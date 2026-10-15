package absentialis.world.gen;

import static absentialis.world.gen.Gen.G;

import absentialis.engine.util.Noise;
import absentialis.engine.util.Rng;
import absentialis.world.Block;
import absentialis.world.District;
import absentialis.world.MapData;
import absentialis.world.World;
import absentialis.world.gen.Streets.Rect;
import absentialis.world.gen.arch.Architect;

/**
 * The Wilderness beyond the quarantine wall, and the wall itself (0.5).
 *
 * One height rule ({@link WorldPlan#naturalHeight}) and one definition of "inside the city"
 * ({@link World#outsideDistance(int, int)}) are shared by the terrain pass, the wall, the gap between wall and ring
 * road, the plan and the regression gates - the 0.4 trench along the east and south wall was two stages disagreeing
 * about who owned column 608. Anything built out here (the highway, farmsteads, pylons, the forester's track)
 * stands on ground graded to meet the natural surface over several metres; nothing is cut in as a notch.
 */
public final class WildernessGen {
    private final Gen g;
    private final int[] height = new int[World.W * World.D];
    public static final int HWY_Z0 = 174, HWY_Z1 = 187;
    /** Wall columns: lo and lo+1 on the north and west, hi and hi+1 on the south and east. */
    public static final int WALL_LO = World.CITY_MIN - 4, WALL_HI = World.CITY_MAX + 2;

    public WildernessGen(Gen g) { this.g = g; }

    private int h(int x, int z) { return height[x * World.D + z]; }

    /** Stage 1: every column of the world gets ground - the wilderness its terrain, the rest a marker for the city fill. */
    public void terrain() {
        for (int x = 0; x < World.W; x++) for (int z = 0; z < World.D; z++) {
            int dist = World.outsideDistance(x, z);
            if (dist <= 0) { height[x * World.D + z] = G - 1; continue; }
            column(x, z, WorldPlan.naturalHeight(x, z, g.seed), -1);
        }
        // highway surface on its graded bed
        for (int x = World.CITY_MAX; x < World.W; x++) for (int z = HWY_Z0; z < HWY_Z1; z++) {
            int across = z - HWY_Z0;
            int id = across == 0 || across == HWY_Z1 - HWY_Z0 - 1 ? Block.CURB : (across == 6 && (x / 3) % 3 != 0 ? Block.ROAD_LINE_X : (Rng.hash(x, 3, z, 3) < 0.12f ? Block.ASPHALT_CRACKED : Block.ASPHALT));
            column(x, z, G - 1, id);
        }
    }

    /** Writes one natural column to height hh: rock, subsoil, a surface by drift, standing water in hollows. */
    private void column(int x, int z, int hh, int top) {
        int seed = (int) g.seed;
        height[x * World.D + z] = hh;
        g.set(x, 0, z, Block.BEDROCK);
        g.fill(x, 1, z, x, hh - 4, z, Block.STONE_DARK);
        g.fill(x, Math.max(1, hh - 3), z, x, hh - 1, z, Block.DIRT);
        g.fill(x, hh + 1, z, x, World.H - 1, z, Block.AIR);
        if (top < 0) {
            float desolation = Math.min(1, World.outsideDistance(x, z) / 90f);
            // ground cover changes in broad drifts (fbm), so fields, burnt ground and ash read as places
            float r = Noise.fbm(x / 21f, z / 21f, 3, seed + 31);
            if (hh < G - 6) top = r < 0.5f ? Block.MUD : Block.GRAVEL;
            else if (r < 0.22f + desolation * 0.30f) top = Block.DEAD_GRASS;
            else if (r > 0.86f - desolation * 0.12f) top = Block.ASH;
            else top = Block.GRASS;
        }
        g.set(x, hh, z, top);
        if (hh < G - 5) g.fill(x, hh + 1, z, x, G - 5, z, Block.WATER);
    }

