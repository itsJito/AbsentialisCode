package absentialis.director;

import static org.lwjgl.glfw.GLFW.*;

import absentialis.director.GUIManager.Menu;
import absentialis.director.SessionManager.State;
import absentialis.director.StageManagers.CameraManager;
import absentialis.director.StageManagers.PerformanceManager;
import absentialis.director.WorldManagers.LocationManager;
import absentialis.director.WorldManagers.ModifierManager;
import absentialis.director.WorldManagers.PopulationManager;
import absentialis.director.WorldManagers.VehicleManager;
import absentialis.director.WorldManagers.WorldManager;
import absentialis.engine.Input;
import absentialis.entity.Actor;
import absentialis.scene.ActorRenderer;
import absentialis.scene.RaidScreen;
import absentialis.ui.Ui;
import absentialis.vehicle.CarHandlingData;
import java.util.ArrayList;
import java.util.List;

/**
 * Director Mode (0.8; console: {@code directormode}). An interactive cinematic sandbox: choose a character, set the
 * world, stage a performance, record it - without the rules of an operation getting in the way.
 *
 * It is two interfaces over twelve managers. The interfaces are the character selection screen (shown on entry: the
 * actors, the shortlist, the settings, and the way in and out) and the interaction menu ([F1] in the world: settings,
 * locations, dialogue and actions, the shortlist, vehicles, camera and recording, NeuralAI, and the way back). Neither
 * of them does anything itself: every row calls a manager, and reads what it shows back from that manager.
 *
 *   SessionManager      activation, transitions, cleanup, restoration        GUIManager          menus, navigation, input
 *   ActorManager        the registry, models, appearances, switching         WorldManager        time and weather
 *   PopulationManager   people, infected and vehicle density                 ModifierManager     invincibility, noclip, wanted, AI
 *   LocationManager     presets and travel                                   PerformanceManager  gestures and dialogue
 *   VehicleManager      who may drive; spawn and repair                      CameraManager       camera modes and transitions
 *   RecordingManager    capture and clips (editor: ClipEditor)               PersistenceManager  shortlist and preferences
 */
public final class Director {
    private final RaidScreen screen;
    public final SessionManager session;
    public final GUIManager gui = new GUIManager();
    public final ActorManager actors;
    public final WorldManager world;
    public final PopulationManager population;
    public final ModifierManager modifiers;
    public final LocationManager locations;
    public final PerformanceManager performance;
    public final VehicleManager vehicles;
    public final CameraManager camera;
    public final RecordingManager recording;
    public final PersistenceManager persistence = new PersistenceManager();
    public final ClipEditor editor = new ClipEditor();

    /** The actor highlighted or chosen on the casting screen. */
    public ActorManager.Spec chosen = new ActorManager.Spec();
    private ActorRenderer.Look previewLook;
    private float previewSpin;
    private String note = "";
    private float noteAt = -99, clock;
    /** People the director put in the scene by hand (none yet beyond the population; kept so cleanup has one list to empty). */
    private final List<Actor> staged = new ArrayList<>();
    private Menu castingRoot, interactionRoot;

    public Director(RaidScreen screen) {
        this.screen = screen;
        session = new SessionManager(screen, this);
        actors = new ActorManager(screen);
        world = new WorldManager(screen.raid);
        population = new PopulationManager(screen.raid);
        modifiers = new ModifierManager(screen.raid);
        locations = new LocationManager(screen.raid);
        performance = new PerformanceManager(screen);
        vehicles = new VehicleManager(screen.raid);
        camera = new CameraManager(screen);
        recording = new RecordingManager(screen.ctx().audio);
        recording.cars = () -> screen.raid.vehicles.cars;
        editor.host = this;
        gui.sfx = name -> screen.ctx().sfx(name, 0.4f);
    }

    public boolean active() { return session.active(); }
    /** True while an interface has the whole screen (character selection, the editor) and the world waits. */
    public boolean fullScreen() { return session.state == State.CASTING || session.state == State.EDITING; }
    /** 0.9: true while a Director Mode interface is being worked with the pointer (a menu, the casting screen, the editor) - the game shows it and lets it go. */
    public boolean wantsPointer() { return session.state == State.CASTING || session.state == State.EDITING && !editor.wantsPointerLocked() || session.state == State.ACTIVE && gui.open(); }
    /** True while the scene's own camera may not be changed: always, in the scene. Perspective belongs to the editor (0.9). */
    public boolean viewLocked() { return session.active(); }
    void clearStaged() { for (Actor a : staged) { screen.raid.neural.remove(a.id, true); screen.raid.neuralBody.forget(a.id); screen.raid.actors.remove(a); } staged.clear(); }
    private void say(String s) { note = s == null ? "" : s; noteAt = clock; }
    public String lastNote() { return note; }

