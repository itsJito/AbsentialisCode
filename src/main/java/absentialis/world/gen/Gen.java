package absentialis.world.gen;

import absentialis.engine.util.Rng;
import absentialis.world.Block;
import absentialis.world.District;
import absentialis.world.MapData;
import absentialis.world.World;

/** Shared generation context and voxel construction helpers. */
public final class Gen {
    public final World w;
    public final MapData map;
    public final Rng rng;
    public final long seed;
    /** District and building data (content/districts.json, content/buildings.json). */
    public final GenDefs defs = GenDefs.get();
    public static final int G = World.GROUND;        // feet level on streets; street surface block is G-1
    /** Storey height, slab to slab. 0.7: five - a floor and four clear blocks under the next (it was four, which left three: the brief asks for no ceiling lower than four). */
    public static final int FLOOR_H = 5;

    public Gen(World w, long seed) {
        this.w = w; this.map = w.map; this.seed = seed; this.rng = new Rng(seed);
    }

    public Rng rng(String salt) { return new Rng(Rng.mix(seed, salt.hashCode())); }
    public Rng rng(int a, int b) { return new Rng(Rng.mix(seed, a * 92821L + b * 7L)); }

    // ------------------------------------------------------------------ district layout
    /**
     * District plan. The power plant sits at the centre of the city on the north bank of the canal
     * and belongs to the Industrial district, which wraps round to the rail yard and the terminal;
     * the Government quarter faces it across the water. Downtown is north-west, the Residential
     * microdistricts fill the west, the Untamed blocks the south-east.
     */
    public static District planDistrict(int x, int z) {
        if (x < World.CITY_MIN || z < World.CITY_MIN || x >= World.CITY_MAX || z >= World.CITY_MAX) return District.WILDERNESS;
        if (x >= 256 && x < 448 && z >= 256 && z < 352) return District.INDUSTRIAL;
        if (x >= 256 && x < 448 && z >= 352 && z < 448) return District.CIVIC;
        if (z < 256 && x < 352) return District.OLD_QUARTER;
        if (x >= 352 && z < 344) return District.INDUSTRIAL;
        if (x < 256) return District.MARKET;
        return District.REDLINE;
    }

    public District district(int x, int z) { return w.district(x, z); }

    // ------------------------------------------------------------------ voxel helpers
    /**
     * The lot being built. While it is set, every write outside it is refused and counted: a structure cannot
     * reach into a road, an alley or its neighbour whatever its generator does (roofs, rubble, damage, props).
     */
    private int clipX0, clipZ0, clipX1 = -1, clipZ1;
    public int clipped;
    public void clip(Streets.Rect r) { if (r == null) { clipX1 = -1; return; } clipX0 = r.x0(); clipZ0 = r.z0(); clipX1 = r.x1(); clipZ1 = r.z1(); }
    public boolean inClip(int x, int z) { return clipX1 < 0 || x >= clipX0 && x < clipX1 && z >= clipZ0 && z < clipZ1; }

    public void set(int x, int y, int z, int id) { if (clipX1 >= 0 && !inClip(x, z)) { clipped++; return; } w.setRaw(x, y, z, id); }
    public int get(int x, int y, int z) { return w.get(x, y, z); }
    public boolean air(int x, int y, int z) { return w.get(x, y, z) == Block.AIR; }

    public void setIfAir(int x, int y, int z, int id) { if (World.inBounds(x, y, z) && w.get(x, y, z) == 0) set(x, y, z, id); }

