package absentialis.scene;

import absentialis.neuralai.integration.NeuralHost;
import absentialis.entity.Actor;
import absentialis.gameplay.items.ItemInstance;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.squad.Roster;

/**
 * The infection (0.4): the infected bite, a bite that breaks the skin carries the virus, and an untreated
 * carrier - the player, a squadmate, a soldier, a raider - turns.
 *
 * <b>Bites.</b> Walkers, sprinters and their kin bite when they get hold of someone; the giant and the fat one do
 * not. Body armour and a helmet stop some bites from breaking the skin, a crouched or staggered victim is easier
 * to bite, and a bite always bleeds.
 *
 * <b>Course.</b> Infection is a number from 0 to 100. It starts where the bite put it and climbs on its own:
 * <i>bitten</i> (no symptoms yet), <i>fever</i> from 25 (stamina comes back slowly, the carrier coughs - and a cough
 * is a noise), <i>sick</i> from 50 (health drains, hands shake), <i>critical</i> from 75 (the drain is fast and the
 * picture goes green). At 100 the carrier turns.
 *
 * <b>Treatment.</b> Antiviral Medicine is common and clears the infection outright at any stage before the end.
 * Antibiotics do not cure it; they hold it where it is for two minutes. Agents who carry antivirals treat
 * themselves once they are out of the fight; a bitten squadmate says so and can be handed a dose with [F].
 *
 * <b>Turning.</b> The carrier keeps their own body - the same face, clothes and kit, the skin gone green - and
 * moves like the infected. A human agent who turns is dead to their faction and squad; a squadmate is a loss.
 * When the player turns the raid is over: the body goes down, and gets back up as one of them.
 */
public final class InfectionSystem {
    public static final float FEVER = 25, SICK = 50, CRITICAL = 75, TURN = 100;
    /** Seconds from a first bite to turning, untreated, at Standard difficulty: the player has longer than an agent does. */
    public static final float PLAYER_COURSE = 420, AGENT_COURSE = 150;
    public static final String ANTIVIRAL = "med.antiviral";

    private final Raid raid;
    public int bites, turnedCount, cured;
    /** The player's body after it has turned and stood up again (the death camera follows it). */
    public Actor playerHusk;
    private float riseAt = -1, coughAt, agentTick;

    InfectionSystem(Raid raid) { this.raid = raid; }

    public static String stage(float infection) {
        return infection <= 0 ? "" : infection < FEVER ? "BITTEN" : infection < SICK ? "FEVER" : infection < CRITICAL ? "SICK" : "CRITICAL";
    }

    /** Which infected bite at all: the ones with a mouth free and a reason to use it. */
    public static boolean biter(Actor a) {
        return a.infected() && !"tank".equals(a.archetype) && !"bomber".equals(a.archetype) && !"gladiator".equals(a.archetype) && !"butcher".equals(a.archetype) && !"tyrant".equals(a.archetype) && !"crawler".equals(a.archetype);
    }

    /** Chance that a landed close attack is a bite that breaks the skin. */
    public float biteChance(Actor a, Actor t) {
        if (!biter(a) || t.infected() || t.dead) return 0;
        float c = "sprinter".equals(a.archetype) ? 0.30f : 0.42f;
        if (a.turned) c *= 0.85f;                              // the freshly turned are clumsier
        if (t.crouch || raid.time < t.staggerUntil) c *= 1.35f; // held down or off balance: easy prey
        if (t.armorClass > 0 && t.armorDur > 0) c *= 0.72f;     // a vest covers the torso, not the arms and neck
        if (t.shield > 0) c *= 0.6f;
        if (t.helmet) c *= 0.9f;
        if (t.isPlayer && raid.difficulty() == 0) c *= 0.75f;
        return Math.min(0.8f, c);
    }

    /** A bite has broken the skin. */
    public void bitten(Actor victim, Actor biter) {
        if (victim.dead || victim.infected()) return;
        if (victim.isPlayer && raid.god) return;
        bites++;
        boolean first = level(victim) <= 0;
        float add = first ? 14 + raid.rng.nextFloat() * 8 : 9 + raid.rng.nextFloat() * 6;
        setLevel(victim, Math.min(TURN - 1, level(victim) + add));
        victim.bleed += 1.2f;
        raid.emitSound(victim.x, victim.y + 1.2f, victim.z, 12, "bite", biter == null ? 0 : biter.id, false);
        if (victim.isPlayer) {
            raid.body.bleedLight = true;
            raid.body.suppressUntil = Math.min(raid.body.suppressUntil, raid.time);   // a fresh bite ends any hold
            raid.notice(first ? "BITTEN. Antiviral Medicine stops it - find some before it takes hold." : "Bitten again - the infection is climbing.", 0xFFB8D048);
            return;
        }
        if (!first) return;
        // agents: do they carry a dose? Organised factions usually do, loners rarely
        float carry = switch (victim.faction) { case "military" -> 0.7f; case "mercenaries" -> 0.6f; case "survivors" -> 0.3f; case "raiders" -> 0.25f; default -> 0.4f; };
        if (victim.mate()) carry = 0.35f;
        victim.infectTreatAt = raid.rng.chance(carry) ? raid.time + 9 + raid.rng.nextFloat() * 16 : -1;
        if (!victim.infected()) victim.morale = Math.max(0.1f, victim.morale - 0.2f);
        raid.say(victim, victim.infectTreatAt > 0 ? "I'm bit! Cover me, I've got a shot for it!" : victim.mate() ? "I'm bitten - I need antivirals!" : "It bit me! It BIT me!", "bark_help");
    }

