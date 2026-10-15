package absentialis.sim;

import absentialis.engine.util.Rng;
import absentialis.world.District;
import absentialis.world.MapData;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A-Life population (v0.3): every human and every infected in the Zone is one persistent agent
 * with an identity, a faction, a home, a shift, needs and a daily routine. The list lives in the
 * profile, so the same individuals are where they left themselves on the next deployment - or are
 * still dead.
 *
 * Agents exist in two states. ONLINE: near the player, bound to an {@link absentialis.entity.Actor}
 * that runs the full perception / navigation / combat AI. OFFLINE: everywhere else, advanced here
 * by a cheap statistical step - they follow their schedule (work, rest, sleep by shift), travel,
 * scavenge, get hungry and tired, meet hostile agents and fight encounters whose casualties are
 * real and whose sites are written to the event log for the next raid to find. The raid (the
 * A-Life Director) converts between the two at a distance and out of the player's sight.
 */
public final class Agents {
    public static final int CELL = 64, CELLS = World.W / CELL;
    public static final String[] FACTION = {"infected", "military", "raiders", "survivors", "mercenaries"};

    public static final class Agent {
        public long id, squad;
        public String faction, name = "", role = "patrol", activity = "idle";
        public float x, z, homeX, homeZ;
        /** Health as a fraction; needs 0 (fine) .. 1 (desperate); morale 0..1. */
        public float hp = 1, hunger, fatigue, morale = 0.8f;
        public boolean night, alive = true;
        public int kills;
        /** Actor id while online, 0 while simulated offline (never saved). */
        public transient int actorId;

        /**
         * 0.6: everyone in the Zone has a Rank on the same ladder as the player. An agent arrives with whatever
         * experience their id gives them (soldiers and contractors with more than scavengers) and earns more with
         * every kill, on screen or off.
         */
        public long xp() {
            if (infected()) return kills * 200L;
            long h = (id * 0x9E3779B97F4A7C15L) >>> 40;
            int start = 1 + (int) (h % ("military".equals(faction) || "mercenaries".equals(faction) ? 22 : 14));
            return absentialis.gameplay.progression.Progression.xpToReach(start) + kills * 900L;
        }
        public int rank() { return rankFor(xp()); }
        public String title() { return infected() ? "Infected" : absentialis.gameplay.progression.Progression.rankName(rank()) + " " + rank(); }
        public boolean female() { return Names.female(id); }

        public int cell() { return cellOf(x, z); }
        public int fi() { for (int i = 0; i < FACTION.length; i++) if (FACTION[i].equals(faction)) return i; return 0; }
        public boolean infected() { return "infected".equals(faction); }
    }

    public final List<Agent> agents = new ArrayList<>();
    /** Strength of each side when the Zone was first surveyed; arrivals drift the population back toward it. */
    public final int[] baseline = new int[5];
    private long nextId = 1;
    public int deaths, encounters, arrivals;
    /** Game hours simulated offline in total (tests / the alife console command). */
    public double hoursSimulated;

    /** The Rank a quantity of experience amounts to (the player's ladder: Progression.costOf per rank, capped at 50). */
    public static int rankFor(long xp) {
        int r = 1;
        long acc = 0;
        while (r < 50 && acc + absentialis.gameplay.progression.Progression.costOf(r) <= xp) { acc += absentialis.gameplay.progression.Progression.costOf(r); r++; }
        return r;
    }

    /** Population keys a profile can scale (0.6): everyone, and each group. */
    public static final String[] POP_KEYS = {"all", "infected", "military", "raiders", "survivors", "mercenaries"};
    public static final float POP_MIN = 0.25f, POP_MAX = 2.5f;

    /**
     * A whole number of people for a fractional count: the fraction is a chance of one more, so totals scale
     * smoothly. The chance is decided by a hash of where and who, not by the survey's own dice - so a multiplier of
     * 1 leaves the Zone exactly as 0.5 surveyed it, and changing one group's numbers never moves another's.
     */
    private static int scaled(int n, float k, long salt) {
        if (k == 1f) return n;
        float v = n * k;
        int whole = (int) Math.floor(v);
        long h = salt * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 29;
        return whole + (((h >>> 40) / (float) (1 << 24)) < v - whole ? 1 : 0);
    }

