package absentialis.persistence;

import absentialis.engine.util.Json;
import absentialis.gameplay.inventory.Container;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.inventory.Loadout;
import absentialis.gameplay.items.ItemInstance;
import absentialis.gameplay.missions.Contracts;
import absentialis.gameplay.progression.Progression;
import absentialis.sim.ZoneState;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One of four isolated survivor profiles (GDD 4.1): appearance, currency, stash, loadout,
 * progression, reputation, hideout, contracts, world-state branch, map discovery and statistics.
 */
public final class Profile {
    public static final int STASH_W = 10, STASH_BASE_H = 24, MAP_CELL = 8, MAP_CELLS = World.W / MAP_CELL;

    public int slot;
    public String name = "Survivor";
    /** One life: when this survivor dies the profile is deleted (chosen at creation, never changed afterwards). */
    public boolean ironman;
    /**
     * 0.8: 0 Easy, 1 Normal, 2 Hard. Chosen when the profile is made and locked from then on (it used to be a global
     * setting anyone could flip between operations). It sets how sharp the Zone is - and what a death costs:
     *   Easy    keep five things of your choice, lose 5% of your cash
     *   Normal  keep three things of your choice, lose 10% of your cash
     *   Hard    keep nothing, lose 20% of your cash
     * Ironman is separate and can be switched on with any of the three: one life, and the profile goes with it.
     */
    public int difficulty = 1;
    public static final String[] DIFFICULTY = {"Easy", "Normal", "Hard"};
    public static final int[] KEEP_ON_DEATH = {5, 3, 0};
    public static final float[] CASH_LOST_ON_DEATH = {0.05f, 0.10f, 0.20f};
    public int keepOnDeath() { return KEEP_ON_DEATH[Math.max(0, Math.min(2, difficulty))]; }
    public float cashLostOnDeath() { return CASH_LOST_ON_DEATH[Math.max(0, Math.min(2, difficulty))]; }
    public String difficultyName() { return DIFFICULTY[Math.max(0, Math.min(2, difficulty))] + (ironman ? " - Ironman" : ""); }
    /** Vehicles owned (handling ids), and the one chosen to bring on the next operation ("" = on foot). */
    public final java.util.List<String> garage = new java.util.ArrayList<>();
    public String deployVehicle = "";
    public int armVariant = 4, skin = 1, outfit = 0, tagColor = 0;
    /** Customization: definition-table ids only (iteration P3). */
    public absentialis.character.Appearance appearance = absentialis.character.Appearance.legacy(1, 0);
    public boolean facePaint = true;
    public long scrip = 1500;
    public long seed = 1337L;
    public final Container stash = new Container("stash", STASH_W, STASH_BASE_H);
    public Loadout loadout = new Loadout();
    public Progression prog = new Progression();
    public final Map<String, Float> rep = new LinkedHashMap<>();
    public final Map<String, Integer> hideout = new LinkedHashMap<>();
    public Contracts contracts = new Contracts();
    /** 0.8: where this survivor stands with the five investigations of Act I. Kept through death; never reset. */
    public absentialis.gameplay.missions.Missions missions = new absentialis.gameplay.missions.Missions();
    /** 1.0: the four faction chains - where each stands, and every decision made in them. */
    public absentialis.gameplay.missions.Chains chains = new absentialis.gameplay.missions.Chains();
    public ZoneState zone = ZoneState.fresh();
    public final BitSet discovered = new BitSet(MAP_CELLS * MAP_CELLS);
    public final Map<String, Integer> stats = new LinkedHashMap<>();
    public final Map<String, Integer> recentSales = new LinkedHashMap<>();
    public final List<Map<String, Object>> presets = new ArrayList<>();
    public float health = 100, energy = 80, hydration = 80;
    /**
     * Yellow Herbs taken: each raises maximum health for good (gameplay.CombatConfig sets how much, and the cap).
     * Kept through rank resets and prestige - it is a property of the survivor, not of the climb.
     */
    public int herbs;
    public long lastFallbackDay = -10;
    public String lastDebrief = "";

    public Profile() {
        for (String f : ZoneState.FACTIONS) rep.put(f, "raiders".equals(f) ? -20f : "mercenaries".equals(f) ? -10f : 0f);
        hideout.put("workbench", 1);
        hideout.put("medical", 1);
        hideout.put("kitchen", 1);
        hideout.put("radio", 0);
        hideout.put("case", 0);
        hideout.put("stash", 0);
    }

