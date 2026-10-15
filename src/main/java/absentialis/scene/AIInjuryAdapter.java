package absentialis.scene;

import absentialis.neuralai.integration.NeuralHost;
import absentialis.anatomy.AnatomicalState;
import absentialis.character.Rig;
import absentialis.entity.Actor;
import absentialis.entity.Physics;

/**
 * Translates anatomy into behaviour. The AI never reads voxels; it reads what this adapter writes onto the actor
 * after every change to their body:
 *
 *   speed share and crawling ..... legs that no longer carry them
 *   aim error ..................... arms that no longer steady the weapon
 *   the weapon itself ............. dropped when the hand that held it is gone or useless
 *   an injury event ............... how bad (0 .. 1), which region, what it cost them - the action-selection layer
 *                                   decides from it whether to press on, get to cover, pull back or give up
 *   morale ........................ a maimed human is a frightened one; the infected do not care
 * and it tells the people around them: allies who see a comrade badly hurt react to that, and say so.
 */
public final class AIInjuryAdapter {
    private AIInjuryAdapter() {}

    /** Impairment bits carried in Actor.impaired. */
    public static final int CANT_WALK = 1, CANT_SHOOT = 2, ONE_HANDED = 4, CANT_STRIKE = 8, LIMPING = 16, BLEEDING_OUT = 32;

    static void apply(Raid raid, Actor v, AnatomicalState s, int group, int lost, float severityBefore, boolean couldWalk, boolean couldHold, Actor attacker) {
        if (v.isPlayer) return;     // the player's body is the Survivor's business (fractures, arm and leg injury)
        boolean infected = v.infected();
        // ---- locomotion
        v.speedMul = Math.min(v.speedMul, s.mobility);
        if (!s.canWalk) v.crawling = true;
        v.limpMask = s.limpMask & ~s.missingMask;
        // ---- weapon handling
        v.aimPenalty = Math.max(v.aimPenalty, s.handling);
        if (!s.canHoldWeapon && v.weapon != null) {
            v.weapon.ownerFaction = v.faction;
            raid.dropItem(v.weapon, v.x, v.y + 1, v.z);
            v.weapon = null;
            v.visual = null;
        }
        int imp = 0;
        if (!s.canWalk) imp |= CANT_WALK;
        else if (s.mobility < 0.85f) imp |= LIMPING;
        if (v.weapon == null && !infected) imp |= CANT_SHOOT;
        if (!s.canTwoHand && s.canHoldWeapon) imp |= ONE_HANDED;
        if (!s.canStrike) imp |= CANT_STRIKE;
        if (v.bleed > 4 || s.bleeding > 2.5f) imp |= BLEEDING_OUT;
        int newly = imp & ~v.impaired;
        v.impaired = imp;
        // ---- the injury event the AI acts on
        float jump = Math.max(0, s.severity - severityBefore) + Math.min(0.3f, lost * 0.006f);
        if (jump > 0.02f || newly != 0) {
            v.injurySeverity = Math.min(1, Math.max(v.injurySeverity * 0.7f, 0) + jump + (newly != 0 ? 0.25f : 0));
            v.injuryGroup = group;
            v.injuryTime = raid.time;
            v.injuryFrom = attacker == null ? 0 : attacker.id;
        }
        if (infected) {
            // the infected do not retreat: they adapt. No legs: they crawl. No arms: they still bite.
            return;
        }
        if (newly != 0 || jump > 0.2f) v.morale = Math.max(0, v.morale - 0.12f - jump * 0.5f - ((newly & (CANT_WALK | CANT_SHOOT)) != 0 ? 0.25f : 0));
        if ((newly & CANT_SHOOT) != 0) v.morale = Math.min(v.morale, 0.12f);
        // ---- the people around them
        boolean critical = s.severity > 0.45f || (newly & (CANT_WALK | CANT_SHOOT)) != 0;
        if (critical && raid.time - v.lastCritical > 6) {
            v.lastCritical = raid.time;
            if ((newly & CANT_WALK) != 0) raid.say(v, "My leg! I can't walk!", "bark_pain");
            else if ((newly & CANT_SHOOT) != 0) raid.say(v, "I've lost my weapon - I'm out!", "bark_help");
            else raid.say(v, "I'm hit! I'm hit bad!", "bark_pain");
            for (Actor m : raid.nearby(v.x, v.z, 28)) {
                if (m == v || m.dead || m.isPlayer || m.infected() || !m.faction.equals(v.faction)) continue;
                if (m.dist(v) > 10 && !Physics.los(raid.world, m.x, m.eyeY(), m.z, v.x, v.y + 1, v.z)) continue;
                NeuralHost.onAllyHurt(raid, m, v, s.severity, attacker);
            }
        }
    }

    /** Short description of what an actor has lost, for callouts and debug overlays. */
    public static String describe(Actor a) {
        if (a.anat == null) return "unhurt";
        StringBuilder b = new StringBuilder();
        if ((a.impaired & CANT_WALK) != 0) b.append("cannot walk ");
        else if ((a.impaired & LIMPING) != 0) b.append("limping ");
        if ((a.impaired & CANT_SHOOT) != 0) b.append("disarmed ");
        if ((a.impaired & ONE_HANDED) != 0) b.append("one-handed ");
        if ((a.anat.missingMask & Rig.bit(Rig.HEAD)) != 0) b.append("headless ");
        return b.length() == 0 ? String.format("hurt %.0f%%", a.anat.severity * 100) : b.toString().trim();
    }
}
