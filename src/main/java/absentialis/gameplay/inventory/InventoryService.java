package absentialis.gameplay.inventory;

import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.gameplay.weapons.Gunsmith;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The only code allowed to mutate item locations (GDD 8.5/8.6, addendum P2.3). Every operation is
 * an atomic transaction: it validates the whole change first and only then mutates, so a rejected
 * request leaves every container, slot and pouch exactly as it was. Quantities are conserved:
 * stacking 30/30 + 15/30 never produces 45, it moves only what fits.
 */
public final class InventoryService {
    private InventoryService() {}

    public record Result(boolean ok, String msg) {
        public static final Result OK = new Result(true, "");
        static Result no(String m) { return new Result(false, m); }
    }

    /** Optional listener used for domain events / telemetry (from, to, item, qty). */
    public interface Listener { void moved(ItemInstance it, String from, String to, int qty); }
    public static Listener listener;

    // ------------------------------------------------------------------ location helpers
    public static String where(ItemInstance it) {
        if (it.container != null) return it.container.id;
        if (it.parent != null) return "in:" + it.parent.uid;
        if (it.slot != null) return it.slot;
        return "world";
    }

    /** Removes an item from its current location. Caller has validated the full transaction. */
    static void detach(ItemInstance it) {
        if (it.container != null) { it.container.take(it); }
        else if (it.parent != null) {
            ItemInstance p = it.parent;
            if (p.mag == it) p.mag = null;
            p.attachments.values().remove(it);
        } else if (it.slotOwner instanceof Loadout l && it.slot != null) {
            if (it.slot.startsWith("charm")) { for (int k = 0; k < l.charms.length; k++) if (l.charms[k] == it) l.charms[k] = null; }
            else if (it.slot.startsWith("pouch:")) { List<ItemInstance> p = l.pouch(it.slot.substring(6)); if (p != null) p.remove(it); }
            else if (l.slots.get(it.slot) == it) l.slots.put(it.slot, null);
        }
        it.detachLocation();
    }

    private static void notify(ItemInstance it, String from, String to, int qty) { if (listener != null) listener.moved(it, from, to, qty); }

    // ------------------------------------------------------------------ grid moves
    /** Moves (or merges) an item to a grid cell. Merging moves only what fits. */
    public static Result move(ItemInstance it, Container to, int x, int y, boolean rot) {
        if (it == null || to == null) return Result.no("Nothing to move");
        if (it.grid == to) return Result.no("Cannot place a container inside itself");
        ItemInstance target = to.at(x, y);
        if (target != null && target != it) {
            Result r = combine(it, target);
            if (r.ok() || !r.msg().isEmpty()) return r;
        }
        int iw = rot ? it.baseH() : it.baseW(), ih = rot ? it.baseW() : it.baseH();
        if (!to.fits(x, y, iw, ih, it)) return Result.no("No room there");
        String from = where(it);
        detach(it);
        to.put(it, x, y, rot);
        notify(it, from, to.id, it.qty);
        return Result.OK;
    }

    /** Places an item anywhere legal in the container (stacking into partial stacks first). */
    public static Result add(ItemInstance it, Container to) {
        ItemDef d = it.def();
        if (d.stackable()) {
            // validate: total room in partial stacks + a free cell for any remainder
            int room = 0;
            for (ItemInstance o : to.items) if (o != it && o.defId.equals(it.defId) && o.ammoId == null) room += d.maxStack - o.qty;
            if (room < it.qty && to.findSpace(it) == null) return Result.no("Not enough room");
            String from = where(it);
            int moved = 0;
            for (ItemInstance o : to.items) {
                if (o == it || !o.defId.equals(it.defId)) continue;
                int n = Math.min(d.maxStack - o.qty, it.qty);
                if (n <= 0) continue;
                o.qty += n; it.qty -= n; moved += n;
                if (it.qty == 0) { detach(it); notify(it, from, to.id, moved); return Result.OK; }
            }
            int[] s = to.findSpace(it);
            detach(it);
            to.put(it, s[0], s[1], s[2] == 1);
            notify(it, from, to.id, moved + it.qty);
            return Result.OK;
        }
        int[] s = to.findSpace(it);
        if (s == null) return Result.no("Not enough room");
        String from = where(it);
        detach(it);
        to.put(it, s[0], s[1], s[2] == 1);
        notify(it, from, to.id, it.qty);
        return Result.OK;
    }

