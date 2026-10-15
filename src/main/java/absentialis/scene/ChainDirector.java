package absentialis.scene;

import absentialis.entity.Actor;
import absentialis.gameplay.inventory.Container;
import absentialis.gameplay.missions.Chains;
import absentialis.world.District;
import absentialis.world.MapData;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.List;

/**
 * The faction missions in an operation (1.0): where the four advisors stand, what they say when they are spoken to,
 * and whether the thing a mission is about has been found yet.
 *
 * An advisor is a person in the world - outside the place their faction keeps, the same place every time - and
 * nothing is taken from a board: the survivor walks up and talks. They cannot be killed and do not fight (they are
 * the fixed point of their chain; lore.md has no room for one who dies and is replaced). They are only made real
 * while the survivor is near, like everyone else. On the developer map all four stand by the road, so the whole of
 * a chain can be walked through there.
 */
public final class ChainDirector {
    public static final class Post {
        public Chains.Advisor advisor;
        public float x, y, z;
        public Actor actor;
        public String siteName = "";
        public int opened;
    }

    private final Raid raid;
    public final List<Post> posts = new ArrayList<>();
    /** Lines for the HUD, each with a sound: {text, sound}. */
    public final List<String[]> notices = new ArrayList<>();
    private float tick;
    public static float NEAR = 70f, FAR = 110f, REACH = 2.8f;
    public int talks, resolved;

    public ChainDirector(Raid raid) { this.raid = raid; Chains.load(); }

    private boolean placed;

