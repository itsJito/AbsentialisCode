package absentialis.scene;

import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.entity.Vital;
import absentialis.rivalry.Doctrine;
import absentialis.rivalry.RivalryEngine;
import absentialis.rivalry.RivalryState;
import absentialis.rivalry.RivalryWorld;
import absentialis.world.District;
import absentialis.world.MapData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where an operation and the Group Rivalry System meet (0.6). Three jobs:
 *
 *   IDENTITY   every faction member brought into the raid belongs to one persistent group; the group's standing
 *              with the player, its current intent and what it has learned decide how that member starts out.
 *   ENCOUNTERS the director's choices become people: a group's leader, with an escort, on ground the group can
 *              plausibly be on - the same individual every time, with what they remember to say.
 *   EVENTS     what the player does in front of a group (or to it) is reported to the rivalry engine as a typed
 *              event with the witnesses there actually were. A killing nobody of theirs saw is reported with none.
 *
 * The raid never reads or writes a relationship directly: it reports what happened and asks how a group stands.
 */
public final class RaidRivalry {
    private final Raid raid;
    public final RivalryEngine engine;
    /** Leader actors in this raid: actor id -> leader id; who has been greeted; who has been reported as attacked or beaten. */
    private final Map<Integer, String> notables = new HashMap<>();
    private final Set<String> met = new HashSet<>(), attacked = new HashSet<>(), defeated = new HashSet<>();
    private final Map<String, Float> defendedAt = new HashMap<>();
    /** How the player has been killing each group's people this raid: group id -> methods seen (for what a defeat teaches). */
    private final Map<String, Set<String>> methods = new HashMap<>();
    private int shown;
    public final List<RivalryEngine.Encounter> planned = new ArrayList<>();
    public int eventsEmitted;

    RaidRivalry(Raid raid) {
        this.raid = raid;
        engine = RivalryWorld.engine(raid.profile);
        shown = engine.s.notices.size();
    }

    private String district(float x, float z) { return raid.world.district(x, z).name(); }

    public RivalryState.Group group(Actor a) { return a == null || a.groupId == null || a.groupId.isEmpty() ? null : engine.s.group(a.groupId); }

    // ------------------------------------------------------------------ identity
    /** A faction member comes into the raid: which group it belongs to, and how that group has it behave toward the player. */
    public void assign(Actor a, long memberId) {
        if (a.infected() || a.isPlayer || a.mate()) return;
        RivalryState.Group g = engine.groupFor(a.faction, district(a.x, a.z), memberId);
        if (g == null) return;
        a.groupId = g.id;
        applyGroup(a, g);
    }

