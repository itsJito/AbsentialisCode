package absentialis.gameplay;

import absentialis.engine.util.Json;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Combat and casualty tuning from {@code content/combat.json} (0.5): health-condition thresholds and their
 * effects, incapacitation eligibility, bleed-out and revival, hit-marker and killfeed timing, speech bubbles and
 * contextual cover. One instance, read once; systems ask it instead of carrying numbers of their own.
 */
public final class CombatConfig {
    /** The four health presentation conditions, in order of severity. */
    public enum Condition {
        FINE("Fine"), CAUTION("Caution"), INJURED("Injured"), CRITICAL("Critical");
        public final String label;
        Condition(String l) { label = l; }
    }

    // ---- health conditions
    public float caution = 0.70f, injured = 0.45f, critical = 0.22f;
    public final float[] speed = {1f, 0.92f, 0.76f, 0.52f}, sprintSpeed = {1f, 0.95f, 0.8f, 0f}, aimError = {1f, 1.08f, 1.25f, 1.55f}, blendRate = {1f, 0.85f, 0.65f, 0.48f}, animation = {0f, 0.34f, 0.68f, 1f};
    public final boolean[] sprint = {true, true, true, false};
    public final int[] colors = {0xFF7DBA5C, 0xFFE0C040, 0xFFE08A30, 0xFFD8483A};
    // ---- maximum health progression
    public int baseHealth = 100, perHerb = 10, healthCap = 200;
    // ---- incapacitation
    public float downChance = 0.2f, downChancePlayer = 0.45f, downChanceMate = 0.6f, overkill = 0.55f, bleedOut = 20f, downedHealth = 34, crawlSpeed = 0.55f, sidearmError = 2.2f, sidearmCooldown = 1.6f, helpCallEvery = 6f;
    public final Map<String, Float> downTypes = new HashMap<>();
    public final java.util.Set<String> fatalRegions = new java.util.HashSet<>();
    // ---- revival
    public float reviveTime = 4f, reviveRange = 1.9f, reviveHealth = 0.18f;
    public float aiUrgency = 1.6f, aiDistance = 0.045f, aiPressure = 1.8f, aiExposure = 1.2f, aiCover = 0.6f, aiOwnHealth = 1.4f, aiRoleCost = 0.5f, aiThreshold = 0.15f, aiMaxDistance = 34f, aiAbortSuppression = 0.75f;
    // ---- feedback
    public float markerLife = 0.28f, markerLifeKill = 0.5f, markerSize = 9, markerGap = 5;
    public final int[] markerColors = {0xFFFFFFFF, 0xFFF2D43A, 0xFFE23B2E};
    public float feedLife = 6.5f, feedFade = 0.6f, feedSlide = 0.18f;
    public int feedMax = 6;
    public float talkBase = 2.2f, talkPerChar = 0.045f, talkMaxDistance = 30, talkFadeFrom = 20, talkWrap = 190;
    public int talkMaxVisible = 6;
    public float coverReach = 0.62f, coverMaxSpeed = 1.4f, coverLean = 0.42f, coverAiLean = 0.46f;
    // ---- 0.6: what takes a head off, by weapon family, and how hard a shotgun hits
    /** How a family of weapon decapitates on a killing head shot (content/combat.json "decapitation"). */
    public record Decap(boolean never, float minForce, float chanceFrom, float chanceSpan, float max, float closeRange, float closeChance) {
        /** Chance that a killing head shot of this force, from this far, takes the head. */
        public float chance(float force, float range) {
            if (never) return 0;
            if (closeRange > 0 && range <= closeRange) return closeChance;
            if (force < minForce) return 0;
            return Math.max(0, Math.min(max, (force - chanceFrom) / Math.max(1f, chanceSpan)));
        }
    }
    private final Map<String, Decap> decap = new HashMap<>();
    private Decap decapDefault = new Decap(false, 85, 70, 120, 0.9f, 0, 0);
    public float shotgunDamage = 1.4f, shotgunCloseRange = 8f, shotgunCloseBonus = 1.35f;
    /**
     * How characters perceive (0.6). Sight: how much slower something at the edge of vision is noticed than something
     * looked straight at; how steeply above or below an unalerted watcher looks (degrees); how much of the fog bank's
     * distance anyone can see through. Hearing: how much a floor between muffles a sound, and how much better the
     * alerted listen.
     */
    public float seeEdge = 0.45f, seeVertical = 52f, seeFog = 0.9f, hearStorey = 0.65f, hearAlert = 1.18f;
    /** The infected eat the dead: seconds one of them takes to finish a body alone, how near it must be to start, and how far it will come for one. */
    public float feedSeconds = 50f, feedReach = 1.5f, feedSeek = 26f;
    /** The fog bank: where the world begins to dissolve, where it is gone (always inside the draw distance), the haze multiplier before it, how much sky it takes overhead, and how much nearer it stands at night and in rain. */
    public float fogStart = 14f, fogEnd = 118f, fogHaze = 2.5f, fogSky = 0.72f, fogNight = 0.8f, fogRain = 0.78f;
    /** 0.9: mist by the hour (sim.ZoneState.mistAt): how much thicker the haze is at its worst, and how much nearer the bank stands. */
    public float fogMist = 2.2f, fogMistWall = 0.6f;
    /** 0.7: how much of the street's haze hangs in the air under a roof, and the daylight that reaches indoors by bouncing (the ambient floor at noon). */
    public float fogIndoor = 0.22f, indoorDaylight = 0.05f;
    /** Name tags over heads: how near a stranger has to be, how near the infected, how far a squadmate's tag carries, and the text size. */
    public float tagRange = 18f, tagInfectedRange = 9f, tagSquadRange = 160f, tagSize = 12f;

