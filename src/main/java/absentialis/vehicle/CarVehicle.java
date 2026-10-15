package absentialis.vehicle;

import absentialis.world.World;

/**
 * One drivable car: a dynamic rigid body carried on four independently simulated wheel contacts.
 *
 * Nothing here is a character controller. Steering input turns the front wheels, and the car turns because those
 * tyres then generate sideways force where they touch the road. The accelerator asks the transmission for thrust
 * at the driven wheels; whether it becomes speed is up to their grip. Suspension, tyres, gravity, drag and
 * collisions are impulses on the body, which is integrated once per fixed 50 Hz step whatever the frame rate.
 *
 * Fixed update order (the same every step, each force computed once):
 *   1 read input            5 wheel contact queries       9 adhesion limits and wheel states
 *   2 steering              6 suspension spring/damper   10 impulses applied at the contacts (done as solved)
 *   3 transmission          7 velocities at the contacts 11 gravity, air resistance, terrain drag
 *   4 thrust distribution   8 tyre forces                12 integrate and resolve collisions   13 visuals
 */
public final class CarVehicle {
    /** Fixed physics rate: 50 Hz. The handling equations are written for a normalised timestep of 1 at this rate. */
    public static final float FIXED_DT = 0.02f;
    public static final float STEER_SMOOTH = 0.2f;

    public final CarHandlingData data;
    public final CarRigidBodyAdapter body = new CarRigidBodyAdapter();
    public final CarInput input = new CarInput();
    public final CarTransmission transmission;
    public final CarWheel[] wheels = {new CarWheel(0), new CarWheel(1), new CarWheel(2), new CarWheel(3)};
    public final CarDamageState damage = new CarDamageState();
    public int paint = 0xFF6A7078;
    public final int id;
    private static int nextId = 1;

    /** Smoothed steering input, -1 .. 1, and the resulting front wheel angle in radians. */
    public float steering, wheelAngle;
    public int groundedWheels;
    public boolean engineOn, lightsOn, siren, wetWeather;
    /** Total drive acceleration the engine offered last step (m/s^2, signed). */
    public float driveAcceleration;
    /** Seconds with every wheel off the ground, and the largest such flight so far. */
    public float airTime, longestAir;
    public float distance;
    private float accumulator, sleepTimer;
    public boolean asleep;
    public int steps;
    private int rngState = 12345;
    // ---- previous transform, for interpolating between fixed steps when drawing
    private float ppx, ppy, ppz, pqw = 1, pqx, pqy, pqz;
    public interface Listener {
        /** A chassis impact: speed change, world position, block struck (or -1,-1,-1), zone that took it. */
        void impact(CarVehicle car, float dv, float x, float y, float z, int zone);
        void blockHit(CarVehicle car, int x, int y, int z, float speed);
        void tyreBurst(CarVehicle car, int wheel);
        void gearChanged(CarVehicle car);
        void landed(CarVehicle car, float speed);
    }
    public Listener listener;

    public CarVehicle(CarHandlingData d) {
        data = d;
        id = nextId++;
        transmission = new CarTransmission(d);
        float yaw = d.yawInertia();
        // roll and pitch inertia from the same box, stiffened a little: a car's mass sits low and spread out
        body.setMass(d.mass, yaw, d.mass * (d.height * d.height + d.length * d.length) / 12f * 1.15f, d.mass * (d.width * d.width + d.height * d.height) / 12f * 1.6f);
        body.setShape(d.collisionBoxes, d.comX, d.comY, d.comZ);
        for (int i = 0; i < 4; i++) { wheels[i].ax = d.wheels[i][0]; wheels[i].ay = d.wheels[i][1]; wheels[i].az = d.wheels[i][2]; wheels[i].length = d.suspensionTravel(); }
        if (d.paints.length > 0) paint = d.paints[0];
    }

    /** Puts the car's origin (ground centre of the wheelbase) at a world position with a heading (0 faces +z). */
    public void place(float x, float y, float z, float yaw) {
        body.setYaw(yaw);
        body.vx = body.vy = body.vz = body.wx = body.wy = body.wz = 0;
        float[] o = new float[3];
        body.px = body.py = body.pz = 0;
        body.toWorld(data.comX, data.comY, data.comZ, o);
        body.px = x + o[0]; body.py = y + o[1] + 0.05f; body.pz = z + o[2];
        snapshot();
        asleep = false; sleepTimer = 0;
    }

