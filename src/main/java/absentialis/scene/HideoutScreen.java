package absentialis.scene;

import absentialis.gameplay.economy.Economy;
import absentialis.gameplay.inventory.Container;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.inventory.Loadout;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.gameplay.missions.Contracts;
import absentialis.gameplay.progression.Challenges;
import absentialis.gameplay.progression.Progression;
import absentialis.gameplay.weapons.Gunsmith;
import absentialis.gameplay.weapons.WeaponService;
import absentialis.persistence.Profile;
import absentialis.persistence.SaveSystem;
import absentialis.sim.ZoneState;
import absentialis.ui.InventoryUi;
import absentialis.ui.Ui;
import absentialis.world.District;
import absentialis.world.MapData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hideout menu (addendum P2.4): STAT (rank / challenges / prestige) - INV (stash + case, traders)
 * - MAP (influence) - WORKBENCH (gunsmith + clean, facilities) - CONTRACTS. Screens slide/fade in
 * over 120 ms; the same item instances appear identically in every view.
 */
public final class HideoutScreen {
    /**
     * 0.7: the tab that was RANK is OPERATOR (the old OPERATOR tab - re-customising the character - is gone: a
     * character is made once, with the profile, and is then who they are). SQUAD is not drawn at all below Rank 4.
     */
    public static final String[] TABS = {"OPERATOR", "INVENTORY", "SHOP", "WORKBENCH", "SQUAD", "MAP", "CONTRACTS", "PROFILE"};
    private static final String[] GLYPHS = {"star", "inv", "coin", "bench", "radio", "map", "contracts", "chevron"};
    public static final int TAB_OPERATOR = 0, TAB_INVENTORY = 1, TAB_SHOP = 2, TAB_WORKBENCH = 3, TAB_SQUAD = 4, TAB_MAP = 5, TAB_CONTRACTS = 6, TAB_PROFILE = 7;

    /** Whether a tab is on the bar for this survivor right now. */
    public static boolean tabShown(Profile p, int tab) { return tab != TAB_SQUAD || SquadPanel.unlocked(p); }
    /** 0.6: the alliance choice, the profile card, and the LEADERBOARD and RIVALRIES tabs of the deployment screen. */
    private final OpsPanels ops;
    /** Which page of the deployment screen is showing: 0 briefing, 1 leaderboard, 2 rivalries. */
    public int deployTab;
    public boolean deployOpen() { return deployOpen; }
    private final RankPanel rank;
    private final ShopPanel shop;
    private final WorkbenchPanel workbench;
    private final SquadPanel squad;
    private final Ctx c;
    private final LoadoutPanel panel;
    public int tab;
    private float tabAnim = 1, stashScroll;
    private boolean arrivedShown;
    private boolean trade, deployOpen, prestigeOpen, upgradesOpen;
    private String traderId = "mara";
    private ItemInstance benchWeapon;
    private int insertion;
    /** 0.7: there is no time limit to choose any more. What is chosen is when you go in: 0 morning, 1 afternoon, 2 midnight. */
    private int timeOfDay;
    public static final String[] TIMES = {"MORNING", "AFTERNOON", "MIDNIGHT"};
    public static final double[] TIME_HOUR = {8.0, 14.0, 0.0};
    public String action;           // "deploy", "settings", "title" for GameApp
    public int deployInsertion;
    public double deployHour = 8.0;
    /** What the last operation opened (0.7): shown once, when the survivor is back at the hideout. */
    public final UnlockNotice unlocks = new UnlockNotice();

    public HideoutScreen(Ctx c) { this.c = c; this.panel = new LoadoutPanel(c); rank = new RankPanel(c); shop = new ShopPanel(c); workbench = new WorkbenchPanel(c); squad = new SquadPanel(c); ops = new OpsPanels(c); shop.squad = squad; }

    private Profile p() { return c.profile; }

    public void enter() {
        tabAnim = 0;
        c.win.setCursorLocked(false);
        p().applyCapacity();
        p().contracts.refill(p().seed ^ p().stat("raids"), p().prog.rank());
        if (c.map.ready()) c.map.rebuildHatch(p().zone);
    }

    public void render(float dt) {
        Ui ui = c.ui;
        int W = c.win.width, H = c.win.height;
        org.lwjgl.opengl.GL11.glViewport(0, 0, W, H);
        org.lwjgl.opengl.GL11.glClearColor(0.05f, 0.055f, 0.05f, 1);
        org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
        ui.b.begin(W, H);
        ui.begin(dt, W, H);
        ui.showCursor = true;
        backdrop(ui);
        tabAnim = Math.min(1, tabAnim + dt / Ui.EASE);
        float ease = 1 - (1 - tabAnim) * (1 - tabAnim);
        // 0.6: a survivor who has not chosen a side yet is asked before anything else in the hideout answers
        boolean unaligned = !absentialis.gameplay.Alliance.chosen(p());
        if (!tabShown(p(), tab)) tab = TAB_OPERATOR;
        boolean modal = deployOpen || rank.modal(p()) || upgradesOpen || unaligned || unlocks.open() || !deciding.isEmpty();
        ui.block = modal;
        header(ui);
        float top = ui.s(118) + (1 - ease) * ui.s(14);
        switch (tab) {
            case 0 -> rank.draw(ui, p(), top);
            case 1 -> inventory(ui, top);
            case 2 -> shop.draw(ui, p(), top);
            case 3 -> { workbench.draw(ui, p(), top); if (ui.button("upgrades", ui.s(36), ui.h - ui.s(24) - ui.s(52), ui.s(266), ui.s(40), "HIDEOUT FACILITIES", "cog", true)) upgradesOpen = true; }
            case 4 -> squad.draw(ui, p(), top);
            case 5 -> map(ui, top);
            case 6 -> contracts(ui, top);
            default -> ops.profile(ui, p(), top);
        }
        if (tabAnim < 1) ui.b.rect(0, top - ui.s(10), W, H, Ui.alpha(0xFF0A0B0A, 1 - ease));
        ui.block = false;
        if (unaligned) { if (ops.allianceModal(ui, p())) enter(); }
        else if (rank.modal(p())) rank.drawModals(ui, p());
        else if (deployOpen) deployModal(ui);
        else if (upgradesOpen) upgradesModal(ui);
        else if (unlocks.open()) unlocks.draw(ui, c);
        if (tab != 1) { c.inv.flashTime -= dt; if (c.inv.flashTime > 0) { float a = Math.min(1, c.inv.flashTime), fw = ui.tw(c.inv.flash, 16); ui.b.rect(ui.w / 2f - fw / 2 - ui.s(12), ui.h - ui.s(96), fw + ui.s(24), ui.s(30), Ui.alpha(0xD0101010, a)); ui.textC(c.inv.flash, ui.w / 2f, ui.h - ui.s(90), 16, Ui.alpha(c.inv.flashColor, a)); } }
        ui.textR(absentialis.Version.LABEL, ui.w - ui.s(8), ui.h - ui.s(18), 11, Ui.TXT_DIM);
        if (rank.freshStartRequested) freshStart();
        c.console.draw(ui, dt);
        ui.end();
        ui.b.end();
    }

