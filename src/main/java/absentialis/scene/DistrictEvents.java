package absentialis.scene;

import absentialis.engine.util.Json;
import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.neuralai.api.Vec3;
import absentialis.world.District;
import absentialis.world.MapData;
import absentialis.world.World;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * District hotspots and random events (0.9). Until now a district differed from another in what stood in it and who
 * patrolled it; what was going on there was the same everywhere. Each district now has situations of its own
 * (content/district_events.json): one hotspot in progress per operation - loners barricaded into an apartment block
 * downtown, a crew stripping the market, a warehouse fought over in the Industrial, the dead gathered at the store
 * in the Barrens - and smaller things that happen near the survivor while they are in it.
 *
 * A hotspot is people at a place with something to do, and nothing more is scripted than that: they are put there,
 * told where they belong, and what follows is NeuralAI's - the sides are enemies or they are not, they see each other
 * or they do not. The survivor is told once, on entering the district, that it is happening; it is marked on the
 * field map; nothing is spawned until they are near enough for it to matter; and it ends when one side of it is
 * gone, with something for the survivor if they had a hand in how it ended.
 */
public final class DistrictEvents {
    /** One hotspot as it is written in the data. */
    public record Def(String id, String name, String kind, String faction, String place, int count, String against, int againstCount, String text) {}

    /** One hotspot in this operation. */
    public static final class Live {
        public Def def;
        public District district;
        public MapData.Building building;
        public float x, y, z;
        public boolean spawned, announced, resolved, rewarded;
        public String outcome = "";
        public final List<Actor> side = new ArrayList<>(), other = new ArrayList<>();
        float nextWave, since;
        public int waves, playerKills;
    }

    private final Raid raid;
    public final List<Live> hotspots = new ArrayList<>();
    private final Map<String, List<Def>> byDistrict = new LinkedHashMap<>();
    private final Map<String, List<String>> randomByDistrict = new LinkedHashMap<>();
    public float activate = 125, randomMin = 150, randomMax = 260;
    public int randomCap = 14, xp = 900;
    private float nextRandom = 70;
    private final List<Actor> strays = new ArrayList<>();
    private int squadSeq = 760000;
    private boolean begun, forced;
    private District lastDistrict;
    /** Counters (tests, the Developer Mode readout). */
    public int announced, activated, resolved, randoms, wavesSent;
    public String lastRandom = "";

    DistrictEvents(Raid raid) { this.raid = raid; load(); }

    @SuppressWarnings("unchecked")
    private void load() {
        try (InputStream in = DistrictEvents.class.getResourceAsStream("/content/district_events.json")) {
            if (in == null) return;
            Map<String, Object> root = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            activate = (float) Json.num(root, "activate", activate);
            List<Object> every = Json.list(root, "randomEvery");
            if (every.size() == 2) { randomMin = ((Number) every.get(0)).floatValue(); randomMax = ((Number) every.get(1)).floatValue(); }
            randomCap = Json.integer(root, "randomCap", randomCap);
            xp = Json.integer(root, "xp", xp);
            for (Map.Entry<String, Object> e : Json.obj(root, "districts").entrySet()) {
                if (!(e.getValue() instanceof Map)) continue;
                Map<String, Object> d = (Map<String, Object>) e.getValue();
                List<Def> defs = new ArrayList<>();
                for (Object o : Json.list(d, "hotspots")) if (o instanceof Map) {
                    Map<String, Object> h = (Map<String, Object>) o;
                    defs.add(new Def(Json.str(h, "id", ""), Json.str(h, "name", "Hotspot"), Json.str(h, "kind", "holdout"), Json.str(h, "faction", "survivors"), Json.str(h, "place", ""),
                            Json.integer(h, "count", 4), Json.str(h, "against", "infected"), Json.integer(h, "againstCount", 4), Json.str(h, "text", "")));
                }
                byDistrict.put(e.getKey(), defs);
                List<String> rnd = new ArrayList<>();
                for (Object o : Json.list(d, "random")) rnd.add(String.valueOf(o));
                randomByDistrict.put(e.getKey(), rnd);
            }
        } catch (Exception e) { System.err.println("[DistrictEvents] " + e.getMessage()); }
    }

    public List<Def> defsOf(District d) { return byDistrict.getOrDefault(d.name(), List.of()); }
    public List<String> randomOf(District d) { return randomByDistrict.getOrDefault(d.name(), List.of()); }

    /** The hotspot of a district in this operation, or null. */
    public Live of(District d) { for (Live l : hotspots) if (l.district == d) return l; return null; }

