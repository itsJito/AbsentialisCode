package absentialis.sydney;

import absentialis.entity.Actor;

/**
 * Sydney's contextual motion (1.0): movements that are worked out from what the body is touching, not played back.
 *
 * The active ragdoll simulates a body that has lost control of itself. This is the other half: a body that is in
 * control, doing something whose shape depends on the world - hauling itself up onto this ledge, at this height, from
 * here. There is no clip for that. What Sydney supplies is a <i>plan</i>: where the hands are on the world (contacts,
 * in world space, that do not move while they bear weight), what each leg is doing, how the trunk is carried - all as
 * functions of how far through the movement the body is. The animator then solves the arms onto the contacts from
 * wherever the body really is that frame. Because the body's rise is the physics controller's and the hands are fixed
 * to the wall, the pull is right by construction: arms straight when the ledge is high above, bending as the chest
 * comes up to it, pressing down once the shoulders are over - for a ledge two blocks up or three, for a tall body or a
 * short one, in third person or seen through its eyes.
 *
 * It costs a few dozen multiplications a frame for a body that is climbing, and nothing for one that is not: no
 * simulation, no clip, no allocation.
 */
public final class ContextualMotion {
    private ContextualMotion() {}

    /** The plan for one frame of a pull-up onto a ledge. Angles in radians (thigh forward and trunk back positive, shin bend negative), heights in rig voxels. */
    public static final class Mantle {
        /** How far through the movement the body is, 0..1. */
        public float t;
        /** The hands on the lip of the ledge: world positions, fixed for as long as they bear weight; and how firmly they are there, 0..1. */
        public final float[] handL = new float[3], handR = new float[3];
        public float handW;
        /** The hips' drop below a standing body's (voxels, negative = lower) and their push toward the wall. */
        public float rootDrop, rootFwd;
        public float torsoPitch, torsoRoll, headPitch;
        public float lThigh, lShin, rThigh, rShin;
        /** Which knee leads over the edge: +1 the right, -1 the left (by the body's id, so a squad does not climb in step). */
        public int lead = 1;
    }

    private static float smooth(float a, float b, float x) { float t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }

    /**
     * Where the hands go on a ledge the actor is pulling up onto, and what the rest of the body does at this moment of it.
     * The actor's own record of the pull-up (where from, where to, how long, how much is left) is all that is read.
     *
     * The movement, in order: a dip and the arms thrown up at the lip; the hang, the feet finding the wall and walking up
     * it as the arms shorten; the press, as the shoulders come over the hands and one knee is brought up onto the top
     * while the other leg trails down the face; and the rise off that knee as the hands let go.
     */
    public static void mantle(Actor a, Mantle m) {
        // 1.1: hanging is the moment the hands have the lip and the arms are long - held for as long as the body hangs
        float t = a.hanging ? 0.13f : 1 - a.mantleT / Math.max(0.01f, a.mantleDur);
        m.t = t;
        float dx = a.mToX - a.mFromX, dz = a.mToZ - a.mFromZ, l = (float) Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-4f) { dx = (float) -Math.sin(a.yaw); dz = (float) -Math.cos(a.yaw); l = 1; }
        dx /= l; dz /= l;
        // the lip: the near edge of the top, a hand's length in from it; the hands a shoulder's width apart on it
        float ex = a.mToX - dx * 0.38f, ez = a.mToZ - dz * 0.38f, ey = a.mToY + 0.02f;
        float sx = -dz, sz = dx, half = 0.20f + 0.05f * Math.min(1.4f, a.radius / 0.3f);
        m.handR[0] = ex + sx * half; m.handR[1] = ey; m.handR[2] = ez + sz * half;
        m.handL[0] = ex - sx * half; m.handL[1] = ey; m.handL[2] = ez - sz * half;
        m.lead = (a.id & 1) == 0 ? 1 : -1;

