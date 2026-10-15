package absentialis;

/**
 * Build identity and the currency format: the one place the version is written. The title screen, the hideout,
 * the HUD footer, the window title, crash reports and save files all read it from here. This iteration is Version 1.3.
 *
 * The game runs on the Astratto Engine (see {@link absentialis.engine.Astratto}).
 */
public final class Version {
    private Version() {}

    public static final String NUMBER = "1.3";
    public static final String LABEL = "Version " + NUMBER;
    public static final String TITLE = "ABSENTIALIS " + NUMBER;
    /** Save-format generation written into profiles so a later build can tell what produced a file. */
    public static final int SAVE_FORMAT = 8;
    /** Short build stamp for the HUD corner, crash reports and the window title: "v1.0". */
    public static final String SHORT = "v" + NUMBER;

    /** 0.7: the Zone trades in cash. */
    public static final char COIN = '$';
    /** What the money is called, in a sentence and on a label. */
    public static final String MONEY = "cash", MONEY_CAP = "Cash", MONEY_UP = "CASH";

    /** "$12,500" */
    public static String coins(long amount) { return COIN + String.format("%,d", amount); }
}
