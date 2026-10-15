package absentialis.gameplay.missions;

import absentialis.engine.util.Json;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The faction missions (1.0, from missions.md): four chains of five, one to a faction, each given out and settled in
 * person by that faction's advisor. This class is the content (content/faction_missions.json) and the part that is
 * saved: for each chain, how many missions are resolved and where the current one stands; and every decision ever
 * made, as a flag that is never taken back.
 *
 * A mission is OFFERED until the advisor has been spoken to and it has been taken; ACTIVE until the thing it is about
 * has been found at its site; FOUND until the survivor has gone back to the advisor and decided. Then the chain moves
 * on. Leaving in the middle suspends it; dying loses nothing that has been seen. A chain is strictly in order and no
 * chain waits on another.
 */
public final class Chains {
    public record Choice(String id, String label, String outcome, int cash, Map<String, Float> rep, String flag, String needs) {}

    public static final class Mission {
        public String id, chain, title, site, find, brief, discovery, ask, lead;
        public int n;
        public final List<Choice> choices = new ArrayList<>();
    }

    public record Advisor(String chain, String faction, String name, String role, String site, String greeting, String done) {}

    public static final List<Advisor> ADVISORS = new ArrayList<>();
    public static final List<Mission> MISSIONS = new ArrayList<>();
    public static float xpShare = 0.5f;
    private static boolean loaded;

