package absentialis.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable block type registry. Block ids are unsigned bytes stored in the world array.
 * Texture names are resolved against the generated HD atlas at startup (see TextureLibrary).
 */
public final class Block {
    public static final int SOLID = 1, OPAQUE = 2, CUTOUT = 4, TRANSLUCENT = 8, CLIMB = 16, STEP = 32,
            LIQUID = 64, HAZARD = 128, DOOR = 256, CONTAINER = 512, INTERACT = 1024, FRAGILE = 2048,
            LIGHT_PASS = 4096, NO_COLLIDE_AI = 8192, EMISSIVE_TEX = 16384, CROSS = 32768;

    public final int id;
    public final String name;
    public final Material mat;
    public final int flags;
    public final String texTop, texSide, texBottom;
    public int layerTop, layerSide, layerBottom, varTop = 1, varSide = 1, varBottom = 1;
    public int light;          // emitted block light 0..15
    public int hp;             // structural hit points; 0 = indestructible
    public int breaksInto;     // block id after destruction
    public String containerType; // loot table key for containers
    /** Sub-block geometry (see {@link Shapes}); 0 = full cube. Shaped blocks never hide their neighbours' faces. */
    public int shape;

    public static final Block[] ALL = new Block[256];
    private static final Map<String, Block> BY_NAME = new HashMap<>();
    private static final List<Block> LIST = new ArrayList<>();
    private static int next = 1;

    private Block(int id, String name, Material mat, int flags, String top, String side, String bottom) {
        this.id = id; this.name = name; this.mat = mat; this.flags = flags;
        this.texTop = top; this.texSide = side; this.texBottom = bottom;
    }

    public boolean is(int f) { return (flags & f) != 0; }
    public boolean solid() { return (flags & SOLID) != 0; }
    public boolean opaque() { return (flags & OPAQUE) != 0; }

    // ---------------------------------------------------------------- builder helpers
    private static Block reg(String name, Material m, int flags, String top, String side, String bottom) {
        Block b = new Block(next++, name, m, flags, top, side, bottom);
        ALL[b.id] = b;
        BY_NAME.put(name, b);
        LIST.add(b);
        return b;
    }
    private static int cube(String name, Material m, String tex) { return reg(name, m, SOLID | OPAQUE, tex, tex, tex).id; }
    private static int cube(String name, Material m, String top, String side) { return reg(name, m, SOLID | OPAQUE, top, side, top).id; }
    private static int cube3(String name, Material m, String top, String side, String bot) { return reg(name, m, SOLID | OPAQUE, top, side, bot).id; }
    private static int custom(String name, Material m, int flags, String top, String side, String bot) { return reg(name, m, flags, top, side, bot).id; }
    private static Block b(int id) { return ALL[id]; }
    private static int hp(int id, int hp, int into) { ALL[id].hp = hp; ALL[id].breaksInto = into; return id; }
    private static int glow(int id, int l) { ALL[id].light = l; return id; }
    private static int loot(int id, String type) { ALL[id].containerType = type; return id; }

    public static Block get(int id) { Block b = ALL[id & 0xFF]; return b == null ? ALL[AIR_ID_FALLBACK] : b; }
    public static Block byName(String n) { return BY_NAME.get(n); }
    public static List<Block> all() { return LIST; }
    private static final int AIR_ID_FALLBACK = 0;

    public static final int AIR = 0;
    static { ALL[0] = new Block(0, "air", Material.AIR, LIGHT_PASS, null, null, null); BY_NAME.put("air", ALL[0]); }

    // ---------------------------------------------------------------- ground & streets
    public static final int BEDROCK = cube("bedrock", Material.STONE, "bedrock");
    public static final int DIRT = cube("dirt", Material.DIRT, "dirt");
    public static final int GRASS = cube3("grass", Material.GRASS, "grass_top", "grass_side", "dirt");
    public static final int DEAD_GRASS = cube3("dead_grass", Material.GRASS, "dead_grass_top", "dead_grass_side", "dirt");
    public static final int MUD = cube("mud", Material.DIRT, "mud");
    public static final int GRAVEL = cube("gravel", Material.DIRT, "gravel");
    public static final int SAND = cube("sand", Material.DIRT, "sand");
    public static final int ASPHALT = cube3("asphalt", Material.ASPHALT, "asphalt", "concrete_dark", "concrete_dark");
    public static final int ASPHALT_CRACKED = cube3("asphalt_cracked", Material.ASPHALT, "asphalt_cracked", "concrete_dark", "concrete_dark");
    public static final int ROAD_LINE = cube3("road_line", Material.ASPHALT, "road_line", "concrete_dark", "concrete_dark");
    public static final int ROAD_EDGE = cube3("road_edge", Material.ASPHALT, "road_edge", "concrete_dark", "concrete_dark");
    public static final int CROSSWALK = cube3("crosswalk", Material.ASPHALT, "crosswalk", "concrete_dark", "concrete_dark");
    public static final int SIDEWALK = cube3("sidewalk", Material.CONCRETE, "sidewalk", "concrete", "concrete");
    public static final int CURB = cube3("curb", Material.CONCRETE, "curb", "concrete", "concrete");
    public static final int COBBLE = cube("cobble", Material.STONE, "cobble");
    public static final int MANHOLE = custom("manhole", Material.METAL, SOLID | OPAQUE | INTERACT, "manhole", "concrete", "concrete");
    public static final int DRAIN = custom("drain", Material.METAL, SOLID | CUTOUT | LIGHT_PASS, "drain", "concrete", "drain");
    public static final int RUBBLE = cube("rubble", Material.CONCRETE, "rubble");
    public static final int RUBBLE_BRICK = cube("rubble_brick", Material.BRICK, "rubble_brick");
    public static final int ASH = cube("ash", Material.DIRT, "ash");
    public static final int RAIL = cube3("rail", Material.METAL, "rail", "gravel", "gravel");
    public static final int RAIL_SLEEPER = cube3("rail_sleeper", Material.WOOD, "rail_sleeper", "gravel", "gravel");
    public static final int CONCRETE_FLOOR = cube("concrete_floor", Material.CONCRETE, "concrete_floor");
    public static final int BLOOD_FLOOR = cube("blood_floor", Material.CONCRETE, "blood_floor");
    public static final int OIL_FLOOR = cube("oil_floor", Material.CONCRETE, "oil_floor");
    public static final int TOXIC_GROUND = custom("toxic_ground", Material.DIRT, SOLID | OPAQUE | HAZARD, "toxic_ground", "toxic_ground", "dirt");

