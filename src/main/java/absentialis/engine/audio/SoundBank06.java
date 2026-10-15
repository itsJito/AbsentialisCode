package absentialis.engine.audio;

import java.util.Map;
import java.util.Random;

/**
 * Version 0.6 sounds. Built after the 0.3 and 0.4 banks, so a name that already exists is replaced here:
 *
 *   amb_machines  0.5 synthesised the industrial bed as a 60 Hz and 120 Hz sine with gated bursts of white noise.
 *                 That is a mains hum and static, not a place - the "droning and static near the power plant" of the
 *                 0.6 report. The bed is now air moving through big empty structures with a far-off knock of metal
 *                 now and then: nothing steady, nothing tonal.
 *   amb_city      the same filtered air without the 50 Hz sine that sat under it.
 *
 * New: the crossbow's own report, glass coming to rest, the slide, a vehicle door, the experience tick.
 */
public final class SoundBank06 {
    private static final int R = UltraEngine.RATE;
    private final Random rng = new Random(606);

    public void build(Map<String, float[]> out) {
        out.put("amb_machines", industry());
        out.put("amb_city", cityAir());
        out.put("crossbow_shot", crossbow());
        out.put("glass_tinkle", tinkle());
        out.put("slide", slide());
        out.put("xp_tick", tick(1320, 0.07f));
        out.put("xp_level", concat(tick(880, 0.09f), tick(1175, 0.09f), tick(1760, 0.22f)));
        out.put("jump_grunt", env(lowpass(noise(0.12f), 0.08f), 0.01f, 0.1f, 0.35f));
        out.put("vehicle_door_open", concat(env(lowpass(noise(0.03f), 0.5f), 0.001f, 0.025f, 0.45f), env(lowpass(noise(0.22f), 0.05f), 0.04f, 0.18f, 0.22f)));
        out.put("vehicle_door_shut", concat(env(lowpass(noise(0.05f), 0.09f), 0.001f, 0.045f, 0.9f), env(lowpass(noise(0.12f), 0.03f), 0.002f, 0.1f, 0.5f)));
        out.put("trade", concat(tick(990, 0.05f), tick(1485, 0.11f)));
    }

    // ------------------------------------------------------------------ helpers
    private static float[] buf(float sec) { return new float[Math.max(1, (int) (sec * R))]; }

    private float[] noise(float sec) { float[] b = buf(sec); for (int i = 0; i < b.length; i++) b[i] = rng.nextFloat() * 2 - 1; return b; }

    private static float[] lowpass(float[] b, float k) { float lp = 0; for (int i = 0; i < b.length; i++) { lp += k * (b[i] - lp); b[i] = lp; } return b; }

