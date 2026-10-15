package absentialis.world.gen.arch;

import absentialis.engine.util.Rng;
import absentialis.world.Block;

/**
 * Which material goes where (brief section 15). A surface is assigned from five things: the building's style
 * (which carries its type and its district's architecture), the purpose of the room on either side, the surface's
 * orientation (floor, wall, ceiling), its structural role (exterior shell, trim, partition, slab, roof) and the
 * building's state of wear. Nothing is picked from a flat list of "wall blocks": wallpaper can only come out of
 * {@link #partition} for a wall between two lived-in rooms of a home, tile only where a room is wet or clinical,
 * bare concrete only where the building is a works, a barracks or a prison.
 */
public final class MaterialRules {
    private MaterialRules() {}

    /** The finish schedule of one building. */
    public static final class Mat {
        public String style = "";
        public int wall, trim, roof, window, foundation, stair, stairSupport, parapet;
        /** A home: wallpaper, parquet and carpet are allowed inside. */
        public boolean homely;
        /** Offices, shops and public buildings: panel partitions, suspended ceilings. */
        public boolean fitted;
        /** Works, depots, barracks, prisons: exposed concrete and steel. */
        public boolean bare;
        public boolean timber;
        public int wallpaper = Block.WALLPAPER_STRIPE, plaster = Block.PLASTER;
        /** The painted lower wall of this building's corridors and service rooms (0.6): green or blue, one per building. */
        public int paint = Block.PAINT_GREEN;
        /** The bottom course of the outside wall. */
        public int plinth;
    }

