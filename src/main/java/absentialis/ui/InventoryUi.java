package absentialis.ui;

import static org.lwjgl.glfw.GLFW.*;

import absentialis.gameplay.inventory.Container;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.inventory.Loadout;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.gameplay.weapons.Gunsmith;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Shared inventory interaction layer for stash, case, loot containers and gear slots (addendum
 * P1 Inventory): drag & drop with a legal-placement preview (green fits, amber combines, red
 * rejects), R rotates while dragging, shift+click quick-transfers, right-click opens actions,
 * hover shows a spinning voxel inspect with stats, holding F opens the full inspect. Every
 * mutation goes through {@link InventoryService}, so the UI can never overlap or duplicate items.
 */
public final class InventoryUi {
    public final Ui ui;
    public final ItemIcons icons;
    public ItemInstance drag, hover, inspected, menuItem;
    /**
     * 1.0: the thing last clicked (it is outlined, and it is what the Inspect button opens), and the thing being
     * inspected: a window of its own over the inventory - the model turning, what it is and what it does under it, and
     * Close, which goes back to the inventory as it was.
     */
    public ItemInstance selected, inspectOpen;
    private float inspYaw, inspPitch = 0.42f, lastMx, lastMy;
    private boolean inspDrag;
    public boolean dragRot;
    private int grabCX, grabCY;
    private float pressX, pressY;
    private boolean pressed;
    private ItemInstance pressItem;
    public float hoverTime, spin;
    public String flash = "";
    public float flashTime;
    public int flashColor = Ui.TXT;
    public float cell = 40;
    /**
     * 0.7: something that has just arrived (bought, given, crafted). Whichever screen shows the container it is in
     * brings it into view and rings it for a few seconds, so a new item can never land where it is not seen.
     */
    public ItemInstance arrived;
    public float arrivedT;
    public void arrive(ItemInstance it) { arrived = it; arrivedT = 5f; }
    public final List<String[]> menu = new ArrayList<>(); // {label, action}
    private float menuX, menuY;
    public BiConsumer<ItemInstance, String> onAction; // context actions: use, unload, split, equip, drop, sell, read, clean
    public Container quickA, quickB;
    /**
     * 1.3, the discard case: a small case that comes up while something is being moved (or turned), to drop things into one
     * after another and be rid of them together - "Discard" with them all, or "Take back". It stays up while anything
     * is in it. What discarding means is the screen's: on an operation they are put down on the ground, in the hideout
     * they are thrown away (after a second click to be sure). Null: no discard case on this screen.
     */
    public final Container discard = new Container("discard", 6, 4);
    public java.util.function.Consumer<java.util.List<ItemInstance>> onDiscard;
    public String discardVerb = "Discard";
    public boolean discardConfirm;
    private final java.util.Map<ItemInstance, Container> discardHome = new java.util.IdentityHashMap<>();
    private Container dragHome;
    private float discardArmed = -99, discardClock;
    /** Things discarded through the case (gates). */
    public int discarded;

    /** 1.3: the stash, when it is open (the hideout's deployment): shift-click equips from it. */
    public Container stash;   // shift+click pairs (left grid <-> right grid)
    public Loadout owner;              // the player's loadout (case side)
    public boolean allowDrop;
    public List<String> extraActions = new ArrayList<>();

    public InventoryUi(Ui ui, ItemIcons icons) { this.ui = ui; this.icons = icons; }

    public void begin(float baseCell) {
        cell = snap(baseCell * ui.scale);
        hover = null;
        if (inspectOpen != null) ui.block = true;      // what is under the inspect window does not answer the mouse
        if (drag != null && ui.in.keyPressed(GLFW_KEY_R)) { dragRot = !dragRot; ui.in.consume(GLFW_KEY_R); if (ui.sound != null) ui.sound.accept("ui_rotate"); }
        spin += ui.dt * 1.4f;
        flashTime -= ui.dt;
    }

    /** Cell sizes are whole multiples of the 16px icon art (24 allowed as the smallest step). */
    public static float snap(float px) { return px >= 32 ? (float) Math.floor(px / 16) * 16 : 24; }

    /** Shrinks the cell so 'rows' grid rows fit into 'avail' pixels. */
    public void fit(float avail, int rows) { cell = Math.min(cell, snap(avail / Math.max(1, rows))); }

    public void say(String s, int col) { flash = s; flashColor = col; flashTime = 2.8f; }

    private void result(InventoryService.Result r) {
        if (!r.ok() && !r.msg().isEmpty()) say(r.msg(), Ui.RED);
        if (ui.sound != null) ui.sound.accept(r.ok() ? "ui_drop" : "ui_invalid");
    }

