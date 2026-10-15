package absentialis.neuralai.integration;

import static absentialis.neuralai.integration.Steering.goTo;
import static absentialis.neuralai.integration.Steering.stop;
import static absentialis.neuralai.integration.Steering.turn;

import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.neuralai.decision.CogState;
import absentialis.neuralai.integration.Archetypes.Arch;
import absentialis.neuralai.integration.Relations.Doc;
import absentialis.neuralai.integration.Relations.Doctrine;
import absentialis.neuralai.runtime.Mind;
import absentialis.scene.Raid;
import absentialis.world.MapData;

/**
 * What people do when they are not fighting (0.8). None of this decides anything about a threat - that is NeuralAI's
 * and nobody else's. These are the host's rules of conduct and of the working day, carried out when a mind chooses
 * them through the seam (GameWorld.conduct, GameWorld.routine):
 *
 *   conduct   a side's way of meeting a stranger before a shot is fired: bandits hold a lone traveller up and take
 *             payment, the cordon gives one warning inside a restricted area, a loner backs away from trouble, and
 *             bandits and contractors strip the bodies they have made.
 *   routine   the day: a post held, a patrol loop walked, a perimeter watched, containers worked through, a bunk
 *             gone back to when the shift ends.
 *
 * The regional tier (people in the loaded district but outside the engagement) is here too: coarse travel toward
 * a goal, with no senses and no fight - a mind out there is dormant until its body comes near.
 */
public final class Routines {
    private Routines() {}

    /** Seconds a bandit hold-up lasts, how close the crew stands, and the distances at which the mark counts as having resisted. */
    public static float EXTORT_SECONDS = 8, EXTORT_STANDOFF = 9, EXTORT_TOO_CLOSE = 3.2f, EXTORT_RAN = 34;
    /** Seconds the cordon gives a trespasser. */
    public static float WARN_SECONDS = 7;
    /** How far a loner backs off, for how long at most, and when it goes to ground. */
    public static float AVOID_METRES = 22, AVOID_SECONDS = 9, AVOID_CROUCH_AFTER = 4;

    static void setState(Actor a, String s) { Creatures.setState(a, s); }

    // ================================================================== conduct
    /**
     * The side's own rules get first word on a fresh contact, before the mind's choice of action is asked: a bandit
     * crew robs before it shoots, a loner backs away, a contractor calls it in. What this leaves behind is a state on
     * the body (extort, warn, avoid) that {@link #holds} then reports to the mind.
     */
    public static void meet(Raid r, Actor a, Mind m, Actor seen, float dt) {
        Doc dc = Relations.doc(a.faction);
        Raid.Squad sq = r.squad(a);
        a.morale = Math.min(dc.morale(), a.morale + dt * 0.012f);
        if (seen != null && !"extort".equals(a.state) && !"warn".equals(a.state)) firstContact(r, a, dc, sq, seen);
        // contractors know when a fight is lost: with two thirds of the team down the rest break contact, in order
        if (dc.d() == Doctrine.MERC && sq != null && sq.initial >= 2 && sq.alive <= Math.max(1, Math.round(sq.initial * 0.35f)) && m.state == CogState.COMBAT) {
            if (a.morale >= dc.breakAt()) r.say(a, "Falling back!", "shout");
            a.morale = Math.min(a.morale, dc.breakAt() - 0.05f);
        }
        // loners do not go and look at trouble they have only heard: they put distance between themselves and it
        if (dc.d() == Doctrine.LONER && seen == null && m.belief.known() && !"avoid".equals(a.state) && a.hp >= a.maxHp * 0.95f && (sq == null || !sq.bloodied)
                && (m.state == CogState.INVESTIGATE || m.state == CogState.SUSPICIOUS) && m.belief.confidence01() > 0.3) {
            a.memX = (float) m.belief.estimatedPosition().x(); a.memY = (float) m.belief.estimatedPosition().y(); a.memZ = (float) m.belief.estimatedPosition().z(); a.memTime = r.time;
            setState(a, "avoid");
        }
        // the cordon enforces its restricted areas: the unauthorised get one warning
        if (r.playerIgnored() || !r.playerKind.isEmpty()) return;   // nobody stops a camera to talk, or an actor nobody is after, or one of the infected
        Actor p = r.player;
        if (dc.d() == Doctrine.MILITARY && m.state == CogState.PATROL && !"warn".equals(a.state) && !a.hostileToPlayer && !p.dead && r.restrictedFor(p.x, p.z) && a.dist(p) < 30
                && r.neural.perceives(a.id, p.id) && !(absentialis.sim.Agents.asleep(a.routine) && r.time > a.wakeUntil)) {
            if (!a.warned) { a.warned = true; a.warnTimer = WARN_SECONDS; r.say(a, "Restricted area! Turn back - you have seconds.", "shout"); }
            setState(a, "warn");
        }
        if (m.justKilled) { m.justKilled = false; if (canLoot(dc) && !a.looted) setState(a, "loot"); }
    }

