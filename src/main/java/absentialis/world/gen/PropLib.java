package absentialis.world.gen;

import absentialis.engine.voxel.VoxModel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * The game's props (1.0): everything a room is furnished with that is not a block - built here, in code, as voxel
 * models at a sixteenth of a block, and stood in the world by the Astratto prop system (engine.props).
 *
 * Before 1.0 a room could only hold what the block table held, and the table is nearly full: every kitchen had the
 * same stove and every office the same desk. A prop costs no block id, so there can be as many as there is reason
 * for, and a place can have things nowhere else has: the plant its control desks and dosimetry posts, the big house
 * its clock, its piano and its portraits, the asylum wing its restraint beds.
 *
 * A model is painted with boxes, cylinders and spheres on a grid of 16 voxels to the block; its back is toward -z
 * (z = 0), which is the side a turn puts against a wall. A voxel whose alpha is below 0xFF glows (GLOW = fully).
 * A definition says how many cells the prop covers and what stands in for it there (see Block.PROP_*):
 * FULL - nothing sees or shoots over it; LOW - cover to crouch behind; POST - something thin; NONE - it hangs on a
 * wall, from a ceiling or lies flat, and takes up no room.
 */
public final class PropLib {
    private PropLib() {}

    public static final int NONE = 0, LOW = 1, FULL = 2, POST = 3;
    public static final int FLOOR = 0, WALL = 1, CEILING = 2;

    public static final class Def {
        public final String name;
        /** Cells covered: along the wall, upward, out from the wall. */
        public final int w, h, d, collide;
        public final boolean metal, glow;
        public final VoxModel model;
        /** Where it goes: on the floor, on a wall (in the open cell in front of it), or under a ceiling. */
        public int mount = FLOOR;
        Def(String name, int w, int h, int d, int collide, boolean metal, boolean glow, VoxModel model) { this.name = name; this.w = w; this.h = h; this.d = d; this.collide = collide; this.metal = metal; this.glow = glow; this.model = model; }
    }

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();

    public static Def get(String name) { return DEFS.get(name); }
    public static int count() { return DEFS.size(); }
    public static Iterable<Def> all() { return DEFS.values(); }

    // ---- palette
    static final int GLOW = 0xFE000000, DIM = 0x90000000, OPQ = 0xFF000000;
    static final int OAK = 0xFF6B4A2B, OAK_D = 0xFF4A3220, OAK_L = 0xFF8A6238, WALNUT = 0xFF3E2A1C, PINE = 0xFFA8834E, EBONY = 0xFF1C1A18,
            IRON = 0xFF3A3D42, STEEL = 0xFF7A8088, STEEL_L = 0xFF9EA4AA, STEEL_D = 0xFF50555C, BRASS = 0xFFB08D3A, GOLD = 0xFFC8A040, COPPER = 0xFF9A5A32, RUST = 0xFF7A4526,
            WHITE = 0xFFDAD8D0, CREAM = 0xFFC8BE9E, BONE = 0xFFB8B09A, GREY = 0xFF8A8A86, GREY_D = 0xFF55565A, BLACK = 0xFF18181A, CHARCOAL = 0xFF2A2B2E,
            RED = 0xFF8E2A22, RED_D = 0xFF5E1A16, WINE = 0xFF4E1420, GREEN = 0xFF3E6A3A, GREEN_D = 0xFF24402A, OLIVE = 0xFF5A6038, BLUE = 0xFF2E4A72, BLUE_D = 0xFF1E2E4A, TEAL = 0xFF2E6A6A,
            YELLOW = 0xFFC8A82A, HAZARD = 0xFFD8B020, ORANGE = 0xFFC86A20, MUSTARD = 0xFF9A8230, LEATHER = 0xFF5A3A22, LEATHER_D = 0xFF3A2416, LINEN = 0xFFB8AC94, VELVET = 0xFF6A1A26,
            GLASS_C = 0xFF9AB4BC, PORCELAIN = 0xFFE4E2DA, MARBLE_C = 0xFFC8C6C0, STONE_C = 0xFF8A8880, PAPER = 0xFFD4CEB8, RUBBER = 0xFF202022, ENAMEL = 0xFFC8CCC4, MINT = 0xFF8AB0A0, CRT = 0xFF1E3A2A;
    static final int LAMP_WARM = GLOW | 0xFFD890, LAMP_COLD = GLOW | 0xD8E8F0, FLAME = GLOW | 0xFFB040, EMBER_C = GLOW | 0xE05A18, LED_G = GLOW | 0x40E060, LED_R = GLOW | 0xE83828, LED_A = GLOW | 0xF0A020, SCREEN_G = DIM | 0x3AC878, SCREEN_B = DIM | 0x6AA8D8;

    private static void def(String name, int w, int h, int d, int collide, boolean metal, boolean glow, Consumer<VoxModel> paint) {
        VoxModel m = new VoxModel(w * 16, h * 16, d * 16);
        paint.accept(m);
        // 1.2: every bed was drawn with its pillows and headboard at the far end from the wall it stands against, so every bed
        // in the city had its foot to the wall and its head out in the room. They are turned end for end here, once.
        if (name.startsWith("bed") || name.equals("bunk_cell")) for (int x = 0; x < m.sx; x++) for (int y = 0; y < m.sy; y++) for (int z = 0; z < m.sz / 2; z++) { int a = m.get(x, y, z), b = m.get(x, y, m.sz - 1 - z); m.set(x, y, z, b); m.set(x, y, m.sz - 1 - z, a); }
        DEFS.put(name, new Def(name, w, h, d, collide, metal, glow, m));
    }

    private static void wood(String name, int w, int h, int d, int collide, Consumer<VoxModel> paint) { def(name, w, h, d, collide, false, false, paint); }
    private static void metal(String name, int w, int h, int d, int collide, Consumer<VoxModel> paint) { def(name, w, h, d, collide, true, false, paint); }
    /** Hangs or lies: takes up no room. */
    private static void deco(String name, int w, int h, int d, Consumer<VoxModel> paint) { def(name, w, h, d, NONE, false, false, paint); }
    private static void light(String name, int w, int h, int d, int collide, Consumer<VoxModel> paint) { def(name, w, h, d, collide, false, true, paint); }

    private static int sh(int c, float k) {
        int a = c & 0xFF000000, r = Math.max(0, Math.min(255, Math.round(((c >> 16) & 255) * k))), g = Math.max(0, Math.min(255, Math.round(((c >> 8) & 255) * k))), b = Math.max(0, Math.min(255, Math.round((c & 255) * k)));
        return a | r << 16 | g << 8 | b;
    }

    /** Four legs under a top: at the corners of the box (x0, z0)-(x1, z1), from the floor up to y. */
    private static void legs(VoxModel m, int x0, int z0, int x1, int z1, int y, int t, int c) {
        m.box(x0, 0, z0, x0 + t - 1, y, z0 + t - 1, c); m.box(x1 - t + 1, 0, z0, x1, y, z0 + t - 1, c);
        m.box(x0, 0, z1 - t + 1, x0 + t - 1, y, z1, c); m.box(x1 - t + 1, 0, z1 - t + 1, x1, y, z1, c);
    }

    private static final int[] BOOKS = {0xFF6A2A22, 0xFF2A4A6A, 0xFF3A5A32, 0xFF7A6A3A, 0xFF4A2A4A, 0xFF8A8270, 0xFF2A2A2E, 0xFF7A3A1E};

    /** A row of book spines standing on a shelf: from x0 to x1 at height y, hgt tall or a little less, z from z0 to z1. */
    private static void books(VoxModel m, Random r, int x0, int x1, int y, int hgt, int z0, int z1) {
        for (int x = x0; x <= x1; ) {
            int wdt = 1 + r.nextInt(2), hh = hgt - r.nextInt(3);
            if (r.nextInt(9) == 0) { x += 1 + r.nextInt(2); continue; }                 // a gap where one was taken down
            m.box(x, y, z0, Math.min(x1, x + wdt - 1), y + hh - 1, z1, BOOKS[r.nextInt(BOOKS.length)]);
            x += wdt;
        }
    }

    private static void bottles(VoxModel m, Random r, int x0, int x1, int y, int z) {
        int[] cols = {0xFF2E5A32, 0xFF5A3A1E, 0xFF8AA0A8, 0xFF3A2A1A, 0xFF7A2A22};
        for (int x = x0; x <= x1; x += 2) { if (r.nextInt(5) == 0) continue; int c = cols[r.nextInt(cols.length)], hh = 3 + r.nextInt(2); m.box(x, y, z, x, y + hh - 1, z, c); m.set(x, y + hh, z, sh(c, 1.3f)); }
    }

    /** A bank of small lights on a face at z: columns x0..x1 step 2, rows y0..y1 step 2, mostly dark, a few lit. */
    private static void lights(VoxModel m, Random r, int x0, int x1, int y0, int y1, int z) {
        for (int x = x0; x <= x1; x += 2) for (int y = y0; y <= y1; y += 2) { int k = r.nextInt(10); m.set(x, y, z, k == 0 ? LED_G : k == 1 ? LED_A : k == 2 ? LED_R : CHARCOAL); }
    }

