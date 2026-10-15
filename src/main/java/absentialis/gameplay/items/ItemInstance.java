package absentialis.gameplay.items;

import absentialis.engine.util.Json;
import absentialis.gameplay.inventory.Container;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A unique owned item (GDD 8.3): instance id, location, orientation, quantity, durability, loaded
 * ammunition, attachments and ownership flags. Inventory references instances, never definitions,
 * so two identical magazines can never share a round count.
 */
public final class ItemInstance {
    private static long nextUid = 1;
    public static synchronized long newUid() { return nextUid++; }
    public static synchronized void ensureUidAbove(long v) { if (nextUid <= v) nextUid = v + 1; }
    public static synchronized long peekUid() { return nextUid; }

    public final long uid;
    public final String defId;
    public int qty = 1;
    public float durability = 1f, maxDurability = 1f;
    /**
     * 1.1: as it left the factory. Set on a weapon that is somebody else's (scene.Raid arms people with these); the
     * survivor's upgrades (gameplay.weapons.Gunsmith) do not apply to it. It stops being true the moment the weapon
     * goes into anything of the survivor's.
     */
    public transient boolean factory;
    public int rounds;              // magazine rounds / internal tube rounds
    public String ammoId;           // ammo definition loaded in mag/tube
    public ItemInstance mag;        // magazine inserted in a weapon
    public final Map<String, ItemInstance> attachments = new LinkedHashMap<>();
    public int fireMode;
    public boolean jammed;
    public boolean chambered;       // detachable-mag weapons: a round (type = ammoId) sits in the chamber
    public String origin = "";      // raid id / source this instance came from (audit trail)
    public int uses = -1;           // remaining uses for multi-use consumables
    public Container grid;          // backpack storage
    public String ownerFaction;     // taken from a faction member (evidence when sold)
    public boolean fallback;        // free fallback-kit item: no resale value
    public boolean insured;
    public boolean foundInRaid;
    /** Purchased camouflage applied to this weapon ("gold"), or null for the factory finish. */
    public String camo;
    /** Rounds pushed out when a smaller magazine was fitted; the workbench hands them back as loose ammo. */
    public int spill;

    // ----- location (exactly one of these is set while the item is owned)
    public Container container;
    public int x, y;
    public boolean rotated;
    /** Set while loading a pre-v3 save (see Profile.fromJson). */
    public static boolean legacyOrientation;
    public String slot;             // equipped slot name
    public Object slotOwner;        // Loadout owning the slot
    public ItemInstance parent;     // weapon holding this as mag/attachment

    public ItemInstance(String defId) { this(newUid(), defId); }

    public ItemInstance(long uid, String defId) {
        this.uid = uid;
        this.defId = Content.canon(defId);
        ensureUidAbove(uid);
        ItemDef d = def();
        if (d != null) {
            if (d.medical != null && d.medical.uses > 1) uses = d.medical.uses;
            if (d.backpack != null) grid = new Container("bag:" + uid, d.backpack.gw, d.backpack.gh);
            if (d.armor != null) { durability = d.armor.durability; maxDurability = d.armor.durability; }
        }
    }

    public ItemDef def() { return Content.def(defId); }
    public String name() { ItemDef d = def(); return d == null ? defId : d.name; }

    /** Footprint width/height including rotation and length-adding attachments. */
    public int baseW() {
        ItemDef d = def();
        int w = d.w, h = d.h;
        int extra = 0;
        for (ItemInstance a : attachments.values()) if (a.def().attachment != null) extra += a.def().attachment.length;
        if (extra > 0) { if (h >= w) h += extra; else w += extra; }
        return w;
    }
    public int baseH() {
        ItemDef d = def();
        int w = d.w, h = d.h;
        int extra = 0;
        for (ItemInstance a : attachments.values()) if (a.def().attachment != null) extra += a.def().attachment.length;
        if (extra > 0) { if (h >= w) h += extra; else w += extra; }
        return h;
    }
    public int w() { return rotated ? baseH() : baseW(); }
    public int h() { return rotated ? baseW() : baseH(); }

    /** Total mass including contents (rounds, magazine, attachments, backpack contents). */
    public float mass() {
        ItemDef d = def();
        float m = d.mass * (d.stackable() ? qty : 1);
        if (rounds > 0 && ammoId != null && Content.def(ammoId) != null) m += rounds * Content.def(ammoId).mass;
        if (mag != null) m += mag.mass();
        if (chambered && ammoId != null && Content.def(ammoId) != null) m += Content.def(ammoId).mass;
        for (ItemInstance a : attachments.values()) m += a.mass();
        if (grid != null) for (ItemInstance i : grid.items) m += i.mass();
        return m;
    }

