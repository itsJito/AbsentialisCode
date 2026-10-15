package absentialis.scene;

import static absentialis.scene.Scenarios.*;

import absentialis.entity.Actor;
import absentialis.neuralai.animal.AnimalBrain;
import java.util.ArrayList;
import java.util.List;

/**
 * The scripts added for 1.3 (see {@link ScenarioScripts}).
 *
 *   ABS_SCENARIO=spots13 ... photographs places in the generated city by eye: ABS_SPOTS="x,y,z;x,y,z;..." (the
 *                             coordinates a player reads off the HUD). Each place is looked at from up to four sides,
 *                             from a free cell near it, at eye height. Used to judge reported props and rooms.
 */
final class ScenarioScripts13 {
    private ScenarioScripts13() {}

    static boolean build(String name) {
        switch (name == null ? "" : name) {
            case "spots13" -> spots13();
            case "anims13" -> anims13();
            default -> { return false; }
        }
        return true;
    }

    private static final List<float[][]> VIEWS = new ArrayList<>();
    private static final List<String> NAMES = new ArrayList<>();

    private static void spots13() {
        profile(p -> java.util.Arrays.fill(p.zone.power, true));
        Scenarios.hour = 13;
        final String spec = System.getenv("ABS_SPOTS") == null ? "155,23,406" : System.getenv("ABS_SPOTS");
        final int step = 70;
        raid(20, h -> {
            Raid r = h.raid().raid;
            r.god = true; r.populate = false; r.freezeAI = true; r.devGhost = true;
            r.actors.removeIf(a -> !a.isPlayer);
            h.console("weather clear");
            h.raid().photo = true;
            // "glassdoors[:n]": closed glass doors with another door behind them, looked at through the glass
            if (spec.startsWith("glassdoors")) {
                int want = spec.contains(":") ? Integer.parseInt(spec.substring(spec.indexOf(':') + 1)) : 6;
                for (Doors.Door d : r.doors.doors) {
                    if (VIEWS.size() >= want || d.mobile || d.kind != Doors.GLASS) continue;
                    float nx = d.alongX ? 0 : 1, nz = d.alongX ? 1 : 0;
                    for (int side = -1; side <= 1; side += 2) {
                        Doors.Door behind = null;
                        for (Doors.Door e : r.doors.doors) if (e != d && !e.mobile && e.y == d.y && Math.abs((e.midX() - d.midX()) * nz - (e.midZ() - d.midZ()) * nx) < 2.5f) {
                            float along = ((e.midX() - d.midX()) * nx + (e.midZ() - d.midZ()) * nz) * side;
                            if (along > 1.5f && along < 9) { behind = e; break; }
                        }
                        if (behind == null) continue;
                        float camX = d.midX() - nx * side * 2.2f, camZ = d.midZ() - nz * side * 2.2f;
                        if (r.world.solid((int) Math.floor(camX), d.y, (int) Math.floor(camZ)) || r.world.solid((int) Math.floor(camX), d.y + 1, (int) Math.floor(camZ))) continue;
                        VIEWS.add(new float[][]{{camX, d.y, camZ}, ScenarioScripts11.look(camX, d.y + 1.6f, camZ, behind.midX(), d.y + 1.0f, behind.midZ(), 70)});
                        NAMES.add("glassdoor_" + d.x + "_" + d.y + "_" + d.z);
                        break;
                    }
                }
            }
            else for (String q : spec.split(";")) {
                String[] s = q.trim().split(",");
                if (s.length < 3) continue;
                float x = Float.parseFloat(s[0]) + 0.5f, y = Float.parseFloat(s[1]), z = Float.parseFloat(s[2]) + 0.5f;
                int fy = (int) Math.floor(y) + 1;
                String base = s[0] + "_" + s[1] + "_" + s[2];
                int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
                int got = 0;
                for (int[] d : dirs) {
                    if (got >= 3) break;
                    // the furthest free cell (up to 4 out) along the direction, with nothing solid between it and the spot
                    float best = -1;
                    for (int k = 4; k >= 2; k--) {
                        int cx = (int) Math.floor(x + d[0] * k), cz = (int) Math.floor(z + d[1] * k);
                        if (r.world.solid(cx, fy, cz) || r.world.solid(cx, fy + 1, cz)) continue;
                        boolean clear = true;
                        for (float t = 0.9f; t < k - 0.3f; t += 0.25f) {
                            int bx = (int) Math.floor(x + d[0] * t), bz = (int) Math.floor(z + d[1] * t);
                            if (r.world.solid(bx, fy + 1, bz)) { clear = false; break; }
                        }
                        if (clear) { best = k; break; }
                    }
                    if (best < 0) continue;
                    float camX = x + d[0] * best, camZ = z + d[1] * best;
                    VIEWS.add(new float[][]{{camX, fy, camZ}, ScenarioScripts11.look(camX, fy + 1.6f, camZ, x, fy + 0.6f, z, 74)});
                    NAMES.add(base + "_" + got);
                    got++;
                }
                if (got == 0) {
                    VIEWS.add(new float[][]{{x, fy, z}, ScenarioScripts11.look(x + 0.01f, fy + 4f, z + 2.5f, x, fy, z, 80)});
                    NAMES.add(base + "_over");
                }
            }
            System.out.println("[Scenario] spots13: " + VIEWS.size() + " views");
        });
        for (int k = 0; k < 60; k++) {
            final int n = k;
            raid(40 + k * step, h -> {
                if (n >= VIEWS.size()) return;
                float[] at = VIEWS.get(n)[0];
                place(h.raid().raid, at[0], at[1], at[2], 0, 0);
                h.raid().thirdPerson = false;
                h.raid().camOverride = VIEWS.get(n)[1];
            });
            raid(40 + k * step + step - 4, h -> { if (n < VIEWS.size()) shot(h, String.format("%02d_%s", n, NAMES.get(n))); });
        }
    }

