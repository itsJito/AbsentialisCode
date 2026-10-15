package absentialis.world.gen;

import static absentialis.world.gen.Gen.G;

import absentialis.world.Block;
import absentialis.world.MapData;
import absentialis.world.World;

/**
 * 1.0: the Halloran Mansion and the Power Plant, rebuilt inside. The shells are the two hand-drawn landmarks of 0.9;
 * what is in them is new. Every storey has its own ceiling under the floor of the one above, no room is lower than
 * five blocks, the stairs are stairs, and the rooms are furnished from the prop library (world.gen.PropLib) - each
 * with things no other room has - instead of from the block table.
 *
 * Both are told as places people lived and worked in (lore.md, section 3): a table still laid, photographs on a wall,
 * a child's room, a clock stopped on the mantel; a shift's worth of coats in the lockers, a control room left in a
 * hurry, an office telephone off its hook. And both are now somewhere the dead are: the lairs marked here
 * ({@link MapData#lairs}) are where an operation finds infected inside them - the Butcher in the big house, the Tank
 * in what is left of the reactor.
 */
final class Landmarks10 {
    private Landmarks10() {}

    // ================================================================== THE HALLORAN MANSION
    static void halloran(LandmarkBuilder lb, Site s, MapData.Landmark lm) {
        int A = s.A, B = s.B, m = A / 2;
        s.ground(Block.GRASS);
        s.enclose(0, 0, A - 1, B - 1, Block.STONE_BLOCK, 3, Block.IRON_FENCE, new int[]{m - 2, 0, m + 1, 0}, new int[]{m - 1, B - 1, m, B - 1});
        s.path(m - 1, 0, m - 1, 19, Block.COBBLE, 3);
        Blueprint bp = new Blueprint(s, lm, "lm_spencer", lm.name, "manor");
        final int S = Block.STONE_BLOCK, WP = Block.WALLPAPER_STRIPE, WF = Block.WALLPAPER_FLORAL, WD = Block.WOOD_DARK, PQ = Block.PARQUET, PL = Block.PLASTER, CL = Block.CEILING_PLASTER;
        final int H0 = 5, F1 = G + 7, H1 = 5, TOP = G + 12, ROOF = G + 13, TALL = 12;      // two storeys of five clear, each under its own ceiling; the roof slab
        final int a0 = 15, a1 = 82, b0 = 26, b1 = 50;                                    // the house's interior box

        // ---- ground floor
        bp.room("dining", 0, 20, 26, 38, 37, G, TALL, WP, PQ, CL);
        bp.room("kitchen", 0, 16, 39, 26, 49, G, H0, Block.TILE_GREEN, Block.TILE_WHITE, CL);
        bp.room("bar", 0, 28, 39, 38, 49, G, H0, WD, PQ, Block.BOARDS_C);
        bp.room("hall", 0, 40, 45, 57, 49, G, H0, WP, Block.CARPET_RED, CL).circulation = true;      // the passage behind the stairs, to the courtyard door
        bp.room("gallery", 0, 59, 26, 70, 37, G, H0, WF, Block.MARBLE, CL);
        bp.room("bathroom", 0, 72, 26, 78, 31, G, H0, Block.TILE_GREEN, Block.TILE_WHITE, CL);
        bp.room("storage", 0, 72, 33, 78, 37, G, H0, PL, Block.BOARDS_C, CL);
        bp.room("corridor", 0, 59, 39, 81, 40, G, H0, WP, Block.CARPET_RED, CL).circulation = true;  // the corridor with the windows
        bp.room("corridor", 0, 80, 26, 81, 38, G, H0, WP, Block.CARPET_RED, CL).circulation = true;
        bp.room("stairs", 0, 59, 42, 70, 49, G, H0, WP, PQ, CL);
        bp.room("study", 0, 72, 42, 81, 49, G, H0, WD, Block.CARPET_BLUE, Block.BOARDS_C);           // the trophy room
        bp.room("corridor", 0, 16, 26, 18, 37, G, H0, WP, Block.CARPET_RED, CL).circulation = true;  // the west service passage

        // ---- first floor
        bp.room("library", 1, 16, 39, 38, 49, F1, H1, WD, Block.CARPET_RED, Block.BOARDS_C);
        bp.room("corridor", 1, 16, 26, 18, 37, F1, H1, WP, Block.CARPET_RED, CL).circulation = true;
        bp.room("hall", 1, 40, 45, 57, 49, F1, H1, WP, Block.CARPET_RED, CL).circulation = true;
        bp.room("bedroom", 1, 59, 26, 70, 37, F1, H1, WF, PQ, CL);                                   // the master bedroom
        bp.room("nursery", 1, 72, 26, 78, 31, F1, H1, Block.PLASTER_BLUE, PQ, CL);
        bp.room("sickroom", 1, 72, 33, 78, 37, F1, H1, WF, PQ, CL);
        bp.room("corridor", 1, 59, 39, 81, 40, F1, H1, WP, Block.CARPET_RED, CL).circulation = true;
        bp.room("corridor", 1, 80, 26, 81, 38, F1, H1, WP, Block.CARPET_RED, CL).circulation = true;
        bp.room("stairs", 1, 59, 42, 70, 49, F1, H1, WP, PQ, CL);
        bp.room("bedroom", 1, 72, 42, 81, 49, F1, H1, WF, Block.CARPET_GRAY, CL);

        // the main hall last, so that its walls are its own on every side: cream plaster over a dark dado
        MapData.Room hall = bp.room("hall", 0, 40, 26, 57, 43, G, TALL, PL, Block.TILE_CHECKER, CL);
        bp.reface(39, 25, 58, 44, G, G + 1, PL, WD);
        bp.reface(39, 25, 58, 44, F1 - 2, F1 - 1, PL, WD);
        bp.reface(39, 25, 58, 44, TOP - 1, TOP - 1, PL, Block.STONE_CARVED);
        // faced in grey stone outside, a carved band at each floor and under the eaves, a darker plinth
        for (int[] side : new int[][]{{a0 - 1, b0 - 1, a1 + 1, b0 - 1}, {a0 - 1, b1 + 1, a1 + 1, b1 + 1}, {a0 - 1, b0 - 1, a0 - 1, b1 + 1}, {a1 + 1, b0 - 1, a1 + 1, b1 + 1}}) {
            bp.reface(side[0], side[1], side[2], side[3], G, ROOF, 0, S);
            bp.reface(side[0], side[1], side[2], side[3], F1 - 2, F1 - 1, 0, Block.STONE_CARVED);
            bp.reface(side[0], side[1], side[2], side[3], TOP, ROOF, 0, Block.STONE_CARVED);
            bp.reface(side[0], side[1], side[2], side[3], G, G, 0, Block.STONE_DARK);
        }

        // ---- the grand staircase: up the middle to a landing against the back wall, a gallery round the other three sides
        bp.flight(46, 33, 0, 1, G, 7, 6, Block.STAIRS_WOOD, WD);
        // its two closed sides: a panelled string that rises with the treads, an iron balustrade on it
        for (int b = 33; b <= 39; b++) for (int a : new int[]{45, 52}) { for (int y = G; y <= G + (b - 33); y++) bp.put(a, b, y, WD); bp.put(a, b, G + (b - 33) + 1, Block.IRON_FENCE); }
        for (int[] g2 : new int[][]{{40, 40, 57, 43}, {40, 26, 42, 39}, {55, 26, 57, 39}, {43, 26, 54, 27}}) { bp.box(g2[0], g2[1], g2[2], g2[3], F1 - 1, F1 - 1, PQ); bp.box(g2[0], g2[1], g2[2], g2[3], F1 - 2, F1 - 2, WD); }
        for (int b = 28; b <= 39; b++) { bp.put(42, b, F1, Block.IRON_FENCE); bp.put(55, b, F1, Block.IRON_FENCE); }
        for (int a = 43; a <= 54; a++) { bp.put(a, 27, F1, Block.IRON_FENCE); if (a < 46 || a > 51) bp.put(a, 40, F1, Block.IRON_FENCE); }
        for (int a = 43; a <= 54; a += 11) for (int y = G; y < F1 - 2; y++) { bp.put(a, 28, y, Block.MARBLE); bp.put(a, 39, y, Block.MARBLE); }      // the columns that carry the gallery
        bp.box(48, 26, 49, 32, G - 1, G - 1, Block.CARPET_RED);
        // light: the great chandelier over the foot of the stairs, lamps under the galleries, candles someone has lit
        bp.prop("chandelier", 48, 30, 49, 31, TOP - 4, 0, -1);
        for (int y = TOP - 2; y < TOP; y++) bp.prop("chain_chandelier", 48, 30, 49, 31, y, 0, -1);           // hung by a chain, not a fence
        bp.prop("sconce", 40, 31, G + 2, -1, 0); bp.prop("sconce", 57, 37, G + 2, 1, 0); bp.prop("sconce", 44, 26, G + 2, 0, -1); bp.prop("sconce", 53, 26, G + 2, 0, -1);
        bp.prop("bench_wood", 40, 35, 40, 36, G, -1, 0); bp.prop("table_side", 40, 33, G, -1, 0); bp.prop("painting_landscape", 46, 26, 47, 26, F1 + 2, 0, -1); bp.prop("painting_landscape", 50, 26, 51, 26, F1 + 2, 0, -1);
        bp.prop("painting_portrait", 40, 29, F1 + 1, -1, 0); bp.prop("painting_lady", 40, 36, F1 + 1, -1, 0); bp.prop("painting_portrait", 57, 36, F1 + 1, 1, 0); bp.prop("painting_lady", 57, 29, F1 + 1, 1, 0);
        bp.prop("pedestal_urn", 42, 39, F1, 0, 1); bp.prop("pedestal_urn", 55, 39, F1, 0, 1);
        for (int[] c : new int[][]{{47, 30}, {50, 31}, {47, 31}, {50, 30}}) bp.lampAt(c[0], c[1], TOP - 1);
        bp.lampAt(41, 33, F1 - 3); bp.lampAt(56, 33, F1 - 3); bp.lampAt(48, 42, TOP - 1); bp.lampAt(41, 42, F1 - 3); bp.lampAt(56, 42, F1 - 3);
        // what stands in the hall: armour at the foot of the stairs, the clock, the family along the walls, plants gone to sticks
        bp.prop("suit_of_armor", 44, 32, G, 0, 1); bp.prop("suit_of_armor", 53, 32, G, 0, 1);
        bp.prop("grandfather_clock", 40, 38, G, -1, 0);
        bp.prop("table_side", 57, 30, G, 1, 0); bp.prop("pedestal_urn", 57, 36, G, 1, 0); bp.prop("coat_stand", 41, 27, G, 0, -1); bp.prop("umbrella_stand", 43, 26, G, 0, -1);
        bp.prop("plant_dead", 56, 27, G, 0, -1); bp.prop("plant_palm", 40, 28, G, -1, 0);
        bp.prop("rug_persian", 47, 28, 49, 29, G, 0, -1);
        for (int b = 29; b <= 35; b += 6) { bp.prop(b == 29 ? "painting_portrait" : "painting_lady", 40, b, G + 2, -1, 0); bp.prop(b == 29 ? "painting_lady" : "painting_portrait", 57, b, G + 2, 1, 0); }
        bp.prop("photo_frames", 57, 34, G + 1, 1, 0);
        bp.prop("painting_landscape", 44, 43, 45, 43, F1 + 1, 0, 1); bp.prop("painting_landscape", 52, 43, 53, 43, F1 + 1, 0, 1);
        bp.prop("tapestry", 48, 43, 49, 43, F1 + 1, 0, 1);
        bp.prop("statue_bust", 40, 41, F1, -1, 0); bp.prop("statue_bust", 57, 41, F1, 1, 0);
        bp.prop("armchair", 41, 26, F1, 0, -1); bp.prop("table_side", 42, 26, F1, 0, -1); bp.prop("armchair_green", 56, 26, F1, 0, -1);
        bp.prop("cobwebs", 40, 26, TOP - 1, -1, 0); bp.prop("cobwebs", 57, 43, TOP - 1, 1, 0);
        bp.prop("papers_floor", 51, 30, G, 0, -1);
        hall.wallBlock = PL;

        // ---- the dining room: the long table still laid for the last evening, the hearth, the clock, the balcony
        for (int a = 24; a <= 32; a += 2) { bp.prop(a == 26 || a == 30 ? "table_laid" : "table_dining", a, 31, a + 1, 31, G, 0, -1); }
        for (int a = 24; a <= 33; a++) { if (a % 3 != 2) bp.prop("chair_dining", a, 30, G, 0, -1); if (a % 4 != 1) bp.prop("chair_dining", a, 32, G, 0, 1); }
        bp.prop("chair_dining", 23, 31, G, -1, 0); bp.prop("chair_dining", 34, 31, G, 1, 0);
        bp.prop("fireplace_cold", 20, 30, 20, 32, G, -1, 0);
        bp.prop("trophy_head", 20, 31, G + 3, -1, 0);
        bp.prop("firewood", 20, 34, G, -1, 0); bp.prop("fire_irons", 20, 29, G, -1, 0);
        bp.prop("rug_persian", 27, 33, 29, 34, G, 0, -1);
        bp.prop("china_cabinet", 38, 27, G, 1, 0); bp.prop("china_cabinet", 38, 35, G, 1, 0); bp.prop("sideboard", 27, 26, 28, 26, G, 0, -1); bp.prop("sideboard", 33, 26, 34, 26, G, 0, -1);
        bp.prop("grandfather_clock", 38, 29, G, 1, 0);
        bp.prop("chandelier", 25, 31, 26, 32, TOP - 5, 0, -1); bp.prop("chandelier", 31, 31, 32, 32, TOP - 5, 0, -1);
        for (int y = TOP - 3; y < TOP; y++) { bp.prop("chain_chandelier", 25, 31, 26, 32, y, 0, -1); bp.prop("chain_chandelier", 31, 31, 32, 32, y, 0, -1); }
        for (int a = 21; a <= 36; a += 5) bp.prop("sconce", a, 26, G + 2, 0, -1);
        bp.prop("sconce", 22, 37, G + 2, 0, 1); bp.prop("sconce", 36, 37, G + 2, 0, 1); bp.prop("rug_persian", 24, 28, 26, 29, G, 0, -1); bp.prop("rug_persian", 31, 33, 33, 34, G, 0, -1);
        bp.prop("pedestal_urn", 38, 33, G, 1, 0); bp.prop("statue_bust", 20, 36, G, -1, 0); bp.prop("plant_palm", 38, 37, G, 1, 0); bp.prop("armchair", 21, 27, G, 0, -1); bp.prop("table_side", 22, 27, G, 0, -1);
        bp.lampAt(24, 31, TOP - 1); bp.lampAt(33, 32, TOP - 1);
        for (int a = 23; a <= 35; a += 6) { bp.prop("curtain_red", a, 26, G + 1, 0, -1); bp.prop("painting_portrait", a + 1, 37, G + 2, 0, 1); }
        bp.box(20, 35, 38, 37, F1 - 1, F1 - 1, PQ); bp.box(20, 35, 38, 37, F1 - 2, F1 - 2, WD);
        for (int a = 20; a <= 38; a++) bp.put(a, 35, F1, Block.IRON_FENCE);                           // the balcony along the back
        bp.loot(20, 27, G, Block.CABINET, "residential"); bp.loot(38, 36, G, Block.CABINET, "food");
        bp.loot(57, 28, G, Block.CABINET, "residential"); bp.loot(16, 28, G, Block.CRATE, "residential"); bp.loot(36, 49, F1, Block.DESK, "office"); bp.loot(60, 37, F1, Block.CABINET, "residential");
        bp.prop("glass_shards", 30, 28, G, 0, -1); bp.prop("blood_old", 35, 33, G, 0, -1);

        // ---- the kitchen: range and sinks along the west wall, a block in the middle with what was being cut on it
        bp.prop("range_cast", 16, 40, 16, 41, G, -1, 0); bp.prop("counter_kitchen", 16, 42, G, -1, 0); bp.prop("sink_kitchen", 16, 43, G, -1, 0); bp.prop("counter_kitchen", 16, 44, G, -1, 0); bp.prop("counter_kitchen", 16, 45, G, -1, 0);
        bp.prop("pots_hanging", 16, 40, 16, 41, G + 4, -1, 0);
        bp.loot(16, 48, G, Block.FRIDGE, "food"); bp.loot(26, 49, G, Block.CABINET, "food");
        bp.prop("butcher_block", 21, 44, G, 0, -1); bp.prop("table_kitchen", 21, 46, 22, 46, G, 0, -1); bp.prop("chair_wood", 21, 47, G, 0, 1); bp.prop("chair_wood", 23, 46, G, 1, 0);
        bp.prop("shelf_cans", 19, 49, G, 0, 1); bp.prop("shelf_empty", 20, 49, G, 0, 1); bp.prop("wine_rack", 22, 49, G, 0, 1); bp.prop("barrel_wine", 24, 49, G, 0, 1); bp.prop("sacks", 26, 40, G, 1, 0);
        bp.prop("meat_hooks", 24, 41, 25, 41, G + 4, 0, -1); bp.prop("blood_old", 24, 42, G, 0, -1); bp.prop("blood_old", 22, 41, G, 0, -1);
        bp.prop("dresser_plates", 22, 39, 23, 39, G, 0, -1); bp.prop("table_work", 19, 41, 21, 41, G, 0, -1); bp.prop("shelf_wall", 26, 43, G + 2, 1, 0); bp.prop("shelf_wall", 26, 46, G + 2, 1, 0);
        bp.prop("barrel_wine", 26, 48, G, 1, 0); bp.prop("mop_bucket", 18, 39, G, 0, -1); bp.prop("firewood", 16, 39, G, -1, 0); bp.prop("coat_hooks", 18, 49, G + 1, 0, 1); bp.prop("clock_wall", 20, 39, G + 3, 0, -1);
        bp.lampAt(21, 44, G + 4); bp.lampAt(21, 47, G + 4);
        bp.lair("butcher", 22, 42, G);

        // ---- the bar and music room: the piano, the billiard table, the back bar with what is left on it
        bp.prop("bar_back", 29, 39, 30, 39, G, 0, -1); bp.prop("bar_back", 32, 39, 33, 39, G, 0, -1);
        for (int a = 29; a <= 33; a++) bp.put(a, 41, G, Block.COUNTER);
        for (int a = 29; a <= 33; a += 2) bp.prop("stool_bar", a, 42, G, 0, 1);
        bp.prop("beer_taps", 31, 41, G + 1, 0, -1);
        bp.prop("piano_grand", 35, 45, 36, 46, G, 0, -1); bp.prop("piano_stool", 35, 44, G, 0, -1); bp.prop("music_stand", 37, 44, G, 0, -1);
        bp.prop("billiard_table", 30, 46, 31, 46, G, 0, -1); bp.prop("rug_bear", 32, 43, 33, 44, G, 0, -1);
        bp.prop("sofa_leather", 28, 48, 28, 49, G, -1, 0); bp.prop("gramophone", 28, 46, G, -1, 0); bp.prop("armchair", 30, 49, G, 0, 1); bp.prop("table_coffee", 32, 49, 33, 49, G, 0, 1);
        bp.prop("trophy_head", 38, 42, G + 2, 1, 0); bp.prop("painting_landscape", 34, 49, 35, 49, G + 2, 0, 1);
        bp.prop("chandelier_small", 33, 45, G + 4, 0, -1); bp.lampAt(31, 44, G + 4);
        bp.loot(38, 49, G, Block.CABINET, "residential");

        // ---- the east wing: the gallery of statues, the bathroom, the store room
        for (int a = 61; a <= 69; a += 4) for (int b = 28; b <= 36; b += 4) bp.prop((a + b) % 8 == 1 ? "statue_bust" : (a + b) % 8 == 5 ? "pedestal_urn" : "statue_figure", a, b, G, 0, -1);
        bp.box(64, 25, 66, 25, G + 1, G + 3, Block.GLASS_STAINED);
        bp.prop("rug_runner", 65, 29, 65, 31, G, 0, -1); bp.prop("rug_runner", 65, 33, 65, 35, G, 0, -1);
        bp.prop("painting_landscape", 59, 31, 59, 32, G + 2, -1, 0); bp.prop("painting_portrait", 70, 34, G + 2, 1, 0); bp.prop("painting_lady", 70, 30, G + 2, 1, 0);
        bp.prop("bench_wood", 62, 37, 63, 37, G, 0, 1); bp.lampAt(65, 32, G + 4);
        bp.prop("bathtub", 76, 31, 77, 31, G, 0, 1); bp.prop("washbasin", 74, 26, G, 0, -1); bp.prop("toilet_p", 72, 26, G, 0, -1); bp.prop("mirror_gilt", 74, 26, G + 2, 0, -1); bp.prop("screen_folding", 77, 27, 78, 27, G, 0, -1);
        bp.loot(72, 31, G, Block.MED_CABINET, "medical"); bp.lampAt(75, 28, G + 4);
        bp.loot(78, 37, G, Block.CRATE, "residential"); bp.prop("crate_stack", 72, 37, G, 0, 1); bp.prop("trunk", 74, 37, G, 0, 1); bp.prop("trunk", 75, 37, G, 0, 1); bp.prop("boxes_cardboard", 72, 33, G, -1, 0); bp.prop("shelf_empty", 78, 33, G, 1, 0); bp.prop("sacks", 78, 35, G, 1, 0);
        bp.prop("lamp_hanging_off", 75, 35, G + 4, 0, -1); bp.prop("cobwebs", 72, 33, G + 4, -1, 0);
        for (int a = 61; a <= 79; a += 6) { bp.prop("rug_runner", a, 39, a + 2, 39, G, -1, 0); }
        bp.prop("table_side", 68, 39, G, 0, -1); bp.prop("painting_small", 62, 39, G + 2, 0, -1); bp.prop("painting_small", 74, 39, G + 2, 0, -1); bp.prop("sconce", 71, 39, G + 2, 0, -1);
        bp.prop("suit_of_armor", 81, 30, G, 1, 0); bp.prop("painting_portrait", 81, 34, G + 2, 1, 0);
        // the trophy room
        bp.prop("desk_writing", 75, 43, 76, 43, G, 0, -1); bp.prop("armchair", 75, 44, G, 0, 1);
        for (int b = 44; b <= 48; b += 2) bp.loot(81, b, G, Block.BOOKSHELF, "office");
        bp.prop("bookcase", 72, 49, G, 0, 1); bp.prop("bookcase", 73, 49, G, 0, 1); bp.prop("globe", 79, 43, G, 0, -1);
        bp.prop("fireplace_cold", 72, 44, 72, 46, G, -1, 0); bp.prop("rug_bear", 74, 45, 75, 46, G, -1, 0);
        bp.prop("trophy_head", 77, 42, G + 2, 0, -1); bp.prop("trophy_head", 79, 49, G + 2, 0, 1); bp.prop("sofa_leather", 77, 48, 78, 48, G, 0, 1);
        bp.lampAt(76, 46, G + 4);

        // ---- the east stairs, and the passage behind the main ones
        bp.flight(61, 48, 1, 0, G, 7, 2, Block.STAIRS_WOOD, WD);
        bp.box(68, 48, 70, 49, F1 - 1, F1 - 1, PQ); bp.box(68, 48, 70, 49, F1 - 2, F1 - 2, WD);
        for (int a = 61; a <= 67; a++) { for (int y = G; y <= G + (a - 61); y++) bp.put(a, 47, y, WD); bp.put(a, 47, G + (a - 61) + 1, Block.IRON_FENCE); }
        // 1.3: the balustrade goes on round the stairwell on the floor above - along the open side of the well and across its
        // head - instead of ending in one post standing on its own at the top of the flight
        for (int a = 63; a <= 66; a++) bp.put(a, 47, F1, Block.IRON_FENCE);
        bp.put(63, 48, F1, Block.IRON_FENCE); bp.put(63, 49, F1, Block.IRON_FENCE);
        bp.lampAt(64, 44, G + 4); bp.lampAt(64, 44, TOP - 1);
        bp.prop("coat_hooks", 60, 42, G + 1, 0, -1); bp.prop("bench_wood", 62, 42, 63, 42, G, 0, -1); bp.prop("plant_dead", 70, 42, G, 0, -1);
        bp.prop("rug_runner", 44, 47, 46, 47, G, -1, 0); bp.prop("rug_runner", 51, 47, 53, 47, G, -1, 0); bp.prop("table_side", 48, 49, G, 0, 1); bp.prop("mirror_gilt", 49, 49, G + 1, 0, 1);
        bp.lampAt(48, 47, G + 4);
        // the west passage: where the house's staff went about
        bp.prop("coat_hooks", 16, 30, G + 1, -1, 0); bp.prop("bench_wood", 16, 32, 16, 33, G, -1, 0); bp.prop("boxes_cardboard", 16, 36, G, -1, 0); bp.prop("lamp_wall_off", 18, 34, G + 2, 1, 0);

        // ---- upstairs: the library
        for (int a = 17; a <= 37; a += 2) bp.prop(a % 6 == 3 ? "bookcase_ransacked" : "bookcase", a, 49, F1, 0, 1);
        for (int a = 19; a <= 35; a += 4) { bp.loot(a, 43, F1, Block.BOOKSHELF, "office"); bp.prop("bookcase", a + 1, 43, F1, 0, 1); }
        bp.prop("desk_writing", 26, 40, 27, 40, F1, 0, -1); bp.prop("armchair", 26, 41, F1, 0, 1);
        bp.prop("armchair_green", 33, 46, F1, 1, 0); bp.prop("armchair", 31, 46, F1, -1, 0); bp.prop("table_round", 32, 46, F1, 0, -1); bp.prop("rug_round", 31, 45, 32, 46, F1, 0, -1);
        bp.prop("globe", 37, 40, F1, 0, -1); bp.prop("ladder_lean", 21, 48, F1, 0, 1); bp.prop("papers_floor", 23, 46, F1, 0, -1); bp.prop("papers_floor", 29, 47, F1, 0, -1);
        bp.prop("chandelier_small", 22, 46, F1 + 4, 0, -1); bp.prop("chandelier_small", 33, 41, F1 + 4, 0, -1); bp.lampAt(27, 45, TOP - 1);
        // the master bedroom
        bp.prop("bed_four_poster", 64, 26, 65, 27, F1, 0, -1); bp.prop("nightstand_lamp", 63, 26, F1, 0, -1); bp.prop("nightstand_lamp", 66, 26, F1, 0, -1);
        bp.prop("wardrobe", 59, 30, F1, -1, 0); bp.prop("wardrobe_open", 59, 31, F1, -1, 0); bp.prop("vanity", 70, 30, F1, 1, 0); bp.prop("dresser", 59, 34, F1, -1, 0);
        bp.prop("fireplace_cold", 70, 33, 70, 35, F1, 1, 0); bp.prop("armchair", 68, 32, F1, 0, -1); bp.prop("armchair_green", 68, 36, F1, 0, 1);
        bp.prop("rug_persian", 63, 31, 65, 32, F1, 0, -1); bp.prop("screen_folding", 60, 36, 61, 36, F1, 0, 1); bp.prop("photo_frames", 70, 28, F1 + 1, 1, 0); bp.prop("painting_lady", 59, 34, F1 + 1, -1, 0);
        bp.loot(70, 37, F1, Block.SAFE, "residential"); bp.lampAt(64, 32, TOP - 1);
        // 1.1: the house's shotgun, where a house keeps one - on the floor of the open wardrobe in the master bedroom, a dozen shells beside it
        bp.item("weapon.pump12", 60, 31, F1); bp.item("ammo.shellsx12", 60, 32, F1);
        // the nursery: nobody has moved anything
        bp.prop("cot_child", 72, 26, F1, -1, 0); bp.prop("bed_single", 78, 26, 78, 27, F1, 0, -1); bp.prop("rocking_horse", 75, 30, F1, 0, 1); bp.prop("toy_blocks", 74, 28, F1, 0, -1);
        bp.prop("bookcase_low", 72, 31, F1, 0, 1); bp.prop("wardrobe", 78, 31, F1, 1, 0); bp.prop("rug_round", 74, 28, 75, 29, F1, 0, -1); bp.prop("photo_frames", 75, 26, F1 + 1, 0, -1);
        bp.loot(77, 31, F1, Block.CABINET, "residential"); bp.lampAt(75, 28, TOP - 1);
        // the sickroom: somebody is still being looked after here (a cupboard of medicine kept for the neighbourhood, a carer's bedroll)
        bp.prop("bed_hospital_p", 72, 33, 72, 34, F1, 0, -1); bp.prop("iv_stand", 73, 33, F1, 0, -1); bp.prop("cart_medicine", 74, 33, F1, 0, -1);
        bp.prop("bedroll", 77, 35, 77, 36, F1, 0, 1); bp.prop("candles_floor", 75, 37, F1, 0, 1); bp.prop("chair_wood", 73, 35, F1, -1, 0); bp.prop("table_side", 78, 33, F1, 1, 0);
        bp.loot(72, 37, F1, Block.MED_CABINET, "medical"); bp.prop("curtain_torn", 75, 33, F1 + 1, 0, -1); bp.prop("lantern_wall", 78, 36, F1 + 1, 1, 0);
        // the far bedroom
        bp.prop("bed_double", 77, 48, 78, 49, F1, 0, 1); bp.prop("nightstand_lamp", 76, 49, F1, 0, 1); bp.prop("wardrobe_open", 81, 45, F1, 1, 0); bp.prop("dresser", 81, 43, F1, 1, 0);
        bp.prop("trunk", 72, 49, F1, 0, 1); bp.prop("mirror_broken", 72, 46, F1 + 1, -1, 0); bp.prop("curtain_torn", 80, 49, F1 + 1, 0, 1); bp.prop("armchair", 72, 43, F1, -1, 0);
        bp.loot(73, 49, F1, Block.CABINET, "residential"); bp.lampAt(76, 46, TOP - 1);
        // the upper corridors and the gallery passage
        for (int a = 61; a <= 79; a += 6) bp.prop("rug_runner", a, 40, a + 2, 40, F1, -1, 0);
        bp.prop("painting_small", 66, 39, F1 + 1, 0, -1); bp.prop("painting_portrait", 72, 39, F1 + 1, 0, -1); bp.prop("table_side", 77, 39, F1, 0, -1); bp.prop("pedestal_urn", 81, 37, F1, 1, 0);
        bp.prop("rug_runner", 47, 47, 49, 47, F1, -1, 0); bp.prop("bench_wood", 44, 49, 45, 49, F1, 0, 1); bp.prop("painting_landscape", 51, 49, 52, 49, F1 + 1, 0, 1);
        bp.prop("bench_wood", 16, 30, 16, 31, F1, -1, 0); bp.prop("painting_small", 18, 33, F1 + 1, 1, 0);
        bp.lampAt(48, 47, TOP - 1); bp.lampAt(70, 40, TOP - 1); bp.lampAt(17, 32, TOP - 1);

        // ---- doors. Ground floor
        bp.door(48, 25, G, Block.DOOR_DOUBLE_WOOD, true); bp.door(49, 25, G, Block.DOOR_DOUBLE_WOOD, true);          // the front doors
        bp.door(39, 31, G, Block.DOOR_DOUBLE_WOOD, false); bp.door(39, 32, G, Block.DOOR_DOUBLE_WOOD, false);        // hall -> dining room
        bp.door(58, 31, G, Block.DOOR_DOUBLE_WOOD, false); bp.door(58, 32, G, Block.DOOR_DOUBLE_WOOD, false);        // hall -> gallery
        bp.door(44, 44, G, Block.DOOR_WOOD, false); bp.door(53, 44, G, Block.DOOR_WOOD, false);                      // hall -> the passage behind the stairs
        bp.door(48, 50, G, Block.DOOR_WOOD, true);                                                                   // the courtyard door
        bp.door(19, 28, G, Block.DOOR_WOOD, false); bp.door(17, 38, G, Block.DOOR_WOOD, false); bp.door(27, 44, G, Block.DOOR_WOOD, false);   // dining -> west passage -> kitchen -> bar
        bp.door(39, 47, G, Block.DOOR_WOOD, false);                                                                  // bar -> the back passage
        bp.door(58, 47, G, Block.DOOR_WOOD, false);                                                                  // back passage -> east stairs
        bp.door(65, 38, G, Block.DOOR_WOOD, false);                                                                  // gallery -> corridor
        bp.door(71, 28, G, Block.DOOR_WOOD, false); bp.opening(79, 28, G, 3, 1, true); bp.door(79, 35, G, Block.DOOR_WOOD, false);
        bp.door(64, 41, G, Block.DOOR_WOOD, false); bp.door(76, 41, G, Block.DOOR_WOOD, false);                      // corridor -> stairs, corridor -> the trophy room
        bp.opening(80, 39, G, 3, 2, true);
        // first floor
        bp.door(39, 42, F1, Block.DOOR_WOOD, false);
        bp.opening(39, 36, F1, 3, 1, false);
        bp.door(30, 38, F1, Block.DOOR_WOOD, false);                                                                 // balcony -> library
        bp.door(19, 36, F1, Block.DOOR_WOOD, false);                                                                 // balcony -> west passage
        bp.door(58, 40, F1, Block.DOOR_WOOD, false);                                                                 // hall gallery -> the east corridor
        bp.door(44, 44, F1, Block.DOOR_WOOD, false); bp.door(58, 47, F1, Block.DOOR_WOOD, false);
        bp.door(65, 38, F1, Block.DOOR_WOOD, false); bp.door(79, 28, F1, Block.DOOR_WOOD, false); bp.door(79, 35, F1, Block.DOOR_WOOD, false);
        bp.door(64, 41, F1, Block.DOOR_WOOD, false); bp.door(76, 41, F1, Block.DOOR_WOOD, false);
        bp.opening(80, 39, F1, 3, 2, true);
        bp.door(48, 25, F1, Block.DOOR_GLASS, false); bp.door(49, 25, F1, Block.DOOR_GLASS, false);                  // out onto the terrace over the porch

        // ---- outside. Tall windows on both floors, round the whole house
        for (int a = a0 + 1; a <= a1 - 1; a += 3) { if (Math.abs(a - 48) < 3) continue; for (int fl = 0; fl < 2; fl++) { int y = fl == 0 ? G + 1 : F1 + 1; bp.window(a, 25, a, 25, y, y + 2, Block.GLASS_DIRTY); bp.window(a, 50, a, 50, y, y + 2, Block.GLASS_DIRTY); } }
        for (int b = b0 + 2; b <= b1 - 2; b += 3) for (int fl = 0; fl < 2; fl++) { int y = fl == 0 ? G + 1 : F1 + 1; bp.window(15, b, 15, b, y, y + 2, Block.GLASS_DIRTY); bp.window(82, b, 82, b, y, y + 2, Block.GLASS_DIRTY); }
        // the two tall rooms take the upper row of windows too
        for (int a = 22; a <= 37; a += 3) bp.window(a, 25, a, 25, F1 + 1, F1 + 3, Block.GLASS_DIRTY);
        // the porch: six columns, a terrace on them, a pediment over that
        bp.mass(42, 20, 55, 24, G - 1, G - 1, Block.MARBLE);
        for (int a = 42; a <= 55; a += (a == 46 ? 5 : 2)) for (int y = G; y < F1 - 1; y++) if (a != 48 && a != 49 && a != 50) bp.put(a, 20, y, Block.MARBLE);
        bp.box(42, 20, 55, 24, F1 - 1, F1 - 1, Block.STONE_CARVED);
        for (int a = 42; a <= 55; a++) { bp.put(a, 20, F1, Block.IRON_FENCE); } for (int b = 21; b <= 24; b++) { bp.put(42, b, F1, Block.IRON_FENCE); bp.put(55, b, F1, Block.IRON_FENCE); }
        // the roof: a slate mansard stepping in, dormers on the front, four chimneys
        for (int k = 0; k < 4; k++) bp.mass(a0 - 1 + k, b0 - 1 + k, a1 + 1 - k, b1 + 1 - k, ROOF + k, ROOF + k, Block.ROOF_SLATE);
        for (int a = 22; a <= 76; a += 9) { bp.put(a, 25, ROOF, Block.PLASTER); bp.put(a, 25, ROOF + 1, Block.PLASTER); bp.put(a, 24, ROOF, Block.GLASS_DIRTY); bp.put(a + 1, 25, ROOF, Block.PLASTER); bp.put(a - 1, 25, ROOF, Block.PLASTER); }
        for (int[] ch : new int[][]{{20, 30}, {34, 46}, {64, 30}, {77, 46}}) bp.mass(ch[0], ch[1], ch[0] + 1, ch[1] + 1, ROOF, ROOF + 6, Block.BRICK_BURNT);
        for (int k = 0; k < 4; k++) bp.mass(43 + k * 2, 25, 54 - k * 2, 25, ROOF + k, ROOF + k, Block.STONE_CARVED);
        // abandoned: boards over some of the windows
        for (int a = a0 + 4; a <= a1 - 4; a += 12) for (int y = G + 1; y <= G + 3; y++) if (bp.at(a, 25, y) == Block.GLASS_DIRTY) bp.put(a, 25, y, Block.BOARDED);
        bp.entry(48, 23, G);                              // on the porch, in front of the doors
        // ---- finishes: every room is shown its own walls (the decal engine lines the side of a shared wall that is not its own)
        for (MapData.Room rm : bp.rec.rooms) switch (rm.type) {
            case "dining" -> bp.finish(rm, WP, WD, 2, PL);
            case "hall" -> { if (rm.height > 6) bp.finish(rm, PL, WD, 2, Block.STONE_CARVED); else bp.finish(rm, WP, WD, 1, 0); }
            case "kitchen" -> bp.finish(rm, PL, Block.TILE_WHITE, 2, 0);
            case "bathroom" -> bp.finish(rm, Block.TILE_GREEN, Block.TILE_WHITE, 2, 0);
            case "corridor", "stairs" -> bp.finish(rm, WP, WD, 1, 0);
            case "gallery" -> bp.finish(rm, WF, Block.MARBLE, 1, PL);
            case "bedroom", "sickroom" -> bp.finish(rm, WF, WD, 1, 0);
            case "nursery" -> bp.finish(rm, Block.PLASTER_BLUE, Block.DADO_BLUE, 1, 0);
            case "storage" -> bp.finish(rm, PL, 0, 0, 0);
            default -> bp.finish(rm, WD, 0, 0, 0);
        }
        // the runner up the grand staircase, and the tall hangings of the two tall rooms
        for (int b = 33; b <= 39; b++) for (int a = 47; a <= 50; a++) bp.prop("stair_carpet", a, b, G + (b - 33), 0, -1);
        for (int a = 23; a <= 35; a += 6) bp.prop("curtain_tall", a + 1, 26, F1, 0, -1);
        bp.prop("tapestry", 27, 37, 28, 37, F1 + 1, 0, 1); bp.prop("painting_landscape", 22, 37, 23, 37, F1 + 2, 0, 1); bp.prop("painting_landscape", 33, 37, 34, 37, F1 + 2, 0, 1);
        bp.prop("tapestry", 20, 28, 20, 29, G + 4, -1, 0); bp.prop("tapestry", 20, 33, 20, 34, G + 4, -1, 0);
        bp.prop("painting_portrait", 40, 32, F1 + 1, -1, 0); bp.prop("painting_lady", 57, 32, F1 + 1, 1, 0); bp.prop("curtain_tall", 45, 26, G + 1, 0, -1); bp.prop("curtain_tall", 52, 26, G + 1, 0, -1);
        for (int a = 22; a <= 36; a += 7) bp.prop("beam_ceiling", a, 27, a, 29, TOP - 1, -1, 0);
        halloran11(bp, G, F1, TOP, ROOF);
        // ---- the dead in the house: in the rooms furthest from the doors, and something in the kitchen
        bp.lair("infected", 30, 29, G); bp.lair("infected", 65, 32, G); bp.lair("infected", 76, 45, G); bp.lair("infected", 48, 47, G); bp.lair("infected", 33, 44, G);
        bp.lair("infected", 27, 45, F1); bp.lair("infected", 64, 33, F1); bp.lair("infected", 76, 45, F1); bp.lair("infected", 70, 40, F1); bp.lair("infected", 48, 41, F1);
        MapData.Building house = bp.done(lm);
        s.walkToStreet(house, Block.COBBLE);

        // ---- the grounds
        MapData.Building coach = lb.bld(s, lm, "lm_coach_house", 4, 58, 17, 69, 1, 2, "Guardhouse");
        lb.bld(s, lm, "lm_greenhouse", A - 13, 56, A - 6, 67, 3, 1, "Greenhouse");
        s.path(m - 1, B - 1, m - 1, 52, Block.COBBLE, 2);
        s.path(18, 63, m - 1, 63, Block.COBBLE, 2);
        // the fountain on the axis of the drive
        for (int a = m - 4; a <= m + 2; a++) for (int b = 8; b <= 14; b++) {
            int da = Math.abs(a - (m - 1)), db = Math.abs(b - 11);
            if (Math.max(da, db) == 3) { if (!s.kept(a, b)) s.set(a, b, G, Block.STONE_CARVED); }
            else if (!s.kept(a, b)) s.set(a, b, G - 1, Block.WATER);
        }
        s.set(m - 1, 11, G, Block.STONE_CARVED); s.set(m - 1, 11, G + 1, Block.STONE_CARVED); s.set(m - 1, 11, G + 2, Block.MARBLE);
        // the graveyard on the east side, and the forest everywhere else
        for (int a = A - 14; a <= A - 5; a += 3) for (int b = 28; b <= 46; b += 4) s.prop(a, b, Block.TOMBSTONE);
        for (int a = 3; a < A - 3; a += 5) for (int b = 3; b < B - 3; b += 6) {
            int ja = a + (a * 7 + b * 3) % 3, jb = b + (a * 5 + b) % 4;
            if (ja > 11 && ja < 87 && jb < 56) continue;                                             // not on the house, and not in front of it: the drive and the lawns are open to the gate
            if (Math.abs(ja - m) < 5 && jb < 20) continue;
            s.tree(ja, jb, 6 + (ja + jb) % 3, (ja * 3 + jb) % 4 == 0);
        }
        for (int a = 6; a < A - 6; a++) if (Math.abs(a - m) > 6 && a % 2 == 0 && s.open(a, 5)) s.set(a, 5, G, Block.HEDGE);
        // the commune keeps to the grounds: tents by the gate and the guardhouse, a fire, a watch on two corners
        s.sandbags(m - 8, 3, 5, true); s.sandbags(m + 4, 3, 5, true);
        s.tent(22, 60, Block.CANVAS); s.tent(30, 70, Block.TARP);
        s.prop(26, 66, Block.BARREL_FIRE);
        s.crate(24, 66, Block.CRATE, "food"); s.crate(20, 72, Block.CRATE, "residential");
        s.tower(2, 2, 7, "Mansion West Watch", lm.faction);
        s.tower(A - 5, B - 5, 7, "Mansion East Watch", lm.faction);
        s.lampPost(m - 4, 2); s.lampPost(m + 3, 2);
        s.post(lm.name + " Gate", "guard", lm.faction, m, 1, G);
        s.post(lm.name, lm.headquarters ? "hq" : "guard", lm.faction, s.local(coach.entryX, coach.entryZ)[0], s.local(coach.entryX, coach.entryZ)[1], G);
        if (lm.headquarters) s.post(lm.name + " Yard", "patrol", lm.faction, 24, 64, G);
    }

