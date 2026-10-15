package absentialis.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Authored/seeded map metadata produced by the city generator: loot hotspots, extraction points,
 * insertion points, landmarks, the street waypoint graph, faction posts, nests and
 * infrastructure sites. Separate from voxel data so gameplay can reason about the city.
 */
public final class MapData {

    public static final class LootSpot {
        public final int x, y, z;
        public final String table;
        public final District district;
        public final long id;
        public boolean named;      // hand-placed named stash (persistently looted)
        public String tag;         // optional quest/document tag
        public LootSpot(int x, int y, int z, String table, District d) {
            this.x = x; this.y = y; this.z = z; this.table = table; this.district = d;
            this.id = ((long) x << 40) ^ ((long) y << 20) ^ z;
        }
    }

    public enum ExitType { PERMANENT, CONDITIONAL, TIME_WINDOW, SIGNALED, EMERGENCY }

    public static final class Exit {
        public final String id, name;
        public final int x, y, z;
        public final ExitType type;
        public final District district;
        public String condition = "";   // e.g. "rep:military:0", "pay:400", "item:key.metro", "power", "window:8-18", "item:flare"
        public String faction = "";     // faction controlling the exit (checkpoints)
        public float radius = 4.5f;
        public Exit(String id, String name, int x, int y, int z, ExitType t, District d) {
            this.id = id; this.name = name; this.x = x; this.y = y; this.z = z; this.type = t; this.district = d;
        }
    }

    public static final class Point {
        public final String name, kind;
        public final int x, y, z;
        public final District district;
        public String faction = "";
        public Point(String name, String kind, int x, int y, int z, District d) {
            this.name = name; this.kind = kind; this.x = x; this.y = y; this.z = z; this.district = d;
        }
    }

    public static final class Node {
        public final int id, x, y, z;
        public final District district;
        public final List<Integer> links = new ArrayList<>();
        /** street (carriageway survey), door, yard, post or nest - see {@link Nav#annotate}. */
        public String kind = "street";
        public Node(int id, int x, int y, int z, District d) { this.id = id; this.x = x; this.y = y; this.z = z; this.district = d; }
    }

