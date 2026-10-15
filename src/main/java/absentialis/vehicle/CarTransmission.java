package absentialis.vehicle;

/**
 * Simplified automatic transmission: no clutch, no torque curve, no engine speed. Gear 0 is reverse; forward
 * gears start at 1. For N forward gears, gear i tops out at (i / N) of the configured maximum velocity.
 *
 * Shifting uses separate thresholds so the box does not hunt at a gear boundary: with L the speed at the bottom of
 * the current gear's band and I the band's width, it changes up above L + 0.6667 I and back down below
 * L(previous) + 0.42 I.
 *
 * Acceleration falls away with speed and with each gear. With f = 1 - (gear - 1) / (N - 1):
 *   speed multiplier = 1 + 3 f^2,  target speed = gear maximum * multiplier,
 *   acceleration = throttle * (target - speed) * gear-adjusted engine acceleration / |target|
 * so first gear pulls hard and top gear runs out exactly at maximum velocity - the engine simply stops adding
 * thrust there; nothing ever assigns the car's speed.
 */
public final class CarTransmission {
    private final CarHandlingData d;
    /** 0 = reverse, 1 .. gears = forward. */
    public int gear = 1;
    /** Seconds since the last change (thrust dips for a moment while the box swaps cogs). */
    public float sinceShift = 9;
    public int shifts;
    /** Position within the current gear's speed band, 0 .. 1 (drives the engine note). */
    public float band;

    public CarTransmission(CarHandlingData d) { this.d = d; }

    public float gearMax(int g) { return g <= 0 ? d.maxReverseVelocity : d.maxVelocity * g / (float) d.gears; }
    private float interval() { return d.maxVelocity / d.gears; }
    /** Speed above which gear g changes up. */
    public float upshiftAt(int g) { return gearMax(g - 1 <= 0 ? 0 : g - 1) * (g > 1 ? 1 : 0) + 0.6667f * interval(); }
    /** Speed below which gear g changes down to g - 1. */
    public float downshiftAt(int g) { return (g >= 3 ? gearMax(g - 2) : 0) + 0.42f * interval(); }

    /** Selects the gear for the current forward speed and the driver's intent. */
    public void update(float forwardSpeed, boolean wantReverse, float dt) {
        sinceShift += dt;
        int before = gear;
        if (wantReverse) {
            if (forwardSpeed < 0.8f) gear = 0;          // still rolling forward: the brake does the work first
        } else {
            if (gear == 0) { if (forwardSpeed > -0.8f) gear = 1; }
            else {
                while (gear < d.gears && forwardSpeed > upshiftAt(gear)) gear++;
                while (gear > 1 && forwardSpeed < downshiftAt(gear)) gear--;
            }
        }
        if (gear != before) { sinceShift = 0; shifts++; }
        float lo = gear <= 1 ? 0 : gearMax(gear - 1) * 0.55f, hi = gear == 0 ? d.maxReverseVelocity : gearMax(gear);
        band = Math.max(0, Math.min(1, (Math.abs(forwardSpeed) - lo) / Math.max(0.5f, hi - lo)));
    }

    /**
     * Total drive acceleration (m/s^2, signed along the car's forward axis) the engine offers at this speed and
     * throttle. The caller splits it between the driven wheels; whether the tyres can transmit it is their affair.
     */
    public float acceleration(float throttle, float forwardSpeed) {
        if (throttle <= 0) return 0;
        // a short dip in drive while a gear change completes
        float cut = sinceShift < 0.18f ? 0.35f + 0.65f * (sinceShift / 0.18f) : 1f;
        if (gear == 0) {
            float target = d.maxReverseVelocity, v = -forwardSpeed;
            if (target < 1e-3f) return 0;
            float a = throttle * (target - v) * d.engineAcceleration * d.firstGearBoost * 0.8f / target;
            return -Math.max(0, a) * cut;
        }
        float f = d.gears > 1 ? 1f - (gear - 1) / (float) (d.gears - 1) : 0f;
        float mult = 1f + 3f * f * f;
        float target = gearMax(gear) * mult;
        if (Math.abs(target) < 1e-3f) return 0;                      // degenerate configuration: no thrust rather than a division by zero
        float accel = d.engineAcceleration * (gear == 1 ? d.firstGearBoost : gear == 2 ? d.secondGearBoost : 1f);
        float a = throttle * (target - forwardSpeed) * accel / Math.abs(target);
        // past the configured maximum the engine adds nothing (drag and the road do the rest)
        if (forwardSpeed >= d.maxVelocity) return 0;
        return Math.max(0, a) * cut;
    }

    public String label() { return gear == 0 ? "R" : String.valueOf(gear); }
}
