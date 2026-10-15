package absentialis.character;

import static absentialis.character.Rig.*;

import absentialis.character.CharDefs.Category;
import absentialis.engine.render.Tex;
import absentialis.engine.util.Rng;
import absentialis.engine.voxel.VoxModel;
import java.util.List;

/**
 * Combines a small library of parts into one character model at runtime: naked body, then the
 * selected definition-table parts, then visible equipment (headgear, face cover, armour, pack) and
 * finally infected mutations. Equipment changes the silhouette; nothing here mutates a part.
 */
public final class CharBuilder {
    private CharBuilder() {}

    /** Visible equipment and faction dressing layered over an {@link Appearance}. */
    public static final class Gear {
        public String head = "", face = "", vest = "", pack = "";
        /** Uniform colour forced onto torso and sleeves (0 = keep the garment's own colour). */
        public int uniform, gearColor = Tex.rgb(70, 76, 64);
        public boolean facePaint, helmetMarks;
        /** The dead do not stare (0.6): lids drawn over the eyes of the model. */
        public boolean eyesClosed;
        /** A flashlight clipped to the chest rig (0.6): the object the light comes from when it is not on the gun. */
        public boolean torch;
        /** Infected archetype ("" for humans): walker, sprinter, bomber, tank ... */
        public String infected = "";

        public String key() { return head + "|" + face + "|" + vest + "|" + pack + "|" + uniform + "|" + gearColor + "|" + (facePaint ? 1 : 0) + (helmetMarks ? 1 : 0) + (eyesClosed ? 1 : 0) + (torch ? 1 : 0) + "|" + infected; }
    }

    private static final Category[] ORDER = {Category.HEAD, Category.LEGS, Category.FEET, Category.TORSO, Category.SLEEVES, Category.HANDS, Category.HAIR, Category.FACIAL_HAIR};

    public static String key(Appearance a, Gear g) { return a.key() + g.key(); }

    public static CharModel build(Appearance a, Gear g) {
        CharModel m = new CharModel();
        CharDefs.Ctx c = new CharDefs.Ctx();
        c.skin = CharDefs.SKIN_TONES[a.get(Category.SKIN)];
        c.hair = CharDefs.HAIR_TONES[a.get(Category.HAIR_COLOR)];
        c.infected = !g.infected.isEmpty();
        if (c.infected) { c.skin = Tex.mix(c.skin, Tex.rgb(146, 158, 118), 0.62f); c.hair = Tex.shade(c.hair, 0.7f); }
        int[] tc = CharDefs.torsoColors(a.part(Category.TORSO));
        c.cloth = g.uniform != 0 ? g.uniform : tc[0];
        c.cloth2 = g.uniform != 0 ? Tex.shade(g.uniform, 0.74f) : tc[1];
        if (c.infected && !"turned".equals(g.infected)) { c.cloth = Tex.shade(c.cloth, 0.62f); c.cloth2 = Tex.shade(c.cloth2, 0.55f); }
        CharDefs.body(m, c);
        for (Category cat : ORDER) a.part(cat).painter().paint(m, c);
        if (g.facePaint) { m.tint(HEAD, 2, 4, 2, 9, 5, 2, Tex.rgb(40, 60, 44)); m.tint(HEAD, 2, 8, 2, 9, 8, 2, Tex.rgb(30, 30, 30)); }
        if (g.eyesClosed) closeEyes(m, c);
        gear(m, g, c);
        if (g.torch) { int k = Tex.rgb(40, 42, 46); m.box(TORSO, 9, 7, 2, 10, 9, 2, k); m.box(TORSO, 9, 10, 1, 10, 10, 2, Tex.rgb(92, 96, 102)); m.box(TORSO, 9, 10, 1, 10, 10, 1, Tex.rgb(244, 236, 180)); m.box(TORSO, 9, 6, 2, 10, 6, 2, Tex.rgb(96, 88, 64)); }
        if (a.female && g.infected.isEmpty() || a.female && "turned".equals(g.infected)) feminine(m);
        if (c.infected && !"turned".equals(g.infected)) mutate(m, g.infected, c, a.key().hashCode());
        m.finish(key(a, g).hashCode());
        return m;
    }

    /** Lids over the eyes: the two rows the face painter put the irises and whites on, in the colour of the skin, with a lash line. */
    private static void closeEyes(CharModel m, CharDefs.Ctx c) {
        int lid = Tex.shade(c.skin, 0.9f), lash = Tex.shade(c.skin, 0.62f);
        for (int x : new int[]{3, 4, 7, 8}) if (m.get(HEAD, x, 6, 2) != 0) m.set(HEAD, x, 6, 2, x == 3 || x == 8 ? lid : lash);
    }

    /** Widths of the limbs in voxels, as built: a man's arm is 4 wide, 12 long (5 + 5 + 2) and 4 deep; a woman's is 3 wide. */
    public static final int ARM_W_MALE = 4, ARM_W_FEMALE = 3, ARM_LEN = 12, ARM_DEPTH = 4;

    /**
     * 1.1, the female body, rebuilt. Until now it was the male body squeezed ({@link #slim}): the torso lost its two
     * middle columns - and with them the middle of whatever was painted on it, the zip, the buttons, the plate carrier's
     * centre - the head lost a column of cheek on each side and its ears with it, the arms went a voxel shallower as well
     * as narrower, and the legs were left as they were, so the hips stood out past the ribs. This is a body of its own
     * proportions made from the same twelve groups:
     *
     *   head ...... the same skull as a man's (so every hairstyle, hat and mask fits as drawn), with a narrower jaw;
     *   torso ..... six wide instead of eight - one column off each side, taken from inside the outline so the centre of
     *               every garment and the colour of its edge both survive;
     *   arms ...... three wide instead of four, the full four deep and twelve long, brought in to meet the shoulder;
     *   legs ...... three wide instead of four, kept together at the middle, so the hips are as wide as the ribs.
     */
    private static void feminine(CharModel m) {
        collapse(m.groups[TORSO], 4, 9);
        for (int g : new int[]{L_UPPER_ARM, L_FOREARM, L_HAND}) { narrowArm(m.groups[g], true); narrowArm(m.groups[mirror(g)], false); }
        for (int g : new int[]{L_THIGH, L_SHIN}) { narrowLeg(m.groups[g], true); narrowLeg(m.groups[mirror(g)], false); }
        // the jaw: the two outer voxels of the chin row go, on the face and under the ears (hair behind them is left alone)
        VoxModel h = m.groups[HEAD];
        for (int z = 2; z <= 6; z++) { h.set(2, 2, z, 0); h.set(9, 2, z, 0); }
    }

