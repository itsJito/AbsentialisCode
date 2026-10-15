package absentialis.sydney;

import static absentialis.character.Ragdoll.*;

import absentialis.character.Ragdoll;
import absentialis.world.World;

/**
 * What the body knows about itself, measured once per fixed step from the physical state and nothing else: where its
 * centre of mass is and how it is moving, what it is standing on, which way its trunk points, and - the quantity
 * balance turns on - the capture point: where a foot would have to be for the body to come to rest over it.
 */
public final class SensorSystem {
    public final float[] com = new float[3], comV = new float[3];
    /** Trunk frame in the world: right, up (pelvis to neck) and forward. */
    public final float[] right = {1, 0, 0}, up = {0, 1, 0}, fwd = {0, 0, -1};
    /** Centre of the feet that are on the ground, and the ground under the body. */
    public float supX, supZ, groundY;
    /** Feet carrying weight (0..2), and anything else touching the ground (hands, knees, trunk, head). */
    public int feet, otherContacts;
    public boolean footL, footR;
    /** Capture point, and its offset from the centre of support: the balance error, in metres. */
    public float cpX, cpZ, errX, errZ, err;
    /** The error measured against how far the feet can answer it (1 = at the edge of what the ankles can hold). */
    public float errNorm;
    /** Lean of the trunk from vertical (radians), height of the centre of mass over the ground, speed of the centre of mass. */
    public float tilt, height, speed;
    /** Standing height of the centre of mass for this body. */
    public float standHeight;
    /** The largest change of velocity of the centre of mass in one step since last read: an impact was felt. */
    public float jolt;
    /** Distance between the feet across the body, and the pendulum frequency sqrt(g / height). */
    public float stance, omega;
    private float lastVx, lastVy, lastVz;
    private boolean primed;
    private static final int HOLD = 4;
    private int heldL, heldR;

    public void measure(PhysicsRig rig, World w, SydneyConfig cfg) {
        Ragdoll r = rig.r;
        float mx = 0, my = 0, mz = 0, vx = 0, vy = 0, vz = 0;
        for (int i = 0; i < N; i++) {
            float m = MASS[i];
            mx += r.x[i] * m; my += r.y[i] * m; mz += r.z[i] * m;
            vx += r.vx(i) * m; vy += r.vy(i) * m; vz += r.vz(i) * m;
        }
        float M = rig.totalMass / rig.massScale;
        com[0] = mx / M; com[1] = my / M; com[2] = mz / M;
        comV[0] = vx / M; comV[1] = vy / M; comV[2] = vz / M;
        speed = (float) Math.sqrt(comV[0] * comV[0] + comV[1] * comV[1] + comV[2] * comV[2]);
        if (primed) jolt = Math.max(jolt, (float) Math.sqrt((comV[0] - lastVx) * (comV[0] - lastVx) + (comV[1] - lastVy) * (comV[1] - lastVy) + (comV[2] - lastVz) * (comV[2] - lastVz)));
        lastVx = comV[0]; lastVy = comV[1]; lastVz = comV[2]; primed = true;

        // the trunk frame: up is pelvis to neck, right is shoulder to shoulder squared off against it
        float ux = r.x[NECK] - r.x[PELVIS], uy = r.y[NECK] - r.y[PELVIS], uz = r.z[NECK] - r.z[PELVIS];
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul > 1e-5f) { up[0] = ux / ul; up[1] = uy / ul; up[2] = uz / ul; }
        float rx = r.x[RS] - r.x[LS], ry = r.y[RS] - r.y[LS], rz = r.z[RS] - r.z[LS];
        float d = rx * up[0] + ry * up[1] + rz * up[2];
        rx -= up[0] * d; ry -= up[1] * d; rz -= up[2] * d;
        float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rl > 1e-5f) { right[0] = rx / rl; right[1] = ry / rl; right[2] = rz / rl; }
        fwd[0] = up[1] * right[2] - up[2] * right[1]; fwd[1] = up[2] * right[0] - up[0] * right[2]; fwd[2] = up[0] * right[1] - up[1] * right[0];
        tilt = (float) Math.acos(Math.max(-1, Math.min(1, up[1])));

        // contact is debounced: a foot that touched within the last few steps is still the foot the body stands on
        heldL = rig.footDown(0) ? HOLD : Math.max(0, heldL - 1); heldR = rig.footDown(1) ? HOLD : Math.max(0, heldR - 1);
        footL = heldL > 0 && rig.legL; footR = heldR > 0 && rig.legR;
        feet = (footL ? 1 : 0) + (footR ? 1 : 0);
        otherContacts = 0;
        for (int i = 0; i < N; i++) if (r.grounded[i] && i != LA && i != RA) otherContacts++;
        groundY = ground(w, r, com[0], com[1], com[2]);
        if (feet == 2) { supX = (r.x[LA] + r.x[RA]) * 0.5f; supZ = (r.z[LA] + r.z[RA]) * 0.5f; }
        else if (footL) { supX = r.x[LA]; supZ = r.z[LA]; }
        else if (footR) { supX = r.x[RA]; supZ = r.z[RA]; }
        else { supX = (r.x[LA] + r.x[RA]) * 0.5f; supZ = (r.z[LA] + r.z[RA]) * 0.5f; }
        stance = (float) Math.hypot(r.x[RA] - r.x[LA], r.z[RA] - r.z[LA]);
        height = com[1] - groundY;
        omega = (float) Math.sqrt(GRAVITY / Math.max(0.25f, height));
        cpX = com[0] + comV[0] / omega; cpZ = com[2] + comV[2] / omega;
        errX = cpX - supX; errZ = cpZ - supZ;
        err = (float) Math.hypot(errX, errZ);
        errNorm = err / reach(cfg, errX, errZ);
    }

    /** How far the planted feet can move the point of support in a direction: the foot's own lever, plus the stance when the direction runs from one foot to the other. */
    public float reach(SydneyConfig cfg, float dx, float dz) {
        float l = (float) Math.hypot(dx, dz);
        if (l < 1e-5f || feet < 2) return cfg.ankleLever * (feet == 0 ? 0.5f : 1f);
        float along = Math.abs(dx * right[0] + dz * right[2]) / l;
        return cfg.ankleLever + 0.5f * stance * along;
    }

    public float takeJolt() { float j = jolt; jolt = 0; return j; }

    /** Top of the first solid block under a point (the body's own lowest particle when there is no world). */
    static float ground(World w, Ragdoll r, float x, float y, float z) {
        if (w == null) return r.lowestY();
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        for (int iy = (int) Math.floor(y); iy >= Math.max(0, (int) Math.floor(y) - 6); iy--) if (w.solid(ix, iy, iz)) return iy + 1;
        return r.lowestY();
    }
}
