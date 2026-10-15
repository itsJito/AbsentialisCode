package absentialis.scene;

import absentialis.gameplay.Alliance;
import absentialis.persistence.Profile;
import absentialis.persistence.SaveSystem;
import absentialis.rivalry.Doctrine;
import absentialis.rivalry.RivalryEngine;
import absentialis.rivalry.RivalryState;
import absentialis.rivalry.RivalryWorld;
import absentialis.sim.Leaderboard;
import absentialis.ui.Layout;
import absentialis.ui.Ui;
import absentialis.world.District;
import java.util.ArrayList;
import java.util.List;

/**
 * The 0.6 hideout panels that are about the world rather than the kit: the alliance the survivor chooses once, the
 * LEADERBOARD and RIVALRIES tabs of the deployment screen, and the PROFILE tab with the survivor's card.
 * Each draws from the profile as it stands; nothing here keeps a copy of the state it shows.
 */
public final class OpsPanels {
    private final Ctx c;
    private float boardScroll, cardSpin;
    private String selectedGroup = "", mediateWith = "", intelAbout = "";
    private int boardFilter;
    private List<Leaderboard.Row> rows;
    private float rowsAge = 99;
    private final List<String> feedback = new ArrayList<>();

    public OpsPanels(Ctx c) { this.c = c; }

    private static int factionColour(String gameKey) {
        Alliance.Def d = Alliance.def(gameKey);
        return d != null ? d.colour() : "player".equals(gameKey) ? 0xFF4A90E2 : 0xFF8A8A8A;
    }

    // ================================================================== the question (asked once, after the profile is made)
    /**
     * 0.8: "What are you looking for?" Four answers, each a word under a sign - a dollar, a pair of scales, a lone
     * figure, a skull over crossed bones. It replaces the allegiance screen and its four columns of text: the answer
     * still leans the survivor toward one side (Profit to the Mercenaries, Stability to the Military, Peace to the
     * Loners, Chaos to the Bandits), which decides the district they start from and the work on the board first, and
     * leaves that side permanently a little warmer toward them. Returns true once the choice is made.
     */
    public boolean allianceModal(Ui ui, Profile p) {
        ui.b.rect(0, 0, ui.w, ui.h, 0xF0050606);
        float w = Math.min(ui.s(1040), ui.w - ui.s(40)), h = Math.min(ui.s(560), ui.h - ui.s(40)), x = (ui.w - w) / 2, y = (ui.h - h) / 2;
        ui.textC("WHAT ARE YOU LOOKING FOR?", ui.w / 2f, y + ui.s(8), 34, Ui.TXT_HI);
        ui.textC("One answer. It decides where you start and who is glad to see you - nothing else is closed to you.", ui.w / 2f, y + ui.s(58), 13, Ui.TXT_DIM);
        float top = y + ui.s(104), gap = ui.s(18), cw = (w - gap * 3) / 4, ch = h - ui.s(104);
        boolean chose = false;
        for (int i = 0; i < Alliance.MOTIVES.length; i++) {
            Alliance.Motive m = Alliance.MOTIVES[i];
            Alliance.Def d = Alliance.def(m.key());
            float cx = x + i * (cw + gap);
            boolean hov = ui.hover(cx, top, cw, ch) && !ui.block;
            float k = ui.ease("motive" + i, hov);
            ui.shadow(cx, top, cw, ch, 0x70);
            ui.b.rect(cx, top, cw, ch, Ui.mix(0xFF111315, 0xFF1C2024, k));
            ui.b.outline(cx, top, cw, ch, hov ? 2 : 1, Ui.mix(0xFF2E2A22, d.colour(), 0.35f + 0.65f * k));
            ui.b.rect(cx, top + ch - ui.s(5), cw, ui.s(5), Ui.mix(Ui.alpha(d.colour(), 0.6f), d.colour(), k));
            // the sign: big, centred, lifting a little under the cursor
            int px = Math.max(3, Math.round(Math.min(cw * 0.52f, ch * 0.34f) / 16f));
            float gs = 16 * px, gx = cx + (cw - gs) / 2, gy = top + ui.s(34) - k * ui.s(6);
            ui.glyph(m.glyph(), gx, gy, px, Ui.mix(0xFFCFC8B8, 0xFFFFFFFF, k));
            float ty = top + ui.s(34) + gs + ui.s(26);
            ui.textC(m.word(), cx + cw / 2, ty, 30, Ui.mix(Ui.TXT, Ui.TXT_HI, k));
            ui.textC(m.line(), cx + cw / 2, ty + ui.s(42), 14, d.colour() | 0xFF000000);
            Layout.wrapC(ui, m.detail(), cx + ui.s(16), ty + ui.s(70), cw - ui.s(32), 12, Ui.TXT_DIM);
            StringBuilder hq = new StringBuilder();
            for (District home : Alliance.homes(m.key())) hq.append(hq.length() > 0 ? " / " : "").append(home.display);
            ui.textC("Leans toward the " + d.name(), cx + cw / 2, top + ch - ui.s(62), 11, Ui.TXT);
            ui.textC("Starts in " + hq, cx + cw / 2, top + ch - ui.s(44), 11, Ui.TXT_DIM);
            if (hov && ui.click && !ui.consumed) {
                ui.consumed = true;
                Alliance.choose(p, m.key());
                SaveSystem.save(p);
                c.sfx("ui_deploy", 0.6f);
                chose = true;
            }
        }
        return chose;
    }