    // ---------------------------------------------------------------- walls and structure
    public static final int CONCRETE = cube("concrete", Material.CONCRETE, "concrete");
    public static final int CONCRETE_DARK = cube("concrete_dark", Material.CONCRETE, "concrete_dark");
    public static final int CONCRETE_PANEL = cube("concrete_panel", Material.CONCRETE, "concrete_panel");
    public static final int CONCRETE_STAINED = cube("concrete_stained", Material.CONCRETE, "concrete_stained");
    public static final int GRAFFITI = cube("graffiti", Material.CONCRETE, "graffiti");
    public static final int GRAFFITI2 = cube("graffiti2", Material.CONCRETE, "graffiti2");
    public static final int BRICK_RED = hp(cube("brick_red", Material.BRICK, "brick_red"), 900, 0);
    public static final int BRICK_BROWN = hp(cube("brick_brown", Material.BRICK, "brick_brown"), 900, 0);
    public static final int BRICK_YELLOW = hp(cube("brick_yellow", Material.BRICK, "brick_yellow"), 900, 0);
    public static final int BRICK_WHITE = hp(cube("brick_white", Material.BRICK, "brick_white"), 900, 0);
    public static final int BRICK_BURNT = hp(cube("brick_burnt", Material.BRICK, "brick_burnt"), 600, 0);
    public static final int PLASTER = hp(cube("plaster", Material.BRICK, "plaster"), 500, 0);
    public static final int PLASTER_BLUE = hp(cube("plaster_blue", Material.BRICK, "plaster_blue"), 500, 0);
    public static final int PLASTER_OCHRE = hp(cube("plaster_ochre", Material.BRICK, "plaster_ochre"), 500, 0);
    public static final int PLASTER_PEEL = hp(cube("plaster_peel", Material.BRICK, "plaster_peel"), 450, 0);
    public static final int STONE_BLOCK = cube("stone_block", Material.STONE, "stone_block");
    public static final int STONE_DARK = cube("stone_dark", Material.STONE, "stone_dark");
    public static final int STONE_CARVED = cube("stone_carved", Material.STONE, "stone_carved");
    public static final int MARBLE = cube("marble", Material.STONE, "marble");
    public static final int GRANITE = cube("granite", Material.STONE, "granite");
    public static final int METAL_PLATE = cube("metal_plate", Material.METAL, "metal_plate");
    public static final int METAL_RUST = cube("metal_rust", Material.METAL, "metal_rust");
    public static final int STEEL_BEAM = cube("steel_beam", Material.METAL, "steel_beam");
    public static final int CORRUGATED = hp(cube("corrugated", Material.THIN_METAL, "corrugated"), 260, 0);
    public static final int CORRUGATED_RUST = hp(cube("corrugated_rust", Material.THIN_METAL, "corrugated_rust"), 220, 0);
    public static final int CORRUGATED_BLUE = hp(cube("corrugated_blue", Material.THIN_METAL, "corrugated_blue"), 260, 0);
    public static final int WOOD_PLANKS = hp(cube("wood_planks", Material.WOOD, "wood_planks"), 140, 0);
    public static final int WOOD_DARK = hp(cube("wood_dark", Material.WOOD, "wood_dark"), 160, 0);
    public static final int WOOD_GREEN = hp(cube("wood_green", Material.WOOD, "wood_green"), 140, 0);
    public static final int BOARDED = hp(cube("boarded", Material.WOOD, "boarded"), 90, 0);
    public static final int CHAR_WOOD = hp(cube("char_wood", Material.WOOD, "char_wood"), 60, 0);
    public static final int LOG = cube("log", Material.WOOD, "log_top", "bark");
    public static final int OFFICE_PANEL = hp(cube("office_panel", Material.PLASTIC, "office_panel"), 120, 0);
    public static final int SEWER_BRICK = cube("sewer_brick", Material.BRICK, "sewer_brick");
    public static final int CATACOMB = cube("catacomb", Material.STONE, "catacomb");
    public static final int BONES = cube("bones", Material.STONE, "bones");
    public static final int BIOMASS = hp(custom("biomass", Material.FLESH, SOLID | OPAQUE | HAZARD, "biomass", "biomass", "biomass"), 200, 0);
    public static final int HESCO = cube3("hesco", Material.DIRT, "hesco_top", "hesco", "hesco_top");
    public static final int SANDBAG = hp(cube("sandbag", Material.DIRT, "sandbag"), 1500, 0);
    public static final int BARRIER = cube("barrier", Material.CONCRETE, "barrier");
    public static final int HAZARD_STRIPE = cube("hazard_stripe", Material.METAL, "hazard_stripe");
    public static final int CONTAINER_RED = hp(cube("container_red", Material.THIN_METAL, "container_top", "container_red"), 400, 0);
    public static final int CONTAINER_BLUE = hp(cube("container_blue", Material.THIN_METAL, "container_top", "container_blue"), 400, 0);
    public static final int CONTAINER_GREEN = hp(cube("container_green", Material.THIN_METAL, "container_top", "container_green"), 400, 0);
    public static final int CONTAINER_ORANGE = hp(cube("container_orange", Material.THIN_METAL, "container_top", "container_orange"), 400, 0);

