package absentialis.scene;

import absentialis.engine.render.Vector2D;
import absentialis.engine.voxel.VoxModel;
import absentialis.gameplay.Alliance;
import absentialis.gameplay.squad.Roster;
import absentialis.persistence.Profile;
import absentialis.ui.Ui;
import java.util.ArrayList;
import java.util.List;

/**
 * The profile card (0.6): one layout for the player and for every squadmate - who they are, what Rank they hold,
 * and what they have done. The player's card background is theirs to choose from the ones their Rank has unlocked;
 * a squadmate's card wears the best background their own Level has earned.
 *
 * The card is drawn, not stored: every figure on it is read from the profile or the roster at the moment it is shown.
 */
public final class ProfileCard {
    private ProfileCard() {}

    /** A card background and the Rank that unlocks it. */
    public record Background(String id, String name, int rank, int top, int bottom, int accent, String pattern) {}

    public static final Background[] BACKGROUNDS = {
            new Background("field", "Field Drab", 1, 0xFF3A4230, 0xFF1C2018, 0xFF9AA86A, "topo"),
            new Background("concrete", "Bunker Concrete", 5, 0xFF4A4C4E, 0xFF202224, 0xFFB8BCC0, "panels"),
            new Background("rust", "Rail Yard Rust", 10, 0xFF6A3A22, 0xFF24140E, 0xFFE08A4A, "rivets"),
            new Background("cordon", "Cordon Tape", 15, 0xFF3C3A20, 0xFF16150C, 0xFFE2B030, "hazard"),
            new Background("night", "Night Watch", 20, 0xFF1A2438, 0xFF080B14, 0xFF7AA0E0, "stars"),
            new Background("frost", "First Frost", 25, 0xFF4E6470, 0xFF182026, 0xFFCFE6F0, "panels"),
            new Background("ember", "Ember", 30, 0xFF7A3A14, 0xFF1E0C06, 0xFFFFB050, "embers"),
            new Background("blood", "Red Ledger", 35, 0xFF5A1818, 0xFF180606, 0xFFE05048, "rivets"),
            new Background("brass", "Spent Brass", 40, 0xFF6A5A28, 0xFF1E1A0C, 0xFFE8D080, "topo"),
            new Background("gold", "Field Marshal", 50, 0xFF8A7020, 0xFF2A2008, 0xFFFFE070, "stars"),
            // ---- 0.8: five cards with an emblem of their own. Three are opened by Rank, two by Prestige (PRESTIGE_NEEDED).
            new Background("jail", "Jail Card", 4, 0xFF3A3C40, 0xFF121315, 0xFFAEB4BC, "chains"),
            new Background("thunder", "Thunder Card", 10, 0xFF2A3350, 0xFF0A0C16, 0xFFF4E36A, "storm"),
            new Background("operator", "Operator Card", 20, 0xFF30362C, 0xFF0C0E0A, 0xFFD8D4C4, "operator"),
            new Background("prestige", "Prestige Card", 1, 0xFF4A3A10, 0xFF100C04, 0xFFFFD24A, "skull"),
            new Background("prestigious", "Prestigious I Card", 1, 0xFF5A1420, 0xFF140408, 0xFFFFD24A, "cross")};

    /** Cards that a Prestige opens rather than a Rank: card id -> the Prestige needed. */
    public static final java.util.Map<String, Integer> PRESTIGE_NEEDED = java.util.Map.of("prestige", 1, "prestigious", 2);

    public static int prestigeNeeded(Background b) { return PRESTIGE_NEEDED.getOrDefault(b.id(), 0); }
    /** 0.8: whether a profile may use a card - by the best Rank ever held, and for the Prestige cards by Prestige. */
    public static boolean unlocked(Background b, absentialis.gameplay.progression.Progression prog) { return prog.bestRank >= b.rank() && prog.prestige >= prestigeNeeded(b); }
    /** What opens a card, in words. */
    public static String requirement(Background b) { int pn = prestigeNeeded(b); return pn > 0 ? "Prestige " + pn : "Rank " + b.rank(); }

