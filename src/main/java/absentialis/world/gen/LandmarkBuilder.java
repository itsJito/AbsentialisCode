package absentialis.world.gen;

import static absentialis.world.gen.Gen.G;

import absentialis.engine.util.Rng;
import absentialis.world.Block;
import absentialis.world.MapData;
import absentialis.world.World;
import absentialis.world.gen.GenDefs.DistrictDef;
import absentialis.world.gen.WorldPlan.LotPlan;
import absentialis.world.gen.arch.Architect;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The ten landmark compounds (0.5), two per developed district. Which landmark stands in which district is data
 * ({@code content/districts.json}); this class only knows how to build a landmark of a given id on whatever lot
 * the plan reserved for it. Each compound is laid out in lot-local coordinates from a few enterable buildings
 * (archetypes in {@code content/buildings.json}) plus the ground works that make it a place: walls and gates,
 * yards, towers, aprons, fields. A district's headquarters is its landmark dressed for the faction that holds it.
 */
public final class LandmarkBuilder {
    interface Compound { void build(LandmarkBuilder lb, Site s, MapData.Landmark lm); }

    private static final Map<String, Compound> REGISTRY = new LinkedHashMap<>();
    static {
        REGISTRY.put("mansion", Landmarks10::halloran);                // 1.0: the Halloran Mansion, rebuilt inside (0.9's is Landmarks09.spencer)
        REGISTRY.put("suburban_house", LandmarkBuilder::suburbanHouse);
        REGISTRY.put("power_plant", Landmarks10::powerPlant);        // 1.0: the Power Plant, rebuilt inside (0.9's is Landmarks09.chernobyl)
        REGISTRY.put("science_lab", LandmarkBuilder::scienceLab);
        REGISTRY.put("military_base", LandmarkBuilder::militaryBase);
        REGISTRY.put("airport", LandmarkBuilder::airport);
        REGISTRY.put("police_hq", LandmarkBuilder::policeHq);
        REGISTRY.put("grocery", LandmarkBuilder::grocery);
        REGISTRY.put("jail", Landmarks11::jail);          // 1.0: rebuilt by hand, with Ward C
        REGISTRY.put("farm", LandmarkBuilder::farm);
    }

    public static boolean known(String id) { return REGISTRY.containsKey(id); }

    final Gen g;
    private final Architect architect;

    public LandmarkBuilder(Gen g, Architect architect) { this.g = g; this.architect = architect; }

    public MapData.Landmark build(LotPlan lp) {
        Compound c = REGISTRY.get(lp.landmark.id);
        if (c == null) throw new IllegalStateException("No landmark compound registered for '" + lp.landmark.id + "' (content/districts.json)");
        DistrictDef def = g.defs.of(lp.district);
        MapData.Landmark lm = new MapData.Landmark();
        lm.id = lp.landmark.id; lm.name = lp.landmark.name; lm.district = lp.district; lm.front = lp.front; lm.headquarters = lp.headquarters;
        lm.faction = lp.landmark.faction == null || lp.landmark.faction.isEmpty() ? def.faction : lp.landmark.faction;
        lm.x0 = lp.rect.x0(); lm.z0 = lp.rect.z0(); lm.x1 = lp.rect.x1() - 1; lm.z1 = lp.rect.z1() - 1;
        Site s = new Site(g, architect, lp.rect, lp.front, lp.district, def, new Rng(lp.seed));
        s.begin();
        if (System.getenv("ABS_GENLOG") != null) System.out.println("[Gen] landmark " + lm.id + " lot " + s.A + " x " + s.B + " at " + lm.x0 + "," + lm.z0 + " front " + lp.front);
        c.build(this, s, lm);
        s.end();
        g.map.anchors.add(lm);
        g.landmark(lm.name, lm.headquarters ? "headquarters" : "landmark", lm.cx(), G, lm.cz());
        return lm;
    }

    MapData.Building bld(Site s, MapData.Landmark lm, String arch, int a0, int b0, int a1, int b1, int front, int floors, String name) {
        MapData.Building b = s.building(arch, a0, b0, a1, b1, front, floors, name, lm.id);
        lm.buildings.add(b.id);
        return b;
    }

    /** Posts for the faction that holds a compound: its command post at the main door and a guard at the gate. */
    void garrison(Site s, MapData.Landmark lm, MapData.Building main, int gateA, int gateB) {
        int[] e = s.local(main.entryX, main.entryZ);
        s.post(lm.name, lm.headquarters ? "hq" : "guard", lm.faction, e[0], Math.max(0, e[1] - 1), G);
        s.post(lm.name + " Gate", "guard", lm.faction, gateA, gateB, G);
        if (lm.headquarters) s.post(lm.name + " Yard", "patrol", lm.faction, Math.min(s.A - 3, e[0] + 6), Math.max(2, e[1] - 4), G);
    }

