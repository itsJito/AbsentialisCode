package absentialis.scene;

import absentialis.world.District;

/** The names of sounds that depend on where the listener is (0.8). */
public final class SoundNames {
    private SoundNames() {}

    /** The bed of a district, without its "amb_" prefix: its own recording. The wilderness between them has the wind. */
    public static String ambience(District d) {
        if (d == null) return "wind";
        return switch (d) {
            case OLD_QUARTER -> "district_downtown"; case MARKET -> "district_residential"; case INDUSTRIAL -> "district_industrial";
            case CIVIC -> "district_munincipal"; case REDLINE -> "district_barrens"; default -> "wind";
        };
    }
}
