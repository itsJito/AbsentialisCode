package absentialis.neuralai.integration;

import static absentialis.neuralai.integration.Steering.goTo;
import static absentialis.neuralai.integration.Steering.stop;
import static absentialis.neuralai.integration.Steering.turn;
import static absentialis.neuralai.integration.Steering.wrapPi;

import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.neuralai.api.SydneyBridge.CreatureIntent;
import absentialis.neuralai.api.SydneyBridge.CreatureMode;
import absentialis.neuralai.integration.Archetypes.Arch;
import absentialis.scene.Raid;
import absentialis.world.MapData;

/**
 * The bodies of the infected (0.8). A creature's mind is a NeuralAI mind like any other: it sees with the same vision
 * meter, hears the same sound events, holds the same kind of belief with the same growing error, and chooses - hunt,
 * scour, prowl, notice, feed, wander. What it chooses arrives here as a {@link CreatureIntent}, and this class is how
 * each kind of body carries that out: the walk and the lunge of the ordinary dead, and the one thing each of the
 * special infected does.
 *
 *   Bomber ...... bloated and soaked in bile. It walks up, swells and bursts; the burst hurts little, but everyone
 *                 it reaches is covered in bile - and for fifteen seconds every infected within smelling distance
 *                 comes for them, while their own sight swims.
 *   Spewer ...... lanky, the jaw gone, poison running from the hole. Keeps its distance and spits pools that burn
 *                 whoever stands in them: it is there to move people off the ground they chose.
 *   Tank ........ the giant. It charges, throws masonry, and takes hold of whoever it reaches - anyone - lifts them
 *                 in one hand and throws them fifteen feet.
 *   Gladiator ... blind, in a helmet and clawed gauntlets, armoured everywhere but the middle of its back. It has no
 *                 eyes and nothing it does comes from sight: it walks slowly and listens; a noise brings it at a run,
 *                 and it swings at the place the noise was, at head height. Being silent is safe. So is being below
 *                 the swing. Only walking into it - body against body - tells it you are there without a sound.
 *   Butcher ..... a sack over its head and a chainsaw. It runs at what it sees and takes the head off with one
 *                 swing; the swing has a wind-up, and a round to the head during the wind-up breaks it.
 *
 * Nothing in here knows where a target is unless the intent says the mind has it in sight; everything else is done
 * about the point the mind believes in. Every attack is telegraphed (a sound and a wind-up, never instant) and every
 * one has an answer. The numbers are public so the gates and a tuning pass can reach them.
 */
public final class Creatures {
    private Creatures() {}

    /** The Tank's throw: how long the victim is held aloft, how far it is thrown (fifteen feet), and the rest between grabs. */
    public static float GRAB_HOLD = 0.75f, THROW_METRES = 4.57f, GRAB_EVERY = 8f, GRAB_REACH = 2.9f;
    /** The Gladiator: reach of the claws, the wind-up, and how close a body must come to be felt (a touch, not a sight). */
    public static float CLAW_REACH = 2.6f, CLAW_WINDUP = 0.5f, TOUCH = 0.25f;
    /** The Butcher: reach of the saw, and the wind-up in which a shot to the head stops it. */
    public static float SAW_REACH = 2.7f, SAW_WINDUP = 0.75f, SAW_STAGGER = 2.0f;
    /** What the Gladiator's plate lets through from the front, and what the gap in its back makes of a hit. */
    public static float PLATE = 0.06f, WEAK_POINT = 1.8f;
    /** Reach of a lunge, the seconds between blows (the giant's are slower), and how far short of its quarry a body stops. */
    public static float LUNGE_REACH = 1.6f, BLOW_EVERY = 1.1f, GIANT_BLOW_EVERY = 1.8f, STAND_OFF = 1.1f;
    /** The Fast: how long the shriek roots it, how often it may shriek, when it tires, and how wide it comes in. */
    public static float SHRIEK_SECONDS = 0.7f, SHRIEK_EVERY = 12f, TIRES_AFTER = 7f, FLANK_FROM = 7f, FLANK_RADIUS = 5.5f;
    /** The Bomber: how close it comes before it swells, and for how long. */
    public static float SWELL_AT = 2.4f, SWELL_SECONDS = 0.9f;
    /** The Tank: ranges for the thrown masonry and for the charge, and the speed of the charge. */
    public static float HURL_MIN = 10f, HURL_MAX = 30f, HURL_EVERY = 9f, CHARGE_MIN = 5f, CHARGE_EVERY = 8f, CHARGE_SPEED = 8.5f, CHARGE_SECONDS = 2.6f;

    /** Feeding: how many storeys up or down a body may be and still be gone to, and how long a walk to one is persisted with. */
    public static float MEAL_STOREYS = 3f, MEAL_PATIENCE = 45f;

    public static int grabs, thrown, swings, ducked, spits, deflected, weakHits, interrupted;

    static void setState(Actor a, String s) { if (!a.state.equals(s)) { a.state = s; a.stateTime = 0; a.hasGoal = false; a.hasCover = false; } }

