package absentialis.sim;

import absentialis.engine.util.Json;
import absentialis.engine.util.Rng;
import absentialis.world.District;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategic-tier A-Life (GDD 10.2): per-district faction influence, infected pressure and supply
 * stockpiles, the world clock and infrastructure state. Advanced in coarse hour steps between and
 * during raids. Raid outcomes (kills, noise, looting, repairs) are fed in as explicit reports so
 * one player decision propagates to territory, spawns, prices and the map.
 */
public final class ZoneState {
    public static final String[] FACTIONS = {"military", "raiders", "survivors", "mercenaries"};
    public static final String[] SUPPLY = {"food", "water", "medicine", "ammo", "fuel", "materials", "tech"};

    public final float[][] influence = new float[District.values().length][FACTIONS.length];
    public final float[] infected = new float[District.values().length];
    public final float[][] supply = new float[District.values().length][SUPPLY.length];
    public final boolean[] power = new boolean[District.values().length];
    public double hour = 9.0;
    public int day = 1;
    public float weatherRain;
    /** 1.0: the kind of weather over the Zone - clear, fog, rain, storm or snow (the Astratto weather system's kinds). weatherRain is kept in step with it for older saves and code. */
    public String weather = "clear";
    public void setWeather(String kind) { weather = kind == null ? "clear" : kind.toLowerCase(); weatherRain = weather.equals("storm") ? 1f : weather.equals("rain") ? 0.8f : 0.15f; }
    public final List<String> news = new ArrayList<>();
    /** Infected ecology inputs: lingering contamination and unattended corpses per district (both decay). */
    public final float[] contamination = new float[District.values().length], corpses = new float[District.values().length];
    /** Strategic squads with persistent ids, and the log of resolved off-screen events (newest first). */
    public final List<ALife.Squad> squads = new ArrayList<>();
    public final List<ALife.Event> log = new ArrayList<>();
    public long nextSquadId = 1;

    public static int fi(String faction) { for (int i = 0; i < FACTIONS.length; i++) if (FACTIONS[i].equals(faction)) return i; return -1; }
    public static int si(String cat) { for (int i = 0; i < SUPPLY.length; i++) if (SUPPLY[i].equals(cat)) return i; return SUPPLY.length - 1; }

    public static ZoneState fresh() {
        ZoneState z = new ZoneState();
        for (District d : District.values()) {
            int i = d.ordinal();
            for (int f = 0; f < FACTIONS.length; f++) z.influence[i][f] = d.def.influence(FACTIONS[f]);
            z.infected[i] = d.def.infected;
            for (int s = 0; s < SUPPLY.length; s++) z.supply[i][s] = 1f;
            z.power[i] = d == District.CIVIC;
        }
        return z;
    }

    /** The faction with the most influence in a district (null for contested / infected-held). */
    public String controller(District d) {
        int i = d.ordinal(), best = -1;
        float bv = 0.3f;
        for (int f = 0; f < FACTIONS.length; f++) if (influence[i][f] > bv) { bv = influence[i][f]; best = f; }
        return best < 0 ? null : FACTIONS[best];
    }

    public float control(District d, String faction) { int f = fi(faction); return f < 0 ? 0 : influence[d.ordinal()][f]; }

    public float daylight() { return daylightAt(hour); }

    /** Daylight 0.08 (night) .. 1 (day) at an hour of the clock, with a two-hour dawn and dusk. */
    public static float daylightAt(double hour) {
        double h = ((hour % 24) + 24) % 24;
        if (h < 5 || h > 21) return 0.08f;
        if (h < 7) return (float) (0.08 + (h - 5) / 2 * 0.92);
        if (h > 19) return (float) (1 - (h - 19) / 2 * 0.92);
        return 1f;
    }