    static {
        final Random r = new Random(1986);

        // ================================================================== seating and tables
        wood("chair_dining", 1, 1, 1, LOW, m -> { legs(m, 3, 3, 12, 12, 6, 2, OAK_D); m.box(3, 7, 3, 12, 8, 12, VELVET); m.box(3, 9, 3, 12, 15, 4, OAK_D); m.box(5, 10, 3, 10, 14, 3, VELVET); });
        wood("chair_wood", 1, 1, 1, LOW, m -> { legs(m, 3, 3, 12, 12, 6, 2, PINE); m.box(3, 7, 3, 12, 7, 12, PINE); m.box(3, 8, 3, 4, 15, 4, PINE); m.box(11, 8, 3, 12, 15, 4, PINE); m.box(3, 11, 3, 12, 12, 3, OAK_L); m.box(3, 14, 3, 12, 15, 3, OAK_L); });
        wood("armchair", 1, 1, 1, LOW, m -> { m.box(2, 0, 2, 13, 2, 13, WALNUT); m.box(2, 3, 2, 13, 7, 13, LEATHER); m.box(2, 8, 2, 13, 15, 5, LEATHER); m.box(2, 8, 5, 4, 11, 13, LEATHER_D); m.box(11, 8, 5, 13, 11, 13, LEATHER_D); m.box(5, 8, 6, 10, 8, 12, sh(LEATHER, 1.15f)); });
        wood("armchair_green", 1, 1, 1, LOW, m -> { m.box(2, 0, 2, 13, 2, 13, OAK_D); m.box(2, 3, 2, 13, 7, 13, GREEN_D); m.box(2, 8, 2, 13, 15, 5, GREEN_D); m.box(2, 8, 5, 4, 11, 13, GREEN); m.box(11, 8, 5, 13, 11, 13, GREEN); });
        wood("sofa_leather", 2, 1, 1, LOW, m -> { m.box(1, 0, 2, 30, 2, 14, WALNUT); m.box(1, 3, 2, 30, 7, 14, LEATHER); m.box(1, 8, 2, 30, 15, 5, LEATHER); m.box(1, 8, 5, 3, 11, 14, LEATHER_D); m.box(28, 8, 5, 30, 11, 14, LEATHER_D); m.box(15, 8, 6, 16, 8, 13, LEATHER_D); });
        wood("sofa_floral", 2, 1, 1, LOW, m -> { m.box(1, 0, 2, 30, 2, 14, OAK_D); m.box(1, 3, 2, 30, 7, 14, 0xFF8A7458); m.box(1, 8, 2, 30, 14, 5, 0xFF8A7458); m.box(1, 8, 5, 3, 10, 14, 0xFF6E5A42); m.box(28, 8, 5, 30, 10, 14, 0xFF6E5A42); for (int x = 4; x < 28; x += 5) for (int z = 7; z < 13; z += 4) m.set(x, 8, z, RED); });
        wood("stool_bar", 1, 1, 1, POST, m -> { m.box(7, 0, 7, 8, 10, 8, STEEL_D); m.cylY(0, 0, 8, 8, 4, STEEL_D); m.cylY(11, 12, 8, 8, 4.5f, RED_D); });
        wood("bench_wood", 2, 1, 1, LOW, m -> { legs(m, 1, 4, 30, 11, 6, 2, OAK_D); m.box(0, 7, 3, 31, 8, 12, OAK); });
        wood("bench_locker", 2, 1, 1, LOW, m -> { legs(m, 2, 5, 29, 10, 6, 2, STEEL_D); m.box(0, 7, 4, 31, 8, 11, PINE); });
        wood("table_dining", 2, 1, 1, LOW, m -> { legs(m, 1, 2, 30, 13, 11, 2, WALNUT); m.box(0, 12, 1, 31, 13, 14, OAK_D); m.box(0, 14, 1, 31, 14, 14, 0xFFB0A688); });
        // "A meal remains laid out": two places set and never cleared
        wood("table_laid", 2, 1, 1, LOW, m -> { legs(m, 1, 2, 30, 13, 11, 2, WALNUT); m.box(0, 12, 1, 31, 13, 14, OAK_D); m.box(0, 14, 1, 31, 14, 14, 0xFFB0A688);
            for (int x : new int[]{7, 23}) { m.cylY(15, 15, x + 0.5f, 8, 3.2f, PORCELAIN); m.set(x - 4, 15, 8, STEEL_L); m.set(x + 4, 15, 8, STEEL_L); m.box(x + 3, 15, 4, x + 3, 17, 4, GLASS_C); }
            m.box(15, 15, 7, 16, 18, 8, BRASS); m.set(15, 19, 7, CREAM); m.set(16, 19, 8, CREAM); });
        wood("table_round", 1, 1, 1, LOW, m -> { m.box(7, 0, 7, 8, 11, 8, WALNUT); m.cylY(0, 0, 8, 8, 4, WALNUT); m.cylY(12, 13, 8, 8, 7.5f, OAK_D); });
        wood("table_side", 1, 1, 1, LOW, m -> { legs(m, 4, 4, 11, 11, 9, 1, OAK_D); m.box(3, 10, 3, 12, 10, 12, OAK); m.box(4, 5, 4, 11, 5, 11, OAK_D); });
        wood("table_coffee", 2, 1, 1, LOW, m -> { legs(m, 3, 4, 28, 11, 5, 2, OAK_D); m.box(2, 6, 3, 29, 7, 12, OAK); m.box(8, 8, 6, 13, 8, 9, PAPER); m.box(20, 8, 7, 22, 9, 9, 0xFF6A2A22); });
        wood("table_kitchen", 2, 1, 1, LOW, m -> { legs(m, 1, 2, 30, 13, 11, 2, PINE); m.box(0, 12, 1, 31, 13, 14, PINE); m.box(0, 14, 1, 31, 14, 14, 0xFF9AA890); m.cylY(15, 16, 9, 8, 2.5f, ENAMEL); m.box(20, 15, 5, 26, 15, 10, OAK_L); m.box(22, 16, 7, 24, 16, 8, BONE); });
        wood("desk_writing", 2, 1, 1, LOW, m -> { m.box(0, 12, 1, 31, 13, 14, WALNUT); m.box(1, 0, 2, 9, 11, 13, OAK_D); m.box(22, 0, 2, 30, 11, 13, OAK_D); for (int y = 2; y < 11; y += 3) { m.box(2, y, 14, 8, y + 1, 14, WALNUT); m.set(5, y, 15, BRASS); m.box(23, y, 14, 29, y + 1, 14, WALNUT); m.set(26, y, 15, BRASS); }
            m.box(4, 14, 4, 12, 14, 10, GREEN_D); m.box(6, 15, 5, 10, 15, 8, PAPER); m.box(24, 14, 3, 26, 17, 5, BRASS); m.box(24, 18, 2, 26, 19, 6, GREEN_D); });
        wood("desk_office", 2, 1, 1, LOW, m -> { m.box(0, 12, 1, 31, 13, 14, 0xFF7A6A52); m.box(1, 0, 2, 2, 11, 13, STEEL_D); m.box(29, 0, 2, 30, 11, 13, STEEL_D); m.box(20, 3, 2, 28, 11, 13, STEEL); for (int y = 4; y < 11; y += 3) m.box(21, y, 14, 27, y + 1, 14, STEEL_L);
            m.box(3, 14, 3, 10, 14, 9, PAPER); m.box(12, 14, 4, 14, 16, 6, BLACK); m.box(15, 14, 9, 22, 14, 12, CHARCOAL); });
        wood("desk_reception", 2, 2, 1, FULL, m -> { m.box(0, 0, 4, 31, 17, 12, OAK_D); m.box(0, 18, 3, 31, 19, 13, MARBLE_C); m.box(2, 3, 13, 29, 15, 13, OAK); m.box(8, 20, 6, 12, 20, 10, PAPER); m.box(20, 20, 5, 22, 23, 7, BLACK); });

        // ================================================================== beds and bedrooms
        wood("bed_single", 1, 1, 2, LOW, m -> { m.box(1, 0, 0, 14, 4, 31, OAK_D); m.box(1, 5, 1, 14, 7, 30, LINEN); m.box(1, 8, 1, 14, 8, 20, 0xFF5A6A7A); m.box(3, 8, 23, 12, 9, 29, WHITE); m.box(1, 5, 0, 14, 12, 0, OAK_D); m.box(1, 0, 31, 14, 14, 31, OAK_D); });
        wood("bed_double", 2, 1, 2, LOW, m -> { m.box(1, 0, 0, 30, 4, 31, WALNUT); m.box(1, 5, 1, 30, 7, 30, LINEN); m.box(1, 8, 1, 30, 8, 19, WINE); m.box(3, 8, 23, 13, 9, 29, WHITE); m.box(18, 8, 23, 28, 9, 29, WHITE); m.box(1, 5, 0, 30, 11, 0, WALNUT); m.box(1, 0, 31, 30, 15, 31, WALNUT); m.box(4, 12, 31, 27, 14, 31, OAK); });
        wood("bed_four_poster", 2, 2, 2, LOW, m -> { m.box(1, 0, 0, 30, 4, 31, WALNUT); m.box(1, 5, 1, 30, 7, 30, LINEN); m.box(1, 8, 1, 30, 8, 19, VELVET); m.box(3, 8, 23, 13, 9, 29, WHITE); m.box(18, 8, 23, 28, 9, 29, WHITE);
            for (int[] c : new int[][]{{0, 0}, {30, 0}, {0, 30}, {30, 30}}) m.box(c[0], 0, c[1], c[0] + 1, 29, c[1] + 1, WALNUT);
            m.box(0, 30, 0, 31, 31, 31, WALNUT); m.box(2, 30, 2, 29, 30, 29, 0); m.box(0, 26, 0, 31, 29, 0, VELVET); m.box(0, 26, 31, 31, 29, 31, VELVET); m.box(0, 26, 0, 0, 29, 31, VELVET); m.box(31, 26, 0, 31, 29, 31, VELVET); m.box(2, 5, 31, 29, 18, 31, OAK_D); });
        wood("bed_bunk", 1, 2, 2, FULL, m -> { for (int y : new int[]{4, 20}) { m.box(1, y, 1, 14, y + 1, 30, STEEL_D); m.box(1, y + 2, 1, 14, y + 3, 30, 0xFF7A7A6A); m.box(3, y + 4, 24, 12, y + 4, 29, WHITE); m.box(1, y + 4, 2, 14, y + 4, 18, OLIVE); }
            for (int[] c : new int[][]{{0, 0}, {14, 0}, {0, 30}, {14, 30}}) m.box(c[0], 0, c[1], c[0] + 1, 30, c[1] + 1, STEEL_D); for (int y = 8; y < 20; y += 4) m.box(14, y, 3, 15, y, 6, STEEL_D); });
        wood("cot_child", 1, 1, 1, LOW, m -> { legs(m, 1, 2, 14, 13, 12, 1, WHITE); m.box(1, 5, 2, 14, 6, 13, LINEN); for (int x = 1; x <= 14; x += 2) { m.box(x, 7, 2, x, 12, 2, WHITE); m.box(x, 7, 13, x, 12, 13, WHITE); } m.box(1, 12, 2, 14, 12, 2, WHITE); m.box(1, 12, 13, 14, 12, 13, WHITE); m.box(4, 7, 5, 8, 8, 9, 0xFF8A9AB8); });
        wood("wardrobe", 1, 2, 1, FULL, m -> { m.box(0, 0, 1, 15, 30, 12, WALNUT); m.box(0, 31, 0, 15, 31, 13, OAK_D); m.box(1, 2, 13, 7, 28, 13, OAK_D); m.box(8, 2, 13, 14, 28, 13, OAK_D); m.box(2, 4, 14, 6, 26, 14, OAK); m.box(9, 4, 14, 13, 26, 14, OAK); m.set(7, 15, 14, BRASS); m.set(8, 15, 14, BRASS); });
        wood("wardrobe_open", 1, 2, 1, FULL, m -> { m.box(0, 0, 1, 15, 30, 12, WALNUT); m.box(0, 31, 0, 15, 31, 13, OAK_D); m.box(1, 2, 3, 14, 28, 12, 0); m.box(0, 2, 13, 0, 28, 15, OAK_D); m.box(15, 2, 13, 15, 28, 15, OAK_D); m.box(1, 26, 7, 14, 26, 7, BRASS);
            int[] coats = {0xFF3A3A4A, 0xFF5A4A32, 0xFF7A2A2A, 0xFF2A4A3A}; for (int i = 0; i < 4; i++) m.box(2 + i * 3, 12 + (i % 2) * 2, 5, 3 + i * 3, 25, 9, coats[i]); m.box(2, 2, 4, 6, 4, 8, LEATHER_D); });
        wood("dresser", 1, 1, 1, LOW, m -> { m.box(0, 0, 1, 15, 12, 12, OAK_D); m.box(0, 13, 0, 15, 13, 13, OAK); for (int y = 1; y < 12; y += 4) { m.box(1, y, 13, 14, y + 2, 13, OAK); m.set(4, y + 1, 14, BRASS); m.set(11, y + 1, 14, BRASS); } m.box(3, 14, 4, 6, 15, 7, PORCELAIN); m.box(10, 14, 3, 12, 17, 4, GOLD); m.box(10, 15, 3, 12, 16, 3, 0xFF8A7458); });
        wood("vanity", 1, 2, 1, LOW, m -> { legs(m, 1, 2, 14, 11, 10, 1, WHITE); m.box(0, 11, 1, 15, 12, 12, WHITE); m.box(1, 8, 12, 14, 10, 12, CREAM); m.box(3, 13, 1, 12, 27, 1, GOLD); m.box(4, 14, 2, 11, 26, 2, GLASS_C); m.box(3, 13, 5, 4, 15, 6, 0xFFB86A8A); m.box(11, 13, 6, 12, 14, 7, GLASS_C); });
        wood("nightstand_lamp", 1, 1, 1, LOW, m -> { m.box(2, 0, 2, 13, 8, 13, OAK_D); m.box(1, 9, 1, 14, 9, 14, OAK); m.box(3, 2, 14, 12, 6, 14, OAK); m.set(7, 4, 15, BRASS); m.box(7, 10, 7, 8, 12, 8, BRASS); m.box(5, 13, 5, 10, 15, 10, DIM | 0xE8D0A0); });
        wood("trunk", 1, 1, 1, LOW, m -> { m.box(1, 0, 3, 14, 7, 12, LEATHER_D); m.box(1, 8, 3, 14, 9, 12, LEATHER); m.box(1, 0, 3, 1, 9, 12, BRASS); m.box(14, 0, 3, 14, 9, 12, BRASS); m.box(7, 5, 13, 8, 7, 13, BRASS); });
        wood("rocking_horse", 1, 1, 1, LOW, m -> { m.box(3, 0, 6, 12, 0, 6, RED); m.box(3, 0, 9, 12, 0, 9, RED); m.set(2, 1, 6, RED); m.set(13, 1, 6, RED); m.set(2, 1, 9, RED); m.set(13, 1, 9, RED); m.box(5, 1, 7, 5, 4, 8, CREAM); m.box(10, 1, 7, 10, 4, 8, CREAM); m.box(4, 5, 6, 11, 8, 9, CREAM); m.box(11, 8, 7, 12, 12, 8, CREAM); m.box(12, 11, 7, 14, 12, 8, CREAM); m.box(6, 9, 6, 9, 9, 9, RED); m.set(3, 8, 7, OAK_D); });
        wood("toy_blocks", 1, 1, 1, NONE, m -> { int[] cs = {RED, BLUE, YELLOW, GREEN}; for (int i = 0; i < 6; i++) { int x = 2 + r.nextInt(11), z = 2 + r.nextInt(11); m.box(x, 0, z, x + 1, 1, z + 1, cs[i % 4]); } m.box(6, 2, 6, 7, 3, 7, YELLOW); });

        // ================================================================== storage and shelving
        wood("bookcase", 1, 2, 1, FULL, m -> { m.box(0, 0, 0, 15, 31, 7, WALNUT); for (int y = 1; y < 28; y += 6) { m.box(1, y, 1, 14, y + 4, 7, 0); books(m, r, 1, 14, y, 5, 2, 6); } m.box(0, 30, 0, 15, 31, 8, OAK_D); });
        wood("bookcase_low", 1, 1, 1, LOW, m -> { m.box(0, 0, 0, 15, 14, 7, OAK_D); for (int y = 1; y < 12; y += 6) { m.box(1, y, 1, 14, y + 4, 7, 0); books(m, r, 1, 14, y, 5, 2, 6); } m.box(0, 15, 0, 15, 15, 8, OAK); m.box(4, 16, 2, 6, 19, 4, PORCELAIN); });
        wood("bookcase_ransacked", 1, 2, 1, FULL, m -> { m.box(0, 0, 0, 15, 31, 7, WALNUT); for (int y = 1; y < 28; y += 6) { m.box(1, y, 1, 14, y + 4, 7, 0); if (y % 12 == 1) books(m, r, 1, 6 + r.nextInt(6), y, 5, 2, 6); else { m.box(2, y, 2, 7, y, 6, BOOKS[1]); m.box(9, y, 3, 13, y + 1, 5, BOOKS[3]); } } });
        wood("china_cabinet", 1, 2, 1, FULL, m -> { m.box(0, 0, 0, 15, 12, 9, WALNUT); m.box(1, 2, 10, 14, 10, 10, OAK_D); m.set(5, 6, 11, BRASS); m.set(10, 6, 11, BRASS); m.box(0, 13, 0, 15, 30, 8, WALNUT); m.box(1, 14, 1, 14, 29, 8, 0); m.box(1, 20, 1, 14, 20, 7, OAK_D); m.box(0, 31, 0, 15, 31, 9, OAK_D);
            for (int x = 2; x < 14; x += 4) { m.cylY(14, 14, x + 1, 4, 1.6f, PORCELAIN); m.box(x, 21, 3, x + 1, 23, 4, PORCELAIN); m.set(x, 24, 3, BLUE); } });
        wood("sideboard", 2, 1, 1, LOW, m -> { m.box(0, 2, 1, 31, 12, 11, WALNUT); legs(m, 1, 2, 30, 10, 1, 2, WALNUT); m.box(0, 13, 0, 31, 13, 12, OAK_D); for (int x = 1; x < 30; x += 10) { m.box(x, 3, 12, x + 8, 11, 12, OAK_D); m.set(x + 4, 7, 13, BRASS); } m.box(4, 14, 3, 6, 19, 5, 0xFF8AA0A8); m.box(14, 14, 4, 17, 14, 8, STEEL_L); m.cylY(14, 16, 25, 6, 2.5f, PORCELAIN); });
        wood("wine_rack", 1, 2, 1, FULL, m -> { m.box(0, 0, 0, 15, 31, 9, OAK_D); for (int y = 2; y < 30; y += 5) for (int x = 2; x < 14; x += 4) { m.box(x, y, 1, x + 2, y + 2, 9, 0); if (r.nextInt(4) != 0) m.box(x, y, 3, x + 1, y + 1, 10, 0xFF24402A); } });
        metal("shelf_cans", 1, 2, 1, FULL, m -> { for (int y = 0; y < 30; y += 8) { m.box(0, y, 1, 15, y, 12, STEEL_D); int[] cs = {0xFF8E2A22, 0xFFC8A82A, 0xFF3E6A3A, 0xFFB8B09A, 0xFF2E4A72}; for (int x = 1; x < 14; x += 3) if (r.nextInt(3) != 0) m.box(x, y + 1, 3 + r.nextInt(3), x + 1, y + 3, 5 + r.nextInt(3), cs[r.nextInt(cs.length)]); }
            for (int[] c : new int[][]{{0, 1}, {15, 1}, {0, 12}, {15, 12}}) m.box(c[0], 0, c[1], c[0], 31, c[1], STEEL_D); });
        metal("shelf_empty", 1, 2, 1, FULL, m -> { for (int y = 0; y < 30; y += 8) m.box(0, y, 1, 15, y, 12, STEEL_D); for (int[] c : new int[][]{{0, 1}, {15, 1}, {0, 12}, {15, 12}}) m.box(c[0], 0, c[1], c[0], 31, c[1], STEEL_D); m.box(3, 1, 4, 8, 3, 9, 0xFF8A7458); m.box(10, 9, 5, 12, 9, 9, PAPER); });
        metal("shelf_parts", 1, 2, 1, FULL, m -> { for (int y = 0; y < 30; y += 8) { m.box(0, y, 1, 15, y, 12, STEEL_D); m.box(1 + r.nextInt(3), y + 1, 3, 6 + r.nextInt(3), y + 3 + r.nextInt(2), 10, r.nextBoolean() ? 0xFF6A6048 : STEEL); m.cylY(y + 1, y + 2, 11.5f, 6, 2.2f, r.nextBoolean() ? RUST : COPPER); }
            for (int[] c : new int[][]{{0, 1}, {15, 1}, {0, 12}, {15, 12}}) m.box(c[0], 0, c[1], c[0], 31, c[1], STEEL_D); });
        metal("locker_row", 1, 2, 1, FULL, m -> { m.box(0, 0, 0, 15, 30, 10, 0xFF5A6A6A); m.box(7, 0, 11, 8, 30, 11, GREY_D); m.box(0, 0, 11, 6, 30, 11, 0xFF62746E); m.box(9, 0, 11, 15, 30, 11, 0xFF62746E); for (int x : new int[]{3, 12}) { for (int y = 24; y < 29; y += 2) m.box(x - 1, y, 12, x + 1, y, 12, CHARCOAL); m.set(x + 2, 15, 12, STEEL_L); } });
        metal("locker_open", 1, 2, 1, FULL, m -> { m.box(0, 0, 0, 15, 30, 10, 0xFF5A6A6A); m.box(1, 1, 1, 6, 29, 10, 0); m.box(0, 1, 11, 0, 29, 15, 0xFF62746E); m.box(9, 0, 11, 15, 30, 11, 0xFF62746E); m.box(1, 22, 2, 6, 22, 9, STEEL_D); m.box(2, 23, 3, 5, 26, 7, HAZARD); m.box(2, 8, 4, 5, 21, 7, 0xFFB8B4A0); m.box(2, 1, 3, 5, 3, 8, RUBBER); });
        metal("filing_cabinet", 1, 2, 1, FULL, m -> { m.box(1, 0, 1, 14, 26, 13, 0xFF6A7068); for (int y = 1; y < 25; y += 6) { m.box(2, y, 14, 13, y + 4, 14, 0xFF7A8078); m.box(6, y + 2, 15, 9, y + 2, 15, STEEL_L); m.box(6, y + 4, 15, 8, y + 4, 15, PAPER); } m.box(3, 27, 3, 9, 28, 9, PAPER); });
        metal("filing_open", 1, 2, 1, FULL, m -> { m.box(1, 0, 1, 14, 26, 13, 0xFF6A7068); for (int y = 1; y < 25; y += 6) { m.box(2, y, 14, 13, y + 4, 14, 0xFF7A8078); m.box(6, y + 2, 15, 9, y + 2, 15, STEEL_L); } m.box(2, 13, 14, 13, 17, 15, 0xFF4A504A); m.box(3, 15, 14, 12, 18, 15, PAPER); });
        wood("crate_stack", 1, 2, 1, FULL, m -> { m.box(0, 0, 0, 15, 14, 15, PINE); m.box(0, 0, 0, 15, 0, 15, OAK_D); m.box(0, 7, 0, 15, 7, 15, OAK_D); m.box(0, 14, 0, 15, 14, 15, OAK_D); m.box(2, 15, 2, 13, 27, 13, 0xFF9A7A48); m.box(2, 21, 2, 13, 21, 13, OAK_D); m.box(5, 17, 14, 10, 19, 14, CHARCOAL); });
        wood("barrel_wine", 1, 1, 1, LOW, m -> { m.cylY(0, 15, 8, 8, 6, OAK); m.cylY(3, 12, 8, 8, 7, OAK); m.cylY(2, 2, 8, 8, 7, IRON); m.cylY(13, 13, 8, 8, 7, IRON); m.cylY(7, 8, 8, 8, 7.4f, IRON); });
        metal("drum_hazard", 1, 1, 1, LOW, m -> { m.cylY(0, 15, 8, 8, 6, HAZARD); m.cylY(5, 5, 8, 8, 6.4f, 0xFFA88818); m.cylY(11, 11, 8, 8, 6.4f, 0xFFA88818); m.cylY(15, 15, 8, 8, 5, 0xFFA88818); m.box(6, 7, 14, 9, 10, 14, BLACK); m.set(7, 8, 14, HAZARD); m.set(8, 9, 14, HAZARD); });
        metal("drum_oil", 1, 1, 1, LOW, m -> { m.cylY(0, 15, 8, 8, 6, 0xFF2A4A6A); m.cylY(5, 5, 8, 8, 6.4f, 0xFF223A54); m.cylY(11, 11, 8, 8, 6.4f, 0xFF223A54); m.cylY(15, 15, 8, 8, 5, 0xFF223A54); m.box(4, 12, 2, 7, 15, 3, RUST); });
        wood("sacks", 1, 1, 1, LOW, m -> { m.box(1, 0, 2, 9, 5, 13, 0xFF9A8A62); m.box(7, 0, 5, 14, 4, 14, 0xFF8A7A54); m.box(3, 5, 4, 12, 9, 12, 0xFFA09068); m.box(5, 10, 6, 10, 10, 10, 0xFF8A7A54); });
        wood("boxes_cardboard", 1, 1, 1, LOW, m -> { m.box(1, 0, 1, 10, 7, 11, 0xFF9A7A4A); m.box(1, 7, 6, 10, 7, 6, 0xFF7A5E36); m.box(8, 0, 9, 15, 5, 15, 0xFF8A6A3E); m.box(3, 8, 3, 11, 13, 10, 0xFFA8864E); m.box(3, 13, 6, 11, 13, 6, 0xFFD4CEB8); });

        // ================================================================== the big house
        wood("grandfather_clock", 1, 2, 1, FULL, m -> { m.box(3, 0, 3, 12, 3, 11, WALNUT); m.box(4, 4, 4, 11, 20, 10, WALNUT); m.box(5, 6, 11, 10, 18, 11, GLASS_C); m.box(7, 8, 10, 8, 16, 10, BRASS); m.cylX(6, 9, 7, 10.5f, 1.6f, BRASS);
            m.box(3, 21, 3, 12, 29, 11, WALNUT); m.box(4, 22, 12, 11, 28, 12, CREAM); m.set(7, 25, 13, BLACK); m.set(8, 25, 13, BLACK); m.set(8, 26, 13, BLACK); m.set(8, 27, 13, BLACK); m.box(2, 30, 2, 13, 30, 12, OAK_D); m.box(5, 31, 5, 10, 31, 9, OAK_D); });
        wood("piano_grand", 2, 1, 2, LOW, m -> { m.box(1, 9, 1, 30, 13, 20, EBONY); m.box(8, 9, 21, 30, 13, 27, EBONY); m.box(16, 9, 28, 30, 13, 30, EBONY); m.box(1, 9, 0, 30, 10, 0, EBONY); m.box(2, 11, 0, 29, 11, 3, PORCELAIN); for (int x = 3; x < 29; x += 2) m.set(x, 12, 1, BLACK);
            m.box(3, 0, 3, 4, 8, 4, EBONY); m.box(27, 0, 3, 28, 8, 4, EBONY); m.box(22, 0, 26, 23, 8, 27, EBONY); m.box(1, 14, 4, 30, 14, 20, sh(EBONY, 1.4f)); m.box(14, 15, 3, 18, 19, 3, EBONY); m.box(15, 16, 4, 17, 18, 4, PAPER); });
        wood("piano_upright", 2, 2, 1, FULL, m -> { m.box(1, 0, 0, 30, 19, 8, WALNUT); m.box(1, 9, 9, 30, 10, 13, WALNUT); m.box(2, 11, 9, 29, 11, 12, PORCELAIN); for (int x = 3; x < 29; x += 2) m.set(x, 12, 9, BLACK); m.box(1, 0, 9, 2, 8, 12, WALNUT); m.box(29, 0, 9, 30, 8, 12, WALNUT); m.box(1, 20, 0, 30, 20, 9, OAK_D); m.box(12, 13, 9, 19, 17, 9, PAPER); m.box(4, 21, 2, 6, 25, 4, BRASS); });
        wood("piano_stool", 1, 1, 1, LOW, m -> { legs(m, 4, 5, 11, 10, 6, 1, EBONY); m.box(3, 7, 4, 12, 8, 11, VELVET); });
        wood("harp", 1, 2, 1, POST, m -> { m.box(4, 0, 6, 11, 1, 10, GOLD); m.box(4, 2, 7, 5, 27, 8, GOLD); for (int i = 0; i < 8; i++) m.set(5 + i, 27 - i / 2, 7, GOLD); m.box(11, 2, 7, 12, 23, 8, OAK_L); for (int x = 6; x < 11; x++) m.box(x, 3, 7, x, 26 - (x - 5) / 2, 7, 0xFFD8D0B8); });
        wood("globe", 1, 1, 1, POST, m -> { legs(m, 5, 5, 10, 10, 6, 1, WALNUT); m.box(5, 7, 5, 10, 7, 10, WALNUT); m.sphere(8, 12, 8, 4.2f, 0xFF3A5E7A); m.box(6, 11, 4, 9, 14, 4, 0xFF6A7A42); m.box(10, 10, 6, 12, 13, 9, 0xFF6A7A42); m.box(7, 16, 8, 8, 16, 8, BRASS); });
        wood("suit_of_armor", 1, 2, 1, POST, m -> { m.box(4, 0, 5, 11, 1, 10, STONE_C); m.box(5, 2, 6, 6, 12, 8, STEEL); m.box(9, 2, 6, 10, 12, 8, STEEL); m.box(4, 13, 5, 11, 22, 9, STEEL_L); m.box(2, 15, 6, 3, 21, 8, STEEL); m.box(12, 15, 6, 13, 21, 8, STEEL); m.box(6, 23, 5, 9, 27, 9, STEEL_L); m.box(6, 25, 10, 9, 25, 10, BLACK); m.box(7, 28, 6, 8, 28, 9, STEEL_D); m.box(13, 4, 7, 13, 27, 7, OAK_D); m.box(13, 28, 7, 13, 30, 7, STEEL_L); });
        wood("statue_bust", 1, 2, 1, POST, m -> { m.box(4, 0, 4, 11, 1, 11, MARBLE_C); m.box(5, 2, 5, 10, 15, 10, sh(MARBLE_C, 0.92f)); m.box(4, 16, 4, 11, 17, 11, MARBLE_C); m.box(4, 18, 6, 11, 21, 10, WHITE); m.box(6, 22, 6, 9, 23, 9, WHITE); m.box(5, 24, 5, 10, 29, 10, WHITE); m.box(7, 26, 11, 8, 27, 11, sh(WHITE, 0.85f)); });
        wood("statue_figure", 1, 2, 1, POST, m -> { m.box(3, 0, 3, 12, 2, 12, STONE_C); m.box(5, 3, 6, 7, 13, 9, MARBLE_C); m.box(8, 3, 6, 10, 13, 9, MARBLE_C); m.box(5, 14, 5, 10, 22, 10, MARBLE_C); m.box(3, 16, 6, 4, 22, 8, MARBLE_C); m.box(11, 18, 6, 12, 26, 8, MARBLE_C); m.box(6, 23, 6, 9, 27, 9, MARBLE_C); m.box(11, 27, 6, 13, 28, 8, sh(MARBLE_C, 0.9f)); });
        wood("pedestal_urn", 1, 1, 1, POST, m -> { m.box(4, 0, 4, 11, 7, 11, MARBLE_C); m.box(3, 8, 3, 12, 8, 12, MARBLE_C); m.cylY(9, 10, 8, 8, 2, 0xFF2E4A72); m.cylY(11, 13, 8, 8, 3.2f, 0xFF2E4A72); m.cylY(14, 15, 8, 8, 2, 0xFF2E4A72); m.cylY(12, 12, 8, 8, 3.4f, GOLD); });
        wood("coat_stand", 1, 2, 1, POST, m -> { m.cylY(0, 0, 8, 8, 4, WALNUT); m.box(7, 1, 7, 8, 28, 8, WALNUT); m.box(4, 26, 7, 11, 26, 8, WALNUT); m.box(7, 26, 4, 8, 26, 11, WALNUT); m.box(3, 14, 6, 6, 25, 9, 0xFF3A3A4A); m.box(9, 22, 6, 12, 24, 9, 0xFF5A4A32); });
        wood("umbrella_stand", 1, 1, 1, POST, m -> { m.cylY(0, 8, 8, 8, 3.5f, BRASS); m.cylY(1, 8, 8, 8, 2.5f, 0); m.box(7, 2, 7, 7, 14, 7, BLACK); m.box(9, 2, 8, 9, 13, 8, 0xFF2E4A72); m.set(7, 15, 8, OAK_D); });
        wood("billiard_table", 2, 1, 1, LOW, m -> { legs(m, 1, 1, 30, 14, 8, 3, WALNUT); m.box(0, 9, 0, 31, 12, 15, WALNUT); m.box(2, 12, 2, 29, 12, 13, GREEN_D); m.set(8, 13, 6, WHITE); m.set(20, 13, 9, RED); m.set(22, 13, 8, YELLOW); m.set(21, 13, 5, BLACK); m.box(4, 13, 11, 16, 13, 11, OAK_L); });
        wood("gramophone", 1, 1, 1, LOW, m -> { legs(m, 3, 3, 12, 12, 8, 1, OAK_D); m.box(2, 9, 2, 13, 9, 13, OAK); m.box(4, 10, 4, 11, 12, 11, WALNUT); m.cylY(13, 13, 7.5f, 7.5f, 3, BLACK); m.box(10, 13, 10, 10, 15, 10, BRASS); m.box(9, 15, 8, 12, 17, 11, BRASS); m.box(8, 16, 5, 13, 20, 8, BRASS); m.box(7, 17, 2, 14, 22, 4, sh(BRASS, 1.2f)); });
        wood("easel", 1, 2, 1, POST, m -> { m.box(3, 0, 6, 3, 26, 6, PINE); m.box(12, 0, 6, 12, 26, 6, PINE); m.box(7, 0, 12, 8, 24, 12, PINE); m.box(3, 9, 7, 12, 9, 8, PINE); m.box(2, 10, 7, 13, 24, 7, CREAM); m.box(4, 13, 8, 9, 20, 8, 0xFF6A7A8A); m.box(8, 12, 8, 11, 16, 8, 0xFF8A6A4A); });
        wood("music_stand", 1, 1, 1, POST, m -> { m.cylY(0, 0, 8, 8, 3, IRON); m.box(7, 1, 7, 8, 11, 8, IRON); m.box(3, 11, 7, 12, 15, 7, IRON); m.box(4, 12, 8, 11, 15, 8, PAPER); });
        wood("plant_palm", 1, 2, 1, POST, m -> { m.cylY(0, 5, 8, 8, 4, 0xFF8A4A32); m.cylY(5, 5, 8, 8, 4.5f, 0xFF7A3E28); m.box(7, 6, 7, 8, 18, 8, 0xFF5A4A2A); for (int i = 0; i < 6; i++) { double a = i * Math.PI / 3; for (int k = 1; k < 7; k++) m.set((int) Math.round(7.5 + Math.cos(a) * k), 19 + (k < 4 ? k : 6 - k), (int) Math.round(7.5 + Math.sin(a) * k), k % 2 == 0 ? GREEN : GREEN_D); } });
        wood("plant_dead", 1, 1, 1, POST, m -> { m.cylY(0, 5, 8, 8, 3.5f, 0xFF8A4A32); m.box(7, 6, 7, 7, 13, 7, 0xFF5A4A2A); m.box(8, 8, 8, 10, 8, 8, 0xFF5A4A2A); m.box(5, 10, 6, 6, 10, 7, 0xFF6A5A32); m.set(10, 9, 8, 0xFF7A6A3A); m.set(4, 11, 6, 0xFF7A6A3A); m.set(3, 0, 12, 0xFF7A6A3A); m.set(12, 0, 4, 0xFF7A6A3A); });
        wood("screen_folding", 2, 2, 1, FULL, m -> { for (int p = 0; p < 3; p++) { int x0 = 1 + p * 10, z = p == 1 ? 9 : 6; m.box(x0, 1, z, x0 + 9, 28, z, WALNUT); m.box(x0 + 1, 3, z + (p == 1 ? -1 : 1), x0 + 8, 26, z + (p == 1 ? -1 : 1), p == 1 ? 0xFF7A6A52 : 0xFF6A7A62); } });
        // hearths: the firebox is in the wall side (z = 0); the lit one carries its own light
        light("fireplace_lit", 3, 2, 1, FULL, m -> hearth(m, true));
        wood("fireplace_cold", 3, 2, 1, FULL, m -> hearth(m, false));
        wood("firewood", 1, 1, 1, LOW, m -> { for (int y = 0; y < 9; y += 3) for (int x = 1 + (y / 3 % 2); x < 14; x += 4) { m.box(x, y, 3, x + 2, y + 2, 12, 0xFF6A4A2E); m.box(x, y, 12, x + 2, y + 2, 12, 0xFFB8935A); } });
        wood("fire_irons", 1, 1, 1, POST, m -> { m.cylY(0, 0, 8, 8, 3, IRON); m.box(7, 1, 7, 8, 13, 8, IRON); m.box(5, 2, 9, 5, 12, 9, IRON); m.box(10, 2, 9, 10, 12, 9, BRASS); m.box(4, 1, 9, 6, 2, 9, IRON); });

        // ---- on walls (no room taken; the back of the model is the wall)
        deco("painting_portrait", 1, 2, 1, m -> { m.box(2, 4, 0, 13, 27, 0, GOLD); m.box(3, 5, 1, 12, 26, 1, 0xFF2A241E); m.box(5, 16, 1, 10, 22, 1, 0xFFB89878); m.box(4, 6, 1, 11, 15, 1, 0xFF1E1E26); m.box(6, 23, 1, 9, 24, 1, 0xFF3A2A1A); m.set(6, 19, 1, BLACK); m.set(9, 19, 1, BLACK); });
        deco("painting_lady", 1, 2, 1, m -> { m.box(2, 4, 0, 13, 27, 0, WALNUT); m.box(3, 5, 1, 12, 26, 1, 0xFF3A3A2E); m.box(5, 17, 1, 10, 22, 1, 0xFFC8A888); m.box(4, 6, 1, 11, 16, 1, 0xFF6A2A32); m.box(5, 23, 1, 10, 25, 1, 0xFF6A4A2A); m.box(7, 15, 1, 8, 16, 1, 0xFFC8A888); });
        deco("painting_landscape", 2, 1, 1, m -> { m.box(1, 2, 0, 30, 14, 0, GOLD); m.box(2, 3, 1, 29, 13, 1, 0xFF8AA4B8); m.box(2, 3, 1, 29, 6, 1, 0xFF4A6A3A); m.box(2, 7, 1, 12, 8, 1, 0xFF5A7A4A); m.box(18, 7, 1, 29, 9, 1, 0xFF6A7A8A); m.box(22, 10, 1, 26, 10, 1, 0xFF7A8A9A); m.box(6, 6, 1, 7, 9, 1, 0xFF3A2A1A); m.box(5, 9, 1, 8, 10, 1, 0xFF2E4A2A); });
        deco("painting_small", 1, 1, 1, m -> { m.box(4, 4, 0, 11, 12, 0, WALNUT); m.box(5, 5, 1, 10, 11, 1, 0xFFB8A888); m.box(6, 6, 1, 9, 8, 1, 0xFF5A6A4A); m.box(7, 9, 1, 8, 10, 1, 0xFF8A3A2A); });
        // "family photographs": small frames hung in a group, some of them crooked
        deco("photo_frames", 1, 1, 1, m -> { int[][] f = {{1, 9, 5, 14}, {7, 8, 10, 12}, {12, 10, 14, 14}, {2, 2, 6, 6}, {9, 2, 13, 6}}; for (int[] q : f) { m.box(q[0], q[1], 0, q[2], q[3], 0, r.nextBoolean() ? WALNUT : BLACK); m.box(q[0] + 1, q[1] + 1, 1, q[2] - 1, q[3] - 1, 1, 0xFFA8A090); m.set((q[0] + q[2]) / 2, q[1] + 1, 1, 0xFF4A4A4E); } });
        deco("mirror_gilt", 1, 2, 1, m -> { m.box(2, 3, 0, 13, 28, 0, GOLD); m.box(3, 4, 1, 12, 27, 1, 0xFF9AAAB0); m.box(4, 5, 1, 6, 12, 1, 0xFFB4C4C8); m.box(6, 29, 0, 9, 30, 0, GOLD); });
        deco("mirror_broken", 1, 1, 1, m -> { m.box(2, 2, 0, 13, 14, 0, STEEL_D); m.box(3, 3, 1, 12, 13, 1, 0xFF9AAAB0); for (int i = 0; i < 9; i++) m.set(4 + i, 4 + i, 1, CHARCOAL); m.box(7, 8, 1, 11, 8, 1, CHARCOAL); m.box(5, 10, 1, 5, 13, 1, CHARCOAL); });
        deco("trophy_head", 1, 1, 1, m -> { m.box(4, 2, 0, 11, 11, 0, WALNUT); m.box(5, 4, 1, 10, 9, 5, 0xFF7A5A3A); m.box(6, 3, 6, 9, 7, 9, 0xFF7A5A3A); m.box(6, 3, 10, 9, 5, 10, BLACK); m.box(3, 10, 2, 4, 14, 3, BONE); m.box(11, 10, 2, 12, 14, 3, BONE); m.set(2, 14, 3, BONE); m.set(13, 14, 3, BONE); });
        deco("clock_wall", 1, 1, 1, m -> { m.cylX(0, 0, 8, 8, 1, 0); for (int x = 3; x <= 12; x++) for (int y = 3; y <= 12; y++) if (Math.hypot(x - 7.5, y - 7.5) < 5.2) m.set(x, y, 0, Math.hypot(x - 7.5, y - 7.5) > 4.2 ? BLACK : CREAM); m.set(7, 8, 1, BLACK); m.set(7, 9, 1, BLACK); m.set(7, 10, 1, BLACK); m.set(8, 7, 1, BLACK); m.set(9, 6, 1, BLACK); });
        // a hanging: a dark field in a gold border, a shield quartered on it, a fringe along the foot
        deco("tapestry", 2, 2, 1, m -> { m.box(1, 30, 0, 30, 30, 1, BRASS); m.box(2, 4, 0, 29, 29, 0, 0xFF3A1A22); m.box(3, 5, 1, 28, 28, 1, GOLD); m.box(4, 6, 1, 27, 27, 1, 0xFF4E1A26);
            m.box(11, 11, 1, 20, 23, 1, 0xFF2E3A52); m.box(11, 17, 1, 15, 23, 1, 0xFF8A7A3A); m.box(16, 11, 1, 20, 16, 1, 0xFF8A7A3A); m.box(12, 9, 1, 19, 10, 1, 0xFF2E3A52); m.box(14, 8, 1, 17, 8, 1, 0xFF2E3A52);
            for (int x = 6; x < 26; x += 4) { m.set(x, 7, 1, GOLD); m.set(x, 26, 1, GOLD); } for (int x = 2; x < 30; x += 2) m.set(x, 3, 0, GOLD); });
        // 1.0: a runner laid up a stair: it lies on the four treads of the stair cell it is put in (turned the way the stair rises)
        deco("stair_carpet", 1, 2, 1, m -> { for (int k = 0; k < 4; k++) { m.box(0, 4 * k + 4, 4 * k, 15, 4 * k + 4, 4 * k + 3, WINE); m.box(0, 4 * k + 4, 4 * k + 3, 15, 4 * k + 4, 4 * k + 3, sh(WINE, 1.25f)); if (k > 0) { m.box(0, 4 * k + 1, 4 * k - 1, 15, 4 * k + 3, 4 * k - 1, sh(WINE, 0.8f)); } } });
        deco("meat_hooks", 2, 1, 1, m -> { m.box(0, 14, 7, 31, 15, 8, IRON); for (int x = 4; x < 30; x += 6) { m.box(x, 9, 7, x, 13, 7, STEEL_L); m.set(x + 1, 9, 7, STEEL_L); m.set(x + 1, 10, 7, STEEL_L); } m.box(9, 1, 6, 12, 8, 9, 0xFF7A2A22); m.box(10, 2, 6, 11, 5, 9, 0xFFB8A090); m.box(21, 3, 6, 23, 8, 9, 0xFF6A1A16); });
        wood("dresser_plates", 2, 2, 1, FULL, m -> { m.box(0, 0, 0, 31, 12, 9, PINE); m.box(0, 13, 0, 31, 13, 10, OAK_D); for (int x = 1; x < 30; x += 10) { m.box(x, 2, 10, x + 8, 11, 10, OAK_L); m.set(x + 4, 6, 11, BRASS); } m.box(0, 14, 0, 31, 30, 1, PINE); for (int y = 14; y < 30; y += 5) { m.box(0, y + 4, 0, 31, y + 4, 6, OAK_D); for (int x = 2; x < 30; x += 4) if (r.nextInt(5) != 0) m.cylX(x, x, y + 2, 2.5f, 1.8f, r.nextInt(4) == 0 ? 0xFF8AA0B8 : PORCELAIN); } m.box(0, 31, 0, 31, 31, 7, OAK_D); });
        wood("table_work", 3, 1, 1, LOW, m -> { legs(m, 1, 2, 46, 13, 11, 2, OAK_D); m.box(0, 12, 1, 47, 14, 14, OAK_L); m.box(2, 5, 3, 45, 5, 12, OAK_D); m.cylY(15, 18, 8, 7, 3, COPPER); m.box(16, 15, 4, 24, 15, 10, PINE); m.box(18, 16, 6, 21, 16, 8, 0xFF8A9A4A); m.box(30, 15, 5, 30, 15, 12, STEEL_L); m.cylY(15, 16, 38, 8, 3.5f, ENAMEL); m.cylY(6, 9, 10, 7, 3, 0xFF8A7458); m.box(30, 6, 5, 40, 8, 10, 0xFF9A7A4A); });
        deco("curtain_tall", 1, 3, 1, m -> { m.box(0, 46, 0, 15, 46, 1, BRASS); for (int x = 0; x < 16; x++) m.box(x, 1 + (x % 3 == 0 ? 1 : 0), x % 2, x, 45, x % 2, x % 4 < 2 ? VELVET : WINE); m.box(0, 14, 2, 15, 15, 2, GOLD); });
        deco("beam_ceiling", 1, 1, 3, m -> { m.box(4, 11, 0, 11, 15, 47, WALNUT); m.box(3, 14, 0, 12, 15, 47, OAK_D); });
        // the upper half of a control room's wall: annunciator windows in rows, a few of them still lit
        deco("panel_annunciator", 1, 2, 1, m -> { m.box(0, 0, 0, 15, 27, 4, 0xFF7A8880); m.box(1, 1, 5, 14, 26, 5, 0xFF2A322E); for (int x = 2; x < 14; x += 3) for (int y = 3; y < 25; y += 3) { int k = r.nextInt(14); m.box(x, y, 6, x + 1, y + 1, 6, k == 0 ? LED_A : k == 1 ? LED_R : k == 2 ? (DIM | 0xE8E0C0) : 0xFF8A8A7A); } m.box(0, 28, 0, 15, 29, 6, 0xFF58665E); });
        // 1.1: the red lamp - a warning light in a wire cage on a short stalk. It was a block of solid red light.
        light("beacon_red", 1, 1, 1, NONE, m -> { m.box(6, 0, 6, 9, 1, 9, IRON); m.box(7, 2, 7, 8, 4, 8, STEEL_D); m.box(5, 5, 5, 10, 10, 10, LED_R); m.box(6, 11, 6, 9, 11, 9, LED_R); for (int[] c : new int[][]{{4, 4}, {11, 4}, {4, 11}, {11, 11}}) m.box(c[0], 4, c[1], c[0], 12, c[1], IRON); m.box(4, 12, 4, 11, 12, 11, IRON); m.box(5, 12, 5, 10, 12, 10, 0); m.box(4, 4, 4, 11, 4, 11, IRON); m.box(5, 4, 5, 10, 4, 10, STEEL_D); });
        light("lamp_ceiling_lit", 1, 1, 1, NONE, m -> { m.box(4, 14, 4, 11, 15, 11, 0xFF8A8E88); m.box(5, 11, 5, 10, 13, 10, LAMP_WARM); });
        deco("curtain_red", 1, 2, 1, m -> { m.box(0, 30, 0, 15, 30, 1, BRASS); for (int x = 0; x < 16; x++) m.box(x, 1 + (x % 3 == 0 ? 1 : 0), x % 2, x, 29, x % 2, x % 4 < 2 ? VELVET : WINE); });
        deco("curtain_torn", 1, 2, 1, m -> { m.box(0, 30, 0, 15, 30, 1, IRON); for (int x = 0; x < 16; x++) m.box(x, 6 + (x * 7 % 11), x % 2, x, 29, x % 2, x % 4 < 2 ? 0xFF8A8672 : 0xFF74705E); });
        deco("shelf_wall", 1, 1, 1, m -> { m.box(0, 7, 0, 15, 7, 5, OAK_D); m.set(1, 5, 1, IRON); m.set(14, 5, 1, IRON); m.set(1, 6, 1, IRON); m.set(14, 6, 1, IRON); books(m, r, 1, 8, 8, 5, 1, 4); m.cylY(8, 10, 12, 3, 1.6f, PORCELAIN); });
        deco("coat_hooks", 1, 1, 1, m -> { m.box(1, 11, 0, 14, 12, 0, OAK_D); for (int x = 2; x < 14; x += 4) m.box(x, 10, 1, x, 11, 2, BRASS); m.box(2, 2, 1, 4, 10, 2, 0xFF4A4A3A); m.box(10, 5, 1, 11, 10, 2, 0xFF2E3A4A); });
        deco("notice_board", 2, 1, 1, m -> { m.box(1, 2, 0, 30, 14, 0, OAK_D); m.box(2, 3, 1, 29, 13, 1, 0xFF9A7A4E); int[] cs = {PAPER, 0xFFD8C890, 0xFFB8C8D0, PAPER, 0xFFD0B0A0}; for (int i = 0; i < 9; i++) { int x = 3 + r.nextInt(22), y = 4 + r.nextInt(6); m.box(x, y, 2, x + 2 + r.nextInt(3), y + 2 + r.nextInt(2), 2, cs[r.nextInt(cs.length)]); m.set(x + 1, y + 3, 2, RED); } });
        // the missing: a wall of handbills (lore: the civilian inquiry form, people looking for people)
        deco("missing_posters", 2, 2, 1, m -> { for (int i = 0; i < 14; i++) { int x = 1 + r.nextInt(25), y = 3 + r.nextInt(22); m.box(x, y, 0, x + 4, y + 6, 0, r.nextInt(4) == 0 ? 0xFFC8C0A0 : PAPER); m.box(x + 1, y + 3, 1, x + 3, y + 5, 1, 0xFF6A6A66); m.box(x + 1, y + 1, 1, x + 3, y + 1, 1, 0xFF3A3A3A); } });
        deco("calendar", 1, 1, 1, m -> { m.box(4, 3, 0, 11, 13, 0, PAPER); m.box(4, 10, 1, 11, 13, 1, RED); for (int x = 5; x < 11; x += 2) for (int y = 4; y < 9; y += 2) m.set(x, y, 1, 0xFF4A4A4A); m.set(7, 6, 1, RED); });
        deco("map_wall", 2, 1, 1, m -> { m.box(1, 1, 0, 30, 14, 0, 0xFFB8AC88); m.box(3, 3, 1, 12, 8, 1, 0xFF8A9A6A); m.box(14, 5, 1, 27, 12, 1, 0xFFA8A078); m.box(2, 7, 1, 29, 7, 1, 0xFF6A8AA8); m.box(16, 2, 1, 16, 13, 1, 0xFF5A5A5A); m.set(9, 6, 1, RED); m.set(21, 9, 1, RED); m.set(24, 4, 1, RED); });
        deco("blackboard", 2, 1, 1, m -> { m.box(0, 1, 0, 31, 15, 0, OAK_D); m.box(1, 2, 1, 30, 14, 1, 0xFF24322A); for (int i = 0; i < 6; i++) { int x = 3 + r.nextInt(18), y = 4 + r.nextInt(8); m.box(x, y, 2, x + 3 + r.nextInt(6), y, 2, 0xFFC8CCC4); } m.box(1, 1, 1, 30, 1, 2, OAK_D); });
        deco("cross_wall", 1, 1, 1, m -> { m.box(7, 2, 0, 8, 14, 0, WALNUT); m.box(4, 10, 0, 11, 11, 0, WALNUT); });
        deco("radiator", 1, 1, 1, m -> { for (int x = 1; x < 15; x += 2) m.box(x, 2, 0, x, 11, 2, ENAMEL); m.box(1, 3, 1, 14, 3, 1, ENAMEL); m.box(1, 10, 1, 14, 10, 1, ENAMEL); m.box(15, 3, 1, 15, 4, 1, COPPER); m.box(0, 0, 1, 0, 3, 1, COPPER); });
        deco("pipes_wall", 1, 2, 1, m -> { m.box(3, 0, 0, 4, 31, 1, COPPER); m.box(8, 0, 0, 10, 31, 2, STEEL_D); m.box(13, 0, 0, 13, 31, 0, RUST); m.box(7, 12, 0, 11, 13, 3, STEEL); m.box(2, 22, 0, 5, 23, 2, BRASS); });
        deco("valve_wheel", 1, 1, 1, m -> { m.box(6, 0, 0, 9, 15, 2, STEEL_D); for (int x = 3; x <= 12; x++) for (int y = 3; y <= 12; y++) { double d = Math.hypot(x - 7.5, y - 7.5); if (d < 5 && d > 3.4) m.set(x, y, 4, RED); } m.box(7, 4, 4, 8, 11, 4, RED); m.box(4, 7, 4, 11, 8, 4, RED); m.box(7, 7, 3, 8, 8, 3, STEEL_D); });
        deco("gauges", 1, 1, 1, m -> { m.box(1, 3, 0, 14, 13, 1, 0xFF5A6A62); for (int[] c : new int[][]{{4, 9}, {11, 9}, {4, 5}, {11, 5}}) { m.box(c[0] - 2, c[1] - 1, 2, c[0] + 2, c[1] + 2, 2, CREAM); m.set(c[0], c[1], 3, BLACK); m.set(c[0] + (c[0] < 8 ? 1 : -1), c[1] + 1, 3, RED); } });
        deco("fuse_box", 1, 1, 1, m -> { m.box(3, 2, 0, 12, 14, 2, GREY_D); m.box(4, 3, 3, 11, 13, 3, GREY); for (int y = 4; y < 13; y += 2) m.box(5, y, 4, 10, y, 4, BLACK); m.set(12, 8, 3, STEEL_L); m.box(7, 0, 0, 8, 1, 1, BLACK); });
        deco("fire_extinguisher", 1, 1, 1, m -> { m.box(6, 9, 0, 9, 10, 0, STEEL_D); m.cylY(1, 9, 8, 2.5f, 2.2f, RED); m.box(7, 10, 2, 8, 11, 2, BLACK); m.box(8, 11, 2, 10, 11, 3, BLACK); m.box(6, 5, 4, 9, 6, 4, CREAM); });
        deco("first_aid_box", 1, 1, 1, m -> { m.box(4, 5, 0, 11, 12, 2, WHITE); m.box(7, 6, 3, 8, 11, 3, RED); m.box(5, 8, 3, 10, 9, 3, RED); });
        deco("sign_radiation", 1, 1, 1, m -> { m.box(3, 3, 0, 12, 12, 0, HAZARD); m.box(7, 7, 1, 8, 8, 1, BLACK); m.box(7, 10, 1, 8, 11, 1, BLACK); m.box(4, 5, 1, 5, 6, 1, BLACK); m.box(10, 5, 1, 11, 6, 1, BLACK); m.box(6, 9, 1, 9, 9, 1, BLACK); });
        deco("sign_exit", 1, 1, 1, m -> { m.box(2, 9, 0, 13, 13, 1, 0xFF1E3A2A); m.box(3, 10, 2, 12, 12, 2, DIM | 0x50D070); });
        deco("dosimeter_post", 1, 2, 1, m -> { m.box(4, 12, 0, 11, 26, 2, 0xFF6A7A72); m.box(5, 19, 3, 10, 24, 3, CRT); m.box(6, 20, 3, 9, 22, 3, SCREEN_G); m.set(6, 15, 3, LED_R); m.set(9, 15, 3, LED_G); m.box(7, 4, 0, 8, 11, 1, BLACK); m.box(5, 2, 1, 10, 4, 3, HAZARD); });
        deco("phone_wall", 1, 1, 1, m -> { m.box(5, 3, 0, 10, 12, 2, 0xFF2A2A2E); m.box(4, 10, 3, 11, 12, 4, BLACK); for (int y = 4; y < 9; y += 2) for (int x = 6; x < 10; x += 2) m.set(x, y, 3, GREY); m.box(7, 0, 1, 7, 2, 1, BLACK); });
        deco("intercom", 1, 1, 1, m -> { m.box(5, 5, 0, 10, 11, 1, STEEL); for (int y = 8; y < 11; y++) m.box(6, y, 2, 9, y, 2, y % 2 == 0 ? BLACK : STEEL_D); m.set(7, 6, 2, LED_R); });
        deco("vent_grille", 1, 1, 1, m -> { m.box(2, 8, 0, 13, 14, 0, STEEL_D); for (int y = 9; y < 14; y += 2) m.box(3, y, 1, 12, y, 1, STEEL); });
        deco("xray_lightbox", 2, 1, 1, m -> { m.box(1, 2, 0, 30, 14, 1, STEEL_D); m.box(2, 3, 2, 29, 13, 2, DIM | 0xC8D8E0); m.box(4, 4, 3, 13, 12, 3, 0xFF1A2228); m.box(7, 5, 3, 10, 11, 3, 0xFF8A9AA0); m.box(18, 4, 3, 27, 12, 3, 0xFF1A2228); m.box(21, 7, 3, 24, 11, 3, 0xFF8A9AA0); });
        light("sconce", 1, 1, 1, NONE, m -> { m.box(7, 4, 0, 8, 8, 1, BRASS); m.box(6, 8, 2, 9, 8, 4, BRASS); m.box(7, 9, 3, 8, 12, 3, CREAM); m.set(7, 13, 3, FLAME); m.set(8, 13, 3, FLAME); });
        light("lantern_wall", 1, 1, 1, NONE, m -> { m.box(7, 12, 0, 8, 13, 3, IRON); m.box(6, 5, 2, 9, 11, 5, IRON); m.box(7, 6, 3, 8, 10, 4, FLAME); });
        deco("lamp_wall_off", 1, 1, 1, m -> { m.box(7, 6, 0, 8, 9, 1, BRASS); m.box(5, 9, 1, 10, 13, 5, 0xFF8A8474); m.box(6, 10, 2, 9, 12, 4, 0xFF6E6A5E); });

        // ---- from ceilings (the top of the model is the ceiling)
        light("chandelier", 2, 2, 2, NONE, m -> { m.box(15, 20, 15, 16, 31, 16, BRASS); m.cylY(17, 19, 16, 16, 3, BRASS); for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; int x = (int) Math.round(15.5 + Math.cos(a) * 11), z = (int) Math.round(15.5 + Math.sin(a) * 11);
                for (int k = 3; k < 11; k++) m.set((int) Math.round(15.5 + Math.cos(a) * k), 16 - (k > 6 ? 0 : (6 - k) / 2), (int) Math.round(15.5 + Math.sin(a) * k), BRASS);
                m.box(x, 16, z, x, 19, z, CREAM); m.set(x, 20, z, FLAME); m.set(x, 14, z, GLASS_C); m.set(x, 13, z, GLASS_C); }
            m.cylY(12, 13, 16, 16, 2, GLASS_C); m.set(15, 10, 15, GLASS_C); m.set(16, 11, 16, GLASS_C); });
        // 1.3: the chain a chandelier hangs by, one block of it, stacked from the ceiling down to the chandelier's crown. Links of
        // wrought iron, alternately across and along, about the middle of a two-by-two footprint (the chandelier's own).
        deco("chain_chandelier", 2, 1, 2, m -> { for (int k = 0; k < 4; k++) { int y = k * 4; if (k % 2 == 0) { m.box(14, y, 15, 14, y + 3, 16, IRON); m.box(17, y, 15, 17, y + 3, 16, IRON); m.box(15, y, 15, 16, y, 16, IRON); m.box(15, y + 3, 15, 16, y + 3, 16, IRON); } else { m.box(15, y, 14, 16, y + 3, 14, IRON); m.box(15, y, 17, 16, y + 3, 17, IRON); m.box(15, y, 15, 16, y, 16, IRON); m.box(15, y + 3, 15, 16, y + 3, 16, IRON); } } });
        light("chandelier_small", 1, 1, 1, NONE, m -> { m.box(7, 9, 7, 8, 15, 8, IRON); for (int[] c : new int[][]{{2, 7}, {13, 7}, {7, 2}, {7, 13}}) { m.box(Math.min(c[0], 7), 8, Math.min(c[1], 7), Math.max(c[0], 8), 8, Math.max(c[1], 8), IRON); m.box(c[0], 9, c[1], c[0], 11, c[1], CREAM); m.set(c[0], 12, c[1], FLAME); } });
        deco("lamp_hanging_off", 1, 1, 1, m -> { m.box(7, 8, 7, 8, 15, 8, BLACK); m.cylY(5, 7, 8, 8, 5, 0xFF3E5A4A); m.cylY(4, 4, 8, 8, 6, 0xFF3E5A4A); m.cylY(5, 6, 8, 8, 3.5f, 0); m.box(7, 5, 7, 8, 6, 8, 0xFF8A8A7A); });
        deco("fluorescent_dead", 2, 1, 1, m -> { m.box(2, 14, 5, 29, 15, 10, 0xFF8A8E88); m.box(3, 13, 6, 28, 13, 7, 0xFF9A9E96); m.box(3, 13, 9, 28, 13, 9, 0xFF6E726C); m.box(20, 9, 9, 20, 12, 9, BLACK); });
        deco("fan_ceiling", 2, 1, 2, m -> { m.box(15, 11, 15, 16, 15, 16, BRASS); m.cylY(9, 10, 16, 16, 3, WALNUT); m.box(2, 10, 14, 13, 10, 17, OAK_D); m.box(18, 10, 14, 29, 10, 17, OAK_D); m.box(14, 10, 2, 17, 10, 13, OAK_D); m.box(14, 10, 18, 17, 10, 29, OAK_D); });
        deco("pots_hanging", 2, 1, 1, m -> { m.box(1, 14, 7, 30, 14, 8, IRON); m.box(1, 15, 7, 1, 15, 8, IRON); m.box(30, 15, 7, 30, 15, 8, IRON); int[] cs = {COPPER, STEEL, COPPER, IRON, COPPER}; for (int i = 0; i < 5; i++) { int x = 3 + i * 6; m.set(x + 1, 13, 7, IRON); m.cylY(8 + (i % 2), 12, x + 1.5f, 8, 2.4f, cs[i]); m.box(x + 4, 11, 7, x + 5, 11, 7, cs[i]); } });
        deco("duct", 2, 1, 1, m -> { m.box(0, 8, 3, 31, 15, 12, 0xFF7A8088); m.box(7, 7, 2, 8, 15, 13, STEEL_D); m.box(23, 7, 2, 24, 15, 13, STEEL_D); m.box(13, 7, 5, 18, 7, 10, CHARCOAL); });
        deco("cable_tray", 2, 1, 1, m -> { m.box(0, 12, 4, 31, 12, 11, STEEL_D); m.box(0, 13, 4, 31, 14, 4, STEEL_D); m.box(0, 13, 11, 31, 14, 11, STEEL_D); m.box(0, 13, 6, 31, 13, 6, BLACK); m.box(0, 13, 8, 31, 13, 8, RED_D); m.box(0, 13, 9, 31, 13, 9, BLUE_D); m.box(15, 15, 7, 16, 15, 8, STEEL_D); });
        deco("pipes_ceiling", 2, 1, 1, m -> { m.box(0, 11, 3, 31, 13, 5, 0xFF8A4A2A); m.box(0, 10, 8, 31, 13, 11, STEEL); m.box(0, 12, 13, 31, 13, 14, HAZARD); m.box(10, 10, 2, 11, 14, 6, STEEL_D); m.box(22, 9, 7, 23, 14, 12, STEEL_D); m.box(5, 14, 4, 5, 15, 4, IRON); m.box(26, 14, 9, 26, 15, 9, IRON); });
        deco("hook_crane", 1, 2, 1, m -> { m.box(7, 12, 7, 8, 31, 8, IRON); m.box(5, 8, 6, 10, 11, 9, HAZARD); m.box(7, 3, 7, 8, 7, 8, IRON); m.box(5, 2, 7, 8, 2, 8, IRON); m.box(5, 3, 7, 5, 5, 8, IRON); });
        deco("cobwebs", 1, 1, 1, m -> { for (int i = 0; i < 12; i++) { m.set(i, 15 - i / 2, 0, 0xFFB0B0AC); m.set(0, 15 - i / 2, i, 0xFFB0B0AC); if (i < 8) m.set(i, 15, i, 0xFFA0A09C); } m.box(0, 13, 0, 3, 15, 3, 0xFF9A9A96); });

