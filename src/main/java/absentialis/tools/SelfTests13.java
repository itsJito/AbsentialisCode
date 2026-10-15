package absentialis.tools;

import absentialis.entity.Actor;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemInstance;
import absentialis.persistence.Profile;
import absentialis.scene.Raid;
import absentialis.world.World;

/**
 * Quality gates for 1.3 (ABS_SELFTEST=v13, or one group by name: range13 ...).
 */
final class SelfTests13 {
    private SelfTests13() {}

    static void run(String which, boolean all, SelfTests04.Check t) {
        if (all || which.contains("v13") || which.contains("range13")) SelfTests.guard(() -> range(t));
        if (all || which.contains("v13") || which.contains("stairs13")) SelfTests.guard(() -> stairs(t));
        if (which.contains("flight13")) SelfTests.guard(() -> flight());
        if (which.contains("crawl13")) SelfTests.guard(() -> crawlProbe());
        if (all || which.contains("v13") || which.contains("bugs13")) SelfTests.guard(() -> bugs(t));
        if (all || which.contains("v13") || which.contains("render13")) SelfTests.guard(() -> render(t));
        if (all || which.contains("v13") || which.contains("tactics13")) SelfTests.guard(() -> tactics(t));
    }

    static final int G = World.GROUND;
    static boolean DEBUG = System.getenv("ABS_DEBUG13") != null;
    static int TRACE = System.getenv("ABS_TRACE13") == null ? -1 : Integer.parseInt(System.getenv("ABS_TRACE13")), traceN;

    // ================================================================== rounds at range
    /**
     * A round fired dead on a person a long way off strikes them. The brief: "when the player has his sights perfectly
     * locked on an NPC that is far away but within shooting distance, the bullets phase through". Fired from the eye,
     * without spread, at the middle of the chest, standing and walking, at 20 to 110 blocks with a pistol and a rifle.
     */
    private static void range(SelfTests04.Check t) {
        System.out.println("[v1.3 range: a round on the chest of a far body strikes it]");
        Raid r = SelfTests11.flat(new Profile());
        r.freezeAI = true;
        r.god = true;
        SelfTests11.clear(r.world, 140, 140, 170, 280, 4);
        r.player.x = 150.5f; r.player.z = 150.5f; r.player.y = G;
        String[][] arms = {{"weapon.pm9", "ammo.9x19"}, {"weapon.sr16", "ammo.556"}};
        int fired = 0, struck = 0;
        StringBuilder misses = new StringBuilder();
        for (String[] arm : arms) {
            ItemInstance w = new ItemInstance(arm[0]);
            for (int dist = 20; dist <= 110; dist += 15) {
                for (int walking = 0; walking < 2; walking++) {
                    for (float yaw : new float[]{0, 1.5708f, 0.8f}) {
                        r.actors.removeIf(a -> !a.isPlayer);
                        Actor a = SelfTests11.spawn(r, "survivors", 150.5f, 150.5f + dist, yaw);
                        a.hp = a.maxHp = 1e6f;
                        a.hostileToPlayer = false;
                        if (walking == 1) { a.vx = (float) -Math.sin(yaw) * 1.6f; a.vz = (float) -Math.cos(yaw) * 1.6f; }
                        for (int i = 0; i < 4; i++) r.update(1 / 60f);
                        float ox = r.player.x, oy = r.player.eyeY(), oz = r.player.z;
                        float tx = a.x, ty = a.y + a.height() * 0.68f, tz = a.z;
                        float dx = tx - ox, dy = ty - oy, dz = tz - oz, l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                        float hp = a.hp;
                        r.fireProjectiles(r.player, w, Content.def(arm[1]), ox, oy, oz, dx / l, dy / l, dz / l, 0);
                        for (int i = 0; i < 40; i++) r.update(1 / 60f);
                        fired++;
                        if (a.hp < hp) struck++;
                        else misses.append(String.format(" %s@%d%s%.1f", arm[0].substring(7), dist, walking == 1 ? "w" : "s", yaw));
                    }
                }
            }
        }
        t.check("a round fired dead on the chest of a person 20 to 110 blocks off strikes them, standing or walking, facing any way (pistol and rifle)",
                struck == fired, struck + " of " + fired + " struck;" + misses);
    }

