package absentialis.scene;

import absentialis.Version;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.progression.Challenges;
import absentialis.gameplay.progression.Progression;
import absentialis.gameplay.weapons.Attachments;
import absentialis.persistence.Profile;
import absentialis.persistence.SaveSystem;
import absentialis.sim.ZoneState;
import absentialis.ui.Ui;
import java.util.List;
import java.util.Map;

/**
 * RANK tab (v0.3): the 50-rank ladder from Private to Commander, the fifteen Prestiges, both kinds
 * of unlock token, the arsenal's unlock state, and the Prestige flow - confirmation, the Prestige
 * Award choice (Extra Custom Class / Unlock Tokens Refund / Permanent Unlock Token / Fresh Start)
 * and the explicit, spelled-out confirmation Fresh Start demands.
 */
public final class RankPanel {
    private final Ctx c;
    private boolean prestigeOpen, freshOpen;
    private float arsenalScroll;
    /** Set when the player confirmed a Fresh Start: the hideout swaps in the wiped profile. */
    public boolean freshStartRequested;

    public RankPanel(Ctx c) { this.c = c; }

    public boolean modal(Profile p) { return prestigeOpen || freshOpen || p.prog.awardPending; }

    public void draw(Ui ui, Profile p, float top) {
        Progression pr = p.prog;
        float x = ui.s(24), w = ui.s(500), h = ui.h - top - ui.s(24);
        float cy = ui.window("Operator  -  Rank & Prestige", "chevron", x, top, w, h);
        ui.text(pr.title().toUpperCase(), x + ui.s(18), cy, 20, Ui.TXT_HI);
        for (int i = 0; i < Progression.MAX_PRESTIGE; i++) ui.glyph("star", x + ui.s(18) + i * ui.s(30), cy + ui.s(30), Math.max(2, ui.s(2)), i < pr.prestige ? (pr.master() ? 0xFFFFD040 : 0xFFFFFFFF) : 0x30FFFFFF);
        cy += ui.s(66);
        ui.text(pr.master() ? "Rank 50 - permanent" : "Next rank", x + ui.s(18), cy, 13, Ui.TXT);
        ui.bar(x + ui.s(150), cy + ui.s(3), w - ui.s(180), ui.s(12), pr.rankProgress(), 0xFFE2A034);
        cy += ui.s(22);
        ui.text("This climb", x + ui.s(18), cy, 13, Ui.TXT);
        ui.bar(x + ui.s(150), cy + ui.s(3), w - ui.s(180), ui.s(12), pr.cycleXp / (float) Progression.CYCLE_XP, 0xFF8A6420);
        ui.text(String.format("%,d / %,d XP    lifetime %,d / %,d", pr.cycleXp, Progression.CYCLE_XP, pr.lifetimeXp, Progression.totalToMaster()), x + ui.s(18), cy + ui.s(20), 11, Ui.TXT_DIM);
        cy += ui.s(46);
        String pl = pr.master() ? "PRESTIGE MASTER" : pr.awardPending ? "CHOOSE YOUR PRESTIGE AWARD" : pr.cycleComplete() ? "ENTER PRESTIGE " + (pr.prestige + 1) : "PRESTIGE  (complete Rank 50)";
        if (ui.button("prestige", x + ui.s(18), cy, w - ui.s(36), ui.s(40), pl, "star", pr.canPrestige())) prestigeOpen = true;
        cy += ui.s(52);
        int[] dims = pr.caseDims();
        String[][] rows = {{"Unlock Tokens (this climb)", String.valueOf(pr.unlockTokens)}, {"Prestige Unlock Tokens", String.valueOf(pr.permTokens)},
                {"Permanent unlocks", String.valueOf(pr.permanent.size())}, {"Inventory", dims[1] + "x" + dims[0] + "  (" + dims[0] * dims[1] + " slots)"},
                {"Custom classes", String.valueOf(pr.presets())}, {"Squadmate roster", p.squad.mates.size() + " / " + absentialis.gameplay.squad.Roster.MAX_ROSTER}};
        for (String[] r : rows) { ui.text(r[0], x + ui.s(18), cy, 13, Ui.TXT); ui.textR(r[1], x + w - ui.s(18), cy, 13, Ui.AMBER); cy += ui.s(19); }
        if (pr.permTokens > 0) { ui.wrap("You hold a Prestige Unlock Token: right-click a locked weapon in the Arsenal, or a locked attachment at the Workbench, to unlock it from Rank 1 forever.", x + ui.s(18), cy + ui.s(2), w - ui.s(36), 11, Ui.AMBER); cy += ui.s(34); }
        cy += ui.s(8);
        ui.text("NEXT UNLOCKS", x + ui.s(18), cy, 13, Ui.TXT_HI);
        cy += ui.s(20);
        for (String[] e : pr.nextUnlocks(5)) { ui.text("Rank " + e[0], x + ui.s(18), cy, 12, Ui.AMBER); ui.text(e[1], x + ui.s(92), cy, 12, Ui.TXT); cy += ui.s(17); }
        cy += ui.s(10);
        ui.text("THE LADDER", x + ui.s(18), cy, 13, Ui.TXT_HI);
        cy += ui.s(20);
        List<String[]> ladder = Progression.rankTable();
        int half = (ladder.size() + 1) / 2;
        float colW = (w - ui.s(36)) / 2;
        for (int i = 0; i < ladder.size(); i++) {
            String[] e = ladder.get(i);
            float lx = x + ui.s(18) + (i / half) * colW, ly = cy + (i % half) * ui.s(16);
            if (ly > top + h - ui.s(20)) continue;
            int lo = Integer.parseInt(e[1]), hi = Integer.parseInt(e[2]);
            boolean cur = pr.rank() >= lo && pr.rank() <= hi, done = pr.rank() > hi;
            ui.text(e[0], lx, ly, 11, cur ? Ui.AMBER : done ? Ui.TXT : Ui.TXT_DIM);
            ui.textR(e[1] + "-" + e[2], lx + colW - ui.s(14), ly, 11, cur ? Ui.AMBER : Ui.TXT_DIM);
        }

        // ---- arsenal
        float x2 = x + w + ui.s(14), w2 = ui.s(470);
        float cy2 = ui.window("Arsenal", "bullet", x2, top, w2, h);
        ui.text("Weapons open by Rank; the shop only shows what is open. Click a locked one to spend an Unlock Token (" + pr.unlockTokens + ").", x2 + ui.s(16), cy2, 11, Ui.TXT_DIM);
        cy2 += ui.s(20);
        List<String> ws = Progression.platforms();
        float rowH = ui.s(34), listH = top + h - cy2 - ui.s(12);
        if (ui.hover(x2, cy2, w2, listH)) arsenalScroll -= (float) c.in.scroll * ui.s(40);
        arsenalScroll = Math.max(0, Math.min(Math.max(0, ws.size() * rowH - listH), arsenalScroll));
        ui.b.scissor((int) x2, (int) cy2, (int) w2, (int) listH);
        for (int i = 0; i < ws.size(); i++) {
            ItemDef d = Content.def(ws.get(i));
            float ry = cy2 + i * rowH - arsenalScroll;
            if (ry + rowH < cy2 || ry > cy2 + listH) continue;
            boolean un = pr.platformUnlocked(d.id), perm = pr.permanent.contains(d.id), tok = pr.tokenSpent.contains(d.id);
            boolean hov = ui.hover(x2 + ui.s(10), Math.max(ry, cy2), w2 - ui.s(20), rowH - ui.s(4)) && ui.my < cy2 + listH;
            ui.b.rect(x2 + ui.s(10), ry, w2 - ui.s(20), rowH - ui.s(4), hov ? 0xFF24262A : 0xFF17191B);
            ui.b.rect(x2 + ui.s(10), ry, ui.s(3), rowH - ui.s(4), d.tierColor());
            ui.glyph(perm ? "star" : un ? "check" : "lock", x2 + ui.s(20), ry + ui.s(7), Math.max(1, ui.s(1)), perm ? 0xFFFFD040 : 0xFFFFFFFF);
            ui.text(d.name, x2 + ui.s(44), ry + ui.s(7), 13, un ? Ui.TXT_HI : Ui.TXT_DIM);
            String st = perm ? "PERMANENT" : tok ? "TOKEN" : un ? (d.weapon.melee() ? "" : "Lv " + pr.weaponLevel(d.id) + "/" + Attachments.maxLevel(d)) : "RANK " + d.rank;
            ui.textR(st, x2 + w2 - ui.s(22), ry + ui.s(8), 11, perm ? 0xFFFFD040 : un ? Ui.TXT_DIM : Ui.AMBER);
            for (int s = 0; s < pr.weaponPrestige(d.id); s++) ui.glyph("star", x2 + w2 - ui.s(120) - s * ui.s(14), ry + ui.s(8), 1, 0xFFFFD040);
            if (hov && !un && ui.click && !ui.consumed) { ui.consumed = true; String e = pr.spendUnlockToken(d.id); c.inv.say(e == null ? d.name + " unlocked until your next Prestige" : e, e == null ? Ui.GREEN : Ui.RED); if (e == null) { c.sfx("ui_buy", 0.6f); SaveSystem.save(p); } }
            if (hov && ui.rclick && pr.permTokens > 0 && pr.eligiblePermanent(d.id)) { String e = pr.spendPermToken(d.id); c.inv.say(e == null ? d.name + " is permanently unlocked" : e, e == null ? Ui.AMBER : Ui.RED); if (e == null) { c.sfx("ui_buy", 0.8f); SaveSystem.save(p); } }
        }
        ui.b.noScissor();

        // ---- service record
        float x3 = x2 + w2 + ui.s(14), w3 = ui.w - x3 - ui.s(24);
        if (w3 >= ui.s(240)) service(ui, p, x3, top, w3, h);
    }

