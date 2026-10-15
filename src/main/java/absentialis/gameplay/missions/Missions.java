package absentialis.gameplay.missions;

import absentialis.engine.util.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a survivor stands with the five investigations of Act I (0.8). This is the part that is saved: one small
 * record to a mission, kept on the profile, changed only forward. Nothing here is lost by dying - what has been seen
 * has been seen - and nothing here has a clock.
 *
 * A mission moves through five states, in order, and the five missions move independently of each other:
 *
 *   RUMOUR         it has been heard of. Nothing is asked.
 *   INVESTIGATING  the survivor has stood on one of its two sites. There is something to find there.
 *   PROVEN         one of its two pieces of evidence has been seen (taken, or read where it lay). Seeing the other
 *                  as well corroborates it. All that is left is to get out of the Zone alive.
 *   DECISION       they got out. The proof is theirs to give - to the shelters, the Military, Halberd, the Rail Yard
 *                  Crew - or to keep.
 *   CLOSED         it has been given, or kept. The one-time story experience has been paid.
 *
 * What is known is recorded separately from what is held (lore.md, rule 6): {@code seen} is knowledge and is never
 * taken back; the document itself is an ordinary item that can be lost with a body.
 */
public final class Missions {
    public enum Stage { RUMOUR, INVESTIGATING, PROVEN, DECISION, CLOSED }

    public static final class State {
        public Stage stage = Stage.RUMOUR;
        /** Which of the two pieces of evidence have been seen. */
        public final boolean[] seen = new boolean[2];
        /** Who the proof was given to ("" until the decision is made; "keep" if it was kept). */
        public String givenTo = "";
        /** The zone day each stage was reached (journal). */
        public final int[] day = new int[Stage.values().length];
        public boolean corroborated() { return seen[0] && seen[1]; }
    }

    public final Map<String, State> states = new LinkedHashMap<>();
    /** What has just happened, for whoever shows it (the raid HUD, the debrief): emptied by {@link #drain}. */
    private final List<String[]> news = new ArrayList<>();

    public State of(String id) { return states.computeIfAbsent(id, k -> new State()); }
    public Stage stage(String id) { return of(id).stage; }

    private void advance(Story.Mission m, Stage to, int day, String say) {
        State s = of(m.id);
        if (to.ordinal() <= s.stage.ordinal()) return;
        s.stage = to;
        s.day[to.ordinal()] = day;
        news.add(new String[]{m.id, to.name(), say});
    }

    /** News since the last call: {mission id, stage reached, a line to show}. */
    public List<String[]> drain() { List<String[]> l = new ArrayList<>(news); news.clear(); return l; }

    /** The survivor is standing on a mission's site. */
    public void onSite(Story.Mission m, int day) { advance(m, Stage.INVESTIGATING, day, "LEAD: " + m.title + " - " + m.investigate); }

    /** A piece of evidence has been seen: picked up, or read. True if this was news. */
    public boolean onEvidence(String itemId, int day) {
        Story.Evidence e = Story.evidence(itemId);
        if (e == null) return false;
        Story.Mission m = Story.mission(e.mission());
        State s = of(m.id);
        if (s.seen[e.index()]) return false;
        s.seen[e.index()] = true;
        if (s.stage.ordinal() < Stage.PROVEN.ordinal()) advance(m, Stage.PROVEN, day, "EVIDENCE: " + e.name() + " - " + m.discovery);
        else news.add(new String[]{m.id, "CORROBORATED", "CORROBORATED: " + e.name() + " agrees with what you already have. " + m.title + " cannot be argued with now."});
        return true;
    }

    /** The survivor has left the Zone alive: every proven mission is theirs to decide. Returns the missions that moved. */
    public List<Story.Mission> onExtract(int day) {
        List<Story.Mission> moved = new ArrayList<>();
        for (Story.Mission m : Story.MISSIONS) if (stage(m.id) == Stage.PROVEN) { advance(m, Stage.DECISION, day, m.title + ": you are out, and you know. Decide who is told."); moved.add(m); }
        return moved;
    }

    /** True if any mission is waiting for its decision. */
    public Story.Mission awaitingDecision() { for (Story.Mission m : Story.MISSIONS) if (stage(m.id) == Stage.DECISION) return m; return null; }

    /**
     * The decision. Returns the experience to pay (the one-time story award, a little more if the proof was
     * corroborated), or 0 if the mission was not waiting for one. The caller applies cash and standing from the
     * {@link Story.Resolution}.
     */
    public long resolve(String id, String to, int day) {
        Story.Mission m = Story.mission(id);
        if (m == null || stage(id) != Stage.DECISION || !Story.RESOLUTIONS.containsKey(to)) return 0;
        State s = of(id);
        s.givenTo = to;
        advance(m, Stage.CLOSED, day, m.title + ": " + m.outcomes.getOrDefault(to, ""));
        return Math.round(Story.storyXp() * (1 + (s.corroborated() ? Story.corroborationBonus : 0)));
    }

    public int closed() { int n = 0; for (Story.Mission m : Story.MISSIONS) if (stage(m.id) == Stage.CLOSED) n++; return n; }

    /** A journal line for a mission as it stands: what was reported, what has been observed, what is corroborated. */
    public String journal(Story.Mission m) {
        State s = of(m.id);
        return switch (s.stage) {
            case RUMOUR -> "Reported: " + m.rumour;
            case INVESTIGATING -> "Reported: " + m.rumour + "  " + m.investigate;
            case PROVEN -> (s.corroborated() ? "Corroborated: " : "Observed: ") + m.discovery + "  " + m.extract;
            case DECISION -> (s.corroborated() ? "Corroborated: " : "Observed: ") + m.discovery + "  " + m.question;
            case CLOSED -> (s.corroborated() ? "Corroborated: " : "Observed: ") + m.discovery + "  " + m.outcomes.getOrDefault(s.givenTo, "");
        };
    }

    // ------------------------------------------------------------------ persistence
    public Map<String, Object> toJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, State> e : states.entrySet()) {
            State s = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stage", s.stage.name()); m.put("seenA", s.seen[0]); m.put("seenB", s.seen[1]); m.put("givenTo", s.givenTo);
            List<Object> d = new ArrayList<>();
            for (int v : s.day) d.add(v);
            m.put("day", d);
            out.put(e.getKey(), m);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static Missions fromJson(Map<String, Object> in) {
        Missions ms = new Missions();
        if (in == null) return ms;
        for (Map.Entry<String, Object> e : in.entrySet()) {
            if (!(e.getValue() instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) e.getValue();
            State s = ms.of(e.getKey());
            try { s.stage = Stage.valueOf(Json.str(m, "stage", "RUMOUR")); } catch (IllegalArgumentException unknown) { s.stage = Stage.RUMOUR; }
            s.seen[0] = Json.bool(m, "seenA", false); s.seen[1] = Json.bool(m, "seenB", false); s.givenTo = Json.str(m, "givenTo", "");
            List<Object> d = Json.list(m, "day");
            for (int i = 0; i < Math.min(d.size(), s.day.length); i++) if (d.get(i) instanceof Number n) s.day[i] = n.intValue();
        }
        return ms;
    }
}
