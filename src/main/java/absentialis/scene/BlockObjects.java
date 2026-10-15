package absentialis.scene;

import absentialis.engine.math.Mat4;
import absentialis.engine.render.ModelRenderer;
import absentialis.engine.render.Tex;
import absentialis.engine.voxel.VoxModel;
import absentialis.world.Block;
import absentialis.world.World;

/**
 * 1.3: containers and boarded windows as voxel objects, not textured cubes. A crate, an ammo box, a medicine cabinet, a
 * toolbox, a locker, a filing cabinet, a safe, a bin, and the planks nailed over a window are modelled at sixteen voxels
 * to the block and drawn as objects standing in their cells - their backs to the wall they stand against (the planks
 * along the line of their wall). The cells keep their blocks, so nothing else changes: they are as solid as they were,
 * they stop rounds and eyes as they did, they hold the same loot, and searching one is searching it. The chunk mesher
 * leaves them out (they have the stand-in shape {@code Shapes.PHANTOM_FULL}, like a prop's cell).
 *
 * Which cells hold one is kept per column of the world, worked out when the column is next looked at after it changed
 * (the renderer reports a column changed when it remeshes it), so the drawing walks short lists, not the world.
 */
public final class BlockObjects {
    /** The blocks drawn this way. */
    public static final boolean[] OBJECT = new boolean[256];
    private static final int[] IDS;
    static {
        IDS = new int[]{Block.CRATE, Block.CRATE_MIL, Block.MED_CABINET, Block.TOOLBOX, Block.LOCKER, Block.FILING, Block.SAFE, Block.TRASH, Block.BOARDED};
        for (int id : IDS) OBJECT[id] = true;
    }
    public static boolean is(int id) { return OBJECT[id & 255]; }

    private final World w;
    private final int[][] cells = new int[World.CX * World.CZ][];
    private final boolean[] stale = new boolean[World.CX * World.CZ];
    private final Mat4 m = new Mat4();
    /** Objects drawn last frame (the console, gates). */
    public int drawn;

    public BlockObjects(World w) { this.w = w; java.util.Arrays.fill(stale, true); }

    /** A column has changed (it is being meshed again): its list is worked out again when next wanted. */
    public void columnChanged(int col) { if (col >= 0 && col < stale.length) stale[col] = true; }