    /** Prestige Award "Fresh Start": the profile is replaced by a brand-new one that keeps only the callsign and the operator's look. */
    private void freshStart() {
        rank.freshStartRequested = false;
        Profile old = p(), np = new Profile();
        np.slot = old.slot; np.name = old.name; np.appearance = old.appearance.copy(); np.seed = old.seed;
        np.starterKit();
        np.contracts.refill(np.seed, 1);
        np.applyCapacity();
        c.profile = np;
        SaveSystem.save(np);
        enter();
        tab = 0;
        c.inv.say("Fresh Start: you are a Private again", Ui.AMBER);
    }

    private void backdrop(Ui ui) {
        // dim bunker: concrete panels and a hanging lamp glow, pixel-snapped bands
        for (int y = 0; y < ui.h; y += ui.s(48)) ui.b.rect(0, y, ui.w, ui.s(47), (y / ui.s(48)) % 2 == 0 ? 0xFF101210 : 0xFF0E100E);
        ui.b.rect(0, 0, ui.w, ui.s(100), 0xFF161816);
        ui.b.rect(0, ui.s(100), ui.w, ui.s(3), 0xFF8A6420);
    }

    private void header(Ui ui) {
        Profile p = p();
        ui.text("ABSENTIALIS", ui.s(24), ui.s(14), 26, Ui.TXT_HI);
        ui.text("v" + absentialis.Version.NUMBER, ui.s(206), ui.s(24), 12, Ui.AMBER);
        ui.text("HIDEOUT  -  Day " + p.zone.day + String.format("  %02d:00", (int) p.zone.hour % 24) + (p.zone.weatherRain > 0.5f ? "  -  Rain" : ""), ui.s(26), ui.s(48), 12, Ui.TXT_DIM);
        // rank chip
        float rx = ui.s(270);
        int g = Math.max(2, ui.s(2));
        ui.glyph(p.prog.master() ? "star" : "chevron", rx, ui.s(16), g, 0xFFFFFFFF);
        ui.text(p.name + "  -  " + p.prog.title(), rx + ui.s(32), ui.s(14), 16, Ui.TXT_HI);
        ui.bar(rx + ui.s(32), ui.s(38), ui.s(200), ui.s(8), p.prog.rankProgress(), 0xFFE2A034);
        ui.glyph("coin", rx + ui.s(250), ui.s(30), g, 0xFFFFFFFF);
        ui.text(absentialis.Version.coins(p.scrip), rx + ui.s(282), ui.s(31), 16, Ui.AMBER);
        ui.glyph("heart", rx + ui.s(420), ui.s(30), g, 0xFFFFFFFF);
        ui.text(Math.round(p.health) + "%", rx + ui.s(452), ui.s(31), 16, p.health < 60 ? Ui.RED : Ui.TXT);
        // tabs
        float tw = Math.min(ui.s(138), (ui.w - ui.s(380)) / TABS.length - ui.s(4)), tx = ui.s(24);
        for (int i = 0, n = 0; i < TABS.length; i++) {
            if (!tabShown(p, i)) continue;                    // not locked: not there
            if (ui.tab("ht" + i, tx + n * (tw + ui.s(4)), ui.s(62), tw, ui.s(34), TABS[i], GLYPHS[i], tab == i) && tab != i) { tab = i; tabAnim = 0; }
            n++;
        }
        float bx = ui.w - ui.s(24);
        if (ui.button("deploy", bx - ui.s(170), ui.s(54), ui.s(170), ui.s(42), "DEPLOY", "flag", c.world != null)) { deployOpen = true; deployTab = 0; }
        if (ui.button("settings", bx - ui.s(170) - ui.s(56), ui.s(54), ui.s(48), ui.s(42), null, "cog", true)) action = "settings";
        if (ui.button("title", bx - ui.s(170) - ui.s(112), ui.s(54), ui.s(48), ui.s(42), null, "arrow", true)) { SaveSystem.save(p()); action = "title"; }
        if (c.world == null) ui.textR("Surveying the Zone...", bx, ui.s(20), 12, Ui.TXT_DIM);
    }

    // ================================================================== INV
    private void inventory(Ui ui, float top) {
        Profile p = p();
        var inv = c.inv;
        inv.begin(34);
        inv.fit(ui.h - top - ui.s(24) - ui.s(44) - ui.s(160), p.loadout.caseGrid.h + (p.loadout.packGrid() == null ? 0 : p.loadout.packGrid().h + 2));
        inv.quickA = p.stash;
        inv.stash = p.stash;
        inv.quickB = p.loadout.caseGrid;
        inv.owner = p.loadout;
        inv.prog = p.prog;
        inv.allowDrop = false;
        // 1.3: in the hideout the discard case throws things away (asked twice)
        inv.discardVerb = "Discard"; inv.discardConfirm = true;
        inv.onDiscard = items -> inv.say("Threw away " + items.size() + (items.size() == 1 ? " thing" : " things"), Ui.TXT_DIM);
        inv.extraActions.clear();
        inv.extraActions.add("quick");
        inv.onAction = (it, act) -> {
            String r = panel.act(p, it, act, p.stash);
            if (r != null) inv.say(r, Ui.TXT);
        };
        float h = ui.h - top - ui.s(24);
        float leftW = p.stash.w * inv.cell + ui.s(40);
        float x0 = ui.s(24);
        float cy = ui.window("Stash  " + p.stash.w + "x" + p.stash.h, "inv", x0, top, leftW, h);
        {
            float gh = h - (cy - top) - ui.s(96);
            int visRows = Math.max(4, (int) (gh / inv.cell));
            if (ui.hover(x0, cy, leftW, gh + ui.s(30))) stashScroll -= (float) c.in.scroll * 2;
            // 0.7: the list keeps its place while you browse - which is how a purchase used to land in rows that had been
            // scrolled off the top and looked as if it had never arrived. Whatever has just come in is brought into view.
            if (inv.arrived != null && inv.arrivedT > 0 && inv.arrived.container == p.stash && !arrivedShown) {
                int row = inv.arrived.y, hgt = inv.arrived.h();
                if (row < (int) stashScroll) stashScroll = row;
                else if (row + hgt > (int) stashScroll + visRows) stashScroll = row + hgt - visRows;
                arrivedShown = true;
            }
            if (inv.arrivedT > 0) { inv.arrivedT -= ui.dt; if (inv.arrivedT <= 0) { inv.arrived = null; arrivedShown = false; } }
            stashScroll = Math.max(0, Math.min(p.stash.h - visRows, stashScroll));
            float gy = cy + ui.s(26);
            ui.b.scissor((int) x0, (int) gy - 2, (int) leftW, (int) (visRows * inv.cell) + 4);
            inv.grid(p.stash, x0 + ui.s(18), gy - (int) stashScroll * inv.cell, null);
            ui.b.noScissor();
            boolean scrolled = (int) stashScroll > 0;
            ui.text("STASH  (scroll)  rows " + (int) stashScroll + "-" + Math.min(p.stash.h, (int) stashScroll + visRows) + " of " + p.stash.h + (scrolled ? "   -   scrolled down" : ""), x0 + ui.s(18), cy, 12, scrolled ? Ui.AMBER : Ui.TXT_DIM);
            // a scroll bar, so where the view sits in the stash is never a guess
            if (p.stash.h > visRows) {
                float trackH = visRows * inv.cell, th = trackH * visRows / p.stash.h, ty = gy + (trackH - th) * ((int) stashScroll / (float) (p.stash.h - visRows));
                ui.b.rect(x0 + leftW - ui.s(12), gy, ui.s(4), trackH, 0xFF1A1C1B);
                ui.b.rect(x0 + leftW - ui.s(12), ty, ui.s(4), th, scrolled ? 0xFFE2A034 : 0xFF8A6420);
            }
            float by = gy + visRows * inv.cell + ui.s(10);
            if (ui.button("sortStash", x0 + ui.s(18), by, ui.s(120), ui.s(30), "SORT", "sort", true)) { InventoryService.Result r = InventoryService.autoSort(p.stash); inv.say(r.ok() ? "Stash sorted" : r.msg(), r.ok() ? Ui.GREEN : Ui.RED); }
            boolean broke = p.scrip < 500 && !hasWeapon(p);
            if (ui.button("fallback", x0 + ui.s(146), by, leftW - ui.s(164), ui.s(30), "FALLBACK KIT", "radio", broke && p.lastFallbackDay != p.zone.day)) {
                for (ItemInstance i : Economy.fallbackKit()) InventoryService.add(i, p.stash);
                p.lastFallbackDay = p.zone.day;
                inv.say("Quartermaster issued a fallback kit (no resale value)", Ui.GREEN);
            }
            presets(ui, x0 + ui.s(18), by + ui.s(36), leftW - ui.s(36));
        }
        float rx = x0 + leftW + ui.s(16);
        float rw = panel.width(p.loadout);
        float ry = ui.window("Loadout", "inv", rx, top, rw + ui.s(24), h);
        panel.draw(p, rx + ui.s(12), ry, rw, h - (ry - top) - ui.s(16), p.stash);
        float ix = rx + rw + ui.s(40);
        if (ix + ui.s(260) < ui.w) inv.inspectPanel(ix, top, Math.min(ui.s(320), ui.w - ix - ui.s(24)), h);
        if (c.in.pressed("sort")) InventoryService.autoSort(p.loadout.caseGrid);
        inv.end();
        if (c.in.keyDown(org.lwjgl.glfw.GLFW.GLFW_KEY_F) && inv.hover != null) inv.fullInspect(inv.spin, 0.45f);
    }

