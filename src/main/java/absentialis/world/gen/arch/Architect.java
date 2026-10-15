package absentialis.world.gen.arch;

import static absentialis.world.gen.Gen.FLOOR_H;
import static absentialis.world.gen.Gen.G;

import absentialis.engine.util.Rng;
import absentialis.world.Block;
import absentialis.world.District;
import absentialis.world.MapData;
import absentialis.world.World;
import absentialis.world.gen.Gen;
import absentialis.world.gen.GenDefs.Archetype;
import absentialis.world.gen.Streets.Rect;
import absentialis.world.gen.arch.FloorPlan.Core;
import absentialis.world.gen.arch.FloorPlan.PEdge;
import absentialis.world.gen.arch.FloorPlan.PRoom;
import absentialis.world.gen.arch.MaterialRules.Mat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds one structure from an archetype (0.5): the room graph of each storey is laid out by {@link FloorPlan},
 * and this class turns the result into voxels - shell, slabs, partitions, one doorway per graph edge, a stairwell
 * that lines up on every storey, windows where a room (not a partition) stands behind the wall, a roof, finishes
 * from {@link MaterialRules} and furniture from {@link Furnisher}. Everything is written through the lot's clip
 * rectangle, and everything that was decided - rooms, links, doorways, the entrance - is recorded on the
 * {@link MapData.Building} so the validator, navigation and the debug views work from the same facts.
 */
public final class Architect {
    public static final class Spec {
        public Frame frame;
        public Archetype arch;
        public int floors = 1;
        /** The lot (half-open); writes outside it are refused. */
        public Rect lot;
        public String name = "", landmark = "";
        public District district;
        public float damage, boarded = 0.1f, broken = 0.25f, lit = 0.5f;
        /** Flat-roofed buildings of two storeys or more get a stair to the roof unless this is false. */
        public boolean roofAccess = true;
        public Rng rng;
    }

    private final Gen g;
    private final Furnisher furnisher;

    public Architect(Gen g) { this.g = g; this.furnisher = new Furnisher(g); }

    // ---- per-build state
    private Frame fr;
    private Mat mat;
    private Rng r;
    private Spec s;
    private Core core;
    private MapData.Building rec;
    private int storeyH, top;
    private final Set<Long> keep = new HashSet<>();

    private static long key(int x, int y, int z) { return ((long) x << 40) | ((long) y << 20) | z; }

    private void put(int a, int b, int y, int id) { g.set(fr.x(a, b), y, fr.z(a, b), id); }
    private int at(int a, int b, int y) { return g.get(fr.x(a, b), y, fr.z(a, b)); }
    /** Interior cell -> footprint cell. */
    private int fa(int p, int q) { return core.ia(p, q) + 1; }
    private int fb(int p, int q) { return core.ib(p, q) + 1; }

