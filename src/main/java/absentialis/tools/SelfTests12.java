package absentialis.tools;

import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.gameplay.items.Content;
import absentialis.neuralai.integration.Steering;
import absentialis.persistence.Profile;
import absentialis.scene.Raid;
import absentialis.world.Block;
import absentialis.world.NativeLink;
import absentialis.world.World;
import astratto.bridge.AstrattoNative;
import astratto.bridge.NativeEngine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Quality gates for 1.2. Headless, like the rest; `gradlew selftest`, or ABS_SELFTEST=v12 (groups: native12 boundary12
 * here; animals12 bugs12 combat12 ui12 in {@link SelfTests12b}). Each gate says in its own text what it holds the game to.
 */
final class SelfTests12 {
    private SelfTests12() {}

    static void run(String which, boolean all, SelfTests04.Check t) {
        if (all || which.contains("v12") || which.contains("native12")) SelfTests.guard(() -> nativeCore(t));
        if (all || which.contains("v12") || which.contains("boundary12")) SelfTests.guard(() -> boundary(t));
        SelfTests12b.run(which, all, t);
    }

    static final int G = World.GROUND;

    // ================================================================== the native core
    /**
     * The Astratto native core against the Java it replaces. The route search and the line of sight were moved to C++
     * as they stood; these gates hold the two to the same answer, cell for cell, on the generated city - which is what
     * makes it safe to have moved them, and what will say so first if either is changed without the other.
     */
    private static void nativeCore(SelfTests04.Check t) {
        System.out.println("[v1.2 native core: the engine's C++ side, held to the Java it replaces]");
        Content.load();
        NativeEngine e = NativeLink.engine();
        t.check("the Astratto native core is on this machine and speaks the interface the bindings were written for", e != null && AstrattoNative.apiVersion() >>> 16 == AstrattoNative.API_MAJOR,
                e == null ? "none: " + (NativeLink.enabled ? AstrattoNative.reason() : "ABS_NATIVE=0") : AstrattoNative.buildInfo());
        if (e == null) return;

        World city = SelfTests06.world(1337L);
        World.DoorOracle oracle = city.doors;
        city.doors = null;
        NativeLink link = NativeLink.attach(city);
        t.check("the generated city is given to the engine whole, in one call", link != null && e.stats().worldX == World.W && e.stats().worldY == World.H && e.stats().worldZ == World.D, e.lastError());
        if (link == null) { city.doors = oracle; return; }
        try {
            Random rnd = new Random(12);
            // ---- routes: the same question to both, four hundred times
            List<float[]> qs = new ArrayList<>();
            while (qs.size() < 400) {
                int x = World.CITY_MIN + 6 + rnd.nextInt(World.CITY_MAX - World.CITY_MIN - 12), z = World.CITY_MIN + 6 + rnd.nextInt(World.CITY_MAX - World.CITY_MIN - 12);
                int y = city.nav.stepY(x, z, G + (rnd.nextInt(5) == 0 ? 4 + rnd.nextInt(8) : 0), true);
                if (y < 0) continue;
                float reach = rnd.nextInt(4) == 0 ? 60 : 24;
                float tx = x + 0.5f + (rnd.nextFloat() - 0.5f) * 2 * reach, tz = z + 0.5f + (rnd.nextFloat() - 0.5f) * 2 * reach;
                boolean floor = rnd.nextInt(3) == 0;
                qs.add(new float[]{x + 0.3f + rnd.nextFloat() * 0.4f, y, z + 0.3f + rnd.nextFloat() * 0.4f, tx, floor ? G + rnd.nextInt(9) : Float.NaN, tz, rnd.nextInt(4) == 0 ? 0 : 1, new int[]{900, 3600, 7000, 9000}[rnd.nextInt(4)]});
            }
            int[][] nat = new int[qs.size()][];
            boolean[] answered = new boolean[qs.size()];
            for (int i = 0; i < qs.size(); i++) { float[] q = qs.get(i); final int k = i; link.requestPath(q[0], q[1], q[2], q[3], q[4], q[5], (int) q[7], q[6] > 0, c -> { nat[k] = c; answered[k] = true; }); }
            long framesBefore = e.stats().frames;
            long t0 = System.nanoTime();
            link.flush(1 / 60f);
            double nativeMs = (System.nanoTime() - t0) / 1e6;
            int got = 0, same = 0, found = 0, cells = 0;
            String first = "";
            t0 = System.nanoTime();
            int[][] ref = new int[qs.size()][];
            for (int i = 0; i < qs.size(); i++) { float[] q = qs.get(i); ref[i] = Float.isNaN(q[4]) ? city.nav.path(q[0], q[1], q[2], q[3], q[5], (int) q[7], q[6] > 0) : city.nav.path(q[0], q[1], q[2], q[3], q[4], q[5], (int) q[7], q[6] > 0); }
            double javaMs = (System.nanoTime() - t0) / 1e6;
            for (int i = 0; i < qs.size(); i++) {
                if (answered[i]) got++;
                if (ref[i] != null) { found++; cells += ref[i].length / 3; }
                if (Arrays.equals(ref[i], nat[i])) same++;
                else if (first.isEmpty()) first = " - first difference at question " + i + " " + Arrays.toString(qs.get(i)) + ": Java " + (ref[i] == null ? "none" : ref[i].length / 3 + " cells") + ", native " + (nat[i] == null ? "none" : nat[i].length / 3 + " cells");
            }
            t.check("four hundred routes across the city go over in one batch and all come back in the same call", got == 400 && e.stats().frames - framesBefore <= 2, got + " answered in " + (e.stats().frames - framesBefore) + " engine frame(s)");
            t.check("every one of them is the route the Java search finds, cell for cell (doors or none, any floor or a given one, four search budgets)", same == 400 && found > 250,
                    same + " of 400 identical, " + found + " found, " + cells + " cells" + first);
            System.out.printf("       routes: native %.1f ms for the batch on %d workers, Java %.1f ms one after another%n", nativeMs, e.stats().workers, javaMs);

            // ---- sight: three thousand lines, both ways of looking
            int n = 3000, agree = 0, clearN = 0;
            float[][] ls = new float[n][];
            boolean[] natSee = new boolean[n];
            for (int i = 0; i < n; i++) {
                float ax = World.CITY_MIN + 8 + rnd.nextFloat() * (World.CITY_MAX - World.CITY_MIN - 16), az = World.CITY_MIN + 8 + rnd.nextFloat() * (World.CITY_MAX - World.CITY_MIN - 16), ay = G + 0.2f + rnd.nextFloat() * (rnd.nextInt(4) == 0 ? 14 : 2);
                float reach = 4 + rnd.nextFloat() * 40;
                ls[i] = new float[]{ax, ay, az, ax + (rnd.nextFloat() - 0.5f) * 2 * reach, G + 0.2f + rnd.nextFloat() * (rnd.nextInt(4) == 0 ? 14 : 2), az + (rnd.nextFloat() - 0.5f) * 2 * reach, i % 2};
                final int k = i;
                link.requestSight(ls[i][0], ls[i][1], ls[i][2], ls[i][3], ls[i][4], ls[i][5], i % 2 == 1, c -> natSee[k] = c);
            }
            link.flush(1 / 60f);
            for (int i = 0; i < n; i++) {
                float[] l = ls[i];
                boolean ref2 = l[6] > 0 ? Physics.sight(city, l[0], l[1], l[2], l[3], l[4], l[5]) : Physics.los(city, l[0], l[1], l[2], l[3], l[4], l[5]);
                if (ref2 == natSee[i]) agree++;
                if (ref2) clearN++;
            }
            t.check("three thousand lines of sight - as light goes and as an eye sees - are clear or blocked exactly as the Java walk says", agree == n && clearN > 200 && clearN < n - 200, agree + " of " + n + " agree, " + clearN + " clear");

            // ---- the world changes by command: the engine's copy follows World.set, and routes change with it
            int wx = -1, wz = -1;
            for (int tries = 0; tries < 4000 && wx < 0; tries++) {
                int x = World.CITY_MIN + 20 + rnd.nextInt(400), z = World.CITY_MIN + 20 + rnd.nextInt(400);
                // a wall one thick between two cells that can be stood in
                if (city.solid(x, G, z) && city.solid(x, G + 1, z) && !Block.ALL[city.get(x, G, z)].is(Block.DOOR) && city.nav.stand(x - 1, G, z, false) && city.nav.stand(x + 1, G, z, false) && city.solid(x, G - 1, z)) { wx = x; wz = z; }
            }
            if (wx >= 0) {
                int b0 = city.get(wx, G, wz), b1 = city.get(wx, G + 1, wz);
                int[][] before = new int[1][], after = new int[1][], back = new int[1][];
                link.requestPath(wx - 0.5f, G, wz + 0.5f, wx + 1.5f, Float.NaN, wz + 0.5f, 3600, false, c -> before[0] = c);
                link.flush(1 / 60f);
                int[] refBefore = city.nav.path(wx - 0.5f, G, wz + 0.5f, wx + 1.5f, wz + 0.5f, 3600, false);
                city.set(wx, G, wz, Block.AIR); city.set(wx, G + 1, wz, Block.AIR);
                link.requestPath(wx - 0.5f, G, wz + 0.5f, wx + 1.5f, Float.NaN, wz + 0.5f, 3600, false, c -> after[0] = c);
                link.flush(1 / 60f);
                int[] refAfter = city.nav.path(wx - 0.5f, G, wz + 0.5f, wx + 1.5f, wz + 0.5f, 3600, false);
                city.set(wx, G, wz, b0); city.set(wx, G + 1, wz, b1);
                link.requestPath(wx - 0.5f, G, wz + 0.5f, wx + 1.5f, Float.NaN, wz + 0.5f, 3600, false, c -> back[0] = c);
                link.flush(1 / 60f);
                t.check("a wall knocked through with World.set is knocked through in the engine's world in the same tick - the route through it is two cells, as Java's is - and put back, it is a wall again",
                        Arrays.equals(before[0], refBefore) && Arrays.equals(after[0], refAfter) && after[0] != null && after[0].length == 6 && Arrays.equals(back[0], refBefore),
                        "at " + wx + "," + wz + ": before " + (before[0] == null ? "none" : before[0].length / 3 + " cells") + ", open " + (after[0] == null ? "none" : after[0].length / 3 + " cells") + ", rebuilt " + (back[0] == null ? "none" : back[0].length / 3 + " cells"));
            } else t.check("a wall to knock through was found in the city", false, "none in 4000 tries");

            // ---- doors: sealed in the engine when the game says so
            int dx = -1, dz = -1;
            boolean alongX = false;
            for (int x = World.CITY_MIN + 4; x < World.CITY_MAX - 4 && dx < 0; x++) for (int z = World.CITY_MIN + 4; z < World.CITY_MAX - 4 && dx < 0; z++) {
                if (!Block.ALL[city.get(x, G, z)].is(Block.DOOR) || !city.solid(x, G, z)) continue;
                if (city.nav.stand(x - 1, G, z, false) && city.nav.stand(x + 1, G, z, false)) { dx = x; dz = z; alongX = false; }
                else if (city.nav.stand(x, G, z - 1, false) && city.nav.stand(x, G, z + 1, false)) { dx = x; dz = z; alongX = true; }
            }
            if (dx >= 0) {
                final int fx = dx, fz = dz;
                float sx = alongX ? dx + 0.5f : dx - 0.5f, sz = alongX ? dz - 0.5f : dz + 0.5f, gx = alongX ? dx + 0.5f : dx + 1.5f, gz = alongX ? dz + 1.5f : dz + 0.5f;
                int[][] open = new int[1][], shut = new int[1][];
                link.requestPath(sx, G, sz, gx, Float.NaN, gz, 2600, true, c -> open[0] = c);
                link.flush(1 / 60f);
                int[] refOpen = city.nav.path(sx, G, sz, gx, gz, 2600, true);
                city.doors = (x, y, z) -> x == fx && z == fz;
                link.doorSeal(dx, G, dz, true); link.doorSeal(dx, G + 1, dz, true);
                link.requestPath(sx, G, sz, gx, Float.NaN, gz, 2600, true, c -> shut[0] = c);
                link.flush(1 / 60f);
                int[] refShut = city.nav.path(sx, G, sz, gx, gz, 2600, true);
                city.doors = null;
                link.doorsUnseal();
                link.flush(1 / 60f);
                t.check("a door is a way through for those who open doors, and is not once the game says it is locked - in the engine as in Java",
                        Arrays.equals(open[0], refOpen) && open[0] != null && open[0].length == 6 && Arrays.equals(shut[0], refShut) && !Arrays.equals(shut[0], open[0]) && e.stats().sealedDoors == 0,
                        "door at " + dx + "," + dz + ": open " + (open[0] == null ? "none" : open[0].length / 3 + " cells") + ", locked " + (shut[0] == null ? "none" : shut[0].length / 3 + " cells"));
            } else t.check("a door to lock was found in the city", false, "none");
        } finally {
            link.detach();
            city.doors = oracle;
        }

        // ---- in an operation: a body that has to go round a wall gets its route from the engine, and gets there
        Raid r = SelfTests11.flat(new Profile());
        World w = r.world;
        int bx = 300, bz = 520;
        SelfTests11.clear(w, bx - 14, bz - 14, bx + 14, bz + 14, 7);
        for (int z = bz - 9; z <= bz + 9; z++) for (int y = G; y < G + 6; y++) w.setRaw(bx, y, z, Block.CONCRETE);      // too high to be pulled up onto: the way is round it
        w.computeHeights();
        Actor a = SelfTests11.spawn(r, "survivors", bx - 5.5f, bz + 0.5f, 0);
        a.hostileToPlayer = false;
        r.freezeAI = true;
        // the operation's own tick gives the world over and runs the engine's frame
        for (int i = 0; i < 3; i++) r.update(1 / 60f);
        boolean wired = w.link != null && r.linkFlushedAt == r.time;
        int asked0 = NativeLink.pathsAsked, answered0 = NativeLink.pathsAnswered, stalls0 = NativeLink.stalls;
        float arrived = -1;
        boolean hadPath = false;
        // then the body is steered as an operation steers it - and the engine's frame run once a tick, as an operation runs it
        // (the operation's whole update is not used here: with its minds frozen it also brakes every body, each tick)
        for (int i = 0; i < 60 * 30 && arrived < 0 && w.link != null; i++) {
            w.nav.budget = 8; r.time += 1 / 60f;
            w.link.flush(1 / 60f); r.linkFlushedAt = r.time;
            Steering.goTo(r, a, bx + 5.5f, bz + 0.5f, 3.2f, 1 / 60f);
            Physics.move(w, a, 1 / 60f);
            hadPath |= a.path != null;
            if (a.distXZ(bx + 5.5f, bz + 0.5f) < 1.2f) arrived = i / 60f;
        }
        boolean linked = w.link != null;
        t.check("in an operation, a body with a wall in its way asks the engine for its route, has it by the next tick, and walks it round the end of the wall",
                linked && wired && NativeLink.pathsAsked > asked0 && NativeLink.pathsAnswered - answered0 == NativeLink.pathsAsked - asked0 && hadPath && arrived > 0 && NativeLink.stalls == stalls0,
                String.format("%s; %d routes asked, %d answered, %d stalled; arrived after %.1f s", linked ? "linked" : "not linked", NativeLink.pathsAsked - asked0, NativeLink.pathsAnswered - answered0, NativeLink.stalls - stalls0, arrived));
        // steered with nobody running the engine's tick, the same body is not left waiting: the search is made here
        a.x = bx - 5.5f; a.z = bz + 0.5f; a.path = null; a.pathAt = -99; a.pathPending = -99; a.vx = a.vz = 0;
        float alone = -1;
        for (int i = 0; i < 60 * 30 && alone < 0; i++) {
            w.nav.budget = 8; r.time += 1 / 60f;
            Steering.goTo(r, a, bx + 5.5f, bz + 0.5f, 3.2f, 1 / 60f);
            Physics.move(w, a, 1 / 60f);
            if (a.distXZ(bx + 5.5f, bz + 0.5f) < 1.2f) alone = i / 60f;
        }
        t.check("steered where nothing runs the engine's tick, the same body is not left waiting on it: the search is made in Java and it arrives all the same", alone > 0, String.format("arrived after %.1f s", alone));
        for (int z = bz - 9; z <= bz + 9; z++) for (int y = G; y < G + 6; y++) w.setRaw(bx, y, z, Block.AIR);
        w.computeHeights();
    }

