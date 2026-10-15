package absentialis.neuralai.integration;

import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.scene.Raid;
import absentialis.world.World;

/**
 * Getting through a hole (0.7). The infected do not open doors, and until now a wall with a broken window in it
 * stopped them as surely as a wall without one. A one-block opening - a window with the glass out, a gap knocked in
 * brickwork, a hatch - is now a way in: an infected that is stopped against a wall while it is trying to get
 * somewhere looks along that wall for an opening one block square, and if the sill is no more than
 * {@link #MAX_SILL} blocks above its feet it hauls itself up, goes through on its belly and drops down the far side.
 *
 * A hole that has been boarded up (a Wooden Plank, [F] on the frame) is a wall again. The giants do not fit.
 * The climb is a real traverse of real positions - feet, sill, far side - taking as long as the height deserves; the
 * body can be shot the whole way through, and anything that knocks it down ends the climb where it is.
 */
public final class Breach {
    private Breach() {}

    /** The highest sill, in blocks above the feet, that can be climbed to (an opening above this is out of reach). */
    public static int MAX_SILL = 3;
    /** How far along the wall, either way, an opening is looked for; seconds per block of climb; seconds to cross the sill. */
    public static int REACH = 3;
    public static float SECONDS_PER_BLOCK = 0.42f, CROSS_SECONDS = 0.7f;
    /** Climbs started and finished (gates). */
    public static int started, finished;

    /** Archetypes too big for a one-block hole. */
    public static boolean fits(Actor a) { return a.radius <= 0.32f && a.stand <= 2.05f; }

    /**
     * A one-block opening at (x, y, z) going through a wall along (dx, dz): the cell is empty, there is wall above
     * and below it, and the cell beyond is empty too - a way through, not an alcove.
     */
    public static boolean hole(World w, int x, int y, int z, int dx, int dz) {
        if (w.solid(x, y, z) || !w.solid(x, y + 1, z) || !w.solid(x, y - 1, z)) return false;
        // one block wide: wall on both sides of it, across the direction of travel
        if (!w.solid(x + dz, y, z + dx) || !w.solid(x - dz, y, z - dx)) return false;
        return !w.solid(x + dx, y, z + dz);
    }

    /** How far ahead a wall is looked for, and how far along it an opening, when choosing a way in from a distance. */
    public static int SEEK_AHEAD = 12, SEEK_ALONG = 6;
    public static int sought;

    /**
     * An opening in the wall whose first cell is (wx, wz), going (dx, dz), reachable from feet at fy: searched outward
     * along the wall to either side. Fills the climb of the actor (where to stand, the sill, where it comes out).
     */
    private static boolean find(World w, Actor a, int wx, int wz, int dx, int dz, int fy, int along) {
        for (int side = 0; side <= along; side++) for (int sgn = -1; sgn <= 1; sgn += 2) {
            if (side == 0 && sgn > 0) continue;
            int hx = wx + dz * side * sgn, hz = wz + dx * side * sgn;
            // room to stand in front of it
            int sx = hx - dx, sz = hz - dz;
            if (w.solid(sx, fy, sz) || w.solid(sx, fy + 1, sz) || !w.solid(sx, fy - 1, sz)) continue;
            for (int up = 0; up <= MAX_SILL; up++) {
                int hy = fy + up;
                if (!hole(w, hx, hy, hz, dx, dz)) continue;
                // the space to climb in must be clear from the feet to the sill
                boolean shaft = true;
                for (int y = fy; y <= hy && shaft; y++) if (w.solid(sx, y, sz)) shaft = false;
                if (!shaft) continue;
                // where it comes out: the first floor below the far side of the opening, not more than a storey down
                int ox = hx + dx, oz = hz + dz, oy = hy;
                while (oy > hy - 5 && !w.solid(ox, oy - 1, oz)) oy--;
                if (!w.solid(ox, oy - 1, oz) || w.solid(ox, oy, oz)) continue;
                float[] b = a.breach;
                b[0] = sx + 0.5f; b[1] = fy; b[2] = sz + 0.5f;
                b[3] = hx + 0.5f; b[4] = hy; b[5] = hz + 0.5f;
                b[6] = ox + 0.5f; b[7] = oy; b[8] = oz + 0.5f;
                return true;
            }
        }
        return false;
    }

    private static void begin(Raid r, Actor a) {
        float[] b = a.breach;
        int up = Math.round(b[4] - b[1]), dx = Math.round(b[3] - b[0]), dz = Math.round(b[5] - b[2]);
        a.breachDur = 0.35f + up * SECONDS_PER_BLOCK + CROSS_SECONDS + Math.max(0, b[4] - b[7]) * 0.12f;
        a.breachT = a.breachDur;
        a.holeUntil = -99;
        a.stuckTime = 0; a.path = null; a.hasVia = false;
        a.yaw = (float) Math.atan2(-dx, -dz);
        if (a.anim != null) a.anim.play("vault", Math.min(1.2f, a.breachDur));
        r.emitSound(a.x, a.y + 1, a.z, 9, "debris", a.id, false);
        started++;
    }

