package absentialis.anatomy;

import absentialis.engine.util.Rng;
import java.util.ArrayList;
import java.util.List;

/**
 * Where blood comes from. Every drop starts at a wound: thrown by the impact that made it (a small splash back
 * toward the shooter, a larger cone out of an exit wound, along the path of the round), and afterwards let go by
 * the wound itself - emitters fixed to the injured region in its own space, so a bleeding arm drips from the arm
 * wherever the arm goes. A severed limb's stump pumps in pulses that weaken as the pressure drops.
 *
 * What happens to a drop once it is in the air - gravity, walls, floors, pooling, running over edges, drying - is
 * the environment's business (the sink). Density scales with the graphics settings; emitters go to sleep when
 * they have nothing left to give.
 */
public final class BloodEffectSystem {
    /** Receives droplets and direct floor spills. */
    public interface Sink {
        void drop(float x, float y, float z, float vx, float vy, float vz, float volume, boolean infected);
    }

    /** Resolves an emitter's place in the world this instant: {x, y, z, dirX, dirY, dirZ}, or null if its owner is gone. */
    public interface Locator { float[] locate(Emitter e); }

    /** A bleeding wound, fixed in the space of the region it is on. */
    public static final class Emitter {
        public int actorId, group, pieceId;
        public float lx, ly, lz, dx, dy, dz;
        /** Volume per second now, what it decays by per second, and how long it has left. */
        public float rate, decay, life, acc, phase;
        public boolean arterial, infected;
    }

    public final List<Emitter> emitters = new ArrayList<>();
    public int maxEmitters = 72;
    /** 0.4 .. 1.6 from the gore detail setting: how many particles a given amount of blood becomes. */
    public float density = 1f;
    public int sprays, drips;

    /** An impact spray: count droplets thrown in a cone around (dx, dy, dz). */
    public void spray(Sink sink, Rng rng, float x, float y, float z, float dx, float dy, float dz, float speed, float cone, float volume, int count, boolean infected) {
        int n = Math.max(1, Math.round(count * density));
        float each = volume / n;
        sprays++;
        for (int i = 0; i < n; i++) {
            float sp = speed * (0.45f + rng.nextFloat() * 0.8f);
            float jx = (rng.nextFloat() - 0.5f) * cone, jy = (rng.nextFloat() - 0.3f) * cone, jz = (rng.nextFloat() - 0.5f) * cone;
            sink.drop(x, y, z, (dx + jx) * sp, (dy + jy) * sp + 0.6f, (dz + jz) * sp, each * (0.6f + rng.nextFloat() * 0.8f), infected);
        }
    }

    /** Adds a bleeding wound. An emitter close to an existing one on the same region just makes that one worse. */
    public Emitter bleed(int actorId, int pieceId, int group, float lx, float ly, float lz, float dx, float dy, float dz, float rate, float seconds, boolean arterial, boolean infected) {
        for (Emitter e : emitters) {
            if (e.actorId != actorId || e.pieceId != pieceId || e.group != group) continue;
            float d = Math.abs(e.lx - lx) + Math.abs(e.ly - ly) + Math.abs(e.lz - lz);
            if (d < 2.5f) { e.rate = Math.min(4f, e.rate + rate * 0.6f); e.life = Math.max(e.life, seconds); e.arterial |= arterial; return e; }
        }
        Emitter e = new Emitter();
        e.actorId = actorId; e.pieceId = pieceId; e.group = group;
        e.lx = lx; e.ly = ly; e.lz = lz; e.dx = dx; e.dy = dy; e.dz = dz;
        e.rate = rate; e.life = seconds; e.decay = rate / Math.max(1f, seconds); e.arterial = arterial; e.infected = infected;
        emitters.add(e);
        if (emitters.size() > maxEmitters) emitters.remove(0);
        return e;
    }

    /** Everything bleeding from one actor stops (they were bandaged, or removed from the world). */
    public void stop(int actorId) { emitters.removeIf(e -> e.actorId == actorId && e.pieceId == 0); }

    public void update(float dt, Locator loc, Sink sink, Rng rng) {
        for (int i = emitters.size() - 1; i >= 0; i--) {
            Emitter e = emitters.get(i);
            e.life -= dt;
            e.rate = Math.max(0, e.rate - e.decay * dt * 0.6f);
            if (e.life <= 0 || e.rate < 0.02f) { emitters.remove(i); continue; }
            e.phase += dt;
            // arterial bleeding comes in beats; a seeping wound just drips
            float beat = e.arterial ? Math.max(0, (float) Math.sin(e.phase * 7.5f)) * 2.2f : 1f;
            e.acc += e.rate * beat * dt * (e.arterial ? 9f : 2.2f) * density;
            if (e.acc < 1) continue;
            float[] w = loc.locate(e);
            if (w == null) { emitters.remove(i); continue; }
            while (e.acc >= 1) {
                e.acc -= 1;
                drips++;
                float sp = e.arterial ? 1.6f + rng.nextFloat() * 2.4f : 0.15f;
                sink.drop(w[0], w[1], w[2], w[3] * sp + (rng.nextFloat() - 0.5f) * 0.5f, w[4] * sp + (e.arterial ? 1.2f : 0), w[5] * sp + (rng.nextFloat() - 0.5f) * 0.5f, e.arterial ? 0.03f : 0.016f, e.infected);
            }
        }
    }
}
