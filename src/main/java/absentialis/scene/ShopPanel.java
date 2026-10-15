package absentialis.scene;

import absentialis.Version;
import absentialis.gameplay.economy.Economy;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.gameplay.weapons.Gunsmith;
import absentialis.persistence.Profile;
import absentialis.persistence.SaveSystem;
import absentialis.ui.Ui;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The SHOP (v0.3) - replaces the old trader lists outright. Three columns: a department rail, a
 * wall of item cards coloured by loot tier, and a showcase with the selected item turning on a
 * lit plinth, its statistics, a quantity stepper and the purchase button. A SELL department turns
 * the wall into the player's own goods. Prices are in cash; weapons cost exactly their listed price.
 *
 * 0.7: four item departments - Weapons, Medical, Supplies, Armor - then Vehicles and Sell. A weapon whose Rank
 * has not been reached is not on the wall at all (its name is on the Operator tab's arsenal, where an Unlock
 * Token can still open it early). From Rank 4 the rail also carries Recruits: squadmates are hired here.
 */
public final class ShopPanel {
    private final Ctx c;
    private int dept;                 // 0..3 item departments, then VEHICLES, SELL, RECRUITS
    public static final int VEHICLES = 4, SELL = 5, RECRUITS = 6;
    private static final int[] RAIL = {0, 1, 2, 3, VEHICLES, RECRUITS, SELL};
    /** The squad tab's recruiting board is drawn here too: people are hired from the shop (0.7). */
    public SquadPanel squad;
    private final GaragePanel garage;
    private String weaponClass = "all";
    private ItemDef selected;
    private ItemInstance sellSel;
    private int qty = 1;
    private float scroll, shownCoins = -1, pulse, spin;
    private final Map<String, ItemInstance> probes = new HashMap<>();
    private static final String[] GLYPH = {"handgun", "heart", "bench", "inv", "key", "coin", "radio"};
    private static final String[] RAIL_LABEL = {"WEAPONS", "MEDICAL", "SUPPLIES", "ARMOR", "VEHICLES", "SELL", "RECRUITS"};
    private static final String[][] CLASSES = {{"all", "ALL"}, {"handgun", "HANDGUNS"}, {"shotgun", "SHOTGUNS"}, {"rifle", "RIFLES"}, {"magnum", "MAGNUMS"}, {"auto", "AUTOMATICS"}, {"special", "SPECIAL"}};

    /** Opens a department directly (scripted visual verification). */
    public void show(int department) { dept = department; scroll = 0; selected = null; sellSel = null; }

    public ShopPanel(Ctx c) { this.c = c; garage = new GaragePanel(c); }

    private ItemInstance probe(ItemDef d) { return probes.computeIfAbsent(d.id, k -> new ItemInstance(-60 - probes.size(), d.id)); }

    /** 1.0: how the shelf is ordered: as stocked, rarest first, dearest first, cheapest first. */
    public int sort;
    public static final String[] SORTS = {"DEFAULT", "RARITY", "PRICE HIGH", "PRICE LOW"};

    private List<ItemDef> stock(Profile p) {
        List<ItemDef> l = new ArrayList<>();
        for (ItemDef d : Content.ITEMS.values()) {
            if (Economy.shopCategory(d) != dept || !Economy.shopShows(d, p.prog)) continue;
            if (dept == 0 && !weaponClass.equals("all") && !(weaponClass.equals("special") ? d.weapon.family.equals("special") || d.weapon.melee() : d.weapon.family.equals(weaponClass))) continue;
            l.add(d);
        }
        l.sort((a, b) -> dept == 0 ? (a.rank != b.rank ? Integer.compare(a.rank, b.rank) : Integer.compare(a.value, b.value)) : Integer.compare(a.value, b.value));
        // 1.0: or as the customer asks - by rarity (the rarest first), or by price either way
        if (sort == 1) l.sort((a, b) -> a.tier != b.tier ? Integer.compare(b.tier, a.tier) : Integer.compare(Economy.shopBuy(b), Economy.shopBuy(a)));
        else if (sort == 2) l.sort((a, b) -> Integer.compare(Economy.shopBuy(b), Economy.shopBuy(a)));
        else if (sort == 3) l.sort((a, b) -> Integer.compare(Economy.shopBuy(a), Economy.shopBuy(b)));
        return l;
    }

