package absentialis.ui;

import absentialis.engine.render.Tex;
import absentialis.engine.voxel.VoxModel;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.gameplay.weapons.Gunsmith;
import java.util.HashMap;
import java.util.Map;

/**
 * Procedural voxel models for every item. Weapons are assembled from their platform body plus the
 * fitted gunsmith parts (muzzle, barrel, optic, stock, grip, light, magazine, finish) and
 * weathered by condition, so modifications visibly change the silhouette in the case, the
 * inspect view, the first-person viewmodel and on the character. Long axis = X.
 */
public final class ItemModels {
    private ItemModels() {}

    private static final Map<String, VoxModel> CACHE = new HashMap<>();

    static final int METAL = Tex.rgb(52, 54, 58), METAL_LT = Tex.rgb(96, 100, 106), POLY = Tex.rgb(34, 35, 37), WOOD = Tex.rgb(122, 74, 38),
            WOOD_DK = Tex.rgb(88, 52, 26), STEEL = Tex.rgb(140, 144, 150), BRASS = Tex.rgb(196, 160, 70), OLIVE = Tex.rgb(86, 96, 60),
            RED = Tex.rgb(170, 40, 36), WHITE = Tex.rgb(220, 218, 210), CLOTH = Tex.rgb(196, 190, 170), GLASS = 0xC060A0C0;

    public static VoxModel of(ItemInstance it) {
        String key = it.visualKey();
        VoxModel m = CACHE.get(key);
        if (m == null) {
            m = build(it);
            if (CACHE.size() > 2400) CACHE.clear();
            CACHE.put(key, m);
        }
        return m;
    }

    public static VoxModel of(ItemDef d) {
        return CACHE.computeIfAbsent("def:" + d.id, k -> build(new ItemInstance(-9, d.id)));
    }

    private static VoxModel build(ItemInstance it) {
        ItemDef d = it.def();
        VoxModel m;
        if (d.weapon != null) m = weapon(it);
        else m = misc(d);
        return m.jitter(0.06f, d.id.hashCode());
    }

    // ------------------------------------------------------------------ weapons
    private static boolean has(ItemInstance it, String cat, String key) { ItemInstance a = it.attachments.get(cat); return a != null && a.defId.endsWith("." + key); }

