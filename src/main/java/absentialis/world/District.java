package absentialis.world;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The six environments of the Zone (0.5): five developed districts and the Wilderness that surrounds them.
 * Downtown takes the centre; Residential, Industrial, Munincipal and the Barrens (0.8: until then Governmental and
 * the Outskirts - lore.md names them) take the four outer slots,
 * each on the side of the city whose terrain suits it (see world.gen.WorldPlan).
 *
 * A district's atmosphere and economy are defined here. Its two landmarks, its headquarters and the faction
 * that holds it are read from {@code content/districts.json} - the same file the generator builds from - so the
 * strategic layer and the world can never disagree about who holds what. Enum constant names are the stable save
 * keys (they predate 0.5); {@link #display} is what the player reads.
 */
public enum District {
    //            display        fogR  fogG  fogB  density tintR tintG tintB  ambience
    WILDERNESS   ("Wilderness",  0.62f, 0.64f, 0.58f, 1.25f, 1.00f, 1.00f, 0.92f, "wind", new Def(
            "The country beyond the quarantine wall: woods, drowned hollows, abandoned farmsteads, the eastern highway. Thin loot, thin patrols, a way out when the city closes - and the radiation climbs the further you go.",
            "none", 0.30f, 0.25f, 0.10f, false)
            .landmarks("Eastern Highway", "Abandoned Farmstead", "Hunter Camp")
            .loot("food", 1.3f, "water", 0.9f, "medicine", 0.6f, "ammo", 0.6f, "fuel", 0.8f, "materials", 0.9f, "tech", 0.4f)
            .influence("military", 0.10f, "raiders", 0.10f, "survivors", 0.12f, "mercenaries", 0.04f)
            .exits("east_road:PERMANENT", "west_track:PERMANENT")),
    OLD_QUARTER  ("Downtown",    0.50f, 0.54f, 0.62f, 1.05f, 0.92f, 0.95f, 1.06f, "bells", new Def(
            "The commercial core: offices, apartment blocks and shops round the police headquarters and the market hall. Vertical, cover-rich, loud - and where the valuables are.",
            "mercenaries", 0.40f, 0.85f, 0.85f, false)
            .loot("food", 1.2f, "water", 1.0f, "medicine", 1.0f, "ammo", 0.9f, "fuel", 0.6f, "materials", 0.8f, "tech", 1.45f)
            .influence("military", 0.20f, "raiders", 0.10f, "survivors", 0.12f, "mercenaries", 0.46f)
            .exits("helipad:SIGNALED", "pump_tunnel:CONDITIONAL")),
    INDUSTRIAL   ("Industrial",  0.60f, 0.55f, 0.46f, 1.20f, 1.06f, 0.98f, 0.88f, "machines", new Def(
            "The power station and the research laboratory, and the belt that served them: warehouses, loading yards, workshops, substations. Fuel and parts, machinery and long sight lines - bandit country, with contractors dug in at the lab.",
            "raiders", 0.42f, 1.00f, 0.55f, false)
            .loot("food", 0.7f, "water", 0.8f, "medicine", 0.8f, "ammo", 1.0f, "fuel", 1.6f, "materials", 1.6f, "tech", 1.2f)
            .influence("military", 0.08f, "raiders", 0.46f, "survivors", 0.08f, "mercenaries", 0.30f)
            .exits("rail_freight:TIME_WINDOW", "sewer_outfall:PERMANENT")),
    MARKET       ("Residential", 0.66f, 0.60f, 0.52f, 0.85f, 1.06f, 1.00f, 0.92f, "city", new Def(
            "Streets of houses behind hedges and fences, corner shops, a pharmacy, the big house on its own grounds. Food and personal goods, short sight lines, back gardens and alleys - and people still living here.",
            "survivors", 0.38f, 0.70f, 0.40f, false)
            .loot("food", 1.5f, "water", 1.3f, "medicine", 1.15f, "ammo", 0.7f, "fuel", 0.7f, "materials", 0.9f, "tech", 0.8f)
            .influence("military", 0.10f, "raiders", 0.16f, "survivors", 0.50f, "mercenaries", 0.06f)
            .exits("fringe_breach:PERMANENT")),
    REDLINE      ("Barrens",     0.56f, 0.60f, 0.46f, 1.15f, 0.98f, 1.03f, 0.90f, "wind", new Def(
            "Where the city thins into farmland: the correctional complex, the big farm, barns, a roadhouse, cabins among the fields. Open ground and few walls; the gaol's old inmates hold the wire and the farm feeds whoever can keep it.",
            "raiders", 0.55f, 0.55f, 0.25f, false)
            .loot("food", 1.4f, "water", 1.0f, "medicine", 0.7f, "ammo", 0.8f, "fuel", 1.0f, "materials", 1.0f, "tech", 0.5f)
            .influence("military", 0.06f, "raiders", 0.42f, "survivors", 0.30f, "mercenaries", 0.04f)
            .exits("farm_track:PERMANENT", "jail_tunnel:CONDITIONAL")),
    CIVIC        ("Munincipal",  0.58f, 0.62f, 0.68f, 0.80f, 0.96f, 1.00f, 1.05f, "wind", new Def(
            "The garrison and the airfield, with the administration blocks, depots and checkpoints that serve them. Intelligence and materiel under military control - go where you are cleared to go.",
            "military", 0.28f, 1.25f, 0.35f, true)
            .loot("food", 0.8f, "water", 0.9f, "medicine", 1.0f, "ammo", 1.3f, "fuel", 0.9f, "materials", 0.7f, "tech", 1.35f)
            .influence("military", 0.66f, "raiders", 0.04f, "survivors", 0.06f, "mercenaries", 0.12f)
            .exits("airfield_charter:TIME_WINDOW", "base_checkpoint:CONDITIONAL", "cordon_gate:EMERGENCY"));

    /** Data-driven identity of a district. */
    public static final class Def {
        public final String role;
        /** The faction whose headquarters this district is (content/districts.json), and that headquarters' name. */
        public String home, headquarters = "";
        /** Infected pressure baseline 0..1, patrol density multiplier, ambient noise 0..1. */
        public final float infected, patrol, noise;
        /** Restricted areas inside are enforced (permit = standing). */
        public final boolean permits;
        public String[] landmarks = {};
        public String[] exits = {};
        private final Map<String, Float> loot = new LinkedHashMap<>(), influence = new LinkedHashMap<>();

        Def(String role, String home, float infected, float patrol, float noise, boolean permits) {
            this.role = role; this.home = home; this.infected = infected; this.patrol = patrol; this.noise = noise; this.permits = permits;
        }
        Def landmarks(String... l) { landmarks = l; return this; }
        Def exits(String... e) { exits = e; return this; }
        Def loot(Object... kv) { for (int i = 0; i < kv.length; i += 2) loot.put((String) kv[i], (Float) kv[i + 1]); return this; }
        Def influence(Object... kv) { for (int i = 0; i < kv.length; i += 2) influence.put((String) kv[i], (Float) kv[i + 1]); return this; }

        /** Loot-table weight for a supply category here (1 = neutral). */
        public float lootWeight(String category) { return loot.getOrDefault(category, 1f); }
        public Map<String, Float> lootWeights() { return loot; }
        /** Baseline influence a faction holds here with nothing disturbing it. */
        public float influence(String faction) { return influence.getOrDefault(faction, 0.05f); }
        public boolean homeOf(String faction) { return home.equals(faction); }
        /** Extraction ids of one type ("PERMANENT", "CONDITIONAL", "TIME_WINDOW", "SIGNALED", "EMERGENCY"). */
        public java.util.List<String> exitsOf(String type) {
            java.util.List<String> l = new java.util.ArrayList<>();
            for (String e : exits) if (e.endsWith(":" + type)) l.add(e.substring(0, e.indexOf(':')));
            return l;
        }
    }

    public final String display;
    public final float fogR, fogG, fogB, fogDensity, tintR, tintG, tintB;
    public final String ambience, resources;
    /** The district's primary faction: holder of its headquarters. Influence elsewhere is a separate, moving quantity (sim.ZoneState). */
    public String faction;
    public final Def def;

    District(String d, float fr, float fg, float fb, float den, float tr, float tg, float tb, String amb, Def def) {
        display = d; fogR = fr; fogG = fg; fogB = fb; fogDensity = den; tintR = tr; tintG = tg; tintB = tb;
        ambience = amb; this.def = def; faction = def.home;
        StringBuilder b = new StringBuilder();
        def.lootWeights().entrySet().stream().filter(e -> e.getValue() >= 1.25f).forEach(e -> b.append(b.length() > 0 ? ", " : "").append(e.getKey()));
        resources = b.length() == 0 ? "Scrap" : Character.toUpperCase(b.charAt(0)) + b.substring(1);
    }

    public static final District[] CITY = {OLD_QUARTER, INDUSTRIAL, MARKET, REDLINE, CIVIC};

    static {
        // headquarters ownership and landmark names come from the generator's own data
        try (java.io.InputStream in = District.class.getResourceAsStream("/content/districts.json")) {
            if (in != null) {
                java.util.Map<String, Object> root = absentialis.engine.util.Json.parseObject(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                for (Object o : absentialis.engine.util.Json.list(root, "districts")) {
                    @SuppressWarnings("unchecked") java.util.Map<String, Object> m = (java.util.Map<String, Object>) o;
                    District d = byName(absentialis.engine.util.Json.str(m, "id", ""));
                    if (d == null) continue;
                    String hq = absentialis.engine.util.Json.str(m, "hq", "");
                    java.util.List<String> names = new java.util.ArrayList<>();
                    for (Object lo : absentialis.engine.util.Json.list(m, "landmarks")) {
                        @SuppressWarnings("unchecked") java.util.Map<String, Object> lm = (java.util.Map<String, Object>) lo;
                        String name = absentialis.engine.util.Json.str(lm, "name", "");
                        names.add(name);
                        if (hq.equals(absentialis.engine.util.Json.str(lm, "id", ""))) d.def.headquarters = name;
                    }
                    d.def.landmarks = names.toArray(new String[0]);
                    d.def.home = absentialis.engine.util.Json.str(m, "faction", d.def.home);
                    d.faction = d.def.home;
                }
            }
        } catch (Exception e) {
            System.err.println("[World] content/districts.json could not be read for district ownership: " + e);
        }
    }

    public static District byName(String s) {
        for (District d : values()) if (d.name().equalsIgnoreCase(s) || d.display.equalsIgnoreCase(s)) return d;
        // the names two of them had before 0.8 (lore.md section 5 renamed them)
        if ("governmental".equalsIgnoreCase(s) || "government".equalsIgnoreCase(s) || "municipal".equalsIgnoreCase(s)) return CIVIC;
        if ("outskirts".equalsIgnoreCase(s)) return REDLINE;
        return null;
    }
}