    // ------------------------------------------------------------------ one frame of one body
    /** Carries out what the creature's mind asked for, in the way of its kind. */
    public static void drive(Raid r, Actor a, CreatureIntent in, float dt) {
        Arch ar = Archetypes.arch(a.archetype);
        ambient(r, a, dt);
        boolean giant = "tank".equals(a.archetype);
        // the giant is never a surprise: its footfalls carry and shake the ground
        if (giant && (Math.abs(a.vx) + Math.abs(a.vz)) > 0.4f) { a.talkTimer -= dt; if (a.talkTimer <= 0) { a.talkTimer = 0.95f; r.emitSound(a.x, a.y, a.z, 46, "stomp", a.id, false); r.quake(a.x, a.z, 26, 0.22f); } }
        float px = (float) in.point().x(), py = (float) in.point().y(), pz = (float) in.point().z();
        boolean roused = in.mode() == CreatureMode.HUNT || in.mode() == CreatureMode.SCOUR || in.mode() == CreatureMode.PROWL || in.mode() == CreatureMode.NOTICE;
        // what the rest of the game reads off a body: where its attention is, and how old that is
        if (roused) { a.memX = px; a.memY = py; a.memZ = pz; a.memTime = r.time - (float) Math.min(600, in.beliefAge()); }
        // 0.9: what it is after may be on the floor above: it goes up to it rather than milling about underneath
        a.goalY = roused && Math.abs(py - a.y) > 1.6f ? py : Float.NaN; a.goalYAt = r.time;
        if ("gladiator".equals(a.archetype)) { gladiator(r, a, ar, roused, dt); return; }
        if ("crawler".equals(a.archetype)) { crawler(r, a, ar, roused, dt); return; }
        // a hand that has closed on someone finishes what it started, whatever the mind has moved on to
        if (giant && a.grabId != 0 && holdAndThrow(r, a, dt)) return;
        switch (in.mode()) {
            case HUNT -> {
                Actor t = in.targetVisible() ? r.actor((int) in.targetId()) : null;
                if (t != null && t.dead) t = null;
                hunt(r, a, ar, t, px, pz, dt);
            }
            case NOTICE -> {
                // it has heard something. The head comes round to the noise first, then the body follows it
                setState(a, "notice");
                a.noticeUntil = r.time + 0.25f;
                stop(a);
                float want = (float) Math.atan2(-(px - a.x), -(pz - a.z));
                a.headLookTarget = Math.max(-1.25f, Math.min(1.25f, wrapPi(want - a.yaw)));
                if (a.stateTime > 0.45f) a.yaw = turn(a.yaw, want, dt * 1.6f);
            }
            case PROWL -> {
                setState(a, "investigate");
                dropMeal(a);
                float d = a.distXZ(px, pz);
                if (d > 2.2f) {
                    // on the way: the eyes are on where the noise was, whatever the feet are stepping round
                    goTo(r, a, px, pz, ar.speed() * (a.alertness > 0.8f ? 1.7f : 1.25f), dt);
                    a.headLookTarget = Math.max(-0.9f, Math.min(0.9f, wrapPi((float) Math.atan2(-(px - a.x), -(pz - a.z)) - a.yaw)));
                } else {
                    // there, and nothing: it stands and looks about - a long look one way, then the other
                    stop(a);
                    float t = a.stateTime + a.id * 0.37f;
                    a.headLookTarget = (float) Math.sin(t * 1.1f) * 1.1f;
                    a.yaw += (float) Math.sin(t * 0.45f) * dt * 0.9f;
                }
            }
            case SCOUR -> {
                setState(a, "search");
                dropMeal(a);
                if (a.distXZ(px, pz) > 2) goTo(r, a, px, pz, ar.speed() * 1.2f, dt); else { stop(a); a.yaw += dt * 1.6f; }
            }
            case FEED -> feed(r, a, ar, dt);
            case WANDER -> {
                if (!"idle".equals(a.state)) { a.headLookTarget = 0; setState(a, "idle"); }
                dropMeal(a);
                // at rest the head is never quite still - it follows small things, and settles
                if (r.rng.chance(dt * 0.25)) a.headLookTarget = r.rng.range(-0.9f, 0.9f); else if (r.rng.chance(dt * 0.35)) a.headLookTarget = 0;
                // ecology: the regular dead drift to unattended corpses and gather there (group pressure builds on bodies)
                if ("walker".equals(a.archetype)) {
                    a.lootTimer -= dt;
                    if (a.lootTimer <= 0) {
                        a.lootTimer = 4 + r.rng.nextFloat() * 4;
                        Actor c = r.nearestCorpse(a.x, a.z, 26);
                        if (c != null && a.distXZ(c.x, c.z) > 3) { a.goalX = c.x + r.rng.range(-1.5f, 1.5f); a.goalZ = c.z + r.rng.range(-1.5f, 1.5f); a.hasGoal = true; a.stateTime = 0; a.thinkTimer = 0; }
                    }
                }
                wander(r, a, ar, dt);
            }
        }
    }

    private static void dropMeal(Actor a) { if (a.feedOn != null) { a.feedOn = null; if (a.anim != null && a.anim.playing("inf_feed")) a.anim.release(); } }

