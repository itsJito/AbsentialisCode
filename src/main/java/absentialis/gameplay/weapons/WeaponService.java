package absentialis.gameplay.weapons;

import absentialis.engine.util.Rng;
import absentialis.gameplay.inventory.Container;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;

/**
 * Weapon state rules that are independent of rendering: degradation per shot, jams, cleaning and
 * repair, chambering, and the tactical/empty reload split (addendum P2.2). All round movements
 * conserve quantity: a round is always in exactly one of loose stack, magazine, chamber or spent.
 */
public final class WeaponService {
    private WeaponService() {}

    /** Environment wear multipliers (rain and sewers accelerate decay). */
    public static float environmentWear(boolean rain, boolean sewer, boolean wilderness) {
        float m = 1;
        if (rain) m += 0.6f;
        if (sewer) m += 1.0f;
        if (wilderness) m += 0.3f;
        return m;
    }

    /** Wear applied per shot on the 0-100 scale. */
    public static void wearShot(ItemInstance w, Gunsmith.Stats s, float envMul) {
        float perShot = w.def().weapon.wear * 100f * s.wearMul * envMul;
        w.setCondition(w.condition() - perShot);
    }

    /** Passive environmental decay per second carried (rust in rain / sewer water). */
    public static void wearCarried(ItemInstance w, float dt, float envMul) {
        if (envMul <= 1f) return;
        w.setCondition(w.condition() - (envMul - 1f) * 0.004f * dt);
    }

    /** Rounds the trigger can fire right now. */
    public static boolean canFire(ItemInstance w) {
        ItemDef.Weapon d = w.def().weapon;
        if (w.jammed) return false;
        return d.internal() ? w.rounds > 0 : w.chambered;
    }

    /**
     * Fires one round: removes it from chamber/tube, cycles the next round, applies wear and may
     * jam. Returns the ammo def fired, or null if nothing fired.
     */
    public static ItemDef fire(ItemInstance w, Gunsmith.Stats s, float envMul, Rng rng) {
        ItemDef.Weapon d = w.def().weapon;
        if (!canFire(w)) return null;
        String ammo;
        if (d.internal()) {
            ammo = w.ammoId;
            w.rounds--;
            if (w.rounds == 0) w.ammoId = null;
        } else {
            ammo = w.ammoId;
            w.chambered = false;
            w.ammoId = null;
            if (!"bolt".equals(w.fireModeName()) && !"pump".equals(w.fireModeName())) chamberFromMag(w);
        }
        wearShot(w, s, envMul);
        if (rng.chance(s.jamChance)) w.jammed = true;
        return Content.def(ammo);
    }

    /** Moves the top round of the magazine into the empty chamber. */
    public static boolean chamberFromMag(ItemInstance w) {
        if (w.chambered || w.mag == null || w.mag.rounds <= 0) return false;
        w.mag.rounds--;
        w.ammoId = w.mag.ammoId;
        w.chambered = true;
        if (w.mag.rounds == 0) w.mag.ammoId = null;
        return true;
    }

    /** Clearing a jam ejects the stuck round (it is lost) and re-chambers. */
    public static void clearJam(ItemInstance w) {
        if (!w.jammed) return;
        w.jammed = false;
        if (w.def().weapon.internal()) return;
        w.chambered = false;
        w.ammoId = null;
        chamberFromMag(w);
    }

    /** Reload outcome descriptor for animation timing. */
    public record Reload(boolean tactical, float time, ItemInstance oldMag, String msg) {}

