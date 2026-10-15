package absentialis.scene;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import absentialis.engine.Window;
import absentialis.engine.audio.UltraEngine;
import absentialis.engine.render.Atlas;
import absentialis.engine.render.Batch2D;
import absentialis.engine.render.Font;
import absentialis.engine.render.ModelRenderer;
import absentialis.engine.render.Screenshot;
import absentialis.engine.render.WorldRenderer;
import absentialis.entity.Actor;
import absentialis.gameplay.economy.Economy;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemInstance;
import absentialis.persistence.Profile;
import absentialis.persistence.SaveSystem;
import absentialis.persistence.Settings;
import absentialis.sim.ZoneState;
import absentialis.ui.InventoryUi;
import absentialis.ui.ItemIcons;
import absentialis.ui.MapView;
import absentialis.ui.Ui;
import absentialis.world.Block;
import absentialis.world.District;
import absentialis.world.MapData;
import absentialis.world.World;
import absentialis.world.gen.CityGenerator;
import java.util.ArrayList;
import java.util.List;

/**
 * Application shell: boots every service, generates the Zone in the background while the menus
 * run, and drives the TITLE -> PROFILES -> HIDEOUT <-> RAID -> DEBRIEF state machine with 120 ms
 * screen transitions. Also hosts the developer console and the ABS_AUTOSHOT capture script.
 */
public final class GameApp {
    private enum State { TITLE, PROFILES, CREATE, HIDEOUT, LOADING, RAID, DEBRIEF }

    private final Window win;
    private final Ctx c = new Ctx();
    private State state = State.TITLE;
    private HideoutScreen hideout;
    private RaidScreen raidScreen;
    private SettingsScreen settings;
    private boolean settingsOpen;
    private volatile String genStatus = "Waiting";
    private volatile boolean worldGenerated, worldDirty;
    private volatile Thread prepThread;
    private boolean renderReady;
    private float fade = 1;
    private List<String> debriefLines = new ArrayList<>();
    private Raid lastRaid;
    private final StringBuilder nameField = new StringBuilder();
    private int createSlot, createArm = 4, createSkin = 1, createOutfit;
    private final String autoshot = System.getenv("ABS_AUTOSHOT");
    private int shotFrame, perfFrames;
    private float perfTime, perfWorst;

    public GameApp(Window w) { this.win = w; }

    /** What a scripted scenario may do to the running game (scene.Scenarios). */
    private final Scenarios.Host host = new Scenarios.Host() {
        public Ctx ctx() { return c; }
        public HideoutScreen hideout() { return hideout; }
        public RaidScreen raid() { return raidScreen; }
        public boolean inHideout() { return state == State.HIDEOUT; }
        public boolean inRaid() { return state == State.RAID; }
        public boolean inTitle() { return state == State.TITLE; }
        public boolean inDebrief() { return state == State.DEBRIEF; }
        public boolean worldReady() { return worldGenerated && renderReady; }
        public void loadProfile(int slot) { GameApp.this.loadProfile(slot); }
        public void deploy(int insertion, double hour) { GameApp.this.deploy(insertion, hour); }
        public void quit() { win.requestClose(); }
        public void console(String line) { c.console.exec(line); System.out.println("[Console] " + line + " -> " + c.console.last()); }
    };

