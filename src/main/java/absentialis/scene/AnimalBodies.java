package absentialis.scene;

import absentialis.engine.math.Mat4;
import absentialis.scene.Animals.Animal;
import absentialis.scene.Animals.Kind;
import absentialis.sydney.SoftBody;

/**
 * 1.2, what an animal's body is to a bullet and to the physics engine.
 *
 * HIT VOLUMES. In 1.1 every animal was one ball at the middle of its height - so a dog was hit by rounds that went
 * over its back and missed by ones that struck its head, and a spider's legs, which are most of what is seen of it,
 * were not there at all. Each kind now has boxes in its own frame (it faces -z; x is its right, y is up), turned with
 * it, and a round is tested against those. The head counts for more; a spider's legs for less.
 *
 * BODIES FOR SYDNEY. A dead animal is no longer turned on its side by the drawing code. At the moment of death it
 * is handed to the Sydney Engine as a {@link SoftBody}: particles where its shoulders, hips, head, feet, wing tips and
 * tail were, sticks for its bones, limits for its joints - built from the pose it was in and moving as it was moving.
 * What is drawn afterwards is read back off the particles ({@link #pose}).
 *
 * Every rig starts the same way so that one piece of code can find its trunk: particles 0-3 are the four corners of
 * the trunk (front left, front right, rear left, rear right), 4 is the top of the back, 5 is the head.
 */
final class AnimalBodies {
    private AnimalBodies() {}

    static final int FL = 0, FR = 1, RL = 2, RR = 3, TOP = 4, HEAD = 5;

    // ------------------------------------------------------------------ hit volumes
    /** {x0, y0, z0, x1, y1, z1, damage multiplier}, in voxels of the kind's own size. */
    private static final float[][] DOG_BOX = {{-4.4f, 6.5f, -9f, 4.4f, 15.2f, 5.8f, 1f}, {-3.4f, 9.6f, -15.2f, 3.4f, 17.4f, -6f, 1.6f}, {-3.3f, 0, -7.6f, 3.3f, 8f, 4.6f, 0.8f}};
    private static final float[][] CROW_BOX = {{-3.6f, 0, -6f, 3.6f, 10.5f, 8.5f, 1f}};
    private static final float[][] CROW_AIR = {{-7.5f, 0.5f, -6f, 7.5f, 10.5f, 8.5f, 1f}};
    private static final float[][] BAT_BOX = {{-4.5f, 0, -3f, 4.5f, 9.5f, 3f, 1f}};
    private static final float[][] BAT_AIR = {{-10.5f, 0, -3.5f, 10.5f, 9.5f, 3.5f, 1f}};
    private static final float[][] CHICKEN_BOX = {{-3.6f, 4f, -4.6f, 3.6f, 11.8f, 4.6f, 1f}, {-2.6f, 8f, -8f, 2.6f, 15.4f, -2f, 1.5f}, {-3f, 0, -1.6f, 3f, 5.2f, 2.6f, 1f}};
    private static final float[][] SPIDER_BOX = {{-4.6f, 3f, -11.6f, 4.6f, 12f, -2.6f, 1.5f}, {-3.6f, 3.6f, -3.2f, 3.6f, 10.6f, 3.6f, 1f}, {-5.6f, 3.2f, 2.6f, 5.6f, 12.4f, 15.6f, 1f}, {-15f, 0, -8f, 15f, 6.5f, 8f, 0.45f}, {-11f, 6.5f, -7f, 11f, 13f, 6f, 0.45f}};
    private static final float[][] LING_BOX = {{-9f, 0, -12f, 9f, 13f, 16f, 1f}};

    static float[][] boxes(Animal a) {
        return switch (a.kind) {
            case DOG, IDOG -> DOG_BOX;
            case CROW -> a.flying ? CROW_AIR : CROW_BOX;
            case BAT -> a.flying ? BAT_AIR : BAT_BOX;
            case CHICKEN -> CHICKEN_BOX;
            case SPIDERLING -> LING_BOX;
            default -> SPIDER_BOX;
        };
    }

