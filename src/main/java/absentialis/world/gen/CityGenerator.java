package absentialis.world.gen;

import static absentialis.world.gen.Gen.G;

import absentialis.engine.util.Rng;
import absentialis.world.Block;
import absentialis.world.District;
import absentialis.world.MapData;
import absentialis.world.MapData.ExitType;
import absentialis.world.World;
import absentialis.world.gen.Streets.Rect;
import absentialis.world.gen.Streets.Style;
import absentialis.world.gen.WorldPlan.LotPlan;
import absentialis.world.gen.WorldPlan.Region;
import absentialis.world.gen.WorldPlan.RoadPlan;
import absentialis.world.gen.arch.Architect;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Deterministic generation of the whole Zone from a seed (0.5): a constraint-driven pipeline in the order the
 * brief sets out.
 *
 *   1  terrain and world boundary            {@link WildernessGen#terrain}
 *   2  district regions by suitability       {@link WorldPlan#of}
 *   3  major roads and district links        plan -> {@link Streets#road}
 *   4  landmark lots and their clearance     plan
 *   5  secondary streets, pedestrian alleys  plan -> streets, alleys
 *   6  blocks into lots                      plan
 *   7  supporting buildings on lots          {@link LotBuilder}, {@link LandmarkBuilder}
 *   8  interiors and entrances               {@link absentialis.world.gen.arch.Architect} (room graph first)
 *   9  props, vegetation, controlled damage  lot yards, street furniture, wilderness
 *  10  navigation, cover, tactical points    {@link absentialis.world.Nav#annotate}, {@link absentialis.world.CoverMap}
 *  11  geometric and accessibility checks    {@link GenValidator}
 *  12  repair or reject                      {@link GenValidator}
 *
 * The layout is data before it is voxels: {@link WorldPlan} reserves every road before anything is placed, every
 * structure is built through a clip rectangle that is its lot, and the validator checks what was built against
 * what was planned.
 */
public final class CityGenerator {
    private final World w;
    private final Gen g;
    public final Streets streets;
    private final Consumer<String> progress;
    public WorldPlan plan;
    public GenReport report;

    public CityGenerator(World w, long seed, Consumer<String> progress) {
        this.w = w;
        this.g = new Gen(w, seed);
        this.streets = new Streets(g);
        this.progress = progress == null ? s -> {} : progress;
        w.seed = seed;
    }

    public void generate() {
        long t0 = System.currentTimeMillis();
        w.map.clear();
        if (w.link != null) w.link.detach();                 // 1.2: built again in place: the native core's copy is of what was here before
        java.util.Arrays.fill(w.blocks, (byte) 0);
        w.dev = false;
        report = new GenReport();
        // ---- stages 2-7 as data
        progress.accept("Surveying terrain and allocating districts");
        plan = WorldPlan.of(g.seed, g.defs);
        w.map.plan = plan;
        w.map.report = report;
        report.planMs = System.currentTimeMillis() - t0;
        for (String n : plan.notes) report.note(n);
        for (int x = 0; x < World.W; x++) for (int z = 0; z < World.D; z++) w.districtMap[x * World.D + z] = (byte) plan.districtAt(x, z).ordinal();

        // ---- stage 1: terrain; then the city's made ground, column for column inside the boundary
        progress.accept("Shaping terrain and the world boundary");
        WildernessGen wild = new WildernessGen(g);
        wild.terrain();
        for (int x = World.CITY_MIN; x < World.CITY_MAX; x++) for (int z = World.CITY_MIN; z < World.CITY_MAX; z++) {
            g.set(x, 0, z, Block.BEDROCK);
            g.fill(x, 1, z, x, G - 2, z, Block.DIRT);
            g.set(x, G - 1, z, Block.SIDEWALK);
            g.fill(x, G, z, x, World.H - 1, z, Block.AIR);
        }

        // ---- stages 3 and 5: the reserved road network, then what runs under it
        progress.accept("Laying the ring road, arterials and district streets");
        Rng r = g.rng("arterial");
        for (RoadPlan rp : plan.roads) streets.road(rp.r, rp.alongX, rp.style);
        for (RoadPlan rp : plan.roads) if (rp.style == Style.AVENUE) streets.sewerAlong(rp.r, rp.alongX, r);
        alleys();

        // ---- stages 4, 6-9: landmark compounds, then every other lot
        Architect architect = new Architect(g);
        LandmarkBuilder landmarks = new LandmarkBuilder(g, architect);
        LotBuilder lots = new LotBuilder(g, architect);
        for (Region rg : plan.regions) {
            progress.accept("Building " + rg.district.display + ": landmarks");
            for (LotPlan lp : plan.lots) if (lp.district == rg.district && lp.landmark != null) landmarks.build(lp);
            progress.accept("Building " + rg.district.display + ": streets and lots");
            for (LotPlan lp : plan.lots) if (lp.district == rg.district && lp.landmark == null) lots.build(lp);
            g.landmark(rg.district.display, "district", rg.rect.cx(), G, rg.rect.cz());
        }
        report.clippedWrites = g.clipped;

        progress.accept("Dressing streets");
        streets.crosswalks();
        streets.furnish(g.rng("furnish"));
        streets.trunkSigns();
        streets.buildNodes();
        alleyNodes();
        factionPosts();

        progress.accept("Raising the quarantine wall");
        wild.quarantineWall(plan);
        for (int x = World.CITY_MAX; x <= WildernessGen.WALL_HI + 1; x++) for (int z = WildernessGen.HWY_Z0 + 1; z < WildernessGen.HWY_Z1 - 1; z++) g.set(x, G - 1, z, Block.ASPHALT);
        wild.outskirts(architect);

        progress.accept("Marking extraction routes");
        exitsAndInsertions(wild);
        w.claimExitZones();
        int swept = g.clearFoliageIndoors();
        System.out.printf("[Gen] Foliage: %d trees grown, %d leaf blocks placed, %d removed from inside rooms%n", g.treesGrown, g.leavesPlaced, swept);
        w.computeHeights();
        report.buildMs = System.currentTimeMillis() - t0 - report.planMs;

        // ---- stages 10-12
        progress.accept("Validating geometry, access and the street graph");
        long tv = System.currentTimeMillis();
        Validation.run(w);
        GenValidator.run(w, plan, streets, report);
        int propsOut = g.auditProps();
        if (g.propsOffWalls + g.propsOffCeilings > 0) System.out.println("[Gen] Fittings audit: " + g.fixturesReplaced + " block fixtures stood as models; " + g.propsOffWalls + " wall fittings taken off doors, doorways and thin air; " + g.propsOffCeilings + " ceiling fittings with no ceiling over them");
        System.out.printf("[Gen] Props: %d stood in the world (%d models), %d refused for want of room, %d taken out by repairs%n", w.map.props.size(), PropLib.count(), g.propsRefused, propsOut);
        w.computeHeights();
        int extra = absentialis.world.Nav.annotate(w);
        report.validateMs = System.currentTimeMillis() - tv;
        report.totalMs = System.currentTimeMillis() - t0;
        System.out.println("[Gen] Navigation graph: +" + extra + " door / yard / post nodes");
        System.out.println("[Gen] " + report);
        for (String line : report.lines) System.out.println("[Gen]   " + line);
        System.out.printf("[Gen] City generated in %d ms (plan %d, build %d, validate %d): %d loot spots, %d exits, %d nodes, %d lamps%n",
                report.totalMs, report.planMs, report.buildMs, report.validateMs, w.map.loot.size(), w.map.exits.size(), w.map.nodes.size(), w.map.lampsOnPower.size());
        absentialis.sim.ALife.configure(plan);
        String dump = System.getenv("ABS_GENDUMP");
        if (dump != null && !dump.isBlank()) GenDebug.dump(w, plan, streets, dump);
    }

    // ------------------------------------------------------------------ pedestrian alleys
    /** Mid-block alleys: paved, lit at their mouths, wide enough to fight along and too narrow to drive. */
    private void alleys() {
        for (Rect a : plan.alleys) {
            District d = w.district(a.cx(), a.cz());
            int surface = d == District.REDLINE ? Block.GRAVEL : d == District.MARKET ? Block.COBBLE : Block.CONCRETE_FLOOR;
            for (int x = a.x0(); x < a.x1(); x++) for (int z = a.z0(); z < a.z1(); z++) {
                g.set(x, G - 1, z, surface);
                g.fill(x, G, z, x, World.H - 1, z, Block.AIR);
                streets.roadMask[x * World.D + z] = 5;
            }
        }
    }

    private void alleyNodes() {
        MapData m = w.map;
        for (Rect a : plan.alleys) {
            boolean alongX = a.w() >= a.d();
            int len = alongX ? a.w() : a.d();
            MapData.Node prev = null;
            for (int i = 2; i < len; i += 14) {
                int x = alongX ? a.x0() + Math.min(i, len - 2) : a.cx(), z = alongX ? a.cz() : a.z0() + Math.min(i, len - 2);
                MapData.Node n = m.addNode(x, G, z, w.district(x, z));
                if (n.kind.equals("street") && m.nodes.get(m.nodes.size() - 1) == n) n.kind = "alley";
                if (prev != null) m.link(prev, n);
                prev = n;
            }
        }
    }

    /**
     * Patrol posts on the district's own streets (influence, not a fence): the home faction watches the spine's
     * junctions, and a neighbour's patrol stands where the district meets an arterial.
     */
    private void factionPosts() {
        for (Region rg : plan.regions) {
            int k = 0;
            for (RoadPlan rp : plan.roads) {
                if (rp.district != rg.district || !rp.kind.equals("cross")) continue;
                if ((k++ & 1) == 1) continue;
                int x = rp.r.cx(), z = rp.r.cz();
                g.post(rg.district.display + " Patrol", "patrol", rg.def.faction, x, G, z);
            }
            for (RoadPlan rp : plan.roads) if (rp.district == rg.district && rp.kind.equals("spine")) g.post(rg.district.display + " Watch", "patrol", rg.def.faction, rp.r.cx(), G, rp.r.cz());
        }
    }

    // ------------------------------------------------------------------ extraction & insertion
    private void exitsAndInsertions(WildernessGen wild) {
        MapData m = w.map;
        int ring = g.defs.ring;
        // wall gates on a district's outer side: a point on the ring road's inner kerb and the gap cut through the wall beside it
        for (Region rg : plan.regions) {
            if (rg.slot < 0) continue;
            int lu = rg.lenU();
            switch (rg.district) {
                case MARKET -> gateExit(wild, rg, lu / 3, "fringe_breach", "Fringe Breach", ExitType.PERMANENT, "", "", true);
                case REDLINE -> gateExit(wild, rg, lu * 2 / 3, "farm_track", "Farm Track Gate", ExitType.PERMANENT, "", "", false);
                case CIVIC -> {
                    gateExit(wild, rg, lu / 4, "base_checkpoint", "Garrison Checkpoint", ExitType.CONDITIONAL, "rep:military:0|pay:450", "military", false);
                    gateExit(wild, rg, lu * 3 / 4, "cordon_gate", "Emergency Cordon Gate", ExitType.EMERGENCY, "emergency", "military", false);
                }
                default -> { }
            }
        }
        // sites the landmark compounds marked
        siteExit("rail_exit", "rail_freight", "Power Plant Freight Siding", ExitType.TIME_WINDOW, "window:6-22", "");
        siteExit("airfield", "airfield_charter", "Airfield Charter", ExitType.TIME_WINDOW, "window:7-19", "military");
        siteExit("helipad", "helipad", "Headquarters Roof Pad", ExitType.SIGNALED, "item:signal.flare", "");
        siteExit("jail_tunnel", "jail_tunnel", "Blackmoor Escape Tunnel", ExitType.CONDITIONAL, "item:key.crypt", "raiders");
        // the sewers under the arterials: an outfall by the industrial side, a pumping-station tunnel by the centre
        sewerExit(District.INDUSTRIAL, "sewer_outfall", "Sewer Outfall", ExitType.PERMANENT, "");
        sewerExit(District.OLD_QUARTER, "pump_tunnel", "Pumping Station Tunnel", ExitType.CONDITIONAL, "power");
        // the wilderness ways out: the eastern highway and the forester's track from the west wall
        exit("east_road", "Eastern Highway", 690, G, 180, ExitType.PERMANENT, "", "");
        int tz = 420;
        wild.opening(3, tz - 2, tz + 2);
        wild.track(WildernessGen.WALL_LO - 1, tz, 34, tz + 6);
        exit("west_track", "Forester's Track", 40, Math.max(G - 4, surfaceTop(40, tz + 5) + 1), tz + 5, ExitType.PERMANENT, "", "");
        for (MapData.Exit e : m.exits) {
            if (e.y < G - 1 || e.type == ExitType.SIGNALED) continue;
            // a painted pad on whatever the ground is, and a beacon post standing on the ground beside it
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                int x = e.x + dx, z = e.z + dz, top = surfaceTop(x, z);
                if (top >= 0 && Math.abs(top - (e.y - 1)) <= 1 && Math.abs(dx) + Math.abs(dz) >= 3 && Block.ALL[w.get(x, top, z)].shape == 0 && !Block.ALL[w.get(x, top, z)].is(Block.CONTAINER) && w.map.buildingAt(x, z) == null) w.setRaw(x, top, z, Block.HAZARD_STRIPE);
            }
            beacon(e);
        }
        // insertion points: both ends of every district's spine street
        for (Region rg : plan.regions) for (RoadPlan rp : plan.roads) {
            if (rp.district != rg.district || !rp.kind.equals("spine")) continue;
            Rect rr = rp.r;
            if (rp.alongX) { insertion(rg.district.display + " West End", rr.x0() + 6, G, rr.cz()); insertion(rg.district.display + " East End", rr.x1() - 7, G, rr.cz()); }
            else { insertion(rg.district.display + " North End", rr.cx(), G, rr.z0() + 6); insertion(rg.district.display + " South End", rr.cx(), G, rr.z1() - 7); }
        }
        // each district's own list of ways out is what was actually built in it
        for (District d : District.values()) {
            List<String> l = new ArrayList<>();
            for (MapData.Exit e : m.exits) if (e.district == d) l.add(e.id + ":" + e.type);
            d.def.exits = l.toArray(new String[0]);
        }
    }

    /** A gate through the wall on a region's outer side at local u: the exit point stands on the ring road's kerb. */
    private void gateExit(WildernessGen wild, Region rg, int u, String id, String name, ExitType type, String cond, String faction, boolean breach) {
        int ring = g.defs.ring;
        int ex = rg.x(u, -ring + 1), ez = rg.z(u, -ring + 1);
        int side = rg.side(0);
        int along = side == 0 || side == 2 ? ex : ez;
        wild.opening(side, along - 2, along + 2);
        // the strip between the wall and the road is the gate's apron
        for (int k = -2; k <= 2; k++) for (int v = -ring - 2; v < -ring; v++) { int x = rg.x(u + k, v), z = rg.z(u + k, v); g.fill(x, G, z, x, World.H - 1, z, Block.AIR); }
        if (breach) {
            // a wall that came down: its masonry lies outside, on the wilderness side
            int ox = rg.x(u, -ring - 8), oz = rg.z(u, -ring - 8);
            g.rubblePile(ox, oz, 4, 3, Block.RUBBLE);
        } else {
            for (int k : new int[]{-3, 3}) { int x = rg.x(u + k, -ring - 1), z = rg.z(u + k, -ring - 1); if (g.air(x, G, z)) g.set(x, G, z, Block.BARRIER); }
            if (!faction.isEmpty()) g.post(name, "checkpoint", faction, rg.x(u + 4, -ring - 1), G, rg.z(u + 4, -ring - 1));
        }
        g.landmark(name, breach ? "breach" : "gate", ex, G, ez);
        exit(id, name, ex, G, ez, type, cond, faction);
    }

    private void siteExit(String site, String id, String name, ExitType type, String cond, String faction) {
        MapData.Point p = w.map.site(site);
        if (p == null) { report.note("no site '" + site + "' was built: exit " + id + " omitted"); return; }
        exit(id, name, p.x, p.y, p.z, type, cond, faction);
    }

    /** An exit on the walkway of the sewer under the arterial nearest a district. */
    private void sewerExit(District d, String id, String name, ExitType type, String cond) {
        Region rg = plan.region(d);
        RoadPlan best = null;
        int bd = Integer.MAX_VALUE;
        for (RoadPlan rp : plan.roads) {
            if (rp.style != Style.AVENUE) continue;
            int dx = Math.max(rp.r.x0() - rg.rect.cx(), rg.rect.cx() - rp.r.x1()), dz = Math.max(rp.r.z0() - rg.rect.cz(), rg.rect.cz() - rp.r.z1());
            int dd = Math.max(0, dx) + Math.max(0, dz) + (id.hashCode() & 7);
            if (dd < bd) { bd = dd; best = rp; }
        }
        if (best == null) return;
        int along = best.alongX ? Math.max(best.r.x0() + 30, Math.min(best.r.x1() - 30, rg.rect.cx())) : Math.max(best.r.z0() + 30, Math.min(best.r.z1() - 30, rg.rect.cz()));
        while (along % 48 == 20 || along % 48 == 19 || along % 48 == 21) along++;
        int x = best.alongX ? along : best.r.cx() + 2, z = best.alongX ? best.r.cz() + 2 : along;
        MapData.Exit e = new MapData.Exit(id, name, x, 15, z, type, d);
        e.condition = cond;
        g.set(x, 18, z, Block.RED_LIGHT);
        w.map.exits.add(e);
        g.landmark(name, "sewer", x, G, z);
    }

    private void beacon(MapData.Exit e) {
        for (int[] o : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}, {3, 0}, {0, 3}}) {
            int x = e.x + o[0], z = e.z + o[1], top = surfaceTop(x, z);
            if (top < 0 || Math.abs(top - (e.y - 1)) > 1 || streets.isCarriage(x, z) || w.map.buildingAt(x, z) != null) continue;
            if (w.get(x, top + 1, z) != 0 || w.get(x, top + 2, z) != 0 || w.get(x, top + 3, z) != 0 || w.get(x, top + 4, z) != 0) continue;
            for (int y = top + 1; y <= top + 3; y++) w.setRaw(x, y, z, Block.LAMP_POST);
            w.setRaw(x, top + 4, z, Block.RED_LIGHT);
            return;
        }
    }

    private int surfaceTop(int x, int z) {
        for (int y = G + 24; y > 1; y--) { int id = w.get(x, y, z); if (id != 0 && Block.ALL[id].solid()) return y; }
        return -1;
    }

    private void exit(String id, String name, int x, int y, int z, ExitType t, String cond, String faction) {
        MapData.Exit e = new MapData.Exit(id, name, x, y, z, t, w.district(x, z));
        e.condition = cond;
        e.faction = faction;
        w.map.exits.add(e);
    }

    private void insertion(String name, int x, int y, int z) { w.map.insertions.add(new MapData.Point(name, "insertion", x, y, z, w.district(x, z))); }
}