    private void service(Ui ui, Profile p, float x3, float top, float w3, float h) {
        float cy3 = ui.window("Service Record", "radio", x3, top, w3, h);
        for (String f : ZoneState.FACTIONS) {
            float r = p.rep(f);
            ui.text(ZoneState.cap(f), x3 + ui.s(16), cy3, 13, Ui.TXT);
            float bw = w3 - ui.s(170), bx = x3 + ui.s(124);
            ui.b.rect(bx, cy3 + ui.s(4), bw, ui.s(10), 0xFF141615);
            float mid = bx + bw / 2;
            ui.b.rect(Math.min(mid, mid + bw / 2 * r / 100f), cy3 + ui.s(5), Math.abs(bw / 2 * r / 100f), ui.s(8), r >= 0 ? 0xFF7DBA5C : 0xFFC0402E);
            ui.b.rect(mid, cy3 + ui.s(2), 1, ui.s(14), 0xFF8E897A);
            ui.textR(String.format("%+.0f", r), x3 + w3 - ui.s(12), cy3, 12, Ui.TXT_DIM);
            cy3 += ui.s(22);
        }
        cy3 += ui.s(8);
        for (String k : new String[]{"raids", "extracts", "deaths"}) { ui.text(ZoneState.cap(k), x3 + ui.s(16), cy3, 12, Ui.TXT); ui.textR(String.valueOf(p.stat(k)), x3 + w3 - ui.s(16), cy3, 12, Ui.AMBER); cy3 += ui.s(17); }
        for (Map.Entry<String, Integer> e : p.stats.entrySet()) if (e.getKey().startsWith("kills_") && cy3 < top + h * 0.45f) { ui.text("Kills: " + e.getKey().substring(6), x3 + ui.s(16), cy3, 12, Ui.TXT_DIM); ui.textR(String.valueOf(e.getValue()), x3 + w3 - ui.s(16), cy3, 12, Ui.TXT); cy3 += ui.s(16); }
        cy3 += ui.s(10);
        ui.text("CHALLENGES  (reset each Prestige)", x3 + ui.s(16), cy3, 13, Ui.TXT_HI);
        cy3 += ui.s(22);
        for (Challenges.Challenge ch : Challenges.ALL) {
            if (cy3 > top + h - ui.s(80)) break;
            boolean done = Challenges.claimed(p, ch);
            int prog = Challenges.progress(p, ch);
            ui.text(ch.label(), x3 + ui.s(16), cy3, 12, done ? Ui.TXT_DIM : Ui.TXT);
            if (Challenges.claimable(p, ch)) {
                if (ui.button("ch" + ch.id(), x3 + w3 - ui.s(126), cy3 - ui.s(4), ui.s(110), ui.s(22), "CLAIM +" + ch.xp() / 1000 + "k", null, true)) {
                    int xp = Challenges.claim(p, ch);
                    c.inv.say("Challenge complete: +" + xp + " XP", Ui.GREEN);
                    c.sfx("ui_buy", 0.6f);
                    SaveSystem.save(p);
                }
            } else if (done) ui.glyph("check", x3 + w3 - ui.s(32), cy3 - ui.s(2), Math.max(1, ui.s(1)), 0xFFFFFFFF);
            else ui.textR(prog + "/" + ch.goal(), x3 + w3 - ui.s(16), cy3, 11, Ui.TXT_DIM);
            cy3 += ui.s(22);
        }
    }

