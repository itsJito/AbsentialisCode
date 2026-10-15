package absentialis.tools;

import absentialis.gameplay.items.Content;
import absentialis.engine.audio.UltraEngine;
import absentialis.engine.audio.gun.AcousticEnvironment;
import absentialis.engine.audio.gun.AcousticEnvironment.Acoustics;
import absentialis.engine.audio.gun.AudioMixer;
import absentialis.engine.audio.gun.GunAudio;
import absentialis.engine.audio.gun.ShotEvent;
import absentialis.engine.audio.gun.ShotEventScheduler;
import absentialis.engine.audio.gun.VoiceManager;
import absentialis.engine.audio.gun.WeaponAudioProfile;
import absentialis.gameplay.items.ItemDef;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Version 0.5 audio gates (brief 17 phase 8): shot timing, variation, overlap, decay, clipping, environment and
 * performance of the gun bus, and the recorded footstep sets. Everything is rendered headless through the same
 * mixer the game uses, so what is measured is what is heard.
 */
final class AudioTests05 {
    private AudioTests05() {}

    private static final int BLOCK = 1024;

    /** An open field: nothing to hit. */
    private static final AcousticEnvironment.Geometry OPEN = new AcousticEnvironment.Geometry() {
        public float ray(float ox, float oy, float oz, float dx, float dy, float dz, float max, float[] hit) { if (dy < -0.9f) { hit[0] = ox; hit[1] = oy - 1.6f; hit[2] = oz; hit[3] = 0.5f; return 1.6f; } return -1; }
        public float openness(float ax, float ay, float az, float bx, float by, float bz) { return 1; }
    };

    /** A closed concrete room, 8 x 8 x 3 m round the origin. */
    private static final AcousticEnvironment.Geometry ROOM = new AcousticEnvironment.Geometry() {
        public float ray(float ox, float oy, float oz, float dx, float dy, float dz, float max, float[] hit) {
            float t = Float.MAX_VALUE;
            if (dx > 1e-4f) t = Math.min(t, (4 - ox) / dx); else if (dx < -1e-4f) t = Math.min(t, (-4 - ox) / dx);
            if (dz > 1e-4f) t = Math.min(t, (4 - oz) / dz); else if (dz < -1e-4f) t = Math.min(t, (-4 - oz) / dz);
            if (dy > 1e-4f) t = Math.min(t, (3 - oy) / dy); else if (dy < -1e-4f) t = Math.min(t, (0 - oy) / dy);
            if (t > max) return -1;
            hit[0] = ox + dx * t; hit[1] = oy + dy * t; hit[2] = oz + dz * t; hit[3] = 0.06f;
            return t;
        }
        public float openness(float ax, float ay, float az, float bx, float by, float bz) { return 1; }
    };

    /** One wall at x = -20, open otherwise. */
    private static final AcousticEnvironment.Geometry WALL = new AcousticEnvironment.Geometry() {
        public float ray(float ox, float oy, float oz, float dx, float dy, float dz, float max, float[] hit) {
            if (dx > -1e-4f) return -1;
            float t = (-20 - ox) / dx;
            if (t > max) return -1;
            hit[0] = ox + dx * t; hit[1] = oy + dy * t; hit[2] = oz + dz * t; hit[3] = 0.06f;
            return t;
        }
        public float openness(float ax, float ay, float az, float bx, float by, float bz) { return 1; }
    };

    /** Everything between any two points is solid. */
    private static final AcousticEnvironment.Geometry BLOCKED = new AcousticEnvironment.Geometry() {
        public float ray(float ox, float oy, float oz, float dx, float dy, float dz, float max, float[] hit) { return -1; }
        public float openness(float ax, float ay, float az, float bx, float by, float bz) { return 0; }
    };

    private static void render(GunAudio g, float[] scratch, int blocks) { for (int i = 0; i < blocks; i++) { java.util.Arrays.fill(scratch, 0f); g.mixer.mix(scratch, BLOCK, 1f); } }

