package absentialis.ui;

import static org.lwjgl.glfw.GLFW.*;

import absentialis.engine.Input;
import absentialis.engine.render.Batch2D;
import absentialis.engine.render.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immediate-mode widget layer over Batch2D (addendum P1): 9-slice pixel frames with drop shadows,
 * buttons with hover / pressed / disabled states eased over 120 ms, tabs, sliders, toggles,
 * glyph icons, tooltips and a pixel cursor. All geometry is snapped to whole pixels.
 */
public final class Ui {
    public final Batch2D b;
    public final Font font;
    public final Input in;
    public final PixelArt art = new PixelArt();
    public float scale = 1f;       // UI scale slider
    public float mx, my, dt;
    public int w, h;
    public boolean click, rclick, down, consumed, shift;
    public String hot, tooltip;
    public List<String> tooltipLines;
    private final Map<String, float[]> anim = new HashMap<>(); // id -> {hover, press}
    public boolean showCursor = true;
    /** When true (a modal is open over this layer) widgets ignore the mouse. */
    public boolean block;
    public static final float EASE = 0.12f;

    // text colours
    public static final int TXT = 0xFFD6CEB8, TXT_DIM = 0xFF8E897A, TXT_HI = 0xFFF4EEDC, AMBER = 0xFFE2A034, RED = 0xFFD8483A, GREEN = 0xFF7DBA5C, BLUE = 0xFF6AA4D6;

    public Ui(Batch2D b, Font font, Input in) { this.b = b; this.font = font; this.in = in; }

    public void init() { art.build(); }

    public void begin(float dt, int w, int h) {
        prevHoverId = hoverId;
        hoverId = null;
        this.dt = dt; this.w = w; this.h = h;
        mx = (float) in.mouseX; my = (float) in.mouseY;
        click = in.mousePressed(GLFW_MOUSE_BUTTON_LEFT);
        rclick = in.mousePressed(GLFW_MOUSE_BUTTON_RIGHT);
        down = in.mouseDown(GLFW_MOUSE_BUTTON_LEFT);
        shift = in.keyDown(GLFW_KEY_LEFT_SHIFT) || in.keyDown(GLFW_KEY_RIGHT_SHIFT) || click && (in.clickMods & GLFW_MOD_SHIFT) != 0;
        consumed = false;
        block = false;
        hot = null;
        tooltip = null;
        tooltipLines = null;
    }

    /** Draws tooltip and cursor last. */
    public void end() {
        if (tooltipLines != null) drawTooltip(tooltipLines);
        else if (tooltip != null) drawTooltip(List.of(tooltip));
        if (showCursor) glyph("cursor", (int) mx - 1, (int) my - 1, Math.max(2, Math.round(2 * scale)), 0xFFFFFFFF);
    }

    public int s(float v) { return Math.round(v * scale); }
    public float px(float v) { return Math.round(v); }

    public boolean hover(float x, float y, float ww, float hh) { return !block && mx >= x && my >= y && mx < x + ww && my < y + hh; }

    /** Eased per-widget animation state (120 ms). */
    private float[] anim(String id, boolean hov, boolean pressed) {
        float[] a = anim.computeIfAbsent(id, k -> new float[2]);
        float k = Math.min(1, dt / EASE);
        a[0] += ((hov ? 1 : 0) - a[0]) * k;
        a[1] += ((pressed ? 1 : 0) - a[1]) * Math.min(1, dt / 0.06f);
        return a;
    }

    public float ease(String id, boolean on) {
        float[] a = anim.computeIfAbsent(id, k -> new float[2]);
        a[0] += ((on ? 1 : 0) - a[0]) * Math.min(1, dt / EASE);
        return a[0];
    }

