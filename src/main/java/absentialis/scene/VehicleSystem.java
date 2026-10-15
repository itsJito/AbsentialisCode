package absentialis.scene;

import absentialis.engine.audio.UltraEngine;
import absentialis.engine.math.Mat4;
import absentialis.engine.render.HybridRenderer;
import absentialis.engine.render.ModelRenderer;
import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.ui.Ui;
import absentialis.vehicle.CarDamageState;
import absentialis.vehicle.CarHandlingData;
import absentialis.vehicle.CarVehicle;
import absentialis.vehicle.CarVisualController;
import absentialis.vehicle.CarWheel;
import absentialis.world.Block;
import absentialis.world.MapData;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.List;

/**
 * Vehicles in an operation: where they are, who is driving, and everything that connects the handling simulation
 * (package vehicle) to the rest of the raid - getting in and out, running people over, bullets through bodywork
 * and tyres, the noise an engine makes in a quiet city, lights, sound and the driver's HUD.
 *
 * Vehicles are rare. An operation never holds more than {@link #MAX} of them: the one the player brought from
 * the hideout, if any, and a few abandoned ones on the roads that still run.
 */
public final class VehicleSystem implements CarVehicle.Listener {
    public static final int MAX = 4;

    /** A car in the world with the things that hang off it. */
    public static final class Car {
        public final CarVehicle sim;
        public final CarVisualController visual;
        public boolean owned;                  // brought by the player: goes back to the garage if it is driven out
        UltraEngine.Voice engine, skid, roll;
        float engineNote = 0.6f, skidGain, noiseT, hornT;
        boolean hornHeard;
        /** 0.6: the driver's door is a door - the same entity, hinge, latch and damage as a building's (scene.Doors), carried by the car. */
        public Doors.Door door;
        /** 1.1: and the passenger's. */
        public Doors.Door door2;
        /** Squadmates riding in this car, in seat order (front passenger, then the back seat). */
        public final List<Actor> riders = new ArrayList<>();
        Car(CarVehicle sim) { this.sim = sim; this.visual = new CarVisualController(sim); }
        public float speed() { return sim.speed(); }
        private final float[] p = new float[3];
        /** Point the chase camera orbits: above the middle of the car. */
        public float[] cameraAnchor() { sim.point(0, sim.data.height * 0.75f, 0, p); return p; }
    }

    private enum Phase { NONE, ENTERING, DRIVING, EXITING }

    public final List<Car> cars = new ArrayList<>();
    /** The car the player is in control of (null on foot, and while still climbing in or out). */
    public Car driving;
    /** The camera is in the driver's seat this frame. */
    public boolean cockpit;
    private Car using;                 // the car an entry or exit animation is playing on
    private Phase phase = Phase.NONE;
    private float phaseT, fromX, fromY, fromZ, toX, toY, toZ, lastCarYaw;
    private static final float ENTER_TIME = 0.95f, EXIT_TIME = 0.8f;
    /** What a car door takes before its hinges go, and the most squadmates a car carries. */
    public static final float DOOR_HEALTH = 85f;
    public static final int MAX_RIDERS = 3;
    /** Doors torn off cars, squadmates who have got in, and who have got out (gates). */
    public int doorsLost, boarded, alighted;

    /** Keeps a car's door where the car is: the hinge, the heading and the velocity it shares with the body. */
    private void carry(Car c) {
        Doors.Door d = c.door;
        if (d == null) return;
        CarVisualController v = c.visual;
        c.sim.point(v.hingeX(), v.hingeY() + v.doorHeight() * 0.5f, v.hingeZ() - v.doorLength() * 0.5f, v3);
        d.mx = v3[0]; d.my = v3[1]; d.mz = v3[2];
        d.myaw = c.sim.body.yaw();
        d.mvx = c.sim.body.vx; d.mvy = 0; d.mvz = c.sim.body.vz;
        if (d.destroyed && !v.doorGone) { v.doorGone = true; doorsLost++; }
        v.doorOpen = d.destroyed ? 0 : Math.min(1, Math.abs(d.angle) / Math.max(0.01f, d.maxOpen));
        Doors.Door d2 = c.door2;
        if (d2 == null) return;
        c.sim.point(v.hinge2X(), v.hingeY() + v.doorHeight() * 0.5f, v.hingeZ() - v.doorLength() * 0.5f, v3);
        d2.mx = v3[0]; d2.my = v3[1]; d2.mz = v3[2];
        d2.myaw = c.sim.body.yaw();
        d2.mvx = c.sim.body.vx; d2.mvy = 0; d2.mvz = c.sim.body.vz;
        if (d2.destroyed && !v.door2Gone) { v.door2Gone = true; doorsLost++; }
        v.doorOpen2 = d2.destroyed ? 0 : Math.min(1, Math.abs(d2.angle) / Math.max(0.01f, d2.maxOpen));
    }

    private boolean hasDoor(Car c) { return c.door != null && !c.door.destroyed; }
    private boolean hasDoor2(Car c) { return c.door2 != null && !c.door2.destroyed; }
    /** 1.1: times the passenger's door has been opened to get in, and the widest it stood on the last such entry (0..1). */
    public int passengerDoorOpened;
    public float passengerDoorPeak;

    /** Where rider n sits, in the car's frame: beside the driver, then the back seat. */
    private void riderSeat(Car c, int n, float[] out) {
        CarVisualController v = c.visual;
        float back = Math.min(0.95f, c.sim.data.length * 0.2f);
        float lx = n == 0 ? -v.seatX : n == 1 ? v.seatX : -v.seatX, lz = n == 0 ? v.seatZ : v.seatZ - back;
        c.sim.point(lx, v.seatY, lz, out);
    }

    /**
     * The element gets in with its leader. Squadmates on their feet and close by take the passenger seat and the
     * back seat; they ride there - carried by the car, out of the fight - until the driver gets out.
     */
    private void board(Car c) {
        for (Actor m : new ArrayList<>(raid.mates)) {
            if (c.riders.size() >= MAX_RIDERS) break;
            if (m.dead || m.seated || m.ragdoll != null || m.downed() || m.crawling) continue;
            c.sim.origin(v3);
            if (m.distXZ(v3[0], v3[2]) > BOARD_RANGE) continue;
            m.seated = true; m.vx = m.vy = m.vz = 0; m.hasGoal = false; m.path = null; m.crouch = false;
            if (m.anim != null) m.anim.play("car_enter", 0.7f);
            c.riders.add(m);
            boarded++;
        }
        if (!c.riders.isEmpty()) raid.emitSound(raid.player.x, raid.player.y + 1, raid.player.z, 9, "car_door", raid.player.id, false);
    }
    public static final float BOARD_RANGE = 14f;
    /** 0.8: how far below the seat point a sitting body's feet are put. 0.7 used 0.55, which sat everyone on top of the seat back: a driver's head came out through the roof. */
    public static final float SEAT_DROP = 0.86f;

    /** Everyone out: each rider is put on its feet at a clear spot beside the car (a rider with nowhere to stand stays in). */
    private void alight(Car c) {
        CarHandlingData d = c.sim.data;
        float[][] spots = {{d.width / 2 + 0.8f, c.visual.seatZ}, {d.width / 2 + 0.8f, c.visual.seatZ - 1.1f}, {-d.width / 2 - 0.8f, c.visual.seatZ - 1.1f}, {d.width / 2 + 0.8f, 0.9f}, {-d.width / 2 - 0.8f, 0.9f}, {0.5f, -d.length / 2 - 1f}, {-0.5f, -d.length / 2 - 1f}, {0, d.length / 2 + 1f}};
        int next = 0;
        for (java.util.Iterator<Actor> it = c.riders.iterator(); it.hasNext(); ) {
            Actor m = it.next();
            boolean out = false;
            for (; next < spots.length && !out; next++) {
                c.sim.point(spots[next][0], 0.3f, spots[next][1], v3);
                int gy = floorAt(v3[0], v3[1] + 1.2f, v3[2]);
                if (gy < 0 || !Physics.boxFree(raid.world, v3[0], gy, v3[2], 0.3f, 1.8f)) continue;
                m.x = v3[0]; m.y = gy; m.z = v3[2]; m.vx = m.vy = m.vz = 0; m.onGround = true; m.fallSpeed = 0;
                out = true;
            }
            if (!out && !m.dead) continue;
            m.seated = false;
            if (m.anim != null && !m.dead) m.anim.play("car_exit", 0.6f);
            it.remove();
            alighted++;
        }
    }

