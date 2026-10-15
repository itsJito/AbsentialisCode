package absentialis.tools;

import absentialis.character.Ragdoll;
import absentialis.character.Rig;
import absentialis.sydney.BehaviorCoordinator.Outcome;
import absentialis.sydney.BehaviorCoordinator.State;
import absentialis.sydney.SydneyCharacter;
import absentialis.world.World;

/**
 * Regression tests for the Sydney Engine (0.6 brief: "make it measurable"): standing, hit reactions, falls,
 * recovery, joint stability, penetration and per-character CPU cost, each as a seeded physical experiment on a flat
 * floor. Filter: {@code ABS_SELFTEST=sydney}. With {@code ABS_SYDNEY_TRACE=1} every experiment prints its timeline.
 */
final class SydneyTests {
    private SydneyTests() {}

    private static final boolean TRACE = "1".equals(System.getenv("ABS_SYDNEY_TRACE"));
    private static final float DT = 1 / 60f;
    static boolean DIAG;

    /** One body on the test floor. */
    static final class Rigged {
        final Ragdoll r;
        final SydneyCharacter c;
        final World w;
        float time, maxStretch, maxHyper, lowest = 99, minHeight = 99;
        final StringBuilder log = new StringBuilder();
        State last;

        Rigged(String kind, float scale, int missing, int limp) {
            w = SelfTests.flatWorld();
            r = new Ragdoll(scale);
            r.standAt(300.5f, World.GROUND, 300.5f, 0);
            c = SydneyCharacter.attach(r, kind, missing, limp);
        }

        void run(float seconds) {
            for (float t = 0; t < seconds; t += DT) {
                r.update(w, DT);
                time += DT;
                maxStretch = Math.max(maxStretch, r.maxStretch); maxHyper = Math.max(maxHyper, r.maxHyper);
                lowest = Math.min(lowest, r.lowestY() - World.GROUND);
                minHeight = Math.min(minHeight, c.sense.height);
                if (DIAG && time < 1.3f && Math.round(time * 60) % 2 == 0) System.out.println(String.format("      t %.2f %s err(%.3f,%.3f) n %.2f v(%.2f,%.2f,%.2f) h %.3f tilt %.1f feet %d other %d ankY %.3f %.3f pelvisY %.3f", time, c.state(), c.sense.errX, c.sense.errZ, c.sense.errNorm, c.sense.comV[0], c.sense.comV[1], c.sense.comV[2], c.sense.height, Math.toDegrees(c.sense.tilt), c.sense.feet, c.sense.otherContacts, r.y[Ragdoll.LA] - World.GROUND, r.y[Ragdoll.RA] - World.GROUND, r.y[Ragdoll.PELVIS] - World.GROUND));
                if (false) System.out.println(String.format("         hipL %.2f rad %.0f Nm  kneeL %.2f rad %.0f Nm  kneeZ %.3f hipZ %.3f ankZ %.3f neckZ %.3f", c.motors.dbgAngle[5], c.motors.dbgTorque[5], c.motors.dbgAngle[6], c.motors.dbgTorque[6], r.z[Ragdoll.LK] - 300.5f, r.z[Ragdoll.LH] - 300.5f, r.z[Ragdoll.LA] - 300.5f, r.z[Ragdoll.NECK] - 300.5f));
                if (c.state() != last) { last = c.state(); log.append(String.format(" %.2fs %s", time, last)); }
            }
        }

        /** Runs until the body has an outcome (or the time is up). */
        Outcome until(float seconds) {
            for (float t = 0; t < seconds && c.outcome() == Outcome.NONE; t += DT) run(DT);
            return c.outcome();
        }

        /** A shove to the chest: a change of velocity of the whole body's worth of momentum, delivered at the neck. */
        void push(float dvx, float dvz) {
            float m = c.rig.totalMass, l = (float) Math.hypot(dvx, dvz);
            c.hit(Ragdoll.NECK, dvx, 0, dvz, m * l, l * 40);
        }