    // ================================================================== the boundary
    /** Engine packages that already named something of the game's when the split began (1.2): the debt, counted. It may shrink; it may not grow. */
    static final int ENGINE_DEBT = 23;

    /**
     * The wall between engine and game, as far as it can be checked from the source: the Astratto bridge names nothing
     * of Absentialis at all, and the engine packages that still lean on the game's classes are not allowed to lean harder.
     */
    private static void boundary(SelfTests04.Check t) {
        System.out.println("[v1.2 boundary: what the engine may know of the game]");
        Path src = Path.of("src/main/java");
        if (!Files.isDirectory(src)) { t.check("the source tree is here to be read (run from the project directory)", false, src.toAbsolutePath().toString()); return; }
        List<String> bridge = importers(src.resolve("astratto"), "absentialis.");
        t.check("nothing in the astratto package - the engine's own Java - names a class of Absentialis", bridge.isEmpty() && Files.isDirectory(src.resolve("astratto/bridge")), bridge.toString());
        List<String> debt = new ArrayList<>();
        for (String p : new String[]{"absentialis/engine", "absentialis/neuralai", "absentialis/sydney"})
            debt.addAll(importers(src.resolve(p), "absentialis.scene.", "absentialis.gameplay.", "absentialis.world.", "absentialis.sim.", "absentialis.rivalry.", "absentialis.entity.", "absentialis.character.", "absentialis.ui.", "absentialis.persistence.", "absentialis.anatomy.", "absentialis.vehicle."));
        t.check("the engine packages that still name the game's classes are no more than when the split began (" + ENGINE_DEBT + " files; astratto/ARCHITECTURE.md lists how each is to be cut loose)", debt.size() <= ENGINE_DEBT, debt.size() + " files");
        Path header = Path.of("astratto/native/include/astratto/astratto.h");
        String h = "";
        try { h = Files.isRegularFile(header) ? Files.readString(header) : ""; } catch (IOException ignored) { }
        boolean versions = h.contains("#define ASTRATTO_API_MAJOR " + AstrattoNative.API_MAJOR) && h.contains("#define ASTRATTO_API_MINOR " + AstrattoNative.API_MINOR);
        t.check("the Java bindings and the C header agree on the version of the interface between them", versions, header + (h.isEmpty() ? " not found" : ""));
    }

    /** Source files under a directory that mention any of the given package prefixes outside a comment line. */
    private static List<String> importers(Path dir, String... prefixes) {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                boolean hit = false;
                for (String line : Files.readAllLines(f)) {
                    String s = line.strip();
                    if (s.startsWith("*") || s.startsWith("//") || s.startsWith("/*")) continue;
                    for (String p : prefixes) if (s.contains(p)) { hit = true; break; }
                    if (hit) break;
                }
                if (hit) out.add(dir.relativize(f).toString());
            }
        } catch (IOException ex) { out.add("unreadable: " + ex); }
        return out;
    }
}