    /** Fires a burst the way the game does: the game ticks at 60 Hz and reports a round when its own timer allows. */
    private static long[] burst(GunAudio g, AcousticEnvironment.Geometry geo, String weapon, int shooter, float rpm, int rounds, float sx, float[] scratch, float fps) {
        long[] at = new long[rounds];
        double timer = 0, dt = 1.0 / fps, audioDebt = 0;
        int fired = 0;
        while (fired < rounds) {
            timer -= dt;
            if (timer <= 0) {
                ShotEvent e = g.shot(geo, weapon, shooter, rpm, sx, 1.6f, 0, 0, -1, false, shooter == 1, 0, 1.6f, 0, 0);
                if (e != null) at[fired] = e.sample();
                fired++;
                timer += 60.0 / rpm;
            }
            audioDebt += dt * AudioMixer.RATE;
            while (audioDebt >= BLOCK) { render(g, scratch, 1); audioDebt -= BLOCK; }
        }
        return at;
    }

    static void run(SelfTests04.Check c) {
        System.out.println("[v0.5 audio: layered gunfire, acoustics, footsteps]");
        Content.load();
        GunAudio g = new GunAudio();
        g.load(Path.of("assets", "audio"));
        float[] scratch = new float[BLOCK * 2];

        // ---- identity: every firearm has its own profile and its own recordings
        int firearms = 0, profiled = 0;
        Set<String> sets = new HashSet<>();
        StringBuilder missing = new StringBuilder();
        int minTakes = 99;
        for (ItemDef d : Content.ITEMS.values()) {
            if (d.weapon == null || d.weapon.melee() || "special".equals(d.weapon.family)) continue;
            firearms++;
            if (g.handles(d.id)) { profiled++; WeaponAudioProfile p = g.library.profiles.get(d.id); sets.add(p.setId); minTakes = Math.min(minTakes, p.set.takes()); }
            else missing.append(d.id).append(' ');
        }
        c.check("every firearm has an audio profile with recorded layers loaded", firearms > 0 && profiled == firearms, profiled + "/" + firearms + (missing.length() > 0 ? " missing: " + missing : ""));
        c.check("no two firearms share a recorded set (identity)", sets.size() == profiled, sets.size() + " sets for " + profiled + " weapons");
        c.check("every set has at least two takes of transient, body and tail", minTakes >= 2 && minTakes < 99, "fewest takes: " + minTakes);
        c.check("profile data loads without problems", g.library.problems.isEmpty(), String.join("; ", g.library.problems));
        if (profiled == 0) return;

        // ---- shot timing: rounds land on the sample grid of the rate of fire, whatever the frame rate does
        for (float[] t : new float[][]{{650, 60}, {900, 60}, {900, 37}, {700, 144}, {70, 60}}) {
            g.forget();
            long[] at = burst(g, OPEN, "weapon.akr", 1, t[0], 20, 0, scratch, t[1]);
            int step = g.scheduler.interval(t[0]);
            long worst = 0;
            for (int i = 1; i < at.length; i++) worst = Math.max(worst, Math.abs(at[i] - at[i - 1] - step));
            c.check("RPM " + (int) t[0] + " at " + (int) t[1] + " fps: 20 rounds exactly " + step + " samples apart", worst == 0, "worst deviation " + worst + " samples (" + String.format("%.3f", worst * 1000.0 / AudioMixer.RATE) + " ms)");
            render(g, scratch, 200);
        }
        g.forget();
        ShotEventScheduler s = new ShotEventScheduler(AudioMixer.RATE);
        long a0 = s.schedule(1, "w", 600, 0)[0], a1 = s.schedule(1, "w", 600, 4410)[0], a2 = s.schedule(1, "w", 600, 44100 * 3L)[0];
        c.check("a pause ends the burst: the next round is placed from now, not from the old grid", a1 - a0 == 4410 && a2 == 44100 * 3L + s.latency && s.started == 2 && s.continued == 1, a0 + " " + a1 + " " + a2);

        // ---- variation
        g.forget();
        int repeats = 0, aba = 0;
        float minR = 9, maxR = 0, minG = 9, maxG = 0;
        int p1 = -1, p2 = -1;
        WeaponAudioProfile ak = g.library.profiles.get("weapon.akr");
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            ShotEvent e = g.shot(OPEN, "weapon.akr", 1, 650, 0, 1.6f, 0, 0, -1, false, true, 0, 1.6f, 0, 0);
            render(g, scratch, 4);
            if (e == null) continue;
            if (e.take() == p1) repeats++;
            if (e.take() == p2 && ak.set.takes() > 2) aba++;
            p2 = p1; p1 = e.take();
            used.add(e.take());
            minR = Math.min(minR, e.rateVar()); maxR = Math.max(maxR, e.rateVar()); minG = Math.min(minG, e.gainVar()); maxG = Math.max(maxG, e.gainVar());
        }
        float gb = (float) Math.pow(10, ak.varDb / 20.0);
        c.check("no take is repeated back to back in 40 rounds (no machine-gun effect)", repeats == 0 && aba == 0 && used.size() == ak.set.takes(), "repeats " + repeats + ", A-B-A " + aba + ", takes used " + used.size() + "/" + ak.set.takes());
        c.check("per-shot rate and level vary, inside the profile's bounds", maxR - minR > ak.varRate * 0.5f && minR >= 1 - ak.varRate - 1e-4f && maxR <= 1 + ak.varRate + 1e-4f && minG >= 1 / gb - 1e-3f && maxG <= gb + 1e-3f && maxG - minG > 0.02f,
                String.format("rate %.4f..%.4f, level %.3f..%.3f", minR, maxR, minG, maxG));
        float[] v1 = ShotEventProbe.vary(12345, 0.02f, 1), v2 = ShotEventProbe.vary(12345, 0.02f, 1);
        c.check("the same seed gives the same shot", v1[0] == v2[0] && v1[1] == v2[1], "");
        render(g, scratch, 200);

