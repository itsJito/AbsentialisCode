package absentialis.world.gen;

import static absentialis.world.gen.Gen.G;

import absentialis.world.Block;
import absentialis.world.MapData;

/**
 * 1.1: Central Police Headquarters, built by hand. Until now the station was an office block with a badge on it; this
 * is a station - and a particular one. The plan is the old municipal art museum that became a police department: a
 * main hall three storeys high with a gallery round it and the reception desk in the middle of the floor, a west wing
 * and an east wing off it, each a corridor with rooms down both sides and a staircase at the far end, and a basement
 * under all of it.
 *
 *   Ground floor ... main hall; west: reception, the operations room, the west office, the safety deposit room, the
 *                    dark room, records, evidence; east: the press room, the watchman's room, the break room, the
 *                    east office, interrogation and the observation room behind its glass; lockers, the boiler room.
 *   First floor .... the gallery; west: the waiting room, the library, the S.T.A.R.S. office, the linen room, the
 *                    showers; east: the art room, the chief's office and his private collection, the lounge, storage;
 *                    the clock tower hall, files, the way up to the roof and the helipad.
 *   Basement ....... the parking garage, the jail, the firing range, the kennel, the morgue, the generator room.
 *
 * In this game's format: no room is lower than five blocks, no corridor narrower than two, every stair is a stair.
 * The S.T.A.R.S. office always holds a Desert Eagle and a full box of magnum rounds (map sites of kind "item").
 */
final class LandmarksPolice {
    private LandmarksPolice() {}

    /** The building's interior box, in cells: 60 across, 36 deep (walls one cell outside it). */
    static final int W = 60, D = 36;

    static boolean fits(Site s) { return s.A >= W + 4 && s.B >= D + 16; }

