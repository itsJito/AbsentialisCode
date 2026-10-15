package absentialis.ui;

import absentialis.engine.voxel.VoxModel;
import absentialis.engine.render.Tex;
import absentialis.gameplay.items.ItemDef;

/**
 * The 0.8 equipment as objects: what each of the eighteen new helmets, sets of armour and packs looks like lying in a
 * grid, on a shop wall or turned in the hand. One model to an item, matching what it looks like worn
 * (character.Gear08). Returns null for anything that is not one of them.
 */
final class GearModels08 {
    private GearModels08() {}

    private static final int BLACK = Tex.rgb(22, 22, 26), VISOR = Tex.rgb(40, 60, 78), STRAP = Tex.rgb(40, 36, 30);

    static VoxModel model(ItemDef d) {
        return switch (d.id) {
            case "helmet.hardhat" -> hardhat();
            case "helmet.moto" -> moto();
            case "helmet.tactical" -> tactical();
            case "helmet.ballistic" -> ballistic();
            case "helmet.sealed" -> sealed();
            case "helmet.smart" -> smart();
            case "armor.scavenged" -> suit(Tex.rgb(196, 188, 170), Tex.rgb(150, 60, 50), Tex.rgb(150, 152, 150), 0);
            case "armor.rider" -> suit(Tex.rgb(44, 34, 28), Tex.rgb(70, 54, 42), Tex.rgb(26, 26, 30), 1);
            case "armor.tactical_rig" -> suit(Tex.rgb(86, 82, 62), Tex.rgb(58, 56, 44), Tex.rgb(40, 42, 46), 2);
            case "armor.full_ballistic" -> suit(Tex.rgb(56, 62, 50), Tex.rgb(78, 84, 70), Tex.rgb(36, 40, 34), 3);
            case "armor.advanced_suit" -> suit(Tex.rgb(40, 44, 50), Tex.rgb(28, 30, 34), Tex.rgb(196, 178, 60), 4);
            case "armor.powered" -> suit(Tex.rgb(170, 174, 180), Tex.rgb(60, 64, 72), Tex.rgb(30, 32, 38), 5);
            case "bag.school" -> bag(9, 11, 5, Tex.rgb(52, 92, 150), Tex.rgb(36, 64, 108), 0);
            case "bag.hiking" -> bag(9, 14, 6, Tex.rgb(190, 84, 40), Tex.rgb(60, 60, 64), 1);
            case "bag.tactical" -> bag(11, 13, 6, Tex.rgb(84, 82, 62), Tex.rgb(58, 56, 44), 2);
            case "bag.ballistic" -> bag(11, 12, 4, Tex.rgb(46, 50, 46), Tex.rgb(74, 80, 72), 3);
            case "bag.advanced" -> bag(13, 14, 6, Tex.rgb(40, 44, 50), Tex.rgb(86, 92, 100), 4);
            case "bag.warbag" -> bag(14, 17, 8, Tex.rgb(74, 82, 58), Tex.rgb(52, 58, 42), 5);
            default -> null;
        };
    }

    private static VoxModel shellOf(int col, float y0) {
        VoxModel m = new VoxModel(12, 11, 13);
        ItemModels.dome(m, 6, y0, 6.5f, 5.8f, 6.4f, col);
        ItemModels.carveInside(m, 6, y0, 6.5f, 4.8f, 5.4f);
        return m;
    }

    private static VoxModel hardhat() {
        int y = Tex.rgb(232, 190, 40), dk = Tex.rgb(176, 138, 24), crack = Tex.rgb(60, 48, 16);
        VoxModel m = new VoxModel(14, 8, 15);
        ItemModels.dome(m, 7, 1, 7.5f, 5.2f, 5.8f, y);
        ItemModels.carveInside(m, 7, 1, 7.5f, 4.2f, 4.8f);
        for (int x = 0; x < 14; x++) for (int z = 0; z < 15; z++) { float dx = (x + 0.5f - 7) / 7f, dz = (z + 0.5f - 7.5f) / 7.5f; if (dx * dx + dz * dz <= 1 && m.get(x, 0, z) == 0 && !(Math.abs(x - 6.5f) < 4 && Math.abs(z - 7.5f) < 4.6f)) m.set(x, 0, z, y); }
        m.box(6, 7, 3, 7, 7, 11, dk);
        m.set(10, 3, 5, crack); m.set(10, 4, 6, crack); m.set(11, 2, 6, crack); m.set(10, 5, 7, crack); m.set(9, 6, 7, crack);
        return m;
    }

    private static VoxModel moto() {
        int shell = Tex.rgb(170, 34, 30), scuff = Tex.rgb(120, 108, 100);
        VoxModel m = shellOf(shell, 3);
        m.box(0, 0, 2, 0, 3, 11, shell); m.box(11, 0, 2, 11, 3, 11, shell); m.box(1, 0, 11, 10, 3, 12, shell);
        m.box(1, 0, 0, 10, 2, 0, shell); m.box(0, 0, 0, 0, 2, 2, shell); m.box(11, 0, 0, 11, 2, 2, shell);    // chin bar
        m.box(1, 3, 0, 10, 6, 0, VISOR); m.box(0, 4, 1, 0, 5, 3, VISOR); m.box(11, 4, 1, 11, 5, 3, VISOR);
        m.set(8, 9, 9, scuff); m.set(9, 8, 9, scuff); m.set(3, 8, 11, scuff);
        return m;
    }