    /** Console: start, or (if running) leave. */
    public String toggle() {
        if (session.active()) { session.end(); return "Director Mode ended: the survivor, their kit, the clock and the world are as they were."; }
        String problem = session.begin();
        return problem == null ? "Director Mode: choose an actor. [Up/Down] [Left/Right] [Enter] [Backspace]." : problem;
    }

    // ------------------------------------------------------------------ preferences (persistent session settings)
    void applyPreferences() {
        modifiers.wanted(persistence.pref("wanted", 0) == 1);
        modifiers.invincible(persistence.pref("invincible", 1) == 1);
        population.setNpc(persistence.pref("npc", 2));
        population.setVehicles(persistence.pref("vehicles", 2));
        actors.customs(persistence.characters);
        chosen = persistence.shortlist.isEmpty() ? new ActorManager.Spec() : persistence.shortlist.get(0).copy();
    }

    void savePreferences() {
        persistence.setPref("wanted", modifiers.wanted ? 1 : 0); persistence.setPref("invincible", modifiers.invincible() ? 1 : 0);
        persistence.setPref("npc", population.npc); persistence.setPref("vehicles", population.vehicles);
        persistence.save();
    }

    // ------------------------------------------------------------------ menus
    /** SETTINGS: the eight rows of the design, each owned by a manager. Shared by both interfaces. */
    private Menu settingsMenu() {
        Menu m = new Menu("Settings");
        m.add("Time of Day").value(world::timeName, d -> world.setTime(world.time + d)).hint("Midnight through evening. The clock moves on from there unless it is frozen.");
        m.add("Freeze Time").toggle(() -> world.frozen, world::freeze).hint("Holds the clock where it is for the session.");
        m.add("Weather").value(world::weatherName, d -> world.setWeather(world.weather + d)).hint("Only Clear for now: there is no weather system to choose from yet. It is held clear for the session.");
        m.add("Wanted Status").value(() -> modifiers.wanted ? "Enabled" : "Disabled", d -> modifiers.wanted(!modifiers.wanted)).hint("Disabled: nobody treats the actor as a target. Enabled: the world reacts to them as it would.");
        m.add("NPC Density").value(population::npcName, d -> population.setNpc(population.npc + d)).hint("People and infected near the actor. None empties the streets; what is removed comes back afterwards.");
        m.add("Vehicle Density").value(population::vehicleName, d -> population.setVehicles(population.vehicles + d)).hint("Vehicles standing in the streets.");
        m.add("Invincibility").toggle(modifiers::invincible, modifiers::invincible).hint("The actor takes no damage. (Either way, an actor is never written out of a session.)");
        m.add("Noclip").toggle(modifiers::noclip, modifiers::noclip).hint("The actor flies and passes through walls.");
        return m;
    }

    private Menu actorsMenu(Runnable picked) {
        Menu cats = new Menu("Actors");
        for (String cat : actors.categories()) {
            Menu m = new Menu(cat);
            for (ActorManager.Def def : actors.defs) {
                if (!def.category.equals(cat)) continue;
                final ActorManager.Spec spec = new ActorManager.Spec(def.id, 0, def.canArm && !def.id.equals("you") ? 4 : 0);
                m.add(def.name).value(() -> def.variants > 1 ? "Look " + (Math.floorMod(spec.variant, def.variants) + 1) + " of " + def.variants : "", def.variants > 1 ? d -> { spec.variant = Math.floorMod(spec.variant + d, def.variants); preview(spec); } : null)
                        .run(() -> { chosen = spec.copy(); chosen.loadout = def.canArm ? chosen.loadout : 0; preview(chosen); say("Selected: " + actors.describe(chosen)); picked.run(); })
                        .hint(actors.capabilities(def));
            }
            m.onHighlight = () -> { ActorManager.Def def = null; int k = 0; for (ActorManager.Def x : actors.defs) if (x.category.equals(cat)) { if (k == m.sel) def = x; k++; } if (def != null) preview(new ActorManager.Spec(def.id, 0, def.canArm && !def.id.equals("you") ? 4 : 0)); };
            cats.add(cat).open(m).hint(cat.equals("Survivor") ? "Yourself." : cat.equals("People") ? "A member of each faction, in several appearances." : cat.equals("Infected") ? "The ordinary dead." : "The special ones: unlocked for Director Mode.");
        }
        return cats;
    }