    // ---------------------------------------------------------------- floors & interior surfaces
    public static final int TILE_WHITE = cube("tile_white", Material.TILE, "tile_white");
    public static final int TILE_GREEN = cube("tile_green", Material.TILE, "tile_green");
    public static final int TILE_CHECKER = cube("tile_checker", Material.TILE, "tile_checker");
    public static final int TILE_GRAY = cube("tile_gray", Material.TILE, "tile_gray");
    public static final int CARPET_RED = cube("carpet_red", Material.FABRIC, "carpet_red");
    public static final int CARPET_BLUE = cube("carpet_blue", Material.FABRIC, "carpet_blue");
    public static final int CARPET_GRAY = cube("carpet_gray", Material.FABRIC, "carpet_gray");
    public static final int PARQUET = cube("parquet", Material.WOOD, "parquet");
    public static final int LINOLEUM = cube("linoleum", Material.PLASTIC, "linoleum");
    public static final int WALLPAPER_FLORAL = hp(cube("wallpaper_floral", Material.BRICK, "wallpaper_floral"), 300, 0);
    public static final int WALLPAPER_STRIPE = hp(cube("wallpaper_stripe", Material.BRICK, "wallpaper_stripe"), 300, 0);
    public static final int WALLPAPER_TORN = hp(cube("wallpaper_torn", Material.BRICK, "wallpaper_torn"), 260, 0);
    public static final int CEILING_TILE = cube("ceiling_tile", Material.PLASTIC, "ceiling_tile");
    public static final int ROOF_TAR = cube3("roof_tar", Material.CONCRETE, "roof_tar", "concrete_dark", "concrete");
    public static final int ROOF_TILES = cube("roof_tiles", Material.STONE, "roof_tiles");
    public static final int ROOF_SLATE = cube("roof_slate", Material.STONE, "roof_slate");
    public static final int ROOF_METAL = hp(cube("roof_metal", Material.THIN_METAL, "roof_metal"), 260, 0);

    // ---------------------------------------------------------------- transparent / cutout
    public static final int GLASS = hp(custom("glass", Material.GLASS, SOLID | TRANSLUCENT | LIGHT_PASS | FRAGILE, "glass", "glass", "glass"), 8, 0);
    public static final int GLASS_DIRTY = hp(custom("glass_dirty", Material.GLASS, SOLID | TRANSLUCENT | LIGHT_PASS | FRAGILE, "glass_dirty", "glass_dirty", "glass_dirty"), 8, 0);
    public static final int GLASS_CRACKED = hp(custom("glass_cracked", Material.GLASS, SOLID | TRANSLUCENT | LIGHT_PASS | FRAGILE, "glass_cracked", "glass_cracked", "glass_cracked"), 4, 0);
    public static final int GLASS_BLUE = hp(custom("glass_blue", Material.GLASS, SOLID | TRANSLUCENT | LIGHT_PASS | FRAGILE, "glass_blue", "glass_blue", "glass_blue"), 10, 0);
    public static final int GLASS_STAINED = hp(custom("glass_stained", Material.GLASS, SOLID | TRANSLUCENT | LIGHT_PASS | FRAGILE, "glass_stained", "glass_stained", "glass_stained"), 8, 0);
    public static final int WATER = custom("water", Material.WATER, TRANSLUCENT | LIQUID | LIGHT_PASS, "water", "water", "water");
    public static final int SEWAGE = custom("sewage", Material.WATER, TRANSLUCENT | LIQUID | LIGHT_PASS, "sewage", "sewage", "sewage");
    public static final int TOXIC = glow(custom("toxic", Material.WATER, TRANSLUCENT | LIQUID | LIGHT_PASS | HAZARD | EMISSIVE_TEX, "toxic", "toxic", "toxic"), 7);
    public static final int CHAINLINK = hp(custom("chainlink", Material.THIN_METAL, SOLID | CUTOUT | LIGHT_PASS, "chainlink", "chainlink", "chainlink"), 120, 0);
    public static final int IRON_FENCE = custom("iron_fence", Material.METAL, SOLID | CUTOUT | LIGHT_PASS, "iron_fence", "iron_fence", "iron_fence");
    public static final int BARBED = hp(custom("barbed", Material.THIN_METAL, CUTOUT | LIGHT_PASS | HAZARD, "barbed", "barbed", "barbed"), 60, 0);
    public static final int GRATE = custom("grate", Material.METAL, SOLID | CUTOUT | LIGHT_PASS, "grate", "grate", "grate");
    public static final int SCAFFOLD = hp(custom("scaffold", Material.METAL, SOLID | CUTOUT | LIGHT_PASS | CLIMB, "grate", "scaffold", "grate"), 300, 0);
    public static final int LADDER = custom("ladder", Material.METAL, CUTOUT | LIGHT_PASS | CLIMB, "ladder", "ladder", "ladder");
    public static final int LEAVES = hp(custom("leaves", Material.FOLIAGE, SOLID | CUTOUT | LIGHT_PASS, "leaves", "leaves", "leaves"), 30, 0);
    public static final int DEAD_LEAVES = hp(custom("dead_leaves", Material.FOLIAGE, SOLID | CUTOUT | LIGHT_PASS, "dead_leaves", "dead_leaves", "dead_leaves"), 30, 0);
    // 0.7, procedural foliage: the same leaf in three more tones - sunlit, shaded and a yellowing olive - so a canopy has patches of colour
    public static final int LEAVES_LIGHT = hp(custom("leaves_light", Material.FOLIAGE, SOLID | CUTOUT | LIGHT_PASS, "leaves_light", "leaves_light", "leaves_light"), 30, 0);
    public static final int LEAVES_DARK = hp(custom("leaves_dark", Material.FOLIAGE, SOLID | CUTOUT | LIGHT_PASS, "leaves_dark", "leaves_dark", "leaves_dark"), 30, 0);
    public static final int LEAVES_OLIVE = hp(custom("leaves_olive", Material.FOLIAGE, SOLID | CUTOUT | LIGHT_PASS, "leaves_olive", "leaves_olive", "leaves_olive"), 30, 0);

