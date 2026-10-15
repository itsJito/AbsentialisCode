package absentialis.character;

import static absentialis.character.Rig.*;

import absentialis.engine.render.Tex;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Character customization definition tables (iteration P3). The creator is a front-end that
 * selects indices into these tables; a hairstyle is an index pointing at a pre-authored part, and a
 * profile stores only definition ids. Each part declares the SKIN groups it may paint, which
 * {@link #validate()} enforces on import together with bounds, emptiness and left/right pairing.
 */
public final class CharDefs {
    private CharDefs() {}

    public enum Category {
        HEAD("Head / Face"), HAIR("Hair"), FACIAL_HAIR("Facial Hair"), TORSO("Torso"), SLEEVES("Sleeves"), LEGS("Legs"),
        HANDS("Hands / Wrist"), FEET("Feet"), SKIN("Skin Colour"), HAIR_COLOR("Hair Colour");
        public final String label;
        Category(String l) { label = l; }
    }

    /** Colours resolved for one character before its parts are painted. */
    public static final class Ctx {
        public int skin, hair, cloth, cloth2;
        public boolean infected;
    }

    public interface Painter { void paint(CharModel m, Ctx c); }

    public record Part(String id, String name, Category cat, int groups, boolean mayBeEmpty, Painter painter) {}

    private static final Map<Category, List<Part>> TABLE = new EnumMap<>(Category.class);
    private static final Map<String, int[]> TORSO_COLORS = new java.util.HashMap<>();
    public static final int[] SKIN_TONES = {
            Tex.rgb(246, 214, 186), Tex.rgb(236, 196, 160), Tex.rgb(226, 182, 142), Tex.rgb(214, 168, 126), Tex.rgb(198, 148, 106), Tex.rgb(176, 122, 84),
            Tex.rgb(154, 104, 70), Tex.rgb(132, 88, 60), Tex.rgb(112, 74, 52), Tex.rgb(92, 62, 42), Tex.rgb(72, 48, 34), Tex.rgb(56, 38, 28)};
    public static final String[] SKIN_NAMES = {"Porcelain", "Fair", "Light", "Sand", "Tan", "Olive", "Bronze", "Umber", "Chestnut", "Brown", "Deep", "Ebony"};
    public static final int[] HAIR_TONES = {
            Tex.rgb(28, 24, 22), Tex.rgb(58, 40, 26), Tex.rgb(96, 66, 36), Tex.rgb(142, 104, 54), Tex.rgb(196, 164, 96), Tex.rgb(150, 60, 34),
            Tex.rgb(128, 128, 126), Tex.rgb(214, 212, 204), Tex.rgb(44, 70, 110), Tex.rgb(110, 40, 70), Tex.rgb(52, 92, 60), Tex.rgb(86, 82, 78)};
    public static final String[] HAIR_NAMES = {"Black", "Dark brown", "Brown", "Dirty blond", "Blond", "Auburn", "Grey", "White", "Dyed blue", "Dyed plum", "Dyed green", "Ash"};

    /** Share of generated strangers who are women (tunable). */
    public static float FEMALE_SHARE = 0.38f;
    /** Hairstyles only the female body wears, and the ones only the male body does; everything else is shared. */
    private static final java.util.Set<String> HAIR_FEMALE = java.util.Set.of("hair.f_straight", "hair.f_highpony", "hair.f_sidebraid", "hair.f_pixie", "hair.f_wave", "hair.f_chignon", "hair.bob", "hair.braids");
    private static final java.util.Set<String> HAIR_MALE = java.util.Set.of("hair.crew", "hair.sidepart", "hair.slick", "hair.mohawk", "hair.undercut", "hair.buzz", "hair.bald");

    /** May this body wear option {@code index} of a category? (Hair differs by sex; a female face has no beard.) */
    public static boolean allowed(Category c, int index, boolean female) {
        String id = part(c, index).id();
        if (c == Category.HAIR) return female ? !HAIR_MALE.contains(id) : !HAIR_FEMALE.contains(id);
        if (c == Category.FACIAL_HAIR) return !female || id.endsWith(".none");
        return true;
    }

    public static List<Part> parts(Category c) { return TABLE.get(c); }
    public static int count(Category c) { return TABLE.get(c).size(); }
    public static Part part(Category c, int index) { List<Part> l = TABLE.get(c); return l.get(Math.floorMod(index, l.size())); }
    public static int indexOf(Category c, String id) { List<Part> l = TABLE.get(c); for (int i = 0; i < l.size(); i++) if (l.get(i).id().equals(id)) return i; return 0; }

    private static void add(Category cat, String id, String name, int groups, boolean empty, Painter p) {
        TABLE.computeIfAbsent(cat, k -> new ArrayList<>()).add(new Part(cat.name().toLowerCase() + "." + id, name, cat, groups, empty, p));
    }

    private static final int G_HEAD = bit(HEAD), G_TORSO = bit(TORSO);
    private static final int G_ARMS = bit(L_UPPER_ARM) | bit(L_FOREARM) | bit(R_UPPER_ARM) | bit(R_FOREARM);
    private static final int G_LEGS = bit(L_THIGH) | bit(L_SHIN) | bit(R_THIGH) | bit(R_SHIN);
    private static final int G_HANDS = bit(L_HAND) | bit(R_HAND) | bit(L_FOREARM) | bit(R_FOREARM);
    private static final int G_FEET = bit(L_SHIN) | bit(R_SHIN);
    private static final int BLACK = Tex.rgb(26, 26, 28), WHITE = Tex.rgb(226, 224, 216), DARK = Tex.rgb(44, 40, 36);

    private static int dk(int c, float f) { return Tex.shade(c, f); }

    // ================================================================== the naked body (always painted first)
    /** Skin-coloured base volume of every SKIN group; garments recolour or wrap it, never remove it. */
    public static void body(CharModel m, Ctx c) {
        m.box(HEAD, 2, 2, 2, 9, 9, 9, c.skin);
        m.box(TORSO, 3, 0, 4, 10, 11, 7, c.skin);
        m.both(L_UPPER_ARM, 1, 1, 1, 4, 5, 4, c.skin);
        m.both(L_FOREARM, 1, 0, 1, 4, 4, 4, c.skin);
        m.both(L_HAND, 1, 0, 1, 4, 1, 4, c.skin);
        m.both(L_THIGH, 1, 0, 1, 4, 5, 4, c.skin);
        m.both(L_SHIN, 1, 0, 2, 4, 5, 5, c.skin);
        // underwear so "bare" options stay decent
        m.tint(TORSO, 3, 0, 4, 10, 1, 7, Tex.rgb(60, 62, 70));
        m.tintBoth(L_THIGH, 1, 4, 1, 4, 5, 4, Tex.rgb(60, 62, 70));
    }

    /** Eyes, brows, nose and mouth shared by every head variant. */
    private static void face(CharModel m, Ctx c, int eye) {
        int brow = dk(c.hair, 0.8f), lip = Tex.mix(c.skin, Tex.rgb(150, 70, 70), 0.45f);
        m.set(HEAD, 4, 6, 2, eye); m.set(HEAD, 7, 6, 2, eye);
        m.set(HEAD, 3, 6, 2, WHITE); m.set(HEAD, 8, 6, 2, WHITE);
        m.box(HEAD, 3, 7, 2, 4, 7, 2, brow); m.box(HEAD, 7, 7, 2, 8, 7, 2, brow);
        m.box(HEAD, 5, 4, 1, 6, 5, 1, dk(c.skin, 0.93f));
        m.box(HEAD, 5, 3, 2, 6, 3, 2, lip);
        m.box(HEAD, 1, 5, 5, 1, 6, 6, dk(c.skin, 0.9f)); m.box(HEAD, 10, 5, 5, 10, 6, 6, dk(c.skin, 0.9f)); // ears
    }

    static {
        // ---------------------------------------------------------------- SKIN / HAIR COLOUR (palette indices)
        for (int i = 0; i < SKIN_TONES.length; i++) add(Category.SKIN, "tone" + i, SKIN_NAMES[i], 0, true, (m, c) -> {});
        for (int i = 0; i < HAIR_TONES.length; i++) add(Category.HAIR_COLOR, "tone" + i, HAIR_NAMES[i], 0, true, (m, c) -> {});

        // ---------------------------------------------------------------- HEAD / FACE
        int eyeBrown = Tex.rgb(60, 40, 26), eyeBlue = Tex.rgb(70, 110, 150), eyeGreen = Tex.rgb(70, 120, 80), eyeGrey = Tex.rgb(110, 116, 120);
        add(Category.HEAD, "plain", "Plain", G_HEAD, false, (m, c) -> face(m, c, eyeBrown));
        add(Category.HEAD, "brow", "Heavy brow", G_HEAD, false, (m, c) -> { face(m, c, eyeBrown); m.box(HEAD, 3, 7, 1, 8, 7, 1, dk(c.skin, 0.82f)); });
        add(Category.HEAD, "scar_l", "Scar, left cheek", G_HEAD, false, (m, c) -> { face(m, c, eyeGrey); for (int k = 0; k < 4; k++) m.set(HEAD, 2 + k / 2, 3 + k, 2, Tex.mix(c.skin, Tex.rgb(200, 120, 120), 0.6f)); });
        add(Category.HEAD, "scar_r", "Scar, right brow", G_HEAD, false, (m, c) -> { face(m, c, eyeBlue); for (int k = 0; k < 4; k++) m.set(HEAD, 8 + k / 3, 5 + k, 2, Tex.mix(c.skin, Tex.rgb(200, 120, 120), 0.6f)); });
        add(Category.HEAD, "freckles", "Freckled", G_HEAD, false, (m, c) -> { face(m, c, eyeGreen); for (int x = 2; x <= 9; x++) for (int y = 4; y <= 5; y++) if (CharModel.h(x, y, 3, 11) < 0.42f && (x < 5 || x > 6)) m.set(HEAD, x, y, 2, dk(c.skin, 0.84f)); });
        add(Category.HEAD, "weathered", "Weathered", G_HEAD, false, (m, c) -> { face(m, c, eyeGrey); m.box(HEAD, 2, 3, 2, 2, 5, 2, dk(c.skin, 0.86f)); m.box(HEAD, 9, 3, 2, 9, 5, 2, dk(c.skin, 0.86f)); m.box(HEAD, 3, 8, 2, 8, 8, 2, dk(c.skin, 0.92f)); });
        add(Category.HEAD, "gaunt", "Gaunt", G_HEAD, false, (m, c) -> { face(m, c, eyeBlue); m.box(HEAD, 3, 4, 2, 3, 5, 2, dk(c.skin, 0.8f)); m.box(HEAD, 8, 4, 2, 8, 5, 2, dk(c.skin, 0.8f)); m.box(HEAD, 2, 2, 2, 2, 3, 9, 0); m.box(HEAD, 9, 2, 2, 9, 3, 9, 0); });
        add(Category.HEAD, "broad", "Broad jaw", G_HEAD, false, (m, c) -> { face(m, c, eyeBrown); m.box(HEAD, 1, 2, 3, 1, 4, 7, c.skin); m.box(HEAD, 10, 2, 3, 10, 4, 7, c.skin); m.box(HEAD, 3, 1, 2, 8, 1, 6, c.skin); });
        add(Category.HEAD, "eyepatch", "Eyepatch", G_HEAD, false, (m, c) -> { face(m, c, eyeBrown); m.box(HEAD, 3, 6, 1, 4, 7, 1, BLACK); m.box(HEAD, 2, 8, 1, 9, 8, 1, BLACK); m.box(HEAD, 1, 8, 2, 1, 8, 9, BLACK); m.box(HEAD, 10, 8, 2, 10, 8, 9, BLACK); m.box(HEAD, 2, 8, 10, 9, 8, 10, BLACK); });
        add(Category.HEAD, "boxer", "Broken nose", G_HEAD, false, (m, c) -> { face(m, c, eyeGreen); m.box(HEAD, 5, 4, 1, 6, 5, 1, 0); m.box(HEAD, 5, 4, 1, 5, 5, 1, dk(c.skin, 0.9f)); m.set(HEAD, 6, 5, 1, dk(c.skin, 0.9f)); m.box(HEAD, 7, 5, 2, 8, 5, 2, Tex.mix(c.skin, Tex.rgb(90, 60, 110), 0.5f)); });
        add(Category.HEAD, "tattoo", "Inked", G_HEAD, false, (m, c) -> { face(m, c, eyeBrown); int ink = Tex.mix(c.skin, Tex.rgb(30, 50, 80), 0.7f); m.box(HEAD, 9, 2, 3, 9, 6, 3, ink); m.set(HEAD, 8, 4, 2, ink); m.set(HEAD, 9, 7, 5, ink); m.box(HEAD, 9, 3, 6, 9, 4, 7, ink); });
        add(Category.HEAD, "aged", "Aged", G_HEAD, false, (m, c) -> { face(m, c, eyeGrey); m.box(HEAD, 3, 7, 2, 4, 7, 2, Tex.rgb(170, 168, 160)); m.box(HEAD, 7, 7, 2, 8, 7, 2, Tex.rgb(170, 168, 160)); m.box(HEAD, 3, 5, 2, 4, 5, 2, dk(c.skin, 0.85f)); m.box(HEAD, 7, 5, 2, 8, 5, 2, dk(c.skin, 0.85f)); m.box(HEAD, 4, 8, 2, 7, 8, 2, dk(c.skin, 0.9f)); m.set(HEAD, 4, 3, 2, dk(c.skin, 0.85f)); m.set(HEAD, 7, 3, 2, dk(c.skin, 0.85f)); });
        add(Category.HEAD, "round", "Soft features", G_HEAD, false, (m, c) -> { face(m, c, eyeBlue); m.box(HEAD, 2, 9, 2, 2, 9, 9, 0); m.box(HEAD, 9, 9, 2, 9, 9, 9, 0); m.box(HEAD, 3, 4, 2, 3, 4, 2, Tex.mix(c.skin, Tex.rgb(220, 130, 120), 0.3f)); m.box(HEAD, 8, 4, 2, 8, 4, 2, Tex.mix(c.skin, Tex.rgb(220, 130, 120), 0.3f)); });

        // ---------------------------------------------------------------- HAIR (all skinned to HEAD: it follows the head animation for free)
        add(Category.HAIR, "bald", "Bald", G_HEAD, true, (m, c) -> {});
        add(Category.HAIR, "buzz", "Buzz cut", G_HEAD, false, (m, c) -> { int k = Tex.mix(c.skin, c.hair, 0.6f); m.tint(HEAD, 2, 9, 2, 9, 9, 9, k); m.tint(HEAD, 2, 7, 9, 9, 8, 9, k); m.tint(HEAD, 2, 8, 4, 2, 8, 9, k); m.tint(HEAD, 9, 8, 4, 9, 8, 9, k); });
        add(Category.HAIR, "crew", "Crew cut", G_HEAD, false, (m, c) -> { m.box(HEAD, 2, 10, 2, 9, 10, 9, c.hair); m.box(HEAD, 3, 11, 2, 8, 11, 4, c.hair); m.box(HEAD, 2, 7, 10, 9, 9, 10, c.hair); m.box(HEAD, 1, 8, 5, 1, 9, 9, c.hair); m.box(HEAD, 10, 8, 5, 10, 9, 9, c.hair); });
        add(Category.HAIR, "sidepart", "Side part", G_HEAD, false, (m, c) -> { m.box(HEAD, 2, 10, 2, 9, 10, 9, c.hair); m.box(HEAD, 2, 11, 2, 6, 11, 8, c.hair); m.box(HEAD, 1, 6, 4, 1, 10, 9, c.hair); m.box(HEAD, 10, 7, 5, 10, 10, 9, c.hair); m.box(HEAD, 2, 5, 10, 9, 10, 10, c.hair); m.box(HEAD, 2, 9, 1, 5, 9, 1, c.hair); });
        add(Category.HAIR, "slick", "Slicked back", G_HEAD, false, (m, c) -> { m.box(HEAD, 2, 10, 2, 9, 10, 9, c.hair); m.box(HEAD, 3, 11, 4, 8, 11, 10, c.hair); m.box(HEAD, 2, 4, 10, 9, 10, 10, c.hair); m.box(HEAD, 1, 7, 5, 1, 10, 9, c.hair); m.box(HEAD, 10, 7, 5, 10, 10, 9, c.hair); });
        add(Category.HAIR, "mohawk", "Mohawk", G_HEAD, false, (m, c) -> { m.box(HEAD, 5, 10, 2, 6, 12, 9, c.hair); m.box(HEAD, 5, 13, 3, 6, 13, 8, c.hair); m.box(HEAD, 5, 6, 10, 6, 11, 10, c.hair); int k = Tex.mix(c.skin, c.hair, 0.35f); m.tint(HEAD, 2, 9, 2, 4, 9, 9, k); m.tint(HEAD, 7, 9, 2, 9, 9, 9, k); });
        add(Category.HAIR, "ponytail", "Ponytail", G_HEAD, false, (m, c) -> { m.box(HEAD, 2, 10, 2, 9, 10, 9, c.hair); m.box(HEAD, 2, 6, 10, 9, 10, 10, c.hair); m.box(HEAD, 1, 7, 4, 1, 10, 9, c.hair); m.box(HEAD, 10, 7, 4, 10, 10, 9, c.hair); m.box(HEAD, 5, 3, 11, 6, 9, 11, c.hair); m.box(HEAD, 5, 8, 10, 6, 8, 11, dk(c.hair, 0.6f)); });
        add(Category.HAIR, "bun", "Top bun", G_HEAD, false, (m, c) -> { m.box(HEAD, 2, 10, 2, 9, 10, 9, c.hair); m.box(HEAD, 2, 6, 10, 9, 10, 10, c.hair); m.box(HEAD, 1, 7, 4, 1, 10, 9, c.hair); m.box(HEAD, 10, 7, 4, 10, 10, 9, c.hair); m.box(HEAD, 4, 11, 6, 7, 12, 9, c.hair); m.box(HEAD, 5, 13, 7, 6, 13, 8, c.hair); });
        add(Category.HAIR, "bob", "Bob", G_HEAD, false, (m, c) -> { m.box(HEAD, 2, 10, 2, 9, 10, 9, c.hair); m.box(HEAD, 1, 3, 3, 1, 10, 9, c.hair); m.box(HEAD, 10, 3, 3, 10, 10, 9, c.hair); m.box(HEAD, 2, 3, 10, 9, 10, 10, c.hair); m.box(HEAD, 2, 9, 1, 9, 9, 1, c.hair); });
        add(Category.HAIR, "long", "Long", G_HEAD, false, (m, c) -> { m.box(HEAD, 2, 10, 2, 9, 10, 9, c.hair); m.box(HEAD, 1, 1, 3, 1, 10, 9, c.hair); m.box(HEAD, 10, 1, 3, 10, 10, 9, c.hair); m.box(HEAD, 2, 0, 10, 9, 10, 10, c.hair); m.box(HEAD, 3, 0, 11, 8, 6, 11, c.hair); m.box(HEAD, 2, 9, 1, 4, 9, 1, c.hair); m.box(HEAD, 7, 9, 1, 9, 9, 1, c.hair); });
        add(Category.HAIR, "curly", "Curls", G_HEAD, false, (m, c) -> { for (int x = 0; x < 12; x++) for (int y = 6; y < 14; y++) for (int z = 1; z < 12; z++) { float dx = x + 0.5f - 6, dy = y + 0.5f - 8.4f, dz = z + 0.5f - 6.6f; if (dx * dx + dy * dy * 1.3f + dz * dz <= 33 && m.get(HEAD, x, y, z) == 0 && !(z <= 2 && y <= 9) && CharModel.h(x, y, z, 5) > 0.12f) m.set(HEAD, x, y, z, c.hair); } });
        add(Category.HAIR, "undercut", "Undercut", G_HEAD, false, (m, c) -> { m.box(HEAD, 2, 10, 2, 9, 11, 9, c.hair); m.box(HEAD, 2, 12, 2, 6, 12, 7, c.hair); m.box(HEAD, 2, 9, 1, 6, 10, 1, c.hair); int k = Tex.mix(c.skin, c.hair, 0.4f); m.tint(HEAD, 2, 6, 9, 9, 9, 9, k); m.tint(HEAD, 2, 7, 5, 2, 9, 8, k); m.tint(HEAD, 9, 7, 5, 9, 9, 8, k); });
        add(Category.HAIR, "braids", "Twin braids", G_HEAD, false, (m, c) -> { m.box(HEAD, 2, 10, 2, 9, 10, 9, c.hair); m.box(HEAD, 1, 6, 4, 1, 10, 9, c.hair); m.box(HEAD, 10, 6, 4, 10, 10, 9, c.hair); m.box(HEAD, 2, 6, 10, 9, 10, 10, c.hair); for (int y = 0; y <= 6; y++) { int k = y % 2 == 0 ? c.hair : dk(c.hair, 0.75f); m.set(HEAD, 2, y, 10, k); m.set(HEAD, 9, y, 10, k); } m.set(HEAD, 2, 0, 10, Tex.rgb(150, 40, 40)); m.set(HEAD, 9, 0, 10, Tex.rgb(150, 40, 40)); });
        add(Category.HAIR, "wild", "Matted", G_HEAD, false, (m, c) -> { m.box(HEAD, 2, 10, 2, 9, 10, 9, c.hair); for (int x = 1; x <= 10; x++) for (int z = 2; z <= 10; z++) if (CharModel.h(x, 1, z, 9) < 0.5f) m.shell(HEAD, x, 11, z, x, 11, z, c.hair); for (int x = 1; x <= 10; x += 9) for (int z = 3; z <= 9; z++) { int len = 2 + (int) (CharModel.h(x, 2, z, 9) * 6); m.box(HEAD, x, 10 - len, z, x, 10, z, c.hair); } for (int x = 2; x <= 9; x++) { int len = 3 + (int) (CharModel.h(x, 3, 10, 9) * 7); m.box(HEAD, x, 10 - len, 10, x, 10, 10, c.hair); } });
        add(Category.HAIR, "fringe", "Fringe", G_HEAD, false, (m, c) -> { m.box(HEAD, 2, 10, 2, 9, 10, 9, c.hair); m.box(HEAD, 2, 8, 1, 9, 9, 1, c.hair); m.box(HEAD, 1, 5, 3, 1, 10, 9, c.hair); m.box(HEAD, 10, 5, 3, 10, 10, 9, c.hair); m.box(HEAD, 2, 4, 10, 9, 10, 10, c.hair); });

        // ---- 0.6: hairstyles for the female body (appended, so every saved index before them still means what it did)
        add(Category.HAIR, "f_straight", "Long straight", G_HEAD, false, (m, c) -> { m.box(HEAD, 2, 10, 2, 9, 10, 9, c.hair); m.box(HEAD, 1, 0, 3, 1, 10, 9, c.hair); m.box(HEAD, 10, 0, 3, 10, 10, 9, c.hair); m.box(HEAD, 2, 0, 10, 9, 10, 10, c.hair); m.box(HEAD, 2, 9, 1, 3, 9, 1, c.hair); m.box(HEAD, 8, 9, 1, 9, 9, 1, c.hair); m.box(HEAD, 5, 11, 3, 6, 11, 9, dk(c.hair, 0.86f)); });
        add(Category.HAIR, "f_highpony", "High ponytail", G_HEAD, false, (m, c) -> { m.box(HEAD, 2, 10, 2, 9, 10, 9, c.hair); m.box(HEAD, 2, 6, 10, 9, 10, 10, c.hair); m.box(HEAD, 1, 8, 4, 1, 10, 9, c.hair); m.box(HEAD, 10, 8, 4, 10, 10, 9, c.hair); m.box(HEAD, 5, 11, 8, 6, 12, 10, c.hair); m.box(HEAD, 5, 4, 11, 6, 12, 11, c.hair); m.box(HEAD, 5, 10, 10, 6, 10, 10, Tex.rgb(170, 40, 50)); });
        add(Category.HAIR, "f_sidebraid", "Side braid", G_HEAD, false, (m, c) -> { m.box(HEAD, 2, 10, 2, 9, 10, 9, c.hair); m.box(HEAD, 2, 6, 10, 9, 10, 10, c.hair); m.box(HEAD, 1, 6, 3, 1, 10, 9, c.hair); m.box(HEAD, 10, 7, 4, 10, 10, 9, c.hair); m.box(HEAD, 2, 9, 1, 5, 9, 1, c.hair); for (int y = 0; y <= 5; y++) m.set(HEAD, 1, y, 3 + (y & 1), (y & 1) == 0 ? c.hair : dk(c.hair, 0.8f)); });
        add(Category.HAIR, "f_pixie", "Pixie cut", G_HEAD, false, (m, c) -> { m.box(HEAD, 2, 10, 2, 9, 10, 9, c.hair); m.box(HEAD, 3, 11, 3, 8, 11, 8, c.hair); m.box(HEAD, 2, 8, 10, 9, 10, 10, c.hair); m.box(HEAD, 1, 8, 4, 1, 10, 9, c.hair); m.box(HEAD, 10, 8, 4, 10, 10, 9, c.hair); m.box(HEAD, 2, 9, 1, 6, 9, 1, c.hair); m.set(HEAD, 2, 8, 1, c.hair); });
        add(Category.HAIR, "f_wave", "Shoulder waves", G_HEAD, false, (m, c) -> { m.box(HEAD, 2, 10, 2, 9, 10, 9, c.hair); m.box(HEAD, 2, 2, 10, 9, 10, 10, c.hair); for (int y = 2; y <= 10; y++) { int o = (y / 2) & 1; m.box(HEAD, 1 - o, y, 3, 1, y, 9, c.hair); m.box(HEAD, 10, y, 3, 10 + o, y, 9, c.hair); } m.box(HEAD, 2, 9, 1, 4, 9, 1, c.hair); m.box(HEAD, 7, 9, 1, 9, 9, 1, c.hair); });
        add(Category.HAIR, "f_chignon", "Low bun", G_HEAD, false, (m, c) -> { m.box(HEAD, 2, 10, 2, 9, 10, 9, c.hair); m.box(HEAD, 2, 5, 10, 9, 10, 10, c.hair); m.box(HEAD, 1, 7, 4, 1, 10, 9, c.hair); m.box(HEAD, 10, 7, 4, 10, 10, 9, c.hair); m.box(HEAD, 4, 4, 11, 7, 6, 11, c.hair); m.box(HEAD, 5, 5, 11, 6, 5, 11, dk(c.hair, 0.8f)); });

        // ---------------------------------------------------------------- FACIAL HAIR
        add(Category.FACIAL_HAIR, "none", "Clean shaven", G_HEAD, true, (m, c) -> {});
        add(Category.FACIAL_HAIR, "stubble", "Stubble", G_HEAD, false, (m, c) -> { int k = Tex.mix(c.skin, c.hair, 0.32f); for (int x = 2; x <= 9; x++) for (int y = 2; y <= 4; y++) if (!(y == 3 && (x == 5 || x == 6))) m.tint(HEAD, x, y, 2, x, y, 2, k); m.tint(HEAD, 2, 2, 3, 2, 4, 5, k); m.tint(HEAD, 9, 2, 3, 9, 4, 5, k); });
        add(Category.FACIAL_HAIR, "moustache", "Moustache", G_HEAD, false, (m, c) -> m.box(HEAD, 4, 4, 1, 7, 4, 1, c.hair));
        add(Category.FACIAL_HAIR, "handlebar", "Handlebar", G_HEAD, false, (m, c) -> { m.box(HEAD, 3, 4, 1, 8, 4, 1, c.hair); m.set(HEAD, 3, 3, 1, c.hair); m.set(HEAD, 8, 3, 1, c.hair); });
        add(Category.FACIAL_HAIR, "goatee", "Goatee", G_HEAD, false, (m, c) -> { m.box(HEAD, 5, 2, 1, 6, 2, 1, c.hair); m.box(HEAD, 4, 1, 2, 7, 1, 3, c.hair); });
        add(Category.FACIAL_HAIR, "soulpatch", "Soul patch", G_HEAD, false, (m, c) -> m.box(HEAD, 5, 2, 1, 6, 2, 1, c.hair));
        add(Category.FACIAL_HAIR, "chinstrap", "Chinstrap", G_HEAD, false, (m, c) -> { m.box(HEAD, 1, 2, 3, 1, 5, 6, c.hair); m.box(HEAD, 10, 2, 3, 10, 5, 6, c.hair); m.box(HEAD, 2, 1, 2, 9, 1, 5, c.hair); });
        add(Category.FACIAL_HAIR, "short", "Short beard", G_HEAD, false, (m, c) -> { m.box(HEAD, 3, 2, 1, 8, 2, 1, c.hair); m.box(HEAD, 2, 1, 2, 9, 1, 5, c.hair); m.box(HEAD, 1, 2, 2, 1, 4, 6, c.hair); m.box(HEAD, 10, 2, 2, 10, 4, 6, c.hair); m.set(HEAD, 3, 3, 1, c.hair); m.set(HEAD, 8, 3, 1, c.hair); });
        add(Category.FACIAL_HAIR, "full", "Full beard", G_HEAD, false, (m, c) -> { m.box(HEAD, 3, 2, 1, 8, 2, 1, c.hair); m.box(HEAD, 3, 4, 1, 8, 4, 1, c.hair); m.set(HEAD, 3, 3, 1, c.hair); m.set(HEAD, 4, 3, 1, c.hair); m.set(HEAD, 7, 3, 1, c.hair); m.set(HEAD, 8, 3, 1, c.hair); m.box(HEAD, 2, 0, 1, 9, 1, 5, c.hair); m.box(HEAD, 1, 1, 2, 1, 5, 7, c.hair); m.box(HEAD, 10, 1, 2, 10, 5, 7, c.hair); });
        add(Category.FACIAL_HAIR, "long", "Long beard", G_HEAD, false, (m, c) -> { m.box(HEAD, 3, 2, 1, 8, 2, 1, c.hair); m.box(HEAD, 3, 4, 1, 8, 4, 1, c.hair); m.set(HEAD, 3, 3, 1, c.hair); m.set(HEAD, 8, 3, 1, c.hair); m.box(HEAD, 2, 0, 1, 9, 1, 5, c.hair); m.box(HEAD, 3, 0, 0, 8, 1, 0, c.hair); m.box(HEAD, 1, 1, 2, 1, 5, 7, c.hair); m.box(HEAD, 10, 1, 2, 10, 5, 7, c.hair); });
        add(Category.FACIAL_HAIR, "chops", "Mutton chops", G_HEAD, false, (m, c) -> { m.box(HEAD, 1, 2, 2, 1, 6, 6, c.hair); m.box(HEAD, 10, 2, 2, 10, 6, 6, c.hair); m.box(HEAD, 2, 3, 1, 3, 4, 1, c.hair); m.box(HEAD, 8, 3, 1, 9, 4, 1, c.hair); });
        add(Category.FACIAL_HAIR, "vandyke", "Van Dyke", G_HEAD, false, (m, c) -> { m.box(HEAD, 4, 4, 1, 7, 4, 1, c.hair); m.box(HEAD, 5, 2, 1, 6, 2, 1, c.hair); m.box(HEAD, 5, 1, 2, 6, 1, 3, c.hair); m.set(HEAD, 4, 3, 1, c.hair); m.set(HEAD, 7, 3, 1, c.hair); });

        // ---------------------------------------------------------------- TORSO (sets the cloth colours sleeves inherit)
        torso("tee", "T-shirt", Tex.rgb(96, 98, 100), (m, c) -> { cloth(m, c.cloth); m.box(TORSO, 6, 11, 4, 7, 11, 4, c.skin); });
        torso("field", "Field shirt", Tex.rgb(86, 94, 62), (m, c) -> { cloth(m, c.cloth); m.box(TORSO, 4, 7, 4, 5, 8, 4, c.cloth2); m.box(TORSO, 8, 7, 4, 9, 8, 4, c.cloth2); m.box(TORSO, 6, 2, 4, 7, 10, 4, c.cloth2); m.box(TORSO, 5, 12, 4, 8, 12, 5, c.cloth); });
        torso("flannel", "Flannel", Tex.rgb(150, 52, 44), (m, c) -> { cloth(m, c.cloth); for (int x = 3; x <= 10; x++) for (int y = 2; y <= 11; y++) for (int z = 4; z <= 7; z++) if (m.get(TORSO, x, y, z) != 0 && (x % 3 == 0 || y % 3 == 0)) m.set(TORSO, x, y, z, (x % 3 == 0 && y % 3 == 0) ? BLACK : c.cloth2); });
        torso("hoodie", "Hoodie", Tex.rgb(64, 70, 86), (m, c) -> { cloth(m, c.cloth); m.box(TORSO, 3, 3, 3, 10, 5, 3, c.cloth2); m.box(TORSO, 4, 9, 8, 9, 12, 8, c.cloth); m.box(TORSO, 4, 12, 5, 9, 12, 8, c.cloth); m.box(TORSO, 4, 12, 3, 4, 12, 4, c.cloth); m.box(TORSO, 9, 12, 3, 9, 12, 4, c.cloth); m.box(TORSO, 6, 9, 3, 6, 10, 3, WHITE); m.box(TORSO, 7, 9, 3, 7, 10, 3, WHITE); });
        torso("workjacket", "Work jacket", Tex.rgb(118, 86, 56), (m, c) -> { cloth(m, Tex.rgb(180, 176, 164)); m.shell(TORSO, 2, 2, 3, 11, 11, 8, c.cloth); m.box(TORSO, 6, 2, 3, 7, 11, 3, 0); m.box(TORSO, 3, 6, 3, 4, 7, 3, c.cloth2); m.box(TORSO, 9, 6, 3, 10, 7, 3, c.cloth2); m.box(TORSO, 4, 12, 3, 5, 12, 8, c.cloth2); m.box(TORSO, 8, 12, 3, 9, 12, 8, c.cloth2); });
        torso("tracksuit", "Tracksuit top", Tex.rgb(30, 44, 96), (m, c) -> { cloth(m, c.cloth); m.box(TORSO, 3, 10, 4, 4, 11, 7, WHITE); m.box(TORSO, 9, 10, 4, 10, 11, 7, WHITE); m.box(TORSO, 6, 2, 4, 6, 11, 4, WHITE); m.box(TORSO, 5, 12, 4, 8, 12, 7, c.cloth); });
        torso("sweater", "Knit sweater", Tex.rgb(126, 110, 84), (m, c) -> { cloth(m, c.cloth); for (int y = 2; y <= 11; y += 2) m.tint(TORSO, 3, y, 4, 10, y, 7, c.cloth2); m.box(TORSO, 5, 12, 4, 8, 12, 7, c.cloth2); m.tint(TORSO, 3, 2, 4, 10, 2, 7, dk(c.cloth, 0.6f)); });
        torso("leather", "Leather jacket", Tex.rgb(46, 38, 34), (m, c) -> { cloth(m, Tex.rgb(70, 70, 74)); m.shell(TORSO, 2, 2, 3, 11, 11, 8, c.cloth); m.box(TORSO, 6, 4, 3, 7, 11, 3, 0); m.box(TORSO, 5, 9, 3, 5, 11, 3, c.cloth2); m.box(TORSO, 8, 9, 3, 8, 11, 3, c.cloth2); m.box(TORSO, 5, 2, 3, 8, 3, 3, c.cloth); m.box(TORSO, 6, 4, 3, 6, 8, 3, Tex.rgb(150, 150, 150)); });
        torso("parka", "Parka", Tex.rgb(74, 84, 62), (m, c) -> { cloth(m, c.cloth); m.shell(TORSO, 2, 0, 3, 11, 12, 8, c.cloth); m.box(TORSO, 3, 13, 3, 10, 13, 8, Tex.rgb(170, 150, 120)); m.box(TORSO, 4, 13, 4, 9, 13, 7, 0); m.box(TORSO, 6, 1, 3, 6, 11, 3, c.cloth2); m.box(TORSO, 3, 3, 3, 4, 4, 3, c.cloth2); m.box(TORSO, 9, 3, 3, 10, 4, 3, c.cloth2); });
        torso("telnyashka", "Telnyashka", Tex.rgb(226, 226, 220), (m, c) -> { cloth(m, c.cloth); for (int y = 2; y <= 11; y += 2) m.tint(TORSO, 3, y, 4, 10, y, 7, Tex.rgb(36, 52, 110)); m.box(TORSO, 6, 11, 4, 7, 11, 4, c.skin); });
        torso("hivis", "Hi-vis vest", Tex.rgb(70, 72, 78), (m, c) -> { cloth(m, c.cloth); int o = Tex.rgb(236, 130, 30), s = Tex.rgb(216, 220, 210); m.shell(TORSO, 2, 2, 3, 11, 11, 8, o); m.box(TORSO, 6, 2, 3, 7, 11, 3, 0); m.box(TORSO, 2, 4, 3, 5, 4, 3, s); m.box(TORSO, 8, 4, 3, 11, 4, 3, s); m.box(TORSO, 2, 4, 8, 11, 4, 8, s); m.box(TORSO, 2, 8, 8, 11, 8, 8, s); m.box(TORSO, 2, 8, 3, 5, 8, 3, s); m.box(TORSO, 8, 8, 3, 11, 8, 3, s); });
        torso("tank", "Tank top", Tex.rgb(196, 194, 184), (m, c) -> { m.tint(TORSO, 3, 0, 4, 10, 9, 7, c.cloth); m.tint(TORSO, 4, 10, 4, 4, 11, 7, c.cloth); m.tint(TORSO, 9, 10, 4, 9, 11, 7, c.cloth); m.tint(TORSO, 3, 0, 4, 10, 1, 7, Tex.rgb(40, 34, 28)); });
        torso("puffer", "Puffer vest", Tex.rgb(96, 40, 36), (m, c) -> { cloth(m, Tex.rgb(60, 62, 68)); m.shell(TORSO, 2, 2, 3, 11, 11, 8, c.cloth); for (int y = 4; y <= 10; y += 3) { m.box(TORSO, 2, y, 3, 11, y, 3, c.cloth2); m.box(TORSO, 2, y, 8, 11, y, 8, c.cloth2); } m.box(TORSO, 6, 2, 3, 6, 11, 3, BLACK); });

        // ---------------------------------------------------------------- SLEEVES (upper arm + forearm, mirrored)
        add(Category.SLEEVES, "bare", "Bare arms", G_ARMS, true, (m, c) -> {});
        add(Category.SLEEVES, "short", "Short sleeves", G_ARMS, false, (m, c) -> m.tintBoth(L_UPPER_ARM, 1, 3, 1, 4, 5, 4, c.cloth));
        add(Category.SLEEVES, "rolled", "Rolled sleeves", G_ARMS, false, (m, c) -> { m.tintBoth(L_UPPER_ARM, 1, 1, 1, 4, 5, 4, c.cloth); m.shellBoth(L_FOREARM, 0, 4, 0, 5, 4, 5, c.cloth2); m.tintBoth(L_FOREARM, 1, 4, 1, 4, 4, 4, c.cloth2); });
        add(Category.SLEEVES, "long", "Long sleeves", G_ARMS, false, (m, c) -> { m.tintBoth(L_UPPER_ARM, 1, 1, 1, 4, 5, 4, c.cloth); m.tintBoth(L_FOREARM, 1, 0, 1, 4, 4, 4, c.cloth); m.tintBoth(L_FOREARM, 1, 0, 1, 4, 0, 4, c.cloth2); });
        add(Category.SLEEVES, "patched", "Elbow patches", G_ARMS, false, (m, c) -> { m.tintBoth(L_UPPER_ARM, 1, 1, 1, 4, 5, 4, c.cloth); m.tintBoth(L_FOREARM, 1, 0, 1, 4, 4, 4, c.cloth); m.tintBoth(L_UPPER_ARM, 2, 1, 4, 3, 1, 4, Tex.rgb(96, 70, 44)); m.tintBoth(L_FOREARM, 2, 4, 4, 3, 4, 4, Tex.rgb(96, 70, 44)); });
        add(Category.SLEEVES, "jacket", "Jacket sleeves", G_ARMS, false, (m, c) -> { m.tintBoth(L_UPPER_ARM, 1, 1, 1, 4, 5, 4, c.cloth); m.tintBoth(L_FOREARM, 1, 0, 1, 4, 4, 4, c.cloth); m.shellBoth(L_UPPER_ARM, 0, 2, 0, 5, 6, 5, c.cloth); m.shellBoth(L_FOREARM, 0, 1, 0, 5, 5, 5, c.cloth); m.shellBoth(L_FOREARM, 0, 0, 0, 5, 0, 5, c.cloth2); });
        add(Category.SLEEVES, "striped", "Striped", G_ARMS, false, (m, c) -> { for (int y = 1; y <= 5; y++) m.tintBoth(L_UPPER_ARM, 1, y, 1, 4, y, 4, y % 2 == 0 ? Tex.rgb(36, 52, 110) : Tex.rgb(226, 226, 220)); for (int y = 0; y <= 4; y++) m.tintBoth(L_FOREARM, 1, y, 1, 4, y, 4, y % 2 == 1 ? Tex.rgb(36, 52, 110) : Tex.rgb(226, 226, 220)); });
        add(Category.SLEEVES, "armband", "Armband", G_ARMS, false, (m, c) -> { m.tintBoth(L_UPPER_ARM, 1, 1, 1, 4, 5, 4, c.cloth); m.tintBoth(L_FOREARM, 1, 0, 1, 4, 4, 4, c.cloth); m.shell(L_UPPER_ARM, 0, 3, 0, 5, 4, 5, Tex.rgb(170, 36, 36)); m.tint(L_UPPER_ARM, 1, 3, 1, 4, 4, 4, Tex.rgb(170, 36, 36)); });
        add(Category.SLEEVES, "wraps", "Bandage wraps", G_ARMS, false, (m, c) -> { m.tintBoth(L_FOREARM, 1, 1, 1, 4, 3, 4, Tex.rgb(214, 208, 190)); m.tintBoth(L_FOREARM, 1, 2, 1, 4, 2, 4, Tex.rgb(186, 178, 160)); m.tint(L_UPPER_ARM, 1, 2, 1, 4, 3, 4, Tex.rgb(214, 208, 190)); });
        add(Category.SLEEVES, "ink", "Tattoo sleeve", G_ARMS, false, (m, c) -> { int ink = Tex.mix(c.skin, Tex.rgb(30, 50, 80), 0.7f); for (int y = 0; y <= 5; y++) for (int x = 1; x <= 4; x++) for (int z = 1; z <= 4; z++) { if (CharModel.h(x, y, z, 21) < 0.5f) { if (y >= 1) m.tint(L_UPPER_ARM, x, y, z, x, y, z, ink); if (y <= 4) m.tint(L_FOREARM, x, y, z, x, y, z, ink); } } m.tint(R_UPPER_ARM, 1, 3, 1, 4, 4, 4, ink); });
        add(Category.SLEEVES, "camo", "Camo sleeves", G_ARMS, false, (m, c) -> { int[] k = {Tex.rgb(84, 92, 60), Tex.rgb(58, 66, 44), Tex.rgb(112, 104, 74)}; for (int y = 0; y <= 5; y++) for (int x = 1; x <= 4; x++) for (int z = 1; z <= 4; z++) { int col = k[(int) (CharModel.h(x / 2, y / 2, z / 2, 33) * 2.99f)]; if (y >= 1) m.tintBoth(L_UPPER_ARM, x, y, z, x, y, z, col); if (y <= 4) m.tintBoth(L_FOREARM, x, y, z, x, y, z, col); } });
        add(Category.SLEEVES, "hivis", "Hi-vis bands", G_ARMS, false, (m, c) -> { m.tintBoth(L_UPPER_ARM, 1, 1, 1, 4, 5, 4, c.cloth); m.tintBoth(L_FOREARM, 1, 0, 1, 4, 4, 4, c.cloth); m.tintBoth(L_UPPER_ARM, 1, 2, 1, 4, 2, 4, Tex.rgb(216, 220, 210)); m.tintBoth(L_FOREARM, 1, 2, 1, 4, 2, 4, Tex.rgb(236, 130, 30)); });
        add(Category.SLEEVES, "threequarter", "Three-quarter", G_ARMS, false, (m, c) -> { m.tintBoth(L_UPPER_ARM, 1, 1, 1, 4, 5, 4, c.cloth); m.tintBoth(L_FOREARM, 1, 3, 1, 4, 4, 4, c.cloth); m.tintBoth(L_FOREARM, 1, 3, 1, 4, 3, 4, c.cloth2); });

        // ---------------------------------------------------------------- LEGS (thigh + shin, mirrored)
        legs("jeans", "Jeans", Tex.rgb(52, 64, 96), (m, c, k) -> { m.tintBoth(L_THIGH, 1, 3, 1, 1, 4, 1, dk(k, 0.75f)); });
        legs("cargo", "Cargo trousers", Tex.rgb(86, 88, 62), (m, c, k) -> { m.shellBoth(L_THIGH, 0, 1, 1, 0, 3, 4, dk(k, 0.82f)); m.tintBoth(L_THIGH, 1, 3, 1, 4, 3, 1, dk(k, 0.8f)); });
        legs("track", "Tracksuit bottoms", Tex.rgb(26, 28, 34), (m, c, k) -> { m.tintBoth(L_THIGH, 1, 0, 2, 1, 5, 3, WHITE); m.tintBoth(L_SHIN, 1, 0, 3, 1, 5, 4, WHITE); });
        legs("camo", "Camo BDU", Tex.rgb(74, 80, 54), (m, c, k) -> { int[] col = {Tex.rgb(84, 92, 60), Tex.rgb(58, 66, 44), Tex.rgb(112, 104, 74)}; for (int y = 0; y <= 5; y++) for (int x = 1; x <= 4; x++) for (int z = 1; z <= 5; z++) { int cc = col[(int) (CharModel.h(x / 2, y / 2, z / 2, 44) * 2.99f)]; m.tintBoth(L_THIGH, x, y, z, x, y, z, cc); m.tintBoth(L_SHIN, x, y, z, x, y, z, cc); } });
        legs("work", "Work trousers", Tex.rgb(142, 118, 80), (m, c, k) -> { m.shellBoth(L_SHIN, 1, 4, 1, 4, 5, 1, DARK); m.tintBoth(L_SHIN, 1, 5, 2, 4, 5, 2, DARK); m.tintBoth(L_THIGH, 1, 0, 1, 4, 0, 1, DARK); });
        add(Category.LEGS, "shorts", "Shorts", G_LEGS, false, (m, c) -> { int k = Tex.rgb(96, 84, 60); m.tintBoth(L_THIGH, 1, 2, 1, 4, 5, 4, k); m.tintBoth(L_THIGH, 1, 2, 1, 4, 2, 4, dk(k, 0.8f)); });
        legs("sweats", "Sweatpants", Tex.rgb(120, 122, 126), (m, c, k) -> { m.tintBoth(L_SHIN, 1, 1, 2, 4, 1, 5, dk(k, 0.75f)); m.tintBoth(L_THIGH, 2, 5, 1, 3, 5, 1, WHITE); });
        legs("tactical", "Tactical black", Tex.rgb(34, 36, 40), (m, c, k) -> { m.shell(R_THIGH, 5, 1, 1, 5, 3, 4, Tex.rgb(70, 64, 50)); m.tint(R_THIGH, 1, 3, 1, 4, 3, 4, Tex.rgb(70, 64, 50)); m.shellBoth(L_SHIN, 1, 4, 1, 4, 5, 1, Tex.rgb(52, 54, 58)); });
        legs("patched", "Patched jeans", Tex.rgb(62, 76, 104), (m, c, k) -> { m.tint(L_THIGH, 2, 1, 1, 3, 2, 1, Tex.rgb(130, 110, 80)); m.tint(R_SHIN, 2, 3, 2, 3, 4, 2, Tex.rgb(96, 60, 50)); m.tint(R_THIGH, 1, 0, 1, 2, 0, 1, c.skin); });
        legs("leather", "Leather trousers", Tex.rgb(40, 32, 30), (m, c, k) -> { m.tintBoth(L_THIGH, 4, 0, 2, 4, 5, 3, dk(k, 1.35f)); m.tintBoth(L_SHIN, 4, 0, 3, 4, 5, 4, dk(k, 1.35f)); });
        legs("thermal", "Thermal leggings", Tex.rgb(54, 50, 60), (m, c, k) -> { for (int y = 0; y <= 5; y += 2) { m.tintBoth(L_THIGH, 1, y, 1, 4, y, 4, dk(k, 1.25f)); m.tintBoth(L_SHIN, 1, y, 2, 4, y, 5, dk(k, 1.25f)); } });
        add(Category.LEGS, "rolled", "Rolled jeans", G_LEGS, false, (m, c) -> { int k = Tex.rgb(46, 58, 90); m.tintBoth(L_THIGH, 1, 0, 1, 4, 5, 4, k); m.tintBoth(L_SHIN, 1, 3, 2, 4, 5, 5, k); m.shellBoth(L_SHIN, 0, 3, 1, 5, 3, 6, dk(k, 1.3f)); m.tintBoth(L_SHIN, 1, 3, 2, 4, 3, 5, dk(k, 1.3f)); });

        // ---------------------------------------------------------------- HANDS / WRISTWEAR
        add(Category.HANDS, "bare", "Bare hands", G_HANDS, true, (m, c) -> {});
        add(Category.HANDS, "fingerless", "Fingerless gloves", G_HANDS, false, (m, c) -> m.tintBoth(L_HAND, 1, 1, 1, 4, 1, 4, Tex.rgb(40, 40, 44)));
        add(Category.HANDS, "work", "Work gloves", G_HANDS, false, (m, c) -> { m.tintBoth(L_HAND, 1, 0, 1, 4, 1, 4, Tex.rgb(176, 142, 86)); m.tintBoth(L_FOREARM, 1, 0, 1, 4, 0, 4, Tex.rgb(140, 108, 62)); });
        add(Category.HANDS, "tactical", "Tactical gloves", G_HANDS, false, (m, c) -> { m.tintBoth(L_HAND, 1, 0, 1, 4, 1, 4, BLACK); m.tintBoth(L_HAND, 1, 1, 1, 4, 1, 1, Tex.rgb(96, 98, 100)); m.tintBoth(L_FOREARM, 1, 0, 1, 4, 0, 4, Tex.rgb(52, 54, 58)); });
        add(Category.HANDS, "leather", "Leather gloves", G_HANDS, false, (m, c) -> { m.tintBoth(L_HAND, 1, 0, 1, 4, 1, 4, Tex.rgb(86, 56, 36)); m.tintBoth(L_FOREARM, 1, 0, 1, 4, 0, 4, Tex.rgb(70, 44, 28)); });
        add(Category.HANDS, "wraps", "Hand wraps", G_HANDS, false, (m, c) -> { m.tintBoth(L_HAND, 1, 1, 1, 4, 1, 4, Tex.rgb(214, 208, 190)); m.tintBoth(L_FOREARM, 1, 0, 1, 4, 0, 4, Tex.rgb(196, 188, 170)); });
        add(Category.HANDS, "watch", "Wristwatch", G_HANDS, false, (m, c) -> { m.shell(L_FOREARM, 0, 0, 0, 5, 0, 5, BLACK); m.tint(L_FOREARM, 1, 0, 1, 4, 0, 4, BLACK); m.box(L_FOREARM, 0, 0, 2, 0, 0, 3, Tex.rgb(200, 210, 190)); });
        add(Category.HANDS, "beads", "Bead bracelet", G_HANDS, false, (m, c) -> { int[] k = {Tex.rgb(170, 40, 40), Tex.rgb(210, 180, 60), Tex.rgb(40, 110, 150)}; for (int x = 1; x <= 4; x++) for (int z = 1; z <= 4; z++) m.tint(R_FOREARM, x, 0, z, x, 0, z, k[(x + z) % 3]); });
        add(Category.HANDS, "paracord", "Paracord bands", G_HANDS, false, (m, c) -> { m.tintBoth(L_FOREARM, 1, 0, 1, 4, 0, 4, Tex.rgb(84, 92, 60)); m.tintBoth(L_FOREARM, 2, 0, 1, 3, 0, 1, Tex.rgb(210, 120, 40)); });
        add(Category.HANDS, "mittens", "Mittens", G_HANDS, false, (m, c) -> { m.tintBoth(L_HAND, 1, 0, 1, 4, 1, 4, Tex.rgb(150, 60, 50)); m.shellBoth(L_HAND, 0, 0, 0, 5, 1, 5, Tex.rgb(150, 60, 50)); m.tintBoth(L_FOREARM, 1, 0, 1, 4, 0, 4, Tex.rgb(220, 214, 200)); });
        add(Category.HANDS, "latex", "Latex gloves", G_HANDS, false, (m, c) -> { m.tintBoth(L_HAND, 1, 0, 1, 4, 1, 4, Tex.rgb(80, 140, 196)); m.tintBoth(L_FOREARM, 1, 0, 1, 4, 0, 4, Tex.rgb(80, 140, 196)); });
        add(Category.HANDS, "wristband", "Red wristband", G_HANDS, false, (m, c) -> m.tint(L_FOREARM, 1, 0, 1, 4, 0, 4, Tex.rgb(180, 36, 36)));

        // ---------------------------------------------------------------- FEET (bottom of the shin group)
        feet("combat", "Combat boots", BLACK, 2, false, (m, c) -> m.tintBoth(L_SHIN, 2, 1, 1, 3, 2, 2, Tex.rgb(70, 70, 72)));
        feet("sneaker_w", "White sneakers", WHITE, 1, false, (m, c) -> m.tintBoth(L_SHIN, 1, 1, 3, 4, 1, 4, Tex.rgb(60, 110, 170)));
        feet("sneaker_r", "Red sneakers", Tex.rgb(176, 40, 36), 1, false, (m, c) -> m.tintBoth(L_SHIN, 1, 0, 1, 4, 0, 5, WHITE));
        feet("work", "Work boots", Tex.rgb(170, 128, 70), 2, false, (m, c) -> m.tintBoth(L_SHIN, 1, 0, 1, 4, 0, 5, DARK));
        feet("rubber", "Rubber boots", Tex.rgb(50, 86, 58), 4, true, (m, c) -> m.tintBoth(L_SHIN, 1, 0, 1, 4, 0, 5, BLACK));
        feet("hiking", "Hiking boots", Tex.rgb(110, 76, 50), 2, false, (m, c) -> m.tintBoth(L_SHIN, 2, 1, 2, 3, 2, 2, Tex.rgb(180, 40, 36)));
        feet("trainer", "Black trainers", Tex.rgb(40, 42, 46), 1, false, (m, c) -> m.tintBoth(L_SHIN, 1, 0, 1, 4, 0, 5, Tex.rgb(200, 200, 196)));
        feet("rags", "Rag wraps", Tex.rgb(150, 138, 112), 2, false, (m, c) -> { m.tintBoth(L_SHIN, 1, 1, 2, 4, 1, 5, Tex.rgb(120, 108, 86)); m.tintBoth(L_SHIN, 2, 0, 1, 3, 0, 1, c.skin); });
        feet("hightop", "High-tops", Tex.rgb(180, 44, 40), 2, false, (m, c) -> { m.tintBoth(L_SHIN, 1, 0, 1, 4, 0, 5, WHITE); m.tintBoth(L_SHIN, 1, 0, 1, 4, 1, 1, WHITE); m.tintBoth(L_SHIN, 1, 2, 3, 1, 2, 4, WHITE); });
        feet("valenki", "Valenki", Tex.rgb(130, 126, 118), 3, true, (m, c) -> m.tintBoth(L_SHIN, 1, 0, 1, 4, 0, 5, Tex.rgb(40, 40, 42)));
        feet("dress", "Dress shoes", Tex.rgb(30, 26, 24), 0, false, (m, c) -> m.tintBoth(L_SHIN, 1, 1, 2, 4, 1, 5, Tex.rgb(70, 72, 90)));
        feet("jungle", "Jungle boots", Tex.rgb(70, 78, 52), 3, false, (m, c) -> m.tintBoth(L_SHIN, 1, 0, 1, 4, 1, 5, BLACK));
    }

    private static void torso(String id, String name, int color, Painter p) {
        TORSO_COLORS.put("torso." + id, new int[]{color, dk(color, 0.74f)});
        add(Category.TORSO, id, name, G_TORSO, false, p);
    }

    /** Cloth colours a torso garment lends to the sleeves. */
    public static int[] torsoColors(Part torso) { return TORSO_COLORS.getOrDefault(torso.id(), new int[]{Tex.rgb(96, 98, 100), Tex.rgb(70, 72, 74)}); }

    private static void cloth(CharModel m, int c) {
        m.tint(TORSO, 3, 0, 4, 10, 11, 7, c);
        m.tint(TORSO, 3, 0, 4, 10, 1, 7, Tex.rgb(40, 34, 28)); // belt
        m.set(TORSO, 6, 1, 4, Tex.rgb(170, 150, 90));
    }

    private interface LegPainter { void paint(CharModel m, Ctx c, int color); }

    private static void legs(String id, String name, int color, LegPainter p) {
        add(Category.LEGS, id, name, G_LEGS, false, (m, c) -> {
            m.tintBoth(L_THIGH, 1, 0, 1, 4, 5, 4, color);
            m.tintBoth(L_SHIN, 1, 0, 2, 4, 5, 5, color);
            p.paint(m, c, color);
        });
    }

    private static void feet(String id, String name, int color, int height, boolean thick, Painter extra) {
        add(Category.FEET, id, name, G_FEET, false, (m, c) -> {
            m.tintBoth(L_SHIN, 1, 0, 2, 4, height, 5, color);
            m.both(L_SHIN, 1, 0, 1, 4, Math.min(1, height), 1, color);             // toe cap
            if (thick) m.shellBoth(L_SHIN, 0, 1, 1, 5, height, 6, color);
            m.tintBoth(L_SHIN, 1, 0, 1, 4, 0, 5, dk(color, 0.6f));                 // sole
            extra.paint(m, c);
        });
    }

    // ================================================================== import validation
    /** Validates every part's SKIN assignment; returns human-readable problems (empty = valid). */
    public static List<String> validate() {
        List<String> problems = new ArrayList<>();
        Ctx c = new Ctx();
        c.skin = SKIN_TONES[3]; c.hair = HAIR_TONES[1]; c.cloth = Tex.rgb(96, 98, 100); c.cloth2 = Tex.rgb(70, 72, 74);
        for (Category cat : Category.values()) {
            List<Part> l = TABLE.get(cat);
            if (l == null || l.size() < 12) { problems.add(cat + ": fewer than 12 options (" + (l == null ? 0 : l.size()) + ")"); continue; }
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (Part p : l) {
                if (!ids.add(p.id())) problems.add(p.id() + ": duplicate id");
                CharModel m = new CharModel();
                body(m, c);
                m.beginPart();
                p.painter().paint(m, c);
                if (m.clipped) problems.add(p.id() + ": voxels outside a SKIN group grid");
                if ((m.touched & ~p.groups()) != 0) problems.add(p.id() + ": paints SKIN groups it is not assigned to (" + groupNames(m.touched & ~p.groups()) + ")");
                if (!p.mayBeEmpty() && m.voxelsWritten == 0) problems.add(p.id() + ": paints nothing");
                // limb parts that touch both sides must cover them as a pair (no one-legged trousers)
                if (cat == Category.LEGS || cat == Category.FEET) for (int g : new int[]{L_THIGH, L_SHIN}) if (((m.touched >> g) & 1) != ((m.touched >> mirror(g)) & 1)) problems.add(p.id() + ": " + NAMES[g] + " has no mirrored partner");
            }
        }
        return problems;
    }

    private static String groupNames(int mask) {
        StringBuilder b = new StringBuilder();
        for (int g = 0; g < GROUPS; g++) if ((mask & (1 << g)) != 0) b.append(b.length() > 0 ? "," : "").append(NAMES[g]);
        return b.toString();
    }
}