    /** Does the side's conduct have this body's hands right now. Stripping a body waits for a quiet moment. */
    public static boolean holds(Actor a, boolean atPeace) {
        return switch (a.state) { case "extort", "warn", "avoid" -> true; case "loot" -> atPeace; default -> false; };
    }

    public static void conduct(Raid r, Actor a, Mind m, float dt) {
        Arch ar = Archetypes.arch(a.archetype);
        Doc dc = Relations.doc(a.faction);
        Raid.Squad sq = r.squad(a);
        switch (a.state) {
            case "extort" -> extort(r, a, ar, sq, dt);
            case "warn" -> warn(r, a, dt);
            case "avoid" -> {
                // loners put distance and a wall between themselves and trouble
                float ax = a.x - a.memX, az = a.z - a.memZ, l = (float) Math.sqrt(ax * ax + az * az);
                if (!a.hasGoal) { float k = l < 0.1f ? 0 : AVOID_METRES / l; a.goalX = a.x + ax * k * 0.6f + (a.homeX - a.x) * 0.4f; a.goalZ = a.z + az * k * 0.6f + (a.homeZ - a.z) * 0.4f; a.hasGoal = true; }
                run(r, a, a.goalX, a.goalZ, ar, true, dt);
                a.crouch = a.stateTime > AVOID_CROUCH_AFTER;
                if (a.stateTime > AVOID_SECONDS || a.distXZ(a.goalX, a.goalZ) < 2) {
                    a.crouch = false;
                    setState(a, "idle");
                    if (m != null) for (var bl : new java.util.ArrayList<>(m.beliefs.all())) if (!bl.visible()) m.beliefs.forget(bl.targetId());
                }
            }
            case "loot" -> loot(r, a, ar, dc, dt);
            default -> { }
        }
    }

    private static boolean canLoot(Doc dc) { return dc.d() == Doctrine.MERC || dc.d() == Doctrine.BANDIT; }

    /** Runs or sprints to a place under the host's own steering. */
    private static void run(Raid r, Actor a, float x, float z, Arch ar, boolean sprint, float dt) {
        a.sprinting = sprint && !a.crouch && a.distXZ(x, z) > 4;
        goTo(r, a, x, z, a.sprinting ? Math.min(SydneyAdapter.SPRINT_MAX, ar.runSpeed() * SydneyAdapter.SPRINT_MUL) : ar.runSpeed() * 0.9f, dt);
    }