    private Menu shortlistMenu(Runnable picked) {
        Menu m = new Menu("Shortlist");
        m.onOpen = () -> {
            m.items.clear();
            for (ActorManager.Spec s : new ArrayList<>(persistence.shortlist))
                m.add(actors.describe(s)).run(() -> { chosen = s.copy(); preview(chosen); say("Selected: " + actors.describe(chosen)); picked.run(); }).when(() -> actors.def(s.def) != null, () -> "this actor is no longer in the game").hint("[Enter] selects this actor.");
            if (persistence.shortlist.isEmpty()) m.add("(empty - up to " + PersistenceManager.SHORTLIST_MAX + " favourites)").when(() -> false, () -> "add the selected actor with 'Shortlist this actor'");
        };
        m.onHighlight = () -> { if (m.sel < persistence.shortlist.size()) preview(persistence.shortlist.get(m.sel)); };
        return m;
    }

    private void preview(ActorManager.Spec s) { previewLook = actors.look(s); previewSpec = s.copy(); }
    private ActorManager.Spec previewSpec = new ActorManager.Spec();

    /** The character selection screen's root: Actors / Shortlist / Settings / Enter / Exit - and the two things a selection can have done to it. */
    void openCasting() {
        castingRoot = new Menu("Director Mode");
        castingRoot.add("Actors").open(actorsMenu(() -> { while (gui.depth() > 1) gui.back(); })).hint("Every character that can be played. Highlight one to see it; [Enter] selects it.");
        castingRoot.add("Shortlist").value(() -> persistence.shortlist.size() + " / " + PersistenceManager.SHORTLIST_MAX, null).open(shortlistMenu(() -> { while (gui.depth() > 1) gui.back(); })).hint("Your favourite actors, kept between sessions.");
        castingRoot.add("Loadout").value(() -> { ActorManager.Def d = actors.def(chosen.def); return d != null && d.canArm && !d.id.equals("you") ? ActorManager.LOADOUTS[Math.floorMod(chosen.loadout, ActorManager.LOADOUTS.length)][0] : d != null && d.id.equals("you") ? "Your own kit" : "-"; },
                d -> { chosen.loadout = Math.floorMod(chosen.loadout + d, ActorManager.LOADOUTS.length); preview(chosen); })
                .when(() -> { ActorManager.Def d = actors.def(chosen.def); return d != null && d.canArm && !d.id.equals("you"); }, () -> actors.def(chosen.def) != null && actors.def(chosen.def).infected ? "the infected carry nothing" : "the survivor goes in with what they have").hint("What the selected actor carries into the scene.");
        castingRoot.add("Shortlist this actor").value(() -> persistence.shortlisted(chosen) ? "Listed" : "Not listed", null).run(() -> say(persistence.toggle(chosen))).hint("Adds the selected actor to the shortlist, or takes it off. Ten at most.");
        castingRoot.add("Create a character").run(() -> beginCreate(-1)).when(() -> persistence.characters.size() < PersistenceManager.CHARACTERS_MAX, () -> "there are " + PersistenceManager.CHARACTERS_MAX + " already: delete one first").hint("The character creator: build an actor of your own, name it, and play it. Kept between sessions.");
        castingRoot.add("Your characters").value(() -> persistence.characters.size() + " / " + PersistenceManager.CHARACTERS_MAX, null).open(charactersMenu()).hint("Select, change or delete the characters you have made.");
        castingRoot.add("Edit recordings").value(() -> RecordingManager.clips().size() + " clip(s)", null).run(session::toEditor).hint("The cinematic editor: trim, order, grade, change the camera of a take, export.");
        castingRoot.add("Settings").open(settingsMenu()).hint("The world the actor walks into. All of it can be changed again from inside.");
        castingRoot.add("Enter Director Mode").run(() -> { String p = session.enterWorld(); if (p != null) say(p); else say("In the scene as " + actors.describe(chosen) + ". [F1] opens the menu."); }).hint("Into the world as the selected actor.");
        castingRoot.add("Exit Director Mode").run(session::end).confirm("Leave Director Mode and go back to the operation as you were?").hint("Everything is put back as it was.");
        castingRoot.onHighlight = () -> preview(chosen);
        gui.show(castingRoot);
        preview(chosen);
    }

    // ------------------------------------------------------------------ 0.9: the character creator
    private PersistenceManager.Character draft;
    private int draftIndex = -1;
    private final StringBuilder draftName = new StringBuilder();
    private absentialis.scene.CharacterCreator creator;

    public boolean creating() { return draft != null; }

    /** Opens the creator on a new character (-1) or on one of the player's own. */
    public void beginCreate(int index) {
        draft = new PersistenceManager.Character();
        draftIndex = index;
        draftName.setLength(0);
        if (index >= 0 && index < persistence.characters.size()) { PersistenceManager.Character c = persistence.characters.get(index); draft.app = c.app.copy(); draftName.append(c.name); }
        else draftName.append("Actor ").append(persistence.characters.size() + 1);
        if (creator == null) creator = new absentialis.scene.CharacterCreator(screen.ctx());
    }

