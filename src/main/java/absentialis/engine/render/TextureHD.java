package absentialis.engine.render;

import static absentialis.engine.render.Tex.*;

import absentialis.engine.util.Noise;
import absentialis.engine.util.Rng;

/**
 * Version 0.6 materials, painted at 64 x 64 per voxel face - four times the detail of the 0.5 tiles.
 *
 * Everything a building is made of or finished in is redrawn here from scratch: wall masonry and panels, plaster,
 * roofing, window glazing in a frame, wallpapers, painted walls, floors, ceilings and light fittings. The brief's
 * rules for them: Eastern European (late Soviet) in character, no torn or broken wallpaper, no lettering anywhere -
 * a shop sign is a painted board with an emblem, a poster is a picture, a mark on a wall is a mark.
 *
 * Tiles are drawn to repeat on all four edges (every primitive wraps), and are deterministic for a name and variant.
 * A name this class does not know is painted by the 0.5 library at 32 x 32 and doubled ({@link #upscale}).
 */
final class TextureHD {
    private TextureHD() {}

    static final int N = 64;

    // ------------------------------------------------------------------ helpers
    private static float h(int x, int y, int s) { return Rng.hash(x, y, 7, s); }
    /** Tileable fractal noise 0..1 over the 64-pixel tile. */
    private static float fbm(Tex t, int x, int y, float scale, int s) { return Noise.fbmTiled(x / scale, y / scale, Math.max(1, (int) (N / scale)), 3, t.seed + s); }

