package absentialis.character;

import absentialis.engine.math.Mat4;

/**
 * The one skeleton every character shares (iteration P2/P3). Twelve SKIN groups with a fixed
 * numbering - 0 = head, 1 = torso, 2 = left upper arm ... - so any part whose voxels are assigned
 * to a group moves with that group's animation: a hairstyle skinned to HEAD needs no walk cycle of
 * its own. Animation data lives in {@link Anims} (keyframes per SKIN group), never in the parts.
 *
 * Units are character voxels (32 voxels = 1.8 m). The character faces -z, +x is its right side.
 * Limbs hang along -y from their pivot.
 */
public final class Rig {
    private Rig() {}

    public static final int HEAD = 0, TORSO = 1, L_UPPER_ARM = 2, L_FOREARM = 3, L_HAND = 4, R_UPPER_ARM = 5, R_FOREARM = 6, R_HAND = 7,
            L_THIGH = 8, L_SHIN = 9, R_THIGH = 10, R_SHIN = 11, GROUPS = 12;
    public static final String[] NAMES = {"head", "torso", "l_upper_arm", "l_forearm", "l_hand", "r_upper_arm", "r_forearm", "r_hand", "l_thigh", "l_shin", "r_thigh", "r_shin"};
    /** Parent group; -1 = the hips frame (legs do not inherit torso lean). */
    public static final int[] PARENT = {TORSO, -1, TORSO, L_UPPER_ARM, L_FOREARM, TORSO, R_UPPER_ARM, R_FOREARM, -1, L_THIGH, -1, R_THIGH};
    /** Evaluation order (parents first). */
    public static final int[] ORDER = {TORSO, HEAD, L_UPPER_ARM, L_FOREARM, L_HAND, R_UPPER_ARM, R_FOREARM, R_HAND, L_THIGH, L_SHIN, R_THIGH, R_SHIN};
    /** Pivot of each group in its parent's space. */
    public static final float[][] PIVOT = {
            {0, 12, 0}, {0, 12, 0}, {-6, 11, 0}, {0, -5, 0}, {0, -5, 0}, {6, 11, 0}, {0, -5, 0}, {0, -5, 0}, {-2, 12, 0}, {0, -6, 0}, {2, 12, 0}, {0, -6, 0}};
    /** Bone length along the group's axis (+y for head/torso, -y for limbs). */
    public static final float[] LENGTH = {8, 12, 5, 5, 2, 5, 5, 2, 6, 6, 6, 6};
    /** Voxel grid of each group: room for the body plus a clothing and a gear shell. */
    public static final int[][] GRID = {
            {12, 14, 12}, {14, 14, 12}, {6, 7, 6}, {6, 6, 6}, {6, 3, 6}, {6, 7, 6}, {6, 6, 6}, {6, 3, 6}, {6, 7, 6}, {6, 7, 8}, {6, 7, 6}, {6, 7, 8}};
    /** Grid coordinates of the group's pivot. */
    public static final float[][] ORIGIN = {
            {6, 2, 6}, {7, 0, 6}, {3, 6, 3}, {3, 5, 3}, {3, 2, 3}, {3, 6, 3}, {3, 5, 3}, {3, 2, 3}, {3, 6, 3}, {3, 6, 4}, {3, 6, 3}, {3, 6, 4}};

    public static final float VOX = 1.8f / 32f;
    public static final float UPPER = 5, FORE = 5.8f, THIGH = 6, SHIN = 6, LEG = THIGH + SHIN, SHOULDER_Y = 11, SHOULDER_X = 6, HIP_X = 2, HIP_Y = 12;
    public static final int UPPER_MASK = bit(HEAD) | bit(TORSO) | bit(L_UPPER_ARM) | bit(L_FOREARM) | bit(L_HAND) | bit(R_UPPER_ARM) | bit(R_FOREARM) | bit(R_HAND);
    public static final int LOWER_MASK = bit(L_THIGH) | bit(L_SHIN) | bit(R_THIGH) | bit(R_SHIN);
    public static final int ALL_MASK = UPPER_MASK | LOWER_MASK;
    public static final int ARMS_MASK = UPPER_MASK & ~(bit(HEAD) | bit(TORSO));

    public static int bit(int g) { return 1 << g; }
    public static boolean leftSide(int g) { return g == L_UPPER_ARM || g == L_FOREARM || g == L_HAND || g == L_THIGH || g == L_SHIN; }
    /** The mirrored group (left <-> right); head and torso map to themselves. */
    public static int mirror(int g) {
        if (g >= L_UPPER_ARM && g <= L_HAND) return g + 3;
        if (g >= R_UPPER_ARM && g <= R_HAND) return g - 3;
        if (g == L_THIGH || g == L_SHIN) return g + 2;
        if (g == R_THIGH || g == R_SHIN) return g - 2;
        return g;
    }

    /**
     * Forward kinematics. {@code base} maps character voxels to the world (position, facing, scale);
     * {@code hips} receives the hips frame and {@code out[g]} the matrix of each SKIN group.
     */
    public static void fk(Pose p, Mat4 base, Mat4 hips, Mat4[] out) {
        hips.set(base).translate(p.rootX, p.rootY, p.rootZ).rotateY(p.rootYaw).rotateX(p.rootPitch).rotateZ(p.rootRoll);
        for (int g : ORDER) {
            Mat4 par = PARENT[g] < 0 ? hips : out[PARENT[g]];
            float[] pv = PIVOT[g];
            out[g].set(par).translate(pv[0], pv[1], pv[2]).rotateY(p.rot[g * 3 + 1]).rotateZ(p.rot[g * 3 + 2]).rotateX(p.rot[g * 3]);
        }
    }

