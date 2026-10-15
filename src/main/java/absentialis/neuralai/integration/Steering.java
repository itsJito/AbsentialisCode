package absentialis.neuralai.integration;

import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.scene.Raid;

/**
 * How a body gets from where it is to where it was told to go (0.8: this is the steering the old AI carried, now the
 * body side of NeuralAI's bridge and nothing else). No decision is made here: a destination and a speed come in, and
 * what goes out is a heading and a velocity for the voxel controller - straight when the way is open, otherwise the
 * next cell of a grid path, with long trips riding the street graph. Doors on the way are opened by people and not by
 * the dead; a ledge is jumped, once; a ladder is climbed; a locked door between an agent and its quarry is kicked
 * when its side allows the noise; and the dead take the short way through a hole in a wall (see {@link Breach}).
 */
public final class Steering {
    private Steering() {}

    public static float wrapPi(float d) { while (d > Math.PI) d -= Math.PI * 2; while (d < -Math.PI) d += Math.PI * 2; return d; }

    /** Radians a second a body comes round to its heading, and how far out it starts easing down for where it is going (0.9). */
    public static final float TURN_RATE = 9f, ARRIVE = 2.4f;

    public static void stop(Actor a) { a.vx *= 0.5f; a.vz *= 0.5f; }

    /** Steering with simple obstacle avoidance; long detours handled by walking the street graph. */
    /** 1.1, the spin governor: how far a body may turn (radians, summed) within {@link #SPIN_RADIUS} metres and {@link #SPIN_WINDOW} seconds before it is a turning loop, and how long it then stands. */
    public static float SPIN_LIMIT = 7.2f, SPIN_RADIUS = 1.4f, SPIN_WINDOW = 3.5f, SPIN_HOLD = 0.55f;
    /** Turning loops broken since the game started (gates). */
    public static int spinBreaks;

    /**
     * 1.1. Whatever the cause - a waypoint that alternates, a goal inside its own turning circle, a crowd round a body it
     * cannot get into, two side-steps that undo each other - a turning loop looks the same from outside: the body turns
     * and turns and goes nowhere. That is measured here, at the one place every body's heading is set: turning is summed
     * while the body stays within a stride of where the sum began. Past a full turn and a bit, the loop is broken - the
     * route is thrown away and planned again by the long way, the side-step is dropped, the body stands for half a second
     * squared up to where it was going, and it is reported stuck so that whoever sent it can think again.
     */
    private static boolean governed(Raid r, Actor a, float yawBefore, float tx, float tz, float dt) {
        if (r.time - a.spinAt > SPIN_WINDOW || a.distXZ(a.spinX, a.spinZ) > SPIN_RADIUS) { a.spinAt = r.time; a.spinX = a.x; a.spinZ = a.z; a.spinAcc = 0; }
        a.spinAcc += Math.abs(wrapPi(a.yaw - yawBefore));
        if (a.spinAcc < SPIN_LIMIT) return false;
        a.spinAcc = 0; a.spinAt = r.time; a.spins++; spinBreaks++;
        a.path = null; a.pathAt = -99; a.hasVia = false; a.detourOff = 0; a.detourUntil = r.time + SPIN_HOLD;
        a.partialUntil = r.time + 8f;
        a.holdUntil = r.time + SPIN_HOLD;
        a.stuckTime = Math.max(a.stuckTime, 1.3f);
        stop(a);
        return true;
    }

    /** The path says the next cell is a step up that is not a stair (set by {@link #waypoint}): the only time a ledge is jumped. */
    private static boolean wpUp, wpHold;

