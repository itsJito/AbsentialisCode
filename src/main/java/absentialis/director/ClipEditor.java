package absentialis.director;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import absentialis.director.RecordingManager.Clip;
import absentialis.engine.Input;
import absentialis.ui.Ui;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.system.MemoryUtil;

/**
 * The cinematic editor. It is a separate system from recording: Director Mode stages a scene and captures clips;
 * this takes clips and makes a film of them. It has no access to the world and changes nothing in it.
 *
 * What it does:
 *   timeline editing     each piece on the timeline has an in point and an out point into its clip
 *   clip sequencing      pieces are added from the library, reordered and removed
 *   camera positioning   a piece can be punched in (zoom) and re-framed (pan) inside the recorded picture
 *   camera transitions   a piece begins with a cut, a cross-fade from the piece before, or a dip through black
 *   playback adjustment  a piece plays at a quarter of its speed up to twice its speed
 *   visual effects       none, monochrome, sepia, cold, vignette, letterbox - applied to the pixels
 *   audio management     the recorded sound of each piece at its own level, or the whole film silent
 *   video export         the timeline is rendered to an .avi (Motion-JPEG with PCM sound) under exports/
 *
 * The preview and the export use the same renderer ({@link #render}), so what is previewed is what is written.
 */
public final class ClipEditor {
    public static final String[] TRANSITIONS = {"Cut", "Cross-fade", "Dip to black"}, EFFECTS = {"None", "Monochrome", "Sepia", "Cold", "Vignette", "Letterbox"};
    public static float TRANSITION_SECONDS = 0.6f;
    /** 0.9: the cameras a take can be looked at from in the editor. The first is the footage itself; the others film the scene tape again. */
    public static final String[] CAMERAS = {"As recorded", "Third person", "Orbit", "Fixed", "Free (fly it)"};
    public static final int CAM_RECORDED = 0, CAM_THIRD = 1, CAM_ORBIT = 2, CAM_FIXED = 3, CAM_FREE = 4;
    /** Set by Director Mode: the session the editor is in (its recorder films the re-shoots). */
    public Director host;

    /** One piece of the timeline. */
    public static final class Piece {
        public Clip clip;
        public float in, out, speed = 1, zoom = 1, panX, panY, volume = 1;
        public int transition, effect;
        /** 0.9, perspective: which camera the take is looked at from (see {@link #CAMERAS}), and how that camera is set. */
        public int camera;
        public float camDist = 4.5f, camHeight = 1.7f, orbitSpeed = 0.35f, orbitFrom, fov = 0;
        public float[] camAt;
        Piece copy() { Piece q = new Piece(); q.clip = clip; q.in = in; q.out = out; q.speed = speed; q.zoom = zoom; q.panX = panX; q.panY = panY; q.volume = volume; q.transition = transition; q.effect = effect; q.camera = camera; q.camDist = camDist; q.camHeight = camHeight; q.orbitSpeed = orbitSpeed; q.orbitFrom = orbitFrom; q.fov = fov; q.camAt = camAt == null ? null : camAt.clone(); return q; }
        public float length() { return Math.max(0.05f, (out - in) / speed); }
    }