        // ---- on floors (flat; nothing is in the way of a foot)
        deco("rug_persian", 3, 1, 2, m -> { m.box(1, 0, 1, 46, 0, 30, WINE); m.box(3, 0, 3, 44, 0, 28, 0xFF7A2A2A); m.box(5, 0, 5, 42, 0, 26, 0xFF2E3A52); m.box(7, 0, 7, 40, 0, 24, 0xFF7A2A2A); for (int x = 9; x < 40; x += 6) for (int z = 9; z < 23; z += 6) { m.box(x, 0, z, x + 2, 0, z + 2, GOLD); m.set(x + 1, 0, z + 1, 0xFF2E3A52); } m.box(20, 0, 13, 27, 0, 18, GOLD); m.box(22, 0, 15, 25, 0, 16, WINE); });
        deco("rug_runner", 1, 1, 3, m -> { m.box(2, 0, 0, 13, 0, 47, WINE); m.box(3, 0, 0, 3, 0, 47, GOLD); m.box(12, 0, 0, 12, 0, 47, GOLD); for (int z = 3; z < 46; z += 5) { m.box(6, 0, z, 9, 0, z + 1, GOLD); } });
        deco("rug_round", 2, 1, 2, m -> { m.cylY(0, 0, 16, 16, 14, 0xFF4A5A4A); m.cylY(0, 0, 16, 16, 11, 0xFF8A7A5A); m.cylY(0, 0, 16, 16, 7, 0xFF4A5A4A); m.cylY(0, 0, 16, 16, 3, 0xFF8A3A2A); });
        deco("rug_bear", 2, 1, 2, m -> { m.box(8, 0, 6, 23, 0, 24, 0xFF4A3424); m.box(4, 0, 4, 9, 0, 9, 0xFF4A3424); m.box(22, 0, 4, 27, 0, 9, 0xFF4A3424); m.box(4, 0, 21, 9, 0, 26, 0xFF4A3424); m.box(22, 0, 21, 27, 0, 26, 0xFF4A3424); m.box(12, 0, 25, 19, 3, 30, 0xFF5A4030); m.set(13, 3, 30, BLACK); m.set(18, 3, 30, BLACK); m.box(15, 1, 31, 16, 1, 31, BLACK); });
        deco("papers_floor", 1, 1, 1, m -> { for (int i = 0; i < 7; i++) { int x = r.nextInt(12), z = r.nextInt(12); m.box(x, 0, z, x + 2 + r.nextInt(2), 0, z + 3, i % 3 == 0 ? 0xFFC8C0A0 : PAPER); } });
        deco("rubble_small", 1, 1, 1, m -> { for (int i = 0; i < 9; i++) { int x = r.nextInt(13), z = r.nextInt(13), s = 1 + r.nextInt(2); m.box(x, 0, z, x + s, r.nextInt(2), z + s, r.nextBoolean() ? 0xFF8A8478 : 0xFF6E6A60); } });
        deco("glass_shards", 1, 1, 1, m -> { for (int i = 0; i < 12; i++) m.set(r.nextInt(16), 0, r.nextInt(16), i % 3 == 0 ? 0xFFB4C8CC : GLASS_C); });
        deco("blood_old", 1, 1, 1, m -> { m.cylY(0, 0, 8, 8, 5, 0xFF3A1210); m.box(9, 0, 11, 14, 0, 12, 0xFF3A1210); m.box(13, 0, 12, 15, 0, 15, 0xFF2E0E0C); m.set(3, 0, 13, 0xFF3A1210); });
        deco("bedroll", 1, 1, 2, m -> { m.box(2, 0, 2, 13, 1, 27, OLIVE); m.box(2, 2, 2, 13, 2, 16, 0xFF4A5030); m.box(4, 2, 22, 11, 3, 27, 0xFF8A8672); m.cylX(2, 13, 2.5f, 30, 2.2f, 0xFF4A5030); });
        deco("candles_floor", 1, 1, 1, m -> { for (int[] c : new int[][]{{4, 5, 3}, {9, 4, 5}, {7, 10, 2}, {11, 9, 4}, {3, 11, 1}}) { m.box(c[0], 0, c[1], c[0], c[2], c[1], CREAM); m.set(c[0], c[2] + 1, c[1], FLAME); } m.box(2, 0, 3, 12, 0, 12, 0xFFB8AC90); });