    private List<ItemInstance> sellable(Profile p) {
        List<ItemInstance> l = new ArrayList<>();
        for (ItemInstance i : p.stash.items) if (Economy.shopSell(i) > 0) l.add(i);
        for (ItemInstance i : p.loadout.caseGrid.items) if (Economy.shopSell(i) > 0) l.add(i);
        for (ItemInstance i : p.loadout.treasures) if (Economy.shopSell(i) > 0) l.add(i);
        l.sort((a, b) -> Integer.compare(Economy.shopSell(b), Economy.shopSell(a)));
        if (sort == 1) l.sort((a, b) -> a.def().tier != b.def().tier ? Integer.compare(b.def().tier, a.def().tier) : Integer.compare(Economy.shopSell(b), Economy.shopSell(a)));
        else if (sort == 3) l.sort((a, b) -> Integer.compare(Economy.shopSell(a), Economy.shopSell(b)));
        return l;
    }

    public void draw(Ui ui, Profile p, float top) {
        float W = ui.w - ui.s(48), H = ui.h - top - ui.s(24), x = ui.s(24);
        pulse += ui.dt;
        spin += ui.dt * 0.7f;
        if (shownCoins < 0) shownCoins = p.scrip;
        shownCoins += (p.scrip - shownCoins) * Math.min(1, ui.dt * 7);

        // ---- backdrop: a dark showroom with a slow light sweep
        ui.shadow(x, top, W, H, 0x80);
        ui.b.rect(x, top, W, H, 0xFF0C0D0E);
        for (int i = 0; i < 9; i++) ui.b.rect(x, top + H * i / 9f, W, 1, 0xFF15171A);
        float sweep = (pulse * 0.07f) % 1.3f - 0.15f;
        ui.b.rect(x + W * sweep, top, ui.s(120), H, 0x06FFFFFF);
        ui.b.outline(x, top, W, H, 2, 0xFF8A6420);

        // ---- header strip
        float hh = ui.s(64);
        ui.b.rect(x + 2, top + 2, W - 4, hh, 0xFF16181B);
        ui.b.rect(x + 2, top + hh, W - 4, ui.s(2), 0xFFE2A034);
        ui.glyph("coin", x + ui.s(18), top + ui.s(18), Math.max(2, ui.s(2)), 0xFFFFFFFF);
        ui.text("THE MERCHANT", x + ui.s(52), top + ui.s(10), 26, Ui.TXT_HI);
        ui.text("Whatever you are buying - " + (dept == SELL ? "or selling" : "it is here") + ", stranger. Cash only.", x + ui.s(54), top + ui.s(42), 12, Ui.TXT_DIM);
        String bal = Version.coins(Math.round(shownCoins));
        float bw = ui.tw(bal, 30);
        ui.textR(bal, x + W - ui.s(22), top + ui.s(10), 30, Ui.AMBER);
        ui.textR("BALANCE" + (p.prog.unlockTokens > 0 ? "   -   " + p.prog.unlockTokens + " UNLOCK TOKEN" + (p.prog.unlockTokens > 1 ? "S" : "") : ""), x + W - ui.s(22), top + ui.s(44), 11, Ui.TXT_DIM);

        float bodyY = top + hh + ui.s(12), bodyH = H - hh - ui.s(22);
        // ---- department rail
        float railW = ui.s(190);
        if (dept == RECRUITS && !SquadPanel.unlocked(p)) dept = 0;
        for (int n = 0, row = 0; n < RAIL.length; n++) {
            int i = RAIL[n];
            if (i == RECRUITS && !SquadPanel.unlocked(p)) continue;      // nobody to hire, and nowhere to put them, before Rank 4
            String label = RAIL_LABEL[i];
            float ry = bodyY + row++ * ui.s(50) + (i == SELL ? ui.s(18) : 0);
            boolean sel = dept == i, hov = ui.hover(x + ui.s(10), ry, railW, ui.s(44));
            float k = ui.ease("shopdept" + i, hov || sel);
            ui.b.rect(x + ui.s(10), ry, railW, ui.s(44), sel ? 0xFF2A2416 : Ui.mix(0xFF14161A, 0xFF1E2024, k));
            ui.b.rect(x + ui.s(10), ry, ui.s(4) + ui.s(4) * k, ui.s(44), sel ? 0xFFE2A034 : i == SELL ? 0xFF7DBA5C : 0xFF4A4436);
            ui.glyph(GLYPH[i], x + ui.s(24) + ui.s(3) * k, ry + ui.s(10), Math.max(2, ui.s(2)), sel ? 0xFFFFFFFF : 0xB0FFFFFF);
            ui.text(label, x + ui.s(58) + ui.s(3) * k, ry + ui.s(13), 15, sel ? Ui.TXT_HI : Ui.TXT);
            if (hov && ui.click && !ui.consumed) { ui.consumed = true; dept = i; scroll = 0; selected = null; sellSel = null; qty = 1; if (ui.sound != null) ui.sound.accept("ui_tab"); }
        }

        if (dept == VEHICLES) { garage.draw(ui, p, x + railW + ui.s(26), bodyY, W - railW - ui.s(38), bodyH); return; }
        if (dept == RECRUITS) { if (squad != null) squad.recruiting(ui, p, x + railW + ui.s(26), bodyY, W - railW - ui.s(38), bodyH); return; }
        float showW = Math.min(ui.s(430), W * 0.32f);
        float gx = x + railW + ui.s(26), gw = W - railW - showW - ui.s(52);
        float gy = bodyY;
        // ---- 1.0: the order of the shelf - as stocked, by rarity, or by price from either end
        if (dept != RECRUITS) {
            float sw = ui.s(118), sx = gx + gw - SORTS.length * sw;
            ui.textR("SORT", sx - ui.s(10), gy + ui.s(6), 13, Ui.TXT_DIM);
            for (int i = 0; i < SORTS.length; i++) if (ui.tab("shopsort" + i, sx + i * sw, gy, sw - ui.s(4), ui.s(26), SORTS[i], null, sort == i)) { sort = i; scroll = 0; }
            gy += ui.s(34);
        }
        // ---- weapon class filter
        if (dept == 0) {
            float fw = gw / CLASSES.length;
            for (int i = 0; i < CLASSES.length; i++) if (ui.tab("shopcls" + i, gx + i * fw, gy, fw - ui.s(4), ui.s(28), CLASSES[i][1], null, weaponClass.equals(CLASSES[i][0]))) { weaponClass = CLASSES[i][0]; scroll = 0; }
            gy += ui.s(36);
        }
        float gh = bodyY + bodyH - gy;
        int cols = Math.max(2, (int) (gw / ui.s(210)));
        float cw = (gw - (cols - 1) * ui.s(10)) / cols, ch = ui.s(132);
        int count = dept == SELL ? sellable(p).size() : stock(p).size();
        int rows = (count + cols - 1) / cols;
        float maxScroll = Math.max(0, rows * (ch + ui.s(10)) - gh);
        if (ui.hover(gx, gy, gw, gh)) scroll -= (float) c.in.scroll * ui.s(60);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        ui.b.scissor((int) gx - 4, (int) gy, (int) gw + 8, (int) gh);
        if (dept == SELL) {
            List<ItemInstance> items = sellable(p);
            if (items.isEmpty()) ui.textC("Nothing to sell. Bring something back from the Zone.", gx + gw / 2, gy + ui.s(60), 15, Ui.TXT_DIM);
            for (int i = 0; i < items.size(); i++) {
                ItemInstance it = items.get(i);
                float cx = gx + (i % cols) * (cw + ui.s(10)), cy = gy + (i / cols) * (ch + ui.s(10)) - scroll;
                if (cy + ch < gy || cy > gy + gh) continue;
                boolean hov = ui.hover(cx, Math.max(cy, gy), cw, ch) && ui.my < gy + gh;
                card(ui, it.def(), it, cx, cy, cw, ch, hov, it == sellSel, "+" + Version.coins(Economy.shopSell(it)), Ui.GREEN, null, it.def().stackable() && it.qty > 1 ? "x" + it.qty : it.slot != null ? "equipped" : null);
                if (hov && ui.click && !ui.consumed) { ui.consumed = true; sellSel = it; if (ui.sound != null) ui.sound.accept("ui_click"); }
            }
        } else {
            List<ItemDef> items = stock(p);
            if ((selected == null || !items.contains(selected)) && !items.isEmpty()) selected = items.get(0);
            if (items.isEmpty()) { selected = null; ui.textC("Nothing in this department at your Rank yet.", gx + gw / 2, gy + ui.s(60), 15, Ui.TXT_DIM); }
            for (int i = 0; i < items.size(); i++) {
                ItemDef d = items.get(i);
                float cx = gx + (i % cols) * (cw + ui.s(10)), cy = gy + (i / cols) * (ch + ui.s(10)) - scroll;
                if (cy + ch < gy || cy > gy + gh) continue;
                boolean hov = ui.hover(cx, Math.max(cy, gy), cw, ch) && ui.my < gy + gh;
                boolean locked = d.weapon != null && !p.prog.platformUnlocked(d.id);
                int price = Economy.shopBuy(d);
                int owned = owned(p, d.id);
                card(ui, d, probe(d), cx, cy, cw, ch, hov, d == selected, Version.coins(price) + (d.stackable() ? " ea" : ""), p.scrip >= price ? Ui.AMBER : Ui.RED, locked ? "RANK " + d.rank : null, owned > 0 ? "owned " + owned : null);
                if (hov && ui.click && !ui.consumed) { ui.consumed = true; selected = d; qty = 1; if (ui.sound != null) ui.sound.accept("ui_click"); }
            }
        }
        ui.b.noScissor();
        if (maxScroll > 0) { float th = gh * gh / (gh + maxScroll); ui.b.rect(gx + gw + ui.s(4), gy + (gh - th) * scroll / maxScroll, ui.s(4), th, 0xFF8A6420); }

        // ---- showcase
        float sx = x + W - showW - ui.s(12);
        ui.b.rect(sx, bodyY, showW, bodyH, 0xFF121417);
        ui.b.outline(sx, bodyY, showW, bodyH, 1, 0xFF2E2A22);
        if (dept == SELL) showcaseSell(ui, p, sx, bodyY, showW, bodyH); else showcaseBuy(ui, p, sx, bodyY, showW, bodyH);
    }

