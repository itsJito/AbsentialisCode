package absentialis.world.gen.arch;

import absentialis.engine.util.Rng;
import absentialis.world.Block;
import absentialis.world.MapData;
import absentialis.world.World;
import absentialis.world.gen.Gen;
import absentialis.world.gen.GenDefs.Archetype;
import absentialis.world.gen.arch.MaterialRules.Mat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Dresses a room from its purpose (0.5). Furniture is cover and loot, never an obstacle: a piece is placed only
 * if every walkable cell of the room stays connected afterwards, and never on a doorway's approach or a stair.
 * So a room can always be crossed from any of its doors to any other, however it was filled.
 */
final class Furnisher {
    private final Gen g;
    private int x0, z0, w, d, y;
    private boolean[] solid, noPlace;
    private int free;
    private final List<int[]> wallCells = new ArrayList<>();
    private int[] queue = new int[256];
    private boolean[] seen = new boolean[256];
    private Rng r;
    private String bias;

    Furnisher(Gen g) { this.g = g; }

    private static long key(int x, int y, int z) { return ((long) x << 40) | ((long) y << 20) | z; }

    void dress(MapData.Room room, MapData.Building b, Mat mat, Archetype a, Set<Long> keep, Rng rng, float lit) {
        if (room.circulation) { light(room, lit * 0.8f, rng); return; }
        r = rng;
        bias = a.loot;
        x0 = room.x0; z0 = room.z0; w = room.x1 - room.x0 + 1; d = room.z1 - room.z0 + 1; y = room.y;
        int n = w * d;
        if (solid == null || solid.length < n) { solid = new boolean[n]; noPlace = new boolean[n]; }
        if (queue.length < n) { queue = new int[n]; seen = new boolean[n]; }
        free = 0;
        wallCells.clear();
        for (int i = 0; i < w; i++) for (int k = 0; k < d; k++) {
            int x = x0 + i, z = z0 + k, c = i * d + k;
            int floor = g.get(x, y, z);
            boolean walk = floor != 0 && Block.ALL[floor].solid() && !Block.ALL[floor].is(Block.STEP) && g.air(x, y + 1, z) && g.air(x, y + 2, z);
            solid[c] = !walk;
            noPlace[c] = !walk || keep.contains(key(x, y + 1, z));
            if (walk) free++;
            if (walk && (i == 0 || k == 0 || i == w - 1 || k == d - 1)) wallCells.add(new int[]{x, z});
        }
        // deterministic shuffle
        for (int i = wallCells.size() - 1; i > 0; i--) { int j = r.nextInt(i + 1); int[] t = wallCells.get(i); wallCells.set(i, wallCells.get(j)); wallCells.set(j, t); }
        int wallsAtStart = wallCells.size();
        furnish(room.type, mat);
        fillOut(room.type, mat, wallsAtStart);
        decorate(room, mat);
        if (r.chance(0.22)) wall(Block.TRASH, 1, "trash");
        if (r.chance(0.18)) { int cx = r.chance(0.5) ? room.x0 : room.x1, cz = r.chance(0.5) ? room.z0 : room.z1; if (g.air(cx, y + 3, cz) && g.get(cx, y + 4, cz) != 0) g.setIfAir(cx, y + 3, cz, Block.COBWEB); }
        light(room, lit, rng);
    }

    // ------------------------------------------------------------------ placement primitives
    private int idx(int x, int z) { return (x - x0) * d + (z - z0); }
    private boolean in(int x, int z) { return x >= x0 && z >= z0 && x < x0 + w && z < z0 + d; }

    /** True when the cell can take a piece and the rest of the floor stays one connected walkable area. */
    private boolean canPlace(int x, int z) {
        if (!in(x, z)) return false;
        int c = idx(x, z);
        if (solid[c] || noPlace[c]) return false;
        if (free <= 1) return false;
        solid[c] = true;
        int n = w * d, start = -1;
        for (int i = 0; i < n; i++) if (!solid[i]) { start = i; break; }
        boolean ok = false;
        if (start >= 0) {
            java.util.Arrays.fill(seen, 0, n, false);
            int head = 0, tail = 0, count = 0;
            queue[tail++] = start; seen[start] = true;
            while (head < tail) {
                int cur = queue[head++];
                count++;
                int ci = cur / d, ck = cur % d;
                if (ci > 0 && !solid[cur - d] && !seen[cur - d]) { seen[cur - d] = true; queue[tail++] = cur - d; }
                if (ci < w - 1 && !solid[cur + d] && !seen[cur + d]) { seen[cur + d] = true; queue[tail++] = cur + d; }
                if (ck > 0 && !solid[cur - 1] && !seen[cur - 1]) { seen[cur - 1] = true; queue[tail++] = cur - 1; }
                if (ck < d - 1 && !solid[cur + 1] && !seen[cur + 1]) { seen[cur + 1] = true; queue[tail++] = cur + 1; }
            }
            ok = count == free - 1;
        }
        solid[c] = false;
        return ok;
    }

