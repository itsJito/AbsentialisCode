package absentialis.gameplay.progression;

import absentialis.engine.util.Json;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.weapons.Attachments;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Player Rank, Prestige and weapon progression (v0.3).
 *
 * RANK      50 ranks, Private to Commander. One climb costs exactly 1,265,500 XP (rank r -> r+1
 *           costs 2,360 + 900r; rank 50 needs a final 47,360 to complete the cycle).
 * PRESTIGE  Fifteen climbs: Prestige 0 is the first, entering Prestige 15 makes a Prestige Master
 *           at 18,982,500 XP, who keeps Rank 50 for good. Each transition grants one Prestige
 *           Unlock Token (a permanent, gold-star unlock) and one Prestige Award of the player's choice.
 * INVENTORY The case grows at Ranks 4, 10, 15 and 20 and is a permanent profile unlock.
 * WEAPONS   Every firearm has its own level track that unlocks its attachments; a maxed weapon can
 *           be prestiged for a permanent stat bonus and death protection. Independent of the above.
 *
 * Rank never adds damage: it opens weapons, attachment categories, case space and squad slots.
 */
public final class Progression {
    public static final int RANKS = 50, MAX_PRESTIGE = 15, MAX_EXTRA_CLASSES = 5, BASE_CLASSES = 5, MAX_WEAPON_PRESTIGE = 3;
    public static final long CYCLE_XP = 1_265_500L;
    public static final int GOLD_CAMO_PRESTIGE = 14, GOLD_CAMO_PRICE = 50_000;

    public long cycleXp;        // XP inside the current cycle [0, CYCLE_XP]
    public long lifetimeXp;
    public int prestige;        // 0..15 (15 = Prestige Master)
    /** Highest Rank ever held: case size and squad slots hang off this, so Prestige never shrinks them. */
    public int bestRank = 1;
    /** Regular Unlock Tokens (one per Rank gained): open a weapon ahead of its Rank for this cycle only. */
    public int unlockTokens;
    public final Set<String> tokenSpent = new LinkedHashSet<>();
    public int lastCycleSpent;
    /** Prestige Unlock Tokens waiting to be spent, and what the spent ones made permanent (weapon or attachment ids). */
    public int permTokens;
    public final Set<String> permanent = new LinkedHashSet<>();
    /** A Prestige Award is owed: the player must pick one before prestiging again. */
    public boolean awardPending;
    public int extraClasses;
    public final Set<String> cosmetics = new LinkedHashSet<>();
    /** Weapon XP per weapon definition (reset by Weapon Prestige) and the Weapon Prestige level reached. */
    public final Map<String, Long> mastery = new LinkedHashMap<>();
    public final Map<String, Integer> weaponPrestige = new LinkedHashMap<>();

    // ------------------------------------------------------------------ curve
    public static long costOf(int rank) { return 2360L + 900L * rank; }

    /** XP needed from the start of the cycle to reach the given rank. */
    public static long xpToReach(int rank) {
        long s = 0;
        for (int r = 1; r < Math.min(rank, RANKS + 1); r++) s += costOf(r);
        return s;
    }

    public int rank() {
        if (master()) return RANKS;
        int r = 1;
        long acc = 0;
        while (r < RANKS && acc + costOf(r) <= cycleXp) { acc += costOf(r); r++; }
        return r;
    }

    /** Progress 0..1 toward the next rank (or toward cycle completion at rank 50). */
    public float rankProgress() {
        if (master()) return 1;
        int r = rank();
        long base = xpToReach(r), need = costOf(r);
        return Math.min(1f, (cycleXp - base) / (float) need);
    }

    /** Where a further amount of experience would put this climb: {rank, progress 0..1 toward the next}. Changes nothing. */
    public float[] preview(long extra) {
        if (master()) return new float[]{RANKS, 1};
        long xp = Math.min(CYCLE_XP, cycleXp + Math.max(0, extra));
        int r = 1;
        long acc = 0;
        while (r < RANKS && acc + costOf(r) <= xp) { acc += costOf(r); r++; }
        return new float[]{r, Math.min(1f, (xp - acc) / (float) costOf(r))};
    }

    public boolean cycleComplete() { return cycleXp >= CYCLE_XP; }
    public boolean canPrestige() { return cycleComplete() && prestige < MAX_PRESTIGE && !awardPending; }
    public boolean master() { return prestige >= MAX_PRESTIGE; }
    public static long totalToMaster() { return CYCLE_XP * MAX_PRESTIGE; }

