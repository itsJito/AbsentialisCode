package absentialis.scene;

import static org.lwjgl.glfw.GLFW.*;

import absentialis.ui.Ui;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 1.0: a decision, put to the survivor in the world - who is speaking, what they say, and the things that can be
 * answered, each with what it will cost or bring written under it. An advisor offering work, a checkpoint asking for
 * papers, someone at the roadside asking for a bandage: all of them are this one window. The operation stands still
 * while it is open (nobody is shot while reading), and it is answered with the mouse or the number keys.
 */
public final class ChoicePopup {
    public boolean open;
    public String title = "", speaker = "", body = "";
    /** {label, what it means (may be empty), colour tag: "" ordinary, "good", "bad", "dim"}. */
    public final List<String[]> options = new ArrayList<>();
    private IntConsumer onPick;
    /** The option Escape chooses (-1: Escape does nothing - the question has to be answered). */
    public int cancel = -1;
    public int shown, picked = -1;
    private float age;

    public ChoicePopup show(String title, String speaker, String body, IntConsumer onPick) {
        this.title = title; this.speaker = speaker; this.body = body; this.onPick = onPick;
        options.clear(); cancel = -1; open = true; age = 0; shown++;
        return this;
    }

    public ChoicePopup option(String label, String detail) { options.add(new String[]{label, detail == null ? "" : detail, ""}); return this; }
    public ChoicePopup option(String label, String detail, String tag) { options.add(new String[]{label, detail == null ? "" : detail, tag}); return this; }
    /** The last option added is what Escape means. */
    public ChoicePopup escapes() { cancel = options.size() - 1; return this; }

    public void pick(int i) {
        if (!open || i < 0 || i >= options.size()) return;
        open = false; picked = i;
        IntConsumer f = onPick; onPick = null;
        if (f != null) f.accept(i);
    }

    /** Escape was pressed: true if that answered it. */
    public boolean escape() { if (open && cancel >= 0) { pick(cancel); return true; } return open; }

    public void draw(Ui ui, absentialis.engine.Input in) {
        if (!open) return;
        age += ui.dt;
        float a = Math.min(1, age / 0.15f);
        ui.b.rect(0, 0, ui.w, ui.h, Ui.alpha(0xB0050605, a));
        float w = Math.min(ui.s(680), ui.w - ui.s(40)), m = ui.s(24), tw = w - m * 2;
        float bodyH = ui.font.wrappedHeight(body, tw, 15 * ui.scale);
        float optH = 0;
        float[] oh = new float[options.size()];
        for (int i = 0; i < options.size(); i++) { String d = options.get(i)[1]; oh[i] = ui.s(34) + (d.isEmpty() ? 0 : ui.font.wrappedHeight(d, tw - ui.s(56), 13 * ui.scale) + ui.s(4)); optH += oh[i] + ui.s(8); }
        float h = ui.s(78) + bodyH + ui.s(18) + optH + ui.s(14);
        float x = Math.round((ui.w - w) / 2f), y = Math.round(Math.max(ui.s(20), (ui.h - h) / 2f - ui.s(20)));
        ui.panel(x, y, w, h);
        ui.b.rect(x + 1, y + 1, w - 2, ui.s(62), 0xFF1A1C1B);
        ui.b.rect(x + 1, y + ui.s(62), w - 2, 1, 0xFF3A3E38);
        ui.text(title, x + m, y + ui.s(12), 22, Ui.TXT_HI);
        if (!speaker.isEmpty()) ui.text(speaker, x + m, y + ui.s(40), 13, Ui.TXT_DIM);
        float cy = y + ui.s(78);
        ui.wrap(body, x + m, cy, tw, 15, Ui.TXT);
        cy += bodyH + ui.s(18);
        for (int i = 0; i < options.size(); i++) {
            String[] o = options.get(i);
            boolean hov = ui.hover(x + m, cy, tw, oh[i]) && age > 0.25f;
            int line = hov ? 0xFFE2A034 : 0xFF4A4E46;
            ui.b.rect(x + m, cy, tw, oh[i], hov ? 0xFF262A26 : 0xFF1B1E1C);
            ui.b.outline(x + m, cy, tw, oh[i], 1, line);
            ui.b.rect(x + m, cy, ui.s(34), oh[i], hov ? 0xFF3A3420 : 0xFF232623);
            ui.textC(String.valueOf(i + 1), x + m + ui.s(17), cy + ui.s(8), 16, hov ? Ui.TXT_HI : Ui.TXT_DIM);
            int col = "bad".equals(o[2]) ? 0xFFE08A7E : "good".equals(o[2]) ? 0xFFA8D08C : "dim".equals(o[2]) ? Ui.TXT_DIM : Ui.TXT_HI;
            ui.text(o[0], x + m + ui.s(46), cy + ui.s(8), 16, col);
            if (!o[1].isEmpty()) ui.wrap(o[1], x + m + ui.s(46), cy + ui.s(32), tw - ui.s(56), 13, Ui.TXT_DIM);
            if (hov && ui.click && !ui.consumed) { ui.consumed = true; if (ui.sound != null) ui.sound.accept("ui_click"); pick(i); return; }
            if (age > 0.25f && in.keyPressed(GLFW_KEY_1 + i)) { if (ui.sound != null) ui.sound.accept("ui_click"); pick(i); return; }
            cy += oh[i] + ui.s(8);
        }
    }
}