    /**
     * 0.9: how much mist the hour puts in the air, 0 (a clear afternoon) to 1 (the thick of the morning). The night holds
     * most of it, it gathers to its thickest just after first light, burns off by mid-morning and comes back at dusk.
     */
    public static float mistAt(double hour) {
        double h = ((hour % 24) + 24) % 24;
        if (h < 4.5) return 0.7f;
        if (h < 6.5) return (float) (0.7 + (h - 4.5) / 2 * 0.3);
        if (h < 10) { double k = (h - 6.5) / 3.5; return (float) (1 - k * k * (3 - 2 * k)); }
        if (h < 18.5) return 0f;
        if (h < 21.5) { double k = (h - 18.5) / 3; return (float) (0.7 * k * k * (3 - 2 * k)); }
        return 0.7f;
    }

    // ------------------------------------------------------------------ raid reports
    /** A witnessed kill of a faction member: their district hold weakens, rivals and infected gain. */
    public void reportKill(District d, String faction, boolean witnessed) {
        int f = fi(faction);
        if (f < 0) return;
        int i = d.ordinal();
        influence[i][f] = Math.max(0.02f, influence[i][f] - (witnessed ? 0.035f : 0.02f));
        infected[i] = Math.min(1f, infected[i] + 0.01f); // corpses draw infected
    }

    /** A body left in the open: carrion draws the infected (ecology input, decays). */
    public void reportCorpse(District d) { corpses[d.ordinal()] = Math.min(12, corpses[d.ordinal()] + 1); }

    /** A rupture or spill: contaminated ground breeds infected until it fades. */
    public void reportContamination(District d, float amount) { contamination[d.ordinal()] = Math.min(8, contamination[d.ordinal()] + amount); }

    /** Appends a resolved event to the strategic log (kept short; the newest is first). */
    public ALife.Event log(String type, District d, int x, int z, String a, String b, String text) {
        ALife.Event e = new ALife.Event(day, (int) hour, type, d.name(), x, z, a == null ? "" : a, b == null ? "" : b, text);
        log.add(0, e);
        while (log.size() > 60) log.remove(log.size() - 1);
        return e;
    }

    public void reportInfectedKill(District d) { infected[d.ordinal()] = Math.max(0.05f, infected[d.ordinal()] - 0.008f); }

    /** Loud raids redistribute infected toward the noise (not infinite spawns). */
    public void reportNoise(District d, float amount) {
        int i = d.ordinal();
        float pull = Math.min(0.15f, amount * 0.0004f);
        for (District o : District.values()) if (o != d && infected[o.ordinal()] > 0.1f) {
            float mv = Math.min(infected[o.ordinal()] - 0.1f, pull / 4);
            infected[o.ordinal()] -= mv;
            infected[i] = Math.min(1f, infected[i] + mv);
        }
    }

    /** Looting depletes local stockpiles for that supply category (prices react). */
    public void reportLoot(District d, String cat, float amount) {
        int i = d.ordinal(), s = si(cat);
        supply[i][s] = Math.max(0.15f, supply[i][s] - amount * 0.01f);
    }

    public void news(String s) { news.add(0, "Day " + day + " " + String.format("%02d:00", (int) hour % 24) + " - " + s); while (news.size() > 30) news.remove(news.size() - 1); }

