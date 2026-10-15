package absentialis.gameplay.weapons;

import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Weapon statistics (v0.3). One consistent pipeline turns a weapon definition, its fitted
 * attachments (at most five), its condition and its Weapon Prestige bonus into the numbers every
 * system reads: ballistics, recoil, handling, movement, noise, capacity. Attachment modifiers are
 * percentages that add up predictably, so the workbench can show the exact result before anything
 * is installed. Player Rank never feeds into these numbers.
 */
public final class Gunsmith {
    private Gunsmith() {}

    /** Weapon Prestige level per weapon definition (set when a profile loads; 0 without one). */
    public static java.util.function.ToIntFunction<String> prestigeLevel = id -> 0;

    public static List<String> slotsFor(ItemDef d) { return Attachments.categoriesFor(d); }

    /** Null when the part can be fitted, otherwise a player-readable reason. */
    public static String incompatibility(ItemInstance weapon, ItemInstance part) { return incompatibility(weapon, part.def()); }

    public static String incompatibility(ItemInstance weapon, ItemDef p) {
        ItemDef w = weapon.def();
        if (w.weapon == null || w.weapon.melee()) return "Melee weapons take no attachments";
        if (p.attachment == null) return p.name + " is not an attachment";
        if (!Attachments.fits(w, p)) return p.name + " does not fit the " + w.name;
        boolean replacing = weapon.attachments.containsKey(p.attachment.socket);
        if (!replacing && weapon.attachments.size() >= Attachments.MAX_EQUIPPED) return "All " + Attachments.MAX_EQUIPPED + " attachment slots are in use";
        return null;
    }

    /** Final handling numbers for one weapon instance. */
    public static final class Stats {
        public float dispersion, recoilV, recoilH, ergo, noise, mass, zoom = 1, wearMul = 1, jamChance, rpm, range, reloadTime, adsTime;
        public float damageMul = 1, penMul = 1, velocityMul = 1, mobility = 1, sprintToFire = 0.2f, steady = 1, hipMul = 1;
        public int capacity, expose;          // expose: 1 = laser / light visible whenever drawn, 2 = only while aiming
        public boolean light, suppressed, launcher;
        public int conditionBand; // 0 = fine, 1 = worn (<50), 2 = critical (<25)
        /** Summed attachment modifiers in percent by statistic key (see Attachments.STATS). */
        public final Map<String, Float> mods = new LinkedHashMap<>();
        public float mod(String k) { return mods.getOrDefault(k, 0f); }
    }

    // ------------------------------------------------------------------ 1.1: upgrades
    /**
     * What a weapon level buys besides a slot. Four tracks on a firearm, four steps on each:
     *
     *   Firepower ....... damage. A weapon nobody has worked on does about half of what it is capable of (six tenths for
     *                     a handgun or an automatic, which have less to spare); the last step is the factory figure
     *                     and a hair over (101.5%).
     *   Rate of Fire .... how soon the next shot or burst is ready: from about seven tenths of the factory rate to a hair
     *                     over it - and from further down for a pump than for an automatic.
     *   Reload Speed .... the factory reload is the middle of the track: 30% slower untouched, 22% faster at the top.
     *   Capacity ........ the factory capacity is the middle: six tenths of it untouched, half as much again at the top.
     *
     * A knife has two: Damage (at the top of it a knife in a person is the end of them) and Durability (how fast the
     * edge goes). A weapon in anybody else's hands is as it left the factory ({@link ItemInstance#factory}).
     */
    public static final String[] UPGRADES = {"Firepower", "Rate of Fire", "Reload Speed", "Capacity"}, KNIFE_UPGRADES = {"Damage", "Durability"};
    public static final int FIREPOWER = 0, RATE = 1, RELOAD = 2, CAPACITY = 3, KNIFE_DAMAGE = 0, KNIFE_DURABILITY = 1, STEPS = Attachments.UPGRADE_STEPS;
    /** A weapon as it left the factory, on each track. */
    public static final int[] FACTORY = {STEPS, STEPS, STEPS / 2, STEPS / 2};
    public interface Tuning { int level(String defId, int track); }
    /** Whose upgrades apply (the profile's, once there is one); until then every weapon is factory. */
    public static Tuning tuning;

    /** True when a weapon is somebody's who has upgrades to apply to it; false and it is exactly the factory weapon. */
    public static boolean tuned(ItemInstance it) { return tuning != null && !it.factory; }
    public static int tune(ItemInstance it, int track) { return tuned(it) ? Math.max(0, Math.min(STEPS, tuning.level(it.defId, track))) : FACTORY[Math.min(3, track)]; }
    public static String[] tracks(ItemDef d) { return d.weapon != null && d.weapon.melee() ? KNIFE_UPGRADES : UPGRADES; }

