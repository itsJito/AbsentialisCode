package absentialis.tools;

import absentialis.engine.util.Json;
import absentialis.rivalry.Doctrine;
import absentialis.rivalry.RivalryEngine;
import absentialis.rivalry.RivalryState;
import absentialis.rivalry.RivalryState.Group;
import absentialis.rivalry.RivalryState.Leader;
import absentialis.rivalry.RivalryState.Memory;
import absentialis.rivalry.RivalryState.Treaty;
import absentialis.rivalry.RivalryState.WorldEvent;
import absentialis.rivalry.RivalryWorld;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Group Rivalry System's acceptance tests (0.6 brief, rivalry section 10), as seeded simulations: each builds a
 * small world, does to it what the test describes, and checks what the engine made of it. The same seed gives the
 * same world every run. Filter: {@code ABS_SELFTEST=rivalry}.
 */
final class RivalryTests {
    private RivalryTests() {}

    private static final String P = RivalryState.PLAYER;

    /** A purse that never runs dry (the tests are about relationships, not prices) and counts what it paid. */
    private static final class Purse implements RivalryEngine.Wallet {
        long paid, got, have = 1_000_000;
        public long coins() { return have; }
        public boolean pay(int a) { if (have < a) return false; have -= a; paid += a; return true; }
        public void receive(int a) { have += a; got += a; }
    }

    /** One group per faction, all on the same ground: the MVP world. */
    private static RivalryEngine four(long seed) {
        RivalryState s = new RivalryState();
        s.seed = seed;
        RivalryEngine e = new RivalryEngine(s, RivalryWorld.DISTRICTS);
        for (String f : Doctrine.FACTIONS) e.createGroup(f, "OLD_QUARTER");
        s.seeded = true;
        return e;
    }

    private static RivalryEngine world(long seed) {
        RivalryEngine e = new RivalryEngine(new RivalryState(), RivalryWorld.DISTRICTS);
        e.ensureSeeded(seed);
        return e;
    }

    private static Group of(RivalryEngine e, String faction, int index) { return e.s.ofFaction(faction).get(index); }

    /** Something good done for a group in front of it and its leader. */
    private static void good(RivalryEngine e, Group g, String type) { e.emit(type, P, g.id, g.homeDistrict, "", "", List.of(g.leaderId), List.of(g.id, g.leaderId)); }

    /** Earns a group's favor and its leader's trust by deeds, the way a player would, until an alliance is possible. Returns deeds done. */
    private static int earn(RivalryEngine e, Group g) {
        String[] deeds = {"RESCUE", "COMPLETE_JOB", "DEFEND_TERRITORY", "HEAL", "GIFT", "INTEL", "TRADE"};
        int n = 0;
        while (n < 400 && (g.playerRelation.favor < Doctrine.alliance("favor", 75) || e.s.leaderOf(g).playerRelation.trust < Doctrine.alliance("trust", 40))) { good(e, g, deeds[n % deeds.length]); n++; if (n % 7 == 0) e.s.tick += 8; }
        return n;
    }

    private static String top(Group g) { String best = ""; float b = -99; for (var en : g.lastUtilities.entrySet()) if (en.getValue() > b) { b = en.getValue(); best = en.getKey(); } return best.contains(" ") ? best.substring(0, best.indexOf(' ')) : best; }

