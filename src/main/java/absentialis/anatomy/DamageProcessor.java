package absentialis.anatomy;

import static absentialis.anatomy.BodyMaterial.*;

import absentialis.character.Rig;
import absentialis.engine.util.Rng;

/**
 * Localised damage: what an impact does to the voxels where it lands.
 *
 * An impact is described physically - where it struck the region and travelling which way (in that region's own
 * voxel space), how much energy it carried, how well it penetrates and what kind of thing it was - and the result
 * follows from the materials in its path, including whatever earlier hits have already taken away:
 *
 *   bullet   bores along its path, losing penetrating power to each voxel it crosses and energy as it goes. The
 *            channel widens with depth (a small entry, a larger exit), armour and bone slow it, and if it still
 *            has power when it leaves the far side it is a through-and-through with an exit wound.
 *   blade    opens a shallow cut along a line across the surface.
 *   blunt    crushes: the surface bruises, and with enough energy the bone underneath breaks though the skin holds.
 *   blast    strips the side facing the explosion and chars what is left.
 *   bite     tears a small crescent out of the surface.
 *   burn     chars without removing.
 *
 * Voxels that are hit but not destroyed keep the damage (their remaining durability drops and they discolour), so
 * a second hit in the same place goes deeper than the first. Nothing here knows about meshes, physics or AI.
 */
public final class DamageProcessor {
    private DamageProcessor() {}

    public static final int BULLET = 0, BLADE = 1, BLUNT = 2, BLAST = 3, BITE = 4, BURN = 5;
    public static final String[] TYPE = {"bullet", "blade", "blunt", "blast", "bite", "burn"};

    /** An impact on one region, in that region's voxel coordinates. */
    public static final class Hit {
        public int type;
        /** Point of impact (on or just outside the surface) and unit direction of travel. */
        public float px, py, pz, dx, dy, dz;
        /** Energy in damage points, and penetrating power (a pistol round is about 2, a rifle round 4, a slug 6). */
        public float energy, power;
        public int seed;
    }

    /** What a hit did. */
    public static final class Result {
        public int destroyed, bone, joint, organ, surface, armor;
        public final int[] byMaterial = new int[COUNT];
        /** Deepest the damage reached, in voxels, and whether a projectile came out the far side (and where, heading where). */
        public float depth;
        public boolean exit, stopped;
        public float ex, ey, ez, exDx, exDy, exDz;
        /** Blood released by the destroyed voxels (volume units). */
        public float blood;
        /** Energy the body absorbed (the part of the hit that was not carried out the other side). */
        public float absorbed;
        /** Row (y) of the region that took the worst of it: where a limb would break. */
        public int worstRow = -1;
        void clear() { destroyed = bone = joint = organ = surface = armor = 0; java.util.Arrays.fill(byMaterial, 0); depth = 0; exit = stopped = false; blood = absorbed = 0; worstRow = -1; }
    }

    /** Applies a hit to a region. The result object is reused by the caller. */
    public static void apply(VoxelBody body, int group, Hit h, Result r) {
        r.clear();
        VoxelBody.Chunk c = body.chunks[group];
        if (c == null || c.empty()) return;
        switch (h.type) {
            case BULLET -> bullet(body, c, h, r);
            case BLADE -> blade(body, c, h, r);
            case BLUNT -> blunt(body, c, h, r);
            case BLAST -> blast(body, c, h, r);
            case BITE -> bite(body, c, h, r);
            default -> burn(body, c, h, r);
        }
        if (r.destroyed > 0) {
            c.bleed += r.blood * 0.06f;
            body.destroyed += r.destroyed;
            for (int m = 0; m < COUNT; m++) body.destroyedBy[m] += r.byMaterial[m];
        }
    }

    /** Damages one voxel by an amount of energy. Returns true when it was destroyed. */
    private static boolean hurt(VoxelBody body, VoxelBody.Chunk c, int x, int y, int z, float e, Result r, boolean stain) {
        if (!c.in(x, y, z)) return false;
        int i = c.idx(x, y, z);
        byte m = c.mat[i];
        if (m == EMPTY || e <= 0) return false;
        c.hp[i] -= e;
        if (c.hp[i] > 0) {
            // hurt but still there: it shows. Flesh and cloth soak, bone and armour chip.
            if (stain && m != ARMOR) c.recolor(x, y, z, BodyMaterial.mix(c.color[i], BodyMaterial.blood(body.infected), Math.min(0.75f, 0.25f + e / DURABILITY[m] * 0.5f)));
            else if (m == ARMOR || m == BONE) c.recolor(x, y, z, BodyMaterial.mix(c.color[i], 0xFF202020, 0.25f));
            return false;
        }
        c.remove(x, y, z);
        r.destroyed++;
        r.byMaterial[m]++;
        r.blood += BLEED[m];
        if (m == BONE) r.bone++;
        else if (m == JOINT) r.joint++;
        else if (m == ORGAN) r.organ++;
        else if (m == ARMOR) r.armor++;
        if (surface(m)) r.surface++;
        return true;
    }

