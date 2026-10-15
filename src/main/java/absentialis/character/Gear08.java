package absentialis.character;

import static absentialis.character.Rig.*;

import absentialis.engine.render.Tex;

/**
 * What the 0.8 equipment looks like when it is worn: six helmets, six sets of armour and six packs, one of each to a
 * rarity (content/items.json, "0.8 ADDITIONS"). They sit beside the 0.7 pieces in {@link CharBuilder} and are drawn
 * the same way - boxes and shells of voxels on the body's own groups - so they ride the skeleton, tear with it and
 * take its lighting. A set of armour covers what its description says it covers: sleeves are on the arms, knee pads
 * and shin guards are on the legs.
 */
final class Gear08 {
    private Gear08() {}

    private static final int BLACK = Tex.rgb(22, 22, 26), RUBBER = Tex.rgb(36, 40, 40), VISOR = Tex.rgb(40, 60, 78), STRAP = Tex.rgb(40, 36, 30);

    /** True if the name was one of this file's helmets (and it has been put on the head). */
    static boolean head(CharModel m, String kind) {
        switch (kind) {
            case "hardhat" -> {
                // a cracked construction hard hat: yellow shell with a ridge, a brim all the way round, the crack down one side
                int y = Tex.rgb(232, 190, 40), dk = Tex.rgb(176, 138, 24), crack = Tex.rgb(60, 48, 16);
                m.box(HEAD, 2, 10, 2, 9, 12, 9, y); m.box(HEAD, 3, 13, 3, 8, 13, 8, y);
                m.shell(HEAD, 1, 10, 1, 10, 10, 10, y);                                    // the brim
                m.box(HEAD, 0, 10, 0, 11, 10, 1, y);                                        // and its peak, a little longer in front
                m.box(HEAD, 5, 13, 2, 6, 13, 9, dk);                                        // the ridge
                m.box(HEAD, 8, 11, 2, 8, 12, 4, crack); m.set(HEAD, 9, 12, 4, crack); m.set(HEAD, 9, 11, 5, crack);
            }
            case "moto" -> {
                // a used motorcycle helmet: full face, a dark visor, a chin bar, scuffs
                int shell = Tex.rgb(170, 34, 30), scuff = Tex.rgb(120, 108, 100);
                m.shell(HEAD, 1, 1, 1, 10, 11, 10, shell); m.box(HEAD, 2, 12, 2, 9, 12, 9, shell);
                m.box(HEAD, 2, 5, 1, 9, 8, 1, VISOR); m.box(HEAD, 1, 6, 2, 1, 7, 3, VISOR); m.box(HEAD, 10, 6, 2, 10, 7, 3, VISOR);
                m.box(HEAD, 2, 1, 0, 9, 3, 0, shell);                                       // chin bar
                m.tint(HEAD, 3, 10, 10, 6, 11, 10, scuff); m.tint(HEAD, 10, 8, 5, 10, 9, 7, scuff);
            }
            case "tactical" -> {
                // a bump helmet cut high over the ears, rails down the sides, goggles pushed up on the brow
                int k = Tex.rgb(72, 70, 58), dk = Tex.rgb(46, 46, 40);
                m.shell(HEAD, 1, 8, 1, 10, 11, 10, k); m.box(HEAD, 2, 12, 2, 9, 12, 9, k);
                m.box(HEAD, 2, 8, 1, 9, 9, 1, 0);                                           // open over the brow
                m.box(HEAD, 1, 8, 4, 1, 9, 8, dk); m.box(HEAD, 10, 8, 4, 10, 9, 8, dk);     // rails
                m.box(HEAD, 2, 10, 0, 9, 11, 0, BLACK); m.box(HEAD, 3, 10, 0, 4, 11, 0, VISOR); m.box(HEAD, 7, 10, 0, 8, 11, 0, VISOR);   // goggles
                m.box(HEAD, 1, 3, 5, 1, 7, 5, STRAP); m.box(HEAD, 10, 3, 5, 10, 7, 5, STRAP); m.box(HEAD, 4, 1, 2, 7, 1, 2, STRAP);
            }
            case "ballistic" -> {
                // a ballistic helmet down over the ears, and a clear face shield hinged at the brow
                int k = Tex.rgb(58, 66, 52), dk = Tex.rgb(38, 44, 36), glass = Tex.rgb(150, 176, 186);
                m.shell(HEAD, 1, 5, 2, 10, 11, 10, k); m.box(HEAD, 2, 12, 2, 9, 12, 9, k); m.box(HEAD, 1, 9, 1, 10, 11, 1, k);
                m.box(HEAD, 1, 9, 0, 10, 9, 0, dk);                                         // the hinge bar
                m.box(HEAD, 2, 2, 0, 9, 8, 0, glass); m.box(HEAD, 1, 3, 1, 1, 8, 1, glass); m.box(HEAD, 10, 3, 1, 10, 8, 1, glass);
                m.box(HEAD, 4, 1, 2, 7, 1, 2, STRAP);
            }
            case "sealed" -> {
                // sealed: nothing of the head is left open - a shell to the collar, two lenses, a respirator and its canisters
                int k = Tex.rgb(44, 48, 52), dk = Tex.rgb(28, 30, 34), lens = Tex.rgb(190, 150, 60), can = Tex.rgb(96, 100, 98);
                m.shell(HEAD, 1, 0, 1, 10, 11, 10, k); m.box(HEAD, 2, 12, 2, 9, 12, 9, k);
                m.box(HEAD, 2, 5, 0, 4, 7, 0, lens); m.box(HEAD, 7, 5, 0, 9, 7, 0, lens); m.box(HEAD, 5, 6, 0, 6, 6, 0, dk);
                m.box(HEAD, 4, 1, 0, 7, 3, 0, dk);                                          // the respirator
                m.box(HEAD, 1, 1, 0, 2, 3, 1, can); m.box(HEAD, 9, 1, 0, 10, 3, 1, can);     // canisters
                m.box(HEAD, 1, 0, 2, 10, 0, 10, RUBBER);                                    // neck seal
            }
            case "smart" -> {
                // the smart helmet: a faceted black shell, a sensor block on the brow, one band of thermal glass across the eyes
                int k = Tex.rgb(26, 28, 34), edge = Tex.rgb(70, 76, 90), glow = Tex.rgb(255, 120, 40), cam = Tex.rgb(140, 150, 170);
                m.shell(HEAD, 1, 1, 1, 10, 11, 10, k); m.box(HEAD, 2, 12, 3, 9, 12, 9, k);
                m.box(HEAD, 1, 6, 0, 10, 7, 0, glow); m.box(HEAD, 1, 6, 1, 1, 7, 2, glow); m.box(HEAD, 10, 6, 1, 10, 7, 2, glow);
                m.box(HEAD, 4, 9, 0, 7, 10, 0, edge); m.set(HEAD, 5, 10, 0, cam); m.set(HEAD, 6, 10, 0, cam);   // the sensor block
                m.box(HEAD, 1, 8, 1, 10, 8, 1, edge); m.box(HEAD, 1, 5, 1, 10, 5, 1, edge);
                m.box(HEAD, 0, 4, 5, 0, 7, 7, edge); m.box(HEAD, 11, 4, 5, 11, 7, 7, edge);   // ear modules
                m.box(HEAD, 10, 12, 8, 10, 13, 8, edge);                                     // antenna
            }
            default -> { return false; }
        }
        return true;
    }

