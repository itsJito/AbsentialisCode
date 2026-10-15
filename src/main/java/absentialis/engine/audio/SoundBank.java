package absentialis.engine.audio;

import absentialis.engine.util.Rng;
import java.util.Map;

/**
 * Procedurally synthesised sound effects: distinct firing signatures per weapon family, material
 * footsteps and impacts, infected vocal telegraphs, explosions, UI and ambience beds.
 */
public final class SoundBank {
    private static final int R = UltraEngine.RATE;
    private final Rng rng = new Rng(4242);

    public void build(Map<String, float[]> out) {
        out.put("shot_pistol", gun(0.38f, 140, 0.035f, 0.55f, 0.9f, 0.10f));
        out.put("shot_revolver", gun(0.5f, 110, 0.05f, 0.5f, 1.0f, 0.14f));
        out.put("shot_rifle", gun(0.55f, 90, 0.05f, 0.7f, 1.0f, 0.16f));
        out.put("shot_rifle2", gun(0.5f, 105, 0.045f, 0.8f, 0.95f, 0.15f));
        out.put("shot_sniper", gun(0.9f, 70, 0.08f, 0.6f, 1.0f, 0.28f));
        out.put("shot_shotgun", gun(0.75f, 65, 0.09f, 0.35f, 1.0f, 0.22f));
        out.put("shot_lmg", gun(0.5f, 80, 0.06f, 0.65f, 1.0f, 0.18f));
        out.put("shot_suppressed", suppressed());
        out.put("shot_distant", distant(0.9f));
        out.put("burst_distant", distantBurst());
        out.put("dry_fire", click(0.03f, 3000, 0.4f));
        out.put("jam", concat(click(0.02f, 2500, 0.5f), silence(0.05f), click(0.04f, 900, 0.6f)));
        out.put("mag_out", concat(click(0.02f, 1800, 0.5f), slide(0.08f, 600, 300, 0.25f)));
        out.put("mag_in", concat(slide(0.05f, 400, 700, 0.25f), click(0.03f, 2200, 0.7f)));
        out.put("bolt", concat(click(0.03f, 1500, 0.6f), slide(0.1f, 800, 1200, 0.3f), click(0.03f, 2500, 0.7f)));
        out.put("shell_in", concat(click(0.02f, 1200, 0.5f), slide(0.06f, 500, 300, 0.2f)));
        out.put("pump", concat(slide(0.08f, 300, 500, 0.5f), slide(0.08f, 500, 300, 0.5f), click(0.03f, 1800, 0.6f)));
        for (String m : new String[]{"concrete", "metal", "wood", "grass", "dirt", "water", "glass", "fabric", "flesh"}) {
            out.put("step_" + m, step(m));
            out.put("impact_" + m, impact(m));
        }
        out.put("ricochet", ricochet());
        out.put("glass_break", glassBreak());
        out.put("explosion", explosion(1.6f));
        out.put("explosion_far", lowpass(explosion(2.2f), 0.04f));
        out.put("bounce", click(0.05f, 700, 0.6f));
        out.put("pin", concat(click(0.02f, 3500, 0.5f), slide(0.05f, 2000, 3000, 0.2f)));
        out.put("door_open", door(true));
        out.put("door_close", door(false));
        // 0.5 door entities: a latch that holds, a boot against the leaf, a leaf coming off its frame
        out.put("door_locked", concat(click(0.02f, 1400, 0.5f), silence(0.05f), click(0.025f, 1000, 0.45f)));
        out.put("door_kick", lowpassRaw(concat(click(0.05f, 140, 1f), noise(0.14f, 0.01f, 0.6f)), 0.3f));
        { float[] b = concat(click(0.05f, 180, 1f), noise(0.32f, 0.02f, 0.8f), click(0.04f, 420, 0.5f), noise(0.18f, 0.02f, 0.35f)); reverb(b, 0.04f, 0.3f, 2); out.put("door_break", lowpassRaw(b, 0.45f)); }
        out.put("search", rustle(0.6f));
        out.put("zipper", zipper());
        out.put("ui_click", click(0.025f, 2200, 0.35f));
        out.put("ui_error", tone(0.18f, 180, 0.3f, true));
        out.put("ui_buy", concat(tone(0.06f, 880, 0.25f, false), tone(0.09f, 1320, 0.25f, false)));
        out.put("pickup", rustle(0.18f));
        out.put("groan", groan(0.9f, 85, 0.6f));
        out.put("groan2", groan(1.2f, 70, 0.5f));
        out.put("growl", groan(1.0f, 55, 0.9f));
        out.put("shriek", shriek(0.6f, 900));
        out.put("screech", screech());
        out.put("gurgle", gurgle());
        out.put("burst", concat(explosion(0.7f), rustle(0.3f)));
        out.put("roar", roar());
        out.put("stomp", lowpass(impact("concrete"), 0.05f));
        out.put("radio", radio());
        out.put("shout", shout());
        out.put("whistle", tone(0.4f, 1600, 0.3f, false));
        out.put("heartbeat", heartbeat());
        out.put("breath", lowpass(noise(0.9f, 0.25f, 0.3f), 0.08f));
        out.put("geiger", click(0.006f, 4000, 0.8f));
        out.put("hurt", hurt());
        out.put("death", concat(hurt(), lowpass(noise(0.5f, 0.2f, 0.4f), 0.05f)));
        out.put("melee_swing", slide(0.15f, 300, 120, 0.3f));
        out.put("melee_hit", impact("flesh"));
        out.put("eat", rustle(0.5f));
        out.put("drink", gurgle());
        out.put("bandage", rustle(0.8f));
        out.put("injector", concat(click(0.02f, 2800, 0.5f), slide(0.2f, 1200, 800, 0.15f)));
        out.put("siren", siren());
        out.put("flare", flareHiss());
        out.put("heli", heli());
        out.put("train_horn", horn(220, 277, 2.2f));
        out.put("boat_horn", horn(110, 138, 2.5f));
        out.put("countdown", tone(0.12f, 1000, 0.35f, false));
        out.put("amb_wind", wind());
        out.put("amb_rain", rain());
        out.put("amb_city", cityDrone());
        out.put("amb_machines", machines());
        out.put("amb_drone", eerie());
        out.put("amb_bells", bells());
        out.put("amb_interior", lowpass(noise(6f, 0.05f, 0.06f), 0.02f));
        out.put("thunder", lowpass(explosion(3.5f), 0.02f));
        out.put("beep", tone(0.08f, 1500, 0.3f, false));
        // ---- weapon foley: tactical and empty reloads are built from different pieces
        out.put("mag_drop", concat(click(0.015f, 900, 0.35f), silence(0.09f), lowpass(click(0.05f, 420, 0.5f), 0.25f)));
        out.put("pouch", concat(rustle(0.16f), click(0.02f, 1400, 0.25f)));
        out.put("bolt_back", concat(click(0.02f, 1300, 0.55f), slide(0.09f, 700, 1100, 0.3f)));
        out.put("bolt_release", concat(slide(0.04f, 1200, 800, 0.3f), click(0.035f, 2600, 0.8f)));
        out.put("equip_rifle", concat(rustle(0.14f), click(0.02f, 900, 0.3f), slide(0.07f, 500, 900, 0.22f), click(0.02f, 2000, 0.4f)));
        out.put("equip_pistol", concat(lowpass(rustle(0.10f), 0.2f), slide(0.05f, 700, 1200, 0.2f), click(0.02f, 2400, 0.35f)));
        out.put("equip_melee", concat(slide(0.12f, 1800, 3200, 0.16f), click(0.015f, 3800, 0.25f)));
        out.put("gear_rattle", concat(click(0.012f, 1900, 0.3f), silence(0.03f), click(0.012f, 1500, 0.22f), rustle(0.1f)));
        out.put("jump", lowpass(rustle(0.14f), 0.2f));
        // ---- interface feedback
        out.put("ui_hover", click(0.012f, 3400, 0.22f));
        out.put("ui_invalid", concat(tone(0.06f, 240, 0.3f, true), silence(0.03f), tone(0.09f, 190, 0.3f, true)));
        out.put("ui_drop", concat(click(0.02f, 700, 0.4f), lowpass(click(0.03f, 300, 0.4f), 0.2f)));
        out.put("ui_rotate", slide(0.05f, 900, 1500, 0.2f));
        out.put("ui_tab", concat(click(0.018f, 1800, 0.3f), click(0.018f, 2400, 0.22f)));
        out.put("extract_tick", concat(tone(0.05f, 880, 0.3f, false), silence(0.02f), tone(0.03f, 1320, 0.18f, false)));
        out.put("extract_done", concat(tone(0.09f, 660, 0.3f, false), tone(0.09f, 880, 0.3f, false), tone(0.22f, 1320, 0.3f, false)));
        // ---- infected specials
        out.put("retch", concat(lowpass(groan(0.55f, 60, 0.9f), 0.12f), gurgle()));
        out.put("splash", concat(lowpass(noise(0.35f, 0.02f, 0.7f), 0.3f), lowpass(noise(0.5f, 0.1f, 0.25f), 0.12f)));
        // ---- music beds (looped on the Music bus)
        out.put("music_hideout", music(110f, new float[]{1f, 1.189f, 1.498f}, 16f, 0.0f));
        out.put("music_tension", music(82.4f, new float[]{1f, 1.06f, 1.498f}, 8f, 1.0f));
    }