    /** Chooses each district's hotspot for this operation (it turns over with the day) and finds the place it happens at. */
    public void begin() {
        if (begun) return;
        begun = true;
        int day = raid.profile.zone.day;
        for (District d : District.values()) {
            List<Def> defs = defsOf(d);
            if (defs.isEmpty()) continue;
            // the day's choice first; if this city has no such place in the district, the other one
            for (int k = 0; k < defs.size(); k++) {
                Def def = defs.get(Math.floorMod(day + d.ordinal() + k, defs.size()));
                MapData.Building b = place(d, def.place(), day);
                if (b == null) continue;
                Live l = new Live();
                l.def = def; l.district = d; l.building = b;
                float[] s = raid.standSpot(b.entryX + 0.5f, b.entryZ + 0.5f);
                l.x = s == null ? b.entryX + 0.5f : s[0]; l.y = s == null ? b.entryY : s[1]; l.z = s == null ? b.entryZ + 0.5f : s[2];
                hotspots.add(l);
                break;
            }
        }
    }

    /** A building of a type, or of a landmark, in a district: the same one all day, a different one another day. */
    private MapData.Building place(District d, String what, int day) {
        List<MapData.Building> fit = new ArrayList<>();
        for (MapData.Building b : raid.world.map.buildings) {
            if (!b.valid || b.district != d || b.rooms.isEmpty()) continue;
            if (b.archetype.equals(what) || b.landmark.equals(what) || b.archetype.equals("lm_" + what)) fit.add(b);
        }
        if (fit.isEmpty()) return null;
        // not on top of where the operation begins: the nearest candidates to the insertion are passed over when there are others
        fit.sort((a, b2) -> Float.compare(dist(b2), dist(a)));
        int keep = Math.max(1, fit.size() - fit.size() / 3);
        return fit.get(Math.floorMod(day * 7 + d.ordinal(), keep));
    }

    private float dist(MapData.Building b) { float dx = b.entryX - raid.insertion.x, dz = b.entryZ - raid.insertion.z; return dx * dx + dz * dz; }

    // ------------------------------------------------------------------ each frame
    void update(float dt) {
        if (!raid.populate && !forced || raid.playerIgnored()) return;
        if (!begun && raid.time > 2f) begin();
        if (!begun) return;
        Actor p = raid.player;
        District here = raid.world.district(p.x, p.z);
        // said once, on coming into a district: what is going on in it
        if (here != lastDistrict) {
            lastDistrict = here;
            Live l = of(here);
            if (l != null && !l.announced && !l.resolved) {
                l.announced = true; announced++;
                raid.notice("HOTSPOT - " + l.def.name() + " (" + bearing(p.x, p.z, l.x, l.z) + ", " + Math.round(p.distXZ(l.x, l.z) / 10f) * 10 + " m)", 0xFFE2A034);
                raid.notice(l.def.text(), 0xFFB8B2A4);
                raid.emitSound(p.x, p.y + 1.5f, p.z, 0, "radio", p.id, true);
            }
        }
        for (Live l : hotspots) {
            if (l.resolved) continue;
            float d = p.distXZ(l.x, l.z);
            if (!l.spawned) { if (d < activate) spawn(l); continue; }
            // people who went with their part of the map when the survivor walked away (the world lets go of what is far off) are
            // not casualties: with nobody left for that reason the situation is simply waiting, and is staged again on the way back
            int deadBefore = dead(l.side) + dead(l.other);
            l.side.removeIf(x -> !x.dead && !raid.actors.contains(x)); l.other.removeIf(x -> !x.dead && !raid.actors.contains(x));
            if (alive(l.side) + alive(l.other) == 0 && deadBefore == 0 && d > activate) { l.spawned = false; l.since = 0; l.side.clear(); l.other.clear(); continue; }
            l.since += dt;
            upkeep(l, d, dt);
            judge(l, d);
        }
        // the smaller things
        strays.removeIf(a -> a.dead || !raid.actors.contains(a));
        nextRandom -= dt;
        if (nextRandom <= 0) {
            nextRandom = randomMin + raid.rng.nextFloat() * (randomMax - randomMin);
            List<String> kinds = randomOf(here);
            if (!kinds.isEmpty() && strays.size() < randomCap) random(kinds.get(raid.rng.nextInt(kinds.size())), here);
        }
    }

    private static final String[] COMPASS = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
    static String bearing(float fx, float fz, float tx, float tz) {
        double a = Math.atan2(tx - fx, -(tz - fz));
        return COMPASS[(int) Math.floor((a + Math.PI * 2 + Math.PI / 8) / (Math.PI / 4)) % 8];
    }

