package absentialis.character;

import absentialis.character.CharDefs.Category;
import absentialis.engine.util.Json;
import absentialis.engine.util.Rng;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A character's look as indices into the customization definition tables - nothing else. Saved as
 * definition ids per category (not meshes, not copies), so a profile survives table reordering and
 * the same record rebuilds the same model in first and third person.
 */
public final class Appearance {
    public final int[] sel = new int[Category.values().length];
    /**
     * 0.6: the body the parts are painted on. Female is the same rig with a narrower head, torso and arms and its
     * own set of hairstyles ({@link CharDefs#allowed}); every garment, animation and piece of gear is shared.
     */
    public boolean female;

    public int get(Category c) { return sel[c.ordinal()]; }
    public Appearance set(Category c, int index) { sel[c.ordinal()] = Math.floorMod(index, CharDefs.count(c)); return this; }
    public Appearance set(Category c, String id) { sel[c.ordinal()] = CharDefs.indexOf(c, c.name().toLowerCase() + "." + id); return this; }
    public CharDefs.Part part(Category c) { return CharDefs.part(c, sel[c.ordinal()]); }

    public Appearance copy() { Appearance a = new Appearance(); System.arraycopy(sel, 0, a.sel, 0, sel.length); a.female = female; return a; }

    public String key() { StringBuilder b = new StringBuilder(); for (int s : sel) b.append(s).append('.'); return b.append(female ? 'F' : 'M').append('.').toString(); }

    /** Steps a category to the next (dir = 1) or previous (-1) option this body may wear. */
    public Appearance step(Category c, int dir) {
        int n = CharDefs.count(c), i = sel[c.ordinal()];
        for (int k = 0; k < n; k++) { i = Math.floorMod(i + dir, n); if (CharDefs.allowed(c, i, female)) break; }
        sel[c.ordinal()] = i;
        return this;
    }

    /** Changes the body and moves any selection the new body does not have to the nearest one it does. */
    public Appearance sex(boolean f) {
        female = f;
        for (Category c : Category.values()) if (!CharDefs.allowed(c, sel[c.ordinal()], f)) {
            if (c == Category.FACIAL_HAIR) sel[c.ordinal()] = 0; else step(c, 1);
        }
        return this;
    }

    public static Appearance random(Rng r) {
        Appearance a = new Appearance();
        for (Category c : Category.values()) a.sel[c.ordinal()] = r.nextInt(CharDefs.count(c));
        // natural hair colours for strangers; the dyed tones are a player choice
        a.sel[Category.HAIR_COLOR.ordinal()] = r.nextInt(8);
        // drawn last, from its own stream, so every look generated before 0.6 keeps the parts it had
        a.sex(new Rng(a.key().hashCode() * 31L + 7).chance(CharDefs.FEMALE_SHARE));
        return a;
    }

    /** Maps the old two-field look (skin 0..4, outfit 0..3) onto the tables for pre-existing profiles. */
    public static Appearance legacy(int skin, int outfit) {
        Appearance a = new Appearance();
        a.set(Category.SKIN, new int[]{1, 3, 5, 8, 10}[Math.floorMod(skin, 5)]);
        a.set(Category.TORSO, new String[]{"field", "tracksuit", "workjacket", "tee"}[Math.floorMod(outfit, 4)]);
        a.set(Category.SLEEVES, "long").set(Category.LEGS, new String[]{"cargo", "jeans", "work", "tactical"}[Math.floorMod(outfit, 4)]);
        a.set(Category.HAIR, "crew").set(Category.HAIR_COLOR, 1).set(Category.FEET, "combat");
        return a;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Category c : Category.values()) m.put(c.name().toLowerCase(), part(c).id());
        m.put("sex", female ? "female" : "male");
        return m;
    }

    public static Appearance fromJson(Map<String, Object> m) {
        Appearance a = new Appearance();
        for (Category c : Category.values()) a.sel[c.ordinal()] = CharDefs.indexOf(c, Json.str(m, c.name().toLowerCase(), ""));
        a.female = "female".equals(Json.str(m, "sex", "male"));       // profiles from before 0.6 have no field: male, as they were
        return a;
    }
}
