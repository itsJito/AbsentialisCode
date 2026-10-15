package absentialis.vehicle;

import absentialis.world.Block;
import absentialis.world.World;

/**
 * Spring-and-damper suspension for one wheel.
 *
 * Each step a collision line is cast from the top of the wheel's travel straight down the car's own vertical, as
 * long as the travel plus the tyre radius, so contact is found where the bottom of the tyre is. The result is a
 * normalised spring ratio (1 = fully extended, 0 = fully compressed); compression is 1 - ratio.
 *
 * Spring impulse = gravity * mass * normalised timestep * suspension force * compression * axle bias * 2, pushed
 * into the chassis at the wheel's contact point along the ground normal. Because that point is away from the
 * centre of mass, the same impulses pitch the car under braking and roll it in corners. Damping opposes the
 * chassis's velocity at the contact along the normal, so the body settles instead of bouncing for ever. A wheel
 * that finds no ground contributes nothing until it touches down again.
 */
public final class CarSuspension {
    private CarSuspension() {}

    /** Gravity as a speed change per 50 Hz step: the unit the handling equations are written in. */
    public static final float GRAVITY = 9.81f;
    public static final float G_STEP = GRAVITY * CarVehicle.FIXED_DT;
    /** Converts the damping setting into (N s / m) / kg per step; 0.28 gives a damping ratio near 0.4 on a family car. */
    private static final float DAMP_K = 0.17f;

    private static final float[] top = new float[3], hit = new float[4], vel = new float[3];

    /** Finds the wheel's contact, updates its compression and applies the spring and damper impulse to the body. */
    static void update(CarWheel w, CarRigidBodyAdapter body, CarHandlingData d, World world, float brake, float forwardSpeed, float nt, boolean wetWeather) {
        float travel = d.suspensionTravel();
        // a burst tyre rides on its rim: the wheel is effectively smaller
        float radius = w.tyre == CarWheel.Tyre.BURST ? d.wheelRadius * 0.82f : d.wheelRadius;
        body.toWorld(w.ax - d.comX, w.ay + d.suspensionUpper - d.comY, w.az - d.comZ, top);
        float dx = -body.upX(), dy = -body.upY(), dz = -body.upZ();
        w.prevCompression = w.compression;
        w.normalImpulse = 0;
        float t = castRay(world, top[0], top[1], top[2], dx, dy, dz, travel + radius, hit);
        if (t < 0) {
            // airborne: no spring, no damper; the wheel drops to the end of its travel
            w.grounded = false;
            w.length += (travel - w.length) * Math.min(1, nt * 0.35f);
            w.compression = 0;
            return;
        }
        w.grounded = true;
        w.cx = top[0] + dx * t; w.cy = top[1] + dy * t; w.cz = top[2] + dz * t;
        w.nx = hit[0]; w.ny = hit[1]; w.nz = hit[2];
        w.blockId = (int) hit[3];
        Block b = Block.ALL[w.blockId];
        boolean open = world.sky((int) Math.floor(w.cx), (int) Math.floor(w.cy + 0.5f), (int) Math.floor(w.cz)) > 12;
        w.surface = CarSurfaceData.of(b, wetWeather && open);
        float s = Math.max(0, Math.min(travel, t - radius));
        w.length = s;
        float ratio = s / travel;                 // 1 = fully extended
        w.compression = 1f - ratio;
        float axle = w.front ? d.suspensionBias : 1f - d.suspensionBias;
        float spring = G_STEP * d.mass * nt * d.suspensionForce * w.compression * axle * 2f;
        // anti-dive: the front springs stiffen under braking so the nose does not bury itself
        // (it limits the dive, it does not cancel it: weight still goes forward and the nose still drops)
        if (w.front && brake > 0 && forwardSpeed > 1) spring *= 1f + d.antiDive * 0.3f * brake * Math.min(1f, forwardSpeed / 12f);
        body.velocityAt(w.cx, w.cy, w.cz, vel);
        float vn = vel[0] * w.nx + vel[1] * w.ny + vel[2] * w.nz;
        float damp = -vn * d.mass * d.suspensionDamping * nt * DAMP_K;
        float total = spring + damp;
        // bump stop: out of travel and still closing - the rubber takes what the spring could not
        if (w.compression > 0.97f && vn < 0) total += -vn * d.mass * 0.25f * 0.7f;
        if (total <= 0) return;                   // a wheel cannot pull the ground toward the car
        total = Math.min(total, d.mass * 3.5f);
        w.normalImpulse = total;
        body.applyImpulse(w.cx, w.cy, w.cz, w.nx * total, w.ny * total, w.nz * total);
    }

    /**
     * Voxel line query. Returns the distance along the ray to the first solid block face within maxLen, or -1.
     * out receives the face normal (x, y, z) and the block id. A ray that starts inside solid ground reports no
     * contact: that wheel is buried and the chassis collision is what must push the car out.
     */
    public static float castRay(World w, float ox, float oy, float oz, float dx, float dy, float dz, float maxLen, float[] out) {
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        if (w.solid(x, y, z)) return -1;
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        float tdx = Math.abs(dx) < 1e-6f ? Float.MAX_VALUE : Math.abs(1 / dx), tdy = Math.abs(dy) < 1e-6f ? Float.MAX_VALUE : Math.abs(1 / dy), tdz = Math.abs(dz) < 1e-6f ? Float.MAX_VALUE : Math.abs(1 / dz);
        float tmx = tdx == Float.MAX_VALUE ? Float.MAX_VALUE : (dx > 0 ? (x + 1 - ox) : (ox - x)) * tdx;
        float tmy = tdy == Float.MAX_VALUE ? Float.MAX_VALUE : (dy > 0 ? (y + 1 - oy) : (oy - y)) * tdy;
        float tmz = tdz == Float.MAX_VALUE ? Float.MAX_VALUE : (dz > 0 ? (z + 1 - oz) : (oz - z)) * tdz;
        for (int i = 0; i < 16; i++) {
            float t;
            int axis;
            if (tmx <= tmy && tmx <= tmz) { t = tmx; x += sx; tmx += tdx; axis = 0; }
            else if (tmy <= tmz) { t = tmy; y += sy; tmy += tdy; axis = 1; }
            else { t = tmz; z += sz; tmz += tdz; axis = 2; }
            if (t > maxLen) return -1;
            if (w.solid(x, y, z)) {
                out[0] = axis == 0 ? -sx : 0; out[1] = axis == 1 ? -sy : 0; out[2] = axis == 2 ? -sz : 0;
                out[3] = w.get(x, y, z);
                return t;
            }
        }
        return -1;
    }
}
