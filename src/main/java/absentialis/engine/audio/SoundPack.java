package absentialis.engine.audio;

import absentialis.engine.util.Json;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The sound pack (0.8). {@code assets/audio/pack} holds recordings under whatever names they came with;
 * {@code content/soundpack.json} says what each one is for. An entry names a sound the game plays and the files that
 * are its takes:
 *
 *   "impact_wood": ["impact_wood_01", "impact_wood_02", ...]            the plain form
 *   "explosion_far": {"files": [...], "level": 0.7, "lowpass": 900}     with a level, and a filter applied at load
 *   "hammer": [{"file": "fs_707864", "start": 0.15, "length": 0.35}]  part of a longer recording
 *
 * The first file becomes the sound itself and the rest its takes (name_2, name_3 ...), which UltraEngine chooses
 * between each time the sound is played. A name the synthesised bank already has is replaced; a new name is added.
 * Files are found by their name without the extension, whatever the extension is and whatever case it is in, and are
 * read by content ({@link Codecs}): .wav, .ogg and .mp3 all work.
 *
 * "streams" are the long ones - district ambiences, music, sirens - which are not decoded at start-up but when first
 * wanted. A sound that is played round and round says so with "loop": true and is joined to itself at load.
 * "weapons" are the close recordings of each firearm; the gun bus reads those itself (gun.WeaponAudioProfile).
 *
 * Nothing is silently dropped: a file the table does not mention, a file the table mentions that is not there and a
 * file that cannot be decoded are each listed, and the quality gate fails on any of them.
 */
public final class SoundPack {
    public static final Path DIR = AssetSounds.DIR.resolve("pack");
    public static final String TABLE = "/content/soundpack.json";

    /** Sounds put in the bank (takes counted), files read, and the game names that were replaced rather than added. */
    public int loaded, files;
    public final List<String> replaced = new ArrayList<>(), added = new ArrayList<>(), problems = new ArrayList<>();
    /** Every file in the pack directory by stem, and the stems the table uses. */
    public final Map<String, Path> onDisk = new LinkedHashMap<>();
    public final TreeSet<String> used = new TreeSet<>();
    /** Files that were found online rather than supplied (their sources are in the table's "credits" and in the pack's CREDITS.txt). */
    public final TreeSet<String> online = new TreeSet<>();
    /** game sound -> the files that are its takes (for the credits, the manifest and the gate). */
    public final Map<String, List<String>> sounds = new LinkedHashMap<>();
    public final Map<String, String> streamFiles = new LinkedHashMap<>();
    public final Map<String, String> formats = new HashMap<>();

    /** Files in the pack that nothing uses. */
    public List<String> unused() { List<String> l = new ArrayList<>(); for (String s : onDisk.keySet()) if (!used.contains(s)) l.add(s); return l; }