        // ---- overlap and release: a 20-round burst; nothing is cut, and what is left after the last round decays
        g.forget(); g.mixer.resetStats();
        g.mixer.environment.listenerRoom(OPEN, 0, 1.6f, 0);
        g.mixer.capture = new float[AudioMixer.RATE * 2 * 8]; g.mixer.captured = 0;
        long capStart = g.mixer.clock();
        long[] at = burst(g, OPEN, "weapon.akr", 1, 650, 20, 0, scratch, 60);
        int during = g.mixer.voices.active();
        int stolenBurst = g.mixer.voices.stolen, refusedBurst = g.mixer.voices.refused;
        long lastShot = at[19];
        render(g, scratch, 4);
        int after = g.mixer.voices.active();
        render(g, scratch, 220);
        float[] cap = g.mixer.capture;
        int n = g.mixer.captured / 2;
        c.check("20-round burst: earlier shots keep sounding under later ones", during > 12 && g.mixer.voices.peakKind[VoiceManager.BODY] >= 10 && g.mixer.voices.peakKind[VoiceManager.TAIL] >= 10,
                during + " voices at the last round; peak bodies " + g.mixer.voices.peakKind[VoiceManager.BODY] + ", tails " + g.mixer.voices.peakKind[VoiceManager.TAIL]);
        c.check("20-round burst: no voice is stolen or refused", stolenBurst == 0 && refusedBurst == 0, "stolen " + stolenBurst + ", refused " + refusedBurst);
        // envelope in 20 ms windows from 60 ms after the last round
        int win = AudioMixer.RATE / 50, from = (int) (lastShot - capStart) + AudioMixer.RATE * 6 / 100;
        double prev = -1, worstDrop = 0, at300 = 0, first = 0;
        for (int w = 0; from + (w + 1) * win < n && w < 150; w++) {
            double e = 0;
            for (int i = from + w * win; i < from + (w + 1) * win; i++) e += cap[i * 2] * cap[i * 2] + cap[i * 2 + 1] * cap[i * 2 + 1];
            double db = 10 * Math.log10(e / (win * 2) + 1e-12);
            if (w == 0) first = db;
            if (w == 14) at300 = db;
            if (System.getenv("ABS_AUDIO_ENV") != null && w % 5 == 0) System.out.printf("    env %4d ms %.1f dB%n", 60 + w * 20, db);
            if (System.getenv("ABS_AUDIO_ENV") != null && w > 0 && Math.abs(prev - db) > 5) System.out.printf("    step at %d ms: %.1f -> %.1f dB%n", 60 + w * 20, prev, db);
            if (w > 0 && prev > -55 && prev - db > worstDrop) worstDrop = prev - db;
            prev = db;
        }
        c.check("release: the burst rings on after the trigger is let go", after > 6 && at300 > -60 && at300 < first, String.format("%d voices still sounding; level %.1f dB at release, %.1f dB 300 ms later", after, first, at300));
        c.check("release: the decay is smooth (no window falls more than 9 dB below the one before)", worstDrop < 9, String.format("largest 20 ms step down %.1f dB", worstDrop));
        boolean nan = false;
        float peak = 0;
        for (int i = 0; i < g.mixer.captured; i++) { float v = cap[i]; if (v != v) nan = true; peak = Math.max(peak, Math.abs(v)); }
        c.check("clipping: a 20-round burst stays inside full scale with no NaN", !nan && peak <= 1.0f && peak > 0.2f && g.mixer.hardOver == 0, String.format("peak %.3f, soft-knee samples %d, pre-knee peak %.3f", peak, g.mixer.softClipped, g.mixer.peakPreClip));
        // a single shot against the burst: sustained fire is louder, but not twenty times louder
        g.mixer.captured = 0; g.forget();
        g.shot(OPEN, "weapon.akr", 1, 650, 0, 1.6f, 0, 0, -1, false, true, 0, 1.6f, 0, 0);
        render(g, scratch, 130);
        float single = 0;
        for (int i = 0; i < g.mixer.captured; i++) single = Math.max(single, Math.abs(cap[i]));
        c.check("headroom: one round leaves room for a burst on top of it", single > 0.15f && single < 0.8f && peak < single * 2.6f, String.format("single-shot peak %.3f, burst peak %.3f", single, peak));
        g.mixer.capture = null;