    public static void goTo(Raid r, Actor a, float tx, float tz, float speed, float dt) {
        boolean otherFloor = !Float.isNaN(a.goalY) && a.goalYAt == r.time && Math.abs(a.goalY - a.y) > 1.6f;
        if (a.distXZ(tx, tz) < 0.3f && !otherFloor) { stop(a); return; }
        // 1.1: breaking a turning loop - stand, squared up to where it was going, and then go again by a new route
        if (r.time < a.holdUntil) { stop(a); a.vx = a.vz = 0; a.yaw = turn(a.yaw, (float) Math.atan2(-(tx - a.x), -(tz - a.z)), dt * 3f); return; }
        final float yawBefore = a.yaw;
        // 0.7: the dead take the short way. With a wall between it and where it is going, an infected looks for an opening it
        // can climb through before it thinks about walking round to a door
        if (a.infected() && a.tier == 0 && a.breachT <= 0) {
            if (r.time < a.holeUntil) { if (Breach.approach(r, a, speed, dt)) return; }
            else if (r.time >= a.holeScanAt && a.onGround) {
                a.holeScanAt = r.time + 0.8f + (a.id % 7) * 0.05f;
                float dd = a.distXZ(tx, tz);
                if (dd > 1.5f && dd < 40 && !r.world.nav.clear(a.x, a.y, a.z, tx, tz, false) && Breach.seek(r, a, tx, tz) && Breach.approach(r, a, speed, dt)) return;
            }
        }
        // route: straight when the way is open, otherwise the next cell of a grid path (long trips ride the waypoint graph)
        float[] wp = waypoint(r, a, tx, tz);
        float dx = wp[0] - a.x, dz = wp[1] - a.z, d = (float) Math.sqrt(dx * dx + dz * dz);
        // (1.1: with no way on from here it stands where the way ran out - and is "stuck", so that whoever sent it thinks again)
        if (wpHold) { a.stuckTime += dt * 2f; stop(a); a.yaw = turn(a.yaw, (float) Math.atan2(-(tx - a.x), -(tz - a.z)), dt * 2.5f); return; }
        if (d < 0.05f) { stop(a); return; }
        float want = (float) Math.atan2(-dx, -dz);
        // 1.0, getting off an obstacle. The side-step used to be chosen afresh every frame the body was "stuck" and dropped the
        // frame it was not - so a body against a corner turned away, came unstuck, turned back, stuck again: the turning on
        // the spot of 0.9. A side-step is now chosen once and kept for most of a second, whichever way the stuck timer goes;
        // each body prefers its own side (two meeting in a doorway do not mirror each other); and one that is still stuck after
        // that asks for a new route instead of trying the same wall again.
        if (a.stuckTime > 0.5f && r.time >= a.detourUntil) {
            float sign = (a.id & 1) == 0 ? 1 : -1;
            a.detourOff = 0;
            for (float off : new float[]{0.8f * sign, -0.8f * sign, 1.5f * sign, -1.5f * sign, 2.3f * sign}) {
                float h = want + off;
                float px = a.x - (float) Math.sin(h) * 1.1f, pz = a.z - (float) Math.cos(h) * 1.1f;
                // 1.3: and something to stand on there - a side-step off the edge of a flight or a landing is a fall
                int gy = (int) Math.floor(a.y + 0.05f);
                boolean footing = r.world.solid((int) Math.floor(px), gy - 1, (int) Math.floor(pz)) || r.world.solid((int) Math.floor(px), gy, (int) Math.floor(pz));
                if (footing && (Physics.bodyFree(r.world, px, a.y, pz, a.radius, 1.7f) || Physics.bodyFree(r.world, px, a.y + 0.6f, pz, a.radius, 1.7f))) { a.detourOff = off; break; }
            }
            a.detourUntil = r.time + 0.75f;
            if (a.stuckTime > 1.4f) { a.path = null; a.pathAt = -99; a.partialUntil = r.time + 10f; }
            if (a.stuckTime > 3) { a.hasGoal = false; a.stuckTime = 0; }
        }
        if (r.time < a.detourUntil && a.detourOff != 0) want += a.detourOff;
        // 0.7: a wall with a hole in it is not a wall to the dead. Stopped against one while going somewhere, an infected
        // looks for a one-block opening it can reach and climbs through it.
        if (a.infected() && a.stuckTime > 0.3f && a.onGround && a.breachT <= 0 && r.time >= a.jumpAt && Breach.start(r, a, dx / d, dz / d)) return;
        // 0.9: a body used to turn at a fixed rate while always moving at full speed along its heading - so anything inside its
        // turning circle (a cover point a metre to the side of a sprinting man) was orbited instead of reached: the "running in
        // circles" of 0.8. Now it turns faster, it slows by how far its heading is off the way it wants to go, it eases down as it
        // arrives, and when what it wants is closer than it could turn onto, it simply steps there.
        a.yaw = turn(a.yaw, want, dt * TURN_RATE);
        if (a.tier == 0 && governed(r, a, yawBefore, tx, tz, dt)) return;
        float off = Math.abs(wrapPi(want - a.yaw));
        float sp = speed * (off > 1.9f ? 0.25f : Math.max(0.35f, (float) Math.cos(off)));
        float left = a.distXZ(tx, tz);
        if (left < ARRIVE) sp = Math.min(sp, Math.max(1.3f, left * 2.8f));
        float heading = off > 0.3f && d < Math.max(1.2f, sp / TURN_RATE * 2.4f) ? want : a.yaw;
        a.vx = (float) -Math.sin(heading) * sp;
        a.vz = (float) -Math.cos(heading) * sp;
        // 0.6: nobody is lifted onto a block by walking at it. A ledge in the way is jumped - once, with a pause before
        // the next - exactly as the player has to; stairs are simply walked.
        // 1.1: and only where the route says so. A block one high that merely happens to be in front of a body - a desk it is
        // walking past, a crate at the side of a corridor - used to be jumped onto because it could be: indoors, people
        // hopped across the furniture. Now a ledge is jumped when the planned route goes up it (or, with no route to be
        // had and the body stuck fast, as the last thing left to try).
        if (a.onGround && !a.climbing && r.time >= a.jumpAt && a.vital == absentialis.entity.Vital.ACTIVE && !a.crawling && (wpUp || a.path == null && a.stuckTime > 0.9f) && Physics.ledgeAhead(r.world, a, dx, dz)) {
            a.vy = Physics.JUMP * ("tank".equals(a.archetype) ? 1.12f : 1f);
            a.onGround = false;
            a.jumpAt = r.time + (a.infected() ? 0.9f : 1.1f);
            a.crouch = false;
            // carry the run-up over the edge
            a.vx = dx / d * Math.max(speed, 2.2f); a.vz = dz / d * Math.max(speed, 2.2f);
            a.pvx = a.vx; a.pvz = a.vz;
            if (a.anim != null) a.anim.play("jump", 0.5f);
        }
    }