    private static int owned(Profile p, String id) { int n = 0; for (ItemInstance i : p.allItems()) if (i.defId.equals(id) && i.parent == null) n += i.qty; return n; }

    /** One card on the wall: tier band, voxel icon, name, price, and a lock plate for weapons above your Rank. */
    private void card(Ui ui, ItemDef d, ItemInstance icon, float x, float y, float w, float h, boolean hov, boolean sel, String price, int priceCol, String lock, String note) {
        float k = ui.ease("shopcard" + d.id + (icon.uid > 0 ? icon.uid : ""), hov || sel);
        float lift = ui.s(3) * k;
        int tier = d.tierColor();
        ui.b.rect(x + ui.s(3), y + ui.s(4), w, h, 0x60000000);
        ui.b.rect(x, y - lift, w, h, sel ? 0xFF26221A : 0xFF191B1F);
        ui.b.rect(x, y - lift, w, ui.s(4), tier);
        ui.b.rect(x, y - lift + ui.s(4), w, ui.s(26), Ui.alpha(tier, 0.10f + 0.10f * k));
        ui.b.outline(x, y - lift, w, h, sel ? 2 : 1, sel ? 0xFFE2A034 : Ui.mix(0xFF2C2E33, tier, k * 0.7f));
        float iw = w - ui.s(24), ih = h - ui.s(64);
        c.icons.draw(ui.b, icon, x + ui.s(12), y - lift + ui.s(10), iw, ih, lock != null ? 0x70FFFFFF : 0xFFFFFFFF);
        String name = d.name;
        while (ui.tw(name, 13) > w - ui.s(16) && name.length() > 4) name = name.substring(0, name.length() - 2);
        ui.text(name.equals(d.name) ? name : name + "..", x + ui.s(8), y - lift + h - ui.s(46), 13, Ui.TXT_HI);
        ui.text(d.tierName().toUpperCase(), x + ui.s(8), y - lift + h - ui.s(26), 10, tier);
        ui.textR(price, x + w - ui.s(8), y - lift + h - ui.s(28), 14, lock != null ? Ui.TXT_DIM : priceCol);
        if (note != null) ui.textR(note, x + w - ui.s(8), y - lift + ui.s(8), 10, Ui.TXT_DIM);
        if (lock != null) {
            ui.b.rect(x, y - lift + h * 0.34f, w, ui.s(26), 0xD0101012);
            ui.glyph("lock", x + ui.s(10), y - lift + h * 0.34f + ui.s(4), Math.max(1, ui.s(1)), 0xFFFFFFFF);
            ui.text(lock, x + ui.s(34), y - lift + h * 0.34f + ui.s(6), 13, Ui.AMBER);
        }
    }