    private float level(Actor a) { return a.isPlayer ? raid.body.infection : a.infection; }
    private void setLevel(Actor a, float v) { if (a.isPlayer) raid.body.infection = v; a.infection = v; }

    /** Antiviral Medicine: the infection is gone, whatever stage it had reached. */
    public void cure(Actor a) {
        if (level(a) <= 0) return;
        setLevel(a, 0);
        a.infectTreatAt = -1;
        cured++;
        if (!a.isPlayer) { a.speedMul = Math.max(a.speedMul, Math.min(1, a.speedMul + 0.2f)); raid.say(a, "That's got it. I'm good.", "bark_clear"); }
    }

    /** The player hands a dose to a bitten squadmate or stranger in front of them. Returns the feedback line. */
    public String give(Actor a) {
        ItemInstance dose = null;
        for (ItemInstance i : raid.profile.loadout.all()) if (ANTIVIRAL.equals(i.defId)) { dose = i; break; }
        if (dose == null) return "You have no Antiviral Medicine";
        InventoryService.consume(dose, 1);
        cure(a);
        if (!a.mate() && !a.infected()) { raid.debrief.repDelta.merge(a.faction, 3f, Float::sum); a.hostileToPlayer = false; raid.debrief.aided++; raid.profile.contracts.onAid(); }
        return "Antiviral given to " + (a.name.isEmpty() ? "them" : Roster.shortName(a.name)) + " - the infection is cleared";
    }

    /** A bitten, untreated human within reach whom the player could dose (squadmates first). */
    public Actor patient(float reach) {
        Actor best = null;
        for (Actor a : raid.nearby(raid.player.x, raid.player.z, reach)) {
            if (a.isPlayer || a.dead || a.infected() || a.infection <= 0 || a.dist(raid.player) > reach) continue;
            if (!a.mate() && a.hostileToPlayer) continue;
            if (best == null || a.mate() && !best.mate()) best = a;
        }
        return best;
    }

    void update(float dt) {
        float diff = raid.difficulty() == 0 ? 0.7f : raid.difficulty() == 2 ? 1.3f : 1f;
        // ---- the player (the Survivor carries the symptoms; this is the course of the disease)
        Actor p = raid.player;
        var body = raid.body;
        if (!p.dead && body.infection > 0) {
            float held = raid.time < body.suppressUntil ? 0.12f : 1f;
            body.infection = Math.min(TURN, body.infection + dt * (TURN - 18) / PLAYER_COURSE * diff * held);
            p.infection = body.infection;
            if (body.infection >= FEVER && raid.time > coughAt) {
                coughAt = raid.time + 14 + raid.rng.nextFloat() * 22 - Math.min(10, (body.infection - FEVER) * 0.2f);
                raid.emitSound(p.x, p.y + 1.5f, p.z, body.infection >= SICK ? 16 : 10, "cough", p.id, false);
            }
            int st = body.infection >= CRITICAL ? 3 : body.infection >= SICK ? 2 : body.infection >= FEVER ? 1 : 0;
            if (st > body.infectionStage) raid.notice(st == 1 ? "Fever. The bite is spreading - Antiviral Medicine cures it." : st == 2 ? "You are sick. Find Antiviral Medicine." : "CRITICAL - you are turning. Antiviral Medicine, now.", st == 3 ? 0xFFD8483A : 0xFFB8D048);
            body.infectionStage = st;
            if (body.infection >= TURN && !raid.god) {
                raid.die("Turned - the infection took you");
                riseAt = raid.time + 4.2f;
            }
        } else if (body.infection <= 0) { body.infectionStage = 0; p.infection = 0; }
        if (riseAt > 0 && raid.time >= riseAt && playerHusk == null) rise();
        // ---- agents: a coarse tick is plenty for a disease
        agentTick += dt;
        if (agentTick < 0.5f) return;
        float step = agentTick;
        agentTick = 0;
        for (int i = 0; i < raid.actors.size(); i++) {
            Actor a = raid.actors.get(i);
            if (a.isPlayer || a.dead || a.infected() || a.infection <= 0) continue;
            if (a.infectTreatAt > 0 && raid.time >= a.infectTreatAt) {
                // they need a quiet moment to use it: under fire the dose waits
                if ("engage".equals(a.state) && raid.time - a.lastSeenTime < 3) { a.infectTreatAt = raid.time + 3; continue; }
                a.staggerUntil = Math.max(a.staggerUntil, raid.time + 1.2f);
                if (a.anim != null) a.anim.play("heal", 1.2f);
                raid.emitSound(a.x, a.y + 1, a.z, 5, "antiviral", a.id, false);
                cure(a);
                continue;
            }
            a.infection = Math.min(TURN, a.infection + step * (TURN - 18) / AGENT_COURSE * diff);
            // the sick fight badly and move badly
            if (a.infection >= SICK) { a.aimPenalty = Math.max(a.aimPenalty, 1.5f); a.speedMul = Math.min(a.speedMul, 0.8f); }
            if (a.infection >= FEVER && raid.rng.chance(step * 0.05f)) raid.emitSound(a.x, a.y + 1.4f, a.z, 9, "cough", a.id, false);
            if (a.infection >= CRITICAL && a.infectTreatAt < 0 && raid.rng.chance(step * 0.06f)) raid.say(a, a.mate() ? "I can't... hold it. Antivirals, please!" : "Stay back - I'm turning!", "bark_help");
            if (a.infection >= TURN) turn(a);
        }
    }