    /**
     * First confirmed sight of a hostile: the side's doctrine, before the mind fights. Asked once per contact.
     */
    private static void firstContact(Raid r, Actor a, Doc dc, Raid.Squad sq, Actor tgt) {
        if (a.paidOff && tgt.isPlayer && !a.hostileToPlayer) return;
        if (sq != null) { sq.tx = tgt.x; sq.tz = tgt.z; sq.targetId = tgt.id; sq.contactTime = r.time; }
        if (a.contactHandled == tgt.id && r.time - a.contactAt < 25) return;
        boolean fresh = !NeuralHost.NEURAL.contains(a.state) || "investigate".equals(a.state);
        a.contactHandled = tgt.id; a.contactAt = r.time;
        if (!fresh) return;
        float d = a.dist(tgt);
        switch (dc.d()) {
            case BANDIT -> {
                // predators size the prey up: rob a lone, quiet target; back off from a strong one
                int mine = sq == null ? 1 : sq.alive;
                float theirs = r.strengthNear(tgt, a.faction);
                if (tgt.isPlayer && sq != null && !sq.extortDone && mine >= 2 && d > 5 && !r.playerAiming && r.time - r.lastPlayerShot > 6) {
                    sq.extortDone = true;
                    sq.extortUntil = r.time + EXTORT_SECONDS;
                    r.say(a, "Hands where we can see them! " + absentialis.Version.coins(Raid.EXTORT_PRICE) + " and you walk.", "shout");
                    for (Actor mm : r.squadMembers(a)) { setState(mm, "extort"); mm.targetId = tgt.id; mm.contactHandled = tgt.id; mm.contactAt = r.time; }
                    return;
                }
                if (mine < theirs * 0.8f && d > 9) { a.morale = Math.min(a.morale, dc.breakAt() - 0.08f); r.say(a, "Too many of them!", "shout"); return; }
                if (r.rng.chance(0.4)) r.say(a, r.rng.chance(0.5) ? "Fresh meat!" : "Get 'em!", "bark_taunt");
            }
            case LONER -> {
                // avoid the fight unless it is on top of us or they hurt one of ours
                if (d > 10 && a.hp >= a.maxHp * 0.95f && (sq == null || !sq.bloodied)) { a.memX = tgt.x; a.memY = tgt.y; a.memZ = tgt.z; a.memTime = r.time; setState(a, "avoid"); }
            }
            case MERC -> {
                if (sq != null && !sq.backupCalled) { sq.backupCalled = true; r.say(a, "Contact! Requesting support on my position.", "radio_chatter"); r.dispatch(a.faction, tgt.x, tgt.z, a.squad, "reinforce"); }
            }
            case MILITARY -> { }
        }
    }

    /** Bandit hold-up: weapons trained, no shots, until the player pays, resists or the clock runs out. */
    private static void extort(Raid r, Actor a, Arch ar, Raid.Squad sq, float dt) {
        Actor p = r.player;
        if (sq == null) { setState(a, "idle"); a.hostileToPlayer = true; return; }
        float d = a.dist(p);
        a.yaw = turn(a.yaw, (float) Math.atan2(-(p.x - a.x), -(p.z - a.z)), dt * 6);
        if (d > EXTORT_STANDOFF) goTo(r, a, p.x, p.z, ar.speed() * 1.2f, dt); else stop(a);
        if (sq.paid) { for (Actor mm : r.squadMembers(a)) { mm.hostileToPlayer = false; mm.paidOff = true; mm.awareness = 0; mm.targetId = 0; setState(mm, "idle"); } r.say(a, "Smart. Now get lost.", "shout"); return; }
        boolean resisted = r.playerAiming || r.time - r.lastPlayerShot < 1 || d < EXTORT_TOO_CLOSE || d > EXTORT_RAN;
        if (resisted || r.time > sq.extortUntil) {
            if (!sq.bloodied) r.say(a, resisted ? "Wrong move!" : "Time's up!", "shout");
            sq.bloodied = true;
            // the hold-up is over: they are hostile, and what each of them does about it is its own mind's business
            for (Actor mm : r.squadMembers(a)) { mm.hostileToPlayer = true; mm.targetId = p.id; setState(mm, "idle"); NeuralHost.pointOut(r, mm, p); }
        }
    }

    /** Military restricted-area enforcement: a spoken warning and a grace period before force. */
    private static void warn(Raid r, Actor a, float dt) {
        Actor p = r.player;
        a.yaw = turn(a.yaw, (float) Math.atan2(-(p.x - a.x), -(p.z - a.z)), dt * 6);
        stop(a);
        a.warnTimer -= dt;
        if (!r.restrictedFor(p.x, p.z)) { a.warned = false; r.say(a, "Keep moving.", "shout"); setState(a, "idle"); return; }
        if (a.warnTimer <= 0 || r.playerAiming && a.dist(p) < 25) {
            r.say(a, "Final warning ignored - engaging!", "shout");
            r.trespass(a);
            for (Actor mm : r.nearby(a.x, a.z, 40)) if (mm.faction.equals(a.faction)) { mm.hostileToPlayer = true; mm.targetId = p.id; setState(mm, "idle"); NeuralHost.pointOut(r, mm, p); }
        }
    }

