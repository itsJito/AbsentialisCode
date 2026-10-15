package absentialis.director;

import static org.lwjgl.opengl.GL11.*;

import absentialis.engine.audio.UltraEngine;
import absentialis.engine.util.Json;
import absentialis.persistence.SaveSystem;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.lwjgl.system.MemoryUtil;

/**
 * RecordingManager: capturing a scene. Recording is manual - it is started and it is stopped - and a clip is never
 * longer than three minutes ({@link #MAX_SECONDS}); at the limit it stops by itself and says so.
 *
 * What is captured is the finished picture of the world, before any interface is drawn over it, at a fixed
 * {@link #FPS} frames a second, and the sound of the game as it was heard. A clip is a folder: its frames as JPEG
 * files, its sound as {@code audio.wav}, and a small {@code clip.json} that says how long it is. The frames are
 * scaled and compressed on a thread of their own, so recording costs the game one read of the back buffer a frame.
 *
 * A clip is footage, not a replay: the world is not re-simulated from it (there is no action replay in this version).
 * Recording stages nothing and edits nothing - that is {@link ClipEditor}, a separate system that works on clips.
 */
public final class RecordingManager {
    public static final float MAX_SECONDS = 180f;
    public static int FPS = 20, MAX_WIDTH = 960;
    public static float JPEG_QUALITY = 0.86f;

    /** A finished clip on disk. */
    public record Clip(String name, Path dir, int frames, int fps, int width, int height, boolean audio) {
        public float seconds() { return frames / (float) Math.max(1, fps); }
        public Path frame(int i) { return dir.resolve(String.format("frame_%05d.jpg", Math.max(0, Math.min(frames - 1, i)))); }
        public Path audioFile() { return dir.resolve("audio.wav"); }
    }

    private final UltraEngine audio;
    /** 0.9: the scene tape of the clip being recorded, and the tapes of every clip recorded this session by clip name (see {@link SceneTape}). */
    public SceneTape tape;
    public static final Map<String, SceneTape> TAPES = new LinkedHashMap<>();
    /** Where the vehicles of the scene are read from when a tape frame is committed. */
    public java.util.function.Supplier<List<absentialis.scene.VehicleSystem.Car>> cars = List::of;
    /** A re-shoot in progress: a clip being filmed again from a tape, frame for frame, with the sound of the original. */
    private Clip derivedFrom;
    private boolean recording;
    private float clock, sinceFrame;
    private int frames, width, height, outW, outH;
    private Path dir;
    private String name = "";
    public String status = "", lastSaved = "";
    private final ArrayBlockingQueue<Object[]> queue = new ArrayBlockingQueue<>(48);
    private Thread writer;
    private final AtomicInteger written = new AtomicInteger(), dropped = new AtomicInteger();
    private OutputStream pcm;
    private long pcmBytes;
    private Path pcmFile;

    public RecordingManager(UltraEngine audio) { this.audio = audio; }

    public static Path root() { Path p = SaveSystem.dir.toAbsolutePath().getParent(); return (p == null ? Path.of(".") : p).resolve("recordings"); }

    public boolean recording() { return recording; }
    public float seconds() { return clock; }
    public int framesCaptured() { return frames; }
    public int framesDropped() { return dropped.get(); }

    /** REC 00:12 / 03:00 */
    public String clock() { int s = (int) clock, m = (int) MAX_SECONDS; return String.format("%02d:%02d / %02d:%02d", s / 60, s % 60, m / 60, m % 60); }

    /** Starts a clip. Null if it started, otherwise why not. */
    public String start() {
        if (recording) return "Already recording";
        name = "clip_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        dir = root().resolve(name);
        try { Files.createDirectories(dir); } catch (Exception e) { return "Cannot create " + dir + ": " + e.getMessage(); }
        frames = 0; clock = 0; sinceFrame = 1; written.set(0); dropped.set(0); width = 0;
        queue.clear();
        writer = new Thread(this::writeLoop, "director-recorder");
        writer.setDaemon(true);
        writer.start();
        // the sound of the game, as the mixer hands it to the device
        pcmBytes = 0; pcm = null; pcmFile = dir.resolve("audio.pcm");
        if (audio != null && audio.available) try {
            OutputStream file = new BufferedOutputStream(Files.newOutputStream(pcmFile), 1 << 16);
            pcm = new OutputStream() {
                @Override public void write(int b) { }
                @Override public synchronized void write(byte[] b, int off, int len) throws java.io.IOException { file.write(b, off, len); pcmBytes += len; }
                @Override public synchronized void close() throws java.io.IOException { file.close(); }
            };
            audio.tap = pcm;
        } catch (Exception e) { pcm = null; }
        recording = true;
        tape = new SceneTape();
        derivedFrom = null;
        status = "Recording";
        return null;
    }

