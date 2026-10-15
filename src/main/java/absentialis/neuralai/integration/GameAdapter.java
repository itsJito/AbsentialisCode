package absentialis.neuralai.integration;

import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.entity.Vital;
import absentialis.neuralai.api.GameWorld;
import absentialis.neuralai.api.Vec3;
import absentialis.scene.AIInjuryAdapter;
import absentialis.scene.Gore;
import absentialis.scene.Raid;
import absentialis.world.CoverMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NeuralAI's view of Absentialis: the existing raid, its actors, its voxel physics, its navigation and its cover
 * map, behind the {@link GameWorld} seam. Nothing is simulated here - every answer is the host's own.
 *
 * Units and axes are the game's and NeuralAI uses them as they are: metres, +y up, a character whose yaw is 0 faces
 * -z, +x is to its right. Everything runs on the raid's update thread.
 */
public final class GameAdapter implements GameWorld {
    private final Raid r;
    private final Map<Long, CoverMap.Point> points = new HashMap<>();
    private final Map<Long, CoverMap.Point> held = new HashMap<>();
    private final ArrayList<CoverMap.Point> scratch = new ArrayList<>();
    /** Lines spoken through NeuralAI, weapon commands refused because a friend was in the way, and shots fired (gates). */
    public int spoken, heldFire, shots;

    public GameAdapter(Raid raid) { this.r = raid; }

    private Actor a(long id) { return r.actor((int) id); }
    private static Vec3 v(float x, float y, float z) { return new Vec3(x, y, z); }

    @Override public double simulationTimeSeconds() { return r.time; }
    @Override public Vec3 actorPosition(long id) { Actor a = a(id); return a == null ? Vec3.ZERO : v(a.x, a.y, a.z); }
    @Override public Vec3 actorForward(long id) { Actor a = a(id); return a == null ? new Vec3(0, 0, -1) : new Vec3(-Math.sin(a.yaw), 0, -Math.cos(a.yaw)); }
    @Override public Vec3 actorVelocity(long id) { Actor a = a(id); return a == null ? Vec3.ZERO : v(a.vx, a.vy, a.vz); }
    @Override public boolean isAlive(long id) { Actor a = a(id); return a != null && !a.dead; }
    @Override public double eyeHeight(long id) { Actor a = a(id); return a == null ? 1.6 : a.eyeY() - a.y; }
    @Override public double bodyHeight(long id) { Actor a = a(id); return a == null ? 1.8 : a.height(); }

    @Override public boolean hostile(long observer, long other) {
        Actor a = a(observer), b = a(other);
        if (a == null || b == null || a == b || b.dead) return false;
        // 0.8, Director Mode: an actor nobody is after is nobody's enemy; an actor who is one of the infected is left alone by
        // the infected and shot at by everybody else
        if (b.isPlayer && r.playerIgnored()) return false;
        if (b.isPlayer && !r.playerKind.isEmpty()) return !a.infected() && !a.mate();
        if (Relations.hostile(r, a, b)) return true;
        // someone who has watched the player shoot at people treats the player as a threat, whatever the standing says
        return b.isPlayer && !a.infected() && !a.mate() && !a.hostileToPlayer && a.sawPlayerAggress;
    }

    @Override public boolean rayClear(Vec3 from, Vec3 to) { return Physics.sight(r.world, (float) from.x(), (float) from.y(), (float) from.z(), (float) to.x(), (float) to.y(), (float) to.z()); }
    @Override public int foliageBetween(Vec3 from, Vec3 to) { return Physics.foliage(r.world, (float) from.x(), (float) from.y(), (float) from.z(), (float) to.x(), (float) to.y(), (float) to.z()); }
    @Override public double lightAt(Vec3 p) { return r.world.brightness((float) p.x(), (float) p.y(), (float) p.z(), r.daylight()); }
    @Override public double visibilityRange() { return r.fogSight(); }

    @Override public List<Long> potentialTargetsNear(Vec3 c, double radius) {
        List<Long> out = new ArrayList<>();
        float x = (float) c.x(), z = (float) c.z(), rad = (float) radius;
        // 0.8: a user in Developer Mode is not in the world as far as anything in it is concerned
        for (Actor o : r.actors) if (!o.dead && !(o.isPlayer && r.playerIgnored()) && Math.abs(o.x - x) < rad && Math.abs(o.z - z) < rad) out.add((long) o.id);
        return out;
    }

    @Override public List<SoundEvent> soundEventsSince(long sequence) { return r.soundsSince(sequence); }
    @Override public long soundSequence() { return r.soundSequence(); }