    public boolean isWeapon() { return def().weapon != null; }
    public boolean isMagazine() { return def().magazine != null; }

    /** Rounds available in a weapon (magazine or internal). */
    public int loadedRounds() {
        ItemDef d = def();
        if (d.weapon == null) return rounds;
        if (d.weapon.internal()) return rounds;
        return (mag == null ? 0 : mag.rounds) + (chambered ? 1 : 0);
    }

    /** Weapon/armor condition on the addendum's 0-100 scale. */
    public float condition() { return durability * 100f; }
    public void setCondition(float c) { durability = Math.max(0, Math.min(maxDurability, c / 100f)); }

    /** Stable key describing the visible configuration (icon / voxel model cache key). */
    public String visualKey() {
        StringBuilder b = new StringBuilder(defId);
        for (Map.Entry<String, ItemInstance> e : attachments.entrySet()) b.append('|').append(e.getKey()).append('=').append(e.getValue().defId);
        if (mag != null) b.append("|mag=").append(mag.defId);
        if (def().weapon != null) b.append("|c").append((int) (condition() / 25));
        if (camo != null) b.append("|camo=").append(camo);
        return b.toString();
    }

    public String loadedAmmoId() {
        ItemDef d = def();
        if (d.weapon != null && !d.weapon.internal()) return mag != null && mag.ammoId != null && mag.rounds > 0 ? mag.ammoId : (chambered ? ammoId : null);
        return ammoId;
    }

    public int capacity() {
        ItemDef d = def();
        if (d.magazine != null) return d.magazine.capacity;
        if (d.weapon != null && d.weapon.internal()) return absentialis.gameplay.weapons.Gunsmith.capacity(this);
        return 0;
    }

    public String fireModeName() {
        ItemDef d = def();
        if (d.weapon == null || d.weapon.modes.isEmpty()) return "";
        return d.weapon.modes.get(Math.floorMod(fireMode, d.weapon.modes.size()));
    }

    public ItemInstance attachment(String socket) { return attachments.get(socket); }

    public float attachmentSum(java.util.function.Function<ItemDef.Attachment, Float> f) {
        float s = 0;
        for (ItemInstance a : attachments.values()) if (a.def().attachment != null) s += f.apply(a.def().attachment);
        return s;
    }

    public boolean hasLight() { for (ItemInstance a : attachments.values()) if (a.def().attachment != null && a.def().attachment.light) return true; return false; }

    public float zoom() { float z = 1; for (ItemInstance a : attachments.values()) if (a.def().attachment != null) z = Math.max(z, a.def().attachment.zoom); return z; }

    public void detachLocation() { container = null; slot = null; slotOwner = null; parent = null; }

    /** Every instance nested inside this one (mag, attachments, backpack grid), recursively. */
    public void collect(List<ItemInstance> out) {
        out.add(this);
        if (mag != null) mag.collect(out);
        for (ItemInstance a : attachments.values()) a.collect(out);
        if (grid != null) for (ItemInstance i : new ArrayList<>(grid.items)) i.collect(out);
    }

    /** Value used by appraisal: base value scaled by condition plus contents. */
    public int baseValueTotal() {
        ItemDef d = def();
        float cond = d.weapon != null || d.armor != null ? Math.max(0.2f, durability / Math.max(0.01f, d.armor != null ? d.armor.durability : 1f)) : 1f;
        float v = d.value * (d.stackable() ? qty : 1) * cond;
        if (uses > 0 && d.medical != null && d.medical.uses > 1) v *= uses / (float) d.medical.uses;
        if (rounds > 0 && ammoId != null && Content.def(ammoId) != null) v += rounds * Content.def(ammoId).value;
        return Math.round(v);
    }

