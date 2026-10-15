package absentialis.director;

import absentialis.character.Rig;
import absentialis.engine.math.Mat4;
import absentialis.entity.Actor;
import absentialis.gameplay.items.ItemInstance;
import absentialis.scene.ActorRenderer;
import absentialis.scene.VehicleSystem;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The scene tape (0.9): what a recording keeps besides its picture, so that the take can be looked at again from
 * somewhere else.
 *
 * A clip's frames are footage - the picture as the gameplay camera saw it. Beside each of them the tape keeps where
 * everybody was: for every character that was drawn, the matrix of each of its bones and of the weapon in its hand,
 * exactly as the renderer had them (so a walk, a ragdoll, a creature feeding and a body at a wheel are all the same
 * thing to it: seventeen matrices), where each vehicle stood, and where the camera was. Played back, the characters
 * of the scene are stood where the tape says and drawn with the bones the tape holds, the world is drawn round them
 * as it is, and the camera can be anywhere - which is what "changing the perspective in the editor" means. The
 * editor then films that from the camera it has chosen: a re-shoot, which is a clip like any other.
 *
 * A tape lives in memory and belongs to the session it was made in: it refers to the characters themselves. A clip
 * from an earlier session is footage only. What a tape does not hold: particles, tracers, muzzle flash, blood as it
 * flew, doors swinging, blocks as they broke - a re-shoot shows the world as it stands when it is re-shot, with the
 * people and vehicles of the take moving through it.
 */
public final class SceneTape implements ActorRenderer.BoneSink, ActorRenderer.BoneSource {
    /** Matrices per character: one per bone group, and the weapon's. */
    public static final int MATS = Rig.GROUPS + 1;
    /** Floats the tapes of a session may hold between them before taping stops (about 110 MB). */
    public static long BUDGET = 28_000_000L;
    private static long held;

    /** One moment of the scene. */
    public static final class Frame {
        Actor[] who;
        ItemInstance[] weapon;
        boolean[] dead, armed;
        float[] place;          // x, y, z, yaw, pitch per character
        float[] mats;           // MATS matrices per character
        VehicleSystem.Car[] cars;
        float[] carState;       // px py pz qw qx qy qz + r[9] per car
        float[] cam;            // x y z yaw pitch fov, as the gameplay camera had it
        int player = -1;        // index of the controlled character
    }

    public final List<Frame> frames = new ArrayList<>();
    /** True when the budget ran out part-way through the take: the frames after that have no tape. */
    public boolean truncated;

    // ---- the frame being gathered (every rendered frame; kept only when the recorder takes a picture)
    private final List<Actor> pWho = new ArrayList<>();
    private final Map<Actor, Integer> pIndex = new IdentityHashMap<>();
    private float[] pMats = new float[MATS * 16 * 24];
    private boolean[] pArmed = new boolean[24];
    private final float[] pCam = new float[6];
    private Actor pPlayer;

    public int size() { return frames.size(); }
    public static void forgetAll() { held = 0; }

    /** A new rendered frame begins: what was gathered for the last one is let go. */
    public void beginFrame(Actor player, float camX, float camY, float camZ, float yaw, float pitch, float fov) {
        pWho.clear(); pIndex.clear();
        pPlayer = player;
        pCam[0] = camX; pCam[1] = camY; pCam[2] = camZ; pCam[3] = yaw; pCam[4] = pitch; pCam[5] = fov;
    }

    /** The renderer has just worked out a character's bones (and its weapon's place, or null). */
    @Override public void bones(Actor a, Mat4[] bones, Mat4 weapon) {
        Integer at = pIndex.get(a);
        int i = at != null ? at : pWho.size();
        if (at == null) { pWho.add(a); pIndex.put(a, i); }
        if ((i + 1) * MATS * 16 > pMats.length) { pMats = java.util.Arrays.copyOf(pMats, pMats.length * 2); pArmed = java.util.Arrays.copyOf(pArmed, pArmed.length * 2); }
        int o = i * MATS * 16;
        for (int g = 0; g < Rig.GROUPS; g++) System.arraycopy(bones[g].m, 0, pMats, o + g * 16, 16);
        pArmed[i] = weapon != null;
        if (weapon != null) System.arraycopy(weapon.m, 0, pMats, o + Rig.GROUPS * 16, 16);
    }

    /** The recorder took a picture this frame: what was gathered becomes the tape's next frame. */
    public void commit(List<VehicleSystem.Car> cars) {
        int n = pWho.size();
        long cost = (long) n * (MATS * 16 + 5) + cars.size() * 16L + 8;
        if (truncated || held + cost > BUDGET) { truncated = true; return; }
        held += cost;
        Frame f = new Frame();
        f.who = pWho.toArray(new Actor[0]);
        f.weapon = new ItemInstance[n]; f.dead = new boolean[n]; f.armed = java.util.Arrays.copyOf(pArmed, n);
        f.place = new float[n * 5];
        f.mats = java.util.Arrays.copyOf(pMats, n * MATS * 16);
        for (int i = 0; i < n; i++) {
            Actor a = f.who[i];
            f.weapon[i] = a.weapon; f.dead[i] = a.dead;
            f.place[i * 5] = a.x; f.place[i * 5 + 1] = a.y; f.place[i * 5 + 2] = a.z; f.place[i * 5 + 3] = a.yaw; f.place[i * 5 + 4] = a.pitch;
            if (a == pPlayer) f.player = i;
        }
        f.cars = cars.toArray(new VehicleSystem.Car[0]);
        f.carState = new float[f.cars.length * 16];
        for (int i = 0; i < f.cars.length; i++) carOut(f.cars[i], f.carState, i * 16);
        f.cam = pCam.clone();
        frames.add(f);
    }