    /**
     * ABS_SCENARIO=anims13: animation studies on the devmap, as frame strips (run with ABS_FIXED_DT=1). ABS_PART picks
     * one: "reload" (a handgun's tactical and empty reloads, from the side, the front and the eyes).
     */
    private static void anims13() {
        String part = System.getenv("ABS_PART") == null ? "reload" : System.getenv("ABS_PART");
        if (part.contains("discard")) {
            // the discard case in the hideout: it appears with a drag under way; two things dropped in it, the buttons
            profile(p -> { give(p, "weapon.pm9", 1, false); give(p, "med.bandage", 2, false); give(p, "food.egg_brown", 1, false); });
            hideout(10, h -> h.hideout().tab = HideoutScreen.TAB_INVENTORY);
            hideout(30, h -> { var inv = h.ctx().inv; var st = h.ctx().profile.stash; if (!st.items.isEmpty()) inv.drag = st.items.get(0); });
            hideout(40, h -> shot(h, "discard13_00_dragging"));
            hideout(42, h -> { var inv = h.ctx().inv; inv.drag = null; var st = h.ctx().profile.stash; int n = 0;
                for (var it : new java.util.ArrayList<>(st.items)) { if (n >= 2) break; int[] sp = inv.discard.findSpace(it); if (sp != null && absentialis.gameplay.inventory.InventoryService.move(it, inv.discard, sp[0], sp[1], false).ok()) n++; } });
            hideout(56, h -> shot(h, "discard13_01_holding"));
            return;
        }
        profile(p -> { give(p, "weapon.pm9", 1, true); give(p, "weapon.akr", 1, true); });
        ScenarioScripts11.devmap();
        final float g = absentialis.world.World.GROUND, rx = absentialis.world.gen.DevMap.ROAD_X0 + 30, rz = (absentialis.world.gen.DevMap.ROAD_Z0 + absentialis.world.gen.DevMap.ROAD_Z1) / 2f + 0.5f;
        if (part.contains("animals")) {
            // sixty seconds of footage, a frame every fifteenth: six subjects, ten seconds each, walked down the road by script
            final Object[][] subjects = {{Animals.Kind.DOG, AnimalBrain.Move.WALK, 1f, "dog_walk"}, {Animals.Kind.DOG, AnimalBrain.Move.RUN, 0.45f, "dog_trot"},
                    {Animals.Kind.DOG, AnimalBrain.Move.RUN, 1f, "dog_gallop"}, {Animals.Kind.CHICKEN, AnimalBrain.Move.WALK, 1f, "hen_walk"},
                    {Animals.Kind.SPIDER, AnimalBrain.Move.WALK, 1f, "spider_walk"}, {Animals.Kind.CROW, AnimalBrain.Move.WALK, 1f, "crow_hop"}};
            final Animals.Animal[] cur = new Animals.Animal[1];
            final float startX = absentialis.world.gen.DevMap.ROAD_X0 + 8;
            ScenarioScripts11.at(30, h -> { Raid r = h.raid().raid; r.god = true; r.freezeAI = true; h.console("weather clear"); h.console("time 13"); h.raid().photo = true; place(r, startX - 20, g, rz + 6, 0, 0); });
            int f = 40;
            final String only = System.getenv("ABS_SUBJECT");
            for (Object[] sub : subjects) {
                if (only != null && !((String) sub[3]).contains(only)) continue;
                final Animals.Kind kind = (Animals.Kind) sub[0];
                final AnimalBrain.Move move = (AnimalBrain.Move) sub[1];
                final float speed = (Float) sub[2];
                final String name = (String) sub[3];
                ScenarioScripts11.at(f, h -> { Raid r = h.raid().raid; r.animals.all.clear(); cur[0] = r.animals.spawn(kind, startX, g, rz); cur[0].yaw = (float) -Math.PI / 2;
                    r.animals.script = (an, in) -> in.go(move, an.x + 30, g, rz, speed); });
                for (int k = 0; k < 660; k++) {
                    final int kk = k;
                    ScenarioScripts11.at(f + 1 + k, h -> { Animals.Animal an = cur[0]; if (an == null) return;
                        float d = an.kind == Animals.Kind.SPIDER ? 3.6f : an.kind == Animals.Kind.DOG ? 2.8f : 1.9f;
                        h.raid().camOverride = ScenarioScripts11.look(an.x - 0.3f, g + an.kind.height * 0.9f + 0.25f, an.z + d, an.x, g + an.kind.height * 0.45f, an.z, 50); });
                    if (k >= 60 && (k - 60) % 15 == 0) ScenarioScripts11.at(f + 2 + k, h -> shot(h, String.format("anim13_%s_%02d", name, (kk - 60) / 15)));
                }
                f += 670;
            }
            ScenarioScripts11.at(f, h -> h.raid().raid.animals.script = null);
        }
        if (part.contains("infected")) {
            // the dead on the road: a shambler, a runner, a Crawler; then a shambler through a window and one through a hole at the
            // foot of a wall, and a Crawler through the same hole. Ten seconds each, a frame every fifteenth
            final Object[][] subjects = {{"walker", 1f, "walk", 0}, {"sprinter", 1f, "run", 0}, {"crawler", 1f, "crawler", 0},
                    {"walker", 1f, "window", 1}, {"walker", 1f, "hole", 2}, {"crawler", 1f, "crawlhole", 2}};
            final Actor[] cur = new Actor[1];
            final float startX = absentialis.world.gen.DevMap.ROAD_X0 + 8, wallX = startX + 2.6f;
            final String only = System.getenv("ABS_SUBJECT");
            ScenarioScripts11.at(30, h -> { Raid r = h.raid().raid; r.god = true; r.freezeAI = true; h.console("weather clear"); h.console("time 13"); h.raid().photo = true; place(r, startX - 20, g, rz + 9, 0, 0); r.actors.removeIf(x -> !x.isPlayer); });
            int f = 40;
            for (Object[] sub : subjects) {
                if (only != null && !((String) sub[2]).contains(only)) continue;
                final String arch = (String) sub[0], name = (String) sub[2];
                final int wall = (Integer) sub[3];
                ScenarioScripts11.at(f, h -> {
                    Raid r = h.raid().raid;
                    r.actors.removeIf(x -> !x.isPlayer && x != r.player);
                    // the wall across the road (or none): a window one block square with its sill a block up, or a hole at its foot
                    int wx = (int) wallX, z0 = (int) rz - 4, z1 = (int) rz + 4, gy = (int) g;
                    for (int z = z0; z <= z1; z++) for (int y = gy; y < gy + 4; y++) r.world.set(wx, y, z, wall == 0 ? absentialis.world.Block.AIR : absentialis.world.Block.BRICK_RED);
                    if (wall == 1) r.world.set(wx, gy + 1, (int) rz, absentialis.world.Block.AIR);
                    if (wall == 2) r.world.set(wx, gy, (int) rz, absentialis.world.Block.AIR);
                    cur[0] = r.debugSpawn(arch, startX, rz, g);
                    if (cur[0] == null) return;
                    cur[0].hostileToPlayer = false; cur[0].hp = cur[0].maxHp = 9999;
                    r.scripted.clear(); r.scripted.add(cur[0].id);
                    final float tx = startX + 30;
                    r.scriptMind = (who, dt) -> { absentialis.neuralai.integration.Archetypes.Arch ar = absentialis.neuralai.integration.Archetypes.arch(who.archetype);
                        if (who.breachT > 0) { absentialis.neuralai.integration.Breach.climb(r, who, dt); return; }
                        absentialis.neuralai.integration.Steering.goTo(r, who, tx, rz, name.equals("run") ? ar.runSpeed() : ar.speed(), dt); };
                });
                for (int k = 0; k < 660; k++) {
                    final int kk = k;
                    ScenarioScripts11.at(f + 1 + k, h -> { Actor an = cur[0]; if (an == null) return;
                        boolean low = "crawler".equals(an.archetype); h.raid().camOverride = low ? ScenarioScripts11.look(an.x - 1.6f, an.y + 1.7f, an.z + 3.0f, an.x, an.y + 0.35f, an.z, 50) : ScenarioScripts11.look(an.x - 0.5f, an.y + 1.4f, an.z + 4.2f, an.x, an.y + 0.9f, an.z, 52); });
                    if (k >= 60 && (k - 60) % 15 == 0) ScenarioScripts11.at(f + 2 + k, h -> shot(h, String.format("inf13_%s_%02d", name, (kk - 60) / 15)));
                }
                f += 670;
            }
            ScenarioScripts11.at(f, h -> { h.raid().raid.scriptMind = null; h.raid().raid.scripted.clear(); });
        }
        if (part.contains("objects")) {
            // the containers and a boarded window, each against a brick wall on the road, close to
            final int[] ids = {absentialis.world.Block.MED_CABINET, absentialis.world.Block.TOOLBOX, absentialis.world.Block.CRATE_MIL, absentialis.world.Block.CRATE, absentialis.world.Block.LOCKER, absentialis.world.Block.FILING, absentialis.world.Block.SAFE, absentialis.world.Block.TRASH};
            final int ox = (int) (absentialis.world.gen.DevMap.ROAD_X0 + 12), oz = (int) rz - 2, gy = (int) g;
            ScenarioScripts11.at(30, h -> { Raid r = h.raid().raid; r.god = true; r.freezeAI = true; h.console("weather clear"); h.console("time 13"); h.raid().photo = true;
                for (int x = ox - 1; x <= ox + ids.length * 2 + 2; x++) for (int y = gy; y < gy + 3; y++) r.world.set(x, y, oz - 1, absentialis.world.Block.BRICK_RED);
                for (int i = 0; i < ids.length; i++) r.world.set(ox + i * 2, gy, oz, ids[i]);
                // a window in the wall, boarded
                int wx = ox + ids.length * 2 + 1; r.world.set(wx, gy + 1, oz - 1, absentialis.world.Block.BOARDED);
                place(r, ox - 4, g, oz + 4, 0, 0); });
            for (int i = 0; i <= ids.length; i++) {
                final int k = i;
                ScenarioScripts11.at(80 + i * 30, h -> { float cx = ox + k * 2 + (k == ids.length ? 1 : 0) + 0.5f, cy = g + (k == ids.length ? 1.5f : 0.5f);
                    h.raid().camOverride = ScenarioScripts11.look(cx + 0.9f, g + 1.3f, oz + 2.1f, cx, cy, oz + 0.3f, 50); });
                ScenarioScripts11.at(80 + i * 30 + 26, h -> shot(h, String.format("obj13_%02d", k)));
            }
        }
        if (part.contains("reload")) {
            final String[] clips = {"reload_tac", "reload_empty"};
            final float[][] cams = {{rx - 2.7f, g + 1.15f, rz - 0.9f, 48}, {rx + 0.9f, g + 1.35f, rz - 2.9f, 46}, null};
            final String[] views = {"side", "front", "eyes"};
            int f = 30;
            ScenarioScripts11.at(f, h -> { Raid r = h.raid().raid; r.god = true; r.freezeAI = true; h.console("weather clear"); h.console("time 13"); h.raid().photo = true; });
            f += 10;
            for (int ci = 0; ci < clips.length; ci++) for (int vi = 0; vi < views.length; vi++) {
                final int c = ci, v = vi;
                ScenarioScripts11.at(f, h -> { Raid r = h.raid().raid; place(r, rx, g, rz, 0, 0); h.raid().scenarioHold("sidearm");
                    h.raid().thirdPerson = cams[v] != null; h.raid().photo = cams[v] != null; h.raid().camOverride = cams[v] == null ? null : ScenarioScripts11.look(cams[v][0], cams[v][1], cams[v][2], rx, g + 1.15f, rz - 0.3f, cams[v][3]); });
                f += 40;
                ScenarioScripts11.at(f, h -> h.raid().raid.player.anim.play(clips[c], c == 0 ? 1.9f : 2.3f));
                for (int k = 0; k < 16; k++) { final int kk = k; ScenarioScripts11.at(f + 1 + k * 9, h -> shot(h, String.format("reload13_%s_%s_%02d", clips[c].substring(7), views[v], kk))); }
                f += 160;
            }
        }
    }
}