    @SuppressWarnings("unchecked")
    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Map<String, Object> root;
        try (InputStream in = Chains.class.getResourceAsStream("/content/faction_missions.json")) {
            if (in == null) return;
            root = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) { System.err.println("[Chains] faction_missions.json: " + e); return; }
        xpShare = (float) Json.num(root, "xpShare", 0.5);
        for (Object o : Json.list(root, "advisors")) {
            Map<String, Object> m = (Map<String, Object>) o;
            ADVISORS.add(new Advisor(Json.str(m, "chain", ""), Json.str(m, "faction", ""), Json.str(m, "name", ""), Json.str(m, "role", ""), Json.str(m, "site", ""), Json.str(m, "greeting", ""), Json.str(m, "done", "")));
        }
        for (Object o : Json.list(root, "missions")) {
            Map<String, Object> m = (Map<String, Object>) o;
            Mission ms = new Mission();
            ms.id = Json.str(m, "id", ""); ms.chain = Json.str(m, "chain", ""); ms.n = (int) Json.num(m, "n", 1); ms.title = Json.str(m, "title", ""); ms.site = Json.str(m, "site", "");
            ms.find = Json.str(m, "find", ""); ms.brief = Json.str(m, "brief", ""); ms.discovery = Json.str(m, "discovery", ""); ms.ask = Json.str(m, "ask", ""); ms.lead = Json.str(m, "lead", "");
            for (Object co : Json.list(m, "choices")) {
                Map<String, Object> c = (Map<String, Object>) co;
                Map<String, Float> rep = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : Json.obj(c, "rep").entrySet()) if (e.getValue() instanceof Number n) rep.put(e.getKey(), n.floatValue());
                ms.choices.add(new Choice(Json.str(c, "id", ""), Json.str(c, "label", ""), Json.str(c, "outcome", ""), (int) Json.num(c, "cash", 0), rep, Json.str(c, "flag", ""), Json.str(c, "needs", "")));
            }
            MISSIONS.add(ms);
        }
    }

    public static Advisor advisor(String chain) { load(); for (Advisor a : ADVISORS) if (a.chain().equals(chain)) return a; return null; }
    public static Mission mission(String chain, int n) { load(); for (Mission m : MISSIONS) if (m.chain.equals(chain) && m.n == n) return m; return null; }
    public static int length(String chain) { load(); int k = 0; for (Mission m : MISSIONS) if (m.chain.equals(chain)) k++; return k; }

    /** The one-time experience a resolution pays. */
    public static long xp() { load(); return Math.round(Story.storyXp() * (double) xpShare); }

    // ------------------------------------------------------------------ what is saved
    public enum Stage { OFFERED, ACTIVE, FOUND }

    public static final class State { public int done; public Stage stage = Stage.OFFERED; public boolean met; }

    public final Map<String, State> chains = new LinkedHashMap<>();
    /** Every decision made, for good. */
    public final Set<String> flags = new LinkedHashSet<>();
    /** Mission id -> the choice taken. */
    public final Map<String, String> taken = new LinkedHashMap<>();

    public State of(String chain) { return chains.computeIfAbsent(chain, k -> new State()); }
    public Mission current(String chain) { return mission(chain, of(chain).done + 1); }
    public boolean complete(String chain) { return of(chain).done >= length(chain) && length(chain) > 0; }
    public boolean has(String flag) { return flags.contains(flag); }
    public int resolved() { int n = 0; for (State s : chains.values()) n += s.done; return n; }

    public boolean accept(String chain) { State s = of(chain); if (s.stage != Stage.OFFERED || current(chain) == null) return false; s.stage = Stage.ACTIVE; return true; }
    public boolean found(String chain) { State s = of(chain); if (s.stage != Stage.ACTIVE) return false; s.stage = Stage.FOUND; return true; }

    /** The choices open on a mission as things stand (one that needs an earlier decision is offered only to someone who made it). */
    public List<Choice> open(Mission m) { List<Choice> l = new ArrayList<>(); for (Choice c : m.choices) if (c.needs().isEmpty() || flags.contains(c.needs())) l.add(c); return l; }

    /** The decision. Returns the choice (the caller pays cash, standing and experience), or null if the mission was not waiting for one. */
    public Choice resolve(String chain, String choiceId) {
        State s = of(chain);
        Mission m = current(chain);
        if (m == null || s.stage != Stage.FOUND) return null;
        for (Choice c : open(m)) if (c.id().equals(choiceId)) {
            if (!c.flag().isEmpty()) flags.add(c.flag());
            taken.put(m.id, c.id());
            s.done++; s.stage = Stage.OFFERED;
            return c;
        }
        return null;
    }

    /** A line for the journal: where a chain stands. */
    public String journal(String chain) {
        Advisor a = advisor(chain);
        Mission m = current(chain);
        State s = of(chain);
        if (a == null) return "";
        if (m == null) return a.name() + ": all five settled.";
        return switch (s.stage) {
            case OFFERED -> s.done == 0 && !s.met ? a.name() + " (" + a.role() + ") has work for someone who will come and ask." : m.n + ". " + m.title + " - " + a.name() + " will offer it in person.";
            case ACTIVE -> m.n + ". " + m.title + " - " + m.brief;
            case FOUND -> m.n + ". " + m.title + " - found: " + m.find + ". Report to " + a.name() + " in person.";
        };
    }

    public Map<String, Object> toJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> cs = new LinkedHashMap<>();
        for (Map.Entry<String, State> e : chains.entrySet()) { Map<String, Object> m = new LinkedHashMap<>(); m.put("done", e.getValue().done); m.put("stage", e.getValue().stage.name()); m.put("met", e.getValue().met); cs.put(e.getKey(), m); }
        out.put("chains", cs);
        out.put("flags", new ArrayList<Object>(flags));
        out.put("taken", new LinkedHashMap<String, Object>(taken));
        return out;
    }

    @SuppressWarnings("unchecked")
    public static Chains fromJson(Map<String, Object> in) {
        Chains c = new Chains();
        if (in == null) return c;
        for (Map.Entry<String, Object> e : Json.obj(in, "chains").entrySet()) {
            if (!(e.getValue() instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) e.getValue();
            State s = c.of(e.getKey());
            s.done = (int) Json.num(m, "done", 0); s.met = Json.bool(m, "met", false);
            try { s.stage = Stage.valueOf(Json.str(m, "stage", "OFFERED")); } catch (IllegalArgumentException x) { s.stage = Stage.OFFERED; }
        }
        for (Object o : Json.list(in, "flags")) c.flags.add(String.valueOf(o));
        for (Map.Entry<String, Object> e : Json.obj(in, "taken").entrySet()) c.taken.put(e.getKey(), String.valueOf(e.getValue()));
        return c;
    }
}