    // ------------------------------------------------------------------ grid
    public void grid(Container c, float x, float y, String title) {
        float cs = cell;
        float w = c.w * cs, h = c.h * cs;
        if (title != null) {
            ui.text(title.toUpperCase(), x, y - ui.s(24), 15, Ui.TXT_HI);
            ui.textR(String.format("%.1f kg", c.mass()), x + w, y - ui.s(24), 13, Ui.TXT_DIM);
        }
        ui.b.rect(x, y, w, h, 0xFF101211);
        ui.b.outline(x - 1, y - 1, w + 3, h + 3, 1, 0xFF4A4E46);
        for (int gx = 0; gx <= c.w; gx++) ui.b.rect(x + gx * cs, y, 1, h, 0xFF262A27);
        for (int gy = 0; gy <= c.h; gy++) ui.b.rect(x, y + gy * cs, w, 1, 0xFF262A27);
        boolean inside = ui.hover(x, y, w, h);
        int hx = (int) Math.floor((ui.mx - x) / cs), hy = (int) Math.floor((ui.my - y) / cs);
        // placement preview
        if (drag != null && inside) {
            int iw = dragRot ? drag.baseH() : drag.baseW(), ih = dragRot ? drag.baseW() : drag.baseH();
            int px = hx - (dragRot ? grabCY : grabCX), py = hy - (dragRot ? grabCX : grabCY);
            ItemInstance under = c.at(hx, hy);
            boolean combine = under != null && under != drag && canCombine(drag, under);
            boolean fits = c.fits(px, py, iw, ih, drag);
            int col = combine ? 0x70E2A034 : fits ? 0x5060C060 : 0x60D04030;
            if (combine) ui.b.rect(x + under.x * cs, y + under.y * cs, under.w() * cs, under.h() * cs, col);
            else ui.b.rect(x + Math.max(0, px) * cs, y + Math.max(0, py) * cs, Math.min(iw, c.w - Math.max(0, px)) * cs, Math.min(ih, c.h - Math.max(0, py)) * cs, col);
            if (ui.in.mouseReleased(GLFW_MOUSE_BUTTON_LEFT)) {
                InventoryService.Result r = combine ? InventoryService.combine(drag, under) : InventoryService.move(drag, c, px, py, dragRot);
                result(r);
                drag = null;
            }
        }
        for (ItemInstance it : new ArrayList<>(c.items)) {
            float ix = x + it.x * cs, iy = y + it.y * cs, iw = it.w() * cs, ih = it.h() * cs;
            boolean hov = drag == null && ui.hover(ix, iy, iw, ih);
            itemTile(it, ix, iy, iw, ih, hov, it == drag);
            if (it == arrived && arrivedT > 0) {
                float k = 0.5f + 0.5f * (float) Math.sin(arrivedT * 7), a = Math.min(1, arrivedT);
                ui.b.outline(ix + 1, iy + 1, iw - 1, ih - 1, 2, Ui.alpha(0xFF7DBA5C, a * (0.55f + 0.45f * k)));
                ui.text("NEW", ix + ui.s(4), iy + ui.s(2), 10, Ui.alpha(0xFF7DBA5C, a));
            }
            if (hov) {
                hover = it;
                if (ui.click && !ui.consumed) {
                    ui.consumed = true;
                    if (ui.shift) quickTransfer(it, c);
                    else { pressed = true; pressItem = it; pressX = ui.mx; pressY = ui.my; grabCX = (int) ((ui.mx - ix) / cs); grabCY = (int) ((ui.my - iy) / cs); if (it.rotated) { int t = grabCX; grabCX = grabCY; grabCY = t; } }
                }
                if (ui.rclick) openMenu(it);
            }
        }
    }

    private void quickTransfer(ItemInstance it, Container from) {
        // anything of the player's goes to the other side; anything else comes to the player
        boolean mine = owner != null && (from == owner.caseGrid || from == owner.packGrid());
        // 1.3: getting ready to deploy, shift-click on a weapon or a piece of equipment in the stash puts it on (into its slot,
        // or in place of what is there, which goes back to the stash) instead of packing it in the case
        if (!mine && from == stash && it.def().slot != null && quickEquip(it)) return;
        Container other = quickA != null && quickA != from && (owner == null || quickA != owner.caseGrid) ? quickA : quickB != null && quickB != from && (owner == null || quickB != owner.caseGrid) ? quickB : null;
        Container to = mine ? other : owner != null ? owner.caseGrid : from == quickA ? quickB : quickA;
        if (to == null || to == from) {
            // 0.7: with nothing else open, shift-click on something that can be worn or carried in the hands puts it on
            if (mine && quickEquip(it)) return;
            say(mine ? "Nothing open to send it to" : "Nowhere to send it", Ui.TXT_DIM);
            return;
        }
        InventoryService.Result r = InventoryService.quickTransfer(it, to, owner != null && owner.caseGrid == to ? owner : null);
        if (!r.ok() && !mine) say("No room in your case for " + it.name(), Ui.RED);
        result(r);
    }

    /** Times shift-click has equipped something (gates). */
    public int quickEquips;

    /**
     * Equips an item from the case by the quickest route: its own slot if that is free, the other slot of the same
     * kind if there is one (a second long gun, another charm), and otherwise it changes places with what is there.
     * False when the thing is not something that is equipped, or there is no room to put the old one down.
     */
    public boolean quickEquip(ItemInstance it) {
        if (owner == null || it.def().slot == null || it.container == null) return false;
        String slot = it.def().slot;
        java.util.List<String> tries = new java.util.ArrayList<>();
        tries.add(slot);
        if (slot.startsWith("charm")) for (int k = 0; k < absentialis.gameplay.inventory.Loadout.CHARM_SLOTS; k++) if (!tries.contains("charm" + k)) tries.add("charm" + k);
        for (String s : tries) {
            if (owner.slot(s) != null) continue;
            InventoryService.Result r = InventoryService.equip(it, owner, s);
            if (r.ok()) { quickEquips++; say("Equipped " + it.name(), Ui.GREEN); return true; }
        }
        // every slot it could go in is taken: the one in its own slot comes off into the case, and this goes on
        ItemInstance old = owner.slot(slot);
        Container home = it.container;
        if (old == null || !InventoryService.unequip(old, home).ok()) { say("No room to take off " + (old == null ? "what is there" : old.name()), Ui.RED); return true; }
        InventoryService.Result r = InventoryService.equip(it, owner, slot);
        if (!r.ok()) { InventoryService.equip(old, owner, slot); result(r); return true; }
        quickEquips++;
        say("Equipped " + it.name() + " (took off " + old.name() + ")", Ui.GREEN);
        return true;
    }

    public static boolean canCombine(ItemInstance a, ItemInstance b) {
        ItemDef s = a.def(), d = b.def();
        if (s.stackable() && a.defId.equals(b.defId) && b.qty < d.maxStack) return true;
        if (InventoryService.herbMix(a.defId, b.defId) != null) return true;
        if (s.ammo != null && (d.magazine != null && d.magazine.caliber.equals(s.ammo.caliber) || d.weapon != null && d.weapon.internal() && s.ammo.caliber.equals(d.weapon.caliber))) return true;
        if (s.magazine != null && d.weapon != null && !d.weapon.internal() && s.magazine.magType.equals(d.weapon.magType)) return true;
        return s.attachment != null && d.weapon != null && Gunsmith.incompatibility(b, a) == null;
    }

