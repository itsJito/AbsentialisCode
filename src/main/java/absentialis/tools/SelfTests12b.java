package absentialis.tools;

import absentialis.entity.Actor;
import absentialis.gameplay.items.Content;
import absentialis.persistence.Profile;
import absentialis.scene.Animals.Animal;
import absentialis.scene.Animals.Kind;
import absentialis.scene.CombatFeed;
import absentialis.scene.Doors;
import absentialis.scene.Raid;
import absentialis.world.Block;
import absentialis.world.gen.PropLib;
import absentialis.world.MapData;
import absentialis.world.World;

/**
 * Quality gates for 1.2, second file: the gameplay groups (ABS_SELFTEST=v12, or animals12 bugs12 combat12 ui12).
 */
final class SelfTests12b {
    private SelfTests12b() {}

    static void run(String which, boolean all, SelfTests04.Check t) {
        if (which.contains("probe12")) SelfTests.guard(SelfTests12b::probe);
        if (all || which.contains("v12") || which.contains("animals12")) SelfTests.guard(() -> animals(t));
        if (all || which.contains("v12") || which.contains("bugs12")) SelfTests.guard(() -> bugs(t));
        if (all || which.contains("v12") || which.contains("combat12")) SelfTests.guard(() -> combat(t));
    }

    static final int G = World.GROUND;

    private static Raid.Projectile round(Raid r, float x, float y, float z) {
        Raid.Projectile p = new Raid.Projectile();
        p.x = x; p.y = y; p.z = z; p.damage = 4; p.owner = r.player.id;
        return p;
    }

