package absentialis.scene;

import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.gameplay.progression.Progression;
import absentialis.gameplay.weapons.Attachments;
import absentialis.ui.Ui;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the last operation opened (0.7). Ranks are gained in the Zone but nothing said so until the player went
 * looking through the ladder: now, back at the hideout, everything the deployment unlocked is laid out once - each
 * thing on its own card, dealt in one after another, the weapons turning on their plinths - and stays until it is
 * dismissed. Nothing here grants anything; it reports what {@link Progression} has already done.
 */
public final class UnlockNotice {
    /** One thing that opened: what kind of thing, its name, a line about it, and (for a weapon) the item to show. */
    public record Entry(String kind, String title, String line, String itemId, int rank) {}

    private final List<Entry> entries = new ArrayList<>();
    private int fromRank, toRank;
    private float age;
    private final Map<String, ItemInstance> probes = new HashMap<>();

    public boolean open() { return !entries.isEmpty(); }
    public List<Entry> entries() { return entries; }

    /** Everything that opens on the way from one Rank to another (exclusive of the first, inclusive of the second). */
    public static List<Entry> between(int from, int to) {
        List<Entry> l = new ArrayList<>();
        for (int r = from + 1; r <= to; r++) {
            for (String id : Progression.platforms()) if (Progression.platformRank(id) == r) {
                ItemDef d = Content.def(id);
                l.add(new Entry("WEAPON", d.name, "Now stocked in the SHOP.", id, r));
            }
            for (String cat : Attachments.CATEGORIES) if (Attachments.categoryRank(cat) == r) l.add(new Entry("ATTACHMENTS", Attachments.categoryName(cat), "Fit them at the WORKBENCH as each weapon levels.", "", r));
            if (r == 4 || r == 10 || r == 15 || r == 20) { int[] d = Progression.caseDimsAt(r); l.add(new Entry("INVENTORY", "Case " + d[1] + " x " + d[0], d[0] * d[1] + " slots, kept for good.", "", r)); }
            for (int s : Progression.SQUAD_RANKS) if (s == r) l.add(new Entry("SQUAD", s == Progression.SQUAD_FIRST_RANK ? "The Squad" : "Squadmate slot", s == Progression.SQUAD_FIRST_RANK ? "Your first squadmate slot. Hire in the SHOP, under Recruits." : "One more place on the roster. Hire in the SHOP.", "", r));
            if (!Progression.rankName(r).equals(Progression.rankName(r - 1))) l.add(new Entry("PROMOTION", Progression.rankName(r), "A new title on your profile card.", "", r));
        }
        return l;
    }

    /** Queues what a climb opened. Called once, when a deployment has been resolved. */
    public void show(int from, int to) {
        if (to <= from) return;
        entries.clear();
        entries.addAll(between(from, to));
        fromRank = from; toRank = to;
        age = 0;
    }

    public void dismiss() { entries.clear(); }

    private ItemInstance probe(String id) { return probes.computeIfAbsent(id, k -> new ItemInstance(-7000 - probes.size(), id)); }

