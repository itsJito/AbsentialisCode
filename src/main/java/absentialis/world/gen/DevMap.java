package absentialis.world.gen;

import absentialis.engine.util.Rng;
import absentialis.world.Block;
import absentialis.world.District;
import absentialis.world.MapData;
import absentialis.world.World;
import absentialis.world.gen.GenDefs.Archetype;
import absentialis.world.gen.Streets.Rect;
import absentialis.world.gen.arch.Architect;

/**
 * The devmap (0.7): a small, flat, purpose-built map for testing, loaded from the developer console ("devmap").
 * It is not part of the Zone and replaces nothing in it - the city is generated again when the console asks for it
 * back ("devmap off"). On it, in one walled field 192 m by 128 m:
 *
 *   a road ......... straight, 170 m, kerbed and lit: somewhere to drive, to be shot at from a distance, to run
 *   a maze ......... eleven cells by eleven of two-metre corridors, one way in and one way out, for path-finding
 *   a mansion ...... the Zone's own mansion archetype built by the Zone's own architect, two storeys, furnished,
 *                    its front entrance a double door: rooms, stairs and doorways for interior path-finding
 *   a yard ......... the things tests need to hand - cover of every height, a wall of one-block openings at one,
 *                    two, three and four blocks up (the infected climb the first three), glass to break, crates and
 *                    lockers to search, a ladder and a platform, stairs, barrels, a plank pile
 *
 * Nobody lives here: a raid on the devmap starts empty, and everything in it is put there from the console.
 */
public final class DevMap {
    private DevMap() {}

    static final int G = World.GROUND;
    /** The field (inclusive) and what stands where on it. */
    public static final int X0 = 256, Z0 = 288, X1 = 447, Z1 = 415;
    public static final int ROAD_Z0 = 334, ROAD_Z1 = 341, ROAD_X0 = 266, ROAD_X1 = 437;
    public static final int MAZE_X = 270, MAZE_Z = 352, MAZE_CELLS = 11, MAZE_PITCH = 3;
    public static final int HOUSE_X0 = 372, HOUSE_Z0 = 350, HOUSE_X1 = 420, HOUSE_Z1 = 398;
    public static final int YARD_X = 276, YARD_Z = 300;
    /** Where the maze is entered and left (cells just outside its wall), and the wall of test openings. */
    public static int mazeInX, mazeInZ, mazeOutX, mazeOutZ, holesX, holesZ;
    public static MapData.Building mansion;

    /** 1.0: where the prop gallery starts (its first row's wall), how long a row is, how far apart rows are, and how many there are. */
    public static final int GALLERY_X = X0 + 2, GALLERY_Z = Z1 + 12, GALLERY_LEN = 64, GALLERY_PITCH = 7;
    public static int galleryRows;

    /**
     * 1.0, the prop gallery, outside the field's south wall: every prop the game has, stood in rows against a wall under
     * a canopy - floor pieces on the ground, wall pieces on the wall, hanging pieces under the canopy - so that a model
     * can be looked at, shot at and walked round without going to find one in the city.
     */
    private static void gallery(Gen g) {
        int x = GALLERY_X + 1, row = 0;
        java.util.List<PropLib.Def> defs = new java.util.ArrayList<>();
        for (PropLib.Def d : PropLib.all()) defs.add(d);
        galleryRows = 0;
        for (int i = 0; i < defs.size(); i++) {
            PropLib.Def d = defs.get(i);
            if (x + d.w > GALLERY_X + GALLERY_LEN - 1) { x = GALLERY_X + 1; row++; }
            int z = GALLERY_Z + row * GALLERY_PITCH;
            if (x == GALLERY_X + 1) {
                galleryRows = row + 1;
                g.fill(GALLERY_X, G - 1, z - 1, GALLERY_X + GALLERY_LEN, G - 1, z + 4, Block.SIDEWALK);
                g.fill(GALLERY_X, G, z - 1, GALLERY_X + GALLERY_LEN, G + 4, z - 1, Block.PLASTER);
                g.fill(GALLERY_X, G + 4, z, GALLERY_X + GALLERY_LEN, G + 4, z + 1, Block.CEILING_PLASTER);
                for (int px = GALLERY_X; px <= GALLERY_X + GALLERY_LEN; px += 16) g.fill(px, G, z + 1, px, G + 3, z + 1, Block.STEEL_BEAM);
            }
            int y = d.mount == PropLib.WALL ? G + 1 : d.mount == PropLib.CEILING ? G + 4 - d.h : G;
            if (g.w.get(x, G, z + 1) == Block.STEEL_BEAM || g.w.get(x + d.w - 1, G, z + 1) == Block.STEEL_BEAM) x++;
            g.prop(d.name, x, y, z, 0);
            x += d.w + 1;
        }
    }