        float drift() { return (float) Math.hypot(c.sense.com[0] - 300.5f, c.sense.com[2] - 300.5f); }

        String summary() {
            return String.format("%s after %.1f s: height %.2f/%.2f, tilt %.0f deg, drift %.2f m, steps %d, falls %d, tries %d |%s", c.outcome(), time, c.sense.height, c.sense.standHeight,
                    Math.toDegrees(c.sense.tilt), drift(), c.mind.steps, c.mind.falls, c.mind.tries, log);
        }
    }

    static void run(String which, boolean all, SelfTests04.Check t) {
        if (!(all || which.contains("v06") || which.contains("sydney"))) return;
        System.out.println("[Sydney Engine: active ragdoll - standing, balance and stepping, falls, getting up, limbs lost, stability, cost]");

        // ---- standing: an undisturbed body stays on its feet
        Rigged st = new Rigged("human", 1, 0, 0);
        DIAG = "1".equals(System.getenv("ABS_SYDNEY_DIAG")); SydneyCharacter.DEBUG = DIAG;
        st.run(6);
        DIAG = false; SydneyCharacter.DEBUG = false;
        if (TRACE) System.out.println("    stand: " + st.summary());
        t.check("an undisturbed body stands: upright, at standing height, where it was put", st.c.state() == State.STAND && st.c.mind.falls == 0 && st.c.sense.height > st.c.sense.standHeight * 0.9f && st.drift() < 0.15f && Math.toDegrees(st.c.sense.tilt) < 8,
                st.summary());

        // ---- a light push is held by the ankles; a harder one costs a step; a hard one more; all without a fall
        Rigged light = new Rigged("human", 1, 0, 0);
        light.run(0.5f); light.push(0, -0.45f);
        Outcome lo = light.until(5);
        if (TRACE) System.out.println("    light: " + light.summary());
        t.check("a light push is absorbed without a step and the body settles", lo == Outcome.RECOVERED && light.c.mind.steps == 0 && light.c.mind.falls == 0, light.summary());

        Rigged mid = new Rigged("human", 1, 0, 0);
        mid.run(0.5f); mid.push(0, 1.3f);
        DIAG = "1".equals(System.getenv("ABS_SYDNEY_DIAG2")); mid.time = 0;
        Outcome mo = mid.until(6);
        DIAG = false;
        if (TRACE) System.out.println("    step:  " + mid.summary());
        t.check("a harder push is caught by stepping: the body moves its feet under itself and stays up", mo == Outcome.RECOVERED && mid.c.mind.steps >= 1 && mid.c.mind.falls == 0, mid.summary());

        // ---- a blow too hard to catch: it falls, lies, and gets back up
        Rigged hard = new Rigged("human", 1, 0, 0);
        hard.run(0.5f); hard.push(1.5f, -5.5f);
        hard.run(1.2f);
        boolean went = hard.c.mind.falls >= 1 && hard.minHeight < hard.c.sense.standHeight * 0.6f;
        Outcome ho = hard.until(14);
        if (TRACE) System.out.println("    fall:  " + hard.summary());
        t.check("a blow it cannot catch puts it on the ground", went, hard.summary());
        t.check("it gets up again from the ground under its own strength and stands", ho == Outcome.RECOVERED && hard.c.sense.height > hard.c.sense.standHeight * 0.88f, hard.summary());

        // ---- directions: pushes from every side
        int recovered = 0, total = 0, stepped = 0;
        StringBuilder dirs = new StringBuilder();
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            Rigged d = new Rigged("human", 1, 0, 0);
            d.run(0.4f); d.push((float) Math.sin(a) * 1.2f, (float) -Math.cos(a) * 1.2f);
            Outcome o = d.until(11);
            total++;
            if (o == Outcome.RECOVERED) recovered++;
            if (d.c.mind.steps > 0) stepped++;
            dirs.append(String.format(" %d:%s/%d/%d", k * 45, o == Outcome.RECOVERED ? "ok" : o.toString(), d.c.mind.steps, d.c.mind.falls));
        }
        t.check("pushes from all eight directions are survived (on its feet at the end of every one)", recovered == total, recovered + " of " + total + " (direction:result/steps/falls)" + dirs);

