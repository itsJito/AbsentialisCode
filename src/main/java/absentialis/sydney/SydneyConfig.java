package absentialis.sydney;

import absentialis.engine.util.Json;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-character tuning of the Sydney Engine (content/sydney.json). Everything that decides how a body fights for its
 * balance is a number here: how stiff and how strong each joint's motor is, how far the centre of mass may stray
 * before a step is taken, how many steps a body gets, how it falls, how long it lies and how hard it can push itself
 * back up. One profile per kind of body: "human", "infected", "giant".
 */
public final class SydneyConfig {
    /** Joint classes: every joint of the rig belongs to one. */
    public static final int SPINE = 0, HIP = 1, KNEE = 2, SHOULDER = 3, ELBOW = 4, NECK = 5, CLASSES = 6;
    static final String[] CLASS_NAMES = {"spine", "hip", "knee", "shoulder", "elbow", "neck"};

    /** Motor natural frequency (Hz), damping ratio and torque limit (newton metres) per joint class. */
    public final float[] freq = {2.5f, 4.5f, 5.5f, 4f, 4.5f, 4f}, zeta = {0.9f, 0.8f, 0.8f, 0.8f, 0.8f, 0.9f}, torque = {1100, 700, 700, 160, 110, 70};
    /** Overall muscle strength (scales every torque limit and the get-up push). */
    public float strength = 1f;
    /** How much stiffer a leg is while its foot carries the body. */
    public float stanceGain = 3f;
    /** How hard the hips turn the trunk toward vertical while standing (0 = the body is held straight and balanced by the ankles alone). */
    public float standPosture = 1f;
    /** How firmly a planted leg's hip holds its own pose (the rest of that hip's effort goes to holding the trunk up). */
    public float stanceHip = 0.2f;
    /** The same two while the body is moving or on one leg: the legs carry, the trunk follows. */
    public float carryHip = 0.2f, carryPosture = 1f;

    // ---- balance
    /** The lever a planted foot gives (half a foot, metres): the most the ankles can shift the point of support. */
    public float ankleLever = 0.13f;
    /** How hard the ankles chase the capture point (1 = just enough to hold it). */
    public float balanceGain = 1.8f;
    /** A step is taken when the capture point is this many times outside the reach of the feet. */
    public float stepThreshold = 1.5f;
    public float stepTime = 0.2f, stepReach = 0.62f, stepLift = 0.1f, stepOvershoot = 0.1f, stepLead = 0.6f, stepForce = 900f;
    /** Recovery steps a body gets before it gives up on its balance. */
    public int maxSteps = 4;
    /** Lean (degrees) and loss of height (share of standing height) past which balance has failed. */
    public float fallTilt = 52f, fallHeight = 0.5f, lostReach = 1.0f;
    /** Seconds of quiet standing before the body is its own again. */
    public float recoverSettle = 0.4f;

    // ---- falling and the ground
    /** How much of its strength a falling body keeps, and how hard it reaches for what it is falling toward (0 = it does not). */
    public float fallStrength = 0.3f, brace = 0.85f;
    public float downStrength = 0.08f, downMin = 0.9f, downPerForce = 0.02f, downMax = 3.5f;
    /** Whether the hand goes to the wound (humans do; the infected do not notice). */
    public boolean holdWound = true;
    public float holdForce = 60f;

    // ---- getting up
    public float getUpTime = 1.5f, getUpStrength = 1.9f, getUpTimeout = 1.9f;
    public int getUpTries = 3;

    // ---- dying
    public float deathFade = 0.5f;
    /** How much the dying trunk still tries to stay over the hips while its tone drains (0 = none: it goes wherever the blow sends it). */
    public float deathPosture = 0.25f;

    /** How much force (in damage points of shove) it takes to stagger and to floor this kind of body, relative to a human. */
    public float staggerMul = 1f, knockMul = 1f;

    /**
     * 1.1: how this kind of body gets up. A person tucks the feet under, crouches and stands. One of the dead does not
     * get up like a person: the hips come up first with the trunk hanging from them, the arms are dragged, the head is
     * the last thing to lift. The Crawler does not stand at all: it rolls onto its front and gets its hands and feet
     * under it, splayed, and is on all fours again.
     */
    public static final int HUMAN = 0, INFECTED = 1, CRAWLER = 2;
    public int style = HUMAN;
    /** The height (a share of standing height) a body that goes on all fours carries its trunk at. */
    public float crawlHeight = 0.36f;

    private static Map<String, SydneyConfig> all;

    public static synchronized SydneyConfig of(String kind) {
        if (all == null) all = load();
        SydneyConfig c = all.get(kind);
        return c != null ? c : all.get("human");
    }

