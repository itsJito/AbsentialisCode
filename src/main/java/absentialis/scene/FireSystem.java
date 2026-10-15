package absentialis.scene;

import absentialis.engine.audio.UltraEngine;
import absentialis.engine.math.Mat4;
import absentialis.engine.render.HybridRenderer;
import absentialis.engine.render.ModelRenderer;
import absentialis.engine.render.ParticleBatch;
import absentialis.engine.util.Rng;
import absentialis.entity.Actor;
import absentialis.world.Block;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.List;

/**
 * Fire as a simulated effect, not a block (0.4 removed the fire voxel).
 *
 * A fire is a point in the world with a size, a heat and a lifetime. Each one feeds a pooled particle simulation -
 * tongues of flame that rise, twist and thin out, smoke that climbs and spreads on the wind, embers thrown off the
 * top - casts a flickering shadowed light, crackles in the mixer where it stands, and burns whoever stands in it.
 * Burn barrels are permanent fires bound to their barrel block (break the barrel and the fire goes out);
 * incendiary grenades start short-lived ground fires that die down before they vanish.
 */
public final class FireSystem {
    public static final class Fire {
        public float x, y, z, radius, intensity, target = 1, life = -1, age, seed, phase, flicker = 1, dps;
        public int owner, bx = -1, by, bz;
        float flameAcc, smokeAcc, emberAcc, burnTick;
        UltraEngine.Voice voice;
        public boolean permanent() { return life < 0; }
    }

    public final List<Fire> fires = new ArrayList<>();
    private final Raid raid;
    private final Rng rng = new Rng(9151);

    // ---- pooled particles (structure of arrays: nothing is allocated while a fire burns)
    private static final int CAP = 7000;
    private final float[] px = new float[CAP], py = new float[CAP], pz = new float[CAP], vx = new float[CAP], vy = new float[CAP], vz = new float[CAP];
    private final float[] life = new float[CAP], max = new float[CAP], size = new float[CAP], seed = new float[CAP], heat = new float[CAP];
    private final byte[] kind = new byte[CAP];
    private int count;
    /** Detail scale 0.4 .. 1.5 from the graphics settings: particle density, never the fire's light or damage. */
    public float detail = 1f;
    public int burns;
    private float camX, camY, camZ, blockCheck;

    FireSystem(Raid raid) { this.raid = raid; }

    /** A permanent fire sitting on a block (burn barrels): it goes out when the block is gone. */
    public Fire bind(int bx, int by, int bz, float radius) {
        Fire f = new Fire();
        f.x = bx + 0.5f; f.y = by + 0.95f; f.z = bz + 0.5f; f.radius = radius; f.bx = bx; f.by = by; f.bz = bz;
        f.seed = rng.nextFloat() * 100; f.intensity = 1; f.dps = 8;
        fires.add(f);
        return f;
    }

    /** Starts a fire that burns for the given time and then dies down. dps is what it does to someone standing in it. */
    public Fire ignite(float x, float y, float z, float radius, float seconds, float dps, int owner) {
        Fire f = new Fire();
        f.x = x; f.y = y; f.z = z; f.radius = radius; f.life = seconds; f.dps = dps; f.owner = owner;
        f.seed = rng.nextFloat() * 100; f.intensity = 0.2f;
        fires.add(f);
        if (fires.size() > 96) for (int i = 0; i < fires.size(); i++) if (!fires.get(i).permanent()) { stop(fires.remove(i)); break; }
        return f;
    }

    /** An incendiary pool: one large fire at the centre and a ring of smaller ones over the area it covers. */
    public void pool(float x, float y, float z, float radius, float seconds, int owner) {
        ignite(x, y, z, Math.min(1.3f, radius * 0.45f), seconds, 0, owner);
        int n = Math.max(3, Math.round(radius * 2.2f));
        for (int i = 0; i < n; i++) {
            float a = i * 6.2832f / n + rng.nextFloat() * 0.6f, r = radius * (0.45f + rng.nextFloat() * 0.5f);
            float fx = x + (float) Math.sin(a) * r, fz = z + (float) Math.cos(a) * r;
            int gy = raid.groundY((int) Math.floor(fx), (int) Math.floor(fz));
            if (gy < 0 || Math.abs(gy - y) > 2.5f) continue;
            ignite(fx, gy, fz, 0.35f + rng.nextFloat() * 0.3f, seconds * (0.7f + rng.nextFloat() * 0.35f), 0, owner);
        }
    }

    private static void stop(Fire f) { if (f.voice != null) { f.voice.fadeTo(0, 0.6f); f.voice = null; } }

