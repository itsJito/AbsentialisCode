package absentialis.scene;

import absentialis.entity.Actor;
import absentialis.gameplay.inventory.Container;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.inventory.Loadout;
import absentialis.gameplay.items.ItemInstance;
import absentialis.ui.Ui;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Trading in the Zone, face to face (0.6).
 *
 * With a squadmate it is simply passing kit across: mark what goes each way and it changes hands. With anyone else
 * who is not shooting at you it is barter - there is no cash out here, only goods. You mark what you would take
 * from what they have on them and what you would give from your case; they weigh the two, and how they weigh them
 * depends on what their group thinks of you (a group that favours you asks less and gives more for the same things).
 * The deal goes through when what you offer is worth, to them, at least what you are asking for.
 *
 * What they carry is what they carry: goods you take are no longer on them, and goods you give are.
 */
public final class BarterPanel {
    public boolean open;
    public Actor with;
    public boolean squad;
    private Raid raid;
    private Loadout lo;
    private Container theirs;
    private final Set<ItemInstance> take = new LinkedHashSet<>(), give = new LinkedHashSet<>();
    private float scrollA, scrollB, factor = 1;
    private String note = "";
    private int noteCol = Ui.TXT_DIM;
    /** Deals struck and items that changed hands this raid (gates, and the debrief could show them). */
    public int deals, itemsTraded;

    /** What a stranger gives for goods compared with what they ask for their own: nobody trades at par. */
    public static final float SPREAD = 0.8f;

    public void open(Raid raid, Loadout lo, Actor with) {
        this.raid = raid; this.lo = lo; this.with = with;
        squad = with.mate();
        theirs = raid.wares(with);
        factor = squad ? 1 : raid.rivalry == null ? 1 : raid.rivalry.priceFactor(with);
        take.clear(); give.clear();
        scrollA = scrollB = 0;
        note = squad ? "Mark what changes hands, then pass it across." : "Mark what you want and what you offer for it.";
        noteCol = Ui.TXT_DIM;
        open = true;
    }

    public void close() { open = false; with = null; take.clear(); give.clear(); }

    /** The other party has gone, died, or turned on you: there is nobody to trade with. */
    public boolean broken() {
        return !open || with == null || with.dead || with.downed() || !squad && with.hostileToPlayer || with.dist(raid.player) > 4.2f || raid.player.dead;
    }

    private static int worth(ItemInstance it) { return Math.max(1, it.def().value) * Math.max(1, it.qty); }

    /** What they ask for the things marked on their side. */
    public int asking() { float s = 0; for (ItemInstance i : take) s += worth(i) * factor; return Math.round(s); }
    /** What they will allow for the things marked on yours. */
    public int offering() { float s = 0; for (ItemInstance i : give) s += worth(i) * (squad ? 1 : SPREAD / Math.max(0.5f, factor)); return Math.round(s); }

    public boolean acceptable() { return (!take.isEmpty() || !give.isEmpty()) && (squad || offering() >= asking()); }

    public void toggleTake(ItemInstance it) { if (!take.remove(it)) take.add(it); }
    public void toggleGive(ItemInstance it) { if (!give.remove(it)) give.add(it); }
    public List<ItemInstance> theirGoods() { return theirs == null ? List.of() : theirs.items; }
    public List<ItemInstance> yourGoods() { return lo.caseGrid.items; }

    /**
     * Strikes the deal. Everything marked changes hands or nothing does: if your case cannot hold what you are
     * taking, the deal is off and nothing has moved.
     */
    public boolean deal() {
        if (!acceptable()) { say(squad ? "Nothing marked." : "They want more for that.", Ui.RED); return false; }
        List<ItemInstance> out = new ArrayList<>(give), in = new ArrayList<>(take), moved = new ArrayList<>();
        // your side leaves first: it makes the room theirs will need
        int[][] was = new int[out.size()][3];
        for (int i = 0; i < out.size(); i++) { ItemInstance it = out.get(i); was[i][0] = it.x; was[i][1] = it.y; was[i][2] = it.rotated ? 1 : 0; InventoryService.release(it); }
        boolean ok = true;
        for (ItemInstance it : in) {
            InventoryService.release(it);
            if (InventoryService.add(it, lo.caseGrid).ok()) moved.add(it); else { ok = false; InventoryService.add(it, theirs); break; }
        }
        if (!ok) {
            for (ItemInstance it : moved) { InventoryService.release(it); InventoryService.add(it, theirs); }
            for (int i = 0; i < out.size(); i++) { ItemInstance it = out.get(i); if (!lo.caseGrid.fits(it, was[i][0], was[i][1], was[i][2] == 1) || !InventoryService.move(it, lo.caseGrid, was[i][0], was[i][1], was[i][2] == 1).ok()) InventoryService.add(it, lo.caseGrid); }
            say("No room in your case for that.", Ui.RED);
            return false;
        }
        for (ItemInstance it : out) if (!InventoryService.add(it, theirs).ok()) raid.dropItem(it, with.x, with.y + 0.3f, with.z);
        deals++; itemsTraded += out.size() + in.size();
        if (!squad && raid.rivalry != null) raid.rivalry.onTrade(with);
        if (!squad && !in.isEmpty()) raid.say(with, raid.rng.chance(0.5) ? "Fair enough." : "Done. Watch yourself out there.", "bark_idle");
        take.clear(); give.clear();
        say(squad ? "Passed across." : "Deal.", Ui.GREEN);
        return true;
    }

