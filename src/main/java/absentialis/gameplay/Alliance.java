package absentialis.gameplay;

import absentialis.persistence.Profile;
import absentialis.rivalry.Doctrine;
import absentialis.rivalry.RivalryEngine;
import absentialis.rivalry.RivalryState;
import absentialis.rivalry.RivalryWorld;
import absentialis.world.District;

/**
 * The survivor's allegiance. 0.6 called this an alliance and made the player a member of the group chosen; 0.7
 * does not. The player is a third party - one of nobody's - and the Military, the Bandits, the Loners or the
 * Mercenaries are simply who they lean toward to begin with. The choice is made once, is stored on the profile
 * (still under the key "alliance", so 0.6 saves read as they are), and decides
 *
 *   WHERE they may deploy from - only ground their faction holds: its headquarters district, any district the
 *   faction has since taken (the strategic layer's controller of that district), or the ground of a group that has
 *   signed a formal alliance with them;
 *
 *   WHAT work they are offered - each faction's contract board is weighted toward its own trade
 *   (see {@link absentialis.gameplay.missions.Contracts#generate});
 *
 *   WHO knows their face to begin with - the side leaned toward accepts them on sight. Nobody is made an enemy by
 *   it: every other standing starts where it would have, and all of them move with what the player does.
 *
 * Keys are the faction keys the whole game uses: military, raiders (the Bandits), survivors (the Loners), mercenaries.
 */
public final class Alliance {
    private Alliance() {}

    /** Standing with the side leaned toward on the day the allegiance is declared (tunable). Above every faction's shoot-on-sight line. */
    public static float START_STANDING = 35f;

    /** 0.8: the permanent lean - standing that is always there with the side the survivor's answer points to, whatever else happens. */
    public static float LEANING = 12f;

    /**
     * 0.8: the question the survivor is actually asked. Not "which faction" but "what are you looking for" - and the
     * answer is one of four plain words, each with a sign. Each answer leans toward one side: its ground is where the
     * survivor starts, its work is what is on the board first, and its people are permanently a little warmer.
     */
    public record Motive(String key, String word, String glyph, String line, String detail) {}

    public static final Motive[] MOTIVES = {
            new Motive("mercenaries", "PROFIT", "motive_profit", "Get paid. Get out.", "Someone is still paying people to go out there."),
            new Motive("military", "STABILITY", "motive_stability", "A line that holds.", "Checkpoints, permits, and orders that might change."),
            new Motive("survivors", "PEACE", "motive_peace", "Keep people alive.", "The ones who never left, and what they need."),
            new Motive("raiders", "CHAOS", "motive_chaos", "Take what is there.", "Out here, that is as close to law as anyone gets.")};

    public static Motive motive(String key) { for (Motive m : MOTIVES) if (m.key().equals(key)) return m; return null; }

    public record Def(String key, String name, String motto, String blurb, String work, int colour) {}

    public static final Def[] ALL = {
            new Def("military", "Military", "Hold the line.",
                    "The cordon garrison: checkpoints, patrols and the airfield. Disciplined, well supplied and answerable for every round. They know your face and let you through; you take their work, not their orders.",
                    "Reconnaissance, intelligence recovery, bounties on bandits, clearing the infected off the approaches.", 0xFF5E7448),
            new Def("raiders", "Bandits", "Take what is there.",
                    "The crews of the rail yards and the gaol. Nobody gives you anything, so you take it. They will deal with you instead of robbing you - for as long as you stay worth dealing with.",
                    "Hits on patrols and contractors, fuel and powder runs, getting loads out past the cordon.", 0xFF7E4A3C),
            new Def("survivors", "Loners", "Get everyone through.",
                    "The people who never left: families in cellars, scavengers, the odd doctor. Poor, careful and hard to find. They will open the door to you; they cannot do much more than that.",
                    "Supply runs for food and medicine, thinning the infected near the shelters, courier work.", 0xFF8A7A52),
            new Def("mercenaries", "Mercenaries", "Everything has a price.",
                    "Contractors guarding somebody's assets downtown and at the laboratory. The best kit in the Zone and no loyalty beyond the invoice. You are on their books as a subcontractor, which is as close as anyone gets.",
                    "Paid eliminations, asset recovery, survey work. The pay is better and the standing it earns is thinner.", 0xFF5A6470)};

