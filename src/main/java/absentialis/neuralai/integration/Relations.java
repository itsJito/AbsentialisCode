package absentialis.neuralai.integration;

import absentialis.entity.Actor;
import absentialis.scene.Raid;

/**
 * Who stands where with whom, and what each side is like (0.8; these rules were scattered through the old AI).
 *
 * Hostility comes from the diplomatic matrix, the player's standing and observed evidence. Each human faction also
 * has a doctrine - how far from home it will fight, how much nerve it starts with, where it breaks - which is the
 * host's description of the side, read by NeuralAI through the seam as part of an agent's own condition.
 *
 *   MERC     disciplined fire teams: call for support, hold their asset, fall back in order, strip weapons from the dead.
 *   BANDIT   predatory and loud: size the target up, extort the weak, rush, and break quickly when it goes wrong.
 *   MILITARY cordon troops: posts and patrols, a warning before a restricted area is enforced, never far from the perimeter.
 *   LONER    cautious scavengers: neutral by default, back away from trouble, fight when cornered or when one of theirs is hit.
 */
public final class Relations {
    private Relations() {}

    public enum Doctrine { MERC, BANDIT, MILITARY, LONER }

    /** leash = how far from home they will fight; fightDist = preferred engagement range. */
    public record Doc(Doctrine d, float leash, float morale, float risk, float fightDist, float breakAt, boolean cover) {}

    private static final Doc MERC = new Doc(Doctrine.MERC, 46, 0.95f, 0.55f, 18, 0.25f, true);
    private static final Doc BANDIT = new Doc(Doctrine.BANDIT, 70, 0.60f, 0.75f, 10, 0.45f, false);
    private static final Doc MILITARY = new Doc(Doctrine.MILITARY, 34, 0.90f, 0.40f, 22, 0.20f, true);
    private static final Doc LONER = new Doc(Doctrine.LONER, 40, 0.55f, 0.15f, 16, 0.50f, true);
    /** The player's own people: they hold to the leader and do not break. */
    public static final Doc SQUAD = new Doc(Doctrine.MERC, 60, 1f, 0.5f, 16, 0.1f, true);

    public static Doc doc(String faction) {
        return switch (faction) { case "mercenaries" -> MERC; case "raiders" -> BANDIT; case "military" -> MILITARY; default -> LONER; };
    }

    /** Sets the doctrine's starting morale and risk tolerance on a freshly spawned human. */
    public static void initHuman(Actor a) { Doc d = doc(a.faction); a.morale = d.morale(); a.risk = d.risk(); }

    /** Factions whose members carry radios: a report reaches them wherever they are, a moment later. */
    public static boolean radio(String faction) { return "military".equals(faction) || "mercenaries".equals(faction); }

    /** Hostility from the diplomatic matrix + player standing + observed evidence. */
    public static boolean hostile(Raid r, Actor a, Actor b) {
        if (a.faction.equals(b.faction)) return false;
        if (a.infected() || b.infected()) return true;
        // the player's squadmates stand exactly where the player stands with everyone
        if ("player".equals(b.faction)) return a.hostileToPlayer;
        if ("player".equals(a.faction)) return b.hostileToPlayer;
        String x = a.faction, y = b.faction;
        // The cordon suppresses bandits; bandits prey on loners and probe the contractors' sites.
        // Military and mercenaries keep an uneasy truce; loners have no standing feud with either.
        return pair(x, y, "military", "raiders") || pair(x, y, "survivors", "raiders") || pair(x, y, "raiders", "mercenaries");
    }

    /** The same question asked of two sides rather than two people. */
    public static boolean factionsHostile(String x, String y) {
        if (x.equals(y)) return false;
        if ("infected".equals(x) || "infected".equals(y)) return true;
        return pair(x, y, "military", "raiders") || pair(x, y, "survivors", "raiders") || pair(x, y, "raiders", "mercenaries");
    }

    private static boolean pair(String a, String b, String p, String q) { return a.equals(p) && b.equals(q) || a.equals(q) && b.equals(p); }

    /** Initial disposition toward the player for this raid, from standing. */
    public static boolean initialHostility(String faction, float rep) {
        switch (faction) {
            case "military": return rep < -10;
            case "survivors": return rep < -25;
            case "raiders": return rep < 30;
            case "mercenaries": return rep < 20;
            default: return true;
        }
    }

    /** How one agent names another out loud. */
    public static String callsign(Actor a) { return a.name.isEmpty() ? "you" : absentialis.gameplay.squad.Roster.shortName(a.name); }
}