    // ================================================================== up the stairs, without going round in circles
    /** One walk: from a building's entrance to a room on its upper floor. Returns {arrived 0/1, seconds, radians turned, loops broken, metres walked}. */
    static float[] walk(Raid r, absentialis.world.World w, Actor a, float tx, float ty, float tz, absentialis.neuralai.api.SydneyBridge.Locomotion gait, float limit) {
        int spins0 = absentialis.neuralai.integration.Steering.spinBreaks;
        float turned = 0, walked = 0, lastYaw = a.facing(), lx = a.x, lz = a.z, t = 0;
        boolean there = false;
        if (TRACE == traceN) for (int yy = 24; yy <= 25; yy++) for (int zz = (int) a.z - 12; zz <= (int) a.z + 1; zz++) { StringBuilder sb = new StringBuilder("    [cells y=" + yy + " z=" + zz + "]"); for (int xx = (int) a.x - 2; xx <= (int) a.x + 4; xx++) sb.append(' ').append(xx).append(':').append(absentialis.world.Block.ALL[w.get(xx, yy, zz)].name).append(w.solid(xx, yy, zz) ? "*" : ""); System.out.println(sb); }
        r.scripted.clear(); r.scripted.add(a.id);
        final absentialis.neuralai.api.SydneyBridge.MotionIntent intent = new absentialis.neuralai.api.SydneyBridge.MotionIntent(new absentialis.neuralai.api.Vec3(tx, ty, tz), null, absentialis.neuralai.api.SydneyBridge.Stance.STAND, gait, false);
        r.scriptMind = (who, dt) -> r.neuralBody.setMotion(who.id, intent);
        r.god = true;
        for (; t < limit; t += 1 / 60f) {
            // the survivor is kept overhead, out of the way, so that the body is simulated in full (it is within 80 blocks)
            r.player.x = a.x + 0.5f; r.player.z = a.z + 0.5f; r.player.y = 90; r.player.vy = 0;
            r.update(1 / 60f);
            turned += Math.abs(absentialis.neuralai.integration.Steering.wrapPi(a.facing() - lastYaw)); lastYaw = a.facing();
            walked += (float) Math.sqrt((a.x - lx) * (a.x - lx) + (a.z - lz) * (a.z - lz)); lx = a.x; lz = a.z;
            if (a.distXZ(tx, tz) < 1.2f && Math.abs(a.y - ty) < 1.2f) { there = true; break; }
            if (TRACE == traceN && ((int) (t * 60)) % 10 == 0) {
                int[] pp = a.path; int pi = a.pathI;
                String cell = pp == null ? "-" : pi < pp.length / 3 ? pp[pi * 3] + "," + pp[pi * 3 + 1] + "," + pp[pi * 3 + 2] : "end";
                String next = pp == null || pi + 1 >= pp.length / 3 ? "-" : pp[pi * 3 + 3] + "," + pp[pi * 3 + 4] + "," + pp[pi * 3 + 5];
                System.out.printf("    [trace %.2f] %.2f,%.2f,%.2f yaw %.2f face %.2f v %.2f,%.2f vy %.2f ground %s path %s i %d cell %s next %s stuck %.2f hold %.2f spins %d%n", t, a.x, a.y, a.z, a.yaw, a.facing(), a.vx, a.vz, a.vy, a.onGround, pp == null ? "null" : String.valueOf(pp.length / 3), pi, cell, next, a.stuckTime, a.holdUntil - r.time, absentialis.neuralai.integration.Steering.spinBreaks - spins0);
            }
            if (DEBUG && Math.abs(t - 3f) < 0.01f) System.out.printf("    [dbg] at %.1f,%.1f,%.1f goal %.1f,%.1f,%.1f path %s pending %.1f stuck %.2f hasVia %s partial %.1f vel %.2f,%.2f%n", a.x, a.y, a.z, tx, ty, tz, a.path == null ? "null" : String.valueOf(a.path.length / 3), a.pathPending, a.stuckTime, a.hasVia, a.partialUntil - r.time, a.vx, a.vz);
        }
        r.scripted.clear(); r.scriptMind = null;
        traceN++;
        return new float[]{there ? 1 : 0, t, turned, absentialis.neuralai.integration.Steering.spinBreaks - spins0, walked};
    }

