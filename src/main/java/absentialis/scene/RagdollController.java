package absentialis.scene;

import absentialis.anatomy.DamageProcessor;
import absentialis.character.Ragdoll;
import absentialis.character.Rig;
import absentialis.entity.Actor;
import java.util.ArrayList;
import java.util.List;

/**
 * How a body answers a blow, and the hand-over between authored animation and physics.
 *
 * The response is graded by the force of the hit, where it landed and what the character was doing - it is not
 * one flinch animation played on every hit:
 *   a light hit        an additive flinch away from the blow; nothing the character was doing is interrupted
 *   a solid hit        a stagger: movement and the current attack are interrupted for a moment
 *   a leg taken out    loss of balance: the character goes down
 *   a limb broken      partial ragdoll: that limb goes limp and hangs while the rest of the body keeps its animation
 *   a heavy hit        a full knock-down: the living body is handed to the ragdoll with the momentum of the blow,
 *                      lies where physics leaves it, and gets back up if it still can
 *   death              the ragdoll takes the body for good (Raid does this; the pose and wounds carry over)
 * A character already committed to something heavy (a giant winding up) shrugs off what would stagger anyone else.
 */
public final class RagdollController {
    private final Raid raid;
    /** Living characters currently on the ground under physics. */
    private final List<Actor> down = new ArrayList<>();
    public int flinches, staggers, knockdowns, recoveries;
    /** 0.6: staggers answered physically, blows taken while physics had the body, bodies that got up by themselves, and those left crawling. */
    public int physicalStaggers, blowsOnTheGround, selfRecoveries, leftCrawling;

    /** Whether this body is one Sydney simulates: alive, not the player, brought fully online and near enough to be seen. */
    private boolean physical(Actor v) {
        // 1.1: every living body within sight, whatever tier of the simulation it is on, whether or not it has been drawn yet,
        // and whether it is on its feet or already crawling. (A clip stood in for all of those.)
        return raid.sydneyEnabled && !v.isPlayer && !v.dead && v.distXZ(raid.player.x, raid.player.z) <= SIMULATED_WITHIN && !v.seated;
    }

    /** How near the player a living body has to be for its loss of balance to be simulated (0.9: 90 m; 1.1: 170 m - as far as anything is drawn). */
    public static float SIMULATED_WITHIN = 170;
    /** 1.1: the wounded who went down under physics, and bodies given to Sydney by a fall. */
    public int collapses, fallRagdolls;

    /**
     * 1.1: somebody has been put down wounded. They do not blend into a pose on the ground: their legs go, and they fall
     * as they were standing - Sydney's body, with nothing pushing it but its own weight. When it has come to rest the
     * wounded pose takes it over where it lies ({@link #update}). False when the body cannot be simulated.
     */
    public boolean collapse(Actor v) {
        if (v.dead || v.isPlayer) return false;
        if (v.ragdoll == null) {
            if (!physical(v)) return false;
            v.hitDirX = v.vx * 0.2f; v.hitDirZ = v.vz * 0.2f; v.hitForce = 0;
            raid.softRagdoll = true;
            try { raid.startRagdoll(v); } finally { raid.softRagdoll = false; }
            down.add(v);
        }
        absentialis.sydney.SydneyCharacter sc = absentialis.sydney.SydneyCharacter.of(v.ragdoll);
        if (sc == null) return false;
        sc.mind.stayDown = true;
        sc.mind.collapse();
        v.downUntil = raid.time;
        collapses++;
        return true;
    }

    /** 1.1: a living body sent sprawling with the velocity it already has and nothing else behind it (no blow is added: a punch that throws it flat back, not up). */
    public boolean shoveDown(Actor v, float seconds) {
        if (v.dead || v.isPlayer || v.ragdoll != null) return false;
        v.hitDirX = 0; v.hitDirZ = 0; v.hitForce = 0;
        raid.softRagdoll = true;
        try { raid.startRagdoll(v); } finally { raid.softRagdoll = false; }
        absentialis.sydney.SydneyCharacter sc = absentialis.sydney.SydneyCharacter.of(v.ragdoll);
        if (sc != null) { sc.mind.collapse(); sc.mind.stun = Math.max(sc.mind.stun, 0.6f); }
        v.downUntil = raid.time + seconds;
        v.fireCooldown = Math.max(v.fireCooldown, seconds + 0.4f); v.burstLeft = 0;
        down.add(v);
        knockdowns++;
        raid.emitSound(v.x, v.y + 0.5f, v.z, 9, "body_fall", v.id, false);
        return true;
    }

