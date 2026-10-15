package absentialis.world;

/**
 * Sub-block geometry for everything that is not a perfect cube (v0.3): ladders, doors, fences,
 * lamp posts, chairs, tables, beds, shelving. A shape is a list of boxes in sixteenths of a block;
 * the chunk mesher rotates them to the orientation it reads from the neighbouring blocks (a ladder
 * hugs its wall, a door lies in its wall line, a chair faces its table), so no per-block metadata
 * is stored.
 *
 * Box layout: {x0, y0, z0, x1, y1, z1, tex}. tex: 0 = natural (top / side / bottom texture by
 * face), 1 = side texture on every face, 2 = top texture, 3 = bottom texture; +{@link #PLANE} emits
 * only the two large faces of a thin box (cut-out textures such as rungs and wire mesh).
 */
public final class Shapes {
    private Shapes() {}

    public static final int CUBE = 0, LADDER = 1, DOOR = 2, DOOR_OPEN = 3, FENCE = 4, POST = 5, ARM = 6, CHAIR = 7, TABLE = 8,
            BED = 9, SOFA = 10, TOILET = 11, PEW = 12, TOMBSTONE = 13, SHELF = 14, BARREL = 15, TIRE = 16, FIXTURE = 17, SLAB_LOW = 18, FENCE_Z = 19,
            // 0.7 (interiors): furniture that is the shape of furniture
            DESK = 20, SCREEN = 21, MONITOR = 22, BASIN = 23, BIN = 24, RACK = 25, PLANT = 26, FLOOR_LAMP = 27, NIGHTSTAND = 28, HEAP = 29,
            // 1.0: stairs are stairs - four treads to the block, rising toward the high side - and not a block to be hopped onto.
            // STAIR is solid under its treads (concrete, timber); STAIR_OPEN is treads on two stringers with daylight between (steel)
            STAIR = 30, STAIR_OPEN = 31,
            // 1.0: the stand-ins for props (engine.props). A prop is a model, not a block; the cell it stands in holds one of
            // these so that bodies, bullets and eyes meet something there. They are never drawn: the boxes say only how much
            // of the cell a line of sight is stopped by (all of it, the lower half, a post's width, nothing).
            PHANTOM_FULL = 32, PHANTOM_LOW = 33, PHANTOM_POST = 34, PHANTOM_NONE = 35;
    public static final int PLANE = 8;

    private static final int[][][] BOXES = new int[36][][];

    /** True for a shape that has no geometry of its own (a prop's stand-in). */
    public static boolean invisible(int shape) { return shape >= PHANTOM_FULL && shape <= PHANTOM_NONE; }

