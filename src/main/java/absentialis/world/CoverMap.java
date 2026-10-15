package absentialis.world;

import java.util.List;

/**
 * Cover metadata derived from the voxels (0.5, brief section 4).
 *
 * A cover point is a cell a body can stand in with something solid directly beside it. For each one the map
 * stores where it is, which way the obstacle lies (the direction it protects against - the surface normal is the
 * opposite), how tall the obstacle is (low: crouch behind it and fire over it; high: stand behind it), whether
 * the obstacle ends to the left or right so a body can lean out round it, how much punishment the obstacle will
 * take, and who is using the point.
 *
 * Points are built per 16 x 16 column chunk the first time someone asks about that chunk, and a chunk is thrown
 * away when a block in it (or on its edge) changes - a wall shot out, a door blown off, a barricade built. Nothing
 * is recomputed for parts of the world nobody is fighting in, and a change never touches more than four chunks.
 * A point whose chunk has been rebuilt reports {@link Point#valid} false, which is how whoever was using it
 * learns its cover is gone.
 */
public final class CoverMap {
    public static final int LOW = 1, HIGH = 2;

    public static final class Point {
        public final int x, y, z;
        /** Direction from the point to the obstacle: fire arriving from within about 60 degrees of it is stopped. */
        public final int dx, dz;
        public final int height;
        /** Left and right as seen facing the obstacle: the obstacle ends there and the ground beside is free. */
        public final boolean leanLeft, leanRight;
        /** 0 .. 1: how well the obstacle stops rounds (material resistance and remaining strength). */
        public final float integrity;
        public int occupant;
        public float occupiedUntil;
        public boolean valid = true;

        Point(int x, int y, int z, int dx, int dz, int height, boolean ll, boolean lr, float integrity) {
            this.x = x; this.y = y; this.z = z; this.dx = dx; this.dz = dz; this.height = height; this.leanLeft = ll; this.leanRight = lr; this.integrity = integrity;
        }
        public float cx() { return x + 0.5f; }
        public float cz() { return z + 0.5f; }
        /** Stances the point supports: a low obstacle hides a crouched body only; a high one hides a standing body too. */
        public boolean hidesStanding() { return height == HIGH; }
        /** A body here can bring a weapon to bear: over a low obstacle, or round the end of a high one. */
        public boolean canFire() { return height == LOW || leanLeft || leanRight; }
        public boolean freeFor(int actorId, float now) { return occupant == 0 || occupant == actorId || now > occupiedUntil; }
        /** Cosine between the protected direction and the direction to a threat (1 = the obstacle is squarely between). */
        public float facing(float tx, float tz) {
            float vx = tx - cx(), vz = tz - cz(), l = (float) Math.sqrt(vx * vx + vz * vz);
            return l < 0.01f ? 0 : (vx * dx + vz * dz) / l;
        }
        /** Left of the obstacle direction, as a unit step. */
        public int leftX() { return dz; }
        public int leftZ() { return -dx; }
    }

    private static final int CX = World.W >> 4, CZ = World.D >> 4;
    private final World w;
    private final Point[][] chunks = new Point[CX * CZ][];
    /** Counters for the gates and the overlay. */
    public int built, rebuilt, points;

    public CoverMap(World w) { this.w = w; }

    /** A block changed: the chunk it is in, and any chunk it borders, will be rebuilt when next asked for. */
    public void invalidate(int x, int z) {
        drop(x >> 4, z >> 4);
        if ((x & 15) == 0) drop((x >> 4) - 1, z >> 4);
        if ((x & 15) == 15) drop((x >> 4) + 1, z >> 4);
        if ((z & 15) == 0) drop(x >> 4, (z >> 4) - 1);
        if ((z & 15) == 15) drop(x >> 4, (z >> 4) + 1);
    }

    private void drop(int cx, int cz) {
        if (cx < 0 || cz < 0 || cx >= CX || cz >= CZ) return;
        Point[] old = chunks[cx * CZ + cz];
        if (old == null) return;
        for (Point p : old) p.valid = false;
        points -= old.length;
        chunks[cx * CZ + cz] = null;
        rebuilt++;
    }

    public void clear() { for (int i = 0; i < chunks.length; i++) if (chunks[i] != null) { for (Point p : chunks[i]) p.valid = false; chunks[i] = null; } points = 0; }

    private boolean walk(int x, int y, int z) { return w.solid(x, y - 1, z) && !w.solid(x, y, z) && !w.solid(x, y + 1, z); }

    /** A block that stops a round well enough to be worth standing behind. */
    private static boolean stops(Block b) { return b != null && b.solid() && !b.is(Block.DOOR) && (b.opaque() || b.mat.resist >= 0.4f) && b.mat != Material.FOLIAGE && b.mat != Material.GLASS; }

