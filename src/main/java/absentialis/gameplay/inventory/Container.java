package absentialis.gameplay.inventory;

import absentialis.gameplay.items.ItemInstance;
import java.util.ArrayList;
import java.util.List;

/**
 * A width x height spatial grid (GDD 8.4). Placement succeeds only when the footprint is inside the
 * grid and none of its cells hold another item (the moved item's own cells are ignored). The item
 * list is the source of truth; occupancy is always derived from it, never from UI state.
 */
public final class Container {
    public final String id;
    public int w, h;
    public final List<ItemInstance> items = new ArrayList<>();
    public String label = "";

    public Container(String id, int w, int h) { this.id = id; this.w = w; this.h = h; }

    /** True when (x,y) with the given footprint is free; 'ignore' is the item being moved. */
    public boolean fits(int x, int y, int iw, int ih, ItemInstance ignore) {
        if (x < 0 || y < 0 || x + iw > w || y + ih > h) return false;
        for (ItemInstance o : items) {
            if (o == ignore) continue;
            if (x < o.x + o.w() && o.x < x + iw && y < o.y + o.h() && o.y < y + ih) return false;
        }
        return true;
    }

    public boolean fits(ItemInstance it, int x, int y, boolean rotated) {
        int iw = rotated ? it.baseH() : it.baseW(), ih = rotated ? it.baseW() : it.baseH();
        return fits(x, y, iw, ih, it);
    }

    /** Finds the first free position (row-major), trying both orientations; returns {x,y,rot} or null. */
    public int[] findSpace(ItemInstance it) {
        for (int pass = 0; pass < 2; pass++) {
            boolean rot = pass == 1;
            int iw = rot ? it.baseH() : it.baseW(), ih = rot ? it.baseW() : it.baseH();
            if (pass == 1 && iw == ih) break;
            for (int y = 0; y + ih <= h; y++) for (int x = 0; x + iw <= w; x++) if (fits(x, y, iw, ih, it)) return new int[]{x, y, rot ? 1 : 0};
        }
        return null;
    }

    public ItemInstance at(int cx, int cy) {
        for (ItemInstance o : items) if (cx >= o.x && cx < o.x + o.w() && cy >= o.y && cy < o.y + o.h()) return o;
        return null;
    }

    /** Low-level insert used by InventoryService and loaders; caller has validated the fit. */
    void put(ItemInstance it, int x, int y, boolean rot) {
        it.detachLocation();
        it.x = x; it.y = y; it.rotated = rot;
        it.container = this;
        it.factory = false;
        items.add(it);
    }

    void take(ItemInstance it) {
        items.remove(it);
        if (it.container == this) it.container = null;
    }

    /** Restores a deserialized item at its saved position (or anywhere legal). Returns false if no space. */
    public boolean restore(ItemInstance it) {
        boolean rot = it.rotated;
        if (fits(it, it.x, it.y, rot)) { put(it, it.x, it.y, rot); return true; }
        int[] s = findSpace(it);
        if (s == null) return false;
        put(it, s[0], s[1], s[2] == 1);
        return true;
    }

    public int freeCells() {
        int used = 0;
        for (ItemInstance o : items) used += o.w() * o.h();
        return w * h - used;
    }

    public float mass() { float m = 0; for (ItemInstance i : items) m += i.mass(); return m; }

    /** Structural self-check: in-bounds, no overlaps, back-pointers consistent. */
    public String verify() {
        for (ItemInstance a : items) {
            if (a.container != this) return id + ": back-pointer mismatch for " + a;
            if (a.x < 0 || a.y < 0 || a.x + a.w() > w || a.y + a.h() > h) return id + ": out of bounds " + a;
            for (ItemInstance b : items) {
                if (a == b) continue;
                if (a.x < b.x + b.w() && b.x < a.x + a.w() && a.y < b.y + b.h() && b.y < a.y + a.h()) return id + ": overlap " + a + " / " + b;
            }
        }
        return null;
    }
}
