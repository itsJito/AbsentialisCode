package absentialis.vehicle;

/** What the driver is asking for this step. Nothing here moves the car: the simulation turns it into forces. */
public final class CarInput {
    /** 0 .. 1 accelerator, 0 .. 1 foot brake, steering -1 .. +1 (positive turns toward the car frame's +x: the driver's left). */
    public float throttle, brake, steer;
    public boolean handbrake;
    /** The driver wants to go backwards (the transmission selects reverse once the car has stopped rolling forward). */
    public boolean reverse;

    public void clear() { throttle = 0; brake = 0; steer = 0; handbrake = false; reverse = false; }

    /**
     * Keyboard driving: forward/back keys become accelerator, brake or reverse depending on which way the car is
     * rolling - holding "back" while moving forward brakes, and only once stopped does it reverse.
     */
    public void fromKeys(boolean fwd, boolean back, boolean left, boolean right, boolean hand, float forwardSpeed) {
        throttle = 0; brake = 0; reverse = false;
        if (fwd && !back) {
            if (forwardSpeed < -0.8f) brake = 1; else throttle = 1;
        } else if (back && !fwd) {
            if (forwardSpeed > 0.8f) brake = 1; else { throttle = 1; reverse = true; }
        } else if (fwd) brake = 1;
        // 0.6: the simulation's positive steer turns the car toward its own +x, and the game's facing convention puts
        // the driver's left there - so through 0.5 the A key carried the car right. Left is left as the driver sees it.
        steer = (left ? 1 : 0) - (right ? 1 : 0);
        handbrake = hand;
    }
}