    public static float firepower(ItemDef d, int lvl) {
        String f = d.weapon == null ? "" : d.weapon.family;
        float lo = "handgun".equals(f) || "auto".equals(f) ? 0.6f : 0.5f;
        return lo + (1.015f - lo) * lvl / STEPS;
    }
    public static float rateOfFire(ItemDef d, int lvl) {
        String f = d.weapon == null ? "" : d.weapon.family;
        float lo = switch (f) { case "auto" -> 0.78f; case "shotgun" -> 0.68f; case "rifle" -> 0.72f; default -> 0.7f; };
        return lo + (1.015f - lo) * lvl / STEPS;
    }
    /** Multiplies the time a reload takes. */
    public static float reloadTime(int lvl) { float mid = STEPS / 2f; return lvl <= mid ? 1.3f - 0.3f * lvl / mid : 1f - 0.22f * (lvl - mid) / mid; }
    public static float capacityMul(int lvl) { float mid = STEPS / 2f; return lvl <= mid ? 0.6f + 0.4f * lvl / mid : 1f + 0.5f * (lvl - mid) / mid; }
    public static float knifeDamage(int lvl) { return 0.7f + 0.5f * lvl / STEPS; }
    /** At the top of its Damage track a knife kills a person outright. */
    public static boolean knifeKills(int lvl) { return lvl >= STEPS; }
    /** Multiplies the wear a blow puts on a blade. */
    public static float knifeWear(int lvl) { return 1.4f - 1.0f * lvl / STEPS; }
    /** What a step on a track changes, in words, for the panel. */
    public static String effect(ItemDef d, int track, int lvl) {
        if (d.weapon != null && d.weapon.melee()) return track == KNIFE_DAMAGE ? (knifeKills(lvl) ? "kills a person outright" : Math.round(d.weapon.meleeDamage * knifeDamage(lvl)) + " damage") : Math.round(100 / knifeWear(lvl)) + "% edge life";
        return switch (track) {
            case FIREPOWER -> Math.round(firepower(d, lvl) * 100) + "% damage";
            case RATE -> Math.round(d.weapon.rpm * rateOfFire(d, lvl)) + " rpm";
            case RELOAD -> String.format("%.2f s", d.weapon.reloadTime * reloadTime(lvl));
            default -> d.weapon.internal() ? Math.max(1, Math.round(d.weapon.capacity * capacityMul(lvl))) + " rounds" : Math.round(capacityMul(lvl) * 100) + "% magazine";
        };
    }

    /** Family bonus granted per Weapon Prestige level: {stat key, percent}. */
    public static Object[] prestigeBonus(ItemDef w) {
        return switch (w.weapon == null ? "" : w.weapon.family) {
            case "handgun" -> new Object[]{"ads", 4f};
            case "shotgun" -> new Object[]{"reload", 5f};
            case "rifle" -> new Object[]{"range", 5f};
            case "magnum" -> new Object[]{"recoil", 4f};
            case "auto" -> new Object[]{"mobility", 3f};
            default -> new Object[]{"velocity", 5f};
        };
    }

