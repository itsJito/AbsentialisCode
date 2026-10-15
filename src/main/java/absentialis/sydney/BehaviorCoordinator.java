package absentialis.sydney;

/**
 * Decides what the body is trying to do, and lets compatible intentions run together.
 *
 * It is a small state machine driven only by the sensors - standing, stepping, falling, down, getting up, dead - and
 * each state is a list of behaviours in order of urgency. They all write into one {@link Goal}; a channel belongs to
 * the first behaviour to claim it, so whatever protects the body (the step that might save it, the brace before it
 * hits the ground) outranks whatever merely keeps it comfortable (a hand on a wound, the arms as they were).
 *
 * Every transition is a judgement the body could be wrong about: it steps when a step might catch it, gives up when
 * it has run out of steps or leaned too far, and tries to stand again when it has lain long enough.
 */
public final class BehaviorCoordinator {
    public enum State { STAND, STEP, FALL, DOWN, GETUP, DEAD }
    /** How it ended, once it has: on its feet again, down for good and crawling, unable to rise where it lies, or dead. */
    public enum Outcome { NONE, RECOVERED, CRAWL, STUCK, DEAD }

    public State state = State.STAND;
    public Outcome outcome = Outcome.NONE;
    /** Time in the current state, quiet time on its feet, time since anything was under it, how long it will lie. */
    public float t, settled, airborne, quiet, downFor = 1.2f, stun;
    private float over;
    public int steps, tries, falls, recoveries;

    private final BehaviorController.Stand stand = new BehaviorController.Stand();
    private final BehaviorController.Step step = new BehaviorController.Step();
    private final BehaviorController.Brace brace = new BehaviorController.Brace();
    private final BehaviorController.Fall fall = new BehaviorController.Fall();
    private final BehaviorController.Lie lie = new BehaviorController.Lie();
    private final BehaviorController.HoldWound hold = new BehaviorController.HoldWound();
    private final BehaviorController.GetUp getUp = new BehaviorController.GetUp();
    private final BehaviorController.Limp limp = new BehaviorController.Limp();
    private final BehaviorController.Flail flail = new BehaviorController.Flail();
    /** 1.1: this body is not to get up of its own accord (it has been put down wounded: what it does next is its owner's to say). */
    public boolean stayDown;
    /** 1.1: seconds this body has been falling through the air without touching anything, and the longest it has (gates). */
    public float longestAir;

    private void go(State s) { state = s; t = 0; }

    /** 1.1: the legs are taken from under it now - a faint, a wound that puts it down, a fall that has gone on too long. */
    public void collapse() { if (state != State.DEAD && state != State.FALL && state != State.DOWN) fallNow(); }

    public void die() { if (state != State.DEAD) { go(State.DEAD); limp.t = 0; outcome = Outcome.NONE; } }

    /** A blow landed: it resets what the body thought it had settled, and a hard one leaves it weak for a moment. */
    public void onHit(SydneyCharacter c, float force) {
        settled = 0;
        stun = Math.max(stun, Math.min(1.2f, force / 110f));
        downFor = Math.max(downFor, Math.min(c.cfg.downMax, c.cfg.downMin + force * c.cfg.downPerForce));
        if (outcome == Outcome.RECOVERED) outcome = Outcome.NONE;
    }

    public void update(SydneyCharacter c, Goal g, float dt) {
        SensorSystem s = c.sense;
        SydneyConfig cfg = c.cfg;
        t += dt;
        stun = Math.max(0, stun - dt);
        airborne = s.feet == 0 && s.otherContacts == 0 ? airborne + dt : 0;
        if (airborne > longestAir) longestAir = airborne;
        if (airborne <= 0) flail.t = 0;
        boolean legs = c.rig.legL && c.rig.legR;
        boolean toppled = s.tilt > Math.toRadians(cfg.fallTilt) || s.height < s.standHeight * cfg.fallHeight;
        if (!c.alive && state != State.DEAD) die();

        switch (state) {
            case STAND -> {
                if (!legs || toppled || airborne > 0.25f) { fallNow(); break; }
                over = s.errNorm > cfg.stepThreshold ? over + dt : 0;
                if (over > 0.04f && s.feet > 0) {
                    over = 0;
                    if (steps >= cfg.maxSteps || s.err > cfg.lostReach * c.scale || !step.begin(c)) fallNow();
                    else { steps++; go(State.STEP); }
                    break;
                }
                settled = s.err < 0.07f * c.scale && s.speed < 0.3f && s.feet == 2 ? settled + dt : 0;
                if (settled >= cfg.recoverSettle && stun <= 0) { outcome = Outcome.RECOVERED; recoveries++; }
            }
            case STEP -> {
                if (toppled) { fallNow(); break; }
                if (step.done(c)) { stand.plant(c); go(State.STAND); }
            }
            case FALL -> {
                quiet = s.speed < 0.7f ? quiet + dt : 0;
                if ((s.height < s.standHeight * 0.55f || t > 0.6f) && quiet > 0.3f || t > 3.5f) { go(State.DOWN); quiet = 0; }
            }
            case DOWN -> {
                if (t < downFor || stayDown) break;
                // (a body that goes on all fours needs its limbs, not its legs, to be back on them)
                if (!legs && cfg.style != SydneyConfig.CRAWLER) { outcome = Outcome.CRAWL; break; }
                if (tries >= cfg.getUpTries) { outcome = Outcome.STUCK; break; }
                tries++;
                getUp.begin(c);
                go(State.GETUP);
            }
            case GETUP -> {
                if (cfg.style == SydneyConfig.CRAWLER) {
                    // on all fours again: the trunk off the ground at the height it runs at, lying level, and still enough
                    boolean four = s.height > s.standHeight * cfg.crawlHeight * 0.72f && Math.abs(s.up[1]) < 0.62f && s.speed < 1.4f;
                    if (four && getUp.progress(c) > 0.75f || getUp.progress(c) > 1.6f) { outcome = Outcome.RECOVERED; recoveries++; }
                    break;
                }
                if (!legs) { go(State.DOWN); downFor = 0.3f; break; }
                boolean up = s.height > s.standHeight * 0.9f && s.tilt < Math.toRadians(16) && s.feet > 0;
                if (up && getUp.progress(c) > 0.7f) { steps = 0; settled = 0; stand.reset(); go(State.STAND); break; }
                if (t > cfg.getUpTime + cfg.getUpTimeout) { go(State.DOWN); downFor = 0.7f; }
            }
            case DEAD -> { if (limp.t >= cfg.deathFade) outcome = Outcome.DEAD; }
        }

        g.clear();
        c.holding = false;
        g.strength = stun > 0 && state != State.DEAD ? 0.4f : 1f;
        switch (state) {
            case STAND -> { hold.goals(c, g, dt); stand.goals(c, g, dt); }
            case STEP -> { step.goals(c, g, dt); stand.goals(c, g, dt); }
            case FALL -> { if (airborne > 0.22f) flail.goals(c, g, dt); brace.goals(c, g, dt); fall.goals(c, g, dt); }
            case DOWN -> { hold.goals(c, g, dt); lie.goals(c, g, dt); }
            case GETUP -> getUp.goals(c, g, dt);
            case DEAD -> limp.goals(c, g, dt);
        }
        g.finish();
    }

    private void fallNow() { falls++; quiet = 0; go(State.FALL); }
}