    /**
     * Levels a pad at street height and grades the ground round it back to the natural surface over {@code margin}
     * cells, so a building or a pylon out here never sits in a pit or on a plinth.
     */
    public void level(int x0, int z0, int w, int d, int margin, int top) {
        for (int x = x0 - margin; x < x0 + w + margin; x++) for (int z = z0 - margin; z < z0 + d + margin; z++) {
            if (x < 1 || z < 1 || x >= World.W - 1 || z >= World.D - 1 || World.outsideDistance(x, z) <= 6) continue;
            int dx = Math.max(x0 - x, x - (x0 + w - 1)), dz = Math.max(z0 - z, z - (z0 + d - 1)), k = Math.max(0, Math.max(dx, dz));
            if (k == 0) { column(x, z, G - 1, top); continue; }
            int nat = WorldPlan.naturalHeight(x, z, g.seed);
            float t = WorldPlan.smooth(k / (float) (margin + 1));
            column(x, z, Math.round(G - 1 + (nat - (G - 1)) * t), -1);
        }
    }

    // ------------------------------------------------------------------ the wall
    public void quarantineWall(WorldPlan plan) {
        Rng r = g.rng("wall");
        int lo = WALL_LO, hi = WALL_HI;
        for (int i = lo; i <= hi + 1; i++) {
            wallColumn(i, lo); wallColumn(i, lo + 1);
            wallColumn(i, hi); wallColumn(i, hi + 1);
            wallColumn(lo, i); wallColumn(lo + 1, i);
            wallColumn(hi, i); wallColumn(hi + 1, i);
        }
        // the strip between wall and ring road: every column from the wall to the city edge, on all four sides
        for (int i = lo; i <= hi + 1; i++) for (int k = 0; k < 2; k++) {
            gapGround(i, lo + 2 + k); gapGround(lo + 2 + k, i);
            gapGround(i, World.CITY_MAX + k); gapGround(World.CITY_MAX + k, i);
        }
        // watchtowers are part of the wall: bastions on its inner face, reached by a ladder from the strip
        for (int i = lo + 30; i < hi - 10; i += 64) for (int side = 0; side < 4; side++) {
            if (side == 1 && i > HWY_Z0 - 12 && i < HWY_Z1 + 12) continue;         // the highway gate stands there
            bastion(side, i);
        }
        // the eastern highway gate (open) with its checkpoint, standing clear of the carriageway
        opening(1, HWY_Z0 + 1, HWY_Z1 - 2);
        g.post("East Gate", "checkpoint", "military", World.CITY_MAX - 6, G, (HWY_Z0 + HWY_Z1) / 2);
        for (int z : new int[]{HWY_Z0 - 1, HWY_Z1}) { g.set(World.CITY_MAX, G, z, Block.BARRIER); g.set(World.CITY_MAX + 1, G, z, Block.BARRIER); }
        // radiation warnings outside the wall, on the natural ground
        for (int i = lo + 10; i < hi; i += 40) { sign(i, lo - 5); sign(i, hi + 6); sign(lo - 5, i); sign(hi + 6, i); }
    }

    private void gapGround(int x, int z) {
        if (x < 0 || z < 0 || x >= World.W || z >= World.D || World.outsideDistance(x, z) <= 0) return;
        if (g.get(x, G, z) == Block.CONCRETE || g.get(x, G, z) == Block.CONCRETE_STAINED) return;   // the wall stands here
        column(x, z, G - 1, Block.GRAVEL);
    }

    private void wallColumn(int x, int z) {
        if (x < 0 || z < 0 || x >= World.W || z >= World.D) return;
        g.fill(x, 1, z, x, G - 6, z, Block.STONE_DARK);
        g.fill(x, G - 5, z, x, G + 6, z, Rng.hash(x, 1, z, 3) < 0.1f ? Block.CONCRETE_STAINED : Block.CONCRETE);
        g.fill(x, G + 8, z, x, World.H - 1, z, Block.AIR);
        g.set(x, G + 7, z, Block.BARBED);
        if ((x + z) % 16 == 0) g.set(x, G + 3, z, Block.HAZARD_STRIPE);
        if (Rng.hash(x, 2, z, 3) < 0.02f) g.set(x, G + 2, z, Block.GRAFFITI);
    }