    private float plinth(Ui ui, ItemInstance it, ItemDef d, float x, float y, float w) {
        float vs = Math.min(w - ui.s(40), ui.s(250));
        float px = x + (w - vs) / 2;
        // lit plinth: radial steps and a tier-coloured rim
        for (int i = 0; i < 6; i++) ui.b.rect(px + vs * 0.06f * i, y + ui.s(14) + i * 2, vs - vs * 0.12f * i, vs - i * 4, Ui.alpha(0xFF1E2228, 0.5f));
        ui.b.rect(px + vs * 0.1f, y + ui.s(14) + vs - ui.s(12), vs * 0.8f, ui.s(4), Ui.alpha(d.tierColor(), 0.55f + 0.25f * (float) Math.sin(pulse * 2.2f)));
        c.icons.drawInspect(ui.b, it, px, y + ui.s(10), vs, spin, 0.32f + 0.06f * (float) Math.sin(pulse * 0.8f));
        float ty = y + vs + ui.s(22);
        ui.textC(d.name.toUpperCase(), x + w / 2, ty, 20, Ui.TXT_HI);
        ui.textC(d.tierName().toUpperCase() + "  -  " + d.category.toUpperCase(), x + w / 2, ty + ui.s(26), 11, d.tierColor());
        return ty + ui.s(46);
    }