        // ---- environment
        AcousticEnvironment env = g.mixer.environment;
        Acoustics w = env.analyse(WALL, 0, 1.6f, 0, 1, 0, 10, 1.6f, 0, 0, 9, 14, AudioMixer.RATE);
        float want = (20 + 30 - 10) / AcousticEnvironment.SPEED_OF_SOUND, best = 9;
        for (int i = 0; i < w.reflections; i++) best = Math.min(best, Math.abs(w.delay[i] - want));
        c.check("early reflection delay is (reflected - direct) / 343", w.reflections > 0 && best < 0.001f, String.format("%d reflections; wall at 20 m behind a shooter 10 m away: expected %.4f s, nearest %.4f s off", w.reflections, want, best));
        Acoustics in = env.analyse(ROOM, 0, 1.5f, 0, 1, 0, 2, 1.5f, 0, 0, 9, 14, AudioMixer.RATE), out = env.analyse(OPEN, 0, 1.6f, 0, 1, 0, 2, 1.6f, 0, 0, 9, 14, AudioMixer.RATE);
        c.check("enclosure: a room is indoors, a field is not", in.indoor > 0.7f && out.indoor < 0.15f && in.reflections >= 4 && out.reflections == 0, String.format("room %.2f with %d reflections, field %.2f with %d", in.indoor, in.reflections, out.indoor, out.reflections));
        env.listenerRoom(ROOM, 0, 1.5f, 0);
        float roomWet = env.roomWet, roomDecay = env.roomDecay;
        env.listenerRoom(OPEN, 0, 1.6f, 0);
        c.check("the shared reverb follows the space: wetter indoors than in the open", roomWet > env.roomWet * 2 && roomDecay > 0.3f && roomDecay < 2.5f, String.format("room wet %.2f decay %.2f s; open wet %.2f decay %.2f s", roomWet, roomDecay, env.roomWet, env.roomDecay));
        Acoustics clear = env.analyse(OPEN, 40, 1.6f, 0, -1, 0, 0, 1.6f, 0, 0, 9, 10, AudioMixer.RATE), blocked = env.analyse(BLOCKED, 40, 1.6f, 0, -1, 0, 0, 1.6f, 0, 0, 9, 10, AudioMixer.RATE);
        c.check("occlusion: a shot behind solid matter is quieter and duller", blocked.directGain < clear.directGain * 0.4f && blocked.directLowpass > clear.directLowpass + 0.05f, String.format("gain %.3f -> %.3f, low-pass %.3f -> %.3f", clear.directGain, blocked.directGain, clear.directLowpass, blocked.directLowpass));
        // distance: the far shot arrives late, and its balance moves to the distant layer
        g.forget(); g.mixer.resetStats();
        java.util.Arrays.fill(g.mixer.layers.startedKind, 0);
        long clock = g.mixer.clock();
        ShotEvent far = g.shot(OPEN, "weapon.mosin", 7, 42, 200, 1.6f, 0, -1, 0, false, false, 0, 1.6f, 0, 0);
        g.mixer.capture = new float[AudioMixer.RATE * 2 * 2]; g.mixer.captured = 0;
        render(g, scratch, 60);
        int onset = -1;
        for (int i = 0; i < g.mixer.captured / 2; i++) if (Math.abs(g.mixer.capture[i * 2]) + Math.abs(g.mixer.capture[i * 2 + 1]) > 1e-4f) { onset = i; break; }
        float travel = 200 / AcousticEnvironment.SPEED_OF_SOUND;
        c.check("distance: a shot 200 m away is heard " + String.format("%.2f", travel) + " s late, through its distant layer", far != null && onset > 0 && Math.abs(onset / (float) AudioMixer.RATE - (far.sample() - clock) / (float) AudioMixer.RATE - travel) < 0.01f && g.mixer.layers.startedKind[VoiceManager.DISTANT] == 1,
                far == null ? "no event" : String.format("onset %.3f s after the trigger; distant layers started %d", (onset - (far.sample() - clock)) / (float) AudioMixer.RATE, g.mixer.layers.startedKind[VoiceManager.DISTANT]));
        g.mixer.capture = null;
        render(g, scratch, 200);

