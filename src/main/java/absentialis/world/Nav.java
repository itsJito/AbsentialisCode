package absentialis.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Navigation for every agent (v0.3). Two layers:
 *
 *   GRAPH  the authored waypoint graph in {@link MapData#nodes}: street nodes every 16 m, plus the
 *          door, yard and post nodes added by {@link #annotate}. Long trips are routed over it.
 *   GRID   A* over standable voxel cells (step up one block, drop up to three, never through
 *          hazards). Closed wooden doors are passable at a cost - the walker opens them.
 *
 * Searches are bounded and reuse their scratch arrays; {@link #budget} caps how many run per
 * simulation tick so a crowd changing its mind in the same frame cannot cause a spike.
 */
public final class Nav {
    private final World w;
    /** Path searches still allowed this tick (reset by the raid every update). */
    public int budget = 8;
    public int searches, expansions;   // counters for tests / the debug overlay

    public Nav(World w) { this.w = w; }

    // ------------------------------------------------------------------ walkability
    private boolean pass(int x, int y, int z, boolean doors) {
        int id = w.get(x, y, z);
        if (id == 0) return true;
        Block b = Block.ALL[id];
        if (b.solid()) return doors && b.is(Block.DOOR) && (w.doors == null || !w.doors.sealed(x, y, z));
        return !b.is(Block.HAZARD);
    }

    /** A body can stand with its feet at (x, y, z): floor below, two clear cells above it. */
    public boolean stand(int x, int y, int z, boolean doors) {
        if (y < 1 || y >= World.H - 2 || x < 1 || z < 1 || x >= World.W - 1 || z >= World.D - 1) return false;
        if (!w.solid(x, y - 1, z) || Block.ALL[w.get(x, y - 1, z)].is(Block.HAZARD)) return false;
        return pass(x, y, z, doors) && pass(x, y + 1, z, doors);
    }

    /** The standable height in a neighbouring column reachable from height y: level, one up, or a drop of up to three. */
    public int stepY(int x, int z, int y, boolean doors) {
        if (stand(x, y, z, doors)) return y;
        if (stand(x, y + 1, z, doors)) return y + 1;
        for (int d = 1; d <= 3; d++) { if (w.solid(x, y - d + 1, z)) break; if (stand(x, y - d, z, doors)) return y - d; }
        return -1;
    }

    /**
     * 1.0: {@link #clear} for something with shoulders. The line is clear down its middle and down both of its edges, r to
     * either side: a line that grazes the end of a wall, or goes through a doorway at a slant, is clear for a point and not
     * for a body - which then walked into the jamb and stood there. Steering uses this wherever it leaves the path.
     */
    public boolean clearBody(float sx, float sy, float sz, float tx, float tz, boolean doors, float r) {
        if (!clear(sx, sy, sz, tx, tz, doors)) return false;
        float dx = tx - sx, dz = tz - sz, len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 0.8f) return true;
        float px = -dz / len * r, pz = dx / len * r;
        return edge(sx + px, sy, sz + pz, tx + px, tz + pz, doors) && edge(sx - px, sy, sz - pz, tx - px, tz - pz, doors);
    }

    /** One edge of a body's sweep: every cell under it can be stood in at about the height the body is at (a wall or a drop stops it). */
    private boolean edge(float sx, float sy, float sz, float tx, float tz, boolean doors) {
        float dx = tx - sx, dz = tz - sz, len = (float) Math.sqrt(dx * dx + dz * dz);
        int y = Math.round(sy), steps = (int) (len / 0.4f) + 1;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = (int) Math.floor(sx + dx * t), z = (int) Math.floor(sz + dz * t);
            int ny = stepY(x, z, y, doors);
            if (ny < 0) { ny = stepY(x, z, y + 1, doors); if (ny < 0) return false; }
            if (ny > y && !stairUp(x, ny, z)) return false;
            y = ny;
        }
        return true;
    }

    /**
     * 1.1: a step up that is walked (a stair tread under the new footing) and not jumped. A straight line is "clear" only
     * if it can be walked: before, a line across a desk, a crate or a bed counted as clear because each is one block high
     * and can be stood on - so anybody crossing a furnished room went over the furniture, jumping on and off it. A jump
     * is now something only a planned route asks for ({@link #path}), where it costs {@link #JUMP_COST}.
     */
    private boolean stairUp(int x, int ny, int z) { return Block.ALL[w.get(x, ny - 1, z)].is(Block.STEP); }

    /** A cell a body has to go through squarely: something solid on both sides of it, at foot or head height (a doorway, a gap in a wall). */
    public boolean choke(int x, int y, int z) {
        return side(x - 1, y, z) && side(x + 1, y, z) || side(x, y, z - 1) && side(x, y, z + 1);
    }

    private boolean side(int x, int y, int z) { return blocks(x, y, z) || blocks(x, y + 1, z); }
    private boolean blocks(int x, int y, int z) { Block b = w.block(x, y, z); return b != null && b.solid() && !b.is(Block.DOOR); }

    /** True when a body can walk the straight line (no walls, hazards, drops over three or steps over one). */
    public boolean clear(float sx, float sy, float sz, float tx, float tz, boolean doors) {
        float dx = tx - sx, dz = tz - sz, len = (float) Math.sqrt(dx * dx + dz * dz);
        int y = Math.round(sy);
        int px = (int) Math.floor(sx), pz = (int) Math.floor(sz);
        if (!stand(px, y, pz, doors)) { y = stepY(px, pz, y, doors); if (y < 0) return false; }
        int steps = (int) (len / 0.45f) + 1;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            int x = (int) Math.floor(sx + dx * t), z = (int) Math.floor(sz + dz * t);
            if (x == px && z == pz) continue;
            // no squeezing diagonally between two wall corners
            if (x != px && z != pz && (stepY(px, z, y, doors) < 0 || stepY(x, pz, y, doors) < 0)) return false;
            int ny = stepY(x, z, y, doors);
            if (ny < 0) return false;
            if (ny > y && !stairUp(x, ny, z)) return false;
            if (doors == false && Block.ALL[w.get(x, ny, z)].is(Block.DOOR) && w.solid(x, ny, z)) return false;
            y = ny; px = x; pz = z;
        }
        return true;
    }

    // ------------------------------------------------------------------ grid A*
    private static final int CAP = 6000, SLOTS = 1 << 14;
    private final int[] nk = new int[CAP], parent = new int[CAP], heap = new int[CAP + 1], slot = new int[SLOTS], slotGen = new int[SLOTS];
    private final float[] ng = new float[CAP], nf = new float[CAP];
    private final boolean[] closed = new boolean[CAP];
    private int count, heapN, gen;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private static int key(int x, int y, int z) { return x | z << 10 | y << 20; }

    private int find(int k, boolean create) {
        int h = (k * 0x9E3779B1) >>> 18;
        while (true) {
            if (slotGen[h] != gen) {
                if (!create || count >= CAP) return -1;
                slotGen[h] = gen; slot[h] = count; nk[count] = k; ng[count] = Float.MAX_VALUE; closed[count] = false; parent[count] = -1;
                return count++;
            }
            if (nk[slot[h]] == k) return slot[h];
            h = (h + 1) & (SLOTS - 1);
        }
    }

    private void push(int n) {
        int i = ++heapN;
        heap[i] = n;
        while (i > 1 && nf[heap[i >> 1]] > nf[heap[i]]) { int t = heap[i]; heap[i] = heap[i >> 1]; heap[i >> 1] = t; i >>= 1; }
    }

    private int pop() {
        int top = heap[1];
        heap[1] = heap[heapN--];
        int i = 1;
        while (true) {
            int l = i << 1, r = l + 1, m = i;
            if (l <= heapN && nf[heap[l]] < nf[heap[m]]) m = l;
            if (r <= heapN && nf[heap[r]] < nf[heap[m]]) m = r;
            if (m == i) break;
            int t = heap[i]; heap[i] = heap[m]; heap[m] = t; i = m;
        }
        return top;
    }

    /**
     * Walkable path from a position toward a target column. Returns packed cells {x, y, z, ...} from the
     * first step to the goal, the closest reachable approach if the goal itself cannot be stood on, or
     * null when the walker is boxed in.
     */
    /**
     * 0.9: a route to a place on a particular floor. The plain form stops at the first cell it reaches in the goal's column,
     * whatever its height - so a body sent to a room upstairs "arrived" underneath it. With a height the goal is that column at
     * that level (give or take a step), and the best effort, when it cannot be reached, is the cell nearest to it in three dimensions.
     */
    public int[] path(float sx, float sy, float sz, float tx, float ty, float tz, int maxExpand, boolean doors) {
        goalY = Math.round(ty);
        try { return path(sx, sy, sz, tx, tz, maxExpand, doors); } finally { goalY = -1; }
    }

    private int goalY = -1;

    public int[] path(float sx, float sy, float sz, float tx, float tz, int maxExpand, boolean doors) {
        searches++;
        gen++;
        count = 0; heapN = 0;
        int x0 = (int) Math.floor(sx), z0 = (int) Math.floor(sz), y0 = Math.round(sy);
        boolean onLadder = climb(x0, y0, z0) || climb(x0, y0 - 1, z0);
        if (!stand(x0, y0, z0, doors) && !onLadder) { int y = stepY(x0, z0, y0, doors); if (y < 0) y = stepY(x0, z0, y0 + 1, doors); if (y < 0) return null; y0 = y; }
        int gx = (int) Math.floor(tx), gz = (int) Math.floor(tz);
        int s = find(key(x0, y0, z0), true);
        ng[s] = 0; nf[s] = h(x0, z0, gx, gz);
        push(s);
        int best = s;
        float bestH = nf[s] + (goalY < 0 ? 0 : Math.abs(y0 - goalY) * 2f);
        int done = 0;
        while (heapN > 0 && done < maxExpand) {
            int n = pop();
            if (closed[n]) continue;
            closed[n] = true;
            done++;
            int k = nk[n], x = k & 1023, z = (k >> 10) & 1023, y = k >> 20;
            float hh = h(x, z, gx, gz) + (goalY < 0 ? 0 : Math.abs(y - goalY) * 2f);
            if (hh < bestH) { bestH = hh; best = n; }
            if (x == gx && z == gz && (goalY < 0 || Math.abs(y - goalY) <= 1)) { best = n; break; }
            // ladders (0.6): a rung column is a vertical edge. Up while there is ladder at the feet, down while there is
            // ladder below them; a body on a ladder needs no floor, only room.
            if (climb(x, y, z) && pass(x, y + 1, z, doors) && pass(x, y + 2, z, doors)) relax(n, x, y + 1, z, LADDER_COST, gx, gz);
            if (climb(x, y - 1, z) && pass(x, y - 1, z, doors)) relax(n, x, y - 1, z, LADDER_COST * 0.7f, gx, gz);
            boolean hanging = !stand(x, y, z, doors);
            for (int d = 0; d < 8; d++) {
                int nx = x + DIRS[d][0], nz = z + DIRS[d][1];
                int ny = hanging ? (stand(nx, y, nz, doors) ? y : -1) : stepY(nx, nz, y, doors);
                // the head of a ladder beside a roof or a landing: no floor in that cell, but rungs to hold
                if (ny < 0 && d < 4 && climb(nx, y - 1, nz) && pass(nx, y, nz, doors) && pass(nx, y + 1, nz, doors)) ny = y;
                // 0.9: people pull themselves up. A ledge two or three blocks up, straight ahead, with room on it and nothing
                // overhead on the way: an edge of the graph for those with hands (the same callers that may open doors)
                boolean pullUp = false;
                if (ny < 0 && doors && mantling && d < 4 && !hanging) for (int up = 2; up <= 3 && ny < 0; up++) {
                    if (!stand(nx, y + up, nz, doors) || !w.solid(nx, y + up - 1, nz) || Block.ALL[w.get(nx, y + up - 1, nz)].shape != 0) continue;
                    boolean clear = true;
                    for (int h = 2; h <= up + 1 && clear; h++) clear = pass(x, y + h, z, doors);
                    if (clear) { ny = y + up; pullUp = true; }
                }
                if (ny < 0) continue;
                if (hanging && d >= 4) continue;
                if (d >= 4 && (stepY(x + DIRS[d][0], z, y, doors) < 0 || stepY(x, z + DIRS[d][1], y, doors) < 0)) continue;
                int m = find(key(nx, ny, nz), true);
                if (m < 0 || closed[m]) continue;
                // a step up is free on a stair and a deliberate jump anywhere else: routes prefer stairs to ledges
                float cost = (d >= 4 ? 1.414f : 1f) + (pullUp ? MANTLE_COST * (ny - y) : ny > y ? (Block.ALL[w.get(nx, ny - 1, nz)].is(Block.STEP) ? 0.6f : JUMP_COST) : ny < y ? 0.3f * (y - ny) : 0);
                int here = w.get(nx, ny, nz);
                if (here != 0 && Block.ALL[here].is(Block.DOOR) && Block.ALL[here].solid()) cost += 5;   // opening a door takes a moment
                else if (here != 0 && Block.ALL[here].is(Block.LIQUID)) cost += 2.5f; // wading is slow and loud
                float g = ng[n] + cost;
                if (g < ng[m]) { ng[m] = g; nf[m] = g + h(nx, nz, gx, gz); parent[m] = n; push(m); }
            }
        }
        expansions += done;
        if (best == s) return null;
        int len = 0;
        for (int n = best; n != s; n = parent[n]) len++;
        int[] out = new int[len * 3];
        int i = len;
        for (int n = best; n != s; n = parent[n]) { i--; int k = nk[n]; out[i * 3] = k & 1023; out[i * 3 + 1] = k >> 20; out[i * 3 + 2] = (k >> 10) & 1023; }
        return out;
    }

    /** Extra cost of a one-block ledge that has to be jumped, and of each rung of a ladder (tunable). */
    public static float JUMP_COST = 7f, LADDER_COST = 1.5f;
    /** 0.9: the cost of each block of a pull-up (a route takes stairs or a ladder when one is near, a wall when it is not), and whether routes may use them at all. */
    public static float MANTLE_COST = 3.2f;
    public boolean mantling = true;

    /** A climbable, non-solid cell (a ladder): something to hold, nothing to stand on. */
    public boolean climb(int x, int y, int z) {
        int id = w.get(x, y, z);
        return id != 0 && Block.ALL[id].is(Block.CLIMB) && !Block.ALL[id].solid();
    }

    private void relax(int from, int x, int y, int z, float cost, int gx, int gz) {
        int m = find(key(x, y, z), true);
        if (m < 0 || closed[m]) return;
        float g = ng[from] + cost;
        if (g < ng[m]) { ng[m] = g; nf[m] = g + h(x, z, gx, gz); parent[m] = from; push(m); }
    }

    private static float h(int x, int z, int gx, int gz) { int dx = Math.abs(x - gx), dz = Math.abs(z - gz); return Math.max(dx, dz) + 0.414f * Math.min(dx, dz); }

    // ------------------------------------------------------------------ graph routing
    private float[] dist = new float[0];
    private int[] prev = new int[0];

    /**
     * Next stop for a long trip: the furthest node on the graph route from start to target that is still
     * within 'reach' of the start. Null when both ends share a node (walk straight) or the graph is split.
     */
    public float[] via(float sx, float sz, float tx, float tz, float reach) {
        MapData m = w.map;
        MapData.Node a = m.nearestNode(sx, sz), b = m.nearestNode(tx, tz);
        if (a == null || b == null || a == b) return null;
        int n = m.nodes.size();
        if (dist.length < n) { dist = new float[n]; prev = new int[n]; }
        java.util.Arrays.fill(dist, 0, n, Float.MAX_VALUE);
        java.util.PriorityQueue<int[]> pq = new java.util.PriorityQueue<>((p, q) -> Float.compare(Float.intBitsToFloat(p[1]), Float.intBitsToFloat(q[1])));
        dist[a.id] = 0; prev[a.id] = -1;
        pq.add(new int[]{a.id, Float.floatToIntBits(0)});
        while (!pq.isEmpty()) {
            int[] e = pq.poll();
            int u = e[0];
            if (Float.intBitsToFloat(e[1]) > dist[u]) continue;
            if (u == b.id) break;
            MapData.Node nu = m.nodes.get(u);
            for (int v : nu.links) {
                MapData.Node nv = m.nodes.get(v);
                float d = dist[u] + (float) Math.hypot(nu.x - nv.x, nu.z - nv.z);
                if (d < dist[v]) { dist[v] = d; prev[v] = u; pq.add(new int[]{v, Float.floatToIntBits(d)}); }
            }
        }
        if (dist[b.id] == Float.MAX_VALUE) return null;
        List<MapData.Node> route = new ArrayList<>();
        for (int u = b.id; u != -1; u = prev[u]) route.add(0, m.nodes.get(u));
        MapData.Node pick = route.size() > 1 ? route.get(1) : route.get(0);
        for (int i = 1; i < route.size(); i++) {
            MapData.Node c = route.get(i);
            if (Math.hypot(c.x - sx, c.z - sz) <= reach) pick = c; else break;
        }
        // standing well short of the first node: go there first
        if (Math.hypot(a.x - sx, a.z - sz) > reach * 0.8f) pick = a;
        return new float[]{pick.x + 0.5f, pick.z + 0.5f};
    }

    /** Whole graph route as node ids (strategic travel and tests); empty when unreachable. */
    public List<MapData.Node> route(float sx, float sz, float tx, float tz) {
        List<MapData.Node> out = new ArrayList<>();
        float[] v = via(sx, sz, tx, tz, 1e9f);
        if (v == null) return out;
        MapData m = w.map;
        MapData.Node b = m.nearestNode(tx, tz);
        for (int u = b.id; u != -1; u = prev[u]) out.add(0, m.nodes.get(u));
        return out;
    }

    // ------------------------------------------------------------------ graph enrichment
    /**
     * Adds the nodes the street survey cannot see: one outside every ground-floor doorway, one in each
     * yard or lot far from a street node, and one at every faction post and nest. Each is linked to its
     * nearest neighbours so routes can leave the carriageway. Returns the number of nodes added.
     */
    public static int annotate(World w) {
        Nav nav = new Nav(w);
        MapData m = w.map;
        int before = m.nodes.size(), G = World.GROUND;
        for (int x = World.CITY_MIN + 2; x < World.CITY_MAX - 2; x++) for (int z = World.CITY_MIN + 2; z < World.CITY_MAX - 2; z++) {
            if (!Block.ALL[w.get(x, G, z)].is(Block.DOOR)) continue;
            for (int[] d : DIRS) {
                if (d[0] != 0 && d[1] != 0) continue;
                int ox = x + d[0] * 2, oz = z + d[1] * 2;
                if (nav.stand(ox, G, oz, false) && w.heightAt(ox, oz) < G) { add(w, nav, ox, G, oz, "door", 44); break; }
            }
        }
        for (int x = World.CITY_MIN + 10; x < World.CITY_MAX - 10; x += 18) for (int z = World.CITY_MIN + 10; z < World.CITY_MAX - 10; z += 18) {
            // nudge the sample to a standable outdoor cell
            for (int k = 0; k < 9; k++) {
                int sx = x + (k % 3 - 1) * 4, sz = z + (k / 3 - 1) * 4;
                if (!nav.stand(sx, G, sz, false) || w.heightAt(sx, sz) >= G) continue;
                MapData.Node near = m.nearestNode(sx, sz);
                if (near == null || Math.hypot(near.x - sx, near.z - sz) > 13) add(w, nav, sx, G, sz, "yard", 40);
                break;
            }
        }
        List<MapData.Point> spots = new ArrayList<>(m.posts);
        spots.addAll(m.nests);
        for (MapData.Point p : spots) {
            if (Math.abs(p.y - G) > 1) continue;
            int y = nav.stepY(p.x, p.z, G, false);
            if (y >= 0) add(w, nav, p.x, y, p.z, p.kind.equals("nest") ? "nest" : "post", 48);
        }
        // nothing is left stranded: an isolated node (a dead-end stub, a walled yard) joins its nearest neighbour
        for (MapData.Node n : m.nodes) {
            if (!n.links.isEmpty()) continue;
            MapData.Node best = null;
            float bd = 160 * 160;
            for (MapData.Node o : m.nodes) { if (o == n) continue; float d = (o.x - n.x) * (o.x - n.x) + (o.z - n.z) * (o.z - n.z); if (d < bd) { bd = d; best = o; } }
            if (best != null) m.link(n, best);
        }
        return m.nodes.size() - before;
    }

    private static void add(World w, Nav nav, int x, int y, int z, String kind, float linkRange) {
        MapData m = w.map;
        int had = m.nodes.size();
        MapData.Node n = m.addNode(x, y, z, w.district(x, z));
        if (m.nodes.size() == had) return;          // merged into an existing node
        n.kind = kind;
        // link to the two nearest nodes; prefer ones a walker can actually reach in a straight line
        MapData.Node b1 = null, b2 = null;
        float d1 = linkRange, d2 = linkRange;
        for (MapData.Node o : m.nodes) {
            if (o == n) continue;
            float d = (float) Math.hypot(o.x - x, o.z - z);
            if (d >= d2) continue;
            if (!nav.clear(x + 0.5f, y, z + 0.5f, o.x + 0.5f, o.z + 0.5f, true)) d += linkRange * 0.5f;
            if (d < d1) { d2 = d1; b2 = b1; d1 = d; b1 = o; } else if (d < d2) { d2 = d; b2 = o; }
        }
        if (b1 != null) m.link(n, b1);
        if (b2 != null) m.link(n, b2);
    }
}
