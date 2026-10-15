package absentialis.tools;

import absentialis.entity.Actor;
import absentialis.gameplay.items.Content;
import absentialis.neuralai.api.Vec3;
import absentialis.neuralai.cognition.ConfidenceClass;
import absentialis.neuralai.cognition.TargetBelief;
import absentialis.neuralai.decision.CogState;
import absentialis.neuralai.group.SquadCoordinator;
import absentialis.neuralai.runtime.Mind;
import absentialis.neuralai.spatial.SearchGrid;
import absentialis.persistence.Profile;
import absentialis.persistence.Settings;
import absentialis.scene.Raid;
import absentialis.world.Block;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The NeuralAI acceptance suite (NeuralAI.md, section 15): the twelve encounters of its table, run against the real
 * raid on a bare stage, each judged on what the agents can be seen to do - where they went, what they believed and
 * how wrong they were - not on which subsystems exist. Every scenario prints the numbers it was judged by.
 *
 * The stage is the flat test world: asphalt to the horizon, daylight, nobody else. Walls are put up for a scenario
 * and taken down after it. The player (the target) is moved by the script; the guards are left entirely to the engine.
 */
final class NeuralTests {
    private NeuralTests() {}

    static final int G = World.GROUND;

    static void run(String which, boolean all, SelfTests04.Check t) {
        if (!(all || which.contains("v07") || which.contains("neural"))) return;
        System.out.println("[NeuralAI acceptance suite: the twelve encounters of NeuralAI.md section 15]");
        Content.load();
        SelfTests.guard(() -> glimpse(t));
        SelfTests.guard(() -> noiseBehindWall(t));
        SelfTests.guard(() -> fallenAlly(t));
        SelfTests.guard(() -> behindCover(t));
        SelfTests.guard(() -> threeRooms(t));
        SelfTests.guard(() -> roles(t));
        SelfTests.guard(() -> blockedFlank(t));
        SelfTests.guard(() -> meleeDodged(t));
        SelfTests.guard(() -> blockedMotion(t));
        SelfTests.guard(() -> radioOff(t));
        SelfTests.guard(() -> difficulty(t));
        SelfTests.guard(() -> replay(t));
    }

    // ------------------------------------------------------------------ staging
    private static final List<int[]> built = new ArrayList<>();