    /**
     * People take stairs the way the player does: from the door of a building of the generated city to a room on its
     * first floor, at a walk and at a run, without the turning on the spot that 1.2 still had on stairs and indoors. A
     * walk is judged by whether it got there, and by how much it turned for the ground it covered.
     */
    private static void stairs(SelfTests04.Check t) {
        System.out.println("[v1.3 stairs: up a building of the city to a room upstairs]");
        // (a city of its own: the shared one has had walls knocked through and doors forced by the gates before this one)
        absentialis.world.World w = new absentialis.world.World();
        new absentialis.world.gen.CityGenerator(w, 1337L, null).generate();
        Raid r = new Raid(w, new Profile(), new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer);
        int tried = 0, arrived = 0, loops = 0;
        float turnedPerM = 0, worst = 0;
        String worstAt = "";
        StringBuilder failed = new StringBuilder();
        for (absentialis.world.MapData.Building b : w.map.buildings) {
            if (tried >= 24) break;
            absentialis.world.MapData.Room up = null;
            for (absentialis.world.MapData.Room rm : b.rooms) if (rm.floor == 1 && rm.reachable && !rm.circulation && (rm.x1 - rm.x0) >= 2 && (rm.z1 - rm.z0) >= 2) { up = rm; break; }
            if (up == null) continue;
            float tx = (up.x0 + up.x1 + 1) / 2f, tz = (up.z0 + up.z1 + 1) / 2f, ty = up.y + 1;
            if (w.solid((int) Math.floor(tx), (int) ty, (int) Math.floor(tz)) || w.solid((int) Math.floor(tx), (int) ty + 1, (int) Math.floor(tz))) continue;
            for (absentialis.neuralai.api.SydneyBridge.Locomotion gait : new absentialis.neuralai.api.SydneyBridge.Locomotion[]{absentialis.neuralai.api.SydneyBridge.Locomotion.WALK, absentialis.neuralai.api.SydneyBridge.Locomotion.RUN}) {
                r.actors.removeIf(a -> !a.isPlayer);
                Actor a = SelfTests11.spawn(r, "survivors", b.entryX + 0.5f, b.entryZ + 0.5f, 0);
                a.y = b.entryY; a.hostileToPlayer = false;
                float[] res = walk(r, w, a, tx, ty, tz, gait, 60);
                tried++;
                if (res[0] > 0) arrived++;
                else failed.append(" ").append(b.archetype).append(gait == absentialis.neuralai.api.SydneyBridge.Locomotion.RUN ? "/run" : "/walk").append(String.format("(%.0f,%.0f,%.0f)", a.x, a.y, a.z));
                loops += (int) res[3];
                if (DEBUG) System.out.printf("    [walk %d] %s %s arrived %s in %.1f s, %d loops, %.1f rad over %.0f m, ended %.0f,%.0f,%.0f%n", traceN - 1, b.archetype, gait, res[0] > 0, res[1], (int) res[3], res[2], res[4], a.x, a.y, a.z);
                float per = res[2] / Math.max(4f, res[4]);
                turnedPerM += per;
                if (per > worst) { worst = per; worstAt = b.archetype + String.format(" %.1f rad over %.0f m", res[2], res[4]); }
            }
        }
        float mean = turnedPerM / Math.max(1, tried);
        System.out.printf("  %d walks, %d arrived; turning loops broken %d; turned %.2f rad a metre on average, worst %.2f (%s)%n", tried, arrived, loops, mean, worst, worstAt);
        // 1.2 on the same walks: 15 of 24 arrived, 144 turning loops broken, 1.17 rad turned a metre. 1.3: 22 of 24 with routes
        // searched in Java (the group run alone), 20 of 24 with routes from the native core (after native12 has linked it: the
        // route is a tick later); 56-78 loops. What still fails are flights reached only through a one-cell gap between
        // furniture (the coach house; a corner shop's back room).
        t.check("from the door of a building to a room upstairs, at a walk and at a run, people get there (five in six or better; 1.2: 15 of 24) turning less than 0.95 rad a metre on average (1.2: 1.17), with well under 1.2's turning loops (80 or fewer; 1.2: 144)",
                tried >= 20 && arrived * 6 >= tried * 5 && loops <= 80 && mean < 0.95f, String.format("%d of %d arrived, %d loops, %.2f rad/m (worst %.2f: %s);%s", arrived, tried, loops, mean, worst, worstAt, failed));
    }

