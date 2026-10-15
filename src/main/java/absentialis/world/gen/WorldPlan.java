package absentialis.world.gen;

import absentialis.engine.util.Noise;
import absentialis.engine.util.Rng;
import absentialis.world.District;
import absentialis.world.World;
import absentialis.world.gen.GenDefs.Archetype;
import absentialis.world.gen.GenDefs.DistrictDef;
import absentialis.world.gen.GenDefs.LandmarkDef;
import absentialis.world.gen.GenDefs.Support;
import absentialis.world.gen.Streets.Rect;
import absentialis.world.gen.Streets.Style;
import java.util.ArrayList;
import java.util.List;

/**
 * The layout of the Zone as data (0.5), computed before a single voxel is written and kept on the map afterwards.
 *
 * Pipeline stages 2 to 7 of the brief happen here, in order and each constrained by the ones before it:
 * district regions are allocated from terrain suitability; arterial roads are laid on the region boundaries and
 * reserved; the two landmark lots of every district are reserved with their frontage; secondary streets are
 * threaded round the landmarks, never through them; what is left becomes blocks, blocks become lots, and every
 * lot is given a building archetype whose footprint fits inside the lot's setbacks and whose front faces the
 * street the lot stands on. Nothing downstream is allowed to move a road to make room for a building.
 *
 * Geometry: Downtown takes the central square; the four outer districts take four congruent slots arranged as a
 * pinwheel round it, so each outer district touches the ring road, two arterials and two neighbours. A slot is
 * planned in its own (u, v) frame - u along its length, v from the wall inwards - and the same code plans all four.
 */
public final class WorldPlan {
    public static final int RES_ROAD = 1, RES_ALLEY = 2, RES_LANDMARK = 3, RES_LOT = 4;

