package absentialis.character;

import static absentialis.character.Rig.*;

import absentialis.engine.util.Rng;
import absentialis.engine.voxel.VoxModel;

/**
 * A character assembled at runtime from definition-table parts: one voxel grid per SKIN group.
 * Parts paint into the groups they are assigned to; the canvas records which groups a part touched
 * and whether it tried to write outside a grid, which is what import validation checks.
 */
public final class CharModel {
    public final VoxModel[] groups = new VoxModel[GROUPS];
    /** Groups written since the last {@link #beginPart()} and whether any write fell outside a grid. */
    public int touched;
    public boolean clipped;
    public int voxelsWritten;

    public CharModel() {
        for (int g = 0; g < GROUPS; g++) groups[g] = new VoxModel(GRID[g][0], GRID[g][1], GRID[g][2]);
    }

    public void beginPart() { touched = 0; clipped = false; voxelsWritten = 0; }

    public int get(int g, int x, int y, int z) { return groups[g].get(x, y, z); }

    public void set(int g, int x, int y, int z, int c) {
        VoxModel m = groups[g];
        if (!m.in(x, y, z)) { clipped = true; return; }
        m.set(x, y, z, c);
        touched |= 1 << g;
        voxelsWritten++;
    }

    public void box(int g, int x0, int y0, int z0, int x1, int y1, int z1, int c) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(g, x, y, z, c);
    }

    /** Recolours existing voxels only (cloth painted onto the body, never into thin air). */
    public void tint(int g, int x0, int y0, int z0, int x1, int y1, int z1, int c) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) if (get(g, x, y, z) != 0) set(g, x, y, z, c);
    }

    /** Paints only where the grid is still empty (shells around the body). */
    public void shell(int g, int x0, int y0, int z0, int x1, int y1, int z1, int c) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) if (get(g, x, y, z) == 0) set(g, x, y, z, c);
    }

    /** Box on a left-side limb group and its mirror image on the right-side group. */
    public void both(int leftGroup, int x0, int y0, int z0, int x1, int y1, int z1, int c) {
        box(leftGroup, x0, y0, z0, x1, y1, z1, c);
        int w = GRID[leftGroup][0] - 1;
        box(mirror(leftGroup), w - x1, y0, z0, w - x0, y1, z1, c);
    }

    public void tintBoth(int leftGroup, int x0, int y0, int z0, int x1, int y1, int z1, int c) {
        tint(leftGroup, x0, y0, z0, x1, y1, z1, c);
        int w = GRID[leftGroup][0] - 1;
        tint(mirror(leftGroup), w - x1, y0, z0, w - x0, y1, z1, c);
    }

    public void shellBoth(int leftGroup, int x0, int y0, int z0, int x1, int y1, int z1, int c) {
        shell(leftGroup, x0, y0, z0, x1, y1, z1, c);
        int w = GRID[leftGroup][0] - 1;
        shell(mirror(leftGroup), w - x1, y0, z0, w - x0, y1, z1, c);
    }

    /** Per-voxel tone variance: the "HD pixel texture on volume" read. */
    public void finish(int seed) {
        for (int g = 0; g < GROUPS; g++) groups[g].jitter(0.06f, seed + g * 17);
    }

    public int count() { int n = 0; for (VoxModel m : groups) n += m.count(); return n; }

    /** Deterministic speckle helper for patterns (camo, freckles, stains). */
    public static float h(int x, int y, int z, int seed) { return Rng.hash(x, y, z, seed); }
}