    /**
     * The base model of each weapon is fixed; attachments add or swap geometry on it (suppressors and
     * barrels at the muzzle, optics on the top rail, stocks at the rear, grips and launchers under the
     * barrel, extended and drum magazines in the well), so a build is readable at a glance.
     */
    private static VoxModel weapon(ItemInstance it) {
        ItemDef d = it.def();
        String model = d.weapon.model;
        int tint = Gunsmith.finishTint(it);
        int body = tint != 0 ? tint : POLY, metal = tint != 0 ? Tex.shade(tint, 0.75f) : METAL;
        int wood = tint != 0 ? Tex.shade(tint, 0.88f) : WOOD, steel = tint != 0 ? Tex.shade(tint, 1.1f) : STEEL;
        boolean longB = has(it, "barrel", "long"), shortB = has(it, "barrel", "short"), heavyB = has(it, "barrel", "heavy");
        int bx = longB ? 5 : shortB ? -4 : 0;
        boolean noStock = has(it, "stock", "none"), heavyStock = has(it, "stock", "heavy"), skeleton = has(it, "stock", "skeleton");
        boolean drum = has(it, "magazine", "drum"), ext = has(it, "magazine", "ext");
        VoxModel m;
        int muzzleX, boreY, topY, opticX, gripX, magX, railZ = 2, stockY = 8;
        boolean pistolMag = false, drumBase = false;
        switch (model) {
            case "pistol", "punisher", "whitetail", "yellow9", "matilda", "deagle", "killer9" -> {
                int len = switch (model) { case "whitetail" -> 10; case "yellow9" -> 17; case "matilda" -> 23; case "deagle", "killer9" -> 19; default -> 13; };
                int thick = model.equals("deagle") || model.equals("killer9") ? 4 : 3;
                m = new VoxModel(len + 12, 16, 5);
                int sy = 8;
                m.box(2, sy, 1, len + bx / 2, sy + thick, 3, model.equals("punisher") ? Tex.rgb(70, 74, 82) : model.equals("killer9") ? steel : metal);   // slide
                m.box(2, sy + thick + 1, 2, 3, sy + thick + 1, 2, METAL_LT);                                    // rear sight
                m.box(len - 1 + bx / 2, sy + thick + 1, 2, len - 1 + bx / 2, sy + thick + 1, 2, METAL_LT);
                m.box(2, 2, 1, 6, sy, 3, model.equals("yellow9") ? wood : model.equals("whitetail") ? Tex.rgb(196, 190, 176) : body);   // grip
                m.box(6, sy - 2, 2, 8, sy - 1, 2, metal);                                                        // trigger guard
                if (model.equals("yellow9")) m.box(7, sy - 3, 1, 10, sy - 1, 3, metal);                          // fixed box magazine ahead of the guard
                if (model.equals("matilda")) { m.box(9, sy - 3, 2, 10, sy - 1, 2, body); }                       // fold-down foregrip
                muzzleX = len + 1 + bx / 2; boreY = sy + thick - 1; topY = sy + thick + 1; opticX = 5; gripX = 9; magX = 3; pistolMag = true; stockY = sy + 1;
            }
            case "revolver" -> {
                m = new VoxModel(30, 14, 6);
                m.box(8, 8, 1, 20 + bx / 2, 10, 3, steel);            // barrel
                m.box(8, 6, 2, 19 + bx / 2, 7, 2, steel);             // underlug
                m.cylX(6, 10, 8, 2.5f, 3f, steel);                     // cylinder
                m.box(3, 7, 1, 7, 11, 3, steel);                       // frame
                m.box(1, 1, 1, 5, 7, 3, wood);                         // grip
                muzzleX = 21 + bx / 2; boreY = 9; topY = 11; opticX = 10; gripX = 12; magX = -1;
            }
            case "mosin" -> {
                // a long, slim rifle: 1.2 m of it at 40 voxels to the metre, and no taller than the one grid row it occupies
                m = new VoxModel(60, 16, 5);
                if (!noStock) { m.box(0, 4, 1, 14, 7, 3, wood); m.box(0, 4, 1, 1, 7, 3, WOOD_DK); }
                m.box(15, 5, 1, 38, 7, 3, wood);                       // fore stock
                m.box(14, 7, 2, 46 + bx, 8, 2, metal);                 // barrel
                m.box(16, 7, 1, 24, 8, 3, metal);                      // receiver
                m.box(22, 8, 3, 22, 9, 4, steel);                      // bolt handle
                muzzleX = 47 + bx; boreY = 7; topY = 8; opticX = 18; gripX = 30; magX = -1; stockY = 6;
            }
            case "shotgun", "riot", "striker" -> {
                boolean striker = model.equals("striker"), riot = model.equals("riot");
                m = new VoxModel(56, 16, 7);
                railZ = 3;
                if (!noStock) { if (striker) { m.box(2, 9, 3, 11, 9, 3, METAL_LT); m.box(2, 5, 3, 2, 9, 3, METAL_LT); } else { m.box(0, 3, 2, 12, 8, 4, riot ? body : wood); m.box(0, 2, 2, 3, 3, 4, riot ? body : WOOD_DK); } }
                m.box(12, 5, 2, 22, 10, 4, metal);                     // receiver
                int end = (striker ? 34 : 44) + bx;
                m.box(22, 8, 3, end, 9, 3, metal);                     // barrel
                if (!striker) m.box(22, 6, 3, end - 4, 7, 3, METAL_LT); // tube
                if (!striker && !riot) m.box(27, 5, 2, 34, 7, 4, wood); // pump
                if (riot) m.box(24, 5, 2, 36, 7, 4, body);             // fixed fore-end
                if (striker) { m.cylX(20, 26, 4.5f, 3.5f, 3.4f, metal); m.box(28, 4, 3, 29, 8, 3, body); drumBase = true; }
                m.box(14, 1, 2, 16, 5, 4, striker || riot ? body : wood); // grip
                muzzleX = end + 1; boreY = 8; topY = 10; opticX = 15; gripX = 30; magX = -1;
            }
            case "smg" -> {
                m = new VoxModel(44, 16, 5);
                if (!noStock) { m.box(0, 9, 2, 9, 9, 2, METAL_LT); m.box(0, 5, 2, 0, 9, 2, METAL_LT); }
                m.box(10, 7, 1, 24, 11, 3, body);                      // receiver
                m.box(24, 8, 2, 30 + bx, 10, 2, metal);                // barrel shroud
                m.box(13, 2, 1, 15, 7, 3, body);                       // grip
                m.box(21, 3, 2, 22, 7, 2, body);                       // fore grip stub
                muzzleX = 31 + bx; boreY = 9; topY = 12; opticX = 13; gripX = 25; magX = 13; pistolMag = true; stockY = 9;
            }
            case "tommy" -> {
                m = new VoxModel(56, 16, 7);
                railZ = 3;
                if (!noStock) { m.box(0, 3, 2, 11, 8, 4, wood); m.box(0, 2, 2, 3, 3, 4, WOOD_DK); }
                m.box(12, 7, 2, 28, 11, 4, metal);                     // receiver
                m.box(28, 8, 3, 44 + bx, 10, 3, metal);                // barrel
                for (int k = 30; k < 40 + bx; k += 2) m.box(k, 7, 2, k, 11, 4, METAL_LT);   // cooling fins
                m.box(14, 2, 2, 16, 7, 4, wood);                       // rear grip
                m.box(30, 3, 3, 32, 7, 3, wood);                       // fore grip
                m.cylX(19, 24, 3.5f, 3.5f, 3.6f, metal);               // the drum it is known for
                muzzleX = 45 + bx; boreY = 9; topY = 12; opticX = 15; gripX = 34; magX = -1; drumBase = true;
            }
            case "crossbow" -> {
                m = new VoxModel(44, 14, 17);
                railZ = 8;
                if (!noStock) { m.box(0, 4, 7, 12, 8, 9, body); m.box(0, 3, 7, 3, 4, 9, body); }
                m.box(12, 6, 7, 38, 8, 9, body);                       // tiller
                m.box(12, 8, 8, 36, 8, 8, STEEL);                      // bolt on the rail
                m.box(34, 7, 0, 36, 8, 16, METAL);                     // limbs
                m.box(30, 7, 0, 33, 7, 1, METAL); m.box(30, 7, 15, 33, 7, 16, METAL);
                for (int k = 0; k < 8; k++) { m.set(33 - k * 2, 8, 1 + k, WHITE); m.set(33 - k * 2, 8, 15 - k, WHITE); }   // string drawn back
                m.box(15, 2, 7, 17, 6, 9, body);                       // grip
                muzzleX = 39; boreY = 8; topY = 9; opticX = 16; gripX = 26; magX = -1;
            }
            case "knife" -> {
                m = new VoxModel(16, 6, 3);
                m.box(0, 2, 1, 5, 3, 1, POLY); m.box(5, 1, 1, 5, 4, 1, METAL);
                m.box(6, 2, 1, 15, 3, 1, STEEL); m.box(6, 3, 1, 13, 3, 1, METAL_LT);
                return m.weather(1f - Math.max(0, it.durability), (int) it.uid);
            }
            case "crowbar" -> {
                m = new VoxModel(28, 8, 3);
                m.box(2, 3, 1, 25, 3, 1, Tex.rgb(150, 40, 34));
                m.box(0, 4, 1, 1, 5, 1, Tex.rgb(150, 40, 34)); m.box(25, 1, 1, 27, 2, 1, Tex.rgb(150, 40, 34));
                return m;
            }
            default -> { // akr (Full-Auto Rifle) / sr16 (Semi-Auto Rifle)
                boolean ar = model.equals("sr16");
                m = new VoxModel(56, 18, 5);
                int furn = ar ? body : wood;
                if (!it.attachments.containsKey("stock")) {
                    if (ar) { m.box(0, 6, 1, 3, 11, 3, body); m.box(4, 8, 2, 9, 10, 2, metal); }
                    else { m.box(0, 4, 1, 4, 10, 3, furn); m.box(5, 7, 1, 9, 10, 3, furn); }
                }
                m.box(10, 7, 1, 22, 11, 3, metal);                      // receiver
                if (ar) m.box(10, 12, 2, 30, 12, 2, METAL_LT);           // flat-top rail
                else m.box(11, 12, 2, 19, 12, 2, metal);                 // dust cover ridge
                m.box(23, 7, 1, 31, 11, 3, ar ? body : furn);           // handguard
                m.box(31, 9, 2, (ar ? 44 : 40) + bx, 10, 2, metal);     // barrel
                m.box(36, 10, 2, 36, 12, 2, metal);                     // front sight
                m.box(12, 2, 1, 14, 7, 3, body);                        // pistol grip
                muzzleX = (ar ? 45 : 41) + bx; boreY = 9; topY = 12; opticX = 14; gripX = 26; magX = 16;
            }
        }
        if (heavyB) m.box(muzzleX - 10, boreY - 1, railZ - 1, muzzleX - 1, boreY + 1, Math.min(m.sz - 1, railZ + 1), metal);
        // magazine well: standard, extended or drum
        if (magX >= 0) {
            int mc = model.equals("akr") ? Tex.rgb(150, 82, 40) : pistolMag ? METAL_LT : POLY;
            if (drum) m.cylX(magX - 1, magX + 4, 3.5f, railZ + 0.5f, 3.3f, METAL);
            else if (pistolMag) m.box(magX, ext ? 0 : 1, 1, magX + 2, 2, 3, mc);
            else for (int k = 0; k < (ext ? 7 : 5); k++) m.box(magX + (model.equals("akr") ? k / 2 : 0), 6 - k, 1, magX + 2 + (model.equals("akr") ? k / 2 : 0), 6 - k, 3, mc);
        } else if (ext && !drumBase) m.box(Math.max(0, muzzleX - 8), boreY - 2, railZ, muzzleX - 2, boreY - 2, railZ, METAL_LT);   // tube extension
        // parts
        for (Map.Entry<String, ItemInstance> e : it.attachments.entrySet()) {
            String s = e.getKey(), id = e.getValue().defId;
            int zr = Math.min(m.sz - 1, railZ + 1), zl = Math.max(0, railZ - 1);
            switch (s) {
                case "muzzle" -> {
                    if (id.contains("supp")) m.cylX(muzzleX, Math.min(m.sx - 1, muzzleX + (id.contains("mono") ? 10 : 6)), boreY + 0.5f, railZ + 0.5f, id.contains("mono") ? 1.9f : 1.5f, POLY);
                    else if (id.endsWith("choke")) m.box(muzzleX, boreY - 1, zl, Math.min(m.sx - 1, muzzleX + 3), boreY + 1, zr, METAL);
                    else { m.box(muzzleX, boreY - 1, zl, Math.min(m.sx - 1, muzzleX + 2), boreY + 1, zr, METAL_LT); if (id.endsWith("brake")) m.box(muzzleX + 1, boreY, zl, Math.min(m.sx - 1, muzzleX + 1), boreY, zr, 0); }
                }
                case "optic" -> {
                    if (id.contains("scope")) { int l = id.endsWith("6") ? 15 : 10; m.cylX(opticX, Math.min(m.sx - 1, opticX + l), topY + 2.5f, railZ + 0.5f, id.endsWith("6") ? 1.9f : 1.5f, POLY); m.box(opticX + 4, topY, railZ, opticX + 5, topY + 1, railZ, METAL); m.set(opticX, topY + 2, railZ, GLASS); }
                    else if (id.endsWith("holo")) {
                        // 0.6: a holographic sight is a window, and the window is open - a hood on two posts with nothing
                        // in the middle of it, so that looking through it shows the target and not the sight
                        m.box(opticX, topY, zl, opticX + 4, topY, zr, POLY);
                        for (int z : new int[]{zl, zr}) { m.box(opticX + 1, topY + 1, z, opticX + 1, topY + 4, z, POLY); m.box(opticX + 3, topY + 1, z, opticX + 3, topY + 4, z, POLY); }
                        m.box(opticX + 1, topY + 5, zl, opticX + 3, topY + 5, zr, POLY);
                    }
                    else {
                        // the red dot: a low base and a ring to look through (0.5 put a solid block with a red voxel in front of the eye)
                        m.box(opticX, topY, zl, opticX + 3, topY, zr, POLY);
                        for (int z : new int[]{zl, zr}) m.box(opticX + 2, topY + 1, z, opticX + 2, topY + 3, z, POLY);
                        m.box(opticX + 2, topY + 4, zl, opticX + 2, topY + 4, zr, POLY);
                        m.set(opticX, topY + 1, railZ, Tex.rgb(220, 40, 40));        // the emitter, behind the window and below the line of sight
                    }
                }
                case "underbarrel" -> {
                    if (id.endsWith("launcher")) { m.cylX(gripX - 2, gripX + 7, boreY - 3.5f, railZ + 0.5f, 1.8f, METAL); m.box(gripX - 3, boreY - 6, railZ, gripX - 2, boreY - 3, railZ, POLY); }
                    else if (id.endsWith("bipod")) { m.box(gripX + 4, Math.max(0, boreY - 7), zl, gripX + 4, boreY - 2, zl, METAL_LT); m.box(gripX + 4, Math.max(0, boreY - 7), zr, gripX + 4, boreY - 2, zr, METAL_LT); }
                    else if (id.endsWith("angled")) { for (int k = 0; k < 4; k++) m.box(gripX + k, boreY - 3 - k / 2, railZ, gripX + k + 1, boreY - 2, railZ, POLY); }
                    else m.box(gripX, Math.max(0, boreY - 7), railZ, gripX + 1, boreY - 2, railZ, POLY);
                }
                case "light" -> {
                    // the weapon light rides the off side of the fore-end: a short tube with a lens (0.6: its own socket, on any gun)
                    // 0.7: it is an actual flashlight on the weapon - a knurled body in a clamp under the barrel, a wider head and
                    // a lens at the front - big enough to see in the hand and in the icon. Where it sits is what the beam leaves from.
                    int[] lp = lightMount(m, muzzleX, boreY, gripX, railZ, pistolMag && magX <= 4);
                    int lx = lp[0], ly = lp[1], lz = lp[2], lbody = Tex.rgb(40, 42, 46), lring = Tex.rgb(92, 96, 102), lens = Tex.rgb(250, 240, 170);
                    m.box(lx, ly, lz, lx + 5, ly + 1, lz + 1, lbody);                                        // body
                    for (int x = lx + 1; x <= lx + 4; x += 2) m.box(x, ly, lz, x, ly + 1, lz + 1, Tex.shade(lbody, 1.45f));   // knurling
                    m.box(lx + 6, ly - (ly > 0 ? 1 : 0), Math.max(0, lz - 1), lx + 7, ly + 2, Math.min(m.sz - 1, lz + 2), lring);   // head
                    m.box(lx + 8, ly, lz, lx + 8, ly + 1, lz + 1, lens);                                      // lens
                    m.box(lx + 2, ly + 2, lz, lx + 3, ly + 2, lz + 1, METAL_LT);                              // the clamp up to the barrel
                    m.set(lx, ly + 1, lz, Tex.rgb(200, 60, 40));                                              // tail switch
                    if (LENS.size() > 2000) LENS.clear();
                    LENS.put(it.visualKey(), new float[]{lx + 9f, ly + 1f, lz + 1f});
                }
                case "laser" -> {
                    int ly = boreY - 1, lx = Math.min(m.sx - 4, gripX + 2);
                    m.box(lx, ly, zr, lx + 3, ly + 1, zr, id.endsWith("light") ? METAL_LT : POLY);
                    // the emitter is the colour of its beam (0.8: red, green or blue)
                    m.set(lx + 3, ly, zr, id.endsWith("light") ? Tex.rgb(240, 230, 160) : id.endsWith("5mw") ? Tex.rgb(60, 230, 80) : id.endsWith("tac") ? Tex.rgb(60, 120, 255) : Tex.rgb(240, 40, 30));
                    m.box(lx, ly + 1, zr, lx + 2, ly + 1, zr, METAL_LT);
                }
                case "stock" -> {
                    if (heavyStock) { m.box(0, stockY - 4, zl, 4, stockY + 3, zr, POLY); m.box(5, stockY - 1, zl, 9, stockY + 2, zr, POLY); }
                    else if (skeleton) { m.box(0, stockY + 1, railZ, 9, stockY + 1, railZ, METAL_LT); m.box(0, stockY - 3, railZ, 0, stockY + 1, railZ, METAL_LT); m.box(0, stockY - 3, railZ, 6, stockY - 3, railZ, METAL_LT); }
                }
                case "reargrip" -> { if (pistolMag || magX >= 0) m.box(Math.max(0, magX - 4), 1, zl, Math.max(0, magX - 4), 3, zr, id.endsWith("rubber") ? Tex.rgb(22, 22, 24) : Tex.rgb(96, 88, 70)); }
                default -> {}
            }
        }
        return m.weather(1f - it.condition() / 100f, (int) it.uid);
    }

