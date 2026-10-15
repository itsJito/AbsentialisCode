package absentialis.world.gen;

import static absentialis.world.gen.Gen.G;

import absentialis.world.Block;
import absentialis.world.MapData;
import absentialis.world.World;

/**
 * The two landmarks of 0.9 that are particular places, drawn by hand on {@link Blueprint}s rather than grown from
 * archetypes: the power station as the Chernobyl plant, and the big house of the residential district as the
 * Spencer mansion.
 *
 * THE PLANT (132 x 78 lot, front at b = 0). What makes Chernobyl Chernobyl from outside, kept in its order: the
 * low turbine hall running the whole length of the back; in front of it the taller deaerator stack; in front of
 * that the reactor blocks - units 1-2 at one end with the old stack, unit 3, the auxiliary block that carries the
 * great red-and-white ventilation stack, and unit 4 under the Sarcophagus with its stepped cascade wall on the
 * front and its buttresses on the end; the administration block out in front, joined to the plant by the long
 * elevated corridor; the switchyard beyond. Inside, every room is at least five blocks high: the turbine hall with
 * its four sets, the service corridor and the golden corridor above it, the control rooms off the golden corridor
 * (unit 4's among them), the pump halls under the reactor halls, unit 3's hall with its reactor lid and fuelling
 * machine, and unit 4's - open to the pit, the lid thrown on its edge.
 *
 * THE MANSION (98 x 78 lot). The Spencer estate's house as it is walked: the main hall, two storeys high, with the
 * grand staircase to a gallery; the dining room off it to the west, as high as the hall, with the long table, the
 * fireplace and the clock, and a balcony over it; the kitchen and the bar with its piano behind; to the east the
 * gallery of statues, the corridor with the windows, the bathroom, the store room with the chest and the east
 * stairs; upstairs a library, a study, bedrooms, and the terrace over the porch. Outside: a columned porch and
 * pediment, tall windows, a slate mansard with dormers and chimneys, a fountain, a graveyard, the forest closing in.
 * It is abandoned: the commune that holds the district camps in its grounds and does not go into the house.
 */
final class Landmarks09 {
    private Landmarks09() {}

    private static final int P = 6;      // one storey: five clear and a slab