    /** Stores an item in a loadout: pouch items go to their no-space pouch, the rest into the case. */
    public static Result store(ItemInstance it, Loadout l) {
        List<ItemInstance> pouch = l.pouchFor(it.def());
        if (pouch != null) {
            String from = where(it);
            // stack into an existing pouch entry
            if (it.def().stackable()) for (ItemInstance o : pouch) if (o != it && o.defId.equals(it.defId)) {
                detach(it);
                o.qty += it.qty;
                notify(it, from, "pouch", it.qty);
                return Result.OK;
            }
            detach(it);
            it.slot = "pouch:" + Loadout.pouchName(it.def());
            it.slotOwner = l;
            pouch.add(it);
            notify(it, from, it.slot, it.qty);
            return Result.OK;
        }
        Result r = add(it, l.caseGrid);
        if (!r.ok() && l.packGrid() != null && it.grid == null) return add(it, l.packGrid());
        return r;
    }

    /** Splits n units off a stack into a new instance placed at (x,y) in the target. */
    public static Result split(ItemInstance it, int n, Container to, int x, int y) {
        if (!it.def().stackable() || n <= 0 || n >= it.qty) return Result.no("Cannot split");
        ItemInstance part = new ItemInstance(it.defId);
        part.qty = n;
        part.origin = it.origin;
        if (!to.fits(x, y, part.baseW(), part.baseH(), null)) return Result.no("No room there");
        it.qty -= n;
        to.put(part, x, y, false);
        notify(part, where(it), to.id, n);
        return Result.OK;
    }

    /**
     * Drag-onto behaviours: stack merge, load ammo into a magazine or internal tube, insert a
     * magazine, fit an attachment. Returns Result("") (not ok, empty msg) when nothing applies.
     */
    public static Result combine(ItemInstance src, ItemInstance dst) {
        ItemDef s = src.def(), d = dst.def();
        if (src == dst) return new Result(false, "");
        // stack merge
        if (s.stackable() && src.defId.equals(dst.defId)) {
            int room = d.maxStack - dst.qty;
            if (room <= 0) return Result.no("Stack is full");
            int n = Math.min(room, src.qty);
            String from = where(src);
            dst.qty += n;
            src.qty -= n;
            if (src.qty == 0) detach(src);
            notify(dst, from, where(dst), n);
            return Result.OK;
        }
        // herbs: green + green, green + red
        String mix = herbMix(src.defId, dst.defId);
        if (mix != null) {
            Container at = dst.container;
            if (at == null) return Result.no("Put the herbs in your case to mix them");
            int px = dst.x, py = dst.y; boolean pr = dst.rotated;
            detach(dst);
            consume(src, 1);
            ItemInstance out = new ItemInstance(mix);
            out.origin = "mixed";
            out.foundInRaid = src.foundInRaid || dst.foundInRaid;
            at.put(out, px, py, pr);
            notify(out, "mix", at.id, 1);
            return Result.OK;
        }
        // ammo -> magazine / internal weapon
        if (s.ammo != null && (d.magazine != null || (d.weapon != null && d.weapon.internal()))) {
            String cal = d.magazine != null ? d.magazine.caliber : d.weapon.caliber;
            if (!s.ammo.caliber.equals(cal)) return Result.no("Wrong caliber (" + s.ammo.caliber + " vs " + cal + ")");
            if (dst.rounds > 0 && dst.ammoId != null && !dst.ammoId.equals(src.defId)) return Result.no("Unload the mixed rounds first");
            int room = dst.capacity() - dst.rounds;
            if (room <= 0) return Result.no("Already full");
            int n = Math.min(room, src.qty);
            dst.rounds += n;
            dst.ammoId = src.defId;
            src.qty -= n;
            if (src.qty == 0) detach(src);
            notify(src, "ammo", "in:" + dst.uid, n);
            return Result.OK;
        }
        // magazine -> weapon
        if (s.magazine != null && d.weapon != null) return insertMag(dst, src);
        // attachment -> weapon
        if (s.attachment != null && d.weapon != null) return attach(dst, src);
        return new Result(false, "");
    }

    /** Herb pairs that grind into a stronger one, or null. */
    public static String herbMix(String a, String b) {
        if (a.equals("herb.green") && b.equals("herb.green")) return "herb.mixed_gg";
        if (a.equals("herb.green") && b.equals("herb.red") || a.equals("herb.red") && b.equals("herb.green")) return "herb.mixed_gr";
        if (a.equals("herb.mixed_gr") && b.equals("herb.yellow") || a.equals("herb.yellow") && b.equals("herb.mixed_gr")) return "herb.mixed_gry";
        return null;
    }

