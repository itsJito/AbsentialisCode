package absentialis.tools;

import absentialis.engine.util.Json;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * The asset-processing pipeline for recorded audio (0.5, brief 17 phase 3): {@code gradlew prepareAudio}.
 *
 * Reads the original recordings named in {@code tools/audio/sources.json} - never modifying them - and writes the
 * ingredients the game plays into {@code assets/audio/}, with an index describing each one:
 *
 *   weapons    every clean single shot of every source take is located, cut at its onset and split into
 *              complementary layers that sum back to the original exactly:
 *                tr  transient   the first 60-100 ms above the crossover: the crack
 *                bd  body        everything below the crossover, with its own low tail: the thump
 *                tl  tail        what is left above the crossover after the transient: the air and the range's decay
 *                ds  distant     the take from the least direct perspective available, band-limited
 *   handling   reload and action recordings cut into their separate events (magazine out, in, charge ...)
 *   footsteps  each surface's steps as individual one-shots; surfaces with no recording of their own are derived
 *              from the nearest one and marked as derived
 *
 * Only what is necessary is done to the audio: mono fold-down, zero-phase crossover, level matching within a
 * weapon, fades at edit points and sample-rate conversion with a windowed-sinc filter. The initial transient of a
 * shot is never touched by a fade or a filter with delay. The tool prints, per source file, sample rate, channels,
 * shot count, clipping and how direct the perspective is, which is where the manifest's figures come from.
 */
public final class AudioPrep {
    private static final int OUT = 44100;