    public MapData.Building build(Spec spec) {
        s = spec; fr = spec.frame; r = spec.rng;
        Archetype a = spec.arch;
        mat = MaterialRules.of(a.style, r);
        g.clip(spec.lot);
        core = new Core();
        boolean flat = "flat".equals(a.roof);
        int floors = Math.max(1, spec.floors);
        boolean roofStairs = flat && floors >= 2 && spec.roofAccess && !"chain".equals(a.plan);
        floors = core.decide(fr.A - 2, fr.B - 2, a, floors, roofStairs);
        if (floors < 2) roofStairs = false;
        if (!core.stairs) roofStairs = false;
        storeyH = floors == 1 ? FLOOR_H * Math.max(1, a.tall) : FLOOR_H;
        top = G - 1 + (floors == 1 ? storeyH : floors * FLOOR_H);
        if (top + 8 >= World.H) { floors = Math.max(1, (World.H - 10 - (G - 1)) / FLOOR_H); top = G - 1 + floors * FLOOR_H; }

        rec = new MapData.Building();
        rec.id = g.map.buildings.size();
        rec.archetype = a.id; rec.name = spec.name; rec.landmark = spec.landmark; rec.style = a.style; rec.district = spec.district;
        rec.x0 = fr.x0; rec.z0 = fr.z0; rec.x1 = fr.x1; rec.z1 = fr.z1; rec.floors = floors; rec.front = fr.front; rec.topY = top;
        if (spec.lot != null) { rec.lotX0 = spec.lot.x0(); rec.lotZ0 = spec.lot.z0(); rec.lotX1 = spec.lot.x1(); rec.lotZ1 = spec.lot.z1(); }
        keep.clear();
        lastBase = -1;

        // ---- the volume: cleared above ground, a foundation under it
        g.fill(fr.x0, G, fr.z0, fr.x1, Math.min(World.H - 1, top + 9), fr.z1, Block.AIR);
        g.fill(fr.x0, G - 1, fr.z0, fr.x1, G - 1, fr.z1, mat.foundation);

        boolean open = "open".equals(a.plan);
        List<FloorPlan> plans = new ArrayList<>();
        List<int[]> roomBase = new ArrayList<>();
        for (int f = 0; f < floors; f++) {
            FloorPlan plan = FloorPlan.plan(core, a.rooms(f, floors), open && f == 0, mat.homely ? "hall" : "corridor", "storage");
            plans.add(plan);
            roomBase.add(new int[]{rec.rooms.size()});
            storey(f, plan, floors);
        }
        roof(a, floors, roofStairs);
        if (core.stairs) for (int f = 0; f < floors - 1 + (roofStairs ? 1 : 0); f++) flight(f);
        entrances(a, plans.get(0), roomBase.get(0)[0], open);
        for (int f = 0; f < floors; f++) windows(a, f, plans.get(f), floors);
        if (a.tall > 1 && floors == 1) mezzanine(plans.get(0));
        facade(a, floors);
        // ---- dress every room from its purpose, keeping doorways and stairs clear
        for (MapData.Room room : rec.rooms) furnisher.dress(room, rec, mat, a, keep, r, spec.lit);
        if (spec.damage > 0) weather(spec.damage, floors);
        g.clip(null);
        g.map.buildings.add(rec);
        return rec;
    }

    private long seedOf() { return rec.id * 2654435761L; }