    /**
     * 0.9: starts a re-shoot of a clip - the same take, filmed again from another camera. Frames are taken with
     * {@link #captureNow}, one for each frame of the original, so the new clip is exactly as long and its sound still fits.
     * Null if it started, otherwise why not.
     */
    public String startDerived(Clip src) {
        if (recording) return "A recording is running";
        int k = 2;
        String base = src.name().replaceAll("_cam\\d+$", "");
        while (Files.exists(root().resolve(base + "_cam" + k))) k++;
        name = base + "_cam" + k;
        dir = root().resolve(name);
        try { Files.createDirectories(dir); } catch (Exception e) { return "Cannot create " + dir + ": " + e.getMessage(); }
        frames = 0; clock = 0; sinceFrame = 0; written.set(0); dropped.set(0); width = 0;
        queue.clear();
        pcm = null; pcmBytes = 0;
        tape = null; derivedFrom = src;
        recording = true;
        writer = new Thread(this::writeLoop, "director-reshoot");
        writer.setDaemon(true);
        writer.start();
        status = "Re-shooting " + src.name();
        return null;
    }

    /** Takes the picture in the back buffer as the next frame of a re-shoot. Waits for the writer rather than dropping: a re-shoot has no clock to keep up with. */
    public void captureNow(int w, int h) {
        if (!recording || derivedFrom == null) return;
        clock = frames / (float) FPS;
        for (int tries = 0; tries < 400 && queue.remainingCapacity() == 0; tries++) { try { Thread.sleep(5); } catch (InterruptedException e) { return; } }
        grab(w, h);
    }

    public boolean reshooting() { return recording && derivedFrom != null; }

    /** Called once a frame with the finished picture in the back buffer. Takes a frame when one is due. */
    public void frame(int w, int h, float dt) {
        if (!recording) return;
        clock += dt; sinceFrame += dt;
        if (clock >= MAX_SECONDS) { stop(); status = "Stopped at the three-minute limit: " + lastSaved; return; }
        if (derivedFrom != null) return;                                  // a re-shoot takes its frames when it is told to (captureNow)
        if (sinceFrame < 1f / FPS) return;
        sinceFrame = Math.min(sinceFrame - 1f / FPS, 1f / FPS);
        if (grab(w, h) && tape != null) tape.commit(cars.get());
    }