    public void clear() { for (Fire f : fires) stop(f); fires.clear(); count = 0; }

    /** True when a point is inside a burning fire (AI avoidance, HUD warning). */
    public Fire at(float x, float y, float z) {
        for (Fire f : fires) if (f.intensity > 0.3f && Math.abs(y - f.y) < 2.2f && (x - f.x) * (x - f.x) + (z - f.z) * (z - f.z) < (f.radius + 0.25f) * (f.radius + 0.25f)) return f;
        return null;
    }

    // ------------------------------------------------------------------ simulation
    void update(float dt) {
        camX = raid.player.x; camY = raid.player.eyeY(); camZ = raid.player.z;
        World w = raid.world;
        blockCheck -= dt;
        boolean check = blockCheck <= 0;
        if (check) blockCheck = 0.5f;
        float windX = 0.35f + raid.rain() * 0.5f, windZ = 0.12f;
        for (int i = fires.size() - 1; i >= 0; i--) {
            Fire f = fires.get(i);
            f.age += dt;
            if (!f.permanent()) { f.life = Math.max(0, f.life - dt); f.target = f.life < 2f ? Math.max(0, f.life / 2f) : 1; }
            else if (check && f.bx >= 0 && w.get(f.bx, f.by, f.bz) != Block.BARREL_FIRE) { f.life = 1.5f; f.bx = -1; }   // the barrel is gone: it burns out
            f.intensity += (f.target - f.intensity) * Math.min(1, dt * 3);
            // heavy rain beats down an open fire
            if (raid.rain() > 0.6f && w.sky((int) Math.floor(f.x), (int) Math.floor(f.y + 1), (int) Math.floor(f.z)) > 13) f.intensity *= 1 - dt * 0.05f;
            f.phase += dt * (9 + (f.seed % 4));
            f.flicker = 0.82f + 0.12f * (float) Math.sin(f.phase) + 0.06f * (float) Math.sin(f.phase * 2.7f + f.seed) + (rng.nextFloat() - 0.5f) * 0.06f;
            if (!f.permanent() && f.life <= 0) { stop(f); fires.remove(i); continue; }
            emit(f, dt);
            burn(f, dt);
        }
        // 1.0: a fire's flames, smoke and embers are particles of the Astratto system (see emit); what is left here is the glow
        // each fire has round it, a sprite renewed every frame
        absentialis.engine.fx.ParticleSystem ps = raid.fx;
        for (Fire f : fires) {
            if (f.intensity < 0.03f || Math.abs(f.x - camX) > 140 || Math.abs(f.z - camZ) > 140) continue;
            float k = f.intensity * f.flicker, s = Math.min(2.4f, f.radius / 0.45f);
            ps.spawn(absentialis.engine.fx.ParticleSystem.GLOW, 0, f.x, f.y + 0.35f * s, f.z, 0, 0, 0, Math.max(0.02f, dt * 1.2f), 1.5f * (0.7f + s * 0.5f), 0, 2.6f * k, 1.05f * k, 0.28f * k, 0.5f);
        }
        for (int i = count - 1; i >= 0; i--) {
            life[i] -= dt;
            if (life[i] <= 0) { kill(i); continue; }
            float age = 1 - life[i] / max[i];
            switch (kind[i]) {
                case ParticleBatch.FLAME -> {
                    // buoyant, and pulled toward the column's axis as it rises: that is what gives a flame its taper
                    vy[i] += 2.2f * dt;
                    float wob = (float) Math.sin(raid.time * 7 + seed[i] * 40) * 0.6f;
                    vx[i] += (wob - vx[i] * 2.4f) * dt; vz[i] += ((float) Math.cos(raid.time * 6 + seed[i] * 31) * 0.6f - vz[i] * 2.4f) * dt;
                    vx[i] += windX * dt * 0.8f;
                }
                case ParticleBatch.SMOKE -> { vy[i] += (0.9f - vy[i]) * dt * 0.8f; vx[i] += (windX * 1.6f - vx[i]) * dt * 0.7f; vz[i] += (windZ * 1.6f - vz[i]) * dt * 0.7f; size[i] += dt * 0.42f; }
                default -> {
                    vy[i] -= 2.6f * dt;
                    vx[i] += windX * dt; vz[i] += (rng.nextFloat() - 0.5f) * dt * 3;
                    // an ember that touches something dies there
                    if (age > 0.15f && w.solid((int) Math.floor(px[i]), (int) Math.floor(py[i]), (int) Math.floor(pz[i]))) { kill(i); continue; }
                }
            }
            px[i] += vx[i] * dt; py[i] += vy[i] * dt; pz[i] += vz[i] * dt;
        }
    }