    /**
     * 1.1: the second furnishing of the Halloran house. 1.0 put the right things in the right rooms and left the
     * middle of every floor bare: this is what a house that was lived in for a century has between its walls -
     * something to sit on in every passage, a second and a third thing on every wall, curtains at every window, the
     * library's shelves standing out into the room, a store room that is full. And outside, a front that is not one
     * flat wall: pilasters between the bays, a cornice under the roof.
     */
    private static void halloran11(Blueprint bp, int G0, int F1, int TOP, int ROOF) {
        // ---- store room
        bp.prop("shelf_cans", 74, 33, G0, 0, -1); bp.prop("shelf_parts", 75, 33, G0, 0, -1); bp.prop("shelf_empty", 76, 33, G0, 0, -1); bp.prop("barrel_wine", 72, 35, G0, -1, 0); bp.prop("crate_stack", 76, 37, G0, 0, 1);
        bp.prop("sacks", 73, 36, G0, 0, -1); bp.prop("boxes_cardboard", 75, 35, G0, 0, -1); bp.prop("ladder_lean", 77, 33, G0, 0, -1); bp.prop("firewood", 72, 34, G0, -1, 0); bp.prop("papers_floor", 76, 35, G0, 0, -1); bp.prop("cobwebs", 78, 37, G0 + 4, 1, 0);
        // ---- the east stairs, below and above
        bp.prop("grandfather_clock", 59, 44, G0, -1, 0); bp.prop("table_side", 59, 46, G0, -1, 0); bp.prop("painting_landscape", 65, 42, 66, 42, G0 + 2, 0, -1); bp.prop("rug_persian", 63, 44, 65, 45, G0, 0, -1); bp.prop("armchair", 69, 44, G0, 1, 0);
        bp.prop("umbrella_stand", 68, 42, G0, 0, -1); bp.prop("pedestal_urn", 70, 46, G0, 1, 0); bp.prop("sconce", 59, 45, G0 + 2, -1, 0); bp.prop("boxes_cardboard", 59, 49, G0, -1, 0); bp.prop("painting_portrait", 70, 44, G0 + 2, 1, 0);
        bp.prop("bench_wood", 62, 42, 63, 42, F1, 0, -1); bp.prop("bookcase", 59, 43, F1, -1, 0); bp.prop("bookcase", 59, 44, F1, -1, 0); bp.prop("painting_portrait", 66, 42, F1 + 1, 0, -1); bp.prop("table_side", 70, 44, F1, 1, 0);
        bp.prop("plant_palm", 70, 42, F1, 1, 0); bp.prop("armchair", 59, 46, F1, -1, 0); bp.prop("chandelier_small", 64, 45, F1 + 4, 0, -1); bp.prop("mirror_gilt", 70, 45, F1 + 1, 1, 0); bp.prop("trunk", 60, 42, F1, 0, -1); bp.prop("rug_persian", 63, 44, 65, 45, F1, 0, -1);
        // ---- the passage behind the stairs, both floors
        bp.prop("sideboard", 51, 49, 52, 49, G0, 0, 1); bp.prop("armchair", 41, 49, G0, 0, 1); bp.prop("coat_stand", 57, 45, G0, 1, 0); bp.prop("painting_small", 43, 49, G0 + 2, 0, 1); bp.prop("plant_palm", 40, 49, G0, -1, 0); bp.prop("sconce", 41, 45, G0 + 2, 0, -1); bp.prop("sconce", 56, 45, G0 + 2, 0, -1);
        bp.prop("pedestal_urn", 55, 49, G0, 0, 1); bp.prop("painting_portrait", 48, 45, G0 + 2, 0, -1);
        bp.prop("table_side", 42, 49, F1, 0, 1); bp.prop("statue_bust", 40, 49, F1, -1, 0); bp.prop("armchair", 55, 49, F1, 0, 1); bp.prop("plant_dead", 57, 45, F1, 1, 0); bp.prop("painting_small", 46, 49, F1 + 2, 0, 1); bp.prop("painting_portrait", 48, 45, F1 + 1, 0, -1);
        bp.prop("sconce", 41, 45, F1 + 2, 0, -1); bp.prop("sconce", 56, 45, F1 + 2, 0, -1); bp.prop("pedestal_urn", 57, 49, F1, 1, 0); bp.prop("grandfather_clock", 40, 46, F1, -1, 0); bp.prop("sideboard", 49, 49, 50, 49, F1, 0, 1); bp.prop("chandelier_small", 48, 47, F1 + 4, 0, -1);
        // ---- the west passage, both floors
        bp.prop("shelf_wall", 18, 30, G0 + 2, 1, 0); bp.prop("mop_bucket", 16, 27, G0, -1, 0); bp.prop("crate_stack", 16, 35, G0, -1, 0); bp.prop("painting_small", 18, 32, G0 + 2, 1, 0); bp.prop("sacks", 18, 35, G0, 1, 0);
        bp.prop("bookcase_low", 18, 29, F1, 1, 0); bp.prop("table_side", 16, 34, F1, -1, 0); bp.prop("plant_dead", 16, 27, F1, -1, 0); bp.prop("rug_runner", 17, 29, 17, 31, F1, 0, -1); bp.prop("sconce", 18, 32, F1 + 2, 1, 0); bp.prop("armchair", 16, 28, F1, -1, 0);
        // ---- the east corridors, both floors
        bp.prop("pedestal_urn", 78, 40, G0, 0, 1); bp.prop("bench_wood", 72, 40, 73, 40, G0, 0, 1); bp.prop("statue_bust", 62, 40, G0, 0, 1); bp.prop("plant_palm", 70, 40, G0, 0, 1); bp.prop("table_side", 81, 27, G0, 1, 0); bp.prop("pedestal_urn", 81, 36, G0, 1, 0); bp.prop("rug_runner", 80, 30, 80, 32, G0, 0, -1);
        bp.prop("sideboard", 68, 39, 69, 39, F1, 0, -1); bp.prop("armchair", 74, 39, F1, 0, -1); bp.prop("bookcase_low", 78, 40, F1, 0, 1); bp.prop("painting_small", 81, 31, F1 + 1, 1, 0); bp.prop("table_side", 81, 28, F1, 1, 0); bp.prop("armchair", 81, 33, F1, 1, 0); bp.prop("plant_dead", 62, 39, F1, 0, -1);
        // ---- the gallery
        bp.prop("tapestry", 59, 28, 59, 29, G0 + 2, -1, 0); bp.prop("painting_landscape", 62, 26, 63, 26, G0 + 2, 0, -1); bp.prop("painting_landscape", 67, 26, 68, 26, G0 + 2, 0, -1); bp.prop("plant_palm", 59, 26, G0, -1, 0); bp.prop("plant_palm", 70, 26, G0, 1, 0);
        bp.prop("china_cabinet", 59, 35, G0, -1, 0); bp.prop("china_cabinet", 70, 36, G0, 1, 0); bp.prop("rug_persian", 64, 31, 66, 32, G0, 0, -1); bp.prop("bench_wood", 66, 37, 67, 37, G0, 0, 1); bp.prop("chandelier_small", 65, 30, G0 + 4, 0, -1); bp.prop("chandelier_small", 65, 34, G0 + 4, 0, -1);
        bp.prop("barrier_rope", 62, 30, 63, 30, G0, 0, -1); bp.prop("barrier_rope", 67, 34, 68, 34, G0, 0, -1); bp.prop("sconce", 59, 33, G0 + 2, -1, 0); bp.prop("sconce", 70, 28, G0 + 2, 1, 0); bp.prop("suit_of_armor", 59, 37, G0, -1, 0);
        // ---- the bathroom
        bp.prop("radiator", 78, 29, G0, 1, 0); bp.prop("shelf_wall", 72, 29, G0 + 2, -1, 0); bp.prop("rug_round", 74, 28, 75, 29, G0, 0, -1); bp.prop("coat_hooks", 76, 26, G0 + 1, 0, -1); bp.prop("table_side", 72, 30, G0, -1, 0); bp.prop("plant_dead", 78, 26, G0, 1, 0); bp.prop("washing_tub", 73, 31, G0, 0, 1);
        // ---- the library: the shelves stand out into the room in bays, tables between them, somewhere to read at every window
        for (int a = 17; a <= 37; a++) { if (a >= 29 && a <= 31) continue; bp.prop(a % 5 == 0 ? "bookcase_ransacked" : "bookcase", a, 39, F1, 0, -1); }
        for (int a : new int[]{20, 24}) for (int b = 45; b <= 47; b++) { bp.prop("bookcase", a, b, F1, -1, 0); }
        bp.prop("table_dining", 28, 41, 29, 41, F1, 0, -1); bp.prop("chair_dining", 28, 42, F1, 0, 1); bp.prop("chair_dining", 29, 42, F1, 0, 1); bp.prop("armchair", 16, 44, F1, -1, 0); bp.prop("table_side", 16, 45, F1, -1, 0); bp.prop("armchair_green", 16, 46, F1, -1, 0);
        bp.prop("rug_persian", 26, 45, 28, 46, F1, 0, -1); bp.prop("table_coffee", 36, 44, 37, 44, F1, 0, -1); bp.prop("sofa_leather", 36, 46, 37, 46, F1, 0, 1); bp.prop("statue_bust", 38, 41, F1, 1, 0); bp.prop("harp", 22, 41, F1, 0, -1); bp.prop("easel", 34, 41, F1, 0, -1);
        bp.prop("chandelier_small", 27, 45, F1 + 4, 0, -1); bp.prop("chandelier_small", 18, 42, F1 + 4, 0, -1); bp.prop("chandelier_small", 36, 46, F1 + 4, 0, -1); bp.prop("cobwebs", 16, 39, F1 + 4, -1, 0); bp.prop("papers_floor", 33, 43, F1, 0, -1); bp.prop("boxes_cardboard", 38, 49, F1, 1, 0);
        // ---- the master bedroom
        bp.prop("trunk", 64, 29, F1, 0, -1); bp.prop("table_round", 67, 34, F1, 0, -1); bp.prop("chandelier_small", 64, 31, F1 + 4, 0, -1); bp.prop("painting_landscape", 64, 26, 65, 26, F1 + 3, 0, -1); bp.prop("painting_portrait", 61, 26, F1 + 1, 0, -1);
        bp.prop("chair_dining", 69, 30, F1, 1, 0); bp.prop("bookcase_low", 59, 28, F1, -1, 0); bp.prop("plant_palm", 70, 26, F1, 1, 0); bp.prop("coat_stand", 59, 26, F1, -1, 0); bp.prop("rug_bear", 67, 31, 68, 32, F1, 0, -1); bp.prop("sofa_floral", 62, 37, 63, 37, F1, 0, 1);
        bp.prop("table_side", 65, 37, F1, 0, 1); bp.prop("mirror_gilt", 59, 33, F1 + 1, -1, 0); bp.prop("papers_floor", 62, 30, F1, 0, -1); bp.prop("gramophone", 66, 37, F1, 0, 1);
        // ---- the nursery, the sickroom, the far bedroom
        bp.prop("toy_blocks", 76, 28, F1, 0, -1); bp.prop("table_side", 72, 29, F1, -1, 0); bp.prop("painting_small", 72, 28, F1 + 1, -1, 0); bp.prop("shelf_wall", 78, 29, F1 + 2, 1, 0); bp.prop("bookcase_low", 74, 31, F1, 0, 1); bp.prop("chair_wood", 76, 30, F1, 0, 1); bp.prop("armchair", 78, 29, F1, 1, 0);
        bp.prop("wheelchair", 76, 34, F1, 0, -1); bp.prop("first_aid_box", 72, 35, F1 + 1, -1, 0); bp.prop("shelf_wall", 78, 34, F1 + 2, 1, 0); bp.prop("blood_old", 74, 35, F1, 0, -1); bp.prop("armchair", 78, 37, F1, 1, 0); bp.prop("washbasin", 76, 33, F1, 0, -1); bp.prop("rug_round", 74, 35, 75, 36, F1, 0, -1);
        bp.prop("rug_persian", 75, 45, 77, 46, F1, 0, -1); bp.prop("desk_writing", 73, 42, 74, 42, F1, 0, -1); bp.prop("chair_wood", 73, 43, F1, 0, 1); bp.prop("bookcase", 78, 42, F1, 0, -1); bp.prop("bookcase", 79, 42, F1, 0, -1); bp.prop("chandelier_small", 76, 45, F1 + 4, 0, -1);
        bp.prop("nightstand_lamp", 79, 49, F1, 0, 1); bp.prop("coat_stand", 81, 48, F1, 1, 0); bp.prop("painting_landscape", 74, 49, 75, 49, F1 + 2, 0, 1); bp.prop("table_side", 72, 45, F1, -1, 0); bp.prop("papers_floor", 75, 44, F1, 0, -1);
        // ---- the study, the bar, the kitchen: the middle of the floor
        bp.prop("plant_palm", 72, 42, G0, -1, 0); bp.prop("table_coffee", 77, 46, 78, 46, G0, 0, -1); bp.prop("painting_landscape", 73, 42, 74, 42, G0 + 2, 0, -1); bp.prop("chandelier_small", 76, 45, G0 + 4, 0, -1); bp.prop("statue_bust", 81, 42, G0, 1, 0);
        bp.prop("table_round", 34, 42, G0, 0, -1); bp.prop("chair_dining", 35, 42, G0, 1, 0); bp.prop("chair_dining", 34, 43, G0, 0, 1); bp.prop("plant_palm", 38, 39, G0, 1, 0); bp.prop("painting_portrait", 28, 44, G0 + 2, -1, 0); bp.prop("harp", 37, 48, G0, 1, 0);
        // ---- curtains at every window that has none
        for (int[] rm : new int[][]{{59, 26, 70, 37}, {72, 26, 78, 31}, {72, 42, 81, 49}, {59, 42, 70, 49}, {28, 39, 38, 49}, {40, 45, 57, 49}, {80, 26, 81, 38}}) bp.curtains(rm[0], rm[1], rm[2], rm[3], G0 + 1, "curtain_red", "curtain_torn");
        for (int[] rm : new int[][]{{16, 39, 38, 49}, {59, 26, 70, 37}, {72, 26, 78, 31}, {72, 33, 78, 37}, {72, 42, 81, 49}, {59, 42, 70, 49}, {40, 45, 57, 49}, {80, 26, 81, 38}, {16, 26, 18, 37}}) bp.curtains(rm[0], rm[1], rm[2], rm[3], F1 + 1, "curtain_red", "curtain_torn");
        // ---- outside: pilasters between the bays, front and back and round the ends; a cornice under the roof; a sill course
        for (int a : new int[]{14, 23, 32, 41, 56, 65, 74, 83}) for (int y = G0; y <= ROOF; y++) { if (a < 41 || a > 56) bp.put(a, 24, y, y == G0 ? Block.STONE_DARK : Block.STONE_CARVED); bp.put(a, 52, y, y == G0 ? Block.STONE_DARK : Block.STONE_CARVED); }
        for (int b : new int[]{25, 34, 42, 51}) for (int y = G0; y <= ROOF; y++) { bp.put(13, b, y, y == G0 ? Block.STONE_DARK : Block.STONE_CARVED); bp.put(84, b, y, y == G0 ? Block.STONE_DARK : Block.STONE_CARVED); }
        for (int a = 13; a <= 84; a++) { if (a < 42 || a > 55) bp.put(a, 24, ROOF, Block.STONE_CARVED); bp.put(a, 52, ROOF, Block.STONE_CARVED); }
        for (int b = 24; b <= 52; b++) { bp.put(13, b, ROOF, Block.STONE_CARVED); bp.put(84, b, ROOF, Block.STONE_CARVED); }
        // taller dormers along the front of the roof, each with its own little gable
        for (int a = 22; a <= 76; a += 9) { if (Math.abs(a - 48) < 8) continue; for (int da = -1; da <= 1; da++) { bp.put(a + da, 26, ROOF + 1, da == 0 ? Block.GLASS_DIRTY : Block.PLASTER); bp.put(a + da, 26, ROOF + 2, Block.ROOF_SLATE); } bp.put(a, 26, ROOF + 3, Block.ROOF_SLATE); }
    }