    private static VoxModel tactical() {
        int k = Tex.rgb(72, 70, 58), dk = Tex.rgb(46, 46, 40);
        VoxModel m = new VoxModel(12, 9, 13);
        ItemModels.dome(m, 6, 2, 6.5f, 5.6f, 6.2f, k);
        ItemModels.carveInside(m, 6, 2, 6.5f, 4.6f, 5.2f);
        m.box(0, 2, 4, 0, 3, 9, dk); m.box(11, 2, 4, 11, 3, 9, dk);                                           // rails
        m.box(1, 4, 0, 10, 5, 0, BLACK); m.box(2, 4, 0, 4, 5, 0, VISOR); m.box(7, 4, 0, 9, 5, 0, VISOR);      // goggles on the brow
        m.box(1, 0, 6, 1, 1, 6, STRAP); m.box(10, 0, 6, 10, 1, 6, STRAP); m.box(3, 0, 3, 8, 0, 3, STRAP);
        return m;
    }

    private static VoxModel ballistic() {
        int k = Tex.rgb(58, 66, 52), dk = Tex.rgb(38, 44, 36), glass = Tex.rgb(150, 176, 186);
        VoxModel m = shellOf(k, 3);
        m.box(0, 1, 3, 0, 3, 11, k); m.box(11, 1, 3, 11, 3, 11, k); m.box(1, 1, 11, 10, 3, 12, k);
        m.box(0, 6, 0, 11, 6, 0, dk);
        m.box(1, 0, 0, 10, 5, 0, glass); m.box(0, 0, 1, 0, 5, 1, glass); m.box(11, 0, 1, 11, 5, 1, glass);
        return m;
    }

    private static VoxModel sealed() {
        int k = Tex.rgb(44, 48, 52), dk = Tex.rgb(28, 30, 34), lens = Tex.rgb(190, 150, 60), can = Tex.rgb(96, 100, 98);
        VoxModel m = shellOf(k, 3);
        m.box(0, 0, 1, 11, 3, 12, k); m.box(1, 0, 2, 10, 3, 11, 0);
        m.box(1, 0, 0, 10, 6, 0, k);
        m.box(1, 4, 0, 4, 6, 0, lens); m.box(7, 4, 0, 10, 6, 0, lens);
        m.box(4, 0, 0, 7, 2, 0, dk); m.box(0, 0, 0, 2, 2, 0, can); m.box(9, 0, 0, 11, 2, 0, can);
        return m;
    }

    private static VoxModel smart() {
        int k = Tex.rgb(26, 28, 34), edge = Tex.rgb(70, 76, 90), glow = Tex.rgb(255, 120, 40), cam = Tex.rgb(140, 150, 170);
        VoxModel m = shellOf(k, 3);
        m.box(0, 0, 1, 11, 3, 12, k); m.box(1, 0, 2, 10, 3, 11, 0);
        m.box(1, 0, 0, 10, 7, 0, k);
        m.box(0, 4, 0, 11, 5, 0, glow); m.box(0, 4, 1, 0, 5, 2, glow); m.box(11, 4, 1, 11, 5, 2, glow);
        m.box(4, 7, 0, 7, 8, 0, edge); m.set(5, 8, 0, cam); m.set(6, 8, 0, cam);
        m.box(0, 3, 0, 11, 3, 0, edge); m.box(0, 6, 0, 11, 6, 0, edge);
        return m;
    }

