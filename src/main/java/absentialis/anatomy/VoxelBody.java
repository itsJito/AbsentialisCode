package absentialis.anatomy;

import static absentialis.anatomy.BodyMaterial.*;

import absentialis.character.CharModel;
import absentialis.character.Rig;
import absentialis.engine.util.Rng;
import absentialis.engine.voxel.VoxModel;

/**
 * A character's destructible anatomy: for every skeletal region (the twelve rig groups) a chunk of voxels, each
 * voxel knowing what it is made of, how much more it can take and what it currently looks like.
 *
 * The chunks share their grids with the character's visible model, so the anatomy is bound to the same bones the
 * animation moves: a hole made in the left forearm is a hole in that chunk and travels with the arm through every
 * animation and into the ragdoll. A body is created the first time a character is hit - until then they are drawn
 * from the shared, undamaged model.
 *
 * Storage is chunked by region and each chunk carries a version, so a hit dirties (and remeshes) only the region
 * it landed in.
 */
public final class VoxelBody {
    /** One region's voxels. Limb chunks hang from their top rows (the joint); the head sits on its bottom rows. */
    public static final class Chunk {
        public final int group, sx, sy, sz;
        public final byte[] mat;
        public final float[] hp;
        public final int[] color;
        /** Structural weight per row (y), as built and now: the ratio is that slice's integrity. */
        public final int[] rowStruct0, rowStruct, rowBone0, rowBone;
        public int solid0, solid;
        /** Bumped on every change; the mesh generator rebuilds a chunk whose version it has not seen. */
        public int version;
        /** False until the first voxel is destroyed or recoloured: untouched chunks draw from the shared model. */
        public boolean modified;
        /** Bleeding from this region right now (volume units per second), decaying as it clots. */
        public float bleed;
        public boolean fractured;

        Chunk(int group, int sx, int sy, int sz) {
            this.group = group; this.sx = sx; this.sy = sy; this.sz = sz;
            mat = new byte[sx * sy * sz]; hp = new float[sx * sy * sz]; color = new int[sx * sy * sz];
            rowStruct0 = new int[sy]; rowStruct = new int[sy]; rowBone0 = new int[sy]; rowBone = new int[sy];
        }

        public int idx(int x, int y, int z) { return (x * sy + y) * sz + z; }
        public boolean in(int x, int y, int z) { return x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz; }
        public byte at(int x, int y, int z) { return in(x, y, z) ? mat[idx(x, y, z)] : EMPTY; }
        public boolean empty() { return solid == 0; }
        public float integrity() { return solid0 == 0 ? 0 : solid / (float) solid0; }
        /** Integrity of one slice across the limb: 1 intact, 0 nothing left holding it. */
        public float rowIntegrity(int y) { return rowStruct0[y] == 0 ? 1 : rowStruct[y] / (float) rowStruct0[y]; }

        /** Destroys a voxel. Returns its material (EMPTY when there was nothing there). */
        public byte remove(int x, int y, int z) {
            if (!in(x, y, z)) return EMPTY;
            int i = idx(x, y, z);
            byte m = mat[i];
            if (m == EMPTY) return EMPTY;
            mat[i] = EMPTY; hp[i] = 0; color[i] = 0;
            solid--;
            rowStruct[y] -= STRUCTURAL[m];
            if (m == BONE) rowBone[y]--;
            version++; modified = true;
            return m;
        }

        public void recolor(int x, int y, int z, int argb) {
            if (!in(x, y, z)) return;
            int i = idx(x, y, z);
            if (mat[i] == EMPTY || color[i] == argb) return;
            color[i] = argb;
            version++; modified = true;
        }

        /** Voxels that are visible from outside: something solid with an open neighbour. */
        public boolean exposed(int x, int y, int z) {
            return at(x + 1, y, z) == EMPTY || at(x - 1, y, z) == EMPTY || at(x, y + 1, z) == EMPTY || at(x, y - 1, z) == EMPTY || at(x, y, z + 1) == EMPTY || at(x, y, z - 1) == EMPTY;
        }

        /** The chunk as a model for meshing (colours as they are now). */
        public VoxModel toModel() {
            VoxModel m = new VoxModel(sx, sy, sz);
            for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) { int i = idx(x, y, z); if (mat[i] != EMPTY) m.set(x, y, z, color[i]); }
            return m;
        }