    // ================================================================== animals
    private static void animals(SelfTests04.Check t) {
        System.out.println("[v1.2 animals: what a round meets, what a marker says, how a crow flies, how the dead lie]");
        Raid r = SelfTests11.flat(new Profile());
        r.freezeAI = true;
        r.player.x = 150.5f; r.player.z = 150.5f; r.player.y = G;
        CombatFeed feed = new CombatFeed(r);
        feed.attach(r.bus);

        // ---- hit volumes: a dog, standing, facing north (-z). Rounds come in from its right, along x
        Animal dog = r.animals.spawn(Kind.DOG, 200.5f, G, 200.5f);
        dog.yaw = 0; dog.hp = 9999;
        boolean back = r.animals.shot(round(r, 203.5f, G + 0.72f, 200.5f), -1, 0, 0, 6);            // through the trunk
        boolean over = r.animals.shot(round(r, 203.5f, G + 1.08f, 200.7f), -1, 0, 0, 6);            // a hand's breadth over its back
        int heads0 = r.animals.headShots;
        boolean head = r.animals.shot(round(r, 203.5f, G + 0.82f, 199.8f), -1, 0, 0, 6);            // the head, out in front of the body
        boolean ahead = r.animals.shot(round(r, 203.5f, G + 0.8f, 199.2f), -1, 0, 0, 6);            // past its nose
        boolean legs = r.animals.shot(round(r, 203.5f, G + 0.2f, 200.3f), -1, 0, 0, 6);             // under the body, at the legs
        t.check("a dog is hit where a dog is: a round through its trunk, its head (which counts for more) or its legs strikes it; one a hand's breadth over its back, or past its nose, does not (it was one ball about its middle: the head was outside it and the air over its back inside)",
                back && !over && head && r.animals.headShots == heads0 + 1 && !ahead && legs, "trunk " + back + ", over the back " + over + ", head " + head + ", past the nose " + ahead + ", legs " + legs);

        // ---- turned a quarter turn, the same rounds meet a different animal
        dog.yaw = (float) (Math.PI / 2);                                                           // facing -x now: its length lies along x
        boolean alongIt = r.animals.shot(round(r, 200.5f, G + 0.72f, 203.5f), 0, 0, -1, 6);         // from the side, through the trunk
        boolean whereHeadWas = r.animals.shot(round(r, 203.5f, G + 0.82f, 199.8f), -1, 0, 0, 6);    // where the head used to be
        boolean headNow = r.animals.shot(round(r, 199.8f, G + 0.82f, 203.5f), 0, 0, -1, 6);         // where it is
        t.check("the boxes turn with the animal: after a quarter turn the round that took its head goes by, and one where the head now is takes it", alongIt && !whereHeadWas && headNow, "trunk " + alongIt + ", old head " + whereHeadWas + ", new head " + headNow);

        // ---- a spider is mostly legs, and they are there to be hit; a crow and a hen are small, and are still hit
        Animal sp = r.animals.spawn(Kind.SPIDER, 220.5f, G, 200.5f);
        sp.yaw = 0; sp.hp = 9999;
        float hp0 = sp.hp;
        boolean leg = r.animals.shot(round(r, 221.3f, G + 0.22f, 204.5f), 0, 0, -1, 8);             // well out to one side, low: legs only
        float legDamage = hp0 - sp.hp;
        hp0 = sp.hp;
        boolean body = r.animals.shot(round(r, 220.5f, G + 0.5f, 204.5f), 0, 0, -1, 8);             // up the middle: the abdomen
        float bodyDamage = hp0 - sp.hp;
        boolean wide = r.animals.shot(round(r, 222.0f, G + 0.3f, 204.5f), 0, 0, -1, 8);             // outside the reach of its legs
        Animal crow0 = r.animals.spawn(Kind.CROW, 230.5f, G, 200.5f), hen = r.animals.spawn(Kind.CHICKEN, 235.5f, G, 200.5f);
        crow0.yaw = 0; hen.yaw = 0; crow0.hp = 9999; hen.hp = 9999;
        boolean crowHit = r.animals.shot(round(r, 233.5f, G + 0.2f, 200.5f), -1, 0, 0, 6), henHit = r.animals.shot(round(r, 238.5f, G + 0.45f, 200.5f), -1, 0, 0, 6), henHead = r.animals.shot(round(r, 238.5f, G + 0.7f, 200.2f), -1, 0, 0, 6);
        t.check("a spider's legs are part of it (a round through them hurts it, less than one through its body) and its reach has an end; a crow and a hen are hit by a round that goes through them",
                leg && body && legDamage > 0 && legDamage < bodyDamage * 0.6f && !wide && crowHit && henHit && henHead, String.format("legs %s (%.1f), body %s (%.1f), wide %s, crow %s, hen %s, hen's head %s", leg, legDamage, body, bodyDamage, wide, crowHit, henHit, henHead));

        // ---- the marker: a hit on an animal is a hit, and a kill is a kill
        feed.markerLevel = -1; feed.markerTime = -99;
        int hits0 = feed.hits;
        dog.hp = 60;
        r.animals.shot(round(r, 200.5f, G + 0.72f, 203.5f), 0, 0, -1, 6);
        boolean marked = feed.hits == hits0 + 1 && feed.markerLevel == CombatFeed.HIT && feed.marker(r.time) > 0;
        Raid.Projectile kill = round(r, 200.5f, G + 0.72f, 203.5f);
        kill.damage = 500;
        r.animals.shot(kill, 0, 0, -1, 6);
        boolean killMark = dog.dead && feed.markerLevel == CombatFeed.KILL && feed.animalKills == 1;
        // somebody else's round raises nothing
        feed.markerLevel = -1;
        Raid.Projectile theirs = round(r, 233.5f, G + 0.2f, 200.5f);
        theirs.owner = 424242;
        r.animals.shot(theirs, -1, 0, 0, 6);
        t.check("shooting an animal raises the hit marker, and killing one raises the kill marker - for the survivor's rounds and nobody else's (it raised nothing: an animal was not an actor, and only a hit on an actor was ever announced)",
                marked && killMark && feed.markerLevel == -1, "hit marker " + marked + ", kill marker " + killMark + ", for another's round " + (feed.markerLevel != -1));

        // ---- the dead are Sydney's: no pose, no wag - a body that falls and comes to rest
        boolean rigged = dog.rag != null && dog.rag.n == 11;
        float top0 = dog.rag == null ? 0 : dog.rag.y[4];
        r.freezeAI = false;
        SelfTests11.run(r, 4);
        float top1 = dog.rag == null ? 0 : dog.rag.y[4], low = dog.rag == null ? 0 : dog.rag.lowestY();
        t.check("a dog that is killed becomes a body of the Sydney Engine's - eleven particles, where its shoulders, hips, head, paws and tail were - which falls and comes to rest on the ground; nothing in the drawing decides how it lies, and nothing in it wags",
                rigged && top0 > G + 0.8f && top1 < G + 0.62f && low > G - 0.05f && low < G + 0.2f && dog.rag.settled, String.format("back %.2f m up when it died, %.2f m after four seconds; lowest part %.2f m off the ground; at rest %s", top0 - G, top1 - G, low - G, dog.rag != null && dog.rag.settled));

        // ---- a crow flies: up, over what is in its way, and down onto somewhere - it does not run at a wall and climb it
        Raid rc = SelfTests11.flat(new Profile());
        World w = rc.world;
        SelfTests11.clear(w, 250, 380, 300, 420, 9);
        for (int z = 388; z <= 412; z++) for (int y = G; y < G + 5; y++) w.setRaw(272, y, z, Block.CONCRETE);     // a wall across the way it will go
        w.computeHeights();
        rc.animals.all.clear();
        Animal crow = rc.animals.spawn(Kind.CROW, 262.5f, G, 400.5f);
        rc.player.x = 250.5f; rc.player.z = 400.5f; rc.player.y = G;
        SelfTests11.run(rc, 1);
        boolean perched = !crow.flying && "perched".equals(crow.brain.state);
        rc.player.x = 260.0f;                                                                        // within three blocks, from the west: it goes east, at the wall
        float peak = 0, lowOverWall = 99, startX = crow.x;
        int inSolid = 0, climbing = 0, airborne = 0;
        boolean crossed = false, landed = false;
        for (int i = 0; i < 60 * 16 && !landed; i++) {
            float y0 = crow.y, x0 = crow.x, z0 = crow.z;
            rc.update(1 / 60f);
            if (crow.flying) {
                airborne++;
                peak = Math.max(peak, crow.y - G);
                if (w.solid((int) Math.floor(crow.x), (int) Math.floor(crow.y + 0.05f), (int) Math.floor(crow.z))) inSolid++;
                // going straight up the face of something: rising, not moving along, with a solid cell beside it at its own height
                boolean beside = w.solid((int) Math.floor(crow.x + 0.6f), (int) Math.floor(crow.y), (int) Math.floor(crow.z)) || w.solid((int) Math.floor(crow.x - 0.6f), (int) Math.floor(crow.y), (int) Math.floor(crow.z));
                if (beside && crow.y > y0 && Math.abs(crow.x - x0) + Math.abs(crow.z - z0) < 0.004f) climbing++;
                if (Math.abs(crow.x - 272.5f) < 0.6f && crow.z > 388 && crow.z < 413) lowOverWall = Math.min(lowOverWall, crow.y - (G + 5));
                if (crow.x > 273.2f) crossed = true;
            } else if (airborne > 20) landed = true;
        }
        boolean onSomething = w.solid((int) Math.floor(crow.x), (int) Math.floor(crow.y - 0.1f), (int) Math.floor(crow.z));
        t.check("a crow that is flushed takes off, climbs, holds a height that clears what is between it and where it is going, and comes down onto something - it is never inside a block, and never goes up the face of a wall a step at a time",
                perched && airborne > 60 && peak > 2.5f && inSolid == 0 && climbing < 6 && landed && onSomething && (!crossed || lowOverWall > 0.3f) && Math.abs(crow.x - startX) + Math.abs(crow.z - 400.5f) > 8,
                String.format("perched %s; %d frames in the air, %.1f m up at the highest; in a block %d frames, up a wall face %d; %s; landed %s on something %s, %.1f m from where it sat",
                        perched, airborne, peak, inSolid, climbing, crossed ? String.format("crossed the wall %.1f m over it", lowOverWall) : "did not have to cross the wall", landed, onSomething, Math.abs(crow.x - startX) + Math.abs(crow.z - 400.5f)));
        // killed on the wing, it comes down as a body
        rc.player.x = 250.5f;
        Animal high = rc.animals.spawn(Kind.CROW, 262.5f, G + 7, 395.5f);
        high.flying = true; high.fvx = 5;
        rc.animals.hurt(high, 50, rc.player.id, 1, 0);
        float hy0 = high.y;
        SelfTests11.run(rc, 4);
        t.check("a crow killed on the wing falls as a body, carrying on the way it was going, and lies where it lands", high.dead && high.rag != null && hy0 > G + 6 && high.y < G + 0.3f && high.x > 263.5f,
                String.format("from %.1f m up to %.2f m; carried %.1f m", hy0 - G, high.y - G, high.x - 262.5f));
        for (int z = 388; z <= 412; z++) for (int y = G; y < G + 5; y++) w.setRaw(272, y, z, Block.AIR);
        w.computeHeights();

        // ---- the plain spider wants what is alive: the dead walk across its ground untouched. And it does not strike in a blur
        Raid rs = SelfTests11.flat(new Profile());
        rs.animals.all.clear();
        rs.player.x = 290.5f; rs.player.z = 290.5f; rs.player.y = G;                                 // near enough for the spider to be awake, too far to be its prey
        Animal spider = rs.animals.spawn(Kind.SPIDER, 320.5f, G, 320.5f);
        Actor walker = SelfTests11.spawn(rs, "walker", 323.0f, 320.5f, 0);
        Actor tyrant = SelfTests11.spawn(rs, "tyrant", 320.5f, 323.5f, 0);
        rs.frozen.add(walker.id); rs.frozen.add(tyrant.id);
        float whp = walker.hp, thp = tyrant.hp;
        boolean rushed = false;
        for (int i = 0; i < 60 * 6; i++) { rs.update(1 / 60f); rushed |= "rush".equals(spider.brain.state); }
        boolean leftAlone = !rushed && walker.hp == whp && tyrant.hp == thp;
        Actor man = SelfTests11.spawn(rs, "survivors", 322.4f, 320.5f, 0);
        man.hostileToPlayer = false; man.hp = man.maxHp = 5000;
        rs.frozen.add(man.id);
        float mhp = man.hp, firstHurt = -1, reared = -1, t0 = rs.time;
        float maxWind = 0;
        for (int i = 0; i < 60 * 12; i++) {
            rs.update(1 / 60f);
            maxWind = Math.max(maxWind, spider.windUp);
            if (reared < 0 && spider.windUp > 0) reared = rs.time - t0;
            if (firstHurt < 0 && man.hp < mhp) firstHurt = rs.time - t0;
        }
        absentialis.neuralai.animal.AnimalBrain.Spider sb = (absentialis.neuralai.animal.AnimalBrain.Spider) spider.brain;
        t.check("a giant spider leaves the dead alone - a walker and a Tyrant stand on its ground for six seconds unbitten - and goes for a living man who steps onto it; its strike is reared for before it lands, and it does not strike again for two seconds and more",
                leftAlone && firstHurt > 0 && reared >= 0 && firstHurt - reared >= 0.4f && maxWind > 0.9f && sb.strikes >= 2 && sb.strikes <= 6,
                String.format("the dead: rushed %s, walker %.0f/%.0f, Tyrant %.0f/%.0f; the man: reared at %.2f s, struck at %.2f s, %d strikes in twelve seconds (%d missed)", rushed, walker.hp, whp, tyrant.hp, thp, reared, firstHurt, sb.strikes, sb.misses));

        // ---- run down
        Raid rv = SelfTests11.flat(new Profile());
        rv.animals.all.clear();
        Animal bird = rv.animals.spawn(Kind.CHICKEN, 400.5f, G, 400.5f), husky = rv.animals.spawn(Kind.DOG, 405.5f, G, 400.5f);
        rv.animals.runDown(bird, (9 - 1.2f) * 14f, rv.player.id, 1, 0, 9, 0, 9);
        rv.animals.runDown(husky, (2.4f - 1.2f) * 14f, rv.player.id, 1, 0, 2.4f, 0, 2.4f);
        float thrown = bird.rag == null ? 0 : bird.rag.vx(0);
        t.check("an animal a car meets at speed is killed and thrown, as a body with the car's speed; one it meets slowly is hurt and knocked aside, alive",
                bird.dead && bird.rag != null && thrown > 6 && !husky.dead && husky.hp < Kind.DOG.hp && husky.body.vx > 2 && rv.animals.runDowns == 2, String.format("hen dead %s, leaving at %.1f m/s; husky alive %s with %.0f of %.0f, shoved at %.1f m/s", bird.dead, thrown, !husky.dead, husky.hp, Kind.DOG.hp, husky.body.vx));
    }