    // ------------------------------------------------------------------ the hunt
    /** t is the quarry while the mind has it in sight, and null when it is going on a belief alone. */
    private static void hunt(Raid r, Actor a, Arch ar, Actor t, float px, float pz, float dt) {
        boolean seen = t != null;
        dropMeal(a);
        if (!"engage".equals(a.state)) {
            // the moment one of the ordinary dead sees something living, it says so
            if ("walker".equals(a.archetype) && a.tier == 0) r.emitSound(a.x, a.y + 1.5f, a.z, 16, "growl", a.id, false);
            setState(a, "engage");
        }
        if (seen) { a.targetId = t.id; a.lastSeenTime = r.time; }
        // out of sight is out of reach: a blow needs something in front of the eyes
        float d = seen ? a.dist(t) : Math.max(LUNGE_REACH + 1.5f, a.distXZ(px, pz));
        if ("spewer".equals(a.archetype)) { spewer(r, a, ar, t, d, px, pz, dt); return; }
        if ("butcher".equals(a.archetype)) { butcher(r, a, ar, t, d, px, pz, dt); return; }
        if ("tyrant".equals(a.archetype)) { tyrant(r, a, ar, t, d, px, pz, dt); return; }
        boolean fast = "sprinter".equals(a.archetype) || "screecher".equals(a.archetype), fat = "bomber".equals(a.archetype), giant = "tank".equals(a.archetype);
        a.yaw = turn(a.yaw, (float) Math.atan2(-(px - a.x), -(pz - a.z)), dt * 6);
        float speed = ar.runSpeed(), gx = px, gz = pz;
        if (fast) {
            // loud vocal cue first, then the rush; they tire quickly and come in on a flank
            if (a.stateTime < 0.05f && a.specialTimer <= 0) { a.specialTimer = SHRIEK_EVERY; r.telegraph(a, "shriek", SHRIEK_SECONDS); a.fireCooldown = SHRIEK_SECONDS; }
            if (r.telegraphing(a, "shriek")) { stop(a); return; }
            if (a.stateTime > TIRES_AFTER) speed = ar.speed() * 1.5f;
            if (seen && d > FLANK_FROM) { float side = (a.id & 1) == 0 ? 1 : -1, ang = (float) Math.atan2(a.x - t.x, a.z - t.z) + side * 0.9f; gx = t.x + (float) Math.sin(ang) * FLANK_RADIUS; gz = t.z + (float) Math.cos(ang) * FLANK_RADIUS; }
        }
        if (fat) {
            // the Bomber walks in, swells and bursts
            if (seen && d < SWELL_AT && !r.telegraphing(a, "swell") && a.suppressUntil <= 0) { a.suppressUntil = r.time + SWELL_SECONDS; r.telegraph(a, "swell", SWELL_SECONDS); }
            if (a.suppressUntil > 0 && r.time >= a.suppressUntil) { r.explodeBomber(a, null); return; }
            if (a.suppressUntil > 0) { stop(a); return; }
        }
        if (giant) {
            if (seen && grab(r, a, t, d, dt)) return;
            if (!seen && (r.telegraphing(a, "grab") || a.burstLeft == 2)) { a.burstLeft = 0; stop(a); return; }       // the hand closes on nothing
            if (seen && d > HURL_MIN && d < HURL_MAX && a.specialTimer <= 0 && !r.telegraphing(a, "throw")) { a.specialTimer = HURL_EVERY; a.goalX = t.x; a.goalZ = t.z; a.memY = t.y; r.telegraph(a, "throw", 1.3f); a.burstLeft = 1; }
            if (r.telegraphing(a, "throw")) { stop(a); return; }
            if (a.burstLeft == 1) { a.burstLeft = 0; r.hurl(a, a.goalX, a.memY, a.goalZ); }
            if (seen && d > CHARGE_MIN && d <= HURL_MIN && a.specialTimer <= 0) { a.specialTimer = CHARGE_EVERY; a.suppressUntil = r.time + CHARGE_SECONDS; r.telegraph(a, "charge", 1.1f); }
            if (r.telegraphing(a, "charge")) { stop(a); return; }
            if (r.time < a.suppressUntil) speed = CHARGE_SPEED;                             // the charge itself
            if (a.stuckTime > 0.15f) r.smash(a);                                            // light cover does not stop it
        }
        if (seen && d < LUNGE_REACH + ar.melee() * 0.01f + (giant ? 0.6f : 0) && a.fireCooldown <= 0 && ar.melee() > 0) {
            a.fireCooldown = giant ? GIANT_BLOW_EVERY : BLOW_EVERY;
            if (a.anim != null) a.anim.play(giant ? "giant_smash" : "inf_attack", 0);
            r.melee(a, t, ar.melee() * r.difficultyDamage());
        }
        if (!seen || d > (fat ? 2.0f : STAND_OFF)) goTo(r, a, gx, gz, speed, dt); else stop(a);
    }

    // ------------------------------------------------------------------ what they do when nothing is happening
    private static void ambient(Raid r, Actor a, float dt) {
        switch (a.archetype) {
            case "spewer" -> {
                // the poison never stops coming out of it: drops from where the jaw was, and the odd one that marks the floor
                if (a.tier == 0 && r.rng.chance(dt * 6)) r.drip(a, 0.82f, 0xFF86D83C, true);
                if (a.tier == 0 && r.rng.chance(dt * 0.5)) r.gore.bile(a.x, a.y + a.height() * 0.8f, a.z, (float) -Math.sin(a.yaw) * 0.6f, 0, (float) -Math.cos(a.yaw) * 0.6f, 0.012f);
            }
            case "bomber" -> { if (a.tier == 0 && r.rng.chance(dt * 1.6)) r.drip(a, 0.55f, absentialis.scene.Gore.BILE, false); }
            case "butcher" -> {
                // the saw is never off: it idles, and it can be heard coming. (0.9: and seen - the chain runs and the engine shakes
                // the thing in its fist, faster when it is after someone, flat out when it is raised. All of this is done by a living
                // Butcher's body, so all of it stops when the Butcher does: Raid.kill stalls the saw.)
                boolean raised = r.telegraphing(a, "rev") || a.anim != null && (a.anim.playing("saw_rev") || a.anim.playing("saw_swing"));
                float want = raised ? 1f : "engage".equals(a.state) ? 0.62f : 0.35f;
                a.sawRev += (want - a.sawRev) * Math.min(1, dt * (want > a.sawRev ? 7 : 2.5f));
                a.sawPhase += dt * (10 + 46 * a.sawRev);
                a.talkTimer -= dt;
                if (a.talkTimer <= 0) { a.talkTimer = 0.85f; r.emitSound(a.x, a.y + 1, a.z, "engage".equals(a.state) ? 36 : 18, "chainsaw", a.id, false); }
            }
            default -> { }
        }
    }

    /** Down at a body, eating. It stays until the body is gone, or until its mind has something living to attend to. */
    private static void feed(Raid r, Actor a, Arch ar, float dt) {
        absentialis.gameplay.CombatConfig fc = absentialis.gameplay.CombatConfig.get();
        Actor c = a.feedOn;
        if (c == null || !r.actors.contains(c) || c.eaten >= 1f) {
            dropMeal(a);
            Actor meal = r.time < a.mealBanUntil ? null : r.nearestMeal(a.x, a.z, fc.feedSeek);
            if (meal == null || Math.abs(meal.y - a.y) > MEAL_STOREYS) { setState(a, "idle"); wander(r, a, ar, dt); return; }
            a.feedOn = c = meal;
        }
        setState(a, "feed");
        // 0.8: it goes to the body it has smelt and gets down beside it. (0.7 only ate a body it happened to be standing
        // within arm's length of, which is why almost none were ever eaten.) A body it cannot get to is given up for a while.
        if (a.distXZ(c.x, c.z) > fc.feedReach * 0.8f || Math.abs(c.y - a.y) > 1.2f) {
            if (a.anim != null && a.anim.playing("inf_feed")) a.anim.release();
            goTo(r, a, c.x, c.z, ar.speed() * 1.15f, dt);
            if (a.stateTime > MEAL_PATIENCE) { a.mealBanUntil = r.time + 60; dropMeal(a); setState(a, "idle"); }
            return;
        }
        a.stateTime = Math.min(a.stateTime, 1);
        stop(a);
        a.yaw = turn(a.yaw, (float) Math.atan2(-(c.x - a.x), -(c.z - a.z)), dt * 4);
        // 0.9: held, not restarted - it gets down once and stays down, the cycle running on without a break
        if (a.anim != null) a.anim.hold("inf_feed", 1.7f);
        a.talkTimer -= dt;
        if (a.talkTimer <= 0) { a.talkTimer = 1.6f + r.rng.nextFloat() * 1.8f; r.emitSound(a.x, a.y + 0.4f, a.z, 9, "feed", a.id, false); }
        if (r.gore.eat(c, dt / Math.max(4f, fc.feedSeconds))) { if (!c.isPlayer) r.consume(c); a.feedOn = null; if (a.anim != null) a.anim.release(); setState(a, "idle"); }
    }