    // ------------------------------------------------------------------ modals
    public void drawModals(Ui ui, Profile p) {
        if (freshOpen) freshModal(ui, p);
        else if (p.prog.awardPending) awardModal(ui, p);
        else if (prestigeOpen) prestigeModal(ui, p);
    }

    private void prestigeModal(Ui ui, Profile p) {
        Progression pr = p.prog;
        ui.b.rect(0, 0, ui.w, ui.h, 0xC0000000);
        float w = ui.s(620), h = ui.s(420), x = (ui.w - w) / 2, y = (ui.h - h) / 2;
        float cy = ui.window(pr.prestige + 1 >= Progression.MAX_PRESTIGE ? "Prestige Master" : "Enter Prestige " + (pr.prestige + 1), "star", x, y, w, h);
        String[] lines = {"^rResets:", "  - Rank, back to Private 1" + (pr.prestige + 1 >= Progression.MAX_PRESTIGE ? " ... except this time: Prestige Master keeps Rank 50" : ""),
                "  - Rank-gated weapon and attachment access, and unspent Unlock Tokens", "  - Challenge progress",
                "^gKept:", "  - Everything you own: stash, case, cash, and your inventory size", "  - Permanent (gold star) unlocks, cosmetics and camouflage",
                "  - Squadmates and their progression; weapon levels and Weapon Prestige",
                "^aYou receive: 1 Prestige Unlock Token + 1 Prestige Award of your choice"};
        for (String l : lines) {
            int col = l.startsWith("^r") ? Ui.RED : l.startsWith("^g") ? Ui.GREEN : l.startsWith("^a") ? Ui.AMBER : Ui.TXT;
            ui.text(l.replaceFirst("^\\^.", ""), x + ui.s(24), cy, 14, col);
            cy += ui.s(25);
        }
        if (ui.button("pconfirm", x + ui.s(24), y + h - ui.s(64), ui.s(260), ui.s(44), "PRESTIGE", "star", true)) {
            String err = pr.prestige();
            if (err == null) {
                for (Challenges.Challenge ch : Challenges.ALL) p.stats.remove("ch_" + ch.id());
                p.applyCapacity();
                c.sfx("ui_buy", 0.8f);
                SaveSystem.save(p);
            } else c.inv.say(err, Ui.RED);
            prestigeOpen = false;
        }
        if (ui.button("pcancel", x + w - ui.s(284), y + h - ui.s(64), ui.s(260), ui.s(44), "CANCEL", "cross", true) || c.in.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) prestigeOpen = false;
    }

