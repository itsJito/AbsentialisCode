package absentialis.neuralai.integration;

import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.entity.Vital;
import absentialis.neuralai.api.Vec3;
import absentialis.neuralai.cognition.ConfidenceClass;
import absentialis.neuralai.cognition.TargetBelief;
import absentialis.neuralai.decision.CogState;
import absentialis.neuralai.integration.Archetypes.Arch;
import absentialis.neuralai.integration.Relations.Doc;
import absentialis.neuralai.integration.Relations.Doctrine;
import absentialis.neuralai.runtime.Mind;
import absentialis.neuralai.runtime.NeuralAi;
import absentialis.scene.Raid;

/**
 * Where the raid hands its people and its infected to NeuralAI (0.8). Through 0.7 this was the old AI: one very
 * large class that sensed, remembered, decided, steered and spoke for everybody, with NeuralAI bolted to the side of
 * it for armed humans only. All of that is gone. Every character that is not the player has a NeuralAI mind - a
 * squadmate, a soldier, a loner, a walker, a Butcher - and this class is only the plumbing either side of it:
 *
 *   in    one call per character per frame ({@link #update}); the things that happen to a character that its mind
 *         should know about (it was hit, a round went past, an ally fell, it can smell bile)
 *   out   what the mind decided, written onto the actor where the rest of the game reads it ({@link #publish}):
 *         the state name the HUD, the music and the rivalry system look at, the remembered position the renderer
 *         pitches a weapon by, the awareness the overlay draws.
 *
 * Nothing here decides anything.
 */
public final class NeuralHost {
    private NeuralHost() {}

    /** Awareness as the rest of the game names it (the overlay, the rivalry system's idea of "caught unaware"). */
    public static final int UNAWARE = 0, SUSPICIOUS = 1, THREAT_AWARE = 2, ENGAGED = 3, SEARCHING = 4;
    public static final String[] LEVEL = {"unaware", "suspicious", "threat aware", "engaged", "searching"};
    /** The published states NeuralAI owns: when a mind lets go of one, the body's state goes back to idle. */
    static final java.util.Set<String> NEURAL = java.util.Set.of("engage", "search", "investigate", "retreat", "flee", "alert", "notice");

    /** Rounds that passed close enough to someone to be felt, and minds that faulted and were started again (gates). */
    public static int nearMisses, faults;

    private static Mind enroll(Raid r, Actor a) {
        NeuralAi ai = r.neural;
        Mind m = ai.enroll(a.id, a.infected() ? 0 : a.squad, a.faction, a.archetype, a.mate());
        // an order given before this mind existed is still the order
        if (a.mate() && a.sqOrder != null && !"follow".equals(a.sqOrder)) ai.order(a.id, a.sqOrder, new Vec3(a.sqX, a.y, a.sqZ));
        if (a.mate()) m.holdFire = r.holdFire(a.team);
        return m;
    }

    // ------------------------------------------------------------------ the frame
    /** One character, one frame: senses, then the mind's choice, then what it chose written where the game reads it. */
    public static void update(Raid r, Actor a, float dt) {
        if (a.dead) return;
        Arch ar = Archetypes.arch(a.archetype);
        a.stateTime += dt;
        a.fireCooldown -= dt;
        a.specialTimer -= dt;
        a.meleeWind = Math.max(0, a.meleeWind - dt);
        a.headLook += (a.headLookTarget - a.headLook) * Math.min(1, dt * (ar.infected() ? 2.6f : 5f));
        a.sprinting = false;
        if (a.tier == 1) {
            // out of the engagement the mind is dormant: what the body last knew simply gets vaguer until it is near enough to think again
            if (r.time - a.lastSeenTime > 0.5f) a.memErr = Math.min(14, a.memErr + dt * 0.7f);
            Routines.regional(r, a, ar, dt);
            return;
        }
        NeuralAi ai = r.neural;
        Mind m = ai.mind(a.id);
        if (m == null) m = enroll(r, a);
        // 1.3: come round after being knocked out: a mind that starts again from nothing, but on edge
        if (a.wokeUneasy) { a.wokeUneasy = false; m.enter(absentialis.neuralai.decision.CogState.SUSPICIOUS, r.time); m.alert = 1; a.alertness = 1; a.wakeUntil = r.time + 120; }
        boolean up = ai.sense(a.id, dt);
        if (a.infected()) {
            if (a.breachT > 0) { Breach.climb(r, a, dt); return; }
            if (!up) return;
            ai.act(a.id, dt);
            publishCreature(r, a, m);
            a.crouch = false;
            return;
        }
        TargetBelief b = m.belief;
        Actor seen = b.visible() && b.targetId() > 0 ? r.actor((int) b.targetId()) : null;
        if (a.vital != Vital.ACTIVE) { r.casualty.downedBrain(a, seen, dt); return; }
        if (!up) return;
        // a squad with someone down weighs a rescue before anything else gets this agent's tick
        Raid.Squad rsq = r.squad(a);
        if (rsq != null && rsq.wounded > 0 || a.mate() && r.player.downed()) {
            if (rsq != null) { r.casualty.assign(rsq, r.squadMembers(a), a.mate()); if (r.casualty.rescue(a, rsq, dt)) return; }
        }
        if ("medic".equals(a.tacRole)) a.tacRole = "";
        boolean mate = a.mate();
        if (mate) {
            a.hostileToPlayer = false; a.light = false;
            boolean follow = "follow".equals(a.sqOrder);
            a.homeX = follow ? r.player.x : a.sqX; a.homeZ = follow ? r.player.z : a.sqZ;
            if (r.time - a.lastSeenTime > 20) a.hp = Math.min(a.maxHp, a.hp + dt * 1.2f);   // patched up between fights
            m.justKilled = false;
        } else Routines.meet(r, a, m, seen, dt);
        ai.act(a.id, dt);
        publish(r, a, m, mate ? Relations.SQUAD : Relations.doc(a.faction), mate);
    }

