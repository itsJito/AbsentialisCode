package absentialis.vehicle;

import absentialis.engine.util.Json;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handling configuration of one drivable vehicle. Every car in the game runs the same simulation
 * ({@link CarVehicle}); what makes a van feel like a van is only the numbers in here, loaded from
 * content/vehicles.json.
 *
 * Units are SI throughout: metres, kilograms, seconds, m/s, m/s^2. The car's own frame is +x right, +y up,
 * +z forward, with the origin at the centre of the wheelbase on the ground plane of a car at rest.
 *
 * <pre>
 * parameter              unit        valid range     default   meaning
 * ---------------------  ----------  --------------  --------  -------------------------------------------------
 * mass                   kg          400 .. 8000     1400      total mass of the rigid body
 * width / length / height m          1 .. 3 / 2 .. 8 / 1 .. 3.5   chassis box (collision and inertia)
 * comX / comY / comZ     m           -0.5 .. 0.5 / 0.1 .. 1.5 / -1 .. 1   centre of mass from the origin
 * turnMass               kg m^2      0 = derive      0         yaw inertia; 0 uses mass*(width^2+length^2)/12
 * engineAcceleration     m/s^2       0.5 .. 12       4.0       drive acceleration available in top gear at rest
 * maxVelocity            m/s         5 .. 60         33        speed at which the engine stops adding thrust
 * maxReverseVelocity     m/s         2 .. 20         8
 * gears                  count       1 .. 6          4         forward gears (gear 0 is reverse)
 * drive                  F | R | 4                   R         driven axle(s)
 * engineType             text                        petrol    metadata: picks the engine sound
 * firstGearBoost         x           1 .. 3          1.5       multiplies acceleration in first gear
 * secondGearBoost        x           1 .. 2          1.2       multiplies acceleration in second gear
 * tractionMultiplier     x           0.3 .. 2        1.0       overall tyre adhesion
 * tractionLoss           x           0.4 .. 1        0.82      share of grip that remains once a tyre is sliding
 * tractionBias           0..1        0.3 .. 0.7      0.5       front share of grip (front factor = 2*bias)
 * steeringLock           degrees     15 .. 50        34        front wheel angle at full lock
 * brakeDeceleration      m/s^2       2 .. 20         9         braking with every wheel inside its grip
 * brakeBias              0..1        0.3 .. 0.85     0.62      front share of braking
 * suspensionForce        x           0.4 .. 4        1.4       spring strength (compression at rest = 1/(4*force))
 * suspensionDamping      x           0.02 .. 1       0.28      resistance to suspension movement
 * suspensionUpper        m           0 .. 0.6        0.28      top of wheel travel above the attachment
 * suspensionLower        m           -0.6 .. 0       -0.16     bottom of wheel travel below the attachment
 * suspensionBias         0..1        0.3 .. 0.7      0.5       front share of spring force
 * antiDive               x           0 .. 1          0.3       extra front spring under braking
 * wheelRadius            m           0.2 .. 0.7      0.34
 * wheelScale             x           0.5 .. 2        1.0       visual size of the wheel model
 * wheels                 m           four [x, y, z]            attachment points: FL, FR, RL, RR
 * collisionBoxes         m           list of [cx, cy, cz, hx, hy, hz]   compound chassis shape (body, cabin, ...)
 * collisionDamage        x           0 .. 4          1.0       how hard impacts are on this chassis
 * rearWheelsFirst        flag                        false     process the rear axle before the front
 * handbrakeTyre          x           0.2 .. 1        0.55      rear lateral grip left while the handbrake holds
 * improvedSand           flag                        false     off-road tyres: sand drags far less
 * </pre>
 */
public final class CarHandlingData {
    public enum Drive { FRONT, REAR, FOUR }

    // ---- identity
    public String id = "", profile = "", model = "sedan", name = "", family = "";
    public int price = 20000, rank = 1;
    // ---- physical values
    public float mass = 1400, width = 1.8f, length = 4.4f, height = 1.45f, comX, comY = 0.5f, comZ, turnMass;
    // ---- engine
    public float engineAcceleration = 4.0f, maxVelocity = 33, maxReverseVelocity = 8, firstGearBoost = 1.5f, secondGearBoost = 1.2f;
    public int gears = 4;
    public Drive drive = Drive.REAR;
    public String engineType = "petrol";
    // ---- traction
    public float tractionMultiplier = 1, tractionLoss = 0.82f, tractionBias = 0.5f;
    // ---- steering, braking
    public float steeringLock = 34, brakeDeceleration = 9, brakeBias = 0.62f;
    // ---- suspension
    public float suspensionForce = 1.4f, suspensionDamping = 0.28f, suspensionUpper = 0.28f, suspensionLower = -0.16f, suspensionBias = 0.5f, antiDive = 0.3f;
    // ---- wheels: FL, FR, RL, RR
    public final float[][] wheels = {{-0.8f, 0.34f, 1.35f}, {0.8f, 0.34f, 1.35f}, {-0.8f, 0.34f, -1.35f}, {0.8f, 0.34f, -1.35f}};
    public float wheelRadius = 0.34f, wheelScale = 1;
    // ---- collisions
    public final List<float[]> collisionBoxes = new ArrayList<>();
    public float collisionDamage = 1;
    // ---- special flags
    public boolean rearWheelsFirst, improvedSand;
    public float handbrakeTyre = 0.55f;
    /** Body colours a spawned car may wear (ARGB). */
    public int[] paints = {0xFF6A7078};