    /** The emblems, as grids ('#' filled). Drawn large and faint on the right of the card, and small on its chip. */
    static final String[] BOLT = {"......#####.", ".....#####..", "....#####...", "...#####....", "..########..", ".....#####..", "....#####...", "...#####....", "..#####.....", ".####.......", ".###........", "##.........."};
    static final String[] SKULL = {"....########....", "...##########...", "..############..", "..##..####..##..", "..##..####..##..", "..############..", "...####..####...", "....########....", "....#.#..#.#....", "##...######...##", "###..........###", "..###......###..", "....###..###....", "....###..###....", "..###......###..", "###..........###"};
    static final String[] OPERATOR = {"....########....", "...##########...", "..############..", ".##############.", ".##############.", "..#..######..#..", "..#...####...#..", "..############..", "...####..####...", "...##########...", "....#.#..#.#....", ".....######.....", "..############..", ".##############.", "################", "################"};
    static final String[] CROSS = {".....####.....", ".....####.....", ".....####.....", ".....####.....", "##############", "#####.##.#####", "####......####", "#####....#####", "#####.##.#####", "##############", ".....####.....", ".....####.....", ".....####.....", ".....####.....", ".....####.....", ".....####....."};

    /**
     * An emblem, in the box its grid would fill.
     *
     * 1.2: drawn. The four emblems were grids of squares - sixteen cells across, blown up to the height of a card - and
     * looked like what they were. Each is now the shapes it is made of (ellipses, wedges, four-sided figures, round-ended
     * strokes), filled at the size it is asked for; what an emblem is has not changed - the bolt is the bolt, the skull
     * still has its crossed bones, the operator his helmet and mask, the cross its open heart. The grids are kept: they
     * say how big each emblem's box is, and which is which.
     */
    static void emblem(Ui ui, String[] g, float x, float y, float cell, int argb) {
        absentialis.engine.render.Batch2D b = ui.b;
        float w = g[0].length() * cell, h = g.length * cell;
        // what is cut out of an emblem (eyes, teeth, a visor) is drawn dark over it, as strong as the emblem itself is
        int cutout = Ui.alpha(0xFF050505, Math.min(1, ((argb >>> 24) & 255) / 255f * 1.05f));
        if (g == BOLT) {
            Vector2D.polyIn(b, x, y, w, h, argb, 0.50f, 0.00f, 0.94f, 0.00f, 0.63f, 0.43f, 0.20f, 0.43f);
            Vector2D.polyIn(b, x, y, w, h, argb, 0.36f, 0.35f, 0.84f, 0.35f, 0.76f, 0.52f, 0.30f, 0.52f);
            Vector2D.polyIn(b, x, y, w, h, argb, 0.40f, 0.44f, 0.80f, 0.44f, 0.10f, 1.00f);
        } else if (g == SKULL) {
            // the bones behind, crossed; then the skull over them
            float t = h * 0.085f;
            Vector2D.capsule(b, x + w * 0.08f, y + h * 0.64f, x + w * 0.92f, y + h * 0.95f, t, argb);
            Vector2D.capsule(b, x + w * 0.92f, y + h * 0.64f, x + w * 0.08f, y + h * 0.95f, t, argb);
            for (float[] e : new float[][]{{0.05f, 0.61f}, {0.10f, 0.68f}, {0.95f, 0.61f}, {0.90f, 0.68f}, {0.05f, 0.98f}, {0.11f, 0.92f}, {0.95f, 0.98f}, {0.89f, 0.92f}}) Vector2D.ellipse(b, x + w * e[0], y + h * e[1], t * 0.62f, t * 0.62f, argb);
            Vector2D.ellipse(b, x + w * 0.5f, y + h * 0.33f, w * 0.37f, h * 0.33f, argb);
            Vector2D.polyIn(b, x, y, w, h, argb, 0.24f, 0.46f, 0.76f, 0.46f, 0.67f, 0.80f, 0.33f, 0.80f);
            Vector2D.ellipse(b, x + w * 0.34f, y + h * 0.37f, w * 0.115f, h * 0.10f, cutout);
            Vector2D.ellipse(b, x + w * 0.66f, y + h * 0.37f, w * 0.115f, h * 0.10f, cutout);
            Vector2D.polyIn(b, x, y, w, h, cutout, 0.50f, 0.48f, 0.555f, 0.60f, 0.445f, 0.60f);
            for (int i = 0; i < 5; i++) b.rect(x + w * (0.385f + i * 0.0575f) - Math.max(1, w * 0.008f), y + h * 0.67f, Math.max(1, w * 0.016f), h * 0.13f, cutout);
            b.rect(x + w * 0.34f, y + h * 0.665f, w * 0.32f, Math.max(1, h * 0.012f), cutout);
        } else if (g == OPERATOR) {
            // shoulders and the collar of the rig; the helmet; the mask under it, with the goggles across
            Vector2D.polyIn(b, x, y, w, h, argb, 0.00f, 1.00f, 0.16f, 0.78f, 0.84f, 0.78f, 1.00f, 1.00f);
            Vector2D.polyIn(b, x, y, w, h, argb, 0.38f, 0.66f, 0.62f, 0.66f, 0.66f, 0.80f, 0.34f, 0.80f);
            Vector2D.arc(b, x + w * 0.5f, y + h * 0.32f, w * 0.36f, h * 0.31f, (float) Math.PI, (float) (Math.PI * 2), argb);
            Vector2D.polyIn(b, x, y, w, h, argb, 0.12f, 0.31f, 0.88f, 0.31f, 0.86f, 0.39f, 0.14f, 0.39f);
            Vector2D.polyIn(b, x, y, w, h, argb, 0.20f, 0.38f, 0.80f, 0.38f, 0.69f, 0.71f, 0.31f, 0.71f);
            Vector2D.polyIn(b, x, y, w, h, argb, 0.06f, 0.36f, 0.20f, 0.36f, 0.22f, 0.56f, 0.10f, 0.52f);            // the ear pieces of a headset
            Vector2D.polyIn(b, x, y, w, h, argb, 0.80f, 0.36f, 0.94f, 0.36f, 0.90f, 0.52f, 0.78f, 0.56f);
            Vector2D.ellipse(b, x + w * 0.365f, y + h * 0.455f, w * 0.115f, h * 0.062f, cutout);
            Vector2D.ellipse(b, x + w * 0.635f, y + h * 0.455f, w * 0.115f, h * 0.062f, cutout);
            Vector2D.polyIn(b, x, y, w, h, cutout, 0.50f, 0.52f, 0.54f, 0.60f, 0.46f, 0.60f);                      // the skull painted on the mask
            for (int i = 0; i < 4; i++) b.rect(x + w * (0.415f + i * 0.0575f) - Math.max(1, w * 0.008f), y + h * 0.635f, Math.max(1, w * 0.016f), h * 0.07f, cutout);
            b.rect(x + w * 0.30f, y + h * 0.84f, w * 0.40f, Math.max(1, h * 0.014f), cutout);                    // the top of the plate carrier
            for (float sx : new float[]{0.27f, 0.71f}) b.rect(x + w * sx, y + h * 0.80f, Math.max(1, w * 0.02f), h * 0.20f, cutout);
        } else if (g == CROSS) {
            // four arms that widen to their ends, about an open heart (the star is put in it by the card)
            float cx = 0.5f, cy = 0.46f;
            Vector2D.polyIn(b, x, y, w, h, argb, cx - 0.10f, cy, cx + 0.10f, cy, 0.68f, 0.00f, 0.32f, 0.00f);
            Vector2D.polyIn(b, x, y, w, h, argb, cx - 0.10f, cy, cx + 0.10f, cy, 0.70f, 1.00f, 0.30f, 1.00f);
            Vector2D.polyIn(b, x, y, w, h, argb, cx, cy - 0.075f, cx, cy + 0.075f, 0.00f, cy + 0.15f, 0.00f, cy - 0.15f);
            Vector2D.polyIn(b, x, y, w, h, argb, cx, cy - 0.075f, cx, cy + 0.075f, 1.00f, cy + 0.15f, 1.00f, cy - 0.15f);
            Vector2D.ellipse(b, x + w * cx, y + h * cy, w * 0.17f, w * 0.17f, argb);
            Vector2D.ellipse(b, x + w * cx, y + h * cy, w * 0.115f, w * 0.115f, cutout);
        } else {
            for (int r = 0; r < g.length; r++) {
                int run = -1;
                for (int c = 0; c <= g[r].length(); c++) {
                    boolean on = c < g[r].length() && g[r].charAt(c) == '#';
                    if (on && run < 0) run = c;
                    if (!on && run >= 0) { b.rect(Math.round(x + run * cell), Math.round(y + r * cell), Math.round((c - run) * cell), Math.max(1, Math.round(cell)), argb); run = -1; }
                }
            }
        }
    }