        float reach = smooth(0.00f, 0.12f, t), pull = smooth(0.10f, 0.58f, t), press = smooth(0.50f, 0.70f, t), rise = smooth(0.80f, 1.0f, t);
        m.handW = reach * (1 - smooth(0.82f, 0.95f, t));
        // the dip before the spring, the hang close to the wall, the hips low over the edge in the press, up again in the rise
        float dip = (float) Math.sin(Math.min(1, t / 0.12f) * Math.PI);
        m.rootDrop = -2.4f * dip - 6.4f * press * (1 - rise);
        m.rootFwd = -3.0f * pull * (1 - press) - 2.6f * press * (1 - rise);
        m.torsoPitch = 0.04f * reach * (1 - pull) - 0.24f * pull * (1 - press) - 0.62f * press * (1 - rise);
        m.torsoRoll = 0.05f * m.lead * press * (1 - rise);
        m.headPitch = 0.38f * reach * (1 - pull * 0.7f) * (1 - press) + 0.28f * press * (1 - rise);
        // the legs. Hanging, the feet walk up the face: each knee in turn comes up and the foot pushes down
        float walkW = smooth(0.10f, 0.20f, t) * (1 - smooth(0.50f, 0.62f, t));
        float cyc = (t - 0.10f) / 0.45f * (float) Math.PI * 3.2f, s = (float) Math.sin(cyc);
        float upL = Math.max(0, s), upR = Math.max(0, -s);
        float lT = 0.55f * dip + walkW * (0.30f + 0.75f * upL), lS = -0.9f * dip - walkW * (0.55f + 0.85f * upL);
        float rT = 0.50f * dip + walkW * (0.30f + 0.75f * upR), rS = -0.8f * dip - walkW * (0.55f + 0.85f * upR);
        // the press: the leading knee up onto the top and folded under the body, the other leg long down the wall
        float p = press * (1 - rise);
        float leadT = 1.62f, leadS = -1.95f, trailT = -0.28f, trailS = -0.12f;
        if (m.lead > 0) { rT += (leadT - rT) * p; rS += (leadS - rS) * p; lT += (trailT - lT) * p; lS += (trailS - lS) * p; }
        else { lT += (leadT - lT) * p; lS += (leadS - lS) * p; rT += (trailT - rT) * p; rS += (trailS - rS) * p; }
        m.lThigh = lT; m.lShin = lS; m.rThigh = rT; m.rShin = rS;
        if (a.hanging) {
            // the legs hang and swing (entity.Physics keeps the pendulum): thighs with the swing, the shins a little behind it and
            // never quite straight, one leg a beat after the other; the trunk goes the other way about the hands
            float sw = a.hangSwing, lag = a.hangVel * 0.06f;
            m.handW = 1;
            m.rootDrop = 0; m.rootFwd = -1.6f;
            m.torsoPitch = -0.05f - sw * 0.22f; m.torsoRoll = 0; m.headPitch = 0.30f;
            m.lThigh = 0.10f + sw * 0.95f; m.rThigh = 0.03f + (sw - lag) * 0.80f;
            m.lShin = -0.20f - Math.abs(sw) * 0.45f + lag * 0.5f; m.rShin = -0.32f - Math.abs(sw - lag) * 0.40f;
        }
    }

    /**
     * A weapon in the air (1.0): what the hands' load does when the body under it jumps and lands. Returned as an offset to
     * the weapon's place in the aim frame - {down (voxels), muzzle pitch (radians)} - for the animator to add: the weapon
     * lags the body on the way up, floats at the top, and comes down into the hands on landing. The arms follow it because
     * they are solved onto it; nothing is keyed.
     */
    public static void airborneWeapon(float verticalSpeed, float mass, float[] out) {
        float heft = 0.7f + Math.min(1.2f, mass * 0.12f);
        float v = Math.max(-9f, Math.min(9f, verticalSpeed));
        out[0] = v * 0.16f * heft;
        out[1] = -v * 0.028f * heft;
    }
}