    private String draftProblem() {
        String n = draftName.toString().trim();
        if (n.isEmpty()) return "give the character a name";
        for (int i = 0; i < persistence.characters.size(); i++) if (i != draftIndex && persistence.characters.get(i).name.equalsIgnoreCase(n)) return "there is already a character called " + n;
        return null;
    }

    /** Keeps the character being made: it is saved, becomes an actor, and is the one selected. Returns null, or why it could not be kept. */
    public String saveDraft() {
        String problem = draftProblem();
        if (problem != null) return problem;
        draft.name = draftName.toString().trim();
        if (draftIndex >= 0 && draftIndex < persistence.characters.size()) persistence.characters.set(draftIndex, draft); else persistence.characters.add(draft);
        persistence.save();
        actors.customs(persistence.characters);
        chosen = new ActorManager.Spec("custom:" + draft.name, 0, 1);
        String made = draft.name;
        draft = null;
        openCasting();
        say(made + " is ready: selected. Choose a loadout, then Enter Director Mode.");
        return null;
    }

    /** A character made in the creator, by hand (tests and scripted runs). */
    public String createCharacter(String name, absentialis.character.Appearance app) { beginCreate(-1); draftName.setLength(0); draftName.append(name); draft.app = app.copy(); return saveDraft(); }

    private Menu charactersMenu() {
        Menu m = new Menu("Your characters");
        m.onOpen = () -> {
            m.items.clear();
            for (int i = 0; i < persistence.characters.size(); i++) {
                final int k = i;
                final PersistenceManager.Character ch = persistence.characters.get(i);
                Menu cm = new Menu(ch.name);
                cm.add("Select").run(() -> { chosen = new ActorManager.Spec("custom:" + ch.name, 0, 1); preview(chosen); say("Selected: " + actors.describe(chosen)); while (gui.depth() > 1) gui.back(); }).hint("Play this character.");
                cm.add("Change appearance").run(() -> beginCreate(k)).hint("Back into the creator with this character.");
                cm.add("Delete").run(() -> { persistence.characters.remove(ch); persistence.shortlist.removeIf(s -> s.def.equals("custom:" + ch.name)); persistence.save(); actors.customs(persistence.characters); if (chosen.def.equals("custom:" + ch.name)) chosen = new ActorManager.Spec(); openCasting(); say("Deleted " + ch.name); }).confirm("Delete " + ch.name + " for good?");
                cm.onHighlight = () -> preview(new ActorManager.Spec("custom:" + ch.name, 0, 1));
                m.add(ch.name).open(cm).hint("[Enter] for what can be done with this character.");
            }
            if (persistence.characters.isEmpty()) m.add("(none yet)").when(() -> false, () -> "make one with 'Create a character'");
        };
        m.onHighlight = () -> { if (m.sel < persistence.characters.size()) preview(new ActorManager.Spec("custom:" + persistence.characters.get(m.sel).name, 0, 1)); };
        return m;
    }

    private void creatorInput(Input in) {
        while (!in.typed.isEmpty()) { char ch = in.typed.poll(); if (ch >= ' ' && ch < 127 && ch != '"' && ch != ';' && draftName.length() < 18) draftName.append(ch); }
        if (in.keyPressed(GLFW_KEY_BACKSPACE) && draftName.length() > 0) draftName.setLength(draftName.length() - 1);
        if (in.keyPressed(GLFW_KEY_ESCAPE)) { draft = null; say("Character creation cancelled"); }
        else if (in.keyPressed(GLFW_KEY_ENTER) || in.keyPressed(GLFW_KEY_KP_ENTER)) { String p = saveDraft(); if (p != null) say("Not saved: " + p); }
    }