    @Override public List<Evidence> evidenceNear(Vec3 c, double radius) {
        List<Evidence> out = new ArrayList<>();
        float x = (float) c.x(), z = (float) c.z(), rad = (float) radius;
        for (Actor o : r.actors) {
            if (!o.dead || o.isPlayer || o.infected() || Math.abs(o.x - x) > rad || Math.abs(o.z - z) > rad) continue;
            // a body says which side it was on, and the wound says which way the blow came from
            out.add(new Evidence(o.id, v(o.x, o.y, o.z), new Vec3(-o.hitDirX, 0, -o.hitDirZ), 0.5, "body:" + o.faction, o.diedAt));
        }
        for (Gore.Pool p : r.gore.pools) {
            if (Math.abs(p.x - x) > rad || Math.abs(p.z - z) > rad || r.time - p.born > 300) continue;
            out.add(new Evidence(1_000_000L + p.id, v(p.x, p.y, p.z), Vec3.ZERO, 0.4, "blood", p.born));
        }
        return out;
    }

    @Override public NavPath path(Vec3 from, Vec3 to) {
        int[] p = r.world.nav.path((float) from.x(), (float) from.y(), (float) from.z(), (float) to.x(), (float) to.z(), 2600, true);
        if (p == null) return new NavPath(List.of(), 0, false);
        List<Vec3> corners = new ArrayList<>();
        double len = 0;
        Vec3 prev = from;
        for (int i = 0; i < p.length / 3; i++) { Vec3 c = new Vec3(p[i * 3] + 0.5, p[i * 3 + 1], p[i * 3 + 2] + 0.5); len += prev.distance(c); prev = c; corners.add(c); }
        boolean reach = !corners.isEmpty() && corners.get(corners.size() - 1).distanceXZ(to) < 2.5;
        return new NavPath(corners, len, reach);
    }

    @Override public boolean walkable(Vec3 from, Vec3 to) { return r.world.nav.clear((float) from.x(), (float) from.y(), (float) from.z(), (float) to.x(), (float) to.z(), true); }

    private static long pointId(CoverMap.Point p) { return ((long) p.x << 42) ^ ((long) p.z << 20) ^ ((long) p.y << 8) ^ ((p.dx + 1) << 4) ^ (p.dz + 1); }

    @Override public List<TacticalPoint> tacticalPointsNear(Vec3 o, double radius) {
        scratch.clear();
        r.world.cover.near((float) o.x(), (float) o.y(), (float) o.z(), (float) radius, scratch);
        List<TacticalPoint> out = new ArrayList<>(scratch.size());
        if (points.size() > 6000) points.clear();
        for (CoverMap.Point p : scratch) {
            if (!p.valid) continue;
            long id = pointId(p);
            points.put(id, p);
            Vec3 pos = new Vec3(p.cx(), p.y, p.cz());
            boolean low = p.height == CoverMap.LOW;
            // where a body shoots from: over a low obstacle from where it kneels; round a high one from a step out past its end
            Vec3 peek = pos;
            if (!low) { int side = p.leanLeft ? 1 : p.leanRight ? -1 : 0; peek = pos.add(new Vec3(p.leftX() * side * 0.9, 0, p.leftZ() * side * 0.9)); }
            out.add(new TacticalPoint(id, pos, peek, new Vec3(-p.dx, 0, -p.dz), low, p.leanLeft, p.leanRight, p.integrity));
        }
        return out;
    }

    private final Map<Integer, Structure> structures = new HashMap<>();

    /**
     * 0.9: the building at a point, with somewhere to stand in each of its rooms on every floor - what a squad needs to
     * search a house room by room. Built once per building and kept (rooms do not move).
     */
    @Override public Structure structureAt(Vec3 p, double margin) {
        absentialis.world.MapData.Building best = null;
        int px = (int) Math.floor(p.x()), pz = (int) Math.floor(p.z()), mg = (int) Math.ceil(margin);
        for (absentialis.world.MapData.Building b : r.world.map.buildings) {
            if (!b.valid || b.rooms.isEmpty() || px < b.x0 - mg || px > b.x1 + mg || pz < b.z0 - mg || pz > b.z1 + mg) continue;
            if (best == null || b.contains(px, pz) && !best.contains(px, pz)) best = b;
        }
        if (best == null) return null;
        final absentialis.world.MapData.Building b = best;
        return structures.computeIfAbsent(b.id, k -> {
            List<Room> rooms = new ArrayList<>();
            for (absentialis.world.MapData.Room rm : b.rooms) {
                int[] s = roomStand(rm);
                if (s != null) rooms.add(new Room(((long) b.id << 16) | (rm.id & 0xFFFF), new Vec3(s[0] + 0.5, s[1], s[2] + 0.5), rm.floor, rm.circulation));
            }
            return new Structure(b.id, b.name == null || b.name.isEmpty() ? b.archetype : b.name, b.x0, b.z0, b.x1, b.z1, rooms);
        });
    }