    // ------------------------------------------------------------------ putting people there
    private Actor person(String faction, float x, float y, float z, int squad, float homeX, float homeZ) {
        Actor a = raid.debugSpawn(faction, x, z, y);
        if (a == null) return null;
        a.squad = a.infected() ? 0 : squad;
        a.homeX = homeX; a.homeZ = homeZ;
        a.wakeUntil = 1e9f;
        a.eventActor = true;
        return a;
    }

    /** Somewhere to stand inside the building: a room's floor, a different room for each of those asked for where there are enough. */
    private float[] inside(MapData.Building b, int n) {
        for (int tries = 0; tries < b.rooms.size(); tries++) {
            MapData.Room rm = b.rooms.get(Math.floorMod(n + tries, b.rooms.size()));
            for (int k = 0; k < 12; k++) {
                int x = rm.x0 + raid.rng.nextInt(Math.max(1, rm.x1 - rm.x0 + 1)), z = rm.z0 + raid.rng.nextInt(Math.max(1, rm.z1 - rm.z0 + 1));
                for (int y = rm.y; y <= rm.y + 1; y++) if (raid.world.nav.stand(x, y, z, true) && Physics.boxFree(raid.world, x + 0.5f, y, z + 0.5f, 0.3f, 1.8f)) return new float[]{x + 0.5f, y, z + 0.5f};
            }
        }
        return null;
    }

    /** Somewhere to stand out of doors, about so far from a point and - if it can be had - out of the survivor's sight. */
    private float[] outside(float cx, float cz, float min, float max, boolean unseen) {
        Actor p = raid.player;
        float[] fallback = null;
        for (int tries = 0; tries < 40; tries++) {
            double ang = raid.rng.nextFloat() * Math.PI * 2;
            float r = min + raid.rng.nextFloat() * (max - min), x = cx + (float) Math.sin(ang) * r, z = cz + (float) Math.cos(ang) * r;
            if (!World.inBounds((int) x, World.GROUND, (int) z)) continue;
            float[] s = raid.standSpot(x, z);
            if (s == null || Math.abs(s[1] - World.GROUND) > 2 || raid.world.heightAt((int) Math.floor(s[0]), (int) Math.floor(s[2])) > s[1] + 1) continue;
            if (p.distXZ(s[0], s[2]) < 26) continue;
            if (fallback == null) fallback = s;
            if (!unseen || !Physics.los(raid.world, p.x, p.eyeY(), p.z, s[0], s[1] + 1.4f, s[2])) return s;
        }
        return fallback;
    }

    private void spawn(Live l) {
        l.spawned = true; activated++;
        Def d = l.def;
        MapData.Building b = l.building;
        int sideSquad = ++squadSeq, otherSquad = ++squadSeq;
        boolean infectedSide = "infected".equals(d.faction());
        switch (d.kind()) {
            case "holdout" -> {
                // the defenders are in the building, a room each; the first wave is already on its way
                for (int i = 0; i < d.count(); i++) { float[] s = inside(b, i); if (s == null) s = new float[]{l.x, l.y, l.z}; Actor a = person(d.faction(), s[0], s[1], s[2], sideSquad, s[0], s[2]); if (a != null) { a.hostileToPlayer = false; l.side.add(a); } }
                l.nextWave = 6;
            }
            case "raid" -> {
                // the crew is at the door and going in; whoever would stop them is a street away and coming
                for (int i = 0; i < d.count(); i++) { float[] s = i < 2 ? inside(b, i) : outside(l.x, l.z, 3, 9, false); if (s == null) continue; Actor a = person(d.faction(), s[0], s[1], s[2], sideSquad, l.x, l.z); if (a != null) { l.side.add(a); if (!a.infected()) looting(a); } }
                float[] from = outside(l.x, l.z, 42, 60, true);
                if (from != null) for (int i = 0; i < d.againstCount(); i++) { Actor a = person(d.against(), from[0] + (i % 2) * 1.6f, from[1], from[2] + (i / 2) * 1.6f, otherSquad, l.x, l.z); if (a != null) { l.other.add(a); send(a, l.x, l.y, l.z); } }
            }
            case "standoff" -> {
                for (int i = 0; i < d.count(); i++) { float[] s = inside(b, i); if (s == null) s = outside(l.x, l.z, 2, 8, false); if (s == null) continue; Actor a = person(d.faction(), s[0], s[1], s[2], sideSquad, s[0], s[2]); if (a != null) l.side.add(a); }
                float[] from = outside(l.x, l.z, 30, 42, false);
                if (from != null) for (int i = 0; i < d.againstCount(); i++) { Actor a = person(d.against(), from[0] + (i % 3) * 1.8f, from[1], from[2] + (i / 3) * 1.8f, otherSquad, from[0], from[2]); if (a != null) l.other.add(a); }
            }
            default -> {
                // horde: the dead round the place, and whoever they have trapped inside it
                for (int i = 0; i < d.count(); i++) { float[] s = outside(l.x, l.z, 3, 14, false); if (s == null) continue; Actor a = person(infectedSide ? (i % 4 == 3 ? "sprinter" : "walker") : d.faction(), s[0], s[1], s[2], sideSquad, l.x, l.z); if (a != null) l.side.add(a); }
                for (int i = 0; i < d.againstCount(); i++) { float[] s = inside(b, i); if (s == null) continue; Actor a = person(d.against(), s[0], s[1], s[2], otherSquad, s[0], s[2]); if (a != null) { a.hostileToPlayer = false; l.other.add(a); } }
            }
        }
    }

