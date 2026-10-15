package absentialis.gameplay.squad;

import absentialis.character.Appearance;
import absentialis.engine.util.Json;
import absentialis.engine.util.Rng;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.progression.Progression;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The player's AI squad (v0.3). A profile starts with four customizable squadmates and unlocks five
 * more roster slots through Rank, nine in all; up to eight deploy on an operation, split between
 * Red Team and Blue Team (four each at most). Gold Team is everyone deployed. The player is the
 * Element Leader and is never counted as a squadmate.
 *
 * Every squadmate is persistent: their look, weapon and build, Level (1-50 over 1,265,500 XP) and
 * Prestige (0-10) are their own and are independent of the player's Rank and Prestige. A
 * squadmate's Prestige keeps Level 50 and everything they have - it is purely added on top.
 */
public final class Roster {
    public static final int START = 0, UNLOCKABLE = 9, MAX_ROSTER = START + UNLOCKABLE, MAX_ACTIVE = 8, TEAM_CAP = 4, MAX_LEVEL = 50, MAX_PRESTIGE = 10;

    /**
     * 0.7: what a squadmate is. The type decides the standard kit they arrive with - weapon, sidearm, armour, helmet
     * and what is in the pack - and the weapon in turn decides how they fight (see Raid.deploySquad).
     * {id, title, primary, sidearm, armor, head, pack..., blurb}
     */
    public static final String[][] TYPES = {
            {"rifleman", "Rifleman", "weapon.akr", "weapon.pm9", "armor.vest_soft", "helmet.ssh", "The line. Automatic rifle, grenades, and the armour to stand in the open for a moment."},
            {"defender", "Defender", "weapon.riot", "weapon.pm9", "armor.body", "helmet.ssh", "Holds a doorway. Shotgun, the heaviest armour on the team, and no interest in moving."},
            {"sniper", "Sniper", "weapon.mosin", "weapon.punisher", "armor.vest_soft", "helmet.cap", "Stays back and picks. Bolt rifle, light kit, a long way from the trouble."},
            {"breacher", "Breacher", "weapon.pump12", "weapon.pm9", "armor.military", "helmet.ssh", "First through the door. Pump shotgun, flash grenades."},
            {"medic", "Medic", "weapon.smg", "weapon.pm9", "armor.vest_soft", "helmet.cap", "Keeps the others on their feet. SMG and a pack full of medicine."},
            {"support", "Support", "weapon.tommy", "weapon.pm9", "armor.military", "helmet.ssh", "Keeps heads down. Drum-fed automatic and all the ammunition he can carry."}};
    private static final String[][] PACKS = {
            {"ammo.assault:60", "grenade.frag:2", "med.bandage:2", "food.mre:1"},
            {"ammo.shells:28", "med.bandage:2", "med.spray:1", "grenade.frag:1"},
            {"ammo.rifle:30", "ammo.handgun:24", "med.bandage:1", "tool.noisemaker:1"},
            {"ammo.shells:24", "grenade.flash:2", "med.bandage:2", "melee.crowbar:1"},
            {"ammo.smg:120", "med.spray:2", "med.bandage:4", "med.antiviral:2", "med.splint:1"},
            {"ammo.smg:200", "grenade.frag:1", "med.bandage:2", "food.mre:1"}};

    public static int typeIndex(String type) { for (int i = 0; i < TYPES.length; i++) if (TYPES[i][0].equals(type)) return i; return 0; }
    public static String typeName(String type) { return TYPES[typeIndex(type)][1]; }
    public static String typeBlurb(String type) { return TYPES[typeIndex(type)][6]; }

    /** The type a weapon implies (squadmates saved before 0.7 had a weapon and no type). */
    public static String typeForWeapon(String weapon) {
        ItemDef d = Content.def(weapon);
        if (d == null || d.weapon == null) return "rifleman";
        if ("weapon.mosin".equals(d.id) || "weapon.sr16".equals(d.id) || "weapon.crossbow".equals(d.id)) return "sniper";
        if ("weapon.riot".equals(d.id) || "weapon.striker".equals(d.id)) return "defender";
        if ("shotgun".equals(d.weapon.family)) return "breacher";
        if ("weapon.tommy".equals(d.id)) return "support";
        if ("weapon.smg".equals(d.id)) return "medic";
        return "rifleman";
    }