    /** A woman's arm: the second column from the outside goes and the rest closes in toward the body (which is itself a column narrower). The depth is untouched. */
    private static void narrowArm(VoxModel v, boolean left) {
        int[] from = left ? new int[]{-1, -1, 0, 1, 3, 4} : new int[]{1, 2, 4, 5, -1, -1};
        int[] col = new int[v.sx];
        for (int y = 0; y < v.sy; y++) for (int z = 0; z < v.sz; z++) {
            for (int x = 0; x < v.sx; x++) col[x] = v.get(x, y, z);
            for (int x = 0; x < v.sx; x++) v.set(x, y, z, from[x] < 0 ? 0 : col[from[x]]);
        }
    }

    /** A woman's leg: one column narrower, the inner edge staying where it is (the legs meet in the middle as before). */
    private static void narrowLeg(VoxModel v, boolean left) {
        for (int y = 0; y < v.sy; y++) for (int z = 0; z < v.sz; z++) {
            if (left) { for (int x = 2; x >= 1; x--) v.set(x, y, z, v.get(x - 1, y, z)); v.set(0, y, z, 0); }
            else { for (int x = 3; x < v.sx - 1; x++) v.set(x, y, z, v.get(x + 1, y, z)); v.set(v.sx - 1, y, z, 0); }
        }
    }

    /** How many voxels wide the body of a limb group is at its widest row (shells of clothing over it included): gates measure the build with this. */
    public static int width(VoxModel v) {
        int best = 0;
        for (int y = 0; y < v.sy; y++) { int lo = v.sx, hi = -1; for (int x = 0; x < v.sx; x++) for (int z = 0; z < v.sz; z++) if (v.get(x, y, z) != 0) { lo = Math.min(lo, x); hi = Math.max(hi, x); } best = Math.max(best, hi - lo + 1); }
        return best;
    }

    /**
     * The lanky bodies of the Spewer and the Crawler (until 1.1 the female body was made this way too): the same twelve groups, narrower. The torso loses its two middle columns and closes up, so
     * everything painted on it - cloth, pockets, armour shells - comes in with it and stays symmetrical; the arms and
     * hands lose their outer column and one of depth and move in against the narrower shoulder; the head loses a
     * column of cheek on each side, with the ears and the hair beside them following.
     */
    private static void slim(CharModel m) {
        collapse(m.groups[TORSO], 6, 7);
        for (int g : new int[]{L_UPPER_ARM, L_FOREARM, L_HAND}) { thinArm(m.groups[g], true); thinArm(m.groups[mirror(g)], false); }
        VoxModel h = m.groups[HEAD];
        // below the crown only: the hair on top keeps its volume
        for (int y = 0; y < h.sy; y++) for (int z = 0; z < h.sz; z++) {
            if (y >= 10) continue;
            for (int x = 2; x >= 1; x--) h.set(x, y, z, h.get(x - 1, y, z));
            h.set(0, y, z, 0);
            for (int x = 9; x <= 10; x++) h.set(x, y, z, h.get(x + 1, y, z));
            h.set(11, y, z, 0);
        }
    }

    /** Removes columns a and b (a < b, adjacent) of a grid and closes both halves in toward them. */
    private static void collapse(VoxModel v, int a, int b) {
        for (int y = 0; y < v.sy; y++) for (int z = 0; z < v.sz; z++) {
            for (int x = a; x >= 1; x--) v.set(x, y, z, v.get(x - 1, y, z));
            v.set(0, y, z, 0);
            for (int x = b; x < v.sx - 1; x++) v.set(x, y, z, v.get(x + 1, y, z));
            v.set(v.sx - 1, y, z, 0);
        }
    }

    /** An arm one voxel narrower and one shallower, moved in to meet the narrower torso. */
    private static void thinArm(VoxModel v, boolean left) {
        // where each new column comes from (-1 = empty): the middle column goes, the rest moves one toward the torso
        int[] from = left ? new int[]{-1, -1, 0, 1, 3, 4} : new int[]{1, 2, 4, 5, -1, -1};
        int[] col = new int[v.sx];
        for (int y = 0; y < v.sy; y++) for (int z = 0; z < v.sz; z++) {
            for (int x = 0; x < v.sx; x++) col[x] = v.get(x, y, z);
            for (int x = 0; x < v.sx; x++) v.set(x, y, z, from[x] < 0 ? 0 : col[from[x]]);
        }
        // one voxel shallower: the back row of the limb goes and whatever was wrapped round it closes up
        for (int y = 0; y < v.sy; y++) for (int x = 0; x < v.sx; x++) { v.set(x, y, 4, v.get(x, y, 5)); v.set(x, y, 5, 0); }
    }