    /** Tree foliage of any tone (living or dead). */
    public static boolean leaf(int id) { return id == LEAVES || id == LEAVES_LIGHT || id == LEAVES_DARK || id == LEAVES_OLIVE || id == DEAD_LEAVES; }
    public static final int HEDGE = hp(cube("hedge", Material.FOLIAGE, "hedge"), 60, 0);
    public static final int LATTICE = custom("lattice", Material.METAL, SOLID | CUTOUT | LIGHT_PASS, "lattice", "lattice", "lattice");
    public static final int LAUNDRY = custom("laundry", Material.FABRIC, CUTOUT | LIGHT_PASS | NO_COLLIDE_AI, "laundry", "laundry", "laundry");
    public static final int WIRES = custom("wires", Material.FABRIC, CUTOUT | LIGHT_PASS, "wires", "wires", "wires");
    public static final int COBWEB = custom("cobweb", Material.FABRIC, CROSS | CUTOUT | LIGHT_PASS, "cobweb", "cobweb", "cobweb");
    public static final int TALL_GRASS = custom("tall_grass", Material.FOLIAGE, CROSS | CUTOUT | LIGHT_PASS, "tall_grass", "tall_grass", "tall_grass");

    // ---------------------------------------------------------------- stairs / doors
    public static final int STAIRS_CONCRETE = custom("stairs_concrete", Material.CONCRETE, SOLID | OPAQUE | STEP, "stair_top", "concrete", "concrete");
    public static final int STAIRS_WOOD = custom("stairs_wood", Material.WOOD, SOLID | OPAQUE | STEP, "stair_wood_top", "wood_planks", "wood_planks");
    public static final int STAIRS_METAL = custom("stairs_metal", Material.METAL, SOLID | CUTOUT | STEP | LIGHT_PASS, "grate", "scaffold", "grate");
    public static final int DOOR_WOOD = hp(custom("door_wood", Material.WOOD, SOLID | OPAQUE | DOOR | INTERACT, "wood_dark", "door_wood", "wood_dark"), 120, 0);
    public static final int DOOR_WOOD_OPEN = custom("door_wood_open", Material.WOOD, CUTOUT | LIGHT_PASS | DOOR | INTERACT, "wood_dark", "door_wood", "wood_dark");
    public static final int DOOR_METAL = hp(custom("door_metal", Material.METAL, SOLID | OPAQUE | DOOR | INTERACT, "metal_plate", "door_metal", "metal_plate"), 800, 0);
    public static final int DOOR_METAL_OPEN = custom("door_metal_open", Material.METAL, CUTOUT | LIGHT_PASS | DOOR | INTERACT, "metal_plate", "door_metal", "metal_plate");
    public static final int BARRICADE = hp(custom("barricade", Material.WOOD, SOLID | CUTOUT | LIGHT_PASS | INTERACT, "barricade", "barricade", "barricade"), 100, 0);
    public static final int GATE_METAL = hp(custom("gate_metal", Material.METAL, SOLID | CUTOUT | LIGHT_PASS, "gate", "gate", "gate"), 600, 0);

