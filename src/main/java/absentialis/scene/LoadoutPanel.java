package absentialis.scene;

import absentialis.engine.events.Events;
import absentialis.gameplay.inventory.Container;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.inventory.Loadout;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.persistence.Profile;
import absentialis.ui.InventoryUi;
import absentialis.ui.Layout;
import absentialis.ui.Ui;
import java.util.ArrayList;
import java.util.List;

/**
 * The survivor's carried kit as one panel: gear slots, three charm slots, the attaché case grid
 * and the space-free tabs (Items / Treasures / Keys / Craft / Documents). Shared by the raid
 * inventory and the hideout so the same instance looks and behaves identically everywhere.
 */
public final class LoadoutPanel {
    public static final String[] TABS = {"ITEMS", "TREASURES", "KEYS", "CRAFT", "DOCUMENTS"};
    private static final String[] TAB_GLYPH = {"inv", "gem", "key", "herb", "doc"};
    private final Ctx c;
    public int tab;
    public Raid raid; // null in the hideout

    public LoadoutPanel(Ctx c) { this.c = c; }

    /** Width needed for the panel at the current cell size. */
    public float width(Loadout l) { return dollW() + c.ui.s(22) + Math.max(c.inv.cell * l.caseGrid.w + c.ui.s(8), c.ui.s(440)); }

    // ---- 1.0: the equipment is laid out as it is worn - a figure, and each thing where it sits on the body
    private float q() { return c.ui.s(60); }
    public float dollW() { return q() * 3 + c.ui.s(6) * 2; }
    /** The height of the figure: three rows of slots, and (for the survivor) the charms and the weight carried under them. */
    public float dollH(boolean own) { return q() * 3 + c.ui.s(6) * 2 + (own ? c.ui.s(26) + q() + c.ui.s(40) : 0); }

    /** What the panel needs from top to bottom at the current cell size: the tabs, the grid (and a pack under it), the controls. */
    public float height(Loadout l) {
        Ui ui = c.ui;
        Container pack = l.packGrid();
        float grid = l.caseGrid.h * c.inv.cell + (pack == null ? 0 : pack.h * c.inv.cell + ui.s(34));
        // (the pouches and the bench want room: on those tabs the panel is as tall as the screen allows)
        if (tab != 0) return Math.max(ui.s(42) + Math.max(dollH(true) - ui.s(42), grid) + ui.s(46), ui.h - ui.s(170));
        return ui.s(42) + Math.max(dollH(true) - ui.s(42), grid) + ui.s(46);
    }

    /**
     * The body, and what is on it: head and face at the head, armour on the chest, the pack at the shoulder, the long
     * gun in the hands, the sidearm and the blade at the hips. The same figure is used for the survivor and for a body
     * being searched, so the two sides of the screen read the same way.
     */
    public float doll(Loadout l, float x, float y, Container back, boolean own, float mass, float cap) {
        Ui ui = c.ui;
        InventoryUi inv = c.inv;
        float q = q(), g = ui.s(6), cx = x + q + g + q / 2;
        // the figure behind the slots: shoulders, arms, hips and legs, as plain dark shapes
        int fig = 0xFF1C201D;
        ui.b.rect(cx - q * 0.92f, y + q + g - ui.s(3), q * 1.84f, ui.s(10), fig);                     // shoulders
        ui.b.rect(cx - q * 0.92f, y + q + g, ui.s(10), q * 1.25f, fig); ui.b.rect(cx + q * 0.92f - ui.s(10), y + q + g, ui.s(10), q * 1.25f, fig);   // arms
        ui.b.rect(cx - ui.s(5), y + q - ui.s(2), ui.s(10), g + ui.s(4), fig);                          // neck
        float ly = y + (q + g) * 2 - ui.s(4);
        ui.b.rect(cx - q * 0.36f, ly, q * 0.72f, ui.s(12), fig);                                       // hips
        ui.b.rect(cx - q * 0.36f, ly, q * 0.30f, q + ui.s(2), fig); ui.b.rect(cx + q * 0.06f, ly, q * 0.30f, q + ui.s(2), fig);   // legs
        ui.b.rect(cx - q * 0.44f, ly + q - ui.s(6), q * 0.38f, ui.s(8), fig); ui.b.rect(cx + q * 0.06f, ly + q - ui.s(6), q * 0.38f, ui.s(8), fig); // boots
        String[][] at = {{"face", "head", "backpack"}, {"primary", "armor", "sidearm"}, {"melee", null, null}};
        for (int r = 0; r < 3; r++) for (int k = 0; k < 3; k++) if (at[r][k] != null) inv.slot(l, at[r][k], x + k * (q + g), y + r * (q + g), q, q, back);
        float by = y + q * 3 + g * 2;
        if (!own) return by - y;
        ui.text("CHARMS", x, by + ui.s(8), 12, Ui.TXT_DIM);
        for (int k = 0; k < Loadout.CHARM_SLOTS; k++) inv.slot(l, "charm" + k, x + k * (q + g), by + ui.s(26), q, q, back);
        float wy = by + ui.s(26) + q + ui.s(12);
        ui.text("CARRIED", x, wy, 12, Ui.TXT_DIM);
        ui.textR(String.format("%.1f / %.0f kg", mass, cap), x + dollW(), wy - ui.s(1), 14, mass > cap ? Ui.RED : Ui.TXT);
        ui.b.rect(x, wy + ui.s(20), dollW(), ui.s(4), 0xFF1A1C1B);
        ui.b.rect(x, wy + ui.s(20), dollW() * Math.min(1, mass / Math.max(1f, cap)), ui.s(4), mass > cap ? Ui.RED : 0xFF9AA08C);
        return wy + ui.s(28) - y;
    }