        // ---- suppressed
        g.forget(); g.mixer.capture = new float[AudioMixer.RATE * 2]; g.mixer.captured = 0;
        g.shot(OPEN, "weapon.pm9", 1, 380, 0, 1.6f, 0, 0, -1, false, true, 0, 1.6f, 0, 0);
        render(g, scratch, 40);
        float loud = 0; for (int i = 0; i < g.mixer.captured; i++) loud = Math.max(loud, Math.abs(g.mixer.capture[i]));
        render(g, scratch, 200); g.mixer.captured = 0; g.forget();
        g.shot(OPEN, "weapon.pm9", 1, 380, 0, 1.6f, 0, 0, -1, true, true, 0, 1.6f, 0, 0);
        render(g, scratch, 40);
        float quiet = 0; for (int i = 0; i < g.mixer.captured; i++) quiet = Math.max(quiet, Math.abs(g.mixer.capture[i]));
        c.check("a suppressed round is much quieter than an unsuppressed one", quiet > 0.005f && quiet < loud * 0.5f, String.format("%.3f against %.3f", quiet, loud));
        g.mixer.capture = null;
        render(g, scratch, 200);

        // ---- performance: three automatic weapons, a shotgun and a rifle at once, in a room
        System.gc();                                   // the gates before this one leave whole worlds behind
        g.forget(); g.mixer.resetStats();
        env.listenerRoom(ROOM, 0, 1.5f, 0);
        double timer1 = 0, timer2 = 0, timer3 = 0, timer4 = 0, debt = 0;
        int dropped0 = g.mixer.eventsDropped;
        long[] blockNs = new long[400];
        int nb = 0;
        for (int frame = 0; frame < 60 * 6; frame++) {
            double dt = 1 / 60.0;
            timer1 -= dt; timer2 -= dt; timer3 -= dt; timer4 -= dt;
            if (timer1 <= 0) { g.shot(ROOM, "weapon.akr", 1, 650, 0, 1.5f, 0, 0, -1, false, true, 0, 1.5f, 0, 0); timer1 += 60 / 650.0; }
            if (timer2 <= 0) { g.shot(ROOM, "weapon.smg", 2, 900, 3, 1.5f, 2, -1, 0, false, false, 0, 1.5f, 0, 0); timer2 += 60 / 900.0; }
            if (timer3 <= 0) { g.shot(ROOM, "weapon.tommy", 3, 700, -3, 1.5f, -2, 1, 0, false, false, 0, 1.5f, 0, 0); timer3 += 60 / 700.0; }
            if (timer4 <= 0) { g.shot(ROOM, "weapon.striker", 4, 230, 2, 1.5f, -3, 0, 1, false, false, 0, 1.5f, 0, 0); timer4 += 60 / 230.0; }
            debt += dt * AudioMixer.RATE;
            while (debt >= BLOCK) { long b0 = System.nanoTime(); render(g, scratch, 1); if (nb < blockNs.length) blockNs[nb++] = System.nanoTime() - b0; debt -= BLOCK; }
        }
        java.util.Arrays.sort(blockNs, 0, nb);
        double medMs = blockNs[nb / 2] / 1e6, p95Ms = blockNs[Math.min(nb - 1, nb * 95 / 100)] / 1e6;
        double avgMs = g.mixer.totalNanos / 1e6 / Math.max(1, g.mixer.blocks), maxMs = g.mixer.maxBlockNanos / 1e6, blockMs = BLOCK * 1000.0 / AudioMixer.RATE;
        c.check("performance: four weapons firing for six seconds mix in a fraction of real time", medMs < blockMs * 0.08,
                // the median is the mixer's own cost. The tail (95th percentile, worst) is reported, not gated: on this desktop it moves
                // from 2 ms to 46 ms between runs with the machine's other load, and blocks with nothing to mix stall as well
                String.format("median %.3f ms, 95th percentile %.3f ms, mean %.3f ms, worst %.3f ms per %.1f ms block (%d of %d over 5 ms); peak %d voices, %d shots", medMs, p95Ms, avgMs, maxMs, blockMs, g.mixer.slowBlocks, g.mixer.blocks, g.mixer.voices.peakVoices, g.mixer.eventsRendered));
        c.check("performance: voices stay inside the pool and no event is dropped", g.mixer.voices.peakVoices <= VoiceManager.POOL && g.mixer.eventsDropped == dropped0 && g.mixer.hardOver == 0,
                "peak " + g.mixer.voices.peakVoices + "/" + VoiceManager.POOL + ", limited samples " + g.mixer.limited + ", over " + g.mixer.hardOver + ", stolen (faded) " + g.mixer.voices.stolen + ", refused " + g.mixer.voices.refused + ", dropped " + (g.mixer.eventsDropped - dropped0));
        // the audio thread allocates nothing while it mixes
        render(g, scratch, 20);
        long alloc = -1;
        try {
            com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
            long id = Thread.currentThread().threadId(), b0 = mx.getThreadAllocatedBytes(id);
            for (int i = 0; i < 400; i++) g.mixer.mix(scratch, BLOCK, 1f);
            alloc = mx.getThreadAllocatedBytes(id) - b0;
        } catch (Throwable t) { alloc = -1; }
        c.check("the mix loop allocates nothing (400 blocks)", alloc >= 0 && alloc < 4096, alloc + " bytes");

