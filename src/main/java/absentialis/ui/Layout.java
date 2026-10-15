package absentialis.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolution-independent layout helpers (0.5, brief 11.4 and 11.5): anchored boxes in scaled UI units, text
 * measurement, wrapping to a width, fitting to a box by shrinking and then eliding, and clipped drawing. The
 * 0.5 interface positions everything through these instead of fixed pixel offsets, so a panel holds its text at
 * any resolution and any UI scale.
 */
public final class Layout {
    private Layout() {}

    /** A rectangle in screen pixels. */
    public static final class Box {
        public float x, y, w, h;
        public Box(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; }
        public float r() { return x + w; }
        public float b() { return y + h; }
        public Box inset(float k) { return new Box(x + k, y + k, w - 2 * k, h - 2 * k); }
        public boolean overlaps(Box o) { return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h; }
    }

    /**
     * A box of w x h scaled units anchored to the screen: ax, ay in 0..1 pick the anchor point on both the screen
     * and the box (0 = left/top, 0.5 = centre, 1 = right/bottom); mx, my are scaled margins from that edge.
     */
    public static Box anchor(Ui ui, float ax, float ay, float w, float h, float mx, float my) {
        float pw = ui.s(w), ph = ui.s(h);
        float x = ui.w * ax - pw * ax + (ax < 0.5f ? ui.s(mx) : ax > 0.5f ? -ui.s(mx) : 0);
        float y = ui.h * ay - ph * ay + (ay < 0.5f ? ui.s(my) : ay > 0.5f ? -ui.s(my) : 0);
        return new Box(Math.round(x), Math.round(y), pw, ph);
    }

    /** Text cut to a pixel width with an ellipsis (the whole text when it already fits). */
    public static String elide(Ui ui, String t, float maxW, float size) {
        if (t == null) return "";
        if (ui.tw(t, size) <= maxW) return t;
        String dots = "...";
        int n = t.length();
        while (n > 1 && ui.tw(t.substring(0, n).stripTrailing() + dots, size) > maxW) n--;
        return t.substring(0, Math.max(1, n)).stripTrailing() + dots;
    }

    /** The largest size from {@code size} down to {@code min} at which the text fits the width. */
    public static float fitSize(Ui ui, String t, float maxW, float size, float min) {
        float s = size;
        while (s > min && ui.tw(t, s) > maxW) s -= 0.5f;
        return s;
    }

    /** Draws one line inside a width: shrunk as far as {@code min}, then elided. Returns the size used. */
    public static float fit(Ui ui, String t, float x, float y, float maxW, float size, float min, int argb) {
        float s = fitSize(ui, t, maxW, size, min);
        ui.text(elide(ui, t, maxW, s), x, y, s, argb);
        return s;
    }

    /** Breaks text into lines no wider than maxW (pixels) at a size. Words longer than a line are split. */
    public static List<String> wrap(Ui ui, String t, float maxW, float size) {
        List<String> out = new ArrayList<>();
        if (t == null || t.isEmpty()) return out;
        for (String para : t.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String trial = line.length() == 0 ? word : line + " " + word;
                if (ui.tw(trial, size) <= maxW) { line.setLength(0); line.append(trial); continue; }
                if (line.length() > 0) { out.add(line.toString()); line.setLength(0); }
                // a single word wider than the box: break it
                while (ui.tw(word, size) > maxW && word.length() > 1) {
                    int n = word.length() - 1;
                    while (n > 1 && ui.tw(word.substring(0, n), size) > maxW) n--;
                    out.add(word.substring(0, n));
                    word = word.substring(n);
                }
                line.append(word);
            }
            out.add(line.toString());
        }
        return out;
    }

    public static float lineHeight(Ui ui, float size) { return Math.round(ui.s(size) * 1.32f); }

    /** Height in pixels the text needs at a width and size. */
    /** Wrapped text with every line centred in the width. Returns the height used. */
    public static float wrapC(Ui ui, String t, float x, float y, float maxW, float size, int argb) {
        float lh = lineHeight(ui, size), yy = y;
        for (String line : wrap(ui, t, maxW, size)) { ui.textC(line, x + maxW / 2, yy, size, argb); yy += lh; }
        return yy - y;
    }

    public static float measure(Ui ui, String t, float maxW, float size) { return wrap(ui, t, maxW, size).size() * lineHeight(ui, size); }

    /**
     * Draws wrapped text inside a box, clipped to it. If the text is taller than the box the size steps down to
     * {@code min}; if it still does not fit, the last visible line is elided. Returns the height drawn.
     */
    public static float paragraph(Ui ui, String t, Box box, float size, float min, int argb) {
        float s = size;
        List<String> lines = wrap(ui, t, box.w, s);
        while (s > min && lines.size() * lineHeight(ui, s) > box.h) { s -= 0.5f; lines = wrap(ui, t, box.w, s); }
        float lh = lineHeight(ui, s);
        int max = Math.max(1, (int) Math.floor(box.h / lh));
        ui.b.scissor((int) Math.floor(box.x), (int) Math.floor(box.y), (int) Math.ceil(box.w), (int) Math.ceil(box.h));
        for (int i = 0; i < lines.size() && i < max; i++) {
            String l = lines.get(i);
            if (i == max - 1 && lines.size() > max) l = elide(ui, l + " ...", box.w, s);
            ui.text(l, box.x, box.y + i * lh, s, argb);
        }
        ui.b.noScissor();
        return Math.min(lines.size(), max) * lh;
    }

    /** A filled arc band drawn as a strip of quads: angles in radians, clockwise from the +x axis on screen. */
    public static void arc(Ui ui, float cx, float cy, float r0, float r1, float a0, float a1, int argb) {
        if (a1 <= a0 || r1 <= r0) return;
        int steps = Math.max(2, (int) Math.ceil((a1 - a0) / 0.055f));
        float r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f, al = ((argb >>> 24) & 255) / 255f;
        float[] xy = new float[8], uv = new float[8];
        float pc = (float) Math.cos(a0), ps = (float) Math.sin(a0);
        for (int i = 1; i <= steps; i++) {
            float a = a0 + (a1 - a0) * i / steps, c = (float) Math.cos(a), s = (float) Math.sin(a);
            xy[0] = cx + pc * r0; xy[1] = cy + ps * r0; xy[2] = cx + pc * r1; xy[3] = cy + ps * r1;
            xy[4] = cx + c * r1; xy[5] = cy + s * r1; xy[6] = cx + c * r0; xy[7] = cy + s * r0;
            ui.b.quad4(ui.b.whiteTex(), xy, uv, r, g, b, al, 0);
            pc = c; ps = s;
        }
    }
}