    /**
     * A watchtower built into the wall. side: 0 north, 1 east, 2 south, 3 west; {@code i} is its position along the
     * wall. It occupies the wall's two columns and the first column of the strip inside it, so it stands on made
     * ground whatever the wilderness does outside; the ladder is on the city face.
     */
    private void bastion(int side, int i) {
        int lo = WALL_LO, hi = WALL_HI;
        for (int along = 0; along < 4; along++) for (int depth = 0; depth < 4; depth++) {
            // depth 0,1 = the wall itself, 2 = the strip column against it, 3 = the ladder column
            int n = side == 0 || side == 3 ? lo + depth : hi + 1 - depth;
            int x = side == 0 || side == 2 ? i + along : n, z = side == 0 || side == 2 ? n : i + along;
            if (depth == 3) {
                if (along == 1) for (int y = G; y <= G + 7; y++) g.set(x, y, z, Block.LADDER);
                continue;
            }
            g.fill(x, G, z, x, G + 6, z, Block.CONCRETE_DARK);
            g.set(x, G + 7, z, Block.CONCRETE_FLOOR);
            boolean rim = along == 0 || along == 3 || depth == 0;
            g.set(x, G + 8, z, rim ? ((along + depth) % 2 == 0 ? Block.SANDBAG : Block.CONCRETE) : Block.AIR);
            g.set(x, G + 9, z, (along == 0 || along == 3) && (depth == 0 || depth == 2) ? Block.STEEL_BEAM : Block.AIR);
            g.set(x, G + 10, z, (along == 0 || along == 3) && (depth == 0 || depth == 2) ? Block.STEEL_BEAM : Block.AIR);
            g.set(x, G + 11, z, Block.CORRUGATED);
            if (along == 1 && depth == 1) { g.set(x, G + 10, z, Block.RED_LIGHT); g.post("Wall Tower", "tower", "military", x, G + 8, z); }
        }
    }

    /** Clears a gate through the wall. side as for {@link #bastion}; p0..p1 inclusive along the wall. */
    public void opening(int side, int p0, int p1) {
        int lo = WALL_LO, hi = WALL_HI;
        for (int p = p0; p <= p1; p++) for (int t = 0; t < 2; t++) {
            int n = side == 0 || side == 3 ? lo + t : hi + t;
            int x = side == 0 || side == 2 ? p : n, z = side == 0 || side == 2 ? n : p;
            g.fill(x, G, z, x, World.H - 1, z, Block.AIR);
            g.set(x, G - 1, z, Block.GRAVEL);
        }
    }