    private static void gear(CharModel m, Gear g, CharDefs.Ctx c) {
        int gc = g.gearColor, dark = Tex.shade(gc, 0.72f);
        if (!g.head.isEmpty()) m.box(HEAD, 0, 10, 0, 11, 13, 11, 0); // headgear sits on the skull: hair above the brow is tucked in
        switch (g.head) {
            case "cap" -> { m.box(HEAD, 2, 10, 2, 9, 10, 9, gc); m.shell(HEAD, 1, 9, 2, 1, 9, 9, gc); m.shell(HEAD, 10, 9, 2, 10, 9, 9, gc); m.shell(HEAD, 2, 9, 10, 9, 9, 10, gc); m.box(HEAD, 2, 9, 0, 9, 9, 1, dark); }
            case "helmet" -> {
                m.shell(HEAD, 1, 7, 1, 10, 10, 10, gc); m.box(HEAD, 2, 11, 2, 9, 11, 9, gc);
                m.box(HEAD, 2, 7, 1, 9, 9, 1, 0); m.box(HEAD, 1, 10, 1, 10, 10, 1, dark);
                m.shell(HEAD, 1, 4, 4, 1, 6, 4, dark); m.shell(HEAD, 10, 4, 4, 10, 6, 4, dark);
                if (g.helmetMarks) { m.box(HEAD, 5, 9, 10, 6, 10, 10, Tex.rgb(220, 216, 200)); m.box(HEAD, 10, 9, 5, 10, 9, 7, Tex.rgb(200, 60, 40)); }
            }
            case "baseball" -> {
                // 0.8: a baseball cap, not a miner's hat. The tall crown with a white block on its front read as a helmet
                // with a lamp. Now: a low, soft, rounded crown (one voxel shorter, its top inset), white only in the seam
                // lines and the button, and a peak that reaches well out in front of the brow.
                int blue = Tex.rgb(36, 78, 170), white = Tex.rgb(232, 232, 226), under = Tex.rgb(26, 56, 122);
                m.box(HEAD, 2, 10, 2, 9, 10, 9, blue); m.box(HEAD, 3, 11, 3, 8, 11, 8, blue);
                m.shell(HEAD, 1, 9, 2, 1, 10, 9, blue); m.shell(HEAD, 10, 9, 2, 10, 10, 9, blue); m.shell(HEAD, 2, 9, 10, 9, 10, 10, blue); m.shell(HEAD, 2, 9, 1, 9, 10, 1, blue);
                m.tint(HEAD, 5, 10, 1, 5, 11, 10, white);                                    // the seam from front to back
                m.box(HEAD, 5, 12, 5, 6, 12, 6, white);                                      // the button
                m.box(HEAD, 2, 9, 0, 9, 9, 0, blue); m.box(HEAD, 1, 9, 1, 10, 9, 1, blue);    // the peak: as far forward as the head's grid goes, and wider than the crown
                m.box(HEAD, 3, 8, 0, 8, 8, 0, under);                                        // and turned down at its front edge
            }
            case "fieldcap" -> {
                // 0.7: a makeshift helmet - a beaten steel pot on a strap. It covers the crown, the sides and the back; the whole of the face, brow to chin, is open
                int steel = Tex.rgb(112, 108, 98), rust = Tex.rgb(132, 86, 56), strap = Tex.rgb(78, 60, 40);
                m.box(HEAD, 2, 11, 3, 9, 12, 9, steel); m.shell(HEAD, 1, 8, 3, 1, 11, 10, steel); m.shell(HEAD, 10, 8, 3, 10, 11, 10, steel); m.shell(HEAD, 2, 7, 10, 9, 11, 10, steel);
                m.box(HEAD, 2, 10, 2, 9, 10, 2, steel);
                for (int k = 0; k < 7; k++) { int x = 2 + (k * 5) % 8, z = 4 + (k * 3) % 6; m.tint(HEAD, x, 11, z, x, 12, z, rust); }
                m.box(HEAD, 1, 3, 5, 1, 7, 5, strap); m.box(HEAD, 10, 3, 5, 10, 7, 5, strap); m.box(HEAD, 3, 1, 2, 8, 1, 2, strap);
            }
            case "bike" -> {
                int shell = Tex.rgb(226, 60, 40), foam = Tex.rgb(40, 40, 44);
                m.box(HEAD, 2, 10, 1, 9, 12, 10, shell); m.shell(HEAD, 1, 9, 1, 10, 10, 10, shell);
                for (int z = 2; z <= 9; z += 2) { m.box(HEAD, 4, 12, z, 4, 12, z, foam); m.box(HEAD, 7, 12, z + 1, 7, 12, z + 1, foam); }
                m.box(HEAD, 1, 9, 1, 10, 9, 1, foam); m.box(HEAD, 1, 3, 5, 1, 8, 5, Tex.rgb(20, 20, 22)); m.box(HEAD, 10, 3, 5, 10, 8, 5, Tex.rgb(20, 20, 22)); m.box(HEAD, 4, 1, 2, 7, 1, 2, Tex.rgb(20, 20, 22));
            }
            case "milhelmet" -> {
                // the Military Helmet: a shell over the whole head - crown, ears, neck, jaw - open only in a slot across the eyes
                m.shell(HEAD, 1, 1, 1, 10, 10, 10, gc); m.box(HEAD, 2, 11, 2, 9, 11, 9, gc); m.box(HEAD, 3, 12, 3, 8, 12, 8, gc);
                m.box(HEAD, 2, 6, 1, 9, 6, 1, 0);                                   // the eye slot
                m.box(HEAD, 1, 7, 1, 10, 7, 1, dark); m.box(HEAD, 1, 5, 1, 10, 5, 1, dark);
                m.box(HEAD, 4, 1, 1, 7, 2, 1, dark);                                 // chin guard
                if (g.helmetMarks) { m.box(HEAD, 5, 9, 10, 6, 10, 10, Tex.rgb(220, 216, 200)); m.box(HEAD, 10, 9, 5, 10, 9, 7, Tex.rgb(200, 60, 40)); }
            }
            case "beanie" -> { m.box(HEAD, 2, 10, 2, 9, 11, 9, gc); m.shell(HEAD, 1, 8, 1, 10, 9, 10, dark); m.box(HEAD, 3, 12, 3, 8, 12, 8, gc); }
            case "hood" -> { m.shell(HEAD, 1, 3, 3, 10, 9, 10, gc); m.box(HEAD, 1, 10, 1, 10, 11, 10, gc); m.shell(HEAD, 1, 9, 1, 10, 9, 2, dark); }
            default -> Gear08.head(m, g.head);
        }
        switch (g.face) {
            case "gasmask" -> {
                int rubber = Tex.rgb(38, 42, 40);
                m.box(HEAD, 2, 2, 1, 9, 7, 1, rubber); m.box(HEAD, 3, 5, 0, 4, 6, 0, Tex.rgb(120, 150, 150)); m.box(HEAD, 7, 5, 0, 8, 6, 0, Tex.rgb(120, 150, 150));
                m.box(HEAD, 5, 2, 0, 6, 3, 0, Tex.rgb(80, 84, 80)); m.box(HEAD, 1, 5, 2, 1, 5, 9, rubber); m.box(HEAD, 10, 5, 2, 10, 5, 9, rubber);
            }
            case "balaclava" -> { int k = Tex.rgb(32, 34, 38); for (int x = 2; x <= 9; x++) for (int y = 2; y <= 9; y++) for (int z = 2; z <= 9; z++) { boolean eyes = z == 2 && y == 6 && x >= 3 && x <= 8; if (!eyes && m.get(HEAD, x, y, z) != 0) m.set(HEAD, x, y, z, k); } m.box(HEAD, 5, 4, 1, 6, 5, 1, k); }
            case "sunglasses" -> {
                int k = Tex.rgb(14, 14, 16);
                m.box(HEAD, 2, 6, 1, 4, 6, 1, k); m.box(HEAD, 7, 6, 1, 9, 6, 1, k); m.box(HEAD, 5, 6, 1, 6, 6, 1, Tex.rgb(34, 34, 38));
                m.box(HEAD, 1, 6, 2, 1, 6, 6, k); m.box(HEAD, 10, 6, 2, 10, 6, 6, k);
                m.set(HEAD, 3, 6, 1, Tex.rgb(60, 60, 70)); m.set(HEAD, 8, 6, 1, Tex.rgb(60, 60, 70));
            }
            case "bandana" -> { int k = Tex.rgb(150, 32, 30); m.box(HEAD, 2, 2, 1, 9, 4, 1, k); m.box(HEAD, 1, 3, 2, 1, 4, 9, k); m.box(HEAD, 10, 3, 2, 10, 4, 9, k); m.box(HEAD, 2, 3, 10, 9, 4, 10, k); m.box(HEAD, 4, 1, 1, 7, 1, 1, k); }
            default -> {}
        }
        switch (g.vest) {
            case "soft" -> {
                // Soft Body Armor: a simple light-green bulletproof vest
                int k = Tex.rgb(138, 176, 118), dk = Tex.rgb(104, 138, 90);
                m.shell(TORSO, 2, 3, 3, 11, 10, 8, k); m.tint(TORSO, 3, 3, 4, 10, 10, 7, k); m.box(TORSO, 4, 10, 3, 5, 11, 8, k); m.box(TORSO, 8, 10, 3, 9, 11, 8, k);
                m.tint(TORSO, 2, 5, 2, 11, 5, 9, dk); m.tint(TORSO, 2, 8, 2, 11, 8, 9, dk);
            }
            case "body" -> {
                // Body Armor: light blue, a plate on the chest and protection the length of both arms
                int k = Tex.rgb(120, 170, 214), dk = Tex.rgb(84, 126, 170), pad = Tex.rgb(150, 196, 232);
                m.shell(TORSO, 2, 3, 3, 11, 11, 8, k); m.tint(TORSO, 3, 3, 4, 10, 10, 7, k);
                m.box(TORSO, 4, 11, 3, 5, 12, 8, dk); m.box(TORSO, 8, 11, 3, 9, 12, 8, dk);
                m.box(TORSO, 4, 5, 2, 9, 9, 2, pad); m.box(TORSO, 4, 5, 9, 9, 9, 9, dk);
                m.shellBoth(L_UPPER_ARM, 0, 1, 0, 5, 7, 5, k); m.shellBoth(L_FOREARM, 0, 1, 0, 5, 5, 5, k);
                m.tintBoth(L_UPPER_ARM, 0, 6, 0, 5, 7, 5, pad); m.tintBoth(L_FOREARM, 0, 1, 0, 5, 1, 5, dk);
            }
            case "heavy" -> {
                // Heavy Body Armor: thick black metal - banded breastplate, pauldrons, a gorget, plate down the arms
                int k = Tex.rgb(24, 24, 28), edge = Tex.rgb(58, 60, 66), rivet = Tex.rgb(110, 112, 118);
                m.shell(TORSO, 1, 2, 2, 12, 11, 9, k); m.tint(TORSO, 2, 2, 3, 11, 11, 8, k);
                m.box(TORSO, 4, 11, 3, 9, 12, 8, k);                                  // gorget
                m.box(TORSO, 0, 9, 3, 2, 12, 8, k); m.box(TORSO, 11, 9, 3, 13, 12, 8, k);   // pauldrons
                for (int y = 3; y <= 9; y += 3) { m.tint(TORSO, 1, y, 2, 12, y, 2, edge); for (int x = 3; x <= 10; x += 3) m.tint(TORSO, x, y + 1, 2, x, y + 1, 2, rivet); }
                m.shellBoth(L_UPPER_ARM, 0, 1, 0, 5, 7, 5, k); m.shellBoth(L_FOREARM, 0, 2, 0, 5, 5, 5, k);
                m.tintBoth(L_UPPER_ARM, 0, 4, 0, 5, 4, 5, edge);
            }
            case "plate", "mil" -> {
                boolean plate = g.vest.equals("plate");
                m.shell(TORSO, 2, 3, 3, 11, 10, 8, gc); m.tint(TORSO, 3, 3, 4, 10, 10, 7, gc);
                m.box(TORSO, 4, 10, 3, 5, 12, 8, dark); m.box(TORSO, 8, 10, 3, 9, 12, 8, dark);
                m.box(TORSO, 4, 5, 2, 9, 9, 2, plate ? dark : gc);
                for (int x = 3; x <= 9; x += 3) m.box(TORSO, x, 3, 2, x + 1, 4, 2, Tex.rgb(96, 88, 64));   // magazine pouches
                if (plate) { m.box(TORSO, 4, 5, 9, 9, 9, 9, dark); m.box(TORSO, 1, 9, 4, 1, 10, 7, dark); m.box(TORSO, 12, 9, 4, 12, 10, 7, dark); }
            }
            default -> Gear08.vest(m, g.vest);
        }
        switch (g.pack) {
            case "case" -> { m.box(TORSO, 5, 4, 8, 8, 8, 9, Tex.rgb(66, 58, 44)); m.box(TORSO, 5, 9, 8, 8, 9, 8, Tex.rgb(40, 34, 28)); }
            case "small" -> { int k = Tex.rgb(78, 70, 52); m.box(TORSO, 4, 4, 8, 9, 10, 10, k); m.box(TORSO, 5, 5, 11, 8, 8, 11, Tex.shade(k, 0.8f)); m.box(TORSO, 4, 11, 4, 4, 11, 8, DARK_STRAP); m.box(TORSO, 9, 11, 4, 9, 11, 8, DARK_STRAP); }
            case "large" -> {
                int k = Tex.rgb(70, 78, 56);
                m.box(TORSO, 3, 2, 8, 10, 12, 11, k); m.box(TORSO, 4, 3, 11, 9, 7, 11, Tex.shade(k, 0.78f));
                m.box(TORSO, 3, 13, 8, 10, 13, 10, Tex.rgb(120, 104, 72));   // bedroll
                m.box(TORSO, 2, 4, 9, 2, 8, 10, Tex.shade(k, 0.85f)); m.box(TORSO, 11, 4, 9, 11, 8, 10, Tex.shade(k, 0.85f));
                m.box(TORSO, 4, 11, 4, 4, 12, 8, DARK_STRAP); m.box(TORSO, 9, 11, 4, 9, 12, 8, DARK_STRAP);
            }
            default -> Gear08.pack(m, g.pack);
        }
    }

