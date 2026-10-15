package absentialis.character;

import static absentialis.character.Rig.*;

import absentialis.engine.math.Mat4;
import absentialis.entity.Actor;

/**
 * Per-character animation state and pose evaluation (iteration P2).
 *
 * Lower body: the gait phase advances by distance actually travelled divided by the blended stride,
 * so a stance foot stays planted whatever the speed, and the hips turn toward the movement
 * direction while the torso keeps facing the aim (upper and lower body are independent layers).
 * Upper body: weapon holds and action clips drive a weapon channel; both hands are solved onto the
 * weapon by IK. Everything blends over 120 ms. Feet are finally re-solved against the ground.
 *
 * The state is advanced by the simulation ({@link #update}); poses are evaluated on demand by the
 * renderer, the first-person camera and - once, at death - the ragdoll.
 */
public final class Animator {
    public static final float BLEND = 0.12f;

    /** Walkable surface lookup for foot placement: world y of the floor near y at (x, z), or NaN. */
    public interface Ground { float top(float x, float z, float y); }

    // ----- locomotion state
    public float phase, speed, moveAngle, hipYaw, visY, crouchT, armedT, aimT, sprintT, climbT, airT, reachT, turnW, climbPhase;
    private float footYaw, lastX, lastY, lastZ;
    private boolean started, started2, reverse, turning;
    /** Gait family: human, walker, sprinter, fat, giant. */
    public String family = "human";
    public float scale = 1;
    // ----- actions
    public String action = "";
    public float actionTime, actionDur, fireT, hitT, hitYaw;
    /** Kneeling over a casualty (set by scene.Casualty); blended in and out like every other layer. */
    public boolean reviving;
    /** Layer weights eased by update: injury (0 fine .. 1 critical), down on the ground, working on a casualty, in cover, lean. */
    public float injuryT, downT, reviveT2, coverT, coverLowT, leanT, clock;
    /** Which leg carries the limp (fixed per character so it reads as an injury, not a wobble). */
    public int limpSide = 1;
    /** 0.6 layers: sliding, seated, and the eased offset of the look direction from the body's facing (radians). */
    public float slideW, seatW, lookYaw;
    /** 0.8: the steering wheel as the driver's hands have it (eased), -1 .. +1. */
    public float wheelT;
    /** Where the wheel is in front of a seated driver (voxels, in the trunk's own frame: across, above the hips, ahead), its radius, and how far it turns lock to lock (radians each way). */
    public static float WHEEL_Y = 7.4f, WHEEL_Z = -7.2f, WHEEL_R = 2.1f, WHEEL_TURN = 1.25f;
    /** Support-hand grip distance ahead of the firing grip (voxels); 0 = one-handed. */
    public float gripSep = 6.5f;
    public boolean twoHanded = true;
    /**
     * 1.0, Sydney's contextual motion: how far into a pull-up the pose is (eased), how far the weapon is put away for
     * something that needs the hands (slung across the back, or holstered: a climb, a ladder, an item in use), and the
     * vertical speed the weapon in the hands is riding (eased).
     */
    public float mantleW, stowT, airVy;
    private final absentialis.sydney.ContextualMotion.Mantle mplan = new absentialis.sydney.ContextualMotion.Mantle();
    private final float[] ctx = new float[3];
    /** What the weapon weighs (kg): heavier things lag the body more in the air. Set with the grips. */
    public float weaponMass = 3f;
    /**
     * 0.7: the weapon in hand is a handgun. Not aimed, it is not held up at all - the arm hangs, the gun points at the
     * ground beside the thigh and the other arm is free (restT = 1). Aimed, it comes up in both hands.
     */
    public boolean sidearm;
    public float restT;
    /** 0.7 layers: lying prone (crawling on elbows and knees), half-risen to look over low cover, and the body turned side-on to its cover. */
    public float proneT, peekT, sideT;
    /** How far the trunk tips in a full lean (radians). The head and the weapon are turned back by the same, so both stay upright. */
    public static float LEAN_TILT = 0.26f;
    /** Which shoulder is against the cover: +1 right, -1 left (the body is turned a quarter that way and the head looks back over it). */
    public float coverSide = 1;
    /**
     * Tuning of the prone pose (radians / voxels): how flat the body lies, how far the chest is propped, how the head
     * lifts, how high the middle of the pelvis is off the floor (half the depth of a body lying on its front), and how far
     * behind the character's own position the pelvis lies (so that the body is centred on it, head to heel).
     */
    public static float PRONE_PITCH = -1.36f, PRONE_CHEST = 0.34f, PRONE_HEAD = 0.72f, PRONE_HIP = 2.6f, PRONE_BACK = 4f;
    /** The wounded ground pose: how far back the trunk lies, and how high the pelvis is off the floor. */
    public static float DOWN_PITCH = 1.12f, DOWN_HIP = 2.6f;

    /**
     * 0.8: where the root has to be for the pelvis to end up at a given height and distance behind the character once
     * the whole body has been pitched over. The root's pivot is at the feet - twelve voxels below the pelvis - so a body
     * tipped flat about it swings its pelvis a long way forward and nearly down to the pivot's own height. 0.7 lowered
     * the pivot by the standing hip height and left it at that, which put a prone body - and since 0.5 a wounded one -
     * waist-deep in the floor. {rootY, rootZ} for a pitch.
     */
    private static void lieDown(Pose p, float pitch, float hipHeight, float back) {
        p.rootY = hipHeight - HIP_Y * (float) Math.cos(pitch);
        p.rootZ = back - HIP_Y * (float) Math.sin(pitch);
    }

    private final Pose a = new Pose(), b = new Pose(), act = new Pose();
    private final float[] v = new float[3], ik = new float[3];
    private static final float PI = (float) Math.PI;

    public static String familyOf(String archetype, boolean infected) {
        if (!infected) return "human";
        return switch (archetype) { case "sprinter", "screecher" -> "sprinter"; case "bomber", "boomer" -> "fat"; case "tank" -> "giant"; case "gladiator" -> "gladiator"; case "butcher" -> "butcher"; case "crawler" -> "crawler"; case "tyrant" -> "tyrant"; default -> "walker"; };
    }

    /** Starts a one-shot action clip; dur <= 0 uses the clip's own length. */
    public void play(String clip, float dur) {
        // 1.3: a handgun is reloaded as a handgun (see Anims: reload_*_pistol), and the hand that does it is on the gun from the
        // first frame - a handgun carried low leaves the off arm hanging, and that used to ease in only half a second into
        // the reload, so it started with the arm still at the side
        if (sidearm && clip.startsWith("reload_") && Anims.has(clip + "_pistol")) { clip = clip + "_pistol"; restT = 0; }
        if (!Anims.has(clip)) return;
        action = clip;
        actionTime = 0;
        actionDur = dur > 0 ? dur : Anims.get(clip).duration;
        held = false;
    }

    /**
     * 0.9: an action that goes on for as long as it is asked for - feeding, a saw held running. Call it every frame the body is
     * at it: the clip eases in once, loops without a seam for as long as the calls keep coming, and eases out when they stop.
     * (Feeding used to be a one-shot started again each time it ended, so the creature rose off the body and dropped back onto
     * it every 1.3 seconds.)
     */
    public void hold(String clip, float period) {
        if (!Anims.has(clip)) return;
        if (!(held && action.equals(clip))) { action = clip; actionTime = 0; actionDur = period > 0 ? period : Anims.get(clip).duration; held = true; heldW = 0; }
        heldFresh = 0.2f;
    }

    /** One frame of whatever action is playing: a one-shot runs out; a held one runs on and follows whether it is still being asked for. */
    public void advance(float dt) {
        if (action.isEmpty()) held = false;
        else if (held) {
            actionTime += dt;
            heldFresh -= dt;
            float hs = dt / HOLD_BLEND;
            heldW = heldFresh > 0 ? Math.min(1, heldW + hs) : Math.max(0, heldW - hs);
            if (heldFresh <= 0 && heldW <= 0) { action = ""; held = false; }
        }
        else { actionTime += dt; if (actionTime >= actionDur) action = ""; }
    }
    /** How far in a held action is, 0..1 (tests). */
    public float heldWeight() { return held ? heldW : 0; }

    /** Lets go of a held action: it eases out from wherever in its cycle it is. */
    public void release() { heldFresh = 0; }
    public boolean holding(String clip) { return held && heldFresh > 0 && action.equals(clip); }
    private boolean held;
    private float heldW, heldFresh;
    /** Seconds a held action takes to come in and to go out. */
    public static final float HOLD_BLEND = 0.4f;

    public boolean playing(String clip) { return action.equals(clip); }
    public void fire() { fire(1f); }
    /**
     * A shot, with how hard this weapon kicks (0.6): a handgun snaps, a rifle shoves, a shotgun or a magnum rocks the
     * whole upper body back and takes longer to come down from.
     */
    public void fire(float power) { fireT = 1; firePower = Math.max(0.3f, Math.min(3.5f, power)); }
    public float firePower = 1;

    /** What a weapon family's shot does to the body holding it. */
    public static float kickOf(String family, boolean boltOrPump) {
        return switch (family == null ? "" : family) { case "shotgun" -> 3.0f; case "magnum" -> 2.7f; case "rifle" -> boltOrPump ? 2.2f : 1.5f; case "handgun" -> 1.15f; case "auto" -> 0.85f; default -> 1.3f; };
    }
    public void hit(float worldYaw) { hitT = 1; hitYaw = worldYaw; }

    /**
     * 1.1: this body has been hurt in a place (a rig group), badly or not (0..1). For a few seconds it favours the place:
     * the trunk closes round it, a leg that was hit takes less of the weight, and - if a hand is free - the hand goes to
     * it. Which hand is free depends on what is being carried: nothing, either; a handgun, the one not holding it; a
     * long gun, neither while it is up.
     */
    public void wound(int rigGroup, float severity) {
        clutchGroup = rigGroup;
        clutchUntil = clock + 1.4f + 2.6f * Math.max(0, Math.min(1, severity));
        clutchHard = Math.max(clutchHard * 0.5f, 0.45f + 0.55f * Math.max(0, Math.min(1, severity)));
    }
    /** The wound being favoured (rig group, -1 none), until when (on {@link #clock}), how hard, and the layer's eased weight. */
    public int clutchGroup = -1;
    /** 1.2: the guard is up (set by whoever owns the body, every frame), with a blade in the hand or without; and how far up it is. */
    public boolean blocking, blockBlade;
    public float blockW;
    public float clutchUntil = -1, clutchHard, clutchW;
    /** 1.1: seconds this body has been falling, and what is left of the give in the knees from the last landing (0..1). */
    public float fallT, landT;
    private boolean wasAir;
    private float lastAirVy;

