package absentialis.world.gen;

import static absentialis.world.gen.Gen.G;

import absentialis.world.Block;
import absentialis.world.MapData;
import absentialis.world.World;
import java.util.HashSet;
import java.util.Set;

/**
 * A building drawn by hand (0.9). The architect (world.gen.arch) grows a building from an archetype: so many
 * storeys, a kind of plan, rooms by weight. That is right for a street of houses and wrong for a place that has to be
 * a particular place - the Chernobyl plant, the Spencer mansion - where what matters is that this room is here, that
 * tall, next to that one. A blueprint is the other way round: the caller says where every room is, how high it is,
 * what it is made of and where its doors are, on the lot's own local grid, and this class does the masonry and keeps
 * the books - the building's record, its rooms and doorways (what the AI searches by, what the validator walks),
 * its containers.
 *
 * Conventions, the same as the architect's: a room's floor slab is at {@code y0 - 1}, the room is clear from y0 up
 * for its height, its ceiling slab is at {@code y0 + h}; a room's interior is the box it is given and its walls stand
 * one cell outside that, shared with whatever room is on the other side. A room is recorded with the y of its slab.
 */
final class Blueprint {
    final Site s;
    final Gen g;
    final MapData.Building rec = new MapData.Building();
    /** Cells that are the inside of some room (a wall is never built through a room that is already there). */
    private final Set<Long> inside = new HashSet<>();
    private int minA = Integer.MAX_VALUE, minB = Integer.MAX_VALUE, maxA = Integer.MIN_VALUE, maxB = Integer.MIN_VALUE, topY = G;

    Blueprint(Site s, MapData.Landmark lm, String archetype, String name, String style) {
        this.s = s; this.g = s.g;
        rec.id = g.map.buildings.size();
        rec.archetype = archetype; rec.name = name; rec.landmark = lm.id; rec.style = style; rec.district = s.district;
        rec.lotX0 = s.lot.x0(); rec.lotZ0 = s.lot.z0(); rec.lotX1 = s.lot.x1(); rec.lotZ1 = s.lot.z1();
        rec.front = s.f.front;
        rec.authored = true;
    }

    private static long key(int a, int b, int y) { return ((long) (a + 512) << 40) | ((long) (b + 512) << 20) | (y & 0xFFFFF); }
    void put(int a, int b, int y, int id) { if (s.in(a, b) && y > 0 && y < World.H - 1) s.set(a, b, y, id); }
    int at(int a, int b, int y) { return s.in(a, b) ? s.get(a, b, y) : 0; }
    void box(int a0, int b0, int a1, int b1, int y0, int y1, int id) { for (int a = Math.min(a0, a1); a <= Math.max(a0, a1); a++) for (int b = Math.min(b0, b1); b <= Math.max(b0, b1); b++) for (int y = y0; y <= y1; y++) put(a, b, y, id); }

    private void grow(int a0, int b0, int a1, int b1, int top) {
        minA = Math.min(minA, a0); minB = Math.min(minB, b0); maxA = Math.max(maxA, a1); maxB = Math.max(maxB, b1); topY = Math.max(topY, top);
        for (int a = a0; a <= a1; a++) for (int b = b0; b <= b1; b++) s.keep(a, b);
    }

    /** A solid mass with nothing inside it worth a room: a plinth, a buttress, a block of plant nobody goes into. */
    void mass(int a0, int b0, int a1, int b1, int y0, int y1, int id) { box(a0, b0, a1, b1, y0, y1, id); grow(a0, b0, a1, b1, y1); }