    private void snapshot() { ppx = body.px; ppy = body.py; ppz = body.pz; pqw = body.qw; pqx = body.qx; pqy = body.qy; pqz = body.qz; }

    public float forwardSpeed() { return body.vx * body.fwdX() + body.vy * body.fwdY() + body.vz * body.fwdZ(); }
    public float lateralSpeed() { return body.vx * body.rightX() + body.vy * body.rightY() + body.vz * body.rightZ(); }
    public float speed() { return (float) Math.sqrt(body.vx * body.vx + body.vy * body.vy + body.vz * body.vz); }
    public float kmh() { return speed() * 3.6f; }
    /** How upright the car is: 1 on its wheels, 0 on its side, -1 on its roof. */
    public float upright() { return body.upY(); }
    public boolean overturned() { return body.upY() < 0.25f; }

    float noise() { rngState = rngState * 1103515245 + 12345; return ((rngState >>> 8) & 0xFFFF) / 65535f; }

    /** World position of a point given in the car frame (origin at the ground centre of the wheelbase). */
    public void point(float lx, float ly, float lz, float[] out) { body.toWorld(lx - data.comX, ly - data.comY, lz - data.comZ, out); }
    public void origin(float[] out) { point(0, 0, 0, out); }

    /** Advances the simulation by real time, in whole fixed steps. Returns the number of steps taken. */
    public int update(float frameDt, World w) {
        if (asleep) {
            if (input.throttle > 0 || input.brake > 0 || Math.abs(input.steer) > 0.01f) wake(); else { accumulator = 0; return 0; }
        }
        accumulator += Math.min(frameDt, 0.1f);
        int n = 0;
        while (accumulator >= FIXED_DT && n < 6) { step(w); accumulator -= FIXED_DT; n++; }
        if (n == 6) accumulator = 0;   // a long hitch: drop the backlog rather than spiral
        return n;
    }

    public void wake() { asleep = false; sleepTimer = 0; }

    /** One fixed 50 Hz step. */
    public void step(World w) {
        final float dt = FIXED_DT, nt = dt * 50f;
        snapshot();
        steps++;
        CarHandlingData d = data;
        for (int i = 0; i < 4; i++) wheels[i].tyre = damage.tyres[i];

        // 2. steering: smoothed input, squared response, front wheels only
        float target = Math.max(-1, Math.min(1, input.steer));
        steering += (target - steering) * STEER_SMOOTH * nt;
        steering = Math.max(-1, Math.min(1, steering));
        float effective = Math.signum(steering) * steering * steering;
        wheelAngle = (float) Math.toRadians(d.steeringLock) * effective;
        wheels[0].steerAngle = wheels[1].steerAngle = wheelAngle;
        wheels[2].steerAngle = wheels[3].steerAngle = 0;

        // 3. transmission, 4. thrust shared between the driven wheels
        float fwd = forwardSpeed();
        int gearBefore = transmission.gear;
        transmission.update(fwd, input.reverse, dt);
        if (transmission.gear != gearBefore && listener != null) listener.gearChanged(this);
        float throttle = engineOn ? input.throttle : 0;
        driveAcceleration = transmission.acceleration(throttle, fwd) * damage.enginePower();
        float perWheel = driveAcceleration / d.drivenWheels();

        // 5 + 6. contacts and suspension
        boolean wasAir = groundedWheels == 0;
        float fall = -body.vy;
        int grounded = 0;
        for (CarWheel wh : wheels) { CarSuspension.update(wh, body, d, w, input.brake, fwd, nt, wetWeather); if (wh.grounded) grounded++; }
        groundedWheels = grounded;
        if (grounded == 0) { airTime += dt; longestAir = Math.max(longestAir, airTime); }
        else {
            if (wasAir && airTime > 0.25f && listener != null) listener.landed(this, fall);
            airTime = 0;
        }

        // 7 - 10. tyres (the rear axle first when the handling data asks for it)
        if (d.rearWheelsFirst) { for (int i = 2; i < 4; i++) CarTireSolver.solve(wheels[i], this, perWheel, nt, dt); for (int i = 0; i < 2; i++) CarTireSolver.solve(wheels[i], this, perWheel, nt, dt); }
        else for (CarWheel wh : wheels) CarTireSolver.solve(wh, this, perWheel, nt, dt);

        // 11. gravity, air resistance, terrain drag
        body.vy -= CarSuspension.GRAVITY * dt;
        float sp = speed();
        if (sp > 0.01f) {
            float air = d.width * d.height / d.mass * 0.24f;           // 0.5 * rho * Cd, folded into one constant
            float k = Math.max(0, 1f - air * sp * dt);
            body.vx *= k; body.vy *= k; body.vz *= k;
            float drag = 0;
            for (CarWheel wh : wheels) if (wh.grounded) drag += wh.surface.drag * (wh.surface == CarSurfaceData.Surface.SAND && d.improvedSand ? 0.25f : 1f) * 0.25f;
            if (drag > 0) { float kd = Math.max(0, 1f - drag * dt); body.vx *= kd; body.vz *= kd; }
        }
        float spin = 1f - (grounded > 0 ? 0.35f : 0.12f) * dt;
        body.wx *= spin; body.wy *= spin; body.wz *= spin;

        // 12. integrate once, then collide
        body.integrate(dt);
        body.collide(w, 0.18f, 0.55f, listener == null ? null : (x, y, z, s) -> listener.blockHit(this, x, y, z, s));
        if (body.impact > 0.5f) {
            int zone = damage.impact(body.impact, body.impactLX + d.comX, body.impactLY + d.comY, body.impactLZ + d.comZ, d, noise());
            // a hard knock next to a wheel can take the tyre with it
            if (body.impact > 7.5f) for (int i = 0; i < 4; i++) {
                float dx = body.impactLX + d.comX - d.wheels[i][0], dz = body.impactLZ + d.comZ - d.wheels[i][2];
                if (dx * dx + dz * dz < 1.0f && noise() < (body.impact - 7.5f) * 0.09f && damage.burst(i) && listener != null) listener.tyreBurst(this, i);
            }
            if (listener != null && body.impact > 1.2f) listener.impact(this, body.impact, body.impactX, body.impactY, body.impactZ, zone);
        }
        distance += sp * dt;

        // rest: a parked car stops simulating until something disturbs it
        boolean idle = sp < 0.06f && Math.abs(body.wx) + Math.abs(body.wy) + Math.abs(body.wz) < 0.05f && input.throttle == 0 && grounded >= 3;
        sleepTimer = idle ? sleepTimer + dt : 0;
        if (sleepTimer > 1.5f) { asleep = true; body.vx = body.vy = body.vz = body.wx = body.wy = body.wz = 0; }
    }

