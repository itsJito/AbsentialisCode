package absentialis.entity;

import absentialis.world.Block;
import absentialis.world.World;

/**
 * Voxel collision for upright AABB actors: per-axis sweep against solid blocks, 1-block auto-step
 * when grounded (curbs, stairs), ladder climbing, and line-of-sight / ray queries.
 */
public final class Physics {
    private Physics() {}

    public static final float GRAVITY = 22f;
    /** Take-off speed of a standing jump: clears one block (7.2^2 / 2g = 1.18). Shared by the player and every agent. */
    public static final float JUMP = 7.2f;
    /**
     * 0.6: walking into a block no longer lifts a body onto it. Only stair blocks ({@link Block#STEP}) are climbed by
     * walking; anything else one block high has to be jumped, by the player and by agents alike. True restores 0.5.
     */
    public static boolean AUTO_STEP_ALL = false;

    /** True when every solid cell the body would meet at foot level is a stair tread (so walking carries it up). */
    public static boolean stairAt(World w, float x, float y, float z, float r) {
        if (AUTO_STEP_ALL) return true;
        int x0 = (int) Math.floor(x - r), x1 = (int) Math.floor(x + r - 1e-4f), z0 = (int) Math.floor(z - r), z1 = (int) Math.floor(z + r - 1e-4f), by = (int) Math.floor(y);
        boolean any = false;
        for (int bx = x0; bx <= x1; bx++) for (int bz = z0; bz <= z1; bz++) {
            if (!w.solid(bx, by, bz)) continue;
            if (!w.block(bx, by, bz).is(Block.STEP)) return false;
            any = true;
        }
        return any;
    }