    private static final int DARK_STRAP = Tex.rgb(40, 36, 30);

    /** Readable infected silhouettes: every archetype's role is legible from its outline. */
    private static void mutate(CharModel m, String arch, CharDefs.Ctx c, int seed) {
        int glow = Tex.rgb(226, 204, 84), gore = Tex.rgb(96, 30, 28), bile = Tex.rgb(170, 196, 70);
        // torn clothing and sores everywhere
        for (int g = 0; g < GROUPS; g++) {
            var v = m.groups[g];
            for (int x = 0; x < v.sx; x++) for (int y = 0; y < v.sy; y++) for (int z = 0; z < v.sz; z++) {
                if (v.get(x, y, z) == 0) continue;
                float h = Rng.hash(x * 3 + g, y * 5, z * 7, seed);
                if (h < 0.07f) v.set(x, y, z, gore); else if (h < 0.15f && g != HEAD) v.set(x, y, z, c.skin);
            }
        }
        m.set(HEAD, 4, 6, 2, glow); m.set(HEAD, 7, 6, 2, glow); m.set(HEAD, 3, 6, 2, Tex.shade(glow, 0.7f)); m.set(HEAD, 8, 6, 2, Tex.shade(glow, 0.7f));
        m.box(HEAD, 4, 2, 2, 7, 3, 2, Tex.rgb(50, 14, 14));
        switch (arch) {
            case "bomber" -> {
                // 0.7, the Bomber: character height, bloated, green, and covered in the bile it is full of
                int green = Tex.rgb(96, 150, 58), belly = Tex.mix(c.skin, green, 0.72f), dk = Tex.rgb(31, 74, 18);
                m.groups[TORSO].sphere(7, 5.5f, 5.2f, 6.1f, belly);
                m.groups[TORSO].sphere(7, 10.5f, 6f, 4.6f, belly);
                m.shellBoth(L_THIGH, 0, 0, 0, 5, 6, 5, belly); m.shellBoth(L_UPPER_ARM, 0, 1, 0, 5, 6, 5, belly);
                m.box(HEAD, 3, 1, 2, 8, 2, 5, belly);   // goitre
                m.box(HEAD, 4, 1, 1, 7, 2, 1, bile);
                // the skin has gone the colour of what is under it, and it runs with the stuff: streaks from the mouth, down the belly, off the hands
                for (int g = 0; g < GROUPS; g++) {
                    var v = m.groups[g];
                    for (int x = 0; x < v.sx; x++) for (int y = 0; y < v.sy; y++) for (int z = 0; z < v.sz; z++) {
                        int col = v.get(x, y, z);
                        if (col == 0 || col == glow) continue;
                        float h = Rng.hash(x * 5 + g * 3, y * 3, z * 7, seed + 91), run = Rng.hash(x * 5 + g * 3, 1, z * 7, seed + 17);
                        v.set(x, y, z, run < 0.22f ? (h < 0.5f ? dk : bile) : h < 0.12f ? dk : Tex.mix(col, green, 0.38f));
                    }
                }
            }
            case "spewer" -> {
                // 0.7, the Spewer: lanky, the lower jaw gone, and poison running out of the hole where it was
                int raw = Tex.rgb(96, 22, 24), tongue = Tex.rgb(146, 44, 52), poison = Tex.rgb(134, 216, 60), tooth = Tex.rgb(214, 206, 180);
                slim(m);
                var h = m.groups[HEAD];
                for (int x = 0; x < h.sx; x++) for (int y = 0; y <= 4; y++) for (int z = 0; z <= 6; z++) h.set(x, y, z, 0);      // the jaw, and the chin with it
                m.box(HEAD, 3, 5, 2, 8, 5, 6, raw);                                   // the roof of the mouth, open to the air
                for (int x = 3; x <= 8; x += 2) m.set(HEAD, x, 4, 2, tooth);            // what is left of the top teeth
                m.box(HEAD, 5, 1, 4, 6, 4, 5, tongue); m.set(HEAD, 5, 0, 4, tongue);    // the tongue hangs where the jaw was
                m.box(HEAD, 4, 3, 7, 7, 4, 8, raw);                                   // the throat
                m.set(HEAD, 4, 4, 3, poison); m.set(HEAD, 7, 3, 3, poison); m.set(HEAD, 6, 2, 3, poison); m.set(HEAD, 5, 0, 5, poison); m.set(HEAD, 3, 4, 4, poison);
                // it has run down the front of it for a long time
                for (int x = 4; x <= 9; x++) for (int y = 3; y <= 12; y++) { if (Rng.hash(x, y / 3, 5, seed) > 0.5f) continue; for (int z = 0; z < 5; z++) if (m.get(TORSO, x, y, z) != 0) { m.set(TORSO, x, y, z, Rng.hash(x, y, 9, seed) < 0.6f ? poison : Tex.shade(poison, 0.6f)); break; } }
                for (int y = 4; y <= 9; y += 2) m.tint(TORSO, 4, y, 4, 9, y, 4, Tex.shade(c.skin, 0.62f));   // ribs
                m.tintBoth(L_HAND, 0, 0, 0, 5, 2, 5, Tex.shade(poison, 0.7f));
            }
            case "crawler" -> {
                // 1.0, the Crawler: a human body with the skin off. Every surface is muscle - dark red, wet, with the pale lines
                // of sinew across it and the bands of each muscle showing; nothing is worn. No hair, no ears to speak of, and no
                // eyes: where they were the flesh has closed over. The top of the skull is open. The mouth is all teeth, and the
                // hands and feet end in bone.
                int muscle = Tex.rgb(126, 34, 32), dark = Tex.rgb(84, 18, 20), wet = Tex.rgb(154, 50, 46), sinew = Tex.rgb(216, 180, 162), bone = Tex.rgb(228, 216, 192), brain = Tex.rgb(204, 128, 126);
                slim(m);
                var h = m.groups[HEAD];
                for (int x = 0; x < h.sx; x++) for (int y = 0; y < h.sy; y++) for (int z = 0; z < h.sz; z++) if (h.get(x, y, z) != 0 && (x < 2 || x > 9 || y > 11 || z < 2 || z > 9)) h.set(x, y, z, 0);
                for (int g : new int[]{HEAD, TORSO}) {
                    var v = m.groups[g];
                    for (int x = 0; x < v.sx; x++) for (int y = 0; y < v.sy; y++) for (int z = 0; z < v.sz; z++) {
                        if (v.get(x, y, z) == 0) continue;
                        float k = Rng.hash(x, y / 2, z, seed + g * 7);
                        v.set(x, y, z, (y + x / 3) % 4 == 0 ? dark : k < 0.10f ? sinew : k < 0.38f ? wet : muscle);
                    }
                }
                m.box(HEAD, 3, 11, 3, 8, 11, 8, brain); m.box(HEAD, 4, 12, 4, 7, 12, 7, brain); m.box(HEAD, 5, 11, 3, 6, 12, 8, Tex.shade(brain, 0.8f));   // the skull is open
                m.box(HEAD, 3, 2, 1, 8, 3, 1, bone); m.box(HEAD, 3, 1, 2, 8, 1, 4, dark);                // teeth to the jaw hinge, and the dark behind them
                for (int x = 3; x <= 8; x += 2) m.set(HEAD, x, 4, 1, bone);                                 // the top row, longer
                m.tint(HEAD, 2, 5, 2, 9, 9, 2, muscle); m.tint(HEAD, 3, 7, 2, 4, 7, 2, dark); m.tint(HEAD, 7, 7, 2, 8, 7, 2, dark);   // no eyes: two closed hollows
                for (int y = 3; y <= 10; y += 2) m.tint(TORSO, 3, y, 2, 10, y, 3, sinew);                    // ribs through the muscle
                m.tint(TORSO, 6, 1, 8, 7, 12, 9, sinew);                                                    // the spine, standing proud of the back
                m.shellBoth(L_UPPER_ARM, 0, 0, 0, 5, 6, 5, muscle); m.shellBoth(L_FOREARM, 0, 0, 0, 5, 5, 5, wet); m.shellBoth(L_HAND, 0, 0, 0, 5, 2, 5, dark);
                m.shellBoth(L_THIGH, 0, 0, 0, 5, 6, 5, muscle); m.shellBoth(L_SHIN, 0, 0, 1, 5, 6, 6, wet);
                m.tintBoth(L_UPPER_ARM, 0, 0, 0, 5, 6, 5, muscle); m.tintBoth(L_FOREARM, 0, 0, 0, 5, 5, 5, wet); m.tintBoth(L_THIGH, 0, 0, 0, 5, 6, 5, muscle); m.tintBoth(L_SHIN, 0, 0, 0, 5, 6, 7, wet);
                m.tintBoth(L_UPPER_ARM, 0, 3, 0, 5, 3, 5, sinew); m.tintBoth(L_THIGH, 0, 3, 0, 5, 3, 5, sinew); m.tintBoth(L_FOREARM, 0, 4, 0, 5, 4, 5, dark); m.tintBoth(L_SHIN, 0, 4, 0, 5, 4, 7, dark);
                m.tintBoth(L_HAND, 0, 0, 0, 5, 0, 5, bone);                                                 // claws
            }
            case "tyrant" -> {
                // 1.0, the Tyrant: a head taller than the Butcher and half as wide again - a hulking man, hairless, the skin a
                // greenish grey with nothing alive in it, in a long dark trench coat buttoned to the neck. The coat is stained
                // down the front and to the elbows with other people's blood; heavy boots; bare grey hands.
                int skin = Tex.rgb(128, 140, 122), skinD = Tex.rgb(98, 110, 96), coat = Tex.rgb(42, 44, 42), coatD = Tex.rgb(26, 28, 27), coatL = Tex.rgb(58, 60, 56), blood = Tex.rgb(116, 16, 16), bloodD = Tex.rgb(78, 12, 14), strap = Tex.rgb(74, 64, 50), boot = Tex.rgb(20, 20, 21), eye = Tex.rgb(226, 224, 210);
                var h = m.groups[HEAD];
                for (int x = 0; x < h.sx; x++) for (int y = 0; y < h.sy; y++) for (int z = 0; z < h.sz; z++) {
                    if (h.get(x, y, z) == 0) continue;
                    if (x < 2 || x > 9 || y > 11 || z < 2 || z > 9) h.set(x, y, z, 0); else h.set(x, y, z, (x + y + z) % 5 == 0 ? skinD : skin);
                }
                m.box(HEAD, 3, 8, 2, 8, 8, 2, skinD);                                                       // a heavy brow
                m.set(HEAD, 3, 7, 2, eye); m.set(HEAD, 8, 7, 2, eye); m.set(HEAD, 4, 7, 2, Tex.rgb(30, 30, 30)); m.set(HEAD, 7, 7, 2, Tex.rgb(30, 30, 30));
                m.box(HEAD, 4, 3, 2, 7, 3, 2, Tex.rgb(70, 78, 70));                                         // a mouth like a cut
                m.shell(TORSO, 0, 0, 2, 13, 13, 9, coat); m.tint(TORSO, 1, 0, 2, 12, 13, 9, coat);           // the coat, shoulder to hip
                m.box(TORSO, 0, 11, 2, 2, 13, 9, coatL); m.box(TORSO, 11, 11, 2, 13, 13, 9, coatL);         // shoulders like a doorway
                m.box(TORSO, 3, 12, 1, 10, 13, 2, coatD); m.box(TORSO, 3, 12, 8, 10, 13, 9, coatD);         // the collar, turned up
                m.tint(TORSO, 6, 0, 2, 7, 12, 2, coatD);                                                    // where it buttons
                for (int y = 2; y <= 11; y += 3) m.set(TORSO, 5, y, 1, strap);                              // the buttons
                m.tint(TORSO, 0, 4, 2, 13, 4, 9, strap);                                                    // a belt over it
                for (int x = 1; x <= 12; x++) for (int y = 0; y <= 12; y++) for (int z = 0; z <= 3; z++) {    // and what is on the front of it
                    if (m.get(TORSO, x, y, z) == 0) continue;
                    float k = Rng.hash(x / 2, y / 3, 11, seed), k2 = Rng.hash(x, y, 13, seed);
                    if (k < 0.34f && k2 < 0.8f || y < 4 && k2 < 0.3f) m.set(TORSO, x, y, z, k2 < 0.35f ? bloodD : blood);
                }
                m.shellBoth(L_UPPER_ARM, 0, 0, 0, 5, 6, 5, coat); m.tintBoth(L_UPPER_ARM, 0, 0, 0, 5, 6, 5, coat);
                m.shellBoth(L_FOREARM, 0, 0, 0, 5, 5, 5, coat); m.tintBoth(L_FOREARM, 0, 0, 0, 5, 5, 5, coat); m.tintBoth(L_FOREARM, 0, 0, 0, 5, 2, 5, blood); m.tintBoth(L_FOREARM, 0, 4, 0, 5, 4, 5, coatD);
                m.shellBoth(L_HAND, 0, 0, 0, 5, 2, 5, skin); m.tintBoth(L_HAND, 0, 0, 0, 5, 2, 5, skin); m.tintBoth(L_HAND, 0, 0, 0, 5, 0, 5, bloodD);
                m.shellBoth(L_THIGH, 0, 0, 0, 5, 6, 6, coat); m.tintBoth(L_THIGH, 0, 0, 0, 5, 6, 6, coat); m.tintBoth(L_THIGH, 0, 0, 0, 5, 0, 6, coatD);   // the skirts of the coat, to the knee
                m.shellBoth(L_SHIN, 0, 0, 1, 5, 6, 6, boot); m.tintBoth(L_SHIN, 0, 0, 0, 5, 6, 7, boot); m.tintBoth(L_SHIN, 0, 5, 0, 5, 6, 7, coatD);
            }
            case "tank" -> {
                // 0.7, the Tank: massive, muscled, bald, in jeans. Arms like legs: it walks on its knuckles
                int hide = Tex.shade(c.skin, 0.82f), shadow = Tex.shade(c.skin, 0.62f), denim = Tex.rgb(54, 76, 116), seam = Tex.rgb(36, 52, 84), knuckle = Tex.rgb(150, 112, 96);
                m.shell(TORSO, 1, 2, 3, 12, 12, 9, hide); m.tint(TORSO, 3, 2, 4, 10, 11, 7, hide);
                m.box(TORSO, 0, 9, 3, 4, 13, 9, hide); m.box(TORSO, 9, 9, 3, 13, 13, 9, hide);         // the shoulders
                m.box(TORSO, 3, 8, 2, 6, 11, 2, hide); m.box(TORSO, 7, 8, 2, 10, 11, 2, hide);         // the chest, in two slabs
                m.tint(TORSO, 6, 3, 2, 7, 11, 3, shadow);                                             // the line down the middle of it
                for (int y = 3; y <= 7; y += 2) m.tint(TORSO, 4, y, 3, 9, y, 3, shadow);                // the stomach
                m.box(TORSO, 1, 0, 3, 12, 2, 9, denim); m.box(TORSO, 1, 2, 3, 12, 2, 9, Tex.rgb(64, 46, 30));   // jeans, and a belt
                m.shellBoth(L_UPPER_ARM, 0, 0, 0, 5, 6, 5, hide); m.shellBoth(L_FOREARM, 0, 0, 0, 5, 5, 5, hide); m.shellBoth(L_HAND, 0, 0, 0, 5, 2, 5, hide);
                m.tintBoth(L_HAND, 0, 0, 0, 5, 0, 5, knuckle);                                         // skinned knuckles
                m.shellBoth(L_THIGH, 0, 0, 0, 5, 6, 5, denim); m.shellBoth(L_SHIN, 0, 0, 1, 5, 6, 6, denim);
                m.tintBoth(L_THIGH, 0, 0, 0, 5, 6, 5, denim); m.tintBoth(L_SHIN, 0, 2, 0, 5, 6, 7, denim);
                m.tintBoth(L_THIGH, 5, 0, 0, 5, 6, 5, seam);
                // bald: whatever the hair painter put there is skin now, and nothing stands proud of the skull
                var h = m.groups[HEAD];
                for (int x = 0; x < h.sx; x++) for (int y = 0; y < h.sy; y++) for (int z = 0; z < h.sz; z++) {
                    if (h.get(x, y, z) == 0) continue;
                    if (x < 2 || x > 9 || y > 11 || z < 2 || z > 9) h.set(x, y, z, 0); else if (y >= 9 || z >= 8) h.set(x, y, z, hide);
                }
                m.box(HEAD, 3, 8, 2, 8, 8, 2, shadow);                                                // a brow like a shelf
            }
            case "gladiator" -> {
                // 0.7, the Gladiator: hulking, blind - the face sewn shut - in a medieval helmet, a breastplate and clawed gauntlets.
                // The plate does not meet at the back, and what shows there is the only soft thing on it
                int steel = Tex.rgb(132, 136, 142), dkSteel = Tex.rgb(84, 88, 96), brass = Tex.rgb(150, 120, 60), thread = Tex.rgb(28, 20, 18), weak = Tex.rgb(214, 48, 44);
                var h = m.groups[HEAD];
                for (int x = 0; x < h.sx; x++) for (int y = 0; y < h.sy; y++) for (int z = 0; z < h.sz; z++) if (h.get(x, y, z) != 0 && (x < 2 || x > 9 || y > 11 || z < 2 || z > 9)) h.set(x, y, z, 0);
                m.tint(HEAD, 2, 2, 2, 9, 9, 2, c.skin);                                              // no eyes, no mouth: skin, drawn over
                for (int x = 3; x <= 8; x++) { if (x % 2 == 1) { m.set(HEAD, x, 6, 2, thread); m.set(HEAD, x, 3, 2, thread); } else { m.set(HEAD, x, 7, 2, Tex.shade(c.skin, 0.7f)); } }
                m.set(HEAD, 4, 5, 2, thread); m.set(HEAD, 7, 5, 2, thread); m.set(HEAD, 5, 2, 2, thread); m.set(HEAD, 6, 4, 2, thread);
                m.shell(HEAD, 1, 8, 1, 10, 12, 10, steel);                                           // the dome
                m.shell(HEAD, 1, 2, 3, 1, 8, 10, steel); m.shell(HEAD, 10, 2, 3, 10, 8, 10, steel); m.shell(HEAD, 1, 2, 10, 10, 8, 10, steel);   // cheeks and the back of the neck
                m.box(HEAD, 5, 4, 1, 6, 9, 1, dkSteel);                                              // the nasal
                m.box(HEAD, 1, 8, 1, 10, 8, 1, brass);                                               // the brow band
                m.box(HEAD, 5, 12, 2, 6, 13, 10, dkSteel);                                           // the crest
                for (int z = 3; z <= 9; z += 3) { m.set(HEAD, 1, 9, z, brass); m.set(HEAD, 10, 9, z, brass); }
                m.shell(TORSO, 1, 2, 2, 12, 11, 7, steel); m.tint(TORSO, 2, 2, 3, 11, 11, 6, steel);     // the breastplate: front and sides
                for (int y = 4; y <= 10; y += 3) m.tint(TORSO, 1, y, 2, 12, y, 2, dkSteel);
                m.box(TORSO, 6, 3, 1, 7, 10, 1, dkSteel);                                            // the keel down the front
                m.box(TORSO, 0, 9, 3, 2, 13, 8, steel); m.box(TORSO, 11, 9, 3, 13, 13, 8, steel);     // pauldrons
                m.box(TORSO, 0, 13, 4, 2, 13, 7, brass); m.box(TORSO, 11, 13, 4, 13, 13, 7, brass);
                m.shell(TORSO, 2, 2, 8, 11, 11, 9, Tex.shade(c.skin, 0.8f));                           // the bare back
                m.box(TORSO, 5, 5, 9, 8, 9, 9, weak); m.box(TORSO, 6, 6, 10, 7, 8, 10, Tex.rgb(244, 96, 72));   // and the thing growing on it
                m.set(TORSO, 5, 9, 10, weak); m.set(TORSO, 8, 5, 10, weak);
                m.shellBoth(L_UPPER_ARM, 0, 0, 0, 5, 6, 5, Tex.shade(c.skin, 0.84f));
                m.shellBoth(L_FOREARM, 0, 0, 0, 5, 5, 5, steel); m.tintBoth(L_FOREARM, 0, 0, 0, 5, 5, 5, steel); m.tintBoth(L_FOREARM, 0, 4, 0, 5, 4, 5, brass);
                m.shellBoth(L_HAND, 0, 0, 0, 5, 2, 5, dkSteel); m.tintBoth(L_HAND, 0, 0, 0, 5, 2, 5, dkSteel);
                m.shellBoth(L_THIGH, 0, 0, 0, 5, 6, 5, Tex.shade(c.cloth2, 0.8f)); m.shellBoth(L_SHIN, 0, 0, 1, 5, 6, 6, dkSteel);
            }
            case "butcher" -> {
                // 0.7, the Butcher: stocky, fat, middle-aged, bloodied - a burlap sack over the head with two holes and red-rimmed eyes in them
                int sack = Tex.rgb(152, 126, 86), weave = Tex.rgb(120, 98, 64), rope = Tex.rgb(86, 66, 40), apron = Tex.rgb(198, 190, 172), blood = Tex.rgb(122, 18, 18), rim = Tex.rgb(206, 42, 38), white = Tex.rgb(232, 222, 198);
                var h = m.groups[HEAD];
                for (int x = 0; x < h.sx; x++) for (int y = 0; y < h.sy; y++) for (int z = 0; z < h.sz; z++) h.set(x, y, z, 0);
                for (int x = 1; x <= 10; x++) for (int y = 2; y <= 12; y++) for (int z = 1; z <= 10; z++) {
                    boolean corner = (x == 1 || x == 10) && (z == 1 || z == 10) || y == 12 && (x == 1 || x == 10 || z == 1 || z == 10);
                    if (!corner) h.set(x, y, z, (y + (x + z) / 3) % 3 == 0 || Rng.hash(x, y, z, seed) < 0.16f ? weave : sack);
                }
                m.box(HEAD, 1, 2, 1, 10, 2, 10, rope); m.box(HEAD, 5, 0, 0, 6, 2, 0, rope);            // tied at the neck, the ends hanging
                m.box(HEAD, 8, 12, 4, 10, 13, 6, weave);                                              // the corner of the sack, flopped over
                for (int ex : new int[]{3, 7}) { m.box(HEAD, ex, 6, 1, ex + 1, 7, 1, rim); m.set(HEAD, ex, 6, 1, white); m.set(HEAD, ex + 1, 6, 1, Tex.rgb(20, 10, 10)); }
                m.set(HEAD, 5, 4, 1, blood); m.set(HEAD, 6, 3, 1, blood); m.set(HEAD, 4, 9, 1, blood);
                int shirt = Tex.shade(c.cloth, 1.15f);
                m.groups[TORSO].sphere(7, 5.6f, 5.2f, 5.5f, shirt);                                   // the gut
                m.groups[TORSO].sphere(7, 10f, 6f, 4.4f, shirt);
                // a butcher's apron over the front of it, and what is on the apron
                for (int x = 3; x <= 10; x++) for (int y = 0; y <= 11; y++) for (int z = 0; z < 6; z++) if (m.get(TORSO, x, y, z) != 0) { float bl = Rng.hash(x / 2, y / 2, 3, seed); m.set(TORSO, x, y, z, bl < 0.42f || y < 4 && bl < 0.7f ? (bl < 0.2f ? Tex.shade(blood, 0.7f) : blood) : apron); break; }
                m.box(TORSO, 4, 12, 3, 4, 13, 8, apron); m.box(TORSO, 9, 12, 3, 9, 13, 8, apron);        // the straps
                m.shellBoth(L_UPPER_ARM, 0, 1, 0, 5, 6, 5, c.skin); m.shellBoth(L_THIGH, 0, 0, 0, 5, 6, 5, Tex.shade(c.cloth2, 0.7f));
                m.tintBoth(L_FOREARM, 0, 0, 0, 5, 3, 5, blood); m.tintBoth(L_HAND, 0, 0, 0, 5, 2, 5, Tex.shade(blood, 0.8f));
            }
            case "sprinter", "screecher" -> {
                for (int y = 4; y <= 9; y += 2) m.tint(TORSO, 4, y, 4, 9, y, 4, Tex.shade(c.skin, 0.62f));   // ribs through the shirt
                m.box(HEAD, 4, 0, 2, 7, 1, 4, Tex.rgb(40, 10, 10));   // unhinged jaw
                m.box(HEAD, 4, 1, 1, 7, 1, 1, Tex.rgb(214, 206, 180));
                m.tintBoth(L_FOREARM, 1, 0, 1, 4, 2, 4, gore);
            }
            case "shambler" -> { m.shell(TORSO, 2, 1, 3, 11, 11, 8, Tex.shade(c.cloth, 0.8f)); m.box(TORSO, 4, 4, 3, 6, 8, 3, gore); }
            default -> m.box(TORSO, 4, 5, 4, 6, 8, 4, gore);
        }
    }

