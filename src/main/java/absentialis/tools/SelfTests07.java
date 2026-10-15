package absentialis.tools;

import absentialis.neuralai.integration.Archetypes;
import absentialis.neuralai.integration.Breach;
import absentialis.neuralai.integration.Creatures;
import absentialis.neuralai.integration.Steering;
import absentialis.entity.Actor;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemInstance;
import absentialis.persistence.Profile;
import absentialis.scene.Gore;
import absentialis.scene.Raid;
import absentialis.world.Block;
import absentialis.world.World;

/**
 * Version 0.7 gates. Each section names the line of the 0.7 brief (instructions.md) it covers; the tracker
 * ({@code docs/V0.7_IMPLEMENTATION.md}) points back here.
 */
final class SelfTests07 {
    private SelfTests07() {}

    static final int G = World.GROUND;

    static void run(String which, boolean all, SelfTests04.Check t) {
        if (all || which.contains("v07") || which.contains("infected07")) SelfTests.guard(() -> infected(t));
        if (all || which.contains("v07") || which.contains("special07")) SelfTests.guard(() -> specials(t));
        if (all || which.contains("v07") || which.contains("vehicle07")) SelfTests.guard(() -> runDown(t));
        if (all || which.contains("v07") || which.contains("door07")) SelfTests.guard(() -> carDoors(t));
        if (all || which.contains("v07") || which.contains("interior07")) SelfTests.guard(() -> interiors(t));
        if (all || which.contains("v07") || which.contains("devmap07")) SelfTests.guard(() -> devmap(t));
        if (all || which.contains("v07") || which.contains("audio07")) SelfTests.guard(() -> audio(t));
        if (all || which.contains("v07") || which.contains("craft07")) SelfTests.guard(() -> craftAndEquip(t));
        NeuralTests.run(which, all, t);
    }

    private static void run(Raid r, float seconds) { for (int i = 0; i < Math.round(seconds * 60); i++) r.update(1 / 60f); }

    private static Actor spawn(Raid r, String what, float x, float z, float yaw) {
        Actor a = r.debugSpawn(what, x, z, G);
        a.yaw = yaw; a.homeX = x; a.homeZ = z; a.wakeUntil = 1e9f;
        return a;
    }

    /** A wall across x0..x1 at one z, four blocks high, of something solid. */
    private static void wall(World w, int x0, int x1, int z, int id) { for (int x = x0; x <= x1; x++) for (int y = G; y < G + 5; y++) w.setRaw(x, y, z, id); }

    // ------------------------------------------------------------------ the ordinary infected: wandering, noticing, holes
    private static void infected(SelfTests04.Check t) {
        System.out.println("[v0.7 infected: they wander, the head turns to a noise before the body goes to it, and a one-block hole is a way through]");
        Content.load();
        // ---- "Infected zombies just stand around when idle, they should wander around"
        Raid r = SelfTests.stage(new Profile());
        r.player.x = 300; r.player.z = 560;                       // in the bubble, far outside what they can see
        Actor[] pack = new Actor[6];
        float[] x0 = new float[6], z0 = new float[6], path = new float[6], lx = new float[6], lz = new float[6], headTurn = new float[6];
        for (int i = 0; i < 6; i++) { pack[i] = spawn(r, "walker", 280 + i * 8, 500 + (i % 2) * 6, i); x0[i] = lx[i] = pack[i].x; z0[i] = lz[i] = pack[i].z; }
        float far = 0;
        for (int f = 0; f < 60 * 45; f++) {
            r.update(1 / 60f);
            for (int i = 0; i < 6; i++) { Actor a = pack[i]; path[i] += (float) Math.hypot(a.x - lx[i], a.z - lz[i]); lx[i] = a.x; lz[i] = a.z; headTurn[i] = Math.max(headTurn[i], Math.abs(a.headLook)); far = Math.max(far, a.distXZ(x0[i], z0[i])); }
        }
        int moved = 0, turned = 0;
        float least = 1e9f;
        for (int i = 0; i < 6; i++) { if (path[i] > 4) moved++; if (headTurn[i] > 0.25f) turned++; least = Math.min(least, path[i]); }
        t.check("idle infected wander: in 45 s every one of six has walked somewhere, none has left its patch, and their heads move",
                moved == 6 && far < 50 && turned >= 4 && java.util.Arrays.stream(pack).noneMatch(a -> a.state.equals("engage")), String.format("%d of 6 walked more than 4 m (least %.1f m), furthest from home %.0f m, %d turned their heads", moved, least, far, turned));

        // ---- "turn their heads when they hear a noise and investigate sounds"
        Raid rn = SelfTests.stage(new Profile());
        rn.player.x = 300; rn.player.z = 560;
        Actor ear = spawn(rn, "walker", 300, 500, 0);              // facing -z; the noise is behind and to the side
        ear.thinkTimer = 30;
        rn.update(1 / 60f);
        rn.emitSound(308, G + 1, 506, 20, "debris", 0, false);
        float yawAt = ear.yaw;
        run(rn, 0.3f);                                                 // 0.8: it hears with a mind now, and a mind takes a moment
        String heard = ear.state;
        float headAt = ear.headLookTarget;
        run(rn, 0.25f);
        boolean headFirst = Math.abs(ear.headLook) > 0.2f && Math.abs(Steering.wrapPi(ear.yaw - yawAt)) < 0.25f && rn.time < ear.noticeUntil;
        float d0 = ear.distXZ(308, 506);
        boolean went = false;
        for (int f = 0; f < 60 * 14 && !went; f++) { rn.update(1 / 60f); went = ear.distXZ(308, 506) < 3.2f; }
        t.check("a noise is noticed before it is walked to: the head comes round first, the body follows, then it goes and looks",
                "notice".equals(heard) && Math.abs(headAt) > 0.3f && headFirst && went, heard + ", head target " + String.format("%.2f", headAt) + ", head led the body " + headFirst + String.format(", %.1f m -> %.1f m from the noise", d0, ear.distXZ(308, 506)));

        // ---- "Infected should be able to climb through open spaces of 1x1 holes unless it is more than 3 blocks or higher than them"
        Raid rb = SelfTests.stage(new Profile());
        World w = rb.world;
        rb.player.x = 300; rb.player.z = 470;
        int wz = 495;
        wall(w, 250, 350, wz, Block.WOOD_PLANKS);
        w.setRaw(300, G + 1, wz, Block.AIR);                        // a window with the glass out, one block up
        boolean isHole = Breach.hole(w, 300, G + 1, wz, 0, -1), notHole = !Breach.hole(w, 302, G + 1, wz, 0, -1);
        Actor climber = spawn(rb, "walker", 300.5f, 498.5f, 0);
        int fin0 = Breach.finished;
        rb.emitSound(300.5f, G + 1, 488, 60, "shot", 0, false);
        float through = -1, highest = 0;
        boolean bellied = false;
        for (int f = 0; f < 60 * 16 && through < 0; f++) { rb.update(1 / 60f); highest = Math.max(highest, climber.y - G); bellied |= climber.crawling && climber.breachT > 0; if (climber.z < wz - 0.4f && climber.breachT <= 0) through = rb.time; }
        t.check("an infected stopped by a wall finds the one-block opening in it and climbs through: up to the sill, through on its belly, down the far side",
                isHole && notHole && through > 0 && Breach.finished == fin0 + 1 && highest > 0.9f && bellied && climber.y < G + 0.2f, String.format("through after %.1f s, rose %.1f blocks, on its belly %s, now at z %.1f", through, highest, bellied, climber.z));
        // boarded up (the Wooden Plank's use): the opening is a wall again
        rb.actors.remove(climber);
        ItemInstance plank = new ItemInstance("mat.plank");
        InventoryService.store(plank, rb.profile.loadout);
        rb.player.x = 300.5f; rb.player.z = 493.2f; rb.player.y = G;
        int[] spot = rb.barricadeSpot(300.5f, G + 1.6f, 493.2f, 0, 0, 1);
        String none = rb.barricadeSpot(304.5f, G + 1.6f, 493.2f, 0, 0, 1) == null ? "wall" : "hole";
        String did = spot == null ? "no opening found" : rb.barricade(spot);
        String again = spot == null ? "" : rb.barricadeSpot(300.5f, G + 1.6f, 493.2f, 0, 0, 1) == null ? "closed" : "still open";
        Actor second = spawn(rb, "walker", 300.5f, 498.5f, 0);
        rb.player.z = 470;
        rb.emitSound(300.5f, G + 1, 488, 60, "shot", 0, false);
        int st0 = Breach.started;
        run(rb, 10);
        t.check("a Wooden Plank boards up a one-block opening - one plank, used up - and the infected can no longer come through it",
                "Boarded up".equals(did) && "closed".equals(again) && "wall".equals(none) && w.get(300, G + 1, wz) == Block.BOARDED && rb.profile.loadout.count("mat.plank") == 0 && rb.barricades == 1 && Breach.started == st0 && second.z > wz + 0.5f,
                did + "; opening now " + again + "; planks left " + rb.profile.loadout.count("mat.plank") + "; the next one stayed at z " + String.format("%.1f", second.z));
        // more than three blocks up: out of reach
        rb.actors.remove(second);
        w.setRaw(300, G + 1, wz, Block.WOOD_PLANKS);
        for (int y = G + 5; y < G + 7; y++) for (int x = 250; x <= 350; x++) w.setRaw(x, y, wz, Block.WOOD_PLANKS);
        w.setRaw(310, G + 4, wz, Block.AIR);                        // four blocks above the feet
        w.setRaw(290, G + 3, wz, Block.AIR);                        // three: the highest that can be reached
        Actor reach = spawn(rb, "walker", 290.5f, 498.5f, 0);
        rb.emitSound(290.5f, G + 1, 488, 300, "shot", 0, false);
        boolean highThrough = false, reachThrough = false;
        float rose = 0;
        for (int f = 0; f < 60 * 16 && !reachThrough; f++) { rb.update(1 / 60f); rose = Math.max(rose, reach.y - G); reachThrough = reach.z < wz - 0.4f && reach.breachT <= 0; }
        rb.actors.remove(reach);
        Actor low = spawn(rb, "walker", 310.5f, 498.5f, 0);
        rb.emitSound(310.5f, G + 1, 488, 300, "shot", 0, false);
        for (int f = 0; f < 60 * 14; f++) { rb.update(1 / 60f); highThrough |= low.z < wz - 0.4f; }
        t.check("an opening three blocks up can be climbed to; one four blocks up cannot (and a Tank, a Bomber or a Butcher fits through neither)",
                reachThrough && !highThrough && Breach.MAX_SILL == 3 && !Breach.fits(spawn(rb, "tank", 200, 400, 0)) && !Breach.fits(spawn(rb, "bomber", 204, 400, 0)) && !Breach.fits(spawn(rb, "butcher", 208, 400, 0)) && Breach.fits(spawn(rb, "sprinter", 212, 400, 0)),
                "three up: through " + reachThrough + String.format(" (climbed %.1f blocks)", rose) + "; four up: through " + highThrough + String.format(" (still at z %.1f)", low.z));
        for (int x = 250; x <= 350; x++) for (int y = G; y < G + 8; y++) w.setRaw(x, y, wz, Block.AIR);
    }