    // ================================================================== the listed bugs
    private static void bugs(SelfTests04.Check t) {
        System.out.println("[v1.2 bugs: the station's front door, beds, fittings, the valve, glass, doors and bodies]");
        Content.load();
        // (a city of this gate's own: the one the other gates share has had walls knocked through it and doors forced by the time this runs)
        World city = new World();
        new absentialis.world.gen.CityGenerator(city, 1337L, null).generate();
        MapData.Building pd = null;
        for (MapData.Building b : city.map.buildings) if (b.archetype.equals("lm_police") && b.authored) pd = b;
        // ---- the police station can be walked into by its front door
        if (pd != null) {
            // from the street in front of the forecourt to the middle of the main hall, by somebody who opens doors and climbs nothing
            int fz = pd.front == 2 ? 1 : pd.front == 0 ? -1 : 0, fx = pd.front == 1 ? 1 : pd.front == 3 ? -1 : 0;
            float sx = pd.entryX + 0.5f + fx * 10, sz = pd.entryZ + 0.5f + fz * 10, gx = pd.entryX + 0.5f - fx * 7, gz = pd.entryZ + 0.5f - fz * 7;
            boolean was = city.nav.mantling;
            World.DoorOracle oracle = city.doors;
            city.doors = null; city.nav.mantling = false;
            int[] path = city.nav.path(sx, G, sz, gx, G, gz, 9000, true);
            city.nav.mantling = was; city.doors = oracle;
            boolean arrives = path != null && Math.abs(path[path.length - 3] + 0.5f - gx) < 1.5f && Math.abs(path[path.length - 1] + 0.5f - gz) < 1.5f;
            boolean throughFront = false;
            if (path != null) for (int i = 0; i + 2 < path.length; i += 3) if (Block.ALL[city.get(path[i], path[i + 1], path[i + 2])].is(Block.DOOR) && Math.abs(path[i] - pd.entryX) <= 2 && Math.abs(path[i + 2] - pd.entryZ) <= 3) throughFront = true;
            t.check("the police headquarters is entered by its entrance: from the street, through the forecourt gate, under the portico and in at the front doors to the main hall - a walk of under forty cells, with nothing to climb (the forecourt's wall ran across the front of the building, through the portico and across the doors)",
                    arrives && throughFront && path.length / 3 < 40 && pd.problems.isEmpty() && city.map.report != null && city.map.report.doorsBlocked == 0,
                    (path == null ? "no way in" : path.length / 3 + " cells, by the front doors " + throughFront) + "; the station's own faults " + pd.problems + "; doorways blocked in the whole city " + (city.map.report == null ? "?" : city.map.report.doorsBlocked));
        } else t.check("the police headquarters was built", false, "not found");

        // ---- beds: the head is at the wall
        String wrong = "";
        for (String bed : new String[]{"bed_single", "bed_double", "bed_four_poster", "bed_bunk", "bed_hospital_p", "bed_restraint", "bunk_cell", "bedroll"}) {
            PropLib.Def d = PropLib.get(bed);
            if (d == null) { wrong += bed + " (missing) "; continue; }
            // where its pillow is along the bed (each kind's own pillow colour)
            int pillow = switch (bed) { case "bed_restraint", "bedroll" -> 0xFF8A8672; case "bunk_cell" -> 0xFF9A9A8A; default -> 0xFFDAD8D0; };
            long near = 0, far = 0;
            for (int x = 0; x < d.model.sx; x++) for (int y = 2; y < d.model.sy; y++) for (int z = 0; z < d.model.sz; z++) if (d.model.get(x, y, z) == pillow) { if (z < d.model.sz / 2) near++; else far++; }
            if (near <= far) wrong += bed + " (" + near + " voxels of pillow at the wall end, " + far + " at the other) ";
        }
        t.check("every bed has its pillow at the end that goes against the wall (every one of them was drawn the other way round: the foot at the wall and the head out in the room)", wrong.isEmpty(), wrong.isEmpty() ? "8 kinds of bed" : wrong);

        // ---- fittings: nothing that hangs on a wall hangs on a door, across a doorway or in the air
        int onDoor = 0, noWall = 0, inWay = 0, wallProps = 0;
        String first = "";
        for (MapData.Prop p : city.map.props) {
            PropLib.Def d = PropLib.get(p.model);
            if (d == null || d.mount != PropLib.WALL || d.collide != PropLib.NONE) continue;
            wallProps++;
            int bx = p.rot == 1 ? 1 : p.rot == 3 ? -1 : 0, bz = p.rot == 0 ? -1 : p.rot == 2 ? 1 : 0;
            for (int x = p.x0; x <= p.x1; x++) for (int z = p.z0; z <= p.z1; z++) {
                int here = city.get(x, p.y, z), behind = city.get(x + bx, p.y, z + bz);
                boolean bad = false;
                if (here != 0 && Block.ALL[here].is(Block.DOOR) || behind != 0 && Block.ALL[behind].is(Block.DOOR)) { onDoor++; bad = true; }
                else if (behind == 0) { noWall++; bad = true; }
                else for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { int id = city.get(x + n[0], p.y, z + n[1]); if (id != 0 && Block.ALL[id].is(Block.DOOR) && !city.solid(x + n[0] * 2, p.y, z + n[1] * 2)) { inWay++; bad = true; break; } }
                if (bad && first.isEmpty()) first = " - first: " + p.model + " at " + x + "," + p.y + "," + z;
            }
        }
        boolean valve = false, floating = false;
        for (MapData.Prop p : city.map.props) { if (p.model.equals("valve_stand") && p.x0 == 556 && p.z0 == 287 && p.y == 24) valve = true; if (p.model.equals("valve_wheel") && p.x0 == 556 && p.z0 == 287) floating = true; }
        t.check("in the whole city nothing that hangs on a wall hangs on a door, in a doorway or on nothing (pictures, boards, sconces, signs: " + wallProps + " of them are left), and the valve by the pump at 556, 24, 287 stands on the floor on its own riser instead of hanging in the air",
                wallProps > 500 && onDoor == 0 && noWall == 0 && inWay == 0 && valve && !floating, onDoor + " on doors, " + noWall + " with no wall behind, " + inWay + " in a doorway" + first + "; the valve stands " + valve + ", the hanging one is gone " + !floating);

        // ---- two panes, one above the other
        Raid rg = SelfTests11.flat(new Profile());
        World w = rg.world;
        int gx = 430, gz = 430;
        SelfTests11.clear(w, gx - 4, gz - 4, gx + 4, gz + 4, 5);
        for (int x = gx - 2; x <= gx + 2; x++) for (int y = G; y <= G + 2; y++) w.setRaw(x, y, gz, x == gx && y <= G + 1 ? Block.GLASS : Block.CONCRETE);
        w.computeHeights();
        rg.damageBlock(gx, G + 1, gz, 50, rg.player.id);                                              // the upper pane first
        int made = rg.doors.slabs.size();
        // and, so that the gate does not depend on where its pieces happen to fall: three laid on the lower pane's top edge
        for (int k = 0; k < 3; k++) { Doors.Slab s = new Doors.Slab(); s.kind = Doors.SHARD; s.w = 0.2f; s.h = 0.2f; s.rest = true; s.pitch = 1.5708f; s.x = gx + 0.3f + k * 0.2f; s.y = G + 1.02f; s.z = gz + 0.5f; rg.doors.slabs.add(s); }
        SelfTests11.run(rg, 3);
        int onEdge = 0;
        for (Doors.Slab s : rg.doors.slabs) if (s.kind == Doors.SHARD && s.y > G + 0.9f) onEdge++;
        rg.damageBlock(gx, G, gz, 50, rg.player.id);                                                  // then the lower
        SelfTests11.run(rg, 3);
        int inAir = 0, lying = 0;
        for (Doors.Slab s : rg.doors.slabs) if (s.kind == Doors.SHARD) { if (s.y > G + 0.3f) inAir++; else lying++; }
        t.check("two panes of glass one above the other, the upper broken first: its pieces that came to rest on the lower pane fall when that is shot out from under them - none is left in the air",
                made > 0 && onEdge >= 3 && inAir == 0 && lying >= onEdge && rg.doors.woken >= 3, made + " pieces from the upper pane, " + onEdge + " at rest on the lower; after it went: " + inAir + " in the air, " + lying + " on the ground, " + rg.doors.woken + " woken");
        for (int x = gx - 2; x <= gx + 2; x++) for (int y = G; y <= G + 2; y++) w.setRaw(x, y, gz, Block.AIR);
        w.computeHeights();

        // ---- a door and a body: the leaf meets the limbs, not a point where the body's owner stood
        Raid rd = SelfTests.stage(new Profile());
        World wd = rd.world;
        SelfTests11.clear(wd, 440, 296, 452, 306, 5);
        for (int x = 441; x <= 451; x++) for (int y = G; y <= G + 3; y++) wd.setRaw(x, y, 300, x == 446 && y <= G + 1 ? Block.DOOR_WOOD : Block.CONCRETE);
        wd.computeHeights();
        rd.doors.scan();
        Doors.Door door = rd.doors.at(446, G, 300);
        Actor body = rd.debugSpawn("raiders", 446.5f, 302.4f, G);
        rd.freezeAI = true;
        rd.update(1 / 60f);
        rd.damageActor(body, null, 5000, null, "torso", body.x, body.y + 1.1f, body.z);
        SelfTests08run(rd, 4);
        boolean lies = body.dead && body.ragdoll != null;
        float pelvisOff = 0, limbMoved = 0, angle = 0;
        int swept0 = rd.doors.limbsSwept;
        String which = "";
        if (lies && door != null) {
            absentialis.character.Ragdoll rg2 = body.ragdoll;
            int pel = absentialis.character.Ragdoll.PELVIS;
            // the door is worked once with nothing in its way, to see which way it goes for somebody standing here, and shut again
            rg2.shift(0, 0, 8);
            rd.player.x = 446.5f; rd.player.z = 297.6f; rd.player.y = G;
            door.locked = false;
            rd.doors.use(door, rd.player);
            SelfTests08run(rd, 2.5f);
            float ang = door.leafAngle(0), lx = door.dirX(0) * (float) Math.cos(ang) - door.dirZ(0) * (float) Math.sin(ang), lz = door.dirX(0) * (float) Math.sin(ang) + door.dirZ(0) * (float) Math.cos(ang);
            rd.doors.use(door, rd.player);
            SelfTests08run(rd, 2.5f);
            boolean shut = Math.abs(door.angle) < 0.05f;
            // half way round its swing, six tenths of the way along the leaf: that is where the part of the body furthest from
            // its middle is laid - the rest of it lies away from the door, out of reach of the point-and-radius the old test used
            float mx = door.dirX(0) + lx, mz = door.dirZ(0) + lz, ml = Math.max(0.01f, (float) Math.hypot(mx, mz));
            float tx = door.pivotX(0) + mx / ml * 0.6f, tz = door.pivotZ(0) + mz / ml * 0.6f;
            int limb = absentialis.character.Ragdoll.HEAD_P;
            float furthest = 0;
            for (int i : new int[]{absentialis.character.Ragdoll.HEAD_P, absentialis.character.Ragdoll.LA, absentialis.character.Ragdoll.RA, absentialis.character.Ragdoll.LW, absentialis.character.Ragdoll.RW}) {
                float dd = (float) Math.hypot(rg2.x[i] - rg2.x[pel], rg2.z[i] - rg2.z[pel]);
                if (dd > furthest) { furthest = dd; limb = i; }
            }
            which = limb == absentialis.character.Ragdoll.HEAD_P ? "its head" : limb == absentialis.character.Ragdoll.LA || limb == absentialis.character.Ragdoll.RA ? "a foot" : "a hand";
            // turned about that part until its middle is as far from the door as it will go
            float bestOff = -1, bestA = 0;
            float px0 = rg2.x[limb], pz0 = rg2.z[limb];
            for (int k = 0; k < 16; k++) {
                float a = k * 0.3927f, c = (float) Math.cos(a), s = (float) Math.sin(a), rx = rg2.x[pel] - px0, rz = rg2.z[pel] - pz0;
                float off = (float) Math.hypot(tx + rx * c - rz * s - door.midX(), tz + rx * s + rz * c - (door.z + 0.5f));
                boolean clear = !wd.solid((int) Math.floor(tx + rx * c - rz * s), G, (int) Math.floor(tz + rx * s + rz * c));
                if (clear && off > bestOff) { bestOff = off; bestA = a; }
            }
            float c = (float) Math.cos(bestA), s = (float) Math.sin(bestA);
            for (int i = 0; i < absentialis.character.Ragdoll.N; i++) { float rx = rg2.x[i] - px0, rz = rg2.z[i] - pz0; rg2.x[i] = tx + rx * c - rz * s; rg2.z[i] = tz + rx * s + rz * c; }
            for (int i = 0; i < absentialis.character.Ragdoll.N; i++) rg2.kick(i, -rg2.vx(i), -rg2.vy(i), -rg2.vz(i));      // laid there, at rest
            body.x = rg2.x[pel]; body.z = rg2.z[pel];
            pelvisOff = (float) Math.hypot(rg2.x[pel] - door.midX(), rg2.z[pel] - (door.z + 0.5f));
            float fx0 = rg2.x[limb], fz0 = rg2.z[limb];
            if (shut) rd.doors.use(door, rd.player);
            SelfTests08run(rd, 2.5f);
            limbMoved = (float) Math.hypot(rg2.x[limb] - fx0, rg2.z[limb] - fz0);
            angle = Math.abs(door.angle);
        }
        t.check("a door opened onto a body lying with only one end of it in the swing meets that end: it is taken round with the leaf, and the door opens or is stopped by it (the body was a point at its middle with a standing man's reach, and the leaf went through everything else)",
                lies && door != null && rd.doors.limbsSwept > swept0 && limbMoved > 0.12f && pelvisOff > 0.45f && (angle > 1.0f || rd.doors.wedged > 0),
                String.format("body %s with %s in the swing, its middle %.2f m from the door; that part moved %.2f m; the door stands %.2f rad open; %d sweeps of a limb, %d stops against a wedged body", lies ? "down" : "not down", which, pelvisOff, limbMoved, angle, rd.doors.limbsSwept - swept0, rd.doors.wedged));
        for (int x = 441; x <= 451; x++) for (int y = G; y <= G + 3; y++) wd.setRaw(x, y, 300, Block.AIR);
        wd.computeHeights();
    }