    static void run(String which, boolean all, SelfTests04.Check t) {
        if (!(all || which.contains("v06") || which.contains("rivalry"))) return;
        System.out.println("[v0.6 rivalry: groups, leaders, events, bounded knowledge, memory, doctrine, succession, treaties, off-screen strategy]");

        // ---- 1. four groups, one per faction: four unique leaders, visibly different priorities in the same situation
        {
            RivalryEngine e = four(101);
            Set<String> names = new HashSet<>(), ids = new HashSet<>(), tops = new HashSet<>(), profiles = new HashSet<>();
            StringBuilder sb = new StringBuilder();
            List<Group> gs = e.s.active();
            for (Group g : gs) {
                // the same situation for all four: same numbers, same stores, one angry neighbour each
                g.size = g.startSize = 30; g.morale = 0.7f; g.cohesion = 0.7f;
                for (String r : new String[]{"supplies", "ammo", "coins", "gear", "medicine", "intel"}) g.resources.put(r, 10f);
            }
            for (int i = 0; i < gs.size(); i++) { Group a = gs.get(i), b = gs.get((i + 1) % gs.size()); e.rel(a, b.id).hostility = 55; e.rel(b, a.id).hostility = 55; }
            for (Group g : gs) {
                Leader l = e.s.leaderOf(g);
                names.add(l.name); ids.add(l.id);
                e.decide(g);
                tops.add(top(g));
                StringBuilder w = new StringBuilder();
                for (String c : Doctrine.considerations()) w.append(Math.round(g.doctrineState.get("w_" + c) * 10)).append(',');
                profiles.add(w.toString());
                sb.append(Doctrine.display(g.faction)).append(": ").append(l.title).append(' ').append(l.name).append(' ').append(l.traits).append(" -> ").append(top(g)).append("; ");
            }
            t.check("four groups, one per faction, have four unique leaders and different priorities in the same situation", gs.size() == 4 && names.size() == 4 && ids.size() == 4 && profiles.size() == 4 && tops.size() >= 3, sb.toString().trim());
            // a leader bends the doctrine inside the faction's range and never out of it
            boolean bounded = true;
            for (Group g : gs) for (String c : Doctrine.considerations()) {
                float base = Doctrine.weight(g.faction, c), w = g.doctrineState.get("w_" + c), range = Doctrine.leaderRange(g.faction);
                if (base > 0 && (w < base * (1 - range) - 1e-4f || w > base * (1 + range) + 1e-4f)) bounded = false;
                if (base == 0 && w != 0) bounded = false;
            }
            t.check("leader traits modify doctrine weights only within the faction's bounds (a leader bends the doctrine, never erases it)", bounded, "");
        }

        // ---- 2. a defeated leader who survives remembers the player, changes dialogue and behaviour, and is the same entity after save/load
        {
            RivalryEngine e = four(202);
            Group g = of(e, "bandits", 0);
            Leader l = e.s.leaderOf(g);
            String before = e.line(l, g);
            int d0 = e.disposition(g);
            e.emit("DEFEAT", P, g.id, g.homeDistrict, "explosive", "driven off", List.of(l.id), List.of(g.id, l.id));
            e.emit("DEFEAT", P, g.id, g.homeDistrict, "explosive", "driven off again", List.of(l.id), List.of(g.id, l.id));
            String after = e.line(l, g);
            boolean remembers = false;
            for (Memory m : l.personalMemories) if ("DEFEAT".equals(m.type) && P.equals(m.subject) && m.known()) remembers = true;
            String json = Json.write(e.s.toJson());
            RivalryState s2 = RivalryState.fromJson(Json.parseObject(json));
            RivalryEngine e2 = new RivalryEngine(s2, RivalryWorld.DISTRICTS);
            Leader l2 = s2.leader(l.id);
            Group g2 = s2.group(g.id);
            boolean same = l2 != null && l2.name.equals(l.name) && l2.traits.equals(l.traits) && l2.injuries.equals(l.injuries) && l2.defeats == l.defeats && l2.personalMemories.size() == l.personalMemories.size()
                    && Math.abs(l2.playerRelation.hostility - l.playerRelation.hostility) < 1e-3f && e2.line(l2, g2).equals(after) && Json.write(s2.toJson()).equals(json);
            float r1 = e.s.roll(), r2 = s2.roll();
            t.check("a defeated leader who survives remembers the player and says so; it is the same person after save and load", remembers && !after.equals(before) && l.defeats == 2 && e.disposition(g) <= d0 && g.playerRelation.fear > 0 && same && r1 == r2,
                    "before: \"" + before + "\"  after: \"" + after + "\"  defeats " + l.defeats + ", injuries " + l.injuries + ", fear " + Math.round(g.playerRelation.fear) + ", saved state identical=" + same + ", dice continue=" + (r1 == r2));
        }

        // ---- 3. a witnessed rescue raises group favor and leader trust; what nobody saw informs nobody, and distant groups learn late or never
        {
            RivalryEngine e = world(303);
            Group g = of(e, "loners", 0), far = of(e, "loners", 1), other = of(e, "government", 0);
            float f0 = g.playerRelation.favor, t0 = e.s.leaderOf(g).playerRelation.trust;
            WorldEvent rescue = e.emit("RESCUE", P, g.id, g.homeDistrict, "", "carried out a wounded man", List.of(), List.of(g.id));
            boolean rose = g.playerRelation.favor > f0 + 5 && e.s.leaderOf(g).playerRelation.trust > t0 + 2;
            boolean nobodyElse = true;
            for (Group o : e.s.active()) if (o != g) for (Memory m : o.groupMemories) if (m.eventId.equals(rescue.id)) nobodyElse = false;
            float of0 = other.playerRelation.favor;
            WorldEvent quiet = e.emit("KILL_MEMBER", P, other.id, other.homeDistrict, "stealth", "nobody saw", List.of(other.id + "#7"), List.of());
            boolean unseen = Math.abs(other.playerRelation.favor - of0) < 1e-4f && other.playerRelation.hostility == 0;
            boolean lossOnly = false;
            for (Memory m : other.groupMemories) if (m.eventId.equals(quiet.id)) lossOnly = "LOSS_UNKNOWN".equals(m.type) && m.subject.isEmpty();
            float ff0 = far.playerRelation.favor;
            e.tick(4, null);
            Memory heard = null;
            for (Memory m : far.groupMemories) if (m.eventId.equals(rescue.id)) heard = m;
            t.check("a witnessed rescue raises group favor and leader trust; no other group knows of it at once", rose && nobodyElse, String.format("favor %.0f -> %.0f, leader trust %.0f -> %.0f", f0, g.playerRelation.favor, t0, e.s.leaderOf(g).playerRelation.trust));
            t.check("an unwitnessed killing changes nobody's opinion: the group knows it lost someone, not to whom", unseen && lossOnly, "favor unchanged=" + unseen + ", remembered as a loss by an unknown hand=" + lossOnly);
            t.check("word reaches a distant group of the same faction later, as rumour, at lower confidence", heard != null && !heard.known() && heard.confidence < 1 && far.playerRelation.favor >= ff0, heard == null ? "never heard" : heard.knowledge + " at confidence " + heard.confidence + " via " + heard.sourceId);
        }

        // ---- 4. alliance eligibility and a treaty with each of the four factions: no faction is locked out
        {
            RivalryEngine e = four(404);
            Purse w = new Purse();
            StringBuilder sb = new StringBuilder();
            int signed = 0;
            Set<String> termSets = new HashSet<>();
            for (String f : Doctrine.FACTIONS) {
                Group g = of(e, f, 0);
                RivalryEngine.Result early = e.alliance(g, w);
                int deeds = earn(e, g);
                RivalryEngine.Result r = e.alliance(g, w);
                Treaty tr = e.s.treatyWith(g.id, "alliance");
                if (!early.ok() && r.ok() && tr != null && tr.active() && !tr.terms.isEmpty() && !tr.obligations.isEmpty()) signed++;
                if (tr != null) termSets.add(String.join("|", tr.terms));
                sb.append(Doctrine.display(f)).append(": ").append(deeds).append(" deeds, ").append(r.ok() ? "signed" : r.text()).append("; ");
            }
            t.check("the player can earn alliance eligibility and sign a treaty with each of the four factions (and hold all four)", signed == 4 && termSets.size() == 4, sb.toString().trim());
        }

        // ---- 5. hostile, reconciled, then betrayed: with consequences that persist
        {
            RivalryEngine e = four(505);
            Purse w = new Purse();
            Group g = of(e, "mercenaries", 0);
            for (int i = 0; i < 4; i++) e.emit("KILL_MEMBER", P, g.id, g.homeDistrict, "", "", List.of(g.id + "#" + i), List.of(g.id));
            String hostile = e.status(g);
            int dHostile = e.disposition(g);
            // reconciliation by deeds: paying, treating their wounded, doing their work
            e.bribe(g, w);
            int deeds = 0;
            while (deeds < 200 && g.playerRelation.favor < 20) { good(e, g, new String[]{"HEAL", "COMPLETE_JOB", "RESCUE", "GIFT"}[deeds % 4]); deeds++; if (deeds % 5 == 0) e.s.tick += 8; }
            String reconciled = e.status(g);
            earn(e, g);
            boolean allied = e.alliance(g, w).ok();
            float favAllied = g.playerRelation.favor;
            RivalryEngine.Result b = e.betray(g);
            Treaty tr = null;
            for (Treaty x : e.s.treaties.values()) if (x.groupId.equals(g.id) && "alliance".equals(x.kind)) tr = x;
            RivalryState s2 = RivalryState.fromJson(Json.parseObject(Json.write(e.s.toJson())));
            RivalryEngine e2 = new RivalryEngine(s2, RivalryWorld.DISTRICTS);
            List<String> blockers = e2.allianceBlockers(s2.group(g.id));
            boolean remembered = false;
            for (String why : blockers) if (why.contains("betrayed")) remembered = true;
            t.check("a group can turn hostile, be reconciled by deeds, be allied with and then betrayed - and the betrayal outlives a save", (hostile.equals("Hostile") || hostile.equals("Hated")) && dHostile < 0 && !reconciled.equals(hostile) && allied && b.ok()
                            && tr != null && "broken".equals(tr.status) && P.equals(tr.brokenBy) && g.playerRelation.favor < favAllied - 30 && remembered && s2.group(g.id).doctrineState.getOrDefault("hunt_player", 0f) > 0,
                    hostile + " -> " + reconciled + " after " + deeds + " deeds -> allied (" + Math.round(favAllied) + ") -> betrayed (" + Math.round(g.playerRelation.favor) + "); after load: " + blockers);
        }

        // ---- 6. killing a leader: valid succession; history, favor and treaties stay; the successor is their own person
        {
            RivalryEngine e = four(606);
            Purse w = new Purse();
            Group g = of(e, "government", 0), rival = of(e, "bandits", 0);
            Leader old = e.s.leaderOf(g);
            good(e, g, "RESCUE"); good(e, g, "DEFEND_TERRITORY");
            e.truce(g, w);
            // something only the old leader holds: the player once let them live
            e.emit("SPARE", P, g.id, g.homeDistrict, "", "let the colonel walk", List.of(old.id), List.of(g.id, old.id));
            float fav = g.playerRelation.favor;
            String name = g.name;
            int memories = g.groupMemories.size(), hist = g.history.size();
            e.emit("KILL_LEADER", rival.id, g.id, g.homeDistrict, "ambush", "killed on the road", List.of(old.id), List.of(g.id));
            e.tick(1, w);
            Leader now = e.s.leaderOf(g);
            Treaty tr = e.s.treatyWith(g.id, "truce");
            boolean spareInherited = now != null && now.personalMemoriesHave("spared_me");
            boolean groupKeeps = false;
            for (Memory m : g.groupMemories) if ("SPARE".equals(m.type)) groupKeeps = true;
            boolean one = true;
            for (Group x : e.s.active()) { Leader xl = e.s.leaderOf(x); if (xl == null || !xl.alive) one = false; }
            t.check("killing a leader triggers a valid succession: the group, its history, its favor and its treaties remain", now != null && now != old && !old.alive && now.alive && g.leaderId.equals(now.id) && g.name.equals(name) && g.formerLeaderIds.contains(old.id)
                            && Math.abs(g.playerRelation.favor - fav) < 4 && tr != null && tr.active() && g.groupMemories.size() >= memories && g.history.size() > hist && one,
                    old.title + " " + old.name + " " + old.traits + " -> " + (now == null ? "nobody" : now.title + " " + now.name + " " + now.traits) + "; favor " + Math.round(fav) + " -> " + Math.round(g.playerRelation.favor) + "; truce " + (tr == null ? "lost" : tr.status));
            t.check("the successor has their own traits and none of the predecessor's personal memories; the group's memory is intact", now != null && !spareInherited && groupKeeps && now.traits != old.traits && !now.id.equals(old.id),
                    "predecessor's 'spared me' memory inherited=" + spareInherited + ", the group still remembers the sparing=" + groupKeeps);
        }

        // ---- 7. off-screen groups pursue objectives and generate auditable events without any combat actors; a seed reproduces
        {
            RivalryEngine a = world(707), b = world(707), c = world(708);
            List<String> news = a.tick(40, null);
            b.tick(40, null); c.tick(40, null);
            int fights = 0, audited = 0, decided = 0;
            Set<String> actions = new HashSet<>();
            for (WorldEvent ev : a.s.log) if ("GROUP_RAID".equals(ev.type)) { fights++; if (ev.outcome.contains("strength") && ev.outcome.contains("roll") || ev.outcome.contains("paid") || ev.outcome.contains("refused")) audited++; }
            for (Group g : a.s.groups.values()) { if (!g.lastUtilities.isEmpty()) decided++; for (String h : g.history) actions.add(h.replaceAll("^t\\d+ ", "").split(" ")[0]); }
            StringBuilder la = new StringBuilder(), lb = new StringBuilder(), lc = new StringBuilder();
            for (WorldEvent ev : a.s.log) la.append(ev).append('\n');
            for (WorldEvent ev : b.s.log) lb.append(ev).append('\n');
            for (WorldEvent ev : c.s.log) lc.append(ev).append('\n');
            t.check("off-screen groups act on their objectives and every clash is an event with its arithmetic on record", fights > 0 && audited == fights && decided == a.s.active().size() && a.s.log.size() > 10,
                    a.s.log.size() + " events in 40 ticks, " + fights + " clashes (all with their working shown), " + news.size() + " lines of news; kinds of thing done: " + actions);
            t.check("the simulation is deterministic for a seed (and different for another)", la.toString().equals(lb.toString()) && !la.toString().equals(lc.toString()), a.s.log.size() + " events reproduced exactly; seed 708 differs");
        }

        // ---- 8. adaptations need recorded experience and resources; exploits cannot push anything past its bounds
        {
            RivalryEngine e = four(808);
            Group g = of(e, "mercenaries", 0), blind = of(e, "government", 0);
            g.resources.put("gear", 0f);
            for (int i = 0; i < 3; i++) e.emit("DEFEAT", P, g.id, g.homeDistrict, "explosive", "", List.of(), List.of(g.id));
            int poor = g.adapted("blast_discipline");
            boolean wanted = false;
            for (String h : g.history) if (h.contains("cannot pay")) wanted = true;
            g.resources.put("gear", 40f);
            for (int i = 0; i < 60; i++) { e.emit("DEFEAT", P, g.id, g.homeDistrict, "explosive", "", List.of(), List.of(g.id)); e.s.tick += 3; }
            int capped = g.adapted("blast_discipline");
            boolean within = true;
            for (Object o : Doctrine.adaptations()) { @SuppressWarnings("unchecked") var rule = (java.util.Map<String, Object>) o; if (g.adapted(Json.str(rule, "id", "")) > Json.integer(rule, "max", 1)) within = false; }
            // the same thing done where nobody of theirs sees it teaches them nothing
            blind.resources.put("gear", 40f);
            for (int i = 0; i < 8; i++) e.emit("KILL_MEMBER", P, blind.id, blind.homeDistrict, "headshot", "", List.of(blind.id + "#" + i), List.of());
            for (int i = 0; i < 300; i++) e.emit("ATTACK", P, g.id, g.homeDistrict, "", "", List.of(), List.of(g.id));
            var r = g.playerRelation;
            boolean bounded = r.favor >= -100 && r.hostility <= 100 && r.fear <= 100 && r.trust >= -100 && g.groupMemories.size() <= Math.round(Doctrine.world("memoryCap", 40));
            t.check("adaptations require known experience and the resources to pay for them", poor == 0 && wanted && capped >= 1 && blind.adapted("helmets") == 0, "without gear: level " + poor + " (wanted it: " + wanted + "); with gear: level " + capped + "; eight unseen head shots taught the other group: " + blind.adapted("helmets"));
            t.check("repeated exploits cannot produce unbounded values: adaptation levels, relationships and memory all stay capped", capped <= 2 && within && bounded, "blast discipline " + capped + "/2, favor " + Math.round(r.favor) + ", hostility " + Math.round(r.hostility) + ", " + g.groupMemories.size() + " memories after 360 events");
        }

        // ---- 9. groups of one faction hold different favor, leaders, ground and alliance states
        {
            RivalryEngine e = world(909);
            Purse w = new Purse();
            Group a = of(e, "loners", 0), b = of(e, "loners", 1);
            earn(e, a);
            boolean signed = e.alliance(a, w).ok();
            for (int i = 0; i < 2; i++) e.emit("KILL_MEMBER", P, b.id, b.homeDistrict, "", "", List.of(b.id + "#" + i), List.of(b.id));
            t.check("two groups of the same faction hold different favor, leaders, ground and alliance states", signed && e.treaty(a, "alliance") && !e.treaty(b, "alliance") && a.playerRelation.favor > 70 && b.playerRelation.favor < 0 && !a.leaderId.equals(b.leaderId)
                            && !e.s.leaderOf(a).name.equals(e.s.leaderOf(b).name) && !a.homeDistrict.equals(b.homeDistrict) && !a.name.equals(b.name),
                    a.name + " (" + e.status(a) + " " + Math.round(a.playerRelation.favor) + ", allied, " + RivalryWorld.DISTRICTS.name(a.homeDistrict) + ") / " + b.name + " (" + e.status(b) + " " + Math.round(b.playerRelation.favor) + ", " + RivalryWorld.DISTRICTS.name(b.homeDistrict) + ")");
        }

        // ---- 10. re-encounters respect distance, cooldowns, who is alive and how things stand
        {
            RivalryEngine e = world(1010);
            Group g = of(e, "bandits", 0);
            Leader l = e.s.leaderOf(g);
            for (int i = 0; i < 3; i++) e.emit("DEFEAT", P, g.id, g.homeDistrict, "", "", List.of(l.id), List.of(g.id, l.id));
            String far = null;
            for (String d : RivalryWorld.DISTRICTS.ids()) { boolean ok = true; for (String tdy : g.territoryIds) if (RivalryWorld.DISTRICTS.distance(d, tdy) < 2) ok = false; if (ok) { far = d; break; } }
            boolean home = false, away = false, cooled = false, back = false, dead = false, hunted = false;
            String kind = "";
            for (int k = 0; k < 12; k++) for (RivalryEngine.Encounter en : e.plan(g.homeDistrict, 8)) if (en.leaderId().equals(l.id)) { home = true; kind = en.kind(); }
            if (far != null) for (int k = 0; k < 12; k++) for (RivalryEngine.Encounter en : e.plan(far, 8)) if (en.groupId().equals(g.id)) away = true;
            e.met(l);
            for (int k = 0; k < 12; k++) for (RivalryEngine.Encounter en : e.plan(g.homeDistrict, 8)) if (en.leaderId().equals(l.id)) cooled = true;
            e.s.tick += Math.round(Doctrine.world("encounterCooldownTicks", 3)) + 1;
            for (int k = 0; k < 12; k++) for (RivalryEngine.Encounter en : e.plan(g.homeDistrict, 8)) if (en.leaderId().equals(l.id)) back = true;
            g.doctrineState.put("hunt_player", 6f);
            if (far != null) for (int k = 0; k < 12; k++) for (RivalryEngine.Encounter en : e.plan(far, 8)) if (en.groupId().equals(g.id) && en.kind().equals("hunt")) hunted = true;
            l.alive = false;
            for (int k = 0; k < 12; k++) for (RivalryEngine.Encounter en : e.plan(g.homeDistrict, 8)) if (en.leaderId().equals(l.id)) dead = true;
            t.check("re-encounters respect distance, cooldown, status and relationship", home && !away && !cooled && back && hunted && !dead && (kind.equals("defend") || kind.equals("hunt")),
                    "on their ground: met as '" + kind + "'; two districts away with no reason to travel: " + (away ? "appeared" : "absent") + "; inside the cooldown: " + (cooled ? "appeared" : "absent") + "; after it: " + (back ? "back" : "absent") + "; hunting, far from home: " + (hunted ? "appears" : "absent") + "; dead: " + (dead ? "appeared" : "never"));
        }

        // ---- event idempotency, bounded memory
        {
            RivalryEngine e = four(1111);
            Group g = of(e, "loners", 0);
            WorldEvent ev = e.event("GIFT", P, g.id, g.homeDistrict, "", "", List.of(), List.of(g.id));
            boolean first = e.emit(ev);
            float f1 = g.playerRelation.favor;
            boolean second = e.emit(ev);
            int log = e.s.log.size();
            t.check("events are idempotent: the same event arriving twice changes nothing the second time", first && !second && g.playerRelation.favor == f1 && log == e.s.log.size(), "favor " + f1 + " after both");
            float prev = g.playerRelation.favor;
            float firstGain = 0, lastGain = 0;
            for (int i = 0; i < 30; i++) { float b4 = g.playerRelation.favor; good(e, g, "TRADE"); float d = g.playerRelation.favor - b4; if (i == 0) firstGain = d; lastGain = d; }
            int trades = 0, folded = 0;
            for (Memory m : g.groupMemories) if ("TRADE".equals(m.type)) { trades++; folded += m.count; }
            t.check("small repeated dealings fold into one memory and earn less each time (diminishing returns)", trades == 1 && folded == 30 && lastGain < firstGain * 0.5f && lastGain > 0, String.format("30 trades -> %d memory standing for %d; first +%.2f favor, thirtieth +%.2f", trades, folded, firstGain, lastGain));
        }

        // ---- MVP scope (section 11): the numbers the brief asks the first version to have
        {
            RivalryEngine e = four(1212);
            boolean sizes = true;
            for (Group g : e.s.active()) if (g.size < 10 || g.size > 50) sizes = false;
            int events = Doctrine.eventTypes().size(), rules = Doctrine.adaptations().size(), inter = Doctrine.interactionNames().size(), miles = Doctrine.milestones().size();
            boolean jobs = true;
            for (String f : Doctrine.FACTIONS) if (Json.strList(Doctrine.faction(f), "jobs").isEmpty()) jobs = false;
            t.check("MVP scope: four groups of 10-50, 12+ event types, 8+ adaptation rules, 5+ interactions, group jobs, 3 favor milestones, an alliance treaty", e.s.active().size() == 4 && sizes && events >= 12 && rules >= 8 && inter >= 5 && jobs && miles == 3,
                    e.s.active().size() + " groups, " + events + " event types, " + rules + " adaptation rules, " + inter + " interactions, " + miles + " milestones");
        }
    }
}