    private int[] list(int col) {
        if (!stale[col] && cells[col] != null) return cells[col];
        stale[col] = false;
        int cx = col / World.CZ, cz = col % World.CZ;
        int[] out = new int[16];
        int n = 0;
        for (int x = cx * 16; x < cx * 16 + 16; x++) for (int z = cz * 16; z < cz * 16 + 16; z++) {
            int base = World.idx(x, 0, z);
            for (int y = 0; y < World.H; y++) {
                int id = w.blocks[base + y] & 0xFF;
                if (!OBJECT[id]) continue;
                if (n + 4 > out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
                out[n++] = x; out[n++] = y; out[n++] = z; out[n++] = id | facing(id, x, y, z) << 8;
            }
        }
        return cells[col] = java.util.Arrays.copyOf(out, n);
    }

    /** Quarter turns that put the object's back (model -z) to a wall; the planks lie along their wall's line. */
    private int facing(int id, int x, int y, int z) {
        if (id == Block.BOARDED) {
            boolean ax = cube(x - 1, y, z) || cube(x + 1, y, z), az = cube(x, y, z - 1) || cube(x, y, z + 1);
            return az && !ax ? 1 : 0;
        }
        if (cube(x, y, z - 1)) return 0;
        if (cube(x + 1, y, z)) return 1;
        if (cube(x, y, z + 1)) return 2;
        if (cube(x - 1, y, z)) return 3;
        return (x * 31 + z * 17) & 3;
    }

    private boolean cube(int x, int y, int z) { Block b = w.block(x, y, z); return b != null && b.solid() && b.shape == 0; }

    /** Draws every object within reach of the camera. */
    public void draw(ModelRenderer mr, float camX, float camZ, float reach, java.util.function.Function<float[], Float> lightAt) {
        drawn = 0;
        float[] pos = new float[3];
        int c0x = Math.max(0, (int) ((camX - reach) / 16)), c1x = Math.min(World.CX - 1, (int) ((camX + reach) / 16));
        int c0z = Math.max(0, (int) ((camZ - reach) / 16)), c1z = Math.min(World.CZ - 1, (int) ((camZ + reach) / 16));
        float k = 1 / 16f;
        for (int cx = c0x; cx <= c1x; cx++) for (int cz = c0z; cz <= c1z; cz++) {
            int[] l = list(cx * World.CZ + cz);
            for (int i = 0; i < l.length; i += 4) {
                int x = l[i], y = l[i + 1], z = l[i + 2], id = l[i + 3] & 255, rot = l[i + 3] >> 8;
                if (Math.abs(x + 0.5f - camX) > reach || Math.abs(z + 0.5f - camZ) > reach) continue;
                // lit from the open side (its own cell is solid)
                pos[0] = x + 0.5f + (rot == 1 ? -0.9f : rot == 3 ? 0.9f : 0); pos[1] = y + 0.6f; pos[2] = z + 0.5f + (rot == 0 ? 0.9f : rot == 2 ? -0.9f : 0);
                float light = lightAt.apply(pos);
                if (id == Block.BOARDED) { pos[0] = x + 0.5f + (rot == 1 ? 0.9f : 0); pos[2] = z + 0.5f + (rot == 0 ? -0.9f : 0); light = Math.max(light, lightAt.apply(pos)); }
                m.identity().translate(x + 0.5f, y, z + 0.5f).rotateY(-rot * (float) Math.PI / 2).scale(k, k, k).translate(-8, 0, -8);
                mr.material(id == Block.SAFE || id == Block.LOCKER || id == Block.FILING || id == Block.TOOLBOX ? 0.45f : 0.8f, id == Block.SAFE || id == Block.TOOLBOX ? 0.5f : 0f);
                mr.draw(mesh(mr, id), m, light, 0xFFFFFFFF, 0);
                drawn++;
            }
        }
        mr.material();
    }

    private static ModelRenderer.Mesh mesh(ModelRenderer mr, int id) {
        String key = "blockobj:" + id;
        ModelRenderer.Mesh me = mr.cached(key);
        return me != null ? me : mr.meshAt(key, model(id), 0, 0, 0);
    }

    // ------------------------------------------------------------------ the models (16 voxels a block; the back is -z)
    /** The model of an object block (public for the gates). */
    public static VoxModel model(int id) {
        VoxModel v = new VoxModel(16, 16, 16);
        if (id == Block.MED_CABINET) {
            // a free-standing medical cabinet: white enamel on a dark plinth, two doors with a seam, chrome pulls, a red cross
            int enamel = Tex.rgb(226, 228, 224), shade = Tex.rgb(196, 198, 196), chrome = Tex.rgb(170, 176, 184), red = Tex.rgb(196, 34, 30);
            v.box(1, 0, 2, 14, 1, 13, Tex.rgb(60, 62, 66));
            v.box(1, 2, 2, 14, 15, 13, enamel);
            v.box(1, 15, 1, 14, 15, 14, shade);                          // the top's lip
            v.box(7, 3, 14, 8, 14, 14, shade);                           // the seam between the doors
            v.box(2, 3, 14, 6, 14, 14, enamel); v.box(9, 3, 14, 13, 14, 14, enamel);
            v.box(6, 7, 15, 6, 10, 15, chrome); v.box(9, 7, 15, 9, 10, 15, chrome);
            v.box(3, 10, 15, 5, 10, 15, red); v.box(4, 9, 15, 4, 11, 15, red);   // a red cross on the left door
            v.box(10, 10, 15, 12, 10, 15, red); v.box(11, 9, 15, 11, 11, 15, red);
            v.box(2, 4, 15, 4, 4, 15, Tex.rgb(80, 120, 170));            // a ward label
            return v.jitter(0.03f, id);
        }
        if (id == Block.TOOLBOX) {
            // a mechanic's tool chest on the floor: red steel, three drawers with bright pulls, a lid with a carry handle, latches
            int red = Tex.rgb(176, 36, 32), dark = Tex.rgb(120, 24, 22), steel = Tex.rgb(190, 194, 198), black = Tex.rgb(32, 32, 34);
            v.box(1, 0, 3, 14, 0, 12, black);                            // feet
            v.box(1, 1, 3, 14, 9, 12, red);
            for (int d = 0; d < 3; d++) { int y = 2 + d * 3; v.box(2, y, 13, 13, y + 1, 13, dark); v.box(6, y + 1, 13, 9, y + 1, 13, steel); }
            v.box(1, 10, 3, 14, 11, 12, dark);                           // the lid
            v.box(5, 12, 7, 5, 13, 8, black); v.box(10, 12, 7, 10, 13, 8, black); v.box(5, 13, 7, 10, 13, 8, black);   // handle
            v.box(2, 9, 13, 3, 10, 13, steel); v.box(12, 9, 13, 13, 10, 13, steel);   // latches
            v.box(9, 12, 4, 13, 12, 6, Tex.rgb(150, 150, 140));         // a spanner left on the lid
            return v.jitter(0.04f, id);
        }
        if (id == Block.CRATE_MIL) {
            // ammunition: two olive drab cans side by side and one across them, latched lids, carry handles, yellow stencils
            int od = Tex.rgb(84, 92, 54), odd = Tex.rgb(62, 68, 40), latch = Tex.rgb(150, 150, 140), yellow = Tex.rgb(220, 190, 60);
            for (int c = 0; c < 2; c++) {
                int x0 = 1 + c * 7;
                v.box(x0, 0, 2, x0 + 5, 7, 13, od);
                v.box(x0, 8, 2, x0 + 5, 8, 13, odd);                     // lid
                v.box(x0 + 2, 9, 6, x0 + 3, 9, 9, Tex.rgb(40, 40, 36));   // handle
                v.box(x0, 6, 13, x0 + 5, 7, 13, latch);
                v.box(x0 + 1, 3, 14, x0 + 4, 3, 14, yellow); v.box(x0 + 1, 1, 14, x0 + 2, 1, 14, yellow);
            }
            v.box(2, 9, 3, 13, 14, 8, od); v.box(2, 15, 3, 13, 15, 8, odd); v.box(13, 12, 4, 13, 13, 7, latch);
            v.box(4, 11, 9, 11, 11, 9, yellow);
            return v.jitter(0.04f, id);
        }
        if (id == Block.CRATE) {
            // a slatted wooden crate: frame posts, boards with gaps, a stencilled lid
            int pine = Tex.rgb(176, 136, 84), dark = Tex.rgb(130, 96, 58), gap = 0;
            v.box(1, 0, 1, 14, 14, 14, pine);
            for (int y = 3; y < 14; y += 4) { v.box(1, y, 1, 14, y, 1, gap); v.box(1, y, 14, 14, y, 14, gap); v.box(1, y, 1, 1, y, 14, gap); v.box(14, y, 1, 14, y, 14, gap); }
            for (int[] p : new int[][]{{1, 1}, {13, 1}, {1, 13}, {13, 13}}) v.box(p[0], 0, p[1], p[0] + 1, 14, p[1] + 1, dark);
            v.box(1, 14, 1, 14, 14, 14, dark); v.box(3, 15, 3, 12, 15, 12, pine);
            v.box(5, 7, 15, 10, 9, 15, Tex.rgb(60, 50, 40));
            return v.jitter(0.06f, id);
        }
        if (id == Block.LOCKER) {
            // a steel locker: louvres at the top, a recessed latch, dented
            int grey = Tex.rgb(96, 110, 120), dark = Tex.rgb(66, 76, 84), steel = Tex.rgb(170, 176, 182);
            v.box(2, 0, 2, 13, 15, 13, grey);
            for (int y = 11; y <= 14; y++) if ((y & 1) == 0) v.box(4, y, 14, 11, y, 14, dark);
            v.box(11, 6, 14, 11, 9, 14, steel); v.box(3, 1, 14, 12, 1, 14, dark);
            v.set(6, 5, 13, 0); v.set(7, 5, 13, dark);
            return v.jitter(0.05f, id);
        }
        if (id == Block.FILING) {
            int beige = Tex.rgb(150, 146, 128), dark = Tex.rgb(110, 106, 92), steel = Tex.rgb(176, 178, 176);
            v.box(2, 0, 2, 13, 15, 13, beige);
            for (int d = 0; d < 4; d++) { int y = 1 + d * 4; v.box(3, y, 14, 12, y + 2, 14, beige); v.box(3, y + 3, 14, 12, y + 3, 14, dark); v.box(6, y + 1, 15, 9, y + 1, 15, steel); v.box(7, y + 2, 15, 8, y + 2, 15, Tex.rgb(230, 226, 210)); }
            return v.jitter(0.04f, id);
        }
        if (id == Block.SAFE) {
            int body = Tex.rgb(52, 58, 54), edge = Tex.rgb(36, 40, 38), brass = Tex.rgb(190, 150, 60), steel = Tex.rgb(170, 174, 178);
            v.box(1, 0, 1, 14, 14, 14, body);
            v.box(1, 0, 14, 14, 14, 14, edge); v.box(3, 2, 15, 12, 12, 15, body);
            for (int a = 0; a < 8; a++) { double t = a * Math.PI / 4; v.set(8 + (int) Math.round(Math.cos(t) * 2), 8 + (int) Math.round(Math.sin(t) * 2), 15, a == 0 ? brass : steel); }
            v.box(10, 4, 15, 11, 6, 15, steel);
            return v.jitter(0.03f, id);
        }
        if (id == Block.TRASH) {
            int bin = Tex.rgb(46, 74, 50), lid = Tex.rgb(36, 56, 38);
            v.cylY(0, 12, 7.5f, 7.5f, 6, bin);
            v.cylY(13, 13, 7.5f, 7.5f, 6.5f, lid);
            v.box(6, 14, 7, 9, 14, 8, lid);
            v.box(3, 13, 3, 6, 13, 5, Tex.rgb(210, 206, 196));              // something spilling out
            return v.jitter(0.05f, id);
        }
        if (id == Block.BOARDED) {
            // planks nailed across the opening: three boards, one askew, gaps between them that daylight shows through, nails
            int[] wood = {Tex.rgb(150, 112, 70), Tex.rgb(132, 98, 60), Tex.rgb(164, 126, 80)};
            int nail = Tex.rgb(70, 70, 74);
            v.box(0, 1, 7, 15, 4, 8, wood[0]);
            v.box(0, 7, 7, 15, 9, 8, wood[1]);
            for (int x = 0; x < 16; x++) { int y = 11 + (x * 3) / 16; v.box(x, y, 7, x, y + 2, 8, wood[2]); }
            for (int[] p : new int[][]{{1, 2}, {14, 3}, {1, 8}, {14, 8}, {1, 11}, {14, 13}}) v.set(p[0], p[1], 9, nail);
            return v.jitter(0.07f, id);
        }
        return v.box(0, 0, 0, 15, 15, 15, Tex.rgb(128, 128, 128));
    }
}