    private void kill(int i) {
        int last = --count;
        if (i == last) return;
        px[i] = px[last]; py[i] = py[last]; pz[i] = pz[last]; vx[i] = vx[last]; vy[i] = vy[last]; vz[i] = vz[last];
        life[i] = life[last]; max[i] = max[last]; size[i] = size[last]; seed[i] = seed[last]; heat[i] = heat[last]; kind[i] = kind[last];
    }

    private int spawn(int k, float x, float y, float z, float lifeSec, float sz) { return -1; }

    private static final int PS_FLAME = absentialis.engine.fx.ParticleSystem.FLAME, PS_SMOKE = absentialis.engine.fx.ParticleSystem.SMOKE, PS_EMBER = absentialis.engine.fx.ParticleSystem.EMBER;

    private void emit(Fire f, float dt) {
        float dx = f.x - camX, dz = f.z - camZ, d2 = dx * dx + dz * dz;
        if (d2 > 110 * 110 || f.intensity < 0.03f) return;
        // distance-based detail: full within 30 m, thinning to a quarter by 110 m
        float lod = detail * (d2 < 900 ? 1 : Math.max(0.25f, 1 - ((float) Math.sqrt(d2) - 30) / 110f));
        float s = f.radius / 0.45f, k = f.intensity * lod;
        f.flameAcc += dt * 30 * k * Math.min(3f, 0.6f + s * 0.6f);
        while (f.flameAcc >= 1) {
            f.flameAcc--;
            float a = rng.nextFloat() * 6.2832f, r = f.radius * 0.75f * (float) Math.sqrt(rng.nextFloat());
            // a tongue of flame: buoyant, drawn in toward the column's axis as it rises (that is what tapers it), shrinking as it burns out
            float fl = 0.42f + rng.nextFloat() * 0.5f, fs = (0.26f + rng.nextFloat() * 0.2f) * Math.min(2.2f, 0.7f + s * 0.45f), heat = 3.4f * f.intensity;
            int i = raid.fx.spawn(PS_FLAME, absentialis.engine.fx.ParticleSystem.GRAVITY | absentialis.engine.fx.ParticleSystem.WIND, f.x + (float) Math.sin(a) * r, f.y + 0.05f + rng.nextFloat() * 0.12f, f.z + (float) Math.cos(a) * r,
                    -(float) Math.sin(a) * r * 0.9f, 1.1f + rng.nextFloat() * 1.3f, -(float) Math.cos(a) * r * 0.9f, fl, fs, -fs * 0.55f / fl, heat, heat, heat, 0.95f);
            if (i < 0) break;
            raid.fx.tune(i, 2.4f, -2.2f, 0);
        }
        f.smokeAcc += dt * 4.2f * k * Math.min(2.5f, s);
        while (f.smokeAcc >= 1) {
            f.smokeAcc--;
            float c = 0.035f + rng.nextFloat() * 0.03f;
            int i = raid.fx.spawn(PS_SMOKE, absentialis.engine.fx.ParticleSystem.WIND, f.x + (rng.nextFloat() - 0.5f) * f.radius, f.y + 0.9f + rng.nextFloat() * 0.5f, f.z + (rng.nextFloat() - 0.5f) * f.radius,
                    0, 1.3f + rng.nextFloat() * 0.6f, 0, 2.6f + rng.nextFloat() * 2.2f, 0.4f + rng.nextFloat() * 0.3f + s * 0.12f, 0.42f, c, c, c * 1.05f, 0.42f);
            if (i < 0) break;
            raid.fx.tune(i, 0.7f, -0.6f, 0.2f);
        }
        f.emberAcc += dt * 5.5f * k * Math.min(2.5f, s);
        while (f.emberAcc >= 1) {
            f.emberAcc--;
            // an ember: thrown up, carried on the wind, dead where it touches anything
            int i = raid.fx.spawn(PS_EMBER, absentialis.engine.fx.ParticleSystem.GRAVITY | absentialis.engine.fx.ParticleSystem.COLLIDE | absentialis.engine.fx.ParticleSystem.WIND, f.x + (rng.nextFloat() - 0.5f) * f.radius, f.y + 0.5f + rng.nextFloat() * 0.5f, f.z + (rng.nextFloat() - 0.5f) * f.radius,
                    (rng.nextFloat() - 0.5f) * 1.6f, 1.8f + rng.nextFloat() * 2.6f, (rng.nextFloat() - 0.5f) * 1.6f, 0.9f + rng.nextFloat() * 1.4f, 0.022f + rng.nextFloat() * 0.03f, 0, 14f, 5.2f, 1.1f, 1f);
            if (i < 0) break;
            raid.fx.tune(i, 0, 2.6f, 0);
        }
    }

