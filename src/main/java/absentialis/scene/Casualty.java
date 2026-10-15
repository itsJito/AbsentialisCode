package absentialis.scene;

import absentialis.engine.events.Events;
import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.entity.Vital;
import absentialis.gameplay.CombatConfig;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.world.CoverMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Incapacitation, bleed-out and rescue (0.5, brief section 5): one state machine for every human character,
 * the player included.
 *
 *     ACTIVE -> DOWNED -> REVIVING -> ACTIVE
 *                  |          |
 *                  v          +--> DOWNED   (the rescue was interrupted)
 *                DEAD                         ACTIVE -> DEAD when the damage is simply fatal
 *
 * {@link #lethal} is the only place that decides between going down and dying, and {@link #set} the only place a
 * state changes, so every transition is announced once on the event bus and nothing can be downed twice. A downed
 * character stays a physical body in the world: it can crawl, fire a sidearm badly, call for help, be shot again,
 * be revived, or run out of blood. The infected never enter the machine at all.
 *
 * The second half of this class is the rescue decision (brief 5.5): whether anyone in a squad should go, who,
 * and when to give it up.
 */
public final class Casualty {
    private final Raid raid;
    private final CombatConfig cfg = CombatConfig.get();
    /** Counters for the gates and the debrief. */
    public int downs, revives, bleedOuts, interrupted, finished;

    Casualty(Raid raid) { this.raid = raid; }

    // ------------------------------------------------------------------ the state machine
    /** The single transition function. Illegal moves are refused. */
    private boolean set(Actor a, Vital to) {
        Vital from = a.vital;
        boolean ok = switch (to) {
            case DOWNED -> from == Vital.ACTIVE || from == Vital.REVIVING;
            case REVIVING -> from == Vital.DOWNED;
            case ACTIVE -> from == Vital.REVIVING;
            case DEAD -> from != Vital.DEAD;
        };
        if (!ok) return false;
        a.vital = to;
        return true;
    }

    /**
     * Called when damage has taken a character to zero. Returns true when the character went down instead of dying
     * (the caller must then not kill it). Eligibility, in the brief's terms: humans only; never when already
     * incapacitated; never from an explicitly fatal injury (a fatal region, a lost head, catastrophic overkill);
     * the chance scales with how the damage was delivered and how far past zero it went.
     */
    public boolean lethal(Actor v, Actor attacker, float overkill, String region, String kind, boolean catastrophic) {
        if (v.infected() || v.vital != Vital.ACTIVE || v.dead) return false;
        if (catastrophic || cfg.fatalRegions.contains(region)) return false;
        float type = cfg.downType(kind);
        if (type <= 0) return false;
        float over = overkill / Math.max(1f, v.maxHp);
        if (over > cfg.overkill) return false;
        float chance = (v.isPlayer ? cfg.downChancePlayer : v.mate() ? cfg.downChanceMate : cfg.downChance) * type * (1f - 0.5f * over / cfg.overkill);
        if (!raid.rng.chance(chance)) return false;
        down(v, attacker, kind);
        return true;
    }

    /** ACTIVE -> DOWNED. */
    public void down(Actor v, Actor attacker, String kind) {
        if (!set(v, Vital.DOWNED)) return;
        downs++;
        v.hp = Math.min(v.maxHp, cfg.downedHealth);
        v.bleedLeft = cfg.bleedOut;
        v.downedAt = raid.time;
        v.downedBy = attacker == null ? 0 : attacker.id;
        v.reviveT = 0; v.reviverId = 0;
        v.crawling = true; v.crouch = false; v.sprinting = false;
        v.vx = v.vz = 0;
        v.hasCover = false; v.cover = null; v.path = null; v.hasGoal = false;
        v.helpCallAt = raid.time + 0.6f;
        // primary weapons cannot be used from the ground: a sidearm if one is carried, otherwise nothing
        if (!v.isPlayer) {
            ItemInstance side = sidearm(v);
            if (v.weapon != side) { v.stowed = v.weapon; v.weapon = side; v.visual = null; }
            v.state = "downed"; v.stateTime = 0;
        }
        // 1.1: they fall. The body is Sydney's from the moment the legs go until it lies still, and only then does the
        // wounded pose have it; where it cannot be simulated (or it is the player's own body, which is theirs to move)
        // the flinch and the blend are what is left.
        boolean wasCrawling = v.crawling;
        v.crawling = false;
        boolean fell = !v.isPlayer && raid.gore.ragdolls.collapse(v);
        v.crawling = true;
        if (!fell && v.anim != null) v.anim.play("hit", 0.3f);
        raid.emitSound(v.x, v.y + 0.5f, v.z, 26, "body_fall", v.id, false);
        raid.bus.emit(new Events.ActorDowned(v.id, attacker == null ? 0 : attacker.id, Raid.displayName(v), v.faction, raid.time, kind));
        // the squad sees one of its own go down: that is a casualty to act on, not yet a death
        for (Actor m : raid.actors) {
            if (m == v || m.dead || m.isPlayer || m.vital != Vital.ACTIVE || !friendly(m, v) || m.dist(v) > 46) continue;
            if (m.dist(v) < 14 || Physics.los(raid.world, m.x, m.eyeY(), m.z, v.x, v.y + 0.4f, v.z)) {
                m.morale -= 0.05f;
                m.alertness = 1;
                Raid.Squad sq = raid.squad(m);
                if (sq != null) { sq.wounded++; sq.rescueAt = 0; }
            }
        }
        if (v.mate()) raid.notice(v.name + " is down - " + (int) cfg.bleedOut + " seconds", 0xFFF2D43A);
    }

    /** The sidearm a downed agent can still work: its own weapon if that is a pistol, else one from its faction's kit. */
    private ItemInstance sidearm(Actor v) {
        if (v.weapon != null && v.weapon.def().weapon != null && "pistol".equals(v.weapon.def().weapon.family)) return v.weapon;
        if (v.sidearm != null) return v.sidearm;
        Map<String, List<String>> kit = Content.KITS.get(v.mate() ? "player" : v.faction);
        List<String> ids = kit == null ? null : kit.get("sidearm");
        if (ids == null || ids.isEmpty() || raid.rng.chance(0.25)) return null;
        String id = ids.get(raid.rng.nextInt(ids.size()));
        ItemDef d = Content.def(id);
        if (d == null || d.weapon == null) return null;
        ItemInstance w = new ItemInstance(id);
        w.origin = raid.raidId;
        w.setCondition(60);
        w.rounds = d.weapon.capacity;
        w.ammoId = Content.ammoFor(d.weapon.caliber).id;
        v.sidearm = w;
        return w;
    }

    /** DOWNED -> REVIVING: a rescuer in reach starts work. */
    public boolean begin(Actor rescuer, Actor c) {
        if (rescuer == null || c == null || rescuer == c || rescuer.dead || rescuer.vital != Vital.ACTIVE || c.dead) return false;
        if (c.vital == Vital.REVIVING) return c.reviverId == rescuer.id;
        if (c.vital != Vital.DOWNED || !friendly(rescuer, c) || rescuer.dist(c) > cfg.reviveRange) return false;
        if (!set(c, Vital.REVIVING)) return false;
        c.reviverId = rescuer.id; c.reviveT = 0; c.reviveFrom = raid.time;
        rescuer.revivingId = c.id;
        if (rescuer.anim != null) rescuer.anim.reviving = true;
        raid.emitSound(c.x, c.y + 0.5f, c.z, 6, "search", rescuer.id, false);
        return true;
    }

    /** REVIVING -> DOWNED: the rescuer was hit, driven off, or let go. The bleed-out clock resumes where it stopped. */
    public void interrupt(Actor c, String why) {
        if (c == null || c.vital != Vital.REVIVING) return;
        Actor rescuer = raid.actor(c.reviverId);
        if (!set(c, Vital.DOWNED)) return;
        interrupted++;
        c.reviverId = 0; c.reviveT = 0;
        if (rescuer != null) { rescuer.revivingId = 0; if (rescuer.anim != null) rescuer.anim.reviving = false; }
        raid.bus.emit(new Events.ReviveInterrupted(c.id, rescuer == null ? 0 : rescuer.id, why));
    }

    /** REVIVING -> ACTIVE: on their feet, critically wounded; healing is for the existing healing system. */
    private void complete(Actor c) {
        Actor rescuer = raid.actor(c.reviverId);
        if (!set(c, Vital.ACTIVE)) return;
        revives++;
        // the player put a stranger back on their feet: that stranger's group will hear of it from the stranger
        if (rescuer != null && rescuer.isPlayer && !c.mate() && !c.isPlayer) raid.rivalry.onAid(c, true);
        c.hp = Math.max(1, c.maxHp * cfg.reviveHealth);
        c.crawling = false;
        c.bleed = 0; c.bleedLeft = 0; c.reviverId = 0; c.reviveT = 0;
        if (c.isPlayer) { raid.body.bleedHeavy = false; raid.body.healthChanged = raid.time; }
        else {
            if (c.stowed != null) { c.weapon = c.stowed; c.stowed = null; c.visual = null; }
            c.state = "idle"; c.stateTime = 0; c.morale = Math.max(c.morale, 0.4f);
            c.wakeUntil = raid.time + 120;
        }
        if (rescuer != null) { rescuer.revivingId = 0; if (rescuer.anim != null) rescuer.anim.reviving = false; }
        raid.bus.emit(new Events.ActorRevived(c.id, rescuer == null ? 0 : rescuer.id, Raid.displayName(c), raid.time));
        if (rescuer != null && rescuer.isPlayer) { raid.debrief.aided++; raid.profile.contracts.onAid(); }
        if (c.mate() || c.isPlayer) raid.notice((c.isPlayer ? "You are" : c.name + " is") + " back up - critical", 0xFF7DBA5C);
    }

    /** DOWNED / REVIVING -> DEAD by blood loss; credited to whoever put them down. */
    private void bleedOut(Actor c) {
        bleedOuts++;
        if (c.vital == Vital.REVIVING) interrupt(c, "too late");
        c.hp = 0;
        c.hitRegion = "torso"; c.hitForce = 4;
        raid.finish(c, raid.actor(c.downedBy), "bleed");
    }

    /** Called by the raid when a character dies by any route: closes whatever this machine had open for it. */
    void died(Actor a) {
        if (a.revivingId != 0) { Actor c = raid.actor(a.revivingId); a.revivingId = 0; if (a.anim != null) a.anim.reviving = false; if (c != null) interrupt(c, "rescuer killed"); }
        if (a.vital == Vital.REVIVING) { Actor r = raid.actor(a.reviverId); if (r != null) { r.revivingId = 0; if (r.anim != null) r.anim.reviving = false; } }
        if (a.vital != Vital.ACTIVE) finished++;
        a.vital = Vital.DEAD;
        a.crawling = false;
    }

    void update(float dt) {
        List<Actor> list = raid.actors;
        for (int i = 0; i < list.size(); i++) {
            Actor a = list.get(i);
            if (a.dead || a.vital == Vital.ACTIVE || a.vital == Vital.DEAD) continue;
            if (a.vital == Vital.DOWNED) {
                a.bleedLeft -= dt;
                if (a.bleedLeft <= 0) { bleedOut(a); continue; }
            } else {
                Actor r = raid.actor(a.reviverId);
                // the rescue holds only while the rescuer is there, able and unhurt since they began
                if (r == null || r.dead || r.vital != Vital.ACTIVE || r.dist(a) > cfg.reviveRange + 0.5f) { interrupt(a, "rescuer left"); continue; }
                if (r.lastHitTime > a.reviveFrom) { interrupt(a, "rescuer hit"); continue; }
                if (!r.isPlayer && (r.suppression > cfg.aiAbortSuppression || raid.time < r.pinnedUntil)) { interrupt(a, "rescuer pinned"); continue; }
                r.vx *= 0.2f; r.vz *= 0.2f;
                a.reviveT += dt;
                if (a.reviveT >= cfg.reviveTime) complete(a);
            }
        }
    }

    public float bleedFraction(Actor a) { return Math.max(0, Math.min(1, a.bleedLeft / cfg.bleedOut)); }
    public float reviveFraction(Actor a) { return a.vital == Vital.REVIVING ? Math.max(0, Math.min(1, a.reviveT / cfg.reviveTime)) : 0; }

    /** Two characters on the same side for the purpose of first aid. */
    public boolean friendly(Actor a, Actor b) {
        if (a == b || a.infected() || b.infected()) return false;
        boolean aP = "player".equals(a.faction), bP = "player".equals(b.faction);
        if (aP && bP) return true;
        if (aP) return !b.hostileToPlayer;
        if (bP) return !a.hostileToPlayer && a.squad == Raid.MATE_SQUAD;
        return a.faction.equals(b.faction);
    }

    /** The downed character the player could work on right now (in reach, friendly), or null. */
    public Actor playerPatient() {
        Actor p = raid.player, best = null;
        if (p.dead || p.vital != Vital.ACTIVE) return null;
        float bd = cfg.reviveRange;
        for (Actor a : raid.nearby(p.x, p.z, cfg.reviveRange + 1)) {
            if (a.dead || a.vital == Vital.ACTIVE || a.vital == Vital.DEAD || !friendly(p, a)) continue;
            if (a.vital == Vital.REVIVING && a.reviverId != p.id) continue;
            float d = a.dist(p);
            if (d < bd) { bd = d; best = a; }
        }
        return best;
    }

    /** The player holds the interact key on a casualty (true) or has let go (false). */
    public void playerRevive(boolean holding) {
        Actor p = raid.player;
        if (!holding) { if (p.revivingId != 0) { Actor c = raid.actor(p.revivingId); if (c != null) interrupt(c, "let go"); p.revivingId = 0; } return; }
        if (p.revivingId != 0) return;
        Actor c = playerPatient();
        if (c != null) begin(p, c);
    }

    // ================================================================== downed behaviour (agents)
    /** What a downed agent does: drags itself toward the nearest cover, fires its sidearm at what it can see, calls for help. */
    public void downedBrain(Actor a, Actor seen, float dt) {
        a.crouch = false;
        a.tacRole = "";
        if (a.vital == Vital.REVIVING) { a.vx = a.vz = 0; return; }
        if (raid.time >= a.helpCallAt) {
            a.helpCallAt = raid.time + cfg.helpCallEvery;
            raid.say(a, raid.rng.chance(0.5) ? "I'm hit - I'm down!" : "Man down! Help me!", "bark_help");
        }
        float sp = absentialis.neuralai.integration.Archetypes.arch(a.archetype).speed() * cfg.crawlSpeed;
        float tx = a.memX, tz = a.memZ;
        boolean threat = raid.time - a.memTime < 12;
        if (seen != null) { tx = seen.x; tz = seen.z; threat = true; }
        if (threat) {
            // away from the threat, to cover if any is close
            CoverMap.Point best = null;
            float bs = 0.3f;
            SCRATCH.clear();
            for (CoverMap.Point p : raid.world.cover.near(a.x, a.y, a.z, 6, SCRATCH)) {
                float s = p.facing(tx, tz) - a.distXZ(p.cx(), p.cz()) * 0.12f;
                if (p.freeFor(a.id, raid.time) && s > bs) { bs = s; best = p; }
            }
            float gx, gz;
            if (best != null) { gx = best.cx(); gz = best.cz(); }
            else { float dx = a.x - tx, dz = a.z - tz, l = Math.max(0.1f, (float) Math.sqrt(dx * dx + dz * dz)); gx = a.x + dx / l * 3; gz = a.z + dz / l * 3; }
            if (a.distXZ(gx, gz) > 0.6f) { float dx = gx - a.x, dz = gz - a.z, l = Math.max(0.1f, (float) Math.sqrt(dx * dx + dz * dz)); a.vx = dx / l * sp; a.vz = dz / l * sp; }
            else { a.vx *= 0.5f; a.vz *= 0.5f; }
            a.yaw = absentialis.neuralai.integration.Steering.turn(a.yaw, (float) Math.atan2(-(tx - a.x), -(tz - a.z)), dt * 3);
            if (seen != null && a.weapon != null && a.fireCooldown <= 0) {
                float keep = a.aimPenalty;
                a.aimPenalty = keep * cfg.sidearmError;
                raid.npcFire(a, seen, dt);
                a.aimPenalty = keep;
                a.fireCooldown = Math.max(a.fireCooldown, cfg.sidearmCooldown * (0.7f + raid.rng.nextFloat() * 0.6f));
            }
        } else { a.vx *= 0.5f; a.vz *= 0.5f; }
    }

    private static final List<CoverMap.Point> SCRATCH = new ArrayList<>();

    // ================================================================== the rescue decision (agents)
    /**
     * How much a rescue is worth to this would-be rescuer right now, weighed as the brief lists it: urgency (time
     * left), distance, enemy pressure, exposure of the casualty's position, cover there, the rescuer's own health,
     * and what the squad loses by taking them off their job. Negative infinity when it cannot be done in time.
     */
    public float rescueScore(Actor a, Actor c, Raid.Squad sq) {
        float d = a.dist(c);
        if (d > cfg.aiMaxDistance) return Float.NEGATIVE_INFINITY;
        float run = absentialis.neuralai.integration.Archetypes.arch(a.archetype).runSpeed() * 0.85f;
        float eta = d / Math.max(0.5f, run) + 0.5f;
        if (eta >= c.bleedLeft) return Float.NEGATIVE_INFINITY;           // would arrive at a body
        if ((a.impaired & AIInjuryAdapter.CANT_WALK) != 0 || a.weapon == null && a.morale < 0.3f) return Float.NEGATIVE_INFINITY;
        Raid.Squad m = sq;
        boolean fresh = m != null && raid.time - m.contactTime < 15;
        boolean threat = fresh || raid.time - a.memTime < 15;
        float tx = fresh ? m.tx : a.memX, ty = fresh ? m.ty : a.memY, tz = fresh ? m.tz : a.memZ;
        float s = cfg.aiUrgency * (0.35f + 0.65f * (1f - c.bleedLeft / cfg.bleedOut));
        s -= cfg.aiDistance * d;
        float pressure = Math.max(a.suppression, raid.time < a.pinnedUntil ? 1f : 0f);
        if (m != null && raid.time - m.contactTime < 3) pressure = Math.max(pressure, 0.35f);
        s -= cfg.aiPressure * pressure;
        if (threat) {
            boolean exposed = Physics.los(raid.world, c.x, c.y + 0.9f, c.z, tx, ty + 1.3f, tz);
            float near = (float) Math.sqrt((tx - c.x) * (tx - c.x) + (tz - c.z) * (tz - c.z));
            if (exposed) s -= cfg.aiExposure * (near < 12 ? 1.4f : near < 30 ? 1f : 0.6f);
            CoverMap.Point cp = raid.world.cover.at((int) Math.floor(c.x), Math.round(c.y), (int) Math.floor(c.z), tx, tz);
            if (cp != null && cp.facing(tx, tz) > 0.4f) s += cfg.aiCover;
        } else s += cfg.aiCover * 0.5f;
        s -= cfg.aiOwnHealth * (1f - a.hp / Math.max(1f, a.maxHp));
        if ("suppress".equals(a.tacRole)) s -= cfg.aiRoleCost; else if (a.tacRole.startsWith("flank")) s -= cfg.aiRoleCost * 0.7f;
        return s;
    }

    /**
     * Squad-level: who (if anyone) goes for which casualty. Re-evaluated every second and a half, so a rescue that
     * was not feasible becomes one when the pressure lifts - and one under way is given up when it stops being so.
     */
    public void assign(Raid.Squad sq, List<Actor> team, boolean mates) {
        Raid.Squad m = sq;
        if (raid.time < m.rescueAt) return;
        m.rescueAt = raid.time + 1.5f;
        Actor best = null, bestC = null;
        float bs = cfg.aiThreshold;
        List<Actor> casualties = new ArrayList<>();
        for (Actor c : team) if (!c.dead && c.vital == Vital.DOWNED) casualties.add(c);
        if (mates && !raid.player.dead && raid.player.vital == Vital.DOWNED) casualties.add(raid.player);
        for (Actor c : casualties) for (Actor a : team) {
            if (a == c || a.dead || a.vital != Vital.ACTIVE || a.ragdoll != null) continue;
            float s = rescueScore(a, c, sq) + (a.id == m.rescuerId && c.id == m.casualtyId ? 0.35f : 0);     // a rescue under way is worth finishing
            if (s > bs) { bs = s; best = a; bestC = c; }
        }
        int was = m.rescuerId;
        m.rescuerId = best == null ? 0 : best.id;
        m.casualtyId = bestC == null ? 0 : bestC.id;
        m.rescueScore = best == null ? 0 : bs;
        if (best != null && was != best.id) {
            m.rescues++;
            raid.say(best, raid.rng.chance(0.5) ? "Cover me - I'm going for " + (bestC.name.isEmpty() ? "him" : absentialis.neuralai.integration.Relations.callsign(bestC)) + "!" : "Man down - moving to him!", "bark_cover");
        }
    }

    /**
     * Agent-level: runs the rescue if this agent is the squad's rescuer. Returns true when it handled the tick
     * (moving to the casualty, or working on them); false when the agent should get on with its own fight.
     */
    public boolean rescue(Actor a, Raid.Squad sq, float dt) {
        if (sq == null || sq.rescuerId != a.id) return false;
        Actor c = raid.actor(sq.casualtyId);
        if (c == null || c.dead || c.vital == Vital.ACTIVE || c.vital == Vital.DEAD) { sq.rescuerId = 0; a.tacRole = ""; return false; }
        if (c.vital == Vital.REVIVING && c.reviverId != a.id) { sq.rescuerId = 0; return false; }
        a.tacRole = "medic";
        a.hasCover = false;
        float d = a.dist(c);
        if (d > cfg.reviveRange - 0.3f) {
            // the last stretch is run low; the interaction position is beside the casualty, not on top of them
            absentialis.neuralai.integration.Steering.goTo(raid, a, c.x, c.z, absentialis.neuralai.integration.Archetypes.arch(a.archetype).runSpeed() * 0.85f, dt);
            a.crouch = d < 6;
            return true;
        }
        a.vx = a.vz = 0;
        a.crouch = true;
        a.yaw = absentialis.neuralai.integration.Steering.turn(a.yaw, (float) Math.atan2(-(c.x - a.x), -(c.z - a.z)), dt * 8);
        if (c.vital == Vital.DOWNED) begin(a, c);
        return true;
    }
}