    static {
        BOXES[LADDER] = new int[][]{{1, 0, 0, 15, 16, 1, 1 | PLANE}};
        BOXES[DOOR] = new int[][]{{0, 0, 6, 16, 16, 9, 0}};
        BOXES[DOOR_OPEN] = new int[][]{{0, 0, 0, 3, 16, 16, 0}};
        BOXES[FENCE] = new int[][]{{0, 0, 7, 16, 16, 9, 1 | PLANE}};
        BOXES[POST] = new int[][]{{6, 0, 6, 10, 16, 10, 1}};
        BOXES[ARM] = new int[][]{{0, 10, 6, 16, 14, 10, 1}};
        BOXES[CHAIR] = new int[][]{{2, 6, 2, 14, 8, 14, 1}, {2, 0, 2, 4, 6, 4, 1}, {12, 0, 2, 14, 6, 4, 1}, {2, 0, 12, 4, 6, 14, 1}, {12, 0, 12, 14, 6, 14, 1}, {2, 8, 2, 14, 16, 4, 1}};
        BOXES[TABLE] = new int[][]{{0, 13, 0, 16, 16, 16, 2}, {1, 0, 1, 3, 13, 3, 3}, {13, 0, 1, 15, 13, 3, 3}, {1, 0, 13, 3, 13, 15, 3}, {13, 0, 13, 15, 13, 15, 3}};
        BOXES[BED] = new int[][]{{0, 4, 0, 16, 9, 16, 0}, {0, 0, 0, 16, 4, 16, 3}};
        BOXES[SOFA] = new int[][]{{0, 0, 0, 16, 7, 16, 0}, {0, 7, 0, 16, 14, 4, 1}};
        BOXES[TOILET] = new int[][]{{4, 0, 5, 12, 7, 14, 3}, {4, 7, 2, 12, 14, 5, 3}, {3, 7, 5, 13, 8, 14, 3}};
        BOXES[PEW] = new int[][]{{0, 6, 3, 16, 8, 13, 2}, {0, 8, 3, 16, 15, 5, 2}, {0, 0, 3, 2, 6, 13, 2}, {14, 0, 3, 16, 6, 13, 2}};
        BOXES[TOMBSTONE] = new int[][]{{3, 0, 6, 13, 14, 10, 0}, {2, 0, 5, 14, 2, 11, 2}};
        BOXES[SHELF] = new int[][]{{0, 0, 0, 16, 1, 16, 2}, {0, 5, 0, 16, 6, 16, 2}, {0, 10, 0, 16, 11, 16, 2}, {0, 15, 0, 16, 16, 16, 2},
                {0, 1, 0, 1, 15, 1, 2}, {15, 1, 0, 16, 15, 1, 2}, {0, 1, 15, 1, 15, 16, 2}, {15, 1, 15, 16, 15, 16, 2},
                {2, 1, 3, 7, 4, 13, 1}, {9, 1, 4, 14, 5, 12, 1}, {3, 6, 3, 8, 9, 12, 1}, {10, 6, 2, 14, 10, 14, 1}, {2, 11, 4, 12, 14, 12, 1}};
        BOXES[BARREL] = new int[][]{{2, 0, 2, 14, 16, 14, 0}};
        BOXES[TIRE] = new int[][]{{1, 0, 1, 15, 6, 15, 0}};
        // 0.7: a ceiling light is a fitting on the ceiling - a plate and a shade - not half a block hanging into the room
        BOXES[FIXTURE] = new int[][]{{3, 15, 3, 13, 16, 13, 1}, {5, 12, 5, 11, 15, 11, 1}};
        BOXES[DESK] = new int[][]{{0, 13, 0, 16, 16, 16, 2}, {0, 0, 1, 5, 13, 15, 1}, {11, 0, 1, 16, 13, 15, 1}, {5, 5, 1, 11, 13, 2, 1}};
        BOXES[SCREEN] = new int[][]{{1, 0, 3, 15, 6, 13, 3}, {7, 6, 6, 9, 7, 8, 3}, {1, 7, 6, 15, 16, 8, 1}};
        BOXES[MONITOR] = new int[][]{{5, 0, 5, 11, 1, 11, 3}, {7, 1, 7, 9, 5, 9, 3}, {2, 5, 6, 14, 14, 8, 1}, {3, 0, 10, 13, 1, 14, 3}};
        BOXES[BASIN] = new int[][]{{6, 0, 6, 10, 10, 10, 3}, {2, 10, 2, 14, 14, 14, 0}, {7, 14, 2, 9, 16, 4, 3}};
        BOXES[BIN] = new int[][]{{4, 0, 4, 12, 11, 12, 0}};
        BOXES[RACK] = new int[][]{{0, 0, 0, 16, 1, 16, 2}, {0, 5, 0, 16, 6, 16, 2}, {0, 10, 0, 16, 11, 16, 2}, {0, 15, 0, 16, 16, 16, 2},
                {0, 1, 0, 1, 15, 1, 2}, {15, 1, 0, 16, 15, 1, 2}, {0, 1, 15, 1, 15, 16, 2}, {15, 1, 15, 16, 15, 16, 2}, {9, 1, 5, 13, 3, 11, 1}};
        BOXES[PLANT] = new int[][]{{5, 0, 5, 11, 5, 11, 3}, {3, 5, 3, 13, 13, 13, 1}, {5, 13, 5, 11, 16, 11, 1}, {1, 7, 6, 3, 11, 10, 1}, {13, 8, 6, 15, 12, 10, 1}};
        BOXES[FLOOR_LAMP] = new int[][]{{5, 0, 5, 11, 1, 11, 3}, {7, 1, 7, 9, 12, 9, 3}, {4, 11, 4, 12, 16, 12, 1}};
        BOXES[NIGHTSTAND] = new int[][]{{2, 0, 2, 14, 9, 14, 0}, {6, 9, 6, 10, 12, 10, 3}};
        BOXES[HEAP] = new int[][]{{1, 0, 2, 14, 3, 13, 0}, {4, 3, 5, 11, 6, 11, 0}, {9, 0, 10, 15, 2, 15, 0}};
        BOXES[FENCE_Z] = new int[][]{{7, 0, 0, 9, 16, 16, 1 | PLANE}};
        BOXES[SLAB_LOW] = new int[][]{{0, 0, 0, 16, 8, 16, 0}};
        BOXES[PHANTOM_FULL] = new int[][]{{0, 0, 0, 16, 16, 16, 0}};
        BOXES[PHANTOM_LOW] = new int[][]{{0, 0, 0, 16, 9, 16, 0}};
        BOXES[PHANTOM_POST] = new int[][]{{5, 0, 5, 11, 16, 11, 0}};
        BOXES[PHANTOM_NONE] = new int[0][];
        // base orientation: the low side at -z, rising toward +z
        BOXES[STAIR] = new int[][]{{0, 0, 0, 16, 4, 16, 0}, {0, 4, 4, 16, 8, 16, 0}, {0, 8, 8, 16, 12, 16, 0}, {0, 12, 12, 16, 16, 16, 0}};
        BOXES[STAIR_OPEN] = new int[][]{
                {0, 3, 0, 16, 4, 4, 2}, {0, 7, 4, 16, 8, 8, 2}, {0, 11, 8, 16, 12, 12, 2}, {0, 15, 12, 16, 16, 16, 2},
                {0, 0, 0, 1, 3, 4, 1}, {0, 2, 4, 1, 7, 8, 1}, {0, 6, 8, 1, 11, 12, 1}, {0, 10, 12, 1, 15, 16, 1},
                {15, 0, 0, 16, 3, 4, 1}, {15, 2, 4, 16, 7, 8, 1}, {15, 6, 8, 16, 11, 12, 1}, {15, 10, 12, 16, 15, 16, 1}};
    }

