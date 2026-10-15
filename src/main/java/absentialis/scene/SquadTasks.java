package absentialis.scene;

import absentialis.entity.Actor;
import absentialis.gameplay.inventory.Container;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.items.ItemInstance;
import absentialis.world.Block;
import absentialis.world.MapData;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.0: what a team is told to do at a door. Two orders, both given by looking at a door and pressing a key:
 *
 *   CLEAR  the team stacks on the door, one of them opens it, and they go in and sweep the room behind it. Nothing
 *          about it is laid down beforehand - where they stand, which way they turn and what they shoot at are the
 *          squad's own minds working on an ordinary move order and then an ordinary sweep order, given at the right
 *          moments - so it happens differently at every door.
 *   LOOT   the same, and then the rest of the house: room by room, each swept, and whatever is in the cupboards
 *          taken as they pass. When they are done they come back and put what they found at the leader's feet.
 *
 * A team has one such task at a time; any other order to that team ends it.
 */
public final class SquadTasks {
    public static final class Task {
        public String team;
        public boolean loot;
        public int doorX, doorY, doorZ, stage, roomI, sx, sz;
        public float outX, outZ, inX, inZ, t, stageT;
        public final List<float[]> rooms = new ArrayList<>();      // {x, z, y} room middles, nearest the door first
        public final List<ItemInstance> haul = new ArrayList<>();
        public final java.util.Set<Long> searched = new java.util.HashSet<>();
        public boolean done;
    }

    private final Raid raid;
    public final List<Task> tasks = new ArrayList<>();
    /** How near the door the first of them has to be before it is opened (metres); the longest a team waits at each step (seconds); the most a team carries out. */
    public static float STACK_AT = 2.4f, STACK_WAIT = 7f, CLEAR_WAIT = 16f, ROOM_WAIT = 11f;
    public static int HAUL_MAX = 14;
    public int cleared, looted, brought, blocked;

    public SquadTasks(Raid raid) { this.raid = raid; }

    public Task of(String team) { for (Task t : tasks) if (t.team.equals(team) && !t.done) return t; return null; }
    public void cancel(String team) { Task t = of(team); if (t != null) { t.done = true; bring(t); } }

