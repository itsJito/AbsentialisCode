package absentialis.tools;

import absentialis.vehicle.CarDamageState;
import absentialis.vehicle.CarHandlingData;
import absentialis.vehicle.CarSuspension;
import absentialis.vehicle.CarVehicle;
import absentialis.vehicle.CarWheel;
import absentialis.world.Block;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.List;

/**
 * Vehicle validation (handling specification, section 22), run headless at the fixed 50 Hz step on a flat test
 * world: acceleration, braking, steering, traction, handbrake, suspension, collisions, surfaces, airborne flight,
 * variation between handling profiles, and independence from the rendering frame rate.
 */
final class VehicleTests {
    private VehicleTests() {}

    private static final int G = World.GROUND;
    private static final List<int[]> edits = new ArrayList<>();

    private static void set(World w, int x, int y, int z, int id) { edits.add(new int[]{x, y, z, w.get(x, y, z)}); w.setRaw(x, y, z, id); }
    private static void restore(World w) { for (int i = edits.size() - 1; i >= 0; i--) { int[] e = edits.get(i); w.setRaw(e[0], e[1], e[2], e[3]); } edits.clear(); }

    private static CarVehicle car(String id, float x, float z, float yaw) {
        CarVehicle c = new CarVehicle(CarHandlingData.get(id));
        c.place(x, G, z, yaw);
        c.engineOn = true;
        return c;
    }

    /** Lets a freshly placed car settle on its springs. */
    private static void settle(CarVehicle c, World w) { for (int i = 0; i < 100; i++) c.step(w); c.wake(); }

    private static void drive(CarVehicle c, World w, float seconds, float throttle, float brake, float steer, boolean hand) {
        int n = Math.round(seconds / CarVehicle.FIXED_DT);
        for (int i = 0; i < n; i++) { c.input.throttle = throttle; c.input.brake = brake; c.input.steer = steer; c.input.handbrake = hand; c.input.reverse = false; c.wake(); c.step(w); }
    }

    /** Full throttle until the car reaches a speed. Returns {seconds, metres} or {-1, -1} if it never does. */
    private static float[] accelerateTo(CarVehicle c, World w, float speed, float limit) {
        float x0 = c.body.px, z0 = c.body.pz;
        for (int i = 0; i < limit / CarVehicle.FIXED_DT; i++) {
            c.input.throttle = 1; c.input.brake = 0; c.input.steer = 0; c.input.handbrake = false; c.wake(); c.step(w);
            if (c.forwardSpeed() >= speed) return new float[]{(i + 1) * CarVehicle.FIXED_DT, (float) Math.hypot(c.body.px - x0, c.body.pz - z0)};
        }
        return new float[]{-1, -1};
    }

    private static float stopDistance(CarVehicle c, World w) {
        float x0 = c.body.px, z0 = c.body.pz;
        for (int i = 0; i < 1000 && c.speed() > 0.3f; i++) { c.input.throttle = 0; c.input.brake = 1; c.input.steer = 0; c.wake(); c.step(w); }
        return (float) Math.hypot(c.body.px - x0, c.body.pz - z0);
    }

