package absentialis.world.gen;

import static absentialis.world.gen.Gen.G;

import absentialis.engine.util.Rng;
import absentialis.tools.TerrainProbe;
import absentialis.world.Block;
import absentialis.world.MapData;
import absentialis.world.Nav;
import absentialis.world.World;
import absentialis.world.gen.arch.MaterialRules;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pipeline stages 11 and 12: check what was built against what was planned, repair what can be repaired, reject
 * what cannot.
 *
 *   roads        every carriageway cell has its surface and clear air above it; anything standing in one is removed
 *   footprints   no two structures share a column
 *   access       a walker starting outside the main entrance must reach a floor cell of every room on every storey
 *                (so stairs, landings and corridors are tested by walking them, with the same rules agents use)
 *   doors        a doorway has standing room on both sides
 *   support      every solid block above ground is joined to the ground through other solid blocks
 *   materials    {@link MaterialRules#audit} over each room's floor and walls
 *   terrain      no column without ground, no cliffs, no trench - anywhere, and specifically along the boundary
 *
 * A room that cannot be reached is first cleared of furniture, then given the doorway its graph edge asked for;
 * a building that still fails is demolished and its lot returned to open ground.
 */
public final class GenValidator {
    private GenValidator() {}

    public static final int F_ROAD = 1, F_ROOM = 2, F_DOOR = 3, F_FLOAT = 4, F_OVERLAP = 5, F_MATERIAL = 6, F_TERRAIN = 7;

    public static void run(World w, WorldPlan plan, Streets streets, GenReport rep) {
        REMOVED.clear();
        roads(w, plan, streets, rep);
        overlaps(w, rep);
        for (MapData.Building b : new ArrayList<>(w.map.buildings)) building(w, b, rep);
        rep.buildings = 0;
        for (MapData.Building b : w.map.buildings) if (b.valid) { rep.buildings++; rep.rooms += b.rooms.size(); rep.doorways += b.doors.size(); }
        rep.landmarks = w.map.anchors.size();
        support(w, rep);
        materials(w, rep);
        TerrainProbe.Report t = TerrainProbe.scan(w);
        rep.voidColumns = t.voidColumns; rep.cliffs = t.cliffs; rep.trenchCells = t.trenchCells; rep.edgeCliffs = t.edgeCliffs;
        for (int[] c : t.worst) rep.flag(c[0], c[1], c[2], F_TERRAIN);
        if (!REMOVED.isEmpty()) {
            w.map.loot.removeIf(l -> REMOVED.contains(cell(l.x, l.y, l.z)));
            w.map.lampsOnPower.removeIf(l -> REMOVED.contains(cell(l.x, l.y, l.z)));
            w.map.documents.removeIf(l -> REMOVED.contains(cell(l.x, l.y, l.z)));
            REMOVED.clear();
        }
    }

    // ------------------------------------------------------------------ roads
    private static boolean roadSurface(int id) {
        return id == Block.ASPHALT || id == Block.ASPHALT_CRACKED || id == Block.ROAD_LINE || id == Block.ROAD_LINE_X || id == Block.ROAD_EDGE || id == Block.ROAD_EDGE_X
                || id == Block.ROAD_TRUNK || id == Block.ROAD_TRUNK_X || id == Block.CROSSWALK || id == Block.HAZARD_STRIPE || id == Block.COBBLE || id == Block.MUD;
    }

    private static boolean kerbFurniture(int id) {
        return id == Block.LAMP_POST || id == Block.LAMP_ARM || id == Block.LAMP || id == Block.LAMP_OFF || id == Block.TRASH || id == Block.PEW || id == Block.STEEL_BEAM || id == Block.POSTER
                || id == Block.METAL_PLATE || id == Block.ROAD_SIGN || id == Block.ROAD_SIGN_Z || id == Block.LOG || Block.leaf(id) || id == Block.RED_LIGHT;
    }

    private static void roads(World w, WorldPlan plan, Streets streets, GenReport rep) {
        for (int x = World.CITY_MIN; x < World.CITY_MAX; x++) for (int z = World.CITY_MIN; z < World.CITY_MAX; z++) {
            int res = plan.reserved(x, z);
            if (res != WorldPlan.RES_ROAD && res != WorldPlan.RES_ALLEY) continue;
            boolean carriage = streets.isCarriage(x, z), alley = res == WorldPlan.RES_ALLEY;
            int surf = w.get(x, G - 1, z);
            if (carriage && !roadSurface(surf) && surf != Block.LADDER) { w.setRaw(x, G - 1, z, Block.ASPHALT); rep.roadSurfaceFixed++; }
            if ((carriage || alley) && (surf == 0 || !Block.ALL[surf].solid()) && surf != Block.LADDER) { w.setRaw(x, G - 1, z, alley ? Block.CONCRETE_FLOOR : Block.ASPHALT); rep.roadSurfaceFixed++; }
            for (int y = G; y < G + 8; y++) {
                int id = w.get(x, y, z);
                if (id == 0) continue;
                // the carriageway and the alleys are clear from kerb to kerb; the pavement may carry street furniture and nothing else
                // (0.7: a bough reaching out over the road, four blocks up or more, is part of the canopy as the leaves on it are)
                boolean allowed = !carriage && !alley && kerbFurniture(id) || (Block.leaf(id) || id == Block.LOG || id == Block.LAMP_ARM || id == Block.LAMP || id == Block.LAMP_OFF) && y >= G + 4;
                if (allowed) continue;
                rep.roadCellsCleared++;
                rep.flag(x, y, z, F_ROAD);
                clear(w, x, y, z);
            }
        }
        // recount: nothing may be left
        for (int x = World.CITY_MIN; x < World.CITY_MAX; x++) for (int z = World.CITY_MIN; z < World.CITY_MAX; z++) {
            if (!streets.isCarriage(x, z)) continue;
            for (int y = G; y < G + 4; y++) if (w.solid(x, y, z)) { rep.roadCellsBlocked++; rep.flag(x, y, z, F_ROAD); if (rep.roadCellsBlocked <= 24) rep.note("road blocked at " + x + "," + y + "," + z + " by " + Block.ALL[w.get(x, y, z)].name + " res=" + plan.reserved(x, z)); }
        }
    }

    // ------------------------------------------------------------------ footprints
    private static void overlaps(World w, GenReport rep) {
        short[] owner = new short[World.W * World.D];
        for (MapData.Building b : w.map.buildings) for (int x = Math.max(0, b.x0); x <= Math.min(World.W - 1, b.x1); x++) for (int z = Math.max(0, b.z0); z <= Math.min(World.D - 1, b.z1); z++) {
            int i = x * World.D + z;
            if (owner[i] != 0 && owner[i] != b.id + 1) { rep.overlaps++; rep.flag(x, G, z, F_OVERLAP); b.problems.add("overlaps building " + (owner[i] - 1)); }
            owner[i] = (short) (b.id + 1);
        }
    }

    // ------------------------------------------------------------------ access
    private static int key(int x, int y, int z) { return x | z << 10 | y << 20; }

    /** Cells a walker can reach from the entrance without leaving the lot (doors are opened, never forced). */
    private static Set<Integer> walk(World w, MapData.Building b) {
        Nav nav = w.nav;
        Set<Integer> seen = new HashSet<>();
        int[] stack = new int[4096];
        int n = 0;
        int sy = nav.stand(b.entryX, b.entryY, b.entryZ, true) ? b.entryY : nav.stepY(b.entryX, b.entryZ, b.entryY, true);
        if (sy < 0) return seen;
        stack[n++] = key(b.entryX, sy, b.entryZ);
        seen.add(stack[0]);
        int lx0 = b.lotX1 > b.lotX0 ? b.lotX0 : b.x0 - 3, lz0 = b.lotX1 > b.lotX0 ? b.lotZ0 : b.z0 - 3, lx1 = b.lotX1 > b.lotX0 ? b.lotX1 - 1 : b.x1 + 3, lz1 = b.lotX1 > b.lotX0 ? b.lotZ1 - 1 : b.z1 + 3;
        while (n > 0) {
            int k = stack[--n], x = k & 1023, z = (k >> 10) & 1023, y = k >> 20;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = x + d[0], nz = z + d[1];
                if (nx < lx0 || nz < lz0 || nx > lx1 || nz > lz1) continue;
                int ny = nav.stepY(nx, nz, y, true);
                if (ny < 0) continue;
                int nk = key(nx, ny, nz);
                if (!seen.add(nk)) continue;
                if (n == stack.length) stack = java.util.Arrays.copyOf(stack, n * 2);
                stack[n++] = nk;
            }
        }
        return seen;
    }

    private static int mark(MapData.Building b, Set<Integer> seen) {
        int bad = 0;
        for (MapData.Room room : b.rooms) {
            room.reachable = false;
            outer:
            for (int x = room.x0; x <= room.x1; x++) for (int z = room.z0; z <= room.z1; z++) if (seen.contains(key(x, room.y + 1, z))) { room.reachable = true; break outer; }
            if (!room.reachable) bad++;
        }
        return bad;
    }

    private static boolean structural(int id) {
        if (id == 0) return false;
        Block bl = Block.ALL[id];
        return bl.shape == 0 && !bl.is(Block.CONTAINER) && !Validation.prop(id) && id != Block.COUNTER && id != Block.STOVE && id != Block.SINK && id != Block.TV && id != Block.COMPUTER
                && id != Block.WASHER && id != Block.MAP_BOARD && id != Block.BIOHAZARD && id != Block.HAY && id != Block.WATER_TANK && id != Block.PIPE && id != Block.CAR_TIRE;
    }

    private static void building(World w, MapData.Building b, GenReport rep) {
        if (b.entryY == 0) { b.problems.add("no entrance recorded"); }
        doorways(w, b, rep);
        Set<Integer> seen = walk(w, b);
        int bad = mark(b, seen);
        if (bad > 0) {
            // repair 1: furniture out of the rooms that cannot be reached, and out of every doorway's approach
            for (MapData.Room room : b.rooms) {
                if (room.reachable) continue;
                for (int x = room.x0; x <= room.x1; x++) for (int z = room.z0; z <= room.z1; z++) for (int y = room.y + 1; y <= room.y + 2; y++) {
                    int id = w.get(x, y, z);
                    if (id != 0 && !structural(id) && !Block.ALL[id].is(Block.DOOR) && !Block.ALL[id].is(Block.STEP) && id != Block.LADDER) { clear(w, x, y, z); }
                }
            }
            // repair 2: cut the doorway the room graph asked for wherever it has been closed up
            for (MapData.Doorway d : b.doors) {
                boolean concerns = d.roomA >= 0 && d.roomA < b.rooms.size() && !b.rooms.get(d.roomA).reachable || d.roomB >= 0 && d.roomB < b.rooms.size() && !b.rooms.get(d.roomB).reachable;
                if (!concerns) continue;
                for (int y = d.y; y <= d.y + 1; y++) { int id = w.get(d.x, y, d.z); if (id != 0 && !Block.ALL[id].is(Block.DOOR)) clear(w, d.x, y, d.z); }
                for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) for (int y = d.y; y <= d.y + 1; y++) { int id = w.get(d.x + o[0], y, d.z + o[1]); if (id != 0 && !structural(id) && !Block.ALL[id].is(Block.DOOR)) clear(w, d.x + o[0], y, d.z + o[1]); }
            }
            seen = walk(w, b);
            int after = mark(b, seen);
            if (after < bad) { b.repaired = true; rep.buildingsRepaired++; }
            bad = after;
        }
        if (bad > 0) {
            for (MapData.Room room : b.rooms) if (!room.reachable) { b.problems.add("room " + room.type + " on storey " + room.floor + " cannot be reached"); rep.flag(room.cx(), room.y + 1, room.cz(), F_ROOM); }
            boolean ground = false;
            for (MapData.Room room : b.rooms) if (room.floor == 0 && room.reachable) ground = true;
            // a building nobody can enter, or one that is mostly sealed, is not a building: reject it
            if (!ground || bad * 2 > b.rooms.size()) { demolish(w, b); rep.buildingsRejected++; return; }
            rep.roomsUnreachable += bad;
        }
    }

    /** Cells emptied by a repair; loot and lamp records pointing at them are pruned once, at the end. */
    private static final Set<Long> REMOVED = new HashSet<>();
    private static long cell(int x, int y, int z) { return ((long) x << 40) | ((long) y << 20) | z; }

    private static void clear(World w, int x, int y, int z) {
        w.setRaw(x, y, z, Block.AIR);
        REMOVED.add(cell(x, y, z));
    }

    /** A doorway must have standing room on both sides along one axis. */
    private static void doorways(World w, MapData.Building b, GenReport rep) {
        for (MapData.Doorway d : b.doors) {
            boolean ok = false;
            for (int axis = 0; axis < 2 && !ok; axis++) {
                int ax = axis == 0 ? 1 : 0, az = axis == 0 ? 0 : 1;
                boolean a = free(w, d.x + ax, d.y, d.z + az), c = free(w, d.x - ax, d.y, d.z - az);
                if (a && c) ok = true;
            }
            if (ok) continue;
            // furniture pushed against a door: move it out of the way and look again
            boolean fixed = false;
            for (int axis = 0; axis < 2 && !fixed; axis++) {
                int ax = axis == 0 ? 1 : 0, az = axis == 0 ? 0 : 1;
                if (clearable(w, d.x + ax, d.y, d.z + az) && clearable(w, d.x - ax, d.y, d.z - az)) {
                    for (int s = -1; s <= 1; s += 2) for (int y = d.y; y <= d.y + 1; y++) { int id = w.get(d.x + ax * s, y, d.z + az * s); if (id != 0 && !structural(id)) clear(w, d.x + ax * s, y, d.z + az * s); }
                    fixed = free(w, d.x + ax, d.y, d.z + az) && free(w, d.x - ax, d.y, d.z - az);
                }
            }
            if (fixed) rep.doorsFixed++;
            else { rep.doorsBlocked++; rep.flag(d.x, d.y, d.z, F_DOOR); b.problems.add("door at " + d.x + "," + d.y + "," + d.z + " opens into solid geometry"); }
        }
    }

    private static boolean free(World w, int x, int y, int z) { return !w.solid(x, y, z) && !w.solid(x, y + 1, z) && w.solid(x, y - 1, z); }
    private static boolean clearable(World w, int x, int y, int z) {
        for (int yy = y; yy <= y + 1; yy++) { int id = w.get(x, yy, z); if (id != 0 && Block.ALL[id].solid() && structural(id)) return false; }
        return w.solid(x, y - 1, z);
    }

    /** Stage 12, the hard way: the structure comes down and its lot is left as open, walkable ground. */
    private static void demolish(World w, MapData.Building b) {
        b.valid = false;
        for (int x = b.x0; x <= b.x1; x++) for (int z = b.z0; z <= b.z1; z++) {
            for (int y = G; y < World.H; y++) w.setRaw(x, y, z, Block.AIR);
            w.setRaw(x, G - 1, z, Block.CONCRETE_FLOOR);
        }
        w.map.loot.removeIf(l -> b.contains(l.x, l.z) && l.y >= G - 1);
        w.map.lampsOnPower.removeIf(l -> b.contains(l.x, l.z) && l.y >= G - 1);
        w.map.documents.removeIf(l -> b.contains(l.x, l.z));
    }

    // ------------------------------------------------------------------ support
    /**
     * Unsupported structure: flood from the ground up through face-joined solid blocks; whatever solid is not
     * reached is hanging in the air and is removed. (Ceiling lamps, signs and leaves hang from something and are
     * reached through it.)
     */
    private static void support(World w, GenReport rep) {
        int n = World.W * World.D * World.H;
        BitSet seen = new BitSet(n);
        int[] stack = new int[1 << 20];
        int sp = 0;
        short[] base = new short[World.W * World.D];
        for (int x = 0; x < World.W; x++) for (int z = 0; z < World.D; z++) {
            int by = World.outsideDistance(x, z) > 0 ? Math.max(1, TerrainProbe.ground(w, x, z)) : G - 1;
            // start from the highest block of the column's unbroken ground
            while (by > 1 && !attached(w.get(x, by, z))) by--;
            base[x * World.D + z] = (short) by;
            int i = World.idx(x, by, z);
            if (attached(w.get(x, by, z))) { seen.set(i); if (sp == stack.length) stack = java.util.Arrays.copyOf(stack, sp * 2); stack[sp++] = i; }
        }
        while (sp > 0) {
            int i = stack[--sp];
            int y = i % World.H, xz = i / World.H, z = xz % World.D, x = xz / World.D;
            for (int d = 0; d < 6; d++) {
                int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0), ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0), nz = z + (d == 4 ? 1 : d == 5 ? -1 : 0);
                if (nx < 0 || nz < 0 || nx >= World.W || nz >= World.D || ny >= World.H) continue;
                if (ny < base[nx * World.D + nz]) continue;                   // the ground itself needs no proof
                int ni = World.idx(nx, ny, nz);
                if (seen.get(ni) || !attached(w.blocks[ni] & 0xFF)) continue;
                seen.set(ni);
                if (sp == stack.length) stack = java.util.Arrays.copyOf(stack, sp * 2);
                stack[sp++] = ni;
            }
        }
        for (int x = 0; x < World.W; x++) for (int z = 0; z < World.D; z++) {
            int b0 = base[x * World.D + z];
            for (int y = b0 + 1; y < World.H; y++) {
                int i = World.idx(x, y, z), id = w.blocks[i] & 0xFF;
                if (id == 0 || !attached(id) || seen.get(i)) continue;
                rep.floatingRemoved++;
                rep.flag(x, y, z, F_FLOAT);
                clear(w, x, y, z);
            }
        }
        rep.floatingBlocks = 0;
    }

    /** Blocks that take part in holding things up (and so must themselves be held up). Liquids and soft decor do not. */
    private static boolean attached(int id) {
        if (id == 0) return false;
        Block b = Block.ALL[id];
        if (b.is(Block.LIQUID)) return false;
        return id != Block.TALL_GRASS && id != Block.CROP && id != Block.COBWEB && id != Block.WIRES && id != Block.LAUNDRY && id != Block.BARBED;
    }

    // ------------------------------------------------------------------ materials
    private static void materials(World w, GenReport rep) {
        for (MapData.Building b : w.map.buildings) {
            if (!b.valid || b.authored) continue;
            MaterialRules.Mat m = MaterialRules.of(b.style, new Rng(b.id));
            for (MapData.Room room : b.rooms) {
                String f = MaterialRules.audit(w.get(room.cx(), room.y, room.cz()), "floor", room.type, m);
                if (f != null) { rep.materialFaults++; rep.flag(room.cx(), room.y, room.cz(), F_MATERIAL); b.problems.add(f); }
                for (int x = room.x0 - 1; x <= room.x1 + 1; x++) for (int z = room.z0 - 1; z <= room.z1 + 1; z++) {
                    boolean ring = x == room.x0 - 1 || x == room.x1 + 1 || z == room.z0 - 1 || z == room.z1 + 1;
                    boolean corner = (x == room.x0 - 1 || x == room.x1 + 1) && (z == room.z0 - 1 || z == room.z1 + 1);
                    if (!ring || corner) continue;        // a corner post touches this room only by its edge
                    boolean exterior = x == b.x0 || x == b.x1 || z == b.z0 || z == b.z1;
                    String a = MaterialRules.audit(w.get(x, room.y + 2, z), exterior ? "exterior" : "partition", room.type, m);
                    if (a != null) { rep.materialFaults++; rep.flag(x, room.y + 2, z, F_MATERIAL); if (b.problems.size() < 8) b.problems.add(a); }
                }
            }
        }
    }
}