    /** Adds XP; returns the number of ranks gained (each grants one Unlock Token). Overflow past Rank 50 is not banked. */
    public int addXp(long xp) {
        if (xp <= 0 || master()) return 0;
        int before = rank();
        lifetimeXp += Math.min(xp, CYCLE_XP - cycleXp);
        cycleXp = Math.min(CYCLE_XP, cycleXp + xp);
        int gained = rank() - before;
        unlockTokens += gained;
        bestRank = Math.max(bestRank, rank());
        return gained;
    }

    // ------------------------------------------------------------------ rank names
    private static final Object[][] NAMES = {
            {"Private", 1}, {"Private First Class", 4}, {"Lance Corporal", 7}, {"Corporal", 10}, {"Sergeant", 14}, {"Staff Sergeant", 18},
            {"Gunnery Sergeant", 20}, {"Master Sergeant", 22}, {"Master Gunnery Sergeant", 24}, {"Second Lieutenant", 26}, {"First Lieutenant", 28},
            {"Captain", 30}, {"Major", 32}, {"Lieutenant Colonel", 34}, {"Colonel", 36}, {"Brigadier General", 38}, {"Major General", 40},
            {"Lieutenant General", 42}, {"General", 44}, {"Field Marshal", 46}, {"Commander", 48}};

    public static String rankName(int rank) {
        String n = (String) NAMES[0][0];
        for (Object[] e : NAMES) if (rank >= (Integer) e[1]) n = (String) e[0];
        return n;
    }

    /** {name, first level, last level} for every title, in order. */
    public static List<String[]> rankTable() {
        List<String[]> l = new ArrayList<>();
        for (int i = 0; i < NAMES.length; i++) l.add(new String[]{(String) NAMES[i][0], String.valueOf(NAMES[i][1]), String.valueOf(i + 1 < NAMES.length ? (Integer) NAMES[i + 1][1] - 1 : RANKS)});
        return l;
    }

    public String title() {
        if (master()) return "Prestige Master  -  Commander 50";
        return (prestige > 0 ? "Prestige " + prestige + "  " : "") + rankName(rank()) + " " + rank();
    }

    // ------------------------------------------------------------------ inventory (permanent)
    /** Case grid {columns, rows} for a Rank: 6x10, 7x12, 8x12, 8x13, 9x13 (rows x columns). */
    public static int[] caseDimsAt(int rank) {
        if (rank >= 20) return new int[]{13, 9};
        if (rank >= 15) return new int[]{13, 8};
        if (rank >= 10) return new int[]{12, 8};
        if (rank >= 4) return new int[]{12, 7};
        return new int[]{10, 6};
    }

    public int[] caseDims() { return caseDimsAt(bestRank); }

    /** 0.7: nobody at Rank 1. A roster slot opens at each of Ranks 4, 8, 12 ... 36 - nine in all - and is kept through Prestige. */
    public int squadSlots() { int n = 0; for (int r : SQUAD_RANKS) if (bestRank >= r) n++; return n; }
    public static final int[] SQUAD_RANKS = {4, 8, 12, 16, 20, 24, 28, 32, 36};
    /** The Rank at which the squad exists at all (the SQUAD tab is hidden below it). */
    public static final int SQUAD_FIRST_RANK = SQUAD_RANKS[0];

    /** Custom class (loadout preset) slots: five, plus up to five from Prestige Awards. */
    public int presets() { return BASE_CLASSES + extraClasses; }

    public float carryCapacityKg() { return 28f + rank() * 0.25f; }
    public int stashRows() { return bestRank / 10 + prestige; }
    public int prestigeStashRows() { return prestige; }
    public int traderTier() { int r = rank(); return r >= 40 ? 4 : r >= 25 ? 3 : r >= 12 ? 2 : 1; }

    // ------------------------------------------------------------------ weapon access
    public static int platformRank(String weaponId) { ItemDef d = Content.def(weaponId); return d == null ? 1 : d.rank; }

    /** Available from the shop and the workbench: by Rank, by a regular token this cycle, or permanently. */
    public boolean platformUnlocked(String weaponId) {
        return permanent.contains(weaponId) || tokenSpent.contains(weaponId) || rank() >= platformRank(weaponId);
    }

    public static List<String> platforms() {
        List<ItemDef> l = new ArrayList<>();
        for (ItemDef d : Content.ITEMS.values()) if (d.weapon != null) l.add(d);
        l.sort((a, b) -> a.rank != b.rank ? Integer.compare(a.rank, b.rank) : a.id.compareTo(b.id));
        List<String> out = new ArrayList<>();
        for (ItemDef d : l) out.add(d.id);
        return out;
    }

