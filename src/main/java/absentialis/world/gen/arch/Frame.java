package absentialis.world.gen.arch;

/**
 * A footprint seen from its front door. Local coordinates: {@code a} runs along the front wall, {@code b} runs
 * back from it (b = 0 is the front wall itself). Every building and every landmark compound is designed once in
 * these coordinates and stands correctly whichever of the four sides its street is on.
 *
 * World sides follow the rest of the generator: 0 = north (-z), 1 = east (+x), 2 = south (+z), 3 = west (-x).
 */
public final class Frame {
    public final int x0, z0, x1, z1, front;
    /** Size along the front and from front to back. */
    public final int A, B;

    /** Inclusive world footprint and the world side its front faces. */
    public Frame(int x0, int z0, int x1, int z1, int front) {
        this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; this.front = front & 3;
        boolean alongX = (this.front & 1) == 0;
        A = alongX ? x1 - x0 + 1 : z1 - z0 + 1;
        B = alongX ? z1 - z0 + 1 : x1 - x0 + 1;
    }

    public int x(int a, int b) {
        switch (front) { case 0: case 2: return x0 + a; case 1: return x1 - b; default: return x0 + b; }
    }

    public int z(int a, int b) {
        switch (front) { case 0: return z0 + b; case 2: return z1 - b; default: return z0 + a; }
    }

    /** Unit step in world x / z for one step outwards through the front wall. */
    public int outX() { return front == 1 ? 1 : front == 3 ? -1 : 0; }
    public int outZ() { return front == 0 ? -1 : front == 2 ? 1 : 0; }

    /** World side of a local side: 0 front, 1 the a-max end, 2 back, 3 the a-min end. */
    public int side(int local) {
        switch (local & 3) {
            case 0: return front;
            case 2: return (front + 2) & 3;
            case 1: return (front & 1) == 0 ? 1 : 2;
            default: return (front & 1) == 0 ? 3 : 0;
        }
    }

    /** A sub-frame: the local box a0..a1, b0..b1 (inclusive) as its own footprint, fronting local side {@code localFront}. */
    public Frame sub(int a0, int b0, int a1, int b1, int localFront) {
        int xa = x(a0, b0), za = z(a0, b0), xb = x(a1, b1), zb = z(a1, b1);
        return new Frame(Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb), side(localFront));
    }
}