    /**
     * A slow minor pad with a detuned chorus and, for the tension bed, a low heartbeat pulse.
     * Loop length is a whole number of every partial's period so the seam is silent.
     */
    private float[] music(float root, float[] chord, float sec, float pulse) {
        float[] b = buf(sec);
        int n = b.length;
        for (float ratio : chord) for (int voice = 0; voice < 3; voice++) {
            // snap each partial to an integer number of cycles over the loop
            float f = Math.round(root * ratio * (1 + (voice - 1) * 0.004f) * sec) / sec;
            float lfo = (1 + voice) / sec, amp = 0.045f / (1 + voice * 0.3f);
            for (int i = 0; i < n; i++) {
                float t = i / (float) R;
                float swell = 0.6f + 0.4f * (float) Math.sin(2 * Math.PI * lfo * t + ratio * 3);
                b[i] += (float) (Math.sin(2 * Math.PI * f * t) + 0.35 * Math.sin(2 * Math.PI * f * 2 * t) + 0.12 * Math.sin(2 * Math.PI * f * 3 * t)) * amp * swell;
            }
        }
        if (pulse > 0) {
            float bps = Math.round(1.6f * sec) / sec, f = Math.round(root * 0.5f * sec) / sec;
            for (int i = 0; i < n; i++) {
                float t = i / (float) R, beat = (t * bps) % 1f;
                float env = (float) Math.exp(-beat * 9) + 0.6f * (float) Math.exp(-Math.abs(beat - 0.22f) * 26);
                b[i] += (float) Math.sin(2 * Math.PI * f * t) * env * 0.16f * pulse;
            }
        }
        return lowpass(b, 0.35f);
    }