    // ------------------------------------------------------------------ small vector helpers (same conventions as Mat4)
    public static void rotX(float[] v, float a) { float c = (float) Math.cos(a), s = (float) Math.sin(a), y = c * v[1] - s * v[2], z = s * v[1] + c * v[2]; v[1] = y; v[2] = z; }
    public static void rotY(float[] v, float a) { float c = (float) Math.cos(a), s = (float) Math.sin(a), x = c * v[0] + s * v[2], z = -s * v[0] + c * v[2]; v[0] = x; v[2] = z; }
    public static void rotZ(float[] v, float a) { float c = (float) Math.cos(a), s = (float) Math.sin(a), x = c * v[0] - s * v[1], y = s * v[0] + c * v[1]; v[0] = x; v[1] = y; }

    /**
     * Two-bone leg solve in the sagittal plane. Target is the ankle relative to the hip pivot:
     * {@code fwd} voxels ahead (positive = forward) and {@code down} voxels below.
     * Writes {thighPitch, shinPitch}; the knee always bends forward.
     */
    public static void legIk(float fwd, float down, float[] out) {
        float d = (float) Math.sqrt(fwd * fwd + down * down);
        d = Math.max(3.2f, Math.min(LEG - 0.06f, d));
        float aim = (float) Math.atan2(fwd, down);
        float phi = (float) Math.acos(Math.min(1, d / (2 * THIGH)));
        float bend = (float) (Math.PI - Math.acos(Math.max(-1, Math.min(1, (THIGH * THIGH + SHIN * SHIN - d * d) / (2 * THIGH * SHIN)))));
        out[0] = aim + phi;
        out[1] = -bend;
    }

    /**
     * Two-bone arm solve. {@code d} is the wrist target relative to the shoulder in torso space.
     * Writes {upperYaw, upperPitch, forearmPitch}; the elbow drops below the shoulder-hand line.
     */
    public static void armIk(float dx, float dy, float dz, float[] out) {
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float reach = Math.max(1.5f, Math.min(UPPER + FORE - 0.05f, dist));
        float hor = (float) Math.sqrt(dx * dx + dz * dz);
        float yaw = hor < 1e-3f ? 0 : (float) Math.atan2(-dx, -dz);
        float pitch = (float) Math.acos(Math.max(-1, Math.min(1, -dy / Math.max(1e-3f, dist))));
        // 1.1: an arm hanging at the side has its hand almost straight under the shoulder, and the plane the arm bends in
        // was read off the little that the hand was in front of, behind or outside it - so as a hand at the thigh swung
        // through the line under the shoulder with the walk, the whole arm turned half a circle about its own length while
        // the hand (and the pistol in it) hardly moved. The arm now bends in a plane that cannot flip: a hand behind the
        // shoulder is reached by swinging the arm back, not by turning it round to face backwards, and close under the
        // shoulder the plane settles to straight ahead.
        if (Math.abs(yaw) > Math.PI / 2) { yaw -= Math.signum(yaw) * (float) Math.PI; pitch = -pitch; }
        float near = Math.max(0, Math.min(1, (hor - 0.25f) / 1.35f));
        yaw *= near * near * (3 - 2 * near);
        float phi = (float) Math.acos(Math.max(-1, Math.min(1, (UPPER * UPPER + reach * reach - FORE * FORE) / (2 * UPPER * reach))));
        float bend = (float) (Math.PI - Math.acos(Math.max(-1, Math.min(1, (UPPER * UPPER + FORE * FORE - reach * reach) / (2 * UPPER * FORE)))));
        out[0] = yaw;
        out[1] = pitch - phi;
        out[2] = bend;
    }

    /**
     * Matrix for a limb group whose pivot is at p and which hangs along the unit direction d
     * (its -y axis). Used where a bone is placed from two points instead of from joint angles.
     */
    public static void aimBone(Mat4 m, float px, float py, float pz, float dx, float dy, float dz, float scale) {
        float yx = -dx, yy = -dy, yz = -dz;
        // reference "right" axis, re-orthogonalised against the bone
        float rx = 1, ry = 0, rz = 0;
        float dot = rx * yx + ry * yy + rz * yz;
        if (Math.abs(dot) > 0.95f) { rx = 0; rz = 1; dot = yz; }
        float xx = rx - yx * dot, xy = ry - yy * dot, xz = rz - yz * dot;
        float l = (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
        xx /= l; xy /= l; xz /= l;
        float zx = xy * yz - xz * yy, zy = xz * yx - xx * yz, zz = xx * yy - xy * yx;
        float[] a = m.m;
        a[0] = xx * scale; a[1] = xy * scale; a[2] = xz * scale; a[3] = 0;
        a[4] = yx * scale; a[5] = yy * scale; a[6] = yz * scale; a[7] = 0;
        a[8] = zx * scale; a[9] = zy * scale; a[10] = zz * scale; a[11] = 0;
        a[12] = px; a[13] = py; a[14] = pz; a[15] = 1;
    }
}
