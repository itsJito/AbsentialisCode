package absentialis.scene;

import absentialis.world.Block;
import absentialis.world.Material;
import absentialis.world.World;

/**
 * 1.0: what the survivor can hear. Until 1.0 every sound of the world reached the listener by distance alone - a door
 * three houses away, a shot inside a building across the street, a groan two floors up were all as clear as if nothing
 * stood between. Hearing is now of the space the listener is in: a sound is let through by how much stands between it
 * and the ear (each wall takes most of it), by whether it shares the listener's space (indoors to indoors, street to
 * street) and their storey. What comes through walls is also dulled (the engine low-passes by the same figure).
 */
public final class Hearing {
    private Hearing() {}

    /** How much of a sound made at (x, y, z) reaches an ear at (ex, ey, ez): 1 = all of it, down to a few hundredths. */
    public static float through(World w, float ex, float ey, float ez, float x, float y, float z) {
        float dx = x - ex, dy = y - ey, dz = z - ez;
        float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 2.2f) return 1f;
        // what stands between: the line is walked in steps of 60 cm; a run of solid cells is one wall
        int steps = Math.min(96, (int) (d / 0.6f)), walls = 0, thick = 0;
        boolean in = false;
        for (int i = 1; i < steps; i++) {
            float t = i / (float) steps;
            int id = w.get((int) Math.floor(ex + dx * t), (int) Math.floor(ey + dy * t), (int) Math.floor(ez + dz * t));
            Block b = id == 0 ? null : Block.ALL[id];
            boolean solid = b != null && b.solid() && b.mat != Material.FOLIAGE && b.shape == 0 && !b.is(Block.CUTOUT);
            if (solid) { thick++; if (!in) { walls++; in = true; } } else in = false;
        }
        float k = walls == 0 ? 1f : walls == 1 ? (thick <= 2 ? 0.42f : 0.26f) : walls == 2 ? 0.16f : walls == 3 ? 0.08f : 0.04f;
        // another space: one of the two is under a roof and the other is not (an open doorway between them is a wall's worth less, not nothing)
        boolean earIn = roofed(w, ex, ey, ez), srcIn = roofed(w, x, y, z);
        if (earIn != srcIn) k *= walls == 0 ? 0.7f : 0.6f;
        // another storey of a building
        if ((earIn || srcIn) && Math.abs(dy) > 3.4f) k *= 0.6f;
        return Math.max(0.02f, k);
    }

    /** True when there is something solid over a point within a few storeys: it is indoors (or under cover). */
    public static boolean roofed(World w, float x, float y, float z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        if (ix < 0 || iz < 0 || ix >= World.W || iz >= World.D) return false;
        return y < World.GROUND - 2 || w.heightAt(ix, iz) > y + 0.5f;
    }
}