    // ------------------------------------------------------------------ "NPCs should be able to physically react much better to being ran over by vehicles"
    /** One car driven at one standing person at a given speed: {went down, metres carried, peak height of the pelvis above standing, alive, knockdowns}. */
    private static float[] hitAt(float speed, boolean corpse) {
        Raid r = SelfTests.stage(new Profile());
        r.player.x = 300; r.player.z = 540;
        absentialis.scene.VehicleSystem.Car c = r.vehicles.spawn("car.volna", 300.5f, G, 470.5f, 0, 1);
        for (int i = 0; i < 60; i++) r.update(1 / 60f);
        float fx = c.sim.body.fwdX(), fz = c.sim.body.fwdZ();
        float[] o = new float[3];
        c.sim.origin(o);
        Actor v = spawn(r, "survivors", o[0] + fx * 5.5f, o[2] + fz * 5.5f, 0);
        v.hostileToPlayer = false; v.hp = v.maxHp = 600;
        r.freezeAI = true;
        if (corpse) { r.damageActor(v, null, 9999, null, "torso", v.x, v.y + 1, v.z); for (int i = 0; i < 150; i++) r.update(1 / 60f); }
        float x0 = v.x, z0 = v.z, y0 = v.y, peak = 0, far = -99, carGone = 0;
        boolean down = false;
        for (int i = 0; i < 60 * 4; i++) {
            if (i * speed / 60f < 14) { c.sim.wake(); c.sim.body.vx = fx * speed; c.sim.body.vz = fz * speed; }      // held at speed for fourteen metres
            r.update(1 / 60f);
            down |= v.ragdoll != null && !corpse;
            peak = Math.max(peak, v.y - y0);
            if (v.ragdoll != null) { float rx = v.ragdoll.x[absentialis.character.Ragdoll.PELVIS], rz = v.ragdoll.z[absentialis.character.Ragdoll.PELVIS]; far = Math.max(far, (rx - x0) * fx + (rz - z0) * fz); peak = Math.max(peak, v.ragdoll.y[absentialis.character.Ragdoll.PELVIS] - y0 - 0.95f); }
            c.sim.origin(o);
            carGone = (o[0] - x0) * fx + (o[2] - z0) * fz;
        }
        float carried = corpse || far < -90 ? (v.x - x0) * fx + (v.z - z0) * fz : far;
        if (System.getenv("ABS_DEBUG_CAR") != null) System.out.println(String.format("    [car %.1f m/s] body furthest %.2f, final %.2f, car ended %.1f past the mark, run-overs %d, knockdowns %d", speed, far, (v.x - x0) * fx + (v.z - z0) * fz, carGone, r.vehicles.runOvers, r.vehicles.knockdowns));
        return new float[]{down ? 1 : 0, carried, peak, v.dead ? 0 : 1, r.vehicles.knockdowns, r.vehicles.runOvers};
    }

