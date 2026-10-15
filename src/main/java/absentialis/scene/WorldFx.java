package absentialis.scene;

import absentialis.engine.decal.Decals;
import absentialis.engine.foliage.Foliage;
import absentialis.engine.fx.ParticleSystem;
import absentialis.world.Block;
import absentialis.world.District;
import absentialis.world.Material;
import absentialis.world.Shapes;
import absentialis.world.World;

/**
 * 1.0: what the Astratto Engine's particle, weather, foliage and decal systems are told about this game's world. The
 * engine knows nothing of blocks, districts or materials; each of its systems asks a few questions through a small
 * interface, and this class answers all of them from the voxel world - what kind of ground a block is, what counts as
 * a wall or a fence or a window, how well things grow in a district and how neglected it is.
 */
public final class WorldFx implements ParticleSystem.Terrain, Foliage.Source, Decals.Source {
    private final World w;
    /** Daylight as the particle system should light things by (set each frame by whoever knows the hour). */
    public float daylight = 1f;

    private static final int[] GROUND = new int[256], OBSTACLE = new int[256], DECAL = new int[256];

    static {
        for (Block b : Block.all()) {
            int id = b.id;
            boolean cube = b.shape == Shapes.CUBE && b.solid() && !b.is(Block.CROSS);
            // ---- what grows on it
            if (id == Block.GRASS) GROUND[id] = Foliage.Source.SOIL_LUSH;
            else if (id == Block.DEAD_GRASS) GROUND[id] = Foliage.Source.SOIL_DRY;
            else if (id == Block.DIRT || id == Block.MUD) GROUND[id] = Foliage.Source.SOIL_BARE;
            else if (id == Block.GRAVEL || id == Block.SAND || id == Block.RUBBLE || id == Block.RUBBLE_BRICK || id == Block.ASH) GROUND[id] = Foliage.Source.GRAVEL;
            else if (id == Block.SIDEWALK || id == Block.COBBLE || id == Block.CONCRETE_FLOOR || id == Block.CURB) GROUND[id] = Foliage.Source.PAVING;
            else if (id == Block.ASPHALT_CRACKED) GROUND[id] = Foliage.Source.PAVING_BROKEN;
            // ---- what stands beside a plant
            if (id == Block.TALL_GRASS || id == Block.CROP) OBSTACLE[id] = Foliage.Source.GROWTH;
            else if (b.is(Block.LIQUID)) OBSTACLE[id] = Foliage.Source.WATER;
            else if (b.shape == Shapes.FENCE || b.shape == Shapes.FENCE_Z) OBSTACLE[id] = b.solid() ? Foliage.Source.FENCE : 0;
            else if (cube && !Block.leaf(id) && !b.is(Block.DOOR)) OBSTACLE[id] = Foliage.Source.WALL;
            // ---- what a decal can lie on
            if (b.mat == Material.GLASS && cube && !b.is(Block.DOOR) && id != Block.CAR_WINDOW && b.light == 0) DECAL[id] = Decals.Source.WINDOW;
            else if (!cube || !b.opaque() || b.is(Block.CONTAINER) || b.is(Block.DOOR) || b.light > 0) DECAL[id] = 0;
            else if (id == Block.SIDEWALK || id == Block.COBBLE || id == Block.CONCRETE_FLOOR || id == Block.CURB || id == Block.HELIPAD) DECAL[id] = Decals.Source.PAVED;
            else if (b.mat == Material.ASPHALT) DECAL[id] = Decals.Source.ROAD;
            else if (b.mat == Material.GRASS || b.mat == Material.DIRT) DECAL[id] = id == Block.SANDBAG || id == Block.HESCO ? 0 : Decals.Source.SOIL;
            else if (b.mat == Material.TILE || b.mat == Material.FABRIC && b.name.startsWith("carpet") || id == Block.PARQUET || id == Block.PARQUET_C || id == Block.LINOLEUM || id == Block.LINOLEUM_C || id == Block.BOARDS_C) DECAL[id] = Decals.Source.FLOOR;
            else if (b.mat == Material.BRICK || b.mat == Material.CONCRETE || b.mat == Material.STONE) DECAL[id] = Decals.Source.MASONRY;
            else if (b.mat == Material.METAL || b.mat == Material.THIN_METAL) DECAL[id] = id == Block.STEEL_BEAM || id == Block.PIPE ? 0 : Decals.Source.METAL;
            else if (b.mat == Material.WOOD) DECAL[id] = Decals.Source.WOOD;
        }
    }

    public WorldFx(World world) { w = world; }

    // ---- particles
    @Override public boolean solid(int x, int y, int z) { return w.solid(x, y, z); }
    @Override public int top(int x, int z) { return x < 0 || z < 0 || x >= World.W || z >= World.D ? World.GROUND - 1 : w.heightAt(x, z); }
    @Override public float light(float x, float y, float z) { return 0.14f + 0.86f * w.brightness(x, y, z, 0.12f + 0.88f * daylight); }

    // ---- foliage
    @Override public int width() { return World.W; }
    @Override public int depth() { return World.D; }