    /** A gravel track from a wall gate out into the wilderness, following the ground with its bed smoothed. */
    public void track(int x0, int z0, int x1, int z1) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        float prev = G - 1;
        for (int i = 0; i <= steps; i++) {
            int x = x0 + Math.round((x1 - x0) * i / (float) Math.max(1, steps)), z = z0 + Math.round((z1 - z0) * i / (float) Math.max(1, steps));
            if (World.outsideDistance(x, z) <= 5 || x < 3 || z < 3 || x >= World.W - 3 || z >= World.D - 3) continue;
            int nat = WorldPlan.naturalHeight(x, z, g.seed);
            // never steeper than one block in two along the track
            float want = Math.max(prev - 0.5f, Math.min(prev + 0.5f, nat));
            prev = want;
            int hh = Math.round(want);
            for (int k = -3; k <= 3; k++) {
                int tx = Math.abs(x1 - x0) >= Math.abs(z1 - z0) ? x : x + k, tz = Math.abs(x1 - x0) >= Math.abs(z1 - z0) ? z + k : z;
                if (World.outsideDistance(tx, tz) <= 5) continue;
                int n2 = WorldPlan.naturalHeight(tx, tz, g.seed);
                int ak = Math.abs(k);
                if (ak <= 1) column(tx, tz, Math.max(G - 5, hh), Block.GRAVEL);
                else column(tx, tz, Math.max(G - 5, Math.round(hh + (n2 - hh) * (ak - 1) / 3f)), -1);
            }
        }
    }

    private void sign(int x, int z) {
        if (x < 1 || z < 1 || x >= World.W - 1 || z >= World.D - 1) return;
        int y = h(x, z) + 1;
        if (y < 4 || y > World.H - 5 || g.get(x, y - 1, z) == Block.WATER || g.get(x, y, z) != 0) return;
        g.set(x, y, z, Block.STEEL_BEAM); g.set(x, y + 1, z, Block.STEEL_BEAM); g.set(x, y + 2, z, Block.BIOHAZARD);
    }

    /** Stage 9 for the wilderness: woods in stands, a few farmsteads, pylons by the highway, hunter camps. */
    public void outskirts(Architect architect) {
        Rng r = g.rng("outskirts");
        int seed = (int) g.seed;
        // farmsteads first (they regrade the ground), on sites that are already close to level
        int placed = 0;
        java.util.List<int[]> used = new java.util.ArrayList<>();
        for (int tries = 0; tries < 600 && placed < 6; tries++) {
            int x = r.nextInt(World.W - 60) + 16, z = r.nextInt(World.D - 60) + 16;
            if (World.outsideDistance(x, z) < 22 || World.outsideDistance(x + 30, z + 26) < 22) continue;
            if (z + 30 >= HWY_Z0 - 20 && z <= HWY_Z1 + 20 && x + 34 > World.CITY_MAX) continue;
            if (!flatEnough(x, z, 30, 26)) continue;
            boolean crowded = false;
            for (int[] u : used) if (Math.abs(u[0] - x) + Math.abs(u[1] - z) < 120) crowded = true;
            if (crowded) continue;
            used.add(new int[]{x, z});
            farmstead(architect, x, z, r);
            placed++;
        }
        // pylons marching away from the east gate, each on a graded pad
        for (int x = World.CITY_MAX + 20; x < World.W - 10; x += 34) {
            int z = HWY_Z1 + 30;
            level(x - 2, z - 2, 6, 6, 5, Block.DEAD_GRASS);
            pylon(x, z, 20);
        }
        // woods grow in stands; open ground stays open (no lone trees, no stray boulders)
        for (int x = 2; x < World.W - 2; x++) for (int z = 2; z < World.D - 2; z++) {
            int dist = World.outsideDistance(x, z);
            if (dist < 8) continue;
            if (x >= World.CITY_MAX && z >= HWY_Z0 - 4 && z < HWY_Z1 + 4) continue;
            boolean built = false;
            for (int[] u : used) if (x >= u[0] - 2 && x < u[0] + 32 && z >= u[1] - 2 && z < u[1] + 28) built = true;
            if (built) continue;
            float forest = Noise.fbm(x / 45f, z / 45f, 3, seed + 99);
            float hh = Rng.hash(x, 7, z, seed);
            int y = h(x, z) + 1;
            int under = g.get(x, y - 1, z);
            if (y >= World.H - 12 || under == Block.WATER || g.get(x, y, z) != 0 || under != Block.GRASS && under != Block.DEAD_GRASS && under != Block.ASH) continue;
            boolean dead = dist > 55 || hh < 0.2f;
            if (forest > 0.55f && hh < 0.05f) g.tree(x, y, z, 5 + r.nextInt(4), dead && dist > 40);
            else if (forest > 0.42f && hh > 0.95f && under == Block.GRASS) g.set(x, y, z, Block.TALL_GRASS);
        }
        g.container(660, G, HWY_Z0 + 2, Block.CRATE_MIL, "military");
        g.landmark("Eastern Highway", "highway", 660, G, 180);
        // a handful of hunter camps, each a known place a good walk from the next
        java.util.List<int[]> camps = new java.util.ArrayList<>();
        for (int i = 0; i < 600 && camps.size() < 8; i++) {
            int x = r.nextInt(World.W - 20) + 10, z = r.nextInt(World.D - 20) + 10;
            if (World.outsideDistance(x, z) < 18) continue;
            if (!flatEnough(x - 4, z - 3, 9, 9) || Math.abs(h(x, z) - (G - 1)) > 2) continue;
            boolean crowded = false;
            for (int[] c : camps) if (Math.abs(c[0] - x) + Math.abs(c[1] - z) < 110) crowded = true;
            for (int[] u : used) if (Math.abs(u[0] - x) + Math.abs(u[1] - z) < 60) crowded = true;
            if (crowded) continue;
            camps.add(new int[]{x, z});
            level(x - 3, z - 2, 8, 8, 4, Block.DEAD_GRASS);
            g.landmark("Hunter Camp", "camp", x, G, z);
            for (int k = 0; k < 3; k++) { int yy = G + 2 - k; g.fill(x - k, yy, z, x - k, yy, z + 4, Block.TARP); g.fill(x + k, yy, z, x + k, yy, z + 4, Block.TARP); }
            g.fill(x - 2, G, z + 4, x + 2, G + 2, z + 4, Block.TARP);
            g.set(x, G, z + 4, Block.AIR); g.set(x, G + 1, z + 4, Block.AIR);
            g.set(x - 1, G, z + 1, Block.BED);
            g.container(x + 1, G, z + 1, Block.STASH_CACHE, "cache");
            g.set(x - 2, G, z - 1, Block.EMBER);
        }
    }

    private boolean flatEnough(int x0, int z0, int w, int d) {
        for (int x = x0; x < x0 + w; x += 2) for (int z = z0; z < z0 + d; z += 2) {
            if (x < 6 || z < 6 || x >= World.W - 6 || z >= World.D - 6) return false;
            if (Math.abs(WorldPlan.naturalHeight(x, z, g.seed) - (G - 1)) > 2) return false;
        }
        return true;
    }

    public void pylon(int x, int z, int hgt) {
        for (int y = G; y < G + hgt; y++) {
            int k = (hgt - (y - G)) / 8;
            g.set(x - k, y, z - k, Block.LATTICE); g.set(x + 1 + k, y, z - k, Block.LATTICE);
            g.set(x - k, y, z + 1 + k, Block.LATTICE); g.set(x + 1 + k, y, z + 1 + k, Block.LATTICE);
        }
        g.fill(x - 4, G + hgt, z, x + 5, G + hgt, z + 1, Block.LATTICE);
        for (int dx : new int[]{-4, 5}) g.set(x + dx, G + hgt - 1, z, Block.WIRES);
    }

    /** An abandoned farm in the wilderness: a house and a barn from the same archetypes the Outskirts use, on a graded yard. */
    private void farmstead(Architect architect, int x, int z, Rng r) {
        level(x, z, 30, 26, 6, Block.DEAD_GRASS);
        Rect lot = new Rect(x, z, x + 30, z + 26);
        Site s = new Site(g, architect, lot, 2, District.WILDERNESS, g.defs.of(District.REDLINE), new Rng(Rng.mix(g.seed, x * 31L + z)));
        s.begin();
        MapData.Building house = s.building("farmhouse", 3, 6, 14, 16, 0, 2, "Abandoned Farmhouse", "");
        s.walkToStreet(house, Block.GRAVEL);
        s.building("barn", 17, 5, 28, 20, 0, 1, "Abandoned Barn", "");
        s.enclose(0, 0, s.A - 1, s.B - 1, Block.WOOD_DARK, 1, 0, new int[]{s.local(house.entryX, house.entryZ)[0], 0, s.local(house.entryX, house.entryZ)[0], 0}, new int[]{15, 0, 16, 0});
        s.crate(15, 12, Block.CRATE, "farm");
        s.prop(15, 18, Block.HAY); s.prop(15, 19, Block.HAY);
        s.end();
        g.landmark("Abandoned Farmstead", "farm", x + 15, G, z + 13);
    }
}
