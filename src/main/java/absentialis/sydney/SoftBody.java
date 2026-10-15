package absentialis.sydney;

/**
 * 1.2, Sydney Engine: a body of any shape. The person's ragdoll ({@code character.Ragdoll}) is fifteen particles in
 * the one arrangement a person has; this is the same physics - particles moved by Verlet steps, rigid sticks between
 * them, limits on how near and how far two of them may come, the voxel world to land on and slide across - with the
 * arrangement left to whoever builds it. A dog is twelve particles, a crow nine, a spider twenty-three.
 *
 * It is driven the same way too: forces may be put on any particle before a step ({@link #force}), which is how
 * something still alive in it twitches, kicks, or curls up. Nothing here knows what kind of creature it is, or of any
 * game: the world is whatever answers {@link Solid}.
 */
public final class SoftBody {
    /** The world, as much as a falling body needs of it. */
    public interface Solid { boolean solid(int x, int y, int z); }

    public static final float STEP = 1 / 90f, GRAVITY = 22f;

    public final int n;
    public final float[] x, y, z, radius, invMass;
    private final float[] px, py, pz, fx, fy, fz;
    public final boolean[] grounded;
    private int[] stickA = new int[16], stickB = new int[16];
    private float[] stickLen = new float[16];
    private int sticks;
    private int[] limA = new int[8], limB = new int[8];
    private float[] limMin = new float[8], limMax = new float[8];
    private int limits;
    private float acc, still;
    /** Seconds it has been simulated, and whether it has come to rest (a settled body costs nothing until it is {@link #wake woken}). */
    public float age;
    public boolean settled;
    /** How much of its speed a particle sliding on the ground keeps each step, and how lively a landing is (0 = dead stop). */
    public float slide = 0.80f, bounce = 0.18f, air = 0.992f;

    public SoftBody(int particles) {
        n = particles;
        x = new float[n]; y = new float[n]; z = new float[n]; px = new float[n]; py = new float[n]; pz = new float[n];
        fx = new float[n]; fy = new float[n]; fz = new float[n]; radius = new float[n]; invMass = new float[n];
        grounded = new boolean[n];
    }

    /** Places a particle (metres), at rest. */
    public SoftBody particle(int i, float wx, float wy, float wz, float r, float massKg) {
        x[i] = px[i] = wx; y[i] = py[i] = wy; z[i] = pz[i] = wz;
        radius[i] = r; invMass[i] = 1f / Math.max(0.01f, massKg);
        return this;
    }

    /** A rigid stick between two particles, as long as they are apart now. */
    public SoftBody stick(int a, int b) {
        if (sticks == stickA.length) { stickA = java.util.Arrays.copyOf(stickA, sticks * 2); stickB = java.util.Arrays.copyOf(stickB, sticks * 2); stickLen = java.util.Arrays.copyOf(stickLen, sticks * 2); }
        stickA[sticks] = a; stickB[sticks] = b; stickLen[sticks] = dist(a, b);
        sticks++;
        return this;
    }

    /** Two particles that may come no nearer than {@code min} times their present distance, nor go further than {@code max} times it: a joint's range. */
    public SoftBody limit(int a, int b, float min, float max) {
        if (limits == limA.length) { limA = java.util.Arrays.copyOf(limA, limits * 2); limB = java.util.Arrays.copyOf(limB, limits * 2); limMin = java.util.Arrays.copyOf(limMin, limits * 2); limMax = java.util.Arrays.copyOf(limMax, limits * 2); }
        float d = dist(a, b);
        limA[limits] = a; limB[limits] = b; limMin[limits] = d * min; limMax[limits] = d * max;
        limits++;
        return this;
    }

    public float dist(int a, int b) { float dx = x[a] - x[b], dy = y[a] - y[b], dz = z[a] - z[b]; return (float) Math.sqrt(dx * dx + dy * dy + dz * dz); }

    /** Gives every particle a velocity (metres a second): what the body was doing when it stopped being animated. */
    public void velocity(float vx, float vy, float vz) { for (int i = 0; i < n; i++) { px[i] = x[i] - vx * STEP; py[i] = y[i] - vy * STEP; pz[i] = z[i] - vz * STEP; } wake(); }

    /** A blow: a change of velocity for one particle, shared out to the rest by the sticks over the steps that follow. */
    public void impulse(int i, float dvx, float dvy, float dvz) { px[i] -= dvx * STEP; py[i] -= dvy * STEP; pz[i] -= dvz * STEP; wake(); }