        // ================================================================== kitchens, baths, service
        metal("range_cast", 2, 1, 1, LOW, m -> { m.box(0, 0, 0, 31, 12, 13, IRON); m.box(0, 13, 0, 31, 13, 14, 0xFF2A2C30); for (int x : new int[]{4, 12, 20, 27}) m.cylY(14, 14, x, 7, 2.6f, BLACK); m.box(2, 3, 14, 13, 10, 14, CHARCOAL); m.box(4, 8, 15, 11, 8, 15, BRASS); m.box(17, 3, 14, 29, 10, 14, CHARCOAL); m.box(19, 8, 15, 27, 8, 15, BRASS); m.box(1, 14, 0, 30, 20, 1, IRON); m.cylY(14, 17, 20, 7, 2.5f, COPPER); });
        metal("stove_enamel", 1, 1, 1, LOW, m -> { m.box(0, 0, 0, 15, 13, 13, ENAMEL); m.box(0, 14, 0, 15, 14, 14, 0xFF2A2C30); m.cylY(15, 15, 4, 4, 2, BLACK); m.cylY(15, 15, 11, 4, 2, BLACK); m.cylY(15, 15, 4, 10, 2, BLACK); m.cylY(15, 15, 11, 10, 2, BLACK); m.box(2, 3, 14, 13, 10, 14, 0xFF2A2C30); m.box(3, 11, 15, 12, 11, 15, STEEL_L); });
        metal("fridge_old", 1, 2, 1, FULL, m -> { m.box(1, 0, 1, 14, 27, 12, ENAMEL); m.box(1, 0, 13, 14, 17, 13, 0xFFD0D4CC); m.box(1, 19, 13, 14, 27, 13, 0xFFD0D4CC); m.box(12, 8, 14, 12, 15, 14, STEEL_L); m.box(12, 20, 14, 12, 25, 14, STEEL_L); m.box(3, 21, 14, 6, 24, 14, PAPER); m.set(4, 25, 14, RED); });
        metal("sink_kitchen", 1, 1, 1, LOW, m -> { m.box(0, 0, 0, 15, 12, 13, 0xFF7A8474); m.box(0, 13, 0, 15, 14, 14, STEEL_L); m.box(2, 12, 3, 13, 14, 11, 0); m.box(2, 11, 3, 13, 11, 11, STEEL); m.box(7, 15, 1, 8, 19, 1, STEEL_L); m.box(7, 19, 1, 8, 19, 5, STEEL_L); m.box(1, 2, 14, 7, 11, 14, 0xFF8A9484); m.box(8, 2, 14, 14, 11, 14, 0xFF8A9484); });
        wood("counter_kitchen", 1, 1, 1, LOW, m -> { m.box(0, 0, 0, 15, 12, 13, 0xFF7A8474); m.box(0, 13, 0, 15, 14, 14, 0xFF5A4A38); m.box(1, 2, 14, 14, 6, 14, 0xFF8A9484); m.box(1, 8, 14, 14, 11, 14, 0xFF8A9484); m.set(7, 4, 15, STEEL_L); m.set(7, 9, 15, STEEL_L); if (r.nextBoolean()) m.cylY(15, 17, 5, 6, 2.2f, ENAMEL); else m.box(9, 15, 4, 13, 15, 9, OAK_L); });
        wood("counter_shop", 2, 1, 1, LOW, m -> { m.box(0, 0, 2, 31, 13, 13, 0xFF6A5A42); m.box(0, 14, 1, 31, 15, 14, OAK); m.box(2, 3, 14, 29, 11, 14, 0xFF7A6A4E); m.box(22, 16, 5, 28, 21, 11, GREY_D); m.box(23, 22, 6, 27, 23, 9, GREY); m.box(23, 17, 12, 27, 18, 12, STEEL_L); m.box(5, 16, 6, 9, 16, 9, PAPER); });
        wood("butcher_block", 1, 1, 1, LOW, m -> { legs(m, 2, 2, 13, 13, 9, 2, OAK_D); m.box(1, 10, 1, 14, 13, 14, OAK_L); m.box(3, 14, 6, 3, 14, 12, STEEL_L); m.box(3, 14, 4, 3, 14, 5, WALNUT); m.box(8, 14, 5, 12, 14, 9, 0xFF6A1A16); });
        metal("bathtub", 2, 1, 1, LOW, m -> { m.box(1, 3, 1, 30, 10, 14, PORCELAIN); m.box(3, 5, 3, 28, 10, 12, 0); m.box(3, 4, 3, 28, 4, 12, 0xFFC8C6BC); legs(m, 3, 2, 28, 13, 2, 2, BRASS); m.box(28, 11, 7, 29, 14, 8, BRASS); m.box(26, 14, 7, 28, 14, 8, BRASS); });
        metal("bathtub_filthy", 2, 1, 1, LOW, m -> { m.box(1, 3, 1, 30, 10, 14, 0xFFB8B4A0); m.box(3, 5, 3, 28, 10, 12, 0); m.box(3, 4, 3, 28, 7, 12, 0xFF3A3A26); legs(m, 3, 2, 28, 13, 2, 2, RUST); m.box(28, 11, 7, 29, 14, 8, RUST); });
        metal("washbasin", 1, 1, 1, LOW, m -> { m.box(6, 0, 1, 9, 9, 4, PORCELAIN); m.box(2, 10, 0, 13, 13, 9, PORCELAIN); m.box(4, 12, 2, 11, 13, 7, 0); m.box(7, 14, 0, 8, 16, 1, STEEL_L); });
        metal("toilet_p", 1, 1, 1, LOW, m -> { m.box(4, 0, 4, 11, 6, 12, PORCELAIN); m.box(3, 7, 3, 12, 8, 13, PORCELAIN); m.box(5, 7, 5, 10, 8, 11, 0); m.box(3, 7, 0, 12, 15, 2, PORCELAIN); m.set(12, 13, 3, STEEL_L); });
        metal("shower_decon", 1, 2, 1, POST, m -> { m.box(1, 0, 1, 14, 0, 14, STEEL_D); m.box(1, 0, 1, 1, 30, 1, STEEL); m.box(14, 0, 1, 14, 30, 1, STEEL); m.box(1, 30, 1, 14, 30, 8, STEEL); m.box(7, 27, 5, 8, 29, 6, STEEL_L); m.cylY(26, 26, 8, 6, 2.2f, STEEL_L); m.box(2, 14, 1, 13, 20, 1, HAZARD); m.box(12, 10, 2, 13, 14, 2, RED); });
        metal("washing_tub", 1, 1, 1, LOW, m -> { m.cylY(0, 10, 8, 8, 6.5f, 0xFF7A8088); m.cylY(2, 10, 8, 8, 5.5f, 0); m.cylY(2, 5, 8, 8, 5.5f, 0xFF5A6A6A); m.box(2, 9, 7, 2, 12, 8, STEEL_D); m.box(1, 12, 3, 3, 12, 12, OAK_L); });
        metal("boiler", 1, 2, 1, FULL, m -> { m.cylY(2, 27, 8, 8, 6, 0xFF6A5A48); m.cylY(0, 1, 8, 8, 5, IRON); m.cylY(28, 29, 8, 8, 5, IRON); m.cylY(10, 10, 8, 8, 6.4f, IRON); m.cylY(20, 20, 8, 8, 6.4f, IRON); m.box(6, 4, 14, 9, 8, 15, IRON); m.box(7, 5, 15, 8, 7, 15, EMBER_C); m.box(11, 16, 13, 13, 18, 14, CREAM); m.box(3, 30, 7, 4, 31, 8, COPPER); });
        metal("water_heater", 1, 2, 1, FULL, m -> { m.cylY(4, 28, 8, 8, 5.5f, ENAMEL); legs(m, 4, 4, 11, 11, 3, 1, STEEL_D); m.box(7, 29, 7, 8, 31, 8, COPPER); m.box(12, 8, 7, 14, 8, 8, COPPER); m.box(14, 0, 7, 14, 8, 8, COPPER); m.box(6, 12, 13, 9, 14, 13, RED); });
        metal("mop_bucket", 1, 1, 1, POST, m -> { m.cylY(0, 5, 6, 9, 3.5f, HAZARD); m.cylY(1, 5, 6, 9, 2.6f, 0xFF3A4A4A); m.box(10, 0, 5, 10, 15, 5, PINE); m.box(8, 0, 4, 12, 1, 7, 0xFF8A8A7A); });