    static MapData.Building build(LandmarkBuilder lb, Site s, MapData.Landmark lm) {
        final int A = s.A, B = s.B;
        final int OA = (A - W) / 2, OB = 13;
        final int H = 5, F1 = G + 7, TOP = G + 12, ROOF = G + 13, TALL = 12, BY = G - 7;       // the basement has a ceiling of its own under the ground floor's slab
        final int ST = Block.STONE_BLOCK, PG = Block.PAINT_GREEN, WD = Block.WOOD_DARK, PL = Block.PLASTER, CL = Block.CEILING_TILE, PQ = Block.PARQUET, OP = Block.OFFICE_PANEL,
                CC = Block.CONCRETE, CF = Block.CONCRETE_FLOOR, TW = Block.TILE_WHITE, TG = Block.TILE_GRAY, LN = Block.LINOLEUM, CR = Block.CARPET_RED, CB = Block.CARPET_BLUE, CG = Block.CARPET_GRAY;
        s.ground(Block.SIDEWALK);
        Blueprint bp = new Blueprint(s, lm, "lm_police", lm.name, "civic");
        // everything below is written in the building's own cells (u across, v back from the front wall)
        final B b = new B(bp, OA, OB);

        // ================================================================ basement
        b.room("stairs", -1, 0, 30, 7, 35, BY, H, CC, CF, CC);
        b.room("stairs", -1, 52, 30, 59, 35, BY, H, CC, CF, CC);
        b.room("corridor", -1, 9, 15, 10, 35, BY, H, CC, CF, CC).circulation = true;
        b.room("corridor", -1, 49, 15, 50, 35, BY, H, CC, CF, CC).circulation = true;
        b.room("corridor", -1, 12, 23, 47, 24, BY, H, CC, CF, CC).circulation = true;
        MapData.Room range = b.room("firing_range", -1, 12, 26, 28, 35, BY, H, CC, CF, CC);
        MapData.Room kennel = b.room("kennel", -1, 30, 26, 47, 35, BY, H, CC, CF, CC);
        MapData.Room jail = b.room("jail", -1, 12, 9, 28, 21, BY, H, Block.BRICK_WHITE, CF, CC);
        MapData.Room garage = b.room("garage", -1, 30, 0, 47, 21, BY, H, CC, Block.ASPHALT, CC);
        MapData.Room morgue = b.room("morgue", -1, 0, 15, 7, 28, BY, H, TW, TG, CC);
        MapData.Room genRoom = b.room("generator_room", -1, 52, 15, 59, 28, BY, H, CC, CF, CC);

        // ================================================================ ground floor
        b.room("stairs", 0, 0, 30, 7, 35, G, H, PG, LN, CL);
        b.room("stairs", 0, 52, 30, 59, 35, G, H, PG, LN, CL);
        b.room("corridor", 0, 9, 0, 10, 35, G, H, PG, CR, CL).circulation = true;
        b.room("corridor", 0, 49, 0, 50, 35, G, H, PG, CR, CL).circulation = true;
        b.room("corridor", 0, 12, 23, 47, 24, G, H, PG, CR, CL).circulation = true;
        MapData.Room reception = b.room("reception", 0, 12, 0, 19, 7, G, H, WD, PQ, CL);
        MapData.Room ops = b.room("operations", 0, 12, 9, 19, 21, G, H, OP, CB, CL);
        MapData.Room westOffice = b.room("west_office", 0, 0, 0, 7, 13, G, H, OP, PQ, CL);
        MapData.Room deposit = b.room("safety_deposit", 0, 0, 15, 7, 21, G, H, PL, TG, CL);
        MapData.Room dark = b.room("dark_room", 0, 0, 23, 7, 28, G, H, Block.BRICK_BROWN, LN, CL);
        MapData.Room records = b.room("records", 0, 12, 26, 19, 35, G, H, PL, LN, CL);
        MapData.Room evidence = b.room("evidence", 0, 21, 26, 28, 35, G, H, PL, LN, CL);
        MapData.Room lockers = b.room("locker_room", 0, 30, 26, 38, 35, G, H, TW, TG, CL);
        MapData.Room boiler = b.room("boiler_room", 0, 40, 26, 47, 35, G, H, Block.BRICK_RED, CF, CC);
        MapData.Room press = b.room("press_room", 0, 40, 0, 47, 7, G, H, WD, CG, CL);
        MapData.Room watch = b.room("watchman", 0, 40, 9, 47, 13, G, H, PL, PQ, CL);
        MapData.Room breakRoom = b.room("break_room", 0, 40, 15, 47, 21, G, H, PL, LN, CL);
        MapData.Room eastOffice = b.room("east_office", 0, 52, 0, 59, 13, G, H, OP, PQ, CL);
        MapData.Room interro = b.room("interrogation", 0, 52, 15, 59, 21, G, H, CC, TG, CL);
        MapData.Room observe = b.room("observation", 0, 52, 23, 59, 28, G, H, WD, CG, CL);

        // ================================================================ first floor
        b.room("stairs", 1, 0, 30, 7, 35, F1, H, PG, LN, CL);
        b.room("stairs", 1, 52, 30, 59, 35, F1, H, PG, LN, CL);
        b.room("corridor", 1, 9, 0, 10, 35, F1, H, PG, CR, CL).circulation = true;
        b.room("corridor", 1, 49, 0, 50, 35, F1, H, PG, CR, CL).circulation = true;
        b.room("corridor", 1, 12, 23, 47, 24, F1, H, PG, CR, CL).circulation = true;
        MapData.Room waiting = b.room("waiting_room", 1, 12, 0, 19, 7, F1, H, WD, PQ, CL);
        MapData.Room library = b.room("library", 1, 12, 9, 19, 21, F1, H, WD, CR, Block.BOARDS_C);
        MapData.Room stars = b.room("stars_office", 1, 0, 0, 7, 13, F1, H, OP, CB, CL);
        MapData.Room linen = b.room("linen_room", 1, 0, 15, 7, 21, F1, H, PL, LN, CL);
        MapData.Room shower = b.room("shower_room", 1, 0, 23, 7, 28, F1, H, TW, TG, CL);
        MapData.Room files = b.room("file_room", 1, 12, 26, 19, 35, F1, H, PL, LN, CL);
        MapData.Room clock = b.room("clock_hall", 1, 21, 26, 38, 35, F1, H, ST, Block.BOARDS_C, Block.BOARDS_C);
        MapData.Room roofWay = b.room("roof_access", 1, 40, 26, 47, 35, F1, H, CC, CF, CC);
        MapData.Room art = b.room("art_room", 1, 40, 0, 47, 7, F1, H, PL, PQ, CL);
        MapData.Room chief = b.room("chief_office", 1, 40, 9, 47, 21, F1, H, WD, CR, Block.BOARDS_C);
        MapData.Room lounge = b.room("lounge", 1, 52, 0, 59, 13, F1, H, WD, CG, CL);
        MapData.Room collection = b.room("collection", 1, 52, 15, 59, 21, F1, H, WD, CR, Block.BOARDS_C);
        MapData.Room storage = b.room("east_storage", 1, 52, 23, 59, 28, F1, H, PL, Block.BOARDS_C, CL);

        // ================================================================ the main hall, last: its walls are its own
        MapData.Room hall = b.room("hall", 0, 21, 0, 38, 21, G, TALL, PL, Block.TILE_CHECKER, CL);
        b.reface(20, -1, 39, 22, G, G + 1, PL, WD);
        b.reface(20, -1, 39, 22, F1 - 2, F1 - 1, PL, WD);
        b.reface(20, -1, 39, 22, TOP - 1, TOP - 1, PL, Block.STONE_CARVED);
        // the gallery: three wide down each side, across the back and across the front, on marble columns
        for (int[] g2 : new int[][]{{21, 19, 38, 21}, {21, 0, 23, 18}, {36, 0, 38, 18}, {24, 0, 35, 2}}) { b.box(g2[0], g2[1], g2[2], g2[3], F1 - 1, F1 - 1, PQ); b.box(g2[0], g2[1], g2[2], g2[3], F1 - 2, F1 - 2, WD); }
        for (int v = 3; v <= 18; v++) { b.put(23, v, F1, Block.IRON_FENCE); b.put(36, v, F1, Block.IRON_FENCE); }
        for (int u = 24; u <= 35; u++) { b.put(u, 2, F1, Block.IRON_FENCE); if (u != 24 && u != 25 && u != 34 && u != 35) b.put(u, 19, F1, Block.IRON_FENCE); }
        for (int u : new int[]{23, 36}) for (int v : new int[]{2, 10, 19}) for (int y = G; y < F1 - 2; y++) b.put(u, v, y, Block.MARBLE);
        // the two flights, one each side of the statue, up to the back gallery
        // (open flights, hard against the side galleries: nothing between them but the floor of the hall)
        for (int u0 : new int[]{24, 34}) {
            b.flight(u0, 12, 0, 1, G, 7, 2, Block.STAIRS_WOOD, WD);
            for (int v = 12; v <= 18; v++) for (int u = u0; u <= u0 + 1; u++) b.prop("stair_carpet", u, v, G + (v - 12), 0, -1);
        }

        // ================================================================ the stairs at the ends of the wings
        // west: up along the back wall going east, down along the front wall going east from the west end
        b.flight(0, 34, 1, 0, G, 7, 2, Block.STAIRS_CONCRETE, CC);
        b.flight(6, 30, -1, 0, BY, 7, 2, Block.STAIRS_CONCRETE, CC);
        for (int u = 0; u <= 6; u++) b.put(u, 33, F1, Block.IRON_FENCE);
        b.flight(59, 34, -1, 0, G, 7, 2, Block.STAIRS_CONCRETE, CC);
        b.flight(53, 30, 1, 0, BY, 7, 2, Block.STAIRS_CONCRETE, CC);
        for (int u = 53; u <= 59; u++) b.put(u, 33, F1, Block.IRON_FENCE);

        // ================================================================ doors and openings
        for (int y : new int[]{BY, G, F1}) {
            b.opening(8, 32, y, 3, 2, false); b.opening(51, 32, y, 3, 2, false);          // stairs <-> corridors
            b.opening(11, 23, y, 3, 2, false); b.opening(48, 23, y, 3, 2, false);        // corridors <-> the back corridor
        }
        // ground floor
        b.door(29, -1, G, Block.DOOR_DOUBLE_WOOD, true); b.door(30, -1, G, Block.DOOR_DOUBLE_WOOD, true);
        b.door(20, 4, G, Block.DOOR_WOOD, false); b.door(11, 4, G, Block.DOOR_WOOD, false);                 // hall -> reception -> west corridor
        b.door(39, 4, G, Block.DOOR_WOOD, false); b.door(48, 4, G, Block.DOOR_WOOD, false);                 // hall -> press room -> east corridor
        b.door(29, 22, G, Block.DOOR_DOUBLE_WOOD, false); b.door(30, 22, G, Block.DOOR_DOUBLE_WOOD, false); // hall -> the back corridor
        b.door(11, 15, G, Block.DOOR_WOOD, false);                                                           // operations
        b.door(8, 3, G, Block.DOOR_WOOD, false); b.door(8, 11, G, Block.DOOR_WOOD, false);                   // west office
        b.door(8, 18, G, Block.DOOR_METAL, false); b.door(8, 25, G, Block.DOOR_WOOD, false);                 // safety deposit, dark room
        b.door(15, 25, G, Block.DOOR_WOOD, false); b.door(24, 25, G, Block.DOOR_METAL, false); b.door(34, 25, G, Block.DOOR_WOOD, false); b.door(43, 25, G, Block.DOOR_METAL, false);
        b.door(48, 11, G, Block.DOOR_WOOD, false); b.door(48, 18, G, Block.DOOR_WOOD, false);                // watchman, break room
        b.door(51, 3, G, Block.DOOR_WOOD, false); b.door(51, 11, G, Block.DOOR_WOOD, false);                 // east office
        b.door(51, 18, G, Block.DOOR_METAL, false); b.door(51, 25, G, Block.DOOR_WOOD, false);               // interrogation, observation
        b.door(43, 36, G, Block.DOOR_METAL, true);                                                           // the boiler room's door to the yard
        b.window(54, 22, 57, 22, G + 1, G + 2, Block.GLASS);                                                 // the glass between interrogation and observation
        // first floor
        b.door(20, 4, F1, Block.DOOR_WOOD, false); b.door(11, 4, F1, Block.DOOR_WOOD, false);                // gallery -> waiting room -> west corridor
        b.door(20, 15, F1, Block.DOOR_DOUBLE_WOOD, false); b.door(20, 16, F1, Block.DOOR_DOUBLE_WOOD, false); b.door(11, 15, F1, Block.DOOR_WOOD, false);   // the library
        b.door(39, 4, F1, Block.DOOR_WOOD, false); b.door(48, 4, F1, Block.DOOR_WOOD, false);                // art room
        b.door(39, 15, F1, Block.DOOR_WOOD, false); b.door(48, 15, F1, Block.DOOR_WOOD, false);              // the chief's office
        b.door(29, 22, F1, Block.DOOR_DOUBLE_WOOD, false); b.door(30, 22, F1, Block.DOOR_DOUBLE_WOOD, false);
        b.door(8, 6, F1, Block.DOOR_WOOD, false); b.door(8, 18, F1, Block.DOOR_WOOD, false); b.door(8, 25, F1, Block.DOOR_WOOD, false);
        b.door(51, 6, F1, Block.DOOR_WOOD, false); b.door(51, 18, F1, Block.DOOR_WOOD, false); b.door(51, 25, F1, Block.DOOR_WOOD, false);
        b.door(15, 25, F1, Block.DOOR_WOOD, false); b.door(29, 25, F1, Block.DOOR_DOUBLE_WOOD, false); b.door(30, 25, F1, Block.DOOR_DOUBLE_WOOD, false); b.door(43, 25, F1, Block.DOOR_METAL, false);
        // basement
        b.door(20, 25, BY, Block.DOOR_METAL, false); b.door(38, 25, BY, Block.DOOR_METAL, false);            // firing range, kennel
        b.door(20, 22, BY, Block.DOOR_METAL, false); b.door(38, 22, BY, Block.DOOR_DOUBLE_METAL, false); b.door(39, 22, BY, Block.DOOR_DOUBLE_METAL, false);   // jail, garage
        b.door(8, 20, BY, Block.DOOR_METAL, false); b.door(51, 20, BY, Block.DOOR_METAL, false);             // morgue, generator room

        // ================================================================ outside
        // faced in pale stone: a dark plinth, a carved band at the first floor and under the parapet
        for (int[] side : new int[][]{{-1, -1, W, -1}, {-1, D, W, D}, {-1, -1, -1, D}, {W, -1, W, D}}) {
            b.reface(side[0], side[1], side[2], side[3], G, ROOF, 0, ST);
            b.reface(side[0], side[1], side[2], side[3], F1 - 2, F1 - 1, 0, Block.STONE_CARVED);
            b.reface(side[0], side[1], side[2], side[3], G, G, 0, Block.STONE_DARK);
        }
        // tall windows on both floors, round the building; three great ones over the doors light the hall
        for (int fl = 0; fl < 2; fl++) {
            int y = fl == 0 ? G + 1 : F1 + 1;
            for (int u = 1; u <= 58; u += 3) {
                boolean partition = false;
                for (int p : new int[]{8, 11, 20, 29, 39, 48, 51}) partition |= p == u || p == u + 1;
                if (partition) continue;
                if (u < 20 || u > 39) b.window(u, -1, u + 1, -1, y, y + 2, Block.GLASS_DIRTY);
                if (!(fl == 0 && u >= 42 && u <= 44)) b.window(u, D, u + 1, D, y, y + 2, Block.GLASS_DIRTY);
            }
            for (int v = 2; v <= 27; v += 4) { if (v == 14 || v == 22) continue; b.window(-1, v, -1, v + 1, y, y + 2, Block.GLASS_DIRTY); b.window(W, v, W, v + 1, y, y + 2, Block.GLASS_DIRTY); }
        }
        for (int u : new int[]{23, 29, 35}) b.window(u, -1, u + 1, -1, F1, TOP - 2, Block.GLASS_DIRTY);
        b.window(26, -1, 26, -1, G + 1, G + 3, Block.GLASS_DIRTY); b.window(33, -1, 33, -1, G + 1, G + 3, Block.GLASS_DIRTY);
        // the roof: one deck over every ceiling (a ceiling is not a roof), a parapet round it
        for (int u = -1; u <= W; u++) for (int v = -1; v <= D; v++) { boolean edge = u == -1 || u == W || v == -1 || v == D; b.put(u, v, ROOF, edge ? Block.STONE_CARVED : Block.CONCRETE_DARK); if (edge) b.put(u, v, ROOF + 1, Block.STONE_CARVED); if (!edge && b.at(u, v, TOP) == 0) b.put(u, v, TOP, Block.CONCRETE_DARK); }
        for (int u = 5; u < W - 4; u += 9) for (int v : new int[]{27, 31}) if (u < 20 || u > 40) b.put(u, v, ROOF + 1, Block.AC_UNIT);
        b.put(14, 4, ROOF + 1, Block.VENT); b.put(15, 4, ROOF + 1, Block.VENT); b.put(8, 18, ROOF + 1, Block.WATER_TANK); b.put(8, 18, ROOF + 2, Block.WATER_TANK);
        // the front is not flat: a pilaster at every bay, a string course over each row of windows, a cornice that stands out
        for (int u : new int[]{-1, 8, 11, 20, 39, 48, 51, W}) for (int y = G; y <= ROOF; y++) { b.put(u, -2, y, y == G ? Block.STONE_DARK : Block.STONE_CARVED); b.put(u, D + 1, y, y == G ? Block.STONE_DARK : Block.STONE_CARVED); }
        for (int v : new int[]{-1, 14, 22, 29, D}) for (int y = G; y <= ROOF; y++) { b.put(-2, v, y, y == G ? Block.STONE_DARK : Block.STONE_CARVED); b.put(W + 1, v, y, y == G ? Block.STONE_DARK : Block.STONE_CARVED); }
        for (int u = -2; u <= W + 1; u++) { b.put(u, -2, ROOF, Block.STONE_CARVED); b.put(u, D + 1, ROOF, Block.STONE_CARVED); b.put(u, -2, ROOF + 1, Block.STONE_DARK); b.put(u, D + 1, ROOF + 1, Block.STONE_DARK); }
        for (int v = -2; v <= D + 1; v++) { b.put(-2, v, ROOF, Block.STONE_CARVED); b.put(W + 1, v, ROOF, Block.STONE_CARVED); b.put(-2, v, ROOF + 1, Block.STONE_DARK); b.put(W + 1, v, ROOF + 1, Block.STONE_DARK); }
        for (int u = -1; u <= W; u++) { b.reface(u, -1, u, -1, G + 4, G + 4, ST, Block.STONE_CARVED); b.reface(u, -1, u, -1, F1 + 4, F1 + 4, ST, Block.STONE_CARVED); }
        // the centre bay stands a block proud of the wings, with a gable over the three great windows
        for (int u = 20; u <= 39; u++) for (int y = G; y <= ROOF; y++) if (b.at(u, -1, y) == ST && (u == 20 || u == 39 || u == 22 || u == 25 || u == 28 || u == 31 || u == 34 || u == 37)) b.put(u, -2, y, y == G ? Block.STONE_DARK : ST);
        for (int k = 0; k < 3; k++) b.box(22 + k * 2, -2, 37 - k * 2, -2, ROOF + 2 + k, ROOF + 2 + k, Block.STONE_CARVED);
        b.prop("statue_figure", 23, -4, G, 0, 1); b.prop("statue_figure", 36, -4, G, 0, 1); b.prop("lantern_wall", 27, -2, G + 2, 0, 1); b.prop("lantern_wall", 32, -2, G + 2, 0, 1);
        b.prop("tapestry", 21, -3, 22, -3, F1, 0, 1); b.prop("tapestry", 37, -3, 38, -3, F1, 0, 1);
        // the portico: steps, four columns, a stone canopy with the blue lamp of a station under it
        b.mass(25, -5, 34, -2, G - 1, G - 1, Block.MARBLE);
        for (int u : new int[]{25, 28, 31, 34}) for (int y = G; y < G + 5; y++) b.put(u, -5, y, Block.MARBLE);
        b.box(25, -5, 34, -2, G + 5, G + 5, Block.STONE_CARVED);
        for (int u = 28; u <= 31; u++) b.put(u, -5, G + 6, u == 28 || u == 31 ? Block.STONE_CARVED : Block.NEON_BLUE);
        // the clock tower over the front of the hall
        b.mass(26, -1, 33, 5, ROOF + 1, ROOF + 9, ST);
        b.box(27, 0, 32, 4, ROOF + 1, ROOF + 8, Block.AIR);
        b.box(25, -2, 34, 6, ROOF + 10, ROOF + 10, Block.STONE_CARVED);
        for (int k = 0; k < 4; k++) b.box(26 + k, -1 + k, 33 - k, 5 - k, ROOF + 11 + k, ROOF + 11 + k, Block.ROOF_SLATE);
        for (int u = 28; u <= 31; u++) for (int y = ROOF + 4; y <= ROOF + 7; y++) { boolean rim = u == 28 || u == 31 || y == ROOF + 4 || y == ROOF + 7; b.put(u, -1, y, rim ? Block.STONE_DARK : Block.MARBLE); }
        b.put(29, -1, ROOF + 6, Block.STONE_DARK); b.put(30, -1, ROOF + 5, Block.STONE_DARK);              // the hands, stopped
        for (int[] c : new int[][]{{26, -1}, {33, -1}, {26, 5}, {33, 5}}) for (int y = ROOF; y <= ROOF + 10; y++) b.put(c[0], c[1], y, Block.STONE_CARVED);
        for (int u = 28; u <= 31; u += 3) for (int y = ROOF + 1; y <= ROOF + 2; y++) b.put(u, -1, y, Block.GLASS_DIRTY);
        b.box(27, -1, 32, -1, ROOF + 3, ROOF + 3, Block.STONE_CARVED); b.box(27, -1, 32, -1, ROOF + 8, ROOF + 8, Block.STONE_CARVED);
        // the hall's skylight: glass in an iron frame over the middle of the floor, and coffering beams either side of it
        for (int u = 26; u <= 33; u++) for (int v = 8; v <= 15; v++) { boolean frame = u == 26 || u == 33 || v == 8 || v == 15 || u == 29 || v == 11; b.put(u, v, TOP, frame ? Block.STEEL_BEAM : Block.GLASS); b.put(u, v, ROOF, u == 26 || u == 33 || v == 8 || v == 15 ? Block.STONE_CARVED : Block.AIR); }
        for (int v : new int[]{3, 6, 17, 20}) b.prop("beam_ceiling", 22, v, 24, v, TOP - 1, 0, -1);
        for (int v : new int[]{3, 6, 17, 20}) b.prop("beam_ceiling", 35, v, 37, v, TOP - 1, 0, -1);
        // the way up to the roof: a ladder in the roof-access room, through a hatch
        for (int y = F1; y <= ROOF; y++) b.put(47, 35, y, Block.LADDER);
        b.put(47, 34, TOP, Block.AIR); b.put(47, 34, ROOF, Block.AIR);
        b.entry(29, -3, G);

        furnish(b, G, F1, TOP, BY, H);

        // ================================================================ finishes
        for (MapData.Room rm : bp.rec.rooms) switch (rm.type) {
            case "hall" -> bp.finish(rm, PL, WD, 2, Block.STONE_CARVED);
            case "corridor", "stairs" -> { if (rm.floor < 0) bp.finish(rm, CC, 0, 0, 0); else bp.finish(rm, PG, WD, 2, 0); }
            case "reception", "waiting_room", "press_room", "lounge", "library", "chief_office", "collection", "observation" -> bp.finish(rm, WD, 0, 0, PL);
            case "west_office", "east_office", "operations", "stars_office" -> bp.finish(rm, OP, WD, 1, 0);
            case "locker_room", "shower_room", "morgue" -> bp.finish(rm, TW, TG, 2, 0);
            case "safety_deposit", "records", "evidence", "file_room", "linen_room", "art_room", "watchman", "break_room", "east_storage" -> bp.finish(rm, PL, Block.DADO_GREEN, 2, 0);
            default -> bp.finish(rm, rm.wallBlock, 0, 0, 0);
        }

        // ================================================================ the dead in the station: the basement was never cleared
        // (the ground floor and the first floor are held - this is somebody's headquarters; what was not cleared is underneath)
        b.lair("infected", 20, 15, BY); b.lair("infected", 38, 10, BY); b.lair("infected", 4, 22, BY); b.lair("infected", 20, 31, BY); b.lair("infected", 30, 23, BY); b.lair("infected", 56, 22, BY);
        b.lair("idog", 38, 31, BY);                                                                         // the kennel
        // the Desert Eagle, in the S.T.A.R.S. office's armoury locker, with a full box for it
        b.item("weapon.deagle", 1, 5, F1);
        b.item("ammo.magnum*", 2, 5, F1);
        return bp.done(lm);
    }