    /**
     * The infected with nothing to hunt. They do not stand. Each drifts: a slow walk to somewhere near, a pause that
     * may be a few seconds or half a minute, another walk - sometimes toward others of its kind, so they bunch and
     * string out along a street, sometimes a long straight wander away from where it was. A goal it cannot get to is
     * given up, not stood in front of.
     */
    public static void wander(Raid r, Actor a, Arch ar, float dt) {
        boolean arrived = a.hasGoal && a.distXZ(a.goalX, a.goalZ) < 1.6f;
        if (!a.hasGoal || arrived || a.stateTime > 40 || a.stuckTime > 2.2f) {
            if (arrived || !a.hasGoal) a.thinkTimer = r.rng.chance(0.25) ? r.rng.range(6f, 18f) : r.rng.range(0.6f, 4f);
            float roll = r.rng.nextFloat();
            float gx, gz;
            if (roll < 0.3f) {
                // toward company: another of the dead within earshot
                Actor other = null;
                float bd = 24;
                for (Actor o : r.nearby(a.x, a.z, 24)) { if (o == a || !o.infected() || o.dead) continue; float d = a.dist(o); if (d > 5 && d < bd) { bd = d; other = o; } }
                if (other != null) { gx = other.x + r.rng.range(-2.5f, 2.5f); gz = other.z + r.rng.range(-2.5f, 2.5f); }
                else { gx = a.x + r.rng.range(-9f, 9f); gz = a.z + r.rng.range(-9f, 9f); }
            } else if (roll < 0.55f) {
                // along the street: the nearest node of the road graph some way off
                MapData.Node n = r.world.map.nearestNode(a.x + r.rng.range(-26f, 26f), a.z + r.rng.range(-26f, 26f));
                if (n != null && Math.abs(n.x - a.homeX) < 60 && Math.abs(n.z - a.homeZ) < 60) { gx = n.x + r.rng.range(-2f, 2f); gz = n.z + r.rng.range(-2f, 2f); }
                else { gx = a.homeX + r.rng.range(-10f, 10f); gz = a.homeZ + r.rng.range(-10f, 10f); }
            } else {
                // a short drift from where it stands, the way it is already facing as often as not
                float ang = r.rng.chance(0.5) ? a.yaw + (float) Math.PI + r.rng.range(-0.9f, 0.9f) : r.rng.range(0f, 6.28f), rad = r.rng.range(3f, 11f);
                gx = a.x + (float) Math.sin(ang) * rad; gz = a.z + (float) Math.cos(ang) * rad;
            }
            // 1.1, the horde: one that belongs to a hotspot - or is in the pull of gunfire - is making for that, not for
            // anywhere of its own choosing; the place becomes where it belongs
            float[] hg = r.horde.goalFor(a);
            if (hg != null) { gx = hg[0]; gz = hg[1]; a.homeX = hg[2]; a.homeZ = hg[3]; if (r.horde.hurry(a)) a.thinkTimer = 0; }
            // never further than a block or two from where it belongs: the dead are a place's dead
            float hx = gx - a.homeX, hz = gz - a.homeZ, hl = (float) Math.sqrt(hx * hx + hz * hz);
            if (hl > 46) { gx = a.homeX + hx / hl * 46; gz = a.homeZ + hz / hl * 46; }
            a.goalX = gx; a.goalZ = gz; a.hasGoal = true; a.stateTime = 0; a.stuckTime = 0;
        }
        if (a.thinkTimer > 0) {
            // standing: but swaying, turning a little, the head following something that is not there
            a.thinkTimer -= dt;
            stop(a);
            a.yaw += (float) Math.sin(r.time * 0.3 + a.id) * dt * 0.5f;
            return;
        }
        goTo(r, a, a.goalX, a.goalZ, ar.speed() * (0.55f + (a.id % 5) * 0.07f) * (r.horde.hurry(a) ? 2.3f : 1f), dt);
    }

    // ------------------------------------------------------------------ Spewer
    private static void spewer(Raid r, Actor a, Arch ar, Actor t, float d, float px, float pz, float dt) {
        boolean seen = t != null;
        if (r.telegraphing(a, "retch")) { stop(a); return; }
        if (a.burstLeft > 0) { a.burstLeft = 0; r.spit(a, a.goalX, a.memY, a.goalZ); spits++; return; }
        // the retch is the warning: the pool lands where the target stood when it began
        if (seen && d > 3.5f && d < 15 && a.specialTimer <= 0) { a.specialTimer = 5.5f; a.goalX = t.x; a.goalZ = t.z; a.memY = t.y; r.telegraph(a, "retch", 0.9f); a.burstLeft = 1; return; }
        if (seen && d < 1.7f && a.fireCooldown <= 0) { a.fireCooldown = 1.3f; if (a.anim != null) a.anim.play("inf_attack", 0); r.melee(a, t, ar.melee() * r.difficultyDamage()); }
        if (seen && d < 5.5f) {
            // it does not want to be close: it backs off the way it came
            float bx = a.x - t.x, bz = a.z - t.z, bl = Math.max(0.01f, (float) Math.sqrt(bx * bx + bz * bz));
            goTo(r, a, a.x + bx / bl * 6, a.z + bz / bl * 6, ar.runSpeed(), dt);
        } else if (!seen || d > 11) goTo(r, a, px, pz, ar.runSpeed() * 0.8f, dt);
        else { stop(a); a.yaw = turn(a.yaw, (float) Math.atan2(-(t.x - a.x), -(t.z - a.z)), dt * 5); }
    }

