package absentialis.gameplay.items;

import absentialis.engine.util.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared read-only item definition (GDD 8.3). Static characteristics only; all mutable state lives
 * on {@link ItemInstance}. Platform handling lives on {@link Weapon}; projectile damage and
 * penetration live on {@link Ammo}.
 */
public final class ItemDef {
    public String id, name, category, desc = "";
    public int w = 1, h = 1, maxStack = 1, value = 10;
    /** Loot tier 0..5: Common, Uncommon, Rare, Epic, Legendary, Mythic. */
    public int tier;
    /** Player Rank at which the shop stocks this item (weapons). */
    public int rank = 1;
    public float mass = 0.1f;
    public List<String> actions = new ArrayList<>();
    public String slot;              // equipment slot it can occupy (primary, sidearm, melee, head, face, armor, backpack)
    public String icon;              // icon painter key
    public boolean questItem, protectedOk;
    public Weapon weapon;
    public Ammo ammo;
    public Magazine magazine;
    public Medical medical;
    public Food food;
    public Armor armor;
    public Backpack backpack;
    /** v3: this definition's w/h were swapped so that 0 degrees shows the model upright (save migration flag). */
    public boolean reoriented;
    public Attachment attachment;
    public Throwable throwable;
    public Charm charm;
    public String keyFor, docId, material; // misc tags

    public static final class Weapon {
        public String family, caliber, magType, sound = "shot_rifle", model = "rifle";
        /** "mag": one reload fills the weapon; "single": rounds go in one at a time. */
        public String reloadStyle = "mag";
        public int capacity;                 // internal (loose-loaded) capacity, 0 = detachable magazine
        public List<String> modes = new ArrayList<>();
        public float rpm = 600, dispersion = 1f, recoilV = 2, recoilH = 0.6f, ergo = 60, range = 80, wear = 0.001f, noise = 110, reloadTime = 2.2f, damageMul = 1;
        public float meleeDamage, meleeRange;
        public List<String> sockets = new ArrayList<>();
        public boolean melee() { return "melee".equals(family); }
        public boolean internal() { return capacity > 0; }
    }

    public static final class Ammo {
        public String caliber, category;
        public float damage = 40, pen = 20, armorDamage = 0.5f, spread = 0;
        public int pellets = 1;
        public boolean tracer;
    }

    public static final class Magazine {
        public String magType, caliber;
        public int capacity = 30;
        public float ergo;
    }

    public static final class Medical {
        public float heal, pain, rad, useTime = 3;
        /** Herbs heal a fraction of maximum health rather than a flat amount. */
        public float healFrac;
        public boolean stopLight, stopHeavy, fracture, infection;
        /** Clears the infection outright (Antiviral Medicine). "infection" alone only holds it for a while (antibiotics). */
        public boolean antiviral;
        /** Raises the user's maximum health permanently (Yellow Herb). */
        public boolean maxHealth;
        public int uses = 1;
    }

    public static final class Food {
        public float energy, hydration, useTime = 3, infection, fatigue;
    }

    public static final class Armor {
        public int armorClass = 2;
        public List<String> regions = new ArrayList<>();
        public float durability = 50, speedPenalty, ergoPenalty;
        /** Body Armor: protection points that sit on top of health and absorb damage first. */
        public float shield;
        public boolean gasMask;
    }

    public static final class Backpack { public int gw = 6, gh = 5; }

    public static final class Attachment {
        public String socket;
        public List<String> calibers = new ArrayList<>();
        public List<String> families = new ArrayList<>();
        public int tint;                 // finish colour (0 = none)
        public float wear;               // wear multiplier delta (e.g. chrome-lined barrel -0.2)
        public float noise, ergo, recoil, dispersion, zoom = 1;
        public int length;
        public boolean light, launcher;
        /** Visible laser / light: 1 = whenever the weapon is drawn, 2 = only while aiming. */
        public int expose;
        /** Statistic modifiers in percent, positive = better (see weapons.Attachments.STATS). */
        public final java.util.Map<String, Float> mods = new java.util.LinkedHashMap<>();
    }

    /** Passive buff in one of the three 1x1 charm slots (addendum P2.3). */
    public static final class Charm { public String effect; public float amount; }

    public static final class Throwable {
        public String kind = "frag";
        public float fuse = 3.5f, radius = 7, damage = 160, noise = 150;
    }