    /** The creator screen: the game's own character creator, a name, and the two ways out of it. Worked with the mouse. */
    private void creatorScreen(Ui ui) {
        float W = ui.w, H = ui.h;
        ui.b.rect(0, 0, W, H, 0xFF08090A);
        for (int i = 0; i < 9; i++) ui.b.rect(0, H * i / 9f, W, H / 9f + 1, Ui.alpha(Ui.mix(0xFF14100E, 0xFF08090A, i / 8f), 1));
        ui.b.rect(0, 0, W, ui.s(4), 0xFFB03A2E);
        ui.text("DIRECTOR MODE", ui.s(40), ui.s(34), 34, Ui.TXT_HI);
        ui.text(draftIndex >= 0 ? "Character creator  -  changing " + persistence.characters.get(Math.min(draftIndex, persistence.characters.size() - 1)).name : "Character creator  -  a new actor", ui.s(42), ui.s(78), 14, Ui.AMBER);
        // ---- the name
        float nx = ui.s(40), ny = ui.s(112), nw = ui.s(360), nh = ui.s(30);
        ui.text("NAME", nx, ny + ui.s(8), 13, Ui.TXT_HI);
        ui.b.rect(nx + ui.s(70), ny, nw, nh, 0xFF14161A);
        ui.b.outline(nx + ui.s(70), ny, nw, nh, 1, 0xFF5A5448);
        ui.text(draftName + ((int) (clock * 2) % 2 == 0 ? "_" : ""), nx + ui.s(80), ny + ui.s(7), 15, Ui.TXT_HI);
        String problem = draftProblem();
        ui.text(problem == null ? "Type to change it." : "Cannot save yet: " + problem + ".", nx + ui.s(80) + nw, ny + ui.s(9), 11, problem == null ? Ui.TXT_DIM : Ui.RED);
        // ---- the creator itself
        creator.draw(ui, draft.app, null, ui.s(40), ui.s(160), W - ui.s(80), H - ui.s(250));
        // ---- out
        float by = H - ui.s(70), bw = ui.s(220), bh = ui.s(38);
        if (ui.button("dir_cc_save", W - ui.s(40) - bw, by, bw, bh, "SAVE CHARACTER", null, problem == null)) { String p = saveDraft(); if (p != null) say("Not saved: " + p); }
        if (draft != null && ui.button("dir_cc_cancel", W - ui.s(52) - bw * 2, by, bw, bh, "CANCEL", null, true)) { draft = null; say("Character creation cancelled"); }
        ui.text("[Enter] saves   [Esc] cancels   Saved characters appear under Actors > " + ActorManager.CUSTOM + " and can be shortlisted.", ui.s(40), by + ui.s(12), 11, Ui.TXT_DIM);
        if (clock - noteAt < 3.5f && !note.isEmpty()) ui.textC(note, W / 2f, H - ui.s(96), 14, Ui.AMBER);
    }

    /** Shows the selected actor on the casting screen (what choosing one in the menu does). */
    public void openCastingPreview() { preview(chosen); }

    /** Opens the interaction menu (what [F1] does). */
    public void openMenu() { openInteraction(); }

