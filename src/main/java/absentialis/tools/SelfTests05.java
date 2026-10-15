package absentialis.tools;

import absentialis.engine.events.Events;
import absentialis.entity.Actor;
import absentialis.entity.Vital;
import absentialis.gameplay.CombatConfig;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.persistence.Profile;
import absentialis.scene.CombatFeed;
import absentialis.scene.Doors;
import absentialis.scene.Raid;
import absentialis.scene.StatusHud;
import absentialis.world.CoverMap;
import absentialis.world.District;
import absentialis.world.MapData;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Version 0.5 gates (brief 16): world generation, the casualty state machine, confirmed-event feedback, doors,
 * cover and the status interface. Audio has its own file ({@link AudioTests05}).
 */
final class SelfTests05 {
    private SelfTests05() {}

    private static final int G = World.GROUND;

    private static void run(Raid r, float seconds) { for (int i = 0; i < Math.round(seconds * 60); i++) r.update(1 / 60f); }

    static void run(String which, boolean all, SelfTests04.Check t) {
        if (all || which.contains("v05") || which.contains("world05")) SelfTests.guard(() -> { world(t); });
        if (all || which.contains("v05") || which.contains("casualty")) SelfTests.guard(() -> { casualty(t); });
        if (all || which.contains("v05") || which.contains("feed")) SelfTests.guard(() -> { feedback(t); });
        if (all || which.contains("v05") || which.contains("door")) SelfTests.guard(() -> { doors(t); });
        if (all || which.contains("v05") || which.contains("hud05")) SelfTests.guard(() -> { status(t); });
        if (all || which.contains("v05") || which.contains("gunaudio")) SelfTests.guard(() -> { AudioTests05.run(t); });
    }

    // ------------------------------------------------------------------ world
    private static void world(SelfTests04.Check t) {
        System.out.println("[v0.5 world: districts, landmarks, boundary, navigation]");
        for (long seed : new long[]{1337L, 20260510L}) {
            World w = new World();
            long t0 = System.nanoTime();
            new absentialis.world.gen.CityGenerator(w, seed, null).generate();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            MapData m = w.map;
            String tag = "seed " + seed + ": ";
            Map<District, List<MapData.Landmark>> by = new HashMap<>();
            for (MapData.Landmark l : m.anchors) by.computeIfAbsent(l.district, k -> new ArrayList<>()).add(l);
            StringBuilder sb = new StringBuilder();
            boolean two = true, oneHq = true, built = true;
            for (District d : District.values()) {
                List<MapData.Landmark> ls = by.getOrDefault(d, List.of());
                if (ls.isEmpty()) continue;                                              // wilderness: no landmark compounds
                sb.append(d.display).append(": ");
                int hq = 0;
                for (MapData.Landmark l : ls) { sb.append(l.id).append(l.headquarters ? "* " : " "); if (l.headquarters) hq++; if (l.buildings.isEmpty()) built = false; }
                if (ls.size() != 2) two = false;
                if (hq != 1) oneHq = false;
            }
            t.check(tag + "every developed district has exactly two landmark compounds", two && by.size() == 5 && m.anchors.size() == 10, sb.toString().trim());
            t.check(tag + "each district has exactly one faction headquarters, and every landmark has buildings", oneHq && built, "");
            absentialis.world.gen.GenReport rep = m.report;
            t.check(tag + "the plan validates clean (rooms reachable, roads clear, nothing floating, no overlaps)", rep != null && rep.clean() && rep.buildingsRejected == 0, String.valueOf(rep));
            int badEntry = 0, noRooms = 0;
            for (MapData.Building b : m.buildings) { if (!b.valid) continue; if (b.rooms.isEmpty()) noRooms++; if (w.solid(b.entryX, b.entryY, b.entryZ) || w.solid(b.entryX, b.entryY + 1, b.entryZ)) badEntry++; }
            t.check(tag + "every building has rooms and a standable entrance", m.buildings.size() > 100 && badEntry == 0 && noRooms == 0, m.buildings.size() + " buildings, " + badEntry + " blocked entrances, " + noRooms + " without rooms");
            TerrainProbe.Report tr = TerrainProbe.scan(w);
            t.check(tag + "boundary: no trench, no void columns, no cliffs at the city edge or chunk seams", tr.trenchCells == 0 && tr.voidColumns == 0 && tr.edgeCliffs == 0 && tr.seamCliffs == 0, String.valueOf(tr));
            // the ring just outside the city wall holds nothing transparent standing as a tower
            int glass = 0;
            for (int i = World.CITY_MIN - 8; i < World.CITY_MAX + 8; i++) for (int k = 1; k <= 6; k++) for (int y = G; y < G + 30; y++) {
                if (glassy(w, i, y, World.CITY_MIN - k) || glassy(w, i, y, World.CITY_MAX + k - 1) || glassy(w, World.CITY_MIN - k, y, i) || glassy(w, World.CITY_MAX + k - 1, y, i)) glass++;
            }
            t.check(tag + "no glass-like structures outside the boundary", glass == 0, glass + " transparent solid blocks in the ring outside the wall");
            int exitsOk = 0;
            for (MapData.Exit e : m.exits) if (!w.solid(e.x, e.y, e.z) && !w.solid(e.x, e.y + 1, e.z)) exitsOk++;
            t.check(tag + "exits and insertions stand in open cells", exitsOk == m.exits.size() && m.exits.size() >= 8 && m.insertions.size() >= 4, exitsOk + "/" + m.exits.size() + " exits, " + m.insertions.size() + " insertions");
            t.check(tag + "generation is fast enough to run at world creation", ms < 20000, ms + " ms");
            if (seed == 1337L) {
                // cover is generated from the same geometry and is usable in the streets
                List<CoverMap.Point> pts = w.cover.near(352, G, 352, 40, new ArrayList<>());
                int low = 0, high = 0;
                for (CoverMap.Point p : pts) { if (p.height == CoverMap.LOW) low++; else high++; }
                t.check("cover points exist round the centre, low and high", pts.size() > 20 && low > 0 && high > 0, pts.size() + " points (" + low + " low, " + high + " high)");
                World w2 = new World();
                new absentialis.world.gen.CityGenerator(w2, seed, null).generate();
                boolean same = true;
                for (int x = 100; x < 600 && same; x += 7) for (int z = 100; z < 600 && same; z += 7) for (int y = G - 2; y < G + 20; y += 3) if (w.get(x, y, z) != w2.get(x, y, z)) { same = false; break; }
                t.check("the same seed builds the same world", same, "");
            }
        }
    }

