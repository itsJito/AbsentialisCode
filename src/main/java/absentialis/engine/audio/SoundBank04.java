package absentialis.engine.audio;

import java.util.Map;
import java.util.Random;

/**
 * Sounds added in 0.4, synthesised at start-up like the rest of the bank: vehicles (engine, tyres, impacts,
 * doors), true fire (a crackling bed and its ignition), anatomical damage (bone, flesh, bodies falling), bites and
 * the infection, the death sequence, and short vocal barks so squad communication is heard and not only read.
 * Any of them - or any older sound - is replaced by a recording dropped into assets/audio (see {@link AssetSounds}).
 */
public final class SoundBank04 {
    private static final int R = UltraEngine.RATE;
    private final Random rng = new Random(40404);

    public void build(Map<String, float[]> out) {
        // ---- vehicles
        out.put("engine_loop", engine(0.5f, 62f, false));
        out.put("engine_diesel", engine(0.5f, 44f, true));
        out.put("engine_start", concat(starter(0.55f), swell(engine(0.6f, 62f, false), 0.25f)));
        out.put("engine_stop", fade(engine(0.5f, 50f, false), 0.02f, 0.45f));
        out.put("tyre_skid", loopable(skid(1.2f)));
        out.put("tyre_roll", loopable(roll(1.5f, 0.18f, 0.07f)));
        out.put("tyre_gravel", loopable(roll(1.5f, 0.5f, 0.16f)));
        out.put("tyre_burst", concat(env(noise(0.09f), 0.001f, 0.05f, 0.95f), lp(env(noise(0.5f), 0.01f, 0.3f, 0.5f), 0.06f)));
        out.put("car_crash", crash(1.1f, 1f));
        out.put("car_bump", crash(0.45f, 0.5f));
        out.put("car_scrape", loopable(lp(env(noise(0.8f), 0.05f, 0.8f, 0.45f), 0.35f)));
        out.put("car_door", concat(lp(click(0.03f, 180, 0.7f), 0.2f), silence(0.04f), lp(click(0.07f, 110, 0.9f), 0.12f)));
        out.put("car_door_open", concat(click(0.02f, 900, 0.4f), lp(env(noise(0.16f), 0.02f, 0.12f, 0.25f), 0.1f)));
        out.put("car_horn", horn(0.5f));
        out.put("suspension", lp(env(noise(0.16f), 0.005f, 0.12f, 0.5f), 0.05f));
        out.put("gear_shift", concat(lp(click(0.02f, 320, 0.4f), 0.2f), silence(0.03f), lp(click(0.03f, 200, 0.45f), 0.15f)));
        out.put("handbrake", concat(click(0.012f, 1500, 0.4f), click(0.012f, 1700, 0.4f), click(0.012f, 1900, 0.4f), click(0.02f, 2100, 0.5f)));
        // ---- true fire
        out.put("fire_loop", loopable(fire(3.0f)));
        out.put("fire_ignite", concat(lp(env(noise(0.5f), 0.02f, 0.4f, 0.8f), 0.12f), fade(fire(0.8f), 0.02f, 0.6f)));
        out.put("fire_burst", concat(env(noise(0.1f), 0.002f, 0.08f, 0.8f), lp(env(noise(0.9f), 0.03f, 0.8f, 0.7f), 0.08f)));
        // ---- anatomical damage
        out.put("bone_snap", concat(click(0.012f, 2600, 0.9f), click(0.02f, 1500, 0.6f), lp(env(noise(0.09f), 0.002f, 0.07f, 0.5f), 0.3f)));
        out.put("flesh_tear", concat(lp(env(noise(0.22f), 0.01f, 0.2f, 0.7f), 0.14f), squelch(0.25f)));
        out.put("gore", squelch(0.4f));
        out.put("squelch", squelch(0.22f));
        out.put("body_fall", concat(lp(env(noise(0.16f), 0.004f, 0.14f, 0.9f), 0.045f), silence(0.06f), lp(env(noise(0.12f), 0.004f, 0.1f, 0.45f), 0.06f)));
        out.put("gib_land", lp(env(noise(0.1f), 0.003f, 0.08f, 0.6f), 0.09f));
        out.put("blood_drip", click(0.02f, 620, 0.35f));
        out.put("slip", concat(lp(env(noise(0.2f), 0.01f, 0.18f, 0.5f), 0.2f), lp(env(noise(0.14f), 0.004f, 0.12f, 0.8f), 0.05f)));
        out.put("stagger", lp(env(noise(0.12f), 0.01f, 0.1f, 0.4f), 0.12f));
        // ---- bites and the infection
        out.put("bite", concat(growl(0.18f, 150, 1f), click(0.02f, 1900, 0.8f), squelch(0.2f)));
        out.put("infected_turn", concat(growl(0.9f, 70, 1.2f), growl(0.7f, 120, 1.4f)));
        out.put("infection_pulse", concat(thump(0.16f, 46, 0.9f), silence(0.12f), thump(0.2f, 40, 0.7f)));
        out.put("antiviral", concat(click(0.02f, 2800, 0.5f), sweep(0.25f, 1500, 700, 0.2f), tone(0.25f, 660, 0.14f)));
        out.put("cough", concat(lp(env(noise(0.09f), 0.004f, 0.08f, 0.8f), 0.2f), silence(0.05f), lp(env(noise(0.12f), 0.004f, 0.1f, 0.7f), 0.16f)));
        // ---- the death sequence
        out.put("death_sting", deathSting());
        out.put("death_drone", loopable(drone(6f)));
        out.put("ui_confirm", concat(tone(0.05f, 660, 0.25f), tone(0.11f, 990, 0.25f)));
        out.put("ui_back", concat(tone(0.05f, 520, 0.22f), tone(0.09f, 390, 0.22f)));
        out.put("ui_deploy", concat(thump(0.25f, 55, 0.9f), tone(0.3f, 220, 0.16f)));
        // ---- squad communication: short spoken-cadence barks, one per kind of callout
        out.put("bark_contact", bark(new float[]{190, 240, 210}, 0.11f, 1.0f));
        out.put("bark_lost", bark(new float[]{200, 170}, 0.15f, 0.8f));
        out.put("bark_flank", bark(new float[]{180, 220, 180, 230}, 0.09f, 0.9f));
        out.put("bark_cover", bark(new float[]{230, 230, 190}, 0.10f, 1.0f));
        out.put("bark_down", bark(new float[]{260, 180}, 0.17f, 1.1f));
        out.put("bark_body", bark(new float[]{170, 150, 190}, 0.14f, 0.7f));
        out.put("bark_search", bark(new float[]{180, 200}, 0.13f, 0.6f));
        out.put("bark_reload", bark(new float[]{210, 190, 210}, 0.09f, 0.8f));
        out.put("bark_help", bark(new float[]{280, 250, 290}, 0.12f, 1.2f));
        out.put("bark_move", bark(new float[]{200, 230}, 0.10f, 0.9f));
        out.put("bark_clear", bark(new float[]{180, 160}, 0.16f, 0.6f));
        out.put("bark_taunt", bark(new float[]{160, 190, 150, 170}, 0.12f, 0.9f));
        out.put("bark_pain", concat(growl(0.22f, 210, 0.6f), bark(new float[]{170}, 0.12f, 0.7f)));
        out.put("bullet_snap", concat(click(0.004f, 5200, 0.9f), lp(env(noise(0.05f), 0.001f, 0.04f, 0.4f), 0.5f)));
        out.put("melee_block", concat(click(0.02f, 1300, 0.8f), lp(env(noise(0.08f), 0.002f, 0.06f, 0.4f), 0.2f)));
        out.put("grab", concat(lp(env(noise(0.12f), 0.01f, 0.1f, 0.5f), 0.1f), growl(0.2f, 120, 0.8f)));
    }