    private static boolean hasWeapon(Profile p) {
        for (ItemInstance i : p.allItems()) if (i.def().weapon != null && !i.def().weapon.melee()) return true;
        return false;
    }

    /** Loadout presets: save equipped/case defs; apply pulls matching instances from the stash. */
    private void presets(Ui ui, float x, float y, float w) {
        Profile p = p();
        int n = p.prog.presets();
        float bw = Math.min(ui.s(110), (w - ui.s(8)) / Math.max(1, n));
        for (int i = 0; i < n; i++) {
            boolean has = i < p.presets.size() && p.presets.get(i) != null;
            if (ui.button("preset" + i, x + i * (bw + ui.s(4)), y, bw, ui.s(30), (has ? "LOAD " : "SAVE ") + (i + 1), null, true)) {
                if (ui.shift || !has) { savePreset(i); c.inv.say("Preset " + (i + 1) + " saved (shift+click to overwrite)", Ui.GREEN); }
                else c.inv.say(applyPreset(i), Ui.TXT);
            }
        }
    }

    private void savePreset(int i) {
        Profile p = p();
        Map<String, Object> m = new LinkedHashMap<>();
        for (String s : Loadout.SLOTS) if (p.loadout.slot(s) != null) m.put(s, p.loadout.slot(s).defId);
        List<Object> cs = new ArrayList<>();
        for (ItemInstance it : p.loadout.caseGrid.items) cs.add(it.defId);
        m.put("case", cs);
        while (p.presets.size() <= i) p.presets.add(null);
        p.presets.set(i, m);
    }

    private String applyPreset(int i) {
        Profile p = p();
        Map<String, Object> m = p.presets.get(i);
        int got = 0, miss = 0;
        for (String s : Loadout.SLOTS) {
            Object id = m.get(s);
            if (id == null || p.loadout.slot(s) != null && p.loadout.slot(s).defId.equals(id)) continue;
            ItemInstance found = null;
            for (ItemInstance it : p.stash.items) if (it.defId.equals(id)) { found = it; break; }
            if (found != null && InventoryService.equip(found, p.loadout, s).ok()) got++; else miss++;
        }
        @SuppressWarnings("unchecked") List<Object> cs = (List<Object>) m.getOrDefault("case", List.of());
        List<String> want = new ArrayList<>();
        for (Object o : cs) want.add(String.valueOf(o));
        for (ItemInstance it : p.loadout.caseGrid.items) want.remove(it.defId);
        for (String id : want) {
            ItemInstance found = null;
            for (ItemInstance it : p.stash.items) if (it.defId.equals(id)) { found = it; break; }
            if (found != null && InventoryService.store(found, p.loadout).ok()) got++; else miss++;
        }
        return "Preset applied: " + got + " moved" + (miss > 0 ? ", " + miss + " missing from stash" : "");
    }

    // ================================================================== MAP
    private void map(Ui ui, float top) {
        Profile p = p();
        float h = ui.h - top - ui.s(24), s = Math.min(h - ui.s(60), ui.w * 0.55f);
        float x = ui.s(24);
        float cy = ui.window("Zone Map  -  Influence", "map", x, top, s + ui.s(28), h);
        if (!c.map.ready()) { ui.text("Map is being drawn...", x + ui.s(20), cy, 14, Ui.TXT_DIM); return; }
        c.map.draw(ui, p, x + ui.s(14), cy, s, s, 0, 0, absentialis.world.World.W, null, Float.NaN, 0, 0, true);
        float x2 = x + s + ui.s(46), w2 = ui.w - x2 - ui.s(24);
        float cy2 = ui.window("District Intel", "radio", x2, top, w2, h);
        String[] fn = {"MIL", "RAI", "SUR", "CUL"};
        int[] fc = {0xFF7A8A5A, 0xFFB0503A, 0xFF5A80B0, 0xFF9A3A6A};
        for (District d : District.CITY) {
            String ctl = p.zone.controller(d);
            ui.text(d.display.toUpperCase(), x2 + ui.s(16), cy2, 14, Ui.TXT_HI);
            ui.textR(ctl == null ? "CONTESTED" : ctl.toUpperCase(), x2 + w2 - ui.s(16), cy2, 12, ctl == null ? Ui.AMBER : Ui.TXT);
            cy2 += ui.s(20);
            float bw = w2 - ui.s(32);
            float bx = x2 + ui.s(16);
            float tot = 0;
            for (int f = 0; f < 4; f++) tot += p.zone.influence[d.ordinal()][f];
            for (int f = 0; f < 4; f++) {
                float fw = bw * p.zone.influence[d.ordinal()][f] / Math.max(0.01f, tot);
                ui.b.rect(bx, cy2, fw, ui.s(10), fc[f]);
                if (fw > ui.s(30)) ui.text(fn[f], bx + ui.s(3), cy2 - ui.s(1), 9, 0xFF0A0A0A);
                bx += fw;
            }
            cy2 += ui.s(14);
            ui.text(String.format("Infected %d%%   Food %d%%   Medicine %d%%   Ammo %d%%   %s", Math.round(p.zone.infected[d.ordinal()] * 100),
                    Math.round(p.zone.supply[d.ordinal()][0] * 100), Math.round(p.zone.supply[d.ordinal()][2] * 100), Math.round(p.zone.supply[d.ordinal()][3] * 100),
                    p.zone.power[d.ordinal()] ? "POWER ON" : "no power"), x2 + ui.s(16), cy2, 11, Ui.TXT_DIM);
            cy2 += ui.s(26);
        }
        ui.text("NEWS", x2 + ui.s(16), cy2, 14, Ui.TXT_HI);
        cy2 += ui.s(22);
        if (p.zone.news.isEmpty()) ui.text("Quiet. For now.", x2 + ui.s(16), cy2, 12, Ui.TXT_DIM);
        for (String n : p.zone.news) { if (cy2 > top + h - ui.s(30)) break; ui.text(n, x2 + ui.s(16), cy2, 11, Ui.TXT); cy2 += ui.s(17); }
    }

