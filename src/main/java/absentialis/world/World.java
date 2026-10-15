package absentialis.world;

import java.util.HashMap;
import java.util.Map;

/**
 * Voxel storage for the Zone. Immutable map structure is kept as a pristine snapshot; mutable
 * per-raid state (opened doors, broken glass, destroyed cover) is applied on top and only
 * meaningful changes are persisted (GDD 3.3).
 */
public final class World {
    public static final int W = 704, D = 704, H = 96;
    public static final int CHUNK = 16, CX = W / CHUNK, CZ = D / CHUNK;
    public static final int GROUND = 24;               // street surface y (top block at GROUND-1)
    public static final int CITY_MIN = 96, CITY_MAX = 608;

    public final byte[] blocks = new byte[W * D * H];
    /** Packed light: high nibble = sky light, low nibble = block light. */
    public final byte[] light = new byte[W * D * H];
    public final byte[] districtMap = new byte[W * D];
    public final short[] heightMap = new short[W * D]; // highest opaque block per column (for map + sun)
    private byte[] pristine;

    public final boolean[] dirtyColumns = new boolean[CX * CZ];
    public volatile boolean anyDirty;
    public final Map<Integer, Integer> blockDamage = new HashMap<>();
    public final MapData map = new MapData();
    public long seed;
    /** 0.7: this is the devmap, not the Zone - nobody lives on it, and a raid on it starts empty. */
    public boolean dev;
    public Lighting lighting = new Lighting(this);
    /** Path-finding over this world's voxels and waypoint graph. */
    public final Nav nav = new Nav(this);
    /** Cover points derived from the voxels, rebuilt per chunk when geometry changes. */
    public final CoverMap cover = new CoverMap(this);

    /** What navigation needs to know about a door beyond its voxels: is it shut against whoever comes (locked)? */
    public interface DoorOracle { boolean sealed(int x, int y, int z); }
    /** Set by the raid's door system; null in worlds without one (every shut door can then simply be opened). */
    public DoorOracle doors;

    /**
     * Navigation change log. Every runtime change that can open or close a way through - a wall destroyed, a door
     * forced, a barricade built - bumps the version and is remembered here, so a walker holding an old path can
     * tell whether the change touched its route without anything being recomputed eagerly.
     */
    public int navVersion;
    private final int[] navX = new int[64], navY = new int[64], navZ = new int[64], navV = new int[64];

    public void navChanged(int x, int y, int z) {
        navVersion++;
        int i = navVersion & 63;
        navX[i] = x; navY[i] = y; navZ[i] = z; navV[i] = navVersion;
    }

    /** True when geometry changed since {@code version} within two cells of any cell of a packed path {x, y, z, ...}. */
    public boolean navTouched(int version, int[] path, int from) {
        if (version == navVersion || path == null) return false;
        if (navVersion - version > 63) return true;                  // too much has happened to tell: assume so
        for (int i = 0; i < 64; i++) {
            if (navV[i] <= version) continue;
            for (int k = Math.max(0, from) * 3; k + 2 < path.length; k += 3)
                if (Math.abs(path[k] - navX[i]) <= 2 && Math.abs(path[k + 2] - navZ[i]) <= 2 && Math.abs(path[k + 1] - navY[i]) <= 3) return true;
        }
        return false;
    }

    public static int idx(int x, int y, int z) { return (x * D + z) * H + y; }
    public static boolean inBounds(int x, int y, int z) { return x >= 0 && z >= 0 && y >= 0 && x < W && z < D && y < H; }

    public int get(int x, int y, int z) {
        if (y < 0) return Block.BEDROCK;
        if (!inBounds(x, y, z)) return Block.AIR;
        return blocks[idx(x, y, z)] & 0xFF;
    }

    public Block block(int x, int y, int z) { return Block.ALL[get(x, y, z)]; }

    public boolean solid(int x, int y, int z) {
        if (y < 0) return true;
        if (x < 0 || z < 0 || x >= W || z >= D) return true; // world edge is a hard boundary
        if (y >= H) return false;
        Block b = Block.ALL[blocks[idx(x, y, z)] & 0xFF];
        return b != null && b.solid();
    }

    public boolean opaque(int x, int y, int z) {
        if (!inBounds(x, y, z)) return y < 0;
        Block b = Block.ALL[blocks[idx(x, y, z)] & 0xFF];
        return b != null && b.opaque();
    }