    /**
     * Where the weapon light is clamped: {x, y, z} of the rear-bottom corner of its body. Under the barrel, behind the
     * muzzle, clear of the fore-end; on a handgun, on the dust-cover rail ahead of the trigger guard.
     */
    static int[] lightMount(VoxModel m, int muzzleX, int boreY, int gripX, int railZ, boolean handgun) {
        int lx = Math.max(0, Math.min(m.sx - 9, handgun ? Math.max(8, muzzleX - 8) : Math.max(gripX + 2, muzzleX - 12)));
        int ly = Math.max(0, boreY - (handgun ? 3 : 4));
        int lz = Math.max(0, Math.min(m.sz - 2, railZ - 1));
        return new int[]{lx, ly, lz};
    }

    /** Model-space position of the lens of a fitted weapon light (voxels, from the model's own origin), or null when there is none. */
    public static float[] lightLens(ItemInstance it) {
        if (it == null || !it.attachments.containsKey("light") || it.def().weapon == null) return null;
        of(it);
        return LENS.get(it.visualKey());
    }
    private static final Map<String, float[]> LENS = new HashMap<>();

    // ------------------------------------------------------------------ everything else
    static final int HERB_G = Tex.rgb(60, 140, 60), HERB_R = Tex.rgb(176, 40, 50), HERB_Y = Tex.rgb(226, 190, 48);

    /** The colours a herb item shows, one per plant in the pot: green, red, yellow, or the two or three that were mixed. */
    public static int[] herbColors(String id) {
        return switch (id) {
            case "herb.red" -> new int[]{HERB_R};
            case "herb.yellow" -> new int[]{HERB_Y};
            case "herb.mixed_gr" -> new int[]{HERB_G, HERB_R};
            case "herb.mixed_gry" -> new int[]{HERB_G, HERB_R, HERB_Y};
            default -> new int[]{HERB_G};
        };
    }

    /** A potted herb. A single herb is one plant; a mixture is one plant of each colour in the same pot, each clearly its own. */
    private static VoxModel herbPot(String id) {
        int[] c = herbColors(id);
        VoxModel m = new VoxModel(10, 14, 10);
        int pot = Tex.rgb(150, 92, 60), stem = Tex.rgb(60, 90, 40);
        m.box(2, 0, 2, 7, 3, 7, pot); m.box(1, 3, 1, 8, 3, 8, Tex.shade(pot, 1.12f)); m.box(2, 3, 2, 7, 3, 7, Tex.rgb(58, 44, 34));
        if (c.length == 1) {
            m.box(4, 4, 4, 5, 8, 5, stem);
            m.sphere(4.5f, 10, 4.5f, 3.4f, c[0]); m.sphere(2.5f, 12, 3.5f, 1.6f, Tex.shade(c[0], 1.12f)); m.sphere(6, 8, 6, 1.8f, Tex.shade(c[0], 0.88f));
        } else {
            float[][] at = c.length == 2 ? new float[][]{{3, 4.5f}, {6.5f, 4.5f}} : new float[][]{{2.6f, 3.2f}, {6.8f, 3.2f}, {4.7f, 6.8f}};
            for (int k = 0; k < c.length; k++) {
                int sx = Math.round(at[k][0]), sz = Math.round(at[k][1]), hgt = 9 + (k == 1 ? 1 : 0) - (k == 2 ? 1 : 0);
                m.box(sx, 4, sz, sx, hgt - 2, sz, stem);
                m.sphere(at[k][0] + 0.5f, hgt, at[k][1] + 0.5f, 2.3f, c[k]);
                m.sphere(at[k][0] + 0.5f, hgt + 2, at[k][1] + 0.5f, 1.3f, Tex.shade(c[k], 1.14f));
            }
        }
        return m;
    }

