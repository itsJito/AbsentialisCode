package absentialis.vehicle;

/**
 * One wheel: a simulated contact point, not a rigid body. It keeps its own geometry, contact, suspension and
 * traction state and applies its own forces to the chassis; nothing about one wheel is shared with another.
 */
public final class CarWheel {
    public static final int FRONT_LEFT = 0, FRONT_RIGHT = 1, REAR_LEFT = 2, REAR_RIGHT = 3;
    public static final String[] NAME = {"FRONT_LEFT", "FRONT_RIGHT", "REAR_LEFT", "REAR_RIGHT"};

    /** Tyre behaviour this step. */
    public enum State { NORMAL, SPINNING, SKIDDING, FIXED }
    /** Tyre condition. */
    public enum Tyre { INTACT, BURST }

    public final int index;
    public final boolean front;
    /** Attachment point in the car frame (relative to the car's origin, not its centre of mass). */
    public float ax, ay, az;

    // ---- contact
    public boolean grounded;
    public float cx, cy, cz;                 // world-space contact point
    public float nx, ny = 1, nz;             // ground normal
    public CarSurfaceData.Surface surface = CarSurfaceData.Surface.ASPHALT;
    public int blockId;
    // ---- suspension
    public float length;                     // current distance from the top of travel to the wheel centre
    public float compression, prevCompression;
    public float normalImpulse;              // what the suspension pushed with this step (N s)
    // ---- directions and velocities at the contact
    public float fx, fy, fz, sx, sy, sz;     // forward and sideways unit vectors on the ground plane
    public float forwardVel, sideVel;
    public float steerAngle;
    // ---- rotation (visual, but driven by the physics state)
    public float rotation, rotationSpeed;
    // ---- traction
    public float adhesion;                   // impulse the tyre could transmit this step
    public float demand;                     // impulse the car asked of it
    public float driveImpulse, brakeImpulse, forwardImpulse, sideImpulse;
    public State state = State.NORMAL;
    public Tyre tyre = Tyre.INTACT;
    /** Seconds this wheel has been continuously sliding (skid marks and sound fade in with it). */
    public float slideTime;

    CarWheel(int index) { this.index = index; this.front = index < 2; }

    public boolean sliding() { return grounded && state != State.NORMAL; }
}