    /** Attack and decay envelope with a peak level. */
    private static float[] env(float[] b, float attack, float decay, float peak) {
        float max = 1e-6f;
        for (float v : b) max = Math.max(max, Math.abs(v));
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) R;
            float e = t < attack ? t / attack : (float) Math.exp(-(t - attack) / Math.max(1e-3f, decay) * 4);
            b[i] = b[i] / max * e * peak;
        }
        return b;
    }

    private static float[] concat(float[]... parts) {
        int n = 0;
        for (float[] p : parts) n += p.length;
        float[] o = new float[n];
        int at = 0;
        for (float[] p : parts) { System.arraycopy(p, 0, o, at, p.length); at += p.length; }
        return o;
    }

    private static float[] tick(float hz, float sec) {
        float[] b = buf(sec);
        for (int i = 0; i < b.length; i++) { float t = i / (float) R; b[i] = (float) (Math.sin(2 * Math.PI * hz * t) * Math.exp(-t / sec * 5) * 0.28); }
        return b;
    }

    private static void normalize(float[] b, float peak) {
        float m = 1e-6f;
        for (float v : b) m = Math.max(m, Math.abs(v));
        for (int i = 0; i < b.length; i++) b[i] *= peak / m;
    }

    /** Folds the last half second into the first and drops it, so the loop repeats without a seam or a dip. */
    private static float[] seamless(float[] b) {
        int n = Math.min(b.length / 4, R / 2);
        for (int i = 0; i < n; i++) { float k = i / (float) n; b[i] = b[i] * k + b[b.length - n + i] * (1 - k); }
        return java.util.Arrays.copyOf(b, b.length - n);
    }

    // ------------------------------------------------------------------ beds
    /**
     * Air in a large industrial space: two bands of filtered noise that swell and fall on slow, unrelated cycles
     * (wind through steelwork has no pitch), and a few distant knocks of loose sheet - each a short, dull, inharmonic
     * ring with a soft onset, well under the level of the air. No sine runs through it and nothing is gated.
     */
    private float[] industry() {
        float[] b = buf(12f);
        float lo = 0, mid = 0, mid2 = 0;
        for (int i = 0; i < b.length; i++) {
            double t = i / (double) b.length * Math.PI * 2;
            float gustA = 0.55f + 0.45f * (float) (Math.sin(t * 2 + 0.6) * Math.sin(t * 5 + 1.9));
            float gustB = 0.5f + 0.5f * (float) Math.sin(t * 3 + 2.4);
            float w = rng.nextFloat() * 2 - 1;
            lo += 0.006f * (w - lo);
            mid += 0.05f * (w - mid);
            mid2 += 0.05f * (mid - mid2);
            b[i] = lo * 5.5f * (0.6f + 0.4f * gustB) + (mid2 - lo) * 0.55f * gustA;
        }
        normalize(b, 0.34f);
        // distant metal: {start s, base Hz, level}
        float[][] knocks = {{1.7f, 212, 0.045f}, {5.3f, 163, 0.05f}, {5.62f, 171, 0.03f}, {9.4f, 248, 0.04f}};
        float[] ratio = {1f, 1.72f, 2.61f, 3.93f};
        for (float[] k : knocks) {
            int at = (int) (k[0] * R), len = (int) (0.9f * R);
            float lp = 0;
            for (int i = 0; i < len && at + i < b.length; i++) {
                float t = i / (float) R, v = 0;
                for (int p = 0; p < ratio.length; p++) v += (float) (Math.sin(2 * Math.PI * k[1] * ratio[p] * t + p) * Math.exp(-t * (5 + p * 4))) / (p + 1);
                float onset = Math.min(1, t / 0.018f);
                lp += 0.12f * (v - lp);                                   // far away: the top is gone
                b[at + i] += lp * onset * k[2];
            }
        }
        return seamless(b);
    }

    private float[] cityAir() {
        float[] b = buf(8f);
        float lp = 0, lp2 = 0;
        for (int i = 0; i < b.length; i++) {
            double t = i / (double) b.length * Math.PI * 2;
            float swell = 0.7f + 0.3f * (float) Math.sin(t * 3 + 0.8);
            lp += 0.004f * ((rng.nextFloat() * 2 - 1) - lp);
            lp2 += 0.03f * ((rng.nextFloat() * 2 - 1) - lp2);
            b[i] = lp * 3 * swell + lp2 * 0.05f;
        }
        normalize(b, 0.35f);
        return seamless(b);
    }

    // ------------------------------------------------------------------ one-shots
    /** A crossbow: the string leaving the catch, the limbs stopping, the stock answering. Quiet to the world, plain to the one holding it. */
    private float[] crossbow() {
        float[] b = buf(0.42f);
        float lp = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) R;
            float snap = (float) (Math.sin(2 * Math.PI * (430 - 900 * t) * t) * Math.exp(-t * 38)) * 0.7f;          // string
            float thunk = (float) (Math.sin(2 * Math.PI * 96 * t) * Math.exp(-t * 22)) * 0.9f;                       // limbs against the stops
            float wood = (float) (Math.sin(2 * Math.PI * 610 * t + 1.1) * Math.exp(-t * 60)) * 0.3f;
            lp += 0.4f * ((rng.nextFloat() * 2 - 1) - lp);
            float air = lp * (float) Math.exp(-t * 55) * 0.5f;
            b[i] = (snap + thunk + wood + air) * Math.min(1, t / 0.002f);
        }
        normalize(b, 0.9f);
        return b;
    }

    /** Shards coming to rest: a handful of small, bright, short rings, no two the same. */
    private float[] tinkle() {
        float[] b = buf(0.5f);
        for (int k = 0; k < 7; k++) {
            int at = (int) (rng.nextFloat() * 0.22f * R);
            float hz = 2600 + rng.nextFloat() * 4200, dec = 40 + rng.nextFloat() * 50, lvl = 0.5f + rng.nextFloat() * 0.5f;
            for (int i = 0; at + i < b.length; i++) { float t = i / (float) R; b[at + i] += (float) (Math.sin(2 * Math.PI * hz * t) * Math.exp(-t * dec)) * lvl; }
        }
        normalize(b, 0.5f);
        return b;
    }

    /** Sliding on a hard floor: cloth and kit dragging, a rising then falling rush. */
    private float[] slide() {
        float[] b = lowpass(noise(0.8f), 0.25f);
        for (int i = 0; i < b.length; i++) { float t = i / (float) b.length; b[i] *= (float) Math.sin(Math.min(1, t * 6) * Math.PI / 2) * (1 - t) * (1 - t); }
        normalize(b, 0.6f);
        return b;
    }
}