    /**
     * Ammunition, one box per calibre, each recognisable at a glance (0.6):
     * SMG - a compact green carton with yellow branding; handgun - red and black with white branding; shells - a dark
     * green carton with red accents and the red shells standing in it; rifle - a long slim white-and-green box;
     * magnum - black and silver; bolts are not boxed at all: wooden shafts, steel heads, fletching.
     * Branding is bands and blocks of colour. Nothing is written on anything.
     */
    private static VoxModel ammo(ItemDef d) {
        String cal = d.ammo == null ? "" : d.ammo.caliber;
        VoxModel m;
        switch (cal) {
            case "smg" -> {
                int g = Tex.rgb(44, 112, 58), y = Tex.rgb(232, 196, 44);
                m = new VoxModel(9, 7, 7);
                m.box(0, 0, 0, 8, 5, 6, g); m.box(0, 6, 0, 8, 6, 6, Tex.shade(g, 1.15f));
                m.box(0, 2, 0, 8, 3, 0, y); m.box(0, 2, 6, 8, 3, 6, y); m.box(0, 2, 0, 0, 3, 6, y); m.box(8, 2, 0, 8, 3, 6, y);
                m.box(3, 6, 2, 5, 6, 4, y); m.box(1, 4, 0, 2, 4, 0, y); m.box(6, 1, 0, 7, 1, 0, Tex.shade(g, 0.7f));
            }
            case "handgun" -> {
                int r = Tex.rgb(176, 36, 34), k = Tex.rgb(24, 24, 26);
                m = new VoxModel(12, 6, 8);
                m.box(0, 0, 0, 11, 4, 7, r); m.box(0, 0, 0, 11, 1, 7, k); m.box(0, 5, 0, 11, 5, 7, k);
                m.box(2, 2, 0, 9, 3, 0, WHITE); m.box(2, 2, 7, 9, 3, 7, WHITE); m.box(4, 5, 3, 7, 5, 4, WHITE);
                m.box(0, 2, 2, 0, 3, 5, WHITE); m.box(11, 2, 2, 11, 3, 5, WHITE);
            }
            case "shell" -> {
                int g = Tex.rgb(30, 62, 42), red = Tex.rgb(190, 38, 34);
                m = new VoxModel(12, 9, 8);
                m.box(0, 0, 0, 11, 5, 7, g); m.box(0, 4, 0, 11, 5, 0, red); m.box(0, 4, 7, 11, 5, 7, red); m.box(0, 0, 0, 0, 5, 0, red); m.box(11, 0, 0, 11, 5, 0, red);
                m.box(1, 5, 1, 10, 5, 6, Tex.shade(g, 0.6f));
                // the shells themselves, standing in two rows: red hulls on brass heads
                for (int i = 0; i < 5; i++) for (int j = 0; j < 2; j++) { int x = 1 + i * 2, z = 1 + j * 4; m.box(x, 5, z, x + 1, 5, z + 1, BRASS); m.box(x, 6, z, x + 1, 8, z + 1, red); }
            }
            case "rifle" -> {
                int w = Tex.rgb(226, 224, 214), g = Tex.rgb(52, 120, 70);
                m = new VoxModel(18, 5, 5);
                m.box(0, 0, 0, 17, 3, 4, w); m.box(0, 4, 0, 17, 4, 4, Tex.shade(w, 1.05f));
                m.box(0, 0, 0, 4, 4, 4, g); m.box(13, 0, 0, 17, 4, 4, g); m.box(5, 1, 0, 12, 1, 0, g); m.box(5, 1, 4, 12, 1, 4, g); m.box(7, 4, 2, 10, 4, 2, g);
            }
            case "assault" -> {
                // 0.7, Assault Rifle Ammo: a squat tan carton with a black shoulder and an orange band - nothing like the rifle box beside it
                int t = Tex.rgb(176, 148, 96), k = Tex.rgb(26, 26, 28), o = Tex.rgb(226, 112, 36);
                m = new VoxModel(14, 7, 6);
                m.box(0, 0, 0, 13, 5, 5, t); m.box(0, 6, 0, 13, 6, 5, k); m.box(0, 4, 0, 13, 5, 0, k); m.box(0, 4, 5, 13, 5, 5, k);
                m.box(0, 2, 0, 13, 2, 0, o); m.box(0, 2, 5, 13, 2, 5, o); m.box(0, 2, 0, 0, 2, 5, o); m.box(13, 2, 0, 13, 2, 5, o);
                m.box(5, 6, 2, 8, 6, 3, o); m.box(2, 0, 0, 3, 1, 0, k); m.box(10, 0, 0, 11, 1, 0, k);
            }
            case "magnum" -> {
                int k = Tex.rgb(22, 22, 24), s = Tex.rgb(190, 194, 200);
                m = new VoxModel(10, 7, 7);
                m.box(0, 0, 0, 9, 5, 6, k); m.box(0, 6, 0, 9, 6, 6, s);
                m.box(0, 0, 0, 9, 0, 6, s); m.box(0, 3, 0, 9, 3, 0, s); m.box(0, 3, 6, 9, 3, 6, s); m.box(0, 3, 0, 0, 3, 6, s); m.box(9, 3, 0, 9, 3, 6, s);
                m.box(4, 6, 2, 5, 6, 4, k); m.box(0, 0, 0, 0, 5, 0, s); m.box(9, 0, 0, 9, 5, 0, s); m.box(0, 0, 6, 0, 5, 6, s); m.box(9, 0, 6, 9, 5, 6, s);
            }
            case "bolt" -> {
                int wood = Tex.rgb(150, 104, 58), steel = Tex.rgb(168, 172, 178), fl = Tex.rgb(190, 44, 40), fl2 = Tex.rgb(226, 220, 204);
                m = new VoxModel(20, 5, 9);
                for (int b = 0; b < 3; b++) {
                    int z = 1 + b * 3;
                    m.box(3, 2, z, 16, 2, z, b == 1 ? Tex.shade(wood, 1.1f) : wood);               // the shaft
                    m.box(17, 2, z, 18, 2, z, steel); m.set(19, 2, z, Tex.shade(steel, 1.2f));      // the head
                    m.set(17, 3, z, steel); m.set(17, 1, z, steel);
                    for (int x = 0; x <= 2; x++) { m.set(x, 3 + (x == 0 ? 1 : 0), z, x == 1 ? fl2 : fl); m.set(x, 1 - (x == 0 ? 1 : 0), z, x == 1 ? fl2 : fl); m.set(x, 2, z, Tex.shade(wood, 0.8f)); }
                }
                m.box(8, 1, 0, 9, 3, 8, Tex.rgb(70, 52, 38));                                      // a leather tie round the bundle
            }
            default -> {
                m = new VoxModel(14, 6, 8);
                m.box(0, 0, 0, 13, 3, 7, Tex.rgb(110, 98, 70));
                for (int k = 0; k < 5; k++) m.box(2 + k * 2, 4, 2, 2 + k * 2, 5, 2, BRASS);
            }
        }
        return m;
    }

