package absentialis.scene;

import absentialis.gameplay.inventory.Loadout;
import absentialis.gameplay.items.ItemInstance;
import absentialis.ui.Ui;

/**
 * Hold-Tab radial (addendum P1): 8 slots - primary, sidearm, melee, throwable and four quick-use
 * consumables. Each slot shows condition and ammo; the hovered slot spins in the centre as an
 * inspect view. While open, single-player time runs at 30%. Releasing Tab commits the choice.
 */
public final class WeaponWheel {
    public static final String[] LABELS = {"PRIMARY", "SIDEARM", "MELEE", "THROW", "QUICK 1", "QUICK 2", "QUICK 3", "QUICK 4"};
    private final Ctx c;
    public boolean open;
    public int selected = -1;
    private float anim, cursorX, cursorY, spin;

    public WeaponWheel(Ctx c) { this.c = c; }

    public void openWheel() { open = true; selected = -1; cursorX = cursorY = 0; }

    /** Items for each of the 8 slots (null = empty). */
    public ItemInstance[] slots(Loadout l) {
        ItemInstance[] s = new ItemInstance[8];
        s[0] = l.slot("primary");
        s[1] = l.slot("sidearm");
        s[2] = l.slot("melee");
        for (ItemInstance i : l.caseGrid.items) if (i.def().throwable != null) { s[3] = i; break; }
        // quick slots: explicit bindings, else auto-filled with medical/food in the case
        java.util.Set<Long> used = new java.util.HashSet<>();
        for (int k = 0; k < 4; k++) {
            long u = l.quick[k];
            ItemInstance b = u == 0 ? null : l.findUid(u);
            if (b != null && b.container == l.caseGrid) { s[4 + k] = b; used.add(b.uid); }
        }
        for (int k = 0; k < 4; k++) if (s[4 + k] == null) for (ItemInstance i : l.caseGrid.items)
            if ((i.def().medical != null || i.def().food != null) && !used.contains(i.uid)) { s[4 + k] = i; used.add(i.uid); break; }
        return s;
    }

    public void update(float dx, float dy) {
        cursorX += dx; cursorY += dy;
        float len = (float) Math.sqrt(cursorX * cursorX + cursorY * cursorY);
        if (len > 160) { cursorX *= 160 / len; cursorY *= 160 / len; }
        if (len > 30) {
            float ang = (float) Math.atan2(cursorX, -cursorY); // 0 = up, clockwise
            selected = Math.floorMod(Math.round(ang / (float) (Math.PI * 2 / 8)), 8);
        }
    }

    public void draw(Ui ui, Loadout l, float dt) {
        anim += ((open ? 1 : 0) - anim) * Math.min(1, dt / Ui.EASE);
        if (anim < 0.01f) return;
        spin += dt * 1.6f;
        ItemInstance[] s = slots(l);
        float cx = ui.w / 2f, cy = ui.h / 2f, R = ui.s(210) * (0.85f + 0.15f * anim);
        ui.b.rect(0, 0, ui.w, ui.h, Ui.alpha(0x90000000, anim));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI * 2 / 8;
            float sx = cx + (float) Math.sin(a) * R, sy = cy - (float) Math.cos(a) * R;
            float bw = ui.s(150), bh = ui.s(96);
            boolean sel = i == selected;
            float k = ui.ease("wheel" + i, sel);
            float grow = 1 + k * 0.08f;
            float x = sx - bw * grow / 2, y = sy - bh * grow / 2;
            ui.shadow(x, y, bw * grow, bh * grow, (int) (0x60 * anim));
            ui.nine(sel ? "btn_hover" : "panel_dark", x, y, bw * grow, bh * grow, Ui.alpha(0xFFFFFFFF, anim));
            ui.text(LABELS[i], x + ui.s(8), y + ui.s(6), 11, Ui.alpha(sel ? Ui.AMBER : Ui.TXT_DIM, anim));
            ItemInstance it = s[i];
            if (it == null) { ui.textC("empty", sx, sy - ui.s(4), 12, Ui.alpha(0xFF5A5C56, anim)); continue; }
            float iw = bw * grow - ui.s(20), ih = bh * grow - ui.s(46);
            c.icons.draw(ui.b, it, x + ui.s(10), y + ui.s(22), iw, ih, Ui.alpha(0xFFFFFFFF, anim));
            String sub = it.def().name;
            if (it.def().weapon != null && !it.def().weapon.melee()) sub = it.loadedRounds() + " rds  " + it.fireModeName();
            else if (it.def().stackable() && it.qty > 1) sub = "x" + it.qty + "  " + it.def().name;
            ui.text(sub, x + ui.s(8), y + bh * grow - ui.s(22), 11, Ui.alpha(Ui.TXT, anim));
            if (it.def().weapon != null && !it.def().weapon.melee()) {
                float cond = it.condition() / 100f;
                ui.b.rect(x + ui.s(8), y + bh * grow - ui.s(6), (bw * grow - ui.s(16)) * cond, ui.s(3), Ui.alpha(cond < 0.25f ? Ui.RED : cond < 0.5f ? Ui.AMBER : Ui.GREEN, anim));
            }
        }
        // centre: inspect animation of the selection
        float cs = ui.s(150);
        ui.nine("panel", cx - cs / 2 - ui.s(6), cy - cs / 2 - ui.s(6), cs + ui.s(12), cs + ui.s(12), Ui.alpha(0xFFFFFFFF, anim));
        ItemInstance sel = selected >= 0 ? s[selected] : null;
        if (sel != null) {
            c.icons.drawInspect(ui.b, sel, cx - cs / 2, cy - cs / 2, cs, spin, 0.35f);
            ui.textC(sel.def().name, cx, cy + cs / 2 + ui.s(14), 14, Ui.alpha(Ui.TXT_HI, anim));
        } else ui.textC("TIME SLOWED", cx, cy - ui.s(8), 13, Ui.alpha(Ui.TXT_DIM, anim));
        ui.b.line(cx, cy, cx + cursorX * ui.scale * 0.6f, cy + cursorY * ui.scale * 0.6f, 2, Ui.alpha(0x80E2A034, anim));
    }
}