    /** Walk to the nearest lootable corpse and strip it: mercenaries take the hardware, bandits take everything fast. */
    private static void loot(Raid r, Actor a, Arch ar, Doc dc, float dt) {
        Actor c = r.nearestCorpse(a.x, a.z, 22);
        if (c == null || c.corpse == null || c.corpse.items.isEmpty() || a.stateTime > 25) { a.looted = true; setState(a, "idle"); return; }
        if (a.distXZ(c.x, c.z) > 1.6f) { goTo(r, a, c.x, c.z, dc.d() == Doctrine.BANDIT ? ar.runSpeed() * 0.8f : ar.speed() * 1.3f, dt); a.lootTimer = 0; return; }
        stop(a);
        a.crouch = true;
        a.lootTimer += dt;
        if (a.lootTimer > (dc.d() == Doctrine.BANDIT ? 1.6f : 3.2f)) { r.npcLoot(a, c, dc.d() == Doctrine.MERC); a.looted = true; a.crouch = false; setState(a, "idle"); }
    }

    // ================================================================== the working day
    /** One frame of an ordinary day for someone with nothing to be alarmed about. */
    public static void day(Raid r, Actor a, float dt) {
        Arch ar = Archetypes.arch(a.archetype);
        Doc dc = Relations.doc(a.faction);
        Raid.Squad sq = r.squad(a);
        a.crouch = false;
        if (!a.hasOrder && schedule(r, a, ar, dc, dt)) return;
        if (a.hasOrder) {
            if (r.time > a.orderUntil) a.hasOrder = false;
            else if (a.distXZ(a.orderX, a.orderZ) > 5) { goTo(r, a, a.orderX, a.orderZ, ar.runSpeed() * 0.8f, dt); return; }
            else { a.hasOrder = false; r.neural.onDispatch(a.id, new absentialis.neuralai.api.Vec3(a.orderX, a.y, a.orderZ)); return; }
        }
        Actor p = r.player;
        switch (dc.d()) {
            case MILITARY -> {
                if ("hold".equals(a.role)) { hold(r, a, dt); return; }
                patrol(r, a, ar, sq, dt);
            }
            case MERC -> {
                // guard the asset: half the team is static, the rest walks a tight loop round it
                if (a.dist(p) < 18 && !a.hostileToPlayer && !p.dead && a.talkTimer <= 0 && r.neural.perceives(a.id, p.id)) { a.talkTimer = 20; r.say(a, "Private site. Keep your distance.", "shout"); }
                a.talkTimer -= dt;
                if ((a.id & 1) == 0) { hold(r, a, dt); return; }
                if (!a.hasGoal || a.distXZ(a.goalX, a.goalZ) < 1.6f) {
                    a.routeIndex = (a.routeIndex + 1) & 3;
                    float ang = a.routeIndex * 1.5708f + (a.squad & 3);
                    a.goalX = a.homeX + (float) Math.sin(ang) * 11; a.goalZ = a.homeZ + (float) Math.cos(ang) * 11; a.hasGoal = true; a.thinkTimer = r.rng.range(1.5f, 4f);
                }
                if (a.thinkTimer > 0) { a.thinkTimer -= dt; stop(a); return; }
                goTo(r, a, a.goalX, a.goalZ, ar.speed(), dt);
                if (a.stuckTime > 2.5f) a.hasGoal = false;
            }
            case BANDIT -> {
                // lurk on the route: crouch in wait most of the time, relocate now and then, never quiet for long
                a.talkTimer -= dt;
                if (a.talkTimer <= 0) { a.talkTimer = r.rng.range(14f, 34f); if (r.rng.chance(0.5)) r.emitSound(a.x, a.y + 1.5f, a.z, 22, "whistle", a.id, false); }
                if (a.thinkTimer > 0) { a.thinkTimer -= dt; stop(a); a.crouch = true; a.yaw += (float) Math.sin(r.time * 0.4 + a.id) * dt * 0.5f; return; }
                if (!a.hasGoal) {
                    MapData.Node n = r.world.map.nearestNode(a.homeX + (r.rng.nextFloat() - 0.5f) * 44, a.homeZ + (r.rng.nextFloat() - 0.5f) * 44);
                    a.goalX = n != null ? n.x + r.rng.range(-5f, 5f) : a.homeX; a.goalZ = n != null ? n.z + r.rng.range(-5f, 5f) : a.homeZ; a.hasGoal = true; a.stateTime = 0;
                }
                goTo(r, a, a.goalX, a.goalZ, ar.speed() * 0.9f, dt);
                if (a.distXZ(a.goalX, a.goalZ) < 2 || a.stateTime > 22) { a.hasGoal = false; a.thinkTimer = r.rng.range(8f, 22f); }
            }
            case LONER -> scavenge(r, a, ar, dt);
        }
    }

