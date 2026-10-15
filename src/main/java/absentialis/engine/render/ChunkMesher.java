package absentialis.engine.render;

import absentialis.world.Block;
import absentialis.world.World;

/**
 * Greedy mesher for one 16 x H x 16 chunk column. Faces are merged only when texture layer,
 * per-corner ambient occlusion and per-corner smooth light all match, so merging never changes
 * the lit result. Output vertex format (12 bytes):
 *   a0 ubyte4: x, y, z, normalIndex
 *   a1 ubyte4: texS, texT, layerLo, (layerHi | (variants-1)<<2 | flags<<4)
 *   a2 ubyte4 normalized: sky, block, ao, unused
 */
public final class ChunkMesher {
    public static final int VERTEX_BYTES = 12;
    private final World w;
    private final long[] mask = new long[World.H * 16];
    public final ByteBuf opaque = new ByteBuf(), translucent = new ByteBuf();

    /** Static previews with no door system draw doors as fixed leaves; in play they are entities. */
    public static volatile boolean drawDoors;
    /** 1.0: false when the Astratto foliage system grows the world's tall grass and crops as plants: the mesher then leaves their crossed planes out. */
    public static volatile boolean foliageBlocks = true;

    public ChunkMesher(World w) { this.w = w; }

    // ------------------------------------------------------------------ 1.3: Astratto stage 2 - the mesher is the engine's
    /**
     * Columns are meshed by the native core when it is there ({@code astratto_mesh_column}, C++): the same rules, the same
     * bytes (gate {@code render13}: every column of the city, both ways, compared byte for byte). The Java below stays as
     * the reference it is held to and as the fallback with no library ({@code ABS_NATIVE=0}).
     */
    public static volatile boolean nativeMeshing = !"0".equals(System.getenv("ABS_NATIVE")) && !"0".equals(System.getenv("ABS_NATIVE_MESH"));
    private astratto.bridge.NativeMesher nat;
    private boolean natTried;
    private final astratto.bridge.NativeMesher.Out natOut = new astratto.bridge.NativeMesher.Out();
    /** Columns meshed natively and in Java by this mesher (gates, the console). */
    public int nativeColumns, javaColumns;

    /** What every kind of block and every shape is to the native mesher (built from Block and Shapes, once). */
    private static astratto.bridge.NativeMesher create() {
        astratto.bridge.NativeMesher.BlockDef[] defs = new astratto.bridge.NativeMesher.BlockDef[256];
        for (int i = 0; i < 256; i++) {
            Block b = Block.ALL[i];
            if (b == null) continue;
            astratto.bridge.NativeMesher.BlockDef d = defs[i] = new astratto.bridge.NativeMesher.BlockDef();
            d.layerTop = b.layerTop; d.layerSide = b.layerSide; d.layerBottom = b.layerBottom;
            d.varTop = b.varTop; d.varSide = b.varSide; d.varBottom = b.varBottom; d.shape = b.shape;
            int f = 0;
            if (b.is(Block.CROSS)) f |= astratto.bridge.AstrattoNative.RB_CROSS;
            if (b.is(Block.TRANSLUCENT)) f |= astratto.bridge.AstrattoNative.RB_TRANSLUCENT;
            if (b.is(Block.LIQUID)) f |= astratto.bridge.AstrattoNative.RB_LIQUID;
            if (b.is(Block.CUTOUT)) f |= astratto.bridge.AstrattoNative.RB_CUTOUT;
            if (b.is(Block.EMISSIVE_TEX)) f |= astratto.bridge.AstrattoNative.RB_EMISSIVE;
            if (Block.leaf(i)) f |= astratto.bridge.AstrattoNative.RB_LEAF;
            if (Block.leaf(i) || i == Block.LAUNDRY) f |= astratto.bridge.AstrattoNative.RB_SWAY;
            if (b.occludes()) f |= astratto.bridge.AstrattoNative.RB_OCCLUDES;
            if (b.is(Block.DOOR)) f |= astratto.bridge.AstrattoNative.RB_DOOR;
            if (b.solid()) f |= astratto.bridge.AstrattoNative.RB_SOLID;
            if (b.is(Block.STEP)) f |= astratto.bridge.AstrattoNative.RB_STEP;
            if (i == Block.TABLE || i == Block.DESK || i == Block.COUNTER || i == Block.COMPUTER) f |= astratto.bridge.AstrattoNative.RB_SEAT;
            if (i == Block.TALL_GRASS || i == Block.CROP) f |= astratto.bridge.AstrattoNative.RB_PLANT;
            d.flags = f;
        }
        astratto.bridge.NativeMesher.ShapeDef[] shapes = new astratto.bridge.NativeMesher.ShapeDef[absentialis.world.Shapes.PHANTOM_NONE + 1];
        for (int s = 0; s < shapes.length; s++) {
            astratto.bridge.NativeMesher.ShapeDef d = shapes[s] = new astratto.bridge.NativeMesher.ShapeDef();
            d.boxes = absentialis.world.Shapes.boxes(s);
            d.invisible = absentialis.world.Shapes.invisible(s);
            d.joins = s == absentialis.world.Shapes.POST || s == absentialis.world.Shapes.FIXTURE || s == absentialis.world.Shapes.ARM || s == absentialis.world.Shapes.FENCE;
            d.orient = absentialis.world.Shapes.orientRule(s);
        }
        return astratto.bridge.NativeMesher.create(defs, shapes, Block.BEDROCK);
    }

