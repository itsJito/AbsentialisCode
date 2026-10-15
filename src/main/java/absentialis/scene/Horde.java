package absentialis.scene;

import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.entity.Vital;
import absentialis.neuralai.integration.Steering;
import absentialis.world.District;
import absentialis.world.MapData;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.1, the horde. Until now one of the dead with nothing to hunt drifted about the place it was put, and that was the
 * whole of its life: a district's dead were an even scatter. Now a district has places the dead gather - and the dead
 * of that district, wherever they were stood down, make their way to one of them and mill about it. So there are
 * streets with nothing in them and squares it is unwise to cross.
 *
 *   hotspots ..... a few fixed places in every district: its nests, the lairs in its landmarks, the forecourts of its
 *                  big buildings. Each of the ordinary dead belongs to the nearest one - about seven in ten of them;
 *                  the rest are roamers, and still shuffle wherever their own feet take them.
 *   gunfire ...... anything loud - a shot, a blast, an alarm - makes a hotspot of its own where it was heard, for most
 *                  of a minute, that reaches a good deal further than the sound itself did: the dead beyond earshot
 *                  see the ones in earshot go, and go too. Every one of them within its pull turns toward it, roamers
 *                  included, and goes at more than a shuffle. Keep shooting and they keep coming.
 *   being overrun  a person with more than six of the dead on them for every one of their own side near by does what
 *                  a person does: they run. Not a fighting withdrawal - they turn their back and sprint for open
 *                  ground until the odds are better.
 *
 * The dead are still driven by their own minds (NeuralAI): this class only supplies where an idle one would like to
 * be, which {@link absentialis.neuralai.integration.Creatures#wander} then walks it to. Somebody running away is the
 * one thing here that overrides a mind, and it gives the body back as soon as the numbers allow.
 */
public final class Horde {
    public static final class Spot {
        public float x, y, z, pull, until = Float.POSITIVE_INFINITY;
        public String kind = "";
        public District district;
        public int drawn;
    }

    /** The share of the ordinary dead that belong to a hotspot (the rest roam), how far off a fixed one is still theirs, and how far round it they mill. */
    public static float BOUND_SHARE = 0.7f, STATIC_REACH = 150f, MILL = 9f;
    /** Gunfire: how much further than the sound the pull reaches, the least it reaches, and how long the place stays one. */
    public static float NOISE_REACH = 2.2f, NOISE_MIN = 70f, NOISE_SECONDS = 50f;
    /** Being overrun: the dead that count are within this many metres; allies likewise; the odds past which somebody runs; and for how long at the least. */
    public static float OVERRUN_RADIUS = 15f, OVERRUN_ODDS = 6f, FLEE_SECONDS = 7f;

    private final Raid raid;
    public final List<Spot> spots = new ArrayList<>();
    private boolean built;
    private float tick, fleeTick;
    /** The dead sent toward gunfire, people who broke and ran, and noises that became hotspots (gates). */
    public int drawnToNoise, routs, noiseSpots;

    Horde(Raid raid) { this.raid = raid; }

    private void build() {
        built = true;
        MapData m = raid.world.map;
        for (MapData.Point p : m.nests) add(p.x + 0.5f, p.y, p.z + 0.5f, "nest", raid.world.district(p.x, p.z));
        for (MapData.Point p : m.lairs) if ("infected".equals(p.kind)) add(p.x + 0.5f, p.y, p.z + 0.5f, "lair", raid.world.district(p.x, p.z));
        // the forecourt of every landmark: the open ground in front of its entrance
        for (MapData.Landmark lm : m.anchors) {
            float cx = (lm.x0 + lm.x1) / 2f, cz = (lm.z0 + lm.z1) / 2f;
            int gy = raid.groundY((int) cx, (int) Math.min(lm.z0, lm.z1) - 4);
            if (gy > 0) add(cx, gy, Math.min(lm.z0, lm.z1) - 4, "forecourt", lm.district);
        }
    }

    private Spot add(float x, float y, float z, String kind, District d) {
        // two of them on top of each other are one
        for (Spot s : spots) if (s.until == Float.POSITIVE_INFINITY && Math.abs(s.x - x) < 24 && Math.abs(s.z - z) < 24) return s;
        Spot s = new Spot();
        s.x = x; s.y = y; s.z = z; s.kind = kind; s.district = d; s.pull = STATIC_REACH;
        spots.add(s);
        return s;
    }

    /** A fixed hotspot put there by hand (the console, a mission, a gate). */
    public Spot mark(float x, float y, float z, String kind) { if (!built) build(); return add(x, y, z, kind, raid.world.district((int) x, (int) z)); }

    /** Something loud was heard here, this far. */
    public void noise(float x, float y, float z, float radius, String category) {
        if (radius < 30 || !("shot".equals(category) || "explosion".equals(category) || "alarm_clock".equals(category) || "screech".equals(category) || "car_horn".equals(category))) return;
        float pull = Math.max(NOISE_MIN, radius * NOISE_REACH);
        for (Spot s : spots) {
            if (!"gunfire".equals(s.kind) || Math.abs(s.x - x) > 18 || Math.abs(s.z - z) > 18) continue;
            // the same fight: the place follows it, the pull is the loudest thing heard there, and it lasts from the last shot
            s.x += (x - s.x) * 0.3f; s.z += (z - s.z) * 0.3f; s.y = y; s.pull = Math.max(s.pull, pull); s.until = raid.time + NOISE_SECONDS;
            return;
        }
        Spot s = new Spot();
        s.x = x; s.y = y; s.z = z; s.kind = "gunfire"; s.pull = pull; s.until = raid.time + NOISE_SECONDS; s.district = raid.world.district((int) x, (int) z);
        spots.add(s);
        noiseSpots++;
    }

    /** One of the ordinary dead that keeps to a hotspot (decided once, by who it is). */
    public static boolean bound(Actor a) { return Math.floorMod(a.id * 7919 + 13, 100) < BOUND_SHARE * 100; }

    private static boolean ordinary(Actor a) { return a.infected() && !a.dead && !a.site && ("walker".equals(a.archetype) || "sprinter".equals(a.archetype) || "shambler".equals(a.archetype) || "bomber".equals(a.archetype) || "spewer".equals(a.archetype) || "idog".equals(a.archetype)); }

    /** The hotspot one of the dead is making for right now: gunfire within its pull first (everybody goes to that), else - for the seven in ten that belong to one - the nearest fixed one of its district; or null (a roamer). */
    public Spot spotFor(Actor a) {
        if (!built) build();
        if (!ordinary(a)) return null;
        Spot best = null;
        float bd = Float.MAX_VALUE;
        for (Spot s : spots) {
            if (!"gunfire".equals(s.kind) || raid.time > s.until) continue;
            float d = a.distXZ(s.x, s.z);
            if (d < s.pull && d < bd) { bd = d; best = s; }
        }
        if (best != null || !bound(a)) return best;
        District mine = raid.world.district((int) a.x, (int) a.z);
        for (Spot s : spots) {
            if ("gunfire".equals(s.kind) || s.district != mine) continue;
            float d = a.distXZ(s.x, s.z);
            if (d < s.pull && d < bd) { bd = d; best = s; }
        }
        return best;
    }

    /** Where an idle one of the dead would like to be: {x, z, the spot, the spot (its new home)} - somewhere round its hotspot - or null for a roamer. */
    public float[] goalFor(Actor a) {
        Spot s = spotFor(a);
        if (s == null) return null;
        boolean fire = "gunfire".equals(s.kind);
        float ang = raid.rng.nextFloat() * 6.2832f, rad = (fire ? 2f : 3f) + raid.rng.nextFloat() * (fire ? MILL * 0.6f : MILL);
        if (fire && a.distXZ(s.x, s.z) > MILL + 4) drawnToNoise++;
        s.drawn++;
        return new float[]{s.x + (float) Math.sin(ang) * rad, s.z + (float) Math.cos(ang) * rad, s.x, s.z};
    }

    /** True while the place this one is going to is where the shooting is: it does not dawdle. */
    public boolean hurry(Actor a) {
        for (Spot s : spots) if ("gunfire".equals(s.kind) && raid.time <= s.until && Math.abs(s.x - a.homeX) < 1f && Math.abs(s.z - a.homeZ) < 1f) return a.distXZ(s.x, s.z) > MILL;
        return false;
    }

    void update(float dt) {
        tick -= dt;
        if (tick <= 0) {
            tick = 1f;
            spots.removeIf(s -> raid.time > s.until);
            // gunfire is news: an idle one that was standing about, or on its way somewhere else, thinks again now
            for (Spot s : spots) {
                if (!"gunfire".equals(s.kind) || raid.time - (s.until - NOISE_SECONDS) > 2.5f) continue;
                for (Actor a : raid.actors) {
                    if (!ordinary(a) || !"idle".equals(a.state) || a.distXZ(s.x, s.z) > s.pull || a.distXZ(s.x, s.z) < MILL) continue;
                    if (Math.abs(a.homeX - s.x) < 1f && Math.abs(a.homeZ - s.z) < 1f) continue;
                    a.hasGoal = false; a.thinkTimer = 0;
                }
            }
        }
        fleeTick -= dt;
        if (fleeTick <= 0) { fleeTick = 0.5f; overrun(); }
    }

    /** How many of the dead are on a person, and how many of their own side are near enough to matter (themselves included). */
    public int[] odds(Actor a) {
        int dead = 0, friends = 1;
        for (Actor o : raid.actors) {
            if (o == a || o.dead || Math.abs(o.x - a.x) > OVERRUN_RADIUS || Math.abs(o.z - a.z) > OVERRUN_RADIUS || Math.abs(o.y - a.y) > 4f) continue;
            float d = a.distXZ(o.x, o.z);
            if (d > OVERRUN_RADIUS) continue;
            if (o.infected() && !o.turned) { if (o.ragdoll == null) dead++; }
            else if (o.vital == Vital.ACTIVE && (o.faction.equals(a.faction) || a.mate() && (o.isPlayer || o.mate()) || o.isPlayer && !a.hostileToPlayer && !raid.playerIgnored())) friends++;
        }
        return new int[]{dead, friends};
    }

    private void overrun() {
        for (int i = 0; i < raid.actors.size(); i++) {
            Actor a = raid.actors.get(i);
            if (a.isPlayer || a.dead || a.infected() || a.advisor || a.seated || a.vital != Vital.ACTIVE || a.ragdoll != null || a.tier != 0) continue;
            int[] o = odds(a);
            if (o[0] <= OVERRUN_ODDS * o[1]) continue;
            if (raid.time >= a.overrunUntil) { routs++; if (raid.time - a.lastSay > 5) raid.say(a, o[1] > 1 ? "Too many - fall back! Go!" : "There's too many of them!", "shout"); }
            a.overrunUntil = raid.time + FLEE_SECONDS;
        }
    }

    /**
     * One frame of somebody running for it. Away from the middle of the dead that are on them, by whichever of five
     * headings round that line can actually be run along; sprinting; not looking back. Returns false when it is over.
     */
    public boolean flee(Actor a, float dt) {
        if (raid.time >= a.overrunUntil || a.dead || a.vital != Vital.ACTIVE) return false;
        float cx = 0, cz = 0;
        int n = 0;
        for (Actor o : raid.actors) {
            if (o.dead || !o.infected() || o.turned || a.distXZ(o.x, o.z) > OVERRUN_RADIUS * 1.6f) continue;
            cx += o.x; cz += o.z; n++;
        }
        if (n == 0) { a.overrunUntil = Math.min(a.overrunUntil, raid.time + 1.5f); cx = a.x - (float) -Math.sin(a.yaw); cz = a.z - (float) -Math.cos(a.yaw); n = 1; }
        cx /= n; cz /= n;
        float ax = a.x - cx, az = a.z - cz, al = (float) Math.sqrt(ax * ax + az * az);
        if (al < 0.05f) { ax = (float) -Math.sin(a.yaw); az = (float) -Math.cos(a.yaw); al = 1; }
        ax /= al; az /= al;
        if (raid.time >= a.fleeAt) {
            // a line that can be run: straight away first, then to either side of that
            a.fleeAt = raid.time + 0.8f;
            float base = (float) Math.atan2(ax, az);
            a.fleeX = a.x + ax * 22; a.fleeZ = a.z + az * 22;
            for (float off : new float[]{0, 0.6f, -0.6f, 1.2f, -1.2f}) {
                float hx = (float) Math.sin(base + off), hz = (float) Math.cos(base + off), tx = a.x + hx * 22, tz = a.z + hz * 22;
                if (raid.world.nav.clear(a.x, a.y, a.z, a.x + hx * 9, a.z + hz * 9, true)) { a.fleeX = tx; a.fleeZ = tz; break; }
            }
        }
        a.state = "flee"; a.crouch = false; a.prone = false; a.sprinting = true; a.cover = null; a.coverAmt = 0; a.faceSet = false;
        a.goalY = Float.NaN;
        Steering.goTo(raid, a, a.fleeX, a.fleeZ, 5.6f, dt);
        return true;
    }
}
