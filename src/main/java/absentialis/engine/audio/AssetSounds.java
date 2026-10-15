package absentialis.engine.audio;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Recorded sound assets. Every .wav, .ogg or .mp3 in assets/audio replaces the synthesised sound of the same name
 * (assets/audio/shot_rifle.ogg replaces "shot_rifle"), or adds a new one. Files are decoded to mono at the mixer
 * rate; a file that cannot be read is reported and skipped, and the synthesised sound stays.
 * See assets/audio/README.txt for the names the game plays and the licences of what ships there.
 */
public final class AssetSounds {
    private AssetSounds() {}

    public static final Path DIR = Path.of("assets", "audio");

    /** Loads every asset over the bank. Returns how many were loaded. */
    public static int load(Map<String, float[]> out) {
        if (!Files.isDirectory(DIR)) return 0;
        int n = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(DIR)) {
            for (Path p : ds) {
                String f = p.getFileName().toString();
                if (Files.isDirectory(p) || !Codecs.accepts(f)) continue;
                String name = Codecs.stem(f);
                try {
                    float[] d = Codecs.decode(p).mono();
                    tame(d, name);
                    out.put(name, d);
                    n++;
                } catch (Codecs.Failure e) {
                    System.err.println("[UltraEngine] " + f + ": " + e.getMessage() + " - keeping the sound it would have replaced");
                }
            }
        } catch (Exception e) {
            System.err.println("[UltraEngine] assets: " + e.getMessage());
        }
        return n;
    }

    /** One recorded file as mono samples at the engine rate, or null when it is absent or unreadable. Levels are left as stored. */
    public static float[] read(Path p) { return Codecs.read(p); }

    /** Peak-normalises a recording to the level the synthesised bank uses, and seals the ends against clicks. */
    private static void tame(float[] d, String name) {
        float m = 1e-6f;
        for (float v : d) m = Math.max(m, Math.abs(v));
        float target = name.startsWith("amb_") || name.startsWith("music_") ? 0.5f : 0.85f;
        float k = target / m;
        for (int i = 0; i < d.length; i++) d[i] *= k;
        int edge = Math.min(d.length / 8, 400);
        for (int i = 0; i < edge; i++) { float f = i / (float) edge; d[i] *= f; d[d.length - 1 - i] *= f; }
    }

    /** Resampling to the mixer rate (see {@link Codecs}). */
    static float[] resample(float[] in, int rate) { return Codecs.resample(in, rate); }
}