    /** True if the name was one of this file's sets of armour. */
    static boolean vest(CharModel m, String kind) {
        switch (kind) {
            case "scav" -> {
                // scavenged: magazines taped round the body, duct tape up the sleeves, knee pads
                int paper = Tex.rgb(196, 188, 170), ink = Tex.rgb(150, 60, 50), tape = Tex.rgb(150, 152, 150), pad = Tex.rgb(50, 50, 54);
                m.shell(TORSO, 2, 3, 3, 11, 10, 8, paper); m.tint(TORSO, 3, 3, 4, 10, 10, 7, paper);
                for (int x = 3; x <= 10; x += 2) m.tint(TORSO, x, 3, 2, x, 10, 9, ink);                       // the spines of the magazines
                m.tint(TORSO, 2, 5, 2, 11, 5, 9, tape); m.tint(TORSO, 2, 8, 2, 11, 8, 9, tape);
                m.box(TORSO, 4, 10, 3, 5, 12, 8, tape); m.box(TORSO, 8, 10, 3, 9, 12, 8, tape);
                m.tintBoth(L_UPPER_ARM, 0, 2, 0, 5, 3, 5, tape); m.tintBoth(L_FOREARM, 0, 1, 0, 5, 4, 5, tape);
                m.shellBoth(L_SHIN, 1, 5, 0, 4, 6, 1, pad);
            }
            case "rider" -> {
                // the rider's kit: a leather jacket, hard cups at the elbows, cargo trousers with shin guards
                int leather = Tex.rgb(44, 34, 28), seam = Tex.rgb(70, 54, 42), cup = Tex.rgb(26, 26, 30), zip = Tex.rgb(150, 150, 150);
                m.tint(TORSO, 2, 2, 2, 11, 12, 9, leather); m.shell(TORSO, 2, 3, 3, 11, 11, 8, leather);
                m.tint(TORSO, 6, 3, 2, 7, 11, 2, zip); m.tint(TORSO, 2, 9, 2, 11, 9, 9, seam);
                m.tintBoth(L_UPPER_ARM, 0, 0, 0, 5, 6, 5, leather); m.tintBoth(L_FOREARM, 0, 0, 0, 5, 5, 5, leather);
                m.shellBoth(L_FOREARM, 0, 4, 1, 5, 5, 5, cup);                                               // elbow cups
                m.shellBoth(L_SHIN, 1, 1, 0, 4, 5, 0, cup); m.shellBoth(L_SHIN, 1, 6, 0, 4, 6, 1, cup);       // shin guards and knee
            }
            case "tacrig" -> {
                // the tactical rig: a light plate carrier, cut-resistant sleeves, gloves, reinforced trousers
                int k = Tex.rgb(86, 82, 62), dk = Tex.rgb(58, 56, 44), sleeve = Tex.rgb(40, 42, 46), pouch = Tex.rgb(104, 96, 70);
                m.shell(TORSO, 2, 3, 3, 11, 10, 8, k); m.tint(TORSO, 3, 3, 4, 10, 10, 7, k);
                m.box(TORSO, 4, 10, 3, 5, 12, 8, dk); m.box(TORSO, 8, 10, 3, 9, 12, 8, dk);
                m.box(TORSO, 4, 6, 2, 9, 9, 2, dk);                                                           // the plate
                for (int x = 3; x <= 9; x += 3) m.box(TORSO, x, 3, 2, x + 1, 5, 2, pouch);
                m.tintBoth(L_UPPER_ARM, 0, 0, 0, 5, 6, 5, sleeve); m.tintBoth(L_FOREARM, 0, 0, 0, 5, 5, 5, sleeve);
                m.tintBoth(L_HAND, 0, 0, 0, 5, 2, 5, BLACK);
                m.tintBoth(L_THIGH, 0, 0, 0, 5, 6, 5, dk); m.shellBoth(L_SHIN, 1, 5, 0, 4, 6, 1, dk);
            }
            case "fullballistic" -> {
                // full ballistic: a heavy carrier with side plates, shoulder and forearm guards, knee protection
                int k = Tex.rgb(56, 62, 50), dk = Tex.rgb(36, 40, 34), plate = Tex.rgb(78, 84, 70);
                m.shell(TORSO, 1, 2, 2, 12, 11, 9, k); m.tint(TORSO, 2, 2, 3, 11, 11, 8, k);
                m.box(TORSO, 4, 5, 2, 9, 10, 2, plate); m.box(TORSO, 4, 5, 9, 9, 10, 9, plate);
                m.box(TORSO, 1, 4, 4, 1, 8, 7, plate); m.box(TORSO, 12, 4, 4, 12, 8, 7, plate);               // side plates
                m.box(TORSO, 0, 10, 3, 2, 12, 8, dk); m.box(TORSO, 11, 10, 3, 13, 12, 8, dk);                 // shoulder guards
                m.box(TORSO, 4, 11, 3, 9, 12, 8, dk);                                                         // collar
                for (int x = 3; x <= 9; x += 3) m.box(TORSO, x, 2, 2, x + 1, 4, 2, dk);
                m.shellBoth(L_UPPER_ARM, 0, 3, 0, 5, 6, 5, k); m.shellBoth(L_FOREARM, 0, 1, 0, 5, 4, 5, dk);
                m.tintBoth(L_THIGH, 0, 0, 0, 5, 6, 5, k); m.shellBoth(L_SHIN, 1, 4, 0, 4, 6, 1, dk);
            }
            case "advsuit" -> {
                // the advanced suit: a sealed chemical suit head to foot, a ballistic vest over it, rigid gauntlets
                int suit = Tex.rgb(196, 178, 60), fold = Tex.rgb(150, 134, 40), vest = Tex.rgb(40, 44, 50), gaunt = Tex.rgb(28, 30, 34), seal = Tex.rgb(230, 230, 220);
                m.tint(TORSO, 0, 0, 0, 13, 13, 11, suit);
                m.shell(TORSO, 2, 3, 3, 11, 10, 8, vest); m.tint(TORSO, 3, 3, 4, 10, 10, 7, vest);
                m.box(TORSO, 4, 6, 2, 9, 9, 2, gaunt); m.box(TORSO, 4, 10, 3, 5, 12, 8, vest); m.box(TORSO, 8, 10, 3, 9, 12, 8, vest);
                m.tintBoth(L_UPPER_ARM, 0, 0, 0, 5, 6, 5, suit); m.tintBoth(L_FOREARM, 0, 0, 0, 5, 5, 5, suit);
                m.tintBoth(L_UPPER_ARM, 0, 3, 0, 5, 3, 5, fold);
                m.shellBoth(L_FOREARM, 0, 0, 0, 5, 3, 5, gaunt); m.tintBoth(L_HAND, 0, 0, 0, 5, 2, 5, gaunt);
                m.tintBoth(L_FOREARM, 0, 4, 0, 5, 4, 5, seal);                                                // the taped seal at the cuff
                m.tintBoth(L_THIGH, 0, 0, 0, 5, 6, 5, suit); m.tintBoth(L_SHIN, 0, 0, 0, 5, 6, 7, suit);
                m.tintBoth(L_THIGH, 0, 3, 0, 5, 3, 5, fold); m.shellBoth(L_SHIN, 1, 4, 0, 4, 6, 1, gaunt);
                m.tintBoth(L_SHIN, 0, 2, 0, 5, 2, 7, seal);
            }
            case "powered" -> {
                // powered composite: white-grey composite plate, a load-bearing frame up the back and down the legs, braces, heavy boots
                int comp = Tex.rgb(170, 174, 180), dk = Tex.rgb(60, 64, 72), frame = Tex.rgb(30, 32, 38), glow = Tex.rgb(80, 200, 255);
                m.shell(TORSO, 1, 2, 2, 12, 12, 9, comp); m.tint(TORSO, 2, 2, 3, 11, 11, 8, comp);
                m.box(TORSO, 4, 5, 2, 9, 10, 2, dk); m.box(TORSO, 6, 7, 2, 7, 8, 2, glow);                     // the core
                m.box(TORSO, 0, 10, 2, 2, 13, 9, comp); m.box(TORSO, 11, 10, 2, 13, 13, 9, comp);             // pauldrons
                m.box(TORSO, 6, 2, 10, 7, 12, 10, frame); m.box(TORSO, 3, 11, 10, 10, 11, 10, frame); m.box(TORSO, 3, 3, 10, 10, 3, 10, frame);   // the spine of the frame
                m.box(TORSO, 5, 6, 11, 8, 9, 11, dk);                                                          // power pack
                m.shellBoth(L_UPPER_ARM, 0, 1, 0, 5, 6, 5, comp); m.shellBoth(L_FOREARM, 0, 0, 0, 5, 5, 5, comp);
                m.tintBoth(L_UPPER_ARM, 0, 3, 0, 5, 3, 5, dk); m.tintBoth(L_HAND, 0, 0, 0, 5, 2, 5, frame);
                m.shellBoth(L_THIGH, 0, 0, 0, 5, 6, 5, comp); m.tintBoth(L_THIGH, 0, 3, 0, 5, 3, 5, dk);
                m.both(L_THIGH, 0, 0, 2, 0, 6, 3, frame);                                                      // the braces down the outside of the leg
                m.shellBoth(L_SHIN, 0, 2, 0, 5, 6, 6, comp); m.both(L_SHIN, 0, 1, 3, 0, 6, 4, frame);
                m.shellBoth(L_SHIN, 0, 0, 0, 5, 1, 7, dk);                                                     // reinforced boots
            }
            default -> { return false; }
        }
        return true;
    }

