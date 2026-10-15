package absentialis.world.gen;

import absentialis.entity.Physics;
import absentialis.world.Block;
import absentialis.world.MapData;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.List;

/**
 * Post-generation placement audit (iteration P1): every prop must stand on something, and every
 * node of the street graph must be a place an agent can actually stand. Floating props are lowered
 * onto their support (or removed when there is none), blocked nodes are cleared or moved to the
 * nearest free cell. The counts are kept on the map so the quality gate can assert them.
 */
public final class Validation {
    private Validation() {}

    /** Props: things that rest on a surface. Hanging decor (laundry, wires, lamps, signs) is exempt by design. */
    public static boolean prop(int id) {
        if (id == 0) return false;
        Block b = Block.ALL[id];
        if (b.is(Block.CONTAINER)) return true;
        return id == Block.BARREL || id == Block.BARREL_FIRE || id == Block.BARRIER || id == Block.SANDBAG || id == Block.TABLE || id == Block.CHAIR || id == Block.SOFA
                || id == Block.BED || id == Block.HOSPITAL_BED || id == Block.STOVE || id == Block.TOILET || id == Block.WASHER || id == Block.TIRE || id == Block.PEW
                || id == Block.TOMBSTONE || id == Block.COFFIN || id == Block.MACHINE || id == Block.GENERATOR || id == Block.TRANSFORMER || id == Block.CONSOLE
                || id == Block.CAR_RED || id == Block.CAR_BLUE || id == Block.CAR_WHITE || id == Block.CAR_RUST || id == Block.CAR_OLIVE || id == Block.HESCO;
    }

    private static boolean supports(int id) {
        if (id == 0) return false;
        // anything structural carries a prop (grates, fences and slabs included); soft hanging decor does not
        return !(id == Block.LAUNDRY || id == Block.WIRES || id == Block.COBWEB || id == Block.TALL_GRASS || Block.leaf(id) || id == Block.EMBER);
    }

    public static void run(World w) {
        MapData m = w.map;
        int grounded = 0, removed = 0;
        // ---- props: bottom-up so a stack settles as a stack
        for (int x = 1; x < World.W - 1; x++) for (int z = 1; z < World.D - 1; z++) {
            int top = Math.min(World.H - 2, w.heightAt(x, z) + 2);
            for (int y = 2; y <= top; y++) {
                int id = w.get(x, y, z);
                if (!prop(id) || supports(w.get(x, y - 1, z))) continue;
                // vehicles and machines are multi-block: supported if any neighbour of the same kind is
                if (sameBeside(w, x, y, z, id)) continue;
                int ny = y;
                while (ny > 2 && !supports(w.get(x, ny - 1, z)) && y - ny < 8) ny--;
                w.setRaw(x, y, z, Block.AIR);
                if (supports(w.get(x, ny - 1, z)) && w.get(x, ny, z) == 0) { w.setRaw(x, ny, z, id); dropLoot(m, x, ny, z); moveLoot(m, x, y, z, ny); grounded++; }
                else { dropLoot(m, x, y, z); removed++; }
            }
        }
        m.propsGrounded = grounded;
        m.propsRemoved = removed;
        // loot records must point at a container that is still there (damage carving removes some), one record per cell
        java.util.Set<Long> seen = new java.util.HashSet<>();
        int before = m.loot.size();
        for (int i = m.loot.size() - 1; i >= 0; i--) {
            MapData.LootSpot l = m.loot.get(i);
            int id = w.get(l.x, l.y, l.z);
            if (id == 0 || !Block.ALL[id].is(Block.CONTAINER) || !seen.add(l.id)) m.loot.remove(i);
        }
        m.lootPruned = before - m.loot.size();
        // ---- street graph: every node standable
        int fixed = 0, blocked = 0;
        for (int i = 0; i < m.nodes.size(); i++) {
            MapData.Node n = m.nodes.get(i);
            if (standable(w, n.x, n.y, n.z)) continue;
            // a prop parked on the node: clear it
            for (int y = n.y; y <= n.y + 1; y++) { int id = w.get(n.x, y, n.z); if (prop(id) || id != 0 && !Block.ALL[id].solid()) { dropLoot(m, n.x, y, n.z); w.setRaw(n.x, y, n.z, Block.AIR); } }
            if (standable(w, n.x, n.y, n.z)) { fixed++; continue; }
            int[] spot = nearestStandable(w, n.x, n.y, n.z, 6);
            if (spot == null) { blocked++; continue; }
            MapData.Node moved = new MapData.Node(n.id, spot[0], spot[1], spot[2], n.district);
            moved.links.addAll(n.links);
            m.nodes.set(i, moved);
            fixed++;
        }
        m.nodesFixed = fixed;
        m.nodesBlocked = blocked;
        m.propsFloating = countFloating(w);
        System.out.printf("[Gen] Placement audit: %d props grounded, %d removed, %d floating left; %d stale loot records pruned; %d nav nodes repaired, %d blocked%n", grounded, removed, m.propsFloating, m.lootPruned, fixed, blocked);
    }

    private static boolean sameBeside(World w, int x, int y, int z, int id) {
        if (!(id == Block.CAR_RED || id == Block.CAR_BLUE || id == Block.CAR_WHITE || id == Block.CAR_RUST || id == Block.CAR_OLIVE || id == Block.MACHINE || id == Block.GENERATOR || id == Block.TRANSFORMER || id == Block.SANDBAG || id == Block.HESCO)) return false;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { int o = w.get(x + d[0], y, z + d[1]); if (o == id && supports(w.get(x + d[0], y - 1, z + d[1]))) return true; }
        return false;
    }

    public static boolean standable(World w, int x, int y, int z) {
        return w.solid(x, y - 1, z) && Physics.boxFree(w, x + 0.5f, y, z + 0.5f, 0.3f, 1.8f);
    }

    private static int[] nearestStandable(World w, int x, int y, int z, int r) {
        for (int d = 1; d <= r; d++) for (int dx = -d; dx <= d; dx++) for (int dz = -d; dz <= d; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != d) continue;
            for (int dy = 0; dy <= 1; dy++) if (standable(w, x + dx, y + dy, z + dz)) return new int[]{x + dx, y + dy, z + dz};
        }
        return null;
    }

    /** Props with nothing under them after the audit (should be zero). */
    public static int countFloating(World w) {
        int n = 0;
        for (int x = 1; x < World.W - 1; x++) for (int z = 1; z < World.D - 1; z++) {
            int top = Math.min(World.H - 2, w.heightAt(x, z) + 2);
            for (int y = 2; y <= top; y++) { int id = w.get(x, y, z); if (prop(id) && !supports(w.get(x, y - 1, z)) && !sameBeside(w, x, y, z, id)) n++; }
        }
        return n;
    }

    private static void moveLoot(MapData m, int x, int y, int z, int ny) {
        List<MapData.LootSpot> add = new ArrayList<>();
        for (int i = m.loot.size() - 1; i >= 0; i--) {
            MapData.LootSpot l = m.loot.get(i);
            if (l.x != x || l.y != y || l.z != z) continue;
            MapData.LootSpot n = new MapData.LootSpot(x, ny, z, l.table, l.district);
            n.named = l.named; n.tag = l.tag;
            m.loot.remove(i);
            add.add(n);
        }
        m.loot.addAll(add);
    }

    private static void dropLoot(MapData m, int x, int y, int z) { m.loot.removeIf(l -> l.x == x && l.y == y && l.z == z); }
}