    @Override public Vec3 exitOf(Vec3 p) {
        Structure s = structureAt(p, 1);
        if (s == null) return null;
        for (absentialis.world.MapData.Building b : r.world.map.buildings) if (b.id == s.id()) return new Vec3(b.entryX + 0.5, b.entryY, b.entryZ + 0.5);
        return null;
    }

    /** A cell of a room's floor a body can stand on, nearest its middle (furniture and walls are stepped round), or null when the room is solid. */
    private int[] roomStand(absentialis.world.MapData.Room rm) {
        int cx = rm.cx(), cz = rm.cz();
        for (int ring = 0; ring <= Math.max(rm.x1 - rm.x0, rm.z1 - rm.z0); ring++) for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
            int x = cx + dx, z = cz + dz;
            if (!rm.contains(x, z)) continue;
            for (int y = rm.y; y <= rm.y + 1; y++) if (r.world.nav.stand(x, y, z, true)) return new int[]{x, y, z};
        }
        return null;
    }

    @Override public Vec3 standNear(Vec3 p) { float[] s = r.standSpot((float) p.x(), (float) p.z()); return s == null ? null : v(s[0], s[1], s[2]); }

    @Override public boolean reservePoint(long pointId, long actor, double until) {
        CoverMap.Point p = points.get(pointId);
        if (p == null || !p.valid || !p.freeFor((int) actor, r.time)) return false;
        releasePoints(actor);
        r.world.cover.claim(p, (int) actor, (float) until);
        held.put(actor, p);
        Actor a = a(actor);
        if (a != null) { a.cover = p; a.hasCover = true; a.coverX = p.cx(); a.coverZ = p.cz(); a.coverSince = r.time; }
        return true;
    }

    @Override public void releasePoints(long actor) {
        CoverMap.Point p = held.remove(actor);
        if (p != null) r.world.cover.release(p, (int) actor);
        Actor a = a(actor);
        if (a != null) { a.cover = null; a.hasCover = false; a.coverAmt = 0; a.coverSide = 0; a.lean = 0; a.leanTarget = 0; }
    }

    @Override public boolean hazardAt(Vec3 p) { return r.zoneAt((float) p.x(), (float) p.y(), (float) p.z()) != null || r.fire.at((float) p.x(), (float) p.y(), (float) p.z()) != null; }

    @Override public void issueWeaponCommand(long actor, WeaponCommand c) {
        Actor a = a(actor);
        if (a == null || a.dead || a.weapon == null) return;
        if (c.reload()) { r.npcReload(a); return; }
        if (!c.trigger()) return;
        int res = r.npcCommandFire(a, (float) c.aim().x(), (float) c.aim().y(), (float) c.aim().z(), c.speculative(), (int) c.intendedTarget());
        if (res > 0) shots++; else if (res < 0) { heldFire++; r.neural.onFireHeld(actor); }
    }

    @Override public SelfState self(long id) {
        Actor a = a(id);
        if (a == null) return new SelfState(Vec3.ZERO, new Vec3(0, 0, -1), 0, 0, "none", false, false, false, false, 0, 0, 0, 0, 0, false, true, Vec3.ZERO, 0, 1, 3);
        Archetypes.Arch ar = Archetypes.arch(a.archetype);
        boolean creature = a.infected();
        Relations.Doc dc = a.mate() || creature ? null : Relations.doc(a.faction);
        boolean asleep = absentialis.sim.Agents.asleep(a.routine) && r.time > a.wakeUntil;
        String kind = "none";
        double ammo = 0;
        if (a.weapon != null && a.weapon.def().weapon != null && !a.weapon.def().weapon.melee()) {
            var w = a.weapon.def().weapon;
            kind = "marksman".equals(a.archetype) ? "scoped" : "shotgun".equals(w.family) ? "shotgun" : w.modes.contains("auto") ? "auto" : "handgun".equals(w.family) || "magnum".equals(w.family) ? "handgun" : "rifle";
            ammo = a.weapon.rounds / (double) Math.max(1, a.weapon.capacity());
        }
        // a creature with its legs gone still comes on along the ground; a person in that state is a casualty
        boolean grounded = creature ? a.ragdoll != null || a.seated || r.time < a.flingUntil : a.vital != Vital.ACTIVE || a.ragdoll != null || a.seated || a.crawling;
        float sight = asleep ? 2.2f : ar.sight() * r.difficultySight() * (1 - 0.3f * r.rain());
        return new SelfState(v(a.x, a.y, a.z), new Vec3(-Math.sin(a.yaw), 0, -Math.cos(a.yaw)), Math.max(0, a.hp / Math.max(1, a.maxHp)), ammo, kind,
                (a.impaired & AIInjuryAdapter.CANT_WALK) == 0, a.weapon != null && (a.impaired & AIInjuryAdapter.CANT_SHOOT) == 0 && r.time >= a.blindUntil, a.weapon != null,
                a.mate() || !creature && Relations.radio(a.faction), sight, Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, ar.fovCos())))), ar.hearing() * (asleep ? 0.4 : 1),
                a.morale, a.mate() || dc == null ? 0.05 : dc.breakAt(), r.time < a.staggerUntil, grounded, v(a.homeX, a.y, a.homeZ), a.mate() || dc == null ? 60 : dc.leash(), ar.speed(), ar.runSpeed());
    }

    /**
     * 1.2: people and the survivor. Somebody on two legs who is not of the survivor's own squad cannot make the survivor
     * out - and will not fire on them - from more than {@link Raid#HUMAN_ENGAGE_RANGE} blocks. Inside that, they see as
     * they always did. (The dead, animals and the squad are not held to it; nor is anybody looking at anybody else.)
     */
    @Override public double sightLimit(long observer, long target) { return engageLimit(observer, target); }
    @Override public double engageLimit(long shooter, long target) {
        Actor t = a(target);
        if (t == null || !t.isPlayer) return Double.MAX_VALUE;
        Actor s = a(shooter);
        return s == null || s.infected() || s.mate() ? Double.MAX_VALUE : Raid.HUMAN_ENGAGE_RANGE;
    }

    @Override public boolean lit(long id) {
        Actor o = a(id);
        if (o == null) return false;
        return o.light || r.time < o.flashUntil || o.isPlayer && (r.playerFlashlight || r.playerLaser || r.time - r.lastPlayerShot < 0.35f);
    }

    /** A hand torch lights what is close in front of whoever carries it. */
    public static float TORCH_REACH = 20, TORCH_LIGHT = 0.75f;

    @Override public double torchLight(long observer, long target) {
        Actor a = a(observer), o = a(target);
        return a != null && o != null && a.light && a.dist(o) < TORCH_REACH ? TORCH_LIGHT : 0;
    }

    @Override public boolean speak(long actor, String event, String detail) {
        Actor a = a(actor);
        if (a == null || a.dead) return false;
        String[] line = Lines.of(r, a, event, detail);
        if (line == null) return false;
        float before = a.lastSay;
        r.say(a, line[0], line[1]);
        if (a.lastSay == before) return false;
        spoken++;
        return true;
    }

    @Override public Vec3 bodyForward(long id) { Actor a = a(id); if (a == null) return new Vec3(0, 0, -1); float f = a.facing(); return new Vec3(-Math.sin(f), 0, -Math.cos(f)); }

    @Override public boolean conductHolds(long id, boolean atPeace) { Actor a = a(id); return a != null && !r.playerIgnored() && !a.dead && !a.infected() && !a.mate() && Routines.holds(a, atPeace); }
    @Override public void conduct(long id, double dt) { Actor a = a(id); if (a != null && !a.dead) Routines.conduct(r, a, r.neural.mind(id), (float) dt); }
    @Override public void routine(long id, double dt) { Actor a = a(id); if (a != null && !a.dead) Routines.day(r, a, (float) dt); }

    @Override public boolean mealNear(long id) {
        Actor a = a(id);
        if (a == null || a.dead || "tank".equals(a.archetype) || "gladiator".equals(a.archetype)) return false;
        if (a.feedOn != null && r.actors.contains(a.feedOn) && a.feedOn.eaten < 1f) return true;
        if (r.time < a.mealBanUntil) return false;
        Actor meal = r.nearestMeal(a.x, a.z, absentialis.gameplay.CombatConfig.get().feedSeek);
        return meal != null && Math.abs(meal.y - a.y) <= Creatures.MEAL_STOREYS;
    }

    @Override public Nature nature(long id) {
        Actor a = a(id);
        if (a == null || !a.infected()) return Nature.PERSON;
        return new Nature(true, Archetypes.arch(a.archetype).blind());
    }

    @Override public boolean throwGrenade(long actor, Vec3 at) {
        Actor a = a(actor);
        if (a == null || a.dead || a.mate()) return false;
        Relations.Doctrine d = Relations.doc(a.faction).d();
        if (d == Relations.Doctrine.LONER) return false;
        r.npcThrow(a, (float) at.x(), (float) at.y(), (float) at.z(), d == Relations.Doctrine.BANDIT ? "grenade.pipe" : "grenade.frag");
        a.fireCooldown = 1.4f;
        return true;
    }
}