        // ================================================================== offices, records, communications
        metal("typewriter_desk", 1, 1, 1, LOW, m -> { legs(m, 1, 2, 14, 13, 10, 1, STEEL_D); m.box(0, 11, 1, 15, 12, 14, 0xFF7A6A52); m.box(4, 13, 5, 11, 15, 11, CHARCOAL); m.box(4, 16, 5, 11, 16, 6, CHARCOAL); m.box(5, 16, 7, 10, 19, 7, PAPER); for (int x = 5; x < 11; x += 2) m.set(x, 14, 12, CREAM); });
        metal("phone_desk", 1, 1, 1, LOW, m -> { legs(m, 3, 3, 12, 12, 9, 1, OAK_D); m.box(2, 10, 2, 13, 11, 13, OAK); m.box(5, 12, 5, 10, 13, 10, BLACK); m.box(4, 14, 6, 11, 15, 7, BLACK); m.box(4, 14, 6, 5, 15, 9, BLACK); m.box(10, 14, 6, 11, 15, 9, BLACK); m.cylY(14, 14, 7.5f, 9, 1.5f, CREAM); m.box(12, 12, 4, 13, 12, 6, PAPER); });
        metal("terminal_crt", 1, 1, 1, LOW, m -> { legs(m, 1, 2, 14, 13, 8, 1, STEEL_D); m.box(0, 9, 1, 15, 10, 14, 0xFF7A7A6E); m.box(3, 11, 2, 12, 20, 9, 0xFFB8B4A0); m.box(4, 12, 10, 11, 19, 10, CRT); m.box(5, 13, 11, 10, 18, 11, SCREEN_G); m.box(3, 11, 11, 12, 11, 14, 0xFFA8A490); for (int x = 4; x < 12; x += 2) m.set(x, 12, 13, CHARCOAL); });
        metal("terminal_dead", 1, 1, 1, LOW, m -> { legs(m, 1, 2, 14, 13, 8, 1, STEEL_D); m.box(0, 9, 1, 15, 10, 14, 0xFF7A7A6E); m.box(3, 11, 2, 12, 20, 9, 0xFFB8B4A0); m.box(4, 12, 10, 11, 19, 10, 0xFF14181A); m.box(6, 15, 10, 9, 17, 10, 0xFF0A0C0C); m.box(3, 11, 11, 12, 11, 14, 0xFFA8A490); });
        metal("mainframe", 1, 2, 1, FULL, m -> { m.box(0, 0, 0, 15, 30, 11, 0xFF5A6A6A); m.box(1, 2, 12, 14, 12, 12, 0xFF4A5858); lights(m, r, 2, 13, 3, 11, 13); m.box(1, 15, 12, 14, 28, 12, 0xFF2A3030);
            for (int[] c : new int[][]{{4, 23}, {11, 23}}) for (int x = c[0] - 2; x <= c[0] + 2; x++) for (int y = c[1] - 2; y <= c[1] + 2; y++) if (Math.hypot(x - c[0], y - c[1]) < 2.6) m.set(x, y, 13, Math.hypot(x - c[0], y - c[1]) < 1 ? STEEL_L : 0xFF6A4A2A);
            m.box(3, 16, 13, 12, 17, 13, 0xFF6A4A2A); m.box(0, 31, 0, 15, 31, 12, 0xFF4A5858); });
        metal("radio_set", 1, 1, 1, LOW, m -> { legs(m, 1, 2, 14, 13, 8, 1, OAK_D); m.box(0, 9, 1, 15, 10, 14, OAK); m.box(1, 11, 2, 14, 19, 9, OLIVE); m.box(2, 12, 10, 13, 18, 10, 0xFF3E4428); m.box(3, 15, 11, 7, 17, 11, CREAM); m.set(5, 16, 12, RED); m.set(10, 16, 11, LED_G); m.set(12, 16, 11, LED_A); m.cylX(10, 12, 13, 11.5f, 1.2f, BLACK); m.box(13, 20, 4, 13, 28, 4, STEEL_L); m.box(4, 11, 11, 9, 11, 13, BLACK); });
        metal("safe_big", 1, 1, 1, FULL, m -> { m.box(1, 0, 1, 14, 15, 13, 0xFF3A4038); m.box(2, 1, 14, 13, 14, 14, 0xFF4A5048); for (int x = 6; x <= 9; x++) for (int y = 6; y <= 9; y++) m.set(x, y, 15, (x + y) % 2 == 0 ? BRASS : STEEL_L); m.box(11, 5, 15, 11, 10, 15, BRASS); });
        metal("photocopier", 1, 1, 1, LOW, m -> { m.box(1, 0, 1, 14, 11, 13, 0xFFB8B4A8); m.box(1, 12, 1, 14, 13, 13, 0xFF8A8A84); m.box(2, 14, 2, 13, 14, 11, 0xFFC8C4B8); m.box(3, 4, 14, 12, 5, 15, 0xFF8A8A84); m.box(4, 6, 15, 11, 6, 15, PAPER); m.set(12, 13, 12, LED_G); });
        metal("water_cooler", 1, 2, 1, POST, m -> { m.box(4, 0, 4, 11, 15, 11, 0xFFC8C8C0); m.cylY(16, 24, 8, 8, 3.5f, 0xFF8AB4C8); m.cylY(25, 25, 8, 8, 2, 0xFF7AA4B8); m.set(6, 10, 12, BLUE); m.set(9, 10, 12, RED); m.box(5, 7, 12, 10, 7, 13, STEEL); });
        metal("vending_machine", 1, 2, 1, FULL, m -> { m.box(0, 0, 0, 15, 30, 11, RED_D); m.box(1, 10, 12, 10, 28, 12, 0xFF1E2428); for (int y = 12; y < 28; y += 5) for (int x = 2; x < 10; x += 3) m.box(x, y, 12, x + 1, y + 2, 12, r.nextInt(3) == 0 ? CHARCOAL : BOOKS[r.nextInt(BOOKS.length)]); m.box(12, 14, 12, 14, 22, 12, GREY); m.set(13, 20, 13, LED_R); m.box(2, 2, 12, 9, 6, 12, CHARCOAL); });
        metal("cart_tool", 1, 1, 1, LOW, m -> { for (int y : new int[]{2, 8, 13}) m.box(1, y, 2, 14, y, 13, RED_D); for (int[] c : new int[][]{{1, 2}, {14, 2}, {1, 13}, {14, 13}}) { m.box(c[0], 1, c[1], c[0], 14, c[1], STEEL_D); m.set(c[0], 0, c[1], BLACK); } m.box(3, 14, 4, 8, 14, 5, STEEL_L); m.box(10, 14, 7, 12, 15, 10, HAZARD); m.box(3, 9, 5, 7, 10, 10, STEEL); m.box(15, 14, 5, 15, 14, 10, STEEL_D); });
        metal("workbench_vice", 2, 1, 1, LOW, m -> { legs(m, 1, 2, 30, 13, 11, 2, STEEL_D); m.box(0, 12, 1, 31, 14, 14, OAK_L); m.box(1, 5, 3, 30, 5, 12, STEEL_D); m.box(24, 15, 9, 29, 18, 13, 0xFF3A5A7A); m.box(25, 19, 10, 28, 19, 12, STEEL_L); m.box(26, 17, 14, 27, 17, 15, STEEL_L); m.box(4, 15, 4, 12, 15, 5, STEEL_L); m.box(14, 15, 7, 15, 15, 12, OAK_D); m.box(5, 6, 5, 11, 9, 10, RED_D); m.box(0, 15, 0, 31, 24, 0, 0xFF6A5A42); m.box(4, 17, 1, 4, 22, 1, STEEL_L); m.box(8, 18, 1, 10, 21, 1, STEEL); m.box(14, 17, 1, 14, 23, 1, STEEL_L); });
        metal("generator_set", 2, 1, 1, LOW, m -> { m.box(1, 0, 2, 30, 1, 13, STEEL_D); m.box(2, 2, 3, 16, 12, 12, 0xFF3E5A3E); m.cylX(17, 28, 7.5f, 8, 4.5f, 0xFF8A7A32); m.box(4, 13, 6, 6, 15, 8, BLACK); m.box(10, 13, 5, 13, 14, 10, RED_D); m.box(3, 6, 13, 9, 10, 13, CHARCOAL); m.set(5, 8, 14, LED_G); m.box(29, 2, 3, 30, 12, 12, STEEL_D); });