    /** The four ways a stair can rise, in the order of the quarter turns that put the base shape that way: +z, -x, -z, +x. */
    public static final int[] STAIR_DX = {0, -1, 0, 1}, STAIR_DZ = {1, 0, -1, 0};

    private static boolean step(World w, int x, int y, int z) { Block b = w.block(x, y, z); return b != null && b.is(Block.STEP); }

    /**
     * Which way a stair block rises (an index into {@link #STAIR_DX} / {@link #STAIR_DZ}), read from what is round it - no
     * block stores a direction. In order: toward the next stair of the flight, one over and one up; away from the last
     * one, one back and one down; toward a full block it leads up onto, with open floor behind it; away from open floor.
     */
    public static int stairDir(World w, int x, int y, int z) {
        for (int d = 0; d < 4; d++) if (step(w, x + STAIR_DX[d], y + 1, z + STAIR_DZ[d])) return d;
        for (int d = 0; d < 4; d++) if (step(w, x - STAIR_DX[d], y - 1, z - STAIR_DZ[d])) return d;
        for (int d = 0; d < 4; d++) {
            int hx = x + STAIR_DX[d], hz = z + STAIR_DZ[d], lx = x - STAIR_DX[d], lz = z - STAIR_DZ[d];
            if (backing(w, hx, y, hz) && !w.solid(hx, y + 1, hz) && !w.solid(lx, y, lz)) return d;
        }
        for (int d = 0; d < 4; d++) { int lx = x - STAIR_DX[d], lz = z - STAIR_DZ[d]; if (!w.solid(lx, y, lz) && w.solid(lx, y - 1, lz)) return d; }
        return 0;
    }

