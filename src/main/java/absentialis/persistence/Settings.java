package absentialis.persistence;

import absentialis.engine.Input;
import absentialis.engine.util.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Global (not per-profile) options: accessibility, audio, controls (GDD 18.3/18.4). */
public final class Settings {
    public float uiScale = 1f, shake = 0.5f, sensitivity = 0.12f, fov = 75, master = 0.8f, sfx = 1f, ambience = 0.7f, music = 0.55f, voice = 1f, uiVolume = 0.8f;
    public boolean muteAll, muteMusic, muteSfx, muteVoice;
    public boolean subtitles = true, hitDirection = true, toggleAim, toggleCrouch = true, showFps;
    /** 0.6: sprint as a toggle instead of a hold, and the third-person camera (boom length in metres, shoulder offset, follow smoothing 0..1). */
    public boolean toggleSprint;
    public float tpsDistance = TPS_DISTANCE, tpsShoulder = 0.62f, tpsSmoothing = 0.6f;
    /** 0.8: the third-person camera sits closer (it was 3.4 m). The shortest and longest boom the slider offers. */
    public static final float TPS_DISTANCE = 2.5f, TPS_MIN = 1.6f, TPS_MAX = 5f;
    /** Which build wrote the settings file (0 = before 0.8): defaults that changed are moved on once, values the player chose are kept. */
    public int settingsVersion = 11;
    /** 1.1, HUD: keep every indicator on screen all the time, the HUD's own scale (0.75 .. 1.5) and the margin kept clear at the screen's edges (0 .. 0.1 of its height). */
    public boolean hudAlways;
    public float hudScale = 1f, hudSafe = 0.02f;
    // ---- graphics (hybrid GI renderer)
    /** 0 Low, 1 Medium, 2 High, 3 Ultra: shadow resolution, cascades, probe budget, shadowed lights. */
    public int gfxPreset = 2;
    public boolean gtao = true, ssr = true, volumetrics = true, taa = true, bloom = true, vsync = true;
    /** Internal resolution as a fraction of the window (0.5 .. 1.0). */
    public float renderScale = 1f;
    /** Fire / particle density (0.4 .. 1.5) and how much blood and debris persists (0 Low, 1 Standard, 2 High). */
    public float particleDetail = 1f;
    public int goreDetail = 1;
    public static final String[] GORE = {"Low", "Standard", "High"};
    /** Development overlay for the vehicle simulation (contact points, suspension, tyre forces, gear). */
    public boolean vehicleDebug;
    private static final Path FILE = Path.of("saves", "settings.json");

    public static Settings load(Input in) {
        Settings s = new Settings();
        try {
            if (!Files.exists(FILE)) return s;
            Map<String, Object> m = Json.parseObject(Files.readString(FILE, StandardCharsets.UTF_8));
            s.uiScale = (float) Json.num(m, "uiScale", 1);
            s.shake = (float) Json.num(m, "shake", 0.5);
            s.sensitivity = (float) Json.num(m, "sensitivity", 0.12);
            s.fov = (float) Json.num(m, "fov", 75);
            s.master = (float) Json.num(m, "master", 0.8);
            s.sfx = (float) Json.num(m, "sfx", 1);
            s.ambience = (float) Json.num(m, "ambience", 0.7);
            s.music = (float) Json.num(m, "music", 0.55);
            s.voice = (float) Json.num(m, "voice", 1);
            s.uiVolume = (float) Json.num(m, "uiVolume", 0.8);
            s.muteAll = Json.bool(m, "muteAll", false);
            s.muteMusic = Json.bool(m, "muteMusic", false);
            s.muteSfx = Json.bool(m, "muteSfx", false);
            s.muteVoice = Json.bool(m, "muteVoice", false);
            s.subtitles = Json.bool(m, "subtitles", true);
            s.hitDirection = Json.bool(m, "hitDirection", true);
            s.toggleAim = Json.bool(m, "toggleAim", false);
            s.toggleCrouch = Json.bool(m, "toggleCrouch", true);
            s.toggleSprint = Json.bool(m, "toggleSprint", false);
            int written = Json.integer(m, "settingsVersion", 0);
            float boom = (float) Json.num(m, "tpsDistance", TPS_DISTANCE);
            if (written < 8 && Math.abs(boom - 3.4f) < 0.01f) boom = TPS_DISTANCE;      // the old default, never touched: take the new one
            s.tpsDistance = Math.max(TPS_MIN, Math.min(TPS_MAX, boom));
            s.tpsShoulder = Math.max(0f, Math.min(1.1f, (float) Json.num(m, "tpsShoulder", 0.62)));
            s.tpsSmoothing = Math.max(0f, Math.min(1f, (float) Json.num(m, "tpsSmoothing", 0.6)));
            s.showFps = Json.bool(m, "showFps", false);
            s.gfxPreset = Math.max(0, Math.min(3, Json.integer(m, "gfxPreset", 2)));
            s.gtao = Json.bool(m, "gtao", true);
            s.ssr = Json.bool(m, "ssr", true);
            s.volumetrics = Json.bool(m, "volumetrics", true);
            s.taa = Json.bool(m, "taa", true);
            s.bloom = Json.bool(m, "bloom", true);
            s.vsync = Json.bool(m, "vsync", true);
            s.renderScale = Math.max(0.5f, Math.min(1f, (float) Json.num(m, "renderScale", 1)));
            s.particleDetail = Math.max(0.4f, Math.min(1.5f, (float) Json.num(m, "particleDetail", 1)));
            s.goreDetail = Math.max(0, Math.min(2, Json.integer(m, "goreDetail", 1)));
            s.vehicleDebug = Json.bool(m, "vehicleDebug", false);
            s.hudAlways = Json.bool(m, "hudAlways", false);
            s.hudScale = Math.max(0.75f, Math.min(1.5f, (float) Json.num(m, "hudScale", 1)));
            s.hudSafe = Math.max(0f, Math.min(0.1f, (float) Json.num(m, "hudSafe", 0.02)));
            for (Map.Entry<String, Object> e : Json.obj(m, "bindings").entrySet())
                if (in.bindings.containsKey(e.getKey()) && e.getValue() instanceof Number n) in.bindings.put(e.getKey(), n.intValue());
            // 1.1: the stance key moved to Left Ctrl. A file written before that which still has the old default (C) takes the
            // new one; a key the player chose themselves is kept.
            if (written < 11 && in.bindings.get("crouch") == org.lwjgl.glfw.GLFW.GLFW_KEY_C) in.bindings.put("crouch", org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL);
        } catch (Exception e) {
            System.err.println("[Settings] " + e.getMessage());
        }
        return s;
    }