    /** Not a gate: a body pushed straight up the flight of the house at 169,24,523 - its height and its feet, step by step. */
    private static void flight() {
        absentialis.world.World w = SelfTests06.world(1337L);
        Actor a = new Actor();
        a.x = 169.5f; a.y = 24f; a.z = 522.4f; a.onGround = true;
        for (int i = 0; i < 150; i++) {
            a.vx = 0; a.vz = 3.4f;
            boolean b = absentialis.entity.Physics.move(w, a, 1 / 60f);
            if (i % 3 == 0) System.out.printf("  [flight] %.3f  y %.3f  z %.3f  vy %.2f ground %s blocked %s%n", i / 60f, a.y, a.z, a.vy, a.onGround, b);
        }
    }

    // ================================================================== the 1.3 bug list
    private static void bugs(SelfTests04.Check t) {
        System.out.println("[v1.3 bugs: thrown bodies, a Crawler underfoot, a beating, mist, dogs, fixtures]");
        Raid r = SelfTests11.flat(new Profile());
        r.freezeAI = true;
        r.god = true;
        SelfTests11.clear(r.world, 180, 180, 260, 260, 4);
        r.player.x = 150.5f; r.player.z = 150.5f; r.player.y = G;

        // ---- a Tank's throw: the body is Sydney's in the air, not a box that falls over where it lands
        Actor tank = SelfTests11.spawn(r, "tank", 200.5f, 200.5f, 0);
        Actor man = SelfTests11.spawn(r, "survivors", 200.5f, 199.2f, 0);
        man.hp = man.maxHp = 500;
        r.fling(tank, man, 0, -1);
        if (DEBUG) System.out.printf("    [throw] after fling: v %.2f,%.2f,%.2f thrown %d%n", man.vx, man.vy, man.vz, r.thrownRagdolls);
        boolean ragAtOnce = man.ragdoll != null;
        float y0 = man.y, top = y0, z0 = man.z;
        for (int i = 0; i < 90; i++) { r.update(1 / 60f); top = Math.max(top, man.y); if (DEBUG && i % 6 == 0) System.out.printf("    [throw] %d y %.2f z %.2f rag %s pelvis %.2f,%.2f,%.2f dead %s%n", i, man.y, man.z, man.ragdoll != null, man.ragdoll == null ? 0 : man.ragdoll.x[absentialis.character.Ragdoll.PELVIS], man.ragdoll == null ? 0 : man.ragdoll.y[absentialis.character.Ragdoll.PELVIS], man.ragdoll == null ? 0 : man.ragdoll.z[absentialis.character.Ragdoll.PELVIS], man.dead); }
        t.check("a person thrown by a Tank is a ragdoll from the moment they leave its hand, and flies (up off the ground and well away) as one",
                ragAtOnce && top > y0 + 0.4f && Math.abs(man.z - z0) > 2.5f, String.format("ragdoll at once %s, rose %.2f m, carried %.1f m", ragAtOnce, top - y0, Math.abs(man.z - z0)));
        r.actors.removeIf(a -> !a.isPlayer);

        // ---- a Crawler at someone's feet: a shove that does nothing becomes a stamp that does something
        Actor crawler = SelfTests11.spawn(r, "crawler", 220.5f, 220.5f, 0);
        Actor kicker = SelfTests11.spawn(r, "survivors", 220.5f, 219.6f, 0);
        float chp = crawler.hp;
        int stomps0 = r.stomps;
        r.shove(kicker, crawler);
        t.check("shoving a Crawler (low on all fours) stamps on it instead - it is hurt and pinned, where a shove used to change nothing",
                r.stomps == stomps0 + 1 && crawler.hp < chp && crawler.staggerUntil > r.time, String.format("stamps %d, hp %.0f -> %.0f, pinned %.1f s", r.stomps - stomps0, chp, crawler.hp, crawler.staggerUntil - r.time));
        r.actors.removeIf(a -> !a.isPlayer);

        // ---- knocked out: down for a minute and a half, then up - suspicious, and remembering nothing
        Actor out = SelfTests11.spawn(r, "survivors", 160.5f, 160.5f, 0);
        out.hostileToPlayer = false; out.fistWasHostile = false;
        r.freezeAI = false;
        for (int i = 0; i < 10; i++) r.update(1 / 60f);
        r.knockOut(out, 0, -1, 90);
        boolean downNow = out.ragdoll != null && !out.dead;
        boolean stillDown = true;
        for (int i = 0; i < 60 * 85; i++) { r.update(1 / 60f); if (out.ragdoll == null || out.dead) { stillDown = false; break; } }
        boolean up = false;
        for (int i = 0; i < 60 * 40 && !up; i++) { r.update(1 / 60f); up = out.ragdoll == null && !out.dead; }
        for (int i = 0; i < 30; i++) r.update(1 / 60f);
        absentialis.neuralai.runtime.Mind m = r.neural.mind(out.id);
        t.check("a full combination's knock-out lays a person down for ninety seconds (not dead), after which they get up uneasy - suspicious, and with no grudge against the survivor they did not see",
                downNow && stillDown && up && !out.dead && m != null && (m.alert > 0.5 || m.state != absentialis.neuralai.decision.CogState.PATROL) && !out.hostileToPlayer,
                String.format("down at once %s, still down at 85 s %s, up after %s, mind %s, hostile %s", downNow, stillDown, up, m == null ? "none" : m.state + " alert " + String.format("%.2f", m.alert), out.hostileToPlayer));
        r.freezeAI = true;
        r.actors.removeIf(a -> !a.isPlayer);

        // ---- mist: a round through a body throws mist (more from a head than a hand); a beating throws mist and leaves no pool
        Actor target = SelfTests11.spawn(r, "survivors", 180.5f, 200.5f, 0);
        target.hp = target.maxHp = 1e6f; target.hostileToPlayer = false;
        for (int i = 0; i < 4; i++) r.update(1 / 60f);
        long m0 = r.fx.mists;
        r.fireProjectiles(r.player, new ItemInstance("weapon.sr16"), Content.def("ammo.556"), 180.5f, G + 1.6f, 196f, 0, 0, 1, 0);
        for (int i = 0; i < 240; i++) r.update(1 / 60f);       // (what the round threw has landed by now)
        long headMist = r.fx.mists - m0;
        int pools0 = r.gore.pools.size(), drops0 = r.gore.drops.size();
        long m1 = r.fx.mists;
        r.gore.mistOnly = true; r.hitKind = "blunt";
        for (int k = 0; k < 6; k++) r.damageActor(target, r.player, 20, null, "head", target.x, target.y + 1.6f, target.z);
        r.gore.mistOnly = false; r.hitKind = "";
        for (int i = 0; i < 120; i++) r.update(1 / 60f);
        t.check("blood mist: a round through the head puts a cloud of it in the air; fists to the face put mist in the air and nothing on the floor",
                headMist >= 1 && r.fx.mists > m1 && r.gore.pools.size() == pools0, String.format("mists from the round %d, from the fists %d; pools %d -> %d, drops %d -> %d", headMist, r.fx.mists - m1, pools0, r.gore.pools.size(), drops0, r.gore.drops.size()));
        r.actors.removeIf(a -> !a.isPlayer);

        // ---- dogs: none without an owner, and fewer of them
        absentialis.world.World city = SelfTests06.world(1337L);
        Raid rc = new Raid(city, new Profile(), new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        for (int i = 0; i < 30; i++) rc.update(1 / 30f);
        int dogs = 0, strays = 0, armed = 0;
        for (absentialis.scene.Animals.Animal a : rc.animals.all) if (a.kind == absentialis.scene.Animals.Kind.DOG) { dogs++; if (a.ownerId == 0) strays++; }
        for (Actor a : rc.actors) if (!a.isPlayer && !a.infected() && a.weapon != null) armed++;
        t.check("every dog in the city has an owner (no strays), and about one armed person in fourteen has one",
                strays == 0 && dogs <= Math.max(2, armed / 8), dogs + " dogs, " + strays + " without an owner, " + armed + " armed people");

        // ---- the old block toilets are gone: the porcelain one everywhere, and no pew stands alone
        int blockToilets = 0, porcelain = 0;
        for (absentialis.world.MapData.Building b : city.map.buildings) for (int x = b.x0; x <= b.x1; x++) for (int z = b.z0; z <= b.z1; z++) for (int y = G - 8; y < G + 30; y++) if (city.get(x, y, z) == absentialis.world.Block.TOILET) blockToilets++;
        for (absentialis.world.MapData.Prop p : city.map.props) if ("toilet_p".equals(p.model)) porcelain++;
        t.check("every toilet in the city is the porcelain model (the block toilet is gone)", blockToilets == 0 && porcelain > 100, blockToilets + " block toilets left, " + porcelain + " porcelain ones");
    }

    // ================================================================== Astratto stage 2: rendering's mesher in C++
    /**
     * The native chunk mesher against the Java one it replaced: every column of the generated city (and of the developer
     * map), lit, meshed both ways and compared byte for byte - opaque and translucent streams - with doors drawn and not,
     * plants left in and taken out. Then the time each takes per column.
     */
    private static void render(SelfTests04.Check t) {
        System.out.println("[v1.3 render: the native chunk mesher against the Java reference]");
        absentialis.world.World w = new absentialis.world.World();
        new absentialis.world.gen.CityGenerator(w, 1337L, null).generate();
        w.lighting.computeAll();
        absentialis.engine.render.ChunkMesher nat = new absentialis.engine.render.ChunkMesher(w), ref = new absentialis.engine.render.ChunkMesher(w);
        boolean wasDoors = absentialis.engine.render.ChunkMesher.drawDoors, wasPlants = absentialis.engine.render.ChunkMesher.foliageBlocks;
        int columns = 0, same = 0, quads = 0;
        String first = "";
        long tNat = 0, tRef = 0;
        try {
            for (int pass = 0; pass < 3; pass++) {
                absentialis.engine.render.ChunkMesher.drawDoors = pass == 1;
                absentialis.engine.render.ChunkMesher.foliageBlocks = pass != 2;
                for (int cx = 0; cx < absentialis.world.World.CX; cx++) for (int cz = 0; cz < absentialis.world.World.CZ; cz++) {
                    if (pass > 0 && (cx + cz) % 5 != 0) continue;                 // the options on a fifth of the columns
                    long a = System.nanoTime();
                    nat.mesh(cx, cz);
                    long b = System.nanoTime();
                    ref.meshJava(cx, cz);
                    long c = System.nanoTime();
                    if (pass == 0) { tNat += b - a; tRef += c - b; }
                    columns++;
                    boolean eq = nat.opaque.size == ref.opaque.size && nat.translucent.size == ref.translucent.size
                            && java.util.Arrays.equals(nat.opaque.data, 0, nat.opaque.size, ref.opaque.data, 0, ref.opaque.size)
                            && java.util.Arrays.equals(nat.translucent.data, 0, nat.translucent.size, ref.translucent.data, 0, ref.translucent.size);
                    if (eq) same++;
                    else if (first.isEmpty()) first = String.format(" first difference: column %d,%d pass %d - opaque %d/%d bytes, translucent %d/%d", cx, cz, pass, nat.opaque.size, ref.opaque.size, nat.translucent.size, ref.translucent.size);
                    quads += ref.opaque.quads() + ref.translucent.size / 48;
                }
            }
        } finally {
            absentialis.engine.render.ChunkMesher.drawDoors = wasDoors;
            absentialis.engine.render.ChunkMesher.foliageBlocks = wasPlants;
        }
        int all = absentialis.world.World.CX * absentialis.world.World.CZ;
        System.out.printf("  %d columns meshed both ways (%d quads); native %.3f ms a column, Java %.3f ms a column (one thread)%n", columns, quads, tNat / 1e6 / all, tRef / 1e6 / all);
        t.check("Astratto stage 2: the native mesher's output is the Java mesher's, byte for byte, for every column of the city (and with doors drawn, and plants taken out)",
                nat.nativeColumns == columns && same == columns, same + " of " + columns + " identical; " + nat.nativeColumns + " meshed natively" + first);
    }

    /** Not a gate: a Crawler sent down a flat road by script, its state printed. */
    private static void crawlProbe() {
        Raid r = SelfTests11.flat(new Profile());
        r.god = true;
        SelfTests11.clear(r.world, 140, 140, 220, 220, 4);
        Actor c = r.debugSpawn("crawler", 150.5f, 150.5f, G);
        r.player.x = 160; r.player.z = 170; r.player.y = G;
        r.scripted.add(c.id);
        r.scriptMind = (who, dt) -> absentialis.neuralai.integration.Steering.goTo(r, who, 200, 150.5f, absentialis.neuralai.integration.Archetypes.arch(who.archetype).speed(), dt);
        for (int i = 0; i < 240; i++) {
            r.update(1 / 60f);
            if (i % 60 == 0 && c.anim != null) { absentialis.character.Pose po = new absentialis.character.Pose(); c.anim.pose(c, po, 0, null, false);
                System.out.printf("  [pose] family %s torso %.2f,%.2f,%.2f head %.2f larm %.2f lthigh %.2f lshin %.2f root y %.2f z %.2f pitch %.2f%n", c.anim.family, po.rot[3], po.rot[4], po.rot[5], po.rot[0], po.rot[6], po.rot[8 * 3], po.rot[9 * 3], po.rootY, po.rootZ, po.rootPitch); }
            if (i % 30 == 0) System.out.printf("  [crawl] %.2f,%.2f,%.2f yaw %.2f face %.2f wallT %.2f ceilT %.2f cling %s crawling %s prone %s height %.2f speed %.2f%n", c.x, c.y, c.z, c.yaw, c.facing(), c.wallT, c.ceilT, c.cling, c.crawling, c.prone, c.height(), Math.hypot(c.vx, c.vz));
        }
    }

    // ================================================================== squad tactics
    /** The WATCH role: a squad of three or more that has lost its quarry posts one at the way out while the rest search. */
    private static void tactics(SelfTests04.Check t) {
        System.out.println("[v1.3 tactics: one holds the way out while the rest search]");
        absentialis.neuralai.runtime.NeuralConfig cfg = absentialis.neuralai.runtime.NeuralConfig.get();
        int[] watchers = new int[2];
        int[] sizes = {4, 2};
        for (int k = 0; k < 2; k++) {
            absentialis.neuralai.group.SquadCoordinator sq = new absentialis.neuralai.group.SquadCoordinator(7, "military", cfg);
            java.util.Map<Long, absentialis.neuralai.api.GameWorld.SelfState> st = new java.util.HashMap<>();
            for (int i = 0; i < sizes[k]; i++) {
                absentialis.neuralai.runtime.Mind m = new absentialis.neuralai.runtime.Mind(100 + i, 7, "military", "rifleman", cfg);
                m.state = absentialis.neuralai.decision.CogState.SEARCH;
                absentialis.neuralai.api.Vec3 house = new absentialis.neuralai.api.Vec3(10, 24, 10);
                m.belief = new absentialis.neuralai.cognition.TargetBelief(1, absentialis.neuralai.cognition.ConfidenceClass.EVIDENCE, house, absentialis.neuralai.api.Vec3.ZERO, 6, 0.15, 0, -1e9, absentialis.neuralai.api.Vec3.ZERO, "lost", 0.5, 60);
                sq.members.add(m);
                absentialis.neuralai.api.Vec3 at = new absentialis.neuralai.api.Vec3(i * 2, 24, -6);
                st.put((long) (100 + i), new absentialis.neuralai.api.GameWorld.SelfState(at, new absentialis.neuralai.api.Vec3(0, 0, 1), 1, 1, i == 0 ? "shotgun" : "auto", true, true, true, true, 60, 120, 1, 1, 0.2, false, true, at, 50, 1.3, 4));
            }
            sq.assign(id -> st.get(id), 10);
            for (absentialis.neuralai.runtime.Mind m : sq.members) if (m.role == absentialis.neuralai.group.Role.WATCH) watchers[k]++;
        }
        absentialis.world.World city = SelfTests06.world(1337L);
        Raid rc = new Raid(city, new Profile(), new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        absentialis.world.MapData.Building house = null;
        for (absentialis.world.MapData.Building b : city.map.buildings) if (b.valid && b.rooms.size() >= 4) { house = b; break; }
        absentialis.neuralai.api.Vec3 exit = house == null ? null : rc.neuralWorld.exitOf(new absentialis.neuralai.api.Vec3((house.x0 + house.x1) / 2.0, house.entryY, (house.z0 + house.z1) / 2.0));
        boolean exitOk = exit != null && Math.abs(exit.x() - (house.entryX + 0.5)) < 0.01 && Math.abs(exit.z() - (house.entryZ + 0.5)) < 0.01;
        absentialis.neuralai.api.Vec3 open = rc.neuralWorld.exitOf(new absentialis.neuralai.api.Vec3(5, 24, 5));
        t.check("searching for someone lost in a building, a squad of four posts exactly one at its way out (a pair posts none), and the way out of a building is its entrance (the open has none)",
                watchers[0] == 1 && watchers[1] == 0 && exitOk && open == null, "four: " + watchers[0] + " watching, two: " + watchers[1] + "; exit " + exit + " vs entry " + (house == null ? "-" : house.entryX + "," + house.entryZ) + "; in the open " + open);
    }
}