    public static Stats stats(ItemInstance it, float charmWear) {
        ItemDef def = it.def();
        ItemDef.Weapon w = def.weapon;
        Stats s = new Stats();
        for (ItemInstance a : it.attachments.values()) {
            ItemDef.Attachment at = a.def() == null ? null : a.def().attachment;
            if (at == null) continue;
            for (Map.Entry<String, Float> e : at.mods.entrySet()) s.mods.merge(e.getKey(), e.getValue(), Float::sum);
            s.zoom = Math.max(s.zoom, at.zoom);
            if (at.light) s.light = true;
            if (at.launcher) s.launcher = true;
            s.expose = s.expose == 1 || at.expose == 1 ? 1 : Math.max(s.expose, at.expose);
        }
        int pl = prestigeLevel.applyAsInt(def.id);
        if (pl > 0) { Object[] b = prestigeBonus(def); s.mods.merge((String) b[0], (Float) b[1] * pl, Float::sum); }
        s.damageMul = 1 + s.mod("damage") / 100f;
        s.penMul = 1 + s.mod("pen") / 100f;
        s.velocityMul = 1 + s.mod("velocity") / 100f;
        s.dispersion = w.dispersion * Math.max(0.2f, 1 - s.mod("accuracy") / 100f);
        s.ergo = w.ergo;
        s.wearMul = Math.max(0.2f, 1 + charmWear);
        s.mass = it.mass();
        s.noise = w.noise * Math.max(0.2f, 1 - s.mod("sound") / 100f);
        s.suppressed = s.mod("sound") >= 40;
        float rc = Math.max(0.3f, 1 - s.mod("recoil") / 100f);
        s.recoilV = w.recoilV * rc;
        s.recoilH = w.recoilH * rc;
        s.rpm = w.rpm * (1 + s.mod("firerate") / 100f);
        s.range = w.range * (1 + s.mod("range") / 100f);
        s.reloadTime = w.reloadTime * (1.25f - Math.min(100, s.ergo) / 400f) / Math.max(0.3f, 1 + s.mod("reload") / 100f);
        s.adsTime = (0.12f + def.mass * 0.035f + (100 - Math.min(100, s.ergo)) * 0.002f) / Math.max(0.3f, 1 + s.mod("ads") / 100f);
        s.mobility = (1f - Math.min(0.15f, def.mass * 0.012f)) * (1 + s.mod("mobility") / 100f);
        s.sprintToFire = (0.14f + def.mass * 0.025f) / Math.max(0.3f, 1 + s.mod("stf") / 100f);
        s.steady = Math.max(0.3f, 1 + s.mod("steady") / 100f);
        s.hipMul = 1f / Math.max(0.3f, 1 + s.mod("hip") / 100f);
        s.capacity = Math.max(1, Math.round(w.capacity * (1 + s.mod("capacity") / 100f) * (tuned(it) ? capacityMul(tune(it, CAPACITY)) : 1)));
        // 1.1: what its owner has made of it (anybody else's is the factory weapon, to the figure)
        if (tuned(it)) {
            s.damageMul *= firepower(def, tune(it, FIREPOWER));
            s.rpm *= rateOfFire(def, tune(it, RATE));
            s.reloadTime *= reloadTime(tune(it, RELOAD));
        }
        // condition (addendum): <50 red stat bars, spread +2%, 2% jam; <25 8% jam, needs bench
        float cond = it.condition();
        if (cond < 25) { s.conditionBand = 2; s.jamChance = 0.08f; s.dispersion *= 1.06f; }
        else if (cond < 50) { s.conditionBand = 1; s.jamChance = 0.02f; s.dispersion *= 1.02f; }
        else s.jamChance = 0.0005f;
        s.dispersion = Math.max(0.02f, s.dispersion);
        return s;
    }

    public static int capacity(ItemInstance it) {
        ItemDef.Weapon w = it.def().weapon;
        float pct = 0;
        for (ItemInstance a : it.attachments.values()) if (a.def() != null && a.def().attachment != null) pct += a.def().attachment.mods.getOrDefault("capacity", 0f);
        return Math.max(1, Math.round(w.capacity * (1 + pct / 100f) * (tuned(it) ? capacityMul(tune(it, CAPACITY)) : 1)));
    }

    // ------------------------------------------------------------------ the six primary statistics (0..1 for the bars)
    public static final String[] PRIMARY = {"Damage", "Range", "Accuracy", "Recoil Control", "Handling", "Mobility"};

    public static float[] primary(ItemInstance it) {
        ItemDef d = it.def();
        Stats s = stats(it, 0);
        ItemDef ammo = Content.ammoFor(d.weapon.caliber);
        float perShot = ammo == null ? 0 : ammo.ammo.damage * Math.max(1, ammo.ammo.pellets) * d.weapon.damageMul * s.damageMul;
        return new float[]{
                clamp((float) Math.sqrt(perShot / 260f)),
                clamp((float) Math.sqrt(s.range / 330f)),
                clamp(1 - s.dispersion / 1.0f),
                clamp(1 - s.recoilV / 8f),
                clamp(1 - (s.adsTime - 0.1f) / 0.42f),
                clamp((s.mobility - 0.78f) / 0.3f)};
    }

    private static float clamp(float v) { return Math.max(0.03f, Math.min(1f, v)); }