    /** 1.1: a body that has been falling too long to land on its feet (the player's included): Sydney has it from here to the ground, and after. */
    public boolean fallRagdoll(Actor v) {
        if (v.dead || v.ragdoll != null || !raid.sydneyEnabled) return false;
        v.hitDirX = 0; v.hitDirZ = 0; v.hitForce = 0;
        raid.softRagdoll = true; raid.playerSydney = v.isPlayer;
        try { raid.startRagdoll(v); } finally { raid.softRagdoll = false; raid.playerSydney = false; }
        absentialis.sydney.SydneyCharacter sc = absentialis.sydney.SydneyCharacter.of(v.ragdoll);
        if (sc == null) { v.ragdoll = null; return false; }
        sc.mind.collapse();
        v.fallSpeed = Math.max(0, -v.vy);
        v.downUntil = raid.time;
        down.add(v);
        fallRagdolls++;
        return true;
    }
    /** 0.9: bodies thrown off balance by something that was not a wound and answered with physics; and how many times a clip had to stand in because the body was out of range. */
    public int unbalanced, clipFallbacks;

    /**
     * 0.9: a body is thrown off balance by something other than a wound - a flash, a slip in blood, a shove. Where the body
     * can be simulated it is given to Sydney with that push and keeps or loses its feet on its own account; nothing is played.
     * Returns false only when it cannot be simulated (too far off to be seen, or not brought fully online), and the caller
     * falls back to its clip.
     */
    public boolean unbalance(Actor v, float dx, float dz, float force, String region) {
        if (v.dead || v.isPlayer) return false;
        if (v.ragdoll != null) return true;
        if (!physical(v)) { clipFallbacks++; return false; }
        v.hitDirX = dx; v.hitDirZ = dz; v.hitForce = force; v.hitRegion = region;
        v.fireCooldown = Math.max(v.fireCooldown, 0.45f);
        v.burstLeft = 0;
        raid.startRagdoll(v);
        absentialis.sydney.SydneyCharacter sc = absentialis.sydney.SydneyCharacter.of(v.ragdoll);
        if (sc == null) { v.ragdoll = null; clipFallbacks++; return false; }
        sc.wound = absentialis.sydney.SydneyCharacter.particleOf("legs".equals(region) ? Rig.L_THIGH : "head".equals(region) ? Rig.HEAD : Rig.TORSO, false);
        down.add(v); v.downUntil = raid.time; unbalanced++; physicalStaggers++;
        return true;
    }

    RagdollController(Raid raid) { this.raid = raid; }

    /** Force thresholds, in damage points of the hit as delivered (before health multipliers). */
    public static final float STAGGER = 24, KNOCKDOWN = 78;