    /** Spawns the designed number from the survey's dice and keeps the first {@code keep} of them; people beyond the designed number come from their own dice. */
    private Agent[] people(World w, String faction, int cx, int cz, Rng r, Rng extra, long squad, int designed, int keep) {
        Agent[] out = new Agent[keep];
        for (int i = 0; i < Math.max(designed, keep); i++) {
            Agent g = spawn(w, faction, cx, cz, i < designed ? r : extra, squad);
            if (i < keep) out[i] = g; else { agents.remove(g); nextId--; }
        }
        return out;
    }

    public static int cellOf(float x, float z) { return clamp((int) x / CELL) * CELLS + clamp((int) z / CELL); }
    private static int clamp(int v) { return Math.max(0, Math.min(CELLS - 1, v)); }

    // ------------------------------------------------------------------ routines
    /**
     * What an agent is doing at an hour of the day. Humans work one of two shifts and spend the rest
     * of the day resting and asleep at home; the infected roam by day and hunt after dark. A small
     * per-agent offset staggers shift changes so a post is never empty at the hand-over.
     */
    public static String activity(String faction, String role, boolean nightShift, double hour, long id) {
        double h = ((hour + (id % 7) * 0.11) % 24 + 24) % 24;
        if ("infected".equals(faction)) return h >= 20 || h < 5 ? "hunt" : "wander";
        boolean onDuty = nightShift ? (h >= 18 || h < 6) : (h >= 6 && h < 18);
        if (onDuty) return role;
        double off = nightShift ? (h - 6 + 24) % 24 : (h - 18 + 24) % 24;   // hours since the shift ended
        return off < 3 ? "rest" : off < 10.5 ? "sleep" : "rest";
    }

    public static boolean asleep(String activity) { return "sleep".equals(activity); }
    public static boolean offDuty(String activity) { return "sleep".equals(activity) || "rest".equals(activity); }

    private static final String[] GIVEN = {"Anton", "Boris", "Dima", "Fyodor", "Grisha", "Ilya", "Kolya", "Lev", "Misha", "Nikita", "Oleg", "Petro", "Roman", "Sasha", "Taras", "Vadim", "Yaroslav", "Zhenya", "Olena", "Daria", "Halyna", "Ksenia", "Larysa", "Marta"};
    private static final String[] FAMILY = {"Kovalenko", "Petrenko", "Shevchuk", "Bondar", "Tkachenko", "Melnyk", "Moroz", "Lysenko", "Hrytsenko", "Savchenko", "Rudenko", "Marchenko", "Sokolov", "Volkov", "Orlov", "Zaitsev"};

    /** 0.6: American names, and a sex that goes with them (see {@link Names}); the tables above are what 0.5 used. */
    public static String nameFor(long id) { return Names.full(id); }

    // ------------------------------------------------------------------ seeding
    /** The Zone's starting population: garrisons at their posts by district influence, the infected at their nests. */
    public static Agents seed(World w, ZoneState z, long seed) { return seed(w, z, seed, k -> 1f); }

