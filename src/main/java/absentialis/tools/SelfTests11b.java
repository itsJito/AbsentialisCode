package absentialis.tools;

import absentialis.entity.Actor;
import absentialis.gameplay.economy.Economy;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemInstance;
import absentialis.gameplay.progression.Progression;
import absentialis.neuralai.animal.AnimalBrain;
import absentialis.persistence.Profile;
import absentialis.scene.Animals;
import absentialis.scene.Animals.Animal;
import absentialis.scene.Animals.Kind;
import absentialis.scene.Raid;
import absentialis.world.Block;
import absentialis.world.MapData;
import absentialis.world.World;

/**
 * Quality gates for 1.1, second file (the first is {@link SelfTests11}; this one holds the groups added as the later
 * parts of the brief were built).
 */
final class SelfTests11b {
    private SelfTests11b() {}

    static void run(String which, boolean all, SelfTests04.Check t) {
        if (all || which.contains("v11") || which.contains("animals11")) SelfTests.guard(() -> animals(t));
        if (all || which.contains("v11") || which.contains("places11")) SelfTests.guard(() -> places(t));
        if (all || which.contains("v11") || which.contains("weapons11")) SelfTests.guard(() -> weapons(t));
        if (all || which.contains("v11") || which.contains("cars11")) SelfTests.guard(() -> cars(t));
        if (all || which.contains("v11") || which.contains("ledge11")) SelfTests.guard(() -> ledges(t));
    }

    static void ledges(SelfTests04.Check t) {
        World w = SelfTests.flatWorld();
        int bx = 260, bz = 260;
        for (int x = bx; x <= bx + 3; x++) for (int z = bz - 2; z <= bz + 2; z++) { for (int y = G; y < G + 3; y++) w.setRaw(x, y, z, Block.CONCRETE); }
        for (int x = bx - 6; x <= bx - 4; x++) for (int z = bz + 5; z <= bz + 8; z++) for (int y = G; y < G + 2; y++) w.setRaw(x, y, z, Block.CONCRETE);
        w.computeHeights();
        try {
            // ---- three blocks up: taken hold of, hung from, and then either up or down
            Actor a = new Actor();
            a.x = bx - 0.42f; a.y = G; a.z = bz + 0.5f; a.yaw = (float) -Math.PI / 2; a.vx = 2.6f; a.onGround = true;      // walking into it
            float[] to = absentialis.entity.Physics.ledge(w, a, 1, 0);
            boolean found = to != null && to[1] == G + 3;
            float inside = 0, swingPeak = 0;
            boolean hung = false, held = true;
            if (found) {
                absentialis.entity.Physics.hang(a, to);
                for (int i = 0; i < 90; i++) {
                    boolean on = absentialis.entity.Physics.hangStep(w, a, 1 / 60f);
                    held &= on;
                    inside = Math.max(inside, (a.x + a.radius) - bx);
                    swingPeak = Math.max(swingPeak, Math.abs(a.hangSwing));
                }
                hung = a.hanging && Math.abs(a.y - (G + 3 - absentialis.entity.Physics.HANG_REACH)) < 0.02f && a.y > G + 0.5f && a.climbing;
            }
            float settle = Math.abs(a.hangSwing);
            for (int i = 0; i < 240; i++) absentialis.entity.Physics.hangStep(w, a, 1 / 60f);
            boolean still = Math.abs(a.hangSwing) < 0.05f && a.hanging;
            // up
            absentialis.entity.Physics.pullUp(a);
            boolean climbing = a.mantling() && !a.hanging;
            for (int i = 0; i < 240 && absentialis.entity.Physics.mantleStep(w, a, 1 / 60f); i++) { if (a.y < G + 2.99f) inside = Math.max(inside, (a.x + a.radius) - bx); }
            boolean onTop = Math.abs(a.y - (G + 3)) < 0.01f && a.x > bx && a.onGround;
            // down
            Actor b = new Actor();
            b.x = bx - 0.42f; b.y = G; b.z = bz - 0.5f; b.yaw = (float) -Math.PI / 2; b.onGround = true;
            float[] tb = absentialis.entity.Physics.ledge(w, b, 1, 0);
            boolean dropped = false;
            if (tb != null) {
                absentialis.entity.Physics.hang(b, tb);
                for (int i = 0; i < 40; i++) absentialis.entity.Physics.hangStep(w, b, 1 / 60f);
                float hy = b.y;
                absentialis.entity.Physics.letGo(b);
                for (int i = 0; i < 90; i++) absentialis.entity.Physics.move(w, b, 1 / 60f);
                dropped = !b.hanging && !b.mantling() && hy > G + 0.5f && Math.abs(b.y - G) < 0.01f && b.onGround;
            }
            t.check("a ledge three blocks up is taken hold of and hung from - the body under the hands, feet off the ground, the legs swinging and coming to rest - and from there it is up and over, or let go of and down; at no point is the body inside the block",
                    found && hung && held && swingPeak > 0.1f && still && climbing && onTop && dropped && inside <= 0.001f,
                    "found " + found + ", hung " + hung + ", swing " + swingPeak + " -> " + settle + ", up " + onTop + ", let go " + dropped + ", deepest into the block " + inside + " m");
            // ---- two blocks up, taken at a walk with the body already against it: straight up the face
            Actor c = new Actor();
            c.x = bx - 5.5f; c.y = G; c.z = bz + 5 - 0.31f; c.yaw = (float) Math.PI; c.vz = 2.6f; c.onGround = true;
            float[] tc = absentialis.entity.Physics.ledge(w, c, 0, 1);
            float deep = 0;
            boolean up2 = false;
            if (tc != null) {
                absentialis.entity.Physics.mantle(c, tc);
                for (int i = 0; i < 240 && absentialis.entity.Physics.mantleStep(w, c, 1 / 60f); i++) if (c.y < G + 1.99f) deep = Math.max(deep, (c.z + c.radius) - (bz + 5));
                up2 = Math.abs(c.y - (G + 2)) < 0.01f && c.z > bz + 5;
            }
            t.check("pulling up onto a ledge while walking into it does not carry the body into the block: it goes straight up the face and only then over the edge", tc != null && up2 && deep <= 0.02f, "on top " + up2 + ", deepest into the block before the feet were level with it " + deep + " m");
        } finally {
            for (int x = bx - 6; x <= bx + 3; x++) for (int z = bz - 2; z <= bz + 8; z++) for (int y = G; y < G + 3; y++) w.setRaw(x, y, z, Block.AIR);
            w.computeHeights();
        }
    }

