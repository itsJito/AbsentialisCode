package absentialis.neuralai.integration;

import absentialis.entity.Actor;
import absentialis.neuralai.integration.Relations.Doc;
import absentialis.neuralai.integration.Relations.Doctrine;
import absentialis.scene.Raid;

/**
 * The raid's ruling on a blow between people (0.8: what is left of the old close-combat class once the deciding went
 * to NeuralAI). A mind asks its body for a strike, a shove or a grab; the body winds up and opens a contact window;
 * this is what happens if somebody is in it - and what the one on the receiving end manages to do about it.
 *
 *   shove ....... makes room; against a wall it hurts
 *   strike ...... a rifle butt, or a blade for those who carry one
 *   grab ........ holds the victim for a moment - long enough for someone else to shoot
 *   block ....... someone who saw it coming, with a weapon to put in the way, stops most of it and is given the
 *                 opening for a counter (their own mind takes it or does not)
 */
public final class Blows {
    private Blows() {}

    /** Damage of a strike with a blade and with anything else, and the reach inside which a blow that was started still lands. */
    public static float BLADE = 24, BUTT = 17, STILL_IN_REACH = 2.4f;
    public static int strikes, shoves, grabs, blocks, counters, interrupted;

    /** The contact window has opened on someone. */
    public static void land(Raid r, Actor a, Actor t) {
        String move = a.meleeMove;
        a.meleeMove = "";
        if (a.dist(t) > STILL_IN_REACH) { a.fireCooldown = 0.85f; return; }       // the opponent stepped out: a miss, and a long recovery
        switch (move) {
            case "shove" -> { shoves++; r.shove(a, t); a.fireCooldown = 0.55f; }
            case "grab" -> {
                grabs++;
                a.lastGrab = r.time;
                r.grab(a, t);
                a.fireCooldown = 1.3f;
                NeuralHost.say(r, a, r.squad(a), "Got him! Shoot!", "bark_help", 0.5f);
            }
            default -> {
                strikes++;
                boolean blade = a.weapon != null && "melee.knife".equals(a.weapon.defId);
                r.melee(a, t, (blade ? BLADE : BUTT) * r.difficultyDamage());
                a.fireCooldown = 0.75f;
            }
        }
    }

    /**
     * Someone swings at v. A person who saw it coming may block (most of the blow is stopped) or step out of it.
     * Returns the share of the damage that gets through: 1, 0.25 or 0.
     */
    public static float defend(Raid r, Actor v, Actor attacker) {
        if (v.dead || v.infected() || v.isPlayer || v.ragdoll != null || r.time < v.staggerUntil || v.meleeWind > 0) return 1;
        if (!"engage".equals(v.state) || v.targetId != attacker.id) return 1;               // not fighting this opponent: caught cold
        float dx = attacker.x - v.x, dz = attacker.z - v.z, dl = Math.max(0.1f, (float) Math.sqrt(dx * dx + dz * dz));
        float cos = (dx * (float) -Math.sin(v.yaw) + dz * (float) -Math.cos(v.yaw)) / dl;
        if (cos < 0.35f) return 1;                                                           // from the side or behind
        Doc dc = Relations.doc(v.faction);
        float skill = dc.d() == Doctrine.MILITARY || dc.d() == Doctrine.MERC ? 0.2f : 0;
        float roll = r.rng.nextFloat();
        if (v.weapon != null && roll < 0.3f + skill - v.injurySeverity * 0.3f) {
            blocks++;
            counters++;
            r.emitSound(v.x, v.y + 1.2f, v.z, 9, "block", v.id, false);
            // straight back out of the block: the weapon is ready at once, and the mind is asked for its answer now
            v.fireCooldown = 0;
            r.neural.nudge(v.id);
            return 0.25f;
        }
        if (roll > 0.86f - (dc.d() == Doctrine.BANDIT ? 0.1f : 0)) {
            // out of reach: a step back along the line of the blow
            v.vx -= dx / dl * 4.5f; v.vz -= dz / dl * 4.5f;
            return 0;
        }
        return 1;
    }
}