    /** The pack directory by stem. */
    public static Map<String, Path> index(Path dir) {
        Map<String, Path> m = new LinkedHashMap<>();
        if (!Files.isDirectory(dir)) return m;
        try (var st = Files.list(dir)) {
            for (Path p : (Iterable<Path>) st.sorted()::iterator) {
                String f = p.getFileName().toString();
                if (Files.isRegularFile(p) && Codecs.accepts(f)) m.put(Codecs.stem(f), p);
            }
        } catch (Exception ignored) { }
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> table() {
        try (InputStream in = SoundPack.class.getResourceAsStream(TABLE)) {
            if (in == null) return new LinkedHashMap<>();
            return Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) { return new LinkedHashMap<>(); }
    }

    @SuppressWarnings("unchecked")
    public static SoundPack load(UltraEngine engine) {
        SoundPack pack = new SoundPack();
        Map<String, Object> root = table();
        if (root.isEmpty()) { pack.problems.add("content/soundpack.json missing or unreadable"); return pack; }
        pack.onDisk.putAll(index(DIR));
        if (pack.onDisk.isEmpty()) { pack.problems.add("no sound pack in " + DIR + " - the game plays what it had before 0.8"); return pack; }
        for (Map.Entry<String, Object> e : Json.obj(root, "sounds").entrySet()) {
            String name = e.getKey();
            List<Object> list;
            float level = name.startsWith("ui_") || name.startsWith("inv_") ? 0.6f : 0.85f, lowpass = 0;
            boolean looped = name.endsWith("_loop") || name.startsWith("amb_");
            if (e.getValue() instanceof List) list = (List<Object>) e.getValue();
            else if (e.getValue() instanceof Map) {
                Map<String, Object> o = (Map<String, Object>) e.getValue();
                list = Json.list(o, "files");
                level = (float) Json.num(o, "level", level);
                lowpass = (float) Json.num(o, "lowpass", 0);
                looped |= Boolean.TRUE.equals(o.get("loop"));
            } else continue;
            List<String> stems = new ArrayList<>();
            boolean had = engine.samples.containsKey(name);
            // takes the bank already had under this name belong to the sound that is being replaced: they go with it
            for (int k = 2; engine.samples.containsKey(name + "_" + k); k++) engine.samples.remove(name + "_" + k);
            int k = 0;
            for (Object f : list) {
                // a take is a file, or part of one: {"file": ..., "start": seconds, "length": seconds}
                float start = 0, length = 0;
                if (f instanceof Map) { Map<String, Object> fo = (Map<String, Object>) f; start = (float) Json.num(fo, "start", 0); length = (float) Json.num(fo, "length", 0); f = Json.str(fo, "file", ""); }
                String stem = String.valueOf(f).toLowerCase();
                Path p = pack.onDisk.get(stem);
                if (p == null) { pack.problems.add(name + ": file '" + stem + "' is not in the pack"); continue; }
                try {
                    Codecs.Decoded d = Codecs.decode(p);
                    float[] x = d.mono();
                    if (start > 0 || length > 0) {
                        int from = Math.min(x.length - 1, Math.round(start * UltraEngine.RATE)), to = length > 0 ? Math.min(x.length, from + Math.round(length * UltraEngine.RATE)) : x.length;
                        x = java.util.Arrays.copyOfRange(x, from, Math.max(from + 8, to));
                    }
                    if (lowpass > 0) lowpass(x, lowpass);
                    trim(x);
                    level(x, level);
                    // something that is played round and round is joined to itself; anything else is closed at both ends
                    if (looped) x = seam(x); else edges(x);
                    engine.samples.put(k == 0 ? name : name + "_" + (k + 1), x);
                    k++;
                    pack.used.add(stem); pack.files++; stems.add(stem);
                    if (stem.startsWith("fs_")) pack.online.add(stem);
                    pack.formats.put(stem, d.format() + " " + d.sourceRate() + " Hz x" + d.channels());
                } catch (Codecs.Failure ex) {
                    pack.problems.add(name + ": " + p.getFileName() + " " + ex.getMessage());
                }
            }
            if (k > 0) { pack.loaded += k; pack.sounds.put(name, stems); (had ? pack.replaced : pack.added).add(name); }
        }
        for (Map.Entry<String, Object> e : Json.obj(root, "streams").entrySet()) {
            String stem = String.valueOf(e.getValue()).toLowerCase();
            Path p = pack.onDisk.get(stem);
            if (p == null) { pack.problems.add(e.getKey() + ": bed '" + stem + "' is not in the pack"); continue; }
            engine.streams.put(e.getKey(), p);
            pack.streamFiles.put(e.getKey(), stem);
            pack.used.add(stem);
            if (stem.startsWith("fs_")) pack.online.add(stem);
            // 0.9: a bed that has a recording has no synthesised stand-in left behind it - the name is the stream, and only the stream
            if (engine.samples.remove(e.getKey()) != null) pack.replaced.add(e.getKey());
            for (int k = 2; engine.samples.remove(e.getKey() + "_" + k) != null; k++) { }
        }
        // the gun bus loads its own; they are counted here so that nothing in the pack goes unaccounted for
        for (Object w : Json.obj(root, "weapons").values()) if (w instanceof Map) for (Object v : ((Map<String, Object>) w).values()) if (v instanceof List) for (Object f : (List<Object>) v) {
            String stem = String.valueOf(f).toLowerCase();
            if (pack.onDisk.containsKey(stem)) pack.used.add(stem); else pack.problems.add("weapons: file '" + stem + "' is not in the pack");
        }
        for (String u : pack.unused()) pack.problems.add("file '" + u + "' is in the pack and nothing uses it");
        return pack;
    }

    // ------------------------------------------------------------------ conditioning (once, at load)
    /** Peak level. */
    public static void level(float[] d, float target) {
        float m = 1e-6f;
        for (float v : d) m = Math.max(m, Math.abs(v));
        float k = target / m;
        for (int i = 0; i < d.length; i++) d[i] *= k;
    }

    /** Short fades at both ends: no clicks from a file cut mid-wave. */
    public static void edges(float[] d) {
        int in = Math.min(d.length / 16, 64), out = Math.min(d.length / 8, 400);
        for (int i = 0; i < in; i++) d[i] *= i / (float) in;
        for (int i = 0; i < out; i++) d[d.length - 1 - i] *= i / (float) out;
    }

    /** Silences the dead air some recordings start with (anything under -60 dB of the peak before the sound begins), so a sound plays when it is asked for. */
    public static void trim(float[] d) {
        float m = 1e-6f;
        for (float v : d) m = Math.max(m, Math.abs(v));
        float floor = m * 0.001f;
        int start = 0;
        while (start < d.length && Math.abs(d[start]) < floor) start++;
        start = Math.max(0, start - 32);
        if (start > 64 && start < d.length - 64) { System.arraycopy(d, start, d, 0, d.length - start); java.util.Arrays.fill(d, d.length - start, d.length, 0f); }
    }

    /** One-pole low-pass, twice (12 dB an octave): what distance does to a sound. */
    public static void lowpass(float[] d, float hz) {
        float k = (float) (1 - Math.exp(-2 * Math.PI * hz / UltraEngine.RATE));
        for (int pass = 0; pass < 2; pass++) { float s = 0; for (int i = 0; i < d.length; i++) { s += (d[i] - s) * k; d[i] = s; } }
    }

    /** Makes a bed loop without a seam: the last second is cross-faded into the first, and the bed is that much shorter. */
    public static float[] seam(float[] d) {
        int n = Math.min(UltraEngine.RATE, d.length / 4);
        float[] o = java.util.Arrays.copyOf(d, d.length - n);
        for (int i = 0; i < n; i++) { float t = i / (float) n; o[i] = d[i] * t + d[d.length - n + i] * (1 - t); }
        return o;
    }
}
