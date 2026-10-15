package absentialis.scene;

import java.util.ArrayList;
import java.util.List;

/**
 * Glass as one sound (0.9). Until now every pane of a window was a "glass_break" of its own and every shard that
 * landed hard was a "glass_tinkle" of its own: one burst through a shopfront played a dozen breaks and thirty
 * tinkles on top of each other. Here the pieces are gathered into the thing that happened: panes that go within a
 * moment and a few paces of each other are one window breaking - heard once, from the middle of them, and carrying
 * a little further the more glass went - and the shards of that window are heard landing once when the first of
 * them reach the floor and once more as the last settle. What a round sounds like going into glass is untouched:
 * that is a cue of its own (Raid.impactSound).
 *
 * The raid's minds hear the same thing the player does, once (it goes out through Raid.emitSound).
 */
public final class GlassAudio {
    /** Panes breaking this close in time and space are one break. */
    static final float WINDOW = 0.12f, REACH = 5f;
    /** A break is remembered this long: panes and shards that follow it in that time belong to it. */
    static final float LIFE = 3.5f;
    /** The shards of one break are heard at most this often, and this many times. */
    static final float SETTLE_GAP = 0.45f;
    static final int SETTLES = 2;

    private final Raid raid;
    private final List<Burst> bursts = new ArrayList<>();
    /** What was asked for and what was played (tests, and the Developer Mode readout). */
    public int panesAsked, shardsAsked, breaksHeard, settlesHeard;

    private static final class Burst {
        float x, y, z, born, lastSettle = -9, floorY;
        int panes, by, landed, settles;
        boolean heard;
    }

    GlassAudio(Raid raid) { this.raid = raid; }

    private Burst near(float x, float y, float z, float reach) {
        Burst best = null;
        float bd = reach * reach;
        for (Burst b : bursts) {
            float dx = b.x - x, dy = (b.y - y) * 0.5f, dz = b.z - z, d = dx * dx + dy * dy + dz * dz;
            if (d <= bd) { bd = d; best = b; }
        }
        return best;
    }

    /** A pane left its frame. */
    public void pane(float x, float y, float z, int by) {
        panesAsked++;
        Burst b = near(x, y, z, REACH);
        if (b == null) {
            b = new Burst();
            b.x = x; b.y = y; b.z = z; b.born = raid.time; b.by = by; b.floorY = y;
            bursts.add(b);
        } else if (!b.heard) {
            // the middle of what is breaking moves toward each pane that joins it
            float k = 1f / (b.panes + 1);
            b.x += (x - b.x) * k; b.y += (y - b.y) * k; b.z += (z - b.z) * k;
        }
        // a pane that goes after its window has been heard is part of the same crash unless the window is long done
        else if (raid.time - b.born > 0.6f) { b.heard = false; b.born = raid.time; b.panes = 0; b.x = x; b.y = y; b.z = z; b.settles = Math.min(b.settles, SETTLES - 1); }
        b.panes++;
        if (by != 0) b.by = by;
    }

    /** A shard hit the floor hard enough to be heard. */
    public void shard(float x, float y, float z) {
        shardsAsked++;
        Burst b = near(x, y, z, REACH * 1.6f);
        if (b == null) {
            // glass that fell without a window breaking near it (a shard knocked off a sill): it is its own small event
            b = new Burst();
            b.x = x; b.y = y; b.z = z; b.born = raid.time - WINDOW; b.heard = true;
            bursts.add(b);
        }
        b.landed++;
        b.floorY = y;
        if (b.landed == 1) { b.x += (x - b.x) * 0.5f; b.z += (z - b.z) * 0.5f; }
    }

    void update() {
        float t = raid.time;
        for (int i = bursts.size() - 1; i >= 0; i--) {
            Burst b = bursts.get(i);
            if (!b.heard && t - b.born >= WINDOW) {
                b.heard = true;
                breaksHeard++;
                // one pane carries 30 m, a whole shopfront 42
                raid.emitSound(b.x, b.y, b.z, 30 + 3 * Math.min(4, b.panes - 1), "glass_break", b.by, false);
            }
            if (b.heard && b.landed > 0 && b.settles < SETTLES && t - b.lastSettle >= SETTLE_GAP
                    // the second time is the tail: wait until shards have stopped arriving in numbers
                    && (b.settles == 0 || t - b.born > 0.9f)) {
                b.settles++; b.lastSettle = t; b.landed = 0;
                settlesHeard++;
                raid.emitSound(b.x, b.floorY, b.z, 9, "glass_tinkle", 0, false);
            }
            if (t - b.born > LIFE) bursts.remove(i);
        }
    }

    void clear() { bursts.clear(); }
}