    // ------------------------------------------------------------------ primitives
    public void sprite(String name, float x, float y, float ww, float hh, int argb) {
        int[] r = art.rects.get(name);
        if (r == null) return;
        float S = PixelArt.SIZE;
        b.quad(art.tex, px(x), px(y), px(ww), px(hh), r[0] / S, r[1] / S, (r[0] + r[2]) / S, (r[1] + r[3]) / S,
                ((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, ((argb >>> 24) & 255) / 255f, 0);
    }

    public void glyph(String name, float x, float y, int pixel, int argb) {
        int[] r = art.rects.get(name);
        if (r == null) return;
        sprite(name, x, y, r[2] * pixel, r[3] * pixel, argb);
    }

    /** 9-slice frame; border drawn at an integer pixel multiple so the art stays crisp. */
    public void nine(String name, float x, float y, float ww, float hh, int argb) {
        // 1.0: one flat language for every frame in the game - a dark plate and a single line round it, where there
        // were stacked bevels. Gold is kept for what is selected or has the focus, and nothing else.
        int[] flat = switch (name) {
            case "panel" -> new int[]{0xF2141615, 0xFF3A3E38}; case "panel_dark" -> new int[]{0xF20E100F, 0xFF2E322D};
            case "slot" -> new int[]{0xFF0F1110, 0xFF30342F}; case "slot_hi" -> new int[]{0xFF141A12, 0xFF7DBA5C};
            case "tip" -> new int[]{0xF8101211, 0xFF4A4E46};
            case "btn" -> new int[]{0xFF1E211F, 0xFF4A4E46}; case "btn_hover" -> new int[]{0xFF2A2E2A, 0xFFE2A034};
            case "btn_down" -> new int[]{0xFF151716, 0xFFE2A034}; case "btn_off" -> new int[]{0xFF161817, 0xFF2A2C29};
            case "tab" -> new int[]{0xFF171A18, 0xFF30342F}; case "tab_on" -> new int[]{0xFF242823, 0xFF4A4E46};
            default -> null;
        };
        if (flat != null) {
            float fa = ((argb >>> 24) & 255) / 255f;
            x = px(x); y = px(y); ww = px(ww); hh = px(hh);
            b.rect(x, y, ww, hh, alpha(flat[0], fa));
            b.outline(x, y, ww, hh, 1, alpha(flat[1], fa));
            if (name.equals("tab_on")) b.rect(x + 1, y + hh - Math.max(2, s(2)) - 1, ww - 2, Math.max(2, s(2)), alpha(0xFFE2A034, fa));
            return;
        }
        int[] r = art.rects.get(name);
        if (r == null) return;
        x = px(x); y = px(y); ww = px(ww); hh = px(hh);
        int p = Math.max(1, Math.round(2 * scale));
        float bs = 5 * p, S = PixelArt.SIZE;
        float[] xs = {x, x + bs, x + ww - bs, x + ww}, ys = {y, y + bs, y + hh - bs, y + hh};
        float[] us = {r[0], r[0] + 5, r[0] + 11, r[0] + 16}, vs = {r[1], r[1] + 5, r[1] + 11, r[1] + 16};
        float cr = ((argb >> 16) & 255) / 255f, cg = ((argb >> 8) & 255) / 255f, cb = (argb & 255) / 255f, ca = ((argb >>> 24) & 255) / 255f;
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
            float qw = xs[i + 1] - xs[i], qh = ys[j + 1] - ys[j];
            if (qw <= 0 || qh <= 0) continue;
            b.quad(art.tex, xs[i], ys[j], qw, qh, us[i] / S, vs[j] / S, us[i + 1] / S, vs[j + 1] / S, cr, cg, cb, ca, 0);
        }
    }

    public void shadow(float x, float y, float ww, float hh, int strength) {
        int o = s(4);
        b.rect(px(x + o), px(y + o), px(ww), px(hh), (strength << 24));
        b.rect(px(x + o / 2f), px(y + o / 2f), px(ww), px(hh), ((strength / 2) << 24));
    }

    public void panel(float x, float y, float ww, float hh) { shadow(x, y, ww, hh, 0x70); nine("panel", x, y, ww, hh, 0xFFFFFFFF); }
    public void panelDark(float x, float y, float ww, float hh) { shadow(x, y, ww, hh, 0x60); nine("panel_dark", x, y, ww, hh, 0xFFFFFFFF); }

    /** Panel with a title strip. Returns content top y. */
    public float window(String title, String glyph, float x, float y, float ww, float hh) {
        panel(x, y, ww, hh);
        float th = s(30);
        b.rect(px(x + 1), px(y + 1), px(ww - 2), px(th + s(5)), 0xFF1A1C1B);
        b.rect(px(x + 1), px(y + s(6) + th - 1), px(ww - 2), 1, 0xFF3A3E38);
        float tx = x + s(14);
        if (glyph != null) { glyph(glyph, tx, y + s(6) + (th - 12 * Math.max(1, s(2))) / 2f, Math.max(1, s(2)), 0xFFFFFFFF); tx += s(30); }
        text(title.toUpperCase(), tx, y + s(6) + (th - s(17)) / 2f, 17, TXT_HI);
        return y + s(6) + th + s(8);
    }

    // ------------------------------------------------------------------ text
    public float text(String t, float x, float y, float size, int argb) {
        float sz = size * scale;
        font.draw(b, t, px(x) + 1, px(y) + 1, sz, (argb & 0xFF000000) == 0 ? 0 : 0xA0000000);
        return font.draw(b, t, px(x), px(y), sz, argb);
    }
    public void textC(String t, float cx, float y, float size, int argb) { text(t, cx - tw(t, size) / 2, y, size, argb); }
    public void textR(String t, float rx, float y, float size, int argb) { text(t, rx - tw(t, size), y, size, argb); }
    public float tw(String t, float size) { return font.width(t, size * scale); }
    public float wrap(String t, float x, float y, float maxW, float size, int argb) { return font.drawWrapped(b, t, px(x), px(y), maxW, size * scale, argb); }

    // ------------------------------------------------------------------ widgets
    /** Interface sound hook: ui_hover, ui_click, ui_tab, ui_invalid. */
    public java.util.function.Consumer<String> sound;
    private String hoverId, prevHoverId;

    private void feedback(String id, boolean hov, boolean fired) {
        if (hov) { hoverId = id; if (!id.equals(prevHoverId) && sound != null) sound.accept("ui_hover"); }
        if (fired && sound != null) sound.accept(id.startsWith("ht") || id.startsWith("lt") ? "ui_tab" : "ui_click");
    }

    public boolean button(String id, float x, float y, float ww, float hh, String label, String glyph, boolean enabled) {
        if (!enabled && hover(x, y, ww, hh) && !consumed && click && sound != null) sound.accept("ui_invalid");
        boolean hov = enabled && hover(x, y, ww, hh) && !consumed;
        boolean pressed = hov && down;
        float[] a = anim(id, hov, pressed);
        float lift = 0;
        if (!enabled) nine("btn_off", x, y, ww, hh, 0xFFFFFFFF);
        else {
            nine("btn", x, y, ww, hh, 0xFFFFFFFF);
            if (a[0] > 0.01f) nine("btn_hover", x, y - lift, ww, hh, ((int) (a[0] * 255) << 24) | 0xFFFFFF);
            if (a[1] > 0.01f) nine("btn_down", x, y, ww, hh, ((int) (a[1] * 255) << 24) | 0xFFFFFF);
        }
        int col = !enabled ? TXT_DIM : mix(TXT, TXT_HI, a[0]);
        float gw = glyph == null ? 0 : 12 * Math.max(1, s(2)) + s(8);
        float lw = label == null ? 0 : tw(label, 16);
        float cx = x + (ww - gw - lw) / 2f, cy = y + (hh - s(16)) / 2f - (enabled ? lift : 0);
        if (glyph != null) glyph(glyph, cx, y + (hh - 12 * Math.max(1, s(2))) / 2f - (enabled ? lift : 0), Math.max(1, s(2)), enabled ? 0xFFFFFFFF : 0x80FFFFFF);
        if (label != null) text(label, cx + gw, cy, 16, col);
        if (hov) hot = id;
        boolean fired = hov && click;
        if (fired) consumed = true;
        feedback(id, hov, fired);
        return fired;
    }

    public boolean tab(String id, float x, float y, float ww, float hh, String label, String glyph, boolean selected) {
        boolean hov = hover(x, y, ww, hh) && !consumed;
        float[] a = anim(id, hov || selected, false);
        nine(selected ? "tab_on" : "tab", x, y, ww, hh, 0xFFFFFFFF);
        if (!selected && a[0] > 0.01f) b.rect(px(x + s(3)), px(y + hh - s(5)), px((ww - s(6)) * a[0]), s(2), 0xFFE2A034);
        float lw = tw(label, 15);
        if (glyph != null && lw + 12 * Math.max(1, s(2)) + s(18) > ww) glyph = null;
        float gw = glyph == null ? 0 : 12 * Math.max(1, s(2)) + s(6);
        float cx = x + (ww - gw - lw) / 2;
        if (glyph != null) glyph(glyph, cx, y + (hh - 12 * Math.max(1, s(2))) / 2f, Math.max(1, s(2)), 0xFFFFFFFF);
        text(label, cx + gw, y + (hh - s(15)) / 2f, 15, selected ? TXT_HI : mix(TXT_DIM, TXT, a[0]));
        boolean fired = hov && click;
        if (fired) consumed = true;
        feedback(id, hov && !selected, fired && !selected);
        return fired;
    }

    public float slider(String id, float x, float y, float ww, String label, float v, float min, float max, String fmt) {
        text(label, x, y, 14, TXT);
        String val = String.format(fmt, v);
        textR(val, x + ww, y, 14, AMBER);
        float ty = y + s(22), th = s(10);
        nine("slot", x, ty, ww, th + s(4), 0xFFFFFFFF);
        float f = (v - min) / (max - min);
        b.rect(px(x + s(3)), px(ty + s(3)), px((ww - s(6)) * f), px(th - s(2)), 0xFF8A6420);
        boolean hov = hover(x, ty - s(4), ww, th + s(12));
        float[] a = anim(id, hov, false);
        float kx = x + s(3) + (ww - s(6)) * f;
        nine(a[0] > 0.5f ? "btn_hover" : "btn", kx - s(7), ty - s(4), s(14), th + s(12), 0xFFFFFFFF);
        if (hov && down && !consumed) {
            f = Math.max(0, Math.min(1, (mx - x - s(3)) / (ww - s(6))));
            v = min + f * (max - min);
        }
        return v;
    }

    public boolean toggle(String id, float x, float y, float ww, String label, boolean v) {
        text(label, x, y + s(3), 14, TXT);
        float bw = s(46), bh = s(22), bx = x + ww - bw;
        boolean hov = hover(bx, y, bw, bh);
        float a = ease(id, v);
        nine("slot", bx, y, bw, bh, 0xFFFFFFFF);
        b.rect(px(bx + s(3)), px(y + s(3)), px((bw - s(6)) * a), px(bh - s(6)), 0xFF4E7A3A);
        nine(hov ? "btn_hover" : "btn", bx + (bw - s(22)) * a, y, s(22), bh, 0xFFFFFFFF);
        if (hov && click && !consumed) { consumed = true; return !v; }
        return v;
    }

    public void bar(float x, float y, float ww, float hh, float f, int col) {
        b.rect(px(x), px(y), px(ww), px(hh), 0xFF141615);
        b.rect(px(x + 1), px(y + 1), px((ww - 2) * Math.max(0, Math.min(1, f))), px(hh - 2), col);
        b.rect(px(x + 1), px(y + 1), px((ww - 2) * Math.max(0, Math.min(1, f))), 1, 0x40FFFFFF);
    }

    public void tip(String... lines) { tooltipLines = new ArrayList<>(List.of(lines)); }

    private void drawTooltip(List<String> lines) {
        float pad = s(10), lh = s(18);
        float ww = 0;
        for (String l : lines) ww = Math.max(ww, tw(l.replace("^", ""), 14));
        ww += pad * 2;
        float hh = lines.size() * lh + pad * 2 - s(4);
        float x = Math.min(mx + s(18), w - ww - s(4)), y = Math.min(my + s(18), h - hh - s(4));
        shadow(x, y, ww, hh, 0x80);
        nine("tip", x, y, ww, hh, 0xF8FFFFFF);
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            int c = i == 0 ? TXT_HI : TXT;
            if (l.startsWith("^r")) { c = RED; l = l.substring(2); }
            else if (l.startsWith("^g")) { c = GREEN; l = l.substring(2); }
            else if (l.startsWith("^a")) { c = AMBER; l = l.substring(2); }
            else if (l.startsWith("^d")) { c = TXT_DIM; l = l.substring(2); }
            text(l, x + pad, y + pad + i * lh, i == 0 ? 15 : 14, c);
        }
    }

    public static int mix(int a, int c, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, ca = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
        return 0xFF000000 | ((int) (ar + (ca - ar) * t) << 16) | ((int) (ag + (cg - ag) * t) << 8) | (int) (ab + (cb - ab) * t);
    }

    public static int alpha(int argb, float a) { return ((int) (((argb >>> 24) & 255) * Math.max(0, Math.min(1, a))) << 24) | (argb & 0xFFFFFF); }
}
