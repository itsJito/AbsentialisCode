package absentialis.character;

/**
 * One evaluated frame of the shared rig: a rotation per SKIN group, the root (hips) offset and the
 * held-weapon channel. Poses are plain data so clips can be sampled, blended and layered.
 */
public final class Pose {
    /** Per group {pitch(x), yaw(y), roll(z)} in radians. */
    public final float[] rot = new float[Rig.GROUPS * 3];
    /** Hips offset in voxels and hips rotation. */
    public float rootX, rootY, rootZ, rootYaw, rootPitch, rootRoll;
    /** Weapon channel in the aim frame: position, {pitch, yaw, roll}, and the support-hand offset from its grip. */
    public final float[] wpn = new float[9];
    /** 1 = the support hand follows the weapon grip, 0 = it is free (wpn[6..8] is then an aim-frame position). */
    public float lhOnWeapon = 1;
    /** 0.7: extra pitch of the aim frame, undoing a root that has been laid down (prone): the weapon points where the eyes do, not at the floor. */
    public float aimComp;

    public Pose clear() {
        java.util.Arrays.fill(rot, 0);
        java.util.Arrays.fill(wpn, 0);
        rootX = rootY = rootZ = rootYaw = rootPitch = rootRoll = 0;
        lhOnWeapon = 1;
        return this;
    }

    public Pose set(Pose o) {
        System.arraycopy(o.rot, 0, rot, 0, rot.length);
        System.arraycopy(o.wpn, 0, wpn, 0, wpn.length);
        rootX = o.rootX; rootY = o.rootY; rootZ = o.rootZ; rootYaw = o.rootYaw; rootPitch = o.rootPitch; rootRoll = o.rootRoll;
        lhOnWeapon = o.lhOnWeapon;
        return this;
    }

    public float rx(int g) { return rot[g * 3]; }
    public float ry(int g) { return rot[g * 3 + 1]; }
    public float rz(int g) { return rot[g * 3 + 2]; }
    public Pose r(int g, float x, float y, float z) { rot[g * 3] = x; rot[g * 3 + 1] = y; rot[g * 3 + 2] = z; return this; }
    public Pose add(int g, float x, float y, float z) { rot[g * 3] += x; rot[g * 3 + 1] += y; rot[g * 3 + 2] += z; return this; }

    /** this = lerp(this, o, t) for the groups in mask; root and weapon channels follow when flagged. */
    public Pose blend(Pose o, float t, int mask, boolean root, boolean weapon) {
        if (t <= 0) return this;
        if (t > 1) t = 1;
        for (int g = 0; g < Rig.GROUPS; g++) {
            if ((mask & (1 << g)) == 0) continue;
            for (int k = 0; k < 3; k++) rot[g * 3 + k] += (o.rot[g * 3 + k] - rot[g * 3 + k]) * t;
        }
        if (root) {
            rootX += (o.rootX - rootX) * t; rootY += (o.rootY - rootY) * t; rootZ += (o.rootZ - rootZ) * t;
            rootYaw += (o.rootYaw - rootYaw) * t; rootPitch += (o.rootPitch - rootPitch) * t; rootRoll += (o.rootRoll - rootRoll) * t;
        }
        if (weapon) {
            for (int k = 0; k < wpn.length; k++) wpn[k] += (o.wpn[k] - wpn[k]) * t;
            lhOnWeapon += (o.lhOnWeapon - lhOnWeapon) * t;
        }
        return this;
    }

    /** this += o * w (additive layers: recoil, flinch). */
    public Pose addScaled(Pose o, float w, int mask) {
        for (int g = 0; g < Rig.GROUPS; g++) {
            if ((mask & (1 << g)) == 0) continue;
            for (int k = 0; k < 3; k++) rot[g * 3 + k] += o.rot[g * 3 + k] * w;
        }
        rootY += o.rootY * w; rootZ += o.rootZ * w; rootPitch += o.rootPitch * w;
        for (int k = 0; k < 6; k++) wpn[k] += o.wpn[k] * w;
        return this;
    }
}