        // ---- people and the infected are different bodies
        float humanLimit = limit("human"), infectedLimit = limit("infected");
        Rigged hw = new Rigged("human", 1, 0, 0), iw = new Rigged("infected", 1, 0, 0);
        boolean hHeld = false, iHeld = false;
        for (Rigged g : new Rigged[]{hw, iw}) {
            g.run(0.3f);
            g.c.hit(Ragdoll.RK, 0, 0, -1, 30, 30);
            for (int i = 0; i < 180; i++) { g.run(DT); if (g == hw) hHeld |= g.c.holding; else iHeld |= g.c.holding; }
        }
        t.check("the infected take more to put down than a person does", infectedLimit > humanLimit + 0.2f, String.format("largest chest shove survived on its feet: human %.1f m/s, infected %.1f m/s", humanLimit, infectedLimit));
        t.check("a person puts a hand to a wound; the infected do not", hHeld && !iHeld, "human " + hHeld + ", infected " + iHeld);

        // ---- a body that has lost a leg cannot stand, and knows it
        int legMask = 1 << Rig.L_SHIN;
        Rigged amp = new Rigged("human", 1, legMask, 0);
        Outcome ao = amp.until(10);
        if (TRACE) System.out.println("    leg:   " + amp.summary());
        t.check("with a leg gone it goes down and does not pretend to stand: it is left to crawl", ao == Outcome.CRAWL && amp.c.mind.falls >= 1 && amp.c.mind.tries == 0, amp.summary());
        Rigged arm = new Rigged("human", 1, 1 << Rig.R_FOREARM | 1 << Rig.R_HAND, 0);
        arm.run(0.4f); arm.push(0, 1.1f);
        Outcome ro = arm.until(7);
        t.check("with an arm gone it still keeps its feet", ro == Outcome.RECOVERED && arm.c.mind.falls == 0, arm.summary());

        // ---- death lets go
        Rigged dead = new Rigged("human", 1, 0, 0);
        dead.run(0.5f); dead.push(0.3f, -0.8f); dead.c.die();
        dead.run(9);
        t.check("death lets the muscles go: the body drops, comes to rest and is left to the ragdoll", dead.r.settled && dead.r.driver == null && dead.r.y[Ragdoll.PELVIS] - World.GROUND < 0.35f && dead.r.y[Ragdoll.NECK] - World.GROUND < 0.45f, "settled " + dead.r.settled + ", height " + dead.c.sense.height + String.format(", pelvis %.2f neck %.2f head %.2f knees %.2f %.2f ankles %.2f %.2f", dead.r.y[Ragdoll.PELVIS] - World.GROUND, dead.r.y[Ragdoll.NECK] - World.GROUND, dead.r.y[Ragdoll.HEAD_P] - World.GROUND, dead.r.y[Ragdoll.LK] - World.GROUND, dead.r.y[Ragdoll.RK] - World.GROUND, dead.r.y[Ragdoll.LA] - World.GROUND, dead.r.y[Ragdoll.RA] - World.GROUND));