    // ------------------------------------------------------------------ faction dressing for NPCs (same tables as the player)
    private static Appearance pick(Rng r, String[] torso, String[] sleeves, String[] legs, String[] feet, String[] hands) {
        Appearance a = Appearance.random(r);
        a.set(Category.TORSO, r.pick(torso)).set(Category.SLEEVES, r.pick(sleeves)).set(Category.LEGS, r.pick(legs)).set(Category.FEET, r.pick(feet)).set(Category.HANDS, r.pick(hands));
        return a;
    }

    /** Seeded look for a faction member; armour/helmet/pack come from what the agent actually carries. */
    public static Appearance factionLook(String faction, long seed, Gear g) {
        Rng r = new Rng(seed * 2654435761L + 17);
        switch (faction) {
            case "military" -> {
                g.gearColor = Tex.rgb(78, 88, 62);
                return pick(r, new String[]{"field"}, new String[]{"long", "camo", "rolled"}, new String[]{"camo"}, new String[]{"combat", "jungle"}, new String[]{"tactical", "bare"});
            }
            case "mercenaries" -> {
                g.uniform = Tex.rgb(50, 54, 60);
                g.gearColor = Tex.rgb(40, 42, 46);
                if (g.face.isEmpty() && r.chance(0.7)) g.face = "balaclava";
                if (g.head.isEmpty()) g.head = r.chance(0.5) ? "cap" : "";
                return pick(r, new String[]{"field", "tee"}, new String[]{"long", "rolled"}, new String[]{"tactical"}, new String[]{"combat"}, new String[]{"tactical"});
            }
            case "raiders" -> {
                g.gearColor = Tex.rgb(70, 60, 46);
                if (g.face.isEmpty() && r.chance(0.55)) g.face = r.chance(0.6) ? "bandana" : "balaclava";
                if (g.head.isEmpty() && r.chance(0.35)) g.head = r.chance(0.5) ? "beanie" : "hood";
                return pick(r, new String[]{"leather", "tracksuit", "hoodie", "flannel", "tank"}, new String[]{"long", "jacket", "bare", "ink", "striped"}, new String[]{"track", "jeans", "patched", "leather"}, new String[]{"sneaker_w", "hightop", "trainer", "combat"}, new String[]{"fingerless", "bare", "wraps"});
            }
            case "survivors" -> {
                g.gearColor = Tex.rgb(96, 84, 60);
                if (g.pack.isEmpty()) g.pack = r.chance(0.6) ? "small" : "large";
                if (g.head.isEmpty() && r.chance(0.4)) g.head = r.chance(0.5) ? "beanie" : "hood";
                return pick(r, new String[]{"parka", "sweater", "workjacket", "hoodie", "puffer", "flannel"}, new String[]{"long", "jacket", "patched"}, new String[]{"cargo", "jeans", "work", "sweats"}, new String[]{"hiking", "rubber", "work", "valenki"}, new String[]{"work", "mittens", "bare", "leather"});
            }
            default -> {
                Appearance a = Appearance.random(r);
                List<CharDefs.Part> torso = CharDefs.parts(Category.TORSO);
                a.set(Category.TORSO, r.nextInt(torso.size()));
                return a;
            }
        }
    }
}