        // ---- the engine carries the gun bus, the recorded footsteps and the handling sounds
        UltraEngine e = new UltraEngine();
        e.buildBank();
        StringBuilder steps = new StringBuilder();
        boolean stepsOk = true, noRepeat = true;
        for (String surf : new String[]{"concrete", "asphalt", "wood", "metal", "dirt", "grass", "gravel"}) {
            int k = e.stepVariations(surf);
            steps.append(surf).append(' ').append(k).append("  ");
            if (k < 5) stepsOk = false;
            String lastName = "";
            for (int i = 0; i < 30; i++) { String nm = e.step(surf); if (nm.equals(lastName) || !e.samples.containsKey(nm)) noRepeat = false; lastName = nm; }
        }
        c.check("footsteps: concrete, asphalt, wood, metal, dirt, grass and gravel each have five or more recorded steps", stepsOk, steps.toString().trim());
        c.check("footsteps: the same step never plays twice running", noRepeat, "");
        float[] cA = e.samples.get("step_concrete_1"), aA = e.samples.get("step_asphalt_1"), gA = e.samples.get("step_gravel_1");
        c.check("footsteps: surfaces are different recordings", cA != null && aA != null && gA != null && cA != aA && differs(cA, gA) && differs(cA, aA), "");
        boolean cues = true;
        for (String fam : new String[]{"rifle", "pistol", "shotgun"}) for (String cue : new String[]{"mag_out", "mag_in", "bolt_back", "bolt_release", "shell_in"}) { String h = e.guns.cue(fam, cue); if (h == null || !e.samples.containsKey("h_" + h)) cues = false; }
        c.check("reload cues map to recorded handling sounds for rifles, pistols and shotguns", cues, e.guns.library.handling.size() + " handling samples");
        float[] mix = new float[2048];
        e.guns.shot(null, "weapon.sr16", 1, 240, 0, 0, 0, 0, -1, false, true, 0, 0, 0, 0);
        float pk = 0;
        for (int i = 0; i < 8; i++) { e.mixBlock(mix); for (float v : mix) pk = Math.max(pk, Math.abs(v)); }
        c.check("the engine's mix carries the gun bus (and reports the fallback room without a world)", pk > 0.05f && e.guns.last != null && e.guns.last.acoustics().fallback, String.format("peak %.3f", pk));
        e.bus[UltraEngine.SFX] = 0;
        e.guns.shot(null, "weapon.sr16", 1, 240, 0, 0, 0, 0, -1, false, true, 0, 0, 0, 0);
        float[] m2 = new float[2048];
        for (int i = 0; i < 400; i++) e.mixBlock(m2);
        e.guns.shot(null, "weapon.sr16", 1, 240, 0, 0, 0, 0, -1, false, true, 0, 0, 0, 0);
        float pk0 = 0;
        for (int i = 0; i < 8; i++) { e.mixBlock(m2); for (float v : m2) pk0 = Math.max(pk0, Math.abs(v)); }
        c.check("gunfire obeys the effects bus level", pk0 == 0, String.format("peak %.4f with the bus at zero", pk0));
    }

    private static boolean differs(float[] a, float[] b) {
        if (a.length != b.length) return true;
        double d = 0;
        for (int i = 0; i < a.length; i++) d += Math.abs(a[i] - b[i]);
        return d / a.length > 1e-3;
    }

    /** Reaches the package-private variation function through a profile-free event. */
    private static final class ShotEventProbe {
        static float[] vary(long seed, float rate, float db) { return GunAudioProbe.vary(seed, rate, db); }
    }
    private static final class GunAudioProbe {
        static float[] vary(long seed, float rate, float db) { return ShotEvent.variation(seed, rate, db); }
    }
}