    private static absentialis.scene.VehicleSystem.Car parkBeside(Raid r) {
        absentialis.scene.VehicleSystem.Car car = r.vehicles.debugSpawn("car.volna");
        r.update(1 / 60f);
        if (car == null) return null;
        float[] o = new float[3];
        car.sim.origin(o);
        r.player.x = o[0] - car.sim.body.rightX() * 1.7f; r.player.z = o[2] - car.sim.body.rightZ() * 1.7f; r.player.y = G;
        return car;
    }

    static void cars(SelfTests04.Check t) {
        // ---- getting in can be called off
        Raid r = SelfTests.stage(new Profile());
        absentialis.scene.VehicleSystem.Car car = parkBeside(r);
        float sx = r.player.x, sz = r.player.z;
        boolean started = car != null && r.vehicles.enter();
        SelfTests11.run(r, 0.35f);
        boolean part = r.vehicles.busy() && r.vehicles.driving == null;
        r.vehicles.exit();
        SelfTests11.run(r, 2.5f);
        boolean out = !r.vehicles.busy() && !r.vehicles.inside() && r.vehicles.driving == null && r.player.distXZ(sx, sz) < 2.2f && !r.player.seated;
        t.check("getting into a car can be called off: the same key again, before the door is shut, and the survivor steps back out to where they were", started && part && out && r.vehicles.entriesCancelled == 1,
                "started " + started + ", part way " + part + ", out " + out + " (" + r.player.distXZ(sx, sz) + " m from where they stood)");
        // ---- the passenger's door
        Raid rp = SelfTests.stage(new Profile());
        absentialis.scene.VehicleSystem.Car pc = rp.vehicles.debugSpawn("car.volna");
        rp.update(1 / 60f);
        float[] po = new float[3];
        boolean side = false, drove = false;
        if (pc != null) {
            pc.sim.origin(po);
            float sgn = Math.signum(pc.visual.seatX) * (pc.sim.body.rightX() == 0 && pc.sim.body.rightZ() == 0 ? 1 : 1);
            // the side away from the wheel
            for (float s : new float[]{1.7f, -1.7f}) { rp.player.x = po[0] + pc.sim.body.rightX() * s; rp.player.z = po[2] + pc.sim.body.rightZ() * s; rp.player.y = G; if (rp.vehicles.passengerSide(pc)) { side = true; break; } }
            if (side && rp.vehicles.enter()) for (int i = 0; i < 60 * 6 && rp.vehicles.driving == null; i++) rp.update(1 / 60f);
            drove = rp.vehicles.driving == pc;
            SelfTests11.run(rp, 1.5f);
        }
        t.check("getting in from the passenger's side opens the passenger's door - a real door on the car's other side, which swings wide, lets the survivor in and is shut behind them",
                side && drove && rp.vehicles.passengerDoorOpened == 1 && rp.vehicles.passengerDoorPeak > 0.6f && pc.door2 != null && Math.abs(pc.door2.angle) < 0.1f && pc.visual.hinge2X() * pc.visual.hingeX() < 0,
                "on that side " + side + ", at the wheel " + drove + ", opened " + rp.vehicles.passengerDoorOpened + ", stood " + rp.vehicles.passengerDoorPeak + " open, now " + (pc == null || pc.door2 == null ? "none" : pc.door2.angle));
        // ---- out of a moving car
        Raid rb = SelfTests.stage(new Profile());
        rb.god = false; rb.player.hp = rb.player.maxHp = 100;
        absentialis.scene.VehicleSystem.Car fast = parkBeside(rb);
        boolean in = fast != null && rb.vehicles.enter();
        for (int i = 0; i < 60 * 5 && in && rb.vehicles.driving == null; i++) rb.update(1 / 60f);
        for (int i = 0; i < 60 * 6 && rb.vehicles.driving != null; i++) { rb.vehicles.keys(true, false, false, false, false); rb.update(1 / 60f); }
        float speed = fast == null ? 0 : fast.sim.speed(), hp0 = rb.player.hp;
        String why = rb.vehicles.exit();
        boolean thrown = rb.vehicles.driving == null && !rb.player.seated && rb.player.ragdoll != null;
        float[] o = new float[3];
        float px = rb.player.x, pz = rb.player.z;
        SelfTests11.run(rb, 1.0f);
        if (fast != null) fast.sim.origin(o);
        float carWent = fast == null ? 0 : (float) Math.sqrt((o[0] - px) * (o[0] - px) + (o[2] - pz) * (o[2] - pz));
        boolean up = false;
        for (int i = 0; i < 60 * 14 && !up; i++) { rb.update(1 / 60f); up = rb.player.ragdoll == null && !rb.player.dead; }
        t.check("there is no speed at which the survivor cannot leave a car: at speed they go out of it as a body in the road (Sydney has them), are hurt by how fast it was, the car rolls on without them, and they get up again",
                in && speed > 8 && why == null && thrown && rb.vehicles.bailOuts == 1 && rb.bailRagdolls == 1 && rb.player.hp < hp0 && carWent > 3 && up,
                "at " + speed + " m/s: " + (why == null ? "out" : why) + ", ragdoll " + thrown + ", lost " + (hp0 - rb.player.hp) + " health, the car went on " + carWent + " m, up again " + up);
        // ---- what is meant for somebody inside lands on the car
        Raid rt = SelfTests.stage(new Profile());
        rt.god = false; rt.player.hp = rt.player.maxHp = 100;
        absentialis.scene.VehicleSystem.Car box = parkBeside(rt);
        boolean sat = box != null && rt.vehicles.enter();
        for (int i = 0; i < 60 * 5 && sat && rt.vehicles.driving == null; i++) rt.update(1 / 60f);
        Actor tank = SelfTests11.spawn(rt, "tank", rt.player.x + 2.2f, rt.player.z, 0);
        rt.freezeOnly(tank);
        float y0 = rt.player.y, h0 = rt.player.hp;
        rt.tackle(tank, rt.player, -1, 0, 40);
        rt.fling(tank, rt.player, -1, 0);
        rt.grabbed(tank, rt.player, 1.2f);
        float topY = y0;
        for (int i = 0; i < 90; i++) { rt.update(1 / 60f); topY = Math.max(topY, rt.player.y); }
        t.check("somebody sitting in a car cannot be tackled, thrown or lifted out of it - the blow lands on the car, which is stove in and shoved, and they are shaken and hurt where they sit",
                sat && rt.player.seated && rt.vehicles.driving == box && topY - y0 < 0.4f && rt.vehicleBlows == 3 && rt.vehicles.blowsTaken == 3 && rt.player.hp < h0 && rt.player.hp > 0 && rt.tackles == 0 && rt.flings == 0,
                "still seated " + rt.player.seated + ", rose " + (topY - y0) + " m, " + rt.vehicleBlows + " blows on the car, health " + h0 + " -> " + rt.player.hp);
    }

