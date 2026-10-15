package absentialis.vehicle;

/**
 * Tyre forces: a simplified, adhesion-limited impulse model, solved for each grounded wheel on its own.
 *
 * The chassis's velocity at the wheel's contact point (linear velocity + angular velocity x contact offset) is
 * split into a part along the direction the tyre is pointing and a part across it.
 *   - Across: the tyre resists being dragged sideways, with the impulse that would stop that motion - shared out
 *     by the number of wheels on the ground. This is what steers the car and what keeps it straight.
 *   - Along: engine thrust (driven wheels only), braking and rolling resistance.
 * Both draw on one budget. Combined demand = sqrt(forward^2 + sideways^2); while that is inside the tyre's
 * adhesion the tyre delivers exactly what was asked. Past it, both are scaled back to what the tyre can still
 * hold while sliding (adhesion x traction loss) and the wheel is spinning, skidding or locked - the motion the
 * tyre could not stop is the slide.
 *
 * Adhesion = traction multiplier x surface x axle grip factor x load, where the load is the weight the wheel is
 * actually carrying this step (so a braking car grips harder at the front, and a wheel in the air grips nothing).
 */
public final class CarTireSolver {
    private CarTireSolver() {}

    private static final float[] vel = new float[3];

    static void solve(CarWheel w, CarVehicle car, float driveAccelPerWheel, float nt, float dt) {
        CarHandlingData d = car.data;
        CarRigidBodyAdapter body = car.body;
        CarInput in = car.input;
        w.driveImpulse = w.brakeImpulse = w.forwardImpulse = w.sideImpulse = 0;
        w.demand = 0;
        boolean handbrake = in.handbrake && !w.front;
        if (!w.grounded) {
            // in the air a tyre generates no road force; a driven wheel just spins up, the rest wind down
            w.adhesion = 0;
            w.state = handbrake || in.brake > 0.5f ? CarWheel.State.FIXED : CarWheel.State.NORMAL;
            if (w.state == CarWheel.State.FIXED) w.rotationSpeed = 0;
            else if (d.driven(w.index) && in.throttle > 0) w.rotationSpeed += (in.reverse ? -1 : 1) * in.throttle * 40f * dt;
            else w.rotationSpeed *= 1f - Math.min(1, dt * 0.8f);
            w.rotation += w.rotationSpeed * dt;
            w.slideTime = 0;
            return;
        }
        // ---- tyre axes on the contact surface
        float ca = (float) Math.cos(w.steerAngle), sa = (float) Math.sin(w.steerAngle);
        float fx = body.fwdX() * ca + body.rightX() * sa, fy = body.fwdY() * ca + body.rightY() * sa, fz = body.fwdZ() * ca + body.rightZ() * sa;
        float dn = fx * w.nx + fy * w.ny + fz * w.nz;
        fx -= w.nx * dn; fy -= w.ny * dn; fz -= w.nz * dn;
        float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fl < 1e-4f) { w.adhesion = 0; w.state = CarWheel.State.NORMAL; return; }   // the tyre points straight into the surface: nothing to transmit
        fx /= fl; fy /= fl; fz /= fl;
        float sx = w.ny * fz - w.nz * fy, sy = w.nz * fx - w.nx * fz, sz = w.nx * fy - w.ny * fx;   // sideways = normal x forward
        w.fx = fx; w.fy = fy; w.fz = fz; w.sx = sx; w.sy = sy; w.sz = sz;
        body.velocityAt(w.cx, w.cy, w.cz, vel);
        float vf = vel[0] * fx + vel[1] * fy + vel[2] * fz, vs = vel[0] * sx + vel[1] * sy + vel[2] * sz;
        w.forwardVel = vf; w.sideVel = vs;

        // ---- adhesion available this step
        float staticLoad = CarSuspension.G_STEP * d.mass * nt * 0.25f;
        float load = Math.min(staticLoad * 3f, 0.5f * staticLoad + 0.5f * w.normalImpulse);
        float frontGrip = 2f * d.tractionBias;
        float mu = d.tractionMultiplier * w.surface.adhesion * (w.front ? frontGrip : 2f - frontGrip);
        if (w.tyre == CarWheel.Tyre.BURST) mu *= 0.55f;
        float adhesion = mu * load;
        w.adhesion = adhesion;

        // ---- what the car asks of this tyre
        float share = car.body.mass / Math.max(1, car.groundedWheels);
        float side = -vs * share;
        if (handbrake) side *= d.handbrakeTyre;                 // a locked rear tyre has little say over where the tail goes
        if (w.tyre == CarWheel.Tyre.BURST) side += (car.noise() - 0.5f) * adhesion * 0.7f * Math.min(1f, Math.abs(vf) / 6f);   // the carcass squirms
        float drive = d.driven(w.index) ? driveAccelPerWheel * d.mass * dt : 0;
        float brakeMax = d.brakeDeceleration * d.mass * dt * in.brake * (w.front ? d.brakeBias : 1f - d.brakeBias) * 0.5f;
        if (handbrake) brakeMax = Float.MAX_VALUE;
        float stopNow = Math.abs(vf) * share;                   // the impulse that would stop this contact dead
        float brake = brakeMax <= 0 ? 0 : -Math.signum(vf) * Math.min(stopNow, brakeMax);
        // rolling resistance: a steady drag while the wheel turns, never more than would stop it
        float rollMax = w.surface.rolling * d.mass * dt * 0.25f * (w.tyre == CarWheel.Tyre.BURST ? 5f : 1f);
        float rolling = -Math.signum(vf) * Math.min(stopNow, rollMax);
        float forward = drive + brake + rolling;
        w.driveImpulse = drive; w.brakeImpulse = brake;

        // ---- combined adhesion limit
        float demand = (float) Math.sqrt(forward * forward + side * side);
        w.demand = demand;
        boolean locked = handbrake && Math.abs(vf) > 0.3f;
        if (demand <= adhesion || demand < 1e-6f) {
            w.state = CarWheel.State.NORMAL;
            w.slideTime = 0;
        } else {
            float hold = adhesion * d.tractionLoss / demand;
            forward *= hold; side *= hold;
            // which demand broke the tyre loose decides what it is doing
            boolean braking = Math.abs(brake) > Math.abs(drive) && Math.abs(brake) > Math.abs(side) * 0.8f;
            if (locked || braking && in.brake > 0.5f) { w.state = CarWheel.State.FIXED; locked = true; }
            else if (Math.abs(drive) > Math.abs(side) && Math.abs(drive) > adhesion * 0.8f) w.state = CarWheel.State.SPINNING;
            else w.state = CarWheel.State.SKIDDING;
            w.slideTime += dt;
        }
        if (locked && w.state == CarWheel.State.NORMAL && handbrake) w.state = CarWheel.State.FIXED;
        w.forwardImpulse = forward; w.sideImpulse = side;
        body.applyImpulse(w.cx, w.cy, w.cz, fx * forward + sx * side, fy * forward + sy * side, fz * forward + sz * side);

        // ---- visible rotation follows the physics, never the throttle alone
        float r = Math.max(0.1f, d.wheelRadius);
        switch (w.state) {
            case FIXED -> w.rotationSpeed = 0;
            case SPINNING -> {
                // the tread is travelling faster than the road: by how much grows with how far the demand overshot
                float over = Math.min(3f, demand / Math.max(1e-3f, adhesion) - 1f);
                w.rotationSpeed = (vf + Math.signum(drive) * (3f + over * 9f)) / r;
            }
            default -> w.rotationSpeed = vf / r;
        }
        w.rotation += w.rotationSpeed * dt;
    }
}