    /**
     * The grounds: a walled forecourt with a gate on the street, the path to the portico, lamps and flag poles; the
     * helipad on the east roof; and behind the building the motor yard, fenced, with the motor pool if there is room.
     */
    static void grounds(LandmarkBuilder lb, Site s, MapData.Landmark lm, MapData.Building hq) {
        final int A = s.A, B = s.B, OA = (A - W) / 2, OB = 13, m = OA + 29, TOP = G + 13;      // (the roof deck)
        // 1.2: the forecourt is walled on three sides - the street, with its gate, and the two ends. It was walled on four: the
        // fourth ran along the front of the building, a block out from it, through the portico and across the front doors,
        // which is why nobody could get into the station by its entrance.
        s.enclose(OA - 2, 1, OA + W + 1, 1, Block.STONE_BLOCK, 1, Block.IRON_FENCE, new int[]{m - 1, 1, m + 2, 1});
        s.enclose(OA - 2, 2, OA - 2, OB - 2, Block.STONE_BLOCK, 1, Block.IRON_FENCE);
        s.enclose(OA + W + 1, 2, OA + W + 1, OB - 2, Block.STONE_BLOCK, 1, Block.IRON_FENCE);
        s.path(m, 0, m, OB - 6, Block.STONE_BLOCK, 4);
        for (int a = OA + 2; a < OA + W - 2; a += 5) if (Math.abs(a - m) > 6 && s.open(a, 3)) { s.set(a, 3, G, Block.HEDGE); if (s.open(a + 1, 3)) s.set(a + 1, 3, G, Block.HEDGE); }
        for (int da : new int[]{-7, 8}) if (s.open(m + da, 5)) { for (int y = G; y < G + 8; y++) s.set(m + da, 5, y, Block.LAMP_POST); }
        s.lampPost(OA + 4, 6); s.lampPost(OA + W - 5, 6); s.lampPost(m - 12, 9); s.lampPost(m + 13, 9);
        for (int da : new int[]{-4, 5}) { s.prop(m + da, 8, Block.BARRIER); }
        s.sandbags(m - 9, 4, 4, true); s.sandbags(m + 6, 4, 4, true);
        // the helipad, on the roof of the east wing
        int ha = OA + 46, hb = OB + 6;
        for (int a = ha; a < ha + 7; a++) for (int b2 = hb; b2 < hb + 7; b2++) s.set(a, b2, TOP, Math.max(Math.abs(a - ha - 3), Math.abs(b2 - hb - 3)) == 3 || a == ha + 3 || b2 == hb + 3 ? Block.HELIPAD : Block.CONCRETE_DARK);
        lb.g.site("helipad", s.x(ha + 3, hb + 3), TOP + 1, s.z(ha + 3, hb + 3));
        // the motor yard behind
        int y0 = OB + D + 2, y1 = B - 2;
        if (y1 - y0 >= 8) {
            s.pave(3, y0, A - 4, y1, Block.ASPHALT);
            s.enclose(3, y0, A - 4, y1, Block.CHAINLINK, 3, Block.BARBED, new int[]{OA + 42, y0, OA + 45, y0}, new int[]{A - 4, y1 - 6, A - 4, y1 - 3});
            for (int a = 8; a <= A - 10; a += 5) for (int k = 2; k <= 5; k++) if (y1 - k > y0 + 2) s.set(a, y1 - k, G - 1, Block.ROAD_LINE);
            s.prop(A - 8, y0 + 2, Block.GENERATOR); s.prop(A - 9, y0 + 2, Block.BARREL); s.crate(A - 7, y0 + 4, Block.DUMPSTER, "trash");
            s.lampPost(5, y0 + 2); s.lampPost(A - 6, y1 - 2);
            if (y1 - y0 >= 18 && A >= 40) {
                MapData.Building garage = lb.bld(s, lm, "lm_police_garage", 7, y1 - 14, 27, y1 - 2, 0, 1, "Motor Pool");
                s.post("Motor Yard", "patrol", lm.faction, s.local(garage.entryX, garage.entryZ)[0], s.local(garage.entryX, garage.entryZ)[1] - 2, G);
            } else s.post("Motor Yard", "patrol", lm.faction, A / 2, (y0 + y1) / 2, G);
        }
        lb.garrison(s, lm, hq, m, 2);
        s.post("Headquarters Roof", "sniper", lm.faction, ha - 3, hb + 3, TOP + 1);
        System.out.printf("[Gen] Police headquarters: lot %dx%d, building %dx%d at (%d, %d), %d rooms on %d floors%n", A, B, W, D, OA, OB, hq.rooms.size(), hq.floors);
    }

