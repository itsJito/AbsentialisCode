package absentialis.scene;

import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.progression.Progression;
import absentialis.gameplay.squad.Roster;
import absentialis.persistence.Profile;
import absentialis.persistence.SaveSystem;
import absentialis.ui.Ui;
import java.util.List;

/**
 * SQUAD tab: the persistent roster, who deploys (eight at most), which of them are Red Team and which Blue (four
 * each at most, any split), what each carries, and what each looks like. Squadmate Level and Prestige are shown
 * but never bought: they are earned on operations.
 *
 * 0.7: nobody at Rank 1 - the tab does not exist until Rank 4, when the first of nine roster slots opens (one every
 * four Ranks to 36). Squadmates are hired (the SHOP, Recruits) and arrive with the standard kit of their type; the
 * weapon list that used to be here is gone, and in its place is what the squadmate actually has: what is worn and
 * what is in the pack.
 */
public final class SquadPanel {
    private final Ctx c;
    private CharacterCreator creator;
    private long selected;
    private float weaponScroll;
    /** Name editing: the squadmate being renamed (0 = none) and the text typed so far. */
    private long editing;
    private final StringBuilder nameBuf = new StringBuilder();
    public static final int NAME_MAX = 20;

    /** True while a name field owns the keyboard (the hideout must not treat the keys as shortcuts). */
    public boolean typing() { return editing != 0; }

    private void commitName(Profile p, Roster.Mate m) {
        String n = Roster.cleanName(nameBuf.toString());
        if (!n.isEmpty() && !n.equals(m.name)) { m.name = n; SaveSystem.save(p); c.sfx("ui_click", 0.4f); }
        editing = 0;
    }