    private static Map<String, SydneyConfig> load() {
        Map<String, SydneyConfig> m = new HashMap<>();
        SydneyConfig human = new SydneyConfig(), infected = new SydneyConfig(), giant = new SydneyConfig(), crawler = new SydneyConfig();
        for (SydneyConfig c : new SydneyConfig[]{infected, crawler}) {
            c.maxSteps = 6; c.ankleLever = 0.17f; c.balanceGain = 2.4f; c.brace = 0.15f; c.holdWound = false; c.downMin = 0.4f; c.downMax = 1.6f; c.getUpTime = 1.0f;
            c.staggerMul = 1.6f; c.knockMul = 1.35f; c.strength = 1.15f; c.fallTilt = 60f; c.deathFade = 0.6f;
            c.style = INFECTED;
        }
        infected.getUpTime = 1.7f; infected.getUpTimeout = 2.2f;
        crawler.style = CRAWLER; crawler.downMin = 0.2f; crawler.downMax = 0.8f; crawler.getUpTime = 0.8f; crawler.getUpTries = 8; crawler.getUpStrength = 1.4f;
        giant.maxSteps = 8; giant.brace = 0.3f; giant.holdWound = false; giant.strength = 1.6f; giant.staggerMul = 1f; giant.knockMul = 1f; giant.downMin = 0.6f; giant.downMax = 2f;
        giant.style = INFECTED;
        m.put("human", human); m.put("infected", infected); m.put("giant", giant); m.put("crawler", crawler);
        try (InputStream in = SydneyConfig.class.getResourceAsStream("/content/sydney.json")) {
            if (in == null) { System.err.println("[Sydney] content/sydney.json missing - using built-in defaults"); return m; }
            Map<String, Object> root = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            // (the Crawler is one of the dead first: it takes their figures, then its own)
            crawler.read(Json.obj(root, "infected"));
            for (Map.Entry<String, SydneyConfig> e : m.entrySet()) e.getValue().read(Json.obj(root, e.getKey()));
            infected.style = INFECTED; giant.style = INFECTED; crawler.style = CRAWLER;
            if (!Json.obj(Json.obj(root, "infected"), "getUp").containsKey("style")) { infected.getUpTime = Math.max(infected.getUpTime, 1.7f); infected.getUpTimeout = Math.max(infected.getUpTimeout, 2.2f); }
            if (!root.containsKey("crawler")) { crawler.downMin = 0.2f; crawler.downMax = 0.8f; crawler.getUpTime = 0.8f; crawler.getUpTries = 8; }
        } catch (Exception ex) { System.err.println("[Sydney] content/sydney.json unreadable (" + ex + ") - using built-in defaults"); }
        return m;
    }

    private void read(Map<String, Object> o) {
        Map<String, Object> mo = Json.obj(o, "motors");
        for (int c = 0; c < CLASSES; c++) {
            Map<String, Object> j = Json.obj(mo, CLASS_NAMES[c]);
            freq[c] = (float) Json.num(j, "hz", freq[c]); zeta[c] = (float) Json.num(j, "damping", zeta[c]); torque[c] = (float) Json.num(j, "torque", torque[c]);
        }
        strength = (float) Json.num(o, "strength", strength); stanceGain = (float) Json.num(o, "stanceGain", stanceGain); standPosture = (float) Json.num(o, "standPosture", standPosture); stanceHip = (float) Json.num(o, "stanceHip", stanceHip); carryHip = (float) Json.num(o, "carryHip", carryHip); carryPosture = (float) Json.num(o, "carryPosture", carryPosture);
        Map<String, Object> b = Json.obj(o, "balance");
        ankleLever = (float) Json.num(b, "ankleLever", ankleLever); balanceGain = (float) Json.num(b, "gain", balanceGain); stepThreshold = (float) Json.num(b, "stepThreshold", stepThreshold);
        stepTime = (float) Json.num(b, "stepTime", stepTime); stepReach = (float) Json.num(b, "stepReach", stepReach); stepLift = (float) Json.num(b, "stepLift", stepLift);
        stepOvershoot = (float) Json.num(b, "stepOvershoot", stepOvershoot); stepLead = (float) Json.num(b, "stepLead", stepLead); stepForce = (float) Json.num(b, "stepForce", stepForce); maxSteps = Json.integer(b, "maxSteps", maxSteps);
        fallTilt = (float) Json.num(b, "fallTilt", fallTilt); fallHeight = (float) Json.num(b, "fallHeight", fallHeight); lostReach = (float) Json.num(b, "lostReach", lostReach);
        recoverSettle = (float) Json.num(b, "recoverSettle", recoverSettle);
        Map<String, Object> f = Json.obj(o, "fall");
        fallStrength = (float) Json.num(f, "strength", fallStrength); brace = (float) Json.num(f, "brace", brace); downStrength = (float) Json.num(f, "downStrength", downStrength);
        downMin = (float) Json.num(f, "downMin", downMin); downPerForce = (float) Json.num(f, "downPerForce", downPerForce); downMax = (float) Json.num(f, "downMax", downMax);
        holdWound = Json.bool(f, "holdWound", holdWound); holdForce = (float) Json.num(f, "holdForce", holdForce); deathFade = (float) Json.num(f, "deathFade", deathFade); deathPosture = (float) Json.num(f, "deathPosture", deathPosture);
        Map<String, Object> g = Json.obj(o, "getUp");
        getUpTime = (float) Json.num(g, "time", getUpTime); getUpStrength = (float) Json.num(g, "strength", getUpStrength); getUpTimeout = (float) Json.num(g, "timeout", getUpTimeout); getUpTries = Json.integer(g, "tries", getUpTries);
        Map<String, Object> r = Json.obj(o, "response");
        staggerMul = (float) Json.num(r, "stagger", staggerMul); knockMul = (float) Json.num(r, "knockdown", knockMul);
    }
}