        // ---- stability over many random blows; penetration; torque limits; cost
        absentialis.engine.util.Rng rng = new absentialis.engine.util.Rng(606);
        float stretch = 0, hyper = 0, low = 0, pen = 0, peak = 0;
        boolean bad = false;
        long steps = 0, nanos = 0;
        int ended = 0, n = 40;
        for (int k = 0; k < n; k++) {
            Rigged g = new Rigged(k % 3 == 2 ? "infected" : "human", 1, 0, 0);
            g.run(0.3f);
            for (int h = 0; h < 3; h++) {
                int part = rng.nextInt(Ragdoll.N);
                double a = rng.nextFloat() * Math.PI * 2;
                float j = 40 + rng.nextFloat() * 420;
                g.c.hit(part, (float) Math.cos(a), rng.nextFloat() * 0.4f - 0.1f, (float) Math.sin(a), j, j * 0.3f);
                g.run(0.4f + rng.nextFloat());
                peak = Math.max(peak, g.c.motors.peakApplied);
            }
            if (g.until(16) != Outcome.NONE) ended++;
            stretch = Math.max(stretch, g.maxStretch); hyper = Math.max(hyper, g.maxHyper); low = Math.min(low, g.lowest); pen = Math.max(pen, g.c.worstPenetration);
            bad |= g.c.invalid;
            steps += g.c.stepsRun; nanos += g.c.nanos;
        }
        float micros = nanos / 1000f / Math.max(1, steps);
        t.check("120 random blows on 40 bodies: no bad numbers, bones keep their length, joints stay inside their limits", !bad && stretch < 1.6f && hyper < 1.5f,
                String.format("worst bone error %.2f voxels, worst hinge violation %.2f voxels, invalid %s", stretch, hyper, bad));
        t.check("nothing sinks into the floor", low > -0.06f && pen < 0.12f, String.format("lowest particle surface %.3f m below the floor, deepest centre %.3f m", -low, pen));
        t.check("every body reaches an end: on its feet, or down for a reason", ended == n, ended + " of " + n);
        t.check("no joint motor exceeds its torque limit", peak <= 1100 * 1.6f * 1.01f, String.format("largest torque applied %.0f N m", peak));
        t.check("control costs under 25 microseconds a step for a character", micros < 25f, String.format("%.2f microseconds per 1/90 s step (sensors, behaviours and motors) over %d steps", micros, steps));
        inTheGame(t);
    }

    /** Sydney inside a raid: who gets it, how a body comes back to animation, what death does. */
    private static void inTheGame(SelfTests04.Check t) {
        absentialis.persistence.Profile p = new absentialis.persistence.Profile();
        absentialis.scene.Raid r = SelfTests.raid(p);
        r.populate = false; r.freezeAI = true;
        r.actors.removeIf(a -> !a.isPlayer);
        r.player.x = 300; r.player.z = 300; r.player.y = World.GROUND;
        absentialis.entity.Actor man = r.debugSpawn("raiders", 305, 300, World.GROUND), far = r.debugSpawn("raiders", 300, 380, World.GROUND);
        r.update(0.05f);
        absentialis.scene.RagdollController rc = r.gore.ragdolls;
        // a knock-down near the player is Sydney's; one far away is resolved at once by the old ragdoll; the player is never taken while alive
        rc.knockDown(man, 1, 0, 90, 2f);
        rc.knockDown(far, 1, 0, 90, 2f);
        rc.knockDown(r.player, 1, 0, 90, 2f);
        SydneyCharacter sc = SydneyCharacter.of(man.ragdoll);
        // (1.1: every ragdoll is Sydney's - the distant one as well; no body in an operation is a passive ragdoll any more)
        boolean taken = sc != null && sc.alive && "human".equals(sc.kind), farPassive = far.ragdoll != null && SydneyCharacter.of(far.ragdoll) != null && r.passiveBodies == 0, playerLeft = r.player.ragdoll == null;
        t.check("in a raid a body knocked down near the player is driven by Sydney, and so is a distant one (1.1: no passive ragdolls); a blow never takes the living player's body", taken && farPassive && playerLeft,
                "near " + taken + ", far driven " + farPassive + ", player untouched " + playerLeft);
        float upAt = -1, x0 = man.x;
        for (int i = 0; i < 60 * 25 && upAt < 0; i++) { r.update(1 / 60f); if (man.ragdoll == null) upAt = i / 60f; }
        t.check("it gets itself up and animation takes it back where it stands", upAt > 0 && !man.dead && rc.selfRecoveries + rc.recoveries >= 1 && Math.abs(man.y - World.GROUND) < 0.3f && Math.abs(man.x - x0) < 6,
                String.format("back on its feet after %.1f s, %.1f m from where it was hit (by itself: %d)", upAt, Math.abs(man.x - x0), rc.selfRecoveries));
        // a blow that staggers is answered physically
        int before = rc.physicalStaggers;
        absentialis.entity.Actor tough = null;
        for (int k = 0; k < 6 && rc.physicalStaggers == before; k++) {
            tough = r.debugSpawn("raiders", 304 + k, 296 - k, World.GROUND);
            r.update(0.05f);
            tough.hp = tough.maxHp = 5000;
            r.damageActor(tough, r.player, 34 + k * 6, absentialis.gameplay.items.Content.def("ammo.rifle"), "torso", tough.x, tough.y + 1.2f, tough.z);
        }
        boolean staggered = rc.physicalStaggers > before && tough != null && SydneyCharacter.of(tough.ragdoll) != null;
        float back = -1;
        for (int i = 0; i < 60 * 25 && back < 0 && tough != null; i++) { r.update(1 / 60f); if (tough.ragdoll == null) back = i / 60f; }
        t.check("a blow that staggers is answered by the body, not by an animation - and the character carries on afterwards", staggered && back > 0 && !tough.dead,
                "physical staggers " + (rc.physicalStaggers - before) + String.format(", back in animation after %.1f s", back));
        // death: the same body, muscles letting go; the player's too
        absentialis.entity.Actor victim = r.debugSpawn("raiders", 303, 304, World.GROUND);
        r.update(0.05f);
        r.damageActor(victim, r.player, 900, absentialis.gameplay.items.Content.def("ammo.rifle"), "torso", victim.x, victim.y + 1.2f, victim.z);
        SydneyCharacter vc = SydneyCharacter.of(victim.ragdoll);
        boolean dying = victim.dead && vc != null && !vc.alive;
        for (int i = 0; i < 60 * 10; i++) r.update(1 / 60f);
        boolean npcRest = victim.ragdoll != null && victim.ragdoll.settled && victim.ragdoll.driver == null;
        r.god = false; r.deathCam = 12f; r.deathHold = true;
        if (r.player.anim == null) r.player.anim = new absentialis.character.Animator();
        r.damageActor(r.player, null, 9000, null, "torso", r.player.x, r.player.y + 1.2f, r.player.z);
        boolean playerBody = false, seen = false;
        for (int i = 0; i < 60 * 14; i++) {
            if (!seen && r.player.dead && r.player.ragdoll != null) { seen = true; SydneyCharacter pc = SydneyCharacter.of(r.player.ragdoll); playerBody = pc != null && !pc.alive; }
            r.update(1 / 60f);
        }
        t.check("death gives the body to Sydney only to let go: it drops and comes to rest as a corpse - the player's included, and only then", dying && playerBody && npcRest && r.player.ragdoll.settled,
                "npc dying under Sydney " + dying + ", player on death " + playerBody + ", npc at rest " + npcRest + ", player settled " + (r.player.ragdoll != null && r.player.ragdoll.settled));
    }

    /** The largest chest shove (m/s of whole-body momentum, from the front) this kind of body stays on its feet through. */
    private static float limit(String kind) {
        float best = 0;
        for (float v = 0.6f; v <= 6f; v += 0.3f) {
            Rigged g = new Rigged(kind, 1, 0, 0);
            g.run(0.4f); g.push(0, v);
            Outcome o = g.until(6);
            if (o == Outcome.RECOVERED && g.c.mind.falls == 0) best = v; else break;
        }
        return best;
    }
}