    // ================================================================== RESIDENTIAL
    private static void mansion(LandmarkBuilder lb, Site s, MapData.Landmark lm) {
        int A = s.A, B = s.B, m = A / 2;
        s.ground(Block.GRASS);
        s.enclose(0, 0, A - 1, B - 1, Block.STONE_BLOCK, 3, Block.IRON_FENCE, new int[]{m - 2, 0, m + 1, 0}, new int[]{m - 1, B - 1, m, B - 1});
        s.path(m - 1, 0, m - 1, 22, Block.COBBLE, 3);
        MapData.Building house = lb.bld(s, lm, "lm_mansion", m - 20, 24, m + 19, 43, 0, 3, lm.name);
        s.walkToStreet(house, Block.COBBLE);
        MapData.Building coach = lb.bld(s, lm, "lm_coach_house", 6, 50, 19, 61, 1, 2, "Coach House");
        lb.bld(s, lm, "lm_greenhouse", A - 21, 50, A - 14, 61, 3, 1, "Greenhouse");
        s.path(m - 1, B - 1, m - 1, 45, Block.COBBLE, 2);
        s.path(20, 55, m - 1, 55, Block.COBBLE, 2);
        // the forecourt: a fountain on the axis, clipped hedges either side of the drive
        int fm = m + 14;
        for (int a = fm - 3; a <= fm + 3; a++) for (int b = 8; b <= 14; b++) {
            int da = Math.abs(a - fm), db = Math.abs(b - 11);
            if (Math.max(da, db) == 3) { if (!s.kept(a, b)) s.set(a, b, G, Block.STONE_CARVED); }
            else if (!s.kept(a, b)) s.set(a, b, G - 1, Block.WATER);
        }
        for (int a = 6; a < A - 6; a++) if (Math.abs(a - m) > 6) { if (s.open(a, 5)) s.set(a, 5, G, Block.HEDGE); if (a % 9 == 4) s.tree(a, 14, 6, false); }
        for (int b = 6; b < B - 6; b += 9) { s.tree(4, b, 6, s.r.chance(0.2)); s.tree(A - 5, b, 6, s.r.chance(0.2)); }
        // held as a commune: bags inside the gate, tents on the lawn, a watch on two corners
        s.sandbags(m - 8, 3, 5, true); s.sandbags(m + 4, 3, 5, true);
        s.tent(9, 28, Block.CANVAS); s.tent(A - 15, 28, Block.TARP); s.tent(A - 15, 66, Block.CANVAS);
        s.prop(m + 7, 18, Block.BARREL_FIRE);
        s.crate(m + 9, 18, Block.CRATE, "food"); s.crate(m - 9, 46, Block.CRATE, "residential");
        s.tower(2, 2, 7, "Mansion West Watch", lm.faction);
        s.tower(A - 5, B - 5, 7, "Mansion East Watch", lm.faction);
        s.lampPost(m - 4, 2); s.lampPost(m + 3, 2);
        lb.garrison(s, lm, house, m, 1);
        s.post("Coach House", "guard", lm.faction, s.local(coach.entryX, coach.entryZ)[0], s.local(coach.entryX, coach.entryZ)[1], G);
    }

    private static void suburbanHouse(LandmarkBuilder lb, Site s, MapData.Landmark lm) {
        int A = s.A, B = s.B, m = A / 2;
        s.ground(Block.GRASS);
        s.enclose(0, 0, A - 1, B - 1, Block.HEDGE, 1, 0, new int[]{m - 1, 0, m, 0}, new int[]{A - 8, 0, A - 5, 0}, new int[]{m, B - 1, m, B - 1});
        MapData.Building house = lb.bld(s, lm, "lm_suburban", m - 13, 10, m + 12, 25, 0, 2, lm.name);
        s.walkToStreet(house, Block.COBBLE);
        s.path(A - 7, 0, A - 7, 9, Block.CONCRETE_FLOOR, 3);          // the drive to the garage end of the house
        lb.bld(s, lm, "garage", 4, B - 16, 10, B - 8, 0, 1, "Garden Shed");
        // patio and pool behind the house
        s.pave(m - 8, 27, m + 8, 32, Block.TILE_GRAY);
        s.prop(m - 2, 29, Block.TABLE); s.prop(m - 3, 29, Block.CHAIR); s.prop(m - 1, 29, Block.CHAIR); s.prop(m + 4, 30, Block.SOFA);
        for (int a = m - 6; a <= m + 6; a++) for (int b = 36; b <= 47; b++) {
            boolean rim = a == m - 6 || a == m + 6 || b == 36 || b == 47;
            s.set(a, b, G - 1, rim ? Block.TILE_WHITE : Block.WATER);
            s.set(a, b, G - 2, Block.TILE_WHITE);
            if (!rim) s.keep(a, b);
        }
        s.path(m, B - 1, m, 49, Block.COBBLE, 1);
        for (int b = 8; b < B - 6; b += 11) { s.tree(4, b, 6, false); s.tree(A - 5, b + 4, 5, s.r.chance(0.2)); }
        for (int k = 0; k < 3; k++) { s.set(A - 12 + k * 2, 56, G, Block.STEEL_BEAM); s.set(A - 12 + k * 2, 56, G + 1, Block.STEEL_BEAM); }
        s.fill(A - 12, 56, A - 8, 56, G + 2, G + 2, Block.STEEL_BEAM);      // a swing frame
        s.crate(m + 10, 27, Block.TRASH, "trash");
        s.crate(6, B - 6, Block.CRATE, "residential");
        s.lampPost(m + 3, 2);
        lb.garrison(s, lm, house, m, 1);
    }

