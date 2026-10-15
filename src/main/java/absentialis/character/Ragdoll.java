package absentialis.character;

import static absentialis.character.Rig.*;

import absentialis.engine.math.Mat4;
import absentialis.world.World;

/**
 * Constrained lightweight ragdoll for the 12-part rig (iteration P0.4).
 *
 * Fifteen joint particles integrated with Verlet steps. Bone lengths and a braced torso are hard
 * distance constraints; joints are limited by distance ranges (no folding through the body) and by
 * hinge-side constraints (knees only bend forward, elbows only back) so nothing hyper-extends.
 * It starts from the exact animated pose, takes an impulse from the hit direction, is live for at
 * most {@link #ACTIVE_TIME} seconds and then freezes into a static corpse. Corpses are render and
 * search targets only: they never collide with actors, block extraction or stop bullets.
 */
public final class Ragdoll {
    public static final float ACTIVE_TIME = 8f, BLEND_TIME = 0.08f, STEP = 1 / 90f;
    public static final int HEAD_P = 0, NECK = 1, PELVIS = 2, LS = 3, LE = 4, LW = 5, RS = 6, RE = 7, RW = 8, LH = 9, LK = 10, LA = 11, RH = 12, RK = 13, RA = 14, N = 15;
    private static final float[] RADIUS = {0.20f, 0.14f, 0.16f, 0.12f, 0.08f, 0.07f, 0.12f, 0.08f, 0.07f, 0.13f, 0.09f, 0.08f, 0.13f, 0.09f, 0.08f};
    /** Rest position of every particle in the T-less standing pose (voxels, character space). */
    private static final float[][] REST = {
            {0, 29, 0}, {0, 24, 0}, {0, 12, 0}, {-6, 23, 0}, {-6, 18, 0}, {-6, 13, 0}, {6, 23, 0}, {6, 18, 0}, {6, 13, 0},
            {-2, 12, 0}, {-2, 6, 0}, {-2, 0, 0}, {2, 12, 0}, {2, 6, 0}, {2, 0, 0}};
    /** Rigid sticks {a, b}: bones plus torso bracing. Lengths come from REST, never from the death pose. */
    private static final int[][] STICKS = {
            {HEAD_P, NECK}, {NECK, PELVIS}, {NECK, LS}, {NECK, RS}, {LS, RS}, {PELVIS, LH}, {PELVIS, RH}, {LH, RH},
            {LS, LH}, {RS, RH}, {LS, RH}, {RS, LH}, {LS, PELVIS}, {RS, PELVIS}, {NECK, LH}, {NECK, RH},
            {LS, LE}, {LE, LW}, {RS, RE}, {RE, RW}, {LH, LK}, {LK, LA}, {RH, RK}, {RK, RA}};
    /** Range limits {a, b, min, max} in voxels: joint angle limits expressed as distances. */
    private static final float[][] LIMITS = {
            {HEAD_P, LS, 6.6f, 9.6f}, {HEAD_P, RS, 6.6f, 9.6f},          // neck: about 35 degrees each way
            {LS, LW, 3.6f, 9.85f}, {RS, RW, 3.6f, 9.85f},                 // elbows: no lock-out, no fold-through
            {LH, LA, 5.2f, 11.8f}, {RH, RA, 5.2f, 11.8f},                 // knees
            {LK, LS, 7.4f, 99}, {RK, RS, 7.4f, 99},                       // hips: thigh flexes to ~120 degrees
            {LE, RS, 8.2f, 99}, {RE, LS, 8.2f, 99},                       // arms never pass through the chest
            {LE, PELVIS, 6.0f, 99}, {RE, PELVIS, 6.0f, 99},
            {LK, RK, 2.6f, 14f}, {LA, RA, 2.2f, 99}, {LW, PELVIS, 3.5f, 99}, {RW, PELVIS, 3.5f, 99}, {HEAD_P, PELVIS, 15.5f, 99}};