    void react(Actor v, float dx, float dz, float force, int group, int type, Gore.Impact im) {
        // 1.1: a person who has been hit favours the place - a hand to it if one is free (the animated body's own answer;
        // a body Sydney has does the same thing with its motors)
        if (!v.dead && v.anim != null && !v.infected() && force > 4) v.anim.wound(group, Math.min(1f, force / 55f));
        if (v.dead || v.isPlayer) return;
        boolean giant = "tank".equals(v.archetype), heavy = giant || "bomber".equals(v.archetype) || "gladiator".equals(v.archetype) || "butcher".equals(v.archetype) || "tyrant".equals(v.archetype);
        float mass = giant ? 5f : heavy ? 1.8f : 1f;
        // blasts and blunt blows shove; a bullet's push is small for the harm it does
        float shove = force * (type == DamageProcessor.BLAST ? 1.6f : type == DamageProcessor.BLUNT ? 1.25f : type == DamageProcessor.BULLET ? 0.75f : 0.6f) / mass;
        boolean leg = group >= Rig.L_THIGH;
        boolean committed = raid.telegraphing(v, "charge") || raid.telegraphing(v, "throw");
        absentialis.sydney.SydneyConfig sy = absentialis.sydney.SydneyConfig.of(giant ? "giant" : v.infected() ? "infected" : "human");
        if (v.ragdoll != null) {
            // physics already has the body: the blow lands on it where it landed, and what that does is Sydney's to find out
            absentialis.sydney.SydneyCharacter sc = absentialis.sydney.SydneyCharacter.of(v.ragdoll);
            if (sc != null && sc.alive) {
                if (v.anat != null) sc.setBody(v.anat.missingMask, v.limpMask);
                sc.hit(absentialis.sydney.SydneyCharacter.particleOf(group, false), dx, 0.12f, dz, sc.rig.totalMass * Math.min(2.2f, 0.15f + shove / 70f) * 0.5f, shove);
                blowsOnTheGround++;
            }
            return;
        }
        if (shove >= KNOCKDOWN * sy.knockMul || leg && (im.severed || im.fractured) && !giant || im.severed && group == Rig.HEAD) {
            knockDown(v, dx, dz, shove, leg && (im.severed || im.fractured) ? 1.4f : 2.2f + Math.min(2.5f, shove / 60f));
            return;
        }
        if (shove >= STAGGER * sy.staggerMul && !committed || leg && force > 14 && !heavy) {
            staggers++;
            // 0.6: a blow that staggers is no longer answered with the stumble animation. The body is given to Sydney
            // with the momentum of the blow where it landed, and keeps its feet or loses them on its own account.
            if (physical(v)) {
                v.fireCooldown = Math.max(v.fireCooldown, 0.45f);
                v.burstLeft = 0;
                v.hitDirX = dx; v.hitDirZ = dz;
                v.hitForce = Math.min(shove * 0.45f, 26f);
                v.hitRegion = group == Rig.HEAD ? "head" : leg ? "legs" : group >= Rig.L_UPPER_ARM && group < Rig.L_THIGH ? "arms" : "torso";
                raid.startRagdoll(v);
                absentialis.sydney.SydneyCharacter sc = absentialis.sydney.SydneyCharacter.of(v.ragdoll);
                if (sc != null) { sc.wound = absentialis.sydney.SydneyCharacter.particleOf(group, false); down.add(v); v.downUntil = raid.time; physicalStaggers++; raid.emitSound(v.x, v.y + 1, v.z, 6, "stagger", v.id, false); return; }
                v.ragdoll = null;      // too far to be simulated after all: fall through to the authored stumble
            }
            v.staggerUntil = Math.max(v.staggerUntil, raid.time + (type == DamageProcessor.BLAST ? 1.6f : 0.75f));
            v.fireCooldown = Math.max(v.fireCooldown, 0.45f);            // the attack in progress is lost
            v.burstLeft = 0;
            if (v.anim != null && !v.anim.playing("stumble")) v.anim.play("stumble", type == DamageProcessor.BLAST ? 1.1f : 0.65f);
            // knocked a step in the direction of the blow
            v.vx += dx * Math.min(3f, shove * 0.04f); v.vz += dz * Math.min(3f, shove * 0.04f);
            raid.emitSound(v.x, v.y + 1, v.z, 6, "stagger", v.id, false);
            return;
        }
        flinches++;
        if (v.anim != null) v.anim.hit((float) Math.atan2(dx, dz));
    }