    // ------------------------------------------------------------------ primitives
    private float[] buf(float sec) { return new float[Math.max(2, (int) (sec * R))]; }
    private float[] silence(float sec) { return buf(sec); }

    private float[] concat(float[]... parts) {
        int n = 0;
        for (float[] p : parts) n += p.length;
        float[] o = new float[n];
        int k = 0;
        for (float[] p : parts) { System.arraycopy(p, 0, o, k, p.length); k += p.length; }
        return o;
    }

    private float[] noise(float sec, float attack, float vol) {
        float[] b = buf(sec);
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) b.length;
            float env = Math.min(1, t / Math.max(1e-4f, attack)) * (1 - t);
            b[i] = (rng.nextFloat() * 2 - 1) * env * vol;
        }
        return b;
    }

    private float[] lowpass(float[] b, float a) {
        float y = 0;
        float[] o = new float[b.length];
        for (int i = 0; i < b.length; i++) { y += a * (b[i] - y); o[i] = y; }
        float peak = 1e-6f;
        for (float v : o) peak = Math.max(peak, Math.abs(v));
        float gain = Math.min(8f, 0.8f / peak);
        for (int i = 0; i < o.length; i++) o[i] *= gain;
        return o;
    }

    private float[] click(float sec, float freq, float vol) {
        float[] b = buf(sec);
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) R;
            float env = (float) Math.exp(-t * 60 / sec * 0.05);
            b[i] = (float) ((Math.sin(2 * Math.PI * freq * t) * 0.6 + (rng.nextFloat() * 2 - 1) * 0.4) * env * vol * (1 - i / (float) b.length));
        }
        return b;
    }

    private float[] slide(float sec, float f0, float f1, float vol) {
        float[] b = buf(sec);
        double ph = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) b.length;
            float f = f0 + (f1 - f0) * t;
            ph += 2 * Math.PI * f / R;
            b[i] = (float) ((Math.sin(ph) * 0.3 + (rng.nextFloat() * 2 - 1) * 0.7) * Math.sin(Math.PI * t) * vol);
        }
        return lowpassRaw(b, 0.3f);
    }

    private float[] lowpassRaw(float[] b, float a) {
        float y = 0;
        for (int i = 0; i < b.length; i++) { y += a * (b[i] - y); b[i] = y; }
        return b;
    }

    private float[] tone(float sec, float f, float vol, boolean square) {
        float[] b = buf(sec);
        for (int i = 0; i < b.length; i++) {
            double s = Math.sin(2 * Math.PI * f * i / R);
            if (square) s = Math.signum(s) * 0.6;
            float t = i / (float) b.length;
            b[i] = (float) (s * vol * Math.min(1, t * 40) * Math.min(1, (1 - t) * 10));
        }
        return b;
    }

    private void reverb(float[] b, float delaySec, float fb, int taps) {
        for (int k = 1; k <= taps; k++) {
            int d = (int) (delaySec * k * R * (0.8 + 0.4 * rng.nextFloat()));
            float g = (float) Math.pow(fb, k);
            for (int i = b.length - 1; i >= d; i--) b[i] += b[i - d] * g;
        }
    }

    private void normalize(float[] b, float peak) {
        float m = 1e-6f;
        for (float v : b) m = Math.max(m, Math.abs(v));
        for (int i = 0; i < b.length; i++) b[i] = b[i] / m * peak;
    }

    // ------------------------------------------------------------------ weapon signatures
    /** crack: high-frequency blast; thump: low body; tail: urban echo. */
    private float[] gun(float len, float thumpHz, float crackDecay, float bright, float vol, float tail) {
        float[] b = buf(len + 0.4f);
        double ph = 0;
        float lp = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) R;
            float n = rng.nextFloat() * 2 - 1;
            lp += (0.15f + bright * 0.6f) * (n - lp);
            float crack = (float) (Math.exp(-t / crackDecay) * (bright * n + (1 - bright) * lp));
            ph += 2 * Math.PI * (thumpHz * (1 + 2 * Math.exp(-t * 40))) / R;
            float thump = (float) (Math.sin(ph) * Math.exp(-t / (crackDecay * 1.8)));
            float body = (float) (lp * Math.exp(-t / (len * 0.35)) * 0.5);
            b[i] = (crack * 1.0f + thump * 0.9f + body) * vol;
        }
        reverb(b, 0.09f, 0.35f, 4);
        reverb(b, tail, 0.25f, 3);
        normalize(b, 0.95f);
        return b;
    }

    private float[] suppressed() {
        float[] b = buf(0.25f);
        float lp = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) R;
            lp += 0.2f * ((rng.nextFloat() * 2 - 1) - lp);
            b[i] = (float) (lp * Math.exp(-t / 0.03) + Math.sin(2 * Math.PI * 160 * t) * Math.exp(-t / 0.02) * 0.4);
        }
        float[] mech = click(0.04f, 1800, 0.5f);
        for (int i = 0; i < mech.length && i + 800 < b.length; i++) b[i + 800] += mech[i] * 0.6f;
        normalize(b, 0.6f);
        return b;
    }

    private float[] distant(float len) {
        float[] b = buf(len);
        float lp = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) R;
            lp += 0.03f * ((rng.nextFloat() * 2 - 1) - lp);
            b[i] = (float) (lp * Math.exp(-t / 0.15));
        }
        reverb(b, 0.2f, 0.4f, 3);
        normalize(b, 0.7f);
        return b;
    }

    private float[] distantBurst() {
        float[] one = distant(0.5f);
        float[] b = buf(1.6f);
        for (int k = 0; k < 7; k++) {
            int off = (int) (k * 0.11f * R + rng.nextFloat() * 400);
            for (int i = 0; i < one.length && off + i < b.length; i++) b[off + i] += one[i] * (0.7f + rng.nextFloat() * 0.3f);
        }
        normalize(b, 0.7f);
        return b;
    }

    // ------------------------------------------------------------------ world sounds
    private float[] step(String mat) {
        switch (mat) {
            case "metal": { float[] b = concat(click(0.03f, 900, 0.7f), tone(0.12f, 420, 0.15f, false)); reverb(b, 0.03f, 0.3f, 2); return b; }
            case "wood": return lowpassRaw(concat(click(0.04f, 300, 0.8f), noise(0.06f, 0.01f, 0.2f)), 0.25f);
            case "grass": return lowpass(noise(0.14f, 0.2f, 0.5f), 0.25f);
            case "dirt": return lowpass(noise(0.1f, 0.05f, 0.6f), 0.15f);
            case "water": { float[] b = lowpass(noise(0.25f, 0.1f, 0.6f), 0.12f); return b; }
            case "fabric": return lowpass(noise(0.08f, 0.1f, 0.3f), 0.1f);
            case "flesh": return lowpass(noise(0.1f, 0.05f, 0.6f), 0.08f);
            default: return lowpassRaw(concat(click(0.025f, 600, 0.7f), noise(0.05f, 0.01f, 0.25f)), 0.35f);
        }
    }

    private float[] impact(String mat) {
        switch (mat) {
            case "metal": { float[] b = concat(click(0.02f, 2400, 0.9f), tone(0.25f, 1700, 0.2f, false)); reverb(b, 0.02f, 0.4f, 3); return b; }
            case "wood": return lowpassRaw(concat(click(0.03f, 400, 1f), noise(0.1f, 0.01f, 0.4f)), 0.3f);
            case "glass": return glassBreak();
            case "flesh": return lowpass(concat(noise(0.05f, 0.01f, 0.9f), noise(0.08f, 0.2f, 0.4f)), 0.12f);
            case "dirt": case "grass": return lowpass(noise(0.15f, 0.01f, 0.8f), 0.1f);
            case "water": return lowpass(noise(0.3f, 0.02f, 0.7f), 0.2f);
            default: { float[] b = concat(click(0.02f, 1200, 1f), noise(0.12f, 0.01f, 0.5f)); return lowpassRaw(b, 0.5f); }
        }
    }

    private float[] ricochet() {
        float[] b = buf(0.4f);
        double ph = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) b.length;
            ph += 2 * Math.PI * (3200 - 2200 * t) / R;
            b[i] = (float) (Math.sin(ph) * (1 - t) * 0.4 + (rng.nextFloat() - 0.5f) * 0.1f * (1 - t));
        }
        return b;
    }

    private float[] glassBreak() {
        float[] b = buf(0.6f);
        for (int k = 0; k < 40; k++) {
            int off = (int) (rng.nextFloat() * rng.nextFloat() * b.length * 0.8f);
            float f = 2500 + rng.nextFloat() * 4500;
            int len = 600 + rng.nextInt(2000);
            for (int i = 0; i < len && off + i < b.length; i++) b[off + i] += (float) (Math.sin(2 * Math.PI * f * i / R) * Math.exp(-i / (len * 0.3)) * 0.3);
        }
        float[] n = noise(0.08f, 0.01f, 0.8f);
        for (int i = 0; i < n.length; i++) b[i] += n[i];
        normalize(b, 0.8f);
        return b;
    }

    private float[] explosion(float len) {
        float[] b = buf(len);
        float lp = 0, lp2 = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) R;
            float n = rng.nextFloat() * 2 - 1;
            lp += 0.08f * (n - lp);
            lp2 += 0.01f * (n - lp2);
            float env = (float) (Math.exp(-t / (len * 0.2)) );
            b[i] = (float) ((n * Math.exp(-t / 0.03) * 0.8) + lp * env * 2 + lp2 * Math.exp(-t / (len * 0.4)) * 6 + Math.sin(2 * Math.PI * 45 * t) * Math.exp(-t / 0.2));
        }
        reverb(b, 0.15f, 0.4f, 4);
        normalize(b, 1f);
        return b;
    }

    private float[] door(boolean open) {
        float[] creak = buf(0.45f);
        double ph = 0;
        for (int i = 0; i < creak.length; i++) {
            float t = i / (float) creak.length;
            ph += 2 * Math.PI * (open ? 180 + 140 * t : 320 - 140 * t) / R;
            creak[i] = (float) (Math.signum(Math.sin(ph)) * 0.15 * Math.sin(Math.PI * t) * (0.6 + 0.4 * Math.sin(t * 60)));
        }
        lowpassRaw(creak, 0.2f);
        return open ? concat(click(0.03f, 600, 0.6f), creak) : concat(creak, lowpassRaw(click(0.06f, 200, 1f), 0.3f));
    }

    private float[] rustle(float sec) {
        float[] b = buf(sec);
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) b.length;
            float am = (float) (0.5 + 0.5 * Math.sin(t * 40 + Math.sin(t * 13) * 3));
            b[i] = (rng.nextFloat() * 2 - 1) * am * 0.3f * (float) Math.sin(Math.PI * t);
        }
        return lowpassRaw(b, 0.35f);
    }

    private float[] zipper() {
        float[] b = buf(0.35f);
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) b.length;
            b[i] = (float) ((rng.nextFloat() * 2 - 1) * (0.5 + 0.5 * Math.signum(Math.sin(i * 2 * Math.PI * 90 / R))) * 0.3 * Math.sin(Math.PI * t));
        }
        return b;
    }

    // ------------------------------------------------------------------ voices
    private float[] groan(float sec, float f0, float rough) {
        float[] b = buf(sec);
        double ph = 0;
        float lp = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) b.length;
            float f = f0 * (1 + 0.25f * (float) Math.sin(t * 7) + 0.1f * t);
            ph += 2 * Math.PI * f / R;
            float saw = (float) ((ph / (2 * Math.PI)) % 1.0) * 2 - 1;
            lp += 0.08f * ((saw + (rng.nextFloat() - 0.5f) * rough) - lp);
            b[i] = lp * (float) Math.sin(Math.PI * t) * (0.7f + 0.3f * (float) Math.sin(t * 31));
        }
        normalize(b, 0.7f);
        return b;
    }

    private float[] shriek(float sec, float f0) {
        float[] b = buf(sec);
        double ph = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) b.length;
            ph += 2 * Math.PI * f0 * (1 + 0.6f * t + 0.05f * Math.sin(t * 90)) / R;
            b[i] = (float) ((Math.sin(ph) * 0.5 + Math.sin(ph * 2.01) * 0.3 + (rng.nextFloat() - 0.5) * 0.4) * Math.sin(Math.PI * t));
        }
        normalize(b, 0.8f);
        return b;
    }

    private float[] screech() {
        float[] b = buf(2.4f);
        double ph = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) b.length;
            float f = 600 + 1200 * t + 80 * (float) Math.sin(t * 120);
            ph += 2 * Math.PI * f / R;
            float env = Math.min(1, t * 6) * Math.min(1, (1 - t) * 8);
            b[i] = (float) ((Math.sin(ph) * 0.4 + Math.sin(ph * 1.5) * 0.3 + (rng.nextFloat() - 0.5) * 0.6) * env);
        }
        reverb(b, 0.12f, 0.4f, 3);
        normalize(b, 0.95f);
        return b;
    }

    private float[] gurgle() {
        float[] b = buf(0.8f);
        for (int k = 0; k < 14; k++) {
            int off = rng.nextInt(b.length - 3000);
            float f = 120 + rng.nextFloat() * 300;
            for (int i = 0; i < 3000; i++) b[off + i] += (float) (Math.sin(2 * Math.PI * (f + i * 0.08) * i / R) * Math.sin(Math.PI * i / 3000.0) * 0.4);
        }
        float[] n = lowpass(noise(0.8f, 0.2f, 0.3f), 0.05f);
        for (int i = 0; i < b.length; i++) b[i] += n[i] * 0.4f;
        normalize(b, 0.7f);
        return b;
    }

    private float[] roar() {
        float[] g = groan(1.8f, 45, 1.2f);
        float[] n = lowpass(noise(1.8f, 0.2f, 0.8f), 0.04f);
        for (int i = 0; i < g.length && i < n.length; i++) g[i] = g[i] * 0.8f + n[i] * 0.5f;
        reverb(g, 0.1f, 0.4f, 3);
        normalize(g, 1f);
        return g;
    }

    private float[] radio() {
        float[] b = buf(0.9f);
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) b.length;
            float voice = (float) Math.sin(2 * Math.PI * (300 + 120 * Math.sin(t * 25)) * i / R) * (float) (0.5 + 0.5 * Math.sin(t * 47));
            b[i] = ((rng.nextFloat() * 2 - 1) * 0.35f + voice * 0.35f) * (t < 0.05f || t > 0.95f ? 1.5f : 1f);
        }
        float[] beep = tone(0.06f, 1800, 0.3f, false);
        return concat(beep, lowpassRaw(b, 0.5f), beep);
    }

    private float[] shout() {
        float[] b = buf(0.5f);
        double ph = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) b.length;
            ph += 2 * Math.PI * (220 - 60 * t) / R;
            double v = Math.sin(ph) + 0.5 * Math.sin(ph * 2) + 0.3 * Math.sin(ph * 3);
            b[i] = (float) ((v * 0.4 + (rng.nextFloat() - 0.5) * 0.2) * Math.sin(Math.PI * t) * (0.6 + 0.4 * Math.sin(t * 20)));
        }
        reverb(b, 0.08f, 0.3f, 2);
        normalize(b, 0.7f);
        return b;
    }

    private float[] heartbeat() {
        float[] beat = lowpassRaw(concat(tone(0.08f, 55, 0.9f, false), silence(0.1f), tone(0.07f, 50, 0.7f, false)), 0.3f);
        return concat(beat, silence(0.55f));
    }

    private float[] hurt() {
        float[] g = groan(0.35f, 160, 0.8f);
        normalize(g, 0.6f);
        return g;
    }

    private float[] siren() {
        float[] b = buf(3f);
        double ph = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) R;
            float f = 600 + 300 * (float) Math.sin(t * Math.PI * 2 / 1.5);
            ph += 2 * Math.PI * f / R;
            b[i] = (float) (Math.sin(ph) * 0.5 + Math.sin(ph * 2) * 0.15);
        }
        return b;
    }

    private float[] flareHiss() {
        float[] b = buf(3f);
        for (int i = 0; i < b.length; i++) b[i] = (rng.nextFloat() * 2 - 1) * 0.3f * (0.8f + 0.2f * (float) Math.sin(i * 0.01));
        return lowpassRaw(b, 0.6f);
    }

    private float[] heli() {
        float[] b = buf(2f);
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) R;
            float blade = (float) Math.pow(Math.max(0, Math.sin(t * Math.PI * 2 * 5.5)), 8);
            b[i] = ((rng.nextFloat() * 2 - 1) * 0.6f * blade) + (float) Math.sin(2 * Math.PI * 60 * t) * 0.1f;
        }
        return lowpassRaw(b, 0.15f);
    }

    private float[] horn(float f1, float f2, float sec) {
        float[] b = buf(sec);
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) b.length;
            double s = 0;
            for (int h = 1; h < 6; h++) s += (Math.sin(2 * Math.PI * f1 * h * i / R) + Math.sin(2 * Math.PI * f2 * h * i / R)) / h;
            b[i] = (float) (s * 0.15 * Math.min(1, t * 10) * Math.min(1, (1 - t) * 6));
        }
        reverb(b, 0.25f, 0.4f, 3);
        normalize(b, 0.8f);
        return b;
    }

    // ------------------------------------------------------------------ ambience beds (loop-friendly)
    private float[] wind() {
        float[] b = buf(8f);
        float lp = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) b.length;
            float gust = 0.5f + 0.5f * (float) Math.sin(t * Math.PI * 2 * 3) * (float) Math.sin(t * Math.PI * 2 * 1);
            lp += (0.01f + 0.02f * gust) * ((rng.nextFloat() * 2 - 1) - lp);
            b[i] = lp * (0.6f + 0.4f * gust);
        }
        crossfadeLoop(b);
        normalize(b, 0.5f);
        return b;
    }

    private float[] rain() {
        float[] b = buf(6f);
        float lp = 0;
        for (int i = 0; i < b.length; i++) {
            lp += 0.3f * ((rng.nextFloat() * 2 - 1) - lp);
            b[i] = lp * 0.4f;
            if (rng.nextFloat() < 0.002f) b[i] += 0.5f;
        }
        crossfadeLoop(b);
        normalize(b, 0.45f);
        return b;
    }

    private float[] cityDrone() {
        float[] b = buf(8f);
        float lp = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) R;
            lp += 0.004f * ((rng.nextFloat() * 2 - 1) - lp);
            b[i] = lp * 3 + (float) Math.sin(2 * Math.PI * 50 * t) * 0.02f;
        }
        crossfadeLoop(b);
        normalize(b, 0.35f);
        return b;
    }

    private float[] machines() {
        float[] b = buf(6f);
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) R;
            b[i] = (float) (Math.sin(2 * Math.PI * 60 * t) * 0.3 + Math.sin(2 * Math.PI * 120 * t) * 0.15 * (0.5 + 0.5 * Math.sin(t * 3)) + (Math.sin(t * Math.PI * 2 * 1.5) > 0.95 ? (rng.nextFloat() - 0.5) * 0.6 : 0));
        }
        crossfadeLoop(b);
        normalize(b, 0.4f);
        return b;
    }

    private float[] eerie() {
        float[] b = buf(10f);
        for (int i = 0; i < b.length; i++) {
            float t = i / (float) R;
            b[i] = (float) (Math.sin(2 * Math.PI * 110 * t + Math.sin(t * 0.7) * 3) * 0.2 + Math.sin(2 * Math.PI * 164.8 * t) * 0.15 * Math.sin(t * 0.4) + Math.sin(2 * Math.PI * 41 * t) * 0.2);
        }
        crossfadeLoop(b);
        normalize(b, 0.35f);
        return b;
    }

    private float[] bells() {
        float[] b = buf(12f);
        float[] tones = {196, 233, 262, 294};
        for (int k = 0; k < 4; k++) {
            int off = (int) ((0.5f + k * 1.4f) * R);
            float f = tones[k % tones.length];
            for (int i = 0; i < 6 * R && off + i < b.length; i++) {
                double env = Math.exp(-i / (1.8 * R));
                b[off + i] += (float) ((Math.sin(2 * Math.PI * f * i / R) + 0.5 * Math.sin(2 * Math.PI * f * 2.4 * i / R) + 0.3 * Math.sin(2 * Math.PI * f * 3.9 * i / R)) * env * 0.3);
            }
        }
        float[] w = wind();
        for (int i = 0; i < b.length; i++) b[i] += w[i % w.length] * 0.6f;
        normalize(b, 0.5f);
        return b;
    }

    private void crossfadeLoop(float[] b) {
        int n = R / 2;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            b[b.length - n + i] = b[b.length - n + i] * (1 - t) + b[i] * t;
        }
    }
}