    /**
     * The same survey with the profile's population settings applied: {@code pop} gives a multiplier for "all" and
     * for each of the five groups (1 = the Zone as designed). Chosen before the profile is created; the strengths it
     * produces become the baseline the world drifts back toward.
     */
    public static Agents seed(World w, ZoneState z, long seed, java.util.function.Function<String, Float> pop) {
        float all = pop.apply("all");
        Agents a = new Agents();
        for (int cx = 0; cx < CELLS; cx++) for (int cz = 0; cz < CELLS; cz++) {
            float x = cx * CELL + CELL / 2f, zz = cz * CELL + CELL / 2f;
            District d = w.district(x, zz);
            Rng r = new Rng(seed ^ (cx * 131L + cz * 7919L));
            float inf = z.infected[d.ordinal()] * (d == District.WILDERNESS ? 0.5f : 1f);
            long salt = seed * 31 + cx * 131L + cz * 7919L;
            Rng extra = new Rng(salt ^ 0x6A09E667F3BCC909L);
            int n0 = Math.round(inf * 6 + r.nextFloat() * 2);
            a.people(w, "infected", cx, cz, r, extra, 0, n0, scaled(n0, all * pop.apply("infected"), salt));
            for (int f = 0; f < 4; f++) {
                float c = z.influence[d.ordinal()][f];
                int m = c > 0.3f ? Math.round(c * 5 * r.nextFloat() * d.def.patrol + (r.chance(c) ? 1 : 0)) : (c > 0.18f && r.chance(c * 0.5f * d.def.patrol) ? 1 : 0);
                if (d == District.WILDERNESS) m = r.chance(0.15) ? 1 : 0;
                int keep = scaled(m, all * pop.apply(FACTION[1 + f]), salt + (f + 1) * 104729L);
                long squad = keep > 0 ? a.nextId * 1000 + f : 0;
                Agent lead = null;
                for (int i = 0; i < Math.max(m, keep); i++) {
                    // the designed members draw from the survey's dice whether or not they are kept, so nothing downstream moves
                    Rng src = i < m ? r : extra;
                    Agent g = a.spawn(w, FACTION[1 + f], cx, cz, src, squad);
                    float ox = i == 0 ? 0 : src.range(-3f, 3f), oz = i == 0 ? 0 : src.range(-3f, 3f);    // the first of a squad is where the squad is
                    if (i >= keep) { a.agents.remove(g); a.nextId--; continue; }
                    if (lead == null) lead = g; else { g.homeX = lead.homeX + ox; g.homeZ = lead.homeZ + oz; g.x = g.homeX; g.z = g.homeZ; if (i < 2) g.role = lead.role; }
                }
            }
        }
        for (int f = 0; f < 5; f++) a.baseline[f] = a.living(FACTION[f]);
        return a;
    }

    private Agent spawn(World w, String faction, int cx, int cz, Rng r, long squad) {
        Agent g = new Agent();
        g.id = nextId++;
        g.faction = faction;
        g.squad = squad;
        g.name = nameFor(g.id);
        boolean inf = g.infected();
        float x = cx * CELL + r.range(4f, CELL - 4), z = cz * CELL + r.range(4f, CELL - 4);
        String kind = "";
        if (w != null) {
            List<MapData.Point> cand = new ArrayList<>();
            for (MapData.Point p : inf ? w.map.nests : w.map.posts) if (p.x / CELL == cx && p.z / CELL == cz && (inf || p.faction.equals(faction))) cand.add(p);
            if (!cand.isEmpty() && r.chance(0.75)) { MapData.Point p = r.pick(cand); x = p.x + r.range(-2f, 2f); z = p.z + r.range(-2f, 2f); kind = p.kind; }
            else { MapData.Node n = w.map.nearestNode(x, z); if (n != null && Math.abs(n.x - x) < CELL && Math.abs(n.z - z) < CELL) { x = n.x + r.range(-3f, 3f); z = n.z + r.range(-3f, 3f); } }
        }
        g.x = g.homeX = x; g.z = g.homeZ = z;
        boolean post = List.of("checkpoint", "tower", "guard", "leader", "sniper", "camp", "restricted").contains(kind);
        g.role = inf ? "wander" : post ? "guard" : "survivors".equals(faction) ? "scavenge" : "patrol";
        // staffing: garrisons keep roughly a third of their people on nights; bandits prefer the dark; loners sleep at night
        float nightShare = switch (faction) { case "military", "mercenaries" -> 0.36f; case "raiders" -> 0.55f; default -> 0.12f; };
        g.night = !inf && r.chance(nightShare);
        g.hunger = r.nextFloat() * 0.4f; g.fatigue = r.nextFloat() * 0.3f;
        agents.add(g);
        return g;
    }