    // ------------------------------------------------------------------ 0.7 models
    /**
     * A pile of gunpowder on a square of waxed paper. Light is a small pile of fine grey powder; standard is a large
     * pile; heavy is the same large pile with a yellow-red cast to the grains.
     */
    private static VoxModel powder(String icon) {
        boolean heavy = icon.endsWith("heavy"), large = heavy || icon.endsWith("med");
        int paper = Tex.rgb(190, 176, 140), a = heavy ? Tex.rgb(150, 92, 40) : Tex.rgb(58, 58, 60), b = heavy ? Tex.rgb(196, 150, 48) : Tex.rgb(82, 82, 84), c = heavy ? Tex.rgb(170, 60, 36) : Tex.rgb(40, 40, 42);
        int n = large ? 14 : 10;
        VoxModel m = new VoxModel(n, large ? 7 : 4, n);
        m.box(0, 0, 0, n - 1, 0, n - 1, paper);
        m.box(0, 0, 0, 1, 0, 1, Tex.shade(paper, 0.8f)); m.box(n - 2, 0, n - 2, n - 1, 0, n - 1, Tex.shade(paper, 0.8f));
        float cx = n / 2f, r = large ? 5.6f : 3.4f, top = large ? 6f : 3f;
        for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) {
            float d = (float) Math.sqrt((x + 0.5f - cx) * (x + 0.5f - cx) + (z + 0.5f - cx) * (z + 0.5f - cx));
            if (d > r) continue;
            // a cone with a rounded top and a ragged foot
            int hgt = Math.max(1, Math.round(top * (1 - d / r) * (1 - d / r * 0.25f) + ((x * 7 + z * 13) % 3 == 0 ? 0.6f : 0)));
            for (int y = 1; y <= Math.min(m.sy - 1, hgt); y++) { int k = (x * 5 + y * 3 + z * 11) % 7; m.set(x, y, z, k == 0 ? c : k < 3 ? b : a); }
        }
        return m;
    }

    /** A length of two-by-four: sawn pine, end grain darker, a knot and a couple of bent nails left in it. */
    private static VoxModel plank() {
        VoxModel m = new VoxModel(30, 2, 4);
        int wood = Tex.rgb(190, 150, 96), dark = Tex.rgb(150, 112, 68);
        m.box(0, 0, 0, 29, 1, 3, wood);
        for (int x = 0; x < 30; x++) if ((x * 7) % 5 == 0) m.box(x, 1, 0, x, 1, 3, Tex.shade(wood, 0.92f));
        m.box(0, 0, 0, 0, 1, 3, dark); m.box(29, 0, 0, 29, 1, 3, dark);
        m.box(11, 1, 1, 12, 1, 2, Tex.rgb(112, 78, 46));
        m.set(4, 1, 1, STEEL); m.set(25, 1, 2, STEEL);
        return m;
    }

    /** An empty grenade body: grey steel, segmented, open at the neck where the fuze would go. */
    private static VoxModel casing() {
        int g = Tex.rgb(128, 132, 138), dk = Tex.rgb(86, 90, 96);
        VoxModel m = new VoxModel(8, 9, 8);
        m.sphere(4, 3.6f, 4, 3.6f, g);
        for (int y = 1; y <= 6; y += 2) for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) if (m.get(x, y, z) != 0 && (x == 0 || z == 0 || x == 7 || z == 7 || m.get(x - 1, y, z) == 0 || m.get(x + 1, y, z) == 0 || m.get(x, y, z - 1) == 0 || m.get(x, y, z + 1) == 0)) m.set(x, y, z, dk);
        m.box(3, 7, 3, 4, 8, 4, dk); m.box(3, 8, 3, 4, 8, 4, 0); m.set(3, 8, 3, dk); m.set(4, 8, 4, dk);
        return m;
    }

    /** A hand grenade in its colour (green fragmentation, red incendiary, blue flash): body, fuze, spoon and pin ring. */
    private static VoxModel grenade(int body) {
        VoxModel m = new VoxModel(9, 12, 8);
        m.sphere(4, 4, 4, 3.7f, body);
        int dk = Tex.shade(body, 0.7f);
        for (int y = 2; y <= 6; y += 2) for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) if (m.get(x, y, z) != 0 && (m.get(x - 1, y, z) == 0 || m.get(x + 1, y, z) == 0 || m.get(x, y, z - 1) == 0 || m.get(x, y, z + 1) == 0)) m.set(x, y, z, dk);
        m.box(3, 8, 3, 4, 10, 4, METAL);                        // fuze
        m.box(5, 9, 3, 6, 9, 4, STEEL); m.box(6, 5, 3, 6, 9, 4, STEEL);   // spoon down the side
        m.set(2, 10, 3, BRASS); m.set(1, 10, 3, BRASS); m.set(1, 11, 3, BRASS); m.set(1, 9, 3, BRASS); m.set(0, 10, 3, BRASS);   // pin ring
        m.box(3, 11, 3, 4, 11, 4, Tex.shade(body, 1.25f));
        return m;
    }

    /**
     * The gemstones, each cut and coloured as it is described: faceted stones are a crown over a pavilion with a flat
     * table and lighter facets; oval stones are a smooth dome. Size says something too - the pink spinel is small.
     */
    private static VoxModel gem(String icon) {
        String k = icon.contains("_") ? icon.substring(icon.indexOf('_') + 1) : "red";
        int col; boolean oval = false, small = false, clear = false; int accent = 0, accent2 = 0;
        switch (k) {
            case "pink" -> { col = Tex.rgb(226, 70, 140); small = true; }
            case "blue" -> { col = 0xE0204CB8; oval = true; clear = true; }
            case "raspberry" -> col = Tex.rgb(204, 30, 86);
            case "sapphire" -> { col = Tex.rgb(34, 70, 198); oval = true; }
            case "green" -> { col = 0xD828B45A; clear = true; }
            case "yellow" -> col = Tex.rgb(240, 196, 40);
            case "purple" -> { col = Tex.rgb(74, 34, 110); accent = Tex.rgb(140, 40, 70); accent2 = Tex.rgb(52, 110, 76); }
            case "white" -> { col = 0xE0E6F0F4; clear = true; }
            default -> col = Tex.rgb(170, 16, 34);            // ruby: deep crimson
        }
        int n = small ? 6 : 9;
        VoxModel m = new VoxModel(n, small ? 5 : 7, oval ? n - 2 : n);
        float cx = n / 2f, cz = m.sz / 2f, rx = n / 2f - 0.2f, rz = m.sz / 2f - 0.2f;
        int girdle = small ? 2 : 3, top = m.sy - 1;
        for (int y = 0; y <= top; y++) {
            // radius by height: a point at the culet widening to the girdle, then a crown stepping in to the table (or a dome)
            float t = y <= girdle ? (y + 0.6f) / (girdle + 0.6f) : oval ? (float) Math.sqrt(Math.max(0, 1 - Math.pow((y - girdle) / (float) (top - girdle + 0.8f), 2))) : 1 - (y - girdle) * (small ? 0.28f : 0.2f);
            for (int x = 0; x < n; x++) for (int z = 0; z < m.sz; z++) {
                float dx = (x + 0.5f - cx) / (rx * t), dz = (z + 0.5f - cz) / (rz * t);
                boolean in = oval ? dx * dx + dz * dz <= 1 : Math.abs(dx) + Math.abs(dz) <= 1.25f && Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                if (!in) continue;
                boolean edge = oval ? dx * dx + dz * dz > 0.55f : Math.abs(dx) + Math.abs(dz) > 0.9f;
                int c = y == top && !oval ? Tex.shade(col, 1.45f) : y > girdle ? (edge ? Tex.shade(col, 1.18f) : col) : (edge ? Tex.shade(col, 0.74f) : Tex.shade(col, 0.9f));
                if (accent != 0) { int hsh = (x * 3 + y * 5 + z * 7) % 6; if (hsh == 0) c = accent; else if (hsh == 3 && y <= girdle) c = accent2; }
                if (clear) c = (c & 0x00FFFFFF) | 0xD8000000;
                m.set(x, y, z, c);
            }
        }
        // the glint: one bright voxel on the shoulder of the stone
        m.set(Math.max(0, (int) cx - 2), Math.min(top, girdle + 1), Math.max(0, (int) cz - (oval ? 1 : 2)), 0xFFFFFFFF);
        return m;
    }

    /** A plain gold wedding band, standing on its edge. */
    private static VoxModel ring() {
        int g = Tex.rgb(226, 176, 54), hi = Tex.rgb(255, 226, 120), lo = Tex.rgb(160, 116, 30);
        VoxModel m = new VoxModel(8, 8, 3);
        for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) {
            float d = (float) Math.sqrt((x - 3.5f) * (x - 3.5f) + (y - 3.5f) * (y - 3.5f));
            if (d > 3.9f || d < 2.3f) continue;
            for (int z = 0; z < 3; z++) m.set(x, y, z, z == 1 ? (y > 4 ? hi : g) : (y < 3 ? lo : g));
        }
        return m;
    }

    /** Everything worn: hats and helmets, what covers the face, and body armour. One model per item of the 0.7 list. */
    private static VoxModel wearable(ItemDef d) {
        String icon = d.icon == null ? "" : d.icon;
        VoxModel m = GearModels08.model(d);          // the 0.8 equipment has models of its own
        if (m != null) return m;
        if ("head".equals(d.slot)) {
            m = new VoxModel(12, 9, 13);
            switch (d.id) {
                case "helmet.baseball" -> {
                    // 0.8: a baseball cap, not a miner's hat. The white block on the front (which read as a lamp) is gone:
                    // a low soft crown in blue panels with white seams and a white button, and a long curved peak
                    int blue = Tex.rgb(36, 78, 170), white = Tex.rgb(232, 232, 226), under = Tex.rgb(26, 56, 122);
                    m = new VoxModel(12, 7, 16);
                    dome(m, 6, 0, 9.5f, 5.2f, 6.2f, blue); carveInside(m, 6, 0, 9.5f, 4.2f, 5.2f);
                    for (int y = 0; y < 7; y++) for (int z = 3; z < 16; z++) { if (m.get(5, y, z) != 0 && m.get(5, y + 1 < 7 ? y + 1 : y, z) == 0) m.set(5, y, z, white); }
                    m.box(5, 6, 9, 6, 6, 10, white);                                                            // the button
                    m.box(2, 0, 0, 9, 0, 3, blue); m.box(3, 0, 0, 8, 0, 0, under); m.set(2, 0, 0, 0); m.set(9, 0, 0, 0);   // the peak, rounded at its corners
                    m.box(3, 1, 3, 8, 1, 3, under);
                }
                case "helmet.cap" -> {
                    // 0.7: the Field Cap is a makeshift helmet - a pot of beaten steel on a strap, open across the whole face
                    int steel = Tex.rgb(112, 108, 98), rust = Tex.rgb(132, 86, 56), strap = Tex.rgb(78, 60, 40);
                    dome(m, 6, 1, 7f, 5.6f, 6.4f, steel); carveInside(m, 6, 1, 7f, 4.6f, 5.4f);
                    m.box(0, 0, 0, 11, 4, 3, 0);                     // the front is cut away: the face is exposed from brow to chin
                    for (int k = 0; k < 9; k++) { int x = 2 + (k * 5) % 8, z = 5 + (k * 3) % 6, y = 4 + k % 3; if (m.get(x, y, z) != 0) m.set(x, y, z, rust); }
                    m.box(1, 1, 6, 1, 3, 6, strap); m.box(10, 1, 6, 10, 3, 6, strap); m.box(2, 0, 5, 9, 0, 5, strap);
                    m.box(4, 7, 5, 5, 7, 6, Tex.rgb(60, 60, 58));   // a rivet plate where two pieces were joined
                }
                case "helmet.bike" -> {
                    int shell = Tex.rgb(226, 60, 40), foam = Tex.rgb(40, 40, 44);
                    dome(m, 6, 1, 6.5f, 5.4f, 6.3f, shell); carveInside(m, 6, 1, 6.5f, 4.4f, 5.3f);
                    for (int z = 2; z <= 10; z += 2) m.box(4, 5, z, 4, 8, z, 0);       // vents
                    for (int z = 3; z <= 11; z += 2) m.box(7, 5, z, 7, 8, z, 0);
                    m.box(1, 1, 1, 10, 1, 11, foam); m.box(5, 0, 2, 6, 0, 2, foam);
                    m.box(1, 0, 6, 1, 0, 7, Tex.rgb(20, 20, 22)); m.box(10, 0, 6, 10, 0, 7, Tex.rgb(20, 20, 22));
                }
                case "helmet.ssh" -> {
                    // the Military Helmet: a full shell down over the ears, the neck and the jaw, open only in a slot for the eyes
                    int k = Tex.rgb(72, 84, 58), dk = Tex.rgb(50, 58, 42);
                    m = new VoxModel(12, 11, 13);
                    dome(m, 6, 3, 6.5f, 5.8f, 6.4f, k); carveInside(m, 6, 3, 6.5f, 4.8f, 5.4f);
                    m.box(0, 0, 3, 0, 4, 11, k); m.box(11, 0, 3, 11, 4, 11, k); m.box(1, 0, 11, 10, 4, 12, k);   // cheek and neck guards
                    m.box(1, 0, 0, 10, 2, 0, k); m.box(0, 0, 0, 0, 2, 3, k); m.box(11, 0, 0, 11, 2, 3, k);         // jaw guard
                    m.box(1, 3, 0, 10, 4, 0, 0);                                                                  // the eye slot
                    m.box(1, 5, 0, 10, 5, 0, dk); m.box(5, 9, 5, 6, 9, 7, dk);
                }
                default -> {
                    if (icon.contains("cap")) { dome(m, 6, 0, 6.5f, 5f, 6f, OLIVE); carveInside(m, 6, 0, 6.5f, 4f, 5f); m.box(3, 0, 0, 8, 0, 1, Tex.shade(OLIVE, 0.8f)); }
                    else { dome(m, 6, 1, 6.5f, 5.8f, 6.4f, Tex.rgb(80, 92, 64)); carveInside(m, 6, 1, 6.5f, 4.8f, 5.4f); }
                }
            }
            return m;
        }
        if ("face".equals(d.slot)) {
            if (d.id.equals("face.sunglasses")) {
                // dark black sunglasses: two lenses, a bridge, arms folded back
                int k = Tex.rgb(14, 14, 16), lens = 0xF0101014;
                m = new VoxModel(12, 4, 8);
                m.box(0, 1, 0, 4, 3, 0, lens); m.box(7, 1, 0, 11, 3, 0, lens); m.box(5, 2, 0, 6, 3, 0, k);
                m.box(0, 3, 0, 11, 3, 0, k); m.box(0, 3, 1, 0, 3, 7, k); m.box(11, 3, 1, 11, 3, 7, k); m.set(0, 2, 7, k); m.set(11, 2, 7, k);
                m.set(1, 2, 0, Tex.rgb(70, 70, 80)); m.set(8, 2, 0, Tex.rgb(70, 70, 80));
                return m;
            }
            m = new VoxModel(10, 12, 9);
            if (icon.contains("gas")) { m.box(1, 2, 0, 8, 8, 4, Tex.rgb(60, 64, 60)); m.cylX(0, 2, 4, 2, 1.4f, GLASS); m.cylX(7, 9, 4, 2, 1.4f, GLASS); m.cylY(0, 2, 5, 1, 2f, POLY); }
            else {
                // a black ski mask: the whole head, with one opening across the eyes and the knit ribbed at the neck
                int k = Tex.rgb(24, 24, 28), rib = Tex.rgb(38, 38, 44);
                dome(m, 5, 2, 4.5f, 4.6f, 4.4f, k); m.box(1, 0, 1, 8, 6, 8, k); carveInside(m, 5, 2, 4.5f, 3.6f, 3.4f); m.box(2, 0, 2, 7, 5, 7, 0);
                m.box(2, 5, 0, 7, 6, 1, 0);
                for (int x = 1; x <= 8; x += 2) { m.box(x, 0, 1, x, 1, 1, rib); m.box(x, 0, 8, x, 1, 8, rib); }
            }
            return m;
        }
        m = new VoxModel(18, 16, 6);
        switch (d.id) {
            case "armor.vest_soft" -> {
                // Soft Body Armor: a simple light-green bulletproof vest - two panels and two straps, nothing on it
                int g = Tex.rgb(138, 176, 118), dk = Tex.rgb(104, 138, 90);
                m.box(2, 0, 0, 15, 12, 5, g); m.box(6, 10, 0, 11, 15, 5, 0); m.box(2, 13, 1, 4, 15, 4, g); m.box(13, 13, 1, 15, 15, 4, g);
                m.box(2, 0, 1, 15, 12, 4, 0); m.box(2, 0, 0, 15, 12, 0, g); m.box(2, 0, 5, 15, 12, 5, g); m.box(6, 10, 0, 11, 12, 5, 0);
                m.box(2, 4, 0, 15, 4, 0, dk); m.box(2, 8, 0, 15, 8, 0, dk); m.box(2, 5, 1, 2, 7, 4, dk); m.box(15, 5, 1, 15, 7, 4, dk);
            }
            case "armor.body" -> {
                // Body Armor: light blue, a real plate carrier with protection down both arms
                int b = Tex.rgb(120, 170, 214), dk = Tex.rgb(84, 126, 170), pad = Tex.rgb(150, 196, 232);
                m.box(4, 0, 0, 13, 12, 5, b); m.box(7, 11, 0, 10, 15, 5, 0); m.box(4, 13, 1, 6, 15, 4, b); m.box(11, 13, 1, 13, 15, 4, b);
                m.box(5, 3, 0, 12, 9, 0, dk); m.box(6, 4, 0, 11, 8, 0, pad);                                    // the plate
                m.box(0, 5, 1, 3, 13, 4, b); m.box(14, 5, 1, 17, 13, 4, b);                                     // arm guards, shoulder to elbow
                m.box(0, 9, 1, 3, 9, 4, dk); m.box(14, 9, 1, 17, 9, 4, dk); m.box(0, 12, 0, 3, 13, 5, pad); m.box(14, 12, 0, 17, 13, 5, pad);
                for (int x = 5; x <= 11; x += 3) m.box(x, 0, 0, x + 1, 1, 0, dk);
            }
            case "armor.heavy" -> {
                // Heavy Body Armor: thick black metal - a breastplate in riveted bands, pauldrons, a gorget
                int k = Tex.rgb(24, 24, 28), edge = Tex.rgb(58, 60, 66), rivet = Tex.rgb(110, 112, 118);
                m = new VoxModel(18, 17, 8);
                m.box(3, 0, 0, 14, 13, 7, k); m.box(4, 0, 1, 13, 12, 6, 0);
                m.box(7, 13, 2, 10, 15, 5, k); m.box(8, 13, 3, 9, 15, 4, 0);                                      // gorget
                for (int y = 2; y <= 11; y += 3) { m.box(3, y, 0, 14, y, 0, edge); for (int x = 4; x <= 13; x += 3) m.set(x, y + 1, 0, rivet); }
                m.box(0, 9, 0, 3, 14, 7, k); m.box(14, 9, 0, 17, 14, 7, k); m.box(0, 14, 1, 3, 14, 6, edge); m.box(14, 14, 1, 17, 14, 6, edge);   // pauldrons
                m.box(0, 5, 1, 2, 8, 6, k); m.box(15, 5, 1, 17, 8, 6, k);
                m.box(7, 4, 0, 10, 9, 0, edge);
            }
            default -> {
                int c = icon.contains("plate") ? Tex.rgb(70, 74, 66) : icon.contains("mil") ? OLIVE : Tex.rgb(46, 52, 70);
                m = new VoxModel(14, 16, 6);
                m.box(0, 0, 0, 13, 13, 5, c); m.box(3, 10, 0, 10, 15, 5, 0); m.box(0, 14, 1, 2, 15, 4, c); m.box(11, 14, 1, 13, 15, 4, c);
                if (icon.contains("mil") || icon.contains("plate")) for (int k = 0; k < 3; k++) m.box(2 + k * 4, 2, 0, 4 + k * 4, 5, 0, Tex.shade(c, 0.7f));
            }
        }
        return m;
    }

    /** The upper half of an ellipsoid standing on y0: the crown of a hat or a helmet. */
    static void dome(VoxModel m, float cx, float y0, float cz, float rx, float rz, int col) {
        float ry = m.sy - y0 - 0.2f;
        for (int x = 0; x < m.sx; x++) for (int y = (int) y0; y < m.sy; y++) for (int z = 0; z < m.sz; z++) {
            float dx = (x + 0.5f - cx) / rx, dy = (y + 0.5f - y0) / ry, dz = (z + 0.5f - cz) / rz;
            if (dx * dx + dy * dy + dz * dz <= 1) m.set(x, y, z, (x + z) % 5 == 0 ? Tex.shade(col, 0.93f) : col);
        }
    }

    /** Hollows a dome out so that it is a shell with a head-sized space inside. */
    static void carveInside(VoxModel m, float cx, float y0, float cz, float rx, float rz) {
        float ry = m.sy - y0 - 1.2f;
        for (int x = 0; x < m.sx; x++) for (int y = (int) y0; y < m.sy; y++) for (int z = 0; z < m.sz; z++) {
            float dx = (x + 0.5f - cx) / rx, dy = (y + 0.5f - y0) / ry, dz = (z + 0.5f - cz) / rz;
            if (dx * dx + dy * dy + dz * dz <= 1) m.set(x, y, z, 0);
        }
    }

    /** A hand torch: knurled alloy body, a wider head, the lens, a switch on the barrel and a lanyard ring. */
    private static VoxModel torch() {
        VoxModel m = new VoxModel(14, 5, 5);
        int body = Tex.rgb(40, 42, 46), ring = Tex.rgb(92, 96, 102);
        m.cylX(0, 9, 2.5f, 2.5f, 1.6f, body);
        for (int x = 2; x <= 8; x += 2) m.cylX(x, x, 2.5f, 2.5f, 1.6f, Tex.shade(body, 1.35f));
        m.cylX(10, 12, 2.5f, 2.5f, 2.4f, ring); m.cylX(13, 13, 2.5f, 2.5f, 2.0f, Tex.rgb(244, 236, 180));
        m.box(5, 4, 2, 6, 4, 2, Tex.rgb(200, 60, 40)); m.set(0, 2, 2, ring);
        return m;
    }

    private static VoxModel misc(ItemDef d) {
        String icon = d.icon == null ? d.category : d.icon;
        int h = Math.abs(d.id.hashCode());
        VoxModel m;
        switch (d.category) {
            case "magazine" -> {
                if (d.magazine.capacity >= 60 && d.magazine.magType.equals("stanag")) { m = new VoxModel(10, 10, 6); m.cylY(0, 4, 5, 3, 3.6f, POLY); m.cylY(5, 9, 5, 3, 3.6f, POLY); m.box(4, 7, 2, 6, 9, 3, METAL); }
                else if (d.magazine.magType.equals("belt")) { m = new VoxModel(10, 8, 7); m.box(0, 0, 0, 9, 6, 6, OLIVE); m.box(3, 7, 3, 6, 7, 3, METAL); }
                else if (d.magazine.magType.equals("pm9")) { m = new VoxModel(4, 10, 3); m.box(0, 0, 0, 3, 9, 2, METAL); m.box(0, 9, 1, 2, 9, 1, BRASS); }
                else { m = new VoxModel(8, 16, 4); int c = d.magazine.magType.equals("akr") ? Tex.rgb(150, 82, 40) : POLY;
                    for (int k = 0; k < 15; k++) m.box(k / 4, k, 0, k / 4 + 3, k, 3, c); m.box(3, 15, 1, 5, 15, 2, BRASS); }
            }
            case "ammo" -> m = ammo(d);
            case "attachment" -> {
                if (d.attachment.socket.equals("finish")) { m = new VoxModel(8, 10, 4); m.box(1, 0, 0, 6, 7, 3, d.attachment.tint == 0 ? STEEL : d.attachment.tint); m.box(2, 8, 1, 5, 9, 2, METAL); }
                else if (d.attachment.socket.equals("muzzle")) { m = new VoxModel(14, 5, 5); if (d.id.contains("suppressor")) m.cylX(0, 13, 2.5f, 2.5f, 2.2f, POLY); else { m.cylX(0, 5, 2.5f, 2.5f, 1.8f, METAL_LT); m.box(1, 2, 0, 4, 2, 4, METAL); } }
                else if (d.attachment.socket.equals("optic")) { m = new VoxModel(14, 8, 5); if (d.id.contains("scope")) { m.cylX(0, 13, 5, 2.5f, 2.2f, POLY); m.box(5, 0, 2, 7, 2, 2, METAL); m.set(0, 5, 2, GLASS); } else { m.box(3, 0, 1, 9, 2, 3, POLY); m.box(4, 3, 1, 8, 6, 3, POLY); m.set(6, 5, 2, Tex.rgb(220, 40, 40)); } }
                else if (d.attachment.socket.equals("barrel")) { m = new VoxModel(20, 5, 5); m.cylX(0, d.id.contains("short") ? 9 : 19, 2.5f, 2.5f, 1.4f, d.id.contains("chrome") ? STEEL : METAL); }
                else if (d.attachment.socket.equals("stock")) { m = new VoxModel(14, 9, 4); if (d.id.contains("folding")) { m.box(0, 4, 1, 12, 5, 2, METAL_LT); m.box(0, 0, 1, 1, 8, 2, METAL_LT); } else { m.box(0, 0, 1, 4, 8, 2, POLY); m.box(5, 4, 1, 13, 6, 2, POLY); } }
                else if (d.attachment.socket.equals("grip")) { m = new VoxModel(6, 10, 4); m.box(1, 0, 1, 3, 9, 2, POLY); m.box(0, 9, 0, 5, 9, 3, METAL); }
                else { m = new VoxModel(8, 5, 5); m.cylX(0, 7, 2.5f, 2.5f, 2f, METAL_LT); m.box(7, 1, 1, 7, 3, 3, Tex.rgb(240, 230, 160)); }
            }
            case "medical" -> {
                if (icon.startsWith("herb")) { m = herbPot(d.id); }
                else if (icon.contains("bandage")) { m = new VoxModel(8, 8, 8); m.cylY(0, 7, 4, 4, 3.6f, CLOTH); m.cylY(0, 7, 4, 4, 1.2f, 0); }
                else if (icon.contains("pills")) { m = new VoxModel(6, 10, 6); m.cylY(0, 7, 3, 3, 2.8f, icon.contains("green") ? Tex.rgb(70, 140, 70) : Tex.rgb(200, 130, 40)); m.cylY(8, 9, 3, 3, 2.8f, WHITE); }
                else if (icon.contains("injector")) { m = new VoxModel(16, 4, 4); m.box(0, 1, 1, 10, 2, 2, Tex.rgb(80, 160, 90)); m.box(11, 1, 1, 15, 1, 1, STEEL); m.box(0, 0, 0, 1, 3, 3, POLY); }
                else if (icon.contains("tourniquet")) { m = new VoxModel(10, 4, 10); for (int k = 0; k < 10; k++) { m.set(k, 1, 0, POLY); m.set(k, 1, 9, POLY); m.set(0, 1, k, POLY); m.set(9, 1, k, POLY); } m.box(3, 2, 3, 6, 3, 6, RED); }
                else if (icon.contains("splint")) { m = new VoxModel(14, 3, 6); m.box(0, 0, 0, 13, 0, 1, Tex.rgb(200, 120, 40)); m.box(0, 0, 4, 13, 0, 5, Tex.rgb(200, 120, 40)); m.box(3, 1, 0, 4, 2, 5, CLOTH); m.box(9, 1, 0, 10, 2, 5, CLOTH); }
                else if (icon.contains("spray")) { m = new VoxModel(6, 14, 6); m.cylY(0, 10, 3, 3, 2.8f, WHITE); m.box(2, 4, 0, 3, 6, 0, RED); m.cylY(11, 13, 3, 3, 1.4f, POLY); }
                else { m = new VoxModel(14, 8, 12); m.box(0, 0, 0, 13, 7, 11, Tex.rgb(170, 36, 32)); m.box(5, 2, 0, 8, 5, 0, WHITE); m.box(3, 3, 0, 10, 4, 0, WHITE); m.box(4, 8 - 1, 4, 9, 7, 7, METAL); }
            }
            case "food" -> {
                if (icon.contains("bottle")) { m = new VoxModel(6, 16, 6); int c = icon.contains("dirty") ? 0xD0807050 : 0xC080B0D0; m.cylY(0, 11, 3, 3, 2.8f, c); m.cylY(12, 13, 3, 3, 1.6f, c); m.cylY(14, 15, 3, 3, 1.4f, Tex.rgb(40, 90, 170)); m.box(1, 5, 0, 4, 8, 0, WHITE); }
                else if (icon.contains("can") || icon.contains("soda") || icon.contains("coffee")) { m = new VoxModel(8, 10, 8); int c = icon.contains("soda") ? RED : icon.contains("coffee") ? Tex.rgb(90, 60, 40) : Tex.rgb(160, 150, 130); m.cylY(0, 9, 4, 4, 3.6f, c); m.cylY(9, 9, 4, 4, 3.6f, STEEL); m.box(2, 3, 0, 5, 6, 0, WHITE); }
                else if (icon.startsWith("egg")) { m = new VoxModel(6, 8, 6); int c = icon.contains("gold") ? Tex.rgb(232, 186, 60) : icon.contains("white") ? Tex.rgb(244, 242, 234) : Tex.rgb(176, 122, 80); m.sphere(3, 3, 3, 2.9f, c); m.sphere(3, 5, 3, 2.1f, c); if (icon.contains("gold")) m.set(2, 6, 2, Tex.rgb(255, 236, 150)); }
                else if (icon.contains("meat")) { m = new VoxModel(10, 5, 8); m.sphere(5, 2, 4, 4f, Tex.rgb(150, 60, 60)); m.box(8, 2, 3, 9, 3, 4, WHITE); }
                else { m = new VoxModel(12, 4, 14); m.box(0, 0, 0, 11, 3, 13, icon.contains("mre") ? Tex.rgb(130, 112, 70) : Tex.rgb(200, 170, 100)); m.box(3, 3, 3, 8, 3, 10, icon.contains("mre") ? Tex.rgb(90, 80, 50) : WHITE); }
            }
            case "armor" -> m = wearable(d);
            case "backpack" -> { VoxModel g8 = GearModels08.model(d); if (g8 != null) { m = g8; break; } m = new VoxModel(12, 14, 8); m.box(0, 0, 1, 11, 12, 7, icon.contains("large") ? OLIVE : Tex.rgb(70, 60, 50)); m.box(2, 2, 0, 9, 6, 0, Tex.rgb(50, 44, 38)); m.box(4, 13, 3, 7, 13, 4, POLY); }
            case "explosive" -> {
                if (icon.startsWith("grenade")) m = grenade(icon.endsWith("red") ? Tex.rgb(176, 38, 34) : icon.endsWith("blue") ? Tex.rgb(44, 92, 190) : Tex.rgb(64, 110, 52));
                else if (icon.equals("clock")) { m = new VoxModel(10, 10, 5); m.cylX(0, 0, 0, 0, 0, 0); m.sphere(5, 5, 2.5f, 4.2f, RED); m.box(1, 2, 0, 8, 8, 4, 0); m.sphere(5, 5, 2.5f, 4f, RED); m.box(3, 3, 0, 6, 6, 0, WHITE); m.box(2, 9, 2, 2, 9, 2, BRASS); m.box(7, 9, 2, 7, 9, 2, BRASS); }
                else { m = new VoxModel(5, 14, 5); m.cylY(0, 12, 2.5f, 2.5f, 2.2f, icon.contains("flare") ? RED : METAL); m.cylY(13, 13, 2.5f, 2.5f, 1.2f, icon.contains("flare") ? WHITE : POLY); }
            }
            case "charm" -> { m = new VoxModel(6, 10, 3); m.box(2, 8, 1, 3, 9, 1, BRASS); m.sphere(3, 4, 1.5f, 3f, h % 2 == 0 ? Tex.rgb(200, 190, 160) : BRASS); }
            case "treasure" -> {
                if (icon.contains("map")) { m = new VoxModel(12, 2, 10); m.box(0, 0, 0, 11, 0, 9, Tex.rgb(200, 180, 130)); m.box(2, 1, 2, 9, 1, 2, Tex.rgb(140, 90, 50)); m.box(4, 1, 5, 7, 1, 7, Tex.rgb(120, 70, 40)); }
                else if (icon.startsWith("gem")) m = gem(icon);
                else if (icon.equals("ring")) m = ring();
                else { m = new VoxModel(8, 12, 8); m.cylY(0, 1, 4, 4, 3f, BRASS); m.cylY(2, 6, 4, 4, 1f, BRASS); m.cylY(7, 11, 4, 4, 3.5f, BRASS); m.cylY(8, 11, 4, 4, 2.4f, 0); if (icon.contains("gem")) m.set(4, 9, 0, RED); }
            }
            case "herb" -> m = herbPot(d.id);
            case "key" -> { m = new VoxModel(12, 6, 2); if (icon.contains("card")) { m.box(0, 0, 0, 11, 5, 0, WHITE); m.box(1, 3, 0, 4, 4, 0, Tex.rgb(40, 90, 170)); } else { m.box(0, 1, 0, 3, 4, 1, BRASS); m.box(4, 2, 0, 11, 3, 0, BRASS); m.box(9, 0, 0, 9, 1, 0, BRASS); m.box(11, 0, 0, 11, 1, 0, BRASS); } }
            case "document" -> { m = new VoxModel(10, 2, 12); m.box(0, 0, 0, 9, 0, 11, icon.contains("drive") ? POLY : WHITE); if (!icon.contains("drive")) for (int k = 2; k < 10; k += 2) m.box(1, 1, k, 8, 1, k, Tex.rgb(60, 60, 70)); }
            case "valuable" -> {
                if (icon.contains("cash")) { m = new VoxModel(12, 3, 6); m.box(0, 0, 0, 11, 2, 5, Tex.rgb(110, 140, 90)); m.box(5, 0, 0, 6, 3, 5, WHITE); }
                else if (icon.contains("gpu")) { m = new VoxModel(18, 4, 8); m.box(0, 0, 0, 17, 1, 7, Tex.rgb(30, 90, 50)); m.box(2, 2, 1, 15, 3, 6, POLY); m.cylY(2, 3, 6, 4, 2.2f, METAL); m.cylY(2, 3, 12, 4, 2.2f, METAL); }
                else if (icon.contains("chain") || icon.contains("watch")) { m = new VoxModel(10, 3, 10); for (int k = 0; k < 10; k++) { m.set(k, 0, 0, BRASS); m.set(k, 0, 9, BRASS); m.set(0, 0, k, BRASS); m.set(9, 0, k, BRASS); } if (icon.contains("watch")) { m.box(3, 0, 3, 6, 2, 6, STEEL); m.box(4, 2, 4, 5, 2, 5, WHITE); } }
                else if (icon.contains("artifact")) { m = new VoxModel(6, 14, 6); m.box(2, 0, 2, 3, 13, 3, Tex.rgb(120, 230, 200)); m.box(1, 3, 1, 4, 9, 4, 0xC060D0B0); m.box(2, 0, 2, 3, 13, 3, Tex.rgb(160, 250, 220)); }
                else if (icon.contains("icon_art")) { m = new VoxModel(14, 16, 2); m.box(0, 0, 0, 13, 15, 1, BRASS); m.box(2, 2, 0, 11, 13, 0, Tex.rgb(140, 40, 30)); m.box(5, 9, 0, 8, 12, 0, Tex.rgb(220, 190, 150)); }
                else { m = new VoxModel(8, 6, 6); m.box(0, 0, 0, 7, 5, 5, Tex.rgb(80 + h % 100, 80 + (h >> 3) % 80, 60 + (h >> 5) % 80)); m.box(2, 5, 2, 5, 5, 3, STEEL); }
            }
            case "quest" -> { m = new VoxModel(12, 10, 10); m.box(0, 0, 0, 11, 9, 9, Tex.rgb(150, 120, 80)); m.box(0, 4, 0, 11, 5, 9, Tex.rgb(200, 170, 60)); m.box(5, 0, 0, 6, 9, 9, Tex.rgb(200, 170, 60)); }
            default -> { // materials / tools
                if (icon.contains("flashlight")) m = torch();
                else if (icon.equals("whistle")) { m = new VoxModel(10, 5, 4); m.box(0, 1, 0, 6, 3, 3, Tex.rgb(196, 200, 208)); m.box(7, 2, 1, 9, 3, 2, Tex.rgb(150, 154, 162)); m.box(2, 4, 1, 3, 4, 2, Tex.rgb(40, 40, 44)); m.box(0, 0, 1, 1, 0, 2, Tex.rgb(170, 40, 34)); }
                else if (icon.startsWith("powder")) m = powder(icon);
                else if (icon.equals("plank")) m = plank();
                else if (icon.equals("casing")) m = casing();
                else if (icon.contains("fuel")) { m = new VoxModel(10, 14, 6); m.box(0, 0, 0, 9, 11, 5, RED); m.box(2, 12, 2, 7, 13, 3, POLY); }
                else if (icon.contains("oil")) { m = new VoxModel(6, 12, 6); m.cylY(0, 8, 3, 3, 2.6f, Tex.rgb(200, 160, 40)); m.cylY(9, 11, 3, 3, 1f, POLY); }
                else if (icon.contains("scrap") || icon.contains("parts")) { m = new VoxModel(14, 5, 8); m.box(0, 0, 0, 9, 1, 5, METAL_LT); m.box(5, 2, 2, 13, 3, 7, Tex.rgb(130, 80, 50)); m.box(2, 2, 1, 3, 4, 2, STEEL); }
                else if (icon.contains("tape")) { m = new VoxModel(8, 4, 8); m.cylY(0, 3, 4, 4, 3.8f, Tex.rgb(150, 150, 150)); m.cylY(0, 3, 4, 4, 1.8f, 0); }
                else if (icon.contains("circuit") || icon.contains("electrical")) { m = new VoxModel(12, 3, 10); m.box(0, 0, 0, 11, 0, 9, Tex.rgb(30, 110, 60)); m.box(2, 1, 2, 4, 2, 4, POLY); m.box(7, 1, 5, 9, 1, 8, BRASS); }
                else if (icon.contains("repairkit") || icon.contains("camera")) { m = new VoxModel(14, 10, 10); m.box(0, 0, 0, 13, 8, 9, icon.contains("camera") ? POLY : Tex.rgb(170, 50, 40)); m.box(4, 9, 4, 9, 9, 5, METAL); if (icon.contains("camera")) m.cylX(13, 13, 4, 5, 2, GLASS); }
                else { m = new VoxModel(10, 8, 10); int c = Tex.rgb(90 + h % 90, 90 + (h >> 4) % 70, 80 + (h >> 8) % 60); m.box(0, 0, 0, 9, 6, 9, c); m.box(0, 7, 0, 9, 7, 9, Tex.shade(c, 1.2f)); m.box(3, 3, 0, 6, 4, 0, Tex.shade(c, 0.6f)); }
            }
        }
        return m;
    }
}