    public void draw(Profile p, float x, float y, float w, float h, Container other) {
        Ui ui = c.ui;
        InventoryUi inv = c.inv;
        Loadout l = p.loadout;
        // the equipment is anchored: on the left, at the top, the same size whatever the case has grown to
        doll(l, x, y, l.caseGrid, true, l.mass(), p.prog.carryCapacityKg());
        float dw = dollW() + ui.s(22);
        x += dw; w -= dw;
        float tw = (w - ui.s(8)) / TABS.length;
        for (int i = 0; i < TABS.length; i++) if (ui.tab("lt" + i, x + i * (tw + ui.s(2)), y, tw, ui.s(30), TABS[i], TAB_GLYPH[i], tab == i)) tab = i;
        float top = y + ui.s(42);
        switch (tab) {
            case 0 -> {
                // (no name over the case: it is the only grid the survivor has, and its size says its rank)
                inv.grid(l.caseGrid, x + ui.s(2), top, null);
                float below = top + l.caseGrid.h * inv.cell;
                // an equipped backpack adds its own grid under the case: deep storage, not reachable for reloads
                Container pack = l.packGrid();
                if (pack != null) { ui.text(l.slot("backpack").name() + "  (storage)", x + ui.s(2), below + ui.s(10), 13, Ui.TXT_DIM); inv.grid(pack, x + ui.s(2), below + ui.s(32), null); below += ui.s(32) + pack.h * inv.cell; }
                inv.hints(x + ui.s(2), below + ui.s(10), Math.max(l.caseGrid.w * inv.cell, ui.s(420)));
            }
            case 1 -> { pouchHeader("Treasures - no case space. Combine pairs for value.", x, top); inv.pouch(l.treasures, x, top + ui.s(4), w, h * 0.42f, "No treasures"); recipes("treasure", p, x, top + h * 0.42f + ui.s(16), w); }
            case 2 -> { pouchHeader("Keys - profile-bound, kept even on death.", x, top); inv.pouch(l.keys, x, top + ui.s(4), w, h * 0.5f, "No keys"); }
            case 3 -> { pouchHeader("Craft materials - no case space.", x, top); inv.pouch(l.craft, x, top + ui.s(4), w, h * 0.32f, "No materials"); recipes("field", p, x, top + h * 0.32f + ui.s(16), w); }
            default -> { pouchHeader("Documents - read for intel and contract progress.", x, top); inv.pouch(l.documents, x, top + ui.s(4), w, h * 0.5f, "No documents"); }
        }
    }

    private void pouchHeader(String s, float x, float y) { c.ui.text(s, x, y + c.ui.s(2), 13, Ui.TXT_DIM); }

    private float craftScroll;
    private final java.util.Map<String, ItemInstance> craftIcons = new java.util.HashMap<>();
    /** Recipes drawn by the last frame's craft list (gates and the capture script). */
    public int craftShown;