    /**
     * What a person's mind has decided, written where the rest of the game looks. One way only - nothing here feeds
     * back into the engine.
     */
    private static void publish(Raid r, Actor a, Mind m, Doc dc, boolean mate) {
        String s = switch (m.state) {
            case COMBAT -> "engage"; case SEARCH -> "search"; case INVESTIGATE, SUSPICIOUS -> "investigate";
            case WITHDRAW -> mate || dc.d() == Doctrine.MILITARY || dc.d() == Doctrine.MERC ? "retreat" : "flee";
            default -> null;
        };
        boolean conduct = !mate && Routines.holds(a, m.state == CogState.PATROL);
        if (s != null && !conduct && !a.state.equals(s)) { a.state = s; a.stateTime = 0; }
        if (s == null && NEURAL.contains(a.state)) Creatures.setState(a, "idle");
        TargetBelief b = m.belief;
        if (b.known()) {
            a.memX = (float) b.estimatedPosition().x(); a.memY = (float) b.estimatedPosition().y(); a.memZ = (float) b.estimatedPosition().z();
            a.memTime = (float) b.lastEvidenceTime(); a.memErr = (float) b.radiusMeters(); a.memConfidence = (float) b.confidence01();
            a.memConfirmed = b.kind().ordinal() >= ConfidenceClass.LAST_SEEN.ordinal();
            if (b.targetId() > 0) a.targetId = (int) b.targetId();
            if (b.lastSeenTime() > -1e8) a.lastSeenTime = (float) b.lastSeenTime();
        } else if (m.state == CogState.PATROL) a.memConfidence = 0;
        a.awareLevel = switch (m.state) { case COMBAT -> ENGAGED; case SEARCH -> SEARCHING; case INVESTIGATE -> THREAT_AWARE; case SUSPICIOUS -> SUSPICIOUS; default -> m.alert > 0.5 ? THREAT_AWARE : UNAWARE; };
        boolean fight = m.state == CogState.COMBAT || m.state == CogState.SEARCH;
        a.tacRole = fight ? m.role.name().toLowerCase() : "";
        a.intent = m.action == null ? "" : m.action.name();
        a.suppression = (float) m.suppression; a.pinnedUntil = (float) m.pinnedUntil; a.alertness = (float) m.alert;
        float best = 0;
        for (var vm : m.meters.values()) best = Math.max(best, (float) vm.progress());
        a.awareness = best;
        if (m.state != CogState.PATROL) a.wakeUntil = Math.max(a.wakeUntil, r.time + 90);
        if (!mate) a.light = r.night() && dc.d() != Doctrine.BANDIT && m.state != CogState.COMBAT && m.state != CogState.WITHDRAW && (m.state != CogState.PATROL || a.light);
        // the cordon calls it in once a fight has gone on a while
        Raid.Squad sq = r.squad(a);
        if (!mate && dc.d() == Doctrine.MILITARY && sq != null && !sq.backupCalled && m.state == CogState.COMBAT && a.stateTime > 8) { sq.backupCalled = true; r.say(a, "Command, troops in contact - send the patrol.", "radio_chatter"); r.dispatch(a.faction, a.memX, a.memZ, a.squad, "reinforce"); }
        if (sq != null && fight) { sq.tx = a.memX; sq.tz = a.memZ; if (a.targetId != 0) sq.targetId = a.targetId; if (m.belief.visible()) sq.contactTime = r.time; }
    }

