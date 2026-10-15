package absentialis.tools;

import absentialis.engine.audio.Codecs;
import absentialis.engine.audio.UltraEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Looks at recordings for someone who cannot listen to them (0.9). For each file it prints the length, the peak, and
 * the loudness envelope as one digit per slice (0 = silence, 9 = the file's loudest slice), with the brightness of
 * the sound (zero crossings a second) - enough to tell an idle from a rev, where the click in a long take is, whether
 * a "single step" is one step, and where to cut. Run: gradlew audioProbe --args="<dir or file> [name filter] [slice ms]".
 */
public final class AudioProbe {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : "assets/audio/pack");
        String filter = args.length > 1 ? args[1].toLowerCase() : "";
        int sliceMs = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(root)) try (var st = Files.list(root)) { st.sorted().filter(p -> Codecs.accepts(p.getFileName().toString())).forEach(files::add); }
        else files.add(root);
        for (Path p : files) {
            String name = p.getFileName().toString();
            boolean wanted = filter.isEmpty();
            for (String f : filter.split(",")) if (!f.isEmpty() && name.toLowerCase().contains(f)) wanted = true;
            if (!wanted) continue;
            try {
                Codecs.Decoded d = Codecs.decode(p);
                System.out.println(describe(name, d.mono(), sliceMs) + "  [" + d.format() + " " + d.sourceRate() + " Hz x" + d.channels() + "]");
            } catch (Throwable e) { System.out.println(name + "  UNREADABLE: " + e.getMessage()); }
        }
    }

    /** One line: name, seconds, peak, zero crossings a second, then the envelope. */
    public static String describe(String name, float[] x, int sliceMs) {
        float secs = x.length / (float) UltraEngine.RATE;
        int ms = sliceMs > 0 ? sliceMs : secs > 60 ? 2000 : secs > 20 ? 500 : secs > 6 ? 200 : secs > 1.5f ? 50 : 20;
        int n = Math.max(1, UltraEngine.RATE * ms / 1000), slices = (x.length + n - 1) / n;
        float[] rms = new float[slices];
        float peak = 0, top = 1e-9f;
        int cross = 0;
        for (int i = 0; i < x.length; i++) {
            rms[i / n] += x[i] * x[i];
            peak = Math.max(peak, Math.abs(x[i]));
            if (i > 0 && (x[i] >= 0) != (x[i - 1] >= 0)) cross++;
        }
        for (int s = 0; s < slices; s++) { rms[s] = (float) Math.sqrt(rms[s] / n); top = Math.max(top, rms[s]); }
        StringBuilder env = new StringBuilder();
        for (int s = 0; s < slices; s++) {
            // 6 dB a digit below the loudest slice: 9 is the top, 0 is 54 dB or more under it
            double db = 20 * Math.log10(Math.max(1e-9, rms[s] / top));
            env.append((char) ('0' + Math.max(0, Math.min(9, 9 + (int) Math.floor(db / 6)))));
        }
        return String.format("%-28s %6.2fs peak %.2f zc/s %5d  %dms: %s", name, secs, peak, Math.round(cross / Math.max(0.001f, secs)), ms, env);
    }
}