    /**
     * 0.7, the craft list. It shows what can be made right now and nothing else: every recipe of this kind whose
     * ingredients are all to hand, as a card - what comes out and how many, what goes in and how much of each is
     * owned, a button to make one and a button to make as many as the materials allow. In the hideout what is in the
     * stash counts as to hand; on an operation only what is carried does. The list scrolls inside its own panel (it
     * used to be drawn straight down the screen with no end, which is where it ran off the bottom), and when nothing
     * can be made it says so, and says how close the nearest recipe is.
     */
    private void recipes(String where, Profile p, float x, float y, float w) {
        Ui ui = c.ui;
        boolean stash = raid == null;
        List<Content.Recipe> can = new ArrayList<>();
        Content.Recipe nearest = null;
        int nearestMissing = 99, kinds = 0;
        for (Content.Recipe r : Content.RECIPES) {
            if (!r.where.equals(where)) continue;
            kinds++;
            if (canCraft(p, r, stash)) { can.add(r); continue; }
            int missing = 0;
            for (Object[] i : r.inputs) missing += Math.max(0, (Integer) i[1] - owned(p, (String) i[0], stash));
            if (missing < nearestMissing) { nearestMissing = missing; nearest = r; }
        }
        craftShown = can.size();
        // 1.0, the bench as a bench: every recipe of this kind is on it - what can be made now first and bright, the rest
        // behind them, dimmed, each saying exactly what it is short of. A recipe is a card: what goes in, each ingredient
        // with its own picture and how much of it there is against how much is wanted; an arrow; what comes out.
        // (What can be made, and what making it does, is as it was.)
        float h = ui.h - y - ui.s(raid == null ? 64 : 112);
        ui.text(where.equals("treasure") ? "COMBINE" : "CRAFTING", x, y, 15, Ui.TXT_HI);
        ui.textR(can.isEmpty() ? "nothing can be made yet" : can.size() + " of " + kinds + " can be made now" + (stash ? "  -  stash counted" : ""), x + w, y + ui.s(2), 13, can.isEmpty() ? Ui.TXT_DIM : Ui.GREEN);
        float top = y + ui.s(26);
        ui.panelDark(x, top, w, h);
        List<Content.Recipe> all = new ArrayList<>(can);
        for (Content.Recipe r : Content.RECIPES) if (r.where.equals(where) && !can.contains(r)) all.add(r);
        if (all.isEmpty()) { ui.textC("There is nothing to make here.", x + w / 2, top + h / 2 - ui.s(10), 14, Ui.TXT_DIM); return; }
        int cols = w > ui.s(760) ? 2 : 1;
        float gap = ui.s(8), cw = (w - gap * (cols + 1)) / cols, ch = ui.s(86);
        int rows = (all.size() + cols - 1) / cols;
        float content = rows * (ch + gap) + gap, max = Math.max(0, content - h);
        if (ui.hover(x, top, w, h)) craftScroll -= (float) c.in.scroll * ui.s(60);
        craftScroll = Math.max(0, Math.min(max, craftScroll));
        for (int k = 0; k < all.size(); k++) {
            Content.Recipe r = all.get(k);
            boolean ok = k < can.size();
            float cx = x + gap + (k % cols) * (cw + gap), cy = top + gap + (k / cols) * (ch + gap) - craftScroll;
            if (cy < top - 1 || cy + ch > top + h + 1) continue;                  // whole cards only: nothing is drawn half out of the panel
            boolean over = ok && ui.hover(cx, cy, cw, ch);
            float al = ok ? 1f : 0.5f;
            ui.b.rect(cx, cy, cw, ch, ok ? 0xFF181B19 : 0xFF131514);
            ui.b.outline(cx, cy, cw, ch, 1, over ? 0xFFE2A034 : ok ? 0xFF4A4E46 : 0xFF2A2C29);
            ui.b.rect(cx, cy, ui.s(3), ch, ok ? 0xFF7DBA5C : 0xFF3A3C38);
            // what comes out: on the right, in its own well
            float box = ch - ui.s(34), ox = cx + cw - box - ui.s(96);
            ItemInstance icon = craftIcons.computeIfAbsent(r.outId, id -> new ItemInstance(-9, id));
            ui.b.rect(ox, cy + ui.s(8), box, box, 0xFF0C0D0C);
            ui.b.outline(ox, cy + ui.s(8), box, box, 1, 0xFF30342F);
            float ar = icon.baseW() / (float) Math.max(1, icon.baseH()), iw = Math.min(box - ui.s(6), (box - ui.s(6)) * ar), ih = iw / ar;
            c.icons.draw(ui.b, icon, Math.round(ox + (box - iw) / 2), Math.round(cy + ui.s(8) + (box - ih) / 2), iw, ih, Ui.alpha(0xFFFFFFFF, al));
            if (r.outQty > 1) ui.textR("x" + r.outQty, ox + box - ui.s(3), cy + ui.s(8) + box - ui.s(16), 13, Ui.alpha(Ui.TXT_HI, al));
            Layout.fit(ui, Content.def(r.outId).name, cx + ui.s(12), cy + ch - ui.s(22), cw - ui.s(120), 14, 10, Ui.alpha(Ui.TXT_HI, al));
            // what goes in: a chip to each ingredient - its picture, and have / want
            float ix = cx + ui.s(12), chip = ui.s(40);
            int times = 999;
            for (Object[] i : r.inputs) {
                int have = owned(p, (String) i[0], stash), need = (Integer) i[1];
                times = Math.min(times, have / Math.max(1, need));
                ItemInstance in = craftIcons.computeIfAbsent((String) i[0], id -> new ItemInstance(-9, id));
                String cnt = have + " / " + need;
                float cwid = chip + ui.tw(cnt, 13) + ui.s(14);
                if (ix + cwid > ox - ui.s(30)) break;
                ui.b.rect(ix, cy + ui.s(10), cwid, chip, 0xFF101211);
                ui.b.outline(ix, cy + ui.s(10), cwid, chip, 1, have >= need ? 0xFF30342F : 0xFF6A2C26);
                float ia = in.baseW() / (float) Math.max(1, in.baseH()), iiw = Math.min(chip - ui.s(8), (chip - ui.s(8)) * ia), iih = iiw / ia;
                c.icons.draw(ui.b, in, Math.round(ix + ui.s(4) + (chip - ui.s(8) - iiw) / 2), Math.round(cy + ui.s(10) + (chip - iih) / 2), iiw, iih, Ui.alpha(0xFFFFFFFF, al));
                ui.text(cnt, ix + chip, cy + ui.s(22), 13, have >= need ? Ui.alpha(Ui.TXT, al) : Ui.RED);
                if (ui.hover(ix, cy + ui.s(10), cwid, chip)) ui.tip(Content.def((String) i[0]).name, "^d" + need + " needed, " + have + " to hand");
                ix += cwid + ui.s(6);
            }
            // the arrow from one to the other
            float ay = cy + ui.s(8) + box / 2, ax0 = ox - ui.s(26);
            ui.b.rect(ax0, ay - 1, ui.s(14), 2, Ui.alpha(0xFF8E897A, al));
            ui.b.line(ax0 + ui.s(14), ay, ax0 + ui.s(8), ay - ui.s(5), 2, Ui.alpha(0xFF8E897A, al)); ui.b.line(ax0 + ui.s(14), ay, ax0 + ui.s(8), ay + ui.s(5), 2, Ui.alpha(0xFF8E897A, al));
            float bw = ui.s(82), bx = cx + cw - bw - ui.s(8);
            if (ok) {
                if (ui.button("craft" + r.id, bx, cy + ui.s(10), bw, ui.s(30), "CRAFT", null, true)) made(p, r, 1, stash);
                if (times > 1 && ui.button("craftall" + r.id, bx, cy + ui.s(46), bw, ui.s(30), "ALL x" + Math.min(times, 20), null, true)) made(p, r, Math.min(times, 20), stash);
            } else ui.textR("short", cx + cw - ui.s(14), cy + ui.s(16), 13, 0xFF8A4A42);
        }
        if (max > 0) {
            float th = h * h / content, ty = top + (h - th) * (craftScroll / max);
            ui.sprite("white", x + w - ui.s(5), ty, ui.s(3), th, 0x90FFFFFF);
        }
    }