    /** The same for a creature: its body has already named its own state; what is added is what its mind knows. */
    private static void publishCreature(Raid r, Actor a, Mind m) {
        TargetBelief b = m.belief;
        a.alertness = (float) m.alert;
        float best = 0;
        for (var vm : m.meters.values()) best = Math.max(best, (float) vm.progress());
        a.awareness = best;
        a.memConfidence = b.known() ? (float) b.confidence01() : 0;
        a.memErr = b.known() ? (float) b.radiusMeters() : 0;
        a.memConfirmed = b.known() && b.kind().ordinal() >= ConfidenceClass.LAST_SEEN.ordinal();
        if (b.known() && b.targetId() > 0 && b.kind().ordinal() >= ConfidenceClass.LAST_SEEN.ordinal()) a.targetId = (int) b.targetId();
        a.awareLevel = switch (m.state) { case COMBAT -> ENGAGED; case SEARCH -> SEARCHING; case INVESTIGATE -> THREAT_AWARE; case SUSPICIOUS -> SUSPICIOUS; default -> UNAWARE; };
        a.intent = m.action == null ? "" : m.action.name();
    }

    // ------------------------------------------------------------------ things that happen to a character
    /**
     * This character has just been hit. If it can see who did it, its mind has a target. If it cannot, it knows only
     * that the blow came from over there. Nobody is told who shot them - and something with no eyes never sees.
     */
    public static void onShot(Raid r, Actor v, Actor attacker) {
        if (v.dead || attacker == null || attacker == v) return;
        v.wakeUntil = r.time + 120;
        v.alertness = 1;
        float d = v.dist(attacker);
        Arch ar = Archetypes.arch(v.archetype);
        float dx = attacker.x - v.x, dz = attacker.z - v.z, dl = Math.max(0.1f, (float) Math.sqrt(dx * dx + dz * dz));
        float cos = (dx * (float) -Math.sin(v.yaw) + dz * (float) -Math.cos(v.yaw)) / dl;
        boolean line = Physics.sight(r.world, v.x, v.eyeY(), v.z, attacker.x, attacker.eyeY() - 0.2f, attacker.z);
        boolean sees = !ar.blind() && (d < 9 && line || d < 3.5f || cos > 0.1f && d < Math.min(r.fogSight(), ar.sight() * 1.3f) && line);
        r.neural.onHit(v.id, attacker.id, new Vec3(dx / dl, 0, dz / dl), sees && Relations.hostile(r, v, attacker));
        if (!sees && !v.mate() && !v.infected()) say(r, v, r.squad(v), r.rng.chance(0.5) ? "I'm hit! Where's it coming from?!" : "Shooter - " + bearing(v.x, v.z, v.x + dx / dl * 10, v.z + dz / dl * 10) + " somewhere!", "bark_pain", 0.5f);
    }

    /** An ally in sight has been badly hurt: it shakes the squad and names the casualty. */
    public static void onAllyHurt(Raid r, Actor ally, Actor hurt, float severity, Actor attacker) {
        if (ally.dead || ally.infected()) return;
        Doc dc = Relations.doc(ally.faction);
        ally.morale -= severity * switch (dc.d()) { case BANDIT -> 0.22f; case LONER -> 0.18f; case MILITARY -> 0.07f; case MERC -> 0.05f; };
        ally.alertness = 1;
        // they did not see the shot, only the result: the direction it came from, not the shooter's position
        if (attacker != null && !attacker.dead && Relations.hostile(r, ally, attacker)) {
            float dx = attacker.x - hurt.x, dz = attacker.z - hurt.z, dl = (float) Math.sqrt(dx * dx + dz * dz);
            if (dl > 0.5f) r.neural.onAllyDown(ally.id, hurt.id, new Vec3(hurt.x, hurt.y, hurt.z), new Vec3(dx / dl, 0, dz / dl));
        }
        if (r.time - ally.lastSay > 4 && r.rng.chance(0.6)) r.say(ally, (hurt.name.isEmpty() ? "Man" : Relations.callsign(hurt)) + (severity > 0.6f ? " is down bad - cover him!" : " is hit!"), "bark_down");
    }

