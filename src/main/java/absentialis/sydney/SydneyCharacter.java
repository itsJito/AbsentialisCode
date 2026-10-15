package absentialis.sydney;

import static absentialis.character.Ragdoll.*;

import absentialis.character.Ragdoll;
import absentialis.character.Rig;
import absentialis.world.World;

/**
 * The Sydney Engine: an active ragdoll. One of these owns a character's physical body while physics has it.
 *
 * The body is the game's existing ragdoll - the same particles, bones, joint limits and collision - and the solver
 * is still the only thing that moves it. What is new is everything between a blow and its result: sensors read the
 * body ({@link SensorSystem}), behaviours decide what it should try ({@link BehaviorCoordinator},
 * {@link BehaviorController}), virtual muscles push toward that with limited strength ({@link MotorController}) and
 * the solver works out what actually happens. So a hit is not answered with one of a set of falls: the character
 * sways, or catches itself with a step, or with two, or runs out of steps and goes down reaching for the ground,
 * lies there, and gets up again if its legs will do it - or does not.
 *
 * It runs inside the ragdoll's fixed 1/90 s step as its {@link Ragdoll.Driver}: sense, decide, drive, then the
 * ragdoll integrates and solves.
 */
public final class SydneyCharacter implements Ragdoll.Driver {
    public final PhysicsRig rig;
    public final SensorSystem sense = new SensorSystem();
    public final MotorController motors = new MotorController();
    public final BehaviorCoordinator mind = new BehaviorCoordinator();
    public final Goal goal = new Goal();
    public final SydneyConfig cfg;
    /** "human", "infected" or "giant". */
    public final String kind;
    public final float scale;
    /** Motor targets taken from the animated pose at hand-over (trunk frame), per joint. */
    public final float[][] pose = new float[PhysicsRig.NJ][3];
    public boolean alive = true;
    /** The particle nearest the last wound (-1: none), and whether a hand is on it this step. */
    public int wound = -1;
    public boolean holding;
    /** Steps simulated and nanoseconds spent in control (sensors, behaviours, motors): the per-character CPU cost. */
    public long stepsRun, nanos;
    /** Worst values seen, for the regression gates: deepest a particle got into the ground, and whether a number ever went bad. */
    public float worstPenetration;
    public boolean invalid;
    private boolean primed;
    public static boolean DEBUG;

    private SydneyCharacter(Ragdoll r, String kind) {
        rig = new PhysicsRig(r);
        this.kind = kind;
        cfg = SydneyConfig.of(kind);
        scale = r.unit / Rig.VOX;
        sense.standHeight = 0.866f * scale + 0.08f * scale;
    }

    /**
     * Gives a ragdoll that already holds the character's pose to Sydney. {@code missing} and {@code limp} are the
     * anatomy's rig-group masks: the body it gets is the body it has.
     */
    public static SydneyCharacter attach(Ragdoll r, String kind, int missing, int limp) {
        SydneyCharacter c = new SydneyCharacter(r, kind);
        c.rig.setBody(missing, limp);
        r.driver = c;
        r.live = true;
        return c;
    }

    public static SydneyCharacter of(Ragdoll r) { return r != null && r.driver instanceof SydneyCharacter c ? c : null; }

    public void setBody(int missing, int limp) { rig.setBody(missing, limp); }