    private static final String[][] FACILITIES = {
            {"workbench", "Workbench", "3", "Unlocks crafting tiers (ammo, repair kits, flares)"},
            {"medical", "Medical Bay", "3", "Medical crafting"},
            {"kitchen", "Kitchen", "2", "Water purification and cooking"},
            {"radio", "Radio Room", "2", "L1: exits on the map  L2: known infected nests"},
            {"stash", "Stash Racks", "3", "+4 stash rows per level"}};

    public static int upgradeCost(int nextLevel) { return (int) (1500 * Math.pow(nextLevel, 1.6)); }

    private void upgradesModal(Ui ui) {
        Profile p = p();
        ui.b.rect(0, 0, ui.w, ui.h, 0xC0000000);
        float w = ui.s(680), h = ui.s(120) + FACILITIES.length * ui.s(62), x = (ui.w - w) / 2, y = (ui.h - h) / 2;
        float cy = ui.window("Hideout Facilities", "cog", x, y, w, h);
        for (String[] f : FACILITIES) {
            int lvl = p.hideout.getOrDefault(f[0], 0), max = Integer.parseInt(f[2]);
            ui.text(f[1] + "  -  level " + p.level(f[0]) + (p.level(f[0]) != lvl ? " (token +1)" : ""), x + ui.s(20), cy, 14, Ui.TXT_HI);
            ui.text(f[3], x + ui.s(20), cy + ui.s(19), 11, Ui.TXT_DIM);
            int cost = upgradeCost(lvl + 1);
            boolean can = lvl < max && p.scrip >= cost;
            if (ui.button("up" + f[0], x + w - ui.s(210), cy, ui.s(190), ui.s(38), lvl >= max ? "MAXED" : "UPGRADE " + absentialis.Version.coins(cost), null, can)) {
                p.scrip -= cost;
                p.hideout.put(f[0], lvl + 1);
                p.applyCapacity();
                c.sfx("ui_buy", 0.7f);
                SaveSystem.save(p);
            }
            cy += ui.s(62);
        }
        // facility crafting
        if (ui.button("upclose", x + w - ui.s(150), y + h - ui.s(52), ui.s(130), ui.s(38), "CLOSE", "cross", true)) upgradesOpen = false;
        if (ui.button("upcraft", x + ui.s(20), y + h - ui.s(52), ui.s(300), ui.s(38), "CRAFT AT FACILITIES", "bench", true)) { upgradesOpen = false; craftOpen = !craftOpen; }
        if (c.in.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) upgradesOpen = false;
    }

    private boolean craftOpen;

    // ================================================================== CONTRACTS
    private float boardScroll;
    /** The investigation whose decision is being made ("" = none). */
    private String deciding = "";

    private void contracts(Ui ui, float top) {
        Profile p = p();
        float h = ui.h - top - ui.s(24), w = (ui.w - ui.s(64)) / 2;
        float x = ui.s(24);
        float cy = ui.window("Contract Board  -  ten jobs, three at a time, paid on extraction", "contracts", x, top, w, h);
        // the board scrolls: all ten contracts are on it unless they are being worked or resting
        float listTop = cy, listH = top + h - cy - ui.s(12), cardH = ui.s(104);
        float maxScroll = Math.max(0, p.contracts.board.size() * cardH - listH);
        if (ui.hover(x, listTop, w, listH) && !ui.block) boardScroll = Math.max(0, Math.min(maxScroll, boardScroll - (float) c.in.scroll * cardH * 0.6f));
        boardScroll = Math.min(boardScroll, maxScroll);
        ui.b.scissor((int) x, (int) listTop, (int) w, (int) listH);
        float by = listTop - boardScroll;
        for (Contracts.Contract k : new ArrayList<>(p.contracts.board)) {
            if (by + cardH > listTop && by < listTop + listH) contractCard(ui, k, x + ui.s(14), by, w - ui.s(34), false);
            by += cardH;
        }
        ui.b.noScissor();
        if (maxScroll > 0) { float sbh = listH * listH / (p.contracts.board.size() * cardH); ui.b.rect(x + w - ui.s(10), listTop + (listH - sbh) * (boardScroll / maxScroll), ui.s(4), sbh, Ui.alpha(Ui.AMBER, 0.7f)); }
        if (p.contracts.board.isEmpty()) ui.text("Nothing on the board. Completed work comes back after a couple of operations.", x + ui.s(16), cy, 13, Ui.TXT_DIM);
        float x2 = x + w + ui.s(16);
        float activeH = ui.s(46) + Math.max(1, p.contracts.active.size()) * ui.s(104) + ui.s(8);
        float cy2 = ui.window("Active (" + p.contracts.active.size() + "/" + Contracts.MAX_ACTIVE + ")", "check", x2, top, w, activeH);
        if (p.contracts.active.isEmpty()) ui.text("No active contracts. Pay comes on extraction.", x2 + ui.s(16), cy2, 13, Ui.TXT_DIM);
        for (Contracts.Contract k : new ArrayList<>(p.contracts.active)) { contractCard(ui, k, x2 + ui.s(14), cy2, w - ui.s(28), true); cy2 += ui.s(104); }
        float my = top + activeH + ui.s(12);
        if (craftOpen) facilityCraft(ui, x2 + ui.s(14), my + ui.s(10), w - ui.s(28));
        else missions(ui, p, x2, my, w, top + h - my);
        if (!deciding.isEmpty()) decision(ui, p);
    }

    // ================================================================== THE NAMES OF THE DEAD (0.8)
    private static final String[] STAGE_WORDS = {"RUMOUR", "INVESTIGATING", "PROVEN - GET OUT ALIVE", "YOURS TO DECIDE", "CLOSED"};

