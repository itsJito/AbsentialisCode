package absentialis.tools;

import absentialis.character.Ragdoll;
import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemInstance;
import absentialis.neuralai.api.Vec3;
import absentialis.neuralai.cognition.ConfidenceClass;
import absentialis.neuralai.cognition.TargetBelief;
import absentialis.neuralai.runtime.Mind;
import absentialis.persistence.Profile;
import absentialis.scene.Raid;
import absentialis.sim.ZoneState;
import absentialis.world.Block;
import absentialis.world.District;
import absentialis.world.MapData;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.List;

/**
 * Version 0.9 gates. Each section names the line of the 0.9 brief (instructions.md) it covers; the tracker
 * ({@code docs/V0.9_IMPLEMENTATION.md}) points back here. Groups: {@code audio09 ai09 moves09 world09 modes09}, or
 * {@code v09} for all of them.
 */
final class SelfTests09 {
    private SelfTests09() {}

    static final int G = World.GROUND;

    static void run(String which, boolean all, SelfTests04.Check t) {
        if (all || which.contains("v09") || which.contains("audio09")) SelfTests.guard(() -> audio(t));
        if (all || which.contains("v09") || which.contains("ai09")) SelfTests.guard(() -> ai(t));
        if (all || which.contains("v09") || which.contains("moves09")) SelfTests.guard(() -> moves(t));
        if (all || which.contains("v09") || which.contains("world09")) SelfTests.guard(() -> world(t));
        if (all || which.contains("v09") || which.contains("modes09")) SelfTests.guard(() -> modes(t));
    }

    private static void run(Raid r, float seconds) { for (int i = 0; i < Math.round(seconds * 60); i++) r.update(1 / 60f); }

    private static Raid flat() {
        Content.load();
        Raid r = new Raid(SelfTests.flatWorld(), new Profile(), new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer);
        return r;
    }

    private static Actor spawn(Raid r, String what, float x, float z, float yaw) {
        Actor a = r.debugSpawn(what, x, z, G);
        a.yaw = yaw; a.homeX = x; a.homeZ = z; a.wakeUntil = 1e9f;
        return a;
    }