    // ------------------------------------------------------------------ director queries
    public Agent byId(long id) { for (Agent a : agents) if (a.id == id) return a; return null; }

    /** Offline, living agents per faction in a cell: {infected, military, raiders, survivors, mercenaries}. */
    public int[] counts(int cell) {
        int[] n = new int[5];
        for (Agent a : agents) if (a.alive && a.actorId == 0 && a.cell() == cell) n[a.fi()]++;
        return n;
    }

    public Map<Integer, int[]> allCounts() {
        Map<Integer, int[]> m = new HashMap<>();
        for (Agent a : agents) if (a.alive && a.actorId == 0) m.computeIfAbsent(a.cell(), k -> new int[5])[a.fi()]++;
        return m;
    }

    /** An offline agent of a faction in a cell to bring online (same squad as 'prefer' first), or null. */
    public Agent take(int cell, String faction, long preferSquad) {
        Agent any = null;
        for (Agent a : agents) {
            if (!a.alive || a.actorId != 0 || a.cell() != cell || !a.faction.equals(faction)) continue;
            if (preferSquad != 0 && a.squad == preferSquad) return a;
            if (any == null) any = a;
        }
        return any;
    }

    /** Moves one offline agent of a faction from one cell into another (migration, dispatch, escalation). */
    public boolean shift(int from, int to, String faction, Rng r) {
        for (Agent a : agents) {
            if (!a.alive || a.actorId != 0 || a.cell() != from || !a.faction.equals(faction)) continue;
            a.x = (to / CELLS) * CELL + r.range(8f, CELL - 8); a.z = (to % CELLS) * CELL + r.range(8f, CELL - 8);
            a.activity = "travel";
            return true;
        }
        return false;
    }

    public void kill(long id) { Agent a = byId(id); if (a != null && a.alive) { a.alive = false; a.actorId = 0; deaths++; } }

    public int living() { int n = 0; for (Agent a : agents) if (a.alive) n++; return n; }
    public int living(String faction) { int n = 0; for (Agent a : agents) if (a.alive && a.faction.equals(faction)) n++; return n; }

    // ------------------------------------------------------------------ offline simulation
    private static boolean hostile(String a, String b) {
        if (a.equals(b)) return false;
        if (a.equals("infected") || b.equals("infected")) return true;
        return pair(a, b, "military", "raiders") || pair(a, b, "survivors", "raiders") || pair(a, b, "raiders", "mercenaries");
    }
    private static boolean pair(String a, String b, String p, String q) { return a.equals(p) && b.equals(q) || a.equals(q) && b.equals(p); }
    private static float power(Agent a) {
        float base = switch (a.faction) { case "mercenaries" -> 1.4f; case "military" -> 1.2f; case "raiders" -> 1.0f; case "survivors" -> 0.8f; default -> 0.34f; };   // one armed human is worth about three of the dead
        boolean atPost = "guard".equals(a.role) && Math.abs(a.x - a.homeX) + Math.abs(a.z - a.homeZ) < 16;
        return base * a.hp * (asleep(a.activity) ? 0.55f : "rest".equals(a.activity) ? 0.8f : 1f) * (atPost ? 1.4f : 1f) * (0.6f + 0.4f * a.morale) * (1 - 0.3f * a.fatigue);
    }