    // ================================================================== PROFILE tab
    public void profile(Ui ui, Profile p, float top) {
        cardSpin += ui.dt * 0.25f;
        float x = ui.s(24), h = ui.h - top - ui.s(24), w = ui.w - ui.s(48);
        float cy = ui.window("Profile  -  " + p.name, "star", x, top, w, h);
        float cw = Math.min(ui.s(760), w * 0.56f), cx = x + ui.s(20);
        ProfileCard.draw(ui, c, ProfileCard.of(c, p), cx, cy, cw, cardSpin);
        float by = cy + ProfileCard.height(cw) + ui.s(18);
        ui.text("CARD BACKGROUND", cx, by, 13, Ui.TXT_HI);
        ui.text("Opened by Rank - and two by Prestige. Yours: " + ProfileCard.background(p.cardBackground).name(), cx + ui.s(170), by + ui.s(2), 11, Ui.TXT_DIM);
        by += ui.s(24);
        int cols = 5;
        float gw = (cw - ui.s(8) * (cols - 1)) / cols, gh = ui.s(54);
        for (int i = 0; i < ProfileCard.BACKGROUNDS.length; i++) {
            ProfileCard.Background b = ProfileCard.BACKGROUNDS[i];
            float gx = cx + (i % cols) * (gw + ui.s(8)), gy = by + (i / cols) * (gh + ui.s(8));
            boolean open = ProfileCard.unlocked(b, p.prog), sel = b.id().equals(p.cardBackground), hov = ui.hover(gx, gy, gw, gh);
            for (int s = 0; s < 6; s++) ui.b.rect(gx, gy + gh * s / 6f, gw, gh / 6f + 1, Ui.alpha(Ui.mix(b.top(), b.bottom(), s / 5f), open ? 1 : 0.35f));
            ui.b.rect(gx, gy, gw, ui.s(3), Ui.alpha(b.accent(), open ? 1 : 0.3f));
            ui.b.outline(gx, gy, gw, gh, sel ? 2 : 1, sel ? Ui.AMBER : hov && open ? b.accent() : 0xFF2E2A22);
            Layout.fit(ui, b.name(), gx + ui.s(8), gy + ui.s(10), gw - ui.s(16), 12, 9, open ? Ui.TXT_HI : Ui.TXT_DIM);
            ui.text(open ? (sel ? "IN USE" : ProfileCard.requirement(b)) : "Unlocks at " + ProfileCard.requirement(b), gx + ui.s(8), gy + gh - ui.s(18), 10, open ? (sel ? Ui.AMBER : Ui.TXT_DIM) : Ui.RED);
            if (!open) ui.glyph("lock", gx + gw - ui.s(22), gy + ui.s(8), Math.max(1, ui.s(1)), 0xB0FFFFFF);
            if (open && hov && ui.click && !ui.consumed) { ui.consumed = true; p.cardBackground = b.id(); SaveSystem.save(p); c.sfx("ui_click", 0.5f); }
        }
        // ---- the record in full, beside the card
        float rx = cx + cw + ui.s(28), rw = x + w - rx - ui.s(20), ry = cy;
        Alliance.Def a = Alliance.def(p.alliance);
        ui.text("ALLIANCE", rx, ry, 12, Ui.AMBER);
        ry += ui.s(20);
        if (a != null) {
            ui.b.rect(rx, ry, ui.s(6), ui.s(40), a.colour());
            ui.text(a.name(), rx + ui.s(16), ry, 18, Ui.TXT_HI);
            ui.text(a.motto(), rx + ui.s(16), ry + ui.s(24), 11, Ui.TXT_DIM);
            ry += ui.s(50);
            StringBuilder dep = new StringBuilder();
            for (District d : District.CITY) if (Alliance.mayDeploy(p, d)) dep.append(dep.length() > 0 ? ", " : "").append(d.display);
            ry += ui.wrap("You can deploy from: " + (dep.length() == 0 ? "nowhere" : dep), rx, ry, rw, 12, Ui.TXT) + ui.s(14);
        } else { ui.text("Not chosen", rx, ry, 14, Ui.TXT_DIM); ry += ui.s(30); }
        ui.text("SERVICE RECORD", rx, ry, 12, Ui.AMBER);
        ry += ui.s(22);
        int human = 0;
        for (String f : absentialis.sim.ZoneState.FACTIONS) human += p.stat("kills_" + f);
        String[][] rec = {{"Rank", p.prog.title()}, {"Lifetime experience", String.format("%,d", p.prog.lifetimeXp)}, {"Coins", absentialis.Version.coins(p.scrip)}, {"Operations", String.valueOf(p.stat("raids"))},
                {"Extractions", String.valueOf(p.stat("extracts"))}, {"Killed in action", String.valueOf(p.stat("deaths"))}, {"Hostiles killed", String.valueOf(human)}, {"Infected killed", String.valueOf(p.stat("kills_infected"))},
                {"Map cells surveyed", String.valueOf(p.stat("cells"))}, {"Containers searched", String.valueOf(p.stat("containers"))}, {"Documents recovered", String.valueOf(p.stat("documents"))},
                {"Squad", p.squad.mates.size() + " on the roster, " + p.squad.fallen.size() + " lost"}, {"Maximum health", String.valueOf(absentialis.gameplay.CombatConfig.get().maxHealth(p.herbs))}};
        for (String[] r : rec) { if (ry > top + h - ui.s(26)) break; ui.text(r[0], rx, ry, 13, Ui.TXT); ui.textR(r[1], rx + rw, ry, 13, Ui.TXT_HI); ui.b.rect(rx, ry + ui.s(18), rw, 1, 0xFF22241F); ry += ui.s(23); }
        for (String f : absentialis.sim.ZoneState.FACTIONS) {
            if (ry > top + h - ui.s(26)) break;
            ui.text(Alliance.name(f) + " standing", rx, ry, 13, Ui.TXT);
            float v = p.rep(f);
            ui.textR(String.format("%+.0f", v), rx + rw, ry, 13, v < -10 ? Ui.RED : v > 20 ? Ui.GREEN : Ui.TXT_HI);
            ry += ui.s(21);
        }
    }

