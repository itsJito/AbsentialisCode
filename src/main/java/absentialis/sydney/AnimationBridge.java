package absentialis.sydney;

import static absentialis.character.Ragdoll.*;

import absentialis.character.Ragdoll;
import absentialis.engine.math.Mat4;

/**
 * The seam between authored animation and the physical body, in both directions.
 *
 * In: when Sydney takes a character over, the pose the animation had it in becomes the pose its motors hold - the
 * arms stay where the weapon was, the head where it was looking - expressed in the trunk's own frame so it travels
 * with the body. Out: the physical body is what gets drawn (the rig's particles become the render skeleton's
 * matrices, the same contract the passive ragdoll had), and when the body is its own again the game is told where
 * it is standing and which way it faces so animation can take it back in place.
 */
public final class AnimationBridge {
    private AnimationBridge() {}

    /** Reads the current pose of the arms and neck as motor targets (trunk frame). Call once the rig holds the animated pose. */
    public static void capture(SydneyCharacter c) {
        Ragdoll r = c.rig.r;
        SensorSystem s = c.sense;
        float[] f = new float[9];
        for (int j = 0; j < PhysicsRig.NJ; j++) {
            int[] J = PhysicsRig.JOINTS[j];
            float dx = r.x[J[2]] - r.x[J[1]], dy = r.y[J[2]] - r.y[J[1]], dz = r.z[J[2]] - r.z[J[1]];
            float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (l < 1e-5f) { c.pose[j][0] = 0; c.pose[j][1] = j == PhysicsRig.J_NECK ? 1 : -1; c.pose[j][2] = 0; continue; }
            MotorController.frame(c.rig, s, j, f);
            MotorController.toLocal(f, dx / l, dy / l, dz / l, c.pose[j]);
        }
    }

    /** The physical body as render-skeleton matrices. */
    public static void bones(SydneyCharacter c, Mat4[] out) { c.rig.r.bones(out); }

    /** Where the body stands: between its feet. */
    public static float standX(SydneyCharacter c) { Ragdoll r = c.rig.r; return (r.x[LA] + r.x[RA]) * 0.5f; }
    public static float standZ(SydneyCharacter c) { Ragdoll r = c.rig.r; return (r.z[LA] + r.z[RA]) * 0.5f; }

    /** The yaw the game's actor should take so that it faces the way the body does (actor forward is (-sin yaw, -cos yaw)). */
    public static float yaw(SydneyCharacter c) { return (float) Math.atan2(-c.sense.fwd[0], -c.sense.fwd[2]); }
}