    // ------------------------------------------------------------------ one storey
    private void storey(int f, FloorPlan plan, int floors) {
        int y0 = G - 1 + f * FLOOR_H, A = fr.A, B = fr.B;
        int base = rec.rooms.size();
        // rooms: the graph's nodes, in world coordinates
        for (PRoom pr : plan.rooms) {
            int xa = fr.x(fa(pr.p0, pr.q0), fb(pr.p0, pr.q0)), za = fr.z(fa(pr.p0, pr.q0), fb(pr.p0, pr.q0));
            int xb = fr.x(fa(pr.p1, pr.q1), fb(pr.p1, pr.q1)), zb = fr.z(fa(pr.p1, pr.q1), fb(pr.p1, pr.q1));
            MapData.Room room = new MapData.Room(base + pr.id, pr.type, f, Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb), y0);
            room.circulation = pr.circulation;
            // 1.0: every room is finished as a room of its own - its walls in what that kind of room would have (paper, paint,
            // tile, panelling; a tiled or panelled dado where one belongs), and a ceiling that is a ceiling, not the
            // boards of the floor above. Sheds and works are left bare: that is what they are.
            if (!mat.bare) {
                int hsh = Math.floorMod(room.x0 * 73 + room.z0 * 31 + f * 17 + (int) (seedOf() & 0xFFFF), 6);
                room.height = storeyH - 1;
                switch (pr.type) {
                    case "bedroom", "nursery" -> { room.wallBlock = new int[]{Block.WALLPAPER_FLORAL, Block.PLASTER_BLUE, Block.WALLPAPER_STRIPE, Block.PLASTER_OCHRE, Block.WALLPAPER_FLORAL, Block.PLASTER}[hsh]; room.ceilBlock = Block.CEILING_PLASTER; }
                    case "living", "lounge", "dining", "foyer" -> { room.wallBlock = new int[]{Block.WALLPAPER_STRIPE, Block.PLASTER_OCHRE, Block.WALLPAPER_FLORAL, Block.PLASTER, Block.WALLPAPER_STRIPE, Block.PLASTER_BLUE}[hsh]; if (hsh % 2 == 0) { room.dadoBlock = Block.WOOD_DARK; room.dadoH = 1; } room.ceilBlock = Block.CEILING_PLASTER; }
                    case "kitchen", "canteen" -> { room.wallBlock = hsh % 2 == 0 ? Block.PLASTER : Block.PLASTER_OCHRE; room.dadoBlock = hsh < 3 ? Block.TILE_WHITE : Block.TILE_GREEN; room.dadoH = 2; room.ceilBlock = Block.CEILING_PLASTER; }
                    case "bathroom", "wc", "toilet", "washroom", "showers" -> { room.wallBlock = hsh % 2 == 0 ? Block.TILE_WHITE : Block.TILE_GREEN; room.ceilBlock = Block.CEILING_PLASTER; }
                    case "study", "library", "office", "records" -> { room.wallBlock = hsh < 2 ? Block.WOOD_DARK : hsh < 4 ? Block.PLASTER : Block.WALLPAPER_STRIPE; if (hsh >= 2) { room.dadoBlock = Block.WOOD_DARK; room.dadoH = 1; } room.ceilBlock = mat.homely ? Block.CEILING_PLASTER : Block.CEILING_TILE; }
                    case "hall", "corridor", "stairs", "landing" -> { room.wallBlock = mat.homely ? (hsh % 2 == 0 ? Block.PLASTER : Block.WALLPAPER_STRIPE) : Block.PLASTER; if (!mat.homely) { room.dadoBlock = Block.TILE_GREEN; room.dadoH = 1; } room.ceilBlock = mat.homely ? Block.CEILING_PLASTER : Block.CEILING_TILE; }
                    case "storage", "closet", "garage", "plant", "boiler", "loading", "workshop" -> { room.ceilBlock = 0; room.height = 0; }
                    default -> { room.wallBlock = hsh < 3 ? Block.PLASTER : hsh < 5 ? Block.PLASTER_OCHRE : Block.PLASTER_BLUE; room.ceilBlock = mat.homely ? Block.CEILING_PLASTER : Block.CEILING_TILE; }
                }
            }
            rec.rooms.add(room);
        }
        // slab and floor finishes: one finish per room, chosen from its purpose; the perimeter band is trim
        int[] finish = new int[plan.rooms.size()];
        for (PRoom pr : plan.rooms) finish[pr.id] = MaterialRules.floor(mat, pr.type, f > 0, r);
        int under = finish.length > 0 ? finish[0] : MaterialRules.floor(mat, "corridor", f > 0, r);
        for (int a = 0; a < A; a++) for (int b = 0; b < B; b++) {
            boolean edge = a == 0 || b == 0 || a == A - 1 || b == B - 1;
            int id;
            if (edge) id = f == 0 ? mat.foundation : mat.trim;
            else { int c = plan.at(a - 1, b - 1); id = c >= 0 ? finish[c] : under; }
            put(a, b, y0, id);
        }
        // exterior shell
        int wallTop = y0 + storeyH - 1;
        for (int a = 0; a < A; a++) for (int b = 0; b < B; b++) {
            if (a != 0 && b != 0 && a != A - 1 && b != B - 1) continue;
            boolean corner = (a == 0 || a == A - 1) && (b == 0 || b == B - 1);
            for (int y = y0 + 1; y <= wallTop; y++) put(a, b, y, corner ? mat.trim : mat.wall);
        }
        // partitions: a wall cell takes its finish from the rooms on either side of it
        for (int p = 0; p < core.P; p++) for (int q = 0; q < core.Q; q++) {
            if (plan.cell[p][q] != FloorPlan.WALL) continue;
            String ta = null, tb = null;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int np = p + d[0], nq = q + d[1];
                if (np < 0 || nq < 0 || np >= core.P || nq >= core.Q) continue;
                int c = plan.cell[np][nq];
                if (c < 0) continue;
                if (ta == null) ta = plan.rooms.get(c).type; else if (tb == null) tb = plan.rooms.get(c).type;
            }
            int id = MaterialRules.partition(mat, ta, tb);
            for (int y = y0 + 1; y <= y0 + FLOOR_H - 1; y++) put(fa(p, q), fb(p, q), y, MaterialRules.course(id, y - y0 - 1));
        }
        // doorways: one per edge of the room graph
        for (PEdge e : plan.edges) {
            int a = fa(e.p, e.q), b = fb(e.p, e.q);
            PRoom into = plan.rooms.get(e.rb);
            String kind = e.open ? "open" : MaterialRules.door(mat, into.type);
            int block = kind.equals("open") ? Block.AIR : kind.equals("metal") ? Block.DOOR_METAL : Block.DOOR_WOOD;
            put(a, b, y0 + 1, block); put(a, b, y0 + 2, block);
            int wx = fr.x(a, b), wz = fr.z(a, b);
            rec.doors.add(new MapData.Doorway(wx, y0 + 1, wz, base + e.ra, base + e.rb, kind, false));
            rec.rooms.get(base + e.ra).links.add(base + e.rb);
            rec.rooms.get(base + e.rb).links.add(base + e.ra);
            clearAround(wx, y0 + 1, wz);
        }
        // the stairwell joins the circulation node of this storey to the one below (the first room of each)
        if (core.stairs && f > 0 && lastBase >= 0 && base < rec.rooms.size()) { rec.rooms.get(lastBase).links.add(base); rec.rooms.get(base).links.add(lastBase); }
        lastBase = base;
    }

    private int lastBase = -1;

    private void clearAround(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) keep.add(key(x + dx, y, z + dz));
    }

    // ------------------------------------------------------------------ stairs
    /**
     * One flight from storey f to f+1 in lane f%2 of the stairwell: three steps and a landing on the upper slab.
     * Lane 1 lies on the corridor's wall line, so the well is open to the corridor along its whole length; the
     * flight above uses the other lane and runs the other way (a switchback).
     */
    private void flight(int f) {
        int y0 = G - 1 + f * FLOOR_H, c = f & 1;
        // the well itself: clear, with a floor on this storey wherever the flight below did not open it
        for (int lane = 0; lane < 2; lane++) for (int along = 0; along < 6; along++) {
            int p = core.sp0 + along, q = core.laneQ(lane);
            if (q >= core.Q) continue;
            int a = fa(p, q), b = fb(p, q);
            for (int y = y0 + 1; y <= y0 + FLOOR_H - 1; y++) put(a, b, y, Block.AIR);
            keep.add(key(fr.x(a, b), y0 + 1, fr.z(a, b)));
        }
        int q = core.laneQ(c);
        if (q >= core.Q) return;
        for (int k = 1; k <= FLOOR_H - 1; k++) {
            int along = c == 0 ? k : 5 - k, p = core.sp0 + along;
            int a = fa(p, q), b = fb(p, q);
            for (int y = y0 + 1; y < y0 + k; y++) put(a, b, y, mat.stairSupport);
            put(a, b, y0 + k, mat.stair);
            // head room through the slab above every tread: a body on the first tread is already rising into the
            // second, and 0.5 left the slab over it - the obstruction at the top of every stair
            put(a, b, y0 + FLOOR_H, Block.AIR);
            keep.add(key(fr.x(a, b), y0 + FLOOR_H + 1, fr.z(a, b)));
        }
        // the fourth rise: the lip of the upper slab is itself a tread (0.5 ended the flight one rise short, which
        // only worked while every block could be stepped onto)
        {
            // (0.7: one more tread for the taller storey - the six cells of the well are a flat start, four treads and the lip)
            int along = c == 0 ? FLOOR_H : 5 - FLOOR_H, p = core.sp0 + along;
            int a = fa(p, q), b = fb(p, q);
            put(a, b, y0 + FLOOR_H, mat.stair);
            for (int y = y0 + FLOOR_H + 1; y <= y0 + FLOOR_H + 2; y++) if (y < World.H) put(a, b, y, Block.AIR);
        }
        // the cells a walker arrives on and leaves by, on the storey above, stay clear of furniture
        for (int along = 0; along < 6; along++) for (int lane = 0; lane < 2; lane++) {
            int qq = core.laneQ(lane);
            if (qq < core.Q) keep.add(key(fr.x(fa(core.sp0 + along, qq), fb(core.sp0 + along, qq)), y0 + FLOOR_H + 1, fr.z(fa(core.sp0 + along, qq), fb(core.sp0 + along, qq))));
        }
    }

    // ------------------------------------------------------------------ roof
    private void roof(Archetype a, int floors, boolean roofStairs) {
        int A = fr.A, B = fr.B;
        boolean glass = "glasshouse".equals(a.style);
        if ("pitched".equals(a.roof)) {
            // a loft floor, then a gable along the longer axis, kept inside the footprint
            for (int x = 0; x < A; x++) for (int b = 0; b < B; b++) put(x, b, top, x == 0 || b == 0 || x == A - 1 || b == B - 1 ? mat.trim : MaterialRules.ceiling(mat));
            boolean ridgeA = A >= B;
            int span = ridgeA ? B : A, len = ridgeA ? A : B;
            for (int k = 0; k <= span / 2; k++) {
                int lo = k, hi = span - 1 - k, y = top + 1 + k;
                if (lo > hi || y >= World.H - 1) break;
                for (int i = 0; i < len; i++) {
                    if (ridgeA) { put(i, lo, y, mat.roof); put(i, hi, y, mat.roof); }
                    else { put(lo, i, y, mat.roof); put(hi, i, y, mat.roof); }
                }
                // gable ends close the triangle under the slope
                for (int j = lo + 1; j <= hi - 1; j++) {
                    if (ridgeA) { put(0, j, y, mat.wall); put(A - 1, j, y, mat.wall); }
                    else { put(j, 0, y, mat.wall); put(j, B - 1, y, mat.wall); }
                }
            }
            return;
        }
        for (int x = 0; x < A; x++) for (int b = 0; b < B; b++) put(x, b, top, glass ? Block.GLASS_DIRTY : mat.roof);
        if ("flat".equals(a.roof) && !glass) for (int x = 0; x < A; x++) for (int b = 0; b < B; b++) if (x == 0 || b == 0 || x == A - 1 || b == B - 1) put(x, b, top + 1, mat.parapet);
        if (!roofStairs) return;
        // roof access: a hut over the stairwell with its door on the corridor side
        int c = (floors - 1) & 1;
        int pa = core.sp0 - 1, pb = core.sp0 + 6, qa = core.cq + core.cw - 1, qb = Math.min(core.Q, core.band1Q0() + 1);
        for (int p = pa; p <= pb; p++) for (int q = qa; q <= qb; q++) {
            boolean wall = p == pa || p == pb || q == qa || q == qb;
            int fa = core.ia(p, q) + 1, fb = core.ib(p, q) + 1;
            if (fa < 0 || fb < 0 || fa >= A || fb >= B) continue;
            for (int y = top + 1; y <= top + 3; y++) put(fa, fb, y, wall ? (mat.trim == Block.CONCRETE ? Block.CONCRETE_DARK : mat.trim) : Block.AIR);
            put(fa, fb, top + 4, Block.ROOF_TAR);
        }
        int dp = core.sp0 + (c == 1 ? 0 : 5);
        int da = core.ia(dp, qa) + 1, db = core.ib(dp, qa) + 1;
        put(da, db, top + 1, Block.DOOR_METAL); put(da, db, top + 2, Block.DOOR_METAL);
        rec.doors.add(new MapData.Doorway(fr.x(da, db), top + 1, fr.z(da, db), -1, -1, "metal", true));
        g.lamp(fr.x(core.ia(core.sp0 + 2, core.cq + core.cw) + 1, core.ib(core.sp0 + 2, core.cq + core.cw) + 1), top + 3, fr.z(core.ia(core.sp0 + 2, core.cq + core.cw) + 1, core.ib(core.sp0 + 2, core.cq + core.cw) + 1));
    }

    // ------------------------------------------------------------------ entrances
    private void entrances(Archetype a, FloorPlan ground, int base, boolean open) {
        int y0 = G - 1, A = fr.A, B = fr.B;
        boolean dbl = a.door.startsWith("double"), roller = "roller".equals(a.door);
        String kind = a.door.contains("glass") ? "glass" : a.door.contains("metal") ? "metal" : roller ? "roller" : "wood";
        int aD, width = roller ? 4 : dbl ? 2 : 1;
        if (core.chain) { aD = 1 + (core.Q - width) / 2; width = Math.min(width, Math.max(1, core.Q)); }
        else if (core.perp) { aD = 1 + core.cq; width = Math.min(width, Math.max(1, core.cw)); }
        else if (core.twoBands && !open) { aD = 1 + core.stubP; width = Math.min(width, core.stubW); }
        else { width = Math.min(width, Math.max(1, core.P - 2)); aD = 1 + Math.max(0, (core.P - width) / 2); }
        aD = Math.max(1, Math.min(A - 1 - width, aD));
        int hallId = base;   // the circulation node (or the open hall) is always the first room of a storey
        for (int k = 0; k < width; k++) {
            int da = aD + k;
            int into = ground.at(da - 1, 0);
            if (roller) {
                // a shutter rolled most or all of the way up: the opening is the way in
                boolean half = r.chance(0.4);
                put(da, 0, y0 + 1, Block.AIR); put(da, 0, y0 + 2, Block.AIR); put(da, 0, y0 + 3, half ? Block.CORRUGATED : Block.AIR);
            } else {
                // a two-cell entrance is one double door (0.6): two leaves on one object, hinged at the jambs
                boolean pair = dbl && width == 2;
                int block = kind.equals("glass") ? (pair ? Block.DOOR_DOUBLE_GLASS : Block.DOOR_GLASS) : kind.equals("metal") ? (pair ? Block.DOOR_DOUBLE_METAL : Block.DOOR_METAL) : pair ? Block.DOOR_DOUBLE_WOOD : Block.DOOR_WOOD;
                put(da, 0, y0 + 1, block); put(da, 0, y0 + 2, block);
            }
            int wx = fr.x(da, 0), wz = fr.z(da, 0);
            rec.doors.add(new MapData.Doorway(wx, y0 + 1, wz, -1, into >= 0 ? base + into : hallId, roller ? "roller" : kind, true));
            clearAround(wx, y0 + 1, wz);
            // the threshold: a step of pavement outside, clear head room above it
            int ox = fr.x(da, -1), oz = fr.z(da, -1);
            g.set(ox, y0, oz, mat.foundation);
            for (int y = y0 + 1; y <= y0 + 3; y++) g.set(ox, y, oz, Block.AIR);
            if (k == 0) { rec.entryX = ox; rec.entryY = y0 + 1; rec.entryZ = oz; }
        }
        if (!a.back || core.chain) return;
        // a second way in: the far end of the corridor (back door) or its side end (side door)
        String bk = mat.bare && !mat.timber || mat.fitted ? "metal" : "wood";
        int block = bk.equals("metal") ? Block.DOOR_METAL : Block.DOOR_WOOD;
        int[][] tries = core.perp ? new int[][]{{1 + core.cq, B - 1, 1 + core.cq, B}} : new int[][]{{0, 1 + core.cq, -1, 1 + core.cq}, {A - 1, 1 + core.cq, A, 1 + core.cq}};
        for (int[] t : tries) {
            int ox = fr.x(t[2], t[3]), oz = fr.z(t[2], t[3]);
            if (!g.inClip(ox, oz)) continue;
            int inside = ground.at(t[0] == 0 ? 0 : t[0] == A - 1 ? A - 3 : t[0] - 1, t[1] == B - 1 ? B - 3 : t[1] - 1);
            if (inside < 0) continue;
            put(t[0], t[1], y0 + 1, block); put(t[0], t[1], y0 + 2, block);
            int wx = fr.x(t[0], t[1]), wz = fr.z(t[0], t[1]);
            rec.doors.add(new MapData.Doorway(wx, y0 + 1, wz, -1, base + inside, bk, true));
            clearAround(wx, y0 + 1, wz);
            for (int y = y0 + 1; y <= y0 + 3; y++) g.set(ox, y, oz, Block.AIR);
            if (!g.w.solid(ox, y0, oz)) g.set(ox, y0, oz, mat.foundation);
            break;
        }
    }

    // ------------------------------------------------------------------ the face of the building (0.6)
    /**
     * What makes the street read as where it is: a dark plinth under a masonry wall, a concrete canopy over the way
     * in, and on the blocks of flats a balcony at every other window of every upper storey - a slab and a rail.
     */
    private void facade(Archetype a, int floors) {
        int A = fr.A, B = fr.B, y0 = G - 1;
        if (mat.plinth != mat.wall) for (int x = 0; x < A; x++) for (int b = 0; b < B; b++) {
            if (x != 0 && b != 0 && x != A - 1 && b != B - 1) continue;
            if (at(x, b, y0 + 1) == mat.wall) put(x, b, y0 + 1, mat.plinth);
        }
        // the canopy: one slab deep, over every cell of the front entrance
        if (!"roller".equals(a.door) && !mat.timber) for (int x = 1; x < A - 1; x++) {
            int id = at(x, 0, y0 + 1);
            if (id == 0 || !Block.ALL[id].is(Block.DOOR)) continue;
            for (int d = -1; d <= 1; d++) if (x + d >= 0 && x + d < A && g.inClip(fr.x(x + d, -1), fr.z(x + d, -1)) && g.get(fr.x(x + d, -1), y0 + 4, fr.z(x + d, -1)) == Block.AIR) g.set(fr.x(x + d, -1), y0 + 4, fr.z(x + d, -1), mat.trim);
        }
        if (!"apartment".equals(a.style) || floors < 2) return;
        for (int f = 1; f < floors; f++) {
            int fy = y0 + f * FLOOR_H;
            for (int side = 0; side < 2; side++) {
                int wb = side == 0 ? 0 : B - 1, ob = side == 0 ? -1 : B;
                for (int x = 2; x < A - 2; x++) {
                    int w = at(x, wb, fy + 2);
                    boolean glazed = w != 0 && w != mat.wall && w != mat.trim && w != mat.plinth && Block.ALL[w].is(Block.TRANSLUCENT);
                    if (!glazed || (x / 3 + f) % 2 != 0) continue;
                    boolean room = true;
                    for (int d = -1; d <= 1 && room; d++) room = g.inClip(fr.x(x + d, ob), fr.z(x + d, ob)) && g.get(fr.x(x + d, ob), fy, fr.z(x + d, ob)) == Block.AIR && g.get(fr.x(x + d, ob), fy + 1, fr.z(x + d, ob)) == Block.AIR;
                    if (!room) continue;
                    for (int d = -1; d <= 1; d++) {
                        g.set(fr.x(x + d, ob), fy, fr.z(x + d, ob), mat.trim);
                        g.set(fr.x(x + d, ob), fy + 1, fr.z(x + d, ob), (x + f) % 5 == 0 ? Block.CORRUGATED : Block.IRON_FENCE);
                    }
                    x += 3;
                }
            }
        }
    }

    // ------------------------------------------------------------------ windows
    private void windows(Archetype a, int f, FloorPlan plan, int floors) {
        int y0 = G - 1 + f * FLOOR_H, A = fr.A, B = fr.B;
        boolean glassHouse = "glasshouse".equals(a.style);
        for (int side = 0; side < 4; side++) {
            int len = (side & 1) == 0 ? A : B;
            for (int i = 1; i < len - 1; i++) {
                int wa, wb, ia, ib;
                switch (side) {
                    case 0 -> { wa = i; wb = 0; ia = i - 1; ib = 0; }
                    case 2 -> { wa = i; wb = B - 1; ia = i - 1; ib = B - 3; }
                    case 3 -> { wa = 0; wb = i; ia = 0; ib = i - 1; }
                    default -> { wa = A - 1; wb = i; ia = A - 3; ib = i - 1; }
                }
                int c = plan.at(ia, ib);
                if (c < 0) continue;                                   // a partition or the stairwell stands behind this cell
                if (at(wa, wb, y0 + 1) != mat.wall) continue;          // a door is here
                String type = plan.rooms.get(c).type;
                if (windowless(type)) continue;
                if (glassHouse) { for (int y = y0 + 1; y <= y0 + storeyH - 1; y++) put(wa, wb, y, i % 4 == 0 ? mat.trim : Block.GLASS_DIRTY); continue; }
                if (a.shopfront && f == 0 && side == 0) {
                    if (i % 5 == 0) continue;
                    int gl = pane(Block.GLASS, true);
                    put(wa, wb, y0 + 1, gl); put(wa, wb, y0 + 2, gl == Block.BOARDED ? Block.BOARDED : pane(Block.GLASS, false));
                    if (Math.abs(i - len / 2) <= 3) put(wa, wb, y0 + 3, Block.SIGN_SHOP);
                    continue;
                }
                if (a.curtain) {
                    if (i % 4 == 0) { for (int y = y0 + 1; y <= y0 + 3; y++) put(wa, wb, y, mat.trim); continue; }
                    put(wa, wb, y0 + 2, pane(mat.window, f == 0)); put(wa, wb, y0 + 3, pane(mat.window, false));
                    continue;
                }
                int sp = Math.max(2, a.windows);
                if (i % sp != 1) continue;
                int w = pane(mat.window, f == 0);
                put(wa, wb, y0 + 2, w);
                // tall lights, two panes high, wherever people live or work (0.5 gave homes a single block of glass)
                if (!mat.bare && !a.shopfront && storeyH == FLOOR_H) put(wa, wb, y0 + 3, w == Block.BOARDED ? Block.BOARDED : w == Block.AIR ? Block.AIR : pane(mat.window, false));
                if (storeyH > FLOOR_H && i % (sp * 2) == 1) put(wa, wb, y0 + storeyH - 2, pane(mat.window, false));   // clerestory light in a tall shed
            }
        }
    }

    private static boolean windowless(String t) {
        return switch (t) { case "cell", "holding", "armory", "evidence", "coldroom", "server", "specimen", "containment", "switchgear", "closet", "pantry", "depot" -> true; default -> false; };
    }

    private int pane(int glass, boolean ground) {
        if (r.chance(s.boarded * (ground ? 2f : 1f))) return Block.BOARDED;
        if (r.chance(s.broken)) return Block.AIR;
        if (r.chance(0.15)) return glass == Block.GLASS_BLUE ? Block.GLASS_BLUE : Block.GLASS_CRACKED;
        return glass;
    }

    // ------------------------------------------------------------------ tall sheds
    /** In a tall single storey the back rooms are roofed at normal height; their roof is a mezzanine deck with a ladder and a rail. */
    private void mezzanine(FloorPlan plan) {
        int y = G - 1 + FLOOR_H;
        boolean any = false;
        int ladderA = -1, ladderB = -1;
        for (int p = 0; p < core.P; p++) for (int q = 0; q < core.Q; q++) {
            int c = plan.cell[p][q];
            boolean hall = c == 0;
            if (hall) continue;
            put(fa(p, q), fb(p, q), y, MaterialRules.ceiling(mat) == Block.CONCRETE_FLOOR ? Block.GRATE : MaterialRules.ceiling(mat));
            any = true;
            // the deck's edge toward the hall carries a rail, except where the ladder comes up
            if (q > 0 && plan.cell[p][q - 1] == 0) {
                // the ladder stands against a blank stretch of the wall, never across a doorway
                int below = at(fa(p, q), fb(p, q), G);
                if (ladderA < 0 && p >= 2 && below != 0 && !Block.ALL[below].is(Block.DOOR) && !keep.contains(key(fr.x(fa(p, q - 1), fb(p, q - 1)), G, fr.z(fa(p, q - 1), fb(p, q - 1))))) { ladderA = fa(p, q - 1); ladderB = fb(p, q - 1); }
                else put(fa(p, q), fb(p, q), y + 1, Block.IRON_FENCE);
            }
        }
        if (!any || ladderA < 0) return;
        for (int yy = G; yy <= y; yy++) put(ladderA, ladderB, yy, Block.LADDER);
        keep.add(key(fr.x(ladderA, ladderB), G, fr.z(ladderA, ladderB)));
    }

    // ------------------------------------------------------------------ wear
    /**
     * Controlled damage (pipeline stage 9): breaches in the shell above head height, scorching round them and the
     * fallen masonry at the foot of the wall - inside the lot, never on a road, never through a slab or a column.
     */
    private void weather(float amount, int floors) {
        int holes = 1 + (int) (amount * 3 * r.nextFloat());
        for (int h = 0; h < holes; h++) {
            int side = r.nextInt(4), len = (side & 1) == 0 ? fr.A : fr.B;
            if (len < 7) continue;
            int i = 2 + r.nextInt(len - 5), f = floors > 1 ? r.nextInt(floors) : 0;
            int y0 = G - 1 + f * FLOOR_H;
            for (int k = 0; k < 2; k++) for (int y = y0 + 2; y <= y0 + 3; y++) {
                int wa = side == 0 || side == 2 ? i + k : side == 3 ? 0 : fr.A - 1, wb = side == 0 ? 0 : side == 2 ? fr.B - 1 : i + k;
                int cur = at(wa, wb, y);
                if (cur == mat.trim || Block.ALL[cur].is(Block.DOOR)) continue;
                put(wa, wb, y, Block.AIR);
                int nb = at(wa, wb, y0 + 1);
                if (nb == mat.wall && r.chance(0.5)) put(wa, wb, y0 + 1, scorched(mat.wall));
            }
            // what fell: a few blocks of rubble at the foot of the wall, on the lot
            int oa = side == 3 ? -1 : side == 1 ? fr.A : i, ob = side == 0 ? -1 : side == 2 ? fr.B : i;
            int ox = fr.x(oa, ob), oz = fr.z(oa, ob);
            if (g.inClip(ox, oz) && g.air(ox, G, oz) && g.w.solid(ox, G - 1, oz) && !keep.contains(key(ox, G, oz))) g.set(ox, G, oz, Block.ALL[mat.wall].mat == absentialis.world.Material.BRICK ? Block.RUBBLE_BRICK : Block.RUBBLE);
        }
    }

    private static int scorched(int wall) {
        if (Block.ALL[wall].mat == absentialis.world.Material.BRICK) return Block.BRICK_BURNT;
        if (Block.ALL[wall].mat == absentialis.world.Material.WOOD) return Block.CHAR_WOOD;
        if (wall == Block.CONCRETE || wall == Block.CONCRETE_PANEL) return Block.CONCRETE_STAINED;
        return wall;
    }
}