    private static void SelfTests08run(Raid r, float seconds) { for (int i = 0; i < Math.round(seconds * 60); i++) r.update(1 / 60f); }

    // ================================================================== fighting
    private static void combat(SelfTests04.Check t) {
        System.out.println("[v1.2 fighting: the twelve-block rule, the guard, jewellery on the special infected]");
        Content.load();
        // ---- nobody on two legs makes the survivor out, or fires on them, from more than twelve blocks
        float was = Raid.HUMAN_ENGAGE_RANGE;
        Raid.HUMAN_ENGAGE_RANGE = 12f;
        try {
            Raid r = SelfTests11.flat(new Profile());
            r.god = true;
            Actor p = r.player;
            p.x = 300.5f; p.z = 300.5f; p.y = G;
            Actor guard = SelfTests11.spawn(r, "military", 300.5f, 320.5f, 0);          // twenty blocks off, facing the survivor
            guard.hostileToPlayer = true; guard.aggressor = true;
            SelfTests11.run(r, 0.5f);
            boolean seenFar = r.neural.perceives(guard.id, p.id);
            int refused0 = r.neural.engagementsRefused;
            var m = r.neural.mind(guard.id);
            if (m != null) r.neural.fire(m, new absentialis.neuralai.api.Vec3(p.x, p.y + 1, p.z), false, p.id);
            boolean refused = r.neural.engagementsRefused == refused0 + 1;
            guard.z = 309.5f; guard.yaw = 0;                                                  // nine blocks off
            SelfTests11.run(r, 0.5f);
            boolean seenNear = r.neural.perceives(guard.id, p.id);
            int refused1 = r.neural.engagementsRefused;
            if (m != null) r.neural.fire(m, new absentialis.neuralai.api.Vec3(p.x, p.y + 1, p.z), false, p.id);
            boolean firedNear = r.neural.engagementsRefused == refused1;
            // the dead are not held to it
            Actor walker = SelfTests11.spawn(r, "walker", 300.5f, 285.5f, (float) Math.PI);
            SelfTests11.run(r, 0.3f);
            boolean deadSee = r.neural.perceives(walker.id, p.id);
            t.check("somebody on two legs cannot make the survivor out from twenty blocks, and a shot at them from there is not fired; from nine blocks they are seen and fired on - and one of the dead at fifteen still sees them (the rule is for people)",
                    m != null && !seenFar && refused && seenNear && firedNear && deadSee, "at 20: seen " + seenFar + ", shot refused " + refused + "; at 9: seen " + seenNear + ", shot fired " + firedNear + "; a walker at 15 sees " + deadSee);
        } finally { Raid.HUMAN_ENGAGE_RANGE = was; }

        // ---- the guard
        float[] lost = new float[3];
        Raid rb = null;
        for (int k = 0; k < 3; k++) {
            rb = SelfTests11.flat(new Profile());                                             // a fresh survivor for each: one blow, nothing before it
            Actor me = rb.player, foe = SelfTests11.spawn(rb, "raiders", me.x + 1, me.z, 0);
            me.hp = me.maxHp = 1000;
            rb.playerBlocking = k > 0; rb.blockWithBlade = k == 2;
            rb.damageActor(me, foe, 100, null, "torso", me.x, me.y + 1.2f, me.z);
            lost[k] = 1000 - me.hp;
        }
        rb.playerBlocking = false;
        float bare = lost[1] / lost[0], blade = lost[2] / lost[0];
        t.check("with the guard up a blow takes 3% less from the survivor with empty hands and 5% less with a knife (the brief's figures), and nothing less without it",
                lost[0] > 0 && Math.abs(bare - 0.97f) < 0.004f && Math.abs(blade - 0.95f) < 0.004f && rb.blocksTaken == 1, String.format("lost %.2f open, %.2f behind fists (%.3f), %.2f behind a blade (%.3f)", lost[0], lost[1], bare, lost[2], blade));

        // ---- every special infected has jewellery on it, and what it is varies; so does the mutated spider
        Raid rj = SelfTests11.flat(new Profile());
        String[] kinds = {"bomber", "spewer", "tank", "gladiator", "butcher", "crawler", "tyrant"};
        java.util.Set<String> seen = new java.util.HashSet<>();
        int bodies = 0, without = 0, valuable = 0;
        for (int i = 0; i < 70; i++) {
            Actor s = SelfTests11.spawn(rj, kinds[i % kinds.length], 200.5f + (i % 10) * 3, 400.5f + (i / 10) * 3, 0);
            if (s == null) continue;
            rj.damageActor(s, rj.player, 1e6f, null, "torso", s.x, s.y + 1, s.z);
            if (!s.dead || s.corpse == null) continue;
            bodies++;
            int found = 0;
            for (absentialis.gameplay.items.ItemInstance it : s.corpse.items) for (Object[] j : Raid.JEWELLERY) if (j[0].equals(it.def().id)) { found++; seen.add(it.def().id); if (it.def().tier >= 2) valuable++; }
            if (found == 0) without++;
        }
        Animal brood = rj.animals.spawn(Kind.BROOD, 250.5f, G, 250.5f);
        boolean broodHas = false;
        for (String id : brood.carrying) for (Object[] j : Raid.JEWELLERY) if (j[0].equals(id)) broodHas = true;
        t.check("every special infected that dies has at least one piece of jewellery on it, drawn from the whole list - so now and then something valuable - and the mutated spider has one in it too",
                bodies >= 60 && without == 0 && seen.size() >= 5 && broodHas, bodies + " bodies, " + without + " without, " + seen.size() + " kinds of piece, " + valuable + " of grade 2 or better; the spider " + broodHas);
    }