    /** Issues the standard kit of a type: what they wear and carry from the day they are hired. */
    public static void issueKit(Mate m, String type) {
        int i = typeIndex(type);
        m.type = TYPES[i][0];
        m.weapon = TYPES[i][2];
        m.build.clear();
        m.sidearm = TYPES[i][3];
        m.armor = TYPES[i][4];
        m.head = TYPES[i][5];
        m.pack.clear();
        for (String s : PACKS[i]) m.pack.add(s);
    }
    public static final long XP_PER_CLIMB = Progression.CYCLE_XP;
    public static final String RED = "red", BLUE = "blue", GOLD = "gold";

    public static final class Mate {
        public long id;
        public String name = "Recruit";
        public Appearance look = Appearance.legacy(1, 0);
        /** Lifetime XP: the first 1,265,500 climb Level 1-50, every further 1,265,500 is one Prestige. */
        public long xp;
        public String weapon = "weapon.smg";
        public final List<String> build = new ArrayList<>();
        /** 0.7: the kind of soldier this is, and the rest of the standard kit that comes with it: sidearm, armour, helmet, and the pack as "itemId:quantity". */
        public String type = "rifleman", sidearm = "weapon.pm9", armor = "armor.vest_soft", head = "";
        public final List<String> pack = new ArrayList<>();
        public String team = RED;
        public boolean active = true;
        public int kills, raids, downs;

        public int level() {
            long x = Math.min(xp, XP_PER_CLIMB);
            int r = 1;
            long acc = 0;
            while (r < MAX_LEVEL && acc + Progression.costOf(r) <= x) { acc += Progression.costOf(r); r++; }
            return r;
        }

        public int prestige() { return (int) Math.min(MAX_PRESTIGE, Math.max(0, (xp - XP_PER_CLIMB) / XP_PER_CLIMB)); }

        /** Progress toward the next Level, or toward the next Prestige once Level 50 is held. */
        public float progress() {
            if (xp >= XP_PER_CLIMB) return prestige() >= MAX_PRESTIGE ? 1 : ((xp - XP_PER_CLIMB) % XP_PER_CLIMB) / (float) XP_PER_CLIMB;
            int l = level();
            return (xp - Progression.xpToReach(l)) / (float) Progression.costOf(l);
        }

        public String title() { return (prestige() > 0 ? "P" + prestige() + " " : "") + Progression.rankName(level()) + " " + level(); }

        /** Experience shows: steadier aim, quicker reactions, a little more staying power. Never more damage per round. */
        public float skill() { return Math.min(1f, (level() - 1) / 49f * 0.8f + prestige() * 0.02f); }
        public float maxHp() { return 100 + level() + prestige() * 3; }

        public int addXp(long amount) {
            if (amount <= 0) return 0;
            int before = level() + prestige();
            xp = Math.min(XP_PER_CLIMB * (1 + MAX_PRESTIGE), xp + amount);
            return level() + prestige() - before;
        }
    }

    public final List<Mate> mates = new ArrayList<>();
    private long nextId = 1;

    private static final String[] FIRST = {"Anton", "Mila", "Dmitri", "Oksana", "Yuri", "Katya", "Pavel", "Irina", "Sergei", "Nadia", "Viktor", "Lena", "Taras", "Zoya", "Bohdan", "Vera"};
    private static final String[] CALL = {"Kestrel", "Brick", "Wren", "Lantern", "Moth", "Anvil", "Sable", "Rook", "Tally", "Ember", "Flint", "Harrow", "Vesper", "Quill", "Garnet", "Bishop"};
    private static final String[] STARTER_WEAPONS = {"weapon.smg", "weapon.pump12", "weapon.mosin", "weapon.smg", "weapon.tommy", "weapon.riot", "weapon.sr16", "weapon.akr", "weapon.striker"};