    private float stats(Ui ui, ItemInstance it, ItemDef d, float x, float y, float w, float limit) {
        if (!d.desc.isEmpty()) y += ui.wrap(d.desc, x + ui.s(16), y, w - ui.s(32), 12, Ui.TXT_DIM) + ui.s(8);
        if (d.weapon != null && !d.weapon.melee()) {
            float[] pr = Gunsmith.primary(it);
            for (int k = 0; k < pr.length && y < limit; k++) {
                ui.text(Gunsmith.PRIMARY[k], x + ui.s(16), y, 12, Ui.TXT);
                ui.bar(x + w * 0.46f, y + ui.s(3), w * 0.5f - ui.s(16), ui.s(9), pr[k], 0xFFB0A070);
                y += ui.s(17);
            }
            ItemDef am = Content.ammoFor(d.weapon.caliber);
            if (y < limit) { ui.text("Ammunition", x + ui.s(16), y, 12, Ui.TXT); ui.textR((am == null ? "-" : am.name) + "  x" + d.weapon.capacity, x + w - ui.s(16), y, 12, Ui.AMBER); y += ui.s(17); }
            if (y < limit) { ui.text("Inventory size", x + ui.s(16), y, 12, Ui.TXT); ui.textR(d.w + " x " + d.h, x + w - ui.s(16), y, 12, Ui.AMBER); y += ui.s(17); }
        } else for (String[] s : absentialis.ui.InventoryUi.stats(it)) {
            if (y > limit || s[0].equals("Value") || s[0].equals("Origin")) continue;
            ui.text(s[0], x + ui.s(16), y, 12, Ui.TXT);
            if (s.length > 2) ui.bar(x + w * 0.46f, y + ui.s(3), w * 0.5f - ui.s(16), ui.s(9), Float.parseFloat(s[2]), 0xFFB0A070); else ui.textR(s[1], x + w - ui.s(16), y, 12, Ui.AMBER);
            y += ui.s(17);
        }
        return y;
    }

