package absentialis.world.gen;

import absentialis.engine.util.Json;
import absentialis.world.District;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generation data (0.5): district definitions from {@code content/districts.json} and building archetypes from
 * {@code content/buildings.json}. Nothing in the generator names a district, a landmark or a building type in
 * code paths that decide the layout - it asks these tables.
 */
public final class GenDefs {
    /** One room of an archetype's programme. */
    public static final class RoomDef {
        public String type;
        public float weight = 2;
        public int min = 3, band = -1;
        /** "" = door to the corridor, "prev" = door to the previous room only, "both" = both. */
        public String link = "";
    }

    public static final class Archetype {
        public String id, plan = "spine", orient = "auto", style = "suburban", roof = "flat", door = "wood", loot;
        public int floorsMin = 1, floorsMax = 1, corridor = 2, windows = 3, tall = 1;
        public int wMin = 8, wMax = 14, dMin = 8, dMax = 12;
        public boolean back, shopfront, curtain, balconies;
        public List<RoomDef> ground = new ArrayList<>(), upper = new ArrayList<>(), top = new ArrayList<>();
        public List<RoomDef> rooms(int floor, int floors) {
            if (floor == 0) return ground;
            if (floor == floors - 1 && !top.isEmpty()) return top;
            return upper.isEmpty() ? ground : upper;
        }
    }

    public static final class Support {
        public String type, yard = "none";
        public float weight = 1;
        public int lotMin = 14, lotMax = 18;
    }

    public static final class LandmarkDef {
        public String id, name, half = "any", faction;
        public int w = 60;
    }

    public static final class DistrictDef {
        public District district;
        public boolean center, permits;
        public float flat, green, highway;
        public String faction = "", hq = "", ground = "sidewalk";
        public final List<LandmarkDef> landmarks = new ArrayList<>();
        public final List<Support> supports = new ArrayList<>();
        public int blockMin = 48, blockMax = 66, front = 3, side = 2, rear = 2;
        public float damage = 0.1f, boarded = 0.1f, lit = 0.5f;
        public LandmarkDef landmark(String id) { for (LandmarkDef l : landmarks) if (l.id.equals(id)) return l; return null; }
    }

    public int arterial = 12, ring = 8, slotMin = 164, slotMax = 176, spine = 10, cross = 8, alley = 3;
    public final Map<District, DistrictDef> districts = new LinkedHashMap<>();
    public final Map<String, Archetype> archetypes = new LinkedHashMap<>();

    private static GenDefs instance;

    public static synchronized GenDefs get() {
        if (instance == null) instance = load();
        return instance;
    }

    public DistrictDef of(District d) { return districts.get(d); }
    public Archetype archetype(String id) {
        Archetype a = archetypes.get(id);
        if (a == null) throw new IllegalStateException("Unknown building archetype '" + id + "' (content/buildings.json)");
        return a;
    }

