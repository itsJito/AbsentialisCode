package absentialis.world.gen;

import static absentialis.world.gen.Gen.G;

import absentialis.engine.util.Rng;
import absentialis.world.Block;
import absentialis.world.District;
import absentialis.world.MapData;
import absentialis.world.World;
import absentialis.world.gen.GenDefs.Archetype;
import absentialis.world.gen.GenDefs.DistrictDef;
import absentialis.world.gen.Streets.Rect;
import absentialis.world.gen.arch.Architect;
import absentialis.world.gen.arch.Frame;

/**
 * A lot seen from its street (0.5): the drawing board every yard and every landmark compound is laid out on.
 * Coordinates are lot-local - {@code a} along the frontage, {@code b} back from it - so a compound designed once
 * stands correctly on any side of any district. All writes go through the generator's clip rectangle for this
 * lot, so nothing drawn here can leave it.
 */
public final class Site {
    public final Gen g;
    public final Frame f;
    public final Rect lot;
    public final int A, B;
    public final District district;
    public final DistrictDef def;
    public final Rng r;
    private final Architect architect;
    /** Cells that must stay walkable (door approaches, paths, gates): props are never placed on them. */
    private final java.util.Set<Long> keep = new java.util.HashSet<>();

    public Site(Gen g, Architect architect, Rect lot, int front, District district, DistrictDef def, Rng r) {
        this.g = g; this.architect = architect; this.lot = lot; this.district = district; this.def = def; this.r = r;
        this.f = new Frame(lot.x0(), lot.z0(), lot.x1() - 1, lot.z1() - 1, front);
        this.A = f.A; this.B = f.B;
    }

    public int x(int a, int b) { return f.x(a, b); }
    public int z(int a, int b) { return f.z(a, b); }
    private static long key(int x, int z) { return ((long) x << 20) | z; }
    public void keep(int a, int b) { keep.add(key(x(a, b), z(a, b))); }
    public boolean kept(int a, int b) { return keep.contains(key(x(a, b), z(a, b))); }
    public boolean in(int a, int b) { return a >= 0 && b >= 0 && a < A && b < B; }

    public void begin() { g.clip(lot); }
    public void end() { g.clip(null); }

    /** Lays the lot's ground and clears everything above it. */
    public void ground(int block) {
        g.fill(lot.x0(), G - 1, lot.z0(), lot.x1() - 1, G - 1, lot.z1() - 1, block);
        g.fill(lot.x0(), G, lot.z0(), lot.x1() - 1, World.H - 1, lot.z1() - 1, Block.AIR);
    }

    public void set(int a, int b, int y, int id) { g.set(x(a, b), y, z(a, b), id); }
    public int get(int a, int b, int y) { return g.get(x(a, b), y, z(a, b)); }
    public boolean air(int a, int b, int y) { return in(a, b) && g.air(x(a, b), y, z(a, b)); }
    /** A free outdoor cell: on the lot, open, on solid ground, not a path. */
    public boolean open(int a, int b) { return in(a, b) && !kept(a, b) && g.air(x(a, b), G, z(a, b)) && g.air(x(a, b), G + 1, z(a, b)) && g.w.solid(x(a, b), G - 1, z(a, b)); }

    public void fill(int a0, int b0, int a1, int b1, int y0, int y1, int id) {
        int xa = x(a0, b0), za = z(a0, b0), xb = x(a1, b1), zb = z(a1, b1);
        g.fill(xa, y0, za, xb, y1, zb, id);
    }

    /** Ground finish over a local box. */
    public void pave(int a0, int b0, int a1, int b1, int id) { fill(a0, b0, a1, b1, G - 1, G - 1, id); }

    /** A prop on the ground, only where the cell is free. Returns true when placed. */
    public boolean prop(int a, int b, int id) {
        if (!open(a, b)) return false;
        if (Block.ALL[id].is(Block.CONTAINER)) g.container(x(a, b), G, z(a, b), id, null); else set(a, b, G, id);
        return true;
    }