    /** One chain link: a rounded rectangle drawn as an outline, {@code t} thick. */
    private static void link(Ui ui, float x, float y, float w, float h, float t, int argb) {
        ui.b.rect(x + t, y, w - 2 * t, t, argb); ui.b.rect(x + t, y + h - t, w - 2 * t, t, argb);
        ui.b.rect(x, y + t, t, h - 2 * t, argb); ui.b.rect(x + w - t, y + t, t, h - 2 * t, argb);
    }

    public static Background background(String id) { for (Background b : BACKGROUNDS) if (b.id().equals(id)) return b; return BACKGROUNDS[0]; }
    public static boolean unlocked(Background b, int bestRank) { return bestRank >= b.rank(); }
    /** The best background a Rank has earned. */
    public static Background best(int rank) { Background r = BACKGROUNDS[0]; for (Background b : BACKGROUNDS) if (rank >= b.rank() && b.rank() >= r.rank() && prestigeNeeded(b) == 0) r = b; return r; }

    /** Everything a card shows. */
    public static final class Data {
        public String name = "", title = "", sub = "", faction = "", serial = "", key = "";
        public int factionColour = 0xFF8A8A8A, rank = 1;
        public float progress;
        public Background background = BACKGROUNDS[0];
        public java.util.function.Supplier<VoxModel> model;
        public final List<String[]> stats = new ArrayList<>();
        public Data stat(String label, String value) { stats.add(new String[]{label, value}); return this; }
    }