    /** A human agent becomes an infected in the body they had. */
    public void turn(Actor a) {
        if (a.dead || a.infected()) return;
        turnedCount++;
        String was = a.faction;
        // the look they wore is the look they keep (ActorRenderer greens the skin of the turned)
        a.appearance = ActorRenderer.lookFor(a).app;
        if (a.weapon != null) { a.weapon.ownerFaction = was; raid.dropItem(a.weapon, a.x, a.y + 1, a.z); a.weapon = null; }
        // their own side sees it happen
        for (Actor m : raid.nearby(a.x, a.z, 30)) if (m != a && !m.dead && !m.isPlayer && !m.infected() && m.faction.equals(was)) NeuralHost.onAllyDown(raid, m, a);
        raid.agentLost(a);
        boolean fast = a.speedMul >= 0.9f && raid.rng.chance(0.25);
        a.turned = true;
        a.turnedFrom = was;
        a.faction = "infected";
        a.archetype = fast ? "sprinter" : "walker";
        float keepStand = a.stand, keepRadius = a.radius;
        Raid.shape(a);
        a.stand = keepStand; a.radius = keepRadius;
        a.visual = null;
        a.squad = 0; a.squadUid = 0; a.role = ""; a.sqOrder = "follow"; a.qOrder = null;
        a.hostileToPlayer = true; a.aggressor = true;
        a.state = "idle"; a.stateTime = 0; a.targetId = 0; a.hasGoal = false; a.hasOrder = false; a.hasCover = false; a.path = null;
        a.infection = TURN; a.infectTreatAt = -1; a.bleed = 0; a.aimPenalty = 1; a.speedMul = Math.max(a.speedMul, 0.85f);
        a.needsHelp = false; a.light = false;
        a.staggerUntil = raid.time + 1.8f;
        if (a.anim != null) a.anim.play("stumble", 1.4f);
        raid.emitSound(a.x, a.y + 1.4f, a.z, 22, "infected_turn", a.id, false);
        if (raid.listener != null && a.dist(raid.player) < 40) raid.listener.message((a.name.isEmpty() ? "A " + absentialis.sim.ZoneState.one(was) : a.name) + " has turned.", 0xFFB8D048);
    }

    /** The player's body gets back up: an infected wearing everything the player wore. */
    private void rise() {
        Actor p = raid.player;
        Actor h = new Actor();
        h.faction = "infected";
        h.archetype = "walker";
        h.turned = true;
        h.turnedFrom = "player";
        h.name = raid.profile.name;
        h.appearance = raid.profile.appearance.copy();
        h.armorClass = p.armorClass; h.helmet = p.helmet;
        float bx = p.ragdoll != null ? p.ragdoll.x[absentialis.character.Ragdoll.PELVIS] : p.x, bz = p.ragdoll != null ? p.ragdoll.z[absentialis.character.Ragdoll.PELVIS] : p.z;
        int gy = raid.groundY((int) Math.floor(bx), (int) Math.floor(bz));
        h.x = bx; h.z = bz; h.y = gy < 0 || Math.abs(gy - p.y) > 3 ? p.y : gy;
        h.homeX = h.x; h.homeZ = h.z; h.yaw = p.yaw;
        Raid.shape(h);
        h.infection = TURN;
        h.hostileToPlayer = true;
        h.staggerUntil = raid.time + 2.4f;
        if (h.anim != null) h.anim.play("stumble", 1.8f);
        raid.actors.add(h);
        playerHusk = h;
        turnedCount++;
        raid.emitSound(h.x, h.y + 1.4f, h.z, 26, "infected_turn", h.id, false);
    }

    public boolean playerRisen() { return playerHusk != null; }
}