    // ================================================================== THE PLANT
    static void chernobyl(LandmarkBuilder lb, Site s, MapData.Landmark lm) {
        int A = s.A, B = s.B;
        s.ground(Block.CONCRETE_FLOOR);
        s.enclose(0, 0, A - 1, B - 1, Block.CONCRETE_PANEL, 3, Block.BARBED, new int[]{60, 0, 65, 0}, new int[]{A - 15, 0, A - 12, 0});
        s.path(62, 0, 62, 17, Block.ASPHALT, 5);
        s.path(2, 17, A - 6, 17, Block.ASPHALT, 3);
        Blueprint bp = new Blueprint(s, lm, "lm_cnpp", lm.name, "plant");
        final int W = Block.CONCRETE_PANEL, D = Block.CONCRETE_DARK, F = Block.CONCRETE_FLOOR, L = Block.LINOLEUM, C = Block.CONCRETE;
        final int L1 = G + P, L2 = G + 2 * P;

        // ---- the turbine hall: one room the length of the plant, twelve high, four sets in a row
        bp.room("turbine", 0, 8, 54, 97, 70, G, 12, W, F, Block.ROOF_METAL);
        for (int a = 10; a <= 96; a += 2) { bp.window(a, 71, a, 71, G + 7, G + 9, Block.GLASS_DIRTY); }
        for (int k = 0; k < 4; k++) {
            int a0 = 12 + k * 22;
            bp.box(a0, 61, a0 + 15, 64, G, G + 1, C);                                        // the pedestal
            bp.box(a0 + 1, 61, a0 + 9, 64, G + 2, G + 4, Block.CORRUGATED_BLUE);              // the casing: low-pressure cylinders
            bp.box(a0 + 1, 62, a0 + 9, 63, G + 5, G + 5, Block.CORRUGATED_BLUE);
            bp.box(a0 + 11, 62, a0 + 14, 63, G + 2, G + 3, Block.GENERATOR);                  // the generator on the same shaft
            bp.box(a0 + 10, 62, a0 + 10, 63, G + 2, G + 3, Block.METAL_PLATE);
            for (int b = 58; b <= 60; b++) bp.put(a0 + 3, b, G + 4, Block.PIPE);              // steam in, overhead (a body walks under it)
            for (int y = G; y <= G + 4; y++) bp.put(a0 + 3, 58, y, Block.PIPE);
            bp.put(a0 + 5, 66, G, Block.CONSOLE); bp.put(a0 + 12, 66, G, Block.TRANSFORMER);
            for (int a = a0; a <= a0 + 15; a++) { bp.put(a, 59, G - 1, Block.HAZARD_STRIPE); bp.put(a, 66, G - 1, Block.HAZARD_STRIPE); }
            bp.loot(a0 + 15, 68, G, Block.TOOLBOX, "industrial");
        }
        // the travelling crane on its rails, parked over the second set
        for (int a = 8; a <= 97; a++) { bp.put(a, 55, G + 10, Block.STEEL_BEAM); bp.put(a, 69, G + 10, Block.STEEL_BEAM); }
        bp.box(40, 55, 41, 69, G + 10, G + 10, Block.HAZARD_STRIPE);
        for (int y = G + 6; y <= G + 9; y++) bp.put(40, 62, y, Block.STEEL_BEAM);
        for (int a = 12; a <= 96; a += 7) for (int b = 57; b <= 68; b += 11) bp.light(a, b, G + 12);
        bp.door(52, 71, G, Block.DOOR_DOUBLE_METAL, true); bp.door(53, 71, G, Block.DOOR_DOUBLE_METAL, true);

        // ---- the reactor blocks (interiors b 20..44; their back wall, b 45, is the service corridor's front wall)
        // units 1-2: the old block, lower, with the old stack
        bp.room("pump_hall", 0, 8, 20, 29, 44, G, 5, W, F, C);
        bp.room("reactor_hall", 1, 8, 20, 29, 44, L1, 14, W, Block.TILE_GRAY, Block.ROOF_TAR);
        reactorLid(bp, 18, 32, 5, L1, false);
        pumps(bp, 10, 22, 27, 42, G);
        // unit 3: whole
        bp.room("pump_hall", 0, 34, 20, 57, 44, G, 5, W, F, C);
        bp.room("reactor_hall", 1, 34, 20, 57, 44, L1, 20, W, Block.TILE_GRAY, Block.ROOF_TAR);
        reactorLid(bp, 45, 31, 6, L1, false);
        pumps(bp, 36, 22, 55, 42, G);
        // the fuelling machine: a gantry over the lid, the mast hanging from its bridge
        for (int y = L1; y <= L1 + 11; y++) for (int[] c : new int[][]{{37, 24}, {37, 38}, {54, 24}, {54, 38}}) bp.put(c[0], c[1], y, Block.STEEL_BEAM);
        bp.box(37, 24, 54, 24, L1 + 12, L1 + 12, Block.STEEL_BEAM); bp.box(37, 38, 54, 38, L1 + 12, L1 + 12, Block.STEEL_BEAM);
        bp.box(44, 24, 46, 38, L1 + 12, L1 + 12, Block.HAZARD_STRIPE);
        for (int y = L1 + 4; y <= L1 + 11; y++) bp.put(45, 31, y, Block.METAL_PLATE);
        // the spent-fuel pool along the back of the hall
        bp.box(37, 41, 54, 43, L1, L1, Block.TILE_WHITE); bp.box(38, 42, 53, 42, L1, L1, Block.WATER);
        for (int a = 36; a <= 56; a += 5) for (int b = 22; b <= 42; b += 10) bp.light(a, b, L1 + 20);
        bp.put(35, 30, L1, Block.FLOOR_LAMP); bp.put(56, 30, L1, Block.FLOOR_LAMP); bp.put(45, 21, L1, Block.FLOOR_LAMP);
        // the auxiliary block between 3 and 4: the sanitary lock below, the ventilation centre above, and the stack on its roof
        bp.room("locker", 0, 59, 20, 68, 44, G, 5, W, Block.TILE_WHITE, C);
        bp.room("utility", 1, 59, 20, 68, 44, L1, 5, W, F, C);
        bp.mass(58, 19, 69, 45, L2, G + 22, D);
        for (int b = 22; b <= 42; b += 4) { bp.put(59, b, G, Block.LOCKER); bp.put(68, b, G, Block.LOCKER); bp.put(60, b + 1, L1, Block.VENT); bp.put(67, b + 1, L1, Block.AC_UNIT); }
        bp.loot(63, 22, G, Block.MED_CABINET, "medical"); bp.loot(64, 22, G, Block.LOCKER, "industrial");
        for (int b = 24; b <= 42; b += 6) { bp.light(63, b, G + 5); bp.light(64, b, L1 + 5); }
        ventStack(bp, 63, 31, G + 23, Math.min(World.H - 5, G + 64), true);
        ventStack(bp, 18, 26, L1 + 15, G + 46, false);

        // unit 4: the Sarcophagus. Dark plate and stained concrete, no windows, a roof of pipes
        final int SK = Block.METAL_RUST;
        bp.room("containment", 0, 70, 20, 97, 44, G, 5, D, Block.CONCRETE_STAINED, D);
        bp.room("reactor_hall", 1, 70, 20, 97, 44, L1, 20, D, Block.CONCRETE_STAINED, SK);
        for (int a = 69; a <= 98; a++) for (int b = 19; b <= 45; b++) {
            boolean edge = a == 69 || a == 98 || b == 19 || b == 45;
            // the cladding: plate in vertical ribs from the first step of the cascade to the roof (no openings anywhere in it)
            if (edge) for (int y = G + 9; y <= L1 + 20; y++) if (bp.at(a, b, y) == D) bp.put(a, b, y, (a + b) % 4 == 0 ? D : (a + b) % 2 == 0 ? Block.METAL_PLATE : SK);
            if (!edge && a % 2 == 0) bp.put(a, b, L1 + 21, Block.PIPE);                                   // the pipe roof
        }
        // the pit: the floor of the hall is gone over the core, down into the rooms below, and the lid stands on its edge in it
        for (int a = 78; a <= 90; a++) for (int b = 26; b <= 38; b++) {
            double d = Math.hypot(a - 84, b - 32);
            if (d < 5.6) { bp.put(a, b, L1 - 1, Block.AIR); if (d > 4.2 && (a * 7 + b * 3) % 4 == 0 && !(a <= 87 && Math.abs(b - 32) <= 4)) bp.put(a, b, L1 - 1, Block.STEEL_BEAM); }   // (no stubs under where the lid lies: nothing to stand on there)
            else if (d < 7.2 && (a + b) % 2 == 0) bp.put(a, b, L1, Block.RUBBLE);
        }
        // "Elena": the reactor's lid, two thousand tonnes of it, thrown up and come to rest on its edge across the pit - its
        // low side on the floor of the hall, the channel stubs still standing out of its upper face
        for (int k = 0; k <= 9; k++) for (int b = 28; b <= 36; b++) { int a = 77 + k, y = L1 + k; if (Math.abs(b - 32) > 4 - (k == 0 || k == 9 ? 1 : 0)) continue; bp.put(a, b, y, Block.METAL_PLATE); bp.put(a, b, y + 1, (k + b) % 2 == 0 ? Block.PIPE : Block.METAL_RUST); if (k > 0) bp.put(a, b, y - 1, Block.METAL_PLATE);
            // where it lies over the floor of the hall rather than over the pit, what is under it is the heap it came down on (nobody stands up under two thousand tonnes of lid)
            if (Math.hypot(a - 84, b - 32) >= 5.6) for (int yy = L1; yy < y - 1; yy++) bp.put(a, b, yy, (a + b + yy) % 3 == 0 ? Block.RUBBLE_BRICK : Block.RUBBLE); }
        // what came down: heaps of rubble and bent steel against the walls, a way to climb out of the pit
        java.util.Random rr = new java.util.Random(4);
        for (int i = 0; i < 46; i++) {
            int a = 71 + rr.nextInt(26), b = 21 + rr.nextInt(23), h = 1 + rr.nextInt(3);
            if (Math.hypot(a - 84, b - 32) < 7.5) continue;
            boolean nearWall = a < 74 || a > 94 || b < 24 || b > 41;
            for (int y = 0; y < (nearWall ? h : 1); y++) bp.put(a, b, L1 + y, rr.nextInt(4) == 0 ? Block.STEEL_BEAM : rr.nextInt(3) == 0 ? Block.RUBBLE_BRICK : Block.RUBBLE);
        }
        // slides of it against the walls: banked high in the corners, running out toward the pit
        for (int a = 70; a <= 97; a++) for (int b = 20; b <= 44; b++) {
            if (Math.hypot(a - 84, b - 32) < 8 || Math.abs(a - 84) <= 2 && b >= 38 || a <= 73 && b <= 23) continue;      // not across the way in from the golden corridor, nor over the cache
            int toWall = Math.min(Math.min(a - 70, 97 - a), Math.min(b - 20, 44 - b));
            int h = Math.max(0, 4 - toWall - ((a * 31 + b * 17) % 3)) + ((a < 76 || a > 91) && (b < 25 || b > 39) ? 2 : 0);
            for (int y = 0; y < h; y++) if (bp.at(a, b, L1 + y) == 0) bp.put(a, b, L1 + y, (a + b + y) % 5 == 0 ? Block.RUBBLE_BRICK : (a * 3 + b + y) % 7 == 0 ? Block.STEEL_BEAM : Block.RUBBLE);
        }
        for (int i = 0; i < 26; i++) { int a = 71 + rr.nextInt(26), b = 21 + rr.nextInt(23); if (bp.at(a, b, G) == 0) bp.put(a, b, G, rr.nextInt(3) == 0 ? Block.DEBRIS_PILE : Block.RUBBLE); }
        // the corium under the core - the "elephant's foot" - still warm
        for (int a = 82; a <= 86; a++) for (int b = 30; b <= 34; b++) { double d = Math.hypot(a - 84, b - 32); if (d < 2.3) { bp.put(a, b, G, Block.RUBBLE); if (d < 1.3) bp.put(a, b, G + 1, Block.EMBER); } }
        for (int a = 80; a <= 88; a += 2) for (int b = 28; b <= 36; b += 2) if (Math.hypot(a - 84, b - 32) > 2.5) bp.put(a, b, G - 1, Block.TOXIC_GROUND);
        bp.put(72, 22, L1 + 1, Block.RED_LIGHT); bp.put(95, 42, L1 + 1, Block.RED_LIGHT); bp.put(72, 42, G + 3, Block.RED_LIGHT); bp.put(95, 22, G + 3, Block.RED_LIGHT);
        for (int[] hole : new int[][]{{76, 24}, {91, 40}, {88, 23}}) { bp.put(hole[0], hole[1], L1 + 20, Block.AIR); bp.put(hole[0], hole[1], L1 + 21, Block.AIR); }       // daylight through the roof
        bp.loot(96, 43, G, Block.CRATE_MIL, "military"); bp.loot(71, 21, L1, Block.STASH_CACHE, "military");
        // the cascade wall on the front: four steps of concrete faced with plate, down from the roof to the yard
        int[][] steps = {{17, 18, 22}, {15, 16, 15}, {13, 14, 9}, {11, 12, 4}};
        for (int[] st : steps) { bp.mass(69, st[0], 98, st[1], G, G + st[2], D); for (int a = 69; a <= 98; a++) { bp.put(a, st[0], G + st[2], Block.METAL_PLATE); if (a % 6 == 3) for (int y = G; y < G + st[2]; y++) bp.put(a, st[0], y, Block.CONCRETE_STAINED); } }
        // the buttress wall on the end
        bp.mass(99, 19, 100, 45, G, G + 26, D);
        for (int b = 20; b <= 44; b += 4) for (int k = 0; k < 4; k++) bp.mass(101 + k, b, 101 + k, b + 1, G, G + 22 - k * 5, k % 2 == 0 ? D : SK);

        // ---- the deaerator stack: the service corridor, the golden corridor over it, the deaerator gallery on top
        bp.room("corridor", 0, 8, 46, 97, 47, G, 5, W, L, C).circulation = true;
        bp.room("corridor", 1, 8, 46, 97, 47, L1, 5, Block.PLASTER_OCHRE, L, Block.CEILING_TILE).circulation = true;
        bp.room("utility", 2, 8, 46, 97, 52, L2, 5, W, F, Block.ROOF_TAR);
        for (int a = 12; a <= 94; a += 6) { bp.light(a, 46, G + 5); bp.light(a, 47, L1 + 5); bp.put(a + 2, 50, L2, Block.WATER_TANK); bp.put(a + 2, 50, L2 + 1, Block.WATER_TANK); bp.put(a + 3, 50, L2, Block.PIPE); bp.light(a, 48, L2 + 5); }
        for (int a = 9; a <= 96; a++) bp.put(a, 47, L1 - 1, a % 9 < 2 ? Block.TILE_CHECKER : L);
        // rooms off the two corridors (five deep, five high): what is behind each door
        String[][] below = {{"switchgear", "8", "29"}, {"locker", "34", "57"}, {"storage", "59", "68"}, {"switchgear", "70", "97"}};
        String[][] above = {{"control", "8", "29"}, {"control", "34", "57"}, {"server", "59", "68"}, {"control", "70", "97"}};
        for (int lvl = 0; lvl < 2; lvl++) for (String[] r : lvl == 0 ? below : above) {
            int a0 = Integer.parseInt(r[1]), a1 = Integer.parseInt(r[2]), y = lvl == 0 ? G : L1;
            bp.room(r[0], lvl, a0, 49, a1, 52, y, 5, W, lvl == 0 ? F : L, lvl == 0 ? C : Block.CEILING_TILE);
            bp.door((a0 + a1) / 2, 48, y, Block.DOOR_METAL, false);
            for (int a = a0 + 2; a <= a1 - 1; a += 5) bp.light(a, 50, y + 5);
            if (r[0].equals("control")) controlRoom(bp, a0, a1, y);
            else if (r[0].equals("server")) { for (int a = a0; a <= a1; a += 2) { bp.put(a, 52, y, Block.CONSOLE); bp.put(a, 52, y + 1, Block.CONSOLE); } bp.loot(a0, 49, y, Block.FILING, "office"); }
            else { for (int a = a0 + 1; a <= a1 - 1; a += 3) { bp.put(a, 52, y, r[0].equals("locker") ? Block.LOCKER : Block.TRANSFORMER); if (!r[0].equals("locker")) bp.put(a, 52, y + 1, Block.MACHINE); } bp.loot(a1, 49, y, r[0].equals("storage") ? Block.CRATE : Block.TOOLBOX, "industrial"); }
            if (lvl == 0) bp.door((a0 + a1) / 2 + 2, 53, G, Block.DOOR_METAL, false);                          // through to the turbine hall
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
            bp.room(f == 0 ? "lobby" : f == 1 ? "office" : "meeting", f, 8, 6, 17, 15, y, 5, AW, f == 0 ? Block.TILE_CHECKER : Block.PARQUET, f == 2 ? Block.ROOF_TAR : Block.CEILING_TILE);
            bp.room(f == 0 ? "breakroom" : f == 1 ? "office" : "archive", f, 19, 6, 27, 15, y, 5, AW, f == 0 ? Block.TILE_WHITE : Block.LINOLEUM, f == 2 ? Block.ROOF_TAR : Block.CEILING_TILE);
            bp.door(18, 10, y, Block.DOOR_WOOD, false);
            bp.window(9, 5, 16, 5, y + 1, y + 3, Block.GLASS); bp.window(20, 5, 26, 5, y + 1, y + 3, Block.GLASS); bp.window(7, 8, 7, 13, y + 1, y + 3, Block.GLASS);
            bp.light(12, 10, y + 5); bp.light(23, 10, y + 5);
            if (f == 0) {
                for (int a = 20; a <= 24; a += 2) { bp.put(a, 8, y, Block.TABLE); bp.put(a, 9, y, Block.CHAIR); bp.put(a, 7, y, Block.CHAIR); }
                bp.put(27, 6, y, Block.STOVE); bp.put(27, 7, y, Block.SINK); bp.loot(27, 8, y, Block.FRIDGE, "food"); bp.put(9, 14, y, Block.COUNTER); bp.put(10, 14, y, Block.COUNTER); bp.put(16, 7, y, Block.PLANT);
            } else {
                for (int a = 9; a <= 15; a += 3) { bp.put(a, 7, y, Block.DESK); bp.put(a, 8, y, Block.CHAIR); bp.put(a + 1, 7, y, Block.COMPUTER); }
                for (int a = 20; a <= 26; a += 2) bp.loot(a, 6, y, f == 2 ? Block.FILING : Block.CABINET, "office");
                if (f == 2) { bp.loot(27, 12, y, Block.SAFE, "office"); bp.put(12, 11, y, Block.TABLE); bp.put(13, 11, y, Block.TABLE); bp.put(12, 12, y, Block.CHAIR); bp.put(13, 10, y, Block.CHAIR); }
            }
        }
        bp.flight(21, 15, 1, 0, G, P, 1, Block.STAIRS_CONCRETE, C);
        bp.flight(26, 14, -1, 0, L1, P, 1, Block.STAIRS_CONCRETE, C);
        bp.door(12, 5, G, Block.DOOR_DOUBLE_GLASS, true); bp.door(13, 5, G, Block.DOOR_DOUBLE_GLASS, true);
        bp.entry(12, 4, G);
        // the corridor: out of the first floor of the block, along, and down the gap between units 2 and 3 into the golden corridor
        bp.room("corridor", 1, 29, 10, 32, 11, L1, 5, W, L, Block.ROOF_METAL).circulation = true;
        bp.room("corridor", 1, 31, 12, 32, 44, L1, 5, W, L, Block.ROOF_METAL).circulation = true;
        bp.door(28, 10, L1, Block.DOOR_WOOD, false);
        bp.door(31, 45, L1, Block.DOOR_METAL, false);
        for (int b = 12; b <= 18; b += 2) { bp.window(30, b, 30, b, L1 + 1, L1 + 3, Block.GLASS); bp.window(33, b, 33, b, L1 + 1, L1 + 3, Block.GLASS); }
        for (int[] c : new int[][]{{30, 9}, {33, 9}, {30, 13}, {33, 13}, {30, 18}, {33, 18}}) for (int y = G; y < L1 - 1; y++) bp.put(c[0], c[1], y, Block.STEEL_BEAM);
        for (int b = 12; b <= 42; b += 6) bp.light(31, b, L1 + 5);
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
        for (int car = 0; car < 3; car++) { int a0 = 20 + car * 18; int col = new int[]{Block.CONTAINER_RED, Block.CONTAINER_GREEN, Block.CONTAINER_BLUE}[car]; s.fill(a0, B - 4, a0 + 11, B - 2, G, G + 2, col); }
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
    private static void reactorLid(Blueprint bp, int ca, int cb, int r, int y0, boolean unused) {
        for (int a = ca - r - 2; a <= ca + r + 2; a++) for (int b = cb - r - 2; b <= cb + r + 2; b++) {
            double d = Math.hypot(a - ca, b - cb);
            if (d < r + 0.4) bp.put(a, b, y0 - 1, (a + b) % 2 == 0 ? Block.METAL_PLATE : Block.TILE_CHECKER);
            else if (d < r + 1.5) bp.put(a, b, y0 - 1, Block.HAZARD_STRIPE);
        }
    }

    /** A pump hall: circulation pumps in two rows with their pipework, lit, with a chest of tools. */
    private static void pumps(Blueprint bp, int a0, int b0, int a1, int b1, int y) {
        for (int a = a0 + 2; a <= a1 - 2; a += 5) for (int b = b0 + 3; b <= b1 - 3; b += 8) {
            bp.put(a, b, y, Block.GENERATOR); bp.put(a, b, y + 1, Block.MACHINE); bp.put(a + 1, b, y, Block.PIPE); bp.put(a + 1, b, y + 1, Block.PIPE); bp.put(a + 1, b, y + 2, Block.PIPE);
        }
        for (int a = a0 + 1; a <= a1; a += 6) for (int b = b0 + 1; b <= b1; b += 7) bp.light(a, b, y + 5);
        bp.loot(a0, b1, y, Block.TOOLBOX, "industrial");
    }

    /** A unit control room: the long desk of consoles facing a wall of panels, the operators' chairs, the shift log. */
    private static void controlRoom(Blueprint bp, int a0, int a1, int y) {
        for (int a = a0 + 1; a <= a1 - 1; a++) { bp.put(a, 52, y, Block.CONSOLE); bp.put(a, 52, y + 1, Block.CONSOLE); bp.put(a, 52, y + 2, (a % 3 == 0) ? Block.NEON_GREEN : Block.CONSOLE); }
        for (int a = a0 + 3; a <= a1 - 3; a++) { if (a % 6 == 0) continue; bp.put(a, 50, y, a % 6 == 3 ? Block.DESK : Block.TABLE); if (a % 3 == 1) bp.put(a, 50, y + 1, Block.COMPUTER); if (a % 4 == 2) bp.put(a, 49, y, Block.CHAIR); }
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
            // the frame: four legs out at the corners, ring landings every ten
            if (y <= y0 + (y1 - y0) * 3 / 4) {
                for (int[] c : new int[][]{{-4, -4}, {5, -4}, {-4, 5}, {5, 5}}) bp.put(ca + c[0], cb + c[1], y, Block.STEEL_BEAM);
                if ((y - y0) % 10 == 9) for (int da = -4; da <= 5; da++) for (int db = -4; db <= 5; db++) if (da == -4 || da == 5 || db == -4 || db == 5) bp.put(ca + da, cb + db, y, Block.LATTICE);
            }
        }
        bp.put(ca, cb - r - 1, y1, Block.RED_LIGHT);
    }