    // ================================================================== INDUSTRIAL
    private static void powerPlant(LandmarkBuilder lb, Site s, MapData.Landmark lm) {
        int A = s.A, B = s.B, m = A / 2;
        s.ground(Block.CONCRETE_FLOOR);
        s.enclose(0, 0, A - 1, B - 1, Block.CHAINLINK, 3, Block.BARBED, new int[]{38, 0, 43, 0}, new int[]{A - 46, 0, A - 43, 0});
        s.path(40, 0, 40, 10, Block.ASPHALT, 4);
        MapData.Building hall = lb.bld(s, lm, "lm_turbine_hall", 8, 12, 57, 35, 0, 1, "Turbine Hall");
        MapData.Building ctrl = lb.bld(s, lm, "lm_plant_control", 62, 12, 85, 27, 0, 3, "Unit Control");
        s.walkToStreet(ctrl, Block.CONCRETE);
        // cooling towers on the far side of the yard
        lb.coolingTower(s.x(A - 22, 22), s.z(A - 22, 22), 34);
        if (B - 22 - 22 >= 31) lb.coolingTower(s.x(A - 22, B - 22), s.z(A - 22, B - 22), 34);
        // the stack: a banded brick chimney beside the hall
        for (int y = G; y < Math.min(World.H - 4, G + 44); y++) for (int da = -2; da <= 2; da++) for (int db = -2; db <= 2; db++) {
            boolean shell = Math.max(Math.abs(da), Math.abs(db)) == 2 && Math.abs(da) + Math.abs(db) < 4;
            if (shell) s.set(72 + da, 40 + db, y, ((y - G) / 6) % 2 == 0 ? Block.BRICK_RED : Block.BRICK_WHITE);
        }
        s.set(72, 38, Math.min(World.H - 4, G + 44), Block.RED_LIGHT);
        // switchyard: transformer bays under gantries; the breaker panel restores the district's power
        int sy0 = 46, sy1 = B - 14;
        s.pave(8, sy0, 50, sy1, Block.GRAVEL);
        s.enclose(8, sy0, 50, sy1, Block.CHAINLINK, 2, 0, new int[]{28, sy0, 30, sy0});
        s.path(29, sy0, 29, sy0 - 8, 0, 2);
        for (int a = 13; a <= 45; a += 8) {
            for (int b = sy0 + 3; b <= sy1 - 3; b += 6) { if (s.prop(a, b, Block.TRANSFORMER)) { s.set(a, b, G + 1, Block.MACHINE); } s.prop(a + 2, b, Block.MACHINE); }
            for (int y = G; y < G + 9; y++) { s.set(a - 2, sy0 + 1, y, Block.LATTICE); s.set(a - 2, sy1 - 1, y, Block.LATTICE); }
            s.fill(a - 2, sy0 + 1, a - 2, sy1 - 1, G + 9, G + 9, Block.LATTICE);
        }
        lb.g.site("substation", s.x(13, sy0 + 3), G, s.z(13, sy0 + 3));
        // rail siding along the back fence: where the freight leaves from
        for (int a = 2; a < A - 42; a++) { s.set(a, B - 6, G - 1, a % 3 == 0 ? Block.RAIL_SLEEPER : Block.RAIL); s.set(a, B - 4, G - 1, a % 3 == 0 ? Block.RAIL_SLEEPER : Block.RAIL); s.keep(a, B - 5); }
        for (int car = 0; car < 3; car++) { int a0 = 14 + car * 16; int col = new int[]{Block.CONTAINER_RED, Block.CONTAINER_GREEN, Block.CONTAINER_BLUE}[car]; s.fill(a0, B - 6, a0 + 11, B - 4, G, G + 2, col); s.fill(a0 + 1, B - 5, a0 + 10, B - 5, G, G + 1, Block.AIR); s.set(a0 + 5, B - 6, G, Block.AIR); s.set(a0 + 5, B - 6, G + 1, Block.AIR); s.crate(a0 + 2, B - 5, Block.CRATE, "industrial"); }
        lb.g.site("rail_exit", s.x(6, B - 8), G, s.z(6, B - 8));
        // the yard as its holders keep it: bags on the gate, a fire going, loot and fuel under guard
        s.sandbags(34, 3, 4, true); s.sandbags(45, 3, 4, true);
        s.prop(48, 6, Block.BARREL_FIRE);
        s.tent(90, 60, Block.TARP);
        for (int k = 0; k < 6; k++) s.prop(60 + k * 2, 38 + (k & 1), Block.BARREL);
        s.crate(64, 44, Block.CRATE, "industrial"); s.crate(66, 44, Block.TOOLBOX, "industrial"); s.crate(58, 8, Block.DUMPSTER, "trash");
        s.tower(2, 2, 8, "Plant Gate Watch", lm.faction);
        s.tower(A - 5, 2, 8, "Plant East Watch", lm.faction);
        s.lampPost(36, 2); s.lampPost(46, 2); s.lampPost(60, 30); s.lampPost(90, 40);
        lb.garrison(s, lm, hall, 40, 2);
        s.post("Unit Control", "leader", lm.faction, s.local(ctrl.entryX, ctrl.entryZ)[0], s.local(ctrl.entryX, ctrl.entryZ)[1], G);
        s.post("Switchyard", "patrol", lm.faction, 29, sy0 - 3, G);
    }

