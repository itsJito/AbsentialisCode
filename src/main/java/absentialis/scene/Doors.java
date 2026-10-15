package absentialis.scene;

import absentialis.engine.events.Events;
import absentialis.engine.math.Mat4;
import absentialis.engine.render.ModelRenderer;
import absentialis.engine.util.Rng;
import absentialis.engine.voxel.VoxModel;
import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.world.Block;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Doors as physical things (0.5, brief section 8; extended in 0.6).
 *
 * Every door in the world - generated, or found by scanning a world that was built some other way - is an entity:
 * a leaf two cells tall on a hinge, with an angle and an angular velocity, a material, structural health, four
 * damage regions (upper panel, lower panel, hinge side, latch side), a latch that can be locked or broken, and a
 * state the AI can ask for. A door swings; it never switches. A round that hits it is located on the leaf: enough
 * on the latch and the door is forced and hangs open, enough on the hinge side and it comes off and falls as a
 * slab, enough anywhere and it breaks up. Blast does the same in one go.
 *
 * The voxel cells a door stands in remain the collision, sight and light proxy (solid while the leaf is within a
 * few degrees of shut, open beyond that, empty once it is gone), so physics, line of sight, navigation, cover and
 * lighting all follow the door through the code they already have; the chunk mesher no longer draws door blocks,
 * the leaf drawn here is the only door you see.
 *
 * 0.6:
 *   DOUBLE DOORS  one entity two cells wide ({@link Door#span} = 2) with a leaf hinged at each jamb. One handle, one
 *                 latch where the leaves meet, one health pool, one state: used, locked, forced and destroyed as one.
 *   BODIES        an open leaf is pushed by whoever walks into it, and held off by whoever (or whatever is left of
 *                 whoever) lies in the doorway. A leaf never becomes solid on top of a body.
 *   VEHICLE DOORS the same entity without voxel cells ({@link Door#mobile}): the vehicle system owns where the hinge
 *                 is, this class owns the swing, the damage regions, the latch and the moment it comes off.
 *   GLASS         a pane that breaks leaves shards: rigid pieces that fly, fall, slide and lie where they land.
 */
public final class Doors implements World.DoorOracle {
    public static final int WOOD = 0, METAL = 1, GLASS = 2, CAR = 3, SHARD = 4;
    public static final int R_UPPER = 0, R_LOWER = 1, R_HINGE = 2, R_LATCH = 3;
    public static final String[] REGION = {"upper panel", "lower panel", "hinge", "latch"};
    public static final float OPEN = 1.45f, SOLID_BELOW = 0.30f;
    /** Pieces of door and glass kept in the world at once (the oldest resting ones go first). */
    public static int MAX_SLABS = 220;

    public enum State { OPEN, CLOSED, LOCKED, BLOCKED, DAMAGED, DESTROYED }

    public static final class Door {
        public int id, x, y, z, kind, hinge;
        /** Cells along the wall this one door fills: 1, or 2 for a double door (a leaf at each jamb). */
        public int span = 1;
        /** The wall the door is in runs along x (so the shut leaf lies along x). */
        public boolean alongX, exterior;
        /** Hinge angle in radians (0 = shut; the sign is the way it has swung), angular velocity, and where it is heading. */
        public float angle, vel, target;
        public float health, maxHealth;
        public final float[] region = new float[4];
        public boolean locked, latchBroken, hingeBroken, destroyed, solid = true;
        public float blockedUntil = -99, lastUse = -99, forcedAt = -99;
        public int lastUser, hits;
        int closedBlock, openBlock, pushFrame;
        boolean toldSealed;

        // ---- vehicle doors: no cells; the owner writes the hinge position, heading and velocity every step
        public boolean mobile;
        public Object owner;
        /** World position of the middle of the leaf, its heading, the velocity it shares with what carries it, and its size. */
        public float mx, my, mz, myaw, mvx, mvy, mvz, mlen = 1, mh = 1, maxOpen = OPEN;
        /** What the leaf looks like once it is off (leaf layout: x from the hinge, y up, z thickness), its voxel size and cache key. */
        public VoxModel model;
        public String modelKey;
        public float vox = 1 / 16f;

        public int leaves() { return span; }
        private int hingeOf(int leaf) { return span == 1 ? hinge : leaf; }
        public float pivotX(int leaf) { return alongX ? x + (hingeOf(leaf) == 0 ? 0 : span) : x + 0.5f; }
        public float pivotZ(int leaf) { return alongX ? z + 0.5f : z + (hingeOf(leaf) == 0 ? 0 : span); }
        /** Unit direction of the shut leaf from the hinge. */
        public float dirX(int leaf) { return alongX ? (hingeOf(leaf) == 0 ? 1 : -1) : 0; }
        public float dirZ(int leaf) { return alongX ? 0 : (hingeOf(leaf) == 0 ? 1 : -1); }
        /** The second leaf of a pair mirrors the first, so both swing to the same side of the wall. */
        public float leafAngle(int leaf) { return span == 2 && leaf == 1 ? -angle : angle; }
        public float pivotX() { return pivotX(0); }
        public float pivotZ() { return pivotZ(0); }
        public float dirX() { return dirX(0); }
        public float dirZ() { return dirZ(0); }
        /** Middle of the first cell (the entity's anchor) and of the whole opening. */
        public float cx() { return mobile ? mx : x + 0.5f; }
        public float cz() { return mobile ? mz : z + 0.5f; }
        public float midX() { return mobile ? mx : alongX ? x + span * 0.5f : x + 0.5f; }
        public float midZ() { return mobile ? mz : alongX ? z + 0.5f : z + span * 0.5f; }
        public boolean damaged() { return health < maxHealth * 0.6f || latchBroken; }
        public int damageLevel() { return health < maxHealth * 0.3f ? 2 : health < maxHealth * 0.6f ? 1 : 0; }
        public boolean isDouble() { return span == 2; }
    }

    /** A leaf off its hinges, a piece of one, or a shard of glass: a rigid slab that falls, slides and comes to rest. */
    public static final class Slab {
        public float x, y, z, vx, vy, vz, yaw, pitch, spin, life = 60, w, h;
        public int kind, level, tint = 0xFFFFFFFF;
        public boolean rest;
        VoxModel model; String key; float vox;
    }

    private final Raid raid;
    public final List<Door> doors = new ArrayList<>();
    public final List<Slab> slabs = new ArrayList<>();
    private final Map<Integer, Door> byCell = new HashMap<>();
    private final List<Door> moving = new ArrayList<>();
    /** Counters for the gates and the overlay. */
    public int swings, forced, detached, shattered, hitsTaken, pushes, panes, doubles;
    private int frame;

    Doors(Raid raid) { this.raid = raid; }

    // ------------------------------------------------------------------ setup
    private static int kindOf(int block) {
        return block == Block.DOOR_METAL || block == Block.DOOR_METAL_OPEN || block == Block.DOOR_DOUBLE_METAL || block == Block.DOOR_DOUBLE_METAL_OPEN ? METAL
                : block == Block.DOOR_GLASS || block == Block.DOOR_GLASS_OPEN || block == Block.DOOR_DOUBLE_GLASS || block == Block.DOOR_DOUBLE_GLASS_OPEN ? GLASS : WOOD;
    }

    /** Finds every door in the world as it stands and makes an entity of it. Cheap enough to run once per deployment. */
    public void scan() {
        World w = raid.world;
        doors.clear(); byCell.clear(); moving.clear(); slabs.clear(); doubles = 0;
        byte[] bl = w.blocks;
        boolean[] isDoor = new boolean[256];
        for (Block b : Block.all()) if (b.is(Block.DOOR)) isDoor[b.id] = true;
        for (int x = 1; x < World.W - 1; x++) for (int z = 1; z < World.D - 1; z++) {
            int base = (x * World.D + z) * World.H;
            for (int y = 1; y < World.H - 2; y++) {
                int id = bl[base + y] & 0xFF;
                if (!isDoor[id] || isDoor[bl[base + y - 1] & 0xFF]) continue;      // the lower cell of each leaf
                if (Block.doubleDoor(id)) {
                    // a pair: the lower-coordinate column is the anchor, the other belongs to it
                    Door px = byCell.get(World.idx(x - 1, y, z)), pz = byCell.get(World.idx(x, y, z - 1));
                    if (px != null && px.span == 2 && px.alongX && px.x == x - 1 || pz != null && pz.span == 2 && !pz.alongX && pz.z == z - 1) continue;
                    if (Block.doubleDoor(w.get(x + 1, y, z))) { add(x, y, z, id, 2, true); continue; }
                    if (Block.doubleDoor(w.get(x, y, z + 1))) { add(x, y, z, id, 2, false); continue; }
                }
                add(x, y, z, id, 1, false);
            }
        }
        w.doors = this;
    }

    private Door add(int x, int y, int z, int id, int span, boolean pairAlongX) {
        World w = raid.world;
        Door d = new Door();
        d.id = doors.size(); d.x = x; d.y = y; d.z = z; d.span = span;
        d.kind = kindOf(id);
        if (span == 2) { d.alongX = pairAlongX; doubles++; }
        else {
            boolean wx = wall(w, x - 1, y, z) || wall(w, x + 1, y, z), wz = wall(w, x, y, z - 1) || wall(w, x, y, z + 1);
            d.alongX = !(wz && !wx);
        }
        float h = Rng.hash(x, y, z, 5113), h2 = Rng.hash(x, y, z, 977);
        d.hinge = h < 0.5f ? 0 : 1;
        boolean dbl = span == 2;
        d.closedBlock = d.kind == METAL ? (dbl ? Block.DOOR_DOUBLE_METAL : Block.DOOR_METAL) : d.kind == GLASS ? (dbl ? Block.DOOR_DOUBLE_GLASS : Block.DOOR_GLASS) : dbl ? Block.DOOR_DOUBLE_WOOD : Block.DOOR_WOOD;
        d.openBlock = d.kind == METAL ? (dbl ? Block.DOOR_DOUBLE_METAL_OPEN : Block.DOOR_METAL_OPEN) : d.kind == GLASS ? (dbl ? Block.DOOR_DOUBLE_GLASS_OPEN : Block.DOOR_GLASS_OPEN) : dbl ? Block.DOOR_DOUBLE_WOOD_OPEN : Block.DOOR_WOOD_OPEN;
        d.maxHealth = d.health = (d.kind == METAL ? 900 : d.kind == GLASS ? 45 : 140) * (dbl ? 1.5f : 1f);
        // out of doors on at least one side: the sky reaches the cell in front or behind
        int nx = d.alongX ? 0 : 1, nz = d.alongX ? 1 : 0;
        d.exterior = w.heightAt(x + nx, z + nz) < y || w.heightAt(x - nx, z - nz) < y;
        boolean shut = Block.ALL[id].solid();
        if (!shut) { d.angle = d.target = (h2 < 0.5f ? 1 : -1) * OPEN; d.solid = false; }
        else {
            // left as people left them: most pulled to, some ajar, a few locked
            if (h2 < (d.kind == METAL ? 0.14f : d.exterior ? 0.12f : 0.04f)) d.locked = true;
            else if (h2 > 0.62f && !d.exterior) { d.angle = d.target = (h < 0.5f ? 1 : -1) * (h2 > 0.82f ? OPEN : 0.55f); }
        }
        doors.add(d);
        for (int k = 0; k < span; k++) {
            int cx = x + (d.alongX ? k : 0), cz = z + (d.alongX ? 0 : k);
            byCell.put(World.idx(cx, y, cz), d);
            byCell.put(World.idx(cx, y + 1, cz), d);
        }
        if (shut && Math.abs(d.angle) >= SOLID_BELOW) {
            // found ajar: the proxy cells say so from the start (no relight for a state the world begins in)
            d.solid = false;
            for (int k = 0; k < span; k++) {
                int cx = x + (d.alongX ? k : 0), cz = z + (d.alongX ? 0 : k);
                w.setRaw(cx, y, cz, d.openBlock); w.setRaw(cx, y + 1, cz, d.openBlock);
                w.cover.invalidate(cx, cz);
            }
        }
        return d;
    }

    /**
     * A door that rides on something else (a vehicle). It has no cells: the owner keeps {@link Door#mx} and the rest
     * current, asks for it to open and close through {@link #swing}, and reports hits through {@link #hitMobile}.
     */
    public Door addMobile(Object owner, float health, float maxOpen, float length, float height) {
        Door d = new Door();
        d.id = doors.size(); d.mobile = true; d.owner = owner; d.kind = CAR; d.solid = false;
        d.maxHealth = d.health = health; d.maxOpen = maxOpen; d.mlen = length; d.mh = height;
        doors.add(d);
        return d;
    }

    private static boolean wall(World w, int x, int y, int z) { Block n = w.block(x, y, z); return n != null && n.solid() && n.shape == 0 && !n.is(Block.DOOR); }

    /**
     * 1.2: tells the native core which doors cannot simply be opened (locked and still latched) - what {@link #sealed} answers
     * for the searches still made in Java. Only a door whose state has changed since it was last told is written down.
     */
    void sealsTo(absentialis.world.NativeLink link) {
        if (sealedFor != link) { sealedFor = link; link.doorsUnseal(); for (Door d : doors) d.toldSealed = false; }
        for (Door d : doors) {
            if (d.mobile) continue;
            boolean s = !d.destroyed && d.solid && d.locked && !d.latchBroken;
            if (s == d.toldSealed) continue;
            d.toldSealed = s;
            for (int k = 0; k < d.span; k++) { int cx = d.alongX ? d.x + k : d.x, cz = d.alongX ? d.z : d.z + k; link.doorSeal(cx, d.y, cz, s); link.doorSeal(cx, d.y + 1, cz, s); }
        }
    }
    private absentialis.world.NativeLink sealedFor;

    public Door at(int x, int y, int z) { return World.inBounds(x, y, z) ? byCell.get(World.idx(x, y, z)) : null; }

    /** The door a body at a position would use next: the nearest live one within reach. */
    public Door near(float x, float y, float z, float reach) {
        Door best = null;
        float bd = reach * reach;
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z), r = (int) Math.ceil(reach);
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = 0; dy <= 1; dy++) {
            Door d = at(ix + dx, iy + dy, iz + dz);
            if (d == null || d.destroyed) continue;
            float dd = (d.midX() - x) * (d.midX() - x) + (d.midZ() - z) * (d.midZ() - z);
            if (dd < bd) { bd = dd; best = d; }
        }
        return best;
    }

    // ------------------------------------------------------------------ state for the AI and navigation
    public State state(Door d) {
        if (d.destroyed) return State.DESTROYED;
        if (d.mobile) return d.angle > 0.15f ? State.OPEN : d.damaged() ? State.DAMAGED : State.CLOSED;
        if (!d.solid) return State.OPEN;
        if (d.locked && !d.latchBroken) return State.LOCKED;
        if (raid.time < d.blockedUntil) return State.BLOCKED;
        return d.damaged() ? State.DAMAGED : State.CLOSED;
    }

    /** World.DoorOracle: a shut door a walker cannot simply open (locked and still latched). */
    @Override public boolean sealed(int x, int y, int z) {
        Door d = at(x, y, z);
        return d != null && !d.destroyed && d.solid && d.locked && !d.latchBroken;
    }

    private void setSolid(Door d, boolean solid) {
        d.solid = solid;
        if (d.mobile) { d.solid = false; return; }
        int id = d.destroyed ? Block.AIR : solid ? d.closedBlock : d.openBlock;
        // the cells are the collision, sight and light proxy; World.set tells navigation, cover and the lighting
        for (int k = 0; k < d.span; k++) {
            int cx = d.x + (d.alongX ? k : 0), cz = d.z + (d.alongX ? 0 : k);
            raid.world.set(cx, d.y, cz, id);
            raid.world.set(cx, d.y + 1, cz, id);
            if (d.destroyed) { byCell.remove(World.idx(cx, d.y, cz)); byCell.remove(World.idx(cx, d.y + 1, cz)); }
        }
    }

    private void wake(Door d) { if (!moving.contains(d)) moving.add(d); }

    // ------------------------------------------------------------------ use
    /** Which way the leaf must swing to move away from a point: the sign of the open angle. */
    private static float away(Door d, float fromX, float fromZ) {
        // a positive angle carries the free edge toward (-dirZ, dirX)
        float px = -d.dirZ(), pz = d.dirX();
        float side = (fromX - d.midX()) * px + (fromZ - d.midZ()) * pz;
        return side > 0 ? -1 : 1;
    }

    /**
     * Someone works the handle. Returns null when the door answered (it is now opening or closing), or a short
     * reason when it did not ("Locked").
     */
    public String use(Door d, Actor by) {
        if (d == null || d.destroyed) return null;
        if (d.mobile) { swing(d, d.target < 0.1f); return null; }
        if (d.locked && !d.latchBroken && d.solid) {
            raid.emitSound(d.midX(), d.y + 1, d.midZ(), 6, "door_locked", by == null ? 0 : by.id, false);
            return "Locked";
        }
        boolean opening = Math.abs(d.target) < 0.1f;
        d.target = opening ? away(d, by == null ? d.midX() : by.x, by == null ? d.midZ() + 1 : by.z) * OPEN : 0;
        d.lastUse = raid.time; d.lastUser = by == null ? 0 : by.id;
        swings++;
        wake(d);
        raid.emitSound(d.midX(), d.y + 1, d.midZ(), d.kind == METAL ? 12 : 9, opening ? "door_open" : "door_close", by == null ? 0 : by.id, false);
        return null;
    }

    /** Opens a door for someone passing through it (agents on a path): no-op if it is already open or opening. */
    public boolean open(Door d, Actor by) {
        if (d == null || d.destroyed || Math.abs(d.target) > 0.1f) return true;
        return use(d, by) == null;
    }

    /** A vehicle door is asked to stand open or pull shut. A door that is gone answers at once. */
    public void swing(Door d, boolean open) {
        if (d == null || d.destroyed) return;
        d.target = open ? d.maxOpen : 0;
        d.lastUse = raid.time;
        swings++;
        wake(d);
    }

    /** A boot or a shoulder against the latch side: how doors are forced without a key. Returns true when it gave. */
    public boolean kick(Door d, Actor by) {
        if (d == null || d.destroyed || !d.solid) return true;
        float nx = by == null ? 0 : d.midX() - by.x, nz = by == null ? 1 : d.midZ() - by.z;
        float force = d.kind == METAL ? 26 : d.kind == GLASS ? 60 : 44;
        raid.emitSound(d.midX(), d.y + 1, d.midZ(), d.kind == METAL ? 34 : 26, "door_kick", by == null ? 0 : by.id, false);
        damage(d, R_LATCH, force, nx, nz, by == null ? 0 : by.id, "blunt");
        return d.destroyed || d.latchBroken;
    }

    // ------------------------------------------------------------------ damage
    /** Region of the leaf a world-space point lies on. A pair is one object: hinges at both jambs, the latch where the leaves meet. */
    public static int regionAt(Door d, float hx, float hy, float hz) {
        float along = d.alongX ? hx - d.x : hz - d.z, rel = hy - d.y;
        if (d.span == 2) {
            float t = along / 2f;
            if (t < 0.1f || t > 0.9f) return R_HINGE;
            if (Math.abs(t - 0.5f) < 0.12f && rel > 0.6f && rel < 1.4f) return R_LATCH;
            return rel >= 1f ? R_UPPER : R_LOWER;
        }
        float t = d.hinge == 0 ? along : 1 - along;
        if (t < 0.2f) return R_HINGE;
        if (t > 0.78f && rel > 0.6f && rel < 1.4f) return R_LATCH;
        return rel >= 1f ? R_UPPER : R_LOWER;
    }

    /** A projectile, a blade or debris struck the shut leaf at a point. */
    public void hit(Door d, float hx, float hy, float hz, float dmg, float dirX, float dirZ, int by, String kind) {
        if (d == null || d.destroyed) return;
        damage(d, regionAt(d, hx, hy, hz), dmg, dirX, dirZ, by, kind);
    }

    /**
     * A hit on a vehicle door, located on the leaf by the owner: u runs 0 at the hinge to 1 at the latch edge, v 0 at
     * the sill to 1 at the top. The same regions, the same latch and hinge failures as any other door.
     */
    public void hitMobile(Door d, float u, float v, float dmg, float dirX, float dirZ, int by, String kind) {
        if (d == null || d.destroyed) return;
        int region = u < 0.22f ? R_HINGE : u > 0.76f && v > 0.3f && v < 0.7f ? R_LATCH : v >= 0.5f ? R_UPPER : R_LOWER;
        damage(d, region, dmg, dirX, dirZ, by, kind);
    }

    /** Blast: every region at once, scaled by distance by the caller. */
    public void blast(Door d, float dmg, float dirX, float dirZ, int by) {
        if (d == null || d.destroyed) return;
        for (int r = 0; r < 4 && !d.destroyed; r++) damage(d, r, dmg * (r == R_HINGE ? 0.9f : 0.6f), dirX, dirZ, by, "blast");
    }

    private void damage(Door d, int region, float dmg, float dirX, float dirZ, int by, String kind) {
        // what a material shrugs off: small arms barely mark plate, anything breaks glass, car doors are thin sheet
        float taken = dmg * (d.kind == METAL ? ("bullet".equals(kind) ? 0.22f : "blast".equals(kind) ? 1f : 0.55f) : d.kind == CAR ? ("bullet".equals(kind) ? 0.7f : 1f) : 1f);
        d.region[region] += taken;
        d.health -= taken;
        d.hits++; hitsTaken++;
        // the blow shoves the leaf: a latched door shudders, an unlatched one swings
        float push = d.mobile ? Math.min(3f, taken * 0.03f) : (dirX * -d.dirZ() + dirZ * d.dirX()) * Math.min(6f, taken * 0.08f);
        d.vel += push;
        wake(d);
        if (d.kind == CAR) {
            // sheet metal does not shatter: it is torn off its hinges, or the catch lets go and it swings
            if (!d.hingeBroken && (d.region[R_HINGE] >= d.maxHealth * 0.40f || d.health <= 0)) { detach(d, dirX, dirZ, by); return; }
            if (!d.latchBroken && d.region[R_LATCH] >= d.maxHealth * 0.28f) { d.latchBroken = true; d.forcedAt = raid.time; d.target = d.maxOpen * 0.55f; d.vel += 3f; forced++; raid.emitSound(d.mx, d.my, d.mz, 24, "door_break", by, false); }
            return;
        }
        if (d.kind == GLASS && d.health < d.maxHealth * 0.5f || d.health <= 0) { shatter(d, dirX, dirZ, by); return; }
        if (!d.hingeBroken && d.region[R_HINGE] >= d.maxHealth * 0.45f) { detach(d, dirX, dirZ, by); return; }
        if (!d.latchBroken && d.region[R_LATCH] >= d.maxHealth * 0.30f) {
            // forced: the latch has gone, the door swings away from the blow and will not stay shut again
            d.latchBroken = true; d.locked = false; d.forcedAt = raid.time;
            float dir = push != 0 ? Math.signum(push) : away(d, d.midX() - dirX, d.midZ() - dirZ);
            d.target = dir * OPEN * 0.9f;
            d.vel += dir * 5.5f;
            forced++;
            raid.emitSound(d.midX(), d.y + 1, d.midZ(), 30, "door_break", by, false);
        }
    }

    private void addSlab(Slab s) {
        slabs.add(s);
        for (int i = 0; i < slabs.size() && slabs.size() > MAX_SLABS; i++) if (slabs.get(i).rest || i > 40) { slabs.remove(i); i--; }
    }

    /** The leaf comes off its hinges and goes where the blow sends it (both leaves of a pair). */
    private void detach(Door d, float dirX, float dirZ, int by) {
        d.hingeBroken = true; d.destroyed = true;
        detached++;
        float l = Math.max(0.2f, (float) Math.sqrt(dirX * dirX + dirZ * dirZ));
        if (d.mobile) {
            Slab s = new Slab();
            s.kind = CAR; s.w = d.mlen; s.h = d.mh; s.model = d.model; s.key = d.modelKey; s.vox = d.vox;
            s.x = d.mx; s.y = d.my; s.z = d.mz;
            s.vx = d.mvx + dirX / l * 2.4f; s.vz = d.mvz + dirZ / l * 2.4f; s.vy = d.mvy + 1.8f;
            s.yaw = d.myaw; s.spin = 1.8f + raid.rng.nextFloat() * 2; s.life = 90;
            addSlab(s);
            moving.remove(d);
            raid.emitSound(d.mx, d.my, d.mz, 34, "door_break", by, false);
            return;
        }
        for (int leaf = 0; leaf < d.leaves(); leaf++) {
            Slab s = new Slab();
            s.kind = d.kind; s.level = d.damageLevel(); s.w = 1; s.h = 2;
            s.x = d.pivotX(leaf) + d.dirX(leaf) * 0.5f; s.y = d.y + 1; s.z = d.pivotZ(leaf) + d.dirZ(leaf) * 0.5f;
            s.vx = dirX / l * (3.2f + leaf * 0.5f); s.vz = dirZ / l * (3.2f + leaf * 0.5f); s.vy = 1.6f;
            s.yaw = (float) Math.atan2(-d.dirZ(leaf), d.dirX(leaf)); s.spin = leaf == 0 ? 2.4f : -2.1f;
            addSlab(s);
        }
        gone(d, by, "door_break");
    }

    /** The leaf breaks up: fragments and splinters where it stood; a glazed door goes as a sheet of shards. */
    private void shatter(Door d, float dirX, float dirZ, int by) {
        d.destroyed = true;
        shattered++;
        float l = Math.max(0.2f, (float) Math.sqrt(dirX * dirX + dirZ * dirZ));
        for (int k = 0; k < d.span; k++) {
            float ox = d.x + 0.5f + (d.alongX ? k : 0), oz = d.z + 0.5f + (d.alongX ? 0 : k);
            if (d.kind == GLASS) { shards(ox, d.y + 1f, oz, 1f, 2f, d.alongX, dirX / l, dirZ / l, 12); continue; }
            for (int i = 0; i < 4; i++) {
                Slab s = new Slab();
                s.kind = d.kind; s.level = 2; s.w = 0.45f; s.h = 0.5f + raid.rng.nextFloat() * 0.5f;
                s.x = ox + (raid.rng.nextFloat() - 0.5f) * 0.6f; s.y = d.y + 0.4f + i * 0.4f; s.z = oz + (raid.rng.nextFloat() - 0.5f) * 0.6f;
                s.vx = dirX / l * (2 + raid.rng.nextFloat() * 3) + (raid.rng.nextFloat() - 0.5f) * 2; s.vz = dirZ / l * (2 + raid.rng.nextFloat() * 3) + (raid.rng.nextFloat() - 0.5f) * 2; s.vy = 1 + raid.rng.nextFloat() * 2.5f;
                s.yaw = raid.rng.nextFloat() * 6.28f; s.spin = (raid.rng.nextFloat() - 0.5f) * 9; s.life = 45;
                addSlab(s);
            }
            dust(ox, d.y, oz, dirX / l, dirZ / l, d.kind == METAL ? 0xFF7C8088 : 0xFF7A5630, 22);
        }
        if (d.kind == GLASS) { setSolid(d, false); moving.remove(d); raid.glass.pane(d.midX(), d.y + 1, d.midZ(), by); raid.bus.emit(new Events.BlockDestroyed(d.x, d.y, d.z, d.closedBlock, by)); }
        else gone(d, by, "door_break");
    }

    private void dust(float x, float y, float z, float dirX, float dirZ, int col, int n) {
        for (int i = 0; i < n; i++) {
            Raid.Effect e = new Raid.Effect();
            e.x = x; e.y = y + raid.rng.nextFloat() * 2; e.z = z;
            e.vx = dirX * 3 + (raid.rng.nextFloat() - 0.5f) * 5; e.vy = raid.rng.nextFloat() * 4; e.vz = dirZ * 3 + (raid.rng.nextFloat() - 0.5f) * 5;
            e.life = e.max = 0.5f + raid.rng.nextFloat() * 0.6f; e.size = 0.05f + raid.rng.nextFloat() * 0.07f; e.color = col;
            raid.effects.add(e);
        }
    }

    /**
     * Glass breaking as glass does: a pane of the given size, centred at a point, comes apart into rigid shards that
     * are thrown the way the blow was going (and a little back toward it), tumble, hit the floor and stay there.
     * Used by glazed doors and by every window block that breaks ({@link #paneBroken}).
     */
    public void shards(float cx, float cy, float cz, float w, float h, boolean alongX, float dirX, float dirZ, int n) {
        Rng r = raid.rng;
        for (int i = 0; i < n; i++) {
            Slab s = new Slab();
            s.kind = SHARD; s.level = i % 3;
            s.w = 0.14f + r.nextFloat() * 0.26f; s.h = 0.16f + r.nextFloat() * 0.34f;
            float u = (r.nextFloat() - 0.5f) * w, v = (r.nextFloat() - 0.5f) * h;
            s.x = cx + (alongX ? u : 0); s.y = cy + v; s.z = cz + (alongX ? 0 : u);
            // most of the pane follows the blow; a few pieces drop straight down or fall back
            float carry = i % 5 == 0 ? -0.6f : 1.2f + r.nextFloat() * 3.2f;
            s.vx = dirX * carry + (r.nextFloat() - 0.5f) * 1.6f; s.vz = dirZ * carry + (r.nextFloat() - 0.5f) * 1.6f; s.vy = 0.4f + r.nextFloat() * 2.2f;
            s.yaw = (alongX ? 0 : 1.5708f) + (r.nextFloat() - 0.5f) * 0.8f;
            s.spin = (r.nextFloat() - 0.5f) * 12; s.life = 50 + r.nextFloat() * 20;
            addSlab(s);
        }
        // the fine stuff nobody could count: a glitter that is gone in a moment
        for (int i = 0; i < 10; i++) {
            Raid.Effect e = new Raid.Effect();
            e.x = cx; e.y = cy + (r.nextFloat() - 0.5f) * h; e.z = cz;
            e.vx = dirX * 3 + (r.nextFloat() - 0.5f) * 4; e.vy = r.nextFloat() * 2.5f; e.vz = dirZ * 3 + (r.nextFloat() - 0.5f) * 4;
            e.life = e.max = 0.35f + r.nextFloat() * 0.4f; e.size = 0.025f + r.nextFloat() * 0.03f; e.color = 0xFFD8EEF4;
            raid.effects.add(e);
        }
    }

    /** A window block has just broken at a cell: the pane leaves as shards, along the direction of whatever broke it. */
    public void paneBroken(int x, int y, int z, float dirX, float dirZ) {
        World w = raid.world;
        // the pane lies in the wall: along x when the wall continues to either side in x
        boolean alongX = w.solid(x - 1, y, z) || w.solid(x + 1, y, z) || !(w.solid(x, y, z - 1) || w.solid(x, y, z + 1));
        float l = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (l < 0.05f) { dirX = alongX ? 0 : 1; dirZ = alongX ? 1 : 0; l = 1; if (raid.rng.chance(0.5)) { dirX = -dirX; dirZ = -dirZ; } }
        shards(x + 0.5f, y + 0.5f, z + 0.5f, 0.9f, 0.9f, alongX, dirX / l, dirZ / l, 9);
        panes++;
    }

    private void gone(Door d, int by, String sound) {
        setSolid(d, false);
        moving.remove(d);
        raid.emitSound(d.midX(), d.y + 1, d.midZ(), 34, sound, by, false);
        raid.bus.emit(new Events.BlockDestroyed(d.x, d.y, d.z, d.closedBlock, by));
    }

    // ------------------------------------------------------------------ simulation
    void update(float dt) {
        frame++;
        bodies(dt);
        for (int i = moving.size() - 1; i >= 0; i--) {
            Door d = moving.get(i);
            if (d.destroyed) { moving.remove(i); continue; }
            float acc;
            if (!d.latchBroken) {
                // a door closer and a hand on the handle: critically damped toward where it was sent
                acc = 46f * (d.target - d.angle) - 11f * d.vel;
            } else {
                // no latch: it goes where it is pushed and drifts toward where it was left
                acc = 5f * (d.target - d.angle) - 2.4f * d.vel;
            }
            if (d.mobile) {
                d.vel += acc * dt;
                d.angle += d.vel * dt;
                if (d.angle > d.maxOpen) { d.angle = d.maxOpen; d.vel = -d.vel * 0.2f; }
                if (d.angle < 0) { d.angle = 0; d.vel = d.latchBroken ? -d.vel * 0.3f : 0; }
                if (Math.abs(d.vel) < 0.04f && Math.abs(d.target - d.angle) < 0.015f) { if (!d.latchBroken) d.angle = d.target; d.vel = 0; moving.remove(i); }
                continue;
            }
            d.vel += acc * dt;
            float na = d.angle + d.vel * dt;
            // the leaf may not come to within a hand of shut while a body is in the frame: it rests against them instead
            // (0.5 let it turn solid whenever the body was not dead centre, and the collision code then carried the
            // player up through the lintel one settle step at a time)
            boolean closing = !d.solid && Math.abs(na) < SOLID_BELOW + 0.04f && Math.abs(na) < Math.abs(d.angle);
            if (closing && occupied(d)) {
                na = (d.angle >= 0 ? 1 : -1) * (SOLID_BELOW + 0.04f);
                d.vel = 0; d.blockedUntil = raid.time + 0.5f;
                // nobody is holding the handle any more: it stays ajar until someone uses it again
                d.target = na;
            }
            d.angle = na;
            if (d.angle > OPEN + 0.08f) { d.angle = OPEN + 0.08f; d.vel = -d.vel * 0.25f; }
            if (d.angle < -OPEN - 0.08f) { d.angle = -OPEN - 0.08f; d.vel = -d.vel * 0.25f; }
            boolean solid = Math.abs(d.angle) < SOLID_BELOW;
            if (solid != d.solid) setSolid(d, solid);
            if (Math.abs(d.vel) < 0.04f && Math.abs(d.target - d.angle) < 0.015f) { if (!d.latchBroken) d.angle = d.target; d.vel = 0; moving.remove(i); }
        }
        for (int i = slabs.size() - 1; i >= 0; i--) {
            Slab s = slabs.get(i);
            s.life -= dt;
            if (s.life <= 0) { slabs.remove(i); continue; }
            if (s.rest) {
                // 1.2: a piece at rest is at rest on something. With two panes one above the other and the upper broken first,
                // its shards came to rest on the top edge of the lower - and stayed there, in the air, when that was shot out
                // from under them, because nothing at rest was ever looked at again. What it lies on is looked at now.
                if (raid.world.solid((int) Math.floor(s.x), (int) Math.floor(s.y - (s.kind == SHARD ? 0.02f : 0.07f) - 0.03f), (int) Math.floor(s.z))) continue;
                s.rest = false; s.vy = 0; s.spin = (raid.rng.nextFloat() - 0.5f) * 5; woken++;
            }
            s.vy -= 20 * dt;
            float nx = s.x + s.vx * dt, ny = s.y + s.vy * dt, nz = s.z + s.vz * dt;
            World w = raid.world;
            if (w.solid((int) Math.floor(nx), (int) Math.floor(s.y), (int) Math.floor(s.z))) { s.vx = -s.vx * 0.3f; nx = s.x; }
            if (w.solid((int) Math.floor(s.x), (int) Math.floor(s.y), (int) Math.floor(nz))) { s.vz = -s.vz * 0.3f; nz = s.z; }
            float half = s.kind == SHARD ? 0.02f : 0.07f;
            if (w.solid((int) Math.floor(nx), (int) Math.floor(ny - half), (int) Math.floor(nz))) {
                ny = (float) Math.floor(ny - half) + 1 + half;
                boolean hard = Math.abs(s.vy) > 3;
                if (hard) { if (s.kind == SHARD) raid.glass.shard(nx, ny, nz); else raid.emitSound(nx, ny, nz, 14, "debris", 0, false); }
                s.vy = s.kind == SHARD && s.vy < -4 ? -s.vy * 0.18f : 0; s.vx *= 0.55f; s.vz *= 0.55f; s.spin *= 0.5f;
                s.pitch += (1.5708f - s.pitch) * Math.min(1, dt * 14);       // it lies down flat
                if (Math.abs(s.vx) + Math.abs(s.vz) < 0.15f && s.pitch > 1.5f && s.vy == 0) { s.rest = true; s.pitch = 1.5708f; }
            } else s.pitch = Math.min(1.5708f, s.pitch + dt * 2.6f);
            s.yaw += s.spin * dt;
            s.x = nx; s.y = ny; s.z = nz;
        }
    }

    /** 1.2: pieces that were at rest and fell again because what they lay on went (gates); bodies a leaf has swept limb by limb; leaves a wedged body has stopped. */
    public int woken, limbsSwept, wedged;
    /** 1.2: the most speed a leaf gives a part of a body it sweeps (m/s). */
    public static float LIMB_SHOVE = 1.7f;

    /** How fast a swinging leaf can shove a body lying in its way (m/s), and how much of its swing the weight takes off it per second. */
    public static float BODY_SHOVE_MAX = 2.6f, BODY_DRAG = 5f;
    /** Times a leaf has pushed a body out of its swing (gates). */
    public int bodiesShoved;

    /** Any body, living or dead, whose box reaches into the door's cells: the leaf cannot become solid through it. */
    private boolean occupied(Door d) {
        for (Actor a : raid.actors) {
            if (a.tier != 0 && !a.isPlayer) continue;
            if (a.dead && a.ragdoll == null && a.deadTime > 1) continue;
            float reach = 0.5f + a.radius + 0.04f;
            if (a.y >= d.y + 1.95f || a.y + (a.dead ? 0.5f : a.height()) <= d.y) continue;
            for (int k = 0; k < d.span; k++) if (Math.abs(a.x - (d.x + 0.5f + (d.alongX ? k : 0))) < reach && Math.abs(a.z - (d.z + 0.5f + (d.alongX ? 0 : k))) < reach) return true;
        }
        return false;
    }

    /**
     * Bodies against open leaves. Whoever walks into a leaf swings it out of the way at the speed they are moving,
     * and it stays where they left it; a body lying in the swing stops it. This is contact, not use: nobody has to
     * press anything, and it is the same for the player, agents, the infected and the dead.
     */
    private void bodies(float dt) {
        if (byCell.isEmpty()) return;
        for (Actor a : raid.actors) {
            // (a body lying still is soon no longer one of the "active" - but while any door is moving, every body the physics
            // has is looked at: it was the corpse that had been lying longest that a door went through)
            if (a.ragdoll != null && (a.dead || a.vital != absentialis.entity.Vital.ACTIVE)) { if (!moving.isEmpty()) limbs(a.ragdoll, dt); continue; }
            if (a.tier != 0 && !a.isPlayer) continue;
            if (a.dead) continue;
            int ix = (int) Math.floor(a.x), iz = (int) Math.floor(a.z);
            for (int dy = 0; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                Door d = at(ix + dx, (int) Math.floor(a.y + 0.4f) + dy, iz + dz);
                if (d == null || d.destroyed || d.solid || d.pushFrame == frame) continue;
                if (a.y >= d.y + 1.95f || a.y + (a.dead ? 0.5f : a.height()) <= d.y) continue;
                if (push(d, a, dt)) d.pushFrame = frame;
            }
        }
        if (!raid.animals.all.isEmpty()) animals(dt);
    }

    private final Door[] swept = new Door[6];

    /**
     * 1.2: a leaf against a body the physics has (somebody dead, down or thrown). A body used to be a single point - where
     * its owner had been standing - with the radius of a standing person, so a door met a body lying across its swing only
     * if that one point happened to be in the way: it went through legs, arms and heads, and "did not always move" what lay
     * in front of it. The leaf is now met by every part of the body that is at its height. Each part in the way is moved
     * with the leaf, at the speed the leaf has at that distance from the hinge - so a door takes a leg round with it and
     * the body turns - and the weight of what it is moving comes off its swing. A part with a wall behind it cannot be
     * moved: the door stops against the body there, and stays where it was stopped.
     */
    private void limbs(absentialis.character.Ragdoll rag, float dt) {
        int nd = 0;
        for (int i = 0; i < absentialis.character.Ragdoll.N && nd < swept.length; i++) {
            int ix = (int) Math.floor(rag.x[i]), iy = (int) Math.floor(rag.y[i]), iz = (int) Math.floor(rag.z[i]);
            for (int dx = -1; dx <= 1 && nd < swept.length; dx++) for (int dz = -1; dz <= 1 && nd < swept.length; dz++) for (int dy = -1; dy <= 0; dy++) {
                Door d = at(ix + dx, iy + dy, iz + dz);
                if (d == null || d.destroyed || d.solid) continue;
                boolean seen = false;
                for (int k = 0; k < nd; k++) if (swept[k] == d) { seen = true; break; }
                if (!seen && nd < swept.length) swept[nd++] = d;
            }
        }
        for (int k = 0; k < nd; k++) {
            Door d = swept[k];
            swept[k] = null;
            float load = 0;
            boolean stopped = false, touched = false;
            for (int leaf = 0; leaf < d.leaves(); leaf++) {
                float ang = d.leafAngle(leaf), c = (float) Math.cos(ang), s = (float) Math.sin(ang);
                float lx = d.dirX(leaf) * c - d.dirZ(leaf) * s, lz = d.dirX(leaf) * s + d.dirZ(leaf) * c;
                float om = d.span == 2 && leaf == 1 ? -d.vel : d.vel;
                for (int i = 0; i < absentialis.character.Ragdoll.N; i++) {
                    if (rag.y[i] < d.y - 0.12f || rag.y[i] > d.y + 2.0f) continue;
                    float rx = rag.x[i] - d.pivotX(leaf), rz = rag.z[i] - d.pivotZ(leaf);
                    float u = Math.max(0, Math.min(1, rx * lx + rz * lz));
                    float px = rx - lx * u, pz = rz - lz * u, dist = (float) Math.sqrt(px * px + pz * pz), reach = (i == absentialis.character.Ragdoll.HEAD_P ? 0.2f : 0.13f) + 0.05f;
                    if (dist >= reach || u < 0.08f) continue;
                    float side = -rx * lz + rz * lx >= 0 ? 1 : -1, nx = -lz * side, nz = lx * side;
                    // how fast the leaf is going at this distance from the hinge, toward the part
                    float lvx = -lz * om * u, lvz = lx * om * u, closing = (lvx - rag.vx(i)) * nx + (lvz - rag.vz(i)) * nz;
                    touched = true;
                    if (closing <= 0.02f) continue;
                    boolean wall = raid.world.solid((int) Math.floor(rag.x[i] + nx * (reach + 0.12f)), (int) Math.floor(rag.y[i]), (int) Math.floor(rag.z[i] + nz * (reach + 0.12f)));
                    if (wall) { stopped = true; continue; }
                    // (no faster than a door pushes: a leaf that flings a body across a room is as wrong as one that goes through it)
                    float go = Math.min(closing, LIMB_SHOVE) * 0.7f;
                    rag.kick(i, nx * go, i == absentialis.character.Ragdoll.HEAD_P ? 0 : 0.04f * go, nz * go);
                    // and out of the leaf, if it is already in it
                    float pen = reach - dist;
                    if (pen > 0.01f) { rag.x[i] += nx * pen * 0.5f; rag.z[i] += nz * pen * 0.5f; }
                    load += absentialis.character.Ragdoll.MASS[i];
                }
            }
            if (!touched) continue;
            if (load > 0 || stopped) { rag.wake(); wake(d); }
            if (stopped && load < 20) {
                // wedged between the leaf and a wall: the door has come as far as it is coming
                d.vel = 0; d.target = d.angle; wedged++;
            } else if (load > 0) {
                d.vel *= Math.max(0, 1 - dt * BODY_DRAG * Math.min(2.4f, load / 16f));
                limbsSwept++; bodiesShoved++;
            }
        }
    }

    /** 1.2: animals and doors. One on its feet pushes a leaf that is ajar as a person does; a dead one is swept by it like any body. */
    private void animals(float dt) {
        for (Animals.Animal an : raid.animals.all) {
            if (an.flying || an.onCeiling) continue;
            int ix = (int) Math.floor(an.x), iy = (int) Math.floor(an.y + 0.2f), iz = (int) Math.floor(an.z);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 0; dy++) {
                Door d = at(ix + dx, iy + dy, iz + dz);
                if (d == null || d.destroyed || d.solid) continue;
                if (!an.dead) {
                    if (d.pushFrame == frame) continue;
                    an.body.x = an.x; an.body.y = an.y; an.body.z = an.z;
                    if (push(d, an.body, dt)) d.pushFrame = frame;
                    an.x = an.body.x; an.z = an.body.z;
                } else if (an.rag != null && Math.abs(d.vel) > 0.05f) {
                    absentialis.sydney.SoftBody b = an.rag;
                    for (int leaf = 0; leaf < d.leaves(); leaf++) {
                        float ang = d.leafAngle(leaf), c = (float) Math.cos(ang), s = (float) Math.sin(ang);
                        float lx = d.dirX(leaf) * c - d.dirZ(leaf) * s, lz = d.dirX(leaf) * s + d.dirZ(leaf) * c, om = d.span == 2 && leaf == 1 ? -d.vel : d.vel;
                        for (int i = 0; i < b.n; i++) {
                            if (b.y[i] < d.y - 0.1f || b.y[i] > d.y + 2.0f) continue;
                            float rx = b.x[i] - d.pivotX(leaf), rz = b.z[i] - d.pivotZ(leaf), u = Math.max(0, Math.min(1, rx * lx + rz * lz));
                            float px = rx - lx * u, pz = rz - lz * u, dist = (float) Math.sqrt(px * px + pz * pz), reach = b.radius[i] + 0.05f;
                            if (dist >= reach || u < 0.08f) continue;
                            float side = -rx * lz + rz * lx >= 0 ? 1 : -1, nx = -lz * side, nz = lx * side;
                            float closing = (-lz * om * u - b.vx(i)) * nx + (lx * om * u - b.vz(i)) * nz;
                            if (closing > 0.02f) { b.impulse(i, nx * Math.min(closing, 4f), 0, nz * Math.min(closing, 4f)); limbsSwept++; }
                        }
                    }
                }
            }
        }
    }

    private boolean push(Door d, Actor a, float dt) {
        boolean pushed = false;
        for (int leaf = 0; leaf < d.leaves(); leaf++) {
            float ang = d.leafAngle(leaf), c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            float lx = d.dirX(leaf) * c - d.dirZ(leaf) * s, lz = d.dirX(leaf) * s + d.dirZ(leaf) * c;
            float rx = a.x - d.pivotX(leaf), rz = a.z - d.pivotZ(leaf);
            float u = Math.max(0, Math.min(1, rx * lx + rz * lz));
            float px = rx - lx * u, pz = rz - lz * u, dist = (float) Math.sqrt(px * px + pz * pz), reach = a.radius + 0.07f;
            if (dist >= reach || u < 0.12f) continue;
            // which side of the leaf the body is on, along the leaf's normal (-lz, lx)
            float side = -rx * lz + rz * lx >= 0 ? 1 : -1;
            float nx = -lz * side, nz = lx * side;                       // from the leaf toward the body
            float closing = -(a.vx * nx + a.vz * nz);                    // how fast the body is coming at it
            // turning the leaf's own angle by -side moves it away from the body; the second leaf of a pair is mirrored
            float sgn = (d.span == 2 && leaf == 1 ? 1 : -1) * side;
            if (a.dead || a.vital != absentialis.entity.Vital.ACTIVE) {
                // 0.8: nobody is pushing, and the body is in the way of the swing - so the leaf moves the body. (0.7 stopped the
                // leaf dead against it and left the door where it stood: a body lying by a door meant that door could not
                // be opened again.) A dead weight slows the swing and is shoved along by it; the door ends up where it was sent.
                if (d.vel * sgn < 0) {
                    float sp = Math.min(BODY_SHOVE_MAX, Math.abs(d.vel) * Math.max(0.3f, u)) + 0.5f;
                    if (a.ragdoll != null) a.ragdoll.carry(nx * sp, 0.1f, nz * sp, Math.min(1, dt * 12));
                    else {
                        float step = sp * dt, tx = a.x + nx * step, tz = a.z + nz * step;
                        if (Physics.boxFree(raid.world, tx, a.y, tz, a.radius, a.height())) { a.x = tx; a.z = tz; }
                    }
                    d.vel *= Math.max(0, 1 - dt * BODY_DRAG);
                    bodiesShoved++;
                    wake(d);
                }
            } else {
                float want = sgn * Math.min(6f, Math.max(0.5f, closing + 0.4f) / Math.max(0.35f, u));
                d.vel += (want - d.vel) * Math.min(1, dt * 16);
                pushed = true;
                pushes++;
            }
            // the leaf is against its stop, or is being held: the body does not pass through it
            float pen = reach - dist;
            boolean atStop = Math.abs(d.angle) >= OPEN - 0.03f && d.angle * sgn > 0;
            if (!a.dead && pen > 0.02f && (atStop || closing < 0.2f)) {
                float tx = a.x + nx * pen * 0.6f, tz = a.z + nz * pen * 0.6f;
                if (Physics.boxFree(raid.world, tx, a.y, tz, a.radius, a.height())) { a.x = tx; a.z = tz; }
            }
        }
        if (pushed) {
            wake(d);
            // it stays where it was pushed to - but a push never latches a door shut on top of whoever is pushing
            float t = Math.max(-OPEN, Math.min(OPEN, d.angle + d.vel * 0.14f));
            float min = SOLID_BELOW + 0.14f;
            if (Math.abs(t) < min) t = (d.angle >= 0 ? 1 : -1) * min;
            d.target = t;
        }
        return pushed;
    }

    // ------------------------------------------------------------------ rays against open leaves
    /**
     * Distance along a ray to the first swung-open leaf it strikes, or -1. Shut doors are met through their voxel
     * cells; this covers the leaf standing open in a doorway or hanging off a broken latch.
     */
    public float ray(float ox, float oy, float oz, float dx, float dy, float dz, float len, Door[] hitOut) {
        float best = -1;
        if (byCell.isEmpty()) return -1;
        int x0 = (int) Math.floor(Math.min(ox, ox + dx * len)) - 1, x1 = (int) Math.floor(Math.max(ox, ox + dx * len)) + 1;
        int z0 = (int) Math.floor(Math.min(oz, oz + dz * len)) - 1, z1 = (int) Math.floor(Math.max(oz, oz + dz * len)) + 1;
        if ((x1 - x0) * (z1 - z0) > 400) return -1;
        int yc = (int) Math.floor(oy + dy * len * 0.5f);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = yc - 2; y <= yc + 1; y++) {
            Door d = at(x, y, z);
            if (d == null || d.destroyed || d.solid || d.y != y || d.x != x || d.z != z) continue;
            for (int leaf = 0; leaf < d.leaves(); leaf++) {
                // into the leaf's frame: origin at the hinge, x along the leaf as it now stands
                float ang = d.leafAngle(leaf), c = (float) Math.cos(ang), s = (float) Math.sin(ang);
                float lx = d.dirX(leaf) * c - d.dirZ(leaf) * s, lz = d.dirX(leaf) * s + d.dirZ(leaf) * c;
                float rx = ox - d.pivotX(leaf), rz = oz - d.pivotZ(leaf);
                float o1 = rx * lx + rz * lz, o2 = -rx * lz + rz * lx, d1 = dx * lx + dz * lz, d2 = -dx * lz + dz * lx;
                if (Math.abs(d2) < 1e-5f) continue;
                float t = -o2 / d2;
                if (t < 0 || t > len) continue;
                float u = o1 + d1 * t, v = oy + dy * t - d.y;
                if (u < 0 || u > 1 || v < 0 || v > 2) continue;
                if (best < 0 || t < best) { best = t; if (hitOut != null) hitOut[0] = d; }
            }
        }
        return best;
    }

    /** A round met an open leaf: damage where it struck. Returns the fraction of its energy that carries on. */
    public float strikeOpen(Door d, float hx, float hy, float hz, float dmg, float dirX, float dirZ, int by) {
        int region = R_LOWER;
        float bestOff = 9;
        for (int leaf = 0; leaf < d.leaves(); leaf++) {
            float ang = d.leafAngle(leaf), c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            float lx = d.dirX(leaf) * c - d.dirZ(leaf) * s, lz = d.dirX(leaf) * s + d.dirZ(leaf) * c;
            float rx = hx - d.pivotX(leaf), rz = hz - d.pivotZ(leaf);
            float u = rx * lx + rz * lz, off = Math.abs(-rx * lz + rz * lx), rel = hy - d.y;
            if (off >= bestOff || u < -0.05f || u > 1.05f) continue;
            bestOff = off;
            region = u < 0.2f ? R_HINGE : u > 0.78f && rel > 0.6f && rel < 1.4f ? R_LATCH : rel >= 1 ? R_UPPER : R_LOWER;
        }
        damage(d, region, dmg, dirX, dirZ, by, "bullet");
        return d.kind == METAL ? 0f : d.kind == GLASS ? 0.95f : 0.6f;
    }

    // ------------------------------------------------------------------ drawing
    private static int[] leafColours(int kind) {
        // (1.3: the pane of a glass door is as clear as a window pane - it was half opaque on both faces and read as a white board)
        return kind == METAL ? new int[]{0xFF6E747C, 0xFF575C63, 0xFFB8BCC2} : kind == GLASS ? new int[]{0xFF4A4F56, 0x38A8D0DC, 0xFFB8BCC2} : new int[]{0xFF7A5630, 0xFF5E4024, 0xFFC8B070};
    }

    /** The leaf as a voxel model: sixteen by thirty-two by two. Damage levels knock real holes in it. */
    static VoxModel leaf(int kind, int level) {
        VoxModel m = new VoxModel(16, 32, 2);
        if (kind == SHARD) {
            // a shard: an irregular wedge of pane, three outlines so no two neighbours match
            for (int x = 0; x < 16; x++) for (int y = 0; y < 32; y++) {
                float fx = x / 15f, fy = y / 31f;
                boolean in = level == 0 ? fy <= 1 - Math.abs(fx - 0.35f) * 1.6f : level == 1 ? fx <= 0.25f + fy * 0.75f && fy >= fx * 0.3f : fy >= Math.abs(fx - 0.6f) * 1.3f && fy < 0.92f;
                if (!in) continue;
                boolean edge = x == 0 || y == 0 || Rng.hash(x, y, level, 7) > 0.93f;
                m.set(x, y, 0, edge ? 0xC0E6F4F8 : 0x70A4CCD8);
            }
            return m;
        }
        int[] c = leafColours(kind);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 32; y++) for (int z = 0; z < 2; z++) {
            boolean frame = x == 0 || x == 15 || y == 0 || y == 31 || y == 15 || y == 16;
            int col = frame ? c[0] : c[1];
            if (kind == WOOD && !frame && (x == 4 || x == 11)) col = c[0];
            if (kind == METAL && !frame && y < 6) col = 0xFF4C5056;                       // kick plate
            if (kind == METAL && (x == 2 || x == 13) && y % 6 == 3) col = c[2];            // rivets
            if (kind == GLASS && !frame && y > 2) col = c[1];
            // a pane is one layer of glass in the middle of its frame, not a block of it (two faces of the same pane doubled it)
            if (kind == GLASS && !frame && z == 1) col = 0;
            m.set(x, y, z, col);
        }
        // handle on the latch side
        m.set(13, 15, 0, c[2]); m.set(13, 15, 1, c[2]); m.set(13, 16, 0, c[2]); m.set(13, 16, 1, c[2]);
        if (level > 0) {
            int holes = level == 1 ? 3 : 7;
            for (int i = 0; i < holes; i++) {
                int hx = 2 + (int) (Rng.hash(i, kind, level, 31) * 12), hy = 2 + (int) (Rng.hash(i, kind, level, 57) * 28), r = level == 1 ? 1 : 1 + (i & 1);
                for (int x = hx - r; x <= hx + r; x++) for (int y = hy - r; y <= hy + r; y++) if ((x - hx) * (x - hx) + (y - hy) * (y - hy) <= r * r + (i & 1)) { m.set(x, y, 0, 0); m.set(x, y, 1, 0); }
            }
        }
        return m;
    }

    private ModelRenderer.Mesh mesh(ModelRenderer mr, int kind, int level) {
        String key = "door:" + kind + ":" + level;
        ModelRenderer.Mesh me = mr.cached(key);
        return me != null ? me : mr.meshAt(key, leaf(kind, level), 0, 0, 1);
    }

    /** Draws every leaf and every fallen slab near the camera. */
    public void draw(ModelRenderer mr, Mat4 tmp, float camX, float camZ, java.util.function.Function<float[], Float> lightAt) {
        float[] pos = new float[3];
        float k = 1 / 16f;
        for (Door d : doors) {
            if (d.destroyed || d.mobile || Math.abs(d.x - camX) > 90 || Math.abs(d.z - camZ) > 90) continue;
            // light from the lit side of the doorway
            float nx = d.alongX ? 0 : 1, nz = d.alongX ? 1 : 0;
            pos[0] = d.midX() + nx; pos[1] = d.y + 1; pos[2] = d.midZ() + nz;
            float la = lightAt.apply(pos);
            pos[0] = d.midX() - nx; pos[2] = d.midZ() - nz;
            float light = Math.max(la, lightAt.apply(pos));
            mr.material(d.kind == METAL ? 0.45f : d.kind == GLASS ? 0.2f : 0.8f, d.kind == METAL ? 0.8f : 0f);
            for (int leaf = 0; leaf < d.leaves(); leaf++) {
                float ang = d.leafAngle(leaf), c = (float) Math.cos(ang), s = (float) Math.sin(ang);
                float lx = d.dirX(leaf) * c - d.dirZ(leaf) * s, lz = d.dirX(leaf) * s + d.dirZ(leaf) * c;
                tmp.identity().translate(d.pivotX(leaf), d.y, d.pivotZ(leaf)).rotateY((float) Math.atan2(-lz, lx)).scale(k, k, k);
                mr.draw(mesh(mr, d.kind, d.damageLevel()), tmp, light, 0xFFFFFFFF, 0);
            }
        }
        for (Slab sl : slabs) {
            if (Math.abs(sl.x - camX) > 70 || Math.abs(sl.z - camZ) > 70) continue;
            pos[0] = sl.x; pos[1] = sl.y + 0.3f; pos[2] = sl.z;
            int fade = sl.life < 2 ? fade(sl.tint, sl.life / 2) : sl.tint;
            if (sl.model != null) {
                mr.material(0.4f, 0.3f);
                ModelRenderer.Mesh me = mr.cached(sl.key);
                if (me == null) me = mr.meshAt(sl.key, sl.model, 0, 0, 0);
                tmp.identity().translate(sl.x, sl.y, sl.z).rotateY(sl.yaw).rotateX(sl.pitch).scale(sl.vox, sl.vox, sl.vox).translate(-sl.model.sx / 2f, -sl.model.sy / 2f, -sl.model.sz / 2f);
                mr.draw(me, tmp, lightAt.apply(pos), fade, 0);
                continue;
            }
            mr.material(sl.kind == METAL ? 0.45f : sl.kind == SHARD ? 0.08f : 0.8f, sl.kind == METAL ? 0.8f : 0f);
            tmp.identity().translate(sl.x, sl.y, sl.z).rotateY(sl.yaw).rotateX(sl.pitch).scale(k * sl.w, k * sl.h * 0.5f, k).translate(-8, -16, 0);
            mr.draw(mesh(mr, sl.kind, sl.level), tmp, lightAt.apply(pos), fade, 0);
        }
        mr.material();
    }

    private static int fade(int argb, float a) { return (argb & 0xFFFFFF) | ((int) (Math.max(0, Math.min(1, a)) * ((argb >>> 24) & 0xFF)) << 24); }
}