    private float strength(int x, int y, int z) {
        Block b = w.block(x, y, z);
        float res = Math.min(1f, b.mat.resist / 6f);
        if (b.hp <= 0) return Math.max(0.35f, res);
        float left = 1f - w.blockDamage.getOrDefault(World.idx(x, y, z), 0) / (float) b.hp;
        return Math.max(0.05f, Math.min(1f, (0.25f + res * 0.75f) * left * Math.min(1f, b.hp / 400f + 0.4f)));
    }

    private Point[] chunk(int cx, int cz) {
        Point[] got = chunks[cx * CZ + cz];
        if (got != null) return got;
        java.util.ArrayList<Point> out = new java.util.ArrayList<>();
        int x0 = cx << 4, z0 = cz << 4;
        for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) {
            if (x < 1 || z < 1 || x >= World.W - 1 || z >= World.D - 1) continue;
            int top = Math.min(World.H - 3, w.heightAt(x, z) + 1);
            for (int y = 2; y <= top; y++) {
                if (!walk(x, y, z)) continue;
                for (int d = 0; d < 4; d++) {
                    int dx = d == 0 ? 1 : d == 1 ? -1 : 0, dz = d == 2 ? 1 : d == 3 ? -1 : 0;
                    Block lo = w.block(x + dx, y, z + dz);
                    if (!stops(lo)) continue;
                    boolean high = stops(w.block(x + dx, y + 1, z + dz));
                    int lx = dz, lz = -dx;      // left of the obstacle direction
                    boolean ll = !stops(w.block(x + dx + lx, y + 1, z + dz + lz)) && !w.solid(x + dx + lx, y, z + dz + lz) && walk(x + lx, y, z + lz);
                    boolean lr = !stops(w.block(x + dx - lx, y + 1, z + dz - lz)) && !w.solid(x + dx - lx, y, z + dz - lz) && walk(x - lx, y, z - lz);
                    float s = strength(x + dx, y, z + dz);
                    if (high) s = Math.min(s, strength(x + dx, y + 1, z + dz) + 0.15f);
                    out.add(new Point(x, y, z, dx, dz, high ? HIGH : LOW, ll, lr, s));
                }
            }
        }
        got = out.toArray(new Point[0]);
        chunks[cx * CZ + cz] = got;
        built++;
        points += got.length;
        return got;
    }

    /** Every cover point within r of a position and two metres of its height, appended to out. */
    public List<Point> near(float x, float y, float z, float r, List<Point> out) {
        int c0 = Math.max(0, (int) (x - r) >> 4), c1 = Math.min(CX - 1, (int) (x + r) >> 4), d0 = Math.max(0, (int) (z - r) >> 4), d1 = Math.min(CZ - 1, (int) (z + r) >> 4);
        float r2 = r * r;
        for (int cx = c0; cx <= c1; cx++) for (int cz = d0; cz <= d1; cz++) for (Point p : chunk(cx, cz)) {
            float ddx = p.cx() - x, ddz = p.cz() - z;
            if (ddx * ddx + ddz * ddz <= r2 && Math.abs(p.y - y) <= 2.2f) out.add(p);
        }
        return out;
    }

    /** The cover point at a cell that best faces a threat, or null. */
    public Point at(int x, int y, int z, float tx, float tz) {
        if (x < 0 || z < 0 || x >= World.W || z >= World.D) return null;
        Point best = null;
        float bf = -2;
        for (Point p : chunk(x >> 4, z >> 4)) if (p.x == x && p.z == z && Math.abs(p.y - y) <= 1) { float f = p.facing(tx, tz); if (f > bf) { bf = f; best = p; } }
        return best;
    }

    /** The cover a body standing at a position is pressed against, if any: the nearest obstacle within reach, facing where it looks. */
    public Point touching(float px, float py, float pz, float faceX, float faceZ) {
        int x = (int) Math.floor(px), y = Math.round(py), z = (int) Math.floor(pz);
        if (x < 0 || z < 0 || x >= World.W || z >= World.D) return null;
        Point best = null;
        float bs = 0.15f;
        for (Point p : chunk(x >> 4, z >> 4)) {
            if (p.x != x || p.z != z || Math.abs(p.y - y) > 1) continue;
            // distance from the body to the face of the obstacle
            float gap = p.dx != 0 ? (p.dx > 0 ? x + 1 - px : px - x) : (p.dz > 0 ? z + 1 - pz : pz - z);
            if (gap > 0.62f) continue;
            float s = (faceX * p.dx + faceZ * p.dz) * 0.5f + (0.62f - gap);
            if (s > bs) { bs = s; best = p; }
        }
        return best;
    }

    public void claim(Point p, int actorId, float until) { if (p != null) { p.occupant = actorId; p.occupiedUntil = until; } }
    public void release(Point p, int actorId) { if (p != null && p.occupant == actorId) p.occupant = 0; }
}