    /** Hands a living character to the ragdoll for a while. */
    public void knockDown(Actor v, float dx, float dz, float shove, float seconds) {
        if (v.dead || v.ragdoll != null || v.isPlayer) return;
        knockdowns++;
        v.hitDirX = dx; v.hitDirZ = dz;
        v.hitForce = Math.max(v.hitForce, shove);
        raid.startRagdoll(v);
        v.downUntil = raid.time + seconds;
        { absentialis.sydney.SydneyCharacter sc = absentialis.sydney.SydneyCharacter.of(v.ragdoll); if (sc != null) { sc.mind.onHit(sc, shove); sc.mind.stun = Math.max(sc.mind.stun, 0.9f); } }
        v.fireCooldown = Math.max(v.fireCooldown, seconds + 0.6f);
        v.burstLeft = 0;
        v.vx = v.vz = 0;
        down.add(v);
        raid.emitSound(v.x, v.y + 0.5f, v.z, 9, "body_fall", v.id, false);
    }

    public boolean isDown(Actor a) { return !a.dead && a.ragdoll != null; }
    /** 1.3: bodies that have come round from being knocked out (gates). */
    public int wokeUp;

    /** Physics for the living who are down, and getting them back up. */
    void update(float dt) {
        for (int i = down.size() - 1; i >= 0; i--) {
            Actor a = down.get(i);
            if (a.dead || a.ragdoll == null) { down.remove(i); continue; }   // died on the ground: the ragdoll is the corpse now
            Ragdoll r = a.ragdoll;
            if (!r.settled) r.update(raid.world, dt);
            a.x = r.x[Ragdoll.PELVIS]; a.z = r.z[Ragdoll.PELVIS]; a.y = Math.max(0, r.lowestY());
            a.vx = a.vz = 0;
            absentialis.sydney.SydneyCharacter sc = absentialis.sydney.SydneyCharacter.of(r);
            // 1.3: out cold - it lies there until it comes round, and then it is a body that has been knocked down and gets up
            if (a.unconsciousUntil > -50) {
                if (raid.time < a.unconsciousUntil) { if (sc != null) sc.mind.stayDown = true; a.downUntil = Math.max(a.downUntil, a.unconsciousUntil); continue; }
                a.unconsciousUntil = -99; a.wokeUneasy = true; a.downUntil = raid.time;
                if (sc != null) sc.mind.stayDown = false;
                wokeUp++;
            }
            // 1.1: what the ground does to a body that fell onto it. The fastest it was going down is remembered while it is in
            // the air; when something is under it again, that is the landing
            // 1.3: a thrown body's landing: where it first touches something after leaving the hand
            if (sc != null && a.thrownRagAt > -50 && raid.time - a.thrownRagAt > 0.12f && sc.mind.airborne <= 0) { raid.landedAt[0] = r.x[Ragdoll.PELVIS]; raid.landedAt[1] = r.z[Ragdoll.PELVIS]; a.thrownRagAt = -99; }
            if (sc != null) {
                if (sc.mind.airborne > 0.05f) a.fallSpeed = Math.max(a.fallSpeed, -sc.sense.comV[1]);
                else if (a.fallSpeed > Raid.HARD_LANDING) {
                    float fs = a.fallSpeed;
                    a.fallSpeed = 0;
                    raid.hitKind = "blunt";
                    raid.damageActor(a, null, (fs - Raid.HARD_LANDING) * 6f, null, "torso", a.x, a.y + 0.3f, a.z);
                    raid.hitKind = "";
                    if (a.isPlayer && !a.dead && fs > Raid.HARD_LANDING + 3.5f) raid.body.fracture = true;
                    if (a.dead) { down.remove(i); continue; }
                } else if (sc.mind.airborne <= 0) a.fallSpeed = 0;
            }
            // 1.1: put down wounded. The body lies where it fell; once it is still, the wounded pose has it - on its back, the
            // way physics left it lying - and it can crawl, call out and be worked on from there
            if (sc != null && a.vital != absentialis.entity.Vital.ACTIVE && !a.isPlayer) {
                sc.mind.stayDown = true;
                boolean still = sc.state() == absentialis.sydney.BehaviorCoordinator.State.DOWN && sc.mind.t > 0.35f;
                if (!still && raid.time < a.downUntil + 5f) continue;
                float ax = r.x[Ragdoll.NECK] - r.x[Ragdoll.PELVIS], az = r.z[Ragdoll.NECK] - r.z[Ragdoll.PELVIS];
                sc.release();
                if (ax * ax + az * az > 0.01f) a.yaw = (float) Math.atan2(ax, az);          // the wounded pose lies back from the hips: the head is behind where it faces
                a.faceSet = false;
                int gyd = raid.standY((int) Math.floor(a.x), (int) Math.floor(a.z), Math.round(sc.sense.groundY));
                a.y = gyd >= 0 && Math.abs(gyd - sc.sense.groundY) < 1.5f ? gyd : sc.sense.groundY;
                a.ragdoll = null; a.crawling = true; a.hasGoal = false; a.path = null;
                if (a.anim != null) { a.anim.downT = 1; a.anim.action = ""; }
                down.remove(i);
                continue;
            }
            if (sc != null) {
                // Sydney has the body: it is up when it has got itself up, not when a timer says so
                if (a.anat != null) sc.setBody(a.anat.missingMask, a.limpMask);
                absentialis.sydney.BehaviorCoordinator.Outcome o = sc.outcome();
                boolean overdue = raid.time > a.downUntil + 25;
                if (o == absentialis.sydney.BehaviorCoordinator.Outcome.NONE && !overdue) continue;
                sc.release();
                if (o == absentialis.sydney.BehaviorCoordinator.Outcome.RECOVERED) {
                    // on its feet: animation takes the body back where it stands, facing the way it faces
                    if (sc.cfg.style == absentialis.sydney.SydneyConfig.CRAWLER) {
                        // (1.1: on all fours - it is where its hips are, and it faces the way its spine points)
                        float ax = r.x[Ragdoll.NECK] - r.x[Ragdoll.PELVIS], az = r.z[Ragdoll.NECK] - r.z[Ragdoll.PELVIS];
                        a.x = r.x[Ragdoll.PELVIS] + ax * 0.5f; a.z = r.z[Ragdoll.PELVIS] + az * 0.5f;
                        if (ax * ax + az * az > 0.01f) a.yaw = (float) Math.atan2(-ax, -az);
                    } else {
                        a.x = absentialis.sydney.AnimationBridge.standX(sc); a.z = absentialis.sydney.AnimationBridge.standZ(sc);
                        a.yaw = absentialis.sydney.AnimationBridge.yaw(sc);
                    }
                    a.faceSet = false;
                    // (1.1: the floor it is on, looked for from where it is - not the highest standable place in that column, which
                    // indoors is the roof)
                    int gy = raid.standY((int) Math.floor(a.x), (int) Math.floor(a.z), Math.round(sc.sense.groundY));
                    a.y = gy >= 0 && Math.abs(gy - sc.sense.groundY) < 1.5f ? gy : sc.sense.groundY;
                    a.ragdoll = null;
                    a.staggerUntil = raid.time + 0.25f;
                    a.hasGoal = false; a.path = null;
                    recoveries++; selfRecoveries++;
                    down.remove(i);
                    continue;
                }
                if (o == absentialis.sydney.BehaviorCoordinator.Outcome.CRAWL) leftCrawling++;
                a.downUntil = 0;       // down for good, or unable to rise where it lies: the 0.5 hand-back below (it crawls if its legs will not carry it)
            }
            boolean time = raid.time >= a.downUntil;
            if (!time || !r.settled && raid.time < a.downUntil + 2.5f) continue;
            // back to animation: stand where the body came to rest (or stay down and crawl if the legs will not carry it)
            int gy = raid.standY((int) Math.floor(a.x), (int) Math.floor(a.z), Math.round(a.y + 0.4f));
            if (gy >= 0 && Math.abs(gy - a.y) < 2.5f) a.y = gy;
            a.ragdoll = null;
            a.staggerUntil = raid.time + 1.1f;
            if (a.anat != null && !a.anat.canWalk) a.crawling = true;
            // (1.1: no clip is played over it. What is drawn eases from where physics left the body into whatever it does next.)
            a.hasGoal = false; a.path = null;
            recoveries++;
            down.remove(i);
        }
    }
}
