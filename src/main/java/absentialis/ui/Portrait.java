package absentialis.ui;

import static org.lwjgl.opengl.GL11.*;

import absentialis.engine.render.Tex;
import absentialis.engine.util.Rng;
import absentialis.engine.voxel.VoxModel;
import absentialis.engine.voxel.VoxRaster;
import absentialis.gameplay.CombatConfig.Condition;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.system.MemoryUtil;

/**
 * Character portraits for the HUD and the inventory (0.5, brief 11.1): a render of the character's own head -
 * the same voxels the world draws, with the gear actually worn - cached as a small texture and re-rendered only
 * when what it shows changes. The health condition is part of the picture: grime at Caution, blood at Injured,
 * blood and pallor at Critical, eyes closed when down.
 */
public final class Portrait {
    public static final int SIZE = 72;
    private final Map<String, Integer> cache = new HashMap<>();
    private ByteBuffer up;

    /** The head and shoulders of a full-body model (as built by ActorRenderer.flatten), marked for a condition. */
    public static VoxModel head(VoxModel body, Condition cond, boolean down, int seed) {
        // 0.8: the head, the neck and the collar - not the shoulders and the tops of the arms, which made the picture twice
        // as wide as the face and the face a third of the disc. The head's own grid is twelve voxels wide about the middle
        // of the body; whatever is worn on it (a brim, a helmet) is inside that.
        int y0 = Math.max(0, body.sy - HEAD_ROWS), h = body.sy - y0, x0 = Math.max(0, body.sx / 2 - HEAD_HALF), w = Math.min(body.sx - x0, HEAD_HALF * 2);
        VoxModel m = new VoxModel(w, h, body.sz);
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) for (int z = 0; z < body.sz; z++) m.set(x, y, z, body.get(x0 + x, y0 + y, z));
        int level = cond.ordinal();
        if (level == 0 && !down) return m;
        for (int x = 0; x < m.sx; x++) for (int y = 0; y < m.sy; y++) for (int z = 0; z < m.sz; z++) {
            int c = m.get(x, y, z);
            if (c == 0) continue;
            boolean surface = m.get(x, y, z - 1) == 0 || m.get(x - 1, y, z) == 0 || m.get(x + 1, y, z) == 0 || m.get(x, y + 1, z) == 0;
            if (!surface) continue;
            float n = Rng.hash(x, y, z, seed), streak = Rng.hash(x, 3, z, seed + 7);
            int out = c;
            // grime first; then blood that runs down from where it started; then the colour going out of the face
            if (n < 0.10f * level) out = Tex.mix(c, Tex.rgb(40, 34, 28), 0.5f);
            if (level >= 2 && streak < 0.10f + 0.07f * (level - 2) && y < m.sy - 2 - (int) (streak * 40)) out = Tex.mix(c, Tex.rgb(120, 14, 12), 0.8f);
            if (level >= 3 || down) out = Tex.mix(out, Tex.rgb(150, 150, 146), 0.22f);
            m.set(x, y, z, out);
        }
        return m;
    }

    /**
     * Texture for a character: key identifies the look (appearance and gear), cond and down the state. The first
     * call for a combination renders it; later calls return the cached texture.
     */
    /**
     * The yaw the portrait is drawn from. A character's face is on the -z side of its model and the rasteriser's camera
     * at yaw 0 stands on the +z side: 0 is the back of the head (which is what 0.7 shipped - hair, a bare neck and no
     * face). Pi is square to the face.
     */
    public static float FACING = (float) Math.PI;
    /** Rows of the body model kept from the crown down, and half the width kept either side of its middle. */
    public static int HEAD_ROWS = 12, HEAD_HALF = 6;

    public int texture(String key, java.util.function.Supplier<VoxModel> body, Condition cond, boolean down) {
        String k = key + "|" + cond.ordinal() + (down ? "d" : "");
        Integer got = cache.get(k);
        if (got != null) return got;
        if (cache.size() > 96) clear();
        VoxModel m = head(body.get(), cond, down, key.hashCode());
        // the head in the HUD looks straight out of it, from very slightly above
        int[] img = VoxRaster.render(m, SIZE, SIZE, FACING, 0.06f, 0, 0.98f, true);
        if (up == null) up = MemoryUtil.memAlloc(SIZE * SIZE * 4);
        up.clear();
        for (int c : img) up.put((byte) Tex.r(c)).put((byte) Tex.g(c)).put((byte) Tex.b(c)).put((byte) Tex.a(c));
        up.flip();
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, SIZE, SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE, up);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        cache.put(k, tex);
        return tex;
    }

    /** Frees every cached portrait (appearance changed, context lost, or the cache grew past its budget). */
    public void clear() {
        for (int t : cache.values()) glDeleteTextures(t);
        cache.clear();
    }

    public int cached() { return cache.size(); }
}
