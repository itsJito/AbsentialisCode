package absentialis.world;

import astratto.bridge.AstrattoNative;
import astratto.bridge.CommandBuffer;
import astratto.bridge.NativeEngine;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 1.2: this game's end of the bridge to the Astratto native core. It tells the engine what Absentialis' blocks mean
 * to it, gives it the world once, and from then on writes down everything that has to cross - a block changed, a
 * door locked or forced, a route somebody needs - as commands that go over together, once a tick, in {@link #flush}.
 * Routes come back in the same call and are handed to whoever asked.
 *
 * The first stage of the engine's move to C++ (see {@code astratto/ARCHITECTURE.md}): the native core owns a copy of
 * the voxel world that is authoritative for route searches, and runs NeuralAI's A* for every body in the game, in
 * parallel. {@link Nav#path} - the same search, in Java - stays as the reference the native one is held to
 * ({@code ABS_SELFTEST=native12}) and as what is used when there is no native library on the machine.
 */
public final class NativeLink {
    /** One engine for the life of the process; a new world replaces the old one in it. */
    private static NativeEngine engine;
    private static boolean tried;
    /** {@code ABS_NATIVE=0} keeps everything in Java (to compare the two, or on a machine where the library misbehaves). */
    public static boolean enabled = !"0".equals(System.getenv("ABS_NATIVE"));
    /** Totals across every world this process has linked (gates and the console). */
    public static int attaches, frames, pathsAsked, pathsAnswered;

    private final World w;
    private final CommandBuffer cmd = new CommandBuffer(1 << 14);
    private final Map<Integer, Consumer<int[]>> waiting = new HashMap<>();
    private int nextRequest = 1;
    private float jump = Float.NaN, ladder, mantle;
    private boolean dead;
    private final NativeEngine.Events sink = new NativeEngine.Events() {
        @Override public void path(int request, int[] cells, int expansions) {
            Consumer<int[]> c = waiting.remove(request);
            w.nav.searches++; w.nav.expansions += expansions;
            pathsAnswered++;
            if (c != null) c.accept(cells);
        }
        @Override public void sight(int request, boolean clear) { Consumer<Boolean> c = seeing.remove(request); if (c != null) c.accept(clear); }
    };
    private final Map<Integer, Consumer<Boolean>> seeing = new HashMap<>();

    private NativeLink(World w) { this.w = w; }

    /** The process's engine, made on first use; null when there is no native core here. */
    public static synchronized NativeEngine engine() {
        if (!tried) {
            tried = true;
            engine = enabled ? NativeEngine.create(0) : null;
            if (engine != null) System.out.println("[astratto] " + AstrattoNative.buildInfo() + " - " + AstrattoNative.path());
            else System.out.println("[astratto] native core not in use (" + (enabled ? AstrattoNative.reason() : "ABS_NATIVE=0") + "): route searches run in Java");
        }
        return engine;
    }

    public static boolean available() { return engine() != null; }

    /** The engine's flags for one of this game's blocks. */
    static int flagsOf(Block b) {
        int f = 0;
        if (b.solid()) f |= AstrattoNative.BLOCK_SOLID;
        if (b.opaque()) f |= AstrattoNative.BLOCK_OPAQUE;
        if (b.is(Block.CUTOUT)) f |= AstrattoNative.BLOCK_CUTOUT;
        if (b.is(Block.CLIMB)) f |= AstrattoNative.BLOCK_CLIMB;
        if (b.is(Block.STEP)) f |= AstrattoNative.BLOCK_STEP;
        if (b.is(Block.LIQUID)) f |= AstrattoNative.BLOCK_LIQUID;
        if (b.is(Block.HAZARD)) f |= AstrattoNative.BLOCK_HAZARD;
        if (b.is(Block.DOOR)) f |= AstrattoNative.BLOCK_DOOR;
        return f;
    }

    /**
     * Gives the engine this world as it stands and links the two: every change made through {@link World#set} or
     * {@link World#setRaw} from now on follows it across. Returns null (and the world stays unlinked) when there is no
     * native core or it refuses the world.
     */
    public static NativeLink attach(World w) {
        NativeEngine e = engine();
        if (w.link != null) w.link.detach();
        if (e == null) return null;
        // the engine holds one world: whichever was linked before this one is not any more (it is given over again when it is next used)
        if (current != null) current.detach();
        int[] flags = new int[256], hide = new int[256], mat = new int[256], shape = new int[256];
        for (int i = 0; i < 256; i++) {
            Block b = Block.ALL[i];
            if (b == null) continue;
            flags[i] = flagsOf(b);
            hide[i] = Shapes.hideMask(b.shape);
            mat[i] = b.mat == Material.GLASS ? AstrattoNative.MATERIAL_GLASS : b.mat == Material.FOLIAGE ? AstrattoNative.MATERIAL_FOLIAGE : AstrattoNative.MATERIAL_OTHER;
            shape[i] = b.shape;
        }
        if (!e.blockTable(flags, hide, mat, shape) || !e.loadWorld(World.W, World.H, World.D, Block.BEDROCK, w.blocks)) {
            System.out.println("[astratto] the native core refused the world (" + e.lastError() + "): route searches run in Java");
            return null;
        }
        NativeLink l = new NativeLink(w);
        w.link = l;
        current = l;
        attaches++;
        return l;
    }

    public void detach() { if (w.link == this) w.link = null; if (current == this) current = null; if (!dead) { dead = true; cmd.free(); } }
    /** The link of the world the engine has now (it has one at a time). */
    private static NativeLink current;

    /** Routes that were asked of the engine and then searched for in Java after all, because nothing ran the engine's tick (a body steered outside an operation's update). */
    public static int stalls;

    // ------------------------------------------------------------------ what is written down during a tick
    void blockSet(int x, int y, int z, int id) { if (!dead) cmd.blockSet(x, y, z, id); }

    /** Whatever doors were sealed before are not: a new operation's doors say which of its own are. */
    public void doorsUnseal() { if (!dead) cmd.doorsUnseal(); }

    /** A door cell that can no longer simply be opened, or can again. */
    public void doorSeal(int x, int y, int z, boolean sealed) { if (!dead) cmd.doorSeal(x, y, z, sealed); }

    /**
     * A route, wanted. It is searched for in the next {@link #flush} and {@code then} is called there with the cells
     * (or null when the walker is boxed in). ty is the floor the goal is on, or NaN when any floor will do.
     */
    public int requestPath(float sx, float sy, float sz, float tx, float ty, float tz, int maxExpand, boolean doors, Consumer<int[]> then) {
        if (dead) return 0;
        int id = nextRequest++;
        boolean floor = !Float.isNaN(ty);
        int f = (doors ? AstrattoNative.PATH_DOORS : 0) | (floor ? AstrattoNative.PATH_GOAL_Y : 0) | (w.nav.mantling ? AstrattoNative.PATH_MANTLE : 0);
        cmd.pathRequest(id, f, sx, sy, sz, tx, floor ? ty : 0, tz, maxExpand);
        waiting.put(id, then);
        pathsAsked++;
        return id;
    }

    /** A line of sight, wanted: answered in the next {@link #flush}. With {@code eye}, what an eye sees through; without, what light does. */
    public int requestSight(float ax, float ay, float az, float bx, float by, float bz, boolean eye, Consumer<Boolean> then) {
        if (dead) return 0;
        int id = nextRequest++;
        cmd.sightRequest(id, eye ? AstrattoNative.SIGHT_EYE : AstrattoNative.SIGHT_LIGHT, ax, ay, az, bx, by, bz);
        seeing.put(id, then);
        return id;
    }

    public int pending() { return waiting.size() + seeing.size(); }

    /** The tick's batch goes across; the engine runs; what it answers is handed out before this returns. */
    public void flush(float dt) {
        if (dead) return;
        if (jump != Nav.JUMP_COST || ladder != Nav.LADDER_COST || mantle != Nav.MANTLE_COST) {
            jump = Nav.JUMP_COST; ladder = Nav.LADDER_COST; mantle = Nav.MANTLE_COST;
            // tuning has to be in force before this tick's routes: it is sent on its own, ahead of the batch
            CommandBuffer t = new CommandBuffer(64);
            t.navTuning(jump, ladder, mantle);
            engine.frame(t, 0, null);
            t.free();
        }
        if (cmd.isEmpty()) return;
        frames++;
        if (!engine.frame(cmd, dt, sink)) {
            // the engine would not take the batch: nobody is left waiting on it
            System.out.println("[astratto] frame refused: " + engine.lastError());
            for (Consumer<int[]> c : new java.util.ArrayList<>(waiting.values())) c.accept(null);
            waiting.clear(); seeing.clear();
        }
    }

    public static String describe() { NativeEngine e = engine(); return e == null ? "native core not in use (" + (enabled ? AstrattoNative.reason() : "ABS_NATIVE=0") + ")" : AstrattoNative.buildInfo() + "\n" + e.stats(); }
}