    /** A set of armour laid flat: the body, its sleeves folded out, and the leg pieces under it. {@code kind} 0..5 by rarity. */
    private static VoxModel suit(int main, int second, int third, int kind) {
        VoxModel m = new VoxModel(18, 20, 6);
        // the body: a vest with a neck opening and two shoulder straps
        m.box(4, 7, 0, 13, 17, 5, main); m.box(5, 7, 1, 12, 16, 4, 0);
        m.box(7, 16, 0, 10, 19, 5, 0); m.box(4, 18, 1, 6, 19, 4, main); m.box(11, 18, 1, 13, 19, 4, main);
        // sleeves
        m.box(0, 10, 1, 3, 17, 4, kind == 0 ? third : kind == 4 ? third : main); m.box(14, 10, 1, 17, 17, 4, kind == 0 ? third : kind == 4 ? third : main);
        // leg pieces: pads, guards or whole trouser legs, by how much the set covers
        int legTop = kind >= 4 ? 6 : kind >= 2 ? 5 : 3;
        m.box(4, 0, 1, 8, legTop, 4, kind == 4 ? third : second); m.box(9, 0, 1, 13, legTop, 4, kind == 4 ? third : second);
        switch (kind) {
            case 0 -> { for (int x = 5; x <= 12; x += 2) m.box(x, 8, 0, x, 16, 0, second); m.box(4, 11, 0, 13, 11, 0, third); m.box(4, 14, 0, 13, 14, 0, third); }
            case 1 -> { m.box(8, 7, 0, 9, 17, 0, Tex.rgb(150, 150, 150)); m.box(0, 10, 0, 3, 11, 5, third); m.box(14, 10, 0, 17, 11, 5, third); m.box(4, 0, 0, 8, 2, 0, third); m.box(9, 0, 0, 13, 2, 0, third); }
            case 2 -> { m.box(6, 10, 0, 11, 14, 0, second); for (int x = 5; x <= 11; x += 3) m.box(x, 7, 0, x + 1, 9, 0, Tex.rgb(104, 96, 70)); m.box(0, 10, 0, 3, 17, 5, third); m.box(14, 10, 0, 17, 17, 5, third); }
            case 3 -> { m.box(6, 9, 0, 11, 15, 0, second); m.box(3, 9, 1, 3, 14, 4, second); m.box(14, 9, 1, 14, 14, 4, second); m.box(2, 16, 0, 5, 19, 5, third); m.box(12, 16, 0, 15, 19, 5, third); m.box(4, 3, 0, 8, 4, 0, third); m.box(9, 3, 0, 13, 4, 0, third); }
            case 4 -> { m.box(6, 10, 0, 11, 14, 0, second); m.box(0, 10, 0, 3, 12, 5, second); m.box(14, 10, 0, 17, 12, 5, second); m.box(0, 13, 1, 3, 13, 4, Tex.rgb(230, 230, 220)); m.box(14, 13, 1, 17, 13, 4, Tex.rgb(230, 230, 220)); }
            default -> { m.box(6, 9, 0, 11, 15, 0, second); m.box(8, 11, 0, 9, 13, 0, Tex.rgb(80, 200, 255)); m.box(2, 15, 0, 5, 19, 5, main); m.box(12, 15, 0, 15, 19, 5, main); m.box(8, 7, 5, 9, 18, 5, third); m.box(4, 0, 0, 8, 1, 5, second); m.box(9, 0, 0, 13, 1, 5, second); m.box(3, 0, 2, 3, 6, 3, third); m.box(14, 0, 2, 14, 6, 3, third); }
        }
        return m;
    }

    /** A pack standing on its base, straps toward the viewer's far side. {@code kind} 0..5 by rarity. */
    private static VoxModel bag(int w, int h, int dp, int main, int second, int kind) {
        VoxModel m = new VoxModel(w + 2, h + 2, dp + 3);
        m.box(1, 1, 2, w, h - 1, dp + 1, main);
        m.box(2, h, 2, w - 1, h, dp + 1, main);                                                               // the shoulders of the bag
        m.box(3, 2, 1, w - 2, Math.max(4, h / 2), 1, second);                                                 // front pocket or panel
        m.box(2, 2, dp + 2, 3, h - 2, dp + 2, STRAP); m.box(w - 2, 2, dp + 2, w - 1, h - 2, dp + 2, STRAP);   // straps
        m.box(w / 2, h + 1, dp / 2 + 1, w / 2 + 1, h + 1, dp / 2 + 2, STRAP);                                 // grab handle
        switch (kind) {
            case 0 -> { m.set(2, 2, 1, Tex.rgb(150, 160, 172)); m.set(w - 1, 4, 1, Tex.rgb(150, 160, 172)); m.set(w / 2, h - 2, 1, Tex.rgb(210, 190, 60)); }
            case 1 -> { m.box(1, h - 2, 2, w, h - 1, dp + 1, second); m.box(0, 2, 3, 0, 6, 4, Tex.rgb(90, 150, 190)); m.box(1, 1, 1, w, 1, 1, STRAP); }
            case 2 -> { for (int y = 3; y <= h - 2; y += 2) m.box(2, y, 1, w - 1, y, 1, second); m.box(0, 3, 3, 0, 7, dp, Tex.rgb(104, 98, 72)); m.box(w + 1, 3, 3, w + 1, 7, dp, Tex.rgb(104, 98, 72)); }
            case 3 -> { m.box(2, 2, 1, w - 1, h - 2, 1, second); m.box(1, 1, 1, w, 1, 1, BLACK); m.box(1, h - 1, 1, w, h - 1, 1, BLACK); }
            case 4 -> { m.box(2, 2, 1, w - 1, h - 3, 1, second); m.box(1, h, 2, w, h, dp + 1, Tex.rgb(196, 178, 60)); m.box(1, 1, 1, 1, h - 1, 1, BLACK); m.box(w, 1, 1, w, h - 1, 1, BLACK); }
            default -> { m.box(0, 3, 3, 0, 10, dp, second); m.box(w + 1, 3, 3, w + 1, 10, dp, second); m.box(1, 0, 2, w, 0, dp, Tex.rgb(120, 104, 72)); m.box(2, h, 2, w - 1, h + 1, dp + 1, second); m.set(4, h - 3, 1, Tex.rgb(150, 150, 140)); m.set(w - 3, h - 3, 1, Tex.rgb(150, 150, 140)); }
        }
        return m;
    }
}
