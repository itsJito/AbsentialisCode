package absentialis.ui;

import static org.lwjgl.opengl.GL11.*;

import absentialis.engine.render.Batch2D;
import absentialis.engine.render.Tex;
import absentialis.engine.voxel.VoxModel;
import absentialis.engine.voxel.VoxRaster;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.system.MemoryUtil;

/**
 * Pixel-art item icons rasterised from the items' voxel models into a shelf-packed GL atlas
 * (16 art pixels per grid cell, nearest filtering), plus the live spinning-inspect texture.
 */
public final class ItemIcons {
    public static final int CELL = 16, ATLAS = 2048, INSPECT = 160;
    private int tex, inspectTex, sx, sy, rowH;
    private final Map<String, float[]> uv = new HashMap<>(); // key -> {u0,v0,u1,v1,wCells,hCells}
    private final ByteBuffer up = MemoryUtil.memAlloc(INSPECT * INSPECT * 4);

    public void init() {
        tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, ATLAS, ATLAS, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        nearest();
        inspectTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, inspectTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, INSPECT, INSPECT, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        nearest();
    }

    private static void nearest() {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
    }

    /**
     * CPU rasterisation of an item at 0 degrees: the voxel model upright (model +y is up on screen)
     * in an image the size of the definition's own footprint. No roll, no landscape normalisation -
     * what the definition says is what gets drawn. size receives {w, h} in pixels.
     */
    public static int[] rasterize(VoxModel m, ItemDef d, int cellsW, int cellsH, int[] size) {
        int w = cellsW * CELL, h = cellsH * CELL;
        size[0] = w; size[1] = h;
        boolean flat = m.sy <= 3;
        if (d.weapon != null) {
            // weapons are drawn side-on so the profile fills the footprint; a one-row footprint gets an even flatter
            // camera, because any elevation spends the little height it has on the top of the receiver
            boolean strip = Math.min(cellsW, cellsH) == 1;
            int[] bb = VoxRaster.bounds(m);
            // a weapon that is wider than it is tall (a crossbow's limbs) only reads from above
            boolean spread = bb[5] - bb[2] > bb[4] - bb[1];
            return VoxRaster.render(m, w, h, strip ? 0.06f : spread ? 0.12f : 0.2f, strip ? 0.10f : spread ? 1.2f : 0.34f, 0, 0.97f, true);
        }
        return VoxRaster.render(m, w, h, 0.6f, flat ? 1.1f : 0.42f, 0, 0.9f, true);
    }

    /** Fraction of the footprint's width and height that the drawn art spans: {x, y} in 0..1 (quality gate). */
    public static float[] coverage(int[] img, int w, int h) {
        int x0 = w, y0 = h, x1 = -1, y1 = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if ((img[y * w + x] >>> 24) > 40) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
        return x1 < 0 ? new float[]{0, 0} : new float[]{(x1 - x0 + 1) / (float) w, (y1 - y0 + 1) / (float) h};
    }

    /** The image a rotated (R) item shows: the 0-degree icon turned a quarter turn counter-clockwise. Never mirrored. */
    public static int[] rotateCcw(int[] img, int w, int h) {
        int[] out = new int[w * h];
        // new image is h wide and w tall; its top row is the old right edge, its left column the old top edge
        for (int y = 0; y < w; y++) for (int x = 0; x < h; x++) out[y * h + x] = img[x * w + (w - 1 - y)];
        return out;
    }

    /** Texture coordinates for the four screen corners {TL, TR, BR, BL} of an icon rect r = {u0, v0, u1, v1}. */
    public static float[] cornerUv(float[] r, boolean rotated) {
        if (!rotated) return new float[]{r[0], r[1], r[2], r[1], r[2], r[3], r[0], r[3]};
        return new float[]{r[2], r[1], r[2], r[3], r[0], r[3], r[0], r[1]};
    }

    /** True when a footprint rectangle shows the item rotated relative to its definition. */
    public static boolean rotatedIn(int baseW, int baseH, float w, float h) {
        boolean rectTall = h > w * 1.02f, rectWide = w > h * 1.02f;
        return rectTall && baseW > baseH || rectWide && baseH > baseW;
    }