    private void made(Profile p, Content.Recipe r, int times, boolean stash) {
        int n = 0;
        String res = null;
        for (int i = 0; i < times; i++) { res = craft(p, r, stash); if (res != null) break; n++; }
        if (n > 0) { c.inv.say("Crafted " + n * r.outQty + "x " + Content.def(r.outId).name, Ui.GREEN); c.sfx(r.outId.startsWith("ammo.") ? "ammo_crafted" : "ui_buy", 0.6f); }
        else { c.inv.say(res, Ui.RED); c.sfx("ui_error", 0.6f); }
    }

    public static boolean canCraft(Profile p, Content.Recipe r) { return canCraft(p, r, false); }

    /** With stash: what is in the stash counts too (the hideout). */
    public static boolean canCraft(Profile p, Content.Recipe r, boolean stash) {
        for (Object[] i : r.inputs) if (owned(p, (String) i[0], stash) < (Integer) i[1]) return false;
        return true;
    }

    public static int owned(Profile p, String id) { return owned(p, id, false); }

    public static int owned(Profile p, String id, boolean stash) {
        int n = 0;
        for (ItemInstance i : p.loadout.all()) if (i.defId.equals(id) && i.parent == null) n += i.qty;
        if (stash) for (ItemInstance i : p.stash.items) if (i.defId.equals(id) && i.parent == null) n += i.qty;
        return n;
    }

