package absentialis.scene;

import absentialis.gameplay.economy.Economy;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.gameplay.missions.Contracts;
import absentialis.persistence.Profile;
import absentialis.sim.ZoneState;
import absentialis.world.District;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies a raid's {@link Raid.Debrief} to the profile exactly once (GDD 4.4 / 22.5): equipment
 * loss on death, XP, reputation, contract payouts and the strategic advance, then lists the
 * visible consequences so the player can see how one raid changed several systems.
 */
public final class RaidResolver {
    private RaidResolver() {}

    /** What each thing done on an operation is worth in experience (the debrief pays these; scene.XpFeed shows them as they are earned). */
    public static final int XP_CONTAINER = 40, XP_CELL = 15, XP_DOCUMENT = 300, XP_AID = 400, XP_POWER = 4000, XP_PER_MINUTE = 120, XP_EXTRACT = 2500;
    /** 0.9: a silent takedown is the one kill that pays - it is a thing done well, not a thing farmed: each is worth this, and only the first so many of an operation count. */
    public static final int XP_STEALTH_KILL = 250, XP_STEALTH_CAP = 12;
    /** Seconds of an operation that still earn experience for simply being there (0.7: operations have no time limit). */
    public static final float XP_TIME_CAP = 2700f;
    /** The Rank held before and after the last operation resolved: what the hideout's unlock notice reads. */
    public static int lastRankBefore = 1, lastRankAfter = 1;