    // ------------------------------------------------------------------ Tank: the grab
    /** The hand is closed on someone: lift, hold, throw. Returns true while it has the Tank's whole attention. */
    private static boolean holdAndThrow(Raid r, Actor a, float dt) {
        Actor v = r.actor(a.grabId);
        if (v == null || v.dead) { a.grabId = 0; return false; }
        a.grabT -= dt;
        stop(a);
        float fx = (float) -Math.sin(a.yaw), fz = (float) -Math.cos(a.yaw), k = 1 - Math.max(0, a.grabT) / GRAB_HOLD;
        // off the ground and up to arm's length overhead, in one hand
        r.hold(a, v, a.x + fx * 1.3f, a.y + 0.4f + 2.0f * Math.min(1, k * 1.7f), a.z + fz * 1.3f);
        if (a.grabT <= 0) { a.grabId = 0; r.fling(a, v, fx, fz); thrown++; a.fireCooldown = 1.4f; }
        return true;
    }

    /** One frame of reaching for someone in sight. Returns true when it has the Tank's whole attention this frame. */
    private static boolean grab(Raid r, Actor a, Actor t, float d, float dt) {
        if (r.telegraphing(a, "grab")) { stop(a); a.yaw = turn(a.yaw, (float) Math.atan2(-(t.x - a.x), -(t.z - a.z)), dt * 5); return true; }
        if (a.burstLeft == 2) {
            a.burstLeft = 0;
            // the hand closes where the target is now: out of reach by then, and it closes on nothing
            if (!t.dead && d < GRAB_REACH + 0.6f && t.ragdoll == null) { a.grabId = t.id; a.grabT = GRAB_HOLD; grabs++; r.grabbed(a, t, GRAB_HOLD); if (a.anim != null) a.anim.play("giant_throw", GRAB_HOLD + 0.25f); }
            return true;
        }
        if (d < GRAB_REACH && r.time >= a.grabAt && a.fireCooldown <= 0.4f && t.ragdoll == null) { a.grabAt = r.time + GRAB_EVERY; r.telegraph(a, "grab", 0.5f); a.burstLeft = 2; return true; }
        return false;
    }

    // ------------------------------------------------------------------ Gladiator
    /** It cannot see. Everything it does, it does about the last thing it heard - or about a body it has walked into. */
    private static void gladiator(Raid r, Actor a, Arch ar, boolean roused, float dt) {
        if (r.time < a.staggerUntil) { stop(a); return; }
        if (r.telegraphing(a, "hark") || r.telegraphing(a, "claw")) { stop(a); return; }
        if (a.burstLeft > 0) { a.burstLeft = 0; sweep(r, a, CLAW_REACH, 0.15f, true, "claw_swing"); a.fireCooldown = 1.3f; return; }
        Actor near = touched(r, a);
        if (near != null && a.fireCooldown <= 0) {
            a.yaw = (float) Math.atan2(-(near.x - a.x), -(near.z - a.z));
            r.telegraph(a, "claw", CLAW_WINDUP); a.burstLeft = 1; stop(a);
            return;
        }
        if (roused) {
            if (!"investigate".equals(a.state)) { setState(a, "investigate"); r.telegraph(a, "hark", 0.45f); stop(a); return; }
            float d = a.distXZ(a.memX, a.memZ);
            if (d > 1.7f) {
                // the charge: straight at the noise, and thin cover in the way is not in the way
                goTo(r, a, a.memX, a.memZ, ar.runSpeed(), dt);
                a.sprinting = true;
                if (a.stuckTime > 0.2f) r.smash(a);
            } else {
                // where the noise was: it cuts at the place, then stands with its head turning, listening
                stop(a);
                if (a.specialTimer <= 0 && a.fireCooldown <= 0) { a.specialTimer = 3.2f; r.telegraph(a, "claw", CLAW_WINDUP); a.burstLeft = 1; }
                a.headLookTarget = (float) Math.sin((a.stateTime + a.id) * 1.4f) * 1.1f;
            }
            return;
        }
        // nothing heard: it walks, slowly, head tilting to one side and the other
        if (!"idle".equals(a.state)) setState(a, "idle");
        a.headLookTarget = (float) Math.sin((r.time + a.id * 1.7f) * 0.5f) * 0.7f;
        wander(r, a, ar, dt);
    }