    /**
     * Where along a ray (origin, unit direction, no further than maxT) it first enters this animal, or -1. {@code mult}
     * receives what the part struck counts for.
     */
    static float rayHit(Animal a, float ox, float oy, float oz, float dx, float dy, float dz, float maxT, float[] mult) {
        float c = (float) Math.cos(a.yaw), s = (float) Math.sin(a.yaw), u = a.kind.unit;
        // into the animal's frame, in its voxels
        float rx = ox - a.x, ry = oy - a.y, rz = oz - a.z;
        float lx = (rx * c - rz * s) / u, ly = ry / u, lz = (rx * s + rz * c) / u;
        float ldx = dx * c - dz * s, ldy = dy, ldz = dx * s + dz * c;
        if (a.ceilT > 0.5f) { float h = a.kind.height / u; lx = -lx; ldx = -ldx; ly = h - ly; ldy = -ldy; }
        float best = -1;
        float lim = maxT / u;
        for (float[] b : boxes(a)) {
            float t0 = 0, t1 = lim;
            // slabs
            if (Math.abs(ldx) < 1e-6f) { if (lx < b[0] || lx > b[3]) continue; } else { float a0 = (b[0] - lx) / ldx, a1 = (b[3] - lx) / ldx; t0 = Math.max(t0, Math.min(a0, a1)); t1 = Math.min(t1, Math.max(a0, a1)); }
            if (Math.abs(ldy) < 1e-6f) { if (ly < b[1] || ly > b[4]) continue; } else { float a0 = (b[1] - ly) / ldy, a1 = (b[4] - ly) / ldy; t0 = Math.max(t0, Math.min(a0, a1)); t1 = Math.min(t1, Math.max(a0, a1)); }
            if (Math.abs(ldz) < 1e-6f) { if (lz < b[2] || lz > b[5]) continue; } else { float a0 = (b[2] - lz) / ldz, a1 = (b[5] - lz) / ldz; t0 = Math.max(t0, Math.min(a0, a1)); t1 = Math.min(t1, Math.max(a0, a1)); }
            if (t0 > t1) continue;
            // the nearest part along the line; where two are entered together, the one that counts for more (a head is a head)
            float t = t0 * u;
            if (best < 0 || t < best - 0.02f) { best = t; mult[0] = b[6]; }
            else if (t <= best + 0.02f && b[6] > mult[0]) mult[0] = b[6];
        }
        return best;
    }

    /** The reach of the largest box of a kind (metres): how near a ray has to pass before the boxes are worth testing. */
    static float reach(Kind k) { return (k == Kind.SPIDER || k == Kind.BROOD ? 17f : k == Kind.SPIDERLING ? 16f : k == Kind.DOG || k == Kind.IDOG ? 17.5f : 11f) * k.unit; }

    // ------------------------------------------------------------------ rigs
    /** Where the middle of the four trunk corners sits in the kind's own voxels: {height, z}. */
    static float[] trunkAt(Kind k) {
        return switch (k) { case DOG, IDOG -> new float[]{10.5f, -0.5f}; case CROW -> new float[]{4.5f, 0}; case CHICKEN -> new float[]{7f, 0}; case BAT -> new float[]{3f, 0}; default -> new float[]{7f, 0}; };
    }

    /** Leg i of a spider: {hip x, y, z, knee x, y, z, foot x, y, z} in its voxels, as it stands. */
    static float[] spiderLeg(int i) {
        int row = i / 2, s = (i & 1) == 0 ? -1 : 1;
        float fan = (row - 1.5f) * 0.42f, hx = s * 3f, hy = 7.5f, hz = (row - 1.5f) * 1.4f;
        float c1 = (float) Math.cos(0.55f), s1 = (float) Math.sin(0.55f), c2 = (float) Math.cos(-1.2f), s2 = (float) Math.sin(-1.2f), cf = (float) Math.cos(fan), sf = (float) Math.sin(fan);
        float kx = hx + s * 15.5f * c1 * cf, ky = hy + 15.5f * s1, kz = hz + 15.5f * c1 * sf;
        return new float[]{hx, hy, hz, kx, ky, kz, kx + s * 16f * c2 * cf, ky + 16f * s2, kz + 16f * c2 * sf};
    }

    private static final float[] L = new float[23 * 5];

    private static int put(int i, float x, float y, float z, float r, float kg) { L[i * 5] = x; L[i * 5 + 1] = y; L[i * 5 + 2] = z; L[i * 5 + 3] = r; L[i * 5 + 4] = kg; return i; }