    /** Item tile: icon, quantity / rounds badge and condition strip. */
    public void itemTile(ItemInstance it, float x, float y, float w, float h, boolean hov, boolean ghosted) {
        ItemDef d = it.def();
        int bg = switch (d.category) {
            case "weapon" -> 0xFF2A2420; case "ammo", "magazine" -> 0xFF2A281C; case "medical" -> 0xFF2C1C1C; case "food" -> 0xFF22281C;
            case "valuable", "treasure" -> 0xFF2E2A18; case "quest", "key" -> 0xFF1C2430; case "attachment" -> 0xFF242628; default -> 0xFF1E2220;
        };
        ui.b.rect(x + 1, y + 1, w - 1, h - 1, ghosted ? 0x40000000 | (bg & 0xFFFFFF) : bg);
        // loot tier: a coloured edge (white common ... yellow mythic)
        if (d.tier > 0 || "weapon".equals(d.category)) ui.b.rect(x + 1, y + 1, Math.max(2, ui.s(3)), h - 1, Ui.alpha(d.tierColor(), ghosted ? 0.3f : 0.9f));
        if (hov || it == selected) ui.b.outline(x + 1, y + 1, w - 1, h - 1, hov ? 2 : 1, 0xFFE2A034);
        icons.draw(ui.b, it, x + 3, y + 3, w - 5, h - 5, ghosted ? 0x60FFFFFF : 0xFFFFFFFF);
        float fs = 14;
        if (d.stackable() && it.qty > 1) ui.textR(String.valueOf(it.qty), x + w - ui.s(4), y + h - ui.s(16), fs, Ui.TXT_HI);
        if (d.magazine != null || d.weapon != null && !d.weapon.melee()) {
            String s = d.weapon != null ? it.loadedRounds() + (it.chambered ? "+" : "") : it.rounds + "/" + it.capacity();
            ui.textR(s, x + w - ui.s(4), y + h - ui.s(16), fs, it.loadedRounds() == 0 && d.weapon != null || d.magazine != null && it.rounds == 0 ? Ui.RED : Ui.TXT_HI);
        }
        if (d.weapon != null && !d.weapon.melee()) {
            float c = it.condition() / 100f;
            ui.b.rect(x + 3, y + h - 5, (w - 6) * c, 3, c < 0.25f ? 0xFFD8483A : c < 0.5f ? 0xFFE2A034 : 0xFF7DBA5C);
            if (it.jammed) ui.text("JAM", x + ui.s(4), y + ui.s(3), 11, Ui.RED);
        }
        if (it.foundInRaid) ui.b.rect(x + w - 6, y + 3, 3, 3, 0xFFE2A034);
        if (it.fallback) ui.text("F", x + ui.s(4), y + ui.s(2), 11, Ui.TXT_DIM);
    }

    // ------------------------------------------------------------------ equipment slots
    public void slot(Loadout l, String slot, float x, float y, float w, float h, Container back) {
        ItemInstance it = slot.startsWith("charm") ? l.charms[slot.charAt(5) - '0'] : l.slots.get(slot);
        boolean hov = ui.hover(x, y, w, h);
        boolean accept = drag != null && (slot.startsWith("charm") ? drag.def().charm != null : slot.equals(drag.def().slot));
        ui.nine(hov && drag != null ? (accept ? "slot_hi" : "slot") : "slot", x, y, w, h, 0xFFFFFFFF);
        if (drag != null && hov) ui.b.rect(x + 3, y + 3, w - 6, h - 6, accept ? 0x4060C060 : 0x40D04030);
        if (it == null) { String sn = slot.startsWith("charm") ? "CHARM" : slot.equals("backpack") ? "PACK" : slot.toUpperCase(); float ss = ui.tw(sn, 12) > w - ui.s(6) ? 10 : 12; ui.textC(sn, x + w / 2, y + h / 2 - ui.s(7), ss, 0xFF6A6C64); }
        else {
            boolean ih = drag == null && hov;
            itemTile(it, x + 2, y + 2, w - 4, h - 4, ih, false);
            if (ih) {
                hover = it;
                if (ui.click && !ui.consumed) { ui.consumed = true; if (ui.shift && back != null) result(InventoryService.unequip(it, back)); else { pressed = true; pressItem = it; pressX = ui.mx; pressY = ui.my; grabCX = grabCY = 0; } }
                if (ui.rclick) openMenu(it);
            }
        }
        if (drag != null && hov && ui.in.mouseReleased(GLFW_MOUSE_BUTTON_LEFT)) {
            if (it != null && drag.def().magazine != null || it != null && drag.def().attachment != null || it != null && drag.def().ammo != null) result(InventoryService.combine(drag, it));
            else result(InventoryService.equip(drag, l, slot));
            drag = null;
        }
    }

    /** Space-free pouch list (treasures / keys / craft / documents). */
    public void pouch(List<ItemInstance> list, float x, float y, float w, float h, String empty) {
        ui.nine("slot", x, y, w, h, 0xFFFFFFFF);
        float tile = cell * 1.25f, pad = ui.s(6);
        int cols = Math.max(1, (int) ((w - pad) / (tile + pad)));
        if (list.isEmpty()) ui.textC(empty, x + w / 2, y + h / 2 - ui.s(8), 13, Ui.TXT_DIM);
        for (int i = 0; i < list.size(); i++) {
            ItemInstance it = list.get(i);
            float tx = x + pad + (i % cols) * (tile + pad), ty = y + pad + (i / cols) * (tile + pad);
            if (ty + tile > y + h) break;
            boolean hov = drag == null && ui.hover(tx, ty, tile, tile);
            itemTile(it, tx, ty, tile, tile, hov, false);
            if (hov) { hover = it; if (ui.rclick) openMenu(it); if (ui.click && ui.shift && quickA != null && !ui.consumed) { ui.consumed = true; result(InventoryService.add(it, quickA)); } }
        }
    }

    // ------------------------------------------------------------------ context menu
    private void openMenu(ItemInstance it) {
        ui.consumed = true;
        menu.clear();
        if (ui.sound != null) ui.sound.accept("inv_menu");
        menuItem = it;
        menuX = ui.mx; menuY = ui.my;
        ItemDef d = it.def();
        if (d.medical != null || d.food != null) menu.add(new String[]{"Use", "use"});
        if ("document".equals(d.category)) menu.add(new String[]{"Read", "read"});
        if (d.slot != null && it.slot == null) menu.add(new String[]{"Equip", "equip"});
        if (d.charm != null && it.slot == null) menu.add(new String[]{"Equip charm", "equip"});
        if (d.magazine != null && it.rounds > 0 || d.weapon != null && d.weapon.internal() && it.rounds > 0) menu.add(new String[]{"Unload rounds", "unload"});
        if (d.weapon != null && it.mag != null) menu.add(new String[]{"Remove magazine", "removemag"});
        if (d.weapon != null && !d.weapon.melee() && !it.attachments.isEmpty()) menu.add(new String[]{"Strip parts", "strip"});
        if (d.stackable() && it.qty > 1) menu.add(new String[]{"Split half", "split"});
        if (d.weapon != null && it.jammed) menu.add(new String[]{"Clear jam", "clearjam"});
        for (String a : extraActions) menu.add(new String[]{a.substring(0, 1).toUpperCase() + a.substring(1), a});
        if (allowDrop && it.slot == null && it.parent == null) menu.add(new String[]{"Drop", "drop"});
        menu.add(new String[]{"Inspect", "inspect"});
    }