    // ================================================================== LEADERBOARD (deployment screen)
    private static final String[] FILTERS = {"EVERYONE", "MY ELEMENT", "LEADERS", "MILITARY", "BANDITS", "LONERS", "MERCENARIES"};

    /** How many places the table shows: the top of the ladder, and - always - the survivor, wherever they stand. */
    public static final int BOARD_PLACES = 20;

    /**
     * 1.2, redone. The top twenty of whoever is alive, and the survivor: among them if they are in the twenty, and
     * otherwise on a line of their own under the twentieth, with the place they actually hold. A row is a place, the
     * mark of the Rank, the name with what they belong to beside it, kills, and standing - there is no column for the
     * group any more, and no scrolling: it is one page.
     */
    public void leaderboard(Ui ui, Profile p, float x, float y, float w, float h) {
        rowsAge += ui.dt;
        if (rows == null || rowsAge > 2) { rows = Leaderboard.rows(p); rowsAge = 0; }
        int[] n = Leaderboard.counts(rows);
        ui.text("THE LADDER", x, y, 15, Ui.TXT_HI);
        ui.text("the top " + BOARD_PLACES + " of everyone alive in the Zone, and you", x + ui.tw("THE LADDER", 15) + ui.s(12), y + ui.s(3), 11, Ui.TXT_DIM);
        ui.textR(rows.size() + " alive: " + n[0] + " in your element, " + n[1] + " leaders, " + n[2] + " others", x + w, y + ui.s(3), 11, Ui.TXT_DIM);
        float fy = y + ui.s(24), fw = Math.min(ui.s(132), (w - ui.s(6) * (FILTERS.length - 1)) / FILTERS.length);
        for (int i = 0; i < FILTERS.length; i++) if (ui.tab("lbf" + i, x + i * (fw + ui.s(6)), fy, fw, ui.s(26), FILTERS[i], null, boardFilter == i) && boardFilter != i) { boardFilter = i; boardScroll = 0; }
        List<Leaderboard.Row> shown = new ArrayList<>();
        String[] fk = {null, null, null, "military", "raiders", "survivors", "mercenaries"};
        Leaderboard.Row me = null;
        int myPlace = 0;
        for (int i = 0; i < rows.size(); i++) {
            Leaderboard.Row r = rows.get(i);
            boolean mine = "you".equals(r.kind()) || "squad".equals(r.kind());
            if ("you".equals(r.kind())) { me = r; myPlace = i + 1; }
            if (boardFilter == 1 && !mine || boardFilter == 2 && !"leader".equals(r.kind()) || boardFilter >= 3 && (mine || !fk[boardFilter].equals(r.faction()))) continue;
            shown.add(r);
        }
        boolean meShown = false;
        int top = Math.min(BOARD_PLACES, shown.size());
        for (int i = 0; i < top; i++) if (shown.get(i) == me) meShown = true;
        boolean extra = me != null && !meShown;
        float hy = fy + ui.s(38), headH = ui.s(22), listY = hy + headH + ui.s(4);
        float rh = Math.max(ui.s(18), Math.min(ui.s(30), (y + h - listY - (extra ? ui.s(10) : 0)) / (BOARD_PLACES + (extra ? 1 : 0))));
        float cPlace = x + ui.s(10), cMark = x + ui.s(58), cName = cMark + rh + ui.s(4), cKills = x + w * 0.66f, cStand = x + w * 0.72f;
        ui.b.rect(x, hy, w, headH, 0xFF15171A);
        ui.b.rect(x, hy + headH - 1, w, 1, Ui.AMBER);
        ui.text("#", cPlace, hy + ui.s(5), 10, Ui.AMBER); ui.text("NAME", cName, hy + ui.s(5), 10, Ui.AMBER);
        ui.textR("KILLS", cKills + ui.s(34), hy + ui.s(5), 10, Ui.AMBER); ui.text("STANDING", cStand + ui.s(20), hy + ui.s(5), 10, Ui.AMBER);
        boardShown = 0; boardOwnPlace = myPlace; boardOwnExtra = extra;
        for (int i = 0; i < top + (extra ? 1 : 0); i++) {
            boolean last = i == top;
            Leaderboard.Row r = last ? me : shown.get(i);
            float ry = listY + i * rh + (last ? ui.s(10) : 0);
            if (last) {
                // a break in the ladder: everything between the twentieth and the survivor
                for (int d = 0; d < 3; d++) ui.b.rect(x + w / 2 - ui.s(10) + d * ui.s(8), listY + i * rh + ui.s(3), ui.s(3), ui.s(3), Ui.TXT_DIM);
            }
            boolean you = "you".equals(r.kind()), squad = "squad".equals(r.kind());
            int place = rows.indexOf(r) + 1, fc = factionColour(r.faction());
            int medal = place == 1 ? 0xFFE8C040 : place == 2 ? 0xFFC8CCD2 : place == 3 ? 0xFFC88A50 : 0;
            ui.b.rect(x, ry, w, rh - ui.s(2), you ? 0xFF2C2616 : i % 2 == 0 ? 0xFF131518 : 0xFF0F1113);
            if (you) ui.b.outline(x, ry, w, rh - ui.s(2), 1, Ui.alpha(Ui.AMBER, 0.8f));
            ui.b.rect(x, ry, ui.s(4), rh - ui.s(2), medal != 0 ? medal : you ? Ui.AMBER : squad ? 0xFF4A90E2 : fc);
            float mid = ry + (rh - ui.s(2)) / 2, ts = Math.min(13, rh / ui.s(1) * 0.5f);
            // the place: the first three wear it on a disc
            if (medal != 0) { absentialis.engine.render.Vector2D.ellipse(ui.b, cPlace + ui.s(10), mid, rh * 0.36f, rh * 0.36f, medal); ui.textC(String.valueOf(place), cPlace + ui.s(10), mid - ui.s(6), 12, 0xFF14120C); }
            else ui.textC(String.valueOf(place), cPlace + ui.s(10), mid - ui.s(6), 12, you ? Ui.AMBER : Ui.TXT_DIM);
            // the mark of the Rank, then the name, then what they belong to
            float ms = rh - ui.s(8);
            ui.b.rect(cMark, ry + ui.s(3), ms, ms, 0xFF1C1F23);
            absentialis.ui.Insignia.draw(ui, r.rank(), cMark + ui.s(1), ry + ui.s(4), ms - ui.s(2), you ? Ui.AMBER : medal != 0 ? medal : 0xFFC8C4B6);
            String name = r.name() + (you ? "  (you)" : ""), group = you || squad ? (p.alliance.isEmpty() ? "Yours" : Alliance.name(p.alliance)) : Alliance.name(r.faction());
            float nameMax = cKills - cName - ui.s(150);
            float used = Layout.fit(ui, name, cName, mid - ui.s(7), nameMax, 13, 9, you ? Ui.TXT_HI : Ui.TXT);
            float nw = Math.min(nameMax, ui.tw(name, used)), gx = cName + nw + ui.s(10), gw = ui.tw(group, 10) + ui.s(12);
            int chip = fc == 0xFF8A8A8A ? 0xFF3A3C40 : Ui.mix(fc, 0xFF000000, 0.35f);
            ui.b.rect(gx, mid - ui.s(8), gw, ui.s(16), chip);
            ui.b.rect(gx, mid - ui.s(8), ui.s(2), ui.s(16), fc);
            ui.text(group, gx + ui.s(7), mid - ui.s(5), 10, 0xFFF0ECE0);
            ui.text(r.title(), gx + gw + ui.s(10), mid - ui.s(5), 10, Ui.TXT_DIM);
            ui.textR(String.valueOf(r.kills()), cKills + ui.s(34), mid - ui.s(7), 13, r.kills() > 0 ? Ui.TXT_HI : Ui.TXT_DIM);
            Layout.fit(ui, r.detail(), cStand + ui.s(20), mid - ui.s(6), x + w - cStand - ui.s(28), 11, 8, Ui.TXT_DIM);
            boardShown++;
        }
        if (shown.isEmpty() && !extra) ui.textC("Nobody of that kind is alive.", x + w / 2, listY + ui.s(30), 13, Ui.TXT_DIM);
    }
    /** What the table last drew (gates and scripted runs): rows, the survivor's place, and whether they were on a line of their own under the rest. */
    public int boardShown, boardOwnPlace;
    public boolean boardOwnExtra;