    /**
     * Daily routine: what the schedule says this agent should be doing at this hour. Returns true when the
     * routine handled the tick (walking to the bunk, resting, sleeping). Combat, alarms, wounds and loud
     * noise interrupt it - a woken agent stays up and alert for a while before going back.
     */
    private static boolean schedule(Raid r, Actor a, Arch ar, Doc dc, float dt) {
        if (a.agentId == 0) return false;
        String now = absentialis.sim.Agents.activity(a.faction, a.duty, a.nightShift, r.hour(), a.agentId);
        if (!now.equals(a.routine)) {
            boolean wasOff = absentialis.sim.Agents.offDuty(a.routine), isOff = absentialis.sim.Agents.offDuty(now);
            a.routine = now;
            a.hasGoal = false;
            if (wasOff != isOff && r.time - a.lastSay > 20 && a.dist(r.player) < 30) r.say(a, isOff ? "That's my shift. Going down." : "Relief's here. Go and sleep.", "shout");
        }
        boolean awake = r.time < a.wakeUntil || a.hp < a.maxHp * 0.5f;
        boolean off = absentialis.sim.Agents.offDuty(now) && !awake;
        // on duty in the dark: sentries and patrols carry a torch (it helps them see and it shows where they are)
        a.light = r.night() && !off && dc.d() != Doctrine.BANDIT && dc.d() != Doctrine.LONER;
        if (!off) { a.role = "guard".equals(a.duty) || r.night() && dc.d() == Doctrine.MILITARY && (a.id & 1) == 0 ? "hold" : a.role.equals("hold") && !"guard".equals(a.duty) ? "" : a.role; return false; }
        float d = a.distXZ(a.homeX, a.homeZ);
        if (d > ("sleep".equals(now) ? 2.5f : 5f)) { goTo(r, a, a.homeX, a.homeZ, ar.speed(), dt); return true; }
        stop(a);
        a.crouch = true;                                   // sitting by the fire, or down on the bedroll
        if ("rest".equals(now)) a.yaw += (float) Math.sin(r.time * 0.2f + a.id) * dt * 0.3f;
        a.hp = Math.min(a.maxHp, a.hp + dt * 0.4f);
        return true;
    }

    private static void hold(Raid r, Actor a, float dt) {
        if (a.distXZ(a.homeX, a.homeZ) > 2.5f) { goTo(r, a, a.homeX, a.homeZ, Archetypes.arch(a.archetype).speed(), dt); return; }
        stop(a);
        a.yaw += (float) Math.sin(r.time * 0.25f + a.id * 1.7f) * dt * 0.45f;   // slow scan of the arc
    }

    /** Fixed patrol loop on the street graph: same nodes, same order, single file behind the lead. */
    private static void patrol(Raid r, Actor a, Arch ar, Raid.Squad sq, float dt) {
        if (sq == null || sq.route.isEmpty()) { stroll(r, a, ar, dt); return; }
        if (a.routeIndex < 0) a.routeIndex = 0;
        float[] wp = sq.route.get(a.routeIndex % sq.route.size());
        int rank = r.squadRank(a);
        float gx = wp[0] + (rank % 2 == 0 ? -1.2f : 1.2f), gz = wp[1] + (rank % 2 == 0 ? -1.2f : 1.2f);
        if (a.thinkTimer > 0) { a.thinkTimer -= dt; stop(a); a.yaw += dt * 0.5f * ((a.id & 1) == 0 ? 1 : -1); return; }
        if (a.distXZ(gx, gz) < 2.4f) { a.routeIndex = (a.routeIndex + 1) % sq.route.size(); a.thinkTimer = 2.5f + rank * 0.6f; return; }
        goTo(r, a, gx, gz, ar.speed(), dt);
        if (a.stuckTime > 3.5f) { a.routeIndex = (a.routeIndex + 1) % sq.route.size(); a.stuckTime = 0; }
    }