    private static void wall(World w, int x0, int z0, int x1, int z1, int h) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) for (int y = G; y < G + h; y++) { w.set(x, y, z, Block.WOOD_PLANKS); built.add(new int[]{x, y, z}); }
    }

    private static void clear(World w) { for (int[] c : built) w.set(c[0], c[1], c[2], Block.AIR); built.clear(); }

    private static Raid stage(int difficulty) {
        Settings s = new Settings();
        Profile dp = new Profile();
        dp.difficulty = difficulty;                      // 0.8: difficulty belongs to the profile
        Raid r = new Raid(SelfTests.flatWorld(), dp, s, new absentialis.engine.events.EventBus(), 0, 30);
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer);
        r.player.x = 300; r.player.z = 500; r.player.y = G;
        r.player.weapon = new absentialis.gameplay.items.ItemInstance("weapon.akr");
        r.god = true;
        return r;
    }

    private static Actor guard(Raid r, String faction, float x, float z, int squad, float faceX, float faceZ) {
        Actor a = r.debugSpawn(faction, x, z, G);
        a.squad = squad; a.hostileToPlayer = true; a.aggressor = true; a.homeX = x; a.homeZ = z; a.wakeUntil = 1e9f;
        a.yaw = (float) Math.atan2(-(faceX - x), -(faceZ - z));
        return a;
    }

    private static void run(Raid r, float seconds) { for (int i = 0; i < Math.round(seconds * 60); i++) r.update(1 / 60f); }
    private static Mind mind(Raid r, Actor a) { return r.neural.mind(a.id); }
    private static double err(TargetBelief b, Actor target) { return Math.hypot(b.estimatedPosition().x() - target.x, b.estimatedPosition().z() - target.z); }
    private static boolean inSolid(Raid r, Actor a) { return r.world.solid((int) Math.floor(a.x), (int) Math.floor(a.y + 0.2f), (int) Math.floor(a.z)) && r.world.solid((int) Math.floor(a.x), (int) Math.floor(a.y + 1.2f), (int) Math.floor(a.z)); }

    // ------------------------------------------------------------------ 1. one-second glimpse, then occlusion
    private static void glimpse(SelfTests04.Check t) {
        Raid r = stage(1);
        World w = r.world;
        wall(w, 286, 485, 310, 485, 4);
        Actor g = guard(r, "military", 300, 470, 71, 316, 490);
        r.player.x = 316; r.player.z = 490;                       // in the open, 26 m off, at the edge of the wall
        run(r, 1.0f);
        Mind m = mind(r, g);
        double meterAtGlimpse = m.meters.values().stream().mapToDouble(v -> v.progress()).max().orElse(0);
        CogState atGlimpse = m.state;
        r.player.x = 306; r.player.z = 488;                       // behind the wall
        run(r, 0.6f);
        // unseen, the target walks fourteen metres along the far side of the wall
        for (int i = 0; i < 60 * 7; i++) { r.player.x = Math.max(292, r.player.x - 2f / 60f); r.update(1 / 60f); }
        TargetBelief b = m.belief;
        double e = b.known() ? err(b, r.player) : -1;
        boolean notTracking = !b.visible() && (!b.known() || e > 5), remembers = b.known() || m.alert > 0.2 || m.state != CogState.PATROL;
        boolean nearGlimpse = !b.known() || Math.hypot(b.estimatedPosition().x() - 316, b.estimatedPosition().z() - 490) < Math.max(12, b.radiusMeters() + 6);
        t.check("NeuralAI 1 - one-second glimpse then occlusion: suspicion or a memory of where it was, never the live position",
                notTracking && remembers && nearGlimpse, String.format("after 1 s: %s, meter %.0f%%; 7 s after the wall: %s, belief %s radius %.1f m, %s m from where the target really is", atGlimpse, meterAtGlimpse * 100, m.state, b.kind(), b.radiusMeters(), b.known() ? String.format("%.1f", e) : "-"));
        clear(w);
    }

    // ------------------------------------------------------------------ 2. heard noise behind a wall
    private static void noiseBehindWall(SelfTests04.Check t) {
        Raid r = stage(1);
        World w = r.world;
        wall(w, 292, 478, 310, 478, 4);
        wall(w, 320, 505, 360, 505, 4);
        Actor g = guard(r, "military", 300, 472, 72, 300, 440);    // back to the wall
        r.player.x = 340; r.player.z = 530;                       // nowhere near the noise, and out of sight behind a wall of his own
        run(r, 1.0f);
        r.emitSound(302, G + 1, 483, 30, "glass_break", r.player.id, false);       // a window going, as loud as the game makes one
        float d0 = g.distXZ(302, 483);
        run(r, 1.0f);
        Mind m = mind(r, g);
        TargetBelief b = m.belief;
        boolean noSight = m.meters.values().stream().noneMatch(v -> v.confirmed()) && b.lastSeenTime() < 0 && !b.visible();
        double off = b.known() ? Math.hypot(b.estimatedPosition().x() - 302, b.estimatedPosition().z() - 483) : -1, fromPlayer = b.known() ? err(b, r.player) : -1;
        CogState heardAs = m.state;
        boolean investigated = false, fought = false;
        float nearest = d0;
        for (int f = 0; f < 60 * 20; f++) { r.update(1 / 60f); investigated |= m.state == CogState.INVESTIGATE; fought |= m.state == CogState.COMBAT; nearest = Math.min(nearest, g.distXZ(302, 483)); }
        t.check("NeuralAI 2 - a noise heard behind a wall: a direction and a region to investigate, not sight of whoever made it",
                b.known() && noSight && (heardAs == CogState.INVESTIGATE || heardAs == CogState.SUSPICIOUS) && investigated && !fought && off < 12 && off > 0.05 && b.radiusMeters() >= 1.5 && fromPlayer > 25 && nearest < d0 - 2.5,
                String.format("%s then %s; belief %s (%s) %.1f m from the noise with a %.1f m radius, %.0f m from the player who made it; walked round from %.0f m to %.1f m of it", heardAs, investigated ? "INVESTIGATE" : "nothing", b.kind(), b.source(), off, b.radiusMeters(), fromPlayer, d0, nearest) );
        clear(w);
    }

    // ------------------------------------------------------------------ 3. fallen ally discovered
    private static void fallenAlly(SelfTests04.Check t) {
        Raid r = stage(1);
        World w = r.world;
        wall(w, 270, 520, 330, 520, 4);                            // the shooter is behind this: nobody sees who fired
        Actor witness = guard(r, "military", 300, 470, 73, 300, 460), ally = guard(r, "military", 303, 462, 73, 300, 440);
        r.player.x = 300; r.player.z = 540;
        run(r, 1.0f);
        Mind m = mind(r, witness);
        boolean calm = m.state == CogState.PATROL && !m.belief.known();
        ally.hitDirX = 0; ally.hitDirZ = -1;
        r.damageActor(ally, r.player, 9999, Content.def("ammo.556"), "head", ally.x, ally.y + 1.6f, ally.z);
        run(r, 1.5f);
        TargetBelief b = m.belief;
        double e = b.known() ? err(b, r.player) : -1;
        t.check("NeuralAI 3 - a fallen ally: alarm, and a bounded guess at where it came from - not the attacker's position",
                calm && ally.dead && m.alert > 0.7 && m.state != CogState.PATROL && b.known() && !b.visible() && b.kind().ordinal() < ConfidenceClass.LAST_SEEN.ordinal() && b.radiusMeters() >= 3 && e > 12 && m.meters.values().stream().noneMatch(v -> v.confirmed()),
                String.format("alert %.2f, %s, belief %s (%s) radius %.0f m, %.0f m from the shooter (who is 70 m away behind a wall)", m.alert, m.state, b.kind(), b.source(), b.radiusMeters(), e));
        clear(w);
    }

    // ------------------------------------------------------------------ 4. target runs behind cover
    private static void behindCover(SelfTests04.Check t) {
        Raid r = stage(1);
        World w = r.world;
        wall(w, 304, 492, 316, 492, 4);
        wall(w, 304, 492, 304, 500, 4);
        Actor g = guard(r, "military", 300, 468, 74, 300, 490);
        r.player.x = 300; r.player.z = 490;
        run(r, 3.0f);
        Mind m = mind(r, g);
        CogState seen = m.state;
        boolean confirmed = m.belief.visible();
        r.player.x = 310; r.player.z = 496;                        // round the corner, out of sight
        run(r, 1.0f);
        double r1 = m.belief.radiusMeters();
        float d1 = g.distXZ(300, 490);
        run(r, 5.5f);
        double r2 = m.belief.radiusMeters();
        CogState after = m.state;
        TargetBelief b = m.belief;
        double lastSeenOff = Math.hypot(b.lastSeenPosition().x() - 300, b.lastSeenPosition().z() - 490);
        run(r, 4);
        double r3 = m.belief.known() ? m.belief.radiusMeters() : r2 + 1;
        t.check("NeuralAI 4 - the target runs behind cover: a search from where it was last seen, and uncertainty that grows with time",
                seen == CogState.COMBAT && confirmed && (after == CogState.SEARCH || after == CogState.COMBAT && !b.visible()) && lastSeenOff < 4 && r2 > r1 + 2 && r3 >= r2 && g.distXZ(300, 490) < d1 - 3,
                String.format("saw: %s; 6.5 s after losing it: %s, last seen %.1f m from the true last position, radius %.1f -> %.1f -> %.1f m, moved %.0f m toward it", seen, after, lastSeenOff, r1, r2, r3, d1 - g.distXZ(300, 490)));
        clear(w);
    }

    // ------------------------------------------------------------------ 5. two guards search three rooms
    private static void threeRooms(SelfTests04.Check t) {
        Raid r = stage(1);
        World w = r.world;
        // three open-fronted rooms in a row, their doors facing the guards
        for (int k = 0; k < 3; k++) { int x = 284 + k * 14; wall(w, x, 508, x + 9, 508, 4); wall(w, x, 500, x, 508, 4); wall(w, x + 9, 500, x + 9, 508, 4); wall(w, x, 500, x + 2, 500, 4); wall(w, x + 7, 500, x + 9, 500, 4); }
        Actor a = guard(r, "military", 296, 474, 75, 302, 494), b = guard(r, "military", 308, 474, 75, 302, 494);
        r.player.x = 302; r.player.z = 494;
        run(r, 3.0f);
        boolean both = mind(r, a).belief.known() && mind(r, b).belief.known();
        wall(w, 270, 532, 334, 532, 4);
        r.player.x = 302; r.player.z = 536; r.player.y = G;         // gone, out of sight behind a far wall: none of the rooms has anyone in it
        Set<Long> byA = new HashSet<>(), byB = new HashSet<>();
        float sameCell = 0, searching = 0;
        boolean clipped = false;
        for (int i = 0; i < 60 * 40; i++) {
            r.update(1 / 60f);
            Mind ma = mind(r, a), mb = mind(r, b);
            if (ma == null || mb == null) break;
            if (ma.searchRegion >= 0) byA.add(ma.searchRegion);
            if (mb.searchRegion >= 0) byB.add(mb.searchRegion);
            if (ma.state == CogState.SEARCH && mb.state == CogState.SEARCH) { searching += 1 / 60f; if (ma.searchRegion >= 0 && ma.searchRegion == mb.searchRegion) sameCell += 1 / 60f; }
            clipped |= inSolid(r, a) || inSolid(r, b);
        }
        SquadCoordinator sq = r.neural.squad(75);
        SearchGrid grid = sq == null ? null : sq.search;
        Set<Long> shared = new HashSet<>(byA); shared.retainAll(byB);
        Set<Long> union = new HashSet<>(byA); union.addAll(byB);
        int inspected = 0;
        if (grid != null) for (SearchGrid.Cell c : grid.cells()) if (c.lastInspected > -1e8) inspected++;
        t.check("NeuralAI 5 - two guards search three rooms: they divide the ground between them rather than walking it together, and nothing is checked forever",
                both && grid != null && union.size() >= 3 && !byA.isEmpty() && !byB.isEmpty() && shared.size() * 2 <= union.size() && inspected >= 3 && searching > 6 && sameCell < searching * 0.2f + 0.5f && !clipped,
                String.format("regions taken: %d and %d, %d in common of %d; %d regions inspected, %d reservation conflicts; both on the same region %.1f s of %.1f s searching", byA.size(), byB.size(), shared.size(), union.size(), inspected, grid == null ? 0 : grid.reservationConflicts, sameCell, searching));
        clear(w);
    }

    // ------------------------------------------------------------------ 6. squad sees an armed target
    private static void roles(SelfTests04.Check t) {
        Raid r = stage(1);
        World w = r.world;
        for (int k = 0; k < 6; k++) wall(w, 282 + k * 7, 482 - (k % 2) * 6, 284 + k * 7, 482 - (k % 2) * 6, 1);       // low walls to fight from
        List<Actor> squad = new ArrayList<>();
        for (int i = 0; i < 4; i++) squad.add(guard(r, "mercenaries", 291 + i * 6, 468, 76, 300, 500));
        run(r, 7.0f);
        Set<String> roles = new HashSet<>();
        List<Vec3> going = new ArrayList<>();
        int feasible = 0;
        StringBuilder dbg = new StringBuilder();
        for (Actor a : squad) {
            Mind m = mind(r, a);
            roles.add(m.role.name());
            dbg.append(String.format("[%s %s cover=%s dest=%s at %.1f,%.1f] ", m.role, m.action == null ? "-" : m.action.name(), m.cover == null ? "-" : String.format("%.1f,%.1f", m.cover.position().x(), m.cover.position().z()), m.destination == null ? "-" : String.format("%.1f,%.1f", m.destination.x(), m.destination.z()), a.x, a.z));
            Vec3 d = m.cover != null ? m.cover.position() : m.destination != null ? m.destination : new Vec3(a.x, a.y, a.z);
            going.add(d);
            int x = (int) Math.floor(d.x()), y = (int) Math.round(d.y()), z = (int) Math.floor(d.z());
            if (!w.solid(x, y, z) && !w.solid(x, y + 1, z)) feasible++;
        }
        double closest = 1e9;
        for (int i = 0; i < 4; i++) for (int j = i + 1; j < 4; j++) closest = Math.min(closest, going.get(i).distanceXZ(going.get(j)));
        t.check("NeuralAI 6 - a squad sees an armed target: its members are given different jobs and each goes somewhere a body can actually stand",
                roles.size() >= 3 && feasible == 4 && closest > 1.2, "roles " + roles + String.format("; %d of 4 destinations standable; the two closest destinations are %.1f m apart", feasible, closest) + " " + dbg);
        clear(w);
    }

    // ------------------------------------------------------------------ 7. flank path blocked
    private static void blockedFlank(SelfTests04.Check t) {
        Raid r = stage(1);
        World w = r.world;
        wall(w, 296, 486, 304, 486, 1);
        List<Actor> squad = new ArrayList<>();
        for (int i = 0; i < 4; i++) squad.add(guard(r, "mercenaries", 291 + i * 6, 466, 77, 300, 500));
        run(r, 3.0f);
        // the ways round are shut while they are being walked: a wall across the whole front, a storey high
        wall(w, 250, 478, 350, 478, 4);
        boolean clipped = false;
        float worstStall = 0;
        float[] stall = new float[4], lx = new float[4], lz = new float[4];
        for (int i = 0; i < 4; i++) { lx[i] = squad.get(i).x; lz[i] = squad.get(i).z; }
        int replans0 = 0, replans1 = 0;
        for (Actor a : squad) replans0 += mind(r, a).replans;
        for (int f = 0; f < 60 * 14; f++) {
            r.update(1 / 60f);
            for (int i = 0; i < 4; i++) {
                Actor a = squad.get(i);
                clipped |= inSolid(r, a);
                Mind m = mind(r, a);
                // "stalled": told to go somewhere, and neither moving nor doing anything else about it
                boolean pushing = m != null && m.motionStatus == absentialis.neuralai.api.SydneyBridge.ActionStatus.BLOCKED;
                stall[i] = pushing ? stall[i] + 1 / 60f : 0;
                worstStall = Math.max(worstStall, stall[i]);
            }
        }
        for (Actor a : squad) replans1 += mind(r, a).replans;
        float north = 0;
        for (Actor a : squad) north = Math.max(north, a.z);
        t.check("NeuralAI 7 - the flank route is shut: they reroute or give it up, and nobody walks through the wall",
                !clipped && north < 478.2f && worstStall < 4.5f && (replans1 > replans0 || worstStall < 1.6f),
                String.format("in a wall: %s; furthest anyone got: z %.1f (the wall is at 478); longest spell pushing at a blocked route %.1f s; replans %d", clipped, north, worstStall, replans1 - replans0));
        clear(w);
    }

    // ------------------------------------------------------------------ 8. melee attack dodged
    private static void meleeDodged(SelfTests04.Check t) {
        Raid r = stage(1);
        Actor g = guard(r, "raiders", 300, 497.7f, 78, 300, 500);   // at arm's length: a blow is quicker than a shot
        float hp0 = r.player.hp;
        int miss0 = r.neuralBody.misses, hit0 = r.neuralBody.hits, con0 = r.neuralBody.contacts;
        boolean dodged = false;
        float jump = 0, lx = g.x, lz = g.z, windupSeen = -1, openAt = -1;
        for (int f = 0; f < 60 * 8 && !(dodged && r.neuralBody.contacts > con0); f++) {
            r.update(1 / 60f);
            jump = Math.max(jump, (float) Math.hypot(g.x - lx, g.z - lz)); lx = g.x; lz = g.z;
            Mind m = mind(r, g);
            if (!dodged && m != null && m.meleeAction != 0) { if (windupSeen < 0) windupSeen = r.time; if (r.time - windupSeen > 0.12f) { r.player.x += 5; dodged = true; } }       // step out during the wind-up
            if (openAt < 0 && r.neuralBody.contacts > con0) openAt = r.time;
        }
        run(r, 0.5f);
        t.check("NeuralAI 8 - a melee attack is dodged: the contact window opens on nothing, the blow misses, and nobody is moved to make it land",
                dodged && r.neuralBody.contacts == con0 + 1 && r.neuralBody.misses == miss0 + 1 && r.neuralBody.hits == hit0 && r.player.hp == hp0 && jump < 0.35f && openAt - windupSeen > 0.15f,
                String.format("wind-up %.2f s before the window; contacts %d, hits %d, misses %d; player hp %.0f -> %.0f; largest single-frame move of the attacker %.2f m", openAt - windupSeen, r.neuralBody.contacts - con0, r.neuralBody.hits - hit0, r.neuralBody.misses - miss0, hp0, r.player.hp, jump) + " TRACE " + mind(r, g).trace.lines() + " " + r.neural.describe(g.id) + " d " + g.dist(r.player));
    }

    // ------------------------------------------------------------------ 9. Sydney reports blocked motion
    private static void blockedMotion(SelfTests04.Check t) {
        Raid r = stage(1);
        World w = r.world;
        Actor g = guard(r, "military", 300, 470, 79, 300, 440);
        r.player.x = 340; r.player.z = 530;
        wall(w, 320, 500, 360, 500, 4);                            // and the player is out of sight
        wall(w, 296, 466, 304, 466, 4); wall(w, 296, 474, 304, 474, 4); wall(w, 296, 466, 296, 474, 4); wall(w, 304, 466, 304, 474, 4);    // a pen with no way out
        run(r, 1.0f);
        r.emitSound(302, G + 1, 481, 30, "glass_break", r.player.id, false);
        Mind m = mind(r, g);
        int blocks0 = r.neuralBody.blocks;
        float longest = 0, cur = 0;
        boolean clipped = false, wanted = false;
        long intents0 = r.neural.motionIntents;
        String firstAction = null;
        Set<String> actions = new HashSet<>();
        for (int f = 0; f < 60 * 24; f++) {
            r.update(1 / 60f);
            wanted |= m.state != CogState.PATROL;
            if (m.action != null) { if (firstAction == null && m.state != CogState.PATROL) firstAction = m.action.name(); actions.add(m.action.name()); }
            cur = m.motionStatus == absentialis.neuralai.api.SydneyBridge.ActionStatus.BLOCKED && m.state != CogState.PATROL ? cur + 1 / 60f : 0;
            longest = Math.max(longest, cur);
            clipped |= inSolid(r, g) || Math.abs(g.x - 300) > 4 || Math.abs(g.z - 470) > 4;
        }
        t.check("NeuralAI 9 - the body reports a blocked route: the mind cancels or replans; it does not push at the same wall for ever",
                wanted && r.neuralBody.blocks > blocks0 && !clipped && longest < 5 && (m.replans > 0 || actions.size() > 1 || m.state == CogState.PATROL),
                String.format("wanted to go: %s; the body said BLOCKED %d times; longest continuous spell blocked %.1f s; replans %d; actions tried %s; ended %s; still inside the pen %s", wanted, r.neuralBody.blocks - blocks0, longest, m.replans, actions, m.state, !clipped) + " TRACE " + m.trace.lines());
        clear(w);
    }

    // ------------------------------------------------------------------ 10. squad radio disabled
    private static void radioOff(SelfTests04.Check t) {
        double[] heard = new double[2];
        String[] what = new String[2];
        for (int pass = 0; pass < 2; pass++) {
            Raid r = stage(1);
            World w = r.world;
            wall(w, 240, 482, 268, 482, 4); wall(w, 268, 436, 268, 482, 4);     // the far man is round a corner: he cannot see the fight
            Actor near = guard(r, "military", 300, 478, 80, 300, 500), far = guard(r, "military", 248, 452, 80, 248, 410);
            run(r, 0.2f);
            SquadCoordinator sq = r.neural.squad(80);
            if (pass == 1) sq.radioEnabled = false;
            // no shots, no shouting distance: fifty-eight metres and a wall
            near.weapon.rounds = 0; near.fireCooldown = 99;
            run(r, 6);
            Mind mf = mind(r, far), mn = mind(r, near);
            heard[pass] = mf.belief.known() ? mf.belief.confidence01() : 0;
            what[pass] = (mn.belief.visible() ? "near man sees the target" : "near man: " + mn.belief.kind()) + "; far man " + (mf.belief.known() ? "told (" + mf.belief.kind() + ", " + mf.belief.source() + ", radius " + String.format("%.0f", mf.belief.radiusMeters()) + " m)" : "knows nothing") + ", " + mf.state;
            clear(w);
        }
        t.check("NeuralAI 10 - radio off: what one man sees does not appear in the head of another who is out of sight and out of earshot",
                heard[0] > 0.2 && heard[1] == 0 && what[0].contains("sees the target") && what[1].contains("sees the target"), "radio on: " + what[0] + "  |  radio off: " + what[1]);
    }

    // ------------------------------------------------------------------ 11. difficulty increases
    private static void difficulty(SelfTests04.Check t) {
        double[] react = new double[3], hiddenErr = new double[3], radius = new double[3];
        for (int d = 0; d < 3; d += 2) {
            Raid r = stage(d);
            World w = r.world;
            wall(w, 286, 485, 310, 485, 4);
            Actor g = guard(r, "military", 300, 466, 81, 316, 490);
            r.player.x = 316; r.player.z = 490;
            react[d] = -1;
            for (int f = 0; f < 60 * 6 && react[d] < 0; f++) { r.update(1 / 60f); Mind m = mind(r, g); if (m != null && m.state == CogState.COMBAT) react[d] = r.time; }
            r.player.x = 306; r.player.z = 488;
            run(r, 0.6f);
            for (int i = 0; i < 60 * 6; i++) { r.player.x = Math.max(290, r.player.x - 2.4f / 60f); r.update(1 / 60f); }
            Mind m = mind(r, g);
            hiddenErr[d] = m.belief.known() ? err(m.belief, r.player) : 99;
            radius[d] = m.belief.radiusMeters();
            clear(w);
        }
        t.check("NeuralAI 11 - harder difficulty: quicker to react, and no better informed about what it cannot see",
                react[0] > 0 && react[2] > 0 && react[2] < react[0] - 0.05 && hiddenErr[2] > 5 && hiddenErr[0] > 5 && Math.abs(hiddenErr[2] - hiddenErr[0]) < Math.max(6, hiddenErr[0] * 0.6),
                String.format("time to recognise a target in the open: easy %.2f s, hard %.2f s; error about a target hidden for 6 s: easy %.1f m, hard %.1f m (radius %.0f / %.0f m)", react[0], react[2], hiddenErr[0], hiddenErr[2], radius[0], radius[2]));
    }

    // ------------------------------------------------------------------ 12. replayed identical seed
    private static void replay(SelfTests04.Check t) {
        String[] sig = new String[2];
        long[] decisions = new long[2];
        long[] point = Raid.replayPoint();
        for (int pass = 0; pass < 2; pass++) {
            Raid.replayFrom(point);
            Raid r = stage(1);
            World w = r.world;
            for (int k = 0; k < 6; k++) wall(w, 282 + k * 7, 482 - (k % 2) * 6, 284 + k * 7, 482 - (k % 2) * 6, 1);
            List<Actor> squad = new ArrayList<>();
            for (int i = 0; i < 4; i++) squad.add(guard(r, "mercenaries", 291 + i * 6, 468, 82, 300, 500));
            for (int f = 0; f < 60 * 10; f++) { if (f == 240) { r.player.x = 330; r.player.z = 530; } r.update(1 / 60f); }
            StringBuilder s = new StringBuilder();
            for (Actor a : squad) { Mind m = mind(r, a); s.append(m == null ? "gone" : Long.toHexString(m.trace.digest) + ":" + m.trace.count + ":" + m.state + ":" + m.role).append(String.format("@%.3f,%.3f ", a.x, a.z)); }
            sig[pass] = s.toString();
            decisions[pass] = r.neural.decisions;
            clear(w);
        }
        t.check("NeuralAI 12 - the same seed replayed: the same decisions, in the same order, ending in the same places", sig[0].equals(sig[1]) && decisions[0] == decisions[1] && decisions[0] > 40,
                decisions[0] + " decisions; " + (sig[0].equals(sig[1]) ? "identical: " + sig[0] : "DIFFERENT: " + sig[0] + " vs " + sig[1]));
    }
}
