package absentialis.world;

/**
 * Physical material of a block: drives footstep sounds, movement noise, bullet penetration
 * (simplified data table, GDD 6.2 step 3), ricochet and impact effects.
 */
public enum Material {
    //            stepSound  noiseMul  resist  ricochet  r    g    b   (impact debris colour)
    AIR       ("none",      0.0f,     0.00f,  0.0f,     0,   0,   0),
    CONCRETE  ("concrete",  1.0f,     9.00f,  0.10f,  150, 148, 140),
    ASPHALT   ("concrete",  1.0f,     9.00f,  0.05f,   60,  60,  62),
    BRICK     ("concrete",  1.0f,     6.00f,  0.05f,  150,  70,  50),
    STONE     ("concrete",  1.0f,     9.00f,  0.15f,  140, 135, 125),
    TILE      ("concrete",  1.1f,     5.00f,  0.08f,  220, 220, 215),
    METAL     ("metal",     1.6f,     9.00f,  0.35f,  120, 125, 130),
    THIN_METAL("metal",     1.5f,     0.70f,  0.25f,  130, 130, 135),
    WOOD      ("wood",      1.2f,     0.45f,  0.00f,  130,  95,  60),
    GLASS     ("glass",     1.3f,     0.05f,  0.00f,  190, 220, 230),
    FABRIC    ("fabric",    0.5f,     0.15f,  0.00f,  120, 110, 100),
    PLASTIC   ("wood",      0.9f,     0.30f,  0.00f,  200, 200, 200),
    DIRT      ("dirt",      0.6f,     4.00f,  0.00f,  100,  80,  55),
    GRASS     ("grass",     0.5f,     4.00f,  0.00f,   80, 110,  50),
    FOLIAGE   ("grass",     0.4f,     0.10f,  0.00f,   60, 100,  40),
    WATER     ("water",     1.4f,     0.80f,  0.00f,   80, 110, 120),
    FLESH     ("flesh",     0.8f,     0.50f,  0.00f,  120,  40,  40);

    public final String stepSound;
    public final float noiseMul;
    /** Penetration resistance per block traversed. >= ~5 effectively stops small arms. */
    public final float resist;
    public final float ricochet;
    public final int r, g, b;

    Material(String step, float noise, float resist, float ric, int r, int g, int b) {
        this.stepSound = step; this.noiseMul = noise; this.resist = resist; this.ricochet = ric;
        this.r = r; this.g = g; this.b = b;
    }
}