    /** True if the name was one of this file's packs. */
    static boolean pack(CharModel m, String kind) {
        switch (kind) {
            case "school" -> {
                // a fraying school backpack: small, bright once, a front pocket, one strap repaired with tape
                int k = Tex.rgb(52, 92, 150), dk = Tex.rgb(36, 64, 108), fray = Tex.rgb(150, 160, 172);
                m.box(TORSO, 4, 5, 8, 9, 10, 10, k); m.box(TORSO, 5, 5, 11, 8, 7, 11, dk);
                m.box(TORSO, 4, 11, 4, 4, 11, 8, STRAP); m.box(TORSO, 9, 11, 4, 9, 11, 8, fray);
                m.set(TORSO, 4, 5, 10, fray); m.set(TORSO, 9, 6, 10, fray); m.set(TORSO, 6, 10, 10, Tex.rgb(210, 190, 60));   // a keyring
            }
            case "hiking" -> {
                // a civilian hiking pack: tall and narrow, a lid, a hip belt, a bottle in the side pocket
                int k = Tex.rgb(190, 84, 40), dk = Tex.rgb(60, 60, 64), bottle = Tex.rgb(90, 150, 190);
                m.box(TORSO, 4, 3, 8, 9, 11, 10, k); m.box(TORSO, 4, 12, 8, 9, 13, 10, dk);                    // the lid
                m.box(TORSO, 5, 4, 11, 8, 8, 11, dk); m.box(TORSO, 3, 4, 9, 3, 7, 9, bottle);
                m.box(TORSO, 2, 3, 3, 11, 3, 8, STRAP);                                                        // hip belt
                m.box(TORSO, 4, 11, 4, 4, 12, 8, STRAP); m.box(TORSO, 9, 11, 4, 9, 12, 8, STRAP);
            }
            case "tacbag" -> {
                // a modular assault pack: webbing rows, pouches clipped to them
                int k = Tex.rgb(84, 82, 62), web = Tex.rgb(58, 56, 44), pouch = Tex.rgb(104, 98, 72);
                m.box(TORSO, 3, 3, 8, 10, 11, 10, k);
                for (int y = 4; y <= 10; y += 2) m.box(TORSO, 3, y, 11, 10, y, 11, web);
                m.box(TORSO, 4, 4, 11, 6, 6, 11, pouch); m.box(TORSO, 8, 7, 11, 9, 9, 11, pouch);
                m.box(TORSO, 2, 5, 9, 2, 8, 10, pouch); m.box(TORSO, 11, 5, 9, 11, 8, 10, pouch);
                m.box(TORSO, 4, 11, 4, 4, 12, 8, STRAP); m.box(TORSO, 9, 11, 4, 9, 12, 8, STRAP);
            }
            case "ballpack" -> {
                // the compact ballistic pack: a hard panel on the back, hardly deeper than a plate
                int k = Tex.rgb(46, 50, 46), plate = Tex.rgb(74, 80, 72), edge = Tex.rgb(28, 30, 28);
                m.box(TORSO, 3, 3, 8, 10, 11, 9, k); m.box(TORSO, 4, 4, 10, 9, 10, 10, plate);
                m.box(TORSO, 3, 3, 10, 10, 3, 10, edge); m.box(TORSO, 3, 11, 10, 10, 11, 10, edge);
                m.box(TORSO, 4, 11, 4, 4, 12, 8, STRAP); m.box(TORSO, 9, 11, 4, 9, 12, 8, STRAP);
            }
            case "advbag" -> {
                // the advanced ballistic bag: the same hard panel, larger, with a rigid frame and a sealed roll top
                int k = Tex.rgb(40, 44, 50), plate = Tex.rgb(86, 92, 100), roll = Tex.rgb(196, 178, 60), edge = Tex.rgb(24, 26, 30);
                m.box(TORSO, 2, 2, 8, 11, 12, 10, k); m.box(TORSO, 3, 3, 11, 10, 10, 11, plate);
                m.box(TORSO, 2, 13, 8, 11, 13, 10, roll);
                m.box(TORSO, 2, 2, 11, 2, 12, 11, edge); m.box(TORSO, 11, 2, 11, 11, 12, 11, edge);
                m.box(TORSO, 4, 11, 4, 4, 12, 8, STRAP); m.box(TORSO, 9, 11, 4, 9, 12, 8, STRAP); m.box(TORSO, 2, 3, 3, 11, 3, 8, STRAP);
            }
            case "warbag" -> {
                // the military warbag: the biggest thing anyone carries - a sack from the hips to above the head, a bedroll under it, pouches down both sides
                int k = Tex.rgb(74, 82, 58), dk = Tex.rgb(52, 58, 42), roll = Tex.rgb(120, 104, 72), buckle = Tex.rgb(150, 150, 140);
                m.box(TORSO, 2, 2, 8, 11, 13, 11, k); m.box(TORSO, 3, 3, 11, 10, 9, 11, dk);
                m.box(TORSO, 1, 3, 9, 1, 9, 10, dk); m.box(TORSO, 12, 3, 9, 12, 9, 10, dk);                   // side pouches
                m.box(TORSO, 2, 0, 8, 11, 1, 10, roll);                                                       // bedroll slung beneath
                m.box(TORSO, 3, 13, 9, 10, 13, 11, dk);                                                       // the lid
                m.set(TORSO, 5, 10, 11, buckle); m.set(TORSO, 8, 10, 11, buckle);
                m.box(TORSO, 4, 11, 4, 4, 12, 8, STRAP); m.box(TORSO, 9, 11, 4, 9, 12, 8, STRAP); m.box(TORSO, 2, 3, 3, 11, 3, 8, STRAP);
            }
            default -> { return false; }
        }
        return true;
    }
}
