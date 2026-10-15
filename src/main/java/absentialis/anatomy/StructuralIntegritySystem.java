package absentialis.anatomy;

import static absentialis.anatomy.BodyMaterial.*;

import absentialis.character.Rig;

/**
 * Decides when part of a body comes off - from the structure that is left, never from a health threshold.
 *
 * Every limb region hangs from the joint in its top rows. After a region has been damaged:
 *   1. each slice across the limb is weighed: the structural weight of the voxels still in it (bone and joint
 *      count for most, flesh for some, skin for a little) against what it had. A slice with less than a quarter
 *      left cannot hold what hangs below it and tears through;
 *   2. the region is flooded from its joint through voxels that still touch one another. Whatever the flood does
 *      not reach is no longer attached to the body: it detaches, and so does every region further down the limb
 *      if the end they were attached to went with it;
 *   3. a slice that has lost all of its bone but still holds together by flesh is a fracture: the limb stays on
 *      and stops working.
 * The head works the same way with the neck at the bottom of its region. The torso is the root and never
 * detaches; fragments blown off it are just lost.
 */
public final class StructuralIntegritySystem {
    private StructuralIntegritySystem() {}

    /** Share of a slice's structure below which it tears. */
    public static final float TEAR = 0.24f;
    /** Fragments smaller than this are lost as debris instead of becoming a detached piece. */
    public static final int MIN_PIECE = 6;

    /** A separation to perform. */
    public static final class Break {
        public int group;
        /** Voxels of the region that are no longer attached (indexed like the chunk). */
        public boolean[] mask;
        public int count;
        /** The region's far end went with it, so everything further down the limb goes too. */
        public boolean children;
        /** Nothing of the region stays on the body. */
        public boolean whole;
        /** Too small to keep as a piece: the voxels are simply gone. */
        public boolean debris;
        /** Row at which the limb parted (for placing the stump's bleeding). */
        public int row;
    }

    /** Regions attached to the far end of each region (the next link down the limb). */
    public static int[] children(int g) {
        return switch (g) {
            case Rig.L_UPPER_ARM -> new int[]{Rig.L_FOREARM, Rig.L_HAND};
            case Rig.L_FOREARM -> new int[]{Rig.L_HAND};
            case Rig.R_UPPER_ARM -> new int[]{Rig.R_FOREARM, Rig.R_HAND};
            case Rig.R_FOREARM -> new int[]{Rig.R_HAND};
            case Rig.L_THIGH -> new int[]{Rig.L_SHIN};
            case Rig.R_THIGH -> new int[]{Rig.R_SHIN};
            default -> new int[0];
        };
    }