    /** Per shape, the sixteenths of its height at which it is bulky enough to hide what is behind it (0.9, lines of sight). */
    private static final boolean[][] HIDES = new boolean[BOXES.length][];

    static {
        for (int s = 1; s < BOXES.length; s++) {
            boolean[] m = new boolean[16];
            if (BOXES[s] != null) for (int y = 0; y < 16; y++) {
                // how much of the cell's footprint is filled at this height (thin cut-out planes - mesh, rungs - fill none)
                boolean[] cover = new boolean[256];
                int n = 0;
                for (int[] b : BOXES[s]) {
                    if ((b[6] & PLANE) != 0 || y < b[1] || y >= b[4]) continue;
                    for (int x = b[0]; x < b[3]; x++) for (int z = b[2]; z < b[5]; z++) if (!cover[x * 16 + z]) { cover[x * 16 + z] = true; n++; }
                }
                m[y] = n >= 96;
            }
            HIDES[s] = m;
        }
        // shelving holds goods: nobody is made out through a stocked shelf
        java.util.Arrays.fill(HIDES[SHELF], true);
    }

    /** True when a line crossing a cell of this shape between two heights (0..1 of the cell) passes through its bulk. A cube hides at every height. */
    public static boolean hides(int shape, float y0, float y1) {
        if (shape <= 0 || shape >= HIDES.length) return true;
        boolean[] m = HIDES[shape];
        int lo = Math.max(0, Math.min(15, (int) Math.floor(y0 * 16))), hi = Math.max(0, Math.min(15, (int) Math.floor(y1 * 16)));
        for (int y = lo; y <= hi; y++) if (m[y]) return true;
        return false;
    }

    /** 1.2: {@link #hides} as sixteen bits, one a sixteenth of the cell's height - what the native core is told of a shape. */
    public static int hideMask(int shape) {
        if (shape <= 0 || shape >= HIDES.length) return 0xFFFF;
        int m = 0;
        for (int y = 0; y < 16; y++) if (HIDES[shape][y]) m |= 1 << y;
        return m;
    }

    /** How high (0..1 of the cell) a shape is bulky enough to crouch behind; 0 when it hides nothing. */
    public static float hideHeight(int shape) {
        if (shape <= 0 || shape >= HIDES.length) return 1;
        for (int y = 15; y >= 0; y--) if (HIDES[shape][y]) return (y + 1) / 16f;
        return 0;
    }

    public static int[][] boxes(int shape) { return shape <= 0 || shape >= BOXES.length ? null : BOXES[shape]; }

    /** Rotates a box a quarter turn (x,z) -> (16-z, x), 'rot' times, writing the normalised result to out. */
    public static void rotate(int[] box, int rot, int[] out) {
        int x0 = box[0], z0 = box[2], x1 = box[3], z1 = box[5];
        for (int r = 0; r < (rot & 3); r++) {
            int nx0 = 16 - z1, nx1 = 16 - z0, nz0 = x0, nz1 = x1;
            x0 = nx0; x1 = nx1; z0 = nz0; z1 = nz1;
        }
        out[0] = x0; out[1] = box[1]; out[2] = z0; out[3] = x1; out[4] = box[4]; out[5] = z1; out[6] = box[6];
    }