    /** Reads the back buffer and queues it as the next frame. False when the frame was let go. */
    private boolean grab(int w, int h) {
        if (width == 0) { width = w; height = h; outW = Math.min(MAX_WIDTH, w) & ~1; outH = Math.round(outW * h / (float) w) & ~1; }
        if (w != width || h != height) return false;                     // the window was resized mid-clip: those frames are left out
        ByteBuffer buf = MemoryUtil.memAlloc(w * h * 3);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, w, h, GL_RGB, GL_UNSIGNED_BYTE, buf);
        byte[] px = new byte[w * h * 3];
        buf.get(px);
        MemoryUtil.memFree(buf);
        if (!queue.offer(new Object[]{frames, px})) { dropped.incrementAndGet(); return false; }   // the disk cannot keep up: this frame is let go rather than the game stalled
        frames++;
        return true;
    }

    private void writeLoop() {
        javax.imageio.ImageWriter jw = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
        javax.imageio.ImageWriteParam prm = jw.getDefaultWriteParam();
        prm.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        prm.setCompressionQuality(JPEG_QUALITY);
        try {
            while (true) {
                Object[] job = queue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (job == null) { if (!recording) break; continue; }
                int index = (Integer) job[0];
                byte[] px = (byte[]) job[1];
                BufferedImage img = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
                int[] row = new int[outW];
                // box-ish nearest scaling with the flip (the back buffer is read bottom row first)
                for (int y = 0; y < outH; y++) {
                    int sy = height - 1 - (int) ((y + 0.5f) * height / outH);
                    for (int x = 0; x < outW; x++) { int sx = (int) ((x + 0.5f) * width / outW), i = (sy * width + sx) * 3; row[x] = ((px[i] & 255) << 16) | ((px[i + 1] & 255) << 8) | (px[i + 2] & 255); }
                    img.setRGB(0, y, outW, 1, row, 0, outW);
                }
                File f = dir.resolve(String.format("frame_%05d.jpg", index)).toFile();
                try (javax.imageio.stream.ImageOutputStream os = javax.imageio.ImageIO.createImageOutputStream(f)) { jw.setOutput(os); jw.write(null, new javax.imageio.IIOImage(img, null, null), prm); }
                written.incrementAndGet();
            }
        } catch (Exception e) { status = "Recording failed: " + e.getMessage(); }
        jw.dispose();
    }

    /** Stops and saves the clip. Returns its name, or null if nothing was being recorded. */
    public String stop() {
        if (!recording) return null;
        recording = false;
        if (audio != null) audio.tap = null;
        try { if (writer != null) writer.join(8000); } catch (InterruptedException ignored) { }
        boolean hasAudio = false;
        // 0.9: a re-shoot carries the sound of the take it was shot from
        if (derivedFrom != null && derivedFrom.audio()) try { Files.copy(derivedFrom.audioFile(), dir.resolve("audio.wav"), java.nio.file.StandardCopyOption.REPLACE_EXISTING); hasAudio = true; } catch (Exception e) { hasAudio = false; }
        if (derivedFrom == null) try {
            if (pcm != null) {
                pcm.close();
                hasAudio = pcmBytes > 0;
                if (hasAudio) wav(pcmFile, dir.resolve("audio.wav"), pcmBytes);
                Files.deleteIfExists(pcmFile);
            }
        } catch (Exception e) { hasAudio = false; }
        Map<String, Object> m = new LinkedHashMap<>();
        if (tape != null && tape.size() > 0) TAPES.put(name, tape);
        if (derivedFrom != null) { m.put("reshotFrom", derivedFrom.name()); clock = frames / (float) FPS; }
        derivedFrom = null;
        m.put("name", name); m.put("frames", frames); m.put("fps", FPS); m.put("width", outW); m.put("height", outH); m.put("seconds", (double) clock); m.put("audio", hasAudio); m.put("dropped", dropped.get());
        try { Files.writeString(dir.resolve("clip.json"), Json.write(m), StandardCharsets.UTF_8); } catch (Exception e) { status = "Could not write clip.json: " + e.getMessage(); }
        lastSaved = name + "  (" + String.format("%.1f", frames / (float) FPS) + " s, " + frames + " frames" + (dropped.get() > 0 ? ", " + dropped.get() + " dropped" : "") + ")";
        status = "Saved " + lastSaved;
        return name;
    }

    /** Wraps the raw sound in a WAV header: 44.1 kHz, 16 bit, stereo, as the mixer writes it. */
    private static void wav(Path raw, Path out, long bytes) throws Exception {
        try (RandomAccessFile f = new RandomAccessFile(out.toFile(), "rw"); java.io.InputStream in = Files.newInputStream(raw)) {
            f.setLength(0);
            java.nio.ByteBuffer h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            h.put("RIFF".getBytes()).putInt((int) (36 + bytes)).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 2).putInt(UltraEngine.RATE).putInt(UltraEngine.RATE * 4).putShort((short) 4).putShort((short) 16);
            h.put("data".getBytes()).putInt((int) bytes);
            f.write(h.array());
            byte[] b = new byte[1 << 16];
            int n;
            while ((n = in.read(b)) > 0) f.write(b, 0, n);
        }
    }

    /** The clip a name refers to, or null. */
    public static Clip clip(String name) { for (Clip c : clips()) if (c.name().equals(name)) return c; return null; }

    /** 0.9: deletes a clip - its frames, its sound and its tape. Returns null when it is gone, otherwise why not. */
    public static String delete(Clip c) {
        if (c == null || !c.dir().startsWith(root())) return "Not a clip";
        try (var st = Files.list(c.dir())) { for (Path p : (Iterable<Path>) st::iterator) Files.deleteIfExists(p); }
        catch (Exception e) { return "Could not delete: " + e.getMessage(); }
        try { Files.deleteIfExists(c.dir()); } catch (Exception e) { return "Could not delete: " + e.getMessage(); }
        TAPES.remove(c.name());
        return null;
    }

    /** The clips on disk, newest first. */
    public static List<Clip> clips() {
        List<Clip> l = new ArrayList<>();
        Path root = root();
        if (!Files.isDirectory(root)) return l;
        try (var st = Files.list(root)) {
            for (Path d : (Iterable<Path>) st.sorted(java.util.Comparator.reverseOrder())::iterator) {
                Path meta = d.resolve("clip.json");
                if (!Files.isRegularFile(meta)) continue;
                try {
                    Map<String, Object> m = Json.parseObject(Files.readString(meta, StandardCharsets.UTF_8));
                    int frames = Json.integer(m, "frames", 0);
                    if (frames <= 0) continue;
                    l.add(new Clip(Json.str(m, "name", d.getFileName().toString()), d, frames, Json.integer(m, "fps", FPS), Json.integer(m, "width", 960), Json.integer(m, "height", 540), Json.bool(m, "audio", false) && Files.exists(d.resolve("audio.wav"))));
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        return l;
    }
}