    public static Data of(Ctx c, Profile p) {
        Data d = new Data();
        d.name = p.name;
        d.title = p.prog.title();
        d.rank = p.prog.rank();
        d.progress = p.prog.rankProgress();
        d.sub = p.ironman ? "IRONMAN" : "ELEMENT LEADER";
        Alliance.Def a = Alliance.def(p.alliance);
        d.faction = a == null ? "UNALIGNED" : a.name().toUpperCase();
        d.factionColour = a == null ? 0xFF8A8A8A : a.colour();
        Background b = background(p.cardBackground);
        d.background = unlocked(b, p.prog.bestRank) ? b : BACKGROUNDS[0];
        d.serial = "ABS-" + String.format("%04d", Math.floorMod(p.seed ^ p.name.hashCode(), 10000)) + "-" + (p.slot + 1);
        ActorRenderer.Look look = ActorRenderer.playerLook(p, null);
        d.key = look.key;
        d.model = () -> c.ar.flatten(look);
        int human = 0, inf = p.stat("kills_infected");
        for (String f : absentialis.sim.ZoneState.FACTIONS) human += p.stat("kills_" + f);
        int raids = p.stat("raids"), deaths = p.stat("deaths");
        d.stat("Operations", String.valueOf(raids)).stat("Extractions", String.valueOf(p.stat("extracts")))
                .stat("Killed in action", String.valueOf(deaths)).stat("Survival", raids == 0 ? "-" : Math.round(100f * p.stat("extracts") / raids) + "%")
                .stat("Hostiles killed", String.valueOf(human)).stat("Infected killed", String.valueOf(inf))
                .stat("Squadmates lost", String.valueOf(p.stat("mates_lost"))).stat("Containers searched", String.valueOf(p.stat("containers")));
        return d;
    }