    /** Pieces placed in the room being dressed (density gate). */
    private int pieces;
    /** Furnished cells over walkable cells, summed over every room dressed (gates read the ratio). */
    public long statPieces, statCells;

    private boolean place(int x, int z, int block, String loot) {
        if (!canPlace(x, z)) return false;
        if (Block.ALL[block].is(Block.CONTAINER)) g.container(x, y + 1, z, block, loot);
        else g.set(x, y + 1, z, block);
        pieces++; statPieces++;
        // storage furniture stands to the ceiling: a second tier of the same unit (not a second thing to search)
        if ((block == Block.BOOKSHELF || block == Block.SHELF || block == Block.LOCKER || block == Block.RACK) && g.air(x, y + 2, z) && g.get(x, y + 3, z) == 0 && Rng.hash(x, y, z, 77) < 0.55f) g.set(x, y + 2, z, block == Block.SHELF ? Block.RACK : block);
        solid[idx(x, z)] = true;
        free--;
        return true;
    }

    private int wall(int block, int count) { return wall(block, count, null); }

    /** Up to {@code count} pieces against the room's walls. */
    private int wall(int block, int count, String loot) {
        int placed = 0;
        boolean box = Block.ALL[block].is(Block.CONTAINER);
        for (int i = 0; i < wallCells.size() && placed < count; i++) {
            int[] c = wallCells.get(i);
            // 0.7: nothing to search stands in a row of its own kind - two of a thing side by side is a pair, three is a warehouse
            if (box && run(c[0], c[1], block) >= 3) continue;
            if (place(c[0], c[1], block, loot)) { wallCells.remove(i); i--; placed++; }
        }
        return placed;
    }