    /** The column, by the native core: its cells and light (with a border of one) copied over, the streams copied back. */
    private boolean meshNative(int cx, int cz) {
        if (!natTried) { natTried = true; try { nat = create(); } catch (Throwable t) { nat = null; } }
        if (nat == null) return false;
        java.nio.ByteBuffer cells = nat.cells(World.H), lit = nat.light(World.H);
        int ox = cx * 16, oz = cz * 16, h = World.H;
        for (int lx = -1; lx <= 16; lx++) for (int lz = -1; lz <= 16; lz++) {
            int wx = ox + lx, wz = oz + lz, dst = ((lx + 1) * 18 + (lz + 1)) * h;
            if (wx < 0 || wz < 0 || wx >= World.W || wz >= World.D) {
                for (int y = 0; y < h; y++) { cells.put(dst + y, (byte) 0); lit.put(dst + y, (byte) 0xF0); }
                continue;
            }
            int src = World.idx(wx, 0, wz);
            cells.put(dst, w.blocks, src, h);
            lit.put(dst, w.light, src, h);
        }
        int flags = (drawDoors ? astratto.bridge.AstrattoNative.MESH_DOORS : 0) | (foliageBlocks ? 0 : astratto.bridge.AstrattoNative.MESH_NO_PLANTS);
        if (!nat.mesh(ox, oz, World.W, World.D, flags, natOut)) return false;
        opaque.reset(); opaque.ensure(natOut.opaqueBytes); System.arraycopy(natOut.opaque, 0, opaque.data, 0, natOut.opaqueBytes); opaque.size = natOut.opaqueBytes;
        translucent.reset(); translucent.ensure(natOut.translucentBytes); System.arraycopy(natOut.translucent, 0, translucent.data, 0, natOut.translucentBytes); translucent.size = natOut.translucentBytes;
        return true;
    }

    public static final class ByteBuf {
        public byte[] data = new byte[1 << 16];
        public int size;
        void ensure(int n) { if (size + n > data.length) data = java.util.Arrays.copyOf(data, Math.max(data.length * 2, size + n)); }
        void reset() { size = 0; }
        public int quads() { return size / (VERTEX_BYTES * 4); }
    }

    private static final int[][] AX = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
    private static final int[] UAX = {2, 0, 0}, VAX = {1, 2, 1};

    private int id(int x, int y, int z) {
        if (y < 0) return Block.BEDROCK;
        if (x < 0 || z < 0 || x >= World.W || z >= World.D || y >= World.H) return 0;
        return w.blocks[World.idx(x, y, z)] & 0xFF;
    }

    private boolean opq(int x, int y, int z) {
        int i = id(x, y, z);
        return Block.ALL[i].occludes();
    }

