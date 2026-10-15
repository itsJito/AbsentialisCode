package absentialis.scene;

import absentialis.Version;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.gameplay.progression.Progression;
import absentialis.gameplay.weapons.Attachments;
import absentialis.gameplay.weapons.Gunsmith;
import absentialis.gameplay.weapons.WeaponService;
import absentialis.persistence.Profile;
import absentialis.persistence.SaveSystem;
import absentialis.ui.Ui;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Gun Workbench (v0.3): the one place weapons are inspected, configured and saved. Pick a
 * firearm, pick a category, hover a part to see exactly what it would do - every statistic it
 * touches is listed with a green (+) for an improvement and a red (-) for a penalty, and the six
 * primary bars show the resulting values before anything is installed. Five attachments at most;
 * five saved builds per weapon. Weapon level, Weapon Prestige and Gold camouflage live here too.
 */
public final class WorkbenchPanel {
    private final Ctx c;
    private ItemInstance weapon;
    private String category;
    private float listScroll, partScroll;
    private boolean confirmPrestige;

    public WorkbenchPanel(Ctx c) { this.c = c; }

    private static final int GOOD = 0xFF6CCB5F, BAD = 0xFFE0503C;

    /** A copy of the weapon with one part fitted (or one category emptied), for previewing statistics. */
    private static ItemInstance preview(ItemInstance w, ItemDef part, String removeCat) {
        ItemInstance t = new ItemInstance(-90, w.defId);
        t.durability = w.durability; t.maxDurability = w.maxDurability;
        t.attachments.putAll(w.attachments);
        if (removeCat != null) t.attachments.remove(removeCat);
        if (part != null) t.attachments.put(part.attachment.socket, new ItemInstance(-91, part.id));
        return t;
    }