    /** Someone the Gladiator has found without hearing them: a body it is actually in contact with. Touch, not sight. */
    private static Actor touched(Raid r, Actor a) {
        Actor best = null;
        float bd = 1e9f;
        for (Actor o : r.nearby(a.x, a.z, CLAW_REACH + 1)) {
            if (o == a || o.dead || o.infected() || Math.abs(o.y - a.y) > 1.6f) continue;
            float dx = o.x - a.x, dz = o.z - a.z, d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d > a.radius + o.radius + TOUCH) continue;
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    // ------------------------------------------------------------------ 1.0: the Tyrant
    /** How far off it sets itself for a charge (metres), how fast and how long the charge is, how often, and what its fist and its shoulder do. */
    public static float TACKLE_MIN = 4.5f, TACKLE_MAX = 20f, TACKLE_SPEED = 9.2f, TACKLE_SECONDS = 2.4f, TACKLE_EVERY = 7.5f, TACKLE_SET = 0.9f, TACKLE_DAMAGE = 34f, FIST_REACH = 2.3f, FIST_EVERY = 1.5f;
    public static int tackled;

    /**
     * It walks. It does not stop, it is not turned aside, and what is thin enough to break is not in its way. When what it
     * is after is in front of it and far enough off, it sets itself - the shoulder drops, and it roars - and then it comes:
     * in a straight line, as fast as a car in a street, and whoever it meets is taken off their feet and thrown. It cannot
     * turn much once it is going; a charge that misses carries on past. Close to, it uses its fists.
     */
    private static void tyrant(Raid r, Actor a, Arch ar, Actor t, float d, float px, float pz, float dt) {
        if (r.time < a.staggerUntil) { stop(a); a.burstLeft = 0; return; }
        if (r.telegraphing(a, "tackle")) {
            stop(a);
            // it lines itself up while it sets: the line it will run is the line to where its quarry is when it goes
            float want = (float) Math.atan2(-(px - a.x), -(pz - a.z));
            a.yaw = turn(a.yaw, want, dt * 4.5f);
            a.goalX = px; a.goalZ = pz;
            return;
        }
        if (a.burstLeft == 3) {
            // the charge. The line was fixed when it started; it bends toward the quarry only slowly
            if (r.time >= a.suppressUntil) { a.burstLeft = 0; a.sprinting = false; a.fireCooldown = Math.max(a.fireCooldown, 0.8f); return; }
            float want = (float) Math.atan2(-(px - a.x), -(pz - a.z));
            a.yaw = turn(a.yaw, want, dt * 0.55f);
            float dx = (float) -Math.sin(a.yaw), dz = (float) -Math.cos(a.yaw);
            a.vx = dx * TACKLE_SPEED; a.vz = dz * TACKLE_SPEED; a.pvx = a.vx; a.pvz = a.vz;
            a.sprinting = true; a.hasGoal = false;
            if (a.stuckTime > 0.12f) { r.smash(a); if (a.stuckTime > 0.5f) { a.burstLeft = 0; a.staggerUntil = r.time + 0.9f; r.quake(a.x, a.z, 18, 0.3f); r.emitSound(a.x, a.y + 1, a.z, 40, "door_break", a.id, false); } }
            for (Actor o : r.nearby(a.x, a.z, a.radius + 1.6f)) {
                if (o == a || o.dead || o.infected() && !o.turned || o.ragdoll != null || r.time < o.flingUntil || Math.abs(o.y - a.y) > 1.7f) continue;
                float ox = o.x - a.x, oz = o.z - a.z, od = (float) Math.sqrt(ox * ox + oz * oz);
                if (od > a.radius + o.radius + 0.45f || ox * dx + oz * dz < -0.1f) continue;
                r.tackle(a, o, dx, dz, TACKLE_DAMAGE);
                tackled++;
                // it runs on through whoever it hit, a little slower, and pulls up
                a.suppressUntil = Math.min(a.suppressUntil, r.time + 0.35f);
            }
            return;
        }
        boolean seen = t != null;
        a.yaw = turn(a.yaw, (float) Math.atan2(-(px - a.x), -(pz - a.z)), dt * 3.2f);
        if (seen && d > TACKLE_MIN && d < TACKLE_MAX && a.specialTimer <= 0 && Math.abs(t.y - a.y) < 1.2f && r.world.nav.clear(a.x, a.y, a.z, t.x, t.z, false)) {
            a.specialTimer = TACKLE_EVERY;
            r.telegraph(a, "tackle", TACKLE_SET);
            a.suppressUntil = r.time + TACKLE_SET + TACKLE_SECONDS;
            a.burstLeft = 3;
            stop(a);
            return;
        }
        if (seen && d < FIST_REACH + a.radius && a.fireCooldown <= 0) {
            a.fireCooldown = FIST_EVERY;
            if (a.anim != null) a.anim.play("giant_smash", 0);
            r.melee(a, t, ar.melee() * r.difficultyDamage());
            // 1.1: its fist sends whoever it hits straight back six feet, flat
            float bx = t.x - a.x, bz = t.z - a.z, bl = Math.max(0.05f, (float) Math.sqrt(bx * bx + bz * bz));
            r.punchBack(a, t, bx / bl, bz / bl);
        }
        // relentless: a walk when it is close or has lost sight, a heavy run to close a distance
        if (!seen || d > 1.5f) { goTo(r, a, px, pz, seen && d > 9 ? ar.runSpeed() : ar.speed() * 1.15f, dt); if (a.stuckTime > 0.3f) r.smash(a); } else stop(a);
    }

    // ------------------------------------------------------------------ 1.0: the Crawler
    /** The tongue's reach (metres) and how often; the leap's reach and how often; how high a ceiling it will take to; how tall a wall it will go straight up. */
    public static float TONGUE_MIN = 3.5f, TONGUE_MAX = 11f, TONGUE_EVERY = 6.5f, TONGUE_WINDUP = 0.5f, POUNCE_MIN = 2.2f, POUNCE_MAX = 7f, POUNCE_EVERY = 4.2f, CLING_MAX = 7f, SCALE_MAX = 6f;
    public static int clings, drops;

    /**
     * Skinless, on all fours, and blind: everything it does, it does at something it heard. At rest it takes to the
     * ceiling where there is one and hangs there, turning its head. Roused, it comes - along the ceiling, down a wall, across
     * the floor, straight up anything in its way - and from a few metres off it does one of two things: it throws its
     * tongue at where the noise was, and whoever is there is dragged to it; or it leaps there itself.
     */
    private static void crawler(Raid r, Actor a, Arch ar, boolean roused, float dt) {
        // how it is carried: eased toward the ceiling it is on, or the wall it is going up, so it turns over rather than snapping
        a.ceilT += ((a.cling ? 1 : 0) - a.ceilT) * Math.min(1, dt * 6);
        a.wallT += ((a.mantling() ? 1 : 0) - a.wallT) * Math.min(1, dt * 9);
        if (a.mantling()) return;
        if (r.time < a.staggerUntil) { stop(a); if (a.cling) drop(r, a); return; }
        if (r.telegraphing(a, "tongue") || r.telegraphing(a, "pounce")) { stop(a); a.yaw = turn(a.yaw, (float) Math.atan2(-(a.memX - a.x), -(a.memZ - a.z)), dt * 7); return; }
        if (a.burstLeft == 4) { a.burstLeft = 0; r.tongue(a, a.memX, a.memY + 1.1f, a.memZ); a.fireCooldown = 0.7f; return; }
        if (a.burstLeft == 5) {
            // the leap: thrown at the place, in an arc that comes down on it
            a.burstLeft = 0;
            if (a.cling) drop(r, a);
            float dx = a.memX - a.x, dz = a.memZ - a.z, l = Math.max(0.3f, (float) Math.sqrt(dx * dx + dz * dz)), tt = 0.55f;
            a.vx = dx / tt; a.vz = dz / tt; a.pvx = a.vx; a.pvz = a.vz; a.vy = a.onGround ? 4.6f : 0.5f; a.onGround = false;
            a.pounceUntil = r.time + tt + 0.25f;
            r.pounces++;
            return;
        }
        if (r.time < a.pounceUntil) {
            // in the air: whatever it lands on, it is on
            a.vx = a.pvx; a.vz = a.pvz;
            for (Actor o : r.nearby(a.x, a.z, 2f)) {
                if (o == a || o.dead || o.infected() || Math.abs(o.y - a.y) > 1.5f || a.dist(o) > a.radius + o.radius + 0.5f) continue;
                a.pounceUntil = -99; a.fireCooldown = BLOW_EVERY * 0.6f;
                if (a.anim != null) a.anim.play("inf_attack", 0);
                r.melee(a, o, ar.melee() * 1.4f * r.difficultyDamage());
                if (!o.isPlayer) r.gore.ragdolls.knockDown(o, a.vx, a.vz, 70, 2.0f);
                break;
            }
            return;
        }
        Actor near = touched(r, a);
        if (near != null && a.fireCooldown <= 0) {
            if (a.cling) { drop(r, a); return; }
            a.yaw = (float) Math.atan2(-(near.x - a.x), -(near.z - a.z));
            a.fireCooldown = BLOW_EVERY * 0.7f;
            if (a.anim != null) a.anim.play("inf_attack", 0);
            r.melee(a, near, ar.melee() * r.difficultyDamage());
            stop(a);
            return;
        }
        if (roused) {
            if (!"investigate".equals(a.state)) setState(a, "investigate");
            float d = a.distXZ(a.memX, a.memZ), dy = a.memY - a.y;
            boolean fresh = r.time - a.memTime < 1.6f, line = fresh && Physics.openness(r.world, a.x, a.y + 0.6f, a.z, a.memX, a.memY + 1.1f, a.memZ) > 0.6f;
            // from a distance: the tongue. Nearer: the leap. Only at a noise that is new - it does not throw itself at silence
            if (line && d > TONGUE_MIN && d < TONGUE_MAX && a.specialTimer <= 0 && Math.abs(dy) < CLING_MAX + 1) { a.specialTimer = TONGUE_EVERY; r.telegraph(a, "tongue", TONGUE_WINDUP); a.burstLeft = 4; stop(a); return; }
            if (line && d > POUNCE_MIN && d < POUNCE_MAX && a.fireCooldown <= 0 && a.specialTimer < TONGUE_EVERY - 1.5f && (a.cling || Math.abs(dy) < 1.5f)) { a.fireCooldown = POUNCE_EVERY; r.telegraph(a, "pounce", 0.35f); a.burstLeft = 5; stop(a); return; }
            if (d > 1.3f) {
                goTo(r, a, a.memX, a.memZ, ar.runSpeed(), dt);
                a.sprinting = true;
                // a wall in the way is a way up
                if (!a.cling && a.stuckTime > 0.25f && scale(r, a)) return;
            } else {
                stop(a);
                if (a.cling) drop(r, a);
                a.headLookTarget = (float) Math.sin((a.stateTime + a.id) * 1.9f) * 1.2f;
            }
            return;
        }
        // nothing heard: it finds a ceiling and hangs from it, creeping about up there
        if (!"idle".equals(a.state)) setState(a, "idle");
        a.sprinting = false;
        if (!a.cling && a.onGround) { a.lootTimer -= dt; if (a.lootTimer <= 0) { a.lootTimer = 3 + r.rng.nextFloat() * 4; float c = ceilingAbove(r, a); if (c > 0) { takeTo(r, a, c); return; } } }
        wander(r, a, ar, dt);
        if (a.cling) { a.vx *= 0.55f; a.vz *= 0.55f; }
    }

    /** The underside of the ceiling over a body, if there is one it could hang from (between its own height and CLING_MAX above the floor); or -1. */
    private static float ceilingAbove(Raid r, Actor a) {
        int x = (int) Math.floor(a.x), z = (int) Math.floor(a.z), y0 = (int) Math.floor(a.y + 0.05f);
        for (int y = y0 + 2; y <= y0 + (int) CLING_MAX; y++) {
            if (!r.world.solid(x, y, z)) continue;
            // a ceiling, not a beam: solid over the cells beside it too
            if (!r.world.solid(x + 1, y, z) || !r.world.solid(x - 1, y, z) || !r.world.solid(x, y, z + 1) || !r.world.solid(x, y, z - 1)) return -1;
            return y - y0 >= 3 ? y : -1;
        }
        return -1;
    }

    /** Up onto the ceiling: from here on the body is under it, and stays there until it drops. */
    private static void takeTo(Raid r, Actor a, float ceilY) {
        if (!Physics.boxFree(r.world, a.x, ceilY - a.stand - 0.02f, a.z, a.radius, a.stand)) return;
        a.cling = true; a.y = ceilY - a.stand - 0.02f; a.vy = 0; a.onGround = false; a.hasGoal = false;
        clings++;
    }

    /** It lets go: an ordinary body again, falling. */
    private static void drop(Raid r, Actor a) { if (!a.cling) return; a.cling = false; a.onGround = false; a.vy = -1; a.fallSpeed = 0; drops++; }

    /**
     * One frame of a body on a ceiling (called in place of the ordinary movement). It goes where its mind sent it for as
     * long as there is ceiling to go along and room under it; where the ceiling ends, or something is in the way, it stays
     * where it is (and, if it is after something, lets go).
     */
    public static void clingMove(Raid r, Actor a, float dt) {
        if (a.dead || a.ragdoll != null) { a.cling = false; return; }
        a.vy = 0; a.onGround = false; a.fallSpeed = 0;
        float nx = a.x + a.vx * dt, nz = a.z + a.vz * dt;
        int top = (int) Math.floor(a.y + a.stand + 0.3f);
        boolean roof = r.world.solid((int) Math.floor(nx), top, (int) Math.floor(nz)), room = Physics.boxFree(r.world, nx, a.y, nz, a.radius, a.stand);
        if (roof && room) { a.x = nx; a.z = nz; a.stuckTime = Math.max(0, a.stuckTime - dt); }
        else { a.stuckTime += dt; if ("investigate".equals(a.state) && a.stuckTime > 0.3f || !r.world.solid((int) Math.floor(a.x), top, (int) Math.floor(a.z))) drop(r, a); else { a.hasGoal = false; a.vx = a.vz = 0; } }
        float sp = (float) Math.sqrt(a.vx * a.vx + a.vz * a.vz);
        a.walkPhase += sp * dt * 2.2f;
        if (a.anim != null) { a.onGround = true; a.anim.update(a, dt, false, false, false); a.onGround = false; }
    }

    /** A wall in front of it that it could go straight up: anything up to SCALE_MAX blocks with somewhere to be on top. Starts the climb. */
    private static boolean scale(Raid r, Actor a) {
        float dx = (float) -Math.sin(a.yaw), dz = (float) -Math.cos(a.yaw);
        int feet = (int) Math.floor(a.y + 0.05f);
        for (float reach = a.radius + 0.2f; reach <= a.radius + 0.95f; reach += 0.25f) {
            int fx = (int) Math.floor(a.x + dx * reach), fz = (int) Math.floor(a.z + dz * reach);
            if (fx == (int) Math.floor(a.x) && fz == (int) Math.floor(a.z)) continue;
            for (int up = 2; up <= (int) SCALE_MAX; up++) {
                int top = feet + up;
                if (!r.world.solid(fx, top - 1, fz) || r.world.solid(fx, top, fz)) continue;
                if (!Physics.boxFree(r.world, fx + 0.5f, top, fz + 0.5f, 0.3f, a.stand)) continue;
                if (!Physics.boxFree(r.world, a.x, a.y + 0.1f, a.z, a.radius * 0.9f, up + a.stand * 0.6f)) continue;
                Physics.mantle(a, new float[]{fx + 0.5f, top, fz + 0.5f});
                a.mantleDur = a.mantleT = 0.28f + up * 0.14f;      // it goes up a wall about as fast as it crosses a floor
                a.stuckTime = 0;
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ Butcher
    private static void butcher(Raid r, Actor a, Arch ar, Actor t, float d, float px, float pz, float dt) {
        if (r.time < a.staggerUntil) { stop(a); a.burstLeft = 0; return; }
        if (r.telegraphing(a, "rev")) { stop(a); if (t != null) a.yaw = turn(a.yaw, (float) Math.atan2(-(t.x - a.x), -(t.z - a.z)), dt * 3.5f); return; }
        if (a.burstLeft > 0) { a.burstLeft = 0; sweep(r, a, SAW_REACH, 0.25f, false, "chainsaw_rev"); a.fireCooldown = 1.5f; a.specialTimer = 1.0f; return; }
        if (t != null && d < SAW_REACH - 0.3f && a.fireCooldown <= 0 && a.specialTimer <= 0) { r.telegraph(a, "rev", SAW_WINDUP); a.burstLeft = 1; stop(a); return; }
        // no flank, no pause: straight in, at a run
        if (t == null || d > 1.4f) { goTo(r, a, px, pz, ar.runSpeed(), dt); a.sprinting = true; if (a.stuckTime > 0.3f) r.smash(a); } else stop(a);
    }

    /**
     * A swing at head height across the front: everyone living within reach and in the arc loses their head. A swing
     * that can be ducked (the claws) passes over anyone who is crouched, prone or already on the ground.
     */
    public static void sweep(Raid r, Actor a, float reach, float minCos, boolean duckable, String sound) {
        swings++;
        float fx = (float) -Math.sin(a.yaw), fz = (float) -Math.cos(a.yaw);
        if (a.anim != null) a.anim.play("gladiator".equals(a.archetype) ? "claw_swing" : "butcher".equals(a.archetype) ? "saw_swing" : "inf_attack", 0);
        r.emitSound(a.x, a.y + 1.4f, a.z, 24, sound, a.id, false);
        for (Actor o : new java.util.ArrayList<>(r.nearby(a.x, a.z, reach + 1.5f))) {
            if (o == a || o.dead || o.infected() || Math.abs(o.y - a.y) > 1.6f) continue;
            float dx = o.x - a.x, dz = o.z - a.z, d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d > reach + o.radius || d > 0.3f && (dx * fx + dz * fz) / d < minCos) continue;
            if (duckable && (o.crouch || o.prone || o.crawling || o.ragdoll != null || o.height() < 1.3f)) { ducked++; if (o.isPlayer) r.notice("The claws pass over you.", 0xFFB8C8D0); continue; }
            r.decapitate(a, o);
        }
    }

    // ------------------------------------------------------------------ what a hit does to them
    /**
     * A blow on one of the specials, before it is counted: the Gladiator's plate turns nearly everything that does not
     * come in through the gap in its back, and a hard hit on the Butcher's head while the saw is raised stops the swing.
     * dirX/dirZ is the way the blow was travelling; round says it was a bullet.
     */
    public static float struck(Raid r, Actor v, float dmg, float hitY, float dirX, float dirZ, boolean blast, boolean round) {
        if ("gladiator".equals(v.archetype)) {
            if (blast) return dmg * 0.35f;
            float l = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
            if (l < 1e-3f) return dmg * PLATE * 2;
            // travelling the way it faces = it came in from behind
            float along = (dirX * (float) -Math.sin(v.yaw) + dirZ * (float) -Math.cos(v.yaw)) / l;
            if (along > 0.35f && hitY < v.y + v.height() * 0.85f) { weakHits++; return dmg * WEAK_POINT; }
            deflected++;
            r.spark(v.x - dirX / l * v.radius, hitY, v.z - dirZ / l * v.radius);
            return dmg * PLATE;
        }
        // a round to the head, or anything heavy enough anywhere (a fist is neither)
        if ("butcher".equals(v.archetype) && !blast && r.telegraphing(v, "rev") && (round && hitY > v.y + v.height() * 0.8f || dmg >= 70)) {
            r.cancelTelegraph(v, "rev");
            v.staggerUntil = r.time + SAW_STAGGER; v.specialTimer = SAW_STAGGER + 0.4f; v.burstLeft = 0;
            if (v.anim != null) v.anim.hit((float) Math.atan2(dirX, dirZ));
            r.emitSound(v.x, v.y + 1.5f, v.z, 20, "chainsaw_stall", v.id, false);
            if (v.anim != null) v.anim.play("saw_stall", Math.min(1.4f, SAW_STAGGER * 0.7f));
            interrupted++;
        }
        return dmg;
    }
}
