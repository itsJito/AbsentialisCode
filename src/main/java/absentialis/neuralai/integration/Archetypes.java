package absentialis.neuralai.integration;

import absentialis.neuralai.runtime.NeuralConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bodies NeuralAI drives (0.8): for every archetype - the three kinds of armed person and every kind of
 * infected - how fast it walks and runs, how much it takes to put down, what its hands do, how far and how widely
 * it sees and how well it hears. The numbers are data ({@code content/neuralai.json}, table "bodies"); the defaults
 * below are the 0.7 values and are what is used when the file leaves one out.
 *
 * A body whose sight is zero has no eyes: nothing it does can come from having seen something (the Gladiator).
 */
public final class Archetypes {
    private Archetypes() {}

    /** speed and runSpeed in m/s, melee in damage per blow, sight in metres, hearing as a multiplier, fovCos the cosine of the half-angle of the far field of view. */
    public record Arch(String id, float speed, float runSpeed, float hp, float melee, float sight, float hearing, float fovCos, boolean infected) {
        public boolean blind() { return sight <= 0; }
    }

    private static final Arch[] DEFAULTS = {
            new Arch("rifleman", 1.6f, 4.6f, 100, 0, 70, 1.0f, 0.45f, false),
            new Arch("marksman", 1.4f, 4.2f, 90, 0, 110, 0.9f, 0.6f, false),
            new Arch("shotgunner", 1.7f, 4.8f, 110, 0, 45, 1.0f, 0.4f, false),
            new Arch("walker", 0.9f, 1.6f, 90, 14, 16, 1.3f, 0.3f, true),
            new Arch("sprinter", 1.4f, 6.4f, 55, 10, 26, 1.4f, 0.2f, true),
            new Arch("bomber", 0.8f, 1.3f, 260, 0, 16, 1.0f, 0.3f, true),
            new Arch("spewer", 1.0f, 2.8f, 130, 8, 30, 1.2f, 0.3f, true),
            new Arch("tank", 1.2f, 3.0f, 1400, 48, 30, 0.9f, 0.3f, true),
            new Arch("gladiator", 0.75f, 7.0f, 900, 0, 0, 2.4f, 1.0f, true),
            new Arch("butcher", 1.1f, 5.4f, 800, 0, 34, 1.0f, 0.25f, true),
            // 1.0: the Crawler has no eyes at all and hears a pin drop; the Tyrant sees as a man does and is very hard to put down
            new Arch("crawler", 1.5f, 6.4f, 170, 18, 0, 2.8f, 1.0f, true),
            new Arch("tyrant", 1.45f, 3.3f, 1700, 44, 36, 1.0f, 0.3f, true),
            new Arch("shambler", 0.7f, 1.1f, 230, 22, 14, 1.2f, 0.3f, true),
            new Arch("screecher", 1.0f, 2.0f, 70, 8, 30, 1.6f, 0.0f, true),
    };

    /** The infected that populate the Zone: the two ordinary kinds, then the five special infected. */
    public static final String[] INFECTED = {"walker", "sprinter", "bomber", "spewer", "tank", "gladiator", "butcher", "crawler", "tyrant"};

    private static Map<String, Arch> table;

    private static synchronized Map<String, Arch> table() {
        if (table != null) return table;
        Map<String, Arch> t = new LinkedHashMap<>();
        for (Arch a : DEFAULTS) t.put(a.id(), a);
        for (Map.Entry<String, Map<String, Object>> e : NeuralConfig.get().bodies.entrySet()) {
            Arch d = t.getOrDefault(e.getKey(), DEFAULTS[0]);
            Map<String, Object> m = e.getValue();
            t.put(e.getKey(), new Arch(e.getKey(), num(m, "speed", d.speed()), num(m, "runSpeed", d.runSpeed()), num(m, "hp", d.hp()), num(m, "melee", d.melee()),
                    num(m, "sight", d.sight()), num(m, "hearing", d.hearing()), num(m, "fovCos", d.fovCos()), m.get("infected") instanceof Boolean b ? b : d.infected()));
        }
        table = t;
        return t;
    }

    private static float num(Map<String, Object> m, String k, float def) { return m.get(k) instanceof Number n ? n.floatValue() : def; }

    public static List<Arch> all() { return new ArrayList<>(table().values()); }

    /** True when the id names a body this table holds (an alias counts). */
    public static boolean known(String id) { return id != null && table().containsKey(canonical(id)); }

    /** What a loose name means: the names the game shows ("regular", "fast"), the old ones ("boomer") and the obvious ones ("zombie"). */
    public static String canonical(String id) {
        if (id == null) return "";
        return switch (id.toLowerCase()) {
            case "boomer" -> "bomber";
            case "regular", "zombie", "infected", "zed" -> "walker";
            case "fast", "runner" -> "sprinter";
            case "spitter" -> "spewer";
            case "giant" -> "tank";
            case "chainsaw", "chainsawman" -> "butcher";
            case "licker", "skinless" -> "crawler";
            case "trenchcoat", "mr_x" -> "tyrant";
            default -> id.toLowerCase();
        };
    }

    public static Arch arch(String id) {
        Arch a = table().get(canonical(id));
        return a != null ? a : table().get("rifleman");
    }

    /** The special infected - the ones with a name of their own, a place in the killfeed and a trick that changes the fight. */
    public static boolean special(String archetype) {
        if (archetype == null) return false;
        String c = canonical(archetype);
        return !"walker".equals(c) && !"sprinter".equals(c) && arch(c).infected();
    }

    /** The ones too big to wrestle, to turn, or to be knocked about like a person. */
    public static boolean huge(String archetype) { return "tank".equals(archetype) || "gladiator".equals(archetype) || "butcher".equals(archetype) || "tyrant".equals(archetype); }

    public static String displayName(String archetype) {
        return switch (canonical(archetype)) { case "walker" -> "Regular"; case "sprinter" -> "Fast"; case "bomber" -> "Bomber"; case "spewer" -> "Spewer"; case "tank" -> "Tank"; case "gladiator" -> "Gladiator"; case "butcher" -> "Butcher"; case "crawler" -> "Crawler"; case "tyrant" -> "Tyrant"; default -> archetype; };
    }

    /** How much bigger than a person the model of a kind is drawn. */
    public static float scale(String archetype) {
        return switch (archetype) { case "tank" -> 1.45f; case "gladiator" -> 1.2f; case "spewer" -> 1.1f; case "butcher" -> 1.04f; case "tyrant" -> 1.34f; default -> 1f; };
    }
}