    /** A one-block ledge that is not a stair stands in the way of a body moving along (dx, dz): it has to be jumped. */
    public static boolean ledgeAhead(World w, Actor a, float dx, float dz) {
        float l = (float) Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-4f) return false;
        float nx = a.x + dx / l * (a.radius + 0.25f), nz = a.z + dz / l * (a.radius + 0.25f);
        if (boxFree(w, nx, a.y, nz, a.radius, a.height())) return false;
        return !stairAt(w, nx, a.y, nz, a.radius) && boxFree(w, nx, a.y + 1.01f, nz, a.radius, a.height()) && boxFree(w, a.x, a.y + 1.01f, a.z, a.radius, a.height());
    }

    // ------------------------------------------------------------------ 0.9: pulling up onto a ledge
    /** The lowest and highest ledge a person can pull up onto, in blocks above the feet (one block is a jump; three is a block taller than the person). */
    public static final int MANTLE_MIN = 2, MANTLE_MAX = 3;
    /** Seconds a pull-up takes: a base, and more for each block of height. */
    public static float MANTLE_BASE = 0.5f, MANTLE_PER_BLOCK = 0.27f;

    /**
     * A ledge ahead of a body that it could pull itself up onto: a wall in front whose top is two or three blocks above
     * its feet - up to a block taller than the body itself - with room on top to stand (or at least crouch) and nothing
     * overhead in the way of going up. Returns where its feet would end up {x, y, z}, or null. dx, dz is the way it faces.
     */
    public static float[] ledge(World w, Actor a, float dx, float dz) {
        float l = (float) Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-4f) return null;
        dx /= l; dz /= l;
        int feet = (int) Math.floor(a.y + 0.05f);
        // the wall has to be right there: within a short reach of the chest
        for (float reach = a.radius + 0.2f; reach <= a.radius + 0.75f; reach += 0.25f) {
            int fx = (int) Math.floor(a.x + dx * reach), fz = (int) Math.floor(a.z + dz * reach);
            if (fx == (int) Math.floor(a.x) && fz == (int) Math.floor(a.z)) continue;
            for (int up = MANTLE_MIN; up <= MANTLE_MAX; up++) {
                int top = feet + up;
                if (!w.solid(fx, top - 1, fz) || w.solid(fx, top, fz)) continue;                 // the top of something, with air on it
                Block under = w.block(fx, top - 1, fz);
                if (under == null || under.is(Block.HAZARD) || under.is(Block.DOOR) || under.shape != absentialis.world.Shapes.CUBE && absentialis.world.Shapes.hideHeight(under.shape) < 0.9f) continue;
                if (!boxFree(w, fx + 0.5f, top, fz + 0.5f, Math.min(a.radius, 0.3f), a.stand * Actor.CROUCH_H)) continue;   // room to be on it
                if (!boxFree(w, a.x, a.y + 0.1f, a.z, a.radius * 0.9f, up + a.stand * 0.55f)) continue;                   // room to go up
                return new float[]{fx + 0.5f, top, fz + 0.5f};
            }
            if (w.solid(fx, feet + 1, fz) || w.solid(fx, feet, fz)) break;                          // that was the wall: nothing behind it is in reach
        }
        return null;
    }

    /** Begins a pull-up to a place found by {@link #ledge}. */
    /** 1.1: how far below a ledge the feet of somebody hanging from it are, how long it takes to get there, and how long the legs are as a pendulum. */
    public static float HANG_REACH = 2.05f, HANG_IN = 0.22f, HANG_LEG = 0.85f;

    /**
     * 1.1: takes hold of a ledge and hangs from it - the first half of a climb that used to be one movement. The body is
     * carried to where the arms are straight under the lip, clear of the face of the wall, and stays there: {@link #pullUp}
     * is the second half, {@link #letGo} is changing your mind.
     */
    public static void hang(Actor a, float[] to) {
        float speed = (float) Math.sqrt(a.vx * a.vx + a.vz * a.vz);
        a.mFromX = a.x; a.mFromY = a.y; a.mFromZ = a.z;
        a.mToX = to[0]; a.mToY = to[1]; a.mToZ = to[2];
        // off the face: the body's own width clear of the block the ledge is the top of, on whichever side it came from
        float px = a.x - to[0], pz = a.z - to[2];
        a.hangX = a.x; a.hangZ = a.z;
        if (Math.abs(px) > Math.abs(pz)) a.hangX = to[0] + Math.signum(px) * (0.5f + a.radius + 0.03f); else a.hangZ = to[2] + Math.signum(pz) * (0.5f + a.radius + 0.03f);
        a.hangY = Math.max(a.y, to[1] - HANG_REACH);
        a.hanging = true; a.hangT = 0; a.mantleT = 0;
        a.hangSwing = 0; a.hangVel = Math.min(3.2f, 0.6f + speed * 0.55f + Math.max(0, -a.vy) * 0.2f);
        a.vx = a.vy = a.vz = 0; a.pvx = a.pvz = 0;
        a.crouch = false; a.prone = false; a.sprinting = false;
        a.yaw = (float) Math.atan2(-(to[0] - a.x), -(to[2] - a.z));
        if (a.faceSet) a.faceYaw = a.yaw;
    }

    /** One frame of hanging: held where the hands have it, no gravity, the legs swinging down to rest. True while it lasts. */
    public static boolean hangStep(World w, Actor a, float dt) {
        if (!a.hanging) return false;
        if (a.dead || a.ragdoll != null) { a.hanging = false; a.climbing = false; return false; }
        // the ledge has gone from under the hands (shot away, a door opened): there is nothing to hang from
        if (!w.solid((int) Math.floor(a.mToX), (int) Math.floor(a.mToY) - 1, (int) Math.floor(a.mToZ))) { letGo(a); return false; }
        a.hangT += dt;
        float k = Math.min(1, a.hangT / HANG_IN);
        k = k * k * (3 - 2 * k);
        a.x = a.mFromX + (a.hangX - a.mFromX) * k; a.z = a.mFromZ + (a.hangZ - a.mFromZ) * k; a.y = a.mFromY + (a.hangY - a.mFromY) * k;
        a.vx = a.vy = a.vz = 0;
        a.onGround = false; a.climbing = true; a.fallSpeed = 0;
        // the legs: a damped pendulum about the hips
        a.hangVel += (-(GRAVITY / HANG_LEG) * a.hangSwing - 1.9f * a.hangVel) * dt;
        a.hangSwing = Math.max(-0.7f, Math.min(0.7f, a.hangSwing + a.hangVel * dt));
        if (a.faceSet) a.faceYaw = a.yaw;
        return true;
    }

    /** The second half: up and over, from the hang. */
    public static void pullUp(Actor a) {
        if (!a.hanging) return;
        a.hanging = false;
        mantle(a, new float[]{a.mToX, a.mToY, a.mToZ});
    }

    /** Lets go of the ledge: the body is in the air again and falls. */
    public static void letGo(Actor a) { a.hanging = false; a.climbing = false; a.mantleT = 0; a.onGround = false; }

    public static void mantle(Actor a, float[] to) {
        a.mFromX = a.x; a.mFromY = a.y; a.mFromZ = a.z;
        a.mToX = to[0]; a.mToY = to[1]; a.mToZ = to[2];
        a.mantleDur = MANTLE_BASE + MANTLE_PER_BLOCK * Math.max(1, to[1] - a.y);
        a.mantleT = a.mantleDur;
        a.vx = a.vy = a.vz = 0; a.pvx = a.pvz = 0;
        a.crouch = false; a.prone = false; a.sprinting = false;
        a.yaw = (float) Math.atan2(-(to[0] - a.x), -(to[2] - a.z));
        // (1.0: the body squares up to what it is climbing - in third person its facing is its own, and it used to keep it)
        if (a.faceSet) a.faceYaw = a.yaw;
    }

    /**
     * One frame of a pull-up: hands on the edge and the body hauled straight up the face of the wall, then a knee over and
     * onto the top. The body is carried along that line - there is no gravity and no steering while it lasts. Returns
     * true while it is going on (the caller does nothing else with the body that frame).
     */
    public static boolean mantleStep(World w, Actor a, float dt) {
        if (a.mantleT <= 0) return false;
        if (a.dead || a.ragdoll != null) { a.mantleT = 0; a.climbing = false; return false; }
        a.mantleT = Math.max(0, a.mantleT - dt);
        float t = 1 - a.mantleT / Math.max(0.01f, a.mantleDur);
        float up = Math.min(1, t / 0.68f), over = Math.max(0, (t - 0.66f) / 0.34f);
        up = up * up * (3 - 2 * up); over = over * over * (3 - 2 * over);
        // 1.1: straight up the face while going up, and not a step toward it until the feet are level with the top. (The body
        // used to be carried a third of the way in while it was still below the edge: taken at a walk, with the body already
        // against the wall, that put it inside the block it was climbing.)
        float hx = a.mFromX, hz = a.mFromZ;
        a.x = a.mFromX + (hx - a.mFromX) * up + (a.mToX - hx) * over;
        a.z = a.mFromZ + (hz - a.mFromZ) * up + (a.mToZ - hz) * over;
        a.y = a.mFromY + (a.mToY - a.mFromY) * up;
        a.vx = a.vy = a.vz = 0;
        a.onGround = false; a.climbing = true; a.fallSpeed = 0;
        if (a.faceSet) a.faceYaw = a.yaw;
        if (a.mantleT <= 0) {
            a.x = a.mToX; a.y = a.mToY; a.z = a.mToZ;
            a.climbing = false; a.onGround = true;
            // on top there may be only room to crouch
            if (!bodyFree(w, a.x, a.y, a.z, a.radius, a.stand)) a.crouch = true;
        }
        return true;
    }

    // ------------------------------------------------------------------ 1.0: stairs
    /**
     * How high the walking surface of a stair cell stands under a point: the cell's floor before its first tread, its top
     * past its last, and a line through the treads between (the body rides the line - a quarter of a block of rise for
     * every quarter of run - so feet, camera and aim go up a staircase smoothly while the eye sees the steps).
     */
    public static float stairHeight(World w, int bx, int by, int bz, float px, float pz) { return stairHeight(w, bx, by, bz, px, pz, 0.3f); }

    /**
     * The same for a body of radius r: the surface is read a little ahead of its middle, toward the high side, so that it
     * has reached the height of whatever the flight leads onto by the time its leading edge gets there (read at the
     * middle, a body arrived at the top landing a few centimetres short of it and stopped against its edge).
     */
    public static float stairHeight(World w, int bx, int by, int bz, float px, float pz, float r) {
        int d = absentialis.world.Shapes.stairDir(w, bx, by, bz);
        int dx = absentialis.world.Shapes.STAIR_DX[d], dz = absentialis.world.Shapes.STAIR_DZ[d];
        float t = (dx != 0 ? (dx > 0 ? px - bx : bx + 1 - px) : (dz > 0 ? pz - bz : bz + 1 - pz)) + Math.max(0, r - 0.1f);
        if (t <= 0) return by;
        return by + Math.min(1f, t + 0.125f);
    }

    /**
     * {@link #boxFree} for a body, with stairs being stairs: a stair cell stops a body only where the treads under the
     * body's middle stand higher than its feet. Everything that moves on legs is tested with this.
     */
    public static boolean bodyFree(World w, float x, float y, float z, float r, float h) {
        int x0 = (int) Math.floor(x - r), x1 = (int) Math.floor(x + r - 1e-4f);
        int y0 = (int) Math.floor(y), y1 = (int) Math.floor(y + h - 1e-4f);
        int z0 = (int) Math.floor(z - r), z1 = (int) Math.floor(z + r - 1e-4f);
        for (int bx = x0; bx <= x1; bx++) for (int by = y0; by <= y1; by++) for (int bz = z0; bz <= z1; bz++) {
            if (!w.solid(bx, by, bz)) continue;
            if (w.block(bx, by, bz).stair()) { if (y + 2e-3f < stairHeight(w, bx, by, bz, x, z, r)) return false; }
            else return false;
        }
        return true;
    }

    /** How far a body at (x, y, z) has to rise to stand on the stairs it overlaps there; 0 when it need not, -1 when something that is not a stair is in its way. */
    private static float stairRise(World w, float x, float y, float z, float r, float h) { return stairRise(w, x, y, z, r, h, STAIR_LIP); }

    private static float stairRise(World w, float x, float y, float z, float r, float h, float lip) {
        int x0 = (int) Math.floor(x - r), x1 = (int) Math.floor(x + r - 1e-4f);
        int y0 = (int) Math.floor(y), y1 = (int) Math.floor(y + h - 1e-4f);
        int z0 = (int) Math.floor(z - r), z1 = (int) Math.floor(z + r - 1e-4f);
        float rise = 0;
        for (int bx = x0; bx <= x1; bx++) for (int by = y0; by <= y1; by++) for (int bz = z0; bz <= z1; bz++) {
            if (!w.solid(bx, by, bz)) continue;
            if (!w.block(bx, by, bz).stair()) {
                // 1.3: the top of a block the feet are a hair below (the fill under the next tread of a flight, met as the body
                // comes off the last of the ramp a few centimetres short of the tread's height) is stepped onto like a tread -
                // this one lip stopped everyone dead half way up every flight built on solid fill
                if (by == y0 && by + 1 - y <= lip && (by + 1 > y1 || !w.solid(bx, by + 1, bz) || w.block(bx, by + 1, bz).stair())) { rise = Math.max(rise, by + 1 - y); continue; }
                return -1;
            }
            rise = Math.max(rise, stairHeight(w, bx, by, bz, x, z, r) - y);
        }
        return rise;
    }

    /** 1.3: how far below the top of a block the feet may be and still be lifted onto it while walking stairs. */
    private static final float STAIR_LIP = 0.08f;

    /** The most a body is carried up in one step of the simulation by walking onto stairs (two treads and a little). */
    private static final float STAIR_LIFT = 0.62f;

    /** Walking into stairs: true when the body could be carried up onto them at (nx, nz), which is then done. */
    private static boolean climbStair(World w, Actor a, float nx, float nz, float r, float h) {
        // 1.3: off a flight, onto the floor beside it: a tread is a ramp, so part way along the top one the feet are still a
        // little under the landing - stepping across onto it is a step like any other (up to half a block)
        float lip = !Float.isNaN(stairBelow(w, a.x, a.y + 0.05f, a.z, r)) ? 0.5f : STAIR_LIP;
        float rise = stairRise(w, nx, a.y, nz, r, h, lip);
        if (rise <= 0 || rise > STAIR_LIFT) return false;
        rise += 3e-3f;
        if (!bodyFree(w, nx, a.y + rise, nz, r, h)) return false;
        a.y += rise; a.x = nx; a.z = nz; a.stepped += rise;
        return true;
    }

    /** 1.3: true when a body standing at (x, y, z) has a stair under its feet (on a flight, not beside one). */
    public static boolean onStairs(World w, float x, float y, float z) { return !Float.isNaN(stairBelow(w, x, y + 0.05f, z, 0.3f)); }

    /** The surface a body's middle is over, if it is a stair within reach below its feet: its height, or NaN. */
    private static float stairBelow(World w, float x, float y, float z, float r) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int by = (int) Math.floor(y - 1e-3f); by >= (int) Math.floor(y - 0.75f); by--) {
            if (!w.solid(bx, by, bz)) continue;
            Block b = w.block(bx, by, bz);
            return b.stair() ? stairHeight(w, bx, by, bz, x, z, r) : Float.NaN;
        }
        return Float.NaN;
    }

    public static boolean boxFree(World w, float x, float y, float z, float r, float h) {
        int x0 = (int) Math.floor(x - r), x1 = (int) Math.floor(x + r - 1e-4f);
        int y0 = (int) Math.floor(y), y1 = (int) Math.floor(y + h - 1e-4f);
        int z0 = (int) Math.floor(z - r), z1 = (int) Math.floor(z + r - 1e-4f);
        for (int bx = x0; bx <= x1; bx++) for (int by = y0; by <= y1; by++) for (int bz = z0; bz <= z1; bz++) if (w.solid(bx, by, bz)) return false;
        return true;
    }

    public static boolean inClimb(World w, float x, float y, float z) {
        // feet, waist and a little below the feet: the climb continues until the body has cleared the top rung,
        // so a ladder that ends at a roof edge delivers you onto the roof instead of dropping you back down it
        for (float dy : new float[]{-0.6f, 0f, 0.9f}) {
            Block b = w.block((int) Math.floor(x), (int) Math.floor(y + dy), (int) Math.floor(z));
            if (b != null && b.is(Block.CLIMB)) return true;
        }
        return false;
    }

    /**
     * Moves the actor by its velocity * dt with collision; updates onGround. Large steps are split
     * so no axis advances more than 0.4 blocks per sub-step (no tunnelling through 1-block floors
     * at terminal velocity or under coarse regional-tier ticks). Returns true if blocked horizontally.
     */
    public static boolean move(World w, Actor a, float dt) {
        float vmax = Math.max(Math.abs(a.vx), Math.max(Math.abs(a.vz), Math.abs(a.vy) + GRAVITY * dt));
        int n = Math.max(1, Math.min(64, (int) Math.ceil(vmax * dt / 0.4f)));
        boolean blocked = false;
        boolean ground = false;
        for (int i = 0; i < n; i++) { blocked |= step(w, a, dt / n); ground |= a.onGround; }
        a.onGround = ground && a.vy <= 0;
        return blocked;
    }

    private static boolean step(World w, Actor a, float dt) {
        float h = a.height(), r = a.radius;
        boolean blocked = false;
        // ladders: holding forward while inside a climbable column moves up
        boolean onLadder = inClimb(w, a.x, a.y, a.z);
        a.climbing = onLadder && a.wantClimb;
        if (a.climbing) {
            a.vy = a.climbDir * 3.2f;
            // going down stops at the floor instead of grinding into it
            if (a.climbDir < 0 && !bodyFree(w, a.x, a.y - 0.1f, a.z, r, h)) a.vy = 0;
            a.vx *= 0.55f; a.vz *= 0.55f;
        } else if (onLadder && !a.onGround && a.vy <= 0) {
            a.vy = Math.max(a.vy - GRAVITY * dt, -1.4f);   // hands on the rails: a controlled slide, never a free fall
            a.fallSpeed = 0;
        } else a.vy -= GRAVITY * dt;
        a.vy = Math.max(a.vy, -40);
        // 1.0: stairs are walked. Going up, the body is carried up the line of the treads as it moves onto them (grounded or
        // already on them: a jump onto a staircase lands on it like anywhere else). Going down, the feet follow the treads
        // instead of stepping off each one into the air. Nothing else is ever stepped onto: a block in the way is jumped.
        boolean wasGround = a.onGround, mayClimb = wasGround || a.vy <= 0.5f && !Float.isNaN(stairBelow(w, a.x, a.y + 0.3f, a.z, r));
        // X
        float nx = a.x + a.vx * dt;
        if (bodyFree(w, nx, a.y, a.z, r, h)) a.x = nx;
        else if (!(mayClimb && !a.climbing && climbStair(w, a, nx, a.z, r, h))) { blocked = true; a.vx = 0; }
        // Z
        float nz = a.z + a.vz * dt;
        if (bodyFree(w, a.x, a.y, nz, r, h)) a.z = nz;
        else if (!(mayClimb && !a.climbing && climbStair(w, a, a.x, nz, r, h))) { blocked = true; a.vz = 0; }
        // down a flight: the feet stay on it
        if (wasGround && a.vy <= 0 && !a.climbing) {
            float s = stairBelow(w, a.x, a.y, a.z, r);
            if (!Float.isNaN(s) && s < a.y - 2e-3f && a.y - s <= STAIR_LIFT && bodyFree(w, a.x, s + 3e-3f, a.z, r, h)) { a.stepped -= a.y - s; a.y = s + 3e-3f; a.vy = 0; }
        }
        // Y
        float ny = a.y + a.vy * dt;
        a.onGround = false;
        if (bodyFree(w, a.x, ny, a.z, r, h)) a.y = ny;
        else {
            if (a.vy < 0) {
                a.onGround = true;
                // (1.1: the hardest of the step's landings is kept - a later sub-step of the same frame, already standing, used to
                // overwrite the speed of the real one with next to nothing, so half of all hard landings cost nothing)
                a.fallSpeed = Math.max(a.fallSpeed, -a.vy);
                // onto stairs: stand on their line where the body came down; onto anything else: on top of it
                float rise = stairRise(w, a.x, ny, a.z, r, h);
                if (rise > 0 && rise <= 1.01f && bodyFree(w, a.x, ny + rise + 3e-3f, a.z, r, h)) a.y = ny + rise + 3e-3f;
                else {
                    a.y = (float) Math.floor(ny) + 1f;
                    if (!bodyFree(w, a.x, a.y, a.z, r, h)) a.y = (float) Math.ceil(ny);
                }
            }
            a.vy = 0;
        }
        if (!bodyFree(w, a.x, a.y, a.z, r, h)) depenetrate(w, a, r, h);
        return blocked;
    }

    /**
     * The body overlaps something solid (geometry changed round it: a door shut, a block was placed). 0.5 lifted it
     * 5 cm per step until it was free - which carried anyone standing in a closing doorway up through the lintel and
     * onto the floor above. Feet a little way into the floor are still lifted; anything else is pushed out sideways
     * to the nearest free spot, and a body that is truly boxed in stays where it is.
     */
    private static void depenetrate(World w, Actor a, float r, float h) {
        float rise = stairRise(w, a.x, a.y, a.z, r, h);
        if (rise > 0 && rise <= 1.01f && bodyFree(w, a.x, a.y + rise + 3e-3f, a.z, r, h)) { a.y += rise + 3e-3f; return; }
        float frac = a.y - (float) Math.floor(a.y);
        if (frac > 0.7f && bodyFree(w, a.x, (float) Math.ceil(a.y), a.z, r, h)) { a.y = (float) Math.ceil(a.y); return; }
        if (bodyFree(w, a.x, a.y + 0.12f, a.z, r, h)) { a.y += 0.05f; return; }
        for (float d = 0.1f; d <= 1.01f; d += 0.1f) for (int k = 0; k < 8; k++) {
            float ox = OFFX[k] * d, oz = OFFZ[k] * d;
            if (bodyFree(w, a.x + ox, a.y, a.z + oz, r, h)) { a.x += ox; a.z += oz; return; }
        }
    }

    private static final float[] OFFX = {1, -1, 0, 0, 0.7071f, 0.7071f, -0.7071f, -0.7071f}, OFFZ = {0, 0, 1, -1, 0.7071f, -0.7071f, 0.7071f, -0.7071f};

    /** True when no opaque block lies between the points (sight checks). */
    public static boolean los(World w, float ax, float ay, float az, float bx, float by, float bz) { return clearLine(w, ax, ay, az, bx, by, bz, false); }

    /**
     * 0.9: one exact walk along a line, cell by cell (the lines of sight used to be sampled every 40 cm, which let a
     * line slip past the corner of a block - so somebody behind the end of a wall, or crouched behind one crate, was
     * seen "through" it). Every cell the line passes through is looked at, once. The cell the line starts in and the
     * cell it ends in are not obstacles: whoever is looking stands in one and what is looked at is in the other.
     *
     * With {@code full} it is what an eye can see through: a full block stops it unless it is glass or mesh; a shut
     * door stops it; furniture stops it at the heights where it is solid (a desk, a barrel, a sofa's seat - see
     * {@link absentialis.world.Shapes#hides}); four cells of leaves stop it. Without, only opaque blocks do.
     */
    private static boolean clearLine(World w, float ax, float ay, float az, float bx, float by, float bz, boolean full) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.5f) return true;
        int x = (int) Math.floor(ax), y = (int) Math.floor(ay), z = (int) Math.floor(az);
        final int ex = (int) Math.floor(bx), ey = (int) Math.floor(by), ez = (int) Math.floor(bz);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        // t runs 0..1 along the line
        float tdx = dx == 0 ? Float.MAX_VALUE : Math.abs(1 / dx), tdy = dy == 0 ? Float.MAX_VALUE : Math.abs(1 / dy), tdz = dz == 0 ? Float.MAX_VALUE : Math.abs(1 / dz);
        float tmx = dx == 0 ? Float.MAX_VALUE : (dx > 0 ? (x + 1 - ax) : (ax - x)) * tdx, tmy = dy == 0 ? Float.MAX_VALUE : (dy > 0 ? (y + 1 - ay) : (ay - y)) * tdy, tmz = dz == 0 ? Float.MAX_VALUE : (dz > 0 ? (z + 1 - az) : (az - z)) * tdz;
        int leaves = 0;
        float tIn = 0;
        for (int guard = (int) (len * 3) + 8; guard > 0; guard--) {
            float tOut = Math.min(tmx, Math.min(tmy, tmz));
            if (tIn > 0 && !(x == ex && y == ey && z == ez)) {
                int id = w.get(x, y, z);
                if (id != 0) {
                    Block b = Block.ALL[id];
                    if (b.opaque()) return false;
                    if (full && b.solid() && b.mat != absentialis.world.Material.GLASS) {
                        if (b.mat == absentialis.world.Material.FOLIAGE) { if (++leaves >= 4) return false; }   // canopy: a few cells of it and nothing can be made out through it
                        else if (b.is(Block.DOOR)) return false;
                        else if (b.shape != absentialis.world.Shapes.CUBE) {
                            float y0 = ay + dy * tIn - y, y1 = ay + dy * Math.min(1, tOut) - y;
                            if (absentialis.world.Shapes.hides(b.shape, Math.min(y0, y1), Math.max(y0, y1))) return false;
                        }
                        else if (!b.is(Block.CUTOUT)) return false;                                            // a full block that is not mesh or grating
                    }
                }
            }
            if (tOut >= 1 || x == ex && y == ey && z == ez) return true;
            tIn = tOut;
            if (tmx <= tmy && tmx <= tmz) { x += sx; tmx += tdx; }
            else if (tmy <= tmz) { y += sy; tmy += tdy; }
            else { z += sz; tmz += tdz; }
        }
        return true;
    }

    /**
     * Sight, as the AI's eyes use it (0.7). {@link #los} stops only at opaque blocks, which let people see through a
     * hedge as if it were air. Here anything that fills its cell and is not glass stops the line: walls, shut doors,
     * hedges, hay, sandbags. Tree leaves and tall grass do not stop it outright - they are counted by
     * {@link #foliage} and each one takes something off how much can be made out.
     */
    public static boolean sight(World w, float ax, float ay, float az, float bx, float by, float bz) { return clearLine(w, ax, ay, az, bx, by, bz, true); }

    /** Cells of leaves, hedge or tall growth a line of sight passes through (each one hides a little more of what is behind it). */
    public static int foliage(World w, float ax, float ay, float az, float bx, float by, float bz) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) (len * 2f), n = 0, lx = Integer.MIN_VALUE, ly = 0, lz = 0;
        for (int i = 1; i < steps; i++) {
            float t = i / (float) steps;
            int x = (int) Math.floor(ax + dx * t), y = (int) Math.floor(ay + dy * t), z = (int) Math.floor(az + dz * t);
            if (x == lx && y == ly && z == lz) continue;
            int id = w.get(x, y, z);
            if (id != 0 && Block.ALL[id].mat == absentialis.world.Material.FOLIAGE) { n++; lx = x; ly = y; lz = z; }
        }
        return n;
    }

    /** Fraction of the path that is clear (0..1) - used for sound occlusion. */
    public static float openness(World w, float ax, float ay, float az, float bx, float by, float bz) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(2, (int) (len / 1.5f)), solid = 0;
        for (int i = 1; i < steps; i++) {
            float t = i / (float) steps;
            if (w.solid((int) Math.floor(ax + dx * t), (int) Math.floor(ay + dy * t), (int) Math.floor(az + dz * t))) solid++;
        }
        return 1f - Math.min(1f, solid / 6f);
    }

    /** Voxel ray cast: returns {x,y,z,face} of the first solid/interactive block within maxDist, or null. */
    public static int[] ray(World w, float ox, float oy, float oz, float dx, float dy, float dz, float maxDist, boolean interactOnly) {
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        float tdx = Math.abs(1 / (dx == 0 ? 1e-6f : dx)), tdy = Math.abs(1 / (dy == 0 ? 1e-6f : dy)), tdz = Math.abs(1 / (dz == 0 ? 1e-6f : dz));
        float tmx = (dx > 0 ? (x + 1 - ox) : (ox - x)) * tdx, tmy = (dy > 0 ? (y + 1 - oy) : (oy - y)) * tdy, tmz = (dz > 0 ? (z + 1 - oz) : (oz - z)) * tdz;
        int face = -1;
        float t = 0;
        while (t <= maxDist) {
            int id = w.get(x, y, z);
            if (id != 0) {
                Block b = Block.ALL[id];
                if (interactOnly ? b.is(Block.INTERACT) || b.is(Block.CONTAINER) : b.solid()) return new int[]{x, y, z, face};
                if (interactOnly && b.opaque()) return null;
            }
            if (tmx < tmy && tmx < tmz) { x += sx; t = tmx; tmx += tdx; face = sx > 0 ? 1 : 0; }
            else if (tmy < tmz) { y += sy; t = tmy; tmy += tdy; face = sy > 0 ? 3 : 2; }
            else { z += sz; t = tmz; tmz += tdz; face = sz > 0 ? 5 : 4; }
        }
        return null;
    }
}