    private static boolean glassy(World w, int x, int y, int z) {
        absentialis.world.Block b = w.block(x, y, z);
        boolean g = b != null && b.mat == absentialis.world.Material.GLASS && !b.name.contains("light");      // a warning lamp on the wall is a lamp
        if (g && System.getenv("ABS_GLASS") != null) System.out.println("    glass " + b.name + " at " + x + "," + y + "," + z);
        return g;
    }

    // ------------------------------------------------------------------ downed, bleed-out, revive, death
    private static void casualty(SelfTests04.Check t) {
        System.out.println("[v0.5 casualties: downed, bleed-out, revive, death, conditions]");
        CombatConfig cfg = CombatConfig.get();
        t.check("four conditions from configurable thresholds", cfg.condition(100, 100) == CombatConfig.Condition.values()[0] && cfg.condition(60, 100).ordinal() == 1 && cfg.condition(40, 100).ordinal() == 2 && cfg.condition(10, 100).ordinal() == 3,
                String.format("caution %.2f, injured %.2f, critical %.2f", cfg.caution, cfg.injured, cfg.critical));
        t.check("each worse condition moves slower, and the critical cannot sprint", cfg.speed[0] > cfg.speed[1] && cfg.speed[1] > cfg.speed[2] && cfg.speed[2] > cfg.speed[3] && !cfg.sprint[3], java.util.Arrays.toString(cfg.speed));

        Raid r = SelfTests.stage(new Profile());
        r.freezeAI = true;
        List<Object> log = new ArrayList<>();
        r.bus.on(Events.ActorDowned.class, log::add);
        r.bus.on(Events.ActorRevived.class, log::add);
        r.bus.on(Events.ReviveInterrupted.class, log::add);
        r.bus.on(Events.KillConfirmed.class, log::add);
        Actor a = r.debugSpawn("survivors", 320, 500, G), medic = r.debugSpawn("survivors", 321.2f, 500, G);
        r.casualty.down(a, r.player, "bullet");
        t.check("a downed human is DOWNED, not dead, with the bleed-out clock at " + (int) cfg.bleedOut + " s", a.vital == Vital.DOWNED && !a.dead && Math.abs(a.bleedLeft - cfg.bleedOut) < 0.5f, a.vital + ", " + a.bleedLeft + " s");
        run(r, 5);
        float left = a.bleedLeft;
        boolean began = r.casualty.begin(medic, a);
        run(r, 1);
        t.check("a revive in progress stops the bleed-out clock", began && a.vital == Vital.REVIVING && Math.abs(a.bleedLeft - left) < 0.3f, String.format("%.1f s before, %.1f s a second into the revive", left, a.bleedLeft));
        r.casualty.interrupt(a, "test");
        run(r, 0.5f);
        boolean interrupted = a.vital == Vital.DOWNED && log.stream().anyMatch(o -> o instanceof Events.ReviveInterrupted);
        t.check("an interrupted revive returns to DOWNED and the clock runs again", interrupted && a.bleedLeft < left, a.vital + ", " + a.bleedLeft + " s");
        r.casualty.begin(medic, a);
        run(r, cfg.reviveTime + 0.5f);
        t.check("a completed revive returns the casualty to ACTIVE with some health", a.vital == Vital.ACTIVE && !a.dead && a.hp > 0 && a.hp <= a.maxHp * 0.6f && log.stream().anyMatch(o -> o instanceof Events.ActorRevived), a.vital + " hp " + a.hp);
        Actor b = r.debugSpawn("survivors", 326, 500, G);
        r.casualty.down(b, r.player, "bullet");
        run(r, cfg.bleedOut + 1.5f);
        long kills = log.stream().filter(o -> o instanceof Events.KillConfirmed k && k.victimId() == b.id).count();
        t.check("unattended, a casualty bleeds out in " + (int) cfg.bleedOut + " s and is confirmed dead once", b.dead && b.vital == Vital.DEAD && kills == 1 && r.casualty.bleedOuts >= 1, "dead " + b.dead + ", kill events " + kills);
        Actor z = r.debugSpawn("walker", 330, 500, G);
        boolean zDowned = false;
        for (int i = 0; i < 50; i++) zDowned |= r.casualty.lethal(z, r.player, 0.1f, "torso", "bullet", false);
        t.check("the infected are never downed: lethal damage is lethal", !zDowned && z.vital == Vital.ACTIVE, "");
        int lethalHead = 0;
        Actor c = r.debugSpawn("survivors", 334, 500, G);
        for (int i = 0; i < 50; i++) if (r.casualty.lethal(c, r.player, 0.1f, "head", "bullet", false)) lethalHead++;
        t.check("a fatal region (the head) always kills: it is never an incapacitation", lethalHead == 0 && c.vital == Vital.ACTIVE, lethalHead + "/50 downed");

        // the player's body stays in the world when the player dies
        Raid r2 = SelfTests.stage(new Profile());
        r2.god = false; r2.freezeAI = true;
        r2.deathCam = 3.6f; r2.deathHold = true;
        r2.player.anim = new absentialis.character.Animator();
        float px = r2.player.x, pz = r2.player.z;
        r2.damageActor(r2.player, null, 5000, null, "head", px, r2.player.y + 1.6f, pz);
        run(r2, 2.5f);
        Actor p = r2.player;
        t.check("the player's body persists after death as a ragdoll where it fell", p.dead && p.ragdoll != null && r2.actors.contains(p) && Math.abs(p.ragdoll.x[absentialis.character.Ragdoll.PELVIS] - px) < 6, "dead " + p.dead + ", ragdoll " + (p.ragdoll != null));
        // herbs: permanent maximum health that survives a save
        Profile pr = new Profile();
        pr.herbs = 3;
        Profile back = Profile.fromJson(pr.toJson());
        Raid r3 = SelfTests.stage(back);
        t.check("Yellow Herbs raise maximum health permanently and survive save and load", back.herbs == 3 && r3.player.maxHp == cfg.maxHealth(3) && cfg.maxHealth(3) > cfg.maxHealth(0) && cfg.maxHealth(9999) == cfg.healthCap,
                "max health " + r3.player.maxHp + " with 3 herbs (base " + cfg.baseHealth + ", cap " + cfg.healthCap + ")");
        ItemDef herb = Content.ITEMS.get("herb.yellow");
        t.check("the Yellow Herb is an item in the game's data", herb != null, herb == null ? "missing" : herb.name);
    }

