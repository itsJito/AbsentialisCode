package absentialis.director;

import absentialis.entity.Actor;
import absentialis.gameplay.inventory.Loadout;
import absentialis.persistence.SaveSystem;
import absentialis.scene.Raid;
import absentialis.scene.RaidScreen;
import java.util.Map;

/**
 * SessionManager: the one place that knows whether Director Mode is running and what state it is in, and the only
 * thing that starts it, moves it from one state to another, and ends it.
 *
 *   Normal gameplay  ->  activate  ->  the original player state is saved  ->  character selection
 *     ->  an actor is chosen  ->  the director world (settings, switching, travel, performances, recording)
 *     ->  exit  ->  the original state is restored  ->  normal gameplay
 *
 * What is saved on the way in and put back on the way out: where the survivor stood and which way they faced, their
 * health, their stance, what they carried (the whole loadout, as data), which view they were in, what they held, and
 * the game's own invulnerability, noclip and AI switches. While a session runs nothing is saved to the profile.
 * Ending a session, in order: stop any recording; close the interfaces; put the survivor and their equipment back;
 * give the camera and the controls back; take off every modifier; restore the clock and the weather; and remove
 * everything the session put in the world.
 */
public final class SessionManager {
    public enum State { OFF, CASTING, ENTERING, ACTIVE, EDITING }

    /** How long the picture is held dark across a transition (input is ignored for the length of it). */
    public static float FADE = 0.45f;

    private final RaidScreen screen;
    private final Director d;
    public State state = State.OFF;
    /** 1 = fully dark, 0 = clear: the curtain drawn across a transition. */
    public float curtain;
    private float enterT;
    // ---- the original player state
    private float x, y, z, yaw, pitch, hp, maxHp;
    private boolean third, crouch, prone, savesWere;
    private String held = "primary";
    private Map<String, Object> loadoutJson;
    public Loadout original;
    public int sessions;

    SessionManager(RaidScreen screen, Director d) { this.screen = screen; this.d = d; }

    public boolean active() { return state != State.OFF; }

    /** Activates Director Mode: saves the original player state and opens character selection. Null if it began, otherwise why not. */
    public String begin() {
        Raid r = screen.raid;
        Actor p = r.player;
        if (state != State.OFF) return "Director Mode is already running";
        if (p.dead) return "Not while dead";
        if (r.vehicles.inside() || r.vehicles.busy()) return "Get out of the vehicle first";
        if (screen.dev.active) screen.dev.toggle();                 // Developer Mode hands over: the survivor is put back before they are borrowed
        x = p.x; y = p.y; z = p.z; yaw = p.yaw; pitch = p.pitch; hp = p.hp; maxHp = p.maxHp; crouch = p.crouch; prone = p.prone;
        third = screen.thirdPersonView(); held = screen.heldSlotNow();
        loadoutJson = r.profile.loadout.toJson();
        original = r.profile.loadout;
        savesWere = SaveSystem.suspended;
        SaveSystem.suspended = true;
        r.directorSession = true;
        d.world.save(); d.modifiers.save();
        d.persistence.load();
        d.applyPreferences();
        state = State.CASTING;
        curtain = 1;
        sessions++;
        d.openCasting();
        return null;
    }

    /** From character selection into the world as the chosen actor. */
    public String enterWorld() {
        String problem = d.actors.switchTo(d.chosen, Loadout.fromJson(loadoutJson));
        if (problem != null) return problem;
        state = State.ENTERING; enterT = 0; curtain = 1;
        d.gui.close();
        d.gui.lock(FADE);
        return null;
    }

    /** Back to character selection, the world left as it is. */
    public void toCasting() {
        if (d.recording.recording()) d.recording.stop();
        d.camera.setMode(StageManagers.CameraManager.GAMEPLAY);
        screen.camOverride = null;
        state = State.CASTING; curtain = 1;
        d.openCasting();
    }

    public void toEditor() {
        if (d.recording.recording()) d.recording.stop();
        cameFromCasting = state == State.CASTING;
        state = State.EDITING;
        d.gui.close();
        d.editor.open();
    }

    /** 0.9: the editor can be opened from the casting screen as well as from the scene, and goes back to where it was opened. */
    private boolean cameFromCasting;
    public void fromEditor() { if (cameFromCasting) { state = State.CASTING; curtain = 1; d.openCasting(); return; } state = State.ACTIVE; curtain = 1; enterT = 0; state = State.ENTERING; }

    void update(float dt) {
        if (state == State.ENTERING) { enterT += dt; curtain = Math.max(0, 1 - enterT / FADE); if (enterT >= FADE) { state = State.ACTIVE; curtain = 0; } }
        else if (state == State.CASTING) curtain = Math.max(0, curtain - dt / FADE);
    }

    /** Ends the session and puts everything back. */
    public void end() {
        if (state == State.OFF) return;
        Raid r = screen.raid;
        Actor p = r.player;
        if (d.recording.recording()) d.recording.stop();                        // stop active actions and recordings
        d.gui.close();                                                            // close Director Mode interfaces
        RecordingManager.TAPES.clear(); SceneTape.forgetAll();                    // the takes' tapes belong to the session (0.9)
        screen.ctx().ar.capture = null; screen.ctx().ar.replay = null;
        d.savePreferences();
        if (r.vehicles.inside()) r.vehicles.exit();
        d.actors.restore();                                                       // restore the original character...
        r.profile.loadout = Loadout.fromJson(loadoutJson);                        // ...and equipment
        r.applyArmor();
        d.camera.restore();                                                       // restore camera...
        screen.thirdPersonView(third);
        screen.scenarioHold(held);                                                // ...and gameplay controls
        screen.hudHidden = false;
        d.modifiers.restore();                                                    // remove temporary modifiers
        d.world.restore();                                                        // restore environmental settings
        d.population.restore();                                                   // clean up temporary entities...
        d.vehicles.restore();
        d.clearStaged();
        p.x = x; p.y = y; p.z = z; p.yaw = yaw; p.pitch = pitch; p.vx = p.vy = p.vz = 0;
        p.maxHp = maxHp; p.hp = Math.min(maxHp, hp); p.crouch = crouch; p.prone = prone; p.dead = false;
        r.directorSession = false;
        SaveSystem.suspended = savesWere;
        state = State.OFF;
        curtain = 0;
    }
}