    /** A district's ground: slot 0 north, 1 east, 2 south, 3 west, -1 the centre. */
    public static final class Region {
        public District district;
        public DistrictDef def;
        public Rect rect;
        public int slot;
        public float flat, green, highway;
        /** Local frame -> world. */
        public int x(int u, int v) { switch (slot) { case 1: return rect.x1() - 1 - v; case 2: return rect.x1() - 1 - u; case 3: return rect.x0() + v; default: return rect.x0() + u; } }
        public int z(int u, int v) { switch (slot) { case 1: return rect.z0() + u; case 2: return rect.z1() - 1 - v; case 3: return rect.z1() - 1 - u; default: return rect.z0() + v; } }
        public int lenU() { return slot == 1 || slot == 3 ? rect.d() : rect.w(); }
        public int lenV() { return slot == 1 || slot == 3 ? rect.w() : rect.d(); }
        /** World rectangle of the local half-open box [u0,u1) x [v0,v1). */
        public Rect rect(int u0, int v0, int u1, int v1) {
            int xa = x(u0, v0), za = z(u0, v0), xb = x(u1 - 1, v1 - 1), zb = z(u1 - 1, v1 - 1);
            return new Rect(Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb) + 1, Math.max(za, zb) + 1);
        }
        /** World side (0 N, 1 E, 2 S, 3 W) of a local direction: 0 = v- (toward the wall), 1 = u+, 2 = v+ (toward the centre), 3 = u-. */
        public int side(int local) {
            int[][] t = {{0, 1, 2, 3}, {1, 2, 3, 0}, {2, 3, 0, 1}, {3, 0, 1, 2}};
            return slot < 0 ? local & 3 : t[slot][local & 3];
        }
        /** Roads along u run along world x in slots 0 and 2. */
        public boolean uAlongX() { return slot == 0 || slot == 2 || slot < 0; }
    }

    public static final class RoadPlan {
        public final Rect r;
        public final boolean alongX;
        public final Style style;
        /** ring, arterial, spine, cross. */
        public final String kind;
        public final District district;
        RoadPlan(Rect r, boolean alongX, Style style, String kind, District d) { this.r = r; this.alongX = alongX; this.style = style; this.kind = kind; this.district = d; }
    }

    public static final class LotPlan {
        public Rect rect;
        /** World side the lot fronts (where its street is). */
        public int front;
        public District district;
        /** Building archetype id, or a yard-only lot type (parking, park, loading_yard, motor_yard, field). */
        public String type = "", yard = "none";
        /** Inclusive footprint {x0, z0, x1, z1}, or null when the lot carries no building. */
        public int[] foot;
        public int floors = 1;
        public LandmarkDef landmark;
        public boolean headquarters;
        public long seed;
    }

    public final long seed;
    public final GenDefs defs;
    public final List<Region> regions = new ArrayList<>();
    public final List<RoadPlan> roads = new ArrayList<>();
    public final List<Rect> alleys = new ArrayList<>();
    public final List<LotPlan> lots = new ArrayList<>();
    /** Per column: what the plan reserved it for (RES_*), 0 = unassigned city ground. */
    public final byte[] reserve = new byte[World.W * World.D];
    /** Inner square (inside the ring road): origin and size; slot depth, slot length and arterial width. */
    public int o, s, t, l, a;
    public final List<String> notes = new ArrayList<>();

    private WorldPlan(long seed, GenDefs defs) { this.seed = seed; this.defs = defs; }

    public Region region(District d) { for (Region r : regions) if (r.district == d) return r; return null; }
    public LotPlan landmarkLot(String id) { for (LotPlan lp : lots) if (lp.landmark != null && lp.landmark.id.equals(id)) return lp; return null; }
    public int reserved(int x, int z) { return x < 0 || z < 0 || x >= World.W || z >= World.D ? 0 : reserve[x * World.D + z]; }

    /** District of a city column as planned (roads belong to the district whose ground they bound on the wall side). */
    public District districtAt(int x, int z) {
        if (World.outsideDistance(x, z) > 0) return District.WILDERNESS;
        Region best = null;
        int bd = Integer.MAX_VALUE;
        for (Region r : regions) {
            int dx = Math.max(r.rect.x0() - x, x - (r.rect.x1() - 1)), dz = Math.max(r.rect.z0() - z, z - (r.rect.z1() - 1));
            int d = Math.max(0, Math.max(dx, dz));
            if (d < bd) { bd = d; best = r; }
        }
        return best == null ? District.WILDERNESS : best.district;
    }

    private void mark(Rect r, int what) {
        for (int x = Math.max(0, r.x0()); x < Math.min(World.W, r.x1()); x++) for (int z = Math.max(0, r.z0()); z < Math.min(World.D, r.z1()); z++) {
            int i = x * World.D + z;
            if (what == RES_ROAD || reserve[i] == 0) reserve[i] = (byte) what;
        }
    }

    private void road(Rect r, boolean alongX, Style st, String kind, District d) { roads.add(new RoadPlan(r, alongX, st, kind, d)); mark(r, RES_ROAD); }

    // ------------------------------------------------------------------ stage 1-2: terrain suitability and regions
    /** Natural ground height outside the city for a column (the one height rule shared by terrain, plan and tests). */
    public static int naturalHeight(int x, int z, long seed) {
        int g = World.GROUND, sd = (int) seed;
        int dist = World.outsideDistance(x, z);
        if (dist <= 0) return g - 1;
        float t = smooth(Math.min(1f, dist / 26f));
        float n = Noise.fbm(x / 70f, z / 70f, 4, sd) * 22 - 9 + Noise.fbm(x / 17f, z / 17f, 2, sd + 5) * 4;
        float h = g - 1 + n * t;
        // the highway cuts and fills its way out of the east gate on a graded bed, not a notch
        if (x >= World.CITY_MAX) {
            int dz = z < WildernessGen.HWY_Z0 ? WildernessGen.HWY_Z0 - z : z >= WildernessGen.HWY_Z1 ? z - WildernessGen.HWY_Z1 + 1 : 0;
            if (dz < 16) h = h + (g - 1 - h) * smooth(1f - dz / 16f);
        }
        return Math.max(8, Math.min(g + 22, Math.round(h)));
    }

    static float smooth(float t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    /** Measures the ground beyond each wall: how level it is, how wooded, and whether the highway leaves through it. */
    private float[][] survey() {
        float[][] out = new float[4][3];
        int sd = (int) seed;
        for (int side = 0; side < 4; side++) {
            double sum = 0, sq = 0;
            int n = 0, wood = 0;
            for (int i = World.CITY_MIN; i < World.CITY_MAX; i += 4) for (int k = 6; k < 70; k += 4) {
                int x = side == 1 ? World.CITY_MAX + k : side == 3 ? World.CITY_MIN - k : i, z = side == 0 ? World.CITY_MIN - k : side == 2 ? World.CITY_MAX + k : i;
                int h = naturalHeight(x, z, seed);
                sum += h; sq += (double) h * h; n++;
                if (Noise.fbm(x / 45f, z / 45f, 3, sd + 99) > 0.55f) wood++;
            }
            double mean = sum / n, dev = Math.sqrt(Math.max(0, sq / n - mean * mean));
            out[side][0] = (float) Math.max(0, 1 - dev / 6.0);
            out[side][1] = wood / (float) n * 2.5f;
            out[side][2] = side == 1 ? 1 : 0;
        }
        return out;
    }

    public static WorldPlan of(long seed, GenDefs defs) {
        WorldPlan p = new WorldPlan(seed, defs);
        Rng r = new Rng(Rng.mix(seed, 0x504C414EL));
        p.a = defs.arterial;
        p.o = World.CITY_MIN + defs.ring;
        p.s = World.CITY_MAX - World.CITY_MIN - 2 * defs.ring;
        p.t = defs.slotMin + 2 * r.nextInt(Math.max(1, (defs.slotMax - defs.slotMin) / 2 + 1));
        p.l = p.s - p.a - p.t;
        int o = p.o, s = p.s, t = p.t, l = p.l, a = p.a;
        // ---- ring road and the four arterial arms (stage 3): reserved before anything can be placed
        int lo = World.CITY_MIN, hi = World.CITY_MAX, rw = defs.ring;
        p.road(new Rect(lo, lo, hi, lo + rw), true, Style.STREET, "ring", null);
        p.road(new Rect(lo, hi - rw, hi, hi), true, Style.STREET, "ring", null);
        p.road(new Rect(lo, lo, lo + rw, hi), false, Style.STREET, "ring", null);
        p.road(new Rect(hi - rw, lo, hi, hi), false, Style.STREET, "ring", null);
        p.road(new Rect(o, o + t, o + l + a, o + t + a), true, Style.AVENUE, "arterial", null);          // north arm
        p.road(new Rect(o + l, o, o + l + a, o + l + a), false, Style.AVENUE, "arterial", null);          // east arm
        p.road(new Rect(o + t, o + l, o + s, o + l + a), true, Style.AVENUE, "arterial", null);           // south arm
        p.road(new Rect(o + t, o + t, o + t + a, o + s), false, Style.AVENUE, "arterial", null);          // west arm
        Rect[] slots = {
                new Rect(o, o, o + l, o + t),
                new Rect(o + l + a, o, o + s, o + l),
                new Rect(o + t + a, o + l + a, o + s, o + s),
                new Rect(o, o + t + a, o + t, o + s)};
        Rect centre = new Rect(o + t + a, o + t + a, o + l, o + l);
        // ---- districts onto slots (stage 2): the assignment with the best total suitability
        float[][] terr = p.survey();
        List<DistrictDef> outer = new ArrayList<>();
        DistrictDef mid = null;
        for (DistrictDef d : defs.districts.values()) { if (d.center) mid = d; else outer.add(d); }
        if (mid == null || outer.size() != 4) throw new IllegalStateException("districts.json must define one centre district and four outer districts");
        int[] best = null;
        float bs = -1e9f;
        int[][] perms = permutations();
        for (int[] pm : perms) {
            float sc = 0;
            for (int i = 0; i < 4; i++) { DistrictDef d = outer.get(i); float[] tv = terr[pm[i]]; sc += d.flat * tv[0] + d.green * tv[1] + d.highway * tv[2]; }
            if (sc > bs + 1e-5f) { bs = sc; best = pm; }
        }
        for (int i = 0; i < 4; i++) {
            Region rg = new Region();
            rg.def = outer.get(i); rg.district = rg.def.district; rg.slot = best[i]; rg.rect = slots[best[i]];
            rg.flat = terr[best[i]][0]; rg.green = terr[best[i]][1]; rg.highway = terr[best[i]][2];
            p.regions.add(rg);
        }
        Region c = new Region();
        c.def = mid; c.district = mid.district; c.slot = -1; c.rect = centre;
        p.regions.add(c);
        p.notes.add(String.format("slot depth %d, slot length %d, centre %d; suitability score %.2f", t, l, centre.w(), bs));
        for (Region rg : p.regions) {
            Rng rr = new Rng(Rng.mix(seed, rg.district.name().hashCode()));
            if (rg.slot < 0) p.planCentre(rg, rr); else p.planSlot(rg, rr);
        }
        return p;
    }

    private static int[][] permutations() {
        List<int[]> out = new ArrayList<>();
        for (int a = 0; a < 4; a++) for (int b = 0; b < 4; b++) for (int c = 0; c < 4; c++) for (int d = 0; d < 4; d++)
            if (a != b && a != c && a != d && b != c && b != d && c != d) out.add(new int[]{a, b, c, d});
        return out.toArray(new int[0][]);
    }

    // ------------------------------------------------------------------ stages 4-7 for an outer slot
    private void planSlot(Region rg, Rng r) {
        DistrictDef d = rg.def;
        int lu = rg.lenU(), tv = rg.lenV(), sp = defs.spine, cw = defs.cross;
        int vs = (tv - sp) / 2;
        // spine street down the middle of the slot, wall to arterial
        road(rg.rect(0, vs, lu, vs + sp), rg.uAlongX(), Style.STREET, "spine", rg.district);
        // ---- landmark lots (stage 4): one per half where the data asks, far apart along the spine
        LandmarkDef la = d.landmarks.get(0), lb = d.landmarks.get(1);
        boolean aOuter = !"inner".equals(la.half), bOuter = "outer".equals(lb.half);
        if (aOuter == bOuter) bOuter = !aOuter;
        int wa = Math.min(la.w, lu - 2 * (cw + 28)), wb = Math.min(lb.w, lu - 2 * (cw + 28));
        boolean aFirst = r.chance(0.5);
        int margin = cw + Math.max(30, Math.min(44, (lu - Math.max(wa, wb)) / 2 - cw));
        int ua = aFirst ? margin : lu - margin - wa, ub = aFirst ? lu - margin - wb : margin;
        int[][] lm = new int[2][];   // per half: {u0, u1} of its landmark, or null
        lm[aOuter ? 0 : 1] = new int[]{ua, ua + wa};
        lm[bOuter ? 0 : 1] = new int[]{ub, ub + wb};
        LandmarkDef[] ldef = new LandmarkDef[2];
        ldef[aOuter ? 0 : 1] = la; ldef[bOuter ? 0 : 1] = lb;
        // ---- cross streets (stage 5): on the landmark edges, then at an even pitch through what is left
        List<Integer> shared = new ArrayList<>();
        for (int h = 0; h < 2; h++) { shared.add(lm[h][0] - cw); shared.add(lm[h][1]); }
        for (int h = 0; h < 2; h++) {
            int v0 = h == 0 ? 0 : vs + sp, v1 = h == 0 ? vs : tv;
            List<Integer> cuts = new ArrayList<>();
            for (int c : shared) if (c > 20 && c + cw < lu - 20 && !(c + cw > lm[h][0] && c < lm[h][1])) cuts.add(c);
            cuts.sort(null);
            // drop cuts that crowd each other
            for (int i = cuts.size() - 1; i > 0; i--) if (cuts.get(i) - cuts.get(i - 1) < cw + 22) cuts.remove(i);
            // intervals between cuts: the landmark keeps its own; the rest are subdivided to the district's block length
            List<int[]> spans = new ArrayList<>();
            int cur = 0;
            for (int c : cuts) { spans.add(new int[]{cur, c}); cur = c + cw; }
            spans.add(new int[]{cur, lu});
            List<int[]> blocks = new ArrayList<>();
            for (int[] sp0 : spans) {
                if (sp0[0] <= lm[h][0] && sp0[1] >= lm[h][1]) {
                    // the landmark's span: the lot is exactly the landmark's frontage, any remainder becomes blocks
                    if (lm[h][0] - sp0[0] >= 14) blocks.add(new int[]{sp0[0], lm[h][0]});
                    if (sp0[1] - lm[h][1] >= 14) blocks.add(new int[]{lm[h][1], sp0[1]});
                    continue;
                }
                int len = sp0[1] - sp0[0];
                int avg = (d.blockMin + d.blockMax) / 2;
                int nb = Math.max(1, Math.round((len + cw) / (float) (avg + cw)));
                while (nb > 1 && (len - (nb - 1) * cw) / nb < d.blockMin) nb--;
                int bl = (len - (nb - 1) * cw) / nb, u = sp0[0];
                for (int i = 0; i < nb; i++) {
                    int end = i == nb - 1 ? sp0[1] : u + bl;
                    blocks.add(new int[]{u, end});
                    if (i < nb - 1) { cuts.add(end); u = end + cw; }
                }
            }
            for (int c : cuts) road(rg.rect(c, v0, c + cw, v1), !rg.uAlongX(), Style.STREET, "cross", rg.district);
            // ---- the landmark lot: reserved whole, fronting the spine
            LotPlan lp = new LotPlan();
            lp.rect = rg.rect(lm[h][0], v0, lm[h][1], v1);
            lp.front = rg.side(h == 0 ? 2 : 0);
            lp.district = rg.district; lp.landmark = ldef[h]; lp.type = ldef[h].id; lp.headquarters = ldef[h].id.equals(d.hq);
            lp.seed = Rng.mix(seed, lp.type.hashCode());
            lots.add(lp);
            mark(lp.rect, RES_LANDMARK);
            // ---- blocks -> an alley through the middle -> two rows of lots (stage 6 and 7)
            int aw = defs.alley, vm = v0 + (v1 - v0 - aw) / 2;
            for (int[] b : blocks) {
                if (b[1] - b[0] < 12) continue;
                Rect alley = rg.rect(b[0], vm, b[1], vm + aw);
                alleys.add(alley);
                mark(alley, RES_ALLEY);
                // the row by the wall or the arterial fronts that road; the other row fronts the spine
                boolean outerHalf = h == 0;
                row(rg, d, r, b[0], b[1], v0, vm, outerHalf ? 0 : 2, outerHalf ? 0 : 2);
                row(rg, d, r, b[0], b[1], vm + aw, v1, outerHalf ? 2 : 0, outerHalf ? 2 : 0);
            }
        }
    }

    /**
     * One row of lots between u0 and u1 with depth v0..v1, all fronting local side {@code frontLocal} (0 = v-, 2 = v+).
     * Lot widths come from the archetype chosen for each; the last lot absorbs the remainder.
     */
    private void row(Region rg, DistrictDef d, Rng r, int u0, int u1, int v0, int v1, int frontLocal, int unused) {
        int u = u0, depth = v1 - v0;
        int guard = 0;
        while (u1 - u >= 10 && guard++ < 40) {
            Support sup = pick(d, r, u1 - u, depth);
            if (sup == null) break;
            int w = r.range(sup.lotMin, sup.lotMax);
            if (u1 - (u + w) < 10) w = u1 - u;
            w = Math.min(w, u1 - u);
            LotPlan lp = new LotPlan();
            lp.rect = rg.rect(u, v0, u + w, v1);
            lp.front = rg.side(frontLocal);
            lp.district = rg.district; lp.type = sup.type; lp.yard = sup.yard;
            lp.seed = Rng.mix(seed, u * 7919L + v0 * 31L + rg.slot);
            fit(lp, d, r, w, depth);
            lots.add(lp);
            mark(lp.rect, RES_LOT);
            u += w;
        }
        if (u1 - u > 0) {
            // a sliver too narrow for a lot: a planted verge
            LotPlan lp = new LotPlan();
            lp.rect = rg.rect(u, v0, u1, v1);
            lp.front = rg.side(frontLocal); lp.district = rg.district; lp.type = "verge"; lp.yard = "verge";
            lots.add(lp);
            mark(lp.rect, RES_LOT);
        }
    }

    /** A supporting building (or yard) type for the space left, by the district's weights. */
    private Support pick(DistrictDef d, Rng r, int room, int depth) {
        float total = 0;
        for (Support s : d.supports) if (s.lotMin <= room && fitsDepth(s, d, depth)) total += s.weight;
        if (total <= 0) return null;
        float roll = r.nextFloat() * total;
        for (Support s : d.supports) {
            if (s.lotMin > room || !fitsDepth(s, d, depth)) continue;
            roll -= s.weight;
            if (roll <= 0) return s;
        }
        return null;
    }

    private boolean fitsDepth(Support s, DistrictDef d, int depth) {
        Archetype a = defs.archetypes.get(s.type);
        return a == null || depth - d.front - d.rear >= a.dMin;
    }

    /** Places the archetype's footprint inside the lot's setbacks: against the front setback line, centred across the width. */
    private void fit(LotPlan lp, DistrictDef d, Rng r, int lotW, int lotD) {
        Archetype a = defs.archetypes.get(lp.type);
        if (a == null) return;                                   // a yard-only lot
        int fw = Math.min(a.wMax, lotW - 2 * d.side), fd = Math.min(a.dMax, lotD - d.front - d.rear);
        if (fw < a.wMin || fd < a.dMin) { lp.type = "verge"; lp.yard = "verge"; return; }
        fw = Math.max(a.wMin, fw - r.nextInt(Math.min(3, fw - a.wMin + 1)));
        fd = Math.max(a.dMin, fd - r.nextInt(Math.min(3, fd - a.dMin + 1)));
        // lot-local frame: along the front (i) and back from it (j)
        boolean frontX = (lp.front & 1) == 0;      // front is a north or south side: the frontage runs along x
        int lw = frontX ? lp.rect.w() : lp.rect.d();
        int i0 = (lw - fw) / 2, j0 = d.front;
        int x0, z0, x1, z1;
        switch (lp.front) {
            case 0 -> { x0 = lp.rect.x0() + i0; z0 = lp.rect.z0() + j0; x1 = x0 + fw - 1; z1 = z0 + fd - 1; }
            case 2 -> { x0 = lp.rect.x0() + i0; z1 = lp.rect.z1() - 1 - j0; x1 = x0 + fw - 1; z0 = z1 - fd + 1; }
            case 1 -> { z0 = lp.rect.z0() + i0; x1 = lp.rect.x1() - 1 - j0; z1 = z0 + fw - 1; x0 = x1 - fd + 1; }
            default -> { z0 = lp.rect.z0() + i0; x0 = lp.rect.x0() + j0; z1 = z0 + fw - 1; x1 = x0 + fd - 1; }
        }
        lp.foot = new int[]{x0, z0, x1, z1};
        lp.floors = r.range(a.floorsMin, a.floorsMax);
    }

    // ------------------------------------------------------------------ the centre district
    private void planCentre(Region rg, Rng r) {
        DistrictDef d = rg.def;
        Rect c = rg.rect;
        int sw = defs.spine, half = (c.w() - sw) / 2;
        int mx = c.x0() + half, mz = c.z0() + half;
        road(new Rect(mx, c.z0(), mx + sw, c.z1()), false, Style.STREET, "spine", rg.district);
        road(new Rect(c.x0(), mz, c.x1(), mz + sw), true, Style.STREET, "spine", rg.district);
        Rect[] q = {new Rect(c.x0(), c.z0(), mx, mz), new Rect(mx + sw, c.z0(), c.x1(), mz), new Rect(c.x0(), mz + sw, mx, c.z1()), new Rect(mx + sw, mz + sw, c.x1(), c.z1())};
        // the two landmarks take diagonal blocks and face each other across the central crossing
        int first = r.nextInt(4), second = 3 - first;
        for (int i = 0; i < 4; i++) {
            if (i == first || i == second) {
                LandmarkDef def = d.landmarks.get(i == first ? 0 : 1);
                LotPlan lp = new LotPlan();
                lp.rect = q[i]; lp.front = i < 2 ? 2 : 0; lp.district = rg.district; lp.landmark = def; lp.type = def.id; lp.headquarters = def.id.equals(d.hq);
                lp.seed = Rng.mix(seed, def.id.hashCode());
                lots.add(lp);
                mark(lp.rect, RES_LANDMARK);
                continue;
            }
            // a commercial block: an alley cross through the middle, four lots each fronting an outside street
            Rect b = q[i];
            int aw = defs.alley, ax = b.x0() + (b.w() - aw) / 2, az = b.z0() + (b.d() - aw) / 2;
            Rect a1 = new Rect(ax, b.z0(), ax + aw, b.z1()), a2 = new Rect(b.x0(), az, b.x1(), az + aw);
            alleys.add(a1); alleys.add(a2);
            mark(a1, RES_ALLEY); mark(a2, RES_ALLEY);
            Rect[] ls = {new Rect(b.x0(), b.z0(), ax, az), new Rect(ax + aw, b.z0(), b.x1(), az), new Rect(b.x0(), az + aw, ax, b.z1()), new Rect(ax + aw, az + aw, b.x1(), b.z1())};
            for (int k = 0; k < 4; k++) {
                LotPlan lp = new LotPlan();
                lp.rect = ls[k];
                lp.front = k < 2 ? 0 : 2;                         // the north pair faces north, the south pair south
                lp.district = rg.district;
                lp.seed = Rng.mix(seed, i * 131L + k);
                Support sup = pick(d, r, lp.rect.w(), lp.rect.d());
                if (sup == null) { lp.type = "verge"; lp.yard = "verge"; }
                else { lp.type = sup.type; lp.yard = sup.yard; fit(lp, d, r, lp.rect.w(), lp.rect.d()); }
                lots.add(lp);
                mark(lp.rect, RES_LOT);
            }
        }
    }
}