    /** Riders are carried in their seats, facing the way the car faces. A rider who dies stays where it sat. */
    private void ride(Car c) {
        for (int i = 0; i < c.riders.size(); i++) {
            Actor m = c.riders.get(i);
            riderSeat(c, i, v3);
            m.x = v3[0]; m.y = v3[1] - SEAT_DROP; m.z = v3[2];
            m.vx = c.sim.body.vx; m.vy = 0; m.vz = c.sim.body.vz;
            m.yaw = playerYaw(c.sim); m.faceSet = false; m.onGround = true; m.fallSpeed = 0;
        }
        // a car on its roof, or burning, is no place to sit: they get themselves out
        if (!c.riders.isEmpty() && c != driving && c != using || c.sim.overturned() && !c.riders.isEmpty()) alight(c);
    }
    private final Raid raid;
    private float enterTime = ENTER_TIME, exitTime = EXIT_TIME;
    /** 0.7, getting in and out in stages: the share of the time spent reaching the door, when the door is shut again, how long the door has to open before the body moves out, and how much longer the whole thing takes for being done properly. */
    public static float TO_DOOR = 0.34f, SHUT_AT = 0.8f, OPEN_FIRST = 0.26f, STAGED = 1.3f;
    private boolean hasDoorSpot;
    private float doorX, doorY, doorZ;
    /** Times a door has been shut behind somebody getting in or out (gates). */
    public int doorCycles;
    /** How far the door being used is open, 0..1 (gates and the capture script). */
    public float doorOpen() { Car c = using != null ? using : driving; return c == null || c.door == null ? 0 : Math.min(1, Math.abs(c.door.angle) / Math.max(0.01f, c.door.maxOpen)); }
    public float phaseProgress() { return phase == Phase.ENTERING ? Math.min(1, phaseT / enterTime) : phase == Phase.EXITING ? Math.min(1, phaseT / exitTime) : -1; }
    private boolean doorShut, passengerShut;
    private final float[] v3 = new float[3], v3b = new float[3];
    public int roadkills, impacts;
    /** 0.7: people knocked down by a car without being killed, and bodies on the road driven over. */
    public int knockdowns, runOvers;
    /** Closing speed (m/s) above which a pedestrian goes down, and below which it is the legs that are taken (above it the whole body is thrown). */
    public static float KNOCK_SPEED = 3.2f, SCOOP_SPEED = 9f;
    public String playerVehicleLost;
    private boolean fwd, back, left, right, hand;

    VehicleSystem(Raid raid) { this.raid = raid; }

    // ------------------------------------------------------------------ population
    /** Places the player's own vehicle (if one was chosen) by the insertion, and a few abandoned ones on the roads. */
    void populate(String playerVehicleId) {
        CarHandlingData.load();
        World w = raid.world;
        if (playerVehicleId != null && !playerVehicleId.isEmpty() && CarHandlingData.get(playerVehicleId) != null) {
            float[] spot = parkingNear(raid.player.x, raid.player.z, 4, 34, CarHandlingData.get(playerVehicleId));
            if (spot != null) { Car c = spawn(playerVehicleId, spot[0], spot[1], spot[2], spot[3], -1); c.owned = true; }
            else raid.notice("No room to bring your vehicle in at this insertion - it stays in the garage.", 0xFFE2A034);
        }
        // abandoned vehicles: a couple per operation at most, on street nodes well away from the player and each other
        List<CarHandlingData> all = CarHandlingData.all();
        int want = Math.min(MAX - cars.size(), 1 + raid.rng.nextInt(3));
        List<MapData.Node> nodes = new ArrayList<>(w.map.nodes);
        int tries = 0;
        while (want > 0 && !nodes.isEmpty() && tries++ < 240) {
            MapData.Node n = nodes.remove(raid.rng.nextInt(nodes.size()));
            if (raid.player.distXZ(n.x, n.z) < 70 || World.outsideDistance(n.x, n.z) > 0) continue;
            boolean crowded = false;
            for (Car c : cars) { c.sim.origin(v3); if (Math.abs(v3[0] - n.x) + Math.abs(v3[2] - n.z) < 140) crowded = true; }
            if (crowded) continue;
            CarHandlingData d = all.get(raid.rng.nextInt(all.size()));
            float[] spot = parkingNear(n.x + 0.5f, n.z + 0.5f, 0, 10, d);
            if (spot == null) continue;
            Car c = spawn(d.id, spot[0], spot[1], spot[2], spot[3], raid.rng.nextInt(1 << 20));
            // left in the open for months: some have a flat, some have been hit
            if (raid.rng.chance(0.35)) c.sim.damage.burst(raid.rng.nextInt(4));
            if (raid.rng.chance(0.4)) c.sim.damage.dentZone(raid.rng.nextInt(4), 0.3f + raid.rng.nextFloat() * 0.4f, c.sim.data, new java.util.Random(raid.rng.nextInt(1 << 30)));
            want--;
        }
    }

    public Car spawn(String id, float x, float y, float z, float yaw, int paintSeed) {
        if (cars.size() >= MAX) return null;
        CarHandlingData d = CarHandlingData.get(id);
        CarVehicle sim = new CarVehicle(d);
        if (d.paints.length > 0) sim.paint = d.paints[paintSeed < 0 ? 0 : paintSeed % d.paints.length];
        sim.place(x, y, z, yaw);
        sim.listener = this;
        Car c = new Car(sim);
        cars.add(c);
        c.door = raid.doors.addMobile(c, DOOR_HEALTH, 1.15f, c.visual.doorLength(), c.visual.doorHeight());
        c.door.model = c.visual.doorLeaf(); c.door.modelKey = c.visual.doorKey(); c.door.vox = CarVisualController.VOX;
        c.door2 = raid.doors.addMobile(c, DOOR_HEALTH, 1.15f, c.visual.doorLength(), c.visual.doorHeight());
        c.door2.model = c.door.model; c.door2.modelKey = c.door.modelKey; c.door2.vox = CarVisualController.VOX;
        carry(c);
        return c;
    }

    /**
     * Developer spawn: the named vehicle on the nearest clear, level ground in front of the player. The limit of
     * {@link #MAX} vehicles holds here too - the one furthest from the player that nobody is using makes room.
     */
    public Car debugSpawn(String id) {
        CarHandlingData d = CarHandlingData.get(id);
        if (d == null) return null;
        Actor p = raid.player;
        float fx = (float) -Math.sin(p.yaw), fz = (float) -Math.cos(p.yaw);
        float[] spot = null;
        for (float dist = 7; dist <= 16 && spot == null; dist += 3) spot = parkingNear(p.x + fx * dist, p.z + fz * dist, 0, 4, d);
        if (spot == null) spot = parkingNear(p.x, p.z, 4, 20, d);
        if (spot == null) return null;
        if (cars.size() >= MAX) {
            Car far = null;
            float fd = -1;
            for (Car c : cars) { if (c == driving || c == using) continue; c.sim.origin(v3); float dd = p.distXZ(v3[0], v3[2]); if (dd > fd) { fd = dd; far = c; } }
            if (far == null) return null;
            remove(far);
        }
        return spawn(id, spot[0], spot[1], spot[2], spot[3], raid.rng.nextInt(1 << 20));
    }

    /** Removes every vehicle nobody is in. Returns how many went. */
    public int debugClear() {
        int n = 0;
        for (Car c : new ArrayList<>(cars)) if (c != driving && c != using) { remove(c); n++; }
        return n;
    }

    private void remove(Car c) {
        alight(c);
        if (c.door != null) c.door.destroyed = true;
        if (c.door2 != null) c.door2.destroyed = true;
        stop(c);
        cars.remove(c);
        gone.add(c);
    }

    /** Vehicles taken out of the world whose cached meshes the renderer still has to release. */
    private final List<Car> gone = new ArrayList<>();

    /** A flat, clear rectangle big enough for the car, on ground it can stand on: {x, y, z, yaw} or null. */
    private float[] parkingNear(float x, float z, int minR, int maxR, CarHandlingData d) {
        World w = raid.world;
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        for (int r = minR; r <= maxR; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            int px = ix + dx, pz = iz + dz;
            int gy = raid.groundY(px, pz);
            if (gy < 0 || gy > World.GROUND + 1 || w.sky(px, gy + 1, pz) < 13) continue;
            for (int o = 0; o < 2; o++) {
                int hw = (int) Math.ceil((o == 0 ? d.width : d.length) / 2 + 0.3f), hl = (int) Math.ceil((o == 0 ? d.length : d.width) / 2 + 0.3f);
                if (!clear(px, gy, pz, hw, hl, (int) Math.ceil(d.height) + 1)) continue;
                return new float[]{px + 0.5f, gy, pz + 0.5f, o == 0 ? (raid.rng.chance(0.5) ? 0 : (float) Math.PI) : (raid.rng.chance(0.5) ? 1.5708f : -1.5708f)};
            }
        }
        return null;
    }