    /**
     * Examines a region after damage. Updates its fracture flag, tears slices that can no longer hold, and
     * returns the separation that follows, or null when everything left is still attached.
     * With allowSever false only fractures are recorded (the living player keeps their limbs).
     */
    public static Break evaluate(VoxelBody body, int g, boolean allowSever) {
        VoxelBody.Chunk c = body.chunks[g];
        if (c == null || c.empty()) return null;
        boolean limb = VoxelBody.limb(g);
        int jointLo = limb ? c.sy - 2 : 0, jointHi = limb ? c.sy - 1 : (g == Rig.HEAD ? (int) Rig.ORIGIN[g][1] : 1);
        // ---- fracture: a slice with no bone left in it
        if (limb || g == Rig.HEAD) {
            boolean frac = false;
            for (int y = 0; y < c.sy; y++) if (c.rowBone0[y] > 0 && c.rowBone[y] == 0 && c.rowStruct[y] > 0) frac = true;
            c.fractured = frac;
        }
        if (!allowSever || g == Rig.TORSO && c.solid > c.solid0 * 0.2f && !looseTorso(c)) return null;
        // ---- tear: slices too weak to carry what is beyond them
        int tornRow = -1;
        if (limb || g == Rig.HEAD) {
            int from = limb ? 0 : jointHi + 1, to = limb ? jointLo - 1 : Math.min(c.sy - 1, jointHi + 3);
            for (int y = from; y <= to; y++) {
                if (c.rowStruct0[y] == 0 || c.rowStruct[y] == 0 || c.rowIntegrity(y) >= TEAR) continue;
                for (int x = 0; x < c.sx; x++) for (int z = 0; z < c.sz; z++) c.remove(x, y, z);
                tornRow = y;
            }
        }
        // ---- connectivity: flood from the joint
        boolean[] reached = new boolean[c.mat.length];
        int[] stack = new int[c.mat.length];
        int sp = 0, seeds = 0;
        for (int y = jointLo; y <= jointHi; y++) for (int x = 0; x < c.sx; x++) for (int z = 0; z < c.sz; z++) {
            int i = c.idx(x, y, z);
            if (c.mat[i] != EMPTY) { reached[i] = true; stack[sp++] = i; seeds++; }
        }
        while (sp > 0) {
            int i = stack[--sp];
            int z = i % c.sz, y = (i / c.sz) % c.sy, x = i / (c.sz * c.sy);
            if (x > 0) sp = visit(c, reached, stack, sp, i - c.sy * c.sz);
            if (x < c.sx - 1) sp = visit(c, reached, stack, sp, i + c.sy * c.sz);
            if (y > 0) sp = visit(c, reached, stack, sp, i - c.sz);
            if (y < c.sy - 1) sp = visit(c, reached, stack, sp, i + c.sz);
            if (z > 0) sp = visit(c, reached, stack, sp, i - 1);
            if (z < c.sz - 1) sp = visit(c, reached, stack, sp, i + 1);
        }
        Break br = new Break();
        br.group = g;
        br.mask = new boolean[c.mat.length];
        int lowest = c.sy, highest = -1, kept = 0;
        boolean farEndKept = false;
        for (int i = 0; i < c.mat.length; i++) {
            if (c.mat[i] == EMPTY) continue;
            int y = (i / c.sz) % c.sy;
            if (reached[i]) { kept++; if (limb && y <= 1) farEndKept = true; continue; }
            br.mask[i] = true; br.count++;
            lowest = Math.min(lowest, y); highest = Math.max(highest, y);
        }
        // the next link down hangs from this region's far end: gone if nothing reached is left there
        br.children = limb && !farEndKept && children(g).length > 0;
        if (br.count == 0 && !br.children) return null;
        br.whole = kept == 0;
        br.row = tornRow >= 0 ? tornRow : limb ? Math.min(c.sy - 1, highest + 1) : Math.max(0, lowest - 1);
        br.debris = g == Rig.TORSO || br.count < MIN_PIECE && !br.children;
        return br;
    }

    private static int visit(VoxelBody.Chunk c, boolean[] reached, int[] stack, int sp, int i) {
        if (c.mat[i] == EMPTY || reached[i]) return sp;
        reached[i] = true;
        stack[sp] = i;
        return sp + 1;
    }

    /** A torso only needs a connectivity pass when enough of it is gone for something to have come loose. */
    private static boolean looseTorso(VoxelBody.Chunk c) { return c.solid < c.solid0 * 0.8f; }

    /** Forces a region off at its joint (scripted dismemberment, tests): destroys the joint slices so the flood finds nothing. */
    public static void cutJoint(VoxelBody body, int g) {
        VoxelBody.Chunk c = body.chunks[g];
        if (c == null) return;
        boolean limb = VoxelBody.limb(g);
        int lo = limb ? c.sy - 2 : 0, hi = limb ? c.sy - 1 : (int) Rig.ORIGIN[g][1] + 1;
        if (g == Rig.HEAD) { lo = (int) Rig.ORIGIN[g][1]; hi = lo + 1; }
        for (int y = lo; y <= hi && y < c.sy; y++) for (int x = 0; x < c.sx; x++) for (int z = 0; z < c.sz; z++) c.remove(x, y, z);
    }
}
