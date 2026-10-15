package absentialis.sydney;

import static absentialis.character.Ragdoll.*;

import absentialis.character.Ragdoll;

/**
 * Virtual muscles. Given what the behaviours want ({@link Goal}), this turns intent into bounded forces and torques
 * on the rig and stops there: gravity, contact and whether the body actually gets where it was told to go are the
 * solver's business.
 *
 * Every joint motor is the same law: torque = stiffness x pose error - damping x joint velocity, clamped to what a
 * muscle at that joint can produce. The pose error is three-dimensional - the rotation that would carry the bone
 * from where it points to where it should, as an axis and an angle in the trunk's own frame (the swing part of the
 * quaternion error; a bone drawn between two particles has no twist to control). Stiffness and damping are not
 * tuned per joint in raw units: each joint class has a natural frequency and a damping ratio, and the gains follow
 * from the inertia the joint actually carries, so a wrist and a hip are equally well behaved.
 */
public final class MotorController {
    /** Largest torque asked for and largest actually applied in the last step (regression tests: the clamp works). */
    public float peakDemand, peakApplied;
    /** Pose error (radians) and torque applied (N m) per joint in the last step. */
    public final float[] dbgAngle = new float[PhysicsRig.NJ], dbgTorque = new float[PhysicsRig.NJ];

    private final float[] fr = new float[9];

    /**
     * Whether knees and elbows are posed against the bone above them. Off: every joint is posed against the trunk.
     * This rig stands on two points, not two feet - it has no ankle joint to put a motor on - and a leg posed against
     * the trunk does the ankle's work as well as its own: holding the shin where the trunk says it should be is what
     * keeps the body over a planted foot. Posed against the thigh, the same knee holds its bend and nothing holds the
     * leg (measured: the body stands indefinitely one way and for a quarter of a second the other).
     */
    public static final boolean HINGES_AGAINST_PARENT = false;