    public static void generate(World w) {
        long t0 = System.currentTimeMillis();
        Gen g = new Gen(w, 70707L);
        w.map.clear();
        if (w.link != null) w.link.detach();                 // 1.2: built again in place: the native core's copy is of what was here before
        java.util.Arrays.fill(w.blocks, (byte) 0);
        w.dev = true;
        java.util.Arrays.fill(w.districtMap, (byte) District.MARKET.ordinal());
        // level ground to the edge of the world (nothing to fall off), grass on the field
        for (int x = 0; x < World.W; x++) for (int z = 0; z < World.D; z++) {
            g.set(x, 0, z, Block.BEDROCK);
            g.fill(x, 1, z, x, G - 2, z, Block.DIRT);
            g.set(x, G - 1, z, x >= X0 && x <= X1 && z >= Z0 && z <= Z1 ? Block.GRASS : Block.DEAD_GRASS);
        }
        // the field's wall
        for (int x = X0; x <= X1; x++) for (int y = G; y < G + 4; y++) { g.set(x, y, Z0, Block.CONCRETE_PANEL); g.set(x, y, Z1, Block.CONCRETE_PANEL); }
        for (int z = Z0; z <= Z1; z++) for (int y = G; y < G + 4; y++) { g.set(X0, y, z, Block.CONCRETE_PANEL); g.set(X1, y, z, Block.CONCRETE_PANEL); }
        road(g);
        maze(g);
        mansion(g);
        yard(g);
        gallery(g);
        // somewhere to arrive, and a way to leave
        w.map.insertions.add(new MapData.Point("Devmap: the road, west end", "insertion", ROAD_X0 + 6, G, (ROAD_Z0 + ROAD_Z1) / 2, District.MARKET));
        w.map.insertions.add(new MapData.Point("Devmap: the maze", "insertion", mazeInX, G, mazeInZ - 3, District.MARKET));
        w.map.insertions.add(new MapData.Point("Devmap: the mansion door", "insertion", mansion.entryX, G, mansion.entryZ - 4, District.MARKET));
        w.map.insertions.add(new MapData.Point("Devmap: the yard", "insertion", YARD_X + 20, G, YARD_Z + 22, District.MARKET));
        MapData.Exit out = new MapData.Exit("devexit", "Devmap Exit (road, east end)", ROAD_X1 - 3, G, (ROAD_Z0 + ROAD_Z1) / 2, MapData.ExitType.PERMANENT, District.MARKET);
        w.map.exits.add(out);
        for (int z = ROAD_Z0; z <= ROAD_Z1; z++) g.set(ROAD_X1 - 3, G - 1, z, Block.HAZARD_STRIPE);
        g.landmark("Devmap Road", "road", (ROAD_X0 + ROAD_X1) / 2, G, ROAD_Z0);
        g.landmark("Devmap Maze", "maze", MAZE_X + MAZE_CELLS * MAZE_PITCH / 2, G, MAZE_Z);
        g.landmark("Devmap Mansion", "mansion", mansion.entryX, G, mansion.entryZ);
        g.landmark("Devmap Yard", "yard", YARD_X + 20, G, YARD_Z + 10);
        int swept = g.clearFoliageIndoors();
        g.auditProps();
        w.computeHeights();
        absentialis.world.Nav.annotate(w);
        System.out.printf("[Gen] Devmap generated in %d ms: road %d m, maze %dx%d, mansion %d rooms on %d floors (entrance %s), %d foliage blocks swept%n",
                System.currentTimeMillis() - t0, ROAD_X1 - ROAD_X0 + 1, MAZE_CELLS, MAZE_CELLS, mansion.rooms.size(), mansion.floors, Block.ALL[w.get(mansion.entryX, G, mansion.entryZ + 1)].name, swept);
    }