    /**
     * Advances every offline agent by a span of game time starting at 'hour'. frozen cells (the ones
     * loaded round the player) are left to the full simulation. w may be null (headless tests).
     */
    public void step(World w, ZoneState z, double hour, float hours, Rng r, java.util.function.IntPredicate frozen) {
        hoursSimulated += hours;
        for (Agent a : agents) {
            if (!a.alive || a.actorId != 0 || frozen != null && frozen.test(a.cell())) continue;
            String act = activity(a.faction, a.role, a.night, hour, a.id);
            a.activity = act;
            switch (act) {
                case "sleep" -> { home(a, r, 1.5f); a.fatigue = Math.max(0, a.fatigue - 0.16f * hours); a.hp = Math.min(1, a.hp + 0.05f * hours); }
                case "rest" -> { home(a, r, 4); a.fatigue = Math.max(0, a.fatigue - 0.06f * hours); a.hp = Math.min(1, a.hp + 0.02f * hours); if (a.hunger > 0.5f) a.hunger = Math.max(0, a.hunger - 0.5f); }
                case "guard" -> { home(a, r, 3); a.fatigue = Math.min(1, a.fatigue + 0.05f * hours); }
                case "patrol" -> { roam(w, a, r, 70); a.fatigue = Math.min(1, a.fatigue + 0.07f * hours); }
                case "scavenge" -> {
                    roam(w, a, r, 95);
                    a.fatigue = Math.min(1, a.fatigue + 0.08f * hours);
                    // scavenging feeds you when the district still has something to find
                    District d = w == null ? District.MARKET : w.district(a.x, a.z);
                    if (r.chance(Math.min(0.9, 0.25 * hours * (0.4 + z.supply[d.ordinal()][0])))) a.hunger = Math.max(0, a.hunger - 0.5f);
                }
                case "hunt" -> toward(a, r, nearestPrey(a), 48 * Math.min(1.5f, hours));
                default -> roam(w, a, r, a.infected() ? 30 : 50);
            }
            a.hunger = Math.min(1.2f, a.hunger + 0.045f * hours);
            if (a.hunger > 1 && !a.infected()) { a.hp -= 0.03f * hours; a.morale = Math.max(0.1f, a.morale - 0.05f * hours); }
            a.morale = Math.min(1, a.morale + 0.01f * hours);
            if (a.hp <= 0) { a.alive = false; deaths++; }
        }
        encounters(w, z, hour, hours, r, frozen);
        arrivals(w, z, hour, hours, r);
    }

    private static void home(Agent a, Rng r, float spread) { a.x = a.homeX + r.range(-spread, spread); a.z = a.homeZ + r.range(-spread, spread); }

    private static void roam(World w, Agent a, Rng r, float leash) {
        float x = a.homeX + r.range(-leash, leash), z = a.homeZ + r.range(-leash, leash);
        if (w != null) { MapData.Node n = w.map.nearestNode(x, z); if (n != null && Math.abs(n.x - a.homeX) < leash * 1.3f && Math.abs(n.z - a.homeZ) < leash * 1.3f) { x = n.x + r.range(-4f, 4f); z = n.z + r.range(-4f, 4f); } }
        a.x = Math.max(4, Math.min(World.W - 4, x)); a.z = Math.max(4, Math.min(World.D - 4, z));
    }

    private float[] nearestPrey(Agent a) {
        float bd = 200 * 200; float[] best = null;
        for (Agent o : agents) {
            if (!o.alive || o.infected() == a.infected() || !hostile(a.faction, o.faction)) continue;
            float d = (o.x - a.x) * (o.x - a.x) + (o.z - a.z) * (o.z - a.z);
            if (d < bd) { bd = d; best = new float[]{o.x, o.z}; }
        }
        return best;
    }

    private static void toward(Agent a, Rng r, float[] t, float dist) {
        if (t == null) { a.x += r.range(-20f, 20f); a.z += r.range(-20f, 20f); }
        else { float dx = t[0] - a.x, dz = t[1] - a.z, l = (float) Math.sqrt(dx * dx + dz * dz); if (l > 1) { float k = Math.min(1, dist / l); a.x += dx * k; a.z += dz * k; } }
        a.x = Math.max(4, Math.min(World.W - 4, a.x)); a.z = Math.max(4, Math.min(World.D - 4, a.z));
    }

