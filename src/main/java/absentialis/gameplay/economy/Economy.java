package absentialis.gameplay.economy;

import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.gameplay.progression.Progression;
import absentialis.sim.ZoneState;
import absentialis.world.District;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Traders and prices (GDD 14). Buy prices respond to district supply and reputation; sell prices
 * are always strictly below the matching buy price, decay with repeated sales of the same item
 * (saturation) and are zero for fallback kit items, so no buy/sell loop can mint currency.
 */
public final class Economy {
    private Economy() {}

    public record Trader(String id, String name, String faction, String blurb, List<String> categories, float sellRate) {}

    public static final List<Trader> TRADERS = List.of(
            new Trader("mara", "Quartermaster Mara", "survivors", "Survivor co-op. Food, medicine, basics. Buys almost anything.",
                    List.of("food", "medical", "material", "ammo", "magazine", "valuable", "treasure", "herb", "tool", "explosive"), 0.45f),
            new Trader("voss", "Lt. Voss (Armory)", "military", "Cordon armory. Weapons, armour, parts. Requires standing with the military.",
                    List.of("weapon", "ammo", "magazine", "armor", "attachment", "medical", "charm"), 0.40f),
            new Trader("kessler", "Kessler (Fence)", "raiders", "No questions asked. Buys stolen faction gear and quest goods - at a cut.",
                    List.of("valuable", "quest", "weapon", "explosive", "attachment", "document", "treasure", "charm"), 0.33f));

    public static Trader trader(String id) { for (Trader t : TRADERS) if (t.id.equals(id)) return t; return null; }

    /** Minimum trader tier at which an item is stocked (enlisted rank gates breadth). */
    public static int tierOf(ItemDef d) {
        if (d.value >= 2000) return 4;
        if (d.value >= 900) return 3;
        if (d.value >= 300) return 2;
        return 1;
    }

    private static float supplyFactor(ZoneState z, ItemDef d) {
        String cat = Content.supplyCategory(d);
        float avg = 0;
        for (District dd : District.CITY) avg += z.supply[dd.ordinal()][ZoneState.si(cat)];
        avg /= District.CITY.length;
        return 1.35f - Math.min(1.2f, avg) * 0.35f; // scarce -> pricier (x0.93 .. x1.3)
    }

    private static float repFactor(float rep) { return 1.15f - Math.max(-100, Math.min(100, rep)) / 100f * 0.15f; }

    public static int buyPrice(ItemDef d, Trader t, ZoneState z, float rep) {
        float p = Math.max(1, d.value) * supplyFactor(z, d) * repFactor(rep);
        if (d.stackable()) p *= 1; // per unit
        return Math.max(1, Math.round(p));
    }

    /** Sale price for one instance (whole stack). recentSales = times this def was sold recently. */
    public static int sellPrice(ItemInstance it, Trader t, ZoneState z, float rep, int recentSales) {
        ItemDef d = it.def();
        if (it.fallback || d.value <= 0 && !"kessler".equals(t.id)) return 0;
        float unitBuy = buyPrice(d, t, z, rep);
        float sat = (float) Math.pow(0.9, Math.min(15, recentSales));
        float v = it.baseValueTotal() / (float) Math.max(1, d.stackable() ? it.qty : 1);
        if (d.value <= 0 && "kessler".equals(t.id)) v = 150; // quest goods fenced
        float unit = Math.min(unitBuy * 0.9f, v * t.sellRate * sat * repFactor(-rep) * 0.95f);
        int qty = d.stackable() ? it.qty : 1;
        return Math.max(0, Math.round(unit * qty));
    }

    public static boolean accepts(Trader t, ItemDef d) { return t.categories.contains(d.category); }

    /** Trader stock for the current progression tier and reputation. */
    public static List<ItemDef> stock(Trader t, Progression p, float rep) {
        List<ItemDef> out = new ArrayList<>();
        if ("voss".equals(t.id) && rep < 0) return out;
        int tier = p.traderTier() + (rep >= 50 ? 1 : 0);
        for (ItemDef d : Content.ITEMS.values()) {
            if (!accepts(t, d) || d.questItem || d.value <= 0 || "treasure".equals(d.category) || "document".equals(d.category)) continue;
            if ("ammo".equals(d.category)) continue;                // 1.1: nobody sells ammunition. It is found, or it is made - gunpowder is what is sold.
            if (d.id.startsWith("food.egg")) continue;              // 1.1: eggs are taken from chickens and sold; nobody sells them
            if (d.id.startsWith("finish.gold") || "valuable".equals(d.category) && !"kessler".equals(t.id)) continue;
            if ("kessler".equals(t.id) && !"explosive".equals(d.category) && !"attachment".equals(d.category)) continue;
            if (tierOf(d) > tier) continue;
            if (d.weapon != null && !p.platformUnlocked(d.id)) continue;
            out.add(d);
        }
        return out;
    }