    public static Mat of(String style, Rng r) {
        Mat m = new Mat();
        m.style = style;
        m.window = Block.GLASS_DIRTY; m.foundation = Block.CONCRETE; m.stair = Block.STAIRS_CONCRETE; m.stairSupport = Block.CONCRETE;
        m.wallpaper = r.chance(0.5) ? Block.WALLPAPER_FLORAL : Block.WALLPAPER_STRIPE;
        m.plaster = r.pick(new int[]{Block.PLASTER, Block.PLASTER, Block.PLASTER_OCHRE, Block.PLASTER_BLUE});
        switch (style) {
            case "suburban" -> { m.homely = true; m.wall = r.pick(new int[]{Block.BRICK_RED, Block.BRICK_YELLOW, Block.PLASTER, Block.PLASTER_BLUE, Block.PLASTER_OCHRE, Block.BRICK_WHITE, Block.WOOD_PLANKS}); m.trim = m.wall == Block.WOOD_PLANKS ? Block.WOOD_DARK : Block.BRICK_WHITE; m.roof = r.chance(0.6) ? Block.ROOF_TILES : Block.ROOF_SLATE; m.stair = Block.STAIRS_WOOD; m.stairSupport = Block.WOOD_DARK; }
            case "suburban_garage" -> { m.bare = true; m.wall = r.pick(new int[]{Block.BRICK_RED, Block.WOOD_PLANKS, Block.PLASTER}); m.trim = Block.WOOD_DARK; m.roof = Block.ROOF_TILES; }
            case "estate" -> { m.homely = true; m.wall = r.pick(new int[]{Block.STONE_BLOCK, Block.BRICK_BROWN, Block.GRANITE}); m.trim = Block.STONE_CARVED; m.roof = Block.ROOF_SLATE; m.foundation = Block.STONE_DARK; m.stair = Block.STAIRS_WOOD; m.stairSupport = Block.WOOD_DARK; m.wallpaper = Block.WALLPAPER_FLORAL; }
            case "apartment" -> { m.homely = true; m.wall = r.pick(new int[]{Block.CONCRETE_PANEL, Block.CONCRETE_PANEL, Block.BRICK_WHITE, Block.BRICK_RED, Block.PLASTER_OCHRE}); m.trim = Block.CONCRETE; m.roof = Block.ROOF_TAR; }
            case "suburban_shop", "commercial" -> { m.fitted = true; m.wall = r.pick(new int[]{Block.BRICK_BROWN, Block.PLASTER, Block.STONE_BLOCK, Block.BRICK_YELLOW, Block.CONCRETE_PANEL}); m.trim = r.chance(0.5) ? Block.STONE_DARK : Block.CONCRETE; m.roof = Block.ROOF_TAR; m.window = Block.GLASS; }
            case "supermarket" -> { m.fitted = true; m.wall = r.chance(0.5) ? Block.CONCRETE_PANEL : Block.BRICK_WHITE; m.trim = Block.CONCRETE; m.roof = Block.ROOF_TAR; m.window = Block.GLASS; }
            case "office" -> { m.fitted = true; m.wall = r.pick(new int[]{Block.CONCRETE_PANEL, Block.GRANITE, Block.MARBLE}); m.trim = Block.CONCRETE_DARK; m.roof = Block.ROOF_TAR; m.window = Block.GLASS_BLUE; }
            case "civic" -> { m.fitted = true; m.wall = r.pick(new int[]{Block.STONE_BLOCK, Block.GRANITE, Block.BRICK_WHITE}); m.trim = Block.STONE_DARK; m.roof = Block.ROOF_TAR; m.foundation = Block.STONE_DARK; }
            case "industrial" -> { m.bare = true; m.wall = r.pick(new int[]{Block.CORRUGATED, Block.CORRUGATED_BLUE, Block.CORRUGATED_RUST, Block.BRICK_BROWN, Block.CONCRETE_PANEL}); m.trim = Block.STEEL_BEAM; m.roof = Block.ROOF_METAL; m.stair = Block.STAIRS_METAL; m.stairSupport = Block.STEEL_BEAM; }
            case "utility" -> { m.bare = true; m.wall = r.pick(new int[]{Block.CONCRETE, Block.CONCRETE_PANEL, Block.BRICK_BROWN}); m.trim = Block.CONCRETE_DARK; m.roof = Block.ROOF_TAR; }
            case "plant" -> { m.bare = true; m.wall = r.chance(0.5) ? Block.CONCRETE_PANEL : Block.CONCRETE; m.trim = Block.STEEL_BEAM; m.roof = Block.ROOF_METAL; m.stair = Block.STAIRS_METAL; m.stairSupport = Block.STEEL_BEAM; }
            case "military" -> { m.bare = true; m.wall = r.pick(new int[]{Block.CONCRETE, Block.CONCRETE_DARK, Block.CONCRETE_PANEL}); m.trim = Block.CONCRETE_DARK; m.roof = Block.ROOF_TAR; m.foundation = Block.CONCRETE_DARK; }
            case "lab" -> { m.fitted = true; m.wall = r.chance(0.5) ? Block.CONCRETE_PANEL : Block.BRICK_WHITE; m.trim = Block.METAL_PLATE; m.roof = Block.ROOF_TAR; m.window = Block.GLASS_BLUE; }
            case "prison" -> { m.bare = true; m.wall = r.chance(0.6) ? Block.CONCRETE : Block.CONCRETE_STAINED; m.trim = Block.CONCRETE_DARK; m.roof = Block.ROOF_TAR; m.foundation = Block.CONCRETE_DARK; }
            case "airport" -> { m.fitted = true; m.wall = r.chance(0.5) ? Block.CONCRETE_PANEL : Block.BRICK_WHITE; m.trim = Block.METAL_PLATE; m.roof = Block.ROOF_TAR; m.window = Block.GLASS_BLUE; }
            case "airport_hangar", "barn_metal" -> { m.bare = true; m.wall = r.pick(new int[]{Block.CORRUGATED, Block.CORRUGATED_BLUE, Block.CORRUGATED_RUST}); m.trim = Block.STEEL_BEAM; m.roof = Block.ROOF_METAL; }
            case "rural" -> { m.homely = true; m.timber = true; m.wall = r.pick(new int[]{Block.WOOD_PLANKS, Block.PLASTER, Block.WOOD_GREEN, Block.BRICK_RED}); m.trim = Block.WOOD_DARK; m.roof = Block.ROOF_TILES; m.foundation = Block.STONE_BLOCK; m.stair = Block.STAIRS_WOOD; m.stairSupport = Block.WOOD_DARK; }
            case "rural_shop" -> { m.fitted = true; m.timber = true; m.wall = r.pick(new int[]{Block.WOOD_PLANKS, Block.BRICK_RED, Block.PLASTER_OCHRE}); m.trim = Block.WOOD_DARK; m.roof = Block.ROOF_METAL; m.foundation = Block.STONE_BLOCK; m.stair = Block.STAIRS_WOOD; m.stairSupport = Block.WOOD_DARK; }
            case "barn" -> { m.bare = true; m.timber = true; m.wall = r.pick(new int[]{Block.WOOD_GREEN, Block.WOOD_DARK, Block.WOOD_PLANKS}); m.trim = Block.WOOD_DARK; m.roof = Block.ROOF_METAL; m.foundation = Block.STONE_BLOCK; m.stair = Block.STAIRS_WOOD; m.stairSupport = Block.WOOD_DARK; }
            case "glasshouse" -> { m.bare = true; m.wall = Block.GLASS_DIRTY; m.trim = Block.STEEL_BEAM; m.roof = Block.GLASS_DIRTY; m.window = Block.GLASS_DIRTY; }
            default -> { m.fitted = true; m.wall = Block.CONCRETE_PANEL; m.trim = Block.CONCRETE; m.roof = Block.ROOF_TAR; }
        }
        m.parapet = m.trim;
        m.paint = Rng.hash(m.wall, m.plaster, m.wallpaper, 606) < 0.6f ? Block.PAINT_GREEN : Block.PAINT_BLUE;
        // a plinth where the wall is masonry, plaster or panel: darker, harder, what the snow and the boots meet
        m.plinth = m.wall == Block.BRICK_RED || m.wall == Block.BRICK_YELLOW || m.wall == Block.BRICK_WHITE || m.wall == Block.BRICK_BROWN || m.wall == Block.PLASTER || m.wall == Block.PLASTER_BLUE
                || m.wall == Block.PLASTER_OCHRE || m.wall == Block.CONCRETE_PANEL ? (m.foundation == Block.CONCRETE ? Block.CONCRETE_DARK : m.foundation) : m.wall;
        return m;
    }