    /** The animal as it stands (or flies, or hangs) this instant, as a body for Sydney: built in its own voxels, then set down in the world. */
    static SoftBody rig(Animal a) {
        Kind k = a.kind;
        int n;
        switch (k) {
            case DOG, IDOG -> {
                put(FL, -2.5f, 10.5f, -5, 2.4f, 4); put(FR, 2.5f, 10.5f, -5, 2.4f, 4); put(RL, -2.5f, 10.5f, 4, 2.4f, 4); put(RR, 2.5f, 10.5f, 4, 2.4f, 4);
                put(TOP, 0, 14.4f, -0.5f, 1.6f, 4); put(HEAD, 0, 13.5f, -10, 2.7f, 3);
                put(6, -2, 0.7f, -6.2f, 0.7f, 0.8f); put(7, 2, 0.7f, -6.2f, 0.7f, 0.8f); put(8, -2, 0.7f, 3.2f, 0.7f, 0.8f); put(9, 2, 0.7f, 3.2f, 0.7f, 0.8f);
                put(10, 0, 16.8f, -2.8f, 0.7f, 0.3f);
                n = 11;
            }
            case CROW -> {
                boolean out = a.flying;
                put(FL, -1.6f, 4.5f, -2.5f, 1.5f, 0.1f); put(FR, 1.6f, 4.5f, -2.5f, 1.5f, 0.1f); put(RL, -1.6f, 4.5f, 2.5f, 1.5f, 0.1f); put(RR, 1.6f, 4.5f, 2.5f, 1.5f, 0.1f);
                put(TOP, 0, 7.8f, 0, 1.2f, 0.1f); put(HEAD, 0, 8.6f, -4, 1.5f, 0.06f);
                put(6, out ? -6f : -2.3f, out ? 6.5f : 2.6f, 0.3f, 0.6f, 0.03f); put(7, out ? 6f : 2.3f, out ? 6.5f : 2.6f, 0.3f, 0.6f, 0.03f);
                put(8, 0, 5.3f, 8, 0.6f, 0.02f);
                n = 9;
            }
            case CHICKEN -> {
                put(FL, -2.4f, 7, -3.2f, 2.2f, 0.4f); put(FR, 2.4f, 7, -3.2f, 2.2f, 0.4f); put(RL, -2.4f, 7, 3.2f, 2.2f, 0.4f); put(RR, 2.4f, 7, 3.2f, 2.2f, 0.4f);
                put(TOP, 0, 10.8f, 0, 1.6f, 0.4f); put(HEAD, 0, 12.4f, -5, 1.9f, 0.2f);
                put(6, -3.3f, 5.5f, 0.5f, 0.7f, 0.06f); put(7, 3.3f, 5.5f, 0.5f, 0.7f, 0.06f);
                put(8, -1.5f, 0.6f, 0.5f, 0.7f, 0.08f); put(9, 1.5f, 0.6f, 0.5f, 0.7f, 0.08f);
                n = 10;
            }
            case BAT -> {
                put(FL, -1.2f, 3, -0.9f, 1.1f, 0.01f); put(FR, 1.2f, 3, -0.9f, 1.1f, 0.01f); put(RL, -1.2f, 3, 0.9f, 1.1f, 0.01f); put(RR, 1.2f, 3, 0.9f, 1.1f, 0.01f);
                put(TOP, 0, 5.4f, 0, 1f, 0.01f); put(HEAD, 0, 7f, 0, 1.3f, 0.008f);
                put(6, a.flying ? -9f : -2.6f, a.flying ? 3 : 0.4f, 0, 0.6f, 0.004f); put(7, a.flying ? 9f : 2.6f, a.flying ? 3 : 0.4f, 0, 0.6f, 0.004f);
                n = 8;
            }
            default -> {
                put(FL, -2.6f, 7, -2.6f, 2.6f, 3); put(FR, 2.6f, 7, -2.6f, 2.6f, 3); put(RL, -2.6f, 7, 2.6f, 2.6f, 3); put(RR, 2.6f, 7, 2.6f, 2.6f, 3);
                put(TOP, 0, 10, 0, 1.6f, 3); put(HEAD, 0, 7.5f, -7, 3.6f, 3);
                put(6, 0, 7.8f, 9.5f, 4.4f, 8);
                for (int i = 0; i < 8; i++) { float[] g = spiderLeg(i); put(7 + i, g[3], g[4], g[5], 0.9f, 0.4f); put(15 + i, g[6], g[7], g[8], 0.8f, 0.2f); }
                n = 23;
            }
        }
        SoftBody b = new SoftBody(n);
        float c = (float) Math.cos(a.yaw), s = (float) Math.sin(a.yaw), u = k.unit, h = k.height / u;
        boolean over = a.ceilT > 0.5f;
        for (int i = 0; i < n; i++) {
            float lx = L[i * 5], ly = L[i * 5 + 1], lz = L[i * 5 + 2];
            if (over) { lx = -lx; ly = h - ly; }
            b.particle(i, a.x + (lx * c + lz * s) * u, a.y + ly * u, a.z + (-lx * s + lz * c) * u, L[i * 5 + 3] * u, L[i * 5 + 4]);
        }
        // the trunk is one piece
        for (int i = 0; i < 5; i++) for (int j = i + 1; j < 5; j++) b.stick(i, j);
        switch (k) {
            case DOG, IDOG -> {
                b.stick(HEAD, TOP).limit(HEAD, FL, 0.78f, 1.22f).limit(HEAD, FR, 0.78f, 1.22f);
                int[][] legs = {{6, FL, RL}, {7, FR, RR}, {8, RL, FL}, {9, RR, FR}};
                for (int[] l : legs) b.stick(l[0], l[1]).limit(l[0], TOP, 0.5f, 1.06f).limit(l[0], l[2], 0.55f, 1.3f);
                b.limit(6, 7, 0.4f, 3.2f).limit(8, 9, 0.4f, 3.2f);
                b.stick(10, RL).stick(10, RR).limit(10, TOP, 0.5f, 2.2f);
            }
            case CROW -> {
                b.stick(HEAD, TOP).limit(HEAD, FL, 0.8f, 1.2f).limit(HEAD, FR, 0.8f, 1.2f);
                b.stick(6, FL).stick(6, RL).limit(6, TOP, 0.35f, 1.6f).stick(7, FR).stick(7, RR).limit(7, TOP, 0.35f, 1.6f);
                b.stick(8, RL).stick(8, RR).limit(8, TOP, 0.8f, 1.25f);
                b.air = 0.975f; b.bounce = 0.05f; b.slide = 0.6f;
            }
            case CHICKEN -> {
                b.stick(HEAD, TOP).limit(HEAD, FL, 0.7f, 1.25f).limit(HEAD, FR, 0.7f, 1.25f);
                b.stick(6, FL).stick(6, RL).limit(6, TOP, 0.4f, 1.5f).stick(7, FR).stick(7, RR).limit(7, TOP, 0.4f, 1.5f);
                b.stick(8, RL).stick(8, FL).limit(8, TOP, 0.5f, 1.05f).stick(9, RR).stick(9, FR).limit(9, TOP, 0.5f, 1.05f);
                b.air = 0.98f; b.bounce = 0.08f; b.slide = 0.65f;
            }
            case BAT -> {
                b.stick(HEAD, TOP).stick(HEAD, FL).stick(HEAD, FR);
                b.stick(6, FL).stick(6, RL).limit(6, TOP, 0.3f, 1.4f).stick(7, FR).stick(7, RR).limit(7, TOP, 0.3f, 1.4f);
                b.air = 0.95f; b.bounce = 0.02f; b.slide = 0.5f;
            }
            default -> {
                b.stick(HEAD, FL).stick(HEAD, FR).stick(HEAD, TOP);
                b.stick(6, RL).stick(6, RR).limit(6, TOP, 0.85f, 1.12f);
                for (int i = 0; i < 8; i++) {
                    boolean left = (i & 1) == 0;
                    b.stick(7 + i, left ? FL : FR).stick(7 + i, left ? RL : RR).limit(7 + i, TOP, 0.45f, 1.25f);
                    b.stick(15 + i, 7 + i).limit(15 + i, TOP, 0.22f, 1.2f);
                }
                b.bounce = 0.06f; b.slide = 0.7f;
            }
        }
        return b;
    }