    private static void carOut(VehicleSystem.Car c, float[] s, int o) {
        var b = c.sim.body;
        s[o] = b.px; s[o + 1] = b.py; s[o + 2] = b.pz; s[o + 3] = b.qw; s[o + 4] = b.qx; s[o + 5] = b.qy; s[o + 6] = b.qz;
        System.arraycopy(b.r, 0, s, o + 7, 9);
    }

    private static void carIn(VehicleSystem.Car c, float[] s, int o) {
        var b = c.sim.body;
        b.px = s[o]; b.py = s[o + 1]; b.pz = s[o + 2]; b.qw = s[o + 3]; b.qx = s[o + 4]; b.qy = s[o + 5]; b.qz = s[o + 6];
        System.arraycopy(s, o + 7, b.r, 0, 9);
    }

    // ---- playing it back: the characters and vehicles of the take are stood where the tape has them, drawn, and put back
    private Frame playing;
    private final Map<Actor, Integer> playIndex = new IdentityHashMap<>();
    private float[] savedPlace = new float[0], savedCars = new float[0];
    private boolean[] savedDead = new boolean[0];
    private ItemInstance[] savedWeapon = new ItemInstance[0];

    public Frame frame(int i) { return frames.isEmpty() ? null : frames.get(Math.max(0, Math.min(frames.size() - 1, i))); }
    public boolean applied() { return playing != null; }

    /** Where the controlled character stood on a frame: {x, y, z, yaw}, or null. */
    public float[] subject(int i) {
        Frame f = frame(i);
        if (f == null || f.player < 0) return null;
        return new float[]{f.place[f.player * 5], f.place[f.player * 5 + 1], f.place[f.player * 5 + 2], f.place[f.player * 5 + 3]};
    }

    public float[] camera(int i) { Frame f = frame(i); return f == null ? null : f.cam; }

    /** Stands the scene as it was on a frame. Everything it moves is remembered; {@link #restore} must follow before the world runs again. */
    public void apply(int i) {
        if (playing != null) restore();
        Frame f = frame(i);
        if (f == null) return;
        playing = f;
        int n = f.who.length;
        if (savedPlace.length < n * 5) { savedPlace = new float[n * 5]; savedDead = new boolean[n]; savedWeapon = new ItemInstance[n]; }
        playIndex.clear();
        for (int k = 0; k < n; k++) {
            Actor a = f.who[k];
            playIndex.put(a, k);
            savedPlace[k * 5] = a.x; savedPlace[k * 5 + 1] = a.y; savedPlace[k * 5 + 2] = a.z; savedPlace[k * 5 + 3] = a.yaw; savedPlace[k * 5 + 4] = a.pitch;
            savedDead[k] = a.dead; savedWeapon[k] = a.weapon;
            a.x = f.place[k * 5]; a.y = f.place[k * 5 + 1]; a.z = f.place[k * 5 + 2]; a.yaw = f.place[k * 5 + 3]; a.pitch = f.place[k * 5 + 4];
            // whoever is drawn from the tape is drawn as they were: the bones say whether they were standing or lying dead
            a.dead = k == f.player ? false : f.dead[k];
            a.weapon = f.weapon[k];
        }
        if (savedCars.length < f.cars.length * 16) savedCars = new float[f.cars.length * 16];
        for (int k = 0; k < f.cars.length; k++) { carOut(f.cars[k], savedCars, k * 16); carIn(f.cars[k], f.carState, k * 16); }
    }

    public void restore() {
        Frame f = playing;
        if (f == null) return;
        for (int k = 0; k < f.who.length; k++) {
            Actor a = f.who[k];
            a.x = savedPlace[k * 5]; a.y = savedPlace[k * 5 + 1]; a.z = savedPlace[k * 5 + 2]; a.yaw = savedPlace[k * 5 + 3]; a.pitch = savedPlace[k * 5 + 4];
            a.dead = savedDead[k]; a.weapon = savedWeapon[k];
        }
        for (int k = 0; k < f.cars.length; k++) carIn(f.cars[k], savedCars, k * 16);
        playing = null;
        playIndex.clear();
    }

    /** The renderer asks for a character's bones: 0 it was not in the scene at this moment, 1 its bones, 2 its bones and its weapon's place. */
    @Override public int bones(Actor a, Mat4[] bones, Mat4 weapon, boolean ignored) {
        Frame f = playing;
        Integer k = f == null ? null : playIndex.get(a);
        if (k == null) return 0;
        int o = k * MATS * 16;
        for (int g = 0; g < Rig.GROUPS; g++) System.arraycopy(f.mats, o + g * 16, bones[g].m, 0, 16);
        if (!f.armed[k]) return 1;
        System.arraycopy(f.mats, o + Rig.GROUPS * 16, weapon.m, 0, 16);
        return 2;
    }
}
