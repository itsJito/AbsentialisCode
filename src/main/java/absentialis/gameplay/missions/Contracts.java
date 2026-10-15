package absentialis.gameplay.missions;

import absentialis.engine.util.Json;
import absentialis.world.District;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The contract board. Until 0.8 it was filled by a generator, and what it generated overlapped - three supply runs
 * and two bounties on the same board. It is now ten written contracts ({@code content/story.json}, from lore.md),
 * each a different kind of task for a different reason, none of them a step in a story mission:
 *
 *   FETCH    bring something out            CULL     thin the infected of a district
 *   AID      get the wounded on their feet  HUNT     a bounty on a faction in a district
 *   RECON    walk new ground                POWER    bring a substation back
 *   INTEL    recover and read documents     SPECIAL  put down one of the changed ones
 *   EXTRACT  go in and come out a district  SEARCH   go through the containers of a district
 *
 * Contracts progress from what happens on an operation and pay only when the survivor gets out. Up to three are held
 * at once. A completed contract leaves the board and comes back a couple of operations later; what it pays grows
 * with Rank. The side the survivor leans toward ({@link #alliance}) has its work at the top of the board.
 */
public final class Contracts {
    public enum Kind { FETCH, CULL, HUNT, RECON, EXTRACT, INTEL, SEARCH, SPECIAL, AID, POWER }

    public static final class Contract {
        public String id, title, desc, giver, target, district;
        public Kind kind;
        public int need, have, scrip, xp;
        public float rep;
        public boolean done() { return have >= need; }
        /** True for a task that is done wherever it is done (its district is empty). */
        public boolean anywhere() { return district == null || district.isEmpty(); }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("title", title); m.put("desc", desc); m.put("giver", giver); m.put("target", target);
            m.put("district", district); m.put("kind", kind.name()); m.put("need", need); m.put("have", have);
            m.put("scrip", scrip); m.put("xp", xp); m.put("rep", (double) rep);
            return m;
        }

        static Contract fromJson(Map<String, Object> m) {
            Contract c = new Contract();
            c.id = Json.str(m, "id", "?"); c.title = Json.str(m, "title", "?"); c.desc = Json.str(m, "desc", "");
            c.giver = Json.str(m, "giver", "survivors"); c.target = Json.str(m, "target", ""); c.district = Json.str(m, "district", "");
            try { c.kind = Kind.valueOf(Json.str(m, "kind", "FETCH")); } catch (IllegalArgumentException unknown) { c.kind = Kind.FETCH; }
            c.need = Json.integer(m, "need", 1); c.have = Json.integer(m, "have", 0);
            c.scrip = Json.integer(m, "scrip", 100); c.xp = Json.integer(m, "xp", 1000); c.rep = (float) Json.num(m, "rep", 2);
            return c;
        }
    }

    public final List<Contract> board = new ArrayList<>(), active = new ArrayList<>();
    public final List<String> completed = new ArrayList<>();
    /** Counts the times the board has been put up afresh (once an operation): the clock a completed contract waits on. */
    public int serial;
    /** Contract id -> the {@link #serial} at which it comes back to the board. */
    public final Map<String, Integer> returns = new LinkedHashMap<>();
    public static final int MAX_ACTIVE = 3;

    /** The side the survivor leans toward ("" = none): its work is at the top of the board. Set by the profile. */
    public String alliance = "";

    /** The ten contracts as they are offered at a Rank. */
    public static List<Contract> catalogue(int rank) {
        Story.load();
        List<Contract> l = new ArrayList<>();
        float scale = 1 + Math.max(1, rank) / 25f;
        for (Map<String, Object> m : Story.CONTRACTS) {
            Contract c = Contract.fromJson(m);
            c.have = 0;
            c.scrip = Math.round(c.scrip * scale / 10f) * 10;
            c.xp = Math.round(c.xp * scale / 50f) * 50;
            l.add(c);
        }
        return l;
    }

    /** True for an id that is one of the ten (anything else on a board came from the generator of an older version). */
    public static boolean written(String id) { Story.load(); for (Map<String, Object> m : Story.CONTRACTS) if (id.equals(Json.str(m, "id", ""))) return true; return false; }

    /**
     * Puts the board up: every contract that is not being worked and is not resting after being completed, the
     * survivor's own side first. It can be called as often as anyone likes; what moves the clock a completed contract
     * waits on is {@link #operationDone}. The seed is unused now (the board is not random) and is kept so that nothing
     * that calls this has to change.
     */
    public void refill(long seed, int rank) {
        board.clear();
        // what an older version generated and nobody took is simply gone; what was accepted is still honoured
        for (Contract c : catalogue(rank)) {
            boolean held = false;
            for (Contract a : active) if (a.id.equals(c.id)) held = true;
            if (held || returns.getOrDefault(c.id, 0) > serial) continue;       // resting after being completed
            board.add(c);
        }
        board.sort((a, b) -> Boolean.compare(!a.giver.equals(alliance), !b.giver.equals(alliance)));
    }

    /** An operation has ended: the clock that completed contracts wait on moves one on. */
    public void operationDone() { serial++; }

    public void refill(long seed, int rank, String alliance) { this.alliance = alliance == null ? "" : alliance; refill(seed, rank); }

    public String accept(String id) {
        if (active.size() >= MAX_ACTIVE) return "Contract limit reached (" + MAX_ACTIVE + ")";
        for (Contract c : board) if (c.id.equals(id)) { board.remove(c); active.add(c); return null; }
        return "Unknown contract";
    }

    /** Dropping a contract puts it straight back on the board, its progress lost. */
    public void abandon(String id) {
        for (Contract c : new ArrayList<>(active)) if (c.id.equals(id)) { active.remove(c); c.have = 0; if (written(c.id)) board.add(c); }
    }

    /** A contract has been paid: it rests before it is offered again. */
    public void complete(Contract c) {
        active.remove(c);
        completed.add(c.id);
        Story.load();
        returns.put(c.id, serial + 1 + Story.contractCooldown);     // +1: the operation it was completed on ends after this
    }

    private static boolean in(Contract c, District d) { return c.anywhere() || d != null && c.district.equals(d.name()); }
    private static void add(Contract c, int n) { c.have = Math.min(c.need, c.have + n); }

    // ------------------------------------------------------------------ what happens on an operation
    public void onKill(String faction, District d) {
        for (Contract c : active) {
            if (!in(c, d)) continue;
            if (c.kind == Kind.CULL && "infected".equals(faction)) add(c, 1);
            if (c.kind == Kind.HUNT && c.target.equals(faction)) add(c, 1);
        }
    }

    /** One of the changed ones has been put down: anything infected that is not a walker or a sprinter. */
    public void onSpecial(String archetype) { for (Contract c : active) if (c.kind == Kind.SPECIAL) add(c, 1); }
    public void onExplore(District d, int cells) { for (Contract c : active) if (c.kind == Kind.RECON && in(c, d)) add(c, cells); }
    public void onDocument() { for (Contract c : active) if (c.kind == Kind.INTEL) add(c, 1); }
    public void onSearch(District d) { for (Contract c : active) if (c.kind == Kind.SEARCH && in(c, d)) add(c, 1); }
    public void onAid() { for (Contract c : active) if (c.kind == Kind.AID) add(c, 1); }
    public void onPower() { for (Contract c : active) if (c.kind == Kind.POWER) add(c, 1); }
    public void onExtract(District deployedIn) { for (Contract c : active) if (c.kind == Kind.EXTRACT && in(c, deployedIn)) c.have = c.need; }

    // ------------------------------------------------------------------ persistence
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Object> b = new ArrayList<>(), a = new ArrayList<>();
        for (Contract c : board) b.add(c.toJson());
        for (Contract c : active) a.add(c.toJson());
        m.put("board", b); m.put("active", a); m.put("completed", new ArrayList<Object>(completed)); m.put("serial", serial);
        m.put("returns", new LinkedHashMap<String, Object>(returns));
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Contracts fromJson(Map<String, Object> m) {
        Contracts c = new Contracts();
        if (m == null) return c;
        // a board from before 0.8 was generated: what was on offer goes, what had been accepted stays and is paid as agreed
        for (Object o : Json.list(m, "board")) { Contract k = Contract.fromJson((Map<String, Object>) o); if (written(k.id)) c.board.add(k); }
        for (Object o : Json.list(m, "active")) c.active.add(Contract.fromJson((Map<String, Object>) o));
        c.completed.addAll(Json.strList(m, "completed"));
        c.serial = Json.integer(m, "serial", 0);
        for (Map.Entry<String, Object> e : Json.obj(m, "returns").entrySet()) if (e.getValue() instanceof Number n) c.returns.put(e.getKey(), n.intValue());
        return c;
    }
}