        // ================================================================== the plant
        // a unit control desk: a sloped panel of keys and lamps, a flat of annunciators behind it
        def("console_control", 2, 1, 1, LOW, true, false, m -> { m.box(0, 0, 0, 31, 9, 12, 0xFF6A7870); m.box(0, 10, 0, 31, 11, 13, 0xFF58665E); for (int z = 3; z < 13; z++) m.box(0, 12 + (12 - z) / 3, z, 31, 12 + (12 - z) / 3, z, 0xFF8A988E);
            lights(m, r, 1, 30, 13, 15, 3); for (int x = 2; x < 30; x += 3) { m.set(x, 13, 10, r.nextInt(3) == 0 ? RED : CREAM); m.set(x + 1, 13, 8, BLACK); } m.box(13, 13, 6, 18, 13, 7, CRT); m.box(14, 14, 6, 17, 14, 6, SCREEN_G); m.box(0, 12, 0, 31, 15, 2, 0xFF58665E); });
        def("console_corner", 1, 1, 1, LOW, true, false, m -> { m.box(0, 0, 0, 15, 9, 12, 0xFF6A7870); m.box(0, 10, 0, 15, 11, 13, 0xFF58665E); for (int z = 3; z < 13; z++) m.box(0, 12 + (12 - z) / 3, z, 15, 12 + (12 - z) / 3, z, 0xFF8A988E); lights(m, r, 1, 14, 13, 15, 3); m.box(3, 13, 7, 6, 13, 9, HAZARD); m.box(10, 13, 6, 12, 14, 8, RED); m.box(0, 12, 0, 15, 15, 2, 0xFF58665E); });
        // the wall of a control room: rows of dials and lamps on standing panels
        def("panel_instruments", 1, 2, 1, FULL, true, false, m -> { m.box(0, 0, 0, 15, 31, 5, 0xFF7A8880); m.box(1, 2, 6, 14, 30, 6, 0xFF909E94); for (int y = 20; y < 29; y += 4) for (int x = 3; x < 13; x += 5) { m.box(x - 1, y - 1, 7, x + 2, y + 2, 7, CREAM); m.set(x, y, 8, BLACK); m.set(x + 1, y + 1, 8, RED); } lights(m, r, 2, 13, 9, 17, 7); m.box(2, 4, 7, 6, 6, 7, CHARCOAL); m.box(9, 4, 7, 13, 6, 7, CHARCOAL); m.set(4, 5, 8, RED); m.set(11, 5, 8, HAZARD); });
        // the mimic board: the reactor's channels as a grid of small lights, most of them out
        def("panel_mimic", 2, 2, 1, FULL, true, false, m -> { m.box(0, 0, 0, 31, 31, 4, 0xFF6A7870); m.box(1, 1, 5, 30, 30, 5, 0xFF2A322E); for (int x = 4; x < 28; x++) for (int y = 5; y < 27; y++) { if (Math.hypot(x - 15.5, y - 15.5) > 11.5) continue; int k = r.nextInt(60); m.set(x, y, 6, k == 0 ? LED_R : k < 3 ? LED_A : k < 6 ? LED_G : (x + y) % 2 == 0 ? 0xFF3A443E : 0xFF323A36); } m.box(2, 28, 6, 12, 29, 6, CREAM); });
        metal("switchgear", 1, 2, 1, FULL, m -> { m.box(0, 0, 0, 15, 31, 10, 0xFF58625C); m.box(1, 16, 11, 14, 30, 11, 0xFF6A746E); m.box(1, 1, 11, 14, 14, 11, 0xFF6A746E); m.box(3, 20, 12, 6, 26, 12, CREAM); m.set(4, 23, 13, BLACK); m.box(9, 19, 12, 12, 21, 12, BLACK); m.box(10, 22, 12, 11, 27, 13, RED); m.set(3, 28, 12, LED_G); m.set(5, 28, 12, LED_R); for (int y = 3; y < 13; y += 3) m.box(3, y, 12, 12, y, 12, CHARCOAL); m.box(6, 7, 12, 9, 9, 12, HAZARD); });
        metal("transformer_small", 1, 2, 1, FULL, m -> { m.box(1, 0, 1, 14, 20, 13, 0xFF5A6258); for (int x = 2; x < 14; x += 2) m.box(x, 2, 14, x, 18, 15, 0xFF4A524A); m.cylY(21, 26, 4, 7, 1.6f, 0xFF7A4A2A); m.cylY(21, 26, 8, 7, 1.6f, 0xFF7A4A2A); m.cylY(21, 26, 12, 7, 1.6f, 0xFF7A4A2A); for (int x : new int[]{4, 8, 12}) { m.cylY(23, 23, x, 7, 2.2f, 0xFF8A5A3A); m.set(x, 27, 7, STEEL_L); } m.box(5, 8, 0, 10, 12, 0, HAZARD); });
        metal("valve_stand", 1, 1, 1, LOW, m -> { m.box(5, 0, 5, 10, 1, 10, STEEL_D); m.cylY(1, 11, 8, 8, 2.2f, 0xFF8A4A2A); m.cylY(5, 6, 8, 8, 3.2f, STEEL_D); m.box(7, 11, 7, 8, 14, 8, STEEL_D); for (int x = 2; x <= 13; x++) for (int z = 2; z <= 13; z++) { double d = Math.hypot(x - 7.5, z - 7.5); if (d < 5.3 && d > 3.7) m.set(x, 13, z, RED); } m.box(7, 13, 3, 8, 13, 12, RED); m.box(3, 13, 7, 12, 13, 8, RED); });
        metal("pump_unit", 2, 1, 1, LOW, m -> { m.box(0, 0, 2, 31, 1, 13, STEEL_D); m.cylX(2, 14, 8, 8, 5.5f, 0xFF3E6A6A); m.cylX(15, 17, 8, 8, 3, STEEL); m.cylX(18, 28, 8, 8, 4.5f, 0xFF5A6A62); m.cylX(1, 1, 8, 8, 6, STEEL_D); m.cylX(14, 14, 8, 8, 6, STEEL_D); m.cylY(9, 15, 23, 8, 2.5f, STEEL); m.cylY(15, 15, 23, 8, 3.5f, STEEL_D); m.box(5, 13, 6, 8, 15, 9, CHARCOAL); m.set(6, 15, 10, LED_G); });
        metal("pipe_stack", 1, 2, 1, FULL, m -> { m.cylY(0, 31, 5, 8, 3.5f, 0xFF8A4A2A); m.cylY(0, 31, 11, 6, 2.5f, STEEL); m.cylY(10, 11, 5, 8, 4.5f, STEEL_D); m.cylY(22, 23, 5, 8, 4.5f, STEEL_D); m.cylY(15, 16, 11, 6, 3.4f, STEEL_D); m.box(11, 26, 9, 14, 27, 13, RED); m.box(1, 5, 12, 4, 9, 13, HAZARD); });
        metal("pipe_run", 2, 1, 1, LOW, m -> { m.cylX(0, 31, 4, 5, 3.2f, 0xFF8A4A2A); m.cylX(0, 31, 9, 11, 2.5f, STEEL); m.cylX(9, 10, 4, 5, 4.2f, STEEL_D); m.cylX(21, 22, 9, 11, 3.4f, STEEL_D); m.box(15, 8, 4, 16, 12, 5, STEEL_D); for (int x = 13; x <= 18; x++) for (int z = 2; z <= 8; z++) if (Math.hypot(x - 15.5, z - 5) < 3.2 && Math.hypot(x - 15.5, z - 5) > 2) m.set(x, 13, z, RED); m.box(4, 0, 4, 5, 1, 5, STEEL_D); m.box(26, 0, 10, 27, 5, 11, STEEL_D); });
        metal("tank_pressure", 1, 2, 1, FULL, m -> { m.cylY(3, 26, 8, 8, 6.5f, 0xFF8A9088); m.cylY(27, 29, 8, 8, 5, 0xFF8A9088); m.cylY(30, 30, 8, 8, 3, 0xFF8A9088); legs(m, 3, 3, 12, 12, 2, 2, STEEL_D); m.cylY(13, 14, 8, 8, 6.9f, STEEL_D); m.box(6, 16, 14, 9, 19, 15, CREAM); m.set(7, 17, 15, RED); m.box(7, 30, 7, 8, 31, 8, STEEL_D); m.box(5, 6, 14, 10, 9, 15, HAZARD); });
        metal("fuel_cask", 2, 2, 2, FULL, m -> { m.cylY(2, 27, 16, 16, 13, 0xFFB8B8AC); m.cylY(0, 1, 16, 16, 14, 0xFF6A7068); m.cylY(28, 29, 16, 16, 14, 0xFF6A7068); m.cylY(30, 30, 16, 16, 9, 0xFF8A9088); for (int y = 5; y < 26; y += 4) m.cylY(y, y, 16, 16, 13.5f, 0xFF9A9A8E); m.box(12, 12, 29, 19, 19, 30, HAZARD); m.box(15, 15, 31, 16, 16, 31, BLACK); m.box(15, 18, 31, 16, 18, 31, BLACK); m.box(13, 13, 31, 14, 14, 31, BLACK); m.box(17, 13, 31, 18, 14, 31, BLACK); });
        metal("crane_control", 1, 1, 1, LOW, m -> { m.box(5, 0, 5, 10, 10, 10, HAZARD); m.box(3, 11, 3, 12, 13, 12, CHARCOAL); m.box(5, 14, 5, 6, 16, 6, BLACK); m.box(9, 14, 5, 10, 16, 6, BLACK); m.set(5, 17, 5, RED); m.set(9, 17, 5, RED); m.set(7, 14, 10, LED_G); m.box(7, 11, 13, 8, 15, 15, BLACK); });
        metal("respirator_rack", 1, 2, 1, FULL, m -> { m.box(0, 0, 0, 15, 31, 2, 0xFF5A6A62); for (int y = 4; y < 28; y += 8) { m.box(1, y, 3, 14, y, 8, STEEL_D); for (int x = 2; x < 13; x += 5) { m.box(x, y + 1, 4, x + 3, y + 4, 7, 0xFF3A4038); m.cylX(x + 1, x + 2, y + 2, 8.5f, 1.4f, 0xFF6A7068); m.box(x, y + 3, 8, x, y + 3, 8, GLASS_C); m.box(x + 3, y + 3, 8, x + 3, y + 3, 8, GLASS_C); } } });
        metal("suit_rack", 1, 2, 1, FULL, m -> { m.box(1, 28, 6, 14, 28, 7, STEEL_D); m.box(1, 0, 6, 1, 28, 7, STEEL_D); m.box(14, 0, 6, 14, 28, 7, STEEL_D); int[] cs = {0xFFC8C8B8, 0xFFB8B8A8, HAZARD}; for (int i = 0; i < 3; i++) { int x = 3 + i * 4; m.box(x, 8, 4, x + 2, 27, 9, cs[i]); m.box(x, 3, 5, x + 2, 7, 8, cs[i]); m.box(x, 0, 5, x + 2, 2, 9, RUBBER); } });
        metal("stretcher", 1, 1, 2, LOW, m -> { legs(m, 2, 2, 13, 29, 8, 1, STEEL); m.box(1, 9, 1, 14, 10, 30, 0xFF7A8A82); m.box(3, 11, 23, 12, 12, 29, WHITE); m.box(1, 11, 4, 14, 11, 18, 0xFFA8A898); for (int[] c : new int[][]{{2, 2}, {13, 2}, {2, 29}, {13, 29}}) m.set(c[0], 0, c[1], BLACK); m.box(4, 12, 8, 9, 12, 14, 0xFF4A1612); });
        metal("barrier_rope", 2, 1, 1, POST, m -> { m.box(2, 0, 7, 3, 12, 8, HAZARD); m.box(28, 0, 7, 29, 12, 8, HAZARD); m.cylY(0, 0, 2.5f, 7.5f, 2.2f, BLACK); m.cylY(0, 0, 28.5f, 7.5f, 2.2f, BLACK); for (int x = 4; x < 28; x++) m.set(x, 10 - (int) (2.5 * Math.sin((x - 4) / 24.0 * Math.PI)), 7, x % 4 < 2 ? HAZARD : BLACK); });
        metal("geiger_station", 1, 1, 1, LOW, m -> { legs(m, 2, 2, 13, 13, 9, 1, STEEL_D); m.box(1, 10, 1, 14, 11, 14, 0xFF7A8880); m.box(3, 12, 4, 9, 15, 10, HAZARD); m.box(4, 13, 11, 8, 14, 11, CREAM); m.set(6, 13, 12, RED); m.box(11, 12, 5, 12, 12, 11, BLACK); m.cylX(10, 13, 13.5f, 6, 1.2f, STEEL); m.box(3, 16, 5, 3, 16, 9, BLACK); });
        metal("reactor_tools", 1, 2, 1, POST, m -> { m.box(1, 0, 6, 14, 1, 9, STEEL_D); m.box(1, 2, 7, 1, 30, 8, STEEL_D); m.box(14, 2, 7, 14, 30, 8, STEEL_D); m.box(1, 30, 7, 14, 30, 8, STEEL_D); for (int x = 3; x < 13; x += 3) { m.box(x, 4, 7, x, 29, 7, STEEL_L); m.box(x - 1, 3, 7, x + 1, 4, 8, x % 2 == 0 ? RED : HAZARD); } });