    // ------------------------------------------------------------------ strategic tick
    /** Advances the strategic layer by whole hours (deterministic given seed + state). */
    public void advance(int hours, long seed) {
        for (int h = 0; h < hours; h++) {
            hour += 1;
            if (hour >= 24) { hour -= 24; day++; }
            Rng r = new Rng(seed ^ (day * 977L + (long) hour * 31L));
            // 1.0: the weather turns over by the hour. It mostly holds; when it changes, what it changes to depends on what it was
            // (a storm comes out of rain and goes back to it; snow comes at night and in the small hours, and lies till morning)
            if (r.nextFloat() < 0.26f) {
                float w = r.nextFloat();
                boolean cold = hour < 7 || hour >= 21;
                String next = switch (weather) {
                    case "rain" -> w < 0.30f ? "storm" : w < 0.62f ? "clear" : w < 0.80f ? "fog" : "rain";
                    case "storm" -> w < 0.70f ? "rain" : w < 0.85f ? "clear" : "storm";
                    case "fog" -> w < 0.55f ? "clear" : w < 0.80f ? "rain" : cold ? "snow" : "fog";
                    case "snow" -> w < 0.40f ? "fog" : w < 0.75f ? "clear" : "snow";
                    default -> w < 0.34f ? "rain" : w < 0.58f ? "fog" : w < 0.70f && cold ? "snow" : "clear";
                };
                setWeather(next);
            }
            for (District d : District.values()) {
                int i = d.ordinal();
                // factions consolidate toward their home district, drift elsewhere
                float total = 0;
                for (int f = 0; f < FACTIONS.length; f++) {
                    float target = d.def.influence(FACTIONS[f]);
                    influence[i][f] += (target - influence[i][f]) * 0.01f + (r.nextFloat() - 0.5f) * 0.006f;
                    influence[i][f] = Math.max(0.02f, influence[i][f]);
                    total += influence[i][f];
                }
                if (total > 1f) for (int f = 0; f < FACTIONS.length; f++) influence[i][f] /= total;
                // infected recover slowly toward ecology baseline; strong military presence suppresses
                // ecology: population grows on contaminated ground and unattended corpses, both of which fade
                float base = d.def.infected * 0.92f;
                infected[i] += (base - infected[i]) * 0.004f - control(d, "military") * 0.002f + contamination[i] * 0.006f + corpses[i] * 0.0025f;
                infected[i] = Math.max(0.05f, Math.min(1f, infected[i]));
                contamination[i] *= 0.97f;
                corpses[i] *= 0.9f;
                // supplies regenerate; faction control determines what flows
                for (int s = 0; s < SUPPLY.length; s++) supply[i][s] = Math.min(1.4f, supply[i][s] + 0.004f);
                String c = controller(d);
                if ("raiders".equals(c)) supply[i][si("food")] = Math.max(0.2f, supply[i][si("food")] - 0.003f);
                if ("survivors".equals(c)) supply[i][si("medicine")] = Math.min(1.4f, supply[i][si("medicine")] + 0.002f);
            }
            ALife.tick(this, r);
        }
    }

    /** Snapshot of influence controllers to detect territory flips for the news feed. */
    public String[] controllers() {
        String[] s = new String[District.values().length];
        for (District d : District.values()) s[d.ordinal()] = controller(d);
        return s;
    }

    public void announceFlips(String[] before) {
        for (District d : District.CITY) {
            String a = before[d.ordinal()], b = controller(d);
            if (a == null ? b != null : !a.equals(b)) news((b == null ? "Control of " + d.display + " is contested" : cap(b) + " seized the " + d.display));
        }
    }