    /** Loners work the nearby containers, one at a time, and take a knee when hurt. */
    private static void scavenge(Raid r, Actor a, Arch ar, float dt) {
        if (a.needsHelp && !a.helped) { stop(a); a.crouch = true; a.talkTimer -= dt; if (a.talkTimer <= 0 && a.dist(r.player) < 14) { a.talkTimer = 12; r.say(a, "Hey - you got a bandage? Please.", "shout"); } return; }
        if (a.thinkTimer > 0) { a.thinkTimer -= dt; stop(a); a.crouch = a.lootTimer > 0; if (a.thinkTimer <= 0 && a.lootTimer > 0) { a.lootTimer = 0; r.emitSound(a.x, a.y + 0.5f, a.z, 6, "search", a.id, false); } return; }
        if (!a.hasGoal) {
            MapData.LootSpot s = r.lootSpotNear(a.homeX, a.homeZ, 26, a.id + (int) (r.time / 20));
            if (s != null) { a.goalX = s.x + 0.5f; a.goalZ = s.z + 0.5f; a.lootTimer = 1; }
            else { a.goalX = a.homeX + (r.rng.nextFloat() - 0.5f) * 20; a.goalZ = a.homeZ + (r.rng.nextFloat() - 0.5f) * 20; a.lootTimer = 0; }
            a.hasGoal = true; a.stateTime = 0;
        }
        goTo(r, a, a.goalX, a.goalZ, ar.speed() * 0.85f, dt);
        if (a.distXZ(a.goalX, a.goalZ) < 2.2f || a.stateTime > 20 || a.stuckTime > 2.5f) { a.hasGoal = false; a.thinkTimer = a.lootTimer > 0 && a.distXZ(a.goalX, a.goalZ) < 3 ? 4f : r.rng.range(1f, 4f); if (a.distXZ(a.goalX, a.goalZ) >= 3) a.lootTimer = 0; }
    }

    /** Wander around the home post along the street graph. */
    private static void stroll(Raid r, Actor a, Arch ar, float dt) {
        if (!a.hasGoal || a.distXZ(a.goalX, a.goalZ) < 1.8f || a.stateTime > 25) {
            float rad = 18;
            MapData.Node n = r.world.map.nearestNode(a.homeX + (r.rng.nextFloat() - 0.5f) * rad * 2, a.homeZ + (r.rng.nextFloat() - 0.5f) * rad * 2);
            if (n != null && Math.abs(n.x - a.homeX) < 40 && Math.abs(n.z - a.homeZ) < 40 && r.rng.chance(0.6)) { a.goalX = n.x + 0.5f; a.goalZ = n.z + 0.5f; }
            else { a.goalX = a.homeX + (r.rng.nextFloat() - 0.5f) * rad; a.goalZ = a.homeZ + (r.rng.nextFloat() - 0.5f) * rad; }
            a.hasGoal = true;
            a.stateTime = 0;
            a.thinkTimer = r.rng.range(1f, 5f);
        }
        if (a.thinkTimer > 0) { a.thinkTimer -= dt; stop(a); a.yaw += (float) Math.sin(r.time * 0.3 + a.id) * dt * 0.4f; return; }
        goTo(r, a, a.goalX, a.goalZ, ar.speed() * 0.8f, dt);
    }

    // ================================================================== the regional tier
    /** Loaded district, outside the engagement: coarse travel toward goals. No senses, no fight - the mind is dormant. */
    public static void regional(Raid r, Actor a, Arch ar, float dt) {
        if (a.hasOrder && r.time < a.orderUntil) {
            if (a.distXZ(a.orderX, a.orderZ) > 6) { goTo(r, a, a.orderX, a.orderZ, ar.runSpeed() * 0.8f, dt); return; }
            a.hasOrder = false;
            a.memX = a.orderX; a.memY = a.y; a.memZ = a.orderZ; a.memTime = r.time;
            setState(a, "investigate");
        }
        if ("investigate".equals(a.state) && r.time - a.memTime < 40) {
            if (a.distXZ(a.memX, a.memZ) > 4) goTo(r, a, a.memX, a.memZ, ar.speed() * 1.2f, dt); else stop(a);
            return;
        }
        if (!"idle".equals(a.state) && a.stateTime > 20) setState(a, "idle");
        if (ar.infected()) Creatures.wander(r, a, ar, dt); else stroll(r, a, ar, dt);
    }
}
