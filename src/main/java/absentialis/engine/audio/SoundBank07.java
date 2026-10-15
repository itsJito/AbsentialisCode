package absentialis.engine.audio;

import java.util.Map;
import java.util.Random;

/**
 * Version 0.7 sounds that are made here rather than recorded: the things 0.7 added that had no sound at all. The
 * infected and people's voices are recordings now (assets/audio, see ATTRIBUTION.md); these are the mechanical
 * ones for which no freely licensed recording was found - the Butcher's saw, the Gladiator's claws, a hammer on a
 * plank, brass and shot-shell hulls on the ground, an aerosol. Built after the earlier banks; a recording of the
 * same name in assets/audio replaces any of them.
 */
public final class SoundBank07 {
    private static final int R = UltraEngine.RATE;
    private final Random rng = new Random(707);

    public void build(Map<String, float[]> out) {
        out.put("chainsaw", saw(0.85f, 92, 96, 0.7f));
        out.put("chainsaw_rev", saw(0.8f, 96, 215, 0.95f));
        out.put("chainsaw_stall", saw(0.7f, 150, 38, 0.8f));
        out.put("claw_scrape", scrape());
        out.put("claw_swing", swing());
        out.put("hammer", hammer());
        out.put("casing_drop", ting(new float[]{4200, 6100, 7800}, 0.11f, 2));
        out.put("shell_drop", ting(new float[]{900, 1700}, 0.09f, 2));
        out.put("spray_hiss", hiss());
    }

    private static float[] buf(float sec) { return new float[Math.max(1, (int) (sec * R))]; }

    /** A two-stroke engine: a ragged pulse train at the firing rate, its rasp, and the chain's whine an octave and a bit up. */
    private float[] saw(float sec, float hz0, float hz1, float gain) {
        float[] b = buf(sec);
        double ph = 0, ph2 = 0;
        float lp = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) b.length, hz = hz0 + (hz1 - hz0) * t * t + (rng.nextFloat() - 0.5f) * 6;
            ph += hz / R; ph2 += hz * 2.37 / R;
            float pulse = (float) (ph % 1.0) < 0.28f ? 1 : -0.35f;
            float rasp = (rng.nextFloat() * 2 - 1) * (pulse > 0 ? 0.9f : 0.25f);
            lp += 0.35f * (pulse * 0.6f + rasp * 0.5f - lp);
            float whine = (float) Math.sin(ph2 * 2 * Math.PI) * 0.16f * Math.min(1, hz / 120f);
            float edge = Math.min(1, Math.min(t * 30, (1 - t) * 14));
            b[i] = (lp + whine) * gain * edge;
        }
        return b;
    }

    /** Steel drawn across stone: a narrow, wavering band of noise with a ring under it. */
    private float[] scrape() {
        float[] b = buf(0.5f);
        float bp = 0, lp = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) b.length, n = rng.nextFloat() * 2 - 1;
            lp += 0.5f * (n - lp); bp = n - lp;
            float ring = (float) Math.sin(i * 2 * Math.PI * (2350 + 300 * Math.sin(t * 40)) / R) * 0.25f;
            b[i] = (bp * 0.6f + ring) * (float) Math.sin(t * Math.PI) * (0.6f + 0.4f * (float) Math.sin(t * 90));
        }
        return b;
    }

    /** Something long and heavy through the air: noise swept from low to high and back, quickly. */
    private float[] swing() {
        float[] b = buf(0.3f);
        float lp = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) b.length, k = 0.04f + 0.5f * (float) Math.sin(t * Math.PI);
            lp += k * ((rng.nextFloat() * 2 - 1) - lp);
            b[i] = lp * (float) Math.pow(Math.sin(t * Math.PI), 1.5) * 1.6f;
        }
        return b;
    }

    /** Three blows on a nail through a board: a knock with the plank's hollow note behind it. */
    private float[] hammer() {
        float[] b = buf(0.75f);
        for (int k = 0; k < 3; k++) {
            int at = (int) (k * 0.22f * R);
            float lp = 0;
            for (int i = 0; i < R * 0.16f && at + i < b.length; i++) {
                float t = i / (float) R, e = (float) Math.exp(-t * 34);
                lp += 0.22f * ((rng.nextFloat() * 2 - 1) - lp);
                b[at + i] += (lp * 1.2f * (float) Math.exp(-t * 90) + (float) Math.sin(t * 2 * Math.PI * (185 + k * 9)) * 0.7f * e + (float) Math.sin(t * 2 * Math.PI * 1320) * 0.12f * (float) Math.exp(-t * 120)) * (0.8f + k * 0.1f);
            }
        }
        return b;
    }

    /** Something small and hard landing, bouncing once and lying still: a few partials that die in a tenth of a second. */
    private float[] ting(float[] partials, float sec, int bounces) {
        float[] b = buf(sec * (bounces + 0.6f));
        for (int k = 0; k < bounces; k++) {
            int at = (int) (k * sec * 0.9f * R);
            float amp = k == 0 ? 1 : 0.45f;
            for (int i = 0; at + i < b.length && i < sec * R; i++) {
                float t = i / (float) R, v = 0;
                for (int p = 0; p < partials.length; p++) v += (float) Math.sin(t * 2 * Math.PI * partials[p] * (1 + k * 0.03f)) * (float) Math.exp(-t * (60 + p * 25)) / (p + 1);
                b[at + i] += v * amp * 0.7f;
            }
        }
        return b;
    }

    /** An aerosol held down for half a second. */
    private float[] hiss() {
        float[] b = buf(0.55f);
        float lp = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) b.length, n = rng.nextFloat() * 2 - 1;
            lp += 0.6f * (n - lp);
            b[i] = (n - lp) * 0.5f * Math.min(1, Math.min(t * 40, (1 - t) * 12));
        }
        return b;
    }
}
