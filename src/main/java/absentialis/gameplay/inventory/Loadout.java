package absentialis.gameplay.inventory;

import absentialis.engine.util.Json;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a survivor carries (addendum P2.3): equipment slots, the attaché case grid (12x16 base,
 * upgradeable to 16x20), three 1x1 charm slots and the space-free pouches (treasures, keys,
 * craft materials, documents). Every item lives in exactly one of these places at a time.
 */
public final class Loadout {
    public static final String[] SLOTS = {"primary", "sidearm", "melee", "head", "face", "armor", "backpack"};
    public static final int CHARM_SLOTS = 3, QUICK_SLOTS = 4;
    /** The case starts at 6 rows x 10 columns (Rank 1) and tops out at 9 x 13 (Rank 20). */
    public static final int CASE_W = 10, CASE_H = 6, CASE_MAX_W = 13, CASE_MAX_H = 9;

    public final Container caseGrid = new Container("case", CASE_W, CASE_H);
    public final Map<String, ItemInstance> slots = new LinkedHashMap<>();
    public final ItemInstance[] charms = new ItemInstance[CHARM_SLOTS];
    /** Quick-use bindings (weapon wheel slots 5-8) by instance uid; 0 = empty. */
    public final long[] quick = new long[QUICK_SLOTS];
    public final List<ItemInstance> treasures = new ArrayList<>(), keys = new ArrayList<>(), craft = new ArrayList<>(), documents = new ArrayList<>();

    public Loadout() { caseGrid.label = "Attaché Case"; for (String s : SLOTS) slots.put(s, null); }

    /** Which space-free pouch an item belongs in, or null if it lives in the case grid. */
    public List<ItemInstance> pouchFor(ItemDef d) {
        switch (d.category) {
            case "treasure": return treasures;
            case "key": return keys;
            case "herb": return craft;
            case "document": return documents;
            case "material": return craft;
            default: return null;
        }
    }

    public static String pouchName(ItemDef d) {
        switch (d.category) {
            case "treasure": return "treasures";
            case "key": return "keys";
            case "herb": case "material": return "craft";
            case "document": return "documents";
            default: return null;
        }
    }

    public List<ItemInstance> pouch(String name) {
        switch (name) {
            case "treasures": return treasures;
            case "keys": return keys;
            case "craft": return craft;
            case "documents": return documents;
            default: return null;
        }
    }

    public ItemInstance slot(String s) { return slots.get(s); }

    /** The equipped backpack's own grid (extra storage that changes the carried silhouette), or null. */
    public Container packGrid() { ItemInstance b = slots.get("backpack"); return b == null ? null : b.grid; }

    public void resizeCase(int w, int h) {
        caseGrid.w = Math.max(caseGrid.w, Math.min(CASE_MAX_W, w));
        caseGrid.h = Math.max(caseGrid.h, Math.min(CASE_MAX_H, h));
    }

    /** Sum of a charm effect across the three charm slots (e.g. "wear" -> -0.05). */
    public float charm(String effect) {
        float s = 0;
        for (ItemInstance c : charms) if (c != null && c.def().charm != null && c.def().charm.effect.equals(effect)) s += c.def().charm.amount;
        return s;
    }

    /** Every owned instance (top level and nested). */
    public List<ItemInstance> all() {
        List<ItemInstance> out = new ArrayList<>();
        for (ItemInstance i : slots.values()) if (i != null) i.collect(out);
        for (ItemInstance i : caseGrid.items) i.collect(out);
        for (ItemInstance c : charms) if (c != null) out.add(c);
        for (List<ItemInstance> p : List.of(treasures, keys, craft, documents)) for (ItemInstance i : p) i.collect(out);
        return out;
    }

    public float mass() {
        float m = caseGrid.mass();
        for (ItemInstance i : slots.values()) if (i != null) m += i.mass();
        for (List<ItemInstance> p : List.of(treasures, keys, craft, documents)) for (ItemInstance i : p) m += i.mass();
        return m;
    }

    public ItemInstance findUid(long uid) {
        for (ItemInstance i : all()) if (i.uid == uid) return i;
        return null;
    }