    /** A blow to the whole body, strongest on the particles nearest a point. */
    public void impulseAt(float wx, float wy, float wz, float dvx, float dvy, float dvz, float reach) {
        for (int i = 0; i < n; i++) {
            float dx = x[i] - wx, dy = y[i] - wy, dz = z[i] - wz, k = Math.max(0.25f, 1 - (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / Math.max(0.01f, reach));
            impulse(i, dvx * k, dvy * k, dvz * k);
        }
    }

    /** A force in newtons on one particle for the next step. */
    public void force(int i, float nx, float ny, float nz) { fx[i] += nx; fy[i] += ny; fz[i] += nz; }

    public float vx(int i) { return (x[i] - px[i]) / STEP; }
    public float vy(int i) { return (y[i] - py[i]) / STEP; }
    public float vz(int i) { return (z[i] - pz[i]) / STEP; }

    public void wake() { settled = false; still = 0; }

    /** Moves the whole body (carried, or set down somewhere else), at rest. */
    public void moveBy(float dx, float dy, float dz) { for (int i = 0; i < n; i++) { x[i] += dx; y[i] += dy; z[i] += dz; px[i] = x[i]; py[i] = y[i]; pz[i] = z[i]; } wake(); }

    public float centreX() { float s = 0; for (int i = 0; i < n; i++) s += x[i]; return s / n; }
    public float centreY() { float s = 0; for (int i = 0; i < n; i++) s += y[i]; return s / n; }
    public float centreZ() { float s = 0; for (int i = 0; i < n; i++) s += z[i]; return s / n; }
    public float lowestY() { float m = Float.MAX_VALUE; for (int i = 0; i < n; i++) m = Math.min(m, y[i] - radius[i]); return m; }

    /** Advances by a frame's worth of fixed steps. {@code driver}, when given, is run before each step to put forces on. */
    public void update(Solid w, float dt, Runnable driver) {
        if (settled) return;
        acc += Math.min(dt, 0.1f);
        while (acc >= STEP && !settled) { acc -= STEP; if (driver != null) driver.run(); step(w); }
    }

    private void step(Solid w) {
        age += STEP;
        float moved = 0;
        for (int i = 0; i < n; i++) {
            float vx = (x[i] - px[i]) * air, vy = (y[i] - py[i]) * air, vz = (z[i] - pz[i]) * air;
            if (grounded[i]) { vx *= slide; vz *= slide; }
            px[i] = x[i]; py[i] = y[i]; pz[i] = z[i];
            x[i] += vx + fx[i] * invMass[i] * STEP * STEP;
            y[i] += vy + (fy[i] * invMass[i] - GRAVITY) * STEP * STEP;
            z[i] += vz + fz[i] * invMass[i] * STEP * STEP;
            fx[i] = fy[i] = fz[i] = 0;
        }
        for (int it = 0; it < 6; it++) {
            for (int s = 0; s < sticks; s++) solve(stickA[s], stickB[s], stickLen[s], stickLen[s], 1f);
            for (int l = 0; l < limits; l++) solve(limA[l], limB[l], limMin[l], limMax[l], 0.8f);
            for (int i = 0; i < n; i++) collide(w, i);
        }
        for (int i = 0; i < n; i++) { float dx = x[i] - px[i], dy = y[i] - py[i], dz = z[i] - pz[i]; moved = Math.max(moved, dx * dx + dy * dy + dz * dz); }
        // at rest: nothing has moved more than a hair for a third of a second
        if (moved < 2.5e-7f) { still += STEP; if (still > 0.35f && age > 0.5f) settled = true; } else still = 0;
        if (age > 14f) settled = true;
    }

    private void solve(int a, int b, float min, float max, float stiff) {
        float dx = x[b] - x[a], dy = y[b] - y[a], dz = z[b] - z[a], d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-6f) { dx = 0; dy = 1e-3f; dz = 0; d = 1e-3f; }
        float want = d < min ? min : d > max ? max : d;
        if (want == d) return;
        float wa = invMass[a], wb = invMass[b], k = (d - want) / d / (wa + wb) * stiff;
        x[a] += dx * k * wa; y[a] += dy * k * wa; z[a] += dz * k * wa;
        x[b] -= dx * k * wb; y[b] -= dy * k * wb; z[b] -= dz * k * wb;
    }

    private void collide(Solid w, int i) {
        float r = radius[i];
        grounded[i] = false;
        if (w == null) return;
        int ix = (int) Math.floor(x[i]), iz = (int) Math.floor(z[i]);
        int fy = (int) Math.floor(y[i] - r);
        if (w.solid(ix, fy, iz)) {
            if (!w.solid(ix, fy + 1, iz)) {
                float top = fy + 1 + r;
                // a landing gives a little of the fall back; a part lying on the ground gives none
                float vy = y[i] - py[i];
                y[i] = top; grounded[i] = true;
                if (vy < -0.012f) py[i] = top + vy * bounce;
            } else {
                // inside something: back to where it was
                x[i] = px[i]; z[i] = pz[i];
                if (w.solid((int) Math.floor(x[i]), fy, (int) Math.floor(z[i])) && w.solid((int) Math.floor(x[i]), fy + 1, (int) Math.floor(z[i]))) y[i] = py[i];
            }
            ix = (int) Math.floor(x[i]); iz = (int) Math.floor(z[i]);
        } else if (w.solid(ix, (int) Math.floor(y[i] - r - 0.02f), iz)) grounded[i] = true;
        int iy = (int) Math.floor(y[i]);
        if (w.solid(ix, (int) Math.floor(y[i] + r), iz) && !w.solid(ix, iy, iz)) y[i] = (float) Math.floor(y[i] + r) - r - 1e-3f;
        if (w.solid((int) Math.floor(x[i] + r), iy, iz)) x[i] = (float) Math.floor(x[i] + r) - r - 1e-3f;
        else if (w.solid((int) Math.floor(x[i] - r), iy, iz)) x[i] = (float) Math.floor(x[i] - r) + 1 + r + 1e-3f;
        if (w.solid(ix, iy, (int) Math.floor(z[i] + r))) z[i] = (float) Math.floor(z[i] + r) - r - 1e-3f;
        else if (w.solid(ix, iy, (int) Math.floor(z[i] - r))) z[i] = (float) Math.floor(z[i] - r) + 1 + r + 1e-3f;
    }
}