    private float actionWeight() {
        if (action.isEmpty()) return 0;
        if (held) return heldW * heldW * (3 - 2 * heldW);
        return Math.max(0, Math.min(1, Math.min(actionTime / BLEND, (actionDur - actionTime) / BLEND)));
    }

    private static float wrap(float x) { while (x > PI) x -= 2 * PI; while (x < -PI) x += 2 * PI; return x; }
    /** Linear cross-fade weight: reaches its target in exactly BLEND seconds. */
    private static float toward(float cur, float target, float dt) { float step = dt / BLEND; return cur < target ? Math.min(target, cur + step) : Math.max(target, cur - step); }

    // ------------------------------------------------------------------ simulation-side update
    public void update(Actor ac, float dt, boolean armed, boolean aiming, boolean engaged) {
        if (!started) { lastX = ac.x; lastY = ac.y; lastZ = ac.z; footYaw = ac.facing(); started = true; }
        if (ac.seated && ac.atWheel) { armed = false; aiming = false; }
        // 0.6 layers: the slide, a seat, and how far the look direction is off the body's facing (the head follows the eyes)
        slideW = toward(slideW, ac.slideT > 0 ? 1 : 0, dt * 0.45f);
        proneT = toward(proneT, ac.prone && !ac.dead ? 1 : 0, dt * 0.34f);
        restT = toward(restT, sidearm && armed && !aiming && fireT <= 0 && action.isEmpty() && !ac.prone && !ac.downed() ? 1 : 0, dt * 0.42f);
        peekT = toward(peekT, ac.coverPeek ? 1 : 0, dt * 0.5f);
        sideT = toward(sideT, ac.coverSide != 0 && ac.cover != null && !aiming && fireT <= 0 ? 1 : 0, dt * 0.45f);
        if (ac.coverSide != 0) coverSide = ac.coverSide;
        seatW = toward(seatW, ac.seated ? 1 : 0, dt * 0.6f);
        float off0 = ac.faceSet ? wrap(ac.yaw - ac.faceYaw) : 0;
        lookYaw += (Math.max(-1.25f, Math.min(1.25f, off0)) - lookYaw) * Math.min(1, dt * 9);
        float dx = ac.x - lastX, dy = ac.y - lastY, dz = ac.z - lastZ;
        lastX = ac.x; lastY = ac.y; lastZ = ac.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist > 3 || Math.abs(dy) > 3) { dist = 0; dy = 0; }  // teleport
        // 0.8: someone sitting in a moving car is not walking. The car's travel is not theirs: no stride, no gait, no step-up bob.
        // (0.7 measured a driver's movement like anyone else's and so played the run cycle in the seat at forty miles an hour.)
        if (ac.seated) { dist = 0; dx = 0; dy = 0; dz = 0; visY = 0; }
        wheelT += (ac.steer - wheelT) * Math.min(1, dt * 9);
        float vel = dt > 1e-5f ? dist / dt : 0;
        // health condition (brief 7): the injury layer eases in, and a hurt body changes pace more slowly
        absentialis.gameplay.CombatConfig cfg = absentialis.gameplay.CombatConfig.get();
        boolean person = family.equals("human");
        int cond = person && !ac.dead ? cfg.condition(ac.hp, ac.maxHp).ordinal() : 0;
        injuryT += (cfg.animation[cond] - injuryT) * Math.min(1, dt * 2.2f);
        clock += dt;
        if (!started2) { limpSide = (ac.id & 1) == 0 ? 1 : -1; started2 = true; }
        speed += (vel - speed) * Math.min(1, dt * 12 * cfg.blendRate[cond]);
        downT = toward(downT, person && ac.downed() ? 1 : 0, dt * 0.4f);
        reviveT2 = toward(reviveT2, reviving ? 1 : 0, dt * 0.5f);
        boolean covered = person && ac.coverAmt > 0.5f && ac.cover != null;
        coverT = toward(coverT, covered ? 1 : 0, dt * 0.55f);
        coverLowT = toward(coverLowT, covered && ac.cover.height == absentialis.world.CoverMap.LOW ? 1 : 0, dt * 0.55f);
        // an agent's lean is kept relative to its cover; the pose needs it relative to the body
        float lean = ac.lean;
        if (!ac.isPlayer && ac.cover != null) { float side = ac.cover.leftX() * (float) Math.cos(ac.facing()) - ac.cover.leftZ() * (float) Math.sin(ac.facing()); lean = -lean * (side >= 0 ? 1 : -1); }
        leanT += (lean - leanT) * Math.min(1, dt * 9);
        // root motion smoothing over one-block steps; a quick knee-up sells the climb
        if (ac.onGround && !ac.climbing && Math.abs(dy) > 0.45f && Math.abs(dy) < 1.3f) {
            visY -= dy;
            if (dy > 0 && action.isEmpty() && !armed) play("vault", 0.32f);
        }
        visY *= (float) Math.exp(-dt * 11);
        if (Math.abs(visY) < 0.002f) visY = 0;
        if (ac.climbing) climbPhase += dy / 1.1f;
        // movement direction relative to the facing; walking backwards runs the cycle in reverse
        float sy = (float) Math.sin(ac.facing()), cy = (float) Math.cos(ac.facing());
        if (dist > 1e-4f && speed > 0.25f) {
            float f = dx * -sy + dz * -cy, r = dx * cy + dz * -sy;
            float ang = (float) Math.atan2(r, f);
            reverse = Math.abs(ang) > (reverse ? 1.45f : 1.9f);
            if (reverse) ang = ang > 0 ? ang - PI : ang + PI;
            moveAngle += wrap(ang - moveAngle) * Math.min(1, dt * 10);
        } else moveAngle *= 1 - Math.min(1, dt * 6);
        phase += (reverse ? -1 : 1) * dist / stride(ac.crouch);
        phase -= (float) Math.floor(phase);
        if (speed > 0.3f && proneT < 0.5f) {
            float target = -Math.max(-0.8f, Math.min(0.8f, moveAngle));
            hipYaw += (target - hipYaw) * Math.min(1, dt * 10);
            footYaw = ac.facing() + hipYaw;
            turning = false;
        } else {
            // feet stay planted while the torso turns; past ~55 degrees the feet shuffle round
            float off = wrap(footYaw - ac.facing());
            if (Math.abs(off) > 0.95f) turning = true;
            if (turning) {
                off -= Math.signum(off) * Math.min(Math.abs(off), dt * 5.5f);
                phase += dt * 1.7f;
                if (Math.abs(off) < 0.08f) turning = false;
            }
            footYaw = ac.facing() + off;
            hipYaw = off;
        }
        turnW = toward(turnW, turning ? 1 : 0, dt);
        crouchT = toward(crouchT, ac.crouch && !ac.prone ? 1 : 0, dt);
        armedT = toward(armedT, armed ? 1 : 0, dt);
        aimT = toward(aimT, aiming ? 1 : 0, dt);
        sprintT = toward(sprintT, ac.sprinting || !ac.isPlayer && armed && !engaged && speed > 3.4f ? 1 : 0, dt);
        climbT = toward(climbT, ac.climbing && !ac.mantling() ? 1 : 0, dt);
        mantleW = toward(mantleW, ac.mantling() ? 1 : 0, dt * 1.2f);
        stowT = toward(stowT, ac.mantling() || ac.climbing || ac.handItem != null ? 1 : 0, dt * 0.6f);
        airVy += ((ac.onGround || ac.climbing ? 0 : ac.vy) - airVy) * Math.min(1, dt * 9);
        boolean inAir = !ac.onGround && !ac.climbing && !ac.seated && !ac.cling;
        airT = toward(airT, inAir && Math.abs(ac.vy) > 2.5f ? 1 : 0, dt);
        // 1.1: how long the fall has gone on (the legs and arms work harder at the air the longer it does), and the landing:
        // the knees take what the fall was going at, and give it back over a third of a second
        fallT = inAir && ac.vy < -2f ? fallT + dt : 0;
        if (inAir) lastAirVy = ac.vy;
        else if (wasAir && lastAirVy < -4.5f) landT = Math.max(landT, Math.min(1, (-lastAirVy - 3f) / 9f));
        wasAir = inAir;
        landT = Math.max(0, landT - dt / 0.36f);
        clutchW = toward(clutchW, clock < clutchUntil && !ac.dead && family.equals("human") ? 1 : 0, dt * 0.55f);
        blockW += ((blocking && !ac.dead ? 1 : 0) - blockW) * Math.min(1, dt * 13);
        reachT = toward(reachT, engaged ? 1 : 0, dt * 0.5f);
        breachUpdate(ac, dt);
        advance(dt);
        fireT = Math.max(0, fireT - dt / (0.11f + 0.045f * firePower));
        hitT = Math.max(0, hitT - dt / 0.35f);
    }

    /** How far the Tank folds forward onto its arms, and how far its hips drop to let the knuckles reach the ground (voxels). */
    public static float KNUCKLE_LEAN = 1.02f, KNUCKLE_DROP = 2.6f;

    /**
     * 0.7: how the big ones carry themselves, laid over the gait.
     *   The Tank goes on its knuckles like a gorilla: the trunk folded forward over bent legs, the arms straight
     *   down to the ground and swung in turn as front legs, the head craned up to see where it is going.
     *   The Gladiator holds its claws out and low to either side, head cocked, listening.
     *   The Butcher carries the saw across its front in the right hand; the left hangs.
     */
    private void carriage(Pose out, float moveW) {
        float sw = (float) Math.sin(phase * 2 * PI) * moveW;
        switch (family) {
            case "giant" -> {
                out.rot[TORSO * 3] = -KNUCKLE_LEAN + 0.06f * (float) Math.sin(phase * 4 * PI) * moveW;
                out.rot[TORSO * 3 + 2] += 0.07f * sw;
                out.rootY -= KNUCKLE_DROP;
                out.rot[HEAD * 3] = KNUCKLE_LEAN * 0.78f;
                // the arms are the front legs: each reaches forward as the opposite leg does
                out.rot[L_UPPER_ARM * 3] = KNUCKLE_LEAN + 0.12f + 0.42f * sw; out.rot[R_UPPER_ARM * 3] = KNUCKLE_LEAN + 0.12f - 0.42f * sw;
                out.rot[L_UPPER_ARM * 3 + 2] = -0.16f; out.rot[R_UPPER_ARM * 3 + 2] = 0.16f;
                out.rot[L_FOREARM * 3] = 0.05f + Math.max(0, sw) * 0.3f; out.rot[R_FOREARM * 3] = 0.05f + Math.max(0, -sw) * 0.3f;
                out.rot[L_HAND * 3] = -0.5f; out.rot[R_HAND * 3] = -0.5f;
            }
            case "gladiator" -> {
                // 0.8: it is blind, and it shows. The head is never still - it turns slowly from side to side with an ear
                // leading, and tips, the way something does that is finding its way by sound; the turning is slower and
                // wider when it is standing than when it is on the move. The chest heaves. The claws hang open at its
                // sides and flex, and trail a little behind each stride instead of pumping like a runner's arms.
                float still = 1 - moveW, listen = (float) Math.sin(clock * 0.85f), listen2 = (float) Math.sin(clock * 0.37f + 1.3f);
                float heave = (float) Math.sin(clock * 1.9f);
                out.rot[TORSO * 3] += -0.16f + 0.035f * heave;
                out.rot[TORSO * 3 + 1] += 0.10f * listen * still;
                out.rot[HEAD * 3] += 0.06f * listen2;
                out.rot[HEAD * 3 + 1] += (0.42f * still + 0.16f * moveW) * listen;
                out.rot[HEAD * 3 + 2] += 0.22f * (listen >= 0 ? 1 : -1) * Math.min(1f, Math.abs(listen) * 2.5f) + 0.08f * listen2;
                float flex = 0.10f * (float) Math.sin(clock * 2.3f), flex2 = 0.10f * (float) Math.sin(clock * 2.3f + 1.9f);
                out.rot[L_UPPER_ARM * 3] = 0.42f + 0.12f * sw - 0.10f * moveW; out.rot[R_UPPER_ARM * 3] = 0.42f - 0.12f * sw - 0.10f * moveW;
                out.rot[L_UPPER_ARM * 3 + 2] = -0.5f - 0.05f * heave; out.rot[R_UPPER_ARM * 3 + 2] = 0.5f + 0.05f * heave;
                out.rot[L_FOREARM * 3] = 0.75f + flex; out.rot[R_FOREARM * 3] = 0.75f + flex2;
                out.rot[L_HAND * 3] = -0.25f + flex * 2; out.rot[R_HAND * 3] = -0.25f + flex2 * 2;
            }
            case "crawler" -> {
                // 1.0, the Crawler: on all fours, and not the way a man would be. The trunk is carried level, close to the ground;
                // the arms are its front legs, planted wide, each reaching as the opposite leg drives; the head is up and never
                // still - it has no eyes, and it turns and tips toward whatever it is listening to. Fast, it flattens and its
                // spine works from side to side.
                // 1.3: on its hands and knees. The hips were carried below knee height with the thighs thrust forward, so the knees
                // came up under the chest and the trunk sat upright on them - from the side, a stack of parts. Now the hips are
                // as high as a thigh is long, the thighs hang from them and step (the knee on the ground, the shin lying back
                // along it), and the trunk leans from the hips to nearly level, the arms reaching from it to the ground.
                float lean = 1.36f + 0.06f * moveW, still = 1 - moveW, hark = (float) Math.sin(clock * 1.1f), hark2 = (float) Math.sin(clock * 0.47f + 0.8f);
                float cw = (float) Math.cos(phase * 2 * PI) * moveW;
                out.rootY = THIGH + 0.9f - HIP_Y + 0.35f * Math.abs(sw) * moveW;
                out.rootZ = 0; out.rootPitch = 0;
                out.rot[L_THIGH * 3] = -0.12f - 0.34f * sw; out.rot[R_THIGH * 3] = -0.12f + 0.34f * sw;
                out.rot[L_THIGH * 3 + 2] = -0.10f; out.rot[R_THIGH * 3 + 2] = 0.10f;
                // the knee coming through lifts the shin off the floor; the one behind lies flat back along it
                out.rot[L_SHIN * 3] = -1.42f - Math.max(0, cw) * 0.45f; out.rot[R_SHIN * 3] = -1.42f - Math.max(0, -cw) * 0.45f;
                out.rot[TORSO * 3] = -lean + 0.05f * (float) Math.sin(phase * 4 * PI) * moveW;
                out.rot[TORSO * 3 + 1] = 0.16f * sw * moveW; out.rot[TORSO * 3 + 2] = 0.10f * sw * moveW;
                out.rot[HEAD * 3] = lean * 0.92f + 0.06f * hark2;
                out.rot[HEAD * 3 + 1] = (0.55f * still + 0.2f * moveW) * hark; out.rot[HEAD * 3 + 2] = 0.3f * hark2 * still;
                out.rot[L_UPPER_ARM * 3] = lean + 0.30f + 0.62f * sw * moveW; out.rot[R_UPPER_ARM * 3] = lean + 0.30f - 0.62f * sw * moveW;
                out.rot[L_UPPER_ARM * 3 + 2] = -0.42f; out.rot[R_UPPER_ARM * 3 + 2] = 0.42f;
                out.rot[L_FOREARM * 3] = 0.30f + Math.max(0, sw) * 0.5f * moveW; out.rot[R_FOREARM * 3] = 0.30f + Math.max(0, -sw) * 0.5f * moveW;
                out.rot[L_HAND * 3] = -0.9f; out.rot[R_HAND * 3] = -0.9f;
            }
            case "tyrant" -> {
                // 1.0, the Tyrant: it does not hurry, and it does not flinch. Walking, it is upright and square - the shoulders
                // level, the arms held a little out from the coat and hardly swinging, the head turning to keep its quarry in
                // front of it. Running, it is a man going through a door: the right shoulder down and forward, the forearm
                // across the chest, the head tucked.
                float run = Math.max(0, Math.min(1, (speed / scale - 2.2f) / 1.6f)), breath = (float) Math.sin(clock * 1.3f);
                out.rot[TORSO * 3] += 0.05f - 0.30f * run + 0.012f * breath;
                out.rot[TORSO * 3 + 1] += -0.42f * run;
                out.rot[HEAD * 3] += -0.04f + 0.22f * run; out.rot[HEAD * 3 + 1] += 0.30f * run;
                out.rot[L_UPPER_ARM * 3] = (0.06f - 0.16f * sw) * (1 - run) - 0.5f * run; out.rot[R_UPPER_ARM * 3] = (0.06f + 0.16f * sw) * (1 - run) + 0.55f * run;
                out.rot[L_UPPER_ARM * 3 + 2] = -0.20f - 0.1f * run; out.rot[R_UPPER_ARM * 3 + 2] = 0.20f - 0.25f * run;
                out.rot[L_FOREARM * 3] = 0.16f + 0.9f * run; out.rot[R_FOREARM * 3] = 0.16f + 1.5f * run;
                out.rot[L_HAND * 3] = 0.1f; out.rot[R_HAND * 3] = 0.1f;
            }
            case "butcher" -> {
                // 0.8: a man carrying a running engine. He leans into its weight and rolls with each step; the saw rides
                // low at the right hip, never quite still - a fine shake from the motor, a slow sag and lift from the arm
                // that is tired of it - and the free hand swings wide for balance, or comes across to steady the bar.
                float idleSag = (float) Math.sin(clock * 0.9f), motor = (float) Math.sin(clock * 31f), motor2 = (float) Math.sin(clock * 47f + 0.7f);
                out.rot[TORSO * 3] += -0.10f - 0.04f * moveW;
                out.rot[TORSO * 3 + 1] += -0.12f + 0.07f * sw;                                     // the right side is carried forward: that is where the weight is
                out.rot[TORSO * 3 + 2] += 0.05f + 0.06f * sw;
                out.rot[HEAD * 3] += 0.10f; out.rot[HEAD * 3 + 1] += 0.12f - 0.05f * sw;
                out.rot[R_UPPER_ARM * 3] = 0.62f + 0.06f * sw + 0.05f * idleSag; out.rot[R_UPPER_ARM * 3 + 2] = 0.14f + 0.012f * motor2;
                out.rot[R_FOREARM * 3] = 0.82f + 0.03f * motor - 0.05f * idleSag;                  // the saw shakes in the fist
                out.rot[R_HAND * 3] = 0.02f * motor2;
                float steady = 1 - moveW;                                                          // standing, the left hand comes across to the bar
                out.rot[L_UPPER_ARM * 3] = (0.1f - 0.28f * sw) * moveW + 0.55f * steady; out.rot[L_UPPER_ARM * 3 + 2] = -0.26f * moveW + 0.30f * steady;
                out.rot[L_FOREARM * 3] = 0.25f * moveW + 0.95f * steady + 0.02f * motor * steady;
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ gait selection
    private String[] gaitNames() {
        return switch (family) {
            case "walker" -> new String[]{"inf_idle", "inf_shamble", "inf_run"};
            case "sprinter" -> new String[]{"inf_idle", "inf_shamble", "inf_run"};
            case "fat" -> new String[]{"inf_idle", "fat_waddle", "fat_waddle"};
            case "giant", "gladiator", "butcher" -> new String[]{"inf_idle", "giant_stomp", "giant_stomp"};
            case "crawler" -> new String[]{"crawl_idle", "crawl_go", "crawl_go"};
            case "tyrant" -> new String[]{"inf_idle", "tyrant_walk", "tyrant_run"};
            default -> new String[]{"idle", "walk", "jog", "sprint"};
        };
    }

    private float[] gaitSpeeds() {
        return switch (family) {
            case "walker" -> new float[]{0, 0.8f, 4.5f};
            case "sprinter" -> new float[]{0, 1.0f, 4.5f};
            case "fat" -> new float[]{0, 0.8f, 3f};
            case "giant" -> new float[]{0, 1.4f, 6f};
            case "gladiator" -> new float[]{0, 0.9f, 7f};
            case "butcher" -> new float[]{0, 1.2f, 5.4f};
            case "crawler" -> new float[]{0, 1.6f, 6.4f};
            case "tyrant" -> new float[]{0, 1.5f, 5.5f};
            default -> new float[]{0, 1.3f, 3.4f, 6.0f};
        };
    }

    /** Index of the lower gait and the blend toward the next one for the current speed. */
    private float gaitBlend(int[] idx) {
        float[] s = gaitSpeeds();
        float sp = speed / scale;
        int i = 0;
        while (i < s.length - 2 && sp > s[i + 1]) i++;
        idx[0] = i;
        return Math.max(0, Math.min(1, (sp - s[i]) / (s[i + 1] - s[i])));
    }

    /** Metres travelled per gait cycle: stance travel / duty factor, so stance feet match ground speed. */
    public float stride(boolean crouched) {
        int[] idx = new int[1];
        float t = gaitBlend(idx);
        String[] n = gaitNames();
        AnimClip c0 = Anims.get(n[idx[0]]), c1 = Anims.get(n[idx[0] + 1]);
        float travel = c0.travel + (c1.travel - c0.travel) * t, duty = c0.duty + (c1.duty - c0.duty) * t;
        if (family.equals("human") && crouchT > 0.01f) {
            AnimClip cw = Anims.get("crouch_walk");
            float ct = Math.min(1, speed / 1.2f);
            travel += (cw.travel * ct - travel) * crouchT;
            duty += (cw.duty - duty) * crouchT;
        }
        return Math.max(0.35f, travel / duty * VOX * scale);
    }

    // ------------------------------------------------------------------ pose evaluation
    /**
     * Evaluates the current pose. aimPitch is the look pitch (radians, up positive); ground may be
     * null (no foot placement). firstPerson keeps the spine upright so the body never swings into
     * the camera.
     */
    public void pose(Actor ac, Pose out, float aimPitch, Ground ground, boolean firstPerson) {
        int[] idx = new int[1];
        float t = gaitBlend(idx);
        String[] names = gaitNames();
        out.aimComp = 0;
        Anims.get(names[idx[0]]).sample(phase, out);
        Anims.get(names[idx[0] + 1]).sample(phase, a);
        out.blend(a, t, ALL_MASK, true, false);
        boolean human = family.equals("human");
        float moveW = Math.min(1, speed / (0.8f * scale));
        if (human && crouchT > 0.01f) {
            Anims.get("crouch_idle").sample(phase, a);
            Anims.get("crouch_walk").sample(phase, b);
            a.blend(b, Math.min(1, speed / 1.2f), ALL_MASK, true, false);
            out.blend(a, crouchT, ALL_MASK, true, false);
        }
        if (turnW > 0.01f) { Anims.get(names[1]).sample(phase, a); out.blend(a, turnW * 0.55f, LOWER_MASK, false, false); }
        if (airT > 0.01f) {
            // 1.1, in the air. It was one fixed pose - a knee up, the arms out - held from take-off to landing. Now it is made
            // from what the body is doing: going up, the knees are drawn up and the arms lift; over the top they open; coming
            // down the legs reach for the ground, and in a fall that goes on they begin to run on nothing while the arms wheel
            // for a balance that is not there - harder and faster the longer it lasts. Each body is a little out of step
            // with the next.
            a.set(out);
            float rise = Math.max(0, Math.min(1, airVy / 5f)), drop = Math.max(0, Math.min(1, -airVy / 8f)), ft = Math.min(1, fallT / 1.1f);
            float rate = 8.5f + 5.5f * ft, ph = clock * rate + (ac.id % 7) * 0.9f, k1 = (float) Math.sin(ph), k2 = (float) Math.cos(ph);
            float amp = drop * (0.14f + 0.62f * ft);
            a.r(L_THIGH, 0.55f * rise + 0.28f * drop + amp * k1, 0, -0.06f * drop).r(L_SHIN, -0.95f * rise - 0.42f * drop - amp * 0.9f * Math.max(0, -k2), 0, 0);
            a.r(R_THIGH, 0.15f * rise + 0.12f * drop - amp * k1, 0, 0.06f * drop).r(R_SHIN, -0.55f * rise - 0.34f * drop - amp * 0.9f * Math.max(0, k2), 0, 0);
            float wheel = drop * ft, a1 = clock * (6f + 4f * ft) + ac.id, a2 = a1 + 2.2f;
            a.r(L_UPPER_ARM, 0.5f * rise + drop * 0.55f + wheel * 0.75f * (float) Math.sin(a1), 0, -0.45f - 0.55f * drop - wheel * 0.3f * (float) Math.cos(a1));
            a.r(R_UPPER_ARM, 0.3f * rise + drop * 0.45f + wheel * 0.75f * (float) Math.sin(a2), 0, 0.45f + 0.55f * drop + wheel * 0.3f * (float) Math.cos(a2));
            a.r(L_FOREARM, 0.35f + 0.5f * drop + wheel * 0.3f * (float) Math.cos(a1), 0, 0).r(R_FOREARM, 0.35f + 0.5f * drop + wheel * 0.3f * (float) Math.cos(a2), 0, 0);
            a.rot[TORSO * 3] += -0.10f * rise + 0.16f * drop + 0.05f * wheel * k2;
            a.rot[HEAD * 3] += -0.22f * drop;                                  // looking at where it is going to land
            a.rootY = 0;
            out.blend(a, airT, ALL_MASK, true, false);
        }
        if (landT > 0.01f) {
            // the landing: hips down over bent knees, the trunk tipped forward over them, and up again
            float l = landT * landT * (3 - 2 * landT);
            out.rootY -= 2.6f * l;
            out.rot[L_THIGH * 3] += 0.55f * l; out.rot[L_SHIN * 3] -= 1.0f * l;
            out.rot[R_THIGH * 3] += 0.48f * l; out.rot[R_SHIN * 3] -= 0.92f * l;
            out.rot[TORSO * 3] -= 0.26f * l; out.rot[HEAD * 3] += 0.12f * l;
        }
        if (climbT > 0.01f) { Anims.get("climb").sample(climbPhase, a); out.blend(a, climbT, ALL_MASK, true, false); }
        // the slide and the seat own the legs and the set of the spine; the arms keep whatever they were doing
        if (slideW > 0.01f) { Anims.get("slide_hold").sample(0, a); out.blend(a, slideW, LOWER_MASK | bit(TORSO), true, false); }
        if (seatW > 0.01f) { Anims.get("seated").sample(0, a); out.blend(a, seatW, LOWER_MASK | bit(TORSO), true, false); }

        // lower body turns into the movement direction; the torso counter-rotates to keep facing the aim
        out.rootYaw = hipYaw;
        float resid = (-moveAngle - hipYaw) * moveW * (1 - climbT);
        out.rot[L_THIGH * 3 + 1] = resid;
        out.rot[R_THIGH * 3 + 1] = resid;
        out.rot[TORSO * 3 + 1] -= hipYaw;

        if (human && injuryT > 0.01f) injury(out, moveW);
        float aw = actionWeight();
        AnimClip ac2 = action.isEmpty() ? null : Anims.get(action);
        if (ac2 != null) ac2.sample(actionTime / actionDur, act);
        float pitch = Math.max(-1.2f, Math.min(1.2f, aimPitch));
        if (human) {
            float armed = armedT * (1 - climbT) * (1 - mantleW);   // (an item in use: the weapon is slung, but the arms are still the clip's - one low at the side, one working)
            // weapon channel: low ready <-> shouldered <-> port arms while sprinting
            Anims.get("wpn_ready").sample(0, a);
            Anims.get("wpn_aim").sample(0, b);
            a.blend(b, aimT, 0, false, true);
            Anims.get("wpn_sprint").sample(0, b);
            a.blend(b, sprintT * (1 - aimT), 0, false, true);
            System.arraycopy(a.wpn, 0, out.wpn, 0, 9);
            // 0.7, a handgun that is not being aimed: the hand hangs at the thigh with the gun pointed at the ground. The rest
            // position is given in the body's frame, so it is turned back through the look pitch (the aim frame nods with it)
            float rest = sidearm ? restT * (1 - climbT) * (1 - crouchT * 0.35f) : 0;
            if (rest > 0.001f) {
                float sw = (float) Math.sin(phase * 2 * PI) * moveW;
                v[0] = 5.6f; v[1] = -10.9f + Math.abs(sw) * 0.3f; v[2] = 0.6f + sw * 1.5f;
                rotX(v, -Math.max(-1.2f, Math.min(1.2f, aimPitch)));
                for (int k = 0; k < 3; k++) out.wpn[k] += (v[k] - out.wpn[k]) * rest;
                out.wpn[3] += (-1.38f - Math.max(-1.2f, Math.min(1.2f, aimPitch)) + sw * 0.10f - out.wpn[3]) * rest;
                out.wpn[4] += (0.06f - out.wpn[4]) * rest;
                out.wpn[5] += (0 - out.wpn[5]) * rest;
            }
            // 0.8, sprinting. With a handgun the run is the run of someone with nothing in their hands: both arms pump, and the gun
            // is simply in the right fist, riding the swing (0.7 clamped both hands to it at port arms, like a rifle - which is
            // what looked stiff). With a long gun the carry is still across the chest, but it moves with the stride now.
            float run = sprintT * (1 - aimT) * (1 - climbT) * (1 - proneT);
            float runFree = sidearm ? run : 0;
            if (runFree > 0.001f) {
                float cs = (float) Math.cos(phase * 2 * PI), sn = (float) Math.sin(phase * 2 * PI);
                v[0] = 5.2f + 0.3f * sn; v[1] = -7.9f + 2.1f * Math.max(0, cs) + 0.5f * Math.max(0, -cs); v[2] = -1.2f - 4.6f * cs;
                rotX(v, -Math.max(-1.2f, Math.min(1.2f, aimPitch)));
                for (int k = 0; k < 3; k++) out.wpn[k] += (v[k] - out.wpn[k]) * runFree;
                out.wpn[3] += (0.25f + 0.75f * cs - Math.max(-1.2f, Math.min(1.2f, aimPitch)) - out.wpn[3]) * runFree;
                out.wpn[4] += (0.12f - out.wpn[4]) * runFree;
                out.wpn[5] += (0.18f * sn - out.wpn[5]) * runFree;
                rest = Math.max(rest, runFree);
            } else if (run > 0.001f && twoHanded) {
                float cs = (float) Math.cos(phase * 4 * PI), sn = (float) Math.sin(phase * 2 * PI);
                out.wpn[0] += sn * 0.85f * run; out.wpn[1] += (0.35f + 0.55f * cs) * run; out.wpn[2] += sn * 0.4f * run;
                out.wpn[4] += sn * 0.13f * run; out.wpn[5] += sn * 0.11f * run; out.wpn[3] += cs * 0.05f * run;
            }
            // 1.0, a jump with a weapon in the hands (Sydney: contextual). The hands are not taken off it - they were, for anything
            // but a handgun, and the weapon stayed where it was in the air while the arms went up without it. The weapon is the
            // hands' load: it lags the body on the way up and settles into the hands on the way down, and the arms go with it.
            if (Math.abs(airVy) > 0.05f) {
                absentialis.sydney.ContextualMotion.airborneWeapon(airVy, weaponMass, ctx);
                out.wpn[1] -= ctx[0] * (1 - aimT * 0.6f);
                out.wpn[3] += ctx[1] * (1 - aimT * 0.6f);
            }
            // weapon handling follows the body: a hurt arm carries the weapon lower and holds it less still
            if (injuryT > 0.01f) { out.wpn[1] -= 0.9f * injuryT * (1 - aimT); out.wpn[3] += (float) Math.sin(clock * 3.1f) * 0.035f * injuryT; out.wpn[4] += (float) Math.sin(clock * 2.3f + 1) * 0.03f * injuryT; }
            out.lhOnWeapon = twoHanded ? 1 : sidearm ? 1 - rest : 0;
            if (!twoHanded && !sidearm) { out.wpn[6] = -4.2f; out.wpn[7] = -7.5f + 1.5f * (float) Math.sin(phase * 2 * PI) * moveW; out.wpn[8] = -1.5f; }
            // a handgun aimed is held in both hands: the support hand wraps the firing hand, just below and inside it
            if (sidearm) { out.wpn[6] = -0.9f; out.wpn[7] = -1.3f; out.wpn[8] = 0; }
            // a whole-body movement made with a weapon in the hands keeps the hands on it: the legs and the trunk are the clip's
            boolean bodyMove = ac2 != null && armed > 0.5f && (action.equals("jump") || action.equals("vault"));
            if (ac2 != null && aw > 0 && !ac2.additive) {
                if (ac2.drivesWeapon && !twoHanded && !sidearm && act.lhOnWeapon > 0.5f) { act.lhOnWeapon = 0; act.wpn[6] = -4.2f; act.wpn[7] = -7.5f; act.wpn[8] = -1.5f; }
                applyAction(out, ac2, aw, bodyMove ? ~ARMS_MASK : ~0);
            }
            // bladed stance and reduced counter-swing with a weapon up
            // squared off to run: the bladed stance is for standing and shooting, and comes most of the way out in a sprint
            float bladed = armed * (twoHanded ? 1 : sidearm ? 0.25f * (1 - rest) : 0.4f) * (1 - 0.7f * run);
            out.rot[TORSO * 3 + 1] += -0.32f * bladed;
            out.rot[HEAD * 3 + 1] += 0.30f * bladed;
            out.rot[TORSO * 3] += (firstPerson ? 0 : pitch * 0.30f);
            out.rot[HEAD * 3] += pitch * 0.45f;
            // third person: the eyes lead - the head turns most of the way to where the player is looking, the chest a little
            if (!firstPerson && Math.abs(lookYaw) > 0.01f) { out.rot[HEAD * 3 + 1] += lookYaw * 0.55f; out.rot[TORSO * 3 + 1] += lookYaw * 0.25f; }
            if (firstPerson) out.rot[TORSO * 3] = Math.max(out.rot[TORSO * 3], -0.10f);
            boolean throwing = ac2 != null && !ac2.drivesWeapon && (ac2.mask & ARMS_MASK) != 0 && !bodyMove;
            // prone: the body lies along the ground, so the aim frame is turned back up by as much as the root went down
            out.aimComp = proneT > 0.01f ? -PRONE_PITCH * proneT : 0;
            if (proneT > 0.01f) prone(out);
            lateW = armed > 0.01f && !throwing ? armed : 0; latePitch = pitch + out.aimComp; lateRest = rest;
            if (lateW > 0) solveArms(out, latePitch, armed, rest);
        } else {
            lateW = 0;
            Anims.get("inf_droop").sample(phase * 0.5f + ac.id * 0.37f, a);
            Anims.get("inf_reach").sample(phase + ac.id * 0.21f, b);
            a.blend(b, reachT, UPPER_MASK, false, false);
            float keep = family.equals("fat") ? 0.6f : 1f;
            // gait lean stays, arms and head come from the infected layer
            out.blend(a, keep, ARMS_MASK | bit(HEAD), false, false);
            out.rot[TORSO * 3] += a.rx(TORSO) * keep * 0.6f;
            out.rot[TORSO * 3 + 2] += a.rz(TORSO) * keep;
            carriage(out, moveW);
            if (ac2 != null && aw > 0 && !ac2.additive) applyAction(out, ac2, aw, ~0);
            out.rot[HEAD * 3] += pitch * 0.3f;
            // 0.7: the head looks where the creature is attending - toward a noise, round an empty room - ahead of the body
            out.rot[HEAD * 3 + 1] += ac.headLook * 0.85f;
            out.rot[TORSO * 3 + 1] += ac.headLook * 0.18f;
        }
        // additive layers: recoil and flinch
        if (fireT > 0 && human) { Anims.get("fire").sample(1 - fireT, a); out.addScaled(a, firePower, bit(TORSO) | bit(HEAD)); }
        if (hitT > 0) {
            Anims.get("hit").sample(1 - hitT, a);
            float rel = wrap(hitYaw - ac.facing());
            out.addScaled(a, (float) Math.cos(rel) > 0 ? -1f : 1f, UPPER_MASK);
            out.rot[TORSO * 3 + 2] += (float) Math.sin(rel) * 0.25f * hitT;
        }
        if (seatW > 0.01f && ac.atWheel && human) wheel(out);
        if (human) {
            if (coverT > 0.01f || Math.abs(leanT) > 0.01f) cover(out);
            if (peekT > 0.01f && coverLowT > 0.01f) peek(out);
            if (reviveT2 > 0.01f) kneel(out);
            if (downT > 0.01f) downed(out);
        }
        // 1.1: the hands are put on the weapon last. They were solved onto it before the recoil, the flinch, the lean, cover and
        // the wounded pose turned the trunk - and the arms hang off the trunk - so each of those carried the hands off a
        // weapon that had stayed where it was aimed. Solved again here, against the trunk as it finally is, the hands are
        // where the weapon is whatever the body is doing under them. (Hands that are busy elsewhere - on a wheel, on a
        // casualty, on a ledge - are left to what is using them.)
        if (human && lateW > 0.01f) {
            float busy = Math.max(seatW > 0.01f && ac.atWheel ? seatW : 0, reviveT2);
            if (busy < 0.99f) solveArms(out, latePitch, lateW * (1 - busy), lateRest);
        }
        if (human && clutchW > 0.01f && clutchGroup >= 0 && downT < 0.5f && proneT < 0.5f && mantleW < 0.3f && seatW < 0.3f && climbT < 0.3f) clutch(out);
        if (human && blockW > 0.01f && downT < 0.5f && proneT < 0.5f && mantleW < 0.3f && seatW < 0.3f && climbT < 0.3f) guard(out);
        if (human && mantleW > 0.01f) mantle(ac, out, firstPerson);
        if (breachW > 0.01f) breach(out);
        if (ground != null && breachW < 0.3f && climbT < 0.5f && airT < 0.5f && downT < 0.5f && proneT < 0.3f && seatW < 0.3f && mantleW < 0.3f) plantFeet(ac, out, ground);
    }

    // ------------------------------------------------------------------ 1.3: through a window, through a hole
    /** How far into a climb through a one-block opening (0..1 each part): hauling up to the sill, through on the belly, down the far side. */
    private float breachW, breachRise, breachCross, breachDrop, breachClock;
    private boolean breachLow;

    private void breachUpdate(Actor ac, float dt) {
        boolean on = ac.breachT > 0 && ac.breach != null && ac.breachDur > 0;
        breachW = on ? Math.min(1, breachW + dt * 9) : Math.max(0, breachW - dt * 4);
        if (!on) return;
        float[] b = ac.breach;
        breachLow = b[4] - b[1] < 0.5f;
        float total = ac.breachDur, at = (1 - ac.breachT / total) * total;
        float rise = Math.max(0.01f, b[4] - b[1]) * absentialis.neuralai.integration.Breach.SECONDS_PER_BLOCK + 0.35f, cross = absentialis.neuralai.integration.Breach.CROSS_SECONDS;
        breachRise = Math.min(1, at / rise);
        breachCross = at < rise ? 0 : Math.min(1, (at - rise) / cross);
        breachDrop = at < rise + cross ? 0 : Math.min(1, (at - rise - cross) / Math.max(0.05f, total - rise - cross));
        breachClock += dt;
    }

    /**
     * 1.3: the climb through a window or a hole the dead take (neuralai.integration.Breach moves the body; this is what it
     * looks like). It was the standing walk pose slid up the wall, through the opening and down the far side. Now: hands
     * up onto the sill and the feet scrabbling at the wall while the body hauls itself up; flat on the belly in the
     * opening, pulling through hand over hand with the legs trailing; and head first over the far sill, the arms
     * reaching down for the floor and the legs swung through after - a body getting in, not a figure being moved. A hole
     * at the foot of a wall (no rise to speak of) is the belly crawl alone.
     */
    private void breach(Pose out) {
        a.set(out);
        float r = breachRise, c = breachCross, d = breachDrop, s = (float) Math.sin(breachClock * 9.5f);
        float pitch, hip;
        if (c <= 0 && breachLow) {
            // a hole at the foot of the wall: down onto the belly in front of it, arms out into it
            float k = r * r * (3 - 2 * r);
            pitch = (PRONE_PITCH - 0.2f) * k;
            hip = HIP_Y + (4.5f - HIP_Y) * k;
            a.r(L_UPPER_ARM, 0.4f + 2.2f * k, 0, -0.25f).r(L_FOREARM, 0.6f - 0.35f * k, 0, 0);
            a.r(R_UPPER_ARM, 0.4f + 2.2f * k, 0, 0.25f).r(R_FOREARM, 0.6f - 0.35f * k, 0, 0);
            float level = -((float) Math.PI / 2 + pitch);
            a.r(L_THIGH, (1 - k) * 0.9f + k * (level + 0.1f), 0, -0.1f).r(L_SHIN, -(1 - k) * 1.4f - 0.25f * k, 0, 0);
            a.r(R_THIGH, (1 - k) * 0.6f + k * (level + 0.1f), 0, 0.1f).r(R_SHIN, -(1 - k) * 1.1f - 0.25f * k, 0, 0);
            a.r(TORSO, 0.3f * (1 - k) + 0.2f * k, 0, 0).r(HEAD, 0.6f * k, 0, 0);
        } else if (c <= 0) {
            // hauling up: leaning into the wall, arms up to the sill and then pressing down on it, the knees climbing
            pitch = -0.25f - 0.35f * r;
            hip = HIP_Y;
            float arm = 2.75f - 1.15f * r * r;
            a.r(L_UPPER_ARM, arm + s * 0.12f, 0, -0.22f).r(L_FOREARM, 0.45f + r * 0.6f, 0, 0);
            a.r(R_UPPER_ARM, arm - s * 0.12f, 0, 0.22f).r(R_FOREARM, 0.45f + r * 0.6f, 0, 0);
            a.r(L_THIGH, 0.55f + Math.max(0, s) * 0.8f, 0, -0.08f).r(L_SHIN, -0.6f - Math.max(0, s) * 0.9f, 0, 0);
            a.r(R_THIGH, 0.55f + Math.max(0, -s) * 0.8f, 0, 0.08f).r(R_SHIN, -0.6f - Math.max(0, -s) * 0.9f, 0, 0);
            a.r(TORSO, 0.15f, 0, 0).r(HEAD, -0.35f, 0, 0);
        } else if (d <= 0) {
            // on the belly in the opening, level, hand over hand
            pitch = PRONE_PITCH - 0.2f;
            hip = 4.5f;
            a.r(L_UPPER_ARM, 2.6f - Math.max(0, s) * 0.7f, 0, -0.25f).r(L_FOREARM, 0.25f + Math.max(0, s) * 0.8f, 0, 0);
            a.r(R_UPPER_ARM, 2.6f - Math.max(0, -s) * 0.7f, 0, 0.25f).r(R_FOREARM, 0.25f + Math.max(0, -s) * 0.8f, 0, 0);
            float level = -((float) Math.PI / 2 + pitch);
            a.r(L_THIGH, level + 0.1f + s * 0.12f, 0, -0.1f).r(L_SHIN, -0.25f, 0, 0);
            a.r(R_THIGH, level + 0.1f - s * 0.12f, 0, 0.1f).r(R_SHIN, -0.25f, 0, 0);
            a.r(TORSO, 0.2f, s * 0.12f, 0).r(HEAD, 0.6f, 0, 0);
        } else {
            // head first down the far side: arms out for the floor, the legs swung through and down as the body comes upright
            pitch = (PRONE_PITCH - 0.2f) * (1 - d) * (1 - d) - 0.15f * (1 - d);
            hip = 4.5f + (HIP_Y - 4.5f) * d;
            a.r(L_UPPER_ARM, 1.4f * (1 - d) + 0.3f, 0, -0.35f).r(L_FOREARM, 0.3f, 0, 0);
            a.r(R_UPPER_ARM, 1.4f * (1 - d) + 0.3f, 0, 0.35f).r(R_FOREARM, 0.3f, 0, 0);
            a.r(L_THIGH, 1.1f * (1 - d) * d * 2.5f, 0, -0.08f).r(L_SHIN, -1.1f * (1 - d), 0, 0);
            a.r(R_THIGH, 0.8f * (1 - d) * d * 2.5f, 0, 0.08f).r(R_SHIN, -0.9f * (1 - d), 0, 0);
            a.r(TORSO, 0.25f * (1 - d), 0, 0).r(HEAD, 0.35f * (1 - d), 0, 0);
        }
        a.rootX = 0; a.rootYaw = 0; a.rootRoll = c > 0 && d <= 0 ? s * 0.06f : 0;
        a.rootPitch = pitch;
        lieDown(a, pitch, hip, c > 0 && d <= 0 ? PRONE_BACK * 0.5f : 0);
        out.blend(a, breachW, ALL_MASK, true, false);
    }

    // ------------------------------------------------------------------ 1.0: Sydney's contextual motion
    /**
     * The pull-up. Sydney plans it (sydney.ContextualMotion): where the hands are on the lip of the ledge, what the legs
     * and the trunk are doing at this point of it. Here the plan is put on the body: the trunk and legs as given, and the
     * arms solved from the shoulders - wherever the climb has carried them this frame - onto the hands' places on the
     * wall, which do not move. Nothing of it is a clip.
     */
    private void mantle(Actor ac, Pose out, boolean firstPerson) {
        absentialis.sydney.ContextualMotion.mantle(ac, mplan);
        float w = mantleW;
        a.set(out);
        a.rootX = 0; a.rootZ = mplan.rootFwd; a.rootY = mplan.rootDrop; a.rootYaw = 0;
        // seen through its own eyes the trunk stays upright: the view is the head's, and a head thrown back or forward is a camera thrown about
        float tp = firstPerson ? Math.max(-0.12f, mplan.torsoPitch * 0.3f) : mplan.torsoPitch;
        a.r(TORSO, tp, 0, mplan.torsoRoll).r(HEAD, firstPerson ? 0 : mplan.headPitch, 0, 0);
        a.r(L_THIGH, mplan.lThigh, 0, 0).r(L_SHIN, mplan.lShin, 0, 0).r(R_THIGH, mplan.rThigh, 0, 0).r(R_SHIN, mplan.rShin, 0, 0);
        out.blend(a, w, ALL_MASK, true, false);
        out.rootYaw *= 1 - w;
        hipYaw *= 1 - w;
        float hw = w * Math.max(0.35f, mplan.handW);
        worldToTorso(ac, out, mplan.handR[0], mplan.handR[1], mplan.handR[2], h);
        armIk(h[0] - SHOULDER_X, h[1] - SHOULDER_Y, h[2], ik);
        blendArm(out, R_UPPER_ARM, hw);
        worldToTorso(ac, out, mplan.handL[0], mplan.handL[1], mplan.handL[2], h);
        armIk(h[0] + SHOULDER_X, h[1] - SHOULDER_Y, h[2], ik);
        blendArm(out, L_UPPER_ARM, hw);
        // palms down on the top: the hands are turned back at the wrist once the weight is over them
        out.rot[L_HAND * 3] += (-0.9f * Math.min(1, mplan.t / 0.6f) - out.rot[L_HAND * 3]) * hw;
        out.rot[R_HAND * 3] += (-0.9f * Math.min(1, mplan.t / 0.6f) - out.rot[R_HAND * 3]) * hw;
        out.lhOnWeapon *= 1 - w;
    }

    /**
     * 1.1, a hand to the wound. The place that was hit is a point on the body (trunk space); the free hand - if there is
     * one - is solved onto it like a hand onto a weapon, the trunk folds toward it, the head goes down to look, and a leg
     * that was hit is carried bent. It eases in over a fifth of a second and out again when the worst has passed.
     */
    /**
     * 1.2, the guard. Empty hands: both fists up in front of the face, the lead a little further out, elbows in, the
     * shoulders round and the chin down behind them. A blade: the hand that holds it across in front of the throat, edge
     * out, and the other hand low and open beside the ribs. Both are solved to where the hands should be - the arms are
     * not played from a clip - so it sits on whatever the body is doing underneath it: standing, walking, crouched.
     */
    private void guard(Pose out) {
        float w = blockW;
        float[] h = new float[3];
        for (int hand = 0; hand < 2; hand++) {
            float hx = hand == 0 ? -1 : 1;
            if (blockBlade) { if (hand == 1) { h[0] = -0.6f; h[1] = 13.6f; h[2] = -4.9f; } else { h[0] = -2.6f; h[1] = 10.2f; h[2] = -3.4f; } }
            else { h[0] = hx * 1.5f; h[1] = hand == 0 ? 13.4f : 12.7f; h[2] = hand == 0 ? -4.8f : -4.1f; }
            armIk(h[0] - hx * SHOULDER_X, h[1] - SHOULDER_Y, h[2], ik);
            blendArm(out, hand == 0 ? L_UPPER_ARM : R_UPPER_ARM, w);
            out.rot[(hand == 0 ? L_HAND : R_HAND) * 3] = -0.35f * w;
        }
        out.lhOnWeapon *= 1 - w;
        // behind it: the back rounds, the head comes down
        out.rot[TORSO * 3] += 0.12f * w;
        out.rot[HEAD * 3] += 0.10f * w;
    }

    private void clutch(Pose out) {
        int g = clutchGroup;
        float w = clutchW * clutchHard;
        boolean left = leftSide(g), legW = g >= L_THIGH, armW = g >= L_UPPER_ARM && g <= R_HAND, mid = g == HEAD || g == TORSO;
        float side = mid ? 0 : left ? -1 : 1;
        // the body closes round the hurt
        out.rot[TORSO * 3] -= (legW ? 0.34f : g == TORSO ? 0.24f : g == HEAD ? 0.12f : 0.10f) * w;
        out.rot[TORSO * 3 + 2] += -side * (legW ? 0.14f : 0.10f) * w;
        out.rot[HEAD * 3] -= (g == HEAD ? 0.32f : 0.16f) * w;
        if (g == HEAD) out.rot[HEAD * 3 + 2] += 0.14f * w;
        if (legW) {
            int th = left ? L_THIGH : R_THIGH;
            out.rootY -= 1.5f * w;
            out.rot[th * 3] += 0.34f * w; out.rot[(th + 1) * 3] -= 0.62f * w;
            int ot = left ? R_THIGH : L_THIGH;
            out.rot[ot * 3] += 0.22f * w; out.rot[(ot + 1) * 3] -= 0.40f * w;
        }
        // which hand: with a long gun up there is none; a handgun leaves the support hand; empty hands leave both
        boolean leftFree = armedT < 0.5f || !twoHanded, rightFree = armedT < 0.5f;
        int hand;
        if (armW) hand = left ? (rightFree ? 1 : -1) : (leftFree ? 0 : -1);          // the other arm's hand
        else if (mid) hand = leftFree ? 0 : rightFree ? 1 : -1;
        else hand = left && leftFree ? 0 : !left && rightFree ? 1 : leftFree ? 0 : rightFree ? 1 : -1;
        if (hand < 0) return;
        float hx = hand == 0 ? -1 : 1;
        switch (g) {
            case HEAD -> { h[0] = hx * 2.2f; h[1] = 15.2f; h[2] = -3.4f; }
            case TORSO -> { h[0] = -hx * 0.9f; h[1] = 6.4f; h[2] = -2.9f; }
            case L_UPPER_ARM, R_UPPER_ARM -> { h[0] = side * 6.2f; h[1] = 8.6f; h[2] = -1.6f; }
            case L_FOREARM, R_FOREARM, L_HAND, R_HAND -> { h[0] = side * 5.2f; h[1] = 4.6f; h[2] = -3.2f; }
            case L_THIGH, R_THIGH -> { h[0] = side * 2.4f; h[1] = -3.2f; h[2] = -2.6f; }
            default -> { h[0] = side * 2.4f; h[1] = -5.2f; h[2] = -3.4f; }
        }
        armIk(h[0] - hx * SHOULDER_X, h[1] - SHOULDER_Y, h[2], ik);
        blendArm(out, hand == 0 ? L_UPPER_ARM : R_UPPER_ARM, clutchW);
        out.rot[(hand == 0 ? L_HAND : R_HAND) * 3] = -0.5f * clutchW;
        if (hand == 0) out.lhOnWeapon *= 1 - clutchW;
    }

    /** A point of the world in the trunk's own frame (voxels, the origin at the hip joint, as the arm solver wants it). */
    private void worldToTorso(Actor ac, Pose p, float wx, float wy, float wz, float[] pt) {
        float unit = VOX * scale, sy = (float) Math.sin(ac.facing()), cy = (float) Math.cos(ac.facing());
        float dx = (wx - ac.x) / unit, dy = (wy - ac.y - visY) / unit, dz = (wz - ac.z) / unit;
        pt[0] = cy * dx - sy * dz - p.rootX; pt[1] = dy - (p.rootY + HIP_Y); pt[2] = sy * dx + cy * dz - p.rootZ;
        rotY(pt, -p.rootYaw);
        rotY(pt, -p.ry(TORSO)); rotZ(pt, -p.rz(TORSO)); rotX(pt, -p.rx(TORSO));
    }

    // ------------------------------------------------------------------ 0.5 layers: injury, cover, rescue, the ground
    /**
     * The limp. On the half of the cycle the bad leg carries the weight, the hips drop onto it and the trunk leans
     * over it; that leg takes a short, stiff step; the whole posture closes up round the hurt. At the critical end
     * the trunk sways and the knees are close to giving. Driven by the same gait phase as the legs, so the dip
     * lands on the footfall.
     */
    private void injury(Pose out, float moveW) {
        float k = injuryT, s = (float) Math.sin(phase * 2 * PI) * limpSide, load = Math.max(0, s);
        int th = limpSide > 0 ? R_THIGH : L_THIGH, sh = th + 1;
        out.rot[th * 3] *= 1 - 0.45f * k;
        out.rot[sh * 3] *= 1 - 0.40f * k;
        out.rootY -= (0.35f + 1.0f * load * moveW) * k;
        out.rot[TORSO * 3 + 2] += -limpSide * 0.17f * k * load * moveW;
        out.rot[TORSO * 3] -= 0.17f * k;
        out.rot[HEAD * 3] -= 0.10f * k;
        // the arm on the hurt side is held in against the body instead of swinging
        int arm = limpSide > 0 ? R_UPPER_ARM : L_UPPER_ARM;
        out.rot[arm * 3] *= 1 - 0.6f * k * (1 - armedT);
        out.rot[arm * 3 + 2] += -limpSide * 0.12f * k * (1 - armedT);
        if (k > 0.7f) {
            float c = (k - 0.7f) / 0.3f, sway = (float) Math.sin(clock * 2.1f);
            out.rot[TORSO * 3 + 2] += sway * 0.07f * c;
            out.rootX += sway * 0.55f * c;
            out.rootY -= 0.5f * c + Math.max(0, (float) Math.sin(clock * 0.9f)) * 0.35f * c;
        }
    }

    /**
     * In cover. 0.7: the body is side-on to the obstacle - a shoulder and the back against it, not the chest - and
     * whoever owns the body has turned its facing along the wall for that (Actor.coverSide); what this layer adds is
     * the press into the wall, the weapon drawn in across the chest and the head looking back over the shoulder.
     * Coming up to aim squares the body to the target again; leaning out takes the trunk with the weapon.
     */
    private void cover(Pose out) {
        float c = coverT, hide = 1 - aimT, side = sideT * coverSide;
        out.rootZ -= 1.1f * c * (1 - sideT);
        out.rootX += side * 1.5f * c;                              // the shoulder goes to the wall
        out.rot[TORSO * 3 + 2] += side * 0.07f * c;                 // and the weight leans on it
        out.rot[TORSO * 3 + 1] += 0.34f * c * hide * (1 - sideT);
        out.rot[HEAD * 3 + 1] -= 0.30f * c * hide * (1 - sideT);
        if (sideT > 0.01f) { out.wpn[0] -= 1.2f * sideT * c; out.wpn[2] += 1.6f * sideT * c; out.wpn[3] += 0.55f * sideT * c * hide; out.wpn[4] += -side * 0.5f * c * hide; }
        out.rootY -= 0.9f * coverLowT * hide;
        out.rot[TORSO * 3] -= 0.10f * coverLowT * hide;
        // 0.8: the trunk goes over with the lean and the head comes back by as much - the eyes (and the sights) stay level
        out.rootX += leanT * 3.0f;
        out.rot[TORSO * 3 + 2] -= leanT * LEAN_TILT;
        out.rot[HEAD * 3 + 2] += leanT * LEAN_TILT;
        out.wpn[5] += leanT * LEAN_TILT;
    }

    /**
     * Looking over low cover (0.7): not a full stand. The legs come half-way up, the trunk tips forward over the top
     * of the obstacle and the head lifts to clear it - so what shows above the cover is the head, the shoulders and
     * the weapon, and it drops back the moment the sights come down.
     */
    private void peek(Pose out) {
        float w = peekT * coverLowT;
        out.rootY -= 3.4f * w;
        out.rootZ -= 1.2f * w;
        out.rot[TORSO * 3] += -0.26f * w;
        out.rot[HEAD * 3] += 0.22f * w;
        for (int th : new int[]{L_THIGH, R_THIGH}) { out.rot[th * 3] += 0.52f * w; out.rot[(th + 1) * 3] += -0.92f * w; }
    }

    /**
     * Prone (0.7): flat on the front, hips on the ground, chest propped on the elbows, head up to look along the
     * ground. Crawling works the opposite elbow and knee together and rocks the hips with them; it goes any way the
     * keys say - forward, back, sideways - because the legs are driven by the travel phase, not by a facing.
     * The arms are left to the solver: with a weapon they hold it out in front; without one they are the prop.
     */
    private void prone(Pose out) {
        a.set(out);
        float crawl = Math.min(1, speed / 0.35f), ph = phase * 2 * PI, s = (float) Math.sin(ph) * crawl, co = (float) Math.cos(ph) * crawl;
        a.rootX = 0; a.rootYaw = 0;
        a.rootRoll = s * 0.09f;
        a.rootPitch = PRONE_PITCH + co * 0.03f;
        lieDown(a, a.rootPitch, PRONE_HIP + Math.abs(co) * 0.25f, PRONE_BACK);
        a.r(TORSO, PRONE_CHEST, s * 0.10f, -s * 0.05f).r(HEAD, PRONE_HEAD, 0, 0);
        // legs: out behind and level with the floor (the trunk slopes up to the elbows, so the thighs are turned back down by
        // as much), a little apart; the knee on the working side is drawn up and out, then pushed straight
        float level = -((float) Math.PI / 2 + PRONE_PITCH);
        a.r(L_THIGH, level + Math.max(0, s) * 0.40f, 0, -0.16f - Math.max(0, s) * 0.34f).r(L_SHIN, -Math.max(0, s) * 0.95f - 0.03f, 0, 0);
        a.r(R_THIGH, level + Math.max(0, -s) * 0.40f, 0, 0.16f + Math.max(0, -s) * 0.34f).r(R_SHIN, -Math.max(0, -s) * 0.95f - 0.03f, 0, 0);
        if (armedT < 0.5f) {
            // unarmed: forearms on the ground, elbows under the shoulders, reaching alternately
            a.r(L_UPPER_ARM, 1.55f - Math.max(0, -s) * 0.45f, 0, -0.30f).r(L_FOREARM, 1.15f + Math.max(0, -s) * 0.35f, 0, 0);
            a.r(R_UPPER_ARM, 1.55f - Math.max(0, s) * 0.45f, 0, 0.30f).r(R_FOREARM, 1.15f + Math.max(0, s) * 0.35f, 0, 0);
        }
        out.blend(a, proneT, ALL_MASK, true, false);
        out.rootYaw *= 1 - proneT;
        // the weapon is held low, just off the ground in front of the face
        out.wpn[1] += (-3.6f - out.wpn[1]) * proneT * 0.8f;
        out.wpn[2] += (-6.6f - out.wpn[2]) * proneT * 0.6f;
        out.wpn[0] += (1.2f - out.wpn[0]) * proneT * 0.6f;
    }

    /**
     * 0.8, at the wheel: both hands on the rim, at a quarter to three, and they go round with it - the left comes up as
     * the wheel goes right. The arms are solved onto the rim like hands onto a weapon, so they reach it whatever the
     * build of the driver; the trunk sits a little back into the seat and the legs are the seated pose's, out to the pedals.
     */
    private void wheel(Pose out) {
        float w = seatW, a = wheelT * WHEEL_TURN, c = (float) Math.cos(a), s = (float) Math.sin(a);
        out.rootYaw *= 1 - w;
        out.rot[TORSO * 3 + 1] *= 1 - w; out.rot[TORSO * 3 + 2] += (wheelT * -0.05f - out.rot[TORSO * 3 + 2]) * w;
        // the rim leans back toward the driver: its plane is tipped, so "up the wheel" is up and a little away
        float ty = WHEEL_Y, tz = WHEEL_Z;
        h[0] = -WHEEL_R * c; h[1] = ty + WHEEL_R * s * 0.92f; h[2] = tz - WHEEL_R * s * 0.38f;
        armIk(h[0] + SHOULDER_X, h[1] - SHOULDER_Y, h[2], ik);
        blendArm(out, L_UPPER_ARM, w);
        h[0] = WHEEL_R * c; h[1] = ty - WHEEL_R * s * 0.92f; h[2] = tz + WHEEL_R * s * 0.38f;
        armIk(h[0] - SHOULDER_X, h[1] - SHOULDER_Y, h[2], ik);
        blendArm(out, R_UPPER_ARM, w);
        out.rot[L_HAND * 3] = -0.3f * w; out.rot[R_HAND * 3] = -0.3f * w;
        out.lhOnWeapon = 0;
    }

    /** Working on a casualty: down on the knees (the crouch supplies the legs), bent over, both hands pressing in rhythm. */
    private void kneel(Pose out) {
        float w = reviveT2, pump = (float) Math.sin(clock * 7.5f) * 0.12f;
        out.rot[TORSO * 3] += (-0.50f - out.rot[TORSO * 3]) * w;
        out.rot[HEAD * 3] += (-0.30f - out.rot[HEAD * 3]) * w;
        for (int arm : new int[]{L_UPPER_ARM, R_UPPER_ARM}) {
            out.rot[arm * 3] += (0.95f + pump - out.rot[arm * 3]) * w;
            out.rot[arm * 3 + 1] *= 1 - w;
            out.rot[arm * 3 + 2] += ((arm == L_UPPER_ARM ? 0.22f : -0.22f) - out.rot[arm * 3 + 2]) * w;
            out.rot[(arm + 1) * 3] += (0.35f - pump - out.rot[(arm + 1) * 3]) * w;
        }
        out.rootY -= 0.6f * w;
    }

    /**
     * The wounded ground pose: on the back with the hips on the floor, propped on one elbow, one leg out and one
     * knee drawn up, the head lifted to look along the body. A weapon hand keeps whatever the arm solver gave it
     * (the sidearm stays on its target); the other arm is the prop. Crawling rocks the body with the gait phase.
     */
    private void downed(Pose out) {
        a.set(out);
        float crawl = Math.min(1, speed / 0.5f), rock = (float) Math.sin(phase * 2 * PI) * crawl;
        a.rootX = 0; a.rootRoll = 0.10f * limpSide + rock * 0.05f;
        a.rootPitch = DOWN_PITCH + rock * 0.05f;
        lieDown(a, a.rootPitch, DOWN_HIP, 0);
        // the trunk is propped a hand's breadth off the floor on the elbow, not sat up (with the pelvis where it belongs,
        // the old angle would have had a wounded man sitting bolt upright)
        a.r(TORSO, -0.05f, 0, 0).r(HEAD, -0.42f, 0, 0);
        a.r(L_THIGH, 0.45f + rock * 0.12f, 0, 0).r(L_SHIN, -0.05f, 0, 0);
        a.r(R_THIGH, 0.95f - rock * 0.12f, 0, 0).r(R_SHIN, -1.15f, 0, 0);
        a.r(L_UPPER_ARM, -1.35f + rock * 0.18f, 0, -0.30f).r(L_FOREARM, 1.40f, 0, 0);      // the elbow behind and the forearm flat: the hand is on the floor, not through it
        if (armedT < 0.5f) a.r(R_UPPER_ARM, 0.35f, 0, 0.55f).r(R_FOREARM, 1.25f, 0, 0);     // a hand over the wound
        a.lhOnWeapon = 0;
        out.blend(a, downT, ALL_MASK, true, false);
        out.lhOnWeapon *= 1 - downT;
    }

    /** Layers the sampled action clip: limbs and head blend in, the torso is added on top of the gait. */
    private void applyAction(Pose out, AnimClip clip, float w, int allow) {
        out.blend(act, w, clip.mask & ~bit(TORSO) & allow, clip.drivesRoot, clip.drivesWeapon);
        if (!clip.drivesRoot) out.rootY += act.rootY * w;
        if ((clip.mask & bit(TORSO)) != 0) for (int k = 0; k < 3; k++) out.rot[TORSO * 3 + k] += act.rot[TORSO * 3 + k] * w;
    }

    /** Shoulder-centre origin of the aim frame in hips space. */
    private void shoulderOrigin(Pose p, float[] o) {
        o[0] = 0; o[1] = SHOULDER_Y; o[2] = 0;
        rotX(o, p.rx(TORSO)); rotZ(o, p.rz(TORSO)); rotY(o, p.ry(TORSO));
        o[1] += HIP_Y;
    }

    /** Aim frame (weapon space parent): hips * shoulder origin * facing * look pitch. */
    public void aimFrame(Pose p, float aimPitch, Mat4 hips, Mat4 out) {
        shoulderOrigin(p, v);
        out.set(hips).translate(v[0], v[1], v[2]).rotateY(-p.rootYaw).rotateX(Math.max(-1.2f, Math.min(1.2f, aimPitch)) + p.aimComp);
    }

    /** 1.0: the frame of the upper back - at the shoulders, turned and leaning with the trunk, not with the look: what a slung weapon hangs in. */
    public void backFrame(Pose p, Mat4 hips, Mat4 out) {
        shoulderOrigin(p, v);
        out.set(hips).translate(v[0], v[1], v[2]).rotateY(p.ry(TORSO)).rotateZ(p.rz(TORSO)).rotateX(p.rx(TORSO));
    }

    /** Weapon matrix: aim frame * weapon channel. Origin = firing grip, -z = muzzle direction. */
    public static void weaponFrame(Pose p, Mat4 aim, Mat4 out) {
        out.set(aim).translate(p.wpn[0], p.wpn[1], p.wpn[2]).rotateY(p.wpn[4]).rotateX(p.wpn[3]).rotateZ(p.wpn[5]);
    }

    private final float[] o = new float[3], h = new float[3];
    /** What the last arm solve of a pose is done with (see the end of {@link #pose}): its weight, the pitch of the aim frame and how far a handgun is at rest. */
    private float lateW, latePitch, lateRest;

    /** Converts an aim-frame point to torso space. */
    private void aimToTorso(Pose p, float pitch, float[] pt) {
        rotX(pt, pitch); rotY(pt, -p.rootYaw);
        pt[0] += o[0]; pt[1] += o[1] - HIP_Y; pt[2] += o[2];
        rotY(pt, -p.ry(TORSO)); rotZ(pt, -p.rz(TORSO)); rotX(pt, -p.rx(TORSO));
    }

    private void solveArms(Pose p, float pitch, float weight, float rest) {
        shoulderOrigin(p, o);
        // firing hand on the grip
        h[0] = p.wpn[0]; h[1] = p.wpn[1]; h[2] = p.wpn[2];
        aimToTorso(p, pitch, h);
        armIk(h[0] - SHOULDER_X, h[1] - SHOULDER_Y, h[2], ik);
        blendArm(p, R_UPPER_ARM, weight);
        // support hand: on the weapon (grip + clip offset) or free
        float on = p.lhOnWeapon;
        h[0] = on >= 0.5f || sidearm ? p.wpn[6] : 0; h[1] = (on >= 0.5f || sidearm ? p.wpn[7] : 0) + 0.5f; h[2] = (on >= 0.5f || sidearm ? p.wpn[8] : 0) - (sidearm ? 0.4f : gripSep);
        rotZ(h, p.wpn[5]); rotX(h, p.wpn[3]); rotY(h, p.wpn[4]);
        h[0] += p.wpn[0]; h[1] += p.wpn[1]; h[2] += p.wpn[2];
        float fx = sidearm ? -5.2f : on < 0.5f ? p.wpn[6] : -4.0f, fy = sidearm ? -9.5f : on < 0.5f ? p.wpn[7] : -7.0f, fz = sidearm ? -0.5f : on < 0.5f ? p.wpn[8] : -2.0f;
        h[0] = fx + (h[0] - fx) * on; h[1] = fy + (h[1] - fy) * on; h[2] = fz + (h[2] - fz) * on;
        aimToTorso(p, pitch, h);
        armIk(h[0] + SHOULDER_X, h[1] - SHOULDER_Y, h[2], ik);
        // a handgun at rest leaves the other arm alone: it hangs and swings with the walk like an empty hand
        blendArm(p, L_UPPER_ARM, sidearm ? weight * (1 - rest) : weight);
    }

    private void blendArm(Pose p, int upper, float w) {
        int fore = upper + 1, hand = upper + 2;
        p.rot[upper * 3] += (ik[1] - p.rot[upper * 3]) * w;
        p.rot[upper * 3 + 1] += (ik[0] - p.rot[upper * 3 + 1]) * w;
        p.rot[upper * 3 + 2] *= 1 - w;
        p.rot[fore * 3] += (ik[2] - p.rot[fore * 3]) * w;
        p.rot[fore * 3 + 1] *= 1 - w; p.rot[fore * 3 + 2] *= 1 - w;
        p.rot[hand * 3] *= 1 - w;
    }

    /** Foot IK: stance feet follow the real floor (kerbs, stairs), swing feet never dip into it. */
    private void plantFeet(Actor ac, Pose p, Ground ground) {
        float sy = (float) Math.sin(ac.facing()), cy = (float) Math.cos(ac.facing()), unit = VOX * scale;
        for (int side = 0; side < 2; side++) {
            int th = side == 0 ? L_THIGH : R_THIGH, sh = th + 1;
            float t = p.rx(th), s = p.rx(sh);
            float fwd = THIGH * (float) Math.sin(t) + SHIN * (float) Math.sin(t + s), down = THIGH * (float) Math.cos(t) + SHIN * (float) Math.cos(t + s);
            v[0] = 0; v[1] = 0; v[2] = -fwd;
            rotY(v, p.ry(th));
            v[0] += side == 0 ? -HIP_X : HIP_X;
            rotY(v, p.rootYaw);
            v[0] += p.rootX; v[2] += p.rootZ;
            float wx = ac.x + (cy * v[0] + sy * v[2]) * unit, wz = ac.z + (-sy * v[0] + cy * v[2]) * unit;
            float footH = p.rootY + HIP_Y - down;                       // voxels above the actor's floor
            float g = ground.top(wx, wz, ac.y);
            if (Float.isNaN(g)) continue;
            float floor = (g - ac.y - visY) / unit;                      // real floor relative to the actor's floor
            float want;
            if (footH < 0.7f) want = Math.max(-9f, Math.min(9f, floor)) + Math.max(0, footH);
            else want = Math.max(footH, floor + 0.3f);
            float delta = want - footH;
            if (Math.abs(delta) < 0.08f) continue;
            legIk(fwd, down - delta, ik);
            p.rot[th * 3] = ik[0];
            p.rot[sh * 3] = ik[1];
        }
    }
}