    // ------------------------------------------------------------------ reading a body back
    /** What the drawing code needs of a body: where the trunk is and how it lies, and every particle in the trunk's own voxels. */
    static final class Pose {
        final Mat4 base = new Mat4();
        final float[] l = new float[23 * 3];
        float lx(int i) { return l[i * 3]; } float ly(int i) { return l[i * 3 + 1]; } float lz(int i) { return l[i * 3 + 2]; }
    }

    /**
     * Reads a body back. {@code out.base} is the matrix the animal is drawn with - the same space the living one is
     * drawn in (its voxels, x right, y up, z back, the origin under its trunk) - and {@code out.l} is each particle in that space.
     */
    static void pose(Animal a, SoftBody b, Pose out) {
        float u = a.kind.unit;
        float fx = (b.x[FL] + b.x[FR]) * 0.5f, fy = (b.y[FL] + b.y[FR]) * 0.5f, fz = (b.z[FL] + b.z[FR]) * 0.5f;
        float bx = (b.x[RL] + b.x[RR]) * 0.5f, by = (b.y[RL] + b.y[RR]) * 0.5f, bz = (b.z[RL] + b.z[RR]) * 0.5f;
        float zx = bx - fx, zy = by - fy, zz = bz - fz, zl = Math.max(1e-5f, (float) Math.sqrt(zx * zx + zy * zy + zz * zz));
        zx /= zl; zy /= zl; zz /= zl;
        float rx = (b.x[FR] + b.x[RR] - b.x[FL] - b.x[RL]), ry = (b.y[FR] + b.y[RR] - b.y[FL] - b.y[RL]), rz = (b.z[FR] + b.z[RR] - b.z[FL] - b.z[RL]);
        float dot = rx * zx + ry * zy + rz * zz;
        rx -= zx * dot; ry -= zy * dot; rz -= zz * dot;
        float rl = Math.max(1e-5f, (float) Math.sqrt(rx * rx + ry * ry + rz * rz));
        rx /= rl; ry /= rl; rz /= rl;
        // up = back x right
        float ux = zy * rz - zz * ry, uy = zz * rx - zx * rz, uz = zx * ry - zy * rx;
        float[] at = trunkAt(a.kind);
        float cx = (fx + bx) * 0.5f, cy = (fy + by) * 0.5f, cz = (fz + bz) * 0.5f;
        float ox = cx - (ux * at[0] + zx * at[1]) * u, oy = cy - (uy * at[0] + zy * at[1]) * u, oz = cz - (uz * at[0] + zz * at[1]) * u;
        float[] m = out.base.m;
        m[0] = rx * u; m[1] = ry * u; m[2] = rz * u; m[3] = 0;
        m[4] = ux * u; m[5] = uy * u; m[6] = uz * u; m[7] = 0;
        m[8] = zx * u; m[9] = zy * u; m[10] = zz * u; m[11] = 0;
        m[12] = ox; m[13] = oy; m[14] = oz; m[15] = 1;
        for (int i = 0; i < b.n; i++) {
            float px = b.x[i] - ox, py = b.y[i] - oy, pz = b.z[i] - oz;
            out.l[i * 3] = (px * rx + py * ry + pz * rz) / u; out.l[i * 3 + 1] = (px * ux + py * uy + pz * uz) / u; out.l[i * 3 + 2] = (px * zx + py * zy + pz * zz) / u;
        }
    }

    /** Turns a matrix so that what hangs down from its origin (a leg, a folded wing, a tail) lies along a direction instead. */
    static Mat4 hang(Mat4 m, float vx, float vy, float vz) {
        float len = Math.max(1e-5f, (float) Math.sqrt(vx * vx + vy * vy + vz * vz));
        return m.rotateZ((float) Math.atan2(vx, -vy)).rotateX((float) -Math.asin(Math.max(-1, Math.min(1, vz / len))));
    }

    /** Turns a matrix so that what runs out along its +x (a spider's leg) lies along a direction instead. */
    static Mat4 along(Mat4 m, float vx, float vy, float vz) {
        float len = Math.max(1e-5f, (float) Math.sqrt(vx * vx + vy * vy + vz * vz));
        return m.rotateY((float) Math.atan2(-vz, vx)).rotateZ((float) Math.asin(Math.max(-1, Math.min(1, vy / len))));
    }
}