    private static void looting(Actor a) { if (!"loot".equals(a.state)) { a.state = "loot"; a.stateTime = 0; } }

    /** Tells a body somewhere to go, on its own side's word. */
    private void send(Actor a, float x, float y, float z) {
        a.hasOrder = true; a.orderX = x; a.orderZ = z; a.orderUntil = raid.time + 240;
        if (raid.neural.mind(a.id) != null) raid.neural.onDispatch(a.id, new Vec3(x, y, z));
    }

    private static int dead(List<Actor> l) { int n = 0; for (Actor a : l) if (a.dead) n++; return n; }
    private static int alive(List<Actor> l) { int n = 0; for (Actor a : l) if (!a.dead) n++; return n; }

    private void upkeep(Live l, float playerDist, float dt) {
        Def d = l.def;
        if (!"holdout".equals(d.kind()) || alive(l.side) == 0) return;
        // a siege comes in waves, for as long as there is anyone inside to come for - but only while somebody is near enough to see it
        l.nextWave -= dt;
        if (l.nextWave > 0 || playerDist > activate + 30 || l.waves >= 4) return;
        l.nextWave = 50 + raid.rng.nextFloat() * 30;
        if (alive(l.other) >= d.againstCount()) return;
        float[] from = outside(l.x, l.z, 34, 52, true);
        if (from == null) return;
        l.waves++; wavesSent++;
        int n = Math.max(2, d.againstCount() / 2 + raid.rng.nextInt(2));
        boolean dead = "infected".equals(d.against());
        int squad = ++squadSeq;
        for (int i = 0; i < n; i++) {
            Actor a = person(dead ? (i % 3 == 2 ? "sprinter" : "walker") : d.against(), from[0] + (i % 3) * 1.4f, from[1], from[2] + (i / 3) * 1.4f, squad, l.x, l.z);
            if (a != null) { l.other.add(a); send(a, l.x, l.y, l.z); }
        }
    }

    /** One side of it is gone: it is over, and it is said how. */
    private void judge(Live l, float playerDist) {
        Def d = l.def;
        if (l.since < 8) return;
        int a = alive(l.side), b = alive(l.other);
        String out = null;
        boolean good = false;
        switch (d.kind()) {
            case "holdout" -> {
                if (a == 0) out = "The holdout has fallen.";
                else if (l.waves >= 3 && b == 0 && l.nextWave < 40) { out = "The siege has broken. The holdout stands."; good = true; }
            }
            case "raid" -> {
                if (a == 0) { out = "The raid on the " + placeName(l) + " has been broken."; good = true; }
                else if (b == 0 && l.since > 45 && !l.other.isEmpty()) out = "The crew has the " + placeName(l) + ". They are taking what they came for.";
            }
            case "standoff" -> {
                if (a == 0 || b == 0) { out = (a == 0 ? ZoneFaction.cap(d.against()) : ZoneFaction.cap(d.faction())) + " hold the " + placeName(l) + "."; good = true; }
            }
            default -> {
                if (a == 0) { out = "The " + placeName(l) + " is clear of the dead."; good = true; }
            }
        }
        if (out == null) return;
        l.resolved = true; l.outcome = out; resolved++;
        // the survivor's share: counted from who they put down there
        int mine = 0;
        for (Actor x : l.side) if (x.dead && x.lastAttacker == raid.player.id) mine++;
        for (Actor x : l.other) if (x.dead && x.lastAttacker == raid.player.id) mine++;
        l.playerKills = mine;
        boolean there = playerDist < activate;
        if (there || l.announced) raid.notice("HOTSPOT - " + l.def.name() + ": " + out, good ? 0xFF8FD070 : 0xFFE2A034);
        if (good && mine >= 2 && there) {
            l.rewarded = true;
            raid.debrief.hotspots++;
            raid.notice("You had a hand in it.  +" + xp + " XP", 0xFF8FD070);
            // whoever came out of it alive remembers who stood with them
            String friend = alive(l.side) > 0 ? d.faction() : d.against();
            if (!"infected".equals(friend)) raid.debrief.repDelta.merge(friend, 4f, Float::sum);
        }
    }