    // ------------------------------------------------------------------ interpolated transform for drawing
    /** Fraction of a fixed step that has elapsed since the last one (0 .. 1). */
    public float alpha() { return asleep ? 1 : Math.max(0, Math.min(1, accumulator / FIXED_DT)); }

    /**
     * Writes the body's transform, blended between the last two fixed steps, as a column-major 4x4 matrix that
     * maps the car frame (origin at the ground centre of the wheelbase) to the world.
     */
    public void matrix(float[] m) {
        float a = alpha();
        float x = ppx + (body.px - ppx) * a, y = ppy + (body.py - ppy) * a, z = ppz + (body.pz - ppz) * a;
        float dot = pqw * body.qw + pqx * body.qx + pqy * body.qy + pqz * body.qz, s = dot < 0 ? -1 : 1;
        float w = pqw + (body.qw * s - pqw) * a, qx = pqx + (body.qx * s - pqx) * a, qy = pqy + (body.qy * s - pqy) * a, qz = pqz + (body.qz * s - pqz) * a;
        float l = (float) Math.sqrt(w * w + qx * qx + qy * qy + qz * qz);
        w /= l; qx /= l; qy /= l; qz /= l;
        float xx = qx * qx, yy = qy * qy, zz = qz * qz, xy = qx * qy, xz = qx * qz, yz = qy * qz, wx = w * qx, wy = w * qy, wz = w * qz;
        m[0] = 1 - 2 * (yy + zz); m[1] = 2 * (xy + wz); m[2] = 2 * (xz - wy); m[3] = 0;
        m[4] = 2 * (xy - wz); m[5] = 1 - 2 * (xx + zz); m[6] = 2 * (yz + wx); m[7] = 0;
        m[8] = 2 * (xz + wy); m[9] = 2 * (yz - wx); m[10] = 1 - 2 * (xx + yy); m[11] = 0;
        // the matrix places the car frame's origin, which sits below and behind the centre of mass
        float ox = -data.comX, oy = -data.comY, oz = -data.comZ;
        m[12] = x + m[0] * ox + m[4] * oy + m[8] * oz;
        m[13] = y + m[1] * ox + m[5] * oy + m[9] * oz;
        m[14] = z + m[2] * ox + m[6] * oy + m[10] * oz;
        m[15] = 1;
    }
}