    /**
     * The five investigations and where each stands. They are not accepted and cannot be failed: this is a journal,
     * and it says only what the survivor has been told, has seen, or has corroborated.
     */
    private void missions(Ui ui, Profile p, float x, float y, float w, float h) {
        absentialis.gameplay.missions.Missions ms = p.missions;
        float cy = ui.window("The Names of the Dead  -  " + ms.closed() + " of " + absentialis.gameplay.missions.Story.MISSIONS.size() + " closed", "search", x, y, w, h);
        // 1.0: the four faction chains, a line each: who to go and see, or what they have asked for
        absentialis.gameplay.missions.Chains.load();
        for (absentialis.gameplay.missions.Chains.Advisor ad : absentialis.gameplay.missions.Chains.ADVISORS) {
            int dn = p.chains.of(ad.chain()).done, of = absentialis.gameplay.missions.Chains.length(ad.chain());
            ui.b.rect(x + ui.s(14), cy, w - ui.s(28), ui.s(22), 0xFF14161A);
            ui.text(absentialis.gameplay.Alliance.name(ad.faction()).toUpperCase() + "  " + dn + "/" + of, x + ui.s(22), cy + ui.s(4), 12, dn >= of ? Ui.GREEN : Ui.TXT_HI);
            ui.text(absentialis.ui.Layout.elide(ui, p.chains.journal(ad.chain()), w - ui.s(210), 12), x + ui.s(180), cy + ui.s(4), 12, Ui.TXT);
            cy += ui.s(25);
        }
        cy += ui.s(6);
        float rowH = Math.max(ui.s(64), (y + h - cy - ui.s(10)) / Math.max(1, absentialis.gameplay.missions.Story.MISSIONS.size()));
        ui.b.scissor((int) x, (int) cy, (int) w, (int) (y + h - cy - ui.s(4)));
        for (absentialis.gameplay.missions.Story.Mission m : absentialis.gameplay.missions.Story.MISSIONS) {
            absentialis.gameplay.missions.Missions.State st = ms.of(m.id);
            int stage = st.stage.ordinal();
            boolean decide = st.stage == absentialis.gameplay.missions.Missions.Stage.DECISION, closed = st.stage == absentialis.gameplay.missions.Missions.Stage.CLOSED;
            float rx = x + ui.s(14), rw = w - ui.s(28);
            ui.b.rect(rx, cy, rw, rowH - ui.s(6), decide ? 0xFF241E10 : 0xFF14161A);
            ui.b.rect(rx, cy, ui.s(3), rowH - ui.s(6), decide ? Ui.AMBER : closed ? Ui.GREEN : stage > 0 ? 0xFFB8B2A4 : 0xFF4A4840);
            District d = District.byName(m.district);
            ui.text(m.id + "  " + m.title, rx + ui.s(12), cy + ui.s(6), 14, stage == 0 ? Ui.TXT : Ui.TXT_HI);
            ui.textR(d == null ? "" : d.display, rx + rw - ui.s(10), cy + ui.s(7), 11, Ui.TXT_DIM);
            // five pips: how far along it is
            for (int i = 0; i < 5; i++) ui.b.rect(rx + ui.s(12) + i * ui.s(16), cy + ui.s(27), ui.s(12), ui.s(4), i <= stage ? (closed ? Ui.GREEN : Ui.AMBER) : 0xFF2E2A22);
            ui.text(STAGE_WORDS[stage] + (st.corroborated() && stage >= 2 ? "  -  corroborated" : stage == 2 ? "  -  one source" : ""), rx + ui.s(100), cy + ui.s(23), 10, decide ? Ui.AMBER : closed ? Ui.GREEN : Ui.TXT_DIM);
            float tw = rw - ui.s(24) - (decide ? ui.s(110) : 0);
            ui.b.scissor((int) rx, (int) Math.max(cy, y), (int) rw, (int) Math.max(1, Math.min(rowH - ui.s(8), y + h - cy - ui.s(4))));
            ui.wrap(ms.journal(m), rx + ui.s(12), cy + ui.s(38), tw, 11, Ui.TXT);
            ui.b.scissor((int) x, (int) (y + ui.s(30)), (int) w, (int) (h - ui.s(34)));
            if (decide && ui.button("ms" + m.id, rx + rw - ui.s(108), cy + rowH - ui.s(44), ui.s(98), ui.s(30), "DECIDE", "flag", true)) { deciding = m.id; c.sfx("ui_click", 0.5f); }
            cy += rowH;
        }
        ui.b.noScissor();
    }

    /** The decision: who is given the proof. Each choice says who wants it, what they pay and what follows. */
    private void decision(Ui ui, Profile p) {
        absentialis.gameplay.missions.Story.Mission m = absentialis.gameplay.missions.Story.mission(deciding);
        if (m == null || p.missions.stage(m.id) != absentialis.gameplay.missions.Missions.Stage.DECISION) { deciding = ""; return; }
        boolean was = ui.block;
        ui.block = false;
        ui.b.rect(0, 0, ui.w, ui.h, 0xE0000000);
        float w = Math.min(ui.s(900), ui.w - ui.s(40)), h = Math.min(ui.s(600), ui.h - ui.s(40)), x = (ui.w - w) / 2, y = (ui.h - h) / 2;
        float cy = ui.window(m.id + "  " + m.title + "  -  who is told?", "flag", x, y, w, h);
        cy += ui.wrap(m.discovery, x + ui.s(20), cy, w - ui.s(40), 13, Ui.TXT_HI) + ui.s(6);
        ui.text(m.question, x + ui.s(20), cy, 12, Ui.AMBER);
        cy += ui.s(26);
        long xp = Math.round(absentialis.gameplay.missions.Story.storyXp() * (1 + (p.missions.of(m.id).corroborated() ? absentialis.gameplay.missions.Story.corroborationBonus : 0)));
        ui.text("Whatever you decide: +" + String.format("%,d", xp) + " XP, once." + (p.missions.of(m.id).corroborated() ? "  (Two sources: corroborated.)" : ""), x + ui.s(20), cy, 11, Ui.TXT_DIM);
        cy += ui.s(24);
        float rowH = (y + h - cy - ui.s(64)) / absentialis.gameplay.missions.Story.RESOLUTIONS.size();
        for (absentialis.gameplay.missions.Story.Resolution r : absentialis.gameplay.missions.Story.RESOLUTIONS.values()) {
            boolean hov = ui.hover(x + ui.s(16), cy, w - ui.s(32), rowH - ui.s(6));
            int col = absentialis.gameplay.Alliance.def(r.key()) != null ? absentialis.gameplay.Alliance.def(r.key()).colour() : 0xFF8A8A8A;
            ui.b.rect(x + ui.s(16), cy, w - ui.s(32), rowH - ui.s(6), hov ? 0xFF1E2126 : 0xFF14161A);
            ui.b.rect(x + ui.s(16), cy, ui.s(4), rowH - ui.s(6), col);
            ui.text(r.label(), x + ui.s(30), cy + ui.s(6), 15, Ui.TXT_HI);
            StringBuilder fx = new StringBuilder(r.cash() > 0 ? "+" + absentialis.Version.coins(r.cash()) : "");
            for (Map.Entry<String, Float> e : r.rep().entrySet()) fx.append(fx.length() > 0 ? "   " : "").append(absentialis.gameplay.Alliance.name(e.getKey())).append(e.getValue() > 0 ? " +" : " ").append(Math.round(e.getValue()));
            ui.textR(fx.toString(), x + w - ui.s(28), cy + ui.s(8), 11, Ui.GREEN);
            String why = m.readings.getOrDefault(r.key(), "");
            ui.text((why.isEmpty() ? "" : why + "  ") + m.outcomes.getOrDefault(r.key(), ""), x + ui.s(30), cy + ui.s(28), 11, Ui.TXT_DIM);
            if (hov && ui.click && !ui.consumed) {
                ui.consumed = true;
                long paid = p.missions.resolve(m.id, r.key(), p.zone.day);
                if (paid > 0) {
                    int before = p.prog.rank();
                    p.prog.addXp(paid);
                    p.scrip += r.cash();
                    for (Map.Entry<String, Float> e : r.rep().entrySet()) p.addRep(e.getKey(), e.getValue());
                    // the papers themselves go with the decision, unless it was to keep them
                    if (!r.key().equals("keep")) for (String ev : m.evidence) { for (ItemInstance it : new ArrayList<>(p.stash.items)) if (it.defId.equals(ev)) InventoryService.consume(it, it.qty); for (ItemInstance it : new ArrayList<>(p.loadout.all())) if (it.defId.equals(ev)) InventoryService.consume(it, it.qty); }
                    p.missions.drain();
                    p.stat("missions_closed", 1);
                    p.applyCapacity();
                    SaveSystem.save(p);
                    c.inv.say(m.title + " closed: +" + String.format("%,d", paid) + " XP" + (r.cash() > 0 ? ", +" + absentialis.Version.coins(r.cash()) : ""), Ui.GREEN);
                    c.sfx("contract_complete", 0.7f);
                    if (p.prog.rank() > before) { c.sfx("rank_up", 0.7f); unlocks.show(before, p.prog.rank()); }
                }
                deciding = "";
            }
            cy += rowH;
        }
        if (ui.button("msback", x + w - ui.s(160), y + h - ui.s(52), ui.s(140), ui.s(36), "NOT YET", "cross", true)) { deciding = ""; c.sfx("ui_back", 0.5f); }
        ui.block = was;
    }

