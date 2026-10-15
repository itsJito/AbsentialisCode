package absentialis.scene;

import absentialis.gameplay.items.ItemInstance;
import absentialis.world.MapData;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.List;

/**
 * Spinels on ceilings (0.8). Here and there in the city a spinel is lodged overhead: in the ceiling of a room, under
 * an awning, a walkway or a canopy outside. It cannot be reached. It can be shot: a round that passes through it
 * knocks it loose, it falls, and then it is an ordinary thing on the ground to be picked up.
 *
 * Where they are is settled when the operation begins, from the profile's seed and the day - the same city on the
 * same day has the same stones, and a new day moves them - so they are found by looking up, not by remembering.
 * Nothing is placed where it could not be seen from the floor beneath it.
 */
public final class CeilingSpinels {
    public static final class Stone {
        public float x, y, z;
        public String item;
        public boolean exterior, fallen;
        /** The floor under it (where it lands). */
        public float floorY;
    }

    /** One building in this many has a stone indoors; one street point in this many is tried for an overhang; and the most there can be of each. */
    public static int ONE_BUILDING_IN = 7, ONE_NODE_IN = 9, MAX_INTERIOR = 26, MAX_EXTERIOR = 12;
    /** How near a round has to pass (metres), and the lowest and highest ceiling a stone is put on (blocks above the floor). */
    public static float HIT_RADIUS = 0.24f;
    public static int MIN_CEILING = 3, MAX_CEILING = 9;
    public static final String[] KINDS = {"treasure.spinel_pink", "treasure.spinel_pink", "treasure.spinel_blue", "treasure.spinel_blue", "treasure.spinel_red"};

    private final Raid raid;
    public final List<Stone> stones = new ArrayList<>();
    /** Stones shot down this operation (tests, and the debrief if it wants it). */
    public int shotDown;