    public static Data of(Ctx c, Profile p, Roster.Mate m) {
        Data d = new Data();
        d.name = m.name;
        d.title = m.title();
        d.rank = m.level();
        d.progress = m.progress();
        d.sub = m.active ? (Roster.RED.equals(m.team) ? "RED TEAM" : "BLUE TEAM") : "RESERVE";
        Alliance.Def a = Alliance.def(p.alliance);
        d.faction = a == null ? "UNALIGNED" : a.name().toUpperCase();
        d.factionColour = a == null ? 0xFF8A8A8A : a.colour();
        d.background = best(m.level());
        d.serial = "ABS-" + String.format("%04d", Math.floorMod(p.seed * 31 + m.id * 977, 10000)) + "-S" + m.id;
        ActorRenderer.Look look = new ActorRenderer.Look();
        look.app = m.look;
        look.gear.vest = "soft";
        look.key = absentialis.character.CharBuilder.key(look.app, look.gear);
        d.key = look.key;
        d.model = () -> c.ar.flatten(look);
        absentialis.gameplay.items.ItemDef wd = absentialis.gameplay.items.Content.def(m.weapon);
        d.stat("Operations", String.valueOf(m.raids)).stat("Kills", String.valueOf(m.kills))
                .stat("Times down", String.valueOf(m.downs)).stat("Kills per operation", m.raids == 0 ? "-" : String.format("%.1f", m.kills / (float) m.raids))
                .stat("Level", m.level() + " / " + Roster.MAX_LEVEL).stat("Prestige", m.prestige() + " / " + Roster.MAX_PRESTIGE)
                .stat("Weapon", wd == null ? "?" : wd.name).stat("Experience", String.format("%,d", m.xp));
        return d;
    }

    /** Height of a card of a given width. */
    public static float height(float w) { return Math.round(w * 0.46f); }

    private static final java.util.Map<String, VoxModel> MODELS = new java.util.HashMap<>();

