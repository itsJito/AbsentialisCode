package absentialis.gameplay.weapons;

import absentialis.gameplay.items.ItemDef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The attachment catalogue (v0.3 weapon overhaul). Every weapon keeps its base voxel model; up to
 * {@link #MAX_EQUIPPED} attachments from its compatible categories bolt onto it and move its
 * statistics. Almost every part buys one thing with another - the modifier table below is the whole
 * design: positive numbers are improvements (shown green with a +), negative numbers are penalties
 * (red with a -), always in percent of the weapon's own value. Perks have no penalty but still
 * occupy one of the five slots.
 *
 * Attachments are unlocked per weapon by levelling that weapon, and per category by player Rank;
 * they are fitted at the Gun Workbench, never found as loot.
 */
public final class Attachments {
    private Attachments() {}

    public static final int MAX_EQUIPPED = 11, MAX_BUILDS = 5;      // 1.1: every slot that is open may be used (it was five at once)

    /** Category -> display name, in workbench order. */
    public static final String[] CATEGORIES = {"light", "muzzle", "barrel", "laser", "optic", "stock", "underbarrel", "magazine", "reargrip", "trigger", "perk"};

    public static String categoryName(String c) {
        return switch (c) { case "underbarrel" -> "Underbarrel"; case "reargrip" -> "Rear Grip"; case "trigger" -> "Trigger Action"; default -> Character.toUpperCase(c.charAt(0)) + c.substring(1); };
    }

    /** Player Rank at which a whole category opens at the workbench. */
    public static int categoryRank(String c) {
        return switch (c) { case "light" -> 1; case "muzzle" -> 1; case "optic" -> 2; case "magazine" -> 4; case "underbarrel", "trigger" -> 6; case "stock" -> 8; case "barrel" -> 10; case "laser" -> 12; case "reargrip" -> 15; default -> 20; };
    }

    /** Statistic keys in display order with their workbench labels. Positive is always better. */
    public static final String[][] STATS = {
            {"damage", "Damage"}, {"range", "Range"}, {"accuracy", "Accuracy"}, {"recoil", "Recoil Control"}, {"mobility", "Mobility"},
            {"ads", "ADS Speed"}, {"stf", "Sprint-to-Fire"}, {"steady", "Aim Walking Steadiness"}, {"hip", "Hipfire Accuracy"}, {"velocity", "Bullet Velocity"},
            {"reload", "Reload Speed"}, {"sound", "Sound Suppression"}, {"capacity", "Magazine Capacity"}, {"firerate", "Fire Rate"}, {"pen", "Penetration"}};

    public static String statName(String key) { for (String[] s : STATS) if (s[0].equals(key)) return s[1]; return key; }

    /** Categories a weapon family accepts. Handguns and magnums take a Trigger Action instead of Stock / Underbarrel. */
    public static List<String> categoriesFor(ItemDef w) {
        if (w.weapon == null || w.weapon.melee()) return List.of();
        return switch (w.weapon.family) {
            case "handgun" -> List.of("light", "muzzle", "barrel", "laser", "optic", "magazine", "reargrip", "trigger", "perk");
            case "magnum" -> "single".equals(w.weapon.reloadStyle) ? List.of("light", "muzzle", "barrel", "laser", "optic", "reargrip", "trigger", "perk") : List.of("light", "muzzle", "barrel", "laser", "optic", "magazine", "reargrip", "trigger", "perk");
            case "shotgun" -> List.of("light", "muzzle", "barrel", "laser", "optic", "stock", "underbarrel", "magazine", "perk");
            case "rifle" -> List.of("light", "muzzle", "barrel", "laser", "optic", "stock", "underbarrel", "magazine", "reargrip", "perk");
            case "auto" -> List.of("light", "muzzle", "barrel", "laser", "optic", "stock", "underbarrel", "magazine", "reargrip", "perk");
            default -> List.of("light", "laser", "optic", "stock", "perk");   // crossbow
        };
    }

    private static final List<ItemDef> ALL = new ArrayList<>();
    private static final Map<String, List<String>> FAMILIES = new LinkedHashMap<>();
    private static final String EVERY = "handgun,magnum,shotgun,rifle,auto,special";

    /** The three colours a laser comes in, by attachment id: {name, ARGB of the beam}. Red is the default. */
    public static final String LASER_DEFAULT = "att.laser.1mw";
    public static int laserColor(String attachmentId) {
        if (attachmentId == null) return 0;
        return attachmentId.endsWith(".5mw") ? 0xFF38F060 : attachmentId.endsWith(".tac") ? 0xFF3C7CFF : attachmentId.endsWith(".1mw") ? 0xFFFF2A20 : 0;
    }
    public static String laserColorName(String attachmentId) { return attachmentId == null ? "" : attachmentId.endsWith(".5mw") ? "Green" : attachmentId.endsWith(".tac") ? "Blue" : attachmentId.endsWith(".1mw") ? "Red" : ""; }

    private static void att(String cat, String key, String name, String families, String desc, Object... mods) {
        ItemDef d = new ItemDef();
        d.id = "att." + cat + "." + key;
        d.name = name;
        d.category = "attachment";
        d.desc = desc;
        d.icon = "att_" + cat;
        d.w = 1; d.h = 1; d.value = 0; d.mass = 0.1f;
        ItemDef.Attachment a = new ItemDef.Attachment();
        a.socket = cat;
        for (int i = 0; i < mods.length; i += 2) {
            String k = (String) mods[i];
            float v = ((Number) mods[i + 1]).floatValue();
            switch (k) {
                case "zoom" -> a.zoom = v;
                case "expose" -> a.expose = (int) v;
                case "light" -> a.light = true;
                case "launcher" -> a.launcher = true;
                default -> a.mods.put(k, v);
            }
        }
        d.attachment = a;
        ALL.add(d);
        FAMILIES.put(d.id, List.of(families.split(",")));
    }

    static {
        // ---- Light (0.6): a flashlight on the gun. Its own socket, on every firearm and the crossbow, from the first day.
        att("light", "torch", "Mounted Flashlight", EVERY, "A flashlight clamped to the fore-end: it lights what the weapon points at, and it is on the weapon, so your hands are free. Whatever you can see can see you.", "light", 1, "expose", 1, "ads", -1);
        // ---- Muzzle: sound, recoil, range
        att("muzzle", "mono_supp", "Monolithic Suppressor", "handgun,rifle,auto", "Integral baffle stack. Hides the shot and carries damage further, but it is long and front-heavy.", "sound", 60, "range", 8, "ads", -6, "steady", -8);
        att("muzzle", "light_supp", "Lightweight Suppressor", "handgun,rifle,auto,special", "Short can. Less suppression, almost no handling cost, a little velocity lost.", "sound", 45, "ads", -2, "range", -5, "velocity", -4);
        att("muzzle", "compensator", "Compensator", "handgun,magnum,shotgun,rifle,auto", "Vents gas upward to hold the muzzle down.", "recoil", 12, "ads", -3, "steady", -4);
        att("muzzle", "brake", "Muzzle Brake", "magnum,shotgun,rifle,auto", "Side ports tame the push and steady the hip - and throw the blast at everyone nearby.", "recoil", 9, "hip", 4, "sound", -12);
        att("muzzle", "choke", "Full Choke", "shotgun", "Tightens the pattern for reach; unforgiving from the hip.", "accuracy", 22, "range", 8, "hip", -10);
        // ---- Barrel: range, velocity, recoil, handling
        att("barrel", "long", "Long Barrel", "handgun,magnum,shotgun,rifle,auto", "More barrel, more reach and velocity; slower to bring up and to carry.", "range", 18, "velocity", 15, "recoil", 4, "ads", -8, "mobility", -4);
        att("barrel", "short", "Short Barrel", "handgun,magnum,shotgun,rifle,auto", "Handy indoors. Gives up range, velocity and some control.", "ads", 9, "mobility", 4, "hip", 6, "range", -15, "velocity", -10, "recoil", -5);
        att("barrel", "heavy", "Heavy Barrel", "magnum,shotgun,rifle,auto", "Thick profile soaks up recoil and tightens groups; you feel every gram of it.", "recoil", 10, "accuracy", 8, "mobility", -6, "ads", -6);
        // ---- Laser: hipfire, ADS, visibility
        // 0.8: a laser is one thing in three colours - red (the default), green and blue. It is a module on the weapon,
        // it is dark until the weapon is aimed, and when it is on its dot is the reticle: where it falls is where the
        // round goes. The dot is real light and anyone can see it. (The ids are the three 0.7 lasers', so saved builds
        // keep the part they had.)
        att("laser", "1mw", "Red Laser", EVERY, "The standard aiming laser. Dark until you aim; then its red dot is your reticle, and it lights what it touches.", "ads", 6, "steady", 8, "accuracy", 4, "expose", 2);
        att("laser", "5mw", "Green Laser", EVERY, "A green dot: the easiest to pick out in daylight and at range, for you and for anyone looking your way.", "ads", 4, "steady", 8, "accuracy", 6, "expose", 2);
        att("laser", "tac", "Blue Laser", EVERY, "A blue dot: quick to find against rust, brick and blood.", "ads", 8, "steady", 10, "accuracy", 4, "expose", 2);
        att("laser", "light", "Weapon Light", EVERY, "Lights what you point at. Whatever you can see can see you.", "hip", 2, "ads", -2, "light", 1, "expose", 1);
        // ---- Optic: magnification, sight picture
        att("optic", "reddot", "Red Dot Sight", EVERY, "Clean dot, both eyes open.", "zoom", 1.25f, "accuracy", 4, "ads", -3);
        att("optic", "holo", "Holographic Sight", EVERY, "Wide window with a ranging ring.", "zoom", 1.4f, "accuracy", 6, "ads", -5);
        att("optic", "scope3", "3.0x Scope", "magnum,shotgun,rifle,auto,special", "Mid-range glass.", "zoom", 3f, "accuracy", 8, "ads", -10, "steady", -5);
        att("optic", "scope6", "6.0x Sniper Scope", "rifle,special", "Long glass for long shots. Useless in a stairwell.", "zoom", 6f, "accuracy", 12, "range", 6, "ads", -16, "hip", -12);
        // ---- Stock: recoil control, aim walking, ADS, movement
        att("stock", "heavy", "Heavy Stock", "shotgun,rifle,auto,special", "Solid shoulder weld. Stable and slow.", "recoil", 12, "steady", 10, "ads", -7, "mobility", -4);
        att("stock", "none", "No Stock", "shotgun,rifle,auto", "Nothing to shoulder: the fastest handling there is, and the wildest recoil.", "ads", 12, "mobility", 6, "stf", 10, "recoil", -14, "steady", -12);
        att("stock", "skeleton", "Skeleton Stock", "rifle,auto,special", "Cut-down frame. Lighter, a little less planted.", "ads", 6, "mobility", 3, "recoil", -6);
        // ---- Underbarrel: recoil, stability, handling
        att("underbarrel", "foregrip", "Vertical Foregrip", "shotgun,rifle,auto", "A fist under the barrel holds it down.", "recoil", 10, "steady", 4, "ads", -4);
        att("underbarrel", "angled", "Angled Grip", "shotgun,rifle,auto", "Drives the weapon onto target quickly; no help from the hip.", "ads", 5, "stf", 6, "hip", -6);
        att("underbarrel", "bipod", "Bipod", "rifle,auto", "Legs for sustained fire. Awkward on the move.", "recoil", 15, "accuracy", 6, "ads", -9, "mobility", -4);
        att("underbarrel", "launcher", "Grenade Launcher", "rifle,auto", "Throws your grenades twice as far and bursts them on impact. A lot of steel under the barrel.", "ads", -10, "mobility", -5, "launcher", 1);
        // ---- Magazine: capacity, reload speed
        att("magazine", "ext", "Extended Magazine", "handgun,magnum,shotgun,rifle,auto", "More rounds before the reload; more weight in the hand.", "capacity", 40, "ads", -4, "reload", -8, "mobility", -2);
        att("magazine", "fast", "Fast Mag", "handgun,magnum,rifle,auto", "Coupled, flared and quick - at the cost of a few rounds.", "reload", 30, "capacity", -10, "ads", -2);
        att("magazine", "drum", "Drum Magazine", "shotgun,rifle,auto", "Double the ammunition. Slow to change, slow to aim, heavy to run with.", "capacity", 100, "ads", -10, "reload", -18, "mobility", -7);
        // ---- Rear grip: recoil, ADS, sprint-to-fire
        att("reargrip", "rubber", "Rubberized Grip", "handgun,magnum,rifle,auto", "Tacky rubber for control; it drags on the draw.", "recoil", 7, "ads", -3, "stf", -3);
        att("reargrip", "stippled", "Stippled Grip Tape", "handgun,magnum,rifle,auto", "Fast hands, loose sights.", "stf", 8, "ads", 5, "steady", -7);
        att("reargrip", "granulated", "Granulated Grip", "handgun,magnum,rifle,auto", "Coarse grit steadies the aim; slower out of a sprint.", "steady", 9, "accuracy", 3, "stf", -5);
        // ---- Trigger action (handguns and magnums)
        att("trigger", "light", "Lightweight Trigger", "handgun,magnum", "Short, light pull: faster follow-ups, easier to yank.", "firerate", 10, "accuracy", -6);
        att("trigger", "heavy", "Heavy Duty Trigger", "handgun,magnum", "Deliberate break for precise shots.", "accuracy", 7, "recoil", 4, "firerate", -8);
        att("trigger", "match", "Match Grade Trigger", "handgun,magnum", "Ready the instant you stop running.", "stf", 10, "ads", 4, "recoil", -4);
        // ---- Perks: no statistical penalty, but each takes one of the five slots
        att("perk", "fmj", "FMJ", EVERY, "Full metal jacket: rounds carry through armour and thin cover.", "pen", 40);
        att("perk", "sleight", "Sleight of Hand", EVERY, "Practised hands: much faster reloads.", "reload", 35);
        att("perk", "loaded", "Fully Loaded", "handgun,magnum,shotgun,rifle,auto", "The weapon holds a quarter more rounds.", "capacity", 25);
        att("perk", "mind", "Presence of Mind", EVERY, "Calm breathing: the sights barely move when you walk.", "steady", 14);
    }

    public static List<ItemDef> all() { return ALL; }

    public static boolean fits(ItemDef weapon, ItemDef part) {
        if (weapon.weapon == null || part.attachment == null) return false;
        if (!categoriesFor(weapon).contains(part.attachment.socket)) return false;
        List<String> f = FAMILIES.get(part.id);
        if (f == null || !f.contains(weapon.weapon.family)) return false;
        // loose-loading weapons have no magazine to swap for a drum or fast mag
        if (part.id.equals("att.magazine.fast") && "single".equals(weapon.weapon.reloadStyle)) return false;
        return true;
    }

    /** Every attachment this weapon can take, in unlock order (cheap parts first, perks last within the weave). */
    public static List<ItemDef> compatible(ItemDef weapon) {
        List<List<ItemDef>> byCat = new ArrayList<>();
        for (String c : categoriesFor(weapon)) {
            List<ItemDef> l = new ArrayList<>();
            for (ItemDef d : ALL) if (d.attachment.socket.equals(c) && fits(weapon, d)) l.add(d);
            if (!l.isEmpty()) byCat.add(l);
        }
        // weave the categories so every few levels opens something for a different part of the gun
        List<ItemDef> out = new ArrayList<>();
        for (int i = 0; ; i++) {
            boolean any = false;
            for (List<ItemDef> l : byCat) if (i < l.size()) { out.add(l.get(i)); any = true; }
            if (!any) break;
        }
        return out;
    }

    /**
     * 1.1: what a weapon level is worth. Every level up to the last opens one more attachment SLOT on that weapon (not
     * one more part: a slot, and anything that fits the slot may go in it) and gives one upgrade to spend on the weapon
     * itself (gameplay.weapons.Gunsmith: Firepower, Rate of Fire, Reload Speed, Capacity - a knife has Damage and
     * Durability instead). There are four steps to each of a weapon's tracks, so sixteen levels after the first for a
     * firearm and eight for a knife.
     */
    public static final int UPGRADE_STEPS = 4;
    /** The order slots open in: the light needs nothing; then what a new owner wants first. */
    public static final String[] SLOT_ORDER = {"light", "laser", "optic", "muzzle", "magazine", "barrel", "stock", "underbarrel", "reargrip", "trigger", "perk"};

    public static List<String> slotOrder(ItemDef w) {
        List<String> has = categoriesFor(w), out = new ArrayList<>();
        for (String c : SLOT_ORDER) if (has.contains(c)) out.add(c);
        return out;
    }

    /** The weapon level at which a slot opens (1 = from the start), or 0 if the weapon has no such slot. */
    public static int slotLevel(ItemDef w, String category) { int i = slotOrder(w).indexOf(category); return i < 0 ? 0 : i + 1; }

    /** How many of a weapon's slots are open at a level. */
    public static int slotsOpen(ItemDef w, int level) { return Math.min(slotOrder(w).size(), Math.max(0, level)); }

    /** Weapon level that opens the slot a part goes in, or 0 if it does not fit. */
    public static int unlockLevel(ItemDef weapon, ItemDef part) {
        if (part.attachment == null || !fits(weapon, part)) return 0;
        return slotLevel(weapon, part.attachment.socket);
    }

    /** The last level of a weapon - where everything about it has been opened and it may be prestiged. */
    public static int maxLevel(ItemDef weapon) {
        if (weapon.weapon == null) return 1;
        return 1 + UPGRADE_STEPS * (weapon.weapon.melee() ? 2 : 4);
    }

    // ------------------------------------------------------------------ weapon levels
    /** Cumulative weapon XP needed to reach a level (level 1 = 0). */
    public static long xpForLevel(int level) { long l = Math.max(0, level - 1); return 600L * l + 90L * l * l; }

    public static int levelFor(long xp, int max) { int l = 1; while (l < max && xp >= xpForLevel(l + 1)) l++; return l; }
}
