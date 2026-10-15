package absentialis.scene;

import absentialis.engine.events.Events;
import absentialis.engine.math.Mat4;
import absentialis.engine.render.ModelRenderer;
import absentialis.engine.render.ParticleBatch;
import absentialis.entity.Actor;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.List;

/**
 * What a shot leaves in the air and on the ground (0.7): the spent case, and the smoke at the muzzle.
 *
 * <b>Cases.</b> Every weapon throws its brass its own way - that is most of what makes one gun look unlike another
 * when it fires. A handgun flicks a small case up and to the right. A pump gun keeps its hull until the fore-end
 * is worked, then drops a fat red one lazily beside the shooter; a self-loading shotgun spits it at once. A bolt
 * rifle holds the case until the bolt is drawn and lifts it out slowly. The semi-automatic rifle lays its cases
 * to the right rear; the automatic hurls them forward, far, one after another; the SMG pours small ones down past
 * the hand. Revolvers and the crossbow leave nothing. Cases are voxels with weight: they tumble, hit the ground,
 * bounce, ring, roll a little and lie there.
 *
 * <b>Smoke.</b> A shot puts a puff at the muzzle that the round's own gas throws forward; then, for a second or
 * so, a wisp keeps rising <i>from the barrel</i> - it is emitted at wherever the muzzle is each frame, so it trails
 * the weapon as it moves. The amount is the weapon's: a shotgun or a magnum hangs a cloud, a suppressed gun only a
 * thread.
 *
 * Driven entirely by {@link Events.ShotFired}, so it is the same for the player and for everybody else.
 */
public final class GunFx {
    /** How a weapon gets rid of its case: velocity in the shooter's frame (right, up, forward) in m/s, scatter, spin, delay, and what the case is. */
    public record Eject(float right, float up, float forward, float scatter, float spin, float delay, float size, float length, int color, int head, boolean none) {}

    private static final int BRASS = 0xFFC8A040, BRASS_DK = 0xFF9A7626, HULL = 0xFFB4281E, STEEL = 0xFF9A9CA0;
    private static final Eject NONE = new Eject(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, true);

    /** The ejection of a weapon, from what it is. Tunable in one place; nothing else in the game repeats these numbers. */
    public static Eject ejectOf(ItemDef d) {
        if (d == null || d.weapon == null || d.weapon.melee()) return NONE;
        ItemDef.Weapon w = d.weapon;
        String model = w.model == null ? "" : w.model, mode = w.modes.isEmpty() ? "semi" : w.modes.get(0);
        return switch (w.family) {
            case "special" -> NONE;                                                     // a crossbow has nothing to throw
            case "handgun" -> model.equals("yellow9") ? new Eject(1.6f, 3.0f, -0.2f, 0.5f, 22, 0.02f, 0.020f, 0.036f, BRASS, BRASS_DK, false)
                    : model.equals("whitetail") ? new Eject(2.6f, 1.6f, 0.3f, 0.6f, 30, 0.02f, 0.018f, 0.030f, BRASS, BRASS_DK, false)
                    : model.equals("matilda") ? new Eject(2.0f, 2.4f, 0.6f, 0.5f, 26, 0.02f, 0.020f, 0.034f, BRASS, BRASS_DK, false)
                    : new Eject(2.2f, 2.2f, 0.0f, 0.55f, 26, 0.02f, 0.019f, 0.032f, BRASS, BRASS_DK, false);
            case "magnum" -> model.equals("deagle") ? new Eject(1.4f, 3.6f, -0.8f, 0.5f, 18, 0.03f, 0.026f, 0.050f, BRASS, BRASS_DK, false) : NONE;   // the revolvers keep theirs
            case "shotgun" -> "pump".equals(mode) ? new Eject(1.3f, 0.9f, 0.1f, 0.35f, 9, 0.36f, 0.036f, 0.074f, HULL, BRASS, false)
                    : model.equals("striker") ? new Eject(2.4f, 1.2f, 1.6f, 0.6f, 16, 0.03f, 0.036f, 0.074f, HULL, BRASS, false)
                    : new Eject(2.8f, 1.4f, 0.4f, 0.5f, 14, 0.03f, 0.036f, 0.074f, HULL, BRASS, false);
            case "rifle" -> "bolt".equals(mode) ? new Eject(0.9f, 1.9f, -0.3f, 0.3f, 7, 0.46f, 0.024f, 0.070f, BRASS, BRASS_DK, false)
                    : new Eject(3.0f, 1.3f, -1.2f, 0.6f, 24, 0.02f, 0.022f, 0.056f, BRASS, BRASS_DK, false);
            case "auto" -> model.equals("smg") ? new Eject(1.9f, 0.5f, 0.2f, 0.7f, 34, 0.01f, 0.017f, 0.028f, BRASS, BRASS_DK, false)
                    : model.equals("tommy") ? new Eject(1.2f, 2.8f, 0.2f, 0.6f, 24, 0.01f, 0.021f, 0.034f, BRASS, BRASS_DK, false)
                    : new Eject(3.4f, 1.5f, 2.6f, 0.8f, 30, 0.01f, 0.022f, 0.052f, STEEL, 0xFF6E5A30, false);       // steel-cased, thrown forward and far
            default -> new Eject(2.2f, 1.6f, 0.2f, 0.6f, 24, 0.02f, 0.022f, 0.05f, BRASS, BRASS_DK, false);
        };
    }