    /** Hyperboloid natural-draught tower with an open colonnade at its foot. */
    private void coolingTower(int cx, int cz, int height) {
        float rmin = 7.5f, yt = 26, a = 22f;
        for (int dx = -13; dx <= 13; dx++) for (int dz = -13; dz <= 13; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < 11.5) { g.set(cx + dx, G - 2, cz + dz, Block.CONCRETE_DARK); g.set(cx + dx, G - 1, cz + dz, d < 9.5 ? Block.WATER : Block.CONCRETE_DARK); }
        }
        for (int y = 0; y < height; y++) {
            double rr = rmin * Math.sqrt(1 + ((y - yt) / a) * ((y - yt) / a));
            for (int dx = -14; dx <= 14; dx++) for (int dz = -14; dz <= 14; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > rr + 0.5 || d <= rr - 0.75) continue;
                int sector = (int) Math.floor((Math.atan2(dz, dx) + Math.PI) / (Math.PI * 2) * 24);
                if (y < 4 && (sector & 1) == 0) continue;
                g.set(cx + dx, G + y, cz + dz, sector % 5 == 0 && y > height * 0.35 ? Block.CONCRETE_STAINED : Block.CONCRETE);
            }
        }
    }

    private static void scienceLab(LandmarkBuilder lb, Site s, MapData.Landmark lm) {
        int A = s.A, B = s.B, m = A / 2;
        s.ground(Block.CONCRETE_FLOOR);
        s.enclose(0, 0, A - 1, B - 1, Block.CONCRETE_PANEL, 3, Block.BARBED, new int[]{m - 2, 0, m + 1, 0}, new int[]{A - 1, B - 12, A - 1, B - 10});
        s.path(m - 1, 0, m - 1, 10, Block.ASPHALT, 3);
        MapData.Building lab = lb.bld(s, lm, "lm_lab", m - 20, 12, m + 19, 31, 0, 3, lm.name);
        s.walkToStreet(lab, Block.CONCRETE);
        MapData.Building annex = lb.bld(s, lm, "lm_lab_annex", m - 10, 42, m + 9, 55, 0, 1, "Containment Annex");
        // covered way from the back of the lab to the annex
        int[] ae = s.local(annex.entryX, annex.entryZ);
        s.path(ae[0], ae[1], ae[0], 33, Block.TILE_GRAY, 2);
        for (int b = 33; b <= ae[1]; b++) { if (b % 4 == 1) { s.set(ae[0] - 1, b, G, Block.STEEL_BEAM); s.set(ae[0] - 1, b, G + 1, Block.STEEL_BEAM); s.set(ae[0] - 1, b, G + 2, Block.STEEL_BEAM); } s.fill(ae[0] - 1, b, ae[0] + 2, b, G + 3, G + 3, Block.METAL_PLATE); }
        // staff car park
        s.pave(4, 6, 18, 30, Block.ASPHALT);
        for (int b = 8; b <= 28; b += 4) { s.set(6, b, G - 1, Block.ROAD_LINE_X); s.set(7, b, G - 1, Block.ROAD_LINE_X); s.set(15, b, G - 1, Block.ROAD_LINE_X); s.set(16, b, G - 1, Block.ROAD_LINE_X); }
        // standby generators behind their own fence
        s.pave(A - 20, 12, A - 5, 24, Block.GRAVEL);
        s.enclose(A - 20, 12, A - 5, 24, Block.CHAINLINK, 2, 0, new int[]{A - 20, 17, A - 20, 18});
        for (int a = A - 17; a <= A - 9; a += 4) { s.prop(a, 15, Block.GENERATOR); s.prop(a, 21, Block.BARREL); s.prop(a + 1, 21, Block.BARREL); }
        // what was set up outside when it went wrong: quarantine tents, markers, the dead
        s.tent(6, 60, Block.CANVAS); s.tent(14, 60, Block.CANVAS); s.tent(A - 22, 60, Block.CANVAS);
        for (int k = 0; k < 4; k++) s.crate(8 + k * 3, 68, Block.BODYBAG, "body");
        for (int a = 6; a < A - 6; a += 12) { if (s.open(a, 57)) { s.set(a, 57, G, Block.STEEL_BEAM); s.set(a, 57, G + 1, Block.BIOHAZARD); } }
        s.crate(m + 14, 36, Block.DUMPSTER, "trash"); s.crate(m - 16, 36, Block.CRATE, "lab");
        // the gate lodge and the people holding the place
        s.fill(m + 4, 2, m + 6, 4, G, G + 2, Block.CONCRETE_PANEL);
        s.fill(m + 5, 3, m + 5, 3, G, G + 1, Block.AIR); s.set(m + 4, 3, G, Block.AIR); s.set(m + 4, 3, G + 1, Block.AIR); s.set(m + 5, 2, G + 1, Block.GLASS_DIRTY);
        s.fill(m + 4, 2, m + 6, 4, G + 3, G + 3, Block.METAL_PLATE);
        s.sandbags(m - 7, 3, 4, true);
        s.lampPost(m - 4, 2); s.lampPost(m + 9, 2); s.lampPost(20, 34); s.lampPost(A - 22, 34);
        lb.garrison(s, lm, lab, m, 2);
        s.post("Annex Door", "guard", lm.faction, ae[0], ae[1] - 1, G);
        s.post("Lab Perimeter", "patrol", lm.faction, A - 8, 34, G);
    }

    // ================================================================== GOVERNMENTAL
    private static void militaryBase(LandmarkBuilder lb, Site s, MapData.Landmark lm) {
        int A = s.A, B = s.B, m = A / 2;
        s.ground(Block.CONCRETE_FLOOR);
        s.enclose(0, 0, A - 1, B - 1, Block.HESCO, 3, Block.BARBED, new int[]{m - 3, 0, m + 2, 0}, new int[]{0, B - 16, 0, B - 13});
        s.path(m - 2, 0, m - 2, 12, Block.ASPHALT, 4);
        // parade ground with the pad on it
        s.pave(36, 12, A - 37, B - 30, Block.ASPHALT);
        for (int a = m - 4; a <= m + 4; a++) for (int b = 30; b <= 38; b++) { s.set(a, b, G - 1, Math.max(Math.abs(a - m), Math.abs(b - 34)) == 4 || a == m || b == 34 ? Block.HELIPAD : Block.CONCRETE_DARK); s.keep(a, b); }
        lb.g.site("helipad", s.x(m, 34), G, s.z(m, 34));
        for (int y = G; y < G + 8; y++) s.set(m + 12, 16, y, Block.LAMP_POST);
        s.set(m + 12, 17, G + 7, Block.AWNING_RED);
        MapData.Building cmd = lb.bld(s, lm, "lm_command", m - 15, B - 26, m + 14, B - 11, 0, 3, "Command Post");
        MapData.Building b1 = lb.bld(s, lm, "lm_barracks", 6, 14, 31, 27, 0, 2, "Barracks A");
        MapData.Building b2 = lb.bld(s, lm, "lm_barracks", A - 32, 14, A - 7, 27, 0, 2, "Barracks B");
        MapData.Building pool = lb.bld(s, lm, "lm_motor_pool", 6, B - 28, 33, B - 13, 0, 1, "Motor Pool");
        lb.bld(s, lm, "storage_depot", A - 31, B - 28, A - 8, B - 15, 0, 1, "Ordnance Store");
        MapData.Building gate = lb.bld(s, lm, "guardhouse", m + 5, 3, m + 11, 9, 3, 1, "Gatehouse");
        for (MapData.Building b : new MapData.Building[]{cmd, b1, b2}) { int[] e = s.local(b.entryX, b.entryZ); s.path(e[0], e[1], e[0], Math.max(11, e[1] - 3), 0, 2); }
        // corner towers, inside the wall and reached from the yard
        s.tower(2, 2, 8, "Base North-West Tower", lm.faction);
        s.tower(A - 5, 2, 8, "Base North-East Tower", lm.faction);
        s.tower(2, B - 5, 8, "Base South-West Tower", lm.faction);
        s.tower(A - 5, B - 5, 8, "Base South-East Tower", lm.faction);
        // gate defences stand on the base side of the wall, clear of the road outside
        s.sandbags(m - 9, 3, 5, true); s.sandbags(m - 9, 8, 5, true);
        s.prop(m - 4, 6, Block.BARRIER); s.prop(m + 3, 6, Block.BARRIER);
        s.tent(38, B - 40, Block.CANVAS_OLIVE); s.tent(46, B - 40, Block.CANVAS_OLIVE); s.tent(A - 50, B - 40, Block.CANVAS_OLIVE);
        for (int k = 0; k < 4; k++) s.crate(40 + k * 2, 14, Block.CRATE_MIL, "military");
        for (int k = 0; k < 5; k++) s.prop(A - 44 + k * 2, 14, Block.BARREL);
        s.lampPost(m - 6, 2); s.lampPost(m + 4, 12); s.lampPost(34, 30); s.lampPost(A - 35, 30); s.lampPost(m, B - 29);
        int[] ce = s.local(cmd.entryX, cmd.entryZ), ge = s.local(gate.entryX, gate.entryZ), pe = s.local(pool.entryX, pool.entryZ);
        s.post(lm.name, "camp", lm.faction, m, 24, G);
        s.post("Command Post", "leader", lm.faction, ce[0], ce[1] - 1, G);
        s.post("Main Gate", "restricted", lm.faction, m, 3, G);
        s.post("Gatehouse", "guard", lm.faction, ge[0], ge[1], G);
        s.post("Motor Pool", "patrol", lm.faction, pe[0], pe[1] - 2, G);
        s.post("Parade Ground", "hq", lm.faction, m - 8, 22, G);
    }

    private static void airport(LandmarkBuilder lb, Site s, MapData.Landmark lm) {
        int A = s.A, B = s.B, m = A / 2;
        boolean alongX = (s.f.front & 1) == 0;
        s.ground(Block.GRASS);
        s.enclose(0, 0, A - 1, B - 1, Block.CHAINLINK, 2, Block.BARBED, new int[]{m - 2, 0, m + 1, 0}, new int[]{44, 0, 48, 0});
        // runway, with its centre line, edge lines and threshold bars
        int r0 = B - 24, r1 = B - 9, rc = (r0 + r1) / 2;
        for (int a = 6; a <= A - 7; a++) for (int b = r0; b <= r1; b++) {
            int id = Block.RUNWAY;
            if (b == rc && (a / 4) % 2 == 0) id = alongX ? Block.ROAD_LINE_X : Block.ROAD_LINE;
            if (b == r0 + 1 || b == r1 - 1) id = alongX ? Block.ROAD_EDGE_X : Block.ROAD_EDGE;
            if ((a < 14 || a > A - 15) && (b - r0) % 3 == 1 && a % 8 < 5) id = Block.CROSSWALK;
            s.set(a, b, G - 1, id);
            s.keep(a, b);
        }
        // apron and taxiways
        s.pave(10, 30, A - 11, r0 - 1, Block.CONCRETE_FLOOR);
        for (int a = 10; a <= A - 11; a++) if (a % 6 < 3) s.set(a, r0 - 2, G - 1, Block.HAZARD_STRIPE);
        s.pave(6, 2, A - 7, 5, Block.ASPHALT);
        MapData.Building term = lb.bld(s, lm, "lm_terminal", m - 23, 8, m + 22, 25, 0, 2, "Terminal");
        s.walkToStreet(term, Block.SIDEWALK);
        MapData.Building h1 = lb.bld(s, lm, "lm_hangar", 12, 6, 41, 27, 2, 1, "Hangar 1");
        MapData.Building h2 = lb.bld(s, lm, "lm_hangar", A - 42, 6, A - 13, 27, 2, 1, "Hangar 2");
        MapData.Building annex = lb.bld(s, lm, "lm_security_annex", 50, 8, 71, 20, 0, 2, "Airfield Security");
        s.walkToStreet(annex, Block.SIDEWALK);
        // control tower: a concrete shaft with a ladder inside and a glazed cab
        int ta = m + 30, tb = 10, cab = G + 20;
        s.fill(ta, tb, ta + 4, tb + 4, G, cab, Block.CONCRETE_PANEL);
        s.fill(ta + 1, tb + 1, ta + 3, tb + 3, G, cab + 3, Block.AIR);
        for (int y = G; y <= cab; y++) s.set(ta + 2, tb + 3, y, Block.LADDER);
        s.set(ta + 2, tb, G, Block.DOOR_METAL); s.set(ta + 2, tb, G + 1, Block.DOOR_METAL);
        s.keep(ta + 2, tb - 1);
        for (int a = ta - 1; a <= ta + 5; a++) for (int b = tb - 1; b <= tb + 5; b++) {
            boolean rim = a == ta - 1 || a == ta + 5 || b == tb - 1 || b == tb + 5;
            if (!(a == ta + 2 && b == tb + 3)) s.set(a, b, cab, Block.CONCRETE_DARK);
            if (rim) { s.set(a, b, cab + 1, Block.CONCRETE_PANEL); s.set(a, b, cab + 2, Block.GLASS_BLUE); }
            s.set(a, b, cab + 3, Block.METAL_PLATE);
        }
        s.set(ta + 2, tb + 2, cab + 4, Block.RED_LIGHT);
        g(lb).container(s.x(ta + 4, tb + 1), cab + 1, s.z(ta + 4, tb + 1), Block.RADIO_SET, "tech");
        // fuel farm
        s.pave(m + 44, 8, m + 60, 20, Block.GRAVEL);
        s.enclose(m + 44, 8, m + 60, 20, Block.CHAINLINK, 2, 0, new int[]{m + 51, 20, m + 52, 20});
        for (int a = m + 47; a <= m + 57; a += 5) { s.fill(a, 11, a + 1, 12, G, G + 2, Block.WATER_TANK); s.prop(a, 17, Block.BARREL); s.prop(a + 1, 17, Block.BARREL); }
        // a light aircraft left on the apron
        int pa = m - 34, pb = 38;
        s.fill(pa, pb, pa + 9, pb, G + 1, G + 1, Block.CORRUGATED);
        s.fill(pa + 3, pb - 4, pa + 4, pb + 4, G + 1, G + 1, Block.CORRUGATED);
        s.fill(pa + 8, pb - 1, pa + 9, pb + 1, G + 1, G + 1, Block.CORRUGATED);
        s.set(pa + 9, pb, G + 2, Block.AWNING_RED); s.set(pa + 2, pb, G + 2, Block.GLASS_BLUE);
        s.set(pa + 3, pb - 2, G, Block.TIRE); s.set(pa + 3, pb + 2, G, Block.TIRE); s.set(pa + 8, pb, G, Block.TIRE);
        for (int k = 0; k < 6; k++) s.crate(m - 12 + k * 4, 34, k % 2 == 0 ? Block.CRATE : Block.CRATE_MIL, k % 2 == 0 ? "residential" : "military");
        s.sandbags(m - 8, 28, 5, true); s.sandbags(m + 4, 28, 5, true);
        for (int a = 20; a < A - 20; a += 28) s.lampPost(a, 29);
        lb.g.site("airfield", s.x(m, 40), G, s.z(m, 40));
        int[] te = s.local(term.entryX, term.entryZ), he = s.local(h1.entryX, h1.entryZ), he2 = s.local(h2.entryX, h2.entryZ);
        s.post("Terminal", "guard", lm.faction, te[0], Math.max(0, te[1] - 1), G);
        s.post("Apron", "patrol", lm.faction, m, 36, G);
        s.post("Hangar 1", "guard", lm.faction, he[0], Math.min(B - 1, he[1] + 1), G);
        s.post("Hangar 2", "guard", lm.faction, he2[0], Math.min(B - 1, he2[1] + 1), G);
        s.post("Control Tower", "sniper", lm.faction, ta + 2, tb + 2, cab + 1);
        s.post("Airfield Gate", "checkpoint", lm.faction, m, 2, G);
    }

    private static Gen g(LandmarkBuilder lb) { return lb.g; }

    // ================================================================== DOWNTOWN
    private static void policeHq(LandmarkBuilder lb, Site s, MapData.Landmark lm) {
        int A = s.A, B = s.B, m = A / 2;
        // 1.1: the station is built by hand (LandmarksPolice) wherever the lot will take it
        if (LandmarksPolice.fits(s)) {
            MapData.Building built = LandmarksPolice.build(lb, s, lm);
            s.walkToStreet(built, Block.STONE_BLOCK);
            LandmarksPolice.grounds(lb, s, lm, built);
            return;
        }
        System.out.printf("[Gen] Police headquarters: lot %dx%d is too small for the 1.1 station; the 1.0 block is built%n", A, B);
        s.ground(Block.SIDEWALK);
        MapData.Building hq = lb.bld(s, lm, "lm_police", m - 20, 8, m + 19, 25, 0, 4, lm.name);
        s.walkToStreet(hq, Block.STONE_BLOCK);
        // forecourt: planters and flag poles either side of the steps
        for (int a = 4; a < A - 4; a += 6) if (Math.abs(a - s.local(hq.entryX, hq.entryZ)[0]) > 3) { s.prop(a, 4, Block.HEDGE); s.prop(a + 1, 4, Block.HEDGE); }
        for (int da : new int[]{-5, 6}) { int a = s.local(hq.entryX, hq.entryZ)[0] + da; if (s.open(a, 2)) for (int y = G; y < G + 7; y++) s.set(a, 2, y, Block.LAMP_POST); }
        // the motor yard behind: fenced, gated on the side lane
        int y0 = 30, y1 = Math.min(B - 2, 58);
        s.pave(3, y0, A - 4, y1, Block.ASPHALT);
        s.enclose(3, y0, A - 4, y1, Block.CHAINLINK, 3, Block.BARBED, new int[]{m - 1, y0, m + 1, y0}, new int[]{A - 4, y1 - 8, A - 4, y1 - 6});
        s.path(m, y0, m, y0 - 3, 0, 2);
        MapData.Building garage = lb.bld(s, lm, "lm_police_garage", 7, y1 - 15, 27, y1 - 3, 0, 1, "Motor Pool");
        for (int a = 32; a <= A - 10; a += 5) { s.set(a, y1 - 4, G - 1, Block.ROAD_LINE); s.set(a, y1 - 5, G - 1, Block.ROAD_LINE); s.set(a, y1 - 6, G - 1, Block.ROAD_LINE); }
        s.prop(A - 8, y0 + 3, Block.GENERATOR); s.prop(A - 9, y0 + 3, Block.BARREL); s.crate(A - 7, y0 + 6, Block.DUMPSTER, "trash");
        s.prop(m - 4, y0 + 2, Block.BARRIER); s.prop(m + 4, y0 + 2, Block.BARRIER);
        // roof pad: where a signalled helicopter sets down
        int[] lo = s.local(hq.x0, hq.z0), hi = s.local(hq.x1, hq.z1);
        int ra = Math.min(lo[0], hi[0]) + 3, rb = Math.min(lo[1], hi[1]) + 3;
        for (int a = ra; a < ra + 7; a++) for (int b = rb; b < rb + 7; b++) if (b < Math.max(lo[1], hi[1]) - 1) s.set(a, b, hq.topY, Math.max(Math.abs(a - ra - 3), Math.abs(b - rb - 3)) == 3 || a == ra + 3 || b == rb + 3 ? Block.HELIPAD : Block.CONCRETE_DARK);
        lb.g.site("helipad", s.x(ra + 3, rb + 3), hq.topY + 1, s.z(ra + 3, rb + 3));
        // held by contractors: bags across the forecourt, a marksman on the roof
        int ea = s.local(hq.entryX, hq.entryZ)[0];
        s.sandbags(ea - 7, 2, 4, true); s.sandbags(ea + 4, 2, 4, true);
        s.lampPost(3, 2); s.lampPost(A - 4, 2); s.lampPost(5, y0 + 2);
        lb.garrison(s, lm, hq, ea, 1);
        s.post("Headquarters Roof", "sniper", lm.faction, ra + 8, rb + 2, hq.topY + 1);
        s.post("Motor Yard", "patrol", lm.faction, s.local(garage.entryX, garage.entryZ)[0], s.local(garage.entryX, garage.entryZ)[1] - 2, G);
    }

    private static void grocery(LandmarkBuilder lb, Site s, MapData.Landmark lm) {
        int A = s.A, B = s.B, m = A / 2;
        s.ground(Block.ASPHALT);
        MapData.Building store = lb.bld(s, lm, "lm_grocery", m - 22, 20, m + 22, Math.min(B - 12, 47), 0, 1, lm.name);
        int ea = s.local(store.entryX, store.entryZ)[0];
        s.path(ea, 19, ea, 0, 0, 3);
        s.pave(m - 24, 17, m + 24, 19, Block.SIDEWALK);
        // car park: marked bays, trolley bays, lamps
        for (int a = 4; a < A - 4; a += 3) if (Math.abs(a - ea) > 3) for (int b = 2; b <= 14; b++) if (b < 7 || b > 9) s.set(a, b, G - 1, Block.ROAD_LINE);
        for (int a : new int[]{12, A - 14}) { s.prop(a, 8, Block.IRON_FENCE); s.prop(a + 1, 8, Block.IRON_FENCE); s.prop(a + 2, 8, Block.IRON_FENCE); s.prop(a, 9, Block.IRON_FENCE); s.prop(a + 2, 9, Block.IRON_FENCE); }
        s.lampPost(6, 8); s.lampPost(A - 7, 8); s.lampPost(m + 9, 16);
        s.crate(ea + 4, 18, Block.TRASH, "trash"); s.crate(ea - 5, 18, Block.TRASH, "trash");
        // the sign over the doors
        for (int da = -3; da <= 3; da++) s.set(ea + da, 20, store.topY + 1, da % 2 == 0 ? Block.NEON_GREEN : Block.NEON_OFF);
        // loading dock behind: a fenced service yard, pallets and bins
        int d0 = Math.min(B - 11, 49), d1 = B - 2;
        s.pave(3, d0, A - 4, d1, Block.CONCRETE_FLOOR);
        s.enclose(3, d0, A - 4, d1, Block.CHAINLINK, 2, 0, new int[]{3, d0 + 2, 3, d0 + 4}, new int[]{A - 4, d0 + 2, A - 4, d0 + 4});
        for (int a = 8; a < A - 8; a += 5) { if (s.crate(a, d1 - 1, Block.CRATE, "food") && s.r.chance(0.5) && s.air(a, d1 - 1, G + 1)) s.set(a, d1 - 1, G + 1, Block.CRATE); }
        s.crate(6, d0 + 1, Block.DUMPSTER, "trash"); s.crate(8, d0 + 1, Block.DUMPSTER, "trash");
        for (int a = m - 4; a <= m + 4; a += 2) s.set(a, d0 + 3, G - 1, Block.HAZARD_STRIPE);
        s.prop(A - 8, d1 - 1, Block.BARREL);
        s.post(lm.name, "guard", lm.faction, ea, 17, G);
        s.post("Loading Dock", "patrol", lm.faction, m, d0 + 3, G);
    }

    // ================================================================== OUTSKIRTS
    private static void jail(LandmarkBuilder lb, Site s, MapData.Landmark lm) {
        int A = s.A, B = s.B, m = A / 2;
        s.ground(Block.GRAVEL);
        // outer wire, a dead strip, then the wall
        s.enclose(0, 0, A - 1, B - 1, Block.CHAINLINK, 3, Block.BARBED, new int[]{m - 2, 0, m + 1, 0});
        s.pave(5, 5, A - 6, B - 6, Block.CONCRETE_FLOOR);
        s.enclose(5, 5, A - 6, B - 6, Block.CONCRETE, 5, Block.BARBED, new int[]{m - 2, 5, m + 1, 5});
        s.path(m - 1, 0, m - 1, 11, Block.ASPHALT, 3);
        MapData.Building admin = lb.bld(s, lm, "lm_prison_admin", m - 14, 12, m + 13, 25, 0, 2, "Administration");
        MapData.Building ca = lb.bld(s, lm, "lm_cellblock", 9, 32, 42, 45, 0, 2, "Cell Block A");
        MapData.Building cb = lb.bld(s, lm, "lm_cellblock", A - 43, 32, A - 10, 45, 0, 2, "Cell Block B");
        MapData.Building mess = lb.bld(s, lm, "lm_mess_hall", m - 12, B - 26, m + 11, B - 13, 0, 1, "Mess Hall");
        MapData.Building shed = lb.bld(s, lm, "utility_building", A - 19, B - 16, A - 11, B - 9, 0, 1, "Boiler House");
        for (MapData.Building b : new MapData.Building[]{admin, ca, cb, mess, shed}) { int[] e = s.local(b.entryX, b.entryZ); s.path(e[0], e[1], e[0], e[1] - 2, 0, 1); }
        // the old escape tunnel comes up in the boiler house
        lb.g.site("jail_tunnel", shed.entryX, G, shed.entryZ);
        // exercise yard: painted court, benches, weights left where they were dropped
        s.pave(m - 10, 28, m + 9, B - 30, Block.ASPHALT);
        for (int b = 30; b <= B - 32; b++) { s.set(m - 8, b, G - 1, Block.ROAD_LINE); s.set(m + 7, b, G - 1, Block.ROAD_LINE); }
        s.prop(m - 9, 29, Block.PEW); s.prop(m + 8, 29, Block.PEW); s.prop(m, B - 31, Block.TIRE); s.prop(m + 1, B - 31, Block.TIRE);
        // towers on the inside corners of the wall
        s.tower(7, 7, 9, "Blackmoor North Tower", lm.faction);
        s.tower(A - 10, 7, 9, "Blackmoor East Tower", lm.faction);
        s.tower(7, B - 10, 9, "Blackmoor West Tower", lm.faction);
        s.tower(A - 10, B - 10, 9, "Blackmoor South Tower", lm.faction);
        // taken over: fires in drums, barricades on the gate, slogans on the wall
        s.prop(m + 4, 9, Block.BARREL_FIRE); s.prop(m - 12, 30, Block.BARREL_FIRE);
        s.sandbags(m - 8, 8, 4, true); s.sandbags(m + 3, 8, 4, true);
        for (int a = 12; a < A - 12; a += 9) if (Math.abs(a - m) > 4 && s.get(a, 5, G + 1) == Block.CONCRETE) s.set(a, 5, G + 1, a % 2 == 0 ? Block.GRAFFITI : Block.GRAFFITI2);
        s.tent(14, 52, Block.TARP); s.tent(22, 52, Block.TARP);
        s.crate(12, 60, Block.CRATE, "weapons"); s.crate(14, 60, Block.STASH_CACHE, "cache"); s.prop(A - 14, 28, Block.BARREL);
        s.lampPost(m - 5, 7); s.lampPost(m + 4, 27); s.lampPost(8, 48); s.lampPost(A - 9, 48);
        lb.garrison(s, lm, admin, m, 3);
        s.post("Cell Block A", "guard", lm.faction, s.local(ca.entryX, ca.entryZ)[0], s.local(ca.entryX, ca.entryZ)[1] - 1, G);
        s.post("Cell Block B", "guard", lm.faction, s.local(cb.entryX, cb.entryZ)[0], s.local(cb.entryX, cb.entryZ)[1] - 1, G);
        s.post("Exercise Yard", "patrol", lm.faction, m, 30, G);
        s.post("Mess Hall", "leader", lm.faction, s.local(mess.entryX, mess.entryZ)[0], s.local(mess.entryX, mess.entryZ)[1] - 1, G);
    }

    private static void farm(LandmarkBuilder lb, Site s, MapData.Landmark lm) {
        int A = s.A, B = s.B, m = A / 2;
        s.ground(Block.GRASS);
        s.enclose(0, 0, A - 1, B - 1, Block.WOOD_DARK, 1, 0, new int[]{m - 2, 0, m + 1, 0}, new int[]{A - 1, 30, A - 1, 32}, new int[]{m, B - 1, m + 1, B - 1});
        // the yard: beaten earth between house, barn and shed
        s.pave(6, 6, A - 7, 38, Block.DIRT);
        s.path(m - 1, 0, m - 1, 8, Block.GRAVEL, 3);
        MapData.Building house = lb.bld(s, lm, "lm_farmhouse", 10, 10, 29, 23, 0, 2, lm.name);
        s.walkToStreet(house, Block.GRAVEL);
        MapData.Building barn = lb.bld(s, lm, "lm_barn", A - 34, 8, A - 15, 33, 3, 1, "Great Barn");
        MapData.Building shed = lb.bld(s, lm, "lm_machine_shed", m - 12, 22, m + 9, 35, 0, 1, "Machine Shed");
        lb.bld(s, lm, "lm_greenhouse", 10, 28, 19, 41, 1, 1, "Glasshouse");
        // silos by the barn
        for (int k = 0; k < 2; k++) {
            int ca = A - 9, cb = 12 + k * 9;
            for (int y = G; y < G + 14; y++) for (int da = -3; da <= 3; da++) for (int db = -3; db <= 3; db++) { int dd = da * da + db * db; if (dd <= 10 && dd >= 5) s.set(ca + da, cb + db, y, y % 5 == 4 ? Block.STEEL_BEAM : Block.CORRUGATED); }
            for (int da = -2; da <= 2; da++) for (int db = -2; db <= 2; db++) if (da * da + db * db <= 5) s.set(ca + da, cb + db, G + 14, Block.ROOF_METAL);
            for (int da = -3; da <= 3; da++) for (int db = -3; db <= 3; db++) s.keep(ca + da, cb + db);
        }
        // a wind pump over the trough
        int wa = 36, wb = 12;
        for (int y = G; y < G + 12; y++) s.set(wa, wb, y, Block.LATTICE);
        for (int k = -2; k <= 2; k++) { s.set(wa + k, wb, G + 12, Block.STEEL_BEAM); s.set(wa, wb, G + 12 + k, Block.STEEL_BEAM); }
        s.fill(wa + 2, wb, wa + 3, wb + 1, G, G, Block.WATER_TANK);
        // stock pen
        s.enclose(m + 14, 36, A - 12, 44, Block.WOOD_DARK, 1, 0, new int[]{m + 14, 39, m + 14, 40});
        for (int k = 0; k < 4; k++) s.prop(m + 17 + k * 4, 42, Block.HAY);
        // the fields: rows with a headland and a track through the middle to the back gate
        int f0 = 46, f1 = B - 4;
        s.pave(4, f0, A - 5, f1, Block.DIRT);
        s.path(m, B - 1, m, 37, Block.GRAVEL, 2);
        for (int a = 5; a <= A - 6; a++) for (int b = f0 + 1; b <= f1 - 1; b++) {
            if (a % 3 == 0 || Math.abs(a - m) <= 2 || b == (f0 + f1) / 2) continue;
            if (s.open(a, b) && s.r.chance(a < m ? 0.9 : 0.55)) s.set(a, b, G, Block.CROP);
        }
        for (int k = 0; k < 6; k++) s.prop(m + 5 + k * 6, (f0 + f1) / 2, Block.HAY);
        s.tree(5, 5, 7, false); s.tree(32, 5, 6, false); s.tree(A - 6, 40, 6, s.r.chance(0.3));
        // worked and guarded: a watch on the yard, stores stacked by the shed
        s.tower(3, 40, 6, "Farm Watch", lm.faction);
        s.prop(34, 26, Block.BARREL_FIRE);
        s.crate(m - 14, 30, Block.CRATE, "farm"); s.crate(m - 14, 31, Block.CRATE, "food"); s.prop(m + 11, 30, Block.BARREL); s.prop(m + 11, 31, Block.BARREL);
        s.sandbags(m - 7, 3, 4, true);
        s.lampPost(m + 3, 2); s.lampPost(32, 20);
        lb.garrison(s, lm, house, m, 2);
        s.post("Great Barn", "guard", lm.faction, s.local(barn.entryX, barn.entryZ)[0], s.local(barn.entryX, barn.entryZ)[1], G);
        s.post("Machine Shed", "patrol", lm.faction, s.local(shed.entryX, shed.entryZ)[0], Math.max(0, s.local(shed.entryX, shed.entryZ)[1] - 2), G);
    }
}
