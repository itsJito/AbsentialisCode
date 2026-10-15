package absentialis.vehicle;

import absentialis.world.Block;
import absentialis.world.Material;

/** What each kind of ground does to a tyre: how much of its grip is left, and how much it drags. */
public final class CarSurfaceData {
    private CarSurfaceData() {}

    public enum Surface {
        //          adhesion  rolling resistance (m/s^2)  drag (1/s, velocity-opposing)
        ASPHALT    (1.00f, 0.13f, 0f),
        CONCRETE   (0.95f, 0.15f, 0f),
        DIRT       (0.72f, 0.38f, 0f),
        GRASS      (0.62f, 0.55f, 0f),
        MUD        (0.45f, 1.10f, 0.35f),
        SAND       (0.52f, 0.90f, 1.10f),
        WET_ROAD   (0.68f, 0.14f, 0f);

        /** Share of a tyre's dry-road adhesion available on this surface. */
        public final float adhesion;
        /** Deceleration of a freely rolling car on this surface (m/s^2): tyre flex and the ground giving way. */
        public final float rolling;
        /** Extra velocity-opposing drag while ploughing through it (sand, deep mud). */
        public final float drag;
        Surface(float adhesion, float rolling, float drag) { this.adhesion = adhesion; this.rolling = rolling; this.drag = drag; }
    }

    /** The surface a wheel is standing on, from the block under it; wet turns paved ground into WET_ROAD. */
    public static Surface of(Block b, boolean wet) {
        if (b == null) return Surface.CONCRETE;
        if (b.id == Block.MUD) return Surface.MUD;
        if (b.id == Block.SAND) return Surface.SAND;
        Material m = b.mat;
        switch (m) {
            case ASPHALT: return wet ? Surface.WET_ROAD : Surface.ASPHALT;
            case CONCRETE: case BRICK: case STONE: case TILE: case METAL: case THIN_METAL: case GLASS: case PLASTIC: return wet ? Surface.WET_ROAD : Surface.CONCRETE;
            case GRASS: case FOLIAGE: return Surface.GRASS;
            case DIRT: return wet ? Surface.MUD : Surface.DIRT;
            case WATER: return Surface.MUD;
            default: return Surface.DIRT;   // wood, fabric, flesh: something a tyre grips poorly
        }
    }
}
