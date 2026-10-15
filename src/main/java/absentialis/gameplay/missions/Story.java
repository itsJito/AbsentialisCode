package absentialis.gameplay.missions;

import absentialis.engine.util.Json;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The narrative table (0.8): {@code content/story.json}, read once. It holds the five Act I investigations - what
 * each is called, where its two sites are, what its two pieces of evidence say, what each faction makes of it and
 * what follows from handing the proof to each of them - and the ten contracts of the board. Everything in it is
 * taken from lore.md; nothing in the code names a mission.
 */
public final class Story {
    private Story() {}

    /** One investigation. */
    public static final class Mission {
        public String id = "", title = "", district = "", rumour = "", investigate = "", extract = "", discovery = "", question = "";
        public final List<String> sites = new ArrayList<>(), evidence = new ArrayList<>();
        public final Map<String, String> readings = new LinkedHashMap<>(), outcomes = new LinkedHashMap<>();
    }

    /** What handing the proof to somebody does. */
    public record Resolution(String key, String label, int cash, Map<String, Float> rep) {}

    /** A piece of evidence: the item it is, and what it says. */
    public record Evidence(String id, String name, String icon, String text, String mission, int index) {}

    public static final List<Mission> MISSIONS = new ArrayList<>();
    public static final Map<String, Resolution> RESOLUTIONS = new LinkedHashMap<>();
    public static final Map<String, Evidence> EVIDENCE = new LinkedHashMap<>();
    public static final List<Map<String, Object>> CONTRACTS = new ArrayList<>();
    public static int xpRank = 20, contractCooldown = 2;
    public static float xpShare = 0.17f, corroborationBonus = 0.10f;
    private static boolean loaded;

    public static Mission mission(String id) { load(); for (Mission m : MISSIONS) if (m.id.equals(id)) return m; return null; }
    public static Evidence evidence(String itemId) { load(); return EVIDENCE.get(itemId); }

    /** The one-time experience an investigation pays: a share of what it takes to reach the Rank the act is meant to carry a new survivor toward. */
    public static long storyXp() { load(); return Math.round(absentialis.gameplay.progression.Progression.xpToReach(xpRank) * (double) xpShare); }

    @SuppressWarnings("unchecked")
    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Map<String, Object> root;
        try (InputStream in = Story.class.getResourceAsStream("/content/story.json")) {
            if (in == null) return;
            root = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) { System.err.println("[Story] story.json: " + e); return; }
        xpRank = Json.integer(root, "storyXpRank", 20);
        xpShare = (float) Json.num(root, "storyXpShare", 0.17);
        corroborationBonus = (float) Json.num(root, "corroborationBonus", 0.10);
        contractCooldown = Json.integer(root, "contractCooldown", 2);
        for (Map.Entry<String, Object> e : Json.obj(root, "resolutions").entrySet()) {
            Map<String, Object> o = (Map<String, Object>) e.getValue();
            Map<String, Float> rep = new LinkedHashMap<>();
            for (Map.Entry<String, Object> r : Json.obj(o, "rep").entrySet()) rep.put(r.getKey(), ((Number) r.getValue()).floatValue());
            RESOLUTIONS.put(e.getKey(), new Resolution(e.getKey(), Json.str(o, "label", e.getKey()), Json.integer(o, "cash", 0), rep));
        }
        Map<String, Object> ev = Json.obj(root, "evidence");
        for (Object mo : Json.list(root, "missions")) {
            Map<String, Object> o = (Map<String, Object>) mo;
            Mission m = new Mission();
            m.id = Json.str(o, "id", ""); m.title = Json.str(o, "title", ""); m.district = Json.str(o, "district", "");
            m.rumour = Json.str(o, "rumour", ""); m.investigate = Json.str(o, "investigate", ""); m.extract = Json.str(o, "extract", "");
            m.discovery = Json.str(o, "discovery", ""); m.question = Json.str(o, "question", "");
            m.sites.addAll(Json.strList(o, "sites")); m.evidence.addAll(Json.strList(o, "evidence"));
            for (Map.Entry<String, Object> r : Json.obj(o, "readings").entrySet()) m.readings.put(r.getKey(), String.valueOf(r.getValue()));
            for (Map.Entry<String, Object> r : Json.obj(o, "outcomes").entrySet()) m.outcomes.put(r.getKey(), String.valueOf(r.getValue()));
            MISSIONS.add(m);
            for (int i = 0; i < m.evidence.size(); i++) {
                Map<String, Object> eo = Json.obj(ev, m.evidence.get(i));
                EVIDENCE.put(m.evidence.get(i), new Evidence(m.evidence.get(i), Json.str(eo, "name", m.evidence.get(i)), Json.str(eo, "icon", "report"), Json.str(eo, "text", ""), m.id, i));
            }
        }
        for (Object co : Json.list(root, "contracts")) CONTRACTS.add((Map<String, Object>) co);
    }

    /** The evidence as items: documents that cannot be sold, each one square of a case. Content adds these to the item table. */
    public static List<Map<String, Object>> evidenceItems() {
        load();
        List<Map<String, Object>> l = new ArrayList<>();
        for (Evidence e : EVIDENCE.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id()); m.put("name", e.name()); m.put("cat", "document"); m.put("w", 1); m.put("h", 1); m.put("mass", 0.05); m.put("value", 0);
            m.put("tier", 3); m.put("icon", e.icon()); m.put("quest", true); m.put("doc", "story");
            m.put("actions", new ArrayList<Object>(List.of("use")));
            m.put("desc", "Evidence. " + mission(e.mission()).title + ".");
            l.add(m);
        }
        return l;
    }
}
