package absentialis.gameplay.items;

import absentialis.engine.util.Json;
import absentialis.engine.util.Rng;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-driven content: item definitions, loot tables, NPC kits and recipes, loaded from
 * /content/*.json and validated at startup (GDD 20.6) for missing links, impossible dimensions and
 * invalid ammunition compatibility.
 */
public final class Content {
    /**
     * 0.8: an item by whatever a person would type for it - its id ("melee.knife"), the end of its id ("knife"), or its
     * name with or without the spaces ("pink spinel", "pink_spinel", "pinkspinel"). Null when nothing matches, or when
     * what was typed fits more than one item and none of them exactly.
     */
    public static ItemDef find(String typed) {
        if (typed == null || typed.isBlank()) return null;
        ItemDef exact = def(typed);
        if (exact != null) return exact;
        String t = typed.trim().toLowerCase(), flat = t.replace(" ", "").replace("_", "").replace("-", "");
        ItemDef hit = null;
        int hits = 0;
        for (ItemDef d : ITEMS.values()) {
            String id = d.id.toLowerCase(), tail = id.substring(id.indexOf('.') + 1), name = d.name == null ? "" : d.name.toLowerCase();
            if (id.equals(t) || tail.equals(t) || name.equals(t) || name.replace(" ", "").replace("-", "").equals(flat) || tail.replace("_", "").equals(flat)) return d;
            if (tail.contains(t) || name.contains(t)) { hit = d; hits++; }
        }
        return hits == 1 ? hit : null;
    }

    /** Every name {@link #find} accepts for an item beyond its id: the tail of the id and the name joined with underscores. */
    public static java.util.List<String> names() {
        java.util.List<String> l = new java.util.ArrayList<>(ITEMS.keySet());
        for (ItemDef d : ITEMS.values()) if (d.name != null) l.add(d.name.toLowerCase().replace(' ', '_'));
        return l;
    }

    public static final Map<String, ItemDef> ITEMS = new LinkedHashMap<>();
    public static final Map<String, LootTable> LOOT = new LinkedHashMap<>();
    public static final Map<String, Map<String, List<String>>> KITS = new LinkedHashMap<>();
    public static final List<Recipe> RECIPES = new ArrayList<>();
    public static final List<String> problems = new ArrayList<>();
    private static boolean loaded;

