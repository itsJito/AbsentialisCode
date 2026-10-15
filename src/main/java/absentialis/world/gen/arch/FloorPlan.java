package absentialis.world.gen.arch;

import absentialis.world.gen.GenDefs.Archetype;
import absentialis.world.gen.GenDefs.RoomDef;
import java.util.ArrayList;
import java.util.List;

/**
 * Room graph first, geometry second.
 *
 * A floor's programme (a list of rooms and how each must be reached) becomes a graph: every room is a node, every
 * required doorway an edge - to the circulation room by default, to the previous room for an ensuite or a pantry,
 * to both for a kitchen that also opens on the dining room. The layout below is derived from that graph, not the
 * other way round: the circulation node is placed first as a corridor (or, in an open plan, as the hall), and each
 * other node is placed against the node its edge points at, so every edge has a shared wall to put its doorway in.
 * Nothing here writes voxels; {@link Architect} builds from the result and the validator walks it afterwards.
 *
 * Corridor coordinates: {@code p} runs along the corridor, {@code q} across it. A {@link Core} fixes, for the
 * whole building, which way the corridor runs, where it lies and where the stairwell stands, so storeys stack.
 */
public final class FloorPlan {
    public static final int WALL = -1, STAIR = -2;

    public static final class PRoom {
        public int id, p0, p1, q0, q1;
        public String type;
        public boolean circulation;
        public RoomDef def;
    }

    /** A doorway on a wall cell (p, q) between two rooms; open = an arch with no leaf. */
    public static final class PEdge {
        public int ra, rb, p, q;
        public boolean open;
        PEdge(int ra, int rb, int p, int q, boolean open) { this.ra = ra; this.rb = rb; this.p = p; this.q = q; this.open = open; }
    }

    /** What is fixed for every storey of one building. */
    public static final class Core {
        /** Interior size (footprint without the exterior walls) along the front and front-to-back. */
        public int IA, IB;
        /** Corridor runs back from the front door (true) or along the front (false). */
        public boolean perp;
        public boolean chain, twoBands, stairs;
        public int P, Q, cw, cq;
        /** Stairwell: first p of its six cells; lanes at q = cq+cw (on the corridor's wall line) and cq+cw+1. */
        public int sp0;
        /** Entry stub through the front band (corridor along the front, two bands): first p and width. */
        public int stubP, stubW = 2;

        public int ia(int p, int q) { return perp ? q : p; }
        public int ib(int p, int q) { return perp ? p : q; }
        public int laneQ(int lane) { return lane == 1 ? cq + cw : cq + cw + 1; }
        public int band1Q0() { return cq + cw + 1; }

        /** Decides the core for a footprint. Returns the number of storeys it can actually serve. */
        public int decide(int ia, int ib, Archetype a, int floors, boolean roofStairs) {
            IA = ia; IB = ib;
            cw = Math.max(1, a.corridor);
            chain = "chain".equals(a.plan);
            boolean wantPerp = "perp".equals(a.orient) || "auto".equals(a.orient) && ib > ia + 1;
            boolean needStairs = floors > 1 || roofStairs;
            if (!chain) {
                boolean ok = false;
                // the preferred orientation first, then the other, then a narrower corridor
                for (int attempt = 0; attempt < 4 && !ok; attempt++) {
                    perp = (attempt & 1) == 0 ? wantPerp : !wantPerp;
                    if (attempt >= 2) cw = 1;
                    P = perp ? ib : ia; Q = perp ? ia : ib;
                    ok = Q >= cw + 4 && (!needStairs || P >= 10) && P >= 4;
                }
                if (!ok) {
                    // too small for stairs: try again as a single storey before giving up the corridor
                    cw = Math.max(1, a.corridor);
                    for (int attempt = 0; attempt < 4 && !ok; attempt++) {
                        perp = (attempt & 1) == 0 ? wantPerp : !wantPerp;
                        if (attempt >= 2) cw = 1;
                        P = perp ? ib : ia; Q = perp ? ia : ib;
                        ok = Q >= cw + 4 && P >= 4;
                    }
                    floors = 1; needStairs = false;
                    if (!ok) chain = true;
                }
            }
            if (chain) {
                perp = true; P = ib; Q = ia; cw = 0; cq = 0; twoBands = false; stairs = false;
                return floors;
            }
            twoBands = Q >= cw + 8;
            boolean open = "open".equals(a.plan);
            if (twoBands) {
                if (open && floors == 1) { int rd = Math.max(3, Math.min(5, Q / 4)); cq = Q - rd - cw - 1; }
                else cq = 1 + (Q - cw - 1) / 2;
                if (cq < 4) cq = 4;
                if (Q - cq - cw - 1 < 3) twoBands = false;
            }
            if (!twoBands) cq = 0;
            stairs = needStairs;
            sp0 = P - 6;
            stubP = P >= 12 ? Math.max(4, Math.min(P - stubW - 4, (P - stubW) / 2)) : 0;
            return floors;
        }
    }

