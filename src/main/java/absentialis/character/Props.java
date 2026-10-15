package absentialis.character;

import absentialis.engine.render.Tex;
import absentialis.engine.voxel.VoxModel;

/**
 * Things that are part of a creature but too long for a limb's own grid (0.7): the Gladiator's claws, the Butcher's
 * chainsaw. Each is a small voxel model drawn on a hand bone, in that bone's own voxel space, so it swings with the
 * arm and falls with the body. Built once.
 */
public final class Props {
    private Props() {}

    /** Where each model's pivot is, in its own voxels: the point that sits on the hand bone's pivot. */
    public static final float[] CLAWS_AT = {3, 12, 3}, SAW_AT = {3, 20, 3.5f};

    private static VoxModel claws;
    /** 0.9: the saw in the four positions of its chain (a tooth every fourth link: four frames is one tooth's travel). */
    public static final int SAW_FRAMES = 4;
    private static final VoxModel[] saw = new VoxModel[SAW_FRAMES];

    /** Three long steel claws on the back of a gauntlet, running on down past the knuckles. */
    public static VoxModel claws() {
        if (claws != null) return claws;
        VoxModel m = new VoxModel(6, 11, 6);
        int steel = Tex.rgb(176, 180, 188), edge = Tex.rgb(226, 230, 236), dark = Tex.rgb(92, 96, 104), blood = Tex.rgb(112, 20, 20);
        for (int k = 0; k < 3; k++) {
            int x = k * 2 + (k == 2 ? 1 : 0), len = k == 1 ? 11 : 10;
            for (int i = 0; i < len; i++) {
                int y = 10 - i, z = i > len - 3 ? 2 : 1;                 // the last of it curves forward to the point
                m.set(x, y, z, i == 0 ? dark : i > len - 3 ? edge : steel);
                if (i < 3) m.set(x, y, z + 1, dark);                     // the root, where it is riveted to the plate
            }
            m.set(x, 10 - len + 2, 2, blood);
        }
        m.jitter(0.05f, 7);
        return claws = m;
    }

    /** A chainsaw: the engine block in the fist, the bar and its chain running on ahead of the hand. */
    public static VoxModel chainsaw() { return chainsaw(0); }

    /**
     * 0.9: the saw with its chain moved on by {@code frame} links. The chain runs out along the top of the bar, round the nose
     * and back along the bottom, so the teeth on the two edges travel opposite ways; drawn frame after frame it is a chain
     * running, and held on one frame it is a chain that has stopped.
     */
    public static VoxModel chainsaw(int frame) {
        frame = Math.floorMod(frame, SAW_FRAMES);
        if (saw[frame] != null) return saw[frame];
        VoxModel m = new VoxModel(6, 22, 8);
        int body = Tex.rgb(206, 96, 26), dark = Tex.rgb(38, 38, 42), bar = Tex.rgb(168, 172, 178), chain = Tex.rgb(60, 62, 68), link = Tex.rgb(96, 99, 108), blood = Tex.rgb(118, 16, 16), grip = Tex.rgb(26, 26, 28);
        // engine block and its housing
        for (int x = 0; x < 6; x++) for (int y = 14; y < 21; y++) for (int z = 0; z < 7; z++) m.set(x, y, z, (y == 14 || y == 20) ? dark : body);
        for (int x = 1; x < 5; x++) for (int z = 1; z < 6; z++) m.set(x, 21, z, grip);            // top handle
        m.set(2, 17, 7, dark); m.set(3, 17, 7, dark); m.set(2, 18, 7, dark); m.set(3, 18, 7, dark); // pull-start
        for (int y = 15; y < 20; y += 2) for (int x = 0; x < 6; x += 5) m.set(x, y, 3, dark);     // cooling slots
        // the guide bar, and the chain round its edge
        for (int y = 1; y < 14; y++) for (int z = 2; z < 6; z++) {
            boolean rim = z == 2 || z == 5 || y == 1;
            // the links alternate dark and light, and the pattern travels: down one edge of the bar, up the other
            int travel = z == 2 ? y + frame : z == 5 ? y - frame : z + frame;
            int c = !rim ? bar : Math.floorMod(travel, 2) == 0 ? chain : link;
            m.set(2, y, z, c); m.set(3, y, z, c);
        }
        for (int y = 2; y < 14; y++) {                                                             // teeth: every fourth link, moving with it
            if (Math.floorMod(y + frame, 4) == 0) m.set(2, y, 1, chain);
            if (Math.floorMod(y - frame, 4) == 0) m.set(3, y, 6, chain);
        }
        if (frame % 2 == 0) m.set(2, 0, 3, chain); else m.set(3, 0, 4, chain);                     // the tooth going round the nose
        // it has been used
        for (int y = 1; y < 9; y++) if ((y * 7 + 3) % 3 != 0) { m.set(2, y, 3 + y % 2, blood); m.set(3, y, 4 - y % 2, blood); }
        m.jitter(0.05f, 11);
        return saw[frame] = m;
    }
}