    private static void road(Gen g) {
        int mid = (ROAD_Z0 + ROAD_Z1) / 2;
        for (int x = ROAD_X0; x <= ROAD_X1; x++) {
            for (int z = ROAD_Z0; z <= ROAD_Z1; z++) g.set(x, G - 1, z, Block.ASPHALT);
            if (x % 6 < 3) { g.set(x, G - 1, mid, Block.ROAD_LINE); }
            g.set(x, G - 1, ROAD_Z0 - 1, Block.CURB); g.set(x, G - 1, ROAD_Z1 + 1, Block.CURB);
            g.set(x, G - 1, ROAD_Z0 - 2, Block.SIDEWALK); g.set(x, G - 1, ROAD_Z1 + 2, Block.SIDEWALK);
            g.set(x, G - 1, ROAD_Z0 - 3, Block.SIDEWALK); g.set(x, G - 1, ROAD_Z1 + 3, Block.SIDEWALK);
        }
        for (int x = ROAD_X0 + 10; x < ROAD_X1 - 6; x += 24) { g.streetLamp(x, ROAD_Z0 - 2, 0, 1); g.streetLamp(x + 12, ROAD_Z1 + 2, 0, -1); }
        // distance boards every 25 m along the kerb, for judging range
        for (int x = ROAD_X0 + 25; x < ROAD_X1; x += 25) g.set(x, G, ROAD_Z1 + 3, Block.ROAD_SIGN);
    }