    static void weapons(SelfTests04.Check t) {
        Content.load();
        var keepTuning = absentialis.gameplay.weapons.Gunsmith.tuning;
        var keepPrestige = absentialis.gameplay.weapons.Gunsmith.prestigeLevel;
        try {
            Profile p = new Profile();
            p.applyCapacity();
            var G = absentialis.gameplay.weapons.Gunsmith.class;
            ItemInstance mine = new ItemInstance("weapon.pm9"), theirs = new ItemInstance("weapon.pm9");
            theirs.factory = true;
            var def = mine.def();
            var s0 = absentialis.gameplay.weapons.Gunsmith.stats(mine, 0);
            var sf = absentialis.gameplay.weapons.Gunsmith.stats(theirs, 0);
            boolean fresh = Math.abs(s0.damageMul / sf.damageMul - 0.6f) < 0.01f && s0.rpm < sf.rpm * 0.75f && Math.abs(s0.reloadTime / sf.reloadTime - 1.3f) < 0.01f && mine.capacity() == Math.round(def.weapon.capacity * 0.6f)
                    && Math.abs(sf.rpm - def.weapon.rpm) < 0.5f && theirs.capacity() == def.weapon.capacity;
            t.check("a handgun nobody has worked on does six tenths of its damage, fires slower, reloads 30% slower and holds six tenths of its rounds - and the same handgun in somebody else's hands is exactly as it left the factory",
                    fresh, "damage x" + s0.damageMul / sf.damageMul + ", " + s0.rpm + " against " + sf.rpm + " rpm, reload x" + s0.reloadTime / sf.reloadTime + ", " + mine.capacity() + " of " + theirs.capacity() + " rounds");
            // ---- levels are upgrades: one for each after the first
            int max = absentialis.gameplay.weapons.Attachments.maxLevel(def);
            boolean none = p.prog.upgradePoints(def.id) == 0 && p.prog.upgrade(def.id, 0) != null;
            p.prog.addMastery(def.id, absentialis.gameplay.weapons.Attachments.xpForLevel(5));
            int at5 = p.prog.upgradePoints(def.id);
            boolean spent = p.prog.upgrade(def.id, 2) == null && p.prog.upgrade(def.id, 2) == null && p.prog.upgrade(def.id, 3) == null && p.prog.upgrade(def.id, 3) == null && p.prog.upgrade(def.id, 0) != null;
            var s2 = absentialis.gameplay.weapons.Gunsmith.stats(mine, 0);
            boolean middle = Math.abs(s2.reloadTime - sf.reloadTime) < 0.001f && mine.capacity() == theirs.capacity();
            p.prog.addMastery(def.id, absentialis.gameplay.weapons.Attachments.xpForLevel(max));
            for (int tr = 0; tr < 4; tr++) for (int k = 0; k < 4; k++) p.prog.upgrade(def.id, tr);
            var s4 = absentialis.gameplay.weapons.Gunsmith.stats(mine, 0);
            boolean top = p.prog.weaponLevel(def.id) == max && max == 17 && p.prog.upgradePoints(def.id) == 0 && Math.abs(s4.damageMul / sf.damageMul - 1.015f) < 0.002f && Math.abs(s4.rpm / sf.rpm - 1.015f) < 0.002f
                    && s4.reloadTime < sf.reloadTime * 0.8f && mine.capacity() == Math.round(def.weapon.capacity * 1.5f);
            var back = absentialis.gameplay.progression.Progression.fromJson(p.prog.toJson());
            t.check("every weapon level after the first is one upgrade; the factory reload and capacity are the middle of their tracks; a weapon taken to its last level (17) is the factory weapon and 1.5% over, faster to reload and half as large again - and all of it is saved",
                    none && at5 == 4 && spent && middle && top && back.tune(def.id, 0) == 4 && back.tune(def.id, 3) == 4 && back.upgradePoints(def.id) == 0,
                    "none at level 1 " + none + ", " + at5 + " at level 5, spent " + spent + ", middle " + middle + ", top " + top + " (damage x" + s4.damageMul / sf.damageMul + ", " + mine.capacity() + " rounds)");
            // ---- levels are slots: a part the survivor is carrying goes on from the inventory, if the slot is open
            Profile q = new Profile();
            q.applyCapacity();
            ItemInstance gun = new ItemInstance("weapon.pm9");
            q.loadout.caseGrid.restore(gun);
            var order = absentialis.gameplay.weapons.Attachments.slotOrder(gun.def());
            String third = order.get(2);
            ItemInstance part = null;
            for (var a : absentialis.gameplay.weapons.Attachments.compatible(gun.def())) if (a.attachment.socket.equals(third)) { part = new ItemInstance(a.id); break; }
            boolean havePart = part != null && q.loadout.caseGrid.restore(part);
            absentialis.ui.InventoryUi inv = new absentialis.ui.InventoryUi(null, null);
            inv.owner = q.loadout; inv.prog = q.prog;
            boolean offered = havePart && inv.partsFor(gun, third).contains(part);
            String refused = havePart ? inv.benchFit(gun, part) : "no part";
            q.prog.addMastery(gun.defId, absentialis.gameplay.weapons.Attachments.xpForLevel(3));
            String fitted = havePart ? inv.benchFit(gun, part) : "no part";
            boolean on = havePart && gun.attachments.get(third) == part && !q.loadout.caseGrid.items.contains(part) && inv.partsFor(gun, third).isEmpty();
            String off = inv.benchRemove(gun, third);
            boolean returned = havePart && gun.attachments.get(third) == null && q.loadout.caseGrid.items.contains(part);
            t.check("a weapon's slots open one a level (light, laser, optic ...): a part in the survivor's case is offered for the slot it fits, is refused while the slot is shut, goes on from the inventory once the level is reached - out of the case, onto the weapon - and comes back to the case when it is taken off",
                    order.get(0).equals("light") && order.get(1).equals("laser") && offered && refused != null && fitted == null && on && off == null && returned && inv.benchFits == 1 && inv.benchRemovals == 1,
                    "order " + order + ", offered " + offered + ", shut: " + refused + ", fitted: " + fitted + ", on " + on + ", returned " + returned);
            // ---- the knife
            var knife = Content.def("melee.knife");
            ItemInstance blade = new ItemInstance("melee.knife");
            int kmax = absentialis.gameplay.weapons.Attachments.maxLevel(knife);
            q.prog.addMastery(knife.id, absentialis.gameplay.weapons.Attachments.xpForLevel(kmax));
            boolean kills0 = absentialis.gameplay.weapons.Gunsmith.knifeKills(absentialis.gameplay.weapons.Gunsmith.tune(blade, 0));
            for (int k = 0; k < 4; k++) { q.prog.upgrade(knife.id, 0); q.prog.upgrade(knife.id, 1); }
            t.check("a knife has two tracks of its own - Damage and Durability - eight levels of them; at the top of Damage it ends a person, and at the top of Durability its edge lasts two and a half times as long",
                    absentialis.gameplay.weapons.Gunsmith.tracks(knife).length == 2 && kmax == 9 && !kills0 && absentialis.gameplay.weapons.Gunsmith.knifeKills(absentialis.gameplay.weapons.Gunsmith.tune(blade, 0))
                            && Math.abs(absentialis.gameplay.weapons.Gunsmith.knifeWear(absentialis.gameplay.weapons.Gunsmith.tune(blade, 1)) - 0.4f) < 0.001f && q.prog.upgrade(knife.id, 2) != null,
                    "max level " + kmax + ", kills at the start " + kills0 + ", wear x" + absentialis.gameplay.weapons.Gunsmith.knifeWear(absentialis.gameplay.weapons.Gunsmith.tune(blade, 1)));
        } finally {
            absentialis.gameplay.weapons.Gunsmith.tuning = keepTuning;
            absentialis.gameplay.weapons.Gunsmith.prestigeLevel = keepPrestige;
        }
    }