    // ---------------------------------------------------------------- furniture & props
    public static final int TABLE = hp(custom("table", Material.WOOD, SOLID | OPAQUE, "table_top", "table_side", "wood_dark"), 80, 0);
    public static final int DESK = hp(custom("desk", Material.WOOD, SOLID | OPAQUE | CONTAINER | INTERACT, "desk_top", "desk_side", "wood_dark"), 90, 0);
    public static final int COUNTER = hp(custom("counter", Material.WOOD, SOLID | OPAQUE, "counter_top", "counter_side", "wood_dark"), 120, 0);
    public static final int SHELF = hp(custom("shelf", Material.METAL, SOLID | OPAQUE | CONTAINER | INTERACT, "metal_plate", "shelf", "metal_plate"), 150, 0);
    public static final int BOOKSHELF = hp(custom("bookshelf", Material.WOOD, SOLID | OPAQUE | CONTAINER | INTERACT, "wood_dark", "bookshelf", "wood_dark"), 90, 0);
    public static final int BED = hp(custom("bed", Material.FABRIC, SOLID | OPAQUE, "bed_top", "bed_side", "wood_dark"), 60, 0);
    public static final int HOSPITAL_BED = hp(custom("hospital_bed", Material.METAL, SOLID | OPAQUE, "hbed_top", "hbed_side", "metal_plate"), 120, 0);
    public static final int SOFA = hp(custom("sofa", Material.FABRIC, SOLID | OPAQUE, "sofa_top", "sofa_side", "wood_dark"), 60, 0);
    public static final int CHAIR = hp(custom("chair", Material.WOOD, SOLID | CUTOUT | LIGHT_PASS, "wood_dark", "wood_planks", "wood_dark"), 30, 0);
    public static final int STOVE = custom("stove", Material.METAL, SOLID | OPAQUE, "stove_top", "stove_side", "metal_plate");
    public static final int SINK = custom("sink", Material.TILE, SOLID | OPAQUE, "sink_top", "counter_side", "tile_white");
    public static final int TOILET = custom("toilet", Material.TILE, SOLID | CUTOUT | LIGHT_PASS, "toilet_top", "toilet_side", "tile_white");
    public static final int TV = custom("tv", Material.PLASTIC, SOLID | OPAQUE, "plastic_black", "tv", "plastic_black");
    public static final int COMPUTER = custom("computer", Material.PLASTIC, SOLID | OPAQUE, "plastic_black", "computer", "plastic_black");
    public static final int WASHER = custom("washer", Material.METAL, SOLID | OPAQUE, "white_metal", "washer", "white_metal");
    public static final int BARREL = hp(custom("barrel", Material.THIN_METAL, SOLID | OPAQUE, "barrel_top", "barrel", "barrel_top"), 200, 0);
    public static final int BARREL_FIRE = glow(custom("barrel_fire", Material.THIN_METAL, SOLID | OPAQUE | EMISSIVE_TEX, "fire_top", "barrel", "barrel_top"), 14);
    public static final int TIRE = custom("tire", Material.FABRIC, SOLID | OPAQUE, "tire_top", "tire", "tire_top");
    public static final int PIPE = custom("pipe", Material.METAL, SOLID | OPAQUE, "pipe_end", "pipe", "pipe_end");
    public static final int VENT = custom("vent", Material.METAL, SOLID | OPAQUE, "vent", "metal_plate", "metal_plate");
    public static final int AC_UNIT = custom("ac_unit", Material.METAL, SOLID | OPAQUE, "ac_top", "ac_side", "metal_plate");
    public static final int WATER_TANK = custom("water_tank", Material.WOOD, SOLID | OPAQUE, "tank_top", "tank_side", "tank_top");
    public static final int MACHINE = custom("machine", Material.METAL, SOLID | OPAQUE, "metal_plate", "machine", "metal_plate");
    public static final int TRANSFORMER = custom("transformer", Material.METAL, SOLID | OPAQUE | INTERACT, "metal_plate", "transformer", "metal_plate");
    public static final int GENERATOR = custom("generator", Material.METAL, SOLID | OPAQUE, "metal_plate", "generator", "metal_plate");
    public static final int CONSOLE = custom("console", Material.METAL, SOLID | OPAQUE | INTERACT | EMISSIVE_TEX, "console_top", "console", "metal_plate");
    public static final int PURIFIER = custom("purifier", Material.METAL, SOLID | OPAQUE | INTERACT, "tank_top", "purifier", "metal_plate");
    public static final int CAR_RED = cube("car_red", Material.METAL, "car_red");
    public static final int CAR_BLUE = cube("car_blue", Material.METAL, "car_blue");
    public static final int CAR_WHITE = cube("car_white", Material.METAL, "car_white");
    public static final int CAR_RUST = cube("car_rust", Material.METAL, "car_rust");
    public static final int CAR_OLIVE = cube("car_olive", Material.METAL, "car_olive");
    public static final int CAR_WINDOW = hp(custom("car_window", Material.GLASS, SOLID | TRANSLUCENT | LIGHT_PASS | FRAGILE, "car_window", "car_window", "car_window"), 6, 0);
    public static final int CAR_TIRE = cube("car_tire", Material.FABRIC, "tire");
    public static final int STATUE = cube("statue", Material.STONE, "statue");
    public static final int TOMBSTONE = custom("tombstone", Material.STONE, SOLID | OPAQUE, "stone_block", "tombstone", "stone_block");
    public static final int COFFIN = custom("coffin", Material.WOOD, SOLID | OPAQUE | CONTAINER | INTERACT, "coffin_top", "wood_dark", "wood_dark");
    public static final int ALTAR = custom("altar", Material.FABRIC, SOLID | OPAQUE, "altar_top", "altar_side", "stone_block");
    public static final int PEW = custom("pew", Material.WOOD, SOLID | OPAQUE, "wood_dark", "pew", "wood_dark");
    public static final int BANNER_CULT = custom("banner_cult", Material.FABRIC, CUTOUT | LIGHT_PASS, "banner_cult", "banner_cult", "banner_cult");
    public static final int CANVAS = cube("canvas", Material.FABRIC, "canvas");
    public static final int CANVAS_OLIVE = cube("canvas_olive", Material.FABRIC, "canvas_olive");
    public static final int TARP = cube("tarp", Material.FABRIC, "tarp");
    public static final int AWNING_RED = custom("awning_red", Material.FABRIC, SOLID | OPAQUE, "awning_red", "awning_red", "awning_red");
    public static final int AWNING_GREEN = custom("awning_green", Material.FABRIC, SOLID | OPAQUE, "awning_green", "awning_green", "awning_green");
    public static final int SIGN_SHOP = cube("sign_shop", Material.WOOD, "sign_shop");
    public static final int SIGN_PHARMACY = glow(custom("sign_pharmacy", Material.PLASTIC, SOLID | OPAQUE | EMISSIVE_TEX, "plastic_black", "sign_pharmacy", "plastic_black"), 0);
    public static final int POSTER = cube("poster", Material.CONCRETE, "poster");
    public static final int MAP_BOARD = cube("map_board", Material.WOOD, "map_board");
    public static final int BIOHAZARD = cube("biohazard", Material.METAL, "biohazard");
    public static final int RED_CROSS = cube("red_cross", Material.TILE, "red_cross");

    // ---------------------------------------------------------------- lights
    public static final int LAMP = glow(custom("lamp", Material.GLASS, SOLID | OPAQUE | EMISSIVE_TEX, "lamp", "lamp", "lamp"), 14);
    public static final int LAMP_OFF = custom("lamp_off", Material.GLASS, SOLID | OPAQUE, "lamp_off", "lamp_off", "lamp_off");
    public static final int NEON_RED = glow(custom("neon_red", Material.GLASS, SOLID | OPAQUE | EMISSIVE_TEX, "neon_red", "neon_red", "neon_red"), 11);
    public static final int NEON_BLUE = glow(custom("neon_blue", Material.GLASS, SOLID | OPAQUE | EMISSIVE_TEX, "neon_blue", "neon_blue", "neon_blue"), 11);
    public static final int NEON_GREEN = glow(custom("neon_green", Material.GLASS, SOLID | OPAQUE | EMISSIVE_TEX, "neon_green", "neon_green", "neon_green"), 11);
    public static final int NEON_OFF = custom("neon_off", Material.GLASS, SOLID | OPAQUE, "neon_off", "neon_off", "neon_off");
    public static final int CANDLES = glow(custom("candles", Material.WOOD, CROSS | CUTOUT | LIGHT_PASS | EMISSIVE_TEX, "candles", "candles", "candles"), 10);
    // 0.4: there is no fire block. Fire is simulated (scene.FireSystem): flames are particles with their own light,
    // sound and heat, and a burn barrel is a barrel block with such a fire bound to it.
    public static final int EMBER = glow(custom("ember", Material.DIRT, SOLID | OPAQUE | EMISSIVE_TEX, "ember", "ember", "ember"), 8);
    public static final int RED_LIGHT = glow(custom("red_light", Material.GLASS, SOLID | OPAQUE | EMISSIVE_TEX, "red_light", "red_light", "red_light"), 9);