    public void draw(Ui ui, Profile p, float top) {
        Progression pr = p.prog;
        float h = ui.h - top - ui.s(24), x = ui.s(24);
        // ---- left: owned firearms
        float lw = ui.s(290);
        float cy = ui.window("Armoury", "bench", x, top, lw, h);
        List<ItemInstance> guns = new ArrayList<>();
        for (ItemInstance i : p.allItems()) if (i.def().weapon != null && !i.def().weapon.melee() && i.parent == null) guns.add(i);
        if (weapon != null && !guns.contains(weapon)) weapon = null;
        if (weapon == null && !guns.isEmpty()) { weapon = guns.get(0); category = null; }
        float lh = ui.s(62), listH = h - (cy - top) - ui.s(66);
        if (ui.hover(x, cy, lw, listH)) listScroll -= (float) c.in.scroll * ui.s(50);
        listScroll = Math.max(0, Math.min(Math.max(0, guns.size() * lh - listH), listScroll));
        ui.b.scissor((int) x, (int) cy, (int) lw, (int) listH);
        for (int i = 0; i < guns.size(); i++) {
            ItemInstance g = guns.get(i);
            float gy = cy + i * lh - listScroll;
            if (gy + lh < cy || gy > cy + listH) continue;
            boolean sel = g == weapon, hov = ui.hover(x + ui.s(10), gy, lw - ui.s(20), lh - ui.s(6));
            if (hov && ui.click && !ui.consumed) { ui.consumed = true; weapon = g; category = null; partScroll = 0; confirmPrestige = false; c.ui("ui_click"); }
            ui.nine(sel ? "slot_hi" : "slot", x + ui.s(10), gy, lw - ui.s(20), lh - ui.s(6), 0xFFFFFFFF);
            ui.b.rect(x + ui.s(10), gy, ui.s(3), lh - ui.s(6), g.def().tierColor());
            c.icons.draw(ui.b, g, x + ui.s(18), gy + ui.s(4), ui.s(104), lh - ui.s(14), 0xFFFFFFFF);
            ui.text(g.def().name, x + ui.s(128), gy + ui.s(5), 12, Ui.TXT_HI);
            int lv = pr.weaponLevel(g.defId), mx = Attachments.maxLevel(g.def());
            ui.text("Lv " + lv + "/" + mx + (g.slot != null ? "  equipped" : ""), x + ui.s(128), gy + ui.s(22), 10, Ui.TXT_DIM);
            ui.bar(x + ui.s(128), gy + ui.s(38), lw - ui.s(160), ui.s(6), pr.weaponLevelProgress(g.defId), 0xFFE2A034);
            for (int s = 0; s < pr.weaponPrestige(g.defId); s++) ui.glyph("star", x + lw - ui.s(30) - s * ui.s(14), gy + ui.s(4), 1, 0xFFFFD040);
        }
        ui.b.noScissor();

        float x2 = x + lw + ui.s(14), w2 = ui.w - x2 - ui.s(24);
        float cy2 = ui.window("Gun Workbench", "bench", x2, top, w2, h);
        if (weapon == null) { ui.text("You own no firearms. The SHOP sells them; the Zone gives them away to whoever survives.", x2 + ui.s(18), cy2, 14, Ui.TXT_DIM); return; }
        ItemInstance g = weapon;
        ItemDef gd = g.def();
        List<String> cats = Attachments.categoriesFor(gd);
        if (category == null || !cats.contains(category)) category = cats.isEmpty() ? null : cats.get(0);
        boolean modifiable = pr.platformUnlocked(gd.id);

        // ---- right: attachment browser
        float rw = Math.min(ui.s(470), w2 * 0.40f), rx = x2 + w2 - rw - ui.s(12);
        ItemDef hoverPart = null;
        String hoverRemove = null;
        float ty = cy2;
        int perRow = 5;
        float tw = (rw - (perRow - 1) * ui.s(3)) / perRow;
        for (int i = 0; i < cats.size(); i++) {
            String cat = cats.get(i);
            float tx = rx + (i % perRow) * (tw + ui.s(3)), tyy = ty + (i / perRow) * ui.s(30);
            boolean fitted = g.attachments.containsKey(cat);
            if (ui.tab("wbcat" + cat, tx, tyy, tw, ui.s(27), Attachments.categoryName(cat).replace("Trigger Action", "Trigger").replace("Underbarrel", "Under").toUpperCase(), null, cat.equals(category))) { category = cat; partScroll = 0; }
            if (fitted) ui.b.rect(tx + ui.s(3), tyy + ui.s(3), ui.s(5), ui.s(5), GOOD);
        }
        ty += ((cats.size() + perRow - 1) / perRow) * ui.s(30) + ui.s(6);
        int rk = category == null ? 1 : Attachments.categoryRank(category);
        ui.text(category == null ? "" : Attachments.categoryName(category).toUpperCase() + (pr.rank() < rk ? "   -   opens at Rank " + rk : ""), rx, ty, 12, pr.rank() < rk ? Ui.AMBER : Ui.TXT_DIM);
        ty += ui.s(20);
        float partH = top + h - ty - ui.s(14);
        List<ItemDef> parts = new ArrayList<>();
        if (category != null) for (ItemDef d : Attachments.compatible(gd)) if (d.attachment.socket.equals(category)) parts.add(d);
        float rowH = ui.s(92);
        if (ui.hover(rx, ty, rw, partH)) partScroll -= (float) c.in.scroll * ui.s(50);
        partScroll = Math.max(0, Math.min(Math.max(0, parts.size() * rowH - partH), partScroll));
        ui.b.scissor((int) rx - 2, (int) ty, (int) rw + 4, (int) partH);
        for (int i = 0; i < parts.size(); i++) {
            ItemDef d = parts.get(i);
            float py = ty + i * rowH - partScroll;
            if (py + rowH < ty || py > ty + partH) continue;
            boolean on = g.attachments.containsKey(category) && g.attachments.get(category).defId.equals(d.id);
            String lock = pr.lockReason(gd, d);
            boolean hov = ui.hover(rx, Math.max(py, ty), rw, rowH - ui.s(6)) && ui.my < ty + partH;
            float k = ui.ease("wbpart" + d.id, hov);
            ui.b.rect(rx, py, rw, rowH - ui.s(6), on ? 0xFF24301E : Ui.mix(0xFF17191B, 0xFF22242A, k));
            ui.b.outline(rx, py, rw, rowH - ui.s(6), on ? 2 : 1, on ? GOOD : lock != null ? 0xFF2A2A2A : Ui.mix(0xFF34363A, 0xFFE2A034, k));
            ui.text(d.name, rx + ui.s(10), py + ui.s(6), 14, lock != null ? Ui.TXT_DIM : Ui.TXT_HI);
            if (pr.permanent.contains(d.id)) ui.glyph("star", rx + ui.s(12) + ui.tw(d.name, 14), py + ui.s(6), 1, 0xFFFFD040);
            if (on) ui.textR("FITTED", rx + rw - ui.s(10), py + ui.s(7), 11, GOOD);
            else if (lock != null) { ui.glyph("lock", rx + rw - ui.s(22), py + ui.s(5), 1, 0xFFFFFFFF); ui.textR(lock, rx + rw - ui.s(28), py + ui.s(7), 11, Ui.AMBER); }
            // every modifier: green (+) improvements first, red (-) penalties after
            float mx = rx + ui.s(10), my = py + ui.s(28);
            List<Map.Entry<String, Float>> mods = new ArrayList<>(d.attachment.mods.entrySet());
            mods.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
            for (Map.Entry<String, Float> e : mods) {
                String t = (e.getValue() > 0 ? "(+) " : "(-) ") + Attachments.statName(e.getKey()) + " " + Math.round(Math.abs(e.getValue())) + "%";
                float wdt = ui.tw(t, 11) + ui.s(12);
                if (mx + wdt > rx + rw - ui.s(6)) { mx = rx + ui.s(10); my += ui.s(16); }
                ui.text(t, mx, my, 11, e.getValue() > 0 ? GOOD : BAD);
                mx += wdt;
            }
            String extra = d.attachment.zoom > 1 ? String.format("%.2fx magnification", d.attachment.zoom) : d.attachment.launcher ? "Fires your grenades (throw key)" : d.attachment.expose == 1 ? "Visible to enemies while drawn" : d.attachment.expose == 2 ? "Visible to enemies while aiming" : d.attachment.mods.isEmpty() ? "" : "";
            if (!extra.isEmpty()) { if (mx > rx + ui.s(10)) { mx = rx + ui.s(10); my += ui.s(16); } ui.text(extra, mx, my, 11, d.attachment.expose > 0 ? BAD : Ui.BLUE); }
            if (hov) {
                hoverPart = on ? null : d;
                if (on) hoverRemove = category;
                ui.tip(d.name, "^d" + d.desc, lock != null ? "^a" + lock + (pr.permTokens > 0 ? "  -  right-click: spend a Prestige Unlock Token" : "") : on ? "^aClick to remove" : "^gClick to fit");
                if (ui.click && !ui.consumed) {
                    ui.consumed = true;
                    if (!modifiable) say("The " + gd.name + " opens at Rank " + gd.rank + " - or spend an Unlock Token on it in the Arsenal", Ui.RED);
                    else if (on) { Gunsmith.remove(g, category); spill(p, g); c.sfx("ui_drop", 0.5f); c.icons.invalidate(g); }
                    else if (lock != null) { say("Locked: " + lock, Ui.RED); c.sfx("ui_invalid", 0.5f); }
                    else { String e = Gunsmith.install(g, d.id); say(e == null ? "Fitted " + d.name : e, e == null ? Ui.GREEN : Ui.RED); c.sfx(e == null ? "mag_in" : "ui_invalid", 0.5f); spill(p, g); c.icons.invalidate(g); }
                }
                if (ui.rclick && lock != null && pr.permTokens > 0) {
                    String e = pr.spendPermToken(d.id);
                    say(e == null ? d.name + " is now permanently unlocked" : e, e == null ? Ui.AMBER : Ui.RED);
                    if (e == null) { c.sfx("ui_buy", 0.7f); SaveSystem.save(p); }
                }
            }
        }
        ui.b.noScissor();

        // ---- centre: the weapon, its five slots, the statistics
        float cx = x2 + ui.s(16), cw = rx - cx - ui.s(18);
        float vs = Math.min(h * 0.30f, cw * 0.42f);
        ui.b.rect(cx, cy2, cw, vs, 0xFF0B0C0D);
        ui.b.outline(cx, cy2, cw, vs, 1, 0xFF2A2C30);
        ItemInstance shown = hoverPart != null ? preview(g, hoverPart, null) : hoverRemove != null ? preview(g, null, hoverRemove) : g;
        shown.camo = g.camo;
        // the weapon fills the bench: drawn larger than its frame and clipped to it
        float big = Math.min(cw * 0.86f, vs * 2.1f);
        ui.b.scissor((int) cx + 1, (int) cy2 + 1, (int) cw - 2, (int) vs - 2);
        c.icons.drawInspect(ui.b, shown, cx + (cw - big) / 2, cy2 + (vs - big) / 2 + ui.s(8), big, c.time * 0.5f, 0.28f);
        ui.b.noScissor();
        ui.text(gd.name.toUpperCase(), cx + ui.s(12), cy2 + ui.s(8), 18, Ui.TXT_HI);
        ui.text(gd.tierName() + "  -  " + (Content.ammoFor(gd.weapon.caliber) == null ? "" : Content.ammoFor(gd.weapon.caliber).name) + "  -  " + gd.w + "x" + gd.h, cx + ui.s(12), cy2 + ui.s(32), 11, gd.tierColor());
        if (hoverPart != null) ui.textR("PREVIEW: " + hoverPart.name, cx + cw - ui.s(12), cy2 + ui.s(10), 12, Ui.AMBER);
        if (!modifiable) ui.textR("ATTACHMENTS OPEN AT RANK " + gd.rank + " (OR WITH AN UNLOCK TOKEN)", cx + cw - ui.s(12), cy2 + vs - ui.s(22), 12, Ui.RED);
        // equipped slots
        float sy = cy2 + vs + ui.s(10), sw = (cw - 4 * ui.s(6)) / 5;
        List<ItemInstance> fitted = new ArrayList<>(g.attachments.values());
        for (int i = 0; i < Attachments.MAX_EQUIPPED; i++) {
            float sx = cx + i * (sw + ui.s(6));
            ItemInstance a = i < fitted.size() ? fitted.get(i) : null;
            boolean hov = a != null && ui.hover(sx, sy, sw, ui.s(40));
            ui.nine(a != null ? "slot_hi" : "slot", sx, sy, sw, ui.s(40), 0xFFFFFFFF);
            if (a == null) ui.textC("SLOT " + (i + 1), sx + sw / 2, sy + ui.s(13), 11, 0xFF5A5C56);
            else {
                ui.text(Attachments.categoryName(a.def().attachment.socket).toUpperCase(), sx + ui.s(6), sy + ui.s(4), 9, Ui.AMBER);
                String n = a.def().name;
                while (ui.tw(n, 11) > sw - ui.s(10) && n.length() > 4) n = n.substring(0, n.length() - 1);
                ui.text(n, sx + ui.s(6), sy + ui.s(20), 11, Ui.TXT_HI);
                if (hov) { hoverRemove = a.def().attachment.socket; ui.tip(a.def().name, "^aClick to remove"); if (ui.click && !ui.consumed && modifiable) { ui.consumed = true; Gunsmith.remove(g, a.def().attachment.socket); spill(p, g); c.sfx("ui_drop", 0.5f); c.icons.invalidate(g); } }
            }
        }
        ui.textR(g.attachments.size() + " / " + Attachments.MAX_EQUIPPED + " attachments", cx + cw, sy + ui.s(44), 11, g.attachments.size() >= Attachments.MAX_EQUIPPED ? Ui.AMBER : Ui.TXT_DIM);
        // primary statistics with the previewed result
        float py = sy + ui.s(62), half = (cw - ui.s(20)) / 2;
        float[] now = Gunsmith.primary(g), then = Gunsmith.primary(shown);
        ui.text("PRIMARY", cx, py - ui.s(16), 11, Ui.TXT_DIM);
        for (int k = 0; k < now.length; k++) {
            float ry = py + k * ui.s(22);
            ui.text(Gunsmith.PRIMARY[k], cx, ry, 12, Ui.TXT);
            float bx = cx + ui.s(112), bw = half - ui.s(150);
            ui.b.rect(bx, ry + ui.s(3), bw, ui.s(10), 0xFF141615);
            float a = Math.min(now[k], then[k]), b = Math.max(now[k], then[k]);
            ui.b.rect(bx + 1, ry + ui.s(3) + 1, (bw - 2) * a, ui.s(10) - 2, 0xFFB0A070);
            if (b - a > 0.002f) ui.b.rect(bx + 1 + (bw - 2) * a, ry + ui.s(3) + 1, (bw - 2) * (b - a), ui.s(10) - 2, then[k] > now[k] ? GOOD : BAD);
            int diff = Math.round((then[k] - now[k]) * 100);
            ui.textR(diff == 0 ? String.valueOf(Math.round(now[k] * 100)) : (diff > 0 ? "(+) " : "(-) ") + Math.abs(diff), bx + bw + ui.s(34), ry, 12, diff == 0 ? Ui.TXT_DIM : diff > 0 ? GOOD : BAD);
        }
        // secondary statistics
        float qx = cx + half + ui.s(20);
        List<String[]> s0 = Gunsmith.secondary(g), s1 = Gunsmith.secondary(shown);
        ui.text("SECONDARY", qx, py - ui.s(16), 11, Ui.TXT_DIM);
        for (int k = 0; k < s0.size(); k++) {
            float ry = py + k * ui.s(16);
            boolean changed = !s0.get(k)[1].equals(s1.get(k)[1]);
            ui.text(s0.get(k)[0], qx, ry, 11, Ui.TXT);
            ui.textR(changed ? s0.get(k)[1] + "  >  " + s1.get(k)[1] : s0.get(k)[1], qx + half, ry, 11, changed ? Ui.AMBER : Ui.TXT_DIM);
        }

        // ---- progression strip, builds, service
        float by = py + Math.max(now.length * ui.s(22), s0.size() * ui.s(16)) + ui.s(12);
        int lv = pr.weaponLevel(gd.id), mxl = Attachments.maxLevel(gd), wp = pr.weaponPrestige(gd.id);
        ui.text("WEAPON LEVEL " + lv + " / " + mxl + (wp > 0 ? "    PRESTIGE " + wp : ""), cx, by, 13, Ui.TXT_HI);
        ui.bar(cx + ui.s(230), by + ui.s(3), cw - ui.s(230), ui.s(10), pr.weaponLevelProgress(gd.id), 0xFFE2A034);
        ItemDef next = null;
        for (ItemDef d : Attachments.compatible(gd)) if (Attachments.unlockLevel(gd, d) == lv + 1) next = d;
        Object[] bonus = Gunsmith.prestigeBonus(gd);
        ui.text(next != null ? "Next unlock at level " + (lv + 1) + ": " + next.name : "Every attachment unlocked" + (pr.canPrestigeWeapon(gd.id) ? " - this weapon can be prestiged" : ""), cx, by + ui.s(18), 11, Ui.TXT_DIM);
        by += ui.s(40);
        float bw3 = (cw - ui.s(16)) / 3;
        if (ui.button("wbprestige", cx, by, bw3, ui.s(34), confirmPrestige ? "CONFIRM PRESTIGE" : "WEAPON PRESTIGE", "star", pr.canPrestigeWeapon(gd.id))) {
            if (!confirmPrestige) { confirmPrestige = true; say("Resets this weapon's attachment unlocks. Grants +" + Math.round((Float) bonus[1]) + "% " + Attachments.statName((String) bonus[0]) + " and death protection. Click again to confirm.", Ui.AMBER); }
            else { String e = pr.prestigeWeapon(gd.id); confirmPrestige = false; if (e == null) { for (ItemInstance o : guns) if (o.defId.equals(gd.id)) for (String sck : new ArrayList<>(o.attachments.keySet())) if (!pr.permanent.contains(o.attachments.get(sck).defId)) Gunsmith.remove(o, sck); c.sfx("ui_buy", 0.8f); SaveSystem.save(p); c.icons.invalidate(g); } say(e == null ? gd.name + " prestiged: it will come home if you do not" : e, e == null ? Ui.AMBER : Ui.RED); }
        }
        boolean owns = pr.ownsGoldCamo(gd.id), avail = pr.goldCamoAvailable(gd.id);
        String gl = owns ? ("gold".equals(g.camo) ? "GOLD CAMO: ON" : "GOLD CAMO: OFF") : "GOLD CAMO  " + Version.coins(Progression.GOLD_CAMO_PRICE);
        if (ui.button("wbgold", cx + bw3 + ui.s(8), by, bw3, ui.s(34), gl, "star", owns || avail && p.scrip >= Progression.GOLD_CAMO_PRICE)) {
            if (owns) g.camo = "gold".equals(g.camo) ? null : "gold";
            else { p.scrip -= Progression.GOLD_CAMO_PRICE; pr.cosmetics.add("gold:" + gd.id); g.camo = "gold"; say("Gold camouflage unlocked for the " + gd.name + " - permanently", Ui.AMBER); c.sfx("ui_buy", 0.8f); }
            c.icons.invalidate(g);
            SaveSystem.save(p);
        }
        if (!owns && !avail && ui.hover(cx + bw3 + ui.s(8), by, bw3, ui.s(34))) ui.tip("Gold camouflage", "^dNeeds Player Prestige " + Progression.GOLD_CAMO_PRESTIGE + " and a prestiged weapon.", "^dOnce bought it is never removed.");
        ItemInstance oil = owned(p, "mat.gun_oil"), kit = owned(p, "kit.repair");
        float bw4 = (bw3 - ui.s(6)) / 2;
        if (ui.button("wbclean", cx + (bw3 + ui.s(8)) * 2, by, bw4, ui.s(34), "CLEAN", null, oil != null)) { String r = WeaponService.clean(g, oil, true); say(r == null ? "Cleaned: condition " + Math.round(g.condition()) : r, r == null ? Ui.GREEN : Ui.RED); c.icons.invalidate(g); }
        if (ui.button("wbrepair", cx + (bw3 + ui.s(8)) * 2 + bw4 + ui.s(6), by, bw4, ui.s(34), "REPAIR", null, kit != null)) { String r = WeaponService.repair(g, kit); say(r == null ? "Repaired to " + Math.round(g.condition()) : r, r == null ? Ui.GREEN : Ui.RED); c.icons.invalidate(g); }
        by += ui.s(46);
        // saved builds: five per weapon
        ui.text("SAVED BUILDS  (click to load, shift+click to save the current build)", cx, by, 11, Ui.TXT_DIM);
        by += ui.s(18);
        List<List<String>> builds = p.builds(gd.id);
        float bbw = (cw - 4 * ui.s(6)) / 5;
        for (int i = 0; i < Attachments.MAX_BUILDS; i++) {
            List<String> b = builds.get(i);
            float bx = cx + i * (bbw + ui.s(6));
            if (by + ui.s(40) > top + h - ui.s(6)) break;
            if (ui.button("wbbuild" + i, bx, by, bbw, ui.s(40), b == null ? "BUILD " + (i + 1) + "  -" : "BUILD " + (i + 1) + "  (" + b.size() + ")", null, true)) {
                if (ui.shift || b == null) { builds.set(i, new ArrayList<>(Gunsmith.build(g))); say("Build " + (i + 1) + " saved (" + g.attachments.size() + " attachments)", Ui.GREEN); SaveSystem.save(p); }
                else if (!modifiable) say("The " + gd.name + " opens at Rank " + gd.rank + " - or spend an Unlock Token on it in the Arsenal", Ui.RED);
                else { int skipped = loadBuild(pr, g, b); spill(p, g); c.icons.invalidate(g); say("Build " + (i + 1) + " fitted" + (skipped > 0 ? " - " + skipped + " part(s) not unlocked" : ""), skipped > 0 ? Ui.AMBER : Ui.GREEN); c.sfx("mag_in", 0.5f); }
            }
            if (b != null && ui.hover(bx, by, bbw, ui.s(40))) { List<String> lines = new ArrayList<>(); lines.add("Build " + (i + 1)); for (String id : b) lines.add("^d" + (Content.def(id) == null ? id : Content.def(id).name)); if (b.isEmpty()) lines.add("^dNo attachments"); ui.tip(lines.toArray(new String[0])); }
        }
    }