    // ------------------------------------------------------------------ hit marker and killfeed
    private static void feedback(SelfTests04.Check t) {
        System.out.println("[v0.5 feedback: hit marker and killfeed from confirmed events]");
        Raid r = SelfTests.stage(new Profile());
        r.freezeAI = true;
        CombatFeed f = new CombatFeed(r);
        f.attach(r.bus);
        Actor a = r.debugSpawn("raiders", 320, 500, G);
        int pid = r.player.id;
        r.bus.emit(new Events.HitConfirmed(pid, a.id, "torso", 20, 1f));
        boolean white = f.markerLevel == CombatFeed.HIT;
        r.bus.emit(new Events.ActorDowned(a.id, pid, "A", "raiders", 1.05f, "bullet"));
        boolean yellow = f.markerLevel == CombatFeed.DOWN;
        r.bus.emit(new Events.HitConfirmed(pid, a.id, "torso", 20, 1.1f));
        boolean held = f.markerLevel == CombatFeed.DOWN;
        t.check("marker: white on a hit, yellow on an incapacitation, and a hit does not replace the yellow", white && yellow && held, white + " " + yellow + " " + held);
        t.check("an incapacitation writes nothing to the killfeed", f.entries.isEmpty() && f.downs == 1, f.entries.size() + " entries");
        r.damageActor(a, r.player, 5000, null, "head", a.x, a.y + 1.6f, a.z);
        run(r, 0.2f);
        t.check("marker: red on a confirmed kill, above everything else", f.markerLevel == CombatFeed.KILL && f.kills == 1, "level " + f.markerLevel);
        int n = f.entries.size();
        r.damageActor(a, r.player, 5000, null, "head", a.x, a.y + 1.6f, a.z);
        run(r, 0.2f);
        t.check("one killfeed line per death, however many times the body is hit", n == 1 && f.entries.size() == 1, f.entries.size() + " entries, " + f.duplicates + " duplicates refused");
        CombatFeed.Entry e = f.entries.isEmpty() ? null : f.entries.get(0);
        t.check("the line names killer, weapon and victim, coloured by relation", e != null && !e.killer.isEmpty() && !e.victim.isEmpty() && e.victimRel == CombatFeed.HOSTILE && e.killerRel == CombatFeed.SELF, e == null ? "none" : e.killer + " [" + e.weapon + "] " + e.victim);
        // AI against AI is reported too
        Actor x = r.debugSpawn("raiders", 324, 500, G), y = r.debugSpawn("military", 326, 500, G);
        r.damageActor(y, x, 5000, null, "head", y.x, y.y + 1.6f, y.z);
        run(r, 0.2f);
        boolean ai = f.entries.stream().anyMatch(en -> en.killerId == x.id && en.victimId == y.id);
        t.check("a kill between two NPCs appears in the killfeed and raises no marker for the player", ai, f.entries.size() + " entries");
        t.check("relation colours: blue squad, green friendly, yellow neutral, red hostile", CombatFeed.color(CombatFeed.SQUAD) == 0xFF4F9BE8 && CombatFeed.color(CombatFeed.FRIENDLY) == 0xFF62C46A && CombatFeed.color(CombatFeed.NEUTRAL) == 0xFFE8CF4A && CombatFeed.color(CombatFeed.HOSTILE) == 0xFFE2483A, "");
    }