    /** A perfect maze (one route between any two cells), by depth-first carving from fixed dice. */
    private static void maze(Gen g) {
        int n = MAZE_CELLS, p = MAZE_PITCH, size = n * p + 1;
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
            g.set(MAZE_X + x, G - 1, MAZE_Z + z, Block.CONCRETE_FLOOR);
            for (int y = G; y < G + 4; y++) g.set(MAZE_X + x, y, MAZE_Z + z, y == G + 3 ? Block.CONCRETE_DARK : Block.CONCRETE);
        }
        boolean[] seen = new boolean[n * n];
        int[] stack = new int[n * n];
        int sp = 0;
        Rng r = new Rng(424242L);
        stack[sp++] = 0; seen[0] = true;
        open(g, 0, 0);
        while (sp > 0) {
            int cur = stack[sp - 1], cx = cur % n, cz = cur / n;
            int[] dirs = {0, 1, 2, 3};
            for (int i = 3; i > 0; i--) { int j = r.nextInt(i + 1), t = dirs[i]; dirs[i] = dirs[j]; dirs[j] = t; }
            boolean moved = false;
            for (int d : dirs) {
                int nx = cx + (d == 0 ? 1 : d == 1 ? -1 : 0), nz = cz + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (nx < 0 || nz < 0 || nx >= n || nz >= n || seen[nz * n + nx]) continue;
                seen[nz * n + nx] = true;
                open(g, nx, nz);
                // the wall between the two cells
                int wx = MAZE_X + 1 + Math.min(cx, nx) * p + (nx != cx ? p - 1 : 0), wz = MAZE_Z + 1 + Math.min(cz, nz) * p + (nz != cz ? p - 1 : 0);
                for (int k = 0; k < p - 1; k++) for (int y = G; y < G + 4; y++) g.set(wx + (nx != cx ? 0 : k), y, wz + (nz != cz ? 0 : k), Block.AIR);
                stack[sp++] = nz * n + nx;
                moved = true;
                break;
            }
            if (!moved) sp--;
        }
        // in on the north side by the first cell, out on the south side by the last
        for (int k = 0; k < p - 1; k++) for (int y = G; y < G + 3; y++) { g.set(MAZE_X + 1 + k, y, MAZE_Z, Block.AIR); g.set(MAZE_X + 1 + (n - 1) * p + k, y, MAZE_Z + size - 1, Block.AIR); }
        mazeInX = MAZE_X + 1; mazeInZ = MAZE_Z - 1; mazeOutX = MAZE_X + 1 + (n - 1) * p; mazeOutZ = MAZE_Z + size;
        // a path from the road to the way in, and a light over each end
        for (int z = ROAD_Z1 + 2; z < MAZE_Z; z++) for (int k = 0; k < 2; k++) g.set(MAZE_X + 1 + k, G - 1, z, Block.GRAVEL);
        g.streetLamp(MAZE_X - 1, MAZE_Z - 2, 1, 0);
    }

    private static void open(Gen g, int cx, int cz) {
        for (int i = 0; i < MAZE_PITCH - 1; i++) for (int k = 0; k < MAZE_PITCH - 1; k++) for (int y = G; y < G + 4; y++) g.set(MAZE_X + 1 + cx * MAZE_PITCH + i, y, MAZE_Z + 1 + cz * MAZE_PITCH + k, Block.AIR);
    }

    /** The Zone's mansion, by the Zone's architect - two storeys, and (for this map) a double door at the front. */
    private static void mansion(Gen g) {
        Archetype a = g.defs.archetype("lm_mansion");
        String door = a.door;
        int fMin = a.floorsMin, fMax = a.floorsMax;
        a.door = "double_wood"; a.floorsMin = 2; a.floorsMax = 2;
        try {
            Rect lot = new Rect(HOUSE_X0, HOUSE_Z0, HOUSE_X1 + 1, HOUSE_Z1 + 1);
            Architect architect = new Architect(g);
            Site s = new Site(g, architect, lot, 0, District.MARKET, g.defs.of(District.MARKET), new Rng(9001L));
            s.begin();
            s.ground(Block.GRASS);
            mansion = s.building("lm_mansion", 4, 10, 43, 29, 0, 2, "Devmap Mansion", "");
            s.end();
        } finally { a.door = door; a.floorsMin = fMin; a.floorsMax = fMax; }
        // the drive: from the road straight to the door
        for (int z = ROAD_Z1 + 2; z <= mansion.entryZ; z++) for (int k = -1; k <= 1; k++) if (!mansion.contains(mansion.entryX + k, z)) g.set(mansion.entryX + k, G - 1, z, Block.GRAVEL);
        g.tree(HOUSE_X0 + 3, G, HOUSE_Z0 + 3, 6, false);
        g.tree(HOUSE_X1 - 3, G, HOUSE_Z0 + 4, 7, false);
        g.tree(HOUSE_X0 + 4, G, HOUSE_Z1 - 4, 5, true);
    }

    /** The yard north of the road: everything a test reaches for, laid out in rows and labelled by where it is. */
    private static void yard(Gen g) {
        int x = YARD_X, z = YARD_Z;
        for (int i = 0; i < 150; i++) for (int k = 0; k < 28; k++) g.set(x + i, G - 1, z + k, Block.CONCRETE_FLOOR);
        // ---- cover, lowest to highest: half a block, one, two, three
        for (int i = 0; i < 5; i++) g.set(x + 2 + i, G, z + 4, Block.SANDBAG);
        for (int i = 0; i < 5; i++) { g.set(x + 10 + i, G, z + 4, Block.BARRIER); }
        for (int i = 0; i < 5; i++) for (int y = G; y < G + 2; y++) g.set(x + 18 + i, y, z + 4, Block.HESCO);
        for (int i = 0; i < 5; i++) for (int y = G; y < G + 3; y++) g.set(x + 26 + i, y, z + 4, Block.BRICK_RED);
        // ---- the wall of openings: one block square, at one, two, three and four blocks above the ground
        holesX = x + 36; holesZ = z + 4;
        for (int i = 0; i < 17; i++) for (int y = G; y < G + 7; y++) g.set(holesX + i, y, holesZ, Block.BRICK_BROWN);
        for (int k = 0; k < 4; k++) g.set(holesX + 2 + k * 4, G + 1 + k, holesZ, Block.AIR);
        // ---- glass: a run of panes to shoot out
        for (int i = 0; i < 7; i++) { g.set(x + 58 + i, G, z + 4, Block.BRICK_WHITE); for (int y = G + 1; y < G + 3; y++) g.set(x + 58 + i, y, z + 4, i % 3 == 2 ? Block.BRICK_WHITE : Block.GLASS); g.set(x + 58 + i, G + 3, z + 4, Block.BRICK_WHITE); }
        // ---- things to search
        int[] boxes = {Block.CRATE, Block.CRATE_MIL, Block.LOCKER, Block.CABINET, Block.MED_CABINET, Block.TOOLBOX, Block.SAFE, Block.FRIDGE, Block.WEAPON_RACK, Block.DUMPSTER};
        String[] loot = {"crate", "military", "locker", "residential", "medical", "industrial", "safe", "food", "weapons", "trash"};
        for (int i = 0; i < boxes.length; i++) g.container(x + 70 + i * 2, G, z + 4, boxes[i], loot[i]);
        // ---- things that are just there: barrels (one burning), tyres, a table and chairs, a plank pile, hay
        g.set(x + 94, G, z + 4, Block.BARREL); g.set(x + 95, G, z + 4, Block.BARREL); g.set(x + 97, G, z + 4, Block.BARREL_FIRE);
        g.set(x + 99, G, z + 4, Block.TIRE); g.set(x + 100, G, z + 4, Block.TIRE);
        g.set(x + 103, G, z + 4, Block.TABLE); g.set(x + 102, G, z + 4, Block.CHAIR); g.set(x + 104, G, z + 4, Block.CHAIR);
        g.set(x + 107, G, z + 4, Block.HAY); g.set(x + 108, G, z + 4, Block.HAY); g.set(x + 108, G + 1, z + 4, Block.HAY);
        g.set(x + 111, G, z + 4, Block.RACK); g.set(x + 112, G, z + 4, Block.SHELF); g.set(x + 113, G, z + 4, Block.PLANT); g.set(x + 114, G, z + 4, Block.FLOOR_LAMP);
        // ---- up and down: a platform three blocks up with stairs at one end and a ladder at the other, and a taller tower
        for (int i = 0; i < 9; i++) for (int k = 0; k < 4; k++) { g.set(x + 4 + i, G + 2, z + 14 + k, Block.CONCRETE_FLOOR); if (i == 0 || i == 8) for (int y = G; y < G + 2; y++) if (k == 0 || k == 3) g.set(x + 4 + i, y, z + 14 + k, Block.STEEL_BEAM); }
        for (int s = 0; s < 3; s++) { g.set(x + 13 + (2 - s), G + s, z + 15, Block.STAIRS_CONCRETE); for (int y = G; y < G + s; y++) g.set(x + 13 + (2 - s), y, z + 15, Block.CONCRETE); }
        for (int y = G; y < G + 3; y++) g.set(x + 3, y, z + 15, Block.LADDER);
        for (int y = G; y < G + 9; y++) { for (int i = 0; i < 3; i++) for (int k = 0; k < 3; k++) if (i == 1 && k == 1) continue; else if (y == G + 8 || (i != 1 && k != 1)) g.set(x + 22 + i, y, z + 14 + k, y == G + 8 ? Block.CONCRETE_FLOOR : Block.STEEL_BEAM); }
        for (int y = G; y < G + 8; y++) g.set(x + 23, y, z + 13, Block.LADDER);
        for (int y = G; y < G + 8; y++) g.set(x + 23, y, z + 14, Block.STEEL_BEAM);
        // ---- a low doorway (two high), a crawl gap (one high) and a vault (one block wall) in one wall
        for (int i = 0; i < 13; i++) for (int y = G; y < G + 4; y++) g.set(x + 32 + i, y, z + 16, Block.CONCRETE);
        g.set(x + 34, G, z + 16, Block.AIR); g.set(x + 34, G + 1, z + 16, Block.AIR);
        g.set(x + 38, G, z + 16, Block.AIR);
        for (int y = G + 1; y < G + 4; y++) g.set(x + 42, y, z + 16, Block.AIR);
        // ---- a firing line: targets (hay) at 10, 25, 50 and 100 m down the yard from the east end
        for (int d : new int[]{10, 25, 50, 100}) { int tx = x + 148 - d; for (int y = G; y < G + 2; y++) g.set(tx, y, z + 24, Block.HAY); g.set(tx, G - 1, z + 22, Block.HAZARD_STRIPE); }
        g.set(x + 148, G - 1, z + 24, Block.HAZARD_STRIPE);
        g.streetLamp(x + 40, z + 1, 0, 1); g.streetLamp(x + 100, z + 1, 0, 1);
    }
}