    public boolean crate(int a, int b, int id, String table) {
        if (!open(a, b)) return false;
        g.container(x(a, b), G, z(a, b), id, table);
        return true;
    }

    /**
     * A fence or wall round the local box, {@code h} high, leaving the listed gates open. A gate is
     * {a0, b0, a1, b1}: the cells left clear (and kept clear of props afterwards).
     */
    public void enclose(int a0, int b0, int a1, int b1, int id, int h, int cap, int[]... gates) {
        for (int a = a0; a <= a1; a++) for (int b = b0; b <= b1; b++) {
            if (a != a0 && a != a1 && b != b0 && b != b1) continue;
            boolean gate = false;
            for (int[] gt : gates) if (a >= gt[0] && a <= gt[2] && b >= gt[1] && b <= gt[3]) gate = true;
            if (gate) { keep(a, b); for (int y = G; y < G + h + 2; y++) set(a, b, y, Block.AIR); continue; }
            for (int y = G; y < G + h; y++) set(a, b, y, id);
            if (cap != 0) set(a, b, G + h, cap);
        }
    }

    /** A straight path kept clear between two local cells (an L when they are not in line), paved with {@code id}. */
    public void path(int a0, int b0, int a1, int b1, int id, int width) {
        int a = a0, b = b0;
        while (true) {
            for (int k = 0; k < width; k++) {
                int pa = a + (b == b1 && a != a1 ? 0 : k), pb = b + (b == b1 && a != a1 ? k : 0);
                if (!in(pa, pb)) continue;
                keep(pa, pb);
                if (id != 0 && g.w.solid(x(pa, pb), G - 1, z(pa, pb))) set(pa, pb, G - 1, id);
                for (int y = G; y < G + 3; y++) { int c = get(pa, pb, y); if (c != 0 && !Block.ALL[c].is(Block.DOOR)) set(pa, pb, y, Block.AIR); }
            }
            if (a == a1 && b == b1) break;
            if (b != b1) b += Integer.signum(b1 - b); else a += Integer.signum(a1 - a);
        }
    }