    public int level(String facility) { return hideout.getOrDefault(facility, 0); }

    /** A-Life: every human and infected in the Zone as a persistent individual (seeded on first deployment). */
    public absentialis.sim.Agents agents = new absentialis.sim.Agents();
    /** Save format this profile was loaded from when it was older than the current one (0 = current); not saved. */
    public int migratedFrom;
    /**
     * 0.6. The faction this survivor has thrown in with ("" = not chosen yet: the hideout asks before anything else):
     * military, raiders (bandits), survivors (loners) or mercenaries. It decides where they may deploy from and what
     * work they are offered (see gameplay.Alliance).
     */
    public String alliance = "";
    /** Background of the profile card (ui.ProfileCard): one is unlocked at each tier of Rank. */
    public String cardBackground = "field";
    /** How many people the Zone holds: overall and per group (scene.PopulationSettings; 1 = as designed). */
    public final Map<String, Float> population = new LinkedHashMap<>();
    /** The rivalry engine: groups, leaders, memories, treaties (rivalry.RivalryState). */
    public absentialis.rivalry.RivalryState rivalry = new absentialis.rivalry.RivalryState();

    public float population(String key) { return population.getOrDefault(key, 1f); }

    /** Saved workbench builds: weapon definition id -> up to five attachment lists (null = empty slot). */
    public final Map<String, List<List<String>>> builds = new LinkedHashMap<>();
    /** The AI squad: roster, teams and their own progression (see gameplay.squad.Roster). */
    public absentialis.gameplay.squad.Roster squad = new absentialis.gameplay.squad.Roster();

    public List<List<String>> builds(String weaponId) {
        List<List<String>> l = builds.computeIfAbsent(weaponId, k -> new ArrayList<>());
        while (l.size() < absentialis.gameplay.weapons.Attachments.MAX_BUILDS) l.add(null);
        return l;
    }

    /**
     * Standing with a faction as it is acted on. 0.8: what the survivor said they were looking for when the profile was
     * made is a permanent lean toward one side - a few points that are always there, on top of whatever has been
     * earned or lost since ({@link absentialis.gameplay.Alliance#LEANING}).
     */
    public float rep(String faction) { float v = rep.getOrDefault(faction, 0f); return faction != null && faction.equals(alliance) ? Math.min(100, v + absentialis.gameplay.Alliance.LEANING) : v; }
    /** The earned figure alone, without the permanent lean. */
    public float earnedRep(String faction) { return rep.getOrDefault(faction, 0f); }
    public void addRep(String faction, float d) { if (rep.containsKey(faction)) rep.put(faction, Math.max(-100, Math.min(100, earnedRep(faction) + d))); }
    public void stat(String k, int d) { stats.merge(k, d, Integer::sum); }
    public int stat(String k) { return stats.getOrDefault(k, 0); }

    /** Applies stash height from rank/prestige/hideout and case size from the case workshop. */
    public void applyCapacity() {
        int rows = STASH_BASE_H + prog.stashRows() + level("stash") * 4;
        stash.h = Math.max(stash.h, rows);
        // the case grows with Rank (6x10 -> 9x13) and never shrinks again, Prestige included
        int[] d = prog.caseDims();
        loadout.resizeCase(d[0], d[1]);
        absentialis.gameplay.weapons.Gunsmith.prestigeLevel = prog::weaponPrestige;
        absentialis.gameplay.weapons.Gunsmith.tuning = prog::tune;
        contracts.alliance = alliance;
        squad.ensure(prog.squadSlots(), seed ^ name.hashCode());
    }

    public boolean discovered(int cx, int cz) { return cx >= 0 && cz >= 0 && cx < MAP_CELLS && cz < MAP_CELLS && discovered.get(cx * MAP_CELLS + cz); }

    /** Marks map cells around a world position as reconnoitred; returns newly revealed count. */
    public int discover(float x, float z, int radiusCells) {
        int cx = (int) x / MAP_CELL, cz = (int) z / MAP_CELL, n = 0;
        for (int dx = -radiusCells; dx <= radiusCells; dx++) for (int dz = -radiusCells; dz <= radiusCells; dz++) {
            if (dx * dx + dz * dz > radiusCells * radiusCells) continue;
            int a = cx + dx, b = cz + dz;
            if (a < 0 || b < 0 || a >= MAP_CELLS || b >= MAP_CELLS) continue;
            int i = a * MAP_CELLS + b;
            if (!discovered.get(i)) { discovered.set(i); n++; }
        }
        return n;
    }