    /** Looks for an opening this body can reach from where it is stuck, and starts through it. dirX/dirZ is the way it was trying to go. */
    public static boolean start(Raid r, Actor a, float dirX, float dirZ) {
        if (!fits(a)) return false;
        int dx = Math.abs(dirX) >= Math.abs(dirZ) ? (dirX > 0 ? 1 : -1) : 0, dz = dx == 0 ? (dirZ > 0 ? 1 : -1) : 0;
        int fx = (int) Math.floor(a.x), fy = Math.round(a.y), fz = (int) Math.floor(a.z);
        if (!find(r.world, a, fx + dx, fz + dz, dx, dz, fy, REACH)) return false;
        begin(r, a);
        return true;
    }

    /**
     * From a distance: is there a wall between this body and where it wants to be, with an opening in it that it
     * could use? If so the opening is remembered and walked to (the far side of a window is usually a good deal
     * nearer through the window than round by the door). Returns true when it has one in mind.
     */
    public static boolean seek(Raid r, Actor a, float tx, float tz) {
        if (!fits(a)) return false;
        World w = r.world;
        float ddx = tx - a.x, ddz = tz - a.z;
        int dx = Math.abs(ddx) >= Math.abs(ddz) ? (ddx > 0 ? 1 : -1) : 0, dz = dx == 0 ? (ddz > 0 ? 1 : -1) : 0;
        int fx = (int) Math.floor(a.x), fy = Math.round(a.y), fz = (int) Math.floor(a.z);
        int far = (int) Math.min(SEEK_AHEAD, Math.abs(dx != 0 ? ddx : ddz));
        for (int k = 1; k <= far; k++) {
            int wx = fx + dx * k, wz = fz + dz * k;
            if (!w.solid(wx, fy, wz) && !w.solid(wx, fy + 1, wz)) { if (!w.solid(wx, fy - 1, wz)) return false; continue; }    // open ground so far (a drop ends the search)
            if (!find(w, a, wx, wz, dx, dz, fy, SEEK_ALONG)) return false;
            a.holeUntil = r.time + 8;
            sought++;
            return true;
        }
        return false;
    }

    /** Walking to the opening it has picked: true while that is what it is doing (and it starts the climb on arrival). */
    public static boolean approach(Raid r, Actor a, float speed, float dt) {
        float[] b = a.breach;
        float dx = b[0] - a.x, dz = b[2] - a.z, d = (float) Math.sqrt(dx * dx + dz * dz);
        // boarded up since, or it is no longer on that floor
        if (Math.abs(a.y - b[1]) > 0.6f || !hole(r.world, (int) Math.floor(b[3]), Math.round(b[4]), (int) Math.floor(b[5]), Math.round(b[3] - b[0]), Math.round(b[5] - b[2]))) { a.holeUntil = -99; return false; }
        if (d < 0.45f) { if (a.onGround && r.time >= a.jumpAt) begin(r, a); else Steering.stop(a); return true; }
        a.yaw = Steering.turn(a.yaw, (float) Math.atan2(-dx, -dz), dt * 5);
        a.vx = dx / d * speed; a.vz = dz / d * speed;
        if (a.stuckTime > 1.5f) a.holeUntil = -99;
        return true;
    }

    /** One frame of a climb in progress: to the wall, up it, through on the belly, and down. */
    public static void climb(Raid r, Actor a, float dt) {
        float[] b = a.breach;
        if (r.time < a.staggerUntil - 0.3f || a.ragdoll != null) { a.breachT = 0; a.crawling = false; return; }       // knocked out of it
        a.breachT -= dt;
        float t = 1 - Math.max(0, a.breachT) / Math.max(0.01f, a.breachDur);
        float rise = Math.max(0.01f, b[4] - b[1]) * SECONDS_PER_BLOCK + 0.35f, cross = CROSS_SECONDS, total = a.breachDur;
        float at = t * total;
        a.vx = a.vz = a.vy = 0; a.pvx = a.pvz = 0;
        a.onGround = true;
        if (at < rise) {
            // against the wall, hauling up
            float k = at / rise;
            a.x += (b[0] - a.x) * Math.min(1, dt * 8); a.z += (b[2] - a.z) * Math.min(1, dt * 8);
            a.y = b[1] + (b[4] - b[1]) * k * k;
            a.crawling = false;
        } else if (at < rise + cross) {
            // on its front in the opening: the only way a body fits a one-block hole
            float k = (at - rise) / cross;
            a.crawling = true;
            a.x = b[0] + (b[6] - b[0]) * k; a.z = b[2] + (b[8] - b[2]) * k;
            a.y = b[4];
        } else {
            float k = Math.min(1, (at - rise - cross) / Math.max(0.05f, total - rise - cross));
            a.x = b[6]; a.z = b[8];
            a.y = b[4] + (b[7] - b[4]) * k;
            a.crawling = k < 0.5f && b[4] - b[7] < 0.5f;
        }
        if (a.breachT <= 0) {
            a.breachT = 0;
            a.crawling = false;
            a.x = b[6]; a.y = b[7]; a.z = b[8];
            if (!Physics.boxFree(r.world, a.x, a.y, a.z, a.radius, a.stand)) a.y = (float) Math.ceil(a.y);
            a.jumpAt = r.time + 0.6f;
            finished++;
        }
    }
}