    private static final float[] WP = new float[2];

    /** A route has come back (from the engine, or from the search here): the body takes it, and what follows from where it ends is decided. */
    private static void planned(Raid r, Actor a, int[] path, float gx, float gz, boolean doors) {
        a.path = path;
        a.pathI = 0; a.pathAt = r.time; a.pathTX = gx; a.pathTZ = gz; a.pathNav = r.world.navVersion;
        // did it get there? If the route ends well short of where it was going, the next one goes by the streets
        if (a.path != null && !a.hasVia) { int pn = a.path.length / 3; float ex = a.path[(pn - 1) * 3] + 0.5f - gx, ez = a.path[(pn - 1) * 3 + 2] + 0.5f - gz; if (ex * ex + ez * ez > 3.5f * 3.5f) a.partialUntil = r.time + 12f; }
        else if (a.path == null && a.tier == 0) a.partialUntil = r.time + 12f;
        // the route stops short of where it was going, at a locked door: force it if this is that kind of moment
        if (doors && a.path != null && !a.mate()) forceEntry(r, a, gx, gz);
    }

    /** Where to head right now to reach (tx, tz): the target itself, or the next point of the agent's path. */
    private static float[] waypoint(Raid r, Actor a, float tx, float tz) {
        absentialis.world.Nav nav = r.world.nav;
        boolean doors = !a.infected();
        WP[0] = tx; WP[1] = tz;
        wpUp = false; wpHold = false;
        float d = a.distXZ(tx, tz);
        if (!a.onGround && a.path == null) return WP;
        // 0.9: somewhere on another floor is never "straight ahead", however clear the ground between is
        boolean otherFloor = !Float.isNaN(a.goalY) && a.goalYAt == r.time && Math.abs(a.goalY - a.y) > 1.6f;
        if (!otherFloor && d < 16 && nav.clearBody(a.x, a.y, a.z, tx, tz, false, a.radius * 0.9f)) { a.path = null; a.hasVia = false; return WP; }
        float gx = tx, gz = tz;
        // 1.0: the street graph is used for anything far - and for anything a grid route has just failed to reach (a search
        // that runs out stops at the cell nearest the goal as the crow flies, which is usually the wrong side of a building:
        // a body sent back there again and again was "stuck pathfinding")
        if (d > 44 || r.time < a.partialUntil && d > 9) {
            if (!a.hasVia || r.time - a.routeAt > 5 || a.distXZ(a.viaX, a.viaZ) < 5) {
                float[] v = nav.via(a.x, a.z, tx, tz, 30);
                a.routeAt = r.time;
                a.hasVia = v != null;
                if (v != null) { a.viaX = v[0]; a.viaZ = v[1]; }
            }
            if (a.hasVia) { gx = a.viaX; gz = a.viaZ; }
        } else a.hasVia = false;
        boolean moved = Math.abs(gx - a.pathTX) + Math.abs(gz - a.pathTZ) > 3;
        boolean stale = a.path == null ? r.time - a.pathAt > 0.6f : moved || r.time - a.pathAt > 4 || a.stuckTime > 1.2f && r.time - a.pathAt > 1;
        // the world changed along the way (a wall down, a door forced or blown): the old route is no longer trusted
        if (!stale && a.path != null && r.world.navTouched(a.pathNav, a.path, a.pathI)) stale = true;
        // 1.2: a route asked of the engine and not yet back is waited for - but not for ever: if nothing has run the engine's
        // tick since (a body steered outside an operation's own update), the request is dropped and the search made here
        boolean ticking = r.world.link != null && r.time - r.linkFlushedAt < 0.1f;
        if (a.pathPending > -50) { if (!ticking) { a.pathPending = -99; a.pathToken++; absentialis.world.NativeLink.stalls++; stale = true; } else stale = false; }
        if (stale && nav.budget > 0) {
            nav.budget--;
            int expand = a.tier == 1 ? 900 : a.mate() ? 9000 : r.time < a.partialUntil ? 7000 : 3600;
            boolean floor = otherFloor && !a.hasVia;
            absentialis.world.NativeLink link = r.world.link;
            if (link != null && ticking) {
                // 1.2: the search is the engine's. It is asked for now, with every other route wanted this tick, and is in
                // hand before this body is next steered; until then it keeps to the route it had (or waits, if it had none)
                final int token = ++a.pathToken;
                final float fgx = gx, fgz = gz;
                final boolean fdoors = doors;
                a.pathPending = r.time;
                a.pathAt = r.time; a.pathTX = gx; a.pathTZ = gz;
                link.requestPath(a.x, a.y, a.z, gx, floor ? a.goalY : Float.NaN, gz, expand, doors, cells -> { if (a.pathToken == token) { a.pathPending = -99; planned(r, a, cells, fgx, fgz, fdoors); } });
            } else planned(r, a, floor ? nav.path(a.x, a.y, a.z, gx, a.goalY, gz, expand, doors) : nav.path(a.x, a.y, a.z, gx, gz, expand, doors), gx, gz, doors);
        }
        int[] p = a.path;
        if (p == null) {
            WP[0] = gx; WP[1] = gz;
            // 1.1: no route (none found, or none planned yet) and no clear line: it waits for one. It used to set off straight
            // at the place regardless - into whatever wall stood between - which is the "walking at a point on the other side".
            if (a.tier == 0 && a.onGround && a.distXZ(gx, gz) > 1.2f && !nav.clear(a.x, a.y, a.z, gx, gz, doors)) wpHold = true;
            return WP;
        }
        int n = p.length / 3;
        // a path is followed in three dimensions: a cell is reached when the feet are at its height, not merely under it
        // (1.0: a doorway is gone through squarely - its cell is not "reached" until the body is nearly at the middle of it)
        // 1.3: a cell above the feet is reached only once the feet are nearly up to it. "Within a block and a bit" let a body on a
        // flight count the landing as reached a step short of it, turn for the next flight there - and walk off the side of the
        // stairs into the well (the turning in circles on stairs: it fell, found itself below, and went round again). And the
        // cell before a step up is reached squarely (as a doorway is), so that the flight is walked up from the front.
        while (a.pathI < n - 1) {
            int ci = a.pathI * 3;
            float rise = p[ci + 1] - a.y;
            boolean beforeStep = p[ci + 4] > p[ci + 1];
            float reach = nav.choke(p[ci], p[ci + 1], p[ci + 2]) || beforeStep && rise < 0.5f ? 0.32f : 0.75f;
            if (a.distXZ(p[ci] + 0.5f, p[ci + 2] + 0.5f) < reach && rise < 0.6f && -rise < 1.15f) a.pathI++; else break;
        }
        // look a few cells ahead and cut the corner when the line to them is open - never across a ladder or a jump, never
        // past a doorway (1.0), and only where the whole width of the body can go (1.0: it was the width of a point)
        int pick = a.pathI, lim = a.pathI;
        boolean rungs = Physics.inClimb(r.world, a.x, a.y, a.z) && !a.onGround;
        while (!rungs && lim < Math.min(n - 1, a.pathI + 4) && p[(lim + 1) * 3 + 1] == p[lim * 3 + 1] && !nav.climb(p[(lim + 1) * 3], p[(lim + 1) * 3 + 1], p[(lim + 1) * 3 + 2]) && !nav.choke(p[lim * 3], p[lim * 3 + 1], p[lim * 3 + 2])) lim++;
        if (Math.abs(p[a.pathI * 3 + 1] - a.y) < 0.6f) for (int k = lim; k > a.pathI; k--) if (nav.clearBody(a.x, a.y, a.z, p[k * 3] + 0.5f, p[k * 3 + 2] + 0.5f, false, a.radius * 0.9f)) { pick = k; break; }
        int cx = p[pick * 3], cy = p[pick * 3 + 1], cz = p[pick * 3 + 2];
        // 1.3: onto a flight from the front. A route may step onto stairs on the diagonal (from the cell beside the foot of a
        // flight); a body taking that line meets the side of the first tread, sticks, and side-steps off. When the next cell is
        // a step up reached on the diagonal, the body first goes to the cell in front of the flight - found from which way the
        // route goes on up it - and from there straight up.
        float frontX = Float.NaN, frontZ = 0;
        if (pick == a.pathI && a.pathI > 0 && a.pathI < n - 1 && cy > a.y + 0.4f) {
            int px0 = p[(a.pathI - 1) * 3], pz0 = p[(a.pathI - 1) * 3 + 2], ax = p[(a.pathI + 1) * 3] - cx, az = p[(a.pathI + 1) * 3 + 2] - cz;
            // (on the diagonal, or square-on from the side of the flight: anything but along it)
            boolean along = (cx - px0) * ax + (cz - pz0) * az > 0 && (cx - px0) * az - (cz - pz0) * ax == 0, axial = (ax == 0) != (az == 0) && p[(a.pathI + 1) * 3 + 1] > cy;
            if (!along && axial) {
                int fx = cx - ax, fz = cz - az;
                // off the line of the flight (to the side of it), or not yet up to the front of it: go to the front first. Once in
                // line and there, it walks on up (without this test the body went back and forth across the front point)
                float perp = Math.abs(ax != 0 ? a.z - (fz + 0.5f) : a.x - (fx + 0.5f));
                float ahead = ax != 0 ? (a.x - (fx + 0.5f)) * ax : (a.z - (fz + 0.5f)) * az;
                if ((perp > 0.22f || ahead < -0.35f) && a.y < cy - 0.55f && ahead < 1.7f && standable(r.world, fx, (int) Math.floor(a.y + 0.05f), fz) && Math.abs(ahead) < 1.6f) { frontX = fx + 0.5f; frontZ = fz + 0.5f; }
                // no room in front of it (furniture, a wall): stepped onto from the side, then, but at the low end of the first
                // tread - a tread is a ramp, and across its high end the step up is more than a stride takes
                else if (!standable(r.world, fx, (int) Math.floor(a.y + 0.05f), fz) && a.y < cy - 0.5f) { frontX = cx + 0.5f - ax * 0.3f; frontZ = cz + 0.5f - az * 0.3f; }
            }
        }
        wpUp = p[a.pathI * 3 + 1] >= a.y + 0.6f && a.distXZ(p[a.pathI * 3] + 0.5f, p[a.pathI * 3 + 2] + 0.5f) < 1.8f;
        // 1.3: a flight is walked, never hopped: a step up onto a stair tread (or up from one) is not a ledge
        if (wpUp && (r.world.block(p[a.pathI * 3], p[a.pathI * 3 + 1] - 1, p[a.pathI * 3 + 2]).stair() || Physics.onStairs(r.world, a.x, a.y, a.z))) wpUp = false;
        // 0.9: the way leads up a wall two or three blocks high: hands on the edge and up (people only - the route was only
        // offered to them). The next cell of the path is the top of it; the body has to be at the foot and on its feet
        if (doors && a.pathI < n && a.onGround && !a.mantling() && r.time >= a.jumpAt && a.vital == absentialis.entity.Vital.ACTIVE && !a.crawling) {
            int ux = p[a.pathI * 3], uy = p[a.pathI * 3 + 1], uz = p[a.pathI * 3 + 2];
            if (uy >= a.y + 1.6f && uy <= a.y + Physics.MANTLE_MAX + 0.3f && a.distXZ(ux + 0.5f, uz + 0.5f) < 1.7f) {
                float[] to = Physics.ledge(r.world, a, ux + 0.5f - a.x, uz + 0.5f - a.z);
                if (to != null && Math.abs(to[1] - uy) < 0.6f) { Physics.mantle(a, to); a.jumpAt = r.time + a.mantleDur + 0.4f; r.mantles++; a.pathI = Math.min(n - 1, a.pathI + 1); if (a.anim != null) a.anim.action = ""; }
            }
        }
        // a closed door on the way: people open it, the dead do not
        if (doors) for (int k = a.pathI; k <= Math.min(n - 1, a.pathI + 1); k++) {
            int qx = p[k * 3], qy = p[k * 3 + 1], qz = p[k * 3 + 2];
            if (a.distXZ(qx + 0.5f, qz + 0.5f) < 1.9f && r.world.solid(qx, qy, qz)) { absentialis.scene.Doors.Door dr = r.doors.at(qx, qy, qz); if (dr != null) r.doors.open(dr, a); }
        }
        // ladders: with rungs in reach and the way leading up or down, take hold and climb (forward climbs, as for the player)
        if (Physics.inClimb(r.world, a.x, a.y, a.z)) {
            // up: until the feet are clear of the level being climbed to (stopping level with it leaves the body hanging
            // on the top rung, a hand short of the roof); down: only once off the ground and above where the way leads
            if (cy > a.y + 0.3f || !a.onGround && cy > a.y - 0.25f) { a.wantClimb = true; a.climbDir = 1; }
            else if (cy < a.y - 0.6f && !a.onGround) { a.wantClimb = true; a.climbDir = -1; }
        }
        WP[0] = cx + 0.5f; WP[1] = cz + 0.5f;
        if (!Float.isNaN(frontX)) { WP[0] = frontX; WP[1] = frontZ; return WP; }
        // 1.3: a diagonal step of the route with no floor at one of its corners (the head of a stairwell, the edge of a landing)
        // is taken round by the corner that has a floor - cut, the body's shoulder went over the drop and it fell down the flight
        if (pick == a.pathI && a.pathI > 0) {
            int qx = p[(a.pathI - 1) * 3], qy = p[(a.pathI - 1) * 3 + 1], qz = p[(a.pathI - 1) * 3 + 2], sx = cx - qx, sz = cz - qz;
            if (sx != 0 && sz != 0 && Math.abs(sx) == 1 && Math.abs(sz) == 1 && qy == cy) {
                boolean aOk = standable(r.world, qx + sx, cy, qz), bOk = standable(r.world, qx, cy, qz + sz);
                if (aOk != bOk) {
                    float kx = (aOk ? qx + sx : qx) + 0.5f, kz = (aOk ? qz : qz + sz) + 0.5f;
                    // until the body is past the corner cell's line on the side of the drop
                    float past = aOk ? (a.x - kx) * sx : (a.z - kz) * sz;
                    if (past < -0.15f || a.distXZ(kx, kz) > 0.45f && past < 0.2f) { WP[0] = kx; WP[1] = kz; return WP; }
                }
            }
        }
        if (pick == n - 1 && a.distXZ(WP[0], WP[1]) < 0.6f && !a.hasVia && !otherFloor && Math.abs(gx - tx) + Math.abs(gz - tz) < 0.01f) {
            // 1.1: the last cell of a route is the place itself only when the route got there. A route that ran out short of
            // it - the best that could be found toward somewhere that cannot be reached - ends at a wall, and from its last
            // cell the body used to make straight for the place: through the wall, as far as its legs were concerned. It now
            // stands at the end of the route unless the rest of the way is open.
            boolean there = cx == (int) Math.floor(tx) && cz == (int) Math.floor(tz);
            if (there || nav.clear(a.x, a.y, a.z, tx, tz, doors)) { WP[0] = tx; WP[1] = tz; a.path = null; a.pathAt = r.time; }
            else { wpHold = true; a.partialUntil = r.time + 12f; }
        }
        return WP;
    }