    /** Strips the weapon and fits a saved build; parts that are not currently unlocked are skipped. Returns how many. */
    public static int loadBuild(Progression pr, ItemInstance g, List<String> build) {
        for (String s : new ArrayList<>(g.attachments.keySet())) Gunsmith.remove(g, s);
        int skipped = 0;
        for (String id : build) {
            ItemDef d = Content.def(id);
            if (d == null || d.attachment == null || !pr.attachmentUnlocked(g.def(), d) || Gunsmith.install(g, id) != null) skipped++;
        }
        return skipped;
    }

    /** Rounds a smaller magazine could not hold go back to the stash as loose ammunition. */
    private void spill(Profile p, ItemInstance g) {
        if (g.spill <= 0) return;
        ItemDef am = Content.ammoFor(g.def().weapon.caliber);
        int left = g.spill;
        g.spill = 0;
        while (am != null && left > 0) { ItemInstance st = new ItemInstance(am.id); st.qty = Math.min(am.maxStack, left); left -= st.qty; st.origin = "unloaded"; if (!InventoryService.add(st, p.stash).ok()) break; }
    }

    private void say(String s, int col) { c.inv.say(s, col); }

    private static ItemInstance owned(Profile p, String id) {
        for (ItemInstance i : p.allItems()) if (i.defId.equals(id) && i.parent == null) return i;
        return null;
    }
}