    /**
     * 1.0: what can be done right now, said directly under the grid it is done in: while something is being carried,
     * how to put it down; over an item, what the buttons do to it; otherwise the keys for the screen. On the right,
     * Inspect for the item that is selected.
     */
    public void hints(float x, float y, float w) {
        ItemInstance sel = selected != null && (selected.container != null || selected.slot != null) ? selected : null;
        float bw = sel == null ? 0 : ui.s(118);
        String s = drag != null ? "[R] rotate     release on a free cell to place it     release anywhere else to cancel"
                : hover != null ? "[Drag] move     [Shift+Click] " + (quickA != null ? "send across" : "equip") + "     [Right click] actions     hold [F] inspect"
                : "[Z] sort the case     [I] or [Esc] close";
        float fs = ui.tw(s, 13) > w - bw - ui.s(8) ? 11 : 13;
        ui.text(s, x, y + ui.s(6), fs, Ui.TXT_DIM);
        if (sel != null && ui.button("inv_inspect", x + w - bw, y, bw, ui.s(28), "Inspect", null, true)) { inspectOpen = sel; inspYaw = 0.6f; inspPitch = 0.42f; }
    }

    /** The inspect window: the model turning (drag it to turn it yourself), the description under it, and Close. */
    /** 1.1: whose weapon levels and upgrades the inspect window shows and spends (the profile's; null = a plain inspect window). */
    public absentialis.gameplay.progression.Progression prog;
    /** How many parts were fitted and removed, and upgrades bought, from the inspect window (gates). */
    public int benchFits, benchRemovals, benchUpgrades;

    /** Attachment items within reach that would go in a slot of a weapon: in the survivor's kit, or in whatever grid is open beside it. */
    public List<ItemInstance> partsFor(ItemInstance weapon, String category) {
        List<ItemInstance> out = new ArrayList<>(), pool = new ArrayList<>();
        if (owner != null) pool.addAll(owner.all());
        for (Container c : new Container[]{quickA, quickB}) if (c != null) for (ItemInstance i : c.items) if (!pool.contains(i)) pool.add(i);
        for (ItemInstance i : pool) {
            ItemDef p = i.def();
            if (p == null || p.attachment == null || i.parent != null || !category.equals(p.attachment.socket) || !absentialis.gameplay.weapons.Attachments.fits(weapon.def(), p)) continue;
            out.add(i);
        }
        return out;
    }

    /** Fits a part the survivor has to a weapon of theirs, if the weapon's level has opened that slot. Returns what to say. */
    public String benchFit(ItemInstance weapon, ItemInstance part) {
        ItemDef d = weapon.def(), p = part.def();
        if (prog != null && !prog.slotOpen(d, p.attachment.socket)) return "The " + absentialis.gameplay.weapons.Attachments.categoryName(p.attachment.socket) + " slot opens at weapon level " + absentialis.gameplay.weapons.Attachments.slotLevel(d, p.attachment.socket);
        InventoryService.Result r = InventoryService.attach(weapon, part);
        if (!r.ok()) return r.msg();
        Gunsmith.clampRounds(weapon);
        benchFits++;
        return null;
    }

    /** Takes a part off: one that was an item goes back into the case; one the workbench made is simply taken off. */
    public String benchRemove(ItemInstance weapon, String category) {
        ItemInstance p = weapon.attachments.get(category);
        if (p == null) return "Nothing fitted";
        if ("workbench".equals(p.origin) || owner == null) Gunsmith.remove(weapon, category);
        else { InventoryService.Result r = InventoryService.detachPart(weapon, category, owner.caseGrid); if (!r.ok()) return r.msg().equals("No room") ? "No room in the case for the " + p.def().name : r.msg(); Gunsmith.clampRounds(weapon); }
        benchRemovals++;
        return null;
    }