    public CeilingSpinels(Raid raid) {
        this.raid = raid;
        if (raid.world.dev) { devRow(); return; }
        long seed = raid.profile.seed * 31 + raid.profile.zone.day * 1009L;
        World w = raid.world;
        int interior = 0, exterior = 0;
        for (MapData.Building b : w.map.buildings) {
            if (interior >= MAX_INTERIOR) break;
            if (b.rooms.isEmpty() || Math.floorMod(mix(seed, b.id), ONE_BUILDING_IN) != 0) continue;
            // a room people stay in rather than pass through, if the building has one
            MapData.Room room = null;
            for (int k = 0; k < b.rooms.size() && room == null; k++) { MapData.Room r = b.rooms.get(Math.floorMod((int) (mix(seed, b.id * 7 + 3) >>> 8) + k, b.rooms.size())); if (!r.circulation) room = r; }
            if (room == null) continue;
            // over open floor: the middle of the room if nothing stands there, otherwise the nearest clear cell to it
            Stone s = null;
            for (int ring = 0; ring <= 3 && s == null; ring++) for (int ox = -ring; ox <= ring && s == null; ox++) for (int oz = -ring; oz <= ring && s == null; oz++) {
                if (Math.max(Math.abs(ox), Math.abs(oz)) != ring) continue;
                int x = room.cx() + ox, z = room.cz() + oz;
                if (!room.contains(x, z)) continue;
                for (int fy = room.y; fy <= room.y + 1 && s == null; fy++) s = under(w, x, fy, z, false);
            }
            if (s == null) continue;
            s.item = KINDS[Math.floorMod((int) (mix(seed, b.id * 13 + 5) >>> 4), KINDS.length)];
            stones.add(s);
            interior++;
        }
        for (MapData.Node n : w.map.nodes) {
            if (exterior >= MAX_EXTERIOR) break;
            if (Math.floorMod(mix(seed, n.id * 17 + 11), ONE_NODE_IN) != 0) continue;
            // out of doors: something overhead with open air under it - an awning, a canopy, a walkway
            Stone s = null;
            for (int[] o : new int[][]{{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {3, 3}, {-3, -3}}) {
                int x = n.x + o[0], z = n.z + o[1];
                if (!World.inBounds(x, n.y, z) || insideBuilding(w, x, z)) continue;
                s = under(w, x, n.y, z, true);
                if (s != null) break;
            }
            if (s == null) continue;
            s.item = KINDS[Math.floorMod((int) (mix(seed, n.id * 29 + 1) >>> 4), KINDS.length)];
            stones.add(s);
            exterior++;
        }
    }

    /** The developer map: one of each over the start of the road, so the thing can be tried without looking for it. */
    private void devRow() {
        // 1.1: in ceilings, like everywhere else. (They were hung in the open air over the road, four blocks up, with nothing
        // above them.) The nearest three ceilings over floor that can be stood on, nearest the start of the road first.
        World w = raid.world;
        String[] kinds = {"treasure.spinel_pink", "treasure.spinel_blue", "treasure.spinel_red"};
        int px = (int) raid.player.x, pz = (int) raid.player.z;
        for (int ring = 2; ring <= 90 && stones.size() < 3; ring++) for (int ox = -ring; ox <= ring && stones.size() < 3; ox++) for (int oz = -ring; oz <= ring && stones.size() < 3; oz++) {
            if (Math.max(Math.abs(ox), Math.abs(oz)) != ring) continue;
            int x = px + ox, z = pz + oz;
            if (!World.inBounds(x, World.GROUND, z)) continue;
            int gy = raid.groundY(x, z);
            if (gy < 0) continue;
            Stone s = under(w, x, gy, z, !insideBuilding(w, x, z));
            if (s == null) continue;
            boolean near = false;
            for (Stone o : stones) near |= Math.abs(o.x - s.x) + Math.abs(o.z - s.z) < 6;
            if (near) continue;
            s.item = kinds[stones.size()];
            stones.add(s);
        }
    }

    private static long mix(long seed, long k) { long h = seed ^ k * 0x9E3779B97F4A7C15L; h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32; return h; }

    private static boolean insideBuilding(World w, int x, int z) { for (MapData.Building b : w.map.buildings) if (b.contains(x, z)) return true; return false; }

    /** A stone on the first ceiling above a floor cell, if there is one at a height worth looking up at and nothing between it and the floor. */
    private static Stone under(World w, int x, int floorY, int z, boolean exterior) {
        if (!World.inBounds(x, floorY, z) || w.solid(x, floorY, z) || !w.solid(x, floorY - 1, z)) return null;
        for (int y = floorY + 1; y <= floorY + MAX_CEILING && y < World.H - 1; y++) {
            if (!w.solid(x, y, z)) continue;
            if (y - floorY < MIN_CEILING) return null;
            Stone s = new Stone();
            s.x = x + 0.5f; s.z = z + 0.5f; s.y = y - 0.14f; s.floorY = floorY; s.exterior = exterior;
            return s;
        }
        return null;
    }

    public int remaining() { int n = 0; for (Stone s : stones) if (!s.fallen) n++; return n; }

    /**
     * A round has travelled from (px, py, pz) along a unit direction for {@code len} metres. Any stone it passed
     * through is knocked down: it becomes an item falling to the floor beneath it. The round is not stopped.
     */
    public void shot(float px, float py, float pz, float dx, float dy, float dz, float len) {
        for (Stone s : stones) {
            if (s.fallen) continue;
            float ax = s.x - px, ay = s.y - py, az = s.z - pz, along = ax * dx + ay * dy + az * dz;
            if (along < 0 || along > len + HIT_RADIUS) continue;
            float cx = ax - dx * along, cy = ay - dy * along, cz = az - dz * along;
            if (cx * cx + cy * cy + cz * cz > HIT_RADIUS * HIT_RADIUS) continue;
            knock(s);
        }
    }

    /** Knocks a stone down (the console can do this too). */
    public void knock(Stone s) {
        if (s.fallen) return;
        s.fallen = true;
        shotDown++;
        ItemInstance it = new ItemInstance(s.item);
        it.foundInRaid = true; it.origin = "ceiling";
        raid.dropItem(it, s.x, s.floorY + 0.25f, s.z);
        raid.cue("glass_tinkle", s.x, s.y, s.z, 0.7f, 40);
        raid.emitSound(s.x, s.floorY + 0.3f, s.z, 5, "glass_tinkle", raid.player.id, false);
        raid.msg("A spinel drops from the ceiling.", 0xFFE2A034);
    }

    /** The nearest stone still up, within a distance (for the console, and for the hint the HUD gives when one is close overhead). */
    public Stone nearest(float x, float y, float z, float within) {
        Stone best = null;
        float bd = within * within;
        for (Stone s : stones) { if (s.fallen) continue; float d = (s.x - x) * (s.x - x) + (s.y - y) * (s.y - y) + (s.z - z) * (s.z - z); if (d < bd) { bd = d; best = s; } }
        return best;
    }
}
