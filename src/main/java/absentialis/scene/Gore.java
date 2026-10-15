package absentialis.scene;

import absentialis.anatomy.AnatomicalState;
import absentialis.anatomy.BloodEffectSystem;
import absentialis.anatomy.BodyMaterial;
import absentialis.anatomy.DamageProcessor;
import absentialis.anatomy.DismembermentSystem;
import absentialis.anatomy.StructuralIntegritySystem;
import absentialis.anatomy.VoxelBody;
import absentialis.character.CharBuilder;
import absentialis.character.CharDefs;
import absentialis.character.CharModel;
import absentialis.character.Rig;
import absentialis.engine.math.Mat4;
import absentialis.engine.render.Tex;
import absentialis.entity.Actor;
import absentialis.world.Block;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Physical damage to bodies, as the raid experiences it (0.4 damage overhaul).
 *
 * This class is the meeting point of the anatomy systems, each of which does one job and knows nothing of the
 * others:
 * <pre>
 *   VoxelBody ................ a character's layered voxel anatomy, bound to the skeleton's regions
 *   DamageProcessor .......... what an impact does to the voxels where it lands
 *   StructuralIntegritySystem  whether what is left is still attached
 *   DismembermentSystem ...... taking what is not attached away as an independent physical piece
 *   WoundMeshGenerator ....... drawing regions from their voxels as they now are (scene.ActorRenderer owns it)
 *   BloodEffectSystem ........ blood that comes from the wound: sprays, drips, arterial pulses
 *   RagdollController ........ how a body answers a blow: flinch, stagger, limp limbs, knock-down
 *   AnatomicalState .......... what the body can still do
 *   AIInjuryAdapter .......... telling the AI, in its own terms
 * </pre>
 * A hit arrives here as a ray and an energy. It is traced against the victim's actual voxels in their current
 * pose - a round between the arm and the ribs is a miss - and everything that follows (the wound, the blood,
 * whether the limb stays on, how the body reacts, what the victim can still do) is derived from where that ray
 * went. Blood in the environment is simulated too: droplets fall, stain walls, pool on floors, creep, run over
 * edges, dry, and are slippery while wet.
 */
public final class Gore implements BloodEffectSystem.Sink, DismembermentSystem.Listener {
    public static final int HEAD = 0, TORSO = 1, ARM_L = 2, ARM_R = 3, LEG_L = 4, LEG_R = 5;
    public static final String[] REGION = {"head", "torso", "left arm", "right arm", "left leg", "right leg"};
    public static final int ARM_L_MASK = Rig.bit(Rig.L_UPPER_ARM) | Rig.bit(Rig.L_FOREARM) | Rig.bit(Rig.L_HAND), ARM_R_MASK = Rig.bit(Rig.R_UPPER_ARM) | Rig.bit(Rig.R_FOREARM) | Rig.bit(Rig.R_HAND);
    public static final int LEG_L_MASK = Rig.bit(Rig.L_THIGH) | Rig.bit(Rig.L_SHIN), LEG_R_MASK = Rig.bit(Rig.R_THIGH) | Rig.bit(Rig.R_SHIN), HEAD_MASK = Rig.bit(Rig.HEAD);
    /** Root rig group of each coarse part (the joint a scripted sever cuts). */
    private static final int[] ROOT = {Rig.HEAD, Rig.TORSO, Rig.L_UPPER_ARM, Rig.R_UPPER_ARM, Rig.L_THIGH, Rig.R_THIGH};
    public static final int HUMAN_BLOOD = 0xFF480909, INFECTED_BLOOD = 0xFF36421A;
    /** 0.7: infected bile - dark green. It flies, lands, pools and stains exactly as blood does (it is the same droplets with another colour). */
    public static final int BILE = 0xFF1F4A12;
    public int bileDrops;

    /** A pool of blood on a floor: eight radii that creep outward until something stops them. */
    public static final class Pool {
        public int id; public float x, y, z, volume, wet = 1, born; public final float[] r = new float[8]; public int color; public boolean infected, dripped;
        public float radius() { float s = 0; for (float v : r) s += v; return s / 8; }
    }
    /** Blood thrown onto a wall. */
    public static final class Stain { public float x, y, z, size, born; public int axis, color; }
    /** A fragment knocked out of a body: flesh, bone chips, cloth. Simplified physics, short life. */
    public static final class Debris { public float x, y, z, vx, vy, vz, life, size, spin; public int color; public boolean rest; }

    /** Where a ray met a body. */
    public static final class Entry {
        public int group;
        /** Entry point and unit direction in the region's voxel coordinates; distance along the ray in metres. */
        public float lx, ly, lz, ldx, ldy, ldz, t;
        /** The ray itself missed and only a neighbouring line touched: a graze. */
        public boolean graze;
    }

    /** What a hit amounted to, for the health model. */
    public static final class Impact {
        public int group = Rig.TORSO;
        public String region = "torso";
        /** Multiplies the hit's health damage: more through the organs, less for a graze. */
        public float vital = 1;
        public boolean severed, fractured, exit;
        public int destroyed;
    }

    public final List<Pool> pools = new ArrayList<>();
    public final List<Stain> stains = new ArrayList<>();
    public final List<Debris> debris = new ArrayList<>();
    /** Airborne droplets: {x, y, z, vx, vy, vz, volume, infected}. */
    public final List<float[]> drops = new ArrayList<>();
    public final BloodEffectSystem blood = new BloodEffectSystem();
    public final DismembermentSystem dismember = new DismembermentSystem();
    public final RagdollController ragdolls;
    private final Raid raid;
    private final Map<String, CharModel> models = new HashMap<>();
    private final Map<Integer, Actor> pieceOwner = new HashMap<>();
    private final DamageProcessor.Hit hit = new DamageProcessor.Hit();
    private final DamageProcessor.Result res = new DamageProcessor.Result();
    private final Entry entry = new Entry();
    private final Impact impact = new Impact();
    private final Mat4 inv = new Mat4();
    private final float[] v3 = new float[3], w6 = new float[6];
    private final int[] gone = new int[1];
    private int nextPool = 1;
    public int severedTotal, slips, hitsTraced, hitsMissed, bodies;
    private static final float[][] DIR8 = {{1, 0}, {0.707f, 0.707f}, {0, 1}, {-0.707f, 0.707f}, {-1, 0}, {-0.707f, -0.707f}, {0, -1}, {0.707f, -0.707f}};
    /** Persistence budgets (the gore detail setting scales them). */
    public int maxPools = 140, maxStains = 180, maxDrops = 500, maxDebris = 200;

