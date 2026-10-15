package absentialis.anatomy;

import static absentialis.anatomy.BodyMaterial.EMPTY;

import absentialis.character.Rig;
import absentialis.engine.math.Mat4;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.List;

/**
 * Separation. When the structure says part of a body is no longer attached, this takes those voxels out of the
 * body - with every wound they already carry - and makes them a body of their own: an independent rigid piece
 * that keeps the pose the limb had at the moment it came away, inherits the character's motion plus the push of
 * the blow, tumbles, lands and comes to rest. What is left on the character is exactly what was not taken; the
 * regions that are entirely gone are reported so animation and gameplay stop using them.
 *
 * Pieces use deliberately simple physics (a handful of contact samples, bounce, friction, sleep): there can be
 * many, and none needs more.
 */
public final class DismembermentSystem {
    /** A detached part of a body lying (or flying) in the world. */
    public static final class Piece {
        public final int id;
        /** The piece's own anatomy: only the regions (and voxels) that came away. */
        public final VoxelBody body;
        /** World transform of the piece's root region (including the character's voxel scale). */
        public final Mat4 xform = new Mat4();
        /** Transform of each carried region relative to the root, frozen from the pose at separation (null = not carried). */
        public final Mat4[] rel = new Mat4[Rig.GROUPS];
        public final int root;
        public float vx, vy, vz, wx, wy, wz, age, quiet;
        public boolean rest;
        /** Centre of the piece in root voxel space, and contact samples around it. */
        public float cx, cy, cz;
        public final float[] samples = new float[6 * 3];
        /** Key for looking up the character model the piece is drawn from (the owner's look), and who it belonged to. */
        public Object look;
        public int ownerId;
        public float bleed;
        Piece(int id, VoxelBody body, int root) { this.id = id; this.body = body; this.root = root; }
        private final float[] p = new float[3];
        public float[] centre() { xform.transformPoint(cx, cy, cz, p); return p; }
    }

    public interface Listener {
        /** A piece struck a surface: where, how hard. */
        void landed(Piece p, float x, float y, float z, float speed);
    }

    public final List<Piece> pieces = new ArrayList<>();
    public int maxPieces = 60, detached;
    private int nextId = 1;
    private final Mat4 inv = new Mat4(), tmp = new Mat4(), rot = new Mat4();
    private final float[] v3 = new float[3];

    /**
     * Carries out a break. bones are the owner's current region transforms; (vx, vy, vz) the owner's velocity and
     * (ix, iy, iz) the velocity the blow adds. Returns the piece, or null when the break was only debris.
     * The owner's body is updated in place; regionsGone receives the rig-group bits that no longer exist on it.
     */
    /**
     * A head is gone when nothing is left above the neck. 0.5 asked for the whole region to be empty, so a beard, a
     * collar of long hair or a hood below the joint kept a severed head "attached": the skull flew off and the body
     * was still counted - and drawn - as having one.
     */
    private static boolean headGone(VoxelBody.Chunk c) {
        int neck = (int) Rig.ORIGIN[Rig.HEAD][1] + 1;
        for (int i = 0; i < c.mat.length; i++) if (c.mat[i] != EMPTY && (i / c.sz) % c.sy > neck) return false;
        return true;
    }

    public Piece detach(VoxelBody owner, StructuralIntegritySystem.Break br, Mat4[] bones, float vx, float vy, float vz, float ix, float iy, float iz, float spin, int[] regionsGone) {
        VoxelBody.Chunk src = owner.chunks[br.group];
        int gone = 0;
        if (br.debris) {
            // too little to keep: the voxels are lost
            for (int i = 0; i < br.mask.length; i++) if (br.mask[i]) { int z = i % src.sz, y = (i / src.sz) % src.sy, x = i / (src.sz * src.sy); src.remove(x, y, z); }
            if (src.empty()) gone |= 1 << br.group;
            if (regionsGone != null) regionsGone[0] = gone;
            return null;
        }
        VoxelBody pb = VoxelBody.piece(owner);
        Piece p = new Piece(nextId++, pb, br.group);
        // ---- the voxels of the struck region that came away, damage and all
        pb.chunks[br.group] = src.extract(br.mask);
        float sx = 0, sy = 0, sz = 0;
        int n = 0, ylo = src.sy, yhi = -1;
        for (int i = 0; i < br.mask.length; i++) {
            if (!br.mask[i]) continue;
            int z = i % src.sz, y = (i / src.sz) % src.sy, x = i / (src.sz * src.sy);
            src.remove(x, y, z);
            sx += x + 0.5f; sy += y + 0.5f; sz += z + 0.5f; n++;
            ylo = Math.min(ylo, y); yhi = Math.max(yhi, y);
        }
        if (src.empty() || br.group == Rig.HEAD && headGone(src)) gone |= 1 << br.group;
        p.xform.set(bones[br.group]);
        p.rel[br.group] = new Mat4();
        inv.invert(bones[br.group]);
        // ---- everything further down the limb goes with it, whole
        if (br.children) for (int c : StructuralIntegritySystem.children(br.group)) {
            VoxelBody.Chunk cc = owner.chunks[c];
            if (cc == null || cc.empty()) continue;
            pb.chunks[c] = cc.extract(null);
            for (int i = 0; i < cc.mat.length; i++) if (cc.mat[i] != EMPTY) { int z = i % cc.sz, y = (i / cc.sz) % cc.sy, x = i / (cc.sz * cc.sy); cc.remove(x, y, z); }
            gone |= 1 << c;
            p.rel[c] = new Mat4().set(inv).mul(bones[c]);
        }
        if (regionsGone != null) regionsGone[0] = gone;
        // ---- centre and contact samples (in root bone space: voxel index minus the region's origin)
        float[] o = Rig.ORIGIN[br.group];
        if (n > 0) { p.cx = sx / n - o[0]; p.cy = sy / n - o[1]; p.cz = sz / n - o[2]; }
        float ext = Math.max(1.5f, (yhi - ylo + 1) * 0.5f);
        float[] s = p.samples;
        int k = 0;
        float[][] offs = {{0, ext, 0}, {0, -ext, 0}, {1.6f, 0, 0}, {-1.6f, 0, 0}, {0, 0, 1.6f}, {0, 0, -1.6f}};
        for (float[] of : offs) { s[k++] = p.cx + of[0]; s[k++] = p.cy + of[1]; s[k++] = p.cz + of[2]; }
        if (br.children) {
            // a longer piece: put the far sample at the end of the last region it carries
            int[] ch = StructuralIntegritySystem.children(br.group);
            for (int c : ch) if (p.rel[c] != null) { p.rel[c].transformPoint(0, -Rig.LENGTH[c], 0, v3); s[3] = v3[0]; s[4] = v3[1]; s[5] = v3[2]; }
        }
        p.vx = vx + ix; p.vy = vy + iy; p.vz = vz + iz;
        p.wx = (hash(p.id, 1) - 0.5f) * spin; p.wy = (hash(p.id, 2) - 0.5f) * spin; p.wz = (hash(p.id, 3) - 0.5f) * spin;
        p.bleed = 1;
        pieces.add(p);
        detached++;
        while (pieces.size() > maxPieces) pieces.remove(0);
        return p;
    }