    static void run(SelfTests04.Check t) {
        System.out.println("[v0.4 vehicles: force-driven four-wheel handling, validated at the fixed 50 Hz step]");
        CarHandlingData.load();
        World w = SelfTests.flatWorld();
        edits.clear();

        // ---- data
        java.util.Set<String> fam = new java.util.HashSet<>();
        String invalid = "";
        for (CarHandlingData d : CarHandlingData.all()) { fam.add(d.family); String e = d.validate(); if (e != null) invalid += d.id + ": " + e + "; "; }
        boolean families = true;
        for (String f : CarHandlingData.FAMILIES) families &= fam.contains(f);
        t.check("every vehicle loads from data and every parameter is inside its documented range", invalid.isEmpty() && CarHandlingData.all().size() >= 7, CarHandlingData.all().size() + " vehicles " + invalid);
        t.check("all seven base families are present (SUV, off-road, emergency, van, 2-door, 4-door, pickup)", families, fam.toString());

        // ---- rest: the car sits on its springs and goes to sleep
        CarVehicle c = car("car.volna", 300.5f, 300.5f, 0);
        settle(c, w);
        float[] o = new float[3];
        c.origin(o);
        float expected = 1f / (4f * c.data.suspensionForce), mean = (c.wheels[0].compression + c.wheels[1].compression + c.wheels[2].compression + c.wheels[3].compression) / 4;
        t.check("at rest all four wheels carry the car at the static compression 1/(4 x suspension force)", c.groundedWheels == 4 && Math.abs(mean - expected) < 0.03f && Math.abs(o[1] - G) < 0.06f && c.speed() < 0.05f,
                String.format("mean compression %.3f (expected %.3f), origin %.3f above ground", mean, expected, o[1] - G));
        for (int i = 0; i < 150; i++) c.step(w);
        t.check("a parked car comes to rest and stops simulating until something disturbs it", c.asleep && c.update(0.1f, w) == 0, "asleep " + c.asleep);
        c.wake();

        // ---- ACCELERATION
        float[] a10 = accelerateTo(c, w, 10, 20);
        float[] a20 = accelerateTo(c, w, 20, 30);
        t.check("acceleration: 0-36 km/h and on to 72 km/h in plausible time and distance", a10[0] > 1.2f && a10[0] < 7 && a20[0] > 0 && c.transmission.gear >= 3,
                String.format("0-10 m/s %.2f s over %.1f m; 10-20 m/s +%.2f s; gear %s", a10[0], a10[1], a20[0], c.transmission.label()));
        // top speed: the engine stops adding thrust, nothing clamps the speed
        c = car("car.volna", 200.5f, 120.5f, 0);
        settle(c, w);
        c.body.vz = c.data.maxVelocity * 0.96f; c.transmission.gear = c.data.gears;
        float topMax = 0;
        for (int i = 0; i < 400; i++) { c.input.throttle = 1; c.input.brake = 0; c.input.steer = 0; c.wake(); c.step(w); topMax = Math.max(topMax, c.forwardSpeed()); }
        float top = c.forwardSpeed();
        float over = c.transmission.acceleration(1, c.data.maxVelocity + 0.01f), under = c.transmission.acceleration(1, c.data.maxVelocity * 0.9f);
        t.check("top speed is approached, never assigned: thrust runs out at maximum velocity", top > c.data.maxVelocity * 0.78f && topMax <= c.data.maxVelocity + 0.3f && over == 0 && under > 0 && c.transmission.gear == c.data.gears,
                String.format("holds %.1f m/s of %.1f in gear %s; thrust %.2f just under, %.2f at the limit", top, c.data.maxVelocity, c.transmission.label(), under, over));
        // coasting
        float v0 = c.speed();
        drive(c, w, 3, 0, 0, 0, false);
        t.check("coasting: releasing the throttle loses speed gradually to rolling and air resistance", c.speed() < v0 * 0.98f && c.speed() > v0 * 0.7f, String.format("%.1f -> %.1f m/s in 3 s", v0, c.speed()));

        // ---- BRAKING (and pitch under braking)
        c = car("car.volna", 300.5f, 200.5f, 0);
        settle(c, w);
        accelerateTo(c, w, 20, 30);
        float frontBefore = (c.wheels[0].compression + c.wheels[1].compression) / 2;
        drive(c, w, 0.4f, 0, 1, 0, false);
        float frontDuring = (c.wheels[0].compression + c.wheels[1].compression) / 2, rearDuring = (c.wheels[2].compression + c.wheels[3].compression) / 2;
        float dist = stopDistance(c, w) + 0.4f * 18;
        float ideal = 20 * 20 / (2 * c.data.brakeDeceleration);
        t.check("braking: stopping distance from 72 km/h is near v^2 / 2a for the configured deceleration", dist > ideal * 0.7f && dist < ideal * 2.2f, String.format("%.1f m (ideal %.1f m)", dist, ideal));
        t.check("braking pitches the car forward: the front springs compress, the rear extend", frontDuring > frontBefore + 0.015f && frontDuring > rearDuring + 0.05f && c.body.fwdY() < 0, String.format("front %.3f -> %.3f, rear %.3f", frontBefore, frontDuring, rearDuring));

        // ---- STEERING: low-speed radius, high-speed response
        c = car("car.volna", 350.5f, 350.5f, 0);
        settle(c, w);
        accelerateTo(c, w, 5, 10);
        float minX = 1e9f, maxX = -1e9f, minZ = 1e9f, maxZ = -1e9f;
        for (int i = 0; i < 700; i++) {
            c.input.throttle = c.forwardSpeed() < 5 ? 0.5f : 0; c.input.brake = 0; c.input.steer = 1; c.wake(); c.step(w);
            if (i > 150) { minX = Math.min(minX, c.body.px); maxX = Math.max(maxX, c.body.px); minZ = Math.min(minZ, c.body.pz); maxZ = Math.max(maxZ, c.body.pz); }
        }
        float radius = ((maxX - minX) + (maxZ - minZ)) / 4;
        float wheelbase = c.data.wheels[0][2] - c.data.wheels[2][2];
        float geometric = wheelbase / (float) Math.tan(Math.toRadians(c.data.steeringLock));
        t.check("steering: low-speed turning circle matches the wheelbase and steering lock", radius > geometric * 0.75f && radius < geometric * 1.9f, String.format("radius %.1f m (geometric %.1f m)", radius, geometric));
        t.check("the car turns because the front tyres push it: steering smooths in and both front wheels take the lock", Math.abs(c.steering - 1) < 0.02f && Math.abs(c.wheels[0].steerAngle - (float) Math.toRadians(c.data.steeringLock)) < 0.01f && c.wheels[2].steerAngle == 0, null);
        // high speed: full lock asks for more than the tyres have
        c = car("car.volna", 300.5f, 150.5f, 0);
        settle(c, w);
        accelerateTo(c, w, 26, 40);
        boolean slid = false;
        float yaw0 = c.body.yaw(), maxLat = 0;
        for (int i = 0; i < 75; i++) { c.input.throttle = 0.6f; c.input.steer = 1; c.wake(); c.step(w); for (CarWheel wh : c.wheels) slid |= wh.state == CarWheel.State.SKIDDING || wh.state == CarWheel.State.SPINNING; maxLat = Math.max(maxLat, Math.abs(c.lateralSpeed())); }
        float turned = Math.abs(angle(c.body.yaw() - yaw0)), geoRate = 26 / geometric;
        t.check("steering: at speed full lock exceeds the grip - the tyres slide and the car turns far less than the wheels ask", slid && turned / 1.5f < geoRate * 0.6f && turned > 0.1f, String.format("yaw rate %.2f rad/s against %.2f geometric, slid sideways %.1f m/s", turned / 1.5f, geoRate, maxLat));

        // ---- TRACTION: acceleration and cornering share one budget
        c = car("car.hauler", 420.5f, 300.5f, 0);
        settle(c, w);
        boolean spun = false, skid = false;
        for (int i = 0; i < 150; i++) { c.input.throttle = 1; c.input.steer = 1; c.wake(); c.step(w); for (int k = 2; k < 4; k++) { spun |= c.wheels[k].state == CarWheel.State.SPINNING; skid |= c.wheels[k].state != CarWheel.State.NORMAL; } }
        CarWheel rw = c.wheels[2];
        t.check("traction: full throttle with lock on breaks the driven tyres loose (combined demand exceeds adhesion)", (spun || skid) && rw.adhesion > 0, "rear " + rw.state + String.format(", demand %.0f against adhesion %.0f", rw.demand, rw.adhesion));

        // ---- HANDBRAKE
        c = car("car.volna", 200.5f, 300.5f, 0);
        settle(c, w);
        accelerateTo(c, w, 18, 30);
        yaw0 = c.body.yaw();
        boolean locked = false;
        float speedIn = c.speed();
        for (int i = 0; i < 60; i++) { c.input.throttle = 0; c.input.steer = 1; c.input.handbrake = true; c.wake(); c.step(w); locked |= c.wheels[2].state == CarWheel.State.FIXED && c.wheels[3].state == CarWheel.State.FIXED; }
        float hbTurn = Math.abs(angle(c.body.yaw() - yaw0));
        t.check("handbrake: the rear wheels lock, the car keeps its momentum and the steering swings it round", locked && hbTurn > 0.5f && c.speed() > speedIn * 0.3f && c.wheels[2].rotationSpeed == 0,
                String.format("turned %.0f degrees in 1.2 s, speed %.1f -> %.1f m/s", Math.toDegrees(hbTurn), speedIn, c.speed()));

        // ---- SUSPENSION: one side over a hole
        c = car("car.kolos", 300.5f, 400.5f, 0);
        settle(c, w);
        for (int x = 298; x < 300; x++) set(w, x, G - 1, 420, Block.AIR);   // a pothole under the left wheels' track
        float maxDiff = 0;
        boolean hung = false;
        for (int i = 0; i < 500 && c.body.pz < 432; i++) { c.input.throttle = c.forwardSpeed() < 5 ? 0.6f : 0; c.input.steer = 0; c.input.handbrake = false; c.wake(); c.step(w); maxDiff = Math.max(maxDiff, Math.abs(c.wheels[0].compression - c.wheels[1].compression)); hung |= !c.wheels[0].grounded && c.wheels[1].grounded; }
        t.check("suspension: each wheel answers the ground under it - one drops into a hole while its neighbour stays loaded", maxDiff > 0.08f && c.body.pz >= 425 && hung, String.format("max left/right compression difference %.2f%s", maxDiff, hung ? ", left wheel hung free" : ""));
        restore(w);

        // ---- COLLISION: frontal, and off-centre
        c = car("car.volna", 300.5f, 500.5f, 0);
        settle(c, w);
        for (int x = 290; x <= 311; x++) for (int y = G; y < G + 3; y++) set(w, x, y, 540, Block.BEDROCK);
        accelerateTo(c, w, 14, 20);
        float before = c.speed();
        float worst = 0;
        for (int i = 0; i < 200; i++) { c.input.throttle = 0; c.wake(); c.step(w); worst = Math.max(worst, c.damage.lastImpact); }
        c.origin(o);
        t.check("collision: a wall stops the car - it does not pass through, and the impact magnitude is reported", o[2] < 540 && c.forwardSpeed() < 0.5f && c.speed() < 4 && c.damage.worstImpact > 5 && c.damage.deform[CarDamageState.FRONT] > 0.1f,
                String.format("%.1f m/s -> %.1f, impact %.1f m/s, front deformation %.2f", before, c.speed(), c.damage.worstImpact, c.damage.deform[CarDamageState.FRONT]));
        restore(w);
        c = car("car.volna", 300.5f, 500.5f, 0);
        settle(c, w);
        for (int x = 290; x <= 300; x++) for (int y = G; y < G + 3; y++) set(w, x, y, 540, Block.BEDROCK);    // only the left half of the lane
        accelerateTo(c, w, 14, 20);
        float maxSpin = 0;
        for (int i = 0; i < 120; i++) { c.input.throttle = 0; c.wake(); c.step(w); maxSpin = Math.max(maxSpin, Math.abs(c.body.wy)); }
        t.check("collision: an off-centre impact turns the car as well as slowing it", maxSpin > 0.5f, String.format("peak yaw rate %.2f rad/s", maxSpin));
        restore(w);
        // side impact: a car sliding sideways into a wall
        c = car("car.volna", 300.5f, 520.5f, 0);
        settle(c, w);
        for (int z = 505; z <= 535; z++) for (int y = G; y < G + 3; y++) set(w, 303, y, z, Block.BEDROCK);
        c.body.vx = 10; c.wake();
        for (int i = 0; i < 100; i++) { c.input.clear(); c.wake(); c.step(w); }
        c.origin(o);
        t.check("collision: a side impact stops the slide and marks the side that was hit", o[0] < 303 && Math.abs(c.body.vx) < 1.5f && c.damage.deform[CarDamageState.RIGHT] > 0.02f, String.format("x %.2f, lateral speed %.2f, right side %.2f", o[0], c.body.vx, c.damage.deform[CarDamageState.RIGHT]));
        restore(w);

        // ---- SURFACE
        float[] stop = new float[2];
        for (int s = 0; s < 2; s++) {
            if (s == 1) for (int x = 395; x <= 405; x++) for (int z = 190; z <= 330; z++) set(w, x, G - 1, z, Block.GRASS);
            c = car("car.volna", 400.5f, 200.5f, 0);
            settle(c, w);
            accelerateTo(c, w, 16, 40);
            stop[s] = stopDistance(c, w);
        }
        t.check("surface: the same stop takes longer on grass than on asphalt, wheel by wheel", stop[1] > stop[0] * 1.25f, String.format("asphalt %.1f m, grass %.1f m", stop[0], stop[1]));
        restore(w);
        // two wheels on the road, two on grass: each reads its own surface
        for (int x = 401; x <= 405; x++) for (int z = 190; z <= 330; z++) set(w, x, G - 1, z, Block.GRASS);
        c = car("car.volna", 400.9f, 200.5f, 0);
        settle(c, w);
        t.check("a car straddling two surfaces has each wheel on its own", c.wheels[0].surface != c.wheels[1].surface, c.wheels[0].surface + " / " + c.wheels[1].surface);
        restore(w);

        // ---- AIRBORNE
        for (int x = 440; x <= 460; x++) for (int z = 330; z <= 420; z++) for (int y = G - 8; y < G; y++) set(w, x, y, z, Block.AIR);
        c = car("car.volna", 450.5f, 290.5f, 0);
        settle(c, w);
        accelerateTo(c, w, 15, 20);
        int air = 0;
        float vyStart = 0, vyEnd = 0, hsStart = 0, hsEnd = 0;
        boolean pushed = false;
        for (int i = 0; i < 300 && c.body.py > G - 6; i++) {
            c.input.throttle = 0; c.wake(); c.step(w);
            if (c.groundedWheels == 0) {
                if (air == 0) { vyStart = c.body.vy; hsStart = (float) Math.hypot(c.body.vx, c.body.vz); }
                air++;
                vyEnd = c.body.vy; hsEnd = (float) Math.hypot(c.body.vx, c.body.vz);
                for (CarWheel wh : c.wheels) pushed |= wh.normalImpulse != 0 || wh.forwardImpulse != 0 || wh.sideImpulse != 0;
            }
        }
        float tAir = (air - 1) * CarVehicle.FIXED_DT, gMeasured = tAir <= 0 ? 0 : (vyStart - vyEnd) / tAir;
        t.check("airborne: off the ground the car falls under gravity, keeps its momentum, and its wheels push on nothing", air > 15 && Math.abs(gMeasured - CarSuspension.GRAVITY) < 1.6f && hsEnd > hsStart * 0.9f && !pushed,
                String.format("%d steps airborne, measured g %.2f, horizontal %.1f -> %.1f m/s", air, gMeasured, hsStart, hsEnd));
        restore(w);

        // ---- overturned cars stay overturned
        c = car("car.courier", 300.5f, 300.5f, 0);
        c.body.qw = 0; c.body.qx = 0; c.body.qy = 0; c.body.qz = 1;      // on its roof
        c.body.updateRotation();
        c.body.py = G + 1.6f;
        for (int i = 0; i < 300; i++) { c.wake(); c.step(w); }
        t.check("an overturned vehicle is not set back on its wheels", c.overturned() && c.upright() < -0.5f && c.groundedWheels == 0, String.format("upright %.2f", c.upright()));

        // ---- VEHICLE VARIATION: one simulation, different numbers
        String[] ids = {"car.sprite", "car.courier", "car.ural", "car.patrol"};
        float[] t15 = new float[ids.length], roll = new float[ids.length];
        for (int k = 0; k < ids.length; k++) {
            CarVehicle v = car(ids[k], 300.5f, 250.5f, 0);
            settle(v, w);
            t15[k] = accelerateTo(v, w, 15, 60)[0];
            for (int i = 0; i < 60; i++) { v.input.throttle = 0.3f; v.input.steer = 0.6f; v.wake(); v.step(w); roll[k] = Math.max(roll[k], Math.abs(v.body.rightY())); }
        }
        t.check("variation: handling profiles differ in acceleration and body roll with no vehicle-specific code", t15[1] > t15[0] * 1.25f && t15[3] < t15[1] && roll[1] > roll[0] * 1.15f,
                String.format("0-15 m/s: coupe %.1f s, van %.1f s, 4x4 %.1f s, patrol %.1f s; roll coupe %.3f van %.3f", t15[0], t15[1], t15[2], t15[3], roll[0], roll[1]));
        // drivetrains put the thrust where the data says
        CarVehicle fwd = car("car.sprite", 300.5f, 250.5f, 0), rwd = car("car.volna", 320.5f, 250.5f, 0), awd = car("car.kolos", 340.5f, 250.5f, 0);
        for (CarVehicle v : new CarVehicle[]{fwd, rwd, awd}) { settle(v, w); drive(v, w, 0.2f, 1, 0, 0, false); }
        t.check("drivetrain: front-, rear- and four-wheel drive put thrust on the wheels the data names", fwd.wheels[0].driveImpulse > 0 && fwd.wheels[2].driveImpulse == 0 && rwd.wheels[0].driveImpulse == 0 && rwd.wheels[2].driveImpulse > 0 && awd.wheels[0].driveImpulse > 0 && awd.wheels[3].driveImpulse > 0
                && Math.abs(awd.wheels[0].driveImpulse * 4 - awd.driveAcceleration * awd.data.mass * CarVehicle.FIXED_DT) < 1f, null);
        // gears: hysteresis, never hunting
        c = car("car.volna", 300.5f, 250.5f, 0);
        settle(c, w);
        int shiftsBefore;
        accelerateTo(c, w, c.transmission.upshiftAt(2) + 0.1f, 30);
        shiftsBefore = c.transmission.shifts;
        for (int i = 0; i < 400; i++) { c.input.throttle = 0.22f; c.input.steer = 0; c.wake(); c.step(w); }
        t.check("transmission: separate up and down thresholds - no hunting at a gear boundary", c.transmission.shifts - shiftsBefore <= 2 && c.transmission.upshiftAt(2) > c.transmission.downshiftAt(3), (c.transmission.shifts - shiftsBefore) + " changes in 8 s near the boundary");
        // reverse
        c = car("car.volna", 300.5f, 250.5f, 0);
        settle(c, w);
        for (int i = 0; i < 200; i++) { c.input.fromKeys(false, true, false, false, false, c.forwardSpeed()); c.wake(); c.step(w); }
        t.check("reverse has its own gear and its own limit", c.transmission.gear == 0 && c.forwardSpeed() < -2 && c.forwardSpeed() > -c.data.maxReverseVelocity - 0.5f, String.format("%.1f m/s in R", c.forwardSpeed()));

        // ---- BASIC DAMAGE: a burst tyre
        c = car("car.volna", 300.5f, 200.5f, 0);
        settle(c, w);
        accelerateTo(c, w, 16, 40);
        float clean = stopDistance(c, w);
        c = car("car.volna", 300.5f, 200.5f, 0);
        c.damage.burst(CarWheel.FRONT_LEFT); c.damage.burst(CarWheel.REAR_LEFT);
        settle(c, w);
        accelerateTo(c, w, 16, 60);
        float xDrift0 = c.body.px;
        float flat = stopDistance(c, w);
        t.check("a burst tyre grips less and pulls: longer stops, the car wanders, and it can still be driven", flat > clean * 1.08f && c.wheels[0].tyre == CarWheel.Tyre.BURST && Math.abs(c.body.px - 300.5f) > 0.05f,
                String.format("stop %.1f m intact, %.1f m with two flats, drift %.2f m", clean, flat, Math.abs(c.body.px - xDrift0)));

        // ---- FRAME RATE: the same simulation whatever the display does
        // frame times that are whole multiples of the step are bit-for-bit identical ...
        float[][] end = new float[3][];
        float[] exact = {0.04f, 0.02f, 0.01f};
        for (int k = 0; k < 3; k++) end[k] = simulate(w, exact[k], 8f);
        float dev = 0;
        for (int k = 1; k < 3; k++) for (int i = 0; i < 4; i++) dev = Math.max(dev, Math.abs(end[k][i] - end[0][i]));
        t.check("frame rate: 25, 50 and 100 fps give exactly the same car in the same place (400 fixed steps)", dev == 0 && end[0][4] == 400 && end[1][4] == 400 && end[2][4] == 400, String.format("max deviation %g; end %.2f, %.2f after %.0f steps", dev, end[0][0], end[0][1], end[0][4]));
        // ... and awkward ones differ only by how many whole steps fitted into the same eight seconds
        float[] odd = {1 / 30f, 1 / 60f, 1 / 144f};
        float worstGap = 0;
        float[] ref = simulate(w, odd[0], 8f);
        for (int k = 1; k < 3; k++) {
            float[] e = simulate(w, odd[k], 8f);
            float gap = (float) Math.hypot(e[0] - ref[0], e[1] - ref[1]), allowed = Math.abs(e[4] - ref[4]) * CarVehicle.FIXED_DT * Math.max(e[3], ref[3]) + 0.05f;
            worstGap = Math.max(worstGap, gap - allowed);
        }
        t.check("frame rate: 30, 60 and 144 fps differ by no more than the steps that fitted in", worstGap <= 0, String.format("excess %.3f m", worstGap));
        restore(w);
    }

    /** Drives a fixed script for a span of real time delivered in frames of one length: {x, z, yaw, speed, steps}. */
    private static float[] simulate(World w, float frameDt, float seconds) {
        CarVehicle v = car("car.volna", 300.5f, 300.5f, 0);
        int frames = Math.round(seconds / frameDt);
        for (int i = 0; i < frames; i++) { v.input.throttle = 1; v.input.steer = 0.3f; v.input.brake = 0; v.input.handbrake = false; v.wake(); v.update(frameDt, w); }
        return new float[]{v.body.px, v.body.pz, v.body.yaw(), v.speed(), v.steps};
    }

    private static float angle(float a) { while (a > Math.PI) a -= 2 * Math.PI; while (a < -Math.PI) a += 2 * Math.PI; return a; }
}