    public Decap decap(String family) { return decap.getOrDefault(family == null ? "" : family, decapDefault); }

    private static CombatConfig instance;

    public static synchronized CombatConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    /** The condition for a health value against a maximum. */
    public Condition condition(float hp, float maxHp) {
        float f = maxHp <= 0 ? 0 : hp / maxHp;
        return f > caution ? Condition.FINE : f > injured ? Condition.CAUTION : f > critical ? Condition.INJURED : Condition.CRITICAL;
    }

    public int color(Condition c) { return colors[c.ordinal()]; }

    /** Maximum health for a number of Yellow Herbs taken (capped). */
    public int maxHealth(int herbs) { return Math.min(healthCap, baseHealth + Math.max(0, herbs) * perHerb); }
    public int herbCap() { return Math.max(0, (healthCap - baseHealth) / Math.max(1, perHerb)); }

    public float downType(String kind) { return downTypes.getOrDefault(kind, 0.5f); }

    private static void floats(Map<String, Object> m, String key, float[] out) {
        List<Object> l = Json.list(m, key);
        for (int i = 0; i < out.length && i < l.size(); i++) out[i] = ((Number) l.get(i)).floatValue();
    }

    private static void colours(Map<String, Object> m, String key, int[] out) {
        List<Object> l = Json.list(m, key);
        for (int i = 0; i < out.length && i < l.size(); i++) out[i] = 0xFF000000 | Integer.parseInt(l.get(i).toString(), 16);
    }