    /**
     * 1.3: which of the rules in {@link #orientation} a shape follows, as the native mesher names them
     * (astratto.h, ASTRATTO_ORIENT_*: 0 none, 1 backing, 2 door, 3 join, 4 chair, 5 pew, 6 scatter, 7 stair).
     */
    public static int orientRule(int shape) {
        return switch (shape) {
            case LADDER, SOFA, TOILET, TOMBSTONE, DESK, SCREEN, MONITOR, BASIN -> 1;
            case DOOR, DOOR_OPEN -> 2;
            case FENCE, ARM -> 3;
            case CHAIR -> 4;
            case PEW -> 5;
            case PLANT, FLOOR_LAMP, NIGHTSTAND, BIN, HEAP -> 6;
            case STAIR, STAIR_OPEN -> 7;
            default -> 0;
        };
    }

    /** Quarter turns that put a shape's back (base: -z) against the given side: 0 -z, 1 +x, 2 +z, 3 -x. */
    public static int orientation(World w, int id, int x, int y, int z) {
        Block b = Block.ALL[id];
        switch (b.shape) {
            case LADDER: case SOFA: case TOILET: case TOMBSTONE: case DESK: case SCREEN: case MONITOR: case BASIN: {
                if (backing(w, x, y, z - 1)) return 0;
                if (backing(w, x + 1, y, z)) return 1;
                if (backing(w, x, y, z + 1)) return 2;
                if (backing(w, x - 1, y, z)) return 3;
                return (x * 31 + z * 17) & 3;
            }
            case DOOR: case DOOR_OPEN: {
                // the wall line: solid neighbours on the x axis mean the leaf spans x
                boolean wx = wall(w, x - 1, y, z) || wall(w, x + 1, y, z), wz = wall(w, x, y, z - 1) || wall(w, x, y, z + 1);
                return wz && !wx ? 1 : 0;
            }
            case FENCE: case ARM: {
                boolean ax = joins(w, id, x - 1, y, z) || joins(w, id, x + 1, y, z), az = joins(w, id, x, y, z - 1) || joins(w, id, x, y, z + 1);
                return az && !ax ? 1 : az ? 4 : 0;   // 4 = both runs (corner / crossing)
            }
            case CHAIR: {
                if (seatFor(w, x, y, z + 1)) return 0;
                if (seatFor(w, x - 1, y, z)) return 1;
                if (seatFor(w, x, y, z - 1)) return 2;
                if (seatFor(w, x + 1, y, z)) return 3;
                return (x * 13 + z * 7 + y) & 3;
            }
            case PEW: return w.get(x, y, z - 1) == id || w.get(x, y, z + 1) == id ? 1 : 0;
            case PLANT: case FLOOR_LAMP: case NIGHTSTAND: case BIN: case HEAP: return (x * 31 + z * 17) & 3;
            case STAIR: case STAIR_OPEN: return stairDir(w, x, y, z);
            default: return 0;
        }
    }

    private static boolean backing(World w, int x, int y, int z) { Block n = w.block(x, y, z); return n != null && n.solid() && n.shape == CUBE; }
    private static boolean wall(World w, int x, int y, int z) { Block n = w.block(x, y, z); return n != null && n.solid() && n.shape == CUBE && !n.is(Block.DOOR); }
    private static boolean joins(World w, int id, int x, int y, int z) {
        int n = w.get(x, y, z);
        if (n == id) return true;
        Block b = Block.ALL[n];
        return b != null && (b.shape == POST || b.shape == FIXTURE || b.shape == ARM || b.shape == FENCE || b.solid() && b.shape == CUBE);
    }
    private static boolean seatFor(World w, int x, int y, int z) { int n = w.get(x, y, z); return n == Block.TABLE || n == Block.DESK || n == Block.COUNTER || n == Block.COMPUTER; }
    /** A monitor faces the chair: its back goes to the wall its desk stands against (one block down). */
    public static int deskBack(World w, int x, int y, int z) { for (int k = 0; k < 4; k++) { int dx = k == 1 ? 1 : k == 3 ? -1 : 0, dz = k == 0 ? -1 : k == 2 ? 1 : 0; if (backing(w, x + dx, y - 1, z + dz)) return k; } return -1; }
}