    public int count(String defId) {
        int n = 0;
        for (ItemInstance i : all()) if (i.defId.equals(defId) && i.parent == null) n += i.qty;
        return n;
    }

    /** Removes everything (death / prestige). Returns the top-level instances that were removed. */
    public List<ItemInstance> strip(boolean keepPouches) {
        List<ItemInstance> lost = new ArrayList<>();
        for (String s : SLOTS) { ItemInstance i = slots.put(s, null); if (i != null) { i.detachLocation(); lost.add(i); } }
        for (ItemInstance i : new ArrayList<>(caseGrid.items)) { caseGrid.take(i); lost.add(i); }
        for (int k = 0; k < charms.length; k++) if (charms[k] != null && !keepPouches) { charms[k].detachLocation(); lost.add(charms[k]); charms[k] = null; }
        if (!keepPouches) for (List<ItemInstance> p : List.of(treasures, craft, documents)) { lost.addAll(p); p.clear(); }
        java.util.Arrays.fill(quick, 0);
        return lost;
    }

    // ------------------------------------------------------------------ persistence
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("caseW", caseGrid.w);
        m.put("caseH", caseGrid.h);
        Map<String, Object> sl = new LinkedHashMap<>();
        for (Map.Entry<String, ItemInstance> e : slots.entrySet()) if (e.getValue() != null) sl.put(e.getKey(), e.getValue().toJson());
        m.put("slots", sl);
        m.put("case", list(caseGrid.items));
        List<Object> ch = new ArrayList<>();
        for (ItemInstance c : charms) ch.add(c == null ? null : c.toJson());
        m.put("charms", ch);
        List<Object> q = new ArrayList<>();
        for (long u : quick) q.add(String.valueOf(u));
        m.put("quick", q);
        m.put("treasures", list(treasures));
        m.put("keys", list(keys));
        m.put("craft", list(craft));
        m.put("documents", list(documents));
        return m;
    }

    private static List<Object> list(List<ItemInstance> l) { List<Object> o = new ArrayList<>(); for (ItemInstance i : l) o.add(i.toJson()); return o; }

    @SuppressWarnings("unchecked")
    public static Loadout fromJson(Map<String, Object> m) {
        Loadout l = new Loadout();
        if (m == null) return l;
        l.caseGrid.w = Json.integer(m, "caseW", CASE_W);
        l.caseGrid.h = Json.integer(m, "caseH", CASE_H);
        for (Map.Entry<String, Object> e : Json.obj(m, "slots").entrySet()) {
            ItemInstance i = ItemInstance.fromJson((Map<String, Object>) e.getValue());
            if (i != null && l.slots.containsKey(e.getKey())) { l.slots.put(e.getKey(), i); i.slot = e.getKey(); i.slotOwner = l; }
        }
        for (Object o : Json.list(m, "case")) { ItemInstance i = ItemInstance.fromJson((Map<String, Object>) o); if (i != null) l.caseGrid.restore(i); }
        List<Object> ch = Json.list(m, "charms");
        for (int k = 0; k < Math.min(CHARM_SLOTS, ch.size()); k++) if (ch.get(k) instanceof Map) {
            ItemInstance c = ItemInstance.fromJson((Map<String, Object>) ch.get(k));
            if (c != null) { l.charms[k] = c; c.slot = "charm" + k; c.slotOwner = l; }
        }
        List<Object> q = Json.list(m, "quick");
        for (int k = 0; k < Math.min(QUICK_SLOTS, q.size()); k++) try { l.quick[k] = Long.parseLong(String.valueOf(q.get(k))); } catch (NumberFormatException ignored) {}
        readPouch(m, "treasures", l.treasures, l);
        readPouch(m, "keys", l.keys, l);
        readPouch(m, "craft", l.craft, l);
        readPouch(m, "documents", l.documents, l);
        return l;
    }

    @SuppressWarnings("unchecked")
    private static void readPouch(Map<String, Object> m, String k, List<ItemInstance> into, Loadout l) {
        for (Object o : Json.list(m, k)) { ItemInstance i = ItemInstance.fromJson((Map<String, Object>) o); if (i != null) { i.slot = "pouch:" + k; i.slotOwner = l; into.add(i); } }
    }
}