    private static String placeName(Live l) { String n = l.building.name == null || l.building.name.isEmpty() ? l.def.place().replace('_', ' ') : l.building.name; return n.toLowerCase(); }

    /** Faction names for a sentence. */
    private static final class ZoneFaction { static String cap(String f) { return "infected".equals(f) ? "The dead" : absentialis.sim.ZoneState.cap(f); } }

    // ------------------------------------------------------------------ the smaller things
    private void random(String kind, District here) {
        Actor p = raid.player;
        if (here == District.WILDERNESS && !kind.equals("pack")) return;
        String own = here.faction == null || here.faction.isEmpty() || "none".equals(here.faction) ? "survivors" : here.faction;
        String foe = own.equals("raiders") ? "mercenaries" : "raiders";
        float[] at = outside(p.x, p.z, 55, 95, true);
        if (at == null) return;
        int squad = ++squadSeq, squad2 = ++squadSeq;
        String said = null;
        switch (kind) {
            case "gunfight" -> {
                float[] other = outside(at[0], at[2], 22, 34, false);
                if (other == null) return;
                for (int i = 0; i < 3; i++) { Actor a = person(own, at[0] + i * 1.4f, at[1], at[2], squad, at[0], at[2]); if (a != null) { strays.add(a); send(a, other[0], other[1], other[2]); } }
                for (int i = 0; i < 3; i++) { Actor a = person(foe, other[0] + i * 1.4f, other[1], other[2], squad2, other[0], other[2]); if (a != null) { strays.add(a); send(a, at[0], at[1], at[2]); } }
                said = "Shooting has started to the " + bearing(p.x, p.z, at[0], at[2]) + ": " + absentialis.sim.ZoneState.cap(own) + " and " + absentialis.sim.ZoneState.cap(foe) + ".";
            }
            case "patrol" -> {
                float[] to = outside(p.x, p.z, 30, 60, false);
                for (int i = 0; i < 3; i++) { Actor a = person(own, at[0] + i * 1.4f, at[1], at[2], squad, at[0], at[2]); if (a != null) { strays.add(a); if (to != null) send(a, to[0], to[1], to[2]); } }
            }
            case "looters" -> {
                for (int i = 0; i < 3; i++) { Actor a = person("raiders", at[0] + i * 1.4f, at[1], at[2], squad, at[0], at[2]); if (a != null) { strays.add(a); looting(a); } }
            }
            case "scavengers" -> {
                for (int i = 0; i < 2; i++) { Actor a = person("survivors", at[0] + i * 1.4f, at[1], at[2], squad, at[0], at[2]); if (a != null) { a.hostileToPlayer = false; strays.add(a); } }
            }
            case "survivor" -> {
                Actor a = person("survivors", at[0], at[1], at[2], squad, at[0], at[2]);
                if (a != null) { a.hostileToPlayer = false; a.hp = Math.max(8, a.maxHp * 0.3f); a.bleed = Math.max(a.bleed, 0.4f); a.needsHelp = true; strays.add(a); said = "Someone is calling for help to the " + bearing(p.x, p.z, at[0], at[2]) + "."; raid.emitSound(at[0], at[1] + 1.4f, at[2], 60, "shout", a.id, false); }
            }
            default -> {
                // a pack on the move: toward where the survivor is, roughly - they have heard something
                for (int i = 0; i < 4 + raid.rng.nextInt(3); i++) { Actor a = person(i % 4 == 3 ? "sprinter" : "walker", at[0] + (i % 3) * 1.3f, at[1], at[2] + (i / 3) * 1.3f, 0, p.x, p.z); if (a != null) { strays.add(a); send(a, p.x + raid.rng.range(-18, 18), p.y, p.z + raid.rng.range(-18, 18)); } }
            }
        }
        randoms++; lastRandom = kind;
        if (said != null) raid.notice(said, 0xFFB8B2A4);
    }

    /** A hotspot brought into being now, whatever the distance (the console, tests and scripted runs). */
    public Live force(District d) { forced = true; begin(); Live l = of(d); if (l != null && !l.spawned) spawn(l); return l; }
    /** A random event of a kind, now. */
    public void forceRandom(String kind) { forced = true; begin(); random(kind, raid.world.district(raid.player.x, raid.player.z)); }
}