    // ================================================================== THE POWER PLANT
    private static final int P = 7;      // one storey: five clear, a ceiling, the floor above

    static void powerPlant(LandmarkBuilder lb, Site s, MapData.Landmark lm) {
        int A = s.A, B = s.B;
        s.ground(Block.CONCRETE_FLOOR);
        s.enclose(0, 0, A - 1, B - 1, Block.CONCRETE_PANEL, 3, Block.BARBED, new int[]{60, 0, 65, 0}, new int[]{A - 15, 0, A - 12, 0});
        s.path(62, 0, 62, 17, Block.ASPHALT, 5);
        s.path(2, 17, A - 6, 17, Block.ASPHALT, 3);
        Blueprint bp = new Blueprint(s, lm, "lm_cnpp", lm.name, "plant");
        final int W = Block.CONCRETE_PANEL, D = Block.CONCRETE_DARK, F = Block.CONCRETE_FLOOR, L = Block.LINOLEUM, C = Block.CONCRETE, CP = Block.CEILING_PANEL, PG = Block.PAINT_GREEN;
        final int L1 = G + P, L2 = G + 2 * P, H = 5;

        // ---- the turbine hall: one room the length of the plant, fourteen high, four sets in a row
        bp.room("turbine", 0, 8, 54, 97, 70, G, 14, W, F, Block.ROOF_METAL);
        bp.reface(7, 53, 98, 71, G, G + 1, W, Block.DADO_GREEN);
        for (int a = 10; a <= 96; a += 2) bp.window(a, 71, a, 71, G + 8, G + 11, Block.GLASS_DIRTY);
        for (int k = 0; k < 4; k++) {
            int ta = 12 + k * 22;
            bp.box(ta, 61, ta + 15, 64, G, G + 1, C);                                        // the pedestal
            bp.box(ta + 1, 61, ta + 9, 64, G + 2, G + 4, Block.CORRUGATED_BLUE);              // the casing: low-pressure cylinders
            bp.box(ta + 1, 62, ta + 9, 63, G + 5, G + 5, Block.CORRUGATED_BLUE);
            bp.box(ta + 11, 62, ta + 14, 63, G + 2, G + 3, Block.GENERATOR);                  // the generator on the same shaft
            bp.box(ta + 10, 62, ta + 10, 63, G + 2, G + 3, Block.METAL_PLATE);
            for (int b = 58; b <= 60; b++) bp.put(ta + 3, b, G + 5, Block.PIPE);              // steam in, overhead (a body walks under it)
            for (int y = G; y <= G + 5; y++) bp.put(ta + 3, 57, y, Block.PIPE);
            for (int a = ta; a <= ta + 15; a++) { bp.put(a, 59, G - 1, Block.HAZARD_STRIPE); bp.put(a, 66, G - 1, Block.HAZARD_STRIPE); }
            // what serves each set: its pumps, its board, a cart of tools, and what was left where it was put down
            bp.prop("pump_unit", ta + 6, 67, ta + 7, 67, G, 0, 1); bp.prop("console_corner", ta + 12, 67, G, 0, 1); bp.prop("gauges", ta + 2, 60, G + 2, 0, -1);
            bp.prop(k % 2 == 0 ? "cart_tool" : "drum_oil", ta + 15, 58, G, 0, -1); bp.prop("valve_wheel", ta + 4, 57, G + 1, 1, 0);
            if (k == 1) { bp.prop("stretcher", ta + 9, 56, ta + 9, 57, G, 0, -1); bp.prop("papers_floor", ta + 10, 57, G, 0, -1); }
            if (k == 2) { bp.prop("barrier_rope", ta + 5, 58, ta + 6, 58, G, 0, -1); bp.prop("drum_hazard", ta + 8, 58, G, 0, -1); bp.prop("drum_hazard", ta + 9, 58, G, 0, -1); }
            bp.loot(ta + 15, 68, G, Block.TOOLBOX, "industrial");
        }
        // the travelling crane on its rails, parked over the second set, its hook hanging
        for (int a = 8; a <= 97; a++) { bp.put(a, 55, G + 12, Block.STEEL_BEAM); bp.put(a, 69, G + 12, Block.STEEL_BEAM); }
        bp.box(40, 55, 41, 69, G + 12, G + 12, Block.HAZARD_STRIPE);
        bp.prop("hook_crane", 40, 62, G + 10, 0, -1);
        for (int a = 12; a <= 96; a += 7) for (int b = 57; b <= 68; b += 11) bp.light(a, b, G + 14);
        for (int a = 9; a <= 96; a += 11) { bp.prop("sign_radiation", a, 54, G + 2, 0, -1); bp.prop(a % 22 == 9 ? "fire_extinguisher" : "fuse_box", a + 2, 54, G + 1, 0, -1); }
        bp.prop("workbench_vice", 9, 70, 10, 70, G, 0, 1); bp.prop("locker_row", 12, 70, G, 0, 1); bp.prop("locker_open", 13, 70, G, 0, 1); bp.prop("shelf_parts", 15, 70, G, 0, 1); bp.prop("crane_control", 43, 67, G, 0, 1);
        bp.prop("tank_pressure", 96, 56, G, 1, 0); bp.prop("tank_pressure", 96, 58, G, 1, 0); bp.prop("pipe_stack", 96, 60, G, 1, 0);
        bp.door(52, 71, G, Block.DOOR_DOUBLE_METAL, true); bp.door(53, 71, G, Block.DOOR_DOUBLE_METAL, true);

        // ---- the reactor blocks (interiors b 20..44; their back wall, b 45, is the service corridor's front wall)
        // units 1-2: the old block, lower, with the old stack
        bp.room("pump_hall", 0, 8, 20, 29, 44, G, H, W, F, CP);
        bp.room("reactor_hall", 1, 8, 20, 29, 44, L1, 14, W, Block.TILE_GRAY, Block.ROOF_TAR);
        reactorLid(bp, 18, 32, 5, L1);
        pumps(bp, 10, 22, 27, 42, G);
        reactorHall(bp, 8, 20, 29, 44, L1, 18, 32, 5, 14, false);
        // unit 3: whole
        bp.room("pump_hall", 0, 34, 20, 57, 44, G, H, W, F, CP);
        bp.room("reactor_hall", 1, 34, 20, 57, 44, L1, 19, W, Block.TILE_GRAY, Block.ROOF_TAR);
        reactorLid(bp, 45, 31, 6, L1);
        pumps(bp, 36, 22, 55, 42, G);
        // the fuelling machine: a gantry over the lid, the mast hanging from its bridge
        for (int y = L1; y <= L1 + 11; y++) for (int[] c : new int[][]{{37, 24}, {37, 38}, {54, 24}, {54, 38}}) bp.put(c[0], c[1], y, Block.STEEL_BEAM);
        bp.box(37, 24, 54, 24, L1 + 12, L1 + 12, Block.STEEL_BEAM); bp.box(37, 38, 54, 38, L1 + 12, L1 + 12, Block.STEEL_BEAM);
        bp.box(44, 24, 46, 38, L1 + 12, L1 + 12, Block.HAZARD_STRIPE);
        for (int y = L1 + 5; y <= L1 + 11; y++) bp.put(45, 31, y, Block.METAL_PLATE);
        // the spent-fuel pool along the back of the hall
        bp.box(37, 41, 54, 43, L1, L1, Block.TILE_WHITE); bp.box(38, 42, 53, 42, L1, L1, Block.WATER);
        reactorHall(bp, 34, 20, 57, 44, L1, 45, 31, 6, 19, true);
        // the auxiliary block between 3 and 4: the sanitary lock below, the ventilation centre above, and the stack on its roof
        bp.room("locker", 0, 59, 20, 68, 44, G, H, Block.TILE_GREEN, Block.TILE_WHITE, CP);
        bp.room("utility", 1, 59, 20, 68, 44, L1, H, W, F, CP);
        bp.mass(58, 19, 69, 45, L2 - 1, G + 24, D);
        // the sanitary lock: dirty side, showers, clean side
        for (int b = 22; b <= 30; b += 2) { bp.prop(b % 4 == 2 ? "locker_row" : "locker_open", 59, b, G, -1, 0); bp.prop("locker_row", 68, b, G, 1, 0); }
        bp.prop("bench_locker", 63, 24, 64, 24, G, 0, -1); bp.prop("bench_locker", 63, 28, 64, 28, G, 0, -1);
        for (int a = 60; a <= 66; a += 3) bp.prop("shower_decon", a, 34, G, 0, -1);
        bp.prop("suit_rack", 59, 38, G, -1, 0); bp.prop("suit_rack", 59, 40, G, -1, 0); bp.prop("respirator_rack", 68, 38, G, 1, 0); bp.prop("respirator_rack", 68, 40, G, 1, 0);
        bp.prop("dosimeter_post", 62, 44, G, 0, 1); bp.prop("dosimeter_post", 65, 20, G, 0, -1); bp.prop("geiger_station", 66, 42, G, 0, 1); bp.prop("sign_radiation", 60, 20, G + 2, 0, -1);
        bp.prop("barrier_rope", 62, 32, 63, 32, G, 0, -1); bp.prop("mop_bucket", 68, 43, G, 1, 0); bp.prop("papers_floor", 64, 30, G, 0, -1);
        bp.loot(63, 22, G, Block.MED_CABINET, "medical"); bp.loot(64, 22, G, Block.LOCKER, "industrial");
        for (int b = 24; b <= 42; b += 6) { bp.light(63, b, G + H); bp.light(64, b, L1 + H); }
        // the ventilation centre
        for (int b = 22; b <= 40; b += 6) { bp.prop("duct", 60, b, 61, b, L1 + 4, 0, -1); bp.prop("tank_pressure", 68, b, L1, 1, 0); bp.prop("pump_unit", 62, b + 2, 63, b + 2, L1, 0, -1); }
        bp.prop("switchgear", 59, 43, L1, -1, 0); bp.prop("switchgear", 59, 42, L1, -1, 0); bp.prop("pipe_stack", 68, 43, L1, 1, 0); bp.prop("cart_tool", 65, 30, L1, 0, -1);
        ventStack(bp, 63, 31, G + 25, Math.min(World.H - 5, G + 66), true);
        ventStack(bp, 18, 26, L1 + 15, G + 48, false);

        // unit 4: the Sarcophagus. Dark plate and stained concrete, no windows, a roof of pipes
        final int SK = Block.METAL_RUST;
        bp.room("containment", 0, 70, 20, 97, 44, G, H, D, Block.CONCRETE_STAINED, D);
        bp.room("reactor_hall", 1, 70, 20, 97, 44, L1, 19, D, Block.CONCRETE_STAINED, SK);
        for (int a = 69; a <= 98; a++) for (int b = 19; b <= 45; b++) {
            boolean edge = a == 69 || a == 98 || b == 19 || b == 45;
            if (edge) for (int y = G + 9; y <= L1 + 19; y++) if (bp.at(a, b, y) == D) bp.put(a, b, y, (a + b) % 4 == 0 ? D : (a + b) % 2 == 0 ? Block.METAL_PLATE : SK);
            if (!edge && a % 2 == 0) bp.put(a, b, L1 + 20, Block.PIPE);                                   // the pipe roof
        }
        // the pit: the floor of the hall is gone over the core, down into the rooms below, and the lid stands on its edge in it
        for (int a = 78; a <= 90; a++) for (int b = 26; b <= 38; b++) {
            double d = Math.hypot(a - 84, b - 32);
            if (d < 5.6) { bp.put(a, b, L1 - 1, Block.AIR); bp.put(a, b, L1 - 2, Block.AIR); if (d > 4.2 && (a * 7 + b * 3) % 4 == 0 && !(a <= 87 && Math.abs(b - 32) <= 4)) bp.put(a, b, L1 - 1, Block.STEEL_BEAM); }
            else if (d < 7.2 && (a + b) % 2 == 0) bp.put(a, b, L1, Block.RUBBLE);
        }
        for (int k = 0; k <= 9; k++) for (int b = 28; b <= 36; b++) { int a = 77 + k, y = L1 + k; if (Math.abs(b - 32) > 4 - (k == 0 || k == 9 ? 1 : 0)) continue; bp.put(a, b, y, Block.METAL_PLATE); bp.put(a, b, y + 1, (k + b) % 2 == 0 ? Block.PIPE : Block.METAL_RUST); if (k > 0) bp.put(a, b, y - 1, Block.METAL_PLATE);
            if (Math.hypot(a - 84, b - 32) >= 5.6) for (int yy = L1; yy < y - 1; yy++) bp.put(a, b, yy, (a + b + yy) % 3 == 0 ? Block.RUBBLE_BRICK : Block.RUBBLE); }
        java.util.Random rr = new java.util.Random(4);
        for (int i = 0; i < 46; i++) {
            int a = 71 + rr.nextInt(26), b = 21 + rr.nextInt(23), h = 1 + rr.nextInt(3);
            if (Math.hypot(a - 84, b - 32) < 7.5) continue;
            boolean nearWall = a < 74 || a > 94 || b < 24 || b > 41;
            for (int y = 0; y < (nearWall ? h : 1); y++) bp.put(a, b, L1 + y, rr.nextInt(4) == 0 ? Block.STEEL_BEAM : rr.nextInt(3) == 0 ? Block.RUBBLE_BRICK : Block.RUBBLE);
        }
        for (int a = 70; a <= 97; a++) for (int b = 20; b <= 44; b++) {
            if (Math.hypot(a - 84, b - 32) < 8 || Math.abs(a - 84) <= 2 && b >= 38 || a <= 73 && b <= 23) continue;      // not across the way in from the golden corridor, nor over the cache
            int toWall = Math.min(Math.min(a - 70, 97 - a), Math.min(b - 20, 44 - b));
            int h = Math.max(0, 4 - toWall - ((a * 31 + b * 17) % 3)) + ((a < 76 || a > 91) && (b < 25 || b > 39) ? 2 : 0);
            for (int y = 0; y < h; y++) if (bp.at(a, b, L1 + y) == 0) bp.put(a, b, L1 + y, (a + b + y) % 5 == 0 ? Block.RUBBLE_BRICK : (a * 3 + b + y) % 7 == 0 ? Block.STEEL_BEAM : Block.RUBBLE);
        }
        for (int i = 0; i < 26; i++) { int a = 71 + rr.nextInt(26), b = 21 + rr.nextInt(23); if (bp.at(a, b, G) == 0 && Math.hypot(a - 84, b - 32) > 5) bp.put(a, b, G, rr.nextInt(3) == 0 ? Block.DEBRIS_PILE : Block.RUBBLE); }
        // the corium under the core - the "elephant's foot" - still warm
        for (int a = 82; a <= 86; a++) for (int b = 30; b <= 34; b++) { double d = Math.hypot(a - 84, b - 32); if (d < 2.3) { bp.put(a, b, G, Block.RUBBLE); if (d < 1.3) bp.put(a, b, G + 1, Block.EMBER); } }
        for (int a = 80; a <= 88; a += 2) for (int b = 28; b <= 36; b += 2) if (Math.hypot(a - 84, b - 32) > 2.5) bp.put(a, b, G - 1, Block.TOXIC_GROUND);
        bp.prop("beacon_red", 72, 22, L1 + 1, 0, -1); bp.prop("beacon_red", 95, 42, L1 + 1, 0, -1); bp.prop("beacon_red", 72, 42, G + 3, 0, -1); bp.prop("beacon_red", 95, 22, G + 3, 0, -1);
        for (int[] hole : new int[][]{{76, 24}, {91, 40}, {88, 23}}) { bp.put(hole[0], hole[1], L1 + 19, Block.AIR); bp.put(hole[0], hole[1], L1 + 20, Block.AIR); }       // daylight through the roof
        bp.loot(96, 43, G, Block.CRATE_MIL, "military"); bp.loot(71, 21, L1, Block.STASH_CACHE, "military");
        // what the liquidators left at the edge of it
        bp.prop("barrier_rope", 82, 41, 83, 41, L1, 0, 1); bp.prop("geiger_station", 86, 42, L1, 0, 1); bp.prop("suit_rack", 80, 43, L1, 0, 1); bp.prop("drum_hazard", 88, 43, L1, 0, 1); bp.prop("drum_hazard", 89, 43, L1, 0, 1);
        bp.prop("sign_radiation", 84, 44, L1 + 2, 0, 1); bp.prop("stretcher", 78, 42, 78, 43, L1, 0, 1);
        bp.prop("sign_radiation", 84, 44, G + 2, 0, 1); bp.prop("drum_hazard", 72, 43, G, 0, 1); bp.prop("geiger_station", 74, 43, G, 0, 1);
        // the cascade wall on the front: four steps of concrete faced with plate, down from the roof to the yard
        int[][] steps = {{17, 18, 23}, {15, 16, 16}, {13, 14, 10}, {11, 12, 4}};
        for (int[] st : steps) { bp.mass(69, st[0], 98, st[1], G, G + st[2], D); for (int a = 69; a <= 98; a++) { bp.put(a, st[0], G + st[2], Block.METAL_PLATE); if (a % 6 == 3) for (int y = G; y < G + st[2]; y++) bp.put(a, st[0], y, Block.CONCRETE_STAINED); } }
        // the buttress wall on the end
        bp.mass(99, 19, 100, 45, G, G + 27, D);
        for (int b = 20; b <= 44; b += 4) for (int k = 0; k < 4; k++) bp.mass(101 + k, b, 101 + k, b + 1, G, G + 23 - k * 5, k % 2 == 0 ? D : SK);
        // the Tank: in the pit, under the core
        bp.lair("tank", 84, 36, G);
        for (int[] c : new int[][]{{74, 30}, {92, 28}, {80, 40}, {90, 38}}) bp.lair("infected", c[0], c[1], G);
        for (int[] c : new int[][]{{74, 40}, {93, 36}, {84, 41}}) bp.lair("infected", c[0], c[1], L1);

        // ---- the deaerator stack: the service corridor, the golden corridor over it, the deaerator gallery on top
        bp.room("corridor", 0, 8, 46, 97, 47, G, H, W, L, CP).circulation = true;
        bp.room("corridor", 1, 8, 46, 97, 47, L1, H, Block.PLASTER_OCHRE, L, CP).circulation = true;
        bp.room("utility", 2, 8, 46, 97, 52, L2, H, W, F, Block.ROOF_TAR);
        for (int a = 12; a <= 94; a += 6) { bp.light(a, 46, G + H); bp.light(a, 47, L1 + H); bp.light(a, 48, L2 + H); }
        for (int a = 11; a <= 92; a += 9) { bp.prop("tank_pressure", a + 2, 51, L2, 0, 1); bp.prop("pipe_run", a + 4, 51, a + 5, 51, L2, 0, 1); bp.prop("valve_wheel", a + 7, 52, L2 + 1, 0, 1); }
        for (int a = 9; a <= 96; a++) bp.put(a, 47, L1 - 1, a % 9 < 2 ? Block.TILE_CHECKER : L);
        // the corridors as they were left: what hangs under the ceilings, what stands along the walls
        for (int a = 14; a <= 92; a += 6) {
            if (a % 12 == 2) bp.prop("pipes_ceiling", a, 46, a + 1, 46, G + 4, 0, -1); else bp.prop("cable_tray", a, 46, a + 1, 46, G + 4, 0, -1);
            if (a % 12 == 8) bp.prop("fluorescent_dead", a, 47, a + 1, 47, L1 + 4, 0, 1);
        }
        for (int a = 13; a <= 93; a += 16) { bp.prop("sign_exit", a, 46, G + 3, 0, -1); bp.prop("fire_extinguisher", a + 3, 46, G + 1, 0, -1); bp.prop("radiator", a + 8, 46, L1, 0, -1); bp.prop("notice_board", a + 5, 46, a + 6, 46, L1 + 1, 0, -1); }
        bp.prop("gurney", 40, 46, 41, 46, L1, 0, -1); bp.prop("papers_floor", 44, 47, L1, 0, -1); bp.prop("wheelchair", 67, 46, L1, 0, -1); bp.prop("dosimeter_post", 83, 46, L1, 0, -1); bp.prop("dosimeter_post", 62, 46, G, 0, -1);
        bp.prop("drum_hazard", 36, 46, G, 0, -1); bp.prop("cart_tool", 56, 46, G, 0, -1); bp.prop("boxes_cardboard", 75, 46, G, 0, -1); bp.prop("phone_wall", 50, 46, L1 + 1, 0, -1); bp.prop("missing_posters", 52, 46, 53, 46, L1 + 1, 0, -1);
        // rooms off the two corridors (four deep, five high): what is behind each door
        String[][] below = {{"switchgear", "8", "29"}, {"locker", "34", "57"}, {"storage", "59", "68"}, {"switchgear", "70", "97"}};
        String[][] above = {{"control", "8", "29"}, {"control", "34", "57"}, {"server", "59", "68"}, {"control", "70", "97"}};
        for (int lvl = 0; lvl < 2; lvl++) for (String[] r : lvl == 0 ? below : above) {
            int ra = Integer.parseInt(r[1]), rb = Integer.parseInt(r[2]), y = lvl == 0 ? G : L1;
            bp.room(r[0], lvl, ra, 49, rb, 52, y, H, lvl == 0 ? W : PG, lvl == 0 ? F : L, CP);
            int door = (ra + rb) / 2;
            bp.door(door, 48, y, Block.DOOR_METAL, false);
            for (int a = ra + 2; a <= rb - 1; a += 5) bp.light(a, 50, y + H);
            switch (r[0]) {
                case "control" -> controlRoom(bp, ra, rb, y, door, rb > 90);
                case "server" -> { for (int a = ra; a <= rb; a++) if (a != door && a != door + 1) bp.prop("mainframe", a, 52, y, 0, 1); bp.prop("terminal_crt", ra, 49, y, -1, 0); bp.prop("terminal_dead", ra + 2, 49, y, 0, -1); bp.prop("cable_tray", ra + 4, 50, ra + 5, 50, y + 4, 0, -1); bp.loot(rb, 49, y, Block.FILING, "office"); }
                case "locker" -> { for (int a = ra + 1; a <= rb - 1; a += 2) if (a < door - 1 || a > door + 3) bp.prop(a % 6 == 5 ? "locker_open" : "locker_row", a, 52, y, 0, 1); bp.prop("bench_locker", ra + 4, 50, ra + 5, 50, y, 0, -1); bp.prop("bench_locker", rb - 6, 50, rb - 5, 50, y, 0, -1); bp.prop("coat_hooks", ra + 1, 49, y + 1, 0, -1); bp.loot(rb, 49, y, Block.LOCKER, "industrial"); }
                case "storage" -> { for (int a = ra; a <= rb; a++) if (a < door - 1 || a > door + 3) bp.prop(a % 3 == 0 ? "shelf_parts" : a % 3 == 1 ? "crate_stack" : "shelf_empty", a, 52, y, 0, 1); bp.prop("drum_oil", ra, 49, y, -1, 0); bp.prop("boxes_cardboard", ra + 1, 49, y, 0, -1); bp.loot(rb, 49, y, Block.CRATE, "industrial"); }
                default -> { for (int a = ra + 1; a <= rb - 1; a += 2) if (a < door - 1 || a > door + 3) bp.prop(a % 8 == 1 ? "transformer_small" : "switchgear", a, 52, y, 0, 1); bp.prop("fuse_box", ra + 3, 49, y + 1, 0, -1); bp.prop("cable_tray", ra + 6, 50, ra + 7, 50, y + 4, 0, -1); bp.prop("sign_exit", door + 1, 49, y + 3, 0, -1); bp.loot(rb, 49, y, Block.TOOLBOX, "industrial"); }
            }
            if (lvl == 0) bp.door(door + 2, 53, G, Block.DOOR_METAL, false);                          // through to the turbine hall
        }
        // stairs: service corridor up to the golden corridor at both ends, and on up to the gallery
        bp.flight(10, 47, 1, 0, G, P, 1, Block.STAIRS_METAL, Block.STEEL_BEAM);
        bp.flight(88, 47, 1, 0, G, P, 1, Block.STAIRS_METAL, Block.STEEL_BEAM);
        bp.flight(20, 46, 1, 0, L1, P, 1, Block.STAIRS_METAL, Block.STEEL_BEAM);
        // doors from the corridors into the blocks (the wall at b 45 is shared)
        int[] into = {18, 45, 63, 84};
        for (int a : into) { bp.door(a, 45, G, Block.DOOR_METAL, false); bp.door(a, 45, L1, Block.DOOR_METAL, false); }
        bp.door(45, 19, G, Block.DOOR_DOUBLE_METAL, true); bp.door(46, 19, G, Block.DOOR_DOUBLE_METAL, true); bp.door(18, 19, G, Block.DOOR_METAL, true); bp.door(63, 19, G, Block.DOOR_METAL, true);

        // ---- the administration block out in front, and the long corridor that joins it to the plant
        final int AW = Block.BRICK_WHITE;
        for (int f = 0; f < 3; f++) {
            int y = G + f * P;
            bp.room(f == 0 ? "lobby" : f == 1 ? "office" : "meeting", f, 8, 6, 17, 15, y, H, f == 0 ? PG : Block.PAINT_BLUE, f == 0 ? Block.TILE_CHECKER : Block.PARQUET, CP);
            bp.room(f == 0 ? "breakroom" : f == 1 ? "office" : "archive", f, 19, 6, 27, 15, y, H, f == 0 ? Block.PLASTER_OCHRE : PG, f == 0 ? Block.TILE_WHITE : Block.LINOLEUM, CP);
            bp.door(18, 10, y, Block.DOOR_WOOD, false);
            bp.window(9, 5, 16, 5, y + 1, y + 3, Block.GLASS); bp.window(20, 5, 26, 5, y + 1, y + 3, Block.GLASS); bp.window(7, 8, 7, 13, y + 1, y + 3, Block.GLASS);
            bp.light(12, 10, y + H); bp.light(23, 10, y + H);
            if (f == 0) {
                // the lobby: a desk nobody is at, the people who are looked for, somewhere to wait; and where the shift ate
                bp.prop("desk_reception", 11, 12, 12, 12, y, 0, 1); bp.prop("sofa_floral", 9, 7, 10, 7, y, 0, -1); bp.prop("plant_dead", 16, 6, y, 0, -1); bp.prop("table_coffee", 9, 9, 10, 9, y, 0, -1);
                bp.prop("missing_posters", 8, 13, 8, 14, y + 1, -1, 0); bp.prop("notice_board", 14, 15, 15, 15, y + 1, 0, 1); bp.prop("clock_wall", 12, 15, y + 3, 0, 1); bp.prop("vending_machine", 17, 13, y, 1, 0); bp.prop("phone_wall", 17, 8, y + 1, 1, 0);
                bp.prop("table_kitchen", 21, 8, 22, 8, y, 0, -1); bp.prop("chair_wood", 21, 9, y, 0, 1); bp.prop("chair_wood", 22, 7, y, 0, -1); bp.prop("table_kitchen", 21, 12, 22, 12, y, 0, -1); bp.prop("chair_wood", 22, 13, y, 0, 1); bp.prop("chair_wood", 20, 12, y, -1, 0);
                bp.prop("stove_enamel", 27, 6, y, 1, 0); bp.prop("sink_kitchen", 27, 7, y, 1, 0); bp.prop("counter_kitchen", 27, 8, y, 1, 0); bp.loot(27, 9, y, Block.FRIDGE, "food"); bp.prop("water_cooler", 19, 6, y, 0, -1); bp.prop("calendar", 24, 6, y + 2, 0, -1);
            } else if (f == 1) {
                // offices: desks as they were got up from
                for (int a = 9; a <= 15; a += 3) { bp.prop(a == 12 ? "typewriter_desk" : "desk_office", a, 7, a == 12 ? a : a + 1, 7, y, 0, -1); bp.prop("chair_wood", a, 8, y, 0, 1); }
                bp.prop("phone_desk", 16, 12, y, 1, 0); bp.prop("filing_cabinet", 8, 14, y, -1, 0); bp.prop("filing_open", 8, 15, y, -1, 0); bp.prop("map_wall", 11, 15, 12, 15, y + 1, 0, 1); bp.prop("calendar", 15, 15, y + 2, 0, 1); bp.prop("papers_floor", 12, 11, y, 0, -1);
                bp.prop("desk_office", 20, 7, 21, 7, y, 0, -1); bp.prop("chair_wood", 20, 8, y, 0, 1); bp.prop("terminal_dead", 24, 6, y, 0, -1); bp.prop("photocopier", 27, 12, y, 1, 0); bp.prop("water_cooler", 27, 14, y, 1, 0);
                for (int b = 7; b <= 10; b++) bp.loot(27, b, y, b % 2 == 0 ? Block.FILING : Block.CABINET, "office");
                bp.prop("coat_stand", 19, 6, y, -1, 0); bp.prop("radiator", 23, 6, y, 0, -1);
            } else {
                // the meeting room, and the archive where the claims are kept
                bp.prop("table_dining", 11, 10, 12, 10, y, 0, -1); bp.prop("table_dining", 13, 10, 14, 10, y, 0, -1);
                for (int a = 11; a <= 14; a++) { bp.prop("chair_wood", a, 9, y, 0, -1); bp.prop("chair_wood", a, 11, y, 0, 1); }
                bp.prop("blackboard", 11, 15, 12, 15, y + 1, 0, 1); bp.prop("map_wall", 8, 9, 8, 10, y + 1, -1, 0); bp.prop("coat_stand", 16, 6, y, 0, -1); bp.prop("plant_dead", 9, 14, y, -1, 0);
                for (int a = 20; a <= 26; a += 2) { bp.loot(a, 6, y, Block.FILING, "office"); bp.prop(a % 4 == 0 ? "filing_open" : "filing_cabinet", a, 15, y, 0, 1); }
                bp.prop("shelf_empty", 27, 9, y, 1, 0); bp.prop("boxes_cardboard", 27, 10, y, 1, 0); bp.prop("boxes_cardboard", 23, 11, y, 0, -1); bp.prop("papers_floor", 22, 9, y, 0, -1); bp.prop("phone_desk", 24, 12, y, 0, 1);
                bp.loot(27, 12, y, Block.SAFE, "office");
            }
        }
        bp.flight(20, 14, 1, 0, G, P, 2, Block.STAIRS_CONCRETE, C);
        bp.flight(27, 12, -1, 0, L1, P, 2, Block.STAIRS_CONCRETE, C);
        bp.door(12, 5, G, Block.DOOR_DOUBLE_GLASS, true); bp.door(13, 5, G, Block.DOOR_DOUBLE_GLASS, true);
        bp.entry(12, 4, G);
        // the corridor: out of the first floor of the block, along, and down the gap between units 2 and 3 into the golden corridor
        bp.room("corridor", 1, 29, 10, 32, 11, L1, H, W, L, Block.ROOF_METAL).circulation = true;
        bp.room("corridor", 1, 31, 12, 32, 44, L1, H, W, L, Block.ROOF_METAL).circulation = true;
        bp.door(28, 10, L1, Block.DOOR_WOOD, false);
        bp.door(31, 45, L1, Block.DOOR_METAL, false);
        for (int b = 12; b <= 18; b += 2) { bp.window(30, b, 30, b, L1 + 1, L1 + 3, Block.GLASS); bp.window(33, b, 33, b, L1 + 1, L1 + 3, Block.GLASS); }
        for (int[] c : new int[][]{{30, 9}, {33, 9}, {30, 13}, {33, 13}, {30, 18}, {33, 18}}) for (int y = G; y < L1 - 1; y++) bp.put(c[0], c[1], y, Block.STEEL_BEAM);
        for (int b = 12; b <= 42; b += 6) { bp.light(31, b, L1 + H); if (b % 12 == 0) bp.prop("cable_tray", 32, b, 32, b + 1, L1 + 4, 1, 0); }
        bp.prop("gurney", 32, 24, 32, 25, L1, 1, 0); bp.prop("papers_floor", 31, 30, L1, 0, -1); bp.prop("sign_radiation", 32, 40, L1 + 2, 1, 0); bp.prop("dosimeter_post", 32, 43, L1, 1, 0);
        for (int[] c : new int[][]{{20, 30}, {44, 24}, {50, 40}, {14, 40}}) bp.lair("infected", c[0], c[1], G);
        for (int[] c : new int[][]{{40, 47}, {75, 47}, {20, 51}, {48, 51}, {85, 51}}) bp.lair("infected", c[0], c[1], L1);
        for (int[] c : new int[][]{{30, 62}, {60, 58}, {85, 66}}) bp.lair("infected", c[0], c[1], G);
        // ---- finishes: paint to shoulder height and whitewash above in the rooms people sat in, a painted skirting in the halls
        for (MapData.Room rm : bp.rec.rooms) switch (rm.type) {
            case "control" -> bp.finish(rm, Block.PLASTER, Block.DADO_GREEN, 2, 0);
            case "server" -> bp.finish(rm, Block.PLASTER, Block.DADO_BLUE, 2, 0);
            case "lobby" -> bp.finish(rm, Block.PLASTER, Block.DADO_GREEN, 2, 0);
            case "office", "meeting", "archive" -> bp.finish(rm, Block.PLASTER, Block.DADO_BLUE, 2, 0);
            case "breakroom" -> bp.finish(rm, Block.PLASTER_OCHRE, Block.TILE_WHITE, 2, 0);
            case "locker" -> bp.finish(rm, rm.wallBlock, Block.TILE_WHITE, 2, 0);
            case "corridor" -> bp.finish(rm, rm.wallBlock == Block.PLASTER_OCHRE ? Block.PLASTER_OCHRE : Block.PLASTER, Block.DADO_GREEN, 2, 0);
            case "containment" -> { }
            case "reactor_hall" -> { if (rm.wallBlock == W) bp.finish(rm, W, Block.DADO_GREEN, 2, 0); }
            default -> bp.finish(rm, rm.wallBlock, Block.DADO_GREEN, 1, 0);
        }
        // 1.1: the halls, the corridors and the plant rooms get what their walls and ceilings carry (the control rooms and offices have their own)
        int dressed = 0;
        for (MapData.Room rm : new java.util.ArrayList<>(bp.rec.rooms)) switch (rm.type) {
            case "turbine", "pump_hall", "reactor_hall", "containment", "utility", "switchgear", "storage" -> dressed += bp.industrial(rm, true);
            case "corridor", "locker", "stairs" -> dressed += bp.industrial(rm, false);
            default -> { }
        }
        System.out.println("[Gen] Power plant: " + dressed + " fittings on the walls and ceilings of its halls");
        MapData.Building plant = bp.done(lm);

        // ---- the switchyard: transformer bays under gantries; the breaker panel restores the district's power
        int sx0 = 106, sx1 = A - 4, sy0 = 22, sy1 = 66;
        s.pave(sx0, sy0, sx1, sy1, Block.GRAVEL);
        s.enclose(sx0, sy0, sx1, sy1, Block.CHAINLINK, 2, 0, new int[]{sx0 + 8, sy0, sx0 + 10, sy0});
        for (int b = sy0 + 4; b <= sy1 - 4; b += 8) {
            for (int a = sx0 + 3; a <= sx1 - 3; a += 6) { if (s.prop(a, b, Block.TRANSFORMER)) s.set(a, b, G + 1, Block.MACHINE); s.prop(a + 2, b, Block.MACHINE); }
            for (int y = G; y < G + 9; y++) { s.set(sx0 + 1, b - 2, y, Block.LATTICE); s.set(sx1 - 1, b - 2, y, Block.LATTICE); }
            s.fill(sx0 + 1, b - 2, sx1 - 1, b - 2, G + 9, G + 9, Block.LATTICE);
        }
        lb.g.site("substation", s.x(sx0 + 3, sy0 + 4), G, s.z(sx0 + 3, sy0 + 4));
        // the rail siding behind the turbine hall: where the freight leaves from
        for (int a = 2; a < 102; a++) { s.set(a, B - 4, G - 1, a % 3 == 0 ? Block.RAIL_SLEEPER : Block.RAIL); s.set(a, B - 2, G - 1, a % 3 == 0 ? Block.RAIL_SLEEPER : Block.RAIL); s.keep(a, B - 3); }
        for (int car = 0; car < 3; car++) { int ca = 20 + car * 18; int col = new int[]{Block.CONTAINER_RED, Block.CONTAINER_GREEN, Block.CONTAINER_BLUE}[car]; s.fill(ca, B - 4, ca + 11, B - 2, G, G + 2, col); }
        lb.g.site("rail_exit", s.x(6, B - 3), G, s.z(6, B - 3));
        // the yard as its holders keep it
        s.sandbags(56, 3, 4, true); s.sandbags(67, 3, 4, true);
        s.prop(70, 6, Block.BARREL_FIRE);
        for (int k = 0; k < 6; k++) s.prop(36 + k * 2, 8 + (k & 1), Block.BARREL);
        s.crate(40, 12, Block.CRATE, "industrial"); s.crate(42, 12, Block.TOOLBOX, "industrial"); s.crate(52, 8, Block.DUMPSTER, "trash");
        s.tower(2, 2, 8, "Plant Gate Watch", lm.faction);
        s.tower(A - 5, 2, 8, "Plant East Watch", lm.faction);
        s.lampPost(58, 2); s.lampPost(68, 2); s.lampPost(34, 16); s.lampPost(104, 16); s.lampPost(104, 40);
        lb.garrison(s, lm, plant, 62, 2);
        s.post("Unit Control", "leader", lm.faction, 45, 18, G);
        s.post("Switchyard", "patrol", lm.faction, sx0 + 9, sy0 - 3, G);
    }