    private static CombatConfig load() {
        CombatConfig c = new CombatConfig();
        c.downTypes.put("bullet", 1f); c.downTypes.put("melee", 0.8f); c.downTypes.put("blunt", 0.7f); c.downTypes.put("blast", 0.35f);
        c.downTypes.put("vehicle", 0.3f); c.downTypes.put("burn", 0f); c.downTypes.put("bleed", 0f);
        c.fatalRegions.add("head");
        try (InputStream in = CombatConfig.class.getResourceAsStream("/content/combat.json")) {
            if (in == null) { System.err.println("[Combat] content/combat.json missing - using built-in defaults"); return c; }
            Map<String, Object> root = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            Map<String, Object> h = Json.obj(root, "health");
            c.caution = (float) Json.num(h, "caution", c.caution); c.injured = (float) Json.num(h, "injured", c.injured); c.critical = (float) Json.num(h, "critical", c.critical);
            floats(h, "speed", c.speed); floats(h, "sprintSpeed", c.sprintSpeed); floats(h, "aimError", c.aimError); floats(h, "blendRate", c.blendRate); floats(h, "animation", c.animation);
            colours(h, "colors", c.colors);
            List<Object> sp = Json.list(h, "sprint");
            for (int i = 0; i < 4 && i < sp.size(); i++) c.sprint[i] = Boolean.TRUE.equals(sp.get(i));
            Map<String, Object> mh = Json.obj(root, "maxHealth");
            c.baseHealth = Json.integer(mh, "base", c.baseHealth); c.perHerb = Json.integer(mh, "perHerb", c.perHerb); c.healthCap = Json.integer(mh, "cap", c.healthCap);
            Map<String, Object> d = Json.obj(root, "downed");
            c.downChance = (float) Json.num(d, "chance", c.downChance); c.downChancePlayer = (float) Json.num(d, "chancePlayer", c.downChancePlayer); c.downChanceMate = (float) Json.num(d, "chanceMate", c.downChanceMate);
            c.overkill = (float) Json.num(d, "overkill", c.overkill); c.bleedOut = (float) Json.num(d, "bleedOut", c.bleedOut); c.downedHealth = (float) Json.num(d, "health", c.downedHealth);
            c.crawlSpeed = (float) Json.num(d, "crawlSpeed", c.crawlSpeed); c.sidearmError = (float) Json.num(d, "sidearmError", c.sidearmError); c.sidearmCooldown = (float) Json.num(d, "sidearmCooldown", c.sidearmCooldown);
            c.helpCallEvery = (float) Json.num(d, "helpCallEvery", c.helpCallEvery);
            for (Map.Entry<String, Object> e : Json.obj(d, "types").entrySet()) if (e.getValue() instanceof Number n) c.downTypes.put(e.getKey(), n.floatValue());
            List<String> fr = Json.strList(d, "fatalRegions");
            if (!fr.isEmpty()) { c.fatalRegions.clear(); c.fatalRegions.addAll(fr); }
            Map<String, Object> r = Json.obj(root, "revive");
            c.reviveTime = (float) Json.num(r, "time", c.reviveTime); c.reviveRange = (float) Json.num(r, "range", c.reviveRange); c.reviveHealth = (float) Json.num(r, "health", c.reviveHealth);
            Map<String, Object> ai = Json.obj(r, "ai");
            c.aiUrgency = (float) Json.num(ai, "urgency", c.aiUrgency); c.aiDistance = (float) Json.num(ai, "distance", c.aiDistance); c.aiPressure = (float) Json.num(ai, "pressure", c.aiPressure);
            c.aiExposure = (float) Json.num(ai, "exposure", c.aiExposure); c.aiCover = (float) Json.num(ai, "cover", c.aiCover); c.aiOwnHealth = (float) Json.num(ai, "ownHealth", c.aiOwnHealth);
            c.aiRoleCost = (float) Json.num(ai, "roleCost", c.aiRoleCost); c.aiThreshold = (float) Json.num(ai, "threshold", c.aiThreshold); c.aiMaxDistance = (float) Json.num(ai, "maxDistance", c.aiMaxDistance);
            c.aiAbortSuppression = (float) Json.num(ai, "abortSuppression", c.aiAbortSuppression);
            Map<String, Object> hm = Json.obj(root, "hitmarker");
            c.markerLife = (float) Json.num(hm, "life", c.markerLife); c.markerLifeKill = (float) Json.num(hm, "lifeKill", c.markerLifeKill); c.markerSize = (float) Json.num(hm, "size", c.markerSize); c.markerGap = (float) Json.num(hm, "gap", c.markerGap);
            colours(hm, "colors", c.markerColors);
            Map<String, Object> kf = Json.obj(root, "killfeed");
            c.feedLife = (float) Json.num(kf, "life", c.feedLife); c.feedFade = (float) Json.num(kf, "fade", c.feedFade); c.feedSlide = (float) Json.num(kf, "slide", c.feedSlide); c.feedMax = Json.integer(kf, "max", c.feedMax);
            Map<String, Object> dl = Json.obj(root, "dialogue");
            c.talkBase = (float) Json.num(dl, "base", c.talkBase); c.talkPerChar = (float) Json.num(dl, "perChar", c.talkPerChar); c.talkMaxDistance = (float) Json.num(dl, "maxDistance", c.talkMaxDistance);
            c.talkFadeFrom = (float) Json.num(dl, "fadeFrom", c.talkFadeFrom); c.talkWrap = (float) Json.num(dl, "wrap", c.talkWrap); c.talkMaxVisible = Json.integer(dl, "maxVisible", c.talkMaxVisible);
            for (Map.Entry<String, Object> e : Json.obj(root, "decapitation").entrySet()) {
                if (!(e.getValue() instanceof Map)) continue;
                @SuppressWarnings("unchecked") Map<String, Object> dm = (Map<String, Object>) e.getValue();
                Decap dd = new Decap(Json.bool(dm, "never", false), (float) Json.num(dm, "minForce", 85), (float) Json.num(dm, "chanceFrom", 70), (float) Json.num(dm, "chanceSpan", 120), (float) Json.num(dm, "max", 0.9),
                        (float) Json.num(dm, "closeRange", 0), (float) Json.num(dm, "closeChance", 0));
                if (e.getKey().equals("default")) c.decapDefault = dd; else c.decap.put(e.getKey(), dd);
            }
            Map<String, Object> nt = Json.obj(root, "nametag");
            c.tagRange = (float) Json.num(nt, "range", c.tagRange); c.tagInfectedRange = (float) Json.num(nt, "infectedRange", c.tagInfectedRange); c.tagSquadRange = (float) Json.num(nt, "squadRange", c.tagSquadRange); c.tagSize = (float) Json.num(nt, "size", c.tagSize);
            Map<String, Object> pc = Json.obj(root, "perception");
            c.seeEdge = (float) Json.num(pc, "edgeOfVision", c.seeEdge); c.seeVertical = (float) Json.num(pc, "verticalDegrees", c.seeVertical); c.seeFog = (float) Json.num(pc, "throughFog", c.seeFog);
            c.hearStorey = (float) Json.num(pc, "throughAFloor", c.hearStorey); c.hearAlert = (float) Json.num(pc, "alertHearing", c.hearAlert);
            Map<String, Object> fd = Json.obj(root, "feeding");
            c.feedSeconds = (float) Json.num(fd, "seconds", c.feedSeconds); c.feedReach = (float) Json.num(fd, "reach", c.feedReach); c.feedSeek = (float) Json.num(fd, "seek", c.feedSeek);
            Map<String, Object> fg = Json.obj(root, "fog");
            c.fogStart = (float) Json.num(fg, "start", c.fogStart); c.fogEnd = (float) Json.num(fg, "end", c.fogEnd); c.fogHaze = (float) Json.num(fg, "haze", c.fogHaze); c.fogIndoor = (float) Json.num(fg, "indoor", c.fogIndoor); c.indoorDaylight = (float) Json.num(fg, "indoorDaylight", c.indoorDaylight);
            c.fogSky = (float) Json.num(fg, "sky", c.fogSky); c.fogNight = (float) Json.num(fg, "night", c.fogNight); c.fogRain = (float) Json.num(fg, "rain", c.fogRain);
            c.fogMist = (float) Json.num(fg, "mist", c.fogMist); c.fogMistWall = (float) Json.num(fg, "mistWall", c.fogMistWall);
            Map<String, Object> sg = Json.obj(root, "shotgun");
            c.shotgunDamage = (float) Json.num(sg, "damageMul", c.shotgunDamage); c.shotgunCloseRange = (float) Json.num(sg, "closeRange", c.shotgunCloseRange); c.shotgunCloseBonus = (float) Json.num(sg, "closeBonus", c.shotgunCloseBonus);
            Map<String, Object> cv = Json.obj(root, "cover");
            c.coverReach = (float) Json.num(cv, "reach", c.coverReach); c.coverMaxSpeed = (float) Json.num(cv, "maxSpeed", c.coverMaxSpeed); c.coverLean = (float) Json.num(cv, "lean", c.coverLean); c.coverAiLean = (float) Json.num(cv, "aiLean", c.coverAiLean);
        } catch (Exception e) {
            System.err.println("[Combat] content/combat.json could not be read: " + e + " - using built-in defaults");
        }
        return c;
    }
}