    private static void runDown(SelfTests04.Check t) {
        System.out.println("[v0.7 vehicles and bodies: a person hit by a car is the physics engine's from the bumper on]");
        Content.load();
        absentialis.vehicle.CarHandlingData.load();
        float[] crawl = hitAt(2.4f, false), town = hitAt(7f, false), fast = hitAt(15f, false), body = hitAt(6f, true);
        t.check("a car at a crawl shoulders a person aside; at town speed it takes them off their feet and carries them; at speed it throws them further still",
                crawl[0] == 0 && crawl[4] == 0 && crawl[1] > 0.3f && town[0] == 1 && town[4] == 1 && town[1] > 1.5f && fast[0] == 1 && fast[1] > town[1] + 1.0f,   // (0.9: a body keeps its bulk and its momentum under physics, so the town-speed carry is longer; the margin is a metre)
                String.format("2.4 m/s: down %s, moved %.1f m; 7 m/s: down %s, carried %.1f m, lifted %.2f m, alive %s; 15 m/s: carried %.1f m, lifted %.2f m, alive %s", crawl[0] == 1, crawl[1], town[0] == 1, town[1], town[2], town[3] == 1, fast[1], fast[2], fast[3] == 1));
        t.check("a body lying in the road is shoved and rolled by a car driven over it (it is not scenery)", body[5] >= 1 && Math.abs(body[1]) > 0.4f, String.format("%d shoves, the body moved %.1f m along the car's path", (int) body[5], body[1]));
    }

    // ------------------------------------------------------------------ "Cars still don't properly have their doors opening/shutting during the enter/exit animations"
    private static void carDoors(SelfTests04.Check t) {
        System.out.println("[v0.7 car doors: the door leads the body in and out, and is shut behind it]");
        absentialis.vehicle.CarHandlingData.load();
        Raid r = SelfTests.stage(new Profile());
        absentialis.scene.VehicleSystem vs = r.vehicles;
        absentialis.scene.VehicleSystem.Car c = vs.spawn("car.volna", 300.5f, G, 470.5f, 0, 1);
        run(r, 1);
        float[] o = new float[3];
        c.sim.origin(o);
        r.player.x = o[0] - c.sim.body.rightX() * 2.0f - c.sim.body.fwdX() * 1.0f; r.player.z = o[2] - c.sim.body.rightZ() * 2.0f - c.sim.body.fwdZ() * 1.0f; r.player.y = G;
        boolean in = vs.enter();
        float sx = r.player.x, sz = r.player.z;
        // getting in: how far open the door was when the body first left the door's side for the seat, the widest it got, and when it was shut again
        float openAtStep = -1, widest = 0, doorAtSeat = -1, shutAfter = -1, seatedAt = -1, lastGap = 1e9f;
        float[] seat = new float[3];
        for (int f = 0; f < 60 * 6; f++) {
            r.update(1 / 60f);
            float k = vs.phaseProgress(), d = vs.doorOpen();
            widest = Math.max(widest, d);
            if (openAtStep < 0 && k > absentialis.scene.VehicleSystem.TO_DOOR + 0.02f) openAtStep = d;
            if (seatedAt < 0 && vs.inside() && k < 0) { seatedAt = r.time; doorAtSeat = d; }
            if (seatedAt > 0 && shutAfter < 0 && d < 0.04f) shutAfter = r.time - seatedAt;
        }
        boolean driving = vs.driving == c;
        String out = vs.exit();
        float openAtLeave = -1, widestOut = 0, shutOut = -1, outAt = -1, px = r.player.x, pz = r.player.z;
        for (int f = 0; f < 60 * 6; f++) {
            r.update(1 / 60f);
            float k = vs.phaseProgress(), d = vs.doorOpen();
            widestOut = Math.max(widestOut, d);
            if (openAtLeave < 0 && Math.hypot(r.player.x - px, r.player.z - pz) > 0.08f) openAtLeave = d;
            if (outAt < 0 && k < 0 && !vs.inside()) outAt = r.time;
            if (outAt > 0 && shutOut < 0 && d < 0.04f) shutOut = r.time - outAt;
        }
        t.check("getting into a car: to the door while it opens, in through an open door, and the door is pulled shut behind you",
                in && driving && openAtStep > 0.6f && widest > 0.95f && shutAfter >= 0 && shutAfter < 1.2f && vs.doorCycles >= 1,
                String.format("door %.0f%% open when the body started for the seat, %.0f%% at its widest, shut %.2f s after sitting down", openAtStep * 100, widest * 100, shutAfter));
        t.check("getting out: the door opens before the body moves, and is pushed shut afterwards",
                out == null && openAtLeave > 0.5f && widestOut > 0.95f && shutOut >= 0 && shutOut < 1.2f && vs.doorCycles >= 2 && !vs.inside(),
                String.format("door %.0f%% open when the body first moved, %.0f%% at its widest, shut %.2f s after stepping clear", openAtLeave * 100, widestOut * 100, shutOut));
    }

    // ------------------------------------------------------------------ "interior ceilings should always be no less than four blocks high ... shelf ... in repetitive fashion ... avoided at all costs"
    /**
     * Every room of every building in the generated city, measured: the clear height over every cell a body can stand
     * on, the longest unbroken run of one kind of container, and that each room can still be walked from each of its
     * doors (the generator's own validator answers the last; it is read here, not repeated).
     */
    private static void interiors(SelfTests04.Check t) {
        System.out.println("[v0.7 interiors: every room of every building - ceiling height, no rows of the same container, nothing in the way]");
        for (long seed : SelfTests06.SEEDS) {
            World w = SelfTests06.world(seed);
            int rooms = 0, low = 0, lowest = 99, worstRun = 0, runs = 0, unreachable = 0, cells = 0, shelves = 0, racks = 0, containers = 0, decor = 0;
            String lowWhere = "", runWhere = "", lowCell = "";
            java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
            for (absentialis.world.MapData.Building b : w.map.buildings) for (absentialis.world.MapData.Room rm : b.rooms) {
                rooms++;
                if (!rm.reachable) unreachable++;
                int roomLow = 99;
                for (int x = rm.x0; x <= rm.x1; x++) for (int z = rm.z0; z <= rm.z1; z++) {
                    int id = w.get(x, rm.y + 1, z);
                    Block bl = Block.ALL[id];
                    if (id == Block.SHELF) shelves++;
                    if (id == Block.RACK) racks++;
                    if (id == Block.PLANT || id == Block.FLOOR_LAMP || id == Block.NIGHTSTAND || id == Block.DEBRIS_PILE) decor++;
                    if (id != 0 && bl.is(Block.CONTAINER)) {
                        containers++;
                        kinds.merge(bl.name, 1, Integer::sum);
                        // a run of the same container along x or z, counted from its first cell
                        for (int ax = 0; ax < 2; ax++) {
                            int dx = ax == 0 ? 1 : 0, dz = 1 - dx;
                            if (w.get(x - dx, rm.y + 1, z - dz) == id) continue;
                            int n = 1;
                            while (w.get(x + dx * n, rm.y + 1, z + dz * n) == id) n++;
                            if (n >= 3) { runs++; if (n > worstRun) { worstRun = n; runWhere = bl.name + " x" + n + " in a " + rm.type + " of " + (b.landmark.isEmpty() ? b.archetype : b.landmark); } }
                        }
                    }
                    if (w.solid(x, rm.y + 1, z) || !w.solid(x, rm.y, z)) continue;       // furniture, a stair, a void
                    cells++;
                    int h = 0;
                    while (h < 12 && !Block.ALL[w.get(x, rm.y + 1 + h, z)].solid()) h++;
                    // a light fitting hangs from a ceiling: it is not the ceiling
                    if (h < 12 && (w.get(x, rm.y + 1 + h, z) == Block.LAMP || w.get(x, rm.y + 1 + h, z) == Block.LAMP_OFF)) h++;
                    if (h < roomLow && h < 4) lowCell = " at " + x + "," + (rm.y + 1) + "," + z + " under " + Block.ALL[w.get(x, rm.y + 1 + h, z)].name + ", standing on " + Block.ALL[w.get(x, rm.y, z)].name + ", room y " + rm.y + " floor " + rm.floor + ", building top " + b.topY;
                    roomLow = Math.min(roomLow, h);
                }
                if (roomLow < 4 && !rm.circulation) { low++; if (roomLow < lowest) { lowest = roomLow; lowWhere = rm.type + " of " + (b.landmark.isEmpty() ? b.archetype : b.landmark) + " (" + roomLow + ")"; } }
            }
            t.check("seed " + seed + ": no room has a ceiling lower than four blocks over anywhere a body can stand", low == 0 && rooms > 1000, rooms + " rooms, " + cells + " standable cells; " + (low == 0 ? "lowest clear height 4 or more everywhere" : low + " rooms under four - worst: " + lowWhere + lowCell));
            t.check("seed " + seed + ": no row of three or more of the same container anywhere, and most shelving is bare frames rather than something to open", worstRun < 3 && racks > shelves && decor > 150,
                    containers + " containers of " + kinds.size() + " kinds; longest run of one kind " + Math.max(worstRun, runs == 0 ? 2 : worstRun) + (runs == 0 ? " or fewer" : " (" + runs + " runs; " + runWhere + ")") + "; " + shelves + " stocked shelves to " + racks + " empty racks; " + decor + " plants, lamps, bedside tables and heaps");
            t.check("seed " + seed + ": every room can be walked to from its building's entrance (nothing left in a doorway or on a stair)", unreachable == 0, unreachable + " unreachable of " + rooms);
        }
    }

