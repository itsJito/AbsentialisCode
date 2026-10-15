package absentialis.tools;

import absentialis.world.Block;
import absentialis.world.World;

/**
 * Terrain continuity measurements shared by the generation validator and the 0.5 regression gates: the height
 * of the walkable surface in every column, steps between neighbouring columns, columns with no ground at all,
 * and how those are distributed along the quarantine wall, the world edge and the chunk seams.
 */
public final class TerrainProbe {
    private TerrainProbe() {}

    /** Highest solid, non-liquid, non-foliage block of a column, or -1 when the column is empty to bedrock. */
    public static int ground(World w, int x, int z) {
        for (int y = World.H - 1; y >= 0; y--) {
            int id = w.get(x, y, z);
            if (id == 0) continue;
            Block b = Block.ALL[id];
            if (!b.solid() || b.is(Block.LIQUID) || b.mat == absentialis.world.Material.FOLIAGE || id == Block.LOG) continue;
            return y;
        }
        return -1;
    }

    /** Result of {@link #scan}: counts and the worst offenders. */
    public static final class Report {
        public int voidColumns, cliffs, seamCliffs, trenchCells, edgeCliffs, maxStep, exposedWater;
        public final java.util.List<int[]> worst = new java.util.ArrayList<>();
        /** Trench cells bucketed by distance outside the city boundary (index = blocks, last bucket = further). */
        public final int[] trenchByDist = new int[40];
        @Override public String toString() {
            return String.format("%d void columns, %d cliffs over %d blocks (max step %d), %d on chunk seams, %d trench cells, %d at the world edge, %d open water faces",
                    voidColumns, cliffs, CLIFF, maxStep, seamCliffs, trenchCells, edgeCliffs, exposedWater);
        }
    }

    /** A step between neighbouring wilderness columns taller than this is a cliff no natural slope produces. */
    public static final int CLIFF = 4;

    /**
     * Scans the wilderness (everything outside the city boundary) and the band just inside it. Man-made
     * structures are skipped by only measuring natural ground blocks.
     */
    public static Report scan(World w) {
        Report r = new Report();
        int[] h = new int[World.W * World.D];
        boolean[] natural = new boolean[World.W * World.D];
        for (int x = 0; x < World.W; x++) for (int z = 0; z < World.D; z++) {
            int g = ground(w, x, z);
            h[x * World.D + z] = g;
            if (g <= 0) { if (World.outsideDistance(x, z) > 0) { r.voidColumns++; if (r.worst.size() < 12) r.worst.add(new int[]{x, g, z, 0}); } continue; }
            int id = w.get(x, g, z);
            natural[x * World.D + z] = id == Block.GRASS || id == Block.DEAD_GRASS || id == Block.DIRT || id == Block.MUD || id == Block.GRAVEL || id == Block.ASH || id == Block.SAND || id == Block.STONE_DARK;
        }
        for (int x = 1; x < World.W - 1; x++) for (int z = 1; z < World.D - 1; z++) {
            float out = World.outsideDistance(x, z);
            if (out <= 0 || !natural[x * World.D + z]) continue;
            int c = h[x * World.D + z];
            int lower = 0, higherN = 0;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int n = h[(x + d[0]) * World.D + z + d[1]];
                if (n < 0) continue;
                int step = Math.abs(n - c);
                if (natural[(x + d[0]) * World.D + z + d[1]] && step > CLIFF) {
                    r.cliffs++;
                    r.maxStep = Math.max(r.maxStep, step);
                    boolean seam = d[0] != 0 ? ((x & 15) == (d[0] > 0 ? 15 : 0)) : ((z & 15) == (d[1] > 0 ? 15 : 0));
                    if (seam) r.seamCliffs++;
                    if (r.worst.size() < 12) r.worst.add(new int[]{x, c, z, step});
                }
                if (n - c >= 2) higherN++;
                if (c - n >= 2) lower++;
            }
            // a trench cell: the ground on both sides of at least one axis stands two or more above it
            int xa = h[(x + 1) * World.D + z], xb = h[(x - 1) * World.D + z], za = h[x * World.D + z + 1], zb = h[x * World.D + z - 1];
            // ... and those sides are ground too: a gateway between two wall ends, or grass under a mast, is not a trench
            boolean nx = natural[(x + 1) * World.D + z] && natural[(x - 1) * World.D + z], nz = natural[x * World.D + z + 1] && natural[x * World.D + z - 1];
            if (nx && xa - c >= 2 && xb - c >= 2 || nz && za - c >= 2 && zb - c >= 2) { r.trenchCells++; if (System.getenv("ABS_TRENCH") != null) System.out.println("    trench " + x + "," + c + "," + z + " neighbours x " + xa + "/" + xb + " z " + za + "/" + zb + " top " + Block.ALL[w.get(x, c, z)].name); r.trenchByDist[Math.min(r.trenchByDist.length - 1, (int) out)]++; }
            // open water: a liquid block with air beside it would hang as a wall of water
            for (int y = c + 1; y < World.H - 1; y++) {
                int id = w.get(x, y, z);
                if (id == 0 || !Block.ALL[id].is(Block.LIQUID)) break;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (w.get(x + d[0], y, z + d[1]) == 0) { r.exposedWater++; break; }
            }
        }
        // the world edge: the outermost ring of columns must hold ground at the height of its inner neighbour
        for (int i = 0; i < World.W; i++) for (int side = 0; side < 4; side++) {
            int x = side == 0 ? 0 : side == 1 ? World.W - 1 : i, z = side == 2 ? 0 : side == 3 ? World.D - 1 : i;
            int ix = Math.max(1, Math.min(World.W - 2, x)), iz = Math.max(1, Math.min(World.D - 2, z));
            if (h[x * World.D + z] < 0 || Math.abs(h[x * World.D + z] - h[ix * World.D + iz]) > CLIFF) r.edgeCliffs++;
        }
        return r;
    }
}