    private void applyGroup(Actor a, RivalryState.Group g) {
        int d = engine.disposition(g);
        // earned favor or a treaty replaces suspicion in any faction; bad blood replaces tolerance
        if (d > 0) { a.hostileToPlayer = false; a.aggressor = false; }
        else if (d < 0) { a.hostileToPlayer = true; a.aggressor = true; }
        // ---- what the group has learned (each adaptation has its price: see content/rivalry.json)
        if (g.adapted("helmets") > 0) { a.helmet = true; a.speedMul = Math.min(a.speedMul, 0.95f); }
        if (g.adapted("night_watch") > 0 && raid.night()) { a.wakeUntil = 1e9f; a.light = true; }
        if (g.adapted("blast_discipline") > 0) a.blastResist = (float) Math.pow(0.8, g.adapted("blast_discipline"));
        if (g.adapted("wariness") > 0) a.morale = Math.max(0.2f, a.morale - 0.1f * g.adapted("wariness"));
        if (g.adapted("medics") > 0) a.hp = Math.min(a.maxHp, a.hp + a.maxHp * 0.2f);
        // ---- what the group is doing right now (its strategic intent, handed to the fighting mind)
        switch (engine.intent(g)) {
            case "hold" -> { if ((a.id & 1) == 0) a.role = "hold"; }
            case "withdraw" -> a.morale = Math.min(a.morale, 0.55f);
            case "raid" -> { a.risk = Math.min(1f, a.risk + 0.2f); a.alertness = Math.max(a.alertness, 0.4f); }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ encounters
    /** Asks the director who the player will meet on this operation and puts them in the world, out of sight. */
    void spawnEncounters() {
        if (!raid.populate) return;
        String here = district(raid.player.x, raid.player.z);
        planned.addAll(engine.plan(here, Math.round(Doctrine.world("encountersPerRaid", 2))));
        for (RivalryEngine.Encounter en : planned) {
            RivalryState.Leader l = engine.s.leader(en.leaderId());
            RivalryState.Group g = engine.s.group(en.groupId());
            if (l == null || g == null) continue;
            String gk = Doctrine.gameKey(g.faction);
            float[] spot = spot(gk, "support".equals(en.kind()) ? 14 : "hunt".equals(en.kind()) ? 70 : 55, "support".equals(en.kind()) ? 26 : 150);
            if (spot == null) continue;
            int squad = 800000 + Math.floorMod(l.id.hashCode(), 90000);
            int escort = "leader".equals(l.role) ? 3 : 1;
            Actor lead = null;
            for (int i = 0; i <= escort; i++) {
                Actor a = raid.debugSpawn(gk, spot[0] + (i % 2) * 1.6f - 0.8f, spot[2] + (i / 2) * 1.6f - 0.8f, spot[1]);
                a.squad = squad; a.groupId = g.id; a.homeX = spot[0]; a.homeZ = spot[2];
                a.hostileToPlayer = false; a.aggressor = false;
                applyGroup(a, g);
                a.wakeUntil = 1e9f;
                if (i == 0) {
                    lead = a;
                    a.leaderId = l.id;
                    a.name = (l.title.isEmpty() ? "" : l.title + " ") + l.name;
                    a.female = l.female; a.sexKnown = true; a.lookSeed = l.appearanceSeed;
                    a.maxHp = a.hp = a.maxHp * (1.15f + Math.min(0.5f, l.rank / 100f));
                    a.rankLevel = l.rank;
                    notables.put(a.id, l.id);
                } else a.lookSeed = l.appearanceSeed * 31 + i;
                if ("hunt".equals(en.kind())) { a.hostileToPlayer = true; a.aggressor = true; a.hasOrder = true; a.orderX = raid.player.x; a.orderZ = raid.player.z; a.orderUntil = raid.time + 300; a.alertness = 0.8f; }
                if ("support".equals(en.kind())) { a.hasOrder = true; a.orderX = raid.player.x + 4; a.orderZ = raid.player.z + 4; a.orderUntil = raid.time + 120; }
            }
            if ("support".equals(en.kind()) && lead != null) raid.notice(g.name + " have sent " + (escort + 1) + " to help you: " + lead.name + ".", 0xFF7DBA5C);
        }
    }

    /** A standable spot at one of the faction's posts in the player's district, at least min metres off and out of sight; else anywhere suitable. */
    private float[] spot(String gameFaction, float min, float max) {
        Actor p = raid.player;
        District here = raid.world.district(p.x, p.z);
        float[] best = null;
        float bd = Float.MAX_VALUE;
        for (int pass = 0; pass < 2; pass++) {
            for (MapData.Point pt : raid.world.map.posts) {
                if (pass == 0 && !pt.faction.equals(gameFaction)) continue;
                if (pt.district != here && pass == 0) continue;
                float d = p.distXZ(pt.x, pt.z);
                if (d < min || d > max || d >= bd) continue;
                int y = raid.standY(pt.x, pt.z, pt.y);
                if (y < 0 || !Physics.boxFree(raid.world, pt.x + 0.5f, y, pt.z + 0.5f, 0.3f, 1.8f)) continue;
                if (min > 30 && Physics.los(raid.world, p.x, p.eyeY(), p.z, pt.x + 0.5f, y + 1.4f, pt.z + 0.5f)) continue;
                bd = d; best = new float[]{pt.x + 0.5f, y, pt.z + 0.5f};
            }
            if (best != null) return best;
        }
        // no post suits: a street node at the right distance
        for (MapData.Node n : raid.world.map.nodes) {
            float d = p.distXZ(n.x, n.z);
            if (d < min || d > Math.min(max, 110) || d >= bd) continue;
            if (min > 30 && Physics.los(raid.world, p.x, p.eyeY(), p.z, n.x + 0.5f, n.y + 1.4f, n.z + 0.5f)) continue;
            if (!Physics.boxFree(raid.world, n.x + 0.5f, n.y, n.z + 0.5f, 0.3f, 1.8f)) continue;
            bd = d; best = new float[]{n.x + 0.5f, n.y, n.z + 0.5f};
        }
        return best;
    }

    /** Each tick: a notable who has the player in view at talking distance says what they have to say, once; favor notices reach the HUD. */
    void update(float dt) {
        Actor p = raid.player;
        if (!notables.isEmpty() && !p.dead) for (Actor a : raid.actors) {
            String lid = notables.get(a.id);
            if (lid == null || a.dead || met.contains(lid)) continue;
            if (a.dist(p) > 26 || !Physics.los(raid.world, a.x, a.eyeY(), a.z, p.x, p.eyeY(), p.z)) continue;
            RivalryState.Leader l = engine.s.leader(lid);
            met.add(lid);
            if (l == null) continue;
            engine.met(l);
            raid.say(a, engine.line(l, engine.s.group(l.groupId)), "shout");
        }
        flush();
    }

    /** New notices from the engine (favor moved, a milestone, an adaptation) shown to the player as they happen. */
    void flush() {
        List<String> n = engine.s.notices;
        if (shown > n.size()) shown = 0;
        while (shown < n.size()) {
            String line = n.get(shown++);
            raid.notice(line, line.contains("favor -") || line.contains("broken") ? 0xFFE08A30 : line.contains("**") ? 0xFFE0C040 : 0xFFB8D048);
        }
    }

    // ------------------------------------------------------------------ events
    private List<String> witnesses(Actor v, RivalryState.Group g, boolean witnessed) {
        List<String> w = new ArrayList<>();
        if (!witnessed || g == null) return w;
        w.add(g.id);
        for (Actor a : raid.nearby(v.x, v.z, 45)) {
            if (a == v || a.dead || a.leaderId == null || a.leaderId.isEmpty() || !g.id.equals(a.groupId)) continue;
            if (Physics.los(raid.world, a.x, a.eyeY(), a.z, v.x, v.y + 1, v.z)) w.add(a.leaderId);
        }
        return w;
    }

    /** How a killing was done, as the things a group could learn from: explosive, headshot, stealth, night, vehicle, long_range, melee. */
    private String method(Actor v, boolean wasUnaware) {
        List<String> m = new ArrayList<>();
        String kind = v.lastHitKind == null ? "" : v.lastHitKind;
        if (kind.equals("blast")) m.add("explosive");
        if (kind.equals("vehicle")) m.add("vehicle");
        if (kind.equals("melee")) m.add("melee");
        if (kind.equals("bullet") && "head".equals(v.hitRegion)) m.add("headshot");
        if (kind.equals("bullet") && v.lastHitRange > 60 && v.lastHitRange < 900) m.add("long_range");
        if (wasUnaware) m.add("stealth");
        if (raid.night()) m.add("night");
        return String.join(",", m);
    }

    /** The player's side killed a faction member. Witnessed is what the raid itself established: one of theirs, alive, with line of sight. */
    void onKill(Actor v, boolean witnessed, boolean wasUnaware) {
        RivalryState.Group g = group(v);
        if (g == null) return;
        String meth = method(v, wasUnaware), here = district(v.x, v.z);
        methods.computeIfAbsent(g.id, k -> new HashSet<>()).addAll(List.of(meth.split(",")));
        boolean leader = v.leaderId != null && !v.leaderId.isEmpty();
        RivalryState.Leader l = leader ? engine.s.leader(v.leaderId) : null;
        boolean top = l != null && l.id.equals(g.leaderId);
        List<String> w = witnesses(v, g, witnessed);
        engine.emit(l != null && ("leader".equals(l.role) || "lieutenant".equals(l.role)) ? "KILL_LEADER" : "KILL_MEMBER", RivalryState.PLAYER, g.id, here, meth, top ? "killed their leader" : "killed", List.of(leader ? v.leaderId : g.id + "#" + v.id), w);
        if (l != null && !top) { l.alive = false; l.diedTick = engine.s.tick; }
        eventsEmitted++;
        engine.progress("kill", g.id, here, RivalryWorld.wallet(raid.profile));
        // fighting a group's enemies on that group's own ground, where its people can see it, is standing with them
        for (RivalryState.Group ally : engine.s.active()) {
            if (ally == g || !ally.territoryIds.contains(here) || engine.rel(ally, g.id).hostility < 25) continue;
            if (raid.time - defendedAt.getOrDefault(ally.id, -999f) < 90) continue;
            boolean seen = false;
            for (Actor a : raid.nearby(v.x, v.z, 50)) if (!a.dead && ally.id.equals(a.groupId) && Physics.los(raid.world, a.x, a.eyeY(), a.z, v.x, v.y + 1, v.z)) { seen = true; break; }
            if (!seen) continue;
            defendedAt.put(ally.id, raid.time);
            engine.emit("DEFEND_TERRITORY", RivalryState.PLAYER, ally.id, here, "", "killed " + g.name + " on their ground", List.of(), List.of(ally.id));
            eventsEmitted++;
        }
        // a squad that has lost most of its people to the player while its leader still stands has been beaten, and knows it
        if (v.squad != 0) for (Actor a : raid.actors) {
            if (a.dead || a.squad != v.squad || a.leaderId == null || a.leaderId.isEmpty() || defeated.contains(a.leaderId)) continue;
            Raid.Squad sq = raid.squads.get(a.squad);
            if (sq == null || sq.initial < 2 || sq.alive > Math.max(1, Math.round(sq.initial * 0.5f))) continue;
            defeated.add(a.leaderId);
            Set<String> ms = methods.getOrDefault(g.id, Set.of());
            engine.emit("DEFEAT", RivalryState.PLAYER, g.id, here, String.join(",", ms), "beaten, the leader survived", List.of(a.leaderId), List.of(g.id, a.leaderId));
            eventsEmitted++;
        }
        flush();
    }

    /** The player's side wounded a faction member who lived: the group knows it was shot at. Reported once per group per operation. */
    void onHit(Actor v) {
        RivalryState.Group g = group(v);
        if (g == null || v.dead || !attacked.add(g.id)) return;
        List<String> w = new ArrayList<>(List.of(g.id));
        if (v.leaderId != null && !v.leaderId.isEmpty()) w.add(v.leaderId);
        engine.emit("ATTACK", RivalryState.PLAYER, g.id, district(v.x, v.z), raid.night() ? "night" : "", "opened fire", List.of(g.id + "#" + v.id), w);
        eventsEmitted++;
        flush();
    }

    /** The player treated (heal) or got back on their feet (rescue) a member of a group. They lived: the group knows. */
    void onAid(Actor a, boolean rescue) {
        RivalryState.Group g = group(a);
        if (g == null) return;
        boolean leader = a.leaderId != null && !a.leaderId.isEmpty();
        engine.emit(rescue ? "RESCUE" : "HEAL", RivalryState.PLAYER, g.id, district(a.x, a.z), "", rescue ? "revived" : "treated", List.of(leader ? a.leaderId : g.id + "#" + a.id), leader ? List.of(g.id, a.leaderId) : List.of(g.id));
        eventsEmitted++;
        engine.progress(rescue ? "rescue" : "heal", g.id, district(a.x, a.z), RivalryWorld.wallet(raid.profile));
        // someone whose life the player saved is somebody now: the world keeps them
        if (rescue && !leader && a.agentId != 0) {
            RivalryState.Leader n = engine.promote(g, a.name, a.female, a.agentId, "the player brought them back");
            n.playerRelation.trust = 25; n.playerRelation.debt = 30; n.playerRelation.favor = 25;
            a.leaderId = n.id;
        }
        flush();
    }

    /** A soldier has caught the player where they may not be. */
    void onTrespass(Actor by) {
        RivalryState.Group g = group(by);
        if (g == null) return;
        engine.emit("TRESPASS", RivalryState.PLAYER, g.id, district(by.x, by.z), raid.night() ? "night" : "", "in a restricted area", List.of(), List.of(g.id));
        eventsEmitted++;
        flush();
    }

    /** The player paid a hold-up instead of fighting it. */
    void onTribute(int squadId, int coins) {
        for (Actor a : raid.actors) {
            if (a.dead || a.squad != squadId) continue;
            RivalryState.Group g = group(a);
            if (g == null) continue;
            engine.emit("PAY_TRIBUTE", RivalryState.PLAYER, g.id, district(a.x, a.z), "holdup", coins + " cash", List.of(), List.of(g.id));
            eventsEmitted++;
            break;
        }
        flush();
    }

    /** Goods changed hands with a member of a group. */
    void onTrade(Actor a) {
        RivalryState.Group g = group(a);
        if (g == null) return;
        engine.emit("TRADE", RivalryState.PLAYER, g.id, district(a.x, a.z), "barter", "goods exchanged", List.of(), List.of(g.id));
        eventsEmitted++;
        flush();
    }

    /** The operation is over. Leaders who were hunting the player and are still alive have been got away from; jobs that wanted an extraction count it. */
    void finish(boolean extracted) {
        if (extracted) {
            for (Map.Entry<Integer, String> e : notables.entrySet()) {
                Actor a = raid.actor(e.getKey());
                RivalryState.Leader l = engine.s.leader(e.getValue());
                if (a == null || a.dead || l == null || !a.hostileToPlayer || !met.contains(l.id)) continue;
                engine.emit("ESCAPE", RivalryState.PLAYER, l.groupId, district(a.x, a.z), "", "left the district alive", List.of(l.id), List.of(l.groupId, l.id));
                eventsEmitted++;
            }
            engine.progress("extract", "", raid.debrief.deployedIn == null ? "" : raid.debrief.deployedIn.name(), RivalryWorld.wallet(raid.profile));
        }
        flush();
    }

    /** Price multiplier for bartering with a member of a group (the group's favor decides). */
    public float priceFactor(Actor a) { return engine.priceFactor(group(a)); }

    /** True when this body is one of the director's named people. */
    public boolean notable(Actor a) { return notables.containsKey(a.id); }

    static boolean unaware(Actor v) { return v.awareLevel == 0 && ("idle".equals(v.state) || v.vital != Vital.ACTIVE && false) && !absentialis.neuralai.integration.Archetypes.arch(v.archetype).infected(); }
}