    /** A patch of the shared flat world cleared for a gate, with what was there kept so it can be put back. */
    private static final class Patch {
        final World w; final List<int[]> was = new ArrayList<>();
        Patch(World w, int x0, int z0, int x1, int z1, int h) { this.w = w; for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = G; y < G + h; y++) if (w.get(x, y, z) != Block.AIR) { was.add(new int[]{x, y, z, w.get(x, y, z)}); w.set(x, y, z, Block.AIR); } }
        void set(int x, int y, int z, int id) { was.add(new int[]{x, y, z, w.get(x, y, z)}); w.set(x, y, z, id); }
        void restore() { for (int i = was.size() - 1; i >= 0; i--) { int[] b = was.get(i); w.set(b[0], b[1], b[2], b[3]); } }
    }

    // ------------------------------------------------------------------ "All synthesized audio must be replaced"; glass; fog; rank-up; mercenary
    private static void audio(SelfTests04.Check t) {
        System.out.println("[v0.9 sound and words: nothing is synthesised, a window is one sound, the mist of the hour, a mercenary]");
        absentialis.engine.audio.UltraEngine ue = new absentialis.engine.audio.UltraEngine();
        ue.buildBank();
        int looped = 0;
        for (String n : new String[]{"engine_loop", "engine_diesel", "tyre_roll", "tyre_skid", "chainsaw_loop", "fire_loop"}) if (ue.samples.containsKey(n) && ue.recorded.contains(n)) looped++;
        t.check("no sound the game plays is synthesised: every name the old synthesiser made - world, interface, music and voices alike - is a recording now, long ones as streams, and the pack has no problems",
                ue.synthesised.isEmpty() && ue.pack.problems.isEmpty() && looped == 6 && ue.hasStream("music_hideout") && ue.hasStream("music_tension") && ue.pack.online.size() >= 150,
                ue.synthesised.size() + " still synthesised " + ue.synthesised + "; " + ue.recorded.size() + " recorded names, " + ue.streams.size() + " streams, " + ue.pack.online.size() + " files found online, " + looped + "/6 loops; problems " + ue.pack.problems);
        // ---- glass: a shopfront going is one break and the shards landing are heard once or twice, not thirty times
        Raid r = flat();
        int before = r.soundSequenceCount("glass_break"), tBefore = r.soundSequenceCount("glass_tinkle");
        for (int i = 0; i < 12; i++) r.glass.pane(400 + (i % 4), G + 1 + i / 4, 300.5f, 0);
        run(r, 0.3f);
        for (int i = 0; i < 40; i++) { r.glass.shard(400 + (i % 5) * 0.8f, G, 301 + (i % 3) * 0.5f); if (i % 8 == 7) run(r, 0.1f); }
        run(r, 2.5f);
        int breaks = r.soundSequenceCount("glass_break") - before, tinkles = r.soundSequenceCount("glass_tinkle") - tBefore;
        // a second window, somewhere else, is its own sound
        r.glass.pane(430, G + 1, 300.5f, 0);
        run(r, 0.4f);
        int second = r.soundSequenceCount("glass_break") - before;
        t.check("a window breaking is one sound: twelve panes and forty shards are heard as one break and one or two settles; a window elsewhere is another",
                breaks == 1 && tinkles >= 1 && tinkles <= 2 && second == 2 && r.glass.panesAsked == 13 && r.glass.shardsAsked == 40,
                r.glass.panesAsked + " panes -> " + second + " breaks; " + r.glass.shardsAsked + " shards -> " + tinkles + " settles");
        // ---- fog by the hour
        float dawn = ZoneState.mistAt(6.5), mid = ZoneState.mistAt(14), night = ZoneState.mistAt(1), dusk = ZoneState.mistAt(20);
        Profile pm = new Profile(), pa = new Profile();
        pm.zone.hour = 6.5; pa.zone.hour = 14;
        Raid rm = new Raid(SelfTests.flatWorld(), pm, new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        Raid ra = new Raid(SelfTests.flatWorld(), pa, new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        t.check("fog is thicker in the morning and at night: the mist peaks just after first light, holds through the night, is gone by the afternoon - and brings the fog bank nearer",
                dawn > 0.95f && night > 0.6f && mid == 0 && dusk > 0.1f && dusk < night && rm.fogNear() < ra.fogNear() * 0.75f && rm.fogSight() < ra.fogSight(),
                String.format("mist 06:30 %.2f, 14:00 %.2f, 20:00 %.2f, 01:00 %.2f; fog bank at %.0f m in the morning, %.0f m in the afternoon", dawn, mid, dusk, night, rm.fogSight(), ra.fogSight()));
        // ---- "Any reference to Mercenarie should be made mercenary"
        Actor merc = new Actor(); merc.faction = "mercenaries"; merc.archetype = "rifleman";
        Actor sol = new Actor(); sol.faction = "military"; sol.archetype = "rifleman";
        t.check("one of the Mercenaries is a mercenary (it was \"a mercenarie rifleman\": the last letter cut off the faction's name)",
                "mercenary".equals(ZoneState.one("mercenaries")) && Raid.describe(merc).equals("a mercenary rifleman") && Raid.describe(sol).equals("a military rifleman") && "bandit".equals(ZoneState.one("raiders")) && "Mercenary".equals(Raid.displayName(merc)),
                Raid.describe(merc) + "; " + Raid.describe(sol) + "; shown as " + Raid.displayName(merc));
    }

    // ------------------------------------------------------------------ circles, cover, sight through blocks, searching a house
    private static void ai(SelfTests04.Check t) {
        System.out.println("[v0.9 AI: nobody orbits a point, a crouched body behind a block is not seen, cover and hiding, a house is searched room by room]");
        Raid r = flat();
        World w = r.world;
        Patch pt = new Patch(w, 596, 296, 640, 330, 5);
        try {
            // ---- "NPC ai will sometimes run around in circles": a point a metre to the side of a body at a sprint is reached, not orbited
            Actor a = spawn(r, "military", 600.5f, 300.5f, 0);
            a.yaw = 0; a.onGround = true;
            float tx = 601.6f, tz = 300.9f, best = 9;
            int reachedAt = -1;
            for (int i = 0; i < 240; i++) {
                absentialis.neuralai.integration.Steering.goTo(r, a, tx, tz, 6.5f, 1 / 60f);
                Physics.move(w, a, 1 / 60f);
                float d = a.distXZ(tx, tz);
                best = Math.min(best, d);
                if (d < 0.5f && reachedAt < 0) reachedAt = i;
                if (reachedAt >= 0) break;
            }
            // and one that is behind it: it turns and goes, it does not swing wide
            Actor b = spawn(r, "military", 610.5f, 300.5f, 0);
            float widest = 0;
            int backAt = -1;
            for (int i = 0; i < 300 && backAt < 0; i++) {
                absentialis.neuralai.integration.Steering.goTo(r, b, 610.5f, 306.5f, 6.5f, 1 / 60f);
                Physics.move(w, b, 1 / 60f);
                widest = Math.max(widest, Math.abs(b.x - 610.5f));
                if (b.distXZ(610.5f, 306.5f) < 0.6f) backAt = i;
            }
            r.actors.remove(a); r.actors.remove(b);
            t.check("nobody runs in circles: a place a metre to one side of a sprinting body is stepped to (it was orbited at the radius of the turn), and a place behind it is turned for and reached without a wide loop",
                    reachedAt >= 0 && reachedAt < 90 && backAt >= 0 && backAt < 150 && widest < 1.4f,
                    "reached the side point in " + reachedAt + " frames (closest " + String.format("%.2f", best) + " m); the point behind in " + backAt + " frames, swinging " + String.format("%.2f", widest) + " m wide");

            // ---- "When a player is crouched and behind a block, some NPCs can still detect them"
            for (int x = 618; x <= 622; x++) pt.set(x, G, 310, Block.CONCRETE);              // a wall one block high, five long
            Actor p = r.player;
            p.x = 620.5f; p.y = G; p.z = 310.5f + 1.0f; p.crouch = true; p.vx = p.vz = 0;
            Actor s = spawn(r, "raiders", 620.5f, 301.5f, (float) Math.PI);                   // nine metres off, looking straight at the wall
            s.hostileToPlayer = true;
            run(r, 0.2f);
            Mind m = r.neural.mind(s.id);
            var self = r.neural.world.self(s.id);
            double hidden = m == null ? -1 : r.neural.perception.exposure(m, p.id, self.sightRange(), self.arcFarDeg(), false);
            // from an angle, past the end of the wall's line but still behind it
            s.x = 626.5f; s.z = 303.5f; s.yaw = (float) Math.atan2(-(p.x - s.x), -(p.z - s.z));
            double angled = r.neural.perception.exposure(m, p.id, self.sightRange(), self.arcFarDeg(), false);
            p.crouch = false;
            s.x = 620.5f; s.z = 301.5f; s.yaw = (float) Math.PI;
            double standing = r.neural.perception.exposure(m, p.id, self.sightRange(), self.arcFarDeg(), false);
            // furniture hides too: crouched behind a desk
            for (int x = 618; x <= 622; x++) w.set(x, G, 310, Block.DESK);
            p.crouch = true;
            double desk = r.neural.perception.exposure(m, p.id, self.sightRange(), self.arcFarDeg(), false);
            // a line that only clips the corner of a block is still stopped by it
            for (int x = 618; x <= 622; x++) w.set(x, G, 310, Block.AIR);
            pt.set(630, G, 320, Block.CONCRETE); pt.set(630, G + 1, 320, Block.CONCRETE);
            boolean corner = Physics.sight(w, 629.2f, G + 1.5f, 319.95f, 631.2f, G + 1.5f, 321.05f);
            boolean open = Physics.sight(w, 629.2f, G + 1.5f, 318.5f, 631.2f, G + 1.5f, 318.9f);
            r.actors.remove(s);
            t.check("walls and objects obstruct vision: crouched behind a one-block wall the survivor is not seen from in front or from an angle, standing up behind it they are; a desk hides a crouched body; a line through the corner of a block is stopped",
                    hidden == 0 && angled == 0 && standing > 0.2 && desk == 0 && !corner && open,
                    String.format("exposure crouched %.2f, from an angle %.2f, standing %.2f, behind a desk %.2f; corner line clear %s, open line clear %s", hidden, angled, standing, desk, corner, open));

            // ---- "instead of prioritizing finding cover/hiding": with no cover point, out of sight of the threat
            for (int z = 314; z <= 318; z++) for (int y = G; y < G + 3; y++) pt.set(606, y, z, Block.BRICK_RED);     // a wall to get behind
            Actor c = spawn(r, "survivors", 603.5f, 316.5f, 0);
            run(r, 0.1f);
            Mind cm = r.neural.mind(c.id);
            Vec3 threat = new Vec3(612.5, G, 316.5), from = new Vec3(608.5, G, 316.5);
            Vec3 spot = cm == null ? null : r.neural.hideSpot(cm, from, threat, 14);
            boolean unseen = spot != null && !Physics.sight(w, 612.5f, G + 1.5f, 316.5f, (float) spot.x(), (float) spot.y() + 1.0f, (float) spot.z());
            r.actors.remove(c);
            t.check("with no cover to be had a body goes to ground out of sight: the place chosen cannot be seen from where the threat is believed to be, and is away from it", spot != null && unseen && spot.distanceXZ(threat) > from.distanceXZ(threat) - 1,
                    spot == null ? "no spot" : String.format("spot %.1f, %.1f - %.1f m from the threat, hidden %s", spot.x(), spot.z(), spot.distanceXZ(threat), unseen));
        } finally { pt.restore(); }

        // ---- "if they suspect a player or character is in a house they will dynamically search it"
        World city = SelfTests06.world(1337L);
        Raid rc = new Raid(city, new Profile(), new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        rc.populate = false;
        MapData.Building house = null;
        for (MapData.Building bd : city.map.buildings) if (bd.valid && bd.floors >= 2 && bd.rooms.size() >= 6 && bd.rooms.size() <= 16) { house = bd; break; }
        int rooms = 0, upstairs = 0, picked = 0;
        boolean isHouse = false;
        String name = "";
        if (house != null) {
            name = house.archetype;
            Vec3 at = new Vec3(house.entryX + 0.5, house.entryY, house.entryZ + 0.5);
            var grid = new absentialis.neuralai.spatial.SearchGrid(rc.neural.world, rc.neural.cfg, 77);
            grid.seed(new TargetBelief(77, ConfidenceClass.LAST_SEEN, at, Vec3.ZERO, 3, 0.6, 0, 0, at, "sight", 0, 0), 1);
            isHouse = grid.house != null;
            rooms = grid.rooms;
            for (var cell : grid.cells()) if (absentialis.neuralai.spatial.SearchGrid.isRoom(cell.id) && cell.centre.y() > house.entryY + 2) upstairs++;
            // somebody asked for the best place to look is sent to a room
            Actor seeker = rc.debugSpawn("military", house.entryX + 0.5f, house.entryZ + 0.5f, house.entryY);
            long region = seeker == null ? -1 : grid.bestRegionFor(seeker.id, TargetBelief.NONE, 2);
            if (region >= 0 && absentialis.neuralai.spatial.SearchGrid.isRoom(region)) picked = 1;
        }
        t.check("a house is searched as a house: a target lost at a building's door makes every room of it, on every floor, a place to look - and the first place anyone is sent is a room", house != null && isHouse && rooms >= 6 && upstairs >= 2 && picked == 1,
                house == null ? "no such building in the city" : name + ": " + rooms + " of " + house.rooms.size() + " rooms are search regions, " + upstairs + " of them upstairs; first region is a room: " + (picked == 1));
        // ---- a route to a floor, not to a column
        int[] up = null;
        MapData.Room top = null;
        if (house != null) for (MapData.Room rm : house.rooms) if (rm.floor >= 1 && !rm.circulation) { top = rm; break; }
        if (top != null) {
            int sx = -1, sz = -1;
            for (int x = top.x0; x <= top.x1 && sx < 0; x++) for (int z = top.z0; z <= top.z1 && sx < 0; z++) if (city.nav.stand(x, top.y + 1, z, true)) { sx = x; sz = z; }
            if (sx >= 0) up = city.nav.path(house.entryX + 0.5f, house.entryY, house.entryZ + 0.5f, sx + 0.5f, top.y + 1, sz + 0.5f, 4000, true);
        }
        int endY = up == null ? -1 : up[up.length - 2];
        t.check("navigation goes to a floor: a route to a room upstairs ends upstairs (it used to end at the first cell reached in that column, underneath it)", top != null && up != null && Math.abs(endY - (top.y + 1)) <= 1,
                top == null ? "no upstairs room" : "route of " + (up == null ? 0 : up.length / 3) + " cells ends at y " + endY + ", the room's floor is at y " + (top.y + 1));
    }

    // ------------------------------------------------------------------ ledges, takedowns, bodies, corpses' equipment
    private static void moves(SelfTests04.Check t) {
        System.out.println("[v0.9 bodies: pulling up onto a ledge, the silent takedown, feeding without a jerk, bodies that stay under physics, equipment on the dead]");
        Raid r = flat();
        World w = r.world;
        Patch pt = new Patch(w, 560, 340, 600, 372, 7);
        try {
            // ---- "pull themselves up onto ledges that are only a block taller than them"
            for (int h = 2; h <= 4; h++) for (int y = G; y < G + h; y++) for (int z = 346; z <= 348; z++) pt.set(560 + h * 6, y, z, Block.CONCRETE);   // walls 2, 3 and 4 high at x = 572, 578, 584
            Actor p = r.player;
            float[][] found = new float[3][];
            for (int h = 2; h <= 4; h++) { p.x = 560 + h * 6 - 0.6f; p.y = G; p.z = 347.5f; found[h - 2] = Physics.ledge(w, p, 1, 0); }
            p.x = 572 - 0.6f; p.y = G; p.z = 347.5f;
            Physics.mantle(p, found[0]);
            int frames = 0;
            while (Physics.mantleStep(w, p, 1 / 60f) && frames < 400) frames++;
            boolean onTop = Math.abs(p.y - (G + 2)) < 0.01f && Math.abs(p.x - 572.5f) < 0.01f && p.onGround;
            // people's routes use it; the dead's do not
            for (int z = 340; z <= 372; z++) for (int y = G; y < G + 2; y++) if (z < 346 || z > 348) pt.set(590, y, z, Block.CONCRETE);     // a two-high wall right across, no way round inside the patch
            for (int y = G; y < G + 2; y++) for (int z = 346; z <= 348; z++) pt.set(590, y, z, Block.CONCRETE);
            int[] man = w.nav.path(587.5f, G, 356.5f, 593.5f, 356.5f, 1500, true), dead = w.nav.path(587.5f, G, 356.5f, 593.5f, 356.5f, 1500, false);
            boolean manOver = man != null && man[man.length - 3] == 593, deadOver = dead != null && dead[dead.length - 3] == 593;
            int climbs = 0, deadClimbs = 0;
            if (man != null) for (int i = 3; i < man.length; i += 3) if (man[i + 1] - man[i - 2] >= 2) climbs++;
            if (dead != null) for (int i = 3; i < dead.length; i += 3) if (dead[i + 1] - dead[i - 2] >= 2) deadClimbs++;
            // and a person sent across actually goes over
            Actor npc = spawn(r, "military", 587.5f, 356.5f, 0);
            int before = r.mantles;
            float end = 0;
            for (int i = 0; i < 600; i++) { absentialis.neuralai.integration.Steering.goTo(r, npc, 593.5f, 356.5f, 4.5f, 1 / 60f); if (!Physics.mantleStep(w, npc, 1 / 60f)) Physics.move(w, npc, 1 / 60f); r.time += 1 / 60f; end = npc.x; if (npc.x > 592.5f && npc.onGround) break; }
            r.actors.remove(npc);
            t.check("a ledge two or three blocks up is pulled up onto - by the survivor and by people, whose routes use it - and one four blocks up is not; the dead have no such route",
                    found[0] != null && found[1] != null && found[2] == null && onTop && manOver && climbs >= 1 && deadClimbs == 0 && r.mantles > before && end > 592.5f,
                    "ledge found at 2: " + (found[0] != null) + ", 3: " + (found[1] != null) + ", 4: " + (found[2] != null) + "; up in " + frames + " frames; a person's route crosses the wall with " + climbs + " pull-up(s), an infected's has " + deadClimbs + " (it goes round: " + deadOver + "); the soldier ended at x " + String.format("%.1f", end));

            // ---- "silently take down an NPC without alerting others ... Bonus XP for stealth kills"
            // two men of the same crew, a wall between them: the survivor comes up behind the nearer one. What the other could learn of it, he could only learn by ear
            for (int x = 562; x <= 571; x++) for (int y = G; y < G + 3; y++) pt.set(x, y, 355, Block.CONCRETE);
            p.x = 566.5f; p.y = G; p.z = 361.9f; p.yaw = 0; p.crouch = true; p.onGround = true; p.vx = p.vz = 0;       // already behind them when they come into being: nobody has had a glimpse of anything
            Actor guard = spawn(r, "raiders", 566.5f, 360.5f, 0), mate = spawn(r, "raiders", 566.5f, 350.5f, 0);
            guard.hostileToPlayer = true; mate.hostileToPlayer = true; mate.squad = guard.squad;
            run(r, 0.3f);
            guard.x = 566.5f; guard.z = 360.5f; guard.yaw = 0; guard.vx = guard.vz = 0; mate.x = 566.5f; mate.z = 350.5f; mate.yaw = 0;
            p.x = 566.5f; p.y = G; p.z = 361.9f; p.yaw = 0; p.crouch = true; p.onGround = true;
            boolean noKnife = r.takedownTarget() == null;
            ItemInstance knife = new ItemInstance("melee.knife");
            absentialis.gameplay.inventory.InventoryService.equip(knife, r.profile.loadout, "melee");
            Actor offered = r.takedownTarget();
            // from in front it is not on offer
            p.z = 359.1f; p.yaw = (float) Math.PI;
            Actor front = r.takedownTarget();
            String why = r.takedownWhy;
            p.z = 361.9f; p.yaw = 0;
            int cries = r.soundSequenceCount("death_cry"), falls = r.soundSequenceCount("body_fall");
            boolean started = r.startTakedown();
            run(r, Raid.TAKEDOWN_SECONDS + 0.4f);
            Mind mm = r.neural.mind(mate.id);
            boolean mateCalm = mm != null && mm.state == absentialis.neuralai.decision.CogState.PATROL && !mm.belief.known();
            int xp = new absentialis.scene.XpFeed(r).pending();
            t.check("a knife and somebody's back: the silent takedown kills without a cry, the man ten metres off with his back turned knows nothing, and it pays",
                    noKnife && offered == guard && front == null && !why.isEmpty() && started && guard.dead && r.takedowns == 1 && r.takedownsSeen == 0 && r.debrief.stealthKills == 1 && r.soundSequenceCount("death_cry") == cries && mateCalm && xp >= absentialis.scene.RaidResolver.XP_STEALTH_KILL,
                    "without a knife offered: " + !noKnife + "; with one: " + (offered == guard) + "; from in front: " + (front != null) + " (" + why + "); dead " + guard.dead + ", cries " + (r.soundSequenceCount("death_cry") - cries) + ", seen " + r.takedownsSeen + ", the other man calm " + mateCalm + " [" + r.neural.describe(mate.id) + "], XP pending " + xp);

            // ---- "When looting corpses, display their equipment ... in the proper equipment slot and not their inventory"
            Actor armed = spawn(r, "military", 575.5f, 365.5f, 0);
            run(r, 0.1f);
            String had = armed.weapon == null ? "" : armed.weapon.defId, slot = armed.weapon == null ? "" : armed.weapon.def().slot;
            String wornArmor = armed.wornArmor, wornHead = armed.wornHead;
            r.debugKill(armed);
            var gear = armed.corpseGear;
            ItemInstance inSlot = gear == null || slot == null || slot.isEmpty() ? null : gear.slots.get(slot);
            boolean weaponInGrid = false, armourInGrid = false;
            if (armed.corpse != null) for (ItemInstance it : armed.corpse.items) { if (it.defId.equals(had)) weaponInGrid = true; if (!wornArmor.isEmpty() && it.defId.equals(wornArmor)) armourInGrid = true; }
            boolean armourSlot = wornArmor.isEmpty() || gear != null && gear.slots.get("armor") != null && gear.slots.get("armor").defId.equals(wornArmor);
            boolean headSlot = wornHead.isEmpty() || gear != null && gear.slots.get("head") != null && gear.slots.get("head").defId.equals(wornHead);
            // taking it off the body is taking it out of the slot
            boolean taken = inSlot != null && absentialis.gameplay.inventory.InventoryService.unequip(inSlot, r.profile.loadout.caseGrid).ok() && gear.slots.get(slot) == null;
            t.check("a body's equipment is found in equipment slots: the weapon in the weapon's slot, the armour and the helmet in theirs - none of it loose in the body's pockets - and taking a thing empties its slot",
                    !had.isEmpty() && inSlot != null && inSlot.defId.equals(had) && !weaponInGrid && !armourInGrid && armourSlot && headSlot && taken,
                    "carried " + had + " -> slot '" + slot + "' " + (inSlot != null) + ", in the grid " + weaponInGrid + "; armour '" + wornArmor + "' in its slot " + armourSlot + ", helmet '" + wornHead + "' in its slot " + headSlot + "; taken " + taken);

            // ---- "When infected eat a dead body, they jerk up and down"
            absentialis.character.Animator an = new absentialis.character.Animator();
            Actor eater = new Actor();
            float least = 1, wAt = 0;
            int restarts = 0;
            float lastT = -1;
            for (int i = 0; i < 360; i++) {
                an.hold("inf_feed", 1.7f);
                an.advance(1 / 60f);
                if (i > 40) least = Math.min(least, an.heldWeight());
                if (an.actionTime < lastT) restarts++;
                lastT = an.actionTime;
            }
            wAt = an.heldWeight();
            for (int i = 0; i < 40; i++) an.advance(1 / 60f);
            t.check("feeding is held, not restarted: the body gets down once and stays down for as long as it feeds - the clip runs on without starting again - and gets up when it stops",
                    least > 0.99f && restarts == 0 && wAt > 0.99f && an.action.isEmpty(), String.format("lowest weight while feeding %.2f, restarts %d, action after stopping '%s'", least, restarts, an.action));

            // ---- "ragdolls should be fully simulated": a body that has come to rest is still a body
            Actor victim = spawn(r, "raiders", 580.5f, 365.5f, 0);
            run(r, 0.1f);
            r.debugKill(victim);
            for (int i = 0; i < 900 && victim.ragdoll != null && !victim.ragdoll.settled; i++) r.update(1 / 60f);
            boolean rested = victim.ragdoll != null && victim.ragdoll.settled;
            float x0 = victim.ragdoll == null ? 0 : victim.ragdoll.x[Ragdoll.PELVIS], z0 = victim.ragdoll == null ? 0 : victim.ragdoll.z[Ragdoll.PELVIS];
            r.explode(x0 + 2.2f, victim.y + 0.2f, z0, 6, 60, 0, false);
            boolean woke = victim.ragdoll != null && !victim.ragdoll.settled;
            for (int i = 0; i < 900 && victim.ragdoll != null && !victim.ragdoll.settled; i++) r.update(1 / 60f);
            float thrown = victim.ragdoll == null ? 0 : (float) Math.hypot(victim.ragdoll.x[Ragdoll.PELVIS] - x0, victim.ragdoll.z[Ragdoll.PELVIS] - z0);
            int part = victim.ragdoll == null ? -1 : victim.ragdoll.struck(victim.ragdoll.x[Ragdoll.PELVIS] - 3, victim.ragdoll.y[Ragdoll.PELVIS] + 0.02f, victim.ragdoll.z[Ragdoll.PELVIS], 1, 0, 0, 6);
            t.check("a body under physics stays under physics: one that has come to rest is thrown by a blast beside it, comes to rest again somewhere else, and a line through it finds the part it strikes",
                    rested && woke && thrown > 0.4f && victim.ragdoll.settled && part >= 0 && r.corpsesThrown >= 1 && victim.ragdoll.maxStretch < 0.6f,
                    "rested " + rested + ", woken by the blast " + woke + String.format(", thrown %.1f m", thrown) + ", settled again " + (victim.ragdoll != null && victim.ragdoll.settled) + ", struck part " + part + ", self-contacts " + (victim.ragdoll == null ? 0 : victim.ragdoll.selfContacts));
        } finally { pt.restore(); }
    }

    // ------------------------------------------------------------------ the two landmarks, the district events, where an operation starts
    private static void world(SelfTests04.Check t) {
        System.out.println("[v0.9 world: the plant and the mansion drawn by hand, a hotspot in every district, the map as one sheet]");
        Content.load();
        World city = SelfTests06.world(1337L);
        MapData.Building plant = null, manor = null;
        for (MapData.Building b : city.map.buildings) { if ("lm_cnpp".equals(b.archetype)) plant = b; if ("lm_spencer".equals(b.archetype)) manor = b; }
        StringBuilder low = new StringBuilder();
        int lowRooms = 0, unreach = 0, tallest = 0;
        if (plant != null) for (MapData.Room rm : plant.rooms) {
            if (!rm.reachable) unreach++;
            // the clear height over somewhere to stand in the room
            int best = 0;
            for (int x = rm.x0; x <= rm.x1; x++) for (int z = rm.z0; z <= rm.z1; z++) {
                if (!city.solid(x, rm.y, z) || city.solid(x, rm.y + 1, z)) continue;
                int h = 0;
                while (h < 40 && !city.block(x, rm.y + 1 + h, z).opaque()) h++;
                best = Math.max(best, h);
            }
            tallest = Math.max(tallest, best);
            if (best < 5) { lowRooms++; low.append(rm.type).append('@').append(rm.floor).append(' '); }
        }
        boolean sites = false, rail = false;
        for (MapData.Point s : city.map.sites) { if (s.name.equals("substation") && city.district(s.x, s.z) == District.INDUSTRIAL) sites = true; if (s.name.equals("rail_exit")) rail = true; }
        int stackTop = 0;
        if (plant != null) for (int x = plant.x0; x <= plant.x1; x++) for (int z = plant.z0; z <= plant.z1; z++) stackTop = Math.max(stackTop, city.heightAt(x, z));
        t.check("the power station is the Chernobyl plant, drawn by hand: one building of many rooms, every one of them reachable and at least five blocks high, a hall four times that, a stack that stands over everything - and the breaker and the freight siding are still where the game needs them",
                plant != null && plant.authored && plant.rooms.size() >= 26 && unreach == 0 && lowRooms == 0 && tallest >= 18 && stackTop - G >= 55 && sites && rail && plant.x1 - plant.x0 >= 60,
                plant == null ? "not built" : plant.rooms.size() + " rooms, " + unreach + " unreachable, " + lowRooms + " under five high " + low + "- the tallest " + tallest + "; stack to " + (stackTop - G) + " above the street; " + plant.doors.size() + " doors; substation " + sites + ", rail exit " + rail + "; problems " + plant.problems);
        int halls = 0, upstairs = 0, boxes = 0;
        if (manor != null) { for (MapData.Room rm : manor.rooms) { if (rm.type.equals("hall") || rm.type.equals("dining")) halls++; if (rm.floor == 1) upstairs++; } for (MapData.LootSpot l : city.map.loot) if (manor.contains(l.x, l.z)) boxes++; }
        int manorUnreach = 0;
        if (manor != null) for (MapData.Room rm : manor.rooms) if (!rm.reachable) manorUnreach++;
        t.check("the big house is the Spencer mansion, drawn by hand: the main hall and the dining room, two floors of rooms all of which can be walked to, and things in it to find",
                manor != null && manor.authored && manor.rooms.size() >= 22 && halls >= 3 && upstairs >= 8 && manorUnreach == 0 && boxes >= 20 && manor.problems.isEmpty(),
                manor == null ? "not built" : manor.rooms.size() + " rooms (" + upstairs + " upstairs), " + manorUnreach + " unreachable, " + boxes + " containers, " + manor.doors.size() + " doors; problems " + manor.problems);
        t.check("the generator still validates clean with the two hand-drawn landmarks in it", city.map.report != null && city.map.report.materialFaults == 0 && city.map.report.roomsUnreachable == 0 && city.map.report.buildingsRejected == 0,
                city.map.report == null ? "no report" : "material faults " + city.map.report.materialFaults + ", unreachable rooms " + city.map.report.roomsUnreachable + ", rejected " + city.map.report.buildingsRejected + ", floating removed " + city.map.report.floatingRemoved);

        // ---- "Each district should have its own hotspots and random events"
        Raid r = new Raid(city, new Profile(), new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer);
        r.events.begin();
        int districts = 0, kinds = 0, placed = 0;
        java.util.Set<String> kindSet = new java.util.TreeSet<>(), names = new java.util.TreeSet<>();
        StringBuilder where = new StringBuilder();
        for (District d : District.values()) {
            var defs = r.events.defsOf(d);
            if (defs.isEmpty()) continue;
            districts++;
            for (var def : defs) { kindSet.add(def.kind()); names.add(def.id()); }
            var live = r.events.of(d);
            if (live != null) { placed++; where.append(d.display).append(": ").append(live.def.name()).append(" at ").append(live.building.archetype).append("; "); }
        }
        kinds = kindSet.size();
        // bring one into being: its people are there, on two sides
        var hot = r.events.force(District.OLD_QUARTER);
        int side = hot == null ? 0 : hot.side.size(), other = hot == null ? 0 : hot.other.size();
        boolean inPlace = hot != null && !hot.side.isEmpty() && hot.side.get(0).distXZ(hot.x, hot.z) < 60;
        // and the smaller things happen where the survivor is
        Actor p = r.player;
        if (hot != null) { p.x = hot.x + 30; p.z = hot.z; p.y = r.groundY((int) p.x, (int) p.z); }
        int actorsBefore = r.actors.size();
        r.events.forceRandom("pack"); r.events.forceRandom("scavengers");
        t.check("every district has hotspots of its own and one of them is in progress - a holdout, a raid, a standoff, a horde - at a real place in that district; brought into being it has its people on both sides, and smaller events happen round the survivor",
                districts == 5 && names.size() >= 10 && kinds >= 4 && placed >= 4 && side >= 3 && inPlace && r.events.randoms >= 1 && r.actors.size() > actorsBefore,
                districts + " districts, " + names.size() + " hotspots of " + kinds + " kinds " + kindSet + "; placed " + placed + ": " + where + "downtown's has " + side + " + " + other + " people; random events run " + r.events.randoms + " (" + (r.actors.size() - actorsBefore) + " bodies)");
        // a hotspot ends when one side of it is gone, and says so
        if (hot != null) { for (Actor a : hot.side) { a.lastAttacker = p.id; r.debugKill(a); } }
        run(r, 12f);
        t.check("a hotspot ends when one side of it is gone", hot != null && hot.resolved && !hot.outcome.isEmpty() && r.events.resolved == 1, hot == null ? "none" : hot.def.name() + ": " + hot.outcome);
    }

    // ------------------------------------------------------------------ Director Mode: controls, the pointer, the tape
    private static void modes(SelfTests04.Check t) {
        System.out.println("[v0.9 Director Mode: the controls come back by themselves, the pointer works the menus, a take is a tape that can be looked at from anywhere]");
        // ---- "Player character can not turn around or look around in director mode"
        absentialis.director.GUIManager gui = new absentialis.director.GUIManager();
        gui.lock(0.45f);
        boolean heldAtFirst = gui.locked();
        for (int i = 0; i < 40; i++) gui.tick(1 / 60f);
        t.check("the lock that holds the controls across Director Mode's curtain runs out by itself (it only counted down while a menu was being read, so after entering the scene the actor could not look or move until a menu had been opened once)",
                heldAtFirst && !gui.locked(), "locked at first " + heldAtFirst + ", after 0.67 s with no menu open " + gui.locked());
        // ---- "Directormode should be allowed to use the mouse for the menu"
        absentialis.director.GUIManager.Menu m = new absentialis.director.GUIManager.Menu("Test");
        int[] hits = new int[3];
        m.add("One").run(() -> hits[0]++);
        m.add("Two").value(() -> String.valueOf(hits[1]), d -> hits[1] += d);
        m.add("Three").run(() -> hits[2]++);
        gui.show(m);
        absentialis.engine.Input in = new absentialis.engine.Input();
        gui.layoutForTests(100, 100, 400, 26, 0, 3);
        // the pointer over the third row, moved, then clicked
        in.mouseX = 200; in.mouseY = 100 + 26 * 2 + 10; gui.input(in, 1 / 60f);
        boolean hovered = m.sel == 2;
        in.inject(absentialis.engine.Input.MOUSE_OFFSET, true); in.endFrame(); gui.input(in, 1 / 60f);
        in.inject(absentialis.engine.Input.MOUSE_OFFSET, false); in.endFrame();
        // the right-hand end of the second row's value: up one
        in.mouseX = 100 + 400 * 0.9; in.mouseY = 100 + 26 + 10; gui.input(in, 1 / 60f);
        in.inject(absentialis.engine.Input.MOUSE_OFFSET, true); in.endFrame(); gui.input(in, 1 / 60f);
        in.inject(absentialis.engine.Input.MOUSE_OFFSET, false); in.endFrame();
        t.check("Director Mode's menus are worked with the pointer: moving it over a row highlights the row, a click selects it, a click on the right of a value turns the value up",
                hovered && hits[2] == 1 && hits[1] == 1 && hits[0] == 0, "highlighted the row under the pointer " + hovered + "; row three selected " + hits[2] + " time(s); row two's value " + hits[1]);
        // ---- "Only allow the change in perspective when editing the footage": a take keeps a tape of the scene
        absentialis.director.SceneTape tape = new absentialis.director.SceneTape();
        Actor a = new Actor(), b = new Actor();
        a.x = 10; a.y = G; a.z = 20; a.yaw = 0.5f; b.x = 14; b.y = G; b.z = 22;
        absentialis.engine.math.Mat4[] bones = new absentialis.engine.math.Mat4[absentialis.character.Rig.GROUPS];
        for (int g = 0; g < bones.length; g++) bones[g] = new absentialis.engine.math.Mat4();
        for (int f = 0; f < 3; f++) {
            tape.beginFrame(a, 1 + f, 2, 3, 0.1f, 0.2f, 70);
            a.x = 10 + f; bones[0].identity().translate(a.x, a.y, a.z);
            tape.bones(a, bones, null);
            if (f < 2) { bones[0].identity().translate(b.x, b.y, b.z); tape.bones(b, bones, bones[0]); }
            tape.commit(List.of());
        }
        // later: both have moved on, one has died. The tape stands them where they were, draws from its own bones, and puts them back
        a.x = 99; b.x = 77; b.dead = true;
        tape.apply(1);
        boolean stood = a.x == 11 && b.x == 14 && !b.dead;
        absentialis.engine.math.Mat4 wm = new absentialis.engine.math.Mat4();
        int ka = tape.bones(a, bones, wm, false);
        float boneX = bones[0].m[12];
        int kb = tape.bones(b, bones, wm, false);
        tape.apply(2);
        int gone = tape.bones(b, bones, wm, false);
        tape.restore();
        float[] subj = tape.subject(2), cam = tape.camera(0);
        t.check("a recorded take keeps a tape of the scene - everybody's bones, the weapon in the hand, the camera - so it can be looked at from another camera afterwards: played back, people stand where they were and are drawn as they were, somebody who was not in the scene at that moment is not drawn, and everyone is put back",
                tape.size() == 3 && stood && ka == 1 && Math.abs(boneX - 11) < 1e-4f && kb == 2 && gone == 0 && a.x == 99 && b.x == 77 && b.dead && subj != null && subj[0] == 12 && cam != null && cam[0] == 1 && !tape.applied(),
                tape.size() + " frames; stood where they were " + stood + "; bones " + ka + ", with a weapon " + kb + ", absent later " + gone + "; restored to " + a.x + " and " + b.x + (b.dead ? " (dead)" : "") + "; subject at " + (subj == null ? "-" : subj[0]) + ", camera at " + (cam == null ? "-" : cam[0]));
        // the editor's cameras
        t.check("perspective is the editor's: five cameras for a take - the footage as recorded, third person, orbit, fixed, free", absentialis.director.ClipEditor.CAMERAS.length == 5 && absentialis.director.ClipEditor.CAM_RECORDED == 0,
                String.join(", ", absentialis.director.ClipEditor.CAMERAS));
        // ---- "put the character creation screen in it": a character of the player's own is an actor, and is kept
        absentialis.director.PersistenceManager pm = new absentialis.director.PersistenceManager();
        java.nio.file.Path was = absentialis.persistence.SaveSystem.dir;
        try {
            absentialis.persistence.SaveSystem.dir = java.nio.file.Files.createTempDirectory("abs09");
            var ch = new absentialis.director.PersistenceManager.Character();
            ch.name = "Vera"; ch.app.sex(true);
            pm.characters.add(ch);
            pm.save();
            absentialis.director.PersistenceManager back = new absentialis.director.PersistenceManager();
            back.load();
            t.check("a character made in Director Mode's creator is kept between sessions with the appearance it was given", back.characters.size() == 1 && back.characters.get(0).name.equals("Vera") && back.characters.get(0).app.female && pm.problem.isEmpty(),
                    back.characters.size() + " character(s): " + (back.characters.isEmpty() ? "-" : back.characters.get(0).name + (back.characters.get(0).app.female ? ", female" : ", male")) + " " + pm.problem);
        } catch (Exception e) { t.check("a character made in Director Mode's creator is kept between sessions", false, e.toString()); }
        finally { absentialis.persistence.SaveSystem.dir = was; }
    }
}