    /** Hostile groups sharing a cell may find each other; the fight is resolved by strength and its dead stay dead. */
    private void encounters(World w, ZoneState z, double hour, float hours, Rng r, java.util.function.IntPredicate frozen) {
        Map<Integer, List<Agent>> byCell = new HashMap<>();
        for (Agent a : agents) if (a.alive && a.actorId == 0) byCell.computeIfAbsent(a.cell(), k -> new ArrayList<>()).add(a);
        boolean night = hour % 24 >= 20 || hour % 24 < 5;
        for (Map.Entry<Integer, List<Agent>> e : byCell.entrySet()) {
            if (frozen != null && frozen.test(e.getKey())) continue;
            List<Agent> here = e.getValue();
            Agent a = null, b = null;
            for (Agent p : here) { for (Agent q : here) if (hostile(p.faction, q.faction)) { a = p; b = q; break; } if (a != null) break; }
            if (a == null) continue;
            boolean infectedInvolved = a.infected() || b.infected();
            // most hours nothing happens: groups sharing a 64 m cell usually never see each other
            if (!r.chance(Math.min(0.6, 0.022 * hours * (infectedInvolved ? (night ? 1.6 : 0.55) : night ? 0.7 : 1)))) continue;
            String fa = a.faction, fb = b.faction;
            List<Agent> sa = new ArrayList<>(), sb = new ArrayList<>();
            float pa = 0, pb = 0;
            for (Agent p : here) { if (p.faction.equals(fa)) { sa.add(p); pa += power(p); } else if (p.faction.equals(fb)) { sb.add(p); pb += power(p); } }
            pa *= 0.75f + r.nextFloat() * 0.5f; pb *= 0.75f + r.nextFloat() * 0.5f;
            boolean aWins = pa >= pb;
            List<Agent> win = aWins ? sa : sb, lose = aWins ? sb : sa;
            float ratio = Math.min(pa, pb) / Math.max(0.01f, Math.max(pa, pb));
            // the beaten side breaks and runs long before it is wiped out
            boolean overrun = (aWins ? fa : fb).equals("infected");   // people who lose to the dead mostly get away
            int lost = lose.size() >= 4 ? Math.max(1, Math.round(lose.size() * (overrun ? 0.2f : 0.22f + 0.3f * (1 - ratio)))) : (!overrun || r.chance(0.5) ? 1 : 0), cost = win.size() >= 3 ? Math.round(win.size() * ratio * 0.25f) : (r.chance(ratio * 0.3) ? 1 : 0);
            float bx = 0, bz = 0;
            for (int i = 0; i < lose.size(); i++) {
                Agent p = lose.get(i);
                if (i < lost) { p.alive = false; deaths++; }
                if (i == 0) { bx = p.x; bz = p.z; }
                else { p.hp = Math.max(0.15f, p.hp - r.nextFloat() * 0.4f); p.morale = Math.max(0.05f, p.morale - 0.35f); p.x += r.range(-CELL, CELL); p.z += r.range(-CELL, CELL); toward(p, r, new float[]{p.homeX, p.homeZ}, 30); }
            }
            for (int i = 0; i < win.size(); i++) { Agent p = win.get(i); if (i < cost) { p.alive = false; deaths++; } else { p.hp = Math.max(0.2f, p.hp - r.nextFloat() * 0.25f * ratio); p.kills += i < lost ? 1 : 0; p.morale = Math.min(1, p.morale + 0.1f); } }
            encounters++;
            if (w != null && z != null && lost + cost > 0) {
                String wf = aWins ? fa : fb, lf = aWins ? fb : fa;
                District d = w.district(bx, bz);
                z.log(lf.equals("infected") || !wf.equals("infected") ? "battle" : "overrun", d, (int) bx, (int) bz, wf.equals("infected") ? lf : wf, wf.equals("infected") ? "infected" : lf,
                        ZoneState.cap(wf) + (night ? " ambushed " : " drove off ") + ZoneState.cap(lf) + " in the " + d.display + " (" + lost + " dead" + (cost > 0 ? ", " + cost + " lost" : "") + ")");
            }
        }
    }