    private static float hash(int a, int b) { int h = a * 374761393 + b * 668265263; h = (h ^ (h >>> 13)) * 1274126177; return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f; }

    /** Steps every piece that is still moving. Resting pieces cost nothing. */
    public void update(World w, float dt, Listener l) {
        for (int i = 0; i < pieces.size(); i++) {
            Piece p = pieces.get(i);
            p.age += dt;
            p.bleed = Math.max(0, p.bleed - dt * 0.12f);
            if (p.rest) continue;
            p.vy -= 18f * dt;
            // rotate about the piece's own centre, then translate
            float[] c = p.centre();
            float cx = c[0], cy = c[1], cz = c[2];
            float wl = (float) Math.sqrt(p.wx * p.wx + p.wy * p.wy + p.wz * p.wz);
            if (wl > 1e-3f) {
                axisAngle(rot, p.wx / wl, p.wy / wl, p.wz / wl, wl * dt);
                tmp.identity().translate(cx, cy, cz).mul(rot).translate(-cx, -cy, -cz).mul(p.xform);
                p.xform.set(tmp);
            }
            float mx = p.vx * dt, my = p.vy * dt, mz = p.vz * dt;
            p.xform.m[12] += mx; p.xform.m[13] += my; p.xform.m[14] += mz;
            // contacts
            boolean hit = false;
            float deepest = 0;
            for (int k = 0; k < 6; k++) {
                p.xform.transformPoint(p.samples[k * 3], p.samples[k * 3 + 1], p.samples[k * 3 + 2], v3);
                int bx = (int) Math.floor(v3[0]), by = (int) Math.floor(v3[1]), bz = (int) Math.floor(v3[2]);
                if (!w.solid(bx, by, bz)) continue;
                hit = true;
                if (!w.solid(bx, by + 1, bz)) deepest = Math.max(deepest, by + 1 - v3[1]);
                else {
                    // a wall: undo the sideways part of the step and bounce off it
                    p.xform.m[12] -= mx; p.xform.m[14] -= mz;
                    p.vx *= -0.3f; p.vz *= -0.3f;
                }
            }
            if (hit) {
                float speed = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
                if (deepest > 0) {
                    p.xform.m[13] += deepest;
                    if (p.vy < -2.4f) { if (l != null) l.landed(p, cx, cy + deepest, cz, speed); p.vy *= -0.28f; }
                    else p.vy = 0;
                    p.vx *= 0.62f; p.vz *= 0.62f;
                }
                p.wx *= 0.55f; p.wy *= 0.55f; p.wz *= 0.55f;
                if (speed < 0.9f && wl < 2.2f) { p.quiet += dt; if (p.quiet > 0.3f) { p.rest = true; p.vx = p.vy = p.vz = p.wx = p.wy = p.wz = 0; if (l != null) l.landed(p, cx, cy, cz, 0); } }
                else p.quiet = 0;
            }
            if (p.xform.m[13] < -30) { pieces.remove(i--); }
        }
    }

    /** Rotation matrix about a unit axis. */
    static void axisAngle(Mat4 m, float x, float y, float z, float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a), t = 1 - c;
        float[] o = m.m;
        o[0] = t * x * x + c; o[1] = t * x * y + s * z; o[2] = t * x * z - s * y; o[3] = 0;
        o[4] = t * x * y - s * z; o[5] = t * y * y + c; o[6] = t * y * z + s * x; o[7] = 0;
        o[8] = t * x * z + s * y; o[9] = t * y * z - s * x; o[10] = t * z * z + c; o[11] = 0;
        o[12] = 0; o[13] = 0; o[14] = 0; o[15] = 1;
    }
}