    private void openInteraction() {
        Menu m = new Menu("Director Mode");
        m.add("Settings").open(settingsMenu()).hint("Time, weather, wanted status, population, invincibility, noclip.");
        Menu loc = new Menu("Location");
        for (LocationManager.Location l : locations.all) loc.add(l.name()).run(() -> { String p = locations.travel(l); if (p == null) { session.curtain = 1; gui.lock(0.35f); say("At " + l.name()); } else say(p); }).when(l::available, l::unavailable).hint("Travel there at once.");
        m.add("Location").open(loc).hint("Instant travel to a place in the city.");
        Menu dlg = new Menu("Dialogue");
        boolean inf = actors.def(actors.current.def) != null && actors.def(actors.current.def).infected;
        for (String g : performance.groups()) {
            Menu gm = new Menu(g);
            for (PerformanceManager.Line l : performance.lines(g)) gm.add(l.text()).run(() -> { String p = performance.speak(l, isInfected()); if (p != null) say(p); }).when(() -> !isInfected(), () -> "the infected do not speak").hint("The actor says it: it is heard, and shown over their head.");
            dlg.add(g).open(gm).hint("Lines the actor can say.");
        }
        Menu act = new Menu("Actions");
        for (PerformanceManager.Gesture g : PerformanceManager.GESTURES) act.add(g.name()).run(() -> { String p = performance.perform(g, isInfected()); if (p != null) say(p); }).when(() -> g.infected() == isInfected() || !g.infected() && (g.clip().equals("stumble") || g.clip().equals("hit")), () -> g.infected() ? "only the infected do that" : "the infected do not make that gesture").hint("A gesture, played on the actor where they stand.");
        dlg.add("Actions and gestures").open(act).hint("Waves, points, salutes - and what the infected do instead.");
        m.add("Dialogue").open(dlg).hint("What the actor says and does.");
        Menu sl = shortlistMenu(() -> { String p = actors.switchTo(chosen, session.original); if (p == null) { session.curtain = 1; gui.lock(0.35f); say("Now playing " + actors.describe(chosen)); } else say(p); });
        Menu all = actorsMenu(() -> { String p = actors.switchTo(chosen, session.original); if (p == null) { session.curtain = 1; gui.lock(0.35f); say("Now playing " + actors.describe(chosen)); } else say(p); });
        Menu swap = new Menu("Shortlist Actors");
        swap.add("From the shortlist").open(sl).hint("Become one of your favourites, where you stand.");
        swap.add("Any actor").open(all).hint("Become any character, where you stand.");
        swap.add("Shortlist the current actor").value(() -> persistence.shortlisted(actors.current) ? "Listed" : "Not listed", null).run(() -> say(persistence.toggle(actors.current)));
        m.add("Shortlist Actors").open(swap).hint("Switch actor without leaving the scene: position and facing are kept.");
        // ---- vehicles
        Menu veh = new Menu("Vehicles");
        Menu spawn = new Menu("Spawn");
        for (CarHandlingData cd : vehicles.models()) spawn.add(cd.name).value(() -> cd.family, null).run(() -> { String p = vehicles.spawn(cd.id); say(p == null ? "Parked a " + cd.name + " ahead" : p); });
        veh.add("Spawn a vehicle").open(spawn).hint("Parked a few metres in front of the actor.");
        veh.add("Repair").run(() -> { String p = vehicles.repair(); say(p == null ? "Repaired" : p); }).hint("The vehicle being driven, or the nearest one.");
        veh.add("This actor can drive").value(() -> vehicles.canDrive() ? "Yes" : "No", null).when(() -> false, () -> vehicles.canDrive() ? "it can: walk up to a vehicle and get in as usual" : "the infected cannot use vehicles").hint("");
        m.add("Vehicles").open(veh).hint("Compatible actors enter, drive and leave vehicles as usual; these are the extras.");
        // ---- camera and recording
        Menu cam = new Menu("Camera & Recording");
        // 0.9: the camera of a scene is not chosen while it is being played. The scene is recorded as it is lived, with a tape of
        // where everybody was; the editor looks at that take from whatever camera is wanted and films it again (ClipEditor)
        cam.add("Camera").value(() -> "The actor's own", null).when(() -> false, () -> "perspective is chosen in the editor: record the scene, then open the take's Perspective there").hint("");
        if (false) cam.add("Field of view").value(() -> camera.fov <= 1 ? "As set" : Math.round(camera.fov) + "°", d -> camera.fov(camera.fov <= 1 ? 60 + d * 5 : camera.fov + d * 5)).when(camera::detached, () -> "only with a detached camera").hint("Wide or long lens for the detached camera.");
        if (false) cam.add("Orbit distance").value(() -> String.format("%.1f m", camera.orbitRadius), d -> camera.orbitRadius = Math.max(1.5f, Math.min(14, camera.orbitRadius + d * 0.5f))).when(() -> camera.mode == CameraManager.ORBIT, () -> "only in the orbit camera");
        if (false) cam.add("Orbit speed").value(() -> String.format("%.2f", camera.orbitSpeed), d -> camera.orbitSpeed = Math.max(-1.5f, Math.min(1.5f, camera.orbitSpeed + d * 0.05f))).when(() -> camera.mode == CameraManager.ORBIT, () -> "only in the orbit camera");
        cam.add("Hide the HUD").toggle(() -> screen.hudHidden, v -> screen.hudHidden = v).hint("The game's own HUD. (It is never in a recording either way: footage is taken before any interface is drawn.)");
        cam.add("Recording").value(() -> recording.recording() ? "REC " + recording.clock() : "Stopped", null).run(this::toggleRecording).hint("[F5] starts and stops. Three minutes at most; then it stops and saves itself.");
        cam.add("Open the editor").value(() -> RecordingManager.clips().size() + " clip(s)", null).run(session::toEditor).hint("Recorded clips go to the cinematic editor: timeline, transitions, effects, sound, perspective, export.");
        m.add("Recording").open(cam).hint("Capturing the scene. [F5] records; the camera of a take is chosen afterwards, in the editor.");
        m.add("Edit recordings").value(() -> RecordingManager.clips().size() + " clip(s)", null).run(session::toEditor).hint("[F6]. The cinematic editor: trim, order, grade, look at a take from another camera, export.");
        // ---- NeuralAI
        Menu ai = new Menu("NeuralAI");
        ai.add("Thinking").toggle(modifiers::thinking, modifiers::thinking).hint("Off: every mind stops. Bodies stand where they are; the world still runs.");
        ai.add("Awareness overlay").toggle(modifiers::overlay, modifiers::overlay).hint("Vision cones, hearing rings, what each mind is doing.");
        ai.add("Minds running").value(() -> String.valueOf(screen.raid.neural.minds().size()), null).when(() -> false, () -> "a reading, not a setting");
        m.add("NeuralAI").open(ai).hint("Control of the AI engine for the scene.");
        m.add("Return to character selection").run(session::toCasting).hint("Choose another actor. The world stays as you have set it.");
        m.add("Quit Director Mode").run(session::end).confirm("Quit Director Mode? Everything is put back as it was.").hint("Back to the operation, as you were.");
        interactionRoot = m;
        gui.show(m);
    }