    /** What stands in each room. */
    private static void furnish(B b, int G0, int F1, int TOP, int BY, int H) {
        // ---- the main hall: the desk in the middle of the floor, the statue between the stairs, benches, flags, the notice boards
        // the desk is a ring of counter in the middle of the floor, open at the back
        for (int u = 27; u <= 32; u++) for (int v = 6; v <= 9; v++) { boolean rim = u == 27 || u == 32 || v == 6 || v == 9; if (rim && !(v == 9 && (u == 29 || u == 30))) b.put(u, v, G0, Block.COUNTER); }
        b.prop("terminal_crt", 28, 6, G0 + 1, 0, 1); b.prop("phone_desk", 31, 6, G0 + 1, 0, 1); b.prop("typewriter_desk", 28, 8, G0, -1, 0); b.prop("chair_wood", 30, 7, G0, 0, 1); b.prop("filing_cabinet", 31, 8, G0, 1, 0);
        b.box(28, 0, 31, 5, G0 - 1, G0 - 1, Block.CARPET_RED);
        b.prop("statue_figure", 29, 16, G0, 0, 1); b.prop("statue_figure", 30, 16, G0, 0, 1); b.box(28, 16, 31, 17, G0 - 1, G0 - 1, Block.MARBLE);
        b.prop("pedestal_urn", 28, 17, G0, 0, 1); b.prop("pedestal_urn", 31, 17, G0, 0, 1);
        for (int v = 4; v <= 16; v += 6) { b.prop("bench_wood", 21, v, 21, v + 1, G0, -1, 0); b.prop("bench_wood", 38, v, 38, v + 1, G0, 1, 0); }
        b.prop("plant_palm", 21, 0, G0, -1, 0); b.prop("plant_palm", 38, 0, G0, 1, 0); b.prop("plant_dead", 21, 21, G0, -1, 0); b.prop("plant_palm", 38, 21, G0, 1, 0);
        b.prop("notice_board", 21, 8, 21, 9, G0 + 1, -1, 0); b.prop("missing_posters", 38, 8, 38, 9, G0 + 1, 1, 0); b.prop("map_wall", 21, 13, 21, 14, G0 + 1, -1, 0); b.prop("clock_wall", 38, 13, G0 + 3, 1, 0);
        b.prop("tapestry", 26, 21, 27, 21, F1 + 1, 0, 1); b.prop("tapestry", 32, 21, 33, 21, F1 + 1, 0, 1); b.prop("painting_landscape", 29, 21, 30, 21, F1 + 2, 0, 1);
        b.prop("papers_floor", 27, 10, G0, 0, -1); b.prop("papers_floor", 32, 6, G0, 0, -1); b.prop("blood_old", 33, 11, G0, 0, -1);
        b.prop("barrier_rope", 26, 6, 27, 6, G0, 0, -1); b.prop("barrier_rope", 32, 6, 33, 6, G0, 0, -1); b.prop("stretcher", 24, 4, 24, 5, G0, 0, -1); b.prop("first_aid_box", 21, 11, G0 + 1, -1, 0);
        b.prop("sign_exit", 29, 0, G0 + 3, 0, -1); b.prop("fire_extinguisher", 38, 11, G0 + 1, 1, 0);
        b.prop("chandelier", 29, 9, 30, 10, TOP - 4, 0, -1); for (int y = TOP - 2; y < TOP; y++) b.prop("chain_chandelier", 29, 9, 30, 10, y, 0, -1);
        for (int[] c : new int[][]{{25, 4}, {34, 4}, {25, 16}, {34, 16}, {29, 13}, {30, 6}}) b.lamp(c[0], c[1], TOP - 1);
        for (int v = 3; v <= 18; v += 5) { b.lamp(22, v, F1 - 3); b.lamp(37, v, F1 - 3); }
        b.lamp(27, 20, F1 - 3); b.lamp(32, 20, F1 - 3); b.lamp(29, 1, F1 - 3);
        b.prop("sconce", 21, 6, G0 + 2, -1, 0); b.prop("sconce", 38, 6, G0 + 2, 1, 0); b.prop("sconce", 21, 16, G0 + 2, -1, 0); b.prop("sconce", 38, 16, G0 + 2, 1, 0);
        // banners over the rail, lamps on the columns, something left on every bench
        b.prop("curtain_tall", 24, 20, G0 + 3, 0, 1); b.prop("curtain_tall", 35, 20, G0 + 3, 0, 1);
        for (int v : new int[]{2, 10, 19}) { b.prop("lantern_wall", 24, v, G0 + 2, -1, 0); b.prop("lantern_wall", 35, v, G0 + 2, 1, 0); }
        b.prop("boxes_cardboard", 22, 2, G0, -1, 0); b.prop("gurney", 37, 18, 37, 19, G0, 1, 0); b.prop("cart_medicine", 36, 15, G0, 1, 0); b.prop("iv_stand", 35, 17, G0, 0, -1); b.prop("bedroll", 22, 18, 22, 19, G0, -1, 0); b.prop("candles_floor", 23, 17, G0, 0, -1);
        b.prop("sacks", 26, 1, G0, 0, -1); b.prop("crate_stack", 33, 0, G0, 0, -1); b.prop("glass_shards", 30, 12, G0, 0, -1);
        // the gallery
        b.prop("bench_wood", 21, 6, 21, 7, F1, -1, 0); b.prop("bench_wood", 38, 10, 38, 11, F1, 1, 0); b.prop("statue_bust", 21, 0, F1, -1, 0); b.prop("statue_bust", 38, 0, F1, 1, 0);
        b.prop("painting_portrait", 21, 10, F1 + 1, -1, 0); b.prop("painting_portrait", 38, 6, F1 + 1, 1, 0); b.prop("painting_landscape", 21, 12, 21, 13, F1 + 2, -1, 0); b.prop("painting_landscape", 38, 13, 38, 14, F1 + 2, 1, 0);
        b.prop("pedestal_urn", 24, 21, F1, 0, 1); b.prop("pedestal_urn", 35, 21, F1, 0, 1); b.prop("cobwebs", 21, 21, TOP - 1, -1, 0); b.prop("cobwebs", 38, 0, TOP - 1, 1, 0);

        // ---- reception: the counter with its shutter, the waiting bench, the key cabinet
        for (int u = 13; u <= 18; u++) b.put(u, 4, G0, Block.COUNTER);
        b.prop("terminal_crt", 14, 4, G0 + 1, 0, -1); b.prop("phone_desk", 17, 4, G0 + 1, 0, -1);
        b.prop("desk_office", 13, 7, 14, 7, G0, 0, 1); b.prop("chair_wood", 14, 6, G0, 0, -1); b.prop("filing_cabinet", 16, 7, G0, 0, 1); b.prop("filing_open", 17, 7, G0, 0, 1); b.prop("photocopier", 19, 7, G0, 1, 0);
        b.prop("bench_wood", 13, 0, 14, 0, G0, 0, -1); b.prop("plant_palm", 19, 0, G0, 1, 0); b.prop("notice_board", 16, 0, 17, 0, G0 + 1, 0, -1); b.prop("calendar", 12, 6, G0 + 1, -1, 0); b.prop("clock_wall", 19, 2, G0 + 3, 1, 0);
        b.loot(18, 7, G0, Block.CABINET, "office"); b.lamp(15, 2, G0 + 4); b.lamp(16, 6, G0 + 4);
        // ---- operations: the briefing room - rows of desks facing a blackboard
        b.prop("blackboard", 15, 9, 16, 9, G0 + 1, 0, -1); b.prop("map_wall", 18, 9, 19, 9, G0 + 1, 0, -1); b.prop("desk_office", 13, 11, 14, 11, G0, 0, -1);
        for (int v = 13; v <= 19; v += 2) for (int u = 13; u <= 17; u += 4) { b.prop("table_kitchen", u, v, u + 1, v, G0, 0, -1); b.prop("chair_wood", u, v + 1, G0, 0, 1); }
        b.prop("notice_board", 12, 17, 12, 18, G0 + 1, -1, 0); b.prop("water_cooler", 19, 21, G0, 1, 0); b.prop("filing_cabinet", 12, 21, G0, -1, 0); b.prop("papers_floor", 16, 16, G0, 0, -1); b.prop("blood_old", 18, 14, G0, 0, -1);
        b.loot(19, 11, G0, Block.LOCKER, "military_personal"); b.lamp(15, 12, G0 + 4); b.lamp(16, 18, G0 + 4); b.prop("fluorescent_dead", 15, 15, 16, 15, G0 + 4, 0, -1);
        // ---- the west office: two rows of desks, the lieutenant's glass box at the end, the safe
        for (int v = 1; v <= 9; v += 4) { b.prop("desk_office", 1, v, 2, v, G0, 0, -1); b.prop("chair_wood", 1, v + 1, G0, 0, 1); b.prop("desk_office", 5, v, 6, v, G0, 0, -1); b.prop("chair_wood", 6, v + 1, G0, 0, 1); }
        b.prop("typewriter_desk", 3, 1, G0, 0, -1); b.prop("terminal_dead", 4, 5, G0, 0, -1); b.prop("phone_desk", 3, 9, G0, 0, -1);
        b.prop("filing_cabinet", 0, 12, G0, -1, 0); b.prop("filing_cabinet", 0, 13, G0, -1, 0); b.prop("filing_open", 1, 13, G0, 0, 1); b.prop("safe_big", 7, 13, G0, 1, 0);
        b.prop("notice_board", 0, 3, 0, 4, G0 + 1, -1, 0); b.prop("calendar", 0, 7, G0 + 1, -1, 0); b.prop("coat_stand", 7, 0, G0, 1, 0); b.prop("papers_floor", 4, 7, G0, 0, -1); b.prop("papers_floor", 3, 11, G0, 0, -1);
        b.loot(4, 13, G0, Block.SAFE, "safe"); b.loot(7, 6, G0, Block.FILING, "office"); b.lamp(3, 3, G0 + 4); b.lamp(4, 10, G0 + 4); b.prop("fan_ceiling", 3, 6, 4, 7, G0 + 4, 0, -1);
        // ---- the safety deposit room: walls of lockers, a keypad terminal
        for (int u = 0; u <= 7; u++) { if (u != 3 && u != 4) b.prop(u % 3 == 0 ? "locker_open" : "locker_row", u, 15, G0, 0, -1); b.prop(u % 4 == 2 ? "locker_open" : "locker_row", u, 21, G0, 0, 1); }
        b.prop("terminal_crt", 0, 18, G0, -1, 0); b.prop("bench_locker", 3, 18, 4, 18, G0, 0, -1);
        b.loot(3, 15, G0, Block.LOCKER, "weapons"); b.loot(4, 15, G0, Block.LOCKER, "military_personal"); b.lamp(4, 18, G0 + 4);
        // ---- the dark room: trays, a line of prints, a red lamp
        b.prop("table_work", 1, 23, 3, 23, G0, 0, -1); b.prop("sink_kitchen", 5, 23, G0, 0, -1); b.prop("shelf_wall", 0, 26, G0 + 2, -1, 0); b.prop("photo_frames", 3, 28, G0 + 1, 0, 1); b.prop("photo_frames", 5, 28, G0 + 1, 0, 1);
        b.prop("curtain_red", 7, 27, G0, 1, 0); b.prop("typewriter_desk", 0, 28, G0, -1, 0); b.prop("chair_wood", 1, 27, G0, 0, -1); b.prop("lamp_hanging_off", 3, 25, G0 + 4, 0, -1);
        b.loot(6, 28, G0, Block.CABINET, "office"); b.prop("beacon_red", 4, 26, G0 + 4, 0, -1);
        // ---- records: aisles of filing shelves
        for (int v = 28; v <= 34; v += 3) for (int u = 13; u <= 18; u++) { if (u == 15 || u == 16) continue; b.prop((u + v) % 4 == 0 ? "filing_open" : "filing_cabinet", u, v, G0, 0, -1); }
        b.prop("desk_office", 12, 26, 13, 26, G0, 0, -1); b.prop("chair_wood", 12, 27, G0, 0, 1); b.prop("boxes_cardboard", 19, 35, G0, 1, 0); b.prop("papers_floor", 15, 31, G0, 0, -1); b.prop("ladder_lean", 19, 30, G0, 1, 0);
        b.loot(19, 26, G0, Block.FILING, "office"); b.lamp(15, 29, G0 + 4); b.lamp(16, 33, G0 + 4);
        // ---- evidence: caged shelves, tagged boxes
        for (int u = 21; u <= 28; u += 1) { if (u == 24 || u == 25) continue; b.prop(u % 2 == 0 ? "shelf_parts" : "crate_stack", u, 35, G0, 0, 1); }
        for (int v = 29; v <= 32; v++) b.put(26, v, G0, Block.CHAINLINK); for (int v = 29; v <= 32; v++) b.put(26, v, G0 + 1, Block.CHAINLINK);
        b.prop("shelf_parts", 28, 29, G0, 1, 0); b.prop("shelf_parts", 28, 31, G0, 1, 0); b.prop("table_work", 21, 28, 21, 30, G0, -1, 0); b.prop("boxes_cardboard", 22, 32, G0, 0, -1); b.prop("trunk", 23, 33, G0, 0, 1);
        b.loot(27, 33, G0, Block.CRATE_MIL, "military"); b.loot(21, 33, G0, Block.CRATE, "cache"); b.lamp(24, 30, G0 + 4);
        // ---- the locker room
        for (int u = 30; u <= 38; u++) { if (u == 34) continue; b.prop(u % 4 == 1 ? "locker_open" : "locker_row", u, 35, G0, 0, 1); }
        for (int v = 28; v <= 32; v += 4) { b.prop("bench_locker", 32, v, 33, v, G0, 0, -1); b.prop("bench_locker", 36, v, 37, v, G0, 0, -1); }
        b.prop("washbasin", 30, 26, G0, 0, -1); b.prop("washbasin", 31, 26, G0, 0, -1); b.prop("mirror_broken", 30, 26, G0 + 2, 0, -1); b.prop("coat_hooks", 38, 30, G0 + 1, 1, 0); b.prop("mop_bucket", 38, 26, G0, 1, 0);
        b.loot(34, 35, G0, Block.LOCKER, "military_personal"); b.lamp(34, 30, G0 + 4);
        // ---- the boiler room
        b.prop("boiler", 40, 35, G0, 0, 1); b.prop("boiler", 41, 35, G0, 0, 1); b.prop("water_heater", 45, 35, G0, 0, 1); b.prop("tank_pressure", 47, 33, G0, 1, 0); b.prop("pipes_wall", 40, 30, G0 + 1, -1, 0); b.prop("valve_wheel", 40, 32, G0 + 1, -1, 0);
        b.prop("gauges", 42, 35, G0 + 2, 0, 1); b.prop("pipe_run", 44, 28, 45, 28, G0, 0, -1); b.prop("fuse_box", 47, 28, G0 + 1, 1, 0); b.prop("drum_oil", 40, 27, G0, -1, 0); b.prop("pipes_ceiling", 42, 31, 43, 31, G0 + 4, 0, -1); b.prop("cart_tool", 46, 30, G0, 0, -1);
        b.loot(47, 26, G0, Block.TOOLBOX, "industrial"); b.lamp(43, 30, G0 + 4);
        // ---- the press room: rows of chairs, a lectern, the flags
        for (int v = 3; v <= 6; v++) for (int u = 41; u <= 46; u++) if (u != 43 && u != 44 && (u + v) % 5 != 0) b.prop("chair_wood", u, v, G0, 0, 1);
        b.prop("desk_writing", 43, 0, 44, 0, G0, 0, -1); b.prop("tapestry", 41, 0, 42, 0, G0 + 1, 0, -1); b.prop("tapestry", 45, 0, 46, 0, G0 + 1, 0, -1); b.prop("music_stand", 45, 1, G0, 0, -1); b.prop("papers_floor", 44, 4, G0, 0, -1);
        b.loot(47, 7, G0, Block.CABINET, "office"); b.lamp(43, 4, G0 + 4);
        // ---- the watchman's room: the key board, a cot, a hotplate
        b.prop("desk_guard", 41, 9, 42, 9, G0, 0, -1); b.prop("chair_wood", 41, 10, G0, 0, 1); b.prop("bed_single", 47, 12, 47, 13, G0, 1, 0); b.prop("stove_enamel", 40, 13, G0, -1, 0); b.prop("coat_hooks", 44, 9, G0 + 1, 0, -1); b.prop("radio_set", 45, 13, G0, 0, 1);
        b.prop("notice_board", 43, 13, 44, 13, G0 + 1, 0, 1); b.loot(46, 9, G0, Block.LOCKER, "military_personal"); b.lamp(43, 11, G0 + 4);
        // ---- the break room: tables, the vending machines, a television nobody turned off
        b.prop("vending_machine", 40, 15, G0, -1, 0); b.prop("vending_machine", 40, 16, G0, -1, 0); b.prop("water_cooler", 40, 18, G0, -1, 0); b.prop("counter_kitchen", 44, 21, G0, 0, 1); b.prop("sink_kitchen", 45, 21, G0, 0, 1); b.prop("fridge_old", 47, 21, G0, 1, 0);
        b.prop("table_round", 43, 17, G0, 0, -1); b.prop("chair_wood", 42, 17, G0, -1, 0); b.prop("chair_wood", 44, 17, G0, 1, 0); b.prop("table_round", 45, 19, G0, 0, -1); b.prop("chair_wood", 46, 19, G0, 1, 0); b.prop("sofa_leather", 42, 21, 43, 21, G0, 0, 1);
        b.put(47, 16, G0, Block.TV); b.prop("jukebox", 47, 18, G0, 1, 0); b.loot(46, 21, G0, Block.CABINET, "food"); b.lamp(43, 18, G0 + 4);
        // ---- the east office: detectives - desks pushed together, the case board
        for (int v = 1; v <= 9; v += 4) { b.prop("desk_office", 53, v, 54, v, G0, 0, -1); b.prop("chair_wood", 53, v + 1, G0, 0, 1); b.prop("desk_office", 57, v, 58, v, G0, 0, -1); b.prop("chair_wood", 58, v + 1, G0, 0, 1); }
        b.prop("missing_posters", 59, 3, 59, 4, G0 + 1, 1, 0); b.prop("map_wall", 59, 7, 59, 8, G0 + 1, 1, 0); b.prop("typewriter_desk", 55, 5, G0, 0, -1); b.prop("terminal_crt", 56, 9, G0, 0, -1); b.prop("phone_desk", 55, 1, G0, 0, -1);
        b.prop("filing_cabinet", 59, 12, G0, 1, 0); b.prop("filing_open", 59, 13, G0, 1, 0); b.prop("safe_big", 52, 13, G0, -1, 0); b.prop("coat_stand", 52, 0, G0, -1, 0); b.prop("papers_floor", 55, 7, G0, 0, -1); b.prop("blood_old", 56, 11, G0, 0, -1);
        b.loot(55, 13, G0, Block.DESK, "office"); b.loot(57, 13, G0, Block.FILING, "office"); b.lamp(55, 3, G0 + 4); b.lamp(56, 10, G0 + 4); b.prop("fan_ceiling", 55, 6, 56, 7, G0 + 4, 0, -1);
        // ---- interrogation, and the room behind the glass
        b.prop("table_kitchen", 55, 18, 56, 18, G0, 0, -1); b.prop("chair_wood", 55, 17, G0, 0, -1); b.prop("chair_wood", 56, 19, G0, 0, 1); b.prop("shackles_wall", 52, 18, G0 + 1, -1, 0); b.prop("lamp_hanging_off", 55, 18, G0 + 4, 0, -1); b.prop("blood_old", 54, 19, G0, 0, -1);
        b.lamp(56, 17, G0 + 4);
        b.prop("desk_office", 55, 23, 56, 23, G0, 0, -1); b.prop("chair_wood", 55, 24, G0, 0, 1); b.prop("chair_wood", 57, 24, G0, 0, 1); b.prop("radio_set", 58, 23, G0, 0, -1); b.prop("filing_cabinet", 59, 28, G0, 1, 0); b.prop("sofa_leather", 53, 28, 54, 28, G0, 0, 1);
        b.loot(52, 28, G0, Block.CABINET, "office"); b.lamp(56, 26, G0 + 4);
        // ---- the corridors, ground floor
        for (int v = 2; v <= 26; v += 8) { b.prop("rug_runner", 9, v, 9, v + 2, G0, 0, -1); b.prop("rug_runner", 50, v, 50, v + 2, G0, 0, -1); b.lamp(9, v + 1, G0 + 4); b.lamp(50, v + 1, G0 + 4); }
        b.prop("fire_extinguisher", 9, 8, G0 + 1, -1, 0); b.prop("sign_exit", 10, 34, G0 + 3, 1, 0); b.prop("bench_wood", 9, 28, 9, 29, G0, -1, 0); b.prop("notice_board", 50, 28, 50, 29, G0 + 1, 1, 0); b.prop("vending_machine", 9, 35, G0, 0, 1); b.prop("plant_dead", 50, 35, G0, 0, 1);
        for (int u = 14; u <= 44; u += 10) { b.lamp(u, 23, G0 + 4); b.prop("painting_small", u + 3, 23, G0 + 2, 0, -1); }
        b.prop("bench_wood", 20, 23, 21, 23, G0, 0, -1); b.prop("water_cooler", 39, 23, G0, 0, -1); b.prop("papers_floor", 33, 24, G0, 0, -1);

        // ---- first floor. The waiting room
        b.prop("sofa_leather", 13, 0, 14, 0, F1, 0, -1); b.prop("sofa_leather", 17, 0, 18, 0, F1, 0, -1); b.prop("table_coffee", 15, 2, 16, 2, F1, 0, -1); b.prop("armchair", 12, 4, F1, -1, 0); b.prop("magazine_rack", 19, 7, F1, 1, 0); b.prop("plant_palm", 12, 7, F1, -1, 0);
        b.prop("painting_landscape", 15, 7, 16, 7, F1 + 2, 0, 1); b.prop("clock_wall", 19, 2, F1 + 3, 1, 0); b.prop("rug_round", 15, 4, 16, 5, F1, 0, -1); b.lamp(15, 4, F1 + 4);
        // the library: two storeys of books in the original; here one tall room lined with them, reading tables down the middle
        for (int v = 9; v <= 21; v++) { if (v == 15) continue; b.prop(v % 5 == 0 ? "bookcase_ransacked" : "bookcase", 12, v, F1, -1, 0); }
        for (int u = 13; u <= 19; u++) b.prop(u % 4 == 0 ? "bookcase_ransacked" : "bookcase", u, 9, F1, 0, -1);
        for (int v = 12; v <= 18; v += 3) { b.prop("table_dining", 15, v, 16, v, F1, 0, -1); b.prop("chair_dining", 15, v + 1, F1, 0, 1); }
        b.prop("bookcase", 18, 18, F1, 0, 1); b.prop("bookcase", 18, 19, F1, 0, -1); b.prop("ladder_lean", 13, 21, F1, 0, 1); b.prop("globe", 19, 21, F1, 1, 0); b.prop("papers_floor", 16, 20, F1, 0, -1); b.prop("armchair_green", 19, 11, F1, 1, 0);
        b.loot(15, 21, F1, Block.BOOKSHELF, "office"); b.loot(17, 21, F1, Block.BOOKSHELF, "office"); b.prop("chandelier_small", 15, 15, F1 + 4, 0, -1); b.lamp(16, 11, F1 + 4); b.lamp(15, 19, F1 + 4);
        // the S.T.A.R.S. office: six desks, the radio, the trophy shelf, and the armoury locker at the far end
        for (int v = 3; v <= 9; v += 3) { b.prop("desk_office", 1, v, 2, v, F1, 0, -1); b.prop("chair_wood", 1, v + 1, F1, 0, 1); b.prop("desk_office", 5, v, 6, v, F1, 0, -1); b.prop("chair_wood", 6, v + 1, F1, 0, 1); }
        b.prop("locker_open", 0, 0, F1, 0, -1); b.prop("locker_row", 3, 0, F1, 0, -1); b.prop("locker_row", 4, 0, F1, 0, -1); b.put(1, 0, F1, Block.WEAPON_RACK); b.put(2, 0, F1, Block.WEAPON_RACK);
        b.prop("radio_set", 7, 3, F1, 1, 0); b.prop("terminal_crt", 3, 6, F1, 0, -1); b.prop("map_wall", 0, 7, 0, 8, F1 + 1, -1, 0); b.prop("photo_frames", 7, 9, F1 + 1, 1, 0); b.prop("trophy_head", 4, 13, F1 + 2, 0, 1);
        b.prop("desk_writing", 3, 12, 4, 12, F1, 0, 1); b.prop("armchair", 3, 13, F1, 0, 1); b.prop("filing_cabinet", 0, 13, F1, -1, 0); b.prop("tapestry", 5, 13, 6, 13, F1 + 1, 0, 1); b.prop("papers_floor", 4, 8, F1, 0, -1);
        b.loot(7, 13, F1, Block.LOCKER, "weapons"); b.loot(7, 0, F1, Block.CRATE_MIL, "military"); b.lamp(3, 2, F1 + 4); b.lamp(4, 10, F1 + 4);
        // the linen room and the showers
        for (int u = 0; u <= 7; u += 1) { if (u == 3 || u == 4) continue; b.prop(u % 2 == 0 ? "shelf_empty" : "shelf_cans", u, 15, F1, 0, -1); }
        b.prop("washing_tub", 0, 20, F1, -1, 0); b.prop("washing_tub", 0, 21, F1, -1, 0); b.prop("sacks", 3, 21, F1, 0, 1); b.prop("sacks", 4, 21, F1, 0, 1); b.prop("clothes_rail", 6, 21, 7, 21, F1, 0, 1); b.prop("mop_bucket", 7, 17, F1, 1, 0);
        b.loot(5, 21, F1, Block.CABINET, "medical_home"); b.lamp(4, 18, F1 + 4);
        for (int u = 0; u <= 6; u += 2) b.prop("shower_decon", u, 23, F1, 0, -1);
        b.prop("washbasin", 0, 28, F1, 0, 1); b.prop("washbasin", 1, 28, F1, 0, 1); b.prop("mirror_broken", 0, 28, F1 + 2, 0, 1); b.prop("toilet_p", 6, 28, F1, 0, 1); b.prop("toilet_p", 7, 28, F1, 0, 1); b.prop("bench_locker", 3, 26, 4, 26, F1, 0, -1); b.prop("blood_old", 2, 25, F1, 0, -1);
        b.loot(7, 26, F1, Block.MED_CABINET, "medical"); b.lamp(4, 26, F1 + 4);
        // files, the clock tower hall, the way to the roof
        for (int v = 28; v <= 34; v += 3) for (int u = 13; u <= 18; u++) { if (u == 15 || u == 16) continue; b.prop((u * 3 + v) % 5 == 0 ? "filing_open" : "filing_cabinet", u, v, F1, 0, -1); }
        b.prop("desk_office", 12, 26, 13, 26, F1, 0, -1); b.prop("boxes_cardboard", 19, 26, F1, 1, 0); b.prop("papers_floor", 16, 30, F1, 0, -1); b.loot(19, 35, F1, Block.FILING, "office"); b.lamp(15, 30, F1 + 4);
        b.prop("pipe_stack", 21, 35, F1, 0, 1); b.prop("crate_stack", 22, 35, F1, 0, 1); b.prop("hook_crane", 29, 31, F1 + 3, 0, -1); b.prop("workbench_vice", 36, 35, 37, 35, F1, 0, 1); b.prop("cart_tool", 34, 33, F1, 0, -1);
        b.prop("ladder_lean", 38, 30, F1, 1, 0); b.prop("drum_oil", 21, 27, F1, -1, 0); b.prop("boxes_cardboard", 23, 27, F1, 0, -1); b.prop("beam_ceiling", 26, 28, 26, 30, F1 + 4, -1, 0); b.prop("beam_ceiling", 33, 28, 33, 30, F1 + 4, -1, 0);
        b.prop("cobwebs", 21, 26, F1 + 4, -1, 0); b.prop("cobwebs", 38, 35, F1 + 4, 1, 0); b.loot(25, 35, F1, Block.TOOLBOX, "industrial"); b.lamp(29, 28, F1 + 4); b.lamp(30, 33, F1 + 4);
        b.prop("fuse_box", 40, 30, F1 + 1, -1, 0); b.prop("drum_hazard", 40, 35, F1, 0, 1); b.prop("crate_stack", 41, 35, F1, 0, 1); b.prop("sign_exit", 46, 35, F1 + 3, 0, 1); b.prop("fire_extinguisher", 40, 27, F1 + 1, -1, 0); b.lamp(43, 30, F1 + 4);
        // the art room
        b.prop("easel", 42, 2, F1, 0, -1); b.prop("easel", 45, 4, F1, 0, -1); b.prop("statue_bust", 40, 0, F1, -1, 0); b.prop("statue_figure", 47, 0, F1, 1, 0); b.prop("pedestal_urn", 44, 6, F1, 0, 1); b.prop("table_work", 41, 7, 43, 7, F1, 0, 1);
        b.prop("painting_landscape", 43, 0, 44, 0, F1 + 2, 0, -1); b.prop("painting_small", 47, 4, F1 + 2, 1, 0); b.prop("crate_stack", 47, 7, F1, 1, 0); b.loot(46, 7, F1, Block.CRATE, "antique"); b.lamp(43, 3, F1 + 4);
        // the chief's office: the desk, the trophies, the paintings, the fire
        b.prop("desk_writing", 43, 12, 44, 12, F1, 0, -1); b.prop("armchair", 43, 11, F1, 0, -1); b.prop("chair_dining", 43, 14, F1, 0, 1); b.prop("chair_dining", 45, 14, F1, 0, 1); b.prop("rug_bear", 43, 16, 44, 17, F1, 0, -1);
        b.prop("fireplace_cold", 47, 16, 47, 18, F1, 1, 0); b.prop("trophy_head", 47, 17, F1 + 3, 1, 0); b.prop("trophy_head", 40, 12, F1 + 2, -1, 0); b.prop("trophy_head", 44, 9, F1 + 2, 0, -1); b.prop("bookcase", 40, 9, F1, 0, -1); b.prop("bookcase", 41, 9, F1, 0, -1);
        b.prop("globe", 47, 9, F1, 1, 0); b.prop("sofa_leather", 41, 21, 42, 21, F1, 0, 1); b.prop("table_side", 44, 21, F1, 0, 1); b.prop("painting_portrait", 40, 18, F1 + 1, -1, 0); b.prop("painting_lady", 46, 21, F1 + 1, 0, 1); b.prop("grandfather_clock", 47, 21, F1, 1, 0);
        b.loot(46, 9, F1, Block.SAFE, "safe"); b.loot(47, 13, F1, Block.CABINET, "antique"); b.prop("chandelier_small", 44, 15, F1 + 4, 0, -1); b.lamp(42, 11, F1 + 4); b.lamp(45, 19, F1 + 4);
        b.prop("easel", 46, 2, F1, 0, -1); b.prop("statue_bust", 41, 4, F1, -1, 0); b.prop("pedestal_urn", 46, 5, F1, 0, -1); b.prop("rug_round", 43, 4, 44, 5, F1, 0, -1); b.prop("painting_portrait", 40, 2, F1 + 1, -1, 0); b.prop("painting_lady", 40, 6, F1 + 1, -1, 0);
        b.prop("shelf_wall", 47, 2, F1 + 2, 1, 0); b.prop("boxes_cardboard", 40, 7, F1, -1, 0); b.prop("mannequin", 45, 0, F1, 0, -1); b.prop("papers_floor", 44, 3, F1, 0, -1); b.prop("curtain_torn", 42, 0, F1 + 1, 0, -1);
        // the lounge
        b.prop("bookcase", 52, 3, F1, -1, 0); b.prop("bookcase", 52, 4, F1, -1, 0); b.prop("table_round", 53, 9, F1, 0, -1); b.prop("armchair", 52, 9, F1, -1, 0); b.prop("armchair_green", 54, 10, F1, 0, 1); b.prop("plant_palm", 52, 13, F1, -1, 0);
        b.prop("curtain_red", 54, 0, F1 + 1, 0, -1); b.prop("curtain_red", 58, 0, F1 + 1, 0, -1); b.prop("trophy_head", 52, 7, F1 + 2, -1, 0); b.prop("painting_small", 57, 13, F1 + 2, 0, 1); b.prop("chandelier_small", 55, 7, F1 + 4, 0, -1);
        b.put(59, 4, F1, Block.TV); b.prop("armchair", 58, 5, F1, 0, 1); b.prop("table_side", 59, 6, F1, 1, 0); b.prop("magazine_rack", 59, 11, F1, 1, 0);
        b.prop("billiard_table", 55, 4, 56, 4, F1, 0, -1); b.prop("sofa_leather", 52, 0, 53, 0, F1, 0, -1); b.prop("sofa_leather", 59, 8, 59, 9, F1, 1, 0); b.prop("table_coffee", 57, 8, 57, 9, F1, 1, 0); b.prop("armchair_green", 52, 6, F1, -1, 0);
        b.prop("bar_back", 54, 13, 55, 13, F1, 0, 1); b.prop("stool_bar", 54, 11, F1, 0, -1); b.prop("stool_bar", 56, 11, F1, 0, -1); b.prop("jukebox", 59, 0, F1, 1, 0); b.prop("gramophone", 52, 12, F1, -1, 0); b.prop("rug_persian", 54, 7, 56, 8, F1, 0, -1);
        b.prop("painting_landscape", 55, 0, 56, 0, F1 + 2, 0, -1); b.prop("plant_palm", 59, 13, F1, 1, 0); b.loot(58, 13, F1, Block.CABINET, "food"); b.lamp(55, 3, F1 + 4); b.lamp(56, 10, F1 + 4);
        // the private collection, and storage
        b.prop("statue_figure", 53, 15, F1, 0, -1); b.prop("suit_of_armor", 56, 15, F1, 0, -1); b.prop("statue_bust", 59, 15, F1, 1, 0); b.prop("pedestal_urn", 55, 18, F1, 0, -1); b.prop("pedestal_urn", 57, 18, F1, 0, -1);
        b.prop("painting_lady", 52, 18, F1 + 1, -1, 0); b.prop("painting_portrait", 59, 18, F1 + 1, 1, 0); b.prop("tapestry", 55, 21, 56, 21, F1 + 1, 0, 1); b.prop("trophy_head", 58, 21, F1 + 2, 0, 1); b.prop("rug_persian", 54, 19, 56, 20, F1, 0, -1);
        b.loot(52, 21, F1, Block.SAFE, "antique"); b.prop("chandelier_small", 56, 17, F1 + 4, 0, -1);
        b.prop("crate_stack", 52, 23, F1, -1, 0); b.prop("crate_stack", 53, 23, F1, 0, -1); b.prop("shelf_parts", 59, 24, F1, 1, 0); b.prop("shelf_empty", 59, 26, F1, 1, 0); b.prop("boxes_cardboard", 55, 28, F1, 0, 1); b.prop("trunk", 57, 28, F1, 0, 1); b.prop("mannequin", 58, 23, F1, 0, -1);
        b.loot(52, 28, F1, Block.CRATE, "cache"); b.prop("lamp_hanging_off", 56, 26, F1 + 4, 0, -1); b.lamp(54, 25, F1 + 4);
        b.prop("shelf_parts", 54, 28, F1, 0, 1); b.prop("crate_stack", 56, 23, F1, 0, -1); b.prop("boxes_cardboard", 57, 25, F1, 0, -1); b.prop("sacks", 59, 28, F1, 1, 0); b.prop("drum_oil", 52, 26, F1, -1, 0); b.prop("ladder_lean", 59, 23, F1, 1, 0); b.prop("cobwebs", 52, 23, F1 + 4, -1, 0);
        b.prop("armchair", 53, 20, F1, 0, 1); b.prop("table_side", 58, 15, F1, 0, -1); b.prop("painting_landscape", 54, 15, 55, 15, F1 + 2, 0, -1); b.prop("mirror_gilt", 59, 20, F1 + 1, 1, 0);
        b.prop("armchair_green", 19, 5, F1, 1, 0); b.prop("table_side", 12, 2, F1, -1, 0); b.prop("curtain_red", 14, 0, F1 + 1, 0, -1); b.prop("curtain_red", 17, 0, F1 + 1, 0, -1); b.prop("water_cooler", 12, 6, F1, -1, 0); b.prop("notice_board", 19, 4, 19, 5, F1 + 1, 1, 0);
        // the corridors, first floor
        for (int v = 2; v <= 26; v += 8) { b.prop("rug_runner", 10, v, 10, v + 2, F1, 0, -1); b.prop("rug_runner", 49, v, 49, v + 2, F1, 0, -1); b.lamp(10, v + 1, F1 + 4); b.lamp(49, v + 1, F1 + 4); }
        b.prop("painting_small", 9, 9, F1 + 2, -1, 0); b.prop("bench_wood", 50, 20, 50, 21, F1, 1, 0); b.prop("plant_dead", 9, 0, F1, 0, -1); b.prop("fire_extinguisher", 50, 9, F1 + 1, 1, 0); b.prop("table_side", 9, 29, F1, -1, 0);
        for (int u = 14; u <= 44; u += 10) { b.lamp(u, 24, F1 + 4); b.prop("painting_small", u + 2, 23, F1 + 2, 0, -1); }
        b.prop("bench_wood", 24, 23, 25, 23, F1, 0, -1); b.prop("papers_floor", 36, 24, F1, 0, -1);

        // ---- the basement. The jail: four cells behind bars along the front wall, the guard's desk opposite
        for (int u = 12; u <= 28; u++) { boolean gate = u == 14 || u == 18 || u == 22 || u == 26; for (int y = BY; y < BY + 3; y++) b.put(u, 13, y, gate && y < BY + 2 ? (y == BY ? Block.DOOR_METAL : Block.DOOR_METAL) : Block.CELL_BARS); }
        for (int u : new int[]{16, 20, 24}) for (int v = 9; v <= 12; v++) for (int y = BY; y < BY + 3; y++) b.put(u, v, y, Block.BRICK_WHITE);
        for (int u : new int[]{12, 17, 21, 25}) { b.prop("bunk_cell", u, 9, u, 10, BY, 0, -1); b.prop("toilet_steel", u + 2, 9, BY, 0, -1); }
        b.prop("desk_guard", 19, 21, 20, 21, BY, 0, 1); b.prop("chair_wood", 19, 20, BY, 0, -1); b.prop("locker_row", 27, 21, BY, 0, 1); b.prop("locker_open", 28, 21, BY, 0, 1); b.prop("shackles_wall", 12, 17, BY + 1, -1, 0); b.prop("blood_old", 15, 16, BY, 0, -1); b.prop("blood_old", 23, 11, BY, 0, -1);
        b.loot(26, 21, BY, Block.LOCKER, "military_personal"); b.lamp(15, 17, BY + 4); b.lamp(24, 17, BY + 4); b.prop("fluorescent_dead", 19, 15, 20, 15, BY + 4, 0, -1);
        // the garage
        for (int u : new int[]{35, 42}) for (int v : new int[]{5, 12, 18}) for (int y = BY; y < BY + H; y++) b.put(u, v, y, Block.CONCRETE_DARK);
        for (int u = 31; u <= 46; u += 5) for (int v = 1; v <= 5; v++) b.put(u, v, BY - 1, Block.ROAD_LINE);
        for (int u = 36; u <= 41; u++) { for (int y = BY; y < BY + 3; y++) b.put(u, -1, y, Block.CORRUGATED); b.put(u, -1, BY + 3, Block.HAZARD_STRIPE); }
        b.prop("tire_stack", 30, 21, BY, -1, 0); b.prop("drum_oil", 31, 21, BY, 0, 1); b.prop("engine_hoist", 46, 20, BY, 0, 1); b.prop("workbench_vice", 44, 21, 45, 21, BY, 0, 1); b.prop("cart_tool", 47, 17, BY, 1, 0); b.prop("shelf_parts", 47, 14, BY, 1, 0);
        b.prop("drum_hazard", 30, 0, BY, -1, 0); b.prop("barrier_rope", 33, 8, 34, 8, BY, 0, -1); b.prop("pipes_ceiling", 38, 10, 39, 10, BY + 4, 0, -1); b.prop("cable_tray", 38, 16, 39, 16, BY + 4, 0, -1);
        b.loot(47, 21, BY, Block.TOOLBOX, "industrial"); b.loot(30, 10, BY, Block.CRATE, "crate"); for (int[] c : new int[][]{{33, 3}, {44, 3}, {38, 9}, {33, 16}, {44, 16}}) b.lamp(c[0], c[1], BY + 4);
        // the firing range: lanes, a counter, the targets at the far end
        for (int u = 14; u <= 26; u += 3) { for (int v = 28; v <= 30; v++) b.put(u, v, BY, Block.OFFICE_PANEL); }
        for (int u = 12; u <= 28; u++) if ((u - 14) % 3 != 0) b.put(u, 28, BY, Block.COUNTER);
        for (int u = 13; u <= 27; u += 3) b.prop("mannequin", u, 35, BY, 0, 1);
        b.put(12, 26, BY, Block.WEAPON_RACK); b.prop("locker_row", 28, 26, BY, 1, 0); b.prop("sign_exit", 20, 26, BY + 3, 0, -1); b.loot(27, 26, BY, Block.CRATE_MIL, "military"); b.loot(13, 26, BY, Block.LOCKER, "weapons");
        b.lamp(16, 27, BY + 4); b.lamp(24, 27, BY + 4); b.lamp(20, 33, BY + 4);
        // the kennel: cages down both walls
        for (int u = 30; u <= 46; u += 2) { if (u == 38) continue; b.prop("cage_cell", u, 35, BY, 0, 1); if (u < 36 || u > 40) b.prop("cage_cell", u, 26, BY, 0, -1); }
        b.prop("trough", 42, 30, 43, 30, BY, 0, -1); b.prop("sacks", 47, 30, BY, 1, 0); b.prop("mop_bucket", 30, 30, BY, -1, 0); b.prop("blood_old", 36, 31, BY, 0, -1); b.prop("blood_old", 41, 32, BY, 0, -1); b.prop("shackles_wall", 47, 32, BY + 1, 1, 0);
        b.lamp(34, 30, BY + 4); b.lamp(43, 31, BY + 4);
        // the morgue: the wall of drawers, the tables
        for (int v = 15; v <= 28; v += 1) if (v != 20 && v != 21) b.prop(v % 3 == 0 ? "locker_open" : "locker_row", 0, v, BY, -1, 0);
        b.prop("table_operating", 4, 17, 4, 18, BY, 0, -1); b.prop("table_operating", 4, 24, 4, 25, BY, 0, -1); b.prop("lamp_surgical", 4, 17, 5, 18, BY + 4, 0, -1); b.prop("gurney", 6, 27, 6, 28, BY, 0, 1); b.prop("cart_medicine", 6, 22, BY, 0, -1);
        b.prop("sink_kitchen", 7, 15, BY, 0, -1); b.prop("xray_lightbox", 3, 28, 4, 28, BY + 1, 0, 1); b.put(2, 22, BY, Block.BODYBAG); b.put(5, 20, BY, Block.BODYBAG); b.prop("blood_old", 3, 20, BY, 0, -1);
        b.loot(7, 28, BY, Block.MED_CABINET, "medical"); b.lamp(3, 21, BY + 4); b.lamp(5, 26, BY + 4);
        // the generator room
        b.prop("generator_set", 54, 17, 55, 17, BY, 0, -1); b.prop("generator_set", 54, 21, 55, 21, BY, 0, -1); b.prop("switchgear", 59, 16, BY, 1, 0); b.prop("switchgear", 59, 17, BY, 1, 0); b.prop("transformer_small", 59, 20, BY, 1, 0); b.prop("fuse_box", 52, 24, BY + 1, -1, 0);
        b.prop("panel_instruments", 59, 23, BY, 1, 0); b.prop("drum_oil", 52, 28, BY, -1, 0); b.prop("drum_oil", 53, 28, BY, 0, 1); b.prop("pipe_run", 56, 28, 57, 28, BY, 0, 1); b.prop("cable_tray", 55, 24, 56, 24, BY + 4, 0, -1); b.prop("valve_wheel", 59, 26, BY + 1, 1, 0);
        b.loot(59, 28, BY, Block.TOOLBOX, "industrial"); b.lamp(55, 19, BY + 4); b.lamp(56, 25, BY + 4);
        // the basement corridors: the emergency lighting is on its own circuit and has never gone out
        for (int v = 17; v <= 33; v += 8) { b.lamp(9, v, BY + 4); b.lamp(50, v, BY + 4); b.prop("lamp_ceiling_lit", 10, v + 3, BY + 4, 0, -1); b.prop("lamp_ceiling_lit", 49, v + 3, BY + 4, 0, -1); }
        for (int u = 14; u <= 44; u += 10) { b.lamp(u, 23, BY + 4); b.prop("lamp_ceiling_lit", u + 4, 24, BY + 4, 0, -1); }
        for (int[] c : new int[][]{{20, 31}, {15, 28}, {25, 28}, {36, 30}, {44, 30}, {20, 15}, {14, 11}, {26, 11}, {38, 10}, {33, 3}, {44, 16}, {4, 21}, {4, 26}, {56, 19}, {56, 26}, {4, 33}, {56, 33}}) b.prop("lamp_ceiling_lit", c[0], c[1], BY + 4, 0, -1);
        b.prop("pipes_ceiling", 14, 24, 15, 24, BY + 4, 0, -1); b.prop("cable_tray", 34, 23, 35, 23, BY + 4, 0, -1); b.prop("duct", 42, 24, 43, 24, BY + 4, 0, -1); b.prop("fire_extinguisher", 9, 18, BY + 1, -1, 0); b.prop("pipes_wall", 50, 26, BY + 1, 1, 0);
        b.prop("locker_row", 4, 35, BY, 0, 1); b.prop("drum_oil", 7, 35, BY, 1, 0); b.prop("boxes_cardboard", 55, 35, BY, 0, 1); b.prop("crate_stack", 52, 35, BY, -1, 0); b.prop("sign_exit", 7, 32, BY + 3, 1, 0);
        b.prop("pipes_ceiling", 24, 23, 25, 23, BY + 4, 0, -1); b.prop("pipes_wall", 9, 22, BY + 1, -1, 0); b.prop("sign_exit", 10, 30, BY + 3, 1, 0); b.prop("drum_hazard", 50, 15, BY, 0, -1); b.prop("gurney", 9, 26, 9, 27, BY, -1, 0); b.prop("blood_old", 30, 24, BY, 0, -1);
    }