    // ------------------------------------------------------------------ "Add a separate map of conservative size called devmap ..."
    private static void devmap(SelfTests04.Check t) {
        System.out.println("[v0.7 devmap: a maze, a two-storey mansion with a double door, a long road, a yard of objects]");
        Content.load();
        World w = new World();
        absentialis.world.gen.DevMap.generate(w);
        w.lighting.computeAll();
        absentialis.world.gen.DevMap d = null;
        int rx0 = absentialis.world.gen.DevMap.ROAD_X0, rx1 = absentialis.world.gen.DevMap.ROAD_X1, rz = (absentialis.world.gen.DevMap.ROAD_Z0 + absentialis.world.gen.DevMap.ROAD_Z1) / 2;
        int paved = 0;
        for (int x = rx0; x <= rx1; x++) { int id = w.get(x, G - 1, rz); if ((id == Block.ASPHALT || id == Block.ROAD_LINE || id == Block.HAZARD_STRIPE) && !w.solid(x, G, rz) && !w.solid(x, G + 1, rz)) paved++; }
        int field = (absentialis.world.gen.DevMap.X1 - absentialis.world.gen.DevMap.X0 + 1) * (absentialis.world.gen.DevMap.Z1 - absentialis.world.gen.DevMap.Z0 + 1);
        t.check("the devmap is its own small map: a walled field with a straight, clear road well over a hundred metres long", w.dev && paved == rx1 - rx0 + 1 && paved >= 150 && field < World.W * World.D / 8 && w.map.insertions.size() == 4 && w.map.exits.size() == 1,
                paved + " m of clear road; the field is " + (absentialis.world.gen.DevMap.X1 - absentialis.world.gen.DevMap.X0 + 1) + " x " + (absentialis.world.gen.DevMap.Z1 - absentialis.world.gen.DevMap.Z0 + 1) + " m (" + (field * 100 / (World.W * World.D)) + "% of the Zone's area); " + w.map.insertions.size() + " insertions, " + w.map.exits.size() + " exit");
        // the maze: one way through, found by the game's own path-finder
        int[] way = w.nav.path(absentialis.world.gen.DevMap.mazeInX + 0.5f, G, absentialis.world.gen.DevMap.mazeInZ + 0.5f, absentialis.world.gen.DevMap.mazeOutX + 0.5f, absentialis.world.gen.DevMap.mazeOutZ + 0.5f, 200000, false);
        int steps = way == null ? 0 : way.length / 3;
        double crow = Math.hypot(absentialis.world.gen.DevMap.mazeOutX - absentialis.world.gen.DevMap.mazeInX, absentialis.world.gen.DevMap.mazeOutZ - absentialis.world.gen.DevMap.mazeInZ);
        boolean inWalls = false;
        if (way != null) for (int i = 0; i + 2 < way.length; i += 3) inWalls |= w.solid(way[i], way[i + 1], way[i + 2]);
        t.check("the maze can be solved by the path-finder, by a route a good deal longer than the straight line and through no wall", way != null && steps > crow * 1.4 && !inWalls,
                way == null ? "no path" : steps + " cells from the way in to the way out (" + String.format("%.0f", crow) + " m as the crow flies)");
        // the mansion: two storeys, a double door, and upstairs reachable from the street
        absentialis.world.MapData.Building m = absentialis.world.gen.DevMap.mansion;
        boolean dbl = false;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -1; dz <= 2; dz++) dbl |= w.get(m.entryX + dx, G, m.entryZ + dz) == Block.DOOR_DOUBLE_WOOD;
        absentialis.world.MapData.Room up = null;
        int upper = 0;
        java.util.Set<String> kinds = new java.util.TreeSet<>();
        for (absentialis.world.MapData.Room rm : m.rooms) { kinds.add(rm.type); if (rm.floor == 1) { upper++; if (up == null && !rm.circulation) up = rm; } }
        int[] down = null;
        if (up != null) {
            for (int x = up.x0; x <= up.x1 && down == null; x++) for (int z = up.z0; z <= up.z1 && down == null; z++)
                if (w.nav.stand(x, up.y + 1, z, true)) down = w.nav.path(x + 0.5f, up.y + 1, z + 0.5f, m.entryX + 0.5f, m.entryZ + 0.5f, 200000, true);
        }
        t.check("the mansion is two storeys, furnished, entered by a double door, and its upstairs can be walked to from the street", m.floors == 2 && dbl && upper >= 3 && m.rooms.size() >= 10 && down != null,
                m.rooms.size() + " rooms (" + upper + " upstairs) of " + kinds.size() + " kinds; double door " + dbl + "; an upstairs " + (up == null ? "?" : up.type) + " to the front step: " + (down == null ? "no path" : down.length / 3 + " cells"));
        // the yard: openings at one to four blocks, and things to search
        int hx = absentialis.world.gen.DevMap.holesX, hz = absentialis.world.gen.DevMap.holesZ, holes = 0, boxes = 0;
        for (int k = 0; k < 4; k++) if (Breach.hole(w, hx + 2 + k * 4, G + 1 + k, hz, 0, 1)) holes++;
        for (int x = absentialis.world.gen.DevMap.X0; x <= absentialis.world.gen.DevMap.X1; x++) for (int z = absentialis.world.gen.DevMap.YARD_Z; z < absentialis.world.gen.DevMap.YARD_Z + 28; z++) if (Block.ALL[w.get(x, G, z)].is(Block.CONTAINER)) boxes++;
        Raid r = new Raid(w, new Profile(), new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        long others = r.actors.stream().filter(a -> !a.isPlayer).count();
        t.check("the yard has the test objects (openings one to four blocks up, ten kinds of container) and a raid on the devmap starts empty", holes == 4 && boxes >= 10 && others == 0 && r.vehicles.cars.isEmpty() && Math.abs(r.player.x - (rx0 + 6)) < 4,
                holes + " openings, " + boxes + " containers; " + others + " other actors and " + r.vehicles.cars.size() + " vehicles at the start; the player arrives at " + String.format("%.0f, %.0f", r.player.x, r.player.z));
    }

