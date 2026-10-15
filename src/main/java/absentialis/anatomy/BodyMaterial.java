package absentialis.anatomy;

/**
 * The materials a body is made of, shared by every character (one table, no per-voxel objects).
 *
 * Layers, from the outside in:
 *   surface    SKIN, CLOTH, ARMOR   what you see on an unhurt character
 *   soft       TISSUE               the destructible bulk under the surface
 *   structure  BONE                 what holds a limb together
 *   joint      JOINT                the connection between a limb and what it hangs from
 *   internal   ORGAN                simplified insides, visible once enough has been taken away
 *
 * Each material answers a hit differently: how much energy a voxel of it absorbs before it is destroyed
 * (durability), how much of a projectile's penetrating power it uses up (resistance), how much it counts toward
 * holding a limb on (structural weight), how freely it bleeds, and what it looks like when exposed.
 */
public final class BodyMaterial {
    private BodyMaterial() {}

    public static final byte EMPTY = 0, SKIN = 1, CLOTH = 2, ARMOR = 3, TISSUE = 4, BONE = 5, JOINT = 6, ORGAN = 7;
    public static final int COUNT = 8;
    public static final String[] NAME = {"empty", "skin", "cloth", "armor", "tissue", "bone", "joint", "organ"};
    /** Energy (in damage points) a voxel takes before it is gone. */
    public static final float[] DURABILITY = {0, 9, 6, 70, 15, 64, 46, 11};
    /** Penetrating power a projectile spends crossing one voxel. */
    public static final float[] RESIST = {0, 0.10f, 0.12f, 1.9f, 0.16f, 0.95f, 0.6f, 0.14f};
    /** Contribution to the structural integrity of the slice of limb it sits in. */
    public static final int[] STRUCTURAL = {0, 1, 0, 0, 2, 7, 5, 1};
    /** Blood released when a voxel of this is destroyed (arbitrary volume units; tissue bleeds, bone hardly does). */
    public static final float[] BLEED = {0, 0.4f, 0, 0, 1.0f, 0.25f, 0.6f, 1.6f};

    public static boolean surface(byte m) { return m == SKIN || m == CLOTH || m == ARMOR; }

    private static int rgb(int r, int g, int b) { return 0xFF000000 | (r << 16) | (g << 8) | b; }
    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    /** Colour of an interior voxel when exposed, with a little per-voxel variation so no two wounds match. */
    public static int exposed(byte m, boolean infected, float hash, boolean head) {
        int v = (int) ((hash - 0.5f) * 44);
        switch (m) {
            case BONE: return rgb(clamp(214 + v / 2), clamp(204 + v / 2), clamp(180 + v / 2));
            case JOINT: return rgb(clamp(206 + v / 2), clamp(176 + v / 2), clamp(168 + v / 2));
            case ORGAN:
                if (head) return infected ? rgb(clamp(96 + v), clamp(104 + v), clamp(72 + v)) : rgb(clamp(190 + v), clamp(138 + v), clamp(138 + v));   // brain
                return infected ? rgb(clamp(52 + v), clamp(58 + v / 2), clamp(26 + v / 2)) : rgb(clamp(112 + v), clamp(20 + v / 3), clamp(34 + v / 3));
            default:
                return infected ? rgb(clamp(64 + v), clamp(72 + v), clamp(30 + v / 2)) : rgb(clamp(150 + v), clamp(26 + v / 3), clamp(24 + v / 3));
        }
    }

    public static int blood(boolean infected) { return infected ? 0xFF3E4A1E : 0xFF7E1410; }

    /** Blends a colour toward another (discolouration: bruising, blood soaking, charring). */
    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
    }
}