    /** Smoke of one shot: {puffs at the muzzle, seconds the barrel keeps smoking, how big a puff starts, how dense}. */
    public static float[] smokeOf(ItemDef d, boolean suppressed) {
        if (d == null || d.weapon == null || d.weapon.melee() || "special".equals(d.weapon.family)) return new float[]{0, 0, 0, 0};
        float[] s = switch (d.weapon.family) {
            case "shotgun" -> new float[]{9, 1.5f, 0.16f, 0.34f};
            case "magnum" -> new float[]{7, 1.3f, 0.14f, 0.30f};
            case "rifle" -> new float[]{5, 1.1f, 0.12f, 0.26f};
            case "auto" -> new float[]{3, 0.9f, 0.09f, 0.20f};
            default -> new float[]{3, 0.8f, 0.08f, 0.20f};
        };
        if (suppressed) { s[0] = 1; s[2] *= 0.6f; s[3] *= 0.55f; s[1] *= 1.2f; }
        return s;
    }

    private static final class Case { float x, y, z, vx, vy, vz, yaw, pitch, spin, life, size, len, rest; int color, head; boolean heard; }
    private static final class Pending { int actor; float t; Eject e; float dirX, dirZ; }
    private static final class Puff { float x, y, z, vx, vy, vz, size, age, life, dens, seed; }
    private static final class Barrel { int actor; float until, rate, acc, size, dens; }

    private final Raid raid;
    private final List<Case> cases = new ArrayList<>();
    private final List<Pending> pending = new ArrayList<>();
    private final List<Puff> puffs = new ArrayList<>();
    private final List<Barrel> barrels = new ArrayList<>();
    /** Where a character's muzzle and ejection port are right now: {mx, my, mz, px, py, pz}. Supplied by the screen, which knows the poses. */
    public interface Ports { boolean locate(Actor a, float[] out); }
    public Ports ports;
    /** A case landed where the player can hear it: (x, y, z, hull?) */
    public interface Ring { void ring(float x, float y, float z, boolean hull); }
    public Ring ring;
    /** Limits (performance): cases lying about, smoke puffs alive. Oldest go first. */
    public static int MAX_CASES = 260, MAX_PUFFS = 420;
    public static float CASE_LIFE = 28f;
    /** Counters for the gates. */
    public int ejected, puffed, landed;
    private final float[] port = new float[6];
    private final java.util.Random rnd = new java.util.Random(7);

    public GunFx(Raid raid) { this.raid = raid; }