    /** One decoded file: mono samples at its own rate. */
    static final class Clip {
        float[] x; int rate, channels, bits; int clipped; String path;
        float seconds() { return x.length / (float) rate; }
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : ".");
        Path cfg = root.resolve("tools/audio/sources.json");
        Map<String, Object> src = Json.parseObject(Files.readString(cfg, StandardCharsets.UTF_8));
        Path outDir = root.resolve(Json.str(src, "out", "assets/audio"));
        Map<String, Object> index = new LinkedHashMap<>();
        index.put("generated", java.time.LocalDate.now().toString());
        index.put("weapons", weapons(root, src, outDir));
        index.put("handling", handling(root, src, outDir));
        index.put("footsteps", footsteps(root, src, outDir));
        Files.createDirectories(outDir);
        Files.writeString(outDir.resolve("index.json"), Json.write(index), StandardCharsets.UTF_8);
        System.out.println("[AudioPrep] index written to " + outDir.resolve("index.json"));
    }

    // ------------------------------------------------------------------ weapons
    @SuppressWarnings("unchecked")
    private static Map<String, Object> weapons(Path root, Map<String, Object> src, Path outDir) throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        Path lib = root.resolve(Json.str(src, "weaponLibrary", ""));
        float cross = (float) Json.num(src, "crossoverHz", 1100);
        int maxTakes = Json.integer(src, "maxTakes", 5);
        for (Object o : Json.list(src, "weapons")) {
            Map<String, Object> w = (Map<String, Object>) o;
            String id = Json.str(w, "profile", "");
            List<Clip> clips = new ArrayList<>();
            List<String> nearFiles = Json.strList(w, "near"), all = new ArrayList<>(nearFiles);
            all.addAll(Json.strList(w, "mid"));
            for (String f : all) { Path p = lib.resolve(f); if (!Files.exists(p)) { System.out.println("[AudioPrep] MISSING " + p); continue; } Clip c = readWav(p); c.path = f; clips.add(c); }
            if (clips.isEmpty()) continue;
            // how direct each perspective is: energy in the first 30 ms against the following second
            float[] direct = new float[clips.size()];
            List<int[]>[] shots = new List[clips.size()];
            for (int i = 0; i < clips.size(); i++) { shots[i] = onsets(clips.get(i), 0.45f, 1.6f); direct[i] = directness(clips.get(i), shots[i]); }
            // which files are close and which are heard from down the range is the library's own description, not a guess
            boolean[] close = new boolean[clips.size()];
            for (int i = 0; i < clips.size(); i++) close[i] = nearFiles.contains(clips.get(i).path);
            Path dir = outDir.resolve("weapons").resolve(id);
            Files.createDirectories(dir);
            try (var old = Files.list(dir)) { for (Path p : (Iterable<Path>) old::iterator) if (p.getFileName().toString().endsWith(".wav")) Files.delete(p); }
            List<Object> takes = new ArrayList<>();
            List<Object> sources = new ArrayList<>();
            int n = 0, dn = 0, rejected = 0;
            for (int i = 0; i < clips.size(); i++) {
                Clip c = clips.get(i);
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("file", c.path); sm.put("rate", (double) c.rate); sm.put("channels", (double) c.channels); sm.put("bits", (double) c.bits);
                sm.put("seconds", (double) Math.round(c.seconds() * 10) / 10.0); sm.put("shots", (double) shots[i].size());
                sm.put("clippedSamples", (double) c.clipped); sm.put("directness", (double) Math.round(direct[i] * 100) / 100.0);
                sm.put("use", close[i] ? "close" : "distant");
                sources.add(sm);
                System.out.printf("[AudioPrep] %-12s %-34s %d Hz %d ch %d bit  %4.1f s  %d shots  clipped %d  directness %.2f%s%n", id, c.path, c.rate, c.channels, c.bits, c.seconds(), shots[i].size(), c.clipped, direct[i], close[i] ? "  (near)" : "  (mid: distant layer)");
                for (int[] s : shots[i]) {
                    boolean distant = !close[i];
                    if (distant ? dn >= maxTakes : n >= maxTakes) continue;
                    float[] take = cut(c, s[0], s[1], 2.6f);
                    float peak = peak(take);
                    if (peak < 1e-4f) continue;
                    // a take with a second report inside its first second is a burst or a neighbour's shot, not one round
                    if (!distant && !single(take, c.rate, peak)) { rejected++; continue; }
                    float g = 0.9f / peak;
                    for (int k = 0; k < take.length; k++) take[k] *= g;
                    if (distant) {
                        float[] ds = lowpass(take, c.rate, 2200);
                        fadeOut(ds, c.rate, 0.35f);
                        write(dir.resolve(id + "_ds" + (++dn) + ".wav"), resample(ds, c.rate, 11025), 11025);
                        continue;
                    }
                    n++;
                    // complementary split: body = zero-phase low-pass, the rest = original minus body, so nothing is lost or doubled
                    float[] bd = lowpass(take, c.rate, cross), hi = new float[take.length];
                    for (int k = 0; k < take.length; k++) hi[k] = take[k] - bd[k];
                    int t0 = (int) (0.060f * c.rate), t1 = (int) (0.100f * c.rate);
                    float[] tr = new float[Math.min(take.length, t1)], tl = new float[take.length];
                    for (int k = 0; k < take.length; k++) {
                        float wgt = k < t0 ? 1 : k >= t1 ? 0 : 0.5f + 0.5f * (float) Math.cos(Math.PI * (k - t0) / (double) (t1 - t0));
                        if (k < tr.length) tr[k] = hi[k] * wgt;
                        tl[k] = hi[k] * (1 - wgt);
                    }
                    fadeOut(bd, c.rate, 0.35f); fadeOut(tl, c.rate, 0.35f);
                    write(dir.resolve(id + "_tr" + n + ".wav"), resample(tr, c.rate, OUT), OUT);
                    write(dir.resolve(id + "_bd" + n + ".wav"), resample(bd, c.rate, 11025), 11025);
                    write(dir.resolve(id + "_tl" + n + ".wav"), resample(tl, c.rate, OUT), OUT);
                    Map<String, Object> tm = new LinkedHashMap<>();
                    tm.put("n", (double) n); tm.put("source", c.path); tm.put("at", (double) Math.round(s[0] / (float) c.rate * 100) / 100.0); tm.put("sourcePeak", (double) Math.round(peak * 100) / 100.0);
                    takes.add(tm);
                }
            }
            // a weapon with one perspective only: its distant layer is derived from the close takes and says so
            boolean derived = dn == 0;
            if (derived) for (int k = 1; k <= n; k++) {
                float[] tl = readWav(dir.resolve(id + "_tl" + k + ".wav")).x, bd = resample(readWav(dir.resolve(id + "_bd" + k + ".wav")).x, 11025, OUT);
                float[] mix = new float[Math.max(tl.length, bd.length)];
                for (int j = 0; j < mix.length; j++) mix[j] = (j < tl.length ? tl[j] * 0.8f : 0) + (j < bd.length ? bd[j] : 0);
                float[] ds = lowpass(mix, OUT, 1800);
                write(dir.resolve(id + "_ds" + k + ".wav"), resample(ds, OUT, 11025), 11025);
                dn++;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("firearm", Json.str(w, "firearm", "")); m.put("takes", (double) n); m.put("distant", (double) dn); m.put("distantDerived", derived);
            m.put("rejectedTakes", (double) rejected);
            m.put("sources", sources); m.put("shots", takes);
            System.out.printf("[AudioPrep] %-12s -> %d close takes, %d distant%s, %d rejected as not a single round%n", id, n, dn, derived ? " (derived)" : "", rejected);
            out.put(id, m);
        }
        return out;
    }

    /** Shot onsets {start, end} in samples: peaks above {@code rel} of the file's peak, at least {@code gap} seconds apart. */
    static List<int[]> onsets(Clip c, float rel, float gap) {
        float pk = peak(c.x);
        List<int[]> out = new ArrayList<>();
        int win = c.rate / 400, minGap = (int) (gap * c.rate), last = -minGap;
        for (int i = 0; i + win < c.x.length; i += win) {
            float e = 0;
            for (int k = i; k < i + win; k++) e = Math.max(e, Math.abs(c.x[k]));
            if (e < pk * rel || i - last < minGap) continue;
            // walk back to where the shot actually begins (5 percent of this peak), and leave a millisecond of lead-in
            int s = i;
            while (s > 0 && i - s < c.rate / 50 && Math.abs(c.x[s]) > e * 0.05f) s--;
            s = Math.max(0, s - c.rate / 1000);
            if (!out.isEmpty()) out.get(out.size() - 1)[1] = Math.min(out.get(out.size() - 1)[1], s - c.rate / 20);
            out.add(new int[]{s, c.x.length});
            last = i;
        }
        return out;
    }

    /** True when nothing after the first 50 ms comes within 8 dB of the shot's own peak. */
    static boolean single(float[] take, int rate, float peak) {
        int a = rate / 20, b = Math.min(take.length, rate);
        for (int k = a; k < b; k++) if (Math.abs(take[k]) > peak * 0.4f) return false;
        return true;
    }

    static float directness(Clip c, List<int[]> shots) {
        double a = 0, b = 0;
        for (int[] s : shots) {
            int e1 = Math.min(c.x.length, s[0] + c.rate * 30 / 1000), e2 = Math.min(Math.min(c.x.length, s[1]), s[0] + c.rate);
            for (int k = s[0]; k < e1; k++) a += c.x[k] * c.x[k];
            for (int k = e1; k < e2; k++) b += c.x[k] * c.x[k];
        }
        return b <= 0 ? 0 : (float) (a / b);
    }

    static float[] cut(Clip c, int s, int e, float maxSeconds) {
        int n = Math.max(1, Math.min(Math.min(e, c.x.length) - s, (int) (maxSeconds * c.rate)));
        float[] o = new float[n];
        System.arraycopy(c.x, s, o, 0, n);
        return o;
    }

    // ------------------------------------------------------------------ handling (reloads, actions)
    @SuppressWarnings("unchecked")
    private static Map<String, Object> handling(Path root, Map<String, Object> src, Path outDir) throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        Path dir = outDir.resolve("handling");
        Files.createDirectories(dir);
        for (Object o : Json.list(src, "handling")) {
            Map<String, Object> h = (Map<String, Object>) o;
            Path p = root.resolve(Json.str(h, "file", ""));
            if (!Files.exists(p)) { System.out.println("[AudioPrep] MISSING " + p); continue; }
            Clip c = readWav(p);
            String name = Json.str(h, "name", "event");
            List<int[]> ev = onsets(c, (float) Json.num(h, "threshold", 0.22), (float) Json.num(h, "gap", 0.14));
            List<Object> parts = new ArrayList<>();
            int n = 0;
            for (int[] s : ev) {
                float[] x = cut(c, s[0], s[1], (float) Json.num(h, "maxSeconds", 0.6));
                float pk = peak(x);
                if (pk < 1e-3f) continue;
                for (int k = 0; k < x.length; k++) x[k] *= 0.8f / pk;
                fadeOut(x, c.rate, 0.03f);
                n++;
                write(dir.resolve(name + "_" + n + ".wav"), resample(x, c.rate, OUT), OUT);
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("n", (double) n); pm.put("at", (double) Math.round(s[0] / (float) c.rate * 100) / 100.0); pm.put("seconds", (double) Math.round(x.length / (float) c.rate * 100) / 100.0);
                parts.add(pm);
            }
            System.out.printf("[AudioPrep] handling %-18s %-34s %d Hz %d ch  %.2f s  %d events%n", name, p.getFileName(), c.rate, c.channels, c.seconds(), n);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", Json.str(h, "file", "")); m.put("events", parts);
            out.put(name, m);
        }
        return out;
    }

    // ------------------------------------------------------------------ footsteps
    @SuppressWarnings("unchecked")
    private static Map<String, Object> footsteps(Path root, Map<String, Object> src, Path outDir) throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Object o : Json.list(src, "footsteps")) {
            Map<String, Object> f = (Map<String, Object>) o;
            String surface = Json.str(f, "surface", "");
            Path from = root.resolve(Json.str(f, "dir", ""));
            Path dir = outDir.resolve("footsteps").resolve(surface);
            Files.createDirectories(dir);
            float lp = (float) Json.num(f, "lowpassHz", 0), rate = (float) Json.num(f, "rate", 1), gain = (float) Json.num(f, "gain", 1);
            String blend = Json.str(f, "blendDir", null);
            int n = 0;
            if (!Files.isDirectory(from)) { System.out.println("[AudioPrep] MISSING " + from); continue; }
            List<Path> files = new ArrayList<>();
            try (var st = Files.list(from)) { st.filter(p -> p.toString().toLowerCase().endsWith(".ogg") || p.toString().toLowerCase().endsWith(".wav")).sorted().forEach(files::add); }
            List<Path> blendFiles = new ArrayList<>();
            if (blend != null && Files.isDirectory(root.resolve(blend))) try (var st = Files.list(root.resolve(blend))) { st.filter(p -> p.toString().toLowerCase().endsWith(".ogg")).sorted().forEach(blendFiles::add); }
            for (Path p : files) {
                Clip c = p.toString().toLowerCase().endsWith(".ogg") ? readOgg(p) : readWav(p);
                float[] x = c.x;
                if (lp > 0) x = lowpass(x, c.rate, lp);
                if (!blendFiles.isEmpty()) {
                    // a second texture laid under the step (grit under a dirt footfall), quietly
                    Clip b = readOgg(blendFiles.get(n % blendFiles.size()));
                    float[] y = resample(b.x, b.rate, c.rate);
                    float bg = (float) Json.num(f, "blendGain", 0.3);
                    float[] mix = new float[Math.max(x.length, y.length)];
                    for (int k = 0; k < mix.length; k++) mix[k] = (k < x.length ? x[k] : 0) + (k < y.length ? y[k] * bg : 0);
                    x = mix;
                }
                float pk = peak(x);
                if (pk < 1e-4f) continue;
                for (int k = 0; k < x.length; k++) x[k] *= 0.7f * gain / pk;
                fadeOut(x, c.rate, 0.02f);
                // rate != 1 plays the recording slower or faster (a heavier, duller or a lighter step)
                float[] y = resample(x, Math.round(c.rate * rate), OUT);
                write(dir.resolve((++n) + ".wav"), y, OUT);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("count", (double) n); m.put("source", Json.str(f, "dir", "")); m.put("derived", lp > 0 || rate != 1 || blend != null);
            out.put(surface, m);
            System.out.printf("[AudioPrep] footsteps %-9s %d steps from %s%s%n", surface, n, Json.str(f, "dir", ""), lp > 0 || rate != 1 || blend != null ? "  (derived)" : "");
        }
        return out;
    }

    // ------------------------------------------------------------------ signal helpers
    static float peak(float[] x) { float p = 0; for (float v : x) p = Math.max(p, Math.abs(v)); return p; }

    /** Zero-phase low-pass: a two-pole filter run forwards then backwards, so nothing is delayed. */
    static float[] lowpass(float[] x, int rate, float hz) {
        float a = (float) (1 - Math.exp(-2 * Math.PI * hz / rate));
        float[] y = x.clone();
        for (int pass = 0; pass < 2; pass++) {
            float s1 = 0, s2 = 0;
            for (int i = 0; i < y.length; i++) { s1 += a * (y[i] - s1); s2 += a * (s1 - s2); y[i] = s2; }
            float t1 = 0, t2 = 0;
            for (int i = y.length - 1; i >= 0; i--) { t1 += a * (y[i] - t1); t2 += a * (t1 - t2); y[i] = t2; }
            if (pass == 0) break;
        }
        return y;
    }

    static void fadeOut(float[] x, int rate, float seconds) {
        int n = Math.min(x.length, (int) (seconds * rate));
        for (int i = 0; i < n; i++) { float w = 0.5f - 0.5f * (float) Math.cos(Math.PI * i / (double) n); x[x.length - 1 - i] *= w; }
    }

    /** Sample-rate conversion with a Hann-windowed sinc, band-limited to the lower of the two rates. */
    static float[] resample(float[] x, int from, int to) {
        if (from == to) return x.clone();
        double ratio = to / (double) from, cutoff = Math.min(1.0, ratio) * 0.95;
        int half = 24;
        int n = (int) Math.floor(x.length * ratio);
        float[] y = new float[Math.max(1, n)];
        double span = half / Math.min(1.0, ratio);
        for (int i = 0; i < y.length; i++) {
            double c = i / ratio;
            int a = (int) Math.ceil(c - span), b = (int) Math.floor(c + span);
            double acc = 0, norm = 0;
            for (int k = a; k <= b; k++) {
                if (k < 0 || k >= x.length) continue;
                double t = (k - c) * cutoff, w = 0.5 + 0.5 * Math.cos(Math.PI * (k - c) / span);
                double sinc = Math.abs(t) < 1e-9 ? 1 : Math.sin(Math.PI * t) / (Math.PI * t);
                acc += x[k] * sinc * w; norm += sinc * w;
            }
            y[i] = (float) (norm == 0 ? 0 : acc / norm);
        }
        return y;
    }

    // ------------------------------------------------------------------ file formats
    /** RIFF/WAVE reader for 16-, 24- and 32-bit PCM (including WAVE_FORMAT_EXTENSIBLE) and 32-bit float; folds to mono. */
    static Clip readWav(Path p) throws IOException {
        byte[] d = Files.readAllBytes(p);
        ByteBuffer b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        Clip c = new Clip();
        int pos = 12, fmt = 1;
        while (pos + 8 <= d.length) {
            String id = new String(d, pos, 4, StandardCharsets.US_ASCII);
            int size = b.getInt(pos + 4), body = pos + 8;
            if (id.equals("fmt ")) {
                fmt = b.getShort(body) & 0xFFFF; c.channels = b.getShort(body + 2); c.rate = b.getInt(body + 4); c.bits = b.getShort(body + 14);
                if (fmt == 0xFFFE && size >= 26) fmt = b.getShort(body + 24) & 0xFFFF;
            } else if (id.equals("data")) {
                int bytes = c.bits / 8, frames = Math.min(size, d.length - body) / (bytes * c.channels);
                c.x = new float[frames];
                int run = 0;
                for (int i = 0; i < frames; i++) {
                    float sum = 0;
                    boolean hot = false;
                    for (int ch = 0; ch < c.channels; ch++) {
                        int o = body + (i * c.channels + ch) * bytes;
                        float v;
                        if (fmt == 3) v = b.getFloat(o);
                        else if (bytes == 2) v = b.getShort(o) / 32768f;
                        else if (bytes == 3) v = ((d[o] & 0xFF) | (d[o + 1] & 0xFF) << 8 | d[o + 2] << 16) / 8388608f;
                        else v = b.getInt(o) / 2147483648f;
                        if (Math.abs(v) >= 0.999f) hot = true;
                        sum += v;
                    }
                    // three or more consecutive full-scale frames is a flat top: clipping in the source
                    run = hot ? run + 1 : 0;
                    if (run == 3) c.clipped += 3; else if (run > 3) c.clipped++;
                    c.x[i] = sum / c.channels;
                }
                break;
            }
            pos = body + size + (size & 1);
        }
        if (c.x == null) throw new IOException("No PCM data in " + p);
        return c;
    }

    static Clip readOgg(Path p) throws IOException {
        try (MemoryStack st = MemoryStack.stackPush()) {
            IntBuffer ch = st.mallocInt(1), rate = st.mallocInt(1);
            ShortBuffer pcm = STBVorbis.stb_vorbis_decode_filename(p.toString(), ch, rate);
            if (pcm == null) throw new IOException("Cannot decode " + p);
            Clip c = new Clip();
            c.channels = ch.get(0); c.rate = rate.get(0); c.bits = 16;
            int frames = pcm.remaining() / c.channels;
            c.x = new float[frames];
            for (int i = 0; i < frames; i++) { float s = 0; for (int k = 0; k < c.channels; k++) s += pcm.get(i * c.channels + k) / 32768f; c.x[i] = s / c.channels; }
            MemoryUtil.memFree(pcm);
            return c;
        }
    }

    static void write(Path p, float[] x, int rate) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(44 + x.length * 2);
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + x.length * 2).put("WAVE".getBytes(StandardCharsets.US_ASCII));
        h.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16);
        h.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(x.length * 2);
        bo.write(h.array());
        ByteBuffer body = ByteBuffer.allocate(x.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        // triangular dither at one LSB: the quiet end of a tail fades into noise, not into steps
        java.util.Random r = new java.util.Random(x.length * 31L + rate);
        for (float v : x) { float d = (r.nextFloat() - r.nextFloat()) / 32768f; body.putShort((short) Math.max(-32768, Math.min(32767, Math.round((v + d) * 32767f)))); }
        bo.write(body.array());
        Files.write(p, bo.toByteArray());
    }

    public static File dirOf(Path p) { return p.toFile(); }
}
