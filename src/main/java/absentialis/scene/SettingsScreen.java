package absentialis.scene;

import absentialis.engine.Input;
import absentialis.engine.render.HybridRenderer;
import absentialis.persistence.Settings;
import absentialis.ui.Ui;
import java.util.ArrayList;
import java.util.List;

/**
 * Options, in four pages: GAMEPLAY (interface scale, camera, accessibility, difficulty), GRAPHICS (renderer preset,
 * the individual lighting and screen-space effects, render scale, gore and particle detail), AUDIO (master, the
 * five buses, mutes, a test tone and the output diagnostics) and CONTROLS (rebindable keys).
 * Everything applies live; DONE writes the file.
 */
public final class SettingsScreen {
    private final Ctx c;
    private String rebinding;
    private float scroll;
    public boolean close;
    private int page;
    private static final String[] PAGES = {"GAMEPLAY", "GRAPHICS", "AUDIO", "CONTROLS"};
    private static final String[] PAGE_ICON = {"cog", "star", "radio", "arrow"};

    public SettingsScreen(Ctx c) { this.c = c; }

    public void draw(Ui ui) {
        Settings s = c.settings;
        Input in = c.in;
        ui.b.rect(0, 0, ui.w, ui.h, 0xD0060706);
        float w = Math.min(ui.w - ui.s(40), ui.s(1000)), h = Math.min(ui.h - ui.s(60), ui.s(760)), x = (ui.w - w) / 2, y = (ui.h - h) / 2;
        float cy = ui.window("Settings", "cog", x, y, w, h);
        float tw = (w - ui.s(48)) / PAGES.length;
        for (int i = 0; i < PAGES.length; i++) if (ui.tab("spage" + i, x + ui.s(24) + i * tw, cy, tw - ui.s(6), ui.s(34), PAGES[i], PAGE_ICON[i], page == i) && rebinding == null) page = i;
        float top = cy + ui.s(52), lx = x + ui.s(32), cw = w - ui.s(64);
        switch (page) {
            case 0 -> gameplay(ui, s, lx, top, cw);
            case 1 -> graphics(ui, s, lx, top, cw);
            case 2 -> audio(ui, s, lx, top, cw);
            default -> controls(ui, in, lx, top, cw, y + h - ui.s(84) - top);
        }
        if (page == 3 && ui.button("defaults", lx, y + h - ui.s(60), ui.s(180), ui.s(40), "DEFAULT KEYS", null, true)) in.resetBindings();
        ui.text(absentialis.Version.LABEL, lx + (page == 3 ? ui.s(200) : 0), y + h - ui.s(46), 11, Ui.TXT_DIM);
        if (ui.button("sclose", x + w - ui.s(200), y + h - ui.s(60), ui.s(176), ui.s(40), "DONE", "check", true) || (rebinding == null && in.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE))) {
            s.save(in);
            close = true;
        }
    }

    private void gameplay(Ui ui, Settings s, float lx, float ly, float cw) {
        float colW = (cw - ui.s(32)) / 2, rx = lx + colW + ui.s(32), ry = ly;
        ui.text("DISPLAY", lx, ly, 14, Ui.TXT_HI); ly += ui.s(28);
        // the scale applies on mouse release so the layout does not reflow under the cursor mid-drag
        if (draggingScale && !ui.down) { s.uiScale = pendingScale; draggingScale = false; }
        if (!draggingScale) pendingScale = s.uiScale;
        float v = Math.round(ui.slider("uiscale", lx, ly, colW, "UI scale", pendingScale, 0.75f, 1.75f, "%.2fx") * 20) / 20f;
        if (ui.down && v != pendingScale) draggingScale = true;
        pendingScale = v;
        ly += ui.s(52);
        s.shake = ui.slider("shake", lx, ly, colW, "Camera shake", s.shake, 0, 1, "%.2f"); ly += ui.s(52);
        s.fov = ui.slider("fov", lx, ly, colW, "Field of view", s.fov, 60, 100, "%.0f"); ly += ui.s(52);
        s.sensitivity = ui.slider("sens", lx, ly, colW, "Mouse sensitivity", s.sensitivity, 0.03f, 0.4f, "%.2f"); ly += ui.s(60);
        ui.text("DIFFICULTY", lx, ly, 14, Ui.TXT_HI); ly += ui.s(24);
        // 0.8: difficulty belongs to the survivor - chosen when the profile is made, and locked
        ui.text(c.profile == null ? "Chosen when a survivor is created (Easy, Normal or Hard), and locked from then on." : c.profile.name + ": " + c.profile.difficultyName() + "  -  locked when the profile was made.", lx, ly, 12, Ui.TXT);
        ly += ui.s(20);
        if (c.profile != null) ui.text("On death: keep " + c.profile.keepOnDeath() + " item" + (c.profile.keepOnDeath() == 1 ? "" : "s") + " of your choice, lose " + Math.round(c.profile.cashLostOnDeath() * 100) + "% of your cash.", lx, ly, 11, Ui.TXT_DIM);
        ui.text("ACCESSIBILITY & CONTROL", rx, ry, 14, Ui.TXT_HI); ry += ui.s(28);
        s.subtitles = ui.toggle("subs", rx, ry, colW, "Subtitles with directional sound tags", s.subtitles); ry += ui.s(32);
        s.hitDirection = ui.toggle("hitdir", rx, ry, colW, "Directional hit indicators", s.hitDirection); ry += ui.s(32);
        s.toggleAim = ui.toggle("tAim", rx, ry, colW, "Aim: toggle instead of hold", s.toggleAim); ry += ui.s(32);
        s.toggleCrouch = ui.toggle("tCrouch", rx, ry, colW, "Crouch: toggle instead of hold", s.toggleCrouch); ry += ui.s(32);
        s.toggleSprint = ui.toggle("tSprint", rx, ry, colW, "Sprint: toggle instead of hold", s.toggleSprint); ry += ui.s(32);
        s.tpsDistance = ui.slider("tpsDist", rx, ry, colW, "Third person: camera distance", s.tpsDistance, absentialis.persistence.Settings.TPS_MIN, absentialis.persistence.Settings.TPS_MAX, "%.1f m"); ry += ui.s(40);
        s.tpsShoulder = ui.slider("tpsSh", rx, ry, colW, "Third person: shoulder offset", s.tpsShoulder, 0f, 1.1f, "%.2f m"); ry += ui.s(40);
        s.tpsSmoothing = ui.slider("tpsSm", rx, ry, colW, "Third person: camera smoothing", s.tpsSmoothing, 0f, 1f, "%.2f"); ry += ui.s(40);
        s.showFps = ui.toggle("fps", rx, ry, colW, "Show FPS and renderer statistics", s.showFps); ry += ui.s(32);
        // 1.1, the HUD: keep every indicator up, its size, and the margin kept clear at the edges of the screen
        s.hudAlways = ui.toggle("hudAlways", rx, ry, colW, "Always show HUD (no fading of idle indicators)", s.hudAlways); ry += ui.s(32);
        s.hudScale = Math.round(ui.slider("hudScale", rx, ry, colW, "HUD scale", s.hudScale, 0.75f, 1.5f, "%.2fx") * 20) / 20f; ry += ui.s(40);
        s.hudSafe = Math.round(ui.slider("hudSafe", rx, ry, colW, "HUD safe area (margin, share of screen height)", s.hudSafe, 0f, 0.1f, "%.3f") * 200) / 200f; ry += ui.s(40);
        s.vehicleDebug = ui.toggle("vdbg", rx, ry, colW, "Vehicle physics overlay (development)", s.vehicleDebug); ry += ui.s(40);
        ui.wrap("Ironman is chosen per survivor when the profile is created: one life, and death deletes the profile.", rx, ry, colW, 11, Ui.TXT_DIM);
    }

    private void graphics(Ui ui, Settings s, float lx, float ly, float cw) {
        float colW = (cw - ui.s(32)) / 2, rx = lx + colW + ui.s(32), ry = ly;
        boolean hybrid = c.hr != null;
        ui.text("RENDERER", lx, ly, 14, Ui.TXT_HI); ly += ui.s(22);
        ui.wrap(hybrid ? "Hybrid GI (deferred PBR, probe global illumination)  -  " + c.win.glInfo : "Legacy forward renderer: this graphics driver did not provide OpenGL 4.5. The options below need the hybrid renderer.", lx, ly, colW, 11, hybrid ? Ui.TXT_DIM : Ui.AMBER);
        ly += ui.s(40);
        ui.text("QUALITY PRESET", lx, ly, 13, Ui.TXT_HI); ly += ui.s(22);
        for (int i = 0; i < 4; i++) if (ui.tab("gq" + i, lx + i * (colW / 4), ly, colW / 4 - ui.s(4), ui.s(30), HybridRenderer.PRESETS[i], null, s.gfxPreset == i)) s.gfxPreset = i;
        ly += ui.s(36);
        ui.wrap(new String[]{
                "1024 shadows, 3 cascades, 8 probe regions a frame. For integrated or older graphics.",
                "2048 shadows, 3 cascades, 12 probe regions a frame.",
                "2048 shadows, 4 cascades, 16 probe regions a frame, four shadowed local lights. The 1080p / 60 target on a mid-range card.",
                "4096 shadows, 28 probe regions a frame at 64 rays, eight shadowed local lights."}[s.gfxPreset], lx, ly, colW, 11, Ui.TXT_DIM);
        ly += ui.s(48);
        float rs = Math.round(ui.slider("rscale", lx, ly, colW, "Render scale", s.renderScale, 0.5f, 1f, "%.2fx") * 20) / 20f;
        if (!ui.down) s.renderScale = rs; else pendingRender = rs;
        ly += ui.s(52);
        s.particleDetail = ui.slider("pdet", lx, ly, colW, "Fire and particle density", s.particleDetail, 0.4f, 1.5f, "%.1fx"); ly += ui.s(52);
        ui.text("GORE DETAIL", lx, ly, 13, Ui.TXT_HI); ly += ui.s(22);
        for (int i = 0; i < 3; i++) if (ui.tab("gore" + i, lx + i * (colW / 3), ly, colW / 3 - ui.s(4), ui.s(30), Settings.GORE[i], null, s.goreDetail == i)) s.goreDetail = i;
        ly += ui.s(36);
        ui.wrap("Blood particles, stains, pools and the number of detached pieces kept in the world. Wounds and dismemberment themselves are part of the simulation and are not affected.", lx, ly, colW, 11, Ui.TXT_DIM);

        ui.text("LIGHTING & SCREEN-SPACE EFFECTS", rx, ry, 14, Ui.TXT_HI); ry += ui.s(28);
        s.gtao = ui.toggle("gtao", rx, ry, colW, "Ambient occlusion (GTAO)", s.gtao); ry += ui.s(32);
        s.ssr = ui.toggle("ssr", rx, ry, colW, "Screen-space reflections", s.ssr); ry += ui.s(32);
        s.volumetrics = ui.toggle("volf", rx, ry, colW, "Volumetric fog and light shafts", s.volumetrics); ry += ui.s(32);
        s.taa = ui.toggle("taa", rx, ry, colW, "Temporal anti-aliasing", s.taa); ry += ui.s(32);
        s.bloom = ui.toggle("bloom", rx, ry, colW, "Bloom", s.bloom); ry += ui.s(32);
        s.vsync = ui.toggle("vsync", rx, ry, colW, "Vertical sync", s.vsync); ry += ui.s(44);
        ui.wrap("Sunlight, local lights and the irradiance probes are always live: the probes relight as the sun moves, as power changes and as the world is damaged, a few regions each frame, so indirect light settles over a moment rather than instantly.", rx, ry, colW, 11, Ui.TXT_DIM);
        ry += ui.s(74);
        if (hybrid) ui.wrap(c.hr.stats, rx, ry, colW, 11, Ui.TXT_DIM);
        s.applyGraphics(c.hr, c.win);
    }

    private float pendingRender = 1, testCooldown;

    private void audio(Ui ui, Settings s, float lx, float ly, float cw) {
        float colW = (cw - ui.s(32)) / 2, rx = lx + colW + ui.s(32), ry = ly, sw = colW - ui.s(96);
        ui.text("LEVELS", lx, ly, 14, Ui.TXT_HI); ly += ui.s(26);
        s.master = ui.slider("vmaster", lx, ly, sw, "Master", s.master, 0, 1, "%.2f"); s.muteAll = ui.toggle("mAll", lx + sw + ui.s(10), ly + ui.s(14), ui.s(86), "Mute", s.muteAll); ly += ui.s(46);
        s.music = ui.slider("vmusic", lx, ly, sw, "Music", s.music, 0, 1, "%.2f"); s.muteMusic = ui.toggle("mMus", lx + sw + ui.s(10), ly + ui.s(14), ui.s(86), "Mute", s.muteMusic); ly += ui.s(46);
        s.sfx = ui.slider("vsfx", lx, ly, sw, "Effects", s.sfx, 0, 1, "%.2f"); s.muteSfx = ui.toggle("mSfx", lx + sw + ui.s(10), ly + ui.s(14), ui.s(86), "Mute", s.muteSfx); ly += ui.s(46);
        s.voice = ui.slider("vvoice", lx, ly, sw, "Voice", s.voice, 0, 1, "%.2f"); s.muteVoice = ui.toggle("mVoi", lx + sw + ui.s(10), ly + ui.s(14), ui.s(86), "Mute", s.muteVoice); ly += ui.s(46);
        s.ambience = ui.slider("vamb", lx, ly, sw, "Ambience", s.ambience, 0, 1, "%.2f"); ly += ui.s(46);
        s.uiVolume = ui.slider("vui", lx, ly, sw, "Interface", s.uiVolume, 0, 1, "%.2f"); ly += ui.s(52);
        s.applyAudio(c.audio);
        ui.text("OUTPUT", rx, ry, 14, Ui.TXT_HI); ry += ui.s(26);
        boolean bad = c.audio == null || !c.audio.available || c.audio.session.silenced();
        ry += ui.wrap(c.audio == null ? "No audio engine." : c.audio.status, rx, ry, colW, 12, bad ? Ui.RED : c.audio.repaired ? Ui.AMBER : Ui.TXT) + ui.s(10);
        testCooldown -= ui.dt;
        if (ui.button("atest", rx, ry, ui.s(200), ui.s(38), "PLAY TEST SOUND", "radio", c.audio != null && c.audio.available && testCooldown <= 0)) {
            testCooldown = 1.2f;
            c.audio.play("extract_done", 0.8f, 0, 1, absentialis.engine.audio.UltraEngine.UI);
            c.audio.play("shot_rifle", 0.7f, 0, 1, absentialis.engine.audio.UltraEngine.SFX);
        }
        ry += ui.s(50);
        // live meters: if these move and you hear nothing, the sound is being lost outside the game
        if (c.audio != null) {
            for (int b = 0; b < absentialis.engine.audio.UltraEngine.BUSES; b++) {
                ui.text(absentialis.engine.audio.UltraEngine.BUS_NAMES[b], rx, ry, 11, Ui.TXT_DIM);
                ui.bar(rx + ui.s(90), ry + ui.s(3), colW - ui.s(90), ui.s(8), Math.min(1, c.audio.peak[b] * 2.5f), 0xFF7DBA5C);
                ry += ui.s(18);
            }
            ry += ui.s(10);
            ry += ui.wrap("If the meters move but nothing is heard, open the Windows Volume Mixer and check the entry for this game (it appears as \"OpenJDK Platform binary\"). The game restores that entry itself when it finds it silenced at start-up.", rx, ry, colW, 11, Ui.TXT_DIM) + ui.s(10);
            ui.wrap("Recorded sounds: any .ogg or .wav placed in assets/audio replaces the built-in sound of the same name (see assets/audio/README.txt).", rx, ry, colW, 11, Ui.TXT_DIM);
        }
    }

    private void controls(Ui ui, Input in, float lx, float ly, float cw, float avail) {
        ui.text("CONTROLS  (click to rebind, Esc cancels)", lx, ly, 14, Ui.TXT_HI);
        float top = ly + ui.s(28);
        List<String> acts = new ArrayList<>(in.bindings.keySet());
        float rowH = ui.s(27), colW = (cw - ui.s(32)) / 2;
        int rows = Math.max(1, (int) ((avail - ui.s(28)) / rowH)), vis = rows * 2;
        scroll = Math.max(0, Math.min(Math.max(0, (acts.size() - vis + 1) / 2), scroll - (float) in.scroll));
        int first = (int) scroll * 2;
        for (int i = first; i < Math.min(acts.size(), first + vis); i++) {
            int k = i - first;
            float bx = lx + (k / rows) * (colW + ui.s(32)), by = top + (k % rows) * rowH;
            String a = acts.get(i);
            ui.text(a.replace('_', ' '), bx, by + ui.s(4), 13, Ui.TXT);
            boolean wait = a.equals(rebinding);
            String key = wait ? "press a key..." : Input.keyName(in.bindings.get(a));
            if (ui.button("bind" + a, bx + colW - ui.s(150), by, ui.s(150), ui.s(24), key, null, true)) rebinding = a;
        }
        if (rebinding != null) {
            if (in.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) rebinding = null;
            else if (in.lastKeyPressed >= 0 && !(in.lastKeyPressed == Input.MOUSE_OFFSET && ui.consumed)) {
                int k = in.lastKeyPressed;
                if (k != Input.MOUSE_OFFSET || !ui.hover(lx, top, cw, avail)) { in.bindings.put(rebinding, k); rebinding = null; }
            }
        }
    }

    private float pendingScale = 1;
    private boolean draggingScale;
}