    /** Raw set used by generators (no dirty marking / relight). */
    public void setRaw(int x, int y, int z, int id) {
        if (inBounds(x, y, z)) { blocks[idx(x, y, z)] = (byte) id; if (link != null) link.blockSet(x, y, z, id); }
    }

    /** 1.2: the bridge to the Astratto native core, when this world has been given to it (see {@link NativeLink}); null otherwise. */
    public NativeLink link;

    /** Runtime set: marks meshes dirty and relights locally. */
    public void set(int x, int y, int z, int id) {
        if (!inBounds(x, y, z)) return;
        int i = idx(x, y, z);
        int old = blocks[i] & 0xFF;
        if (old == id) return;
        blocks[i] = (byte) id;
        if (link != null) link.blockSet(x, y, z, id);
        blockDamage.remove(i);
        // the mesh only changes when something drawn by the mesher changed (door leaves are drawn as entities)
        if (!(Block.ALL[old].is(Block.DOOR) || old == 0) || !(Block.ALL[id].is(Block.DOOR) || id == 0)) markDirty(x, z);
        cover.invalidate(x, z);
        if (Block.ALL[old].solid() != Block.ALL[id].solid() || Block.ALL[old].is(Block.DOOR) != Block.ALL[id].is(Block.DOOR)) navChanged(x, y, z);
        if (Block.ALL[old].light != Block.ALL[id].light || Block.ALL[old].opaque() != Block.ALL[id].opaque()
                || Block.ALL[old].is(Block.LIGHT_PASS) != Block.ALL[id].is(Block.LIGHT_PASS)) {
            lighting.relightAround(x, y, z);
        }
        updateHeight(x, z);
    }

    public void markDirty(int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        mark(cx, cz);
        if ((x & 15) == 0) mark(cx - 1, cz);
        if ((x & 15) == 15) mark(cx + 1, cz);
        if ((z & 15) == 0) mark(cx, cz - 1);
        if ((z & 15) == 15) mark(cx, cz + 1);
    }

    public void markRegionDirty(int x0, int z0, int x1, int z1) {
        for (int cx = Math.max(0, (x0 >> 4) - 1); cx <= Math.min(CX - 1, (x1 >> 4) + 1); cx++)
            for (int cz = Math.max(0, (z0 >> 4) - 1); cz <= Math.min(CZ - 1, (z1 >> 4) + 1); cz++) mark(cx, cz);
    }

    /** 1.0: a counter per chunk column that moves whenever the column changes - what caches built from the world (foliage, decals) are checked against. */
    public final int[] columnVersion = new int[CX * CZ];
    public int version(int cx, int cz) { return cx < 0 || cz < 0 || cx >= CX || cz >= CZ ? 0 : columnVersion[cx * CZ + cz]; }

    private void mark(int cx, int cz) {
        if (cx < 0 || cz < 0 || cx >= CX || cz >= CZ) return;
        columnVersion[cx * CZ + cz]++;
        dirtyColumns[cx * CZ + cz] = true;
        anyDirty = true;
    }

    public int sky(int x, int y, int z) {
        if (y >= H) return 15;
        if (!inBounds(x, y, z)) return y < 0 ? 0 : 15;
        return (light[idx(x, y, z)] >> 4) & 15;
    }

    public int blockLight(int x, int y, int z) {
        if (!inBounds(x, y, z)) return 0;
        return light[idx(x, y, z)] & 15;
    }