    // ------------------------------------------------------------------ building blocks
    private static float[] buf(float sec) { return new float[Math.max(2, (int) (sec * R))]; }
    private static float[] silence(float sec) { return buf(sec); }

    private static float[] concat(float[]... parts) {
        int n = 0;
        for (float[] p : parts) n += p.length;
        float[] o = new float[n];
        int at = 0;
        for (float[] p : parts) { System.arraycopy(p, 0, o, at, p.length); at += p.length; }
        return o;
    }

    private float[] noise(float sec) { float[] b = buf(sec); for (int i = 0; i < b.length; i++) b[i] = rng.nextFloat() * 2 - 1; return b; }

    /** Attack / exponential decay envelope, scaled to vol. */
    private static float[] env(float[] b, float attack, float decay, float vol) {
        int a = Math.max(1, (int) (attack * R));
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) R;
            float e = i < a ? i / (float) a : (float) Math.exp(-(t - attack) / Math.max(1e-3f, decay) * 3);
            b[i] *= e * vol;
        }
        return b;
    }

    /** One-pole low-pass; a is the per-sample blend (smaller = darker). */
    private static float[] lp(float[] b, float a) { float s = 0; for (int i = 0; i < b.length; i++) { s += (b[i] - s) * a; b[i] = s; } return b; }
    private static float[] hp(float[] b, float a) { float s = 0; for (int i = 0; i < b.length; i++) { s += (b[i] - s) * a; b[i] -= s; } return b; }

    private static float[] click(float sec, float f, float vol) {
        float[] b = buf(sec);
        for (int i = 0; i < b.length; i++) { float t = i / (float) R; b[i] = (float) Math.sin(2 * Math.PI * f * t) * (float) Math.exp(-t / sec * 5) * vol; }
        return b;
    }

    private static float[] tone(float sec, float f, float vol) {
        float[] b = buf(sec);
        for (int i = 0; i < b.length; i++) { float t = i / (float) R, e = Math.min(1, Math.min(t / 0.008f, (sec - t) / 0.03f)); b[i] = (float) Math.sin(2 * Math.PI * f * t) * vol * Math.max(0, e); }
        return b;
    }

    private static float[] sweep(float sec, float f0, float f1, float vol) {
        float[] b = buf(sec);
        double ph = 0;
        for (int i = 0; i < b.length; i++) { float k = i / (float) b.length; ph += 2 * Math.PI * (f0 + (f1 - f0) * k) / R; b[i] = (float) Math.sin(ph) * vol * (1 - k); }
        return b;
    }

    private static float[] thump(float sec, float f, float vol) {
        float[] b = buf(sec);
        double ph = 0;
        for (int i = 0; i < b.length; i++) { float t = i / (float) R; ph += 2 * Math.PI * f * (1 + 1.5 * Math.exp(-t * 40)) / R; b[i] = (float) Math.sin(ph) * (float) Math.exp(-t / sec * 4) * vol; }
        return b;
    }

    private static float[] fade(float[] b, float in, float out) {
        int a = (int) (in * R), o = (int) (out * R);
        for (int i = 0; i < b.length; i++) { float k = 1; if (i < a) k = i / (float) a; if (i > b.length - o) k = Math.min(k, (b.length - i) / (float) o); b[i] *= k; }
        return b;
    }

    private static float[] swell(float[] b, float in) { int a = (int) (in * R); for (int i = 0; i < a && i < b.length; i++) b[i] *= i / (float) a; return fade(b, 0, 0.12f); }

    /** Crossfades the tail into the head so the sample loops without a seam. */
    private static float[] loopable(float[] b) {
        int x = Math.min(b.length / 4, R / 8);
        float[] o = new float[b.length - x];
        System.arraycopy(b, 0, o, 0, o.length);
        for (int i = 0; i < x; i++) { float k = i / (float) x; o[i] = b[i] * k + b[b.length - x + i] * (1 - k); }
        return o;
    }

    private static void normalize(float[] b, float peak) {
        float m = 1e-6f;
        for (float v : b) m = Math.max(m, Math.abs(v));
        for (int i = 0; i < b.length; i++) b[i] *= peak / m;
    }

    // ------------------------------------------------------------------ voices
    /**
     * A four-stroke engine as its firing pulses: a whole number of cycles so the loop is seamless, each pulse a
     * decaying burst with a little cycle-to-cycle unevenness. Pitch-shifting this loop is the engine speed.
     */
    private float[] engine(float sec, float fireHz, boolean diesel) {
        int cycles = Math.max(1, Math.round(fireHz * sec));
        float[] b = new float[Math.round(cycles * R / fireHz)];
        int per = b.length / cycles;
        for (int c = 0; c < cycles; c++) {
            float amp = 0.75f + rng.nextFloat() * 0.25f, tone = diesel ? 0.6f : 1f;
            for (int i = 0; i < per; i++) {
                float t = i / (float) per;
                float pulse = (float) Math.exp(-t * (diesel ? 5 : 7));
                float body = (float) (Math.sin(2 * Math.PI * t * 2) * 0.6 + Math.sin(2 * Math.PI * t * 3 * tone) * 0.3 + Math.sin(2 * Math.PI * t * 5) * 0.14);
                b[c * per + i] += (body * pulse + (rng.nextFloat() - 0.5f) * 0.22f * pulse * (diesel ? 1.6f : 1)) * amp;
            }
        }
        lp(b, diesel ? 0.22f : 0.3f);
        for (int i = 0; i < b.length; i++) b[i] += (rng.nextFloat() - 0.5f) * 0.012f;   // intake hiss
        normalize(b, 0.6f);
        return b;
    }

    private float[] starter(float sec) {
        float[] b = buf(sec);
        for (int i = 0; i < b.length; i++) { float t = i / (float) R; b[i] = (float) Math.sin(2 * Math.PI * 38 * t * (1 + t)) * (0.4f + 0.3f * (float) Math.sin(2 * Math.PI * 11 * t)) * 0.5f + (rng.nextFloat() - 0.5f) * 0.12f; }
        return fade(lp(b, 0.25f), 0.02f, 0.06f);
    }

    private float[] skid(float sec) {
        float[] b = noise(sec);
        // a screech is band-limited noise riding two wandering resonances
        float s1 = 0, s2 = 0, v1 = 0, v2 = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) R;
            float f1 = 0.19f + 0.03f * (float) Math.sin(t * 9), f2 = 0.27f + 0.03f * (float) Math.sin(t * 6 + 1);
            v1 += (b[i] - s1) * f1 - v1 * 0.035f; s1 += v1 * f1;
            v2 += (b[i] - s2) * f2 - v2 * 0.05f; s2 += v2 * f2;
            b[i] = (s1 * 0.6f + s2 * 0.4f) * (0.75f + 0.25f * (float) Math.sin(t * 31));
        }
        normalize(b, 0.55f);
        return b;
    }

    private float[] roll(float sec, float grit, float vol) {
        float[] b = lp(noise(sec), 0.04f + grit * 0.1f);
        for (int i = 0; i < b.length; i++) if (rng.nextFloat() < grit * 0.004f) { int n = Math.min(b.length - i, 260); for (int k = 0; k < n; k++) b[i + k] += (rng.nextFloat() - 0.5f) * (1 - k / (float) n) * 0.5f; }
        normalize(b, vol * 3);
        return b;
    }

    private float[] crash(float sec, float weight) {
        float[] metal = buf(sec);
        float[] fs = {210, 337, 523, 811, 1290, 1870};
        for (float f : fs) { float ph = rng.nextFloat() * 6.28f, d = 3 + rng.nextFloat() * 6; for (int i = 0; i < metal.length; i++) { float t = i / (float) R; metal[i] += (float) Math.sin(2 * Math.PI * f * t + ph) * (float) Math.exp(-t * d) * 0.14f; } }
        float[] bang = lp(env(noise(sec), 0.002f, sec * 0.35f, 1f), 0.16f);
        float[] glass = hp(env(noise(sec), 0.03f, sec * 0.5f, 0.3f * weight), 0.5f);
        float[] low = thump(Math.min(sec, 0.5f), 52, 1f);
        for (int i = 0; i < metal.length; i++) metal[i] = metal[i] + bang[i] * 0.8f + glass[i] + (i < low.length ? low[i] * weight : 0);
        normalize(metal, 0.9f);
        return metal;
    }

    private static float[] horn(float sec) {
        float[] b = buf(sec);
        for (int i = 0; i < b.length; i++) { float t = i / (float) R; float s = 0; for (float f : new float[]{392, 494}) for (int h = 1; h <= 5; h++) s += Math.sin(2 * Math.PI * f * h * t) / h; b[i] = s * 0.12f; }
        return fade(b, 0.01f, 0.05f);
    }

    /** Fire: a low roar, a wandering mid flutter, and irregular crackles and pops. */
    private float[] fire(float sec) {
        float[] roar = lp(noise(sec), 0.02f), flutter = lp(noise(sec), 0.12f), b = buf(sec);
        for (int i = 0; i < b.length; i++) { float t = i / (float) R; b[i] = roar[i] * 2.4f + flutter[i] * 0.35f * (0.6f + 0.4f * (float) Math.sin(t * 7.3) * (float) Math.sin(t * 2.1)); }
        int pops = (int) (sec * 26);
        for (int p = 0; p < pops; p++) {
            int at = rng.nextInt(b.length - 800), n = 40 + rng.nextInt(500);
            float a = 0.15f + rng.nextFloat() * rng.nextFloat() * 0.9f;
            for (int k = 0; k < n; k++) b[at + k] += (rng.nextFloat() - 0.5f) * a * (float) Math.exp(-k / (float) n * 6);
        }
        normalize(b, 0.55f);
        return b;
    }

    private float[] squelch(float sec) {
        float[] b = noise(sec);
        float s = 0;
        for (int i = 0; i < b.length; i++) { float t = i / (float) R; float a = 0.03f + 0.25f * Math.abs((float) Math.sin(t * 38 + Math.sin(t * 90))); s += (b[i] - s) * a; b[i] = s * (float) Math.exp(-t / sec * 2.2f) * 1.6f; }
        return fade(b, 0.004f, 0.03f);
    }

    private float[] growl(float sec, float f0, float rough) {
        float[] b = buf(sec);
        double ph = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) R, k = t / sec;
            float f = f0 * (1 + 0.25f * (float) Math.sin(t * 23) * rough) * (1 - k * 0.3f);
            ph += 2 * Math.PI * f / R;
            float saw = (float) ((ph / (2 * Math.PI)) % 1.0) * 2 - 1;
            b[i] = (saw * 0.5f + (rng.nextFloat() - 0.5f) * 0.4f * rough) * (float) Math.sin(Math.PI * Math.min(1, k * 1.05f)) * 0.55f;
        }
        return lp(b, 0.16f);
    }

    /** A spoken cadence without words: voiced syllables with formants, pitched by urgency. */
    private float[] bark(float[] pitch, float syl, float urgency) {
        float[][] parts = new float[pitch.length * 2][];
        for (int s = 0; s < pitch.length; s++) {
            float[] b = buf(syl);
            double ph = 0;
            float f1 = 500 + rng.nextFloat() * 400, f2 = 1200 + rng.nextFloat() * 900, s1 = 0, v1 = 0, s2 = 0, v2 = 0;
            for (int i = 0; i < b.length; i++) {
                float k = i / (float) b.length;
                ph += 2 * Math.PI * pitch[s] * (1 + (0.5f - k) * 0.12f) * (0.9f + urgency * 0.15f) / R;
                float src = (float) ((ph / (2 * Math.PI)) % 1.0) * 2 - 1 + (rng.nextFloat() - 0.5f) * 0.25f;
                float a1 = 2 * (float) Math.sin(Math.PI * f1 / R), a2 = 2 * (float) Math.sin(Math.PI * f2 / R);
                v1 += (src - s1) * a1 - v1 * 0.12f; s1 += v1 * a1;
                v2 += (src - s2) * a2 - v2 * 0.1f; s2 += v2 * a2;
                b[i] = (s1 * 0.7f + s2 * 0.4f) * (float) Math.sin(Math.PI * k) * 0.35f;
            }
            parts[s * 2] = b;
            parts[s * 2 + 1] = silence(0.025f + rng.nextFloat() * 0.03f);
        }
        float[] o = concat(parts);
        normalize(o, 0.6f);
        return o;
    }

    private float[] deathSting() {
        float[] b = buf(3.2f);
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) R;
            float e = (float) Math.exp(-t * 0.9f) * Math.min(1, t / 0.01f);
            b[i] = (float) (Math.sin(2 * Math.PI * 55 * t) * 0.5 + Math.sin(2 * Math.PI * 58.3 * t) * 0.4 + Math.sin(2 * Math.PI * 82.4 * t) * 0.25 + Math.sin(2 * Math.PI * 110 * (1 - t * 0.02) * t) * 0.12) * e * 0.6f;
        }
        float[] hit = lp(env(noise(0.6f), 0.002f, 0.4f, 0.8f), 0.05f);
        for (int i = 0; i < hit.length; i++) b[i] += hit[i];
        return fade(b, 0, 0.4f);
    }

    private float[] drone(float sec) {
        float[] b = buf(sec);
        float[] fs = {41.2f, 61.7f, 82.4f, 123.5f};
        for (float f0 : fs) { float f = Math.round(f0 * sec) / sec; for (int i = 0; i < b.length; i++) { float t = i / (float) R; b[i] += (float) Math.sin(2 * Math.PI * f * t) * (0.5f + 0.5f * (float) Math.sin(2 * Math.PI * t / sec * (1 + f0 % 3))) * 0.09f; } }
        float[] air = lp(noise(sec), 0.01f);
        for (int i = 0; i < b.length; i++) b[i] += air[i] * 0.6f;
        return b;
    }
}
