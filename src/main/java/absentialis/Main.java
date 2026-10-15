package absentialis;

import absentialis.engine.Window;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ABSENTIALIS bootstrap. Initialises configuration, the window/GL context and hands control to the
 * scene-level application. Contains no gameplay logic.
 *
 * Failures are never silent: GLFW errors are printed by the error callback (see {@link Window}),
 * init/creation failures throw, and any fatal exception is printed and written to crash.log.
 *
 * Test environment variables:
 *   ABS_SELFTEST=all|inventory|weapon|progress|econ|save|death|extraction|noise|tier|soak
 *                      run the quality gates without a window and exit (non-zero on failure)
 *   ABS_TEST_INVENTORY / ABS_TEST_EXTRACTION / ABS_TEST_NOISE / ABS_TEST_SOAK = 1
 *                      run that gate before the window opens and print the result
 *   ABS_AUTOSHOT=dir   scripted run: title, hideout tabs, raid, overlays, debrief -> PNGs, then exit
 *   ABS_PREVIEW=dir    render fixed map preview shots and exit
 */
public final class Main {
    public static void main(String[] args) {
        String jv = System.getProperty("java.version");
        System.out.println("[Boot] ABSENTIALIS on Java " + jv + " (" + System.getProperty("java.vendor") + ")");
        int major = Runtime.version().feature();
        if (major != 21) System.err.println("[Boot] WARNING: built for Java 21; LWJGL 3.3.3 does not support Java " + major
                + ". Run with .\\gradlew.bat run (it provisions a Java 21 toolchain).");
        try {
            // ABS_SAVES=dir keeps automated runs away from the player's real profiles
            String saves = System.getenv("ABS_SAVES");
            if (saves != null && !saves.isBlank()) absentialis.persistence.SaveSystem.dir = Path.of(saves);
            // ABS_ATLAS=file.png paints every block texture without a window and writes the sheet (texture review)
            String sheet = System.getenv("ABS_ATLAS");
            if (sheet != null && !sheet.isBlank()) {
                absentialis.engine.render.Atlas at = new absentialis.engine.render.Atlas();
                at.paintAll();
                String only = System.getenv("ABS_ATLAS_NAMES");
                if (only != null && !only.isBlank()) at.dumpSheet(sheet, only.split(",")); else at.dumpPng(sheet);
                StringBuilder idx = new StringBuilder();
                at.layerOf.forEach((k, v) -> idx.append(v).append(' ').append(k).append(' ').append(at.variantsOf.get(k)).append(System.lineSeparator()));
                java.nio.file.Files.writeString(Path.of(sheet + ".txt"), idx.toString());
                System.out.println("[Atlas] " + at.pixels.size() + " tiles -> " + sheet + (at.missing.isEmpty() ? "" : "  MISSING " + at.missing));
                return;
            }
            String selftest = System.getenv("ABS_SELFTEST");
            if (selftest != null && !selftest.isBlank()) {
                int failed = absentialis.tools.SelfTests.runHeadless(selftest);
                System.exit(failed == 0 ? 0 : 1);
                return;
            }
            StringBuilder gates = new StringBuilder();
            for (String g : new String[]{"INVENTORY", "EXTRACTION", "NOISE", "SOAK"}) if ("1".equals(System.getenv("ABS_TEST_" + g))) gates.append(g.toLowerCase()).append(',');
            if (gates.length() > 0) absentialis.tools.SelfTests.runHeadless(gates.toString());

            Window win = new Window();
            win.create(Version.TITLE, 1600, 900);
            String preview = System.getenv("ABS_PREVIEW");
            if (preview != null && !preview.isBlank()) {
                absentialis.tools.MapPreview.run(win, preview);
                win.destroy();
                return;
            }
            try {
                new absentialis.scene.GameApp(win).run();
            } finally {
                win.destroy();
            }
            System.exit(0);
        } catch (Throwable t) {
            crash(t);
            System.exit(2);
        }
    }

    private static void crash(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        System.err.println("[FATAL] " + t);
        System.err.println(sw);
        String report = Version.TITLE + " crash on Java " + System.getProperty("java.version") + " at " + java.time.LocalDateTime.now().withNano(0) + "\n" + sw;
        try {
            Files.writeString(Path.of("crash.log"), report);
            System.err.println("[FATAL] Details written to " + Path.of("crash.log").toAbsolutePath());
        } catch (Exception ignored) {}
        // 0.8: crash.log is the latest one; every one is also kept, in order, so that a second crash does not erase the first
        try {
            Files.writeString(Path.of("crash-history.log"), report + "\n" + "-".repeat(72) + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }
}