    private void contractCard(Ui ui, Contracts.Contract k, float x, float y, float w, boolean active) {
        ui.nine("slot", x, y, w, ui.s(96), 0xFFFFFFFF);
        ui.text(k.title, x + ui.s(12), y + ui.s(8), 15, Ui.TXT_HI);
        ui.textR(ZoneState.cap(k.giver), x + w - ui.s(12), y + ui.s(8), 12, Ui.AMBER);
        ui.text(k.desc, x + ui.s(12), y + ui.s(30), 12, Ui.TXT);
        ui.text("+" + absentialis.Version.coins(k.scrip) + "   +" + k.xp + " XP   +" + (int) k.rep + " rep", x + ui.s(12), y + ui.s(50), 12, Ui.GREEN);
        if (active) {
            ui.bar(x + ui.s(12), y + ui.s(74), w - ui.s(150), ui.s(10), k.have / (float) Math.max(1, k.need), 0xFFE2A034);
            ui.text(k.have + "/" + k.need, x + w - ui.s(132), y + ui.s(70), 12, Ui.TXT);
            if (ui.button("ab" + k.id, x + w - ui.s(90), y + ui.s(62), ui.s(80), ui.s(28), "DROP", null, true)) p().contracts.abandon(k.id);
        } else if (ui.button("ac" + k.id, x + w - ui.s(110), y + ui.s(58), ui.s(100), ui.s(30), "ACCEPT", "check", p().contracts.active.size() < Contracts.MAX_ACTIVE)) {
            String e = p().contracts.accept(k.id);
            if (e != null) c.inv.say(e, Ui.RED); else c.sfx("mission_accepted", 0.6f);
        }
    }

    private void facilityCraft(Ui ui, float x, float y, float w) {
        Profile p = p();
        // 0.7: like the field list, only what can be made now - the facility is built far enough and the materials are in the case or the stash
        ui.text("FACILITY CRAFTING  -  what can be made now (case and stash)", x, y, 13, Ui.TXT_HI);
        float ry = y + ui.s(24);
        int shown = 0;
        for (Content.Recipe r : Content.RECIPES) {
            if (r.where.equals("field") || r.where.equals("treasure")) continue;
            if (p.level(r.facility()) < r.level() || !LoadoutPanel.canCraft(p, r, true)) continue;
            if (ry > ui.h - ui.s(60)) break;
            shown++;
            StringBuilder in = new StringBuilder();
            for (Object[] i : r.inputs) in.append(in.length() > 0 ? " + " : "").append(i[1]).append("x ").append(Content.def((String) i[0]).name);
            ui.text(r.outQty + "x " + Content.def(r.outId).name, x, ry, 12, Ui.TXT_HI);
            ui.text(in.toString(), x, ry + ui.s(15), 10, Ui.TXT_DIM);
            if (ui.button("fc" + r.id, x + w - ui.s(90), ry, ui.s(86), ui.s(26), "CRAFT", null, true)) { String e = LoadoutPanel.craft(p, r, true); c.inv.say(e == null ? "Crafted " + r.name : e, e == null ? Ui.GREEN : Ui.RED); c.sfx(e != null ? "ui_error" : r.outId.startsWith("ammo.") ? "ammo_crafted" : "ui_buy", 0.6f); }
            ry += ui.s(38);
        }
        if (shown == 0) ui.text("Nothing can be made here yet: build the facility further, or bring the materials.", x, ry, 12, Ui.TXT_DIM);
    }

    /** Scripted visual verification: open a shop department, or the operation briefing. */
    public void debugShow(int shopDept, boolean briefing) { if (shopDept >= 0) { tab = TAB_SHOP; shop.show(shopDept); } deployOpen = briefing; deployTab = 0; }

    /** Scripted verification: buys one of an item through the shop's own purchase path. */
    public void debugBuy(String itemId) { shop.debugBuy(p(), itemId); }

    /** Scripted visual verification: the SQUAD tab's recruiting board. */
    public void debugRecruiting() { tab = TAB_SHOP; shop.show(ShopPanel.RECRUITS); }

    /** Scripted visual verification: a page of the deployment screen (1 leaderboard, 2 rivalries). */
    public void debugDeployTab(int page) { deployOpen = true; deployTab = page; }

    // ================================================================== DEPLOY
    /**
     * The insertion cards as last laid out: {x, y, w, h, textX, textWidth, widestLine, textBottomOffset}. The
     * layout gate reads these to prove no line is wider than its card and no text block taller.
     */
    public final List<float[]> deployCards = new java.util.ArrayList<>();