    // ------------------------------------------------------------------ "Replace the sounds of the infected with high quality ones you can find online" / removed ambience
    private static void audio(SelfTests04.Check t) {
        System.out.println("[v0.7 audio: recorded voices for the infected and for people, the new effects, no synthesised district beds]");
        java.util.Map<String, float[]> bank = new java.util.HashMap<>();
        new absentialis.engine.audio.SoundBank().build(bank);
        new absentialis.engine.audio.SoundBank04().build(bank);
        new absentialis.engine.audio.SoundBank06().build(bank);
        new absentialis.engine.audio.SoundBank07().build(bank);
        java.util.Map<String, float[]> synth = new java.util.HashMap<>(bank);
        int loaded = absentialis.engine.audio.AssetSounds.load(bank);
        String[] voices = {"groan", "groan_2", "groan_3", "groan_4", "groan2", "growl", "growl_2", "growl_3", "growl_4", "shriek", "shriek_2", "shriek_3", "screech", "roar", "roar_2", "gladiator_roar", "gurgle", "gurgle_2", "retch", "retch_2",
                "inf_feed", "inf_feed_2", "inf_feed_3", "bite", "infected_turn", "inf_death", "inf_death_2", "inf_death_3", "inf_death_4", "hurt", "hurt_2", "hurt_3", "hurt_4", "hurt_5", "death", "death_2", "death_3", "death_4", "death_5"};
        StringBuilder missing = new StringBuilder(), quiet = new StringBuilder();
        int recorded = 0;
        float shortest = 99, longest = 0;
        for (String v : voices) {
            float[] d = bank.get(v);
            if (d == null) { missing.append(v).append(' '); continue; }
            if (d != synth.get(v)) recorded++;
            double e = 0; float peak = 0;
            for (float s : d) { e += s * s; peak = Math.max(peak, Math.abs(s)); }
            float rms = (float) Math.sqrt(e / d.length), sec = d.length / (float) absentialis.engine.audio.UltraEngine.RATE;
            shortest = Math.min(shortest, sec); longest = Math.max(longest, sec);
            if (rms < 0.02f || peak < 0.5f || peak > 1.001f) quiet.append(v).append(String.format("(rms %.3f peak %.2f) ", rms, peak));
        }
        t.check("the infected and people have recorded voices: 39 takes from three CC0 packs, every one loaded over its synthesised stand-in, none silent, none clipped",
                missing.length() == 0 && quiet.length() == 0 && recorded == voices.length && loaded >= voices.length, recorded + " of " + voices.length + " are recordings (" + loaded + " asset files loaded), " + String.format("%.2f s to %.2f s long", shortest, longest) + (missing.length() > 0 ? "; missing: " + missing : "") + (quiet.length() > 0 ? "; too quiet or clipped: " + quiet : ""));
        String[] made = {"chainsaw", "chainsaw_rev", "chainsaw_stall", "claw_scrape", "claw_swing", "hammer", "casing_drop", "shell_drop", "spray_hiss"};
        StringBuilder bad = new StringBuilder();
        for (String m : made) { float[] d = bank.get(m); float peak = 0; if (d != null) for (float s : d) peak = Math.max(peak, Math.abs(s)); if (d == null || peak < 0.15f || peak > 1.6f || Float.isNaN(peak)) bad.append(m).append(' '); }
        t.check("what 0.7 added has a sound: the saw (idling, revving, stalling), the claws, a hammer on a plank, brass and hulls on the ground, the spray", bad.length() == 0, bad.length() == 0 ? made.length + " effects" : "missing or wrong: " + bad);
        // takes: asked for by number, every take comes up, and a sound with one take is itself
        absentialis.engine.audio.UltraEngine ae = new absentialis.engine.audio.UltraEngine();
        ae.samples.putAll(bank);
        java.util.Set<String> takes = new java.util.TreeSet<>();
        for (int i = 0; i < 40; i++) takes.add(ae.variant("groan", i));
        t.check("a sound with several takes plays a different one each time it is asked for", takes.size() == 4 && "screech".equals(ae.variant("screech", 7)) && ae.variant("death", 3).equals("death_4"), "groan: " + takes);
        // the beds: a raid screen asks for no district ambience at all
        boolean none = true;
        try {
            java.lang.reflect.Method m = absentialis.scene.RaidScreen.class.getDeclaredMethod("ambienceFor", absentialis.world.District.class);
            m.setAccessible(true);
            // 0.8: the districts have beds again - recordings of each (the sound pack), which is not what 0.7 took out.
            // What must still be true is that none of them is one of the synthesised beds.
            absentialis.engine.audio.UltraEngine whole = new absentialis.engine.audio.UltraEngine();
            whole.buildBank();
            for (absentialis.world.District d : absentialis.world.District.values()) { Object bed = m.invoke(null, d); none &= bed == null || whole.hasStream("amb_" + bed) || whole.recorded.contains("amb_" + bed); }
        } catch (Exception e) { none = false; }
        t.check("no district has a synthesised bed under it any more: not the Outskirts' hiss, not the Residential air, not Downtown's bells", none, absentialis.world.District.values().length + " districts: recorded beds (0.8) or none");
    }