    /** Pushes the audio options to the mixer buses (called whenever a slider moves). */
    public void applyAudio(absentialis.engine.audio.UltraEngine a) {
        if (a == null) return;
        a.master = master;
        a.muted = muteAll;
        a.bus[absentialis.engine.audio.UltraEngine.SFX] = sfx;
        a.bus[absentialis.engine.audio.UltraEngine.MUSIC] = music;
        a.bus[absentialis.engine.audio.UltraEngine.VOICE] = voice;
        a.bus[absentialis.engine.audio.UltraEngine.AMBIENCE] = ambience;
        a.bus[absentialis.engine.audio.UltraEngine.UI] = uiVolume;
        a.busMuted[absentialis.engine.audio.UltraEngine.SFX] = muteSfx;
        a.busMuted[absentialis.engine.audio.UltraEngine.MUSIC] = muteMusic;
        a.busMuted[absentialis.engine.audio.UltraEngine.VOICE] = muteVoice;
    }

    private int appliedPreset = -1;
    private boolean appliedVsync = true;

    /** Pushes the graphics options to the renderer (called whenever one changes; cheap when nothing did). */
    public void applyGraphics(absentialis.engine.render.HybridRenderer hr, absentialis.engine.Window win) {
        if (win != null && vsync != appliedVsync && System.getenv("ABS_NOVSYNC") == null) { org.lwjgl.glfw.GLFW.glfwSwapInterval(vsync ? 1 : 0); appliedVsync = vsync; }
        if (hr == null) return;
        if (appliedPreset != gfxPreset) { hr.preset(gfxPreset); appliedPreset = gfxPreset; }
        hr.q.gtao = gtao; hr.q.ssr = ssr; hr.q.volumetrics = volumetrics; hr.q.taa = taa; hr.q.bloom = bloom;
        hr.q.renderScale = renderScale;
    }

    public void save(Input in) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uiScale", (double) uiScale); m.put("shake", (double) shake); m.put("sensitivity", (double) sensitivity); m.put("fov", (double) fov);
        m.put("master", (double) master); m.put("sfx", (double) sfx); m.put("ambience", (double) ambience);
        m.put("music", (double) music); m.put("voice", (double) voice); m.put("uiVolume", (double) uiVolume);
        m.put("muteAll", muteAll); m.put("muteMusic", muteMusic); m.put("muteSfx", muteSfx); m.put("muteVoice", muteVoice);
        m.put("subtitles", subtitles); m.put("hitDirection", hitDirection); m.put("toggleAim", toggleAim); m.put("toggleCrouch", toggleCrouch);
        m.put("toggleSprint", toggleSprint); m.put("tpsDistance", (double) tpsDistance); m.put("tpsShoulder", (double) tpsShoulder); m.put("tpsSmoothing", (double) tpsSmoothing);
        m.put("showFps", showFps); m.put("settingsVersion", 11);
        m.put("hudAlways", hudAlways); m.put("hudScale", (double) hudScale); m.put("hudSafe", (double) hudSafe);
        m.put("gfxPreset", gfxPreset); m.put("gtao", gtao); m.put("ssr", ssr); m.put("volumetrics", volumetrics); m.put("taa", taa);
        m.put("bloom", bloom); m.put("vsync", vsync); m.put("renderScale", (double) renderScale); m.put("particleDetail", (double) particleDetail);
        m.put("goreDetail", goreDetail); m.put("vehicleDebug", vehicleDebug);
        m.put("bindings", new LinkedHashMap<String, Object>(in.bindings));
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, Json.write(m), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[Settings] save failed: " + e.getMessage());
        }
    }
}
