package absentialis.gameplay.progression;

import absentialis.persistence.Profile;
import java.util.List;

/**
 * Service challenges shown on the STAT page (addendum P2.4): long-running goals tracked from
 * profile statistics. Completing one is claimed once for XP; claims survive prestige (they are
 * service record, not enlisted access).
 */
public final class Challenges {
    private Challenges() {}

    public record Challenge(String id, String label, String stat, int goal, int xp) {}

    public static final List<Challenge> ALL = List.of(
            new Challenge("first_out", "Get out alive", "extracts", 1, 3000),
            new Challenge("regular", "Extract 10 times", "extracts", 10, 20000),
            new Challenge("survivor", "Extract 50 times", "extracts", 50, 90000),
            new Challenge("cartographer", "Reconnoitre 600 map cells", "cells", 600, 25000),
            new Challenge("scavenger", "Search 150 containers", "containers", 150, 20000),
            new Challenge("exterminator", "Kill 100 infected", "kills_infected", 100, 30000),
            new Challenge("archivist", "Read 15 documents", "documents", 15, 15000),
            new Challenge("lineman", "Restore district power", "power", 1, 12000),
            new Challenge("veteran", "Deploy 25 times", "raids", 25, 15000));

    public static int progress(Profile p, Challenge c) { return Math.min(c.goal(), p.stat(c.stat())); }
    public static boolean claimed(Profile p, Challenge c) { return p.stat("ch_" + c.id()) > 0; }
    public static boolean claimable(Profile p, Challenge c) { return !claimed(p, c) && progress(p, c) >= c.goal(); }

    /** Claims the reward once. Returns the XP granted (0 if not claimable). */
    public static int claim(Profile p, Challenge c) {
        if (!claimable(p, c)) return 0;
        p.stats.put("ch_" + c.id(), 1);
        p.prog.addXp(c.xp());
        return c.xp();
    }
}