        // ================================================================== the wards (gaol and asylum)
        metal("bed_restraint", 1, 1, 2, LOW, m -> { legs(m, 1, 1, 14, 30, 7, 1, 0xFFB8B8AC); m.box(1, 8, 1, 14, 9, 30, 0xFFB8B8AC); m.box(2, 10, 2, 13, 11, 29, 0xFF9A9480); m.box(1, 12, 9, 14, 12, 10, LEATHER_D); m.box(1, 12, 19, 14, 12, 20, LEATHER_D); m.box(0, 9, 9, 0, 12, 10, BRASS); m.box(15, 9, 19, 15, 12, 20, BRASS); m.box(1, 8, 31, 14, 16, 31, 0xFFB8B8AC); m.box(4, 11, 24, 11, 12, 29, 0xFF8A8672); m.box(5, 11, 12, 9, 11, 16, 0xFF5A2A1E); });
        metal("bed_hospital_p", 1, 1, 2, LOW, m -> { legs(m, 1, 1, 14, 30, 7, 1, 0xFFC8CCC4); m.box(1, 8, 1, 14, 9, 30, 0xFFC8CCC4); m.box(2, 10, 2, 13, 11, 29, WHITE); m.box(2, 12, 3, 13, 12, 18, MINT); m.box(4, 12, 23, 11, 13, 28, WHITE); m.box(1, 8, 31, 14, 17, 31, 0xFFC8CCC4); m.box(1, 8, 0, 14, 13, 0, 0xFFC8CCC4); });
        metal("gurney", 1, 1, 2, LOW, m -> { m.box(1, 9, 1, 14, 10, 30, STEEL); m.box(2, 11, 2, 13, 11, 29, 0xFF2A2C30); m.box(2, 0, 3, 2, 8, 4, STEEL_D); m.box(13, 0, 3, 13, 8, 4, STEEL_D); m.box(2, 0, 27, 2, 8, 28, STEEL_D); m.box(13, 0, 27, 13, 8, 28, STEEL_D); m.box(2, 4, 4, 13, 4, 27, STEEL_D); m.box(2, 12, 6, 13, 13, 24, 0xFFB0AC98); m.box(5, 13, 10, 10, 13, 12, 0xFF5A1A16); });
        metal("wheelchair", 1, 1, 1, LOW, m -> { m.box(4, 5, 4, 11, 6, 11, CHARCOAL); m.box(4, 7, 4, 11, 14, 5, CHARCOAL); m.box(3, 8, 5, 3, 9, 11, STEEL); m.box(12, 8, 5, 12, 9, 11, STEEL); m.cylX(2, 2, 5, 8, 4.6f, BLACK); m.cylX(13, 13, 5, 8, 4.6f, BLACK); m.cylX(2, 2, 5, 8, 3.4f, 0); m.cylX(13, 13, 5, 8, 3.4f, 0); m.box(2, 5, 8, 2, 5, 8, STEEL); m.box(13, 5, 8, 13, 5, 8, STEEL); m.box(5, 0, 12, 5, 4, 13, STEEL); m.box(10, 0, 12, 10, 4, 13, STEEL); m.box(4, 15, 3, 4, 15, 4, BLACK); m.box(11, 15, 3, 11, 15, 4, BLACK); });
        metal("iv_stand", 1, 2, 1, POST, m -> { m.box(7, 1, 7, 8, 28, 8, STEEL_L); m.box(4, 0, 7, 11, 0, 8, STEEL_D); m.box(7, 0, 4, 8, 0, 11, STEEL_D); m.box(5, 28, 7, 10, 28, 8, STEEL_L); m.box(4, 22, 7, 6, 27, 8, 0xFFC8D8D8); m.box(5, 12, 7, 5, 21, 7, 0xFFB8C8C8); });
        metal("cart_medicine", 1, 1, 1, LOW, m -> { for (int y : new int[]{3, 9, 14}) m.box(2, y, 3, 13, y, 12, 0xFFC8CCC4); for (int[] c : new int[][]{{2, 3}, {13, 3}, {2, 12}, {13, 12}}) { m.box(c[0], 1, c[1], c[0], 14, c[1], STEEL); m.set(c[0], 0, c[1], BLACK); } for (int x = 3; x < 12; x += 2) m.box(x, 15, 4 + (x % 3), x, 16 + (x % 2), 4 + (x % 3), x % 4 == 1 ? 0xFF8A5A2A : WHITE); m.box(4, 10, 5, 9, 11, 9, STEEL_L); m.box(10, 15, 9, 12, 15, 11, 0xFF8E2A22); });
        metal("table_operating", 1, 1, 2, LOW, m -> { m.cylY(0, 1, 8, 16, 5, STEEL_D); m.box(6, 2, 14, 9, 9, 17, STEEL); m.box(2, 10, 2, 13, 11, 29, STEEL_L); m.box(3, 12, 3, 12, 12, 28, 0xFF3A4A44); m.box(0, 11, 10, 1, 11, 13, STEEL); m.box(14, 11, 18, 15, 11, 21, STEEL); m.box(5, 13, 12, 10, 13, 17, 0xFF4A1612); });
        deco("lamp_surgical", 2, 1, 2, m -> { m.box(15, 10, 15, 16, 15, 16, STEEL); m.cylY(7, 9, 16, 16, 9, 0xFFC8CCC4); m.cylY(6, 6, 16, 16, 8, 0xFF8A9088); for (int[] c : new int[][]{{12, 12}, {19, 12}, {12, 19}, {19, 19}, {16, 16}}) m.set(c[0], 6, c[1], 0xFFD8E0E0); });
        metal("electroshock", 1, 1, 1, LOW, m -> { legs(m, 2, 2, 13, 13, 8, 1, STEEL_D); m.box(1, 9, 1, 14, 10, 14, OAK_D); m.box(2, 11, 3, 11, 16, 11, 0xFF3E3A32); m.box(3, 13, 12, 6, 15, 12, CREAM); m.set(4, 14, 13, RED); m.cylX(8, 9, 14, 12.5f, 1.2f, BLACK); m.set(10, 12, 12, LED_R); m.box(12, 11, 5, 13, 11, 6, BLACK); m.box(12, 11, 9, 13, 12, 10, STEEL_L); m.box(11, 11, 12, 14, 11, 13, BLACK); });
        metal("cage_cell", 1, 2, 1, FULL, m -> { m.box(0, 0, 0, 15, 0, 15, STEEL_D); m.box(0, 31, 0, 15, 31, 15, STEEL_D); for (int i = 0; i < 16; i += 3) { m.box(i, 1, 0, i, 30, 0, IRON); m.box(i, 1, 15, i, 30, 15, IRON); m.box(0, 1, i, 0, 30, i, IRON); m.box(15, 1, i, 15, 30, i, IRON); } m.box(0, 15, 0, 15, 15, 0, IRON); m.box(0, 15, 15, 15, 15, 15, IRON); });
        metal("bunk_cell", 1, 1, 2, LOW, m -> { m.box(0, 6, 0, 15, 7, 31, 0xFF5A6258); m.box(1, 8, 1, 14, 9, 30, 0xFF7A7A6A); m.box(3, 10, 24, 12, 10, 29, 0xFF9A9A8A); m.box(1, 10, 2, 14, 10, 16, 0xFF5A5A52); m.box(0, 0, 0, 0, 5, 1, IRON); m.box(15, 0, 30, 15, 5, 31, IRON); m.box(0, 0, 30, 0, 5, 31, IRON); m.box(15, 0, 0, 15, 5, 1, IRON); });
        metal("toilet_steel", 1, 1, 1, LOW, m -> { m.box(4, 0, 2, 11, 7, 11, STEEL); m.box(5, 7, 4, 10, 7, 10, 0); m.box(3, 8, 0, 12, 13, 2, STEEL); m.box(5, 10, 3, 10, 12, 6, STEEL_L); m.box(6, 12, 4, 9, 12, 5, 0); });
        deco("padding_wall", 1, 2, 1, m -> { m.box(0, 0, 0, 15, 31, 1, 0xFFB8B09A); for (int y = 0; y < 32; y += 8) m.box(0, y, 2, 15, y, 2, 0xFF8A8472); for (int x = 0; x < 16; x += 8) m.box(x, 0, 2, x, 31, 2, 0xFF8A8472); for (int y = 4; y < 32; y += 8) for (int x = 4; x < 16; x += 8) m.set(x, y, 2, 0xFF6A6456); m.box(9, 3, 2, 12, 9, 2, 0xFF7A7462); });
        deco("shackles_wall", 1, 1, 1, m -> { m.box(3, 12, 0, 4, 13, 1, IRON); m.box(11, 12, 0, 12, 13, 1, IRON); for (int y = 5; y < 12; y++) { m.set(3 + (y % 2), y, 1, IRON); m.set(11 + (y % 2), y, 1, IRON); } m.box(2, 3, 1, 5, 4, 2, IRON); m.box(10, 3, 1, 13, 4, 2, IRON); });
        metal("desk_guard", 2, 1, 1, LOW, m -> { m.box(0, 0, 2, 31, 12, 13, 0xFF5A6258); m.box(0, 13, 1, 31, 14, 14, 0xFF7A6A52); m.box(3, 15, 4, 9, 15, 9, PAPER); m.box(12, 15, 5, 13, 18, 6, BLACK); m.box(20, 15, 4, 27, 20, 10, 0xFFB8B4A0); m.box(21, 16, 11, 26, 19, 11, 0xFF14181A); m.box(15, 15, 10, 17, 15, 12, BRASS); });
        metal("visit_booth", 1, 2, 1, FULL, m -> { m.box(0, 0, 6, 15, 12, 9, 0xFF5A6258); m.box(0, 13, 4, 15, 13, 11, 0xFF7A6A52); m.box(0, 14, 7, 15, 28, 7, GLASS_C); m.box(0, 14, 7, 0, 30, 8, STEEL_D); m.box(15, 14, 7, 15, 30, 8, STEEL_D); m.box(0, 29, 7, 15, 30, 8, STEEL_D); m.box(12, 15, 9, 13, 20, 10, BLACK); for (int x = 6; x < 10; x++) m.set(x, 18, 7, STEEL_D); });
        wood("pew_church", 2, 1, 1, LOW, m -> { m.box(0, 0, 3, 1, 14, 12, WALNUT); m.box(30, 0, 3, 31, 14, 12, WALNUT); m.box(1, 6, 4, 30, 7, 12, OAK_D); m.box(1, 8, 3, 30, 14, 4, OAK_D); });