    private boolean isInfected() { ActorManager.Def d = actors.def(actors.current.def); return d != null && d.infected; }

    public void toggleRecording() {
        if (recording.recording()) { recording.stop(); say(recording.status + "   [F6] edit it"); screen.ctx().sfx("ui_confirm", 0.5f); }
        else { String p = recording.start(); say(p == null ? "Recording. [F5] stops." : p); }
    }

    // ------------------------------------------------------------------ each frame
    /** Before the world updates. Returns true if the session has the keyboard this frame (a menu is open, or the camera is being flown). */
    public boolean update(Input in, float dt) {
        clock += dt;
        session.update(dt);
        gui.tick(dt);
        State st = session.state;
        // the scene tape is gathered from what the renderer draws, while a take is being recorded and at no other time
        screen.ctx().ar.capture = st == State.ACTIVE && recording.recording() && !recording.reshooting() ? recording.tape : null;
        if (st == State.OFF) return false;
        if (st == State.EDITING) { if (!editor.update(in, dt)) session.fromEditor(); return true; }
        if (st == State.CASTING) {
            previewSpin += dt * 0.6f;
            if (draft != null) { creatorInput(in); return true; }
            int r = gui.input(in, dt);
            if (r == 2 || !gui.open()) { if (session.state == State.CASTING) gui.show(castingRoot); }
            return true;
        }
        world.update();
        population.update(dt, staged);
        if (st == State.ENTERING) { camera.update(in, dt, false); return true; }
        // ---- in the world
        if (in.keyPressed(GLFW_KEY_F5)) toggleRecording();
        if (in.keyPressed(GLFW_KEY_F6)) { session.toEditor(); return true; }
        // the scene is played through the actor's own eyes: no detached cameras here (they are the editor's)
        if (camera.mode != CameraManager.GAMEPLAY) camera.setMode(CameraManager.GAMEPLAY);
        if (in.keyPressed(GLFW_KEY_F1)) { if (gui.open()) gui.close(); else openInteraction(); }
        if (gui.open() && in.keyPressed(GLFW_KEY_ESCAPE)) gui.close();
        boolean menu = gui.open();
        if (menu) { int r = gui.input(in, dt); if (r == 2) gui.close(); }
        if (session.curtain > 0 && session.state == State.ACTIVE) session.curtain = Math.max(0, session.curtain - dt / SessionManager.FADE);
        camera.update(in, dt, !menu);
        if (!menu && !camera.holdsControls() && isInfected() && in.pressed("fire")) actors.strike();
        return menu || camera.holdsControls() || gui.locked();
    }

    /** After the world has been drawn and before any interface: this is the picture a recording takes. */
    public void frameRendered(int w, int h, float dt) { if (session.state == State.ACTIVE) recording.frame(w, h, dt); }

    public void draw(Ui ui, float dt) {
        State st = session.state;
        float W = ui.w, H = ui.h;
        if (st == State.EDITING) { editor.draw(ui, dt); return; }
        if (st == State.CASTING) { if (draft != null) creatorScreen(ui); else casting(ui); return; }
        // ---- in the world: a badge, the recording light, the menu when it is open, the last thing said
        if (gui.open()) gui.draw(ui, ui.s(20), ui.s(20), ui.s(430), "DIRECTOR MODE");
        else if (!screen.hudHidden || !recording.recording()) {
            String badge = "DIRECTOR MODE   [F1] menu   [F5] record   [F6] edit recordings";
            float bw = ui.tw(badge, 10) + ui.s(16);
            ui.b.rect(ui.s(12), ui.s(12), bw, ui.s(18), 0xA00A0B0D);
            ui.b.rect(ui.s(12), ui.s(12), ui.s(3), ui.s(18), 0xFFB03A2E);
            ui.text(badge, ui.s(22), ui.s(16), 10, Ui.TXT);
        }
        if (recording.recording()) {
            String rec = "REC  " + recording.clock();
            float rw = ui.tw(rec, 13) + ui.s(40), rx = W / 2f + ui.s(60);           // beside the clock, clear of the map in the corner
            ui.b.rect(rx, ui.s(12), rw, ui.s(24), 0xC00A0B0D);
            if ((int) (clock * 2) % 2 == 0) ui.b.rect(rx + ui.s(9), ui.s(19), ui.s(10), ui.s(10), 0xFFE03A2E);
            ui.text(rec, rx + ui.s(28), ui.s(17), 13, Ui.TXT_HI);
            float left = RecordingManager.MAX_SECONDS - recording.seconds();
            ui.b.rect(rx, ui.s(36), rw * Math.min(1, recording.seconds() / RecordingManager.MAX_SECONDS), ui.s(2), left < 15 ? Ui.RED : 0xFFE03A2E);
        }
        if (camera.detached() && !gui.open()) ui.textC(camera.modeName().toUpperCase() + (camera.mode == CameraManager.FREE ? "   [WASD] fly  [Space]/[Ctrl] up and down  [Shift] fast  [=]/[-] lens" : ""), W / 2f, H - ui.s(30), 10, Ui.TXT_DIM);
        if (clock - noteAt < 3.5f && !note.isEmpty()) { float nw = ui.tw(note, 13) + ui.s(24); ui.b.rect(W / 2f - nw / 2, H - ui.s(78), nw, ui.s(26), 0xD00A0B0D); ui.textC(note, W / 2f, H - ui.s(72), 13, Ui.AMBER); }
        if (session.curtain > 0.01f) ui.b.rect(0, 0, W, H, Ui.alpha(0xFF000000, Math.min(1, session.curtain)));
    }