    /** Every instance the profile owns (stash + loadout), for audits and uid uniqueness checks. */
    public List<ItemInstance> allItems() {
        List<ItemInstance> out = new ArrayList<>();
        for (ItemInstance i : stash.items) i.collect(out);
        out.addAll(loadout.all());
        return out;
    }

    public String verify() {
        String s = stash.verify();
        if (s != null) return s;
        s = loadout.caseGrid.verify();
        if (s != null) return s;
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (ItemInstance i : allItems()) if (!seen.add(i.uid)) return "duplicate instance uid " + i.uid + " (" + i.name() + ")";
        if (scrip < 0) return "negative currency";
        return null;
    }

    /**
     * v0.3 migration: the old 12x16 attaché case becomes the Rank-sized grid. Everything that was in it is
     * repacked; what no longer fits moves to the stash (which grows if it has to) - nothing is lost.
     */
    void migrateCase() {
        List<ItemInstance> held = new ArrayList<>(loadout.caseGrid.items);
        for (ItemInstance i : held) InventoryService.release(i);
        int[] d = prog.caseDims();
        loadout.caseGrid.w = d[0]; loadout.caseGrid.h = d[1];
        for (ItemInstance i : held) {
            if (InventoryService.store(i, loadout).ok()) continue;
            while (!InventoryService.add(i, stash).ok()) stash.h += 4;
        }
    }

    /**
     * 0.7: what a Rank 1 profile begins with, and all it begins with: a Handgun (fully loaded) in the holster,
     * thirty rounds for it, a knife, and one can of first-aid spray. No rifle, no armour, no squad. It is put on the
     * survivor rather than in the stash, so the first deployment can be made as they stand.
     */
    public void starterKit() {
        ItemInstance pistol = new ItemInstance("weapon.pm9");
        pistol.origin = "starter";
        pistol.rounds = pistol.capacity(); pistol.ammoId = "ammo.handgun";
        // 1.1: the issue pistol comes with the red laser already on it
        ItemInstance laser = new ItemInstance(absentialis.gameplay.weapons.Attachments.LASER_DEFAULT);
        laser.origin = "starter";
        pistol.attachments.put("laser", laser);
        if (!InventoryService.equip(pistol, loadout, "sidearm").ok()) InventoryService.add(pistol, stash);
        ItemInstance knife = new ItemInstance("melee.knife");
        knife.origin = "starter";
        if (!InventoryService.equip(knife, loadout, "melee").ok()) InventoryService.add(knife, stash);
        ItemInstance ammo = new ItemInstance("ammo.handgun");
        ammo.qty = STARTER_ROUNDS; ammo.origin = "starter";
        if (!InventoryService.store(ammo, loadout).ok()) InventoryService.add(ammo, stash);
        ItemInstance spray = new ItemInstance("med.spray");
        spray.origin = "starter";
        if (!InventoryService.store(spray, loadout).ok()) InventoryService.add(spray, stash);
    }
    public static final int STARTER_ROUNDS = 30;