    /**
     * 1.1: a weapon inspected is a weapon on the bench. The same window, with the model and the figures on the left; on
     * the right what its levels have earned - the level and the WXP toward the next, an upgrade to spend on one of its
     * tracks, and its attachment slots: the ones its level has opened, what is in them, and for an empty one whatever
     * the survivor is carrying that fits it, to be fitted there and then.
     */
    private void drawBench() {
        ItemInstance it = inspectOpen;
        ItemDef d = it.def();
        boolean knife = d.weapon.melee();
        ui.block = false;
        ui.b.rect(0, 0, ui.w, ui.h, 0xC0050605);
        float w = Math.min(ui.s(1060), ui.w - ui.s(40)), h = Math.min(ui.s(700), ui.h - ui.s(40));
        float x = Math.round((ui.w - w) / 2f), y = Math.round((ui.h - h) / 2f), m = ui.s(22), lw = Math.round(w * 0.46f);
        ui.panel(x, y, w, h);
        ui.text(d.name, x + m, y + ui.s(16), 22, d.tier > 0 ? d.tierColor() : Ui.TXT_HI);
        ui.text(cap(d.tierName()) + " " + d.category + "     " + it.baseW() + " x " + it.baseH() + "     " + String.format("%.2f kg", it.mass()), x + m, y + ui.s(46), 13, Ui.TXT_DIM);
        float bottom = y + h - ui.s(58);
        // ---- left: the weapon, turning, and its figures
        float vw = lw - m * 2, vs = Math.round(Math.min(vw, h * 0.34f)), vy = y + ui.s(74);
        ui.b.rect(x + m, vy, vw, vs, 0xFF0A0B0A);
        ui.b.outline(x + m, vy, vw, vs, 1, 0xFF30342F);
        if (ui.click && ui.hover(x + m, vy, vw, vs)) inspDrag = true;
        if (!ui.down) inspDrag = false;
        if (inspDrag) { inspYaw += (ui.mx - lastMx) * 0.012f; inspPitch = Math.max(-1.2f, Math.min(1.2f, inspPitch + (ui.my - lastMy) * 0.012f)); }
        else inspYaw += ui.dt * 0.7f;
        lastMx = ui.mx; lastMy = ui.my;
        icons.drawInspect(ui.b, it, Math.round(x + m + (vw - vs) / 2), vy, vs, inspYaw, inspPitch);
        float ty = vy + vs + ui.s(12), rh = ui.s(21);
        List<String[]> st = stats(it);
        for (String[] s : st) {
            if (ty > bottom - rh) break;
            ui.text(s[0], x + m, ty, 13, Ui.TXT_DIM);
            if (s.length > 2) ui.bar(x + m + vw * 0.45f, ty + ui.s(3), vw * 0.55f, ui.s(10), Float.parseFloat(s[2]), s.length > 3 && "bad".equals(s[3]) ? 0xFFC0402E : 0xFFB0A070);
            else ui.textR(s[1], x + m + vw, ty, 13, Ui.TXT_HI);
            ty += rh;
        }
        // ---- right: what its levels have earned
        float rx = x + lw + ui.s(6), rw = w - lw - m - ui.s(6), ry = y + ui.s(74);
        ui.b.rect(x + lw - ui.s(8), y + ui.s(74), 1, bottom - y - ui.s(74), 0xFF30342F);
        int lvl = prog.weaponLevel(d.id), max = absentialis.gameplay.weapons.Attachments.maxLevel(d), steps = Gunsmith.STEPS;
        ui.text("WEAPON LEVEL " + lvl + " / " + max, rx, ry, 16, Ui.AMBER);
        long have = prog.mastery.getOrDefault(d.id, 0L), next = absentialis.gameplay.weapons.Attachments.xpForLevel(lvl + 1);
        ui.textR(lvl >= max ? "MAX" : have + " / " + next + " WXP", rx + rw, ry + ui.s(2), 12, Ui.TXT_DIM);
        ry += ui.s(24);
        ui.bar(rx, ry, rw, ui.s(7), prog.weaponLevelProgress(d.id), 0xFFB0A070);
        ry += ui.s(22);
        int pts = prog.upgradePoints(d.id);
        ui.text("UPGRADES", rx, ry, 13, Ui.TXT_DIM);
        ui.textR(pts > 0 ? pts + (pts == 1 ? " upgrade to spend" : " upgrades to spend") : lvl >= max ? "every level earned" : "the next comes with level " + (lvl + 1), rx + rw, ry, 12, pts > 0 ? Ui.AMBER : Ui.TXT_DIM);
        ry += ui.s(22);
        String[] tracks = Gunsmith.tracks(d);
        float pipX = rx + rw * 0.30f, pipW = ui.s(18), pipGap = ui.s(4);
        for (int t = 0; t < tracks.length; t++) {
            int tl = prog.tune(d.id, t);
            ui.text(tracks[t], rx, ry + ui.s(5), 13, Ui.TXT);
            for (int k = 0; k < steps; k++) ui.b.rect(pipX + k * (pipW + pipGap), ry + ui.s(7), pipW, ui.s(9), k < tl ? 0xFFB0A070 : 0xFF2A2D28);
            float ex = pipX + steps * (pipW + pipGap) + ui.s(8);
            ui.text(Gunsmith.effect(d, t, tl), ex, ry + ui.s(5), 12, Ui.TXT_HI);
            if (tl < steps) {
                ui.text("> " + Gunsmith.effect(d, t, tl + 1), ex + ui.s(92), ry + ui.s(5), 12, pts > 0 ? 0xFF8FB070 : 0xFF6A6C64);
                if (ui.button("bench_up" + t, rx + rw - ui.s(34), ry, ui.s(34), ui.s(24), "+", null, pts > 0)) {
                    String r = prog.upgrade(d.id, t);
                    if (r != null) say(r, Ui.RED); else { benchUpgrades++; pts--; Gunsmith.clampRounds(it); say(tracks[t] + " improved", Ui.AMBER); if (ui.sound != null) ui.sound.accept("ui_confirm"); }
                }
            } else ui.textR("MAX", rx + rw, ry + ui.s(5), 12, Ui.AMBER);
            ry += ui.s(29);
        }
        if (!knife) {
            List<String> order = absentialis.gameplay.weapons.Attachments.slotOrder(d);
            ry += ui.s(8);
            ui.text("ATTACHMENTS", rx, ry, 13, Ui.TXT_DIM);
            ui.textR(absentialis.gameplay.weapons.Attachments.slotsOpen(d, lvl) + " of " + order.size() + " slots open", rx + rw, ry, 12, Ui.TXT_DIM);
            ry += ui.s(22);
            float row = Math.max(ui.s(22), Math.min(ui.s(30), (bottom - ry) / Math.max(1, order.size()))), bh = Math.min(ui.s(24), row - ui.s(3)), cx = rx + rw * 0.30f;
            for (int i = 0; i < order.size(); i++) {
                String cat = order.get(i);
                boolean open = lvl >= i + 1;
                ItemInstance fitted = it.attachments.get(cat);
                ui.text(absentialis.gameplay.weapons.Attachments.categoryName(cat), rx, ry + ui.s(4), 13, open ? Ui.TXT : 0xFF6A6C64);
                if (fitted != null) {
                    ui.text(fitted.def().name, cx, ry + ui.s(4), 13, Ui.TXT_HI);
                    if (ui.button("bench_rm" + i, rx + rw - ui.s(78), ry, ui.s(78), bh, "Remove", null, true)) { String r = benchRemove(it, cat); if (r != null) say(r, Ui.RED); else if (ui.sound != null) ui.sound.accept("ui_back"); }
                } else if (!open) ui.text("opens at weapon level " + (i + 1), cx, ry + ui.s(4), 12, 0xFF6A6C64);
                else {
                    List<ItemInstance> parts = partsFor(it, cat);
                    if (parts.isEmpty()) ui.text("open - nothing in your kit fits it", cx, ry + ui.s(4), 12, 0xFF6A6C64);
                    else {
                        float bw = (rx + rw - cx - ui.s(6)) / Math.min(2, parts.size());
                        for (int k = 0; k < Math.min(2, parts.size()); k++) {
                            ItemInstance p = parts.get(k);
                            if (ui.button("bench_fit" + i + "_" + k, cx + k * (bw + ui.s(6)), ry, bw - (parts.size() > 1 ? ui.s(6) : 0), bh, "Fit " + p.def().name, null, true)) { String r = benchFit(it, p); if (r != null) say(r, Ui.RED); else { say(p.def().name + " fitted", Ui.AMBER); if (ui.sound != null) ui.sound.accept("ui_confirm"); } }
                        }
                    }
                }
                ry += row;
            }
        } else {
            ry += ui.s(10);
            ui.wrap("A blade takes no attachments. Every kill with it is weapon experience; every level is an upgrade to its edge or to how long the edge lasts.", rx, ry, rw, 13, Ui.TXT_DIM);
        }
        if (flashTime > 0 && !flash.isEmpty()) ui.textC(flash, x + w / 2, y + h - ui.s(76), 13, flashColor);
        boolean shut = ui.button("insp_close", x + w / 2 - ui.s(80), y + h - ui.s(48), ui.s(160), ui.s(34), "Close", null, true);
        if (ui.in.keyPressed(GLFW_KEY_ESCAPE)) { shut = true; ui.in.consume(GLFW_KEY_ESCAPE); }
        if (shut) { inspectOpen = null; if (ui.sound != null) ui.sound.accept("ui_back"); }
        ui.consumed = true;
        pressed = false; drag = null;
    }