    private void showcaseBuy(Ui ui, Profile p, float x, float y, float w, float h) {
        ItemDef d = selected;
        if (d == null) { ui.textC("Select an item", x + w / 2, y + h / 2, 14, Ui.TXT_DIM); return; }
        ItemInstance it = probe(d);
        float ty = plinth(ui, it, d, x, y, w);
        float by = y + h - ui.s(150);
        stats(ui, it, d, x, ty, w, by - ui.s(20));
        boolean locked = d.weapon != null && !p.prog.platformUnlocked(d.id);
        int unit = Economy.shopBuy(d), max = d.stackable() ? d.maxStack : 1;
        qty = Math.max(1, Math.min(max, qty));
        long total = (long) unit * qty;
        ui.b.rect(x + ui.s(12), by - ui.s(8), w - ui.s(24), 1, 0xFF2E2A22);
        if (locked) {
            ui.textC("Stocked at Rank " + d.rank + "  (you are Rank " + p.prog.rank() + ")", x + w / 2, by, 13, Ui.AMBER);
            if (ui.button("shoptoken", x + ui.s(16), by + ui.s(28), w - ui.s(32), ui.s(44), "UNLOCK NOW  (1 TOKEN, " + p.prog.unlockTokens + " HELD)", "token", p.prog.unlockTokens > 0)) {
                String e = p.prog.spendUnlockToken(d.id);
                c.inv.say(e == null ? d.name + " unlocked for this Prestige cycle" : e, e == null ? Ui.GREEN : Ui.RED);
                if (e == null) { c.sfx("ui_buy", 0.7f); SaveSystem.save(p); }
            }
            ui.wrap("Unlock Tokens are earned one per Rank. What they open resets when you Prestige.", x + ui.s(16), by + ui.s(82), w - ui.s(32), 11, Ui.TXT_DIM);
            return;
        }
        if (max > 1) {
            ui.text("QUANTITY", x + ui.s(16), by + ui.s(4), 12, Ui.TXT_DIM);
            float qx = x + w - ui.s(16) - ui.s(196);
            if (ui.button("shopq-", qx, by - ui.s(2), ui.s(34), ui.s(30), "-", null, qty > 1)) qty = Math.max(1, qty - (ui.shift ? 10 : 1));
            ui.textC(String.valueOf(qty), qx + ui.s(66), by + ui.s(4), 16, Ui.TXT_HI);
            if (ui.button("shopq+", qx + ui.s(98), by - ui.s(2), ui.s(34), ui.s(30), "+", null, qty < max)) qty = Math.min(max, qty + (ui.shift ? 10 : 1));
            if (ui.button("shopqmax", qx + ui.s(138), by - ui.s(2), ui.s(58), ui.s(30), "MAX", null, true)) qty = (int) Math.max(1, Math.min(max, p.scrip / Math.max(1, unit)));
        }
        ui.text("TOTAL", x + ui.s(16), by + ui.s(42), 12, Ui.TXT_DIM);
        ui.textR(Version.coins(total), x + w - ui.s(16), by + ui.s(34), 24, p.scrip >= total ? Ui.AMBER : Ui.RED);
        if (ui.button("shopbuy", x + ui.s(16), by + ui.s(72), w - ui.s(32), ui.s(50), p.scrip >= total ? "PURCHASE" : "NOT ENOUGH CASH", "coin", p.scrip >= total)) buy(p, d, unit, qty);
    }

