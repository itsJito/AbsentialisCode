package absentialis.engine.render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL30.*;

import absentialis.engine.util.Noise;
import absentialis.world.Block;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

/**
 * HD voxel texture atlas. Tiles are painted procedurally, packed into one atlas (layer index =
 * tile slot) and uploaded as a GL_TEXTURE_2D_ARRAY so greedy-meshed quads can repeat a tile
 * across merged faces without bleeding into neighbouring tiles.
 */
public final class Atlas {
    public static final int N = TextureLibrary.SIZE;
    public int texId, noiseTex;
    public final Map<String, Integer> layerOf = new LinkedHashMap<>();
    public final Map<String, Integer> variantsOf = new LinkedHashMap<>();
    public final List<int[]> pixels = new ArrayList<>();
    public int[] avgColor;
    public final List<String> missing = new ArrayList<>();

    /** Extra textures used by gear / props that are not block faces. */
    private static final String[] EXTRA = {"canvas_olive", "metal_plate", "plastic_black", "white_metal", "tarp", "car_olive"};

    /** CPU-only build (safe to call from tests without a GL context). */
    public void paintAll() {
        List<String> names = new ArrayList<>();
        for (Block b : Block.all()) for (String s : new String[]{b.texTop, b.texSide, b.texBottom}) if (s != null && !names.contains(s)) names.add(s);
        for (String s : EXTRA) if (!names.contains(s)) names.add(s);
        for (String name : names) {
            int vars = TextureLibrary.variants(name);
            layerOf.put(name, pixels.size());
            variantsOf.put(name, vars);
            for (int v = 0; v < vars; v++) {
                Tex t = TextureLibrary.paint(name, v);
                if (t == null) { missing.add(name); t = new Tex(N, 1).fill(0xFFFF00FF); for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) if (((x / 8) + (y / 8)) % 2 == 0) t.set(x, y, 0xFF000000); }
                pixels.add(t.px);
            }
        }
        avgColor = new int[pixels.size()];
        for (int i = 0; i < pixels.size(); i++) {
            long r = 0, g = 0, b = 0, c = 0;
            for (int p : pixels.get(i)) { if ((p >>> 24) < 40) continue; r += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255; c++; }
            avgColor[i] = c == 0 ? 0 : (int) (0xFF000000L | ((r / c) << 16) | ((g / c) << 8) | (b / c));
        }
        for (Block b : Block.all()) {
            if (b.texTop == null) continue;
            b.layerTop = layerOf.get(b.texTop); b.varTop = variantsOf.get(b.texTop);
            b.layerSide = layerOf.get(b.texSide); b.varSide = variantsOf.get(b.texSide);
            b.layerBottom = layerOf.get(b.texBottom); b.varBottom = variantsOf.get(b.texBottom);
        }
        if (!missing.isEmpty()) System.err.println("[Atlas] Missing texture painters: " + missing);
    }

    public int layer(String name) { Integer l = layerOf.get(name); return l == null ? 0 : l; }

    public void upload() {
        int layers = pixels.size();
        ByteBuffer buf = MemoryUtil.memAlloc(N * N * 4 * layers);
        for (int[] px : pixels) for (int p : px) {
            buf.put((byte) ((p >> 16) & 255)).put((byte) ((p >> 8) & 255)).put((byte) (p & 255)).put((byte) ((p >>> 24) & 255));
        }
        buf.flip();
        texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, texId);
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8, N, N, layers, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAX_LEVEL, 5);
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
        if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
            float max = glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            glTexParameterf(GL_TEXTURE_2D_ARRAY, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, Math.min(8f, max));
        }
        MemoryUtil.memFree(buf);

        // tileable grime/wear noise used by the world shader
        int S = 256;
        ByteBuffer nb = MemoryUtil.memAlloc(S * S * 4);
        for (int y = 0; y < S; y++) for (int x = 0; x < S; x++) {
            float a = Noise.fbmTiled(x / 32f, y / 32f, 8, 4, 101);
            float b = Noise.fbmTiled(x / 8f, y / 64f, 32, 3, 202); // vertical streaks
            float c = Noise.fbmTiled(x / 16f, y / 16f, 16, 3, 303);
            float d = Noise.fbmTiled(x / 64f, y / 64f, 4, 2, 404);
            nb.put((byte) (a * 255)).put((byte) (b * 255)).put((byte) (c * 255)).put((byte) (d * 255));
        }
        nb.flip();
        noiseTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, noiseTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, S, S, 0, GL_RGBA, GL_UNSIGNED_BYTE, nb);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glGenerateMipmap(GL_TEXTURE_2D);
        MemoryUtil.memFree(nb);
    }

    /** Writes the packed atlas sheet to a PNG (dev tool: "atlas" console command). */
    public void dumpPng(String path) {
        int cols = 16, rows = (pixels.size() + cols - 1) / cols;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(cols * N, rows * N, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < pixels.size(); i++) {
            int ox = (i % cols) * N, oy = (i / cols) * N;
            img.setRGB(ox, oy, N, N, pixels.get(i), 0, N);
        }
        try { javax.imageio.ImageIO.write(img, "png", new java.io.File(path)); } catch (Exception e) { e.printStackTrace(); }
    }

    /** Review sheet (dev tool): the named materials, every variant, each tiled 2 x 2 over a flat ground and enlarged. */
    public void dumpSheet(String path, String[] names) {
        List<Integer> tiles = new ArrayList<>();
        for (String n : names) { Integer l = layerOf.get(n.trim()); if (l == null) continue; for (int k = 0; k < variantsOf.get(n.trim()); k++) tiles.add(l + k); }
        int cols = 6, rows = Math.max(1, (tiles.size() + cols - 1) / cols), cell = N * 2 + 4, scale = 2;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(cols * cell * scale, rows * cell * scale, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < tiles.size(); i++) {
            int[] px = pixels.get(tiles.get(i));
            int ox = (i % cols) * cell, oy = (i / cols) * cell;
            for (int y = 0; y < N * 2 * scale; y++) for (int x = 0; x < N * 2 * scale; x++) {
                int p = px[((y / scale) % N) * N + (x / scale) % N], a = p >>> 24;
                int r = (((p >> 16) & 255) * a + 40 * (255 - a)) / 255, g = (((p >> 8) & 255) * a + 60 * (255 - a)) / 255, b = ((p & 255) * a + 90 * (255 - a)) / 255;
                img.setRGB(ox * scale + x, oy * scale + y, (r << 16) | (g << 8) | b);
            }
        }
        try { javax.imageio.ImageIO.write(img, "png", new java.io.File(path)); } catch (Exception e) { e.printStackTrace(); }
    }
}
