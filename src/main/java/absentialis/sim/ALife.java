package absentialis.sim;

import absentialis.engine.util.Rng;
import absentialis.world.District;
import java.util.ArrayList;
import java.util.List;

/**
 * A-Life foundation (iteration P5), strategic tier. The city is simulated in three tiers:
 *
 *   ACTIVE    near the player (scene.Raid, tier 0): navigation, tactics, perception, combat,
 *             animation, physics.
 *   REGIONAL  loaded district outside the engagement (scene.Raid, tier 1): coarse travel, goals,
 *             patrol routes and dispatch orders at a reduced tick, no perception or combat.
 *   STRATEGIC unloaded (this class): squads with persistent ids resolve travel, conflict, supply
 *             and infected pressure once per world hour, deterministically, and log every outcome.
 *
 * State is compact: a squad is five numbers, an event a short record. When a raid loads a district
 * it reads the log and spawns evidence consistent with it - corpses and smoke where a fight was
 * resolved, sandbags where a roadblock went up.
 */
public final class ALife {
    private ALife() {}

    /** A strategic-tier squad: who, where, how many, and where it is heading. */
    public static final class Squad {
        public final long id;
        public final String faction;
        public int district, size, goal;
        public Squad(long id, String faction, int district, int size) { this.id = id; this.faction = faction; this.district = district; this.size = size; this.goal = district; }
        public String pack() { return id + ";" + faction + ";" + district + ";" + size + ";" + goal; }
        public static Squad unpack(String s) {
            try { String[] p = s.split(";"); Squad q = new Squad(Long.parseLong(p[0]), p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3])); q.goal = Integer.parseInt(p[4]); return q; }
            catch (RuntimeException e) { return null; }
        }
    }

    /** A resolved off-screen outcome. x/z is where its evidence belongs. */
    public record Event(int day, int hour, String type, String district, int x, int z, String a, String b, String text) {
        public String pack() { return day + ";" + hour + ";" + type + ";" + district + ";" + x + ";" + z + ";" + a + ";" + b + ";" + text.replace(';', ','); }
        public static Event unpack(String s) {
            try { String[] p = s.split(";", 9); return new Event(Integer.parseInt(p[0]), Integer.parseInt(p[1]), p[2], p[3], Integer.parseInt(p[4]), Integer.parseInt(p[5]), p[6], p[7], p[8]); }
            catch (RuntimeException e) { return null; }
        }
        /** Hours since this event at the given clock. */
        public int age(int nowDay, double nowHour) { return (nowDay - day) * 24 + (int) nowHour - hour; }
    }

    public static final int SQUADS_PER_FACTION = 3, SQUAD_SIZE = 4;
    /** District adjacency (by ordinal): the routes squads and infected can take in one hour. */
    private static final int[][] ADJ = new int[District.values().length][];
    /** Street-level anchor points per district where off-screen events leave their evidence. */
    private static final int[][][] ANCHORS = new int[District.values().length][][];

    static { configure(null); }

    /**
     * Takes district adjacency and street anchors from a world plan (the generator calls this when it finishes);
     * with no plan it derives them from the default seed, so the strategic layer works before any world exists.
     * Adjacency follows the plan's geometry: the centre touches all four outer districts, each outer district
     * touches its two neighbours round the ring, and every outer district opens on the Wilderness.
     */
    public static synchronized void configure(absentialis.world.gen.WorldPlan plan) {
        if (plan == null) plan = absentialis.world.gen.WorldPlan.of(1337L, absentialis.world.gen.GenDefs.get());
        District[] bySlot = new District[4];
        District centre = District.OLD_QUARTER;
        for (absentialis.world.gen.WorldPlan.Region rg : plan.regions) { if (rg.slot < 0) centre = rg.district; else bySlot[rg.slot] = rg.district; }
        for (int i = 0; i < 4; i++) link(bySlot[i], centre, bySlot[(i + 1) & 3], bySlot[(i + 3) & 3], District.WILDERNESS);
        link(centre, bySlot);
        link(District.WILDERNESS, bySlot);
        java.util.List<int[]>[] pts = new java.util.List[District.values().length];
        for (int i = 0; i < pts.length; i++) pts[i] = new java.util.ArrayList<>();
        for (absentialis.world.gen.WorldPlan.RoadPlan rp : plan.roads) {
            if (rp.district == null) continue;
            pts[rp.district.ordinal()].add(new int[]{rp.r.cx(), rp.r.cz()});
            if (rp.kind.equals("spine")) {
                pts[rp.district.ordinal()].add(rp.alongX ? new int[]{rp.r.x0() + rp.r.w() / 4, rp.r.cz()} : new int[]{rp.r.cx(), rp.r.z0() + rp.r.d() / 4});
                pts[rp.district.ordinal()].add(rp.alongX ? new int[]{rp.r.x1() - rp.r.w() / 4, rp.r.cz()} : new int[]{rp.r.cx(), rp.r.z1() - rp.r.d() / 4});
            }
        }
        pts[District.WILDERNESS.ordinal()].add(new int[]{640, 180});
        pts[District.WILDERNESS.ordinal()].add(new int[]{660, 181});
        for (int i = 0; i < pts.length; i++) { if (pts[i].isEmpty()) pts[i].add(new int[]{352, 352}); ANCHORS[i] = pts[i].toArray(new int[0][]); }
    }

    private static void link(District d, District... to) { int[] a = new int[to.length]; for (int i = 0; i < to.length; i++) a[i] = to[i].ordinal(); ADJ[d.ordinal()] = a; }

    public static int[] adjacent(District d) { return ADJ[d.ordinal()]; }

    private static boolean hostile(String a, String b) {
        return pair(a, b, "military", "raiders") || pair(a, b, "survivors", "raiders") || pair(a, b, "raiders", "mercenaries");
    }
    private static boolean pair(String a, String b, String p, String q) { return a.equals(p) && b.equals(q) || a.equals(q) && b.equals(p); }

    private static District home(String faction) {
        for (District d : District.CITY) if (d.def.homeOf(faction)) return d;
        return District.MARKET;
    }

    /** One strategic hour. Deterministic for a given state and rng. */
    public static void tick(ZoneState z, Rng r) {
        District[] ds = District.values();
        // muster: every faction keeps a few squads in the field
        for (String f : ZoneState.FACTIONS) {
            int n = 0;
            for (Squad s : z.squads) if (s.faction.equals(f)) n++;
            if (n < SQUADS_PER_FACTION) {
                Squad s = new Squad(z.nextSquadId++, f, home(f).ordinal(), SQUAD_SIZE);
                z.squads.add(s);
            }
        }
        // travel toward goals along the adjacency graph
        for (Squad s : z.squads) {
            District here = ds[s.district];
            if (s.district == s.goal || r.chance(0.2)) s.goal = pickGoal(z, s, r);
            if (s.district != s.goal && r.chance(0.35)) {
                int[] adj = ADJ[s.district];
                int next = adj[r.nextInt(adj.length)];
                for (int a : adj) if (a == s.goal) next = a;
                if (next != District.WILDERNESS.ordinal() || s.goal == next) s.district = next;
            }
            // supply: what a squad does where it stands
            District d = ds[s.district];
            int i = d.ordinal();
            switch (s.faction) {
                case "raiders" -> { z.supply[i][ZoneState.si("food")] = Math.max(0.2f, z.supply[i][ZoneState.si("food")] - 0.004f); z.supply[i][ZoneState.si("fuel")] = Math.max(0.2f, z.supply[i][ZoneState.si("fuel")] - 0.003f); }
                case "survivors" -> z.supply[i][ZoneState.si("medicine")] = Math.min(1.4f, z.supply[i][ZoneState.si("medicine")] + 0.003f);
                case "military" -> { if (d != here && r.chance(0.12)) roadblock(z, s, d, r); }
                default -> z.supply[i][ZoneState.si("tech")] = Math.min(1.4f, z.supply[i][ZoneState.si("tech")] + 0.002f);
            }
            z.influence[i][ZoneState.fi(s.faction)] = Math.min(0.9f, z.influence[i][ZoneState.fi(s.faction)] + 0.0015f * s.size);
        }
        // conflict: hostile squads in the same district fight; the log remembers who lost what, where
        List<Squad> dead = new ArrayList<>();
        for (int a = 0; a < z.squads.size(); a++) for (int b = a + 1; b < z.squads.size(); b++) {
            Squad s = z.squads.get(a), t = z.squads.get(b);
            if (s.district != t.district || s.size <= 0 || t.size <= 0 || !hostile(s.faction, t.faction) || !r.chance(0.3)) continue;
            District d = ds[s.district];
            float ps = s.size * (0.6f + z.control(d, s.faction)) * r.range(0.6f, 1.4f), pt = t.size * (0.6f + z.control(d, t.faction)) * r.range(0.6f, 1.4f);
            Squad win = ps >= pt ? s : t, lose = win == s ? t : s;
            int losses = Math.min(lose.size, 1 + r.nextInt(2));
            lose.size -= losses;
            if (r.chance(0.3)) win.size = Math.max(1, win.size - 1);
            int[] at = anchor(d, r);
            int fw = ZoneState.fi(win.faction), fl = ZoneState.fi(lose.faction);
            z.influence[d.ordinal()][fw] = Math.min(0.9f, z.influence[d.ordinal()][fw] + 0.02f);
            z.influence[d.ordinal()][fl] = Math.max(0.02f, z.influence[d.ordinal()][fl] - 0.03f);
            z.corpses[d.ordinal()] = Math.min(12, z.corpses[d.ordinal()] + losses);
            z.log("battle", d, at[0], at[1], win.faction, lose.faction, ZoneState.cap(win.faction) + " beat " + ZoneState.cap(lose.faction) + " in the " + d.display + " (" + losses + " dead)");
            if (lose.size <= 0) { dead.add(lose); z.news(ZoneState.cap(lose.faction) + " squad wiped out in the " + d.display); }
        }
        // infected pressure: squads standing in an overrun district take losses
        for (Squad s : z.squads) {
            District d = ds[s.district];
            if (s.size > 0 && z.infected[d.ordinal()] > 0.6f && r.chance(0.08 + (z.infected[d.ordinal()] - 0.6f) * 0.3f)) {
                s.size--;
                int[] at = anchor(d, r);
                z.corpses[d.ordinal()] = Math.min(12, z.corpses[d.ordinal()] + 1);
                z.log("overrun", d, at[0], at[1], s.faction, "infected", "Infected mauled a " + ZoneState.cap(s.faction) + " squad in the " + d.display);
                if (s.size <= 0) dead.add(s);
            }
        }
        z.squads.removeAll(dead);
        // migration: infected flow from crowded districts to their quieter neighbours
        float[] delta = new float[ds.length];
        for (District d : ds) for (int n : ADJ[d.ordinal()]) {
            float diff = z.infected[d.ordinal()] - z.infected[n];
            if (diff > 0.15f) { float mv = diff * 0.006f; delta[d.ordinal()] -= mv; delta[n] += mv; }
        }
        for (int i = 0; i < ds.length; i++) z.infected[i] = Math.max(0.05f, Math.min(1f, z.infected[i] + delta[i]));
    }

    private static int pickGoal(ZoneState z, Squad s, Rng r) {
        District h = home(s.faction);
        switch (s.faction) {
            case "raiders": {
                // predators go where the food is, unless the cordon is strong there
                int best = h.ordinal(); float bv = -1;
                for (District d : District.CITY) { float v = z.supply[d.ordinal()][ZoneState.si("food")] - z.control(d, "military") + r.nextFloat() * 0.4f; if (v > bv) { bv = v; best = d.ordinal(); } }
                return best;
            }
            case "military": return r.chance(0.6) ? h.ordinal() : ADJ[h.ordinal()][r.nextInt(ADJ[h.ordinal()].length)];
            case "mercenaries": return r.chance(0.8) ? h.ordinal() : District.OLD_QUARTER.ordinal();
            default: return r.chance(0.5) ? h.ordinal() : District.OLD_QUARTER.ordinal();
        }
    }

    private static void roadblock(ZoneState z, Squad s, District d, Rng r) {
        if (d == District.WILDERNESS) return;
        for (Event e : z.log) if (e.type().equals("roadblock") && e.district().equals(d.name()) && e.age(z.day, z.hour) < 48) return;
        int[] at = anchor(d, r);
        z.log("roadblock", d, at[0], at[1], s.faction, "", "Military roadblock raised in the " + d.display);
    }

    private static int[] anchor(District d, Rng r) { int[][] a = ANCHORS[d.ordinal()]; int[] p = a[r.nextInt(a.length)]; return new int[]{p[0] + r.range(-6, 6), p[1] + r.range(-2, 2)}; }

    /** Total living members across strategic squads of a faction. */
    public static int strength(ZoneState z, String faction) { int n = 0; for (Squad s : z.squads) if (s.faction.equals(faction)) n += s.size; return n; }
}