    private void showcaseSell(Ui ui, Profile p, float x, float y, float w, float h) {
        ItemInstance it = sellSel;
        if (it == null || it.container == null && it.slot == null) { sellSel = null; ui.textC("Choose something of yours to sell", x + w / 2, y + h / 2, 14, Ui.TXT_DIM); return; }
        ItemDef d = it.def();
        float ty = plinth(ui, it, d, x, y, w);
        float by = y + h - ui.s(120);
        stats(ui, it, d, x, ty, w, by - ui.s(20));
        int price = Economy.shopSell(it);
        ui.b.rect(x + ui.s(12), by - ui.s(8), w - ui.s(24), 1, 0xFF2E2A22);
        ui.text("OFFER" + (d.stackable() && it.qty > 1 ? "  (x" + it.qty + ")" : ""), x + ui.s(16), by + ui.s(10), 12, Ui.TXT_DIM);
        ui.textR("+" + Version.coins(price), x + w - ui.s(16), by, 24, Ui.GREEN);
        boolean nested = !it.attachments.isEmpty();
        if (ui.button("shopsell", x + ui.s(16), by + ui.s(42), w - ui.s(32), ui.s(50), nested ? "SELL (ATTACHMENTS ARE STRIPPED)" : "SELL", "coin", true)) {
            for (String s : new ArrayList<>(it.attachments.keySet())) Gunsmith.remove(it, s);
            InventoryService.release(it);
            p.scrip += price;
            c.inv.say("Sold " + it.name() + " for " + Version.coins(price), Ui.GREEN);
            c.sfx("ui_buy", 0.6f);
            sellSel = null;
            SaveSystem.save(p);
        }
    }

    /** Scripted verification: the same purchase the PURCHASE button makes. */
    public void debugBuy(Profile p, String itemId) { ItemDef d = Content.def(itemId); if (d != null) buy(p, d, Economy.shopBuy(d), 1); }

    private void buy(Profile p, ItemDef d, int unit, int n) {
        long total = (long) unit * n;
        if (p.scrip < total) { c.inv.say("Not enough cash", Ui.RED); c.sfx("ui_error", 0.5f); return; }
        ItemInstance it = new ItemInstance(d.id);
        it.qty = n;
        it.origin = "shop";
        // bought to be used: a firearm comes with one full load (0.6 handed over an empty one)
        if (d.weapon != null && !d.weapon.melee()) { ItemDef am = Content.ammoFor(d.weapon.caliber); if (am != null) { it.rounds = it.capacity(); it.ammoId = am.id; } }
        java.util.List<ItemInstance> before = new java.util.ArrayList<>(p.stash.items);
        if (!InventoryService.add(it, p.stash).ok()) { c.inv.say("Your stash is full", Ui.RED); c.sfx("ui_error", 0.5f); return; }
        // what arrived (the new instance, or the stack it was added to) is shown to the player when they next look at the stash
        ItemInstance landed = it.container == p.stash ? it : null;
        if (landed == null) for (ItemInstance o : p.stash.items) if (o.defId.equals(d.id) && before.contains(o)) landed = o;
        if (landed != null) c.inv.arrive(landed);
        p.scrip -= total;
        c.inv.say("Purchased " + (n > 1 ? n + "x " : "") + d.name + " for " + Version.coins(total) + " - delivered to your stash", Ui.GREEN);
        c.sfx("ui_buy", 0.7f);
        SaveSystem.save(p);
    }
}