    private void drawInspect() {
        if (inspectOpen.def().weapon != null && prog != null) { drawBench(); return; }
        ItemInstance it = inspectOpen;
        ItemDef d = it.def();
        ui.block = false;
        ui.b.rect(0, 0, ui.w, ui.h, 0xC0050605);
        float w = Math.min(ui.s(600), ui.w - ui.s(40)), h = Math.min(ui.s(700), ui.h - ui.s(40));
        float x = Math.round((ui.w - w) / 2f), y = Math.round((ui.h - h) / 2f), m = ui.s(22);
        ui.panel(x, y, w, h);
        ui.text(d.name, x + m, y + ui.s(16), 22, d.tier > 0 ? d.tierColor() : Ui.TXT_HI);
        ui.text(cap(d.tierName()) + " " + d.category + "     " + it.baseW() + " x " + it.baseH() + "     " + String.format("%.2f kg", it.mass()), x + m, y + ui.s(46), 13, Ui.TXT_DIM);
        float vs = Math.round(Math.min(w - m * 2, h * 0.44f)), vx = Math.round(x + (w - vs) / 2), vy = y + ui.s(74);
        ui.b.rect(x + m, vy, w - m * 2, vs, 0xFF0A0B0A);
        ui.b.outline(x + m, vy, w - m * 2, vs, 1, 0xFF30342F);
        if (ui.click && ui.hover(x + m, vy, w - m * 2, vs)) inspDrag = true;
        if (!ui.down) inspDrag = false;
        if (inspDrag) { inspYaw += (ui.mx - lastMx) * 0.012f; inspPitch = Math.max(-1.2f, Math.min(1.2f, inspPitch + (ui.my - lastMy) * 0.012f)); }
        else inspYaw += ui.dt * 0.7f;
        lastMx = ui.mx; lastMy = ui.my;
        icons.drawInspect(ui.b, it, vx, vy, vs, inspYaw, inspPitch);
        ui.textR("drag to turn", x + w - m - ui.s(8), vy + vs - ui.s(20), 11, 0xFF6A6C64);
        // underneath: what it is, in words; then what it does, in two columns
        float ty = vy + vs + ui.s(14), bottom = y + h - ui.s(58);
        if (!d.desc.isEmpty()) ty += ui.wrap(d.desc, x + m, ty, w - m * 2, 15, Ui.TXT) + ui.s(10);
        ui.b.rect(x + m, ty - ui.s(4), w - m * 2, 1, 0xFF30342F);
        ty += ui.s(6);
        List<String[]> st = stats(it);
        float cw = (w - m * 2 - ui.s(24)) / 2, rh = ui.s(21);
        int rows = (st.size() + 1) / 2;
        for (int i = 0; i < st.size(); i++) {
            String[] s = st.get(i);
            float sx = x + m + (i / rows) * (cw + ui.s(24)), sy = ty + (i % rows) * rh;
            if (sy > bottom - rh) continue;
            ui.text(s[0], sx, sy, 13, Ui.TXT_DIM);
            if (s.length > 2) ui.bar(sx + cw * 0.5f, sy + ui.s(3), cw * 0.5f, ui.s(10), Float.parseFloat(s[2]), s.length > 3 && "bad".equals(s[3]) ? 0xFFC0402E : 0xFFB0A070);
            else ui.textR(s[1], sx + cw, sy, 13, Ui.TXT_HI);
        }
        boolean shut = ui.button("insp_close", x + w / 2 - ui.s(80), y + h - ui.s(48), ui.s(160), ui.s(34), "Close", null, true);
        if (ui.in.keyPressed(GLFW_KEY_ESCAPE)) { shut = true; ui.in.consume(GLFW_KEY_ESCAPE); }
        if (shut || ui.rclick) { inspectOpen = null; if (ui.sound != null) ui.sound.accept("ui_back"); }
        ui.consumed = true;
        pressed = false; drag = null;
    }

    // ------------------------------------------------------------------ frame end
    /** Draws drag ghost, context menu, flash text; resolves drag start/cancel. */
    public void end() {
        // start a drag after a small movement threshold
        if (pressed && pressItem != null && (Math.abs(ui.mx - pressX) > 4 || Math.abs(ui.my - pressY) > 4)) { drag = pressItem; dragRot = pressItem.rotated; pressed = false; dragHome = pressItem.container; if (ui.sound != null) ui.sound.accept("inv_lift"); }
        discardClock += ui.dt;
        if (onDiscard != null && (drag != null || !discard.items.isEmpty())) discardCase();
        if (ui.in.mouseReleased(GLFW_MOUSE_BUTTON_LEFT)) {
            if (pressed && pressItem != null && drag == null) { inspected = pressItem; selected = pressItem; } // click = select (the Inspect button opens it)
            pressed = false;
            if (drag != null) { drag = null; if (ui.sound != null) ui.sound.accept("inv_move"); } // released over nothing: cancelled, item never moved
        }
        if (!menu.isEmpty()) drawMenu();
        if (drag != null) {
            int iw = dragRot ? drag.baseH() : drag.baseW(), ih = dragRot ? drag.baseW() : drag.baseH();
            float gx = ui.mx - (dragRot ? grabCY : grabCX) * cell - cell / 2, gy = ui.my - (dragRot ? grabCX : grabCY) * cell - cell / 2;
            ui.shadow(gx, gy, iw * cell, ih * cell, 0x60);
            icons.draw(ui.b, drag, gx, gy, iw * cell, ih * cell, 0xE0FFFFFF);
            ui.text("[R] rotate", gx, gy + ih * cell + ui.s(4), 12, Ui.TXT_DIM);
        }
        if (flashTime > 0) {
            float a = Math.min(1, flashTime);
            float tw = ui.tw(flash, 16);
            ui.b.rect(ui.w / 2f - tw / 2 - ui.s(12), ui.h - ui.s(96), tw + ui.s(24), ui.s(30), Ui.alpha(0xD0101010, a));
            ui.textC(flash, ui.w / 2f, ui.h - ui.s(90), 16, Ui.alpha(flashColor, a));
        }
        if (hover != null) { if (hover != inspected) hoverTime = 0; inspected = hover; hoverTime += ui.dt; }
        if (inspectOpen != null) drawInspect();
    }