    /** After voxels have gone, whatever they uncovered is raw: freshly exposed tissue around the hole is wet with blood. */
    private static void soak(VoxelBody body, VoxelBody.Chunk c, float cx, float cy, float cz, float radius, int seed) {
        int r = (int) Math.ceil(radius);
        int blood = BodyMaterial.blood(body.infected);
        for (int x = (int) cx - r; x <= (int) cx + r; x++) for (int y = (int) cy - r; y <= (int) cy + r; y++) for (int z = (int) cz - r; z <= (int) cz + r; z++) {
            if (!c.in(x, y, z)) continue;
            int i = c.idx(x, y, z);
            byte m = c.mat[i];
            if (m == EMPTY || m == ARMOR || m == BONE) continue;
            float d = (float) Math.sqrt((x + 0.5f - cx) * (x + 0.5f - cx) + (y + 0.5f - cy) * (y + 0.5f - cy) + (z + 0.5f - cz) * (z + 0.5f - cz));
            if (d > radius || !c.exposed(x, y, z)) continue;
            float k = (1 - d / radius) * (0.35f + 0.4f * Rng.hash(x, y, z, seed));
            if (k > 0.08f) c.recolor(x, y, z, BodyMaterial.mix(c.color[i], blood, Math.min(0.8f, k)));
        }
    }

    // ------------------------------------------------------------------ bullet
    private static void bullet(VoxelBody body, VoxelBody.Chunk c, Hit h, Result r) {
        // march in half-voxel steps from just outside the entry point
        float x = h.px - h.dx * 0.75f, y = h.py - h.dy * 0.75f, z = h.pz - h.dz * 0.75f;
        float energy = h.energy, power = h.power, travelled = 0;
        boolean inside = false;
        float baseR = Math.max(0.55f, Math.min(2.7f, 0.42f + h.energy / 46f));
        int lastX = Integer.MIN_VALUE, lastY = 0, lastZ = 0;
        float worst = 0;
        // the path wanders a little: no two wounds from the same weapon are the same shape
        float jx = (Rng.hash(h.seed, 1, 2, 7) - 0.5f) * 0.14f, jy = (Rng.hash(h.seed, 3, 4, 7) - 0.5f) * 0.14f, jz = (Rng.hash(h.seed, 5, 6, 7) - 0.5f) * 0.14f;
        float dx = h.dx + jx, dy = h.dy + jy, dz = h.dz + jz, dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= dl; dy /= dl; dz /= dl;
        for (int step = 0; step < 64; step++) {
            x += dx * 0.5f; y += dy * 0.5f; z += dz * 0.5f;
            int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
            if (!c.in(ix, iy, iz)) { if (inside || step > 12) break; continue; }
            if (ix == lastX && iy == lastY && iz == lastZ) continue;
            lastX = ix; lastY = iy; lastZ = iz;
            byte m = c.mat[c.idx(ix, iy, iz)];
            if (m != EMPTY) {
                inside = true;
                travelled += 1;
                // the channel: narrow going in, opening up as the round yaws and breaks up
                float rad = baseR * (0.55f + Math.min(1f, travelled / 5f) * 0.75f);
                int rr = (int) Math.ceil(rad);
                int before = r.destroyed;
                for (int ox = -rr; ox <= rr; ox++) for (int oy = -rr; oy <= rr; oy++) for (int oz = -rr; oz <= rr; oz++) {
                    float d = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
                    if (d > rad) continue;
                    float fall = 1f - d / (rad + 0.35f);
                    float ragged = 0.75f + 0.5f * Rng.hash(ix + ox, iy + oy, iz + oz, h.seed);
                    hurt(body, c, ix + ox, iy + oy, iz + oz, energy * fall * ragged, r, true);
                }
                if (r.destroyed - before > worst) { worst = r.destroyed - before; r.worstRow = iy; }
                power -= RESIST[m];
                energy *= 1f - Math.min(0.5f, RESIST[m] * 0.16f);
                r.depth = travelled;
                if (power <= 0) { r.stopped = true; break; }
            } else if (inside) {
                // crossing a cavity an earlier hit left: nothing to slow it
                travelled += 0.5f;
            }
        }
        if (inside && !r.stopped) {
            r.exit = true;
            r.ex = x; r.ey = y; r.ez = z; r.exDx = dx; r.exDy = dy; r.exDz = dz;
        }
        r.absorbed = h.energy - (r.exit ? energy * 0.6f : 0);
        if (r.worstRow < 0) r.worstRow = Math.max(0, Math.min(c.sy - 1, (int) Math.floor(h.py)));
        soak(body, c, h.px, h.py, h.pz, baseR + 1.4f, h.seed);
        if (r.exit) soak(body, c, r.ex, r.ey, r.ez, baseR + 1.9f, h.seed + 1);
    }