    /** Not a gate: prints what is at a place in the generated city (ABS_SELFTEST=probe12, ABS_PROBE="x,y,z" or a building archetype). */
    static void slice(World w, int cx, int cy, int cz, int rx, int rz) {
        for (int y = cy + 3; y >= cy - 1; y--) {
            System.out.println("  y=" + y + "  (x " + (cx - rx) + ".." + (cx + rx) + " across, z " + (cz - rz) + ".." + (cz + rz) + " down)");
            for (int z = cz - rz; z <= cz + rz; z++) {
                StringBuilder sb = new StringBuilder("    ");
                for (int x = cx - rx; x <= cx + rx; x++) {
                    int id = w.get(x, y, z);
                    Block b = Block.ALL[id];
                    char ch = id == 0 ? '.' : b.is(Block.DOOR) ? (b.solid() ? 'D' : 'd') : Block.prop(id) ? 'p' : b.is(Block.STEP) ? 's' : b.mat == absentialis.world.Material.GLASS ? 'g' : b.solid() ? (b.shape != 0 ? 'f' : '#') : b.is(Block.CLIMB) ? 'H' : ',';
                    sb.append(x == cx && z == cz ? (ch == '.' ? '+' : Character.toUpperCase(ch)) : ch);
                }
                System.out.println(sb);
            }
        }
    }