    public static Def def(String key) { for (Def d : ALL) if (d.key().equals(key)) return d; return null; }
    public static boolean chosen(Profile p) { return def(p.alliance) != null; }
    public static String name(String key) { Def d = def(key); return d == null ? "Independent" : d.name(); }

    /** The districts whose headquarters belong to a faction (the Bandits hold two). */
    public static java.util.List<District> homes(String key) {
        java.util.List<District> l = new java.util.ArrayList<>();
        for (District d : District.CITY) if (d.def.homeOf(key)) l.add(d);
        return l;
    }

    /** Why the player may deploy into a district, or null when they may not. A profile that has not chosen yet may go anywhere. */
    public static String deployReason(Profile p, District d) {
        if (!chosen(p)) return "unaligned";
        if (d.def.homeOf(p.alliance)) return name(p.alliance) + " headquarters";
        if (p.alliance.equals(p.zone.controller(d))) return "taken by the " + name(p.alliance);
        RivalryState s = p.rivalry;
        for (RivalryState.Treaty t : s.treaties.values()) {
            if (!t.active() || !"alliance".equals(t.kind)) continue;
            RivalryState.Group g = s.group(t.groupId);
            if (g != null && g.active && g.territoryIds.contains(d.name())) return "ground of your allies, " + g.name;
        }
        return null;
    }

    public static boolean mayDeploy(Profile p, District d) { return deployReason(p, d) != null; }

    /** What it would take to open a district the player cannot deploy into yet. */
    public static String howToOpen(Profile p, District d) {
        String holder = p.zone.controller(d);
        return "Held by " + (holder == null ? "nobody" : name(holder)) + ". You can deploy here once the " + name(p.alliance) + " control it, or once a group that holds it signs an alliance with you.";
    }

    /**
     * Makes the choice. Standing with the chosen faction rises to where its people accept the player on sight, the
     * factions at war with it turn cold, and the faction's own groups start from a first impression in the player's
     * favor - an impression only: each group's favor is its own from then on.
     */
    public static void choose(Profile p, String key) {
        Def d = def(key);
        if (d == null) return;
        p.alliance = key;
        // 0.7: an allegiance is a leaning, not a membership. The player belongs to no group - a third party to every
        // quarrel in the Zone. The side leaned toward takes them as a known face (enough standing not to be shot on
        // sight, START_STANDING), and its groups start with a first impression in their favor. Nobody is made an
        // enemy by the choice: that side's rivals are not turned against the player, and whatever anyone comes to
        // think of them afterwards is earned in the field.
        p.rep.put(key, Math.max(p.rep(key), START_STANDING));
        RivalryEngine e = RivalryWorld.engine(p);
        String rf = Doctrine.ofGameKey(key);
        float share = Doctrine.world("firstImpressionShare", 0.25f);
        for (RivalryState.Group g : e.s.active()) {
            if (g.faction.equals(rf)) { g.playerRelation.favor = Math.max(g.playerRelation.favor, 100 * share); g.playerRelation.trust = Math.max(g.playerRelation.trust, 60 * share); }
        }
        if (rf != null) e.s.factionReputation.put(rf, Math.max(e.s.factionReputation.getOrDefault(rf, 0f), 25f));
        p.contracts.board.clear();
        p.contracts.refill(p.seed ^ p.stat("raids"), p.prog.rank(), key);
    }

    private static boolean pair(String a, String b, String p, String q) { return a.equals(p) && b.equals(q) || a.equals(q) && b.equals(p); }
}