    /**
     * A room. Interior a0..a1 x b0..b1, clear from y0 for h; walls one cell out. Pass 0 for wall, floor or ceiling to
     * leave what is there (an open side, a floor that is somebody else's ceiling, a hall open to the sky of a taller one).
     */
    MapData.Room room(String type, int floor, int a0, int b0, int a1, int b1, int y0, int h, int wall, int floorId, int ceiling) {
        for (int a = a0 - 1; a <= a1 + 1; a++) for (int b = b0 - 1; b <= b1 + 1; b++) {
            boolean in = a >= a0 && a <= a1 && b >= b0 && b <= b1;
            if (floorId != 0 && in) put(a, b, y0 - 1, floorId);
            // 1.0: a storey's floor lies on the ceiling of the one below, so under an upper room's walls there is a course of wall too
            if (!in && wall != 0 && at(a, b, y0 - 1) == 0 && !inside.contains(key(a, b, y0 - 1))) put(a, b, y0 - 1, wall);
            if (ceiling != 0) { if (in || !inside.contains(key(a, b, y0 + h))) put(a, b, y0 + h, ceiling); }
            for (int y = y0; y < y0 + h; y++) {
                if (in) { put(a, b, y, Block.AIR); inside.add(key(a, b, y)); }
                else if (wall != 0 && !inside.contains(key(a, b, y))) put(a, b, y, wall);
            }
        }
        grow(a0 - 1, b0 - 1, a1 + 1, b1 + 1, y0 + h);
        int xa = s.x(a0, b0), za = s.z(a0, b0), xb = s.x(a1, b1), zb = s.z(a1, b1);
        MapData.Room r = new MapData.Room(rec.rooms.size(), type, floor, Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb), y0 - 1);
        r.reachable = true;
        r.wallBlock = wall;
        r.height = h;
        rec.rooms.add(r);
        rec.floors = Math.max(rec.floors, floor + 1);
        return r;
    }

    /** An opening through a wall: w cells along the wall (dirA/dirB says which way it runs), h high. */
    void opening(int a, int b, int y0, int h, int w, boolean alongA) {
        for (int k = 0; k < w; k++) for (int y = y0; y < y0 + h; y++) { int pa = alongA ? a + k : a, pb = alongA ? b : b + k; put(pa, pb, y, Block.AIR); inside.add(key(pa, pb, y)); }
    }

    /** A door in a wall cell: two blocks high, with the wall kept above it. A double door is two of them side by side. */
    void door(int a, int b, int y0, int id, boolean exterior) {
        put(a, b, y0, id); put(a, b, y0 + 1, id);
        inside.add(key(a, b, y0)); inside.add(key(a, b, y0 + 1));
        rec.doors.add(new MapData.Doorway(s.x(a, b), y0, s.z(a, b), -1, -1, Block.ALL[id].mat == absentialis.world.Material.METAL ? "metal" : Block.ALL[id].mat == absentialis.world.Material.GLASS ? "glass" : "wood", exterior));
        if (exterior && (rec.entryX == 0 && rec.entryZ == 0)) entry(a, b, y0);
    }

    void entry(int a, int b, int y) { rec.entryX = s.x(a, b); rec.entryY = y; rec.entryZ = s.z(a, b); }

    /** Glass in a wall: every cell of the box that is wall now becomes a pane. */
    void window(int a0, int b0, int a1, int b1, int y0, int y1, int glass) {
        for (int a = Math.min(a0, a1); a <= Math.max(a0, a1); a++) for (int b = Math.min(b0, b1); b <= Math.max(b0, b1); b++) for (int y = y0; y <= y1; y++) if (!inside.contains(key(a, b, y))) put(a, b, y, glass);
    }

    /**
     * A straight flight of stairs: from (a, b) at floor level y0, one tread up for each step in the direction (da, db),
     * `rise` treads, `width` cells wide (to the side). The headroom over every tread is cleared - through the slab above -
     * and the space under the flight is filled, so it is a staircase and not a row of blocks in the air.
     */
    void flight(int a, int b, int da, int db, int y0, int rise, int width, int tread, int under) {
        for (int k = 0; k < rise; k++) for (int wdt = 0; wdt < width; wdt++) {
            int pa = a + da * k + (da == 0 ? wdt : 0), pb = b + db * k + (db == 0 ? wdt : 0);
            for (int y = y0; y < y0 + k; y++) put(pa, pb, y, under);
            put(pa, pb, y0 + k, tread);
            for (int y = y0 + k + 1; y <= y0 + k + 3; y++) { put(pa, pb, y, Block.AIR); inside.add(key(pa, pb, y)); }
        }
    }

    /** Re-faces walls: every wall cell (not the inside of a room, not glass, not a door) in the box that is `from` - or anything solid when from is 0 - becomes `to`. */
    void reface(int a0, int b0, int a1, int b1, int y0, int y1, int from, int to) {
        for (int a = Math.min(a0, a1); a <= Math.max(a0, a1); a++) for (int b = Math.min(b0, b1); b <= Math.max(b0, b1); b++) for (int y = y0; y <= y1; y++) {
            if (inside.contains(key(a, b, y))) continue;
            int id = at(a, b, y);
            if (id == 0 || Block.ALL[id].is(Block.DOOR) || Block.ALL[id].mat == absentialis.world.Material.GLASS || !Block.ALL[id].opaque()) continue;
            if (from == 0 || id == from) put(a, b, y, to);
        }
    }

    /** A ceiling light under the slab over a room cell (y is the slab's y). */
    void light(int a, int b, int slabY) { lampAt(a, b, slabY - 1); }

    /** A lamp that runs off the district's power, like every other lamp in the city: dark when the power is out, a light source when it is on. */
    void lampAt(int a, int b, int y) { if (s.in(a, b) && y > 0 && y < World.H - 1) g.lamp(s.x(a, b), y, s.z(a, b)); }

    /**
     * 1.0: a prop (world.gen.PropLib) on the local box (a0, b0)-(a1, b1) at y, its back toward the local direction
     * (backA, backB) - the wall it stands against. Returns false when it could not go there (something is in the way).
     */
    boolean prop(String name, int a0, int b0, int a1, int b1, int y, int backA, int backB) {
        if (!s.in(a0, b0) || !s.in(a1, b1)) return false;
        int bx = s.x(a0 + backA, b0 + backB) - s.x(a0, b0), bz = s.z(a0 + backA, b0 + backB) - s.z(a0, b0);
        int rot = bz < 0 ? 0 : bx > 0 ? 1 : bz > 0 ? 2 : 3;
        return g.prop(name, s.x(a0, b0), s.z(a0, b0), s.x(a1, b1), s.z(a1, b1), y, rot) != null;
    }

    /**
     * 1.1: curtains at a room's windows. Every cell along the inside of the box (a0, b0)-(a1, b1) that has glass in the
     * wall beside it at y gets the named hanging (where nothing is already standing there). Returns how many were hung.
     */
    int curtains(int a0, int b0, int a1, int b1, int y, String name, String other) {
        int n = 0;
        for (int a = a0; a <= a1; a++) {
            if (glass(a, b0 - 1, y)) n += prop((a + b0) % 3 == 0 ? other : name, a, b0, y, 0, -1) ? 1 : 0;
            if (glass(a, b1 + 1, y)) n += prop((a + b1) % 3 == 0 ? other : name, a, b1, y, 0, 1) ? 1 : 0;
        }
        for (int b = b0; b <= b1; b++) {
            if (glass(a0 - 1, b, y)) n += prop((a0 + b) % 3 == 0 ? other : name, a0, b, y, -1, 0) ? 1 : 0;
            if (glass(a1 + 1, b, y)) n += prop((a1 + b) % 3 == 0 ? other : name, a1, b, y, 1, 0) ? 1 : 0;
        }
        return n;
    }
    private boolean glass(int a, int b, int y) { int id = at(a, b, y); return id != 0 && Block.ALL[id].mat == absentialis.world.Material.GLASS && !Block.ALL[id].is(Block.DOOR); }

    /**
     * 1.1: what the walls and ceilings of a working building carry. A plant's halls were furnished in 1.0 with their
     * machines and nothing else, and looked like empty boxes with a machine in them; this goes round a room and gives it
     * the rest - runs of pipe, duct and cable tray under the ceiling, gauges, valves, fuse boxes, signs and extinguishers
     * along the walls, and (in a room wide enough to spare the floor) drums, crates, carts and pipe stacks standing against
     * them. Never beside a door or an opening, never in a passage less than four across. Deterministic: it goes by the cell.
     */
    int industrial(MapData.Room r, boolean floorClutter) {
        int n = 0, fy = r.y + 1, top = fy + r.height - 1, w = r.x1 - r.x0 + 1, d = r.z1 - r.z0 + 1;
        String[] ceil = {"pipes_ceiling", "duct", "cable_tray", "pipes_ceiling", "fluorescent_dead"}, wall = {"pipes_wall", "gauges", "valve_wheel", "fuse_box", "sign_radiation", "fire_extinguisher", "vent_grille", "intercom", "phone_wall"},
                floor = {"drum_hazard", "drum_oil", "crate_stack", "cart_tool", "pipe_stack", "tank_pressure", "drum_oil", "boxes_cardboard", "respirator_rack", "switchgear"};
        boolean alongX = w >= d;
        // under the ceiling: two or three runs the length of the room
        int runs = Math.max(1, Math.min(3, (alongX ? d : w) / 4));
        for (int k = 0; k < runs; k++) {
            int off = (alongX ? d : w) * (k + 1) / (runs + 1);
            for (int i = 1; i + 1 < (alongX ? w : d); i += 3) {
                int x = alongX ? r.x0 + i : r.x0 + off, z = alongX ? r.z0 + off : r.z0 + i, x1 = alongX ? x + 1 : x, z1 = alongX ? z : z + 1;
                if (g.w.get(x, top, z) != 0 || g.w.get(x1, top, z1) != 0 || g.w.get(x, top + 1, z) == 0) continue;
                int pick = Math.floorMod(x * 7 + z * 13 + k * 5, 11);
                if (pick < 2) { if (g.prop("lamp_ceiling_lit", x, z, x, z, top, 0) != null) n++; continue; }
                if (g.prop(ceil[(k + pick) % ceil.length], x, z, x1, z1, top, alongX ? 0 : 1) != null) n++;
            }
        }
        // along the walls
        for (int side = 0; side < 4; side++) {
            int len = side < 2 ? w : d;
            for (int i = 1; i < len - 1; i++) {
                int x = side == 0 ? r.x0 + i : side == 1 ? r.x0 + i : side == 2 ? r.x0 : r.x1, z = side == 0 ? r.z0 : side == 1 ? r.z1 : r.z0 + i;
                int bx = side == 2 ? -1 : side == 3 ? 1 : 0, bz = side == 0 ? -1 : side == 1 ? 1 : 0, lx = side < 2 ? 1 : 0, lz = side < 2 ? 0 : 1;
                // a real wall behind this cell and behind the cells either side of it (so: not a doorway, not beside one)
                boolean solidWall = true;
                for (int k = -1; k <= 1 && solidWall; k++) for (int h = 0; h < 2 && solidWall; h++) { int id = g.w.get(x + bx + lx * k, fy + h, z + bz + lz * k); solidWall = id != 0 && Block.ALL[id].opaque() && !Block.ALL[id].is(Block.DOOR); }
                if (!solidWall) continue;
                int rot = bz < 0 ? 0 : bx > 0 ? 1 : bz > 0 ? 2 : 3, hash = Math.floorMod(x * 31 + z * 17 + r.y * 3, 97);
                if (i % 4 == 1 && g.w.get(x, fy + 1, z) == 0) { if (g.prop(wall[hash % wall.length], x, z, x, z, fy + 1, rot) != null) n++; }
                if (floorClutter && Math.min(w, d) >= 6 && i % 5 == 3 && hash % 3 != 0) { if (g.prop(floor[hash % floor.length], x, z, x, z, fy, rot) != null) n++; }
            }
        }
        return n;
    }

    /** A one-cell prop. */
    boolean prop(String name, int a, int b, int y, int backA, int backB) { return prop(name, a, b, a, b, y, backA, backB); }

    /** 1.0: how a room's walls are finished on its own side: the paper or paint, a dado of so many courses, a cornice under the ceiling (0 = none). */
    MapData.Room finish(MapData.Room r, int wall, int dado, int dadoH, int cornice) { r.wallBlock = wall; r.dadoBlock = dado; r.dadoH = dadoH; r.corniceBlock = cornice; return r; }

    /** 1.0: a place in this building where the dead are found (see MapData.lairs). */
    void lair(String kind, int a, int b, int y) { if (s.in(a, b)) g.map.lairs.add(new MapData.Point(rec.landmark, kind, s.x(a, b), y, s.z(a, b), s.district)); }

    /**
     * 1.1: something that is always here - a map site of kind "item", which scene.Raid puts on the floor at the start of
     * every operation. "weapon.x" is that weapon, loaded; "ammo.yx12" is twelve of them; "ammo.y*" is a full box.
     */
    void item(String id, int a, int b, int y) { if (s.in(a, b)) g.map.sites.add(new MapData.Point(id, "item", s.x(a, b), y, s.z(a, b), s.district)); }

    /** A container with a loot table, anywhere in the building. */
    void loot(int a, int b, int y, int block, String table) { if (s.in(a, b)) g.container(s.x(a, b), y, s.z(a, b), block, table); }

    /** Closes the record: the footprint, the height, the entrance (the first exterior door unless one was named), and onto the map. */
    MapData.Building done(MapData.Landmark lm) {
        int xa = s.x(minA, minB), za = s.z(minA, minB), xb = s.x(maxA, maxB), zb = s.z(maxA, maxB);
        rec.x0 = Math.min(xa, xb); rec.z0 = Math.min(za, zb); rec.x1 = Math.max(xa, xb); rec.z1 = Math.max(za, zb);
        rec.topY = topY;
        if (rec.entryX == 0 && rec.entryZ == 0) { rec.entryX = (rec.x0 + rec.x1) / 2; rec.entryY = G; rec.entryZ = (rec.z0 + rec.z1) / 2; }
        g.map.buildings.add(rec);
        lm.buildings.add(rec.id);
        return rec;
    }
}