    // ================================================================== THE MANSION
    static void spencer(LandmarkBuilder lb, Site s, MapData.Landmark lm) {
        int A = s.A, B = s.B, m = A / 2;
        s.ground(Block.GRASS);
        s.enclose(0, 0, A - 1, B - 1, Block.STONE_BLOCK, 3, Block.IRON_FENCE, new int[]{m - 2, 0, m + 1, 0}, new int[]{m - 1, B - 1, m, B - 1});
        s.path(m - 1, 0, m - 1, 19, Block.COBBLE, 3);
        Blueprint bp = new Blueprint(s, lm, "lm_spencer", lm.name, "manor");
        final int S = Block.STONE_BLOCK, WP = Block.WALLPAPER_STRIPE, WF = Block.WALLPAPER_FLORAL, WD = Block.WOOD_DARK, PQ = Block.PARQUET, PL = Block.PLASTER;
        final int F1 = G + 7, H0 = 6, H1 = 6, ROOF = F1 + H1;          // two storeys of six clear; the roof slab
        final int a0 = 15, a1 = 82, b0 = 26, b1 = 50;                  // the house's interior box

        // ---- ground floor
        // the main hall: both storeys in one, the staircase at the back of it
        // the dining room, to the west: as high as the hall
        bp.room("dining", 0, 20, 26, 38, 37, G, H0 + 1 + H1, WP, PQ, Block.CEILING_TILE);
        bp.room("kitchen", 0, 16, 39, 26, 49, G, H0, PL, Block.TILE_WHITE, WD);
        bp.room("bar", 0, 28, 39, 38, 49, G, H0, WD, PQ, WD);
        bp.room("hall", 0, 40, 45, 57, 49, G, H0, WP, Block.CARPET_RED, WD).circulation = true;      // the passage behind the stairs, to the courtyard door
        // the east wing
        bp.room("gallery", 0, 59, 26, 70, 37, G, H0, WF, Block.MARBLE, WD);
        bp.room("bathroom", 0, 72, 26, 78, 31, G, H0, Block.TILE_GREEN, Block.TILE_WHITE, WD);
        bp.room("storage", 0, 72, 33, 78, 37, G, H0, WP, PQ, WD);
        bp.room("corridor", 0, 59, 39, 81, 40, G, H0, WP, Block.CARPET_RED, WD).circulation = true;  // the corridor with the windows
        bp.room("corridor", 0, 80, 26, 81, 38, G, H0, WP, Block.CARPET_RED, WD).circulation = true;
        bp.room("stairs", 0, 59, 42, 70, 49, G, H0, WP, PQ, WD);
        bp.room("study", 0, 72, 42, 81, 49, G, H0, WD, Block.CARPET_BLUE, WD);                       // the tiger-eyed room: trophies and a desk
        // the west service passage that joins dining room, kitchen and bar to the hall
        bp.room("corridor", 0, 16, 26, 18, 37, G, H0, WP, Block.CARPET_RED, WD).circulation = true;

        // ---- first floor
        bp.room("library", 1, 16, 39, 38, 49, F1, H1, WD, Block.CARPET_GRAY, Block.CEILING_TILE);
        bp.room("corridor", 1, 16, 26, 18, 37, F1, H1, WP, Block.CARPET_RED, Block.CEILING_TILE).circulation = true;
        bp.room("hall", 1, 40, 45, 57, 49, F1, H1, WP, Block.CARPET_RED, Block.CEILING_TILE).circulation = true;
        bp.room("study", 1, 59, 26, 70, 37, F1, H1, WD, Block.CARPET_BLUE, Block.CEILING_TILE);
        bp.room("bedroom", 1, 72, 26, 78, 31, F1, H1, WF, PQ, Block.CEILING_TILE);
        bp.room("bedroom", 1, 72, 33, 78, 37, F1, H1, WF, PQ, Block.CEILING_TILE);
        bp.room("corridor", 1, 59, 39, 81, 40, F1, H1, WP, Block.CARPET_RED, Block.CEILING_TILE).circulation = true;
        bp.room("corridor", 1, 80, 26, 81, 38, F1, H1, WP, Block.CARPET_RED, Block.CEILING_TILE).circulation = true;
        bp.room("stairs", 1, 59, 42, 70, 49, F1, H1, WP, PQ, Block.CEILING_TILE);
        bp.room("bedroom", 1, 72, 42, 81, 49, F1, H1, WF, Block.CARPET_GRAY, Block.CEILING_TILE);

        // the main hall last, so that its walls are its own on every side: cream plaster over a dark dado
        bp.room("hall", 0, 40, 26, 57, 43, G, H0 + 1 + H1, PL, Block.TILE_CHECKER, Block.CEILING_TILE);
        bp.reface(39, 25, 58, 44, G, G + 2, PL, WD);
        bp.reface(39, 25, 58, 44, F1 - 1, F1 - 1, PL, WD);
        bp.box(48, 26, 49, 32, G - 1, G - 1, Block.CARPET_RED);
        // the whole house is faced in grey stone, with a carved band at each floor and under the eaves
        for (int[] side : new int[][]{{a0 - 1, b0 - 1, a1 + 1, b0 - 1}, {a0 - 1, b1 + 1, a1 + 1, b1 + 1}, {a0 - 1, b0 - 1, a0 - 1, b1 + 1}, {a1 + 1, b0 - 1, a1 + 1, b1 + 1}}) {
            bp.reface(side[0], side[1], side[2], side[3], G, ROOF, 0, S);
            bp.reface(side[0], side[1], side[2], side[3], F1 - 1, F1 - 1, 0, Block.STONE_CARVED);
            bp.reface(side[0], side[1], side[2], side[3], ROOF, ROOF, 0, Block.STONE_CARVED);
            bp.reface(side[0], side[1], side[2], side[3], G, G, 0, Block.STONE_DARK);
        }
        // ---- the main hall's staircase and gallery. Up the middle to a landing against the back wall, then left and right
        // along it, and a gallery round the other three sides at first-floor height
        bp.flight(46, 33, 0, 1, G, 7, 6, Block.STAIRS_WOOD, WD);
        for (int a = 46; a <= 51; a++) for (int b = 33; b <= 39; b++) bp.put(a, b, G + (b - 33), Block.CARPET_RED == 0 ? Block.STAIRS_WOOD : Block.STAIRS_WOOD);
        bp.box(40, 40, 57, 43, F1 - 1, F1 - 1, PQ);                                                  // the landing and the back gallery
        bp.box(40, 26, 42, 39, F1 - 1, F1 - 1, PQ); bp.box(55, 26, 57, 39, F1 - 1, F1 - 1, PQ);      // the side galleries
        bp.box(43, 26, 54, 27, F1 - 1, F1 - 1, PQ);                                                  // the gallery over the front door
        // the balustrade stands on the edge of the gallery's own floor (a rail out past the edge has nothing under it)
        for (int b = 28; b <= 39; b++) { bp.put(42, b, F1, Block.IRON_FENCE); bp.put(55, b, F1, Block.IRON_FENCE); }
        for (int a = 43; a <= 54; a++) { bp.put(a, 27, F1, Block.IRON_FENCE); if (a < 46 || a > 51) bp.put(a, 40, F1, Block.IRON_FENCE); }
        for (int a = 43; a <= 54; a += 11) for (int y = G; y < F1 - 1; y++) { bp.put(a, 28, y, Block.MARBLE); bp.put(a, 39, y, Block.MARBLE); }    // the columns that carry it
        for (int[] c : new int[][]{{48, 30}, {49, 30}, {48, 36}, {49, 36}}) bp.light(c[0], c[1], G + H0 + 1 + H1);
        // the chandelier: a chain down from the ceiling over the foot of the stairs, a ring of lights on it
        for (int y = F1 + 1; y < G + H0 + 1 + H1; y++) { bp.put(48, 30, y, Block.IRON_FENCE); bp.put(49, 30, y, Block.IRON_FENCE); }
        bp.box(47, 29, 50, 31, F1, F1, Block.STEEL_BEAM); bp.put(48, 30, F1, Block.AIR); bp.put(49, 30, F1, Block.AIR);
        for (int[] c : new int[][]{{47, 29}, {50, 29}, {47, 31}, {50, 31}, {48, 29}, {49, 31}}) bp.lampAt(c[0], c[1], F1 - 1);
        for (int[] c : new int[][]{{45, 33}, {52, 33}}) { bp.put(c[0], c[1], G, WD); bp.put(c[0], c[1], G + 1, Block.CANDLES); }           // newel posts at the foot of the stairs

        bp.lampAt(44, 31, F1 - 2); bp.lampAt(53, 31, F1 - 2); bp.lampAt(41, 42, F1 - 2); bp.lampAt(56, 42, F1 - 2);
        bp.put(41, 27, G, Block.PLANT); bp.put(56, 27, G, Block.PLANT); bp.put(41, 38, G, Block.FLOOR_LAMP); bp.put(56, 38, G, Block.FLOOR_LAMP);
        // the dining room: the long table laid for ten, the fireplace at the far end with the crest over it, the clock, the balcony
        for (int a = 24; a <= 34; a++) { bp.put(a, 31, G, Block.TABLE); bp.put(a, 32, G, Block.TABLE); if (a % 2 == 0) { bp.put(a, 30, G, Block.CHAIR); bp.put(a, 33, G, Block.CHAIR); } if (a % 3 == 0) bp.put(a, 31, G + 1, Block.CANDLES); }
        bp.box(19, 29, 19, 34, G, G + 3, Block.STONE_CARVED); bp.box(19, 30, 19, 33, G, G + 1, Block.BRICK_BURNT); bp.put(19, 31, G, Block.EMBER); bp.put(19, 32, G, Block.EMBER);
        bp.box(19, 31, 19, 32, G + 4, G + 6, Block.GLASS_STAINED);
        bp.box(38, 27, 38, 27, G, G + 2, WD); bp.put(38, 27, G + 3, Block.CABINET);                   // the grandfather clock
        bp.box(20, 35, 38, 37, F1 - 1, F1 - 1, PQ); for (int a = 20; a <= 38; a++) bp.put(a, 35, F1, Block.IRON_FENCE);   // the balcony along the south side
        for (int a = 23; a <= 35; a += 6) bp.light(a, 31, G + H0 + 1 + H1);
        bp.loot(20, 27, G, Block.CABINET, "residential");
        // kitchen and bar
        for (int b = 40; b <= 46; b += 2) { bp.put(16, b, G, b == 42 ? Block.STOVE : b == 44 ? Block.SINK : Block.COUNTER); }
        bp.loot(16, 48, G, Block.FRIDGE, "food"); bp.loot(26, 49, G, Block.CABINET, "food"); bp.put(21, 44, G, Block.TABLE); bp.put(22, 44, G, Block.TABLE); bp.light(21, 44, G + H0);
        for (int a = 29; a <= 33; a++) bp.put(a, 41, G, Block.COUNTER);                               // the bar
        for (int a = 29; a <= 33; a += 2) bp.put(a, 39, G, Block.SHELF);
        bp.box(35, 46, 37, 47, G, G, WD); bp.put(36, 46, G + 1, Block.WOOD_DARK); bp.put(36, 48, G, Block.CHAIR);     // the piano and its stool
        bp.loot(38, 49, G, Block.CABINET, "residential"); bp.light(33, 45, G + H0);
        // the gallery of statues, the bathroom, the store room with the chest
        for (int a = 61; a <= 69; a += 4) for (int b = 28; b <= 36; b += 4) { bp.put(a, b, G, Block.STONE_CARVED); bp.put(a, b, G + 1, Block.MARBLE); }
        bp.box(64, 25, 66, 25, G + 2, G + 4, Block.GLASS_STAINED); bp.light(65, 32, G + H0);
        bp.put(72, 26, G, Block.TOILET); bp.put(74, 26, G, Block.SINK); bp.box(76, 29, 78, 31, G, G, Block.TILE_WHITE); bp.put(77, 30, G, Block.WATER); bp.loot(72, 31, G, Block.MED_CABINET, "medical"); bp.light(75, 28, G + H0);
        bp.loot(78, 37, G, Block.CRATE, "residential"); bp.put(72, 33, G, Block.DESK); bp.put(72, 34, G, Block.CHAIR); bp.put(73, 33, G + 1, Block.CANDLES); bp.put(78, 33, G, Block.BED); bp.light(75, 35, G + H0);
        for (int a = 60; a <= 78; a += 3) bp.window(a, 41, a, 41, G + 1, G + 4, Block.GLASS_DIRTY);
        bp.put(73, 42, G, Block.DESK); bp.put(74, 42, G, Block.COMPUTER); bp.put(73, 43, G, Block.CHAIR); for (int b = 44; b <= 48; b += 2) bp.loot(81, b, G, Block.BOOKSHELF, "office"); bp.put(76, 46, G, Block.SOFA); bp.light(76, 45, G + H0);
        // the east stairs
        bp.flight(61, 48, 1, 0, G, 7, 2, Block.STAIRS_WOOD, WD);
        bp.box(68, 48, 70, 49, F1 - 1, F1 - 1, PQ); bp.light(64, 44, G + H0); bp.light(64, 44, F1 + H1);
        // upstairs: the library, the study, the bedrooms
        for (int a = 17; a <= 37; a += 3) { bp.loot(a, 49, F1, Block.BOOKSHELF, "office"); bp.put(a, 49, F1 + 1, Block.BOOKSHELF); if (a % 2 == 1) { bp.put(a, 43, F1, Block.BOOKSHELF); bp.put(a, 43, F1 + 1, Block.BOOKSHELF); } }
        bp.put(27, 46, F1, Block.TABLE); bp.put(28, 46, F1, Block.TABLE); bp.put(27, 47, F1, Block.CHAIR); bp.put(36, 40, F1, Block.SOFA); bp.light(22, 45, F1 + H1); bp.light(33, 45, F1 + H1);
        bp.put(64, 28, F1, Block.DESK); bp.put(65, 28, F1, Block.DESK); bp.put(64, 29, F1, Block.CHAIR); bp.put(65, 28, F1 + 1, Block.CANDLES); for (int b = 30; b <= 36; b += 3) { bp.loot(59, b, F1, Block.BOOKSHELF, "office"); bp.put(59, b, F1 + 1, Block.BOOKSHELF); }
        bp.loot(70, 37, F1, Block.SAFE, "residential"); bp.light(64, 32, F1 + H1);
        for (int[] bed : new int[][]{{72, 26}, {72, 33}, {73, 42}}) { bp.put(bed[0], bed[1], F1, Block.BED); bp.put(bed[0] + 1, bed[1], F1, Block.BED); bp.put(bed[0] + 2, bed[1], F1, Block.NIGHTSTAND); bp.loot(bed[0] + 5, bed[1], F1, Block.CABINET, "residential"); bp.light(bed[0] + 3, bed[1] + 2, F1 + H1); }

        // ---- doors. Ground floor
        bp.door(48, 25, G, Block.DOOR_DOUBLE_WOOD, true); bp.door(49, 25, G, Block.DOOR_DOUBLE_WOOD, true);          // the front doors
        bp.door(39, 31, G, Block.DOOR_DOUBLE_WOOD, false); bp.door(39, 32, G, Block.DOOR_DOUBLE_WOOD, false);        // hall -> dining room
        bp.door(58, 31, G, Block.DOOR_WOOD, false);                                                                  // hall -> gallery
        bp.door(44, 44, G, Block.DOOR_WOOD, false); bp.door(53, 44, G, Block.DOOR_WOOD, false);                      // hall -> the passage behind the stairs
        bp.door(48, 50, G, Block.DOOR_WOOD, true);                                                                   // the courtyard door
        bp.door(19, 28, G, Block.DOOR_WOOD, false); bp.door(17, 38, G, Block.DOOR_WOOD, false); bp.door(27, 44, G, Block.DOOR_WOOD, false);   // dining -> west passage -> kitchen -> bar
        bp.door(39, 47, G, Block.DOOR_WOOD, false);                                                                  // bar -> the back passage
        bp.door(58, 47, G, Block.DOOR_WOOD, false);                                                                  // back passage -> east stairs
        bp.door(65, 38, G, Block.DOOR_WOOD, false);                                                                  // gallery -> corridor
        bp.door(71, 28, G, Block.DOOR_WOOD, false); bp.opening(79, 28, G, 2, 1, true); bp.door(79, 35, G, Block.DOOR_WOOD, false);          // bathroom <- gallery; store room <- the east corridor
        bp.door(64, 41, G, Block.DOOR_WOOD, false); bp.door(76, 41, G, Block.DOOR_WOOD, false);                      // corridor -> stairs, corridor -> the study
        bp.opening(80, 39, G, 3, 2, true);
        // first floor
        bp.door(39, 42, F1, Block.DOOR_WOOD, false);                                                                 // hall gallery -> the dining balcony... and on to the library
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
        for (int a = a0 + 1; a <= a1 - 1; a += 3) { if (Math.abs(a - 48) < 3) continue; for (int fl = 0; fl < 2; fl++) { int y = fl == 0 ? G + 1 : F1 + 1; bp.window(a, 25, a, 25, y, y + 3, Block.GLASS_DIRTY); bp.window(a, 50, a, 50, y, y + 3, Block.GLASS_DIRTY); } }
        for (int b = b0 + 2; b <= b1 - 2; b += 3) for (int fl = 0; fl < 2; fl++) { int y = fl == 0 ? G + 1 : F1 + 1; bp.window(15, b, 15, b, y, y + 3, Block.GLASS_DIRTY); bp.window(82, b, 82, b, y, y + 3, Block.GLASS_DIRTY); }
        // the porch: six columns, a terrace on them, a pediment over that
        bp.mass(42, 20, 55, 24, G - 1, G - 1, Block.MARBLE);
        for (int a = 42; a <= 55; a += (a == 46 ? 5 : 2)) for (int y = G; y < F1 - 1; y++) if (a != 48 && a != 49 && a != 50) bp.put(a, 20, y, Block.MARBLE);
        bp.box(42, 20, 55, 24, F1 - 1, F1 - 1, Block.STONE_CARVED);
        for (int a = 42; a <= 55; a++) { bp.put(a, 20, F1, Block.IRON_FENCE); } for (int b = 21; b <= 24; b++) { bp.put(42, b, F1, Block.IRON_FENCE); bp.put(55, b, F1, Block.IRON_FENCE); }
        // the roof: a slate mansard stepping in, dormers on the front, four chimneys
        for (int k = 0; k < 4; k++) bp.mass(a0 - 1 + k, b0 - 1 + k, a1 + 1 - k, b1 + 1 - k, ROOF + 1 + k, ROOF + 1 + k, Block.ROOF_SLATE);
        for (int a = 22; a <= 76; a += 9) { bp.put(a, 25, ROOF + 1, Block.PLASTER); bp.put(a, 25, ROOF + 2, Block.PLASTER); bp.put(a, 24, ROOF + 1, Block.GLASS_DIRTY); bp.put(a + 1, 25, ROOF + 1, Block.PLASTER); bp.put(a - 1, 25, ROOF + 1, Block.PLASTER); }
        for (int[] ch : new int[][]{{20, 30}, {34, 46}, {64, 30}, {77, 46}}) bp.mass(ch[0], ch[1], ch[0] + 1, ch[1] + 1, ROOF + 1, ROOF + 7, Block.BRICK_BURNT);
        // the pediment over the porch
        for (int k = 0; k < 4; k++) bp.mass(43 + k * 2, 25, 54 - k * 2, 25, ROOF + 1 + k, ROOF + 1 + k, Block.STONE_CARVED);
        // abandoned: boards over some of the windows, cobwebs in the corners, leaves blown in
        java.util.Random rr = new java.util.Random(9);
        for (int i = 0; i < 26; i++) { int a = a0 + 2 + rr.nextInt(a1 - a0 - 3), b = b0 + 1 + rr.nextInt(b1 - b0 - 1), y = rr.nextBoolean() ? G + H0 - 1 : F1 + H1 - 1; if (bp.at(a, b, y) == 0 && bp.at(a, b, y + 1) != 0) bp.put(a, b, y, Block.COBWEB); }
        for (int a = a0 + 4; a <= a1 - 4; a += 12) for (int y = G + 1; y <= G + 3; y++) if (bp.at(a, 25, y) == Block.GLASS_DIRTY) bp.put(a, 25, y, Block.BOARDED);
        bp.entry(48, 23, G);                              // on the porch, in front of the doors
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
        // posts: the gate and the guardhouse - nobody is posted in the house
        s.post(lm.name + " Gate", "guard", lm.faction, m, 1, G);
        s.post(lm.name, lm.headquarters ? "hq" : "guard", lm.faction, s.local(coach.entryX, coach.entryZ)[0], s.local(coach.entryX, coach.entryZ)[1], G);
        if (lm.headquarters) s.post(lm.name + " Yard", "patrol", lm.faction, 24, 64, G);
    }
}