    /** Display name: factions use their in-world names, everything else is capitalised. */
    public static String cap(String s) {
        if (s == null || s.isEmpty()) return "";
        switch (s) { case "raiders": return "Bandits"; case "survivors": return "Loners"; case "mercenaries": return "Mercenaries"; default: break; }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** One member of a faction, as a word: "a mercenary", "a soldier". (0.9: this used to be made by cutting the last letter off the faction's name, which gave "mercenarie" and "militar".) */
    public static String one(String faction) {
        if (faction == null) return "stranger";
        return switch (faction) { case "mercenaries" -> "mercenary"; case "military" -> "soldier"; case "raiders" -> "bandit"; case "survivors" -> "loner"; case "infected" -> "infected"; default -> faction.endsWith("ies") ? faction.substring(0, faction.length() - 3) + "y" : faction.endsWith("s") ? faction.substring(0, faction.length() - 1) : faction; };
    }

    /** Supply availability multipliers for loot rolls in a district. */
    public Map<String, Float> availability(District d) {
        Map<String, Float> m = new LinkedHashMap<>();
        for (int s = 0; s < SUPPLY.length; s++) m.put(SUPPLY[s], supply[d.ordinal()][s] * d.def.lootWeight(SUPPLY[s]));
        return m;
    }

    // ------------------------------------------------------------------ persistence
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> inf = new LinkedHashMap<>(), sup = new LinkedHashMap<>(), infd = new LinkedHashMap<>(), pw = new LinkedHashMap<>();
        for (District d : District.values()) {
            List<Object> l = new ArrayList<>();
            for (float v : influence[d.ordinal()]) l.add((double) v);
            inf.put(d.name(), l);
            List<Object> s = new ArrayList<>();
            for (float v : supply[d.ordinal()]) s.add((double) v);
            sup.put(d.name(), s);
            infd.put(d.name(), (double) infected[d.ordinal()]);
            pw.put(d.name(), power[d.ordinal()]);
        }
        m.put("influence", inf);
        m.put("supply", sup);
        m.put("infected", infd);
        m.put("power", pw);
        m.put("hour", hour);
        m.put("day", day);
        m.put("rain", (double) weatherRain);
        m.put("weather", weather);
        m.put("news", new ArrayList<Object>(news));
        List<Object> ct = new ArrayList<>(), cp = new ArrayList<>(), sq = new ArrayList<>(), lg = new ArrayList<>();
        for (District d : District.values()) { ct.add((double) contamination[d.ordinal()]); cp.add((double) corpses[d.ordinal()]); }
        for (ALife.Squad s : squads) sq.add(s.pack());
        for (ALife.Event e : log) lg.add(e.pack());
        m.put("contamination", ct);
        m.put("corpses", cp);
        m.put("squads", sq);
        m.put("log", lg);
        m.put("nextSquadId", String.valueOf(nextSquadId));
        return m;
    }

    public static ZoneState fromJson(Map<String, Object> m) {
        ZoneState z = fresh();
        if (m == null || m.isEmpty()) return z;
        Map<String, Object> inf = Json.obj(m, "influence"), sup = Json.obj(m, "supply"), infd = Json.obj(m, "infected"), pw = Json.obj(m, "power");
        for (District d : District.values()) {
            List<Object> l = Json.list(inf, d.name());
            for (int f = 0; f < Math.min(l.size(), FACTIONS.length); f++) z.influence[d.ordinal()][f] = ((Number) l.get(f)).floatValue();
            List<Object> s = Json.list(sup, d.name());
            for (int k = 0; k < Math.min(s.size(), SUPPLY.length); k++) z.supply[d.ordinal()][k] = ((Number) s.get(k)).floatValue();
            z.infected[d.ordinal()] = (float) Json.num(infd, d.name(), z.infected[d.ordinal()]);
            z.power[d.ordinal()] = Json.bool(pw, d.name(), z.power[d.ordinal()]);
        }
        z.hour = Json.num(m, "hour", 9);
        z.day = Json.integer(m, "day", 1);
        z.weatherRain = (float) Json.num(m, "rain", 0);
        z.weather = m.containsKey("weather") ? String.valueOf(m.get("weather")) : z.weatherRain > 0.5f ? "rain" : "clear";
        z.news.addAll(Json.strList(m, "news"));
        List<Object> ct = Json.list(m, "contamination"), cp = Json.list(m, "corpses");
        for (int i = 0; i < Math.min(ct.size(), z.contamination.length); i++) z.contamination[i] = ((Number) ct.get(i)).floatValue();
        for (int i = 0; i < Math.min(cp.size(), z.corpses.length); i++) z.corpses[i] = ((Number) cp.get(i)).floatValue();
        for (Object o : Json.list(m, "squads")) { ALife.Squad s = ALife.Squad.unpack(String.valueOf(o)); if (s != null) z.squads.add(s); }
        for (Object o : Json.list(m, "log")) { ALife.Event e = ALife.Event.unpack(String.valueOf(o)); if (e != null) z.log.add(e); }
        z.nextSquadId = Json.lng(m, "nextSquadId", 1);
        return z;
    }
}