    /** The editable callsign: click to type, [Enter] keeps it, [Esc] discards. Any printable text, up to NAME_MAX characters. */
    private void nameField(Ui ui, Profile p, Roster.Mate m, float x, float y, float w) {
        float fh = ui.s(34);
        boolean on = editing == m.id, hov = ui.hover(x, y, w, fh);
        if (editing != 0 && !on && p.squad.byId(editing) == null) editing = 0;
        ui.nine(on ? "slot_hi" : "slot", x, y, w, fh, 0xFFFFFFFF);
        if (on) {
            while (!c.in.typed.isEmpty()) { char ch = c.in.typed.poll(); if (nameBuf.length() < NAME_MAX && ch >= 32 && ch != 127) nameBuf.append(ch); }
            if (c.in.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) && nameBuf.length() > 0) nameBuf.setLength(nameBuf.length() - 1);
            if (c.in.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) || c.in.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) commitName(p, m);
            else if (c.in.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) editing = 0;
            else if (ui.click && !hov && !ui.consumed) commitName(p, m);
        } else if (hov && ui.click && !ui.consumed) {
            ui.consumed = true;
            editing = m.id;
            nameBuf.setLength(0);
            nameBuf.append(m.name);
            c.in.typed.clear();
            c.sfx("ui_click", 0.4f);
        }
        String shown = on ? nameBuf + ((System.currentTimeMillis() / 450) % 2 == 0 ? "_" : "") : m.name;
        ui.text(shown, x + ui.s(12), y + ui.s(8), 16, on ? Ui.AMBER : Ui.TXT_HI);
        ui.textR(on ? nameBuf.length() + " / " + NAME_MAX + "   [Enter] keep   [Esc] cancel" : hov ? "click to rename" : "", x + w - ui.s(10), y + ui.s(11), 11, Ui.TXT_DIM);
    }
    public static final int RED_COL = 0xFFD8483A, BLUE_COL = 0xFF4A90E2, GOLD_COL = 0xFFE2B030;

    public SquadPanel(Ctx c) { this.c = c; }

    /** Whether this survivor has a squad to speak of yet: Rank 4, or squadmates carried over from before 0.7. */
    public static boolean unlocked(Profile p) { return p.prog.bestRank >= Progression.SQUAD_FIRST_RANK || p.prog.prestige > 0 || !p.squad.mates.isEmpty(); }

    private final java.util.Map<String, absentialis.gameplay.items.ItemInstance> probes = new java.util.HashMap<>();
    private absentialis.gameplay.items.ItemInstance probe(String id) { return probes.computeIfAbsent(id, k -> new absentialis.gameplay.items.ItemInstance(-4000 - probes.size(), id)); }

    /** One worn item: a labelled slot with the thing in it (or the word for what is missing). */
    private void kitSlot(Ui ui, String label, String itemId, float x, float y, float w, float h) {
        ItemDef d = itemId == null || itemId.isEmpty() ? null : Content.def(itemId);
        ui.nine("slot", x, y, w, h, 0xFFFFFFFF);
        ui.text(label, x + ui.s(6), y + ui.s(4), 9, Ui.TXT_DIM);
        if (d == null) { ui.textC("-", x + w / 2, y + h / 2 - ui.s(6), 12, Ui.TXT_DIM); return; }
        ui.b.rect(x + 1, y + h - ui.s(3), w - 2, ui.s(3), d.tierColor());
        c.icons.draw(ui.b, probe(d.id), x + ui.s(6), y + ui.s(16), w - ui.s(12), h - ui.s(36), 0xFFFFFFFF);
        absentialis.ui.Layout.fit(ui, d.name, x + ui.s(6), y + h - ui.s(18), w - ui.s(12), 11, 8, Ui.TXT);
        if (ui.hover(x, y, w, h)) ui.tip(d.name + "  -  " + d.tierName(), d.desc);
    }

    /**
     * What the squadmate has, as it is: the four things worn and the pack laid out in cells. It is a view - the kit
     * is the standard issue of the type, and it is traded with in the field ([F], facing them), not edited here.
     */
    private void kitView(Ui ui, Roster.Mate m, float x, float y, float w, float h) {
        ui.text("EQUIPMENT", x, y, 12, Ui.TXT_HI);
        ui.textR(Roster.typeName(m.type).toUpperCase(), x + w, y, 12, Ui.AMBER);
        y += ui.s(20);
        float gap = ui.s(8), sw = (w - gap) / 2, sh = Math.min(ui.s(96), (h * 0.46f - gap) / 2);
        kitSlot(ui, "PRIMARY", m.weapon, x, y, sw, sh);
        kitSlot(ui, "SIDEARM", m.sidearm, x + sw + gap, y, sw, sh);
        kitSlot(ui, "ARMOR", m.armor, x, y + sh + gap, sw, sh);
        kitSlot(ui, "HEAD", m.head, x + sw + gap, y + sh + gap, sw, sh);
        y += (sh + gap) * 2 + ui.s(6);
        if (!m.build.isEmpty()) {
            StringBuilder b = new StringBuilder("Fitted: ");
            for (int i = 0; i < m.build.size(); i++) { ItemDef a = Content.def(m.build.get(i)); b.append(i > 0 ? ", " : "").append(a == null ? m.build.get(i) : a.name); }
            y += ui.wrap(b.toString(), x, y, w, 11, Ui.TXT_DIM) + ui.s(6);
        }
        ui.text("PACK", x, y, 12, Ui.TXT_HI);
        y += ui.s(20);
        int cols = 4;
        float cell = Math.min((w - gap * (cols - 1)) / cols, ui.s(64));
        for (int i = 0; i < Math.max(cols * 2, m.pack.size()); i++) {
            float cx = x + (i % cols) * (cell + gap), cy = y + (i / cols) * (cell + gap);
            if (cy + cell > y + h) break;
            ui.nine("slot", cx, cy, cell, cell, i < m.pack.size() ? 0xFFFFFFFF : 0x70FFFFFF);
            if (i >= m.pack.size()) continue;
            String e = m.pack.get(i);
            int cpos = e.lastIndexOf(':');
            ItemDef d = Content.def(cpos > 0 ? e.substring(0, cpos) : e);
            if (d == null) continue;
            c.icons.draw(ui.b, probe(d.id), cx + ui.s(5), cy + ui.s(5), cell - ui.s(10), cell - ui.s(10), 0xFFFFFFFF);
            String q = cpos > 0 ? e.substring(cpos + 1) : "1";
            if (!"1".equals(q)) ui.textR("x" + q, cx + cell - ui.s(4), cy + cell - ui.s(15), 11, Ui.TXT_HI);
            if (ui.hover(cx, cy, cell, cell)) ui.tip(d.name + ("1".equals(q) ? "" : "  x" + q), d.desc);
        }
    }

    public void draw(Ui ui, Profile p, float top) {
        Roster r = p.squad;
        float x = ui.s(24), h = ui.h - top - ui.s(24), lw = ui.s(440);
        float cy = ui.window("Element  -  " + r.activeCount() + " / " + Roster.MAX_ACTIVE + " deployed, plus you", "radio", x, top, lw, h);
        ui.b.rect(x + ui.s(14), cy, ui.s(10), ui.s(10), GOLD_COL);
        ui.text("GOLD " + r.activeCount(), x + ui.s(30), cy - ui.s(2), 12, GOLD_COL);
        ui.b.rect(x + ui.s(120), cy, ui.s(10), ui.s(10), RED_COL);
        ui.text("RED " + r.teamSize(Roster.RED) + "/" + Roster.TEAM_CAP, x + ui.s(136), cy - ui.s(2), 12, RED_COL);
        ui.b.rect(x + ui.s(220), cy, ui.s(10), ui.s(10), BLUE_COL);
        ui.text("BLUE " + r.teamSize(Roster.BLUE) + "/" + Roster.TEAM_CAP, x + ui.s(236), cy - ui.s(2), 12, BLUE_COL);
        cy += ui.s(22);
        float rowH = Math.min(ui.s(66), (top + h - cy - ui.s(10)) / Roster.MAX_ROSTER);
        if (r.byId(selected) == null && !r.mates.isEmpty()) selected = r.mates.get(0).id;
        for (int i = 0; i < Roster.MAX_ROSTER; i++) {
            float ry = cy + i * rowH;
            if (i >= r.mates.size() && i < r.mates.size() + r.vacancies(p.prog.squadSlots())) {
                // an open place: 0.7 fills none of them for you - someone is hired into it, or it stays empty
                ui.nine("slot", x + ui.s(12), ry, lw - ui.s(24), rowH - ui.s(6), 0xC0FFFFFF);
                ui.b.rect(x + ui.s(12), ry, ui.s(5), rowH - ui.s(6), 0xFF5A2A26);
                ui.text("Vacant place", x + ui.s(26), ry + ui.s(5), 13, Ui.TXT);
                ui.text("Nobody in it. Hire someone (SHOP, Recruits).", x + ui.s(26), ry + ui.s(23), 11, Ui.TXT_DIM);
                if (ui.button("sqrec" + i, x + lw - ui.s(134), ry + ui.s(8), ui.s(112), rowH - ui.s(22), "RECRUIT", "star", true)) recruitOpen = true;
                continue;
            }
            if (i >= r.mates.size()) {
                int need = Progression.SQUAD_RANKS[Math.min(Progression.SQUAD_RANKS.length - 1, Math.max(0, i - Math.max(0, r.mates.size() - p.prog.squadSlots())))];
                ui.nine("slot", x + ui.s(12), ry, lw - ui.s(24), rowH - ui.s(6), 0x90FFFFFF);
                ui.glyph("lock", x + ui.s(24), ry + rowH / 2 - ui.s(10), Math.max(1, ui.s(1)), 0xFFFFFFFF);
                ui.text("Roster slot " + (i + 1) + "  -  unlocks at Rank " + need, x + ui.s(52), ry + rowH / 2 - ui.s(11), 12, Ui.TXT_DIM);
                continue;
            }
            Roster.Mate m = r.mates.get(i);
            boolean sel = m.id == selected, hov = ui.hover(x + ui.s(12), ry, lw - ui.s(24), rowH - ui.s(6));
            int tc = Roster.RED.equals(m.team) ? RED_COL : BLUE_COL;
            ui.nine(sel ? "slot_hi" : "slot", x + ui.s(12), ry, lw - ui.s(24), rowH - ui.s(6), m.active ? 0xFFFFFFFF : 0xA0FFFFFF);
            ui.b.rect(x + ui.s(12), ry, ui.s(5), rowH - ui.s(6), m.active ? tc : 0xFF3A3A3A);
            ui.text(m.name, x + ui.s(26), ry + ui.s(5), 13, m.active ? Ui.TXT_HI : Ui.TXT_DIM);
            ItemDef wd = Content.def(m.weapon);
            ui.text(m.title() + "   -   " + Roster.typeName(m.type) + " (" + (wd == null ? "?" : wd.name) + ")", x + ui.s(26), ry + ui.s(23), 11, Ui.TXT_DIM);
            ui.bar(x + ui.s(26), ry + rowH - ui.s(18), ui.s(170), ui.s(6), m.progress(), 0xFFE2A034);
            float bx = x + lw - ui.s(214);
            if (ui.button("sqteam" + m.id, bx, ry + ui.s(8), ui.s(74), rowH - ui.s(22), Roster.RED.equals(m.team) ? "RED" : "BLUE", null, true)) {
                String e = r.assign(m, Roster.RED.equals(m.team) ? Roster.BLUE : Roster.RED);
                if (e != null) c.inv.say(e, Ui.RED); else SaveSystem.save(p);
            }
            if (ui.button("sqact" + m.id, bx + ui.s(80), ry + ui.s(8), ui.s(112), rowH - ui.s(22), m.active ? "DEPLOYED" : "RESERVE", m.active ? "check" : null, true)) {
                String e = r.setActive(m, !m.active);
                if (e != null) c.inv.say(e, Ui.RED); else SaveSystem.save(p);
            }
            if (hov && ui.click && !ui.consumed && editing == 0) { ui.consumed = true; selected = m.id; }
        }

        Roster.Mate m = r.byId(selected);
        float x2 = x + lw + ui.s(14), w2 = ui.w - x2 - ui.s(24);
        if (ui.button("sqboard", x + lw - ui.s(150), top + ui.s(6), ui.s(136), ui.s(26), recruitOpen ? "ROSTER" : "RECRUITING", null, true)) recruitOpen = !recruitOpen;
        if (recruitOpen || m == null) { recruiting(ui, p, x2, top, w2, h); return; }
        float cy2 = ui.window(m.name, "star", x2, top, w2, h);
        // identity: the name is the player's to choose, and is what the squad answers to on the radio and the HUD
        ui.text("NAME", x2 + ui.s(18), cy2 + ui.s(9), 12, Ui.TXT_HI);
        float nfw = Math.min(ui.s(420), w2 - ui.s(300));
        nameField(ui, p, m, x2 + ui.s(70), cy2, nfw);
        if (ui.button("sqrand" + m.id, x2 + ui.s(80) + nfw, cy2, ui.s(150), ui.s(34), "RANDOM NAME", null, editing == 0)) { m.name = r.randomName(m.id * 31 + System.nanoTime()); SaveSystem.save(p); }
        cy2 += ui.s(46);
        // 0.6: the squadmate's profile card - the same card, laid out the same way, as the player's own
        cardSpin += ui.dt * 0.25f;
        float cardW = Math.min(ui.s(470), w2 * 0.46f);
        ProfileCard.draw(ui, c, ProfileCard.of(c, p, m), x2 + w2 - cardW - ui.s(18), top + ui.s(8), cardW, cardSpin);
        ui.text(m.title().toUpperCase(), x2 + ui.s(18), cy2, 18, Ui.TXT_HI);
        ui.text(String.format("Level %d / %d    Prestige %d / %d    %,d XP", m.level(), Roster.MAX_LEVEL, m.prestige(), Roster.MAX_PRESTIGE, m.xp), x2 + ui.s(18), cy2 + ui.s(26), 12, Ui.TXT_DIM);
        ui.bar(x2 + ui.s(18), cy2 + ui.s(46), w2 - cardW - ui.s(60), ui.s(10), m.progress(), 0xFFE2A034);
        ui.text(m.xp >= Roster.XP_PER_CLIMB ? "Level 50 is kept: further experience builds Prestige." : "If this squadmate dies in the Zone they are gone for good.", x2 + ui.s(18), cy2 + ui.s(60), 11, Ui.TXT_DIM);
        cy2 += ui.s(86);
        // 0.7: what they have - worn and carried - in place of the weapon list
        float ww = ui.s(300);
        kitView(ui, m, x2 + ui.s(18), cy2, ww - ui.s(18), top + h - cy2 - ui.s(14));
        // appearance
        if (creator == null) creator = new CharacterCreator(c);
        float ax = x2 + ww + ui.s(24);
        ui.text("APPEARANCE", ax, cy2, 12, Ui.TXT_HI);
        float aTop = Math.max(cy2 + ui.s(20), top + ui.s(8) + ProfileCard.height(cardW) + ui.s(32));
        if (creator.draw(ui, m.look, null, ax, aTop, w2 - (ax - x2) - ui.s(18), top + h - aTop - ui.s(20))) SaveSystem.save(p);
    }

    private static int nextSlotRank(Profile p) { for (int r : Progression.SQUAD_RANKS) if (p.prog.bestRank < r) return r; return Progression.SQUAD_RANKS[Progression.SQUAD_RANKS.length - 1]; }

    private boolean recruitOpen;
    public void showRecruiting() { recruitOpen = true; }
    private float cardSpin;

    /** The recruiting board: who is looking for work today and what they ask, and the names of those who did not come back. */
    void recruiting(Ui ui, Profile p, float x, float top, float w, float h) {
        Roster r = p.squad;
        int slots = p.prog.squadSlots(), vac = r.vacancies(slots);
        float cy = ui.window("Recruiting  -  " + vac + (vac == 1 ? " place" : " places") + " to fill", "star", x, top, w, h);
        ui.wrap("Everyone on your squad is hired here and arrives with the full standard kit of their trade. A squadmate killed in the Zone is gone for good - rank, record and kit. The experienced ask more; the list changes each day. Slots open at Ranks 4, 8, 12 ... 36.", x + ui.s(18), cy, w - ui.s(36), 12, Ui.TXT_DIM);
        cy += ui.s(44);
        java.util.List<Roster.Offer> offers = r.offers(p.seed, p.zone.day, p.prog.rank());
        float cw = (w - ui.s(36) - ui.s(16) * 2) / 3;
        if (offers.isEmpty()) ui.text("Nobody else is asking today. Come back after your next operation.", x + ui.s(18), cy + ui.s(10), 13, Ui.TXT_DIM);
        for (int i = 0; i < offers.size(); i++) {
            Roster.Offer o = offers.get(i);
            float ox = x + ui.s(18) + i * (cw + ui.s(16));
            ProfileCard.Data d = ProfileCard.of(c, p, o.mate());
            d.sub = Roster.typeName(o.mate().type).toUpperCase() + "  -  FOR HIRE";
            ProfileCard.draw(ui, c, d, ox, cy, cw, cardSpin);
            float by = cy + ProfileCard.height(cw) + ui.s(8);
            boolean can = vac > 0 && p.scrip >= o.price();
            if (ui.button("hire" + o.key(), ox, by, cw, ui.s(38), "HIRE  -  " + absentialis.Version.coins(o.price()), "check", can)) {
                String err = r.hire(o, slots, p.scrip);
                if (err == null) { p.scrip -= o.price(); selected = o.mate().id; recruitOpen = false; SaveSystem.save(p); c.inv.say(o.mate().name + " joins the element", Ui.GREEN); c.sfx("ui_confirm", 0.5f); }
                else c.inv.say(err, Ui.RED);
            }
            ItemDef kw = Content.def(o.mate().weapon), ka = Content.def(o.mate().armor);
            absentialis.ui.Layout.fit(ui, "Kit: " + (kw == null ? "-" : kw.name) + (ka == null ? "" : ", " + ka.name) + ", " + o.mate().pack.size() + " pack items", ox, by + ui.s(42), cw, 11, 8, Ui.TXT_DIM);
            if (!can) ui.textC(vac <= 0 ? (p.prog.squadSlots() >= Roster.MAX_ROSTER ? "The roster is full" : "No vacant place - the next opens at Rank " + nextSlotRank(p)) : "Not enough cash", ox + cw / 2, by + ui.s(58), 11, Ui.RED);
        }
        cy += ProfileCard.height(cw) + ui.s(92);
        cardSpin += ui.dt * 0.25f;
        ui.text("KILLED IN ACTION  (" + r.fallen.size() + ")", x + ui.s(18), cy, 13, Ui.TXT_HI);
        cy += ui.s(24);
        if (r.fallen.isEmpty()) ui.text("Nobody yet.", x + ui.s(18), cy, 12, Ui.TXT_DIM);
        for (Roster.Fallen f : r.fallen) {
            if (cy > top + h - ui.s(30)) break;
            ui.b.rect(x + ui.s(18), cy, ui.s(4), ui.s(18), 0xFF5A2A26);
            absentialis.ui.Layout.fit(ui, f.name() + "   " + f.title() + "   " + f.kills() + " kills in " + f.raids() + " operations   -   day " + f.day() + (f.cause().isEmpty() ? "" : ", " + f.cause()), x + ui.s(30), cy + ui.s(1), w - ui.s(60), 12, 9, Ui.TXT);
            cy += ui.s(22);
        }
    }
}