    public static void onAllyDown(Raid r, Actor ally, Actor dead) {
        if (ally.dead || ally.infected()) return;
        r.neural.onAllyDown(ally.id, dead.id, new Vec3(dead.x, dead.y, dead.z), new Vec3(-dead.hitDirX, 0, -dead.hitDirZ));
        Doc dc = Relations.doc(ally.faction);
        Raid.Squad asq = r.squad(ally);
        if (asq != null) {
            if (r.time - asq.lastCasualty > 0.2f || asq.casualties == 0) asq.casualties++;
            asq.lastCasualty = r.time;
            String who = dead.name.isEmpty() ? "Man" : Relations.callsign(dead);
            say(r, ally, asq, asq.casualties >= 2 ? who + "'s down too! Get to cover!" : r.rng.chance(0.5) ? who + "'s down!" : "They got " + who + "!", "bark_down", 0.8f);
        }
        ally.morale -= switch (dc.d()) { case BANDIT -> 0.38f; case LONER -> 0.30f; case MILITARY -> 0.12f; case MERC -> 0.08f; };
        // the cautious defend their own: whoever did it is an enemy now, if they are there to be seen
        if (dc.d() == Doctrine.LONER && dead.lastAttacker != 0) {
            Actor k = r.actor(dead.lastAttacker);
            if (k != null && !k.dead && ally.dist(k) < 40 && (k.isPlayer || k.mate())) ally.hostileToPlayer = true;
        }
    }

    /** A round has struck or passed close to this character: nerves, and - when nothing is known yet - a bearing. */
    public static void nearMiss(Raid r, Actor a, float x, float y, float z, int shooterId) {
        if (a.dead || a.isPlayer || a.infected() || a.tier != 0 || a.ragdoll != null) return;
        a.wakeUntil = r.time + 120;
        Actor sh = r.actor(shooterId);
        if (sh != null && (sh.isPlayer || sh.mate()) && !a.mate()) a.hostileToPlayer = true;
        Vec3 from = sh == null ? null : new Vec3(sh.x - a.x, 0, sh.z - a.z);
        r.neural.onNearMiss(a.id, new Vec3(x, y, z), from, shooterId);
        Mind m = r.neural.mind(a.id);
        if (m != null) { a.suppression = (float) m.suppression; a.pinnedUntil = (float) m.pinnedUntil; }     // published at once: the HUD and the animator read these
        nearMisses++;
    }

    /** One of the infected catches the bile on somebody: it knows where they are for as long as they wear it. The eyeless do not go by smell either. */
    public static void scent(Raid r, Actor a, Actor v) {
        if (a.dead || !a.infected() || a.ragdoll != null) return;
        if (r.neural.mind(a.id) == null) enroll(r, a);
        r.neural.onScent(a.id, v.id);
    }

    /** Someone is pointed out to a character that is looking straight at them (the end of a hold-up, a warning ignored). */
    static void pointOut(Raid r, Actor a, Actor target) {
        if (a.dead || r.neural.mind(a.id) == null) return;
        if (Physics.sight(r.world, a.x, a.eyeY(), a.z, target.x, target.eyeY() - 0.2f, target.z)) r.neural.onSighted(a.id, target.id);
    }

    // ------------------------------------------------------------------ words
    private static final String[] COMPASS = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};

    /** Compass bearing from one point to another (north is -z, east is +x), as a word the player can act on. */
    public static String bearing(float fx, float fz, float tx, float tz) {
        double a = Math.atan2(tx - fx, -(tz - fz));
        return COMPASS[(int) Math.floor((a + Math.PI * 2 + Math.PI / 8) / (Math.PI / 4)) % 8];
    }

    /** Says a line if the squad has not just spoken (so callouts do not pile on top of one another). */
    static boolean say(Raid r, Actor a, Raid.Squad sq, String text, String bark, float gap) {
        if (sq != null && r.time - sq.lastSpeech < gap) return false;
        if (r.time - a.lastSay < 2.5f) return false;
        if (sq != null) sq.lastSpeech = r.time;
        r.say(a, text, bark);
        return true;
    }

    /** What the character's published state means, for the overlay. */
    public static String describe(Actor a) { return LEVEL[Math.max(0, Math.min(4, a.awareLevel))] + (a.tacRole.isEmpty() ? "" : "/" + a.tacRole) + (a.suppression > 0.5f ? " PINNED" : ""); }
}
