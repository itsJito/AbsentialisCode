package absentialis.persistence;

import absentialis.engine.util.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Save safety (GDD 20.5): each profile is written to a temp file, verified by re-parsing, then
 * atomically moved over the live file; the previous save is kept as .bak and used if the live
 * file is unreadable. Profiles are isolated files - nothing is shared between slots.
 */
public final class SaveSystem {
    public static final int SLOTS = 4;
    public static Path dir = Path.of("saves");
    /** 0.8: while true nothing is saved (a Director Mode session changes the survivor's kit and state for the length of the session). */
    public static volatile boolean suspended;

    private SaveSystem() {}

    public static Path file(int slot) { return dir.resolve("profile" + slot + ".json"); }

    public static boolean exists(int slot) { return Files.exists(file(slot)) || Files.exists(bak(slot)); }

    private static Path bak(int slot) { return dir.resolve("profile" + slot + ".json.bak"); }

    public static synchronized void save(Profile p) {
        if (suspended) return;                 // Director Mode: the survivor's state is on loan and is not to be written down
        try {
            Files.createDirectories(dir);
            String text = Json.write(p.toJson());
            Json.parseObject(text); // verify round-trip before replacing anything
            Path tmp = dir.resolve("profile" + p.slot + ".json.tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            Path live = file(p.slot);
            if (Files.exists(live)) Files.copy(live, bak(p.slot), StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(tmp, live, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, live, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) {
            System.err.println("[Save] failed: " + e);
        }
    }

    public static Profile load(int slot) {
        for (Path f : new Path[]{file(slot), bak(slot)}) {
            if (!Files.exists(f)) continue;
            try {
                Map<String, Object> m = Json.parseObject(Files.readString(f, StandardCharsets.UTF_8));
                Profile p = Profile.fromJson(m);
                p.slot = slot;
                return p;
            } catch (Exception e) {
                System.err.println("[Save] " + f + " unreadable (" + e.getMessage() + "), trying backup");
            }
        }
        return null;
    }

    /** Lightweight header for the profile select screen without keeping the profile loaded. */
    public static String[] summary(int slot) {
        Profile p = load(slot);
        if (p == null) return null;
        return new String[]{p.name + (p.ironman ? "   [IRONMAN]" : ""), p.prog.title(), absentialis.Version.coins(p.scrip), p.stat("raids") + " raids, " + p.stat("extracts") + " extracted"};
    }

    public static void delete(int slot) {
        try { Files.deleteIfExists(file(slot)); Files.deleteIfExists(bak(slot)); } catch (IOException ignored) {}
    }
}