    /** The top of a reactor: a disc of square caps in a ring of warning paint, flush with the floor of the hall. */
    private static void reactorLid(Blueprint bp, int ca, int cb, int r, int y0) {
        for (int a = ca - r - 2; a <= ca + r + 2; a++) for (int b = cb - r - 2; b <= cb + r + 2; b++) {
            double d = Math.hypot(a - ca, b - cb);
            if (d < r + 0.4) bp.put(a, b, y0 - 1, (a + b) % 2 == 0 ? Block.METAL_PLATE : Block.TILE_CHECKER);
            else if (d < r + 1.5) bp.put(a, b, y0 - 1, Block.HAZARD_STRIPE);
        }
    }

    /** What stands in a reactor hall round its lid: the rope, the tools for the channels, casks, the health-physics post, lamps. */
    private static void reactorHall(Blueprint bp, int a0, int b0, int a1, int b1, int y, int ca, int cb, int r, int h, boolean fuelling) {
        bp.reface(a0 - 1, b0 - 1, a1 + 1, b1 + 1, y, y + 1, Block.CONCRETE_PANEL, Block.DADO_GREEN);
        for (int k = 0; k < 4; k++) { int da = k == 0 ? -1 : k == 1 ? 1 : 0, db = k == 2 ? -1 : k == 3 ? 1 : 0; int pa = ca + da * (r + 3), pb = cb + db * (r + 3); if (da != 0) bp.prop("barrier_rope", pa, pb, pa, pb + 1, y, da, 0); else bp.prop("barrier_rope", pa, pb, pa + 1, pb, y, 0, db); }
        bp.prop("reactor_tools", a0, b0 + 3, y, -1, 0); bp.prop("reactor_tools", a0, b0 + 5, y, -1, 0); bp.prop("suit_rack", a0, b1 - 3, y, -1, 0);
        bp.prop("geiger_station", a1, b0 + 2, y, 1, 0); bp.prop("dosimeter_post", a1, b0 + 6, y, 1, 0); bp.prop("sign_radiation", a1, b0 + 8, y + 2, 1, 0); bp.prop("sign_radiation", a0, b1 - 6, y + 2, -1, 0);
        bp.prop("fuel_cask", a1 - 2, b1 - 2, a1 - 1, b1 - 1, y, 0, 1);
        if (!fuelling) { bp.prop("fuel_cask", a0 + 1, b1 - 2, a0 + 2, b1 - 1, y, 0, 1); bp.prop("crane_control", a0 + 4, b1, y, 0, 1); bp.prop("hook_crane", ca, cb, y + h - 2, 0, -1); }
        else { bp.prop("crane_control", a1 - 4, b0, y, 0, -1); bp.prop("drum_hazard", a0 + 1, b0, y, 0, -1); bp.prop("drum_hazard", a0 + 2, b0, y, 0, -1); bp.prop("cart_tool", a0 + 4, b0, y, 0, -1); }
        for (int a = a0 + 2; a <= a1 - 2; a += 5) for (int b = b0 + 2; b <= b1 - 2; b += 10) bp.light(a, b, y + h);
        bp.put(a0 + 1, cb, y, Block.FLOOR_LAMP); bp.put(a1 - 1, cb, y, Block.FLOOR_LAMP);
    }

