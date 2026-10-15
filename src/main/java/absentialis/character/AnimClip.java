package absentialis.character;

import java.util.ArrayList;
import java.util.List;

/**
 * An animation sequence: keyframes of per-SKIN-group rotations (plus root and weapon channels).
 * Clips know nothing about meshes - they drive group numbers, and every model shares those.
 */
public final class AnimClip {
    public final String name;
    public final float duration;
    public final boolean loop;
    /** SKIN groups this clip drives. */
    public int mask = Rig.ALL_MASK;
    public boolean additive, drivesRoot = true, drivesWeapon;
    /** Gait metadata (locomotion clips): stance travel in voxels and duty factor. */
    public float travel, duty = 0.6f;
    private final List<Float> times = new ArrayList<>();
    private final List<Pose> keys = new ArrayList<>();

    public AnimClip(String name, float duration, boolean loop) { this.name = name; this.duration = duration; this.loop = loop; }

    /** Adds a keyframe at normalised time t (0..1); returns the pose to fill. */
    public Pose key(float t) {
        Pose p = new Pose();
        if (!keys.isEmpty()) p.set(keys.get(keys.size() - 1));
        times.add(t);
        keys.add(p);
        return p;
    }

    public AnimClip mask(int m) { mask = m; return this; }
    public AnimClip additive() { additive = true; return this; }
    /** Weapon-channel clip: both arms are solved onto the weapon, so only the torso is keyed directly. */
    public AnimClip weapon() { drivesWeapon = true; mask = Rig.bit(Rig.TORSO); return this; }
    public AnimClip noRoot() { drivesRoot = false; return this; }
    public int keyCount() { return keys.size(); }

    /** Samples at normalised time (wrapped when looping) with eased interpolation between keys. */
    public void sample(float t, Pose out) {
        int n = keys.size();
        if (n == 0) { out.clear(); return; }
        if (loop) { t = t - (float) Math.floor(t); } else t = Math.max(0, Math.min(1, t));
        int a = -1;
        for (int i = 0; i < n; i++) if (times.get(i) <= t) a = i;
        int b = a + 1;
        float ta, tb;
        if (a < 0) { if (!loop) { out.set(keys.get(0)); return; } a = n - 1; ta = times.get(a) - 1; } else ta = times.get(a);
        if (b >= n) { if (!loop) { out.set(keys.get(n - 1)); return; } b = 0; tb = times.get(0) + 1; } else tb = times.get(b);
        float span = tb - ta, f = span < 1e-5f ? 0 : (t - ta) / span;
        f = Math.max(0, Math.min(1, f));
        if (!loop) f = f * f * (3 - 2 * f);
        out.set(keys.get(a));
        out.blend(keys.get(b), f, Rig.ALL_MASK, true, true);
    }
}