    // ------------------------------------------------------------------ the 0.7 recipes, the craft list, shift-click
    private static void craftAndEquip(SelfTests04.Check t) {
        System.out.println("[v0.7 crafting and quick equip: the ten new recipes, only what can be made is offered, the stash counts at home, shift-click equips]");
        Content.load();
        String[][] want = {{"ammo.handgun", "mat.gunpowder_s", "mat.gunpowder_s"}, {"ammo.shells", "mat.gunpowder_s", "mat.gunpowder_l"}, {"ammo.smg", "mat.gunpowder_s", "mat.gunpowder_m"}, {"ammo.rifle", "mat.gunpowder_m", "mat.gunpowder_m"},
                {"ammo.assault", "mat.gunpowder_m", "mat.gunpowder_l"}, {"ammo.magnum", "mat.gunpowder_l", "mat.gunpowder_l"}, {"ammo.bolt", "mat.plank", "mat.gunpowder_s"}, {"grenade.incendiary", "mat.grenade_casing", "mat.gunpowder_l"},
                {"grenade.flash", "mat.grenade_casing", "mat.gunpowder_s"}, {"grenade.frag", "mat.grenade_casing", "mat.gunpowder_m"}};
        StringBuilder wrong = new StringBuilder();
        for (String[] e : want) {
            boolean found = false;
            for (Content.Recipe r : Content.RECIPES) {
                if (!r.outId.equals(e[0]) || !r.where.equals("field")) continue;
                java.util.Map<String, Integer> in = new java.util.TreeMap<>(), exp = new java.util.TreeMap<>();
                for (Object[] i : r.inputs) in.merge((String) i[0], (Integer) i[1], Integer::sum);
                exp.merge(e[1], 1, Integer::sum); exp.merge(e[2], 1, Integer::sum);
                found |= in.equals(exp);
            }
            if (!found) wrong.append(e[0]).append(' ');
        }
        t.check("the ten recipes of the brief exist as written (two powders make each kind of ammunition; a casing and a powder make each grenade; a plank and light powder make bolts)", wrong.length() == 0,
                wrong.length() == 0 ? "Light Gunpowder / Gunpowder / Heavy Gunpowder, Wooden Plank, Grenade Casing -> 7 ammunition types and 3 grenades" : "missing or different: " + wrong);
        // only what can be made is offered; the stash counts at home and not in the field
        Profile p = new Profile();
        p.applyCapacity();
        Content.Recipe hg = null, mag = null;
        for (Content.Recipe r : Content.RECIPES) { if (r.id.equals("gp_handgun") || r.outId.equals("ammo.handgun") && r.where.equals("field")) hg = r; if (r.outId.equals("ammo.magnum") && r.where.equals("field")) mag = r; }
        ItemInstance powder = new ItemInstance("mat.gunpowder_s");
        powder.qty = 6;
        InventoryService.add(powder, p.stash);
        int offeredField = 0, offeredHome = 0;
        for (Content.Recipe r : Content.RECIPES) { if (!r.where.equals("field")) continue; if (absentialis.scene.LoadoutPanel.canCraft(p, r, false)) offeredField++; if (absentialis.scene.LoadoutPanel.canCraft(p, r, true)) offeredHome++; }
        int before = p.loadout.count("ammo.handgun") + (int) p.stash.items.stream().filter(i -> i.defId.equals("ammo.handgun")).mapToInt(i -> i.qty).sum();
        String made = absentialis.scene.LoadoutPanel.craft(p, hg, true), refused = absentialis.scene.LoadoutPanel.craft(p, mag, true), field = absentialis.scene.LoadoutPanel.craft(p, hg, false);
        int after = p.loadout.count("ammo.handgun") + (int) p.stash.items.stream().filter(i -> i.defId.equals("ammo.handgun")).mapToInt(i -> i.qty).sum();
        int left = absentialis.scene.LoadoutPanel.owned(p, "mat.gunpowder_s", true);
        t.check("the craft list offers only what can be made now: with six Light Gunpowder in the stash exactly one recipe at home and none in the field; making it takes two and gives the rounds",
                offeredField == 0 && offeredHome == 1 && made == null && refused != null && field != null && left == 4 && after == before + hg.outQty && p.verify() == null,
                "offered in the field " + offeredField + ", at home " + offeredHome + "; made " + (after - before) + " rounds, " + left + " powder left; Magnum Ammo: " + refused + "; in the field: " + field);
        // shift-click with nothing else open: it goes on
        Profile q = new Profile();
        q.starterKit(); q.applyCapacity();
        absentialis.ui.InventoryUi ui = new absentialis.ui.InventoryUi(null, null);
        ui.owner = q.loadout;
        ItemInstance cap = new ItemInstance("helmet.baseball"), vest = new ItemInstance("armor.vest_soft"), gun = new ItemInstance("weapon.yellow9"), beans = new ItemInstance("food.beans");
        for (ItemInstance i : new ItemInstance[]{cap, vest, gun, beans}) InventoryService.add(i, q.loadout.caseGrid);
        ItemInstance oldGun = q.loadout.slot("sidearm");
        boolean e1 = ui.quickEquip(cap), e2 = ui.quickEquip(vest), e3 = ui.quickEquip(gun), e4 = ui.quickEquip(beans);
        t.check("shift-click equips: a cap and a vest go onto their free slots, a second handgun changes places with the one worn, and a tin of beans is not something to wear",
                e1 && e2 && e3 && !e4 && q.loadout.slot("head") == cap && q.loadout.slot("armor") == vest && q.loadout.slot("sidearm") == gun && oldGun != null && oldGun.container == q.loadout.caseGrid && ui.quickEquips == 3 && q.verify() == null,
                "head: " + (q.loadout.slot("head") == null ? "-" : q.loadout.slot("head").name()) + ", armor: " + (q.loadout.slot("armor") == null ? "-" : q.loadout.slot("armor").name()) + ", sidearm: " + (q.loadout.slot("sidearm") == null ? "-" : q.loadout.slot("sidearm").name()) + "; the old handgun is " + (oldGun != null && oldGun.container == q.loadout.caseGrid ? "in the case" : "lost") + "; integrity " + q.verify());
    }

    private static Actor at(float x, float z) { Actor a = new Actor(); a.x = x; a.z = z; a.y = G; return a; }