    // ------------------------------------------------------------------ magazines & ammo
    public static Result insertMag(ItemInstance weapon, ItemInstance mag) {
        ItemDef w = weapon.def();
        if (w.weapon == null || w.weapon.internal()) return Result.no("Weapon takes loose rounds");
        if (!mag.def().magazine.magType.equals(w.weapon.magType)) return Result.no("Magazine does not fit " + w.name);
        ItemInstance old = weapon.mag;
        Container back = mag.container;
        int bx = mag.x, by = mag.y;
        if (old != null) {
            // the old magazine must have somewhere to go: the new magazine's cell, else anywhere in that grid
            if (back == null) return Result.no("Remove the current magazine first");
            boolean fitsSame = back.fits(bx, by, old.baseW(), old.baseH(), mag);
            if (!fitsSame && back.findSpace(old) == null) return Result.no("No room for the removed magazine");
        }
        String from = where(mag);
        detach(mag);
        if (old != null) {
            detach(old);
            if (back.fits(bx, by, old.baseW(), old.baseH(), null)) back.put(old, bx, by, false);
            else { int[] sp = back.findSpace(old); back.put(old, sp[0], sp[1], sp[2] == 1); }
        }
        weapon.mag = mag;
        mag.parent = weapon;
        notify(mag, from, "in:" + weapon.uid, 1);
        return Result.OK;
    }

    /** Removes the magazine from a weapon into a container (chambered round stays in the weapon). */
    public static Result removeMag(ItemInstance weapon, Container to) {
        ItemInstance m = weapon.mag;
        if (m == null) return Result.no("No magazine");
        int[] s = to.findSpace(m);
        if (s == null) return Result.no("No room for the magazine");
        detach(m);
        to.put(m, s[0], s[1], s[2] == 1);
        notify(m, "in:" + weapon.uid, to.id, 1);
        return Result.OK;
    }

    /** Unloads rounds from a magazine / internal weapon back into loose ammo stacks. */
    public static Result unload(ItemInstance holder, Container to) {
        if (holder.rounds <= 0 || holder.ammoId == null) return Result.no("Empty");
        ItemDef ad = Content.def(holder.ammoId);
        // validate: room for all rounds in existing stacks + free cells for new stacks
        int remaining = holder.rounds, room = 0;
        for (ItemInstance o : to.items) if (o.defId.equals(holder.ammoId)) room += ad.maxStack - o.qty;
        int needStacks = Math.max(0, (int) Math.ceil((remaining - room) / (double) ad.maxStack));
        if (needStacks > 0) {
            Container sim = simulate(to);
            for (int k = 0; k < needStacks; k++) {
                ItemInstance probe = new ItemInstance(-1 - k, holder.ammoId);
                int[] sp = sim.findSpace(probe);
                if (sp == null) return Result.no("Not enough room to unload");
                sim.put(probe, sp[0], sp[1], sp[2] == 1);
            }
        }
        for (ItemInstance o : to.items) {
            if (!o.defId.equals(holder.ammoId) || remaining == 0) continue;
            int n = Math.min(ad.maxStack - o.qty, remaining);
            o.qty += n; remaining -= n;
        }
        while (remaining > 0) {
            ItemInstance st = new ItemInstance(holder.ammoId);
            st.qty = Math.min(ad.maxStack, remaining);
            remaining -= st.qty;
            int[] sp = to.findSpace(st);
            to.put(st, sp[0], sp[1], sp[2] == 1);
        }
        notify(holder, "in:" + holder.uid, to.id, holder.rounds);
        holder.rounds = 0;
        holder.ammoId = null;
        return Result.OK;
    }