    @Override public int surface(int x, int z) {
        if (x < 1 || z < 1 || x >= World.W - 1 || z >= World.D - 1) return -1;
        int y = w.heightAt(x, z);
        if (y < 2 || y >= World.H - 3) return -1;
        if (GROUND[w.get(x, y, z)] == 0) return -1;
        int above = w.get(x, y + 1, z);
        if (above != 0 && OBSTACLE[above] != Foliage.Source.GROWTH) return -1;
        // nothing grows under a roof: leaves overhead are not one, a slab is
        return w.sky(x, y + 1, z) >= 9 ? y + 1 : -1;
    }

    @Override public int ground(int x, int y, int z) { return GROUND[w.get(x, y, z)]; }

    @Override public int obstacle(int x, int y, int z) {
        int o = OBSTACLE[w.get(x, y, z)];
        if (o == 0 && Block.ALL[w.get(x, y - 1, z)].is(Block.LIQUID)) return Foliage.Source.WATER;
        return o;
    }

    @Override public int skyLight(int x, int y, int z) { return w.sky(x, y, z); }

    @Override public float fertility(int x, int z) {
        if (w.dev) return 1f;
        return switch (w.district(x, z)) {
            case WILDERNESS -> 1.35f;
            case MARKET -> 1.0f;
            case REDLINE -> 0.95f;
            case CIVIC -> 0.6f;
            case INDUSTRIAL -> 0.55f;
            case OLD_QUARTER -> 0.45f;
        };
    }

    @Override public int version(int cx, int cz) { return w.version(cx, cz); }

    // ---- decals
    @Override public int height() { return World.H; }
    @Override public int kind(int x, int y, int z) { return DECAL[w.get(x, y, z)]; }
    @Override public boolean open(int x, int y, int z) { return !Block.ALL[w.get(x, y, z)].occludes() && DECAL[w.get(x, y, z)] != Decals.Source.WINDOW; }

    // ---- linings: every room is shown its own walls
    private java.util.HashMap<Integer, java.util.List<absentialis.world.MapData.Room>> finished;
    private int finishedFor = -1;

    private void indexRooms() {
        finished = new java.util.HashMap<>();
        finishedFor = w.map.buildings.size() * 31 + w.map.props.size();
        for (absentialis.world.MapData.Building b : w.map.buildings) for (absentialis.world.MapData.Room rm : b.rooms) {
            if (rm.wallBlock == 0 && rm.ceilBlock == 0 || rm.height <= 0) continue;
            for (int x = rm.x0; x <= rm.x1; x++) for (int z = rm.z0; z <= rm.z1; z++) finished.computeIfAbsent(x * World.D + z, k -> new java.util.ArrayList<>(2)).add(rm);
        }
    }

    @Override public int lining(int x, int y, int z, int d) {
        if (finished == null || finishedFor != w.map.buildings.size() * 31 + w.map.props.size()) indexRooms();
        int nx = x + (d == 1 ? 1 : d == 3 ? -1 : 0), nz = z + (d == 2 ? 1 : d == 0 ? -1 : 0);
        java.util.List<absentialis.world.MapData.Room> col = finished.get(nx * World.D + nz);
        if (col == null) return -1;
        if (d == 4) {
            // 1.0, the ceiling: the underside of whatever is over the room, finished as the room's own ceiling
            for (absentialis.world.MapData.Room rm : col) {
                if (rm.ceilBlock == 0 || y - rm.y != rm.height + 1) continue;
                Block hb = Block.ALL[w.get(x, y, z)];
                if (hb.shape != Shapes.CUBE || !hb.opaque() || hb.light > 0 || w.get(x, y, z) == rm.ceilBlock) return -1;
                return Block.ALL[rm.ceilBlock].layerBottom;
            }
            return -1;
        }
        for (absentialis.world.MapData.Room rm : col) {
            int up = y - rm.y;                                    // 1 = the course that stands on the floor
            if (up < 1 || up > rm.height || rm.wallBlock == 0) continue;
            // only the room's own walls - the ring of cells round it - are lined: a column or a stair inside it is itself
            if (rm.contains(x, z)) return -1;
            int want = up <= rm.dadoH && rm.dadoBlock != 0 ? rm.dadoBlock : up == rm.height && rm.corniceBlock != 0 ? rm.corniceBlock : rm.wallBlock;
            int have = w.get(x, y, z);
            if (have == want) return -1;
            Block hb = Block.ALL[have];
            // only a plain wall is lined: not a pane, a door, a box of loot, a lamp, a piece of plant
            if (hb.shape != Shapes.CUBE || !hb.opaque() || hb.is(Block.CONTAINER) || hb.is(Block.DOOR) || hb.light > 0 || hb.mat == Material.METAL || hb.mat == Material.GLASS) return -1;
            return Block.ALL[want].layerSide;
        }
        return -1;
    }

    @Override public float wear(int x, int z) {
        if (w.dev) return 1f;
        District d = w.district(x, z);
        return switch (d) {
            case INDUSTRIAL -> 1.25f;
            case REDLINE -> 1.1f;
            case MARKET -> 1.0f;
            case OLD_QUARTER -> 0.95f;
            case WILDERNESS -> 0.85f;
            case CIVIC -> 0.7f;
        };
    }
}
