package absentialis.scene;

import absentialis.gameplay.inventory.Container;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.items.ItemInstance;
import absentialis.gameplay.missions.Missions;
import absentialis.gameplay.missions.Story;
import absentialis.world.MapData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The five investigations as they exist inside an ordinary operation (0.8; lore.md section 7). There is no mission
 * map and no script: each investigation has two sites - two of the landmarks the city already has - and at each of
 * them one piece of evidence that is really there, in one of the containers of that place, whether or not anyone
 * has heard the rumour. This class knows where the sites are, puts each piece of evidence where it lies, and notices
 * three things: the survivor standing on a site, the survivor holding (or reading) a piece of evidence, and nothing
 * else. Everything that follows from those is {@link Missions}.
 *
 * Evidence already seen is not put out again - knowledge is not regenerated as loot - and a mission that is closed
 * leaves its sites as they are.
 */
public final class StoryDirector {
    /** One site: a landmark's lot, the evidence that lies there, and how many of its containers have been gone through. */
    public static final class Site {
        public Story.Mission mission;
        public String landmark, evidence;
        public int x0, z0, x1, z1, opened;
        public boolean placed;
        public boolean contains(float x, float z) { return x >= x0 - MARGIN && x <= x1 + MARGIN && z >= z0 - MARGIN && z <= z1 + MARGIN; }
    }

    /** How far outside a landmark's lot still counts as being there, and the container of a site by which its evidence is certain to have turned up. */
    public static int MARGIN = 4, CERTAIN_BY = 4;

    private final Raid raid;
    public final List<Site> sites = new ArrayList<>();
    private float tick;
    /** Lines waiting to be shown: {text, sound}. */
    public final List<String[]> notices = new ArrayList<>();

    public StoryDirector(Raid raid) {
        this.raid = raid;
        Story.load();
        if (raid.world.dev) return;                                    // the developer map has no city, and no story
        Map<String, MapData.Landmark> byId = new HashMap<>();
        for (MapData.Landmark lm : raid.world.map.anchors) byId.put(lm.id, lm);
        for (Story.Mission m : Story.MISSIONS) for (int i = 0; i < m.sites.size() && i < m.evidence.size(); i++) {
            MapData.Landmark lm = byId.get(m.sites.get(i));
            if (lm == null) continue;
            Site s = new Site();
            s.mission = m; s.landmark = lm.name; s.evidence = m.evidence.get(i);
            s.x0 = Math.min(lm.x0, lm.x1); s.x1 = Math.max(lm.x0, lm.x1); s.z0 = Math.min(lm.z0, lm.z1); s.z1 = Math.max(lm.z0, lm.z1);
            sites.add(s);
        }
    }

    public Site siteAt(float x, float z) { for (Site s : sites) if (s.contains(x, z)) return s; return null; }

    private Missions missions() { return raid.profile.missions; }

    /**
     * A container has been opened for the first time. If it stands on a site whose evidence has not been seen and
     * has not been put out yet, this may be the one it is in: one container in three by its own position, and the
     * {@link #CERTAIN_BY}th gone through on that site for certain - so the proof takes a search to find and cannot
     * be missed by a thorough one.
     */
    public void onContainer(int x, int y, int z, Container c) {
        Site s = siteAt(x + 0.5f, z + 0.5f);
        if (s == null || s.placed) return;
        Story.Evidence e = Story.evidence(s.evidence);
        if (e == null || missions().of(s.mission.id).seen[e.index()] || missions().stage(s.mission.id) == Missions.Stage.CLOSED) return;
        s.opened++;
        long h = (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L) ^ raid.profile.seed;
        if (s.opened < CERTAIN_BY && Math.floorMod(h, 3) != 0) return;
        ItemInstance it = new ItemInstance(s.evidence);
        it.origin = "story"; it.foundInRaid = true;
        if (!InventoryService.add(it, c).ok()) { c.h += 1; if (!InventoryService.add(it, c).ok()) return; }
        s.placed = true;
    }

    /** Twice a second: is the survivor on a site, and are they carrying anything that is evidence? */
    public void update(float dt) {
        if (sites.isEmpty()) return;
        tick -= dt;
        if (tick > 0) return;
        tick = 0.5f;
        int day = raid.profile.zone.day;
        Site here = siteAt(raid.player.x, raid.player.z);
        if (here != null && !raid.player.dead) missions().onSite(here.mission, day);
        for (ItemInstance i : raid.profile.loadout.all()) if (i.defId.startsWith("story.")) missions().onEvidence(i.defId, day);
        for (String[] n : missions().drain()) {
            boolean lead = n[1].equals("INVESTIGATING");
            notices.add(new String[]{n[2], lead ? "mission_accepted" : "file_scroll"});
        }
    }

    /** Where a mission's unseen evidence lies, as places a map can mark: {x, z} of the middle of each site still worth going to. */
    public List<float[]> leads() {
        List<float[]> l = new ArrayList<>();
        for (Site s : sites) {
            Missions.State st = missions().of(s.mission.id);
            Story.Evidence e = Story.evidence(s.evidence);
            if (st.stage == Missions.Stage.RUMOUR || st.stage == Missions.Stage.DECISION || st.stage == Missions.Stage.CLOSED || e == null || st.seen[e.index()]) continue;
            l.add(new float[]{(s.x0 + s.x1) / 2f, (s.z0 + s.z1) / 2f});
        }
        return l;
    }
}