    // ---------------------------------------------------------------- loot containers (searchable)
    public static final int CRATE = loot(hp(custom("crate", Material.WOOD, SOLID | OPAQUE | CONTAINER | INTERACT, "crate_top", "crate", "crate_top"), 70, 0), "crate");
    public static final int CRATE_MIL = loot(hp(custom("crate_mil", Material.WOOD, SOLID | OPAQUE | CONTAINER | INTERACT, "crate_mil_top", "crate_mil", "crate_mil_top"), 90, 0), "military");
    public static final int LOCKER = loot(custom("locker", Material.THIN_METAL, SOLID | OPAQUE | CONTAINER | INTERACT, "metal_plate", "locker", "metal_plate"), "locker");
    public static final int CABINET = loot(hp(custom("cabinet", Material.WOOD, SOLID | OPAQUE | CONTAINER | INTERACT, "counter_top", "cabinet", "wood_dark"), 80, 0), "residential");
    public static final int FRIDGE = loot(custom("fridge", Material.METAL, SOLID | OPAQUE | CONTAINER | INTERACT, "white_metal", "fridge", "white_metal"), "food");
    public static final int SAFE = loot(custom("safe", Material.METAL, SOLID | OPAQUE | CONTAINER | INTERACT, "safe_top", "safe", "safe_top"), "safe");
    public static final int MED_CABINET = loot(custom("med_cabinet", Material.METAL, SOLID | OPAQUE | CONTAINER | INTERACT, "white_metal", "med_cabinet", "white_metal"), "medical");
    public static final int FILING = loot(custom("filing", Material.METAL, SOLID | OPAQUE | CONTAINER | INTERACT, "metal_plate", "filing", "metal_plate"), "office");
    public static final int TOOLBOX = loot(custom("toolbox", Material.METAL, SOLID | OPAQUE | CONTAINER | INTERACT, "toolbox_top", "toolbox", "metal_plate"), "industrial");
    public static final int TRASH = loot(custom("trash", Material.PLASTIC, SOLID | OPAQUE | CONTAINER | INTERACT, "trash_top", "trash", "plastic_black"), "trash");
    public static final int DUMPSTER = loot(custom("dumpster", Material.THIN_METAL, SOLID | OPAQUE | CONTAINER | INTERACT, "dumpster_top", "dumpster", "metal_plate"), "trash");
    public static final int WEAPON_RACK = loot(custom("weapon_rack", Material.METAL, SOLID | OPAQUE | CONTAINER | INTERACT, "metal_plate", "weapon_rack", "metal_plate"), "weapons");
    public static final int STASH_CACHE = loot(custom("stash_cache", Material.WOOD, SOLID | OPAQUE | CONTAINER | INTERACT, "cache_top", "cache", "cache_top"), "cache");
    public static final int BODYBAG = loot(custom("bodybag", Material.FABRIC, SOLID | OPAQUE | CONTAINER | INTERACT, "bodybag", "bodybag", "bodybag"), "body");
    public static final int MARKET_STALL = loot(custom("market_stall", Material.WOOD, SOLID | OPAQUE | CONTAINER | INTERACT, "stall_top", "stall", "wood_dark"), "market");
    public static final int RADIO_SET = loot(custom("radio_set", Material.METAL, SOLID | OPAQUE | CONTAINER | INTERACT | EMISSIVE_TEX, "radio_top", "radio", "metal_plate"), "tech");

    // ---------------------------------------------------------------- v0.3: street furniture with real proportions
    public static final int LAMP_POST = custom("lamp_post", Material.METAL, SOLID | CUTOUT | LIGHT_PASS, "steel_beam", "steel_beam", "steel_beam");
    public static final int LAMP_ARM = custom("lamp_arm", Material.METAL, CUTOUT | LIGHT_PASS, "steel_beam", "steel_beam", "steel_beam");
    public static final int ROAD_SIGN = custom("road_sign", Material.METAL, CUTOUT | LIGHT_PASS, "metal_plate", "road_sign", "metal_plate");

    // lane markings that run with the road whichever way it points, and the ring road's double line
    public static final int ROAD_LINE_X = cube3("road_line_x", Material.ASPHALT, "road_line_x", "concrete_dark", "concrete_dark");
    public static final int ROAD_EDGE_X = cube3("road_edge_x", Material.ASPHALT, "road_edge_x", "concrete_dark", "concrete_dark");
    public static final int ROAD_TRUNK = cube3("road_trunk", Material.ASPHALT, "road_trunk", "concrete_dark", "concrete_dark");
    public static final int ROAD_TRUNK_X = cube3("road_trunk_x", Material.ASPHALT, "road_trunk_x", "concrete_dark", "concrete_dark");
    public static final int ROAD_SIGN_Z = custom("road_sign_z", Material.METAL, CUTOUT | LIGHT_PASS, "metal_plate", "road_sign", "metal_plate");