    public int cases() { return cases.size(); }
    public int puffs() { return raid.fx.count(absentialis.engine.fx.ParticleSystem.SMOKE); }

    /** A round has been fired by someone. */
    public void onShot(Events.ShotFired e) {
        ItemDef d = Content.def(e.weaponId());
        Actor a = raid.actor(e.shooterId());
        if (d == null || a == null) return;
        // far-off fire is heard, not watched: nothing is simulated for it
        if (a.distXZ(raid.player.x, raid.player.z) > 70) return;
        Eject ej = ejectOf(d);
        if (!ej.none()) { Pending p = new Pending(); p.actor = a.id; p.t = ej.delay(); p.e = ej; p.dirX = e.dirX(); p.dirZ = e.dirZ(); pending.add(p); }
        float[] sm = smokeOf(d, e.suppressed());
        if (sm[0] <= 0 || !locate(a, e.x(), e.y(), e.z(), e.dirX(), e.dirZ())) return;
        float fx = e.dirX(), fz = e.dirZ(), fy = (float) Math.sin(a.pitch);
        // 1.0: the flash, the spit of sparks, the gas ring and the dust the blast lifts - the Astratto particle system's muzzle
        float power = switch (d.weapon.family) { case "shotgun", "magnum" -> 1.55f; case "rifle" -> 1.2f; case "auto" -> 0.95f; default -> 0.7f; };
        float fl = (float) Math.sqrt(fx * fx + fz * fz);
        float hk = (float) Math.cos(a.pitch) / Math.max(1e-3f, fl);
        raid.fx.muzzle(port[0], port[1], port[2], fx * hk, fy, fz * hk, power, e.suppressed(), raid.groundY((int) Math.floor(port[0]), (int) Math.floor(port[2])));
        for (int i = 0; i < (int) sm[0]; i++) {
            float sp = 1.6f + rnd.nextFloat() * 3.2f;
            puff(port[0], port[1], port[2], fx * sp + (rnd.nextFloat() - 0.5f) * 0.9f, fy * sp + 0.25f + rnd.nextFloat() * 0.5f, fz * sp + (rnd.nextFloat() - 0.5f) * 0.9f, sm[2] * (0.7f + rnd.nextFloat() * 0.7f), 0.7f + rnd.nextFloat() * 0.9f, sm[3]);
        }
        Barrel b = null;
        for (Barrel o : barrels) if (o.actor == a.id) b = o;
        if (b == null) { b = new Barrel(); b.actor = a.id; barrels.add(b); }
        // a barrel that is fired again before it has cleared smokes for longer and more thickly: sustained fire shows
        b.until = Math.min(raid.time + 3.2f, Math.max(b.until, raid.time) + sm[1] * 0.6f);
        b.rate = Math.min(34, Math.max(b.rate * 0.7f, 0) + 9 + sm[0]);
        b.size = sm[2] * 0.55f; b.dens = sm[3] * 0.75f;
    }

    private boolean locate(Actor a, float x, float y, float z, float dirX, float dirZ) {
        if (ports != null && ports.locate(a, port)) return true;
        // nobody told us: the muzzle is ahead of the chest, the port a hand's width behind it on the right
        float rx = -dirZ, rz = dirX;
        port[0] = a.x + dirX * 0.9f; port[1] = a.y + a.height() * 0.74f; port[2] = a.z + dirZ * 0.9f;
        port[3] = a.x + dirX * 0.45f + rx * 0.12f; port[4] = port[1]; port[5] = a.z + dirZ * 0.45f + rz * 0.12f;
        return true;
    }

    private void puff(float x, float y, float z, float vx, float vy, float vz, float size, float life, float dens) {
        // 1.0: gun smoke is the particle system's smoke (it used to be a list of this class's own)
        raid.fx.smoke(x, y, z, vx, vy, vz, size, life, 0.20f + rnd.nextFloat() * 0.08f, dens);
        puffed++;
    }