    public static String craft(Profile p, Content.Recipe r) { return craft(p, r, false); }

    /** Validates, reserves output space, then consumes inputs - all or nothing. */
    public static String craft(Profile p, Content.Recipe r, boolean stash) {
        if (!canCraft(p, r, stash)) return "Missing ingredients";
        ItemInstance out = new ItemInstance(r.outId);
        out.qty = r.outQty;
        out.origin = "crafted";
        if (p.loadout.pouchFor(out.def()) == null && p.loadout.caseGrid.findSpace(out) == null) {
            boolean stackRoom = false;
            if (out.def().stackable()) for (ItemInstance i : p.loadout.caseGrid.items) if (i.defId.equals(out.defId) && out.def().maxStack - i.qty >= out.qty) stackRoom = true;
            // in the hideout what does not fit the case goes to the stash, as anything made at a bench would
            if (!stackRoom && !stash) return "No case space for the result";
        }
        for (Object[] in : r.inputs) {
            int need = (Integer) in[1];
            List<ItemInstance> pool = new ArrayList<>();
            for (ItemInstance i : p.loadout.all()) if (i.defId.equals(in[0]) && i.parent == null) pool.add(i);
            if (stash) for (ItemInstance i : p.stash.items) if (i.defId.equals(in[0]) && i.parent == null) pool.add(i);
            for (ItemInstance i : pool) { if (need <= 0) break; int k = Math.min(need, i.qty); InventoryService.consume(i, k); need -= k; }
        }
        if (!InventoryService.store(out, p.loadout).ok() && stash) { while (!InventoryService.add(out, p.stash).ok()) p.stash.h += 4; }
        return null;
    }