    private int lightAt(int x, int y, int z) {
        if (y >= World.H) return 0xF0;
        if (x < 0 || z < 0 || x >= World.W || z >= World.D || y < 0) return 0xF0;
        return w.light[World.idx(x, y, z)] & 0xFF;
    }

    public void mesh(int cx, int cz) {
        if (nativeMeshing && meshNative(cx, cz)) { nativeColumns++; return; }
        javaColumns++;
        meshJava(cx, cz);
    }

    /** The reference: the column meshed here, in Java. */
    public void meshJava(int cx, int cz) {
        opaque.reset();
        translucent.reset();
        int ox = cx * 16, oz = cz * 16;
        int maxY = 0;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int base = World.idx(ox + x, 0, oz + z);
            for (int y = World.H - 1; y > maxY; y--) if (w.blocks[base + y] != 0) { maxY = y; break; }
        }
        maxY = Math.min(World.H - 1, maxY + 1);
        int[] size = {16, maxY + 1, 16};
        int[] p = new int[3], q = new int[3];
        int[] cs = new int[4], cb = new int[4], ca = new int[4];

        for (int a = 0; a < 3; a++) {
            int ua = UAX[a], va = VAX[a];
            int su = size[ua], sv = size[va];
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                int normal = a * 2 + (sgn > 0 ? 0 : 1);
                for (int i = 0; i < size[a]; i++) {
                    // ---- build mask
                    boolean any = false;
                    for (int v = 0; v < sv; v++) for (int u = 0; u < su; u++) {
                        p[a] = i; p[ua] = u; p[va] = v;
                        int wx = ox + p[0], wy = p[1], wz = oz + p[2];
                        int bid = id(wx, wy, wz);
                        long key = 0;
                        if (bid != 0) {
                            Block b = Block.ALL[bid];
                            if (!b.is(Block.CROSS) && b.shape == 0) {
                                int nx = wx + AX[a][0] * sgn, ny = wy + AX[a][1] * sgn, nz = wz + AX[a][2] * sgn;
                                int nid = id(nx, ny, nz);
                                Block nb = Block.ALL[nid];
                                boolean vis;
                                boolean trans = b.is(Block.TRANSLUCENT);
                                if (trans) vis = !nb.occludes() && nid != bid && !(b.is(Block.LIQUID) && nb.is(Block.LIQUID)) && !(nb.is(Block.TRANSLUCENT) && !b.is(Block.LIQUID) && !nb.is(Block.LIQUID));
                                // 0.7: inside a canopy nothing is drawn - a leaf's face against another leaf (of any tone) is hidden
                                else if (b.is(Block.CUTOUT)) vis = !nb.occludes() && nid != bid && !(Block.leaf(bid) && Block.leaf(nid));
                                else vis = !nb.occludes();
                                if (ny >= World.H && a == 1) vis = true;
                                if (vis) {
                                    int layer, var;
                                    if (a == 1 && sgn > 0) { layer = b.layerTop; var = b.varTop; }
                                    else if (a == 1) { layer = b.layerBottom; var = b.varBottom; }
                                    else { layer = b.layerSide; var = b.varSide; }
                                    // per-corner AO + smooth light
                                    for (int c = 0; c < 4; c++) {
                                        int du = (c == 1 || c == 2) ? 1 : -1, dv = (c >= 2) ? 1 : -1;
                                        q[0] = nx; q[1] = ny; q[2] = nz;
                                        int s1x = q[0] + (ua == 0 ? du : 0) , s1y = q[1] + (ua == 1 ? du : 0), s1z = q[2] + (ua == 2 ? du : 0);
                                        int s2x = q[0] + (va == 0 ? dv : 0), s2y = q[1] + (va == 1 ? dv : 0), s2z = q[2] + (va == 2 ? dv : 0);
                                        int ccx = s1x + (va == 0 ? dv : 0), ccy = s1y + (va == 1 ? dv : 0), ccz = s1z + (va == 2 ? dv : 0);
                                        boolean o1 = opq(s1x, s1y, s1z), o2 = opq(s2x, s2y, s2z), oc = opq(ccx, ccy, ccz);
                                        int ao = (o1 && o2) ? 0 : 3 - ((o1 ? 1 : 0) + (o2 ? 1 : 0) + (oc ? 1 : 0));
                                        // 0.7, foliage: leaves shade leaves, softly - the corner of a leaf face tucked in among others is a step
                                        // or two darker (never black), so a canopy has depth: dark underneath and in its hollows, bright where it is open
                                        boolean leafy = Block.leaf(bid);
                                        int leafAo = 3;
                                        if (leafy) { int n3 = (o1 || Block.leaf(id(s1x, s1y, s1z)) ? 1 : 0) + (o2 || Block.leaf(id(s2x, s2y, s2z)) ? 1 : 0) + (oc || Block.leaf(id(ccx, ccy, ccz)) ? 1 : 0); leafAo = n3 >= 3 ? 1 : n3 == 2 ? 2 : n3 == 1 ? 2 : 3; if (a == 1 && sgn < 0) leafAo = Math.max(1, leafAo - 1); }
                                        int l0 = lightAt(nx, ny, nz);
                                        int ss = (l0 >> 4) & 15, sb = l0 & 15, n = 1;
                                        if (!o1) { int l = lightAt(s1x, s1y, s1z); ss += (l >> 4) & 15; sb += l & 15; n++; }
                                        if (!o2) { int l = lightAt(s2x, s2y, s2z); ss += (l >> 4) & 15; sb += l & 15; n++; }
                                        if (!oc && !(o1 && o2)) { int l = lightAt(ccx, ccy, ccz); ss += (l >> 4) & 15; sb += l & 15; n++; }
                                        if (trans || b.is(Block.CUTOUT)) ao = leafy ? leafAo : 3;
                                        cs[c] = (ss + n / 2) / n; cb[c] = (sb + n / 2) / n; ca[c] = ao;
                                    }
                                    int flags = (b.is(Block.EMISSIVE_TEX) ? 1 : 0) | (Block.leaf(bid) || bid == Block.LAUNDRY ? 2 : 0) | (b.is(Block.LIQUID) ? 4 : 0);
                                    key = 1L | ((long) layer << 1) | ((long) (var - 1) << 11) | ((long) flags << 13) | ((trans ? 1L : 0L) << 16);
                                    for (int c = 0; c < 4; c++) key |= ((long) ca[c] << (17 + c * 2)) | ((long) cs[c] << (25 + c * 4)) | ((long) cb[c] << (41 + c * 4));
                                    any = true;
                                }
                            }
                        }
                        mask[u + v * su] = key;
                    }
                    if (!any) continue;
                    // ---- greedy merge
                    for (int v = 0; v < sv; v++) for (int u = 0; u < su; ) {
                        long k = mask[u + v * su];
                        if (k == 0) { u++; continue; }
                        int wdt = 1;
                        while (u + wdt < su && mask[u + wdt + v * su] == k) wdt++;
                        int hgt = 1;
                        outer:
                        while (v + hgt < sv) {
                            for (int t = 0; t < wdt; t++) if (mask[u + t + (v + hgt) * su] != k) break outer;
                            hgt++;
                        }
                        for (int hh = 0; hh < hgt; hh++) for (int t = 0; t < wdt; t++) mask[u + t + (v + hh) * su] = 0;
                        emitQuad(k, a, ua, va, sgn, normal, i, u, v, wdt, hgt);
                        u += wdt;
                    }
                }
            }
        }
        // ---- cross-shaped props (grass, flames, candles, webs)
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y <= maxY; y++) {
            int bid = id(ox + x, y, oz + z);
            if (bid == 0 || !Block.ALL[bid].is(Block.CROSS)) continue;
            if (!foliageBlocks && (bid == Block.TALL_GRASS || bid == Block.CROP)) continue;
            Block b = Block.ALL[bid];
            int l = lightAt(ox + x, y, oz + z);
            int flags = (b.is(Block.EMISSIVE_TEX) ? 1 : 0) | 2;
            int[][] planes = {{x, z, x + 1, z + 1}, {x + 1, z, x, z + 1}};
            for (int[] pl : planes) for (int side = 0; side < 2; side++) {
                int[][] cs4 = {{pl[0], y, pl[1]}, {pl[2], y, pl[3]}, {pl[2], y + 1, pl[3]}, {pl[0], y + 1, pl[1]}};
                for (int c = 0; c < 4; c++) {
                    int cc = side == 0 ? c : 3 - c;
                    int[] cp = cs4[cc];
                    putVertex(opaque, cp[0], cp[1], cp[2], 6, cp[0] == x ? 0 : 1, cp[1], b.layerSide, b.varSide, flags, (l >> 4) & 15, l & 15, 3);
                }
            }
        }
        // ---- shaped blocks: ladders, doors, fences, posts, furniture (sub-block boxes, see world.Shapes)
        int[] box = new int[7];
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y <= maxY; y++) {
            int bid = id(ox + x, y, oz + z);
            if (bid == 0) continue;
            Block b = Block.ALL[bid];
            if (drawDoors == false && b.is(Block.DOOR)) continue;      // the leaf is drawn by scene.Doors, on its hinge
            int[][] boxes = absentialis.world.Shapes.boxes(b.shape);
            if (boxes == null || absentialis.world.Shapes.invisible(b.shape)) continue;
            // a shaped block sits in its own (often unlit, opaque) cell: light it from the brightest side
            int sky = 0, blk = 0;
            for (int n = 0; n < 7; n++) {
                int l = n == 0 ? lightAt(ox + x, y, oz + z) : lightAt(ox + x + (n == 1 ? 1 : n == 2 ? -1 : 0), y + (n == 3 ? 1 : n == 4 ? -1 : 0), oz + z + (n == 5 ? 1 : n == 6 ? -1 : 0));
                sky = Math.max(sky, (l >> 4) & 15); blk = Math.max(blk, l & 15);
            }
            int rot = absentialis.world.Shapes.orientation(w, bid, ox + x, y, oz + z);
            int flags = b.is(Block.EMISSIVE_TEX) ? 1 : 0;
            ByteBuf out = b.is(Block.TRANSLUCENT) ? translucent : opaque;
            for (int pass = 0; pass < (rot == 4 ? 2 : 1); pass++) for (int[] src : boxes) {
                absentialis.world.Shapes.rotate(src, rot == 4 ? pass : rot, box);
                shapeBox(out, b, x * 16, y * 16, z * 16, box, flags, sky, blk);
            }
        }
    }

    /** One box of a shaped block; coordinates are chunk-local sixteenths. */
    private void shapeBox(ByteBuf out, Block b, int bx, int by, int bz, int[] box, int flags, int sky, int blk) {
        int tex = box[6] & 7;
        boolean plane = (box[6] & absentialis.world.Shapes.PLANE) != 0;
        int[] lo = {bx + box[0], by + box[1], bz + box[2]}, hi = {bx + box[3], by + box[4], bz + box[5]};
        int thin = 0;
        for (int a = 1; a < 3; a++) if (hi[a] - lo[a] < hi[thin] - lo[thin]) thin = a;
        int[] pos = new int[3];
        for (int a = 0; a < 3; a++) {
            if (plane && a != thin) continue;
            int ua = UAX[a], va = VAX[a];
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                int normal = a * 2 + (sgn > 0 ? 0 : 1);
                int layer, var;
                if (tex == 1 || tex == 0 && a != 1) { layer = b.layerSide; var = b.varSide; }
                else if (tex == 2 || tex == 0 && sgn > 0) { layer = b.layerTop; var = b.varTop; }
                else { layer = b.layerBottom; var = b.varBottom; }
                boolean reversed = (a == 2) ? (sgn < 0) : (sgn > 0);
                int[] order = reversed ? new int[]{0, 3, 2, 1} : new int[]{0, 1, 2, 3};
                for (int oi = 0; oi < 4; oi++) {
                    int c = order[oi];
                    boolean u1 = c == 1 || c == 2, v1 = c >= 2;
                    pos[a] = sgn > 0 ? hi[a] : lo[a]; pos[ua] = u1 ? hi[ua] : lo[ua]; pos[va] = v1 ? hi[va] : lo[va];
                    putVertexF(out, pos[0], pos[1], pos[2], normal, u1 ? 1 : 0, v1 ? 1 : 0, layer, var, flags, sky, blk);
                }
            }
        }
    }

    /** Vertex at a sixteenth-of-a-block position: whole blocks in the position bytes, the fraction in spare bits. */
    private static void putVertexF(ByteBuf o, int fx, int fy, int fz, int normal, int s, int t, int layer, int var, int flags, int sky, int blk) {
        o.ensure(VERTEX_BYTES);
        byte[] d = o.data;
        int i = o.size;
        d[i] = (byte) (fx >> 4); d[i + 1] = (byte) (fy >> 4); d[i + 2] = (byte) (fz >> 4); d[i + 3] = (byte) (normal | (fz & 15) << 3);
        d[i + 4] = (byte) s; d[i + 5] = (byte) t; d[i + 6] = (byte) (layer & 255); d[i + 7] = (byte) (((layer >> 8) & 3) | ((var - 1) << 2) | (flags << 4));
        d[i + 8] = (byte) (sky * 17); d[i + 9] = (byte) (blk * 17); d[i + 10] = (byte) 255; d[i + 11] = (byte) ((fx & 15) | (fy & 15) << 4);
        o.size = i + VERTEX_BYTES;
    }

    private void emitQuad(long k, int a, int ua, int va, int sgn, int normal, int slice, int u, int v, int wdt, int hgt) {
        int layer = (int) ((k >> 1) & 1023), var = (int) ((k >> 11) & 3) + 1, flags = (int) ((k >> 13) & 7);
        boolean trans = ((k >> 16) & 1) != 0;
        ByteBuf out = trans ? translucent : opaque;
        int plane = slice + (sgn > 0 ? 1 : 0);
        int[] cu = {u, u + wdt, u + wdt, u}, cv = {v, v, v + hgt, v + hgt};
        int[] ao = new int[4], sk = new int[4], bl = new int[4];
        for (int c = 0; c < 4; c++) { ao[c] = (int) ((k >> (17 + c * 2)) & 3); sk[c] = (int) ((k >> (25 + c * 4)) & 15); bl[c] = (int) ((k >> (41 + c * 4)) & 15); }
        boolean reversed = (a == 2) ? (sgn < 0) : (sgn > 0);
        int[] order = reversed ? new int[]{0, 3, 2, 1} : new int[]{0, 1, 2, 3};
        // flip triangulation to avoid AO anisotropy
        if (ao[0] + sk[0] + ao[2] + sk[2] < ao[1] + sk[1] + ao[3] + sk[3]) order = reversed ? new int[]{3, 2, 1, 0} : new int[]{1, 2, 3, 0};
        int[] pos = new int[3];
        for (int oi = 0; oi < 4; oi++) {
            int c = order[oi];
            pos[a] = plane; pos[ua] = cu[c]; pos[va] = cv[c];
            putVertex(out, pos[0], pos[1], pos[2], normal, cu[c], cv[c], layer, var, flags, sk[c], bl[c], ao[c]);
        }
    }

    private static void putVertex(ByteBuf o, int x, int y, int z, int normal, int s, int t, int layer, int var, int flags, int sky, int blk, int ao) {
        o.ensure(VERTEX_BYTES);
        byte[] d = o.data;
        int i = o.size;
        d[i] = (byte) x; d[i + 1] = (byte) y; d[i + 2] = (byte) z; d[i + 3] = (byte) normal;
        d[i + 4] = (byte) s; d[i + 5] = (byte) t; d[i + 6] = (byte) (layer & 255); d[i + 7] = (byte) (((layer >> 8) & 3) | ((var - 1) << 2) | (flags << 4));
        d[i + 8] = (byte) (sky * 17); d[i + 9] = (byte) (blk * 17); d[i + 10] = (byte) (ao * 85); d[i + 11] = 0;
        o.size = i + VERTEX_BYTES;
    }
}
