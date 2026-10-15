package absentialis.engine.render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL30.*;

import absentialis.world.Block;
import absentialis.world.World;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.lwjgl.system.MemoryUtil;

/**
 * The world's voxels on the GPU: one unsigned byte per block in a 3D texture, kept in step with the CPU world a
 * column at a time, plus a palette that tells a ray what it hit (albedo and emitted light per block id) and a
 * bitmask of the ids that stop light. Probe updates and local-light shadows traverse it directly.
 *
 * The texture is laid out (y, z, x) because that is the order the world array is stored in: a 16x16 column of
 * blocks is sixteen contiguous runs and uploads without repacking.
 */
public final class VoxelVolume {
    public int tex, palette;
    /** 256 bits: block ids that are full, light-stopping cubes. */
    public final int[] opaqueMask = new int[8];
    private final World world;
    private final ByteBuffer column = MemoryUtil.memAlloc(16 * 16 * World.H);
    private boolean filled;
    public int uploads;

    public VoxelVolume(World world) { this.world = world; }

    public void init(Atlas atlas) {
        tex = glGenTextures();
        glBindTexture(GL_TEXTURE_3D, tex);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage3D(GL_TEXTURE_3D, 0, GL_R8UI, World.H, World.D, World.W, 0, GL_RED_INTEGER, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        buildPalette(atlas);
    }

    /** Albedo and emission per block id, from the textures the block is actually drawn with. */
    private void buildPalette(Atlas atlas) {
        FloatBuffer fb = MemoryUtil.memAllocFloat(256 * 2 * 4);
        float[] emit = new float[256 * 4];
        for (int id = 0; id < 256; id++) {
            Block b = Block.ALL[id];
            float r = 0.5f, g = 0.5f, bl = 0.5f;
            if (b != null && b.texSide != null && atlas.avgColor != null) {
                // walls are seen side-on, floors from above: the bounce colour is a blend of both
                int cs = atlas.avgColor[b.layerSide], ct = atlas.avgColor[b.layerTop];
                r = lin((((cs >> 16) & 255) + ((ct >> 16) & 255)) / 510f);
                g = lin((((cs >> 8) & 255) + ((ct >> 8) & 255)) / 510f);
                bl = lin(((cs & 255) + (ct & 255)) / 510f);
            }
            if (b != null && b.occludes()) opaqueMask[id >> 5] |= 1 << (id & 31);
            fb.put(r).put(g).put(bl).put(1);
            if (b != null && b.light > 0) {
                // emitted radiance: the block's own colour, as bright as its light level says
                float k = (float) Math.pow(b.light / 15f, 2.0) * 9f;
                float m = Math.max(r, Math.max(g, bl)) + 1e-3f;
                emit[id * 4] = r / m * k; emit[id * 4 + 1] = g / m * k; emit[id * 4 + 2] = bl / m * k;
            }
        }
        fb.put(emit);
        fb.flip();
        palette = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, palette);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, 256, 2, 0, GL_RGBA, GL_FLOAT, fb);
        MemoryUtil.memFree(fb);
    }

    private static float lin(float c) { return (float) Math.pow(c, 2.2); }

    /**
     * Uploads changed columns (at most maxColumns per call, 0 = all of them) and reports each to the probe field.
     * Returns the number still waiting.
     */
    public int sync(WorldRenderer wr, ProbeField probes, int maxColumns) {
        java.util.ArrayDeque<Integer> q = wr.voxelDirty;
        if (q.isEmpty()) return 0;
        glBindTexture(GL_TEXTURE_3D, tex);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        int n = 0;
        while (!q.isEmpty() && (maxColumns <= 0 || n < maxColumns)) {
            int c = q.poll();
            wr.voxelQueued[c] = false;
            int cx = c / World.CZ, cz = c % World.CZ;
            column.clear();
            for (int x = cx * 16; x < cx * 16 + 16; x++) column.put(world.blocks, World.idx(x, 0, cz * 16), 16 * World.H);
            column.flip();
            glTexSubImage3D(GL_TEXTURE_3D, 0, 0, cz * 16, cx * 16, World.H, 16, 16, GL_RED_INTEGER, GL_UNSIGNED_BYTE, column);
            if (probes != null) probes.invalidate(cx, cz, filled);
            n++;
            uploads++;
        }
        if (q.isEmpty()) filled = true;
        return q.size();
    }

    public void dispose() { glDeleteTextures(tex); glDeleteTextures(palette); MemoryUtil.memFree(column); }
}