    /** Yaw inertia: the configured turn mass, or the box approximation mass * (width^2 + length^2) / 12. */
    public float yawInertia() { return turnMass > 0 ? turnMass : mass * (width * width + length * length) / 12f; }
    public float suspensionTravel() { return suspensionUpper - suspensionLower; }
    public int drivenWheels() { return drive == Drive.FOUR ? 4 : 2; }
    public boolean driven(int wheel) { return drive == Drive.FOUR || (drive == Drive.FRONT) == (wheel < 2); }

    // ------------------------------------------------------------------ loading
    public static final Map<String, CarHandlingData> ALL = new LinkedHashMap<>();
    /** The seven base families, in the order the shop lists them. */
    public static final String[] FAMILIES = {"SUVs", "Off-road", "Emergency, Rescue & Government", "Vans", "2-Door", "4-Door", "Pickup Trucks"};

    public static synchronized void load() {
        if (!ALL.isEmpty()) return;
        try (InputStream in = CarHandlingData.class.getResourceAsStream("/content/vehicles.json")) {
            if (in == null) throw new IllegalStateException("content/vehicles.json is missing");
            Map<String, Object> root = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            for (Object o : Json.list(root, "vehicles")) {
                @SuppressWarnings("unchecked") CarHandlingData d = parse((Map<String, Object>) o);
                String err = d.validate();
                if (err != null) throw new IllegalStateException("vehicle " + d.id + ": " + err);
                ALL.put(d.id, d);
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("vehicles.json: " + e.getMessage());
        }
    }

    public static CarHandlingData get(String id) { load(); return ALL.get(id); }
    public static List<CarHandlingData> all() { load(); return new ArrayList<>(ALL.values()); }

    private static float f(Map<String, Object> m, String k, float def) { return (float) Json.num(m, k, def); }

    private static CarHandlingData parse(Map<String, Object> m) {
        CarHandlingData d = new CarHandlingData();
        d.id = Json.str(m, "id", "");
        d.profile = Json.str(m, "profile", d.id);
        d.model = Json.str(m, "model", "sedan");
        d.name = Json.str(m, "name", d.id);
        d.family = Json.str(m, "family", "4-Door");
        d.price = Json.integer(m, "price", 20000);
        d.rank = Json.integer(m, "rank", 1);
        d.mass = f(m, "mass", d.mass); d.width = f(m, "width", d.width); d.length = f(m, "length", d.length); d.height = f(m, "height", d.height);
        d.comX = f(m, "comX", 0); d.comY = f(m, "comY", d.comY); d.comZ = f(m, "comZ", 0); d.turnMass = f(m, "turnMass", 0);
        d.engineAcceleration = f(m, "engineAcceleration", d.engineAcceleration);
        d.maxVelocity = f(m, "maxVelocity", d.maxVelocity); d.maxReverseVelocity = f(m, "maxReverseVelocity", d.maxReverseVelocity);
        d.gears = Json.integer(m, "gears", d.gears);
        String dr = Json.str(m, "drive", "R");
        d.drive = dr.equals("F") ? Drive.FRONT : dr.equals("4") ? Drive.FOUR : Drive.REAR;
        d.engineType = Json.str(m, "engineType", "petrol");
        d.firstGearBoost = f(m, "firstGearBoost", d.firstGearBoost); d.secondGearBoost = f(m, "secondGearBoost", d.secondGearBoost);
        d.tractionMultiplier = f(m, "tractionMultiplier", 1); d.tractionLoss = f(m, "tractionLoss", d.tractionLoss); d.tractionBias = f(m, "tractionBias", 0.5f);
        d.steeringLock = f(m, "steeringLock", d.steeringLock);
        d.brakeDeceleration = f(m, "brakeDeceleration", d.brakeDeceleration); d.brakeBias = f(m, "brakeBias", d.brakeBias);
        d.suspensionForce = f(m, "suspensionForce", d.suspensionForce); d.suspensionDamping = f(m, "suspensionDamping", d.suspensionDamping);
        d.suspensionUpper = f(m, "suspensionUpper", d.suspensionUpper); d.suspensionLower = f(m, "suspensionLower", d.suspensionLower);
        d.suspensionBias = f(m, "suspensionBias", 0.5f); d.antiDive = f(m, "antiDive", d.antiDive);
        d.wheelRadius = f(m, "wheelRadius", d.wheelRadius); d.wheelScale = f(m, "wheelScale", 1);
        List<Object> ws = Json.list(m, "wheels");
        if (ws.size() == 4) for (int i = 0; i < 4; i++) { List<?> p = (List<?>) ws.get(i); for (int k = 0; k < 3; k++) d.wheels[i][k] = ((Number) p.get(k)).floatValue(); }
        else {
            // derived from track and wheelbase when not listed one by one
            // height: where the attachment must sit for the car's origin to rest on the ground at its static compression
            float rest = d.suspensionTravel() * (1f - 1f / (4f * d.suspensionForce)) + d.wheelRadius - d.suspensionUpper;
            float track = f(m, "track", d.width - 0.3f) / 2, base = f(m, "wheelbase", d.length * 0.6f) / 2, wy = f(m, "wheelY", rest);
            float[][] w = {{-track, wy, base}, {track, wy, base}, {-track, wy, -base}, {track, wy, -base}};
            for (int i = 0; i < 4; i++) System.arraycopy(w[i], 0, d.wheels[i], 0, 3);
        }
        for (Object o : Json.list(m, "collisionBoxes")) { List<?> b = (List<?>) o; float[] box = new float[6]; for (int k = 0; k < 6; k++) box[k] = ((Number) b.get(k)).floatValue(); d.collisionBoxes.add(box); }
        if (d.collisionBoxes.isEmpty()) {
            // body from the sills to the waistline, cabin above it and a little shorter
            float clear = d.wheelRadius * 0.75f, waist = clear + (d.height - clear) * 0.55f;
            d.collisionBoxes.add(new float[]{0, (clear + waist) / 2, 0, d.width / 2, (waist - clear) / 2, d.length / 2});
            d.collisionBoxes.add(new float[]{0, (waist + d.height) / 2, -d.length * 0.06f, d.width / 2 - 0.08f, (d.height - waist) / 2, d.length * 0.30f});
        }
        d.collisionDamage = f(m, "collisionDamage", 1);
        d.rearWheelsFirst = Json.bool(m, "rearWheelsFirst", false);
        d.improvedSand = Json.bool(m, "improvedSand", false);
        d.handbrakeTyre = f(m, "handbrakeTyre", d.handbrakeTyre);
        List<Object> ps = Json.list(m, "paints");
        if (!ps.isEmpty()) { d.paints = new int[ps.size()]; for (int i = 0; i < ps.size(); i++) d.paints[i] = 0xFF000000 | (int) Long.parseLong(String.valueOf(ps.get(i)).replace("#", ""), 16); }
        return d;
    }

    private static String range(String n, float v, float lo, float hi) { return v < lo || v > hi || v != v ? n + " = " + v + " is outside " + lo + " .. " + hi : null; }

    /** Checks every parameter against its documented range. Returns the first problem, or null. */
    public String validate() {
        String[] checks = {
                range("mass", mass, 400, 8000), range("width", width, 1, 3), range("length", length, 2, 8), range("height", height, 1, 3.5f),
                range("comX", comX, -0.5f, 0.5f), range("comY", comY, 0.1f, 1.5f), range("comZ", comZ, -1, 1),
                range("engineAcceleration", engineAcceleration, 0.5f, 12), range("maxVelocity", maxVelocity, 5, 60), range("maxReverseVelocity", maxReverseVelocity, 2, 20),
                range("gears", gears, 1, 6), range("firstGearBoost", firstGearBoost, 1, 3), range("secondGearBoost", secondGearBoost, 1, 2),
                range("tractionMultiplier", tractionMultiplier, 0.3f, 2), range("tractionLoss", tractionLoss, 0.4f, 1), range("tractionBias", tractionBias, 0.3f, 0.7f),
                range("steeringLock", steeringLock, 15, 50), range("brakeDeceleration", brakeDeceleration, 2, 20), range("brakeBias", brakeBias, 0.3f, 0.85f),
                range("suspensionForce", suspensionForce, 0.4f, 4), range("suspensionDamping", suspensionDamping, 0.02f, 1), range("suspensionUpper", suspensionUpper, 0, 0.6f),
                range("suspensionLower", suspensionLower, -0.6f, 0), range("suspensionBias", suspensionBias, 0.3f, 0.7f), range("antiDive", antiDive, 0, 1),
                range("wheelRadius", wheelRadius, 0.2f, 0.7f), range("wheelScale", wheelScale, 0.5f, 2), range("collisionDamage", collisionDamage, 0, 4),
                range("handbrakeTyre", handbrakeTyre, 0.2f, 1)};
        for (String c : checks) if (c != null) return c;
        if (suspensionTravel() < 0.08f) return "suspension travel under 8 cm";
        if (id.isEmpty()) return "missing id";
        return null;
    }
}