    /** A blueprint addressed in the building's own cells. */
    static final class B {
        final Blueprint bp; final int oa, ob;
        B(Blueprint bp, int oa, int ob) { this.bp = bp; this.oa = oa; this.ob = ob; }
        MapData.Room room(String type, int floor, int u0, int v0, int u1, int v1, int y, int h, int wall, int floorId, int ceil) { return bp.room(type, floor, oa + u0, ob + v0, oa + u1, ob + v1, y, h, wall, floorId, ceil); }
        void put(int u, int v, int y, int id) { bp.put(oa + u, ob + v, y, id); }
        int at(int u, int v, int y) { return bp.at(oa + u, ob + v, y); }
        void box(int u0, int v0, int u1, int v1, int y0, int y1, int id) { bp.box(oa + u0, ob + v0, oa + u1, ob + v1, y0, y1, id); }
        void mass(int u0, int v0, int u1, int v1, int y0, int y1, int id) { bp.mass(oa + u0, ob + v0, oa + u1, ob + v1, y0, y1, id); }
        void reface(int u0, int v0, int u1, int v1, int y0, int y1, int from, int to) { bp.reface(oa + u0, ob + v0, oa + u1, ob + v1, y0, y1, from, to); }
        void flight(int u, int v, int du, int dv, int y0, int rise, int width, int tread, int under) { bp.flight(oa + u, ob + v, du, dv, y0, rise, width, tread, under); }
        void opening(int u, int v, int y, int h, int w, boolean alongA) { bp.opening(oa + u, ob + v, y, h, w, alongA); }
        void door(int u, int v, int y, int id, boolean ext) { bp.door(oa + u, ob + v, y, id, ext); }
        void window(int u0, int v0, int u1, int v1, int y0, int y1, int glass) { bp.window(oa + u0, ob + v0, oa + u1, ob + v1, y0, y1, glass); }
        boolean prop(String n, int u0, int v0, int u1, int v1, int y, int ba, int bb) { return bp.prop(n, oa + u0, ob + v0, oa + u1, ob + v1, y, ba, bb); }
        boolean prop(String n, int u, int v, int y, int ba, int bb) { return bp.prop(n, oa + u, ob + v, y, ba, bb); }
        void lamp(int u, int v, int y) { bp.lampAt(oa + u, ob + v, y); }
        void loot(int u, int v, int y, int block, String table) { bp.loot(oa + u, ob + v, y, block, table); }
        void lair(String kind, int u, int v, int y) { bp.lair(kind, oa + u, ob + v, y); }
        void entry(int u, int v, int y) { bp.entry(oa + u, ob + v, y); }
        /** Something that is always here: a map site of kind "item" (scene.Raid puts it on the floor at the start of every operation). */
        void item(String id, int u, int v, int y) { if (bp.s.in(oa + u, ob + v)) bp.g.map.sites.add(new MapData.Point(id, "item", bp.s.x(oa + u, ob + v), y, bp.s.z(oa + u, ob + v), bp.s.district)); }
    }
}