    // ------------------------------------------------------------------ gunsmith
    public static Result attach(ItemInstance weapon, ItemInstance part) {
        String why = Gunsmith.incompatibility(weapon, part);
        if (why != null) return Result.no(why);
        String socket = part.def().attachment.socket;
        ItemInstance old = weapon.attachments.get(socket);
        Container back = part.container;
        if (old != null && (back == null || (!back.fits(part.x, part.y, old.baseW(), old.baseH(), part) && back.findSpace(old) == null)))
            return Result.no("No room for the removed " + old.name());
        // the weapon may grow (barrels/suppressors add length). The part's own cells are freed by fitting
        // it, so they count as room; if the longer weapon still does not fit in place it moves to a free spot.
        Container wc = weapon.container;
        int[] spot = null;
        if (wc != null) {
            int wx = weapon.x, wy = weapon.y;
            boolean wr = weapon.rotated, pr = part.rotated;
            int ppx = part.x, ppy = part.y;
            if (back != null) back.take(part);
            wc.take(weapon);
            weapon.attachments.put(socket, part);
            int nw = wr ? weapon.baseH() : weapon.baseW(), nh = wr ? weapon.baseW() : weapon.baseH();
            spot = wc.fits(wx, wy, nw, nh, null) ? new int[]{wx, wy, wr ? 1 : 0} : wc.findSpace(weapon);
            if (old != null) weapon.attachments.put(socket, old); else weapon.attachments.remove(socket);
            wc.put(weapon, wx, wy, wr);
            if (back != null) back.put(part, ppx, ppy, pr);
            if (spot == null) return Result.no("No room in the grid for the longer weapon");
        }
        int px = part.x, py = part.y;
        String from = where(part);
        detach(part);
        if (old != null) {
            detach(old);
            if (back.fits(px, py, old.baseW(), old.baseH(), null)) back.put(old, px, py, false);
            else { int[] sp = back.findSpace(old); back.put(old, sp[0], sp[1], sp[2] == 1); }
        }
        if (wc != null) wc.take(weapon);
        weapon.attachments.put(socket, part);
        part.parent = weapon;
        if (wc != null) wc.put(weapon, spot[0], spot[1], spot[2] == 1);
        notify(part, from, "in:" + weapon.uid, 1);
        return Result.OK;
    }

    public static Result detachPart(ItemInstance weapon, String socket, Container to) {
        ItemInstance p = weapon.attachments.get(socket);
        if (p == null) return Result.no("Empty slot");
        int[] s = to.findSpace(p);
        if (s == null) return Result.no("No room");
        detach(p);
        to.put(p, s[0], s[1], s[2] == 1);
        notify(p, "in:" + weapon.uid, to.id, 1);
        return Result.OK;
    }

    // ------------------------------------------------------------------ equipment
    public static Result equip(ItemInstance it, Loadout l, String slot) {
        ItemDef d = it.def();
        it.factory = false;                     // 1.1: in the survivor's hands it is the survivor's weapon
        if (slot.startsWith("charm")) {
            int k = slot.charAt(5) - '0';
            if (d.charm == null) return Result.no("Not a charm");
            if (k < 0 || k >= Loadout.CHARM_SLOTS) return Result.no("Bad charm slot");
            ItemInstance old = l.charms[k];
            Container back = it.container;
            if (old != null && (back == null || back.findSpace(old) == null)) return Result.no("No room for the removed charm");
            String from = where(it);
            detach(it);
            if (old != null) { detach(old); int[] sp = back.findSpace(old); back.put(old, sp[0], sp[1], sp[2] == 1); }
            l.charms[k] = it; it.slot = slot; it.slotOwner = l;
            notify(it, from, slot, 1);
            return Result.OK;
        }
        if (!slot.equals(d.slot)) return Result.no(d.name + " does not go in " + slot);
        ItemInstance old = l.slots.get(slot);
        Container back = it.container;
        if (old != null) {
            if (back == null) return Result.no("Unequip the current item first");
            boolean same = back.fits(it.x, it.y, old.baseW(), old.baseH(), it) || back.fits(it.x, it.y, old.baseH(), old.baseW(), it);
            if (!same && back.findSpace(old) == null) return Result.no("No room for " + old.name());
        }
        int px = it.x, py = it.y;
        String from = where(it);
        detach(it);
        if (old != null) {
            detach(old);
            if (back.fits(px, py, old.baseW(), old.baseH(), null)) back.put(old, px, py, false);
            else if (back.fits(px, py, old.baseH(), old.baseW(), null)) back.put(old, px, py, true);
            else { int[] sp = back.findSpace(old); back.put(old, sp[0], sp[1], sp[2] == 1); }
        }
        l.slots.put(slot, it);
        it.slot = slot;
        it.slotOwner = l;
        notify(it, from, slot, 1);
        return Result.OK;
    }

    public static Result unequip(ItemInstance it, Container to) {
        int[] s = to.findSpace(it);
        if (s == null) return Result.no("No room");
        String from = where(it);
        detach(it);
        to.put(it, s[0], s[1], s[2] == 1);
        notify(it, from, to.id, 1);
        return Result.OK;
    }

    /** Shift+click: send an item to the other side (stacking first), atomically. */
    public static Result quickTransfer(ItemInstance it, Container to, Loadout ownerIfCase) {
        if (ownerIfCase != null && to == ownerIfCase.caseGrid) return store(it, ownerIfCase);
        return add(it, to);
    }

