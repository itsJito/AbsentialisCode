package absentialis.ui;

import absentialis.gameplay.progression.Progression;

/**
 * Rank insignia (0.8). Every title on the ladder has a mark of its own, drawn from a few strokes so it reads at any
 * size - on the HUD when experience is earned, on the rank-up notice, on the debrief and beside a squadmate's name:
 *
 *   Private ... Corporal                       one to four chevrons
 *   Sergeant ... Master Gunnery Sergeant       three chevrons over one to five rockers
 *   Second Lieutenant ... Colonel              one to two bars, then one to four diamonds
 *   Brigadier General ... Commander            one to five stars, and a wreath for the last
 *
 * The mark belongs to the title, not the number: Rank 14 and Rank 17 are both Sergeants and wear the same one.
 */
public final class Insignia {
    private Insignia() {}

    /** Which title a Rank holds (0 = Private). */
    public static int title(int rank) {
        java.util.List<String[]> t = Progression.rankTable();
        int k = 0;
        for (int i = 0; i < t.size(); i++) if (rank >= Integer.parseInt(t.get(i)[1])) k = i;
        return k;
    }

    public static int titles() { return Progression.rankTable().size(); }

    /** What a title's mark is made of: {chevrons, rockers, bars, diamonds, stars, wreath}. Every title's is different. */
    public static int[] parts(int title) {
        if (title <= 3) return new int[]{title + 1, 0, 0, 0, 0, 0};
        if (title <= 8) return new int[]{3, title - 3, 0, 0, 0, 0};
        if (title <= 10) return new int[]{0, 0, title - 8, 0, 0, 0};
        if (title <= 14) return new int[]{0, 0, 0, title - 10, 0, 0};
        return new int[]{0, 0, 0, 0, Math.min(5, title - 14), title >= 20 ? 1 : 0};
    }

    /** Draws the mark of a Rank in a square of the given size with its top-left corner at (x, y). */
    public static void draw(Ui ui, int rank, float x, float y, float size, int argb) {
        int[] p = parts(title(rank));
        float u = size / 16f;                                  // the mark is laid out on a 16 x 16 grid
        float t = Math.max(1, Math.round(u * 1.6f));           // stroke
        if (p[0] > 0) {
            // chevrons, points up, stacked from the top; rockers are arcs hung beneath
            int rows = p[0] + p[1];
            float gap = Math.min(u * 3.2f, (size - t) / Math.max(1, rows)), top = y + (size - rows * gap) / 2f;
            for (int i = 0; i < p[0]; i++) chevron(ui, x + u * 2, top + i * gap, size - u * 4, gap * 0.9f, t, argb);
            for (int i = 0; i < p[1]; i++) rocker(ui, x + u * 2, top + (p[0] + i) * gap, size - u * 4, gap * 0.8f, t, argb);
        } else if (p[2] > 0) {
            float bw = u * 3, bh = u * 10, gap = u * 2, total = p[2] * bw + (p[2] - 1) * gap;
            for (int i = 0; i < p[2]; i++) ui.b.rect(Math.round(x + (size - total) / 2 + i * (bw + gap)), Math.round(y + u * 3), Math.round(bw), Math.round(bh), argb);
        } else if (p[3] > 0) {
            float d = Math.min(u * 6f, (size - u) / p[3]), total = p[3] * d;
            for (int i = 0; i < p[3]; i++) diamond(ui, x + (size - total) / 2 + i * d + d / 2, y + size / 2, d * 0.44f, argb);
        } else {
            int n = p[4];
            float d = Math.min(u * 7f, size / Math.max(1, Math.min(3, n))), cy = n > 3 ? y + size * 0.3f : y + size / 2;
            int first = Math.min(3, n), second = n - first;
            for (int i = 0; i < first; i++) star(ui, x + (size - first * d) / 2 + i * d + d / 2, cy, d * 0.46f, argb);
            for (int i = 0; i < second; i++) star(ui, x + (size - second * d) / 2 + i * d + d / 2, y + size * 0.7f, d * 0.46f, argb);
            if (p[5] > 0) { ui.b.rect(Math.round(x), Math.round(y + size - t), Math.round(size), t, argb); ui.b.rect(Math.round(x), Math.round(y), Math.round(size), t, argb); }
        }
    }

    private static void chevron(Ui ui, float x, float y, float w, float h, float t, int argb) {
        int steps = Math.max(4, Math.round(w / 2 / Math.max(1, t * 0.5f)));
        for (int i = 0; i < steps; i++) {
            float k = i / (float) (steps - 1), sx = w / 2 / steps + 1;
            float yy = y + (1 - k) * (h - t);
            ui.b.rect(Math.round(x + k * w / 2 - sx / 2 + (k == 0 ? sx / 2 : 0)), Math.round(yy), Math.round(sx), t, argb);
            ui.b.rect(Math.round(x + w - k * w / 2 - sx / 2 - (k == 0 ? sx / 2 : 0)), Math.round(yy), Math.round(sx), t, argb);
        }
    }

    private static void rocker(Ui ui, float x, float y, float w, float h, float t, int argb) {
        int steps = Math.max(6, Math.round(w / Math.max(1, t)));
        for (int i = 0; i < steps; i++) {
            float k = i / (float) (steps - 1), c = 2 * k - 1;
            ui.b.rect(Math.round(x + k * (w - w / steps)), Math.round(y + (1 - c * c) * (h - t)), Math.round(w / steps + 1), t, argb);
        }
    }

    private static void diamond(Ui ui, float cx, float cy, float r, int argb) {
        int rows = Math.max(3, Math.round(r * 2));
        for (int i = 0; i < rows; i++) { float k = 1 - Math.abs(2f * i / (rows - 1) - 1), hw = Math.max(0.5f, r * k); ui.b.rect(Math.round(cx - hw), Math.round(cy - r + i * (2 * r / rows)), Math.max(1, Math.round(hw * 2)), Math.max(1, Math.round(2 * r / rows + 0.5f)), argb); }
    }

    /** A five-pointed star, filled, by scanlines. */
    private static void star(Ui ui, float cx, float cy, float r, int argb) {
        float[] px = new float[10], py = new float[10];
        for (int i = 0; i < 10; i++) { double a = -Math.PI / 2 + i * Math.PI / 5; float rr = i % 2 == 0 ? r : r * 0.42f; px[i] = cx + (float) Math.cos(a) * rr; py[i] = cy + (float) Math.sin(a) * rr; }
        int y0 = (int) Math.floor(cy - r), y1 = (int) Math.ceil(cy + r);
        for (int y = y0; y <= y1; y++) {
            float sy = y + 0.5f, lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            float[] xs = new float[10];
            int n = 0;
            for (int i = 0; i < 10; i++) {
                int j = (i + 1) % 10;
                if ((py[i] <= sy && py[j] > sy) || (py[j] <= sy && py[i] > sy)) xs[n++] = px[i] + (sy - py[i]) / (py[j] - py[i]) * (px[j] - px[i]);
            }
            java.util.Arrays.sort(xs, 0, n);
            for (int k = 0; k + 1 < n; k += 2) ui.b.rect(Math.round(xs[k]), y, Math.max(1, Math.round(xs[k + 1] - xs[k])), 1, argb);
        }
    }
}