    /**
     * 0.7: a slot is a place, not a person. Nobody is handed a squadmate - not at Rank 1, not when a slot opens:
     * every one of them is hired (the SHOP, Recruits) and arrives with the standard kit of their type. Kept so that
     * callers which used to grow the roster still compile and still mean "the roster is in a legal state".
     */
    public void ensure(int slots, long seed) {
        for (Mate m : mates) if (m.pack.isEmpty() && m.head.isEmpty() && "rifleman".equals(m.type)) { String w = m.weapon; List<String> b = new ArrayList<>(m.build); issueKit(m, typeForWeapon(w)); m.weapon = w; m.build.addAll(b); }
    }

    /**
     * Puts people on the roster without the shop: n recruits, one of each type in turn, each with the standard kit of
     * their type, split between Red and Blue. The dev console's "recruit" and the gates use it; play never does.
     */
    public void enlist(int n, long seed) {
        for (int i = 0; i < n && mates.size() < MAX_ROSTER; i++) {
            Rng r = new Rng(seed * 7919L + mates.size() * 104729L + 17);
            Mate m = new Mate();
            m.look = Appearance.random(r);
            m.name = cleanName(absentialis.sim.Names.squad(r, m.look.female));
            issueKit(m, TYPES[mates.size() % TYPES.length][0]);
            m.id = nextId++;
            m.team = teamSize(RED) <= teamSize(BLUE) ? RED : BLUE;
            m.active = activeCount() < MAX_ACTIVE && teamSize(m.team) < TEAM_CAP;
            mates.add(m);
        }
    }

    /** A fresh generated name (the RANDOM NAME button). */
    public String randomName(long salt) { return randomName(salt, false); }

    public String randomName(long salt, boolean female) { return absentialis.sim.Names.squad(new Rng(salt), female); }

    // ------------------------------------------------------------------ 0.6: loss and recruitment
    /** Squadmates this roster has ever been given by unlocking slots (never more than the slots unlocked). */
    public int granted;
    /** Recruits already hired, as "day:index" of the offer they answered. */
    public final java.util.Set<String> hired = new java.util.HashSet<>();

    /** Someone who did not come back: what the memorial keeps of them. */
    public record Fallen(String name, String title, int kills, int raids, int day, String cause) {}
    public final List<Fallen> fallen = new ArrayList<>();

    /** A candidate who can be hired into an empty place, and what they ask. */
    public record Offer(String key, Mate mate, int price) {}

    /** Places on the roster with nobody in them. */
    public int vacancies(int slots) { return Math.max(0, Math.min(MAX_ROSTER, Math.max(START, slots)) - mates.size()); }

    /** How many places this roster has: what Rank has opened, or - for a roster from before 0.7 - however many people are already on it. */
    public int places(int slots) { return Math.min(MAX_ROSTER, Math.max(slots, mates.size())); }

    /**
     * Permadeath. The squadmate is struck off the roster with everything they were - level, prestige, kills, build -
     * and remembered on the memorial. Nothing of theirs returns.
     */
    public Fallen bury(Mate m, int day, String cause) {
        if (!mates.remove(m)) return null;
        Fallen f = new Fallen(m.name, m.title(), m.kills, m.raids, day, cause == null ? "" : cause);
        fallen.add(0, f);
        while (fallen.size() > 40) fallen.remove(fallen.size() - 1);
        return f;
    }

    /** Hiring price: a green recruit is cheap, experience is not (tunable). */
    public static int RECRUIT_BASE = 6000, RECRUIT_PER_LEVEL = 900;

