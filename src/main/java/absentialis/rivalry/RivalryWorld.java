package absentialis.rivalry;

import absentialis.persistence.Profile;
import absentialis.world.District;
import java.util.ArrayList;
import java.util.List;

/**
 * Where the rivalry system meets the rest of the game: the Zone's districts as the places groups hold, the profile's
 * coins as the purse treaties and bribes are paid from, and one engine per profile. Everything the engine needs to
 * know about the world comes through here, so the rules themselves ({@link RivalryEngine}) depend on nothing but
 * their own state and data.
 */
public final class RivalryWorld {
    private RivalryWorld() {}

    /** The five developed districts and the Wilderness, by enum name (the stable save key). */
    public static final RivalryEngine.Locations DISTRICTS = new RivalryEngine.Locations() {
        public List<String> ids() { List<String> l = new ArrayList<>(); for (District d : District.CITY) l.add(d.name()); l.add(District.WILDERNESS.name()); return l; }
        public float influence(String location, String gameFaction) {
            District d = District.byName(location);
            if (d == null) return 0;
            return d.def.influence(gameFaction) + (d.def.homeOf(gameFaction) ? 0.5f : 0) - (d == District.WILDERNESS ? 0.2f : 0);
        }
        /** Downtown is the middle of the city: one hop from every other district. The outer districts are two hops from each other, the Wilderness two from everything. */
        public int distance(String a, String b) {
            if (a.equals(b)) return 0;
            if (a.equals(District.WILDERNESS.name()) || b.equals(District.WILDERNESS.name())) return 2;
            return a.equals(District.OLD_QUARTER.name()) || b.equals(District.OLD_QUARTER.name()) ? 1 : 2;
        }
        public String name(String location) { District d = District.byName(location); return d == null ? location : d.display; }
    };

    /** The engine for a profile's rivalry state, seeded on first use from the profile's own seed. */
    public static RivalryEngine engine(Profile p) {
        RivalryEngine e = new RivalryEngine(p.rivalry, DISTRICTS);
        e.ensureSeeded(p.seed ^ 0x5157A1D0C0DEL);
        return e;
    }

    public static RivalryEngine.Wallet wallet(Profile p) {
        return new RivalryEngine.Wallet() {
            public long coins() { return p.scrip; }
            public boolean pay(int amount) { if (amount < 0 || p.scrip < amount) return false; p.scrip -= amount; return true; }
            public void receive(int amount) { if (amount > 0) p.scrip += amount; }
        };
    }
}