    /**
     * Mass of each particle in kilograms at scale 1 (0.6, Sydney stage 1: the baseline is given a mass distribution -
     * a constraint now moves a hand more than it moves a chest). 78 kg in all.
     */
    public static final float[] MASS = {5f, 18f, 14f, 3f, 2f, 1.2f, 3f, 2f, 1.2f, 7f, 4.5f, 3f, 7f, 4.5f, 3f};

    /** Something that drives the body with forces: called once per fixed step, before integration (the Sydney Engine). */
    public interface Driver { void drive(Ragdoll r, World w, float dt); }
    public Driver driver;
    /** A driver has the body and it is alive: it does not freeze into a corpse while this is set. */
    public boolean live;
    public final float[] invMass = new float[N];
    /** Forces in newtons accumulated for the next step, then cleared. */
    public final float[] fx = new float[N], fy = new float[N], fz = new float[N];
    public static final float GRAVITY = 22f, FRICTION = 0.9f;

    public final float[] x = new float[N], y = new float[N], z = new float[N];
    private final float[] px = new float[N], py = new float[N], pz = new float[N];

    /** Velocity of a particle in metres a second. */
    public float vx(int i) { return (x[i] - px[i]) / STEP; }
    public float vy(int i) { return (y[i] - py[i]) / STEP; }
    public float vz(int i) { return (z[i] - pz[i]) / STEP; }
    /** A change of velocity applied to one particle (an impulse divided by its mass). */
    public void kick(int i, float dvx, float dvy, float dvz) { px[i] -= dvx * STEP; py[i] -= dvy * STEP; pz[i] -= dvz * STEP; }
    private final float[] tx = new float[N], ty = new float[N], tz = new float[N]; // animated pose (blend-in target)
    private final float[] rest = new float[STICKS.length];
    public final boolean[] grounded = new boolean[N];
    public final float unit;       // metres per voxel for this character (giants are scaled)
    public float age, quiet;
    public boolean settled;
    private float acc;
    /** Largest stick error (voxels) and worst hinge violation seen in the last step - for the quality gate. */
    public float maxStretch, maxHyper;

    public Ragdoll(float scale) {
        unit = VOX * scale;
        for (int i = 0; i < STICKS.length; i++) rest[i] = dist(REST[STICKS[i][0]], REST[STICKS[i][1]]);
        for (int i = 0; i < N; i++) invMass[i] = 1f / (MASS[i] * scale * scale * scale);
    }