    // ------------------------------------------------------------------ the Shop (v0.3: replaces the trader screens)
    /** 0.7: four departments. Ammunition, grenades, food, tools and crafting stock are all Supplies. */
    public static final String[] SHOP_CATEGORIES = {"Weapons", "Medical", "Supplies", "Armor"};
    public static final int WEAPONS = 0, MEDICAL = 1, SUPPLIES = 2, ARMOR = 3;

    /** Shop department of a definition, or -1 when the shop does not stock it (loot, treasures, attachments, quest goods). */
    public static int shopCategory(ItemDef d) {
        // Mythic things are found, not bought - except the 0.8 equipment, whose Mythic set is sold at Rank 40 like the rest of it
        boolean gear08 = ("armor".equals(d.category) || "backpack".equals(d.category)) && d.rank > 1;
        if (d.questItem || d.value <= 0 || d.tier >= 5 && !gear08 || d.attachment != null) return -1;
        if (d.id.startsWith("food.egg")) return -1;                // 1.1: sold to the shop, never by it
        switch (d.category) {
            case "weapon": return WEAPONS;
            case "medical": return MEDICAL;
            // 1.1: ammunition is not for sale anywhere. What the shop sells instead is the powder to load it with (the three
            // grades of gunpowder, under Supplies); cartridges are found, taken off the dead, or made at the bench.
            case "ammo": return -1;
            case "explosive": case "food": case "tool": return SUPPLIES;
            case "armor": case "backpack": return ARMOR;
            case "material": return d.id.equals("mat.gun_oil") || d.id.equals("kit.repair") || d.id.startsWith("mat.gunpowder") || d.id.equals("mat.scrap_small") || d.id.equals("mat.plank") || d.id.equals("mat.grenade_casing") ? SUPPLIES : -1;
            default: return -1;
        }
    }

    /** 0.7: what the shop puts on the wall for this customer - a weapon the Rank has not opened yet is not shown at all. */
    public static boolean shopShows(ItemDef d, Progression p) {
        if (shopCategory(d) < 0) return false;
        // 0.8: equipment with a Rank on it is on the wall once that Rank is held
        if (d.weapon == null && d.rank > 1 && p != null && p.bestRank < d.rank) return false;
        return d.weapon == null || p == null || p.platformUnlocked(d.id);
    }

    /** List price in cash. Weapons and grenades cost exactly their published price. */
    public static int shopBuy(ItemDef d) { return Math.max(1, d.value); }

    /**
     * What the shop pays for one instance (a whole stack). Gems and treasures fetch their full worth;
     * everything else half its list price scaled by condition - always below the buy price, so no
     * purchase can be sold back at a profit.
     */
    public static int shopSell(ItemInstance it) {
        ItemDef d = it.def();
        if (it.fallback || d.value <= 0 || d.questItem) return 0;
        boolean prize = "treasure".equals(d.category) || "valuable".equals(d.category);
        float v = it.baseValueTotal() * (prize ? 1f : 0.5f);
        return Math.max(prize ? 1 : 0, Math.round(v));
    }

    /** Free fallback kit (GDD 4.4): offered when the survivor cannot afford to deploy. */
    public static List<ItemInstance> fallbackKit() {
        List<ItemInstance> l = new ArrayList<>();
        ItemInstance pistol = new ItemInstance("weapon.pm9");
        pistol.rounds = pistol.capacity(); pistol.ammoId = "ammo.handgun";
        ItemInstance spare = new ItemInstance("ammo.handgun");
        spare.qty = 20;
        ItemInstance knife = new ItemInstance("melee.knife");
        ItemInstance herb = new ItemInstance("herb.green");
        ItemInstance water = new ItemInstance("drink.water");
        for (ItemInstance i : List.of(pistol, spare, knife, herb, water)) { i.fallback = true; i.origin = "fallback"; }
        l.add(pistol); l.add(spare); l.add(knife); l.add(herb); l.add(water);
        return l;
    }

    /** Audit: proves no buy->sell round trip is profitable for any stocked item (anti-exploit). */
    public static String audit(ZoneState z, Map<String, Float> reps) {
        StringBuilder b = new StringBuilder();
        int worst = Integer.MIN_VALUE;
        String worstId = "";
        for (Trader t : TRADERS) for (ItemDef d : Content.ITEMS.values()) {
            if (!accepts(t, d) || d.value <= 0) continue;
            for (Trader s : TRADERS) {
                if (!accepts(s, d)) continue;
                float rep = reps.getOrDefault(t.faction, 0f);
                int buy = buyPrice(d, t, z, rep);
                ItemInstance probe = new ItemInstance(-77, d.id);
                probe.qty = 1;
                int sell = sellPrice(probe, s, z, 100, 0);
                if (sell - buy > worst) { worst = sell - buy; worstId = d.id + " (" + t.id + "->" + s.id + ")"; }
            }
        }
        b.append("Best buy->sell margin: ").append(worst).append(" cash on ").append(worstId).append(worst < 0 ? "  [OK: no loop]" : "  [EXPLOIT]");
        return b.toString();
    }
}