    /**
     * Swaps in the given magazine. Tactical reload (round chambered) keeps the chambered round and
     * is faster; an empty reload racks the action (chambering from the new magazine).
     * The old magazine is stowed into 'stow' if possible, otherwise returned for dropping to the
     * ground as an ItemInstance with its remaining rounds.
     */
    public static Reload reloadMag(ItemInstance w, ItemInstance newMag, Container stow, float baseTime, boolean speedDrop) {
        ItemDef.Weapon d = w.def().weapon;
        if (d.internal() || newMag == null || newMag.def().magazine == null || !newMag.def().magazine.magType.equals(d.magType))
            return new Reload(false, 0, null, "No compatible magazine");
        boolean tactical = w.chambered;
        ItemInstance old = w.mag;
        ItemInstance dropped = null;
        // detach the new mag from its grid first so its cells can take the old one
        Container from = newMag.container;
        int fx = newMag.x, fy = newMag.y;
        InventoryService.release(newMag);
        if (old != null) {
            old.parent = null;
            w.mag = null;
            boolean stowed = false;
            if (!speedDrop) {
                if (from != null && from.fits(fx, fy, old.baseW(), old.baseH(), null)) { InventoryService.move(old, from, fx, fy, false); stowed = old.container != null; }
                if (!stowed && stow != null) stowed = InventoryService.add(old, stow).ok();
            }
            if (!stowed) dropped = old;
        }
        w.mag = newMag;
        newMag.parent = w;
        if (!tactical) chamberFromMag(w);
        float t = baseTime * (tactical ? 0.8f : 1.15f) * (speedDrop ? 0.75f : 1f);
        return new Reload(tactical, t, dropped, tactical ? "Tactical reload" : "Empty reload - racking");
    }

    /** Loads one loose round into an internal magazine (revolver / shotgun / bolt). */
    public static boolean loadOne(ItemInstance w, ItemInstance ammoStack) {
        ItemDef.Weapon d = w.def().weapon;
        if (!d.internal() || ammoStack == null || ammoStack.def().ammo == null || !ammoStack.def().ammo.caliber.equals(d.caliber)) return false;
        if (w.rounds >= w.capacity()) return false;
        if (w.rounds > 0 && w.ammoId != null && !w.ammoId.equals(ammoStack.defId)) return false;
        if (!InventoryService.consume(ammoStack, 1)) return false;
        w.rounds++;
        w.ammoId = ammoStack.defId;
        return true;
    }

    /** Magazine-style reload: tops the weapon up from loose stacks of its ammunition. Returns rounds loaded. */
    public static int fill(ItemInstance w, java.util.List<ItemInstance> stacks) {
        ItemDef.Weapon d = w.def().weapon;
        int loaded = 0;
        for (ItemInstance st : new java.util.ArrayList<>(stacks)) {
            int room = w.capacity() - w.rounds;
            if (room <= 0) break;
            if (st.def().ammo == null || !st.def().ammo.caliber.equals(d.caliber)) continue;
            int n = Math.min(room, st.qty);
            if (!InventoryService.consume(st, n)) continue;
            w.rounds += n;
            w.ammoId = st.defId;
            loaded += n;
        }
        return loaded;
    }

    // ------------------------------------------------------------------ bench
    /** Cleaning with gun oil: +25 condition (up to max). Bench-only below 25 (addendum). */
    public static String clean(ItemInstance w, ItemInstance oil, boolean atBench) {
        if (oil == null || !"mat.gun_oil".equals(oil.defId)) return "Needs gun oil";
        if (!atBench && w.condition() < 25) return "Too far gone - needs a workbench";
        if (w.condition() >= w.maxDurability * 100f - 0.5f) return "Already clean";
        InventoryService.consume(oil, 1);
        w.setCondition(w.condition() + 25);
        w.jammed = false;
        return null;
    }

    /** Full repair with a kit: restores to max but each repair permanently lowers max by 5. */
    public static String repair(ItemInstance w, ItemInstance kit) {
        if (kit == null || !"kit.repair".equals(kit.defId)) return "Needs a repair kit";
        if (w.maxDurability <= 0.3f) return "Receiver is worn out - cannot repair further";
        InventoryService.consume(kit, 1);
        w.maxDurability = Math.max(0.3f, w.maxDurability - 0.05f);
        w.durability = w.maxDurability;
        w.jammed = false;
        return null;
    }
}