    /** Every cell of a building that can be walked to from its entrance (a step up or down at a time, through doors). */
    private static java.util.Set<Long> walkable(World w, MapData.Building b) {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        int sx = b.entryX, sy = b.entryY, sz = b.entryZ;
        for (int dy = -1; dy <= 2 && q.isEmpty(); dy++) if (stand(w, sx, sy + dy, sz)) q.add(new int[]{sx, sy + dy, sz});
        while (!q.isEmpty()) {
            int[] c = q.poll();
            long k = ((long) c[0] << 40) | ((long) c[2] << 16) | c[1];
            if (!seen.add(k)) continue;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = c[0] + d[0], nz = c[2] + d[1];
                if (nx < b.lotX0 - 2 || nz < b.lotZ0 - 2 || nx > b.lotX1 + 2 || nz > b.lotZ1 + 2) continue;
                for (int dy = 1; dy >= -1; dy--) if (stand(w, nx, c[1] + dy, nz)) { q.add(new int[]{nx, c[1] + dy, nz}); break; }
            }
        }
        return seen;
    }
    private static boolean open(World w, int x, int y, int z) { int id = w.get(x, y, z); return id == 0 || !Block.ALL[id].solid() || Block.ALL[id].is(Block.DOOR); }
    private static boolean stand(World w, int x, int y, int z) { int under = w.get(x, y - 1, z); return open(w, x, y, z) && open(w, x, y + 1, z) && under != 0 && Block.ALL[under].solid() && !Block.ALL[under].is(Block.DOOR); }

    static void places(SelfTests04.Check t) {
        Content.load();
        World city = SelfTests06.world(1337L);
        MapData.Building pd = null, manor = null;
        for (MapData.Building b : city.map.buildings) { if (b.archetype.equals("lm_police") && b.authored) pd = b; if (b.archetype.equals("lm_spencer")) manor = b; }
        t.check("the police headquarters is one hand-built building: a basement, a ground floor and a first floor, forty rooms or more", pd != null && pd.rooms.size() >= 40, pd == null ? "not built" : pd.rooms.size() + " rooms");
        if (pd == null || manor == null) return;
        // ---- the format: no ceiling under four blocks, no corridor under two wide
        int low = 0, narrow = 0, floorsSeen = 0;
        String worst = "";
        boolean[] floors = new boolean[3];
        for (MapData.Room rm : pd.rooms) {
            floors[rm.floor + 1] = true;
            int w = rm.x1 - rm.x0 + 1, d = rm.z1 - rm.z0 + 1;
            if (rm.circulation && Math.min(w, d) < 2) { narrow++; worst = rm.type + " " + w + "x" + d; }
            // measured in the world, not read off the record: the clear height over the middle of the room's floor
            int best = 0;
            for (int x = rm.x0; x <= rm.x1; x++) for (int z = rm.z0; z <= rm.z1; z++) {
                if (!stand(city, x, rm.y + 1, z)) continue;
                int h = 0;
                while (h < 16 && open(city, x, rm.y + 1 + h, z)) h++;
                best = Math.max(best, h);
            }
            if (best < 4) { low++; worst = rm.type + " floor " + rm.floor + " is " + best + " high"; }
        }
        for (boolean f : floors) if (f) floorsSeen++;
        t.check("in the station no room is lower than four blocks and no corridor is narrower than two - measured in the world", low == 0 && narrow == 0 && floorsSeen == 3, low + " low, " + narrow + " narrow, " + floorsSeen + " storeys; " + worst);
        // ---- every room can be walked to from the front doors
        java.util.Set<Long> reach = walkable(city, pd);
        int unreached = 0;
        String which = "";
        for (MapData.Room rm : pd.rooms) {
            boolean hit = false;
            for (int x = rm.x0; x <= rm.x1 && !hit; x++) for (int z = rm.z0; z <= rm.z1 && !hit; z++) hit = reach.contains(((long) x << 40) | ((long) z << 16) | (rm.y + 1));
            if (!hit) { unreached++; which += rm.type + "(" + rm.floor + ") "; }
        }
        t.check("every room of the station - basement, ground floor and first floor - can be walked to from its front doors", unreached == 0, unreached + " not reached: " + which + "(" + reach.size() + " cells walked)");
        // ---- the dead are in it, on every floor, and the kennel has its dogs
        int[] lairs = new int[3];
        int kennel = 0;
        for (MapData.Point p : city.map.lairs) { if (!pd.contains(p.x, p.z)) continue; if (p.kind.equals("idog")) kennel++; lairs[p.y < World.GROUND ? 0 : p.y > World.GROUND + 3 ? 2 : 1]++; }
        int manorLairs = 0;
        for (MapData.Point p : city.map.lairs) if (manor.contains(p.x, p.z)) manorLairs++;
        t.check("the infected are found in the station's basement, which its garrison never cleared (and dogs in its kennel), and inside the Halloran house", lairs[0] >= 5 && lairs[1] == 0 && lairs[2] == 0 && kennel == 1 && manorLairs >= 10,
                "station lairs " + lairs[0] + " / " + lairs[1] + " / " + lairs[2] + ", kennel " + kennel + "; the house " + manorLairs);
        // ---- what is always there
        Raid r = new Raid(city, new Profile(), new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        boolean eagle = false, magnum = false, pump = false, shells = false;
        for (Raid.WorldItem wi : r.items) {
            String id = wi.it.def().id;
            MapData.Room in = null;
            for (MapData.Building b : new MapData.Building[]{pd, manor}) for (MapData.Room rm : b.rooms) if (wi.x >= rm.x0 && wi.x <= rm.x1 + 1 && wi.z >= rm.z0 && wi.z <= rm.z1 + 1 && Math.abs(wi.y - (rm.y + 1)) < 1.5f) in = rm;
            if (in == null) continue;
            if (id.equals("weapon.deagle") && in.type.equals("stars_office")) eagle = wi.it.rounds == wi.it.capacity();
            if (id.equals("ammo.magnum") && in.type.equals("stars_office")) magnum = wi.it.qty == wi.it.def().maxStack;
            if (id.equals("weapon.pump12") && in.type.equals("bedroom") && in.floor == 1) pump = true;
            if (id.equals("ammo.shells") && in.type.equals("bedroom") && in.floor == 1) shells = wi.it.qty == 12;
        }
        t.check("a loaded Desert Eagle and a full box of magnum rounds are always in the station's S.T.A.R.S. office, and a pump shotgun with twelve shells in the master bedroom on the Halloran house's upper floor",
                eagle && magnum && pump && shells, "eagle " + eagle + ", magnum " + magnum + ", pump " + pump + ", shells " + shells + " (" + r.fixedItems + " placed)");
    }

    static final int G = World.GROUND;

    private static float dist(Animal a, float x, float z) { return (float) Math.sqrt((a.x - x) * (a.x - x) + (a.z - z) * (a.z - z)); }

    static void animals(SelfTests04.Check t) {
        // ---- the measurements the brief gives, part by part
        int[][] want = {{6, 6, 4}, {2, 8, 2}, {3, 3, 3}, {1, 4, 6}, {4, 3, 2}, {6, 8, 1}, {4, 6, 3}, {3, 5, 3}, {8, 8, 8}, {10, 8, 12}, {16, 2, 2}};
        int[][] got = {Animals.size(Kind.DOG, "head"), Animals.size(Kind.DOG, "leg"), Animals.size(Kind.CROW, "head"), Animals.size(Kind.CROW, "wing"), Animals.size(Kind.BAT, "head"), Animals.size(Kind.BAT, "wing_out"),
                Animals.size(Kind.CHICKEN, "head"), Animals.size(Kind.CHICKEN, "leg"), Animals.size(Kind.SPIDER, "head"), Animals.size(Kind.SPIDER, "abdomen"), Animals.size(Kind.BROOD, "leg")};
        boolean sizes = true;
        for (int i = 0; i < want.length; i++) sizes &= java.util.Arrays.equals(want[i], got[i]);
        t.check("every animal is built from parts of the size asked for: a husky's head 6x6x4 on legs 2x8x2, a crow's head 3x3x3 and wings 1x4x6, a bat's outer wing 6x8, a chicken's head 4x6x3, a spider's 8x8x8 head, 10x8x12 abdomen and legs 16 long", sizes, "");

        // ---- the crow
        Raid r = SelfTests11.flat(new Profile());
        Actor p = r.player;
        p.x = 150.5f; p.z = 150.5f; p.y = G;
        Animal crow = r.animals.spawn(Kind.CROW, 160.5f, G, 150.5f);
        SelfTests11.run(r, 3);
        boolean sat = dist(crow, 160.5f, 150.5f) < 0.2f && "perched".equals(crow.brain.state);
        p.x = 156.5f;                                                        // four blocks off: still nothing to it
        SelfTests11.run(r, 2);
        boolean satAtFour = "perched".equals(crow.brain.state);
        p.x = 157.8f;                                                        // inside three
        SelfTests11.run(r, 0.5f);
        boolean went = "flying".equals(crow.brain.state) && crow.flying;
        SelfTests11.run(r, 6);
        float away = dist(crow, p.x, p.z);
        t.check("a crow sits where it is while the nearest person is four blocks off, and is in the air the moment one is within three - and it goes a long way",
                sat && satAtFour && went && away > 12, "sat " + sat + ", at four " + satAtFour + ", flew " + went + ", ended " + away + " m off");
        int carrying = 0, n = 2000, itemsOk = 0;
        for (int i = 0; i < n; i++) { Animal c = r.animals.spawn(Kind.CROW, 100, G, 100); if (!c.carrying.isEmpty()) { carrying++; if (Content.def(c.carrying.get(0)) != null) itemsOk++; } }
        r.animals.all.removeIf(a -> a != crow);
        Animal rich = r.animals.spawn(Kind.CROW, 140.5f, G, 140.5f);
        rich.carrying.clear(); rich.carrying.add("val.ledger_cash");
        int itemsBefore = r.items.size();
        r.animals.hurt(rich, 50, p.id, 1, 0);
        t.check("three crows in four are carrying something real (cash, a grenade, gunpowder or rounds), and a crow that is shot drops what it had",
                Math.abs(carrying / (float) n - 0.75f) < 0.04f && itemsOk == carrying && rich.dead && r.items.size() == itemsBefore + 1, carrying + " of " + n + " carrying, " + itemsOk + " real items, dropped " + (r.items.size() - itemsBefore));

        // ---- the dog
        Raid rd = SelfTests11.flat(new Profile());
        Actor pd = rd.player;
        pd.x = 150.5f; pd.z = 150.5f; pd.y = G;
        Animal dog = rd.animals.dogFor(pd);
        for (int i = 0; i < 600; i++) { pd.x += 3.5f / 60f; pd.vx = 3.5f; rd.update(1 / 60f); }      // ten seconds' walk east
        pd.vx = 0;
        float heel = dist(dog, pd.x, pd.z);
        SelfTests11.run(rd, 6);
        boolean sits = dog.sitting && dist(dog, pd.x, pd.z) < 4;
        Actor dead = SelfTests11.spawn(rd, "walker", pd.x + 5, pd.z, 0);
        float hp0 = dead.hp;
        boolean attacked = false;
        for (int i = 0; i < 480 && !attacked; i++) { rd.update(1 / 60f); attacked = dead.hp < hp0 || dead.dead; }
        t.check("a dog keeps at its owner's heel on the move, sits when they stop, and goes for one of the dead that comes near them - and it barks or growls about it",
                heel < 5 && sits && attacked && rd.animals.voices.getOrDefault("bark", 0) + rd.animals.voices.getOrDefault("growl", 0) > 0,
                "at heel " + heel + " m, sits " + sits + ", bit it " + attacked + ", voices " + rd.animals.voices);
        Progression nine = new Progression(), ten = new Progression();
        nine.bestRank = 9; ten.bestRank = 10;
        var whistle = Content.def(Animals.WHISTLE);
        Profile owner = new Profile();
        owner.loadout.caseGrid.restore(new ItemInstance(Animals.WHISTLE));
        Raid ro = SelfTests11.flat(owner);
        SelfTests11.run(ro, 1);
        t.check("the husky is on the shop's wall at Rank 10 and not before, and whoever carries its whistle into an operation has the dog with them",
                whistle != null && !Economy.shopShows(whistle, nine) && Economy.shopShows(whistle, ten) && ro.animals.playersDog() != null && rd.animals.count(Kind.DOG) == 1,
                "rank 9 " + (whistle != null && Economy.shopShows(whistle, nine)) + ", rank 10 " + (whistle != null && Economy.shopShows(whistle, ten)) + ", deployed " + (ro.animals.playersDog() != null));

        // ---- the chicken
        Raid rc = SelfTests11.flat(new Profile());
        rc.player.x = 150.5f; rc.player.z = 150.5f;
        int gold = 0, eggs = 0, hens = 4000;
        for (int i = 0; i < hens; i++) { Animal h = rc.animals.spawn(Kind.CHICKEN, 100, G, 100); for (String e : h.carrying) { eggs++; if (e.equals("food.egg_golden")) gold++; } }
        rc.animals.all.clear();
        Animal hen = rc.animals.spawn(Kind.CHICKEN, 160.5f, G, 150.5f);
        SelfTests11.run(rc, 2);
        float calm = dist(hen, 160.5f, 150.5f);
        rc.player.x = 158.5f; rc.player.vx = 5;                               // somebody running at it
        SelfTests11.run(rc, 1.5f);
        float fled = dist(hen, rc.player.x, rc.player.z);
        int before = rc.items.size();
        rc.animals.hurt(hen, 50, rc.player.id, 1, 0);
        boolean sellOnly = true;
        for (String id : new String[]{"food.egg_brown", "food.egg_white", "food.egg_golden"}) { var d = Content.def(id); sellOnly &= d != null && d.food != null && !Economy.shopShows(d, ten) && Economy.shopSell(new ItemInstance(id)) > 0; }
        boolean stocked = false;
        for (var tr : Economy.TRADERS) for (var d : Economy.stock(tr, ten, 100)) stocked |= d.id.startsWith("food.egg");
        t.check("a chicken keeps to its yard, runs from somebody coming at it, and leaves an egg or two - brown, white, or (one in twenty or so) golden - which can be eaten and sold and are not for sale anywhere",
                calm < 9 && fled > 3 && rc.items.size() > before && sellOnly && !stocked && gold > 0 && gold < eggs * 0.09f,
                "wandered " + calm + " m, fled to " + fled + " m, dropped " + (rc.items.size() - before) + ", sell-only " + sellOnly + ", golden " + gold + " of " + eggs);

        // ---- the bat and the spider
        Raid rb = SelfTests11.flat(new Profile());
        World w = rb.world;
        rb.player.x = 120.5f; rb.player.z = 120.5f;
        for (int x = 156; x <= 166; x++) for (int z = 146; z <= 156; z++) w.setRaw(x, G + 4, z, Block.CONCRETE);
        w.computeHeights();
        Animal bat = rb.animals.debugSpawn("bat", 160.5f, G, 150.5f, 1) == null ? null : rb.animals.all.get(0);
        SelfTests11.run(rb, 2);
        boolean hangs = bat != null && !bat.flying && Math.abs(bat.y + Kind.BAT.height - (G + 4)) < 0.2f;
        rb.player.x = 160.5f; rb.player.z = 151.5f; rb.player.y = G + 2;        // somebody right under it
        SelfTests11.run(rb, 2);
        boolean flies = bat != null && bat.flying;
        rb.player.x = 120.5f; rb.player.z = 120.5f; rb.player.y = G;
        Animal spider = rb.animals.spawn(Kind.SPIDER, 180.5f, G, 180.5f);
        Actor walkerBy = SelfTests11.spawn(rb, "survivors", 180.5f, 192.5f, 0);
        rb.freezeOnly(walkerBy);
        SelfTests11.run(rb, 3);
        boolean leftAlone = "lurk".equals(spider.brain.state) && walkerBy.hp >= walkerBy.maxHp - 0.01f;
        walkerBy.x = 180.5f; walkerBy.z = 184.5f;
        float h0 = walkerBy.hp;
        SelfTests11.run(rb, 4);
        t.check("a bat hangs from the roof it was put under until somebody is beneath it and then flies; a spider leaves alone what stays off its ground and bites what walks onto it",
                hangs && flies && leftAlone && (walkerBy.hp < h0 || walkerBy.dead), "hangs " + hangs + ", flies " + flies + ", left alone " + leftAlone + ", bitten " + (walkerBy.hp < h0 || walkerBy.dead));
        for (int x = 156; x <= 166; x++) for (int z = 146; z <= 156; z++) w.setRaw(x, G + 4, z, Block.AIR);
        w.computeHeights();

        // ---- the infected dogs
        Raid rp = SelfTests11.flat(new Profile());
        rp.player.x = 120.5f; rp.player.z = 120.5f;
        int small = rp.animals.pack(100, G, 100, 1).size(), big = rp.animals.pack(100, G, 100, 40).size();
        rp.animals.all.clear();
        var pack = rp.animals.pack(170.5f, G, 170.5f, 4);
        Actor man = SelfTests11.spawn(rp, "survivors", 170.5f, 188.5f, 0), deadOne = SelfTests11.spawn(rp, "walker", 173.5f, 172.5f, 0);
        rp.freezeOnly(man); rp.freezeOnly(deadOne);
        man.weapon = null;
        float manHp = man.hp, deadHp = deadOne.hp, t0 = rp.time, reached = -1;
        for (int i = 0; i < 60 * 12; i++) {
            rp.update(1 / 60f);
            if (reached < 0 && (man.hp < manHp || man.dead)) reached = rp.time - t0;
            if (man.dead) break;
        }
        int leaps = 0;
        boolean jaws = true;
        for (Animal d : pack) { leaps += ((AnimalBrain.PackDog) d.brain).leaps; jaws &= d.mouthOpen; }
        float sprinter = absentialis.neuralai.integration.Archetypes.arch("sprinter").runSpeed();
        t.check("infected dogs come in packs of two to six, run faster than a sprinter, go for people - leaping the last of the way, mouths open - and leave the other dead alone",
                small == 2 && big == 6 && Kind.IDOG.run > sprinter && reached > 0 && reached < 6 && leaps > 0 && jaws && deadOne.hp >= deadHp - 0.01f && rp.animals.bites > 0,
                "packs " + small + ".." + big + ", " + Kind.IDOG.run + " m/s against " + sprinter + ", teeth in after " + reached + " s, " + leaps + " leaps, " + rp.animals.bites + " bites, the walker lost " + (deadHp - deadOne.hp));

        // ---- the mutated spider
        Raid rm = SelfTests11.flat(new Profile());
        World wm = rm.world;
        rm.player.x = 120.5f; rm.player.z = 120.5f;
        Animal brood = rm.animals.spawn(Kind.BROOD, 170.5f, G, 170.5f);
        brood.young = 4;
        Actor prey = SelfTests11.spawn(rm, "survivors", 170.5f, 182.5f, 0);
        rm.freezeOnly(prey);
        prey.weapon = null; prey.hp = prey.maxHp = 2000;
        SelfTests11.run(rm, 14);
        AnimalBrain.Brood mind = (AnimalBrain.Brood) brood.brain;
        boolean three = mind.spits > 0 && mind.tackles > 0 && mind.thrusts > 0 && rm.venomPools > 0 && prey.hp < 2000;
        // a ceiling over the way to its prey: it goes up and along it
        for (int x = 196; x <= 204; x++) for (int z = 196; z <= 224; z++) wm.setRaw(x, G + 4, z, Block.CONCRETE);
        wm.computeHeights();
        Animal climber = rm.animals.spawn(Kind.BROOD, 200.5f, G, 198.5f);
        climber.young = 0;
        Actor under = SelfTests11.spawn(rm, "survivors", 200.5f, 220.5f, 0);
        rm.freezeOnly(under);
        under.weapon = null; under.hp = under.maxHp = 2000;
        boolean hung = false;
        float ceilZ = climber.z;
        for (int i = 0; i < 60 * 8; i++) { rm.update(1 / 60f); if (climber.onCeiling) { hung = true; ceilZ = Math.max(ceilZ, climber.z); } }
        int kinds = rm.animals.count(Kind.SPIDERLING);
        rm.animals.hurt(brood, 9999, rm.player.id, 1, 0);
        int young = rm.animals.count(Kind.SPIDERLING) - kinds;
        t.check("the mutated spider spits venom from a distance, throws itself at what is nearer and stabs at what is in reach; under a ceiling it crosses on the ceiling; and one that is carrying young lets them out when it dies",
                three && hung && ceilZ > 202 && young == 4, "spits " + mind.spits + ", tackles " + mind.tackles + ", thrusts " + mind.thrusts + ", pools " + rm.venomPools + ", on the ceiling " + hung + " as far as z " + ceilZ + ", young " + young);
        for (int x = 196; x <= 204; x++) for (int z = 196; z <= 224; z++) wm.setRaw(x, G + 4, z, Block.AIR);
        wm.computeHeights();
    }
}