    /** The order. (dx, dz) is the way the leader is looking: the room is on the far side of the door from them. Returns how many are on the team. */
    public int order(String team, int x, int y, int z, float dx, float dz, boolean loot) {
        cancel(team);
        List<Actor> who = raid.team(team);
        if (who.isEmpty()) return 0;
        Task t = new Task();
        t.team = team; t.loot = loot; t.doorX = x; t.doorY = y; t.doorZ = z;
        // which way is in: along whichever axis the leader is facing more squarely
        int sx = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 1 : -1) : 0, sz = sx != 0 ? 0 : (dz > 0 ? 1 : -1);
        t.sx = sx; t.sz = sz;
        t.outX = x + 0.5f - sx * 1.6f; t.outZ = z + 0.5f - sz * 1.6f;
        t.inX = x + 0.5f + sx * 2.6f; t.inZ = z + 0.5f + sz * 2.6f;
        MapData.Building b = raid.world.map.buildingAt(x + sx, z + sz);
        if (b == null) b = raid.world.map.buildingAt(x, z);
        if (b != null) {
            MapData.Room first = null;
            for (MapData.Room r : b.rooms) if (x + sx * 2 >= r.x0 - 1 && x + sx * 2 <= r.x1 + 1 && z + sz * 2 >= r.z0 - 1 && z + sz * 2 <= r.z1 + 1 && Math.abs(r.y - y) <= 2) { first = r; break; }
            if (first != null) { t.inX = (first.x0 + first.x1) / 2f + 0.5f; t.inZ = (first.z0 + first.z1) / 2f + 0.5f; }
            if (loot) {
                for (MapData.Room r : b.rooms) if (r != first && r.reachable) t.rooms.add(new float[]{(r.x0 + r.x1) / 2f + 0.5f, (r.z0 + r.z1) / 2f + 0.5f, r.y});
                final float ix = t.inX, iz = t.inZ; final int fy = y;
                t.rooms.sort((p, q) -> Float.compare(Math.abs(p[2] - fy) * 30 + Math.abs(p[0] - ix) + Math.abs(p[1] - iz), Math.abs(q[2] - fy) * 30 + Math.abs(q[0] - ix) + Math.abs(q[1] - iz)));
            }
        }
        tasks.add(t);
        raid.orderHintY = y;
        raid.squadOrder(team, "move", t.outX, t.outZ, false);
        raid.say(who.get(0), loot ? "Stacking up. We will take the house." : "Stacking up on the door.", "radio_chatter");
        return who.size();
    }

    public void update(float dt) {
        for (Task t : tasks) if (!t.done) step(t, dt);
        tasks.removeIf(t -> t.done);
    }

    private boolean hostileNear(float x, float z, float y, float within) {
        for (Actor o : raid.nearby(x, z, within)) if (!o.dead && !o.isPlayer && !o.mate() && !o.advisor && Math.abs(o.y - y) < 3 && (o.infected() || o.hostileToPlayer)) return true;
        return false;
    }

    private void step(Task t, float dt) {
        List<Actor> who = new ArrayList<>();
        for (Actor a : raid.team(t.team)) if (!a.dead && !a.downed()) who.add(a);
        if (who.isEmpty()) { t.done = true; return; }
        t.t += dt; t.stageT += dt;
        Actor lead = who.get(0);
        switch (t.stage) {
            case 0 -> {
                // stacked: one of them is at the door (or it has taken too long, and they go from where they are)
                float near = 1e9f;
                for (Actor a : who) near = Math.min(near, a.distXZ(t.outX, t.outZ));
                if (near > STACK_AT && t.stageT < STACK_WAIT) return;
                Block d = raid.world.block(t.doorX, t.doorY, t.doorZ);
                if (d != null && d.is(Block.DOOR) && d.solid()) raid.toggleDoor(t.doorX, t.doorY, t.doorZ, lead);
                raid.orderHintY = t.doorY;
                raid.squadOrder(t.team, "sweep", t.inX, t.inZ, false);
                raid.say(lead, "Going in.", "radio_chatter");
                t.stage = 1; t.stageT = 0;
            }
            case 1 -> {
                boolean inside = false;
                // inside means through the door: on the far side of it, not standing in front of it
                for (Actor a : who) if ((a.x - (t.doorX + 0.5f)) * t.sx + (a.z - (t.doorZ + 0.5f)) * t.sz > 0.8f && a.distXZ(t.inX, t.inZ) < 9f) inside = true;
                if (t.stageT < CLEAR_WAIT && (!inside || t.stageT < 2.5f || hostileNear(t.inX, t.inZ, t.doorY, 9))) return;
                // nobody got in at all: that is said, and it is not called clear
                if (!inside) { blocked++; raid.say(lead, "Cannot get in.", "radio_chatter"); raid.msg(t.team.toUpperCase() + ": could not get through that door", 0xFFE2A034); t.done = true; raid.squadOrder(t.team, "follow", Float.NaN, Float.NaN, false); return; }
                cleared++;
                raid.say(lead, "Room clear.", "radio_chatter");
                raid.msg(t.team.toUpperCase() + ": room clear", 0xFFD6CEB8);
                if (!t.loot || t.rooms.isEmpty()) { if (t.loot) search(t, who); finish(t, who); return; }
                t.stage = 2; t.stageT = 0; t.roomI = -1;
            }
            case 2 -> {
                search(t, who);
                boolean next = t.roomI < 0;
                if (!next) {
                    float[] r = t.rooms.get(t.roomI);
                    boolean there = false;
                    for (Actor a : who) if (a.distXZ(r[0], r[1]) < 3f && Math.abs(a.y - r[2]) < 2.5f) there = true;
                    next = t.stageT > ROOM_WAIT || there && t.stageT > 2.2f && !hostileNear(r[0], r[1], r[2], 8);
                }
                if (!next) return;
                t.roomI++; t.stageT = 0;
                if (t.roomI >= t.rooms.size() || t.haul.size() >= HAUL_MAX || t.t > 150) { looted++; finish(t, who); return; }
                float[] r = t.rooms.get(t.roomI);
                raid.orderHintY = r[2];
                raid.squadOrder(t.team, "sweep", r[0], r[1], false);
            }
            default -> {
                // coming back: when one of them reaches the leader, what they found is put down
                for (Actor a : who) if (a.dist(raid.player) < 4.5f) { bring(t); t.done = true; return; }
                if (t.stageT > 40) { bring(t); t.done = true; }
            }
        }
    }

    /** Whoever is beside a cupboard goes through it: the best of what is in it goes into the haul. */
    private void search(Task t, List<Actor> who) {
        for (Actor a : who) {
            if (t.haul.size() >= HAUL_MAX) return;
            int ax = (int) Math.floor(a.x), ay = (int) Math.floor(a.y), az = (int) Math.floor(a.z);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 1; dy++) {
                Block b = raid.world.block(ax + dx, ay + dy, az + dz);
                if (b == null || !b.is(Block.CONTAINER)) continue;
                long key = ((long) (ax + dx) << 40) ^ ((long) (ay + dy) << 20) ^ (az + dz);
                if (!t.searched.add(key)) continue;
                Container c = raid.openContainer(ax + dx, ay + dy, az + dz);
                if (c == null) continue;
                if (a.anim != null) a.anim.play("interact", 0.7f);
                List<ItemInstance> all = new ArrayList<>(c.items);
                all.sort((p, q) -> Integer.compare(q.baseValueTotal(), p.baseValueTotal()));
                for (int i = 0; i < Math.min(3, all.size()) && t.haul.size() < HAUL_MAX; i++) { ItemInstance it = all.get(i); InventoryService.release(it); t.haul.add(it); }
            }
        }
    }

    private void finish(Task t, List<Actor> who) {
        if (t.haul.isEmpty()) { t.done = true; raid.squadOrder(t.team, "follow", Float.NaN, Float.NaN, false); if (t.loot) raid.msg(t.team.toUpperCase() + ": nothing worth carrying out", 0xFFD6CEB8); return; }
        t.stage = 3; t.stageT = 0;
        raid.squadOrder(t.team, "follow", Float.NaN, Float.NaN, false);
        raid.say(who.get(0), "House is done. Bringing it out.", "radio_chatter");
    }

    /** What the team is carrying is put on the ground by the leader. */
    private void bring(Task t) {
        if (t.haul.isEmpty()) return;
        int n = t.haul.size();
        for (ItemInstance it : t.haul) { it.foundInRaid = true; raid.dropItem(it, raid.player.x + raid.rng.range(-0.9f, 0.9f), raid.player.y + 0.25f, raid.player.z + raid.rng.range(-0.9f, 0.9f)); }
        t.haul.clear();
        brought += n;
        raid.msg(t.team.toUpperCase() + ": brought out " + n + " thing" + (n == 1 ? "" : "s") + " - at your feet", 0xFFA8D08C);
    }
}