    /** Effective brightness 0..1 at a point, used by AI perception (darkness conceals). */
    public float brightness(float x, float y, float z, float daylight) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        float s = sky(ix, iy, iz) / 15f * daylight, b = blockLight(ix, iy, iz) / 15f;
        return Math.max(s, b * 0.9f);
    }

    public District district(float x, float z) { return district((int) x, (int) z); }

    public District district(int x, int z) {
        if (x < 0 || z < 0 || x >= W || z >= D) return District.WILDERNESS;
        return District.values()[districtMap[x * D + z]];
    }

    public int heightAt(int x, int z) {
        if (x < 0 || z < 0 || x >= W || z >= D) return 0;
        return heightMap[x * D + z];
    }

    public void updateHeight(int x, int z) {
        int base = (x * D + z) * H;
        int h = 0;
        for (int y = H - 1; y >= 0; y--) {
            int id = blocks[base + y] & 0xFF;
            if (id != 0 && !Block.ALL[id].is(Block.LIQUID) && Block.ALL[id].solid()) { h = y; break; }
        }
        heightMap[x * D + z] = (short) h;
    }

    public void computeHeights() {
        for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) updateHeight(x, z);
    }

    /**
     * Columns outside the city boundary (0 inside). The city occupies columns CITY_MIN .. CITY_MAX-1, so this is
     * the one rule every generation stage uses for "is this column mine": 0.4 measured against CITY_MAX itself,
     * which left column 608 claimed by nobody and empty to bedrock along the whole east and south wall.
     */
    public static int outsideDistance(int x, int z) {
        int dx = Math.max(CITY_MIN - x, x - (CITY_MAX - 1)), dz = Math.max(CITY_MIN - z, z - (CITY_MAX - 1));
        return Math.max(0, Math.max(dx, dz));
    }

    /** Distance outside the city boundary (0 inside). Wilderness radiation scales with it. */
    public static float outsideDistance(float x, float z) {
        float dx = Math.max(CITY_MIN - x, x - CITY_MAX), dz = Math.max(CITY_MIN - z, z - CITY_MAX);
        return Math.max(0, Math.max(dx, dz));
    }

    /** How far past the wall the hazard is measured from, and the dose per metre beyond it (content/combat.json could override). */
    public static float RAD_BASE = 0.5f, RAD_PER_M = 0.12f;

    /**
     * Wilderness exposure at a column: metres into irradiated ground, 0 where there is none. The one rule the
     * dosimeter, the health drain, weapon wear and the map all read (0.5 measured the wall distance in four
     * places, which irradiated the gate extraction zones and anything else the plan had placed against the wall).
     * Ground that belongs to a city district is clean, and so is every extraction zone, wherever it stands.
     */
    public float exposure(float x, float z) {
        float out = outsideDistance(x, z);
        if (out <= 0 || district(x, z) != District.WILDERNESS) return 0;
        for (MapData.Exit e : map.exits) {
            float dx = x - (e.x + 0.5f), dz = z - (e.z + 0.5f), r = e.radius + 1.5f;
            if (dx * dx + dz * dz < r * r) return 0;
        }
        return out;
    }

    /** Radiation dose rate at a column (0 = none). */
    public float radiation(float x, float z) { float out = exposure(x, z); return out > 0 ? RAD_BASE + out * RAD_PER_M : 0; }

    /**
     * Extraction zones that the plan put in a city district belong to that district to their edge: a gate through the
     * wall has half its zone on the far side of the boundary, and the district map said Wilderness there.
     */
    public void claimExitZones() {
        for (MapData.Exit e : map.exits) {
            if (e.district == District.WILDERNESS) continue;
            int r = (int) Math.ceil(e.radius) + 2;
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                int x = e.x + dx, z = e.z + dz;
                if (dx * dx + dz * dz > r * r || x < 0 || z < 0 || x >= W || z >= D) continue;
                if (districtMap[x * D + z] == District.WILDERNESS.ordinal()) districtMap[x * D + z] = (byte) e.district.ordinal();
            }
        }
    }

    // ------------------------------------------------------------- pristine snapshot
    public void snapshotPristine() {
        if (pristine == null) pristine = new byte[blocks.length];
        System.arraycopy(blocks, 0, pristine, 0, blocks.length);
    }

    public boolean hasPristine() { return pristine != null; }

    /** Restores the immutable map; caller re-applies persistent changes and relights. */
    public void restorePristine() {
        System.arraycopy(pristine, 0, blocks, 0, blocks.length);
        if (link != null) link.detach();                     // 1.2: the native core's copy is of the world as it was; whoever uses this one next gives it over again
        blockDamage.clear();
        cover.clear();
        doors = null;
        navVersion += 100;
        map.fires = null;
        java.util.Arrays.fill(dirtyColumns, true);
        anyDirty = true;
    }

    /** Finds every burn barrel (the anchors of the simulated fires). Cheap enough to run once per deployment. */
    public void scanFires() {
        java.util.List<int[]> l = new java.util.ArrayList<>();
        final byte want = (byte) Block.BARREL_FIRE;
        for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            int base = (x * D + z) * H;
            for (int y = 1; y < H - 1; y++) if (blocks[base + y] == want) l.add(new int[]{x, y, z});
        }
        map.fires = l;
    }

    public int pristineAt(int x, int y, int z) { return pristine == null ? get(x, y, z) : pristine[idx(x, y, z)] & 0xFF; }
}
