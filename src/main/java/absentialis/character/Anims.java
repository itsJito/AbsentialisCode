package absentialis.character;

import static absentialis.character.Rig.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The animation sequence cache. Every clip is a list of keyframes per SKIN group; locomotion
 * cycles are baked from a foot-path description so that the stance foot travels exactly as far as
 * the body moves (no sliding), action clips are keyed by hand. Nothing here references a mesh.
 */
public final class Anims {
    private Anims() {}

    private static final Map<String, AnimClip> CLIPS = new LinkedHashMap<>();
    private static final float TAU = (float) (Math.PI * 2);

    public static AnimClip get(String name) {
        AnimClip c = CLIPS.get(name);
        if (c == null) throw new IllegalArgumentException("unknown animation clip " + name);
        return c;
    }

    public static boolean has(String name) { return CLIPS.containsKey(name); }
    public static Map<String, AnimClip> all() { return CLIPS; }

    /** Foot-path description of one gait. Distances in voxels. */
    public record Gait(float travel, float duty, float lift, float hip, float lean, float twist, float armSwing, float armBend, float sway, float stagger) {}

    static {
        // ---- humans: lower body + default (unarmed) upper body
        bake("idle", new Gait(0, 0.6f, 0, 11.86f, 0.0f, 0, 0, 0.14f, 0, 0.5f), 8, true);
        bake("walk", new Gait(10.4f, 0.56f, 1.7f, 11.9f, -0.05f, 0.10f, 0.45f, 0.22f, 0.025f, 0), 24, false);
        // 1.0: a run is a run. The run reaches further and lifts higher than it did (it read as a jog); the sprint is longer in
        // the stride again - the legs go right out in front and behind - lower in the hips, and hunched: the trunk is driven
        // forward over the knees. The same ground is covered in fewer steps, so the feet are heard less often at the same speed.
        // (the hips are carried low enough for the legs to reach that far: a leg is twelve voxels, and a foot that could not
        // reach the end of its stride would slide)
        bake("jog", new Gait(12.0f, 0.32f, 3.5f, 11.5f, -0.24f, 0.18f, 0.95f, 1.00f, 0.03f, 0), 24, false);
        bake("sprint", new Gait(13.6f, 0.23f, 5.2f, 11.0f, -0.50f, 0.24f, 1.35f, 1.40f, 0.035f, 0), 24, false);
        bake("crouch_idle", new Gait(0, 0.6f, 0, 4.6f, -0.55f, 0, 0, 0.5f, 0, 1.4f), 8, true);
        bake("crouch_walk", new Gait(6.4f, 0.62f, 1.2f, 4.9f, -0.55f, 0.06f, 0.2f, 0.5f, 0.03f, 0), 24, false);
        // ---- infected gaits
        bake("inf_idle", new Gait(0, 0.6f, 0, 11.5f, -0.16f, 0, 0, 0.2f, 0, 0.8f), 8, true);
        bake("inf_shamble", new Gait(6.4f, 0.70f, 0.8f, 11.4f, -0.20f, 0.14f, 0.12f, 0.2f, 0.09f, 0), 24, false);
        bake("inf_run", new Gait(10.8f, 0.27f, 3.6f, 10.8f, -0.52f, 0.22f, 0.3f, 0.3f, 0.06f, 0), 24, false);
        // 1.0, the Bomber: the same pace as ever, but not a shuffle. The weight is carried: the trunk lies back to balance the
        // belly at every moment, the body is thrown from one leg over onto the other with each step (a wide roll, and the
        // shoulders turn with it), the feet barely leave the ground, and the arms hang off the sides and swing loose.
        bake("fat_waddle", new Gait(5.2f, 0.76f, 0.6f, 11.0f, 0.24f, 0.20f, 0.34f, 0.10f, 0.30f, 0), 24, false);
        bake("giant_stomp", new Gait(9.0f, 0.66f, 2.6f, 11.0f, -0.24f, 0.18f, 0.5f, 0.6f, 0.07f, 0), 24, false);
        // 1.0: the Crawler goes on all fours with its hips barely off the ground (the arms and the trunk are posed by the animator);
        // the Tyrant walks like something that has never had to run, and runs like a wall falling
        bake("crawl_idle", new Gait(0, 0.6f, 0, 4.6f, 0, 0, 0, 0.2f, 0, 2.2f), 8, true);
        bake("crawl_go", new Gait(7.6f, 0.46f, 1.5f, 4.9f, 0, 0.06f, 0, 0.3f, 0.05f, 2.0f), 24, false);
        bake("tyrant_walk", new Gait(10.2f, 0.64f, 1.5f, 11.8f, -0.02f, 0.07f, 0.2f, 0.2f, 0.03f, 0), 24, false);
        bake("tyrant_run", new Gait(12.8f, 0.34f, 3.0f, 11.2f, -0.20f, 0.14f, 0.3f, 0.6f, 0.04f, 0), 24, false);

        // ---- weapon holds (weapon channel: pos, {pitch, yaw, roll}, support-hand offset)
        hold("wpn_ready", 2.2f, -5.0f, -5.0f, -0.50f, 0.35f, 0.0f);
        hold("wpn_aim", 2.0f, -2.3f, -5.2f, 0.0f, 0.0f, 0.0f);
        hold("wpn_sprint", 1.0f, -5.4f, -4.2f, 0.55f, 0.90f, 0.2f);

        // ---- weapon actions (upper body; the support hand travels in weapon space)
        AnimClip c = clip("reload_tac", 2.4f).weapon().noRoot();
        w(c.key(0.00f), 2.0f, -4.2f, -5.0f, 0.15f, 0.25f, 0.35f, 0, 0, 0);
        w(c.key(0.18f), 2.0f, -4.2f, -5.0f, 0.18f, 0.28f, 0.42f, 0, -1.6f, 3.5f);     // hand on the magazine
        w(c.key(0.34f), 2.0f, -4.0f, -5.0f, 0.24f, 0.28f, 0.42f, 0.5f, -6.5f, 4.0f);  // magazine out
        w(c.key(0.52f), 2.0f, -4.2f, -5.0f, 0.18f, 0.30f, 0.45f, -2.0f, -9.5f, 7.0f); // pouch: stow it, grab a fresh one
        w(c.key(0.72f), 2.0f, -4.2f, -5.0f, 0.18f, 0.28f, 0.42f, 0.3f, -5.0f, 3.8f);
        w(c.key(0.84f), 2.0f, -3.9f, -5.1f, 0.26f, 0.26f, 0.40f, 0, -1.6f, 3.5f);     // seated
        w(c.key(1.00f), 2.2f, -5.0f, -5.0f, -0.50f, 0.35f, 0.0f, 0, 0, 0);
        c = clip("reload_empty", 3.0f).weapon().noRoot();
        w(c.key(0.00f), 2.0f, -4.2f, -5.0f, 0.15f, 0.25f, 0.35f, 0, 0, 0);
        w(c.key(0.14f), 2.0f, -4.2f, -5.0f, 0.20f, 0.28f, 0.50f, 0, -1.6f, 3.5f);
        w(c.key(0.24f), 2.0f, -3.8f, -5.0f, 0.34f, 0.28f, 0.55f, 0.6f, -7.5f, 4.5f);  // empty magazine falls away
        w(c.key(0.40f), 2.0f, -4.2f, -5.0f, 0.20f, 0.30f, 0.50f, -2.0f, -9.5f, 7.0f);
        w(c.key(0.56f), 2.0f, -4.2f, -5.0f, 0.20f, 0.28f, 0.45f, 0.3f, -5.0f, 3.8f);
        w(c.key(0.66f), 2.0f, -3.9f, -5.1f, 0.28f, 0.26f, 0.40f, 0, -1.6f, 3.5f);     // seated
        w(c.key(0.76f), 2.0f, -4.0f, -5.0f, 0.10f, 0.20f, -0.20f, 1.2f, 1.5f, 4.6f);  // hand to the charging handle
        w(c.key(0.84f), 2.0f, -4.0f, -4.4f, 0.14f, 0.20f, -0.22f, 1.2f, 1.5f, 7.4f);  // rack
        w(c.key(0.89f), 2.0f, -4.0f, -5.4f, 0.04f, 0.20f, -0.20f, 1.2f, 1.5f, 4.6f);  // bolt home
        w(c.key(1.00f), 2.2f, -5.0f, -5.0f, -0.50f, 0.35f, 0.0f, 0, 0, 0);
        // 1.3: a handgun's own reloads. The two above are a rifle's - the support hand goes to a magazine well ahead of the grip
        // and to a charging handle at the back of the receiver - and a handgun has neither: its magazine is in the grip and it
        // is made ready by the slide. (Support-hand targets are relative to the firing hand: x to the left, y up, z back.)
        // The gun comes up and in, muzzle high and canted toward the body, so the eyes see the grip; the off hand drops
        // to the belt for a fresh magazine, seats it in the butt of the grip with the heel of the palm and wraps the grip again.
        c = clip("reload_tac_pistol", 1.9f).weapon().noRoot();
        w(c.key(0.00f), 2.0f, -4.2f, -5.0f, 0.15f, 0.25f, 0.20f, -0.9f, -1.3f, 0);
        w(c.key(0.12f), 0.9f, -2.7f, -4.5f, 0.30f, 0.38f, 0.45f, -1.2f, -3.2f, 0.4f);   // thumb on the release: the magazine is caught as it drops
        w(c.key(0.30f), 0.9f, -2.7f, -4.5f, 0.30f, 0.38f, 0.45f, -2.6f, -9.4f, 3.4f);   // the part-used one to the pouch
        w(c.key(0.50f), 0.9f, -2.8f, -4.5f, 0.30f, 0.38f, 0.45f, -2.3f, -9.0f, 3.0f);   // a full one out of it
        w(c.key(0.68f), 0.9f, -2.7f, -4.5f, 0.32f, 0.38f, 0.45f, -0.5f, -4.3f, 0.3f);   // lined up under the grip
        w(c.key(0.78f), 0.9f, -2.5f, -4.5f, 0.34f, 0.38f, 0.45f, -0.5f, -2.5f, 0.2f);   // seated with the heel of the hand
        w(c.key(0.88f), 1.8f, -3.8f, -4.8f, 0.22f, 0.30f, 0.25f, -0.9f, -1.3f, 0);      // wrapped round the grip again
        w(c.key(1.00f), 2.2f, -5.0f, -5.0f, -0.50f, 0.35f, 0.0f, -0.9f, -1.3f, 0);
        c = clip("reload_empty_pistol", 2.3f).weapon().noRoot();
        w(c.key(0.00f), 2.0f, -4.2f, -5.0f, 0.15f, 0.25f, 0.20f, -0.9f, -1.3f, 0);
        w(c.key(0.10f), 0.9f, -2.7f, -4.5f, 0.30f, 0.38f, 0.45f, -1.4f, -3.6f, 0.5f);   // the empty magazine falls clear
        w(c.key(0.28f), 0.9f, -2.7f, -4.5f, 0.30f, 0.38f, 0.45f, -2.4f, -9.4f, 3.3f);   // straight to the pouch for a full one
        w(c.key(0.46f), 0.9f, -2.7f, -4.5f, 0.32f, 0.38f, 0.45f, -0.5f, -4.3f, 0.3f);
        w(c.key(0.55f), 0.9f, -2.5f, -4.5f, 0.34f, 0.38f, 0.45f, -0.5f, -2.5f, 0.2f);   // seated
        w(c.key(0.66f), 1.1f, -2.8f, -4.4f, 0.20f, 0.30f, 0.15f, 0.3f, 1.7f, 1.3f);     // the slide is locked back: the hand over its rear
        w(c.key(0.76f), 1.1f, -2.8f, -4.0f, 0.18f, 0.30f, 0.15f, 0.3f, 1.7f, 3.3f);     // slingshot: pulled back...
        w(c.key(0.81f), 1.1f, -2.8f, -4.7f, 0.18f, 0.30f, 0.12f, 0.8f, 2.6f, 3.6f);     // ...and let go: the gun drives forward
        w(c.key(0.90f), 1.8f, -3.8f, -4.8f, 0.20f, 0.28f, 0.10f, -0.9f, -1.3f, 0);      // two hands on it again
        w(c.key(1.00f), 2.2f, -5.0f, -5.0f, -0.50f, 0.35f, 0.0f, -0.9f, -1.3f, 0);
        c = clip("reload_shell", 0.7f).weapon().noRoot();
        w(c.key(0.00f), 2.0f, -4.4f, -5.0f, 0.10f, 0.30f, 0.60f, 0, -1.2f, 2.5f);
        w(c.key(0.40f), 2.0f, -4.4f, -5.0f, 0.10f, 0.30f, 0.60f, -1.5f, -8.5f, 6.0f);
        w(c.key(0.85f), 2.0f, -4.2f, -5.1f, 0.14f, 0.30f, 0.60f, 0, -1.4f, 3.0f);
        w(c.key(1.00f), 2.0f, -4.4f, -5.0f, 0.10f, 0.30f, 0.60f, 0, -1.2f, 2.5f);
        c = clip("clear_jam", 1.2f).weapon().noRoot();
        w(c.key(0.00f), 2.0f, -3.6f, -5.0f, 0.30f, 0.20f, -0.50f, 0, 0, 0);
        w(c.key(0.30f), 2.0f, -3.6f, -5.0f, 0.34f, 0.20f, -0.60f, 1.2f, 1.5f, 4.6f);
        w(c.key(0.50f), 2.0f, -3.4f, -4.2f, 0.40f, 0.20f, -0.65f, 1.2f, 1.5f, 7.6f);
        w(c.key(0.62f), 2.0f, -3.8f, -5.6f, 0.20f, 0.20f, -0.50f, 1.2f, 1.5f, 4.6f);
        w(c.key(1.00f), 2.2f, -5.0f, -5.0f, -0.50f, 0.35f, 0.0f, 0, 0, 0);
        c = clip("equip", 0.45f).weapon().noRoot();
        w(c.key(0.00f), 4.2f, -11.5f, 1.5f, -1.35f, 0.20f, 0.40f, 0, 0, 0).lhOnWeapon = 0;
        free(c.key(0.00f), -4.5f, -9.0f, -1.0f);
        Pose e = w(c.key(0.55f), 3.0f, -7.0f, -3.5f, -0.90f, 0.30f, 0.20f, 0, 0, 0); e.lhOnWeapon = 0.4f;
        w(c.key(1.00f), 2.2f, -5.0f, -5.0f, -0.50f, 0.35f, 0.0f, 0, 0, 0).lhOnWeapon = 1;
        c = clip("fire", 0.14f).weapon().noRoot().additive();
        Pose f = c.key(0.0f); f.wpn[2] = 0.9f; f.wpn[3] = 0.07f; f.r(TORSO, 0.035f, 0, 0); f.r(HEAD, 0.02f, 0, 0);
        c.key(1.0f).clear();
        c = clip("interact", 0.7f).weapon().noRoot();
        free(w(c.key(0.00f), 3.4f, -7.0f, -3.0f, -0.70f, 0.20f, 0.1f, 0, 0, 0), -4.0f, -6.5f, -3.0f);
        free(w(c.key(0.40f), 3.4f, -7.2f, -3.0f, -0.74f, 0.20f, 0.1f, 0, 0, 0), -2.2f, -2.5f, -9.6f);
        free(w(c.key(0.70f), 3.4f, -7.2f, -3.0f, -0.74f, 0.20f, 0.1f, 0, 0, 0), -2.0f, -3.0f, -9.2f);
        free(w(c.key(1.00f), 3.0f, -6.2f, -4.0f, -0.60f, 0.30f, 0.0f, 0, 0, 0), -3.0f, -5.5f, -5.0f);
        c = clip("heal", 1.6f).weapon().noRoot();
        free(w(c.key(0.00f), 3.6f, -7.6f, -2.6f, -0.85f, 0.15f, 0.2f, 0, 0, 0), -3.5f, -6.5f, -3.0f);
        free(w(c.key(0.25f), 3.6f, -7.6f, -2.6f, -0.85f, 0.15f, 0.2f, 0, 0, 0), 1.5f, -3.5f, -4.5f);
        free(w(c.key(0.50f), 3.6f, -7.6f, -2.6f, -0.85f, 0.15f, 0.2f, 0, 0, 0), 2.5f, -5.0f, -4.0f);
        free(w(c.key(0.75f), 3.6f, -7.6f, -2.6f, -0.85f, 0.15f, 0.2f, 0, 0, 0), 1.5f, -3.5f, -4.5f);
        free(w(c.key(1.00f), 3.0f, -6.2f, -4.0f, -0.60f, 0.30f, 0.0f, 0, 0, 0), -3.0f, -5.5f, -5.0f);
        c = clip("melee", 0.5f).weapon().noRoot();
        free(w(c.key(0.00f), 4.6f, 0.5f, -2.0f, 0.95f, 0.10f, 0.2f, 0, 0, 0), -4.0f, -6.0f, -4.0f).r(TORSO, 0.08f, -0.35f, 0);
        free(w(c.key(0.45f), 0.5f, -3.5f, -8.5f, -0.70f, 0.60f, 0.3f, 0, 0, 0), -4.5f, -6.5f, -2.0f).r(TORSO, -0.25f, 0.40f, 0);
        free(w(c.key(1.00f), 3.0f, -5.0f, -4.5f, -0.30f, 0.30f, 0.0f, 0, 0, 0), -4.0f, -6.0f, -4.0f).r(TORSO, 0, 0, 0);
        c = clip("throw", 0.6f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(TORSO, 0.10f, -0.45f, 0).r(R_UPPER_ARM, 2.7f, 0, 0.3f).r(R_FOREARM, 1.2f, 0, 0).r(L_UPPER_ARM, 1.0f, 0, -0.2f).r(L_FOREARM, 0.3f, 0, 0);
        c.key(0.45f).r(TORSO, -0.30f, 0.45f, 0).r(R_UPPER_ARM, 1.3f, 0, 0.1f).r(R_FOREARM, 0.1f, 0, 0).r(L_UPPER_ARM, 0.1f, 0, -0.2f).r(L_FOREARM, 0.5f, 0, 0);
        c.key(1.00f).r(TORSO, 0, 0, 0).r(R_UPPER_ARM, 0.2f, 0, 0.05f).r(R_FOREARM, 0.3f, 0, 0).r(L_UPPER_ARM, 0.1f, 0, -0.05f).r(L_FOREARM, 0.3f, 0, 0);

        // ---- 1.1: bare hands. A right, a left, and a heavy right with the whole trunk behind it; each starts and ends in the guard.
        c = clip("punch_r", 0.3f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(TORSO, 0.05f, -0.25f, 0).r(R_UPPER_ARM, 0.65f, 0, 0.25f).r(R_FOREARM, 1.9f, 0, 0).r(L_UPPER_ARM, 0.9f, 0, -0.25f).r(L_FOREARM, 1.8f, 0, 0);
        c.key(0.45f).r(TORSO, -0.12f, 0.45f, 0).r(R_UPPER_ARM, 1.5f, 0, 0.05f).r(R_FOREARM, 0.12f, 0, 0).r(L_UPPER_ARM, 0.85f, 0, -0.3f).r(L_FOREARM, 1.9f, 0, 0);
        c.key(1.00f).r(TORSO, 0, -0.1f, 0).r(R_UPPER_ARM, 0.75f, 0, 0.2f).r(R_FOREARM, 1.75f, 0, 0).r(L_UPPER_ARM, 0.9f, 0, -0.25f).r(L_FOREARM, 1.8f, 0, 0);
        c = clip("punch_l", 0.3f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(TORSO, 0.05f, 0.2f, 0).r(L_UPPER_ARM, 0.65f, 0, -0.25f).r(L_FOREARM, 1.9f, 0, 0).r(R_UPPER_ARM, 0.9f, 0, 0.25f).r(R_FOREARM, 1.8f, 0, 0);
        c.key(0.45f).r(TORSO, -0.12f, -0.45f, 0).r(L_UPPER_ARM, 1.5f, 0, -0.05f).r(L_FOREARM, 0.12f, 0, 0).r(R_UPPER_ARM, 0.85f, 0, 0.3f).r(R_FOREARM, 1.9f, 0, 0);
        c.key(1.00f).r(TORSO, 0, 0.1f, 0).r(L_UPPER_ARM, 0.75f, 0, -0.2f).r(L_FOREARM, 1.75f, 0, 0).r(R_UPPER_ARM, 0.9f, 0, 0.25f).r(R_FOREARM, 1.8f, 0, 0);
        c = clip("punch_heavy", 0.46f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(TORSO, 0.08f, -0.45f, 0).r(R_UPPER_ARM, 0.3f, 0, 0.45f).r(R_FOREARM, 2.0f, 0, 0).r(L_UPPER_ARM, 0.9f, 0, -0.25f).r(L_FOREARM, 1.8f, 0, 0);
        c.key(0.28f).r(TORSO, 0.14f, -0.80f, 0.05f).r(R_UPPER_ARM, -0.35f, 0, 0.6f).r(R_FOREARM, 2.1f, 0, 0).r(L_UPPER_ARM, 1.05f, 0, -0.2f).r(L_FOREARM, 1.6f, 0, 0);
        c.key(0.58f).r(TORSO, -0.32f, 0.72f, -0.06f).r(R_UPPER_ARM, 1.58f, 0, -0.12f).r(R_FOREARM, 0.08f, 0, 0).r(L_UPPER_ARM, 0.5f, 0, -0.4f).r(L_FOREARM, 1.9f, 0, 0);
        c.key(1.00f).r(TORSO, 0, 0, 0).r(R_UPPER_ARM, 0.7f, 0, 0.2f).r(R_FOREARM, 1.7f, 0, 0).r(L_UPPER_ARM, 0.9f, 0, -0.25f).r(L_FOREARM, 1.8f, 0, 0);

        // ---- reactions and traversal
        c = clip("hit", 0.35f).mask(UPPER_MASK).noRoot().additive();
        c.key(0.00f).r(TORSO, 0.30f, 0, 0).r(HEAD, 0.25f, 0, 0).r(L_UPPER_ARM, 0.3f, 0, -0.2f).r(R_UPPER_ARM, 0.3f, 0, 0.2f);
        c.key(1.00f).clear();
        c = clip("stumble", 0.6f).mask(UPPER_MASK);
        c.key(0.00f).r(TORSO, -0.15f, 0, 0);
        Pose s = c.key(0.30f); s.rootY = -2.2f; s.r(TORSO, -0.62f, 0.2f, 0.1f).r(HEAD, 0.3f, 0, 0).r(L_UPPER_ARM, 1.1f, 0, -0.5f).r(R_UPPER_ARM, 0.7f, 0, 0.6f).r(L_FOREARM, 0.6f, 0, 0).r(R_FOREARM, 0.9f, 0, 0);
        s = c.key(1.00f); s.rootY = 0; s.r(TORSO, -0.08f, 0, 0).r(HEAD, 0, 0, 0).r(L_UPPER_ARM, 0.1f, 0, -0.05f).r(R_UPPER_ARM, 0.1f, 0, 0.05f).r(L_FOREARM, 0.2f, 0, 0).r(R_FOREARM, 0.2f, 0, 0);
        c = new AnimClip("climb", 0.8f, true);
        CLIPS.put(c.name, c);
        for (int i = 0; i < 4; i++) {
            float a = i / 4f, sw = (float) Math.sin(a * TAU), cw = (float) Math.cos(a * TAU);
            Pose k = c.key(a);
            k.rootZ = -1.2f;
            k.r(TORSO, -0.06f, sw * 0.08f, 0).r(HEAD, 0.35f, 0, 0);
            k.r(L_UPPER_ARM, 2.3f + sw * 0.5f, 0, -0.1f).r(L_FOREARM, 0.5f - sw * 0.3f, 0, 0).r(R_UPPER_ARM, 2.3f - sw * 0.5f, 0, 0.1f).r(R_FOREARM, 0.5f + sw * 0.3f, 0, 0);
            k.r(L_THIGH, 0.75f - sw * 0.55f, 0, 0).r(L_SHIN, -1.0f + sw * 0.6f + cw * 0.1f, 0, 0).r(R_THIGH, 0.75f + sw * 0.55f, 0, 0).r(R_SHIN, -1.0f - sw * 0.6f - cw * 0.1f, 0, 0);
        }
        c = clip("vault", 0.35f).mask(ALL_MASK);
        s = c.key(0.00f); s.rootY = -1.2f; s.r(TORSO, -0.35f, 0, 0).r(L_THIGH, 1.25f, 0, 0).r(L_SHIN, -1.4f, 0, 0).r(R_THIGH, -0.3f, 0, 0).r(R_SHIN, -0.2f, 0, 0).r(L_UPPER_ARM, 0.9f, 0, -0.2f).r(R_UPPER_ARM, 0.6f, 0, 0.2f).r(L_FOREARM, 0.6f, 0, 0).r(R_FOREARM, 0.6f, 0, 0);
        s = c.key(1.00f); s.rootY = 0; s.r(TORSO, -0.05f, 0, 0).r(L_THIGH, 0.1f, 0, 0).r(L_SHIN, -0.2f, 0, 0).r(R_THIGH, 0, 0, 0).r(R_SHIN, -0.1f, 0, 0).r(L_UPPER_ARM, 0.1f, 0, -0.05f).r(R_UPPER_ARM, 0.1f, 0, 0.05f).r(L_FOREARM, 0.2f, 0, 0).r(R_FOREARM, 0.2f, 0, 0);
        c = clip("death", 0.3f).mask(ALL_MASK);
        c.key(0.00f);
        s = c.key(1.00f); s.rootY = -5.5f; s.r(TORSO, -0.55f, 0.1f, 0.12f).r(HEAD, -0.45f, 0, 0.2f).r(L_THIGH, 0.9f, 0, 0).r(L_SHIN, -1.6f, 0, 0).r(R_THIGH, 0.6f, 0, 0).r(R_SHIN, -1.3f, 0, 0).r(L_UPPER_ARM, 0.2f, 0, -0.3f).r(R_UPPER_ARM, 0.1f, 0, 0.3f);

        // ---- 0.6: jumping, sliding, sitting, racking a pump, getting into and out of a vehicle, feeding
        c = clip("jump", 0.5f).mask(ALL_MASK);
        s = c.key(0.00f); s.rootY = -1.6f; s.r(TORSO, -0.30f, 0, 0).r(L_THIGH, 0.85f, 0, 0).r(L_SHIN, -1.25f, 0, 0).r(R_THIGH, 0.70f, 0, 0).r(R_SHIN, -1.10f, 0, 0).r(L_UPPER_ARM, -0.5f, 0, -0.15f).r(R_UPPER_ARM, -0.4f, 0, 0.15f).r(L_FOREARM, 0.5f, 0, 0).r(R_FOREARM, 0.5f, 0, 0);
        s = c.key(0.35f); s.rootY = 0.6f; s.r(TORSO, 0.08f, 0, 0).r(L_THIGH, 1.05f, 0, 0).r(L_SHIN, -1.45f, 0, 0).r(R_THIGH, 0.35f, 0, 0).r(R_SHIN, -0.85f, 0, 0).r(L_UPPER_ARM, 1.0f, 0, -0.35f).r(R_UPPER_ARM, 0.8f, 0, 0.35f).r(L_FOREARM, 0.6f, 0, 0).r(R_FOREARM, 0.6f, 0, 0);
        s = c.key(0.75f); s.rootY = 0; s.r(TORSO, -0.12f, 0, 0).r(L_THIGH, 0.45f, 0, 0).r(L_SHIN, -0.55f, 0, 0).r(R_THIGH, 0.25f, 0, 0).r(R_SHIN, -0.45f, 0, 0).r(L_UPPER_ARM, 0.4f, 0, -0.2f).r(R_UPPER_ARM, 0.3f, 0, 0.2f).r(L_FOREARM, 0.4f, 0, 0).r(R_FOREARM, 0.4f, 0, 0);
        s = c.key(1.00f); s.rootY = -0.8f; s.r(TORSO, -0.18f, 0, 0).r(L_THIGH, 0.5f, 0, 0).r(L_SHIN, -0.8f, 0, 0).r(R_THIGH, 0.4f, 0, 0).r(R_SHIN, -0.7f, 0, 0).r(L_UPPER_ARM, 0.1f, 0, -0.05f).r(R_UPPER_ARM, 0.1f, 0, 0.05f).r(L_FOREARM, 0.2f, 0, 0).r(R_FOREARM, 0.2f, 0, 0);
        // the slide: hips almost on the ground, the leading leg out, the other folded under, the back leaning away from the direction of travel
        c = new AnimClip("slide_hold", 1f, true);
        CLIPS.put(c.name, c);
        s = c.key(0f); s.rootY = -7.4f; s.rootZ = 1.2f; s.r(TORSO, 0.62f, 0.12f, 0.06f).r(HEAD, -0.42f, 0, 0).r(L_THIGH, 1.42f, 0, 0.10f).r(L_SHIN, -0.22f, 0, 0).r(R_THIGH, 1.10f, 0, -0.22f).r(R_SHIN, -2.05f, 0, 0);
        c = new AnimClip("seated", 1f, true);
        CLIPS.put(c.name, c);
        s = c.key(0f); s.rootY = -5.6f; s.r(TORSO, 0.34f, 0, 0).r(HEAD, -0.28f, 0, 0).r(L_THIGH, 1.42f, 0, 0.10f).r(L_SHIN, -1.05f, 0, 0).r(R_THIGH, 1.36f, 0, -0.10f).r(R_SHIN, -0.90f, 0, 0);
        // racking a pump: the support hand runs the fore-end back to the receiver and forward again, the muzzle dips and recovers
        c = clip("pump", 0.55f).weapon().noRoot();
        w(c.key(0.00f), 2.0f, -3.4f, -5.0f, 0.10f, 0.14f, 0.12f, 0, 0, 0);
        w(c.key(0.30f), 2.0f, -3.5f, -4.4f, 0.24f, 0.16f, 0.20f, 0, 0, 2.9f);         // back: the spent shell is out
        w(c.key(0.42f), 2.0f, -3.5f, -4.4f, 0.24f, 0.16f, 0.20f, 0, 0, 2.9f);
        w(c.key(0.66f), 2.0f, -3.5f, -5.5f, 0.02f, 0.12f, 0.10f, 0, 0, -0.3f);        // home: the next one is in the chamber
        w(c.key(1.00f), 2.2f, -5.0f, -5.0f, -0.50f, 0.35f, 0.0f, 0, 0, 0);
        // into a vehicle: a hand to the roof, duck under it, a leg in, down onto the seat - and out again the other way
        c = clip("car_enter", 0.95f).mask(ALL_MASK);
        c.key(0.00f);
        s = c.key(0.30f); s.rootY = -1.4f; s.r(TORSO, -0.45f, 0.25f, 0).r(HEAD, -0.25f, 0, 0).r(R_UPPER_ARM, 1.5f, 0, 0.5f).r(R_FOREARM, 0.5f, 0, 0).r(L_UPPER_ARM, 0.4f, 0, -0.2f).r(L_FOREARM, 0.5f, 0, 0).r(L_THIGH, 0.35f, 0, 0).r(L_SHIN, -0.5f, 0, 0).r(R_THIGH, 0.2f, 0, 0).r(R_SHIN, -0.3f, 0, 0);
        s = c.key(0.62f); s.rootY = -3.8f; s.r(TORSO, -0.60f, 0.35f, 0.08f).r(HEAD, -0.30f, 0, 0).r(R_UPPER_ARM, 1.1f, 0, 0.4f).r(R_FOREARM, 0.8f, 0, 0).r(L_UPPER_ARM, 0.7f, 0, -0.2f).r(L_FOREARM, 0.7f, 0, 0).r(L_THIGH, 1.35f, 0, 0.2f).r(L_SHIN, -1.20f, 0, 0).r(R_THIGH, 0.55f, 0, 0).r(R_SHIN, -0.9f, 0, 0);
        s = c.key(1.00f); s.rootY = -5.6f; s.r(TORSO, 0.10f, 0, 0).r(HEAD, 0, 0, 0).r(R_UPPER_ARM, 0.9f, 0, 0.1f).r(R_FOREARM, 0.7f, 0, 0).r(L_UPPER_ARM, 0.9f, 0, -0.1f).r(L_FOREARM, 0.7f, 0, 0).r(L_THIGH, 1.50f, 0, 0.06f).r(L_SHIN, -1.45f, 0, 0).r(R_THIGH, 1.50f, 0, -0.06f).r(R_SHIN, -1.45f, 0, 0);
        c = clip("car_exit", 0.8f).mask(ALL_MASK);
        s = c.key(0.00f); s.rootY = -5.6f; s.r(TORSO, 0.10f, 0, 0).r(L_THIGH, 1.50f, 0, 0.06f).r(L_SHIN, -1.45f, 0, 0).r(R_THIGH, 1.50f, 0, -0.06f).r(R_SHIN, -1.45f, 0, 0).r(L_UPPER_ARM, 0.9f, 0, -0.1f).r(R_UPPER_ARM, 0.9f, 0, 0.1f).r(L_FOREARM, 0.7f, 0, 0).r(R_FOREARM, 0.7f, 0, 0);
        s = c.key(0.40f); s.rootY = -3.6f; s.r(TORSO, -0.55f, -0.30f, 0).r(HEAD, -0.2f, 0, 0).r(L_THIGH, 1.25f, 0, -0.2f).r(L_SHIN, -1.0f, 0, 0).r(R_THIGH, 0.7f, 0, 0).r(R_SHIN, -1.0f, 0, 0).r(L_UPPER_ARM, 1.2f, 0, -0.4f).r(R_UPPER_ARM, 0.6f, 0, 0.2f).r(L_FOREARM, 0.6f, 0, 0).r(R_FOREARM, 0.6f, 0, 0);
        s = c.key(0.75f); s.rootY = -1.0f; s.r(TORSO, -0.30f, -0.12f, 0).r(HEAD, 0, 0, 0).r(L_THIGH, 0.3f, 0, 0).r(L_SHIN, -0.4f, 0, 0).r(R_THIGH, 0.15f, 0, 0).r(R_SHIN, -0.3f, 0, 0).r(L_UPPER_ARM, 0.5f, 0, -0.2f).r(R_UPPER_ARM, 0.3f, 0, 0.1f).r(L_FOREARM, 0.4f, 0, 0).r(R_FOREARM, 0.4f, 0, 0);
        s = c.key(1.00f); s.rootY = 0; s.r(TORSO, -0.05f, 0, 0).r(L_THIGH, 0, 0, 0).r(L_SHIN, -0.1f, 0, 0).r(R_THIGH, 0, 0, 0).r(R_SHIN, -0.1f, 0, 0).r(L_UPPER_ARM, 0.1f, 0, -0.05f).r(R_UPPER_ARM, 0.1f, 0, 0.05f).r(L_FOREARM, 0.2f, 0, 0).r(R_FOREARM, 0.2f, 0, 0);
        // feeding: down on the knees over the body, hands tearing by turns, the head going down to it
        // (0.9: eight keys round one cycle, the last flowing into the first - it is held with Animator.hold and loops. The head
        // goes down to the body and comes away with a pull, twice a cycle; the hips barely move: it is kneeling, not bobbing.)
        c = new AnimClip("inf_feed", 1.7f, true).mask(ALL_MASK);
        CLIPS.put(c.name, c);
        for (int i = 0; i < 8; i++) {
            float a = i / 8f, sw = (float) Math.sin(a * TAU), cw = (float) Math.cos(a * TAU), s2 = (float) Math.sin(a * TAU * 2), pull = Math.max(0, s2);
            Pose k = c.key(a);
            k.rootY = -6.6f + sw * 0.10f;
            k.r(TORSO, -1.05f - cw * 0.10f + pull * 0.12f, sw * 0.12f, s2 * 0.03f).r(HEAD, -0.22f + cw * 0.16f + pull * 0.34f, sw * 0.20f, -s2 * 0.08f);
            k.r(L_UPPER_ARM, 1.15f + sw * 0.30f, 0, 0.10f).r(L_FOREARM, 0.55f - sw * 0.28f + pull * 0.2f, 0, 0).r(R_UPPER_ARM, 1.15f - sw * 0.30f, 0, -0.10f).r(R_FOREARM, 0.55f + sw * 0.28f + pull * 0.2f, 0, 0);
            k.r(L_THIGH, 1.20f, 0, 0.18f).r(L_SHIN, -2.30f, 0, 0).r(R_THIGH, 1.20f, 0, -0.18f).r(R_SHIN, -2.30f, 0, 0);
        }

        // ---- infected upper-body states and telegraphs
        c = new AnimClip("inf_reach", 1.1f, true).mask(UPPER_MASK).noRoot();
        CLIPS.put(c.name, c);
        c.key(0.0f).r(TORSO, -0.22f, 0, 0.04f).r(HEAD, 0.18f, 0, 0.12f).r(L_UPPER_ARM, 1.40f, 0, 0.10f).r(L_FOREARM, 0.20f, 0, 0).r(R_UPPER_ARM, 1.18f, 0, -0.06f).r(R_FOREARM, 0.34f, 0, 0);
        c.key(0.5f).r(TORSO, -0.26f, 0, -0.04f).r(HEAD, 0.14f, 0, 0.06f).r(L_UPPER_ARM, 1.30f, 0, 0.04f).r(L_FOREARM, 0.32f, 0, 0).r(R_UPPER_ARM, 1.30f, 0, -0.10f).r(R_FOREARM, 0.22f, 0, 0);
        // 1.0: going through a body on the ground - down beside it, both hands working, one then the other
        c = new AnimClip("loot_body", 1.2f, true).mask(UPPER_MASK).noRoot();
        CLIPS.put(c.name, c);
        c.key(0.0f).r(TORSO, -0.78f, 0.12f, 0).r(HEAD, 0.30f, 0, 0).r(R_UPPER_ARM, 0.95f, 0, 0.10f).r(R_FOREARM, 0.60f, 0, 0).r(L_UPPER_ARM, 0.55f, 0, -0.10f).r(L_FOREARM, 1.05f, 0, 0);
        c.key(0.5f).r(TORSO, -0.84f, -0.12f, 0).r(HEAD, 0.24f, 0, 0).r(R_UPPER_ARM, 0.55f, 0, 0.10f).r(R_FOREARM, 1.05f, 0, 0).r(L_UPPER_ARM, 0.95f, 0, -0.10f).r(L_FOREARM, 0.60f, 0, 0);
        c = new AnimClip("inf_droop", 2.6f, true).mask(UPPER_MASK).noRoot();
        CLIPS.put(c.name, c);
        c.key(0.0f).r(TORSO, -0.18f, 0, 0.07f).r(HEAD, -0.22f, 0, 0.24f).r(L_UPPER_ARM, 0.06f, 0, -0.05f).r(L_FOREARM, 0.14f, 0, 0).r(R_UPPER_ARM, 0.34f, 0, 0.05f).r(R_FOREARM, 0.50f, 0, 0);
        c.key(0.5f).r(TORSO, -0.14f, 0, 0.03f).r(HEAD, -0.16f, 0, 0.30f).r(L_UPPER_ARM, 0.12f, 0, -0.09f).r(L_FOREARM, 0.10f, 0, 0).r(R_UPPER_ARM, 0.28f, 0, 0.02f).r(R_FOREARM, 0.58f, 0, 0);
        c = clip("inf_attack", 0.55f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(TORSO, 0.18f, 0, 0).r(HEAD, 0.25f, 0, 0).r(L_UPPER_ARM, 2.2f, 0, -0.25f).r(L_FOREARM, 0.5f, 0, 0).r(R_UPPER_ARM, 2.4f, 0, 0.25f).r(R_FOREARM, 0.4f, 0, 0);
        c.key(0.40f).r(TORSO, -0.55f, 0, 0).r(HEAD, -0.1f, 0, 0).r(L_UPPER_ARM, 0.85f, 0, 0.2f).r(L_FOREARM, 0.2f, 0, 0).r(R_UPPER_ARM, 0.75f, 0, -0.2f).r(R_FOREARM, 0.2f, 0, 0);
        c.key(1.00f).r(TORSO, -0.22f, 0, 0).r(HEAD, 0.18f, 0, 0).r(L_UPPER_ARM, 1.35f, 0, 0.1f).r(L_FOREARM, 0.2f, 0, 0).r(R_UPPER_ARM, 1.2f, 0, -0.06f).r(R_FOREARM, 0.3f, 0, 0);
        c = clip("fat_retch", 1.0f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(TORSO, 0.02f, 0, 0).r(HEAD, 0.05f, 0, 0).r(L_UPPER_ARM, 0.2f, 0, -0.5f).r(R_UPPER_ARM, 0.2f, 0, 0.5f);
        c.key(0.70f).r(TORSO, 0.34f, 0, 0).r(HEAD, 0.70f, 0, 0).r(L_UPPER_ARM, -0.2f, 0, -1.0f).r(R_UPPER_ARM, -0.2f, 0, 1.0f).r(L_FOREARM, 0.4f, 0, 0).r(R_FOREARM, 0.4f, 0, 0);
        c.key(0.88f).r(TORSO, -0.55f, 0, 0).r(HEAD, -0.45f, 0, 0).r(L_UPPER_ARM, 0.5f, 0, -0.6f).r(R_UPPER_ARM, 0.5f, 0, 0.6f);
        c.key(1.00f).r(TORSO, -0.30f, 0, 0).r(HEAD, -0.2f, 0, 0).r(L_UPPER_ARM, 0.3f, 0, -0.4f).r(R_UPPER_ARM, 0.3f, 0, 0.4f);
        c = clip("fat_swell", 0.9f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(TORSO, 0.05f, 0, 0).r(HEAD, 0.1f, 0, 0).r(L_UPPER_ARM, 0.1f, 0, -0.4f).r(R_UPPER_ARM, 0.1f, 0, 0.4f);
        c.key(1.00f).r(TORSO, 0.42f, 0, 0).r(HEAD, 0.75f, 0, 0).r(L_UPPER_ARM, -0.3f, 0, -1.25f).r(R_UPPER_ARM, -0.3f, 0, 1.25f).r(L_FOREARM, 0.3f, 0, 0).r(R_FOREARM, 0.3f, 0, 0);
        c = clip("giant_windup", 1.1f).mask(UPPER_MASK);
        c.key(0.00f).r(TORSO, -0.2f, 0, 0);
        s = c.key(0.75f); s.rootY = -2.8f; s.r(TORSO, -0.62f, 0, 0).r(HEAD, 0.5f, 0, 0).r(L_UPPER_ARM, -0.9f, 0, -0.5f).r(R_UPPER_ARM, -0.9f, 0, 0.5f).r(L_FOREARM, 0.9f, 0, 0).r(R_FOREARM, 0.9f, 0, 0);
        s = c.key(1.00f); s.rootY = -1.4f; s.r(TORSO, -0.75f, 0, 0).r(HEAD, 0.6f, 0, 0).r(L_UPPER_ARM, 0.4f, 0, -0.6f).r(R_UPPER_ARM, 0.4f, 0, 0.6f);
        c = clip("giant_throw", 1.3f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(TORSO, -0.5f, 0.2f, 0).r(HEAD, 0.3f, 0, 0).r(R_UPPER_ARM, 0.6f, 0, 0.3f).r(R_FOREARM, 0.8f, 0, 0).r(L_UPPER_ARM, 0.6f, 0, -0.3f).r(L_FOREARM, 0.8f, 0, 0);
        c.key(0.70f).r(TORSO, 0.25f, -0.55f, 0).r(HEAD, 0.1f, 0.4f, 0).r(R_UPPER_ARM, 3.0f, 0, 0.25f).r(R_FOREARM, 0.9f, 0, 0).r(L_UPPER_ARM, 1.3f, 0, -0.3f).r(L_FOREARM, 0.2f, 0, 0);
        c.key(0.86f).r(TORSO, -0.5f, 0.5f, 0).r(HEAD, -0.1f, -0.3f, 0).r(R_UPPER_ARM, 1.1f, 0, 0.0f).r(R_FOREARM, 0.1f, 0, 0).r(L_UPPER_ARM, 0.0f, 0, -0.5f).r(L_FOREARM, 0.5f, 0, 0);
        c.key(1.00f).r(TORSO, -0.25f, 0, 0).r(HEAD, 0.1f, 0, 0).r(R_UPPER_ARM, 0.5f, 0, 0.3f).r(R_FOREARM, 0.5f, 0, 0).r(L_UPPER_ARM, 0.4f, 0, -0.3f).r(L_FOREARM, 0.5f, 0, 0);
        // 1.0: the Tyrant sets itself (the shoulder drops, the arm comes across); the Crawler rears back off its hands, jaw open
        c = clip("tyrant_set", 0.9f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(TORSO, 0, 0, 0).r(R_UPPER_ARM, 0.1f, 0, 0.2f).r(R_FOREARM, 0.2f, 0, 0).r(L_UPPER_ARM, 0.1f, 0, -0.2f).r(L_FOREARM, 0.2f, 0, 0).r(HEAD, 0, 0, 0);
        c.key(0.55f).r(TORSO, -0.34f, -0.5f, 0.06f).r(R_UPPER_ARM, 0.6f, 0, -0.1f).r(R_FOREARM, 1.5f, 0, 0).r(L_UPPER_ARM, -0.6f, 0, -0.3f).r(L_FOREARM, 0.8f, 0, 0).r(HEAD, 0.26f, 0.36f, 0);
        c.key(1.00f).r(TORSO, -0.38f, -0.52f, 0.06f).r(R_UPPER_ARM, 0.6f, 0, -0.1f).r(R_FOREARM, 1.5f, 0, 0).r(L_UPPER_ARM, -0.7f, 0, -0.3f).r(L_FOREARM, 0.9f, 0, 0).r(HEAD, 0.28f, 0.38f, 0);
        c = clip("crawler_rear", 0.55f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(TORSO, -1.24f, 0, 0).r(HEAD, 1.1f, 0, 0).r(L_UPPER_ARM, 1.5f, 0, -0.42f).r(R_UPPER_ARM, 1.5f, 0, 0.42f).r(L_FOREARM, 0.3f, 0, 0).r(R_FOREARM, 0.3f, 0, 0);
        c.key(0.60f).r(TORSO, -0.62f, 0, 0).r(HEAD, 0.25f, 0, 0).r(L_UPPER_ARM, 0.5f, 0, -0.8f).r(R_UPPER_ARM, 0.5f, 0, 0.8f).r(L_FOREARM, 1.1f, 0, 0).r(R_FOREARM, 1.1f, 0, 0);
        c.key(1.00f).r(TORSO, -0.50f, 0, 0).r(HEAD, 0.05f, 0, 0).r(L_UPPER_ARM, 0.3f, 0, -0.9f).r(R_UPPER_ARM, 0.3f, 0, 0.9f).r(L_FOREARM, 1.2f, 0, 0).r(R_FOREARM, 1.2f, 0, 0);
        c = clip("giant_smash", 0.8f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(TORSO, 0.2f, 0, 0).r(L_UPPER_ARM, 2.8f, 0, -0.3f).r(R_UPPER_ARM, 2.8f, 0, 0.3f).r(L_FOREARM, 0.6f, 0, 0).r(R_FOREARM, 0.6f, 0, 0);
        c.key(0.50f).r(TORSO, -0.7f, 0, 0).r(L_UPPER_ARM, 0.7f, 0, 0.1f).r(R_UPPER_ARM, 0.7f, 0, -0.1f).r(L_FOREARM, 0.1f, 0, 0).r(R_FOREARM, 0.1f, 0, 0);
        c.key(1.00f).r(TORSO, -0.25f, 0, 0).r(L_UPPER_ARM, 0.4f, 0, -0.3f).r(R_UPPER_ARM, 0.5f, 0, 0.3f).r(L_FOREARM, 0.5f, 0, 0).r(R_FOREARM, 0.5f, 0, 0);
        // ---- 0.8, Director Mode: gestures a person can be asked to make (director.StageManagers.PerformanceManager)
        c = clip("gest_wave", 1.6f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(R_UPPER_ARM, 0.2f, 0, 0.2f).r(R_FOREARM, 0.3f, 0, 0);
        c.key(0.20f).r(R_UPPER_ARM, 2.6f, 0, 0.55f).r(R_FOREARM, 0.5f, 0, 0).r(HEAD, 0.05f, -0.1f, 0);
        c.key(0.38f).r(R_UPPER_ARM, 2.7f, 0, 0.20f).r(R_FOREARM, 0.7f, 0, 0).r(HEAD, 0.05f, -0.1f, 0);
        c.key(0.56f).r(R_UPPER_ARM, 2.6f, 0, 0.60f).r(R_FOREARM, 0.5f, 0, 0).r(HEAD, 0.05f, -0.1f, 0);
        c.key(0.74f).r(R_UPPER_ARM, 2.7f, 0, 0.20f).r(R_FOREARM, 0.7f, 0, 0).r(HEAD, 0.05f, -0.1f, 0);
        c.key(1.00f).r(R_UPPER_ARM, 0.2f, 0, 0.2f).r(R_FOREARM, 0.3f, 0, 0);
        c = clip("gest_point", 1.4f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(R_UPPER_ARM, 0.2f, 0, 0.1f).r(R_FOREARM, 0.3f, 0, 0);
        c.key(0.22f).r(TORSO, -0.06f, -0.12f, 0).r(R_UPPER_ARM, 1.55f, 0, 0.05f).r(R_FOREARM, 0.05f, 0, 0).r(HEAD, 0.04f, 0.05f, 0);
        c.key(0.78f).r(TORSO, -0.06f, -0.12f, 0).r(R_UPPER_ARM, 1.58f, 0, 0.05f).r(R_FOREARM, 0.02f, 0, 0).r(HEAD, 0.04f, 0.05f, 0);
        c.key(1.00f).r(R_UPPER_ARM, 0.2f, 0, 0.1f).r(R_FOREARM, 0.3f, 0, 0);
        c = clip("gest_surrender", 2.4f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(L_UPPER_ARM, 0.2f, 0, -0.2f).r(R_UPPER_ARM, 0.2f, 0, 0.2f);
        c.key(0.18f).r(TORSO, 0.04f, 0, 0).r(HEAD, -0.08f, 0, 0).r(L_UPPER_ARM, 2.75f, 0, -0.55f).r(R_UPPER_ARM, 2.75f, 0, 0.55f).r(L_FOREARM, 0.45f, 0, 0).r(R_FOREARM, 0.45f, 0, 0);
        c.key(0.85f).r(TORSO, 0.04f, 0, 0).r(HEAD, -0.08f, 0, 0).r(L_UPPER_ARM, 2.70f, 0, -0.60f).r(R_UPPER_ARM, 2.70f, 0, 0.60f).r(L_FOREARM, 0.50f, 0, 0).r(R_FOREARM, 0.50f, 0, 0);
        c.key(1.00f).r(L_UPPER_ARM, 0.2f, 0, -0.2f).r(R_UPPER_ARM, 0.2f, 0, 0.2f);
        c = clip("gest_salute", 1.5f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(R_UPPER_ARM, 0.2f, 0, 0.1f).r(R_FOREARM, 0.3f, 0, 0);
        c.key(0.20f).r(TORSO, 0.06f, 0, 0).r(HEAD, -0.04f, 0, 0).r(R_UPPER_ARM, 1.35f, 0, 0.95f).r(R_FOREARM, 2.15f, 0, 0).r(L_UPPER_ARM, 0.02f, 0, -0.04f);
        c.key(0.75f).r(TORSO, 0.06f, 0, 0).r(HEAD, -0.04f, 0, 0).r(R_UPPER_ARM, 1.35f, 0, 0.95f).r(R_FOREARM, 2.15f, 0, 0).r(L_UPPER_ARM, 0.02f, 0, -0.04f);
        c.key(0.86f).r(R_UPPER_ARM, 0.9f, 0, 0.5f).r(R_FOREARM, 0.6f, 0, 0);
        c.key(1.00f).r(R_UPPER_ARM, 0.2f, 0, 0.1f).r(R_FOREARM, 0.3f, 0, 0);
        c = clip("gest_beckon", 1.5f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(R_UPPER_ARM, 0.2f, 0, 0.1f).r(R_FOREARM, 0.3f, 0, 0);
        c.key(0.20f).r(R_UPPER_ARM, 1.30f, 0, 0.15f).r(R_FOREARM, 0.30f, 0, 0);
        c.key(0.38f).r(R_UPPER_ARM, 1.15f, 0, 0.15f).r(R_FOREARM, 1.60f, 0, 0);
        c.key(0.56f).r(R_UPPER_ARM, 1.30f, 0, 0.15f).r(R_FOREARM, 0.30f, 0, 0);
        c.key(0.74f).r(R_UPPER_ARM, 1.15f, 0, 0.15f).r(R_FOREARM, 1.60f, 0, 0);
        c.key(1.00f).r(R_UPPER_ARM, 0.2f, 0, 0.1f).r(R_FOREARM, 0.3f, 0, 0);
        c = clip("gest_shrug", 1.3f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(L_UPPER_ARM, 0.2f, 0, -0.1f).r(R_UPPER_ARM, 0.2f, 0, 0.1f);
        c.key(0.30f).r(TORSO, 0.03f, 0, 0).r(HEAD, 0.0f, 0, 0.16f).r(L_UPPER_ARM, 0.45f, 0, -0.75f).r(R_UPPER_ARM, 0.45f, 0, 0.75f).r(L_FOREARM, 1.35f, 0, 0).r(R_FOREARM, 1.35f, 0, 0);
        c.key(0.70f).r(TORSO, 0.03f, 0, 0).r(HEAD, 0.0f, 0, 0.16f).r(L_UPPER_ARM, 0.45f, 0, -0.80f).r(R_UPPER_ARM, 0.45f, 0, 0.80f).r(L_FOREARM, 1.40f, 0, 0).r(R_FOREARM, 1.40f, 0, 0);
        c.key(1.00f).r(L_UPPER_ARM, 0.2f, 0, -0.1f).r(R_UPPER_ARM, 0.2f, 0, 0.1f);
        // ---- 0.8: the Gladiator and the Butcher have movements of their own (they borrowed the Tank's until now, and the
        // swings they were told to play - claw_swing, saw_swing - did not exist, so nothing moved when they struck).
        // The Gladiator: it hears something and stops - the head goes over to one side, then the other, the claws open
        c = clip("glad_hark", 0.9f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(TORSO, -0.16f, 0, 0).r(HEAD, 0.0f, 0, 0.22f).r(L_UPPER_ARM, 0.42f, 0, -0.5f).r(R_UPPER_ARM, 0.42f, 0, 0.5f).r(L_FOREARM, 0.75f, 0, 0).r(R_FOREARM, 0.75f, 0, 0);
        c.key(0.30f).r(TORSO, -0.02f, 0.18f, 0).r(HEAD, 0.20f, 0.45f, -0.42f).r(L_UPPER_ARM, 0.20f, 0, -0.85f).r(R_UPPER_ARM, 0.20f, 0, 0.85f).r(L_FOREARM, 0.35f, 0, 0).r(R_FOREARM, 0.35f, 0, 0);
        c.key(0.65f).r(TORSO, -0.02f, -0.18f, 0).r(HEAD, 0.24f, -0.50f, 0.42f).r(L_UPPER_ARM, 0.14f, 0, -0.95f).r(R_UPPER_ARM, 0.14f, 0, 0.95f).r(L_FOREARM, 0.25f, 0, 0).r(R_FOREARM, 0.25f, 0, 0);
        c.key(1.00f).r(TORSO, -0.20f, 0, 0).r(HEAD, 0.05f, 0, 0.22f).r(L_UPPER_ARM, 0.42f, 0, -0.5f).r(R_UPPER_ARM, 0.42f, 0, 0.5f).r(L_FOREARM, 0.75f, 0, 0).r(R_FOREARM, 0.75f, 0, 0);
        // ...the wind-up: both arms go out wide and back, the chest opens, the body rears - this is the moment to duck
        c = clip("claw_windup", 0.5f).mask(UPPER_MASK);
        c.key(0.00f).r(TORSO, -0.16f, 0, 0).r(L_UPPER_ARM, 0.42f, 0, -0.5f).r(R_UPPER_ARM, 0.42f, 0, 0.5f).r(L_FOREARM, 0.75f, 0, 0).r(R_FOREARM, 0.75f, 0, 0);
        s = c.key(0.70f); s.rootY = -0.8f; s.r(TORSO, 0.34f, 0, 0).r(HEAD, -0.30f, 0, 0).r(L_UPPER_ARM, -0.55f, 0, -1.45f).r(R_UPPER_ARM, -0.55f, 0, 1.45f).r(L_FOREARM, 0.30f, 0, 0).r(R_FOREARM, 0.30f, 0, 0).r(L_HAND, -0.5f, 0, 0).r(R_HAND, -0.5f, 0, 0);
        s = c.key(1.00f); s.rootY = -0.5f; s.r(TORSO, 0.42f, 0, 0).r(HEAD, -0.36f, 0, 0).r(L_UPPER_ARM, -0.70f, 0, -1.55f).r(R_UPPER_ARM, -0.70f, 0, 1.55f).r(L_FOREARM, 0.20f, 0, 0).r(R_FOREARM, 0.20f, 0, 0).r(L_HAND, -0.6f, 0, 0).r(R_HAND, -0.6f, 0, 0);
        // ...and the swing: the two arms come round and across at head height, the body thrown in behind them, then a
        // long recovery with the claws dragging back out - it has put everything into it and is open afterwards
        c = clip("claw_swing", 0.75f).mask(UPPER_MASK);
        s = c.key(0.00f); s.rootY = -0.5f; s.r(TORSO, 0.42f, 0, 0).r(HEAD, -0.36f, 0, 0).r(L_UPPER_ARM, -0.70f, 0, -1.55f).r(R_UPPER_ARM, -0.70f, 0, 1.55f).r(L_FOREARM, 0.20f, 0, 0).r(R_FOREARM, 0.20f, 0, 0);
        s = c.key(0.18f); s.rootY = -1.6f; s.rootZ = -2.2f; s.r(TORSO, -0.48f, 0.10f, 0).r(HEAD, 0.30f, 0, 0).r(L_UPPER_ARM, 1.55f, 0, 0.55f).r(R_UPPER_ARM, 1.55f, 0, -0.55f).r(L_FOREARM, 0.10f, 0, 0).r(R_FOREARM, 0.10f, 0, 0).r(L_HAND, 0.4f, 0, 0).r(R_HAND, 0.4f, 0, 0);
        s = c.key(0.32f); s.rootY = -2.0f; s.rootZ = -2.6f; s.r(TORSO, -0.66f, -0.10f, 0).r(HEAD, 0.42f, 0, 0).r(L_UPPER_ARM, 1.20f, 0, 0.95f).r(R_UPPER_ARM, 1.20f, 0, -0.95f).r(L_FOREARM, 0.55f, 0, 0).r(R_FOREARM, 0.55f, 0, 0);
        s = c.key(0.62f); s.rootY = -1.2f; s.rootZ = -1.2f; s.r(TORSO, -0.50f, 0, 0).r(HEAD, 0.30f, 0, 0.2f).r(L_UPPER_ARM, 0.80f, 0, 0.30f).r(R_UPPER_ARM, 0.80f, 0, -0.30f).r(L_FOREARM, 0.80f, 0, 0).r(R_FOREARM, 0.80f, 0, 0);
        c.key(1.00f).r(TORSO, -0.16f, 0, 0).r(HEAD, 0.0f, 0, 0.22f).r(L_UPPER_ARM, 0.42f, 0, -0.5f).r(R_UPPER_ARM, 0.42f, 0, 0.5f).r(L_FOREARM, 0.75f, 0, 0).r(R_FOREARM, 0.75f, 0, 0);
        // The Butcher. Revving: the saw is hauled up and back over the right shoulder with both hands, the body turning
        // away from the stroke it is about to make
        c = clip("saw_rev", 0.75f).mask(UPPER_MASK);
        c.key(0.00f).r(TORSO, -0.10f, 0, 0).r(R_UPPER_ARM, 0.62f, 0, 0.14f).r(R_FOREARM, 0.82f, 0, 0).r(L_UPPER_ARM, 0.1f, 0, -0.2f).r(L_FOREARM, 0.25f, 0, 0);
        s = c.key(0.55f); s.rootY = -0.6f; s.r(TORSO, 0.10f, -0.55f, -0.08f).r(HEAD, 0.05f, 0.45f, 0).r(R_UPPER_ARM, 1.95f, 0, 0.75f).r(R_FOREARM, 1.20f, 0, 0).r(L_UPPER_ARM, 1.60f, 0, 0.55f).r(L_FOREARM, 1.10f, 0, 0);
        s = c.key(1.00f); s.rootY = -0.9f; s.r(TORSO, 0.16f, -0.72f, -0.10f).r(HEAD, 0.05f, 0.60f, 0).r(R_UPPER_ARM, 2.15f, 0, 0.90f).r(R_FOREARM, 1.05f, 0, 0).r(L_UPPER_ARM, 1.75f, 0, 0.62f).r(L_FOREARM, 1.00f, 0, 0);
        // ...the stroke: a flat sweep at neck height from the right shoulder round to the left hip, the whole trunk
        // turning with it, a step into it, and the saw's own weight carrying it past the end before it is dragged back
        c = clip("saw_swing", 0.9f).mask(UPPER_MASK);
        s = c.key(0.00f); s.rootY = -0.9f; s.r(TORSO, 0.16f, -0.72f, -0.10f).r(HEAD, 0.05f, 0.60f, 0).r(R_UPPER_ARM, 2.15f, 0, 0.90f).r(R_FOREARM, 1.05f, 0, 0).r(L_UPPER_ARM, 1.75f, 0, 0.62f).r(L_FOREARM, 1.00f, 0, 0);
        s = c.key(0.16f); s.rootY = -1.4f; s.rootZ = -1.8f; s.r(TORSO, -0.20f, 0.10f, 0.06f).r(HEAD, 0.10f, -0.10f, 0).r(R_UPPER_ARM, 1.50f, 0, -0.10f).r(R_FOREARM, 0.25f, 0, 0).r(L_UPPER_ARM, 1.30f, 0, 0.30f).r(L_FOREARM, 0.60f, 0, 0);
        s = c.key(0.30f); s.rootY = -1.6f; s.rootZ = -2.2f; s.r(TORSO, -0.34f, 0.85f, 0.14f).r(HEAD, 0.16f, -0.60f, 0).r(R_UPPER_ARM, 1.10f, 0, -0.85f).r(R_FOREARM, 0.15f, 0, 0).r(L_UPPER_ARM, 0.70f, 0, -0.25f).r(L_FOREARM, 0.50f, 0, 0);
        s = c.key(0.48f); s.rootY = -1.5f; s.rootZ = -1.6f; s.r(TORSO, -0.40f, 1.05f, 0.18f).r(HEAD, 0.20f, -0.70f, 0).r(R_UPPER_ARM, 0.70f, 0, -1.00f).r(R_FOREARM, 0.30f, 0, 0).r(L_UPPER_ARM, 0.40f, 0, -0.40f).r(L_FOREARM, 0.45f, 0, 0);
        s = c.key(0.78f); s.rootY = -0.7f; s.rootZ = -0.6f; s.r(TORSO, -0.24f, 0.40f, 0.06f).r(HEAD, 0.10f, -0.25f, 0).r(R_UPPER_ARM, 0.50f, 0, -0.30f).r(R_FOREARM, 0.70f, 0, 0).r(L_UPPER_ARM, 0.20f, 0, -0.25f).r(L_FOREARM, 0.35f, 0, 0);
        c.key(1.00f).r(TORSO, -0.10f, 0, 0).r(R_UPPER_ARM, 0.62f, 0, 0.14f).r(R_FOREARM, 0.82f, 0, 0).r(L_UPPER_ARM, 0.1f, 0, -0.2f).r(L_FOREARM, 0.25f, 0, 0);
        // ...and what a bullet in the saw does to him: the arm is thrown wide and the body folds over the stalled engine
        c = clip("saw_stall", 1.2f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(TORSO, 0.20f, 0.3f, 0).r(HEAD, -0.2f, 0, 0).r(R_UPPER_ARM, 1.0f, 0, 1.2f).r(R_FOREARM, 0.2f, 0, 0).r(L_UPPER_ARM, 0.4f, 0, -0.6f);
        c.key(0.30f).r(TORSO, -0.55f, -0.2f, 0.1f).r(HEAD, 0.45f, 0, 0).r(R_UPPER_ARM, 0.25f, 0, 0.35f).r(R_FOREARM, 1.10f, 0, 0).r(L_UPPER_ARM, 0.85f, 0, 0.35f).r(L_FOREARM, 1.20f, 0, 0);
        c.key(0.55f).r(TORSO, -0.62f, -0.1f, 0.1f).r(HEAD, 0.50f, 0, 0).r(R_UPPER_ARM, 0.20f, 0, 0.30f).r(R_FOREARM, 1.20f, 0, 0).r(L_UPPER_ARM, 1.30f, 0, 0.40f).r(L_FOREARM, 0.50f, 0, 0);   // the pull on the cord
        c.key(0.70f).r(TORSO, -0.50f, -0.2f, 0.1f).r(HEAD, 0.40f, 0, 0).r(R_UPPER_ARM, 0.25f, 0, 0.30f).r(R_FOREARM, 1.15f, 0, 0).r(L_UPPER_ARM, 0.70f, 0, 0.35f).r(L_FOREARM, 1.30f, 0, 0);
        c.key(0.86f).r(TORSO, -0.62f, -0.1f, 0.1f).r(HEAD, 0.50f, 0, 0).r(R_UPPER_ARM, 0.20f, 0, 0.30f).r(R_FOREARM, 1.20f, 0, 0).r(L_UPPER_ARM, 1.35f, 0, 0.40f).r(L_FOREARM, 0.45f, 0, 0);   // and again
        c.key(1.00f).r(TORSO, -0.10f, 0, 0).r(R_UPPER_ARM, 0.62f, 0, 0.14f).r(R_FOREARM, 0.82f, 0, 0).r(L_UPPER_ARM, 0.1f, 0, -0.2f).r(L_FOREARM, 0.25f, 0, 0);
        c = clip("inf_shriek", 0.8f).mask(UPPER_MASK).noRoot();
        c.key(0.00f).r(TORSO, -0.2f, 0, 0).r(HEAD, 0.1f, 0, 0);
        c.key(0.35f).r(TORSO, 0.30f, 0, 0).r(HEAD, 0.75f, 0, 0).r(L_UPPER_ARM, -0.4f, 0, -0.9f).r(R_UPPER_ARM, -0.4f, 0, 0.9f).r(L_FOREARM, 0.7f, 0, 0).r(R_FOREARM, 0.7f, 0, 0);
        c.key(0.80f).r(TORSO, 0.26f, 0, 0).r(HEAD, 0.70f, 0, 0).r(L_UPPER_ARM, -0.3f, 0, -0.8f).r(R_UPPER_ARM, -0.3f, 0, 0.8f).r(L_FOREARM, 0.7f, 0, 0).r(R_FOREARM, 0.7f, 0, 0);
        c.key(1.00f).r(TORSO, -0.3f, 0, 0).r(HEAD, 0.2f, 0, 0).r(L_UPPER_ARM, 1.2f, 0, 0).r(R_UPPER_ARM, 1.2f, 0, 0).r(L_FOREARM, 0.2f, 0, 0).r(R_FOREARM, 0.2f, 0, 0);
    }

    private static AnimClip clip(String name, float dur) {
        AnimClip c = new AnimClip(name, dur, false).mask(UPPER_MASK);
        CLIPS.put(name, c);
        return c;
    }

    private static Pose w(Pose p, float x, float y, float z, float pitch, float yaw, float roll, float lx, float ly, float lz) {
        p.wpn[0] = x; p.wpn[1] = y; p.wpn[2] = z; p.wpn[3] = pitch; p.wpn[4] = yaw; p.wpn[5] = roll; p.wpn[6] = lx; p.wpn[7] = ly; p.wpn[8] = lz;
        p.lhOnWeapon = 1;
        return p;
    }

    /** Marks the support hand as free and gives it an aim-frame target. */
    private static Pose free(Pose p, float x, float y, float z) { p.lhOnWeapon = 0; p.wpn[6] = x; p.wpn[7] = y; p.wpn[8] = z; return p; }

    private static void hold(String name, float x, float y, float z, float pitch, float yaw, float roll) {
        AnimClip c = new AnimClip(name, 1, true).mask(0).weapon().noRoot();
        w(c.key(0), x, y, z, pitch, yaw, roll, 0, 0, 0);
        CLIPS.put(name, c);
    }

    /** Foot position at a leg phase: {forward, height}. Stance runs front to back at constant speed. */
    public static void foot(Gait g, float ph, float[] out) {
        ph -= (float) Math.floor(ph);
        if (g.travel() <= 0) { out[0] = 0; out[1] = 0; return; }
        if (ph < g.duty()) { out[0] = g.travel() / 2 - g.travel() * (ph / g.duty()); out[1] = 0; return; }
        float s = (ph - g.duty()) / (1 - g.duty());
        out[0] = -g.travel() / 2 * (float) Math.cos(Math.PI * s);
        out[1] = g.lift() * (float) Math.sin(Math.PI * s) * (0.75f + 0.25f * (float) Math.sin(Math.PI * Math.min(1, s * 1.4f)));
    }

    /** Bakes one gait cycle into keyframes: legs by IK, hips by reach, arms/torso by counter-swing. */
    private static void bake(String name, Gait g, int n, boolean breathing) {
        AnimClip c = new AnimClip(name, 1, true);
        c.travel = g.travel();
        c.duty = g.duty();
        float[] hips = new float[n], fl = new float[2], fr = new float[2], ik = new float[2];
        float reach = LEG * 0.985f;
        for (int i = 0; i < n; i++) {
            float ph = i / (float) n, hy = g.hip();
            foot(g, ph, fl); foot(g, ph + 0.5f, fr);
            fl[0] += g.stagger(); fr[0] -= g.stagger();
            for (float[] ft : new float[][]{fl, fr}) if (ft[1] < 0.05f) hy = Math.min(hy, (float) Math.sqrt(Math.max(9, reach * reach - ft[0] * ft[0])));
            hips[i] = hy;
        }
        for (int i = 0; i < n; i++) {
            float ph = i / (float) n, a = ph * TAU, cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            float hy = n > 8 ? (hips[(i + n - 1) % n] + hips[i] * 2 + hips[(i + 1) % n]) / 4f : hips[i];
            hy = Math.min(hy, hips[i]);
            Pose k = c.key(ph).clear();
            foot(g, ph, fl); foot(g, ph + 0.5f, fr);
            fl[0] += g.stagger(); fr[0] -= g.stagger();
            float breathe = breathing ? sn * 0.014f : 0;
            k.rootY = hy - HIP_Y + (breathing ? sn * 0.06f : 0);
            k.rootRoll = -g.sway() * 0.5f * sn;
            legIk(fl[0], hy - fl[1], ik); k.r(L_THIGH, ik[0], 0, 0).r(L_SHIN, ik[1], 0, 0);
            legIk(fr[0], hy - fr[1], ik); k.r(R_THIGH, ik[0], 0, 0).r(R_SHIN, ik[1], 0, 0);
            k.r(TORSO, g.lean() + breathe, g.twist() * cs, g.sway() * sn);
            k.r(HEAD, -g.lean() * 0.7f - breathe, -g.twist() * 0.7f * cs, -g.sway() * 0.6f * sn);
            float sw = g.armSwing();
            k.r(L_UPPER_ARM, -sw * cs - g.lean() * 0.5f, 0, -0.07f).r(R_UPPER_ARM, sw * cs - g.lean() * 0.5f, 0, 0.07f);
            k.r(L_FOREARM, g.armBend() + 0.25f * sw * Math.max(0, -cs), 0, 0).r(R_FOREARM, g.armBend() + 0.25f * sw * Math.max(0, cs), 0, 0);
        }
        CLIPS.put(name, c);
    }
}