    /** Definitions merged or renamed by the v0.3 roster: old save ids resolve to their successor. */
    public static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("ammo.9x19", "ammo.handgun"), Map.entry("ammo.45", "ammo.magnum"), Map.entry("ammo.545", "ammo.rifle"), Map.entry("ammo.556", "ammo.rifle"),
            Map.entry("ammo.556_tracer", "ammo.rifle"), Map.entry("ammo.762x54", "ammo.rifle"), Map.entry("ammo.12buck", "ammo.shells"), Map.entry("ammo.12slug", "ammo.shells"),
            Map.entry("ammo.762x51", "ammo.smg"), Map.entry("weapon.lmg74", "weapon.tommy"), Map.entry("mat.gunpowder", "mat.gunpowder_s"), Map.entry("treasure.gem_red", "treasure.ruby"),
            Map.entry("mag.pm9", "ammo.handgun"), Map.entry("mag.akr", "ammo.rifle"), Map.entry("mag.stanag", "ammo.rifle"), Map.entry("mag.stanag60", "ammo.rifle"), Map.entry("mag.belt", "ammo.smg"));

    /** The current id for a possibly retired one. */
    public static String canon(String id) { return id == null ? null : ALIASES.getOrDefault(id, id); }

    public static final class LootTable {
        public String id;
        public int minRolls, maxRolls;
        public float empty;
        public final List<Object[]> entries = new ArrayList<>(); // [id, weight, min, max]
    }

    public static final class Recipe {
        public String id, name, where;
        public final List<Object[]> inputs = new ArrayList<>(); // [id, qty]
        public String outId;
        public int outQty;
        public float time;
        public String facility() { return where.contains(":") ? where.substring(0, where.indexOf(':')) : where; }
        public int level() { return where.contains(":") ? Integer.parseInt(where.substring(where.indexOf(':') + 1)) : 0; }
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Map<String, Object> items = Json.parseObject(read("/content/items.json"));
        for (Object o : Json.list(items, "items")) {
            @SuppressWarnings("unchecked") ItemDef d = ItemDef.parse((Map<String, Object>) o);
            if (d.id == null) { problems.add("item without id"); continue; }
            if (ITEMS.containsKey(d.id)) problems.add("duplicate item id " + d.id);
            ITEMS.put(d.id, d);
        }
        // 0.8: the evidence of the five investigations (content/story.json) - documents with a text of their own
        for (Map<String, Object> ev : absentialis.gameplay.missions.Story.evidenceItems()) { ItemDef d = ItemDef.parse(ev); ITEMS.put(d.id, d); }
        // attachments are catalogue definitions, not loot: registered so instances, saves and models can refer to them
        for (ItemDef a : absentialis.gameplay.weapons.Attachments.all()) ITEMS.put(a.id, a);
        Map<String, Object> loot = Json.parseObject(read("/content/loot.json"));
        for (Map.Entry<String, Object> e : Json.obj(loot, "tables").entrySet()) {
            @SuppressWarnings("unchecked") Map<String, Object> t = (Map<String, Object>) e.getValue();
            LootTable lt = new LootTable();
            lt.id = e.getKey();
            List<Object> rolls = Json.list(t, "rolls");
            lt.minRolls = rolls.size() > 0 ? ((Number) rolls.get(0)).intValue() : 1;
            lt.maxRolls = rolls.size() > 1 ? ((Number) rolls.get(1)).intValue() : lt.minRolls;
            lt.empty = (float) Json.num(t, "empty", 0.3);
            for (Object en : Json.list(t, "entries")) {
                List<?> l = (List<?>) en;
                lt.entries.add(new Object[]{l.get(0).toString(), ((Number) l.get(1)).floatValue(), ((Number) l.get(2)).intValue(), ((Number) l.get(3)).intValue()});
            }
            LOOT.put(lt.id, lt);
        }
        for (Map.Entry<String, Object> e : Json.obj(loot, "npcKits").entrySet()) {
            @SuppressWarnings("unchecked") Map<String, Object> k = (Map<String, Object>) e.getValue();
            Map<String, List<String>> kit = new LinkedHashMap<>();
            for (Map.Entry<String, Object> s : k.entrySet()) {
                List<String> vals = new ArrayList<>();
                if (s.getValue() instanceof List) for (Object v : (List<?>) s.getValue()) vals.add(v.toString());
                else vals.add(String.valueOf(s.getValue()));
                kit.put(s.getKey(), vals);
            }
            KITS.put(e.getKey(), kit);
        }
        Map<String, Object> rec = Json.parseObject(read("/content/recipes.json"));
        for (Object o : Json.list(rec, "recipes")) {
            @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
            Recipe r = new Recipe();
            r.id = Json.str(m, "id", "?");
            r.name = Json.str(m, "name", r.id);
            r.where = Json.str(m, "where", "field");
            r.time = (float) Json.num(m, "time", 0);
            for (Object in : Json.list(m, "inputs")) { List<?> l = (List<?>) in; r.inputs.add(new Object[]{l.get(0).toString(), ((Number) l.get(1)).intValue()}); }
            List<Object> out = Json.list(m, "output");
            r.outId = out.get(0).toString();
            r.outQty = ((Number) out.get(1)).intValue();
            RECIPES.add(r);
        }
        validate();
        System.out.printf("[Content] %d items, %d loot tables, %d recipes, %d problems%n", ITEMS.size(), LOOT.size(), RECIPES.size(), problems.size());
        for (String p : problems) System.err.println("[Content] " + p);
    }

    private static String read(String path) {
        try (InputStream in = Content.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Cannot read " + path, e); }
    }

    /** Validation report: missing links, impossible dimensions, invalid ammo compatibility. */
    private static void validate() {
        int maxW = 10, maxH = 40; // largest legal container: stash
        for (ItemDef d : ITEMS.values()) {
            if (d.w > maxW && d.h > maxW) problems.add(d.id + " exceeds every legal container");
            if (d.weapon != null && !d.weapon.melee()) {
                if (d.weapon.caliber == null) problems.add(d.id + " weapon without caliber");
                boolean ammoExists = false;
                for (ItemDef a : ITEMS.values()) if (a.ammo != null && a.ammo.caliber.equals(d.weapon.caliber)) ammoExists = true;
                if (!ammoExists) problems.add(d.id + " has no ammunition of caliber " + d.weapon.caliber);
                if (!d.weapon.internal()) problems.add(d.id + " has no internal capacity (v0.3 weapons load loose rounds)");
                if (false) {
                    boolean magExists = false;
                    for (ItemDef m : ITEMS.values()) if (m.magazine != null && m.magazine.magType.equals(d.weapon.magType)) {
                        magExists = true;
                        if (!m.magazine.caliber.equals(d.weapon.caliber)) problems.add(m.id + " caliber mismatch with " + d.id);
                    }
                    if (!magExists) problems.add(d.id + " has no magazine of type " + d.weapon.magType);
                }
            }
            if (d.magazine != null) {
                boolean ok = false;
                for (ItemDef a : ITEMS.values()) if (a.ammo != null && a.ammo.caliber.equals(d.magazine.caliber)) ok = true;
                if (!ok) problems.add(d.id + " magazine caliber has no ammunition");
            }
        }
        for (LootTable t : LOOT.values()) for (Object[] e : t.entries) if (!ITEMS.containsKey((String) e[0])) problems.add("loot table " + t.id + " references missing item " + e[0]);
        for (Map<String, List<String>> kit : KITS.values()) for (Map.Entry<String, List<String>> s : kit.entrySet()) {
            if (s.getKey().equals("loot")) { for (String v : s.getValue()) if (!LOOT.containsKey(v)) problems.add("kit loot table missing " + v); continue; }
            for (String v : s.getValue()) if (!v.isEmpty() && !ITEMS.containsKey(v)) problems.add("kit references missing item " + v);
        }
        for (Recipe r : RECIPES) {
            if (!ITEMS.containsKey(r.outId)) problems.add("recipe " + r.id + " outputs missing item " + r.outId);
            for (Object[] in : r.inputs) if (!ITEMS.containsKey((String) in[0])) problems.add("recipe " + r.id + " needs missing item " + in[0]);
        }
    }

    /** One loot-tier draw at the published drop rates. */
    public static int rollTier(Rng r) {
        float p = r.nextFloat();
        for (int t = 0; t < 6; t++) { p -= ItemDef.TIER_RATES[t]; if (p < 0) return t; }
        return 0;
    }

    public static ItemDef def(String id) { ItemDef d = ITEMS.get(id); return d != null || id == null ? d : ITEMS.get(ALIASES.get(id)); }

    /** Finds an ammunition definition for a caliber (first match). */
    public static ItemDef ammoFor(String caliber) {
        for (ItemDef d : ITEMS.values()) if (d.ammo != null && d.ammo.caliber.equals(caliber)) return d;
        return null;
    }

    public static ItemDef magFor(String magType) {
        for (ItemDef d : ITEMS.values()) if (d.magazine != null && d.magazine.magType.equals(magType)) return d;
        return null;
    }

    /** Supply category used by district stockpiles for dynamic loot availability and prices. */
    public static String supplyCategory(ItemDef d) {
        switch (d.category) {
            case "medical": return "medicine";
            case "food": return d.food != null && d.food.hydration > d.food.energy ? "water" : "food";
            case "ammo": case "magazine": case "explosive": return "ammo";
            case "weapon": case "attachment": case "armor": return "ammo";
            case "valuable": case "document": return "tech";
            case "material": return "fuel".equals(d.id.substring(4)) ? "fuel" : "materials";
            default: return "materials";
        }
    }

    /**
     * Rolls a loot table. availability: map supply category -> multiplier (district stockpiles /
     * faction looting); depletion in [0,1] raises the chance the container is already picked clean.
     */
    public static List<Object[]> roll(String tableId, Rng r, Map<String, Float> availability, float depletion) {
        List<Object[]> out = new ArrayList<>();
        LootTable t = LOOT.get(tableId);
        if (t == null) t = LOOT.get("crate");
        if (r.chance(Math.min(0.95, t.empty + depletion * 0.5))) return out;
        int rolls = r.range(t.minRolls, t.maxRolls);
        for (int i = 0; i < rolls; i++) {
            // tier first (50 / 20 / 15 / 10 / 4 / 1), then an entry of that tier by weight and local supply
            int tier = rollTier(r);
            boolean[] has = new boolean[6];
            for (Object[] e : t.entries) has[ITEMS.get((String) e[0]).tier] = true;
            while (tier > 0 && !has[tier]) tier--;
            while (tier < 5 && !has[tier]) tier++;
            float total = 0;
            float[] w = new float[t.entries.size()];
            for (int k = 0; k < w.length; k++) {
                Object[] e = t.entries.get(k);
                ItemDef d = ITEMS.get((String) e[0]);
                if (d.tier != tier) continue;
                float mul = availability == null ? 1 : availability.getOrDefault(supplyCategory(d), 1f);
                w[k] = (Float) e[1] * Math.max(0.05f, mul);
                total += w[k];
            }
            float pick = r.nextFloat() * total;
            for (int k = 0; k < w.length; k++) {
                pick -= w[k];
                if (pick <= 0) {
                    Object[] e = t.entries.get(k);
                    out.add(new Object[]{e[0], r.range((Integer) e[2], (Integer) e[3])});
                    break;
                }
            }
        }
        return out;
    }
}