    // ------------------------------------------------------------------ the special infected, and bile
    private static void specials(SelfTests04.Check t) {
        System.out.println("[v0.7 special infected: Bomber and bile, Spewer, the Tank's throw, the Gladiator, the Butcher]");
        Content.load();
        boolean names = Archetypes.INFECTED.length == 9 && "Crawler".equals(Archetypes.displayName("crawler")) && "Tyrant".equals(Archetypes.displayName("tyrant")) && Archetypes.arch("crawler").sight() == 0 && "Bomber".equals(Archetypes.displayName("bomber")) && "Spewer".equals(Archetypes.displayName("spewer")) && "Tank".equals(Archetypes.displayName("tank")) && "Gladiator".equals(Archetypes.displayName("gladiator")) && "Butcher".equals(Archetypes.displayName("butcher"));
        boolean special = Archetypes.special("bomber") && Archetypes.special("spewer") && Archetypes.special("tank") && Archetypes.special("gladiator") && Archetypes.special("butcher") && !Archetypes.special("walker") && !Archetypes.special("sprinter") && !Archetypes.special("rifleman");
        t.check("seven kinds of infected: two ordinary, five special - Bomber (renamed from Boomer; old saves still load), Spewer, Tank, Gladiator, Butcher", names && special && Archetypes.arch("boomer") == Archetypes.arch("bomber") && Archetypes.arch("gladiator").sight() == 0, String.join(", ", Archetypes.INFECTED));

        // ---- Bomber: bursts into bile; bile marks, it does not hurt; the infected come for whoever wears it; fifteen seconds
        Raid r = SelfTests.stage(new Profile());
        r.god = false;
        Actor bomber = spawn(r, "bomber", 300, 496.5f, (float) Math.PI);
        Actor shielded = spawn(r, "survivors", 300, 492.2f, 0);    // within the burst, behind a wall
        shielded.hostileToPlayer = false;
        for (int x = 296; x <= 304; x++) for (int y = G; y < G + 4; y++) r.world.setRaw(x, y, 495, Block.WOOD_PLANKS);
        Actor nose = spawn(r, "walker", 300, 536, 0);              // 36 m off, the far side of the player, looking the other way
        Actor glad = spawn(r, "gladiator", 330, 500, 0);
        r.freezeAI = true;
        r.update(1 / 60f);
        float hp0 = r.player.hp;
        r.damageActor(bomber, null, 9999, null, "torso", bomber.x, bomber.y + 1, bomber.z);
        boolean burst = bomber.dead && bomber.burst, on = r.player.biled && r.player.bileUntil > r.time + 14 && r.player.bileUntil <= r.time + 15.01f, wallHeld = !shielded.biled;
        Raid.Zone pool = null;
        for (Raid.Zone z : r.zones) if ("bile".equals(z.kind)) pool = z;
        float blastHurt = hp0 - r.player.hp;
        r.actors.remove(shielded);
        r.freezeAI = false;
        run(r, 1.2f);
        boolean smelled = "engage".equals(nose.state) && nose.targetId == r.player.id, blindSmelled = "investigate".equals(glad.state) && Math.abs(glad.memX - r.player.x) < 6;
        float fx = r.bileFx(), hp1 = r.player.hp;
        r.actors.remove(nose); r.actors.remove(glad);
        run(r, 3);
        boolean harmless = pool != null && pool.dps == 0 && r.player.hp == hp1;         // three seconds stood in the pool: nothing
        run(r, 11.5f);
        boolean worn = !r.player.biled && r.bileFx() == 0;
        for (int x = 296; x <= 304; x++) for (int y = G; y < G + 4; y++) r.world.setRaw(x, y, 495, Block.AIR);
        t.check("a Bomber bursts into bile: whoever it reaches is covered (a wall keeps it off), the burst itself hurts little and the bile not at all",
                burst && on && wallHeld && harmless && blastHurt < 40 && r.gore.bileDrops > 20 && Gore.BILE == 0xFF1F4A12, String.format("burst %s, player covered %s, the one behind the wall covered %s, blast took %.0f hp, 3 s in the pool took %.0f, %d bile droplets (dark green, on the blood system)", burst, on, shielded.biled, blastHurt, hp1 - r.player.hp, r.gore.bileDrops));
        t.check("bile draws every infected in smelling distance to whoever wears it - the blind one too - blurs the wearer's sight, and wears off after fifteen seconds",
                smelled && blindSmelled && fx > 0.95f && worn && Raid.BILE_SECONDS == 15, "walker 36 m off: " + (smelled ? "coming for the player" : nose.state + " target " + nose.targetId) + "; Gladiator: " + (blindSmelled ? "coming" : glad.state + " mem " + glad.memX) + String.format("; sight lost %.0f%%; gone after 15 s: %s", fx * 100, worn));

        // ---- Spewer: a retch for warning, a pool where you stood, and it burns
        Raid rs = SelfTests.stage(new Profile());
        rs.god = false;
        Actor sp = spawn(rs, "spewer", 300, 492, (float) Math.PI);
        float px = rs.player.x, retchAt = -1, poolAt = -1;
        for (int f = 0; f < 60 * 6 && poolAt < 0; f++) {
            rs.update(1 / 60f);
            if (retchAt < 0 && rs.telegraphing(sp, "retch")) retchAt = rs.time;
            if (retchAt >= 0 && rs.time - retchAt > 0.3f) rs.player.x = px + 9;                 // the answer: move during the retch
            for (Raid.Zone z : rs.zones) if ("acid".equals(z.kind)) poolAt = rs.time;
        }
        float hpA = rs.player.hp;
        run(rs, 1.5f);
        boolean missed = rs.player.hp == hpA;
        Raid.Zone acid = rs.zones.isEmpty() ? null : rs.zones.get(0);
        float gap = sp.dist(rs.player);
        rs.player.x = acid == null ? px : acid.x; rs.player.z = acid == null ? rs.player.z : acid.z;
        float hpB = rs.player.hp;
        run(rs, 2);
        t.check("a Spewer retches for most of a second, then spits a pool where its target stood: move during the retch and it misses; stand in it and it burns",
                retchAt >= 0 && poolAt - retchAt >= 0.85f && acid != null && Math.abs(acid.x - px) < 1.5f && missed && rs.player.hp < hpB - 8 && Creatures.spits >= 1,
                String.format("retch %.2f s before the pool; pool at x %.1f (stood at %.1f); moved away: unhurt %s; 2 s standing in it took %.0f hp", poolAt - retchAt, acid == null ? 0 : acid.x, px, missed, hpB - rs.player.hp));

        // ---- Tank: picks anyone up in one hand and throws them fifteen feet
        Raid rt = SelfTests.stage(new Profile());
        rt.player.x = 300; rt.player.z = 548;                      // in the bubble, behind it and out of its sight
        Actor tank = spawn(rt, "tank", 300, 500, 0);
        Actor victim = spawn(rt, "survivors", 300, 497.6f, (float) Math.PI);
        victim.hostileToPlayer = false; victim.hp = victim.maxHp = 400;
        int g0 = Creatures.grabs, th0 = Creatures.thrown;
        float lifted = 0, grabAt = -1, warnAt = -1, thrownAt = -1, fromX = 0, fromZ = 0;
        for (int f = 0; f < 60 * 10 && thrownAt < 0; f++) {
            rt.update(1 / 60f);
            if (tank.grabId != victim.id && Creatures.thrown == th0) { victim.x = 300; victim.z = 497.6f; victim.vx = victim.vz = 0; }     // it stands where it is until the hand has it
            if (warnAt < 0 && rt.telegraphing(tank, "grab")) warnAt = rt.time;
            if (grabAt < 0 && tank.grabId == victim.id) grabAt = rt.time;
            if (tank.grabId == victim.id) lifted = Math.max(lifted, victim.y - G);
            if (Creatures.thrown > th0) { thrownAt = rt.time; fromX = victim.x; fromZ = victim.z; }
        }
        run(rt, 3.5f);
        float flew = (float) Math.hypot(rt.landedAt[0] - fromX, rt.landedAt[1] - fromZ), rest = (float) Math.hypot(victim.x - fromX, victim.z - fromZ);
        t.check("the Tank takes hold of a person, lifts them off the ground in one hand and throws them fifteen feet (4.6 m) - telegraphed, and it does not kill outright",
                Creatures.grabs == g0 + 1 && warnAt > 0 && grabAt - warnAt >= 0.45f && lifted > 1.4f && thrownAt > grabAt && Math.abs(flew - Creatures.THROW_METRES) < 0.7f && rest < 8 && !victim.dead && rt.flings == 1,
                String.format("warning %.2f s before the hand closed, lifted %.1f m, held %.2f s, hit the ground %.2f m from the hand (15 ft = %.2f m) and tumbled to rest at %.1f m, alive %s", grabAt - warnAt, lifted, thrownAt - grabAt, flew, Creatures.THROW_METRES, rest, !victim.dead));
        // the player is thrown the same way: the same hand, the same distance
        Raid rp = SelfTests.stage(new Profile());
        Actor tank2 = spawn(rp, "tank", 300, 497.6f, (float) Math.PI);
        float pvx = 0, pvz = 0, pvy = 0, relY = 0;
        for (int f = 0; f < 60 * 10 && rp.flings == 0; f++) { rp.update(1 / 60f); relY = rp.player.y - G; }
        pvx = rp.player.vx; pvz = rp.player.vz; pvy = rp.player.vy;
        float g = absentialis.entity.Physics.GRAVITY, air = (pvy + (float) Math.sqrt(pvy * pvy + 2 * g * Math.max(0, relY))) / g, carry = (float) Math.hypot(pvx, pvz) * air;
        t.check("the player is thrown too: off the ground, no say in it until landing, and the arc comes down fifteen feet away",
                rp.flings == 1 && relY > 1.4f && rp.player.flingUntil > rp.time && Math.abs(carry - Creatures.THROW_METRES) < 0.6f && pvz > 0 && tank2.grabAt > rp.time,
                String.format("released %.1f m up at %.1f m/s forward, %.1f m/s up: %.2f s in the air, %.2f m carried", relY, Math.hypot(pvx, pvz), pvy, air, carry));

        // ---- Gladiator: blind, hunts by sound, armoured but for its back, takes heads - and the swing can be ducked
        Raid rg = SelfTests.stage(new Profile());
        Actor gl = spawn(rg, "gladiator", 300, 492, (float) Math.PI);     // face to face with the player, eight metres, broad daylight
        run(rg, 5);
        boolean blind = !"engage".equals(gl.state) && !"investigate".equals(gl.state) && gl.targetId == 0;
        float walk = (float) Math.hypot(gl.vx, gl.vz);
        rg.emitSound(330, G + 1, 492, 40, "glass", rg.player.id, false);
        rg.update(1 / 60f);
        boolean harked = rg.telegraphing(gl, "hark") && "investigate".equals(gl.state);
        float top = 0, arrived = -1;
        for (int f = 0; f < 60 * 9 && arrived < 0; f++) { rg.update(1 / 60f); top = Math.max(top, (float) Math.hypot(gl.vx, gl.vz)); if (gl.distXZ(330, 492) < 3.5f) arrived = rg.time; }
        t.check("the Gladiator cannot see: stood in front of it in daylight you are not there - until something makes a noise, and then it comes at a run",
                blind && walk < 1.2f && harked && arrived > 0 && top > 5.5f, String.format("ignored the player for 5 s (%s), walking at %.1f m/s; a breaking window 30 m off: there in %.1f s at up to %.1f m/s", gl.state, walk, arrived - 5, top));
        Raid ra = SelfTests.stage(new Profile());
        ra.freezeAI = true;
        Actor pl = spawn(ra, "gladiator", 300, 480, 0);           // facing -z
        float full = pl.hp;
        ra.damageActor(pl, at(300, 470), 100, Content.def("ammo.556"), "torso", pl.x, pl.y + 1.2f, pl.z);       // from the front
        float front = full - pl.hp;
        pl.hp = full;
        ra.damageActor(pl, at(300, 490), 100, Content.def("ammo.556"), "torso", pl.x, pl.y + 1.2f, pl.z);       // into its back
        float back = full - pl.hp;
        t.check("the Gladiator is armoured everywhere but the middle of its back: a round from the front does next to nothing, the same round from behind does nearly double",
                front > 0 && front < 12 && back > front * 12 && Creatures.deflected >= 1 && Creatures.weakHits >= 1, String.format("100 damage from the front took %.0f; into the back, %.0f", front, back));
        pl.hp = full;
        Actor stood = spawn(ra, "survivors", 299.4f, 478.4f, 0), ducking = spawn(ra, "survivors", 300.7f, 478.4f, 0), behind = spawn(ra, "survivors", 300, 482.2f, 0);
        ducking.crouch = true;
        int d0 = Creatures.ducked;
        Creatures.sweep(ra, pl, Creatures.CLAW_REACH, 0.15f, true, "claw_swing");
        t.check("one swing of the claws takes the head off anyone standing in front of it - and passes over anyone who is down low, or behind it",
                stood.dead && Gore.lost(stood, Gore.HEAD) && !ducking.dead && !behind.dead && Creatures.ducked == d0 + 1 && ra.decapitations == 1, "standing: " + (stood.dead ? "decapitated" : "alive") + "; crouched: " + (ducking.dead ? "dead" : "untouched") + "; behind it: " + (behind.dead ? "dead" : "untouched"));

        // ---- Butcher: rushes on sight, one swing of the saw, and a shot to the head during the wind-up stops it
        Raid rb = SelfTests.stage(new Profile());
        rb.player.x = 300; rb.player.z = 540;                      // inside the active bubble, behind it and out of its sight
        Actor bu = spawn(rb, "butcher", 300, 500, 0);
        Actor prey = spawn(rb, "survivors", 300, 478, (float) Math.PI);
        prey.hostileToPlayer = false; prey.weapon = null;           // unarmed, so the only thing that happens is the Butcher
        rb.freezeAI = false;
        float rush = 0, revAt = -1, cutAt = -1;
        for (int f = 0; f < 60 * 14 && cutAt < 0; f++) {
            rb.update(1 / 60f);
            prey.x = 300; prey.z = 478; prey.vx = prey.vz = 0;      // it stands its ground (this gate is about the Butcher)
            rush = Math.max(rush, (float) Math.hypot(bu.vx, bu.vz));
            if (revAt < 0 && rb.telegraphing(bu, "rev")) revAt = rb.time;
            if (prey.dead) cutAt = rb.time;
        }
        t.check("the Butcher runs at what it sees and takes the head off with one swing of the saw - after a wind-up you can hear",
                rush > 4.5f && revAt > 0 && cutAt - revAt >= Creatures.SAW_WINDUP - 0.05f && prey.dead && Gore.lost(prey, Gore.HEAD) && rb.sounds.stream().anyMatch(s -> s.category().equals("chainsaw")) && rb.sounds.stream().anyMatch(s -> s.category().equals("chainsaw_rev")),
                String.format("closed at %.1f m/s, wind-up %.2f s, victim %s", rush, cutAt - revAt, prey.dead ? "decapitated" : "alive (" + bu.state + ", " + Creatures.swings + " swings, " + String.format("%.1f", bu.dist(prey)) + " m off, vital " + prey.vital + ", hp " + prey.hp + ")"));
        Raid rc = SelfTests.stage(new Profile());
        rc.player.x = 300; rc.player.z = 540;
        Actor bu2 = spawn(rc, "butcher", 300, 481.5f, 0);
        Actor prey2 = spawn(rc, "survivors", 300, 479.6f, (float) Math.PI);
        prey2.hostileToPlayer = false; prey2.weapon = null;
        float rev2 = -1;
        int i0 = Creatures.interrupted;
        boolean shot = false;
        for (int f = 0; f < 60 * 8; f++) {
            rc.update(1 / 60f);
            prey2.x = 300; prey2.z = 479.6f; prey2.vx = prey2.vz = 0;
            if (rev2 < 0 && rc.telegraphing(bu2, "rev")) rev2 = rc.time;
            if (!shot && rev2 > 0 && rc.time - rev2 > 0.3f) { shot = true; rc.damageActor(bu2, at(300, 470), 30, Content.def("ammo.556"), "head", bu2.x, bu2.y + bu2.height() * 0.93f, bu2.z); }
            if (shot && rc.time - rev2 > 1.6f) break;
        }
        t.check("a well-timed shot stops it: a round to the Butcher's head while the saw is raised breaks the swing and staggers it",
                shot && Creatures.interrupted == i0 + 1 && !prey2.dead && !rc.telegraphing(bu2, "rev") && bu2.staggerUntil > rc.time && bu2.hp > 600,
                "interrupted " + (Creatures.interrupted - i0) + ", victim " + (prey2.dead ? "dead" : "alive") + String.format(", staggered for another %.1f s, Butcher at %.0f of 800 hp", bu2.staggerUntil - rc.time, bu2.hp));
    }
}
