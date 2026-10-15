package absentialis.scene;

import static absentialis.character.Rig.*;

import absentialis.character.Animator;
import absentialis.character.Appearance;
import absentialis.character.CharBuilder;
import absentialis.character.CharModel;
import absentialis.character.Pose;
import absentialis.character.Rig;
import absentialis.engine.math.Mat4;
import absentialis.engine.render.ModelRenderer;
import absentialis.engine.render.Tex;
import absentialis.engine.voxel.VoxModel;
import absentialis.entity.Actor;
import absentialis.gameplay.inventory.Loadout;
import absentialis.gameplay.items.ItemInstance;
import absentialis.persistence.Profile;
import absentialis.ui.ItemModels;
import absentialis.world.World;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws characters as twelve skinned voxel parts on the shared rig. A character is a look
 * (definition-table indices) plus visible gear; the assembled part meshes are cached by that key
 * and posed every frame by the {@link Animator} - or by the ragdoll once the actor is dead. The
 * same model is used in third person, as the first-person body and for the viewmodel arms.
 */
public final class ActorRenderer {
    public static final float VOX = Rig.VOX;
    /** Held weapons are modelled at 1/40 m per voxel; in character voxels that is this scale. */
    public static final float WEAPON_SCALE = (1f / 40f) / VOX;
    private final ModelRenderer mr;
    /** Assembled looks, least recently drawn first: a full cache lets go of the oldest one, never of everybody at once (0.7). */
    private final Map<String, CharModel> models = new java.util.LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, CharModel> e) { return size() > MODEL_CACHE; }
    };
    public static int MODEL_CACHE = 700;
    private final Mat4[] bones = new Mat4[GROUPS];
    private final Mat4 sawBone = new Mat4(), wm4 = new Mat4();
    /** 0.9, Director Mode's scene tape: somewhere the bones of every character drawn are handed to, and somewhere they can be taken from instead of being worked out. */
    public interface BoneSink { void bones(Actor a, Mat4[] bones, Mat4 weaponOrNull); }
    public interface BoneSource { /** 0 = not in the scene: draw nothing; 1 = bones written; 2 = bones and the weapon's matrix written. */ int bones(Actor a, Mat4[] bones, Mat4 weapon, boolean unused); }
    public BoneSink capture;
    public BoneSource replay;
    private boolean dry;

    /** Works out a character's whole body as seen from outside and hands it to {@link #capture} without drawing anything (the first-person player, whose own draw is a body without a head). */
    public void poseOnly(Actor a, Look l, float aimPitch, Animator.Ground ground) { if (capture == null) return; dry = true; try { draw(a, l, 1, aimPitch, ground, 0, false); } finally { dry = false; } }
    private final Mat4 base = new Mat4(), hips = new Mat4(), aim = new Mat4(), wpn = new Mat4(), m = new Mat4();
    private final Pose pose = new Pose();
    private final float[] v = new float[3];
    /** SKIN groups hidden on the first-person body: the head (the camera is inside it) and the arms the viewmodel draws. Legs stay. */
    public static final int FP_HIDE = bit(HEAD) | ARMS_MASK;
    /** Whole-character opacity (third-person camera pulled in close to the player). */
    public float alpha = 1;

    /** Meshes of damaged regions, built from their voxels (anatomy.WoundMeshGenerator). */
    public final absentialis.anatomy.WoundMeshGenerator wounds;

    public ActorRenderer(ModelRenderer mr) { this.mr = mr; wounds = new absentialis.anatomy.WoundMeshGenerator(mr); for (int g = 0; g < GROUPS; g++) bones[g] = new Mat4(); }

    /** A broken arm hangs: its joints give up their animation and sway with the body's movement instead. */
    private static void limp(Pose p, int mask, Actor a) {
        float sway = (float) Math.sin(a.walkPhase * 3.1f) * Math.min(0.5f, 0.12f + (Math.abs(a.vx) + Math.abs(a.vz)) * 0.06f);
        for (int g = L_UPPER_ARM; g <= R_HAND; g++) {
            if ((mask & (1 << g)) == 0) continue;
            boolean upper = g == L_UPPER_ARM || g == R_UPPER_ARM, fore = g == L_FOREARM || g == R_FOREARM;
            p.rot[g * 3] = upper ? sway : fore ? -0.12f - sway * 0.5f : 0;
            p.rot[g * 3 + 1] = 0;
            p.rot[g * 3 + 2] = upper ? (leftSide(g) ? -0.07f : 0.07f) : 0;
        }
    }

    /** Appearance + gear resolved for an actor, with the cache key of its assembled model. */
    public static final class Look {
        public Appearance app;
        public final CharBuilder.Gear gear = new CharBuilder.Gear();
        public String key;
        public float scale = 1;
    }

    // ------------------------------------------------------------------ looks
    /** The player's look: profile definition ids plus whatever is actually equipped. */
    public static Look playerLook(Profile p, Look reuse) {
        Look l = reuse != null ? reuse : new Look();
        l.app = p.appearance;
        Loadout lo = p.loadout;
        ItemInstance head = lo.slot("head"), face = lo.slot("face"), vest = lo.slot("armor"), pack = lo.slot("backpack");
        l.gear.head = headGear(head == null ? "" : head.defId);
        l.gear.face = faceGear(face == null ? "" : face.defId);
        l.gear.vest = vestGear(vest == null ? "" : vest.defId);
        l.gear.pack = pack == null ? "case" : packGear(pack.defId);
        l.gear.torch = lo.count("tool.flashlight") > 0;
        l.gear.facePaint = p.prog.cosmetics.contains("face_paint") && p.facePaint;
        l.gear.helmetMarks = p.prog.cosmetics.contains("helmet_marks");
        l.key = CharBuilder.key(l.app, l.gear);
        return l;
    }

    // ---- 0.7: every worn item has its own piece of model. These say which, by item id.
    public static String headGear(String id) {
        if (id == null || id.isEmpty()) return "";
        absentialis.gameplay.items.ItemDef d = absentialis.gameplay.items.Content.def(id);
        return switch (id) { case "helmet.ssh" -> "milhelmet"; case "helmet.cap" -> "fieldcap"; case "helmet.baseball" -> "baseball"; case "helmet.bike" -> "bike";
            // 0.8
            case "helmet.hardhat" -> "hardhat"; case "helmet.moto" -> "moto"; case "helmet.tactical" -> "tactical"; case "helmet.ballistic" -> "ballistic"; case "helmet.sealed" -> "sealed"; case "helmet.smart" -> "smart";
            default -> d != null && d.armor != null && d.armor.armorClass > 0 ? "helmet" : "cap"; };
    }

    /** 0.8: which pack is on the back, by item id (the two older ones are told apart by size, as before). */
    public static String packGear(String id) {
        if (id == null || id.isEmpty()) return "";
        absentialis.gameplay.items.ItemDef d = absentialis.gameplay.items.Content.def(id);
        return switch (id) {
            case "bag.school" -> "school"; case "bag.hiking" -> "hiking"; case "bag.tactical" -> "tacbag"; case "bag.ballistic" -> "ballpack"; case "bag.advanced" -> "advbag"; case "bag.warbag" -> "warbag";
            default -> d != null && d.backpack != null && d.backpack.gw >= 8 ? "large" : "small"; };
    }

    public static String faceGear(String id) {
        if (id == null || id.isEmpty()) return "";
        absentialis.gameplay.items.ItemDef d = absentialis.gameplay.items.Content.def(id);
        return "face.sunglasses".equals(id) ? "sunglasses" : d != null && d.armor != null && d.armor.gasMask ? "gasmask" : "balaclava";
    }

    public static String vestGear(String id) {
        if (id == null || id.isEmpty()) return "";
        absentialis.gameplay.items.ItemDef d = absentialis.gameplay.items.Content.def(id);
        if (d == null || d.armor == null) return "";
        return switch (id) { case "armor.vest_soft" -> "soft"; case "armor.body" -> "body"; case "armor.heavy" -> "heavy";
            // 0.8
            case "armor.scavenged" -> "scav"; case "armor.rider" -> "rider"; case "armor.tactical_rig" -> "tacrig"; case "armor.full_ballistic" -> "fullballistic"; case "armor.advanced_suit" -> "advsuit"; case "armor.powered" -> "powered";
            default -> d.armor.armorClass >= 4 ? "plate" : d.armor.armorClass >= 3 ? "mil" : "soft"; };
    }

    /** What is still on a body: a worn item that has been taken off the corpse is no longer drawn on it. */
    private static String stillWorn(Actor a, int which, String id) {
        if (id.isEmpty() || !a.dead || a.wornItems == null || a.wornItems[which] == null) return id;
        // (0.9: on the body means in the body's equipment slot, or - a profile from before - in its container)
        return a.corpseGear != null && a.wornItems[which].slotOwner == a.corpseGear || a.corpse != null && a.wornItems[which].container == a.corpse ? id : "";
    }

    private static void dress(Actor a, Look l) {
        String h = stillWorn(a, 0, a.wornHead), f = stillWorn(a, 1, a.wornFace), v = stillWorn(a, 2, a.wornArmor);
        if (!a.wornArmor.isEmpty() || a.dead && a.wornItems != null) l.gear.vest = vestGear(v);
        if (!a.wornHead.isEmpty() || a.dead && a.wornItems != null) l.gear.head = headGear(h);
        if (!a.wornFace.isEmpty()) l.gear.face = faceGear(f);
        if (!a.wornPack.isEmpty()) l.gear.pack = a.dead && a.wornPackItem != null && a.wornPackItem.slotOwner != a.corpseGear ? packGear("") : packGear(a.wornPack);
    }

    /** Seeded faction look for an NPC, cached on the actor. */
    public static Look lookFor(Actor a) {
        if (a.visual instanceof Look l) {
            // death closes the eyes: the same look, rebuilt once with lids (the model cache keys on it)
            boolean changed = a.dead != l.gear.eyesClosed;
            l.gear.eyesClosed = a.dead;
            // 0.7: a body that has been stripped shows it - the helmet, the mask or the armour is gone from the model the moment it leaves the corpse
            if (a.dead && a.wornItems != null && !a.infected()) {
                String h = l.gear.head, f = l.gear.face, v = l.gear.vest;
                dress(a, l);
                changed |= !h.equals(l.gear.head) || !f.equals(l.gear.face) || !v.equals(l.gear.vest);
            }
            if (changed) l.key = CharBuilder.key(l.app, l.gear);
            return l;
        }
        Look l = new Look();
        if (a.infected() && !a.turned) {
            l.gear.infected = a.archetype;
            l.scale = absentialis.neuralai.integration.Archetypes.scale(a.archetype);
        } else {
            l.gear.vest = a.armorClass >= 4 ? "plate" : a.armorClass >= 3 ? "mil" : a.armorClass > 0 ? "soft" : "";
            l.gear.head = a.helmet ? "helmet" : a.look.contains("head:helmet.cap") ? "cap" : "";
            dress(a, l);
            // turned: the same person, the same clothes and kit - green skin and nothing else (no archetype mutations)
            if (a.turned) l.gear.infected = "turned";
        }
        // the same individual looks the same every time it is brought online; squadmates wear the look the player gave them
        l.app = a.appearance != null ? a.appearance : CharBuilder.factionLook(a.infected() && !a.turned ? "infected" : a.turned && !a.turnedFrom.isEmpty() && !"player".equals(a.turnedFrom) ? a.turnedFrom : a.faction, (a.lookSeed != 0 ? a.lookSeed : a.agentId != 0 ? a.agentId : a.id) * 7919L + 13, l.gear);
        l.app.sex(a.sexKnown ? a.female : l.app.female);
        // 0.7: the three big ones are one build each - a man, bald (or bagged, or helmeted), and the Tank is in jeans
        if (a.infected() && !a.turned && ("tank".equals(a.archetype) || "gladiator".equals(a.archetype) || "butcher".equals(a.archetype))) {
            l.app.sex(false);
            l.app.set(absentialis.character.CharDefs.Category.HAIR, "bald").set(absentialis.character.CharDefs.Category.FACIAL_HAIR, 0);
            if ("tank".equals(a.archetype)) l.app.set(absentialis.character.CharDefs.Category.LEGS, "jeans");
        }
        l.gear.eyesClosed = a.dead;
        l.key = CharBuilder.key(l.app, l.gear);
        a.visual = l;
        return l;
    }

    private CharModel model(Look l) {
        CharModel cm = models.get(l.key);
        if (cm == null) {
            cm = CharBuilder.build(l.app, l.gear);
            models.put(l.key, cm);
        }
        return cm;
    }

    private Animator animator(Actor a, Look l) {
        if (a.anim == null) a.anim = new Animator();
        a.anim.scale = l.scale;
        a.anim.family = Animator.familyOf(a.archetype, a.infected());
        return a.anim;
    }

    /** Grip geometry of a held weapon: sets the animator's two-handed flag and support-hand distance. */
    private static void grips(Animator an, ItemInstance w) {
        if (w == null) { an.sidearm = false; return; }
        VoxModel wm = ItemModels.of(w);
        boolean melee = w.def().weapon != null && w.def().weapon.melee();
        an.twoHanded = w.def().weapon != null && !melee && !"sidearm".equals(w.def().slot);
        an.sidearm = w.def().weapon != null && !melee && "sidearm".equals(w.def().slot);
        an.gripSep = Math.max(0.6f, Math.min(8.5f, wm.sx * WEAPON_SCALE * 0.34f));
    }

    private void basis(Actor a, Animator an, float scale, float backOff) {
        float s = VOX * scale;
        base.identity().translate(a.x + (float) Math.sin(a.facing()) * backOff, a.y + an.visY, a.z + (float) Math.cos(a.facing()) * backOff).rotateY(a.facing()).scale(s, s, s);
    }

    // ------------------------------------------------------------------ drawing
    /** Draws one actor in the world. hideMask removes SKIN groups (first-person body hides head and arms). */
    public void draw(Actor a, Look l, float light, float aimPitch, Animator.Ground ground, int hideMask, boolean firstPerson) {
        CharModel cm = model(l);
        // 0.7: someone covered in bile is green with it, head to foot, for as long as it is on them
        int tint = ((int) (alpha * 255) << 24) | (a.hurtFlash > 0 ? 0xFF9090 : a.biled && !a.dead ? 0x8CC464 : 0xFFFFFF);
        boolean rag = a.ragdoll != null;
        Animator an = animator(a, l);
        final int asked = hideMask;
        // 0.9: played back from a scene tape, the bones are the ones the tape holds - nothing is animated, simulated or solved
        int played = replay == null ? -1 : replay.bones(a, bones, wm4, false);
        if (played == 0) return;
        if (played > 0) { }
        else if (rag) {
            a.ragdoll.bones(bones);
            // 1.1: what physics has the body doing is remembered, so that when animation takes it back the picture can ease
            // from the one to the other instead of jumping
            if (!a.dead && !firstPerson) {
                if (a.ragSnap == null) a.ragSnap = new float[GROUPS * 16];
                for (int g = 0; g < GROUPS; g++) System.arraycopy(bones[g].m, 0, a.ragSnap, g * 16, 16);
                a.ragSnapAt = now;
            }
        }
        else {
            grips(an, a.weapon);
            an.pose(a, pose, aimPitch, ground, firstPerson);
            if (a.limpMask != 0) limp(pose, a.limpMask, a);
            float back = 0;
            if (firstPerson) {
                // the body hangs from the head: shift it so the head bone sits on the capsule axis
                basis(a, an, l.scale, 0);
                fk(pose, base, hips, bones);
                // the eye is at the front of the skull, so looking down shows the legs and feet, not the inside of the chest
                bones[HEAD].transformPoint(0, 5.3f, -6.5f, v);
                float ox = v[0] - a.x, oz = v[2] - a.z;
                base.identity().translate(a.x - ox, a.y + an.visY, a.z - oz).rotateY(a.facing()).scale(VOX * l.scale, VOX * l.scale, VOX * l.scale);
            } else basis(a, an, l.scale, back);
            // 1.0, the Crawler on a wall or a ceiling: the whole body is turned about its own middle - tipped head-up on a wall,
            // rolled right over under a ceiling (its feet, and its hands, are then on the ceiling it is hanging from)
            if (a.ceilT > 0.01f || a.wallT > 0.01f) {
                float hv = a.height() / (VOX * l.scale) * 0.5f;
                base.translate(0, hv, 0).rotateZ((float) Math.PI * a.ceilT).rotateX(1.3f * a.wallT).translate(0, -hv, 0);
            }
            fk(pose, base, hips, bones);
            // 1.1, physics to animation: for a moment after a body is given back, each bone is drawn part-way between where
            // physics left it and where the animation has it (a get-up, a stagger caught, a fall onto the back that becomes
            // the wounded pose - none of them cut any more)
            float since = now - a.ragSnapAt;
            if (a.ragSnap != null && since >= 0 && since < RAG_BLEND && !firstPerson) {
                float k = since / RAG_BLEND;
                k = k * k * (3 - 2 * k);
                for (int g = 0; g < GROUPS; g++) easeBone(bones[g].m, a.ragSnap, g * 16, k);
                ragBlends++;
            }
            // 1.3: kept for the hit test - a round meets the body that is on the screen (ground under the feet, a limp, a
            // crawler on a wall, the ease out of a fall - none of which a pose worked out afresh for the hit would have)
            if (!firstPerson && !a.isPlayer) {
                if (a.drawnBones == null) a.drawnBones = new float[GROUPS * 16];
                for (int g = 0; g < GROUPS; g++) System.arraycopy(bones[g].m, 0, a.drawnBones, g * 16, 16);
                a.drawnAt = now;
            }
        }
        // the weapon in the hand: where it is, worked out once (drawn at the end; a tape is given it with the bones)
        boolean showWeapon = a.weapon != null && (hideMask & bit(R_HAND)) == 0 && !(a.seated && a.atWheel), haveWeapon = false;      // a driver's weapon is put away: the hands are on the wheel
        if (played > 0) haveWeapon = showWeapon && played == 2;
        else if (showWeapon && !rag) {
            an.aimFrame(pose, aimPitch, hips, aim);
            Animator.weaponFrame(pose, aim, wpn);
            VoxModel wv = ItemModels.of(a.weapon);
            boolean melee = a.weapon.def().weapon != null && a.weapon.def().weapon.melee();
            // weapon models point +x; the firing grip sits behind the centre, low on the model
            // 1.1: the weapon is in the hand. It is drawn where the weapon channel puts it, and the firing hand is solved onto
            // that place - but an arm has a length, a pose can be blended, and a hand can be pulled off its grip by either:
            // whatever is left between the grip and the wrist that is supposed to be on it is taken up by the weapon, which
            // goes to the hand. A weapon never hangs in the air beside a fist.
            wpn.transformPoint(0, 0, 0, v);
            float gx0 = v[0], gy0 = v[1], gz0 = v[2];
            bones[R_HAND].transformPoint(0, 0, 0, v);
            float ldx = v[0] - gx0, ldy = v[1] - gy0, ldz = v[2] - gz0, ld = (float) Math.sqrt(ldx * ldx + ldy * ldy + ldz * ldz);
            handGap = ld;
            if (ld > 0.004f && ld < 1.2f && an.stowT <= 0.5f) { wpn.m[12] += ldx; wpn.m[13] += ldy; wpn.m[14] += ldz; }
            wm4.set(wpn).rotateY((float) Math.PI / 2f).scale(WEAPON_SCALE, WEAPON_SCALE, WEAPON_SCALE).translate(melee ? wv.sx * 0.32f : wv.sx * 0.16f, -wv.sy * 0.38f, 0);
            // 1.0: put away for something that needs both hands (a climb, a ladder, an item in use): a long gun slung across the
            // back, muzzle down over the left hip; a handgun or a blade at the right hip - on the body, moving with it
            if (an.stowT > 0.5f) {
                an.backFrame(pose, hips, aim);
                // (the model lies along +x: turned in the plane of the back so that the muzzle points down past the left hip)
                if (an.twoHanded && !melee) wm4.set(aim).translate(0.4f, -5.5f, STOW_BACK).rotateZ(2.05f).scale(WEAPON_SCALE, WEAPON_SCALE, WEAPON_SCALE);   // (the models are built about their own middle)
                else wm4.set(aim).translate(4.8f, -12.5f, 0.4f).rotateZ(-1.45f).scale(WEAPON_SCALE * 0.9f, WEAPON_SCALE * 0.9f, WEAPON_SCALE * 0.9f);
            }
            haveWeapon = true;
        }
        // 1.0: the thing being used is in the hand that would hold the weapon (the spray can, a bandage, a syringe)
        boolean haveItem = false;
        if (a.handItem != null && !rag && played <= 0 && (hideMask & bit(R_HAND)) == 0) {
            absentialis.gameplay.items.ItemDef idf = absentialis.gameplay.items.Content.def(a.handItem);
            if (idf != null) {
                an.aimFrame(pose, aimPitch, hips, aim);
                Animator.weaponFrame(pose, aim, wpn);
                VoxModel iv = ItemModels.of(idf);
                float sc = 3.6f / Math.max(iv.sx, Math.max(iv.sy, iv.sz));
                // in the hand that is doing the work: the support hand, where the clip has put it (held upright, a little above the fist)
                item4.set(wpn).translate(pose.wpn[6], pose.wpn[7] + 1.4f, pose.wpn[8]).rotateX(0.5f).scale(sc, sc, sc);
                handItemModel = iv; handItemKey = "def:" + a.handItem; haveItem = true;
            }
        }
        if (capture != null && played < 0 && asked == 0 && !firstPerson) capture.bones(a, bones, haveWeapon ? wm4 : null);
        if (dry) return;
        hideMask |= a.severed;   // regions that are entirely gone (they lie in the world as their own pieces)
        absentialis.anatomy.VoxelBody vb = a.body;
        for (int g = 0; g < GROUPS; g++) {
            if ((hideMask & (1 << g)) != 0) continue;
            ModelRenderer.Mesh mesh = null;
            if (vb != null) {
                absentialis.anatomy.VoxelBody.Chunk ch = vb.chunks[g];
                if (ch.empty()) continue;
                // a region that has been hit is drawn from its own voxels: holes, exposed layers, stains, a shortened limb
                if (ch.modified) mesh = wounds.mesh("vb:" + vb.id + "#" + g, ch);
            }
            if (mesh == null) mesh = mr.meshAt(l.key + "#" + g, cm.groups[g], ORIGIN[g][0], ORIGIN[g][1], ORIGIN[g][2]);
            mr.draw(mesh, bones[g], light, tint, 0);
        }
        if (!a.dead && (hideMask & bit(HEAD)) == 0 && l.gear.infected.isEmpty()) blink(a, cm, light);
        // 0.7: what a special infected carries is part of it - on the hand bone, so it swings with the arm and falls with the body
        if ("gladiator".equals(l.gear.infected)) {
            float[] at = absentialis.character.Props.CLAWS_AT;
            for (int hand : new int[]{L_HAND, R_HAND}) if ((hideMask & bit(hand)) == 0) mr.draw(mr.meshAt("prop:claws", absentialis.character.Props.claws(), at[0], at[1], at[2]), bones[hand], light, tint, 0);
        } else if ("butcher".equals(l.gear.infected) && (hideMask & bit(R_HAND)) == 0) {
            float[] at = absentialis.character.Props.SAW_AT;
            // 0.9: the saw runs. The chain is one of four models, a link further round each time; the engine shakes the whole thing
            // in the fist by how hard it is working. A dead Butcher's saw has stalled: the chain stays where it stopped and nothing moves
            int frame = (int) a.sawPhase & (absentialis.character.Props.SAW_FRAMES - 1);
            float amp = a.dead ? 0 : 0.05f + 0.16f * a.sawRev, ph = a.sawPhase;
            sawBone.set(bones[R_HAND]).translate((float) Math.sin(ph * 2.9f) * amp, (float) Math.sin(ph * 4.3f + 1) * amp * 0.5f, (float) Math.cos(ph * 3.7f) * amp);
            mr.draw(mr.meshAt("prop:chainsaw" + frame, absentialis.character.Props.chainsaw(frame), at[0], at[1], at[2]), sawBone, light, tint, 0);
        }
        if (haveWeapon) mr.draw(mr.mesh("w:" + a.weapon.visualKey(), ItemModels.of(a.weapon), 1), wm4, light, 0xFFFFFFFF, 0);
        if (haveItem) mr.draw(mr.mesh(handItemKey, handItemModel, 1), item4, light, 0xFFFFFFFF, 0);
        // 1.0, the Crawler's tongue: a rope of segments from the mouth to the barb, sagging a little, for as long as it is out
        if (a.tongueUntil > now && !rag && played <= 0) {
            bones[HEAD].transformPoint(0, 2.5f, -6.5f, v);
            float mx = v[0], my = v[1], mz = v[2], k = Math.min(1, (0.45f - (a.tongueUntil - now)) / 0.12f) * Math.min(1, (a.tongueUntil - now) / 0.1f);
            float ex = mx + (a.tongueX - mx) * k, ey = my + (a.tongueY - my) * k, ez = mz + (a.tongueZ - mz) * k;
            float len = (float) Math.sqrt((ex - mx) * (ex - mx) + (ey - my) * (ey - my) + (ez - mz) * (ez - mz));
            int n = Math.max(2, Math.min(40, (int) (len / 0.14f)));
            VoxModel seg = tongueModel();
            for (int i = 0; i <= n; i++) {
                float t = i / (float) n, sag = (float) Math.sin(t * Math.PI) * Math.min(0.25f, len * 0.03f), s = (i == n ? 0.075f : 0.045f);
                item4.identity().translate(mx + (ex - mx) * t, my + (ey - my) * t - sag, mz + (ez - mz) * t).rotateY(i * 0.7f).scale(s, s, s);
                mr.draw(mr.mesh("fx:tongue", seg, 1), item4, light, 0xFFFFFFFF, 0);
            }
        }
    }

    /** The time the frame being drawn belongs to (seconds of the operation): set by whoever draws, for what is timed (a tongue that is out). */
    public float now;
    /** 1.1: seconds the picture takes to ease from the pose physics left a body in to the one animation gives it; how many bodies were drawn mid-ease; and how far the last weapon drawn was from the hand holding it before it was put there (metres). */
    public static float RAG_BLEND = 0.42f;
    public int ragBlends;
    public float handGap;

    /** One bone, eased from a remembered matrix toward the one it has: the place by a straight line, the three axes by a straight line put back to their own lengths. */
    private static void easeBone(float[] m, float[] from, int o, float k) {
        for (int c = 0; c < 3; c++) {
            int i = c * 4;
            float tl = (float) Math.sqrt(m[i] * m[i] + m[i + 1] * m[i + 1] + m[i + 2] * m[i + 2]);
            float x = from[o + i] + (m[i] - from[o + i]) * k, y = from[o + i + 1] + (m[i + 1] - from[o + i + 1]) * k, z = from[o + i + 2] + (m[i + 2] - from[o + i + 2]) * k;
            float l = (float) Math.sqrt(x * x + y * y + z * z);
            if (l > 1e-6f) { float s = tl / l; m[i] = x * s; m[i + 1] = y * s; m[i + 2] = z * s; }
        }
        for (int i = 12; i < 15; i++) m[i] = from[o + i] + (m[i] - from[o + i]) * k;
    }
    private VoxModel tongue;
    private VoxModel tongueModel() {
        if (tongue == null) { tongue = new VoxModel(2, 2, 2); for (int i = 0; i < 8; i++) tongue.set(i & 1, (i >> 1) & 1, (i >> 2) & 1, absentialis.engine.render.Tex.rgb(168 + (i & 1) * 20, 44, 58)); }
        return tongue;
    }

    /** 1.0: how far behind the spine a slung long gun lies (voxels, in the trunk's frame). */
    public static float STOW_BACK = 3.4f;
    private final Mat4 item4 = new Mat4();
    private VoxModel handItemModel;
    private String handItemKey;

    /** The clock eyes blink by (seconds). Set once a frame by whoever is drawing; the same for everyone, so a paused game does not blink. */
    public float clock;
    /** Seconds the lids stay down, and the shortest and longest wait between blinks (each character keeps its own rhythm). */
    public static float BLINK_TIME = 0.13f, BLINK_MIN = 2.4f, BLINK_MAX = 6.5f;
    public int blinksDrawn;

    /** True while this character's eyes are shut in a blink: a rhythm of its own, with the odd double blink. */
    public static boolean blinking(int id, float t) {
        float period = BLINK_MIN + ((id * 2654435761L >>> 8) & 1023) / 1023f * (BLINK_MAX - BLINK_MIN);
        float ph = (t + (id * 37 % 97) * 0.173f) % period;
        boolean dbl = (((int) ((t + id) / period)) * 31 + id) % 5 == 0;       // one blink in five is a double
        return ph < BLINK_TIME || dbl && ph > BLINK_TIME * 2.1f && ph < BLINK_TIME * 3.1f;
    }

    /**
     * 0.7: the living blink. The eyes are two voxels on the face; a blink lays a lid of the character's own skin over
     * each for a moment. Nothing is rebuilt - it is two thin boxes on the head bone - so a crowd can blink for free.
     * A face that is covered where the eyes are (a mask, goggles, a visor) has nothing to blink.
     */
    private void blink(Actor a, CharModel cm, float light) {
        if (!blinking(a.id, clock)) return;
        VoxModel h = cm.groups[HEAD];
        if (h.get(3, 6, 2) == 0 || h.get(3, 6, 1) != 0 || h.get(8, 6, 1) != 0) return;
        int skin = h.get(5, 5, 2);
        if (skin == 0) skin = h.get(5, 4, 2);
        if (skin == 0) return;
        int lid = Tex.shade(skin, 0.9f) | 0xFF000000;
        for (int side = 0; side < 2; side++) {
            float x0 = side == 0 ? 3 : 7;
            m.set(bones[HEAD]).translate(x0 + 1f - ORIGIN[HEAD][0], 6f - ORIGIN[HEAD][1] - 0.02f, 1.93f - ORIGIN[HEAD][2]).scale(2.06f, 1.04f, 0.16f);
            mr.cube(m, lid, light, 0);
        }
        blinksDrawn++;
    }

    /**
     * Eye position for the first-person camera: the head bone of the evaluated pose (so the view
     * rides the real gait, crouch and step-ups) kept on the capsule axis. Writes {x, y, z}.
     */
    public void eye(Actor a, Look l, float aimPitch, float[] out) {
        Animator an = animator(a, l);
        an.pose(a, pose, aimPitch, null, true);
        basis(a, an, l.scale, 0);
        fk(pose, base, hips, bones);
        bones[HEAD].transformPoint(0, 5.3f, 0, v);
        out[0] = a.x;
        out[1] = Math.min(v[1], a.y + a.height() - 0.10f);
        out[2] = a.z;
    }

    /** World position of the muzzle of the held weapon for the last drawn/evaluated pose of this actor. */
    public void muzzle(Actor a, Look l, float aimPitch, float[] out) {
        Animator an = animator(a, l);
        grips(an, a.weapon);
        an.pose(a, pose, aimPitch, null, false);
        basis(a, an, l.scale, 0);
        fk(pose, base, hips, bones);
        an.aimFrame(pose, aimPitch, hips, aim);
        Animator.weaponFrame(pose, aim, wpn);
        float len = a.weapon == null ? 6 : ItemModels.of(a.weapon).sx * WEAPON_SCALE * 0.84f;
        wpn.transformPoint(0, 1, -len, out);
    }

    /** World position of the right hand of whoever was drawn last (the palm, a little below the wrist). */
    public void rightHand(float[] out) { bones[R_HAND].transformPoint(0, -1.4f, 0, out); }

    /** A severed piece lying in the world: the rig groups in 'mask', kept in their relative pose, as one rigid body. */
    public void drawGib(Look l, int mask, float x, float y, float z, float yaw, float pitch, float light) {
        CharModel cm = model(l);
        float s = VOX * l.scale;
        float oy = 0;
        boolean first = true;
        for (int g = 0; g < GROUPS; g++) {
            if ((mask & (1 << g)) == 0) continue;
            // chain the groups of a limb end to end along -y (an arm is upper arm, forearm, hand)
            m.identity().translate(x, y, z).rotateY(yaw).rotateX(pitch).scale(s, s, s).translate(0, oy, 0);
            mr.draw(mr.meshAt(l.key + "#" + g, cm.groups[g], ORIGIN[g][0], ORIGIN[g][1], ORIGIN[g][2]), m, light, 0xFFFFFFFF, 0);
            if (first) { m.translate(0, g == HEAD ? 0 : 0.5f, 0).scale(3, 1.4f, 3).translate(0, -0.5f, 0); mr.cube(m, 0xFF5A0C0A, light * 0.8f, 0); first = false; }
            oy -= absentialis.character.Rig.LENGTH[g];
        }
    }

    /** One arm part of a look, for the first-person viewmodel (same meshes as the body). */
    public void drawPart(Look l, int group, Mat4 model, float light) {
        CharModel cm = model(l);
        mr.draw(mr.meshAt(l.key + "#" + group, cm.groups[group], ORIGIN[group][0], ORIGIN[group][1], ORIGIN[group][2]), model, light, 0xFFFFFFFF, 0);
    }

    /** Voxel preview of a look for menus: front view, slowly turning. */
    public VoxModel flatten(Look l) {
        CharModel cm = model(l);
        VoxModel out = new VoxModel(26, 36, 14);
        int[][] off = new int[GROUPS][];
        // rest pose placement of each group's grid inside one model
        float[][] piv = new float[GROUPS][3];
        for (int g : ORDER) {
            float[] par = PARENT[g] < 0 ? new float[]{0, 0, 0} : piv[PARENT[g]];
            piv[g][0] = par[0] + PIVOT[g][0]; piv[g][1] = par[1] + PIVOT[g][1]; piv[g][2] = par[2] + PIVOT[g][2];
            off[g] = new int[]{Math.round(13 + piv[g][0] - ORIGIN[g][0]), Math.round(piv[g][1] - ORIGIN[g][1]), Math.round(7 + piv[g][2] - ORIGIN[g][2])};
        }
        for (int g = 0; g < GROUPS; g++) out.paste(cm.groups[g], off[g][0], off[g][1], off[g][2]);
        return out;
    }

    /** World light at a point for model shading (sky * daylight vs block light). */
    public static float lightAt(World w, float x, float y, float z, float daylight) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y + 1), iz = (int) Math.floor(z);
        float s = (float) Math.pow(w.sky(ix, iy, iz) / 15f, 1.5) * daylight, b = (float) Math.pow(w.blockLight(ix, iy, iz) / 15f, 1.35);
        return Math.max(0.08f, Math.max(s * 0.95f + 0.06f, b * 1.1f));
    }

    /** Unused tint helper kept for callers that colour-code debug overlays. */
    public static int factionColor(String f) {
        return switch (f) { case "military" -> Tex.rgb(96, 130, 70); case "raiders" -> Tex.rgb(190, 70, 50); case "survivors" -> Tex.rgb(70, 120, 190); case "mercenaries" -> Tex.rgb(150, 150, 160); default -> Tex.rgb(170, 190, 90); };
    }
}