    private void eject(Actor a, Eject e, float dirX, float dirZ) {
        if (!locate(a, a.x, a.eyeY(), a.z, dirX, dirZ)) return;
        if (cases.size() >= MAX_CASES) cases.remove(0);
        float rx = -dirZ, rz = dirX;
        Case c = new Case();
        c.x = port[3]; c.y = port[4]; c.z = port[5];
        float s = e.scatter();
        float r = e.right() + (rnd.nextFloat() - 0.5f) * s, u = e.up() + (rnd.nextFloat() - 0.5f) * s, f = e.forward() + (rnd.nextFloat() - 0.5f) * s;
        c.vx = rx * r + dirX * f + a.vx; c.vy = u + Math.max(0, a.vy); c.vz = rz * r + dirZ * f + a.vz;
        c.spin = e.spin() * (0.7f + rnd.nextFloat() * 0.6f) * (rnd.nextBoolean() ? 1 : -1);
        c.yaw = (float) Math.atan2(dirX, dirZ); c.pitch = rnd.nextFloat() * 6.28f;
        c.size = e.size(); c.len = e.length(); c.color = e.color(); c.head = e.head();
        cases.add(c);
        ejected++;
    }

    public void update(float dt) {
        World w = raid.world;
        for (int i = pending.size() - 1; i >= 0; i--) {
            Pending p = pending.get(i);
            p.t -= dt;
            if (p.t > 0) continue;
            pending.remove(i);
            Actor a = raid.actor(p.actor);
            if (a != null && !a.dead) eject(a, p.e, (float) -Math.sin(a.yaw), (float) -Math.cos(a.yaw));
        }
        for (int i = barrels.size() - 1; i >= 0; i--) {
            Barrel b = barrels.get(i);
            Actor a = raid.actor(b.actor);
            if (a == null || a.dead || raid.time > b.until) { barrels.remove(i); continue; }
            float left = (b.until - raid.time);
            b.acc += dt * b.rate * Math.min(1, left / 0.6f);
            float dx = (float) -Math.sin(a.yaw), dz = (float) -Math.cos(a.yaw);
            while (b.acc >= 1) {
                b.acc -= 1;
                if (!locate(a, a.x, a.eyeY(), a.z, dx, dz)) break;
                // it leaves the barrel almost still and is carried by whatever the weapon is doing
                puff(port[0], port[1], port[2], a.vx * 0.3f + (rnd.nextFloat() - 0.5f) * 0.15f, 0.35f + rnd.nextFloat() * 0.3f, a.vz * 0.3f + (rnd.nextFloat() - 0.5f) * 0.15f, b.size * (0.6f + rnd.nextFloat() * 0.6f), 0.9f + rnd.nextFloat() * 0.8f, b.dens);
            }
        }
        for (int i = puffs.size() - 1; i >= 0; i--) {
            Puff p = puffs.get(i);
            p.age += dt;
            if (p.age >= p.life) { puffs.remove(i); continue; }
            float drag = (float) Math.exp(-dt * 3.4f);
            p.vx *= drag; p.vz *= drag; p.vy += (0.55f - p.vy) * Math.min(1, dt * 2.2f);
            p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
            p.size += dt * 0.34f;
        }
        for (int i = cases.size() - 1; i >= 0; i--) {
            Case c = cases.get(i);
            c.life += dt;
            if (c.life > CASE_LIFE) { cases.remove(i); continue; }
            if (c.rest > 0.6f) continue;                                   // lying still: nothing to do until it expires
            c.vy -= 9.81f * dt;
            float nx = c.x + c.vx * dt, ny = c.y + c.vy * dt, nz = c.z + c.vz * dt;
            if (w.solid((int) Math.floor(nx), (int) Math.floor(c.y), (int) Math.floor(c.z))) { c.vx *= -0.35f; nx = c.x; c.spin *= 0.6f; }
            if (w.solid((int) Math.floor(c.x), (int) Math.floor(c.y), (int) Math.floor(nz))) { c.vz *= -0.35f; nz = c.z; c.spin *= 0.6f; }
            if (w.solid((int) Math.floor(nx), (int) Math.floor(ny - c.size), (int) Math.floor(nz))) {
                float floor = (float) Math.floor(ny - c.size) + 1 + c.size;
                if (c.vy < -1.1f) {
                    // it bounces - less each time - and the first landing is heard
                    if (!c.heard && ring != null) { ring.ring(nx, floor, nz, c.len > 0.06f && c.color == HULL); c.heard = true; }
                    if (c.vy < -1.1f) landed++;
                    c.vy = -c.vy * 0.32f; c.vx *= 0.55f; c.vz *= 0.55f; c.spin *= 0.5f;
                } else { c.vy = 0; c.vx *= Math.max(0, 1 - dt * 7); c.vz *= Math.max(0, 1 - dt * 7); c.spin *= Math.max(0, 1 - dt * 6); c.pitch += (1.5708f - c.pitch % 3.14159f) * Math.min(1, dt * 8) * 0; if (Math.abs(c.vx) + Math.abs(c.vz) < 0.05f) c.rest += dt; }
                ny = floor;
            }
            c.x = nx; c.y = ny; c.z = nz;
            c.pitch += c.spin * dt; c.yaw += c.spin * 0.31f * dt;
        }
    }