    public void run() {
        init();
        long last = System.nanoTime();
        while (!win.shouldClose()) {
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - last) / 1e9f);
            if (Scenarios.active() && System.getenv("ABS_FIXED_DT") != null) dt = 1 / 60f;        // frame studies: every picture is one sixtieth of a second after the last
            last = now;
            c.time += dt;
            win.poll();
            frame(dt);
            if (autoshot != null) { autoshotStep(); if (state == State.RAID) { perfFrames++; perfTime += dt; perfWorst = Math.max(perfWorst, dt); } }
            else if (Scenarios.active()) { Scenarios.step(host); if (state == State.RAID) { perfFrames++; perfTime += dt; perfWorst = Math.max(perfWorst, dt); } }
            win.swap();
            c.in.endFrame();
        }
        if (c.hr != null && !c.hr.profile().isEmpty()) System.out.println(c.hr.profile());
        if (perfFrames > 0) System.out.printf("[Perf] raid frames %d, mean %.2f ms (%.0f fps), worst %.1f ms%n", perfFrames, perfTime / perfFrames * 1000, perfFrames / perfTime, perfWorst * 1000);
        if (c.profile != null && state != State.RAID) SaveSystem.save(c.profile);
        if (c.wr != null) c.wr.shutdown();
        if (c.hr != null) c.hr.dispose();
        if (c.audio != null) c.audio.stop();
    }

    // ------------------------------------------------------------------ boot
    private void init() {
        c.win = win;
        c.in = win.input;
        Content.load();
        c.settings = Settings.load(c.in);
        Batch2D b = new Batch2D();
        b.init();
        Font font = new Font(48);
        font.init();
        c.ui = new Ui(b, font, c.in);
        c.ui.init();
        c.icons = new ItemIcons();
        c.icons.init();
        c.inv = new InventoryUi(c.ui, c.icons);
        c.map = new MapView();
        c.console = new Console();
        c.mr = new ModelRenderer();
        c.mr.init();
        c.ar = new ActorRenderer(c.mr);
        c.audio = new UltraEngine();
        c.audio.buildBank();
        c.audio.start();
        c.settings.applyAudio(c.audio);
        c.ui.sound = c::ui;
        menuMusic(true);
        c.atlas = new Atlas();
        c.atlas.paintAll();
        c.atlas.upload();
        settings = new SettingsScreen(c);
        registerCommands();
        win.setCursorLocked(false);
        glfwSetInputMode(win.handle, GLFW_CURSOR, GLFW_CURSOR_HIDDEN);
        startWorldGeneration();
        if (autoshot != null) autoProfile();
    }

    private void startWorldGeneration() {
        Thread t = new Thread(() -> {
            try {
                World w = new World();
                new CityGenerator(w, 1337L, s -> genStatus = s).generate();
                genStatus = "Lighting the Zone";
                w.lighting.computeAll();
                w.snapshotPristine();
                c.world = w;
                worldGenerated = true;
                genStatus = "Ready";
            } catch (Throwable e) {
                e.printStackTrace();
                genStatus = "World generation failed: " + e;
            }
        }, "worldgen");
        t.setDaemon(true);
        t.start();
    }

    /** GL-side setup once the voxel data exists (main thread). */
    private void ensureRenderer() {
        if (renderReady || !worldGenerated) return;
        c.wr = new WorldRenderer(c.world, c.atlas);
        c.wr.init();
        java.util.Arrays.fill(c.world.dirtyColumns, true);
        c.world.anyDirty = true;
        c.wr.scheduleDirty();
        c.map.build(c.world);
        if (c.profile != null) c.map.rebuildHatch(c.profile.zone);
        if (win.gl45) {
            try {
                c.hr = new absentialis.engine.render.HybridRenderer();
                c.hr.init(c.world, c.atlas, win.width, win.height);
                // 1.0: the Astratto foliage system and decal engine, told about this world
                WorldFx wfx = new WorldFx(c.world);
                c.hr.foliage = new absentialis.engine.foliage.Foliage();
                c.hr.foliage.attach(wfx);
                c.hr.foliage.init();
                c.hr.decals = new absentialis.engine.decal.Decals();
                c.hr.decals.attach(wfx);
                c.hr.decals.atlasTex = c.atlas.texId;
                c.hr.decals.init();
                absentialis.engine.render.ChunkMesher.foliageBlocks = false;
                c.hr.props = new absentialis.engine.props.Props();
                c.hr.props.light = (lx, ly, lz) -> (c.world.sky(lx, ly, lz) << 4) | c.world.blockLight(lx, ly, lz);
                for (absentialis.world.gen.PropLib.Def pd : absentialis.world.gen.PropLib.all()) c.hr.props.model(pd.name, pd.model, 1 / 16f);
                c.hr.props.init();
                loadProps();
                c.settings.applyGraphics(c.hr, win);
                System.out.println("[Render] Hybrid GI renderer ready (deferred PBR, irradiance probes, compute culling)");
            } catch (Throwable t) {
                // a driver that advertises 4.5 but rejects a shader must not cost the player the game
                System.err.println("[Render] Hybrid renderer unavailable, using the legacy forward path: " + t);
                c.hr = null;
            }
        }
        renderReady = true;
    }

    /**
     * 0.7: swaps what the world object holds - the devmap, or the Zone again - in place. The same World, renderer and
     * probes are kept (they are bound to the object, not to what is in it); the voxels, the map data, the lighting, the
     * pristine copy and the map screen are all rebuilt. Returns null when done, or why it could not be.
     */
    private String loadMap(boolean dev) {
        if (prepThread != null && prepThread.isAlive()) return "The world is busy - try again in a moment";
        if (state == State.RAID && raidScreen != null) { raidScreen = null; state = State.HIDEOUT; }
        try {
            long t0 = System.currentTimeMillis();
            World w = c.world;
            if (dev) absentialis.world.gen.DevMap.generate(w);
            else new CityGenerator(w, 1337L, s -> genStatus = s).generate();
            w.blockDamage.clear();
            w.cover.clear();
            w.doors = null;
            w.navVersion += 100;
            w.map.fires = null;
            w.lighting.computeAll();
            w.snapshotPristine();
            worldDirty = false;
            java.util.Arrays.fill(w.dirtyColumns, true);
            w.anyDirty = true;
            c.map.build(w);
            loadProps();
            if (c.profile != null) c.map.rebuildHatch(c.profile.zone);
            System.out.println("[Dev] " + (dev ? "devmap" : "Zone") + " loaded in " + (System.currentTimeMillis() - t0) + " ms");
            return null;
        } catch (Throwable e) {
            e.printStackTrace();
            return "Could not load the map: " + e;
        }
    }

    /**
     * 1.0: stands the world's props (world.map.props, placed by generation) in the engine's prop system. Called when
     * the renderer is made and whenever the world is generated again (the devmap, the Zone).
     */
    private void loadProps() {
        if (c.hr == null || c.hr.props == null || c.world == null) return;
        var ps = c.hr.props;
        ps.clear();
        for (MapData.Prop p : c.world.map.props) {
            int model = ps.id(p.model);
            if (model < 0) continue;
            // the model's middle is the middle of the box of cells it covers
            float ox = (p.x1 - p.x0) * 0.5f, oz = (p.z1 - p.z0) * 0.5f;
            var in = ps.add(model, p.x0, p.y, p.z0, p.rot, ox, p.lift, oz);
            var d = absentialis.world.gen.PropLib.get(p.model);
            for (int x = p.x0; x <= p.x1; x++) for (int z = p.z0; z <= p.z1; z++) for (int h = 0; h < d.h; h++) ps.claim(in, x, p.y + h, z);
        }
        System.out.println("[Props] " + ps.count() + " props of " + ps.modelCount() + " models");
    }

    /** Restores the pristine city and re-applies persistent world state before a deployment. */
    private void prepareWorldForRaid() {
        Profile p = c.profile;
        prepThread = new Thread(() -> {
            if (worldDirty) {
                genStatus = "Resetting the operational slice";
                c.world.restorePristine();
                if (c.hr != null && c.hr.props != null) c.hr.props.restore();
            }
            for (MapData.Point lp : c.world.map.lampsOnPower) {
                District d = c.world.district(lp.x, lp.z);
                int want = p.zone.power[d.ordinal()] || d == District.CIVIC ? Block.LAMP : Block.LAMP_OFF;
                int cur = c.world.get(lp.x, lp.y, lp.z);
                if (cur == Block.LAMP || cur == Block.LAMP_OFF) c.world.setRaw(lp.x, lp.y, lp.z, want);
            }
            genStatus = "Relighting";
            c.world.lighting.computeAll();
            c.world.scanFires();
            java.util.Arrays.fill(c.world.dirtyColumns, true);
            c.world.anyDirty = true;
            worldDirty = true;
            genStatus = "Meshing";
        }, "raid-prep");
        prepThread.setDaemon(true);
        prepThread.start();
    }

    // ------------------------------------------------------------------ frame
    private void frame(float dt) {
        ensureRenderer();
        if (c.wr != null && state != State.RAID) { c.wr.scheduleDirty(); c.wr.uploadResults(48); }
        c.ui.scale = c.settings.uiScale;
        if (c.in.keyPressed(GLFW_KEY_F12)) Screenshot.save(win.width, win.height, "screenshots/shot_" + System.currentTimeMillis() + ".png");
        if (state != State.RAID && c.in.keyPressed(GLFW_KEY_GRAVE_ACCENT)) c.console.toggle();
        if (state != State.RAID && c.console.open) c.console.input(c.in);
        switch (state) {
            case TITLE -> menuFrame(dt, this::title);
            case PROFILES -> menuFrame(dt, this::profiles);
            case CREATE -> menuFrame(dt, this::create);
            case HIDEOUT -> hideoutFrame(dt);
            case LOADING -> loadingFrame(dt);
            case RAID -> raidFrame(dt);
            case DEBRIEF -> menuFrame(dt, this::debrief);
        }
        fade = Math.max(0, fade - dt / Ui.EASE);
        if (fade > 0) {
            c.ui.b.begin(win.width, win.height);
            c.ui.b.rect(0, 0, win.width, win.height, Ui.alpha(0xFF000000, fade));
            c.ui.b.end();
        }
    }

    private void go(State s) { state = s; fade = 1; c.inv.drag = null; c.inv.menu.clear(); titleScanAge = 99; }

    private void menuFrame(float dt, Runnable body) {
        glViewport(0, 0, win.width, win.height);
        glClearColor(0.04f, 0.045f, 0.04f, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        Ui ui = c.ui;
        ui.b.begin(win.width, win.height);
        ui.begin(dt, win.width, win.height);
        ui.showCursor = true;
        if (menuTrack != null) menuMusic(true);
        menuBackdrop(ui);
        if (settingsOpen) ui.block = true;
        body.run();
        ui.block = false;
        if (settingsOpen) { settings.draw(ui); if (settings.close) { settings.close = false; settingsOpen = false; } }
        c.console.draw(ui, dt);
        ui.end();
        ui.b.end();
    }

    private void menuBackdrop(Ui ui) {
        // slow drifting voxel skyline silhouette in pixel blocks
        int px = Math.max(4, ui.s(6));
        for (int x = 0; x < ui.w; x += px) {
            int hgt = (int) (ui.h * 0.18f + Math.abs(Math.sin(x * 0.013 + 1) * 90 + Math.sin(x * 0.051) * 40) * ui.scale);
            hgt = hgt / px * px;
            ui.b.rect(x, ui.h - hgt, px, hgt, 0xFF0C0E0C);
            if (((x / px) * 7 + (int) (c.time * 0.2)) % 23 == 0) ui.b.rect(x, ui.h - hgt + px * 3, px, px, 0xFF6A5420);
        }
        ui.b.rect(0, ui.h - ui.s(3), ui.w, ui.s(3), 0xFF8A6420);
    }

    // ------------------------------------------------------------------ title & profiles
    private void title() {
        Ui ui = c.ui;
        float cx = ui.w / 2f;
        ui.textC("ABSENTIALIS", cx, ui.h * 0.16f, 64, Ui.TXT_HI);
        ui.textC("A PERSISTENT VOXEL EXTRACTION SURVIVAL", cx, ui.h * 0.16f + ui.s(76), 14, Ui.AMBER);
        ui.textC(absentialis.Version.LABEL.toUpperCase(), cx, ui.h * 0.16f + ui.s(100), 13, Ui.TXT_DIM);
        if (ironLost != null) {
            ui.textC("IRONMAN  -  " + ironLost + " died in the Zone. The profile has been deleted.", cx, ui.h * 0.16f + ui.s(132), 14, Ui.RED);
        }
        ui.textR(absentialis.Version.LABEL, ui.w - ui.s(12), ui.h - ui.s(22), 12, Ui.TXT_DIM);
        float bw = ui.s(320), bh = ui.s(48), y = ui.h * 0.42f, step = bh + ui.s(10);
        // 0.8: the menu offers what there is. With no profile on disk it is New Game, Settings and Quit and nothing else;
        // Continue appears only when there is a profile that can actually be read, and the profile list only when there is one to list.
        scanProfiles();
        if (titleContinue >= 0) { if (ui.button("t_continue", cx - bw / 2, y, bw, bh, "CONTINUE  (" + titleContinueName + ")", "arrow", true)) loadProfile(titleContinue); y += step; }
        if (ui.button("t_new", cx - bw / 2, y, bw, bh, "NEW GAME", "star", true)) {
            ironLost = null;
            c.sfx("new_game", 0.6f);
            if (titleEmpty >= 0) beginCreate(titleEmpty);
            else { go(State.PROFILES); c.inv.say("Every slot is in use: delete a profile to start a new game", Ui.AMBER); }
        }
        y += step;
        if (titleAny) { if (ui.button("t_profiles", cx - bw / 2, y, bw, bh, "SURVIVOR PROFILES", "inv", true)) { ironLost = null; go(State.PROFILES); } y += step; }
        if (ui.button("t_settings", cx - bw / 2, y, bw, bh, "SETTINGS", "cog", true)) settingsOpen = true;
        y += step;
        if (ui.button("t_quit", cx - bw / 2, y, bw, bh, "QUIT", "cross", true)) win.requestClose();
        // the engine's mark and credit
        float cell = Math.max(1, ui.s(2)), lw = absentialis.engine.Astratto.logoWidth() * cell, lh = absentialis.engine.Astratto.logoHeight() * cell;
        absentialis.engine.Astratto.drawLogo(ui.b, ui.w - ui.s(18) - lw, ui.h - ui.s(74) - lh, cell, 0xFFB8B2A4);
        ui.textR(absentialis.engine.Astratto.NAME.toUpperCase(), ui.w - ui.s(12), ui.h - ui.s(68), 12, Ui.TXT);
        ui.textR(absentialis.engine.Astratto.NOTICE, ui.w - ui.s(12), ui.h - ui.s(50), 10, Ui.TXT_DIM);
        // audio health, in plain sight: which device, and whether Windows had the game turned down to nothing
        if (c.audio != null) ui.text(c.audio.status, ui.s(12), ui.h - ui.s(22), 11, c.audio.repaired ? Ui.AMBER : !c.audio.available || c.audio.session.silenced() ? Ui.RED : Ui.TXT_DIM);
        ui.textC(worldGenerated ? (renderReady && c.wr.busy() ? "Meshing the city... " + c.wr.pending.get() : "The Zone is ready.") : "Generating the Zone: " + genStatus + "...", cx, ui.h - ui.s(48), 12, Ui.TXT_DIM);
    }

    private int lastProfile() { for (int i = 0; i < SaveSystem.SLOTS; i++) if (SaveSystem.exists(i)) return i; return -1; }

    /** What the title screen can offer: the first profile that reads back whole (for Continue), whether any file is there at all, and the first empty slot. */
    private int titleContinue = -1, titleEmpty = -1;
    private boolean titleAny;
    private String titleContinueName = "";
    private float titleScanAge = 99;

    private void scanProfiles() {
        titleScanAge += c.ui.dt;
        if (titleScanAge < 3) return;
        titleScanAge = 0;
        titleContinue = -1; titleEmpty = -1; titleAny = false;
        for (int i = 0; i < SaveSystem.SLOTS; i++) {
            if (!SaveSystem.exists(i)) { if (titleEmpty < 0) titleEmpty = i; continue; }
            titleAny = true;
            if (titleContinue >= 0) continue;
            // a file being there is not enough: it has to load
            Profile got = null;
            try { got = SaveSystem.load(i); } catch (RuntimeException unreadable) { got = null; }
            if (got != null) { titleContinue = i; titleContinueName = got.name + ", " + got.prog.title(); }
        }
    }

    public boolean titleOffersContinue() { titleScanAge = 99; scanProfiles(); return titleContinue >= 0; }
    public boolean titleOffersProfiles() { return titleAny; }

    private void beginCreate(int slot) { createSlot = slot; nameField.setLength(0); createIronman = false; createDifficulty = -1; go(State.CREATE); }

    private final String[][] summaries = new String[SaveSystem.SLOTS][];
    private float summaryAge = 99;

    private void profiles() {
        Ui ui = c.ui;
        summaryAge += ui.dt;
        if (summaryAge > 2) { for (int i = 0; i < SaveSystem.SLOTS; i++) summaries[i] = SaveSystem.summary(i); summaryAge = 0; }
        float w = ui.s(860), h = ui.s(520), x = (ui.w - w) / 2, y = (ui.h - h) / 2;
        float cy = ui.window("Survivor Profiles  (4 isolated slots)", "inv", x, y, w, h);
        for (int i = 0; i < SaveSystem.SLOTS; i++) {
            float ry = cy + i * ui.s(96);
            ui.nine("slot", x + ui.s(20), ry, w - ui.s(40), ui.s(86), 0xFFFFFFFF);
            String[] s = summaries[i];
            if (s == null) {
                ui.text("SLOT " + (i + 1) + "  -  empty", x + ui.s(40), ry + ui.s(30), 16, Ui.TXT_DIM);
                if (ui.button("p_new" + i, x + w - ui.s(220), ry + ui.s(22), ui.s(180), ui.s(42), "NEW SURVIVOR", "star", true)) beginCreate(i);
            } else {
                ui.text(s[0], x + ui.s(40), ry + ui.s(14), 20, Ui.TXT_HI);
                ui.text(s[1] + "   -   " + s[2] + "   -   " + s[3], x + ui.s(40), ry + ui.s(46), 13, Ui.TXT);
                if (ui.button("p_load" + i, x + w - ui.s(330), ry + ui.s(22), ui.s(150), ui.s(42), "PLAY", "arrow", true)) loadProfile(i);
                if (ui.button("p_del" + i, x + w - ui.s(170), ry + ui.s(22), ui.s(130), ui.s(42), ui.shift ? "CONFIRM" : "DELETE", "cross", true)) {
                    if (ui.shift) { SaveSystem.delete(i); summaryAge = 99; } else c.inv.say("Hold Shift and click DELETE to erase this profile", Ui.AMBER);
                }
            }
        }
        if (ui.button("p_back", x + ui.s(20), y + h - ui.s(60), ui.s(160), ui.s(42), "BACK", "arrow", true) || c.in.keyPressed(GLFW_KEY_ESCAPE)) go(State.TITLE);
        c.inv.flashTime -= ui.dt;
        if (c.inv.flashTime > 0) ui.textC(c.inv.flash, ui.w / 2f, y + h + ui.s(12), 14, c.inv.flashColor);
    }

    private CharacterCreator creator;
    private boolean createIronman, createPopOpen;
    /** The difficulty picked on the creation screen (-1 = not picked yet: the profile cannot be made until it is). */
    private int createDifficulty = -1;
    private final java.util.Map<String, Float> createPop = new java.util.LinkedHashMap<>();
    private absentialis.character.Appearance createApp = absentialis.character.Appearance.legacy(1, 0);
    private UltraEngine.Voice menuMusic;

    private String menuTrack;

    /**
     * 1.0: the music of the menus is the supplied recording ("mainmenu"), and the deployment screen has its own
     * ("deploymentscreenbackgroundmusic"): one fades into the other as the briefing is opened and closed.
     */
    private void menuMusic(boolean on) {
        if (c.audio == null) return;
        String want = !on ? null : state == State.HIDEOUT && hideout != null && hideout.deployOpen() ? "music_deploy" : "music_menu";
        if (java.util.Objects.equals(want, menuTrack)) return;
        if (menuMusic != null) { menuMusic.fadeTo(0, want == null ? 2.5f : 1.4f); menuMusic = null; }
        menuTrack = want;
        // 1.1: the track is asked for at the level it is to be heard at. It was asked for at a gain of zero and then "faded
        // in" - but a fade scales the gain, and nought times anything is silence: neither the menu's music nor the
        // briefing's was ever heard.
        if (want != null) { menuMusic = c.audio.loop(want, MENU_MUSIC_GAIN, UltraEngine.MUSIC); menuMusic.fadeTo(1f, 1.4f); }
    }

    /** How loud the menu and deployment music is played (before the Music slider). */
    public static final float MENU_MUSIC_GAIN = 1.25f;
    /** The music the menus are playing now ("music_menu", "music_deploy" or null), and the level its voice was started at (gates). */
    public String menuTrackNow() { return menuTrack; }
    public float menuMusicGain() { return menuMusic == null ? 0 : menuMusic.gain(); }

    private void create() {
        Ui ui = c.ui;
        while (!c.in.typed.isEmpty()) { char ch = c.in.typed.poll(); if (nameField.length() < 18 && ch >= 32 && ch < 127) nameField.append(ch); }
        if (c.in.keyPressed(GLFW_KEY_BACKSPACE) && nameField.length() > 0) nameField.setLength(nameField.length() - 1);
        float w = Math.min(ui.w - ui.s(40), ui.s(1100)), h = Math.min(ui.h - ui.s(40), ui.s(760)), x = (ui.w - w) / 2, y = (ui.h - h) / 2;
        float cy = ui.window("New Survivor  -  Slot " + (createSlot + 1), "star", x, y, w, h);
        ui.text("CALLSIGN", x + ui.s(24), cy, 13, Ui.TXT_HI);
        ui.nine("slot", x + ui.s(24), cy + ui.s(20), ui.s(360), ui.s(40), 0xFFFFFFFF);
        ui.text(nameField + ((System.currentTimeMillis() / 450) % 2 == 0 ? "_" : ""), x + ui.s(36), cy + ui.s(30), 18, Ui.AMBER);
        // 0.8: the difficulty is chosen here, once, and is locked from then on. Ironman goes with any of the three.
        float dx = x + ui.s(420), dw = Math.min(ui.s(132), (w - ui.s(444) - ui.s(210)) / 3f);
        ui.text("DIFFICULTY  -  locked once the profile is made", dx, cy, 13, createDifficulty < 0 ? Ui.AMBER : Ui.TXT_HI);
        for (int i = 0; i < 3; i++) if (ui.tab("c_diff" + i, dx + i * (dw + ui.s(4)), cy + ui.s(20), dw, ui.s(34), Profile.DIFFICULTY[i].toUpperCase(), null, createDifficulty == i)) { if (createDifficulty != i) c.sfx("difficulty_chosen", 0.6f); createDifficulty = i; }
        createIronman = ui.toggle("c_iron", dx + 3 * (dw + ui.s(4)) + ui.s(10), cy + ui.s(24), ui.s(170), "IRONMAN", createIronman);
        String[] cost = {"Easy: the Zone is slower to notice you and hits less hard. Dying, you keep 5 things of your choice and lose 5% of your cash.",
                "Normal: the intended balance. Dying, you keep 3 things of your choice and lose 10% of your cash.",
                "Hard: sharper eyes, heavier blows, harsher hunger. Dying, you lose everything you carried and 20% of your cash."};
        ui.wrap(createDifficulty < 0 ? "Choose one. It decides how sharp the Zone is and what a death costs - and it cannot be changed afterwards." : cost[createDifficulty] + (createIronman ? "  IRONMAN: one life - when this survivor dies the profile is deleted." : ""), dx, cy + ui.s(60), w - ui.s(444), 11, createDifficulty < 0 ? Ui.AMBER : createIronman ? Ui.RED : Ui.TXT_DIM);
        cy += ui.s(100);
        // 0.6: how many people the Zone holds is decided here, before the profile exists - overall and for each group
        if (ui.tab("c_tabA", x + ui.s(24), cy, ui.s(190), ui.s(30), "APPEARANCE", null, !createPopOpen)) createPopOpen = false;
        if (ui.tab("c_tabP", x + ui.s(220), cy, ui.s(190), ui.s(30), "POPULATION", null, createPopOpen)) createPopOpen = true;
        cy += ui.s(40);
        if (creator == null) creator = new CharacterCreator(c);
        if (!createPopOpen) creator.draw(ui, createApp, null, x + ui.s(24), cy, w - ui.s(48), h - (cy - y) - ui.s(84));
        else {
            ui.wrap("How many people live in this survivor's Zone. 1.00 is the Zone as designed; the figure is a multiplier on everyone who is there when the Zone is first surveyed, and on what it drifts back toward afterwards. It cannot be changed once the profile is made.", x + ui.s(24), cy, w - ui.s(48), 12, Ui.TXT_DIM);
            float sy = cy + ui.s(56), sw = Math.min(ui.s(560), w - ui.s(48));
            String[][] rows = {{"all", "Everyone"}, {"infected", "Infected"}, {"military", "Military"}, {"raiders", "Bandits"}, {"survivors", "Loners"}, {"mercenaries", "Mercenaries"}};
            float total = createPop.getOrDefault("all", 1f);
            for (String[] rw : rows) {
                float v = createPop.getOrDefault(rw[0], 1f);
                float nv = ui.slider("c_pop_" + rw[0], x + ui.s(24), sy, sw, rw[1], v, absentialis.sim.Agents.POP_MIN, absentialis.sim.Agents.POP_MAX, "%.2fx");
                nv = Math.round(nv * 20) / 20f;
                if (nv != v) createPop.put(rw[0], nv);
                if (!rw[0].equals("all")) ui.text("= " + String.format("%.2fx", nv * total) + " of the designed number", x + ui.s(40) + sw, sy + ui.s(4), 11, Ui.TXT_DIM);
                sy += ui.s(rw[0].equals("all") ? 62 : 46);
            }
            if (ui.button("c_popreset", x + ui.s(24), sy + ui.s(6), ui.s(220), ui.s(36), "RESET TO DESIGNED", null, true)) createPop.clear();
        }
        boolean ok = nameField.toString().trim().length() >= 2 && createDifficulty >= 0;
        if (nameField.toString().trim().length() >= 2 && createDifficulty < 0) ui.text("Choose a difficulty to continue", x + ui.s(300), y + h - ui.s(52), 12, Ui.AMBER);
        if (ui.button("c_go", x + ui.s(24), y + h - ui.s(64), ui.s(260), ui.s(44), "ENTER THE ZONE", "arrow", ok) || ok && c.in.keyPressed(GLFW_KEY_ENTER)) {
            Profile p = new Profile();
            p.slot = createSlot;
            p.name = nameField.toString().trim();
            p.appearance = createApp.copy();
            p.ironman = createIronman;
            p.difficulty = createDifficulty;
            for (java.util.Map.Entry<String, Float> pe : createPop.entrySet()) if (Math.abs(pe.getValue() - 1f) > 0.001f) p.population.put(pe.getKey(), pe.getValue());
            p.seed = 1337L;
            p.starterKit();
            p.contracts.refill(p.seed, 1);
            SaveSystem.save(p);
            c.sfx("profile_created", 0.7f);
            loadProfile(createSlot);
        }
        if (ui.button("c_back", x + w - ui.s(184), y + h - ui.s(64), ui.s(160), ui.s(44), "BACK", "cross", true) || c.in.keyPressed(GLFW_KEY_ESCAPE)) go(State.PROFILES);
    }

    private void loadProfile(int slot) {
        Profile p = SaveSystem.load(slot);
        if (p == null) return;
        c.profile = p;
        String err = p.verify();
        if (err != null) System.err.println("[Profile] integrity warning: " + err);
        InventoryService.listener = null;
        hideout = new HideoutScreen(c);
        hideout.enter();
        go(State.HIDEOUT);
    }

    // ------------------------------------------------------------------ hideout
    private void hideoutFrame(float dt) {
        if (menuTrack != null) menuMusic(true);
        if (settingsOpen) { menuFrame(dt, () -> {}); return; }
        hideout.render(dt);
        String a = hideout.action;
        hideout.action = null;
        if ("settings".equals(a)) settingsOpen = true;
        else if ("title".equals(a)) go(State.TITLE);
        else if ("deploy".equals(a)) deploy(hideout.deployInsertion, hideout.deployHour);
    }

    private int pendingInsertion;

    /**
     * 0.7: there is no length to choose - only the hour to go in at (morning, afternoon, midnight). The Zone's clock is
     * moved forward to the next time it shows that hour; it then runs on through the operation as it always did.
     */
    private void deploy(int insertion, double hour) {
        if (!worldGenerated) return;
        pendingInsertion = insertion;
        ZoneState z = c.profile.zone;
        double h = ((hour % 24) + 24) % 24;
        if (Math.abs(z.hour - h) > 0.01) { if (h < z.hour) z.day++; z.hour = h; }
        prepareWorldForRaid();
        lightReady = 0; lightWait = 0;
        go(State.LOADING);
    }

    private float lightReady, lightWait;
    private final WorldRenderer.Env loadEnv = new WorldRenderer.Env();

    /** Lights the irradiance probes around the insertion before the raid begins, so the first frame is already lit. */
    private boolean prelight(float dt) {
        if (c.hr == null) return true;
        MapData.Point ins = c.world.map.insertions.get(Math.floorMod(pendingInsertion, c.world.map.insertions.size()));
        double h = c.profile.zone.hour;
        double sa = (h % 24 - 6) / 12.0 * Math.PI;
        loadEnv.daylight = ZoneState.daylightAt(h);
        loadEnv.sunX = (float) Math.cos(sa) * 0.8f; loadEnv.sunY = (float) Math.sin(sa); loadEnv.sunZ = 0.35f;
        District d = c.world.district(ins.x, ins.z);
        float nightK = 0.12f + 0.88f * loadEnv.daylight;
        loadEnv.fogR = d.fogR * nightK; loadEnv.fogG = d.fogG * nightK; loadEnv.fogB = d.fogB * nightK;
        loadEnv.rain = 0;
        lightReady = c.hr.prepare(c.wr, loadEnv, ins.x, ins.z, 192);
        lightWait += dt;
        return lightReady >= 0.92f || lightWait > 25;
    }

    private void loadingFrame(float dt) {
        boolean prepDone = prepThread == null || !prepThread.isAlive();
        if (prepDone && c.wr != null) { c.wr.scheduleDirty(); c.wr.uploadResults(0); }
        boolean meshed = prepDone && c.wr != null && !c.wr.busy();
        boolean lit = meshed && prelight(dt);
        menuFrame(dt, () -> {
            Ui ui = c.ui;
            ui.textC("DEPLOYING", ui.w / 2f, ui.h * 0.38f, 36, Ui.TXT_HI);
            String s = !prepDone ? genStatus : !meshed ? "Meshing the city (" + (c.wr == null ? "?" : c.wr.pending.get()) + " columns left)" : "Lighting the insertion area (" + Math.round(lightReady * 100) + "%)";
            float bw = ui.s(360), bx = ui.w / 2f - bw / 2, by = ui.h * 0.38f + ui.s(84);
            ui.b.rect(bx, by, bw, ui.s(6), 0xFF1A1C1B);
            ui.b.rect(bx, by, bw * (!prepDone ? 0.15f : !meshed ? 0.45f : 0.5f + 0.5f * lightReady), ui.s(6), Ui.AMBER);
            ui.textC(s, ui.w / 2f, ui.h * 0.38f + ui.s(56), 14, Ui.TXT_DIM);
            String[] tips = {"Sound carries. Sprinting on metal is heard a block away; crouching on carpet is not.", "A tactical reload keeps the chambered round. Double-tap R to drop the magazine and go faster.",
                    "Weapons under 50 condition jam. Under 25 they need a workbench.", "Faction hostility needs evidence. Unwitnessed is not forgiven - only unknown.",
                    "Treasures and keys take no case space. Combine treasures for value.", "There is no clock on an operation. Leave by any exit when you have what you came for.",
                    "[F7] tells the squad to hold fire. They will still defend themselves.", "A Gladiator has no eyes. Stand still and it will walk past you."};
            ui.textC(tips[(int) (c.time / 6) % tips.length], ui.w / 2f, ui.h * 0.62f, 14, Ui.AMBER);
            float cell = Math.max(1, ui.s(3)), lw = absentialis.engine.Astratto.logoWidth() * cell;
            absentialis.engine.Astratto.drawLogo(ui.b, ui.w / 2f - lw / 2, ui.h * 0.74f, cell, 0xFF8E897C);
            ui.textC("BUILT ON THE " + absentialis.engine.Astratto.NAME.toUpperCase(), ui.w / 2f, ui.h * 0.74f + absentialis.engine.Astratto.logoHeight() * cell + ui.s(10), 11, Ui.TXT_DIM);
        });
        if (lit) startRaid();
    }

    private void startRaid() {
        Profile p = c.profile;
        c.bus.clear();
        Raid r = new Raid(c.world, p, c.settings, c.bus, pendingInsertion);
        c.map.rebuildHatch(p.zone);
        raidScreen = new RaidScreen(c, r);
        lastRaid = r;
        menuMusic(false);
        go(State.RAID);
    }

    private void raidFrame(float dt) {
        if (settingsOpen) { win.setCursorLocked(false); menuFrame(dt, () -> {}); if (!settingsOpen) raidScreen.paused = true; return; }
        raidScreen.update(dt);
        raidScreen.render(dt);
        if ("settings".equals(raidScreen.pauseAction)) { raidScreen.pauseAction = null; settingsOpen = true; }
        if ("quit".equals(raidScreen.pauseAction)) { raidScreen.raid.debrief.outcome = Raid.Debrief.Outcome.ABANDONED; raidScreen.raid.debrief.reason = "Left the Zone (quit)"; endRaid(); win.requestClose(); return; }
        if (raidScreen.done) endRaid();
    }

    /** Callsign of an Ironman survivor who has just been lost (shown once on the title screen). */
    private String ironLost;

    private void endRaid() {
        raidScreen.stopAudio();
        win.setCursorLocked(false);
        Raid r = raidScreen.raid;
        String choice = raidScreen.deathChoice;
        boolean killed = r.debrief.outcome == Raid.Debrief.Outcome.KILLED;
        debriefLines = RaidResolver.resolve(r);
        // Ironman: one life. The profile and its backup are deleted the moment the death resolves.
        boolean ironDeath = killed && c.profile.ironman;
        if (ironDeath) {
            ironLost = c.profile.name;
            SaveSystem.delete(c.profile.slot);
            c.profile = null;
            hideout = null;
            summaryAge = 99;
        } else SaveSystem.save(c.profile);
        menuMusic(true);
        if (r.debrief.outcome == Raid.Debrief.Outcome.EXTRACTED) c.sfx("extract_done", 0.6f);
        if ("quit".equals(choice)) { win.requestClose(); return; }
        if (ironDeath || "menu".equals(choice)) { go(State.TITLE); return; }
        c.sfx("debrief_summary", 0.6f);
        debriefRankUp = RaidResolver.lastRankAfter > RaidResolver.lastRankBefore || debriefLines.stream().anyMatch(l -> l.contains(" -> ") && !l.startsWith("+"));
        debriefAge = 0;
        go(State.DEBRIEF);
    }

    private boolean debriefRankUp;
    private float debriefAge;

    private void debrief() {
        Ui ui = c.ui;
        // 0.9: a promotion is heard once, in the operation, when the XP bar says so (scene.XpFeed) - not again here afterwards
        float was = debriefAge;
        debriefAge += ui.dt;
        // a contract paid, or an investigation brought out: heard once, after the summary has landed
        if (lastRaid != null && lastRaid.debrief.contractsDone + lastRaid.debrief.missionsProven > 0 && was < 2.2f && debriefAge >= 2.2f) c.sfx("contract_complete", 0.65f);
        Raid.Debrief d = lastRaid.debrief;
        boolean ok = d.outcome == Raid.Debrief.Outcome.EXTRACTED, left = d.outcome == Raid.Debrief.Outcome.ABANDONED;
        float w = Math.min(ui.w - ui.s(40), ui.s(980)), h = ui.h - ui.s(80), x = (ui.w - w) / 2, y = ui.s(40);
        float cy = ui.window("Debrief", ok || left ? "flag" : "skull", x, y, w, h);
        int col = ok ? Ui.GREEN : left || d.outcome == Raid.Debrief.Outcome.MIA ? Ui.AMBER : Ui.RED;
        ui.text(ok ? "EXTRACTED" : left ? "OPERATION ABANDONED" : d.outcome == Raid.Debrief.Outcome.MIA ? "MISSING IN ACTION" : "KILLED IN ACTION", x + ui.s(24), cy, 32, col);
        ui.text(d.reason + String.format("   -   %d:%02d in the Zone", (int) lastRaid.time / 60, (int) lastRaid.time % 60), x + ui.s(24), cy + ui.s(42), 14, Ui.TXT);
        cy += ui.s(80);
        float colW = (w - ui.s(72)) / 2;
        ui.text("EXPERIENCE", x + ui.s(24), cy, 14, Ui.TXT_HI);
        float ly = cy + ui.s(24);
        for (String[] xp : d.xp) { ui.text(xp[0], x + ui.s(24), ly, 13, Ui.TXT); ui.textR("+" + xp[1], x + ui.s(24) + colW, ly, 13, Ui.AMBER); ly += ui.s(20); }
        ly += ui.s(8);
        ui.text(String.format("Shots %d  -  hits %d  -  containers %d  -  noise %.0f", d.shotsFired, d.hits, d.containersSearched, d.noise), x + ui.s(24), ly, 12, Ui.TXT_DIM);
        ly += ui.s(26);
        absentialis.ui.Insignia.draw(ui, c.profile.prog.rank(), x + ui.s(24), ly - ui.s(4), ui.s(26), RaidResolver.lastRankAfter > RaidResolver.lastRankBefore ? 0xFFE2A034 : 0xFFB8B2A4);
        ui.text(c.profile.prog.title() + "  -  " + absentialis.Version.coins(c.profile.scrip) + (RaidResolver.lastRankAfter > RaidResolver.lastRankBefore ? "   RANK UP" : ""), x + ui.s(58), ly, 16, Ui.TXT_HI);
        ui.bar(x + ui.s(24), ly + ui.s(26), colW, ui.s(10), c.profile.prog.rankProgress(), 0xFFE2A034);
        float rx = x + ui.s(48) + colW;
        ui.text("CONSEQUENCES", rx, cy, 14, Ui.TXT_HI);
        float ry = cy + ui.s(24);
        for (String l : debriefLines) {
            if (ry > y + h - ui.s(90)) break;
            ry += ui.wrap("- " + l, rx, ry, colW, 13, l.startsWith("NEWS") || l.startsWith("STORY") ? Ui.AMBER : l.startsWith("Lost") ? Ui.RED : l.startsWith("Contract complete") || l.startsWith("Kept") ? Ui.GREEN : Ui.TXT) + ui.s(4);
        }
        if (ui.button("d_cont", x + w - ui.s(300), y + h - ui.s(64), ui.s(276), ui.s(44), ok || left ? "RETURN TO HIDEOUT" : "RESPAWN AT THE HIDEOUT", "arrow", true) || c.in.keyPressed(GLFW_KEY_ENTER)) {
            hideout.enter();
            // 0.7: what this deployment opened is shown as soon as the survivor is home
            hideout.unlocks.show(RaidResolver.lastRankBefore, RaidResolver.lastRankAfter);
            RaidResolver.lastRankBefore = RaidResolver.lastRankAfter;
            go(State.HIDEOUT);
        }
    }

    // ------------------------------------------------------------------ console
    private Raid raid() { return state == State.RAID && raidScreen != null ? raidScreen.raid : null; }

    private boolean categoryExists(String cat) { for (absentialis.gameplay.items.ItemDef d : Content.ITEMS.values()) if (cat.equalsIgnoreCase(d.category)) return true; return false; }

    /** Makes one item and puts it where the player can get at it: the case in a raid (the ground if it does not fit), the stash otherwise. */
    private boolean giveOne(absentialis.gameplay.items.ItemDef d, int qty) {
        try {
            ItemInstance it = new ItemInstance(d.id);
            it.qty = Math.max(1, Math.min(d.maxStack, qty));
            it.origin = "console";
            if (d.weapon != null && !d.weapon.melee() && Content.ammoFor(d.weapon.caliber) != null) { it.rounds = it.capacity(); it.ammoId = Content.ammoFor(d.weapon.caliber).id; }
            Raid r = raid();
            if (r != null) {
                if (!InventoryService.store(it, c.profile.loadout).ok()) r.dropItem(it, r.player.x + (float) Math.sin(giveSpread) * 1.2f, r.player.y + 1f, r.player.z + (float) Math.cos(giveSpread) * 1.2f);
                giveSpread += 0.7f;
            } else while (!InventoryService.add(it, c.profile.stash).ok() && c.profile.stash.h < 600) c.profile.stash.h += 4;
            return true;
        } catch (RuntimeException e) { return false; }
    }
    private float giveSpread;

    /**
     * 0.8: more of the game reachable from the console, for testing a feature without playing up to it. Each says what
     * it did, and each refuses with a reason where it does not apply.
     */
    private void registerCommands08(Console k) {
        k.register("items", "items [text] - list item ids (all of them, or those whose id, name or category contains the text)", a -> {
            String f = a.length > 1 ? a[1].toLowerCase() : "";
            StringBuilder b = new StringBuilder();
            int n = 0;
            for (absentialis.gameplay.items.ItemDef d : Content.ITEMS.values()) if (f.isEmpty() || d.id.contains(f) || d.name.toLowerCase().contains(f) || f.equalsIgnoreCase(d.category)) { b.append(n++ % 4 == 0 ? "\n  " : "   ").append(d.id); }
            return n + " items" + (f.isEmpty() ? "" : " matching '" + f + "'") + ":" + b;
        }, null);
        k.register("rank", "rank <1-50> - set your Rank (the XP of this climb is set to exactly that Rank)", a -> {
            if (c.profile == null || a.length < 2) return "rank <1-50>";
            int want = Math.max(1, Math.min(absentialis.gameplay.progression.Progression.RANKS, Integer.parseInt(a[1])));
            c.profile.prog.cycleXp = absentialis.gameplay.progression.Progression.xpToReach(want);
            c.profile.prog.bestRank = Math.max(c.profile.prog.bestRank, want);
            c.profile.applyCapacity();
            SaveSystem.save(c.profile);
            return "Rank " + c.profile.prog.rank() + " - " + c.profile.prog.title();
        }, null);
        k.register("prestige", "prestige <0-15> - set your Prestige", a -> {
            if (c.profile == null || a.length < 2) return "prestige <0-15>";
            c.profile.prog.prestige = Math.max(0, Math.min(absentialis.gameplay.progression.Progression.MAX_PRESTIGE, Integer.parseInt(a[1])));
            SaveSystem.save(c.profile);
            return c.profile.prog.title();
        }, null);
        k.register("difficulty", "difficulty [easy|normal|hard] - show the profile's locked difficulty, or (developer only) change it", a -> {
            if (c.profile == null) return "No profile loaded";
            if (a.length > 1) { int d = switch (a[1].toLowerCase()) { case "easy" -> 0; case "hard" -> 2; default -> 1; }; c.profile.difficulty = d; SaveSystem.save(c.profile); }
            return c.profile.difficultyName() + ": on death keep " + c.profile.keepOnDeath() + ", lose " + Math.round(c.profile.cashLostOnDeath() * 100) + "% of cash";
        }, i -> List.of("easy", "normal", "hard"));
        k.register("pos", "print where you are: coordinates, district, facing", a -> {
            Raid r = raid();
            if (r == null) return "Only in a raid";
            District d = r.world.district(r.player.x, r.player.z);
            return String.format("x %.1f  y %.1f  z %.1f   facing %.0f deg   %s", r.player.x, r.player.y, r.player.z, Math.toDegrees(r.player.yaw), d == null ? "" : d.display);
        }, null);
        k.register("goto", "goto <x> <z> [y] - teleport to coordinates", a -> {
            Raid r = raid();
            if (r == null || a.length < 3) return "In a raid: goto <x> <z> [y]";
            float x = Float.parseFloat(a[1]), z = Float.parseFloat(a[2]);
            int gy = r.groundY((int) x, (int) z);
            place(r, x, a.length > 3 ? Float.parseFloat(a[3]) : gy < 0 ? r.player.y : gy, z);
            return String.format("At %.1f, %.1f, %.1f", r.player.x, r.player.y, r.player.z);
        }, null);
        k.register("kill", "kill [all|infected|<faction>] - kill what is within 60 m (everything hostile by default)", a -> {
            Raid r = raid();
            if (r == null) return "Only in a raid";
            String what = a.length > 1 ? a[1].toLowerCase() : "hostile";
            int n = 0;
            for (Actor o : new ArrayList<>(r.actors)) {
                if (o.isPlayer || o.dead || o.mate()) continue;
                float d = (float) Math.hypot(o.x - r.player.x, o.z - r.player.z);
                boolean pick = what.equals("all") || what.equals("infected") && o.infected() || what.equals("hostile") && (o.infected() || o.hostileToPlayer) || what.equals(o.faction);
                if (d > 60 || !pick) continue;
                r.damageActor(o, r.player, 99999, null, "head", o.x, o.y + 1.6f, o.z);
                n++;
            }
            return "Killed " + n;
        }, i -> { List<String> l = new ArrayList<>(List.of("all", "infected", "hostile")); l.addAll(List.of(ZoneState.FACTIONS)); return l; });
        k.register("clear", "clear - remove every NPC and body from the operation (the squad stays)", a -> {
            Raid r = raid();
            if (r == null) return "Only in a raid";
            int n = 0;
            for (Actor o : new ArrayList<>(r.actors)) if (!o.isPlayer && !o.mate()) { r.neural.remove(o.id, true); r.neuralBody.forget(o.id); r.actors.remove(o); n++; }
            return "Removed " + n;
        }, null);
        k.register("freeze", "freeze - stop every NPC thinking (again to let them go)", a -> { Raid r = raid(); if (r == null) return "Only in a raid"; r.freezeAI = !r.freezeAI; return "AI " + (r.freezeAI ? "frozen" : "thinking"); }, null);
        k.register("holdfire", "holdfire [on|off] - tell the squad to hold fire or fire at will", a -> {
            Raid r = raid();
            if (r == null) return "Only in a raid";
            boolean hold = a.length > 1 ? a[1].equalsIgnoreCase("on") : !r.holdFire("gold");
            r.setHoldFire("gold", hold);
            return "Squad: " + (hold ? "holding fire" : "fire at will");
        }, i -> List.of("on", "off"));
        k.register("car", "car <id> - put a vehicle on the road ahead (Tab lists them)", a -> {
            Raid r = raid();
            if (r == null || a.length < 2) return "In a raid: car <id>";
            return r.vehicles.debugSpawn(a[1]) == null ? "No such vehicle" : "Spawned " + a[1];
        }, i -> { List<String> l = new ArrayList<>(); for (absentialis.vehicle.CarHandlingData d : absentialis.vehicle.CarHandlingData.all()) l.add(d.id); return l; });
        k.register("weather", "weather clear|fog|rain|storm|snow [now] | weather strike", a -> {
            if (c.profile == null || a.length < 2) return "weather clear|fog|rain|storm|snow [now] | weather strike";
            if (a[1].equalsIgnoreCase("strike")) { if (raidScreen == null) return "No operation."; var r = raidScreen.raid; r.weather.strike(r.focusX, r.focusY, r.focusZ, a.length > 2 ? Float.parseFloat(a[2]) : 90f, a.length > 3 ? (float) Math.atan2(-Math.cos(r.player.yaw), -Math.sin(r.player.yaw)) : r.rng.nextFloat() * 6.28f, r.fx, r.worldFx); return "Lightning."; }
            c.profile.zone.setWeather(absentialis.engine.weather.Weather.Kind.of(a[1]).name());
            // "now": no waiting for it to come on (the scripted runs use it)
            if (raidScreen != null) { raidScreen.raid.weather.set(absentialis.engine.weather.Weather.Kind.of(a[1])); if (a.length > 2 || a[1].equalsIgnoreCase("clear")) raidScreen.raid.weather.snap(); }
            return "Weather: " + c.profile.zone.weather;
        }, i -> List.of("clear", "rain"));
        k.register("hurt", "hurt <amount> - take damage (tests the hurt voice, armour and the death screen)", a -> {
            Raid r = raid();
            if (r == null) return "Only in a raid";
            boolean was = r.god; r.god = false;
            r.damageActor(r.player, null, a.length > 1 ? Float.parseFloat(a[1]) : 25, null, "torso", r.player.x, r.player.y + 1.2f, r.player.z);
            r.god = was;
            return "Health " + Math.round(r.player.hp);
        }, null);
        k.register("sound", "sound <name> - play a sound by name (Tab lists them); 'sound' alone reports the audio engine", a -> {
            if (c.audio == null) return "No audio";
            if (a.length < 2) return absentialis.engine.audio.UltraEngine.NAME + ": " + c.audio.samples.size() + " sounds, " + c.audio.recorded.size() + " recorded, " + c.audio.streams.size() + " streamed beds, " + c.audio.activeVoices() + " voices playing. " + c.audio.status;
            if (c.audio.hasStream(a[1])) { c.audio.loopStream(a[1], 0.6f, absentialis.engine.audio.UltraEngine.AMBIENCE).fadeTo(0, 12); return "Bed " + a[1] + " (12 s)"; }
            if (!c.audio.samples.containsKey(a[1])) return "No sound called " + a[1];
            c.audio.play(a[1], 0.8f);
            return a[1] + ": " + c.audio.takes(a[1]) + " take(s), " + (c.audio.recorded.contains(a[1]) ? "recorded" : "synthesised");
        }, i -> { List<String> l = new ArrayList<>(); if (c.audio != null) { for (String n : c.audio.samples.keySet()) if (!n.matches(".*_\\d+$")) l.add(n); l.addAll(c.audio.streams.keySet()); } java.util.Collections.sort(l); return l; });
        k.register("spinel", "spinel [down|goto] - where the nearest ceiling spinel is; 'down' knocks it loose, 'goto' puts you under it", a -> {
            Raid r = raid();
            if (r == null || r.spinels == null) return "Only in a raid";
            CeilingSpinels.Stone s = r.spinels.nearest(r.player.x, r.player.y, r.player.z, 2000);
            if (s == null) return "None left up (" + r.spinels.shotDown + " shot down of " + r.spinels.stones.size() + ")";
            if (a.length > 1 && a[1].equalsIgnoreCase("down")) { r.spinels.knock(s); return "Knocked down a " + Content.def(s.item).name; }
            if (a.length > 1 && a[1].equalsIgnoreCase("goto")) { place(r, s.x + 1.2f, s.floorY, s.z + 1.2f); return "Under a " + Content.def(s.item).name + " - look up"; }
            return String.format("%s %s at %.0f, %.0f, %.0f - %.0f m away; %d still up of %d", Content.def(s.item).name, s.exterior ? "outdoors" : "indoors", s.x, s.y, s.z, Math.hypot(s.x - r.player.x, s.z - r.player.z), r.spinels.remaining(), r.spinels.stones.size());
        }, i -> List.of("down", "goto"));
        k.register("devmode", "devmode - Developer Mode: the player character is gone, you are a camera (noclip) with a workbench for spawning anything; nothing reacts to you. Again to leave.", a -> {
            if (state != State.RAID || raidScreen == null || raidScreen.raid == null) return "Deploy first: Developer Mode works on a live operation (the devmap is a good place for it)";
            if (raidScreen.raid.player.dead) return "Not while dead";
            if (raidScreen.raid.vehicles.inside()) return "Get out of the vehicle first";
            return raidScreen.dev.toggle();
        }, null);
        k.register("photo", "photo - 0.9: the picture and nothing else: hides the HUD, the hands and the weapon. Again to bring them back.", a -> { if (state != State.RAID || raidScreen == null) return "Only in a raid"; raidScreen.photo = !raidScreen.photo; return "Photo mode " + (raidScreen.photo ? "on" : "off"); }, null);
        k.register("hotspot", "hotspot [go] - 0.9: what is going on in each district this operation. 'hotspot go' brings the one in your district into being now and puts it on the map.", a -> {
            if (state != State.RAID || raidScreen == null || raidScreen.raid == null) return "Deploy first";
            Raid r = raidScreen.raid;
            r.events.begin();
            if (a.length > 1 && a[1].equals("go")) { var l = r.events.force(r.world.district(r.player.x, r.player.z)); if (l == null) return "No hotspot in this district"; l.announced = true; return l.def.name() + ": " + l.side.size() + " and " + l.other.size() + " people, " + Math.round(r.player.distXZ(l.x, l.z)) + " m " + DistrictEvents.bearing(r.player.x, r.player.z, l.x, l.z); }
            StringBuilder b = new StringBuilder();
            for (var l : r.events.hotspots) b.append(l.district.display).append(": ").append(l.def.name()).append(l.resolved ? " (over: " + l.outcome + ")" : l.spawned ? " (under way)" : "").append("   ");
            return b.length() == 0 ? "No hotspots" : b.toString();
        }, null);
        k.register("event", "event <gunfight|patrol|looters|scavengers|survivor|pack> - 0.9: one of the smaller district events, now, near you.", a -> {
            if (state != State.RAID || raidScreen == null || raidScreen.raid == null) return "Deploy first";
            if (a.length < 2) return "Which? gunfight, patrol, looters, scavengers, survivor, pack";
            int before = raidScreen.raid.events.randoms;
            raidScreen.raid.events.forceRandom(a[1]);
            return raidScreen.raid.events.randoms > before ? "Event: " + a[1] : "Could not place it here";
        }, null);
        k.register("directormode", "directormode - Director Mode: a cinematic sandbox. Choose an actor (yourself, anyone, any infected), set the world, stage and record a scene. Again to leave; everything is put back.", a -> {
            if (state != State.RAID || raidScreen == null || raidScreen.raid == null) return "Deploy first: Director Mode runs on a live operation";
            if (raidScreen.director == null) raidScreen.director = new absentialis.director.Director(raidScreen);
            return raidScreen.director.toggle();
        }, null);
        k.register("faults", "faults - what the AI has tripped over this operation", a -> { Raid r = raid(); if (r == null) return "Only in a raid"; return r.aiFaults == 0 ? "No AI faults" : r.aiFaults + " faults; last: " + r.aiFaultText; }, null);
        k.register("version", "version - the game, the engine and the save format", a -> absentialis.Version.LABEL + " on the " + absentialis.engine.Astratto.NAME + " " + absentialis.engine.Astratto.VERSION + " (save format " + absentialis.Version.SAVE_FORMAT + "). " + absentialis.engine.Astratto.NOTICE, null);
    }

    private void registerCommands() {
        Console k = c.console;
        k.register("help", "list commands", a -> k.help(), null);
        k.register("god", "toggle invulnerability", a -> { Raid r = raid(); if (r == null) return "Only in a raid"; r.god = !r.god; return "god " + (r.god ? "ON" : "OFF"); }, null);
        k.register("noclip", "toggle noclip flight", a -> { Raid r = raid(); if (r == null) return "Only in a raid"; r.noclip = !r.noclip; return "noclip " + (r.noclip ? "ON" : "OFF"); }, null);
        k.register("thirdperson", "toggle third-person camera", a -> { if (raidScreen == null) return "Only in a raid"; raidScreen.thirdPerson = !raidScreen.thirdPerson; return "third person " + raidScreen.thirdPerson; }, null);
        k.register("give", "give <itemId> [qty] - creates a new instance in your case (or stash)", a -> {
            if (c.profile == null) return "No profile loaded";
            if (a.length < 2) return "give <item> [qty]  -  every item in the game can be given: by id, by the end of its id, or by name. 'items' lists them; 'give all' gives one of each.";
            // 0.8: 'give all' and 'give <category>' - one of everything, for testing what has no shop
            if (a[1].equalsIgnoreCase("all") || Content.find(a[1]) == null && categoryExists(a[1])) {
                int n = 0, failed = 0;
                for (absentialis.gameplay.items.ItemDef d : Content.ITEMS.values()) {
                    if (!a[1].equalsIgnoreCase("all") && !a[1].equalsIgnoreCase(d.category)) continue;
                    if (giveOne(d, 1)) n++; else failed++;
                }
                SaveSystem.save(c.profile);
                return "Gave " + n + " items" + (failed > 0 ? " (" + failed + " could not be made)" : "") + (state == State.RAID ? " - what did not fit is at your feet" : " - in your stash");
            }
            // the last word is a quantity if it is a number; everything before it is the item, spaces and all
            int qtyArg = 1, last = a.length;
            if (a.length > 2) try { qtyArg = Integer.parseInt(a[a.length - 1]); last = a.length - 1; } catch (NumberFormatException notANumber) { /* part of the name */ }
            absentialis.gameplay.items.ItemDef found = Content.find(String.join(" ", java.util.Arrays.copyOfRange(a, 1, last)));
            if (found == null) return "No one item is called that. 'items " + a[1] + "' lists what is close; Tab completes ids.";
            ItemInstance it = new ItemInstance(found.id);
            it.qty = Math.max(1, Math.min(it.def().maxStack, qtyArg));
            it.origin = "console";
            if (it.def().weapon != null && !it.def().weapon.melee()) { it.rounds = it.capacity(); it.ammoId = Content.ammoFor(it.def().weapon.caliber).id; }
            var res = state == State.RAID ? InventoryService.store(it, c.profile.loadout) : InventoryService.add(it, c.profile.stash);
            // no room in the case: it is put on the ground in front of you rather than refused
            if (!res.ok() && state == State.RAID && raid() != null) { Raid rr = raid(); rr.dropItem(it, rr.player.x - (float) Math.sin(rr.player.yaw) * 0.9f, rr.player.y + 1f, rr.player.z - (float) Math.cos(rr.player.yaw) * 0.9f); return "Gave " + it + " (no room: it is on the ground in front of you)"; }
            if (!res.ok() && state != State.RAID) { while (!InventoryService.add(it, c.profile.stash).ok() && c.profile.stash.h < 400) c.profile.stash.h += 4; res = InventoryService.Result.OK; }
            if (res.ok() && state != State.RAID) {
                ItemInstance landed = it.container == c.profile.stash ? it : null;
                if (landed == null) for (ItemInstance o : c.profile.stash.items) if (o.defId.equals(it.defId)) landed = o;
                if (landed != null) c.inv.arrive(landed);
                SaveSystem.save(c.profile);
            }
            return res.ok() ? "Gave " + it + (state == State.RAID ? " (in your case)" : " (in your stash - INVENTORY tab)") : res.msg();
        }, i -> { if (i != 0) return null; List<String> l = Content.names(); l.add("all"); return l; });
        registerCommands08(k);
        k.register("tp", "tp <district|exit id> - teleport", a -> {
            Raid r = raid();
            if (r == null || a.length < 2) return "Usage in raid: tp <district|exit>";
            for (MapData.Exit e : c.world.map.exits) if (e.id.equalsIgnoreCase(a[1])) { place(r, e.x + 0.5f, e.y, e.z + 2.5f); return "Teleported to " + e.name; }
            District d = District.byName(a[1]);
            if (d == null) return "Unknown destination";
            for (MapData.Point p : c.world.map.insertions) if (p.district == d) { place(r, p.x + 0.5f, p.y, p.z + 0.5f); return "Teleported to " + p.name; }
            for (MapData.Node n : c.world.map.nodes) if (n.district == d) { place(r, n.x + 0.5f, n.y, n.z + 0.5f); return "Teleported to " + d.display; }
            return "No spot found";
        }, i -> { List<String> l = new ArrayList<>(); for (District d : District.values()) l.add(d.name().toLowerCase()); if (c.world != null) for (MapData.Exit e : c.world.map.exits) l.add(e.id); return l; });
        k.register("setrep", "setrep <faction> <value>", a -> { if (c.profile == null || a.length < 3) return "Usage: setrep <faction> <value>"; c.profile.rep.put(a[1], Float.parseFloat(a[2])); return a[1] + " = " + a[2]; }, i -> i == 0 ? List.of(ZoneState.FACTIONS) : null);
        k.register("econ", "econ audit [graph] - verify no buy/sell loop, show price curves", a -> {
            if (c.profile == null) return "No profile";
            StringBuilder b = new StringBuilder(Economy.audit(c.profile.zone, c.profile.rep));
            if (a.length > 2 && "graph".equals(a[2])) {
                b.append("\nSale saturation (repeated sales of one item):");
                ItemInstance probe = new ItemInstance(-1, "val.watch");
                for (int s = 0; s <= 10; s += 2) {
                    int v = Economy.sellPrice(probe, Economy.trader("mara"), c.profile.zone, 0, s);
                    b.append("\n  sale ").append(s).append(": ").append(v).append(" ").append("#".repeat(Math.max(1, v / 4)));
                }
            }
            return b.toString();
        }, i -> i == 0 ? List.of("audit") : i == 1 ? List.of("graph") : null);
        k.register("skip", "skip hour [n] - advance the strategic world clock", a -> {
            if (c.profile == null) return "No profile";
            int n = a.length > 2 ? Integer.parseInt(a[2]) : 1;
            String[] before = c.profile.zone.controllers();
            c.profile.zone.advance(n, c.profile.seed);
            c.profile.zone.announceFlips(before);
            if (c.map.ready()) c.map.rebuildHatch(c.profile.zone);
            return "Advanced " + n + "h -> day " + c.profile.zone.day + " " + (int) c.profile.zone.hour + ":00";
        }, i -> i == 0 ? List.of("hour") : null);
        k.register("ai", "ai debug - toggle AI state overlay", a -> { Raid r = raid(); if (r == null) return "Only in a raid"; r.aiDebug = !r.aiDebug; return "AI debug " + r.aiDebug; }, i -> i == 0 ? List.of("debug") : null);
        k.register("noise", "noise debug - show sound propagation rings", a -> { Raid r = raid(); if (r == null) return "Only in a raid"; r.noiseDebug = !r.noiseDebug; return "noise debug " + r.noiseDebug; }, i -> i == 0 ? List.of("debug") : null);
        k.register("save", "save the profile now", a -> { if (c.profile == null) return "No profile"; if (state == State.RAID) return "Saving is disabled mid-raid (raid state resolves on exit)"; SaveSystem.save(c.profile); return "Saved slot " + c.profile.slot; }, null);
        k.register("load", "reload the profile from disk", a -> { if (c.profile == null || state == State.RAID) return "Not available"; loadProfile(c.profile.slot); return "Loaded"; }, null);
        k.register("xp", "xp <amount> - grant XP", a -> { if (c.profile == null) return "No profile"; int r = c.profile.prog.addXp(Long.parseLong(a[1])); return "Rank " + c.profile.prog.rank() + (r > 0 ? " (+" + r + ")" : ""); }, null);
        k.register("heal", "full heal (raid) / health (hideout)", a -> { Raid r = raid(); if (r != null) { r.player.hp = 100; r.body.bleedHeavy = r.body.bleedLight = r.body.fracture = false; } else if (c.profile != null) c.profile.health = 100; return "Healed"; }, null);
        k.register("extract", "force a successful extraction", a -> { Raid r = raid(); if (r == null) return "Only in a raid"; r.debrief.outcome = Raid.Debrief.Outcome.EXTRACTED; r.debrief.exitName = "Console"; r.debrief.reason = "Extracted (console)"; return "Extracting"; }, null);
        k.register("time", "time <hour 0-24> - set the hour of the day (0.7: operations have no time limit)", a -> {
            if (c.profile == null || a.length < 2) return "Usage: time <hour 0-24>";
            Raid r = raid();
            double h = ((Double.parseDouble(a[1]) % 24) + 24) % 24;
            c.profile.zone.hour = r == null ? h : ((h - r.time / 120.0) % 24 + 24) % 24;
            return String.format("It is %02d:%02d", (int) h, (int) (h * 60) % 60);
        }, i -> i == 0 ? List.of("8", "14", "0", "20") : null);
        k.register("weaponlevel", "weaponlevel [weaponId] <level> - set the level of a weapon (the one in your hands when no id is given)", a -> {
            if (c.profile == null) return "No profile loaded";
            if (a.length < 2) return "Usage: weaponlevel [weaponId] <level>";
            String id = a.length >= 3 ? Content.canon(a[1]) : null;
            if (id == null) {
                for (String s : new String[]{"primary", "sidearm"}) { ItemInstance w = c.profile.loadout.slot(s); if (w != null && w.def().weapon != null && !w.def().weapon.melee()) { id = w.defId; break; } }
                Raid r = raid();
                if (r != null && r.player.weapon != null && r.player.weapon.def().weapon != null && !r.player.weapon.def().weapon.melee()) id = r.player.weapon.defId;
            }
            absentialis.gameplay.items.ItemDef d = id == null ? null : Content.def(id);
            if (d == null || d.weapon == null || d.weapon.melee()) return "No firearm: name one (weaponlevel weapon.pm9 12) or hold one";
            int max = absentialis.gameplay.weapons.Attachments.maxLevel(d), lvl;
            try { lvl = Integer.parseInt(a[a.length - 1].replace("max", String.valueOf(max))); } catch (NumberFormatException e) { return "Level must be a number from 1 to " + max; }
            lvl = Math.max(1, Math.min(max, lvl));
            c.profile.prog.mastery.put(d.id, absentialis.gameplay.weapons.Attachments.xpForLevel(lvl));
            if (state != State.RAID) SaveSystem.save(c.profile);
            return d.name + " is level " + c.profile.prog.weaponLevel(d.id) + " / " + max;
        }, i -> { if (i != 0) return List.of("1", "5", "10", "20", "max"); List<String> l = new ArrayList<>(); for (String id : absentialis.gameplay.progression.Progression.platforms()) { var d = Content.def(id); if (d.weapon != null && !d.weapon.melee()) l.add(id); } return l; });
        k.register("spawn", "spawn <archetype|faction> - spawn an infected archetype or a faction member 12m ahead", a -> {
            Raid r = raid();
            if (r == null || a.length < 2) return "Only in a raid";
            float x = r.player.x - (float) Math.sin(r.player.yaw) * 12, z = r.player.z - (float) Math.cos(r.player.yaw) * 12;
            if (Animals.Kind.of(a[1]) != null) return "Spawned " + r.animals.debugSpawn(a[1], x, r.standY((int) Math.floor(x), (int) Math.floor(z), Math.round(r.player.y)), z, a.length > 2 ? Math.max(1, Integer.parseInt(a[2])) : 1);
            Actor sp = r.debugSpawn(a[1], x, z, r.player.y);
            if (sp == null) return "No such kind. Infected: " + String.join(", ", absentialis.neuralai.integration.Archetypes.INFECTED) + " (or regular, fast). People: " + String.join(", ", ZoneState.FACTIONS) + ".";
            return "Spawned " + (sp.infected() ? absentialis.neuralai.integration.Archetypes.displayName(sp.archetype) + " infected" : ZoneState.cap(sp.faction) + " " + sp.archetype);
        }, i -> { List<String> l = new ArrayList<>(); for (var ar : absentialis.neuralai.integration.Archetypes.all()) if (ar.infected()) l.add(ar.id()); l.add("regular"); l.add("fast"); l.addAll(List.of(ZoneState.FACTIONS)); return l; });
        k.register("sydney", "sydney on|off|stats - the Sydney Engine (active ragdolls). off puts bodies back on the 0.5 passive ragdoll for comparison", a -> {
            if (state != State.RAID || raidScreen == null || raidScreen.raid == null) return "Deploy first";
            Raid r = raidScreen.raid;
            if (a.length > 1 && (a[1].equals("on") || a[1].equals("off"))) r.sydneyEnabled = a[1].equals("on");
            RagdollController rc = r.gore.ragdolls;
            return "Sydney " + (r.sydneyEnabled ? "on" : "off") + ": " + r.sydneyBodies + " bodies driven, " + rc.physicalStaggers + " staggers answered physically, " + rc.selfRecoveries + " got up by themselves, " + rc.leftCrawling + " left crawling, " + rc.blowsOnTheGround + " hit while down";
        }, i -> i == 0 ? List.of("on", "off", "stats") : null);
        k.register("money", "money <amount> - set the cash you hold (money +500 / money -500 adds or takes)", a -> {
            if (c.profile == null) return "No profile";
            if (a.length < 2) return "You hold " + absentialis.Version.coins(c.profile.scrip) + ". Usage: money <amount>";
            String v = a[1].replace(",", "").replace("_", "");
            long n = Long.parseLong(v.startsWith("+") ? v.substring(1) : v);
            long now = v.startsWith("+") || v.startsWith("-") ? c.profile.scrip + n : n;
            c.profile.scrip = (int) Math.max(0, Math.min(Integer.MAX_VALUE, now));
            if (state != State.RAID) SaveSystem.save(c.profile);
            return "Cash: " + absentialis.Version.coins(c.profile.scrip);
        }, i -> i == 0 ? List.of("1000", "10000", "100000", "+5000") : null);
        k.register("devmap", "devmap [n|off] - load the developer test map (a maze, a two-storey mansion with a double door, a long road, a yard of test objects) and deploy on it at insertion n (0 road, 1 maze, 2 mansion, 3 yard); devmap off brings the Zone back", a -> {
            if (!worldGenerated || !renderReady) return "The world is still being built";
            if (state == State.LOADING) return "Already loading";
            boolean off = a.length > 1 && "off".equals(a[1]);
            if (off && !c.world.dev) return "The Zone is already loaded";
            if (c.profile == null) return "Load a profile first";
            int n = 0;
            if (a.length > 1 && !off) try { n = Integer.parseInt(a[1]); } catch (NumberFormatException e) { return "devmap [0-3|off]"; }
            String r = loadMap(!off);
            if (r != null) return r;
            if (off) { go(State.HIDEOUT); return "The Zone is back. Deploy as usual."; }
            deploy(n, 12);
            return "Devmap loaded - deploying. It is empty: spawn what you need (spawn, vehicle). 'devmap off' brings the Zone back.";
        }, i -> i == 0 ? List.of("0", "1", "2", "3", "off") : null);
        k.register("vehicle", "vehicle <id> - spawn a vehicle ahead of you (vehicle list shows the ids; vehicle clear removes the ones you are not in)", a -> {
            Raid r = raid();
            if (r == null) return "Only in a raid";
            List<absentialis.vehicle.CarHandlingData> all = absentialis.vehicle.CarHandlingData.all();
            if (a.length < 2 || a[1].equals("list")) { StringBuilder b = new StringBuilder("Vehicles:"); for (var d : all) b.append("\n  ").append(d.id).append(" - ").append(d.name).append(" (").append(d.family).append(")"); return b.toString(); }
            if (a[1].equals("clear")) return r.vehicles.debugClear() + " vehicle(s) removed";
            var d = absentialis.vehicle.CarHandlingData.get(a[1]);
            if (d == null) for (var o : all) if (o.id.contains(a[1]) || o.name.toLowerCase().contains(a[1].toLowerCase())) { d = o; break; }
            if (d == null) return "No such vehicle. Try: vehicle list";
            var car = r.vehicles.debugSpawn(d.id);
            return car == null ? "No clear, level ground ahead for a " + d.name : "Spawned " + d.name;
        }, i -> { if (i != 0) return null; List<String> l = new ArrayList<>(List.of("list", "clear")); for (var d : absentialis.vehicle.CarHandlingData.all()) l.add(d.id); return l; });
        k.register("rivalry", "rivalry groups | group <id> | mem <id> | utility <id> | events [n] | seed | tick [n] | favor <id> <value>  -  inspect and drive the Group Rivalry System", a -> {
            if (c.profile == null) return "No profile";
            absentialis.rivalry.RivalryEngine e = absentialis.rivalry.RivalryWorld.engine(c.profile);
            String sub = a.length < 2 ? "groups" : a[1];
            absentialis.rivalry.RivalryState.Group g = a.length >= 3 ? e.s.group(a[2].startsWith("grp-") ? a[2] : "grp-" + a[2]) : null;
            switch (sub) {
                case "groups": { StringBuilder b = new StringBuilder("tick " + e.s.tick + ", " + e.s.active().size() + " groups:"); for (var x : e.s.active()) { var l = e.s.leaderOf(x); b.append(String.format("\n  %-7s %-30s %-12s %2d strong  %-18s favor %4.0f (%s)  doing: %s", x.id, x.name, absentialis.rivalry.Doctrine.display(x.faction), x.size, l == null ? "-" : l.name, x.playerRelation.favor, e.status(x), x.lastAction)); } return b.toString(); }
                case "group": return g == null ? "Usage: rivalry group <id>" : e.describe(g) + "\n  history: " + String.join("\n           ", g.history.subList(0, Math.min(8, g.history.size()))) + "\n  favor audit: " + String.join(" ; ", g.playerRelation.audit.subList(0, Math.min(6, g.playerRelation.audit.size())));
                case "mem": return g == null ? "Usage: rivalry mem <id>" : e.memories(g);
                case "utility": return g == null ? "Usage: rivalry utility <id>" : e.utilities(g);
                case "events": { int n = a.length >= 3 ? Integer.parseInt(a[2]) : 12; StringBuilder b = new StringBuilder(e.s.log.size() + " events on record; the last " + n + ":"); for (int i = Math.max(0, e.s.log.size() - n); i < e.s.log.size(); i++) b.append("\n  ").append(e.s.log.get(i)); return b.toString(); }
                case "seed": return "seed " + e.s.seed + ", " + e.s.rolls + " numbers drawn, tick " + e.s.tick + ", " + e.s.rumours.size() + " rumours in transit";
                case "tick": { int n = a.length >= 3 ? Integer.parseInt(a[2]) : 1; var news = e.tick(n, absentialis.rivalry.RivalryWorld.wallet(c.profile)); return "Advanced " + n + " tick(s) to " + e.s.tick + (news.isEmpty() ? "" : ":\n  " + String.join("\n  ", news)); }
                case "favor": { if (g == null || a.length < 4) return "Usage: rivalry favor <id> <value>"; g.playerRelation.favor = Math.max(-100, Math.min(100, Float.parseFloat(a[3]))); return g.name + ": favor " + g.playerRelation.favor + " (" + e.status(g) + ")"; }
                default: return "rivalry groups | group <id> | mem <id> | utility <id> | events [n] | seed | tick [n] | favor <id> <value>";
            }
        }, i -> i == 0 ? List.of("groups", "group", "mem", "utility", "events", "seed", "tick", "favor") : i == 1 && c.profile != null ? new ArrayList<>(c.profile.rivalry.groups.keySet()) : null);
        k.register("freeze", "toggle AI decisions (bodies and animation keep running)", a -> { Raid r = raid(); if (r == null) return "Only in a raid"; r.freezeAI = !r.freezeAI; return "AI " + (r.freezeAI ? "frozen" : "running"); }, null);
        k.register("alife", "alife [log] - strategic squads and the off-screen event log", a -> {
            if (c.profile == null) return "No profile";
            StringBuilder sb = new StringBuilder();
            for (var sq : c.profile.zone.squads) sb.append(String.format("#%d %s x%d in %s -> %s%n", sq.id, ZoneState.cap(sq.faction), sq.size, District.values()[sq.district].display, District.values()[sq.goal].display));
            for (int i = 0; i < Math.min(10, c.profile.zone.log.size()); i++) { var e = c.profile.zone.log.get(i); sb.append(String.format("  d%d %02d:00 [%s] %s%n", e.day(), e.hour(), e.type(), e.text())); }
            Raid r = raid();
            if (r != null) sb.append("raid: noise events ").append(r.noiseEvents).append(", migrations ").append(r.migrations).append(", dispatches ").append(r.dispatches).append(", evidence ").append(r.evidence.size());
            return sb.length() == 0 ? "No strategic state yet - skip hour to advance" : sb.toString().trim();
        }, i -> i == 0 ? List.of("log") : null);
        k.register("selftest", "run the quality gates now", a -> { int f = absentialis.tools.SelfTests.runHeadless("all"); return f == 0 ? "All gates passed" : f + " gate(s) FAILED - see stdout"; }, null);
        k.print("ABSENTIALIS console. Type 'help'. Tab autocompletes.");
    }

    private static void place(Raid r, float x, float y, float z) { r.player.x = x; r.player.y = y + 0.1f; r.player.z = z; r.player.vy = 0; }

    // ------------------------------------------------------------------ autoshot (scripted visual verification)
    private void autoProfile() {
        Profile p = new Profile();
        p.slot = 3;
        p.name = "Autoshot";
        p.starterKit();
        p.contracts.refill(p.seed, 1);
        p.scrip += 60000;
        p.garage.add("car.kolos");
        p.deployVehicle = "car.kolos";
        SaveSystem.save(p);
    }

    private void autoshotStep() {
        shotFrame++;
        String dir = autoshot;
        switch (state) {
            case TITLE -> { if (shotFrame == 20) shot(dir, "01_title"); if (shotFrame > 25 && worldGenerated) loadProfile(3); }
            case HIDEOUT -> {
                int f = shotFrame;
                if (hideoutT == 0) { hideoutT = f; equipForAuto(); }
                int rel = f - hideoutT;
                int nt = HideoutScreen.TABS.length;
                // 0.6: a new survivor is asked for an alliance before the hideout answers; the pass takes a picture of the question, then answers it
                if (rel == 5 && !absentialis.gameplay.Alliance.chosen(c.profile)) shot(dir, "02a_alliance_choice");
                if (rel == 7 && !absentialis.gameplay.Alliance.chosen(c.profile)) { absentialis.gameplay.Alliance.choose(c.profile, "military"); hideout.enter(); }
                for (int t = 0; t < nt; t++) if (rel == 10 + t * 12) { hideout.tab = t; }
                for (int t = 0; t < nt; t++) if (rel == 20 + t * 12) shot(dir, "02_hideout_" + HideoutScreen.TABS[t].toLowerCase());
                if (rel == 24 + nt * 12) hideout.debugShow(ShopPanel.VEHICLES, false);
                if (rel == 34 + nt * 12) shot(dir, "02b_shop_vehicles");
                if (rel == 36 + nt * 12) hideout.debugShow(-1, true);
                if (rel == 46 + nt * 12) shot(dir, "02c_operation_briefing");
                if (rel == 48 + nt * 12) hideout.debugDeployTab(1);
                if (rel == 58 + nt * 12) shot(dir, "02d_leaderboard");
                if (rel == 60 + nt * 12) hideout.debugDeployTab(2);
                if (rel == 70 + nt * 12) shot(dir, "02e_rivalries");
                if (rel == 72 + nt * 12) { hideout.debugShow(-1, false); hideout.debugRecruiting(); }
                if (rel == 82 + nt * 12) shot(dir, "02f_recruiting");
                if (rel == 84 + nt * 12) hideout.debugShow(-1, false);
                if (rel >= 88 + nt * 12 && renderReady && raidT == 0 && state == State.HIDEOUT && !deployed) { deployed = true; deploy(0, 9.0); }
            }
            case RAID -> {
                if (raidT == 0) raidT = shotFrame;
                int rel = shotFrame - raidT;
                Raid r = raidScreen.raid;
                var in = c.in;
                if (rel == 1) stageSpot(r.world);
                if (tour >= 0) { raidT++; tourStep(r, dir); break; }
                int fire = absentialis.engine.Input.MOUSE_OFFSET + GLFW_MOUSE_BUTTON_LEFT;
                // staged on an open stretch of street found in the generated city: one of everyone in a line-up, AI frozen so they hold the pose
                if (rel == 3) { r.freezeAI = true; r.god = true; place(r, sx, World.GROUND, sz); r.player.yaw = 0; r.player.pitch = 0; }
                if (rel == 6) {
                    String[] cast = {"military", "mercenaries", "raiders", "survivors", "walker", "sprinter", "bomber", "tank"};
                    for (int i = 0; i < cast.length; i++) { Actor a = r.debugSpawn(cast[i], sx - 7f + i * 1.5f, sz - 8f - (i >= 4 ? 2 : 0), World.GROUND); a.yaw = (float) Math.PI; a.homeX = a.x; a.homeZ = a.z; lineup.add(a); }
                }
                if (rel == 40) shot(dir, "03_raid_start");
                if (rel == 44) r.player.pitch = -1.25f;
                if (rel == 56) shot(dir, "03b_first_person_legs");
                if (rel == 58) { r.player.pitch = 0; raidScreen.thirdPerson = true; }
                if (rel == 80) shot(dir, "06_raid_thirdperson");
                if (rel == 82) in.inject(GLFW_KEY_W, true);
                if (rel == 104) shot(dir, "06b_thirdperson_walk");
                if (rel == 106) { in.inject(GLFW_KEY_W, false); raidScreen.thirdPerson = false; place(r, sx, World.GROUND, sz); }
                if (rel >= 112 && rel < 116) in.inject(fire, rel % 2 == 1);
                if (rel == 116) { shot(dir, "05_firing"); in.inject(fire, false); }
                if (rel == 120) in.inject(GLFW_KEY_R, true);
                if (rel == 121) in.inject(GLFW_KEY_R, false);
                if (rel == 150) shot(dir, "05b_reload");
                if (rel == 200) { for (int i = 0; i < 4 && i < lineup.size(); i++) r.damageActor(lineup.get(i), r.player, 500, absentialis.gameplay.items.Content.def("ammo.rifle"), i % 2 == 0 ? "torso" : "head", lineup.get(i).x, lineup.get(i).y + 1, lineup.get(i).z); }
                if (rel == 212) shot(dir, "10a_ragdoll_falling");
                if (rel == 290) shot(dir, "10b_ragdoll_settled");
                if (rel == 292) in.inject(GLFW_KEY_TAB, true);
                if (rel == 312) shot(dir, "07_weapon_wheel");
                if (rel == 313) in.inject(GLFW_KEY_TAB, false);
                if (rel == 320) { if (!raidScreen.debugOpenNearestContainer(25)) raidScreen.invOpen = true; }
                if (rel == 340) shot(dir, "08_inventory_loot");
                if (rel == 341) { raidScreen.invOpen = false; raidScreen.debugBarter(); }
                if (rel == 344) { shot(dir, "08b_barter"); raidScreen.trade.close(); }
                if (rel == 345) { in.inject(GLFW_KEY_I, true); }
                if (rel == 346) { in.inject(GLFW_KEY_I, false); raidScreen.invOpen = false; raidScreen.mapOpen = true; }
                if (rel == 365) shot(dir, "09_map");
                if (rel == 370) { raidScreen.mapOpen = false; tour = 0; }
                if (rel == 372) { place(r, sx, World.GROUND, sz); r.player.yaw = 0; r.player.pitch = 0; }
                if (rel == 405) { c.console.exec("time 2.5"); r.player.pitch = 0; r.player.hp = 60; r.body.healthChanged = r.time; r.body.staminaChanged = r.time; r.body.stamina = 40; }
                if (rel == 425) shot(dir, "11_hud_red_timer");
                // v0.4: a fire at night, then the vehicle brought in from the garage - outside, inside, and on the move
                if (rel == 430) { c.console.exec("time 22"); place(r, sx, World.GROUND, sz); r.player.yaw = 0; r.player.pitch = 0; r.fire.ignite(sx, World.GROUND, sz - 5f, 1.2f, 60, 0, 0); r.fire.ignite(sx - 3f, World.GROUND, sz - 7f, 0.6f, 60, 0, 0); }
                if (rel == 480) shot(dir, "15_fire_night");
                if (rel == 482) {
                    c.console.exec("time 12");
                    r.fire.clear();
                    VehicleSystem.Car car = null;
                    for (VehicleSystem.Car v : r.vehicles.cars) if (v.owned) car = v;
                    if (car == null) car = r.vehicles.spawn("car.kolos", sx, World.GROUND, sz - 20f, 0f, 3);
                    if (car != null) { car.sim.place(sx, World.GROUND, sz - 20f, 0f); car.sim.wake(); }
                    place(r, sx - 4.5f, World.GROUND, sz - 13.5f); r.player.yaw = -0.6f;
                }
                if (rel == 510) shot(dir, "16_vehicle");
                if (rel == 512) { place(r, sx - 2f, World.GROUND, sz - 20f); }
                if (rel == 516) r.vehicles.enter();
                if (rel == 570) shot(dir, "17_vehicle_driver_seat");
                if (rel == 572) in.inject(GLFW_KEY_W, true);
                if (rel == 600) in.inject(GLFW_KEY_D, true);
                if (rel == 612) { shot(dir, "18_vehicle_driving"); c.settings.vehicleDebug = true; }
                if (rel == 613) raidScreen.thirdPerson = false;
                if (rel == 616) { shot(dir, "18b_vehicle_cockpit_debug"); c.settings.vehicleDebug = false; in.inject(GLFW_KEY_W, false); in.inject(GLFW_KEY_D, false); raidScreen.thirdPerson = true; }
                if (rel == 618) r.vehicles.exit();
                // v0.3: a live contact for the squad (torches, gore, severed limbs), then the player's death sequence
                if (rel == 628) {
                    c.console.exec("time 25");
                    r.freezeAI = false;
                    place(r, sx, World.GROUND, sz); r.player.yaw = 0; r.player.pitch = 0;
                    for (Actor m : r.mates) { m.x = sx - 2f + r.mates.indexOf(m) * 1.5f; m.z = sz + 3f; m.y = World.GROUND; }
                    for (int i = 0; i < 3; i++) { Actor e = r.debugSpawn("raiders", sx - 4f + i * 4f, sz - 18f, World.GROUND); e.hostileToPlayer = true; e.yaw = (float) Math.PI; if (i == 0) r.gore.sever(e, Gore.ARM_L, 0, 1, 4); }
                }
                if (rel == 760) shot(dir, "13_squad_contact");
                if (rel == 764) { r.god = false; r.player.hp = 0; }
                if (rel == 840) shot(dir, "14_death_cam");
                if (rel == 1210) shot(dir, "14b_death_menu");
                if (rel == 1214) raidScreen.chooseDeath("respawn");
            }
            case DEBRIEF -> { if (debriefT == 0) debriefT = shotFrame; if (shotFrame - debriefT == 20) { shot(dir, "12_debrief"); win.requestClose(); } }
            default -> {}
        }
        if (shotFrame > 20000) win.requestClose();
    }

    /** Where the scripted scenes are staged: the first stretch of open ground in the city wide and long enough for them. */
    private float sx = 250.5f, sz = 300.5f;
    private int tour = -1;
    private Actor tourCasualty;
    private absentialis.scene.Doors.Door tourDoor;

    private void stageSpot(World w) {
        int g = World.GROUND;
        for (int ring = 0; ring < 200; ring += 2) for (int x = 352 - ring; x <= 352 + ring; x += 2) for (int z = 352 - ring; z <= 352 + ring; z += 2) {
            if (Math.max(Math.abs(x - 352), Math.abs(z - 352)) != ring) continue;
            boolean ok = w.block(x, g - 1, z).mat == absentialis.world.Material.ASPHALT;
            for (int ix = x - 8; ix <= x + 6 && ok; ix++) for (int iz = z - 22; iz <= z + 5 && ok; iz++) {
                if (!w.solid(ix, g - 1, iz)) ok = false;
                for (int y = g; y < g + 4 && ok; y++) if (w.get(ix, y, iz) != 0) ok = false;
            }
            if (ok) { sx = x + 0.5f; sz = z + 0.5f; System.out.println("[Autoshot] staged at " + x + ", " + z); return; }
        }
        System.out.println("[Autoshot] no open stretch found; staging at the default spot");
    }

    /** A flight over the generated city: overview, boundary, each landmark compound, then a door and a casualty at street level. */
    private void tourStep(Raid r, String dir) {
        final int each = 50;
        var anchors = r.world.map.anchors;
        int views = 2 + anchors.size(), k = tour / each, f = tour % each;
        Actor p = r.player;
        if (k < views) {
            if (f == 0) {
                r.noclip = true;
                if (k == 0) { place(r, 352, 88, 520); p.yaw = 0; p.pitch = -0.62f; }
                else if (k == 1) { place(r, 660, World.GROUND + 16, 300); p.yaw = (float) Math.PI / 2; p.pitch = -0.2f; }
                else { var l = anchors.get(k - 2); place(r, l.cx(), World.GROUND + 36, l.z1 + 30); p.yaw = 0; p.pitch = -0.72f; }
            }
            p.vx = p.vy = p.vz = 0;
            if (f == each - 6) shot(dir, k == 0 ? "20_city_overview" : k == 1 ? "21_boundary_east" : String.format("22_%02d_%s", k - 1, anchors.get(k - 2).id));
        } else {
            int t = tour - views * each;
            if (t == 0) {
                r.noclip = false;
                // the nearest unlocked wooden door with standing room in front of it
                float best = 1e9f;
                for (var d : r.doors.doors) {
                    if (d.locked || d.destroyed || d.kind != absentialis.scene.Doors.WOOD || d.y != World.GROUND) continue;
                    int nx = d.alongX ? 0 : 1, nz = d.alongX ? 1 : 0;
                    boolean free = true;
                    for (int i = 1; i <= 3 && free; i++) free = !r.world.solid(d.x + nx * i, d.y, d.z + nz * i) && !r.world.solid(d.x + nx * i, d.y + 1, d.z + nz * i) && r.world.solid(d.x + nx * i, d.y - 1, d.z + nz * i);
                    if (!free) continue;
                    float dd = (d.x - sx) * (d.x - sx) + (d.z - sz) * (d.z - sz);
                    if (dd < best) { best = dd; tourDoor = d; }
                }
                if (tourDoor != null) {
                    var d = tourDoor;
                    int nx = d.alongX ? 0 : 1, nz = d.alongX ? 1 : 0;
                    place(r, d.cx() + nx * 2.4f, d.y, d.cz() + nz * 2.4f);
                    p.yaw = (float) Math.atan2(nx, nz); p.pitch = -0.05f;
                }
            }
            if (t == 30) shot(dir, "23_door_closed");
            if (t == 32 && tourDoor != null) r.doors.open(tourDoor, p);
            if (t == 75) shot(dir, "23b_door_open");
            if (t == 77 && tourDoor != null) r.doors.blast(tourDoor, 60, 0, 0, p.id);
            if (t == 79 && tourDoor != null) r.doors.use(tourDoor, p);
            if (t == 120) shot(dir, "23c_door_damaged");
            if (t == 122) {
                place(r, sx, World.GROUND, sz); p.yaw = 0; p.pitch = -0.25f;
                tourCasualty = r.debugSpawn("survivors", sx, sz - 3.2f, World.GROUND);
                Actor foe = r.debugSpawn("raiders", sx + 2.5f, sz - 6f, World.GROUND);
                r.casualty.down(tourCasualty, p, "bullet");
                r.bus.emit(new absentialis.engine.events.Events.HitConfirmed(p.id, foe.id, "torso", 10, r.time));
                r.damageActor(foe, p, 5000, absentialis.gameplay.items.Content.def("ammo.rifle"), "head", foe.x, foe.y + 1.6f, foe.z);
            }
            if (t == 134) shot(dir, "24_downed_marker_killfeed");
            if (t == 200) shot(dir, "24b_downed_bleeding");
            if (t == 202) { r.aiDebug = true; }
            if (t == 215) { shot(dir, "25_ai_debug"); r.aiDebug = false; }
            if (t == 217) { if (tourCasualty != null) r.actors.remove(tourCasualty); tour = -1; return; }
        }
        tour++;
    }

    private final List<Actor> lineup = new ArrayList<>();
    private int hideoutT, raidT, debriefT;
    private boolean deployed;

    private void equipForAuto() {
        Profile p = c.profile;
        for (ItemInstance i : new ArrayList<>(p.stash.items)) {
            if (i.def().slot != null && p.loadout.slot(i.def().slot) == null) InventoryService.equip(i, p.loadout, i.def().slot);
        }
        for (ItemInstance i : new ArrayList<>(p.stash.items)) if (i.def().medical != null || i.def().ammo != null || i.def().food != null || i.def().throwable != null) InventoryService.store(i, p.loadout);
        ItemInstance rifle = p.loadout.slot("primary");
        if (rifle != null) { absentialis.gameplay.weapons.Gunsmith.install(rifle, "att.optic.reddot"); absentialis.gameplay.weapons.Gunsmith.install(rifle, "att.muzzle.compensator"); }
    }

    private void shot(String dir, String name) { Screenshot.save(win.width, win.height, dir + "/" + name + ".png"); }
}