    // ================================================================== RIVALRIES (deployment screen)
    private void say(RivalryEngine.Result r) { feedback.add(0, (r.ok() ? "" : "! ") + r.text()); while (feedback.size() > 4) feedback.remove(feedback.size() - 1); c.sfx(r.ok() ? "ui_click" : "ui_invalid", 0.5f); }

    private void meter(Ui ui, String label, float v, float x, float y, float w, int col, boolean signed) {
        ui.text(label, x, y, 10, Ui.TXT_DIM);
        float bx = x + ui.s(74), bw = w - ui.s(74) - ui.s(34), bh = ui.s(8);
        ui.b.rect(bx, y + ui.s(3), bw, bh, 0xFF1A1C1E);
        if (signed) { float mid = bx + bw / 2, len = bw / 2 * Math.min(1, Math.abs(v) / 100f); ui.b.rect(v >= 0 ? mid : mid - len, y + ui.s(3), len, bh, v >= 0 ? col : Ui.RED); ui.b.rect(mid, y + ui.s(1), 1, bh + ui.s(4), 0xFF5A5C56); }
        else ui.b.rect(bx, y + ui.s(3), bw * Math.min(1, Math.max(0, v) / 100f), bh, col);
        ui.textR(String.format(signed ? "%+.0f" : "%.0f", v), x + w, y, 11, Ui.TXT);
    }