    // ------------------------------------------------------------------ persistence
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", absentialis.Version.SAVE_FORMAT);
        m.put("game", absentialis.Version.NUMBER);
        m.put("slot", slot);
        m.put("name", name);
        m.put("armVariant", armVariant);
        m.put("skin", skin);
        m.put("outfit", outfit);
        m.put("tagColor", tagColor);
        m.put("appearance", appearance.toJson());
        m.put("ironman", ironman);
        m.put("difficulty", difficulty);
        m.put("garage", new java.util.ArrayList<Object>(garage));
        m.put("deployVehicle", deployVehicle);
        m.put("build", absentialis.Version.NUMBER);
        m.put("facePaint", facePaint);
        m.put("scrip", String.valueOf(scrip));
        m.put("seed", String.valueOf(seed));
        m.put("nextUid", String.valueOf(ItemInstance.peekUid()));
        m.put("stashH", stash.h);
        List<Object> st = new ArrayList<>();
        for (ItemInstance i : stash.items) st.add(i.toJson());
        m.put("stash", st);
        m.put("loadout", loadout.toJson());
        m.put("progression", prog.toJson());
        Map<String, Object> r = new LinkedHashMap<>();
        for (Map.Entry<String, Float> e : rep.entrySet()) r.put(e.getKey(), (double) e.getValue());
        m.put("rep", r);
        m.put("hideout", new LinkedHashMap<String, Object>(hideout));
        m.put("contracts", contracts.toJson());
        m.put("missions", missions.toJson());
        m.put("chains", chains.toJson());
        m.put("zone", zone.toJson());
        m.put("discovered", java.util.Base64.getEncoder().encodeToString(discovered.toByteArray()));
        m.put("stats", new LinkedHashMap<String, Object>(stats));
        m.put("recentSales", new LinkedHashMap<String, Object>(recentSales));
        m.put("presets", new ArrayList<Object>(presets));
        m.put("health", (double) health);
        m.put("herbs", (double) herbs);
        m.put("energy", (double) energy);
        m.put("hydration", (double) hydration);
        m.put("lastFallbackDay", String.valueOf(lastFallbackDay));
        m.put("lastDebrief", lastDebrief);
        Map<String, Object> bs = new LinkedHashMap<>();
        for (Map.Entry<String, List<List<String>>> e : builds.entrySet()) {
            List<Object> slots = new ArrayList<>();
            for (List<String> b : e.getValue()) slots.add(b == null ? null : new ArrayList<Object>(b));
            bs.put(e.getKey(), slots);
        }
        m.put("builds", bs);
        m.put("squad", squad.toJson());
        m.put("agents", agents.toJson());
        m.put("alliance", alliance);
        m.put("cardBackground", cardBackground);
        Map<String, Object> pop = new LinkedHashMap<>();
        for (Map.Entry<String, Float> e : population.entrySet()) pop.put(e.getKey(), (double) e.getValue());
        m.put("population", pop);
        m.put("rivalry", rivalry.toJson());
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Profile fromJson(Map<String, Object> m) {
        Profile p = new Profile();
        p.slot = Json.integer(m, "slot", 0);
        p.name = Json.str(m, "name", "Survivor");
        p.ironman = Json.bool(m, "ironman", false);
        // a profile from before 0.8 was played at whatever the global setting happened to be: it is Normal from here on
        p.difficulty = Math.max(0, Math.min(2, Json.integer(m, "difficulty", 1)));
        p.garage.addAll(Json.strList(m, "garage"));
        p.deployVehicle = Json.str(m, "deployVehicle", "");
        if (!p.garage.contains(p.deployVehicle)) p.deployVehicle = "";
        p.armVariant = Json.integer(m, "armVariant", 4);
        p.skin = Json.integer(m, "skin", 1);
        p.outfit = Json.integer(m, "outfit", 0);
        p.tagColor = Json.integer(m, "tagColor", 0);
        p.appearance = m.containsKey("appearance") ? absentialis.character.Appearance.fromJson(Json.obj(m, "appearance")) : absentialis.character.Appearance.legacy(p.skin, p.outfit);
        p.facePaint = Json.bool(m, "facePaint", true);
        ItemInstance.legacyOrientation = Json.integer(m, "version", 2) < 3;
        try { load(p, m); } finally { ItemInstance.legacyOrientation = false; }
        return p;
    }