    public final List<Piece> timeline = new ArrayList<>();
    public List<Clip> library = new ArrayList<>();
    public boolean muted;
    public float master = 1f;
    public String name = "film";
    public String status = "";
    /** Export progress 0..1 while a film is being written (-1 otherwise), and where the last one went. */
    public volatile float exporting = -1;
    public volatile String exported = "";
    private float playhead;
    private boolean playing;
    private int previewTex, texW, texH;
    private float shownAt = -1;
    private final Map<String, BufferedImage> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> e) { return size() > 10; }
    };
    private final GUIManager gui = new GUIManager();
    private GUIManager.Menu root;
    public boolean closeRequested;
    private int selected;

    public static Path exportsDir() { return RecordingManager.root().resolveSibling("exports"); }

    public ClipEditor() { build(); }

    public void open() {
        library = RecordingManager.clips(); closeRequested = false; build(); gui.show(root);
        // 0.9: the take just recorded is on the timeline when the editor opens - there is nothing to hunt for
        timeline.removeIf(p -> !Files.isDirectory(p.clip.dir()));
        if (timeline.isEmpty() && !library.isEmpty()) add(library.get(0));
        framing = false; shooting = null; status = library.isEmpty() ? "No clips yet: record one in Director Mode first (Camera & Recording)." : library.size() + " clip(s) in the library."; }

    public float duration() { float t = 0; for (Piece p : timeline) t += p.length(); return t; }

    public Piece add(Clip c) {
        Piece p = new Piece();
        p.clip = c; p.in = 0; p.out = c.seconds();
        timeline.add(p);
        selected = timeline.size() - 1;
        return p;
    }

    // ------------------------------------------------------------------ menus
    private static String secs(float s) { return String.format("%.1f s", s); }

    private void build() {
        root = new GUIManager.Menu("Editor");
        GUIManager.Menu lib = new GUIManager.Menu("Add clip");
        lib.onOpen = () -> {
            lib.items.clear();
            library = RecordingManager.clips();
            for (Clip c : library) lib.add(c.name()).value(() -> secs(c.seconds()) + (c.audio() ? "  sound" : "  silent"), null).run(() -> { add(c); status = "Added " + c.name() + " to the timeline"; gui.back(); }).hint(c.frames() + " frames at " + c.fps() + " fps, " + c.width() + " x " + c.height());
            if (library.isEmpty()) lib.add("(no clips recorded)").when(() -> false, () -> "record one in Director Mode first");
        };
        GUIManager.Menu tl = new GUIManager.Menu("Timeline");
        tl.onOpen = () -> {
            tl.items.clear();
            for (int i = 0; i < timeline.size(); i++) {
                final int k = i;
                Piece p = timeline.get(i);
                tl.add((i + 1) + ". " + p.clip.name()).value(() -> secs(p.length()), null).open(pieceMenu(k)).hint("In " + secs(p.in) + ", out " + secs(p.out) + ", " + TRANSITIONS[p.transition] + ", " + EFFECTS[p.effect]);
            }
            if (timeline.isEmpty()) tl.add("(the timeline is empty)").when(() -> false, () -> "add a clip first");
        };
        tl.onHighlight = () -> { if (!timeline.isEmpty()) { selected = Math.min(timeline.size() - 1, tl.sel); float t = 0; for (int i = 0; i < selected; i++) t += timeline.get(i).length(); playhead = t; playing = false; } };
        GUIManager.Menu proj = new GUIManager.Menu("Audio");
        proj.add("Sound").value(() -> muted ? "Muted" : "Recorded", d -> muted = !muted).hint("The sound that was recorded with each clip, or a silent film.");
        proj.add("Master level").value(() -> Math.round(master * 100) + "%", d -> master = Math.max(0, Math.min(1.5f, master + d * 0.1f))).hint("Applied to every piece on top of its own level.");
        GUIManager.Menu del = new GUIManager.Menu("Delete a clip");
        del.onOpen = () -> {
            del.items.clear();
            library = RecordingManager.clips();
            for (Clip c : library) del.add(c.name()).value(() -> secs(c.seconds()), null).run(() -> { timeline.removeIf(p -> p.clip.name().equals(c.name())); String e = RecordingManager.delete(c); cache.clear(); status = e == null ? "Deleted " + c.name() : e; gui.back(); }).confirm("Delete " + c.name() + " for good? Its frames and sound are removed from the disk.");
            if (library.isEmpty()) del.add("(no clips recorded)").when(() -> false, () -> "there is nothing to delete");
        };
        root.add("Add clip").open(lib).hint("Put a recorded clip on the end of the timeline.");
        root.add("Timeline").value(() -> timeline.size() + " piece" + (timeline.size() == 1 ? "" : "s") + ", " + secs(duration()), null).open(tl).hint("Trim, reorder, re-frame and grade each piece.");
        root.add("Audio").open(proj).hint("The film's sound.");
        root.add("Play preview").value(() -> playing ? "Playing" : "Stopped", null).run(() -> { playing = !playing; if (playhead >= duration() - 0.05f) playhead = 0; }).when(() -> !timeline.isEmpty(), () -> "the timeline is empty").hint("[P] plays and stops; [,] and [.] step a frame; [Home] goes to the start.");
        root.add("Export video").run(this::export).when(() -> !timeline.isEmpty() && exporting < 0, () -> timeline.isEmpty() ? "the timeline is empty" : "an export is running").confirm("Write the film to an .avi file in the exports folder?").hint("Motion-JPEG video with PCM sound: opens in any player or editor.");
        root.add("Delete a clip").open(del).hint("Remove a recording from the disk.");
        root.add("Back to Director Mode").run(() -> closeRequested = true).hint("The timeline is kept until Director Mode is left.");
    }

    private GUIManager.Menu pieceMenu(int k) {
        Piece p = timeline.get(k);
        GUIManager.Menu m = new GUIManager.Menu("Piece " + (k + 1));
        float step = 1f / Math.max(1, p.clip.fps()) * 5;
        m.add("In point").value(() -> secs(p.in), d -> p.in = Math.max(0, Math.min(p.out - 0.2f, p.in + d * step))).hint("Where in the clip this piece starts.");
        m.add("Out point").value(() -> secs(p.out), d -> p.out = Math.max(p.in + 0.2f, Math.min(p.clip.seconds(), p.out + d * step))).hint("Where in the clip this piece ends.");
        m.add("Speed").value(() -> String.format("%.2fx", p.speed), d -> p.speed = Math.max(0.25f, Math.min(2f, p.speed + d * 0.25f))).hint("Playback adjustment: slow motion down to a quarter, fast up to double.");
        m.add("Transition in").value(() -> TRANSITIONS[p.transition], d -> p.transition = Math.floorMod(p.transition + d, TRANSITIONS.length)).hint("How this piece arrives from the one before it.");
        m.add("Visual effect").value(() -> EFFECTS[p.effect], d -> p.effect = Math.floorMod(p.effect + d, EFFECTS.length)).hint("Applied to every frame of the piece.");
        m.add("Zoom").value(() -> String.format("%.1fx", p.zoom), d -> p.zoom = Math.max(1f, Math.min(2.5f, p.zoom + d * 0.1f))).hint("Camera positioning: punch in on the recorded picture.");
        m.add("Pan across").value(() -> Math.round(p.panX * 100) + "%", d -> p.panX = Math.max(-1f, Math.min(1f, p.panX + d * 0.1f))).when(() -> p.zoom > 1.01f, () -> "zoom in first: there is nothing to pan across at 1.0x").hint("Left and right inside the picture.");
        m.add("Pan up/down").value(() -> Math.round(p.panY * 100) + "%", d -> p.panY = Math.max(-1f, Math.min(1f, p.panY + d * 0.1f))).when(() -> p.zoom > 1.01f, () -> "zoom in first").hint("Up and down inside the picture.");
        m.add("Sound level").value(() -> p.clip.audio() ? Math.round(p.volume * 100) + "%" : "no sound", d -> p.volume = Math.max(0, Math.min(1.5f, p.volume + d * 0.1f))).when(() -> p.clip.audio(), () -> "this clip was recorded without sound").hint("This piece's own level.");
        // ---- 0.9: perspective. The camera of a take is chosen here, in the editor, and nowhere else
        GUIManager.Menu cam = new GUIManager.Menu("Perspective");
        java.util.function.BooleanSupplier taped = () -> tapeOf(p) != null;
        java.util.function.Supplier<String> noTape = () -> "this clip has no scene tape: it was recorded in an earlier session (or before 0.9), so it is footage only";
        cam.add("Camera").value(() -> CAMERAS[p.camera], d -> { p.camera = Math.floorMod(p.camera + d, CAMERAS.length); seat(p); }).when(taped, noTape).hint("As recorded: the footage. The others look at the same take from somewhere else - the picture on the right is the scene itself, live.");
        cam.add("Distance").value(() -> String.format("%.1f m", p.camDist), d -> p.camDist = Math.max(1.2f, Math.min(16, p.camDist + d * 0.5f))).when(() -> taped.getAsBoolean() && (p.camera == CAM_THIRD || p.camera == CAM_ORBIT), () -> "for the third-person and orbit cameras");
        cam.add("Height").value(() -> String.format("%.1f m", p.camHeight), d -> p.camHeight = Math.max(0.2f, Math.min(12, p.camHeight + d * 0.25f))).when(() -> taped.getAsBoolean() && (p.camera == CAM_THIRD || p.camera == CAM_ORBIT), () -> "for the third-person and orbit cameras");
        cam.add("Orbit speed").value(() -> String.format("%.2f", p.orbitSpeed), d -> p.orbitSpeed = Math.max(-1.5f, Math.min(1.5f, p.orbitSpeed + d * 0.05f))).when(() -> taped.getAsBoolean() && p.camera == CAM_ORBIT, () -> "for the orbit camera");
        cam.add("Lens").value(() -> p.fov <= 1 ? "As recorded" : Math.round(p.fov) + " deg", d -> p.fov = p.fov <= 1 ? 60 + d * 5 : Math.max(20, Math.min(110, p.fov + d * 5))).when(() -> taped.getAsBoolean() && p.camera != CAM_RECORDED, () -> "choose a camera first").hint("Wide or long.");
        cam.add("Place the camera").run(() -> { seat(p); framing = true; status = "Fly the camera: mouse to look, [WASD] [Space] [Ctrl] to move, [Shift] fast, wheel for the lens. [Enter] or right-click when it is placed."; }).when(() -> taped.getAsBoolean() && (p.camera == CAM_FIXED || p.camera == CAM_FREE), () -> "for the fixed and free cameras").hint("Take the camera in hand and put it where the shot should be seen from.");
        cam.add("Re-shoot from this camera").run(() -> reshoot(p)).when(() -> taped.getAsBoolean() && p.camera != CAM_RECORDED && shooting == null, () -> p.camera == CAM_RECORDED ? "choose a camera other than the recorded one" : "a re-shoot is running").confirm("Film this take again from the chosen camera? It becomes a new clip and takes this piece's place on the timeline.").hint("The take is played from its tape and filmed from the camera chosen above. With the free camera, you fly it while it films.");
        m.add("Perspective").value(() -> tapeOf(p) == null ? "footage only" : CAMERAS[p.camera], null).open(cam).hint("Look at this take from another camera, and film it again from there.");
        m.add("Split at the playhead").run(() -> { float t = 0; for (Piece q : timeline) { if (q == p) break; t += q.length(); } float into = (playhead - t) * p.speed; if (into > 0.2f && into < p.out - p.in - 0.2f) { Piece b = p.copy(); b.in = p.in + into; b.transition = 0; p.out = p.in + into; timeline.add(timeline.indexOf(p) + 1, b); status = "Split"; gui.back(); gui.back(); } else status = "Move the playhead inside this piece first"; }).hint("Cuts this piece in two where the playhead is.");
        m.add("Duplicate").run(() -> { timeline.add(timeline.indexOf(p) + 1, p.copy()); status = "Duplicated"; gui.back(); gui.back(); }).hint("A second copy of this piece, after it.");
        m.add("Move earlier").run(() -> { int i = timeline.indexOf(p); if (i > 0) { timeline.remove(i); timeline.add(i - 1, p); } }).when(() -> timeline.indexOf(p) > 0, () -> "it is already first").hint("Clip sequencing.");
        m.add("Move later").run(() -> { int i = timeline.indexOf(p); if (i >= 0 && i < timeline.size() - 1) { timeline.remove(i); timeline.add(i + 1, p); } }).when(() -> timeline.indexOf(p) < timeline.size() - 1, () -> "it is already last").hint("Clip sequencing.");
        m.add("Remove from timeline").run(() -> { timeline.remove(p); gui.back(); gui.back(); }).confirm("Take this piece off the timeline? (The clip itself is kept.)");
        m.onHighlight = () -> { float t = 0; for (Piece q : timeline) { if (q == p) break; t += q.length(); } if (!playing) playhead = Math.max(t, Math.min(t + p.length() - 0.05f, playhead)); shownAt = -1; };
        return m;
    }

    // ------------------------------------------------------------------ rendering a moment of the film
    private BufferedImage load(Clip c, float seconds) {
        int i = Math.max(0, Math.min(c.frames() - 1, Math.round(seconds * c.fps())));
        String key = c.name() + "#" + i;
        BufferedImage img = cache.get(key);
        if (img != null) return img;
        try {
            BufferedImage raw = javax.imageio.ImageIO.read(c.frame(i).toFile());
            if (raw == null) return null;
            img = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_RGB);
            img.getGraphics().drawImage(raw, 0, 0, null);
            cache.put(key, img);
            return img;
        } catch (Exception e) { return null; }
    }

    /** The piece playing at a time on the timeline: {index, seconds into the piece}, or null past the end. */
    public float[] at(float t) {
        float acc = 0;
        for (int i = 0; i < timeline.size(); i++) { float len = timeline.get(i).length(); if (t < acc + len || i == timeline.size() - 1) return new float[]{i, Math.max(0, Math.min(len, t - acc))}; acc += len; }
        return null;
    }

    private static void framed(BufferedImage src, Piece p, int w, int h, int[] out) {
        // camera positioning: the window of the recorded picture that is used
        float vw = src.getWidth() / p.zoom, vh = src.getHeight() / p.zoom;
        float x0 = (src.getWidth() - vw) * (0.5f + p.panX * 0.5f), y0 = (src.getHeight() - vh) * (0.5f + p.panY * 0.5f);
        int[] s = ((DataBufferInt) src.getRaster().getDataBuffer()).getData();
        int sw = src.getWidth(), sh = src.getHeight();
        for (int y = 0; y < h; y++) {
            int sy = Math.max(0, Math.min(sh - 1, (int) (y0 + (y + 0.5f) * vh / h)));
            for (int x = 0; x < w; x++) { int sx = Math.max(0, Math.min(sw - 1, (int) (x0 + (x + 0.5f) * vw / w))); out[y * w + x] = s[sy * sw + sx] & 0xFFFFFF; }
        }
        if (p.effect == 0) return;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int c = out[y * w + x], r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255, l = (r * 77 + g * 150 + b * 29) >> 8;
            switch (p.effect) {
                case 1 -> { r = g = b = l; }
                case 2 -> { r = Math.min(255, l * 112 / 100 + 18); g = l * 96 / 100 + 6; b = l * 72 / 100; }
                case 3 -> { r = r * 82 / 100; g = g * 94 / 100; b = Math.min(255, b * 112 / 100 + 8); }
                case 4 -> { float dx = (x - w / 2f) / (w / 2f), dy = (y - h / 2f) / (h / 2f), v = Math.max(0.25f, 1 - 0.55f * (dx * dx + dy * dy)); r = (int) (r * v); g = (int) (g * v); b = (int) (b * v); }
                default -> { int bar = h * 12 / 100; if (y < bar || y >= h - bar) { r = g = b = 0; } }
            }
            out[y * w + x] = (r << 16) | (g << 8) | b;
        }
    }

    /** The picture of the film at a time: a piece of a clip, framed, graded, and mixed with what came before it during a transition. */
    public boolean render(float t, int w, int h, int[] out) {
        float[] where = at(t);
        if (where == null) return false;
        Piece p = timeline.get((int) where[0]);
        BufferedImage img = load(p.clip, p.in + where[1] * p.speed);
        if (img == null) { java.util.Arrays.fill(out, 0); return false; }
        framed(img, p, w, h, out);
        int i = (int) where[0];
        if (p.transition != 0 && where[1] < TRANSITION_SECONDS && i > 0) {
            float k = where[1] / TRANSITION_SECONDS;
            if (p.transition == 1) {
                Piece q = timeline.get(i - 1);
                BufferedImage prev = load(q.clip, q.out - 0.02f);
                if (prev != null) { int[] a = new int[w * h]; framed(prev, q, w, h, a); for (int j = 0; j < out.length; j++) out[j] = mix(a[j], out[j], k); }
            } else for (int j = 0; j < out.length; j++) out[j] = mix(0, out[j], Math.max(0, k * 2 - 1));
        }
        // the other half of a dip: the end of a piece goes down to black when the next one comes up from it
        if (i + 1 < timeline.size() && timeline.get(i + 1).transition == 2 && where[1] > p.length() - TRANSITION_SECONDS / 2) {
            float k = (p.length() - where[1]) / (TRANSITION_SECONDS / 2);
            for (int j = 0; j < out.length; j++) out[j] = mix(0, out[j], Math.max(0, Math.min(1, k)));
        }
        return true;
    }

    private static int mix(int a, int b, float k) {
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k), g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k), bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return (r << 16) | (g << 8) | bl;
    }

    // ------------------------------------------------------------------ export
    /** Writes the film. It runs on a thread of its own; {@link #exporting} says how far it has got. */
    public void export() {
        if (timeline.isEmpty() || exporting >= 0) return;
        final List<Piece> pieces = new ArrayList<>(timeline);
        final float total = duration();
        final int fps = pieces.get(0).clip.fps(), w = pieces.get(0).clip.width() & ~1, h = pieces.get(0).clip.height() & ~1;
        final boolean sound = !muted && pieces.stream().anyMatch(p -> p.clip.audio());
        exporting = 0; exported = "";
        Thread t = new Thread(() -> {
            Path out = exportsDir().resolve(name + "_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".avi");
            try {
                Files.createDirectories(out.getParent());
                javax.imageio.ImageWriter jw = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
                javax.imageio.ImageWriteParam prm = jw.getDefaultWriteParam();
                prm.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                prm.setCompressionQuality(0.9f);
                int n = Math.max(1, Math.round(total * fps)), rate = 44100, perFrame = rate / fps * 4;
                Map<Clip, RandomAccessFile> wavs = new LinkedHashMap<>();
                try (AviWriter avi = new AviWriter(out, w, h, fps, sound ? rate : 0, 2)) {
                    int[] px = new int[w * h];
                    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                    byte[] pcm = new byte[perFrame];
                    for (int f = 0; f < n; f++) {
                        float time = f / (float) fps;
                        render(time, w, h, px);
                        img.setRGB(0, 0, w, h, px, 0, w);
                        ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 16);
                        try (javax.imageio.stream.ImageOutputStream os = javax.imageio.ImageIO.createImageOutputStream(bos)) { jw.setOutput(os); jw.write(null, new javax.imageio.IIOImage(img, null, null), prm); }
                        avi.frame(bos.toByteArray());
                        if (sound) {
                            // the sound of this frame: the piece's own recording at the place the picture came from, at the piece's level
                            java.util.Arrays.fill(pcm, (byte) 0);
                            float[] where = at(time);
                            Piece p = where == null ? null : pieces.get(Math.min(pieces.size() - 1, (int) where[0]));
                            if (p != null && p.clip.audio()) {
                                RandomAccessFile wav = wavs.get(p.clip);
                                if (wav == null) { wav = new RandomAccessFile(p.clip.audioFile().toFile(), "r"); wavs.put(p.clip, wav); }
                                long start = 44 + ((long) ((p.in + where[1] * p.speed) * rate)) * 4;
                                float vol = p.volume * master, step = p.speed;
                                byte[] src = new byte[(int) (perFrame * step) + 8 & ~3];
                                if (start < wav.length()) { wav.seek(start); int got = Math.max(0, wav.read(src));
                                    for (int s = 0; s < perFrame / 4; s++) { int si = ((int) (s * step)) * 4; if (si + 3 >= got) break;
                                        for (int ch = 0; ch < 2; ch++) { int v = (short) ((src[si + ch * 2] & 255) | (src[si + ch * 2 + 1] << 8)); v = Math.max(-32768, Math.min(32767, Math.round(v * vol))); pcm[s * 4 + ch * 2] = (byte) v; pcm[s * 4 + ch * 2 + 1] = (byte) (v >> 8); } } }
                            }
                            avi.sound(pcm, perFrame);
                        }
                        exporting = (f + 1) / (float) n;
                    }
                } finally { for (RandomAccessFile r : wavs.values()) try { r.close(); } catch (Exception ignored) { } jw.dispose(); }
                exported = out.toString();
                status = "Exported " + out.getFileName() + "  (" + String.format("%.1f", total) + " s" + (sound ? ", with sound" : ", silent") + ")";
            } catch (Exception e) { status = "Export failed: " + e.getMessage(); }
            exporting = -1;
        }, "director-export");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ 0.9: the scene behind a take
    private boolean framing;
    private Piece shooting;
    private int shotDone, shotTotal;
    private float shotClock;
    private boolean shotDue;
    private int sceneTex, sceneW, sceneH;
    private boolean sceneFresh;
    private float tlX, tlY, tlW, tlH;
    private boolean scrubbing;
    /** Re-shoots made this session (tests). */
    public int reshoots;

    /** Chooses a piece's camera (what the Perspective menu's first row does). */
    public void setCamera(Piece p, int camera) { p.camera = Math.floorMod(camera, CAMERAS.length); seat(p); }
    /** Puts the playhead somewhere on the timeline (seconds). */
    public void seek(float seconds) { playhead = Math.max(0, Math.min(duration(), seconds)); playing = false; shownAt = -1; }

    public SceneTape tapeOf(Piece p) { return p == null ? null : RecordingManager.TAPES.get(p.clip.name()); }
    public boolean framing() { return framing; }
    public boolean shooting() { return shooting != null; }
    /** True while the pointer is the camera (the game locks and hides it). */
    public boolean wantsPointerLocked() { return framing || shooting != null && shooting.camera == CAM_FREE; }

    /** Gives a fixed or free camera somewhere to start from: where the gameplay camera was at this point of the take. */
    private void seat(Piece p) {
        SceneTape t = tapeOf(p);
        if (t == null) return;
        int f = frameOf(p);
        if (p.camAt == null) { float[] c = t.camera(f); p.camAt = c == null ? new float[5] : new float[]{c[0], c[1] + 0.4f, c[2], c[3], c[4]}; }
        float[] s = t.subject(f);
        if (s != null && p.orbitFrom == 0) p.orbitFrom = s[3] + (float) Math.PI;
        sceneFresh = false;
    }

    /** The piece under the playhead and the frame of its clip that is showing. */
    private Piece current() { float[] w = timeline.isEmpty() ? null : at(Math.min(playhead, Math.max(0, duration() - 0.01f))); return w == null ? null : timeline.get((int) w[0]); }
    private int frameOf(Piece p) {
        float t = 0;
        for (Piece q : timeline) { if (q == p) break; t += q.length(); }
        float into = Math.max(0, Math.min(p.length(), playhead - t));
        return Math.max(0, Math.min(p.clip.frames() - 1, Math.round((p.in + into * p.speed) * p.clip.fps())));
    }

    /** The piece whose scene is to be drawn in the world this frame (a re-shoot, or a piece being looked at through another camera), or null. */
    private Piece scenePiece() {
        if (shooting != null) return shooting;
        Piece p = current();
        return p != null && p.camera != CAM_RECORDED && tapeOf(p) != null ? p : null;
    }

    /** Where a piece's camera is on a frame of its take: {x, y, z, yaw, pitch, fov}. */
    public float[] cameraFor(Piece p, int frame) {
        SceneTape t = tapeOf(p);
        float[] rec = t.camera(frame), s = t.subject(frame);
        float[] out = rec == null ? new float[6] : rec.clone();
        if (p.fov > 1) out[5] = p.fov;
        if ((p.camera == CAM_FIXED || p.camera == CAM_FREE) && p.camAt != null) { System.arraycopy(p.camAt, 0, out, 0, 5); return out; }
        if (s == null || p.camera == CAM_RECORDED) return out;
        float ang = p.camera == CAM_ORBIT ? p.orbitFrom + p.orbitSpeed * frame / (float) Math.max(1, p.clip.fps()) : s[3] + (float) Math.PI;
        // third person stands behind the way the character faces; the orbit goes round them
        float cx = s[0] - (float) -Math.sin(ang - (float) Math.PI) * p.camDist, cz = s[2] - (float) -Math.cos(ang - (float) Math.PI) * p.camDist, cy = s[1] + p.camHeight;
        float dx = s[0] - cx, dy = s[1] + 1.25f - cy, dz = s[2] - cz, hl = (float) Math.sqrt(dx * dx + dz * dz);
        out[0] = cx; out[1] = cy; out[2] = cz; out[3] = (float) Math.atan2(-dx, -dz); out[4] = (float) Math.atan2(dy, Math.max(0.01f, hl));
        return out;
    }

    /**
     * Before the world is drawn: if the editor wants the scene of a take on the screen, the characters and vehicles of that
     * moment are stood where the tape has them and the picture is taken from the piece's camera. Returns whether it did.
     */
    public boolean beginScene(absentialis.scene.RaidScreen screen) {
        Piece p = scenePiece();
        SceneTape t = tapeOf(p);
        if (p == null || t == null || t.size() == 0) return false;
        int f = shooting != null ? Math.min(shotDone, t.size() - 1) : Math.min(frameOf(p), t.size() - 1);
        t.apply(f);
        screen.ctx().ar.replay = t;
        screen.ctx().ar.capture = null;
        screen.camOverride = cameraFor(p, f);
        return true;
    }

    /** After the world is drawn and before any interface: the picture is kept for the preview, filmed if a re-shoot is due, and everybody is put back. */
    public void endScene(absentialis.scene.RaidScreen screen, int w, int h) {
        if (sceneTex == 0) { sceneTex = glGenTextures(); glBindTexture(GL_TEXTURE_2D, sceneTex); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR); }
        glBindTexture(GL_TEXTURE_2D, sceneTex);
        glCopyTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, 0, 0, w, h, 0);
        sceneW = w; sceneH = h; sceneFresh = true;
        if (shooting != null && shotDue && host != null) { host.recording.captureNow(w, h); shotDue = false; shotDone++; }
        SceneTape t = tapeOf(scenePiece());
        if (t != null) t.restore();
        screen.ctx().ar.replay = null;
        screen.camOverride = null;
    }

    /** Films a piece's whole take again from the piece's camera. */
    public void reshoot(Piece p) {
        SceneTape t = tapeOf(p);
        if (t == null || host == null || shooting != null) return;
        seat(p);
        String problem = host.recording.startDerived(p.clip);
        if (problem != null) { status = problem; return; }
        shooting = p; shotDone = 0; shotTotal = Math.min(p.clip.frames(), t.size()); shotClock = 0; shotDue = true;
        playing = false; framing = false;
        while (gui.depth() > 1) gui.back();
        status = "Re-shooting from the " + CAMERAS[p.camera].toLowerCase() + " camera...";
    }

    private void finishShoot(boolean keep) {
        Piece p = shooting;
        SceneTape t = tapeOf(p);
        shooting = null; shotDue = false;
        String made = host.recording.stop();
        Clip nc = made == null ? null : RecordingManager.clip(made);
        if (!keep || nc == null) { if (nc != null) RecordingManager.delete(nc); status = keep ? "The re-shoot could not be saved" : "Re-shoot abandoned"; return; }
        // the new clip is the same take: its tape is the take's tape, so it can be filmed again from somewhere else
        if (t != null) RecordingManager.TAPES.put(nc.name(), t);
        String was = CAMERAS[p.camera];
        p.clip = nc; p.out = Math.min(p.out, nc.seconds()); p.camera = CAM_RECORDED;
        cache.clear(); shownAt = -1; reshoots++;
        library = RecordingManager.clips();
        status = "Re-shot from the " + was.toLowerCase() + " camera: " + nc.name();
    }

    /** Flying the camera of the piece being framed or filmed. */
    private void fly(Input in, Piece p, float dt) {
        if (p == null || p.camAt == null) return;
        float[] c = p.camAt;
        c[3] -= (float) in.dx * 0.0022f; c[4] = Math.max(-1.5f, Math.min(1.5f, c[4] - (float) in.dy * 0.0022f));
        float f = (in.keyDown(GLFW_KEY_W) ? 1 : 0) - (in.keyDown(GLFW_KEY_S) ? 1 : 0), s = (in.keyDown(GLFW_KEY_D) ? 1 : 0) - (in.keyDown(GLFW_KEY_A) ? 1 : 0);
        float fx = (float) (-Math.sin(c[3]) * Math.cos(c[4])), fy = (float) Math.sin(c[4]), fz = (float) (-Math.cos(c[3]) * Math.cos(c[4]));
        float sp = 7f * (in.keyDown(GLFW_KEY_LEFT_SHIFT) ? 3.5f : 1) * dt;
        c[0] += (fx * f + (float) Math.cos(c[3]) * s) * sp; c[1] += fy * f * sp + (in.keyDown(GLFW_KEY_SPACE) ? sp : 0) - (in.keyDown(GLFW_KEY_LEFT_CONTROL) ? sp : 0); c[2] += (fz * f - (float) Math.sin(c[3]) * s) * sp;
        if (in.scroll != 0) p.fov = Math.max(20, Math.min(110, (p.fov <= 1 ? 70 : p.fov) - (float) in.scroll * 4));
    }

    // ------------------------------------------------------------------ the screen
    /** Keys and the pointer, each frame. Returns true while the editor wants to stay open. */
    public boolean update(Input in, float dt) {
        // ---- a re-shoot runs at the take's own pace: one frame of the tape for each picture taken
        if (shooting != null) {
            if (shooting.camera == CAM_FREE) fly(in, shooting, dt);
            if (in.keyPressed(GLFW_KEY_ESCAPE)) { finishShoot(false); return true; }
            if (!shotDue) {
                shotClock += dt;
                if (shotDone >= shotTotal) finishShoot(true);
                else if (shotClock * shooting.clip.fps() >= shotDone) shotDue = true;
            }
            return true;
        }
        // ---- the camera in hand
        if (framing) {
            Piece p = current();
            fly(in, p, dt);
            if (in.keyPressed(GLFW_KEY_ENTER) || in.keyPressed(GLFW_KEY_ESCAPE) || in.mousePressed(1) || p == null) { framing = false; status = "Camera placed"; }
            return true;
        }
        // ---- the pointer on the timeline: click a piece to select it, drag to scrub
        float mx = (float) in.mouseX, my = (float) in.mouseY;
        if (in.mousePressed(0) && tlW > 0 && mx >= tlX && mx <= tlX + tlW && my >= tlY - 6 && my <= tlY + tlH + 6) scrubbing = true;
        if (!in.mouseDown(0)) scrubbing = false;
        if (scrubbing && !timeline.isEmpty()) {
            playhead = Math.max(0, Math.min(duration(), (mx - tlX) / tlW * duration())); playing = false;
            float[] w = at(Math.min(playhead, duration() - 0.01f));
            if (w != null) selected = (int) w[0];
        }
        if (in.keyPressed(GLFW_KEY_P)) { playing = !playing; if (playhead >= duration() - 0.05f) playhead = 0; }
        if (in.keyPressed(GLFW_KEY_HOME)) { playhead = 0; playing = false; }
        float frame = timeline.isEmpty() ? 0.05f : 1f / timeline.get(0).clip.fps();
        if (in.keyPressed(GLFW_KEY_COMMA)) { playhead = Math.max(0, playhead - frame); playing = false; }
        if (in.keyPressed(GLFW_KEY_PERIOD)) { playhead = Math.min(duration(), playhead + frame); playing = false; }
        if (playing) { playhead += dt; if (playhead >= duration()) { playhead = duration(); playing = false; } }
        gui.tick(dt);
        int r = scrubbing ? 1 : gui.input(in, dt);
        if (r == 2 || in.keyPressed(GLFW_KEY_ESCAPE)) closeRequested = true;
        return !closeRequested;
    }

    private void upload(int[] px, int w, int h) {
        if (previewTex == 0) { previewTex = glGenTextures(); glBindTexture(GL_TEXTURE_2D, previewTex); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR); }
        ByteBuffer b = MemoryUtil.memAlloc(w * h * 4);
        for (int c : px) b.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) 255);
        b.flip();
        glBindTexture(GL_TEXTURE_2D, previewTex);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, b);
        MemoryUtil.memFree(b);
        texW = w; texH = h;
    }

    public void draw(Ui ui, float dt) {
        float W = ui.w, H = ui.h;
        ui.b.rect(0, 0, W, H, 0xFF08090A);
        ui.text("CINEMATIC EDITOR", ui.s(24), ui.s(16), 22, Ui.TXT_HI);
        ui.textR(status, W - ui.s(24), ui.s(22), 12, status.startsWith("Export failed") ? Ui.RED : Ui.AMBER);
        float mw = ui.s(440);
        gui.draw(ui, ui.s(24), ui.s(60), mw, "EDITOR");
        // ---- preview
        float px = ui.s(24) + mw + ui.s(24), pw = W - px - ui.s(24), ph = pw * 9 / 16f, py = ui.s(60);
        if (ph > H - ui.s(260)) { ph = H - ui.s(260); pw = ph * 16 / 9f; }
        ui.b.rect(px - 2, py - 2, pw + 4, ph + 4, 0xFF2E2A22);
        ui.b.rect(px, py, pw, ph, 0xFF000000);
        Piece live = scenePiece();
        if (live != null && sceneFresh && sceneTex != 0) {
            // 0.9: the scene itself, from this piece's camera (the back buffer of the frame just drawn: bottom row first, so it is flipped)
            ui.b.flush();
            ui.b.quad(sceneTex, px, py, pw, ph, 0, 1, 1, 0, 1, 1, 1, 1, 0);
            String tag = shooting != null ? "RE-SHOOTING   " + shotDone + " / " + shotTotal + (shooting.camera == CAM_FREE ? "   - you have the camera: mouse, [WASD], [Space]/[Ctrl]" : "") + "   [Esc] abandons" : CAMERAS[live.camera].toUpperCase() + " CAMERA - LIVE SCENE" + (framing ? "   - placing: mouse, [WASD], [Space]/[Ctrl], wheel = lens, [Enter] done" : "");
            float tw = ui.tw(tag, 11) + ui.s(16);
            ui.b.rect(px + ui.s(8), py + ui.s(8), tw, ui.s(20), 0xC00A0B0D);
            ui.b.rect(px + ui.s(8), py + ui.s(8), ui.s(3), ui.s(20), shooting != null ? 0xFFE03A2E : Ui.AMBER);
            ui.text(tag, px + ui.s(16), py + ui.s(12), 11, Ui.TXT_HI);
            if (shooting != null) ui.b.rect(px, py + ph - ui.s(4), pw * shotDone / (float) Math.max(1, shotTotal), ui.s(4), 0xFFE03A2E);
        } else if (!timeline.isEmpty()) {
            int rw = 640, rh = 360;
            if (Math.abs(playhead - shownAt) > 1e-4f || previewTex == 0) { int[] buf = new int[rw * rh]; render(Math.min(playhead, Math.max(0, duration() - 0.01f)), rw, rh, buf); ui.b.flush(); upload(buf, rw, rh); shownAt = playing ? playhead : playhead; }
            ui.b.quad(previewTex, px, py, pw, ph, 0, 0, 1, 1, 1, 1, 1, 1, 0);
        } else ui.textC("Add a clip to begin", px + pw / 2, py + ph / 2 - ui.s(8), 16, Ui.TXT_DIM);
        // ---- timeline strip
        float ty = py + ph + ui.s(18), th = ui.s(54), total = Math.max(0.01f, duration());
        ui.text(String.format("%s   %.1f s of %.1f s", playing ? "PLAYING" : "STOPPED", playhead, duration()), px, ty - ui.s(2), 11, playing ? Ui.GREEN : Ui.TXT_DIM);
        ty += ui.s(16);
        ui.b.rect(px, ty, pw, th, 0xFF14161A);
        tlX = px; tlY = ty; tlW = pw; tlH = th;
        float acc = 0;
        int[] cols = {0xFF4A6A8A, 0xFF8A6A4A, 0xFF5A8A5A, 0xFF8A4A6A, 0xFF6A5A8A};
        for (int i = 0; i < timeline.size(); i++) {
            Piece p = timeline.get(i);
            float x0 = px + pw * acc / total, x1 = px + pw * (acc + p.length()) / total;
            ui.b.rect(x0 + 1, ty + ui.s(4), Math.max(2, x1 - x0 - 2), th - ui.s(8), Ui.alpha(cols[i % cols.length], i == selected ? 1f : 0.6f));
            if (p.transition != 0) ui.b.rect(x0 + 1, ty + ui.s(4), Math.min(x1 - x0 - 2, pw * TRANSITION_SECONDS / total), th - ui.s(8), 0x60FFFFFF);
            ui.b.scissor((int) x0, (int) ty, (int) Math.max(1, x1 - x0), (int) th);
            ui.text((i + 1) + "  " + EFFECTS[p.effect] + (p.speed != 1 ? String.format("  %.2fx", p.speed) : "") + (p.camera != 0 ? "  [" + CAMERAS[p.camera] + "]" : ""), x0 + ui.s(6), ty + ui.s(10), 10, Ui.TXT_HI);
            ui.text(secs(p.length()), x0 + ui.s(6), ty + ui.s(28), 10, Ui.TXT);
            ui.b.noScissor();
            acc += p.length();
        }
        ui.b.rect(px + pw * Math.min(1, playhead / total) - 1, ty - ui.s(4), 2, th + ui.s(8), 0xFFFFFFFF);
        if (exporting >= 0) {
            float ey = ty + th + ui.s(16);
            ui.b.rect(px, ey, pw, ui.s(18), 0xFF14161A);
            ui.b.rect(px, ey, pw * exporting, ui.s(18), 0xFFB03A2E);
            ui.textC("EXPORTING  " + Math.round(exporting * 100) + "%", px + pw / 2, ey + ui.s(3), 11, Ui.TXT_HI);
        } else if (!exported.isEmpty()) absentialis.ui.Layout.fit(ui, "Last export: " + exported, px, ty + th + ui.s(16), pw, 11, 9, Ui.GREEN);
        ui.text("Mouse: menus, click or drag the timeline   [P] play/stop   [,] [.] step   [Home] start   [Backspace] back   [Esc] leave the editor", px, H - ui.s(28), 10, Ui.TXT_DIM);
    }
}