    /** A pump hall: the main circulation pumps in two rows with their headers and tanks, lit, with a chest of tools. */
    private static void pumps(Blueprint bp, int a0, int b0, int a1, int b1, int y) {
        for (int a = a0 + 2; a <= a1 - 3; a += 5) for (int b = b0 + 3; b <= b1 - 3; b += 8) {
            bp.prop("pump_unit", a, b, a + 1, b, y, 0, -1);
            bp.prop("pipe_stack", a + 2, b, y, 0, -1);
            // 1.2: the valve by a pump stands on the floor, on its own riser. It was the wall-mounted wheel - a plate and a wheel
            // made to hang on a wall - set a block up in the open air beside the pump, with nothing behind it and nothing under it.
            if ((a + b) % 3 == 0) bp.prop("valve_stand", a + 1, b + 1, y, 0, -1);
        }
        for (int b = b0 + 6; b <= b1 - 6; b += 8) { bp.prop("pipe_run", a0, b, a0 + 1, b, y, -1, 0); bp.prop("tank_pressure", a1, b, y, 1, 0); bp.prop("gauges", a1, b + 1, y + 1, 1, 0); }
        bp.prop("pipes_ceiling", a0 + 4, b0, a0 + 5, b0, y + 4, 0, -1); bp.prop("pipes_ceiling", a1 - 6, b1, a1 - 5, b1, y + 4, 0, 1);
        bp.prop("switchgear", a0, b0, y, -1, 0); bp.prop("drum_oil", a0, b0 + 1, y, -1, 0); bp.prop("cart_tool", a1, b1 - 1, y, 1, 0); bp.prop("sign_exit", a0 + 9, b1, y + 3, 0, 1);
        for (int a = a0 + 1; a <= a1; a += 6) for (int b = b0 + 1; b <= b1; b += 7) bp.light(a, b, y + 5);
        bp.loot(a0, b1, y, Block.TOOLBOX, "industrial");
    }