    /** How many of the four neighbours of a cell already hold this block. */
    private int same(int x, int z, int block) {
        int n = 0;
        for (int[] dd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (g.get(x + dd[0], y + 1, z + dd[1]) == block) n++;
        return n;
    }

    /** The longest line of this block a cell would be part of if it held one too (along either axis). */
    private int run(int x, int z, int block) {
        int best = 1;
        for (int ax = 0; ax < 2; ax++) {
            int dx = ax == 0 ? 1 : 0, dz = 1 - dx, n = 1;
            for (int k = 1; k < 4 && g.get(x + dx * k, y + 1, z + dz * k) == block; k++) n++;
            for (int k = 1; k < 4 && g.get(x - dx * k, y + 1, z - dz * k) == block; k++) n++;
            best = Math.max(best, n);
        }
        return best;
    }

    private boolean center(int block, String loot) { return w >= 3 && d >= 3 && place(x0 + w / 2, z0 + d / 2, block, loot); }

    /** A table in the middle with chairs drawn up to it (rooms big enough to walk round it). */
    private void tableSet(int chairs) {
        if (w < 4 || d < 4) { wall(Block.TABLE, 1); return; }
        int cx = x0 + w / 2, cz = z0 + d / 2;
        if (!place(cx, cz, Block.TABLE, null)) return;
        if (w >= 6 && d >= 4) place(cx - 1, cz, Block.TABLE, null);
        int[][] around = {{1, 0}, {0, 1}, {0, -1}, {-2, 0}, {-1, 1}, {-1, -1}};
        for (int i = 0, n = 0; i < around.length && n < chairs; i++) if (place(cx + around[i][0], cz + around[i][1], Block.CHAIR, null)) n++;
    }

    /** A desk against a wall with a chair and, sometimes, a terminal on it; the desk is searchable. */
    private void desks(int count, boolean computers, boolean document) {
        int placed = 0;
        for (int i = 0; i < wallCells.size() && placed < count; i++) {
            int[] c = wallCells.get(i);
            if (!place(c[0], c[1], Block.DESK, "office")) continue;
            wallCells.remove(i); i--; placed++;
            if (computers && r.chance(0.6) && g.air(c[0], y + 2, c[1])) g.set(c[0], y + 2, c[1], Block.COMPUTER);
            for (int[] dd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (place(c[0] + dd[0], c[1] + dd[1], Block.CHAIR, null)) break;
            if (document && placed == 1) g.document("doc", c[0], y + 1, c[1]);
        }
    }

    /** Beds along the walls, each two cells long where there is room. */
    private void beds(int count, int block) {
        int placed = 0;
        for (int i = 0; i < wallCells.size() && placed < count; i++) {
            int[] c = wallCells.get(i);
            if (!place(c[0], c[1], block, null)) continue;
            wallCells.remove(i); i--; placed++;
            for (int[] dd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = c[0] + dd[0], nz = c[1] + dd[1];
                if (in(nx, nz) && (nx == x0 || nz == z0 || nx == x0 + w - 1 || nz == z0 + d - 1) && place(nx, nz, block, null)) break;
            }
        }
    }

    /** Free-standing rows with aisles between them and a cross aisle in the middle (shop shelving, racking, benches). */
    private int rows(int block, String loot, int stack, float fill) {
        boolean alongX = w >= d;
        int len = alongX ? w : d, across = alongX ? d : w, n = 0;
        if (across < 5 || len < 6) return 0;
        boolean shelving = block == Block.SHELF;
        for (int j = 2; j <= across - 3; j += 3) {
            // 0.7: each run is broken into short lengths with a way through between them, and no two lengths are alike
            int since = 0, gapAt = 3 + r.nextInt(3);
            for (int i = 2; i <= len - 3; i++) {
                if (len >= 12 && Math.abs(i - len / 2) < 1) { since = 0; continue; }      // the cross aisle
                if (since >= gapAt) { since = 0; gapAt = 3 + r.nextInt(3); continue; }      // a gap in the run
                if (!r.chance(fill)) continue;
                int x = alongX ? x0 + i : x0 + j, z = alongX ? z0 + j : z0 + i;
                int put = block;
                String what = loot;
                if (shelving) {
                    // what years of other people have left: a few units still with something on them, most stripped to the
                    // frame, some pulled over and lying as a heap of what was on them, here and there a stack of stock nobody carried off
                    float roll = r.nextFloat();
                    boolean stocked = roll < 0.3f && same(x, z, Block.SHELF) == 0;
                    if (stocked) put = Block.SHELF;
                    else if (roll < 0.72f) { put = Block.RACK; what = null; }
                    else if (roll < 0.84f) { put = Block.CRATE; what = r.chance(0.35) ? loot : null; if (what == null) put = Block.RUBBLE; }
                    else if (roll < 0.92f) { put = Block.COUNTER; what = null; }
                    else { since++; continue; }
                }
                if (put == Block.RUBBLE) { if (canPlace(x, z)) { g.set(x, y + 1, z, Block.DEBRIS_PILE); solid[idx(x, z)] = true; free--; pieces++; statPieces++; n++; } since++; continue; }
                if (!place(x, z, put, what)) continue;
                n++; since++;
                if (put == block && !shelving || put == Block.RACK) for (int k = 2; k <= stack; k++) if (g.air(x, y + k, z) && r.chance(0.6)) g.set(x, y + k, z, put);
            }
        }
        return n;
    }

    /** A piece that is only there to be a room's: placed against a wall, never a container. */
    private void decor(int block, int count) { wall(block, count, null); }

    private String loot(String fallback) { return bias != null ? bias : fallback; }

    /**
     * 0.6: a room is furnished like a room somebody used, not like a list was ticked off. After the pieces a room of
     * this purpose must have, the walls are filled out with what such a room accumulates - chairs, a second cupboard,
     * shelving, stores - until a little over half its wall is taken. Doorways, stairs and the way between them stay
     * clear (every piece still has to leave the floor connected), and the extra pieces are mostly not containers,
     * so a denser room is not a richer one.
     */
    private void fillOut(String type, Mat mat, int wallsAtStart) {
        int[] extra; String loot;
        float want;
        switch (type) {
            case "living", "lounge", "dining", "study", "library", "bedroom", "foyer" -> { extra = new int[]{Block.CHAIR, Block.PLANT, Block.NIGHTSTAND, Block.TABLE, Block.FLOOR_LAMP, Block.SOFA, Block.CHAIR, Block.BOOKSHELF}; loot = "residential"; want = 0.5f; }
            case "kitchen", "pantry", "laundry" -> { extra = new int[]{Block.COUNTER, Block.CHAIR, Block.COUNTER, Block.RACK, Block.TABLE, Block.TRASH}; loot = "food"; want = 0.55f; }
            case "bathroom", "wc", "shower", "decon", "cell", "holding", "interview" -> { return; }
            case "office", "meeting", "briefing", "command", "archive", "radio", "mailroom", "security", "booking", "frontdesk", "breakroom", "control", "server" -> { extra = new int[]{Block.CHAIR, Block.PLANT, Block.CHAIR, Block.RACK, Block.TABLE, Block.FILING, Block.TRASH}; loot = "office"; want = 0.55f; }
            case "store", "bar", "pharmacy", "supermarket", "cafe", "concourse", "lobby", "visiting", "mess" -> { extra = new int[]{Block.CHAIR, Block.COUNTER, Block.PEW, Block.PLANT, Block.TABLE, Block.DEBRIS_PILE, Block.RACK}; loot = "trash"; want = 0.4f; }
            case "lab", "specimen", "containment", "infirmary", "coldroom" -> { extra = new int[]{Block.COUNTER, Block.CHAIR, Block.RACK, Block.MACHINE, Block.TRASH}; loot = "lab"; want = 0.48f; }
            case "barn", "feed", "greenhouse", "tack" -> { extra = new int[]{Block.HAY, Block.BARREL, Block.CRATE, Block.HAY}; loot = "farm"; want = 0.4f; }
            default -> { extra = mat.homely ? new int[]{Block.CRATE, Block.CHAIR, Block.RACK, Block.BARREL, Block.DEBRIS_PILE} : new int[]{Block.CRATE, Block.BARREL, Block.RACK, Block.BARREL, Block.PIPE, Block.TIRE, Block.DEBRIS_PILE}; loot = loot("crate"); want = w * d > 150 ? 0.28f : 0.45f; }
        }
        int target = Math.round(wallsAtStart * want), used = wallsAtStart - wallCells.size(), guard = 0;
        while (used < target && !wallCells.isEmpty() && guard++ < 64) {
            int block = extra[r.nextInt(extra.length)];
            boolean box = Block.ALL[block].is(Block.CONTAINER);
            // a container among the extras is the exception, and is as poor as the room's rubbish
            if (box && !r.chance(0.3)) block = mat.homely ? Block.CHAIR : mat.bare ? Block.BARREL : Block.CHAIR;
            int n = wall(block, 1, Block.ALL[block].is(Block.CONTAINER) ? loot : null);
            if (n == 0) break;
            used += n;
        }
        statCells += wallsAtStart;
    }

    // ------------------------------------------------------------------ 1.0: what makes this room this room
    /** Rooms dressed, and things put in them by {@link #decorate} (tuning, gates). */
    public static int statDecorRooms, statDecor;

    /**
     * After the furniture: the things that make a room this one and not the one next door. Something on the walls -
     * pictures and a clock in a parlour, a calendar and a notice board in an office, a cracked mirror over a basin;
     * something that was dropped or spilt on the floor; and one or two pieces from the prop library that the purpose of
     * the room suggests, each a single thing, stood against a wall where it leaves the room walkable. What is chosen,
     * and how much, is the draw of that room alone: no two rooms of a building come out alike.
     */
    private void decorate(MapData.Room room, Mat mat) {
        if (mat.bare || w < 2 || d < 2) return;
        String[] walls, floor, pieces;
        switch (room.type) {
            case "living", "lounge", "dining", "foyer", "study", "library" -> { walls = new String[]{"painting_landscape", "painting_small", "photo_frames", "mirror_gilt", "clock_wall", "painting_portrait", "painting_lady", "shelf_wall", "lamp_wall_off", "trophy_head"}; floor = new String[]{"papers_floor", "rug_round", "candles_floor"}; pieces = new String[]{"grandfather_clock", "plant_palm", "plant_dead", "coat_stand", "table_side", "globe", "umbrella_stand", "firewood", "pedestal_urn", "statue_bust", "gramophone", "music_stand", "easel"}; }
            case "bedroom", "nursery" -> { walls = new String[]{"painting_small", "photo_frames", "mirror_broken", "cross_wall", "calendar", "coat_hooks", "lamp_wall_off", "shelf_wall"}; floor = new String[]{"papers_floor", "bedroll", "rug_round"}; pieces = new String[]{"trunk", "coat_stand", "rocking_horse", "toy_blocks", "plant_dead", "table_side", "boxes_cardboard"}; }
            case "kitchen", "pantry", "laundry", "canteen", "mess" -> { walls = new String[]{"shelf_wall", "clock_wall", "calendar", "coat_hooks", "fire_extinguisher", "pipes_wall"}; floor = new String[]{"glass_shards", "blood_old", "papers_floor"}; pieces = new String[]{"sacks", "mop_bucket", "boxes_cardboard", "washing_tub", "barrel_wine"}; }
            case "bathroom", "wc", "shower" -> { walls = new String[]{"mirror_broken", "pipes_wall", "first_aid_box", "coat_hooks"}; floor = new String[]{"blood_old", "glass_shards"}; pieces = new String[]{"mop_bucket"}; }
            case "office", "meeting", "briefing", "command", "archive", "radio", "mailroom", "security", "booking", "frontdesk", "breakroom", "control", "server", "records", "evidence" -> { walls = new String[]{"notice_board", "calendar", "map_wall", "clock_wall", "missing_posters", "phone_wall", "fuse_box", "fire_extinguisher", "blackboard", "intercom", "sign_exit"}; floor = new String[]{"papers_floor", "glass_shards"}; pieces = new String[]{"water_cooler", "plant_dead", "coat_stand", "boxes_cardboard", "photocopier", "vending_machine"}; }
            case "store", "bar", "pharmacy", "supermarket", "cafe", "concourse", "lobby", "visiting", "stock" -> { walls = new String[]{"notice_board", "missing_posters", "clock_wall", "sign_exit", "fire_extinguisher", "calendar", "phone_wall"}; floor = new String[]{"papers_floor", "glass_shards", "rubble_small"}; pieces = new String[]{"magazine_rack", "vending_machine", "mannequin", "boxes_cardboard", "plant_dead", "shopping_cart"}; }
            case "lab", "specimen", "containment", "infirmary", "coldroom" -> { walls = new String[]{"xray_lightbox", "first_aid_box", "clock_wall", "sign_exit", "fire_extinguisher", "notice_board"}; floor = new String[]{"blood_old", "glass_shards", "papers_floor"}; pieces = new String[]{"iv_stand", "wheelchair", "cart_medicine", "drum_hazard"}; }
            default -> { walls = new String[]{"notice_board", "pipes_wall", "fuse_box", "vent_grille", "fire_extinguisher", "coat_hooks", "valve_wheel"}; floor = new String[]{"papers_floor", "rubble_small", "blood_old"}; pieces = new String[]{"boxes_cardboard", "crate_stack", "drum_oil", "ladder_lean", "sacks"}; }
        }
        statDecorRooms++;
        // on the walls: about one thing to every dozen cells of wall, hung at eye height on a plain stretch of it
        int want = 1 + (w + d) / 6, placed = 0;
        java.util.Set<Long> hung = new java.util.HashSet<>();
        for (int tries = 0; tries < 28 && placed < want; tries++) {
            int side = r.nextInt(4), i = r.nextInt(w), k = r.nextInt(d);
            int x = side == 1 ? x0 + w - 1 : side == 3 ? x0 : x0 + i, z = side == 0 ? z0 : side == 2 ? z0 + d - 1 : z0 + k;
            int wx = x + (side == 1 ? 1 : side == 3 ? -1 : 0), wz = z + (side == 2 ? 1 : side == 0 ? -1 : 0);
            boolean plain = true;
            for (int h = 2; h <= 3; h++) { int id = g.get(wx, y + h, wz); plain &= id != 0 && Block.ALL[id].shape == absentialis.world.Shapes.CUBE && Block.ALL[id].opaque() && !Block.ALL[id].is(Block.DOOR) && !Block.ALL[id].is(Block.CONTAINER); }
            if (!plain || !g.air(x, y + 3, z) || !hung.add(key(x, 0, z) * 4 + side)) continue;
            // (nothing either side of it already: two pictures are not hung touching)
            if (hung.contains(key(x + (side % 2 == 0 ? 1 : 0), 0, z + (side % 2 == 0 ? 0 : 1)) * 4 + side) || hung.contains(key(x - (side % 2 == 0 ? 1 : 0), 0, z - (side % 2 == 0 ? 0 : 1)) * 4 + side)) continue;
            String name = walls[r.nextInt(walls.length)];
            absentialis.world.gen.PropLib.Def pd = absentialis.world.gen.PropLib.get(name);
            if (pd == null || pd.w != 1 || pd.d != 1) continue;
            if (g.prop(name, x, y + 3, z, side) != null) { placed++; statDecor++; }
        }
        // on the floor: what was dropped, where it fell
        if (r.chance(0.55)) {
            String name = floor[r.nextInt(floor.length)];
            absentialis.world.gen.PropLib.Def pd = absentialis.world.gen.PropLib.get(name);
            int x = x0 + (w > 2 ? 1 + r.nextInt(w - 2) : 0), z = z0 + (d > 2 ? 1 + r.nextInt(d - 2) : 0);
            if (pd != null && pd.w == 1 && pd.d == 1 && pd.collide == absentialis.world.gen.PropLib.NONE && g.air(x, y + 1, z) && g.prop(name, x, y + 1, z, r.nextInt(4)) != null) statDecor++;
        }
        // one or two things of its own, against a wall, each leaving the room walkable
        int things = w * d >= 24 ? 2 : w * d >= 9 ? 1 : 0;
        for (int n = 0; n < things; n++) {
            String name = pieces[r.nextInt(pieces.length)];
            absentialis.world.gen.PropLib.Def pd = absentialis.world.gen.PropLib.get(name);
            if (pd == null || pd.w != 1 || pd.d != 1 || pd.collide == absentialis.world.gen.PropLib.NONE) continue;
            for (int i = 0; i < wallCells.size(); i++) {
                int[] c = wallCells.get(i);
                boolean tall = true;
                for (int h = 1; h <= pd.h; h++) tall &= g.air(c[0], y + h, c[1]);
                if (!tall || !place(c[0], c[1], Block.BARREL, null)) continue;
                wallCells.remove(i);
                // the cell is held (and the room still walks): the barrel that held it is taken away and the thing stood there, its back to the wall
                g.set(c[0], y + 1, c[1], 0);
                int side = c[1] == z0 ? 0 : c[0] == x0 + w - 1 ? 1 : c[1] == z0 + d - 1 ? 2 : 3;
                if (g.prop(name, c[0], y + 1, c[1], side) != null) statDecor++;
                break;
            }
        }
    }

    // ------------------------------------------------------------------ what each room holds
    private void furnish(String type, Mat mat) {
        switch (type) {
            case "living" -> { wall(Block.SOFA, 2); wall(Block.TV, 1); wall(Block.BOOKSHELF, 1, "residential"); wall(Block.CABINET, 1, "residential"); decor(Block.FLOOR_LAMP, 1); decor(Block.PLANT, 1); if (w >= 5 && d >= 5) tableSet(2); }
            case "lounge" -> { wall(Block.SOFA, 3); wall(Block.BOOKSHELF, 1, "residential"); wall(Block.CABINET, 1, "residential"); decor(Block.PLANT, 2); decor(Block.FLOOR_LAMP, 1); if (w >= 5 && d >= 5) center(Block.TABLE, null); }
            case "dining" -> { tableSet(4); wall(Block.CABINET, 1, "residential"); decor(Block.PLANT, 1); }
            case "kitchen" -> { wall(Block.COUNTER, 3); wall(Block.STOVE, 1); wall(Block.SINK, 1); wall(Block.FRIDGE, 1, "food"); wall(Block.CABINET, 2, "food"); }
            case "pantry" -> { wall(Block.SHELF, 1, "food"); decor(Block.RACK, 2); wall(Block.CRATE, 1, "food"); wall(Block.BARREL, 1); }
            case "bedroom" -> { beds(w * d >= 20 ? 2 : 1, Block.BED); decor(Block.NIGHTSTAND, 1); wall(Block.CABINET, 1, "residential"); decor(Block.FLOOR_LAMP, 1); if (r.chance(0.4)) wall(Block.BOOKSHELF, 1, "residential"); if (r.chance(0.12)) wall(Block.SAFE, 1, "safe"); }
            case "bathroom" -> { wall(Block.TOILET, 1); wall(Block.SINK, 1); wall(Block.MED_CABINET, 1, "medical_home"); }
            case "wc" -> { wall(Block.TOILET, 2); wall(Block.SINK, 1); }
            case "shower" -> { wall(Block.SINK, 2); wall(Block.TOILET, 1); wall(Block.LOCKER, 1, "locker"); }
            case "laundry" -> { wall(Block.WASHER, 2); wall(Block.SINK, 1); wall(Block.CABINET, 1, "residential"); }
            case "study" -> { desks(1, true, r.chance(0.3)); wall(Block.BOOKSHELF, 2, "office"); wall(Block.SOFA, 1); decor(Block.FLOOR_LAMP, 1); decor(Block.PLANT, 1); }
            case "library" -> { wall(Block.BOOKSHELF, 5, "office"); wall(Block.SOFA, 2); decor(Block.FLOOR_LAMP, 2); desks(1, false, r.chance(0.3)); if (w >= 5 && d >= 5) tableSet(3); }
            case "foyer", "lobby" -> { wall(mat.homely ? Block.SOFA : Block.PEW, 2); decor(Block.PLANT, 2); wall(Block.TRASH, 1, "trash"); if (mat.homely) decor(Block.NIGHTSTAND, 1); else wall(Block.MAP_BOARD, 1); }
            case "frontdesk" -> { wall(Block.COUNTER, 3); desks(1, true, false); wall(Block.FILING, 1, "office"); }
            case "mailroom" -> { wall(Block.FILING, 2, "office"); wall(Block.CABINET, 1, "residential"); }
            case "storage", "closet" -> { wall(Block.CRATE, 1, loot("crate")); wall(Block.SHELF, 1, loot("residential")); decor(Block.RACK, 1); decor(Block.CRATE, 1); if (r.chance(0.5)) wall(Block.BARREL, 1); }
            case "utility", "pump" -> { wall(Block.MACHINE, 2); wall(Block.PIPE, 1); wall(Block.TOOLBOX, 1, "industrial"); if ("pump".equals(type)) wall(Block.WATER_TANK, 1); }
            case "switchgear" -> { wall(Block.MACHINE, 3); wall(Block.GENERATOR, 1); wall(Block.TOOLBOX, 1, "industrial"); }
            case "garage" -> { wall(Block.TOOLBOX, 1, "industrial"); wall(Block.SHELF, 1, "industrial"); decor(Block.RACK, 1); wall(Block.BARREL, 1); wall(Block.TIRE, 2); wall(Block.COUNTER, 1); }
            case "store", "bar" -> { if (rows(Block.SHELF, loot("store"), 1, 0.85f) == 0) { wall(Block.SHELF, 1, loot("store")); decor(Block.RACK, 2); } wall(Block.COUNTER, 3); wall(Block.FRIDGE, 1, "food"); wall(Block.CABINET, 1, "store"); }
            case "supermarket" -> { rows(Block.SHELF, "food", 2, 0.9f); wall(Block.FRIDGE, 8, "food"); wall(Block.COUNTER, 6); wall(Block.TRASH, 2, "trash"); }
            case "pharmacy" -> { if (rows(Block.SHELF, "medical", 1, 0.8f) == 0) { wall(Block.SHELF, 1, "medical"); decor(Block.RACK, 1); } wall(Block.MED_CABINET, 2, "medical"); wall(Block.COUNTER, 3); decor(Block.PLANT, 1); }
            case "stock" -> { if (rows(Block.SHELF, loot("store"), 2, 0.8f) == 0) { wall(Block.SHELF, 1, loot("store")); decor(Block.RACK, 2); } wall(Block.CRATE, 2, loot("crate")); decor(Block.CRATE, 2); wall(Block.BARREL, 1); }
            case "coldroom" -> { wall(Block.FRIDGE, 5, "food"); wall(Block.CRATE, 2, "food"); }
            case "office" -> { desks(Math.max(1, w * d / 14), true, r.chance(0.25)); wall(Block.FILING, 1, "office"); wall(Block.BOOKSHELF, 1, "office"); decor(Block.PLANT, 1); wall(Block.TRASH, 1, "trash"); }
            case "meeting", "briefing" -> { tableSet(6); wall(Block.MAP_BOARD, 1); wall(Block.CHAIR, 2); }
            case "command" -> { tableSet(4); wall(Block.MAP_BOARD, 2); wall(Block.RADIO_SET, 1, "tech"); desks(1, true, true); wall(Block.FILING, 1, "office"); }
            case "radio" -> { wall(Block.RADIO_SET, 2, "tech"); desks(1, false, r.chance(0.4)); }
            case "server" -> { int n = wall(Block.MACHINE, 4); if (n == 0) wall(Block.COMPUTER, 1); wall(Block.RADIO_SET, 1, "tech"); }
            case "control" -> { wall(Block.CONSOLE, 5); desks(1, true, true); wall(Block.FILING, 1, "office"); }
            case "archive", "evidence" -> { wall(Block.FILING, 3, "office"); wall(Block.SHELF, 1, "office"); decor(Block.RACK, 2); desks(1, false, false); if ("evidence".equals(type)) { wall(Block.LOCKER, 2, "military"); wall(Block.SAFE, 1, "safe"); } }
            case "security" -> { desks(1, true, false); wall(Block.LOCKER, 2, "military_personal"); wall(Block.FILING, 1, "office"); }
            case "guardroom" -> { desks(1, false, false); wall(Block.LOCKER, 2, "military_personal"); wall(Block.WEAPON_RACK, 1, "weapons"); }
            case "locker" -> { wall(Block.LOCKER, 5, "locker"); wall(Block.PEW, 1); }
            case "breakroom", "cafe", "mess", "visiting" -> { tableSet(4); wall(Block.COUNTER, 2); wall(Block.FRIDGE, 1, "food"); wall(Block.SOFA, 1); if ("mess".equals(type)) rows(Block.TABLE, null, 1, 0.5f); }
            case "lab" -> { rows(Block.COUNTER, null, 1, 0.6f); wall(Block.MED_CABINET, 2, "medical_rare"); wall(Block.FILING, 1, "lab"); desks(1, true, r.chance(0.5)); wall(Block.BIOHAZARD, 1); }
            case "specimen" -> { wall(Block.MED_CABINET, 2, "medical_rare"); wall(Block.SHELF, 1, "lab"); decor(Block.RACK, 1); wall(Block.COUNTER, 2); wall(Block.FRIDGE, 1, "food"); }
            case "decon" -> { wall(Block.SINK, 2); wall(Block.LOCKER, 2, "locker"); }
            case "containment" -> { center(Block.HOSPITAL_BED, null); wall(Block.MACHINE, 3); wall(Block.MED_CABINET, 2, "medical_rare"); wall(Block.BIOHAZARD, 2); wall(Block.BODYBAG, 2, "body"); }
            case "infirmary" -> { beds(3, Block.HOSPITAL_BED); wall(Block.MED_CABINET, 2, "medical"); wall(Block.SINK, 1); }
            case "dorm" -> { beds(Math.max(2, w * d / 8), Block.BED); wall(Block.LOCKER, 3, "military_personal"); }
            case "armory" -> { wall(Block.WEAPON_RACK, 2, "weapons"); wall(Block.CRATE_MIL, 3, "military"); wall(Block.LOCKER, 2, "military"); }
            case "depot" -> { rows(Block.SHELF, "military", 2, 0.6f); wall(Block.CRATE_MIL, 5, "military"); wall(Block.BARREL, 3); }
            case "cell", "holding" -> { beds(1, Block.BED); wall(Block.TOILET, 1); }
            case "interview" -> { tableSet(2); }
            case "booking" -> { desks(1, true, false); wall(Block.FILING, 2, "office"); wall(Block.PEW, 1); }
            case "workshop" -> { wall(Block.MACHINE, 3); wall(Block.TOOLBOX, 2, "industrial"); wall(Block.SHELF, 1, "industrial"); decor(Block.RACK, 1); wall(Block.COUNTER, 2); wall(Block.BARREL, 2); if (w >= 6 && d >= 6) center(Block.TABLE, null); }
            case "tools", "tack" -> { wall(Block.TOOLBOX, 2, "industrial"); wall(Block.SHELF, 1, loot("industrial")); decor(Block.RACK, 1); wall(Block.BARREL, 1); wall(Block.COUNTER, 1); }
            case "hall" -> { rows(Block.SHELF, loot("industrial"), 3, 0.7f); wall(Block.CRATE, Math.max(2, w * d / 60), loot("crate")); decor(Block.CRATE, 3); wall(Block.BARREL, 4); wall(Block.MACHINE, 2); wall(Block.TIRE, 2); }
            case "hangar" -> { wall(Block.TOOLBOX, 3, "industrial"); wall(Block.CRATE, 5, loot("crate")); wall(Block.BARREL, 5); wall(Block.MACHINE, 2); }
            case "turbine" -> { rows(Block.GENERATOR, null, 2, 0.75f); wall(Block.MACHINE, 6); wall(Block.CONSOLE, 2); wall(Block.TOOLBOX, 2, "industrial"); wall(Block.PIPE, 4); }
            case "barn", "feed" -> { wall(Block.HAY, Math.max(4, w * d / 14)); wall(Block.CRATE, 2, "farm"); wall(Block.BARREL, 2); if ("barn".equals(type)) rows(Block.HAY, null, 2, 0.3f); }
            case "greenhouse" -> { rows(Block.CRATE, "farm", 1, 0.25f); wall(Block.TABLE, 2); wall(Block.BARREL, 1); }
            case "concourse" -> { rows(Block.PEW, null, 1, 0.5f); wall(Block.COUNTER, 5); wall(Block.TRASH, 2, "trash"); wall(Block.MAP_BOARD, 1); }
            case "baggage" -> { rows(Block.SHELF, "residential", 1, 0.5f); wall(Block.CRATE, 2, "residential"); decor(Block.CRATE, 2); wall(Block.PEW, 2); }
            default -> { wall(Block.CRATE, 1, loot("crate")); wall(Block.CABINET, 1, "residential"); }
        }
    }

    // ------------------------------------------------------------------ lighting
    /** Ceiling lights on a grid: every lit room is lit throughout, and the lamps depend on district power. */
    private void light(MapData.Room room, float lit, Rng rng) {
        // 0.6: every room has its light fitting. Whether it works is the district's luck; a room whose lamp is dead
        // still has the lamp (0.5 left unlit rooms with a bare ceiling, as if nobody had ever lit them)
        boolean on = rng.chance(lit);
        int rw = room.x1 - room.x0 + 1, rd = room.z1 - room.z0 + 1;
        int nx = Math.max(1, rw / 9), nz = Math.max(1, rd / 9);
        for (int i = 0; i < nx; i++) for (int k = 0; k < nz; k++) {
            int lx = room.x0 + (2 * i + 1) * rw / (2 * nx), lz = room.z0 + (2 * k + 1) * rd / (2 * nz);
            for (int ly = room.y + 3; ly < Math.min(World.H - 2, room.y + 17); ly++) {
                if (!g.air(lx, ly, lz)) break;
                int above = g.get(lx, ly + 1, lz);
                if (above != 0 && Block.ALL[above].solid() && Block.ALL[above].shape == 0) { if (on) g.lamp(lx, ly, lz); else if (i == nx / 2 && k == nz / 2) g.set(lx, ly, lz, Block.LAMP_OFF); break; }
            }
        }
    }
}