    /** 1.3: the discard case, bottom right: its grid (a drop target like any other), and what to do with what is in it. */
    private void discardCase() {
        float cs = cell, w = discard.w * cs, h = discard.h * cs;
        float x = ui.w - w - ui.s(28), y = ui.h - h - ui.s(120);
        ui.shadow(x - ui.s(8), y - ui.s(34), w + ui.s(16), h + ui.s(80), 0x90);
        grid(discard, x, y, discardVerb + " case");
        // whatever has just been dropped in remembers where it came from (for Take back)
        for (ItemInstance it : discard.items) if (!discardHome.containsKey(it)) discardHome.put(it, dragHome != null && dragHome != discard ? dragHome : owner != null ? owner.caseGrid : null);
        discardHome.keySet().removeIf(it -> it.container != discard);
        if (discard.items.isEmpty()) { ui.text("drop things here to " + discardVerb.toLowerCase() + " them together", x, y + h + ui.s(8), 12, Ui.TXT_DIM); return; }
        float bw = (w - ui.s(8)) / 2, by = y + h + ui.s(8), bh = ui.s(30);
        boolean armed = discardConfirm && discardClock - discardArmed < 3;
        String label = armed ? "Sure? " + discardVerb + " " + discard.items.size() : discardVerb + " " + discard.items.size();
        if (ui.button("discard_all", x, by, bw, bh, label, null, true)) {
            if (discardConfirm && !armed) { discardArmed = discardClock; say("Click again to " + discardVerb.toLowerCase() + " " + discard.items.size() + (discard.items.size() == 1 ? " thing" : " things"), Ui.AMBER); }
            else {
                java.util.List<ItemInstance> gone = new java.util.ArrayList<>(discard.items);
                for (ItemInstance it : gone) InventoryService.release(it);
                discardHome.clear();
                discarded += gone.size();
                onDiscard.accept(gone);
                discardArmed = -99;
                if (ui.sound != null) ui.sound.accept("ui_drop");
            }
        }
        if (ui.button("discard_back", x + bw + ui.s(8), by, bw, bh, "Take back", null, true)) {
            int left = 0;
            for (ItemInstance it : new java.util.ArrayList<>(discard.items)) {
                Container home = discardHome.get(it);
                InventoryService.Result r = home != null ? InventoryService.add(it, home) : new InventoryService.Result(false, "");
                if (!r.ok() && owner != null && home != owner.caseGrid) r = InventoryService.add(it, owner.caseGrid);
                if (!r.ok()) left++;
            }
            if (left > 0) say("No room to take " + left + " back", Ui.RED);
        }
    }

    private void drawMenu() {
        float w = ui.s(170), lh = ui.s(26), h = menu.size() * lh + ui.s(10);
        float x = Math.min(menuX, ui.w - w - 4), y = Math.min(menuY, ui.h - h - 4);
        ui.shadow(x, y, w, h, 0x80);
        ui.nine("tip", x, y, w, h, 0xFFFFFFFF);
        boolean any = false;
        for (int i = 0; i < menu.size(); i++) {
            float iy = y + ui.s(5) + i * lh;
            boolean hov = ui.hover(x, iy, w, lh);
            if (hov) { ui.b.rect(x + 3, iy, w - 6, lh, 0xFF3A3420); any = true; }
            ui.text(menu.get(i)[0], x + ui.s(12), iy + ui.s(5), 14, hov ? Ui.TXT_HI : Ui.TXT);
            if (hov && ui.click) {
                String act = menu.get(i)[1];
                ItemInstance it = menuItem;
                menu.clear();
                ui.consumed = true;
                if ("inspect".equals(act)) { inspected = it; selected = it; inspectOpen = it; inspYaw = 0.6f; inspPitch = 0.42f; }
                else if (onAction != null) onAction.accept(it, act);
                return;
            }
        }
        if ((ui.click || ui.rclick) && !any && !ui.consumed) menu.clear();
        if (ui.in.keyPressed(GLFW_KEY_ESCAPE)) menu.clear();
    }

    // ------------------------------------------------------------------ inspect
    /** Side panel: spinning voxel model + stats of the hovered / selected item. */
    public void inspectPanel(float x, float y, float w, float h) {
        ui.panelDark(x, y, w, h);
        ItemInstance it = inspected;
        if (it == null) { ui.textC("Hover an item to inspect", x + w / 2, y + h / 2, 14, Ui.TXT_DIM); return; }
        ItemDef d = it.def();
        float vs = Math.min(w - ui.s(20), ui.s(170));
        ui.b.rect(x + (w - vs) / 2, y + ui.s(10), vs, vs, 0xFF0C0D0C);
        icons.drawInspect(ui.b, it, x + (w - vs) / 2, y + ui.s(10), vs, spin, 0.45f + 0.1f * (float) Math.sin(spin * 0.7));
        float ty = y + vs + ui.s(18);
        ui.text(d.name, x + ui.s(12), ty, 16, d.tier > 0 ? d.tierColor() : Ui.TXT_HI);
        ty += ui.s(22);
        ui.text(d.tierName() + " " + d.category + "  -  " + it.baseW() + "x" + it.baseH() + "  -  " + String.format("%.2f kg", it.mass()), x + ui.s(12), ty, 12, Ui.TXT_DIM);
        ty += ui.s(20);
        for (String[] s : stats(it)) {
            if (ty > y + h - ui.s(40)) break;
            ui.text(s[0], x + ui.s(12), ty, 13, Ui.TXT);
            if (s.length > 2) {
                float f = Float.parseFloat(s[2]);
                boolean bad = s.length > 3 && "bad".equals(s[3]);
                ui.bar(x + w * 0.45f, ty + ui.s(3), w * 0.5f - ui.s(12), ui.s(10), f, bad ? 0xFFC0402E : 0xFFB0A070);
            } else ui.textR(s[1], x + w - ui.s(12), ty, 13, Ui.AMBER);
            ty += ui.s(19);
        }
        if (!d.desc.isEmpty() && ty < y + h - ui.s(50)) ui.wrap(d.desc, x + ui.s(12), ty + ui.s(6), w - ui.s(24), 12, Ui.TXT_DIM);
        ui.text("Hold [F] full inspect", x + ui.s(12), y + h - ui.s(22), 11, Ui.TXT_DIM);
    }