    Gore(Raid raid) { this.raid = raid; ragdolls = new RagdollController(raid); }

    /** Applies the gore detail setting (0 Low, 1 Standard, 2 High): how much blood and debris persists. */
    public void detail(int level) {
        level = Math.max(0, Math.min(2, level));
        maxPools = new int[]{70, 140, 240}[level]; maxStains = new int[]{80, 180, 300}[level]; maxDrops = new int[]{220, 500, 900}[level];
        maxDebris = new int[]{60, 200, 380}[level]; dismember.maxPieces = new int[]{24, 60, 110}[level];
        blood.density = new float[]{0.5f, 1f, 1.5f}[level]; blood.maxEmitters = new int[]{32, 72, 120}[level];
    }

    // ------------------------------------------------------------------ anatomy
    /** The character's anatomy, built from the model of their look the first time it is needed. */
    /** Voxels eaten off corpses, and corpses eaten to nothing (0.6). */
    public int eatenVoxels, corpsesConsumed;

    /**
     * The infected eat the dead. A share of the corpse is taken off it - flesh first, from the outside in, torn at
     * random so no two are stripped the same way - and what is left around each bite is stained. The body is the same
     * voxel anatomy a bullet damages, so what has been eaten is simply not there any more: it thins, shows bone, comes
     * apart and is gone. Returns true once nothing worth eating is left.
     */
    public boolean eat(Actor corpse, float share) {
        VoxelBody b = body(corpse);
        int total = 0;
        for (VoxelBody.Chunk c : b.chunks) if (c != null) total += c.solid0;
        if (total == 0) return true;
        corpse.eaten = Math.min(1, corpse.eaten + share);
        int target = Math.round(total * (1 - corpse.eaten * 0.96f)), left = 0;
        for (VoxelBody.Chunk c : b.chunks) if (c != null) left += c.solid;
        int tries = 0;
        while (left > target && tries++ < 4000) {
            VoxelBody.Chunk c = b.chunks[raid.rng.nextInt(b.chunks.length)];
            if (c == null || c.solid == 0) continue;
            int x = raid.rng.nextInt(c.sx), y = raid.rng.nextInt(c.sy), z = raid.rng.nextInt(c.sz);
            byte m = c.at(x, y, z);
            if (m == absentialis.anatomy.BodyMaterial.EMPTY || !c.exposed(x, y, z)) continue;
            if (m == absentialis.anatomy.BodyMaterial.BONE && corpse.eaten < 0.75f && raid.rng.chance(0.85)) continue;     // bone is left till last
            c.remove(x, y, z);
            left--; eatenVoxels++;
            for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) if (c.at(x + d[0], y + d[1], z + d[2]) != absentialis.anatomy.BodyMaterial.EMPTY && c.at(x + d[0], y + d[1], z + d[2]) != absentialis.anatomy.BodyMaterial.BONE && raid.rng.chance(0.5)) c.recolor(x + d[0], y + d[1], z + d[2], Tex.rgb(96 + raid.rng.nextInt(40), 14, 16));
        }
        if (corpse.anat != null) corpse.anat.evaluate(b);
        return corpse.eaten >= 1f || left <= total * 0.05f;
    }

    public VoxelBody body(Actor a) {
        if (a.body != null) return a.body;
        ActorRenderer.Look l = a.isPlayer ? ActorRenderer.playerLook(raid.profile, null) : ActorRenderer.lookFor(a);
        CharModel cm = models.get(l.key);
        if (cm == null) {
            if (models.size() > 300) models.clear();
            cm = CharBuilder.build(l.app, l.gear);
            models.put(l.key, cm);
        }
        boolean inf = a.infected();
        int skin = CharDefs.SKIN_TONES[l.app.get(CharDefs.Category.SKIN)];
        if (!l.gear.infected.isEmpty()) skin = Tex.mix(skin, Tex.rgb(146, 158, 118), 0.62f);
        a.body = VoxelBody.build(cm, skin, inf, !l.gear.vest.isEmpty(), "helmet".equals(l.gear.head), a.id * 7919L + 31);
        a.anat = new AnatomicalState();
        a.anat.evaluate(a.body);
        bodies++;
        return a.body;
    }

    /**
     * Traces a ray against a character's voxels in the pose they are in right now. Returns where it went in, or
     * null when it passed between limbs or beside the body. The returned object is reused: read it at once.
     */
    public Entry trace(Actor v, float ox, float oy, float oz, float dx, float dy, float dz, float len) { return trace(v, ox, oy, oz, dx, dy, dz, len, 0); }

    /**
     * As above, with the round already {@code travelled} blocks from the muzzle. 1.3: the graze allowance widens a little
     * with range (up to two voxels more at 100 blocks): a body far off is a few pixels across, a sight laid on it is laid on
     * those pixels, and a round that the eye sees go through the outline is not let pass between voxels of it.
     */
    public Entry trace(Actor v, float ox, float oy, float oz, float dx, float dy, float dz, float len, float travelled) {
        VoxelBody b = body(v);
        Mat4[] bones = raid.bones(v);
        hitsTraced++;
        Entry e = entry;
        e.t = Float.MAX_VALUE;
        e.graze = false;
        if (cast(b, bones, ox, oy, oz, dx, dy, dz, len, e)) return e;
        // a near miss still clips the body if a line beside it would have struck: a graze
        float px = -dz, pz = dx, pl = (float) Math.sqrt(px * px + pz * pz);
        if (pl > 1e-4f) { px /= pl; pz /= pl; } else { px = 1; pz = 0; }
        float off = Rig.VOX * (0.9f + Math.min(2f, Math.max(0, travelled - 20) / 40f));
        float[][] offs = {{px * off, 0, pz * off}, {-px * off, 0, -pz * off}, {0, off, 0}, {0, -off, 0}};
        for (float[] o : offs) if (cast(b, bones, ox + o[0], oy + o[1], oz + o[2], dx, dy, dz, len, e)) { e.graze = true; return e; }
        hitsMissed++;
        return null;
    }

    private boolean cast(VoxelBody b, Mat4[] bones, float ox, float oy, float oz, float dx, float dy, float dz, float len, Entry e) {
        boolean any = false;
        for (int g = 0; g < Rig.GROUPS; g++) {
            VoxelBody.Chunk c = b.chunks[g];
            if (c == null || c.empty()) continue;
            inv.invert(bones[g]);
            inv.transformPoint(ox, oy, oz, v3);
            float lx = v3[0] + Rig.ORIGIN[g][0], ly = v3[1] + Rig.ORIGIN[g][1], lz = v3[2] + Rig.ORIGIN[g][2];
            inv.transformDir(dx, dy, dz, v3);
            float sl = (float) Math.sqrt(v3[0] * v3[0] + v3[1] * v3[1] + v3[2] * v3[2]);
            if (sl < 1e-6f) continue;
            float ldx = v3[0] / sl, ldy = v3[1] / sl, ldz = v3[2] / sl;   // sl = voxels per metre along the ray
            // clip to the region's grid
            float t0 = 0, t1 = len * sl;
            float[] o = {lx, ly, lz}, d = {ldx, ldy, ldz};
            int[] hi = {c.sx, c.sy, c.sz};
            boolean miss = false;
            for (int a = 0; a < 3 && !miss; a++) {
                if (Math.abs(d[a]) < 1e-6f) { if (o[a] < 0 || o[a] > hi[a]) miss = true; continue; }
                float ta = (0 - o[a]) / d[a], tb = (hi[a] - o[a]) / d[a];
                if (ta > tb) { float t = ta; ta = tb; tb = t; }
                t0 = Math.max(t0, ta); t1 = Math.min(t1, tb);
                if (t0 > t1) miss = true;
            }
            if (miss) continue;
            for (float t = t0 + 0.05f; t <= t1; t += 0.2f) {
                int x = (int) Math.floor(lx + ldx * t), y = (int) Math.floor(ly + ldy * t), z = (int) Math.floor(lz + ldz * t);
                if (c.at(x, y, z) == BodyMaterial.EMPTY) continue;
                float tw = t / sl;
                if (tw < e.t) { e.t = tw; e.group = g; e.lx = lx + ldx * t; e.ly = ly + ldy * t; e.lz = lz + ldz * t; e.ldx = ldx; e.ldy = ldy; e.ldz = ldz; any = true; }
                break;
            }
        }
        return any;
    }

    /**
     * Applies an impact that entered at e. type is a DamageProcessor type; dmg the energy in damage points; power
     * the penetrating power; (dx, dy, dz) the world direction of travel. The returned summary is reused.
     */
    public Impact impact(Actor v, Actor attacker, Entry e, int type, float dmg, float power, float dx, float dy, float dz) {
        VoxelBody b = body(v);
        Mat4[] bones = raid.bones(v);
        Impact im = impact;
        int g = e.group;
        im.group = g; im.region = DamageProcessor.regionName(g); im.vital = e.graze ? 0.55f : 1; im.severed = im.fractured = im.exit = false; im.destroyed = 0;
        hit.type = type;
        hit.px = e.lx; hit.py = e.ly; hit.pz = e.lz; hit.dx = e.ldx; hit.dy = e.ldy; hit.dz = e.ldz;
        hit.energy = dmg * (e.graze ? 0.5f : 1f); hit.power = power; hit.seed = raid.rng.nextInt(1 << 30);
        boolean wasFractured = b.chunks[g].fractured;
        DamageProcessor.apply(b, g, hit, res);
        im.destroyed = res.destroyed;
        im.exit = res.exit;
        boolean infected = b.infected;
        // organs and the brain are what kill: a wound through them counts for more than the same wound through muscle
        if (res.organ > 0) im.vital *= 1f + Math.min(type == DamageProcessor.BULLET ? 0.7f : 0.2f, res.organ * (g == Rig.HEAD ? 0.14f : 0.07f));
        // ---- blood at the wound, thrown the way the blow went
        bones[g].transformPoint(e.lx - Rig.ORIGIN[g][0], e.ly - Rig.ORIGIN[g][1], e.lz - Rig.ORIGIN[g][2], v3);
        float wx = v3[0], wy = v3[1], wz = v3[2];
        // 1.3: the mist - by the region struck and how badly (see ParticleSystem.bloodMist)
        mist(v, g, type, dmg, res.destroyed, wx, wy, wz, dx, dy, dz, false, infected);
        if (res.exit && type == DamageProcessor.BULLET) {
            bones[g].transformPoint(res.ex - Rig.ORIGIN[g][0], res.ey - Rig.ORIGIN[g][1], res.ez - Rig.ORIGIN[g][2], v4);
            mist(v, g, type, dmg, res.destroyed, v4[0], v4[1], v4[2], dx, dy, dz, true, infected);
        }
        // 1.3: a beating draws blood into the air, not onto the floor: no drops, no pools, no wound left bleeding
        if (mistOnly) { refresh(v, g, res.destroyed, attacker); ragdolls.react(v, dx, dz, dmg, g, type, im); return im; }
        if (res.destroyed > 0 || type == DamageProcessor.BLADE || type == DamageProcessor.BITE) {
            float vol = Math.min(0.5f, 0.02f + res.blood * 0.012f);
            if (type == DamageProcessor.BULLET) {
                blood.spray(this, raid.rng, wx, wy, wz, -dx, -dy, -dz, 2.2f, 0.9f, vol * 0.35f, 2 + (int) (dmg / 30), infected);      // back-splash from the entry
                if (res.exit) {
                    bones[g].transformPoint(res.ex - Rig.ORIGIN[g][0], res.ey - Rig.ORIGIN[g][1], res.ez - Rig.ORIGIN[g][2], v3);
                    blood.spray(this, raid.rng, v3[0], v3[1], v3[2], dx, dy, dz, 3.2f + dmg * 0.045f, 0.55f, vol * 1.5f, 4 + (int) (dmg / 9), infected);   // the exit wound throws far more
                    splatter(v, v3[0], v3[1], v3[2], dx, dz);
                }
            } else blood.spray(this, raid.rng, wx, wy, wz, -dx * 0.3f, 0.4f, -dz * 0.3f, 2.4f, 1.2f, vol, 3 + (int) (dmg / 12), infected);
            // and the wound keeps bleeding from where it is, on the region it is on
            float rate = Math.min(2.2f, res.blood * 0.022f + (type == DamageProcessor.BITE ? 0.2f : 0));
            if (rate > 0.03f) blood.bleed(v.id, 0, g, e.lx - Rig.ORIGIN[g][0], e.ly - Rig.ORIGIN[g][1], e.lz - Rig.ORIGIN[g][2], -e.ldx, -e.ldy, -e.ldz, rate, 14 + rate * 18, false, infected);
            fragments(wx, wy, wz, dx, dy, dz, res, infected, type);
        }
        if (attacker != null && (type == DamageProcessor.BLADE || type == DamageProcessor.BLUNT) && res.destroyed > 0 && attacker.dist(v) < 2.6f) stainActor(attacker, -dx, -dz, 5);
        // ---- is everything still attached?
        boolean giant = "tank".equals(v.archetype);
        boolean allowSever = !(v.isPlayer && !v.dead) && !(giant && type != DamageProcessor.BLAST);
        StructuralIntegritySystem.Break br = StructuralIntegritySystem.evaluate(b, g, allowSever);
        if (br != null) { separate(v, b, br, bones, dx, dy, dz, 1.5f + Math.min(7f, dmg * 0.05f)); im.severed = !br.debris; }
        im.fractured = b.chunks[g].fractured && !wasFractured;
        if (im.fractured) raid.emitSound(wx, wy, wz, 9, "bone", v.id, false);
        // ---- what can the body still do, and how does it take the blow?
        refresh(v, g, res.destroyed + (im.severed ? 40 : 0) + (im.fractured ? 12 : 0), attacker);
        ragdolls.react(v, dx, dz, dmg, g, type, im);
        return im;
    }

    /** 1.3: blows struck while this is set leave mist only (fists: see RaidScreen.punchContact). */
    public boolean mistOnly;
    private final float[] v4 = new float[3];
    public int mists;

    /**
     * 1.3: blood mist from one wound. The head throws the most and the finest (a wide pink cloud from a round through it),
     * the trunk a good deal, an arm or a leg a little; a round that comes out throws far more, and forward, than where it
     * went in throws back; a blunt blow only bursts what is under the skin - a puff, and only once it has broken something
     * (or, for a fist to the face, the nose and the mouth). Severity is the energy against what the region can take.
     */
    private void mist(Actor v, int g, int type, float dmg, int destroyed, float x, float y, float z, float dx, float dy, float dz, boolean exit, boolean infected) {
        if (raid.fx == null) return;
        boolean head = g == Rig.HEAD, trunk = g == Rig.TORSO;
        float spread = head ? 1.25f : trunk ? 1f : 0.55f;
        float sev;
        if (type == DamageProcessor.BULLET) sev = Math.min(1f, dmg / (head ? 45f : trunk ? 70f : 90f) + destroyed * 0.01f);
        else if (type == DamageProcessor.BLUNT) { if (destroyed <= 0 && !(head && mistOnly)) return; sev = Math.min(0.45f, dmg / 80f + 0.08f); spread *= 0.55f; }
        else sev = Math.min(0.6f, dmg / 70f + 0.05f);
        // back-splash from an entry goes against the round; an exit goes with it
        float s = exit ? 1 : -0.6f;
        raid.fx.bloodMist(x, y, z, dx * s, dy * s + 0.05f, dz * s, sev, spread, exit, infected ? 0x3A3418 : head ? 0x9A1A16 : 0x7E1410);
        mists++;
    }

    /** A blast reaches every region that faces it. */
    public void blast(Actor v, float bx, float by, float bz, float dmg) {
        VoxelBody b = body(v);
        Mat4[] bones = raid.bones(v);
        float dx = v.x - bx, dy = v.y + 1 - by, dz = v.z - bz, dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl < 1e-3f) { dx = 0; dy = 1; dz = 0; dl = 1; }
        dx /= dl; dy /= dl; dz /= dl;
        int worst = Rig.TORSO, lost = 0;
        boolean allowSever = !(v.isPlayer && !v.dead);
        for (int g = 0; g < Rig.GROUPS; g++) {
            VoxelBody.Chunk c = b.chunks[g];
            if (c == null || c.empty()) continue;
            inv.invert(bones[g]);
            inv.transformDir(dx, dy, dz, v3);
            float sl = (float) Math.sqrt(v3[0] * v3[0] + v3[1] * v3[1] + v3[2] * v3[2]);
            hit.type = DamageProcessor.BLAST;
            hit.dx = v3[0] / sl; hit.dy = v3[1] / sl; hit.dz = v3[2] / sl;
            hit.px = c.sx / 2f; hit.py = c.sy / 2f; hit.pz = c.sz / 2f;
            // limbs are thin and exposed: they take more of it than the trunk does
            hit.energy = dmg * (VoxelBody.limb(g) ? 0.75f + raid.rng.nextFloat() * 0.6f : 0.6f); hit.power = 1; hit.seed = raid.rng.nextInt(1 << 30);
            DamageProcessor.apply(b, g, hit, res);
            if (res.destroyed > lost) { lost = res.destroyed; worst = g; }
            if (res.destroyed > 0) {
                bones[g].transformPoint(0, -Rig.LENGTH[g] * 0.5f, 0, v3);
                blood.spray(this, raid.rng, v3[0], v3[1], v3[2], dx, 0.5f, dz, 3.5f, 1.1f, Math.min(0.4f, res.blood * 0.01f), 2 + res.destroyed / 6, b.infected);
                fragments(v3[0], v3[1], v3[2], dx, dy, dz, res, b.infected, DamageProcessor.BLAST);
            }
            StructuralIntegritySystem.Break br = StructuralIntegritySystem.evaluate(b, g, allowSever);
            if (br != null) separate(v, b, br, bones, dx, dy + 0.4f, dz, 3.5f + Math.min(8f, dmg * 0.05f));
        }
        refresh(v, worst, lost, null);
    }

    /** Fire: the skin chars a little more each time it is burned. */
    public void burn(Actor v, float dmg) {
        VoxelBody b = body(v);
        for (int k = 0; k < 3; k++) {
            int g = raid.rng.nextInt(Rig.GROUPS);
            if (b.chunks[g] == null || b.chunks[g].empty()) continue;
            hit.type = DamageProcessor.BURN; hit.energy = dmg * 2.5f; hit.seed = raid.rng.nextInt(1 << 30);
            DamageProcessor.apply(b, g, hit, res);
        }
    }

    /** Recomputes what the body can do and passes it on to the AI and the animation. */
    private void refresh(Actor v, int group, int lost, Actor attacker) {
        if (v.anat == null) v.anat = new AnatomicalState();
        float before = v.anat.severity;
        boolean couldWalk = v.anat.canWalk, couldHold = v.anat.canHoldWeapon;
        v.anat.evaluate(v.body);
        AIInjuryAdapter.apply(raid, v, v.anat, group, lost, before, couldWalk, couldHold, attacker);
    }

    /** Takes a broken-off part away from the body and puts it in the world. */
    private void separate(Actor v, VoxelBody b, StructuralIntegritySystem.Break br, Mat4[] bones, float dx, float dy, float dz, float force) {
        boolean infected = b.infected;
        DismembermentSystem.Piece p = dismember.detach(b, br, bones, v.vx, v.vy, v.vz, dx * force + (raid.rng.nextFloat() - 0.5f) * 1.5f, 1.6f + raid.rng.nextFloat() * 2.2f + dy * force * 0.3f, dz * force + (raid.rng.nextFloat() - 0.5f) * 1.5f, 10 + force * 2, gone);
        v.severed |= gone[0];
        if (p == null) return;
        severedTotal++;
        p.ownerId = v.id;
        p.look = v.isPlayer ? ActorRenderer.playerLook(raid.profile, null) : ActorRenderer.lookFor(v);
        pieceOwner.put(p.id, v);
        float[] c = p.centre();
        raid.emitSound(c[0], c[1], c[2], 14, "gore", v.id, false);
        // the stump pumps: arterial bleeding from where the limb parted, on the body and on the piece
        int g = br.group;
        float sy = VoxelBody.limb(g) ? br.row + 0.5f - Rig.ORIGIN[g][1] : br.row + 0.5f - Rig.ORIGIN[g][1];
        if (!br.whole) blood.bleed(v.id, 0, g, 0, sy, 0, 0, VoxelBody.limb(g) ? -1 : 1, 0, 2.4f, 26, true, infected);
        else if (Rig.PARENT[g] >= 0 || g == Rig.L_THIGH || g == Rig.R_THIGH) {
            int par = Rig.PARENT[g] < 0 ? Rig.TORSO : Rig.PARENT[g];
            float[] pv = Rig.PIVOT[g];
            float py = Rig.PARENT[g] < 0 ? 0.5f : pv[1];
            blood.bleed(v.id, 0, par, pv[0], py, pv[2], Math.signum(pv[0]), g == Rig.HEAD ? 1 : -0.3f, 0, 2.6f, 30, true, infected);
        }
        blood.bleed(v.id, p.id, g, p.cx, p.cy, p.cz, 0, 1, 0, 1.2f, 9, false, infected);
        blood.spray(this, raid.rng, c[0], c[1], c[2], dx, 0.6f, dz, 3.5f, 1.4f, 0.5f, 14, infected);
        v.bleed += g == Rig.HEAD ? 0 : 5f;
    }

    /**
     * Scripted separation at a joint (tests, console, the old API): cuts the joint of a coarse part and lets the
     * structure do the rest, exactly as if damage had destroyed it.
     */
    public void sever(Actor v, int part, float dx, float dz, float force) {
        if (part == TORSO || part < 0 || part > LEG_R) return;
        VoxelBody b = body(v);
        int g = ROOT[part];
        if (b.chunks[g] == null || b.chunks[g].empty()) return;
        Mat4[] bones = raid.bones(v);
        StructuralIntegritySystem.cutJoint(b, g);
        StructuralIntegritySystem.Break br = StructuralIntegritySystem.evaluate(b, g, true);
        if (br != null) separate(v, b, br, bones, dx, 0, dz, force);
        refresh(v, g, 60, null);
    }

    /**
     * The fatal blow's dismemberment (brief 6.4). While a character lives its anatomy decides what comes away;
     * at death the blow that killed it is judged once more for the parts that only a death allows to part: a
     * heavy round or a blast to the head takes the head, a blast that kills can take a limb. The rest of the body
     * stays whole and goes to the ragdoll; what separates becomes a piece with its own momentum and collision.
     */
    /** A pellet of the killing charge reaches the head just after death: judged as the death blow would have been. */
    public void lateHeadPellet(Actor v, String weaponId, float range, float dx, float dz) {
        if (lost(v, HEAD) || v.body == null && v.tier != 0) return;
        absentialis.gameplay.items.ItemDef wd = absentialis.gameplay.items.Content.def(weaponId);
        String family = wd == null || wd.weapon == null ? "" : wd.weapon.family;
        if (!raid.rng.chance(raid.combat.decap(family).chance(v.hitForce, range))) return;
        sever(v, HEAD, dx, dz, 2f + 140 / 45f);
        decapitations++;
    }

    /** Heads taken by a killing blow this raid (gates). */
    public int decapitations;

    public void deathBlow(Actor v, String kind) {
        if (v.body == null && v.tier != 0) return;
        boolean blast = "blast".equals(kind);
        float force = v.hitForce;
        // 0.6: what takes a head off depends on what fired. A shotgun at close range always does; a heavy rifle or magnum
        // round may; a handgun never does (content/combat.json "decapitation").
        absentialis.gameplay.items.ItemDef wd = absentialis.gameplay.items.Content.def(v.lastHitWeapon);
        String family = wd == null || wd.weapon == null ? "" : wd.weapon.family;
        boolean shotgun = "shotgun".equals(family);
        // a charge of shot is one blow: if any of it struck the head in the moment of death, the head took it
        boolean headHit = "head".equals(v.hitRegion) || shotgun && raid.time - v.headPelletAt < 0.15f;
        if (headHit && !lost(v, HEAD) && (blast || "bullet".equals(kind) && raid.rng.chance(raid.combat.decap(family).chance(force, v.lastHitRange)))) { sever(v, HEAD, v.hitDirX, v.hitDirZ, 2f + Math.max(force, shotgun ? 140 : 0) / 45f); decapitations++; }
        if (blast && force >= 55) {
            int n = force >= 110 ? 2 : 1;
            for (int i = 0; i < n; i++) { int part = ARM_L + raid.rng.nextInt(4); if (!lost(v, part) && raid.rng.chance(0.6f)) sever(v, part, v.hitDirX, v.hitDirZ, 2.5f + force / 40f); }
        }
    }

    /** Which coarse parts of a character are gone (any rig group of the part missing). */
    public static boolean lost(Actor v, int part) {
        int mask = switch (part) { case HEAD -> HEAD_MASK; case ARM_L -> ARM_L_MASK; case ARM_R -> ARM_R_MASK; case LEG_L -> LEG_L_MASK; case LEG_R -> LEG_R_MASK; default -> 0; };
        return (v.severed & mask) != 0;
    }

    /** What death adds: the pool the body will lie in. Wounds, missing limbs and stains are already on the body and stay. */
    public void onDeath(Actor v, float dmg, String kind) {
        v.bleed = Math.max(v.bleed, 2.5f);
        v.poolLeft = 0.5f + Math.min(1.6f, dmg * 0.012f) + Integer.bitCount(v.severed) * 0.12f;
        raid.emitSound(v.x, v.y + 0.5f, v.z, 9, "body_fall", v.id, false);
    }

    /** Blood from an exit wound lands on whoever is standing close behind the victim. */
    private void splatter(Actor from, float x, float y, float z, float dx, float dz) {
        for (Actor o : raid.nearby(x, z, 2.4f)) {
            if (o == from || o.dead) continue;
            float ox = o.x - x, oz = o.z - z, d = (float) Math.sqrt(ox * ox + oz * oz);
            if (d > 2.4f || d > 0.2f && (ox * dx + oz * dz) / d < 0.45f || Math.abs(o.y + 1 - y) > 1.4f) continue;
            stainActor(o, -dx, -dz, 7);
        }
    }

    /** Blood transferred onto a character: some of the surface facing where it came from takes the colour. */
    public void stainActor(Actor o, float fromX, float fromZ, int amount) {
        if (o.tier != 0) return;
        VoxelBody b = body(o);
        int col = HUMAN_BLOOD;
        int[] pick = {Rig.TORSO, Rig.TORSO, Rig.R_FOREARM, Rig.L_FOREARM, Rig.R_UPPER_ARM, Rig.L_UPPER_ARM, Rig.HEAD};
        for (int k = 0; k < amount; k++) {
            VoxelBody.Chunk c = b.chunks[pick[raid.rng.nextInt(pick.length)]];
            if (c == null || c.empty()) continue;
            int x = raid.rng.nextInt(c.sx), y = raid.rng.nextInt(c.sy), z = raid.rng.nextInt(c.sz);
            // walk in from the front of the grid (characters face -z) until the surface is found
            for (int zz = 0; zz < c.sz; zz++) if (c.at(x, y, zz) != BodyMaterial.EMPTY) { z = zz; break; }
            if (c.at(x, y, z) == BodyMaterial.EMPTY) continue;
            c.recolor(x, y, z, BodyMaterial.mix(c.color[c.idx(x, y, z)], col, 0.6f));
            if (c.at(x + 1, y, z) != BodyMaterial.EMPTY && raid.rng.chance(0.5)) c.recolor(x + 1, y, z, BodyMaterial.mix(c.color[c.idx(x + 1, y, z)], col, 0.45f));
        }
    }

    /** Fragments of what a hit destroyed, thrown along its path. Pooled and capped. */
    private void fragments(float x, float y, float z, float dx, float dy, float dz, DamageProcessor.Result r, boolean infected, int type) {
        int n = Math.min(type == DamageProcessor.BLAST ? 7 : 5, r.destroyed / 3);
        for (int i = 0; i < n; i++) {
            Debris d;
            if (debris.size() >= maxDebris) { d = debris.remove(0); d.rest = false; } else d = new Debris();
            d.x = x; d.y = y; d.z = z;
            float sp = 1.5f + raid.rng.nextFloat() * 4f;
            d.vx = dx * sp + (raid.rng.nextFloat() - 0.5f) * 2.6f; d.vy = dy * sp + 1 + raid.rng.nextFloat() * 2.8f; d.vz = dz * sp + (raid.rng.nextFloat() - 0.5f) * 2.6f;
            d.life = 7 + raid.rng.nextFloat() * 6; d.size = Rig.VOX * (0.8f + raid.rng.nextFloat() * 0.9f); d.spin = raid.rng.nextFloat() * 6;
            boolean bone = r.bone > 0 && raid.rng.chance(Math.min(0.6, r.bone / (double) Math.max(1, r.destroyed) * 2));
            d.color = bone ? 0xFFD4CCB4 : BodyMaterial.exposed(BodyMaterial.TISSUE, infected, raid.rng.nextFloat(), false);
            debris.add(d);
        }
    }

    // ------------------------------------------------------------------ BloodEffectSystem.Sink
    public void drop(float x, float y, float z, float vx, float vy, float vz, float volume, boolean infected) {
        if (drops.size() > maxDrops) drops.remove(0);
        drops.add(new float[]{x, y, z, vx, vy, vz, volume, infected ? 1 : 0});
    }

    /** A drop of bile: the blood system's droplet, marked as bile so that what it leaves is dark green. */
    public void bile(float x, float y, float z, float vx, float vy, float vz, float volume) {
        if (drops.size() > maxDrops) drops.remove(0);
        drops.add(new float[]{x, y, z, vx, vy, vz, volume, 2});
        bileDrops++;
    }

    /** Adds blood to the floor at a point: tops up a pool already there, or starts one. */
    public Pool spill(float x, float y, float z, float volume, boolean infected) {
        Pool best = null;
        float bd = 1e9f;
        for (Pool p : pools) {
            if (Math.abs(p.y - y) > 0.6f || p.infected != infected) continue;
            float d = (p.x - x) * (p.x - x) + (p.z - z) * (p.z - z), reach = p.radius() + 0.35f;
            if (d < reach * reach && d < bd) { bd = d; best = p; }
        }
        if (best == null) {
            best = new Pool();
            best.id = nextPool++;
            best.x = x; best.y = y; best.z = z; best.infected = infected; best.color = infected ? INFECTED_BLOOD : HUMAN_BLOOD; best.born = raid.time;
            pools.add(best);   // trimmed at the end of update(): spill() is called while the pool list is being walked
        }
        best.volume += volume;
        best.wet = 1;
        return best;
    }

    // ------------------------------------------------------------------ DismembermentSystem.Listener
    public void landed(DismembermentSystem.Piece p, float x, float y, float z, float speed) {
        int gy = floorUnder(x, y + 0.3f, z);
        if (gy >= 0) spill(x, gy, z, speed > 0 ? 0.05f : 0.22f, p.body.infected);
        if (speed > 2.5f) raid.emitSound(x, y, z, 7, "gib_land", p.ownerId, false);
    }

    /** Radius a pool wants for its volume: a thin film, so a little blood covers a lot of floor. */
    /** How far a pool of a given volume spreads. 0.7 (fall study): a body's worth of blood is a pool about a metre across, not a lake - the cap was 2.4 m of radius. */
    public static float POOL_MAX = 1.15f;
    private static float target(float volume) { return Math.min(POOL_MAX, 0.14f + (float) Math.sqrt(volume) * 0.8f); }

    /** Where a wound is right now. On a piece: the piece's transform. On a body: the region's bone. */
    private float[] locate(BloodEffectSystem.Emitter e) {
        Mat4 m;
        if (e.pieceId != 0) {
            DismembermentSystem.Piece pc = null;
            for (DismembermentSystem.Piece q : dismember.pieces) if (q.id == e.pieceId) { pc = q; break; }
            if (pc == null) return null;
            m = pc.xform;
        } else {
            Actor a = raid.actor(e.actorId);
            if (a == null || a.body == null || a.tier != 0 && !a.dead) return null;
            if (a.body.chunks[e.group] == null || a.body.chunks[e.group].empty()) return null;
            m = raid.bones(a)[e.group];
        }
        m.transformPoint(e.lx, e.ly, e.lz, v3);
        w6[0] = v3[0]; w6[1] = v3[1]; w6[2] = v3[2];
        m.transformDir(e.dx, e.dy, e.dz, v3);
        float l = (float) Math.sqrt(v3[0] * v3[0] + v3[1] * v3[1] + v3[2] * v3[2]);
        w6[3] = l < 1e-6f ? 0 : v3[0] / l; w6[4] = l < 1e-6f ? 0 : v3[1] / l; w6[5] = l < 1e-6f ? 0 : v3[2] / l;
        return w6;
    }

    void update(float dt) {
        World w = raid.world;
        blood.update(dt, this::locate, this, raid.rng);
        dismember.update(w, dt, this);
        ragdolls.update(dt);
        // ---- droplets
        for (int i = drops.size() - 1; i >= 0; i--) {
            float[] d = drops.get(i);
            d[4] -= 14 * dt;
            float nx = d[0] + d[3] * dt, ny = d[1] + d[4] * dt, nz = d[2] + d[5] * dt;
            if (full(w, nx, ny, nz)) {
                boolean floor = !full(w, nx, d[1], nz) && d[4] < 0;
                if (floor) { Pool pl = spill(nx, (float) Math.floor(ny) + 1, nz, d[6], d[7] > 0); if (d[7] > 1.5f) pl.color = BILE; }
                else {
                    Stain s = new Stain();
                    s.axis = full(w, nx, d[1], d[2]) ? 0 : 2;
                    s.x = s.axis == 0 ? (d[3] > 0 ? (float) Math.floor(nx) - 0.01f : (float) Math.floor(nx) + 1.01f) : nx; s.y = d[1];
                    s.z = s.axis == 2 ? (d[5] > 0 ? (float) Math.floor(nz) - 0.01f : (float) Math.floor(nz) + 1.01f) : nz;
                    s.size = Math.min(0.42f, 0.1f + d[6] * 3); s.color = d[7] > 1.5f ? BILE : d[7] > 0 ? INFECTED_BLOOD : HUMAN_BLOOD;
                    s.born = raid.time;
                    stains.add(s);
                    if (stains.size() > maxStains) stains.remove(0);
                }
                drops.remove(i);
                continue;
            }
            d[0] = nx; d[1] = ny; d[2] = nz;
            if (ny < 0) drops.remove(i);
        }
        // ---- fragments: fall, bounce once, lie there, and go
        for (int i = debris.size() - 1; i >= 0; i--) {
            Debris d = debris.get(i);
            d.life -= dt;
            if (d.life <= 0) { debris.remove(i); continue; }
            if (d.rest) continue;
            d.vy -= 16 * dt;
            float nx = d.x + d.vx * dt, ny = d.y + d.vy * dt, nz = d.z + d.vz * dt;
            if (w.solid((int) Math.floor(nx), (int) Math.floor(d.y), (int) Math.floor(nz))) { d.vx *= -0.3f; d.vz *= -0.3f; nx = d.x; nz = d.z; }
            if (w.solid((int) Math.floor(nx), (int) Math.floor(ny), (int) Math.floor(nz))) {
                ny = (float) Math.floor(ny) + 1 + d.size * 0.5f;
                if (d.vy < -3) { d.vy *= -0.25f; d.vx *= 0.5f; d.vz *= 0.5f; } else { d.rest = true; d.vx = d.vy = d.vz = 0; }
            }
            d.x = nx; d.y = ny; d.z = nz;
        }
        // ---- pools creep outward, run over edges and dry
        // by index, and only over the pools that existed when the pass began: a pool running over an edge spills a
        // new one below it (v0.3 crashed here with a ConcurrentModificationException - see crash.log)
        for (int pi = 0, pn = pools.size(); pi < pn; pi++) {
            Pool p = pools.get(pi);
            float want = target(p.volume);
            int fy = (int) Math.floor(p.y - 0.5f);
            for (int k = 0; k < 8; k++) {
                if (p.r[k] >= want) continue;
                float nr = p.r[k] + dt * (0.05f + (want - p.r[k]) * 0.35f);
                int cx = (int) Math.floor(p.x + DIR8[k][0] * (nr + 0.05f)), cz = (int) Math.floor(p.z + DIR8[k][1] * (nr + 0.05f));
                if (w.solid(cx, fy + 1, cz)) continue;                       // a wall or kerb stops it
                if (!w.solid(cx, fy, cz)) {                                  // an edge: it runs over once and starts a pool below
                    if (!p.dripped && p.volume > 0.25f) {
                        p.dripped = true;
                        for (int y = fy - 1; y > fy - 8 && y > 0; y--) if (w.solid(cx, y, cz)) { spill(cx + 0.5f, y + 1, cz + 0.5f, p.volume * 0.4f, p.infected); p.volume *= 0.6f; break; }
                    }
                    continue;
                }
                p.r[k] = nr;
            }
            if (raid.time - p.born > 25) p.wet = Math.max(0, p.wet - dt / 150f);
        }
        // ---- the wounded and the dead (a death here can rupture a bomber, so walk a stable view of the cast)
        for (int ai = 0; ai < raid.actors.size(); ai++) {
            Actor a = raid.actors.get(ai);
            if (a.body != null) for (VoxelBody.Chunk c : a.body.chunks) if (c != null && c.bleed > 0) c.bleed = Math.max(0, c.bleed - dt * 0.03f);   // clotting
            if (a.bleed <= 0 && a.poolLeft <= 0) continue;
            if (a.dead) {
                // a body drains into the floor it lies on
                if (a.poolLeft > 0 && a.deadTime > 0.8f) { float v = Math.min(a.poolLeft, dt * 0.09f); a.poolLeft -= v; int gy = floorUnder(a.x, a.y + 0.6f, a.z); if (gy >= 0) spill(a.x, gy, a.z, v, a.infected()); }
                continue;
            }
            if (a.isPlayer) continue;   // the player's bleeding is the Survivor's light / heavy bleed
            a.bleed = Math.max(0, a.bleed - dt * 0.05f);          // slow clotting
            float loss = Math.min(a.bleed, 6) * 0.55f * dt;
            a.hp -= loss;
            a.trail += loss;
            // a blood trail: a drip every so often, more often the worse the wound
            if (a.trail > 0.35f) { a.trail = 0; int gy = floorUnder(a.x, a.y + 0.5f, a.z); if (gy >= 0) spill(a.x + (raid.rng.nextFloat() - 0.5f) * 0.3f, gy, a.z + (raid.rng.nextFloat() - 0.5f) * 0.3f, 0.03f + a.bleed * 0.008f, a.infected()); }
            if (a.hp <= 0) raid.bleedOut(a);
        }
        // ---- wet blood is slippery
        for (int ai = 0; ai < raid.actors.size(); ai++) {
            Actor a = raid.actors.get(ai);
            if (a.dead || !a.onGround || a.tier == 1 || a.ragdoll != null) continue;
            float sp = (float) Math.sqrt(a.vx * a.vx + a.vz * a.vz);
            if (sp < 3.4f || raid.time < a.slipUntil || friction(a.x, a.y, a.z) >= 1) continue;
            if (!raid.rng.chance(dt * (a.isPlayer ? 1.1f : 0.8f) * (sp - 2.4f))) continue;
            a.slipUntil = raid.time + 3;
            slips++;
            if (a.isPlayer) raid.playerSlip();
            else {
                // 0.9: the feet go from under it - physically, the way it was running, where it can be simulated
                float sl = Math.max(0.01f, (float) Math.sqrt(a.vx * a.vx + a.vz * a.vz));
                if (!ragdolls.unbalance(a, a.vx / sl, a.vz / sl, 16, "legs")) { a.staggerUntil = raid.time + 1.3f; if (a.anim != null) a.anim.play("stumble", 1.0f); }
                raid.emitSound(a.x, a.y, a.z, 12, "slip", a.id, false);
            }
        }
        // ---- budgets: the oldest blood goes first, and only once nothing is iterating
        if (pools.size() > maxPools) pools.subList(0, pools.size() - maxPools).clear();
        // 1.0: blood does not stay for good either. A pool dries from its edges in over its last half minute and is gone;
        // what was thrown on a wall goes a little later
        for (int i = pools.size() - 1; i >= 0; i--) {
            Pool p = pools.get(i);
            float age = raid.time - p.born;
            if (age > BLOOD_SECONDS) { pools.remove(i); continue; }
            if (age > BLOOD_SECONDS - 30) { float k = 1 - dt * 0.07f; for (int j = 0; j < 8; j++) p.r[j] *= k; p.wet = Math.min(p.wet, 0.2f); }
        }
        while (!stains.isEmpty() && raid.time - stains.get(0).born > BLOOD_SECONDS * 1.3f) stains.remove(0);
    }

    /** 1.0: how long blood on the ground lasts (seconds); on a wall, a third longer. */
    public static float BLOOD_SECONDS = 140f;

    /** The look a detached piece is drawn with (its owner's). */
    public Actor owner(DismembermentSystem.Piece p) { return pieceOwner.get(p.id); }

    /** Blood only marks real surfaces: posts, fences, furniture and other thin props let it fall past. */
    private static boolean full(World w, float x, float y, float z) {
        Block b = w.block((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return y < 0 || b != null && b.solid() && b.shape == 0;
    }

    private int floorUnder(float x, float y, float z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        for (int by = (int) Math.floor(y); by > Math.floor(y) - 4 && by > 0; by--) if (raid.world.solid(ix, by - 1, iz) && !raid.world.solid(ix, by, iz)) return by;
        return -1;
    }

    /** The wet pool a point stands in, or null. */
    public Pool poolAt(float x, float y, float z) {
        for (Pool p : pools) {
            if (p.wet < 0.25f || Math.abs(p.y - y) > 0.7f) continue;
            float r = p.radius();
            if (r > 0.4f && (p.x - x) * (p.x - x) + (p.z - z) * (p.z - z) < r * r) return p;
        }
        return null;
    }

    /** Surface grip at a point: 1 on dry ground, down to 0.2 in fresh blood. */
    public float friction(float x, float y, float z) { Pool p = poolAt(x, y, z); return p == null ? 1 : 1 - 0.8f * p.wet; }

    /** The freshest blood within range that this observer has not yet examined (pools are found in order: that is a trail). */
    public Pool evidence(Actor a, float range) {
        Pool best = null;
        for (Pool p : pools) {
            if (p.infected || p.radius() < 0.22f || Math.abs(p.y - a.y) > 2.5f) continue;
            if (a.evidenceSeen != null && a.evidenceSeen.contains(p.id)) continue;
            float d = a.distXZ(p.x, p.z);
            if (d > range) continue;
            if (best == null || p.born > best.born) best = p;
        }
        return best;
    }
}