    public String spendUnlockToken(String weaponId) {
        if (unlockTokens <= 0) return "No Unlock Tokens - one is earned with every Rank";
        if (platformUnlocked(weaponId)) return "Already unlocked";
        if (Content.def(weaponId) == null || Content.def(weaponId).weapon == null) return "Not a weapon";
        unlockTokens--;
        tokenSpent.add(weaponId);
        return null;
    }

    /** Items a Prestige Unlock Token may make permanent: any weapon or attachment not already starred. */
    public boolean eligiblePermanent(String id) {
        ItemDef d = Content.def(id);
        return d != null && (d.weapon != null && d.rank > 1 || d.attachment != null) && !permanent.contains(id);
    }

    public String spendPermToken(String id) {
        if (permTokens <= 0) return "No Prestige Unlock Token to spend";
        if (!eligiblePermanent(id)) return "Not eligible for a permanent unlock";
        permTokens--;
        permanent.add(id);
        return null;
    }

    // ------------------------------------------------------------------ 1.1: weapon upgrades
    /** Steps bought on each of a weapon's tracks (gameplay.weapons.Gunsmith), by weapon definition. Reset with the weapon's levels by Weapon Prestige. */
    public final Map<String, int[]> tuning = new LinkedHashMap<>();

    public int tune(String weapon, int track) { int[] t = tuning.get(weapon); return t == null || track < 0 || track >= t.length ? 0 : t[track]; }
    public int upgradesSpent(String weapon) { int[] t = tuning.get(weapon); int n = 0; if (t != null) for (int v : t) n += v; return n; }
    /** One for every level after the first, less what has been spent. */
    public int upgradePoints(String weapon) { return Math.max(0, weaponLevel(weapon) - 1 - upgradesSpent(weapon)); }

    /** Spends an upgrade on a track. Returns why not, or null. */
    public String upgrade(String weapon, int track) {
        ItemDef d = Content.def(weapon);
        if (d == null || d.weapon == null) return "Not a weapon";
        int tracks = absentialis.gameplay.weapons.Gunsmith.tracks(d).length;
        if (track < 0 || track >= tracks) return "No such upgrade";
        if (tune(d.id, track) >= Attachments.UPGRADE_STEPS) return absentialis.gameplay.weapons.Gunsmith.tracks(d)[track] + " is as far as it goes";
        if (upgradePoints(d.id) <= 0) return "No upgrade to spend - the next comes with weapon level " + (weaponLevel(d.id) + 1);
        tuning.computeIfAbsent(d.id, k -> new int[4])[track]++;
        return null;
    }

    // ------------------------------------------------------------------ weapon levels & weapon prestige
    public void addMastery(String weapon, long xp) {
        ItemDef d = Content.def(weapon);
        if (d == null || d.weapon == null) return;
        long cap = Attachments.xpForLevel(Attachments.maxLevel(d));
        mastery.merge(d.id, xp, (a, b) -> Math.min(cap, a + b));
    }

    public static int masteryLevel(long xp) { return Attachments.levelFor(xp, 99); }
    public int masteryLevel(String weapon) { return weaponLevel(weapon); }

    public int weaponLevel(String weapon) {
        ItemDef d = Content.def(weapon);
        if (d == null || d.weapon == null) return 1;
        return Attachments.levelFor(mastery.getOrDefault(d.id, 0L), Attachments.maxLevel(d));
    }

    public float weaponLevelProgress(String weapon) {
        ItemDef d = Content.def(weapon);
        int l = weaponLevel(weapon);
        if (d == null || l >= Attachments.maxLevel(d)) return 1;
        long a = Attachments.xpForLevel(l), b = Attachments.xpForLevel(l + 1);
        return (mastery.getOrDefault(d.id, 0L) - a) / (float) (b - a);
    }

    public int weaponPrestige(String weapon) { return weaponPrestige.getOrDefault(weapon, 0); }

    public boolean attachmentUnlocked(ItemDef weapon, ItemDef part) {
        if (!Attachments.fits(weapon, part)) return false;
        if (permanent.contains(part.id)) return true;
        return weaponLevel(weapon.id) >= Attachments.unlockLevel(weapon, part);
    }

    /** 1.1: whether a weapon's slot is open yet. */
    public boolean slotOpen(ItemDef weapon, String category) { int l = Attachments.slotLevel(weapon, category); return l > 0 && weaponLevel(weapon.id) >= l; }

    /** Why a part is locked, for the workbench. */
    public String lockReason(ItemDef weapon, ItemDef part) {
        if (attachmentUnlocked(weapon, part)) return null;
        int lvl = Attachments.unlockLevel(weapon, part);
        return "Weapon level " + lvl + " (" + Attachments.categoryName(part.attachment.socket) + " slot)";
    }