    /** The cases: a body and a head, two boxes each, turned as they tumble. */
    public void draw(ModelRenderer mr, Mat4 tmp, float camX, float camZ, java.util.function.Function<float[], Float> lightAt) {
        float[] pos = new float[3];
        for (Case c : cases) {
            if (Math.abs(c.x - camX) > 34 || Math.abs(c.z - camZ) > 34) continue;
            pos[0] = c.x; pos[1] = c.y; pos[2] = c.z;
            float l = lightAt.apply(pos) + 0.12f;
            float pitch = c.rest > 0.2f ? 1.5708f : c.pitch;                // at rest it lies on its side
            mr.material(0.32f, 0.9f);
            tmp.identity().translate(c.x, c.y, c.z).rotateY(c.yaw).rotateX(pitch).scale(c.size, c.len, c.size).translate(0, -0.5f, 0);
            mr.cube(tmp, c.color, l, 0);
            tmp.identity().translate(c.x, c.y, c.z).rotateY(c.yaw).rotateX(pitch).translate(0, -c.len * 0.5f, 0).scale(c.size * 1.18f, c.len * 0.22f, c.size * 1.18f).translate(0, -0.5f, 0);
            mr.cube(tmp, c.head, l, 0);
        }
        mr.material();
    }

    /** The smoke, into the particle pass: grey, thin, lit by whatever is around it. */
    public void submit(ParticleBatch pb) {
        for (Puff p : puffs) {
            float k = p.age / p.life, a = p.dens * (k < 0.12f ? k / 0.12f : 1 - (k - 0.12f) / 0.88f);
            float c = 0.20f + p.seed * 0.08f;
            pb.add(p.x, p.y, p.z, p.size, c, c, c * 1.03f, Math.max(0, a), p.seed * 6.28f + p.age * 0.6f, ParticleBatch.SMOKE, k, p.seed);
        }
    }

    /** Legacy (forward) renderer: there is no particle pass, so the smoke is drawn as soft cubes. */
    public void drawLegacy(ModelRenderer mr, Mat4 tmp) {
        for (Puff p : puffs) {
            float k = p.age / p.life, a = p.dens * (1 - k) * 1.4f;
            tmp.identity().translate(p.x, p.y, p.z).scale(p.size, p.size, p.size).translate(0, -0.5f, 0);
            mr.cube(tmp, ((int) (Math.min(1, a) * 255) << 24) | 0x8A8A8E, 0.7f, 0);
        }
    }

    public void clear() { cases.clear(); pending.clear(); puffs.clear(); barrels.clear(); raid.fx.clear(); }
}
