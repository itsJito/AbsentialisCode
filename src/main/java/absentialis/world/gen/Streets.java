package absentialis.world.gen;

import static absentialis.world.gen.Gen.G;

import absentialis.engine.util.Rng;
import absentialis.world.Block;
import absentialis.world.District;
import absentialis.world.MapData;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.List;

/**
 * Street network and civil infrastructure: carriageways with markings, sidewalks and curbs,
 * street furniture (lamps, wrecks, bins, barricades), sewer tunnels with manhole shafts, the
 * canal with embankments and bridges, and the metro line beneath the Civic Core.
 */
public final class Streets {
    public enum Style { AVENUE, STREET, LANE, INDUSTRIAL }

    public record Rect(int x0, int z0, int x1, int z1) { // half-open [x0,x1) x [z0,z1)
        public int w() { return x1 - x0; }
        public int d() { return z1 - z0; }
        public int cx() { return (x0 + x1) / 2; }
        public int cz() { return (z0 + z1) / 2; }
        public boolean contains(int x, int z) { return x >= x0 && x < x1 && z >= z0 && z < z1; }
        public Rect inset(int k) { return new Rect(x0 + k, z0 + k, x1 - k, z1 - k); }
    }

    public record Road(Rect r, boolean alongX, Style style) {}

    private final Gen g;
    public final List<Road> roads = new ArrayList<>();
    public final byte[] roadMask = new byte[World.W * World.D]; // 1 carriageway, 2 sidewalk, 3 canal, 4 plaza
    public static final int CANAL_Z0 = 344, CANAL_Z1 = 360;
    /** True only for a world that has the 0.4 canal; the 0.5 plan has none and roads run straight through. */
    public boolean hasCanal;

    public Streets(Gen g) { this.g = g; }

    public boolean isRoad(int x, int z) { return x >= 0 && z >= 0 && x < World.W && z < World.D && roadMask[x * World.D + z] != 0; }
    public boolean isCarriage(int x, int z) { return x >= 0 && z >= 0 && x < World.W && z < World.D && roadMask[x * World.D + z] == 1; }

    // ------------------------------------------------------------------ roads
    public void road(Rect r, boolean alongX, Style style) {
        roads.add(new Road(r, alongX, style));
        int width = alongX ? r.d() : r.w();
        int sw = style == Style.LANE ? 0 : (style == Style.AVENUE ? 3 : 2);
        if (width < 6) sw = 0;
        for (int x = r.x0; x < r.x1; x++) for (int z = r.z0; z < r.z1; z++) {
            if (x < 0 || z < 0 || x >= World.W || z >= World.D) continue;
            if (hasCanal && z >= CANAL_Z0 && z < CANAL_Z1) continue; // bridges are built separately
            int across = alongX ? z - r.z0 : x - r.x0;
            int along = alongX ? x : z;
            int idx = x * World.D + z;
            boolean side = across < sw || across >= width - sw;
            if (roadMask[idx] == 1 && side) continue; // never paint sidewalk over an existing carriageway (intersections)
            int block;
            if (style == Style.LANE) block = Rng.hash(x, 0, z, 5) < 0.08f ? Block.MUD : Block.COBBLE;
            else if (side) {
                block = (across == sw - 1 || across == width - sw) ? Block.CURB : Block.SIDEWALK;
                roadMask[idx] = 2;
            } else {
                roadMask[idx] = 1;
                int mid = width / 2;
                float h = Rng.hash(x, 1, z, 11);
                block = h < 0.06f ? Block.ASPHALT_CRACKED : Block.ASPHALT;
                // the avenues are the ring road: an unbroken double line you can follow to every district
                if (style == Style.AVENUE && across == mid) block = alongX ? Block.ROAD_TRUNK_X : Block.ROAD_TRUNK;
                else if (style != Style.INDUSTRIAL && across == mid && (along / 3) % 3 != 0) block = alongX ? Block.ROAD_LINE_X : Block.ROAD_LINE;
                if (style == Style.AVENUE && (across == sw || across == width - sw - 1)) block = alongX ? Block.ROAD_EDGE_X : Block.ROAD_EDGE;
            }
            if (style == Style.LANE) roadMask[idx] = 1;
            g.fill(x, 1, z, x, G - 2, z, Block.DIRT);
            g.set(x, G - 1, z, block);
            g.fill(x, G, z, x, G + 30, z, Block.AIR);
        }
    }