    private void awardModal(Ui ui, Profile p) {
        Progression pr = p.prog;
        ui.b.rect(0, 0, ui.w, ui.h, 0xD0000000);
        float w = ui.s(700), h = ui.s(150) + Progression.AWARDS.length * ui.s(76), x = (ui.w - w) / 2, y = Math.max(ui.s(16), (ui.h - h) / 2);
        float cy = ui.window("Prestige " + pr.prestige + "  -  choose one Prestige Award", "star", x, y, w, h);
        ui.wrap("Your Prestige Unlock Token has been granted (spend it in the Arsenal or at the Workbench). Now pick exactly one award. This choice is made once per Prestige.", x + ui.s(20), cy, w - ui.s(40), 12, Ui.TXT_DIM);
        cy += ui.s(44);
        for (String a : Progression.AWARDS) {
            boolean ok = pr.awardAvailable(a);
            ui.nine("slot", x + ui.s(20), cy, w - ui.s(40), ui.s(68), 0xFFFFFFFF);
            ui.text(Progression.awardName(a).toUpperCase(), x + ui.s(34), cy + ui.s(8), 15, !ok ? Ui.TXT_DIM : a.equals("fresh") ? Ui.RED : Ui.TXT_HI);
            ui.wrap(ok ? pr.awardText(a) : "Unavailable.", x + ui.s(34), cy + ui.s(30), w - ui.s(250), 11, Ui.TXT_DIM);
            if (ui.button("award" + a, x + w - ui.s(196), cy + ui.s(14), ui.s(160), ui.s(40), a.equals("fresh") ? "REVIEW" : "CHOOSE", a.equals("fresh") ? "skull" : "check", ok)) {
                if (a.equals("fresh")) freshOpen = true;
                else { String e = pr.chooseAward(a); c.inv.say(e == null ? "Prestige Award: " + Progression.awardName(a) : e, e == null ? Ui.AMBER : Ui.RED); if (e == null) { c.sfx("ui_buy", 0.8f); p.applyCapacity(); SaveSystem.save(p); } }
            }
            cy += ui.s(76);
        }
    }

