package absentialis.sim;

import absentialis.gameplay.progression.Progression;
import absentialis.gameplay.squad.Roster;
import absentialis.persistence.Profile;
import absentialis.rivalry.Doctrine;
import absentialis.rivalry.RivalryState;
import java.util.ArrayList;
import java.util.List;

/**
 * Who is alive in the Zone and what they have done (0.6: the LEADERBOARD tab of the deployment screen). One ladder
 * for everybody - the player, their squad, every group's leader and lieutenants, and every named member of every
 * faction: Rank from experience, kills, and where they stand. The dead are not on it.
 *
 * Nothing is invented for the table: a row is a view of a record that already exists (the profile, the roster, the
 * rivalry state, the A-Life agents), so what it shows is what the world holds.
 */
public final class Leaderboard {
    private Leaderboard() {}

    /** kind: "you", "squad", "leader", "agent". */
    public record Row(String name, String title, int rank, long xp, String faction, int kills, String detail, String kind) {}

    public static List<Row> rows(Profile p) {
        List<Row> l = new ArrayList<>();
        long pxp = p.prog.lifetimeXp;
        int pk = 0;
        for (var e : p.stats.entrySet()) if (e.getKey().startsWith("kills_")) pk += e.getValue();
        l.add(new Row(p.name, p.prog.title(), p.prog.rank(), pxp, p.alliance.isEmpty() ? "player" : p.alliance, pk, p.stat("extracts") + " extractions, " + p.stat("raids") + " operations", "you"));
        for (Roster.Mate m : p.squad.mates)
            l.add(new Row(m.name, m.title(), m.level(), m.xp, p.alliance.isEmpty() ? "player" : p.alliance, m.kills, m.raids + " operations with you" + (m.active ? "" : " (reserve)"), "squad"));
        for (RivalryState.Leader x : p.rivalry.leaders.values()) {
            if (!x.alive) continue;
            RivalryState.Group g = p.rivalry.group(x.groupId);
            if (g == null || !g.active) continue;
            long xp = Progression.xpToReach(Math.max(1, Math.min(50, x.rank))) + x.encounters * 400L;
            String role = "leader".equals(x.role) ? "leads " + g.name : "lieutenant".equals(x.role) ? "second in " + g.name : "of " + g.name;
            l.add(new Row((x.title.isEmpty() ? "" : x.title + " ") + x.name, Progression.rankName(Math.min(50, x.rank)) + " " + Math.min(50, x.rank), Math.min(50, x.rank), xp, Doctrine.gameKey(g.faction), 0, role + (x.captured ? " (captured)" : ""), "leader"));
        }
        for (Agents.Agent a : p.agents.agents) {
            if (!a.alive || a.infected()) continue;
            l.add(new Row(a.name, a.title(), a.rank(), a.xp(), a.faction, a.kills, a.role + (a.night ? ", night shift" : ""), "agent"));
        }
        l.sort((a, b) -> b.xp() != a.xp() ? Long.compare(b.xp(), a.xp()) : a.name().compareTo(b.name()));
        return l;
    }

    /** How many people of each kind the table holds: {you and squad, leaders, agents}. */
    public static int[] counts(List<Row> rows) {
        int[] c = new int[3];
        for (Row r : rows) c["you".equals(r.kind()) || "squad".equals(r.kind()) ? 0 : "leader".equals(r.kind()) ? 1 : 2]++;
        return c;
    }
}