    private static void probe() {
        World w = SelfTests06.world(1337L);
        String what = System.getenv("ABS_PROBE");
        if (what == null || what.isBlank()) what = "556,24,286";
        for (String q : what.split(";")) {
            q = q.trim();
            if (q.matches("-?\\d+,-?\\d+,-?\\d+(,\\d+)?")) {
                String[] s = q.split(",");
                int x = Integer.parseInt(s[0]), y = Integer.parseInt(s[1]), z = Integer.parseInt(s[2]), rad = s.length > 3 ? Integer.parseInt(s[3]) : 6;
                System.out.println("[probe] " + x + "," + y + "," + z + "  district " + w.district(x, z) + "  block " + Block.ALL[w.get(x, y, z)].name);
                for (MapData.Building b : w.map.buildings) if (x >= b.lotX0 && x < b.lotX1 && z >= b.lotZ0 && z < b.lotZ1) System.out.println("  in the lot of " + b.archetype + " '" + b.name + "' footprint " + b.x0 + "," + b.z0 + " .. " + b.x1 + "," + b.z1 + " entry " + b.entryX + "," + b.entryY + "," + b.entryZ);
                for (MapData.Prop p : w.map.props) if (Math.abs((p.x0 + p.x1) / 2 - x) <= rad && Math.abs((p.z0 + p.z1) / 2 - z) <= rad && Math.abs(p.y - y) <= 4) System.out.println("  prop " + p.model + " at " + p.x0 + "," + p.y + "," + p.z0 + (p.x1 != p.x0 || p.z1 != p.z0 ? " .. " + p.x1 + "," + p.z1 : "") + " rot " + p.rot + " lift " + p.lift);
                for (int yy = y + 3; yy >= y - 1; yy--) { StringBuilder sb = new StringBuilder("  names y=" + yy + ":"); for (int xx = x - 2; xx <= x + 2; xx++) sb.append(' ').append(Block.ALL[w.get(xx, yy, z)].name); System.out.println(sb); }
                slice(w, x, y, z, rad + 4, rad);
            } else {
                for (MapData.Building b : w.map.buildings) {
                    if (!b.archetype.equals(q)) continue;
                    System.out.println("[probe] " + b.archetype + " '" + b.name + "' footprint " + b.x0 + "," + b.z0 + " .. " + b.x1 + "," + b.z1 + " lot " + b.lotX0 + "," + b.lotZ0 + " .. " + b.lotX1 + "," + b.lotZ1 + " front " + b.front + " entry " + b.entryX + "," + b.entryY + "," + b.entryZ + " problems " + b.problems);
                    for (MapData.Doorway d : b.doors) {
                        boolean ax = !w.solid(d.x + 1, d.y, d.z) && !w.solid(d.x + 1, d.y + 1, d.z) && !w.solid(d.x - 1, d.y, d.z) && !w.solid(d.x - 1, d.y + 1, d.z), az = !w.solid(d.x, d.y, d.z + 1) && !w.solid(d.x, d.y + 1, d.z + 1) && !w.solid(d.x, d.y, d.z - 1) && !w.solid(d.x, d.y + 1, d.z - 1);
                        if (!ax && !az) System.out.println("  doorway " + d.x + "," + d.y + "," + d.z + " has no room on either side along either axis: " + Block.ALL[w.get(d.x, d.y, d.z)].name);
                    }
                    slice(w, b.entryX, b.entryY, b.entryZ, 12, 9);
                }
            }
        }
    }
}