    private void deployModal(Ui ui) {
        Profile p = p();
        if (c.world == null) { deployOpen = false; return; }      // the Zone is still being built: there is nothing to brief on yet
        ui.b.rect(0, 0, ui.w, ui.h, 0xD8000000);
        List<MapData.Point> ins = c.world.map.insertions;
        if (insertion >= ins.size()) insertion = 0;
        // the briefing keeps its size; the leaderboard and the rivalries are tables and take the room there is
        float w = Math.min(ui.s(deployTab == 0 ? 1040 : 1320), ui.w - ui.s(40)), h = Math.min(ui.s(deployTab == 0 ? 600 : 760), ui.h - ui.s(40)), x = (ui.w - w) / 2, y = (ui.h - h) / 2;
        float pad = ui.s(24);
        ui.shadow(x, y, w, h, 0xA0);
        ui.b.rect(x, y, w, h, 0xFF0E1011);
        ui.b.outline(x, y, w, h, 2, 0xFF8A6420);
        // ---- title strip
        ui.b.rect(x + 2, y + 2, w - 4, ui.s(58), 0xFF16181B);
        ui.b.rect(x + 2, y + ui.s(58), w - 4, ui.s(2), Ui.AMBER);
        ui.glyph("flag", x + pad, y + ui.s(16), Math.max(2, ui.s(2)), 0xFFFFFFFF);
        String[] pages = {"BRIEFING", "LEADERBOARD", "RIVALRIES"};
        float tbw = ui.s(142), tbx = x + w - pad - pages.length * (tbw + ui.s(6)) + ui.s(6);
        ui.text(deployTab == 0 ? "OPERATION BRIEFING" : deployTab == 1 ? "LEADERBOARD" : "GROUP RIVALRIES", x + pad + ui.s(36), y + ui.s(10), 24, Ui.TXT_HI);
        absentialis.ui.Layout.fit(ui, deployTab == 0 ? "Choose where you go in and at what hour. Stay as long as you like - there is no clock on you. Everything you carry is at risk." : deployTab == 1 ? "Who is alive in the Zone, and what they have done." : "Every group, who leads it, what it remembers of you and what it will do about it.",
                x + pad + ui.s(38), y + ui.s(38), tbx - (x + pad + ui.s(38)) - ui.s(12), 11, 8, Ui.TXT_DIM);
        for (int i = 0; i < pages.length; i++) if (ui.tab("deptab" + i, tbx + i * (tbw + ui.s(6)), y + ui.s(14), tbw, ui.s(34), pages[i], null, deployTab == i) && deployTab != i) { deployTab = i; c.sfx("ui_tab", 0.5f); }
        float top = y + ui.s(78), bottom = y + h - ui.s(78);
        if (deployTab != 0) {
            float bh = y + h - pad - ui.s(52) - top;
            if (deployTab == 1) ops.leaderboard(ui, p, x + pad, top, w - pad * 2, bh);
            else ops.rivalries(ui, p, x + pad, top, w - pad * 2, bh);
            if (ui.button("dclose", x + w - pad - ui.s(180), y + h - pad - ui.s(40), ui.s(180), ui.s(40), "CLOSE", "cross", true) || c.in.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) deployOpen = false;
            if (ui.button("dbrief", x + pad, y + h - pad - ui.s(40), ui.s(220), ui.s(40), "BACK TO BRIEFING", "flag", true)) deployTab = 0;
            return;
        }
        // 0.6: you deploy from ground your side holds. A point you may not use is shown, and says what would open it.
        boolean[] open = new boolean[ins.size()];
        int firstOpen = -1;
        for (int i = 0; i < ins.size(); i++) { open[i] = absentialis.gameplay.Alliance.mayDeploy(p, ins.get(i).district); if (open[i] && firstOpen < 0) firstOpen = i; }
        if (firstOpen < 0) { java.util.Arrays.fill(open, true); firstOpen = 0; }       // a world with no ground of yours at all: never a dead end
        if (!open[insertion]) insertion = firstOpen;

        // ---- left: insertion points, one card each
        float lw = w * 0.56f - pad * 1.5f, lx = x + pad;
        ui.text("1   INSERTION POINT", lx, top, 12, Ui.AMBER);
        // Cards are laid out from what they have to hold, not from fixed offsets: as many rows as fit at a readable
        // height, more columns when there are more points than rows, every line measured and fitted to its card and
        // the card clipped - so no name, however long, at any resolution or UI scale, leaves its panel (brief 11.5).
        float gap = ui.s(8), listTop = top + ui.s(24), avail = bottom - listTop;
        int rowsMax = Math.max(1, (int) Math.floor((avail + gap) / (ui.s(60) + gap)));
        int cols = Math.max(1, Math.min(3, (int) Math.ceil(ins.size() / (float) rowsMax)));
        int rows = Math.max(1, (int) Math.ceil(ins.size() / (float) cols));
        float cardH = Math.max(ui.s(34), Math.min(ui.s(74), (avail - gap * (rows - 1)) / rows));
        float cardW = (lw - gap * (cols - 1)) / cols;
        boolean roomy = cardH >= ui.s(56);
        float nameSize = roomy ? 15 : 12, subSize = roomy ? 10 : 9, inPad = ui.s(roomy ? 9 : 5);
        deployCards.clear();
        for (int i = 0; i < ins.size(); i++) {
            MapData.Point pt = ins.get(i);
            String ctl = p.zone.controller(pt.district);
            float inf = p.zone.infected[pt.district.ordinal()];
            float cx0 = lx + (i / rows) * (cardW + gap), cy = listTop + (i % rows) * (cardH + gap);
            boolean sel = insertion == i, hov = ui.hover(cx0, cy, cardW, cardH);
            float k = ui.ease("depins" + i, (hov || sel) && open[i]);
            ui.b.rect(cx0, cy, cardW, cardH, sel ? 0xFF2A2416 : open[i] ? Ui.mix(0xFF14161A, 0xFF1E2024, k) : 0xFF0F1012);
            ui.b.rect(cx0, cy, ui.s(4), cardH, sel ? Ui.AMBER : open[i] ? 0xFF4A4436 : 0xFF5A2A26);
            if (sel) ui.b.outline(cx0, cy, cardW, cardH, 1, 0xFF8A6420);
            ui.b.scissor((int) Math.floor(cx0), (int) Math.floor(cy), (int) Math.ceil(cardW), (int) Math.ceil(cardH));
            float tx = cx0 + ui.s(14), iw = cardW - ui.s(14) - ui.s(12);
            int col = inf > 0.66f ? Ui.RED : inf > 0.33f ? Ui.AMBER : Ui.GREEN;
            String pct = "INFECTED " + Math.round(inf * 100) + "%";
            float pw = ui.tw(pct, subSize);
            // the name takes the full width; the district line shares its row with the infection figure when both fit
            float used = absentialis.ui.Layout.fit(ui, pt.name, tx, cy + inPad, iw, nameSize, 10, !open[i] ? Ui.TXT_DIM : sel ? Ui.TXT_HI : Ui.TXT);
            float subY = cy + inPad + absentialis.ui.Layout.lineHeight(ui, used);
            String sub = pt.district.display + "  -  " + (!open[i] ? "LOCKED: " + (ctl == null ? "contested" : "held by " + absentialis.gameplay.Alliance.name(ctl)) : ctl == null ? "contested ground" : "held by " + absentialis.gameplay.Alliance.name(ctl));
            boolean share = iw - pw - ui.s(12) >= ui.s(90);
            absentialis.ui.Layout.fit(ui, sub, tx, subY, share ? iw - pw - ui.s(12) : iw, subSize, 8, ctl == null ? Ui.TXT_DIM : Ui.TXT);
            if (share) ui.textR(pct, tx + iw, subY, subSize, col);
            // infection pressure: the one number that changes how the first minute goes - a bar along the card's foot
            ui.bar(tx, cy + cardH - ui.s(8), iw, ui.s(4), inf, col);
            ui.b.noScissor();
            deployCards.add(new float[]{cx0, cy, cardW, cardH, tx, iw, Math.max(ui.tw(absentialis.ui.Layout.elide(ui, pt.name, iw, used), used), ui.tw(absentialis.ui.Layout.elide(ui, sub, share ? iw - pw - ui.s(12) : iw, absentialis.ui.Layout.fitSize(ui, sub, share ? iw - pw - ui.s(12) : iw, subSize, 8)), absentialis.ui.Layout.fitSize(ui, sub, share ? iw - pw - ui.s(12) : iw, subSize, 8))), subY + absentialis.ui.Layout.lineHeight(ui, subSize) - cy});
            if (hov && !open[i]) ui.tip(pt.name + " is not yours to deploy from.", absentialis.gameplay.Alliance.howToOpen(p, pt.district));
            if (hov && ui.click && !ui.consumed) { ui.consumed = true; if (open[i]) { insertion = i; if (ui.sound != null) ui.sound.accept("ui_tab"); } else { c.inv.say("Your allegiance only opens ground the " + absentialis.gameplay.Alliance.name(p.alliance) + " hold", Ui.RED); c.sfx("ui_invalid", 0.5f); } }
        }

        // ---- right: length, vehicle, element, readiness
        float rx = x + w * 0.56f + pad * 0.5f, rw = x + w - pad - rx, ry = top;
        ui.b.rect(rx - pad * 0.5f, top, 1, bottom - top, 0xFF2E2A22);
        ui.text("2   TIME OF DAY", rx, ry, 12, Ui.AMBER);
        ry += ui.s(24);
        float sw = (rw - ui.s(12)) / TIMES.length;
        for (int i = 0; i < TIMES.length; i++) if (ui.tab("tod" + i, rx + i * (sw + ui.s(6)), ry, sw, ui.s(34), TIMES[i], null, timeOfDay == i)) timeOfDay = i;
        ry += ui.s(42);
        ui.text(timeOfDay == 0 ? "08:00. Good light, long shadows. The day is ahead of you." : timeOfDay == 1 ? "14:00. Full daylight, and dusk a few hours off." : "00:00. Dark. A torch helps you see and shows everyone where you are.", rx, ry, 11, Ui.TXT_DIM);
        ry += ui.s(16);
        ui.text("This is when you go in, not a limit: the clock runs on from there.", rx, ry, 11, Ui.TXT_DIM);
        ry += ui.s(26);

        ui.text("3   VEHICLE", rx, ry, 12, Ui.AMBER);
        ry += ui.s(24);
        List<String> rides = new java.util.ArrayList<>();
        rides.add("");
        for (String g : p.garage) if (absentialis.vehicle.CarHandlingData.get(g) != null) rides.add(g);
        int ri = Math.max(0, rides.indexOf(p.deployVehicle));
        boolean many = rides.size() > 1;
        if (ui.button("depvl", rx, ry, ui.s(34), ui.s(34), "<", null, many)) { p.deployVehicle = rides.get((ri + rides.size() - 1) % rides.size()); c.sfx("ui_tab", 0.5f); }
        if (ui.button("depvr", rx + rw - ui.s(34), ry, ui.s(34), ui.s(34), ">", null, many)) { p.deployVehicle = rides.get((ri + 1) % rides.size()); c.sfx("ui_tab", 0.5f); }
        ui.b.rect(rx + ui.s(40), ry, rw - ui.s(80), ui.s(34), 0xFF14161A);
        absentialis.vehicle.CarHandlingData ride = p.deployVehicle.isEmpty() ? null : absentialis.vehicle.CarHandlingData.get(p.deployVehicle);
        ui.textC(ride == null ? "ON FOOT" : ride.name.toUpperCase(), rx + rw / 2, ry + ui.s(8), 15, ride == null ? Ui.TXT : Ui.TXT_HI);
        ry += ui.s(42);
        ui.text(ride != null ? ride.family + " - parked by the insertion point. Lost if you do not come back." : many ? "Use the arrows to bring a vehicle from your garage." : "No vehicles owned. The SHOP sells them under VEHICLES.", rx, ry, 11, Ui.TXT_DIM);
        ry += ui.s(30);

        ui.text("4   ELEMENT", rx, ry, 12, Ui.AMBER);
        ry += ui.s(22);
        List<absentialis.gameplay.squad.Roster.Mate> act = p.squad.active();
        if (act.isEmpty()) { ui.text(SquadPanel.unlocked(p) ? "You are going in alone. Squadmates are hired in the SHOP and assigned on the SQUAD tab." : "You are going in alone. The first squadmate slot opens at Rank " + Progression.SQUAD_FIRST_RANK + ".", rx, ry, 11, Ui.TXT_DIM); ry += ui.s(20); }
        else {
            float nx = rx;
            for (absentialis.gameplay.squad.Roster.Mate m : act) {
                String nm = absentialis.gameplay.squad.Roster.shortName(m.name);
                float tw = ui.tw(nm, 12) + ui.s(26);
                if (nx + tw > rx + rw) { nx = rx; ry += ui.s(26); }
                ui.b.rect(nx, ry, tw - ui.s(6), ui.s(22), 0xFF16181B);
                ui.b.rect(nx, ry, ui.s(4), ui.s(22), "red".equals(m.team) ? Ui.RED : Ui.BLUE);
                ui.text(nm, nx + ui.s(10), ry + ui.s(4), 12, Ui.TXT);
                nx += tw;
            }
            ry += ui.s(30);
        }

        // readiness: the things people forget, said once, in one place
        boolean armed = p.loadout.slot("primary") != null || p.loadout.slot("sidearm") != null;
        int bandage = 0, antiviral = 0;
        for (absentialis.gameplay.items.ItemInstance it : p.loadout.all()) { if (it.defId.equals("med.antiviral")) antiviral += it.qty; else if (it.def().medical != null) bandage += it.qty; }
        ry += ui.s(4);
        ui.text("READINESS", rx, ry, 12, Ui.AMBER);
        ry += ui.s(22);
        ry = ready(ui, rx, ry, armed, armed ? "Firearm equipped" : "No firearm equipped");
        ry = ready(ui, rx, ry, p.health >= 50, p.health >= 50 ? "Fit to deploy (" + Math.round(p.health) + "%)" : "Deploying injured (" + Math.round(p.health) + "%)");
        ry = ready(ui, rx, ry, bandage > 0, bandage > 0 ? "Medical supplies packed" : "No medical supplies packed");
        ready(ui, rx, ry, antiviral > 0, antiviral > 0 ? "Antiviral Medicine x" + antiviral : "No Antiviral Medicine - a bite will turn you");

        // ---- footer
        ui.b.rect(x + 2, bottom + ui.s(14), w - 4, 1, 0xFF2E2A22);
        String sum = ins.get(insertion).name + "   -   " + TIMES[timeOfDay].toLowerCase() + "   -   " + (act.isEmpty() ? "solo" : "you + " + act.size()) + (ride == null ? "" : "   -   " + ride.name) + (p.ironman ? "   -   IRONMAN: death is permanent" : "");
        ui.text(sum, x + pad, bottom + ui.s(36), 12, p.ironman ? Ui.RED : Ui.TXT);
        float bw2 = ui.s(230);
        if (ui.button("go", x + w - pad - bw2, bottom + ui.s(24), bw2, ui.s(42), "DEPLOY", "flag", true) || c.in.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER)) {
            deployOpen = false;
            action = "deploy";
            deployInsertion = insertion;
            deployHour = TIME_HOUR[timeOfDay];
            SaveSystem.save(p);
        }
        if (ui.button("dcancel", x + w - pad - bw2 - ui.s(150), bottom + ui.s(24), ui.s(140), ui.s(42), "CANCEL", "cross", true) || c.in.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) deployOpen = false;
    }

    private float ready(Ui ui, float x, float y, boolean ok, String text) {
        ui.glyph(ok ? "check" : "cross", x, y - ui.s(2), Math.max(1, ui.s(1)), ok ? Ui.GREEN : Ui.RED);
        ui.text(text, x + ui.s(22), y, 12, ok ? Ui.TXT : Ui.RED);
        return y + ui.s(20);
    }
}