    private void say(String s, int col) { note = s; noteCol = col; }

    public void draw(Ui ui) {
        if (!open || with == null) return;
        float w = Math.min(ui.w - ui.s(80), ui.s(980)), h = Math.min(ui.h - ui.s(120), ui.s(560)), x = (ui.w - w) / 2, y = (ui.h - h) / 2;
        String who = with.name == null || with.name.isEmpty() ? Raid.displayName(with) : with.name;
        float cy = ui.window((squad ? "Trade kit  -  " : "Barter  -  ") + who, "inv", x, y, w, h);
        float pad = ui.s(16), colW = (w - pad * 3) / 2, lx = x + pad, rx = lx + colW + pad, listH = h - (cy - y) - ui.s(128);
        ui.text(squad ? "THEIR PACK" : "WHAT THEY HAVE", lx, cy, 13, Ui.AMBER);
        ui.text("YOUR CASE", rx, cy, 13, Ui.AMBER);
        if (!squad) ui.textR(factor < 0.95f ? "they favour you: easier terms" : factor > 1.08f ? "they do not trust you: harder terms" : "ordinary terms", x + w - pad, cy, 12, factor < 0.95f ? Ui.GREEN : factor > 1.08f ? Ui.RED : Ui.TXT_DIM);
        cy += ui.s(22);
        scrollA = list(ui, "bt_a", theirGoods(), take, lx, cy, colW, listH, scrollA, true);
        scrollB = list(ui, "bt_b", yourGoods(), give, rx, cy, colW, listH, scrollB, false);
        float by = cy + listH + ui.s(12);
        if (squad) {
            ui.text(take.size() + " to you, " + give.size() + " to them", lx, by + ui.s(2), 14, Ui.TXT);
        } else {
            int ask = asking(), off = offering();
            ui.text("They ask  " + absentialis.Version.coins(ask) + "  in goods", lx, by, 14, Ui.TXT);
            ui.text("They allow  " + absentialis.Version.coins(off) + "  for yours", rx, by, 14, off >= ask && ask + off > 0 ? Ui.GREEN : Ui.TXT);
            float bw = w - pad * 2;
            ui.bar(lx, by + ui.s(24), bw, ui.s(6), ask <= 0 ? (off > 0 ? 1 : 0) : Math.min(1, off / (float) ask), off >= ask && ask + off > 0 ? Ui.GREEN : Ui.AMBER);
        }
        ui.text(note, lx, by + ui.s(40), 13, noteCol);
        float bw = ui.s(190), bh = ui.s(38), byb = y + h - bh - ui.s(14);
        if (ui.button("bt_deal", x + w - pad - bw * 2 - ui.s(10), byb, bw, bh, squad ? "PASS ACROSS" : "DEAL", "check", acceptable())) deal();
        if (ui.button("bt_close", x + w - pad - bw, byb, bw, bh, "CLOSE", "cross", true)) close();
    }

    private float list(Ui ui, String id, List<ItemInstance> items, Set<ItemInstance> marked, float x, float y, float w, float h, float scroll, boolean theirSide) {
        ui.panelDark(x, y, w, h);
        float rowH = ui.s(26), inner = h - ui.s(8), total = items.size() * rowH;
        if (ui.hover(x, y, w, h)) scroll -= (float) ui.in.scroll * rowH * 2;
        scroll = Math.max(0, Math.min(Math.max(0, total - inner), scroll));
        if (items.isEmpty()) ui.text(theirSide ? "Nothing to spare." : "Your case is empty.", x + ui.s(12), y + ui.s(12), 13, Ui.TXT_DIM);
        float ry = y + ui.s(4) - scroll;
        for (ItemInstance it : new ArrayList<>(items)) {
            if (ry + rowH > y + ui.s(2) && ry < y + h - ui.s(4) - rowH * 0.5f) {
                boolean on = marked.contains(it), hov = ui.hover(x + ui.s(4), ry, w - ui.s(8), rowH - ui.s(2));
                if (on || hov) ui.b.rect(ui.px(x + ui.s(4)), ui.px(ry), ui.px(w - ui.s(8)), ui.px(rowH - ui.s(2)), on ? 0x664A7A3A : 0x33FFFFFF);
                if (on) ui.b.rect(ui.px(x + ui.s(4)), ui.px(ry), ui.s(3), ui.px(rowH - ui.s(2)), Ui.GREEN);
                String name = it.name() + (it.qty > 1 ? "  x" + it.qty : "");
                ui.text(name, x + ui.s(14), ry + ui.s(5), 13, on ? Ui.TXT_HI : Ui.TXT);
                float v = worth(it) * (squad ? 1 : theirSide ? factor : SPREAD / Math.max(0.5f, factor));
                ui.textR(absentialis.Version.coins(Math.round(v)), x + w - ui.s(14), ry + ui.s(5), 12, Ui.TXT_DIM);
                if (hov && ui.click && !ui.consumed) { if (!marked.remove(it)) marked.add(it); ui.consumed = true; if (ui.sound != null) ui.sound.accept("ui_click"); }
            }
            ry += rowH;
        }
        marked.retainAll(items);
        return scroll;
    }
}