    public boolean canPrestigeWeapon(String weapon) {
        ItemDef d = Content.def(weapon);
        return d != null && d.weapon != null && !d.weapon.melee() && weaponLevel(weapon) >= Attachments.maxLevel(d) && weaponPrestige(weapon) < MAX_WEAPON_PRESTIGE;
    }

    /** Resets the weapon's attachment progression and grants its permanent bonus and death protection. */
    public String prestigeWeapon(String weapon) {
        if (!canPrestigeWeapon(weapon)) return weaponPrestige(weapon) >= MAX_WEAPON_PRESTIGE ? "Weapon Prestige is at its maximum" : "Unlock every attachment first";
        weaponPrestige.merge(weapon, 1, Integer::sum);
        mastery.put(weapon, 0L);
        tuning.remove(weapon);
        return null;
    }

    /** A prestiged weapon comes home when its owner does not. */
    public boolean deathProtected(String weapon) { return weaponPrestige(weapon) > 0; }

    public boolean goldCamoAvailable(String weapon) { return prestige >= GOLD_CAMO_PRESTIGE && weaponPrestige(weapon) > 0; }
    public boolean ownsGoldCamo(String weapon) { return cosmetics.contains("gold:" + weapon); }

    // ------------------------------------------------------------------ prestige
    public static final String[] AWARDS = {"class", "refund", "token", "fresh"};

    public static String awardName(String a) {
        return switch (a) { case "class" -> "Extra Custom Class"; case "refund" -> "Unlock Tokens Refund"; case "token" -> "Permanent Unlock Token"; default -> "Fresh Start"; };
    }

    public String awardText(String a) {
        return switch (a) {
            case "class" -> "One more custom class slot (" + presets() + " now, 10 at most).";
            case "refund" -> "Returns the " + lastCycleSpent + " regular Unlock Token" + (lastCycleSpent == 1 ? "" : "s") + " you spent last cycle. What they bought still resets.";
            case "token" -> "A second Prestige Unlock Token: one more item unlocked from Rank 1, permanently.";
            default -> "Irreversible. Wipes this profile back to a non-Prestige Rank 1.";
        };
    }

    public boolean awardAvailable(String a) {
        return switch (a) { case "class" -> extraClasses < MAX_EXTRA_CLASSES; case "fresh" -> !master(); default -> true; };
    }

    public static String rewardText(int p) {
        if (p >= MAX_PRESTIGE) return "Prestige Master: Rank 50 kept permanently";
        return p == GOLD_CAMO_PRESTIGE ? "Gold camouflage for prestiged weapons" : "Prestige " + p + " emblem";
    }

    /**
     * Enters the next Prestige: Rank returns to 1, rank-gated weapon and attachment access and regular
     * token unlocks reset, and the player receives one Prestige Unlock Token plus a Prestige Award
     * selection. Inventory size, items, cosmetics, permanent unlocks, squadmates and weapon
     * progression are untouched. Returns an error or null.
     */
    public String prestige() {
        if (!cycleComplete()) return "Complete Rank 50 first";
        if (prestige >= MAX_PRESTIGE) return "Already a Prestige Master";
        if (awardPending) return "Choose your Prestige Award first";
        prestige++;
        lastCycleSpent = tokenSpent.size();
        tokenSpent.clear();
        unlockTokens = 0;
        permTokens++;
        awardPending = true;
        cosmetics.add("prestige_emblem_" + prestige);
        if (master()) { cycleXp = CYCLE_XP; cosmetics.add("master_emblem"); }
        else cycleXp = 0;
        return null;
    }

    /** Applies the chosen Prestige Award ("fresh" is carried out by the caller, which owns the profile). */
    public String chooseAward(String a) {
        if (!awardPending) return "No Prestige Award to choose";
        if (!awardAvailable(a)) return awardName(a) + " is no longer available";
        switch (a) {
            case "class" -> extraClasses++;
            case "refund" -> unlockTokens += lastCycleSpent;
            case "token" -> permTokens++;
            default -> {}
        }
        awardPending = false;
        return null;
    }