    /** Builds an archetype on the local box (inclusive), its front on local side {@code localFront} (0 front, 1 a-max, 2 back, 3 a-min). */
    public MapData.Building building(String archetype, int a0, int b0, int a1, int b1, int localFront, int floors, String name, String landmark) {
        Archetype arch = g.defs.archetype(archetype);
        Architect.Spec s = new Architect.Spec();
        s.frame = f.sub(a0, b0, a1, b1, localFront);
        s.arch = arch;
        s.floors = floors > 0 ? floors : r.range(arch.floorsMin, arch.floorsMax);
        s.lot = lot; s.name = name; s.landmark = landmark; s.district = district;
        s.damage = r.chance(def.damage) ? r.range(0.2f, 0.7f) : 0;
        s.boarded = def.boarded; s.broken = 0.12f + def.damage * 0.8f; s.lit = def.lit;
        s.rng = new Rng(Rng.mix(g.seed, s.frame.x0 * 73856093L ^ s.frame.z0 * 19349663L));
        MapData.Building b = architect.build(s);
        g.clip(lot);                                  // the architect releases the clip when it is done
        // every exterior door gets a clear cell outside it
        for (MapData.Doorway d : b.doors) if (d.exterior && d.y == G) for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int ox = d.x + o[0], oz = d.z + o[1];
            if (!b.contains(ox, oz)) { keep.add(key(ox, oz)); keep.add(key(ox + o[0], oz + o[1])); }
        }
        return b;
    }

    /** Local cell of a world column on this lot. */
    public int[] local(int wx, int wz) {
        switch (f.front) {
            case 0: return new int[]{wx - f.x0, wz - f.z0};
            case 2: return new int[]{wx - f.x0, f.z1 - wz};
            case 1: return new int[]{wz - f.z0, f.x1 - wx};
            default: return new int[]{wz - f.z0, wx - f.x0};
        }
    }

    /** Paves and keeps clear a walk from a building's main entrance to the street edge of the lot. */
    public void walkToStreet(MapData.Building b, int id) {
        int[] e = local(b.entryX, b.entryZ);
        path(e[0], e[1], e[0], 0, id, 1);
    }

    public void post(String name, String kind, String faction, int a, int b, int y) { if (in(a, b)) g.post(name, kind, faction, x(a, b), y, z(a, b)); }

    public void tree(int a, int b, int h, boolean dead) {
        if (a < 3 || b < 3 || a > A - 4 || b > B - 4 || !open(a, b)) return;
        for (int da = -1; da <= 1; da++) for (int db = -1; db <= 1; db++) if (kept(a + da, b + db)) return;
        set(a, b, G - 1, Block.DIRT);
        g.tree(x(a, b), G, z(a, b), h, dead);
    }

    public void lampPost(int a, int b) {
        if (!open(a, b)) return;
        for (int y = G; y <= G + 4; y++) set(a, b, y, Block.LAMP_POST);
        if (g.inClip(x(a, b), z(a, b))) g.lamp(x(a, b), G + 5, z(a, b));
    }

    /**
     * A guard tower that belongs where it stands: four legs on the lot's ground, a ladder up the inside, a
     * decked and sandbagged platform under a roof. Returns the platform's standing height.
     */
    public int tower(int a, int b, int h, String name, String faction) {
        int top = G + h;
        for (int da = 0; da <= 2; da += 2) for (int db = 0; db <= 2; db += 2) for (int y = G; y < top; y++) set(a + da, b + db, y, Block.STEEL_BEAM);
        for (int y = G; y <= top; y++) set(a + 1, b, y, Block.LADDER);
        for (int da = -1; da <= 3; da++) for (int db = -1; db <= 3; db++) {
            if (!(da == 1 && db == 0)) set(a + da, b + db, top, Block.GRATE);
            boolean rim = da == -1 || db == -1 || da == 3 || db == 3;
            if (rim && !(da == 1 && db == -1)) set(a + da, b + db, top + 1, Block.SANDBAG);
            if (rim && (da == -1 || da == 3) && (db == -1 || db == 3)) { set(a + da, b + db, top + 2, Block.STEEL_BEAM); set(a + da, b + db, top + 3, Block.STEEL_BEAM); }
            set(a + da, b + db, top + 4, Block.CORRUGATED);
        }
        set(a + 1, b + 1, top + 3, Block.RED_LIGHT);
        for (int da = -1; da <= 3; da++) for (int db = -1; db <= 3; db++) keep(a + da, b + db);
        if (name != null) post(name, "tower", faction, a + 1, b + 1, top + 1);
        return top + 1;
    }

    /** Sandbag firing position: a low L of bags with a gap, facing local direction (da, db). */
    public void sandbags(int a, int b, int len, boolean alongA) {
        for (int k = 0; k < len; k++) {
            int pa = alongA ? a + k : a, pb = alongA ? b : b + k;
            if (!open(pa, pb)) continue;
            set(pa, pb, G, Block.SANDBAG);
            if (k % 3 != 1 && air(pa, pb, G + 1)) set(pa, pb, G + 1, Block.SANDBAG);
        }
    }

    public void tent(int a, int b, int cloth) {
        for (int da = 0; da < 5; da++) for (int db = 0; db < 4; db++) if (!open(a + da, b + db)) return;
        for (int db = 0; db < 4; db++) {
            set(a, b + db, G, cloth); set(a + 4, b + db, G, cloth);
            set(a + 1, b + db, G + 1, cloth); set(a + 3, b + db, G + 1, cloth);
            set(a + 2, b + db, G + 2, cloth);
        }
        set(a + 1, b + 3, G, cloth); set(a + 3, b + 3, G, cloth); set(a + 2, b + 3, G, cloth); set(a + 2, b + 3, G + 1, cloth);
        set(a + 2, b + 2, G, Block.BED);
        if (r.chance(0.6)) g.container(x(a + 1, b + 2), G, z(a + 1, b + 2), r.chance(0.5) ? Block.CRATE : Block.LOCKER, null);
    }
}
