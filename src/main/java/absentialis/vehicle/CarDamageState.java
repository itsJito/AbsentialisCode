package absentialis.vehicle;

/**
 * Physical condition of one car: its four tyres, how far each part of the body has been pushed in, and what the
 * engine has left. Impacts arrive from the rigid body with a magnitude (the speed change they caused) and the
 * place on the chassis they landed; this class turns them into deformation of the zone that was hit. The visual
 * model is rebuilt from these numbers (see {@link CarVisualController}); the physics reads only the tyres and the
 * engine.
 */
public final class CarDamageState {
    public static final int FRONT = 0, REAR = 1, LEFT = 2, RIGHT = 3, ROOF = 4, ZONES = 5;
    public static final String[] ZONE = {"front", "rear", "left side", "right side", "roof"};

    public final CarWheel.Tyre[] tyres = {CarWheel.Tyre.INTACT, CarWheel.Tyre.INTACT, CarWheel.Tyre.INTACT, CarWheel.Tyre.INTACT};
    /** Deformation per zone, 0 (factory) .. 1 (crushed). */
    public final float[] deform = new float[ZONES];
    /** Engine condition 1 .. 0: below 0.5 it loses power, at 0 it will not run. */
    public float engine = 1;
    /** Strongest impact so far and the last one, in m/s of speed change (exposed for the wider condition system). */
    public float worstImpact, lastImpact;
    public int impacts, lastZone = -1;
    public boolean windscreen = true, rearGlass = true;
    public boolean lightL = true, lightR = true;
    /** Bumped whenever something the model shows has changed: the visual controller rebuilds on a new value. */
    public int version;

    /**
     * 0.8: where the car has actually been hit. Each dent is a place on the chassis - {x, y, z} in the car's own frame,
     * metres, y up from the underside - with how deep it went (0 .. 1) and how far round it the metal gave (metres).
     * The model is crumpled at these places and nowhere else: a pole in the left front corner pushes in the left front
     * corner. (Through 0.7 a hit only raised the level of one of five zones, and the whole of that end was squeezed
     * the same amount wherever on it the blow had landed.) The zone levels are still kept: the engine, the glass and
     * the lamps answer to them.
     */
    public final java.util.List<float[]> dents = new java.util.ArrayList<>();
    /** The most dents a car keeps (after that the shallowest gives way to a new one), and how close a blow must land to deepen an old one instead. */
    public static int MAX_DENTS = 14;
    public static float DENT_MERGE = 0.45f, DENT_PER_DV = 0.085f;

    /** As it left the factory: no dents, sound tyres, whole glass and lamps, a healthy engine. */
    public void repair() {
        java.util.Arrays.fill(deform, 0f); dents.clear();
        for (int i = 0; i < tyres.length; i++) tyres[i] = CarWheel.Tyre.INTACT;
        engine = 1; worstImpact = lastImpact = 0; impacts = 0; lastZone = -1;
        windscreen = rearGlass = lightL = lightR = true;
        version++;
    }

    /** Records a blow at a point: deepens the dent that is already there, or makes a new one. */
    public void dent(float lx, float ly, float lz, float depth, float radius) {
        if (depth <= 0.01f) return;
        for (float[] d : dents) {
            float dx = d[0] - lx, dy = d[1] - ly, dz = d[2] - lz;
            if (dx * dx + dy * dy + dz * dz < DENT_MERGE * DENT_MERGE) {
                float w = depth / (d[3] + depth);
                d[0] += (lx - d[0]) * w * 0.5f; d[1] += (ly - d[1]) * w * 0.5f; d[2] += (lz - d[2]) * w * 0.5f;
                d[3] = Math.min(1, d[3] + depth * 0.8f); d[4] = Math.min(1.9f, Math.max(d[4], radius) + depth * 0.25f);
                version++;
                return;
            }
        }
        if (dents.size() >= MAX_DENTS) { int k = 0; for (int i = 1; i < dents.size(); i++) if (dents.get(i)[3] < dents.get(k)[3]) k = i; dents.remove(k); }
        dents.add(new float[]{lx, ly, lz, Math.min(1, depth), radius});
        version++;
    }