    // ------------------------------------------------------------------ context actions (shared)
    /** Executes a context-menu action. Returns feedback text. */
    public String act(Profile p, ItemInstance it, String action, Container side) {
        ItemDef d = it.def();
        Loadout l = p.loadout;
        Container home = it.container != null ? it.container : l.caseGrid;
        InventoryService.Result r;
        switch (action) {
            case "use" -> {
                // 0.6: a quick slot can hold anything; only medicine and food are "used" (crash.log: d.food was null for a weapon)
                if (d.medical == null && d.food == null) return d.name + " cannot be used like that";
                if (raid != null) {
                    if (raid.body.using != null) return "Already using something";
                    raid.body.using = d;
                    raid.body.useTimer = d.medical != null ? d.medical.useTime : d.food.useTime;
                    pendingUse = it;
                    return "Using " + d.name + "...";
                }
                // hideout: immediate, applies to the persistent body
                if (d.medical != null) p.health = Math.min(100, p.health + Math.max(10, d.medical.heal + d.medical.healFrac * 100));
                if (d.food != null) { p.energy = Math.min(100, p.energy + d.food.energy); p.hydration = Math.min(100, p.hydration + d.food.hydration); }
                consumeOne(it);
                return "Used " + d.name;
            }
            case "read" -> {
                c.bus.emit(new Events.DocumentRead(d.id + "#" + it.uid));
                return readDocument(p, it);
            }
            case "equip" -> { r = InventoryService.equip(it, l, d.charm != null ? firstFreeCharm(l) : d.slot); return r.ok() ? "Equipped " + d.name : r.msg(); }
            case "unload" -> { r = InventoryService.unload(it, home); return r.ok() ? "Unloaded" : r.msg(); }
            case "removemag" -> { r = InventoryService.removeMag(it, home); return r.ok() ? "Magazine removed" : r.msg(); }
            case "strip" -> {
                // attachments are fitted at the Gun Workbench, not carried loose: stripping simply takes them off
                for (String s : new ArrayList<>(it.attachments.keySet())) absentialis.gameplay.weapons.Gunsmith.remove(it, s);
                if (it.spill > 0) { ItemInstance sp = new ItemInstance(Content.ammoFor(d.weapon.caliber).id); sp.qty = it.spill; it.spill = 0; InventoryService.add(sp, home); }
                return "Attachments removed - refit them at the Workbench";
            }
            case "split" -> {
                int[] sp = home.findSpace(new ItemInstance(-3, it.defId));
                if (sp == null) return "No room to split";
                r = InventoryService.split(it, it.qty / 2, home, sp[0], sp[1]);
                return r.ok() ? "Split" : r.msg();
            }
            case "clearjam" -> { absentialis.gameplay.weapons.WeaponService.clearJam(it); return "Jam cleared (round lost)"; }
            case "drop" -> {
                if (raid == null) return "Cannot drop in the hideout";
                InventoryService.release(it);
                raid.dropItem(it, raid.player.x, raid.player.y + 0.5f, raid.player.z);
                return "Dropped " + d.name;
            }
            case "quick" -> {
                for (int k = 0; k < Loadout.QUICK_SLOTS; k++) if (l.quick[k] == 0 || l.findUid(l.quick[k]) == null) { l.quick[k] = it.uid; return "Bound to quick slot " + (k + 1); }
                l.quick[0] = it.uid;
                return "Bound to quick slot 1";
            }
            default -> { return null; }
        }
    }

    public ItemInstance pendingUse;

    private static String firstFreeCharm(Loadout l) { for (int k = 0; k < Loadout.CHARM_SLOTS; k++) if (l.charms[k] == null) return "charm" + k; return "charm0"; }

    public static void consumeOne(ItemInstance it) {
        ItemDef d = it.def();
        if (d.medical != null && d.medical.uses > 1 && it.uses > 1) { it.uses--; return; }
        InventoryService.consume(it, 1);
    }

    private final java.util.Set<Long> readDocs = new java.util.HashSet<>();

    /** Documents yield intel: reveal a cache or nest area on the map and progress INTEL contracts. */
    private String readDocument(Profile p, ItemInstance it) {
        // 0.8: evidence says what it says, every time it is read - and having read it is having seen it
        absentialis.gameplay.missions.Story.Evidence ev = absentialis.gameplay.missions.Story.evidence(it.defId);
        if (ev != null) {
            boolean first = readDocs.add(it.uid);
            p.missions.onEvidence(it.defId, p.zone.day);
            if (first) { p.contracts.onDocument(); if (raid != null) raid.debrief.documents++; c.sfx("file_scroll", 0.5f); }
            return ev.text();
        }
        if (!readDocs.add(it.uid)) return "You have already read this.";
        var rng = new absentialis.engine.util.Rng(it.uid * 31 + p.seed);
        String[] lines = {
                "\"...the Deacon says the bells call them. I say the bells call *something*.\"",
                "\"Convoy 7 never reached the checkpoint. Kessler's men had the bridge.\"",
                "\"Water's bad past the canal. Boil it twice. Pray once.\"",
                "\"Substation K-4 can be rebuilt. Two sets of breakers. Then the trains run.\"",
                "\"They hear you more than they see you. Remember that.\""};
        String text = lines[rng.nextInt(lines.length)];
        if (c.world != null && !c.world.map.loot.isEmpty()) {
            var spot = c.world.map.loot.get(rng.nextInt(c.world.map.loot.size()));
            int n = p.discover(spot.x, spot.z, 4);
            text += "  [Map updated: " + spot.district.display + "" + (n > 0 ? ", " + n + " cells" : "") + "]";
        }
        p.contracts.onDocument();
        if (raid != null) raid.debrief.documents++;
        return text;
    }
}