    @SuppressWarnings("unchecked")
    private static void load(Profile p, Map<String, Object> m) {
        p.scrip = Json.lng(m, "scrip", 0);
        p.seed = Json.lng(m, "seed", 1337L);
        ItemInstance.ensureUidAbove(Json.lng(m, "nextUid", 1) - 1);
        p.stash.h = Json.integer(m, "stashH", STASH_BASE_H);
        for (Object o : Json.list(m, "stash")) { ItemInstance i = ItemInstance.fromJson((Map<String, Object>) o); if (i != null) p.stash.restore(i); }
        p.loadout = Loadout.fromJson(Json.obj(m, "loadout"));
        p.prog = Progression.fromJson(Json.obj(m, "progression"));
        for (Map.Entry<String, Object> e : Json.obj(m, "rep").entrySet()) p.rep.put(e.getKey(), ((Number) e.getValue()).floatValue());
        for (Map.Entry<String, Object> e : Json.obj(m, "hideout").entrySet()) p.hideout.put(e.getKey(), ((Number) e.getValue()).intValue());
        p.contracts = Contracts.fromJson(Json.obj(m, "contracts"));
        p.missions = absentialis.gameplay.missions.Missions.fromJson(Json.obj(m, "missions"));
        p.chains = absentialis.gameplay.missions.Chains.fromJson(Json.obj(m, "chains"));
        p.zone = ZoneState.fromJson(Json.obj(m, "zone"));
        String disc = Json.str(m, "discovered", "");
        if (!disc.isEmpty()) p.discovered.or(BitSet.valueOf(java.util.Base64.getDecoder().decode(disc)));
        for (Map.Entry<String, Object> e : Json.obj(m, "stats").entrySet()) p.stats.put(e.getKey(), ((Number) e.getValue()).intValue());
        for (Map.Entry<String, Object> e : Json.obj(m, "recentSales").entrySet()) p.recentSales.put(e.getKey(), ((Number) e.getValue()).intValue());
        for (Object o : Json.list(m, "presets")) if (o instanceof Map) p.presets.add((Map<String, Object>) o);
        p.health = (float) Json.num(m, "health", 100);
        p.herbs = Math.max(0, Math.min(absentialis.gameplay.CombatConfig.get().herbCap(), Json.integer(m, "herbs", 0)));
        p.energy = (float) Json.num(m, "energy", 80);
        p.hydration = (float) Json.num(m, "hydration", 80);
        p.lastFallbackDay = Json.lng(m, "lastFallbackDay", -10);
        p.lastDebrief = Json.str(m, "lastDebrief", "");
        for (Map.Entry<String, Object> e : Json.obj(m, "builds").entrySet()) {
            List<List<String>> slots = new ArrayList<>();
            if (e.getValue() instanceof List) for (Object o : (List<Object>) e.getValue()) {
                if (!(o instanceof List)) { slots.add(null); continue; }
                List<String> b = new ArrayList<>();
                for (Object s : (List<Object>) o) b.add(String.valueOf(s));
                slots.add(b);
            }
            p.builds.put(e.getKey(), slots);
        }
        if (m.containsKey("agents")) p.agents = absentialis.sim.Agents.fromJson(Json.obj(m, "agents"));
        if (m.containsKey("squad")) p.squad = absentialis.gameplay.squad.Roster.fromJson(Json.obj(m, "squad"));
        if (Json.integer(m, "version", 2) < 4) p.migrateCase();
        if (Json.integer(m, "version", 2) < 5) {
            // 0.5 rebuilt the city. Everything the survivor owns and has earned carries over; what described the old
            // streets - who lived where, which ground had been walked, where fights had left their marks - describes
            // nothing now and is started afresh.
            p.agents = new absentialis.sim.Agents();
            p.discovered.clear();
            p.zone.log.clear();
            p.migratedFrom = Json.integer(m, "version", 2);
        }
        // ---- 0.6 fields: absent in older saves, where every default below is the behaviour the save was made under
        p.alliance = Json.str(m, "alliance", "");
        p.cardBackground = Json.str(m, "cardBackground", "field");
        for (Map.Entry<String, Object> e : Json.obj(m, "population").entrySet()) if (e.getValue() instanceof Number n) p.population.put(e.getKey(), Math.max(0.1f, Math.min(3f, n.floatValue())));
        p.rivalry = absentialis.rivalry.RivalryState.fromJson(Json.obj(m, "rivalry"));
        if (Json.integer(m, "version", 2) < 6) {
            // names generated before 0.6 came from Ukrainian and Russian tables: generated ones are redrawn from the
            // American ones (the callsign is kept); a name the player typed is left exactly as it is
            for (absentialis.gameplay.squad.Roster.Mate mt : p.squad.mates) mt.name = absentialis.gameplay.squad.Roster.cleanName(absentialis.sim.Names.migrate(mt.name, mt.id + p.seed, mt.look.female));
            if (p.migratedFrom == 0) p.migratedFrom = Json.integer(m, "version", 2);
            // 0.6 made the flashlight an object: everyone who had the free one is issued the real one
            if (p.loadout.count("tool.flashlight") == 0 && p.stash.items.stream().noneMatch(i -> i.defId.equals("tool.flashlight"))) { ItemInstance t = new ItemInstance("tool.flashlight"); t.origin = "starter"; InventoryService.add(t, p.stash); }
        }
        if (Json.integer(m, "version", 2) < 7) {
            // ---- 0.7. Nothing owned is taken away. The paid treatment is gone, so nobody starts 0.7 half-healed; squadmates
            // already on the roster stay (the roster simply has no free place until Rank opens one) and are issued the
            // standard kit of the trade their weapon implies; the allegiance already declared stands.
            p.health = 100;
            if (p.migratedFrom == 0) p.migratedFrom = Json.integer(m, "version", 2);
        }
        if (Json.integer(m, "version", 2) < 8 && p.migratedFrom == 0) p.migratedFrom = Json.integer(m, "version", 2);
        p.applyCapacity();
    }
}