    /** A car that was already knocked about when it was found: a dent somewhere on the named side, as deep as the zone says. */
    public void dentZone(int zone, float amount, CarHandlingData d, java.util.Random rnd) {
        deform[zone] = Math.max(deform[zone], amount);
        float hw = d.width / 2, hl = d.length / 2, side = (rnd.nextFloat() - 0.5f) * 1.6f;
        float lx = zone == LEFT ? -hw : zone == RIGHT ? hw : side * hw, lz = zone == FRONT ? hl : zone == REAR ? -hl : side * hl;
        dent(lx, zone == ROOF ? d.height : d.height * (0.3f + rnd.nextFloat() * 0.3f), zone == ROOF ? side * hl * 0.4f : lz, amount, 0.6f + amount * 0.8f);
    }

    /** Deformation as one of four visible steps (0 none .. 3 wrecked). */
    public int level(int zone) { return Math.min(3, (int) (deform[zone] * 3.999f)); }
    public int burstCount() { int n = 0; for (CarWheel.Tyre t : tyres) if (t == CarWheel.Tyre.BURST) n++; return n; }
    public float enginePower() { return engine <= 0 ? 0 : engine < 0.5f ? 0.45f + engine * 1.1f : 1f; }

    /** Which zone a point on the chassis belongs to, from where it sits relative to the car's half extents. */
    public static int zoneOf(float lx, float ly, float lz, float halfW, float halfL, float height) {
        float fx = Math.abs(lx) / Math.max(0.1f, halfW), fz = Math.abs(lz) / Math.max(0.1f, halfL);
        if (ly > height * 0.82f && fx < 0.9f && fz < 0.75f) return ROOF;
        if (fz >= fx * 0.92f) return lz > 0 ? FRONT : REAR;
        return lx < 0 ? LEFT : RIGHT;
    }

    /**
     * An impact that changed the car's speed by dv (m/s) at a chassis point. Scrapes and kerb strikes below
     * 2 m/s leave no mark. Returns the zone that took it, or -1.
     */
    public int impact(float dv, float lx, float ly, float lz, CarHandlingData d, float roll) {
        lastImpact = dv;
        if (dv < 2f) return -1;
        impacts++;
        worstImpact = Math.max(worstImpact, dv);
        int zone = zoneOf(lx, ly, lz, d.width / 2, d.length / 2, d.height);
        int before = level(zone);
        float add = (dv - 2f) * 0.055f * d.collisionDamage;
        deform[zone] = Math.min(1, deform[zone] + add);
        dent(lx, ly, lz, (dv - 2f) * DENT_PER_DV * d.collisionDamage, 0.5f + Math.min(0.9f, dv * 0.07f));
        // a frontal hit reaches the engine; any heavy hit shakes it
        if (zone == FRONT) engine = Math.max(0, engine - add * 0.7f);
        else if (dv > 9) engine = Math.max(0, engine - add * 0.15f);
        boolean changed = level(zone) != before;
        if (zone == FRONT && deform[FRONT] > 0.3f && windscreen && roll > 0.5f) { windscreen = false; changed = true; }
        if (zone == FRONT && deform[FRONT] > 0.45f) { if (lx < 0 && lightL) { lightL = false; changed = true; } if (lx >= 0 && lightR) { lightR = false; changed = true; } }
        if (zone == REAR && deform[REAR] > 0.4f && rearGlass) { rearGlass = false; changed = true; }
        if (zone == ROOF && deform[ROOF] > 0.25f && windscreen) { windscreen = false; rearGlass = false; changed = true; }
        if (changed) version++;
        lastZone = zone;
        return zone;
    }

    public boolean burst(int wheel) {
        if (tyres[wheel] == CarWheel.Tyre.BURST) return false;
        tyres[wheel] = CarWheel.Tyre.BURST;
        version++;
        return true;
    }

    /** A bullet through the bodywork. Returns true when it mattered (glass, a lamp, the engine). */
    public boolean shot(float lx, float ly, float lz, CarHandlingData d, float damage) {
        int zone = zoneOf(lx, ly, lz, d.width / 2, d.length / 2, d.height);
        if (zone == FRONT && ly < d.height * 0.6f) { engine = Math.max(0, engine - damage * 0.0016f); return true; }
        if (ly > d.height * 0.6f && zone == FRONT && windscreen) { windscreen = false; version++; return true; }
        if (ly > d.height * 0.6f && zone == REAR && rearGlass) { rearGlass = false; version++; return true; }
        return false;
    }
}