    // ---------------------------------------------------------------- v0.5: floors with their own ceiling underneath
    // A storey's floor block is also the ceiling of the room below, so each finish carries a ceiling texture on its
    // underside: plaster under homes, suspended tile under offices and shops, boards under a loft.
    public static final int PARQUET_C = cube3("parquet_c", Material.WOOD, "parquet", "wood_dark", "plaster");
    public static final int CARPET_RED_C = cube3("carpet_red_c", Material.FABRIC, "carpet_red", "wood_dark", "plaster");
    public static final int CARPET_BLUE_C = cube3("carpet_blue_c", Material.FABRIC, "carpet_blue", "wood_dark", "plaster");
    public static final int CARPET_GRAY_C = cube3("carpet_gray_c", Material.FABRIC, "carpet_gray", "concrete", "ceiling_tile");
    // 0.7 (interiors): things a room has that are not somewhere to look for loot - emptied shelving, a plant, a standing lamp, a bedside table
    public static final int RACK = hp(custom("rack", Material.METAL, SOLID | CUTOUT | LIGHT_PASS, "metal_plate", "shelf", "metal_plate"), 120, 0);
    public static final int PLANT = hp(custom("plant", Material.FOLIAGE, SOLID | CUTOUT | LIGHT_PASS, "leaves", "leaves", "wood_dark"), 20, 0);
    public static final int FLOOR_LAMP = hp(custom("floor_lamp", Material.THIN_METAL, SOLID | CUTOUT | LIGHT_PASS, "lamp_off", "lamp_off", "plastic_black"), 20, 0);
    public static final int NIGHTSTAND = hp(custom("nightstand", Material.WOOD, SOLID | CUTOUT | LIGHT_PASS, "table_top", "cabinet", "wood_dark"), 50, 0);
    public static final int DEBRIS_PILE = hp(custom("debris_pile", Material.PLASTIC, SOLID | CUTOUT | LIGHT_PASS, "trash_top", "trash", "plastic_black"), 20, 0);
    public static final int TILE_WHITE_C = cube3("tile_white_c", Material.TILE, "tile_white", "concrete", "plaster");
    public static final int TILE_CHECKER_C = cube3("tile_checker_c", Material.TILE, "tile_checker", "concrete", "plaster");
    public static final int TILE_GRAY_C = cube3("tile_gray_c", Material.TILE, "tile_gray", "concrete", "ceiling_tile");
    public static final int TILE_GREEN_C = cube3("tile_green_c", Material.TILE, "tile_green", "concrete", "ceiling_tile");
    public static final int LINOLEUM_C = cube3("linoleum_c", Material.PLASTIC, "linoleum", "concrete", "ceiling_tile");
    public static final int BOARDS_C = hp(cube3("boards_c", Material.WOOD, "wood_planks", "wood_dark", "wood_dark"), 140, 0);
    public static final int CEILING_PLASTER = cube3("ceiling_plaster", Material.CONCRETE, "concrete_floor", "concrete", "plaster");
    public static final int CEILING_PANEL = cube3("ceiling_panel", Material.CONCRETE, "concrete_floor", "concrete", "ceiling_tile");
    /** Glazed doors: seen through when shut, and a round goes through the pane. */
    public static final int DOOR_GLASS = hp(custom("door_glass", Material.GLASS, SOLID | TRANSLUCENT | LIGHT_PASS | DOOR | INTERACT, "metal_plate", "glass", "metal_plate"), 40, 0);
    public static final int DOOR_GLASS_OPEN = custom("door_glass_open", Material.GLASS, CUTOUT | LIGHT_PASS | DOOR | INTERACT, "metal_plate", "glass", "metal_plate");
    /** Cell bars: a door-sized grille a body cannot pass and a round can. */
    public static final int CELL_BARS = hp(custom("cell_bars", Material.METAL, SOLID | CUTOUT | LIGHT_PASS, "iron_fence", "iron_fence", "iron_fence"), 900, 0);
    public static final int HAY = hp(cube("hay", Material.FOLIAGE, "hesco_top", "sandbag"), 80, 0);
    public static final int CROP = custom("crop", Material.FOLIAGE, CROSS | CUTOUT | LIGHT_PASS, "tall_grass", "tall_grass", "tall_grass");
    public static final int RUNWAY = cube3("runway", Material.ASPHALT, "asphalt", "concrete_dark", "concrete_dark");
    public static final int HELIPAD = cube3("helipad", Material.CONCRETE, "hazard_stripe", "concrete_dark", "concrete_dark");

    // ---------------------------------------------------------------- 0.6 (appended: earlier ids never move)
    /** Double doors: two cells wide, one object (scene.Doors pairs the two columns into a single entity with two leaves). */
    public static final int DOOR_DOUBLE_WOOD = hp(custom("door_double_wood", Material.WOOD, SOLID | OPAQUE | DOOR | INTERACT, "wood_dark", "door_wood", "wood_dark"), 160, 0);
    public static final int DOOR_DOUBLE_WOOD_OPEN = custom("door_double_wood_open", Material.WOOD, CUTOUT | LIGHT_PASS | DOOR | INTERACT, "wood_dark", "door_wood", "wood_dark");
    public static final int DOOR_DOUBLE_METAL = hp(custom("door_double_metal", Material.METAL, SOLID | OPAQUE | DOOR | INTERACT, "metal_plate", "door_metal", "metal_plate"), 900, 0);
    public static final int DOOR_DOUBLE_METAL_OPEN = custom("door_double_metal_open", Material.METAL, CUTOUT | LIGHT_PASS | DOOR | INTERACT, "metal_plate", "door_metal", "metal_plate");
    public static final int DOOR_DOUBLE_GLASS = hp(custom("door_double_glass", Material.GLASS, SOLID | TRANSLUCENT | LIGHT_PASS | DOOR | INTERACT, "metal_plate", "glass", "metal_plate"), 60, 0);
    public static final int DOOR_DOUBLE_GLASS_OPEN = custom("door_double_glass_open", Material.GLASS, CUTOUT | LIGHT_PASS | DOOR | INTERACT, "metal_plate", "glass", "metal_plate");

    /** Two-tone walls: gloss paint to shoulder height, whitewash above (MaterialRules.course lays the three courses). */
    public static final int PAINT_GREEN = hp(cube("paint_green", Material.BRICK, "paint_green"), 500, 0);
    public static final int DADO_GREEN = hp(cube("dado_green", Material.BRICK, "dado_green"), 500, 0);
    public static final int PAINT_BLUE = hp(cube("paint_blue", Material.BRICK, "paint_blue"), 500, 0);
    public static final int DADO_BLUE = hp(cube("dado_blue", Material.BRICK, "dado_blue"), 500, 0);

    public static boolean doubleDoor(int id) { return id >= DOOR_DOUBLE_WOOD && id <= DOOR_DOUBLE_GLASS_OPEN; }

