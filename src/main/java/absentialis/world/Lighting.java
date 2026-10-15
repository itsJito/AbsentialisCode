package absentialis.world;

/**
 * Two-channel voxel light (sky + block emitters) with flood-fill propagation. Full computation runs
 * once after generation; runtime changes (destruction, doors, power) relight a bounded box.
 */
public final class Lighting {
    private final World w;
    private int[] q = new int[1 << 20];
    private int head, tail;

    public Lighting(World w) { this.w = w; }

    private void push(int i) {
        if (tail == q.length) {
            if (head > 0) { System.arraycopy(q, head, q, 0, tail - head); tail -= head; head = 0; }
            else q = java.util.Arrays.copyOf(q, q.length * 2);
            if (tail == q.length) q = java.util.Arrays.copyOf(q, q.length * 2);
        }
        q[tail++] = i;
    }

    private static boolean blocksLight(int id) {
        Block b = Block.ALL[id];
        return b != null && b.opaque();
    }

    public void computeAll() {
        computeBox(0, 0, World.W - 1, World.D - 1, false);
    }

    public void relightAround(int x, int y, int z) {
        computeBox(x - 15, z - 15, x + 15, z + 15, true);
    }

    public void relightBox(int x0, int z0, int x1, int z1) {
        computeBox(x0 - 15, z0 - 15, x1 + 15, z1 + 15, true);
    }

    private void computeBox(int x0, int z0, int x1, int z1, boolean seedBorder) {
        x0 = Math.max(0, x0); z0 = Math.max(0, z0); x1 = Math.min(World.W - 1, x1); z1 = Math.min(World.D - 1, z1);
        final int H = World.H, D = World.D;
        byte[] bl = w.blocks, li = w.light;
        head = tail = 0;
        // 1) column sky pass + emitters
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            int base = (x * D + z) * H;
            int sky = 15;
            for (int y = H - 1; y >= 0; y--) {
                int id = bl[base + y] & 0xFF;
                if (blocksLight(id)) sky = 0;
                else if (sky > 0 && id != 0) {
                    Block b = Block.ALL[id];
                    if (b.is(Block.LIQUID) || Block.leaf(id)) sky = Math.max(0, sky - 2);
                }
                int em = Block.ALL[id].light;
                li[base + y] = (byte) ((sky << 4) | em);
                if (em > 0) push(base + y);
            }
        }
        // 2) seed cells adjacent to darker non-opaque neighbours
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            int base = (x * D + z) * H;
            for (int y = 0; y < H; y++) {
                int s = (li[base + y] >> 4) & 15;
                if (s < 2) continue;
                boolean edge = false;
                if (x > 0) edge |= dim(base - D * H + y, s);
                if (x < World.W - 1) edge |= dim(base + D * H + y, s);
                if (z > 0) edge |= dim(base - H + y, s);
                if (z < World.D - 1) edge |= dim(base + H + y, s);
                if (y > 0 && s < 15) edge |= dim(base + y - 1, s);
                if (edge) push(base + y);
            }
        }
        // 3) border ring: existing light flows inward
        if (seedBorder) {
            for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) {
                if (x >= x0 && x <= x1 && z >= z0 && z <= z1) continue;
                if (x < 0 || z < 0 || x >= World.W || z >= World.D) continue;
                int base = (x * D + z) * H;
                for (int y = 0; y < H; y++) if (li[base + y] != 0) push(base + y);
            }
        }
        // 4) flood fill
        int bx0 = seedBorder ? x0 : 0, bx1 = seedBorder ? x1 : World.W - 1, bz0 = seedBorder ? z0 : 0, bz1 = seedBorder ? z1 : World.D - 1;
        while (head < tail) {
            int i = q[head++];
            int v = li[i];
            int s = (v >> 4) & 15, b = v & 15;
            if (s <= 1 && b <= 1) continue;
            int y = i % H, col = i / H, z = col % D, x = col / D;
            if (x > bx0) spread(i - D * H, s, b);
            if (x < bx1) spread(i + D * H, s, b);
            if (z > bz0) spread(i - H, s, b);
            if (z < bz1) spread(i + H, s, b);
            if (y > 0) spread(i - 1, s, b);
            if (y < H - 1) spread(i + 1, s, b);
        }
        head = tail = 0;
    }

    private boolean dim(int ni, int s) {
        int id = w.blocks[ni] & 0xFF;
        if (blocksLight(id)) return false;
        return ((w.light[ni] >> 4) & 15) < s - 1;
    }

    private void spread(int ni, int s, int b) {
        int id = w.blocks[ni] & 0xFF;
        if (blocksLight(id)) return;
        int v = w.light[ni];
        int ns = (v >> 4) & 15, nb = v & 15;
        int ts = Math.max(ns, s - 1), tb = Math.max(nb, b - 1);
        if (ts != ns || tb != nb) {
            w.light[ni] = (byte) ((ts << 4) | tb);
            push(ni);
        }
    }
}
