package absentialis.world.gen;

import absentialis.world.Block;
import absentialis.world.MapData;
import absentialis.world.World;

/**
 * 1.0: Blackmoor Correctional Complex, built by hand as one building (it was five generic ones round a yard).
 *
 * The front of it is a prison: administration behind the gate - a lobby, a visiting room with its booths, the
 * warden's office, an armoury, the control room - then a sally corridor into the cell hall, two tiers of cells facing
 * each other across a gallery open to the roof, with the mess hall and its kitchen at the west end and the exercise
 * yard at the east.
 *
 * The back of it is Ward C, the psychiatric wing: one long tiled corridor with everything opening off it. A day room
 * with its chairs still in a ring; the nurses' station and the dispensary; the treatment room, with the machine; the
 * hydrotherapy room and its row of tubs; the padded cells. On the other side the observation ward, beds with straps;
 * the isolation cells; the operating theatre; the mortuary; and the boiler house, where the old escape tunnel comes up.
 * It is the part of Blackmoor nobody was moved out of in time (lore.md: the roll at the gate and the roll in the ward
 * never agreed), and on some days something that used to work in the theatre is still at work there.
 */
final class Landmarks11 {
    private Landmarks11() {}

    private static final int G = World.GROUND;

    static void jail(LandmarkBuilder lb, Site s, MapData.Landmark lm) {
        int A = s.A, B = s.B, m = A / 2;
        s.ground(Block.GRAVEL);
        // outer wire, a dead strip, then the wall
        s.enclose(0, 0, A - 1, B - 1, Block.CHAINLINK, 3, Block.BARBED, new int[]{m - 2, 0, m + 1, 0});
        s.pave(5, 5, A - 6, B - 6, Block.CONCRETE_FLOOR);
        s.enclose(5, 5, A - 6, B - 6, Block.CONCRETE, 5, Block.BARBED, new int[]{m - 2, 5, m + 1, 5});
        s.path(m - 1, 0, m - 1, 8, Block.ASPHALT, 3);

        Blueprint bp = new Blueprint(s, lm, "lm_prison", lm.name, "institutional");
        final int CO = Block.CONCRETE, CP = Block.CONCRETE_PANEL, PL = Block.PLASTER, PP = Block.PLASTER, TG = Block.TILE_GREEN, TW = Block.TILE_WHITE, LI = Block.LINOLEUM, CF = Block.CONCRETE_FLOOR;
        final int CT = Block.CEILING_TILE, CC = Block.CEILING_PLASTER, CN = Block.CEILING_PANEL;
        final int H = 5, HT = 10, ROOF = G + 6, ROOF2 = G + 11;

        // ---------------------------------------------------------------- administration
        MapData.Room visiting = bp.room("visiting", 0, m - 16, 10, m - 6, 15, G, H, PL, LI, CT);
        MapData.Room lobby = bp.room("lobby", 0, m - 4, 10, m + 3, 15, G, H, PL, Block.TILE_CHECKER, CT);
        MapData.Room warden = bp.room("office", 0, m + 5, 10, m + 15, 15, G, H, Block.WOOD_DARK, Block.CARPET_GRAY, CT);
        MapData.Room adminHall = bp.room("corridor", 0, m - 16, 17, m + 15, 18, G, H, PL, LI, CT);
        adminHall.circulation = true;
        MapData.Room armoury = bp.room("armoury", 0, m - 16, 20, m - 10, 24, G, H, CO, CF, CN);
        MapData.Room control = bp.room("control", 0, m - 8, 20, m - 3, 24, G, H, CP, LI, CT);
        MapData.Room records = bp.room("records", 0, m + 3, 20, m + 8, 24, G, H, PL, LI, CT);
        MapData.Room lockers = bp.room("lockers", 0, m + 10, 20, m + 15, 24, G, H, TW, Block.TILE_GRAY, CT);
        MapData.Room sally = bp.room("corridor", 0, m - 1, 20, m, 27, G, H, CO, CF, CN);
        sally.circulation = true;
        bp.finish(lobby, PL, TG, 2, 0); bp.finish(visiting, PL, TG, 2, 0); bp.finish(adminHall, PL, TG, 2, 0); bp.finish(records, PL, TG, 1, 0);

        // ---------------------------------------------------------------- the cell hall: two tiers, open through the middle
        final int c0 = m - 23, c1 = m + 21;
        MapData.Room hall = bp.room("cellblock", 0, c0, 29, c1, 42, G, HT, CO, CF, CN);
        hall.circulation = true;
        final int T1 = G + 5;                                                       // the upper tier's floor (its slab is one below)
        // the upper tier: a slab over each row of cells, and a walkway one block wide along the front of it, railed
        bp.box(c0, 29, c1, 34, T1 - 1, T1 - 1, CO); bp.box(c0, 37, c1, 42, T1 - 1, T1 - 1, CO);
        for (int a = c0; a <= c1; a++) { bp.put(a, 34, T1, Block.IRON_FENCE); bp.put(a, 37, T1, Block.IRON_FENCE); }
        // the cells: a wall between each pair, bars across the front with a gap for the door, a bunk and a pan in each
        for (int tier = 0; tier < 2; tier++) {
            int y = tier == 0 ? G : T1;
            for (int a = c0; a <= c1; a++) {
                // (the way in from the sally port and the way out to Ward C go straight through both rows: no cell there, a jamb either side)
                if (tier == 0 && (a == m - 1 || a == m)) continue;
                boolean jamb = tier == 0 && (a == m - 2 || a == m + 1), party = (a - c0) % 4 == 3 || jamb;
                // (the upper tier's cells are a block shallower: the walkway runs along their fronts, inside the rail)
                int bn = tier == 0 ? 33 : 32, bs = tier == 0 ? 38 : 39;
                for (int yy = y; yy < y + 4; yy++) {
                    if (party) { for (int b = 29; b < bn; b++) bp.put(a, b, yy, CO); for (int b = bs + 1; b <= 42; b++) bp.put(a, b, yy, CO); }
                    boolean gap = !jamb && (a - c0) % 4 == 1 && yy < y + 2 && (a * 7 + tier * 3) % 5 != 0;      // most doors stand open; one in five is shut
                    if (!gap) { bp.put(a, bn, yy, party ? CO : Block.CELL_BARS); bp.put(a, bs, yy, party ? CO : Block.CELL_BARS); }
                }
                if ((a - c0) % 4 == 0 && a + 2 <= c1 && !(tier == 0 && a >= m - 4 && a <= m + 1)) {
                    bp.prop("bunk_cell", a, 29, a, 30, y, 0, -1); bp.prop("toilet_steel", a + 2, 29, y, 0, -1);
                    bp.prop("bunk_cell", a, 41, a, 42, y, 0, 1); bp.prop("toilet_steel", a + 2, 42, y, 0, 1);
                    if ((a / 4 + tier) % 3 == 0) bp.prop("blood_old", a + 1, 31, y, 0, -1);
                    if ((a / 4 + tier) % 4 == 1) bp.prop("papers_floor", a + 1, 40, y, 0, 1);
                    if ((a / 4 + tier) % 5 == 2) bp.prop("shackles_wall", a + 1, 42, y + 1, 0, 1);
                }
            }
        }
        // the walkways are behind the rail: the cells' fronts on the upper tier stand one back from it
        // stairs at each end of the gallery, and a bridge across at the foot of each
        bp.flight(c0 + 1, 35, 1, 0, G, 5, 2, Block.STAIRS_CONCRETE, CO);
        bp.flight(c1 - 1, 35, -1, 0, G, 5, 2, Block.STAIRS_CONCRETE, CO);
        bp.box(c0 + 6, 34, c0 + 8, 37, T1 - 1, T1 - 1, CO); bp.box(c1 - 8, 34, c1 - 6, 37, T1 - 1, T1 - 1, CO);
        for (int a = c0 + 6; a <= c0 + 8; a++) { bp.put(a, 34, T1, Block.AIR); bp.put(a, 37, T1, Block.AIR); }
        for (int a = c1 - 8; a <= c1 - 6; a++) { bp.put(a, 34, T1, Block.AIR); bp.put(a, 37, T1, Block.AIR); }
        for (int b = 35; b <= 36; b++) { bp.put(c0 + 9, b, T1, Block.IRON_FENCE); bp.put(c1 - 9, b, T1, Block.IRON_FENCE); }
        // the gallery floor: the guard's desk, a line of tables bolted down, what was dropped in the last hour
        bp.prop("desk_guard", m - 1, 35, m, 36, G, 0, -1);
        for (int a = c0 + 12; a <= c1 - 12; a += 9) { if (Math.abs(a - m) < 4) continue; bp.prop("table_work", a, 35, a + 1, 36, G, 0, 1); }
        bp.prop("stretcher", c0 + 10, 36, G, 1, 0); bp.prop("rubble_small", m + 8, 35, G, 0, 1); bp.prop("papers_floor", m - 9, 36, G, 0, 1); bp.prop("blood_old", m + 13, 35, G, 0, 1);
        for (int a = c0 + 4; a <= c1 - 4; a += 8) { bp.lampAt(a, 35, G + HT - 1); bp.prop((a / 8) % 3 == 0 ? "lamp_hanging_off" : "fluorescent_dead", a, 36, G + HT - 1, 0, 1); }
        for (int a = c0 + 2; a <= c1 - 2; a += 6) { bp.prop("vent_grille", a, 29, G + 3, 0, -1); }

        // ---------------------------------------------------------------- mess hall and kitchen (west end)
        MapData.Room mess = bp.room("mess", 0, 10, 29, c0 - 2, 36, G, H, TW, Block.TILE_CHECKER, CT);
        MapData.Room kitchen = bp.room("kitchen", 0, 10, 38, c0 - 2, 42, G, H, TW, Block.TILE_GRAY, CT);
        bp.finish(mess, PL, TW, 2, 0);
        for (int a = 12; a <= c0 - 6; a += 5) { bp.prop("table_work", a, 31, a + 1, 31, G, 0, 1); bp.prop("bench_wood", a, 30, a + 1, 30, G, 0, -1); bp.prop("bench_wood", a, 32, a + 1, 32, G, 0, 1); bp.prop("table_work", a, 34, a + 1, 34, G, 0, 1); bp.prop("bench_wood", a, 35, a + 1, 35, G, 0, 1); }
        bp.prop("counter_kitchen", 12, 38, 16, 38, G, 0, -1); bp.prop("range_cast", 18, 42, 19, 42, G, 0, 1); bp.prop("sink_kitchen", 21, 42, 22, 42, G, 0, 1); bp.prop("fridge_old", 10, 42, G, -1, 0); bp.prop("pots_hanging", 20, 40, G + 3, 0, 1);
        bp.prop("butcher_block", 25, 40, G, 0, 1); bp.prop("meat_hooks", 27, 42, G + 3, 0, 1); bp.prop("sacks", c0 - 3, 42, G, 1, 0); bp.prop("blood_old", 24, 39, G, 0, 1);
        bp.loot(10, 38, G, Block.FRIDGE, "food"); bp.loot(c0 - 2, 38, G, Block.CABINET, "food"); bp.loot(14, 42, G, Block.CRATE, "food");
        bp.lampAt(16, 32, G + H - 1); bp.lampAt(26, 32, G + H - 1); bp.lampAt(18, 40, G + H - 1);

        // ---------------------------------------------------------------- the exercise yard (east end): open to the sky, walled
        final int y0a = c1 + 2, y1a = A - 11;
        bp.mass(y0a - 1, 28, y1a + 1, 43, G - 1, G - 1, Block.ASPHALT);
        for (int a = y0a - 1; a <= y1a + 1; a++) for (int yy = G; yy < G + 5; yy++) { bp.put(a, 28, yy, yy == G + 4 ? Block.BARBED : CO); bp.put(a, 43, yy, yy == G + 4 ? Block.BARBED : CO); }
        for (int b = 28; b <= 43; b++) for (int yy = G; yy < G + 5; yy++) bp.put(y1a + 1, b, yy, yy == G + 4 ? Block.BARBED : CO);
        for (int b = 30; b <= 41; b++) { bp.put(y0a + 2, b, G - 1, Block.ROAD_LINE); bp.put(y1a - 2, b, G - 1, Block.ROAD_LINE); }
        s.prop(y0a + 1, 30, Block.PEW); s.prop(y1a - 1, 41, Block.PEW); s.prop(y0a + 8, 36, Block.TIRE); s.prop(y0a + 9, 36, Block.TIRE); s.prop(y1a - 6, 31, Block.BARREL_FIRE);

        // ---------------------------------------------------------------- Ward C: the psychiatric wing
        final int w0 = 12, w1 = A - 13;                                              // the long corridor, end to end
        MapData.Room link = bp.room("corridor", 0, m - 1, 45, m, 51, G, H, PP, TW, CC);
        link.circulation = true;
        MapData.Room ward = bp.room("ward_corridor", 0, w0, 53, w1, 55, G, H, PP, TW, CC);
        ward.circulation = true;
        bp.finish(ward, PP, TG, 2, 0); bp.finish(link, PP, TG, 2, 0);
        // north side
        MapData.Room day = bp.room("dayroom", 0, w0, 45, 30, 51, G, H, PP, LI, CC);
        MapData.Room nurses = bp.room("nurses", 0, 32, 45, 40, 51, G, H, PL, LI, CC);
        MapData.Room pharmacy = bp.room("pharmacy", 0, 42, 45, m - 3, 51, G, H, TW, TW, CC);
        MapData.Room treatment = bp.room("treatment", 0, m + 2, 45, m + 12, 51, G, H, TG, TW, CC);
        MapData.Room hydro = bp.room("hydrotherapy", 0, m + 14, 45, m + 28, 51, G, H, TG, Block.TILE_GRAY, CC);
        MapData.Room[] padded = {bp.room("padded", 0, m + 30, 45, m + 34, 51, G, H, PL, LI, CC), bp.room("padded", 0, m + 36, 45, m + 40, 51, G, H, PL, LI, CC), bp.room("padded", 0, m + 42, 45, w1, 51, G, H, PL, LI, CC)};
        // south side
        MapData.Room obs = bp.room("ward", 0, w0, 57, 34, 66, G, H, PP, LI, CC);
        MapData.Room[] iso = {bp.room("isolation", 0, 36, 57, 40, 66, G, H, CO, CF, CN), bp.room("isolation", 0, 42, 57, 46, 66, G, H, CO, CF, CN), bp.room("isolation", 0, 48, 57, 52, 66, G, H, CO, CF, CN)};
        MapData.Room theatre = bp.room("theatre", 0, 54, 57, m + 14, 66, G, H, TG, TW, CC);
        MapData.Room morgue = bp.room("morgue", 0, m + 16, 57, m + 30, 66, G, H, TW, Block.TILE_GRAY, CC);
        MapData.Room boiler = bp.room("boiler", 0, m + 32, 57, w1, 66, G, H, Block.BRICK_BROWN, CF, CN);
        bp.finish(day, PP, TG, 2, 0); bp.finish(obs, PP, TG, 2, 0); bp.finish(nurses, PL, TG, 2, 0);

        // the corridor: the long view. Gurneys left where they stopped, a wheelchair facing the wall, most of the lights dead
        for (int a = w0 + 3; a <= w1 - 3; a += 7) {
            boolean lit = (a / 7) % 3 == 1;
            if (lit) bp.lampAt(a, 54, G + H - 1);
            bp.prop(lit ? "lamp_ceiling_lit" : "fluorescent_dead", a, 54, G + H - 1, 0, 1);
        }
        bp.prop("gurney", w0 + 6, 53, w0 + 7, 53, G, 0, -1); bp.prop("wheelchair", 31, 55, G, 0, 1); bp.prop("gurney", m + 9, 55, m + 10, 55, G, 0, 1); bp.prop("wheelchair", m + 33, 53, G, 0, -1);
        bp.prop("iv_stand", 41, 53, G, 0, -1); bp.prop("cart_medicine", m + 20, 53, G, 0, -1); bp.prop("papers_floor", 47, 54, G, 0, 1); bp.prop("blood_old", m + 5, 54, G, 0, 1); bp.prop("blood_old", m + 24, 54, G, 1, 0);
        bp.prop("bench_wood", 20, 55, 21, 55, G, 0, 1); bp.prop("notice_board", 26, 53, G + 2, 0, -1); bp.prop("missing_posters", 44, 55, G + 2, 0, 1); bp.prop("sign_exit", m - 3, 53, G + 3, 0, -1);
        bp.prop("radiator", 15, 55, G, 0, 1); bp.prop("radiator", m + 38, 55, G, 0, 1); bp.prop("pipes_ceiling", m + 16, 54, G + H - 1, 1, 0); bp.prop("cobwebs", w1, 53, G + H - 1, 1, 0); bp.prop("glass_shards", 36, 54, G, 0, 1);
        bp.prop("fire_extinguisher", m + 1, 55, G + 1, 0, 1); bp.prop("clock_wall", m - 6, 55, G + 3, 0, 1);

        // the day room: the chairs still in their ring, the piano, a television nobody will switch on
        for (int[] c : new int[][]{{17, 47}, {19, 46}, {21, 47}, {22, 49}, {20, 50}, {17, 49}}) bp.prop("chair_wood", c[0], c[1], G, c[0] < 19 ? -1 : 1, 0);
        bp.prop("piano_upright", 26, 45, 27, 45, G, 0, -1); bp.prop("table_round", 26, 49, G, 0, 1); bp.prop("bookcase_ransacked", w0, 47, G, -1, 0); bp.prop("plant_dead", 30, 51, G, 1, 0); bp.prop("armchair_green", w0, 50, G, -1, 0);
        bp.prop("calendar", 23, 45, G + 2, 0, -1); bp.prop("cross_wall", 14, 45, G + 3, 0, -1); bp.prop("rug_round", 19, 48, 20, 49, G, 0, 1); bp.prop("fan_ceiling", 20, 48, G + H - 1, 0, 1);
        bp.lampAt(24, 48, G + H - 1); bp.loot(30, 45, G, Block.CABINET, "residential");
        // the nurses' station and the dispensary
        bp.prop("desk_reception", 34, 50, 37, 50, G, 0, 1); bp.prop("filing_cabinet", 32, 45, G, 0, -1); bp.prop("filing_open", 33, 45, G, 0, -1); bp.prop("typewriter_desk", 39, 45, 40, 45, G, 0, -1); bp.prop("phone_wall", 36, 45, G + 2, 0, -1);
        bp.prop("first_aid_box", 40, 48, G + 2, 1, 0); bp.prop("water_cooler", 32, 50, G, -1, 0); bp.prop("intercom", 38, 45, G + 2, 0, -1);
        bp.loot(40, 51, G, Block.DESK, "office"); bp.lampAt(36, 48, G + H - 1);
        bp.prop("cart_medicine", 44, 47, G, 0, -1); bp.prop("shelf_empty", 42, 46, 42, 48, G, -1, 0); bp.prop("shelf_wall", 47, 45, G + 2, 0, -1); bp.prop("washbasin", m - 3, 46, G, 1, 0); bp.prop("glass_shards", 46, 49, G, 0, 1);
        bp.loot(44, 45, G, Block.MED_CABINET, "medical"); bp.loot(50, 45, G, Block.MED_CABINET, "medical"); bp.loot(m - 3, 51, G, Block.SAFE, "medical");
        // the treatment room: the chair, the machine, the light over it; what is on the floor has been there a long time
        bp.prop("bed_restraint", m + 6, 47, m + 7, 48, G, 0, -1); bp.prop("electroshock", m + 4, 47, G, -1, 0); bp.prop("lamp_surgical", m + 6, 48, G + H - 1, 0, 1); bp.prop("iv_stand", m + 9, 47, G, 1, 0);
        bp.prop("cart_medicine", m + 10, 50, G, 1, 0); bp.prop("xray_lightbox", m + 7, 45, G + 2, 0, -1); bp.prop("shackles_wall", m + 12, 48, G + 1, 1, 0); bp.prop("blood_old", m + 7, 50, G, 0, 1); bp.prop("washbasin", m + 2, 50, G, -1, 0);
        bp.lampAt(m + 7, 48, G + H - 1); bp.loot(m + 12, 45, G, Block.MED_CABINET, "medical");
        // hydrotherapy: a row of tubs with their canvas covers, the big valves, the drain
        for (int a = m + 16; a <= m + 26; a += 4) { bp.prop((a / 4) % 2 == 0 ? "bathtub_filthy" : "bathtub", a, 46, a + 1, 46, G, 0, -1); bp.prop("valve_wheel", a, 45, G + 2, 0, -1); }
        bp.prop("pipes_wall", m + 14, 48, G + 1, -1, 0); bp.prop("shower_decon", m + 27, 50, G, 1, 0); bp.prop("mop_bucket", m + 15, 51, G, 0, 1); bp.prop("gauges", m + 22, 51, G + 2, 0, 1); bp.prop("blood_old", m + 20, 49, G, 0, 1);
        bp.prop("wheelchair", m + 24, 50, G, 0, 1); bp.lampAt(m + 21, 49, G + H - 1);
        // the padded cells: nothing in them but the walls, and what was written on the walls
        for (int k = 0; k < 3; k++) {
            int a0 = m + 30 + k * 6, a1 = k == 2 ? w1 : a0 + 4;
            for (int a = a0; a <= a1; a += 2) { bp.prop("padding_wall", a, 45, G + 1, 0, -1); }
            bp.prop("padding_wall", a0, 48, G + 1, -1, 0); bp.prop("padding_wall", a1, 48, G + 1, 1, 0);
            if (k == 1) bp.prop("bedroll", a0 + 2, 47, G, 0, -1);
            if (k == 2) { bp.prop("shackles_wall", a1, 46, G + 1, 1, 0); bp.prop("blood_old", a0 + 2, 49, G, 0, 1); }
        }
        // the observation ward: beds with straps in two rows, screens, drips; one bed made
        for (int a = w0 + 1; a <= 32; a += 4) { bp.prop(a % 8 == 5 ? "bed_restraint" : "bed_hospital_p", a, 65, a, 66, G, 0, 1); if (a % 8 == 1) bp.prop("iv_stand", a + 1, 66, G, 0, 1); bp.prop("bed_hospital_p", a, 57, a, 58, G, 0, -1); }
        bp.prop("screen_folding", 20, 61, 21, 61, G, 0, 1); bp.prop("wheelchair", 28, 61, G, 1, 0); bp.prop("cart_medicine", 15, 62, G, -1, 0); bp.prop("curtain_torn", 24, 66, G + 1, 0, 1); bp.prop("papers_floor", 30, 62, G, 0, 1);
        bp.prop("cross_wall", 34, 61, G + 3, 1, 0); bp.prop("radiator", w0, 61, G, -1, 0); bp.prop("blood_old", 18, 63, G, 0, 1);
        bp.lampAt(18, 61, G + H - 1); bp.loot(34, 66, G, Block.MED_CABINET, "medical"); bp.loot(w0, 64, G, Block.CABINET, "residential");
        // isolation: a steel door, a slot, a cage inside the room
        for (int k = 0; k < 3; k++) { int a0 = 36 + k * 6; bp.prop("cage_cell", a0 + 1, 62, a0 + 3, 65, G, 0, 1); bp.prop("toilet_steel", a0, 66, G, -1, 0); if (k != 1) bp.prop("shackles_wall", a0 + 4, 60, G + 1, 1, 0); if (k == 1) bp.prop("blood_old", a0 + 2, 59, G, 0, 1); }
        // the operating theatre: the table under the lamp, the trays, the tiled gutter; this is where it works
        bp.prop("table_operating", m + 4, 61, m + 5, 62, G, 0, 1); bp.prop("lamp_surgical", m + 4, 61, G + H - 1, 0, 1); bp.prop("cart_tool", m + 7, 61, G, 1, 0); bp.prop("cart_medicine", m + 2, 62, G, -1, 0);
        bp.prop("iv_stand", m + 6, 63, G, 1, 0); bp.prop("xray_lightbox", m + 2, 57, G + 2, 0, -1); bp.prop("xray_lightbox", m + 5, 57, G + 2, 0, -1); bp.prop("washbasin", 54, 59, G, -1, 0); bp.prop("washbasin", 54, 61, G, -1, 0);
        bp.prop("meat_hooks", m + 10, 65, G + 3, 0, 1); bp.prop("butcher_block", m + 11, 60, G, 1, 0); bp.prop("blood_old", m + 4, 64, G, 0, 1); bp.prop("blood_old", m + 9, 62, G, 1, 0); bp.prop("blood_old", m + 1, 59, G, 0, 1);
        bp.prop("gurney", 56, 65, 57, 65, G, 0, 1); bp.prop("shelf_parts", m + 14, 63, G, 1, 0); bp.prop("drum_hazard", 54, 66, G, -1, 0);
        bp.lampAt(m + 4, 61, G + H - 1); bp.loot(m + 14, 57, G, Block.MED_CABINET, "medical"); bp.loot(54, 64, G, Block.CABINET, "medical");
        bp.lair("butcher", m + 8, 64, G);
        // the mortuary: the drawers, two tables, the scale
        for (int a = m + 17; a <= m + 29; a += 3) bp.prop("locker_row", a, 66, a + 1, 66, G, 0, 1);
        bp.prop("table_operating", m + 20, 60, m + 21, 61, G, 0, 1); bp.prop("table_operating", m + 26, 60, m + 27, 61, G, 0, 1); bp.prop("gurney", m + 17, 58, m + 18, 58, G, 0, -1); bp.prop("freezer_chest", m + 29, 57, m + 30, 57, G, 0, -1);
        bp.prop("blood_old", m + 23, 63, G, 0, 1); bp.prop("lamp_hanging_off", m + 23, 61, G + H - 1, 0, 1); bp.prop("mop_bucket", m + 16, 65, G, -1, 0);
        bp.lampAt(m + 23, 61, G + H - 1); bp.loot(m + 30, 62, G, Block.CABINET, "medical");
        // the boiler house: the old escape tunnel comes up here
        bp.prop("boiler", m + 34, 64, m + 36, 66, G, 0, 1); bp.prop("water_heater", m + 39, 66, G, 0, 1); bp.prop("pipe_run", m + 33, 57, m + 38, 57, G + 3, 0, -1); bp.prop("valve_wheel", m + 37, 66, G + 2, 0, 1);
        bp.prop("workbench_vice", w1 - 3, 57, w1 - 2, 57, G, 0, -1); bp.prop("drum_oil", w1, 66, G, 1, 0); bp.prop("fuse_box", w1, 61, G + 2, 1, 0); bp.prop("ladder_lean", m + 32, 62, G, -1, 0);
        bp.lampAt(m + 38, 61, G + H - 1); bp.loot(w1, 58, G, Block.CRATE, "industrial");
        lb.g.site("jail_tunnel", s.x(m + 41, 62), G, s.z(m + 41, 62));

        // ---------------------------------------------------------------- what is in the front of the house
        bp.prop("desk_reception", m - 2, 13, m + 1, 13, G, 0, 1); bp.prop("bench_wood", m - 4, 11, m - 4, 12, G, -1, 0); bp.prop("bench_wood", m + 3, 11, m + 3, 12, G, 1, 0); bp.prop("notice_board", m + 3, 14, G + 2, 1, 0);
        bp.prop("missing_posters", m - 4, 14, G + 2, -1, 0); bp.prop("plant_dead", m - 4, 15, G, -1, 0); bp.prop("clock_wall", m, 15, G + 3, 0, 1); bp.prop("papers_floor", m + 1, 11, G, 0, 1);
        bp.lampAt(m, 12, G + H - 1); bp.loot(m + 2, 15, G, Block.DESK, "office");
        for (int a = m - 15; a <= m - 8; a += 3) bp.prop("visit_booth", a, 12, a + 1, 13, G, 0, 1);
        bp.prop("bench_wood", m - 16, 15, m - 15, 15, G, 0, 1); bp.prop("vending_machine", m - 6, 15, G, 1, 0); bp.prop("sign_exit", m - 6, 11, G + 3, 1, 0); bp.lampAt(m - 11, 12, G + H - 1);
        bp.prop("desk_writing", m + 9, 11, m + 11, 11, G, 0, -1); bp.prop("armchair", m + 10, 13, G, 0, 1); bp.prop("bookcase", m + 5, 12, G, -1, 0); bp.prop("bookcase", m + 5, 14, G, -1, 0); bp.prop("safe_big", m + 15, 15, G, 1, 0);
        bp.prop("painting_portrait", m + 10, 10, G + 2, 0, -1); bp.prop("rug_persian", m + 9, 13, m + 11, 14, G, 0, 1); bp.prop("globe", m + 14, 10, G, 0, -1); bp.prop("coat_stand", m + 6, 10, G, 0, -1); bp.prop("map_wall", m + 15, 12, G + 2, 1, 0);
        bp.lampAt(m + 10, 13, G + H - 1); bp.loot(m + 13, 15, G, Block.SAFE, "office"); bp.loot(m + 8, 15, G, Block.DESK, "office");
        bp.prop("locker_row", m - 16, 20, m - 15, 20, G, 0, -1); bp.prop("locker_open", m - 12, 20, G, 0, -1); bp.prop("shelf_parts", m - 16, 23, G, -1, 0); bp.prop("crate_stack", m - 11, 24, G, 1, 0); bp.prop("cart_tool", m - 13, 23, G, 0, 1);
        bp.lampAt(m - 13, 22, G + H - 1); bp.loot(m - 10, 20, G, Block.CRATE, "weapons"); bp.loot(m - 16, 24, G, Block.STASH_CACHE, "cache");
        bp.prop("console_control", m - 8, 20, m - 6, 20, G, 0, -1); bp.prop("panel_annunciator", m - 4, 20, G + 2, 0, -1); bp.prop("terminal_crt", m - 8, 23, G, -1, 0); bp.prop("radio_set", m - 3, 23, G, 1, 0); bp.prop("chair_wood", m - 6, 22, G, 0, -1);
        bp.lampAt(m - 6, 22, G + H - 1);
        for (int a = m + 3; a <= m + 7; a += 2) bp.prop(a % 4 == 1 ? "filing_open" : "filing_cabinet", a, 24, G, 0, 1);
        bp.prop("desk_office", m + 4, 21, m + 6, 21, G, 0, -1); bp.prop("photocopier", m + 8, 22, G, 1, 0); bp.prop("papers_floor", m + 5, 23, G, 0, 1); bp.lampAt(m + 5, 22, G + H - 1); bp.loot(m + 3, 20, G, Block.DESK, "office");
        bp.prop("bench_locker", m + 11, 22, m + 13, 22, G, 0, 1); bp.prop("locker_row", m + 10, 24, m + 11, 24, G, 0, 1); bp.prop("locker_row", m + 13, 24, m + 14, 24, G, 0, 1); bp.prop("shower_decon", m + 15, 20, G, 1, 0); bp.prop("washbasin", m + 10, 20, G, 0, -1);
        bp.lampAt(m + 12, 22, G + H - 1); bp.loot(m + 15, 24, G, Block.CABINET, "residential");
        for (int a = m - 14; a <= m + 13; a += 9) { bp.lampAt(a, 17, G + H - 1); bp.prop("fluorescent_dead", a + 3, 18, G + H - 1, 0, 1); }
        bp.prop("fire_extinguisher", m - 3, 18, G + 1, 0, 1); bp.prop("water_cooler", m + 2, 18, G, 0, 1); bp.prop("bench_wood", m - 12, 18, m - 11, 18, G, 0, 1);

        // ---------------------------------------------------------------- doors and openings
        bp.door(m - 1, 9, G, Block.DOOR_DOUBLE_METAL, true); bp.door(m, 9, G, Block.DOOR_DOUBLE_METAL, true);                 // the front doors
        bp.door(m - 5, 13, G, Block.DOOR_WOOD, false); bp.door(m + 4, 13, G, Block.DOOR_WOOD, false);                         // lobby -> visiting, lobby -> the warden
        bp.door(m - 1, 16, G, Block.DOOR_DOUBLE_METAL, false); bp.door(m, 16, G, Block.DOOR_DOUBLE_METAL, false);            // lobby -> the corridor
        bp.door(m - 13, 19, G, Block.DOOR_METAL, false); bp.door(m - 6, 19, G, Block.DOOR_METAL, false); bp.door(m + 5, 19, G, Block.DOOR_WOOD, false); bp.door(m + 12, 19, G, Block.DOOR_WOOD, false);
        bp.opening(m - 1, 19, G, 3, 2, true);                                                                              // the corridor -> the sally port
        bp.door(m - 1, 28, G, Block.DOOR_DOUBLE_METAL, false); bp.door(m, 28, G, Block.DOOR_DOUBLE_METAL, false);            // -> the cell hall
        bp.door(c0 - 1, 35, G, Block.DOOR_METAL, false); bp.door(c0 - 1, 36, G, Block.DOOR_METAL, false);                     // cell hall -> the mess
        bp.door(20, 37, G, Block.DOOR_METAL, false); bp.door(21, 37, G, Block.DOOR_METAL, false);                             // mess -> kitchen
        bp.door(c1 + 1, 35, G, Block.DOOR_METAL, false); bp.door(c1 + 1, 36, G, Block.DOOR_METAL, false);                     // cell hall -> the yard
        bp.door(m - 1, 43, G, Block.DOOR_DOUBLE_METAL, false); bp.door(m, 43, G, Block.DOOR_DOUBLE_METAL, false);            // cell hall -> Ward C
        bp.opening(m - 1, 44, G, 3, 2, true);
        bp.opening(m - 1, 52, G, 3, 2, true);                                                                              // the link -> the long corridor
        for (int[] d : new int[][]{{22, 52}, {36, 52}, {48, 52}, {m + 7, 52}, {m + 21, 52}, {m + 32, 52}, {m + 38, 52}, {m + 44, 52}}) bp.door(d[0], d[1], G, d[0] >= m + 30 ? Block.DOOR_METAL : Block.DOOR_WOOD, false);
        bp.door(23, 52, G, Block.DOOR_WOOD, false); bp.door(m + 22, 52, G, Block.DOOR_WOOD, false);
        for (int[] d : new int[][]{{22, 56}, {23, 56}, {38, 56}, {44, 56}, {50, 56}, {m + 3, 56}, {m + 4, 56}, {m + 22, 56}, {m + 23, 56}, {m + 36, 56}}) bp.door(d[0], d[1], G, d[0] >= 36 && d[0] <= 50 || d[0] >= m + 36 ? Block.DOOR_METAL : Block.DOOR_DOUBLE_WOOD, false);
        bp.door(w1 + 1, 62, G, Block.DOOR_METAL, true);                                                                     // the boiler house's own door, to the back of the yard
        // windows: barred, high, and few - the front offices have the only ordinary ones
        for (int a = m - 15; a <= m + 14; a += 3) { if (Math.abs(a - m) < 3) continue; bp.window(a, 9, a, 9, G + 1, G + 2, Block.GLASS_DIRTY); }
        for (int a = c0 + 2; a <= c1 - 2; a += 4) { bp.put(a, 28, G + 8, Block.CELL_BARS); bp.put(a, 43, G + 8, Block.CELL_BARS); }
        for (int a = w0 + 2; a <= w1 - 2; a += 5) bp.put(a, 67, G + 3, Block.CELL_BARS);
        for (int b = 47; b <= 49; b += 2) bp.put(w0 - 1, b, G + 3, Block.CELL_BARS);
        bp.put(w0 - 1, 54, G + 2, Block.CELL_BARS); bp.put(w0 - 1, 54, G + 3, Block.CELL_BARS); bp.put(w1 + 1, 54, G + 2, Block.CELL_BARS); bp.put(w1 + 1, 54, G + 3, Block.CELL_BARS);

        // ---------------------------------------------------------------- the roofs, the outside
        bp.mass(m - 17, 9, m + 16, 25, ROOF, ROOF, Block.ROOF_METAL);
        bp.mass(c0 - 1, 28, c1 + 1, 43, ROOF2, ROOF2, Block.ROOF_METAL);
        bp.mass(9, 28, c0 - 2, 43, ROOF, ROOF, Block.ROOF_METAL);
        bp.mass(w0 - 1, 44, w1 + 1, 67, ROOF, ROOF, Block.ROOF_METAL);
        bp.mass(m - 2, 26, m + 1, 27, ROOF, ROOF, Block.ROOF_METAL);
        bp.entry(m - 1, 8, G);
        // where the dead are: the cell hall, the mess, and all down the ward
        for (int[] c : new int[][]{{m - 14, 35}, {m + 12, 36}, {c0 + 4, 36}, {18, 33}, {26, 40}}) bp.lair("infected", c[0], c[1], G);
        for (int[] c : new int[][]{{m - 10, 30}, {m + 10, 41}}) bp.lair("infected", c[0], c[1], T1);
        for (int[] c : new int[][]{{20, 54}, {44, 54}, {m + 12, 54}, {m + 40, 54}, {22, 48}, {22, 62}, {m + 21, 48}, {m + 23, 62}, {38, 60}, {m + 37, 48}}) bp.lair("infected", c[0], c[1], G);
        MapData.Building main = bp.done(lm);

        // towers on the inside corners of the wall
        s.tower(7, 7, 9, "Blackmoor North Tower", lm.faction);
        s.tower(A - 10, 7, 9, "Blackmoor East Tower", lm.faction);
        s.tower(7, B - 10, 9, "Blackmoor West Tower", lm.faction);
        s.tower(A - 10, B - 10, 9, "Blackmoor South Tower", lm.faction);
        // taken over: fires in drums, barricades on the gate, slogans on the wall
        s.prop(m + 6, 7, Block.BARREL_FIRE); s.prop(m - 8, 7, Block.BARREL_FIRE);
        s.sandbags(m - 8, 8, 4, true); s.sandbags(m + 3, 8, 4, true);
        for (int a = 12; a < A - 12; a += 9) if (Math.abs(a - m) > 4 && s.get(a, 5, G + 1) == Block.CONCRETE) s.set(a, 5, G + 1, a % 2 == 0 ? Block.GRAFFITI : Block.GRAFFITI2);
        s.lampPost(m - 5, 7); s.lampPost(m + 4, 7); s.lampPost(8, 48); s.lampPost(A - 9, 48);
        lb.garrison(s, lm, main, m, 3);
        s.post("Cell Hall", "guard", lm.faction, m, 30, G);
        s.post("Exercise Yard", "patrol", lm.faction, y0a + 6, 36, G);
        s.post("Mess Hall", "leader", lm.faction, 20, 33, G);
    }
}