    /**
     * A partition finish by course (0 at the floor). Paint is laid as it was: gloss to shoulder height, a ruled line,
     * whitewash above. Every other finish is the same all the way up.
     */
    public static int course(int id, int k) {
        if (id == Block.PAINT_GREEN) return k == 0 ? Block.PAINT_GREEN : k == 1 ? Block.DADO_GREEN : Block.PLASTER;
        if (id == Block.PAINT_BLUE) return k == 0 ? Block.PAINT_BLUE : k == 1 ? Block.DADO_BLUE : Block.PLASTER;
        return id;
    }

    public static boolean wet(String t) {
        return switch (t) { case "bathroom", "wc", "shower", "kitchen", "pantry", "laundry", "decon", "infirmary", "lab", "specimen", "coldroom", "containment" -> true; default -> false; };
    }

    private static boolean lived(String t) {
        return switch (t) { case "living", "bedroom", "dining", "lounge", "study", "library", "foyer", "hall", "corridor", "storage", "closet" -> true; default -> false; };
    }

    private static boolean secure(String t) {
        return switch (t) { case "cell", "holding", "armory", "evidence", "depot", "guardroom", "switchgear", "turbine", "pump", "garage", "workshop", "tools", "hall", "hangar", "barn", "tack", "feed" -> true; default -> false; };
    }

    /**
     * The partition between two rooms (either may be null: a wall with nothing known on that side). One voxel
     * shows the same face to both rooms, so the stricter requirement wins: wet beats bare beats fitted beats homely.
     */
    public static int partition(Mat m, String a, String b) {
        boolean wetA = a != null && wet(a), wetB = b != null && wet(b);
        if (wetA || wetB) return Block.TILE_WHITE;
        if (m.timber && m.bare) return Block.WOOD_DARK;
        boolean institution = "prison".equals(m.style) || "military".equals(m.style) || "utility".equals(m.style);
        if (institution) return m.paint;
        // (0.7: in a home the wall to the garage or the tool room is still a wall of the house - plastered, not shuttered concrete)
        if (m.bare || !m.homely && (a != null && secure(a) || b != null && secure(b))) return Block.CONCRETE_DARK;
        if (m.homely) {
            boolean la = a == null || lived(a), lb = b == null || lived(b);
            if (la && lb) return m.timber ? m.plaster : m.wallpaper;
            return m.plaster;
        }
        boolean officeA = a != null && officeLike(a), officeB = b != null && officeLike(b);
        if (officeA && officeB) return Block.OFFICE_PANEL;
        return m.paint;
    }

    private static boolean officeLike(String t) {
        return switch (t) { case "office", "meeting", "briefing", "command", "archive", "radio", "server", "security", "booking", "interview", "breakroom" -> true; default -> false; };
    }