    // ------------------------------------------------------------------ blade
    private static void blade(VoxelBody body, VoxelBody.Chunk c, Hit h, Result r) {
        // a cut runs across the surface, perpendicular to the thrust, as long and as deep as the blow was hard
        float ux = -h.dz, uy = 0.35f * (Rng.hash(h.seed, 0, 0, 3) - 0.5f) + 0.6f, uz = h.dx;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        float len = Math.min(6f, 1.5f + h.energy / 14f), depth = Math.min(3.2f, 0.8f + h.energy / 28f);
        for (float t = -len / 2; t <= len / 2; t += 0.5f) for (float d = -0.5f; d <= depth; d += 0.5f) {
            float x = h.px + ux * t + h.dx * d, y = h.py + uy * t + h.dy * d, z = h.pz + uz * t + h.dz * d;
            float e = h.energy * (1f - Math.abs(t) / (len / 2 + 0.5f)) * (1f - Math.max(0, d) / (depth + 0.5f)) * 0.9f;
            hurt(body, c, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), e, r, true);
        }
        r.depth = depth;
        r.worstRow = Math.max(0, Math.min(c.sy - 1, (int) Math.floor(h.py)));
        r.absorbed = h.energy;
        soak(body, c, h.px, h.py, h.pz, len * 0.5f + 1, h.seed);
    }

    // ------------------------------------------------------------------ blunt
    private static void blunt(VoxelBody body, VoxelBody.Chunk c, Hit h, Result r) {
        float rad = Math.min(4.5f, 1.6f + h.energy / 26f);
        int rr = (int) Math.ceil(rad);
        int bruise = body.infected ? 0xFF2A2E1A : 0xFF4A2A3E;
        int cx = (int) Math.floor(h.px), cy = (int) Math.floor(h.py), cz = (int) Math.floor(h.pz);
        for (int ox = -rr; ox <= rr; ox++) for (int oy = -rr; oy <= rr; oy++) for (int oz = -rr; oz <= rr; oz++) {
            float d = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
            if (d > rad) continue;
            int x = cx + ox, y = cy + oy, z = cz + oz;
            if (!c.in(x, y, z)) continue;
            int i = c.idx(x, y, z);
            byte m = c.mat[i];
            if (m == EMPTY) continue;
            float fall = 1f - d / (rad + 0.5f);
            if (surface(m) || m == TISSUE || m == ORGAN) {
                // skin and flesh take a blow without opening - until the energy is far beyond what they hold
                if (h.energy * fall > DURABILITY[m] * 3.2f) hurt(body, c, x, y, z, h.energy * fall * 0.4f, r, true);
                else if (m != ARMOR && c.exposed(x, y, z)) c.recolor(x, y, z, BodyMaterial.mix(c.color[i], bruise, Math.min(0.6f, fall * h.energy / 60f)));
                c.hp[i] = Math.max(1f, c.hp[i] - h.energy * fall * 0.12f);
            } else {
                // what is rigid cracks: the shock goes through the flesh into the bone
                hurt(body, c, x, y, z, h.energy * fall * 0.85f, r, false);
            }
        }
        r.depth = rad;
        r.worstRow = Math.max(0, Math.min(c.sy - 1, cy));
        r.absorbed = h.energy;
    }

    // ------------------------------------------------------------------ blast
    private static void blast(VoxelBody body, VoxelBody.Chunk c, Hit h, Result r) {
        int char1 = 0xFF1E1A18;
        // everything on the side facing the explosion is exposed to it; a stronger blast peels deeper, layer after layer
        int layers = 1 + (int) Math.min(3, h.energy / 70f);
        for (int layer = 0; layer < layers; layer++)
        for (int x = 0; x < c.sx; x++) for (int y = 0; y < c.sy; y++) for (int z = 0; z < c.sz; z++) {
            int i = c.idx(x, y, z);
            if (c.mat[i] == EMPTY || !c.exposed(x, y, z)) continue;
            // facing: is the way back toward the blast open?
            int bx = x - Math.round(h.dx), by = y - Math.round(h.dy), bz = z - Math.round(h.dz);
            boolean facing = c.at(bx, by, bz) == EMPTY;
            float k = Rng.hash(x, y, z, h.seed + layer * 977);
            if (facing && k < Math.min(0.85f, h.energy / 130f) / (1 + layer * 0.6f)) {
                if (!hurt(body, c, x, y, z, h.energy * (0.4f + k), r, true) && c.mat[i] != EMPTY) c.recolor(x, y, z, BodyMaterial.mix(c.color[i], char1, 0.5f));
            } else if (facing || k < 0.3f) c.recolor(x, y, z, BodyMaterial.mix(c.color[i], char1, facing ? 0.45f : 0.2f));
        }
        // the pressure wave breaks what is inside too
        if (h.energy > 55) for (int n = 0; n < (int) (h.energy / 30); n++) {
            int x = (int) (Rng.hash(n, 1, 0, h.seed) * c.sx), y = (int) (Rng.hash(n, 2, 0, h.seed) * c.sy), z = (int) (Rng.hash(n, 3, 0, h.seed) * c.sz);
            if (c.at(x, y, z) == BONE || c.at(x, y, z) == JOINT) hurt(body, c, x, y, z, h.energy * 0.9f, r, false);
        }
        // and it wrenches limbs at the joint: the stronger the blast, the likelier the joint gives way altogether
        if (VoxelBody.limb(c.group) && Rng.hash(c.group, 7, 3, h.seed) < (h.energy - 55f) / 110f) {
            for (int y = c.sy - 2; y < c.sy; y++) for (int x = 0; x < c.sx; x++) for (int z = 0; z < c.sz; z++)
                hurt(body, c, x, y, z, h.energy * (0.35f + 0.9f * Rng.hash(x, y, z, h.seed + 5)), r, true);
            r.worstRow = c.sy - 2;
        } else r.worstRow = c.sy / 2;
        r.depth = 1.5f;
        r.absorbed = h.energy;
    }

    // ------------------------------------------------------------------ bite and burn
    private static void bite(VoxelBody body, VoxelBody.Chunk c, Hit h, Result r) {
        float rad = 1.35f + Math.min(0.9f, h.energy / 40f);
        int rr = (int) Math.ceil(rad);
        int cx = (int) Math.floor(h.px), cy = (int) Math.floor(h.py), cz = (int) Math.floor(h.pz);
        for (int ox = -rr; ox <= rr; ox++) for (int oy = -1; oy <= 1; oy++) for (int oz = -rr; oz <= rr; oz++) {
            float d = (float) Math.sqrt(ox * ox + oy * oy * 2.2f + oz * oz);
            if (d > rad) continue;
            int x = cx + ox, y = cy + oy, z = cz + oz;
            byte m = c.at(x, y, z);
            if (m == EMPTY || m == ARMOR || m == BONE) continue;
            hurt(body, c, x, y, z, 30f * (1f - d / (rad + 0.6f)) + h.energy * 0.3f, r, true);
        }
        r.depth = 1.5f;
        r.worstRow = Math.max(0, Math.min(c.sy - 1, cy));
        r.absorbed = h.energy;
        soak(body, c, h.px, h.py, h.pz, rad + 1.2f, h.seed);
    }

    private static void burn(VoxelBody body, VoxelBody.Chunk c, Hit h, Result r) {
        int char1 = 0xFF1C1614, char2 = 0xFF4A1E12;
        float share = Math.min(0.6f, h.energy / 50f);
        for (int x = 0; x < c.sx; x++) for (int y = 0; y < c.sy; y++) for (int z = 0; z < c.sz; z++) {
            int i = c.idx(x, y, z);
            if (c.mat[i] == EMPTY || !c.exposed(x, y, z)) continue;
            float k = Rng.hash(x, y, z, h.seed);
            if (k < share) { c.recolor(x, y, z, BodyMaterial.mix(c.color[i], k < share * 0.5f ? char1 : char2, 0.55f)); c.hp[i] = Math.max(1f, c.hp[i] - h.energy * 0.2f); }
        }
        r.absorbed = h.energy;
        r.worstRow = c.sy / 2;
    }

    /** The coarse body region a rig group belongs to, in the terms the rest of the game uses. */
    public static String regionName(int g) {
        if (g == Rig.HEAD) return "head";
        if (g == Rig.TORSO) return "torso";
        return g >= Rig.L_THIGH ? "legs" : "arms";
    }
}