    /** Crosswalk stripes where two carriageways meet. */
    public void crosswalks() {
        for (Road rd : roads) {
            if (rd.style == Style.LANE || rd.style == Style.INDUSTRIAL) continue;
            Rect r = rd.r;
            // look for perpendicular carriageway just beyond the road ends
            for (int end = 0; end < 2; end++) {
                if (rd.alongX) {
                    int x = end == 0 ? r.x0 : r.x1 - 1;
                    for (int z = r.z0; z < r.z1; z++) {
                        if (!isCarriage(x, z)) continue;
                        for (int k = 1; k <= 3; k++) { int xx = end == 0 ? x + 3 + k : x - 3 - k; if (isCarriage(xx, z) && g.get(xx, G - 1, z) != Block.ROAD_LINE && !Block.trunk(g.get(xx, G - 1, z))) g.set(xx, G - 1, z, Block.CROSSWALK); }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ street furniture
    /**
     * Street dressing by rule, not by dice. Lamps stand on a fixed spacing with a bin at their foot;
     * boulevards carry a regular row of trees; vehicles are parked in bays against the kerb, bumper
     * to bumper and square to the road; avenues get a bus shelter every few blocks. The carriageway
     * itself stays clear - anything blocking a road is an authored roadblock, placed on purpose.
     * Only which bay is occupied, and how weathered a car is, comes from the seed.
     */
    public void furnish(Rng r) {
        for (Road rd : roads) {
            Rect rc = rd.r;
            int len = rd.alongX ? rc.w() : rc.d();
            int width = rd.alongX ? rc.d() : rc.w();
            District d = g.district(rc.cx(), rc.cz());
            if (rd.style == Style.LANE) {
                // back lanes: a bin against the wall every dozen metres, alternating sides
                for (int i = 6; i < len - 3; i += 12) {
                    int across = (i / 12) % 2 == 0 ? 0 : width - 1;
                    int a = rd.alongX ? rc.x0 + i : rc.x0 + across, b = rd.alongX ? rc.z0 + across : rc.z0 + i;
                    if (g.air(a, G, b)) g.container(a, G, b, Block.TRASH, "trash");
                    if (i % 24 == 6) wires(rd, i, r);
                }
                continue;
            }
            if (width < 6) continue;
            int sw = rd.style == Style.AVENUE ? 3 : 2;
            float wreck = d == District.REDLINE ? 0.85f : d == District.CIVIC ? 0.15f : d == District.INDUSTRIAL ? 0.55f : 0.4f;
            float deadTrees = d == District.REDLINE ? 0.3f : d == District.MARKET ? 0.2f : 0.35f;
            // lamps every 18, alternating kerbs, each with a bin beside it
            if (width >= 8) for (int i = 6; i < len - 3; i += 18) {
                lampAndBin(rd, i, 0, rd.alongX ? 0 : 1, rd.alongX ? 1 : 0);
                lampAndBin(rd, i + 9, width - 1, rd.alongX ? 0 : -1, rd.alongX ? -1 : 0);
            }
            // boulevard trees: avenues only, one every nine metres, set back one cell from the kerb
            if (rd.style == Style.AVENUE) for (int i = 2; i < len - 3; i += 9) {
                if (i % 18 == 6 || i % 18 == 15) continue;       // lamp positions
                for (int across : new int[]{1, width - 2}) {
                    int x = rd.alongX ? rc.x0 + i : rc.x0 + across, z = rd.alongX ? rc.z0 + across : rc.z0 + i;
                    if (isSide(x, z) && g.air(x, G, z) && g.air(x, G + 3, z) && d != District.INDUSTRIAL) g.tree(x, G, z, 6, Rng.hash(x, 7, z, 13) < deadTrees);
                }
            }
            // bus shelter every 64 metres on avenues, on the right-hand kerb
            if (rd.style == Style.AVENUE) for (int i = 30; i < len - 12; i += 64) shelter(rd, i, width);
            // parking bays: runs of one to three vehicles against the kerb, alternating sides
            int[] colors = {Block.CAR_RED, Block.CAR_BLUE, Block.CAR_WHITE, Block.CAR_WHITE, Block.CAR_RUST};
            int bay = 0;
            for (int i = 10; i < len - 14; i += 22, bay++) {
                if (r.chance(0.3)) continue;                     // an empty bay
                int lane = bay % 2 == 0 ? sw : width - sw - 2;
                int cars = 1 + r.nextInt(3);
                for (int k = 0; k < cars; k++) {
                    int along = i + k * 5;
                    int x = rd.alongX ? rc.x0 + along : rc.x0 + lane, z = rd.alongX ? rc.z0 + lane : rc.z0 + along;
                    if (clear(x, z, rd.alongX ? 4 : 2, rd.alongX ? 2 : 4)) g.car(x, z, rd.alongX, r.pick(colors), r.chance(wreck));
                }
            }
        }
    }

    /**
     * Ring-road wayfinding: a direction board on the kerb every 48 m of avenue, square to the traffic,
     * plus a node list of the loop itself so the map and the HUD can name it.
     */
    public void trunkSigns() {
        for (Road rd : roads) {
            if (rd.style != Style.AVENUE) continue;
            Rect rc = rd.r;
            int len = rd.alongX ? rc.w() : rc.d(), width = rd.alongX ? rc.d() : rc.w();
            for (int i = 20; i < len - 6; i += 48) for (int side = 0; side < 2; side++) {
                int across = side == 0 ? 1 : width - 2;
                int x = rd.alongX ? rc.x0 + i : rc.x0 + across, z = rd.alongX ? rc.z0 + across : rc.z0 + i;
                if (!isSide(x, z) || !g.air(x, G, z) || !g.air(x, G + 1, z) || !g.air(x, G + 2, z)) continue;
                g.set(x, G, z, Block.LAMP_POST);
                g.set(x, G + 1, z, Block.LAMP_POST);
                g.set(x, G + 2, z, rd.alongX ? Block.ROAD_SIGN_Z : Block.ROAD_SIGN);
            }
        }
    }

    private void lampAndBin(Road rd, int i, int across, int dirX, int dirZ) {
        Rect rc = rd.r;
        int x = rd.alongX ? rc.x0 + i : rc.x0 + across, z = rd.alongX ? rc.z0 + across : rc.z0 + i;
        if (!isSide(x, z) || !g.air(x, G, z)) return;
        g.streetLamp(x, z, dirX, dirZ);
        int bx = rd.alongX ? x + 1 : x, bz = rd.alongX ? z : z + 1;
        if (isSide(bx, bz) && g.air(bx, G, bz) && (i / 9) % 2 == 0) g.container(bx, G, bz, Block.TRASH, "trash");
    }

    /** A bus shelter: bench under a steel canopy with a timetable board, back to the buildings. */
    private void shelter(Road rd, int i, int width) {
        Rect rc = rd.r;
        int across = width - 1;
        for (int k = 0; k < 4; k++) {
            int x = rd.alongX ? rc.x0 + i + k : rc.x0 + across, z = rd.alongX ? rc.z0 + across : rc.z0 + i + k;
            if (!isSide(x, z) || !g.air(x, G, z)) return;
        }
        for (int k = 0; k < 4; k++) {
            int x = rd.alongX ? rc.x0 + i + k : rc.x0 + across, z = rd.alongX ? rc.z0 + across : rc.z0 + i + k;
            if (k == 0 || k == 3) { g.set(x, G, z, Block.STEEL_BEAM); g.set(x, G + 1, z, Block.STEEL_BEAM); g.set(x, G + 2, z, k == 0 ? Block.POSTER : Block.STEEL_BEAM); }
            else g.set(x, G, z, Block.PEW);
            g.set(x, G + 3, z, Block.METAL_PLATE);
            int ox = rd.alongX ? x : x - 1, oz = rd.alongX ? z - 1 : z;
            if (g.air(ox, G + 3, oz)) g.set(ox, G + 3, oz, Block.METAL_PLATE);
        }
    }

    private void wires(Road rd, int i, Rng r) {
        Rect rc = rd.r;
        int y = G + 6 + r.nextInt(4);
        if (rd.alongX) { int x = rc.x0 + i; for (int z = rc.z0; z < rc.z1; z++) g.setIfAir(x, y, z, r.chance(0.5) ? Block.WIRES : Block.LAUNDRY); }
        else { int z = rc.z0 + i; for (int x = rc.x0; x < rc.x1; x++) g.setIfAir(x, y, z, r.chance(0.5) ? Block.WIRES : Block.LAUNDRY); }
    }

    private boolean isSide(int x, int z) { return x >= 0 && z >= 0 && x < World.W && z < World.D && roadMask[x * World.D + z] == 2; }

    private boolean clear(int x, int z, int w, int d) {
        for (int a = x; a < x + w; a++) for (int b = z; b < z + d; b++) if (!isCarriage(a, b) || !g.air(a, G, b)) return false;
        return true;
    }

    // ------------------------------------------------------------------ waypoint graph
    public void buildNodes() {
        MapData m = g.map;
        for (Road rd : roads) {
            Rect r = rd.r;
            int len = rd.alongX ? r.w() : r.d();
            MapData.Node prev = null;
            for (int i = 0; i <= len; i += 16) {
                int a = Math.min(i, len - 1);
                int x = rd.alongX ? r.x0 + a : r.cx(), z = rd.alongX ? r.cz() : r.z0 + a;
                if (hasCanal && z >= CANAL_Z0 && z < CANAL_Z1 && !isBridge(x)) { prev = null; continue; }
                MapData.Node n = m.addNode(x, G, z, g.district(x, z));
                if (prev != null) m.link(prev, n);
                prev = n;
            }
        }
    }

    private final List<int[]> bridgeX = new ArrayList<>();
    public boolean isBridge(int x) { for (int[] b : bridgeX) if (x >= b[0] && x < b[1]) return true; return false; }

    // ------------------------------------------------------------------ underground: sewers
    public void sewerAlong(Rect road, boolean alongX, Rng r) {
        int c = alongX ? road.cz() : road.cx();
        int len0 = alongX ? road.x0 : road.z0, len1 = alongX ? road.x1 : road.z1;
        for (int a = len0; a < len1; a++) {
            for (int k = -3; k <= 3; k++) {
                int x = alongX ? a : c + k, z = alongX ? c + k : a;
                if (hasCanal && z >= CANAL_Z0 - 1 && z <= CANAL_Z1) continue;
                int ak = Math.abs(k);
                if (ak == 3) { g.fill(x, 13, z, x, 19, z, Block.SEWER_BRICK); continue; }
                g.set(x, 13, z, Block.SEWER_BRICK);
                g.set(x, 19, z, Block.SEWER_BRICK);
                if (ak <= 1) { g.set(x, 14, z, Block.SEWAGE); g.fill(x, 15, z, x, 18, z, Block.AIR); }
                else { g.set(x, 14, z, Block.SEWER_BRICK); g.fill(x, 15, z, x, 18, z, Block.AIR); }
            }
            // details
            float h = Rng.hash(a, c, 3, 77);
            int wx = alongX ? a : c + 2, wz = alongX ? c + 2 : a;
            if (hasCanal && wz >= CANAL_Z0 - 1 && wz <= CANAL_Z1) continue;
            if (h < 0.02f) g.set(wx, 18, wz, Block.RED_LIGHT);
            else if (h < 0.06f) g.set(alongX ? a : c - 2, 15, alongX ? c - 2 : a, Block.CRATE);
            else if (h < 0.08f) g.setIfAir(wx, 18, wz, Block.COBWEB);
            else if (h < 0.10f) { g.set(wx, 15, wz, Block.BONES); }
            if ((a % 48) == 20) manhole(alongX ? a : c + 2, alongX ? c + 2 : a);
            if ((a % 96) == 50 && r.chance(0.6)) g.container(alongX ? a : c - 2, 15, alongX ? c - 2 : a, Block.STASH_CACHE, "cache_sewer");
        }
    }

    public void manhole(int x, int z) {
        if (x < 0 || z < 0 || x >= World.W || z >= World.D) return;
        if (hasCanal && z >= CANAL_Z0 - 1 && z <= CANAL_Z1) return;
        for (int y = 15; y <= G - 1; y++) g.set(x, y, z, Block.LADDER);
        g.set(x, 19, z, Block.LADDER);
        // concrete shaft lining
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) for (int y = 20; y <= G - 2; y++) if (g.get(x + d[0], y, z + d[1]) == Block.DIRT || g.get(x + d[0], y, z + d[1]) == 0) g.set(x + d[0], y, z + d[1], Block.CONCRETE_DARK);
        g.landmark("Manhole", "manhole", x, G, z);
    }

    // ------------------------------------------------------------------ canal & bridges
    public void canal(Rng r) {
        for (int x = World.CITY_MIN - 40; x < World.CITY_MAX + 40; x++) {
            for (int z = CANAL_Z0; z < CANAL_Z1; z++) {
                int dz = z - CANAL_Z0;
                g.fill(x, 1, z, x, 11, z, Block.STONE_DARK);
                g.fill(x, 12, z, x, G + 30, z, Block.AIR);
                if (dz == 0 || dz == 15) { g.fill(x, 12, z, x, G - 1, z, Block.STONE_BLOCK); continue; }
                if (dz == 1 || dz == 2 || dz == 13 || dz == 14) { g.fill(x, 12, z, x, 18, z, Block.STONE_BLOCK); g.set(x, 19, z, Block.COBBLE); continue; }
                g.set(x, 12, z, Block.MUD);
                g.fill(x, 13, z, x, 18, z, Block.WATER);
                roadMask[x * World.D + z] = 3;
            }
            g.w.districtMap[x * World.D + CANAL_Z0] = g.w.districtMap[x * World.D + CANAL_Z0];
            // rusted rail on top of embankment walls
            if (x % 2 == 0) { g.set(x, G, CANAL_Z0, Block.IRON_FENCE); g.set(x, G, CANAL_Z1 - 1, Block.IRON_FENCE); }
            // stairs down to the walkways every 40 blocks
            if (x % 40 == 12) {
                for (int k = 0; k < 4; k++) {
                    // 1.0: the top stair is in the street's own surface, so the flight starts flush with the pavement (it used to
                    // start a block down: a step nobody could walk back up)
                    g.fill(x + k, G - k, CANAL_Z0, x + k, G + 2, CANAL_Z0, Block.AIR);
                    g.set(x + k, G - 1 - k, CANAL_Z0, Block.STAIRS_CONCRETE);
                    g.fill(x + k, G - k, CANAL_Z1 - 1, x + k, G + 2, CANAL_Z1 - 1, Block.AIR);
                    g.set(x + k, G - 1 - k, CANAL_Z1 - 1, Block.STAIRS_CONCRETE);
                }
                g.fill(x + 4, 20, CANAL_Z0, x + 5, 22, CANAL_Z0, Block.AIR); g.set(x + 4, 19, CANAL_Z0, Block.COBBLE); g.set(x + 5, 19, CANAL_Z0, Block.COBBLE);
                g.fill(x + 4, 20, CANAL_Z1 - 1, x + 5, 22, CANAL_Z1 - 1, Block.AIR); g.set(x + 4, 19, CANAL_Z1 - 1, Block.COBBLE); g.set(x + 5, 19, CANAL_Z1 - 1, Block.COBBLE);
            }
        }
    }

    /** Road bridge across the canal; destroyed=true leaves a collapsed span with a gap. */
    public void bridge(int x0, int x1, boolean destroyed, boolean checkpoint, Rng r) {
        bridgeX.add(new int[]{x0, x1});
        for (int x = x0; x < x1; x++) for (int z = CANAL_Z0 - 1; z <= CANAL_Z1; z++) {
            boolean edge = x == x0 || x == x1 - 1;
            if (destroyed && z > CANAL_Z0 + 5 && z < CANAL_Z1 - 5) {
                if (Rng.hash(x, 1, z, 3) < 0.35f) g.set(x, 18, z, Block.RUBBLE);
                continue;
            }
            g.set(x, G - 1, z, edge ? Block.CONCRETE : (x == (x0 + x1) / 2 ? (x1 - x0 >= 12 ? Block.ROAD_TRUNK : Block.ROAD_LINE) : Block.ASPHALT));
            g.set(x, G - 2, z, Block.CONCRETE_DARK);
            g.fill(x, G, z, x, G + 20, z, Block.AIR);
            if (edge) g.set(x, G, z, Block.IRON_FENCE);
            roadMask[x * World.D + z] = 1;
        }
        // piers
        for (int z : new int[]{CANAL_Z0 + 4, CANAL_Z1 - 5}) {
            g.fill(x0 + 1, 12, z, x0 + 2, G - 3, z, Block.CONCRETE_DARK);
            g.fill(x1 - 3, 12, z, x1 - 2, G - 3, z, Block.CONCRETE_DARK);
        }
        if (destroyed) {
            // broken edges, dangling slabs
            for (int x = x0; x < x1; x++) { g.set(x, G - 2, CANAL_Z0 + 6, Block.STEEL_BEAM); g.set(x, G - 3, CANAL_Z0 + 7, r.chance(0.5) ? Block.STEEL_BEAM : Block.AIR); }
            g.landmark("Collapsed Bridge", "bridge", (x0 + x1) / 2, G, (CANAL_Z0 + CANAL_Z1) / 2);
        }
        if (checkpoint) {
            int cz0 = CANAL_Z0 - 4, cz1 = CANAL_Z1 + 3;
            for (int z : new int[]{cz0, cz1}) {
                for (int x = x0; x < x1; x++) if ((x - x0) % 4 != 1) { g.set(x, G, z, Block.SANDBAG); if ((x - x0) % 4 == 0) g.set(x, G + 1, z, Block.SANDBAG); }
                g.set(x0 + 1, G, z, Block.BARRIER);
            }
            // watch tower
            int tx = x1 + 1, tz = CANAL_Z0 - 6;
            for (int y = G; y < G + 7; y++) { g.set(tx, y, tz, Block.STEEL_BEAM); g.set(tx + 3, y, tz, Block.STEEL_BEAM); g.set(tx, y, tz + 3, Block.STEEL_BEAM); g.set(tx + 3, y, tz + 3, Block.STEEL_BEAM); g.set(tx + 1, y, tz, Block.LADDER); }
            g.fill(tx, G + 7, tz, tx + 3, G + 7, tz + 3, Block.GRATE);
            g.fill(tx, G + 8, tz, tx + 3, G + 8, tz + 3, Block.SANDBAG);
            g.fill(tx + 1, G + 8, tz + 1, tx + 2, G + 8, tz + 2, Block.AIR);
            g.set(tx + 1, G + 7, tz, Block.LADDER); g.set(tx + 1, G + 8, tz, Block.AIR);
            g.fill(tx, G + 10, tz, tx + 3, G + 10, tz + 3, Block.CANVAS_OLIVE);
            g.lamp(tx + 2, G + 9, tz + 2);
            g.post("Bridge Checkpoint", "checkpoint", "military", (x0 + x1) / 2, G, cz0 - 2);
            g.post("Bridge Tower", "tower", "military", tx + 1, G + 8, tz + 1);
        }
    }

    // ------------------------------------------------------------------ metro
    public void metro(int xa, int xb, int z, Rng r) {
        // tunnel: floor y=4, air 5..9, ceiling 10
        for (int x = xa; x <= xb; x++) for (int k = -3; k <= 3; k++) {
            int zz = z + k;
            if (Math.abs(k) == 3) { g.fill(x, 4, zz, x, 10, zz, Block.CONCRETE_DARK); continue; }
            g.set(x, 3, zz, Block.CONCRETE_DARK);
            g.set(x, 4, zz, (k == -1 || k == 1) ? Block.RAIL : Block.RAIL_SLEEPER);
            g.fill(x, 5, zz, x, 9, zz, Block.AIR);
            g.set(x, 10, zz, Block.CONCRETE_DARK);
            if (x % 24 == 0 && k == 0) g.set(x, 9, zz, Block.RED_LIGHT);
        }
        // abandoned train
        int tx = xa + (xb - xa) / 3;
        for (int x = tx; x < tx + 30; x++) for (int k = -2; k <= 2; k++) {
            int zz = z + k;
            boolean shell = Math.abs(k) == 2 || x == tx || x == tx + 29;
            g.fill(x, 5, zz, x, 8, zz, shell ? Block.CONTAINER_BLUE : Block.AIR);
            g.set(x, 5, zz, Block.TILE_GRAY);
            if (shell && Math.abs(k) == 2 && (x - tx) % 5 == 2) g.set(x, 7, zz, Block.GLASS_CRACKED);
            if (shell && Math.abs(k) == 2 && (x - tx) % 10 == 5) { g.set(x, 6, zz, Block.AIR); g.set(x, 7, zz, Block.AIR); }
        }
        for (int x = tx + 3; x < tx + 27; x += 6) g.container(x, 6, z - 1, r.chance(0.5) ? Block.BODYBAG : Block.CRATE, null);
    }

    /** Station hall + stair entrance from the street at (ex, ez) descending along +x. */
    public void metroStation(int x0, int x1, int z, int ex, int ez, Rng r) {
        for (int x = x0; x <= x1; x++) for (int k = -9; k <= 9; k++) {
            int zz = z + k;
            if (Math.abs(k) == 9) { g.fill(x, 3, zz, x, 12, zz, Block.TILE_WHITE); continue; }
            g.fill(x, 5, zz, x, 11, zz, Block.AIR);
            g.set(x, 12, zz, Block.CONCRETE_PANEL);
            if (Math.abs(k) >= 4) { g.fill(x, 3, zz, x, 4, zz, Block.CONCRETE); g.set(x, 5, zz, Block.TILE_GRAY); g.fill(x, 6, zz, x, 11, zz, Block.AIR); }
            if (Math.abs(k) == 4) g.set(x, 5, zz, Block.HAZARD_STRIPE);
            if ((x - x0) % 8 == 0 && Math.abs(k) == 6) g.fill(x, 6, zz, x, 11, zz, Block.CONCRETE_PANEL);
            if ((x - x0) % 8 == 4 && Math.abs(k) == 7) g.set(x, 11, zz, Block.LAMP);
        }
        for (int x = x0 + 4; x <= x1 - 4; x += 8) for (int k : new int[]{-7, 7}) g.set(x, 6, z + k, Block.PEW);
        g.container(x0 + 6, 6, z - 8, Block.TRASH, "trash");
        g.container(x1 - 6, 6, z + 8, Block.LOCKER, "office");
        g.container(x0 + 10, 6, z + 8, Block.CRATE_MIL, "military");
        g.lamp(x0 + 2, 11, z);
        // stair down from street: 3 wide, descends from G-1 to 5 along +x, then corridor to hall
        int steps = (G - 1) - 5;
        for (int s = 0; s <= steps; s++) {
            int x = ex + s, top = G - 1 - s;
            for (int k = -1; k <= 1; k++) {
                int zz = ez + k;
                g.fill(x, top + 1, zz, x, Math.min(G + 2, top + 4), zz, Block.AIR);
                g.set(x, top, zz, Block.STAIRS_CONCRETE);
                if (top + 4 < G - 1) g.set(x, top + 4, zz, Block.CONCRETE_PANEL);
            }
            g.fill(x, top, ez - 2, x, Math.min(G - 1, top + 4), ez - 2, Block.TILE_WHITE);
            g.fill(x, top, ez + 2, x, Math.min(G - 1, top + 4), ez + 2, Block.TILE_WHITE);
            if (s % 6 == 3) g.set(x, Math.min(G - 2, top + 3), ez + 1, Block.LAMP);
        }
        // corridor from stair bottom to station
        int bx = ex + steps;
        int zA = Math.min(ez, z), zB = Math.max(ez, z);
        for (int zz = zA - 1; zz <= zB + 1; zz++) for (int x = bx - 1; x <= bx + 1; x++) {
            g.set(x, 5, zz, Block.TILE_GRAY);
            g.fill(x, 6, zz, x, 9, zz, Block.AIR);
            g.set(x, 10, zz, Block.CONCRETE_PANEL);
        }
        for (int x = bx; x <= Math.max(bx, x0); x++) for (int k = -1; k <= 1; k++) { g.set(x, 5, z + k, Block.TILE_GRAY); g.fill(x, 6, z + k, x, 9, z + k, Block.AIR); }
        // entrance canopy at street
        for (int x = ex - 1; x <= ex + 4; x++) { g.set(x, G + 3, ez - 2, Block.METAL_PLATE); g.set(x, G + 3, ez + 2, Block.METAL_PLATE); for (int k = -2; k <= 2; k++) g.set(x, G + 4, ez + k, Block.METAL_PLATE); }
        g.set(ex - 1, G + 2, ez, Block.NEON_BLUE);
        g.landmark("Metro Central", "metro", ex, G, ez);
    }
}
