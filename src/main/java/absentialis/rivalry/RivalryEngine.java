package absentialis.rivalry;

import absentialis.engine.util.Json;
import absentialis.rivalry.RivalryState.Group;
import absentialis.rivalry.RivalryState.Job;
import absentialis.rivalry.RivalryState.Leader;
import absentialis.rivalry.RivalryState.Memory;
import absentialis.rivalry.RivalryState.Relation;
import absentialis.rivalry.RivalryState.Treaty;
import absentialis.rivalry.RivalryState.WorldEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rules of the Group Rivalry System (0.6). The loop the brief asks for runs through this class and nowhere else:
 *
 *   Encounter -> significant EVENT ({@link #emit}) -> who KNOWS (participants, witnesses, then bounded rumour)
 *   -> MEMORY on those who know -> RELATIONSHIP and consequence rules -> ADAPTATION and strategy
 *   ({@link #tick}) -> a contextual RE-ENCOUNTER ({@link #plan}).
 *
 * Nothing here reads the player's position or any truth a group has not been told: a group's view of the player is
 * built only from events it took part in, saw, or heard of. Every change to a relationship is written to that
 * relationship's audit trail with the event that caused it; every decision keeps the utility of what it weighed;
 * every off-screen fight is an event with its arithmetic in the outcome. Randomness comes from the state's own
 * dice ({@link RivalryState#roll}), so a seed and a history reproduce.
 */
public final class RivalryEngine {
    /** Where groups can be: the districts of the Zone, how strongly each faction is rooted in each, and how far apart they are. */
    public interface Locations {
        List<String> ids();
        float influence(String location, String gameFaction);
        /** Hops between two locations (0 = the same). */
        int distance(String a, String b);
        String name(String location);
    }

    /** The player's purse, for tribute, bribes and pay. */
    public interface Wallet { long coins(); boolean pay(int amount); void receive(int amount); }

    public record Result(boolean ok, String text) {}
    /** A notable the director has chosen to put in the player's way, and why. */
    public record Encounter(String leaderId, String groupId, String kind, String reason, String line, float score) {}

    public final RivalryState s;
    public final Locations loc;
    /** Event types the target group always learns of, because its own people lived through them. */
    private static final Set<String> LIVED = Set.of("SPARE", "TRADE", "GIFT", "HEAL", "RESCUE", "COMPLETE_JOB", "BREAK_PROMISE", "MAKE_TREATY", "INTIMIDATE", "BRIBE", "INTEL", "MEDIATE", "PAY_TRIBUTE", "BETRAY", "DEFEAT", "ESCAPE", "CAPTURE", "GROUP_RAID", "GROUP_AID", "SUCCESSION", "ADAPT");
    /** Goodwill that could be farmed: repeats of these are worth less each time. Harm is never discounted. */
    private static final Set<String> DIMINISHING = Set.of("TRADE", "GIFT", "BRIBE", "HEAL", "INTEL", "PAY_TRIBUTE", "COMPLETE_JOB", "RESCUE", "DEFEND_TERRITORY", "MEDIATE");

    public RivalryEngine(RivalryState s, Locations loc) { this.s = s; this.loc = loc; }

    // ================================================================== creation (brief section 5)
    /** Founds the world's groups once per profile: the configured number for each faction, each with its own leader. */
    public void ensureSeeded(long seed) {
        if (s.seeded) return;
        s.seed = seed;
        int per = Math.max(1, Math.round(Doctrine.world("groupsPerFaction", 1)));
        for (int i = 0; i < per; i++) for (String f : Doctrine.FACTIONS) createGroup(f, null);
        // how the factions regard each other before anything has happened: the diplomatic matrix of the Zone
        for (Group a : s.groups.values()) for (Group b : s.groups.values()) {
            if (a == b) continue;
            Relation r = rel(a, b.id);
            if (a.faction.equals(b.faction)) { r.favor = 15; r.trust = 10; }
            else if (pair(a.faction, b.faction, "government", "bandits")) { r.hostility = 45; r.favor = -30; r.trust = -25; }
            else if (pair(a.faction, b.faction, "loners", "bandits")) { r.hostility = 35; r.favor = -25; r.trust = -30; r.fear = a.faction.equals("loners") ? 25 : 0; }
            else if (pair(a.faction, b.faction, "bandits", "mercenaries")) { r.hostility = 30; r.favor = -15; }
            else if (pair(a.faction, b.faction, "government", "mercenaries")) { r.favor = 5; r.trust = -5; }
            else { r.favor = 5; }
        }
        s.seeded = true;
    }

    private static boolean pair(String a, String b, String p, String q) { return a.equals(p) && b.equals(q) || a.equals(q) && b.equals(p); }

    /** The creation pipeline: region, resources, size and territory; a new leader; doctrine bent by that leader; lieutenants; registration. */
    public Group createGroup(String faction, String location) {
        Group g = new Group();
        g.id = "grp-" + s.nextGroup++;
        g.faction = faction;
        g.createdTick = s.tick;
        g.seed = (long) (s.roll() * (1L << 40));
        String gk = Doctrine.gameKey(faction);
        // 1. where: the district where the faction is strongest that no group of its own already holds
        if (location == null) {
            List<String> ids = new ArrayList<>(loc.ids());
            ids.sort(Comparator.comparingDouble((String l) -> -loc.influence(l, gk)));
            for (String l : ids) { boolean taken = false; for (Group o : s.ofFaction(faction)) if (o.territoryIds.contains(l)) taken = true; if (!taken) { location = l; break; } }
            if (location == null) location = ids.isEmpty() ? "" : ids.get(0);
        }
        g.homeDistrict = location;
        g.territoryIds.add(location);
        int min = Math.round(Doctrine.world("minSize", 10)), max = Math.round(Doctrine.world("maxSize", 50));
        g.size = g.startSize = min + (int) (s.roll() * (max - min + 1));
        g.resources.put("supplies", g.size * (0.4f + s.roll() * 0.4f));
        g.resources.put("ammo", g.size * (0.3f + s.roll() * 0.4f));
        g.resources.put("coins", 4 + s.roll() * 12);
        g.resources.put("gear", 3 + s.roll() * 8);
        g.resources.put("medicine", 2 + s.roll() * 6);
        g.resources.put("intel", s.roll() * 4);
        g.morale = 0.6f + s.roll() * 0.3f;
        g.cohesion = 0.55f + s.roll() * 0.35f;
        // 4. a name and colours that belong to the faction and to nobody else in this world
        List<String> names = Json.strList(Doctrine.faction(faction), "groupNames");
        for (int k = 0; k < names.size(); k++) { String n = names.get((int) ((s.roll() * names.size()) + k) % names.size()); boolean used = false; for (Group o : s.groups.values()) if (o.name.equals(n)) used = true; if (!used) { g.name = n; break; } }
        if (g.name.isEmpty()) g.name = Doctrine.display(faction) + " " + g.id.substring(4);
        g.colour = Doctrine.colour(faction);
        g.emblem = faction + ":" + (int) (s.roll() * 6);
        for (Map.Entry<String, Object> e : Json.obj(Doctrine.faction(faction), "state").entrySet()) if (e.getValue() instanceof Number n) g.doctrineState.put(e.getKey(), n.floatValue());
        s.groups.put(g.id, g);
        // 2. a new, unique leader - and 3. the doctrine as that leader applies it
        Leader l = generateLeader(g, "leader");
        g.leaderId = l.id;
        bendDoctrine(g, l);
        int lts = 1 + (s.roll() < 0.6f ? 1 : 0);
        for (int i = 0; i < lts; i++) g.lieutenantIds.add(generateLeader(g, "lieutenant").id);
        g.objective = "hold";
        note(g, "founded in " + loc.name(location) + " under " + l.title + " " + l.name + " (" + String.join(", ", l.traits) + "), " + g.size + " strong");
        return g;
    }

    /** A persistent person with a stable id and seeded traits (section 3.1). */
    public Leader generateLeader(Group g, String role) {
        Leader l = new Leader();
        l.id = "ldr-" + s.nextLeader++;
        l.groupId = g.id;
        l.role = role;
        l.bornTick = s.tick;
        l.appearanceSeed = (long) (s.roll() * (1L << 40)) + 1;
        l.female = s.roll() < absentialis.sim.Names.femaleShare();
        l.name = absentialis.sim.Names.full(l.appearanceSeed, l.female);
        List<String> titles = Json.strList(Doctrine.faction(g.faction), "titles");
        l.title = "leader".equals(role) ? (titles.isEmpty() ? "Leader" : titles.get((int) (s.roll() * titles.size()))) : "notable".equals(role) ? "" : "Lieutenant";
        List<String> pool = new ArrayList<>(Doctrine.traitNames());
        for (int i = 0; i < 2 && !pool.isEmpty(); i++) l.traits.add(pool.remove((int) (s.roll() * pool.size())));
        // a few temperaments contradict each other: nobody is both
        if (l.traits.contains("aggressive") && l.traits.contains("cautious")) l.traits.remove(1);
        if (l.traits.contains("honorable") && l.traits.contains("greedy")) l.traits.remove(1);
        l.ambitions.add(pick(Json.strList(Doctrine.faction(g.faction), "ambitions")));
        List<String> sk = new ArrayList<>(Doctrine.pool("skills"));
        for (int i = 0; i < 2 && !sk.isEmpty(); i++) l.skills.add(sk.remove((int) (s.roll() * sk.size())));
        l.fears.add(pick(Doctrine.pool("fears")));
        l.weaknesses.add(pick(Doctrine.pool("weaknesses")));
        l.voice = pick(Doctrine.pool("voices"));
        l.competence = 0.35f + s.roll() * 0.6f;
        l.loyalty = 0.3f + s.roll() * 0.65f;
        l.ambition = 0.15f + s.roll() * 0.8f;
        l.dread = s.roll() * 0.8f;
        l.rank = "leader".equals(role) ? 18 + (int) (s.roll() * 28) : 6 + (int) (s.roll() * 22);
        s.leaders.put(l.id, l);
        return l;
    }

    private String pick(List<String> l) { return l.isEmpty() ? "" : l.get((int) (s.roll() * l.size()) % l.size()); }

    /** Writes the group's working weights: the faction's doctrine times this leader's bounded modifiers. */
    public void bendDoctrine(Group g, Leader l) {
        List<String> traits = l == null ? List.of() : l.traits;
        for (String c : Doctrine.considerations()) g.doctrineState.put("w_" + c, Doctrine.weight(g.faction, c) * Doctrine.leaderModifier(g.faction, traits, c));
        g.doctrineState.put("risk", Doctrine.riskTolerance(g.faction, traits));
    }

    public Relation rel(Group g, String otherGroupId) { return g.relationsByGroup.computeIfAbsent(otherGroupId, k -> new Relation()); }

    private void note(Group g, String what) { g.history.add(0, "t" + s.tick + " " + what); while (g.history.size() > 30) g.history.remove(g.history.size() - 1); }

    // ================================================================== lookups for the rest of the game
    /** The group a member of a faction, met in a district, belongs to: one that holds that ground, else the nearest of its faction. */
    public Group groupFor(String gameFaction, String location, long memberId) {
        String f = Doctrine.ofGameKey(gameFaction);
        if (f == null) return null;
        List<Group> all = s.ofFaction(f), here = new ArrayList<>();
        if (all.isEmpty()) return null;
        for (Group g : all) if (g.territoryIds.contains(location)) here.add(g);
        if (!here.isEmpty()) return here.get((int) Math.floorMod(memberId, (long) here.size()));
        Group best = all.get(0);
        int bd = 99;
        for (Group g : all) for (String t : g.territoryIds) { int d = loc.distance(t, location); if (d < bd) { bd = d; best = g; } }
        return best;
    }

    public String status(Group g) { return Doctrine.status(g.playerRelation.favor).name(); }

    /** Whether there is a live agreement of this kind (null = any) between the player and a group. */
    public boolean treaty(Group g, String kind) { return s.treatyWith(g.id, kind) != null; }

    /**
     * How members of a group take the sight of the player before anything else is said: -1 hostile on sight,
     * 0 wary (the faction's ordinary behaviour applies), 1 friendly. A treaty or earned favor replaces suspicion in
     * any faction; no faction is locked out of it (invariant 6).
     */
    public int disposition(Group g) {
        if (g == null) return 0;
        if (treaty(g, null)) return 1;
        float f = g.playerRelation.favor;
        if (g.state("hunt_player") > 0) return -1;
        int st = Doctrine.statusIndex(f);
        if (st <= 1) return -1;                // Hated, Hostile
        if (st >= 3) return 1;                 // Accepted and better
        return g.playerRelation.hostility >= 35 ? -1 : 0;
    }

    /** What the combat AI is told the group is doing right now: hold, patrol, raid, escort or withdraw (section 7). */
    public String intent(Group g) {
        if (g == null || g.lastAction.isEmpty()) return "hold";
        String a = g.lastAction.contains(" ") ? g.lastAction.substring(0, g.lastAction.indexOf(' ')) : g.lastAction;
        return Json.str(Doctrine.action(a), "intent", "hold");
    }

    /** Rivalry between the player and a leader: an emergent state, 0 (none) .. 1 (consuming), never a fixed label (section 3.4). */
    public float rivalry(Leader l) {
        if (l == null) return 0;
        Relation r = l.playerRelation;
        float history = 0;
        for (Memory m : l.personalMemories) if (RivalryState.PLAYER.equals(m.subject)) history += m.salience * (m.known() ? 1 : 0.5f) * Math.min(3, m.count);
        float v = Math.max(0, r.hostility) / 100f * 0.45f + Math.min(1, history / 4f) * 0.25f + Math.min(1, (l.encounters + l.defeats * 2 + l.escapes) / 6f) * 0.2f + Math.abs(r.fear) / 100f * 0.1f;
        return Math.max(0, Math.min(1, v));
    }

    /** Rivalry between two groups, 0 .. 1: mutual hostility, what they remember of each other, and competition for the same ground. */
    public float rivalry(Group a, Group b) {
        float h = (Math.max(0, rel(a, b.id).hostility) + Math.max(0, rel(b, a.id).hostility)) / 200f;
        float mem = 0;
        for (Memory m : a.groupMemories) if (b.id.equals(m.subject)) mem += m.salience * Math.min(3, m.count);
        boolean shared = false;
        for (String t : a.territoryIds) if (b.territoryIds.contains(t)) shared = true;
        return Math.max(0, Math.min(1, h * 0.6f + Math.min(1, mem / 4f) * 0.25f + (shared ? 0.15f : 0)));
    }

    // ================================================================== events, knowledge and memory (section 3.2)
    public WorldEvent event(String type, String actorId, String groupId, String location, String method, String outcome, List<String> targets, List<String> witnesses) {
        WorldEvent e = new WorldEvent();
        e.id = "evt-" + s.nextEvent++;
        e.tick = s.tick;
        e.type = type; e.actorId = actorId == null ? "" : actorId; e.groupId = groupId == null ? "" : groupId; e.locationId = location == null ? "" : location;
        e.method = method == null ? "" : method; e.outcome = outcome == null ? "" : outcome;
        if (targets != null) e.targetIds.addAll(targets);
        if (witnesses != null) e.witnessIds.addAll(witnesses);
        e.salience = (float) Json.num(Doctrine.event(type), "salience", 0.4);
        return e;
    }

    /** Builds and ingests an event in one call. */
    public WorldEvent emit(String type, String actorId, String groupId, String location, String method, String outcome, List<String> targets, List<String> witnesses) {
        WorldEvent e = event(type, actorId, groupId, location, method, outcome, targets, witnesses);
        emit(e);
        return e;
    }

    private boolean saw(Group g, WorldEvent e) {
        for (String w : e.witnessIds) {
            if (w.equals(g.id) || w.startsWith(g.id + "#")) return true;
            Leader l = s.leader(w);
            if (l != null && l.groupId.equals(g.id)) return true;
        }
        return false;
    }

    private boolean present(Leader l, WorldEvent e) { return e.targetIds.contains(l.id) || e.witnessIds.contains(l.id) || e.actorId.equals(l.id); }

    /** The group an actor id stands for: itself, or the group of the leader with that id. */
    private Group groupOfActor(String actorId) {
        Group g = s.group(actorId);
        if (g != null) return g;
        Leader l = s.leader(actorId);
        return l == null ? null : s.group(l.groupId);
    }

    /**
     * Ingests an event exactly once. Returns false if this id has been seen before (the same event arriving twice -
     * from a retry, a replay or two reporters - changes nothing the second time).
     *
     * Who learns of it: the group it was done to, if its people lived through it or one of them saw it; any other
     * group with a witness; the leaders who were there in person, and (as a report) the leaders of groups that
     * know. Everyone else hears nothing now - rumour may reach some of them later, weaker, and never further than
     * the configured number of hops.
     */
    public boolean emit(WorldEvent e) {
        if (e.id.isEmpty() || !s.seen.add(e.id)) return false;
        s.log.add(e);
        int cap = Math.round(Doctrine.world("logCap", 400));
        while (s.log.size() > cap) s.log.remove(0);
        Group target = s.group(e.groupId), actorGroup = groupOfActor(e.actorId);
        List<Group> informed = new ArrayList<>();
        for (Group g : s.active()) {
            boolean isTarget = g == target, isActor = g == actorGroup;
            boolean knows = isActor || saw(g, e) || isTarget && LIVED.contains(e.type);
            if (!knows) {
                // its people were killed with nobody left to say by whom: it knows the loss, not the hand
                if (isTarget && ("KILL_MEMBER".equals(e.type) || "KILL_LEADER".equals(e.type))) {
                    Memory m = memory(e, "known", 1, "");
                    m.type = "LOSS_UNKNOWN"; m.subject = ""; m.tags.clear(); m.tags.add("lost_someone"); m.salience *= 0.5f; m.pivotal = false;
                    remember(g.groupMemories, m);
                }
                continue;
            }
            informed.add(g);
            learn(g, e, 1f, "known", isTarget ? "" : g.id, isTarget, isActor);
        }
        // the slow, faction-wide impression moves a little with every favor change the factions come to know of
        scheduleRumours(e, informed, 0);
        // what happened, happened - whether or not anyone knows who did it. What a group makes of it needs knowledge.
        if (target != null) facts(target, e);
        if (target != null && informed.contains(target)) consequences(target, e);
        return true;
    }

    private Memory memory(WorldEvent e, String knowledge, float confidence, String source) {
        Memory m = new Memory();
        m.eventId = e.id; m.type = e.type; m.tick = e.tick; m.salience = e.salience; m.knowledge = knowledge; m.confidence = confidence; m.sourceId = source;
        m.subject = e.actorId;
        m.tags.addAll(Json.strList(Doctrine.event(e.type), "tags"));
        for (String meth : e.method.split(",")) if (!meth.isBlank()) m.tags.add("m:" + meth.trim());
        if (!e.locationId.isEmpty()) m.tags.add("at:" + e.locationId);
        m.pivotal = e.salience >= Doctrine.world("pivotalSalience", 0.75f);
        return m;
    }

    /**
     * Stores a memory: the same event is never held twice (the firmer knowledge wins), repeats of small things fold
     * into one entry, and when the store is full the least valuable entry that is not pivotal makes room.
     */
    void remember(List<Memory> store, Memory m) {
        for (Memory x : store) if (x.eventId.equals(m.eventId)) {
            if (m.known() && !x.known()) { x.knowledge = "known"; x.confidence = Math.max(x.confidence, m.confidence); x.sourceId = m.sourceId; }
            return;
        }
        if (m.salience < Doctrine.world("aggregateBelow", 0.35f)) {
            for (Memory x : store) if (x.type.equals(m.type) && x.subject.equals(m.subject) && !x.pivotal && x.knowledge.equals(m.knowledge)) { x.count++; x.tick = m.tick; x.eventId = m.eventId; return; }
        }
        store.add(m);
        int cap = Math.round(Doctrine.world("memoryCap", 40));
        while (store.size() > cap) {
            int worst = -1;
            float wv = Float.MAX_VALUE;
            for (int i = 0; i < store.size(); i++) {
                Memory x = store.get(i);
                if (x.pivotal) continue;
                float v = x.salience * x.confidence + (x.tick - s.tick) * 0.004f;       // older is worth less
                if (v < wv) { wv = v; worst = i; }
            }
            if (worst < 0) { store.remove(0); } else store.remove(worst);
        }
    }

    /** A group comes to know of an event (first hand, or as a rumour at lower confidence) and it changes how the group feels. */
    private void learn(Group g, WorldEvent e, float confidence, String knowledge, String source, boolean isTarget, boolean isActor) {
        remember(g.groupMemories, memory(e, knowledge, confidence, source));
        Leader l = s.leaderOf(g);
        boolean leaderThere = l != null && l.alive && present(l, e);
        if (l != null && l.alive) remember(l.personalMemories, memory(e, knowledge, leaderThere ? confidence : confidence * 0.8f, leaderThere ? "" : g.id));
        if (isActor) return;
        Map<String, Object> def = Doctrine.event(e.type);
        Group actorGroup = groupOfActor(e.actorId);
        boolean byPlayer = RivalryState.PLAYER.equals(e.actorId);
        Map<String, Object> delta = null;
        float scale = confidence;
        if (isTarget) delta = Json.obj(def, "to");
        else {
            // not done to this group: it matters to them through whom it was done to
            Group target = s.group(e.groupId);
            if (target == null || target == g) return;
            Relation toTarget = rel(g, target.id);
            if (toTarget.hostility >= 25) delta = Json.obj(def, "ally");                       // an enemy's loss
            else if (toTarget.favor >= 20) { delta = Json.obj(def, "to"); scale *= 0.3f; }     // a friend's
            else return;
        }
        if (delta == null || delta.isEmpty()) return;
        scale *= Doctrine.reaction(g.faction, e.type);
        if (byPlayer) {
            float df = apply(g.playerRelation, delta, scale, e, traitsOf(null));
            postNotice(g, e, df, "rumored".equals(knowledge));
            // the leader's own view: at full weight if they were there, as a report if they were told, coloured by temperament
            if (l != null && l.alive) apply(l.playerRelation, delta, scale * (leaderThere ? 1.15f : 0.6f), e, l.traits);
            if (Math.abs(df) >= 0.5f) {
                String fk = g.faction;
                s.factionReputation.merge(fk, df * Doctrine.world("factionRepShare", 0.15f), Float::sum);
                s.factionReputation.put(fk, Math.max(-100, Math.min(100, s.factionReputation.get(fk))));
            }
        } else if (actorGroup != null && actorGroup != g) {
            apply(rel(g, actorGroup.id), delta, scale, e, traitsOf(l));
        }
    }

    private List<String> traitsOf(Leader l) { return l == null ? List.of() : l.traits; }

    /**
     * Moves a relationship by an event's table entry. Bounded (-100 .. 100), audited, and - for goodwill that could
     * be farmed - worth less each time it is repeated. Returns the change in favor.
     */
    private float apply(Relation r, Map<String, Object> delta, float scale, WorldEvent e, List<String> traits) {
        float rep = 1;
        if (DIMINISHING.contains(e.type)) {
            int n = r.repeats.merge(e.type, 1, Integer::sum);
            rep = Math.max(Doctrine.world("repeatFloor", 0.2f), (float) Math.pow(Doctrine.world("repeatDiminish", 0.7f), n - 1));
        }
        float f0 = r.favor;
        float fav = (float) Json.num(delta, "favor", 0), tru = (float) Json.num(delta, "trust", 0), res = (float) Json.num(delta, "respect", 0), fea = (float) Json.num(delta, "fear", 0), hos = (float) Json.num(delta, "hostility", 0), deb = (float) Json.num(delta, "debt", 0);
        // temperament colours how a person takes it; it never reverses it
        if (traits.contains("vengeful") && hos > 0) hos *= 1.3f;
        if (traits.contains("honorable")) { if (tru < 0) tru *= 1.3f; if (deb > 0) deb *= 1.3f; }
        if (traits.contains("paranoid") && tru > 0) tru *= 0.6f;
        if (traits.contains("pragmatic") && fav > 0) fav *= 1.2f;
        if (traits.contains("greedy") && ("BRIBE".equals(e.type) || "GIFT".equals(e.type) || "PAY_TRIBUTE".equals(e.type))) fav *= 1.35f;
        if (traits.contains("cautious") && fea > 0) fea *= 1.25f;
        if (traits.contains("aggressive") && fea > 0) fea *= 0.7f;
        float k = scale * rep;
        r.favor = clamp(r.favor + fav * k); r.trust = clamp(r.trust + tru * k); r.respect = clamp(r.respect + res * k);
        r.fear = clamp(r.fear + fea * k); r.hostility = clamp(r.hostility + hos * k); r.debt = Math.max(0, Math.min(100, r.debt + deb * k));
        r.touched = s.tick;
        float df = r.favor - f0;
        r.audit.add(0, "t" + s.tick + "|" + e.id + "|" + e.type + "|" + String.format("%+.1f favor", df) + (rep < 1 ? String.format(" (repeat x%.2f)", rep) : ""));
        while (r.audit.size() > 16) r.audit.remove(r.audit.size() - 1);
        return df;
    }

    private static float clamp(float v) { return Math.max(-100, Math.min(100, v)); }

    /** Who a knowing group tells: its own faction and whoever is near, a few at a time, arriving later and less certain. */
    private void scheduleRumours(WorldEvent e, List<Group> from, int hop) {
        int maxHops = Math.round(Doctrine.world("rumourHops", 2));
        if (hop >= maxHops || e.salience < Doctrine.world("rumourMinSalience", 0.4f)) return;
        long delay = Math.max(1, Math.round(Doctrine.world("rumourDelayTicks", 2)));
        float conf = (float) Math.pow(Doctrine.world("rumourConfidence", 0.6f), hop + 1);
        for (Group k : from) {
            int told = 0;
            for (Group o : s.active()) {
                if (o == k || told >= 3) continue;
                boolean already = false;
                for (Memory m : o.groupMemories) if (m.eventId.equals(e.id)) already = true;
                for (String ru : s.rumours) if (ru.startsWith(e.id + "|" + o.id + "|")) already = true;
                if (already) continue;
                boolean near = false;
                for (String a : k.territoryIds) for (String b : o.territoryIds) if (loc.distance(a, b) <= 1) near = true;
                boolean talks = o.faction.equals(k.faction) && rel(k, o.id).favor >= 0 || near && rel(k, o.id).hostility < 40;
                if (!talks) continue;
                s.rumours.add(e.id + "|" + o.id + "|" + (s.tick + delay) + "|" + conf + "|" + k.id + "|" + (hop + 1));
                told++;
            }
        }
    }

    private void deliverRumours() {
        int budget = Math.round(Doctrine.world("rumoursPerTick", 6));
        // delivering a rumour can start another: work from a copy, and let whatever is passed on join the queue behind it
        List<String> pending = new ArrayList<>(s.rumours);
        s.rumours.clear();
        for (String ru : pending) {
            String[] p = ru.split("\\|");
            if (p.length < 6) continue;
            long at = Long.parseLong(p[2]);
            if (at > s.tick || budget <= 0) { s.rumours.add(ru); continue; }
            WorldEvent e = s.event(p[0]);
            Group g = s.group(p[1]);
            if (e == null || g == null || !g.active) continue;
            budget--;
            float conf = Float.parseFloat(p[3]);
            int hop = Integer.parseInt(p[5]);
            boolean has = false;
            for (Memory m : g.groupMemories) if (m.eventId.equals(e.id)) has = true;
            if (has) continue;
            learn(g, e, conf, "rumored", p[4], g.id.equals(e.groupId), false);
            scheduleRumours(e, List.of(g), hop);
        }
    }

    // ================================================================== consequences (section 3.3)
    /** What an event the group knows of does beyond feelings: to its leader's body and nerve, to its leadership, to how it works. */
    /** The physical facts of an event for the group it happened to: who is dead, how many are left. True whoever knows. */
    private void facts(Group g, WorldEvent e) {
        if ("KILL_LEADER".equals(e.type)) {
            Leader dead = null;
            for (String t : e.targetIds) { Leader x = s.leader(t); if (x != null && x.groupId.equals(g.id)) dead = x; }
            if (dead == null) dead = s.leaderOf(g);
            if (dead != null && dead.alive) { dead.alive = false; dead.diedTick = s.tick; }
            if (dead != null && dead.id.equals(g.leaderId)) { g.morale = Math.max(0.1f, g.morale - 0.25f); g.cohesion = Math.max(0.1f, g.cohesion - 0.2f); }
            else if (dead != null) { g.lieutenantIds.remove(dead.id); g.memberIds.remove(dead.id); }
            g.size = Math.max(2, g.size - 1);
        }
        if ("KILL_MEMBER".equals(e.type)) { g.size = Math.max(2, g.size - Math.max(1, e.targetIds.size())); g.morale = Math.max(0.05f, g.morale - 0.03f); }
        if ("CAPTURE".equals(e.type)) for (String t : e.targetIds) { Leader x = s.leader(t); if (x != null && x.groupId.equals(g.id)) x.captured = true; }
    }

    private void consequences(Group g, WorldEvent e) {
        Leader l = s.leaderOf(g);
        boolean byPlayer = RivalryState.PLAYER.equals(e.actorId);
        if (l != null && l.alive && present(l, e)) {
            if ("DEFEAT".equals(e.type)) {
                l.defeats++;
                // a beating leaves a mark: sometimes on the body, always on the nerve - and the mark depends on how it was done
                if (s.roll() < 0.6f) {
                    String scar = e.method.contains("explosive") ? "burn scars" : e.method.contains("headshot") ? "a furrow across the scalp" : e.method.contains("melee") ? "a broken nose" : s.roll() < 0.5f ? "a limp" : "a scarred cheek";
                    if (!l.injuries.contains(scar) && l.injuries.size() < 3) { l.injuries.add(scar); note(g, l.name + " carries " + scar + " from the fight"); }
                }
                l.dread = Math.max(0, l.dread - 0.05f);
            }
            if ("ESCAPE".equals(e.type)) l.escapes++;
            if ("SPARE".equals(e.type) && byPlayer && !l.personalMemoriesHave("spared_me")) { Memory m = memory(e, "known", 1, ""); m.eventId = e.id + ":p"; m.tags.add("spared_me"); m.pivotal = true; l.personalMemories.add(m); }
            if ("RESCUE".equals(e.type) && byPlayer) { l.playerRelation.trust = clamp(l.playerRelation.trust + 8); l.playerRelation.debt = Math.min(100, l.playerRelation.debt + 8); }
        }
        if (byPlayer) {
            // government: violations feed the alert ladder (warn -> inspect -> pursue -> lockdown)
            if ("government".equals(g.faction) && ("TRESPASS".equals(e.type) || "ATTACK".equals(e.type) || "KILL_MEMBER".equals(e.type) || "KILL_LEADER".equals(e.type))) {
                g.doctrineState.merge("violations", "TRESPASS".equals(e.type) ? 1f : 2f, Float::sum);
                g.doctrineState.put("alert", Math.min(1f, g.state("alert") + ("TRESPASS".equals(e.type) ? 0.12f : 0.25f)));
            }
            if ("bandits".equals(g.faction) && ("DEFEAT".equals(e.type) || "KILL_MEMBER".equals(e.type))) g.doctrineState.put("prey", Math.max(0f, g.state("prey") - 0.15f));
            // a treaty is broken by what the group comes to know, not by what happens out of its sight
            Treaty t = s.treatyWith(g.id, null);
            if (t != null && ("ATTACK".equals(e.type) || "KILL_MEMBER".equals(e.type) || "KILL_LEADER".equals(e.type) || "CAPTURE".equals(e.type))) breakTreaty(t, RivalryState.PLAYER, "attacked their people");
        }
        adapt(g);
    }

    /** Tells the player a group's favor moved, why, and where it now stands - and when it crossed a milestone. */
    private void postNotice(Group g, WorldEvent e, float df, boolean hearsay) {
        if (Math.abs(df) < 0.5f) return;
        float after = g.playerRelation.favor, before = after - df;
        String line = g.name + ": favor " + String.format("%+.0f", df) + " (" + describe(e.type) + (hearsay ? ", as word reached them" : "") + ") - " + Doctrine.status(after).name() + " " + Math.round(after);
        for (Object o : Doctrine.milestones()) {
            if (!(o instanceof Map)) continue;
            @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
            float th = (float) Json.num(m, "favor", 0);
            if (before < th && after >= th) line += "  ** " + Json.str(m, "name", "") + ": " + Json.str(m, "grants", "") + " **";
        }
        s.notices.add(line);
        while (s.notices.size() > 24) s.notices.remove(0);
    }

    public static String describe(String type) {
        return switch (type) {
            case "ATTACK" -> "you attacked them"; case "DEFEAT" -> "you beat them in a fight"; case "KILL_MEMBER" -> "you killed one of theirs"; case "KILL_LEADER" -> "you killed their leader";
            case "SPARE" -> "you spared them"; case "CAPTURE" -> "you took a prisoner"; case "ESCAPE" -> "you got away from them"; case "BETRAY" -> "you betrayed them"; case "TRADE" -> "trade";
            case "GIFT" -> "supplies delivered"; case "HEAL" -> "you treated their wounded"; case "RESCUE" -> "you rescued one of theirs"; case "COMPLETE_JOB" -> "work done for them";
            case "BREAK_PROMISE" -> "a promise broken"; case "DEFEND_TERRITORY" -> "you fought for their ground"; case "AID_RIVAL" -> "you helped their enemies"; case "MAKE_TREATY" -> "an agreement signed";
            case "INTIMIDATE" -> "you leaned on them"; case "BRIBE" -> "coin changed hands"; case "INTEL" -> "information given"; case "MEDIATE" -> "peace brokered"; case "TRESPASS" -> "you trespassed";
            case "PAY_TRIBUTE" -> "tribute paid"; default -> type.toLowerCase().replace('_', ' ');
        };
    }

    /**
     * Adaptation (sections 3.3 and 7): a group changes how it works only from outcomes it KNOWS of, only when it can
     * pay for the change, never beyond the rule's cap, and never as immunity - each adaptation names its tradeoff.
     */
    @SuppressWarnings("unchecked")
    private void adapt(Group g) {
        for (Object o : Doctrine.adaptations()) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> rule = (Map<String, Object>) o;
            String id = Json.str(rule, "id", "");
            int level = g.adapted(id), max = Json.integer(rule, "max", 1);
            if (level >= max || g.cooldownUntil.getOrDefault("adapt:" + id, -1L) > s.tick) continue;
            Map<String, Object> trig = Json.obj(rule, "trigger");
            List<String> types = Json.strList(trig, "types");
            String method = Json.str(trig, "method", "");
            int need = Json.integer(trig, "count", 2) * (level + 1), have = 0;
            for (Memory m : g.groupMemories) {
                if (!m.known() || !RivalryState.PLAYER.equals(m.subject) || !types.contains(m.type) || s.tick - m.tick > 60) continue;
                if (!method.isEmpty() && !m.tags.contains("m:" + method)) continue;
                have += m.count;
            }
            if (have < need) continue;
            Map<String, Object> cost = Json.obj(rule, "cost");
            boolean afford = true;
            for (Map.Entry<String, Object> c : cost.entrySet()) if (c.getValue() instanceof Number n && g.res(c.getKey()) < n.floatValue()) afford = false;
            if (!afford) {
                if (g.cooldownUntil.getOrDefault("want:" + id, -1L) <= s.tick) { note(g, "wants " + Json.str(rule, "name", id) + " but cannot pay for it"); g.cooldownUntil.put("want:" + id, s.tick + 6); }
                continue;
            }
            for (Map.Entry<String, Object> c : cost.entrySet()) if (c.getValue() instanceof Number n) g.res(c.getKey(), -n.floatValue());
            g.adaptations.put(id, level + 1);
            g.cooldownUntil.put("adapt:" + id, s.tick + Json.integer(rule, "cooldown", 6));
            note(g, "adapted: " + Json.str(rule, "name", id) + " " + (level + 1) + "/" + max + " - " + Json.str(rule, "effect", "") + " (tradeoff: " + Json.str(rule, "tradeoff", "") + ")");
            emit("ADAPT", g.id, g.id, g.homeDistrict, id, Json.str(rule, "name", id) + " level " + (level + 1), List.of(), List.of(g.id));
            s.notices.add(g.name + " have changed how they work: " + Json.str(rule, "name", id).toLowerCase() + ".");
        }
    }

    // ================================================================== hierarchy and succession (section 3.5)
    /** Makes sure the group has a living leader, resolving succession if it has not. Returns the new leader, or null if none was needed. */
    public Leader succeed(Group g) {
        Leader old = s.leaderOf(g);
        if (old != null && old.alive && !old.captured) return null;
        Leader pick = null;
        float best = -1;
        // who the faction would follow: rank and ability in a chain of command, fear among bandits, trust among loners, results among contractors
        float wc = switch (g.faction) { case "government" -> 0.5f; case "mercenaries" -> 0.6f; case "loners" -> 0.25f; default -> 0.2f; };
        float wl = switch (g.faction) { case "government" -> 0.3f; case "mercenaries" -> 0.15f; case "loners" -> 0.55f; default -> 0.05f; };
        float wa = switch (g.faction) { case "bandits" -> 0.35f; case "mercenaries" -> 0.25f; default -> 0.1f; };
        float wd = "bandits".equals(g.faction) ? 0.4f : 0.05f;
        for (String id : new ArrayList<>(g.lieutenantIds)) {
            Leader c = s.leader(id);
            if (c == null || !c.alive || c.captured) continue;
            float sc = c.competence * wc + c.loyalty * wl + c.ambition * wa + c.dread * wd + s.roll() * 0.08f;
            if (sc > best) { best = sc; pick = c; }
        }
        boolean promoted = pick != null;
        if (pick == null) pick = generateLeader(g, "leader");
        g.lieutenantIds.remove(pick.id);
        if (old != null) g.formerLeaderIds.add(old.id);
        pick.role = "leader";
        List<String> titles = Json.strList(Doctrine.faction(g.faction), "titles");
        if (!titles.isEmpty()) pick.title = titles.get((int) (s.roll() * titles.size()));
        pick.rank = Math.max(pick.rank, 16);
        g.leaderId = pick.id;
        // the successor inherits the group - its assets, what the group knows, its treaties and its enemies - and none
        // of the predecessor's personal memories or temperament. Their own view of the player starts from what the
        // group knows and from what the change of leadership means to them.
        Relation pr = pick.playerRelation, gr = g.playerRelation;
        pr.favor = gr.favor * 0.4f; pr.trust = gr.trust * 0.3f; pr.respect = gr.respect * 0.5f; pr.hostility = gr.hostility * 0.5f; pr.fear = gr.fear * 0.5f; pr.debt = 0;
        boolean playerDidIt = false;
        for (Memory m : g.groupMemories) if ("KILL_LEADER".equals(m.type) && RivalryState.PLAYER.equals(m.subject) && m.known() && s.tick - m.tick <= 4) playerDidIt = true;
        String mood;
        if (playerDidIt && pick.loyalty >= 0.6f) { pr.hostility = clamp(pr.hostility + 40); pr.favor = clamp(pr.favor - 30); pr.trust = clamp(pr.trust - 20); mood = "has sworn to answer for " + (old == null ? "the last leader" : old.name); }
        else if (playerDidIt && pick.ambition >= 0.6f && pick.loyalty < 0.45f) { pr.respect = clamp(pr.respect + 20); pr.favor = clamp(pr.favor + 15); pr.debt = 15; mood = "owes the vacancy to the player and knows it"; }
        else if (playerDidIt) { pr.fear = clamp(pr.fear + 20); pr.hostility = clamp(pr.hostility + 15); mood = "is wary of whoever killed the last one"; }
        else mood = "takes over";
        // the doctrine as the new leader applies it; cohesion pays for the change
        bendDoctrine(g, pick);
        g.cohesion = Math.max(0.15f, g.cohesion - (promoted ? 0.08f : 0.2f));
        note(g, "succession: " + pick.title + " " + pick.name + " (" + String.join(", ", pick.traits) + ") " + mood + (promoted ? "; promoted from within" : "; brought in from outside"));
        emit("SUCCESSION", pick.id, g.id, g.homeDistrict, promoted ? "promotion" : "appointment", mood, List.of(), List.of(g.id));
        s.notices.add(g.name + " have a new leader: " + pick.title + " " + pick.name + ", who " + mood + ".");
        // treaties stand, but the new leader reads them again: an early review, and stricter terms if they do not trust the other party
        for (String tid : g.treatyIds) {
            Treaty t = s.treaties.get(tid);
            if (t == null || !t.active()) continue;
            t.reviewTick = s.tick + 2;
            t.signedWith = pick.id;
            if (pr.trust < Doctrine.alliance("trust", 40) * 0.5f) { t.tribute = Math.round(t.tribute * 1.25f) + 100; t.obligations.add("renegotiated under " + pick.name + ": tribute raised"); s.notices.add(g.name + ": the new leader is reviewing your " + t.kind + " and has raised its price."); }
        }
        // a divided group does not always stay one group: a passed-over lieutenant with ambition takes followers and goes
        if (g.cohesion < 0.3f && g.size >= 16) for (String id : new ArrayList<>(g.lieutenantIds)) {
            Leader rival = s.leader(id);
            if (rival == null || !rival.alive || rival.ambition < 0.6f) continue;
            schism(g, rival);
            break;
        }
        return pick;
    }

    private void schism(Group g, Leader rival) {
        Group n = new Group();
        n.id = "grp-" + s.nextGroup++;
        n.faction = g.faction; n.createdTick = s.tick; n.seed = (long) (s.roll() * (1L << 40));
        n.homeDistrict = g.homeDistrict; n.territoryIds.add(g.homeDistrict);
        n.size = n.startSize = Math.max(6, g.size / 3);
        g.size -= n.size;
        for (Map.Entry<String, Float> e : new ArrayList<>(g.resources.entrySet())) { n.resources.put(e.getKey(), e.getValue() / 3f); g.resources.put(e.getKey(), e.getValue() * 2f / 3f); }
        n.morale = 0.6f; n.cohesion = 0.75f; n.colour = g.colour; n.emblem = g.emblem + "'";
        n.name = rival.name.substring(rival.name.lastIndexOf(' ') + 1) + "'s " + (g.faction.equals("government") ? "Detachment" : g.faction.equals("mercenaries") ? "Outfit" : g.faction.equals("loners") ? "Company" : "Crew");
        n.doctrineState.putAll(g.doctrineState);
        // what the group knew, both halves know; where the group stood with the player, both start from
        for (Memory m : g.groupMemories) if (m.pivotal) n.groupMemories.add(m);
        RivalryState.copyInto(g.playerRelation, n.playerRelation);
        for (Map.Entry<String, Relation> e : g.relationsByGroup.entrySet()) n.relationsByGroup.put(e.getKey(), e.getValue().copy());
        s.groups.put(n.id, n);
        g.lieutenantIds.remove(rival.id);
        rival.groupId = n.id; rival.role = "leader"; n.leaderId = rival.id;
        bendDoctrine(n, rival);
        Relation a = rel(g, n.id), b = rel(n, g.id);
        a.hostility = 30; a.favor = -20; a.trust = -30; b.hostility = 25; b.favor = -15; b.trust = -25;
        for (Group o : s.active()) if (o != g && o != n) rel(o, n.id);
        note(g, "schism: " + rival.name + " left with " + n.size + " and founded " + n.name);
        note(n, "founded by " + rival.name + " in a split from " + g.name);
        emit("SUCCESSION", rival.id, n.id, n.homeDistrict, "schism", "split from " + g.name, List.of(), List.of(n.id, g.id));
        s.notices.add(g.name + " have split: " + rival.name + " now leads " + n.name + ".");
    }

    // ================================================================== strategy: utility, offscreen action (sections 3.6 and 7)
    /** Advances the world by n strategic ticks. Returns what happened that the player would hear about. */
    public List<String> tick(int n, Wallet wallet) {
        List<String> news = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            s.tick++;
            deliverRumours();
            decay();
            reviewTreaties(wallet, news);
            for (Group g : new ArrayList<>(s.groups.values())) {
                if (!g.active) continue;
                succeed(g);                                    // invariant 2: a leader before the group acts
                upkeep(g);
                String did = decide(g);
                if (did != null && !did.isEmpty()) news.add(g.name + ": " + did);
                jobs(g);
            }
            mergeAndFold();
        }
        while (news.size() > 40) news.remove(0);
        return news;
    }

    private void decay() {
        float df = Doctrine.world("relationDecayPerTick", 0.35f), dh = Doctrine.world("hostilityDecayPerTick", 0.5f), dfe = Doctrine.world("fearDecayPerTick", 0.6f);
        for (Group g : s.groups.values()) {
            boolean bound = treaty(g, null);
            fade(g.playerRelation, bound ? 0 : df, dh, dfe);
            for (Relation r : g.relationsByGroup.values()) fade(r, df * 0.5f, dh * 0.5f, dfe);
            if (s.tick % 8 == 0) { for (Relation r : g.relationsByGroup.values()) r.repeats.replaceAll((k, v) -> Math.max(0, v - 1)); g.playerRelation.repeats.replaceAll((k, v) -> Math.max(0, v - 1)); }
            for (String k : new ArrayList<>(g.doctrineState.keySet())) if (k.equals("hunt_player") || k.equals("lockdown") || k.equals("fortified") || k.equals("withdrawn") || k.equals("aid")) g.doctrineState.put(k, Math.max(0, g.state(k) - 1));
            if ("government".equals(g.faction)) { g.doctrineState.put("alert", Math.max(0.05f, g.state("alert") - 0.04f)); g.doctrineState.put("violations", Math.max(0, g.state("violations") - 0.25f)); }
        }
        for (Leader l : s.leaders.values()) if (l.alive) fade(l.playerRelation, df * 0.5f, dh * 0.6f, dfe);
        for (String f : new ArrayList<>(s.factionReputation.keySet())) { float v = s.factionReputation.get(f); s.factionReputation.put(f, Math.abs(v) < 0.1f ? 0 : v * 0.995f); }
    }

    /** Feelings cool: mild favor drifts back toward indifference, hostility and fear fade. Earned standing (Trusted and beyond) and hatred do not evaporate. */
    private static void fade(Relation r, float favor, float hostility, float fear) {
        if (Math.abs(r.favor) < 50) r.favor = toward(r.favor, 0, favor);
        r.hostility = toward(r.hostility, r.favor < -50 ? 40 : 0, hostility);
        r.fear = toward(r.fear, 0, fear);
        r.debt = Math.max(0, r.debt - 0.15f);
    }

    private static float toward(float v, float target, float step) { return v > target ? Math.max(target, v - step) : Math.min(target, v + step); }

    /** What keeping a group alive costs each tick, and what its way of life brings in. */
    private void upkeep(Group g) {
        g.res("supplies", -g.size * 0.03f);
        switch (g.faction) {
            case "government" -> { g.res("supplies", 1.2f * g.state("reserves") + 0.6f); g.res("ammo", 0.6f); g.doctrineState.put("reserves", Math.min(1f, g.state("reserves") + 0.02f)); }
            case "mercenaries" -> { g.res("coins", -0.4f); g.doctrineState.put("contract", Math.min(1f, g.state("contract") + 0.12f)); }
            case "loners" -> { g.doctrineState.put("hunger", Math.max(0, Math.min(1f, 1 - g.res("supplies") / Math.max(1f, g.size * 0.4f)))); g.doctrineState.put("shelter", Math.min(1f, g.state("shelter") + 0.03f)); }
            default -> { g.doctrineState.put("loot", Math.max(0f, g.state("loot") - 0.05f)); g.doctrineState.put("heat", Math.max(0f, g.state("heat") - 0.06f)); g.doctrineState.put("prey", Math.min(1f, g.state("prey") + 0.04f)); }
        }
        if (g.res("supplies") <= 0.01f) { g.morale = Math.max(0.05f, g.morale - 0.06f); g.cohesion = Math.max(0.05f, g.cohesion - 0.03f); if (s.roll() < 0.25f && g.size > 4) { g.size--; note(g, "someone walked away hungry"); } }
        else g.morale = toward(g.morale, 0.7f, g.adapted("night_watch") > 0 ? 0.01f : 0.02f);
        g.cohesion = toward(g.cohesion, 0.7f, 0.01f);
        if (g.adapted("medics") > 0 && g.res("medicine") >= 0.3f && g.size < g.startSize && s.roll() < 0.3f) { g.res("medicine", -0.3f); g.size++; }
    }

    /** The strongest grievance the group knows of against another group (0 .. 1), and against whom. */
    private Group grievance(Group g, float[] out) {
        Group worst = null;
        float w = 0;
        for (Group o : s.active()) {
            if (o == g) continue;
            Relation r = rel(g, o.id);
            float v = Math.max(0, r.hostility) / 100f;
            if (v > w && s.treatyBetween(g, o) == null) { w = v; worst = o; }
        }
        if (out != null) out[0] = w;
        return worst;
    }

    private float strength(Group g) {
        Leader l = s.leaderOf(g);
        return g.size * (0.4f + 0.6f * g.morale) * (0.8f + 0.4f * (l == null ? 0.5f : l.competence)) * (1 + g.res("gear") / 40f) * (1 + Math.min(10, g.res("intel")) / 40f) * (0.9f + 0.2f * g.cohesion);
    }

    /** How pressing each consideration is for this group right now, 0 .. 1.5: the contextual half of the utility formula. */
    private Map<String, Float> needs(Group g) {
        Map<String, Float> n = new LinkedHashMap<>();
        float[] gv = new float[1];
        Group enemy = grievance(g, gv);
        float threat = 0;
        for (Group o : s.active()) {
            if (o == g) continue;
            boolean near = false;
            for (String a : g.territoryIds) for (String b : o.territoryIds) if (loc.distance(a, b) <= 1) near = true;
            if (near) threat = Math.max(threat, Math.max(0, rel(o, g.id).hostility) / 100f * Math.min(1.5f, strength(o) / Math.max(1f, strength(g))));
        }
        float playerThreat = Math.max(0, g.playerRelation.hostility) / 100f * (0.5f + Math.max(0, g.playerRelation.fear) / 100f);
        float supplyNeed = 1 - Math.min(1, g.res("supplies") / Math.max(1f, g.size * 0.5f));
        float manpower = 1 - Math.min(1f, g.size / (float) Math.max(1, g.startSize));
        n.put("survival", Math.min(1.5f, 0.2f + supplyNeed * 0.7f + (1 - g.morale) * 0.5f + threat * 0.3f + manpower * 0.3f));
        n.put("security", Math.min(1.5f, 0.25f + threat + playerThreat * 0.5f + manpower * 0.4f + g.state("alert") * 0.5f));
        n.put("profit", Math.min(1.5f, 0.3f + (1 - Math.min(1, g.res("coins") / 14f)) * 0.7f + g.state("contract") * 0.5f));
        n.put("loot", Math.min(1.5f, 0.2f + (1 - g.state("loot")) * 0.5f + g.state("prey") * 0.6f + supplyNeed * 0.3f));
        n.put("order", Math.min(1.5f, 0.3f + g.state("alert") * 0.8f + Math.min(1, g.state("violations") / 4f) * 0.6f + (1 - g.state("control")) * 0.4f));
        float shame = 0;
        for (Memory m : g.groupMemories) if (("DEFEAT".equals(m.type) || "GROUP_RAID".equals(m.type) || "KILL_LEADER".equals(m.type)) && m.known() && s.tick - m.tick < 12) shame += 0.25f;
        n.put("reputation", Math.min(1.5f, 0.3f + shame));
        n.put("revenge", Math.min(1.5f, Math.max(gv[0], playerThreat) * 1.3f));
        n.put("bonds", Math.min(1.5f, 0.2f + (1 - g.cohesion) * 0.8f + manpower * 0.5f));
        return n;
    }

    /** Whom an action would be aimed at, or null when it has no target (or none exists, which makes the action impossible). */
    private Group target(Group g, String action) {
        switch (action) {
            case "retaliate": { float[] v = new float[1]; Group e = grievance(g, v); return v[0] >= 0.35f ? e : null; }
            case "raid": case "ambush": case "extort": {
                // predation: expected loot times how weak they look, less what it would cost and who would come back for it
                Group best = null;
                float bs = 0.05f;
                for (Group o : s.active()) {
                    if (o == g || o.faction.equals(g.faction) || s.treatyBetween(g, o) != null) continue;
                    int d = 9;
                    for (String a : g.territoryIds) for (String b : o.territoryIds) d = Math.min(d, loc.distance(a, b));
                    if (d > 1) continue;
                    float lootv = (o.res("supplies") + o.res("coins") * 2 + o.res("gear")) / 40f, weak = strength(g) / Math.max(1f, strength(g) + strength(o));
                    float back = Math.max(0, rel(g, o.id).fear) / 100f + ("government".equals(o.faction) ? 0.15f : 0);
                    float sc = lootv * weak - (1 - weak) * 0.3f - back * 0.4f;
                    if (sc > bs) { bs = sc; best = o; }
                }
                return best;
            }
            case "contest": {
                Group best = null;
                float bs = 0;
                for (Group o : s.active()) {
                    if (o == g || s.treatyBetween(g, o) != null || rel(g, o.id).hostility < 20) continue;
                    int d = 9;
                    for (String a : g.territoryIds) for (String b : o.territoryIds) d = Math.min(d, loc.distance(a, b));
                    if (d > 1) continue;
                    float sc = strength(g) / Math.max(1f, strength(o));
                    if (sc > 1.15f && sc > bs) { bs = sc; best = o; }
                }
                return best;
            }
            case "trade": {
                Group best = null;
                float bf = -1;
                for (Group o : s.active()) { if (o == g) continue; Relation r = rel(g, o.id); if (r.hostility < 25 && r.favor > bf) { bf = r.favor; best = o; } }
                return best;
            }
            case "negotiate": {
                // talk to whoever it costs most to keep fighting
                Group best = null;
                float bs = 10;
                for (Group o : s.active()) { if (o == g || s.treatyBetween(g, o) != null) continue; Relation r = rel(g, o.id); float sc = r.hostility * Math.min(1.5f, strength(o) / Math.max(1f, strength(g))); if (sc > bs && r.hostility < 80) { bs = sc; best = o; } }
                return best;
            }
            case "pursue_contract": {
                // a sponsor with money and an enemy: the contract is on that enemy
                for (Group sp : s.active()) {
                    if (sp == g || sp.res("coins") < 5 || rel(g, sp.id).hostility > 30) continue;
                    float[] v = new float[1];
                    Group e = grievance(sp, v);
                    if (e != null && e != g && v[0] >= 0.4f && s.treatyBetween(g, e) == null) { contractSponsor = sp; return e; }
                }
                contractSponsor = null;
                return null;
            }
            default: return null;
        }
    }

    private Group contractSponsor;

    /**
     * utility(a) = sum_i( factionWeight[i] x leaderModifier[i] x contextualValue[i,a] ) + relationshipEffects(a)
     *              - riskCost(a) - resourceCost(a)
     * Illegal and impossible actions are filtered first; the choice is a seeded, weighted pick among the best few.
     * Returns a line of news, or null when what the group did is nobody else's business.
     */
    public String decide(Group g) {
        Map<String, Float> need = needs(g);
        float riskTol = g.doctrineState.getOrDefault("risk", Doctrine.risk(g.faction));
        g.lastUtilities.clear();
        List<String> names = new ArrayList<>();
        List<Float> utils = new ArrayList<>();
        List<Group> targets = new ArrayList<>();
        for (String a : Doctrine.actions(g.faction)) {
            Map<String, Object> def = Doctrine.action(a);
            if (def.isEmpty()) continue;
            // ---- filter: cooldowns, resources, doctrine, treaties, whether there is anyone to do it to
            if (g.cooldownUntil.getOrDefault(a, -1L) > s.tick) continue;
            Map<String, Object> cost = Json.obj(def, "cost");
            boolean afford = true;
            float resourceCost = 0;
            for (Map.Entry<String, Object> c : cost.entrySet()) if (c.getValue() instanceof Number nv) { float have = g.res(c.getKey()); if (have < nv.floatValue()) afford = false; resourceCost += nv.floatValue() / Math.max(1f, have) * 0.5f; }
            if (!afford) continue;
            boolean needsTarget = Set.of("retaliate", "raid", "ambush", "extort", "contest", "trade", "negotiate", "pursue_contract").contains(a);
            Group tgt = needsTarget ? target(g, a) : null;
            boolean huntPlayer = false;
            if (needsTarget && tgt == null) {
                // the player can be the grievance: a hunt is prepared, to be played out when the player is next in reach
                if (("retaliate".equals(a) || "ambush".equals(a)) && g.playerRelation.hostility >= 55 && !treaty(g, null)) huntPlayer = true;
                else continue;
            }
            if ("lockdown".equals(a) && g.state("alert") < 0.6f) continue;
            if ("inspect".equals(a) && g.state("alert") < 0.25f && g.state("violations") < 1) continue;
            if ("recruit".equals(a) && g.size >= Math.round(Doctrine.world("maxSize", 50))) continue;
            if ("relocate".equals(a) && need.get("security") < 0.9f) continue;
            if ("withdraw".equals(a) && g.morale > 0.45f && need.get("survival") < 0.8f) continue;
            // ---- value
            float u = 0;
            Map<String, Object> values = Json.obj(def, "values");
            for (String c : Doctrine.considerations()) {
                float base = (float) Json.num(values, c, 0);
                if (base == 0) continue;
                u += g.doctrineState.getOrDefault("w_" + c, Doctrine.weight(g.faction, c)) * base * need.getOrDefault(c, 0.3f);
            }
            // ---- relationships: nobody raids a friend lightly, and talking to someone you already half trust is easier
            float relEffect = 0;
            if (tgt != null) {
                Relation r = rel(g, tgt.id);
                if (Set.of("retaliate", "raid", "ambush", "extort", "contest").contains(a)) relEffect += r.hostility / 100f * 0.5f - Math.max(0, r.favor) / 100f * 0.8f - Math.max(0, r.fear) / 100f * 0.3f;
                else relEffect += Math.max(0, r.favor) / 100f * 0.3f - Math.max(0, r.hostility) / 100f * ("negotiate".equals(a) ? 0f : 0.3f);
            }
            if (huntPlayer) relEffect += g.playerRelation.hostility / 100f * 0.6f - Math.max(0, g.playerRelation.fear) / 100f * 0.35f;
            float danger = tgt == null ? 1 : Math.min(2f, strength(tgt) / Math.max(1f, strength(g)));
            float riskCost = (float) Json.num(def, "risk", 0.1) * (1 - riskTol) * (0.6f + danger * 0.6f) * 1.6f;
            u += relEffect - riskCost - resourceCost;
            g.lastUtilities.put(a + (tgt != null ? " -> " + tgt.name : huntPlayer ? " -> the player" : ""), u);
            names.add(a); utils.add(u); targets.add(huntPlayer ? null : tgt);
        }
        if (names.isEmpty()) { g.lastAction = "defend"; g.lastActionTick = s.tick; return null; }
        // ---- weighted, seeded choice among the top few: the same situation does not always produce the same move
        int top = Math.max(1, Math.round(Doctrine.world("topActions", 3)));
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) order.add(i);
        order.sort((x, y) -> Float.compare(utils.get(y), utils.get(x)));
        float umax = utils.get(order.get(0)), sharp = Doctrine.world("choiceSharpness", 2f), total = 0;
        int n = Math.min(top, order.size());
        float[] w = new float[n];
        for (int i = 0; i < n; i++) { w[i] = (float) Math.exp(sharp * (utils.get(order.get(i)) - umax)); total += w[i]; }
        float roll = s.roll() * total;
        int chosen = order.get(0);
        for (int i = 0; i < n; i++) { roll -= w[i]; if (roll <= 0) { chosen = order.get(i); break; } }
        String a = names.get(chosen);
        Group tgt = targets.get(chosen);
        Map<String, Object> def = Doctrine.action(a);
        for (Map.Entry<String, Object> c : Json.obj(def, "cost").entrySet()) if (c.getValue() instanceof Number nv) g.res(c.getKey(), -nv.floatValue());
        int cd = Json.integer(def, "cooldown", 0);
        if (cd > 0) g.cooldownUntil.put(a, s.tick + cd);
        g.lastAction = a + (tgt != null ? " " + tgt.id : "");
        g.lastActionTick = s.tick;
        g.objective = a;
        return perform(g, a, tgt);
    }

    /** Carries a decision out off screen: a few numbers move, and anything that happened between groups is an event on the record. */
    private String perform(Group g, String a, Group t) {
        switch (a) {
            case "patrol" -> { g.res("intel", 0.5f); if ("government".equals(g.faction)) g.doctrineState.put("control", Math.min(1f, g.state("control") + 0.04f)); return null; }
            case "defend" -> { g.doctrineState.put("fortified", 2f); return null; }
            case "recruit" -> { int n = 1 + (int) (s.roll() * 3); g.size = Math.min(Math.round(Doctrine.world("maxSize", 50)), g.size + n); g.cohesion = Math.max(0.2f, g.cohesion - 0.02f); note(g, "took on " + n + " (now " + g.size + ")"); return null; }
            case "scavenge" -> {
                float got = 2 + s.roll() * 4;
                g.res("supplies", got); g.res("medicine", s.roll() * 0.8f);
                if (s.roll() < 0.12f && g.size > 4) { g.size--; g.morale = Math.max(0.1f, g.morale - 0.04f); note(g, "scavenging party came back one short (+" + Math.round(got) + " supplies)"); }
                else note(g, "scavenged " + Math.round(got) + " supplies");
                return null;
            }
            case "trade" -> {
                if (t == null) return null;
                g.res("coins", 1.5f); g.res("supplies", 1.5f); t.res("coins", 1f); t.res("supplies", 1f);
                emit("TRADE", g.id, t.id, t.homeDistrict, "barter", "goods exchanged", List.of(), List.of(t.id));
                return null;
            }
            case "negotiate" -> {
                if (t == null) return null;
                Relation ab = rel(g, t.id), ba = rel(t, g.id);
                float before = ab.hostility;
                ab.hostility = Math.max(0, ab.hostility - 12); ba.hostility = Math.max(0, ba.hostility - 9); ab.favor = clamp(ab.favor + 4); ba.favor = clamp(ba.favor + 3);
                emit("GROUP_AID", g.id, t.id, t.homeDistrict, "talks", "talks held: hostility " + Math.round(before) + " -> " + Math.round(ab.hostility), List.of(), List.of(t.id));
                note(g, "talks with " + t.name);
                if (ab.hostility < 15 && ab.favor >= 15 && ba.hostility < 15) { g.cooldownUntil.put("pact:" + t.id, s.tick + 14); t.cooldownUntil.put("pact:" + g.id, s.tick + 14); note(g, "pact with " + t.name + " for 14 ticks"); note(t, "pact with " + g.name + " for 14 ticks"); }
                return "opened talks with " + t.name;
            }
            case "inspect" -> { g.doctrineState.put("control", Math.min(1f, g.state("control") + 0.06f)); g.res("intel", 1f); note(g, "inspections on the approaches (alert " + Math.round(g.state("alert") * 100) + "%)"); return g.state("alert") >= 0.4f ? "is stopping and searching in " + loc.name(g.homeDistrict) : null; }
            case "lockdown" -> { g.doctrineState.put("lockdown", 4f); g.doctrineState.put("reserves", Math.max(0f, g.state("reserves") - 0.2f)); note(g, "LOCKDOWN of " + loc.name(g.homeDistrict) + " for 4 ticks: reserves committed"); return "has locked down " + loc.name(g.homeDistrict); }
            case "relocate" -> {
                String best = g.homeDistrict;
                float bs = -99;
                for (String l : loc.ids()) {
                    float sc = loc.influence(l, Doctrine.gameKey(g.faction));
                    for (Group o : s.active()) if (o != g && o.territoryIds.contains(l)) sc -= Math.max(0, rel(o, g.id).hostility) / 100f;
                    if (!l.equals(g.homeDistrict) && sc > bs) { bs = sc; best = l; }
                }
                if (best.equals(g.homeDistrict)) return null;
                String from = g.homeDistrict;
                g.territoryIds.remove(from); if (!g.territoryIds.contains(best)) g.territoryIds.add(best);
                g.homeDistrict = best;
                if ("loners".equals(g.faction)) g.doctrineState.put("shelter", 0.35f);
                note(g, "relocated from " + loc.name(from) + " to " + loc.name(best));
                return "has moved to " + loc.name(best);
            }
            case "withdraw" -> { g.doctrineState.put("withdrawn", 2f); g.morale = Math.min(1f, g.morale + 0.08f); note(g, "pulled back to recover"); return null; }
            case "extort" -> {
                if (t == null) return null;
                float fear = Math.max(0, rel(t, g.id).fear) / 100f, weak = strength(g) / Math.max(1f, strength(g) + strength(t));
                if (s.roll() < 0.25f + fear * 0.4f + weak * 0.4f) {
                    float take = Math.min(t.res("coins"), 2 + s.roll() * 3);
                    t.res("coins", -take); g.res("coins", take); g.doctrineState.put("loot", Math.min(1f, g.state("loot") + 0.2f));
                    emit("GROUP_RAID", g.id, t.id, t.homeDistrict, "extortion", "paid " + Math.round(take) + " to be left alone", List.of(), List.of(t.id));
                    note(g, "took a toll of " + Math.round(take) + " off " + t.name);
                    return "is taking a toll from " + t.name;
                }
                emit("GROUP_RAID", g.id, t.id, t.homeDistrict, "extortion", "refused", List.of(), List.of(t.id));
                note(g, t.name + " refused to pay");
                return null;
            }
            case "pursue_contract" -> {
                if (t == null) return null;
                Group sp = contractSponsor;
                float payout = 4 + s.roll() * 5, acceptable = Math.max(1, g.size * 0.12f);
                int before = g.size;
                String res = conflict(g, t, "contract", 0.6f);
                int lost = before - g.size;
                boolean won = res.contains("-> won");
                if (won && sp != null) { float pay = Math.min(sp.res("coins"), payout); sp.res("coins", -pay); g.res("coins", pay); rel(sp, g.id).favor = clamp(rel(sp, g.id).favor + 6); rel(sp, g.id).trust = clamp(rel(sp, g.id).trust + 5); }
                g.doctrineState.put("contract", 0f);
                g.doctrineState.put("losses", (float) lost);
                note(g, "contract for " + (sp == null ? "a client" : sp.name) + " against " + t.name + ": " + res + (lost > acceptable ? "; losses over the " + Math.round(acceptable) + " the contract allowed - standing down" : ""));
                if (lost > acceptable) { g.cooldownUntil.put("pursue_contract", s.tick + 6); g.doctrineState.put("withdrawn", 2f); }
                return (won ? "completed a contract against " : "failed a contract against ") + t.name;
            }
            case "raid", "ambush", "retaliate", "contest" -> {
                if (t == null) {
                    // the grievance is the player: nothing is resolved out of sight - the group readies a party and waits for the chance
                    g.doctrineState.put("hunt_player", 6f);
                    note(g, "sent a party to look for the player");
                    return null;
                }
                String res = conflict(g, t, a, "contest".equals(a) ? 1f : "ambush".equals(a) ? 0.5f : 0.75f);
                boolean won = res.contains("-> won");
                if (won && "contest".equals(a)) {
                    String ground = null;
                    for (String l : t.territoryIds) for (String m : g.territoryIds) if (ground == null && loc.distance(l, m) <= 1 && !g.territoryIds.contains(l)) ground = l;
                    if (ground != null && t.territoryIds.size() > 1) { t.territoryIds.remove(ground); g.territoryIds.add(ground); if (t.homeDistrict.equals(ground)) t.homeDistrict = t.territoryIds.get(0); res += "; took " + loc.name(ground); }
                    else if (ground != null) { g.territoryIds.add(ground); res += "; now shares " + loc.name(ground); }
                }
                if ("bandits".equals(g.faction)) { g.doctrineState.put("loot", Math.min(1f, g.state("loot") + (won ? 0.35f : 0))); g.doctrineState.put("heat", Math.min(1f, g.state("heat") + 0.25f)); g.doctrineState.put("prey", Math.max(0f, g.state("prey") - 0.3f)); }
                note(g, a + " on " + t.name + ": " + res);
                return (won ? ("contest".equals(a) ? "pushed into the ground of " : "retaliate".equals(a) ? "struck back at " : "raided ") : "was beaten off by ") + t.name;
            }
            default -> { return null; }
        }
    }

    /**
     * An off-screen fight, resolved from what each side brings: numbers, morale, leadership, equipment, intelligence and
     * cohesion, with the defender's advantage and one seeded roll. The arithmetic is in the outcome of the event it
     * records, so anyone can check why it went the way it did. No actors are created.
     */
    public String conflict(Group a, Group d, String kind, float scale) {
        float sa = strength(a), sd = strength(d) * 1.2f * (d.state("fortified") > 0 ? 1.2f : 1f) * (d.state("withdrawn") > 0 ? 1.1f : 1f);
        float p = sa / Math.max(0.01f, sa + sd), roll = s.roll();
        boolean won = roll < p;
        int la = Math.max(0, Math.round(a.size * (won ? 0.03f + s.roll() * 0.06f : 0.08f + s.roll() * 0.12f) * scale));
        int ld = Math.max(0, Math.round(d.size * (won ? 0.10f + s.roll() * 0.14f : 0.03f + s.roll() * 0.05f) * scale));
        la = Math.min(la, Math.max(0, a.size - 3)); ld = Math.min(ld, Math.max(0, d.size - 3));
        a.size -= la; d.size -= ld;
        float took = 0;
        if (won) { took = Math.min(d.res("supplies"), d.res("supplies") * (0.15f + s.roll() * 0.2f) * scale); d.res("supplies", -took); a.res("supplies", took); float coins = d.res("coins") * 0.2f * scale; d.res("coins", -coins); a.res("coins", coins); }
        a.morale = Math.max(0.05f, Math.min(1f, a.morale + (won ? 0.06f : -0.1f)));
        d.morale = Math.max(0.05f, Math.min(1f, d.morale + (won ? -0.12f : 0.07f)));
        String out = String.format("strength %.1f against %.1f, chance %.2f, roll %.2f -> %s; lost %d, they lost %d%s", sa, sd, p, roll, won ? "won" : "repelled", la, ld, won ? String.format("; took %.0f supplies", took) : "");
        emit("GROUP_RAID", a.id, d.id, d.homeDistrict, kind, out, List.of(), List.of(d.id));
        note(d, (won ? "hit by " : "beat off ") + a.name + " (" + kind + "): lost " + ld);
        return out;
    }

    /** Loners who have dwindled join friends; a group that is gone is closed, its leader and history kept on record. */
    private void mergeAndFold() {
        for (Group g : new ArrayList<>(s.active())) {
            if ("loners".equals(g.faction) && g.size < 8) for (Group o : s.ofFaction("loners")) {
                if (o == g || rel(g, o.id).favor < 20 || rel(o, g.id).favor < 20 || o.size + g.size > Doctrine.world("maxSize", 50)) continue;
                o.size += g.size;
                for (Map.Entry<String, Float> e : g.resources.entrySet()) o.res(e.getKey(), e.getValue());
                g.active = false;
                note(o, "took in what was left of " + g.name + " (" + g.size + ")");
                note(g, "merged into " + o.name);
                s.notices.add(g.name + " have gone to live with " + o.name + ".");
                break;
            }
        }
    }

    // ================================================================== treaties (section 6)
    private void reviewTreaties(Wallet w, List<String> news) {
        for (Treaty t : new ArrayList<>(s.treaties.values())) {
            if (!t.active()) continue;
            Group g = s.group(t.groupId);
            if (g == null || !g.active) { t.status = "expired"; continue; }
            if ("truce".equals(t.kind)) { if (s.tick >= t.reviewTick) { t.status = "expired"; s.notices.add("Your truce with " + g.name + " has run out."); } continue; }
            if (s.tick < t.reviewTick) continue;
            t.reviewTick = s.tick + Math.round(Doctrine.alliance("reviewTicks", 28));
            // the obligation falls due
            if (t.tribute > 0) {
                if (w != null && w.pay(t.tribute)) { t.missed = 0; emit("PAY_TRIBUTE", RivalryState.PLAYER, g.id, g.homeDistrict, "treaty", t.tribute + " cash under the " + t.kind, List.of(), List.of(g.id)); news.add("Paid " + t.tribute + " coins to " + g.name + " under your agreement."); }
                else { t.missed++; emit("BREAK_PROMISE", RivalryState.PLAYER, g.id, g.homeDistrict, "treaty", "missed an obligation (" + t.missed + ")", List.of(), List.of(g.id)); news.add("You missed what you owe " + g.name + " (" + t.missed + ")."); }
                if (t.missed > Doctrine.alliance("maxMissed", 2)) { breakTreaty(t, RivalryState.PLAYER, "obligations unpaid"); continue; }
            }
            // the other party reads the terms again too: an ally keeps its own interests, and can walk away
            Leader l = s.leaderOf(g);
            float trust = l == null ? 0 : l.playerRelation.trust;
            if (g.playerRelation.favor < 30 || trust < -10) { breakTreaty(t, g.id, g.playerRelation.favor < 30 ? "the group no longer sees the point" : "the leader does not trust you"); continue; }
            if (l != null && l.has("greedy") && g.playerRelation.favor < 60 && s.roll() < 0.2f) { breakTreaty(t, g.id, l.name + " found a better offer"); }
        }
    }

    /** Ends a treaty with a culprit. Breaking your word is an event like any other: those who know of it remember. */
    public void breakTreaty(Treaty t, String by, String why) {
        if (!t.active()) return;
        t.status = "broken"; t.brokenBy = by;
        Group g = s.group(t.groupId);
        if (g == null) return;
        if (RivalryState.PLAYER.equals(by)) {
            emit("BETRAY", RivalryState.PLAYER, g.id, g.homeDistrict, "treaty", why, List.of(g.leaderId), List.of(g.id, g.leaderId));
            g.doctrineState.put("hunt_player", 8f);
            s.notices.add("Your " + t.kind + " with " + g.name + " is broken: " + why + ". They will not forget it.");
        } else {
            emit("BETRAY", g.id, g.id, g.homeDistrict, "treaty", why, List.of(), List.of(g.id));
            g.playerRelation.favor = clamp(g.playerRelation.favor - 15);
            note(g, "walked away from the " + t.kind + " with the player: " + why);
            s.notices.add(g.name + " have broken their " + t.kind + " with you: " + why + ".");
        }
    }

    // ================================================================== jobs (section 6)
    private void jobs(Group g) {
        for (Job j : new ArrayList<>(g.jobs)) {
            if ("open".equals(j.status) && s.tick > j.deadlineTick) g.jobs.remove(j);
            if ("taken".equals(j.status) && s.tick > j.deadlineTick) { j.status = "failed"; emit("BREAK_PROMISE", RivalryState.PLAYER, g.id, g.homeDistrict, "job", "took the work and did not do it: " + j.title, List.of(), List.of(g.id)); g.jobs.remove(j); }
            if ("paid".equals(j.status) || "failed".equals(j.status)) g.jobs.remove(j);
        }
        if (s.tick % 3 != 0 || g.jobs.size() >= 2 || disposition(g) < 0) return;
        List<String> kinds = Json.strList(Doctrine.faction(g.faction), "jobs");
        if (kinds.isEmpty()) return;
        String kind = kinds.get((int) (s.roll() * kinds.size()));
        Job j = new Job();
        j.id = "job-" + s.nextJob++; j.groupId = g.id; j.kind = kind; j.offeredTick = s.tick; j.deadlineTick = s.tick + 16; j.district = g.homeDistrict;
        float[] v = new float[1];
        Group enemy = grievance(g, v);
        switch (kind) {
            case "eliminate" -> { if (enemy == null) return; j.targetGroupId = enemy.id; j.need = 3; j.payout = 1400; j.favor = 1; j.title = "Thin out " + enemy.name; j.text = "Kill three of " + enemy.name + ". " + g.name + " will know when it is done."; j.district = enemy.homeDistrict; }
            case "raid" -> { if (enemy == null) return; j.targetGroupId = enemy.id; j.need = 4; j.payout = 1800; j.title = "Hit " + enemy.name; j.text = "Put four of " + enemy.name + " down in " + loc.name(enemy.homeDistrict) + " and you get a cut."; j.district = enemy.homeDistrict; }
            case "supply" -> { j.need = 1; j.payout = 300; j.title = "Bring supplies"; j.text = "Food and medicine are short. Deliver supplies (hand over $500 worth) and " + g.name + " will remember it."; }
            case "rescue" -> { j.need = 1; j.payout = 600; j.title = "Bring one of ours back"; j.text = "Treat or revive a wounded member of " + g.name + " in the field."; }
            case "patrol", "report", "escort", "smuggle", "recover" -> {
                j.need = 1; j.payout = kind.equals("smuggle") ? 1500 : kind.equals("recover") ? 1300 : 900;
                j.title = switch (kind) { case "patrol" -> "Walk the perimeter"; case "report" -> "Report on the district"; case "escort" -> "Escort through " + loc.name(j.district); case "smuggle" -> "Run a load out"; default -> "Recover the asset"; };
                j.text = "Deploy to " + loc.name(j.district) + " and extract alive.";
            }
            default -> { return; }
        }
        g.jobs.add(j);
    }

    /** Game progress toward the jobs the player has taken: called when something a job could be counting happens. */
    public void progress(String what, String groupId, String district, Wallet w) {
        for (Group g : s.active()) for (Job j : g.jobs) {
            if (!"taken".equals(j.status)) continue;
            boolean hit = switch (j.kind) {
                case "eliminate", "raid" -> "kill".equals(what) && j.targetGroupId.equals(groupId);
                case "rescue" -> ("rescue".equals(what) || "heal".equals(what)) && j.groupId.equals(groupId);
                case "patrol", "report", "escort", "smuggle", "recover" -> "extract".equals(what) && j.district.equals(district);
                default -> false;
            };
            if (!hit) continue;
            j.have = Math.min(j.need, j.have + 1);
            if (j.have >= j.need) {
                j.status = "paid";
                if (w != null) w.receive(j.payout);
                emit("COMPLETE_JOB", RivalryState.PLAYER, g.id, g.homeDistrict, j.kind, j.title, List.of(), List.of(g.id));
                s.notices.add("Job done for " + g.name + ": " + j.title + " (+" + j.payout + " cash).");
            }
        }
    }

    // ================================================================== the player's side (section 6)
    private Result need(Group g, String what) {
        float min = (float) Json.num(Doctrine.interaction(what), "minFavor", -100);
        if (g == null || !g.active) return new Result(false, "They are gone.");
        if (g.playerRelation.favor < min) return new Result(false, g.name + " will not hear of it (" + Doctrine.status(g.playerRelation.favor).name() + "; needs favor " + Math.round(min) + ").");
        return null;
    }

    /** What the leader says when the player comes to them: chosen from what the leader remembers, then from standing. */
    public Result talk(Group g) {
        Result no = need(g, "talk");
        if (no != null) return no;
        Leader l = s.leaderOf(g);
        if (l != null) { l.encounters++; l.lastSeenTick = s.tick; }
        return new Result(true, (l == null ? g.name : l.title + " " + l.name) + ": \"" + line(l, g) + "\"");
    }

    public Result gift(Group g, Wallet w) {
        int cost = Json.integer(Doctrine.interaction("gift"), "cost", 400);
        if (g == null || !g.active) return new Result(false, "They are gone.");
        if (w == null || !w.pay(cost)) return new Result(false, "Supplies worth " + cost + " cash is needed.");
        g.res("supplies", 4); g.res("medicine", 1);
        float before = g.playerRelation.favor;
        emit("GIFT", RivalryState.PLAYER, g.id, g.homeDistrict, "supplies", cost + " cash of supplies", List.of(g.leaderId), List.of(g.id, g.leaderId));
        for (Job j : g.jobs) if ("taken".equals(j.status) && "supply".equals(j.kind)) { j.have = j.need; j.status = "paid"; w.receive(j.payout); emit("COMPLETE_JOB", RivalryState.PLAYER, g.id, g.homeDistrict, "supply", j.title, List.of(), List.of(g.id)); }
        return new Result(true, "Supplies delivered to " + g.name + String.format(" (favor %+.0f).", g.playerRelation.favor - before));
    }

    public Result bribe(Group g, Wallet w) {
        int cost = Json.integer(Doctrine.interaction("bribe"), "cost", 900);
        if (g == null || !g.active) return new Result(false, "They are gone.");
        if ("government".equals(g.faction) && s.leaderOf(g) != null && s.leaderOf(g).has("honorable")) { emit("INTIMIDATE", RivalryState.PLAYER, g.id, g.homeDistrict, "bribe_refused", "offered money to an officer who took it as an insult", List.of(g.leaderId), List.of(g.id, g.leaderId)); return new Result(false, g.name + ": the officer does not take money. The offer was noted."); }
        if (w == null || !w.pay(cost)) return new Result(false, cost + " cash is needed.");
        g.res("coins", cost / 150f);
        float before = g.playerRelation.favor;
        emit("BRIBE", RivalryState.PLAYER, g.id, g.homeDistrict, "coins", cost + " cash", List.of(g.leaderId), List.of(g.id, g.leaderId));
        return new Result(true, cost + " cash changes hands with " + g.name + String.format(" (favor %+.0f, hostility now %.0f).", g.playerRelation.favor - before, g.playerRelation.hostility));
    }

    /** Leaning on a group. Strength impresses bandits, frightens loners into resentment, and is an offence to the government. */
    public Result intimidate(Group g, int playerRank) {
        if (g == null || !g.active) return new Result(false, "They are gone.");
        float weight = Math.min(1f, playerRank / 40f) + Math.max(0, g.playerRelation.fear) / 150f + Math.max(0, g.playerRelation.respect) / 300f - g.morale * 0.3f;
        boolean works = s.roll() < Math.max(0.05f, Math.min(0.9f, 0.2f + weight * 0.6f));
        emit("INTIMIDATE", RivalryState.PLAYER, g.id, g.homeDistrict, works ? "worked" : "laughed_off", works ? "backed down" : "did not back down", List.of(g.leaderId), List.of(g.id, g.leaderId));
        if (works) {
            g.doctrineState.put("hunt_player", 0f);
            if ("bandits".equals(g.faction)) g.playerRelation.respect = clamp(g.playerRelation.respect + 8);
            g.cooldownUntil.put("retaliate", s.tick + 5); g.cooldownUntil.put("ambush", s.tick + 5);
            return new Result(true, g.name + " back down - for now. They will remember being leaned on.");
        }
        return new Result(false, g.name + " do not back down. You have made it worse.");
    }

    /** Telling a group what you know of one of its enemies. The enemy may hear of it. */
    public Result intel(Group g, Group about) {
        Result no = need(g, "intel");
        if (no != null) return no;
        if (about == null || about == g) return new Result(false, "Intel about whom?");
        boolean knowsSomething = false;
        for (WorldEvent e : s.log) if (RivalryState.PLAYER.equals(e.actorId) && about.id.equals(e.groupId) && s.tick - e.tick <= 24) knowsSomething = true;
        if (s.lastEncounter.containsKey(about.leaderId) && s.tick - s.lastEncounter.get(about.leaderId) <= 24) knowsSomething = true;
        if (!knowsSomething) return new Result(false, "You have nothing recent on " + about.name + ". Meet them first.");
        if (rel(g, about.id).hostility < 15) return new Result(false, g.name + " have no quarrel with " + about.name + " and no use for it.");
        g.res("intel", 3);
        emit("INTEL", RivalryState.PLAYER, g.id, g.homeDistrict, "about:" + about.id, "told them about " + about.name, List.of(g.leaderId), List.of(g.id, g.leaderId));
        // informing on someone is helping their enemy: if it gets back to them it costs
        WorldEvent leak = event("AID_RIVAL", RivalryState.PLAYER, about.id, g.homeDistrict, "informing", "informed on them to " + g.name, List.of(), List.of());
        if (s.roll() < 0.35f) leak.witnessIds.add(about.id);
        emit(leak);
        return new Result(true, g.name + " listen. " + (leak.witnessIds.isEmpty() ? "" : about.name + " hear that you talked."));
    }

    public Result acceptJob(Group g, Job j) {
        Result no = need(g, "job");
        if (no != null) return no;
        if (j == null || !"open".equals(j.status)) return new Result(false, "That work is no longer on offer.");
        j.status = "taken";
        return new Result(true, "Taken: " + j.title + ". " + j.text);
    }

    public Result truce(Group g, Wallet w) {
        if (g == null || !g.active) return new Result(false, "They are gone.");
        if (treaty(g, null)) return new Result(false, "You already have an agreement with " + g.name + ".");
        if (g.playerRelation.favor < Doctrine.alliance("truceFavor", -45)) return new Result(false, g.name + " hate you too much to talk (" + Math.round(g.playerRelation.favor) + "). Cool it first: tribute, a bribe, time.");
        int cost = Math.round(Doctrine.alliance("truceCost", 600) * (1 + Math.max(0, g.playerRelation.hostility) / 60f));
        if (w == null || !w.pay(cost)) return new Result(false, "A truce with " + g.name + " will cost " + cost + " cash.");
        Treaty t = new Treaty();
        t.id = "trt-" + s.nextTreaty++; t.groupId = g.id; t.kind = "truce"; t.startTick = s.tick; t.reviewTick = s.tick + Math.round(Doctrine.alliance("truceTicks", 20)); t.signedWith = g.leaderId;
        t.terms.add("nonaggression until tick " + t.reviewTick); t.breach.add("attack their people");
        s.treaties.put(t.id, t); g.treatyIds.add(t.id);
        g.doctrineState.put("hunt_player", 0f);
        g.playerRelation.hostility = Math.max(0, g.playerRelation.hostility - 25);
        emit("MAKE_TREATY", RivalryState.PLAYER, g.id, g.homeDistrict, "truce", "truce for " + Math.round(Doctrine.alliance("truceTicks", 20)) + " ticks, " + cost + " cash", List.of(g.leaderId), List.of(g.id, g.leaderId));
        return new Result(true, "Truce agreed with " + g.name + " (" + cost + " cash).");
    }

    /** Why an alliance with this group cannot be signed yet, or an empty list when every prerequisite holds. */
    public List<String> allianceBlockers(Group g) {
        List<String> why = new ArrayList<>();
        if (g == null || !g.active) { why.add("the group is gone"); return why; }
        Leader l = s.leaderOf(g);
        float fav = Doctrine.alliance("favor", 75), tr = Doctrine.alliance("trust", 40);
        if (g.playerRelation.favor < fav) why.add("group favor " + Math.round(g.playerRelation.favor) + " of " + Math.round(fav));
        if (l == null || l.playerRelation.trust < tr) why.add("the leader's trust " + (l == null ? "-" : String.valueOf(Math.round(l.playerRelation.trust))) + " of " + Math.round(tr));
        long window = Math.round(Doctrine.alliance("betrayalMemoryTicks", 160));
        for (Memory m : g.groupMemories) if ("BETRAY".equals(m.type) && RivalryState.PLAYER.equals(m.subject) && s.tick - m.tick <= window) { why.add("they remember being betrayed by you"); break; }
        if (l != null) for (Memory m : l.personalMemories) if ("BETRAY".equals(m.type) && RivalryState.PLAYER.equals(m.subject) && m.known() && s.tick - m.tick <= window * 2 && !why.contains("they remember being betrayed by you")) { why.add("the leader has heard you break your word"); break; }
        if (g.state("hunt_player") > 0) why.add("they are hunting you");
        for (Treaty t : s.treaties.values()) {
            if (!t.active() || !"alliance".equals(t.kind) || t.groupId.equals(g.id)) continue;
            Group other = s.group(t.groupId);
            if (other != null && rel(g, other.id).hostility >= 60) why.add("you are allied with their enemies, " + other.name);
        }
        Treaty have = s.treatyWith(g.id, "alliance");
        if (have != null) why.add("already allied");
        return why;
    }

    /**
     * Negotiates a formal alliance: sufficient favor and the leader's trust, no unresolved betrayal, interests that
     * do not collide, and a commitment - the first tribute paid now, or (where the faction asks none) a pledge of
     * defence. The terms, obligations and breach conditions are the faction's own.
     */
    public Result alliance(Group g, Wallet w) {
        List<String> why = allianceBlockers(g);
        if (!why.isEmpty()) return new Result(false, "No alliance with " + g.name + " yet: " + String.join("; ", why) + ".");
        Map<String, Object> a = Json.obj(Doctrine.faction(g.faction), "alliance");
        int tribute = Json.integer(a, "tribute", 0);
        if (tribute > 0 && (w == null || !w.pay(tribute))) return new Result(false, g.name + " want a commitment first: " + tribute + " cash.");
        Treaty old = s.treatyWith(g.id, "truce");
        if (old != null) old.status = "expired";
        Treaty t = new Treaty();
        t.id = "trt-" + s.nextTreaty++; t.groupId = g.id; t.kind = "alliance"; t.startTick = s.tick; t.reviewTick = s.tick + Math.round(Doctrine.alliance("reviewTicks", 28)); t.tribute = tribute; t.signedWith = g.leaderId;
        t.terms.addAll(Json.strList(a, "terms")); t.obligations.addAll(Json.strList(a, "obligations")); t.breach.addAll(Json.strList(a, "breach"));
        if (tribute == 0) t.obligations.add("defence pledge: come when they are attacked");
        s.treaties.put(t.id, t); g.treatyIds.add(t.id);
        emit("MAKE_TREATY", RivalryState.PLAYER, g.id, g.homeDistrict, "alliance", Json.str(a, "name", "alliance") + (tribute > 0 ? ", " + tribute + " cash each review" : ", defence pledge"), List.of(g.leaderId), List.of(g.id, g.leaderId));
        return new Result(true, Json.str(a, "name", "Alliance") + " signed with " + g.name + ": " + String.join(", ", t.terms) + ". You owe: " + String.join("; ", t.obligations) + ".");
    }

    /** Asking an ally (or a group that trusts you) for help on the next operation. They keep their own mind. */
    public Result aid(Group g) {
        Result no = need(g, "aid");
        if (no != null) return no;
        if (!treaty(g, "alliance") && g.playerRelation.favor < 50) return new Result(false, g.name + " are not that close to you.");
        if (g.morale < 0.3f || g.size < 8) return new Result(false, g.name + " cannot spare anyone: " + (g.size < 8 ? "too few of them left" : "they are in no state to fight") + ".");
        if ("loners".equals(g.faction) && needs(g).get("security") > 1.0f) return new Result(false, g.name + " will not leave their own unguarded right now.");
        if (g.cooldownUntil.getOrDefault("aid", -1L) > s.tick) return new Result(false, g.name + " helped recently. Ask again later.");
        g.doctrineState.put("aid", 3f);
        g.cooldownUntil.put("aid", s.tick + 6);
        g.playerRelation.debt = Math.max(0, g.playerRelation.debt - 10);
        return new Result(true, g.name + " will send people to your next operation.");
    }

    public Result mediate(Group a, Group b, Wallet w) {
        if (a == null || b == null || a == b) return new Result(false, "Between whom?");
        if (a.playerRelation.favor < 20 || b.playerRelation.favor < 20) return new Result(false, "Both sides have to accept you first (favor 20 with each).");
        int cost = Json.integer(Doctrine.interaction("mediate"), "cost", 700);
        if (w == null || !w.pay(cost)) return new Result(false, "Brokering it will cost " + cost + " cash.");
        Relation ab = rel(a, b.id), ba = rel(b, a.id);
        ab.hostility = Math.max(0, ab.hostility - 25); ba.hostility = Math.max(0, ba.hostility - 25); ab.favor = clamp(ab.favor + 8); ba.favor = clamp(ba.favor + 8);
        emit("MEDIATE", RivalryState.PLAYER, a.id, a.homeDistrict, "with:" + b.id, "peace brokered with " + b.name, List.of(a.leaderId), List.of(a.id, a.leaderId));
        emit("MEDIATE", RivalryState.PLAYER, b.id, b.homeDistrict, "with:" + a.id, "peace brokered with " + a.name, List.of(b.leaderId), List.of(b.id, b.leaderId));
        return new Result(true, a.name + " and " + b.name + " have stepped back from each other.");
    }

    public Result betray(Group g) {
        Treaty t = g == null ? null : s.treatyWith(g.id, null);
        if (t == null) return new Result(false, "There is no agreement to break.");
        breakTreaty(t, RivalryState.PLAYER, "you tore it up");
        return new Result(true, "You have broken your " + t.kind + " with " + g.name + ".");
    }

    /** Price multiplier when trading with a group: earned favor makes it cheaper, bad blood dearer (0.75 .. 1.5). */
    public float priceFactor(Group g) {
        if (g == null) return 1;
        float f = g.playerRelation.favor;
        float k = 1 - f / 100f * 0.25f;
        if (treaty(g, "alliance")) k -= 0.05f;
        return Math.max(0.75f, Math.min(1.5f, k));
    }

    // ================================================================== dialogue from memory (section 3.6)
    /** One line for a leader to say on meeting the player: the most telling thing they remember, else how the group stands. */
    public String line(Leader l, Group g) {
        Memory best = null;
        if (l != null) for (Memory m : l.personalMemories) {
            if (!RivalryState.PLAYER.equals(m.subject)) continue;
            if (best == null || m.salience * m.confidence * (m.pivotal ? 1.3f : 1) > best.salience * best.confidence * (best.pivotal ? 1.3f : 1)) best = m;
        }
        if (best != null) {
            boolean heard = !best.known();
            String pre = heard ? "They say " : "";
            String n = best.count > 1 ? best.count + " times" : "once";
            if (best.tags.contains("spared_me") || best.tags.contains("spared_us")) return heard ? "They say you let some of mine walk. I'm still deciding what that makes you." : "You could have finished it and you didn't. I have not forgotten that.";
            if (best.tags.contains("rescued_ours")) return heard ? "I hear you pulled one of ours out. That counts for something here." : "You brought one of mine back. Whatever you need, ask.";
            if (best.tags.contains("healed_ours")) return pre + (heard ? "you patched up our wounded. Why?" : "You treated my people when you had no reason to.");
            if (best.tags.contains("betrayed_us")) return heard ? "I'm told your word is worth nothing." : "We had an agreement. I remember exactly how you ended it.";
            if (best.tags.contains("killed_our_leader")) return heard ? "They say it was you who killed the one before me." : "I watched you kill the one who led us.";
            if (best.tags.contains("beat_us")) {
                // what they say about it follows what they actually did about it
                if (best.tags.contains("m:explosive")) return g.adapted("blast_discipline") > 0 ? "No grenades this time? We spread out now, because of you." : "I can still hear your explosives. So can what is left of my people.";
                if (best.tags.contains("m:headshot")) return g.adapted("helmets") > 0 ? "You like the head shot. We wear helmets now." : "You shoot for the head. I've buried the proof.";
                return "Last time you had the better of us. I've thought about that every day since.";
            }
            if (best.tags.contains("killed_our_people")) return heard ? "Word is you've been killing my people." : "You've put mine in the ground - " + n + " that I know of.";
            if (best.tags.contains("attacked_us")) return heard ? "I hear you shot at us." : "You opened fire on us. Give me a reason not to answer it.";
            if (best.tags.contains("did_our_work")) return "You did what you said you would. That is rarer than it should be.";
            if (best.tags.contains("stood_with_us")) return "You stood on our ground and fought for it. You're welcome on it.";
            if (best.tags.contains("gave_to_us")) return "The supplies got here. People ate because of that.";
            if (best.tags.contains("threatened_us")) return "You think you can lean on us. Try it again and see.";
            if (best.tags.contains("paid_us_off")) return "Your money's good. It doesn't buy friendship, but it buys quiet.";
            if (best.tags.contains("broke_word")) return "You said you would and you didn't.";
            if (best.tags.contains("helped_our_enemies")) return heard ? "Somebody says you've been talking to our enemies about us." : "You carried tales about us to people who want us dead.";
        }
        int d = disposition(g);
        String key = treaty(g, "alliance") ? "greet_allied" : d < 0 ? "greet_hostile" : d > 0 ? "greet_friendly" : "greet_neutral";
        return Doctrine.bark(g.faction, key);
    }

    // ================================================================== the encounter director (section 3.6)
    /**
     * Chooses which notable people the player meets on an operation in a district, weighted by
     * history salience + unresolved conflict or debt + local plausibility + novelty - recency.
     * The dead and the captured are never chosen; nobody is chosen twice inside the cooldown; a group that neither
     * holds ground near the district nor has a reason to come there cannot appear in it.
     */
    public List<Encounter> plan(String district, int max) {
        List<Encounter> cand = new ArrayList<>();
        long cool = Math.round(Doctrine.world("encounterCooldownTicks", 3));
        for (Leader l : s.leaders.values()) {
            if (!l.alive || l.captured) continue;
            Group g = s.group(l.groupId);
            if (g == null || !g.active) continue;
            long since = s.tick - s.lastEncounter.getOrDefault(l.id, -9999L);
            if (since < cool) continue;
            int dist = 9;
            for (String t : g.territoryIds) dist = Math.min(dist, loc.distance(t, district));
            boolean hunting = g.state("hunt_player") > 0, helping = g.state("aid") > 0;
            // travel: a group reaches one hop beyond its own ground only with a reason to make the trip
            float plaus = dist == 0 ? 1.2f : dist == 1 ? (hunting || helping ? 0.9f : 0.35f) : hunting || helping ? 0.5f : 0;
            if (plaus <= 0) continue;
            if (!"leader".equals(l.role) && !hunting && dist > 0) continue;
            float hist = 0;
            for (Memory m : l.personalMemories) if (RivalryState.PLAYER.equals(m.subject)) hist += m.salience * m.confidence * Math.min(2, m.count);
            Relation r = l.playerRelation;
            float unresolved = Math.max(0, r.hostility) / 50f + r.debt / 40f + Math.abs(g.playerRelation.favor) / 120f + (hunting ? 1.5f : 0) + (helping ? 1.2f : 0);
            float novelty = l.encounters == 0 ? 0.5f : 0;
            float recency = 1.5f / (1 + since / 4f);
            float score = Math.min(2f, hist * 0.5f) + unresolved + plaus + novelty - recency;
            if (!"leader".equals(l.role)) score *= 0.6f;
            if (score <= 0.4f) continue;
            int d = disposition(g);
            String kind = helping ? "support" : hunting ? "hunt" : d < 0 ? (dist == 0 ? "defend" : "patrol") : d > 0 ? "greet" : "watch";
            String reason = (helping ? "sent to help you" : hunting ? "looking for you" : dist == 0 ? "on their own ground" : "passing through") + String.format(" (history %.1f, unresolved %.1f, plausibility %.1f, novelty %.1f, recency -%.1f)", Math.min(2f, hist * 0.5f), unresolved, plaus, novelty, recency);
            cand.add(new Encounter(l.id, g.id, kind, reason, line(l, g), score));
        }
        List<Encounter> out = new ArrayList<>();
        while (out.size() < max && !cand.isEmpty()) {
            float total = 0;
            for (Encounter c : cand) total += c.score();
            float roll = s.roll() * total;
            Encounter pick = cand.get(0);
            for (Encounter c : cand) { roll -= c.score(); if (roll <= 0) { pick = c; break; } }
            out.add(pick);
            final String gid = pick.groupId();
            cand.removeIf(c -> c.groupId().equals(gid));
        }
        return out;
    }

    /** The meeting happened: the director will leave this person alone for a while, and they have one more encounter behind them. */
    public void met(Leader l) {
        if (l == null) return;
        s.lastEncounter.put(l.id, s.tick);
        l.encounters++;
        l.lastSeenTick = s.tick;
    }

    /** An ordinary member becomes somebody: a persistent, named person the world will keep (section 3.1). */
    public Leader promote(Group g, String name, boolean female, long appearanceSeed, String why) {
        Leader l = generateLeader(g, "notable");
        if (name != null && !name.isEmpty()) l.name = name;
        l.female = female;
        if (appearanceSeed != 0) l.appearanceSeed = appearanceSeed;
        l.title = "";
        g.memberIds.add(l.id);
        note(g, l.name + " is known by name now: " + why);
        return l;
    }

    // ================================================================== debugging (section 9, step 8)
    public String describe(Group g) {
        Leader l = s.leaderOf(g);
        StringBuilder b = new StringBuilder();
        b.append(g.id).append("  ").append(g.name).append("  [").append(Doctrine.display(g.faction)).append("]  size ").append(g.size).append('/').append(g.startSize).append("  morale ").append(Math.round(g.morale * 100)).append("%  cohesion ").append(Math.round(g.cohesion * 100)).append('%');
        b.append("\n  ground: ").append(g.territoryIds).append("  objective: ").append(g.objective).append("  intent: ").append(intent(g)).append("  last: ").append(g.lastAction);
        b.append("\n  resources: ");
        for (Map.Entry<String, Float> e : g.resources.entrySet()) b.append(e.getKey()).append(' ').append(Math.round(e.getValue())).append("  ");
        if (l != null) b.append("\n  leader: ").append(l.id).append("  ").append(l.title).append(' ').append(l.name).append(l.alive ? "" : " (DEAD)").append("  traits ").append(l.traits).append("  wants ").append(l.ambitions).append("  fears ").append(l.fears).append("  injuries ").append(l.injuries).append("  encounters ").append(l.encounters).append("  rivalry ").append(Math.round(rivalry(l) * 100)).append('%');
        Relation r = g.playerRelation;
        b.append(String.format("\n  player <- group : favor %.0f (%s) trust %.0f respect %.0f fear %.0f hostility %.0f debt %.0f", r.favor, Doctrine.status(r.favor).name(), r.trust, r.respect, r.fear, r.hostility, r.debt));
        if (l != null) { Relation p = l.playerRelation; b.append(String.format("\n  player <- leader: favor %.0f trust %.0f respect %.0f fear %.0f hostility %.0f debt %.0f", p.favor, p.trust, p.respect, p.fear, p.hostility, p.debt)); }
        b.append("\n  adaptations: ").append(g.adaptations).append("  state: ");
        for (Map.Entry<String, Float> e : g.doctrineState.entrySet()) if (!e.getKey().startsWith("w_")) b.append(e.getKey()).append(' ').append(String.format("%.2f", e.getValue())).append("  ");
        b.append("\n  weights: ");
        for (String c : Doctrine.considerations()) b.append(c).append(' ').append(String.format("%.2f", g.doctrineState.getOrDefault("w_" + c, 0f))).append("  ");
        for (String tid : g.treatyIds) { Treaty t = s.treaties.get(tid); if (t != null) b.append("\n  treaty ").append(t.id).append(' ').append(t.kind).append(' ').append(t.status).append(" terms ").append(t.terms).append(" owes ").append(t.obligations); }
        return b.toString();
    }

    public String utilities(Group g) {
        StringBuilder b = new StringBuilder(g.name + " weighed (tick " + g.lastActionTick + ", chose " + g.lastAction + "):");
        g.lastUtilities.entrySet().stream().sorted((x, y) -> Float.compare(y.getValue(), x.getValue())).forEach(e -> b.append(String.format("\n  %6.2f  %s", e.getValue(), e.getKey())));
        return b.toString();
    }

    public String memories(Group g) {
        StringBuilder b = new StringBuilder(g.name + " remembers:");
        for (Memory m : g.groupMemories) b.append(String.format("\n  t%d %-16s %-8s conf %.2f sal %.2f x%d %s%s about %s  %s", m.tick, m.type, m.knowledge, m.confidence, m.salience, m.count, m.pivotal ? "PIVOTAL " : "", m.sourceId.isEmpty() ? "" : "via " + m.sourceId, m.subject, m.tags));
        Leader l = s.leaderOf(g);
        if (l != null) { b.append("\n").append(l.name).append(" personally:"); for (Memory m : l.personalMemories) b.append(String.format("\n  t%d %-16s %-8s conf %.2f about %s %s", m.tick, m.type, m.knowledge, m.confidence, m.subject, m.tags)); }
        return b.toString();
    }
}