    /**
     * Who is looking for work today. Three candidates, the same three for a given profile and day until one is
     * hired; their experience runs from none up to a little under half the Rank of whoever is hiring.
     */
    public List<Offer> offers(long seed, int day, int rank) {
        List<Offer> l = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String key = day + ":" + i;
            if (hired.contains(key)) continue;
            Rng r = new Rng(seed * 131 + day * 7919L + i * 104729L);
            Mate m = new Mate();
            m.look = Appearance.random(r);
            m.name = cleanName(absentialis.sim.Names.squad(r, m.look.female));
            int lvl = 1 + r.nextInt(Math.max(1, Math.min(20, rank / 2)));
            m.xp = Progression.xpToReach(lvl);
            // three people, three different trades: the board never offers the same kind of soldier twice in a day
            issueKit(m, TYPES[Math.floorMod((int) (seed % 7) + day * 3 + i * 2 + r.nextInt(2), TYPES.length)][0]);
            l.add(new Offer(key, m, RECRUIT_BASE + (lvl - 1) * RECRUIT_PER_LEVEL));
        }
        return l;
    }

    /** Takes a recruit on. Returns null when done, or why not. */
    public String hire(Offer o, int slots, long coins) {
        if (vacancies(slots) <= 0) return "No empty place on the roster";
        if (coins < o.price()) return "Not enough cash";
        Mate m = o.mate();
        m.id = nextId++;
        m.team = teamSize(RED) <= teamSize(BLUE) ? RED : BLUE;
        m.active = activeCount() < MAX_ACTIVE && teamSize(m.team) < TEAM_CAP;
        mates.add(m);
        hired.add(o.key());
        return null;
    }

    /**
     * A name as the player typed it, made safe to store and draw: printable characters only, runs of spaces
     * collapsed, trimmed, at most 20 characters. Anything else is theirs to choose - first names, callsigns,
     * numbers, punctuation. Returns "" when nothing printable is left (the caller keeps the old name).
     */
    public static String cleanName(String raw) {
        if (raw == null) return "";
        StringBuilder b = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch < 32 || ch == 127) continue;
            if (ch == ' ') { space = b.length() > 0; continue; }
            if (space) { b.append(' '); space = false; }
            b.append(ch);
            if (b.length() >= 20) break;
        }
        return b.toString().trim();
    }

    /** How the squad says a name aloud: the callsign in quotes when there is one, otherwise the first word. */
    public static String shortName(String name) {
        if (name == null || name.isEmpty()) return "you";
        int a = name.indexOf('"'), b = name.lastIndexOf('"');
        if (a >= 0 && b > a + 1) return name.substring(a + 1, b);
        int sp = name.indexOf(' ');
        return sp > 0 ? name.substring(0, sp) : name;
    }

    public List<Mate> active() { List<Mate> l = new ArrayList<>(); for (Mate m : mates) if (m.active) l.add(m); return l; }
    public int activeCount() { int n = 0; for (Mate m : mates) if (m.active) n++; return n; }
    /** Deployed members of a team; "gold" is the whole deployed element. */
    public List<Mate> team(String team) { List<Mate> l = new ArrayList<>(); for (Mate m : mates) if (m.active && (GOLD.equals(team) || m.team.equals(team))) l.add(m); return l; }
    public int teamSize(String team) { return team(team).size(); }
    public Mate byId(long id) { for (Mate m : mates) if (m.id == id) return m; return null; }

    /** Moves a squadmate to Red or Blue. Teams need not be equal, but neither holds more than four deployed soldiers. */
    public String assign(Mate m, String team) {
        if (!RED.equals(team) && !BLUE.equals(team)) return "Squadmates belong to Red or Blue; Gold is the whole element";
        if (m.team.equals(team)) return null;
        if (m.active && teamSize(team) >= TEAM_CAP) return (RED.equals(team) ? "Red" : "Blue") + " Team is full (" + TEAM_CAP + ")";
        m.team = team;
        return null;
    }

    public String setActive(Mate m, boolean on) {
        if (m.active == on) return null;
        if (on) {
            if (activeCount() >= MAX_ACTIVE) return "The element is full: " + MAX_ACTIVE + " squadmates plus you";
            if (teamSize(m.team) >= TEAM_CAP) { String other = RED.equals(m.team) ? BLUE : RED; if (teamSize(other) >= TEAM_CAP) return "Both teams are full"; m.team = other; }
        }
        m.active = on;
        return null;
    }

    /** Weapons a squadmate may be issued: any firearm the player has unlocked. */
    public static List<ItemDef> armoury(Progression p) {
        List<ItemDef> l = new ArrayList<>();
        for (String id : Progression.platforms()) { ItemDef d = Content.def(id); if (d.weapon != null && !d.weapon.melee() && (p == null || p.platformUnlocked(id))) l.add(d); }
        return l;
    }

    // ------------------------------------------------------------------ persistence (separate from player and weapon progression)
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nextId", String.valueOf(nextId));
        List<Object> l = new ArrayList<>();
        for (Mate s : mates) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", String.valueOf(s.id));
            o.put("name", s.name);
            o.put("look", s.look.toJson());
            o.put("xp", String.valueOf(s.xp));
            o.put("weapon", s.weapon);
            o.put("build", new ArrayList<Object>(s.build));
            o.put("type", s.type);
            o.put("sidearm", s.sidearm);
            o.put("armor", s.armor);
            o.put("head", s.head);
            o.put("pack", new ArrayList<Object>(s.pack));
            o.put("team", s.team);
            o.put("active", s.active);
            o.put("kills", s.kills);
            o.put("raids", s.raids);
            o.put("downs", s.downs);
            l.add(o);
        }
        m.put("mates", l);
        m.put("granted", granted);
        m.put("hired", new ArrayList<Object>(hired));
        List<Object> fl = new ArrayList<>();
        for (Fallen f : fallen) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("name", f.name()); o.put("title", f.title()); o.put("kills", f.kills()); o.put("raids", f.raids()); o.put("day", f.day()); o.put("cause", f.cause());
            fl.add(o);
        }
        m.put("fallen", fl);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Roster fromJson(Map<String, Object> m) {
        Roster r = new Roster();
        if (m == null) return r;
        r.nextId = Json.lng(m, "nextId", 1);
        for (Object o : Json.list(m, "mates")) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> s = (Map<String, Object>) o;
            Mate t = new Mate();
            t.id = Json.lng(s, "id", r.mates.size() + 1);
            t.name = cleanName(Json.str(s, "name", "Recruit"));
            if (t.name.isEmpty()) t.name = "Recruit";
            t.look = Appearance.fromJson(Json.obj(s, "look"));
            t.xp = Json.lng(s, "xp", 0);
            t.weapon = Content.canon(Json.str(s, "weapon", "weapon.smg"));
            if (Content.def(t.weapon) == null || Content.def(t.weapon).weapon == null) t.weapon = "weapon.smg";
            t.build.addAll(Json.strList(s, "build"));
            if (s.containsKey("type")) {
                t.type = TYPES[typeIndex(Json.str(s, "type", "rifleman"))][0];
                t.sidearm = Json.str(s, "sidearm", "weapon.pm9"); t.armor = Json.str(s, "armor", ""); t.head = Json.str(s, "head", "");
                for (String pk : Json.strList(s, "pack")) { int c = pk.lastIndexOf(':'); if (c > 0 && Content.def(pk.substring(0, c)) != null) t.pack.add(pk); }
                if (!t.sidearm.isEmpty() && Content.def(t.sidearm) == null) t.sidearm = "weapon.pm9";
                if (!t.armor.isEmpty() && Content.def(t.armor) == null) t.armor = "";
                if (!t.head.isEmpty() && Content.def(t.head) == null) t.head = "";
            } else {
                // a squadmate from before 0.7: the weapon they were issued says what they are; the rest of the kit is the standard one
                String w = t.weapon; List<String> b = new ArrayList<>(t.build);
                issueKit(t, typeForWeapon(w));
                t.weapon = w; t.build.addAll(b);
            }
            t.team = BLUE.equals(Json.str(s, "team", RED)) ? BLUE : RED;
            t.active = Json.bool(s, "active", true);
            t.kills = Json.integer(s, "kills", 0);
            t.raids = Json.integer(s, "raids", 0);
            t.downs = Json.integer(s, "downs", 0);
            if (r.mates.size() < MAX_ROSTER) r.mates.add(t);
            r.nextId = Math.max(r.nextId, t.id + 1);
        }
        // a roster saved before 0.6 has no count: every place it had unlocked was full, so that is how many it was given
        r.granted = Json.integer(m, "granted", r.mates.size());
        r.hired.addAll(Json.strList(m, "hired"));
        for (Object o : Json.list(m, "fallen")) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> s = (Map<String, Object>) o;
            r.fallen.add(new Fallen(Json.str(s, "name", "?"), Json.str(s, "title", ""), Json.integer(s, "kills", 0), Json.integer(s, "raids", 0), Json.integer(s, "day", 0), Json.str(s, "cause", "")));
        }
        return r;
    }
}