    /**
     * A unit control room: the wall of instruments with the mimic board in the middle of it, the operators' desks facing
     * it, their chairs, and how the last shift left it - unit 4's in a hurry.
     */
    private static void controlRoom(Blueprint bp, int a0, int a1, int y, int door, boolean wrecked) {
        int mid = (a0 + a1) / 2;
        for (int a = a0; a <= a1; a++) {
            if (a == mid || a == mid + 1) continue;
            bp.prop("panel_instruments", a, 52, y, 0, 1);
            if (a % 2 == 0) bp.prop("panel_annunciator", a, 52, y + 2, 0, 1);
        }
        bp.prop("panel_mimic", mid, 52, mid + 1, 52, y, 0, 1);
        bp.prop("panel_annunciator", mid, 52, y + 2, 0, 1); bp.prop("panel_annunciator", mid + 1, 52, y + 2, 0, 1);
        // the desks: in a row down the room, a gap at the door
        for (int a = a0 + 2; a <= a1 - 3; a += 3) {
            if (Math.abs(a - door) <= 1 || Math.abs(a + 1 - door) <= 1) continue;
            bp.prop("console_control", a, 50, a + 1, 50, y, 0, 1);
            if (!wrecked || a % 2 == 0) bp.prop("chair_wood", a, 49, y, 0, -1);
        }
        bp.prop("console_corner", a0, 50, y, 0, 1); bp.prop("console_corner", a1, 50, y, 0, 1);
        bp.prop("clock_wall", door - 2, 49, y + 3, 0, -1); bp.prop("calendar", door + 3, 49, y + 2, 0, -1); bp.prop("phone_desk", a0 + 1, 49, y, 0, -1); bp.prop("dosimeter_post", door + 1, 49, y, 0, -1);
        bp.prop("cable_tray", mid - 6, 51, mid - 5, 51, y + 4, 0, 1); bp.prop("cable_tray", mid + 5, 51, mid + 6, 51, y + 4, 0, 1);
        if (wrecked) { bp.prop("papers_floor", door - 1, 50, y, 0, -1); bp.prop("papers_floor", mid + 3, 49, y, 0, -1); bp.prop("glass_shards", mid - 3, 49, y, 0, -1); bp.prop("blood_old", a1 - 4, 49, y, 0, -1); bp.prop("stretcher", a1 - 7, 49, a1 - 6, 49, y, 0, -1); }
        bp.loot(a0, 49, y, Block.FILING, "office");
        bp.loot(a1, 49, y, Block.CABINET, "industrial");
    }