    /**
     * The frame a joint's target is given in, as right, up and forward in the world (nine numbers): the trunk's own
     * frame (or, with {@link #HINGES_AGAINST_PARENT}, the bone above a knee or an elbow).
     */
    public static void frame(PhysicsRig rig, SensorSystem s, int j, float[] f) {
        int cls = PhysicsRig.JOINTS[j][3];
        if (!HINGES_AGAINST_PARENT || cls != SydneyConfig.KNEE && cls != SydneyConfig.ELBOW) {
            f[0] = s.right[0]; f[1] = s.right[1]; f[2] = s.right[2]; f[3] = s.up[0]; f[4] = s.up[1]; f[5] = s.up[2]; f[6] = s.fwd[0]; f[7] = s.fwd[1]; f[8] = s.fwd[2];
            return;
        }
        Ragdoll r = rig.r;
        int p = PhysicsRig.JOINTS[j][0], jt = PhysicsRig.JOINTS[j][1];
        float ux = r.x[p] - r.x[jt], uy = r.y[p] - r.y[jt], uz = r.z[p] - r.z[jt], ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1e-5f) { ux = s.up[0]; uy = s.up[1]; uz = s.up[2]; } else { ux /= ul; uy /= ul; uz /= ul; }
        float d = s.right[0] * ux + s.right[1] * uy + s.right[2] * uz;
        float rx = s.right[0] - ux * d, ry = s.right[1] - uy * d, rz = s.right[2] - uz * d, rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rl < 0.2f) { d = s.fwd[0] * ux + s.fwd[1] * uy + s.fwd[2] * uz; float bx = s.fwd[0] - ux * d, by = s.fwd[1] - uy * d, bz = s.fwd[2] - uz * d; rx = by * uz - bz * uy; ry = bz * ux - bx * uz; rz = bx * uy - by * ux; rl = Math.max(1e-5f, (float) Math.sqrt(rx * rx + ry * ry + rz * rz)); }
        rx /= rl; ry /= rl; rz /= rl;
        f[0] = rx; f[1] = ry; f[2] = rz; f[3] = ux; f[4] = uy; f[5] = uz;
        f[6] = uy * rz - uz * ry; f[7] = uz * rx - ux * rz; f[8] = ux * ry - uy * rx;
    }

    /** A world direction expressed in a joint's frame. */
    public static void toLocal(float[] f, float wx, float wy, float wz, float[] out) {
        out[0] = wx * f[0] + wy * f[1] + wz * f[2];
        out[1] = wx * f[3] + wy * f[4] + wz * f[5];
        out[2] = -(wx * f[6] + wy * f[7] + wz * f[8]);
    }

    public void drive(PhysicsRig rig, SensorSystem s, Goal g, SydneyConfig cfg) {
        Ragdoll r = rig.r;
        peakDemand = peakApplied = 0;
        for (int j = 0; j < PhysicsRig.NJ; j++) {
            float gain = g.gain[j];
            if (gain <= 0) continue;
            int[] J = PhysicsRig.JOINTS[j];
            if (!rig.limbWorks(J[4])) continue;                       // nothing answers in a limb that is gone or broken
            if (j == PhysicsRig.J_NECK && !rig.head) continue;
            int p = J[0], jt = J[1], c = J[2], cls = J[3];
            float cx = r.x[c] - r.x[jt], cy = r.y[c] - r.y[jt], cz = r.z[c] - r.z[jt];
            float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (len < 1e-5f) continue;
            float ux = cx / len, uy = cy / len, uz = cz / len;
            // where the bone should point: the goal is in the joint's frame (x right, y up, z back)
            float[] t = g.dir[j];
            frame(rig, s, j, fr);
            float wx = fr[0] * t[0] + fr[3] * t[1] - fr[6] * t[2], wy = fr[1] * t[0] + fr[4] * t[1] - fr[7] * t[2], wz = fr[2] * t[0] + fr[5] * t[1] - fr[8] * t[2];
            float wl = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
            if (wl < 1e-5f) continue;
            wx /= wl; wy /= wl; wz /= wl;
            // pose error: axis * angle
            float ex = uy * wz - uz * wy, ey = uz * wx - ux * wz, ez = ux * wy - uy * wx;
            float sin = (float) Math.sqrt(ex * ex + ey * ey + ez * ez), cos = ux * wx + uy * wy + uz * wz;
            float ang = (float) Math.atan2(sin, cos);
            if (sin < 1e-5f) {
                if (cos > 0) { ex = ey = ez = 0; }
                else { ex = s.right[0]; ey = s.right[1]; ez = s.right[2]; }     // pointing exactly the wrong way: turn about the body's side axis
            } else { ex /= sin; ey /= sin; ez /= sin; }
            // joint velocity: how the bone is turning about the joint
            float vx = r.vx(c) - r.vx(jt), vy = r.vy(c) - r.vy(jt), vz = r.vz(c) - r.vz(jt);
            float ox = (cy * vz - cz * vy) / (len * len), oy = (cz * vx - cx * vz) / (len * len), oz = (cx * vy - cy * vx) / (len * len);
            boolean stance = (J[4] == 3 && s.footL || J[4] == 4 && s.footR) && g.stance;
            float inertia = rig.carried(j) * len * len;
            float w0 = (float) (2 * Math.PI * cfg.freq[cls]);
            // a leg carrying the body is stiffer, not more damped: explicit damping beyond the bone's own inertia would ring at 90 Hz
            float k = inertia * w0 * w0 * gain * (stance ? cfg.stanceGain : 1f), d = 2 * cfg.zeta[cls] * inertia * w0 * (float) Math.sqrt(gain) * (stance ? 1.25f : 1f);
            float tx = ex * ang * k - ox * d, ty = ey * ang * k - oy * d, tz = ez * ang * k - oz * d;
            float along = tx * ux + ty * uy + tz * uz;
            tx -= ux * along; ty -= uy * along; tz -= uz * along;
            float mag = (float) Math.sqrt(tx * tx + ty * ty + tz * tz), max = cfg.torque[cls] * cfg.strength * rig.massScale * Math.min(1f, gain * 1.5f) * g.strength;
            peakDemand = Math.max(peakDemand, mag);
            if (mag > max) { float f = max / mag; tx *= f; ty *= f; tz *= f; mag = max; }
            peakApplied = Math.max(peakApplied, mag);
            dbgAngle[j] = ang; dbgTorque[j] = mag;
            rig.torque(p, jt, c, tx, ty, tz);
        }
        posture(rig, s, g, cfg);
        balance(rig, s, g, cfg);
        lift(rig, s, g, cfg);
        for (Goal.Reach e : g.reach) if (e.on) reach(rig, e);
    }

    /**
     * The hips hold the trunk up. With a foot on the ground the hip of that leg can turn the trunk against the leg;
     * with nothing on the ground there is nothing to turn against, and the trunk is not turned.
     */
    private void posture(PhysicsRig rig, SensorSystem s, Goal g, SydneyConfig cfg) {
        if (g.posture <= 0) return;
        float support = s.feet > 0 ? 1f : s.otherContacts > 0 ? g.postureOnGround : 0f;
        if (support <= 0) return;
        Ragdoll r = rig.r;
        float[] u = s.up, want = g.up;
        float ex = u[1] * want[2] - u[2] * want[1], ey = u[2] * want[0] - u[0] * want[2], ez = u[0] * want[1] - u[1] * want[0];
        float sin = (float) Math.sqrt(ex * ex + ey * ey + ez * ez), cos = u[0] * want[0] + u[1] * want[1] + u[2] * want[2];
        float ang = (float) Math.atan2(sin, cos);
        if (sin < 1e-5f) { if (cos > 0) return; ex = s.right[0]; ey = s.right[1]; ez = s.right[2]; } else { ex /= sin; ey /= sin; ez /= sin; }
        float cx = r.x[NECK] - r.x[PELVIS], cy = r.y[NECK] - r.y[PELVIS], cz = r.z[NECK] - r.z[PELVIS], l2 = cx * cx + cy * cy + cz * cz;
        if (l2 < 1e-6f) return;
        float vx = r.vx(NECK) - r.vx(PELVIS), vy = r.vy(NECK) - r.vy(PELVIS), vz = r.vz(NECK) - r.vz(PELVIS);
        float ox = (cy * vz - cz * vy) / l2, oy = (cz * vx - cx * vz) / l2, oz = (cx * vy - cy * vx) / l2;
        float inertia = (rig.mass(NECK) + rig.mass(HEAD_P) + rig.mass(LS) + rig.mass(RS)) * l2;
        float w0 = (float) (2 * Math.PI * cfg.freq[SydneyConfig.SPINE]);
        float k = inertia * w0 * w0 * g.posture, d = 2 * cfg.zeta[SydneyConfig.SPINE] * inertia * w0;
        float tx = ex * ang * k - ox * d, ty = ey * ang * k - oy * d, tz = ez * ang * k - oz * d;
        float mag = (float) Math.sqrt(tx * tx + ty * ty + tz * tz), max = cfg.torque[SydneyConfig.SPINE] * cfg.strength * rig.massScale * support * g.strength;
        if (mag > max) { float f = max / mag; tx *= f; ty *= f; tz *= f; }
        float fx = (ty * cz - tz * cy) / l2, fy = (tz * cx - tx * cz) / l2, fz = (tx * cy - ty * cx) / l2;
        rig.force(NECK, fx, fy, fz);
        // the reaction goes down the legs that are planted (or into whatever else is holding the body off the ground)
        if (s.feet > 0) { float sh = 1f / s.feet; if (s.footL) rig.force(LH, -fx * sh, -fy * sh, -fz * sh); if (s.footR) rig.force(RH, -fx * sh, -fy * sh, -fz * sh); }
        else rig.force(PELVIS, -fx, -fy, -fz);
    }

    /**
     * The ankles. A planted foot can move the point the body pivots on by no more than its own lever; inside that
     * limit the pivot is put beyond the capture point so the body is pushed back over its feet. Out of the body's
     * side this is a horizontal force on the whole mass and the opposite force on the feet, which the ground's
     * friction has to take - a foot that cannot take it slides.
     */
    private void balance(PhysicsRig rig, SensorSystem s, Goal g, SydneyConfig cfg) {
        if (g.balance <= 0 || s.feet == 0) return;
        float px = s.errX * (1 + cfg.balanceGain), pz = s.errZ * (1 + cfg.balanceGain);
        float l = (float) Math.hypot(px, pz), max = s.reach(cfg, px, pz);
        if (l > max) { px *= max / l; pz *= max / l; }
        float f = rig.totalMass * s.omega * s.omega * g.balance * g.strength;
        float fx = -px * f, fz = -pz * f;
        rig.bodyForce(fx, 0, fz);
        float sh = 1f / s.feet;
        if (s.footL) rig.force(LA, -fx * sh, 0, -fz * sh);
        if (s.footR) rig.force(RA, -fx * sh, 0, -fz * sh);
    }

    /**
     * Pushing up off the ground: the legs (and early on the arms) drive the trunk toward a height, with no more
     * force than their strength allows and only while something is on the ground to push against - which is pushed
     * down in return.
     */
    private void lift(PhysicsRig rig, SensorSystem s, Goal g, SydneyConfig cfg) {
        if (g.lift <= 0) return;
        Ragdoll r = rig.r;
        int n = 0;
        int[] sup = {LA, RA, LK, RK, LW, RW};
        for (int i : sup) if (r.grounded[i] && works(rig, i)) n++;
        if (n == 0) return;
        float weight = rig.totalMass * GRAVITY;
        float want = rig.totalMass * (60f * (g.height - s.height) - 14f * s.comV[1]) + weight;
        float f = Math.max(0, Math.min(want, weight * cfg.getUpStrength * cfg.strength * g.lift));
        int[] trunk = {PELVIS, NECK, LS, RS, LH, RH};
        float m = 0;
        for (int i : trunk) m += MASS[i];
        for (int i : trunk) rig.force(i, 0, f * MASS[i] / m, 0);
        for (int i : sup) if (r.grounded[i] && works(rig, i)) rig.force(i, 0, -f / n, 0);
    }

    private static boolean works(PhysicsRig rig, int i) {
        return switch (i) { case LA, LK -> rig.legL; case RA, RK -> rig.legR; case LW -> rig.armL; case RW -> rig.armR; default -> true; };
    }

    /** A hand or a foot sent to a place: a spring and a damper on the end of the limb, limited, and reacted on the trunk. */
    private void reach(PhysicsRig rig, Goal.Reach e) {
        Ragdoll r = rig.r;
        int i = e.particle;
        float m = rig.mass(i) * 2.5f, w0 = (float) (2 * Math.PI * e.hz);
        float fx = m * (w0 * w0 * (e.x - r.x[i]) - 2 * w0 * r.vx(i)), fy = m * (w0 * w0 * (e.y - r.y[i]) - 2 * w0 * r.vy(i)), fz = m * (w0 * w0 * (e.z - r.z[i]) - 2 * w0 * r.vz(i));
        float mag = (float) Math.sqrt(fx * fx + fy * fy + fz * fz), max = e.force * rig.massScale;
        if (mag > max) { float k = max / mag; fx *= k; fy *= k; fz *= k; }
        rig.force(i, fx, fy, fz);
        rig.force(e.against, -fx, -fy, -fz);
    }
}
