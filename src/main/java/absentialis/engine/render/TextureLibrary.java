package absentialis.engine.render;

import static absentialis.engine.render.Tex.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Procedural HD pixel-art material library (32x32 per voxel face). Every block face texture is
 * painted here from primitives: material read (concrete, brick, metal, wood, fabric...) plus
 * wear, grime, stains and highlights. Several materials are generated in multiple variants which
 * the world shader selects per-block by position hash.
 */
public final class TextureLibrary {
    /** The size the 0.5 painters in this file work at; anything they paint is doubled to {@link #SIZE}. */
    public static final int N = 32;

    /** A face of foliage in one tone: a shaded under-layer with holes, then leaf-shaped dabs of the tone and its highlights over it. */
    private static void leafTex(Tex t, int tone) {
        t.clear();
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            // the gaps: coherent, so they read as openings between sprays of leaves and not as static
            float n = absentialis.engine.util.Noise.fbmTiled(x / 8f, y / 8f, 4, 2, t.seed * 31 + 7);
            if (n > 0.36f) t.set(x, y, shade(tone, 0.52f + 0.2f * t.rng.nextFloat()));
        }
        for (int i = 0; i < 150; i++) {
            int x = t.rng.nextInt(N), y = t.rng.nextInt(N);
            float k = 0.78f + t.rng.nextFloat() * 0.5f;
            int c = shade(tone, k), hi = shade(tone, k * 1.18f);
            // a leaf: three across, lit along its upper edge
            t.set(x, y, hi); t.set(x + 1, y, hi); t.set(x + 2, y + 1, c); t.set(x, y + 1, c); t.set(x + 1, y + 1, c); t.set(x + 1, y + 2, shade(c, 0.82f));
        }
    }
    /** Atlas tile size (0.6): 64 x 64 per voxel face. The redrawn materials live in {@link TextureHD}. */
    public static final int SIZE = TextureHD.N;

    /** Names with more than one generated variant. */
    private static final Map<String, Integer> VARIANTS = new LinkedHashMap<>();
    static {
        for (String s : new String[]{"concrete", "concrete_dark", "asphalt", "sidewalk", "brick_red", "brick_brown", "brick_yellow",
                "brick_white", "plaster", "plaster_blue", "plaster_ochre", "stone_block", "cobble", "wood_planks", "grass_top", "dirt",
                "roof_tar", "corrugated", "corrugated_rust", "metal_rust", "concrete_floor", "rubble", "gravel", "dead_grass_top",
                "concrete_panel", "wallpaper_floral", "wallpaper_stripe", "carpet_gray", "linoleum", "office_panel", "tile_white",
                "sewer_brick", "roof_tiles", "stone_dark", "parquet"})
            VARIANTS.put(s, 3);
        VARIANTS.put("ceiling_white", 2);
        VARIANTS.put("graffiti", 3);
        VARIANTS.put("graffiti2", 3);
        VARIANTS.put("poster", 3);
        VARIANTS.put("sign_shop", 4);
    }

    public static int variants(String name) { return VARIANTS.getOrDefault(name, 1); }

    public static Tex paint(String name, int variant) {
        int seed = name.hashCode() * 31 + variant * 7919;
        Tex hd = new Tex(SIZE, seed);
        if (TextureHD.draw(hd, name, variant)) return hd;
        Tex t = new Tex(N, seed);
        if (!draw(t, name, variant)) return null;
        return TextureHD.upscale(t, seed);
    }

    /** Quarter-turns a painted tile so markings follow roads that run along x. */
    private static void transpose(Tex t) {
        int n = t.n;
        int[] o = t.px.clone();
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) t.px[y * n + x] = o[x * n + y];
    }

    private static boolean draw(Tex t, String name, int v) {
        switch (name) {
            // ======================================================= ground
            case "bedrock": t.fill(rgb(40, 40, 42)).noise(4, 0.3f, 1).grain(0.15f); return true;
            case "dirt": t.fill(rgb(105, 78, 52)).noise(6, 0.18f, 1).speckle(rgb(80, 60, 40), 0.06f).speckle(rgb(130, 110, 85), 0.04f).grain(0.08f); return true;
            case "mud": t.fill(rgb(72, 58, 40)).noise(8, 0.2f, 2).stains(4, 0.25f, 5).speckle(rgb(90, 80, 60), 0.03f).grain(0.06f); return true;
            case "grass_top": t.fill(rgb(78, 104, 48)).noise(5, 0.2f, 3);
                for (int i = 0; i < 160; i++) { int x = t.rng.nextInt(N), y = t.rng.nextInt(N); t.set(x, y, rgb(95 + t.rng.nextInt(30), 125 + t.rng.nextInt(30), 55)); t.set(x, y + 1, rgb(60, 85, 38)); }
                t.speckle(rgb(110, 100, 60), 0.02f).grain(0.06f); return true;
            case "grass_side": draw(t, "dirt", 0);
                for (int x = 0; x < N; x++) { int d = 4 + t.rng.nextInt(5); for (int y = 0; y < d; y++) t.set(x, y, shade(rgb(78, 104, 48), 0.85f + t.rng.nextFloat() * 0.3f)); }
                return true;
            case "dead_grass_top": t.fill(rgb(128, 116, 72)).noise(5, 0.22f, 4);
                for (int i = 0; i < 140; i++) { int x = t.rng.nextInt(N), y = t.rng.nextInt(N); t.set(x, y, rgb(150 + t.rng.nextInt(30), 135, 85)); t.set(x, y + 1, rgb(95, 85, 55)); }
                t.grain(0.06f); return true;
            case "dead_grass_side": draw(t, "dirt", 0);
                for (int x = 0; x < N; x++) { int d = 3 + t.rng.nextInt(5); for (int y = 0; y < d; y++) t.set(x, y, shade(rgb(128, 116, 72), 0.85f + t.rng.nextFloat() * 0.3f)); }
                return true;
            case "gravel": t.fill(rgb(110, 106, 100));
                for (int i = 0; i < 90; i++) t.disc(t.rng.nextFloat() * N, t.rng.nextFloat() * N, 1 + t.rng.nextFloat() * 1.8f, shade(rgb(120, 116, 108), 0.6f + t.rng.nextFloat() * 0.7f), 0);
                t.grain(0.1f); return true;
            case "sand": t.fill(rgb(190, 170, 125)).noise(6, 0.1f, 5).grain(0.08f); return true;
            case "asphalt": t.fill(rgb(58, 58, 60)).noise(3, 0.12f, 6).speckle(rgb(85, 85, 85), 0.08f).speckle(rgb(40, 40, 42), 0.08f).stains(2, 0.15f, 7).grain(0.07f);
                if (v == 1) t.crack(rgb(30, 30, 30), 30);
                if (v == 2) { t.stains(2, 0.3f, 6); } return true;
            case "asphalt_cracked": draw(t, "asphalt", 0); t.crack(rgb(25, 25, 25), 50).crack(rgb(25, 25, 25), 40).crack(rgb(30, 30, 30), 30);
                for (int i = 0; i < 12; i++) t.set(t.rng.nextInt(N), t.rng.nextInt(N), rgb(70, 90, 40)); return true;
            case "road_line": draw(t, "asphalt", 0); for (int y = 0; y < N; y++) for (int x = 13; x < 19; x++) if (t.rng.nextFloat() > 0.1f) t.set(x, y, rgb(200, 170, 50)); t.grain(0.05f); return true;
            case "road_edge": draw(t, "asphalt", 1); for (int y = 0; y < N; y++) for (int x = N / 2 - 2; x < N / 2 + 2; x++) if (t.rng.nextFloat() > 0.15f) t.set(x, y, rgb(205, 205, 200)); t.grain(0.05f); return true;
            case "road_trunk": draw(t, "asphalt", 0); for (int y = 0; y < N; y++) for (int x = N / 2 - 5; x < N / 2 + 5; x++) if ((x < N / 2 - 1 || x >= N / 2 + 1) && t.rng.nextFloat() > 0.06f) t.set(x, y, rgb(214, 176, 44)); t.grain(0.05f); return true;
            case "road_line_x": draw(t, "road_line", v); transpose(t); return true;
            case "road_edge_x": draw(t, "road_edge", v); transpose(t); return true;
            case "road_trunk_x": draw(t, "road_trunk", v); transpose(t); return true;
            case "crosswalk": draw(t, "asphalt", 2); for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) if ((x / 8) % 2 == 0 && t.rng.nextFloat() > 0.12f) t.set(x, y, rgb(210, 210, 205)); t.grain(0.05f); return true;
            case "sidewalk": t.tiles(rgb(150, 147, 140), rgb(144, 141, 135), rgb(105, 103, 98), 16).noise(4, 0.08f, 8).stains(2, 0.12f, 5).grain(0.05f);
                if (v == 1) t.crack(rgb(90, 88, 84), 20); if (v == 2) t.speckle(rgb(60, 60, 60), 0.02f); return true;
            case "curb": t.fill(rgb(160, 158, 152)).noise(4, 0.08f, 9).rect(0, 0, N, 3, rgb(185, 183, 178)).rect(0, N - 3, N, 3, rgb(110, 108, 104)).grain(0.06f); return true;
            case "cobble": t.fill(rgb(70, 66, 60));
                for (int y = 0; y < N; y += 8) for (int x = (y / 8 % 2) * 4 - 4; x < N; x += 8) {
                    int col = shade(rgb(125, 118, 108), 0.8f + t.rng.nextFloat() * 0.4f);
                    for (int yy = 1; yy < 7; yy++) for (int xx = 1; xx < 7; xx++) {
                        float d = (float) Math.hypot(xx - 3.5, yy - 3.5);
                        if (d < 3.6f) t.set(x + xx, y + yy, shade(col, 1.15f - d * 0.08f - (yy > 4 ? 0.1f : 0)));
                    }
                }
                t.grain(0.08f); if (v > 0) t.stains(3, 0.2f, 4); return true;
            case "manhole": draw(t, "asphalt", 0); t.disc(16, 16, 14, rgb(60, 58, 55), 0);
                for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) { double d = Math.hypot(x - 15.5, y - 15.5); if (d < 13 && ((x + y) % 4 == 0 || (x - y + 64) % 4 == 0)) t.set(x, y, rgb(80, 78, 74)); if (d > 12.5 && d < 14) t.set(x, y, rgb(40, 40, 40)); }
                t.rust(0.3f, 3).grain(0.06f); return true;
            case "drain": t.fill(rgb(40, 40, 40)); for (int x = 0; x < N; x += 4) t.rect(x, 0, 2, N, rgb(80, 80, 80)); t.outline(0, 0, N, N, rgb(90, 90, 90)); t.rust(0.3f, 1); return true;
            case "rubble": t.fill(rgb(95, 92, 88));
                for (int i = 0; i < 40; i++) { int x = t.rng.nextInt(N), y = t.rng.nextInt(N), s = 2 + t.rng.nextInt(5); int c = shade(rgb(150, 146, 138), 0.6f + t.rng.nextFloat() * 0.6f); t.rect(x, y, s, s - 1, c).rectShade(x, y + s - 2, s, 1, 0.7f); }
                if (v == 1) for (int i = 0; i < 8; i++) t.rect(t.rng.nextInt(N), t.rng.nextInt(N), 3, 2, rgb(150, 70, 50));
                if (v == 2) t.line(2, 5, 25, 9, rgb(110, 60, 40));
                t.grain(0.1f); return true;
            case "rubble_brick": draw(t, "rubble", 1); for (int i = 0; i < 25; i++) t.rect(t.rng.nextInt(N), t.rng.nextInt(N), 4, 2, shade(rgb(150, 70, 50), 0.7f + t.rng.nextFloat() * 0.4f)); return true;
            case "ash": t.fill(rgb(60, 58, 56)).noise(4, 0.25f, 4).speckle(rgb(30, 28, 26), 0.1f).speckle(rgb(140, 70, 30), 0.01f).grain(0.1f); return true;
            case "rail": draw(t, "rail_sleeper", 0); t.rect(6, 0, 3, N, rgb(105, 100, 98)).rect(23, 0, 3, N, rgb(105, 100, 98)).rect(6, 0, 1, N, rgb(170, 168, 165)).rect(23, 0, 1, N, rgb(170, 168, 165)); t.rust(0.15f, 5); return true;
            case "rail_sleeper": draw(t, "gravel", 0); for (int y = 2; y < N; y += 12) t.rect(0, y, N, 6, rgb(80, 60, 45)).noise(3, 0.05f, 2); t.grain(0.06f); return true;
            case "concrete_floor": t.fill(rgb(130, 128, 122)).noise(5, 0.1f, 11).stains(3, 0.15f, 6).speckle(rgb(110, 108, 104), 0.05f).grain(0.05f);
                if (v == 1) t.rect(0, 0, N, 1, rgb(100, 98, 94)); if (v == 2) t.crack(rgb(95, 93, 89), 25); return true;
            case "blood_floor": draw(t, "concrete_floor", 0); t.blood(6); return true;
            case "oil_floor": draw(t, "concrete_floor", 0); t.stains(4, 0.5f, 7); return true;
            case "toxic_ground": t.fill(rgb(70, 80, 45)).noise(4, 0.25f, 3); for (int i = 0; i < 6; i++) t.disc(t.rng.nextFloat() * N, t.rng.nextFloat() * N, 2 + t.rng.nextFloat() * 3, rgb(140, 200, 40), 1.5f); t.grain(0.08f); return true;

            // ======================================================= walls
            case "concrete": t.fill(rgb(142, 140, 134)).noise(6, 0.08f, 12).speckle(rgb(118, 116, 112), 0.05f).speckle(rgb(160, 158, 152), 0.03f).streaks(5, 0.12f).grain(0.04f);
                for (int i = 0; i < 4; i++) t.set(t.rng.nextInt(N), t.rng.nextInt(N), rgb(60, 60, 60));
                if (v == 1) t.crack(rgb(95, 93, 90), 18); if (v == 2) t.stains(3, 0.15f, 6); return true;
            case "concrete_dark": t.fill(rgb(92, 90, 88)).noise(5, 0.1f, 13).speckle(rgb(70, 70, 70), 0.06f).streaks(4, 0.15f).grain(0.05f); if (v == 1) t.crack(rgb(55, 55, 55), 20); return true;
            case "concrete_panel": t.fill(rgb(165, 162, 155)).noise(6, 0.06f, 14).grain(0.04f).rect(0, N - 1, N, 1, rgb(110, 108, 104)).rect(N - 1, 0, 1, N, rgb(110, 108, 104));
                t.rect(3, 3, 1, 1, rgb(100, 100, 100)).rect(N - 5, 3, 1, 1, rgb(100, 100, 100)).streaks(4, 0.1f); if (v == 2) t.stains(2, 0.15f, 6); return true;
            case "concrete_stained": draw(t, "concrete", 0); t.streaks(12, 0.25f).stains(5, 0.25f, 6); return true;
            case "graffiti": draw(t, v == 2 ? "brick_red" : "concrete", v); paintGraffiti(t, v); return true;
            case "graffiti2": draw(t, "concrete_dark", 0); paintGraffiti(t, v + 3); return true;
            case "brick_red": t.bricks(v == 2 ? rgb(150, 70, 52) : rgb(140, 62, 46), rgb(120, 112, 100), 16, 8, 0.12f).streaks(4, 0.12f).grain(0.04f); if (v == 1) t.stains(3, 0.15f, 5); return true;
            case "brick_brown": t.bricks(rgb(110, 72, 52), rgb(105, 98, 90), 16, 8, 0.12f).streaks(5, 0.12f).grain(0.04f); if (v == 1) t.stains(3, 0.2f, 6); return true;
            case "brick_yellow": t.bricks(rgb(185, 160, 105), rgb(135, 128, 115), 16, 8, 0.1f).streaks(5, 0.1f).grain(0.04f); if (v == 2) t.stains(3, 0.15f, 5); return true;
            case "brick_white": t.bricks(rgb(205, 200, 190), rgb(160, 155, 148), 16, 8, 0.05f).streaks(6, 0.12f).grain(0.03f);
                if (v >= 1) for (int i = 0; i < 8; i++) t.rect(t.rng.nextInt(N), t.rng.nextInt(N), 3, 2, rgb(140, 70, 52)); return true;
            case "brick_burnt": t.bricks(rgb(70, 45, 38), rgb(50, 46, 42), 16, 8, 0.2f).stains(6, 0.4f, 7).grain(0.08f); return true;
            case "plaster": t.fill(rgb(200, 190, 170)).noise(5, 0.07f, 15).streaks(6, 0.12f).grain(0.04f); if (v == 1) t.stains(3, 0.12f, 6); if (v == 2) chip(t, rgb(150, 75, 55)); return true;
            case "plaster_blue": t.fill(rgb(120, 150, 165)).noise(5, 0.08f, 16).streaks(6, 0.14f).grain(0.04f); if (v >= 1) chip(t, rgb(190, 185, 170)); return true;
            case "plaster_ochre": t.fill(rgb(195, 150, 85)).noise(5, 0.08f, 17).streaks(6, 0.14f).grain(0.04f); if (v >= 1) chip(t, rgb(150, 75, 55)); return true;
            case "plaster_peel": draw(t, "plaster", 0); for (int i = 0; i < 5; i++) chip(t, rgb(140, 66, 48)); t.stains(4, 0.2f, 6); return true;
            case "stone_block": t.fill(rgb(95, 92, 85));
                for (int row = 0; row < 2; row++) for (int col = -1; col < 2; col++) {
                    int x0 = col * 16 + (row % 2) * 8 + 1, y0 = row * 16 + 1;
                    int c = shade(rgb(150, 143, 128), 0.85f + t.rng.nextFloat() * 0.25f);
                    t.rect(x0, y0, 15, 15, c).bevel(x0, y0, 15, 15, 1.15f, 0.75f);
                }
                t.noise(4, 0.1f, 18).grain(0.06f); if (v == 1) t.stains(4, 0.2f, 6); if (v == 2) t.rust(0.15f, 9); return true;
            case "stone_dark": draw(t, "stone_block", v); for (int i = 0; i < t.px.length; i++) t.px[i] = shade(t.px[i], 0.6f); t.streaks(8, 0.2f); return true;
            case "stone_carved": draw(t, "stone_block", 0); t.outline(4, 4, 24, 24, rgb(80, 76, 70)).outline(7, 7, 18, 18, rgb(175, 168, 152)); t.circle(16, 16, 4, rgb(110, 105, 95)); return true;
            case "marble": t.fill(rgb(220, 216, 208)).noise(3, 0.05f, 19); for (int i = 0; i < 4; i++) t.crack(rgb(170, 165, 160), 40); t.grain(0.02f); return true;
            case "granite": t.fill(rgb(110, 100, 100)).speckle(rgb(160, 150, 150), 0.15f).speckle(rgb(50, 45, 45), 0.15f).grain(0.05f).rect(0, N - 1, N, 1, rgb(70, 65, 65)); return true;
            case "metal_plate": t.fill(rgb(118, 122, 126)).noise(6, 0.05f, 20).rivets(16, rgb(150, 155, 160)).outline(0, 0, N, N, rgb(85, 88, 92)).stains(2, 0.1f, 6).grain(0.03f);
                for (int i = 0; i < 6; i++) { int x = t.rng.nextInt(N), y = t.rng.nextInt(N); t.line(x, y, x + 4, y + 1, rgb(150, 154, 158)); } return true;
            case "metal_rust": draw(t, "metal_plate", 0); t.rust(0.45f + v * 0.1f, v).streaks(8, 0.2f); return true;
            case "steel_beam": t.fill(rgb(70, 80, 90)).rect(0, 0, N, 4, rgb(95, 105, 115)).rect(0, N - 4, N, 4, rgb(55, 62, 70)).rect(13, 0, 6, N, rgb(80, 90, 100)).rivets(8, rgb(110, 118, 125)).rust(0.25f, 2).grain(0.04f); return true;
            case "corrugated": t.corrugate(rgb(140, 145, 148), 4).noise(5, 0.06f, 21).streaks(5, 0.15f).grain(0.03f); if (v >= 1) t.rust(0.25f * v, v); return true;
            case "corrugated_rust": t.corrugate(rgb(130, 85, 55), 4).rust(0.7f, v).streaks(6, 0.2f).grain(0.05f); return true;
            case "corrugated_blue": t.corrugate(rgb(70, 100, 135), 4).streaks(5, 0.15f).rust(0.2f, 3).grain(0.03f); return true;
            case "wood_planks": t.planks(v == 2 ? rgb(150, 110, 70) : rgb(160, 120, 78), 4, true); if (v == 1) t.stains(2, 0.15f, 5); return true;
            case "wood_dark": t.planks(rgb(92, 64, 42), 4, false); return true;
            case "wood_green": t.planks(rgb(80, 115, 85), 4, true); chip(t, rgb(140, 100, 65)); return true;
            case "boarded": {
                // a window boarded shut: weathered planks nailed edge to edge over a dark gap, not stripes on black
                t.fill(rgb(52, 40, 30));
                int rows = Math.max(4, N / 6), bh = N / rows;
                for (int i = 0; i < rows; i++) {
                    int col = shade(rgb(132, 100, 66), 0.78f + t.rng.nextFloat() * 0.3f), y = i * bh;
                    t.rect(0, y, N, bh - 1, col);
                    for (int x = 0; x < N; x++) { t.set(x, y, shade(col, 1.12f)); t.set(x, y + bh - 2, shade(col, 0.72f)); if (t.rng.nextFloat() < 0.18f) t.set(x, y + 1 + t.rng.nextInt(Math.max(1, bh - 3)), shade(col, 0.85f)); }
                    t.set(2, y + bh / 2 - 1, rgb(60, 60, 64)); t.set(N - 3, y + bh / 2 - 1, rgb(60, 60, 64));
                }
                t.grain(0.05f); return true;
            }
            case "road_sign": {
                // district direction board on a slim post (cut-out)
                t.clear();
                t.rect(N / 2 - 1, N / 2, 2, N / 2, rgb(120, 124, 128));
                t.rect(1, 2, N - 2, N / 2 - 1, rgb(28, 70, 130)).outline(1, 2, N - 2, N / 2 - 1, rgb(232, 232, 226));
                // 0.6: no rows of bars standing for writing - the board carries an arrow and nothing else
                t.rect(5, 5 + N / 8, N - 14, Math.max(1, N / 16), rgb(232, 232, 226));
                t.line(N - 7, 5, N - 4, 5 + N / 8, rgb(232, 232, 226)).line(N - 4, 5 + N / 8, N - 7, 5 + N / 4, rgb(232, 232, 226));
                return true;
            }
            case "char_wood": t.planks(rgb(45, 35, 30), 4, false); t.speckle(rgb(20, 18, 16), 0.1f).speckle(rgb(160, 70, 30), 0.006f); return true;
            case "log_top": t.fill(rgb(150, 115, 75)); for (int r = 14; r > 0; r -= 3) t.disc(16, 16, r, shade(rgb(150, 115, 75), r % 2 == 0 ? 0.85f : 1f), 0); t.outline(0, 0, N, N, rgb(70, 50, 35)).grain(0.06f); return true;
            case "bark": t.fill(rgb(80, 60, 42)); for (int x = 0; x < N; x += 3) for (int y = 0; y < N; y++) if (t.rng.nextFloat() < 0.7f) t.set(x + (y / 5) % 2, y, rgb(55, 40, 30)); t.grain(0.1f); return true;
            case "office_panel": t.fill(rgb(175, 170, 160)).noise(8, 0.04f, 22).rect(0, 0, 1, N, rgb(130, 125, 118)).rect(0, N - 3, N, 3, rgb(90, 85, 80)).grain(0.03f); if (v == 1) t.stains(2, 0.12f, 6); if (v == 2) t.rect(10, 8, 12, 9, rgb(230, 225, 210)).outline(10, 8, 12, 9, rgb(150, 140, 120)); return true;
            case "sewer_brick": t.bricks(rgb(90, 85, 70), rgb(55, 55, 45), 12, 6, 0.15f);
                for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) { float n2 = absentialis.engine.util.Noise.tiled(x / 6f, y / 6f, 6, t.seed + 3); if (n2 > 0.55f + y * 0.006f) t.blend(x, y, rgb(60, 90, 40), 0.6f); }
                t.streaks(8, 0.25f).grain(0.06f); return true;
            case "catacomb": t.bricks(rgb(120, 110, 95), rgb(70, 65, 58), 10, 6, 0.2f).stains(5, 0.3f, 6).grain(0.08f); return true;
            case "bones": t.fill(rgb(60, 55, 48));
                for (int i = 0; i < 9; i++) { int x = t.rng.nextInt(N), y = t.rng.nextInt(N); if (t.rng.chance(0.4)) { t.disc(x, y, 4, rgb(205, 195, 170), 0); t.disc(x - 1.5f, y, 1, rgb(30, 25, 20), 0); t.disc(x + 1.5f, y, 1, rgb(30, 25, 20), 0); } else { t.line(x, y, x + 6, y + 2, rgb(200, 190, 165)); t.line(x, y + 1, x + 6, y + 3, rgb(170, 160, 140)); } }
                t.grain(0.06f); return true;
            case "biomass": t.fill(rgb(110, 40, 45)).noise(4, 0.3f, 23);
                for (int i = 0; i < 8; i++) t.disc(t.rng.nextFloat() * N, t.rng.nextFloat() * N, 2 + t.rng.nextFloat() * 3, rgb(170, 90, 80), 2);
                for (int i = 0; i < 5; i++) t.crack(rgb(60, 15, 20), 25); t.speckle(rgb(190, 200, 90), 0.01f).grain(0.08f); return true;
            case "hesco": t.fill(rgb(150, 135, 100)).noise(5, 0.12f, 24); for (int x = 0; x < N; x += 6) t.rect(x, 0, 1, N, rgb(80, 80, 75)); for (int y = 0; y < N; y += 6) t.rect(0, y, N, 1, rgb(80, 80, 75)); t.grain(0.06f); return true;
            case "hesco_top": t.fill(rgb(110, 95, 70)).noise(4, 0.2f, 25).outline(0, 0, N, N, rgb(80, 80, 75)).grain(0.08f); return true;
            case "sandbag": t.fill(rgb(70, 62, 48));
                for (int row = 0; row < 4; row++) for (int c = -1; c < 3; c++) { int x0 = c * 14 + (row % 2) * 7, y0 = row * 8; int col = shade(rgb(165, 148, 108), 0.85f + t.rng.nextFloat() * 0.2f);
                    for (int y = 0; y < 7; y++) for (int x = 0; x < 13; x++) { float e = Math.min(Math.min(x, 12 - x), Math.min(y, 6 - y)); if (e >= 0) t.set(x0 + x, y0 + y, shade(col, 0.7f + Math.min(1, e / 2f) * 0.35f - (y > 4 ? 0.1f : 0))); } }
                t.grain(0.08f); return true;
            case "barrier": t.fill(rgb(175, 172, 165)).noise(5, 0.08f, 26); for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) if (y > 8 && y < 18 && ((x + y) / 6) % 2 == 0) t.set(x, y, rgb(200, 60, 40)); t.stains(3, 0.15f, 6).grain(0.04f); return true;
            case "hazard_stripe": t.diagStripes(rgb(220, 180, 40), rgb(30, 30, 30), 6).grain(0.06f).stains(3, 0.2f, 6); return true;
            case "container_top": t.fill(rgb(100, 100, 100)).corrugate(rgb(110, 105, 100), 6).rust(0.4f, 4).grain(0.05f); return true;
            case "container_red": container(t, rgb(150, 50, 40)); return true;
            case "container_blue": container(t, rgb(45, 80, 130)); return true;
            case "container_green": container(t, rgb(55, 100, 60)); return true;
            case "container_orange": container(t, rgb(190, 110, 40)); return true;

            // ======================================================= floors & interior
            case "tile_white": t.tiles(rgb(215, 215, 210), rgb(208, 208, 204), rgb(150, 150, 145), 8).stains(2, 0.12f, 5); if (v == 1) t.crack(rgb(140, 140, 135), 15); if (v == 2) t.blood(1); return true;
            case "tile_green": t.tiles(rgb(130, 170, 150), rgb(125, 165, 145), rgb(90, 110, 100), 8).stains(3, 0.15f, 6); return true;
            case "tile_checker": t.tiles(rgb(210, 205, 195), rgb(50, 50, 55), rgb(120, 120, 115), 8).stains(2, 0.12f, 5); return true;
            case "tile_gray": t.tiles(rgb(140, 140, 140), rgb(130, 130, 132), rgb(95, 95, 95), 16).stains(2, 0.12f, 6); return true;
            case "carpet_red": t.fill(rgb(120, 35, 40)).noise(2, 0.08f, 27).grain(0.12f).outline(2, 2, N - 4, N - 4, rgb(180, 140, 60)).stains(2, 0.15f, 5); return true;
            case "carpet_blue": t.fill(rgb(45, 60, 105)).noise(2, 0.08f, 28).grain(0.12f).stains(2, 0.15f, 6); return true;
            case "carpet_gray": t.fill(rgb(100, 100, 105)).noise(2, 0.08f, 29).grain(0.12f); for (int y = 0; y < N; y += 4) for (int x = (y / 4 % 2) * 2; x < N; x += 4) t.mul(x, y, 0.85f); if (v > 0) t.stains(v * 2, 0.15f, 6); return true;
            case "parquet": t.fill(rgb(140, 95, 55));
                for (int by = 0; by < N; by += 8) for (int bx = 0; bx < N; bx += 8) { boolean h = ((bx + by) / 8) % 2 == 0; int c = shade(rgb(150, 102, 60), 0.85f + t.rng.nextFloat() * 0.25f);
                    for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) { int k = h ? y : x; t.set(bx + x, by + y, shade(c, (k % 4 == 0) ? 0.7f : 1 + ((h ? x : y) % 3) * 0.03f)); } }
                t.grain(0.05f); if (v == 1) t.stains(3, 0.2f, 5); return true;
            case "linoleum": t.fill(rgb(175, 175, 160)).noise(4, 0.06f, 30).speckle(rgb(140, 140, 130), 0.1f).grain(0.03f); if (v == 1) t.tiles(rgb(180, 178, 165), rgb(170, 168, 155), rgb(150, 148, 138), 16); if (v == 2) t.stains(3, 0.2f, 6); return true;
            case "wallpaper_floral": {
                int base = v == 1 ? rgb(170, 160, 120) : (v == 2 ? rgb(150, 165, 150) : rgb(185, 160, 150));
                t.fill(base).noise(6, 0.05f, 31);
                for (int y = 4; y < N; y += 16) for (int x = 4; x < N; x += 16) { int ox = ((y / 16) % 2) * 8; int fl = shade(base, 0.7f); t.circle(x + ox, y, 2, fl); t.set(x + ox, y, rgb(180, 60, 60)); t.line(x + ox, y + 3, x + ox, y + 7, rgb(80, 110, 70)); }
                t.streaks(4, 0.1f).grain(0.03f); return true; }
            case "wallpaper_stripe": { int base = v == 1 ? rgb(120, 140, 120) : (v == 2 ? rgb(140, 120, 150) : rgb(190, 180, 150));
                t.vstripes(base, shade(base, 0.85f), 4).noise(6, 0.05f, 32).streaks(5, 0.12f).grain(0.03f); return true; }
            case "wallpaper_torn": draw(t, "wallpaper_floral", 0); for (int i = 0; i < 4; i++) chip(t, rgb(180, 175, 165)); t.stains(5, 0.25f, 7); return true;
            case "ceiling_tile": t.tiles(rgb(205, 202, 192), rgb(200, 198, 188), rgb(150, 148, 140), 16).speckleShade(0.2f, 0.92f).stains(1, 0.25f, 6); return true;
            case "roof_tar": t.fill(rgb(55, 55, 58)).noise(4, 0.15f, 33).speckle(rgb(85, 85, 85), 0.06f).stains(3, 0.2f, 7).grain(0.07f); if (v == 1) t.rect(0, 15, N, 2, rgb(40, 40, 42)); if (v == 2) for (int i = 0; i < 6; i++) t.rect(t.rng.nextInt(N), t.rng.nextInt(N), 2, 2, rgb(90, 110, 60)); return true;
            case "roof_tiles": t.fill(rgb(90, 40, 30));
                for (int row = 0; row < 4; row++) for (int c = -1; c < 5; c++) { int x0 = c * 8 + (row % 2) * 4, y0 = row * 8; int col = shade(v == 1 ? rgb(130, 75, 50) : rgb(155, 70, 48), 0.85f + t.rng.nextFloat() * 0.25f);
                    for (int y = 0; y < 8; y++) for (int x = 0; x < 7; x++) { float d = (float) Math.abs(x - 3) / 3.5f; t.set(x0 + x, y0 + y, shade(col, 1.15f - d * 0.3f - y * 0.03f)); } }
                t.stains(3, 0.2f, 6).grain(0.05f); return true;
            case "roof_slate": t.fill(rgb(55, 58, 65)); for (int row = 0; row < 4; row++) for (int c = -1; c < 3; c++) { int x0 = c * 12 + (row % 2) * 6, y0 = row * 8; t.rect(x0 + 1, y0 + 1, 11, 7, shade(rgb(75, 80, 90), 0.8f + t.rng.nextFloat() * 0.3f)).rectShade(x0 + 1, y0 + 7, 11, 1, 0.6f); } t.grain(0.05f).stains(3, 0.15f, 6); return true;
            case "roof_metal": t.corrugate(rgb(120, 125, 128), 6).rust(0.35f, 2).grain(0.04f); return true;

            // ======================================================= glass / liquid / cutout
            case "glass": glass(t, argb(70, 170, 200, 215), false); return true;
            case "glass_dirty": glass(t, argb(140, 140, 150, 130), false); t.stains(4, 0.25f, 7); return true;
            case "glass_cracked": glass(t, argb(90, 170, 200, 215), true); return true;
            case "glass_blue": glass(t, argb(150, 60, 110, 150), false); for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) if (x == 0 || y == 0) t.set(x, y, rgb(70, 75, 80)); return true;
            case "glass_stained": t.fill(argb(210, 60, 60, 60));
                int[] cols = {argb(200, 180, 40, 40), argb(200, 40, 70, 170), argb(200, 210, 170, 40), argb(200, 50, 140, 70)};
                for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) { int cell = ((x / 8) * 3 + (y / 8) * 5) % 4; if (x % 8 != 0 && y % 8 != 0) t.set(x, y, cols[cell]); else t.set(x, y, argb(255, 30, 30, 30)); }
                return true;
            case "water": t.fill(argb(150, 50, 80, 95)).noise(4, 0.15f, 34); for (int i = 0; i < 10; i++) { int x = t.rng.nextInt(N), y = t.rng.nextInt(N); t.line(x, y, x + 4, y, argb(170, 120, 150, 160)); } return true;
            case "sewage": t.fill(argb(200, 70, 72, 45)).noise(4, 0.2f, 35); t.speckle(argb(210, 100, 95, 60), 0.05f); return true;
            case "toxic": t.fill(argb(190, 110, 180, 40)).noise(3, 0.25f, 36); for (int i = 0; i < 8; i++) t.disc(t.rng.nextFloat() * N, t.rng.nextFloat() * N, 1 + t.rng.nextFloat() * 2, argb(230, 200, 255, 120), 1); return true;
            case "chainlink": t.clear(); for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) if ((x + y) % 8 == 0 || (x - y + 64) % 8 == 0) t.set(x, y, rgb(150, 155, 160)); t.outline(0, 0, N, 1, rgb(110, 115, 120)); return true;
            case "iron_fence": t.clear(); for (int x = 2; x < N; x += 8) t.rect(x, 0, 2, N, rgb(40, 40, 42)); t.rect(0, 3, N, 2, rgb(40, 40, 42)).rect(0, N - 6, N, 2, rgb(40, 40, 42)); for (int x = 2; x < N; x += 8) t.rect(x - 1, 0, 4, 2, rgb(55, 55, 58)); return true;
            case "barbed": t.clear(); for (int k = 0; k < 3; k++) { int y0 = 6 + k * 10; for (int x = 0; x < N; x++) { int y = y0 + (int) (Math.sin(x * 0.4 + k) * 3); t.set(x, y, rgb(120, 120, 125)); if (x % 6 == 0) { t.set(x, y - 1, rgb(150, 150, 155)); t.set(x, y + 1, rgb(150, 150, 155)); } } } return true;
            case "grate": t.clear(); for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) if (x % 6 < 2 || y % 6 < 2) t.set(x, y, shade(rgb(95, 98, 100), 0.9f + t.rng.nextFloat() * 0.2f)); t.outline(0, 0, N, N, rgb(70, 72, 75)); return true;
            case "scaffold": t.clear(); t.rect(0, 0, 3, N, rgb(150, 150, 155)).rect(N - 3, 0, 3, N, rgb(150, 150, 155)).rect(0, 0, N, 3, rgb(130, 130, 135)).rect(0, N - 3, N, 3, rgb(130, 130, 135)); t.line(2, 2, N - 3, N - 3, rgb(140, 140, 145)); t.line(3, 2, N - 2, N - 3, rgb(140, 140, 145)); return true;
            case "ladder": t.clear(); t.rect(3, 0, 3, N, rgb(110, 115, 120)).rect(N - 6, 0, 3, N, rgb(110, 115, 120)); for (int y = 3; y < N; y += 8) t.rect(3, y, N - 6, 2, rgb(140, 145, 150)); t.rust(0.2f, 1); for (int i = 0; i < t.px.length; i++) if ((t.px[i] >>> 24) != 0xFF) t.px[i] = 0; return true;
            // 0.7, procedural foliage: a leaf face is mostly leaf - overlapping leaf shapes over a darker layer, with gaps the sky shows
            // through (alpha cut-out; depth is written, nothing is blended or sorted). It was a scatter of dots on nothing.
            case "leaves": leafTex(t, rgb(60, 104, 40)); return true;
            case "leaves_light": leafTex(t, rgb(92, 138, 50)); return true;
            case "leaves_dark": leafTex(t, rgb(38, 76, 32)); return true;
            case "leaves_olive": leafTex(t, rgb(112, 122, 44)); return true;
            case "dead_leaves": t.clear(); for (int i = 0; i < 160; i++) { int x = t.rng.nextInt(N), y = t.rng.nextInt(N); int c = shade(rgb(120, 95, 50), 0.65f + t.rng.nextFloat() * 0.6f); t.set(x, y, c); t.set(x + 1, y, c); } return true;
            case "hedge": t.fill(rgb(40, 70, 30)); for (int i = 0; i < 300; i++) { int x = t.rng.nextInt(N), y = t.rng.nextInt(N); t.set(x, y, shade(rgb(65, 105, 45), 0.6f + t.rng.nextFloat() * 0.7f)); } return true;
            case "lattice": t.clear(); t.rect(0, 0, 2, N, rgb(120, 125, 130)).rect(N - 2, 0, 2, N, rgb(120, 125, 130)); t.line(0, 0, N - 1, N - 1, rgb(140, 140, 145)); t.line(N - 1, 0, 0, N - 1, rgb(140, 140, 145)); t.rect(0, 0, N, 2, rgb(120, 125, 130)); return true;
            case "laundry": t.clear(); t.rect(0, 1, N, 1, rgb(60, 60, 60)); { int[] lc = {rgb(200, 60, 60), rgb(220, 220, 210), rgb(60, 90, 160), rgb(200, 180, 60)}; int x = 1; for (int k = 0; k < 4 && x < N - 4; k++) { int w = 5 + t.rng.nextInt(3); t.rect(x, 2, w, 10 + t.rng.nextInt(10), lc[(k + t.seed) & 3]).grain(0.05f); x += w + 2; } } return true;
            case "wires": t.clear(); for (int k = 0; k < 3; k++) { int y0 = 4 + k * 4; for (int x = 0; x < N; x++) t.set(x, y0 + (int) (Math.sin(x * 0.2 + k) * 2), rgb(25, 25, 25)); } return true;
            case "cobweb": t.clear(); for (int a = 0; a < 8; a++) { double ang = a * Math.PI / 4; t.line(16, 16, 16 + (int) (Math.cos(ang) * 15), 16 + (int) (Math.sin(ang) * 15), argb(160, 220, 220, 220)); } for (int r = 4; r < 16; r += 4) for (int a = 0; a < 64; a++) { double ang = a * Math.PI / 32; t.set(16 + (int) (Math.cos(ang) * r), 16 + (int) (Math.sin(ang) * r), argb(130, 220, 220, 220)); } return true;
            case "tall_grass": t.clear(); for (int i = 0; i < 18; i++) { int x = t.rng.nextInt(N), h = 10 + t.rng.nextInt(20); int c = shade(rgb(120, 125, 60), 0.7f + t.rng.nextFloat() * 0.5f); for (int y = 0; y < h; y++) t.set(x + (y > h / 2 ? (i % 2) : 0), N - 1 - y, c); } return true;

            // ======================================================= stairs / doors
            case "stair_top": draw(t, "concrete", 0); t.rect(0, 0, N, 4, rgb(200, 180, 60)).rect(0, 4, N, 1, rgb(80, 80, 80)); return true;
            case "stair_wood_top": draw(t, "wood_planks", 0); t.rect(0, 0, N, 3, rgb(90, 64, 42)); return true;
            case "door_wood": t.planks(rgb(120, 82, 50), 1, false); t.rect(0, 0, 3, N, rgb(70, 50, 35)).rect(N - 3, 0, 3, N, rgb(70, 50, 35)).rect(0, 0, N, 3, rgb(70, 50, 35));
                t.outline(6, 5, 20, 10, rgb(90, 62, 40)).outline(6, 18, 20, 11, rgb(90, 62, 40)); t.rect(23, 16, 3, 2, rgb(190, 170, 90)); t.stains(2, 0.15f, 6); return true;
            case "door_metal": t.fill(rgb(95, 105, 100)).noise(6, 0.05f, 37).outline(0, 0, N, N, rgb(50, 55, 52)).rect(3, 3, N - 6, 2, rgb(120, 130, 125)); t.rect(22, 15, 6, 2, rgb(170, 170, 170)); t.rust(0.25f, 4).grain(0.04f); return true;
            case "door_open": t.clear(); t.rect(0, 0, 3, N, rgb(70, 50, 35)).rect(N - 3, 0, 3, N, rgb(70, 50, 35)).rect(0, 0, N, 3, rgb(70, 50, 35)); return true;
            case "barricade": t.clear(); for (int i = 0; i < 4; i++) { int y = 3 + i * 8; int col = shade(rgb(140, 100, 65), 0.8f + t.rng.nextFloat() * 0.3f); for (int x = 0; x < N; x++) { int yy = y + (x * (i % 2 == 0 ? 1 : -1)) / 10; t.rect(x, yy, 1, 4, col); } } t.rect(4, 0, 3, N, rgb(100, 70, 45)).rect(N - 7, 0, 3, N, rgb(100, 70, 45)); return true;
            case "gate": t.clear(); for (int x = 1; x < N; x += 5) t.rect(x, 0, 2, N, rgb(80, 85, 90)); t.rect(0, 0, N, 3, rgb(70, 75, 80)).rect(0, 14, N, 3, rgb(70, 75, 80)).rect(0, N - 3, N, 3, rgb(70, 75, 80)); t.rust(0.3f, 2); return true;

            // ======================================================= furniture
            case "table_top": t.planks(rgb(130, 90, 55), 2, false); t.outline(0, 0, N, N, rgb(90, 60, 38)); t.stains(2, 0.15f, 5); return true;
            case "table_side": t.clear(); t.rect(0, 0, N, 5, rgb(110, 75, 45)).rect(1, 5, 4, N - 5, rgb(95, 65, 40)).rect(N - 5, 5, 4, N - 5, rgb(95, 65, 40)); return true;
            case "desk_top": t.fill(rgb(115, 85, 60)).noise(6, 0.05f, 38).outline(0, 0, N, N, rgb(80, 58, 40)); t.rect(4, 6, 10, 13, rgb(225, 222, 210)).rect(18, 10, 8, 2, rgb(40, 40, 120)); return true;
            case "desk_side": t.fill(rgb(105, 75, 52)).outline(0, 0, N, N, rgb(70, 50, 35)); t.outline(3, 4, 12, 9, rgb(80, 58, 40)).outline(3, 16, 12, 12, rgb(80, 58, 40)); t.rect(8, 8, 3, 1, rgb(190, 180, 120)).rect(8, 22, 3, 1, rgb(190, 180, 120)); return true;
            case "counter_top": t.fill(rgb(185, 180, 170)).noise(3, 0.06f, 39).speckle(rgb(150, 145, 140), 0.08f).grain(0.03f).rect(0, 0, N, 2, rgb(140, 135, 128)); return true;
            case "counter_side": t.fill(rgb(160, 130, 95)).outline(0, 0, N, N, rgb(110, 85, 60)).outline(3, 6, 12, 22, rgb(130, 100, 70)).outline(17, 6, 12, 22, rgb(130, 100, 70)); t.rect(12, 15, 1, 3, rgb(60, 60, 60)).rect(19, 15, 1, 3, rgb(60, 60, 60)); t.stains(2, 0.12f, 5); return true;
            case "shelf": t.fill(rgb(35, 35, 38)); for (int y = 0; y < N; y += 10) { t.rect(0, y, N, 2, rgb(130, 135, 140)); for (int x = 2; x < N - 3; x += 4 + t.rng.nextInt(3)) { int h = 3 + t.rng.nextInt(5); int[] cc = {rgb(180, 50, 40), rgb(50, 110, 160), rgb(200, 180, 60), rgb(220, 220, 210), rgb(60, 140, 70), rgb(160, 120, 80)}; if (t.rng.chance(0.75)) t.rect(x, y + 10 - h, 3, h, t.rng.pick(cc)); } } t.rect(0, 0, 2, N, rgb(110, 115, 120)).rect(N - 2, 0, 2, N, rgb(110, 115, 120)); return true;
            case "bookshelf": t.fill(rgb(60, 40, 28)); for (int y = 0; y < N; y += 11) { t.rect(0, y, N, 2, rgb(110, 78, 50)); for (int x = 2; x < N - 2; x += 2) { if (t.rng.chance(0.15)) continue; int h = 6 + t.rng.nextInt(3); t.rect(x, y + 11 - h, 2, h, shade(t.rng.pick(new int[]{rgb(120, 40, 35), rgb(40, 60, 100), rgb(50, 90, 50), rgb(150, 120, 70), rgb(90, 50, 90)}), 0.8f + t.rng.nextFloat() * 0.3f)); } } t.rect(0, 0, 2, N, rgb(110, 78, 50)).rect(N - 2, 0, 2, N, rgb(110, 78, 50)); return true;
            case "bed_top": t.fill(rgb(220, 215, 205)).rect(0, 0, N, 9, rgb(235, 232, 225)).rect(0, 12, N, N - 12, rgb(70, 90, 130)); t.noise(3, 0.05f, 40).grain(0.04f).stains(1, 0.2f, 6); return true;
            case "bed_side": t.fill(rgb(95, 68, 45)).rect(0, 0, N, 10, rgb(70, 90, 130)).rect(0, 10, N, 3, rgb(220, 215, 205)); t.grain(0.04f); return true;
            case "hbed_top": t.fill(rgb(225, 228, 230)).rect(0, 0, N, 8, rgb(240, 240, 240)).rect(0, 14, N, N - 14, rgb(150, 190, 200)).stains(2, 0.15f, 5); if (t.rng.chance(0.5)) t.blood(2); return true;
            case "hbed_side": t.clear(); t.rect(0, 0, N, 8, rgb(150, 190, 200)).rect(0, 8, N, 3, rgb(180, 185, 190)).rect(1, 11, 2, N - 11, rgb(170, 175, 180)).rect(N - 3, 11, 2, N - 11, rgb(170, 175, 180)).circle(2, N - 3, 2, rgb(40, 40, 40)).circle(N - 3, N - 3, 2, rgb(40, 40, 40)); return true;
            case "sofa_top": t.fill(rgb(110, 75, 60)).noise(2, 0.08f, 41).grain(0.1f).rect(0, 0, N, 5, rgb(90, 60, 48)).rect(15, 5, 2, N - 5, rgb(90, 60, 48)); return true;
            case "sofa_side": t.fill(rgb(105, 72, 58)).noise(2, 0.08f, 42).grain(0.1f).rect(0, 0, N, 6, rgb(120, 85, 68)).rect(0, N - 4, N, 4, rgb(50, 35, 28)); return true;
            case "chair_top": t.clear(); t.rect(4, 4, N - 8, N - 8, rgb(120, 85, 55)).outline(4, 4, N - 8, N - 8, rgb(85, 60, 40)); return true;
            case "chair_side": t.clear(); t.rect(4, 14, N - 8, 3, rgb(120, 85, 55)).rect(4, 17, 3, N - 17, rgb(95, 65, 42)).rect(N - 7, 17, 3, N - 17, rgb(95, 65, 42)).rect(N - 7, 0, 3, 14, rgb(95, 65, 42)); return true;
            case "stove_top": t.fill(rgb(210, 210, 205)); for (int[] p : new int[][]{{9, 9}, {23, 9}, {9, 23}, {23, 23}}) { t.circle(p[0], p[1], 5, rgb(40, 40, 40)); t.circle(p[0], p[1], 3, rgb(70, 70, 70)); } t.stains(2, 0.2f, 5); return true;
            case "stove_side": t.fill(rgb(210, 210, 205)).rect(3, 9, N - 6, 18, rgb(40, 40, 42)).rect(5, 11, N - 10, 14, rgb(60, 55, 50)).rect(5, 4, N - 10, 2, rgb(150, 150, 150)); t.stains(3, 0.15f, 5); return true;
            case "sink_top": t.fill(rgb(185, 180, 170)).rect(5, 5, 22, 18, rgb(200, 205, 210)).rect(7, 7, 18, 14, rgb(150, 160, 165)).circle(16, 14, 1, rgb(60, 60, 60)).rect(14, 25, 4, 4, rgb(170, 170, 175)); return true;
            case "toilet_top": t.clear(); t.disc(16, 18, 11, rgb(235, 235, 230), 0); t.disc(16, 18, 7, rgb(170, 190, 195), 0); t.rect(6, 0, 20, 6, rgb(235, 235, 230)); return true;
            case "toilet_side": t.clear(); t.rect(8, 12, 16, 20, rgb(230, 230, 225)).rect(6, 10, 20, 3, rgb(240, 240, 235)).rect(10, 0, 12, 10, rgb(225, 225, 220)); return true;
            case "plastic_black": t.fill(rgb(35, 35, 38)).noise(4, 0.05f, 43).grain(0.04f); return true;
            case "tv": t.fill(rgb(30, 30, 32)).rect(2, 3, N - 4, N - 10, rgb(20, 25, 25)).rect(4, 5, 10, 3, rgb(60, 70, 70)); t.crack(rgb(90, 100, 100), 15); return true;
            case "computer": t.fill(rgb(200, 195, 180)).rect(3, 3, N - 6, N - 12, rgb(20, 30, 25)).rect(5, 5, N - 10, N - 16, rgb(26, 48, 38)).rect(6, 6, 3, 2, rgb(70, 160, 96)).rect(3, N - 7, N - 6, 3, rgb(170, 165, 150)); return true;
            case "white_metal": t.fill(rgb(215, 215, 210)).noise(6, 0.04f, 44).stains(2, 0.12f, 5).grain(0.02f); return true;
            case "washer": draw(t, "white_metal", 0); t.circle(16, 18, 9, rgb(120, 120, 125)); t.circle(16, 18, 7, rgb(60, 70, 80)); t.rect(4, 3, 24, 4, rgb(180, 180, 175)); return true;
            case "barrel_top": t.fill(rgb(60, 90, 140)).disc(16, 16, 14, rgb(75, 105, 155), 0).circle(9, 9, 2, rgb(40, 40, 40)).rust(0.3f, 2); return true;
            case "barrel": t.fill(rgb(60, 90, 140)).rect(0, 5, N, 2, rgb(45, 70, 115)).rect(0, N - 7, N, 2, rgb(45, 70, 115)).noise(5, 0.08f, 45).rust(0.35f, 3).grain(0.04f); for (int x = 0; x < N; x++) t.mul(x, 0, 1); return true;
            case "fire_top": t.fill(rgb(40, 20, 10)); for (int i = 0; i < 60; i++) t.disc(t.rng.nextFloat() * N, t.rng.nextFloat() * N, 1 + t.rng.nextFloat() * 3, t.rng.chance(0.5) ? rgb(255, 160, 40) : rgb(255, 220, 90), 1.2f); return true;
            case "tire_top": t.fill(rgb(30, 30, 30)).disc(16, 16, 8, rgb(55, 55, 55), 0).disc(16, 16, 5, rgb(20, 20, 20), 0).grain(0.06f); return true;
            case "tire": t.fill(rgb(32, 32, 34)); for (int y = 0; y < N; y += 4) for (int x = 0; x < N; x += 4) t.rect(x + (y / 4 % 2) * 2, y, 2, 2, rgb(45, 45, 48)); t.grain(0.06f); return true;
            case "pipe_end": t.fill(rgb(120, 125, 130)).disc(16, 16, 12, rgb(60, 62, 65), 0).disc(16, 16, 9, rgb(20, 20, 22), 0).rust(0.2f, 1); return true;
            case "pipe": t.fill(rgb(120, 125, 130)).vgradient(1.2f, 0.7f).rect(0, 4, N, 2, rgb(90, 92, 95)).rust(0.3f, 2).grain(0.04f); return true;
            case "vent": t.fill(rgb(110, 115, 120)); for (int y = 3; y < N - 2; y += 4) t.rect(3, y, N - 6, 2, rgb(40, 40, 42)); t.outline(0, 0, N, N, rgb(80, 85, 90)).rust(0.2f, 3); return true;
            case "ac_top": t.fill(rgb(170, 170, 165)).disc(16, 16, 12, rgb(60, 60, 60), 0); for (int a = 0; a < 4; a++) t.line(16, 16, 16 + (int) (Math.cos(a * 1.57 + 0.4) * 10), 16 + (int) (Math.sin(a * 1.57 + 0.4) * 10), rgb(130, 130, 130)); t.stains(2, 0.2f, 6); return true;
            case "ac_side": t.fill(rgb(170, 170, 165)); for (int y = 4; y < N - 4; y += 3) t.rect(3, y, N - 6, 1, rgb(110, 110, 105)); t.streaks(5, 0.2f).rust(0.15f, 2); return true;
            case "tank_top": t.fill(rgb(110, 85, 60)).disc(16, 16, 14, rgb(90, 70, 50), 0).rect(13, 13, 6, 6, rgb(60, 60, 60)); return true;
            case "tank_side": t.planks(rgb(120, 92, 62), 1, false); for (int y = 4; y < N; y += 10) t.rect(0, y, N, 2, rgb(70, 70, 72)); return true;
            case "machine": t.fill(rgb(90, 110, 100)).outline(0, 0, N, N, rgb(60, 70, 65)).rect(4, 4, 10, 8, rgb(40, 40, 40)).circle(22, 9, 4, rgb(180, 180, 180)).circle(22, 9, 2, rgb(60, 60, 60)); t.rect(4, 18, 24, 2, rgb(200, 170, 40)).rust(0.3f, 4).stains(3, 0.2f, 5); return true;
            case "transformer": t.fill(rgb(110, 120, 110)); for (int x = 2; x < N; x += 5) t.rect(x, 2, 3, N - 4, rgb(85, 95, 85)); t.rect(8, 10, 16, 10, rgb(220, 200, 40)).line(16, 11, 13, 15, rgb(20, 20, 20)).line(13, 15, 18, 15, rgb(20, 20, 20)).line(18, 15, 15, 19, rgb(20, 20, 20)); t.rust(0.2f, 5); return true;
            case "generator": t.fill(rgb(160, 120, 40)).outline(0, 0, N, N, rgb(80, 60, 25)); for (int y = 6; y < 18; y += 3) t.rect(4, y, 12, 2, rgb(60, 50, 30)); t.circle(23, 22, 4, rgb(50, 50, 50)).rect(20, 6, 8, 5, rgb(30, 30, 30)); t.stains(3, 0.2f, 5); return true;
            case "console_top": t.fill(rgb(60, 65, 70)).rect(4, 4, 24, 14, rgb(20, 40, 30)).rect(6, 7, 12, 1, rgb(80, 220, 120)).rect(6, 10, 8, 1, rgb(80, 220, 120)); for (int x = 5; x < 28; x += 4) t.rect(x, 22, 2, 2, rgb(200, 60, 50)); return true;
            case "console": t.fill(rgb(70, 75, 82)).rect(3, 3, N - 6, 12, rgb(15, 35, 25)).rect(5, 5, 16, 1, rgb(90, 230, 130)).rect(5, 8, 10, 1, rgb(90, 230, 130)).rect(5, 11, 13, 1, rgb(90, 230, 130)); for (int x = 4; x < N - 4; x += 5) t.rect(x, 20, 3, 3, x % 2 == 0 ? rgb(220, 60, 50) : rgb(220, 200, 60)); return true;
            case "purifier": t.fill(rgb(80, 120, 150)).outline(0, 0, N, N, rgb(50, 70, 90)); t.rect(4, 4, 8, N - 8, rgb(170, 200, 220)).rect(5, 12, 6, 14, rgb(70, 140, 200)); t.circle(22, 10, 4, rgb(200, 200, 200)).circle(22, 22, 4, rgb(200, 200, 200)); t.rust(0.15f, 5); return true;
            case "car_red": car(t, rgb(150, 35, 30)); return true;
            case "car_blue": car(t, rgb(40, 70, 130)); return true;
            case "car_white": car(t, rgb(200, 200, 195)); return true;
            case "car_rust": car(t, rgb(120, 80, 55)); t.rust(0.6f, 3); return true;
            case "car_olive": car(t, rgb(85, 95, 60)); return true;
            case "car_window": t.fill(argb(200, 40, 50, 55)); for (int i = 0; i < 4; i++) t.line(t.rng.nextInt(N), 0, t.rng.nextInt(N), N - 1, argb(220, 120, 130, 135)); t.outline(0, 0, N, N, argb(255, 25, 25, 25)); return true;
            case "statue": t.fill(rgb(120, 125, 110)).noise(4, 0.15f, 46).streaks(10, 0.25f); for (int i = 0; i < t.px.length; i++) if (t.rng.chance(0.1)) t.px[i] = mix(t.px[i], rgb(70, 130, 110), 0.5f); return true;
            case "tombstone": draw(t, "stone_block", 0); t.rect(10, 6, 12, 2, rgb(70, 66, 60)).rect(15, 3, 2, 10, rgb(70, 66, 60)); t.rect(9, 16, 14, 1, rgb(90, 86, 80)).rect(9, 19, 10, 1, rgb(90, 86, 80)); return true;
            case "coffin_top": t.planks(rgb(80, 52, 35), 2, false); t.rect(14, 4, 4, 22, rgb(170, 150, 90)).rect(9, 10, 14, 4, rgb(170, 150, 90)); return true;
            case "altar_top": t.fill(rgb(110, 20, 25)).noise(3, 0.1f, 47).outline(1, 1, N - 2, N - 2, rgb(200, 170, 60)); t.circle(16, 16, 6, rgb(30, 10, 10)); t.circle(16, 16, 4, rgb(150, 25, 30)); return true;
            case "altar_side": t.fill(rgb(110, 20, 25)).noise(3, 0.1f, 48).rect(0, 0, N, 4, rgb(200, 170, 60)); for (int x = 4; x < N; x += 8) { t.line(x, 8, x + 3, 26, rgb(30, 10, 10)); t.line(x + 3, 8, x, 26, rgb(30, 10, 10)); } return true;
            case "pew": t.planks(rgb(85, 55, 35), 3, false); t.rect(0, 0, N, 3, rgb(60, 40, 25)); return true;
            case "banner_cult": t.clear(); t.rect(4, 0, 24, 28, rgb(100, 15, 20)).rect(4, 28, 6, 4, rgb(100, 15, 20)).rect(22, 28, 6, 4, rgb(100, 15, 20)); t.circle(16, 12, 6, rgb(20, 5, 5)); t.circle(16, 12, 4, rgb(100, 15, 20)); t.line(16, 4, 16, 22, rgb(20, 5, 5)); t.line(10, 18, 22, 18, rgb(20, 5, 5)); return true;
            case "canvas": t.fill(rgb(195, 190, 170)).noise(3, 0.08f, 49).grain(0.08f).streaks(6, 0.15f); return true;
            case "canvas_olive": t.fill(rgb(90, 100, 65)).noise(3, 0.08f, 50).grain(0.08f).streaks(4, 0.15f); return true;
            case "tarp": t.fill(rgb(40, 85, 150)).noise(3, 0.15f, 51).grain(0.06f); for (int y = 0; y < N; y += 8) t.rectShade(0, y, N, 1, 0.8f); return true;
            case "awning_red": t.vstripes(rgb(170, 40, 40), rgb(220, 215, 200), 4).noise(4, 0.08f, 52).streaks(6, 0.2f).grain(0.05f); return true;
            case "awning_green": t.vstripes(rgb(50, 110, 70), rgb(220, 215, 200), 4).noise(4, 0.08f, 53).streaks(6, 0.2f).grain(0.05f); return true;
            case "sign_shop": { int[] bg = {rgb(160, 40, 35), rgb(40, 80, 140), rgb(200, 160, 50), rgb(50, 110, 60)}; t.fill(bg[v % 4]).outline(0, 0, N, N, rgb(40, 35, 30)).outline(1, 1, N - 2, N - 2, rgb(220, 210, 190));
                for (int k = 0; k < 4; k++) { int x0 = 4 + k * 6; t.rect(x0, 10, 4, 12, rgb(235, 230, 215)); if ((k + v) % 2 == 0) t.rect(x0 + 1, 13, 2, 3, bg[v % 4]); else t.rect(x0 + 1, 15, 3, 2, bg[v % 4]); }
                t.streaks(5, 0.2f).grain(0.05f); return true; }
            case "sign_pharmacy": t.fill(rgb(30, 30, 32)).rect(12, 4, 8, 24, rgb(60, 220, 90)).rect(4, 12, 24, 8, rgb(60, 220, 90)); return true;
            case "poster": draw(t, "concrete", 0); { int[] pc = {rgb(200, 190, 160), rgb(180, 60, 50), rgb(60, 70, 90)}; t.rect(5, 3, 22, 27, pc[v % 3]).rect(8, 6, 16, 10, shade(pc[(v + 1) % 3], 0.9f)); for (int y = 19; y < 28; y += 3) t.rect(8, y, 16 - (y % 5), 1, rgb(30, 30, 30)); t.stains(3, 0.2f, 5); chip(t, rgb(140, 138, 132)); } return true;
            case "map_board": t.fill(rgb(120, 90, 60)).rect(2, 2, N - 4, N - 4, rgb(210, 200, 170)); for (int i = 0; i < 6; i++) t.line(t.rng.nextInt(N), t.rng.nextInt(N), t.rng.nextInt(N), t.rng.nextInt(N), rgb(140, 130, 110)); for (int i = 0; i < 4; i++) t.circle(4 + t.rng.nextInt(24), 4 + t.rng.nextInt(24), 1, rgb(200, 40, 40)); return true;
            case "biohazard": t.fill(rgb(220, 180, 40)).outline(0, 0, N, N, rgb(30, 30, 30));
                for (int a = 0; a < 3; a++) { double ang = a * 2.094 - 1.57; t.circle(16 + (int) (Math.cos(ang) * 6), 16 + (int) (Math.sin(ang) * 6), 5, rgb(30, 30, 30)); t.circle(16 + (int) (Math.cos(ang) * 8), 16 + (int) (Math.sin(ang) * 8), 3, rgb(220, 180, 40)); }
                t.circle(16, 16, 3, rgb(220, 180, 40)).circle(16, 16, 2, rgb(30, 30, 30)); t.stains(3, 0.2f, 5).grain(0.04f); return true;
            case "red_cross": t.fill(rgb(225, 225, 220)).rect(12, 5, 8, 22, rgb(190, 30, 30)).rect(5, 12, 22, 8, rgb(190, 30, 30)).stains(2, 0.15f, 5).grain(0.03f); return true;

            // ======================================================= lights
            case "lamp": t.fill(rgb(255, 235, 180)).disc(16, 16, 14, rgb(255, 250, 225), 6).outline(0, 0, N, N, rgb(120, 110, 90)); return true;
            case "lamp_off": t.fill(rgb(150, 150, 140)).disc(16, 16, 12, rgb(180, 180, 170), 6).outline(0, 0, N, N, rgb(90, 90, 85)).stains(2, 0.2f, 5); return true;
            case "neon_red": neon(t, rgb(255, 60, 70)); return true;
            case "neon_blue": neon(t, rgb(60, 160, 255)); return true;
            case "neon_green": neon(t, rgb(80, 255, 120)); return true;
            case "neon_off": neon(t, rgb(70, 70, 75)); return true;
            case "candles": t.clear(); for (int k = 0; k < 5; k++) { int x = 4 + k * 5 + t.rng.nextInt(2), h = 6 + t.rng.nextInt(10); t.rect(x, N - h, 3, h, rgb(220, 210, 180)); t.rect(x + 1, N - h - 3, 1, 2, rgb(255, 200, 80)); t.set(x + 1, N - h - 4, rgb(255, 240, 160)); } return true;
            case "fire": t.clear(); for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) { float f = absentialis.engine.util.Noise.tiled(x / 4f, y / 3f, 8, t.seed) * (y / (float) N) * 1.8f; if (f > 0.45f) t.set(x, y, f > 0.9f ? rgb(255, 240, 150) : (f > 0.65f ? rgb(255, 170, 50) : rgb(220, 80, 20))); } return true;
            case "ember": t.fill(rgb(40, 20, 15)); for (int i = 0; i < 40; i++) t.disc(t.rng.nextFloat() * N, t.rng.nextFloat() * N, 1 + t.rng.nextFloat() * 2, rgb(255, 120 + t.rng.nextInt(80), 30), 1); return true;
            case "red_light": t.fill(rgb(255, 40, 30)).disc(16, 16, 12, rgb(255, 160, 140), 6); return true;

            // ======================================================= loot containers
            case "crate_top": t.planks(rgb(165, 125, 80), 4, true); t.outline(0, 0, N, N, rgb(100, 72, 45)); return true;
            case "crate": t.planks(rgb(165, 125, 80), 4, true); t.rect(0, 0, 3, N, rgb(120, 88, 55)).rect(N - 3, 0, 3, N, rgb(120, 88, 55)).line(3, 3, N - 4, N - 4, rgb(120, 88, 55)).line(4, 3, N - 3, N - 4, rgb(120, 88, 55)); return true;
            case "crate_mil_top": t.fill(rgb(80, 92, 55)).noise(5, 0.06f, 54).outline(0, 0, N, N, rgb(55, 62, 38)).rect(10, 14, 12, 4, rgb(200, 195, 170)); return true;
            case "crate_mil": t.fill(rgb(80, 92, 55)).noise(5, 0.06f, 55).outline(0, 0, N, N, rgb(55, 62, 38)).rect(0, 6, N, 2, rgb(60, 68, 42)).rect(0, N - 8, N, 2, rgb(60, 68, 42)); t.rect(6, 12, 20, 7, rgb(220, 215, 190)); for (int x = 8; x < 24; x += 3) t.rect(x, 14, 2, 3, rgb(40, 40, 40)); t.rect(2, 13, 2, 5, rgb(140, 140, 140)); t.rect(N - 4, 13, 2, 5, rgb(140, 140, 140)); return true;
            case "locker": t.fill(rgb(80, 105, 120)).outline(0, 0, 16, N, rgb(50, 65, 75)).outline(16, 0, 16, N, rgb(50, 65, 75)); for (int y = 3; y < 10; y += 2) { t.rect(4, y, 8, 1, rgb(40, 50, 60)); t.rect(20, y, 8, 1, rgb(40, 50, 60)); } t.rect(12, 16, 2, 4, rgb(170, 170, 170)).rect(28, 16, 2, 4, rgb(170, 170, 170)); t.stains(2, 0.15f, 5).rust(0.15f, 2); return true;
            case "cabinet": t.fill(rgb(140, 105, 70)).outline(0, 0, N, N, rgb(90, 65, 42)).outline(3, 3, 12, N - 6, rgb(110, 80, 52)).outline(17, 3, 12, N - 6, rgb(110, 80, 52)); t.rect(13, 14, 1, 4, rgb(200, 180, 100)).rect(18, 14, 1, 4, rgb(200, 180, 100)); t.grain(0.04f); return true;
            case "fridge": t.fill(rgb(225, 225, 220)).rect(0, 11, N, 1, rgb(150, 150, 150)).rect(26, 3, 2, 6, rgb(160, 160, 160)).rect(26, 14, 2, 12, rgb(160, 160, 160)).stains(3, 0.15f, 5).streaks(3, 0.1f); return true;
            case "safe_top": t.fill(rgb(55, 58, 62)).noise(5, 0.06f, 56).outline(0, 0, N, N, rgb(35, 37, 40)); return true;
            case "safe": t.fill(rgb(55, 58, 62)).outline(0, 0, N, N, rgb(35, 37, 40)).outline(3, 3, N - 6, N - 6, rgb(75, 78, 82)); t.circle(16, 16, 5, rgb(160, 160, 165)); t.circle(16, 16, 3, rgb(90, 90, 95)); t.rect(24, 12, 2, 8, rgb(170, 170, 170)); return true;
            case "med_cabinet": draw(t, "white_metal", 0); t.rect(12, 5, 8, 22, rgb(200, 30, 30)).rect(5, 12, 22, 8, rgb(200, 30, 30)).outline(0, 0, N, N, rgb(160, 160, 160)); return true;
            case "filing": t.fill(rgb(120, 125, 120)).outline(0, 0, N, N, rgb(80, 85, 80)); for (int y = 1; y < N; y += 8) { t.outline(2, y, N - 4, 7, rgb(90, 95, 90)); t.rect(12, y + 3, 8, 1, rgb(190, 190, 190)); t.rect(14, y + 1, 4, 1, rgb(230, 225, 200)); } return true;
            case "toolbox_top": t.fill(rgb(180, 40, 30)).rect(6, 12, 20, 4, rgb(60, 60, 60)).outline(0, 0, N, N, rgb(110, 25, 20)); return true;
            case "toolbox": t.fill(rgb(180, 40, 30)).rect(0, 10, N, 2, rgb(110, 25, 20)).rect(13, 13, 6, 3, rgb(190, 190, 190)).outline(0, 0, N, N, rgb(110, 25, 20)).stains(2, 0.15f, 5); return true;
            case "trash_top": t.fill(rgb(40, 45, 40)).disc(16, 16, 13, rgb(60, 65, 58), 0).stains(3, 0.3f, 5); return true;
            case "trash": t.fill(rgb(50, 70, 55)); for (int x = 2; x < N; x += 5) t.rect(x, 2, 2, N - 4, rgb(40, 58, 45)); t.rect(0, 0, N, 3, rgb(60, 80, 65)).stains(3, 0.25f, 6); return true;
            case "dumpster_top": t.fill(rgb(40, 80, 50)).rect(0, 15, N, 2, rgb(30, 60, 38)).rust(0.25f, 1).stains(3, 0.25f, 6); return true;
            case "dumpster": t.fill(rgb(45, 90, 55)).rect(0, 0, N, 4, rgb(35, 70, 42)); for (int x = 4; x < N; x += 8) t.rect(x, 6, 2, N - 8, rgb(38, 76, 46)); t.rust(0.35f, 2).stains(3, 0.25f, 6); return true;
            case "weapon_rack": t.fill(rgb(60, 65, 60)).outline(0, 0, N, N, rgb(40, 42, 40)); for (int x = 4; x < N - 2; x += 7) { t.rect(x, 4, 3, 24, rgb(30, 30, 30)); t.rect(x, 16, 3, 5, rgb(110, 80, 50)); } return true;
            case "cache_top": t.planks(rgb(110, 80, 50), 3, true); t.rect(4, 4, 6, 6, rgb(220, 200, 60)); return true;
            case "cache": t.planks(rgb(110, 80, 50), 3, true); t.rect(12, 12, 8, 8, rgb(40, 40, 40)).rect(14, 14, 4, 4, rgb(220, 200, 60)); t.rect(0, 0, N, 2, rgb(220, 200, 60)); return true;
            case "bodybag": t.fill(rgb(30, 32, 30)).noise(3, 0.15f, 57).rect(15, 0, 2, N, rgb(120, 120, 120)).grain(0.06f); return true;
            case "stall_top": t.planks(rgb(140, 100, 60), 4, false); for (int i = 0; i < 10; i++) t.rect(t.rng.nextInt(N - 4), t.rng.nextInt(N - 4), 4, 3, t.rng.pick(new int[]{rgb(200, 60, 40), rgb(220, 190, 60), rgb(80, 150, 60), rgb(200, 200, 200), rgb(60, 90, 160)})); return true;
            case "stall": t.planks(rgb(130, 92, 58), 3, true); t.rect(0, 0, N, 4, rgb(170, 40, 40)); return true;
            case "radio_top": t.fill(rgb(60, 70, 60)).rect(4, 4, 24, 10, rgb(30, 30, 30)).rect(22, 18, 4, 10, rgb(40, 40, 40)); return true;
            case "radio": t.fill(rgb(70, 80, 65)).outline(0, 0, N, N, rgb(40, 45, 38)).rect(3, 4, 16, 10, rgb(20, 40, 25)).rect(5, 8, 12, 1, rgb(120, 230, 120)); t.circle(24, 9, 3, rgb(30, 30, 30)).circle(8, 22, 3, rgb(30, 30, 30)).circle(18, 22, 3, rgb(30, 30, 30)); return true;
            default: return false;
        }
    }

    // ------------------------------------------------------------ shared composites
    private static void chip(Tex t, int under) {
        int x = t.rng.nextInt(28), y = t.rng.nextInt(28), w = 2 + t.rng.nextInt(5), h = 2 + t.rng.nextInt(4);
        for (int yy = 0; yy < h; yy++) for (int xx = 0; xx < w; xx++) if (t.rng.nextFloat() < 0.85f) t.set(x + xx, y + yy, shade(under, 0.85f + t.rng.nextFloat() * 0.2f));
        for (int xx = 0; xx < w; xx++) t.mul(x + xx, y + h, 0.7f);
    }

    private static void container(Tex t, int base) {
        t.corrugate(base, 5).noise(5, 0.06f, 3).rust(0.3f, 7).streaks(6, 0.2f).grain(0.04f);
        t.rect(0, 0, N, 2, shade(base, 0.6f)).rect(0, N - 2, N, 2, shade(base, 0.6f));
    }

    private static void car(Tex t, int base) {
        t.fill(base).noise(6, 0.06f, 9).vgradient(1.15f, 0.8f).grain(0.03f);
        t.rect(0, 14, N, 1, shade(base, 0.6f));
        t.stains(3, 0.2f, 5);
        for (int i = 0; i < 6; i++) { int x = t.rng.nextInt(N), y = t.rng.nextInt(N); t.line(x, y, x + 3, y + 1, add(base, 50)); }
        t.rust(0.12f, 4);
    }

    private static void glass(Tex t, int col, boolean cracked) {
        t.fill(col);
        for (int i = 0; i < 3; i++) { int x = 4 + i * 9; t.line(x, N - 2, x + 10, 2, argb(140, 230, 240, 245)); }
        t.outline(0, 0, N, N, argb(255, 80, 85, 90));
        if (cracked) {
            int cx = 8 + t.rng.nextInt(16), cy = 8 + t.rng.nextInt(16);
            for (int a = 0; a < 7; a++) { double ang = a * 0.9 + t.rng.nextFloat(); t.line(cx, cy, cx + (int) (Math.cos(ang) * 16), cy + (int) (Math.sin(ang) * 16), argb(230, 235, 240, 240)); }
            for (int r = 3; r < 10; r += 3) for (int a = 0; a < 20; a++) if (t.rng.chance(0.6)) t.set(cx + (int) (Math.cos(a * 0.314) * r), cy + (int) (Math.sin(a * 0.314) * r), argb(220, 230, 235, 240));
        }
    }

    private static void neon(Tex t, int col) {
        t.fill(rgb(25, 25, 30));
        t.outline(3, 3, N - 6, N - 6, col).outline(4, 4, N - 8, N - 8, add(col, 40));
        for (int x = 8; x < N - 8; x++) { t.set(x, 14, col); t.set(x, 17, col); }
        t.rect(8, 10, 2, 12, col).rect(N - 10, 10, 2, 12, col);
    }

    private static void paintGraffiti(Tex t, int v) {
        int[] cols = {rgb(220, 50, 60), rgb(60, 180, 220), rgb(240, 220, 60), rgb(80, 210, 90), rgb(230, 230, 230), rgb(200, 80, 200)};
        int c = cols[v % cols.length], c2 = cols[(v + 2) % cols.length];
        switch (v % 6) {
            case 0: // tag scrawl
                for (int k = 0; k < 3; k++) { int x = 3 + k * 9; t.line(x, 22, x + 3, 8, c); t.line(x + 3, 8, x + 7, 22, c); t.line(x + 1, 16, x + 6, 16, c2); }
                break;
            case 1: // circled A (anarchist / raider mark)
                for (int a = 0; a < 60; a++) t.set(16 + (int) (Math.cos(a * 0.105) * 11), 16 + (int) (Math.sin(a * 0.105) * 11), c);
                t.line(9, 26, 16, 5, c); t.line(16, 5, 23, 26, c); t.line(11, 19, 21, 19, c);
                break;
            case 2: // warning text bars "DANGER" style
                t.rect(2, 8, 28, 9, c);
                for (int x = 4; x < 28; x += 4) t.rect(x, 10, 2, 5, rgb(20, 20, 20));
                t.line(4, 24, 28, 24, c2);
                break;
            case 3: // survivor arrow + tally marks
                t.line(4, 16, 24, 16, c); t.line(24, 16, 18, 10, c); t.line(24, 16, 18, 22, c);
                for (int k = 0; k < 4; k++) t.line(6 + k * 3, 24, 6 + k * 3, 29, c2);
                break;
            case 4: // cult eye
                for (int a = 0; a < 60; a++) { t.set(16 + (int) (Math.cos(a * 0.105) * 12), 16 + (int) (Math.sin(a * 0.105) * 6), rgb(140, 20, 25)); }
                t.circle(16, 16, 3, rgb(140, 20, 25));
                for (int k = 0; k < 5; k++) t.line(10 + k * 3, 23, 10 + k * 3, 30, rgb(120, 15, 20));
                break;
            default: // X-code search mark (disaster responders)
                t.line(6, 6, 26, 26, c); t.line(26, 6, 6, 26, c); t.rect(14, 2, 4, 3, c2); t.rect(2, 14, 3, 4, c2);
        }
    }
}