    /** Removes an item from ownership entirely (drop to world / consume / sell). */
    public static ItemInstance release(ItemInstance it) {
        String from = where(it);
        detach(it);
        notify(it, from, "world", it.qty);
        return it;
    }

    /** Consumes n units (stack) or the whole instance; returns false if not enough. */
    public static boolean consume(ItemInstance it, int n) {
        if (it.qty < n) return false;
        it.qty -= n;
        if (it.qty == 0) release(it);
        return true;
    }

    // ------------------------------------------------------------------ auto sort
    /**
     * Repacks a grid: largest footprints first, stacks merged. Computed entirely on a simulation;
     * applied only if every item still fits, so sorting can never lose an item.
     */
    public static Result autoSort(Container c) {
        // merge partial stacks first (conserving)
        Map<String, List<ItemInstance>> stacks = new HashMap<>();
        for (ItemInstance i : c.items) if (i.def().stackable()) stacks.computeIfAbsent(i.defId, k -> new ArrayList<>()).add(i);
        List<ItemInstance> sorted = new ArrayList<>(c.items);
        sorted.sort(Comparator.<ItemInstance>comparingInt(i -> -(i.baseW() * i.baseH()))
                .thenComparing(i -> i.def().category).thenComparing(i -> i.defId).thenComparingLong(i -> i.uid));
        Container sim = new Container("sim", c.w, c.h);
        int[][] pos = new int[sorted.size()][];
        for (int k = 0; k < sorted.size(); k++) {
            ItemInstance it = sorted.get(k);
            int[] best = null;
            // prefer vertical (tall) orientation for long items so rows stay tidy
            for (int pass = 0; pass < 2 && best == null; pass++) {
                boolean rot = (pass == 0) == (it.baseW() > it.baseH());
                int iw = rot ? it.baseH() : it.baseW(), ih = rot ? it.baseW() : it.baseH();
                outer:
                for (int y = 0; y + ih <= c.h; y++) for (int x = 0; x + iw <= c.w; x++) if (sim.fits(x, y, iw, ih, null)) { best = new int[]{x, y, rot ? 1 : 0}; break outer; }
            }
            if (best == null) return Result.no("Sort would not fit - nothing changed");
            ItemInstance ghost = new ItemInstance(-1000 - k, it.defId);
            ghost.attachments.putAll(it.attachments);
            ghost.x = best[0]; ghost.y = best[1]; ghost.rotated = best[2] == 1;
            sim.items.add(ghost);
            pos[k] = best;
        }
        for (int k = 0; k < sorted.size(); k++) { ItemInstance it = sorted.get(k); it.x = pos[k][0]; it.y = pos[k][1]; it.rotated = pos[k][2] == 1; }
        // stack merge after layout (frees cells, never loses quantity)
        for (List<ItemInstance> l : stacks.values()) {
            for (int a = 0; a < l.size(); a++) for (int b = a + 1; b < l.size(); b++) {
                ItemInstance x = l.get(a), y = l.get(b);
                if (x.container != c || y.container != c) continue;
                int room = x.def().maxStack - x.qty;
                int n = Math.min(room, y.qty);
                if (n <= 0) continue;
                x.qty += n; y.qty -= n;
                if (y.qty == 0) c.take(y);
            }
        }
        return Result.OK;
    }

    private static Container simulate(Container c) {
        Container s = new Container("sim", c.w, c.h);
        for (ItemInstance i : c.items) {
            ItemInstance g = new ItemInstance(-5000 - i.uid, i.defId);
            g.attachments.putAll(i.attachments);
            g.x = i.x; g.y = i.y; g.rotated = i.rotated;
            s.items.add(g);
        }
        return s;
    }

    // ------------------------------------------------------------------ audit
    /** Quantity ledger: defId -> units (stacks, loaded rounds, chambered rounds, nested items). */
    public static Map<String, Integer> ledger(List<ItemInstance> roots) {
        Map<String, Integer> m = new HashMap<>();
        List<ItemInstance> all = new ArrayList<>();
        for (ItemInstance r : roots) r.collect(all);
        for (ItemInstance i : all) {
            m.merge(i.defId, i.qty, Integer::sum);
            if (i.rounds > 0 && i.ammoId != null) m.merge(i.ammoId, i.rounds, Integer::sum);
            if (i.chambered && i.ammoId != null) m.merge(i.ammoId, 1, Integer::sum);
        }
        return m;
    }
}
