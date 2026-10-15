package absentialis.anatomy;

import absentialis.character.Rig;

/**
 * What a body can still do. Derived from the voxel anatomy after every change - how much of each region is left,
 * which are fractured, which are gone - and expressed as the functional limits the rest of the game needs:
 * how well each leg and arm works, whether the character can walk or only crawl, hold a weapon, steady it with
 * two hands, strike or bite. Animation and AI read this; neither ever looks at voxels.
 */
public final class AnatomicalState {
    public final float[] integrity = new float[Rig.GROUPS];
    public final boolean[] fractured = new boolean[Rig.GROUPS], missing = new boolean[Rig.GROUPS];
    /** Rig groups that are entirely gone. */
    public int missingMask;
    /** Function of each limb, 0 (useless or gone) .. 1 (whole). */
    public float legL = 1, legR = 1, armL = 1, armR = 1;
    /** Movement speed share, aim error multiplier (1 = steady), and how much is bleeding out. */
    public float mobility = 1, handling = 1, bleeding;
    public boolean canWalk = true, canHoldWeapon = true, canTwoHand = true, canStrike = true, headless;
    /** Regions that no longer work properly (missing, fractured or mostly destroyed), as rig-group bits: limp limbs. */
    public int limpMask;
    /** 0 .. 1: how badly hurt the body is overall, for behaviour (not for health). */
    public float severity;

    private static final int[][] LEG_L = {{Rig.L_THIGH, Rig.L_SHIN}}, LEG_R = {{Rig.R_THIGH, Rig.R_SHIN}};

    private float part(int g) {
        if (missing[g]) return 0;
        float f = (float) Math.pow(Math.max(0, integrity[g]), 0.6);
        if (fractured[g]) f *= 0.3f;
        return f;
    }

    /** Recomputes everything from the anatomy. */
    public void evaluate(VoxelBody b) {
        missingMask = 0; limpMask = 0;
        float worst = 0;
        for (int g = 0; g < Rig.GROUPS; g++) {
            VoxelBody.Chunk c = b.chunks[g];
            integrity[g] = c == null ? 0 : c.integrity();
            missing[g] = c == null || c.empty();
            fractured[g] = c != null && c.fractured;
            if (missing[g]) missingMask |= 1 << g;
            if (missing[g] || fractured[g] || integrity[g] < 0.45f) limpMask |= 1 << g;
            worst = Math.max(worst, 1 - integrity[g]);
        }
        // a chain is only as good as its links; what hangs below a useless link is useless too
        legL = part(Rig.L_THIGH) * (0.25f + 0.75f * part(Rig.L_SHIN));
        legR = part(Rig.R_THIGH) * (0.25f + 0.75f * part(Rig.R_SHIN));
        armL = part(Rig.L_UPPER_ARM) * part(Rig.L_FOREARM) * (0.3f + 0.7f * part(Rig.L_HAND));
        armR = part(Rig.R_UPPER_ARM) * part(Rig.R_FOREARM) * (0.3f + 0.7f * part(Rig.R_HAND));
        if (fractured[Rig.L_UPPER_ARM] || fractured[Rig.L_FOREARM]) limpMask |= Rig.bit(Rig.L_UPPER_ARM) | Rig.bit(Rig.L_FOREARM) | Rig.bit(Rig.L_HAND);
        if (fractured[Rig.R_UPPER_ARM] || fractured[Rig.R_FOREARM]) limpMask |= Rig.bit(Rig.R_UPPER_ARM) | Rig.bit(Rig.R_FOREARM) | Rig.bit(Rig.R_HAND);
        float best = Math.max(legL, legR), poor = Math.min(legL, legR);
        canWalk = poor > 0.18f;
        mobility = canWalk ? 0.42f + 0.58f * poor : best > 0.18f ? 0.3f : 0.14f;
        boolean rightHand = !missing[Rig.R_HAND] && !missing[Rig.R_FOREARM] && !missing[Rig.R_UPPER_ARM];
        boolean leftHand = !missing[Rig.L_HAND] && !missing[Rig.L_FOREARM] && !missing[Rig.L_UPPER_ARM];
        canHoldWeapon = rightHand && armR > 0.12f;
        canTwoHand = canHoldWeapon && leftHand && armL > 0.3f && armR > 0.3f;
        canStrike = armL > 0.15f && leftHand || armR > 0.15f && rightHand;
        handling = 1f + (1 - armR) * 1.7f + (1 - armL) * 0.9f;
        headless = missing[Rig.HEAD] || integrity[Rig.HEAD] < 0.3f;
        bleeding = b.bleeding();
        severity = Math.min(1, worst * 0.6f + (1 - b.integrity()) * 2.2f + Integer.bitCount(missingMask) * 0.2f);
    }

    public boolean missingAny(int mask) { return (missingMask & mask) != 0; }

    /** One line for debug overlays and test output. */
    public String summary() {
        return String.format("legs %.2f/%.2f arms %.2f/%.2f mobility %.2f handling %.2f%s%s%s missing %s", legL, legR, armL, armR, mobility, handling,
                canWalk ? "" : " CRAWL", canHoldWeapon ? "" : " NO-WEAPON", headless ? " HEADLESS" : "", Integer.toBinaryString(missingMask));
    }
}