    private static float dist(float[] a, float[] b) { return (float) Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])); }

    /** Seeds the particles from the animated pose (world matrices from {@link Rig#fk}) and a base velocity. */
    public void fromPose(Mat4[] bones, float vx, float vy, float vz) {
        float[] o = new float[3];
        put(NECK, bones[HEAD], 0, 0, 0, o); put(HEAD_P, bones[HEAD], 0, 5, 0, o); put(PELVIS, bones[TORSO], 0, 0, 0, o);
        put(LS, bones[L_UPPER_ARM], 0, 0, 0, o); put(LE, bones[L_FOREARM], 0, 0, 0, o); put(LW, bones[L_HAND], 0, 0, 0, o);
        put(RS, bones[R_UPPER_ARM], 0, 0, 0, o); put(RE, bones[R_FOREARM], 0, 0, 0, o); put(RW, bones[R_HAND], 0, 0, 0, o);
        put(LH, bones[L_THIGH], 0, 0, 0, o); put(LK, bones[L_SHIN], 0, 0, 0, o); put(LA, bones[L_SHIN], 0, -SHIN, 0, o);
        put(RH, bones[R_THIGH], 0, 0, 0, o); put(RK, bones[R_SHIN], 0, 0, 0, o); put(RA, bones[R_SHIN], 0, -SHIN, 0, o);
        for (int i = 0; i < N; i++) {
            tx[i] = x[i]; ty[i] = y[i]; tz[i] = z[i];
            px[i] = x[i] - vx * STEP; py[i] = y[i] - vy * STEP; pz[i] = z[i] - vz * STEP;
        }
        age = 0; quiet = 0; settled = false; acc = 0;
    }

    /** Places the body standing in its rest pose, feet on the ground at a point (tests and benchmarks: the game always starts from the animated pose). */
    public void standAt(float wx, float wy, float wz, float yaw) {
        float c = (float) Math.cos(yaw), sn = (float) Math.sin(yaw), s = unit / VOX;
        for (int i = 0; i < N; i++) {
            // a standing leg is not locked: the knees sit a little forward, inside the joint's range
            boolean knee = i == LK || i == RK, foot = i == LA || i == RA;
            float lx = REST[i][0] * unit, lz = (REST[i][2] - (knee ? 1.25f : 0)) * unit, ly = (REST[i][1] - (foot ? 0 : knee ? 0.132f : 0.264f)) * unit;
            x[i] = wx + lx * c + lz * sn; z[i] = wz - lx * sn + lz * c; y[i] = wy + ly + 0.08f * s;
            px[i] = tx[i] = x[i]; py[i] = ty[i] = y[i]; pz[i] = tz[i] = z[i];
        }
        age = BLEND_TIME; quiet = 0; settled = false; acc = 0;
    }

    private void put(int i, Mat4 m, float lx, float ly, float lz, float[] o) { m.transformPoint(lx, ly, lz, o); x[i] = o[0]; y[i] = o[1]; z[i] = o[2]; }

    /** Moves the whole body, velocities untouched. */
    public void shift(float dx, float dy, float dz) {
        for (int i = 0; i < N; i++) { x[i] += dx; y[i] += dy; z[i] += dz; px[i] += dx; py[i] += dy; pz[i] += dz; tx[i] += dx; ty[i] += dy; tz[i] += dz; }
    }

    /**
     * 1.1: a body is never handed to physics already inside the world. A pose taken low to the ground - the Crawler on
     * all fours, somebody prone, a body on a stair - can put a hand, a knee or the pelvis a little way into the floor,
     * and a part that starts inside a block is pushed out of it by the nearest face: for one that is more than half-way
     * in, that is downward, and the rest of the body is dragged through after it - under the map. The whole body is
     * lifted by as much as its deepest part is buried (never more than a block and a bit). Returns the lift.
     */
    public float unbury(World w) {
        if (w == null) return 0;
        float lift = 0, s = unit / VOX;
        for (int i = 0; i < N; i++) {
            float rr = RADIUS[i] * s * 0.6f;
            int bx = (int) Math.floor(x[i]), by = (int) Math.floor(y[i] - rr), bz = (int) Math.floor(z[i]);
            if (!w.solid(bx, by, bz)) continue;
            // the top of the run of solid cells this part is in (two at most: deeper than that it is not "a little way in")
            int top = by;
            if (w.solid(bx, top + 1, bz)) top++;
            if (w.solid(bx, top + 1, bz)) continue;
            lift = Math.max(lift, top + 1 + rr + 0.01f - y[i]);
        }
        lift = Math.min(lift, 1.35f);
        if (lift > 0) shift(0, lift, 0);
        return lift;
    }

    /** Velocity change (m/s) on the particles of a hit region, with a share for the whole body. */
    public void impulse(String region, float vx, float vy, float vz) {
        int[] hit = switch (region) {
            case "head" -> new int[]{HEAD_P, NECK};
            case "arms" -> new int[]{LS, RS, LE, RE};
            case "legs" -> new int[]{LK, RK, LH, RH};
            default -> new int[]{NECK, PELVIS, LS, RS};
        };
        for (int i = 0; i < N; i++) { px[i] -= vx * 0.35f * STEP; py[i] -= vy * 0.35f * STEP; pz[i] -= vz * 0.35f * STEP; }
        for (int i : hit) { px[i] -= vx * 0.65f * STEP; py[i] -= vy * 0.65f * STEP; pz[i] -= vz * 0.65f * STEP; }
    }

    /**
     * Something large and moving has hold of the body (the front of a car): every part of it is brought toward that
     * thing's velocity by the given share. Verlet: velocity lives in the previous position, so that is what is moved.
     */
    public void carry(float vx, float vy, float vz, float share) {
        for (int i = 0; i < N; i++) {
            float cx = (x[i] - px[i]) / STEP, cy = (y[i] - py[i]) / STEP, cz = (z[i] - pz[i]) / STEP;
            px[i] = x[i] - (cx + (vx - cx) * share) * STEP;
            py[i] = y[i] - (cy + Math.max(0, vy - cy) * share) * STEP;
            pz[i] = z[i] - (cz + (vz - cz) * share) * STEP;
        }
        age = Math.min(age, 0.6f); quiet = 0; settled = false;
    }

    // ------------------------------------------------------------------ 0.9: a body under physics stays under physics
    /** Pairs of parts that a bone or a limit already keeps apart: everything else is kept from passing through everything else. */
    private static final boolean[][] LINKED = new boolean[N][N];
    static {
        for (int[] s : STICKS) { LINKED[s[0]][s[1]] = true; LINKED[s[1]][s[0]] = true; }
        for (float[] l : LIMITS) { LINKED[(int) l[0]][(int) l[1]] = true; LINKED[(int) l[1]][(int) l[0]] = true; }
    }
    /** Share of the overlap two parts are pushed apart by each pass (soft: a body is not made of billiard balls), and how far inside each other they may sit. */
    public static float SELF_PUSH = 0.5f, SELF_OVERLAP = 0.78f;
    /** Times parts of this body were found inside each other and separated (tests). */
    public int selfContacts;

    /**
     * The body's own bulk: a forearm does not pass through the chest, a shin does not lie inside the other thigh. Parts that
     * no bone or limit already relates are pushed apart when they overlap, lighter parts moving further. (Until 0.9 only the
     * jointed neighbours constrained each other, so a body could come to rest folded through itself.)
     */
    private void selfCollide() {
        float s = unit / VOX;
        for (int a = 0; a < N; a++) for (int b = a + 1; b < N; b++) {
            if (LINKED[a][b]) continue;
            float min = (RADIUS[a] + RADIUS[b]) * s * SELF_OVERLAP;
            float dx = x[b] - x[a], dy = y[b] - y[a], dz = z[b] - z[a], l2 = dx * dx + dy * dy + dz * dz;
            if (l2 >= min * min) continue;
            float l = (float) Math.sqrt(l2);
            if (l < 1e-4f) { dx = 1e-3f * (a - b); dy = 1e-3f; l = 1.5e-3f; }
            float k = (l - min) / l * SELF_PUSH, wa = invMass[a] / (invMass[a] + invMass[b]), wb = 1 - wa;
            x[a] += dx * k * wa; y[a] += dy * k * wa; z[a] += dz * k * wa;
            x[b] -= dx * k * wb; y[b] -= dy * k * wb; z[b] -= dz * k * wb;
            selfContacts++;
        }
    }

    /** A body that had come to rest is under physics again (something has touched it). */
    public void wake() { settled = false; quiet = 0; age = Math.max(BLEND_TIME, Math.min(age, 1.2f)); }

    /**
     * Something has happened beside or to a body that was lying still - a blast, a round, a boot: every part within reach is
     * given a share of the velocity (m/s; the full amount at the centre, none at the edge; a hand takes more of it than a
     * chest) and the body is simulated again until it comes to rest. Returns how many parts were moved.
     */
    public int disturb(float wx, float wy, float wz, float radius, float vx, float vy, float vz) {
        int n = 0;
        float mean = 0;
        for (int i = 0; i < N; i++) mean += invMass[i] / N;
        for (int i = 0; i < N; i++) {
            float dx = x[i] - wx, dy = y[i] - wy, dz = z[i] - wz, d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > radius) continue;
            float k = (1 - d / radius) * Math.max(0.6f, Math.min(2.2f, invMass[i] / mean));
            kick(i, vx * k, vy * k, vz * k);
            n++;
        }
        if (n > 0) wake();
        return n;
    }

    /** The part of the body a line strikes first (a round going into a body on the ground), or -1. The distance along the line is left in {@link #struckAt}. */
    public int struck(float ox, float oy, float oz, float dx, float dy, float dz, float len) {
        int best = -1;
        float bt = len, s = unit / VOX;
        for (int i = 0; i < N; i++) {
            float rx = x[i] - ox, ry = y[i] - oy, rz = z[i] - oz, t = rx * dx + ry * dy + rz * dz;
            if (t < 0 || t > bt) continue;
            float cx = rx - dx * t, cy = ry - dy * t, cz = rz - dz * t, r = RADIUS[i] * s * 1.15f;
            if (cx * cx + cy * cy + cz * cz <= r * r) { bt = t; best = i; }
        }
        struckAt = bt;
        return best;
    }
    public float struckAt;

    /** Share of its velocity a part keeps each step in the air; sliding friction on the ground; and what tumbling takes on top of that. */
    public static float AIR = 0.9965f, SLIDE = 0.85f, ROLL = 0.985f;
    /** The speeds (m/s) between which a part goes from the slow regime to the fast one. */
    public static float FAST_FROM = 2.0f, FAST_FULL = 4.2f;

    public void update(World w, float dt) {
        if (settled) return;
        acc += Math.min(dt, 0.1f);
        while (acc >= STEP && !settled) { acc -= STEP; step(w); }
    }

    /** Distant corpses skip the visible simulation: resolve the fall at once and freeze. */
    public void fastForward(World w) {
        for (int i = 0; i < 320 && !settled; i++) step(w);
        settled = true;
    }

    private void step(World w) {
        age += STEP;
        float moved = 0;
        if (driver != null) driver.drive(this, w, STEP);
        for (int i = 0; i < N; i++) {
            // 0.7 (fall study): momentum is kept. The air took three quarters of a body's speed every second and the ground took
            // all of it at a touch, so nothing thrown, blown or run down ever travelled. Air drag is now slight, and a part on the
            // ground is slowed the way sliding things are - by a fixed amount a second (friction times gravity), which stops a
            // slow part dead (feet still hold) and lets a fast one slide and tumble to a stop over a few metres
            // (a part that is hardly moving keeps the old heavy damping: that is the regime balance and stepping were tuned in, and
            // what holds a standing body still. The change is for what is going fast.)
            float vx = x[i] - px[i], vy = y[i] - py[i], vz = z[i] - pz[i];
            float speed = (float) Math.sqrt(vx * vx + vy * vy + vz * vz) / STEP, fast = Math.max(0, Math.min(1, (speed - FAST_FROM) / (FAST_FULL - FAST_FROM)));
            float air = 0.985f + (AIR - 0.985f) * fast;
            vx *= air; vy *= air; vz *= air;
            if (grounded[i]) {
                float sp = (float) Math.sqrt(vx * vx + vz * vz), lose = SLIDE * GRAVITY * STEP * STEP;
                float slide = sp <= lose ? 0 : (sp - lose) / sp * ROLL, keep = 0.72f + (slide - 0.72f) * fast;
                vx *= keep; vz *= keep;
            }
            px[i] = x[i]; py[i] = y[i]; pz[i] = z[i];
            float k = invMass[i] * STEP * STEP;
            x[i] += vx + fx[i] * k; y[i] += vy - GRAVITY * STEP * STEP + fy[i] * k; z[i] += vz + fz[i] * k;
            fx[i] = fy[i] = fz[i] = 0;
            load[i] = 0;
        }
        // 80 ms hand-over: the animated pose still pulls while the simulation takes hold
        if (age < BLEND_TIME) {
            float k = (1 - age / BLEND_TIME) * 0.35f;
            for (int i = 0; i < N; i++) { tx[i] += (x[i] - px[i]) * 0.6f; tz[i] += (z[i] - pz[i]) * 0.6f; x[i] += (tx[i] - x[i]) * k; y[i] += (ty[i] - y[i]) * k; z[i] += (tz[i] - z[i]) * k; }
        }
        for (int it = 0; it < 8; it++) {
            for (int s = 0; s < STICKS.length; s++) pull(STICKS[s][0], STICKS[s][1], rest[s] * unit, rest[s] * unit);
            for (float[] l : LIMITS) pull((int) l[0], (int) l[1], l[2] * unit, l[3] * unit);
            hinges();
            if ((it & 1) == 1) selfCollide();
            for (int i = 0; i < N; i++) collide(w, i);
        }
        maxStretch = 0;
        for (int s = 0; s < STICKS.length; s++) maxStretch = Math.max(maxStretch, Math.abs(d(STICKS[s][0], STICKS[s][1]) / unit - rest[s]));
        for (int i = 0; i < N; i++) moved = Math.max(moved, Math.abs(x[i] - px[i]) + Math.abs(y[i] - py[i]) + Math.abs(z[i] - pz[i]));
        quiet = moved / STEP < 0.22f ? quiet + STEP : 0;
        if (!live && (age >= ACTIVE_TIME * 3 || quiet > 0.7f && age > 0.5f)) settle();
    }

    private void settle() {
        settled = true;
        for (int i = 0; i < N; i++) { px[i] = x[i]; py[i] = y[i]; pz[i] = z[i]; }
    }

    private float d(int a, int b) { float dx = x[a] - x[b], dy = y[a] - y[b], dz = z[a] - z[b]; return (float) Math.sqrt(dx * dx + dy * dy + dz * dz); }

    /** Keeps the distance a-b inside [min, max]. */
    private void pull(int a, int b, float min, float max) {
        float dx = x[b] - x[a], dy = y[b] - y[a], dz = z[b] - z[a];
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-5f) { dy = 1e-3f; l = 1e-3f; }
        float want = l < min ? min : l > max ? max : l;
        if (want == l) return;
        float k = (l - want) / l, wa = invMass[a] / (invMass[a] + invMass[b]), wb = 1 - wa;
        x[a] += dx * k * wa; y[a] += dy * k * wa; z[a] += dz * k * wa;
        x[b] -= dx * k * wb; y[b] -= dy * k * wb; z[b] -= dz * k * wb;
    }

    private final float[] fwd = new float[3];

    /** Body forward from the torso triangle: right x up. */
    private void forward() {
        float rx = x[RS] - x[LS], ry = y[RS] - y[LS], rz = z[RS] - z[LS];
        float ux = x[NECK] - x[PELVIS], uy = y[NECK] - y[PELVIS], uz = z[NECK] - z[PELVIS];
        // character faces -z with +x right and +y up: forward = up x right
        fwd[0] = uy * rz - uz * ry; fwd[1] = uz * rx - ux * rz; fwd[2] = ux * ry - uy * rx;
        float l = (float) Math.sqrt(fwd[0] * fwd[0] + fwd[1] * fwd[1] + fwd[2] * fwd[2]);
        if (l < 1e-6f) { fwd[0] = 0; fwd[1] = 0; fwd[2] = -1; return; }
        fwd[0] /= l; fwd[1] /= l; fwd[2] /= l;
    }

    /** One-sided hinges: the knee stays in front of the hip-ankle line, the elbow behind the shoulder-wrist line. */
    private void hinges() {
        forward();
        maxHyper = 0;
        hinge(LH, LK, LA, 1); hinge(RH, RK, RA, 1);
        hinge(LS, LE, LW, -1); hinge(RS, RE, RW, -1);
        // thighs do not swing far behind the body
        for (int[] leg : new int[][]{{LH, LK}, {RH, RK}}) {
            float back = -((x[leg[1]] - x[leg[0]]) * fwd[0] + (y[leg[1]] - y[leg[0]]) * fwd[1] + (z[leg[1]] - z[leg[0]]) * fwd[2]) - THIGH * unit * 0.55f;
            if (back > 0) { x[leg[1]] += fwd[0] * back; y[leg[1]] += fwd[1] * back; z[leg[1]] += fwd[2] * back; }
        }
    }

    private void hinge(int a, int mid, int b, float sign) {
        float mx = (x[a] + x[b]) * 0.5f, my = (y[a] + y[b]) * 0.5f, mz = (z[a] + z[b]) * 0.5f;
        float side = ((x[mid] - mx) * fwd[0] + (y[mid] - my) * fwd[1] + (z[mid] - mz) * fwd[2]) * sign;
        float slack = 0.4f * unit;
        if (side >= -slack) return;
        float push = (-slack - side) * sign;
        maxHyper = Math.max(maxHyper, (-slack - side) / unit);
        x[mid] += fwd[0] * push * 0.66f; y[mid] += fwd[1] * push * 0.66f; z[mid] += fwd[2] * push * 0.66f;
        for (int e : new int[]{a, b}) { x[e] -= fwd[0] * push * 0.17f; y[e] -= fwd[1] * push * 0.17f; z[e] -= fwd[2] * push * 0.17f; }
    }

    /** Normal load on each particle accumulated over the current step, as the distance the floor pushed it back. */
    private final float[] load = new float[N];

    private void friction(int i) {
        float sx = x[i] - px[i], sz = z[i] - pz[i], sl = (float) Math.sqrt(sx * sx + sz * sz), lim = (i == LA || i == RA ? FRICTION : i == LW || i == RW ? FRICTION * 0.75f : FRICTION * 0.35f) * load[i];   // soles grip; clothes and skin slide
        if (sl <= lim) { x[i] = px[i]; z[i] = pz[i]; }
        else if (sl > 1e-7f) { float k = (sl - lim * 0.8f) / sl; x[i] = px[i] + sx * k; z[i] = pz[i] + sz * k; }
    }

    private void collide(World w, int i) {
        float r = RADIUS[i] * (unit / VOX);
        grounded[i] = false;
        if (w == null) return;
        int ix = (int) Math.floor(x[i]), iz = (int) Math.floor(z[i]);
        // floor
        int fy = (int) Math.floor(y[i] - r);
        if (w.solid(ix, fy, iz)) {
            if (!w.solid(ix, fy + 1, iz)) {
                // Coulomb friction (0.6): how far the floor has had to push the particle back up over this step is its
                // normal load; within the cone it does not slide at all, beyond it the slide is shortened. A planted foot stays planted.
                load[i] += fy + 1 + r - y[i];
                y[i] = fy + 1 + r; grounded[i] = true;
                friction(i);
            }
            else { x[i] = px[i]; z[i] = pz[i]; if (w.solid((int) Math.floor(x[i]), fy, (int) Math.floor(z[i])) && w.solid((int) Math.floor(x[i]), fy + 1, (int) Math.floor(z[i]))) y[i] = py[i]; }
            ix = (int) Math.floor(x[i]); iz = (int) Math.floor(z[i]);
        } else if (w.solid(ix, (int) Math.floor(y[i] - r - 0.02f), iz)) { grounded[i] = true; friction(i); }   // resting on it: contact (and its friction) does not flicker with the bounce
        int iy = (int) Math.floor(y[i]);
        // ceiling
        if (w.solid(ix, (int) Math.floor(y[i] + r), iz) && !w.solid(ix, iy, iz)) y[i] = (float) Math.floor(y[i] + r) - r - 1e-3f;
        // walls
        if (w.solid((int) Math.floor(x[i] + r), iy, iz)) x[i] = (float) Math.floor(x[i] + r) - r - 1e-3f;
        else if (w.solid((int) Math.floor(x[i] - r), iy, iz)) x[i] = (float) Math.floor(x[i] - r) + 1 + r + 1e-3f;
        if (w.solid(ix, iy, (int) Math.floor(z[i] + r))) z[i] = (float) Math.floor(z[i] + r) - r - 1e-3f;
        else if (w.solid(ix, iy, (int) Math.floor(z[i] - r))) z[i] = (float) Math.floor(z[i] - r) + 1 + r + 1e-3f;
    }

    public float lowestY() { float m = Float.MAX_VALUE; for (int i = 0; i < N; i++) m = Math.min(m, y[i] - RADIUS[i] * (unit / VOX)); return m; }

    // ------------------------------------------------------------------ particles -> SKIN group matrices
    private final float[] ax = new float[3], ay = new float[3], az = new float[3], ref = new float[3];

    /** Writes the world matrix of every SKIN group (voxel units, same contract as {@link Rig#fk}). */
    public void bones(Mat4[] out) {
        ref[0] = x[RS] - x[LS]; ref[1] = y[RS] - y[LS]; ref[2] = z[RS] - z[LS];
        frame(out[TORSO], PELVIS, NECK, PELVIS, 1, 0);
        frame(out[HEAD], NECK, HEAD_P, NECK, 1, 0);
        frame(out[L_UPPER_ARM], LE, LS, LS, 1, 0); frame(out[L_FOREARM], LW, LE, LE, 1, 0); frame(out[L_HAND], LW, LE, LW, 1, 0);
        frame(out[R_UPPER_ARM], RE, RS, RS, 1, 0); frame(out[R_FOREARM], RW, RE, RE, 1, 0); frame(out[R_HAND], RW, RE, RW, 1, 0);
        frame(out[L_THIGH], LK, LH, LH, 1, 0); frame(out[L_SHIN], LA, LK, LK, 1, 0);
        frame(out[R_THIGH], RK, RH, RH, 1, 0); frame(out[R_SHIN], RA, RK, RK, 1, 0);
    }

    /** Builds a matrix at particle 'at' whose +y axis runs from particle 'from' to particle 'to'. */
    private void frame(Mat4 m, int from, int to, int at, float s, int unused) {
        ay[0] = x[to] - x[from]; ay[1] = y[to] - y[from]; ay[2] = z[to] - z[from];
        norm(ay, 0, 1, 0);
        float dot = ref[0] * ay[0] + ref[1] * ay[1] + ref[2] * ay[2];
        ax[0] = ref[0] - ay[0] * dot; ax[1] = ref[1] - ay[1] * dot; ax[2] = ref[2] - ay[2] * dot;
        if (ax[0] * ax[0] + ax[1] * ax[1] + ax[2] * ax[2] < 1e-6f) { ax[0] = -fwd[2]; ax[1] = 0; ax[2] = fwd[0]; }
        norm(ax, 1, 0, 0);
        az[0] = ax[1] * ay[2] - ax[2] * ay[1]; az[1] = ax[2] * ay[0] - ax[0] * ay[2]; az[2] = ax[0] * ay[1] - ax[1] * ay[0];
        float[] a = m.m;
        a[0] = ax[0] * unit; a[1] = ax[1] * unit; a[2] = ax[2] * unit; a[3] = 0;
        a[4] = ay[0] * unit; a[5] = ay[1] * unit; a[6] = ay[2] * unit; a[7] = 0;
        a[8] = az[0] * unit; a[9] = az[1] * unit; a[10] = az[2] * unit; a[11] = 0;
        a[12] = x[at]; a[13] = y[at]; a[14] = z[at]; a[15] = 1;
    }

    private static void norm(float[] v, float fx, float fy, float fz) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (l < 1e-6f) { v[0] = fx; v[1] = fy; v[2] = fz; return; }
        v[0] /= l; v[1] /= l; v[2] /= l;
    }

    /** Knee/elbow interior angle in radians (pi = straight) - used by the quality gate. */
    public float jointAngle(int a, int mid, int b) {
        float ux = x[a] - x[mid], uy = y[a] - y[mid], uz = z[a] - z[mid], vx = x[b] - x[mid], vy = y[b] - y[mid], vz = z[b] - z[mid];
        float lu = (float) Math.sqrt(ux * ux + uy * uy + uz * uz), lv = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        return (float) Math.acos(Math.max(-1, Math.min(1, (ux * vx + uy * vy + uz * vz) / Math.max(1e-6f, lu * lv))));
    }

    /** Signed side of a hinge joint relative to the body forward (voxels; positive = legal side). */
    public float hingeSide(int a, int mid, int b, float sign) {
        forward();
        float mx = (x[a] + x[b]) * 0.5f, my = (y[a] + y[b]) * 0.5f, mz = (z[a] + z[b]) * 0.5f;
        return ((x[mid] - mx) * fwd[0] + (y[mid] - my) * fwd[1] + (z[mid] - mz) * fwd[2]) * sign / unit;
    }
}