    /** Fill inclusive box. */
    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, int id) {
        int ax = Math.min(x0, x1), bx = Math.max(x0, x1), ay = Math.min(y0, y1), by = Math.max(y0, y1), az = Math.min(z0, z1), bz = Math.max(z0, z1);
        ax = Math.max(0, ax); az = Math.max(0, az); ay = Math.max(0, ay);
        bx = Math.min(World.W - 1, bx); bz = Math.min(World.D - 1, bz); by = Math.min(World.H - 1, by);
        if (clipX1 >= 0) {
            if (ax < clipX0 || az < clipZ0 || bx >= clipX1 || bz >= clipZ1) clipped++;
            ax = Math.max(ax, clipX0); az = Math.max(az, clipZ0); bx = Math.min(bx, clipX1 - 1); bz = Math.min(bz, clipZ1 - 1);
        }
        for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) {
            int base = World.idx(x, 0, z);
            for (int y = ay; y <= by; y++) w.blocks[base + y] = (byte) id;
        }
    }

    /** Replace only blocks of id 'from' (or any non-air if from<0) inside the box. */
    public void replace(int x0, int y0, int z0, int x1, int y1, int z1, int from, int to) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    int c = w.get(x, y, z);
                    if ((from < 0 && c != 0) || c == from) set(x, y, z, to);
                }
    }

    /** Hollow walls of a box (no floor/roof). */
    public void walls(int x0, int y0, int z0, int x1, int y1, int z1, int id) {
        fill(x0, y0, z0, x1, y1, z0, id);
        fill(x0, y0, z1, x1, y1, z1, id);
        fill(x0, y0, z0, x0, y1, z1, id);
        fill(x1, y0, z0, x1, y1, z1, id);
    }

    /** Sphere of id (air for damage carving), optionally sparing ids in 'keep'. */
    public void sphere(float cx, float cy, float cz, float r, int id, boolean ragged) {
        for (int x = (int) (cx - r - 1); x <= cx + r + 1; x++) for (int y = (int) (cy - r - 1); y <= cy + r + 1; y++) for (int z = (int) (cz - r - 1); z <= cz + r + 1; z++) {
            float d = (float) Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz));
            float rr = ragged ? r * (0.75f + 0.5f * Rng.hash(x, y, z, (int) seed)) : r;
            if (d <= rr && y > 1) set(x, y, z, id);
        }
    }

    /** Pile of rubble with a rough cone shape. */
    public void rubblePile(int cx, int cz, int r, int h, int id) {
        for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++) {
            float d = (float) Math.hypot(x - cx, z - cz) / (r + 0.5f);
            if (d > 1) continue;
            int top = (int) ((1 - d) * h + Rng.hash(x, 0, z, (int) seed) * 1.5f);
            int base = groundY(x, z);
            for (int y = base; y < base + top; y++) if (air(x, y, z)) set(x, y, z, Rng.hash(x, y, z, 7) < 0.25f ? Block.RUBBLE_BRICK : id);
        }
    }

    /** First air y above the highest solid at street level region (scans down from y0). */
    public int groundY(int x, int z) {
        for (int y = Math.min(World.H - 2, G + 40); y > 0; y--) {
            int id = w.get(x, y - 1, z);
            if (id != 0 && Block.ALL[id].solid()) {
                if (y <= G + 6 || true) return y;
            }
        }
        return G;
    }

    /** Lowest air at or above G scanning up from street level (inside lots). */
    public int surfaceY(int x, int z) {
        int y = G;
        while (y < World.H - 1 && w.get(x, y, z) != 0 && Block.ALL[w.get(x, y, z)].solid()) y++;
        return y;
    }

    // ------------------------------------------------------------------ gameplay markers
    public void container(int x, int y, int z, int blockId, String table) {
        if (!inClip(x, z)) { clipped++; return; }
        set(x, y, z, blockId);
        String t = table != null ? table : Block.ALL[blockId].containerType;
        if (t == null) t = "crate";
        map.loot.add(new MapData.LootSpot(x, y, z, t, district(x, z)));
    }

    public MapData.LootSpot namedCache(int x, int y, int z, String table, String tag) {
        set(x, y, z, Block.STASH_CACHE);
        MapData.LootSpot l = new MapData.LootSpot(x, y, z, table, district(x, z));
        l.named = true;
        l.tag = tag;
        map.loot.add(l);
        return l;
    }

    // ------------------------------------------------------------------ 1.0: props
    /** Props placed and refused (a cell was taken, or off the lot), for the generation report. */
    public int propsPlaced, propsRefused;

    /**
     * Stands a prop on the world-space box of cells (x0, z0)-(x1, z1) at floor cell y, turned so that its back is toward
     * rot (0 -z, 1 +x, 2 +z, 3 -x). Something that takes up room needs every cell of its box free (air, or growth);
     * something that hangs on a wall or from a ceiling needs nothing. Returns the record, or null when it could not go there.
     */
    public MapData.Prop prop(String name, int x0, int z0, int x1, int z1, int y, int rot) {
        PropLib.Def d = PropLib.get(name);
        if (d == null) { propsRefused++; return null; }
        int ax = Math.min(x0, x1), bx = Math.max(x0, x1), az = Math.min(z0, z1), bz = Math.max(z0, z1);
        for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) if (!inClip(x, z) || !World.inBounds(x, y, z)) { propsRefused++; return null; }
        if (d.collide != PropLib.NONE) {
            for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) for (int h = 0; h < d.h; h++) { int id = w.get(x, y + h, z); if (id != 0 && id != Block.TALL_GRASS && id != Block.COBWEB) { propsRefused++; return null; } }
            int low = d.metal ? Block.PROP_METAL_LOW : Block.PROP_WOOD_LOW, full = d.metal ? Block.PROP_METAL : Block.PROP_WOOD;
            for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) for (int h = 0; h < d.h; h++)
                set(x, y + h, z, d.collide == PropLib.POST ? Block.PROP_POST : d.collide == PropLib.LOW && h == d.h - 1 ? low : full);
        }
        if (d.glow) { int gy = y + (d.collide == PropLib.NONE ? 0 : d.h); int gx = (ax + bx) / 2, gz = (az + bz) / 2; if (World.inBounds(gx, gy, gz) && w.get(gx, gy, gz) == 0) set(gx, gy, gz, Block.PROP_GLOW); }
        MapData.Prop p = new MapData.Prop(name, ax, az, bx, bz, y, rot & 3);
        map.props.add(p);
        propsPlaced++;
        return p;
    }

    /** A prop of its own footprint with its anchor cell at (x, y, z): the footprint is laid out from there as the turn says. */
    public MapData.Prop prop(String name, int x, int y, int z, int rot) {
        PropLib.Def d = PropLib.get(name);
        if (d == null) { propsRefused++; return null; }
        int w1 = d.w - 1, d1 = d.d - 1;
        return switch (rot & 3) {
            case 0 -> prop(name, x, z, x + w1, z + d1, y, rot);
            case 1 -> prop(name, x - d1, z, x, z + w1, y, rot);
            case 2 -> prop(name, x - w1, z - d1, x, z, y, rot);
            default -> prop(name, x, z - w1, x + d1, z, y, rot);
        };
    }

    /**
     * After everything else has been built and validated: a prop whose stand-in cells were cleared by a repair (a doorway
     * opened through it, a floating block removed) is taken out whole, and a stand-in that has lost its prop is cleared.
     */
    public int auditProps() {
        replaceFixtureBlocks();
        int removed = 0;
        java.util.Set<Long> held = new java.util.HashSet<>();
        for (java.util.Iterator<MapData.Prop> it = map.props.iterator(); it.hasNext(); ) {
            MapData.Prop p = it.next();
            PropLib.Def d = PropLib.get(p.model);
            if (d == null) { it.remove(); removed++; continue; }
            // 1.2: what hangs on a wall hangs on a wall - not on a door, not across a doorway, not in the air - and what hangs
            // from a ceiling has a ceiling over it. (A picture on a door, a notice board in a doorway and a lamp under the sky
            // were all things the furnishing passes could do, and did: a place is furnished before its last doors are cut.)
            if (d.collide == PropLib.NONE && (d.mount == PropLib.WALL && !wallHolds(p) || d.mount == PropLib.CEILING && !ceilingHolds(p, d))) {
                if (d.mount == PropLib.WALL) propsOffWalls++; else propsOffCeilings++;
                if (d.glow) { int gx = (p.x0 + p.x1) / 2, gz = (p.z0 + p.z1) / 2; if (w.get(gx, p.y, gz) == Block.PROP_GLOW) w.setRaw(gx, p.y, gz, Block.AIR); }
                it.remove(); removed++;
                continue;
            }
            if (d.collide == PropLib.NONE) continue;
            boolean whole = true;
            for (int x = p.x0; x <= p.x1 && whole; x++) for (int z = p.z0; z <= p.z1 && whole; z++) for (int h = 0; h < d.h; h++) if (!Block.prop(w.get(x, p.y + h, z))) { whole = false; break; }
            // it must also stand on something
            for (int x = p.x0; x <= p.x1 && whole; x++) for (int z = p.z0; z <= p.z1; z++) if (!w.solid(x, p.y - 1, z)) { whole = false; break; }
            if (!whole) {
                for (int x = p.x0; x <= p.x1; x++) for (int z = p.z0; z <= p.z1; z++) for (int h = 0; h <= d.h; h++) if (Block.prop(w.get(x, p.y + h, z))) w.setRaw(x, p.y + h, z, Block.AIR);
                it.remove(); removed++;
            } else for (int x = p.x0; x <= p.x1; x++) for (int z = p.z0; z <= p.z1; z++) for (int h = 0; h < d.h; h++) held.add(((long) x << 40) | ((long) (p.y + h) << 20) | z);
        }
        return removed;
    }

    /** 1.2: wall and ceiling fittings the audit took down because there was no wall behind them (or a door), or no ceiling over them. */
    public int propsOffWalls, propsOffCeilings;

    /** The wall a fitting hangs on is the cell behind it: something solid that is not a door. And it does not hang in the way through a door. */
    public int fixturesReplaced;

    /**
     * 1.3: the old block-shaped fixtures are stood as modelled props. Every toilet block becomes the porcelain toilet
     * (the mansion's), and a pew standing on its own - a pew is one of a row; alone it read as a broken chair - becomes a
     * wooden chair. Each keeps its back to the wall it stood against (or, in the open, faces the way it was set).
     */
    private void replaceFixtureBlocks() {
        for (MapData.Building b : map.buildings) {
            for (int x = b.x0 - 1; x <= b.x1 + 1; x++) for (int z = b.z0 - 1; z <= b.z1 + 1; z++) for (int y = World.GROUND - 12; y < World.GROUND + 40; y++) {
                if (!World.inBounds(x, y, z)) continue;
                int id = w.get(x, y, z);
                String model;
                if (id == Block.TOILET) model = "toilet_p";
                else if (id == Block.PEW && w.get(x + 1, y, z) != Block.PEW && w.get(x - 1, y, z) != Block.PEW && w.get(x, y, z + 1) != Block.PEW && w.get(x, y, z - 1) != Block.PEW) model = "chair_wood";
                else continue;
                // the side its back goes to: the first solid, door-free wall around it (rot 0 = back to -z, 1 = +x, 2 = +z, 3 = -x)
                int rot = -1;
                int[][] back = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
                for (int k = 0; k < 4 && rot < 0; k++) {
                    int n = w.get(x + back[k][0], y, z + back[k][1]);
                    if (n != 0 && Block.ALL[n].solid() && !Block.ALL[n].is(Block.DOOR) && !Block.prop(n) && Block.ALL[n].shape == 0) rot = k;
                }
                if (rot < 0) rot = Math.floorMod(x * 31 + z * 17, 4);
                w.setRaw(x, y, z, Block.AIR);
                if (prop(model, x, y, z, rot) != null) fixturesReplaced++;
                else w.setRaw(x, y, z, id);
            }
        }
    }

    private boolean wallHolds(MapData.Prop p) {
        int bx = p.rot == 1 ? 1 : p.rot == 3 ? -1 : 0, bz = p.rot == 0 ? -1 : p.rot == 2 ? 1 : 0;
        for (int x = p.x0; x <= p.x1; x++) for (int z = p.z0; z <= p.z1; z++) {
            int here = w.get(x, p.y, z), behind = w.get(x + bx, p.y, z + bz);
            if (here != 0 && Block.ALL[here].is(Block.DOOR)) return false;
            if (behind == 0 || Block.ALL[behind].is(Block.DOOR) || !Block.ALL[behind].solid()) return false;
            // in front of a door, on the line through it: the cell is the doorway's, whatever height the fitting is at
            for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) for (int dy = 0; dy >= -1; dy--) {
                int id = w.get(x + n[0], p.y + dy, z + n[1]);
                if (id == 0 || !Block.ALL[id].is(Block.DOOR)) continue;
                if (!w.solid(x + n[0] * 2, p.y + dy, z + n[1] * 2) || Block.ALL[w.get(x + n[0] * 2, p.y + dy, z + n[1] * 2)].is(Block.DOOR)) return false;
            }
        }
        return true;
    }

    private boolean ceilingHolds(MapData.Prop p, PropLib.Def d) {
        for (int x = p.x0; x <= p.x1; x++) for (int z = p.z0; z <= p.z1; z++) if (w.solid(x, p.y + d.h, z)) return true;
        // 1.3: or it hangs by a chain (stacked chain blocks of its own footprint) that reaches a ceiling
        int top = p.y + d.h;
        for (int guard = 0; guard < 8; guard++) {
            boolean link = false;
            for (MapData.Prop c : map.props) if (c.y == top && c.x0 == p.x0 && c.z0 == p.z0 && "chain_chandelier".equals(c.model)) { link = true; break; }
            if (!link) return false;
            top++;
            for (int x = p.x0; x <= p.x1; x++) for (int z = p.z0; z <= p.z1; z++) if (w.solid(x, top, z)) return true;
        }
        return false;
    }

    /** Power-dependent lamp (switched by district power state at raid start). */
    public void lamp(int x, int y, int z) {
        if (!inClip(x, z)) { clipped++; return; }
        set(x, y, z, Block.LAMP);
        map.lampsOnPower.add(new MapData.Point("lamp", "lamp", x, y, z, district(x, z)));
    }

    public void post(String name, String kind, String faction, int x, int y, int z) {
        MapData.Point p = new MapData.Point(name, kind, x, y, z, district(x, z));
        p.faction = faction;
        map.posts.add(p);
    }

    public void landmark(String name, String kind, int x, int y, int z) {
        map.landmarks.add(new MapData.Point(name, kind, x, y, z, district(x, z)));
    }

    public void site(String name, int x, int y, int z) {
        map.sites.add(new MapData.Point(name, "site", x, y, z, district(x, z)));
    }

    public void document(String docId, int x, int y, int z) {
        map.documents.add(new MapData.Point(docId, "document", x, y, z, district(x, z)));
    }

    public void nest(int x, int y, int z) {
        map.nests.add(new MapData.Point("nest", "nest", x, y, z, district(x, z)));
    }

    // ------------------------------------------------------------------ small props
    public void car(int x, int z, boolean alongX, int color, boolean wrecked) {
        if (true) return; // v0.3: no vehicles anywhere on the map
        int y = G;
        int len = 4, wid = 2;
        int dx = alongX ? len : wid, dz = alongX ? wid : len;
        if (wrecked && rng.chance(0.3)) color = Block.CAR_RUST;
        // body
        fill(x, y, z, x + dx - 1, y, z + dz - 1, color);
        // cabin
        if (alongX) {
            fill(x + 1, y + 1, z, x + 2, y + 1, z + dz - 1, Block.CAR_WINDOW);
            if (!wrecked || rng.chance(0.5)) { set(x + 1, y + 1, z, color); set(x + 1, y + 1, z + dz - 1, color); }
        } else {
            fill(x, y + 1, z + 1, x + dx - 1, y + 1, z + 2, Block.CAR_WINDOW);
            if (!wrecked || rng.chance(0.5)) { set(x, y + 1, z + 1, color); set(x + dx - 1, y + 1, z + 1, color); }
        }
        if (wrecked && rng.chance(0.4)) { // smashed windows
            replace(x, y + 1, z, x + dx - 1, y + 1, z + dz - 1, Block.CAR_WINDOW, Block.AIR);
        }
        if (wrecked && rng.chance(0.15)) set(x + dx / 2, y + (rng.chance(0.5) ? 1 : 0), z + dz / 2, Block.ASH);
    }

    public void truck(int x, int z, boolean alongX, int color) {
        if (true) return; // v0.3: no vehicles anywhere on the map
        int y = G, len = 7, wid = 3;
        int dx = alongX ? len : wid, dz = alongX ? wid : len;
        fill(x, y, z, x + dx - 1, y + 2, z + dz - 1, color);
        if (alongX) { fill(x, y + 1, z, x, y + 1, z + dz - 1, Block.CAR_WINDOW); fill(x + 2, y, z, x + dx - 1, y + 2, z + dz - 1, Block.CANVAS_OLIVE); }
        else { fill(x, y + 1, z, x + dx - 1, y + 1, z, Block.CAR_WINDOW); fill(x, y, z + 2, x + dx - 1, y + 2, z + dz - 1, Block.CANVAS_OLIVE); }
    }

    public void tree(int x, int y, int z, int h, boolean dead) {
        for (int i = 0; i < h; i++) set(x, y + i, z, Block.LOG);
        if (dead) {
            // a bare crown: a few boughs that each grow out of the trunk, twigs that each hang from a bough
            for (int b = 0; b < 3; b++) {
                int dx = rng.range(-1, 1), dz = rng.range(-1, 1), by = y + h - 1 - b;
                if (dx == 0 && dz == 0) dx = 1;
                if (dx != 0 && dz != 0) dz = 0;
                setIfAir(x + dx, by, z + dz, Block.LOG);
                if (rng.chance(0.7)) setIfAir(x + dx * 2, by, z + dz * 2, Block.DEAD_LEAVES);
                if (rng.chance(0.5)) setIfAir(x + dx, by + 1, z + dz, Block.DEAD_LEAVES);
            }
            setIfAir(x, y + h, z, Block.DEAD_LEAVES);
            return;
        }
        canopy(x, y, z, h);
    }

    /** Trees grown, and leaf blocks placed, since generation began (gates). */
    public int treesGrown, leavesPlaced;
    /** Tuning of the procedural foliage: how many boughs, how long, how big a cluster, and how ragged its edge. */
    public static int BOUGHS_MIN = 3, BOUGHS_MAX = 5;
    public static float CLUSTER_MIN = 1.7f, CLUSTER_MAX = 2.7f, RAGGED = 0.7f, GAPS = 0.07f;

    /** Smooth 3D value noise in 0..1 (trilinear over a hashed lattice): what makes a cluster's edge irregular and its colour patchy. */
    static float noise3(float x, float y, float z, int seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        float fx = x - x0, fy = y - y0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy); fz = fz * fz * (3 - 2 * fz);
        float a = 0;
        for (int i = 0; i < 8; i++) {
            int ix = i & 1, iy = (i >> 1) & 1, iz = (i >> 2) & 1;
            a += Rng.hash(x0 + ix, y0 + iy, z0 + iz, seed) * (ix == 1 ? fx : 1 - fx) * (iy == 1 ? fy : 1 - fy) * (iz == 1 ? fz : 1 - fz);
        }
        return a;
    }

    /**
     * 0.7, procedural foliage. A crown is grown, not stamped: boughs leave the upper trunk in their own directions,
     * each ends in a cluster of leaves, and one more cluster caps the trunk; the clusters are ellipsoids of different
     * sizes that overlap, their edges pushed in and out by 3D noise so that no two silhouettes match and there are
     * hollows the sky shows through. When every cluster is in place the leaves are toned - lighter toward the top and
     * wherever nothing is above them, darker underneath and inside, with patches of a yellower green laid across by
     * a slower noise. Everything comes from the world's own dice and the tree's position: the same seed grows the
     * same wood. Leaves are cut-out cubes that write depth (no blending, no sorting); the mesher culls the faces
     * between them and shades the ones that remain.
     */
    private void canopy(int x, int y, int z, int h) {
        treesGrown++;
        int seed = x * 73856093 ^ z * 19349663 ^ y * 83492791;
        int top = y + h - 1;
        java.util.List<float[]> clusters = new java.util.ArrayList<>();
        // the crown over the trunk: the biggest, and never quite centred
        float lean = rng.range(-0.7f, 0.7f), lean2 = rng.range(-0.7f, 0.7f);
        clusters.add(new float[]{x + 0.5f + lean, top + 1.2f, z + 0.5f + lean2, rng.range(CLUSTER_MAX - 0.4f, CLUSTER_MAX + 0.3f), rng.range(1.6f, 2.2f), rng.range(CLUSTER_MAX - 0.5f, CLUSTER_MAX + 0.2f)});
        int boughs = BOUGHS_MIN + rng.nextInt(BOUGHS_MAX - BOUGHS_MIN + 1) + (h > 6 ? 1 : 0);
        float turn = rng.range(0f, 6.283f);
        for (int b = 0; b < boughs; b++) {
            float ang = turn + b * 6.283f / boughs + rng.range(-0.45f, 0.45f), rise = rng.range(0.25f, 0.8f);
            int len = 2 + rng.nextInt(h > 6 ? 3 : 2), from = top - rng.nextInt(Math.max(1, h / 3));
            float bx = x + 0.5f, by = from, bz = z + 0.5f;
            // a bough is wood all the way from the trunk: it grows a block at a time, each one face to face with the last
            int px = x, py = from, pz = z;
            grow:
            for (int s = 1; s <= len; s++) {
                bx += (float) Math.cos(ang); bz += (float) Math.sin(ang); by += rise;
                int cx = (int) Math.floor(bx), cy = Math.round(by), cz = (int) Math.floor(bz);
                while (px != cx || py != cy || pz != cz) {
                    if (px != cx) px += Integer.signum(cx - px); else if (pz != cz) pz += Integer.signum(cz - pz); else py += Integer.signum(cy - py);
                    if (px == x && pz == z) continue;                    // still inside the trunk
                    if (!air(px, py, pz) && get(px, py, pz) != Block.LOG) break grow;
                    setIfAir(px, py, pz, Block.LOG);
                }
            }
            bx = px + 0.5f; by = py; bz = pz + 0.5f;
            float r1 = rng.range(CLUSTER_MIN, CLUSTER_MAX - 0.3f);
            clusters.add(new float[]{bx, by + 0.6f, bz, r1, rng.range(1.2f, 1.8f), r1 * rng.range(0.8f, 1.25f)});
        }
        java.util.List<int[]> placed = new java.util.ArrayList<>();
        for (float[] c : clusters) {
            int rx = (int) Math.ceil(c[3] + 1), ry = (int) Math.ceil(c[4] + 1), rz = (int) Math.ceil(c[5] + 1);
            for (int dx = -rx; dx <= rx; dx++) for (int dy = -ry; dy <= ry; dy++) for (int dz = -rz; dz <= rz; dz++) {
                int wx = (int) Math.floor(c[0]) + dx, wy = Math.round(c[1]) + dy, wz = (int) Math.floor(c[2]) + dz;
                if (wy < y + Math.max(4, h - 3)) continue;                    // nothing hangs to the ground: a body walks under every tree, and a van drives under one
                float ex = (wx + 0.5f - c[0]) / c[3], ey = (wy - c[1]) / c[4] * (dy < 0 ? 1.25f : 1f), ez = (wz + 0.5f - c[2]) / c[5];
                float d = ex * ex + ey * ey + ez * ez;
                // the edge of the cluster moves in and out with the noise; well inside it, a few leaves are simply missing
                float edge = 1 - RAGGED * 0.5f + RAGGED * noise3(wx * 0.42f, wy * 0.42f, wz * 0.42f, seed);
                if (d > edge) continue;
                if (d > 0.3f && Rng.hash(wx, wy, wz, seed + 5) < GAPS) continue;
                if (!air(wx, wy, wz)) continue;
                setIfAir(wx, wy, wz, Block.LEAVES);
                if (get(wx, wy, wz) == Block.LEAVES) placed.add(new int[]{wx, wy, wz});
            }
        }
        if (placed.isEmpty()) return;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int[] p : placed) { lo = Math.min(lo, p[1]); hi = Math.max(hi, p[1]); }
        for (int[] p : placed) {
            if (get(p[0], p[1], p[2]) != Block.LEAVES) continue;
            boolean open = !Block.leaf(get(p[0], p[1] + 1, p[2])), buried = Block.leaf(get(p[0], p[1] + 1, p[2])) && Block.leaf(get(p[0], p[1] + 2, p[2]));
            float rel = (p[1] - lo) / (float) Math.max(1, hi - lo);
            float patch = noise3(p[0] * 0.19f, p[1] * 0.19f, p[2] * 0.19f, seed + 11), speck = Rng.hash(p[0], p[1], p[2], seed + 23);
            float light = rel * 0.5f + (open ? 0.32f : 0) - (buried ? 0.22f : 0) + (patch - 0.5f) * 0.55f + (speck - 0.5f) * 0.16f;
            int tone = patch > 0.66f && speck < 0.75f ? Block.LEAVES_OLIVE : light > 0.62f ? Block.LEAVES_LIGHT : light < 0.22f ? Block.LEAVES_DARK : Block.LEAVES;
            if (tone != Block.LEAVES) set(p[0], p[1], p[2], tone);
            leavesPlaced++;
        }
    }

    /**
     * A tree planted beside a building may reach over it; it may not reach into it. Any branch or leaf that has ended
     * up inside a room is taken out (one seed in the 0.6 world had a cabin with foliage under its ceiling).
     */
    public int clearFoliageIndoors() {
        int n = 0;
        for (MapData.Building b : map.buildings) for (MapData.Room rm : b.rooms) for (int x = rm.x0; x <= rm.x1; x++) for (int z = rm.z0; z <= rm.z1; z++) for (int yy = rm.y + 1; yy <= rm.y + FLOOR_H - 1; yy++) {
            int id = get(x, yy, z);
            if (Block.leaf(id) || id == Block.LOG) { set(x, yy, z, Block.AIR); n++; }
        }
        // and nothing grows into the head room over a stair, inside or on a roof
        for (MapData.Building b : map.buildings) for (int x = b.x0; x <= b.x1; x++) for (int z = b.z0; z <= b.z1; z++) for (int yy = G; yy <= Math.min(World.H - 4, b.topY + 1); yy++) {
            int id = get(x, yy, z);
            if (id == 0 || !Block.ALL[id].is(Block.STEP)) continue;
            for (int up = 1; up <= 2; up++) { int a = get(x, yy + up, z); if (Block.leaf(a) || a == Block.LOG) { set(x, yy + up, z, Block.AIR); n++; } }
        }
        return n;
    }

    public void streetLamp(int x, int z, int dirX, int dirZ) {
        for (int y = G; y <= G + 5; y++) set(x, y, z, Block.LAMP_POST);
        set(x + dirX, G + 5, z + dirZ, Block.LAMP_ARM);
        lamp(x + dirX * 2, G + 5, z + dirZ * 2);
    }

    public void barrelFire(int x, int z) {
        set(x, G, z, Block.BARREL_FIRE);   // the flames above it are a simulated fire bound to this block at raid start
    }

    public void sandbagWall(int x0, int z0, int x1, int z1, int h) {
        fill(x0, G, z0, x1, G + h - 1, z1, Block.SANDBAG);
    }
}
