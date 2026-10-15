package absentialis.scene;

import absentialis.engine.audio.gun.AcousticEnvironment;
import absentialis.entity.Physics;
import absentialis.world.Block;
import absentialis.world.Material;
import absentialis.world.World;

/**
 * The voxel world as the acoustic model sees it (0.5): rays against real blocks, and how much of a surface's
 * energy its material keeps. No allocation - a shot casts a bounded fan of these.
 */
public final class WorldAcoustics implements AcousticEnvironment.Geometry {
    private final World w;

    public WorldAcoustics(World w) { this.w = w; }

    /** Fraction of a reflection a material swallows. */
    public static float absorption(Material m) {
        return switch (m) {
            case CONCRETE, STONE, BRICK, ASPHALT -> 0.06f;
            case METAL, THIN_METAL -> 0.04f;
            case TILE, GLASS -> 0.05f;
            case WOOD, PLASTIC -> 0.16f;
            case DIRT -> 0.40f;
            case GRASS -> 0.55f;
            case FOLIAGE -> 0.75f;
            case FABRIC, FLESH -> 0.65f;
            case WATER -> 0.10f;
            default -> 0.5f;
        };
    }

    /** The name of the footstep set for the block underfoot (gravel and rubble, asphalt and tile have their own). */
    public static String stepSurface(Block b) {
        if (b == null) return "concrete";
        if (b.id == Block.GRAVEL || b.id == Block.RUBBLE || b.id == Block.RUBBLE_BRICK) return "gravel";
        if (b.mat == Material.ASPHALT) return "asphalt";
        if (b.mat == Material.TILE) return "tile";
        return b.mat.stepSound;
    }

    @Override
    public float ray(float ox, float oy, float oz, float dx, float dy, float dz, float max, float[] hit) {
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        float tdx = Math.abs(1 / (dx == 0 ? 1e-6f : dx)), tdy = Math.abs(1 / (dy == 0 ? 1e-6f : dy)), tdz = Math.abs(1 / (dz == 0 ? 1e-6f : dz));
        float tmx = (dx > 0 ? (x + 1 - ox) : (ox - x)) * tdx, tmy = (dy > 0 ? (y + 1 - oy) : (oy - y)) * tdy, tmz = (dz > 0 ? (z + 1 - oz) : (oz - z)) * tdz;
        float t = 0;
        boolean first = true;
        while (t <= max) {
            if (y < 0 || y >= World.H) return -1;
            int id = w.get(x, y, z);
            // the cell the source stands in is never the answer (a muzzle beside a post is not inside a wall)
            if (id != 0 && !first) {
                Block b = Block.ALL[id];
                if (b.solid() && b.mat != Material.FOLIAGE) {
                    hit[0] = ox + dx * t; hit[1] = oy + dy * t; hit[2] = oz + dz * t; hit[3] = absorption(b.mat);
                    return t;
                }
            }
            first = false;
            if (tmx < tmy && tmx < tmz) { x += sx; t = tmx; tmx += tdx; }
            else if (tmy < tmz) { y += sy; t = tmy; tmy += tdy; }
            else { z += sz; t = tmz; tmz += tdz; }
        }
        return -1;
    }

    @Override
    public float openness(float ax, float ay, float az, float bx, float by, float bz) {
        // 1.0: gunfire obeys the same hearing as everything else - a shot in another building, or behind two walls, is a thud
        return Math.min(Physics.openness(w, ax, ay, az, bx, by, bz), Hearing.through(w, bx, by, bz, ax, ay, az) * 1.15f);
    }
}