    // ---------------------------------------------------------------- 1.0 (appended)
    /**
     * Stand-ins for props (see engine.props.Props and world.gen.PropLib): the cell a piece of furniture or machinery
     * stands in. Never drawn - the prop's model is - but solid to bodies and bullets, of wood or of metal, standing the
     * full height of the cell or the lower half of it; PROP_POST is something thin (a lamp standard, a hat stand).
     * PROP_GLOW is the light of a prop that burns (a candle, a hearth): not solid, not drawn, a source of light.
     */
    public static final int PROP_WOOD = hp(custom("prop_wood", Material.WOOD, SOLID | CUTOUT | LIGHT_PASS, "wood_dark", "wood_dark", "wood_dark"), 110, 0);
    public static final int PROP_WOOD_LOW = hp(custom("prop_wood_low", Material.WOOD, SOLID | CUTOUT | LIGHT_PASS, "wood_dark", "wood_dark", "wood_dark"), 80, 0);
    public static final int PROP_METAL = hp(custom("prop_metal", Material.METAL, SOLID | CUTOUT | LIGHT_PASS, "metal_plate", "metal_plate", "metal_plate"), 500, 0);
    public static final int PROP_METAL_LOW = hp(custom("prop_metal_low", Material.METAL, SOLID | CUTOUT | LIGHT_PASS, "metal_plate", "metal_plate", "metal_plate"), 400, 0);
    public static final int PROP_POST = hp(custom("prop_post", Material.THIN_METAL, SOLID | CUTOUT | LIGHT_PASS, "metal_plate", "metal_plate", "metal_plate"), 40, 0);
    public static final int PROP_GLOW = glow(custom("prop_glow", Material.AIR, CUTOUT | LIGHT_PASS | NO_COLLIDE_AI, "candles", "candles", "candles"), 11);

    public static boolean prop(int id) { return id >= PROP_WOOD && id <= PROP_GLOW; }

    /** The signposted ring road that links every district (the centre line of the avenues). */
    public static boolean trunk(int id) { return id == ROAD_TRUNK || id == ROAD_TRUNK_X; }

    private static void shape(int shape, int... ids) { for (int id : ids) ALL[id].shape = shape; }

    static {
        shape(Shapes.LADDER, LADDER);
        shape(Shapes.DOOR, DOOR_WOOD, DOOR_METAL);
        shape(Shapes.DOOR_OPEN, DOOR_WOOD_OPEN, DOOR_METAL_OPEN);
        shape(Shapes.FENCE, IRON_FENCE, CHAINLINK, BARBED, GATE_METAL, BARRICADE, BANNER_CULT, LAUNDRY, WIRES, ROAD_SIGN);
        shape(Shapes.POST, LAMP_POST);
        shape(Shapes.ARM, LAMP_ARM);
        shape(Shapes.CHAIR, CHAIR);
        shape(Shapes.TABLE, TABLE);
        shape(Shapes.BED, BED, HOSPITAL_BED);
        shape(Shapes.SOFA, SOFA);
        shape(Shapes.TOILET, TOILET);
        shape(Shapes.PEW, PEW);
        shape(Shapes.TOMBSTONE, TOMBSTONE);
        shape(Shapes.SHELF, SHELF);
        shape(Shapes.BARREL, BARREL, BARREL_FIRE);
        shape(Shapes.TIRE, TIRE);
        shape(Shapes.FIXTURE, LAMP, LAMP_OFF);
        shape(Shapes.DESK, DESK);
        shape(Shapes.SCREEN, TV);
        shape(Shapes.MONITOR, COMPUTER);
        shape(Shapes.BASIN, SINK);
        shape(Shapes.BIN, TRASH);
        shape(Shapes.RACK, RACK);
        shape(Shapes.PLANT, PLANT);
        shape(Shapes.FLOOR_LAMP, FLOOR_LAMP);
        shape(Shapes.NIGHTSTAND, NIGHTSTAND);
        shape(Shapes.HEAP, DEBRIS_PILE);
        shape(Shapes.FENCE_Z, ROAD_SIGN_Z);
        shape(Shapes.FENCE, CELL_BARS);
        shape(Shapes.DOOR, DOOR_GLASS);
        shape(Shapes.DOOR_OPEN, DOOR_GLASS_OPEN);
        shape(Shapes.DOOR, DOOR_DOUBLE_WOOD, DOOR_DOUBLE_METAL, DOOR_DOUBLE_GLASS);
        shape(Shapes.DOOR_OPEN, DOOR_DOUBLE_WOOD_OPEN, DOOR_DOUBLE_METAL_OPEN, DOOR_DOUBLE_GLASS_OPEN);
        shape(Shapes.STAIR, STAIRS_CONCRETE, STAIRS_WOOD);
        shape(Shapes.STAIR_OPEN, STAIRS_METAL);
        shape(Shapes.PHANTOM_FULL, PROP_WOOD, PROP_METAL);
        // 1.3: drawn as voxel objects (scene.BlockObjects), not cubes: the cell keeps the block, the mesher leaves it out
        shape(Shapes.PHANTOM_FULL, CRATE, CRATE_MIL, MED_CABINET, TOOLBOX, LOCKER, FILING, SAFE, TRASH, BOARDED);
        shape(Shapes.PHANTOM_LOW, PROP_WOOD_LOW, PROP_METAL_LOW);
        shape(Shapes.PHANTOM_POST, PROP_POST);
        shape(Shapes.PHANTOM_NONE, PROP_GLOW);
    }

    /** 1.0: a stair with treads (see {@link Shapes#STAIR}): walked up and down, not stepped onto. */
    public boolean stair() { return shape == Shapes.STAIR || shape == Shapes.STAIR_OPEN; }

    /** True when this block fills its cell and hides whatever is behind it (mesher face culling / AO). */
    public boolean occludes() { return (flags & OPAQUE) != 0 && shape == 0; }

    public static int count() { return next; }
}