    // ------------------------------------------------------------------ roadmap
    /** What each Rank opens, in order: {rank, description}. */
    public static List<String[]> roadmap() {
        List<String[]> l = new ArrayList<>();
        for (int r = 2; r <= RANKS; r++) {
            for (String id : platforms()) if (platformRank(id) == r) l.add(new String[]{String.valueOf(r), "Weapon: " + Content.def(id).name});
            for (String c : Attachments.CATEGORIES) if (Attachments.categoryRank(c) == r) l.add(new String[]{String.valueOf(r), "Attachments: " + Attachments.categoryName(c)});
            if (r == 4 || r == 10 || r == 15 || r == 20) { int[] d = caseDimsAt(r); l.add(new String[]{String.valueOf(r), "Inventory " + d[1] + "x" + d[0] + " (" + d[0] * d[1] + " slots)"}); }
            for (int s : SQUAD_RANKS) if (s == r) l.add(new String[]{String.valueOf(r), s == SQUAD_FIRST_RANK ? "The SQUAD: first squadmate slot (hire in the SHOP)" : "+1 squadmate roster slot"});
            for (Object[] e : NAMES) if ((Integer) e[1] == r) l.add(new String[]{String.valueOf(r), "Promotion: " + e[0]});
        }
        return l;
    }

    /** The next few roadmap entries above the current rank. */
    public List<String[]> nextUnlocks(int n) {
        List<String[]> out = new ArrayList<>();
        int r = rank();
        for (String[] e : roadmap()) { if (Integer.parseInt(e[0]) > r && out.size() < n) out.add(e); }
        return out;
    }

    // ------------------------------------------------------------------ persistence
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cycleXp", String.valueOf(cycleXp));
        m.put("lifetimeXp", String.valueOf(lifetimeXp));
        m.put("prestige", prestige);
        m.put("bestRank", bestRank);
        m.put("unlockTokens", unlockTokens);
        m.put("tokenSpent", new ArrayList<>(tokenSpent));
        m.put("lastCycleSpent", lastCycleSpent);
        m.put("permTokens", permTokens);
        m.put("permanent", new ArrayList<>(permanent));
        m.put("awardPending", awardPending);
        m.put("extraClasses", extraClasses);
        m.put("cosmetics", new ArrayList<>(cosmetics));
        Map<String, Object> ms = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : mastery.entrySet()) ms.put(e.getKey(), String.valueOf(e.getValue()));
        m.put("mastery", ms);
        m.put("weaponPrestige", new LinkedHashMap<String, Object>(weaponPrestige));
        Map<String, Object> tn = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : tuning.entrySet()) tn.put(e.getKey(), e.getValue()[0] + "," + e.getValue()[1] + "," + e.getValue()[2] + "," + e.getValue()[3]);
        m.put("tuning", tn);
        return m;
    }

    public static Progression fromJson(Map<String, Object> m) {
        Progression p = new Progression();
        if (m == null) return p;
        p.cycleXp = Json.lng(m, "cycleXp", 0);
        p.lifetimeXp = Json.lng(m, "lifetimeXp", 0);
        p.prestige = Math.min(MAX_PRESTIGE, Json.integer(m, "prestige", 0));
        p.unlockTokens = Json.integer(m, "unlockTokens", 0);
        p.tokenSpent.addAll(Json.strList(m, "tokenSpent"));
        p.lastCycleSpent = Json.integer(m, "lastCycleSpent", 0);
        p.permTokens = Json.integer(m, "permTokens", Json.integer(m, "tokens", 0));   // "tokens": pre-0.3 name
        p.permanent.addAll(Json.strList(m, "permanent"));
        for (String old : Json.strList(m, "tokenUnlocks")) if (old.startsWith("platform:")) p.permanent.add(Content.canon(old.substring(9)));
        p.awardPending = Json.bool(m, "awardPending", false);
        p.extraClasses = Json.integer(m, "extraClasses", 0);
        p.cosmetics.addAll(Json.strList(m, "cosmetics"));
        for (Map.Entry<String, Object> e : Json.obj(m, "mastery").entrySet()) p.addMastery(e.getKey(), Long.parseLong(String.valueOf(e.getValue())));
        for (Map.Entry<String, Object> e : Json.obj(m, "weaponPrestige").entrySet()) p.weaponPrestige.put(e.getKey(), ((Number) e.getValue()).intValue());
        p.bestRank = Math.max(Json.integer(m, "bestRank", 1), p.prestige > 0 ? RANKS : p.rank());
        for (Map.Entry<String, Object> e : Json.obj(m, "tuning").entrySet()) {
            String[] parts = String.valueOf(e.getValue()).split(",");
            int[] t = new int[4];
            for (int i = 0; i < 4 && i < parts.length; i++) try { t[i] = Math.max(0, Math.min(Attachments.UPGRADE_STEPS, Integer.parseInt(parts[i].trim()))); } catch (NumberFormatException ignored) { }
            p.tuning.put(e.getKey(), t);
        }
        return p;
    }
}