        // ================================================================== shops, bars, streets brought indoors
        metal("cash_register", 1, 1, 1, NONE, m -> { m.box(3, 0, 4, 12, 4, 12, GREY_D); m.box(4, 5, 5, 11, 8, 9, GREY); for (int x = 5; x < 11; x += 2) for (int z = 10; z < 12; z++) m.set(x, 5, z, CREAM); m.box(5, 9, 5, 10, 11, 6, CHARCOAL); m.box(6, 10, 7, 9, 10, 7, SCREEN_G); });
        metal("shopping_cart", 1, 1, 1, LOW, m -> { for (int[] c : new int[][]{{3, 3}, {12, 3}, {3, 13}, {12, 13}}) { m.set(c[0], 0, c[1], BLACK); m.box(c[0], 1, c[1], c[0], 5, c[1], STEEL); } m.box(3, 6, 3, 12, 6, 13, STEEL); for (int x = 3; x <= 12; x += 3) { m.box(x, 7, 3, x, 12, 3, STEEL_L); m.box(x, 7, 13, x, 12, 13, STEEL_L); } for (int z = 3; z <= 13; z += 2) { m.box(3, 7, z, 3, 12, z, STEEL_L); m.box(12, 7, z, 12, 12, z, STEEL_L); } m.box(3, 13, 14, 12, 13, 14, RED); });
        metal("freezer_chest", 2, 1, 1, LOW, m -> { m.box(0, 0, 1, 31, 12, 14, ENAMEL); m.box(0, 13, 1, 31, 13, 14, 0xFF8AA4B0); m.box(1, 13, 2, 30, 13, 13, 0xFFB0C8D0); m.box(15, 13, 1, 16, 14, 14, STEEL); m.box(2, 3, 15, 9, 6, 15, BLUE_D); m.box(24, 2, 15, 29, 4, 15, CHARCOAL); });
        wood("mannequin", 1, 2, 1, POST, m -> { m.cylY(0, 0, 8, 8, 3.5f, STEEL_D); m.box(7, 1, 7, 8, 12, 8, STEEL_D); m.box(5, 13, 6, 10, 23, 9, 0xFFC8B8A0); m.box(3, 17, 6, 4, 23, 8, 0xFFC8B8A0); m.box(11, 17, 6, 12, 23, 8, 0xFFC8B8A0); m.box(6, 24, 6, 9, 28, 9, 0xFFC8B8A0); m.box(5, 13, 5, 10, 22, 10, 0xFF3A4A6A); });
        wood("clothes_rail", 2, 2, 1, POST, m -> { m.box(1, 0, 7, 1, 26, 8, STEEL); m.box(30, 0, 7, 30, 26, 8, STEEL); m.box(1, 26, 7, 30, 26, 8, STEEL); m.box(0, 0, 5, 2, 0, 10, STEEL_D); m.box(29, 0, 5, 31, 0, 10, STEEL_D); int[] cs = {0xFF6A2A2A, 0xFF2E4A6A, 0xFF8A8672, 0xFF3A3A3E, 0xFF5A6A3A, 0xFF7A5A7A}; for (int i = 0; i < 6; i++) { if (i == 3) continue; int x = 4 + i * 4; m.box(x, 10 + (i % 3) * 2, 5, x + 2, 25, 10, cs[i]); } });
        wood("magazine_rack", 1, 2, 1, FULL, m -> { m.box(0, 0, 0, 15, 30, 3, OAK_D); for (int y = 2; y < 28; y += 6) { m.box(0, y, 4, 15, y, 7, OAK_D); for (int x = 1; x < 14; x += 4) if (r.nextInt(4) != 0) m.box(x, y + 1, 4, x + 2, y + 4, 5, BOOKS[r.nextInt(BOOKS.length)]); } });
        metal("jukebox", 1, 2, 1, FULL, m -> { m.box(1, 0, 1, 14, 20, 11, WALNUT); for (int x = 1; x <= 14; x++) for (int y = 21; y <= 27; y++) if (Math.hypot(x - 7.5, y - 20) < 7.5) m.box(x, y, 1, x, y, 11, WALNUT); m.box(3, 12, 12, 12, 20, 12, DIM | 0xE0A040); m.box(3, 3, 12, 12, 9, 12, 0xFF6A5A32); for (int y = 4; y < 9; y += 2) m.box(4, y, 13, 11, y, 13, BRASS); m.box(2, 21, 12, 13, 23, 12, DIM | 0xC83A3A); });
        metal("beer_taps", 1, 1, 1, NONE, m -> { m.box(2, 0, 6, 13, 1, 9, BRASS); for (int x = 4; x < 13; x += 4) { m.box(x, 2, 7, x, 8, 8, BRASS); m.box(x, 8, 8, x, 8, 10, BRASS); m.box(x, 9, 7, x, 12, 7, x == 8 ? RED : BLACK); } });
        wood("bar_back", 2, 2, 1, FULL, m -> { m.box(0, 0, 0, 31, 12, 8, WALNUT); m.box(0, 13, 0, 31, 13, 9, OAK_D); m.box(0, 14, 0, 31, 31, 2, WALNUT); m.box(2, 15, 3, 29, 28, 3, 0xFF8A9AA0); for (int y = 14; y < 28; y += 7) { m.box(1, y + 6, 3, 30, y + 6, 7, OAK_D); bottles(m, r, 2, 29, y + (y == 14 ? 0 : 7), 5); } bottles(m, r, 2, 29, 14, 6); m.box(0, 30, 0, 31, 31, 9, OAK_D); });
        wood("stall_market", 2, 2, 1, FULL, m -> { legs(m, 0, 1, 31, 14, 28, 1, PINE); m.box(0, 9, 1, 31, 10, 14, PINE); for (int x = 0; x < 32; x += 4) m.box(x, 28, 0, x + 3, 29 + (x % 8 == 0 ? 1 : 0), 15, x % 8 == 0 ? RED : CREAM); m.box(2, 11, 3, 10, 13, 12, 0xFF9A7A4A); m.box(13, 11, 4, 20, 12, 11, 0xFF6A8A3A); m.box(23, 11, 3, 29, 14, 10, 0xFF9A7A4A); m.box(24, 15, 4, 28, 15, 9, 0xFFC86A20); });
        metal("tire_stack", 1, 1, 1, LOW, m -> { for (int y = 0; y < 15; y += 5) { m.cylY(y, y + 3, 8 + (y == 5 ? 1 : 0), 8, 6.5f, RUBBER); m.cylY(y, y + 3, 8 + (y == 5 ? 1 : 0), 8, 3.2f, 0); } });
        metal("engine_hoist", 1, 2, 1, POST, m -> { m.box(1, 0, 2, 2, 1, 14, RED_D); m.box(13, 0, 2, 14, 1, 14, RED_D); m.box(1, 0, 2, 14, 1, 3, RED_D); m.box(7, 2, 2, 8, 26, 3, RED_D); for (int k = 0; k < 11; k++) m.box(7, 26 + (k > 5 ? 0 : 0), 3 + k, 8, 27, 3 + k, RED_D); m.box(7, 16, 13, 8, 25, 13, IRON); m.box(5, 11, 11, 10, 15, 14, 0xFF5A5E60); m.box(6, 9, 12, 9, 10, 13, STEEL_D); });
        metal("anvil", 1, 1, 1, LOW, m -> { m.box(4, 0, 4, 11, 5, 11, OAK_D); m.box(5, 6, 5, 10, 8, 10, IRON); m.box(2, 9, 5, 13, 11, 10, IRON); m.box(13, 10, 6, 15, 10, 9, IRON); m.box(1, 12, 7, 6, 12, 7, OAK_L); m.box(0, 12, 6, 1, 13, 8, STEEL); });
        wood("hay_bales", 1, 1, 1, LOW, m -> { m.box(0, 0, 1, 15, 7, 14, 0xFFB8A050); m.box(2, 8, 2, 13, 14, 13, 0xFFC0A858); m.box(0, 2, 1, 15, 2, 14, 0xFF7A6A32); m.box(0, 5, 1, 15, 5, 14, 0xFF7A6A32); m.box(2, 11, 2, 13, 11, 13, 0xFF7A6A32); });
        metal("trough", 2, 1, 1, LOW, m -> { m.box(0, 2, 3, 31, 8, 12, 0xFF6A7068); m.box(1, 4, 4, 30, 8, 11, 0); m.box(1, 4, 4, 30, 6, 11, 0xFF3A4A4A); legs(m, 1, 4, 30, 11, 1, 2, STEEL_D); });
        wood("ladder_lean", 1, 2, 1, NONE, m -> { for (int y = 0; y < 31; y++) { int z = 12 - y * 11 / 31; m.set(4, y, z, PINE); m.set(11, y, z, PINE); if (y % 5 == 2) m.box(4, y, z, 11, y, z, OAK_L); } });
        metal("campfire_tripod", 1, 1, 1, POST, m -> { m.cylY(0, 0, 8, 8, 5, 0xFF2A2622); for (int i = 0; i < 12; i++) { m.set(3 + i / 3, i, 4 + i / 3, IRON); m.set(12 - i / 3, i, 4 + i / 3, IRON); m.set(8, i, 13 - i / 2, IRON); } m.box(7, 4, 7, 8, 11, 8, IRON); m.cylY(2, 4, 8, 8, 2.5f, BLACK); m.set(6, 1, 9, EMBER_C); m.set(9, 1, 7, EMBER_C); });
    }

    static {
        for (String n : new String[]{"painting_portrait", "painting_lady", "painting_landscape", "painting_small", "photo_frames", "mirror_gilt", "mirror_broken", "trophy_head", "clock_wall", "tapestry", "curtain_red", "curtain_torn",
                "shelf_wall", "coat_hooks", "notice_board", "missing_posters", "calendar", "map_wall", "blackboard", "cross_wall", "radiator", "pipes_wall", "valve_wheel", "gauges", "fuse_box", "fire_extinguisher", "first_aid_box",
                "sign_radiation", "sign_exit", "dosimeter_post", "phone_wall", "intercom", "vent_grille", "xray_lightbox", "sconce", "lantern_wall", "lamp_wall_off", "padding_wall", "shackles_wall", "curtain_tall", "panel_annunciator"}) DEFS.get(n).mount = WALL;
        for (String n : new String[]{"chandelier", "chandelier_small", "lamp_hanging_off", "fluorescent_dead", "fan_ceiling", "pots_hanging", "duct", "cable_tray", "pipes_ceiling", "hook_crane", "cobwebs", "lamp_surgical", "beam_ceiling", "lamp_ceiling_lit", "meat_hooks"}) DEFS.get(n).mount = CEILING;
    }

    /** A hearth, three blocks wide and two high: stone surround, mantel with what stands on it, the firebox with logs - burning or dead. */
    private static void hearth(VoxModel m, boolean lit) {
        m.box(0, 0, 0, 47, 21, 7, STONE_C); m.box(6, 0, 0, 41, 15, 9, sh(STONE_C, 0.9f)); m.box(10, 0, 2, 37, 12, 9, 0); m.box(10, 0, 2, 37, 12, 2, 0xFF1E1A18); m.box(10, 0, 3, 37, 0, 9, 0xFF2A2420);
        m.box(0, 22, 0, 47, 23, 10, OAK_D); m.box(2, 24, 0, 45, 31, 5, STONE_C); m.box(8, 25, 6, 39, 30, 6, sh(STONE_C, 0.85f));
        m.box(14, 1, 4, 22, 2, 7, 0xFF3A2A1C); m.box(22, 1, 5, 33, 3, 6, 0xFF4A3220); m.box(18, 3, 4, 28, 4, 5, 0xFF3A2A1C);
        m.box(11, 0, 9, 11, 6, 9, IRON); m.box(36, 0, 9, 36, 6, 9, IRON); m.box(11, 6, 9, 36, 6, 9, IRON);
        if (lit) { m.box(16, 3, 5, 31, 4, 6, EMBER_C); m.box(19, 5, 5, 28, 7, 5, FLAME); m.box(22, 8, 5, 25, 10, 5, FLAME); m.set(17, 5, 6, FLAME); m.set(30, 5, 6, FLAME); }
        else { m.box(15, 1, 3, 32, 1, 8, 0xFF5A5650); m.set(20, 2, 7, 0xFF6E6A62); m.set(27, 2, 4, 0xFF6E6A62); }
        // the mantel: a clock that has stopped, two candlesticks, a photograph
        m.box(21, 24, 6, 26, 29, 9, WALNUT); m.box(22, 25, 10, 25, 28, 10, CREAM); m.set(23, 27, 11, BLACK); m.set(24, 26, 11, BLACK);
        m.box(8, 24, 7, 8, 28, 7, BRASS); m.set(8, 29, 7, CREAM); m.box(39, 24, 7, 39, 28, 7, BRASS); m.set(39, 29, 7, CREAM);
        m.box(31, 24, 7, 35, 28, 7, BLACK); m.box(32, 25, 8, 34, 27, 8, 0xFFA8A090);
    }
}