    /** A fire with a damage rate burns whoever stands in it (twice a second, like the other hazards). */
    private void burn(Fire f, float dt) {
        if (f.dps <= 0 || f.intensity < 0.4f) return;
        f.burnTick -= dt;
        if (f.burnTick > 0) return;
        f.burnTick = 0.5f;
        float reach = f.radius + 0.3f;
        for (Actor a : raid.nearby(f.x, f.z, reach + 1)) {
            if (a.distXZ(f.x, f.z) > reach + a.radius || a.y > f.y + 1.4f || a.y + a.height() < f.y - 0.6f) continue;
            raid.burnActor(a, f.dps * 0.5f, f.owner);
            burns++;
        }
    }

    // ------------------------------------------------------------------ presentation
    /** Hands this frame's particles to the renderer. */
    public void submit(ParticleBatch pb) {
        for (int i = 0; i < count; i++) {
            float age = 1 - life[i] / max[i];
            switch (kind[i]) {
                case ParticleBatch.FLAME -> pb.add(px[i], py[i], pz[i], size[i] * (1 - age * 0.55f), 3.4f * heat[i], 3.4f * heat[i], 3.4f * heat[i], 0.95f, 0, ParticleBatch.FLAME, age, seed[i]);
                case ParticleBatch.SMOKE -> { float c = 0.035f + seed[i] * 0.03f; pb.add(px[i], py[i], pz[i], size[i], c, c, c * 1.05f, 0.42f, seed[i] * 6.28f + age, ParticleBatch.SMOKE, age, seed[i]); }
                default -> pb.add(px[i], py[i], pz[i], size[i], 14f, 5.2f, 1.1f, 1f, 0, ParticleBatch.EMBER, age, seed[i]);
            }
        }
    }

    /** One flickering, shadow-casting light per fire near the camera (nearest first: the renderer shadows the first few). */
    public void lights(HybridRenderer hr) {
        fires.sort((a, b) -> Float.compare(d2(a), d2(b)));
        int n = 0;
        for (Fire f : fires) {
            if (f.intensity < 0.05f || d2(f) > 80 * 80 || n >= 14) continue;
            float k = f.intensity * f.flicker, s = Math.min(2.2f, 0.7f + f.radius);
            hr.addPoint(f.x + (float) Math.sin(f.phase * 0.7f) * 0.06f, f.y + 0.55f, f.z + (float) Math.cos(f.phase * 0.9f) * 0.06f, 9.5f * s, 7.5f * k * s, 3.3f * k * s, 0.95f * k * s, HybridRenderer.LIGHT_SHADOW);
            n++;
        }
    }

    private float d2(Fire f) { return (f.x - camX) * (f.x - camX) + (f.z - camZ) * (f.z - camZ); }

    /** Positional crackle for the nearest fires; everything further away is silent. */
    public void audio(UltraEngine au) {
        if (au == null || !au.available) return;
        int voiced = 0;
        for (Fire f : fires) {   // sorted nearest first by lights()
            boolean near = d2(f) < 34 * 34 && f.intensity > 0.1f && voiced < 4;
            if (near) {
                if (f.voice == null || f.voice.done()) { f.voice = au.loop("fire_loop", 0f, UltraEngine.SFX); f.voice.setPitch(0.9f + (f.seed % 1f) * 0.2f); }
                au.place(f.voice, f.x, f.y + 0.4f, f.z, 0.75f * f.intensity * Math.min(1.6f, 0.6f + f.radius), 34);
                voiced++;
            } else stop(f);
        }
    }

    /** Legacy forward renderer: glowing boxes stand in for the particle flames. */
    public void drawLegacy(ModelRenderer mr, Mat4 tmp) {
        for (Fire f : fires) {
            if (f.intensity < 0.05f) continue;
            for (int k = 0; k < 5; k++) {
                float t = (raid.time * 1.6f + k / 5f + f.seed) % 1f, sz = f.radius * (0.9f - t * 0.7f);
                tmp.identity().translate(f.x + (float) Math.sin(t * 9 + k) * 0.08f, f.y + t * 1.3f, f.z + (float) Math.cos(t * 7 + k) * 0.08f).scale(sz, sz * 1.4f, sz).translate(0, -0.5f, 0);
                mr.cube(tmp, k % 2 == 0 ? 0xD0FFB040 : 0xD0FF6A1C, 1, 1);
            }
        }
    }

    public int particles() { return raid.fx.count(PS_FLAME) + raid.fx.count(PS_EMBER) + raid.fx.count(PS_SMOKE); }
}