    private boolean clear(int px, int gy, int pz, int hw, int hl, int h) {
        World w = raid.world;
        for (int x = px - hw; x <= px + hw; x++) for (int z = pz - hl; z <= pz + hl; z++) {
            if (!w.solid(x, gy - 1, z)) return false;
            Block under = w.block(x, gy - 1, z);
            if (under == null || under.is(Block.LIQUID)) return false;
            for (int y = gy; y < gy + h; y++) if (w.get(x, y, z) != 0) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ getting in and out
    /** The car whose driver's door (or any side, if that one is blocked) the player is standing at. */
    public Car nearby() {
        if (phase != Phase.NONE) return null;
        Actor p = raid.player;
        Car best = null;
        float bd = 1e9f;
        for (Car c : cars) {
            c.sim.body.toLocal(p.x, p.y + 0.9f, p.z, v3);
            float lx = v3[0] + c.sim.data.comX, lz = v3[2] + c.sim.data.comZ;
            float ex = Math.abs(lx) - c.sim.data.width / 2, ez = Math.abs(lz) - c.sim.data.length / 2;
            float d = Math.max(ex, ez);
            if (d < 1.5f && d < bd && Math.abs(v3[1]) < 2.2f) { bd = d; best = c; }
        }
        return best;
    }

    public String prompt() {
        if (phase == Phase.DRIVING) return null;
        Car c = nearby();
        if (c == null) return null;
        if (c.sim.overturned()) return c.sim.data.name + " - overturned";
        return (passengerSide(c) ? "[F] Get in " : "[F] Drive ") + c.sim.data.name + (passengerSide(c) ? " (passenger side)" : "") + (c.sim.damage.engine <= 0 ? "  (engine dead)" : c.sim.damage.burstCount() > 0 ? "  (" + c.sim.damage.burstCount() + " flat)" : "");
    }

    /** 0.9: which side of the car the player is standing on - true when it is the side away from the wheel. */
    public boolean passengerSide(Car c) {
        Actor p = raid.player;
        c.sim.body.toLocal(p.x, p.y + 0.9f, p.z, v3);
        float lx = v3[0] + c.sim.data.comX, lz = v3[2] + c.sim.data.comZ;
        // beside the cabin, on the far side from the driver's seat (at the bonnet or the boot there is no side to speak of)
        return lx * Math.signum(c.visual.seatX) < -0.25f && Math.abs(lz) < c.sim.data.length / 2 + 0.2f;
    }
    /** True while the player is getting in through the passenger's side (tests, the capture script). */
    public boolean viaPassenger() { return viaPassenger; }
    private boolean viaPassenger;
    /** Times the player has got in from the passenger's side and moved across (tests). */
    public int passengerEntries;
    /** How much longer getting in takes when the seat has to be crossed, and the share of the way in that is spent getting into the near seat. */
    public static float ACROSS = 1.4f, NEAR_SEAT = 0.58f;

    public boolean busy() { return phase == Phase.ENTERING || phase == Phase.EXITING; }
    public boolean inside() { return phase == Phase.DRIVING || phase == Phase.ENTERING; }

    /** Starts climbing in. False when there is no usable car in reach. */
    public boolean enter() {
        if (!raid.playerCanDrive) { raid.msg("This actor cannot use a vehicle.", 0xFFE2A034); return false; }
        Car c = nearby();
        if (c == null || c.sim.overturned()) return false;
        Actor p = raid.player;
        using = c;
        phase = Phase.ENTERING;
        phaseT = 0;
        fromX = p.x; fromY = p.y; fromZ = p.z;
        p.vx = p.vy = p.vz = 0;
        // 0.6: a getting-in animation of its own, and the door is opened through the door engine - if the car still has one
        // 0.7: getting in is three things in order - to the door while it swings open, in through it, and the door pulled shut -
        // and each has its share of the time. The body no longer starts for the seat while the door is still closed in front of it.
        // 0.9: you get in on the side you are standing at. From the passenger's side that is the passenger's door and seat, and
        // then across to the wheel - the body is no longer carried round the car to the driver's door first
        viaPassenger = passengerSide(c);
        float[] at = viaPassenger ? sideSpot(c, -Math.signum(c.visual.seatX)) : exitSpot(c);
        hasDoorSpot = at != null;
        if (hasDoorSpot) { doorX = at[0]; doorY = at[1]; doorZ = at[2]; }
        enterTime = (hasDoor(c) && !viaPassenger ? ENTER_TIME : ENTER_TIME * 0.72f) * STAGED * (viaPassenger ? ACROSS : 1);
        if (p.anim != null) p.anim.play("car_enter", enterTime);
        if (viaPassenger) { passengerEntries++; if (hasDoor2(c)) { raid.doors.swing(c.door2, true); passengerDoorOpened++; } raid.emitSound(p.x, p.y + 1, p.z, 9, "car_door", p.id, false); }
        else if (hasDoor(c)) { raid.doors.swing(c.door, true); raid.emitSound(p.x, p.y + 1, p.z, 9, "car_door", p.id, false); }
        c.sim.wake();
        return true;
    }

    /** The car somebody is sitting in (the driver's, or one they ride in), or null. */
    public Car carOf(Actor a) {
        if (a.isPlayer) return driving != null ? driving : using;
        for (Car c : cars) if (c.riders.contains(a)) return c;
        return null;
    }

    /** 1.1: a blow meant for somebody inside lands on the car: bodywork on that side, a shove, the noise of it. */
    public void struck(Actor inside, Actor by, float dmg, float dx, float dz) {
        Car c = carOf(inside);
        if (c == null) return;
        float l = Math.max(0.01f, (float) Math.sqrt(dx * dx + dz * dz));
        dx /= l; dz /= l;
        // which side it came from, in the car's own frame
        float yaw = c.sim.body.yaw(), lx = dx * (float) Math.cos(yaw) - dz * (float) Math.sin(yaw), lz = dx * (float) Math.sin(yaw) + dz * (float) Math.cos(yaw);
        CarHandlingData d = c.sim.data;
        c.sim.damage.dent(-Math.signum(lx) * d.width * 0.5f * (Math.abs(lx) > Math.abs(lz) ? 1 : 0.3f), 0.9f, Math.abs(lz) > Math.abs(lx) ? -Math.signum(lz) * d.length * 0.4f : c.visual.seatZ, 0.12f + Math.min(0.25f, dmg / 160f), 0.9f);
        c.sim.wake();
        c.sim.body.vx += dx * Math.min(3.5f, dmg / 9f); c.sim.body.vz += dz * Math.min(3.5f, dmg / 9f);
        c.sim.origin(v3);
        raid.emitSound(v3[0], v3[1] + 1, v3[2], 40, "car_crash", by == null ? 0 : by.id, false);
        if (raid.fx != null) raid.fx.dust(v3[0], v3[1] + 0.8f, v3[2], dx, 0.4f, dz, 8, 0.6f, 0x8A8478, 0.4f);
        blowsTaken++;
    }
    /** Blows a car has taken for the people in it; times the survivor has thrown themselves out of one that was moving; entries called off. */
    public int blowsTaken, bailOuts, entriesCancelled;
    /** The speed (m/s) above which getting out is jumping out. */
    public static float BAIL_SPEED = 4.5f;

    /**
     * 1.1: out of a moving car. There is no speed at which the door cannot be opened: above a walking pace the survivor
     * goes out of it with the car's speed and no feet under them - a body in the road (the Sydney Engine has it from the
     * moment it leaves the seat), hurt by how fast that was, getting up when it has stopped rolling. The car carries on
     * with nobody at the wheel until something stops it.
     */
    private String bailOut() {
        Car c = driving;
        Actor p = raid.player;
        float sp = c.sim.speed();
        // out of the driver's side if there is air there, the other side if not, straight up and over as the last resort
        float[] at = null;
        for (float s : new float[]{Math.signum(c.visual.seatX), -Math.signum(c.visual.seatX)}) {
            c.sim.point(s * (c.sim.data.width / 2 + 0.7f), 0.5f, c.visual.seatZ, v3);
            if (Physics.boxFree(raid.world, v3[0], v3[1], v3[2], 0.3f, 1.2f)) { at = new float[]{v3[0], v3[1], v3[2], s}; break; }
        }
        if (at == null) return "No room to get the door open";
        float cvx = c.sim.body.vx, cvz = c.sim.body.vz;
        // sideways out of the car as well as along with it
        c.sim.point(at[3], 0, 0, v3); c.sim.origin(v3b);
        float ox = v3[0] - v3b[0], oz = v3[2] - v3b[2], ol = Math.max(0.01f, (float) Math.sqrt(ox * ox + oz * oz));
        if (hasDoor(c)) raid.doors.swing(c.door, true);
        c.sim.input.clear();                       // nobody is steering it now; it rolls on
        alight(c);
        driving = null; using = null; phase = Phase.NONE;
        p.seated = false; p.atWheel = false; p.steer = 0;
        p.x = at[0]; p.y = at[1]; p.z = at[2];
        p.vx = cvx + ox / ol * 2.2f; p.vz = cvz + oz / ol * 2.2f; p.vy = 1.2f; p.onGround = false;
        raid.emitSound(p.x, p.y + 1, p.z, 14, "car_door", p.id, false);
        raid.bailedOut(p, sp);
        bailOuts++;
        return null;
    }

    /** 1.1: getting in can be called off. Until the door has been pulled shut the survivor steps back out to where they came from. */
    public boolean cancelEnter() {
        if (phase != Phase.ENTERING || using == null || phaseT / enterTime > SHUT_AT) return false;
        Actor p = raid.player;
        float[] spot = hasDoorSpot ? new float[]{doorX, doorY, doorZ} : new float[]{fromX, fromY, fromZ};
        float k = Math.min(1, phaseT / enterTime);
        phase = Phase.EXITING;
        fromX = p.x; fromY = p.y; fromZ = p.z;
        toX = spot[0]; toY = spot[1]; toZ = spot[2];
        // as long going back as it was coming, and no waiting for a door that is already open
        exitTime = Math.max(0.3f, enterTime * k * 0.8f);
        phaseT = exitTime * OPEN_FIRST;
        viaPassenger = false; passengerShut = false;
        if (p.anim != null) p.anim.play("car_exit", exitTime);
        entriesCancelled++;
        return true;
    }

    /** Starts climbing out; at speed it is a jump (see {@link #bailOut}). Returns a reason when it cannot happen. */
    public String exit() {
        if (phase == Phase.ENTERING) { cancelEnter(); return null; }
        if (phase != Phase.DRIVING || driving == null) return null;
        if (driving.sim.speed() > BAIL_SPEED) return bailOut();
        float[] spot = exitSpot(driving);
        if (spot == null) return "No room to open a door here";
        using = driving;
        driving = null;
        phase = Phase.EXITING;
        phaseT = 0;
        seat(using, v3);
        fromX = v3[0]; fromY = v3[1] - SEAT_DROP; fromZ = v3[2];
        toX = spot[0]; toY = spot[1]; toZ = spot[2];
        using.sim.input.clear();
        using.sim.input.handbrake = true;      // you leave it parked
        exitTime = (hasDoor(using) ? EXIT_TIME : EXIT_TIME * 0.75f) * STAGED;
        if (raid.player.anim != null) raid.player.anim.play("car_exit", exitTime);
        if (hasDoor(using)) { raid.doors.swing(using.door, true); raid.emitSound(fromX, fromY + 1, fromZ, 9, "car_door", raid.player.id, false); }
        alight(using);
        return null;
    }

    private void seat(Car c, float[] out) { c.sim.point(c.visual.seatX, c.visual.seatY, c.visual.seatZ, out); }

    /** Eye position for the first-person driving view. */
    public void eye(float[] out) {
        Car c = driving != null ? driving : using;
        c.sim.point(c.visual.seatX, c.visual.seatY + 0.62f, c.visual.seatZ - 0.05f, out);
    }

    /** The standable spot at a front door on one side of the car (side = the sign of local x), or null when something is in the way there. */
    private float[] sideSpot(Car c, float side) {
        c.sim.point(side * (c.sim.data.width / 2 + 0.75f), 0.3f, c.visual.seatZ, v3);
        int gy = floorAt(v3[0], v3[1] + 1.2f, v3[2]);
        if (gy < 0 || !Physics.boxFree(raid.world, v3[0], gy, v3[2], 0.3f, 1.8f)) return null;
        return new float[]{v3[0], gy, v3[2]};
    }

    /** A standable spot beside the car: the driver's side first, then the passenger's, then behind. */
    private float[] exitSpot(Car c) {
        CarHandlingData d = c.sim.data;
        float[][] tries = {{-d.width / 2 - 0.75f, c.visual.seatZ}, {d.width / 2 + 0.75f, c.visual.seatZ}, {-d.width / 2 - 0.75f, 0}, {d.width / 2 + 0.75f, 0}, {0, -d.length / 2 - 0.9f}, {0, d.length / 2 + 0.9f}};
        for (float[] t : tries) {
            c.sim.point(t[0], 0.3f, t[1], v3);
            int gy = floorAt(v3[0], v3[1] + 1.2f, v3[2]);
            if (gy < 0 || !Physics.boxFree(raid.world, v3[0], gy, v3[2], 0.3f, 1.8f)) continue;
            return new float[]{v3[0], gy, v3[2]};
        }
        return null;
    }

    private int floorAt(float x, float y, float z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        for (int by = (int) Math.floor(y); by > Math.floor(y) - 4 && by > 0; by--) if (raid.world.solid(ix, by - 1, iz) && !raid.world.solid(ix, by, iz)) return by;
        return -1;
    }

    /** Controls from the keyboard this frame (only read while driving). */
    public void keys(boolean fwd, boolean back, boolean left, boolean right, boolean hand) { this.fwd = fwd; this.back = back; this.left = left; this.right = right; this.hand = hand; }

    public void horn() { if (driving != null && driving.hornT <= 0) { driving.hornT = 1.0f; driving.sim.origin(v3); raid.emitSound(v3[0], v3[1] + 1, v3[2], 70, "car_horn", raid.player.id, false); } }
    public void toggleLights() { if (driving != null) driving.sim.lightsOn = !driving.sim.lightsOn; }
    public void toggleSiren() { if (driving != null && driving.sim.data.family.startsWith("Emergency")) driving.sim.siren = !driving.sim.siren; }

    // ------------------------------------------------------------------ update
    void update(float dt) {
        Actor p = raid.player;
        boolean wet = raid.rain() > 0.3f;
        // ---- entry / exit animation: the body is carried between the ground and the seat, the door swings
        if (phase == Phase.ENTERING) {
            phaseT += dt;
            float k = Math.min(1, phaseT / enterTime);
            seat(using, v3);
            boolean door = hasDoor(using) && !viaPassenger;
            // to the door (it is opening), then in, then the door
            float a = hasDoorSpot ? Math.min(1, k / TO_DOOR) : 1, b = Math.max(0, Math.min(1, (k - (hasDoorSpot ? TO_DOOR : 0)) / (SHUT_AT - (hasDoorSpot ? TO_DOOR : 0))));
            a = a * a * (3 - 2 * a); b = b * b * (3 - 2 * b);
            float sx = hasDoorSpot ? fromX + (doorX - fromX) * a : fromX, sy = hasDoorSpot ? fromY + (doorY - fromY) * a : fromY, sz = hasDoorSpot ? fromZ + (doorZ - fromZ) * a : fromZ;
            // a foot up onto the sill, then down into the seat
            float lift = (float) Math.sin(Math.min(1, b * 1.4f) * Math.PI) * 0.25f;
            if (viaPassenger) {
                // in through the passenger's door and down into that seat; then, sitting, across to the wheel
                float dxs = v3[0], dys = v3[1], dzs = v3[2];
                using.sim.point(-using.visual.seatX, using.visual.seatY, using.visual.seatZ, v3);
                float in = Math.min(1, b / NEAR_SEAT), over = Math.max(0, (b - NEAR_SEAT) / (1 - NEAR_SEAT));
                in = in * in * (3 - 2 * in); over = over * over * (3 - 2 * over);
                float nx = sx + (v3[0] - sx) * in, ny = sy + (v3[1] - SEAT_DROP - sy) * in + (float) Math.sin(Math.min(1, in * 1.4f) * Math.PI) * 0.25f, nz = sz + (v3[2] - sz) * in;
                p.x = nx + (dxs - v3[0]) * over; p.y = ny + (dys - v3[1]) * over + (float) Math.sin(over * Math.PI) * 0.06f; p.z = nz + (dzs - v3[2]) * over;
                v3[0] = dxs; v3[1] = dys; v3[2] = dzs;
            } else {
            p.x = sx + (v3[0] - sx) * b; p.z = sz + (v3[2] - sz) * b; p.y = sy + (v3[1] - SEAT_DROP - sy) * b + lift;
            }
            p.vx = p.vy = p.vz = 0;
            // the door is pulled shut once you are in the seat
            if (k > SHUT_AT && door && using.door.target > 0) { raid.doors.swing(using.door, false); raid.emitSound(p.x, p.y + 1, p.z, 11, "car_door", p.id, false); doorCycles++; }
            if (viaPassenger && k > SHUT_AT && !passengerShut) { passengerShut = true; if (hasDoor2(using)) raid.doors.swing(using.door2, false); raid.emitSound(p.x, p.y + 1, p.z, 11, "car_door", p.id, false); doorCycles++; }
            // how far the passenger's door got open on the way in (gates)
            if (viaPassenger && using.door2 != null) passengerDoorPeak = Math.max(passengerDoorPeak, Math.abs(using.door2.angle) / Math.max(0.01f, using.door2.maxOpen));
            if (k >= 1 || p.dead) {
                passengerShut = false; viaPassenger = false;
                if (hasDoor2(using)) raid.doors.swing(using.door2, false);
                if (hasDoor(using)) raid.doors.swing(using.door, false);
                if (p.dead) { phase = Phase.NONE; using = null; }
                else {
                    driving = using; phase = Phase.DRIVING;
                    board(driving);
                    driving.sim.engineOn = driving.sim.damage.engine > 0;
                    driving.sim.lightsOn = raid.night();
                    lastCarYaw = driving.sim.body.yaw();
                    p.yaw = playerYaw(driving.sim); p.pitch = -0.05f;
                    raid.emitSound(p.x, p.y + 1, p.z, 30, "engine_start", p.id, false);
                    if (!driving.sim.engineOn) raid.notice("The engine is dead. This one is not going anywhere.", 0xFFD8483A);
                }
            }
        } else if (phase == Phase.EXITING) {
            phaseT += dt;
            float k = Math.min(1, phaseT / exitTime);
            // the door first, from the seat; then out through it; then it is pushed shut behind
            float b = Math.max(0, Math.min(1, (k - OPEN_FIRST) / (SHUT_AT - OPEN_FIRST))), e = b * b * (3 - 2 * b);
            p.x = fromX + (toX - fromX) * e; p.z = fromZ + (toZ - fromZ) * e; p.y = fromY + (toY - fromY) * e + (float) Math.sin(b * Math.PI) * 0.2f;
            if (k > SHUT_AT && hasDoor(using) && using.door.target > 0) { raid.doors.swing(using.door, false); raid.emitSound(p.x, p.y + 1, p.z, 11, "car_door", p.id, false); doorCycles++; }
            if (k >= 1) { if (hasDoor(using)) raid.doors.swing(using.door, false); if (hasDoor2(using)) raid.doors.swing(using.door2, false); using.sim.engineOn = false; phase = Phase.NONE; using = null; p.crouch = false; p.onGround = true; p.fallSpeed = 0; }
        }
        if (phase != Phase.DRIVING && p.seated) { p.seated = false; p.atWheel = false; p.steer = 0; }
        if (phase == Phase.DRIVING && (p.dead || driving == null)) { if (driving != null) { driving.sim.input.clear(); driving.sim.engineOn = false; alight(driving); } driving = null; phase = Phase.NONE; }

        for (Car c : cars) {
            CarVehicle s = c.sim;
            s.wetWeather = wet;
            if (c == driving) s.input.fromKeys(fwd, back, left, right, hand, s.forwardSpeed());
            else if (c != using) { s.input.throttle = 0; s.input.steer = 0; s.input.brake = 0; s.input.handbrake = true; }
            // distance-based simulation: a parked car far from anyone is not stepped at all
            s.origin(v3);
            float d2 = (v3[0] - p.x) * (v3[0] - p.x) + (v3[2] - p.z) * (v3[2] - p.z);
            if (c == driving || !s.asleep && d2 < 200 * 200) s.update(dt, raid.world);
            if (v3[1] < -20) { s.place(v3[0], World.GROUND, v3[2], s.body.yaw()); }
            carry(c);
            ride(c);
            people(c, dt);
            c.hornT -= dt;
            // an engine is loud in a dead city: everything in earshot knows where you are
            if (s.engineOn && c == driving) {
                c.noiseT -= dt;
                if (c.noiseT <= 0) { c.noiseT = 0.8f; raid.emitSound(v3[0], v3[1] + 1, v3[2], 26 + 34 * s.input.throttle + Math.min(20, s.speed()), "engine", p.id, false); }
            }
        }
        // ---- the driver rides in the seat and turns with the car
        if (phase == Phase.DRIVING && driving != null) {
            seat(driving, v3);
            p.x = v3[0]; p.y = v3[1] - SEAT_DROP; p.z = v3[2];
            p.vx = driving.sim.body.vx; p.vy = 0; p.vz = driving.sim.body.vz;
            p.crouch = true; p.onGround = true; p.fallSpeed = 0; p.sprinting = false;
            // 0.8: the driver is sitting in the car - facing the way the car faces, hands on the wheel, wherever the camera looks
            p.seated = true; p.atWheel = true; p.steer = -driving.sim.input.steer;      // the simulation's positive steer is a turn to the left; the wheel's is to the right
            p.faceSet = true; p.faceYaw = playerYaw(driving.sim);
            float yaw = driving.sim.body.yaw();
            float dy = yaw - lastCarYaw;
            while (dy > Math.PI) dy -= 2 * Math.PI;
            while (dy < -Math.PI) dy += 2 * Math.PI;
            // the view turns with the car: the same change of heading, in the player's own yaw convention
            p.yaw += dy;
            lastCarYaw = yaw;
        }
    }

    public static float playerYawOf(CarVehicle sim) { return playerYaw(sim); }

    /** Car heading as a player yaw (the direction the bonnet points, in the game's facing convention). */
    public static float playerYaw(CarVehicle s) { return (float) Math.atan2(-s.body.fwdX(), -s.body.fwdZ()); }

    /**
     * 1.2: animals and the car. They used not to be there for it at all - a car went through a dog as through air. Now the
     * bodywork meets them as it meets people: what it hits at speed is hurt by how fast it was met, thrown if that kills it
     * (as a body of Sydney's, with the car's speed), and shouldered aside if it does not; a body in the road is pushed along.
     */
    private void animals(Car c, float dt) {
        if (raid.animals.all.isEmpty()) return;
        CarVehicle s = c.sim;
        CarHandlingData d = s.data;
        s.origin(v3);
        float ox = v3[0], oz = v3[2], reach = d.length * 0.5f + 2, sp = s.speed();
        for (int i = 0; i < raid.animals.all.size(); i++) {
            Animals.Animal a = raid.animals.all.get(i);
            if (Math.abs(a.x - ox) > reach + 1 || Math.abs(a.z - oz) > reach + 1) continue;
            s.body.toLocal(a.x, a.y + a.kind.height * 0.5f, a.z, v3b);
            float lx = v3b[0] + d.comX, ly = v3b[1] + d.comY, lz = v3b[2] + d.comZ;
            float hx = d.width / 2 + a.kind.radius, hz = d.length / 2 + a.kind.radius;
            if (Math.abs(lx) > hx || Math.abs(lz) > hz || ly < -0.7f || ly > d.height + 0.4f) continue;
            s.body.velocityAt(a.x, a.y + 0.3f, a.z, v3b);
            float cvx = v3b[0], cvz = v3b[2];
            if (a.dead) { if (a.rag != null && sp > 1.2f) { a.rag.wake(); for (int k = 0; k < a.rag.n; k++) a.rag.impulse(k, (cvx - a.rag.vx(k)) * Math.min(1, dt * 9), 0, (cvz - a.rag.vz(k)) * Math.min(1, dt * 9)); } continue; }
            float px = hx - Math.abs(lx), pz = hz - Math.abs(lz), nlx = 0, nlz = 0, depth;
            if (px < pz) { nlx = Math.signum(lx == 0 ? 1 : lx); depth = px; } else { nlz = Math.signum(lz == 0 ? 1 : lz); depth = pz; }
            float nx = s.body.rightX() * nlx + s.body.fwdX() * nlz, nz = s.body.rightZ() * nlx + s.body.fwdZ() * nlz, nl = (float) Math.sqrt(nx * nx + nz * nz);
            if (nl < 1e-4f) continue;
            nx /= nl; nz /= nl;
            float closing = (cvx - a.body.vx) * nx + (cvz - a.body.vz) * nz;
            if (closing < 1.6f || sp < 1.2f) {
                // standing against it, or it creeping: the animal is moved out of the bodywork and that is all
                float tx = a.x + nx * Math.min(depth, 0.25f), tz = a.z + nz * Math.min(depth, 0.25f);
                if (Physics.boxFree(raid.world, tx, a.y + 0.05f, tz, a.kind.radius * 0.8f, a.kind.height * 0.8f)) { a.x = tx; a.z = tz; a.body.x = tx; a.body.z = tz; }
                continue;
            }
            float dmg = (closing - 1.2f) * 14f * Math.min(1.8f, d.mass / 1500f);
            boolean big = a.kind == Animals.Kind.SPIDER || a.kind == Animals.Kind.BROOD || a.kind == Animals.Kind.DOG || a.kind == Animals.Kind.IDOG;
            raid.animals.runDown(a, dmg, c == driving ? raid.player.id : 0, nx, nz, cvx, cvz, closing);
            if (big) { s.body.applyImpulse(a.x, a.y + 0.3f, a.z, -nx * closing * (a.kind.radius > 0.5f ? 60 : 22), 0, -nz * closing * (a.kind.radius > 0.5f ? 60 : 22)); raid.emitSound(a.x, a.y + 0.3f, a.z, 14, "car_bump", raid.player.id, false); }
            raid.gore.spill(a.x, a.y, a.z, 0.03f, a.kind.infected());
            if (a.dead) roadkills++;
        }
    }

    /** People and the car: nobody walks through bodywork, and a moving car hits what is in front of it. */
    private void people(Car c, float dt) {
        animals(c, dt);
        CarVehicle s = c.sim;
        CarHandlingData d = s.data;
        s.origin(v3);
        float ox = v3[0], oz = v3[2], reach = d.length * 0.5f + 2;
        float sp = s.speed();
        for (int ai = 0; ai < raid.actors.size(); ai++) {
            Actor a = raid.actors.get(ai);
            if (Math.abs(a.x - ox) > reach + 1 || Math.abs(a.z - oz) > reach + 1) continue;       // the dead too: a body in the road is in the road
            if (a.dead && a.ragdoll == null) continue;
            if (a.isPlayer && (phase != Phase.NONE)) continue;
            if (a.seated) continue;                 // riding in it
            // a body the physics has is where its pelvis is, not where it was standing when it was hit
            float bx = a.ragdoll != null ? a.ragdoll.x[absentialis.character.Ragdoll.PELVIS] : a.x, by = a.ragdoll != null ? a.ragdoll.y[absentialis.character.Ragdoll.PELVIS] - 0.3f : a.y, bz = a.ragdoll != null ? a.ragdoll.z[absentialis.character.Ragdoll.PELVIS] : a.z;
            s.body.toLocal(bx, by + 0.6f, bz, v3b);
            float lx = v3b[0] + d.comX, ly = v3b[1] + d.comY, lz = v3b[2] + d.comZ;
            float hx = d.width / 2 + a.radius, hz = d.length / 2 + a.radius;
            if (Math.abs(lx) > hx || Math.abs(lz) > hz || ly < -0.6f || ly > d.height + 0.4f) continue;
            if (a.ragdoll != null && (a.dead || !a.isPlayer)) {
                // 0.7: a body on the road is not painted on it. A car driven over or into one shoves it, rolls it, drags it a
                // little way - with the speed of the bodywork where it touched - and the car feels the bump
                if (sp > 1.2f) {
                    s.body.velocityAt(bx, by + 0.3f, bz, v3b);
                    // against the front (or the back, reversing) it is pushed along at the car's speed; underneath, it is rolled and dragged
                    float ahead = (v3b[0] * (bx - ox) + v3b[2] * (bz - oz));
                    boolean bumper = Math.abs(lz) > hz * 0.55f && ahead > 0;
                    a.ragdoll.carry(v3b[0], bumper ? 0.5f + sp * 0.05f : 0, v3b[2], Math.min(1, dt * (bumper ? 14f : 4.5f)));
                    if (raid.time >= a.runOverAt) {
                        a.runOverAt = raid.time + 0.25f;
                        s.body.applyImpulse(a.x, a.y + 0.2f, a.z, -v3b[0] * 22, bumper ? 0 : sp * 30, -v3b[2] * 22);
                        runOvers++;
                        if (sp > 3) { raid.emitSound(a.x, a.y + 0.3f, a.z, 14, "car_bump", raid.player.id, false); raid.gore.spill(a.x, a.y, a.z, 0.04f, a.infected()); }
                        if (!a.dead && sp > 4 && !bumper) { raid.hitKind = "vehicle"; raid.damageActor(a, c == driving ? raid.player : null, (sp - 3) * 6f, null, "torso", a.x, a.y + 0.3f, a.z); raid.hitKind = ""; if (a.dead) roadkills++; }
                    }
                }
                continue;
            }
            if (a.dead) continue;
            // push out through the nearest side
            float px = hx - Math.abs(lx), pz = hz - Math.abs(lz);
            float nlx = 0, nlz = 0, depth;
            if (px < pz) { nlx = Math.signum(lx == 0 ? 1 : lx); depth = px; } else { nlz = Math.signum(lz == 0 ? 1 : lz); depth = pz; }
            float nx = s.body.rightX() * nlx + s.body.fwdX() * nlz, nz = s.body.rightZ() * nlx + s.body.fwdZ() * nlz;
            float nl = (float) Math.sqrt(nx * nx + nz * nz);
            if (nl < 1e-4f) continue;
            nx /= nl; nz /= nl;
            float tx = a.x + nx * depth, tz = a.z + nz * depth;
            if (Physics.boxFree(raid.world, tx, a.y, tz, a.radius, a.height())) { a.x = tx; a.z = tz; }
            // closing speed between the bodywork at that point and the person
            s.body.velocityAt(a.x, a.y + 0.6f, a.z, v3b);
            float closing = (v3b[0] - a.vx) * nx + (v3b[2] - a.vz) * nz;
            if (closing < 2.2f || sp < 1.5f) continue;
            float dmg = (closing - 1.8f) * 11f * Math.min(1.8f, d.mass / 1500f);
            Actor driver = c == driving ? raid.player : null;
            boolean wasAlive = !a.dead;
            a.vx += nx * closing * 0.9f; a.vz += nz * closing * 0.9f; a.vy = Math.max(a.vy, 2.5f + closing * 0.25f);
            a.staggerUntil = raid.time + 1.6f;
            raid.hitKind = "vehicle";
            raid.damageActor(a, driver, dmg, null, closing > 9 ? "torso" : "legs", a.x - nx * a.radius, a.y + 0.8f, a.z - nz * a.radius);
            raid.hitKind = "";
            if (wasAlive && a.dead) roadkills++;
            // 0.7: nobody is hit by a car and left standing. Above a walking pace of closing speed the body is the physics
            // engine's: it leaves the bumper with the speed of the bodywork that hit it. A slow car takes the legs from under it
            // and it folds onto the bonnet; a fast one throws all of it. (The giant is not thrown by a car. It stops one.)
            // (1.1: the blow itself may already have given the body to Sydney - every body is simulated now - in which case
            // it is that body that leaves the bumper)
            if (!a.dead && !a.isPlayer && closing > KNOCK_SPEED && !"tank".equals(a.archetype)) {
                float lift = 1.6f + closing * 0.22f;
                if (a.ragdoll == null) {
                    a.vx = v3b[0] + nx * closing * 0.3f; a.vz = v3b[2] + nz * closing * 0.3f; a.vy = lift;
                    raid.gore.ragdolls.knockDown(a, nx, nz, Math.min(140, closing * 12f), 2.0f + Math.min(2.5f, closing * 0.18f));
                } else a.ragdoll.carry(v3b[0] + nx * closing * 0.3f, lift, v3b[2] + nz * closing * 0.3f, 0.85f);
                if (a.ragdoll != null) {
                    if (closing < SCOOP_SPEED) a.ragdoll.impulse("legs", nx * closing * 0.55f, 0.4f, nz * closing * 0.55f);    // legs swept: the trunk comes down toward the car
                    else a.ragdoll.impulse("torso", nx * closing * 0.2f, closing * 0.12f, nz * closing * 0.2f);
                    knockdowns++;
                }
            }
            // a body is not nothing: the car feels it
            float m = "tank".equals(a.archetype) ? 600 : 85;
            s.body.applyImpulse(a.x, a.y + 0.7f, a.z, -nx * closing * m * 0.6f, 0, -nz * closing * m * 0.6f);
            raid.emitSound(a.x, a.y + 1, a.z, 18, "car_bump", raid.player.id, false);
            s.damage.impact(Math.min(6, closing * m / d.mass * 4f), lx, ly, lz, d, 0.2f);
        }
    }

    // ------------------------------------------------------------------ CarVehicle.Listener
    public void impact(CarVehicle car, float dv, float x, float y, float z, int zone) {
        impacts++;
        raid.emitSound(x, y, z, dv > 6 ? 60 : 30, dv > 5 ? "car_crash" : "car_bump", raid.player.id, false);
        for (int i = 0; i < Math.min(18, 3 + (int) (dv * 2)); i++) {
            Raid.Effect e = new Raid.Effect();
            e.x = x; e.y = y; e.z = z;
            e.vx = (raid.rng.nextFloat() - 0.5f) * 6; e.vy = raid.rng.nextFloat() * 4; e.vz = (raid.rng.nextFloat() - 0.5f) * 6;
            e.life = e.max = 0.3f + raid.rng.nextFloat() * 0.4f; e.size = 0.04f + raid.rng.nextFloat() * 0.05f;
            e.color = i % 3 == 0 ? 0xFFFFC870 : car.paint; e.glow = i % 3 == 0;
            raid.effects.add(e);
        }
        if (driving != null && driving.sim == car) {
            raid.damageShake = Math.min(1, raid.damageShake + dv * 0.09f);
            // the driver is thrown against the belt: a hard enough stop hurts
            if (dv > 7.5f && !raid.god) { raid.hitKind = "vehicle"; raid.damageActor(raid.player, null, (dv - 6.5f) * 5.5f, null, "torso", raid.player.x, raid.player.y + 1, raid.player.z); raid.hitKind = ""; }
            if (zone >= 0 && dv > 4) raid.notice("Impact: " + CarDamageState.ZONE[zone] + (car.damage.engine <= 0 ? " - the engine is dead" : car.damage.engine < 0.5f ? " - the engine is failing" : ""), 0xFFE2A034);
        }
    }

    public void blockHit(CarVehicle car, int x, int y, int z, float speed) {
        // fences, glass, crates and thin doors give way to a car; masonry does not
        Block b = raid.world.block(x, y, z);
        if (b == null || b.hp <= 0) return;
        float energy = speed * speed * car.data.mass * 0.0011f;
        if (b.hp < 400 || b.is(Block.FRAGILE)) raid.damageBlock(x, y, z, energy, raid.player.id);
    }

    public void tyreBurst(CarVehicle car, int wheel) {
        car.point(car.data.wheels[wheel][0], car.data.wheelRadius, car.data.wheels[wheel][2], v3);
        raid.emitSound(v3[0], v3[1], v3[2], 45, "tyre_burst", raid.player.id, false);
        if (driving != null && driving.sim == car) raid.notice(CarWheel.NAME[wheel].replace('_', ' ').toLowerCase() + " tyre burst", 0xFFD8483A);
    }

    public void gearChanged(CarVehicle car) { if (driving != null && driving.sim == car && raid.listener != null) raid.listener.caption("gear_shift", raid.player.x, raid.player.z, 0.2f); }

    public void landed(CarVehicle car, float speed) {
        car.origin(v3);
        raid.emitSound(v3[0], v3[1], v3[2], 24, "suspension", raid.player.id, false);
        if (driving != null && driving.sim == car) raid.damageShake = Math.min(1, raid.damageShake + speed * 0.05f);
    }

    // ------------------------------------------------------------------ bullets
    /**
     * Ray against every car: bodywork (the collision boxes) and tyres. Returns the distance to the nearest hit
     * within len, or -1; hit[0] receives the car index and hit[1] the wheel struck (-1 for bodywork).
     */
    public float rayHit(float ox, float oy, float oz, float dx, float dy, float dz, float len, int[] hit) {
        float best = -1;
        for (int ci = 0; ci < cars.size(); ci++) {
            CarVehicle s = cars.get(ci).sim;
            CarHandlingData d = s.data;
            s.origin(v3);
            float ddx = v3[0] - ox, ddz = v3[2] - oz;
            if (ddx * ddx + ddz * ddz > (len + d.length) * (len + d.length)) continue;
            // into the car frame (relative to the centre of mass, then shifted to the car origin)
            s.body.toLocal(ox, oy, oz, v3);
            float lox = v3[0] + d.comX, loy = v3[1] + d.comY, loz = v3[2] + d.comZ;
            float ldx = s.body.rightX() * dx + s.body.rightY() * dy + s.body.rightZ() * dz, ldy = s.body.upX() * dx + s.body.upY() * dy + s.body.upZ() * dz, ldz = s.body.fwdX() * dx + s.body.fwdY() * dy + s.body.fwdZ() * dz;
            for (float[] b : d.collisionBoxes) {
                float t = slab(lox - b[0], loy - b[1], loz - b[2], ldx, ldy, ldz, b[3], b[4], b[5], len);
                if (t >= 0 && (best < 0 || t < best)) { best = t; hit[0] = ci; hit[1] = -1; }
            }
            for (int wi = 0; wi < 4; wi++) {
                CarWheel w = s.wheels[wi];
                float wy = w.ay + d.suspensionUpper - w.length;
                float cx = lox - w.ax, cy = loy - wy, cz = loz - w.az, r = d.wheelRadius * d.wheelScale;
                float bq = cx * ldx + cy * ldy + cz * ldz, cq = cx * cx + cy * cy + cz * cz - r * r, disc = bq * bq - cq;
                if (disc < 0) continue;
                float t = -bq - (float) Math.sqrt(disc);
                if (t >= 0 && t <= len && (best < 0 || t < best)) { best = t; hit[0] = ci; hit[1] = wi; }
            }
        }
        return best;
    }

    private static float slab(float ox, float oy, float oz, float dx, float dy, float dz, float hx, float hy, float hz, float len) {
        float t0 = 0, t1 = len;
        float[] o = {ox, oy, oz}, d = {dx, dy, dz}, h = {hx, hy, hz};
        for (int a = 0; a < 3; a++) {
            if (Math.abs(d[a]) < 1e-7f) { if (Math.abs(o[a]) > h[a]) return -1; continue; }
            float ta = (-h[a] - o[a]) / d[a], tb = (h[a] - o[a]) / d[a];
            if (ta > tb) { float t = ta; ta = tb; tb = t; }
            t0 = Math.max(t0, ta); t1 = Math.min(t1, tb);
            if (t0 > t1) return -1;
        }
        return t0;
    }

    /** A round has struck a car. Returns the share of its energy that carries on through (0 = stopped). */
    public float bullet(int carIndex, int wheel, float x, float y, float z, float damage, float power) {
        Car c = cars.get(carIndex);
        if (wheel >= 0) {
            if (c.sim.damage.burst(wheel)) tyreBurst(c.sim, wheel);
            return 0.2f;
        }
        c.sim.body.toLocal(x, y, z, v3);
        // a round that lands on the driver's door lands on a door: its hinges and its latch take it as any door's would
        if (hasDoor(c)) {
            CarVisualController vs = c.visual;
            float lx = v3[0] + c.sim.data.comX, ly = v3[1] + c.sim.data.comY, lz = v3[2] + c.sim.data.comZ;
            float u = (vs.hingeZ() - lz) / Math.max(0.1f, vs.doorLength()), vv = (ly - vs.hingeY()) / Math.max(0.1f, vs.doorHeight());
            if (lx < -c.sim.data.width * 0.5f + 0.35f && u >= 0 && u <= 1 && vv >= 0 && vv <= 1) raid.doors.hitMobile(c.door, u, vv, damage, c.sim.body.rightX(), c.sim.body.rightZ(), raid.player.id, "bullet");
        }
        c.sim.damage.shot(v3[0] + c.sim.data.comX, v3[1] + c.sim.data.comY, v3[2] + c.sim.data.comZ, c.sim.data, damage);
        // thin sheet metal: rifle rounds go through a door, pistol rounds mostly do not
        return power > 2.2f ? 0.55f : power > 1.2f ? 0.25f : 0f;
    }

    // ------------------------------------------------------------------ presentation
    void draw(ModelRenderer mr, float camX, float camZ, java.util.function.Function<float[], Float> lightAt) {
        if (!gone.isEmpty()) { for (Car c : gone) c.visual.dispose(mr); gone.clear(); }
        for (Car c : cars) {
            c.sim.origin(v3);
            if (Math.abs(v3[0] - camX) > 220 || Math.abs(v3[2] - camZ) > 220) continue;
            c.visual.hideGlass = cockpit && (c == driving || c == using);     // from the driver's seat you look through the glass, not at it
            c.visual.draw(mr, lightAt.apply(v3));
        }
    }

    void lights(HybridRenderer hr) {
        for (Car c : cars) {
            CarVehicle s = c.sim;
            CarHandlingData d = s.data;
            if (s.lightsOn && (s.damage.lightL || s.damage.lightR)) {
                s.point(0, d.height * 0.42f, d.length / 2 + 0.25f, v3);
                float k = (s.damage.lightL ? 0.5f : 0) + (s.damage.lightR ? 0.5f : 0);
                hr.addLight(v3[0], v3[1], v3[2], 46, 13f * k, 12.2f * k, 10.4f * k, HybridRenderer.LIGHT_SHADOW | HybridRenderer.LIGHT_SPOT, s.body.fwdX(), s.body.fwdY() - 0.07f, s.body.fwdZ(), 0.80f);
                s.point(0, d.height * 0.45f, -d.length / 2 - 0.2f, v3);
                hr.addPoint(v3[0], v3[1], v3[2], 5, s.input.brake > 0 ? 3.2f : 1.0f, 0.12f, 0.06f, 0);
            }
            if (s.siren) {
                s.point(0, d.height + 0.3f, 0, v3);
                boolean red = ((int) (raid.time * 5) & 1) == 0;
                hr.addPoint(v3[0], v3[1], v3[2], 22, red ? 9 : 0.4f, 0.4f, red ? 0.4f : 10, 0);
            }
        }
    }

    /** Engine, tyres and wind for the car being driven; a parked car is silent. */
    void audio(UltraEngine au, float dt) {
        if (au == null || !au.available) return;
        for (Car c : cars) {
            CarVehicle s = c.sim;
            boolean run = s.engineOn && c == driving;
            if (run && (c.engine == null || c.engine.done())) {
                au.play("engine_start", 0.5f, 0, 1, UltraEngine.SFX);
                c.engine = au.loop("diesel".equals(s.data.engineType) ? "engine_diesel" : "engine_loop", 0f, UltraEngine.SFX);
                c.skid = au.loop("tyre_skid", 0f, UltraEngine.SFX);
                c.roll = au.loop("tyre_roll", 0f, UltraEngine.SFX);
            }
            if (!run) { if (c.engine != null) { au.play("engine_stop", 0.35f, 0, 1, UltraEngine.SFX); stop(c); } continue;
            }
            // the note climbs through each gear and drops on the change, like the box it stands for
            // (0.9: the engine is a recording of one idling, so the note starts at the recording's own pitch and rises from there)
            float target = 0.92f + s.transmission.band * 0.85f + s.input.throttle * 0.14f;
            c.engineNote += (target - c.engineNote) * Math.min(1, dt * 5);
            c.engine.setPitch(c.engineNote);
            c.engine.setGain(0.32f + 0.3f * s.input.throttle + Math.min(0.2f, s.speed() * 0.008f));
            int sliding = 0;
            boolean loose = false;
            for (CarWheel w : s.wheels) { if (w.sliding() && Math.abs(w.forwardVel) + Math.abs(w.sideVel) > 2.5f) sliding++; if (w.grounded && w.surface.adhesion < 0.8f) loose = true; }
            c.skidGain += ((loose ? 0.1f : 0.4f) * Math.min(1f, sliding / 2f) - c.skidGain) * Math.min(1, dt * 9);
            c.skid.setGain(c.skidGain);
            c.skid.setPitch(0.8f + Math.min(0.5f, s.speed() * 0.02f));
            c.roll.setGain(s.groundedWheels > 0 ? Math.min(0.5f, s.speed() * (loose ? 0.035f : 0.016f)) : 0);
            c.roll.setPitch(0.6f + Math.min(1.2f, s.speed() * 0.04f));
            if (c.hornT > 0 && !c.hornHeard) au.play("car_horn", 0.6f, 0, 1, UltraEngine.SFX);
            c.hornHeard = c.hornT > 0;
        }
    }

    private static void stop(Car c) {
        if (c.engine != null) c.engine.fadeTo(0, 0.25f);
        if (c.skid != null) c.skid.fadeTo(0, 0.2f);
        if (c.roll != null) c.roll.fadeTo(0, 0.2f);
        c.engine = c.skid = c.roll = null;
    }

    public void stopAudio() { for (Car c : cars) stop(c); }

    /** The driver's instruments: speed, gear, tyres, engine, and what the keys do. */
    void hud(Ui ui) {
        if (phase != Phase.DRIVING || driving == null) return;
        CarVehicle s = driving.sim;
        float w = ui.s(250), h = ui.s(92), x = ui.w / 2f - w / 2, y = ui.h - h - ui.s(26);
        ui.b.rect(x, y, w, h, 0xB00C0D0C);
        ui.b.rect(x, y, w, ui.s(2), Ui.AMBER);
        ui.text(String.format("%3.0f", s.kmh()), x + ui.s(14), y + ui.s(10), 34, Ui.TXT_HI);
        ui.text("km/h", x + ui.s(88), y + ui.s(30), 11, Ui.TXT_DIM);
        ui.text("GEAR", x + ui.s(132), y + ui.s(12), 10, Ui.TXT_DIM);
        ui.text(s.transmission.label(), x + ui.s(132), y + ui.s(24), 22, s.transmission.gear == 0 ? Ui.AMBER : Ui.TXT_HI);
        // the four tyres as the car sees them: lit while gripping, amber while sliding, red when flat
        float tx = x + ui.s(178), ty = y + ui.s(12);
        for (int i = 0; i < 4; i++) {
            CarWheel wl = s.wheels[i];
            int col = wl.tyre == CarWheel.Tyre.BURST ? Ui.RED : !wl.grounded ? 0xFF3A3C3B : wl.state == CarWheel.State.NORMAL ? Ui.GREEN : Ui.AMBER;
            ui.b.rect(tx + (i & 1) * ui.s(26), ty + (i >> 1) * ui.s(22), ui.s(12), ui.s(16), col);
        }
        ui.b.rect(tx + ui.s(13), ty + ui.s(2), ui.s(12), ui.s(34), 0xFF2A2C2B);
        float eng = s.damage.engine;
        ui.text("ENGINE", x + ui.s(14), y + ui.s(58), 10, Ui.TXT_DIM);
        ui.bar(x + ui.s(62), y + ui.s(60), ui.s(92), ui.s(7), eng, eng < 0.3f ? Ui.RED : eng < 0.6f ? Ui.AMBER : Ui.GREEN);
        if (s.input.handbrake) ui.text("HANDBRAKE", x + ui.s(166), y + ui.s(58), 10, Ui.RED);
        ui.textC("[W/S] drive  [A/D] steer  [Space] handbrake  [F] exit  [H] horn  [L] lights  [V] camera", ui.w / 2f, y + h + ui.s(4), 10, Ui.TXT_DIM);
    }

    /** Development overlay: contact points, suspension lines, ground normals, tyre force vectors, centre of mass. */
    void debugDraw(ModelRenderer mr, Mat4 tmp) {
        for (Car c : cars) {
            CarVehicle s = c.sim;
            CarHandlingData d = s.data;
            tmp.identity().translate(s.body.px, s.body.py, s.body.pz).scale(0.14f, 0.14f, 0.14f).translate(0, -0.5f, 0);
            mr.cube(tmp, 0xFFFF40FF, 1, 1);
            for (CarWheel w : s.wheels) {
                s.point(w.ax, w.ay + d.suspensionUpper, w.az, v3);
                float tx = v3[0], ty = v3[1], tz = v3[2];
                float len = w.length + d.wheelRadius;
                mr.beam(tx, ty, tz, tx - s.body.upX() * len, ty - s.body.upY() * len, tz - s.body.upZ() * len, 0.02f, w.grounded ? 0xFF40FF60 : 0xFFFF4040, 1);
                if (!w.grounded) continue;
                tmp.identity().translate(w.cx, w.cy, w.cz).scale(0.08f, 0.08f, 0.08f).translate(0, -0.5f, 0);
                mr.cube(tmp, 0xFFFFFF40, 1, 1);
                mr.beam(w.cx, w.cy, w.cz, w.cx + w.nx * 0.5f, w.cy + w.ny * 0.5f, w.cz + w.nz * 0.5f, 0.015f, 0xFF40C0FF, 1);
                float k = 1f / (d.mass * CarVehicle.FIXED_DT) * 0.12f;      // impulse -> acceleration -> a readable length
                float fxw = (w.fx * w.forwardImpulse + w.sx * w.sideImpulse) * k, fyw = (w.fy * w.forwardImpulse + w.sy * w.sideImpulse) * k, fzw = (w.fz * w.forwardImpulse + w.sz * w.sideImpulse) * k;
                int col = w.state == CarWheel.State.NORMAL ? 0xFFFFFFFF : w.state == CarWheel.State.SPINNING ? 0xFFFFA020 : w.state == CarWheel.State.FIXED ? 0xFFFF3030 : 0xFFFFE040;
                mr.beam(w.cx, w.cy + 0.05f, w.cz, w.cx + fxw, w.cy + 0.05f + fyw, w.cz + fzw, 0.025f, col, 1);
            }
        }
    }

    void debugText(Ui ui) {
        Car c = driving != null ? driving : cars.isEmpty() ? null : cars.get(0);
        if (c == null) return;
        CarVehicle s = c.sim;
        float x = ui.s(8), y = ui.s(140);
        ui.text(String.format("%s  gear %s  %.1f m/s (%.0f km/h)  lateral %.1f  steer %.2f (%.1f deg)  drive %.2f m/s2  wheels down %d  upright %.2f", s.data.id, s.transmission.label(), s.forwardSpeed(), s.kmh(), s.lateralSpeed(), s.steering, Math.toDegrees(s.wheelAngle), s.driveAcceleration, s.groundedWheels, s.upright()), x, y, 11, Ui.AMBER);
        for (int i = 0; i < 4; i++) {
            CarWheel w = s.wheels[i];
            ui.text(String.format("%-11s %-8s %-8s comp %.2f  grip %5.0f  demand %5.0f  vf %5.1f  vs %5.1f  %s", CarWheel.NAME[i], w.grounded ? w.state : "AIR", w.grounded ? w.surface : "-", w.compression, w.adhesion, w.demand, w.forwardVel, w.sideVel, w.tyre), x, y + ui.s(14) * (i + 1), 11, w.state == CarWheel.State.NORMAL ? Ui.TXT : Ui.AMBER);
        }
    }
}
