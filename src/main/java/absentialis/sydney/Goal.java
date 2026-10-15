package absentialis.sydney;

/**
 * What the behaviours ask of the body in one step: intent, not mechanics. A direction for each bone in the trunk's
 * own frame (x right, y up, z back) with how hard to hold it, which way the trunk should point, whether to balance,
 * whether to push up, and where to send a hand or a foot.
 *
 * Several behaviours write into the same goal. A channel is claimed by the first (highest priority) behaviour that
 * sets it and later ones leave it alone, so a hand going to a wound and legs keeping balance can coexist while a
 * brace for impact takes the arms away from both.
 */
public final class Goal {
    public final float[][] dir = new float[PhysicsRig.NJ][3];
    /** Motor gain per joint, 0 = slack. Negative while unclaimed. */
    public final float[] gain = new float[PhysicsRig.NJ];
    /** Scales every torque limit: how much of its strength the body is using. */
    public float strength = 1;
    /** Legs carrying weight are stiffened. */
    public boolean stance;
    public final float[] up = {0, 1, 0};
    public float posture = -1, postureOnGround, balance = -1, lift = -1, height;

    /** A limb end sent to a point in the world. */
    public static final class Reach {
        public boolean on;
        public int particle, against;
        public float x, y, z, force, hz = 4;
    }
    /** Left hand, right hand, left foot, right foot. */
    public final Reach[] reach = {new Reach(), new Reach(), new Reach(), new Reach()};
    private final boolean[] reachClaimed = new boolean[4];

    public void clear() {
        for (int j = 0; j < PhysicsRig.NJ; j++) gain[j] = -1;
        strength = 1; stance = false; posture = balance = lift = -1; postureOnGround = 0; height = 0;
        up[0] = 0; up[1] = 1; up[2] = 0;
        for (int i = 0; i < 4; i++) { reach[i].on = false; reachClaimed[i] = false; }
    }

    public boolean free(int joint) { return gain[joint] < 0; }

    /** Claims a joint if nobody has. */
    public void joint(int j, float x, float y, float z, float g) {
        if (gain[j] >= 0) return;
        dir[j][0] = x; dir[j][1] = y; dir[j][2] = z; gain[j] = g;
    }

    public void posture(float ux, float uy, float uz, float g, float onGround) {
        if (posture >= 0) return;
        up[0] = ux; up[1] = uy; up[2] = uz; posture = g; postureOnGround = onGround;
    }

    public void balance(float g) { if (balance < 0) balance = g; }
    public void lift(float g, float toHeight) { if (lift < 0) { lift = g; height = toHeight; } }

    public boolean reachFree(int slot) { return !reachClaimed[slot]; }

    public void reach(int slot, int particle, int against, float x, float y, float z, float force, float hz) {
        if (reachClaimed[slot]) return;
        reachClaimed[slot] = true;
        Reach r = reach[slot];
        r.on = true; r.particle = particle; r.against = against; r.x = x; r.y = y; r.z = z; r.force = force; r.hz = hz;
    }

    /** Unclaimed channels go slack. */
    public void finish() {
        for (int j = 0; j < PhysicsRig.NJ; j++) if (gain[j] < 0) gain[j] = 0;
        if (posture < 0) posture = 0;
        if (balance < 0) balance = 0;
        if (lift < 0) lift = 0;
    }
}