    /**
     * Forced entry. A locked door is an obstacle navigation routes round; an agent only puts a boot through one
     * when it has a reason - it is in a fight or hunting someone it believes is on the other side - and when its
     * doctrine allows the noise: soldiers and contractors breach, bandits kick anything in, loners never do.
     */
    private static void forceEntry(Raid r, Actor a, float gx, float gz) {
        if (r.time < a.kickAt) return;
        int n = a.path.length / 3;
        float ex = a.path[(n - 1) * 3] + 0.5f, ez = a.path[(n - 1) * 3 + 2] + 0.5f;
        if (Math.abs(ex - gx) + Math.abs(ez - gz) < 2.5f) return;                       // the path gets there
        absentialis.scene.Doors.Door d = r.doors.near(a.x, a.y, a.z, 1.8f);
        if (d == null || r.doors.state(d) != absentialis.scene.Doors.State.LOCKED) return;
        // the door has to be between here and where it is going
        float tx = gx - a.x, tz = gz - a.z, dx = d.cx() - a.x, dz = d.cz() - a.z;
        if (tx * dx + tz * dz <= 0) return;
        Relations.Doctrine dc = Relations.doc(a.faction).d();
        boolean hunting = "engage".equals(a.state) || "search".equals(a.state) && a.awareLevel >= NeuralHost.THREAT_AWARE;
        boolean may = dc == Relations.Doctrine.BANDIT && (hunting || "loot".equals(a.state)) || (dc == Relations.Doctrine.MILITARY || dc == Relations.Doctrine.MERC) && hunting;
        if (!may) return;
        a.kickAt = r.time + 0.9f;
        a.yaw = (float) Math.atan2(-dx, -dz);
        if (a.anim != null) a.anim.play("melee", 0.45f);
        if (r.doors.kick(d, a) && r.time - a.lastSay > 6) r.say(a, "Door's open - go!", "shout");
    }

    /** 1.3: a cell a body can stand in: floor under it (a stair tread counts) and room for the body over it. */
    private static boolean standable(absentialis.world.World w, int x, int y, int z) {
        return w.solid(x, y - 1, z) && !w.solid(x, y, z) && !w.solid(x, y + 1, z);
    }

    public static float turn(float cur, float target, float maxStep) {
        float d = target - cur;
        while (d > Math.PI) d -= Math.PI * 2;
        while (d < -Math.PI) d += Math.PI * 2;
        return cur + Math.max(-maxStep, Math.min(maxStep, d));
    }
}