    // ------------------------------------------------------------------ serialization
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uid", String.valueOf(uid));
        m.put("def", defId);
        if (qty != 1) m.put("qty", qty);
        if (durability != 1f) m.put("dur", durability);
        if (maxDurability != 1f) m.put("maxDur", maxDurability);
        if (rounds != 0) m.put("rounds", rounds);
        if (ammoId != null) m.put("ammo", ammoId);
        if (fireMode != 0) m.put("mode", fireMode);
        if (jammed) m.put("jammed", true);
        if (chambered) m.put("chambered", true);
        if (origin != null && !origin.isEmpty()) m.put("origin", origin);
        if (uses >= 0) m.put("uses", uses);
        if (ownerFaction != null) m.put("owner", ownerFaction);
        if (fallback) m.put("fallback", true);
        if (insured) m.put("insured", true);
        if (camo != null) m.put("camo", camo);
        if (rotated) m.put("rot", true);
        m.put("x", x);
        m.put("y", y);
        if (mag != null) m.put("mag", mag.toJson());
        if (!attachments.isEmpty()) {
            Map<String, Object> at = new LinkedHashMap<>();
            for (Map.Entry<String, ItemInstance> e : attachments.entrySet()) at.put(e.getKey(), e.getValue().toJson());
            m.put("att", at);
        }
        if (grid != null && !grid.items.isEmpty()) {
            List<Object> l = new ArrayList<>();
            for (ItemInstance i : grid.items) l.add(i.toJson());
            m.put("grid", l);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    public static ItemInstance fromJson(Map<String, Object> m) {
        String def = Json.str(m, "def", null);
        if (def == null || Content.def(def) == null) return null;
        // v0.3 migration: loose magazines become the rounds they held; physical gun parts and finishes are retired
        boolean oldMag = def.startsWith("mag."), oldPart = (def.startsWith("att.") || def.startsWith("finish.")) && def.indexOf('.', 4) < 0;
        if (oldPart || oldMag && Json.integer(m, "rounds", 0) <= 0) return null;
        long uid = Json.lng(m, "uid", -1);
        ItemInstance i = uid < 0 ? new ItemInstance(def) : new ItemInstance(uid, def);
        i.qty = oldMag ? Json.integer(m, "rounds", 0) : Json.integer(m, "qty", 1);
        if (oldMag) { i.x = Json.integer(m, "x", 0); i.y = Json.integer(m, "y", 0); i.origin = Json.str(m, "origin", ""); return i; }
        i.camo = Json.str(m, "camo", null);
        i.durability = (float) Json.num(m, "dur", i.durability);
        i.maxDurability = (float) Json.num(m, "maxDur", i.maxDurability);
        i.rounds = Json.integer(m, "rounds", 0);
        i.ammoId = Content.canon(Json.str(m, "ammo", null));
        i.fireMode = Json.integer(m, "mode", 0);
        i.jammed = Json.bool(m, "jammed", false);
        i.chambered = Json.bool(m, "chambered", false);
        i.origin = Json.str(m, "origin", "");
        i.uses = Json.integer(m, "uses", i.uses);
        i.ownerFaction = Json.str(m, "owner", null);
        i.fallback = Json.bool(m, "fallback", false);
        i.insured = Json.bool(m, "insured", false);
        i.rotated = Json.bool(m, "rot", false);
        // v3 re-oriented some definitions so that 0 degrees is upright; keep the saved footprint identical
        if (legacyOrientation && i.def() != null && i.def().reoriented) i.rotated = !i.rotated;
        i.x = Json.integer(m, "x", 0);
        i.y = Json.integer(m, "y", 0);
        if (i.def().weapon != null && !i.def().weapon.melee()) {
            // v0.3 migration: a fitted magazine and a chambered round fold into the weapon's own rounds
            if (m.get("mag") instanceof Map) i.rounds += Json.integer((Map<String, Object>) m.get("mag"), "rounds", 0);
            if (i.chambered) { i.rounds++; i.chambered = false; }
            if (i.rounds > 0) i.ammoId = Content.ammoFor(i.def().weapon.caliber).id;
        }
        for (Map.Entry<String, Object> e : Json.obj(m, "att").entrySet()) {
            ItemInstance a = fromJson((Map<String, Object>) e.getValue());
            if (a != null && a.def().attachment != null && i.attachments.size() < absentialis.gameplay.weapons.Attachments.MAX_EQUIPPED) { i.attachments.put(a.def().attachment.socket, a); a.parent = i; }
        }
        if (i.def().weapon != null && !i.def().weapon.melee()) i.rounds = Math.min(i.rounds, i.capacity());
        if (i.grid != null) for (Object o : Json.list(m, "grid")) {
            ItemInstance c = fromJson((Map<String, Object>) o);
            if (c != null) i.grid.restore(c);
        }
        return i;
    }

    @Override public String toString() { return name() + (qty > 1 ? " x" + qty : "") + "#" + uid; }
}