    /** Floor finish for a room. {@code upper}: the block also forms the ceiling of the room below. */
    public static int floor(Mat m, String t, boolean upper, Rng r) {
        int f;
        switch (t) {
            case "living", "dining", "lounge", "library", "study", "foyer" -> f = m.homely && r.chance(0.35) ? Block.CARPET_RED : Block.PARQUET;
            case "bedroom" -> f = r.chance(0.5) ? Block.CARPET_BLUE : Block.PARQUET;
            case "kitchen", "pantry" -> f = Block.TILE_CHECKER;
            case "bathroom", "wc", "shower", "laundry" -> f = Block.TILE_WHITE;
            case "hall", "corridor", "closet" -> f = m.homely ? Block.PARQUET : m.bare ? Block.CONCRETE_FLOOR : Block.LINOLEUM;
            case "office", "meeting", "briefing", "command", "archive", "radio", "mailroom", "breakroom", "interview", "booking", "security", "frontdesk" -> f = m.bare ? Block.LINOLEUM : Block.CARPET_GRAY;
            case "server", "switchgear", "control" -> f = Block.TILE_GRAY;
            case "lab", "decon", "specimen", "containment", "infirmary", "coldroom" -> f = Block.TILE_GREEN;
            case "store", "pharmacy", "supermarket", "cafe", "concourse", "baggage", "lobby", "visiting" -> f = Block.LINOLEUM;
            case "bar" -> f = Block.PARQUET;
            case "barn", "tack", "feed", "greenhouse" -> f = upper ? Block.WOOD_PLANKS : Block.DIRT;
            default -> f = m.timber && m.homely ? Block.PARQUET : Block.CONCRETE_FLOOR;
        }
        return upper ? overCeiling(m, f) : f;
    }

    /** The same finish with a ceiling on its underside, chosen for the room below's kind of building. */
    public static int overCeiling(Mat m, int f) {
        if (f == Block.PARQUET) return m.timber ? Block.BOARDS_C : Block.PARQUET_C;
        if (f == Block.CARPET_RED) return Block.CARPET_RED_C;
        if (f == Block.CARPET_BLUE) return Block.CARPET_BLUE_C;
        if (f == Block.CARPET_GRAY) return Block.CARPET_GRAY_C;
        if (f == Block.TILE_WHITE) return Block.TILE_WHITE_C;
        if (f == Block.TILE_CHECKER) return Block.TILE_CHECKER_C;
        if (f == Block.TILE_GRAY) return Block.TILE_GRAY_C;
        if (f == Block.TILE_GREEN) return Block.TILE_GREEN_C;
        if (f == Block.LINOLEUM) return Block.LINOLEUM_C;
        if (f == Block.WOOD_PLANKS) return Block.BOARDS_C;
        if (f == Block.CONCRETE_FLOOR) return m.homely ? Block.CEILING_PLASTER : m.fitted ? Block.CEILING_PANEL : Block.CONCRETE_FLOOR;
        return f;
    }

    /** The ceiling slab under a roof or a mezzanine. */
    public static int ceiling(Mat m) { return m.homely ? (m.timber ? Block.BOARDS_C : Block.CEILING_PLASTER) : m.fitted ? Block.CEILING_PANEL : m.timber ? Block.BOARDS_C : Block.CONCRETE_FLOOR; }

    /** What hangs in a doorway into a room of this type: wood, metal, or none (an open arch is decided by the plan). */
    public static String door(Mat m, String t) {
        switch (t) {
            case "cell", "holding", "armory", "evidence", "server", "control", "coldroom", "specimen", "containment", "switchgear", "depot", "command", "radio", "decon" -> { return "metal"; }
            default -> { return m.bare && !m.timber ? "metal" : "wood"; }
        }
    }

    /**
     * Audit of a finished building's surfaces (generation validator and the material overlay): returns a short
     * description of the first implausible placement, or null. The rules mirror the brief's examples.
     */
    public static String audit(int block, String role, String roomType, Mat m) {
        boolean wallpaper = block == Block.WALLPAPER_FLORAL || block == Block.WALLPAPER_STRIPE || block == Block.WALLPAPER_TORN;
        if (wallpaper && !"partition".equals(role)) return "wallpaper used as " + role;
        if (wallpaper && !m.homely) return "wallpaper in a " + m.style + " building";
        // (a home's hall is a hallway: lived in. The same word is a factory floor elsewhere - where wallpaper is refused two lines up)
        if (wallpaper && roomType != null && (wet(roomType) || secure(roomType) && !lived(roomType))) return "wallpaper in a " + roomType;
        if ("exterior".equals(role) && (block == Block.OFFICE_PANEL || block == Block.TILE_WHITE || block == Block.TILE_GREEN)) return "interior finish on an exterior wall";
        if ("floor".equals(role) && roomType != null && wet(roomType) && (block == Block.CARPET_RED || block == Block.CARPET_BLUE || block == Block.CARPET_GRAY || block == Block.PARQUET)) return "soft floor in a " + roomType;
        return null;
    }
}
