package absentialis.rivalry;

import absentialis.engine.util.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the Group Rivalry System remembers (0.6, brief "The Group Rivalry System" sections 3 and 8): groups,
 * their leaders, what each of them knows, how each of them stands with the player and with each other, treaties,
 * jobs, the event log and the dice. Plain data - the rules that change it are in {@link RivalryEngine} - saved
 * whole with the profile, so every id, relationship and seed survives save and load.
 *
 * Vocabulary (design invariant 1): a <em>faction</em> is a doctrine (mercenaries, government, loners, bandits);
 * a <em>group</em> is one organisation that follows it; a <em>leader</em> is one person who runs one group.
 * In the rest of the game the four factions are keyed "mercenaries", "military", "survivors" and "raiders";
 * {@link Doctrine#gameKey} and {@link Doctrine#ofGameKey} translate.
 */
public final class RivalryState {
    public static final String PLAYER = "player";

    /** Directed attitude of one party toward another. Every field runs -100 .. 100 except debt, which is what is owed. */
    public static final class Relation {
        public float favor, trust, respect, fear, hostility, debt;
        /** Tick of the last change, and how many times each kind of event has moved this relation (diminishing returns). */
        public long touched;
        public final Map<String, Integer> repeats = new LinkedHashMap<>();
        /** Why it stands where it does: the last few changes, newest first ("tick|eventId|type|+3.0 favor"). */
        public final List<String> audit = new ArrayList<>();

        public Relation copy() { Relation r = new Relation(); r.favor = favor; r.trust = trust; r.respect = respect; r.fear = fear; r.hostility = hostility; r.debt = debt; r.touched = touched; return r; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("favor", (double) favor); m.put("trust", (double) trust); m.put("respect", (double) respect); m.put("fear", (double) fear); m.put("hostility", (double) hostility); m.put("debt", (double) debt);
            m.put("touched", String.valueOf(touched));
            m.put("repeats", new LinkedHashMap<String, Object>(repeats));
            m.put("audit", new ArrayList<Object>(audit));
            return m;
        }

        static Relation fromJson(Map<String, Object> m) {
            Relation r = new Relation();
            if (m == null) return r;
            r.favor = (float) Json.num(m, "favor", 0); r.trust = (float) Json.num(m, "trust", 0); r.respect = (float) Json.num(m, "respect", 0);
            r.fear = (float) Json.num(m, "fear", 0); r.hostility = (float) Json.num(m, "hostility", 0); r.debt = (float) Json.num(m, "debt", 0);
            r.touched = Json.lng(m, "touched", 0);
            for (Map.Entry<String, Object> e : Json.obj(m, "repeats").entrySet()) if (e.getValue() instanceof Number n) r.repeats.put(e.getKey(), n.intValue());
            r.audit.addAll(Json.strList(m, "audit"));
            return r;
        }
    }

    /** What someone knows of an event: seen or told, how sure, from whom. Repeats of the same kind of thing fold into one memory. */
    public static final class Memory {
        public String eventId = "", type = "", sourceId = "", subject = "";
        public long tick;
        public float salience, confidence = 1;
        /** "known" (took part or saw it) or "rumored" (was told). */
        public String knowledge = "known";
        public final List<String> tags = new ArrayList<>();
        /** How many events this memory stands for (low-value repeats are aggregated, not stored one by one). */
        public int count = 1;
        public boolean pivotal;

        public boolean known() { return "known".equals(knowledge); }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eventId", eventId); m.put("type", type); m.put("tick", String.valueOf(tick)); m.put("salience", (double) salience); m.put("confidence", (double) confidence);
            m.put("knowledge", knowledge); m.put("sourceId", sourceId); m.put("subject", subject); m.put("tags", new ArrayList<Object>(tags)); m.put("count", count); m.put("pivotal", pivotal);
            return m;
        }

        static Memory fromJson(Map<String, Object> m) {
            Memory x = new Memory();
            x.eventId = Json.str(m, "eventId", ""); x.type = Json.str(m, "type", ""); x.tick = Json.lng(m, "tick", 0); x.salience = (float) Json.num(m, "salience", 0); x.confidence = (float) Json.num(m, "confidence", 1);
            x.knowledge = Json.str(m, "knowledge", "known"); x.sourceId = Json.str(m, "sourceId", ""); x.subject = Json.str(m, "subject", ""); x.tags.addAll(Json.strList(m, "tags")); x.count = Json.integer(m, "count", 1); x.pivotal = Json.bool(m, "pivotal", false);
            return x;
        }
    }

    /** Something that happened and matters (section 3.2). Immutable once emitted; ingestion is idempotent on {@link #id}. */
    public static final class WorldEvent {
        public String id = "", actorId = "", groupId = "", locationId = "", type = "", method = "", outcome = "";
        public long tick;
        public final List<String> targetIds = new ArrayList<>(), witnessIds = new ArrayList<>();
        public float salience;

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("tick", String.valueOf(tick)); m.put("actorId", actorId); m.put("targetIds", new ArrayList<Object>(targetIds)); m.put("groupId", groupId); m.put("locationId", locationId);
            m.put("type", type); m.put("method", method); m.put("outcome", outcome); m.put("witnessIds", new ArrayList<Object>(witnessIds)); m.put("salience", (double) salience);
            return m;
        }

        static WorldEvent fromJson(Map<String, Object> m) {
            WorldEvent e = new WorldEvent();
            e.id = Json.str(m, "id", ""); e.tick = Json.lng(m, "tick", 0); e.actorId = Json.str(m, "actorId", ""); e.targetIds.addAll(Json.strList(m, "targetIds")); e.groupId = Json.str(m, "groupId", "");
            e.locationId = Json.str(m, "locationId", ""); e.type = Json.str(m, "type", ""); e.method = Json.str(m, "method", ""); e.outcome = Json.str(m, "outcome", ""); e.witnessIds.addAll(Json.strList(m, "witnessIds"));
            e.salience = (float) Json.num(m, "salience", 0);
            return e;
        }

        @Override public String toString() { return "t" + tick + " " + type + (method.isEmpty() ? "" : "/" + method) + " " + actorId + " -> " + targetIds + (groupId.isEmpty() ? "" : " [" + groupId + "]") + (locationId.isEmpty() ? "" : " @" + locationId) + " : " + outcome + " (salience " + Math.round(salience * 100) / 100f + ", " + witnessIds.size() + " witnesses)"; }
    }

    /** A persistent, named person: a leader, a lieutenant, or anyone an encounter made worth remembering (section 3.1). */
    public static final class Leader {
        public String id = "", groupId = "", name = "", title = "", voice = "", role = "leader";
        public boolean female, alive = true, captured;
        public long appearanceSeed, bornTick, lastSeenTick = -9999, diedTick = -1;
        public int rank = 1, encounters, defeats, escapes;
        /** Temperament (aggressive, cautious, charismatic, vengeful ...), what they want, what they are good at, what they are afraid of and bad at. */
        public final List<String> traits = new ArrayList<>(), ambitions = new ArrayList<>(), skills = new ArrayList<>(), fears = new ArrayList<>(), weaknesses = new ArrayList<>(), injuries = new ArrayList<>();
        public final List<Memory> personalMemories = new ArrayList<>();
        /** How this person stands with the player - their own view, which need not be the group's (invariant 5). */
        public final Relation playerRelation = new Relation();
        public final Map<String, Relation> relationsByLeader = new LinkedHashMap<>();
        /** Internal politics: how loyal, able, feared and ambitious (succession and mutiny read these). */
        public float loyalty = 0.7f, competence = 0.5f, ambition = 0.4f, dread = 0.2f;

        public boolean has(String trait) { return traits.contains(trait); }
        public boolean personalMemoriesHave(String tag) { for (Memory m : personalMemories) if (m.tags.contains(tag)) return true; return false; }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("groupId", groupId); m.put("name", name); m.put("title", title); m.put("voice", voice); m.put("role", role);
            m.put("female", female); m.put("alive", alive); m.put("captured", captured);
            m.put("appearanceSeed", String.valueOf(appearanceSeed)); m.put("bornTick", String.valueOf(bornTick)); m.put("lastSeenTick", String.valueOf(lastSeenTick)); m.put("diedTick", String.valueOf(diedTick));
            m.put("rank", rank); m.put("encounters", encounters); m.put("defeats", defeats); m.put("escapes", escapes);
            m.put("traits", new ArrayList<Object>(traits)); m.put("ambitions", new ArrayList<Object>(ambitions)); m.put("skills", new ArrayList<Object>(skills));
            m.put("fears", new ArrayList<Object>(fears)); m.put("weaknesses", new ArrayList<Object>(weaknesses)); m.put("injuries", new ArrayList<Object>(injuries));
            List<Object> mem = new ArrayList<>();
            for (Memory x : personalMemories) mem.add(x.toJson());
            m.put("personalMemories", mem);
            m.put("playerRelation", playerRelation.toJson());
            Map<String, Object> rl = new LinkedHashMap<>();
            for (Map.Entry<String, Relation> e : relationsByLeader.entrySet()) rl.put(e.getKey(), e.getValue().toJson());
            m.put("relationsByLeader", rl);
            m.put("loyalty", (double) loyalty); m.put("competence", (double) competence); m.put("ambition", (double) ambition); m.put("dread", (double) dread);
            return m;
        }

        @SuppressWarnings("unchecked")
        static Leader fromJson(Map<String, Object> m) {
            Leader l = new Leader();
            l.id = Json.str(m, "id", ""); l.groupId = Json.str(m, "groupId", ""); l.name = Json.str(m, "name", ""); l.title = Json.str(m, "title", ""); l.voice = Json.str(m, "voice", ""); l.role = Json.str(m, "role", "leader");
            l.female = Json.bool(m, "female", false); l.alive = Json.bool(m, "alive", true); l.captured = Json.bool(m, "captured", false);
            l.appearanceSeed = Json.lng(m, "appearanceSeed", 0); l.bornTick = Json.lng(m, "bornTick", 0); l.lastSeenTick = Json.lng(m, "lastSeenTick", -9999); l.diedTick = Json.lng(m, "diedTick", -1);
            l.rank = Json.integer(m, "rank", 1); l.encounters = Json.integer(m, "encounters", 0); l.defeats = Json.integer(m, "defeats", 0); l.escapes = Json.integer(m, "escapes", 0);
            l.traits.addAll(Json.strList(m, "traits")); l.ambitions.addAll(Json.strList(m, "ambitions")); l.skills.addAll(Json.strList(m, "skills"));
            l.fears.addAll(Json.strList(m, "fears")); l.weaknesses.addAll(Json.strList(m, "weaknesses")); l.injuries.addAll(Json.strList(m, "injuries"));
            for (Object o : Json.list(m, "personalMemories")) if (o instanceof Map) l.personalMemories.add(Memory.fromJson((Map<String, Object>) o));
            Relation pr = Relation.fromJson(Json.obj(m, "playerRelation"));
            copyInto(pr, l.playerRelation);
            for (Map.Entry<String, Object> e : Json.obj(m, "relationsByLeader").entrySet()) if (e.getValue() instanceof Map) l.relationsByLeader.put(e.getKey(), Relation.fromJson((Map<String, Object>) e.getValue()));
            l.loyalty = (float) Json.num(m, "loyalty", 0.7); l.competence = (float) Json.num(m, "competence", 0.5); l.ambition = (float) Json.num(m, "ambition", 0.4); l.dread = (float) Json.num(m, "dread", 0.2);
            return l;
        }
    }

    static void copyInto(Relation from, Relation to) {
        to.favor = from.favor; to.trust = from.trust; to.respect = from.respect; to.fear = from.fear; to.hostility = from.hostility; to.debt = from.debt; to.touched = from.touched;
        to.repeats.clear(); to.repeats.putAll(from.repeats); to.audit.clear(); to.audit.addAll(from.audit);
    }

    /** Work a group offers the player (section 6: group-specific jobs). */
    public static final class Job {
        public String id = "", groupId = "", kind = "", title = "", text = "", targetGroupId = "", district = "", status = "open";
        public int need = 1, have, payout;
        public float favor, trust;
        public long offeredTick, deadlineTick;

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("groupId", groupId); m.put("kind", kind); m.put("title", title); m.put("text", text); m.put("targetGroupId", targetGroupId); m.put("district", district); m.put("status", status);
            m.put("need", need); m.put("have", have); m.put("payout", payout); m.put("favor", (double) favor); m.put("trust", (double) trust); m.put("offeredTick", String.valueOf(offeredTick)); m.put("deadlineTick", String.valueOf(deadlineTick));
            return m;
        }

        static Job fromJson(Map<String, Object> m) {
            Job j = new Job();
            j.id = Json.str(m, "id", ""); j.groupId = Json.str(m, "groupId", ""); j.kind = Json.str(m, "kind", ""); j.title = Json.str(m, "title", ""); j.text = Json.str(m, "text", ""); j.targetGroupId = Json.str(m, "targetGroupId", "");
            j.district = Json.str(m, "district", ""); j.status = Json.str(m, "status", "open"); j.need = Json.integer(m, "need", 1); j.have = Json.integer(m, "have", 0); j.payout = Json.integer(m, "payout", 0);
            j.favor = (float) Json.num(m, "favor", 0); j.trust = (float) Json.num(m, "trust", 0); j.offeredTick = Json.lng(m, "offeredTick", 0); j.deadlineTick = Json.lng(m, "deadlineTick", 0);
            return j;
        }
    }

    /** One persistent organisation (section 8). */
    public static final class Group {
        public String id = "", faction = "", name = "", leaderId = "", objective = "hold", emblem = "", homeDistrict = "";
        public int size = 20, startSize = 20, colour = 0xFFB0B0B0;
        public long createdTick, lastActionTick = -99, seed;
        public float morale = 0.7f, cohesion = 0.7f;
        public boolean active = true;
        public final List<String> memberIds = new ArrayList<>(), lieutenantIds = new ArrayList<>(), territoryIds = new ArrayList<>(), treatyIds = new ArrayList<>(), formerLeaderIds = new ArrayList<>();
        /** supplies, ammo, coins, intel, medicine, gear ... */
        public final Map<String, Float> resources = new LinkedHashMap<>();
        public final List<Memory> groupMemories = new ArrayList<>();
        /** How the group as a whole stands with the player (its favor is not shared by other groups of the faction). */
        public final Relation playerRelation = new Relation();
        public final Map<String, Relation> relationsByGroup = new LinkedHashMap<>();
        /** Doctrine weights as this leader has bent them, the faction's own state (alert, contract, loot ...) and cooldowns by action. */
        public final Map<String, Float> doctrineState = new LinkedHashMap<>();
        public final Map<String, Long> cooldownUntil = new LinkedHashMap<>();
        /** Adaptations learned from recorded experience: id -> level (capped; each has a cost and a tradeoff, see content/rivalry.json). */
        public final Map<String, Integer> adaptations = new LinkedHashMap<>();
        public final List<Job> jobs = new ArrayList<>();
        /** The last strategic decision and the utility of everything that was considered (debug: "rivalry utility"). */
        public String lastAction = "";
        public final Map<String, Float> lastUtilities = new LinkedHashMap<>();
        /** Short history of what the group has done and had done to it, newest first. */
        public final List<String> history = new ArrayList<>();

        public float res(String k) { return resources.getOrDefault(k, 0f); }
        public void res(String k, float d) { resources.put(k, Math.max(0, res(k) + d)); }
        public float state(String k) { return doctrineState.getOrDefault(k, 0f); }
        public int adapted(String id) { return adaptations.getOrDefault(id, 0); }

        @SuppressWarnings("unchecked")
        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("faction", faction); m.put("name", name); m.put("leaderId", leaderId); m.put("objective", objective); m.put("emblem", emblem); m.put("homeDistrict", homeDistrict);
            m.put("size", size); m.put("startSize", startSize); m.put("colour", colour); m.put("createdTick", String.valueOf(createdTick)); m.put("lastActionTick", String.valueOf(lastActionTick)); m.put("seed", String.valueOf(seed));
            m.put("morale", (double) morale); m.put("cohesion", (double) cohesion); m.put("active", active);
            m.put("memberIds", new ArrayList<Object>(memberIds)); m.put("lieutenantIds", new ArrayList<Object>(lieutenantIds)); m.put("territoryIds", new ArrayList<Object>(territoryIds));
            m.put("treatyIds", new ArrayList<Object>(treatyIds)); m.put("formerLeaderIds", new ArrayList<Object>(formerLeaderIds));
            Map<String, Object> rs = new LinkedHashMap<>();
            for (Map.Entry<String, Float> e : resources.entrySet()) rs.put(e.getKey(), (double) e.getValue());
            m.put("resources", rs);
            List<Object> mem = new ArrayList<>();
            for (Memory x : groupMemories) mem.add(x.toJson());
            m.put("groupMemories", mem);
            m.put("playerRelation", playerRelation.toJson());
            Map<String, Object> rg = new LinkedHashMap<>();
            for (Map.Entry<String, Relation> e : relationsByGroup.entrySet()) rg.put(e.getKey(), e.getValue().toJson());
            m.put("relationsByGroup", rg);
            Map<String, Object> ds = new LinkedHashMap<>();
            for (Map.Entry<String, Float> e : doctrineState.entrySet()) ds.put(e.getKey(), (double) e.getValue());
            m.put("doctrineState", ds);
            Map<String, Object> cd = new LinkedHashMap<>();
            for (Map.Entry<String, Long> e : cooldownUntil.entrySet()) cd.put(e.getKey(), String.valueOf(e.getValue()));
            m.put("cooldownUntil", cd);
            m.put("adaptations", new LinkedHashMap<String, Object>(adaptations));
            List<Object> jl = new ArrayList<>();
            for (Job j : jobs) jl.add(j.toJson());
            m.put("jobs", jl);
            m.put("lastAction", lastAction);
            Map<String, Object> lu = new LinkedHashMap<>();
            for (Map.Entry<String, Float> e : lastUtilities.entrySet()) lu.put(e.getKey(), (double) e.getValue());
            m.put("lastUtilities", lu);
            m.put("history", new ArrayList<Object>(history));
            return m;
        }

        @SuppressWarnings("unchecked")
        static Group fromJson(Map<String, Object> m) {
            Group g = new Group();
            g.id = Json.str(m, "id", ""); g.faction = Json.str(m, "faction", ""); g.name = Json.str(m, "name", ""); g.leaderId = Json.str(m, "leaderId", ""); g.objective = Json.str(m, "objective", "hold");
            g.emblem = Json.str(m, "emblem", ""); g.homeDistrict = Json.str(m, "homeDistrict", "");
            g.size = Json.integer(m, "size", 20); g.startSize = Json.integer(m, "startSize", g.size); g.colour = Json.integer(m, "colour", 0xFFB0B0B0);
            g.createdTick = Json.lng(m, "createdTick", 0); g.lastActionTick = Json.lng(m, "lastActionTick", -99); g.seed = Json.lng(m, "seed", 0);
            g.morale = (float) Json.num(m, "morale", 0.7); g.cohesion = (float) Json.num(m, "cohesion", 0.7); g.active = Json.bool(m, "active", true);
            g.memberIds.addAll(Json.strList(m, "memberIds")); g.lieutenantIds.addAll(Json.strList(m, "lieutenantIds")); g.territoryIds.addAll(Json.strList(m, "territoryIds"));
            g.treatyIds.addAll(Json.strList(m, "treatyIds")); g.formerLeaderIds.addAll(Json.strList(m, "formerLeaderIds"));
            for (Map.Entry<String, Object> e : Json.obj(m, "resources").entrySet()) if (e.getValue() instanceof Number n) g.resources.put(e.getKey(), n.floatValue());
            for (Object o : Json.list(m, "groupMemories")) if (o instanceof Map) g.groupMemories.add(Memory.fromJson((Map<String, Object>) o));
            copyInto(Relation.fromJson(Json.obj(m, "playerRelation")), g.playerRelation);
            for (Map.Entry<String, Object> e : Json.obj(m, "relationsByGroup").entrySet()) if (e.getValue() instanceof Map) g.relationsByGroup.put(e.getKey(), Relation.fromJson((Map<String, Object>) e.getValue()));
            for (Map.Entry<String, Object> e : Json.obj(m, "doctrineState").entrySet()) if (e.getValue() instanceof Number n) g.doctrineState.put(e.getKey(), n.floatValue());
            for (Map.Entry<String, Object> e : Json.obj(m, "cooldownUntil").entrySet()) try { g.cooldownUntil.put(e.getKey(), Long.parseLong(String.valueOf(e.getValue()))); } catch (NumberFormatException ignored) { }
            for (Map.Entry<String, Object> e : Json.obj(m, "adaptations").entrySet()) if (e.getValue() instanceof Number n) g.adaptations.put(e.getKey(), n.intValue());
            for (Object o : Json.list(m, "jobs")) if (o instanceof Map) g.jobs.add(Job.fromJson((Map<String, Object>) o));
            g.lastAction = Json.str(m, "lastAction", "");
            for (Map.Entry<String, Object> e : Json.obj(m, "lastUtilities").entrySet()) if (e.getValue() instanceof Number n) g.lastUtilities.put(e.getKey(), n.floatValue());
            g.history.addAll(Json.strList(m, "history"));
            return g;
        }
    }

    /** A negotiated, persistent agreement between a group and the player (section 6). */
    public static final class Treaty {
        public String id = "", groupId = "", playerId = PLAYER, kind = "alliance", status = "active", signedWith = "", brokenBy = "";
        public final List<String> terms = new ArrayList<>(), obligations = new ArrayList<>(), breach = new ArrayList<>();
        public long startTick, reviewTick, lastDueTick;
        /** What the standing obligation costs each review period, and how many have been missed. */
        public int tribute, missed;

        public boolean active() { return "active".equals(status); }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("groupId", groupId); m.put("playerId", playerId); m.put("kind", kind); m.put("status", status); m.put("signedWith", signedWith); m.put("brokenBy", brokenBy);
            m.put("terms", new ArrayList<Object>(terms)); m.put("obligations", new ArrayList<Object>(obligations)); m.put("breach", new ArrayList<Object>(breach));
            m.put("startTick", String.valueOf(startTick)); m.put("reviewTick", String.valueOf(reviewTick)); m.put("lastDueTick", String.valueOf(lastDueTick)); m.put("tribute", tribute); m.put("missed", missed);
            return m;
        }

        static Treaty fromJson(Map<String, Object> m) {
            Treaty t = new Treaty();
            t.id = Json.str(m, "id", ""); t.groupId = Json.str(m, "groupId", ""); t.playerId = Json.str(m, "playerId", PLAYER); t.kind = Json.str(m, "kind", "alliance"); t.status = Json.str(m, "status", "active");
            t.signedWith = Json.str(m, "signedWith", ""); t.brokenBy = Json.str(m, "brokenBy", "");
            t.terms.addAll(Json.strList(m, "terms")); t.obligations.addAll(Json.strList(m, "obligations")); t.breach.addAll(Json.strList(m, "breach"));
            t.startTick = Json.lng(m, "startTick", 0); t.reviewTick = Json.lng(m, "reviewTick", 0); t.lastDueTick = Json.lng(m, "lastDueTick", 0); t.tribute = Json.integer(m, "tribute", 0); t.missed = Json.integer(m, "missed", 0);
            return t;
        }
    }

    // ------------------------------------------------------------------ the world's memory
    public final Map<String, Group> groups = new LinkedHashMap<>();
    public final Map<String, Leader> leaders = new LinkedHashMap<>();
    public final Map<String, Treaty> treaties = new LinkedHashMap<>();
    /** The player's slow, faction-wide reputation as the rivalry layer sees it: first impressions only (section 6). */
    public final Map<String, Float> factionReputation = new LinkedHashMap<>();
    /** Every event still on record, oldest first (bounded), and every id ever ingested (idempotency survives the log's trimming). */
    public final List<WorldEvent> log = new ArrayList<>();
    public final Set<String> seen = new LinkedHashSet<>();
    /** Rumours on their way: "eventId|toGroupId|arrivalTick|confidence|sourceId". */
    public final List<String> rumours = new ArrayList<>();
    /** Who the encounter director last put in front of the player, and when: "leaderId" -> tick. */
    public final Map<String, Long> lastEncounter = new LinkedHashMap<>();
    public long tick, seed, rolls;
    public int nextGroup = 1, nextLeader = 1, nextEvent = 1, nextTreaty = 1, nextJob = 1;
    public boolean seeded;
    /** Feedback for the player that has not been shown yet ("Garrison North: favor +6 (rescued a wounded soldier)"). */
    public final List<String> notices = new ArrayList<>();

    public Group group(String id) { return id == null ? null : groups.get(id); }
    public Leader leader(String id) { return id == null ? null : leaders.get(id); }
    public Leader leaderOf(Group g) { return g == null ? null : leaders.get(g.leaderId); }
    public List<Group> active() { List<Group> l = new ArrayList<>(); for (Group g : groups.values()) if (g.active) l.add(g); return l; }
    public List<Group> ofFaction(String faction) { List<Group> l = new ArrayList<>(); for (Group g : groups.values()) if (g.active && g.faction.equals(faction)) l.add(g); return l; }
    public WorldEvent event(String id) { for (int i = log.size() - 1; i >= 0; i--) if (log.get(i).id.equals(id)) return log.get(i); return null; }

    public Treaty treatyWith(String groupId, String kind) {
        for (Treaty t : treaties.values()) if (t.groupId.equals(groupId) && t.active() && (kind == null || t.kind.equals(kind))) return t;
        return null;
    }

    /** A standing pact between two groups (made by their own talks), or null. Either side holding it is enough. */
    public String treatyBetween(Group a, Group b) {
        return a.cooldownUntil.getOrDefault("pact:" + b.id, -1L) > tick || b.cooldownUntil.getOrDefault("pact:" + a.id, -1L) > tick ? "pact" : null;
    }

    /** The next number from this world's own dice: deterministic for a seed and the count of numbers already drawn, across save and load. */
    public float roll() {
        long h = (seed ^ 0x5DEECE66DL) + (++rolls) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 30; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 27; h *= 0x94D049BB133111EBL; h ^= h >>> 31;
        return (h >>> 40) / (float) (1 << 24);
    }

    // ------------------------------------------------------------------ persistence
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", 1);
        m.put("tick", String.valueOf(tick)); m.put("seed", String.valueOf(seed)); m.put("rolls", String.valueOf(rolls)); m.put("seeded", seeded);
        m.put("nextGroup", nextGroup); m.put("nextLeader", nextLeader); m.put("nextEvent", nextEvent); m.put("nextTreaty", nextTreaty); m.put("nextJob", nextJob);
        List<Object> gl = new ArrayList<>();
        for (Group g : groups.values()) gl.add(g.toJson());
        m.put("groups", gl);
        List<Object> ll = new ArrayList<>();
        for (Leader l : leaders.values()) ll.add(l.toJson());
        m.put("leaders", ll);
        List<Object> tl = new ArrayList<>();
        for (Treaty t : treaties.values()) tl.add(t.toJson());
        m.put("treaties", tl);
        Map<String, Object> fr = new LinkedHashMap<>();
        for (Map.Entry<String, Float> e : factionReputation.entrySet()) fr.put(e.getKey(), (double) e.getValue());
        m.put("factionReputation", fr);
        List<Object> el = new ArrayList<>();
        for (WorldEvent e : log) el.add(e.toJson());
        m.put("log", el);
        m.put("seen", new ArrayList<Object>(seen));
        m.put("rumours", new ArrayList<Object>(rumours));
        Map<String, Object> le = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : lastEncounter.entrySet()) le.put(e.getKey(), String.valueOf(e.getValue()));
        m.put("lastEncounter", le);
        m.put("notices", new ArrayList<Object>(notices));
        return m;
    }

    @SuppressWarnings("unchecked")
    public static RivalryState fromJson(Map<String, Object> m) {
        RivalryState s = new RivalryState();
        if (m == null || m.isEmpty()) return s;          // a save from before 0.6: an empty state, seeded on first use
        s.tick = Json.lng(m, "tick", 0); s.seed = Json.lng(m, "seed", 0); s.rolls = Json.lng(m, "rolls", 0); s.seeded = Json.bool(m, "seeded", false);
        s.nextGroup = Json.integer(m, "nextGroup", 1); s.nextLeader = Json.integer(m, "nextLeader", 1); s.nextEvent = Json.integer(m, "nextEvent", 1); s.nextTreaty = Json.integer(m, "nextTreaty", 1); s.nextJob = Json.integer(m, "nextJob", 1);
        for (Object o : Json.list(m, "groups")) if (o instanceof Map) { Group g = Group.fromJson((Map<String, Object>) o); s.groups.put(g.id, g); }
        for (Object o : Json.list(m, "leaders")) if (o instanceof Map) { Leader l = Leader.fromJson((Map<String, Object>) o); s.leaders.put(l.id, l); }
        for (Object o : Json.list(m, "treaties")) if (o instanceof Map) { Treaty t = Treaty.fromJson((Map<String, Object>) o); s.treaties.put(t.id, t); }
        for (Map.Entry<String, Object> e : Json.obj(m, "factionReputation").entrySet()) if (e.getValue() instanceof Number n) s.factionReputation.put(e.getKey(), n.floatValue());
        for (Object o : Json.list(m, "log")) if (o instanceof Map) s.log.add(WorldEvent.fromJson((Map<String, Object>) o));
        s.seen.addAll(Json.strList(m, "seen"));
        s.rumours.addAll(Json.strList(m, "rumours"));
        for (Map.Entry<String, Object> e : Json.obj(m, "lastEncounter").entrySet()) try { s.lastEncounter.put(e.getKey(), Long.parseLong(String.valueOf(e.getValue()))); } catch (NumberFormatException ignored) { }
        s.notices.addAll(Json.strList(m, "notices"));
        return s;
    }
}