    public final Core core;
    /** [p][q]: room id, WALL or STAIR. */
    public final int[][] cell;
    public final List<PRoom> rooms = new ArrayList<>();
    public final List<PEdge> edges = new ArrayList<>();
    /** Rooms of the programme that did not fit and were left out (reported, never silently squeezed). */
    public int dropped;

    private FloorPlan(Core c) { core = c; cell = new int[c.P][c.Q]; for (int[] row : cell) java.util.Arrays.fill(row, WALL); }

    public int at(int ia, int ib) {
        int p = core.perp ? ib : ia, q = core.perp ? ia : ib;
        return p < 0 || q < 0 || p >= core.P || q >= core.Q ? WALL : cell[p][q];
    }

    private static final class Seg {
        final int band, p0, p1;
        final List<RoomDef> defs = new ArrayList<>();
        int used;
        Seg(int band, int p0, int p1) { this.band = band; this.p0 = p0; this.p1 = p1; }
        int len() { return p1 - p0 + 1; }
        int need(RoomDef d) { return Math.max(2, d.min) + (defs.isEmpty() ? 0 : 1); }
        boolean fits(RoomDef d) { return used + need(d) <= len(); }
        void add(RoomDef d) { used += need(d); defs.add(d); }
    }

    private PRoom room(String type, int p0, int p1, int q0, int q1, RoomDef def) {
        PRoom r = new PRoom();
        r.id = rooms.size(); r.type = type; r.p0 = p0; r.p1 = p1; r.q0 = q0; r.q1 = q1; r.def = def;
        rooms.add(r);
        for (int p = p0; p <= p1; p++) for (int q = q0; q <= q1; q++) cell[p][q] = r.id;
        return r;
    }