    /** Where each advisor stands: worked out once the operation has begun and the survivor is in the world. */
    private void place() {
        placed = true;
        int i = 0;
        for (Chains.Advisor a : Chains.ADVISORS) {
            Post p = new Post();
            p.advisor = a;
            if (raid.world.dev) {
                // the developer map: in a row beside where the survivor comes in
                float[] at = raid.outdoorSpot((int) raid.player.x + 4 + i * 3, (int) raid.player.z - 6);
                p.x = at[0]; p.y = at[1]; p.z = at[2]; p.siteName = "the developer map";
            } else {
                MapData.Landmark lm = landmark(a.site());
                if (lm == null) { i++; continue; }
                // outside it, on the side nearest the middle of the map (the way most people come at it)
                int x0 = Math.min(lm.x0, lm.x1), x1 = Math.max(lm.x0, lm.x1), z0 = Math.min(lm.z0, lm.z1), z1 = Math.max(lm.z0, lm.z1);
                float cx = (x0 + x1) / 2f, cz = (z0 + z1) / 2f, dx = World.W / 2f - cx, dz = World.D / 2f - cz;
                int px = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? x1 + 5 : x0 - 5) : (int) cx + 3, pz = Math.abs(dx) > Math.abs(dz) ? (int) cz + 3 : (dz > 0 ? z1 + 5 : z0 - 5);
                float[] at = raid.outdoorSpot(px, pz);
                p.x = at[0]; p.y = at[1]; p.z = at[2]; p.siteName = lm.name;
            }
            posts.add(p);
            i++;
        }
    }

    private Chains chains() { return raid.profile.chains; }

    private MapData.Landmark landmark(String id) { for (MapData.Landmark lm : raid.world.map.anchors) if (lm.id.equals(id)) return lm; return null; }

    /** What a site is called to someone being sent there. */
    public String siteName(String site) {
        if (raid.world.dev) return "any container on the developer map";
        if (site.startsWith("district:")) { District d = District.byName(site.substring(9)); return "a building in " + (d == null ? site.substring(9) : d.display); }
        MapData.Landmark lm = landmark(site);
        return lm == null ? site : lm.name;
    }

    /** True if a place is on a site. */
    public boolean atSite(String site, float x, float z) {
        if (raid.world.dev) return true;
        if (site.startsWith("district:")) {
            District d = District.byName(site.substring(9));
            if (d == null || raid.world.district(x, z) != d) return false;
            for (MapData.Landmark lm : raid.world.map.anchors) if (x >= Math.min(lm.x0, lm.x1) - 2 && x <= Math.max(lm.x0, lm.x1) + 2 && z >= Math.min(lm.z0, lm.z1) - 2 && z <= Math.max(lm.z0, lm.z1) + 2) return false;
            return true;
        }
        MapData.Landmark lm = landmark(site);
        return lm != null && x >= Math.min(lm.x0, lm.x1) - 4 && x <= Math.max(lm.x0, lm.x1) + 4 && z >= Math.min(lm.z0, lm.z1) - 4 && z <= Math.max(lm.z0, lm.z1) + 4;
    }

    /** Twice a second: the advisors near enough to matter are made real; the ones left far behind are put away. */
    public void update(float dt) {
        if (!placed) { if (raid.time < 0.5f) return; place(); }
        tick -= dt;
        for (Post p : posts) if (p.actor != null && !p.actor.dead) {
            // they stay where they are, and they look at whoever comes up to them
            Actor a = p.actor;
            a.vx = a.vz = 0; a.x += (p.x - a.x) * Math.min(1, dt * 2); a.z += (p.z - a.z) * Math.min(1, dt * 2);
            a.hp = a.maxHp; a.hostileToPlayer = false;
            float d = a.dist(raid.player);
            if (d < 9) a.yaw = absentialis.neuralai.integration.Steering.turn(a.yaw, (float) Math.atan2(-(raid.player.x - a.x), -(raid.player.z - a.z)), dt * 4);
        }
        if (tick > 0) return;
        tick = 0.5f;
        for (Post p : posts) {
            float d = (float) Math.sqrt((p.x - raid.player.x) * (p.x - raid.player.x) + (p.z - raid.player.z) * (p.z - raid.player.z));
            boolean there = p.actor != null && raid.actors.contains(p.actor) && !p.actor.dead;
            if (!there && d < NEAR) {
                Actor a = raid.debugSpawn(p.advisor.faction(), p.x, p.z, p.y);
                if (a == null) continue;
                a.name = p.advisor.name(); a.advisor = true; a.hostileToPlayer = false; a.aggressor = false;
                a.yaw = (float) Math.atan2(-(raid.player.x - a.x), -(raid.player.z - a.z));
                raid.frozen.add(a.id);
                p.actor = a;
            } else if (there && d > FAR) { raid.actors.remove(p.actor); raid.frozen.remove(p.actor.id); p.actor = null; }
            else if (!there) p.actor = null;
        }
    }

    /** The advisor the survivor is standing in front of, or null. */
    public Post near() {
        Post best = null; float bd = REACH;
        for (Post p : posts) { if (p.actor == null || p.actor.dead) continue; float d = p.actor.dist(raid.player); if (d < bd) { bd = d; best = p; } }
        return best;
    }

    public String prompt() { Post p = near(); return p == null ? null : "[F] Talk to " + p.advisor.name(); }

    private static String effects(Chains.Choice c) {
        StringBuilder sb = new StringBuilder();
        if (c.cash() != 0) sb.append(c.cash() > 0 ? "+" : "-").append(absentialis.Version.coins(Math.abs(c.cash()))).append("    ");
        for (var e : c.rep().entrySet()) sb.append(absentialis.gameplay.Alliance.name(e.getKey())).append(e.getValue() > 0 ? " +" : " ").append(Math.round(e.getValue())).append("    ");
        return sb.toString().trim();
    }

    /** The survivor has walked up and spoken. Puts the right question in the window. */
    public void talk(Post p, ChoicePopup popup) {
        Chains cs = chains();
        Chains.Advisor a = p.advisor;
        Chains.State st = cs.of(a.chain());
        Chains.Mission m = cs.current(a.chain());
        String who = a.name() + "  -  " + a.role();
        talks++;
        boolean first = !st.met;
        st.met = true;
        if (m == null) { popup.show(a.name(), a.role(), a.done(), i -> { }).option("Leave", "").escapes(); return; }
        String head = m.n + " of " + Chains.length(a.chain()) + ":  " + m.title;
        switch (st.stage) {
            case OFFERED -> popup.show(head, who, (first ? a.greeting() + "\n\n" : "") + m.brief, i -> {
                        if (i != 0) return;
                        cs.accept(a.chain());
                        p.opened = 0;
                        notices.add(new String[]{"TAKEN: " + m.title + " - go to " + siteName(m.site) + " and search for " + m.find + ".", "mission_accepted"});
                    })
                    .option("Take the work", "Go to " + siteName(m.site) + ". Search it for " + m.find + ". Then come back here, in person.", "good")
                    .option("Not now", "The offer stands. Nothing is lost by leaving it.", "dim").escapes();
            case ACTIVE -> popup.show(head, who, "You have not found it yet.\n\n" + m.brief + "\n\nIt is at " + siteName(m.site) + ": " + m.find + ". Go through what is there - a careful search cannot miss it.", i -> { })
                    .option("Still on it", "", "dim").escapes();
            case FOUND -> {
                List<Chains.Choice> open = cs.open(m);
                popup.show(head, who, "You found " + m.find + ".\n\n" + m.discovery + "\n\n" + m.ask, i -> {
                    if (i >= open.size()) return;
                    Chains.Choice c = cs.resolve(a.chain(), open.get(i).id());
                    if (c == null) return;
                    resolved++;
                    raid.profile.scrip = Math.max(0, raid.profile.scrip + c.cash());
                    for (var e : c.rep().entrySet()) raid.profile.addRep(e.getKey(), e.getValue());
                    long xp = Chains.xp();
                    raid.profile.prog.addXp(xp);
                    notices.add(new String[]{"SETTLED: " + m.title + "  (+" + String.format("%,d", xp) + " XP)", "mission_accepted"});
                    boolean over = cs.complete(a.chain());
                    popup.show(m.title, who, c.outcome() + (m.lead.isEmpty() ? "" : "\n\n" + m.lead) + (over ? "\n\n" + a.done() : ""), k -> { }).option(over ? "Leave" : "Continue", over ? "" : "Speak to " + a.name() + " again for what comes next.", "dim").escapes();
                });
                for (Chains.Choice c : open) popup.option(c.label(), effects(c));
                popup.option("Decide later", "Nothing is settled until you say so.", "dim").escapes();
            }
        }
    }

    /** A container has been opened for the first time. On the site of a mission that is under way, it may be the one. */
    public void onContainer(int x, int y, int z, Container c) {
        Chains cs = chains();
        for (Post p : posts) {
            Chains.State st = cs.of(p.advisor.chain());
            Chains.Mission m = cs.current(p.advisor.chain());
            if (m == null || st.stage != Chains.Stage.ACTIVE || !atSite(m.site, x + 0.5f, z + 0.5f)) continue;
            p.opened++;
            long h = (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L) ^ raid.profile.seed ^ m.id.hashCode();
            if (p.opened < 3 && Math.floorMod(h, 3) != 0) continue;
            cs.found(p.advisor.chain());
            notices.add(new String[]{"FOUND: " + m.find + ".  Report to " + p.advisor.name() + " in person.", "file_scroll"});
        }
    }

    /** Places for a map: {x, z, kind} - 0 an advisor with nothing new to say, 1 an advisor worth going to, 2 a site to search. */
    public List<float[]> marks() {
        List<float[]> l = new ArrayList<>();
        Chains cs = chains();
        for (Post p : posts) {
            Chains.State st = cs.of(p.advisor.chain());
            Chains.Mission m = cs.current(p.advisor.chain());
            l.add(new float[]{p.x, p.z, m != null && st.stage != Chains.Stage.ACTIVE ? 1 : 0});
            if (m != null && st.stage == Chains.Stage.ACTIVE && !m.site.startsWith("district:") && !raid.world.dev) { MapData.Landmark lm = landmark(m.site); if (lm != null) l.add(new float[]{(lm.x0 + lm.x1) / 2f, (lm.z0 + lm.z1) / 2f, 2}); }
        }
        return l;
    }
}