    private void freshModal(Ui ui, Profile p) {
        ui.b.rect(0, 0, ui.w, ui.h, 0xE8000000);
        float w = ui.s(640), h = ui.s(430), x = (ui.w - w) / 2, y = (ui.h - h) / 2;
        float cy = ui.window("Fresh Start  -  this cannot be undone", "skull", x, y, w, h);
        String[] lines = {"^rFresh Start erases this profile and returns it to a non-Prestige Private 1:", "  - Prestige " + p.prog.prestige + ", every Rank, all XP and all statistics",
                "  - Every permanent unlock, Unlock Token and Prestige Award", "  - Inventory size, stash, case, cash (" + Version.coins(p.scrip) + ") and every item",
                "  - All weapon levels, Weapon Prestige and camouflage", "  - Your squadmates, their levels and their Prestige",
                "  - Faction standing, contracts, hideout facilities and the map", "^gKept: your callsign and your operator's appearance. Nothing else.",
                "^aHold SHIFT and click ERASE to confirm."};
        for (String l : lines) {
            int col = l.startsWith("^r") ? Ui.RED : l.startsWith("^g") ? Ui.GREEN : l.startsWith("^a") ? Ui.AMBER : Ui.TXT;
            ui.text(l.replaceFirst("^\\^.", ""), x + ui.s(24), cy, 13, col);
            cy += ui.s(25);
        }
        if (ui.button("freshgo", x + ui.s(24), y + h - ui.s(64), ui.s(280), ui.s(44), ui.shift ? "ERASE THIS PROFILE" : "HOLD SHIFT TO ARM", "skull", ui.shift)) { freshOpen = false; freshStartRequested = true; }
        if (ui.button("freshno", x + w - ui.s(264), y + h - ui.s(64), ui.s(240), ui.s(44), "KEEP MY PROFILE", "cross", true) || c.in.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) freshOpen = false;
    }
}