    public void rivalries(Ui ui, Profile p, float x, float y, float w, float h) {
        RivalryEngine e = RivalryWorld.engine(p);
        RivalryEngine.Wallet wallet = RivalryWorld.wallet(p);
        List<RivalryState.Group> groups = e.s.active();
        if (e.s.group(selectedGroup) == null && !groups.isEmpty()) selectedGroup = groups.get(0).id;
        // ---- left: every group, how it stands with you, and what it is doing
        float lw = Math.min(ui.s(360), w * 0.36f), rh = Math.min(ui.s(58), (h - ui.s(4)) / Math.max(1, groups.size()));
        for (int i = 0; i < groups.size(); i++) {
            RivalryState.Group g = groups.get(i);
            RivalryState.Leader l = e.s.leaderOf(g);
            float ry = y + i * rh;
            boolean sel = g.id.equals(selectedGroup), hov = ui.hover(x, ry, lw, rh - ui.s(4));
            ui.b.rect(x, ry, lw, rh - ui.s(4), sel ? 0xFF2A2416 : Ui.mix(0xFF14161A, 0xFF1E2024, ui.ease("rg" + g.id, hov)));
            ui.b.rect(x, ry, ui.s(5), rh - ui.s(4), g.colour);
            if (sel) ui.b.outline(x, ry, lw, rh - ui.s(4), 1, 0xFF8A6420);
            float fav = g.playerRelation.favor;
            Doctrine.Status st = Doctrine.status(fav);
            int sc = fav <= -51 ? Ui.RED : fav <= -11 ? 0xFFE08A30 : fav < 20 ? Ui.TXT : fav < 50 ? 0xFFB8D048 : Ui.GREEN;
            Layout.fit(ui, g.name, x + ui.s(14), ry + ui.s(4), lw - ui.s(130), 13, 9, Ui.TXT_HI);
            ui.textR(e.treaty(g, "alliance") ? "ALLIED" : e.treaty(g, "truce") ? "TRUCE" : st.name().toUpperCase(), x + lw - ui.s(8), ry + ui.s(5), 10, e.treaty(g, null) ? Ui.AMBER : sc);
            if (rh >= ui.s(44)) Layout.fit(ui, Doctrine.display(g.faction) + "  -  " + (l == null ? "leaderless" : l.title + " " + l.name) + "  -  " + g.size + " strong", x + ui.s(14), ry + ui.s(21), lw - ui.s(22), 10, 8, Ui.TXT_DIM);
            float by = ry + rh - ui.s(13), bw = lw - ui.s(28), mid = x + ui.s(14) + bw / 2;
            ui.b.rect(x + ui.s(14), by, bw, ui.s(4), 0xFF1A1C1E);
            float len = bw / 2 * Math.min(1, Math.abs(fav) / 100f);
            ui.b.rect(fav >= 0 ? mid : mid - len, by, len, ui.s(4), sc);
            ui.b.rect(mid, by - ui.s(1), 1, ui.s(6), 0xFF5A5C56);
            if (hov && ui.click && !ui.consumed) { ui.consumed = true; selectedGroup = g.id; c.sfx("ui_tab", 0.4f); }
        }
        RivalryState.Group g = e.s.group(selectedGroup);
        if (g == null) return;
        RivalryState.Leader l = e.s.leaderOf(g);
        float dx = x + lw + ui.s(18), dw = x + w - dx, dy = y;
        ui.b.rect(dx - ui.s(9), y, 1, h, 0xFF2E2A22);
        // ---- header: the group and the one who runs it
        ui.b.rect(dx, dy, ui.s(6), ui.s(40), g.colour);
        ui.text(g.name, dx + ui.s(14), dy - ui.s(2), 20, Ui.TXT_HI);
        StringBuilder ground = new StringBuilder();
        for (String t : g.territoryIds) ground.append(ground.length() > 0 ? ", " : "").append(RivalryWorld.DISTRICTS.name(t));
        Layout.fit(ui, Doctrine.display(g.faction) + "  -  \"" + absentialis.engine.util.Json.str(Doctrine.faction(g.faction), "question", "") + "\"  -  holds " + ground + "  -  " + g.size + " strong, morale " + Math.round(g.morale * 100) + "%", dx + ui.s(14), dy + ui.s(24), dw - ui.s(16), 11, 8, Ui.TXT_DIM);
        dy += ui.s(50);
        float colW = (dw - ui.s(18)) / 2;
        // left column: the leader, relations
        float ly = dy;
        if (l != null) {
            ui.text((l.title + " " + l.name).trim().toUpperCase(), dx, ly, 14, Ui.TXT_HI);
            ly += ui.s(20);
            ly += ui.wrap(String.join(", ", l.traits) + (l.ambitions.isEmpty() ? "" : ". Wants " + l.ambitions.get(0)) + (l.injuries.isEmpty() ? "" : ". Carries " + String.join(", ", l.injuries)) + ". Met you " + l.encounters + (l.encounters == 1 ? " time." : " times."), dx, ly, colW, 11, Ui.TXT) + ui.s(4);
            ly += ui.wrap("\"" + e.line(l, g) + "\"", dx, ly, colW, 12, Ui.AMBER) + ui.s(10);
        }
        ui.text("HOW THE GROUP SEES YOU", dx, ly, 10, Ui.AMBER);
        ly += ui.s(16);
        RivalryState.Relation r = g.playerRelation;
        meter(ui, "Favor", r.favor, dx, ly, colW, Ui.GREEN, true); ly += ui.s(16);
        meter(ui, "Trust", r.trust, dx, ly, colW, 0xFF7AA0E0, true); ly += ui.s(16);
        meter(ui, "Respect", r.respect, dx, ly, colW, 0xFFE2B030, true); ly += ui.s(16);
        meter(ui, "Fear", r.fear, dx, ly, colW, 0xFFB080D0, false); ly += ui.s(16);
        meter(ui, "Hostility", r.hostility, dx, ly, colW, Ui.RED, false); ly += ui.s(16);
        meter(ui, "Debt to you", r.debt, dx, ly, colW, 0xFF7DBA5C, false); ly += ui.s(20);
        if (l != null) {
            ui.text("HOW " + l.name.toUpperCase().split(" ")[0] + " SEES YOU", dx, ly, 10, Ui.AMBER);
            ly += ui.s(16);
            meter(ui, "Trust", l.playerRelation.trust, dx, ly, colW, 0xFF7AA0E0, true); ly += ui.s(16);
            meter(ui, "Hostility", l.playerRelation.hostility, dx, ly, colW, Ui.RED, false); ly += ui.s(16);
            meter(ui, "Rivalry", e.rivalry(l) * 100, dx, ly, colW, 0xFFE08A30, false); ly += ui.s(20);
        }
        // their own quarrels
        ui.text("THEIR RIVALS", dx, ly, 10, Ui.AMBER);
        ly += ui.s(16);
        List<RivalryState.Group> others = new ArrayList<>(groups);
        others.remove(g);
        others.sort((a, b) -> Float.compare(e.rivalry(g, b), e.rivalry(g, a)));
        for (int i = 0; i < Math.min(3, others.size()) && ly < y + h - ui.s(14); i++) {
            RivalryState.Group o = others.get(i);
            float rv = e.rivalry(g, o);
            if (rv < 0.05f) break;
            ui.b.rect(dx, ly + ui.s(2), ui.s(4), ui.s(10), o.colour);
            Layout.fit(ui, o.name, dx + ui.s(10), ly, colW - ui.s(110), 11, 8, Ui.TXT);
            ui.b.rect(dx + colW - ui.s(96), ly + ui.s(4), ui.s(60), ui.s(6), 0xFF1A1C1E);
            ui.b.rect(dx + colW - ui.s(96), ly + ui.s(4), ui.s(60) * rv, ui.s(6), 0xFFE08A30);
            ui.textR(Math.round(rv * 100) + "%", dx + colW, ly, 10, Ui.TXT_DIM);
            ly += ui.s(16);
        }
        // right column: standing, treaty, what they have learned, what they are doing, what you can do
        float rx = dx + colW + ui.s(18), ry = dy;
        Doctrine.Status st = Doctrine.status(r.favor);
        ui.text(st.name().toUpperCase() + "  " + Math.round(r.favor), rx, ry, 14, Ui.TXT_HI);
        ry += ui.s(20) + ui.wrap(st.text(), rx, ry + ui.s(20), colW, 11, Ui.TXT_DIM) + ui.s(6);
        RivalryState.Treaty tr = e.s.treatyWith(g.id, null);
        if (tr != null) {
            ry += ui.wrap(("alliance".equals(tr.kind) ? absentialis.engine.util.Json.str(absentialis.engine.util.Json.obj(Doctrine.faction(g.faction), "alliance"), "name", "Alliance") : "Truce") + ": " + String.join(", ", tr.terms) + (tr.obligations.isEmpty() ? "" : ". You owe: " + String.join("; ", tr.obligations)) + (tr.tribute > 0 ? " (" + tr.tribute + " coins each review)" : ""), rx, ry, colW, 11, Ui.AMBER) + ui.s(6);
        } else {
            List<String> blockers = e.allianceBlockers(g);
            ry += ui.wrap(blockers.isEmpty() ? "An alliance can be negotiated now." : "Alliance needs: " + String.join("; ", blockers) + ".", rx, ry, colW, 11, blockers.isEmpty() ? Ui.GREEN : Ui.TXT_DIM) + ui.s(6);
        }
        if (!g.adaptations.isEmpty()) {
            StringBuilder ad = new StringBuilder();
            for (Object o : Doctrine.adaptations()) { @SuppressWarnings("unchecked") java.util.Map<String, Object> rule = (java.util.Map<String, Object>) o; if (g.adapted(absentialis.engine.util.Json.str(rule, "id", "")) > 0) ad.append(ad.length() > 0 ? ", " : "").append(absentialis.engine.util.Json.str(rule, "name", "")); }
            ry += ui.wrap("Learned from experience: " + ad + ".", rx, ry, colW, 11, 0xFFE08A30) + ui.s(6);
        }
        ui.text("LATELY", rx, ry, 10, Ui.AMBER);
        ry += ui.s(15);
        for (int i = 0; i < Math.min(3, g.history.size()); i++) ry += ui.wrap(g.history.get(i).replaceFirst("^t(\\d+) ", "Tick $1: "), rx, ry, colW, 10, Ui.TXT_DIM) + ui.s(2);
        ry += ui.s(6);
        // jobs
        for (RivalryState.Job j : new ArrayList<>(g.jobs)) {
            if (ry > y + h - ui.s(150)) break;
            ui.b.rect(rx, ry, colW, ui.s(38), 0xFF14161A);
            Layout.fit(ui, j.title + "  (" + j.have + "/" + j.need + ")", rx + ui.s(8), ry + ui.s(4), colW - ui.s(120), 12, 9, Ui.TXT_HI);
            ui.text("+" + j.payout + " cash", rx + ui.s(8), ry + ui.s(21), 10, Ui.GREEN);
            if ("open".equals(j.status)) { if (ui.button("rjob" + j.id, rx + colW - ui.s(104), ry + ui.s(5), ui.s(98), ui.s(28), "TAKE JOB", null, true)) say(e.acceptJob(g, j)); }
            else ui.textR(j.status.toUpperCase(), rx + colW - ui.s(8), ry + ui.s(12), 10, Ui.AMBER);
            if (ui.hover(rx, ry, colW, ui.s(38))) ui.tip(j.text);
            ry += ui.s(42);
        }
        // ---- what you can do: the same surface for every group, whatever its faction
        float ay = y + h - ui.s(104), bw = (dw - ui.s(8) * 3) / 4, bh = ui.s(30);
        String[][] acts = {{"TALK", "talk"}, {"SUPPLIES 400", "gift"}, {"BRIBE 900", "bribe"}, {"INTIMIDATE", "intimidate"}, {"TRUCE", "truce"}, {"ALLIANCE", "alliance"}, {"ASK FOR AID", "aid"}, {tr != null ? "BREAK AGREEMENT" : "INFORM ON RIVAL", tr != null ? "betray" : "intel"}};
        for (int i = 0; i < acts.length; i++) {
            float bx = dx + (i % 4) * (bw + ui.s(8)), byy = ay + (i / 4) * (bh + ui.s(6));
            if (!ui.button("ract" + i, bx, byy, bw, bh, acts[i][0], null, true)) continue;
            switch (acts[i][1]) {
                case "talk" -> say(e.talk(g));
                case "gift" -> say(e.gift(g, wallet));
                case "bribe" -> say(e.bribe(g, wallet));
                case "intimidate" -> say(e.intimidate(g, p.prog.rank()));
                case "truce" -> say(e.truce(g, wallet));
                case "alliance" -> say(e.alliance(g, wallet));
                case "aid" -> say(e.aid(g));
                case "betray" -> say(e.betray(g));
                default -> say(e.intel(g, others.isEmpty() ? null : others.get(0)));
            }
            SaveSystem.save(p);
        }
        float fy = y + h - ui.s(30);
        ui.b.rect(dx, fy - ui.s(4), dw, 1, 0xFF2E2A22);
        String last = !feedback.isEmpty() ? feedback.get(0) : e.s.notices.isEmpty() ? "Favor is earned and lost by what a group sees you do or hears of it." : e.s.notices.get(e.s.notices.size() - 1);
        Layout.fit(ui, last, dx, fy, dw, 12, 8, last.startsWith("!") ? Ui.RED : Ui.TXT);
    }
}