    /** Fine per-pixel tone variation plus a broad, soft unevenness: what keeps a flat colour from looking like a flat colour. */
    private static void surface(Tex t, float fine, float broad, int s) {
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int c = t.get(x, y);
            if (a(c) == 0) continue;
            float f = 1 + (h(x, y, t.seed + s) - 0.5f) * 2 * fine + (fbm(t, x, y, 16, s) - 0.5f) * 2 * broad;
            t.set(x, y, shade(c, f));
        }
    }

    /** Darkening that runs down from a line, as water does: under sills, seams and parapets. */
    private static void weep(Tex t, int count, float strength, int minLen, int maxLen) {
        for (int i = 0; i < count; i++) {
            int x = t.rng.nextInt(N), y0 = t.rng.nextInt(N), len = minLen + t.rng.nextInt(Math.max(1, maxLen - minLen)), w = 1 + t.rng.nextInt(2);
            for (int y = 0; y < len; y++) for (int k = 0; k < w; k++) t.mul(x + k, y0 + y, 1 - strength * (1 - y / (float) len));
        }
    }

    private static void hairline(Tex t, int col, int len) {
        float x = t.rng.nextInt(N), y = t.rng.nextInt(N), a = t.rng.nextFloat() * 6.28f;
        for (int i = 0; i < len; i++) { t.blend((int) x, (int) y, col, 0.7f); a += (t.rng.nextFloat() - 0.5f) * 0.7f; x += Math.cos(a); y += Math.sin(a) * 0.8f + 0.25f; }
    }

    // ------------------------------------------------------------------ masonry
    /**
     * Running-bond brickwork at a true scale: eight courses to the metre, four bricks along it. Every brick has its
     * own tone, a lit top edge and a shadowed bottom one; the mortar sits back in the joint; a few bricks are
     * spalled, and salts have bloomed here and there.
     */
    static void brick(Tex t, int base, int mortar, float var, int v) {
        t.fill(mortar);
        surface(t, 0.05f, 0.04f, 3);
        int bw = 16, bh = 8;
        for (int row = 0; row < N / bh; row++) {
            int off = (row & 1) * (bw / 2);
            for (int bx = -bw; bx < N + bw; bx += bw) {
                float tone = 1 + (t.rng.nextFloat() - 0.5f) * 2 * var;
                int col = shade(base, tone);
                float kind = t.rng.nextFloat();
                if (kind < 0.10f) col = mix(col, rgb(r(base) - 34, g(base) - 22, b(base) - 12), 0.6f);          // an over-fired one
                else if (kind > 0.93f) col = mix(col, rgb(r(base) + 22, g(base) + 18, b(base) + 12), 0.5f);     // a pale one
                int x0 = bx + off + 1, y0 = row * bh + 1, w = bw - 2, hh = bh - 2;
                boolean spalled = t.rng.nextFloat() < 0.05f;
                for (int y = 0; y < hh; y++) for (int x = 0; x < w; x++) {
                    float sh = y == 0 ? 1.14f : y == hh - 1 ? 0.80f : 1f;
                    if (x == 0) sh *= 1.05f; else if (x == w - 1) sh *= 0.88f;
                    sh *= 1 + (h(x0 + x, y0 + y, t.seed) - 0.5f) * 0.14f;
                    if (spalled && x > w / 2 && y > 1) sh *= 0.72f;
                    t.set(x0 + x, y0 + y, shade(col, sh));
                }
                // the joint is recessed: a line of shadow under each brick, on the mortar
                for (int x = 0; x < w; x++) t.mul(x0 + x, y0 + hh, 0.78f);
            }
        }
        if (v == 1) for (int i = 0; i < 3; i++) { int cx = t.rng.nextInt(N), cy = t.rng.nextInt(N); for (int k = 0; k < 40; k++) { int x = cx + (int) (t.rng.nextGaussian() * 5), y = cy + (int) (t.rng.nextGaussian() * 3); t.blend(x, y, rgb(226, 224, 214), 0.28f); } }
        if (v == 2) weep(t, 5, 0.22f, 14, 40);
        weep(t, 2, 0.10f, 10, 30);
    }

    /**
     * A precast facade panel as the panel blocks were clad: fine exposed aggregate, a sealed seam along two edges
     * (so a wall of them shows its grid), lifting-eye plugs, and the streaks that start at every seam.
     */
    static void panel(Tex t, int base, int v) {
        t.fill(base);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            float a = h(x, y, t.seed + 1);
            int c = base;
            if (a < 0.16f) c = shade(base, 0.86f); else if (a > 0.88f) c = shade(base, 1.10f); else if (a > 0.80f) c = mix(base, rgb(150, 132, 116), 0.35f);   // the aggregate
            t.set(x, y, shade(c, 0.94f + fbm(t, x, y, 20, 2) * 0.12f));
        }
        int seam = shade(base, 0.52f), putty = shade(base, 0.74f);
        for (int i = 0; i < N; i++) {
            t.set(i, N - 1, seam); t.set(i, N - 2, putty); t.mul(i, N - 3, 0.93f); t.mul(i, 0, 1.06f);
            t.set(N - 1, i, seam); t.set(N - 2, i, putty); t.mul(N - 3, i, 0.93f); t.mul(0, i, 1.05f);
        }
        t.disc(9, 9, 2.2f, shade(base, 0.7f), 0.6f); t.disc(N - 11, 9, 2.2f, shade(base, 0.7f), 0.6f);
        weep(t, v == 2 ? 7 : 3, v == 2 ? 0.24f : 0.13f, 18, 54);
        if (v == 1) { hairline(t, shade(base, 0.6f), 30); }
    }

    /** Lime plaster or stucco: trowelled, a little uneven, weathered in soft patches. Variant 1 has a hairline crack; variant 2 is stained under a ledge. */
    static void plaster(Tex t, int base, int v) {
        t.fill(base);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            float trowel = fbm(t, x * 3, y, 14, 5), patch = fbm(t, x, y, 32, 6);
            t.set(x, y, shade(base, 0.90f + trowel * 0.12f + patch * 0.08f + (h(x, y, t.seed) - 0.5f) * 0.05f));
        }
        if (v == 1) { hairline(t, shade(base, 0.62f), 46); hairline(t, shade(base, 0.7f), 18); }
        if (v == 2) { weep(t, 6, 0.20f, 20, 50); t.stains(2, 0.14f, 9); }
        weep(t, 2, 0.07f, 16, 40);
    }

    /** Cast concrete: the lines the shuttering boards left, tie holes, pour lines, a little honeycombing. */
    static void concrete(Tex t, int base, int v, boolean floor) {
        t.fill(base);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            float board = floor ? 0 : ((y % 16) == 0 ? -0.10f : (y % 16) == 1 ? 0.04f : 0);
            float f = 0.92f + fbm(t, x, y, 18, 4) * 0.14f + (h(x, y, t.seed) - 0.5f) * 0.07f + board;
            t.set(x, y, shade(base, f));
        }
        if (!floor) for (int k = 0; k < 4; k++) { int x = 8 + (k % 2) * 32 + t.rng.nextInt(4), y = 8 + (k / 2) * 32; t.disc(x, y, 1.6f, shade(base, 0.55f), 0.5f); }
        for (int i = 0; i < 18; i++) t.mul(t.rng.nextInt(N), t.rng.nextInt(N), 0.72f);
        if (floor) { for (int i = 0; i < N; i++) { t.mul(i, 0, 0.84f); t.mul(0, i, 0.86f); } t.stains(v == 2 ? 4 : 2, 0.12f, 10); }
        if (v == 1) hairline(t, shade(base, 0.55f), 50);
        if (v == 2 && !floor) weep(t, 6, 0.2f, 18, 50);
    }

    // ------------------------------------------------------------------ roofing
    /** Corrugated asbestos-cement sheet ("shifer"): the roof of half a continent. Waves down the slope, overlaps across it, moss in the valleys. */
    static void shifer(Tex t, int base) {
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            float wave = (float) Math.sin(x / 8.0 * Math.PI * 2);
            float f = 0.86f + wave * 0.16f + fbm(t, x, y, 20, 3) * 0.10f + (h(x, y, t.seed) - 0.5f) * 0.06f;
            t.set(x, y, shade(base, f));
        }
        for (int x = 0; x < N; x++) { t.mul(x, 31, 0.66f); t.mul(x, 32, 1.10f); t.mul(x, 63, 0.66f); t.mul(x, 0, 1.10f); }
        for (int i = 0; i < 60; i++) { int x = (t.rng.nextInt(8)) * 8 + 5 + t.rng.nextInt(2), y = t.rng.nextInt(N); t.blend(x, y, rgb(96, 110, 70), 0.4f); }
        for (int k = 0; k < 4; k++) t.disc(4 + k * 16, 28, 1.2f, shade(base, 0.5f), 0.4f);
    }

    /** Clay tiles in overlapping courses. */
    static void pantiles(Tex t, int base) {
        for (int row = 0; row < 4; row++) for (int col = -1; col < 5; col++) {
            int x0 = col * 16 + (row & 1) * 8, y0 = row * 16;
            int c = shade(base, 0.86f + t.rng.nextFloat() * 0.26f);
            if (t.rng.nextFloat() < 0.1f) c = mix(c, rgb(110, 96, 80), 0.5f);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                float bulge = (float) Math.sin((x + 0.5f) / 16 * Math.PI);
                float f = 0.74f + bulge * 0.34f - (y > 12 ? (y - 12) * 0.06f : 0) + (y == 0 ? 0.10f : 0);
                t.set(x0 + x, y0 + y, shade(c, f * (1 + (h(x0 + x, y0 + y, t.seed) - 0.5f) * 0.08f)));
            }
        }
        for (int i = 0; i < 26; i++) t.blend(t.rng.nextInt(N), t.rng.nextInt(N), rgb(92, 104, 66), 0.35f);
    }

    /** Standing-seam sheet metal: flat pans, a raised seam every half metre, rust starting at the laps. */
    static void seamMetal(Tex t, int base) {
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int m = x % 32;
            float f = m == 0 ? 1.30f : m == 1 ? 0.62f : m == 31 ? 0.86f : 0.96f + fbm(t, x, y * 3, 24, 2) * 0.08f;
            t.set(x, y, shade(base, f * (1 + (h(x, y, t.seed) - 0.5f) * 0.04f)));
        }
        for (int x = 0; x < N; x++) { t.mul(x, 0, 0.74f); t.mul(x, 1, 1.08f); }
        for (int i = 0; i < 46; i++) { int x = t.rng.nextInt(N), y = t.rng.nextInt(6) + (t.rng.nextFloat() < 0.3f ? t.rng.nextInt(N) : 0); t.blend(x, y, rgb(128, 74, 44), 0.45f); }
    }

    /** Bituminous felt in rolled strips, lapped and sealed with poured tar. */
    static void felt(Tex t, int v) {
        int base = rgb(52, 52, 55);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            float f = 0.88f + fbm(t, x, y, 10, 3) * 0.20f + (h(x, y, t.seed) - 0.5f) * 0.16f;
            t.set(x, y, shade(base, f));
        }
        for (int x = 0; x < N; x++) { int wob = (int) (Math.sin(x * 0.35) * 1.2); t.set(x, 31 + wob, rgb(22, 22, 24)); t.mul(x, 32 + wob, 1.22f); t.set(x, 63, rgb(24, 24, 26)); }
        if (v > 0) t.stains(3, 0.22f, 9);
        if (v == 2) hairline(t, rgb(20, 20, 22), 40);
    }

    // ------------------------------------------------------------------ glazing
    /**
     * A window as a window: a painted timber casement with a glazing bar, the pane behind it reflecting sky in soft
     * diagonal bands, a drift of dust along the sill. Frame pixels are opaque, the pane lets most light through.
     * The frame is symmetrical, so the tile reads the same from inside and out, and stacks into a tall window with a
     * transom between the lights.
     */
    static void window(Tex t, int paneRgb, int paneAlpha, int frame, float dirt, boolean cracked) {
        int pr = r(paneRgb), pg = g(paneRgb), pb = b(paneRgb);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            float sky = 1.12f - y / (float) N * 0.30f;
            float band = (float) Math.sin((x + y) / 23.0 * Math.PI);
            band = band > 0.86f ? 0.22f : band > 0.6f ? 0.07f : 0;
            int al = Math.min(255, (int) (paneAlpha + band * 260));
            t.set(x, y, argb(al, (int) (pr * sky + band * 120), (int) (pg * sky + band * 120), (int) (pb * sky + band * 110)));
        }
        if (dirt > 0) for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            float edge = Math.max(0, (y - 36) / 28f) + Math.max(0, (8 - Math.min(x, N - 1 - x)) / 16f);
            float d = dirt * (0.25f + edge) * (0.6f + fbm(t, x, y, 9, 4) * 0.8f);
            if (d > 0.02f) { int c = t.get(x, y); int al = Math.min(255, a(c) + (int) (d * 150)); t.set(x, y, argb(al, (int) (r(c) * (1 - d * 0.5f) + 96 * d * 0.5f), (int) (g(c) * (1 - d * 0.5f) + 92 * d * 0.5f), (int) (b(c) * (1 - d * 0.5f) + 80 * d * 0.5f))); }
        }
        if (cracked) {
            int cx = 20 + t.rng.nextInt(24), cy = 20 + t.rng.nextInt(24);
            for (int k = 0; k < 8; k++) { double ang = k * 0.78 + t.rng.nextFloat() * 0.5; float len = 14 + t.rng.nextFloat() * 22; for (float s = 0; s < len; s += 0.5f) { int x = cx + (int) (Math.cos(ang) * s), y = cy + (int) (Math.sin(ang) * s); if (x > 5 && y > 5 && x < N - 6 && y < N - 6) t.set(x, y, argb(235, 236, 244, 246)); } }
            for (int rr = 5; rr < 18; rr += 6) for (int k = 0; k < 40; k++) if (t.rng.chance(0.45)) { int x = cx + (int) (Math.cos(k * 0.157) * rr), y = cy + (int) (Math.sin(k * 0.157) * rr); if (x > 5 && y > 5 && x < N - 6 && y < N - 6) t.set(x, y, argb(210, 228, 238, 242)); }
        }
        // the casement: 5 px all round, a 4 px glazing bar up the middle, each member lit from above and shadowed below
        int hi = shade(frame, 1.14f), lo = shade(frame, 0.72f), in = shade(frame, 0.56f);
        for (int i = 0; i < N; i++) for (int k = 0; k < 5; k++) {
            int c = k == 0 ? hi : k == 4 ? in : frame;
            t.set(i, k, c); t.set(k, i, k == 4 ? in : k == 0 ? hi : frame);
            t.set(i, N - 1 - k, k == 0 ? lo : k == 4 ? hi : shade(frame, 0.9f)); t.set(N - 1 - k, i, k == 0 ? lo : k == 4 ? in : shade(frame, 0.92f));
        }
        for (int y = 5; y < N - 5; y++) { t.set(30, y, hi); t.set(31, y, frame); t.set(32, y, frame); t.set(33, y, lo); }
        // paint that has seen some winters
        for (int i = 0; i < 26; i++) { int x = t.rng.nextInt(N), y = t.rng.nextInt(N); if (a(t.get(x, y)) == 255) t.mul(x, y, 0.80f); }
        t.set(28, 34, rgb(60, 58, 54)); t.set(28, 35, rgb(60, 58, 54)); t.set(35, 34, rgb(60, 58, 54)); t.set(35, 35, rgb(60, 58, 54));   // the catches
    }

    // ------------------------------------------------------------------ wall finishes
    /** A damask wallpaper: a medallion on a half-drop repeat, printed tone on tone, with a fine vertical rule. Whole and clean. */
    static void damask(Tex t, int ground, int figure, int v) {
        t.fill(ground);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            // two sprigs per tile on a half-drop: four petals round a centre, a leaf above and below
            for (int k = 0; k < 2; k++) {
                float cx = k == 0 ? 16 : 48, cy = k == 0 ? 16 : 48;
                float dx = Math.abs(((x - cx + 96 + 32) % 64) - 32), dy = ((y - cy + 96 + 32) % 64) - 32, ay = Math.abs(dy);
                boolean petal = (dx - 5) * (dx - 5) + ay * ay < 10 || dx * dx + (ay - 5) * (ay - 5) < 10;
                boolean heart = dx * dx + ay * ay < 3;
                boolean leaf = ay > 9 && ay < 15 && dx < (15 - ay) * 0.55f + 0.5f;
                boolean dot = Math.abs(dx - 11) < 1.2f && ay < 1.2f;
                if (heart) t.set(x, y, shade(figure, 0.86f));
                else if (petal || leaf || dot) t.set(x, y, figure);
            }
            if (x % 32 == 0) t.blend(x, y, figure, 0.45f);
        }
        surface(t, 0.035f, 0.03f, 8);
        if (v == 1) for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) t.mul(x, y, 0.985f + (y / (float) N) * 0.03f);
        if (v == 2) t.stains(1, 0.06f, 12);
    }

    /** A striped paper: broad bands with a pinstripe either side and a small sprig down each band. */
    static void stripePaper(Tex t, int a, int b, int accent, int v) {
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int m = x % 32;
            int c = m < 14 ? a : m < 16 ? accent : m < 30 ? b : accent;
            t.set(x, y, c);
            if ((m == 7 || m == 23) && y % 16 < 5) t.set(x, y, shade(c, 0.86f));
            if ((m == 6 || m == 8 || m == 22 || m == 24) && y % 16 == 2) t.set(x, y, shade(c, 0.86f));
        }
        surface(t, 0.03f, 0.03f, 9);
        if (v == 2) t.stains(1, 0.05f, 12);
    }

    /** A small lozenge-trellis paper (what 0.5's torn wallpaper becomes: the same wall, whole). */
    static void trellis(Tex t, int ground, int line) {
        t.fill(ground);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int u = (x + y) % 16, w = (x - y + 64) % 16;
            if (u == 0 || w == 0) t.set(x, y, line);
            if (u == 8 && w == 8) { t.set(x, y, shade(line, 0.85f)); t.set(x + 1, y, shade(line, 0.9f)); t.set(x, y + 1, shade(line, 0.9f)); }
        }
        surface(t, 0.03f, 0.03f, 10);
    }

    /**
     * The two-tone wall of every stairwell, corridor, school and clinic east of the Elbe: gloss oil paint to shoulder
     * height, a ruled line, whitewash above. The tile is the lower part of the wall on its lower half and whitewash
     * on its upper, so a wall three blocks high reads right when the generator puts it on the bottom course.
     */
    static void dado(Tex t, int paint) {
        int white = rgb(214, 210, 198);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            boolean lower = y >= 26;
            int c = lower ? paint : white;
            float f = lower ? 0.92f + fbm(t, x, y * 2, 22, 2) * 0.14f : 0.93f + fbm(t, x, y, 14, 3) * 0.10f;
            if (lower && y - 26 < 3) f *= 1.10f - (y - 26) * 0.03f;            // the sheen at the top of the gloss
            t.set(x, y, shade(c, f * (1 + (h(x, y, t.seed) - 0.5f) * 0.04f)));
        }
        for (int x = 0; x < N; x++) { t.set(x, 24, shade(paint, 0.55f)); t.set(x, 25, shade(paint, 0.70f)); }
        for (int i = 0; i < 12; i++) { int x = t.rng.nextInt(N), y = 30 + t.rng.nextInt(32); t.mul(x, y, 0.82f); t.mul(x + 1, y, 0.88f); }
        for (int x = 0; x < N; x++) { t.mul(x, 62, 0.86f); t.mul(x, 63, 0.72f); }
    }

    /** Plain painted wall, the same gloss all the way up (the middle and upper courses over a dado, or a painted cell). */
    static void paintedWall(Tex t, int paint) {
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) t.set(x, y, shade(paint, 0.93f + fbm(t, x, y, 16, 3) * 0.12f + (h(x, y, t.seed) - 0.5f) * 0.04f));
    }

    /** Glazed wall tiles, four to the block: each catches the light on its top edge; the grout has greyed. */
    static void glazed(Tex t, int tile, int grout, int accent, boolean checker) {
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int tx = x / 16, ty = y / 16, mx = x % 16, my = y % 16;
            if (mx == 0 || my == 0) { t.set(x, y, shade(grout, 0.9f + h(x, y, t.seed) * 0.15f)); continue; }
            int c = checker && ((tx + ty) & 1) == 1 ? accent : tile;
            float tone = 0.95f + h(tx, ty, t.seed + 3) * 0.08f;
            float f = tone * (my == 1 ? 1.12f : my == 15 ? 0.84f : 1f) * (mx == 1 ? 1.05f : mx == 15 ? 0.88f : 1f);
            if (mx + my > 6 && mx + my < 10 && mx < 8) f *= 1.07f;              // a glint across the glaze
            t.set(x, y, shade(c, f * (1 + (h(x, y, t.seed) - 0.5f) * 0.03f)));
        }
        for (int i = 0; i < 2; i++) { int tx = t.rng.nextInt(4), ty = t.rng.nextInt(4); for (int k = 0; k < 4; k++) t.mul(tx * 16 + 12 + (k & 1), ty * 16 + 12 + k / 2, 0.86f); }     // a chipped corner
    }

    /** Veneered panelling with a cover strip at each joint. */
    static void veneer(Tex t, int wood) {
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            float grain = (float) Math.sin(x * 1.7 + Math.sin(y * 0.09 + (x / 32) * 2.3) * 2.2);
            float f = 0.93f + grain * 0.025f + fbm(t, x * 4, y, 30, 3) * 0.08f;
            t.set(x, y, shade(wood, f));
        }
        for (int y = 0; y < N; y++) { t.set(31, y, shade(wood, 0.62f)); t.set(32, y, shade(wood, 1.12f)); t.set(0, y, shade(wood, 1.08f)); t.set(63, y, shade(wood, 0.62f)); }
        for (int x = 0; x < N; x++) { t.mul(x, 0, 1.08f); t.mul(x, 63, 0.75f); }
    }

    // ------------------------------------------------------------------ floors and ceilings
    /** Herringbone oak parquet under old varnish. */
    static void herringbone(Tex t, int oak, int v) {
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            // blocks 16 x 4 laid in a 16-pixel zigzag
            int col = x / 16, lx = x % 16;
            boolean up = (col & 1) == 0;
            int along = up ? (y + lx + 640) : (y - lx + 640);
            int block = along / 4 + col * 31, edge = along % 4;
            float tone = 0.84f + h(block, col, t.seed + 1) * 0.30f;
            float f = tone * (edge == 0 ? 0.72f : 1f) * (lx == 0 ? 0.74f : 1f);
            float grain = (float) Math.sin((up ? y - lx : y + lx) * 1.9 + h(block, 3, t.seed) * 6) * 0.03f;
            t.set(x, y, shade(oak, f + grain));
        }
        surface(t, 0.025f, 0.03f, 4);
        if (v == 1) t.stains(2, 0.10f, 10);
        if (v == 2) for (int i = 0; i < 20; i++) { int x = t.rng.nextInt(N), y = t.rng.nextInt(N); t.mul(x, y, 0.8f); t.mul(x + 1, y + 1, 0.86f); }
    }

    /** Painted floorboards: long boards, nailed, the paint worn through on the walking line. */
    static void boards(Tex t, int paint, int wood) {
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int board = x / 8, lx = x % 8;
            float tone = 0.90f + h(board, 1, t.seed) * 0.16f;
            float wear = fbm(t, x, y, 26, 5);
            int c = wear > 0.66f ? mix(paint, wood, Math.min(1, (wear - 0.66f) * 4)) : paint;
            float f = tone * (lx == 0 ? 0.62f : lx == 1 ? 1.06f : 1f) + (float) Math.sin(y * 0.7 + board * 2.1) * 0.02f;
            t.set(x, y, shade(c, f));
        }
        for (int b = 0; b < 8; b++) { int y = (int) (h(b, 9, t.seed) * 56) + 4; for (int x = 0; x < 8; x++) t.mul(b * 8 + x, y, 0.68f); t.set(b * 8 + 3, y + 2, shade(paint, 0.5f)); t.set(b * 8 + 5, y - 2, shade(paint, 0.5f)); }
    }

    /** Sheet linoleum printed as small tiles, scuffed where chairs have stood. */
    static void lino(Tex t, int a, int b, int v) {
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int tx = x / 16, ty = y / 16, mx = x % 16, my = y % 16;
            int c = ((tx + ty) & 1) == 0 ? a : b;
            float marble = fbm(t, x + tx * 37, y + ty * 19, 7, 3);
            float f = 0.92f + marble * 0.12f;
            if (mx == 0 || my == 0) f *= 0.90f;
            t.set(x, y, shade(c, f));
        }
        surface(t, 0.025f, 0.03f, 6);
        for (int i = 0; i < (v == 2 ? 5 : 2); i++) { int x = t.rng.nextInt(N), y = t.rng.nextInt(N), len = 5 + t.rng.nextInt(10); for (int k = 0; k < len; k++) t.mul(x + k, y + k / 3, 0.84f); }
    }

    /**
     * A wall-hanging-turned-floor carpet in the pattern every flat had: a field with a lozenge medallion, a guard
     * stripe and a patterned border. The border is on the tile's edge, so each block of floor reads as one bordered
     * panel and a floor of them as a patterned carpet.
     */
    static void rug(Tex t, int field, int figure, int border, int cream) {
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int e = Math.min(Math.min(x, N - 1 - x), Math.min(y, N - 1 - y));
            int c;
            if (e < 2) c = border;
            else if (e < 3) c = cream;
            else if (e < 7) c = ((x + y) / 2 % 3 == 0) ? cream : ((x - y + 64) / 2 % 3 == 0) ? figure : border;
            else if (e < 8) c = cream;
            else {
                int dx = Math.abs(x - 32), dy = Math.abs(y - 32), man = dx + dy;
                c = man < 6 ? cream : man < 10 ? figure : man < 12 ? cream : man < 20 ? border : man == 20 || man == 21 ? cream : field;
                if (man > 21 && (dx % 8 == 0 && dy % 8 == 0)) c = cream;
                if (man > 21 && ((dx % 8 == 1 || dx % 8 == 7) && dy % 8 == 0 || dx % 8 == 0 && (dy % 8 == 1 || dy % 8 == 7))) c = figure;
            }
            // pile: every knot a slightly different height
            t.set(x, y, shade(c, 0.86f + h(x, y, t.seed) * 0.22f - ((x + y) % 2) * 0.03f));
        }
    }

    /** Office loop-pile carpet. */
    static void loop(Tex t, int base) {
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            float f = 0.82f + h(x, y, t.seed) * 0.26f + ((x + y * 2) % 3 == 0 ? 0.06f : 0) + fbm(t, x, y, 24, 3) * 0.08f;
            t.set(x, y, shade(base, f));
        }
        t.stains(1, 0.10f, 9);
    }

    /** Whitewashed ceiling: brush marks, the shadow of a slab joint, a yellowed patch where the pipe above leaks. */
    static void whitewash(Tex t, int v) {
        int base = rgb(214, 211, 200);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) t.set(x, y, shade(base, 0.94f + fbm(t, x * 2, y, 12, 2) * 0.08f + (h(x, y, t.seed) - 0.5f) * 0.03f));
        for (int x = 0; x < N; x++) { t.mul(x, 0, 0.88f); t.mul(x, 1, 0.96f); }
        if (v == 1) for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) { float d = (float) Math.hypot(x - 40, y - 26); if (d < 14) t.blend(x, y, rgb(188, 170, 124), (1 - d / 14) * 0.35f); }
    }

    /** Pressed-fibre ceiling tiles on a metal grid. */
    static void ceilingTile(Tex t) {
        int base = rgb(206, 204, 194);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int mx = x % 32, my = y % 32;
            if (mx == 0 || my == 0) { t.set(x, y, rgb(150, 150, 146)); continue; }
            if (mx == 1 || my == 1) { t.set(x, y, rgb(228, 226, 218)); continue; }
            float pin = h(x / 2, y / 2, t.seed + 5) > 0.82f ? 0.84f : 1f;
            t.set(x, y, shade(base, pin * (0.96f + h(x, y, t.seed) * 0.06f)));
        }
        t.stains(1, 0.10f, 10);
    }

    // ------------------------------------------------------------------ lights
    /**
     * A ceiling light fitting seen for what it is: a pressed-steel rose and a frosted glass bowl, brightest at the
     * middle where the lamp is, falling off evenly to the rim. No rings, no cross, no pattern on the glass.
     */
    static void fitting(Tex t, boolean lit, int glow) {
        int rim = lit ? rgb(196, 190, 170) : rgb(126, 126, 120), glass = lit ? glow : rgb(168, 168, 160);
        t.fill(rim);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            float d = (float) Math.hypot(x - 31.5f, y - 31.5f);
            if (d > 30) { t.set(x, y, shade(rim, 0.82f + h(x, y, t.seed) * 0.08f)); continue; }
            if (d > 27) { t.set(x, y, shade(rim, d > 28.5f ? 0.74f : 1.10f)); continue; }
            float k = 1 - d / 27f;
            if (lit) t.set(x, y, mix(shade(glass, 0.86f), rgb(255, 252, 240), k * k * 0.9f + 0.1f));
            else t.set(x, y, shade(glass, 0.84f + k * 0.20f + (h(x, y, t.seed) - 0.5f) * 0.03f));
        }
    }

    /** A light box: an even field of colour behind opal acrylic in a thin dark frame. Lit or dead, it is plain. */
    static void lightbox(Tex t, int col, boolean lit) {
        int frame = rgb(34, 34, 38);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int e = Math.min(Math.min(x, N - 1 - x), Math.min(y, N - 1 - y));
            if (e < 3) { t.set(x, y, shade(frame, e == 0 ? 1.3f : 1f)); continue; }
            float k = 1 - (float) Math.hypot(x - 31.5f, y - 31.5f) / 46f;
            t.set(x, y, lit ? mix(shade(col, 0.78f), mix(col, rgb(255, 255, 255), 0.45f), Math.max(0, k)) : shade(rgb(84, 86, 90), 0.9f + k * 0.12f + (h(x, y, t.seed) - 0.5f) * 0.03f));
        }
    }

    /** A small round bulkhead lamp behind a wire guard. */
    static void bulkhead(Tex t, int col) {
        t.fill(rgb(48, 48, 52));
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            float d = (float) Math.hypot(x - 31.5f, y - 31.5f);
            if (d < 24) t.set(x, y, mix(shade(col, 0.7f), mix(col, rgb(255, 240, 230), 0.5f), Math.max(0, 1 - d / 20f)));
            else if (d < 27) t.set(x, y, rgb(70, 70, 74));
        }
        for (int i = 4; i < N - 4; i++) { if (Math.abs(i - 31.5f) < 24) { t.set(i, 21, rgb(40, 40, 44)); t.set(i, 42, rgb(40, 40, 44)); t.set(21, i, rgb(40, 40, 44)); t.set(42, i, rgb(40, 40, 44)); } }
    }

    // ------------------------------------------------------------------ signs, pictures and marks - with no lettering
    /** A shop fascia: a painted board in a frame with an emblem in the middle. The emblem says what is sold; nothing is written. */
    static void fascia(Tex t, int v) {
        int[] bg = {rgb(150, 44, 38), rgb(44, 78, 128), rgb(190, 152, 54), rgb(52, 104, 62)};
        int board = bg[v & 3], cream = rgb(226, 216, 190), dark = shade(board, 0.55f);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int e = Math.min(Math.min(x, N - 1 - x), Math.min(y, N - 1 - y));
            t.set(x, y, e < 2 ? rgb(46, 40, 34) : e < 4 ? cream : shade(board, 0.94f + fbm(t, x, y * 3, 20, 2) * 0.10f));
        }
        for (int y = 8; y < N - 8; y++) for (int x = 8; x < N - 8; x++) {
            float dx = x - 31.5f, dy = y - 31.5f, d = (float) Math.hypot(dx, dy);
            boolean on = switch (v & 3) {
                case 0 -> d < 15 && d > 11 || Math.abs(dx) < 2 && Math.abs(dy) < 9 || Math.abs(dy) < 2 && Math.abs(dx) < 9;                       // a wheel: hardware
                case 1 -> Math.abs(dx) < 12 && dy > -4 && dy < 12 && !(Math.abs(dx) < 9 && dy > -1 && dy < 9) || Math.abs(dx) < 6 && dy > -11 && dy <= -4 && !(Math.abs(dx) < 3 && dy > -8);   // a basket: groceries
                case 2 -> d < 14 && (Math.atan2(dy, dx) + Math.PI) % (Math.PI / 4) < Math.PI / 8 || d < 5;                                    // a sun: bakery
                default -> Math.abs(dx) + Math.abs(dy) < 15 && Math.abs(dx) + Math.abs(dy) > 10 || d < 4;                                       // a lozenge: general
            };
            if (on) t.set(x, y, cream);
            else if (d < 18 && d > 17) t.set(x, y, dark);
        }
        weep(t, 2, 0.10f, 10, 30);
    }

    /** A picture pasted to a wall: flat shapes in three colours, an edge curling. A picture, not a notice. */
    static void picture(Tex t, int v) {
        int paper = rgb(206, 196, 168);
        int[][] pal = {{rgb(170, 52, 44), rgb(44, 54, 76)}, {rgb(46, 84, 110), rgb(196, 150, 60)}, {rgb(60, 96, 66), rgb(150, 52, 44)}};
        int a = pal[v % 3][0], b = pal[v % 3][1];
        for (int y = 6; y < N - 5; y++) for (int x = 9; x < N - 9; x++) {
            int c = paper;
            float dx = x - 32, dy = y - 26;
            if (v % 3 == 0) { if (dy > 8 - Math.abs(dx) * 0.5f && y < 46) c = b; if (Math.hypot(dx, dy + 2) < 9) c = a; }                    // a sun over hills
            else if (v % 3 == 1) { if (Math.abs(dx) < 4 && y > 14 && y < 50) c = a; if (y > 14 && y < 26 && Math.abs(dx) < (26 - y)) c = b; if (y >= 44 && y < 50 && Math.abs(dx) < 12) c = a; }   // a tower
            else { for (int k = 0; k < 3; k++) if (Math.abs(dx - (k - 1) * 11) < 4 && y > 20 - k * 3 && y < 48) c = k == 1 ? a : b; }           // three chimneys
            if (y > 50) c = shade(paper, 0.9f);
            t.set(x, y, shade(c, 0.94f + h(x, y, t.seed) * 0.08f));
        }
        for (int k = 0; k < 6; k++) for (int j = 0; j <= k; j++) t.set(N - 10 - j, 6 + k - j, rgb(150, 142, 120));       // the corner lifting
    }

    /** Paint on a wall: sprayed shapes - loops, arrows, crosses, an eye. Marks people leave, none of them words. */
    static void marks(Tex t, int v) {
        int[] cols = {rgb(214, 54, 62), rgb(64, 176, 214), rgb(236, 214, 64), rgb(84, 204, 94), rgb(226, 226, 226), rgb(196, 84, 196)};
        int c = cols[v % cols.length], c2 = cols[(v + 2) % cols.length];
        java.util.function.BiConsumer<float[], Integer> spray = (p, col) -> { for (int k = 0; k < 5; k++) t.blend((int) (p[0] + (t.rng.nextFloat() - 0.5f) * 2.4f), (int) (p[1] + (t.rng.nextFloat() - 0.5f) * 2.4f), col, 0.85f); };
        switch (v % 6) {
            case 0 -> { for (float a = 0; a < 12.6f; a += 0.04f) spray.accept(new float[]{32 + (float) Math.cos(a) * (8 + a * 1.4f), 32 + (float) Math.sin(a) * (6 + a * 1.1f)}, c); }                    // a spiral
            case 1 -> { for (float s = 0; s < 44; s += 0.3f) { spray.accept(new float[]{10 + s, 34 - (float) Math.sin(s * 0.28f) * 9}, c); } for (float s = 0; s < 10; s += 0.3f) { spray.accept(new float[]{54 - s, 34 - s * 0.9f}, c2); spray.accept(new float[]{54 - s, 34 + s * 0.9f}, c2); } }   // a wave and an arrowhead
            case 2 -> { for (float s = 0; s < 40; s += 0.3f) { spray.accept(new float[]{12 + s, 14 + s}, c); spray.accept(new float[]{52 - s, 14 + s}, c); } for (float a = 0; a < 6.3f; a += 0.03f) spray.accept(new float[]{32 + (float) Math.cos(a) * 24, 34 + (float) Math.sin(a) * 24}, c2); }   // a struck circle
            case 3 -> { for (float s = 0; s < 40; s += 0.3f) spray.accept(new float[]{10 + s, 32}, c); for (float s = 0; s < 12; s += 0.3f) { spray.accept(new float[]{50 - s, 32 - s}, c); spray.accept(new float[]{50 - s, 32 + s}, c); } for (int k = 0; k < 4; k++) for (float s = 0; s < 10; s += 0.3f) spray.accept(new float[]{12 + k * 6, 46 + s}, c2); }   // an arrow and a tally
            case 4 -> { int red = rgb(144, 22, 28); for (float a = 0; a < 6.3f; a += 0.02f) spray.accept(new float[]{32 + (float) Math.cos(a) * 24, 32 + (float) Math.sin(a) * 11}, red); for (float a = 0; a < 6.3f; a += 0.05f) spray.accept(new float[]{32 + (float) Math.cos(a) * 6, 32 + (float) Math.sin(a) * 6}, red); for (int k = 0; k < 5; k++) for (float s = 0; s < 12; s += 0.4f) spray.accept(new float[]{20 + k * 6, 46 + s}, red); }   // an eye that weeps
            default -> { for (float s = 0; s < 44; s += 0.3f) { spray.accept(new float[]{10 + s, 10 + s}, c); spray.accept(new float[]{54 - s, 10 + s}, c); } for (float a = 0; a < 6.3f; a += 0.08f) { spray.accept(new float[]{32 + (float) Math.cos(a) * 4, 8 + (float) Math.sin(a) * 4}, c2); spray.accept(new float[]{8 + (float) Math.cos(a) * 4, 32 + (float) Math.sin(a) * 4}, c2); } }   // a search cross
        }
    }

    // ------------------------------------------------------------------ dispatch
    /** Paints a 0.6 material at 64 x 64, or returns false to leave it to the 0.5 library. */
    static boolean draw(Tex t, String name, int v) {
        switch (name) {
            case "brick_red": brick(t, v == 2 ? rgb(152, 72, 54) : rgb(142, 64, 48), rgb(124, 116, 104), 0.13f, v); return true;
            case "brick_brown": brick(t, rgb(112, 74, 54), rgb(108, 100, 92), 0.13f, v); return true;
            case "brick_yellow": brick(t, rgb(188, 164, 110), rgb(140, 132, 118), 0.10f, v); return true;
            case "brick_white": brick(t, rgb(206, 202, 192), rgb(162, 158, 150), 0.06f, v); return true;     // sand-lime "silicate" brick
            case "concrete_panel": panel(t, rgb(168, 164, 156), v); return true;
            case "plaster": plaster(t, rgb(202, 192, 172), v); return true;
            case "plaster_blue": plaster(t, rgb(124, 152, 166), v); return true;
            case "plaster_ochre": plaster(t, rgb(198, 154, 90), v); return true;
            case "concrete": concrete(t, rgb(144, 142, 136), v, false); return true;
            case "concrete_dark": concrete(t, rgb(94, 92, 90), v, false); return true;
            case "concrete_floor": concrete(t, rgb(132, 130, 124), v, true); return true;
            case "roof_tiles": pantiles(t, v == 1 ? rgb(144, 70, 52) : v == 2 ? rgb(156, 80, 58) : rgb(150, 74, 54)); return true;
            case "roof_slate": shifer(t, rgb(98, 100, 104)); return true;
            case "roof_metal": seamMetal(t, rgb(118, 124, 128)); return true;
            case "roof_tar": felt(t, v); return true;
            case "glass": window(t, rgb(150, 186, 204), 64, rgb(214, 208, 192), 0f, false); return true;
            case "glass_dirty": window(t, rgb(142, 160, 160), 84, rgb(190, 184, 166), 0.9f, false); return true;
            case "glass_cracked": window(t, rgb(146, 172, 182), 78, rgb(190, 184, 166), 0.5f, true); return true;
            case "glass_blue": window(t, rgb(96, 142, 186), 110, rgb(96, 100, 106), 0.15f, false); return true;
            case "wallpaper_floral": damask(t, v == 1 ? rgb(176, 170, 138) : rgb(188, 176, 144), v == 1 ? rgb(150, 150, 110) : rgb(166, 150, 112), v); return true;
            case "wallpaper_stripe": stripePaper(t, rgb(196, 182, 156), rgb(178, 164, 140), rgb(138, 110, 86), v); return true;
            case "wallpaper_torn": trellis(t, rgb(182, 186, 164), rgb(148, 156, 128)); return true;
            case "dado_green": dado(t, rgb(92, 128, 102)); return true;
            case "dado_blue": dado(t, rgb(104, 134, 158)); return true;
            case "paint_green": paintedWall(t, rgb(92, 128, 102)); return true;
            case "paint_blue": paintedWall(t, rgb(104, 134, 158)); return true;
            case "office_panel": veneer(t, rgb(150, 112, 72)); return true;
            case "tile_white": glazed(t, rgb(222, 222, 214), rgb(150, 148, 140), 0, false); return true;
            case "tile_green": glazed(t, rgb(150, 186, 164), rgb(128, 136, 126), 0, false); return true;
            case "tile_gray": glazed(t, rgb(150, 152, 152), rgb(104, 104, 104), 0, false); return true;
            case "tile_checker": glazed(t, rgb(214, 208, 192), rgb(120, 116, 108), rgb(96, 60, 48), true); return true;
            case "parquet": herringbone(t, rgb(160, 114, 66), v); return true;
            case "wood_planks": boards(t, rgb(142, 88, 54), rgb(150, 120, 84)); return true;
            case "linoleum": lino(t, rgb(150, 116, 84), rgb(132, 100, 72), v); return true;
            case "carpet_red": rug(t, rgb(132, 34, 36), rgb(30, 34, 58), rgb(92, 22, 28), rgb(206, 186, 140)); return true;
            case "carpet_blue": rug(t, rgb(44, 62, 104), rgb(126, 36, 38), rgb(30, 40, 72), rgb(204, 190, 150)); return true;
            case "carpet_gray": loop(t, rgb(112, 114, 116)); return true;
            case "ceiling_tile": ceilingTile(t); return true;
            case "ceiling_white": whitewash(t, v); return true;
            case "lamp": fitting(t, true, rgb(255, 236, 190)); return true;
            case "lamp_off": fitting(t, false, 0); return true;
            case "neon_red": lightbox(t, rgb(255, 64, 72), true); return true;
            case "neon_blue": lightbox(t, rgb(70, 140, 255), true); return true;
            case "neon_green": lightbox(t, rgb(70, 230, 120), true); return true;
            case "neon_off": lightbox(t, 0, false); return true;
            case "red_light": bulkhead(t, rgb(255, 44, 34)); return true;
            case "sign_shop": fascia(t, v); return true;
            case "poster": concrete(t, rgb(144, 142, 136), 0, false); picture(t, v); return true;
            case "graffiti": if (v == 2) brick(t, rgb(142, 64, 48), rgb(124, 116, 104), 0.13f, 0); else concrete(t, rgb(144, 142, 136), v, false); marks(t, v); return true;
            case "graffiti2": concrete(t, rgb(94, 92, 90), 0, false); marks(t, v + 3); return true;
            default: return false;
        }
    }

    /**
     * Doubles a 32 x 32 tile to 64 x 64 for the materials 0.6 did not redraw. Edges stay edges (the Scale2x rule: a
     * corner is rounded only where its two neighbours agree and the opposite ones do not), and each new pixel takes
     * a touch of its own tone so a doubled flat does not read as blocks.
     */
    static Tex upscale(Tex s, int seed) {
        int n = s.n;
        Tex o = new Tex(n * 2, seed);
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            int p = s.get(x, y), a = s.get(x, y - 1), b = s.get(x + 1, y), c = s.get(x - 1, y), d = s.get(x, y + 1);
            int e0 = p, e1 = p, e2 = p, e3 = p;
            if (c != b && a != d) { e0 = c == a ? c : p; e1 = a == b ? b : p; e2 = d == c ? c : p; e3 = b == d ? b : p; }
            int[] e = {e0, e1, e2, e3};
            for (int k = 0; k < 4; k++) {
                int px = x * 2 + (k & 1), py = y * 2 + (k >> 1), col = e[k];
                if (Tex.a(col) != 0) col = shade(col, 1 + (Rng.hash(px, py, 3, seed) - 0.5f) * 0.045f);
                o.set(px, py, col);
            }
        }
        return o;
    }
}