    /**
     * Lays out one storey. {@code open}: the first room of the programme is a hall that takes the front band and
     * the corridor (shop floor, warehouse, barn); the remaining rooms line its back wall.
     */
    public static FloorPlan plan(Core c, List<RoomDef> defs, boolean open, String corridorType, String filler) {
        FloorPlan f = new FloorPlan(c);
        if (c.chain) { f.chain(defs); return f; }
        if (open && !c.twoBands && !c.stairs && !defs.isEmpty()) {
            // no depth for back rooms: the hall is the whole floor
            f.room(defs.get(0).type, 0, c.P - 1, 0, c.Q - 1, defs.get(0));
            f.dropped = defs.size() - 1;
            return f;
        }
        int band0q1 = c.cq - 2, wall0 = c.cq - 1, wall1 = c.cq + c.cw, band1q0 = c.cq + c.cw + 1;
        // ---- circulation node
        PRoom hall;
        int first = 0;
        if (open && !defs.isEmpty()) { hall = f.room(defs.get(0).type, 0, c.P - 1, 0, c.cq + c.cw - 1, defs.get(0)); first = 1; }
        else hall = f.room(corridorType, 0, c.P - 1, c.cq, c.cq + c.cw - 1, null);
        hall.circulation = !open;
        // ---- stairwell (the same cells on every storey)
        int band1p1 = c.P - 1;
        if (c.stairs) {
            for (int p = c.sp0; p < c.P; p++) { f.cell[p][wall1] = STAIR; if (band1q0 < c.Q) f.cell[p][band1q0] = STAIR; }
            band1p1 = c.sp0 - 2;
        }
        // ---- segments rooms can stand in
        List<Seg> segs = new ArrayList<>();
        if (c.twoBands && !open) {
            if (c.perp) segs.add(new Seg(0, 0, c.P - 1));
            else {
                // the entry stub joins the front door to the corridor; rooms stand either side of it
                for (int p = c.stubP; p < c.stubP + c.stubW; p++) for (int q = 0; q <= wall0; q++) f.cell[p][q] = hall.id;
                if (c.stubP - 2 >= 1) segs.add(new Seg(0, 0, c.stubP - 2));
                if (c.P - 1 - (c.stubP + c.stubW + 1) >= 1) segs.add(new Seg(0, c.stubP + c.stubW + 1, c.P - 1));
            }
        }
        if (band1q0 < c.Q && band1p1 >= 1) segs.add(new Seg(1, 0, band1p1));
        // ---- graph order -> segments: a linked room stays beside the room it is linked to
        Seg last = null;
        for (int i = first; i < defs.size(); i++) {
            RoomDef d = defs.get(i);
            Seg pick = null;
            if (!d.link.isEmpty()) { if (last != null && last.fits(d)) pick = last; }
            else {
                int room = -1;
                for (Seg s : segs) {
                    if (d.band >= 0 && s.band != d.band || !s.fits(d)) continue;
                    int left = s.len() - s.used;
                    if (left > room) { room = left; pick = s; }
                }
                if (pick == null && d.band >= 0) for (Seg s : segs) if (s.fits(d) && (pick == null || s.len() - s.used > pick.len() - pick.used)) pick = s;
            }
            if (pick == null) { f.dropped++; continue; }
            pick.add(d);
            last = pick;
        }
        // ---- cells
        PRoom lastBand1 = null;
        for (Seg s : segs) {
            int q0 = s.band == 0 ? 0 : band1q0, q1 = s.band == 0 ? band0q1 : c.Q - 1, wallQ = s.band == 0 ? wall0 : wall1;
            if (s.defs.isEmpty()) {
                // nothing asked for this stretch: it becomes a store room rather than a sealed void
                PRoom r = f.room(filler, s.p0, s.p1, q0, q1, null);
                f.edges.add(new PEdge(hall.id, r.id, (s.p0 + s.p1) / 2, wallQ, false));
                if (s.band == 1) lastBand1 = r;
                continue;
            }
            int n = s.defs.size(), spare = s.len() - s.used;
            float total = 0;
            for (RoomDef d : s.defs) total += d.weight;
            int p = s.p0, given = 0;
            PRoom prev = null;
            for (int i = 0; i < n; i++) {
                RoomDef d = s.defs.get(i);
                int extra = i == n - 1 ? spare - given : (int) Math.floor(spare * d.weight / total);
                given += extra;
                int len = Math.max(2, d.min) + extra;
                if (i > 0) p++;                      // the partition between this room and the last
                PRoom r = f.room(d.type, p, p + len - 1, q0, q1, d);
                boolean toHall = !"prev".equals(d.link), toPrev = !d.link.isEmpty() && prev != null;
                if (!toPrev && !toHall) toHall = true;
                if (toHall) f.edges.add(new PEdge(hall.id, r.id, doorP(r, c), wallQ, openArch(d.type)));
                if (toPrev) f.edges.add(new PEdge(prev.id, r.id, p - 1, s.band == 0 ? q1 : q0 + Math.min(1, q1 - q0), false));
                p += len;
                prev = r;
                if (s.band == 1) lastBand1 = r;
            }
        }
        // ---- the space behind the stairwell: a closet off the last back room when it is big enough to be one
        if (c.stairs && c.Q - 1 >= band1q0 + 3 && lastBand1 != null && lastBand1.p1 == c.sp0 - 2) {
            PRoom closet = f.room("closet", c.sp0, c.P - 1, band1q0 + 2, c.Q - 1, null);
            f.edges.add(new PEdge(lastBand1.id, closet.id, c.sp0 - 1, c.Q - 1, false));
        }
        return f;
    }

    /** A doorway onto the corridor: near the middle of the room, never on the stairwell or the entry stub. */
    private static int doorP(PRoom r, Core c) { return (r.p0 + r.p1) / 2; }

    private static boolean openArch(String type) {
        return switch (type) { case "living", "dining", "lobby", "foyer", "lounge", "mess", "concourse", "cafe", "mailroom", "breakroom", "frontdesk", "visiting" -> true; default -> false; };
    }

    /** Rooms in series, front to back, each opening into the next (footprints too small for a corridor). */
    private void chain(List<RoomDef> defs) {
        Core c = core;
        List<RoomDef> fit = new ArrayList<>();
        int used = 0;
        for (RoomDef d : defs) { int need = Math.max(2, d.min) + (fit.isEmpty() ? 0 : 1); if (used + need <= c.P) { fit.add(d); used += need; } else dropped++; }
        if (fit.isEmpty()) { RoomDef d = new RoomDef(); d.type = defs.isEmpty() ? "storage" : defs.get(0).type; d.min = Math.max(1, c.P); fit.add(d); used = Math.max(1, c.P); dropped = Math.max(0, defs.size() - 1); }
        int spare = c.P - used, p = 0;
        PRoom prev = null;
        for (int i = 0; i < fit.size(); i++) {
            RoomDef d = fit.get(i);
            int len = Math.max(i == 0 && fit.size() == 1 ? 1 : 2, d.min) + (i == 0 ? spare : 0);
            len = Math.min(len, c.P - p);
            if (i > 0) p++;
            PRoom r = room(d.type, p, p + len - 1, 0, c.Q - 1, d);
            if (prev != null) edges.add(new PEdge(prev.id, r.id, p - 1, c.Q / 2, false));
            p += len;
            prev = r;
        }
    }
}