    public static List<String> resolve(Raid raid) {
        Raid.Debrief d = raid.debrief;
        Profile p = raid.profile;
        List<String> out = new ArrayList<>();
        if (d.resolved || d.outcome == Raid.Debrief.Outcome.NONE) return out;
        d.resolved = true;
        boolean survived = d.outcome == Raid.Debrief.Outcome.EXTRACTED;
        // 0.7: calling the operation off is not dying. The player walks out with everything they were carrying; what
        // they give up is what an extraction pays - the extraction bonus, contract completion, the full experience.
        boolean abandoned = d.outcome == Raid.Debrief.Outcome.ABANDONED;
        int rankBefore = p.prog.rank();
        String[] ctrlBefore = p.zone.controllers();
        float[] infBefore = p.zone.infected.clone();
        float priceBefore = medicinePrice(p);

        // ---- XP (never from kills alone: exploration, discovery, survival and objectives count)
        float xpMul = 1 + p.loadout.charm("xp");
        // kills pay nothing by themselves (not farmable); what you bring out, find out and finish does
        int lootValue = 0;
        if (survived || abandoned) for (ItemInstance i : p.loadout.all()) if (i.foundInRaid && i.parent == null) lootValue += i.baseValueTotal();
        d.lootValue = lootValue;
        d.xp("High-value loot secured (" + absentialis.Version.coins(lootValue) + ")", Math.min(9000, lootValue / 2));
        d.xp("Aided a loner x" + d.aided, d.aided * XP_AID);
        d.xp("Reconnaissance (" + d.cellsRevealed + " map cells)", d.cellsRevealed * XP_CELL);
        d.xp("Searched " + d.containersSearched + " containers", d.containersSearched * XP_CONTAINER);
        d.xp("Intel recovered (" + d.documents + ")", d.documents * XP_DOCUMENT);
        if (d.powerRestored) d.xp("Restored district power", XP_POWER);
        d.xp("Hotspots settled x" + d.hotspots, d.hotspots * raid.events.xp);
        d.xp("Silent takedowns x" + d.stealthKills, Math.min(d.stealthKills, XP_STEALTH_CAP) * XP_STEALTH_KILL);
        if (survived) {
            d.xp("Extraction: " + d.exitName, XP_EXTRACT);
            // with no clock to leave by, time alone stops paying after forty-five minutes: standing still is not progress
            d.xp("Time in the Zone", Math.round(Math.min(raid.time, XP_TIME_CAP) / 60f * XP_PER_MINUTE));
        }
        // weapon challenges: mastery milestones reached with what you carried
        for (String w : new String[]{"primary", "sidearm", "melee"}) {
            ItemInstance wi = p.loadout.slot(w);
            if (wi == null) continue;
            int lvl = p.prog.masteryLevel(wi.defId), claimed = p.stat("mastery_" + wi.defId);
            if (lvl > claimed) { d.xp("Weapon challenge: " + wi.name() + " mastery " + lvl, (lvl - claimed) * 1500); p.stats.put("mastery_" + wi.defId, lvl); d.challenges += lvl - claimed; }
        }
        if (!p.deployVehicle.isEmpty()) {
            VehicleSystem.Car own = null;
            for (VehicleSystem.Car v : raid.vehicles.cars) if (v.owned) own = v;
            if (own != null) {
                String vn = own.sim.data.name;
                if (abandoned) out.add("Your " + vn + " is back in the garage");
                else if (!survived) { p.garage.remove(p.deployVehicle); p.deployVehicle = ""; out.add("Vehicle lost: your " + vn + " stays in the Zone"); }
                else if (own.sim.damage.engine <= 0) { p.garage.remove(p.deployVehicle); p.deployVehicle = ""; out.add("Vehicle written off: the " + vn + " was wrecked and left behind"); }
                else out.add("Your " + vn + " is back in the garage");
            }
        }
        if (raid.scripPaid > 0) { p.scrip = Math.max(0, p.scrip - raid.scripPaid); out.add("Paid off a bandit hold-up: -" + absentialis.Version.coins(raid.scripPaid)); }
        if (raid.trespasses > 0) out.add("Trespassed in a military restricted area x" + raid.trespasses);
        int raw = d.totalXp();
        int gained = Math.round(raw * xpMul * (survived ? 1f : 0.5f));
        int ranks = p.prog.addXp(gained);
        out.add("+" + gained + " XP" + (survived ? "" : abandoned ? " (halved: operation abandoned)" : " (halved: failed raid)") + (ranks > 0 ? "  -  RANK UP x" + ranks + " -> Rank " + p.prog.rank() : ""));

        // ---- loss / retention
        raid.releaseAll();   // A-Life: everyone still online returns to the offline population where they stand
        // ---- squad: each deployed squadmate earns their own experience (separate from Rank and from weapon levels)
        List<absentialis.gameplay.squad.Roster.Mate> deployed = p.squad.active();
        int home = 0, lostMates = 0;
        for (absentialis.gameplay.squad.Roster.Mate m : deployed) {
            boolean alive = false;
            for (absentialis.entity.Actor a : raid.mates) if (a.mateId == m.id && !a.dead) alive = true;
            // 0.6: dead is dead. A squadmate killed in the Zone is gone with their rank, their record and their kit;
            // the place on the roster stays empty until someone is recruited into it.
            if (raid.mateDeaths.containsKey(m.id)) {
                absentialis.gameplay.squad.Roster.Fallen f = p.squad.bury(m, p.zone.day, raid.mateDeaths.get(m.id));
                if (f != null) { lostMates++; out.add("KILLED IN ACTION: " + f.name() + ", " + f.title() + " (" + f.kills() + " kills, " + f.raids() + " operations) - " + f.cause()); p.stat("mates_lost", 1); }
                continue;
            }
            if (alive) home++;
            long sx = Math.round(Math.min(raid.time, 2700f) / 60f * 400) + raid.mateKills.getOrDefault(m.id, 0) * 600L + (survived && alive ? 6000 : 0);
            if (!survived) sx /= 2;
            String was = m.title();
            if (m.addXp(sx) > 0) out.add(m.name + ": " + was + " -> " + m.title());
        }
        if (!deployed.isEmpty()) out.add("Squad: " + home + " of " + deployed.size() + " walked out" + (lostMates > 0 ? " - " + lostMates + " will not. Recruit replacements at the hideout (SQUAD)." : home < deployed.size() ? " - the wounded are carried back and will be fit for the next operation" : ""));
        if (abandoned) {
            out.add("Operation abandoned: you walked out with everything you were carrying");
            p.stat("abandoned", 1);
            p.energy = raid.body.energy;
            p.hydration = raid.body.hydration;
            for (ItemInstance i : p.loadout.all()) i.foundInRaid = false;
        } else if (!survived) {
            // Weapon Prestige death protection: the prestiged weapon itself comes home - the one instance, once -
            // without its attachments or its ammunition. Everything else carried is lost as usual.
            List<ItemInstance> guarded = new ArrayList<>();
            for (ItemInstance i : p.loadout.all()) if (i.parent == null && i.def().weapon != null && !i.def().weapon.melee() && p.prog.deathProtected(i.defId)) guarded.add(i);
            // 0.8: what the survivor chose to keep - five things on Easy, three on Normal, nothing on Hard. Anything carried
            // can be chosen: a weapon with everything fitted to it, the armour, a ring. With no choice made (the game was
            // quit from the death screen) the most valuable things are kept for them.
            int keepN = p.keepOnDeath();
            List<ItemInstance> carried = new ArrayList<>();
            for (ItemInstance i : p.loadout.all()) if (i.parent == null) carried.add(i);
            List<ItemInstance> keeping = new ArrayList<>();
            if (keepN > 0) {
                // in the order they were chosen: the first few ticks are the ones honoured
                for (long uid : d.keep) for (ItemInstance i : carried) if (i.uid == uid && keeping.size() < keepN && !guarded.contains(i) && !keeping.contains(i)) keeping.add(i);
                if (d.keep.isEmpty()) {
                    List<ItemInstance> byValue = new ArrayList<>(carried);
                    byValue.removeAll(guarded);
                    byValue.sort((a, b) -> Integer.compare(b.baseValueTotal(), a.baseValueTotal()));
                    for (ItemInstance i : byValue) if (keeping.size() < keepN) keeping.add(i);
                }
            }
            List<ItemInstance> lost = p.loadout.strip(false);
            for (ItemInstance k : keeping) {
                if (!lost.remove(k)) continue;
                k.foundInRaid = false;
                while (!InventoryService.add(k, p.stash).ok()) p.stash.h += 2;
                d.kept.add(k.name());
            }
            if (keepN > 0) out.add(d.kept.isEmpty() ? "Nothing was carried that could be kept" : "Kept (" + d.kept.size() + " of " + keepN + "): " + String.join(", ", d.kept) + " - in the stash");
            for (ItemInstance g : guarded) {
                if (!lost.remove(g)) InventoryService.release(g);
                for (String sck : new ArrayList<>(g.attachments.keySet())) absentialis.gameplay.weapons.Gunsmith.remove(g, sck);
                g.rounds = 0; g.ammoId = null; g.spill = 0; g.foundInRaid = false; g.jammed = false;
                while (!InventoryService.add(g, p.stash).ok()) p.stash.h += 2;
                out.add("Weapon Prestige: your " + g.name() + " was recovered to the stash (attachments and ammunition lost)");
            }
            // keys are profile-bound information and survive; everything carried is lost once
            int value = 0;
            for (ItemInstance i : lost) value += i.baseValueTotal();
            out.add("Lost " + lost.size() + " carried items (~" + absentialis.Version.coins(value) + " of gear)" + (keepN == 0 ? " - Hard keeps nothing" : ""));
            // and a share of the cash, by difficulty
            long cash = Math.round(p.scrip * (double) p.cashLostOnDeath());
            if (cash > 0) { p.scrip -= cash; d.cashLost = cash; out.add("Lost " + absentialis.Version.coins(cash) + " (" + Math.round(p.cashLostOnDeath() * 100) + "% of your cash, " + Profile.DIFFICULTY[p.difficulty] + ")"); }
            p.stat("deaths", 1);
            p.energy = 55;
            p.hydration = 55;
        } else {
            p.stat("extracts", 1);
            p.energy = raid.body.energy;
            p.hydration = raid.body.hydration;
            if (d.emergencyCost > 0) { p.scrip = Math.max(0, p.scrip - d.emergencyCost); out.add("Cordon toll paid: -" + absentialis.Version.coins(d.emergencyCost)); }
            if (d.emergencyCost < 0) { int n = p.loadout.treasures.size(); p.loadout.treasures.clear(); out.add("Cordon guards confiscated " + n + " treasures"); }
            p.contracts.onExtract(d.deployedIn);
            for (ItemInstance i : p.loadout.all()) i.foundInRaid = false;
        }
        // 0.7: the paid TREATMENT at the hideout is gone, and with it the idea of going out half-healed because you
        // could not afford the medic. Whoever makes it back - on their feet or carried - is rested and whole for the next one.
        p.health = 100;
        p.stat("raids", 1);
        p.stat("cells", d.cellsRevealed);
        p.stat("containers", d.containersSearched);
        p.stat("documents", d.documents);
        if (d.powerRestored) p.stat("power", 1);
        int infectedKills = 0;
        for (var e : d.kills.entrySet()) if (!java.util.List.of(ZoneState.FACTIONS).contains(e.getKey())) infectedKills += e.getValue();
        p.stat("kills_infected", infectedKills);
        for (var e : d.kills.entrySet()) p.stat("kills_" + e.getKey(), e.getValue());

        // ---- reputation (observed evidence only; accumulated during the raid)
        for (var e : d.repDelta.entrySet()) {
            p.addRep(e.getKey(), e.getValue());
            if (Math.abs(e.getValue()) >= 0.5f) out.add(String.format("%s standing %+.1f (now %.0f)", ZoneState.cap(e.getKey()), e.getValue(), p.rep(e.getKey())));
        }

        // ---- contracts (pay only on extraction)
        if (survived) for (Contracts.Contract c : new ArrayList<>(p.contracts.active)) {
            if (c.kind == Contracts.Kind.FETCH) {
                int have = p.loadout.count(c.target) + countStash(p, c.target);
                if (have < c.need) continue;
                takeItems(p, c.target, c.need);
                c.have = c.need;
            }
            if (!c.done()) continue;
            p.contracts.complete(c);
            d.contractsDone++;
            p.scrip += c.scrip;
            p.prog.addXp(c.xp);
            p.addRep(c.giver, c.rep);
            out.add("Contract complete: " + c.title + "  +" + absentialis.Version.coins(c.scrip) + ", +" + c.xp + " XP, " + ZoneState.cap(c.giver) + " +" + (int) c.rep);
            if (c.kind == Contracts.Kind.CULL && !c.anywhere()) p.zone.infected[District.valueOf(c.district).ordinal()] *= 0.8f;
        }
        p.contracts.operationDone();
        p.contracts.refill(p.seed ^ p.stat("raids"), p.prog.rank(), p.alliance);
        // ---- 0.8, the investigations: what was seen out there stays seen whatever happened; getting out alive makes it yours to decide
        for (String[] n : p.missions.drain()) out.add("STORY: " + n[2]);
        if (survived || abandoned) for (absentialis.gameplay.missions.Story.Mission sm : p.missions.onExtract(p.zone.day)) { out.add("STORY: " + sm.title + " - you are out, and you know. Decide who is told (CONTRACTS tab)."); d.missionsProven++; }
        p.missions.drain();

        // ---- the groups: what this operation meant to them, then their own business while the player was away
        raid.rivalry.finish(survived);
        absentialis.rivalry.RivalryEngine re = raid.rivalry.engine;
        int seenNotices = re.s.notices.size();
        int rticks = Math.max(1, Math.round(Math.max(1, raid.time / 60f * 0.5f) / Math.max(1f, absentialis.rivalry.Doctrine.world("hoursPerTick", 6))));
        List<String> rnews = re.tick(rticks, absentialis.rivalry.RivalryWorld.wallet(p));
        for (int i = Math.min(seenNotices, re.s.notices.size()); i < re.s.notices.size(); i++) out.add(re.s.notices.get(i));
        for (int i = Math.max(0, rnews.size() - 4); i < rnews.size(); i++) out.add("WORD: " + rnews.get(i));
        // ---- strategic advance: the world moves on while you were out
        int hours = Math.max(1, Math.round(raid.time / 60f * 0.5f));
        p.zone.advance(hours, p.seed);
        p.zone.announceFlips(ctrlBefore);
        for (District dd : District.CITY) {
            float delta = p.zone.infected[dd.ordinal()] - infBefore[dd.ordinal()];
            if (delta > 0.04f) out.add("Infected are massing in the " + dd.display + " (+" + Math.round(delta * 100) + "% pressure)");
            if (delta < -0.04f) out.add("Infected pressure eased in the " + dd.display + " (" + Math.round(delta * 100) + "%)");
        }
        for (String f : d.killedFactions.stream().distinct().toList()) {
            String c = p.zone.controller(d.deployedIn);
            out.add(ZoneState.cap(f) + " hold on the " + d.deployedIn.display + " weakened (" + Math.round(p.zone.control(d.deployedIn, f) * 100) + "% influence)" + (c != null && !c.equals(f) ? " - " + ZoneState.cap(c) + " now dominant" : ""));
        }
        float priceAfter = medicinePrice(p);
        if (Math.abs(priceAfter - priceBefore) >= 1) out.add(String.format("Market reacts: medicine prices %s (bandage $%d -> $%d)", priceAfter > priceBefore ? "up" : "down", Math.round(priceBefore), Math.round(priceAfter)));
        if (d.powerRestored) out.add("Lights are back on: survivors trade more freely and the pumping station tunnel is live");
        for (String n : p.zone.news.subList(0, Math.min(2, p.zone.news.size()))) out.add("NEWS: " + n);
        // sales saturation decays between raids (anti-exploit memory)
        p.recentSales.replaceAll((k, v) -> Math.max(0, v - 2));
        p.recentSales.values().removeIf(v -> v == 0);
        p.applyCapacity();
        lastRankBefore = rankBefore; lastRankAfter = p.prog.rank();
        d.consequences.addAll(out);
        p.lastDebrief = String.join("\n", out);
        return out;
    }

    private static float medicinePrice(Profile p) {
        ItemDef b = absentialis.gameplay.items.Content.def("med.bandage");
        return Economy.buyPrice(b, Economy.trader("mara"), p.zone, p.rep("survivors"));
    }

    private static int countStash(Profile p, String id) { int n = 0; for (ItemInstance i : p.stash.items) if (i.defId.equals(id)) n += i.qty; return n; }

    private static void takeItems(Profile p, String id, int n) {
        List<ItemInstance> pool = new ArrayList<>();
        for (ItemInstance i : p.loadout.all()) if (i.defId.equals(id) && i.parent == null) pool.add(i);
        for (ItemInstance i : p.stash.items) if (i.defId.equals(id)) pool.add(i);
        for (ItemInstance i : pool) {
            if (n <= 0) break;
            int k = Math.min(n, i.qty);
            InventoryService.consume(i, k);
            n -= k;
        }
    }
}