    /** A ventilation stack: a banded tube, and - for the great one - the braced frame round it with its landings. */
    private static void ventStack(Blueprint bp, int ca, int cb, int y0, int y1, boolean great) {
        int r = great ? 2 : 1;
        for (int y = y0; y <= y1; y++) {
            boolean red = ((y1 - y) / (great ? 6 : 5)) % 2 == 0;
            for (int da = -r; da <= r + 1; da++) for (int db = -r; db <= r + 1; db++) {
                double d = Math.hypot(da - 0.5, db - 0.5);
                if (d > r + 0.9 || d < r - 0.4) continue;
                bp.put(ca + da, cb + db, y, red ? Block.CONTAINER_RED : Block.BRICK_WHITE);
            }
            if (!great) continue;
            if (y <= y0 + (y1 - y0) * 3 / 4) {
                for (int[] c : new int[][]{{-4, -4}, {5, -4}, {-4, 5}, {5, 5}}) bp.put(ca + c[0], cb + c[1], y, Block.STEEL_BEAM);
                if ((y - y0) % 10 == 9) for (int da = -4; da <= 5; da++) for (int db = -4; db <= 5; db++) if (da == -4 || da == 5 || db == -4 || db == 5) bp.put(ca + da, cb + db, y, Block.LATTICE);
            }
        }
        bp.prop("beacon_red", ca, cb - r - 1, y1, 0, -1);
    }
}