        /** A copy holding only the voxels selected by the mask (detached pieces keep the damage they had). */
        Chunk extract(boolean[] mask) {
            Chunk c = new Chunk(group, sx, sy, sz);
            for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) {
                int i = idx(x, y, z);
                if (mat[i] == EMPTY || mask != null && !mask[i]) continue;
                c.mat[i] = mat[i]; c.hp[i] = hp[i]; c.color[i] = color[i];
                c.solid++; c.rowStruct[y] += STRUCTURAL[mat[i]];
                if (mat[i] == BONE) c.rowBone[y]++;
            }
            c.solid0 = c.solid;
            System.arraycopy(c.rowStruct, 0, c.rowStruct0, 0, sy);
            System.arraycopy(c.rowBone, 0, c.rowBone0, 0, sy);
            c.modified = true; c.version = 1;
            return c;
        }
    }

    public final Chunk[] chunks = new Chunk[Rig.GROUPS];
    public final boolean infected;
    public final int id;
    private static int nextId = 1;
    /** Total voxels destroyed on this body so far, and by material. */
    public int destroyed;
    public final int[] destroyedBy = new int[COUNT];

    private VoxelBody(boolean infected) { this.infected = infected; id = nextId++; }

    /** True for the regions that hang down from a joint at their top (arms and legs). */
    public static boolean limb(int g) { return g != Rig.HEAD && g != Rig.TORSO; }

    /**
     * Builds the layered anatomy under a character's visible model. skin is the model's skin tone (surface voxels
     * of that colour are skin, the rest clothing); vest and helmet put armour over the torso and the skull.
     */
    public static VoxelBody build(CharModel cm, int skin, boolean infected, boolean vest, boolean helmet, long seed) {
        VoxelBody b = new VoxelBody(infected);
        for (int g = 0; g < Rig.GROUPS; g++) {
            VoxModel src = cm.groups[g];
            Chunk c = new Chunk(g, src.sx, src.sy, src.sz);
            int[] depth = depth(src);
            float ax = Rig.ORIGIN[g][0], az = Rig.ORIGIN[g][2], py = Rig.ORIGIN[g][1];
            boolean limb = limb(g);
            for (int x = 0; x < c.sx; x++) for (int y = 0; y < c.sy; y++) for (int z = 0; z < c.sz; z++) {
                int col = src.get(x, y, z);
                if (col == 0) continue;
                int i = c.idx(x, y, z), d = depth[i];
                float h = Rng.hash(x + g * 31, y, z, (int) seed);
                float rx = x + 0.5f - ax, rz = z + 0.5f - az, r = (float) Math.sqrt(rx * rx + rz * rz);
                byte m;
                if (d == 0) {
                    boolean armoured = g == Rig.TORSO && vest && y >= 3 || g == Rig.HEAD && helmet && y >= py + 6;
                    m = armoured ? ARMOR : near(col, skin) ? SKIN : CLOTH;
                } else if (limb) {
                    // a bone down the axis, cartilage where the limb meets what it hangs from, flesh around both
                    boolean core = r < 1.05f;
                    m = core ? (y >= c.sy - 2 ? JOINT : BONE) : TISSUE;
                } else if (g == Rig.HEAD) {
                    // skull: a shell of bone one voxel under the skin, the brain inside, the neck's vertebrae below
                    m = y <= py + 1 ? (r < 1.3f ? JOINT : TISSUE) : d == 1 ? BONE : ORGAN;
                } else {
                    // torso: spine at the back, ribs as alternate rows of the first layer in the chest, organs within
                    boolean spine = Math.abs(rx) < 1.05f && z >= az + 1 && z <= az + 3;
                    boolean rib = d == 1 && y >= 6 && (y & 1) == 0;
                    m = spine || rib ? BONE : d >= 2 ? ORGAN : TISSUE;
                }
                c.mat[i] = m;
                c.hp[i] = DURABILITY[m] * (0.85f + 0.3f * h);
                c.color[i] = surface(m) ? col : BodyMaterial.exposed(m, infected, h, g == Rig.HEAD);
                c.solid++;
                c.rowStruct[y] += STRUCTURAL[m];
                if (m == BONE) c.rowBone[y]++;
            }
            c.solid0 = c.solid;
            System.arraycopy(c.rowStruct, 0, c.rowStruct0, 0, c.sy);
            System.arraycopy(c.rowBone, 0, c.rowBone0, 0, c.sy);
            b.chunks[g] = c;
        }
        return b;
    }

    private static boolean near(int a, int b) {
        int dr = ((a >> 16) & 255) - ((b >> 16) & 255), dg = ((a >> 8) & 255) - ((b >> 8) & 255), db = (a & 255) - (b & 255);
        return dr * dr + dg * dg + db * db < 46 * 46;
    }

    /** Steps from the outside to each solid voxel (0 = on the surface). */
    private static int[] depth(VoxModel m) {
        int sx = m.sx, sy = m.sy, sz = m.sz;
        int[] d = new int[sx * sy * sz];
        java.util.Arrays.fill(d, -1);
        boolean more = true;
        for (int pass = 0; more && pass < 8; pass++) {
            more = false;
            int[] next = d.clone();
            for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) {
                int i = (x * sy + y) * sz + z;
                if (m.get(x, y, z) == 0 || d[i] >= 0) continue;
                boolean open = false;
                int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                for (int[] n : nb) {
                    int nx = x + n[0], ny = y + n[1], nz = z + n[2];
                    boolean in = nx >= 0 && ny >= 0 && nz >= 0 && nx < sx && ny < sy && nz < sz;
                    if (pass == 0) { if (!in || m.get(nx, ny, nz) == 0) open = true; }
                    else if (in && d[(nx * sy + ny) * sz + nz] == pass - 1) open = true;
                }
                if (open) next[i] = pass; else more = true;
            }
            d = next;
        }
        for (int i = 0; i < d.length; i++) if (d[i] < 0) d[i] = 8;
        return d;
    }

    /** A body made of the given chunks only (a detached piece). */
    static VoxelBody piece(VoxelBody from) { return new VoxelBody(from.infected); }

    public float integrity() { int a = 0, b = 0; for (Chunk c : chunks) if (c != null) { a += c.solid; b += c.solid0; } return b == 0 ? 0 : a / (float) b; }
    public float bleeding() { float s = 0; for (Chunk c : chunks) if (c != null) s += c.bleed; return s; }
}