    private float[] icon(ItemInstance it) {
        String key = it.visualKey();
        float[] r = uv.get(key);
        if (r != null) return r;
        int[] size = new int[2];
        int wc = it.baseW(), hc = it.baseH();
        int[] img = rasterize(ItemModels.of(it), it.def(), wc, hc, size);
        int w = size[0], h = size[1];
        if (sx + w > ATLAS) { sx = 0; sy += rowH + 2; rowH = 0; }
        if (sy + h > ATLAS) { uv.clear(); sx = sy = rowH = 0; } // atlas full: recycle
        ByteBuffer b = MemoryUtil.memAlloc(w * h * 4);
        for (int c : img) b.put((byte) Tex.r(c)).put((byte) Tex.g(c)).put((byte) Tex.b(c)).put((byte) Tex.a(c));
        b.flip();
        glBindTexture(GL_TEXTURE_2D, tex);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexSubImage2D(GL_TEXTURE_2D, 0, sx, sy, w, h, GL_RGBA, GL_UNSIGNED_BYTE, b);
        MemoryUtil.memFree(b);
        r = new float[]{sx / (float) ATLAS, sy / (float) ATLAS, (sx + w) / (float) ATLAS, (sy + h) / (float) ATLAS, wc, hc};
        uv.put(key, r);
        sx += w + 2;
        rowH = Math.max(rowH, h);
        return r;
    }

    /**
     * Draws an instance's icon inside a rectangle, keeping its aspect (equipment slots are square,
     * grid footprints match exactly). The icon is turned a quarter turn only when the rectangle is
     * the rotated footprint; rotation swaps width and height and never flips the image.
     */
    public void draw(Batch2D b, ItemInstance it, float x, float y, float w, float h, int argb) {
        float[] r = icon(it);
        float cr = ((argb >> 16) & 255) / 255f, cg = ((argb >> 8) & 255) / 255f, cb = (argb & 255) / 255f, ca = ((argb >>> 24) & 255) / 255f;
        boolean rot = rotatedIn((int) r[4], (int) r[5], w, h);
        float iw = rot ? r[5] : r[4], ih = rot ? r[4] : r[5];
        float sc = Math.min(w / iw, h / ih);
        float dw = Math.round(iw * sc), dh = Math.round(ih * sc);
        float x0 = Math.round(x + (w - dw) / 2), y0 = Math.round(y + (h - dh) / 2);
        b.quad4(tex, new float[]{x0, y0, x0 + dw, y0, x0 + dw, y0 + dh, x0, y0 + dh}, cornerUv(r, rot), cr, cg, cb, ca, 0);
    }

    /** Re-rasterises the inspect view for this frame (spinning voxel model). */
    public void drawInspect(Batch2D b, ItemInstance it, float x, float y, float size, float yaw, float pitch) {
        drawModel(b, ItemModels.of(it), x, y, size, yaw, pitch);
    }

    /** Any voxel model as a live pixel-art preview (items, the character creator). */
    public void drawModel(Batch2D b, VoxModel m, float x, float y, float size, float yaw, float pitch) {
        int[] img = VoxRaster.render(m, INSPECT, INSPECT, yaw, pitch, 0, 0.92f, true);
        up.clear();
        for (int c : img) up.put((byte) Tex.r(c)).put((byte) Tex.g(c)).put((byte) Tex.b(c)).put((byte) Tex.a(c));
        up.flip();
        b.flush();
        glBindTexture(GL_TEXTURE_2D, inspectTex);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, INSPECT, INSPECT, GL_RGBA, GL_UNSIGNED_BYTE, up);
        float s = Math.max(1, Math.round(size / INSPECT)) * INSPECT;
        if (s > size) s = size;
        b.image(inspectTex, Math.round(x + (size - s) / 2), Math.round(y + (size - s) / 2), s, s, 0xFFFFFFFF);
        b.flush();
    }

    public void invalidate(ItemInstance it) { uv.remove(it.visualKey()); }
}