    /** The Zone is not sealed: at dawn a few newcomers walk in at the fringe, toward each side's baseline strength. */
    private void arrivals(World w, ZoneState z, double hour, float hours, Rng r) {
        double h = hour % 24;
        if (!(h < 6 && h + hours >= 6)) return;
        for (int f = 0; f < 5; f++) {
            // each side is topped up toward the strength it started with: the fringe leaks in both directions
            int have = living(FACTION[f]), add = Math.min(f == 0 ? 8 : 3, Math.max(0, (baseline[f] - have + 5) / 6));
            for (int i = 0; i < add; i++) {
                boolean edgeX = r.chance(0.5);
                int cx = edgeX ? (r.chance(0.5) ? 1 : CELLS - 2) : 1 + r.nextInt(CELLS - 2), cz = edgeX ? 1 + r.nextInt(CELLS - 2) : (r.chance(0.5) ? 1 : CELLS - 2);
                Agent g = spawn(w, FACTION[f], cx, cz, r, 0);
                g.activity = "travel";
                arrivals++;
            }
        }
        // the dead are forgotten after a while so the list does not grow without bound
        if (agents.size() > 1400) agents.removeIf(a -> !a.alive);
    }

    // ------------------------------------------------------------------ persistence
    public Map<String, Object> toJson() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("nextId", String.valueOf(nextId));
        m.put("deaths", deaths);
        m.put("encounters", encounters);
        m.put("hours", hoursSimulated);
        List<Object> bl = new ArrayList<>();
        for (int b : baseline) bl.add(b);
        m.put("baseline", bl);
        List<Object> l = new ArrayList<>();
        for (Agent a : agents) {
            if (!a.alive) continue;
            l.add(a.id + ";" + a.faction + ";" + a.role + ";" + Math.round(a.x) + ";" + Math.round(a.z) + ";" + Math.round(a.homeX) + ";" + Math.round(a.homeZ) + ";" + Math.round(a.hp * 100) + ";"
                    + Math.round(a.hunger * 100) + ";" + Math.round(a.fatigue * 100) + ";" + Math.round(a.morale * 100) + ";" + (a.night ? 1 : 0) + ";" + a.squad + ";" + a.kills);
        }
        m.put("agents", l);
        return m;
    }

    public static Agents fromJson(Map<String, Object> m) {
        Agents a = new Agents();
        if (m == null) return a;
        a.nextId = absentialis.engine.util.Json.lng(m, "nextId", 1);
        a.deaths = absentialis.engine.util.Json.integer(m, "deaths", 0);
        a.encounters = absentialis.engine.util.Json.integer(m, "encounters", 0);
        a.hoursSimulated = absentialis.engine.util.Json.num(m, "hours", 0);
        List<Object> bl = absentialis.engine.util.Json.list(m, "baseline");
        for (int f = 0; f < 5 && f < bl.size(); f++) a.baseline[f] = ((Number) bl.get(f)).intValue();
        for (Object o : absentialis.engine.util.Json.list(m, "agents")) {
            String[] s = String.valueOf(o).split(";");
            if (s.length < 14) continue;
            Agent g = new Agent();
            g.id = Long.parseLong(s[0]); g.faction = s[1]; g.role = s[2];
            g.x = Integer.parseInt(s[3]); g.z = Integer.parseInt(s[4]); g.homeX = Integer.parseInt(s[5]); g.homeZ = Integer.parseInt(s[6]);
            g.hp = Integer.parseInt(s[7]) / 100f; g.hunger = Integer.parseInt(s[8]) / 100f; g.fatigue = Integer.parseInt(s[9]) / 100f; g.morale = Integer.parseInt(s[10]) / 100f;
            g.night = s[11].equals("1"); g.squad = Long.parseLong(s[12]); g.kills = Integer.parseInt(s[13]);
            g.name = nameFor(g.id);
            a.agents.add(g);
            a.nextId = Math.max(a.nextId, g.id + 1);
        }
        return a;
    }
}