    /** Draws a card at (x, y), w wide. spin turns the figure. The same code draws the player and squadmates. */
    public static void draw(Ui ui, Ctx c, Data d, float x, float y, float w, float spin) {
        float h = height(w), k = w / 620f;          // every measure below is in card units at 620 wide
        Background bg = d.background;
        ui.shadow(x, y, w, h, 0x90);
        // background: a vertical blend of the two tones, then its pattern, then a vignette toward the edges
        int bands = 28;
        for (int i = 0; i < bands; i++) ui.b.rect(x, y + h * i / bands, w, h / bands + 1, Ui.mix(bg.top(), bg.bottom(), i / (float) (bands - 1)));
        ui.b.scissor((int) x, (int) y, (int) w, (int) h);
        pattern(ui, bg, x, y, w, h, k);
        ui.b.noScissor();
        ui.b.rect(x, y, w, 3 * k, Ui.alpha(bg.accent(), 0.9f));
        ui.b.rect(x, y + h - 22 * k, w, 22 * k, 0xC0080808);
        ui.b.outline(x, y, w, h, Math.max(1, 2 * k), Ui.mix(bg.accent(), 0xFF000000, 0.45f));
        // ---- the figure
        float fs = h - 54 * k, fx = x + 16 * k, fy = y + 18 * k;
        ui.b.rect(fx, fy, fs * 0.78f, fs, 0x90000000);
        ui.b.outline(fx, fy, fs * 0.78f, fs, 1, Ui.alpha(bg.accent(), 0.6f));
        if (d.model != null) {
            VoxModel m = MODELS.get(d.key);
            if (m == null) { if (MODELS.size() > 24) MODELS.clear(); m = d.model.get(); MODELS.put(d.key, m); }
            ui.b.scissor((int) fx, (int) fy, (int) (fs * 0.78f), (int) fs);
            c.icons.drawModel(ui.b, m, fx - fs * 0.11f, fy, fs, (float) Math.PI + spin, 0.14f);
            ui.b.noScissor();
        }
        // ---- identity
        float tx = fx + fs * 0.78f + 18 * k, tw = x + w - tx - 16 * k;
        float nameSize = absentialis.ui.Layout.fit(ui, d.name.toUpperCase(), tx, y + 16 * k, tw, 24 * k / ui.scale, 12, Ui.TXT_HI);
        float ty = y + 16 * k + absentialis.ui.Layout.lineHeight(ui, nameSize) + 2 * k;
        // 1.2: the mark of the Rank, beside its name
        float is = 17 * k;
        ui.b.rect(tx, ty - 1 * k, is + 4 * k, is + 2 * k, 0x70000000);
        absentialis.ui.Insignia.draw(ui, d.rank, tx + 2 * k, ty, is, bg.accent());
        ui.text(d.title, tx + is + 10 * k, ty, 13 * k / ui.scale, bg.accent());
        ty += 20 * k;
        // faction chip and role
        float cw = ui.tw(d.faction, 10 * k / ui.scale) + 14 * k;
        ui.b.rect(tx, ty, cw, 17 * k, d.factionColour);
        ui.text(d.faction, tx + 7 * k, ty + 3 * k, 10 * k / ui.scale, 0xFFF4F0E4);
        ui.text(d.sub, tx + cw + 10 * k, ty + 3 * k, 10 * k / ui.scale, Ui.TXT_DIM);
        ty += 26 * k;
        // rank progress
        ui.b.rect(tx, ty, tw, 7 * k, 0xA0000000);
        ui.b.rect(tx, ty, tw * Math.max(0, Math.min(1, d.progress)), 7 * k, bg.accent());
        ui.textR(Math.round(d.progress * 100) + "% to next", tx + tw, ty + 9 * k, 9 * k / ui.scale, Ui.TXT_DIM);
        ty += 24 * k;
        // ---- stats: two columns
        float colW = tw / 2, rowH = 19 * k;
        for (int i = 0; i < d.stats.size(); i++) {
            String[] st = d.stats.get(i);
            float sx = tx + (i % 2) * colW, sy = ty + (i / 2) * rowH;
            if (sy + rowH > y + h - 24 * k) break;
            ui.text(st[0], sx, sy, 10 * k / ui.scale, Ui.TXT_DIM);
            ui.textR(st[1], sx + colW - 10 * k, sy, 11 * k / ui.scale, Ui.TXT_HI);
        }
        ui.text(d.serial, x + 16 * k, y + h - 17 * k, 9 * k / ui.scale, Ui.TXT_DIM);
        ui.textR(bg.name().toUpperCase() + "   " + absentialis.Version.TITLE, x + w - 16 * k, y + h - 17 * k, 9 * k / ui.scale, Ui.TXT_DIM);
    }

