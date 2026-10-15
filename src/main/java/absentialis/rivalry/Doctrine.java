package absentialis.rivalry;

import absentialis.engine.util.Json;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rivalry system's data ({@code content/rivalry.json}): faction doctrines, leader traits and their bounds, what
 * events do to relationships, statuses, alliance terms, strategic actions and adaptation rules. Loaded once. The
 * engine asks this class for numbers; it never carries a copy of them.
 *
 * One decision framework serves all four factions (brief section 4): what differs between them is here - their
 * weights, the actions open to them, their tolerance for risk and how they take what is done to them.
 */
public final class Doctrine {
    private Doctrine() {}

    public static final String[] FACTIONS = {"mercenaries", "government", "loners", "bandits"};
    private static Map<String, Object> root;
    private static String[] considerations;

    public record Status(String name, float min, float max, String text) {}
    private static final List<Status> STATUS = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private static synchronized Map<String, Object> root() {
        if (root != null) return root;
        try (var in = Doctrine.class.getResourceAsStream("/content/rivalry.json")) {
            root = in == null ? new LinkedHashMap<>() : Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[Rivalry] content/rivalry.json could not be read: " + e);
            root = new LinkedHashMap<>();
        }
        for (Object o : Json.list(root, "status")) if (o instanceof Map) { Map<String, Object> m = (Map<String, Object>) o; STATUS.add(new Status(Json.str(m, "name", "?"), (float) Json.num(m, "min", 0), (float) Json.num(m, "max", 0), Json.str(m, "text", ""))); }
        List<String> c = Json.strList(root, "considerations");
        considerations = c.toArray(new String[0]);
        return root;
    }

    public static float world(String key, float def) { return (float) Json.num(Json.obj(root(), "world"), key, def); }
    public static float alliance(String key, float def) { return (float) Json.num(Json.obj(root(), "alliance"), key, def); }
    public static String[] considerations() { root(); return considerations; }
    public static Map<String, Object> faction(String f) { return Json.obj(Json.obj(root(), "factions"), f); }
    public static Map<String, Object> event(String type) { return Json.obj(Json.obj(root(), "events"), type); }
    public static Map<String, Object> action(String a) { return Json.obj(Json.obj(root(), "actions"), a); }
    public static Map<String, Object> trait(String t) { return Json.obj(Json.obj(root(), "traits"), t); }
    public static Map<String, Object> interaction(String i) { return Json.obj(Json.obj(root(), "interactions"), i); }
    public static List<Object> adaptations() { return Json.list(root(), "adaptations"); }
    public static List<Object> milestones() { return Json.list(root(), "milestones"); }
    public static List<String> pool(String key) { return Json.strList(root(), key); }

    public static List<String> traitNames() { List<String> l = new ArrayList<>(); for (String k : Json.obj(root(), "traits").keySet()) if (!k.startsWith("_")) l.add(k); return l; }
    public static List<String> eventTypes() { List<String> l = new ArrayList<>(); for (String k : Json.obj(root(), "events").keySet()) if (!k.startsWith("_")) l.add(k); return l; }
    public static List<String> actionNames() { List<String> l = new ArrayList<>(); for (String k : Json.obj(root(), "actions").keySet()) if (!k.startsWith("_")) l.add(k); return l; }
    public static List<String> interactionNames() { List<String> l = new ArrayList<>(); for (String k : Json.obj(root(), "interactions").keySet()) if (!k.startsWith("_")) l.add(k); return l; }

    /** The faction key the rest of the game uses (military, raiders, survivors, mercenaries) for a rivalry faction. */
    public static String gameKey(String faction) { return Json.str(faction(faction), "gameKey", faction); }

    /** The rivalry faction for one of the game's faction keys, or null (the infected, the player). */
    public static String ofGameKey(String key) {
        for (String f : FACTIONS) if (gameKey(f).equals(key)) return f;
        return null;
    }

    public static String display(String faction) {
        return switch (faction) { case "government" -> "Military"; case "mercenaries" -> "Mercenaries"; case "loners" -> "Loners"; case "bandits" -> "Bandits"; default -> faction; };
    }

    public static float weight(String faction, String consideration) { return (float) Json.num(Json.obj(faction(faction), "weights"), consideration, 0); }
    public static float risk(String faction) { return (float) Json.num(faction(faction), "risk", 0.5); }
    public static float leaderRange(String faction) { return (float) Json.num(faction(faction), "leaderRange", 0.3); }
    public static List<String> actions(String faction) { return Json.strList(faction(faction), "actions"); }
    /** How strongly this faction takes a kind of event (1 = as the event table says). */
    public static float reaction(String faction, String type) { return (float) Json.num(Json.obj(faction(faction), "reaction"), type, 1); }
    public static String bark(String faction, String key) { return Json.str(Json.obj(faction(faction), "barks"), key, ""); }
    public static int colour(String faction) { try { return 0xFF000000 | Integer.parseInt(Json.str(faction(faction), "colour", "B0B0B0"), 16); } catch (NumberFormatException e) { return 0xFFB0B0B0; } }

    public static List<Status> statuses() { root(); return STATUS; }

    /** The standing a favor value amounts to (Hated .. Alliance-eligible). */
    public static Status status(float favor) {
        root();
        for (Status s : STATUS) if (favor >= s.min() && favor <= s.max() + 0.999f) return s;
        return STATUS.isEmpty() ? new Status("Neutral", -10, 19, "") : favor < STATUS.get(0).min() ? STATUS.get(0) : STATUS.get(STATUS.size() - 1);
    }

    public static int statusIndex(float favor) { Status s = status(favor); return Math.max(0, STATUS.indexOf(s)); }

    /**
     * The weight a group gives a consideration: the faction's, bent by its leader's traits and held inside the
     * faction's own range - a leader changes how the doctrine is applied, never which doctrine it is (invariant 3).
     */
    public static float leaderModifier(String faction, List<String> traits, String consideration) {
        float m = 1;
        for (String t : traits) m *= (float) Json.num(Json.obj(trait(t), "weights"), consideration, 1);
        float range = leaderRange(faction);
        return Math.max(1 - range, Math.min(1 + range, m));
    }

    public static float riskTolerance(String faction, List<String> traits) {
        float r = 0;
        for (String t : traits) r += (float) Json.num(trait(t), "risk", 0);
        return Math.max(0.02f, Math.min(0.98f, risk(faction) + Math.max(-0.2f, Math.min(0.2f, r))));
    }
}