    /** The character selection screen: the menu on the left, the highlighted actor turning on the right, what it can do under it. */
    private void casting(Ui ui) {
        float W = ui.w, H = ui.h;
        ui.b.rect(0, 0, W, H, 0xFF08090A);
        for (int i = 0; i < 9; i++) ui.b.rect(0, H * i / 9f, W, H / 9f + 1, Ui.alpha(Ui.mix(0xFF14100E, 0xFF08090A, i / 8f), 1));
        ui.b.rect(0, 0, W, ui.s(4), 0xFFB03A2E);
        ui.text("DIRECTOR MODE", ui.s(40), ui.s(34), 34, Ui.TXT_HI);
        ui.text("Character selection", ui.s(42), ui.s(78), 14, Ui.AMBER);
        gui.draw(ui, ui.s(40), ui.s(118), ui.s(470), "CASTING");
        // ---- the preview
        float px = ui.s(560), pw = W - px - ui.s(40), ph = H - ui.s(240), py = ui.s(100);
        ui.b.rect(px, py, pw, ph, 0x60000000);
        ui.b.outline(px, py, pw, ph, 1, 0xFF3A3630);
        ActorManager.Def d = actors.def(previewSpec.def);
        if (previewLook != null && d != null) {
            float size = Math.min(pw * 0.62f, ph * 0.92f);
            try { screen.ctx().icons.drawModel(ui.b, screen.ctx().ar.flatten(previewLook), px + (pw - size) / 2, py + (ph - size) / 2 + ui.s(8), size, (float) Math.PI + (float) Math.sin(previewSpin) * 0.9f, 0.10f); }
            catch (RuntimeException e) { ui.textC("(no preview for this actor)", px + pw / 2, py + ph / 2, 13, Ui.TXT_DIM); }
            ui.text(d.name.toUpperCase(), px + ui.s(18), py + ui.s(14), 22, Ui.TXT_HI);
            ui.text(d.category + (d.variants > 1 ? "   -   look " + (Math.floorMod(previewSpec.variant, d.variants) + 1) + " of " + d.variants + "  [Left/Right]" : ""), px + ui.s(18), py + ui.s(44), 12, Ui.AMBER);
            if (persistence.shortlisted(previewSpec)) ui.textR("SHORTLISTED", px + pw - ui.s(18), py + ui.s(18), 12, Ui.GREEN);
            absentialis.ui.Layout.wrapC(ui, actors.capabilities(d), px + ui.s(30), py + ph - ui.s(48), pw - ui.s(60), 12, Ui.TXT);
        }
        float sy = py + ph + ui.s(16);
        ui.text("SELECTED", px, sy, 11, Ui.TXT_DIM);
        ui.text(actors.describe(chosen), px + ui.s(90), sy - ui.s(2), 15, Ui.TXT_HI);
        ui.text("SHORTLIST  " + persistence.shortlist.size() + " / " + PersistenceManager.SHORTLIST_MAX, px, sy + ui.s(26), 11, Ui.TXT_DIM);
        StringBuilder sb = new StringBuilder();
        for (ActorManager.Spec s : persistence.shortlist) { ActorManager.Def sd = actors.def(s.def); sb.append(sb.length() > 0 ? "   " : "").append(sd == null ? "?" : sd.name); }
        absentialis.ui.Layout.fit(ui, sb.toString(), px + ui.s(120), sy + ui.s(26), pw - ui.s(120), 11, 9, Ui.TXT);
        if (!persistence.problem.isEmpty()) ui.text(persistence.problem, px, sy + ui.s(48), 11, Ui.RED);
        if (clock - noteAt < 3.5f && !note.isEmpty()) ui.textC(note, W / 2f, H - ui.s(40), 14, Ui.AMBER);
        if (session.curtain > 0.01f) ui.b.rect(0, 0, W, H, Ui.alpha(0xFF000000, Math.min(1, session.curtain)));
    }
}