    public static final String[] TIER_NAMES = {"Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic"};
    public static final int[] TIER_COLORS = {0xFFE8E4D8, 0xFF6CC24A, 0xFF4A90E2, 0xFFA45BD8, 0xFFF08A24, 0xFFFFE23A};
    /** Share of loot rolls that land in each tier: 50 / 20 / 15 / 10 / 4 / 1 percent. */
    public static final float[] TIER_RATES = {0.50f, 0.20f, 0.15f, 0.10f, 0.04f, 0.01f};
    public String tierName() { return TIER_NAMES[Math.max(0, Math.min(5, tier))]; }
    public int tierColor() { return TIER_COLORS[Math.max(0, Math.min(5, tier))]; }

    public boolean is(String cat) { return cat.equals(category); }
    public boolean stackable() { return maxStack > 1; }

    @SuppressWarnings("unchecked")
    public static ItemDef parse(Map<String, Object> m) {
        ItemDef d = new ItemDef();
        d.id = Json.str(m, "id", null);
        d.name = Json.str(m, "name", d.id);
        d.category = Json.str(m, "cat", "misc");
        d.desc = Json.str(m, "desc", "");
        d.w = Json.integer(m, "w", 1);
        d.h = Json.integer(m, "h", 1);
        d.reoriented = Json.bool(m, "reoriented", false);
        d.maxStack = Json.integer(m, "stack", 1);
        d.value = Json.integer(m, "value", 10);
        d.tier = Json.integer(m, "tier", 0);
        d.rank = Json.integer(m, "rank", 1);
        d.mass = (float) Json.num(m, "mass", 0.1);
        d.actions = Json.strList(m, "actions");
        d.slot = Json.str(m, "slot", null);
        d.icon = Json.str(m, "icon", d.category);
        d.questItem = Json.bool(m, "quest", false);
        d.protectedOk = Json.bool(m, "protected", false);
        d.keyFor = Json.str(m, "key", null);
        d.docId = Json.str(m, "doc", null);
        d.material = Json.str(m, "material", null);
        if (m.containsKey("weapon")) {
            Map<String, Object> o = Json.obj(m, "weapon");
            Weapon w = new Weapon();
            w.family = Json.str(o, "family", "rifle");
            w.caliber = Json.str(o, "caliber", null);
            w.magType = Json.str(o, "mag", null);
            w.capacity = Json.integer(o, "capacity", 0);
            w.modes = Json.strList(o, "modes");
            if (w.modes.isEmpty()) w.modes.add("semi");
            w.rpm = (float) Json.num(o, "rpm", 600);
            w.dispersion = (float) Json.num(o, "disp", 1);
            w.recoilV = (float) Json.num(o, "recoilV", 2);
            w.recoilH = (float) Json.num(o, "recoilH", 0.6);
            w.ergo = (float) Json.num(o, "ergo", 60);
            w.range = (float) Json.num(o, "range", 80);
            w.wear = (float) Json.num(o, "wear", 0.001);
            w.noise = (float) Json.num(o, "noise", 110);
            w.reloadTime = (float) Json.num(o, "reload", 2.2);
            w.damageMul = (float) Json.num(o, "dmgMul", 1);
            w.sound = Json.str(o, "sound", "shot_rifle");
            w.reloadStyle = Json.str(o, "reloadStyle", "mag");
            w.model = Json.str(o, "model", "rifle");
            w.meleeDamage = (float) Json.num(o, "meleeDamage", 0);
            w.meleeRange = (float) Json.num(o, "meleeRange", 0);
            w.sockets = Json.strList(o, "sockets");
            d.weapon = w;
        }
        if (m.containsKey("ammo")) {
            Map<String, Object> o = Json.obj(m, "ammo");
            Ammo a = new Ammo();
            a.caliber = Json.str(o, "caliber", null);
            a.category = Json.str(o, "category", "rifle");
            a.damage = (float) Json.num(o, "damage", 40);
            a.pen = (float) Json.num(o, "pen", 20);
            a.armorDamage = (float) Json.num(o, "armorDmg", 0.5);
            a.pellets = Json.integer(o, "pellets", 1);
            a.spread = (float) Json.num(o, "spread", 0);
            a.tracer = Json.bool(o, "tracer", false);
            d.ammo = a;
        }
        if (m.containsKey("magazine")) {
            Map<String, Object> o = Json.obj(m, "magazine");
            Magazine g = new Magazine();
            g.magType = Json.str(o, "magType", null);
            g.caliber = Json.str(o, "caliber", null);
            g.capacity = Json.integer(o, "capacity", 30);
            g.ergo = (float) Json.num(o, "ergo", 0);
            d.magazine = g;
        }
        if (m.containsKey("medical")) {
            Map<String, Object> o = Json.obj(m, "medical");
            Medical md = new Medical();
            md.heal = (float) Json.num(o, "heal", 0);
            md.maxHealth = Json.bool(o, "maxHealth", false);
            md.healFrac = (float) Json.num(o, "healFrac", 0);
            md.pain = (float) Json.num(o, "pain", 0);
            md.rad = (float) Json.num(o, "rad", 0);
            md.useTime = (float) Json.num(o, "useTime", 3);
            md.stopLight = Json.bool(o, "stopLight", false);
            md.stopHeavy = Json.bool(o, "stopHeavy", false);
            md.fracture = Json.bool(o, "fracture", false);
            md.infection = Json.bool(o, "infection", false);
            md.antiviral = Json.bool(o, "antiviral", false);
            md.uses = Json.integer(o, "uses", 1);
            d.medical = md;
        }
        if (m.containsKey("food")) {
            Map<String, Object> o = Json.obj(m, "food");
            Food f = new Food();
            f.energy = (float) Json.num(o, "energy", 0);
            f.hydration = (float) Json.num(o, "hydration", 0);
            f.useTime = (float) Json.num(o, "useTime", 3);
            f.infection = (float) Json.num(o, "infection", 0);
            f.fatigue = (float) Json.num(o, "fatigue", 0);
            d.food = f;
        }
        if (m.containsKey("armor")) {
            Map<String, Object> o = Json.obj(m, "armor");
            Armor a = new Armor();
            a.armorClass = Json.integer(o, "class", 2);
            a.regions = Json.strList(o, "regions");
            a.durability = (float) Json.num(o, "durability", 50);
            a.speedPenalty = (float) Json.num(o, "speed", 0);
            a.ergoPenalty = (float) Json.num(o, "ergo", 0);
            a.gasMask = Json.bool(o, "gasMask", false);
            a.shield = (float) Json.num(o, "shield", 0);
            d.armor = a;
        }
        if (m.containsKey("backpack")) {
            Map<String, Object> o = Json.obj(m, "backpack");
            Backpack b = new Backpack();
            b.gw = Json.integer(o, "gw", 6);
            b.gh = Json.integer(o, "gh", 5);
            d.backpack = b;
        }
        if (m.containsKey("attachment")) {
            Map<String, Object> o = Json.obj(m, "attachment");
            Attachment a = new Attachment();
            a.socket = Json.str(o, "socket", null);
            a.calibers = Json.strList(o, "calibers");
            a.noise = (float) Json.num(o, "noise", 0);
            a.ergo = (float) Json.num(o, "ergo", 0);
            a.recoil = (float) Json.num(o, "recoil", 0);
            a.dispersion = (float) Json.num(o, "disp", 0);
            a.zoom = (float) Json.num(o, "zoom", 1);
            a.length = Json.integer(o, "length", 0);
            a.light = Json.bool(o, "light", false);
            a.families = Json.strList(o, "families");
            String tint = Json.str(o, "tint", null);
            if (tint != null) a.tint = 0xFF000000 | Integer.parseInt(tint.replace("#", ""), 16);
            a.wear = (float) Json.num(o, "wear", 0);
            d.attachment = a;
        }
        if (m.containsKey("throwable")) {
            Map<String, Object> o = Json.obj(m, "throwable");
            Throwable t = new Throwable();
            t.kind = Json.str(o, "kind", "frag");
            t.fuse = (float) Json.num(o, "fuse", 3.5);
            t.radius = (float) Json.num(o, "radius", 7);
            t.damage = (float) Json.num(o, "damage", 160);
            t.noise = (float) Json.num(o, "noise", 150);
            d.throwable = t;
        }
        if (m.containsKey("charm")) {
            Map<String, Object> o = Json.obj(m, "charm");
            Charm c = new Charm();
            c.effect = Json.str(o, "effect", "wear");
            c.amount = (float) Json.num(o, "amount", 0);
            d.charm = c;
        }
        return d;
    }
}