    /** One room of a generated building: its purpose, its floor and the cells it occupies (inclusive, world coordinates). */
    public static final class Room {
        public final int id, floor, x0, z0, x1, z1, y;
        public final String type;
        /** Rooms this one is joined to by a doorway (indices into the building's room list): the room graph. */
        public final List<Integer> links = new ArrayList<>();
        /** Set by the generation validator: a body can walk here from the building's entrance. */
        public boolean reachable;
        /** Circulation (hall, corridor, stairwell) rather than a destination. */
        public boolean circulation;
        /** 1.0: the block this room's walls are finished in (0 = whatever the wall cells are). Where a wall is shared with a room finished otherwise, each side is shown its own (the decal engine lines it). */
        public int wallBlock;
        /** 1.0: how many clear cells high the room is (0 = a storey of the building it is in). */
        public int height;
        /** 1.0: a dado - the lowest dadoH courses of the walls finished in another block - and a cornice, the top course under the ceiling (0 = none). */
        public int dadoBlock, dadoH, corniceBlock;
        /** 1.0: what the ceiling is finished in: a surface of its own under whatever the storey above stands on (0 = none: the underside of the floor above shows). */
        public int ceilBlock;
        public Room(int id, String type, int floor, int x0, int z0, int x1, int z1, int y) { this.id = id; this.type = type; this.floor = floor; this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; this.y = y; }
        public int cx() { return (x0 + x1) / 2; }
        public int cz() { return (z0 + z1) / 2; }
        public boolean contains(int x, int z) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }
    }

    /** A doorway the generator cut: where, which rooms it joins (-1 = outside) and what hangs in it. */
    public static final class Doorway {
        public final int x, y, z, roomA, roomB;
        /** wood, metal, glass, bars, roller or open (an arch with no leaf). */
        public final String kind;
        public final boolean exterior;
        public Doorway(int x, int y, int z, int roomA, int roomB, String kind, boolean exterior) { this.x = x; this.y = y; this.z = z; this.roomA = roomA; this.roomB = roomB; this.kind = kind; this.exterior = exterior; }
    }

    /** A generated structure: footprint, lot, room graph, doorways and the verdict of the validator. */
    public static final class Building {
        public int id;
        public String archetype = "", name = "", landmark = "", style = "";
        public District district;
        /** Footprint (inclusive) and the lot it stands on (half-open, as Streets.Rect). */
        public int x0, z0, x1, z1, lotX0, lotZ0, lotX1, lotZ1, floors, front, topY;
        public final List<Room> rooms = new ArrayList<>();
        public final List<Doorway> doors = new ArrayList<>();
        /** The standable cell just outside the main entrance (where navigation and validation start). */
        public int entryX, entryY, entryZ;
        public boolean valid = true, repaired;
        /** 0.9: drawn by hand room by room (world.gen.Blueprint) rather than grown from an archetype: its materials are its author's choice, not a style's. */
        public boolean authored;
        public final List<String> problems = new ArrayList<>();
        public boolean contains(int x, int z) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }
    }

    /** A landmark compound as planned: which district it anchors, its lot and whether it is the headquarters. */
    public static final class Landmark {
        public String id = "", name = "", faction = "";
        public District district;
        public int x0, z0, x1, z1, front;
        public boolean headquarters;
        public final List<Integer> buildings = new ArrayList<>();
        public int cx() { return (x0 + x1) / 2; }
        public int cz() { return (z0 + z1) / 2; }
    }

    /** 1.0: a prop (engine.props): a model stood in the world. (x0, z0)-(x1, z1) are the cells it covers, y its floor cell, rot its quarter turn. */
    public static final class Prop {
        public final String model;
        public final int x0, z0, x1, z1, y, rot;
        public float lift;
        public Prop(String model, int x0, int z0, int x1, int z1, int y, int rot) { this.model = model; this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; this.y = y; this.rot = rot; }
    }
    public final List<Prop> props = new ArrayList<>();
    public final List<Building> buildings = new ArrayList<>();
    public final List<Landmark> anchors = new ArrayList<>();
    /** The layout the world was generated from (district regions, roads, lots); null for hand-built test worlds. */
    public absentialis.world.gen.WorldPlan plan;
    /** What the generation validator found and repaired (null for hand-built test worlds). */
    public absentialis.world.gen.GenReport report;

    /** The building whose footprint contains a column, or null. */
    public Building buildingAt(int x, int z) {
        if (buildingIndex == null) {
            buildingIndex = new short[World.W * World.D];
            for (Building b : buildings) for (int bx = Math.max(0, b.x0); bx <= Math.min(World.W - 1, b.x1); bx++) for (int bz = Math.max(0, b.z0); bz <= Math.min(World.D - 1, b.z1); bz++) buildingIndex[bx * World.D + bz] = (short) (b.id + 1);
        }
        if (x < 0 || z < 0 || x >= World.W || z >= World.D) return null;
        int i = buildingIndex[x * World.D + z];
        return i == 0 ? null : buildings.get(i - 1);
    }
    private short[] buildingIndex;

    public Landmark anchor(String id) { for (Landmark l : anchors) if (l.id.equals(id)) return l; return null; }

    public final List<LootSpot> loot = new ArrayList<>();
    public final List<Exit> exits = new ArrayList<>();
    public final List<Point> insertions = new ArrayList<>();
    public final List<Point> landmarks = new ArrayList<>();
    public final List<Point> posts = new ArrayList<>();       // faction guard posts / camps / shrines / checkpoints
    public final List<Point> nests = new ArrayList<>();       // infected nests
    /** 1.0: places inside landmarks where an operation finds the dead: name = the landmark's id, kind = "infected", or the special that lives there ("butcher", "tank"). */
    public final List<Point> lairs = new ArrayList<>();
    public final List<Point> sites = new ArrayList<>();       // infrastructure / mission sites (substation, purifier ...)
    public final List<Point> documents = new ArrayList<>();   // document / signal spots
    public final List<Point> lampsOnPower = new ArrayList<>(); // street/interior lamps that depend on district power
    public final List<Point> barricades = new ArrayList<>();  // persistent barricade spots (breakable shortcuts)
    public final List<Node> nodes = new ArrayList<>();
    /** Burn barrels standing in the world right now: {x, y, z} block positions (rebuilt by World.scanFires before a raid). */
    public List<int[]> fires;
    /** Placement audit results (world.gen.Validation): props lowered onto support / removed / still floating, nav nodes repaired / unusable. */
    public int propsGrounded, propsRemoved, propsFloating, nodesFixed, nodesBlocked, lootPruned;

    public void clear() {
        loot.clear(); exits.clear(); insertions.clear(); landmarks.clear(); posts.clear(); nests.clear(); lairs.clear();
        sites.clear(); documents.clear(); lampsOnPower.clear(); barricades.clear(); nodes.clear(); index = null;
        buildings.clear(); anchors.clear(); buildingIndex = null; plan = null; report = null;
        props.clear();
    }

    public Node addNode(int x, int y, int z, District d) {
        for (Node n : nodes) if (Math.abs(n.x - x) < 6 && Math.abs(n.z - z) < 6) return n;
        Node n = new Node(nodes.size(), x, y, z, d);
        nodes.add(n);
        index = null;
        return n;
    }

    public void link(Node a, Node b) {
        if (a == b) return;
        if (!a.links.contains(b.id)) a.links.add(b.id);
        if (!b.links.contains(a.id)) b.links.add(a.id);
    }

    private static final int CELL = 32, CELLS = World.W / CELL;
    private List<Node>[] index;

    /** Nearest waypoint, found through a coarse grid so hundreds of agents can ask every tick. */
    @SuppressWarnings("unchecked")
    public Node nearestNode(float x, float z) {
        List<Node>[] ix = index;
        if (ix == null || indexed != nodes.size()) {
            ix = new List[CELLS * CELLS];
            for (Node n : nodes) { int c = cell(n.x) * CELLS + cell(n.z); if (ix[c] == null) ix[c] = new ArrayList<>(); ix[c].add(n); }
            index = ix; indexed = nodes.size();
        }
        int cx = cell(x), cz = cell(z);
        Node best = null; float bd = Float.MAX_VALUE;
        for (int ring = 0; ring < CELLS; ring++) {
            for (int a = cx - ring; a <= cx + ring; a++) for (int b = cz - ring; b <= cz + ring; b++) {
                if (Math.max(Math.abs(a - cx), Math.abs(b - cz)) != ring || a < 0 || b < 0 || a >= CELLS || b >= CELLS) continue;
                List<Node> l = ix[a * CELLS + b];
                if (l == null) continue;
                for (Node n : l) { float d = (n.x - x) * (n.x - x) + (n.z - z) * (n.z - z); if (d < bd) { bd = d; best = n; } }
            }
            // a hit in ring r can still be beaten by a node in ring r+1, never by one further out
            if (best != null && bd <= (float) ring * CELL * ring * CELL) break;
        }
        return best;
    }

    private int indexed = -1;
    private static int cell(float v) { return Math.max(0, Math.min(CELLS - 1, (int) v / CELL)); }

    public Point site(String name) { for (Point p : sites) if (p.name.equals(name)) return p; return null; }
    public Exit exit(String id) { for (Exit e : exits) if (e.id.equals(id)) return e; return null; }
    public LootSpot lootAt(int x, int y, int z) { for (LootSpot l : loot) if (l.x == x && l.y == y && l.z == z) return l; return null; }
}