    /** Secondary statistics as {label, value text}. */
    public static List<String[]> secondary(ItemInstance it) {
        Stats s = stats(it, 0);
        ItemDef d = it.def();
        ItemDef ammo = Content.ammoFor(d.weapon.caliber);
        float speed = ammo == null ? 0 : velocity(ammo) * s.velocityMul;
        List<String[]> l = new ArrayList<>();
        l.add(new String[]{"ADS Speed", Math.round(s.adsTime * 1000) + " ms"});
        l.add(new String[]{"Sprint-to-Fire", Math.round(s.sprintToFire * 1000) + " ms"});
        l.add(new String[]{"Aim Walking Steadiness", Math.round(s.steady * 100) + "%"});
        l.add(new String[]{"Hipfire Spread", Math.round(s.hipMul * 100) + "%"});
        l.add(new String[]{"Bullet Velocity", Math.round(speed) + " m/s"});
        l.add(new String[]{"Reload Speed", String.format("%.2f s%s", s.reloadTime, "single".equals(d.weapon.reloadStyle) ? " / round" : "")});
        l.add(new String[]{"Fire Rate", Math.round(s.rpm) + " rpm"});
        l.add(new String[]{"Capacity", String.valueOf(s.capacity)});
        l.add(new String[]{"Sound Radius", Math.round(s.noise * (s.suppressed ? 0.9f : 1.6f)) + " m" + (s.suppressed ? " (suppressed)" : "")});
        return l;
    }

    /** Muzzle velocity in m/s by ammunition category. */
    public static float velocity(ItemDef ammo) {
        return switch (ammo.ammo.category) { case "pistol" -> 360; case "shell" -> 400; case "bolt" -> 110; case "precision" -> 820; case "heavy" -> 840; default -> 880; };
    }

    /** Ammo types compatible with the platform. */
    public static List<ItemDef> ammoFor(ItemDef w) {
        List<ItemDef> out = new ArrayList<>();
        if (w.weapon == null) return out;
        for (ItemDef d : Content.ITEMS.values()) if (d.ammo != null && d.ammo.caliber.equals(w.weapon.caliber)) out.add(d);
        return out;
    }

    /** Camouflage tint for the voxel model (0 = factory finish). */
    public static int finishTint(ItemInstance it) { return "gold".equals(it.camo) ? 0xFFE0B030 : 0; }

    // ------------------------------------------------------------------ builds
    /** Fits a catalogue attachment (no physical item changes hands - the workbench makes it). Returns an error or null. */
    public static String install(ItemInstance weapon, String attId) {
        ItemDef p = Content.def(attId);
        if (p == null || p.attachment == null) return "Unknown attachment";
        String why = incompatibility(weapon, p);
        if (why != null) return why;
        ItemInstance part = new ItemInstance(attId);
        part.origin = "workbench";
        part.parent = weapon;
        weapon.attachments.put(p.attachment.socket, part);
        clampRounds(weapon);
        return null;
    }

    public static void remove(ItemInstance weapon, String category) {
        ItemInstance p = weapon.attachments.remove(category);
        if (p != null) p.parent = null;
        clampRounds(weapon);
    }

    /** Attachment ids currently fitted, in category order. */
    public static List<String> build(ItemInstance weapon) {
        List<String> l = new ArrayList<>();
        for (ItemInstance a : weapon.attachments.values()) l.add(a.defId);
        return l;
    }

    /** A smaller magazine cannot hold what the larger one did: the surplus is returned by the caller. */
    public static int clampRounds(ItemInstance weapon) {
        int cap = weapon.capacity(), over = Math.max(0, weapon.rounds - cap);
        weapon.rounds -= over;
        weapon.spill += over;
        return over;
    }

    // ------------------------------------------------------------------ recoil patterns
    /**
     * Distinct, learnable per-platform recoil pattern: shot index -> {pitchUp, yaw} in degrees,
     * before per-instance scaling. Deterministic so players can learn to pull against it.
     */
    public static float[] recoilKick(ItemDef d, int shotIndex) {
        ItemDef.Weapon w = d.weapon;
        int seed = d.id.hashCode();
        float t = shotIndex;
        float up = w.recoilV, side;
        switch (w.family) {
            case "rifle", "auto" -> {
                // climbs, then a drift whose direction is a platform signature
                float dir = (seed & 1) == 0 ? 1 : -1;
                up *= t < 4 ? 1.0f : 0.65f;
                side = w.recoilH * (t < 4 ? 0.2f * (float) Math.sin(t * 1.7) : dir * (0.6f + 0.35f * (float) Math.sin(t * 0.9 + seed)));
            }
            case "shotgun", "magnum", "special" -> { side = w.recoilH * ((seed >> 3 & 1) == 0 ? 0.5f : -0.5f); }
            default -> side = w.recoilH * (float) Math.sin(t * 2.3 + seed) * 0.8f;
        }
        return new float[]{up, side};
    }

    /** Seconds for the view to settle back after the last shot. */
    public static float recoveryTime(Stats s) { return 0.18f + s.recoilV * 0.05f + (100 - Math.min(100, s.ergo)) * 0.002f; }
}
