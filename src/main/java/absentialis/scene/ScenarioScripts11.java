package absentialis.scene;

import static absentialis.scene.Scenarios.*;

import absentialis.entity.Actor;
import absentialis.world.gen.DevMap;
import java.util.function.Consumer;

/**
 * The scripts added for 1.1 (see {@link ScenarioScripts}). All of them run on the developer map: that is where the
 * brief says mechanics are to be tested, and it is where the things a test needs are stood ready.
 */
final class ScenarioScripts11 {
    private ScenarioScripts11() {}

    static boolean build(String name) {
        switch (name == null ? "" : name) {
            case "bugs11" -> bugs11();
            case "bodies11" -> bodies11();
            case "hud11" -> hud11();
            case "animals11" -> animals11();
            case "landmark11" -> landmark11();
            case "cars11" -> cars11();
            default -> { return ScenarioScripts12.build(name); }
        }
        return true;
    }

    private static final Actor[] cast = new Actor[8];

    /** The devmap, loaded from the hideout before deployment (every 1.1 script starts with this). */
    static void devmap() {
        Scenarios.hour = 13;
        for (int f = 8; f < 3000; f += 30) hideout(f, h -> { if (DevMap.mansion == null && h.worldReady()) h.console("devmap 0"); });
    }

    /** A camera at a place, looking at another. */
    static float[] look(float cx, float cy, float cz, float tx, float ty, float tz, float fov) {
        float dx = tx - cx, dy = ty - cy, dz = tz - cz;
        return new float[]{cx, cy, cz, (float) Math.atan2(-dx, -dz), (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)), fov};
    }

    static void at(int f, Consumer<Scenarios.Host> r) { raid(f, r); }

    /**
     * The 1.1 bug list, the parts of it that can be looked at: the devmap's road; the stairs (nobody goes prone on
     * them); glass a long way off in clear air and in fog; a column of smoke; grass with somebody standing in it.
     */
    private static void bugs11() {
        profile(p -> give(p, "weapon.akr", 1, true));
        devmap();
        final float g = g();
        final float rx = DevMap.ROAD_X0 + 30, rz = (DevMap.ROAD_Z0 + DevMap.ROAD_Z1) / 2f + 0.5f;
        final float yx = DevMap.YARD_X, yz = DevMap.YARD_Z;
        at(30, h -> { Raid r = h.raid().raid; r.god = true; h.raid().photo = true; h.console("weather clear"); h.console("time 13"); h.raid().scenarioHold("primary"); });
        // ---- the road
        at(40, h -> { place(h.raid().raid, rx, g, rz, 0, 0); h.raid().camOverride = look(rx - 6, g + 5.5f, rz + 3, rx + 10, g, rz, 62); });
        at(240, h -> shot(h, "bug_00_road"));
        at(244, h -> h.raid().camOverride = look(rx + 2, g + 1.7f, rz + 1.5f, rx + 30, g + 0.2f, rz, 60));
        at(300, h -> shot(h, "bug_01_road_low"));
        // ---- the stairs: walked up, standing
        at(304, h -> { Raid r = h.raid().raid; place(r, yx + 17.5f, g, yz + 15.5f, (float) Math.PI / 2, 0); h.raid().thirdPerson = true; h.raid().camOverride = look(yx + 15, g + 2.6f, yz + 20.5f, yx + 14.5f, g + 1.6f, yz + 15.5f, 55); });
        for (int k = 0; k < 150; k++) at(310 + k, h -> h.ctx().in.inject(h.ctx().in.bindings.get("forward"), true));
        at(380, h -> { Raid r = h.raid().raid; System.out.println("[Scenario] on the stairs: y " + r.player.y + " prone " + r.player.prone + " crouch " + r.player.crouch); shot(h, "bug_02_stairs"); });
        at(462, h -> { h.ctx().in.inject(h.ctx().in.bindings.get("forward"), false); Raid r = h.raid().raid; System.out.println("[Scenario] top of the stairs: y " + r.player.y + " (platform " + (g + 3) + ") prone " + r.player.prone); });
        // ---- glass, far off: clear, then in fog
        at(470, h -> { Raid r = h.raid().raid; h.raid().thirdPerson = false; place(r, yx + 61, g, yz + 26, 0, 0); h.raid().camOverride = look(yx + 61.5f, g + 1.7f, yz + 26, yx + 61.5f, g + 1.6f, yz + 4, 60); });
        at(640, h -> shot(h, "bug_03_glass_22m"));
        at(644, h -> { Raid r = h.raid().raid; place(r, yx + 61, g, yz + 26, 0, 0); h.raid().camOverride = look(yx + 61.5f, g + 2.2f, yz + 110, yx + 61.5f, g + 1.6f, yz + 4, 22); });
        at(800, h -> shot(h, "bug_04_glass_106m_zoom"));
        at(804, h -> h.console("weather fog now"));
        at(960, h -> shot(h, "bug_05_glass_106m_fog"));
        at(964, h -> h.raid().camOverride = look(yx + 61.5f, g + 1.7f, yz + 40, yx + 61.5f, g + 1.6f, yz + 4, 50));
        at(1100, h -> shot(h, "bug_06_glass_36m_fog"));
        at(1104, h -> h.console("weather clear now"));
        // ---- smoke
        at(1110, h -> { Raid r = h.raid().raid; r.smoulder(rx + 40, (int) g, rz - 14); place(r, rx + 30, g, rz, 0, 0); h.raid().camOverride = look(rx + 24, g + 2.4f, rz + 6, rx + 40, g + 7, rz - 14, 62); });
        at(1900, h -> { Raid r = h.raid().raid; System.out.println("[Scenario] smoke: " + r.smokePuffs + " puffs, " + r.fx.count(absentialis.engine.fx.ParticleSystem.SMOKE) + " in the air"); shot(h, "bug_07_smoke"); });
        at(1904, h -> h.raid().camOverride = look(rx + 37, g + 1.5f, rz - 9, rx + 40, g + 0.6f, rz - 14, 55));
        at(1960, h -> shot(h, "bug_08_smoke_source"));
        // ---- grass with somebody in it
        at(1964, h -> { Raid r = h.raid().raid; h.raid().thirdPerson = true; place(r, rx + 6, g, rz + 9, 0, 0); h.raid().camOverride = look(rx + 6, g + 1.5f, rz + 12.5f, rx + 6, g + 0.3f, rz + 9, 50); });
        at(2100, h -> shot(h, "bug_09_grass_underfoot"));
        at(2104, h -> { Raid r = h.raid().raid; place(r, rx + 12, g, rz + 9, 0, 0); });
        at(2180, h -> { shot(h, "bug_10_grass_after"); h.raid().camOverride = null; });
    }

    /**
     * Bodies: the male and the female build side by side; a hand to a wound; one of the dead and the Crawler knocked
     * down and getting up again; somebody put down wounded; a long fall.
     */
    private static void bodies11() {
        profile(p -> give(p, "weapon.akr", 1, true));
        devmap();
        final float g = g();
        final float bx = DevMap.ROAD_X0 + 30, bz = (DevMap.ROAD_Z0 + DevMap.ROAD_Z1) / 2f + 0.5f;
        at(30, h -> {
            Raid r = h.raid().raid;
            r.god = true; r.populate = false; r.freezeAI = true; h.raid().photo = true; h.console("weather clear"); h.console("time 13");
            r.actors.removeIf(a -> !a.isPlayer);
            for (int i = 0; i < 4; i++) {
                Actor a = r.debugSpawn(i < 2 ? "survivors" : "military", bx + (i - 1.5f) * 1.6f, bz - 5, g);
                a.sexKnown = true; a.female = (i & 1) == 1; a.visual = null; a.yaw = (float) Math.PI; a.hostileToPlayer = false; a.weapon = null;
                cast[i] = a;
            }
            place(r, bx, g, bz + 6, 0, 0);
            h.raid().camOverride = look(bx, g + 1.15f, bz - 0.9f, bx, g + 0.95f, bz - 5, 46);
        });
        at(120, h -> shot(h, "body_00_builds_front"));
        at(124, h -> h.raid().camOverride = look(bx + 4.6f, g + 1.15f, bz - 5, bx, g + 0.95f, bz - 5, 46));
        at(170, h -> shot(h, "body_01_builds_side"));
        at(174, h -> { for (int i = 0; i < 4; i++) cast[i].yaw = 0; h.raid().camOverride = look(bx, g + 1.15f, bz - 0.9f, bx, g + 0.95f, bz - 5, 46); });
        at(220, h -> shot(h, "body_02_builds_back"));
        // ---- a hand to the wound: leg, belly, arm, head
        at(224, h -> { for (int i = 0; i < 4; i++) { cast[i].yaw = (float) Math.PI; } int[] where = {absentialis.character.Rig.R_THIGH, absentialis.character.Rig.TORSO, absentialis.character.Rig.L_UPPER_ARM, absentialis.character.Rig.HEAD}; for (int i = 0; i < 4; i++) if (cast[i].anim != null) cast[i].anim.wound(where[i], 1f); });
        at(262, h -> shot(h, "body_03_wounds"));
        // ---- one of the dead knocked down: twelve frames of it going over and getting up
        at(300, h -> { Raid r = h.raid().raid; r.actors.removeIf(a -> !a.isPlayer); r.freezeAI = true; cast[0] = r.debugSpawn("walker", bx, bz - 4, g); cast[0].yaw = (float) Math.PI / 2; h.raid().camOverride = look(bx + 0.5f, g + 1.3f, bz + 1.8f, bx + 0.8f, g + 0.7f, bz - 4, 50); });
        at(340, h -> h.raid().raid.gore.ragdolls.knockDown(cast[0], 1, 0, 95, 1.0f));
        for (int k = 0; k < 16; k++) { final int kk = k; at(344 + k * 16, h -> shot(h, String.format("body_dead_%03d", kk))); }
        at(344 + 16 * 16, h -> { Actor a = cast[0]; System.out.println("[Scenario] walker: ragdoll " + (a.ragdoll != null) + " recoveries " + h.raid().raid.gore.ragdolls.selfRecoveries + " y " + a.y); sheet("body_dead", 8, 300, 260, 1000, 560, 0.4f); });
        // ---- the Crawler
        final int c0 = 344 + 16 * 16 + 10;
        at(c0, h -> { Raid r = h.raid().raid; r.actors.removeIf(a -> !a.isPlayer); cast[0] = r.debugSpawn("crawler", bx, bz - 4, g); cast[0].yaw = (float) Math.PI / 2; });
        at(c0 + 60, h -> shot(h, "body_crawler_idle"));
        at(c0 + 64, h -> h.raid().raid.gore.ragdolls.knockDown(cast[0], 1, 0, 95, 0.6f));
        for (int k = 0; k < 16; k++) { final int kk = k; at(c0 + 68 + k * 12, h -> shot(h, String.format("body_crawler_%03d", kk))); }
        at(c0 + 68 + 16 * 12, h -> { Actor a = cast[0]; Raid r = h.raid().raid; System.out.println("[Scenario] crawler: ragdoll " + (a.ragdoll != null) + " recoveries " + r.gore.ragdolls.selfRecoveries + " y " + a.y + " (ground " + g + ") unburied " + r.unburied + " rescues " + r.buriedRescues); sheet("body_crawler", 8, 300, 260, 1000, 560, 0.4f); });
        // (and on the move: the gait, from the side)
        final int c1 = c0 + 68 + 16 * 12 + 10;
        at(c1, h -> { Raid r = h.raid().raid; r.freezeAI = false; r.actors.removeIf(a -> !a.isPlayer); cast[0] = r.debugSpawn("crawler", bx - 9, bz - 4, g); Actor v = r.debugSpawn("survivors", bx + 12, bz - 4, g); v.hostileToPlayer = false; r.frozen.add(v.id); cast[1] = v; h.raid().camOverride = look(bx, g + 1.0f, bz + 1.0f, bx, g + 0.5f, bz - 4, 62); });
        for (int k = 0; k < 8; k++) { final int kk = k; at(c1 + 4 + k * 9, h -> { Raid r = h.raid().raid; r.emitSound(cast[1].x, cast[1].y + 1, cast[1].z, 40, "shout", cast[1].id, false); shot(h, String.format("body_crawl_%03d", kk)); }); }
        at(c1 + 80, h -> sheet("body_crawl", 4, 100, 300, 1400, 500, 0.4f));
        // ---- somebody put down wounded: they fall, and the wounded pose takes over where they lie
        final int d0 = c1 + 90;
        at(d0, h -> { Raid r = h.raid().raid; r.freezeAI = true; r.actors.removeIf(a -> !a.isPlayer); cast[0] = r.debugSpawn("military", bx, bz - 4, g); cast[0].yaw = (float) Math.PI / 2; cast[0].hostileToPlayer = false; h.raid().camOverride = look(bx + 0.5f, g + 1.3f, bz + 1.8f, bx + 0.4f, g + 0.7f, bz - 4, 50); });
        at(d0 + 50, h -> h.raid().raid.casualty.down(cast[0], null, "bullet"));
        for (int k = 0; k < 16; k++) { final int kk = k; at(d0 + 54 + k * 9, h -> shot(h, String.format("body_downed_%03d", kk))); }
        at(d0 + 54 + 16 * 9, h -> { Actor a = cast[0]; Raid r = h.raid().raid; System.out.println("[Scenario] wounded: vital " + a.vital + " ragdoll " + (a.ragdoll != null) + " collapses " + r.gore.ragdolls.collapses + " crawling " + a.crawling); sheet("body_downed", 8, 300, 260, 1000, 560, 0.4f); });
        // ---- a long fall: the survivor, put a long way up
        final int f0 = d0 + 54 + 16 * 9 + 10;
        at(f0, h -> { Raid r = h.raid().raid; r.actors.removeIf(a -> !a.isPlayer); h.raid().thirdPerson = true; h.raid().camOverride = null; place(r, bx, g + 78, bz - 4, 0, -0.5f); r.player.vy = 0; r.player.onGround = false; });
        for (int k = 0; k < 12; k++) { final int kk = k; at(f0 + 20 + k * 22, h -> { Raid r = h.raid().raid; if (kk == 0 || kk == 5 || kk == 9) System.out.println("[Scenario] falling: y " + r.player.y + " vy " + r.player.vy + " air " + r.player.airTime + " ragdoll " + (r.player.ragdoll != null)); shot(h, String.format("body_fall_%03d", kk)); }); }
        at(f0 + 20 + 12 * 22, h -> { Raid r = h.raid().raid; System.out.println("[Scenario] after the fall: ragdoll " + (r.player.ragdoll != null) + " hp " + r.player.hp + " falls " + r.gore.ragdolls.fallRagdolls + " y " + r.player.y); sheet("body_fall", 6, 250, 100, 1100, 700, 0.36f); });
    }

    /** The 1.1 HUD in three states: ordinary play in daylight; hurt, low on ammunition and in a fight, indoors; and a promotion. */
    /** The patrol cruiser on the devmap's road: in by the passenger's door (the door open), then sitting at the wheel, seen from both sides and the front. */
    private static void cars11() {
        devmap();
        final float g = g();
        final float rx = DevMap.ROAD_X0 + 30, rz = (DevMap.ROAD_Z0 + DevMap.ROAD_Z1) / 2f + 0.5f;
        final VehicleSystem.Car[] car = new VehicleSystem.Car[1];
        final float[] o = new float[3];
        at(30, h -> { Raid r = h.raid().raid; r.god = true; r.freezeAI = true; h.console("weather clear"); h.console("time 13"); place(r, rx, g, rz, 0, 0); });
        at(40, h -> { Raid r = h.raid().raid; car[0] = r.vehicles.spawn("car.patrol", rx + 6, g, rz, 1.5708f, 0); });
        at(60, h -> { Raid r = h.raid().raid; if (car[0] == null) return; car[0].sim.origin(o);
            // to the side away from the wheel
            for (float s : new float[]{1.7f, -1.7f}) { place(r, o[0] + car[0].sim.body.rightX() * s, g, o[2] + car[0].sim.body.rightZ() * s, 0, 0); if (r.vehicles.passengerSide(car[0])) break; }
            h.raid().thirdPerson = true;
            float sx = r.player.x - o[0], sz = r.player.z - o[2];
            h.raid().camOverride = look(o[0] + sx * 3.2f + car[0].sim.body.fwdX() * 2.5f, g + 1.9f, o[2] + sz * 3.2f + car[0].sim.body.fwdZ() * 2.5f, o[0], g + 0.9f, o[2], 50);
            r.vehicles.enter(); });
        at(60 + 34, h -> shot(h, "cars_00_passenger_door"));
        at(60 + 52, h -> shot(h, "cars_01_across"));
        at(260, h -> { Raid r = h.raid().raid; if (car[0] == null) return; car[0].sim.origin(o);
            h.raid().camOverride = look(o[0] - car[0].sim.body.rightX() * 3.4f + car[0].sim.body.fwdX() * 1.2f, g + 1.5f, o[2] - car[0].sim.body.rightZ() * 3.4f + car[0].sim.body.fwdZ() * 1.2f, o[0], g + 1.0f, o[2], 46); });
        at(290, h -> shot(h, "cars_02_seated_driver_side"));
        at(294, h -> h.raid().camOverride = look(o[0] + car[0].sim.body.fwdX() * 4.2f, g + 1.5f, o[2] + car[0].sim.body.fwdZ() * 4.2f, o[0], g + 1.0f, o[2], 46));
        at(320, h -> shot(h, "cars_03_seated_front"));
        at(324, h -> h.raid().camOverride = look(o[0] + car[0].sim.body.rightX() * 3.4f - car[0].sim.body.fwdX() * 1.0f, g + 2.4f, o[2] + car[0].sim.body.rightZ() * 3.4f - car[0].sim.body.fwdZ() * 1.0f, o[0], g + 1.0f, o[2], 46));
        at(350, h -> shot(h, "cars_04_seated_above"));
        // 1.1: at night, lights on, somebody standing in the beam: their shadow lies on the road and on the one behind them
        at(354, h -> { Raid r = h.raid().raid; h.console("time 23"); car[0].sim.lightsOn = true; car[0].sim.origin(o);
            float fx = car[0].sim.body.fwdX(), fz = car[0].sim.body.fwdZ(), rxx = car[0].sim.body.rightX(), rzz = car[0].sim.body.rightZ();
            Actor a1 = r.debugSpawn("survivors", o[0] + fx * 6.5f + rxx * 0.4f, o[2] + fz * 6.5f + rzz * 0.4f, g), a2 = r.debugSpawn("survivors", o[0] + fx * 10.5f - rxx * 0.5f, o[2] + fz * 10.5f - rzz * 0.5f, g);
            for (Actor a : new Actor[]{a1, a2}) if (a != null) { a.weapon = null; r.freezeOnly(a); }
            h.raid().camOverride = look(o[0] + fx * 4.5f + rxx * 6.5f, g + 3.2f, o[2] + fz * 4.5f + rzz * 6.5f, o[0] + fx * 9.5f, g + 0.6f, o[2] + fz * 9.5f, 62); });
        at(420, h -> shot(h, "cars_05_headlight_shadows"));
    }

    private static final java.util.List<float[][]> VIEWS = new java.util.ArrayList<>();
    private static final java.util.List<String> VIEW_NAMES = new java.util.ArrayList<>();

    /**
     * A hand-built landmark, photographed for review: the outside from the street and from a corner, then every room
     * from a free camera high in its far corner (two corners for a big room). ABS_LANDMARK is the building's
     * archetype (lm_police, lm_spencer, ...), ABS_ROOM_FILTER a comma list of room types, ABS_HOUR the time of day.
     */
    private static void landmark11() {
        profile(null);
        final String arch = System.getenv("ABS_LANDMARK") != null ? System.getenv("ABS_LANDMARK") : "lm_police";
        final String filter = System.getenv("ABS_ROOM_FILTER");
        final int hour = System.getenv("ABS_HOUR") != null ? Integer.parseInt(System.getenv("ABS_HOUR")) : 13;
        Scenarios.hour = hour;
        final int max = 110, step = 60;
        raid(20, h -> {
            Raid r = h.raid().raid;
            r.god = true; r.populate = false; r.freezeAI = true; r.devGhost = true;
            r.actors.removeIf(a -> !a.isPlayer);
            h.console("weather clear");
            absentialis.world.MapData.Building b = null;
            for (absentialis.world.MapData.Building x : r.world.map.buildings) if (x.archetype.equals(arch) && (b == null || x.rooms.size() > b.rooms.size())) b = x;
            if (b == null) { System.out.println("[Scenario] landmark11: no building of archetype " + arch); return; }
            float cx = (b.x0 + b.x1 + 1) / 2f, cz = (b.z0 + b.z1 + 1) / 2f, ex = b.entryX + 0.5f, ez = b.entryZ + 0.5f;
            float ox = ex - cx, oz = ez - cz, ol = Math.max(0.01f, (float) Math.sqrt(ox * ox + oz * oz));
            // the front is the side the entrance is on: straight out from it, along whichever axis it is further from the middle
            float fx = Math.abs(ox) > Math.abs(oz) ? Math.signum(ox) : 0, fz = Math.abs(ox) > Math.abs(oz) ? 0 : Math.signum(oz);
            float gy = absentialis.world.World.GROUND;
            if (filter == null || filter.contains("ext")) {
                view("ext_front", new float[]{ex + fx * 3, gy, ez + fz * 3}, look(ex + fx * 30, gy + 9, ez + fz * 30, cx, gy + 7, cz, 62));
                view("ext_corner", new float[]{ex + fx * 3, gy, ez + fz * 3}, look(ex + fx * 24 - fz * 34, gy + 16, ez + fz * 24 + fx * 34, cx, gy + 5, cz, 60));
                view("ext_entrance", new float[]{ex + fx * 3, gy, ez + fz * 3}, look(ex + fx * 11 + fz * 3, gy + 2.2f, ez + fz * 11 - fx * 3, ex, gy + 4, ez, 68));
                float half = (Math.abs(fx) > 0 ? (b.x1 - b.x0) : (b.z1 - b.z0)) / 2f;
                view("ext_back", new float[]{cx - fx * (half + 3), gy, cz - fz * (half + 3)}, look(cx - fx * (half + 11) + fz * 12, gy + 9, cz - fz * (half + 11) - fx * 12, cx, gy + 5, cz, 80));
                view("ext_roof", new float[]{ex + fx * 3, gy, ez + fz * 3}, look(cx + fx * 6 - fz * 26, gy + 24, cz + fz * 6 + fx * 26, cx, gy + 12, cz, 70));
            }
            java.util.Map<String, Integer> seen = new java.util.HashMap<>();
            for (absentialis.world.MapData.Room rm : b.rooms) {
                if (filter != null) { boolean hit = false; for (String part : filter.split(",")) hit |= rm.type.equals(part); if (!hit) continue; }
                int w = rm.x1 - rm.x0 + 1, d = rm.z1 - rm.z0 + 1;
                if (rm.circulation && seen.merge("circ" + rm.floor + (w > d ? "x" : "z"), 1, Integer::sum) > 1) continue;
                int n = seen.merge(rm.type + rm.floor, 1, Integer::sum);
                String name = String.format("f%s_%s%s", rm.floor < 0 ? "B" : String.valueOf(rm.floor), rm.type, n > 1 ? "_" + n : "");
                float rcx = (rm.x0 + rm.x1 + 1) / 2f, rcz = (rm.z0 + rm.z1 + 1) / 2f;
                int fy = rm.y + 1, hgt = Math.max(3, rm.height);
                // the free cells of the room; the first camera in the one furthest from the middle, the second in the one furthest from the first
                float camY = fy + Math.min(2.9f, hgt - 1.3f), fov = w * d > 150 ? 82 : 76;
                float bx = rcx, bz = rcz, bd = -1;
                for (int x = rm.x0; x <= rm.x1; x++) for (int z = rm.z0; z <= rm.z1; z++) {
                    if (r.world.solid(x, fy, z) || r.world.solid(x, fy + 1, z) || r.world.solid(x, fy + 2, z) || !r.world.solid(x, fy - 1, z)) continue;
                    if (!seesMiddle(r, x + 0.5f, camY, z + 0.5f, rcx, fy + 0.9f, rcz)) continue;
                    float dd = (x + 0.5f - rcx) * (x + 0.5f - rcx) + (z + 0.5f - rcz) * (z + 0.5f - rcz);
                    if (dd > bd) { bd = dd; bx = x + 0.5f; bz = z + 0.5f; }
                }
                float tx = rcx + (rcx - bx) * 0.25f, tz = rcz + (rcz - bz) * 0.25f;
                if (rm.height > 7) {
                    // a tall room: from the door end at eye height down its length; back from the far end at the height of a gallery;
                    // across it from one side; and from high in a front corner
                    boolean alongX = Math.abs(fx) > 0;
                    float nearX = alongX ? (fx > 0 ? rm.x1 - 0.5f : rm.x0 + 1.5f) : rcx, nearZ = alongX ? rcz : (fz > 0 ? rm.z1 - 0.5f : rm.z0 + 1.5f);
                    float farX = alongX ? (fx > 0 ? rm.x0 + 1.5f : rm.x1 - 0.5f) : rcx, farZ = alongX ? rcz : (fz > 0 ? rm.z0 + 1.5f : rm.z1 - 0.5f);
                    float sideX = alongX ? rcx : rm.x0 + 3.5f, sideZ = alongX ? rm.z0 + 3.5f : rcz, otherX = alongX ? rcx : rm.x1 - 2.5f, otherZ = alongX ? rm.z1 - 2.5f : rcz;
                    view(name + "_entry", new float[]{nearX, fy, nearZ}, look(nearX, fy + 1.7f, nearZ, farX, fy + 3.4f, farZ, 92));
                    view(name + "_back", new float[]{nearX, fy, nearZ}, look(farX + (nearX - farX) * 0.06f, fy + 8.7f, farZ + (nearZ - farZ) * 0.06f, nearX, fy + 1.5f, nearZ, 92));
                    view(name + "_across", new float[]{nearX, fy, nearZ}, look(alongX ? nearX + (farX - nearX) * 0.2f : rm.x0 + 1.5f, fy + 1.7f, alongX ? rm.z0 + 1.5f : nearZ + (farZ - nearZ) * 0.2f, alongX ? farX : rm.x1 - 1f, fy + 3.2f, alongX ? rm.z1 - 1f : farZ, 92));
                    view(name + "_gallery", new float[]{nearX, fy, nearZ}, look(alongX ? nearX : rm.x0 + 1.5f, fy + 8.7f, alongX ? rm.z0 + 1.5f : nearZ, alongX ? farX : rm.x1 - 2f, fy + 3f, alongX ? rm.z1 - 2f : farZ, 92));
                    continue;
                }
                view(name + "_a", new float[]{bx, fy, bz}, look(bx + (rcx - bx) * 0.06f, camY, bz + (rcz - bz) * 0.06f, tx, fy + 0.9f, tz, fov));
                if (w * d >= 60 || rm.height > 7) {
                    float sx = rcx, sz = rcz, sd = -1;
                    for (int x = rm.x0; x <= rm.x1; x++) for (int z = rm.z0; z <= rm.z1; z++) {
                        if (r.world.solid(x, fy, z) || r.world.solid(x, fy + 1, z) || r.world.solid(x, fy + 2, z) || !r.world.solid(x, fy - 1, z)) continue;
                        if (!seesMiddle(r, x + 0.5f, camY, z + 0.5f, rcx, fy + 0.9f, rcz)) continue;
                        float dd = (x + 0.5f - bx) * (x + 0.5f - bx) + (z + 0.5f - bz) * (z + 0.5f - bz);
                        if (dd > sd) { sd = dd; sx = x + 0.5f; sz = z + 0.5f; }
                    }
                    view(name + "_b", new float[]{sx, fy, sz}, look(sx + (rcx - sx) * 0.06f, camY, sz + (rcz - sz) * 0.06f, rcx + (rcx - sx) * 0.25f, fy + 0.9f, rcz + (rcz - sz) * 0.25f, fov));
                }
                if (rm.height > 7) {
                    // a tall room is also looked at from half way up, and straight down its long axis from the door end
                    view(name + "_high", new float[]{bx, fy, bz}, look(bx + (rcx - bx) * 0.1f, fy + rm.height - 3.2f, bz + (rcz - bz) * 0.1f, rcx, fy + 2.5f, rcz, 84));
                    float lx = w >= d ? rm.x0 + 1.5f : rcx, lz = w >= d ? rcz : rm.z0 + 1.5f, hx2 = w >= d ? rm.x1 - 0.5f : rcx, hz2 = w >= d ? rcz : rm.z1 - 0.5f;
                    float nearX = Math.abs(ex - lx) + Math.abs(ez - lz) < Math.abs(ex - hx2) + Math.abs(ez - hz2) ? lx : hx2, nearZ = nearX == lx ? lz : hz2, farX = nearX == lx ? hx2 : lx, farZ = nearX == lx ? hz2 : lz;
                    view(name + "_axis", new float[]{nearX, fy, nearZ}, look(nearX, fy + 2.0f, nearZ, farX, fy + 3.2f, farZ, 84));
                }
            }
            System.out.println("[Scenario] landmark11: " + arch + ", " + VIEWS.size() + " views of " + b.rooms.size() + " rooms; entrance " + b.entryX + "," + b.entryY + "," + b.entryZ);
        });
        for (int k = 0; k < max; k++) {
            final int n = k;
            raid(40 + k * step, h -> {
                if (n >= VIEWS.size()) return;
                float[] at = VIEWS.get(n)[0];
                place(h.raid().raid, at[0], at[1], at[2], 0, 0);
                h.raid().thirdPerson = false;
                h.raid().camOverride = VIEWS.get(n)[1];
            });
            raid(40 + k * step + step - 4, h -> { if (n < VIEWS.size()) shot(h, String.format("%02d_%s", n, VIEW_NAMES.get(n))); });
        }
    }
    private static void view(String name, float[] stand, float[] cam) { VIEWS.add(new float[][]{stand, cam}); VIEW_NAMES.add(name); }
    /** Nothing solid within the first few metres of the line from a camera to the middle of its room (a camera is not put behind a column). */
    private static boolean seesMiddle(Raid r, float x, float y, float z, float tx, float ty, float tz) {
        float dx = tx - x, dy = ty - y, dz = tz - z, len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        for (float t = 0.4f; t < Math.min(len, 6f); t += 0.3f) if (r.world.solid((int) Math.floor(x + dx / len * t), (int) Math.floor(y + dy / len * t), (int) Math.floor(z + dz / len * t))) return false;
        return true;
    }

    /** Every animal, close to, on the devmap's road: standing, then doing what it does. */
    private static void animals11() {
        devmap();
        final float g = g();
        final float rx = DevMap.ROAD_X0 + 30, rz = (DevMap.ROAD_Z0 + DevMap.ROAD_Z1) / 2f + 0.5f;
        final Animals.Animal[] an = new Animals.Animal[12];
        at(30, h -> { Raid r = h.raid().raid; r.god = true; r.devGhost = true; r.freezeAI = true; h.console("weather clear"); h.console("time 13"); place(r, rx - 12, g, rz, 0, 0);
            an[0] = r.animals.spawn(Animals.Kind.DOG, rx, g, rz); an[1] = r.animals.spawn(Animals.Kind.IDOG, rx + 2.2f, g, rz);
            an[2] = r.animals.spawn(Animals.Kind.CHICKEN, rx + 4f, g, rz); an[3] = r.animals.spawn(Animals.Kind.CROW, rx + 5.2f, g, rz);
            an[4] = r.animals.spawn(Animals.Kind.BAT, rx + 6.2f, g + 0.6f, rz); an[4].homeY = g + 0.6f;
            an[5] = r.animals.spawn(Animals.Kind.SPIDER, rx + 9f, g, rz); an[6] = r.animals.spawn(Animals.Kind.BROOD, rx + 13f, g, rz);
            for (int i = 0; i < 7; i++) an[i].yaw = (float) Math.PI * 0.75f;
            Actor man = r.debugSpawn("survivors", rx - 1.6f, rz, g); if (man != null) { man.yaw = (float) Math.PI * 0.75f; r.freezeOnly(man); }
            h.raid().camOverride = look(rx + 5, g + 2.6f, rz + 9.5f, rx + 6, g + 0.7f, rz, 58); });
        at(120, h -> shot(h, "animals_00_lineup"));
        at(124, h -> h.raid().camOverride = look(rx + 1, g + 1.2f, rz + 3.4f, rx + 1.1f, g + 0.6f, rz, 50));
        at(150, h -> shot(h, "animals_01_dogs"));
        at(154, h -> h.raid().camOverride = look(rx + 5, g + 0.9f, rz + 2.6f, rx + 5.1f, g + 0.4f, rz, 42));
        at(180, h -> shot(h, "animals_02_birds"));
        at(184, h -> h.raid().camOverride = look(rx + 11, g + 2.2f, rz + 5.5f, rx + 11, g + 0.6f, rz, 52));
        at(210, h -> shot(h, "animals_03_spiders"));
        // alive: the dog at heel of the survivor, the crow flushed, the pack running a man down
        at(214, h -> { Raid r = h.raid().raid; r.freezeAI = false; r.devGhost = false; r.animals.all.clear(); h.raid().thirdPerson = true;
            place(r, rx, g, rz, (float) -Math.PI / 2, 0);
            an[0] = r.animals.dogFor(r.player); an[3] = r.animals.spawn(Animals.Kind.CROW, rx + 2.2f, g, rz - 1.5f);
            h.raid().camOverride = look(rx - 3.5f, g + 1.9f, rz + 4.5f, rx + 0.8f, g + 0.8f, rz, 55); });
        at(240, h -> shot(h, "animals_04_flush"));
        at(420, h -> shot(h, "animals_05_heel"));
        at(424, h -> { Raid r = h.raid().raid; r.animals.pack(rx + 22, g, rz, 4); Actor man = r.debugSpawn("raiders", rx + 9, rz, g); if (man != null) man.weapon = null;
            h.raid().camOverride = look(rx + 7, g + 2.2f, rz + 6.5f, rx + 11, g + 0.7f, rz, 55); });
        at(500, h -> shot(h, "animals_06_pack"));
        at(560, h -> shot(h, "animals_07_pack_on"));
    }

    private static void hud11() {
        profile(p -> { give(p, "weapon.akr", 1, true); give(p, "ammo.assault", 30, true); give(p, "ammo.assault", 30, true); give(p, "med.bandage", 2, false); });
        devmap();
        final float g = g();
        final float rx = DevMap.ROAD_X0 + 30, rz = (DevMap.ROAD_Z0 + DevMap.ROAD_Z1) / 2f + 0.5f;
        at(30, h -> { Raid r = h.raid().raid; r.god = true; h.console("weather clear"); h.console("time 13"); h.raid().scenarioHold("primary"); place(r, rx, g, rz, (float) -Math.PI / 2, -0.05f); });
        at(200, h -> shot(h, "hud_00_idle"));
        // everything asked for at once (the status key), with a notice or two and a container searched
        at(204, h -> { Raid r = h.raid().raid; h.raid().hud11.peek(); h.raid().hud.message("Picked up Assault Rifle Ammo x30", absentialis.ui.Ui.TXT, r.time); h.raid().hud.message("Map updated: Devmap Yard", absentialis.ui.Ui.TXT, r.time); r.debrief.containersSearched++; r.body.stamina = 46; r.body.staminaChanged = r.time; r.body.focus = 70; r.body.focusChanged = r.time; });
        at(290, h -> shot(h, "hud_01_peek"));
        // in a fight, in the dark: hurt, bleeding, three rounds left, kills in the feed, the Rank shown after them
        at(294, h -> { Raid r = h.raid().raid; r.god = false; h.console("time 23"); var mn = DevMap.mansion; float mx = (mn.x0 + mn.x1) / 2f + 0.5f, mz = mn.entryZ + 6.5f; int fy = r.standY((int) mx, (int) mz, (int) g); place(r, mx, fy, mz, 0, -0.05f);
            r.player.hp = 31; r.body.healthChanged = r.time; r.body.bleedLight = true; r.body.legInjury = 0.7f; r.body.energy = 14; r.body.hydration = 40;
            var w = r.player.weapon; if (w != null) { w.rounds = 3; } r.god = true; });
        at(330, h -> { Raid r = h.raid().raid; for (int i = 0; i < 3; i++) { Actor z = r.debugSpawn(i == 2 ? "raiders" : "walker", r.player.x + i - 1, r.player.z - 5, r.player.y); if (z != null) r.damageActor(z, r.player, 9999, absentialis.gameplay.items.Content.def("ammo.assault"), "head", z.x, z.y + 1.6f, z.z); } h.raid().hud.message("Somebody is bleeding nearby", absentialis.ui.Ui.AMBER, r.time); });
        at(380, h -> shot(h, "hud_02_fight_dark"));
        // the promotion
        at(384, h -> { Raid r = h.raid().raid; h.console("time 13"); place(r, rx, g, rz, (float) -Math.PI / 2, -0.05f); r.player.hp = r.player.maxHp; r.body.bleedLight = false; r.body.legInjury = 0; r.body.energy = 80; h.raid().xpFeed.noticeRank = 12; h.raid().xpFeed.noticeAt = r.time; });
        at(384 + 30, h -> shot(h, "hud_03_promotion_early"));
        at(384 + 110, h -> shot(h, "hud_04_promotion"));
    }
}
