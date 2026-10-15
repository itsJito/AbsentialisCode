package absentialis.sydney;

import static absentialis.character.Ragdoll.*;

import absentialis.character.Ragdoll;
import absentialis.character.Rig;

/**
 * The Sydney Engine's view of a body: an adapter over the game's existing ragdoll (character.Ragdoll - fifteen
 * particles, rigid bones, joint limits, one-sided hinges, voxel collision), not a second solver. It adds what a
 * controller needs and a passive ragdoll never did: masses, the joint hierarchy with a parent to react against, a
 * way to apply forces and torques that the solver then resolves, and which limbs the body still has.
 *
 * A joint here is three particles: the joint itself, the far end of the child bone, and a point on the parent to
 * take the reaction. A torque is applied as forces on those three that sum to nothing - a muscle pulls on two bones,
 * it does not push on the world.
 */
public final class PhysicsRig {
    /** {parent reference, joint, child end, joint class, limb}. Limb: 0 trunk, 1 left arm, 2 right arm, 3 left leg, 4 right leg. */
    public static final int[][] JOINTS = {
            {PELVIS, NECK, HEAD_P, SydneyConfig.NECK, 0},
            {NECK, LS, LE, SydneyConfig.SHOULDER, 1}, {LS, LE, LW, SydneyConfig.ELBOW, 1},
            {NECK, RS, RE, SydneyConfig.SHOULDER, 2}, {RS, RE, RW, SydneyConfig.ELBOW, 2},
            {NECK, LH, LK, SydneyConfig.HIP, 3}, {LH, LK, LA, SydneyConfig.KNEE, 3},
            {NECK, RH, RK, SydneyConfig.HIP, 4}, {RH, RK, RA, SydneyConfig.KNEE, 4}};
    public static final int J_NECK = 0, J_L_SHOULDER = 1, J_L_ELBOW = 2, J_R_SHOULDER = 3, J_R_ELBOW = 4, J_L_HIP = 5, J_L_KNEE = 6, J_R_HIP = 7, J_R_KNEE = 8, NJ = 9;
    /** Mass hanging off each joint (the child bone and everything beyond it), kilograms at scale 1. */
    private static final float[] CARRIED = {MASS[HEAD_P], MASS[LE] + MASS[LW], MASS[LW], MASS[RE] + MASS[RW], MASS[RW], MASS[LK] + MASS[LA], MASS[LA], MASS[RK] + MASS[RA], MASS[RA]};

    public final Ragdoll r;
    /** Metres per voxel for this body, and its mass scale (giants are heavier by the cube). */
    public final float unit, massScale;
    public final float totalMass;
    /** What the body still has to work with: a limb that is gone or broken takes no orders and gives no support. */
    public boolean armL = true, armR = true, legL = true, legR = true, head = true;

    public PhysicsRig(Ragdoll r) {
        this.r = r;
        unit = r.unit;
        float s = unit / Rig.VOX;
        massScale = s * s * s;
        float m = 0;
        for (float k : MASS) m += k;
        totalMass = m * massScale;
    }

    public float mass(int i) { return MASS[i] * massScale; }
    public float carried(int joint) { return CARRIED[joint] * massScale; }

    /** Tells the rig what the anatomy has lost (rig-group bit masks from the dismemberment system: gone, and limp). */
    public void setBody(int missingMask, int limpMask) {
        int off = missingMask | limpMask;
        armL = (off & (1 << Rig.L_UPPER_ARM | 1 << Rig.L_FOREARM)) == 0;
        armR = (off & (1 << Rig.R_UPPER_ARM | 1 << Rig.R_FOREARM)) == 0;
        legL = (off & (1 << Rig.L_THIGH | 1 << Rig.L_SHIN)) == 0;
        legR = (off & (1 << Rig.R_THIGH | 1 << Rig.R_SHIN)) == 0;
        head = (missingMask & (1 << Rig.HEAD)) == 0;
    }

    public boolean limbWorks(int limb) { return switch (limb) { case 1 -> armL; case 2 -> armR; case 3 -> legL; case 4 -> legR; default -> true; }; }
    public boolean footDown(int side) { return side == 0 ? legL && r.grounded[LA] : legR && r.grounded[RA]; }

    public void force(int i, float fx, float fy, float fz) { r.fx[i] += fx; r.fy[i] += fy; r.fz[i] += fz; }

    /** A force spread over the whole body in proportion to mass (what a shift of the support point does to it). */
    public void bodyForce(float fx, float fy, float fz) {
        for (int i = 0; i < N; i++) { float k = MASS[i] * massScale / totalMass; r.fx[i] += fx * k; r.fy[i] += fy * k; r.fz[i] += fz * k; }
    }

    /**
     * A torque about joint j acting on the bone j-c, reacted on the bone j-p. Returns nothing and moves nothing: the
     * three forces go to the solver.
     */
    public void torque(int p, int j, int c, float tx, float ty, float tz) {
        float cx = r.x[c] - r.x[j], cy = r.y[c] - r.y[j], cz = r.z[c] - r.z[j], c2 = cx * cx + cy * cy + cz * cz;
        float qx = r.x[p] - r.x[j], qy = r.y[p] - r.y[j], qz = r.z[p] - r.z[j], q2 = qx * qx + qy * qy + qz * qz;
        if (c2 < 1e-8f || q2 < 1e-8f) return;
        float fcx = (ty * cz - tz * cy) / c2, fcy = (tz * cx - tx * cz) / c2, fcz = (tx * cy - ty * cx) / c2;
        float fpx = -(ty * qz - tz * qy) / q2, fpy = -(tz * qx - tx * qz) / q2, fpz = -(tx * qy - ty * qx) / q2;
        force(c, fcx, fcy, fcz);
        force(p, fpx, fpy, fpz);
        force(j, -fcx - fpx, -fcy - fpy, -fcz - fpz);
    }
}