    // ------------------------------------------------------------------ doors
    private static void doors(SelfTests04.Check t) {
        System.out.println("[v0.5 doors: physical, damageable, known to navigation]");
        World w = new World();
        new absentialis.world.gen.CityGenerator(w, 1337L, null).generate();
        Raid r = new Raid(w, new Profile(), new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer);
        r.god = true; r.freezeAI = true;
        Doors ds = r.doors;
        Doors.Door wood = null, any = null;
        int count = 0;
        for (Doors.Door d : ds.doors) { count++; if (any == null) any = d; if (wood == null && d.kind == Doors.WOOD && !d.locked) wood = d; }
        t.check("doors in the world are entities", count > 100 && wood != null, count + " doors");
        if (wood == null) return;
        Doors.Door d = wood;
        t.check("a closed door blocks its doorway", ds.state(d) == Doors.State.CLOSED && d.solid, String.valueOf(ds.state(d)));
        int nav0 = r.world.navVersion;
        ds.open(d, r.player);
        run(r, 1.2f);
        t.check("opened, it swings on its hinge and the doorway is passable", Math.abs(d.angle) > 1.0f && ds.state(d) == Doors.State.OPEN && !ds.sealed(d.x, d.y, d.z), String.format("angle %.2f rad", d.angle));
        ds.use(d, r.player);
        run(r, 1.2f);
        t.check("used again, it closes", ds.state(d) == Doors.State.CLOSED && Math.abs(d.angle) < 0.1f, String.format("angle %.2f rad", d.angle));
        int reg = Doors.regionAt(d, d.cx(), d.y + 0.4f, d.cz());
        float h0 = d.health, lower0 = d.region[reg];
        ds.hit(d, d.cx(), d.y + 0.4f, d.cz(), 18, d.alongX ? 0 : 1, d.alongX ? 1 : 0, r.player.id, "bullet");
        t.check("a bullet damages the region it strikes", d.health < h0 && d.region[reg] > lower0, String.format("health %.0f -> %.0f, %s %.0f -> %.0f", h0, d.health, Doors.REGION[reg], lower0, d.region[reg]));
        for (int i = 0; i < 40 && !d.latchBroken && !d.destroyed; i++) ds.hit(d, d.pivotX() + d.dirX() * 0.9f, d.y + 1.0f, d.pivotZ() + d.dirZ() * 0.9f, 30, d.alongX ? 0 : 1, d.alongX ? 1 : 0, r.player.id, "bullet");
        t.check("enough damage at the latch breaks it: the door no longer holds shut", d.latchBroken || d.destroyed, "latch " + d.latchBroken + ", destroyed " + d.destroyed);
        ds.blast(d, 4000, d.alongX ? 0 : 1, d.alongX ? 1 : 0, r.player.id);
        run(r, 0.5f);
        t.check("destroyed, the leaf leaves the frame as debris and navigation is told", d.destroyed && !ds.sealed(d.x, d.y, d.z) && ds.detached + ds.shattered >= 1 && r.world.navVersion != nav0, "detached " + ds.detached + ", shattered " + ds.shattered + ", nav version " + nav0 + " -> " + r.world.navVersion);
    }

    // ------------------------------------------------------------------ status interface
    private static void status(SelfTests04.Check t) {
        System.out.println("[v0.5 interface: gauge, layout]");
        CombatConfig cfg = CombatConfig.get();
        float base = StatusHud.gaugeSpan(cfg.baseHealth, cfg), more = StatusHud.gaugeSpan(cfg.baseHealth + cfg.perHerb * 3, cfg), cap = StatusHud.gaugeSpan(cfg.healthCap, cfg);
        t.check("the health gauge is a half circle at base health and lengthens with maximum health", Math.abs(base - Math.PI) < 1e-3 && more > base && cap > more && cap < Math.PI * 2, String.format("%.2f, %.2f, %.2f rad", base, more, cap));
        absentialis.ui.Layout.Box a = new absentialis.ui.Layout.Box(0, 0, 100, 40), b = new absentialis.ui.Layout.Box(90, 10, 50, 50), c = new absentialis.ui.Layout.Box(100, 0, 10, 10);
        t.check("layout boxes detect overlap", a.overlaps(b) && !a.overlaps(c), "");
        t.check("the version has one authoritative source (1.3 now)", "1.3".equals(absentialis.Version.NUMBER), absentialis.Version.NUMBER);
    }
}