    /** The background's own texture: drawn from rectangles, no text and no images. */
    private static void pattern(Ui ui, Background bg, float x, float y, float w, float h, float k) {
        int a = Ui.alpha(bg.accent(), 0.10f), a2 = Ui.alpha(bg.accent(), 0.20f), dark = 0x30000000;
        switch (bg.pattern()) {
            case "storm" -> {
                // a stormy card: banks of cloud across the top, rain slanting under them, one bolt of lightning
                for (int i = 0; i < 7; i++) { float cx = x + w * (0.08f + i * 0.14f), cy = y + h * (0.10f + 0.05f * (i % 3)); ui.b.rect(cx - 60 * k, cy, 150 * k, 22 * k, 0x38000000); ui.b.rect(cx - 30 * k, cy - 10 * k, 90 * k, 14 * k, 0x30000000); }
                for (int i = 0; i < 60; i++) { float rx = x + w * absentialis.engine.util.Rng.hash(i, 4, 9, 17), ry = y + h * (0.25f + 0.7f * absentialis.engine.util.Rng.hash(i, 7, 1, 31)); for (int s = 0; s < 4; s++) ui.b.rect(rx - s * 2 * k, ry + s * 4 * k, Math.max(1, k), 4 * k, a); }
                float cell = h / 16f;
                emblem(ui, BOLT, x + w * 0.80f - 6 * cell, y + h * 0.12f, cell, Ui.alpha(bg.accent(), 0.5f));
                emblem(ui, BOLT, x + w * 0.80f - 6 * cell + 2 * k, y + h * 0.12f + 2 * k, cell, 0x30FFFFFF);
            }
            case "skull" -> {
                // gold skull with crossed bones, on a field of fine diagonal lines
                for (float px = -h; px < w; px += 18 * k) for (int s = 0; s < (int) (h / (2 * k)); s += 3) ui.b.rect(x + px + s * 2 * k, y + s * 2 * k, 2 * k, Math.max(1, k), a);
                float cell = h / 20f;
                emblem(ui, SKULL, x + w * 0.80f - 8 * cell + 2 * k, y + h * 0.10f + 2 * k, cell, 0x60000000);
                emblem(ui, SKULL, x + w * 0.80f - 8 * cell, y + h * 0.10f, cell, Ui.alpha(bg.accent(), 0.5f));
            }
            case "operator" -> {
                // a skull-masked soldier in tactical gear: helmet, mask, shoulders - over a faint grid of sight lines
                for (float px = 0; px < w; px += 40 * k) ui.b.rect(x + px, y, 1, h, a); for (float py = 0; py < h; py += 40 * k) ui.b.rect(x, y + py, w, 1, a);
                float cell = h / 18f;
                emblem(ui, OPERATOR, x + w * 0.80f - 8 * cell + 2 * k, y + h * 0.06f + 2 * k, cell, 0x70000000);
                emblem(ui, OPERATOR, x + w * 0.80f - 8 * cell, y + h * 0.06f, cell, Ui.alpha(bg.accent(), 0.5f));
                ui.b.rect(x + w * 0.80f - 6 * cell, y + h * 0.06f + 5 * cell, 12 * cell, cell * 0.5f, Ui.alpha(0xFF6A7A4A, 0.9f));   // the band of the goggles strap
            }
            case "chains" -> {
                // interlocked chain links, run diagonally across the card: each link passes through the next
                float lw = 58 * k, lh = 26 * k, t = Math.max(2, 5 * k);
                for (int row = -1; row < 4; row++) for (int i = -1; i < 12; i++) {
                    float lx = x + i * lw * 0.78f + row * 30 * k, ly = y + h * 0.16f + row * 70 * k + i * 6 * k;
                    boolean flat = i % 2 == 0;
                    int col = Ui.alpha(bg.accent(), row == 1 ? 0.55f : 0.22f);
                    if (flat) link(ui, lx, ly, lw, lh, t, col); else link(ui, lx + lw * 0.30f, ly - lh * 0.55f, lh, lw * 0.9f, t, col);
                    if (flat) ui.b.rect(lx + t, ly + t, lw - 2 * t, Math.max(1, k), 0x40FFFFFF);
                }
            }
            case "cross" -> {
                // a gold cross with a star at its heart, rays behind it
                for (int i = 0; i < 16; i++) { double ang = i * Math.PI / 8; for (int s = 6; s < 30; s++) ui.b.rect(x + w * 0.80f + (float) Math.cos(ang) * s * 7 * k, y + h * 0.48f + (float) Math.sin(ang) * s * 7 * k, 2 * k, 2 * k, a); }
                float cell = h / 20f;
                emblem(ui, CROSS, x + w * 0.80f - 7 * cell + 2 * k, y + h * 0.10f + 2 * k, cell, 0x60000000);
                emblem(ui, CROSS, x + w * 0.80f - 7 * cell, y + h * 0.10f, cell, Ui.alpha(bg.accent(), 0.5f));
                // the star: five points, in the open heart of the cross
                Vector2D.star(ui.b, x + w * 0.80f, y + h * 0.10f + 20 * cell * 0.46f, cell * 1.35f, 0.42f, 5, 0xFFFFF4C0);
            }
            case "topo" -> {
                // contour lines: closed curves about two summits, each a little wider and a little less regular than the last
                for (int s = 0; s < 2; s++) {
                    float cx = x + w * (s == 0 ? 0.80f : 0.30f), cy = y + h * (s == 0 ? 0.32f : 0.78f);
                    for (int i = 0; i < (s == 0 ? 9 : 6); i++) { float r = (34 + i * 30) * k; Vector2D.ring(ui.b, cx + i * 3 * k, cy - i * 2 * k, r * (1.25f + 0.05f * (i % 3)), r * (0.72f + 0.04f * (i % 2)), Math.max(1, 1.2f * k), 0, 6.2832f, s == 0 ? a2 : a); }
                }
            }
            case "panels" -> { for (float px = 0; px < w; px += 62 * k) ui.b.rect(x + px, y, 1, h, dark); for (float py = 0; py < h; py += 48 * k) ui.b.rect(x, y + py, w, 1, dark); for (float px = 31 * k; px < w; px += 62 * k) for (float py = 24 * k; py < h; py += 48 * k) ui.b.rect(x + px - k, y + py - k, 2 * k, 2 * k, a2); }
            case "rivets" -> {
                // plate: seams, and a row of round rivet heads along the top and bottom, each with its light and its shadow
                for (int i = 1; i < 6; i++) ui.b.rect(x + w * i / 6f, y, Math.max(1, k), h, dark);
                for (float px = 14 * k; px < w; px += 28 * k) for (int row = 0; row < 2; row++) {
                    float ry = row == 0 ? y + 11 * k : y + h - 33 * k;
                    Vector2D.ellipse(ui.b, px + x + 1 * k, ry + 1 * k, 3.2f * k, 3.2f * k, 0x50000000);
                    Vector2D.ellipse(ui.b, px + x, ry, 3 * k, 3 * k, a2);
                    Vector2D.ellipse(ui.b, px + x - 0.9f * k, ry - 0.9f * k, 1.1f * k, 1.1f * k, 0x38FFFFFF);
                }
            }
            case "hazard" -> { for (float px = -h; px < w; px += 46 * k) for (int s = 0; s < 14; s++) ui.b.rect(x + px + s * 2 * k + (h - 40 * k) * 0, y + h - 40 * k + s * 1.2f * k, 22 * k, 1.2f * k + 1, a2); }
            case "stars" -> {
                // a night sky: many small points, a few four-pointed stars, one bright one
                for (int i = 0; i < 70; i++) {
                    float sx = x + w * absentialis.engine.util.Rng.hash(i, 3, 7, 11), sy = y + h * 0.78f * absentialis.engine.util.Rng.hash(i, 5, 2, 23), s = (1 + 2.2f * absentialis.engine.util.Rng.hash(i, 9, 4, 5)) * k;
                    if (i % 9 == 0) Vector2D.star(ui.b, sx, sy, s * 3.2f, 0.22f, 4, Ui.alpha(bg.accent(), 0.75f));
                    else Vector2D.ellipse(ui.b, sx, sy, s * 0.7f, s * 0.7f, i % 3 == 0 ? a2 : a);
                }
                Vector2D.star(ui.b, x + w * 0.83f, y + h * 0.26f, 26 * k, 0.16f, 4, Ui.alpha(bg.accent(), 0.9f));
                Vector2D.star(ui.b, x + w * 0.83f, y + h * 0.26f, 13 * k, 0.5f, 8, Ui.alpha(0xFFFFFFFF, 0.55f));
            }
            case "embers" -> {
                // sparks going up: thick at the bottom, thinning as they rise, each a small bright point with a glow about it
                for (int i = 0; i < 48; i++) {
                    float sx = x + w * absentialis.engine.util.Rng.hash(i, 2, 8, 13), f = absentialis.engine.util.Rng.hash(i, 6, 3, 29), sy = y + h * (1 - f * f), s = (1.2f + 2.6f * (1 - f)) * k;
                    Vector2D.ellipse(ui.b, sx, sy, s * 2.4f, s * 2.4f, Ui.alpha(bg.accent(), 0.10f));
                    Vector2D.ellipse(ui.b, sx, sy, s, s * 1.3f, Ui.alpha(bg.accent(), 0.35f + 0.5f * (1 - f)));
                }
            }
            default -> { }
        }
    }
}
