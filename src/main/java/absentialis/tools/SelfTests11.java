package absentialis.tools;

import absentialis.character.Appearance;
import absentialis.character.CharBuilder;
import absentialis.character.CharModel;
import absentialis.character.Rig;
import absentialis.engine.fx.ParticleSystem;
import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.entity.Survivor;
import absentialis.gameplay.economy.Economy;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.neuralai.integration.Steering;
import absentialis.persistence.Profile;
import absentialis.scene.Raid;
import absentialis.sydney.SydneyCharacter;
import absentialis.world.Block;
import absentialis.world.World;

/**
 * Quality gates for 1.1. Headless, like the rest; `gradlew selftest`, or ABS_SELFTEST=v11 (groups: bugs11 bodies11
 * rules11 ...). Each gate says in its own text what it holds the game to; where 1.1 changed what an older gate
 * expected, the older gate says so.
 */
final class SelfTests11 {
    private SelfTests11() {}

    static void run(String which, boolean all, SelfTests04.Check t) {
        if (all || which.contains("v11") || which.contains("bugs11")) SelfTests.guard(() -> bugs(t));
        if (all || which.contains("v11") || which.contains("bodies11")) SelfTests.guard(() -> bodies(t));
        if (all || which.contains("v11") || which.contains("rules11")) SelfTests.guard(() -> rules(t));
        SelfTests11b.run(which, all, t);
    }

    static final int G = World.GROUND;

    static void run(Raid r, float seconds) { for (int i = 0; i < Math.round(seconds * 60); i++) r.update(1 / 60f); }

    static Raid flat(Profile p) {
        Content.load();
        Raid r = new Raid(SelfTests.flatWorld(), p, new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer);
        return r;
    }

    static Actor spawn(Raid r, String what, float x, float z, float yaw) {
        Actor a = r.debugSpawn(what, x, z, G);
        a.yaw = yaw; a.homeX = x; a.homeZ = z; a.wakeUntil = 1e9f;
        return a;
    }