    private static String read(String path) {
        try (InputStream in = GenDefs.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read " + path, e); }
    }

    @SuppressWarnings("unchecked")
    private static GenDefs load() {
        GenDefs g = new GenDefs();
        Map<String, Object> b = Json.parseObject(read("/content/buildings.json"));
        for (Object o : Json.list(b, "archetypes")) {
            Map<String, Object> m = (Map<String, Object>) o;
            Archetype a = new Archetype();
            a.id = Json.str(m, "id", "");
            a.plan = Json.str(m, "plan", "spine"); a.orient = Json.str(m, "orient", "auto"); a.style = Json.str(m, "style", "suburban");
            a.roof = Json.str(m, "roof", "flat"); a.door = Json.str(m, "door", "wood"); a.loot = Json.str(m, "loot", null);
            List<Object> fl = Json.list(m, "floors");
            if (fl != null && fl.size() == 2) { a.floorsMin = ((Number) fl.get(0)).intValue(); a.floorsMax = ((Number) fl.get(1)).intValue(); }
            a.corridor = Json.integer(m, "corridor", 2); a.windows = Json.integer(m, "windows", 3); a.tall = Json.integer(m, "tall", 1);
            a.back = Json.bool(m, "back", false); a.shopfront = Json.bool(m, "shopfront", false); a.curtain = Json.bool(m, "curtain", false); a.balconies = Json.bool(m, "balconies", false);
            Map<String, Object> foot = Json.obj(m, "foot");
            if (foot.containsKey("w")) {
                List<Object> w = Json.list(foot, "w"), d = Json.list(foot, "d");
                a.wMin = ((Number) w.get(0)).intValue(); a.wMax = ((Number) w.get(1)).intValue();
                a.dMin = ((Number) d.get(0)).intValue(); a.dMax = ((Number) d.get(1)).intValue();
            }
            rooms(m, "ground", a.ground); rooms(m, "upper", a.upper); rooms(m, "top", a.top);
            g.archetypes.put(a.id, a);
        }
        Map<String, Object> d = Json.parseObject(read("/content/districts.json"));
        Map<String, Object> lay = Json.obj(d, "layout");
        {
            g.arterial = Json.integer(lay, "arterial", 12); g.ring = Json.integer(lay, "ring", 8); g.spine = Json.integer(lay, "spine", 10);
            g.cross = Json.integer(lay, "cross", 8); g.alley = Json.integer(lay, "alley", 3);
            List<Object> sd = Json.list(lay, "slotDepth");
            if (sd.size() == 2) { g.slotMin = ((Number) sd.get(0)).intValue(); g.slotMax = ((Number) sd.get(1)).intValue(); }
        }
        for (Object o : Json.list(d, "districts")) {
            Map<String, Object> m = (Map<String, Object>) o;
            DistrictDef dd = new DistrictDef();
            dd.district = District.valueOf(Json.str(m, "id", ""));
            Object slot = m.get("slot");
            if (slot instanceof String) dd.center = "center".equals(slot);
            else if (slot instanceof Map) { Map<String, Object> s = (Map<String, Object>) slot; dd.flat = (float) Json.num(s, "flat", 0); dd.green = (float) Json.num(s, "green", 0); dd.highway = (float) Json.num(s, "highway", 0); }
            dd.faction = Json.str(m, "faction", ""); dd.hq = Json.str(m, "hq", ""); dd.ground = Json.str(m, "ground", "sidewalk"); dd.permits = Json.bool(m, "permits", false);
            for (Object lo : Json.list(m, "landmarks")) {
                Map<String, Object> lm = (Map<String, Object>) lo;
                LandmarkDef l = new LandmarkDef();
                l.id = Json.str(lm, "id", ""); l.name = Json.str(lm, "name", l.id); l.half = Json.str(lm, "half", "any"); l.w = Json.integer(lm, "w", 60);
                l.faction = Json.str(lm, "faction", dd.faction);
                dd.landmarks.add(l);
            }
            if (dd.landmarks.size() != 2) throw new IllegalStateException("District " + dd.district + " must define exactly two landmarks, found " + dd.landmarks.size());
            if (dd.landmark(dd.hq) == null) throw new IllegalStateException("District " + dd.district + ": headquarters '" + dd.hq + "' is not one of its landmarks");
            List<Object> blk = Json.list(m, "block");
            if (blk.size() == 2) { dd.blockMin = ((Number) blk.get(0)).intValue(); dd.blockMax = ((Number) blk.get(1)).intValue(); }
            Map<String, Object> sb = Json.obj(m, "setback");
            { dd.front = Json.integer(sb, "front", 3); dd.side = Json.integer(sb, "side", 2); dd.rear = Json.integer(sb, "rear", 2); }
            dd.damage = (float) Json.num(m, "damage", 0.1); dd.boarded = (float) Json.num(m, "boarded", 0.1); dd.lit = (float) Json.num(m, "lit", 0.5);
            for (Object so : Json.list(m, "supports")) {
                Map<String, Object> sm = (Map<String, Object>) so;
                Support s = new Support();
                s.type = Json.str(sm, "type", ""); s.yard = Json.str(sm, "yard", "none"); s.weight = (float) Json.num(sm, "weight", 1);
                List<Object> lw = Json.list(sm, "lotW");
                if (lw.size() == 2) { s.lotMin = ((Number) lw.get(0)).intValue(); s.lotMax = ((Number) lw.get(1)).intValue(); }
                dd.supports.add(s);
            }
            g.districts.put(dd.district, dd);
        }
        for (District ds : District.CITY) if (!g.districts.containsKey(ds)) throw new IllegalStateException("content/districts.json has no entry for " + ds);
        return g;
    }

    @SuppressWarnings("unchecked")
    private static void rooms(Map<String, Object> m, String key, List<RoomDef> out) {
        List<Object> l = Json.list(m, key);
        for (Object o : l) {
            Map<String, Object> rm = (Map<String, Object>) o;
            RoomDef r = new RoomDef();
            r.type = Json.str(rm, "t", "storage"); r.weight = (float) Json.num(rm, "w", 2); r.min = Json.integer(rm, "min", 3);
            r.band = Json.integer(rm, "band", -1); r.link = Json.str(rm, "link", "");
            out.add(r);
        }
    }
}