    /** Hold F: large centred model the mouse can rotate. */
    public void fullInspect(float yaw, float pitch) {
        if (inspected == null) return;
        ui.b.rect(0, 0, ui.w, ui.h, 0xD8080908);
        float s = Math.min(ui.w, ui.h) * 0.7f;
        icons.drawInspect(ui.b, inspected, (ui.w - s) / 2, (ui.h - s) / 2 - ui.s(20), s, yaw, pitch);
        ui.textC(inspected.def().name.toUpperCase(), ui.w / 2f, ui.h - ui.s(90), 22, Ui.TXT_HI);
        ui.textC("Move the mouse to turn  -  release [F] to close", ui.w / 2f, ui.h - ui.s(58), 13, Ui.TXT_DIM);
    }

    public static String cap(String s) { return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    /** Stat rows: {label, text} or {label, text, fraction, bad?}. */
    public static List<String[]> stats(ItemInstance it) {
        List<String[]> l = new ArrayList<>();
        ItemDef d = it.def();
        if (d.weapon != null && !d.weapon.melee()) {
            Gunsmith.Stats s = Gunsmith.stats(it, 0);
            boolean worn = s.conditionBand > 0;
            l.add(new String[]{"Condition", Math.round(it.condition()) + "", String.valueOf(it.condition() / 100f), worn ? "bad" : ""});
            float[] pr = Gunsmith.primary(it);
            for (int k = 0; k < pr.length; k++) l.add(new String[]{Gunsmith.PRIMARY[k], "", String.valueOf(pr[k]), worn && (k == 2 || k == 3) ? "bad" : ""});
            ItemDef am = Content.ammoFor(d.weapon.caliber);
            l.add(new String[]{"Ammunition", am == null ? d.weapon.caliber : am.name});
            l.add(new String[]{"Loaded", it.loadedRounds() + " / " + s.capacity});
            l.add(new String[]{"Fire rate", Math.round(s.rpm) + " rpm"});
            l.add(new String[]{"Attachments", it.attachments.size() + " / " + absentialis.gameplay.weapons.Attachments.MAX_EQUIPPED});
            for (ItemInstance a : it.attachments.values()) l.add(new String[]{"  " + a.def().name, ""});
            if (worn) l.add(new String[]{s.conditionBand == 2 ? "CRITICAL: needs a workbench" : "Worn: clean with gun oil", ""});
        } else if (d.weapon != null) {
            l.add(new String[]{"Damage", String.valueOf((int) d.weapon.meleeDamage)});
            l.add(new String[]{"Reach", d.weapon.meleeRange + " m"});
        }
        if (d.ammo != null) { l.add(new String[]{"Damage", String.valueOf((int) d.ammo.damage)}); l.add(new String[]{"Penetration", String.valueOf((int) d.ammo.pen)}); if (d.ammo.pellets > 1) l.add(new String[]{"Pellets", String.valueOf(d.ammo.pellets)}); }
        if (d.magazine != null) { l.add(new String[]{"Rounds", it.rounds + " / " + it.capacity()}); l.add(new String[]{"Caliber", d.magazine.caliber}); }
        if (d.armor != null && d.armor.armorClass > 0) { l.add(new String[]{"Class", String.valueOf(d.armor.armorClass)}); l.add(new String[]{"Durability", Math.round(it.durability) + " / " + Math.round(it.maxDurability), String.valueOf(it.durability / Math.max(1, it.maxDurability)), ""}); }
        if ("melee.knife".equals(d.id)) l.add(new String[]{"Edge", Math.round(it.durability * 100) + "%", String.valueOf(Math.max(0, it.durability)), it.durability < 0.25f ? "bad" : ""});
        if (d.medical != null) { if (d.medical.heal > 0) l.add(new String[]{"Heals", "+" + (int) d.medical.heal}); if (d.medical.healFrac > 0) l.add(new String[]{"Heals", Math.round(d.medical.healFrac * 100) + "% of health"}); if (d.medical.uses > 1) l.add(new String[]{"Uses", it.uses + " / " + d.medical.uses}); }
        if (d.food != null) { if (d.food.energy != 0) l.add(new String[]{"Energy", String.format("%+d", (int) d.food.energy)}); if (d.food.hydration != 0) l.add(new String[]{"Hydration", String.format("%+d", (int) d.food.hydration)}); }
        if (d.attachment != null) {
            l.add(new String[]{"Slot", absentialis.gameplay.weapons.Attachments.categoryName(d.attachment.socket)});
            for (java.util.Map.Entry<String, Float> e : d.attachment.mods.entrySet()) l.add(new String[]{absentialis.gameplay.weapons.Attachments.statName(e.getKey()), String.format("%+d%%", Math.round(e.getValue()))});
        }
        if (d.armor != null && d.armor.shield > 0) l.add(new String[]{"Protection", Math.round(it.durability) + " / " + Math.round(d.armor.shield), String.valueOf(it.durability / d.armor.shield), ""});
        if (d.charm != null) l.add(new String[]{"Effect", cap(d.charm.effect) + String.format(" %+d%%", Math.round(d.charm.amount * 100))});
        l.add(new String[]{"Value", absentialis.Version.coins(it.baseValueTotal())});
        if (it.origin != null && !it.origin.isEmpty()) l.add(new String[]{"Origin", it.origin});
        return l;
    }
}