    public void draw(Ui ui, Ctx c) {
        age += ui.dt;
        ui.b.rect(0, 0, ui.w, ui.h, Ui.alpha(0xFF000000, Math.min(0.86f, age * 3)));
        int n = entries.size();
        int cols = Math.max(1, Math.min(n, n <= 3 ? n : n <= 8 ? 4 : 5));
        int rows = (n + cols - 1) / cols;
        float gap = ui.s(14), cw = Math.min(ui.s(250), (ui.w - ui.s(120) - gap * (cols - 1)) / cols), ch = Math.min(ui.s(232), (ui.h - ui.s(300) - gap * (rows - 1)) / Math.max(1, rows));
        float w = cols * cw + (cols - 1) * gap, h = rows * ch + (rows - 1) * gap;
        float x = (ui.w - w) / 2, y = (ui.h - h) / 2 + ui.s(20);
        // the banner: a rule that draws itself out from the middle, the title settling onto it
        float k = Math.min(1, age / 0.45f), e = 1 - (1 - k) * (1 - k);
        float bw = (w + ui.s(80)) * e;
        ui.b.rect(ui.w / 2f - bw / 2, y - ui.s(64), bw, ui.s(2), Ui.AMBER);
        ui.textC("UNLOCKED", ui.w / 2f, y - ui.s(118) + (1 - e) * ui.s(12), 34, Ui.alpha(Ui.TXT_HI, e));
        ui.textC(toRank > fromRank + 1 ? "Ranks " + (fromRank + 1) + " to " + toRank + "  -  " + Progression.rankName(toRank) : "Rank " + toRank + "  -  " + Progression.rankName(toRank), ui.w / 2f, y - ui.s(56), 14, Ui.alpha(Ui.AMBER, e));
        for (int i = 0; i < n; i++) {
            Entry en = entries.get(i);
            // each card is dealt a beat after the one before it: it rises into place and its edge lights
            float t = Math.max(0, Math.min(1, (age - 0.35f - i * 0.11f) / 0.32f)), s = 1 - (1 - t) * (1 - t) * (1 - t);
            if (t <= 0) continue;
            int col = i % cols, row = i / cols;
            int inRow = row == rows - 1 ? n - row * cols : cols;
            float rowX = x + (w - (inRow * cw + (inRow - 1) * gap)) / 2;
            float cx = rowX + col * (cw + gap), cy = y + row * (ch + gap) + (1 - s) * ui.s(26);
            ItemDef d = en.itemId().isEmpty() ? null : Content.def(en.itemId());
            int tier = d != null ? d.tierColor() : switch (en.kind()) { case "SQUAD" -> 0xFF4A90E2; case "INVENTORY" -> 0xFF6CC24A; case "PROMOTION" -> 0xFFE2A034; default -> 0xFFA45BD8; };
            boolean hov = ui.hover(cx, cy, cw, ch);
            float glow = 0.55f + 0.45f * (float) Math.sin(age * 2.4f + i * 0.9f);
            ui.b.rect(cx + ui.s(4), cy + ui.s(6), cw, ch, Ui.alpha(0xFF000000, 0.5f * s));
            ui.b.rect(cx, cy, cw, ch, Ui.alpha(hov ? 0xFF22252A : 0xFF17191D, s));
            ui.b.rect(cx, cy, cw, ui.s(5), Ui.alpha(tier, s));
            ui.b.rect(cx, cy + ui.s(5), cw, ui.s(30), Ui.alpha(tier, (0.10f + 0.10f * glow) * s));
            ui.b.outline(cx, cy, cw, ch, 1, Ui.alpha(Ui.mix(0xFF2E3036, tier, 0.35f + 0.4f * glow), s));
            ui.text(en.kind(), cx + ui.s(12), cy + ui.s(13), 11, Ui.alpha(tier, s));
            ui.textR("RANK " + en.rank(), cx + cw - ui.s(12), cy + ui.s(13), 11, Ui.alpha(Ui.TXT_DIM, s));
            float iy = cy + ui.s(42), ih = ch - ui.s(42) - ui.s(74);
            if (d != null && ih > ui.s(30)) {
                float vs = Math.min(cw - ui.s(40), ih);
                c.icons.draw(ui.b, probe(d.id), cx + (cw - vs * 1.6f) / 2, iy + (ih - vs) / 2, vs * 1.6f, vs, Ui.alpha(0xFFFFFFFF, s));
            } else if (ih > ui.s(30)) {
                String glyph = switch (en.kind()) { case "SQUAD" -> "radio"; case "INVENTORY" -> "inv"; case "PROMOTION" -> "chevron"; default -> "bench"; };
                int g = Math.max(3, ui.s(4));
                ui.glyph(glyph, cx + cw / 2 - 6 * g, iy + ih / 2 - 6 * g, g, Ui.alpha(0xFFFFFFFF, s));
            }
            absentialis.ui.Layout.fit(ui, en.title().toUpperCase(), cx + ui.s(12), cy + ch - ui.s(66), cw - ui.s(24), 17, 11, Ui.alpha(Ui.TXT_HI, s));
            ui.wrap(en.line(), cx + ui.s(12), cy + ch - ui.s(42), cw - ui.s(24), 11, Ui.alpha(Ui.TXT_DIM, s));
        }
        float ready = Math.max(0, Math.min(1, (age - 0.6f - n * 0.11f) / 0.3f));
        float bwid = ui.s(260), bx = ui.w / 2f - bwid / 2, by = y + h + ui.s(28);
        if (ready > 0 && (ui.button("unlock_ok", bx, by, bwid, ui.s(46), "CONTINUE", "arrow", true) || c.in.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) || c.in.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE))) { c.sfx("ui_confirm", 0.5f); dismiss(); }
    }
}