    @Override public void drive(Ragdoll r, World w, float dt) {
        long t0 = System.nanoTime();
        sense.measure(rig, w, cfg);
        if (!primed) { AnimationBridge.capture(this); primed = true; }
        mind.update(this, goal, dt);
        motors.drive(rig, sense, goal, cfg);
        if (DEBUG && stepsRun < 3) { StringBuilder b = new StringBuilder("  [syd step " + stepsRun + " " + mind.state + " feet " + sense.feet + "]"); for (int j = 0; j < PhysicsRig.NJ; j++) b.append(String.format(" j%d a%.2f t%.0f g%.2f", j, motors.dbgAngle[j], motors.dbgTorque[j], goal.gain[j])); b.append(String.format(" posture %.2f bal %.2f fLA(%.0f,%.0f,%.0f) fLK(%.0f,%.0f,%.0f) fLH(%.0f,%.0f,%.0f)", goal.posture, goal.balance, r.fx[LA], r.fy[LA], r.fz[LA], r.fx[LK], r.fy[LK], r.fz[LK], r.fx[LH], r.fy[LH], r.fz[LH])); System.out.println(b); }
        nanos += System.nanoTime() - t0;
        stepsRun++;
        for (int i = 0; i < N; i++) {
            if (Float.isNaN(r.x[i]) || Float.isNaN(r.y[i]) || Float.isNaN(r.z[i]) || Float.isInfinite(r.fx[i]) || Float.isNaN(r.fx[i]) || Float.isNaN(r.fy[i]) || Float.isNaN(r.fz[i])) { invalid = true; r.fx[i] = r.fy[i] = r.fz[i] = 0; }
            if (w != null) { float pen = sense.groundY - r.y[i]; if (pen > worstPenetration && Math.abs(r.x[i] - sense.com[0]) < 0.4f && Math.abs(r.z[i] - sense.com[2]) < 0.4f) worstPenetration = pen; }
        }
        // death: when the last of the tone has drained the body is an ordinary ragdoll again and may come to rest
        if (mind.outcome == BehaviorCoordinator.Outcome.DEAD) { r.live = false; r.driver = null; }
    }

    /**
     * A blow at a place on the body: a change of momentum (newton seconds) applied where it landed. The particle
     * struck takes it; the bones carry it to the rest. Nothing else is decided here - whether this staggers the
     * character or floors it is what the following steps find out.
     */
    public void hit(int particle, float dx, float dy, float dz, float impulse, float force) {
        Ragdoll r = rig.r;
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-5f) return;
        dx /= l; dy /= l; dz /= l;
        float dv = impulse / rig.mass(particle), cap = 6f;
        float rest = 0;
        if (dv > cap) { rest = (dv - cap) * rig.mass(particle); dv = cap; }
        r.kick(particle, dx * dv, dy * dv, dz * dv);
        if (rest > 0) { float share = rest / (rig.mass(NECK) + rig.mass(PELVIS)); r.kick(NECK, dx * share, dy * share, dz * share); r.kick(PELVIS, dx * share, dy * share, dz * share); }
        wound = particle;
        mind.onHit(this, force);
    }

    /** The character has died: its muscles let go over a moment and the body is left to the solver. */
    public void die() { alive = false; mind.die(); }

    public BehaviorCoordinator.State state() { return mind.state; }
    public BehaviorCoordinator.Outcome outcome() { return mind.outcome; }

    /** Hands the body back: Sydney stops driving it. (The caller returns the character to animation, or leaves the ragdoll to settle.) */
    public void release() { rig.r.driver = null; rig.r.live = false; }

    /** Mean cost of one control step in microseconds. */
    public float microsPerStep() { return stepsRun == 0 ? 0 : nanos / 1000f / stepsRun; }

    /** The rig particle that stands for a region of the render skeleton (where a hit on that region lands). */
    public static int particleOf(int rigGroup, boolean low) {
        return switch (rigGroup) {
            case Rig.HEAD -> HEAD_P;
            case Rig.L_UPPER_ARM -> LE; case Rig.L_FOREARM, Rig.L_HAND -> LW;
            case Rig.R_UPPER_ARM -> RE; case Rig.R_FOREARM, Rig.R_HAND -> RW;
            case Rig.L_THIGH -> LK; case Rig.L_SHIN -> LA;
            case Rig.R_THIGH -> RK; case Rig.R_SHIN -> RA;
            default -> low ? PELVIS : NECK;
        };
    }
}