    /** Clears a box of the shared flat world (gates that build put back what they found: air over the ground). */
    static void clear(World w, int x0, int z0, int x1, int z1, int h) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = G; y < G + h; y++) w.setRaw(x, y, z, Block.AIR);
        w.computeHeights();
    }

    // ------------------------------------------------------------------ the bug list
    private static void bugs(SelfTests04.Check t) {
        System.out.println("[v1.1 bugs: stairs, stamina, steering, the things that were silent or wrong]");
        World w = SelfTests.flatWorld();
        // ---- stairs: a body standing on a flight has room to stand
        int sx = 120, sz = 420;
        w.setRaw(sx, G, sz, Block.STAIRS_CONCRETE); w.setRaw(sx + 1, G, sz, Block.CONCRETE); w.setRaw(sx + 1, G + 1, sz, Block.STAIRS_CONCRETE); w.setRaw(sx + 2, G, sz, Block.CONCRETE); w.setRaw(sx + 2, G + 1, sz, Block.CONCRETE);
        w.computeHeights();
        float px = sx + 0.5f, pz = sz + 0.5f, py = Physics.stairHeight(w, sx, G, sz, px, pz) + 0.004f;
        boolean box = Physics.boxFree(w, px, py, pz, 0.3f, 1.8f), body = Physics.bodyFree(w, px, py, pz, 0.3f, 1.8f);
        Raid rs = flat(new Profile());
        Actor climber = spawn(rs, "survivors", sx - 2.5f, sz + 0.5f, (float) -Math.PI / 2);
        climber.hostileToPlayer = false;
        boolean wentProne = false, crouched = false;
        float top = 0;
        for (int i = 0; i < 60 * 7; i++) {
            w.nav.budget = 8; rs.time += 1 / 60f;
            rs.neuralBody.setMotion(climber.id, new absentialis.neuralai.api.SydneyBridge.MotionIntent(new absentialis.neuralai.api.Vec3(sx + 2.5f, G + 2, sz + 0.5f), null, absentialis.neuralai.api.SydneyBridge.Stance.STAND, absentialis.neuralai.api.SydneyBridge.Locomotion.WALK, false));
            Physics.move(w, climber, 1 / 60f);
            if (climber.y > G + 0.15f && climber.y < G + 1.9f) { wentProne |= climber.prone; crouched |= climber.crouch; }
            top = Math.max(top, climber.y);
        }
        t.check("on a flight of stairs a body has room to stand - measured as a body is moved, not as a plain box - and somebody walking up one stays on their feet all the way",
                !box && body && !wentProne && !crouched && top > G + 1.7f,
                String.format("plain box free %s, body free %s; walked up to %.2f (top %d), prone on the way %s, crouched %s", box, body, top, G + 2, wentProne, crouched));
        clear(w, sx - 4, sz - 2, sx + 4, sz + 2, 4);

        // ---- out of breath: a held sprint does not stutter
        Survivor s = new Survivor();
        Actor runner = new Actor();
        int flips = 0, ran = 0;
        boolean was = true;
        for (int i = 0; i < 60 * 90; i++) {
            boolean sprint = s.canSprint();
            if (sprint != was) { flips++; was = sprint; }
            if (sprint) ran++;
            s.update(runner, 1 / 60f, i / 60f, true, sprint, false, 3f, 0, false, 1f);
        }
        t.check("with the sprint key held and no stamina left, the survivor stops, gets their breath (to " + (int) Survivor.SECOND_WIND + ") and runs again - a few times a minute, not several times a second",
                flips >= 4 && flips <= 40 && ran > 60 * 20, flips + " changes between running and not in 90 s, " + ran / 60 + " s of it spent running");

        // ---- the slide, the stance key
        absentialis.engine.Input in = new absentialis.engine.Input();
        t.check("a slide is on offer half a second into a sprint, and the stance key is Left Ctrl",
                absentialis.scene.RaidScreen.SLIDE_AFTER == 0.5f && in.bindings.get("crouch") == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,
                "slide after " + absentialis.scene.RaidScreen.SLIDE_AFTER + " s; crouch bound to " + absentialis.engine.Input.keyName(in.bindings.get("crouch")));

        // ---- rain can be taken out of the air
        ParticleSystem ps = new ParticleSystem(4096);
        for (int i = 0; i < 300; i++) ps.raindrop(i * 0.01f, 40, 0, 20, 0.5f, 0.5f);
        for (int i = 0; i < 40; i++) ps.snowflake(i * 0.01f, 40, 0, 1.5f, 0.02f);
        ps.smoke(0, 30, 0, 0, 1, 0, 0.4f, 3f, 0.2f, 0.3f);
        int before = ps.count();
        ps.clearPrecipitation();
        t.check("turning the rain off takes what is falling out of the air and leaves everything else in it", before == 341 && ps.count() == 1 && ps.count(ParticleSystem.SMOKE) == 1, before + " particles before, " + ps.count() + " after (the smoke)");

        // ---- music is asked for at a level that can be heard
        absentialis.engine.audio.UltraEngine au = new absentialis.engine.audio.UltraEngine();
        au.buildBank();
        absentialis.engine.audio.UltraEngine.Voice mv = au.loop("music_menu", absentialis.scene.GameApp.MENU_MUSIC_GAIN, absentialis.engine.audio.UltraEngine.MUSIC);
        absentialis.engine.audio.UltraEngine.Voice dv = au.loop("music_deploy", absentialis.scene.GameApp.MENU_MUSIC_GAIN, absentialis.engine.audio.UltraEngine.MUSIC);
        boolean ready = false;
        for (int i = 0; i < 600 && !ready; i++) { try { Thread.sleep(50); } catch (InterruptedException e) { break; } ready = au.streamReady("music_menu") && au.streamReady("music_deploy"); }
        float[] mix = new float[4096];
        float peak = 0;
        for (int b = 0; b < 400; b++) { au.mixBlock(mix); for (float v : mix) peak = Math.max(peak, Math.abs(v)); }
        t.check("the main menu's music and the deployment screen's are in the sound pack, decode, and are played at a level above nothing (they were started at a gain of zero)",
                au.hasStream("music_menu") && au.hasStream("music_deploy") && mv.gain() > 0.5f && dv.gain() > 0.5f && ready && peak > 0.02f,
                String.format("gain %.2f / %.2f, decoded %s, peak of 400 mixed blocks %.3f", mv.gain(), dv.gain(), ready, peak));
        mv.stop(); dv.stop();

        // ---- steering: round the furniture, not over it
        Raid rn = flat(new Profile());
        int bx = 140, bz = 420;
        for (int z = bz - 1; z <= bz + 1; z++) w.setRaw(bx, G, z, Block.CONCRETE);         // a desk, as far as walking is concerned: one high, three long
        w.computeHeights();
        boolean lineClear = w.nav.clear(bx - 3.5f, G, bz + 0.5f, bx + 4.5f, bz + 0.5f, true);
        Actor walker = spawn(rn, "survivors", bx - 3.5f, bz + 0.5f, (float) -Math.PI / 2);
        float highest = 0;
        for (int i = 0; i < 60 * 9; i++) {
            w.nav.budget = 8;
            rn.time += 1 / 60f;
            Steering.goTo(rn, walker, bx + 4.5f, bz + 0.5f, 2.6f, 1 / 60f);
            if (Physics.move(w, walker, 1 / 60f)) walker.stuckTime += 1 / 60f; else walker.stuckTime = Math.max(0, walker.stuckTime - 1 / 60f);
            highest = Math.max(highest, walker.y - G);
        }
        t.check("a block one high in the way is walked round, not jumped onto: a straight line across it is not 'clear', and somebody sent past it stays on the floor",
                !lineClear && highest < 0.3f && walker.distXZ(bx + 4.5f, bz + 0.5f) < 1.2f,
                String.format("line across it clear %s; highest the feet got %.2f m; ended %.1f m from where it was sent", lineClear, highest, walker.distXZ(bx + 4.5f, bz + 0.5f)));
        clear(w, bx - 5, bz - 3, bx + 6, bz + 3, 3);

        // ---- steering: somewhere that cannot be reached is not walked at through the wall
        Raid rw = flat(new Profile());
        int cx = 160, cz = 420;
        for (int x = cx - 2; x <= cx + 2; x++) for (int z = cz - 2; z <= cz + 2; z++) for (int y = G; y < G + 4; y++) if (Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2) w.setRaw(x, y, z, Block.CONCRETE);
        w.computeHeights();
        Actor seeker = spawn(rw, "survivors", cx - 8.5f, cz + 0.5f, (float) -Math.PI / 2);
        float pushed = 0;
        for (int i = 0; i < 60 * 12; i++) {
            w.nav.budget = 8;
            rw.time += 1 / 60f;
            Steering.goTo(rw, seeker, cx + 0.5f, cz + 0.5f, 2.6f, 1 / 60f);
            boolean hit = Physics.move(w, seeker, 1 / 60f);
            if (hit) seeker.stuckTime += 1 / 60f; else seeker.stuckTime = Math.max(0, seeker.stuckTime - 1 / 60f);
            if (i > 60 * 6 && hit) pushed += 1 / 60f;
        }
        float still = (float) Math.sqrt(seeker.vx * seeker.vx + seeker.vz * seeker.vz);
        t.check("sent to a place it cannot get to (a sealed room), a body goes as near as its route takes it and stands there: it does not walk into the wall",
                pushed < 0.5f && still < 0.4f, String.format("%.1f s spent pressed against the wall in the last six; speed at the end %.2f m/s; %.1f m from the room's middle", pushed, still, seeker.distXZ(cx + 0.5f, cz + 0.5f)));
        clear(w, cx - 3, cz - 3, cx + 3, cz + 3, 5);

        // ---- the spin governor
        Raid rg = flat(new Profile());
        Actor spinner = spawn(rg, "walker", 180.5f, 420.5f, 0);
        int breaks0 = Steering.spinBreaks;
        float turned = 0, heldFor = 0;
        for (int i = 0; i < 60 * 8; i++) {
            rg.time += 1 / 60f;
            w.nav.budget = 8;
            // a goal that is always a metre and a half off at right angles to the way it faces: the textbook turning loop
            float gx = spinner.x + (float) -Math.sin(spinner.yaw + 1.7f) * 1.5f, gz = spinner.z + (float) -Math.cos(spinner.yaw + 1.7f) * 1.5f, y0 = spinner.yaw;
            Steering.goTo(rg, spinner, gx, gz, 0.2f, 1 / 60f);
            spinner.vx *= 0.1f; spinner.vz *= 0.1f;
            turned += Math.abs(Steering.wrapPi(spinner.yaw - y0));
            if (rg.time < spinner.holdUntil) heldFor += 1 / 60f;
        }
        t.check("a body that turns and turns without getting anywhere is caught at it: the loop is broken, the body stands a moment, and whoever sent it is told it is stuck",
                Steering.spinBreaks - breaks0 >= 2 && spinner.spins >= 2 && heldFor > 0.8f, String.format("%d loops broken in 8 s of a goal held at its shoulder; %.1f s stood still; %.0f degrees turned in all", Steering.spinBreaks - breaks0, heldFor, Math.toDegrees(turned)));

        // ---- an arm hanging at the side does not flip
        float[] a1 = new float[3], a2 = new float[3], a3 = new float[3];
        Rig.armIk(-0.4f, -10.4f, -0.6f, a1); Rig.armIk(-0.4f, -10.4f, 0.6f, a2); Rig.armIk(0.4f, -10.4f, 0.1f, a3);
        float flip = Math.max(Math.abs(a1[0] - a2[0]), Math.abs(a1[0] - a3[0]));
        t.check("an arm hanging at the side - a hand at the thigh with a pistol in it - bends in one plane as the hand swings under the shoulder (it used to turn half a circle about its own length)",
                flip < 0.5f && Math.abs(a1[1] - a2[1]) < 0.35f, String.format("the arm's turn changes by %.2f rad as the hand passes under the shoulder; its swing by %.2f", flip, Math.abs(a1[1] - a2[1])));

        // ---- foliage: a weed is not a flower
        boolean yellow = false;
        for (int v = 0; v < absentialis.engine.foliage.FoliageMeshes.VARIANTS; v++) {
            float[] m = absentialis.engine.foliage.FoliageMeshes.mesh(absentialis.engine.foliage.FoliageMeshes.WEED, v);
            for (int i = 0; i + 9 < m.length; i += absentialis.engine.foliage.FoliageMeshes.FLOATS) if (m[i + 6] > 0.75f && m[i + 7] > 0.65f && m[i + 8] < 0.4f) yellow = true;
        }
        t.check("the weed that grows in cracked paving has no flower head (a yellow one stood in the middle of streets and read as a flower planted there)", !yellow, "yellow petals on a weed: " + yellow);
    }

    // ------------------------------------------------------------------ bodies
    private static int rows(absentialis.engine.voxel.VoxModel v) { int n = 0; for (int y = 0; y < v.sy; y++) { boolean any = false; for (int x = 0; x < v.sx && !any; x++) for (int z = 0; z < v.sz; z++) if (v.get(x, y, z) != 0) { any = true; break; } if (any) n++; } return n; }
    private static int depth(absentialis.engine.voxel.VoxModel v) { int lo = v.sz, hi = -1; for (int y = 0; y < v.sy; y++) for (int x = 0; x < v.sx; x++) for (int z = 0; z < v.sz; z++) if (v.get(x, y, z) != 0) { lo = Math.min(lo, z); hi = Math.max(hi, z); } return hi - lo + 1; }

    private static void bodies(SelfTests04.Check t) {
        System.out.println("[v1.1 bodies: the two builds, and everything that falls is Sydney's]");
        Content.load();
        Appearance man = Appearance.legacy(1, 0), woman = Appearance.legacy(1, 0);
        man.sex(false); woman.sex(true);
        CharBuilder.Gear gear = new CharBuilder.Gear();
        for (Appearance a : new Appearance[]{man, woman}) { a.set(absentialis.character.CharDefs.Category.SLEEVES, 0); a.set(absentialis.character.CharDefs.Category.HANDS, 0); }
        CharModel mm = CharBuilder.build(man, gear), fm = CharBuilder.build(woman, gear);
        int mw = CharBuilder.width(mm.groups[Rig.L_UPPER_ARM]), fw = CharBuilder.width(fm.groups[Rig.L_UPPER_ARM]), mwr = CharBuilder.width(mm.groups[Rig.R_FOREARM]), fwr = CharBuilder.width(fm.groups[Rig.R_FOREARM]);
        int len = rows(mm.groups[Rig.L_UPPER_ARM]) + rows(mm.groups[Rig.L_FOREARM]) + rows(mm.groups[Rig.L_HAND]), flen = rows(fm.groups[Rig.L_UPPER_ARM]) + rows(fm.groups[Rig.L_FOREARM]) + rows(fm.groups[Rig.L_HAND]);
        int md = depth(mm.groups[Rig.L_UPPER_ARM]), fd = depth(fm.groups[Rig.L_UPPER_ARM]);
        int tw = CharBuilder.width(mm.groups[Rig.TORSO]), ftw = CharBuilder.width(fm.groups[Rig.TORSO]), lw = CharBuilder.width(mm.groups[Rig.L_THIGH]), flw = CharBuilder.width(fm.groups[Rig.L_THIGH]);
        int hw = CharBuilder.width(mm.groups[Rig.HEAD]), fhw = CharBuilder.width(fm.groups[Rig.HEAD]);
        t.check("a man's arm is 4 voxels wide, 12 long and 4 deep; a woman's is 3 wide, as long and as deep - on a body rebuilt to match (torso 6 wide to his 8, legs 3 to his 4, the same head)",
                mw == 4 && mwr == 4 && fw == 3 && fwr == 3 && len == 12 && flen == 12 && md == 4 && fd == 4 && tw == 8 && ftw == 6 && lw - flw == 1 && hw == fhw,
                String.format("arm %d x %d x %d / %d x %d x %d; torso %d / %d; thigh %d / %d; head %d / %d", mw, len, md, fw, flen, fd, tw, ftw, lw, flw, hw, fhw));

        // ---- a body is not handed to physics inside the floor
        absentialis.character.Ragdoll rd = new absentialis.character.Ragdoll(1);
        rd.standAt(200.5f, G - 0.45f, 430.5f, 0);
        float low0 = rd.lowestY(), lift = rd.unbury(SelfTests.flatWorld());
        t.check("a body is never handed to physics already inside the world: it is lifted by as much as its deepest part is buried", low0 < G - 0.2f && lift > 0.2f && rd.lowestY() >= G - 0.06f, String.format("lowest part %.2f m under the floor, lifted %.2f, now %.2f above it", G - low0, lift, rd.lowestY() - G));

        // ---- the Crawler: floored, it comes back onto all fours, on the floor it was on
        Raid r = flat(new Profile());
        r.player.x = 300; r.player.z = 430; r.player.y = G;
        r.freezeAI = true;
        Actor crawler = spawn(r, "crawler", 306, 430, 0), dead = spawn(r, "walker", 306, 436, 0);
        r.update(0.05f);
        r.gore.ragdolls.knockDown(crawler, 1, 0, 95, 0.6f);
        r.gore.ragdolls.knockDown(dead, 1, 0, 150, 0.8f);
        SydneyCharacter cs = SydneyCharacter.of(crawler.ragdoll), ds = SydneyCharacter.of(dead.ragdoll);
        boolean kinds = cs != null && "crawler".equals(cs.kind) && cs.cfg.style == absentialis.sydney.SydneyConfig.CRAWLER && ds != null && ds.cfg.style == absentialis.sydney.SydneyConfig.INFECTED;
        float lowest = 99, upAt = -1, deadUpAt = -1, tallest = 0;
        for (int i = 0; i < 60 * 14; i++) {
            r.update(1 / 60f);
            lowest = Math.min(lowest, crawler.y);
            if (crawler.ragdoll != null) tallest = Math.max(tallest, crawler.ragdoll.y[absentialis.character.Ragdoll.NECK] - G);
            if (upAt < 0 && crawler.ragdoll == null) upAt = r.time;
            if (deadUpAt < 0 && dead.ragdoll == null) deadUpAt = r.time;
        }
        t.check("a Crawler knocked flat does not stand up like a person: it gets its limbs under it and is on all fours again - never upright on the way, never under the floor; and one of the dead gets up as the dead do, not as a person does",
                kinds && upAt > 0 && lowest > G - 0.3f && Math.abs(crawler.y - G) < 0.3f && tallest < 1.25f && deadUpAt > 0 && !crawler.dead,
                String.format("crawler back on all fours after %.1f s, its neck never higher than %.2f m, lowest %.2f, ends at %.2f above the floor; walker up after %.1f s; lifted out of the floor at hand-over: %d", upAt, tallest, lowest - G, crawler.y - G, deadUpAt, r.unburied));

        // ---- somebody put down wounded falls, and the wounded pose takes over where they lie
        Raid rc = flat(new Profile());
        rc.player.x = 300; rc.player.z = 430; rc.player.y = G;
        rc.freezeAI = true;
        Actor man2 = spawn(rc, "military", 305, 430, 0);
        rc.update(0.05f);
        rc.casualty.down(man2, null, "bullet");
        boolean fell = man2.ragdoll != null && rc.gore.ragdolls.collapses == 1;
        float lay = -1;
        for (int i = 0; i < 60 * 8; i++) { rc.update(1 / 60f); if (lay < 0 && man2.ragdoll == null) lay = rc.time; }
        t.check("somebody put down wounded falls - the body is Sydney's from the moment the legs go - and the wounded pose takes it over where it lies, still alive, still down",
                fell && lay > 0.3f && man2.vital == absentialis.entity.Vital.DOWNED && man2.crawling && Math.abs(man2.y - G) < 0.3f && !man2.dead,
                String.format("fell under physics %s; on the ground and handed back after %.1f s; vital %s", fell, lay, man2.vital));

        // ---- every ragdoll is Sydney's; long falls and hard landings
        Raid rf = flat(new Profile());
        rf.player.x = 300; rf.player.z = 430; rf.player.y = G;
        rf.freezeAI = true;
        Actor far = spawn(rf, "raiders", 300, 560, 0), jumper = spawn(rf, "survivors", 304, 430, 0), faller = spawn(rf, "survivors", 310, 430, 0);
        far.hostileToPlayer = false; jumper.hostileToPlayer = false; faller.hostileToPlayer = false;
        jumper.hp = jumper.maxHp = 5000; faller.hp = faller.maxHp = 5000;
        rf.update(0.05f);
        rf.damageActor(far, null, 9999, null, "torso", far.x, far.y + 1, far.z);
        jumper.y = G + 14; jumper.onGround = false;
        faller.y = G + 150; faller.onGround = false;
        boolean inAir = false, kicked = false;
        for (int i = 0; i < 60 * 9; i++) {
            rf.update(1 / 60f);
            if (System.getenv("ABS_DEBUG_FALL") != null && i % 20 == 0) System.out.println("    jumper y " + jumper.y + " vy " + jumper.vy + " ground " + jumper.onGround + " fs " + jumper.fallSpeed + " rag " + (jumper.ragdoll != null) + " tier " + jumper.tier + " | faller y " + faller.y + " air " + faller.airTime + " rag " + (faller.ragdoll != null));
            if (faller.ragdoll != null && faller.ragdoll.y[absentialis.character.Ragdoll.PELVIS] > G + 6) { inAir = true; SydneyCharacter fs = SydneyCharacter.of(faller.ragdoll); if (fs != null && fs.mind.longestAir > 0.3f) kicked = true; }
        }
        boolean playerOk = rf.gore.ragdolls.fallRagdoll(rf.player) && SydneyCharacter.of(rf.player.ragdoll) != null && !rf.player.dead;
        t.check("everything that falls is Sydney's: a death far away, a landing too hard to stand (NPCs go down in a heap), a fall of more than " + (int) Raid.FALL_RAGDOLL_AFTER + " seconds (the body is given over in the air, limbs working) - and that last one for the survivor too",
                far.dead && rf.passiveBodies == 0 && rf.hardLandings >= 1 && rf.gore.ragdolls.fallRagdolls >= 2 && inAir && kicked && playerOk,
                String.format("passive ragdolls %d; hard landings %d; given over mid-fall %d (seen in the air %s, flailing %s); the survivor's own body %s", rf.passiveBodies, rf.hardLandings, rf.gore.ragdolls.fallRagdolls, inAir, kicked, playerOk));
    }

    // ------------------------------------------------------------------ rules
    private static void rules(SelfTests04.Check t) {
        System.out.println("[v1.1 rules: marksmanship by difficulty, the Tyrant's fist, what is and is not for sale]");
        Content.load();
        // ---- marksmanship
        float[] share = new float[3], hits = new float[3];
        for (int diff = 0; diff < 3; diff++) {
            Profile p = new Profile();
            p.difficulty = diff;
            Raid r = flat(p);
            r.player.x = 300; r.player.z = 460; r.player.y = G;
            r.freezeAI = true;
            Actor shooter = spawn(r, "military", 300, 430, 0), target = spawn(r, "raiders", 300, 414, 0);
            target.hp = target.maxHp = 1e7f;
            r.update(0.05f);
            int struck = 0, shots = 0;
            for (int i = 0; i < 400; i++) {
                shooter.weapon.rounds = shooter.weapon.capacity(); shooter.weapon.jammed = false; shooter.weapon.setCondition(100); shooter.fireCooldown = 0; shooter.suppression = 0; shooter.aimPenalty = 1;
                // a fresh man each time: one that has been hit is a body with holes in it, down on the ground, and no fair mark
                r.actors.remove(target); r.neural.remove(target.id, true);
                target = spawn(r, "raiders", 300, 414, 0); target.hp = target.maxHp = 1e6f;
                target.armorDur = 0; target.armorMax = 0; target.armorClass = 0; target.shield = 0; target.helmet = false; target.helmetClass = 0; target.armorArms = target.armorLegs = false;
                float hp0 = target.hp;
                int s0 = r.npcShots;
                r.npcFire(shooter, target, 1 / 60f);
                if (r.npcShots == s0) continue;
                shots++;
                int pc = r.projectiles.size(); float pvx0 = pc > 0 ? r.projectiles.get(0).vz : 0;
                for (int k = 0; k < 14; k++) r.update(1 / 120f);
                if (System.getenv("ABS_DEBUG_AIM") != null && i < 12) System.out.println("    diff " + diff + " wpn " + shooter.weapon.defId + " mode " + shooter.weapon.fireModeName() + " projectiles " + pc + " vz " + pvx0 + " left " + r.projectiles.size() + " dhp " + (hp0 - target.hp) + " tgt " + target.x + "," + target.y + "," + target.z + " rag " + (target.ragdoll != null) + " misses " + r.npcMisses + "/" + r.npcShots);
                if (target.hp < hp0) struck++;
                r.projectiles.clear();
            }
            share[diff] = r.npcShots == 0 ? -1 : r.npcMisses / (float) r.npcShots;
            hits[diff] = shots == 0 ? -1 : struck / (float) shots;
        }
        t.check("people miss by difficulty: half of their aimed rounds on Easy, a quarter on Normal, one in ten on Hardcore - laid off the body on purpose, and the rest laid on it",
                Math.abs(share[0] - 0.50f) < 0.08f && Math.abs(share[1] - 0.25f) < 0.07f && Math.abs(share[2] - 0.10f) < 0.05f && Math.abs(hits[0] - 0.50f) < 0.12f && Math.abs(hits[1] - 0.75f) < 0.12f && hits[2] > 0.8f,
                String.format("laid off: Easy %.0f%%, Normal %.0f%%, Hardcore %.0f%%; rounds that struck a standing man at 16 m: %.0f%% / %.0f%% / %.0f%%", share[0] * 100, share[1] * 100, share[2] * 100, hits[0] * 100, hits[1] * 100, hits[2] * 100));

        // ---- the Tyrant's fist
        Raid rt = flat(new Profile());
        rt.player.x = 300; rt.player.z = 460; rt.player.y = G;
        rt.freezeAI = true;
        Actor tyrant = spawn(rt, "tyrant", 300, 430, 0), man = spawn(rt, "survivors", 301.6f, 430, 0);
        man.hostileToPlayer = false; man.hp = man.maxHp = 5000;
        rt.update(0.05f);
        if (System.getenv("ABS_PUNCH") != null) Raid.PUNCH_SPEED = Float.parseFloat(System.getenv("ABS_PUNCH"));
        rt.punchBack(tyrant, man, 1, 0);
        float x0 = 301.6f, highest = 0;
        for (int i = 0; i < 60 * 5; i++) { rt.update(1 / 60f); if (System.getenv("ABS_DEBUG_PUNCH") != null && i % 10 == 0) System.out.println("    punch t " + i / 60f + " x " + man.x + " rag " + (man.ragdoll != null) + (man.ragdoll != null ? " pelvis " + man.ragdoll.x[absentialis.character.Ragdoll.PELVIS] + " y " + man.ragdoll.y[absentialis.character.Ragdoll.PELVIS] : "")); if (man.ragdoll != null) highest = Math.max(highest, man.ragdoll.y[absentialis.character.Ragdoll.PELVIS] - G); }
        float back = man.x - x0;
        t.check("whoever the Tyrant's fist lands on goes straight back about six feet (1.83 m) and down - along the ground, not up into the air", back > 1.1f && back < 2.9f && highest < 1.35f && rt.punches == 1,
                String.format("came to rest %.2f m back (%.1f ft); the hips never higher than %.2f m", back, back / 0.3048f, highest));

        // ---- the shop
        int ammoSold = 0, powder = 0, ammoKinds = 0;
        for (ItemDef d : Content.ITEMS.values()) {
            if ("ammo".equals(d.category)) { ammoKinds++; if (Economy.shopCategory(d) >= 0) ammoSold++; for (Economy.Trader tr : Economy.TRADERS) { Profile pp = new Profile(); pp.prog.addXp(5_000_000); if (Economy.stock(tr, pp.prog, 100).contains(d)) ammoSold++; } }
            if (d.id.startsWith("mat.gunpowder") && Economy.shopCategory(d) == Economy.SUPPLIES) powder++;
        }
        Profile sp = new Profile();
        sp.starterKit();
        ItemInstance side = sp.loadout.slot("sidearm");
        boolean laser = side != null && side.attachments.get("laser") != null && absentialis.gameplay.weapons.Attachments.LASER_DEFAULT.equals(side.attachments.get("laser").defId);
        t.check("nobody sells ammunition - the shop sells gunpowder (three grades) instead - and the issue pistol comes with the red laser on it", ammoKinds >= 6 && ammoSold == 0 && powder == 3 && laser,
                ammoKinds + " kinds of ammunition, " + ammoSold + " on sale anywhere; " + powder + " grades of gunpowder under Supplies; starting pistol has the laser: " + laser);

        // ---- smoke is the particle system's, and comes from something
        Raid rs = flat(new Profile());
        rs.player.x = 300; rs.player.z = 430; rs.player.y = G;
        rs.smoulder(310.5f, G, 430.5f);
        run(rs, 6);
        int ash = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (rs.world.get(310 + dx, G - 1, 430 + dz) == Block.ASH) ash++;
        t.check("a column of smoke is real smoke from a real place: particles of the Astratto particle system rising off a patch of burnt ground", rs.smoke.size() == 1 && rs.fx.count(ParticleSystem.SMOKE) >= 20 && ash >= 5,
                rs.fx.count(ParticleSystem.SMOKE) + " puffs in the air after 6 s; " + ash + " cells of ash under it");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) rs.world.setRaw(310 + dx, G - 1, 430 + dz, Block.ASPHALT);
    }
}
