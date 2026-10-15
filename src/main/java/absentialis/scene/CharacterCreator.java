package absentialis.scene;

import absentialis.character.Appearance;
import absentialis.character.CharBuilder;
import absentialis.character.CharDefs;
import absentialis.character.CharDefs.Category;
import absentialis.engine.util.Rng;
import absentialis.engine.voxel.VoxModel;
import absentialis.persistence.Profile;
import absentialis.ui.Ui;

/**
 * Character creator (iteration P3): a graphical front-end over the customization definition
 * tables. Every row selects an index; the preview is the same assembled model the raid renders in
 * first and third person. Nothing is edited here except those indices.
 */
public final class CharacterCreator {
    private final Ctx c;
    private final ActorRenderer.Look look = new ActorRenderer.Look();
    private float spin = 0.5f;
    private boolean showGear = true, dragging;
    /** 0.7: whether the preview turns by itself. Off, the character stands still facing the player (a drag still turns it). */
    private boolean autoSpin = true;
    private VoxModel preview;
    private String previewKey = "";

    public CharacterCreator(Ctx c) { this.c = c; }

    /**
     * Draws the creator into a rectangle. gearFrom supplies equipped gear for the preview (null
     * while creating a new survivor). Returns true when a selection changed this frame.
     */
    public boolean draw(Ui ui, Appearance app, Profile gearFrom, float x, float y, float w, float h) {
        boolean changed = false;
        Category[] cats = Category.values();
        float rowH = Math.min(ui.s(44), (h - ui.s(60)) / (cats.length + 1)), listW = Math.min(ui.s(520), w * 0.55f);
        // ---- 0.6: body. Male or female; the choice decides which hairstyles are offered and whether there is a beard row.
        ui.text("SEX", x, y + ui.s(9), 13, Ui.TXT_HI);
        float sbx = x + ui.s(170), sbw = (listW - ui.s(170)) / 2 - ui.s(3);
        if (ui.button("cc_male", sbx, y, sbw, rowH - ui.s(6), "MALE", null, true) && app.female) { app.sex(false); changed = true; }
        if (ui.button("cc_female", sbx + sbw + ui.s(6), y, sbw, rowH - ui.s(6), "FEMALE", null, true) && !app.female) { app.sex(true); changed = true; }
        float hx = app.female ? sbx + sbw + ui.s(6) : sbx;
        ui.b.rect(hx, y + rowH - ui.s(9), sbw, ui.s(3), Ui.AMBER);
        y += rowH;
        for (int i = 0; i < cats.length; i++) {
            Category cat = cats[i];
            float ry = y + i * rowH;
            int n = 0, sel = app.get(cat), ord = 0;
            for (int k = 0; k < CharDefs.count(cat); k++) if (CharDefs.allowed(cat, k, app.female)) { n++; if (k <= sel) ord = n; }
            if (n <= 1) { ui.text(cat.label.toUpperCase(), x, ry + ui.s(9), 13, Ui.TXT_DIM); ui.text("-", x + ui.s(214), ry + ui.s(9), 14, Ui.TXT_DIM); continue; }
            ui.text(cat.label.toUpperCase(), x, ry + ui.s(9), 13, Ui.TXT_HI);
            float bx = x + ui.s(170), bw = ui.s(34), nameW = listW - ui.s(170) - bw * 2 - ui.s(12);
            if (ui.button("cc_l" + i, bx, ry, bw, rowH - ui.s(6), "<", null, true)) { app.step(cat, -1); changed = true; }
            ui.nine("slot", bx + bw + ui.s(4), ry, nameW, rowH - ui.s(6), 0xFFFFFFFF);
            float tx = bx + bw + ui.s(14);
            if (cat == Category.SKIN || cat == Category.HAIR_COLOR) {
                int col = cat == Category.SKIN ? CharDefs.SKIN_TONES[sel] : CharDefs.HAIR_TONES[sel];
                ui.b.rect(tx, ry + ui.s(7), ui.s(22), rowH - ui.s(20), col);
                ui.b.outline(tx, ry + ui.s(7), ui.s(22), rowH - ui.s(20), 1, 0xFF101010);
                tx += ui.s(30);
            }
            ui.text(CharDefs.part(cat, sel).name(), tx, ry + ui.s(9), 14, Ui.TXT);
            ui.textR(ord + " / " + n, bx + bw + nameW - ui.s(6), ry + ui.s(11), 11, Ui.TXT_DIM);
            if (ui.button("cc_r" + i, bx + bw + nameW + ui.s(8), ry, bw, rowH - ui.s(6), ">", null, true)) { app.step(cat, 1); changed = true; }
        }
        float by = y + cats.length * rowH + ui.s(6);
        if (ui.button("cc_rand", x, by, ui.s(200), ui.s(38), "RANDOMISE", "star", true)) {
            Appearance r = Appearance.random(new Rng(System.nanoTime()));
            System.arraycopy(r.sel, 0, app.sel, 0, app.sel.length);
            app.sex(app.female);                      // randomise keeps the body the player chose
            changed = true;
        }
        if (gearFrom != null) showGear = ui.toggle("cc_gear", x + ui.s(216), by + ui.s(8), ui.s(230), "Show equipped gear", showGear);
        if (gearFrom != null && gearFrom.prog.cosmetics.contains("face_paint")) gearFrom.facePaint = ui.toggle("cc_paint", x + ui.s(216), by + ui.s(36), ui.s(230), "Face paint (Prestige 11)", gearFrom.facePaint);

        // ---- preview: the assembled model, turned by dragging
        float px = x + listW + ui.s(28), ps = Math.min(h, w - listW - ui.s(28));
        ui.nine("slot", px, y, ps, ps, 0xFFFFFFFF);
        if (ui.hover(px, y, ps, ps) && ui.down) dragging = true;
        if (!ui.down) dragging = false;
        // 0.7: the turning can be stopped - the character then stands facing you, and still turns to a drag
        boolean was = autoSpin;
        autoSpin = ui.toggle("cc_spin", px + ui.s(12), y + ps + ui.s(8), ui.s(220), "Rotate the character", autoSpin);
        if (was && !autoSpin) spin = 0;
        if (dragging) spin -= (float) c.in.dx * 0.012f; else if (autoSpin) spin += ui.dt * 0.35f;
        if (gearFrom != null && showGear) ActorRenderer.playerLook(gearFrom, look);
        else { look.gear.head = look.gear.face = look.gear.vest = look.gear.pack = ""; look.gear.facePaint = false; look.gear.helmetMarks = false; }
        look.app = app;
        look.key = CharBuilder.key(look.app, look.gear);
        if (!look.key.equals(previewKey)) { preview = c.ar.flatten(look); previewKey = look.key; }
        c.icons.drawModel(ui.b, preview, px + ui.s(8), y + ui.s(8), ps - ui.s(16), (float) Math.PI + spin, 0.18f);
        ui.textC("drag to turn", px + ps / 2, y + ps - ui.s(22), 11, Ui.TXT_DIM);
        return changed;
    }
}
