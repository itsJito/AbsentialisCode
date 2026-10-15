package absentialis.scene;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import absentialis.character.Animator;
import absentialis.character.Anims;
import absentialis.character.Pose;
import absentialis.character.Rig;
import absentialis.engine.Input;
import absentialis.engine.math.Mat4;
import absentialis.engine.render.WorldRenderer;
import absentialis.engine.voxel.VoxModel;
import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.gameplay.inventory.Container;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.inventory.Loadout;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.gameplay.weapons.Gunsmith;
import absentialis.gameplay.weapons.WeaponService;
import absentialis.ui.InventoryUi;
import absentialis.ui.ItemModels;
import absentialis.ui.Ui;
import absentialis.world.Block;
import absentialis.world.District;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.List;

/**
 * The raid as the player experiences it: input -> player body and weapon, camera, world and
 * model rendering, HUD and the in-raid overlays (inventory split view, weapon wheel, map,
 * pause). Simulation authority stays in {@link Raid}.
 */
public final class RaidScreen {
    final Ctx c;
    public final Raid raid;
    final Hud hud;
    private final WeaponWheel wheel;
    private final LoadoutPanel panel;
    private String heldSlot = "primary";
    // camera & feel
    private float bobAmt, lean, adsT, recoilPitch, recoilYaw, kick, switchT, shake, swayX, swayY, landDip, lastStepPhase, extractTick, stepGate;
    private ActorRenderer.Look playerLook;
    private final float[] eye = new float[3];
    private final Pose vmPose = new Pose(), vmBase = new Pose();
    /** Floor lookup for foot placement: the top of the first walkable block around the given height. */
    private final Animator.Ground ground = this::groundTop;

    private float groundTop(float x, float z, float y) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        for (int by = (int) Math.floor(y + 1.2f); by >= (int) Math.floor(y - 1.3f); by--) if (raid.world.solid(ix, by, iz) && !raid.world.solid(ix, by + 1, iz)) return by + 1;
        return Float.NaN;
    }
    public boolean thirdPerson;
    private final Mat4 proj = new Mat4(), view = new Mat4(), pv = new Mat4(), tmp = new Mat4();
    private float eyeX, eyeY, eyeZ, tpsDistance = 3.2f;
    // weapon timers
    private float fireTimer, reloadT, reloadMax, cycleT, lastShot = -9, lastRTime = -9, meleeT;
    private int shotIndex, burstLeft;
    private String reloadKind;
    private ItemInstance reloadMag;
    private boolean crouchToggle;
    /**
     * 0.7 input state. fireGate: the fire button was down when a menu closed, so it does nothing until it has been let go
     * (resuming from pause with a click no longer fires the weapon). The crouch key: how long it has been held, whether
     * this press has already done its work, and whether crouch is being ignored because sprint took over.
     */
    private boolean fireGate, crouchUsed, crouchSuppressed;
    private float crouchHoldT;
    /** Seconds the crouch key is held to go prone, and how fast a prone body crawls (m/s). */
    public static final float PRONE_HOLD = 0.42f, PRONE_SPEED = 1.15f;
    /** The throwable in hand (0.7: grenades are equipped, aimed and then thrown), the slot to return to, and the wind-up. */
    private ItemInstance heldThrow;
    private String slotBeforeThrow = "primary";
    private float throwCharge, throwFlick;
    private boolean throwAiming;
    /** Spent cases and barrel smoke. */
    public GunFx gunFx;
    /** World position of the lens of the weapon light, when one is fitted and the weapon is drawn (set while drawing the weapon). */
    private final float[] lensWorld = new float[3];
    private boolean lensKnown;
    /** The player's muzzle and ejection port in the world, as last drawn: {mx, my, mz, px, py, pz}. */
    private final float[] playerPorts = new float[6];
    private boolean playerPortsKnown;
    // overlays
    public boolean invOpen, mapOpen, paused, done;
    private Container lootSide;
    private String lootTitle = "";
    private float inspectYaw, inspectPitch = 0.4f, mapZoom = 1;
    private final WorldRenderer.Env env = new WorldRenderer.Env();
    private absentialis.engine.audio.UltraEngine.Voice ambienceVoice;
    private String ambienceName = "";

    /** The 0.5 status interface: character module, squad column, killfeed, hit marker, speech bubbles. */
    public final StatusHud status;
    /** 1.1: the redesigned HUD's layout - the weapon-and-health group, the body's meters, and the scale and margins all of it is drawn at. */
    public final Hud11 hud11;

    public RaidScreen(Ctx c, Raid raid) {
        this.c = c;
        this.raid = raid;
        hud = new Hud(c);
        status = new StatusHud(c, raid);
        hud11 = new Hud11(c, raid, status);
        xpFeed = new XpFeed(raid);
        xpFeed.sound = name -> c.sfx(name, 0.7f);
        wheel = new WeaponWheel(c);
        panel = new LoadoutPanel(c);
        panel.raid = raid;
        raid.listener = new Raid.Listener() {
            public void message(String text, int color) { hud.message(text, color, raid.time); }
            public void caption(String cat, float x, float z, float loud) {
                float ang = (float) Math.atan2(-(x - raid.player.x), -(z - raid.player.z)) - raid.player.yaw;
                hud.caption(cat, (float) Math.toDegrees(-ang), loud, raid.time);
                // gunfire the layered system rendered from its ShotFired event is not played a second time as a one-shot
                if (cat.startsWith("shot") && raid.time == gunHeardAt) return;
                playWorldSound(cat, x, z, loud);
            }
            public void voice(Actor a, String sample) {
                // 1.0: nobody but the survivor has a voice. What people say is still said - in the bubble over their head, and
                // to whoever of the Zone is in earshot - but no recording is played for it.
                if (!a.isPlayer) return;
                if (c.audio == null || !c.audio.available) return;
                // every speaker has their own pitch, so a squad sounds like several people
                float pitch = 0.82f + ((a.id * 37) % 40) / 100f + (a.infected() ? -0.2f : 0);
                c.audio.playAt(sample, a.x, a.y + 1.6f, a.z, 0.85f, 46, pitch, absentialis.engine.audio.UltraEngine.VOICE);
            }
            public void footstep(Actor a, String material, float speed) {
                if (c.audio == null || !c.audio.available) return;
                boolean giant = "tank".equals(a.archetype);
                float gain = Math.min(0.55f, 0.16f + speed * 0.06f) * (giant ? 0.3f : 1f) * (a.crouch ? 0.4f : 1f);
                c.audio.playAt(c.audio.step(material), a.x, a.y + 0.1f, a.z, gain, giant ? 48 : 20, (giant ? 0.6f : a.infected() ? 0.85f : 1f) * (0.92f + raid.rng.nextFloat() * 0.16f));
            }
        };
        acoustics = new WorldAcoustics(raid.world);
        // 1.0: the survivor hears the space they are in - walls, roofs and floors stand between them and everything else
        if (c.audio != null) c.audio.hearing = (hx, hy, hz) -> Hearing.through(raid.world, raid.player.x, raid.player.eyeY(), raid.player.z, hx, hy, hz);
        raid.bus.on(absentialis.engine.events.Events.ShotFired.class, this::gunShot);
        gunFx = new GunFx(raid);
        raid.bus.on(absentialis.engine.events.Events.ShotFired.class, gunFx::onShot);
        gunFx.ports = (a, out) -> { if (!a.isPlayer || !playerPortsKnown) return false; System.arraycopy(playerPorts, 0, out, 0, 6); return true; };
        gunFx.ring = (x, y, z, hull) -> { if (c.audio != null && c.audio.available) c.audio.playAt(hull ? "shell_drop" : "casing_drop", x, y, z, 0.32f, 14, 0.9f + raid.rng.nextFloat() * 0.25f); };
        if (c.audio != null) c.audio.guns.forget();
        raid.weaponMastery = (w, xp) -> c.profile.prog.addMastery(w, xp);
        raid.deathCam = 3.6f;    // the body ragdolls in the world ...
        raid.deathHold = true;   // ... and the raid waits there until the player chooses what happens next
        raid.gore.detail(c.settings.goreDetail);
        if (c.profile.loadout.slot("primary") == null) heldSlot = c.profile.loadout.slot("sidearm") != null ? "sidearm" : "melee";
        c.win.setCursorLocked(true);
        raid.player.weapon = held();
        if (raid.player.anim == null) raid.player.anim = new Animator();
        raid.player.anim.play("equip", 0.45f);
        if (held() != null) c.sfx(equipSound(held()), 0.5f);
        hud.message("Deployed at " + raid.insertion.name + " (" + raid.debrief.deployedIn.display + "). Stay as long as you like; leave by any exit.", Ui.AMBER, 0);
        hud.message("[I] inventory  [Tab] weapon wheel  [M] map  [F] interact  [V] view  [G] grenade  hold [C] prone  [~] console", Ui.TXT_DIM, 0);
    }

    private WorldAcoustics acoustics;
    /** Raid time of the last shot the gun bus rendered, and whether the player's last round was one of them. */
    private float gunHeardAt = -1, roomT;
    private boolean gunLocal;

    /** Level of the one-shot a weapon without a recorded profile fires (the crossbow): one number, used by the game and its gate. */
    public static float fallbackShotGain(absentialis.gameplay.items.ItemDef.Weapon w) { return "special".equals(w.family) ? 0.8f : 0.9f; }

    /** Every round fired in the raid - the player's and everyone else's - becomes a shot event on the gun bus. */
    private void gunShot(absentialis.engine.events.Events.ShotFired e) {
        if (c.audio == null || !c.audio.available) return;
        boolean local = e.shooterId() == raid.player.id;
        if (local) gunLocal = false;
        var a = c.audio;
        // the listener is where the camera's owner stands: the engine's copy is a frame old at most
        var ev = a.guns.shot(acoustics, e.weaponId(), e.shooterId(), e.rpm(), e.x(), e.y(), e.z(), e.dirX(), e.dirZ(), e.suppressed(), local, raid.player.x, raid.player.eyeY(), raid.player.z, raid.player.yaw);
        gunHeardAt = ev == null ? -1 : raid.time;
        if (ev == null) return;
        if (local) gunLocal = true;
    }

    private void playWorldSound(String cat, float x, float z, float loud) {
        if (c.audio == null || !c.audio.available) return;
        String s = switch (cat) {
            case "shot" -> loud < 0.3f ? "shot_distant" : "shot_rifle"; case "shot_suppressed" -> "shot_suppressed"; case "explosion" -> loud < 0.3f ? "explosion_far" : "explosion";
            case "bolt_release" -> "crossbow_shot"; case "glass_tinkle" -> "glass_tinkle";
            case "glass_break" -> "glass_break"; case "groan" -> "groan"; case "screech" -> "screech"; case "shriek" -> "shriek"; case "gurgle" -> "gurgle"; case "retch" -> "retch"; case "roar" -> "roar";
            case "splash" -> "splash"; case "stomp" -> "stomp"; case "shout" -> "shout"; case "whistle" -> "whistle"; case "radio_chatter" -> "radio"; case "search" -> "search";
            case "door_open" -> "door_open"; case "door_close" -> "door_close"; case "door_locked" -> "door_locked"; case "door_kick" -> "door_kick"; case "door_break" -> "door_break"; case "impact" -> null /* 0.8: each strike is a cue of its own, by what was struck (Raid.cue) */; case "melee" -> "melee_hit"; case "alarm_clock" -> "beep";
            case "flare" -> "flare"; case "reload" -> "mag_in"; case "bounce" -> null; case "debris" -> "impact_wood";
            case "bite" -> "bite"; case "cough" -> "cough"; case "infected_turn" -> "infected_turn"; case "antiviral" -> "antiviral"; case "gore" -> "flesh_tear";
            case "slip" -> "slip"; case "fire_burst" -> "fire_burst"; case "flashbang" -> "flashbang"; case "pin" -> "pin"; case "geiger" -> "geiger"; case "radio" -> "radio";
            case "bone" -> "bone_snap"; case "body_fall" -> "body_fall"; case "stagger" -> "stagger"; case "bullet_snap" -> "bullet_snap"; case "block" -> "melee_block"; case "grab" -> "grab";
            case "engine_start" -> "engine_start"; case "suspension" -> "suspension"; case "gear_shift" -> "gear_shift";
            case "car_crash" -> "car_crash"; case "car_bump" -> "car_bump"; case "tyre_burst" -> "tyre_burst"; case "car_door" -> "car_door"; case "car_horn" -> "car_horn";
            // 0.7: voices that are recordings now, and the sounds of what 0.7 added
            case "growl" -> "growl"; case "feed" -> "inf_feed"; case "infected_death" -> "inf_death"; case "pain" -> "hurt"; case "death_cry" -> "death";
            case "gladiator_roar" -> "gladiator_roar"; case "chainsaw" -> null /* 0.9: the engine is a loop that follows the Butcher (sawAudio) */; case "chainsaw_rev" -> "chainsaw_rev"; case "chainsaw_stall" -> "chainsaw_stall";
            case "claw_scrape" -> "claw_scrape"; case "claw_swing" -> "claw_swing"; case "hammer" -> "hammer"; case "ricochet" -> "ricochet";
            default -> null;
        };
        // 1.0: human voices other than the survivor's own are not played (cries of pain, death, shouts, whistles, radio talk, a cough)
        boolean human = cat.equals("pain") || cat.equals("death_cry") || cat.equals("shout") || cat.equals("whistle") || cat.equals("radio_chatter") || cat.equals("cough");
        if (human && raid.player.distXZ(x, z) > 0.6f) return;
        // a sound with several takes is never the same take twice running
        if (s != null) s = c.audio.variant(s, raid.rng.nextInt(64));
        boolean voice = cat.equals("growl") || cat.equals("feed") || cat.equals("infected_death") || cat.equals("pain") || cat.equals("death_cry") || cat.equals("gladiator_roar") || cat.equals("shout") || cat.equals("groan") || cat.equals("screech") || cat.equals("shriek") || cat.equals("roar") || cat.equals("retch") || cat.equals("gurgle") || cat.equals("radio_chatter") || cat.equals("whistle");
        // 1.0: how far a kind of sound carries at all (it used to be 300 m for every one of them)
        float range = switch (cat) {
            case "explosion" -> 260f; case "shot", "shot_suppressed", "flashbang" -> 170f; case "car_crash", "car_horn", "roar", "gladiator_roar", "chainsaw_rev" -> 90f;
            case "shout", "shriek", "screech", "whistle", "glass_break", "door_break", "door_kick", "engine_start", "tyre_burst", "stomp" -> 60f;
            case "groan", "growl", "gurgle", "retch", "pain", "death_cry", "infected_death", "radio_chatter", "radio", "search", "feed", "body_fall", "melee", "block" -> 34f;
            default -> 26f;
        };
        if (s != null) c.audio.playAt(s, x, raid.player.y + 1, z, 0.9f, range, voice ? 0.94f + raid.rng.nextFloat() * 0.12f : 1, voice ? absentialis.engine.audio.UltraEngine.VOICE : absentialis.engine.audio.UltraEngine.SFX);
    }

    private ItemInstance held() { return "throw".equals(heldSlot) ? null : c.profile.loadout.slot(heldSlot); }

    /** The throwable that is in the hand right now (the same kind is drawn from the case again when one has gone), or null. */
    private ItemInstance currentThrow() {
        if (!"throw".equals(heldSlot)) return null;
        if (heldThrow != null && heldThrow.container == lo().caseGrid && heldThrow.qty > 0) return heldThrow;
        String want = heldThrow == null ? null : heldThrow.defId;
        heldThrow = null;
        for (ItemInstance i : lo().caseGrid.items) if (i.def().throwable != null && (want == null || i.defId.equals(want))) { heldThrow = i; break; }
        return heldThrow;
    }

    private List<ItemInstance> throwables() { List<ItemInstance> l = new ArrayList<>(); for (ItemInstance i : lo().caseGrid.items) if (i.def().throwable != null) l.add(i); return l; }

    /** True while a throwable is in hand and being aimed (the arc is showing). */
    public boolean throwAiming() { return throwAiming; }
    public String heldSlot() { return heldSlot; }

    /**
     * [G], or the wheel: takes a throwable in hand. Pressed again it takes the next kind, and with only one kind it puts
     * it away. Nothing is thrown by this: the grenade is in the hand, and the fire button aims and throws it.
     */
    private void equipThrowable(ItemInstance pick) {
        List<ItemInstance> all = throwables();
        if (all.isEmpty()) { hud.message("Nothing to throw", Ui.TXT_DIM, raid.time); return; }
        if ("throw".equals(heldSlot) && pick == null) {
            ItemInstance cur = currentThrow(), next = null;
            boolean seen = false;
            for (ItemInstance i : all) { if (cur != null && i.defId.equals(cur.defId)) { seen = true; continue; } if (seen) { next = i; break; } }
            if (next == null) { stowThrowable(); return; }
            pick = next;
        }
        if (pick == null) pick = all.get(0);
        if (!"throw".equals(heldSlot)) slotBeforeThrow = heldSlot;
        cancelReload();
        heldSlot = "throw"; heldThrow = pick;
        throwAiming = false; throwCharge = 0;
        switchT = 0.3f; adsT = 0;
        raid.player.weapon = null;
        raid.player.anim.play("equip", 0.3f);
        c.sfx("pickup", 0.4f);
        hud.message(pick.def().name + " in hand  -  hold [fire] to aim, release to throw, [aim] cancels", Ui.TXT, raid.time);
    }

    /** Puts the throwable away and brings back what was in hand before it. */
    private void stowThrowable() {
        if (!"throw".equals(heldSlot)) return;
        throwAiming = false; throwCharge = 0; heldThrow = null;
        String back = lo().slot(slotBeforeThrow) != null ? slotBeforeThrow : lo().slot("primary") != null ? "primary" : lo().slot("sidearm") != null ? "sidearm" : "melee";
        heldSlot = "";
        if (lo().slot(back) != null) switchTo(back); else { heldSlot = "none"; raid.player.weapon = null; }
    }

    /** Fire button with a throwable in hand: held, it aims (the arc shows where it will land); released, it throws. */
    private void throwControl(Input in, float dt) {
        ItemInstance g = currentThrow();
        if (g == null) { stowThrowable(); return; }
        boolean down = in.down("fire");
        if (fireGate) { if (!down) fireGate = false; down = false; }
        if (switchT > 0 || raid.body.using != null || raid.player.revivingId != 0 || raid.player.downed()) { throwAiming = false; throwCharge = 0; return; }
        if (down) {
            if (in.pressed("aim")) { throwAiming = false; throwCharge = 0; fireGate = true; hud.message("Throw cancelled", Ui.TXT_DIM, raid.time); return; }
            throwAiming = true;
            throwCharge = Math.min(1, throwCharge + dt / 0.55f);
            raid.playerVulnerableUntil = raid.time + 0.2f;
        } else if (throwAiming) {
            throwAiming = false;
            throwIt(g, throwPower());
            throwCharge = 0; throwFlick = 0.35f;
            if (currentThrow() == null) stowThrowable();
        }
    }

    /** How hard the throw in hand would go right now: a quick flick is a short lob, a full wind-up is the full throw. */
    private float throwPower() { return 0.62f + 0.38f * throwCharge; }
    private Loadout lo() { return c.profile.loadout; }
    /** Scripted visual verification: opens the barter panel with the nearest living person, brought to arm's length. */
    public void debugBarter() {
        Actor best = null;
        for (Actor a : raid.actors) if (!a.isPlayer && !a.dead && !a.infected() && !a.mate() && a.ragdoll == null && (best == null || a.dist(raid.player) < best.dist(raid.player))) best = a;
        if (best == null) return;
        best.hostileToPlayer = false;
        best.x = raid.player.x - (float) Math.sin(raid.player.yaw) * 1.6f; best.z = raid.player.z - (float) Math.cos(raid.player.yaw) * 1.6f; best.y = raid.player.y;
        trade.open(raid, lo(), best);
    }

    /** Barter with the people you meet, and passing kit to and from the squad (0.6). */
    public final BarterPanel trade = new BarterPanel();

    private boolean overlay() { return invOpen || mapOpen || paused || c.console.open || trade.open || dev.panel || popup.open; }
    /** 1.0: a decision being put to the survivor (an advisor, an encounter). The operation stands still while it is open. */
    public final ChoicePopup popup = new ChoicePopup();
    public Encounters encounters;
    /** 0.8: Developer Mode - the player character gone, a camera and a workbench in its place (console: devmode). */
    public final DevMode dev = new DevMode(this);
    // ---- 0.8, Director Mode: what the session needs of the screen. It owns none of this; it asks.
    /** The session, once one has been started (console: directormode). */
    public absentialis.director.Director director;
    /** When set, the controlled character is drawn as this instead of as the survivor. */
    public ActorRenderer.Look lookOverride;
    /** When set, the picture is taken from here instead of from the character: {x, y, z, yaw, pitch, field of view in degrees (0 = as it is)}. */
    public float[] camOverride;
    /** 1.3: the world's containers and boarded windows, drawn as objects. */
    private BlockObjects blockObjects;
    private absentialis.world.World blockObjectsWorld;
    /** True while the game's own HUD is not to be drawn (the session draws what it needs). */
    public boolean hudHidden;
    /** 0.9: the picture and nothing else - no HUD, no hands, no weapon (console: photo). */
    public boolean photo;
    public Ctx ctx() { return c; }
    public boolean thirdPersonView() { return thirdPerson; }
    public void thirdPersonView(boolean on) { thirdPerson = on; }
    public String heldSlotNow() { return heldSlot; }
    private boolean dirMenu, camDetached;
    private ActorRenderer.Look lastLookOverride;
    /** Scripted scenarios hold the sights up with this (tools; nothing in play sets it). */
    public boolean scenarioAim;

    // ================================================================== update
    public void update(float dt) {
        Input in = c.in;
        // an operation that ends while a Director Mode session is running ends the session first: the survivor leaves as themselves
        if (raid.debrief.outcome != Raid.Debrief.Outcome.NONE && director != null && director.active()) director.session.end();
        if (raid.debrief.outcome != Raid.Debrief.Outcome.NONE && dev.active) dev.toggle();
        if (raid.debrief.outcome != Raid.Debrief.Outcome.NONE) { done = true; return; }
        boolean consoleWas = c.console.open;
        if (in.keyPressed(GLFW_KEY_GRAVE_ACCENT)) c.console.toggle();
        if (c.console.input(in)) { c.win.setCursorLocked(false); if (!consoleWas) in.typed.clear(); raid.update(dt * 0.0f); return; }
        // 0.8: Director Mode. Its casting screen and its editor have the screen to themselves and the world waits; its
        // interaction menu is laid over a world that carries on, and takes the keys while it is open.
        dirMenu = false;
        if (director != null && director.active()) {
            if (director.fullScreen()) { director.update(in, dt); c.win.setCursorLocked(!director.wantsPointer()); invOpen = false; mapOpen = false; paused = false; fireGate = true; return; }
            dirMenu = director.update(in, dt);
            if (dirMenu) { fireGate = true; invOpen = false; mapOpen = false; }
        }
        // 0.8: Developer Mode has the screen to itself - no inventory, no map, no weapon; a camera and its workbench
        if (dev.active) {
            if (in.keyPressed(GLFW_KEY_ESCAPE) && !dev.panel) { paused = !paused; }
            boolean typing = !paused && dev.update(in, dt);
            c.win.setCursorLocked(!dev.panel && !paused);
            invOpen = false; mapOpen = false; wheel.open = false; fireGate = true;
            if (paused) return;
            if (!typing) look(in);
            if (!dev.panel && in.keyPressed(GLFW_KEY_F7)) { raid.freezeAI = !raid.freezeAI; }
            raid.playerAiming = false; adsT = 0; adsK = 0;
            raid.update(dt);
            c.audio.lx = raid.player.x; c.audio.ly = raid.player.eyeY(); c.audio.lz = raid.player.z; c.audio.lyaw = raid.player.yaw;
            for (int i = 0; i < raid.cues.size(); i++) { Raid.Cue q = raid.cues.get(i); if (c.audio.available) c.audio.playAt(q.sample(), q.x(), q.y(), q.z(), q.gain(), q.range(), 1f); }
            raid.cues.clear();
            return;
        }
        // overlays
        if (in.keyPressed(GLFW_KEY_ESCAPE) && !dirMenu) {
            if (c.inv.drag != null) c.inv.drag = null;
            else if (popup.open) popup.escape();
            else if (trade.open) trade.close();
            else if (invOpen) { if (c.inv.inspectOpen != null) c.inv.inspectOpen = null; else closeInventory(); }
            else if (mapOpen) mapOpen = false;
            else { paused = !paused; c.sfx(paused ? "pause_menu" : "ui_back", 0.5f); }
        }
        if (trade.open && (trade.broken() || !paused && (in.pressed("interact") || in.pressed("inventory")))) { trade.close(); return; }
        if (!paused && in.pressed("inventory")) { if (invOpen) closeInventory(); else openInventory(null, ""); }
        if (!paused && !invOpen && in.pressed("map")) mapOpen = !mapOpen;
        boolean wheelKey = in.down("wheel") && !overlay();
        if (wheelKey && !wheel.open) wheel.openWheel();
        if (!wheelKey && wheel.open) { wheel.open = false; commitWheel(); }
        // (0.9: Director Mode's menu is worked with the pointer too, so the pointer is let go while it is open)
        c.win.setCursorLocked(!invOpen && !paused && !mapOpen && !trade.open && !popup.open && !(director != null && director.active() && director.wantsPointer()));
        // 0.7: whatever closes a menu, the button that closed it is not a trigger pull. The gate opens when fire is let go.
        if (overlay() || raid.player.dead) fireGate = true;
        if (paused) return;
        if (popup.open) { fireGate = true; return; }                 // 1.0: nothing moves while a question is being answered
        float simDt = wheel.open ? dt * 0.3f : dt; // SP slow-time while choosing
        boolean alive = !raid.player.dead;
        if (!alive) {
            if (!deathStarted) beginDeath();
            invOpen = false; mapOpen = false; wheel.open = false;
            // any key after the first moments skips ahead to the choice
            if (deadT > 2.2f && deadT < DEATH_MENU_AT && (in.lastKeyPressed >= 0 || in.pressed("fire"))) deadT = DEATH_MENU_AT;
            deadT += dt;
            if (deadT >= DEATH_MENU_AT) c.win.setCursorLocked(false);
        }
        if (wheel.open) wheel.update((float) in.dx, (float) in.dy);
        else if (!invOpen && !mapOpen && !trade.open && alive && !dirMenu) look(in);
        boolean inCar = raid.vehicles.inside() || raid.vehicles.busy();
        // 1.1: alive, and Sydney has the body (a fall that went on too long): there is nothing to steer until it is on its feet again
        boolean tumbling = alive && raid.player.ragdoll != null;
        if (tumbling) { adsT = 0; adsK = 0; reloadT = 0; raid.playerAiming = false; fireGate = true; raid.player.sprinting = false; raid.vehicles.keys(false, false, false, false, true); }
        else if (inCar && alive && !invOpen && !mapOpen) { driveControls(in); squadKeys(in); }
        else if (!invOpen && !mapOpen && !trade.open && alive && !dirMenu) { controls(in, simDt); squadKeys(in); }
        else { raid.player.vx *= 0.8f; raid.player.vz *= 0.8f; raid.vehicles.keys(false, false, false, false, true); }
        if (alive && !inCar && !tumbling) movePlayer(simDt);
        // the moments an opponent waits for: reloading, treating a wound, the recovery of a missed swing, a slip
        if (alive && (reloadT > 0 || raid.body.using != null || meleeT > 0.25f || raid.slipT > 0)) raid.playerVulnerableUntil = raid.time + 0.25f;
        raid.update(simDt);
        if (encounters == null) encounters = new Encounters(raid);
        if (alive && !raid.world.dev) encounters.update(simDt, popup);
        gunFx.update(simDt);
        c.ar.clock = raid.time;
        throwFlick = Math.max(0, throwFlick - simDt);
        sprayMist(simDt);
        footfalls();
        music(dt);
        weaponTimers(simDt);
        useTimer(simDt);
        // extraction countdown: one tick per second, rising toward departure
        if (raid.extracting != null && raid.extractProgress > 0) {
            if ((int) raid.extractProgress != (int) extractTick) c.sfx("extract_tick", 0.5f, 0.8f + raid.extractProgress * 0.06f);
            extractTick = raid.extractProgress;
        } else extractTick = 0;
        if (invOpen && inspectArmed && c.inv.inspected != null) { inspectYaw += (float) in.dx * 0.01f; inspectPitch = Math.max(-1.2f, Math.min(1.2f, inspectPitch + (float) in.dy * 0.01f)); }
        if (mapOpen) mapZoom = Math.max(0.6f, Math.min(4f, mapZoom * (1 + (float) in.scroll * 0.12f)));
        // audio listener + ambience bed per district
        if (c.audio != null) {
            c.audio.lx = raid.player.x; c.audio.ly = raid.player.eyeY(); c.audio.lz = raid.player.z; c.audio.lyaw = raid.player.yaw;
            // the room the shared reverb models follows the listener, a few times a second
            roomT -= dt;
            if (roomT <= 0) { roomT = 0.2f; c.audio.guns.mixer.environment.listenerRoom(acoustics, raid.player.x, raid.player.eyeY(), raid.player.z); }
            // 0.8: every district has a bed of its own (the recordings in the sound pack). Underground there is none; rain
            // is laid over the district rather than replacing it.
            String bed = raid.player.y < World.GROUND - 3 ? null : ambienceFor(raid.world.district(raid.player.x, raid.player.z));
            String amb = bed == null ? "" : "amb_" + bed;
            if (!amb.equals(ambienceName)) {
                ambienceHold += dt;
                // a border is not a switch: the bed changes once the survivor has been across it for a moment
                if (ambienceHold > (ambienceName.isEmpty() ? 0 : 2.5f)) {
                    if (ambienceVoice != null) ambienceVoice.fadeTo(0, 4);
                    ambienceVoice = amb.isEmpty() ? null : c.audio.loopStream(amb, 0.55f, absentialis.engine.audio.UltraEngine.AMBIENCE);
                    ambienceName = amb; ambienceHold = 0;
                }
            } else ambienceHold = 0;
            // out of breath, or badly hurt: the survivor is heard breathing (their own voice, every breath a little different)
            breathT -= dt;
            float wind = 1 - raid.body.stamina / 100f, hurtK = 1 - raid.player.hp / Math.max(1f, raid.player.maxHp);
            if (!raid.player.dead && breathT <= 0 && (wind > 0.72f || hurtK > 0.7f)) {
                breathT = 1.25f - 0.35f * Math.max(wind, hurtK) + raid.rng.nextFloat() * 0.15f;
                c.audio.play(raid.playerVoice("breath"), 0.22f + 0.2f * Math.max(wind, hurtK), 0, 0.95f + raid.rng.nextFloat() * 0.1f, absentialis.engine.audio.UltraEngine.VOICE);
            }
            // 1.0: the rain is heard as hard as it is falling (less of it under a roof), and thunder when the weather says it has arrived
            boolean raining = raid.weather.rain > 0.06f && raid.player.y >= World.GROUND - 3;
            if (raining && rainVoice == null) rainVoice = c.audio.loop("amb_rain", 0.05f, true);
            if (raining && rainVoice != null) rainVoice.fadeTo((0.18f + 0.5f * raid.weather.rain) * (1 - 0.55f * indoorT), 0.5f);
            if (!raining && rainVoice != null) { rainVoice.fadeTo(0, 3); rainVoice = null; }
            if (raid.weather.listener == null) raid.weather.listener = (dist, gain) -> { if (c.audio != null && c.audio.available) c.audio.play("thunder", gain * (1 - 0.35f * indoorT), 0, 0.85f + raid.rng.nextFloat() * 0.25f); };
            // 0.8, the investigations: a lead picked up or a piece of evidence seen is said once, with its sound
            if (raid.chains != null && !raid.chains.notices.isEmpty()) { String[] n = raid.chains.notices.remove(0); hud.message(n[0], Ui.AMBER, raid.time); c.sfx(n[1], 0.65f); }
            if (raid.story != null && !raid.story.notices.isEmpty()) {
                String[] n = raid.story.notices.remove(0);
                hud.message(n[0], Ui.AMBER, raid.time);
                c.sfx(n[1], 0.65f);
            }
            // the sounds that are only sounds: what was struck, who cried out
            for (int i = 0; i < raid.cues.size(); i++) {
                Raid.Cue q = raid.cues.get(i);
                if (c.audio.available) c.audio.playAt(q.sample(), q.x(), q.y(), q.z(), q.gain(), q.range(), q.voice() ? 1f : 0.95f + raid.rng.nextFloat() * 0.1f, q.voice() ? absentialis.engine.audio.UltraEngine.VOICE : absentialis.engine.audio.UltraEngine.SFX);
            }
            raid.cues.clear();
        }
    }

    /** At the wheel: the movement keys drive, [Space] is the handbrake, [F] gets out. Weapons stay down. */
    private void driveControls(Input in) {
        boolean ctl = !overlay() && raid.vehicles.driving != null;
        raid.vehicles.keys(ctl && in.down("forward"), ctl && in.down("back"), ctl && in.down("left"), ctl && in.down("right"), ctl && in.down("jump"));
        raid.playerAiming = false;
        adsT = 0;
        if (!ctl) return;
        // 0.9: in Director Mode the scene is played from one view; another perspective is a decision for the editor
        if (in.pressed("view")) { if (director != null && director.viewLocked()) hud.message("Perspective is chosen in the editor: record the scene, then [F6]", Ui.TXT_DIM, raid.time); else thirdPerson = !thirdPerson; }
        if (in.pressed("interact") && raid.player.ragdoll == null) { String r = raid.vehicles.exit(); if (r != null) { hud.message(r, Ui.AMBER, raid.time); c.sfx("ui_invalid", 0.4f); } else c.sfx("car_door_open", 0.5f); }
        if (in.pressed("flashlight")) { raid.vehicles.toggleLights(); c.sfx("ui_click", 0.3f); }
        if (in.keyPressed(GLFW_KEY_H)) raid.vehicles.horn();
        if (in.keyPressed(GLFW_KEY_J)) raid.vehicles.toggleSiren();
    }

    private void vehicleHud(Ui ui) {
        raid.vehicles.hud(ui);
        if (c.settings.vehicleDebug) raid.vehicles.debugText(ui);
    }

    private float deadT;
    /** Team the next order goes to: gold (everyone), red or blue. */
    public String squadSel = "gold";

    /** Where the leader is pointing: the first surface under the crosshair, or a spot ahead on the ground. */
    private float[] aimPoint() {
        Actor p = raid.player;
        float dx = (float) (-Math.sin(p.yaw) * Math.cos(p.pitch)), dy = (float) Math.sin(p.pitch), dz = (float) (-Math.cos(p.yaw) * Math.cos(p.pitch));
        // third person: from the camera, along what the crosshair is on (the eye is the head's, and the head is off to one side of the picture)
        float ox = camThird ? camPX : eyeX, oy = camThird ? camPY : eyeY, oz = camThird ? camPZ : eyeZ;
        int[] hit = Physics.ray(raid.world, ox, oy, oz, dx, dy, dz, 70, false);
        if (hit != null) return new float[]{hit[0] + 0.5f - dx * 1.2f, hit[2] + 0.5f - dz * 1.2f, hit[1] + (dy < -0.05f ? 1 : 0)};
        return new float[]{p.x + dx * 18, p.z + dz * 18, p.y};
    }

    /**
     * Squad command keys. [T] picks the team an order goes to (Gold = everyone deployed, Red, Blue). [F1] follow,
     * [F2] hold, [F3] move to the crosshair, [F4] sweep the crosshair. Hold [Left Alt] to queue the order (1.1: Left Ctrl is the stance key)
     * instead of issuing it; [F5] is Gold's Initiate, which releases every queued order at once. [F6] moves the
     * squadmate in front of you to the other team. [F7] tells the commanded team to hold fire, or to fire at will again.
     */
    private void squadKeys(Input in) {
        if (raid.mates.isEmpty()) return;
        if (in.keyPressed(GLFW_KEY_T)) { squadSel = squadSel.equals("gold") ? "red" : squadSel.equals("red") ? "blue" : "gold"; c.sfx("ui_tab", 0.4f); hud.message("Commanding " + squadSel.toUpperCase() + " team (" + raid.team(squadSel).size() + ")", teamColor(squadSel), raid.time); }
        boolean queue = in.keyDown(GLFW_KEY_LEFT_ALT);
        String order = in.keyPressed(GLFW_KEY_F1) ? "follow" : in.keyPressed(GLFW_KEY_F2) ? "hold" : in.keyPressed(GLFW_KEY_F3) ? "move" : in.keyPressed(GLFW_KEY_F4) ? "sweep" : null;
        if (order != null) {
            raid.squadTasks.cancel(squadSel);
            float[] at = order.equals("move") || order.equals("sweep") ? aimPoint() : new float[]{Float.NaN, Float.NaN, Float.NaN};
            raid.orderHintY = at[2];
            int n = raid.squadOrder(squadSel, order, at[0], at[1], queue);
            hud.message(squadSel.toUpperCase() + ": " + order.toUpperCase() + (queue ? " queued - [F5] to initiate" : "") + (n == 0 ? " (nobody on that team)" : ""), teamColor(squadSel), raid.time);
            c.sfx("radio", 0.35f);
            raid.player.anim.play("interact", 0.5f);
        }
        // 1.0, at a door: [F8] the team opens it and clears the room behind it; [F9] they clear it and then take the house
        if (in.keyPressed(GLFW_KEY_F8) || in.keyPressed(GLFW_KEY_F9)) {
            boolean loot = in.keyPressed(GLFW_KEY_F9);
            Actor p = raid.player;
            float dx = (float) (-Math.sin(p.yaw) * Math.cos(p.pitch)), dy = (float) Math.sin(p.pitch), dz = (float) (-Math.cos(p.yaw) * Math.cos(p.pitch));
            int[] hit = Physics.ray(raid.world, eyeX, eyeY, eyeZ, dx, dy, dz, 14f, true);
            Block hb = hit == null ? null : raid.world.block(hit[0], hit[1], hit[2]);
            if (hb == null || !hb.is(Block.DOOR)) hud.message("Look at a door to send " + squadSel.toUpperCase() + " team through it", Ui.TXT_DIM, raid.time);
            else {
                int by = hit[1];
                while (by > 0 && raid.world.block(hit[0], by - 1, hit[2]) != null && raid.world.block(hit[0], by - 1, hit[2]).is(Block.DOOR)) by--;
                int n = raid.squadTasks.order(squadSel, hit[0], by, hit[2], dx, dz, loot);
                hud.message(squadSel.toUpperCase() + (loot ? ": CLEAR AND LOOT THE HOUSE" : ": OPEN AND CLEAR") + (n == 0 ? " (nobody on that team)" : ""), teamColor(squadSel), raid.time);
                c.sfx("radio", 0.35f);
                raid.player.anim.play("interact", 0.5f);
            }
        }
        // 0.8, [F7]: hold fire / fire at will, for the team being commanded
        if (in.keyPressed(GLFW_KEY_F7)) {
            boolean hold = !raid.holdFire(squadSel);
            int n = raid.setHoldFire(squadSel, hold);
            hud.message(squadSel.toUpperCase() + ": " + (hold ? "HOLD FIRE" : "FIRE AT WILL") + (n == 0 ? " (nobody on that team)" : ""), hold ? Ui.AMBER : teamColor(squadSel), raid.time);
            c.sfx("radio", 0.35f);
            raid.player.anim.play("interact", 0.5f);
        }
        if (in.keyPressed(GLFW_KEY_F5)) { int n = raid.initiate(); if (n == 0) hud.message("Nothing queued. Hold [Alt] with an order to queue it.", Ui.TXT_DIM, raid.time); else c.sfx("radio", 0.5f); }
        if (in.keyPressed(GLFW_KEY_F6)) {
            Actor p = raid.player, best = null;
            float fx = (float) -Math.sin(p.yaw), fz = (float) -Math.cos(p.yaw), bc = 0.9f;
            for (Actor m : raid.mates) { float dx = m.x - p.x, dz = m.z - p.z, d = (float) Math.sqrt(dx * dx + dz * dz); if (m.dead || d > 16 || d < 0.1f) continue; float cs = (dx * fx + dz * fz) / d; if (cs > bc) { bc = cs; best = m; } }
            hud.message(best == null ? "Look at a squadmate to move them between Red and Blue" : raid.reassign(best), best == null ? Ui.TXT_DIM : teamColor(best.team), raid.time);
        }
    }

    private static int teamColor(String t) { return t.equals("red") ? SquadPanel.RED_COL : t.equals("blue") ? SquadPanel.BLUE_COL : SquadPanel.GOLD_COL; }

    /** The element at a glance: who is alive, on which team, doing what, and what they have queued. */
    private void drawSquad(Ui ui) {
        if (raid.profile.squad.active().isEmpty()) return;
        float x = ui.s(24), y = ui.h * 0.34f, w = ui.s(216);
        String[] teams = {"gold", "red", "blue"};
        for (int i = 0; i < 3; i++) {
            boolean sel = teams[i].equals(squadSel);
            ui.b.rect(x + i * ui.s(72), y, ui.s(68), ui.s(18), sel ? Ui.alpha(teamColor(teams[i]), 0.85f) : 0x90101010);
            ui.textC(teams[i].toUpperCase() + " " + raid.team(teams[i]).size(), x + i * ui.s(72) + ui.s(34), y + ui.s(2), 11, sel ? 0xFF101010 : teamColor(teams[i]));
        }
        y += ui.s(22);
        for (Actor m : raid.mates) {
            ui.b.rect(x, y, w, ui.s(17), 0x80101010);
            ui.b.rect(x, y, ui.s(4), ui.s(17), teamColor(m.team));
            ui.text(m.name, x + ui.s(9), y + ui.s(1), 11, "engage".equals(m.state) ? Ui.RED : Ui.TXT);
            float hp = Math.max(0, m.hp / m.maxHp);
            ui.b.rect(x + ui.s(82), y + ui.s(6), ui.s(46), ui.s(5), 0xFF1A1C1B);
            ui.b.rect(x + ui.s(82), y + ui.s(6), ui.s(46) * hp, ui.s(5), hp < 0.35f ? Ui.RED : hp < 0.7f ? Ui.AMBER : Ui.GREEN);
            ui.text(("engage".equals(m.state) ? "FIGHTING" : m.sqOrder.toUpperCase()) + (m.qOrder != null ? " > " + m.qOrder.toUpperCase() : ""), x + ui.s(134), y + ui.s(1), 10, m.qOrder != null ? Ui.AMBER : Ui.TXT_DIM);
            y += ui.s(19);
        }
        int down = raid.profile.squad.active().size() - raid.mates.size();
        if (down > 0) { ui.text(down + " down", x + ui.s(9), y, 10, Ui.RED); y += ui.s(15); }
        ui.text("[T] team  [F1] follow  [F2] hold  [F3] move  [F4] sweep", x, y + ui.s(2), 9, Ui.TXT_DIM);
        ui.text("[Ctrl]+order queue  [F5] initiate  [F6] swap team  [F7] hold fire", x, y + ui.s(14), 9, Ui.TXT_DIM);
    }

    private absentialis.engine.audio.UltraEngine.Voice tensionVoice;
    private float tension;

    /** Combat music: fades in while something hostile is actively engaging the player, out again afterwards. */
    private void music(float dt) {
        if (c.audio == null || !c.audio.available) return;
        if (tensionVoice == null) { tensionVoice = c.audio.loop("music_tension", 0f, absentialis.engine.audio.UltraEngine.MUSIC); }
        boolean hot = false;
        for (Actor a : raid.actors) if (!a.isPlayer && !a.dead && "engage".equals(a.state) && a.targetId == raid.player.id && a.distXZ(raid.player.x, raid.player.z) < 70) { hot = true; break; }
        tension += ((hot ? 0.6f : 0f) - tension) * Math.min(1, dt * (hot ? 1.2f : 0.25f));
        tensionVoice.setGain(tension);
    }

    /**
     * 0.7: the districts have no bed of synthesised sound under them any more - not the hiss of "wind" in the Outskirts
     * and the Wilderness, not the Residential district's air, not Downtown's bells, not the hum and the drone. They were
     * noise generators, and they sounded like it. What is heard outdoors now is what is there: steps, voices, the dead,
     * shots, engines, and the rain when it rains.
     */
    /** The bed of a district: its own recording. The wilderness between them has the wind. */
    static String ambienceFor(District d) { return SoundNames.ambience(d); }
    private float ambienceHold, breathT;
    private absentialis.engine.audio.UltraEngine.Voice rainVoice;

    /** 0.9: every living Butcher in earshot has an engine running - a loop that follows it, climbs when it comes on and screams when the saw goes up. */
    private final java.util.Map<Integer, absentialis.engine.audio.UltraEngine.Voice> saws = new java.util.HashMap<>();
    /** Saws that stalled because their Butcher died (tests). */
    public int sawsStalled;

    private void sawAudio(float dt) {
        if (c.audio == null || !c.audio.available) return;
        var au = c.audio;
        for (Actor a : raid.actors) {
            if (!"butcher".equals(a.archetype) || !a.infected()) continue;
            var v = saws.get(a.id);
            boolean running = !a.dead && a.tier == 0 && a.dist(raid.player) < 95;
            if (!running) {
                if (v != null) {
                    // the engine dies with the hand that held the throttle: the note falls away and the saw coughs out
                    v.fadeTo(0, a.dead ? 0.18f : 0.6f);
                    saws.remove(a.id);
                    if (a.dead) { sawsStalled++; au.playAt("chainsaw_stall", a.x, a.y + 1, a.z, 0.8f, 70, 1f); }
                }
                continue;
            }
            if (v == null || v.done()) { v = au.loop("chainsaw_loop", 0f, absentialis.engine.audio.UltraEngine.SFX); saws.put(a.id, v); }
            au.place(v, a.x, a.y + 1.2f, a.z, 0.5f + 0.45f * a.sawRev, 80);
            v.setPitch(0.9f + 0.55f * a.sawRev);
        }
        // a Butcher that has left the world altogether (despawned with its cell) takes its engine with it
        if (!saws.isEmpty()) saws.entrySet().removeIf(e -> { if (raid.actor(e.getKey()) != null) return false; e.getValue().fadeTo(0, 0.4f); return true; });
    }

    public void stopAudio() {
        for (var v : saws.values()) v.fadeTo(0, 0.2f);
        saws.clear();
        if (ambienceVoice != null) ambienceVoice.fadeTo(0, 1);
        if (rainVoice != null) { rainVoice.fadeTo(0, 1); rainVoice = null; }
        if (tensionVoice != null) tensionVoice.fadeTo(0, 1);
        if (deathDrone != null) deathDrone.fadeTo(0, 1.5f);
        raid.fire.clear();
        raid.vehicles.stopAudio();
        if (c.audio != null) { c.audio.duckTo(1f, 1f); c.audio.hearing = null; }
    }

    private void look(Input in) {
        Actor p = raid.player;
        float sens = c.settings.sensitivity * 0.01f * (adsT > 0.5f ? 1f / Math.max(1, zoom()) : 1);
        p.yaw -= (float) in.dx * sens;
        p.pitch = Math.max(-1.5f, Math.min(1.5f, p.pitch - (float) in.dy * sens));
    }

    /**
     * 0.9, over the shoulder: the camera sees round things the muzzle does not. When the sights are up in third person, find
     * what the reticle is on (the camera's line), then what a round from the head toward that point meets first; if that is
     * something else - the corner of the cover the body is behind, a window frame - the HUD marks where the round will
     * stop, so a shot into one's own cover is seen before it is fired. Also hands the HUD how wide the next shot can go.
     */
    private void aimCheck(boolean aiming) {
        hud.aimBlocked = false;
        Actor p = raid.player;
        ItemInstance h = held();
        boolean gun = h != null && h.def().weapon != null && !h.def().weapon.melee();
        if (gun) {
            Gunsmith.Stats st = Gunsmith.stats(h, lo().charm("wear"));
            boolean moving = Math.abs(p.vx) + Math.abs(p.vz) > 0.5f;
            float spread = st.dispersion * (0.35f + (1 - adsT) * 1.6f * st.hipMul) * (moving ? 1 + 0.6f / (adsT > 0.5f ? st.steady : 1) : 1) * (p.crouch ? 0.8f : 1) * (p.onGround ? 1 : 2.5f);
            hud.aimSpread += (spread - hud.aimSpread) * 0.25f;
        } else hud.aimSpread = 0;
        if (!aiming || !gun || p.dead || !camThird) return;
        float pitch = p.pitch, yaw = p.yaw;
        float dx = (float) (-Math.sin(yaw) * Math.cos(pitch)), dy = (float) Math.sin(pitch), dz = (float) (-Math.cos(yaw) * Math.cos(pitch));
        float s0 = tpsDistance * 0.9f + 0.4f, sx = camPX + dx * s0, sy = camPY + dy * s0, sz = camPZ + dz * s0, t = 120;
        int[] hb = Physics.ray(raid.world, sx, sy, sz, dx, dy, dz, t, false);
        if (hb != null) { float bx = hb[0] + 0.5f - sx, by = hb[1] + 0.5f - sy, bz = hb[2] + 0.5f - sz; t = Math.max(0.5f, (float) Math.sqrt(bx * bx + by * by + bz * bz) - 0.5f); }
        t = Math.max(t, 3f);
        float ex = p.x, ey = p.eyeY(), ez = p.z;
        float ax = sx + dx * t - ex, ay = sy + dy * t - ey, az = sz + dz * t - ez, al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (al < 1.5f) return;
        ax /= al; ay /= al; az /= al;
        int[] own = Physics.ray(raid.world, ex + ax * 0.4f, ey + ay * 0.4f, ez + az * 0.4f, ax, ay, az, Math.min(al - 0.9f, 6f), false);
        if (own == null) return;
        float ox = own[0] + 0.5f - ex, oy = own[1] + 0.5f - ey, oz = own[2] + 0.5f - ez, ol = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
        if (ol > al - 1.2f) return;
        // where on the screen the round stops: a point on the muzzle's line, at the face of what is in the way
        float d = Math.max(0.3f, ol - 0.6f);
        if (pv.project(ex + ax * d, ey + ay * d, ez + az * d, aimScratch) <= 0) return;
        hud.aimBlocked = true;
        hud.aimBlockX = (aimScratch[0] * 0.5f + 0.5f) * c.ui.w; hud.aimBlockY = (1 - (aimScratch[1] * 0.5f + 0.5f)) * c.ui.h;
        aimBlocks++;
    }
    private final float[] aimScratch = new float[4];
    /** Frames on which the HUD showed that the muzzle's line was blocked (tests). */
    public int aimBlocks;
    public float shoulderSide() { return tpsSide; }
    /** Opens or shuts the field map at a zoom (scripted runs). */
    public void debugMap(boolean open, float zoom) { mapOpen = open; mapZoom = zoom; }

    private float zoom() { ItemInstance h = held(); return h == null ? 1 : h.zoom(); }

    private void controls(Input in, float dt) {
        Actor p = raid.player;
        if (in.pressed("view")) thirdPerson = !thirdPerson;
        if (in.pressed("flashlight")) {
            if (raid.lightSource(held()) == 0) { raid.playerFlashlight = false; hud.message("No flashlight: carry one in your case, or fit one to the weapon", Ui.RED, raid.time); }
            else { raid.playerFlashlight = !raid.playerFlashlight; c.sfx("ui_click", 0.35f); }
        }
        boolean down = p.downed();
        // 0.6: the slide. More than two seconds into a sprint, the crouch key drops the body onto its hip and it carries on
        // along the ground: fast, low, and committed to the direction it was going.
        boolean cPress = in.pressed("crouch"), cDown = in.down("crouch");
        if (cPress && sprintHeld >= SLIDE_AFTER && p.onGround && p.slideT <= 0 && !down && !raid.noclip && !p.prone) { startSlide(p); crouchUsed = true; }
        else if (cPress) {
            crouchHoldT = 0; crouchUsed = false; crouchSuppressed = false;
            // 0.7: a press while prone gets up onto a knee (and that is all this press does)
            if (p.prone) { if (Physics.bodyFree(raid.world, p.x, p.y, p.z, p.radius, p.stand * Actor.CROUCH_H)) { p.prone = false; crouchToggle = true; } crouchUsed = true; }
            else if (c.settings.toggleCrouch) crouchToggle = !crouchToggle;
        }
        // 0.7: holding the key goes prone - from standing or from a crouch - and you stay down when you let go
        if (cDown && !crouchUsed && !down && p.slideT <= 0 && !raid.noclip) {
            crouchHoldT += dt;
            if (crouchHoldT >= PRONE_HOLD && p.onGround && !p.climbing) { p.prone = true; crouchUsed = true; crouchToggle = true; adsT = Math.min(adsT, 0.3f); c.sfx("zipper", 0.25f, 0.7f); }
        }
        if (!cDown) crouchSuppressed = false;
        p.crouch = c.settings.toggleCrouch ? crouchToggle : cDown && !crouchSuppressed;
        // 0.7: sprint gets you up. From a crouch or from prone, the sprint key stands the body (when there is room) and runs
        boolean wantsRun = (in.down("forward") || thirdPerson && (in.down("back") || in.down("left") || in.down("right"))) && (c.settings.toggleSprint ? in.pressed("sprint") : in.down("sprint") && (in.pressed("sprint") || sprintFromLow));
        if (wantsRun && (p.crouch || p.prone) && p.slideT <= 0 && !down && raid.body.canSprint()) {
            if (Physics.bodyFree(raid.world, p.x, p.y, p.z, p.radius, p.stand)) {
                p.prone = false; p.crouch = false; crouchToggle = false; crouchSuppressed = true; crouchUsed = true; sprintFromLow = true;
                if (c.settings.toggleSprint) sprintLatch = true;
            }
        }
        if (!in.down("sprint")) sprintFromLow = false;
        if (p.slideT > 0) { p.crouch = true; p.prone = false; }
        if (down || raid.noclip || p.climbing) { p.prone = false; }
        if (p.prone) p.crouch = false;
        if (down) { p.crouch = false; crouchToggle = false; }
        // 1.1: room to stand is measured the way the body is moved - a stair under the feet is a floor, not a block the
        // body is inside (measured as a plain box, every flight of stairs put the survivor flat on their face)
        if (p.crouch == false && !p.prone && !down && !p.mantling() && !Physics.bodyFree(raid.world, p.x, p.y, p.z, p.radius, p.stand)) {
            // no room to stand: stay on a knee - or flat, where there is not even room for that
            if (Physics.bodyFree(raid.world, p.x, p.y, p.z, p.radius, p.stand * Actor.CROUCH_H)) p.crouch = true; else p.prone = true;
        }
        // on the ground only the sidearm can be worked
        if (down && !"sidearm".equals(heldSlot)) { if (lo().slot("sidearm") != null) switchTo("sidearm"); else { heldSlot = "none"; raid.player.weapon = null; } }
        if (!down && "none".equals(heldSlot)) { heldSlot = lo().slot("primary") != null ? "primary" : lo().slot("sidearm") != null ? "sidearm" : "melee"; raid.player.weapon = held(); }
        contextualCover(p, down);
        float tl = in.down("lean_left") ? -1 : in.down("lean_right") ? 1 : 0;
        // 0.8, aiming round a block: with the sights up and something solid right in front of the muzzle - the end of a
        // wall, a pillar, a doorframe - the body leans out of its own accord toward the side that is open, as far as it
        // takes to see past it, and comes back when the sights come down. Either lean key overrides it.
        autoLean = 0;
        if (tl == 0 && adsT > 0.35f && !raid.player.prone && raid.vehicles.driving == null) autoLean = raid.leanToClear(raid.player, raid.player.yaw, raid.player.pitch, LEAN_METRES);
        if (tl == 0) tl = autoLean;
        lean += (tl - lean) * Math.min(1, dt * 10);
        // weapon selection
        if (!down) {
            if (in.pressed("primary")) switchTo("primary");
            if (in.pressed("sidearm")) switchTo("sidearm");
            if (in.pressed("melee")) switchTo("melee");
        }
        for (int k = 0; k < 4; k++) if (in.pressed("quick" + (k + 1))) { ItemInstance[] s = wheel.slots(lo()); if (s[4 + k] != null) useItem(s[4 + k]); }
        if (in.pressed("throw") && !down) {
            // a launcher under the barrel takes the grenade itself; otherwise it goes into the hand to be aimed and thrown
            ItemInstance hw = held();
            ItemInstance[] s = wheel.slots(lo());
            boolean launcher = hw != null && hw.def().weapon != null && !hw.def().weapon.melee() && Gunsmith.stats(hw, 0).launcher;
            if (launcher && s[3] != null && !"noise".equals(s[3].def().throwable.kind) && !"flare".equals(s[3].def().throwable.kind)) throwIt(s[3], 1);
            else equipThrowable(null);
        }
        if (in.pressed("status")) hud11.peek();
        if (in.pressed("firemode")) { ItemInstance h = held(); if (h != null && h.def().weapon != null && h.def().weapon.modes.size() > 1) { h.fireMode = (h.fireMode + 1) % h.def().weapon.modes.size(); c.sfx("ui_click", 0.4f); hud.message("Fire mode: " + h.fireModeName().toUpperCase(), Ui.TXT, raid.time); } }
        if (in.pressed("reload")) reload();
        // a casualty in reach: holding [F] works on them, letting go leaves them where they are
        Actor patient = down ? null : raid.casualty.playerPatient();
        boolean working = patient != null && in.down("interact") && !p.sprinting;
        raid.casualty.playerRevive(working);
        if (working) { p.crouch = true; adsT = 0; raid.playerVulnerableUntil = raid.time + 0.3f; if (!p.anim.reviving) p.anim.reviving = true; }
        else if (p.anim.reviving && p.revivingId == 0) p.anim.reviving = false;
        if (p.hanging && in.pressed("interact")) { Physics.letGo(p); raid.hangDrops++; }
        else if (in.pressed("interact") && patient == null && !down) interact();
        if (p.hanging) {
            if (in.pressed("jump") && raid.body.stamina > 6) pullUp(p);
        } else if (in.pressed("jump") && p.prone && !down) {
            // [jump] while prone gets up; it does not jump
            if (Physics.bodyFree(raid.world, p.x, p.y, p.z, p.radius, p.stand * Actor.CROUCH_H)) { p.prone = false; crouchToggle = true; crouchUsed = true; }
        } else if (in.pressed("jump") && !down && !p.mantling() && raid.body.stamina > 8 && tryMantle(p)) {
            // 0.9: a ledge in front, two or three blocks up: hands on it, and up
        } else if (in.pressed("jump") && p.onGround && !p.crouch && !down && raid.body.stamina > 5) {
            p.vy = Physics.JUMP; raid.body.stamina -= 4; c.sfx("jump", 0.35f);
            if (p.anim != null && p.anim.action.isEmpty()) p.anim.play("jump", 0.5f);
        }
        // ADS
        boolean aim = scenarioAim || (c.settings.toggleAim ? (in.pressed("aim") ? adsT < 0.5f : adsT > 0.5f) : in.down("aim"));
        ItemInstance h = held();
        boolean canAim = h != null && h.def().weapon != null && !h.def().weapon.melee() && reloadT <= 0 && !p.sprinting;
        float adsSpeed = h != null && h.def().weapon != null && !h.def().weapon.melee() ? 1f / Gunsmith.stats(h, 0).adsTime : 8;
        // ADS progress is linear in the weapon's own ADS time, so the transition takes exactly that long (coming
        // down is a little quicker); what is drawn follows the eased value, what is decided follows the plain one
        float adsTarget = aim && canAim ? 1 : 0, adsStep = dt * adsSpeed;
        adsT = adsT < adsTarget ? Math.min(adsTarget, adsT + adsStep) : Math.max(adsTarget, adsT - adsStep * 1.25f);
        adsK = adsT * adsT * (3 - 2 * adsT);
        raid.playerAiming = adsT > 0.6f;
        // 1.2, blocking: the aim button with empty hands, or with a blade in them, puts the guard up for as long as it is held
        boolean blade = h != null && h.def().weapon != null && h.def().weapon.melee();
        boolean canBlock = (unarmed() || blade && "melee".equals(heldSlot) || blade) && !p.sprinting && punchT <= 0 && !p.downed() && !p.mantling() && !p.dead;
        blockT += ((scenarioBlock || in.down("aim")) && canBlock ? 1 : -1) * dt * 9;
        blockT = Math.max(0, Math.min(1, blockT));
        raid.playerBlocking = blockT > 0.55f; raid.blockWithBlade = blade;
        // 0.7: a laser is a laser and a flashlight is a flashlight. The beam others can see comes from a part in the laser
        // socket and nothing else - the Mounted Flashlight used to count as one, and drew a laser line from the gun.
        ItemInstance las = canAim ? h.attachments.get("laser") : null;
        int expose = las == null || las.def().attachment == null || las.def().attachment.light ? 0 : las.def().attachment.expose;
        // 0.8: a laser is dark until the weapon is aimed - whichever colour it is
        raid.playerLaser = expose > 0 && adsT > 0.5f;
        int lc = las == null ? 0 : absentialis.gameplay.weapons.Attachments.laserColor(las.defId);
        laserColor = lc == 0 ? 0xFFFF2A20 : lc;
        hud.laserOn = raid.playerLaser;
        hud.thirdPersonAim = thirdPerson && adsT > 0.6f;
        aimCheck(hud.thirdPersonAim);
        if (!raid.playerLaser) laserDotKnown = false;
        fire(in, dt);
    }

    /**
     * Contextual cover for the player (brief 4.2). Crouched against something low, or pressed to the end of a wall
     * and leaning, the body settles onto the cover point there: it aligns with the surface, and what it does next
     * is real exposure - aiming over low cover stands the body up to shoot and drops it back when the sights come
     * down; a lean is only offered round an end the obstacle actually has.
     */
    private void contextualCover(Actor p, boolean down) {
        absentialis.gameplay.CombatConfig cfg = raid.combat;
        float fx = (float) -Math.sin(p.yaw), fz = (float) -Math.cos(p.yaw);
        // 0.7: once in cover the camera is free - looking away from the wall must not take you off it. In third person the
        // cover is found from where the body is and what it is already against, not from where the eyes happen to point.
        if (thirdPerson && p.cover != null && p.cover.valid) { fx = p.cover.dx; fz = p.cover.dz; }
        else if (thirdPerson && p.faceSet) { fx = (float) -Math.sin(p.faceYaw); fz = (float) -Math.cos(p.faceYaw); }
        float hs = (float) Math.sqrt(p.vx * p.vx + p.vz * p.vz);
        absentialis.world.CoverMap.Point cp = down || p.prone || p.sprinting || !p.onGround || hs > cfg.coverMaxSpeed ? null : raid.world.cover.touching(p.x, p.y, p.z, fx, fz);
        if (cp == null && thirdPerson && !down && !p.prone && !p.sprinting && p.onGround && hs <= cfg.coverMaxSpeed) cp = raid.world.cover.touching(p.x, p.y, p.z, (float) -Math.sin(p.yaw), (float) -Math.cos(p.yaw));
        boolean wantLean = Math.abs(lean) > 0.25f;
        // (a lean the body took by itself counts as leaning here too: at the end of a wall, sights up, it settles onto the cover point)
        boolean in = cp != null && (crouchHeld() || cp.height == absentialis.world.CoverMap.HIGH && wantLean && (lean < 0 ? cp.leanLeft : cp.leanRight));
        p.cover = in ? cp : null;
        p.coverAmt = in ? 1 : 0;
        if (!in) { p.lean = lean * 0.6f; coverPeek = false; p.coverPeek = false; p.coverSide = 0; return; }
        // a lean only where the obstacle ends on that side
        float side = lean < -0.25f && cp.leanLeft ? -1 : lean > 0.25f && cp.leanRight ? 1 : 0;
        // "left" is as seen facing the obstacle; the player may be turned, so express it in the body's own frame
        float lx = cp.leftX(), lz = cp.leftZ(), rx = (float) Math.cos(p.yaw), rz = (float) -Math.sin(p.yaw);
        p.lean = side == 0 ? 0 : (-(lx * rx + lz * rz) >= 0 ? 1 : -1) * side;
        // over the top: sights up means standing up, and the head and chest are then really above the cover
        coverPeek = cp.height == absentialis.world.CoverMap.LOW && adsT > 0.35f;
        p.coverPeek = coverPeek;
        if (coverPeek) p.crouch = false;
    }

    // ------------------------------------------------------------------ 0.6 movement: sprint, slide, third-person weight
    /** Seconds of sprint before a slide is on offer, how long a slide lasts, what it costs, and how it sheds speed (m/s per second). */
    public static final float SLIDE_AFTER = 0.5f, SLIDE_TIME = 0.95f, SLIDE_STAMINA = 6f, SLIDE_FRICTION = 5.2f, SLIDE_MIN_SPEED = 6.2f, SLIDE_END_SPEED = 2.4f;
    /** Third person: how quickly the body gathers speed, sheds it and changes direction (per second), and how fast it turns to face (radians per second). */
    public static final float TPS_ACCEL = 8.5f, TPS_DECEL = 7f, TPS_TURN_ACCEL = 10f, TPS_FACE_WALK = 9.5f, TPS_FACE_SPRINT = 6.5f, TPS_FACE_AIM = 14f, TPS_FACE_PRONE = 2.6f, TPS_FACE_PRONE_AIM = 3.6f, TPS_FACE_GUARD = 40f;
    /** How long the current sprint has lasted, the sprint toggle's state, and the speed of a slide in progress. */
    public float sprintHeld;
    private boolean sprintLatch, sprintFromLow;
    private float slideSpeed;
    private int laserColor = 0xFFFF2A20;
    /** Where the laser's dot fell this frame (the light it casts is put there), and whether it fell on anything. */
    private final float[] laserDot = new float[3];
    private boolean laserDotKnown;
    /** How far a laser reaches, how big its dot is drawn, and how much light the dot throws. */
    public static float LASER_REACH = 70f, LASER_DOT = 0.035f, LASER_LIGHT_RADIUS = 2.6f, LASER_LIGHT = 3.2f;

    /** The laser this frame: for tests. {on (0/1), dot x, y, z, ARGB as float bits are not kept - see laserColorNow}. */
    public boolean laserOn() { return raid.playerLaser; }
    public int laserColorNow() { return laserColor; }
    public float[] laserDot() { return laserDotKnown ? laserDot.clone() : null; }
    /** 1.1: whose body the laser dot is lying on this frame (an actor id; 0 when it is on the world or on nothing). */
    public int laserOnActor;
    /** Works out where the weapon is pointed now (the laser test, callable from gates and scripts): the distance, or -1. */
    public float aimProbe(float[] out) { return aimHit(out); }

    /**
     * Where the weapon is pointed: the first thing on the line of aim, a wall or a body. In first person the line
     * starts at the eye; in third person at the camera, because what the player is aiming with is the middle of the
     * picture. Fills {@code out} and returns the distance, or -1 when there is nothing within reach.
     */
    private float aimHit(float[] out) {
        Actor pl = raid.player;
        float dx = (float) (-Math.sin(pl.yaw) * Math.cos(pl.pitch)), dy = (float) Math.sin(pl.pitch), dz = (float) (-Math.cos(pl.yaw) * Math.cos(pl.pitch));
        float ox = thirdPerson ? env.camX : eyeX, oy = thirdPerson ? env.camY : eyeY, oz = thirdPerson ? env.camZ : eyeZ;
        float best = -1;
        // voxel walk: fine steps, so the dot sits on the face it struck and not in the middle of the block
        for (float t = 0.3f; t <= LASER_REACH; t += 0.08f) {
            if (raid.world.solid((int) Math.floor(ox + dx * t), (int) Math.floor(oy + dy * t), (int) Math.floor(oz + dz * t))) { best = Math.max(0.3f, t - 0.08f); break; }
        }
        float lim = best < 0 ? LASER_REACH : best;
        laserOnActor = 0;
        // 1.1: the beam stops on a body - anybody's, standing, crouched, crawling, on all fours, or lying where physics left
        // it. (It was tested against a post a metre tall and thirty centimetres through stood at each character's feet, which
        // a giant, a Crawler, a prone man and every corpse are not - the dot went through them and lay on the wall behind.)
        for (Actor a : raid.actors) {
            if (a == pl || a.dead && a.ragdoll == null) continue;
            if (Math.abs(a.x - ox) > lim + 3 || Math.abs(a.z - oz) > lim + 3) continue;
            float t = -1;
            if (a.ragdoll != null) {
                absentialis.character.Ragdoll rg = a.ragdoll;
                for (int i = 0; i < rg.x.length; i++) {
                    float cx = rg.x[i] - ox, cy = rg.y[i] - oy, cz = rg.z[i] - oz, along = cx * dx + cy * dy + cz * dz;
                    if (along < 0.4f || along > lim) continue;
                    float qx = cx - dx * along, qy = cy - dy * along, qz = cz - dz * along, rr = 0.17f;
                    if (qx * qx + qy * qy + qz * qz < rr * rr && (t < 0 || along - rr < t)) t = along - rr;
                }
            } else t = a.intersectBody(ox, oy, oz, dx, dy, dz, lim);
            if (t > 0.4f && t < lim) { lim = t; best = t; laserOnActor = a.id; }
        }
        if (best < 0) return -1;
        out[0] = ox + dx * best; out[1] = oy + dy * best; out[2] = oz + dz * best;
        return best;
    }
    public int slides;

    private void startSlide(Actor p) {
        float hs = (float) Math.sqrt(p.vx * p.vx + p.vz * p.vz);
        if (hs < 3f) return;
        p.slideT = SLIDE_TIME;
        p.slideX = p.vx / hs; p.slideZ = p.vz / hs;
        slideSpeed = Math.max(hs * 1.12f, SLIDE_MIN_SPEED);
        raid.body.stamina = Math.max(0, raid.body.stamina - SLIDE_STAMINA);
        crouchToggle = true;                      // you come out of it on one knee
        sprintHeld = 0; sprintLatch = false;
        adsT = 0;
        slides++;
        c.sfx("slide", 0.5f);
        raid.emitSound(p.x, p.y, p.z, 14, "step", p.id, false);
    }

    /** Whether the light that is on comes from the weapon (else from the hand torch on the chest). */
    private boolean lightOnWeapon;
    /** How hard the last shot kicked (see Animator.kickOf): scales the view model's recoil and how long it lasts. */
    private float firePunch = 1;
    /** Experience shown as it is earned (0.6). */
    public XpFeed xpFeed;
    private boolean coverPeek;
    /** Eased ADS blend for everything visual (view model, field of view); {@link #adsT} stays the authority for rules. */
    private float adsK;
    private final java.util.Map<String, Float> sightCache = new java.util.HashMap<>();

    /**
     * Where this weapon's sight line is, measured from its own model: the highest point over the rear of the
     * receiver (the rear sight, or the optic mounted there). Aiming brings that line onto the view axis, so every
     * weapon - long, short, scoped - aligns by its own geometry instead of sharing one offset.
     */
    private float sightLine(ItemInstance h, VoxModel m, float s) {
        Float got = sightCache.get(h.visualKey());
        if (got != null) return got;
        int top = -1;
        for (int x = (int) (m.sx * 0.12f); x <= (int) (m.sx * 0.5f); x++) for (int z = 0; z < m.sz; z++) for (int y = m.sy - 1; y > top; y--) if (m.get(x, y, z) != 0) { top = y; break; }
        if (top < 0) top = m.sy - 1;
        // irons: the top of the rear sight sits just under the aim point; an optic is looked through, not over
        float v = -((top + 1) - 0.55f * m.sy) * s + (h.zoom() > 1 ? 1.4f * s : -0.003f);
        if (sightCache.size() > 256) sightCache.clear();
        sightCache.put(h.visualKey(), v);
        return v;
    }
    private boolean crouchHeld() { return c.settings.toggleCrouch ? crouchToggle : c.in.down("crouch"); }

    /** 1.1: how far above the feet a ledge has to be before it is hung from rather than simply climbed, and what hanging costs a second. */
    public static float HANG_FROM = 2.5f, HANG_STAMINA = 2.5f;

    /** 1.1: the second half of a hang - up and over - or letting go of it. */
    private void pullUp(Actor p) {
        float from = p.y;
        Physics.pullUp(p);
        raid.body.stamina -= 4 + 3 * (p.mToY - from);
        raid.mantles++;
        c.sfx("gear_rattle", 0.3f);
        raid.emitSound(p.x, p.y + 1, p.z, 7, "search", p.id, false);
    }

    /** 0.9: pulls the survivor up onto a ledge in front of them if there is one in reach (two or three blocks up). */
    private boolean tryMantle(Actor p) {
        if (p.prone || p.seated || raid.noclip || raid.vehicles.inside() || !raid.playerKind.isEmpty() && !raid.playerCanDrive) return false;   // an infected actor has no hands for it
        float[] to = Physics.ledge(raid.world, p, (float) -Math.sin(p.yaw), (float) -Math.cos(p.yaw));
        if (to == null) return false;
        // 1.1: a ledge that takes the feet off the ground is taken hold of first, and hung from: [jump] again pulls up, [interact] lets go
        if (to[1] - p.y >= HANG_FROM || !p.onGround) {
            Physics.hang(p, to);
            raid.body.stamina -= 4;
            raid.hangs++;
            adsT = 0; reloadT = 0;
            c.sfx("gear_rattle", 0.3f);
            raid.emitSound(p.x, p.y + 1, p.z, 6, "search", p.id, false);
            hud.message("[" + absentialis.engine.Input.keyName(c.in.bindings.getOrDefault("jump", 32)) + "] pull up     [" + absentialis.engine.Input.keyName(c.in.bindings.getOrDefault("interact", 70)) + "] let go", Ui.TXT_DIM, raid.time);
            return true;
        }
        Physics.mantle(p, to);
        raid.body.stamina -= 6 + 3 * (to[1] - p.mFromY);
        raid.mantles++;
        adsT = 0; reloadT = 0;
        // 1.0: the grunt the survivor gave on every climb is gone; what is heard is their kit
        c.sfx("gear_rattle", 0.3f);
        raid.emitSound(p.x, p.y + 1, p.z, 7, "search", p.id, false);
        return true;
    }

    private void movePlayer(float dt) {
        Actor p = raid.player;
        Input in = c.in;
        if (raid.takedown != null) { p.vx = p.vz = 0; p.sprinting = false; Physics.move(raid.world, p, dt); return; }      // both hands are busy
        if (Physics.hangStep(raid.world, p, dt)) {
            // hanging is work: the arms give out before long, and then there is no choosing about it
            p.sprinting = false;
            raid.body.stamina = Math.max(0, raid.body.stamina - HANG_STAMINA * dt);
            if (raid.body.stamina <= 0.5f) { Physics.letGo(p); raid.hangDrops++; hud.message("Your arms gave out", Ui.AMBER, raid.time); }
            return;
        }
        if (Physics.mantleStep(raid.world, p, dt)) { p.sprinting = false; if (!p.mantling()) { landDip = Math.max(landDip, 0.05f); c.sfx("step_concrete", 0.4f); } return; }
        boolean ctl = !overlay();
        float f = ctl ? (in.down("forward") ? 1 : 0) - (in.down("back") ? 1 : 0) : 0;
        float s = ctl ? (in.down("right") ? 1 : 0) - (in.down("left") ? 1 : 0) : 0;
        boolean moving = f != 0 || s != 0;
        absentialis.gameplay.CombatConfig cfg = raid.combat;
        int cond = cfg.condition(p.hp, p.maxHp).ordinal();
        boolean downed = p.downed();
        // sprint: held, or a toggle that lets go when you stop (Settings). First person runs where it looks; in third
        // person the body is sent by the keys and runs wherever it is sent.
        boolean sprintKey;
        if (c.settings.toggleSprint) { if (ctl && in.pressed("sprint")) sprintLatch = !sprintLatch; if (!moving) sprintLatch = false; sprintKey = sprintLatch; }
        else sprintKey = ctl && in.down("sprint");
        boolean sprintDir = thirdPerson ? moving : f > 0;
        p.sprinting = ctl && sprintKey && sprintDir && !p.crouch && !p.prone && p.slideT <= 0 && adsT < 0.3f && raid.body.canSprint() && cfg.sprint[cond] && !downed && p.revivingId == 0;
        sprintHeld = p.sprinting ? sprintHeld + dt : p.slideT > 0 ? 0 : Math.max(0, sprintHeld - dt * 6);
        ItemInstance h = held();
        boolean gun = h != null && h.def().weapon != null && !h.def().weapon.melee();
        float wMass = h == null || gun ? 0 : h.mass();
        float speed = raid.body.moveSpeed(p, p.sprinting, ctl && in.down("walk"), lo().mass(), c.profile.prog.carryCapacityKg(), wMass) * raid.playerSpeedMul;
        if (gun) speed *= Gunsmith.stats(h, 0).mobility;
        speed *= 1 + lo().charm("speed");
        if (adsT > 0.5f) speed *= 0.6f;
        if (raid.body.using != null) speed *= 0.5f;
        if (raid.time < raid.grabUntil) speed *= 0.18f;      // held: you can turn and strike, not run
        // health condition: the limp is in the legs as well as the animation
        speed *= p.sprinting ? cfg.sprintSpeed[cond] : cfg.speed[cond];
        if (downed) speed = Math.min(speed, absentialis.entity.Survivor.WALK / 25f * cfg.crawlSpeed * 0.5f);
        // prone: a crawl, the same pace whichever way it goes
        if (p.prone) speed = Math.min(speed, PRONE_SPEED * (adsT > 0.5f ? 0.5f : 1));
        if (p.revivingId != 0) speed = 0;
        if (raid.noclip) {
            float fx = (float) (-Math.sin(p.yaw) * Math.cos(p.pitch)), fy = (float) Math.sin(p.pitch), fz = (float) (-Math.cos(p.yaw) * Math.cos(p.pitch));
            float sp = (in.down("sprint") ? 40 : 14) * dt;
            p.x += (fx * f + (float) Math.cos(p.yaw) * s) * sp; p.y += fy * f * sp + (in.down("jump") ? sp : 0) - (in.down("crouch") ? sp : 0); p.z += (fz * f - (float) Math.sin(p.yaw) * s) * sp;
            p.vx = p.vy = p.vz = 0;
            return;
        }
        float fx = (float) -Math.sin(p.yaw), fz = (float) -Math.cos(p.yaw), rx = (float) Math.cos(p.yaw), rz = (float) -Math.sin(p.yaw);
        float tx = (fx * f + rx * s), tz = (fz * f + rz * s);
        float l = (float) Math.sqrt(tx * tx + tz * tz);
        if (l > 0) { tx = tx / l * speed; tz = tz / l * speed; }
        // wet blood has almost no grip: you keep sliding the way you were going
        // third person has weight: the body gathers speed, sheds it and comes about at different rates, none of them instant
        float ground = 14;
        if (thirdPerson) {
            float cur = (float) Math.sqrt(p.vx * p.vx + p.vz * p.vz), want = (float) Math.sqrt(tx * tx + tz * tz);
            ground = want < 0.1f ? TPS_DECEL : want > cur + 0.15f ? TPS_ACCEL : TPS_TURN_ACCEL;
        }
        float acc = (p.onGround ? ground : 3) * raid.gore.friction(p.x, p.y, p.z) * (raid.slipT > 0 ? 0.12f : 1);
        if (raid.slipT > 0) { landDip = Math.max(landDip, 0.2f); speed *= 0.4f; }
        if (p.slideT > 0) {
            // sliding: the ground takes the speed off; the keys can only nudge the line
            p.slideT -= dt;
            slideSpeed = Math.max(0, slideSpeed - SLIDE_FRICTION * dt);
            if (l > 0) { float nx = p.slideX + tx / Math.max(0.01f, speed) * dt * 1.2f, nz = p.slideZ + tz / Math.max(0.01f, speed) * dt * 1.2f, nl = (float) Math.sqrt(nx * nx + nz * nz); if (nl > 0.01f) { p.slideX = nx / nl; p.slideZ = nz / nl; } }
            p.vx = p.slideX * slideSpeed; p.vz = p.slideZ * slideSpeed;
            raid.playerVulnerableUntil = raid.time + 0.2f;
            if (slideSpeed < SLIDE_END_SPEED || !p.onGround && p.vy < -4) p.slideT = 0;
        } else if (raid.time < p.flingUntil) {
            // 0.7: in a Tank's hand, or in the air from its throw: the legs have no say until the ground does
            if (p.onGround && p.vy <= 0) { p.vx *= Math.max(0, 1 - dt * 6); p.vz *= Math.max(0, 1 - dt * 6); landDip = Math.max(landDip, 0.25f); }
        } else {
            p.vx += (tx - p.vx) * Math.min(1, dt * acc);
            p.vz += (tz - p.vz) * Math.min(1, dt * acc);
        }
        // forward climbs (or descends when looking down the ladder), back always descends
        p.wantClimb = ctl && f != 0 && Physics.inClimb(raid.world, p.x, p.y, p.z);
        p.climbDir = f < 0 || p.pitch < -0.6f ? -1 : 1;
        boolean hitWall = Physics.move(raid.world, p, dt);
        if (p.slideT > 0 && hitWall) { p.slideT = 0; landDip = Math.max(landDip, 0.08f); }
        // third person: the body turns to face where it is going; aiming, firing, cover and anything done with the hands
        // turn it to face where the player is looking instead
        if (thirdPerson) {
            float hsNow = (float) Math.sqrt(p.vx * p.vx + p.vz * p.vz);
            // what the hands are doing turns the body to the look direction; cover, by itself, no longer does
            // 1.3: and a guard. With it up, the body is locked to the cursor - squared up to where the player looks, turning
            // with the mouse as fast as the mouse turns; with it down, the body goes back to facing the way it moves
            boolean guarding = blockT > 0.25f;
            boolean busy = adsT > 0.05f || raid.time - lastShot < 0.9f || p.revivingId != 0 || downed || meleeT > 0 || raid.body.using != null || reloadT > 0 && hsNow < 0.5f || throwAiming || guarding;
            if (!p.faceSet) { p.faceYaw = p.yaw; p.faceSet = true; }
            float target, rate;
            if (p.cover != null && p.cover.valid && !busy && !p.prone) {
                // 0.7: in cover the body is side-on to the wall - a shoulder and the back against it - and stays that way while
                // the camera looks wherever it likes. Which shoulder: the one already there; moving along the wall turns to
                // face the way of travel; coming into cover, whichever is nearer how the body stood.
                float lx = p.cover.leftX(), lz = p.cover.leftZ();
                float yawA = (float) Math.atan2(-lx, -lz), yawB = (float) Math.atan2(lx, lz);     // facing along "left" (wall on the right) / the other way
                boolean rightToWall;
                if (hsNow > 0.35f) rightToWall = p.vx * lx + p.vz * lz >= 0;
                else if (p.coverSide != 0) rightToWall = p.coverSide > 0;
                else rightToWall = Math.abs(wrapPi(yawA - p.faceYaw)) <= Math.abs(wrapPi(yawB - p.faceYaw));
                p.coverSide = rightToWall ? 1 : -1;
                target = rightToWall ? yawA : yawB;
                rate = 9f;
            } else {
                p.coverSide = 0;
                // 0.8: a prone body does not swing round with the camera. It crawls the way it is sent - the body comes about, slowly,
                // to where it is going - lies as it lay when it stops, and only turns to the look when the hands are busy (aiming, firing)
                boolean lock = busy || p.cover != null;
                target = lock ? p.yaw : hsNow > (p.prone ? 0.15f : 0.5f) ? (float) Math.atan2(-p.vx, -p.vz) : p.faceYaw;
                rate = p.prone ? (lock ? TPS_FACE_PRONE_AIM : TPS_FACE_PRONE) : lock ? TPS_FACE_AIM : p.slideT > 0 ? 3f : p.sprinting ? TPS_FACE_SPRINT : TPS_FACE_WALK;
            }
            if (guarding && !p.prone) { target = p.yaw; rate = TPS_FACE_GUARD; }
            p.faceYaw = absentialis.neuralai.integration.Steering.turn(p.faceYaw, target, dt * rate);
        } else { p.faceSet = false; p.coverSide = 0; }
        // fall damage
        if (p.onGround && p.fallSpeed > 13) { float dmg = (p.fallSpeed - 13) * 6; raid.damageActor(p, null, dmg, null, "legs", p.x, p.y, p.z); if (dmg > 20) raid.body.fracture = true; hud.message("Hard landing", Ui.RED, raid.time); }
        if (p.onGround && p.fallSpeed > 5) { landDip = Math.min(0.22f, p.fallSpeed * 0.012f); playStep(0.5f + Math.min(0.4f, p.fallSpeed * 0.03f), 0.85f); }
        p.fallSpeed = 0;
        p.stepped = 0;
        landDip = Math.max(0, landDip - dt * 0.9f);
        float hs = (float) Math.sqrt(p.vx * p.vx + p.vz * p.vz);
        bobAmt += ((p.onGround && moving ? Math.min(1, hs / 4f) : 0) - bobAmt) * Math.min(1, dt * 8);
    }

    private static float wrapPi(float a) { while (a > Math.PI) a -= 2 * Math.PI; while (a < -Math.PI) a += 2 * Math.PI; return a; }

    /** Footstep material under the player: water when wading, otherwise the block's material. */
    private String stepMaterial() {
        Actor p = raid.player;
        Block in = raid.world.block((int) Math.floor(p.x), (int) Math.floor(p.y + 0.2f), (int) Math.floor(p.z));
        if (in != null && in.mat == absentialis.world.Material.WATER) return "water";
        Block under = raid.world.block((int) Math.floor(p.x), (int) Math.floor(p.y - 0.1f), (int) Math.floor(p.z));
        return WorldAcoustics.stepSurface(under);
    }

    private void playStep(float gain, float pitch) {
        String mat = stepMaterial();
        if (c.audio != null && !"none".equals(mat)) c.audio.play(c.audio.step(mat), gain, (raid.rng.nextFloat() - 0.5f) * 0.3f, pitch * (0.92f + raid.rng.nextFloat() * 0.16f));
    }

    /** One footstep per animated footfall (each half gait cycle): what you hear is what the legs do. */
    private void footfalls() {
        Actor p = raid.player;
        Animator an = p.anim;
        if (an == null) return;
        float ph = an.phase;
        boolean crossed = (lastStepPhase < 0.5f) != (ph < 0.5f);
        lastStepPhase = ph;
        stepGate -= c.ui.dt;
        if (!crossed || !p.onGround || an.speed < 0.5f || stepGate > 0 || p.climbing) return;
        stepGate = 0.14f;
        Block under = raid.world.block((int) Math.floor(p.x), (int) Math.floor(p.y - 0.1f), (int) Math.floor(p.z));
        boolean walk = c.in.down("walk");
        float radius = (p.prone ? 1.1f : p.crouch ? 2.5f : p.sprinting ? 20 : walk ? 4 : 9) * (under == null ? 1 : under.mat.noiseMul) * (1 + lo().charm("noise")) * (1 + Math.max(0, lo().mass() - c.profile.prog.carryCapacityKg()) * 0.02f);
        raid.emitSound(p.x, p.y, p.z, radius, "step", p.id, false);
        playStep(p.prone ? 0.07f : p.crouch ? 0.15f : p.sprinting ? 0.5f : walk ? 0.2f : 0.32f, p.prone ? 0.8f : 1f);
        // loose kit rattles when you run with a heavy pack
        if (p.sprinting && lo().mass() > 14 && raid.rng.chance(0.5)) c.sfx("gear_rattle", 0.12f);
    }

    // ------------------------------------------------------------------ weapons
    /** Scripted scenarios put a weapon in the hands with this. */
    public void scenarioHold(String slot) { heldSlot = slot; raid.player.weapon = held(); }

    private void switchTo(String slot) {
        if (slot.equals(heldSlot) || lo().slot(slot) == null) return;
        heldThrow = null; throwAiming = false; throwCharge = 0;
        heldSlot = slot;
        cancelReload();
        switchT = 0.45f;
        adsT = 0;
        c.sfx(equipSound(held()), 0.5f);
        raid.player.weapon = held();
        raid.player.anim.play("equip", 0.45f);
    }

    private static String equipSound(ItemInstance w) {
        if (w == null || w.def().weapon == null) return "zipper";
        return w.def().weapon.melee() ? "equip_melee" : "sidearm".equals(w.def().slot) ? "equip_pistol" : "equip_rifle";
    }

    private void cancelReload() {
        if (reloadT > 0 && raid.player.anim != null && raid.player.anim.action.startsWith("reload")) raid.player.anim.action = "";
        reloadT = 0; reloadKind = null; reloadMag = null;
    }

    private boolean reloadEmpty;
    /** Length of the swing in progress (the view model plays the strike against it). */
    private float meleeDur = 0.6f;
    private int reloadCue;
    private float stfT;

    /** Reload foley keyed to the animation: tactical and empty reloads sound as different as they look. */
    private void reloadFoley(float t) {
        if (reloadKind == null) return;
        float[] at; String[] snd;
        switch (reloadKind) {
            case "jam", "rack" -> { at = new float[]{0.42f, 0.60f}; snd = new String[]{"bolt_back", "bolt_release"}; }
            case "internal" -> { at = new float[]{0.80f}; snd = new String[]{"shell_in"}; }
            case "pumpfull" -> { at = new float[]{0.28f}; snd = new String[]{"pump"}; }
            default -> {
                if (reloadEmpty) { at = new float[]{0.16f, 0.26f, 0.64f, 0.80f, 0.88f}; snd = new String[]{"mag_out", "mag_drop", "mag_in", "bolt_back", "bolt_release"}; }
                else { at = new float[]{0.20f, 0.50f, 0.82f}; snd = new String[]{"mag_out", "pouch", "mag_in"}; }
            }
        }
        // each cue is a recorded handling sound of the weapon's family where one exists
        ItemInstance h = held();
        String fam = h == null || h.def().weapon == null ? "rifle" : handlingFamily(h.def().weapon);
        while (reloadCue < at.length && t >= at[reloadCue]) {
            String rec = c.audio == null ? null : c.audio.guns.cue(fam, snd[reloadCue]);
            if (rec != null) c.sfx("h_" + rec, 0.75f, 0.96f + raid.rng.nextFloat() * 0.08f); else c.sfx(snd[reloadCue], 0.6f);
            reloadCue++;
        }
    }

    private static String handlingFamily(ItemDef.Weapon w) {
        String f = w.family == null ? "" : w.family;
        return f.contains("pistol") || f.contains("handgun") || f.contains("sidearm") || f.contains("revolver") || f.contains("magnum") ? "pistol" : f.contains("shotgun") ? "shotgun" : "rifle";
    }

    // ------------------------------------------------------------------ 1.1: bare hands
    /** What the three punches of a combination do (right, left, heavy right), what each costs in stamina, how long each takes, how long there is to follow one with the next, and the rest after the third. */
    public static float[] PUNCH_DAMAGE = {9f, 9f, 24f}, PUNCH_STAMINA = {5f, 5f, 9f}, PUNCH_SECONDS = {0.30f, 0.30f, 0.46f};
    public static float PUNCH_COMBO_WINDOW = 0.6f, PUNCH_REST = 2.5f, PUNCH_REACH = 1.55f, PUNCH_CONTACT = 0.46f;
    /** 1.3: how long a full combination puts a person out for (a minute and a half), and how many it has (gates). */
    public static float KNOCKOUT_SECONDS = 90f;
    public int punchKnockouts;
    private float punchT, punchDur, punchComboUntil = -99, punchRestUntil = -99, lastPunch = -99;
    /** 1.2: how far up the guard is (0..1), and a scripted run holding it up. */
    public float blockT;
    public boolean scenarioBlock;
    private int punchIdx = -1, punchNext;
    private boolean punchLanded;
    /** Punches thrown, punches that landed, and third punches that put somebody down (gates). */
    public int punchesThrown, punchesLanded, punchKnockdowns;
    /** Which punch of the combination is in the air (0 right, 1 left, 2 heavy right; -1 none), and which the next will be. */
    public int punchNow() { return punchT > 0 ? punchIdx : -1; }
    public int punchUpNext() { return raid.time <= punchComboUntil ? punchNext : 0; }
    public boolean punchResting() { return raid.time < punchRestUntil; }

    /** Nothing in the hands (and nothing about to be thrown): the hands themselves. */
    public boolean unarmed() { return !"throw".equals(heldSlot) && held() == null; }

    /** Puts the weapon in hand away and leaves the hands free (choosing an empty slot of the quick menu does this). */
    public void unequip() {
        if (unarmed()) return;
        heldThrow = null; throwAiming = false; throwCharge = 0;
        cancelReload();
        heldSlot = "fists";
        raid.player.weapon = null;
        adsT = 0; switchT = 0.3f;
        c.sfx("zipper", 0.3f, 1.1f);
        hud.message("Hands free - [" + Input.keyName(c.in.bindings.get("fire")) + "] to throw a punch", Ui.TXT, raid.time);
    }

    /**
     * A punch. The first of a run is always the right hand; thrown again while the first is still landing or just after,
     * the next is the left; a third is the right again with the whole body behind it. After the third the hands need two
     * or three seconds. Each one costs stamina, and somebody out of breath cannot throw one. What it does to whoever is
     * in front of it happens at the moment of contact ({@link #punchContact}), not at the click.
     */
    public boolean punch() {
        if (raid.time < punchRestUntil || punchT > punchDur * 0.38f) return false;
        int n = raid.time <= punchComboUntil ? punchNext : 0;
        if (raid.body.stamina < PUNCH_STAMINA[n] || raid.body.exhausted > 0) { hud.message("Too winded to throw a punch", Ui.AMBER, raid.time); return false; }
        raid.body.stamina -= PUNCH_STAMINA[n]; raid.body.staminaChanged = raid.time;
        punchIdx = n; punchDur = PUNCH_SECONDS[n]; punchT = punchDur; punchLanded = false; lastPunch = raid.time;
        if (n == 2) { punchRestUntil = raid.time + punchDur + PUNCH_REST; punchNext = 0; punchComboUntil = -99; }
        else { punchNext = n + 1; punchComboUntil = raid.time + punchDur + PUNCH_COMBO_WINDOW; }
        if (raid.player.anim != null) raid.player.anim.play(n == 0 ? "punch_r" : n == 1 ? "punch_l" : "punch_heavy", punchDur + 0.1f);
        c.sfx("melee_swing", n == 2 ? 0.7f : 0.45f, n == 2 ? 0.85f : 1.15f);
        punchesThrown++;
        return true;
    }

    /** The fist arrives: whoever is in front of it and in reach is hit, and their body answers through Sydney - rocked by the first two, floored by the third. */
    private void punchContact() {
        punchLanded = true;
        Actor p = raid.player;
        float fx = (float) -Math.sin(p.yaw), fz = (float) -Math.cos(p.yaw);
        Actor best = null; float bd = PUNCH_REACH;
        for (Actor a : raid.nearby(p.x, p.z, 3)) {
            if (a.isPlayer || a.dead || a.seated || Math.abs(a.y - p.y) > 1.4f) continue;
            float dx = a.x - p.x, dz = a.z - p.z, d = (float) Math.sqrt(dx * dx + dz * dz) - a.radius;
            if (d < bd && (dx * fx + dz * fz) / Math.max(0.01f, (float) Math.sqrt(dx * dx + dz * dz)) > 0.55f) { bd = d; best = a; }
        }
        if (best == null) {
            // 1.1: an animal in front of the fist takes it
            Animals.Animal an = raid.animals.inFront(p, PUNCH_REACH + 0.3f);
            if (an != null) { punchesLanded++; raid.animals.hurt(an, PUNCH_DAMAGE[punchIdx], p.id, fx, fz); raid.cue("impact_flesh", an.x, an.y + 0.3f, an.z, 0.6f, 20); return; }
            // 1.3: a pane of glass in front of the fist goes through (a window, a glass door, a display case)
            if (punchGlass(p, fx, fz)) return;
            raid.emitSound(p.x, p.y + 1.3f, p.z, 3, "melee", p.id, false);
            return;
        }
        int n = punchIdx;
        float through = absentialis.neuralai.integration.Blows.defend(raid, best, p);
        if (through <= 0) { hud.message("They stepped out of it", Ui.TXT_DIM, raid.time); best.fistChain = 0; return; }
        punchesLanded++;
        boolean heavy = n == 2, big = absentialis.neuralai.integration.Archetypes.huge(best.archetype);
        // 1.3: the combination, counted on the body that takes it: the right, the left and the heavy right, each landing, one
        // straight after the other. The whole of it does not kill a person - it puts them out (Raid.knockOut)
        boolean person = !best.infected() && !big;
        if (n == 0) { best.fistChain = 1; best.fistWasHostile = best.hostileToPlayer; }
        else best.fistChain = best.fistFrom == p.id && best.fistChain == n && raid.time - best.fistAt < PUNCH_SECONDS[n] + PUNCH_COMBO_WINDOW + 0.5f ? n + 1 : 0;
        best.fistFrom = p.id; best.fistAt = raid.time;
        boolean knockout = heavy && person && best.fistChain == 3 && best.ragdoll == null;
        float dmg = PUNCH_DAMAGE[n] * through * (1 + lo().charm("melee"));
        // fists do not kill a person: a beating leaves them on the floor, not dead (the dead are the infected's business)
        if (person && best.hp > 1) dmg = Math.min(dmg, (best.hp - 1) / (heavy || n == 0 ? 3.2f : 1f));
        raid.hitKind = "blunt";
        best.hitDirX = fx; best.hitDirZ = fz;
        raid.gore.mistOnly = person;
        raid.damageActor(best, p, dmg, null, heavy || n == 0 ? "head" : "torso", best.x, best.y + (heavy || n == 0 ? best.height() * 0.88f : best.height() * 0.62f), best.z);
        raid.gore.mistOnly = false;
        raid.hitKind = "";
        if (best.id == raid.grabbedBy) raid.grabUntil = -99;
        // the body answers: a jab rocks it (it keeps or loses its feet on its own account); the third one puts it down
        if (knockout && !best.dead) { raid.knockOut(best, fx, fz, KNOCKOUT_SECONDS); punchKnockdowns++; punchKnockouts++; hud.message("Knocked out cold", Ui.TXT, raid.time); }
        else if (!best.dead && !big) {
            if (heavy) { raid.gore.ragdolls.knockDown(best, fx, fz, 72, 1.4f); if (best.ragdoll != null) punchKnockdowns++; }
            else raid.gore.ragdolls.unbalance(best, fx + (n == 0 ? -0.35f : 0.35f) * (float) Math.cos(p.yaw), fz + (n == 0 ? 0.35f : -0.35f) * (float) Math.sin(p.yaw), 11f, n == 0 ? "head" : "torso");
        }
        c.sfx(through < 1 ? "melee_block" : "punch", heavy ? 0.9f : 0.65f, heavy ? 0.85f : 1.05f);
        shake = Math.max(shake, heavy ? 0.5f : 0.22f);
        raid.emitSound(p.x, p.y + 1.3f, p.z, heavy ? 9 : 6, "melee", p.id, false);
    }

    /** 1.3: the fist's line, from the eyes along the facing, to the end of its reach: the first glass it meets is broken. */
    private boolean punchGlass(Actor p, float fx, float fz) {
        float ey = p.eyeY() - 0.25f, py = (float) Math.sin(p.pitch);
        float cp = (float) Math.cos(p.pitch);
        for (float t = 0.2f; t <= PUNCH_REACH + 0.35f; t += 0.08f) {
            int bx = (int) Math.floor(p.x + fx * cp * t), by = (int) Math.floor(ey + py * t), bz = (int) Math.floor(p.z + fz * cp * t);
            int id = raid.world.get(bx, by, bz);
            if (id == 0) continue;
            absentialis.world.Block b = absentialis.world.Block.ALL[id];
            if (b.mat != absentialis.world.Material.GLASS) { if (b.solid()) return false; continue; }
            Doors.Door d = b.is(absentialis.world.Block.DOOR) ? raid.doors.at(bx, by, bz) : null;
            if (d != null) raid.doors.hit(d, bx + 0.5f, by + 0.5f, bz + 0.5f, 200, fx, fz, p.id, "melee");
            else { raid.breakDX = fx; raid.breakDZ = fz; raid.damageBlock(bx, by, bz, 200, p.id); raid.breakDX = raid.breakDZ = 0; }
            punchesLanded++; punchedGlass++;
            c.sfx("punch", 0.7f, 0.9f);
            shake = Math.max(shake, 0.3f);
            // bare knuckles through a pane: it cuts
            if (raid.rng.chance(0.35)) { raid.hitKind = "blunt"; raid.damageActor(p, null, 3f, null, "arms", p.x, p.y + 1.2f, p.z); raid.hitKind = ""; }
            return true;
        }
        return false;
    }
    public int punchedGlass;

    private void fire(Input in, float dt) {
        ItemInstance h = held();
        raid.player.weapon = h;
        if ("throw".equals(heldSlot)) { throwControl(in, dt); return; }
        // 1.1: with nothing in the hands, the fire button is a punch
        if (h == null) {
            if (switchT > 0 || raid.body.using != null || raid.player.revivingId != 0 || raid.player.downed()) return;
            boolean press = in.pressed("fire");
            if (fireGate) { if (!in.down("fire") && !press) fireGate = false; press = false; }
            if (press) punch();
            return;
        }
        if (h == null || switchT > 0 || raid.body.using != null) return;
        if (raid.player.revivingId != 0 || raid.player.downed() && !"sidearm".equals(heldSlot)) return;
        ItemDef.Weapon w = h.def().weapon;
        if (w == null) return;
        boolean press = in.pressed("fire"), down = in.down("fire");
        // the click that closed a menu is not a shot: nothing fires until the button has come up again
        if (fireGate) { if (!down && !press) fireGate = false; press = false; down = false; burstLeft = 0; }
        if (w.melee()) { if (press && meleeT <= 0) melee(h); return; }
        if (reloadT > 0) {
            if (press && "internal".equals(reloadKind)) cancelReload(); // interrupt shell-by-shell loading
            return;
        }
        // sprint-to-fire: the weapon is down while you run and takes a moment to come back up
        if (raid.player.sprinting) { stfT = Gunsmith.stats(h, 0).sprintToFire; return; }
        if (stfT > 0) return;
        String mode = h.fireModeName();
        boolean want = switch (mode) { case "auto" -> down; case "burst" -> press || burstLeft > 0; default -> press; };
        if (!want || fireTimer > 0 || cycleT > 0) return;
        if (h.jammed) { if (press) { c.sfx("jam", 0.6f); hud.message("Weapon jammed - press [R] to clear", Ui.RED, raid.time); } burstLeft = 0; return; }
        if (!WeaponService.canFire(h)) {
            if (press) { c.sfx("dry_fire", 0.6f); hud.ammoShown = raid.time; }
            burstLeft = 0;
            return;
        }
        Gunsmith.Stats st = Gunsmith.stats(h, lo().charm("wear"));
        ItemDef ammo = WeaponService.fire(h, st, raid.environmentWear(), raid.rng);
        if (ammo == null) return;
        if (lo().charm("infinite_ammo") > 0) { h.rounds++; h.ammoId = ammo.id; }   // Cat Ears
        if ("burst".equals(mode)) burstLeft = burstLeft > 0 ? burstLeft - 1 : 2;
        Actor p = raid.player;
        float pitch = p.pitch + recoilPitch, yaw = p.yaw + recoilYaw;
        float dx = (float) (-Math.sin(yaw) * Math.cos(pitch)), dy = (float) Math.sin(pitch), dz = (float) (-Math.cos(yaw) * Math.cos(pitch));
        if (camThird && !p.dead) {
            // third person: the camera is over the shoulder, the muzzle is not. Find what the crosshair is on - the first
            // thing the camera's line meets beyond the body - and send the round from the head to that point.
            float s0 = tpsDistance * 0.9f + 0.4f, sx = camPX + dx * s0, sy = camPY + dy * s0, sz = camPZ + dz * s0, t = 240;
            int[] hb = Physics.ray(raid.world, sx, sy, sz, dx, dy, dz, t, false);
            if (hb != null) { float bx = hb[0] + 0.5f - sx, by = hb[1] + 0.5f - sy, bz = hb[2] + 0.5f - sz; t = Math.max(0.5f, (float) Math.sqrt(bx * bx + by * by + bz * bz) - 0.5f); }
            for (Actor a : raid.actors) { if (a.isPlayer || a.dead || Math.abs(a.x - p.x) > 250 || Math.abs(a.z - p.z) > 250) continue; float ta = a.intersect(sx, sy, sz, dx, dy, dz, t); if (ta >= 0 && ta < t) t = ta; }
            t = Math.max(t, 3f);
            float ax = sx + dx * t - eyeX, ay = sy + dy * t - eyeY, az = sz + dz * t - eyeZ, al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            if (al > 0.5f) { dx = ax / al; dy = ay / al; dz = az / al; }
        }
        boolean moving = Math.abs(p.vx) + Math.abs(p.vz) > 0.5f;
        float spread = st.dispersion * (0.35f + (1 - adsT) * 1.6f * st.hipMul) * (moving ? 1 + 0.6f / (adsT > 0.5f ? st.steady : 1) : 1) * (p.crouch ? 0.8f : 1) * (p.onGround ? 1 : 2.5f);
        // a hurt body holds a weapon worse; from the ground, worse again
        spread *= raid.combat.aimError[raid.combat.condition(p.hp, p.maxHp).ordinal()] * (p.downed() ? raid.combat.sidearmError : 1);
        if (p.cover != null && !coverPeek && Math.abs(p.lean) < 0.2f) spread *= 0.9f;      // braced on the cover
        raid.fireProjectiles(p, h, ammo, eyeX + dx * 0.4f, eyeY + dy * 0.4f, eyeZ + dz * 0.4f, dx, dy, dz, spread);
        raid.shotSignature(p, h, st, p.x, p.eyeY(), p.z);
        // recoil: per-platform pattern scaled by focus (recoil control) and ADS
        if (raid.time - lastShot > Gunsmith.recoveryTime(st) + 0.1f) shotIndex = 0;
        float[] k = Gunsmith.recoilKick(h.def(), shotIndex++);
        float mul = raid.body.recoilMul() * (adsT > 0.5f ? 0.75f : 1f) * (p.crouch ? 0.85f : 1f);
        p.pitch = Math.min(1.5f, p.pitch + (float) Math.toRadians(k[0] * 0.55f * mul));
        p.yaw -= (float) Math.toRadians(k[1] * 0.55f * mul);
        recoilPitch += (float) Math.toRadians(k[0] * 0.35f * mul);
        // 0.6: what the shot does to the hands depends on what was fired - the same kick for everything made a shotgun feel like a pistol
        firePunch = absentialis.character.Animator.kickOf(w.family, "bolt".equals(mode) || "pump".equals(mode));
        kick = Math.min(1, kick + 0.3f + w.recoilV * 0.05f + firePunch * 0.12f);
        shake = Math.max(shake, (firePunch - 1f) * 0.9f * c.settings.shake);
        raid.body.focus = Math.max(0, raid.body.focus - w.recoilV * 0.8f);
        raid.body.focusChanged = raid.time;
        lastShot = raid.time;
        fireTimer = 60f / st.rpm;
        if ("bolt".equals(mode) || "pump".equals(mode)) cycleT = 60f / st.rpm;
        raid.muzzleFlash = st.suppressed ? 0.3f : 1;
        p.anim.fire(firePunch);
        hud.ammoShown = raid.time;
        // the round was reported through ShotFired; only a weapon without a recorded profile falls back to its one-shot
        if (!gunLocal) {
            if (c.audio != null) c.audio.play(st.suppressed ? "shot_suppressed" : w.sound, fallbackShotGain(w), 0, 0.95f + raid.rng.nextFloat() * 0.1f);
            if ("bolt".equals(mode)) c.sfx("bolt", 0.5f);
            if ("pump".equals(mode)) c.sfx("pump", 0.5f);
        }
        if (h.jammed) { c.sfx("jam", 0.7f); hud.message("Jammed! (condition " + Math.round(h.condition()) + ") [R] to clear", Ui.RED, raid.time); }
        if (h.condition() < 25 && raid.rng.chance(0.05)) hud.message("Weapon is in critical condition - it needs a workbench", Ui.AMBER, raid.time);
    }

    private void melee(ItemInstance h) {
        meleeT = meleeDur = 60f / h.def().weapon.rpm;
        boolean blade = h.defId.contains("knife") || h.defId.contains("machete") || h.defId.contains("blade");
        c.sfx(blade ? "knife_swing" : "melee_swing", 0.6f);
        raid.player.anim.play("melee", Math.min(0.5f, meleeT));
        Actor p = raid.player;
        float fx = (float) -Math.sin(p.yaw), fz = (float) -Math.cos(p.yaw);
        Actor best = null; float bd = h.def().weapon.meleeRange + 0.4f;
        for (Actor a : raid.nearby(p.x, p.z, 4)) {
            if (a.isPlayer || a.dead) continue;
            float dx = a.x - p.x, dz = a.z - p.z, d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d < bd && (dx * fx + dz * fz) / Math.max(0.01f, d) > 0.6f) { bd = d; best = a; }
        }
        if (best != null) {
            boolean sneak = !"engage".equals(best.state);
            float dmg = h.def().weapon.meleeDamage * (sneak ? 2.5f : 1) * (1 + lo().charm("melee"));
            // 1.1: what the survivor has made of the weapon - and a knife at the top of its Damage track ends a person
            int kd = Gunsmith.tune(h, Gunsmith.KNIFE_DAMAGE);
            dmg *= Gunsmith.knifeDamage(kd);
            if ("melee.knife".equals(h.defId) && Gunsmith.knifeKills(kd) && !best.infected()) dmg = Math.max(dmg, best.hp * 4 + 400);
            // someone who sees it coming may block or step out of it
            float through = absentialis.neuralai.integration.Blows.defend(raid, best, p);
            wearBlade(h, 0.035f * Gunsmith.knifeWear(Gunsmith.tune(h, Gunsmith.KNIFE_DURABILITY)));
            if (through <= 0) { hud.message("They stepped out of it", Ui.TXT_DIM, raid.time); raid.emitSound(p.x, p.y, p.z, h.def().weapon.noise, "melee", p.id, false); return; }
            if (through < 1) hud.message("Blocked!", Ui.AMBER, raid.time);
            raid.meleeKind = "melee.knife".equals(h.defId) ? "blade" : "";
            raid.damageActor(best, p, dmg * through, null, sneak && best.infected() ? "head" : "torso", best.x, best.y + 1.4f, best.z);
            raid.meleeKind = "";
            if (best.id == raid.grabbedBy) raid.grabUntil = -99;
            // a blade goes in or across; anything else lands
            c.sfx(through < 1 ? (blade ? "knife_hit" : "melee_block") : blade ? (sneak ? "knife_stab" : "knife_slice") : h.def().weapon.meleeDamage < 30 ? "punch" : "melee_hit", 0.7f);
            raid.emitSound(p.x, p.y, p.z, h.def().weapon.noise, "melee", p.id, false);
            return;
        }
        int[] hit = Physics.ray(raid.world, eyeX, eyeY, eyeZ, fx * (float) Math.cos(p.pitch), (float) Math.sin(p.pitch), fz * (float) Math.cos(p.pitch), 2.2f, false);
        if (hit != null) {
            Block hb = raid.world.block(hit[0], hit[1], hit[2]);
            // a knife opens crates; a crowbar opens most things
            float mul = "melee.crowbar".equals(h.defId) ? 2 : hb.is(Block.CONTAINER) && hb.mat == absentialis.world.Material.WOOD ? 1.4f : 0.5f;
            raid.damageBlock(hit[0], hit[1], hit[2], h.def().weapon.meleeDamage * mul, p.id);
            c.sfx("impact_wood", 0.5f);
            wearBlade(h, 0.02f);
        }
    }

    /** Knives have limited durability: every strike dulls the edge until it snaps. */
    private void wearBlade(ItemInstance h, float amount) {
        if (!"melee.knife".equals(h.defId)) return;
        h.durability -= amount;
        if (h.durability > 0) { if (h.durability < 0.2f && h.durability + amount >= 0.2f) hud.message("Your knife is about to break", Ui.AMBER, raid.time); return; }
        InventoryService.release(h);
        hud.message("Your knife broke", Ui.RED, raid.time);
        c.sfx("glass_break", 0.4f, 1.6f);
        for (String s : new String[]{"primary", "sidearm"}) if (lo().slot(s) != null) { heldSlot = s; break; }
        raid.player.weapon = held();
    }

    /** How long the rack after a full reload of a pump shotgun takes, and how many have been done (gates). */
    public static final float PUMP_TIME = 0.55f;
    public int pumpsAfterReload;

    private void reload() {
        ItemInstance h = held();
        if (h == null || h.def().weapon == null || h.def().weapon.melee() || reloadT > 0) return;
        ItemDef.Weapon w = h.def().weapon;
        if (h.jammed) { reloadKind = "jam"; reloadT = reloadMax = 1.2f; reloadCue = 0; raid.player.anim.play("clear_jam", 1.2f); return; }
        if (w.internal()) {
            if (h.rounds >= h.capacity()) return;
            ItemDef am = absentialis.gameplay.items.Content.ammoFor(w.caliber);
            // 0.7: no buzzer for an empty pouch - the message says it, and nothing is played
            if (findAmmo(w.caliber, null) == null) { hud.message("No " + (am == null ? w.caliber : am.name) + " in the case", Ui.RED, raid.time); return; }
            float rt = Gunsmith.stats(h, 0).reloadTime;
            reloadCue = 0;
            hud.ammoShown = raid.time;
            if ("single".equals(w.reloadStyle)) {
                reloadKind = "internal"; reloadT = reloadMax = rt; reloadEmpty = h.rounds == 0;
                raid.player.anim.play("reload_shell", reloadMax);
            } else {
                // a round still in the weapon = quick tactical change; run dry = full reload with a rack
                reloadEmpty = h.rounds == 0;
                reloadKind = "magfill"; reloadT = reloadMax = rt * (reloadEmpty ? 1.15f : 0.85f);
                raid.player.anim.play(reloadEmpty ? "reload_empty" : "reload_tac", reloadMax);
            }
            return;
        }
        boolean speed = raid.time - lastRTime < 0.35f;
        lastRTime = raid.time;
        ItemInstance best = null;
        for (ItemInstance i : lo().caseGrid.items) if (i.def().magazine != null && i.def().magazine.magType.equals(w.magType) && i.rounds > 0 && (best == null || i.rounds > best.rounds)) best = i;
        if (best == null) {
            if (!h.chambered && h.mag != null && h.mag.rounds > 0) { reloadKind = "rack"; reloadT = reloadMax = 0.6f; reloadCue = 0; raid.player.anim.play("clear_jam", 0.6f); return; }
            hud.message("No loaded " + w.magType.toUpperCase() + " magazines - load one in the inventory", Ui.RED, raid.time);
            return;
        }
        if (h.mag != null && h.mag.rounds >= best.rounds && h.chambered) { hud.message("Current magazine is fuller", Ui.TXT_DIM, raid.time); return; }
        reloadMag = best;
        reloadKind = speed ? "speed" : "mag";
        reloadEmpty = !h.chambered;
        reloadCue = 0;
        reloadT = reloadMax = Gunsmith.stats(h, 0).reloadTime * (h.chambered ? 0.8f : 1.15f) * (speed ? 0.75f : 1);
        // bullets fired decide the animation: a round still chambered = tactical, run dry = full reload with a rack
        raid.player.anim.play(reloadEmpty ? "reload_empty" : "reload_tac", reloadMax);
        hud.ammoShown = raid.time;
        hud.message(h.chambered ? "Tactical reload (round kept in chamber)" + (speed ? " - dropping mag" : "") : "Empty reload - racking the action", Ui.TXT_DIM, raid.time);
    }

    private ItemInstance findAmmo(String caliber, String prefer) {
        ItemInstance best = null;
        for (ItemInstance i : lo().caseGrid.items) if (i.def().ammo != null && i.def().ammo.caliber.equals(caliber) && (prefer == null || prefer.equals(i.defId))) { best = i; break; }
        return best;
    }

    private void weaponTimers(float dt) {
        fireTimer -= dt; cycleT -= dt; switchT -= dt; meleeT -= dt; stfT -= dt;
        if (punchT > 0) {
            punchT -= dt;
            if (!punchLanded && 1 - punchT / Math.max(0.01f, punchDur) >= PUNCH_CONTACT) punchContact();
            raid.playerVulnerableUntil = Math.max(raid.playerVulnerableUntil, raid.time + 0.12f);
        }
        kick = Math.max(0, kick - dt * (6.6f - Math.min(2.6f, firePunch)));      // a heavy weapon takes longer to come back down
        recoilPitch *= Math.max(0, 1 - dt * 9);
        recoilYaw *= Math.max(0, 1 - dt * 9);
        if (reloadT <= 0) return;
        ItemInstance h = held();
        if (h == null) { cancelReload(); return; }
        reloadT -= dt;
        hud.ammoShown = raid.time;
        reloadFoley(1 - Math.max(0, reloadT) / Math.max(0.01f, reloadMax));
        if (reloadT > 0) return;
        switch (reloadKind) {
            case "jam" -> { WeaponService.clearJam(h); hud.message("Jam cleared", Ui.TXT, raid.time); }
            case "rack" -> WeaponService.chamberFromMag(h);
            case "internal" -> {
                ItemInstance ammo = findAmmo(h.def().weapon.caliber, null);
                if (ammo != null && WeaponService.loadOne(h, ammo)) {
                    if (h.rounds < h.capacity() && findAmmo(h.def().weapon.caliber, null) != null) { reloadT = reloadMax; reloadCue = 0; raid.player.anim.play("reload_shell", reloadMax); return; }
                }
                // 0.6: a pump gun loaded to the brim (or loaded from empty) is racked before it is ready - the fore-end
                // comes back and goes forward, and the weapon cannot fire until it has
                if ("pump".equals(h.fireModeName()) && (h.rounds >= h.capacity() || reloadEmpty) && h.rounds > 0) {
                    reloadKind = "pumpfull"; reloadT = reloadMax = PUMP_TIME; reloadCue = 0; reloadEmpty = false; pumpsAfterReload++;
                    raid.player.anim.play("pump", PUMP_TIME);
                    return;
                }
            }
            case "pumpfull" -> { }
            case "magfill" -> {
                List<ItemInstance> stacks = new ArrayList<>();
                for (ItemInstance i : lo().caseGrid.items) if (i.def().ammo != null) stacks.add(i);
                WeaponService.fill(h, stacks);
            }
            default -> {
                if (reloadMag != null && reloadMag.container == lo().caseGrid) {
                    WeaponService.Reload r = WeaponService.reloadMag(h, reloadMag, lo().caseGrid, 0, "speed".equals(reloadKind));
                    if (r.oldMag() != null) { raid.dropItem(r.oldMag(), raid.player.x, raid.player.y + 0.3f, raid.player.z); hud.message("Dropped magazine (" + r.oldMag().rounds + " rds) - it's on the ground", Ui.TXT_DIM, raid.time); }
                }
            }
        }
        cancelReload();
    }

    // ------------------------------------------------------------------ items
    private void useItem(ItemInstance it) {
        if (it.def().throwable != null) { equipThrowable(it); return; }
        if (it.def().weapon != null) { drawFromCase(it); return; }
        String r = panel.act(c.profile, it, "use", lo().caseGrid);
        if (r != null) hud.message(r, Ui.TXT, raid.time);
        if (raid.body.using != null) raid.player.anim.play("heal", Math.max(0.6f, raid.body.useTimer));
    }

    /** 1.0: for the scripted runs - the camera to use, and a jump as if the key had been pressed (a pull-up where there is a ledge). */
    public boolean scenarioMantle() { return tryMantle(raid.player); }
    public void scenarioTab(int tab) { panel.tab = tab; }

    /** A weapon chosen from a quick slot or the wheel while it is still in the case: it takes its slot and comes to hand. */
    private void drawFromCase(ItemInstance it) {
        String slot = it.def().slot;
        if (slot == null || !(slot.equals("primary") || slot.equals("sidearm") || slot.equals("melee"))) { hud.message(it.def().name + " cannot be drawn", Ui.TXT_DIM, raid.time); return; }
        cancelReload();
        InventoryService.Result r = InventoryService.equip(it, lo(), slot);
        if (!r.ok()) { hud.message(r.msg(), Ui.RED, raid.time); return; }
        heldSlot = "";
        switchTo(slot);
    }

    private void useTimer(float dt) {
        if (raid.body.using == null) return;
        raid.body.useTimer -= dt;
        if (raid.body.useTimer > 0) return;
        ItemInstance it = panel.pendingUse;
        if (it != null && (it.container != null || it.slot != null)) {
            String r = raid.body.apply(raid.player, it.def());
            LoadoutPanel.consumeOne(it);
            c.sfx(it.def().food != null ? (it.def().food.hydration > it.def().food.energy ? "drink" : "eat") : it.def().medical.heal > 30 ? "injector" : "bandage", 0.6f);
            hud.message(it.def().name + ": " + r, Ui.GREEN, raid.time);
        }
        raid.body.using = null;
        panel.pendingUse = null;
    }

    /** Lets go of a throwable (or, with a launcher under the barrel, fires it): power 0..1 scales how far it goes. */
    private void throwIt(ItemInstance it, float power) {
        Actor p = raid.player;
        float dx = (float) (-Math.sin(p.yaw) * Math.cos(p.pitch)), dy = (float) Math.sin(p.pitch), dz = (float) (-Math.cos(p.yaw) * Math.cos(p.pitch));
        ItemInstance one = it;
        String name = it.def().name;
        ItemInstance hw = held();
        boolean launch = hw != null && hw.def().weapon != null && !hw.def().weapon.melee() && Gunsmith.stats(hw, 0).launcher && it.def().throwable != null && !"noise".equals(it.def().throwable.kind) && !"flare".equals(it.def().throwable.kind);
        InventoryService.consume(it, 1);
        if (launch) { raid.throwItem(one, dx, dy, dz, 2.3f, true); kick = 1; c.sfx("shot_shotgun", 0.5f, 0.6f); hud.message("Launched " + name, Ui.TXT_DIM, raid.time); raid.player.anim.fire(); return; }
        raid.throwItem(one, dx, dy, dz, power, false);
        raid.player.anim.play("throw", 0.6f);
        c.sfx("throw", 0.5f);
        hud.message("Threw " + name, Ui.TXT_DIM, raid.time);
    }

    private void commitWheel() {
        int s = wheel.selected;
        if (s < 0) return;
        ItemInstance[] slots = wheel.slots(lo());
        // 1.1: an empty weapon slot is a choice too - it puts away whatever is in the hands
        if (slots[s] == null) { if (s <= 2 && !raid.player.downed()) unequip(); return; }
        switch (s) {
            case 0 -> switchTo("primary");
            case 1 -> switchTo("sidearm");
            case 2 -> switchTo("melee");
            case 3 -> equipThrowable(slots[3]);
            default -> useItem(slots[s]);
        }
    }

    /** What [F] would do right now (for the HUD prompt). */
    private String interactPrompt() {
        Actor p = raid.player;
        // 0.9: a knife, and somebody's back within reach
        if (raid.takedown != null) return null;
        if (raid.takedownTarget() != null) return "[F] Silent takedown";
        if (raid.chains != null && raid.chains.near() != null && raid.extortion() == null) return raid.chains.prompt();
        // close enough, with a knife, but not possible: said only to someone who is creeping (so a trader faced in passing is not met with advice on stabbing him)
        if (!raid.takedownWhy.isEmpty() && p.crouch && raid.extortion() == null) return "Takedown: " + raid.takedownWhy;
        if (raid.extortion() != null) return "[F] Pay " + absentialis.Version.coins(Raid.EXTORT_PRICE) + "   -   or raise your weapon";
        String car = raid.vehicles.prompt();
        if (car != null && raid.nearestItem(p.x, p.y + 0.3f, p.z, 1.2f) == null) return car;
        if (raid.lonerInNeed(2.6f) != null) return "[F] Give a bandage";
        Actor bitten = raid.infection.patient(2.8f);
        if (bitten != null) return "[F] Give Antiviral Medicine to " + (bitten.name.isEmpty() ? "them" : absentialis.gameplay.squad.Roster.shortName(bitten.name)) + "  (" + InfectionSystem.stage(bitten.infection).toLowerCase() + ")";
        Raid.WorldItem wi = raid.nearestItem(p.x, p.y + 0.3f, p.z, 1.8f);
        if (wi != null) return "[F] Pick up " + wi.it.name();
        Actor patient = raid.casualty.playerPatient();
        if (patient != null) {
            String who = Raid.displayName(patient);
            return patient.vital == absentialis.entity.Vital.REVIVING ? "Reviving " + who + "  " + Math.round(raid.casualty.reviveFraction(patient) * 100) + "%  -  keep holding [F]"
                    : "[F] hold: revive " + who + "  (" + Math.max(0, (int) Math.ceil(patient.bleedLeft)) + "s)";
        }
        // 0.8: what is being looked at comes first. A door or a container under the crosshair is what [F] means, whatever is
        // lying near it (0.7 always offered the body, so a body by a door meant the door could not be worked at all)
        int[] aimed = rayInteract();
        Block aimedBlock = aimed == null ? null : raid.world.block(aimed[0], aimed[1], aimed[2]);
        boolean aimedUse = aimedBlock != null && (aimedBlock.is(Block.DOOR) || aimedBlock.is(Block.CONTAINER)) && !lookingDownAtBody();
        Actor corpse = aimedUse ? null : raid.searchableCorpse(1.8f);
        if (corpse != null) return "[F] Search " + corpse.corpse.label;
        Actor partner = raid.tradePartner(2.6f);
        if (partner != null && rayInteract() == null) return (partner.mate() ? "[F] Trade kit with " : "[F] Barter with ") + (partner.name == null || partner.name.isEmpty() ? Raid.displayName(partner) : absentialis.gameplay.squad.Roster.shortName(partner.name));
        if (boardSpot() != null) return raid.profile.loadout.count("mat.plank") > 0 ? "[F] Board up the opening (Wooden Plank)" : "An opening - a Wooden Plank would board it up";
        int[] hit = rayInteract();
        if (hit != null) {
            Block b = raid.world.block(hit[0], hit[1], hit[2]);
            if (b.is(Block.DOOR)) {
                Doors.Door dr = raid.doors.at(hit[0], hit[1], hit[2]);
                if (dr == null) return null;
                return switch (raid.doors.state(dr)) { case LOCKED -> "Locked  -  [F] force it (loud)"; case OPEN -> "[F] Close door"; case DESTROYED -> null; default -> dr.latchBroken ? "[F] Push door" : "[F] Open door"; };
            }
            if (b.is(Block.CONTAINER)) return raid.isLocked(hit[0], hit[1], hit[2]) ? "[F] Unlock drawer (Small Key)" : "[F] Search " + b.name.replace('_', ' ');
            if (b.id == Block.TRANSFORMER) return "[F] Repair substation (2x Electrical Parts)";
            if (b.id == Block.BARRICADE) return "Barricade - break it with a crowbar or explosives";
        }
        return null;
    }

    /** The player is looking steeply down at a body at their feet: that, and not the door beyond it, is what they mean. */
    private boolean lookingDownAtBody() {
        Actor p = raid.player;
        if (p.pitch > -0.75f) return false;
        Actor c = raid.searchableCorpse(1.8f);
        if (c == null) return false;
        float dx = c.x - p.x, dz = c.z - p.z, d = (float) Math.sqrt(dx * dx + dz * dz);
        return d < 0.6f || (dx * (float) -Math.sin(p.yaw) + dz * (float) -Math.cos(p.yaw)) / d > 0.6f;
    }

    private int[] rayInteract() {
        Actor p = raid.player;
        float dx = (float) (-Math.sin(p.yaw) * Math.cos(p.pitch)), dy = (float) Math.sin(p.pitch), dz = (float) (-Math.cos(p.yaw) * Math.cos(p.pitch));
        return Physics.ray(raid.world, eyeX, eyeY, eyeZ, dx, dy, dz, 3.0f, true);
    }

    /** The one-block opening the player is looking at and could board up (0.7), or null. */
    private int[] boardSpot() {
        Actor p = raid.player;
        float dx = (float) (-Math.sin(p.yaw) * Math.cos(p.pitch)), dy = (float) Math.sin(p.pitch), dz = (float) (-Math.cos(p.yaw) * Math.cos(p.pitch));
        return raid.barricadeSpot(eyeX, eyeY, eyeZ, dx, dy, dz);
    }

    /** [F]: the reach animation plays only when there is something in reach to act on. */
    private void interact() {
        // 1.0: an advisor is spoken to before anything else that [F] could mean where they stand
        if (raid.takedownTarget() == null && raid.extortion() == null && raid.chains != null && raid.chains.near() != null) { raid.chains.talk(raid.chains.near(), popup); c.sfx("ui_click", 0.4f); return; }
        if (tryInteract()) raid.player.anim.play("interact", 0.7f);
    }

    /** Performs whatever [F] means here. False when there was nothing to interact with (no animation, no sound). */
    private boolean tryInteract() {
        if (raid.takedown != null) return false;
        if (raid.startTakedown()) { adsT = 0; return false; }       // the takedown is its own movement: not the reach of an ordinary interaction
        Actor p = raid.player;
        if (raid.extortion() != null) { String r = raid.payExtortion(); hud.message(r, r.startsWith("Paid") ? Ui.AMBER : Ui.RED, raid.time); c.sfx(r.startsWith("Paid") ? "ui_buy" : "ui_error", 0.5f); return true; }
        Actor needy = raid.lonerInNeed(2.6f);
        if (needy != null) { String r = raid.helpLoner(needy); hud.message(r, r.startsWith("You have") ? Ui.RED : Ui.GREEN, raid.time); c.sfx("bandage", 0.5f); return true; }
        Actor bitten = raid.infection.patient(2.8f);
        if (bitten != null) { String r = raid.infection.give(bitten); hud.message(r, r.startsWith("You have") ? Ui.RED : Ui.GREEN, raid.time); c.sfx(r.startsWith("You have") ? "ui_invalid" : "antiviral", 0.5f); return true; }
        Raid.WorldItem wi = raid.nearestItem(p.x, p.y + 0.3f, p.z, 1.8f);
        if (wi != null) {
            InventoryService.Result r = InventoryService.store(wi.it, lo());
            if (r.ok()) { raid.items.remove(wi); c.sfx(pickupSound(wi.it), 0.5f); hud.message("Picked up " + wi.it.name(), Ui.TXT, raid.time); }
            else hud.message(r.msg(), Ui.RED, raid.time);
            return true;
        }
        int[] aimed = rayInteract();
        Block aimedBlock = aimed == null ? null : raid.world.block(aimed[0], aimed[1], aimed[2]);
        boolean aimedUse = aimedBlock != null && (aimedBlock.is(Block.DOOR) || aimedBlock.is(Block.CONTAINER)) && !lookingDownAtBody();
        Actor corpse = aimedUse ? null : raid.searchableCorpse(1.8f);
        if (corpse != null) { openInventory(corpse.corpse, corpse.corpse.label); lootBody = corpse; lootHadItems = !Raid.onBody(corpse).isEmpty(); return true; }
        Actor partner = raid.tradePartner(2.6f);
        if (partner != null && rayInteract() == null) { trade.open(raid, lo(), partner); cancelReload(); c.sfx("ui_click", 0.4f); return true; }
        int[] gap = boardSpot();
        if (gap != null) { String r = raid.barricade(gap); boolean ok = r.startsWith("Boarded"); hud.message(r, ok ? Ui.GREEN : Ui.AMBER, raid.time); c.sfx(ok ? "hammer" : "ui_invalid", 0.5f); return ok; }
        int[] hit = rayInteract();
        if (hit == null || raid.vehicles.nearby() != null && !raid.world.block(hit[0], hit[1], hit[2]).is(Block.DOOR) && !raid.world.block(hit[0], hit[1], hit[2]).is(Block.CONTAINER)) {
            // nothing else in reach but a car: get in
            if (raid.vehicles.enter()) { cancelReload(); c.sfx("car_door_open", 0.5f); return true; }
            if (hit == null) return false;
        }
        Block b = raid.world.block(hit[0], hit[1], hit[2]);
        if (b.is(Block.DOOR)) {
            Doors.Door dr = raid.doors.at(hit[0], hit[1], hit[2]);
            if (dr == null) return false;
            if (raid.doors.use(dr, p) != null) {
                // locked: a boot against the latch - it takes a few, and everyone in earshot hears each one
                boolean gave = raid.doors.kick(dr, p);
                raid.playerVulnerableUntil = Math.max(raid.playerVulnerableUntil, raid.time + 0.5f);
                hud.message(gave ? "The latch gives" : "Locked - it holds", gave ? Ui.GREEN : Ui.AMBER, raid.time);
            }
            return true;
        }
        if (b.is(Block.CONTAINER) && raid.isLocked(hit[0], hit[1], hit[2]) && !raid.unlock(hit[0], hit[1], hit[2])) { hud.message("Locked. A Small Key opens it.", Ui.AMBER, raid.time); c.sfx("ui_invalid", 0.5f); return true; }
        if (b.is(Block.CONTAINER)) { Container box = raid.openContainer(hit[0], hit[1], hit[2]); openInventory(box, box.label); if (c.audio != null) c.audio.playAt("search", hit[0] + 0.5f, hit[1] + 0.5f, hit[2] + 0.5f, 0.6f, 20, 1); return true; }
        if (b.id == Block.TRANSFORMER) { hud.message(raid.repairSite(hit[0], hit[1], hit[2]), Ui.AMBER, raid.time); return true; }
        return false;
    }

    /** Automation: opens the nearest searchable container within r blocks. */
    public boolean debugOpenNearestContainer(int r) {
        Actor p = raid.player;
        int px = (int) p.x, py = (int) p.y, pz = (int) p.z;
        for (int d = 1; d <= r; d++) for (int x = px - d; x <= px + d; x++) for (int z = pz - d; z <= pz + d; z++) for (int y = py - 2; y <= py + 3; y++) {
            Block b = raid.world.block(x, y, z);
            if (b != null && b.is(Block.CONTAINER)) { Container box = raid.openContainer(x, y, z); openInventory(box, box.label); return true; }
        }
        return false;
    }

    private boolean inspectArmed, inspectWait;
    /** The open container held something when it was opened; once the last item leaves it, the search closes itself. */
    private boolean lootHadItems;
    private float lootEmptyT;

    /** Closes the search view a beat after the container or body has been emptied (never mid-drag). */
    private boolean wasInCar, viewBeforeCar;

    private void autoCloseLoot(float dt) {
        if (!invOpen || lootSide == null || !lootHadItems) return;
        if (!lootSide.items.isEmpty() || c.inv.drag != null || lootBody != null && !Raid.onBody(lootBody).isEmpty()) { lootEmptyT = 0; return; }
        lootEmptyT += dt;
        if (lootEmptyT < 0.22f) return;
        String what = lootSide.label == null || lootSide.label.isEmpty() ? "Container" : lootSide.label;
        closeInventory();
        c.sfx("zipper", 0.35f, 1.15f);
        hud.message(InventoryUi.cap(what) + " emptied", Ui.TXT_DIM, raid.time);
    }

    /** Seconds [F] has been down in the open menu, and how long a press may last and still count as a tap. */
    private float fHeld;
    private static final float F_TAP = 0.22f;

    /** What picking a thing up sounds like: keys, medicine and valuables have a sound of their own. */
    static String pickupSound(ItemInstance it) {
        String cat = it.def().category == null ? "" : it.def().category, id = it.defId;
        if (cat.equals("key") || id.startsWith("key.")) return "pickup_key";
        if (it.def().medical != null || id.startsWith("med.")) return "pickup_medical";
        if (cat.equals("treasure") || cat.equals("valuable") || id.startsWith("treasure.") || id.startsWith("spinel")) return "pickup_money";
        if (cat.equals("document")) return "file_scroll";
        return "pickup";
    }

    private void openInventory(Container loot, String title) {
        invOpen = true;
        c.sfx(loot != null ? "container_open" : "inv_select", 0.5f);
        fHeld = 0;
        inspectArmed = false;
        inspectWait = true;
        c.inv.inspected = null;
        c.inv.selected = null;
        c.inv.inspectOpen = null;
        lootSide = loot;
        lootBody = null;
        lootTitle = title;
        lootHadItems = loot != null && !loot.items.isEmpty();
        lootEmptyT = 0;
        c.inv.quickA = loot;
        c.inv.stash = null;
        c.inv.quickB = lo().caseGrid;
        c.inv.owner = lo();
        c.inv.prog = c.profile.prog;
        c.inv.allowDrop = true;
        // 1.3: the discard case puts what is dropped into it down on the ground, all of it at once
        c.inv.discardVerb = "Drop"; c.inv.discardConfirm = false;
        c.inv.onDiscard = items -> { for (ItemInstance it : items) raid.dropItem(it, raid.player.x, raid.player.y + 0.5f, raid.player.z); hud.message("Dropped " + items.size() + (items.size() == 1 ? " thing" : " things"), Ui.TXT_DIM, raid.time); };
        c.inv.extraActions.clear();
        c.inv.extraActions.add("quick");
        c.inv.onAction = (it, act) -> { String r = panel.act(c.profile, it, act, lootSide); if (r != null) { c.inv.say(r, Ui.TXT); if (act.equals("use")) closeInventory(); } };
        c.sfx("zipper", 0.4f);
    }

    /** 1.0: for the scripted runs - the inventory, alone (0), with a container that has things in it (1), or with an empty one (2); -1 shuts it. */
    public void scenarioInventory(int mode) {
        if (mode < 0) { if (invOpen) closeInventory(); return; }
        Container k = null;
        if (mode > 0) {
            k = new Container("Locker", 6, 5);
            if (mode == 1) for (String id : new String[]{"med.bandage", "ammo.556", "med.painkillers", "med.spray"}) { absentialis.gameplay.items.ItemDef d = absentialis.gameplay.items.Content.def(id); if (d != null) InventoryService.add(new ItemInstance(id), k); }
        }
        openInventory(k, k == null ? null : "Locker");
    }

    private void closeInventory() {
        c.sfx(lootSide != null ? "container_close" : "inv_close", 0.45f);
        invOpen = false;
        lootSide = null;
        lootHadItems = false;
        c.inv.drag = null;
        c.inv.menu.clear();
        raid.applyArmor();
        raid.player.weapon = held();
        if ("throw".equals(heldSlot)) { if (currentThrow() == null) stowThrowable(); }
        else if (held() == null) for (String s : new String[]{"primary", "sidearm", "melee"}) if (lo().slot(s) != null) { heldSlot = s; break; }
    }

    // ================================================================== render
    private final Mat4 invView = new Mat4();
    private float camYaw, camPitch, fovNow = 70;
    private boolean camThird;
    /** 1.1: how much wider the picture is for a sprint or a slide right now (degrees, eased). */
    private float sprintFov;
    public float sprintFovNow() { return sprintFov; }
    /** Third person: where the camera is, the point its boom hangs from (smoothed), the shoulder offset in use, and the framing offsets in play. */
    private float camPX, camPY, camPZ, tpsAX, tpsAY, tpsAZ, tpsShoulderNow, frameD, frameS, frameH, frameF;
    private boolean tpsTracking;
    /** 0.9: which shoulder the third-person camera is over (1 right, -1 left, eased between), which it is going to, and when that last changed. */
    private float tpsSide = 1, tpsSideWant = 1, tpsSideAt = -9;
    /** Boom length with the sights up (metres). */
    public static final float TPS_AIM_DISTANCE = 1.45f;
    /** Aiming, the camera is at least this far out to the right of the head (metres), and this much higher: the body is in the left third of the picture and the middle is clear. */
    // (1.0: aiming, the camera sits a little lower than it did - at the shoulder, looking along the weapon, not down onto it)
    public static float TPS_AIM_SHOULDER = 0.90f, TPS_AIM_RISE = 0.01f;
    /** 1.0: the third-person camera stands this much nearer than the setting says (the setting is kept; the picture is closer). */
    public static float TPS_CLOSER = 0.88f;
    /** 1.0: in cover, how far the camera moves along the wall toward its open end, and how far out to that side it swings, to see round the corner. */
    public static float COVER_CAM_SLIDE = 0.55f, COVER_CAM_SHOULDER = 0.95f;
    private float coverCamX, coverCamZ, clipK = 1;
    /** How far along its boom the camera was allowed to go last frame, 0..1 (gates: 1 in the open). */
    public float cameraReach() { return clipK; }
    /** How far to the side a full lean carries the eye (metres). */
    public static final float LEAN_METRES = 0.45f;
    /** The lean the body has taken by itself this frame to aim round what is in front of it (-1 left, 0 none, 1 right). */
    public float autoLean;
    /** How short the boom has to be squeezed (by a wall behind) before the body starts to fade, and where it is at its faintest. */
    public static final float TPS_FADE_FROM = 0.80f, TPS_FADE_TO = 0.42f;

    /**
     * Dynamic framing: what the body is doing changes where the camera wants to be - {distance, shoulder, height, field
     * of view} offsets from the standing frame, eased so the picture never jumps. A slide drops and widens it; working
     * on a casualty or lying wounded pulls it up and out; using something or working a door brings it in over the hands.
     */
    /** 1.0: half the size of the box the third-person camera has to fit in all the way out along its boom (metres). */
    public static final float CAM_BODY = 0.17f;

    private void frame(Actor p, float dt) {
        String act = p.anim == null ? "" : p.anim.action;
        float d = 0, s = 0, h = 0, f = 0;
        if (p.downed()) { d = 0.9f; h = 0.55f; s = -0.25f; }
        else if (p.prone) { d = 0.25f; h = -0.05f; s = 0.05f; }
        else if (p.slideT > 0) { d = 0.5f; h = -0.45f; f = 3; }
        else if (p.revivingId != 0) { d = 0.9f; h = 0.35f; s = -0.2f; }
        else if (raid.body.using != null || "heal".equals(act)) { d = -0.7f; s = 0.10f; h = -0.05f; }
        else if ("interact".equals(act) || "car_enter".equals(act) || "car_exit".equals(act)) { d = -0.5f; s = 0.08f; }
        else if (act.startsWith("reload") || "pump".equals(act) || "clear_jam".equals(act)) { d = -0.3f; }
        else if ("melee".equals(act)) { d = 0.35f; f = 3; }
        else if (p.climbing) { d = 0.7f; h = 0.3f; s = -0.3f; }
        else if (p.sprinting) { d = 0.35f; h = -0.04f; }
        else if (p.cover != null) { d = -0.15f; s = 0.12f; h = p.crouch ? 0.05f : 0; }
        else if (p.crouch) { d = -0.2f; h = -0.1f; }
        float k = Math.min(1, dt * 4.5f);
        frameD += (d - frameD) * k; frameS += (s - frameS) * k; frameH += (h - frameH) * k; frameF += (f - frameF) * k;
    }

    public void render(float dt) {
        Actor p = raid.player;
        int W = c.win.width, H = c.win.height;
        glViewport(0, 0, W, H);
        // 0.9, Director Mode's editor: when it is looking at a take from another camera, the people and vehicles of that moment
        // are stood where the take's tape has them for the length of this frame's drawing, and put back after it
        boolean scene = director != null && director.active() && director.session.state == absentialis.director.SessionManager.State.EDITING && director.editor.beginScene(this);
        // camera: first person rides the head bone of the animated body (gait, crouch, step-ups, landings)
        // Director Mode: the controlled character may be somebody - or something - else
        playerLook = lookOverride != null ? lookOverride : ActorRenderer.playerLook(c.profile, playerLook != null && playerLook == lastLookOverride ? null : playerLook);
        lastLookOverride = lookOverride;
        if (playerLook.gear.eyesClosed != raid.player.dead) { playerLook.gear.eyesClosed = raid.player.dead; playerLook.key = absentialis.character.CharBuilder.key(playerLook.app, playerLook.gear); }
        shake = Math.max(shake * Math.max(0, 1 - dt * 5), raid.damageShake * c.settings.shake);
        c.ar.eye(p, playerLook, p.pitch, eye);
        float rx = (float) Math.cos(p.yaw), rz = (float) -Math.sin(p.yaw);
        // 0.8: a lean is a step of the head to one side with the eyes level - the picture no longer tilts with it - and it
        // goes no further than there is room for (leaning at a wall used to put the eye inside the wall)
        float leanShift = lean * LEAN_METRES;
        if (Math.abs(leanShift) > 0.01f) {
            float sg = Math.signum(leanShift);
            for (int k = 0; k < 6; k++) {
                float sx = eye[0] + rx * (leanShift + sg * 0.2f), sz = eye[2] + rz * (leanShift + sg * 0.2f);
                if (!raid.world.solid((int) Math.floor(sx), (int) Math.floor(eye[1]), (int) Math.floor(sz))) break;
                leanShift *= 0.6f;
            }
        }
        eyeX = eye[0] + rx * leanShift;
        eyeY = eye[1] - landDip - Math.abs(leanShift) * 0.06f;
        eyeZ = eye[2] + rz * leanShift;
        float yaw = p.yaw + (raid.rng.nextFloat() - 0.5f) * shake * 0.02f, pitch = p.pitch + (raid.rng.nextFloat() - 0.5f) * shake * 0.02f;
        boolean deathCam = p.dead && p.ragdoll != null;
        // 1.1: the survivor's own body under physics while they live (a long fall): the camera follows it from outside, as it does a death, but is still theirs to turn
        boolean tumbleCam = !p.dead && p.ragdoll != null;
        if (tumbleCam) { eyeX = p.ragdoll.x[absentialis.character.Ragdoll.NECK]; eyeY = p.ragdoll.y[absentialis.character.Ragdoll.NECK] + 0.25f; eyeZ = p.ragdoll.z[absentialis.character.Ragdoll.NECK]; lean = 0; }
        if (deathCam) {
            // you watch yourself fall: the camera leaves the head, looks back at the body from where the killing
            // blow came, then drifts slowly around it while the picture drains
            eyeX = p.ragdoll.x[absentialis.character.Ragdoll.PELVIS]; eyeY = p.ragdoll.y[absentialis.character.Ragdoll.PELVIS] + 0.5f; eyeZ = p.ragdoll.z[absentialis.character.Ragdoll.PELVIS];
            // turned: the camera stays with what you have become
            Actor husk = raid.infection.playerHusk;
            if (husk != null) { eyeX = husk.x; eyeY = husk.y + husk.height() * 0.7f; eyeZ = husk.z; }
            yaw = deathYaw + deadT * 0.16f; pitch = -0.36f - 0.1f * (float) Math.sin(deadT * 0.21f); lean = 0;
        }
        boolean driving = !p.dead && (raid.vehicles.driving != null || raid.vehicles.busy());
        if (raid.vehicles.driving != null && !p.dead && !this.thirdPerson) { raid.vehicles.eye(eye); eyeX = eye[0]; eyeY = eye[1]; eyeZ = eye[2]; }
        // behind the wheel the chase camera is the default; [V] still switches, and the old view returns on getting out
        boolean inCar = raid.vehicles.inside();
        if (inCar != wasInCar) { if (inCar) { viewBeforeCar = this.thirdPerson; this.thirdPerson = true; } else this.thirdPerson = viewBeforeCar; wasInCar = inCar; }
        raid.vehicles.cockpit = inCar && !this.thirdPerson && !deathCam;
        boolean thirdPerson = this.thirdPerson || deathCam || tumbleCam;
        float cx = eyeX, cy = eyeY, cz = eyeZ;
        float fx = (float) (-Math.sin(yaw) * Math.cos(pitch)), fy = (float) Math.sin(pitch), fz = (float) (-Math.cos(yaw) * Math.cos(pitch));
        if (raid.vehicles.driving != null && !p.dead && this.thirdPerson) {
            // chase camera: behind and above the car, looking where the player looks
            float[] vp = raid.vehicles.driving.cameraAnchor();
            eyeX = vp[0]; eyeY = vp[1]; eyeZ = vp[2];
            cx = eyeX; cy = eyeY; cz = eyeZ;
        }
        if (thirdPerson) {
            // 0.6 third-person camera. On foot it sits behind the right shoulder on a boom the mouse swings freely; the
            // point it hangs from follows the head with a little lag (tracking), so steps and stops do not jolt the
            // picture; aiming draws it in to the shoulder; and what the body is doing reframes it (dynamic framing).
            boolean foot = !driving && !deathCam;
            float ax = eyeX, ay = eyeY, az = eyeZ;
            if (foot) {
                float sm = c.settings.tpsSmoothing, gap = (float) Math.sqrt((eyeX - tpsAX) * (eyeX - tpsAX) + (eyeY - tpsAY) * (eyeY - tpsAY) + (eyeZ - tpsAZ) * (eyeZ - tpsAZ));
                if (!tpsTracking || gap > 1.6f) { tpsAX = eyeX; tpsAY = eyeY; tpsAZ = eyeZ; tpsTracking = true; }
                float kxz = Math.min(1, dt * (30 - 20 * sm)), ky = Math.min(1, dt * (22 - 16 * sm));
                tpsAX += (eyeX - tpsAX) * kxz; tpsAZ += (eyeZ - tpsAZ) * kxz; tpsAY += (eyeY - tpsAY) * ky;
                ax = tpsAX; ay = tpsAY; az = tpsAZ;
                frame(p, dt);
            } else { tpsTracking = false; frameD = frameS = frameH = frameF = 0; }
            float d = deathCam ? 4.6f - 0.8f * Math.min(1, deadT / 9f) : driving ? 7.2f : Math.max(0.95f, (c.settings.tpsDistance * TPS_CLOSER + frameD) * (1 - adsK) + TPS_AIM_DISTANCE * adsK);
            // 0.8: aiming, the camera comes in close and stays over the right shoulder - further out to the side, if anything, so that
            // the body is off to the left of the picture and what is in front of it is in the open
            // (0.8, after looking at it: at 1.05 m with the ordinary offset the back of the head sat on the reticle. It is a little
            // further back than that now, and well out to the side.)
            float shoulderHip = Math.max(0, c.settings.tpsShoulder + frameS);
            float shoulder = driving ? 0 : shoulderHip * (1 - adsK) + Math.max(shoulderHip, TPS_AIM_SHOULDER) * adsK;
            float side = driving ? 0 : 0.25f;
            float bx = -fx + rx * side, by = -fy + (driving ? 0.3f : 0.08f), bz = -fz + rz * side, bl = (float) Math.sqrt(bx * bx + by * by + bz * bz);
            int[] hit = Physics.ray(raid.world, ax, ay, az, bx / bl, by / bl, bz / bl, d + 0.4f, false);
            if (hit != null) {
                float hx = hit[0] + 0.5f - ax, hy = hit[1] + 0.5f - ay, hz = hit[2] + 0.5f - az;
                d = Math.max(0.35f, Math.min(d, (float) Math.sqrt(hx * hx + hy * hy + hz * hz) - 0.95f));
            }
            // 0.9, over the shoulder: which shoulder. The right - unless the body is leaning out to the left, or there is a wall at
            // the right shoulder and room at the left one, in which case the camera goes over the left shoulder instead of being
            // crushed in against the back of the head (it used to give way to a quarter of its offset, which put the head on the
            // reticle exactly where cover made aiming matter most). With walls both sides it comes in behind the head.
            boolean wallR = shoulder > 0.05f && Physics.ray(raid.world, ax, ay, az, rx, 0, rz, shoulder + 0.35f, false) != null;
            boolean wallL = shoulder > 0.05f && Physics.ray(raid.world, ax, ay, az, -rx, 0, -rz, shoulder + 0.35f, false) != null;
            float lean = driving ? 0 : p.lean + autoLean * 0.6f;
            float wantSide = lean < -0.25f && !wallL ? -1 : lean > 0.25f && !wallR ? 1 : wallR && !wallL ? -1 : tpsSide < 0 && wallR ? -1 : 1;
            if (wallR && wallL) shoulder *= 0.25f;
            // 1.0, in cover: the camera goes where the looking is. At a wall that ends - a corner, a door jamb, the end of a
            // counter - it slides along the wall toward that end and swings out past it, so the survivor sees round the
            // corner while their body stays behind it. Which end: the one being looked toward. Looking away from the
            // wall, or at a wall with no end in reach, it stays where it was.
            float slideX = 0, slideZ = 0;
            if (foot && p.cover != null && p.cover.valid && !p.prone && Math.abs(lean) < 0.25f) {
                float lx = p.cover.leftX(), lz = p.cover.leftZ(), toward = fx * lx + fz * lz, into = fx * p.cover.dx + fz * p.cover.dz;
                int end = p.cover.leanLeft && (!p.cover.leanRight || toward >= 0) ? 1 : p.cover.leanRight ? -1 : 0;
                if (end != 0 && into > -0.35f) {
                    float ex = lx * end, ez = lz * end, sideOf = ex * rx + ez * rz;
                    slideX = ex * COVER_CAM_SLIDE; slideZ = ez * COVER_CAM_SLIDE;
                    if (Math.abs(sideOf) > 0.25f) { wantSide = sideOf > 0 ? 1 : -1; shoulder = Math.max(shoulder, COVER_CAM_SHOULDER); }
                }
            }
            coverCamX += (slideX - coverCamX) * Math.min(1, dt * 5); coverCamZ += (slideZ - coverCamZ) * Math.min(1, dt * 5);
            // a change of shoulder is held for a moment before the next: a doorway's two jambs do not flick the picture from side to side
            if (wantSide != tpsSideWant && raid.time - tpsSideAt > 0.35f) { tpsSideWant = wantSide; tpsSideAt = raid.time; }
            tpsSide += (tpsSideWant - tpsSide) * Math.min(1, dt * 7);
            tpsDistance += (d - tpsDistance) * Math.min(1, dt * (d < tpsDistance ? 30 : 6));
            tpsShoulderNow += (shoulder * tpsSide - tpsShoulderNow) * Math.min(1, dt * 12);
            float off = driving ? 0 : tpsShoulderNow * Math.min(1, tpsDistance / 0.9f);
            cx = ax - fx * tpsDistance + rx * off + coverCamX; cy = ay - fy * tpsDistance + (driving ? 0.9f : 0.2f + TPS_AIM_RISE * adsK + frameH); cz = az - fz * tpsDistance + rz * off + coverCamZ;
            // 1.0: harder to put through a wall. The boom was tested along its middle line only - the shoulder offset, the
            // rise and the slide in cover were added afterwards, and any of them could carry the lens into a wall, a ceiling
            // or a door frame. The whole way from the head to where the camera wants to be is now walked with a body the
            // size of the lens housing, and the camera stops at the last place that body fits. It comes in at once and
            // goes back out gently, so brushing past a post does not make the picture jump.
            if (foot) {
                float vx = cx - ax, vy = cy - ay, vz = cz - az, vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz), free = 1;
                if (vl > 0.1f) {
                    int steps = Math.max(2, (int) Math.ceil(vl / 0.09f));
                    for (int i = 1; i <= steps; i++) {
                        float t = i / (float) steps;
                        if (!Physics.boxFree(raid.world, ax + vx * t, ay + vy * t - CAM_BODY, az + vz * t, CAM_BODY, CAM_BODY * 2)) { free = Math.max(0.12f, (i - 1) / (float) steps - 0.06f / vl); break; }
                    }
                }
                clipK = free < clipK ? free : clipK + (free - clipK) * Math.min(1, dt * 5);
                cx = ax + vx * clipK; cy = ay + vy * clipK; cz = az + vz * clipK;
            } else clipK = 1;
        } else tpsTracking = false;
        camPX = cx; camPY = cy; camPZ = cz;
        camYaw = yaw; camPitch = pitch; camThird = thirdPerson;
        // (1.1: the wider view of a sprint and of a slide ease in and out - they were switched, and anything that made the sprint
        // stutter made the whole picture pump)
        sprintFov += ((p.sprinting ? 4f : 0f) + (p.slideT > 0 ? 5f : 0f) - sprintFov) * Math.min(1, dt * 5.5f);
        float fov = c.settings.fov / (1 + (zoom() - 1) * adsK) - adsK * 8 + sprintFov + (thirdPerson ? frameF : 0) + (raid.vehicles.driving != null ? Math.min(8, raid.vehicles.driving.speed() * 0.25f) : 0);
        // Director Mode's camera: when it has taken the picture away from the character, the picture is its own
        boolean camFree = camOverride != null && !deathCam;
        if (camFree) {
            cx = camOverride[0]; cy = camOverride[1]; cz = camOverride[2]; yaw = camOverride[3]; pitch = camOverride[4];
            if (camOverride.length > 5 && camOverride[5] > 1) fov = camOverride[5];
            fx = (float) (-Math.sin(yaw) * Math.cos(pitch)); fy = (float) Math.sin(pitch); fz = (float) (-Math.cos(yaw) * Math.cos(pitch));
            camPX = cx; camPY = cy; camPZ = cz; camYaw = yaw; camPitch = pitch;
        }
        camDetached = camFree;
        c.ar.now = raid.time;
        float aspect = W / (float) Math.max(1, H);
        proj.perspective((float) Math.toRadians(fov), aspect, 0.05f, 900f);
        fovNow = fov;
        view.identity().rotateX(-pitch).rotateY(-yaw).translate(-cx, -cy, -cz);
        pv.set(proj).mul(view);
        // 1.0: the fog belongs to where the player is, not to where the camera happens to be looking at them from
        fogOX = camFree ? cx : eyeX; fogOY = camFree ? cy : eyeY; fogOZ = camFree ? cz : eyeZ;
        setupEnv(cx, cy, cz, fx, fy, fz);
        c.wr.scheduleDirty();
        c.wr.uploadResults(24);
        c.wr.viewDistance = 260;
        // the picture drains as you die: colour to black and white, grain rising, over the first seconds on the ground
        deathFx = p.dead ? Math.min(1, Math.max(0, (deadT - 0.6f) / 5.4f)) : Math.max(0, deathFx - dt * 2);
        boolean hands = !thirdPerson && !invOpen && !p.dead && !driving && !raid.devGhost && !camFree && !photo;   // a camera has no hands
        // 1.0: what is being used is in the hand - in third person too (the weapon is slung while it is)
        p.handItem = raid.body.using != null && !p.dead ? raid.body.using.id : null;
        if (thirdPerson) thirdPersonPorts(p);
        if (c.hr != null) {
            var hr = c.hr;
            var f = hr.frame;
            f.view.set(view); f.proj.set(proj);
            f.vmProj.perspective((float) Math.toRadians(60), aspect, 0.01f, 10f);
            f.camX = cx; f.camY = cy; f.camZ = cz; f.fwdX = fx; f.fwdY = fy; f.fwdZ = fz;
            f.fovY = (float) Math.toRadians(fov); f.aspect = aspect; f.dt = dt; f.env = env; f.viewDistance = 260;
            f.fogX = fogOX; f.fogY = fogOY; f.fogZ = fogOZ;
            f.bloom = 0.05f * (1 - 0.55f * indoorT);
            f.death = deathFx * deathFx * (3 - 2 * deathFx);
            f.hurt = p.dead ? 0 : Math.max(Math.min(1, raid.damageShake * 1.4f), p.hp < 35 ? (35 - p.hp) / 35f * (0.75f + 0.25f * (float) Math.sin(raid.time * 4.5f)) : 0);
            f.infection = p.dead ? 0 : raid.body.infectionFx(raid.time);
            f.bile = raid.bileFx();
            f.flash = raid.flashBlind > 0.01f ? Math.min(1, raid.flashBlind * 1.6f) : 0;
            c.settings.applyGraphics(hr, c.win);
            raid.fire.detail = c.settings.particleDetail;
            hr.scene.clear(); hr.viewmodel.clear(); hr.particles.clear(); hr.clearLights();
            c.mr.record(hr.scene);
            drawModels(dt);
            if (hands) { invView.invert(view); c.mr.record(hr.viewmodel, invView); drawViewmodel(fov, aspect); }
            else if (!thirdPerson) { lensKnown = false; playerPortsKnown = false; }
            c.mr.stop();
            sceneLights(hr, fx, fy, fz);
            // 1.0: one particle system - fire, gun smoke, sparks, debris, rain, snow - submitted in one go; the weather and the
            // wind it blows are the Astratto weather system's, and the foliage and the wet ground take their cue from it
            var wx = raid.weather;
            raid.focusX = cx; raid.focusY = cy; raid.focusZ = cz; raid.focusSet = true;
            raid.fx.detail = Math.max(0.3f, c.settings.particleDetail);
            wx.density = Math.max(0.3f, c.settings.particleDetail);
            raid.fx.submit(hr.particles, cx, cy, cz, view.m[0], view.m[4], view.m[8], view.m[1], view.m[5], view.m[9], 200f);
            raid.fx.lights((lx, ly, lz, lr, cr, cg, cb) -> hr.addPoint(lx, ly, lz, lr, cr, cg, cb, 0), cx, cz, 5);
            env.overcast = wx.overcast; env.wet = wx.wetness; env.snow = wx.snowCover; env.lightning = wx.lightning; env.rain = wx.rain;
            if (hr.foliage != null) {
                var fo = hr.foliage;
                fo.windX = wx.windX; fo.windZ = wx.windZ; fo.gust = wx.gust; fo.time = raid.time; fo.snow = wx.snowCover;
                fo.clearPushers();
                fo.pusher(p.x, p.y, p.z, 0.75f);
                for (Actor a : raid.actors) { if (a.isPlayer || a.dead || a.tier != 0 || a.distXZ(p.x, p.z) > 26) continue; fo.pusher(a.x, a.y, a.z, a.radius + 0.4f); }
            }
            raid.decals = hr.decals; raid.foliage = hr.foliage; raid.props = hr.props;
            if (hr.decals != null) hr.decals.snow = wx.snowCover;
            hr.render(c.wr, W, H);
        } else {
            glClearColor(env.fogR, env.fogG, env.fogB, 1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            float[] f = {fx, fy, fz}, r = {(float) Math.cos(yaw), 0, (float) -Math.sin(yaw)};
            float[] up = {r[1] * fz - r[2] * fy, r[2] * fx - r[0] * fz, r[0] * fy - r[1] * fx};
            c.wr.renderSky(env, f, r, up, (float) Math.tan(Math.toRadians(fov / 2)), aspect);
            c.wr.render(pv, env);
            drawModels(dt);
            if (hands) drawViewmodel(fov, aspect);
            else { lensKnown = false; playerPortsKnown = false; }
        }
        raid.fire.audio(c.audio);
        raid.vehicles.audio(c.audio, dt);
        sawAudio(dt);
        // Director Mode records the picture as it is here: the world, finished, before anything is written over it
        if (director != null && director.active()) director.frameRendered(W, H, dt);
        if (scene) director.editor.endScene(this, W, H);
        // 2D
        Ui ui = c.ui;
        ui.b.begin(W, H);
        ui.begin(dt, W, H);
        boolean deathMenu = p.dead && deadT >= DEATH_MENU_AT;
        ui.showCursor = popup.open || invOpen || paused || mapOpen || c.console.open || deathMenu || trade.open || dev.panel || director != null && director.active() && director.wantsPointer();
        if (c.hr == null) {
            if (raid.muzzleFlash > 0.3f && !thirdPerson) ui.b.rect(0, 0, W, H, Ui.alpha(0x30FFD080, raid.muzzleFlash * 0.3f));
            if (raid.flashBlind > 0.01f) ui.b.rect(0, 0, W, H, Ui.alpha(0xFFFFFFFF, Math.min(1, raid.flashBlind * 1.6f)));
            if (p.dead) ui.b.rect(0, 0, W, H, Ui.alpha(0xFF000000, deathFx * 0.55f));   // no post chain here: a plain dim stands in for the drain
        }
        if (dev.active) { status.nameTags(ui, pv, env.camX, env.camY, env.camZ); if (raid.aiDebug) aiDebug(ui); dev.camera(pv); dev.draw(ui, dt); }
        else if (photo || director != null && director.active() && (director.fullScreen() || hudHidden)) { /* the session draws its own (below); a photograph has nothing on it */ }
        else if (!invOpen && !mapOpen && !p.dead) {
            if (!p.dead) { status.nameTags(ui, pv, env.camX, env.camY, env.camZ); status.bubbles(ui, pv, env.camX, env.camY, env.camZ); }
            // 1.1: the whole HUD is laid out at its own scale (the interface scale, the HUD option, the height of the screen
            // against 1080) inside the safe area. Top centre: experience. Top left: notices, the kill feed under them. Lower
            // right: weapon, ammunition, health. Lower left: what the body runs on, when it is worth showing.
            if (adsT > 0.6f && zoom() > 2) scopeOverlay(ui, W, H);
            else if (adsT > 0.35f && held() != null && held().attachments.get("optic") != null) dotOverlay(ui, W, H, held().attachments.get("optic").defId.endsWith("holo"), Math.min(1, (adsT - 0.35f) / 0.4f));
            hud11.begin(ui);
            float safe = hud11.safe(ui);
            xpFeed.update();
            xpFeed.draw(ui);
            hud.noteTop = safe + ui.s(10); hud.noteLeft = safe + ui.s(18);
            if (!p.dead) { hud11.weapon(ui, held(), reloadT, reloadMax, playerLook, hud.ammoShown, dt); hud11.vitals(ui, dt); }
            hud.warnTop = hud11.weaponBox.y; hud.warnRight = ui.w - safe - ui.s(18);
            hud.draw(raid, held(), reloadT, reloadMax, driving ? null : interactPrompt(), adsT > 0.6f, dt);
            if (!p.dead) status.hitmarker(ui);
            drawMinimap(ui);
            // the kill feed directly under the notices; the squad column under that (never over it)
            status.feedX = hud.noteLeft;
            status.killfeed(ui, hud.noteBottom);
            status.squadMinY = status.feedBox.b() + ui.s(14); status.squadX = hud.noteLeft;
            status.squad(ui, squadSel, new int[]{SquadPanel.GOLD_COL, SquadPanel.RED_COL, SquadPanel.BLUE_COL});
            hud11.end(ui);
            vehicleHud(ui);
            if (raid.aiDebug) aiDebug(ui);
        }
        wheel.draw(ui, lo(), dt);
        if (director != null && director.active()) director.draw(ui, dt);
        if (p.dead) drawDeath(ui);
        if (invOpen) drawInventory(ui, dt);
        if (popup.open) popup.draw(ui, c.in);
        if (trade.open) trade.draw(ui);
        if (mapOpen) drawMap(ui);
        if (paused) drawPause(ui);
        c.console.draw(ui, dt);
        if (c.settings.showFps) {
            ui.text(Math.round(1 / Math.max(1e-4f, dt)) + " fps  " + c.wr.drawnColumns + " cols  actors " + raid.actors.size() + "  fire " + raid.fire.particles() + "  " + absentialis.Version.SHORT, ui.s(8), H - ui.s(20), 12, Ui.TXT_DIM);
            if (c.hr != null) ui.text(c.hr.stats, ui.s(8), H - ui.s(36), 11, Ui.TXT_DIM);
        }
        ui.end();
        ui.b.end();
    }

    // ------------------------------------------------------------------ death sequence
    /** Seconds on the ground before the choice appears; any key after two seconds brings it up early. */
    private static final float DEATH_MENU_AT = 6.5f;
    private float deathFx, deathYaw;
    private boolean deathStarted;
    private absentialis.engine.audio.UltraEngine.Voice deathDrone;
    /** What the player chose once dead: "respawn", "menu" or "quit" (read by GameApp when the raid ends). */
    public String deathChoice;

    /** First frame of being dead: frame the camera on the killer's side of the body, start the sound of it. */
    private void beginDeath() {
        deathStarted = true;
        Actor p = raid.player, k = raid.actor(p.lastAttacker);
        // look across the body toward whoever did it; with nobody to blame, keep the way you were facing
        deathYaw = k != null && k != p ? (float) Math.atan2(-(k.x - p.x), -(k.z - p.z)) : p.yaw;
        invOpen = false; mapOpen = false; paused = false; wheel.open = false;
        c.inv.drag = null;
        c.sfx("death_sting", 0.9f);
        if (c.audio != null && c.audio.available) { deathDrone = c.audio.loop("death_drone", 0.9f, absentialis.engine.audio.UltraEngine.MUSIC); c.audio.duckTo(0.25f, 5f); }
        if (tensionVoice != null) tensionVoice.fadeTo(0, 2);
    }

    /** Resolves the death with the player's choice; the raid then ends on the next update. */
    public void chooseDeath(String choice) {
        if (deathChoice != null) return;
        deathChoice = choice;
        c.sfx("ui_confirm", 0.5f);
        raid.resolveDeath();
    }

    private void drawDeath(Ui ui) {
        float W = ui.w, H = ui.h;
        // 1.0: someone is going through the survivor's kit - said plainly, at the edge of the picture, as it happens
        if (raid.corpseLooter != null || !raid.corpseLootTaken.isEmpty()) {
            String who = raid.corpseLooter != null ? Raid.displayName(raid.corpseLooter) + " is going through your kit" : "Your kit was gone through";
            float lw = Math.max(ui.tw(who, 14), 0) + ui.s(24), ly = H * 0.62f;
            for (String s : raid.corpseLootTaken) lw = Math.max(lw, ui.tw("taken:  " + s, 13) + ui.s(24));
            StatusHud.plate(ui, ui.s(24), ly, lw, ui.s(30) + raid.corpseLootTaken.size() * ui.s(20), 1);
            ui.text(who, ui.s(36), ly + ui.s(7), 14, Ui.AMBER);
            for (int i = 0; i < raid.corpseLootTaken.size(); i++) ui.text("taken:  " + raid.corpseLootTaken.get(i), ui.s(36), ly + ui.s(28) + i * ui.s(20), 13, Ui.TXT);
        }
        boolean ironman = c.profile.ironman;
        float k = Math.min(1, deadT / 1.4f);
        // letterbox bars close in as the colour leaves
        float bar = H * 0.085f * deathFx;
        ui.b.rect(0, 0, W, bar, 0xFF000000);
        ui.b.rect(0, H - bar, W, bar, 0xFF000000);
        Actor killer = raid.actor(raid.player.lastAttacker);
        String how = raid.deathReason().isEmpty() ? (killer != null && killer != raid.player ? "Killed by " + Raid.describe(killer) : "The Zone keeps what it kills") : raid.deathReason();
        boolean menu = deadT >= DEATH_MENU_AT;
        float ty = menu ? H * 0.20f : H * 0.36f;
        ui.textC("YOU DIED", W / 2f, ty, 56, Ui.alpha(0xFFD8D4CC, k));
        ui.textC(how, W / 2f, ty + ui.s(70), 15, Ui.alpha(Ui.TXT, k));
        ui.textC(String.format("%d:%02d in the Zone", (int) raid.time / 60, (int) raid.time % 60) + "   -   " + raid.debrief.deployedIn.display, W / 2f, ty + ui.s(94), 12, Ui.alpha(Ui.TXT_DIM, k));
        if (!menu) {
            if (deadT > 2.2f) ui.textC("press any key", W / 2f, H - bar - ui.s(34), 11, Ui.alpha(Ui.TXT_DIM, 0.5f + 0.4f * (float) Math.sin(deadT * 3)));
            return;
        }
        float a = Math.min(1, (deadT - DEATH_MENU_AT) / 0.6f);
        int keepN = ironman ? 0 : c.profile.keepOnDeath();
        List<ItemInstance> carried = new ArrayList<>();
        if (keepN > 0) for (ItemInstance i : lo().all()) if (i.parent == null) carried.add(i);
        boolean picking = keepN > 0 && !carried.isEmpty();
        float bw = ui.s(340), bh = ui.s(48), pw = ui.s(430), gapX = ui.s(44);
        float bx = picking ? W / 2f + gapX / 2 + ui.s(8) : W / 2f - bw / 2, by = H * 0.50f;
        int cashPct = Math.round(c.profile.cashLostOnDeath() * 100);
        ui.b.rect(bx - ui.s(28), by - ui.s(56), bw + ui.s(56), (ironman ? 2 : 3) * (bh + ui.s(12)) + ui.s(118), Ui.alpha(0xD0080808, a));
        if (ironman) {
            ui.textC("IRONMAN", bx + bw / 2, by - ui.s(42), 16, Ui.alpha(Ui.RED, a));
            ui.textC("One life. This survivor is gone and the profile is deleted.", bx + bw / 2, by - ui.s(18), 12, Ui.alpha(Ui.TXT, a));
        } else {
            ui.textC("WHAT NOW?", bx + bw / 2, by - ui.s(42), 16, Ui.alpha(Ui.TXT_HI, a));
            // 0.8: what a death costs is the profile's difficulty - said here, before the choice is made
            String cost = keepN == 0 ? "Hard: everything you carried is lost, and " + cashPct + "% of your cash."
                    : absentialis.persistence.Profile.DIFFICULTY[c.profile.difficulty] + ": keep " + keepN + " things you carried. The rest is lost, and " + cashPct + "% of your cash.";
            ui.textC(cost, bx + bw / 2, by - ui.s(18), 12, Ui.alpha(Ui.TXT_DIM, a));
        }
        float y = by + ui.s(10);
        if (!ironman) { if (ui.button("d_respawn", bx, y, bw, bh, "RESPAWN AT THE HIDEOUT", "arrow", a > 0.6f)) chooseDeath("respawn"); y += bh + ui.s(12); }
        if (ui.button("d_menu", bx, y, bw, bh, "MAIN MENU", "inv", a > 0.6f)) chooseDeath("menu");
        y += bh + ui.s(12);
        if (ui.button("d_quit", bx, y, bw, bh, "QUIT TO DESKTOP", "cross", a > 0.6f)) chooseDeath("quit");
        ui.textC("Stash and secured cash are safe. " + absentialis.Version.coins(Math.round(c.profile.scrip * (double) c.profile.cashLostOnDeath())) + " will be lost.", bx + bw / 2, y + bh + ui.s(14), 11, Ui.alpha(Ui.TXT_DIM, a));
        if (picking) drawKeepList(ui, carried, keepN, W / 2f - gapX / 2 - pw - ui.s(8), by - ui.s(56), pw, Math.min(H - bar - (by - ui.s(56)) - ui.s(20), ui.s(430)), a);
    }

    private float keepScroll;

    /**
     * 0.8, the pick: everything the player was carrying, in a list - weapons, armour, what was in the case, the rings
     * and charms - and they tick the few their difficulty lets them keep. Anything not ticked is gone with the body.
     * With nothing ticked, the most valuable things are kept for them.
     */
    private void drawKeepList(Ui ui, List<ItemInstance> carried, int keepN, float x, float y, float w, float h, float a) {
        List<Long> keep = raid.debrief.keep;
        keep.removeIf(uid -> carried.stream().noneMatch(i -> i.uid == uid));
        carried.sort((p, q) -> Integer.compare(q.baseValueTotal(), p.baseValueTotal()));
        ui.b.rect(x, y, w, h, Ui.alpha(0xD0080808, a));
        ui.b.outline(x, y, w, h, 1, Ui.alpha(keep.size() == keepN ? Ui.GREEN : Ui.AMBER, a * 0.8f));
        ui.text("KEEP " + keepN + " OF WHAT YOU CARRIED", x + ui.s(14), y + ui.s(10), 15, Ui.alpha(Ui.TXT_HI, a));
        ui.textR(keep.size() + " / " + keepN, x + w - ui.s(14), y + ui.s(10), 15, Ui.alpha(keep.size() == keepN ? Ui.GREEN : Ui.AMBER, a));
        ui.text(keep.isEmpty() ? "Tick what comes home. Untouched, the most valuable are kept." : "Everything else stays with the body.", x + ui.s(14), y + ui.s(32), 11, Ui.alpha(Ui.TXT_DIM, a));
        float top = y + ui.s(54), rowH = ui.s(38), listH = h - ui.s(62);
        float maxScroll = Math.max(0, carried.size() * rowH - listH);
        if (ui.hover(x, top, w, listH)) keepScroll = Math.max(0, Math.min(maxScroll, keepScroll - (float) c.in.scroll * rowH * 1.5f));
        keepScroll = Math.min(keepScroll, maxScroll);
        ui.b.scissor((int) x, (int) top, (int) w, (int) listH);
        for (int i = 0; i < carried.size(); i++) {
            ItemInstance it = carried.get(i);
            float ry = top + i * rowH - keepScroll;
            if (ry + rowH < top || ry > top + listH) continue;
            boolean on = keep.contains(it.uid), hov = a > 0.6f && ui.hover(x + ui.s(6), ry, w - ui.s(12), rowH - ui.s(3)) && ui.my >= top && ui.my <= top + listH;
            boolean full = keep.size() >= keepN && !on;
            ui.b.rect(x + ui.s(6), ry, w - ui.s(12), rowH - ui.s(3), Ui.alpha(on ? 0xFF22301E : hov ? 0xFF1E2126 : 0xFF14161A, a));
            ui.b.outline(x + ui.s(12), ry + ui.s(8), ui.s(18), ui.s(18), 1, Ui.alpha(on ? Ui.GREEN : 0xFF6A665C, a));
            if (on) ui.b.rect(x + ui.s(15), ry + ui.s(11), ui.s(12), ui.s(12), Ui.alpha(Ui.GREEN, a));
            float ar = it.baseW() / (float) Math.max(1, it.baseH()), ih = rowH - ui.s(9), iw = Math.min(ui.s(64), ih * ar);
            ih = iw / ar;
            c.icons.draw(ui.b, it, Math.round(x + ui.s(40) + (ui.s(64) - iw) / 2), Math.round(ry + (rowH - ui.s(3) - ih) / 2), iw, ih, Ui.alpha(0xFFFFFFFF, a * (full ? 0.45f : 1)));
            absentialis.ui.Layout.fit(ui, it.name() + (it.qty > 1 ? "  x" + it.qty : ""), x + ui.s(112), ry + ui.s(9), w - ui.s(112) - ui.s(96), 13, 10, Ui.alpha(full ? Ui.TXT_DIM : on ? Ui.TXT_HI : Ui.TXT, a));
            ui.textR(absentialis.Version.coins(it.baseValueTotal()), x + w - ui.s(16), ry + ui.s(10), 11, Ui.alpha(Ui.TXT_DIM, a));
            if (hov && ui.click && !ui.consumed) {
                ui.consumed = true;
                if (on) { keep.remove(Long.valueOf(it.uid)); c.sfx("ui_click", 0.4f); }
                else if (!full) { keep.add(it.uid); c.sfx("ui_confirm", 0.4f); }
                else c.sfx("ui_invalid", 0.4f);
            }
        }
        ui.b.noScissor();
        if (maxScroll > 0) { float sbh = listH * listH / (carried.size() * rowH); ui.b.rect(x + w - ui.s(5), top + (listH - sbh) * (keepScroll / maxScroll), ui.s(3), sbh, Ui.alpha(Ui.AMBER, a * 0.7f)); }
    }

    // ------------------------------------------------------------------ dynamic lights (hybrid renderer)
    private final java.util.List<float[]> lampScratch = new java.util.ArrayList<>();

    /** This frame's local lights, most important first: the renderer shadows and glows around the head of the list. */
    private void sceneLights(absentialis.engine.render.HybridRenderer hr, float fx, float fy, float fz) {
        Actor p = raid.player;
        // 0.8: a spinel overhead glints: a little light of its own colour, for the few nearest
        if (raid.spinels != null) {
            int lit = 0;
            for (CeilingSpinels.Stone sp : raid.spinels.stones) {
                if (sp.fallen || lit >= 4) continue;
                float d2 = (sp.x - p.x) * (sp.x - p.x) + (sp.z - p.z) * (sp.z - p.z);
                if (d2 > 22 * 22) continue;
                float tw = 0.7f + 0.3f * (float) Math.sin(raid.time * 3.1f + sp.x * 1.7f + sp.z);
                boolean red = sp.item.endsWith("red"), blue = sp.item.endsWith("blue");
                hr.addLight(sp.x, sp.y - 0.25f, sp.z, 2.4f, (red ? 2.2f : blue ? 0.5f : 2.0f) * tw, (red ? 0.3f : blue ? 0.9f : 0.6f) * tw, (red ? 0.4f : blue ? 2.4f : 1.5f) * tw, 0, 0, 0, 0, 0);
                lit++;
            }
        }
        // 0.8: the laser's dot is real light, in its own colour, on whatever it has fallen on - and a glint at the emitter
        if (raid.playerLaser && laserDotKnown && !p.dead) {
            float lr = ((laserColor >> 16) & 255) / 255f * LASER_LIGHT, lg = ((laserColor >> 8) & 255) / 255f * LASER_LIGHT, lb = (laserColor & 255) / 255f * LASER_LIGHT;
            hr.addLight(laserDot[0] - fx * 0.12f, laserDot[1] - fy * 0.12f, laserDot[2] - fz * 0.12f, LASER_LIGHT_RADIUS, lr, lg, lb, 0, 0, 0, 0, 0);
            if (playerPortsKnown) hr.addLight(playerPorts[0], playerPorts[1] - 0.04f, playerPorts[2], 0.6f, lr * 0.4f, lg * 0.4f, lb * 0.4f, 0, 0, 0, 0, 0);
        }
        final int SH = absentialis.engine.render.HybridRenderer.LIGHT_SHADOW, SPOT = absentialis.engine.render.HybridRenderer.LIGHT_SPOT, STATIC = absentialis.engine.render.HybridRenderer.LIGHT_STATIC;
        // the beam starts where the flashlight is: at the muzzle when it is on the gun, at the chest when it is the hand torch
        // clipped to the rig (a little lower, a little to the left, and it does not follow the sights up and down as closely)
        if (env.flashlight > 0 && !p.dead) {
            // 0.7: the light is a thing on the gun, and the beam starts at its lens - wherever the weapon is being held
            if (lightOnWeapon && lensKnown) hr.addLight(lensWorld[0] + fx * 0.06f, lensWorld[1], lensWorld[2] + fz * 0.06f, 40, 11.5f, 10.8f, 9.4f, SH | SPOT, fx, fy, fz, 0.86f);
            else if (lightOnWeapon) hr.addLight(eyeX + fx * 0.45f, eyeY - 0.10f, eyeZ + fz * 0.45f, 40, 11.5f, 10.8f, 9.4f, SH | SPOT, fx, fy, fz, 0.86f);
            else { float rx = (float) Math.cos(p.yaw), rz = (float) -Math.sin(p.yaw); hr.addLight(eyeX + fx * 0.2f - rx * 0.14f, eyeY - 0.38f, eyeZ + fz * 0.2f - rz * 0.14f, 34, 10.4f, 9.6f, 8.0f, SH | SPOT, fx, fy * 0.85f, fz, 0.84f); }
        }
        if (raid.muzzleFlash > 0.05f) hr.addPoint(env.flashX, env.flashY, env.flashZ, 11, 22 * raid.muzzleFlash, 15 * raid.muzzleFlash, 7 * raid.muzzleFlash, SH);
        raid.fire.lights(hr);
        raid.vehicles.lights(hr);
        for (float[] m : raid.orderMarks) {
            float a = raid.orderMarkAlpha(m);
            if (a <= 0) continue;
            int col = MARK_COLORS[(int) m[4] % 3];
            hr.addPoint(m[0], m[1] + 1.2f, m[2], 6, ((col >> 16) & 255) / 255f * 5 * a, ((col >> 8) & 255) / 255f * 5 * a, (col & 255) / 255f * 5 * a, 0);
        }
        // 1.1: people throw shadows in local lights (a car's headlights, somebody's torch): the nearest two dozen bodies on their feet
        {
            bodyScratch.clear();
            for (Actor a : raid.actors) {
                if (a.dead || a.ragdoll != null || a.seated || a.isPlayer && !camThird) continue;
                float d = a.distXZ(env.camX, env.camZ);
                if (d < 46) bodyScratch.add(a);
            }
            bodyScratch.sort((p1, p2) -> Float.compare(p1.distXZ(env.camX, env.camZ), p2.distXZ(env.camX, env.camZ)));
            for (int i = 0; i < bodyScratch.size() && i < absentialis.engine.render.HybridRenderer.MAX_BODIES; i++) { Actor a = bodyScratch.get(i); hr.addBody(a.x, a.y, a.z, a.radius * 0.82f, a.height()); }
        }
        for (Actor a : raid.actors) {
            if (a.isPlayer || a.dead || a.distXZ(env.camX, env.camZ) > 70) continue;
            float tx = (float) -Math.sin(a.yaw), tz = (float) -Math.cos(a.yaw), hy = a.y + a.height() * 0.72f;
            if (a.light) hr.addLight(a.x + tx * 0.42f, hy, a.z + tz * 0.42f, 20, 7.5f, 6.4f, 4.6f, SH | SPOT, tx, -0.12f, tz, 0.80f);
            if (raid.time < a.flashUntil && raid.time > a.flashUntil - 0.07f) hr.addPoint(a.x + tx * 0.9f, hy + 0.1f, a.z + tz * 0.9f, 9, 16, 11, 5, 0);
        }
        // explosions and other glowing debris light what is around them for as long as they last
        int glow = 0;

        // powered lamps near the camera, nearest first (the rest of the city's lamps reach the frame through the probes)
        lampScratch.clear();
        for (absentialis.world.MapData.Point lp : raid.world.map.lampsOnPower) {
            float dx = lp.x + 0.5f - env.camX, dz = lp.z + 0.5f - env.camZ, d2 = dx * dx + dz * dz;
            if (d2 > 66 * 66 || raid.world.get(lp.x, lp.y, lp.z) != Block.LAMP) continue;
            lampScratch.add(new float[]{lp.x + 0.5f, lp.y + 0.35f, lp.z + 0.5f, d2});
        }
        lampScratch.sort((a, b) -> Float.compare(a[3], b[3]));
        for (int i = 0; i < Math.min(72, lampScratch.size()); i++) { float[] l = lampScratch.get(i); hr.addPoint(l[0], l[1], l[2], 14, 5.6f, 4.5f, 3.0f, STATIC | SH); }
    }

    private final java.util.ArrayList<Actor> bodyScratch = new java.util.ArrayList<>();
    /** Tracer sparks and muzzle glow as real emitters of light in the particle pass. */
    private void sparks(absentialis.engine.render.ParticleBatch pb) {
        if (raid.muzzleFlash > 0.2f && !camThird && !raid.player.dead) pb.add(env.flashX, env.flashY - 0.05f, env.flashZ, 0.22f * raid.muzzleFlash, 9f, 6f, 2.6f, 0.9f, 0, absentialis.engine.render.ParticleBatch.GLOW, 0, raid.time);
        for (Actor a : raid.actors) {
            if (a.isPlayer || a.dead || !(raid.time < a.flashUntil && raid.time > a.flashUntil - 0.06f)) continue;
            float tx = (float) -Math.sin(a.yaw), tz = (float) -Math.cos(a.yaw);
            pb.add(a.x + tx * 0.95f, a.y + a.height() * 0.74f, a.z + tz * 0.95f, 0.3f, 10f, 6.5f, 2.6f, 0.9f, 0, absentialis.engine.render.ParticleBatch.GLOW, 0, a.id);
        }
    }

    /** Where the fog bank is complete this frame, in metres: nothing past it is visible, to the player or to anybody else. */
    public float fogEnd = 118f;
    /** 0.7: how far under a roof the camera is, 0..1 (eased), and the last frame's length. */
    public float indoorT;
    private float lastDt = 1 / 60f;

    /** 1.0: where the weather is felt from - the player's head (the camera's own position only when it has been taken away from them). */
    private float fogOX, fogOY, fogOZ;

    private void setupEnv(float cx, float cy, float cz, float fx, float fy, float fz) {
        final float ox = fogOX, oy = fogOY, oz = fogOZ;
        District d = raid.world.district(ox, oz);
        float day = raid.daylight();
        env.daylight = day;
        env.time = raid.time;
        env.rain = raid.profile.zone.weatherRain > 0.5f ? (raid.profile.zone.weatherRain - 0.5f) * 2 : 0;
        float out = World.outsideDistance(ox, oz);
        absentialis.gameplay.CombatConfig fc = absentialis.gameplay.CombatConfig.get();
        // 0.9: the hour has its own mist - thickest in the morning, heavy all night - on top of the district's and the rain's
        float dens = d.fogDensity * (1 + env.rain * 0.8f) * (1 + Math.min(1.5f, out / 60f)) * (1 + (fc.fogMist - 1) * raid.mist());
        // 0.7: the fog is heavier (it closes in at 118 m, from 30), but it is weather - it belongs to the street. Under a roof
        // only a little of it hangs in the air, and the change is eased so that a doorway is not a switch
        boolean roofed = oy > World.GROUND - 3 && raid.world.heightAt((int) Math.floor(ox), (int) Math.floor(oz)) > oy + 0.5f;
        indoorT += ((roofed ? 1 : 0) - indoorT) * Math.min(1, lastDt * 2.5f);
        env.fogDensity = dens / 230f * fc.fogHaze * (1 - (1 - fc.fogIndoor) * indoorT);
        env.ambientFloor = 0.0035f + fc.indoorDaylight * day;
        float near = raid.fogNear();
        env.fogWallStart = fc.fogStart * near; env.fogWallEnd = fogEnd = Math.min(fc.fogEnd * near, 236f);
        // 0.9, interiors: the fog bank is weather, and weather is outside. Under a roof the far end of a long hall used to
        // dissolve into the white of the horizon thirty metres away; indoors the bank now stands back to the limit of what is drawn.
        // (What anybody can see through the fog - fogEnd - is the street's figure and does not move.)
        env.fogWallStart += (170f - env.fogWallStart) * indoorT; env.fogWallEnd += (236f - env.fogWallEnd) * indoorT;
        env.fogSkyHorizon = 1f; env.fogSkyZenith = fc.fogSky;
        float nightK = 0.12f + 0.88f * day;
        env.fogR = d.fogR * nightK; env.fogG = d.fogG * nightK; env.fogB = d.fogB * nightK;
        if (oy < World.GROUND - 3) { env.fogDensity = 1 / 45f; env.fogR = 0.04f; env.fogG = 0.05f; env.fogB = 0.04f; }
        env.skyR = 0.85f + 0.15f * day; env.skyG = 0.9f; env.skyB = 1f;
        env.tintR = d.tintR; env.tintG = d.tintG; env.tintB = d.tintB;
        double h = raid.profile.zone.hour + raid.time / 60.0 * 0.5;
        double sa = (h % 24 - 6) / 12.0 * Math.PI;
        env.sunX = (float) Math.cos(sa) * 0.8f; env.sunY = (float) Math.sin(sa); env.sunZ = 0.35f;
        env.camX = cx; env.camY = cy; env.camZ = cz; env.dirX = fx; env.dirY = fy; env.dirZ = fz;
        ItemInstance hw = held();
        int lightFrom = raid.lightSource(held());
        if (lightFrom == 0) raid.playerFlashlight = false;        // dropped, traded away or left on the other gun
        env.flashlight = raid.playerFlashlight ? 1 : 0;
        lightOnWeapon = lightFrom == 2;
        env.flashR = raid.muzzleFlash; env.flashX = eyeX + fx; env.flashY = eyeY; env.flashZ = eyeZ + fz;
    }

    private void drawModels(float dt) {
        var mr = c.mr;
        mr.begin(pv, env);
        float day = raid.daylight();
        // 0.9: a take being recorded keeps a tape of the scene - a new moment of it starts here, with where the camera is
        if (c.ar.capture instanceof absentialis.director.SceneTape tape) tape.beginFrame(raid.player, camPX, camPY, camPZ, camYaw, camPitch, fovNow);
        for (Actor a : raid.actors) {
            if (a.distXZ(env.camX, env.camZ) > 150) continue;
            a.hurtFlash = Math.max(0, a.hurtFlash - dt);
            float light = ActorRenderer.lightAt(raid.world, a.x, a.y, a.z, day);
            if (a.isPlayer && raid.vehicles.driving != null && !a.dead && !thirdPerson) continue;   // at the wheel in first person: the cabin is what you see
            if (a.isPlayer && raid.devGhost) continue;                                              // Developer Mode: there is no body
            if (a.isPlayer) {
                if (a.dead) { if (a.ragdoll != null && !raid.infection.playerRisen()) c.ar.draw(a, playerLook, light, 0, null, 0, false); continue; }
                if (thirdPerson || camDetached) {
                    // 0.8: the body is solid. It fades only when a wall has pushed the camera right up against it and it really is in
                    // the way - never because the sights are up (0.7 faded it by the length of the boom, and aiming shortens the boom)
                    c.ar.alpha = camDetached ? 1 : Math.max(0.22f, Math.min(1, (tpsDistance - TPS_FADE_TO) / (TPS_FADE_FROM - TPS_FADE_TO)));
                    c.ar.draw(a, playerLook, light, a.pitch, ground, 0, false);
                    c.ar.alpha = 1;
                    // a throwable in the hand is drawn there (it is not a weapon, so the rig does not carry it for us)
                    ItemInstance gh = currentThrow();
                    if (gh != null && throwFlick <= 0) { c.ar.rightHand(vt); VoxModel gm = ItemModels.of(gh); tmp.identity().translate(vt[0], vt[1] - 0.05f, vt[2]).rotateY(a.facing()).scale(1 / 46f, 1 / 46f, 1 / 46f); mr.draw(mr.mesh("w:" + gh.visualKey(), gm, 1), tmp, light + 0.1f, 0xFFFFFFFF, 0); }
                } else {
                    // true first-person body: the same rig and gait, minus the head and the arms the viewmodel draws.
                    // 0.7: while sliding or lying prone the body is laid out along the ground with the camera at its head - drawn,
                    // it filled the whole view for as long as the pose lasted. In those poses only its shadow is drawn.
                    boolean low = a.slideT > 0 || a.prone || a.anim != null && (a.anim.slideW > 0.03f || a.anim.proneT > 0.03f);
                    mr.flags = absentialis.engine.render.DrawList.NO_SHADOW;
                    if (!low) c.ar.draw(a, playerLook, light, a.pitch, ground, ActorRenderer.FP_HIDE, true);
                    // ... while the shadow on the ground is the whole of you, head and arms included
                    mr.flags = absentialis.engine.render.DrawList.SHADOW_ONLY;
                    if (mr.recording()) c.ar.draw(a, playerLook, light, a.pitch, ground, 0, false);
                    mr.flags = 0;
                    // the tape wants the whole of the actor as seen from outside, which first person never draws
                    c.ar.poseOnly(a, playerLook, a.pitch, ground);
                }
                continue;
            }
            ActorRenderer.Look look = ActorRenderer.lookFor(a);
            // agents look where they believe their target is
            float pitch = 0;
            if (!a.dead && "engage".equals(a.state)) { float dd = a.distXZ(a.memX, a.memZ); pitch = (float) Math.atan2(a.memY + 1.2f - a.eyeY(), Math.max(1, dd)); }
            c.ar.draw(a, look, light, pitch, a.distXZ(env.camX, env.camZ) < 40 ? ground : null, 0, false);
            if (a.dead) continue;
            if (a.light) {
                // a carried torch: a warm beam ahead of the bearer - you can see a night sentry coming, and so can they
                float tx = (float) -Math.sin(a.yaw), tz = (float) -Math.cos(a.yaw), hy = a.y + a.height() * 0.72f;
                if (c.hr == null) mr.beam(a.x + tx * 0.4f, hy, a.z + tz * 0.4f, a.x + tx * 9, hy - 1.1f, a.z + tz * 9, 0.22f, 0x18FFE8B0, 1);
                tmp.identity().translate(a.x + tx * 0.42f, hy, a.z + tz * 0.42f).scale(0.09f, 0.09f, 0.09f).translate(0, -0.5f, 0);
                mr.cube(tmp, 0xFFFFF0C0, 1, 1);
            }
            // telegraphs are visual too (accessibility: never audio-only)
            float top = a.y + a.height();
            if (raid.telegraphing(a, "swell")) { float k = 0.9f + 0.25f * (float) Math.sin(raid.time * 30); tmp.identity().translate(a.x, a.y + 1.0f, a.z).scale(k, k, k).translate(0, -0.5f, 0); mr.cube(tmp, 0x70A0D040, 1, 1); }
            if (raid.telegraphing(a, "retch")) { tmp.identity().translate(a.x, top + 0.05f, a.z).scale(0.3f, 0.3f, 0.3f).translate(0, -0.5f, 0); mr.cube(tmp, 0xE0B4E040, 1, 1); }
            if (raid.telegraphing(a, "screech") || raid.telegraphing(a, "shriek")) { tmp.identity().translate(a.x, top + 0.3f, a.z).scale(0.25f, 0.25f, 0.25f); mr.cube(tmp, 0xFFFF3020, 1, 1); }
            if (raid.telegraphing(a, "throw")) { tmp.identity().translate(a.x + (float) Math.cos(a.yaw) * 0.9f, top + 0.5f, a.z - (float) Math.sin(a.yaw) * 0.9f).scale(0.7f, 0.6f, 0.7f).translate(0, -0.5f, 0); mr.cube(tmp, 0xFF8A8478, 0.9f, 0); tmp.identity().translate(a.x, top + 1.4f, a.z).scale(0.3f, 0.3f, 0.3f); mr.cube(tmp, 0xFFFF7020, 1, 1); }
            if (raid.telegraphing(a, "charge")) { for (int k = 0; k < 3; k++) { tmp.identity().translate(a.x + (float) Math.sin(raid.time * 9 + k * 2) * 0.8f, a.y + 0.1f + k * 0.12f, a.z + (float) Math.cos(raid.time * 7 + k * 2) * 0.8f).scale(0.25f, 0.25f, 0.25f); mr.cube(tmp, 0xB0A09880, 0.9f, 0); } tmp.identity().translate(a.x, top + 0.4f, a.z).scale(0.3f, 0.3f, 0.3f); mr.cube(tmp, 0xFFFF3020, 1, 1); }
        }
        // ---- vehicles
        mr.material(); mr.flags = 0;
        raid.vehicles.draw(mr, env.camX, env.camZ, pos -> ActorRenderer.lightAt(raid.world, pos[0], pos[1] + 0.5f, pos[2], day));
        raid.doors.draw(mr, tmp, env.camX, env.camZ, pos -> ActorRenderer.lightAt(raid.world, pos[0], pos[1], pos[2], day));
        // 1.3: containers and boarded windows, as objects
        if (blockObjects == null || blockObjectsWorld != raid.world) { blockObjects = new BlockObjects(raid.world); blockObjectsWorld = raid.world; c.wr.onColumnDirty = blockObjects::columnChanged; }
        blockObjects.draw(mr, env.camX, env.camZ, 72, pos -> ActorRenderer.lightAt(raid.world, pos[0], pos[1], pos[2], day));
        if (c.settings.vehicleDebug) raid.vehicles.debugDraw(mr, tmp);
        // ---- blood and severed parts (scene.Gore)
        Gore gore = raid.gore;
        for (Gore.Pool pl : gore.pools) {
            if (Math.abs(pl.x - env.camX) > 46 || Math.abs(pl.z - env.camZ) > 46) continue;
            float rad = pl.radius();
            if (rad < 0.05f) continue;
            float pl2 = ActorRenderer.lightAt(raid.world, pl.x, pl.y, pl.z, day);
            int col = Ui.alpha(Ui.mix(pl.color, 0xFF2A0C0A, 1 - pl.wet), 0.92f);
            // 0.7: lit as the floor it lies on (it used to be drawn brighter than its surroundings, which in daylight made it glow)
            pl2 *= 0.45f;
            mr.material(0.34f + (1 - pl.wet) * 0.5f, 0);
            mr.flags = absentialis.engine.render.DrawList.NO_SHADOW;
            col |= 0xFF000000;   // opaque in the deferred path: it is a surface, lit and reflective, not an overlay
            tmp.identity().translate(pl.x, pl.y + 0.012f, pl.z).rotateY(pl.id * 0.7f).scale(rad * 1.25f, 0.02f, rad * 1.25f).translate(0, 0, 0);
            mr.cube(tmp, col, pl2, 0);
            for (int k = 0; k < 8; k++) {
                if (pl.r[k] < 0.12f) continue;
                float ang = k * 0.7854f, lr = pl.r[k];
                tmp.identity().translate(pl.x + (float) Math.cos(ang) * lr * 0.55f, pl.y + 0.014f, pl.z + (float) Math.sin(ang) * lr * 0.55f).rotateY(-ang).scale(lr * 0.95f, 0.018f, Math.max(0.12f, lr * 0.6f));
                mr.cube(tmp, col, pl2, 0);
            }
        }
        mr.material(0.45f, 0);
        for (Gore.Stain st : gore.stains) {
            if (Math.abs(st.x - env.camX) > 40 || Math.abs(st.z - env.camZ) > 40) continue;
            tmp.identity().translate(st.x, st.y, st.z).scale(st.axis == 0 ? 0.02f : st.size, st.size * 1.3f, st.axis == 2 ? 0.02f : st.size).translate(0, -0.5f, 0);
            mr.cube(tmp, st.color, ActorRenderer.lightAt(raid.world, st.x, st.y, st.z, day), 0);
        }
        for (float[] dr : gore.drops) {
            float sz = 0.035f + dr[6] * 0.5f;
            tmp.identity().translate(dr[0], dr[1], dr[2]).scale(sz, sz * 1.6f, sz).translate(0, -0.5f, 0);
            mr.cube(tmp, dr[7] > 0 ? Gore.INFECTED_BLOOD : 0xFFA01814, 0.8f, 0);
        }
        mr.material(); mr.flags = 0;
        // detached parts: each region the piece carries, drawn from its own voxels in the pose it had when it came away
        c.ar.wounds.pump();
        for (absentialis.anatomy.DismembermentSystem.Piece pc : gore.dismember.pieces) {
            float[] pcc = pc.centre();
            if (Math.abs(pcc[0] - env.camX) > 70 || Math.abs(pcc[2] - env.camZ) > 70) continue;
            float pl = ActorRenderer.lightAt(raid.world, pcc[0], pcc[1], pcc[2], day);
            for (int g = 0; g < Rig.GROUPS; g++) {
                absentialis.anatomy.VoxelBody.Chunk ch = pc.body.chunks[g];
                if (ch == null || ch.empty() || pc.rel[g] == null) continue;
                tmp.set(pc.xform).mul(pc.rel[g]);
                mr.draw(c.ar.wounds.mesh("pc:" + pc.id + "#" + g, ch), tmp, pl, 0xFFFFFFFF, 0);
            }
        }
        // fragments knocked out of bodies
        mr.flags = absentialis.engine.render.DrawList.NO_SHADOW;
        for (Gore.Debris db : gore.debris) {
            if (Math.abs(db.x - env.camX) > 40 || Math.abs(db.z - env.camZ) > 40) continue;
            tmp.identity().translate(db.x, db.y, db.z).rotateY(db.spin).scale(db.size, db.size, db.size).translate(0, -0.5f, 0);
            mr.cube(tmp, db.life < 1 ? Ui.alpha(db.color, db.life) : db.color, 0.8f, 0);
        }
        mr.flags = 0;
        for (Raid.WorldItem w : raid.items) {
            VoxModel m = ItemModels.of(w.it);
            float s = 1 / 40f;
            tmp.identity().translate(w.x, w.y + 0.05f + (float) Math.sin(raid.time * 2 + w.x) * 0.03f, w.z).rotateY(raid.time * 0.6f + w.z).scale(s, s, s);
            mr.draw(mr.mesh("w:" + w.it.visualKey(), m, 1), tmp, ActorRenderer.lightAt(raid.world, w.x, w.y, w.z, day) + 0.15f, 0xFFFFFFFF, 0.15f);
        }
        // 0.8: spinels in the ceilings - the stone itself, turning slowly so that it catches the light
        if (raid.spinels != null) for (CeilingSpinels.Stone sp : raid.spinels.stones) {
            if (sp.fallen) continue;
            float ddx = sp.x - env.camX, ddz = sp.z - env.camZ;
            if (ddx * ddx + ddz * ddz > 70 * 70) continue;
            VoxModel gm = ItemModels.of(absentialis.gameplay.items.Content.def(sp.item));
            float gs = 1 / 30f, twinkle = 0.75f + 0.25f * (float) Math.sin(raid.time * 3.1f + sp.x * 1.7f + sp.z);
            // 1.1: set in the ceiling and still - its top is in the slab, and it does not turn (it used to hang clear and spin)
            tmp.identity().translate(sp.x, sp.y + 0.14f - gm.sy * gs * 0.82f, sp.z).rotateY(sp.x * 2.3f + sp.z * 1.1f).scale(gs, gs, gs).translate(-gm.sx / 2f, 0, -gm.sz / 2f);
            mr.draw(mr.mesh("w:" + sp.item, gm, 1), tmp, 1f, 0xFFFFFFFF, twinkle);
        }
        if (raid.playerLaser && !raid.player.dead) {
            // 0.8: the laser is an object. Its beam leaves the module under the barrel (not the eye), runs to the first
            // thing on the line of aim, and ends in a dot that is drawn in the world - on the wall, on the body - and
            // that is the reticle while it is on. Past its reach there is no dot, only the beam going out.
            Actor pl = raid.player;
            float lx = (float) (-Math.sin(pl.yaw) * Math.cos(pl.pitch)), ly = (float) Math.sin(pl.pitch), lz = (float) (-Math.cos(pl.yaw) * Math.cos(pl.pitch));
            float rx2 = (float) Math.cos(pl.yaw) * 0.12f, rz2 = (float) -Math.sin(pl.yaw) * 0.12f;
            float sx = playerPortsKnown ? playerPorts[0] - lx * 0.10f : eyeX + rx2 + lx * 0.5f, sy = playerPortsKnown ? playerPorts[1] - 0.045f : eyeY - 0.12f + ly * 0.5f, sz = playerPortsKnown ? playerPorts[2] - lz * 0.10f : eyeZ + rz2 + lz * 0.5f;
            float dist = aimHit(laserDot);
            laserDotKnown = dist > 0;
            float ex = laserDotKnown ? laserDot[0] : eyeX + lx * LASER_REACH, ey = laserDotKnown ? laserDot[1] : eyeY + ly * LASER_REACH, ez = laserDotKnown ? laserDot[2] : eyeZ + lz * LASER_REACH;
            mr.beam(sx, sy, sz, ex, ey, ez, 0.006f, (laserColor & 0x00FFFFFF) | 0x70000000, 1);
            if (laserDotKnown) {
                // the dot keeps its size on the screen as it goes away, the way a real one seems to
                float size = LASER_DOT * Math.max(1f, dist / 9f);
                mr.beam(ex - lx * size * 0.5f, ey - ly * size * 0.5f, ez - lz * size * 0.5f, ex + lx * 0.01f, ey + ly * 0.01f, ez + lz * 0.01f, size, laserColor | 0xFF000000, 1);
                mr.beam(ex - lx * size * 0.6f, ey - ly * size * 0.6f, ez - lz * size * 0.6f, ex, ey, ez, size * 0.45f, 0xFFFFFFFF, 1);
            }
        }
        gunFx.draw(mr, tmp, env.camX, env.camZ, pos -> ActorRenderer.lightAt(raid.world, pos[0], pos[1], pos[2], day));
        if (c.hr == null) gunFx.drawLegacy(mr, tmp);
        drawOrderMarks(mr);
        drawLootBeams(mr);
        raid.animals.draw(mr, env.camX, env.camZ, pos -> ActorRenderer.lightAt(raid.world, pos[0], pos[1], pos[2], day));
        if (throwAiming && !raid.player.dead) drawThrowArc(mr);
        if (raid.aiDebug) drawAiDebug(mr);
        for (Raid.Thrown t : raid.thrown) {
            if (!t.debris && t.def != null && !t.carried) {
                // what was thrown is what flies: the grenade's own model, tumbling
                VoxModel gm = ItemModels.of(t.def);
                tmp.identity().translate(t.x, t.y, t.z).rotateY(raid.time * 5 + t.x).rotateX(raid.time * 7).scale(1 / 44f, 1 / 44f, 1 / 44f).translate(0, -gm.sy * 0.5f, 0);
                mr.draw(mr.mesh("def:" + t.def.id, gm, 1), tmp, ActorRenderer.lightAt(raid.world, t.x, t.y, t.z, day) + 0.15f, 0xFFFFFFFF, 0);
                continue;
            }
            float sz = t.debris ? 0.55f : 0.12f;
            tmp.identity().translate(t.x, t.y, t.z).rotateY(raid.time * 5).rotateX(raid.time * 7).scale(sz, sz, sz).translate(0, -0.5f, 0);
            mr.cube(tmp, t.debris ? 0xFF8A8478 : 0xFF4A5030, 0.8f, 0);
        }
        for (Raid.Projectile pr : raid.projectiles) {
            if (!pr.tracer && pr.owner == raid.player.id) continue;
            mr.beam(pr.px, pr.py, pr.pz, pr.x, pr.y, pr.z, pr.tracer ? 0.04f : 0.015f, pr.tracer ? 0xFFFFC060 : 0x80FFF0C0, 1);
        }
        // 1.0: debris and chips are voxels of the particle system: lit cubes that tumble, land and lie
        raid.fx.chunks((ex, ey, ez, es, eyaw, epitch, argb, light, glowing) -> {
            tmp.identity().translate(ex, ey, ez).rotateY(eyaw).rotateX(epitch).scale(es, es, es).translate(0, -0.5f, 0);
            mr.cube(tmp, argb, glowing > 0 ? 1 : 0.25f + 0.6f * light, glowing > 0 ? 1 : 0);
        }, env.camX, env.camZ, 60f);
        // hazard zones: a stippled pool so the edge of the danger is readable
        if (c.hr == null) raid.fire.drawLegacy(mr, tmp);
        for (Raid.Zone z : raid.zones) {
            if (z.kind.equals("fire")) continue;   // real flames mark it (scene.FireSystem)
            int col = z.kind.equals("bile") ? 0x701F4A12 : z.kind.equals("venom") ? 0x90181C10 : z.kind.equals("contam") ? 0x508CA83A : 0x70B4D040;
            for (int k = 0; k < 14; k++) {
                float ang = k * 2.4f + z.x, rad = z.radius * (float) Math.sqrt((k + 0.5f) / 14f);
                float px = z.x + (float) Math.sin(ang) * rad, pz = z.z + (float) Math.cos(ang) * rad;
                // 1.1: a pool does not go through a wall (its glow used to lie on the floor of the next room)
                if (!raid.zoneReaches(z, px, z.y, pz)) continue;
                tmp.identity().translate(px, z.y + 0.02f, pz).scale(0.9f, 0.04f, 0.9f).translate(0, 0, 0);
                mr.cube(tmp, col, 1, 0.6f);
            }
        }
        // (1.1: the smoke over places where the strategic tier resolved a fight is the particle system's - Raid.smokeColumns.
        // It was ten grey cubes going up in a line.)
        if (raid.noiseDebug) for (Raid.Sound s : raid.sounds) {
            float age = raid.time - s.time(), rad = s.radius() * Math.min(1, age * 3);
            for (int k = 0; k < 24; k++) {
                double a0 = k * Math.PI * 2 / 24, a1 = (k + 1) * Math.PI * 2 / 24;
                mr.beam(s.x() + (float) Math.sin(a0) * rad, s.y(), s.z() + (float) Math.cos(a0) * rad, s.x() + (float) Math.sin(a1) * rad, s.y(), s.z() + (float) Math.cos(a1) * rad, 0.06f, Ui.alpha(0xFF40C0FF, 1 - age / 4), 1);
            }
        }
        // extraction beacons: visible signal columns at eligible exits nearby
        for (Raid.ExitState s : raid.exits) {
            if (!s.eligible) continue;
            float d = raid.player.distXZ(s.exit.x, s.exit.z);
            if (d > 120 || !c.profile.discovered(s.exit.x / 8, s.exit.z / 8)) continue;
            tmp.identity().translate(s.exit.x + 0.5f, s.exit.y, s.exit.z + 0.5f).scale(0.15f, 18f, 0.15f);
            mr.cube(tmp, 0x5050FF70, 1, 1);
        }
        mr.end();
    }

    // ------------------------------------------------------------------ first-person viewmodel
    /** Viewmodel space is a miniature of the world in front of the camera: this is one character voxel there. */
    private static final float VM_VOX = Rig.VOX * 40f / 88f;
    private final Mat4 vmWeapon = new Mat4(), vmBone = new Mat4();
    private final float[] vt = new float[3], ve = new float[3];

    /**
     * Weapon and both arms. The weapon follows the same action clips as the third-person body
     * (tactical reload, empty reload with a rack, shell loading, jam clearing), so a reload looks
     * different depending on whether a round is still chambered. Sway lags the mouse and scales
     * with the weapon's mass; the arms are the player's own sleeves and gloves, solved onto the grips.
     */
    private final absentialis.sydney.ContextualMotion.Mantle fpMantle = new absentialis.sydney.ContextualMotion.Mantle();
    private final float[] fpHand = new float[3];

    /**
     * 1.0, the pull-up through the survivor's own eyes (Sydney: contextual). The hands are where the body's are - on the lip
     * of the ledge, the same two places in the world - brought into the eye's frame; so they come up into view as the arms
     * are thrown at the edge, stay planted on it while the view rises past them, and are pressed down on as it goes over.
     */
    private void drawMantleArms(float aspect) {
        Actor p = raid.player;
        absentialis.sydney.ContextualMotion.mantle(p, fpMantle);
        if (fpMantle.handW < 0.05f) return;
        if (!c.mr.recording()) glClear(GL_DEPTH_BUFFER_BIT);
        Mat4 vp = new Mat4().perspective((float) Math.toRadians(60), aspect, 0.01f, 10f);
        WorldRenderer.Env e = new WorldRenderer.Env();
        e.fogDensity = 0; e.camX = 0; e.camY = 0; e.camZ = 0;
        c.mr.begin(vp, e);
        float light = Math.max(0.25f, ActorRenderer.lightAt(raid.world, p.x, p.y + 1, p.z, raid.daylight()));
        for (int side = 0; side < 2; side++) {
            float[] hw = side == 0 ? fpMantle.handR : fpMantle.handL;
            view.transformPoint(hw[0], hw[1], hw[2], fpHand);
            // the hand eases to its place as it takes the weight (it starts where a raised hand would be) and lets go late
            float k = fpMantle.handW, rx = side == 0 ? 0.22f : -0.22f;
            float tx = rx + (fpHand[0] - rx) * k, ty = -0.05f + (fpHand[1] + 0.03f + 0.05f) * k, tz = -0.36f + (fpHand[2] + 0.36f) * k;
            // never behind the eye, never further than an arm
            tz = Math.min(tz, -0.16f);
            float len = (float) Math.sqrt((tx - rx) * (tx - rx) + (ty + 0.36f) * (ty + 0.36f) + tz * tz);
            if (len > 0.74f) { float s = 0.74f / len; tx = rx + (tx - rx) * s; ty = -0.36f + (ty + 0.36f) * s; tz *= s; }
            // and kept where the eye can see them: at the top of the view as they are thrown up, at the bottom as they are pressed on
            ty = Math.max(tz * 0.50f, Math.min(-tz * 0.44f, ty));
            arm(playerLook, side == 0, tx, ty, tz, side == 0 ? 0.20f : -0.20f, -0.36f, -0.02f, light);
        }
        c.mr.end();
    }

    /**
     * 1.1, the hands, seen through the eyes of whoever is using them. They hang out of sight until a punch is thrown and
     * for a moment after; then they are up in a guard, and the one that is punching goes out to arm's length along the
     * line of sight and comes back - the heavy one drawn back past the shoulder first and driven across the body.
     */
    private void drawFists(float aspect) {
        float since = raid.time - lastPunch;
        float up = punchT > 0 ? 1 : Math.max(0, Math.min(1, (1.6f - since) / 0.35f));
        float bk = blockT * blockT * (3 - 2 * blockT);
        up = Math.max(up, bk);
        if (up <= 0.01f) return;
        Actor p = raid.player;
        if (!c.mr.recording()) glClear(GL_DEPTH_BUFFER_BIT);
        Mat4 vp = new Mat4().perspective((float) Math.toRadians(60), aspect, 0.01f, 10f);
        WorldRenderer.Env e = new WorldRenderer.Env();
        e.fogDensity = 0; e.camX = 0; e.camY = 0; e.camZ = 0;
        c.mr.begin(vp, e);
        float light = Math.max(0.25f, ActorRenderer.lightAt(raid.world, p.x, p.y + 1, p.z, raid.daylight()));
        float k = punchT > 0 ? 1 - punchT / Math.max(0.01f, punchDur) : 1;
        for (int side = 0; side < 2; side++) {
            boolean right = side == 0, active = punchT > 0 && (punchIdx == 1) != right;
            float sx = right ? 1 : -1;
            // the guard: fists under the chin, a little apart, the lead hand a little further out
            float tx = sx * 0.13f, ty = -0.16f - (1 - up) * 0.3f, tz = -0.34f + (right ? 0.03f : 0);
            // 1.2, the guard: both fists come up between the eyes and what is coming, close together, the forearms upright
            // - and give a little, together, each time something lands on them
            if (bk > 0 && !active) { float jolt = Math.max(0, 1 - (raid.time - raid.player.lastHitTime) / 0.18f) * 0.03f; tx += (sx * 0.085f - tx) * bk; ty += (-0.105f + (right ? -0.012f : 0) - jolt - ty) * bk; tz += (-0.40f + (right ? 0.03f : 0) + jolt - tz) * bk; }
            if (active) {
                boolean heavy = punchIdx == 2;
                float wind = heavy ? (k < 0.28f ? k / 0.28f : Math.max(0, 1 - (k - 0.28f) / 0.2f)) : 0;
                float c0 = heavy ? 0.30f : 0.05f, c1 = PUNCH_CONTACT + 0.06f;
                float out = k < c0 ? 0 : k < c1 ? (k - c0) / (c1 - c0) : Math.max(0, 1 - (k - c1) / (1 - c1));
                out = out * out * (3 - 2 * out);
                tx += sx * 0.07f * wind - sx * (heavy ? 0.16f : 0.10f) * out;
                ty += 0.03f * wind + (heavy ? 0.07f : 0.05f) * out;
                tz += 0.14f * wind - (heavy ? 0.36f : 0.30f) * out;
            }
            arm(playerLook, right, tx, ty, tz, sx * 0.22f, -0.40f, -0.04f, light);
        }
        c.mr.end();
    }

    private void drawViewmodel(float fov, float aspect) {
        lensKnown = false; playerPortsKnown = false;
        if (raid.player.mantling()) { drawMantleArms(aspect); return; }
        if (unarmed() && !raid.player.downed()) { drawFists(aspect); return; }
        // what is in the hand: a throwable that has been equipped, the first-aid can while it is being used, or the weapon
        boolean spraying = raid.body.using != null && "med.spray".equals(raid.body.using.id);
        ItemInstance h = spraying ? null : "throw".equals(heldSlot) ? currentThrow() : held();
        if (h == null && !spraying) return;
        if (!spraying && h.def().throwable != null && throwFlick > 0) return;      // it has just left the hand
        Actor p = raid.player;
        Animator an = p.anim;
        if (!c.mr.recording()) glClear(GL_DEPTH_BUFFER_BIT);
        Mat4 vp = new Mat4().perspective((float) Math.toRadians(60), aspect, 0.01f, 10f);
        WorldRenderer.Env e = new WorldRenderer.Env();
        e.fogDensity = 0; e.camX = 0; e.camY = 0; e.camZ = 0;
        c.mr.begin(vp, e);
        if (spraying || h.def().throwable != null || h.def().weapon != null && h.def().weapon.melee()) { drawHandheld(h, spraying, p, an); c.mr.end(); return; }
        VoxModel m = ItemModels.of(h);
        boolean melee = false;
        boolean pistol = "sidearm".equals(h.def().slot);
        float s = 1 / 88f;
        // weight: heavier weapons lag further behind the look, drift more at rest and settle slower
        float mass = Math.max(0.4f, h.mass()), heft = 0.6f + mass * 0.16f;
        float dt = c.ui.dt;
        float lagX = overlay() ? 0 : (float) -c.in.dx * 0.00045f * heft, lagY = overlay() ? 0 : (float) c.in.dy * 0.00045f * heft;
        swayX += (Math.max(-0.05f, Math.min(0.05f, lagX)) - swayX) * Math.min(1, dt * (14f / heft));
        swayY += (Math.max(-0.05f, Math.min(0.05f, lagY)) - swayY) * Math.min(1, dt * (14f / heft));
        float tired = 1 + (1 - raid.body.stamina / 100f) * 1.5f;
        // through the sights the weapon is held to the eye: lag, drift and gait bob are almost entirely taken out,
        // leaving the breath - what moves the point of aim is recoil and the shooter's condition, not the walk cycle
        float ak = adsK, hip = 1 - ak, loose = 1 - ak * 0.92f;
        float breath = 1 + (1 - raid.body.focus / 100f) * 0.8f + raid.combat.animation[raid.combat.condition(p.hp, p.maxHp).ordinal()] * 1.2f;
        float idleX = (float) Math.sin(raid.time * 0.9f) * 0.0022f * heft * tired * (hip + ak * 0.10f * breath), idleY = (float) Math.sin(raid.time * 1.3f + 1) * 0.0018f * heft * tired * (hip + ak * 0.14f * breath);
        // gait bob straight from the animated footfalls
        float ph = an.phase * 6.2832f, walk = bobAmt * (1 - ak * 0.95f);
        float bobX = (float) Math.sin(ph) * 0.010f * walk, bobY = -Math.abs((float) Math.cos(ph)) * 0.008f * walk;
        // action clip (weapon channel) relative to the shouldered pose
        float aw = 0;
        Anims.get("wpn_aim").sample(0, vmBase);
        vmPose.set(vmBase);
        if (!an.action.isEmpty() && Anims.get(an.action).drivesWeapon && !Anims.get(an.action).additive && !an.action.equals("equip")) {
            Anims.get(an.action).sample(an.actionTime / an.actionDur, vmPose);
            aw = Math.max(0, Math.min(1, Math.min(an.actionTime / Animator.BLEND, (an.actionDur - an.actionTime) / Animator.BLEND)));
        }
        float dxp = (vmPose.wpn[0] - vmBase.wpn[0]) * aw, dyp = (vmPose.wpn[1] - vmBase.wpn[1]) * aw, dzp = (vmPose.wpn[2] - vmBase.wpn[2]) * aw;
        float dpitch = (vmPose.wpn[3] - vmBase.wpn[3]) * aw, dyaw = (vmPose.wpn[4] - vmBase.wpn[4]) * aw, droll = (vmPose.wpn[5] - vmBase.wpn[5]) * aw;
        float sw = Math.max(0, switchT) / 0.45f;
        float sight = sightLine(h, m, s);
        float ax = 0.13f * hip + swayX * loose + idleX + bobX + dxp * VM_VOX;
        float ay = -0.13f * hip + sight * ak + swayY * loose + idleY + bobY + dyp * VM_VOX - sw * sw * 0.32f - landDip * 0.4f * hip;
        float az = -0.34f + ak * 0.045f + kick * (0.03f + 0.022f * firePunch) + dzp * VM_VOX;
        float sprintLower = p.sprinting ? 1 : 0;
        vmWeapon.identity().translate(ax, ay - sprintLower * 0.05f, az)
                .rotateY(dyaw + hip * 0.06f - swayX * 2.5f * loose + sprintLower * 0.5f + sw * 0.4f)
                .rotateX(kick * (0.05f + 0.035f * firePunch - ak * 0.03f) + dpitch * 0.8f + swayY * 2.5f * loose - sw * 0.9f - sprintLower * 0.25f)
                .rotateZ(droll * 0.7f - swayX * 3f * loose);
        Mat4 mm = new Mat4().set(vmWeapon).rotateY((float) Math.PI / 2).scale(s, s, s).translate(m.sx * 0.22f, -m.sy * 0.55f, 0);
        // where this weapon's light, muzzle and ejection port are in the world this frame: the beam, the smoke and the brass start there
        float[] lens = lightOnWeapon ? ItemModels.lightLens(h) : null;
        if (lens != null) { mm.transformPoint(lens[0] - m.sx / 2f, lens[1], lens[2] - m.sz / 2f, vt); invView.transformPoint(vt[0], vt[1], vt[2], lensWorld); lensKnown = true; }
        mm.transformPoint(m.sx / 2f + 1, m.sy * 0.6f, 0, vt); invView.transformPoint(vt[0], vt[1], vt[2], ve);
        playerPorts[0] = ve[0]; playerPorts[1] = ve[1]; playerPorts[2] = ve[2];
        mm.transformPoint(-m.sx * 0.12f, m.sy * 0.62f, m.sz * 0.6f, vt); invView.transformPoint(vt[0], vt[1], vt[2], ve);
        playerPorts[3] = ve[0]; playerPorts[4] = ve[1]; playerPorts[5] = ve[2];
        playerPortsKnown = true;
        float light = Math.max(0.25f, ActorRenderer.lightAt(raid.world, p.x, p.y, p.z, raid.daylight())) + raid.muzzleFlash * 0.4f;
        c.mr.material(0.46f, 0.55f);
        c.mr.draw(c.mr.mesh("w:" + h.visualKey(), m, 1), mm, light, 0xFFFFFFFF, 0);
        c.mr.material();

        // hands: firing grip, and the support hand on the fore-end or wherever the clip sends it
        float gz = -0.06f * m.sx * s, gy = -0.17f * m.sy * s;
        vmWeapon.transformPoint(0.004f, gy - 0.012f, gz + 0.01f, vt);
        if (melee) vmWeapon.transformPoint(0, -0.5f * m.sy * s, 0.30f * m.sx * s, vt);
        arm(playerLook, true, vt[0], vt[1], vt[2], 0.17f, -0.30f, -0.06f, light);
        if (!melee) {
            float sep = pistol ? 0.012f : 0.34f * m.sx * s;
            float lx = vmPose.lhOnWeapon > 0.5f ? vmPose.wpn[6] * aw : 0, ly = vmPose.lhOnWeapon > 0.5f ? vmPose.wpn[7] * aw : 0, lz = vmPose.lhOnWeapon > 0.5f ? vmPose.wpn[8] * aw : 0;
            vmWeapon.transformPoint((pistol ? -0.012f : 0) + lx * VM_VOX, gy + (pistol ? -0.012f : 0.006f) + ly * VM_VOX, gz - sep + lz * VM_VOX, vt);
            if (aw > 0 && vmPose.lhOnWeapon < 0.5f) {
                // free hand (interact / heal): an eye-space reach instead of a grip
                float k = aw;
                vt[0] += (vmPose.wpn[6] * VM_VOX * 1.6f - 0.02f - vt[0]) * k; vt[1] += (-0.20f + (vmPose.wpn[7] + 6) * VM_VOX - vt[1]) * k; vt[2] += (-0.30f + vmPose.wpn[8] * VM_VOX * 0.6f - vt[2]) * k;
            }
            arm(playerLook, false, vt[0], vt[1], vt[2], -0.12f, -0.32f, -0.14f, light);
            // the fresh magazine travels in the support hand from the pouch to the well
            if (reloadT > 0 && reloadMag != null && ("mag".equals(reloadKind) || "speed".equals(reloadKind))) {
                float t = 1 - reloadT / Math.max(0.01f, reloadMax), t0 = reloadEmpty ? 0.40f : 0.52f, t1 = reloadEmpty ? 0.66f : 0.84f;
                if (t > t0 && t < t1) {
                    VoxModel mg = ItemModels.of(reloadMag);
                    vmBone.identity().translate(vt[0], vt[1] + 0.01f, vt[2]).rotateY((float) Math.PI / 2 + dyaw).rotateZ(-0.2f).scale(s, s, s).translate(0, -mg.sy * 0.2f, 0);
                    c.mr.draw(c.mr.mesh("w:" + reloadMag.visualKey(), mg, 1), vmBone, light, 0xFFFFFFFF, 0);
                }
            }
        }
        if (raid.muzzleFlash > 0.2f && h.def().weapon != null && !melee) {
            float[] mz = new float[3];
            mm.transformPoint(m.sx / 2f + 1, m.sy * 0.6f, 0, mz);
            float fs = 0.05f * raid.muzzleFlash;
            Mat4 fm = new Mat4().translate(mz[0], mz[1] - fs / 2, mz[2]).scale(fs, fs, fs);
            c.mr.cube(fm, 0xB0FFD070, 1, 1);
        }
        c.mr.end();
    }

    /** Grip of each thing that is held in one hand, in its own model's voxels, and how big it is drawn (metres per voxel in view space). */
    private static float[] gripOf(ItemInstance h, boolean spray) {
        if (spray) return new float[]{3, 4.5f, 3, 1 / 46f};
        if (h.def().throwable != null) { VoxModel m = ItemModels.of(h); return new float[]{m.sx * 0.5f, m.sy * 0.36f, m.sz * 0.5f, 1 / 50f}; }
        return switch (h.def().weapon.model) {
            case "crowbar" -> new float[]{6.5f, 3.5f, 1.5f, 1 / 54f};
            // 0.7: the knife was drawn at rifle scale - a blade the length of a thumb - and swung about a corner of its own
            // model while the hand stayed where it was. It is drawn at its own scale now, and the hand is on the handle.
            default -> new float[]{2.6f, 2.5f, 1.5f, 1 / 34f};
        };
    }

    private final Mat4 vmHand = new Mat4();

    /**
     * Things held in one hand - the knife and the crowbar, a grenade, the first-aid can. One frame carries both the
     * object and the hand: the object is drawn about its grip and the arm is solved onto the same point, so whatever
     * the motion - a slash, a wind-up, the can turning to spray - the thing stays in the hand that holds it.
     */
    private void drawHandheld(ItemInstance h, boolean spray, Actor p, Animator an) {
        VoxModel m = spray ? ItemModels.of(absentialis.gameplay.items.Content.def("med.spray")) : ItemModels.of(h);
        float[] g = gripOf(h, spray);
        float s = g[3], dt = c.ui.dt;
        boolean thrown = !spray && h.def().throwable != null;
        float mass = spray ? 0.4f : Math.max(0.3f, h.mass()), heft = 0.7f + mass * 0.12f;
        float lagX = overlay() ? 0 : (float) -c.in.dx * 0.0004f * heft, lagY = overlay() ? 0 : (float) c.in.dy * 0.0004f * heft;
        swayX += (Math.max(-0.05f, Math.min(0.05f, lagX)) - swayX) * Math.min(1, dt * 12);
        swayY += (Math.max(-0.05f, Math.min(0.05f, lagY)) - swayY) * Math.min(1, dt * 12);
        float ph = an.phase * 6.2832f, bobX = (float) Math.sin(ph) * 0.010f * bobAmt, bobY = -Math.abs((float) Math.cos(ph)) * 0.008f * bobAmt;
        float sw = Math.max(0, switchT) / 0.45f, run = p.sprinting ? 1 : 0;
        float hx = 0.165f + swayX + bobX, hy = -0.175f + swayY + bobY - sw * sw * 0.3f - landDip * 0.4f - run * 0.04f, hz = -0.37f;
        float yaw = -0.18f - swayX * 2, pitch = 0.34f + swayY * 2, roll = -0.10f - run * 0.25f;
        // 1.2, the guard with a blade: the hand comes up and in, and the blade is turned across the line of sight, edge out
        if (!spray && !thrown && blockT > 0.01f && h.def().weapon != null && h.def().weapon.melee()) {
            float bk = blockT * blockT * (3 - 2 * blockT), jolt = Math.max(0, 1 - (raid.time - p.lastHitTime) / 0.18f) * 0.03f;
            hx += (0.05f - hx) * bk; hy += (0.075f - jolt) * bk; hz += (0.07f + jolt) * bk; yaw += 1.15f * bk; pitch -= 0.30f * bk; roll += 0.55f * bk;
        }
        if (spray) {
            // the can comes up in front of the chest and turns to spray back at the body
            float t = 1 - raid.body.useTimer / Math.max(0.1f, raid.body.using.medical.useTime), up = Math.min(1, t / 0.18f) * Math.min(1, (1 - t) / 0.12f);
            float wav = (float) Math.sin(raid.time * 5.5f) * 0.035f * up;
            hx = 0.165f + (0.02f + wav - 0.165f) * up; hy = -0.175f + (-0.13f + (float) Math.sin(raid.time * 3.1f) * 0.02f + 0.175f) * up; hz = -0.37f + (-0.30f + 0.37f) * up;
            yaw = -0.18f + (3.0f + wav * 4 + 0.18f) * up; pitch = 0.34f + (-0.55f - 0.34f) * up; roll = -0.10f * (1 - up);
            sprayUp = up;
        } else if (thrown) {
            // wound up behind the shoulder as the throw is held; a flick forward as it goes
            float cgh = throwCharge;
            hx += 0.05f * cgh; hy += 0.12f * cgh; hz += 0.13f * cgh; pitch += 0.7f * cgh; roll -= 0.3f * cgh;
        } else if (meleeT > 0) {
            // the strike: drawn back and up, then driven forward and across the body, then recovered - hand and blade as one
            float k = 1 - meleeT / Math.max(0.05f, meleeDur);
            float wind = k < 0.18f ? k / 0.18f : Math.max(0, 1 - (k - 0.18f) / 0.16f), cut = k < 0.18f ? 0 : k < 0.5f ? (k - 0.18f) / 0.32f : Math.max(0, 1 - (k - 0.5f) / 0.5f);
            cut = cut * cut * (3 - 2 * cut);
            hx += 0.05f * wind - 0.20f * cut; hy += 0.07f * wind - 0.05f * cut; hz += 0.05f * wind - 0.15f * cut;
            yaw += -0.35f * wind + 1.05f * cut; pitch += 0.45f * wind - 0.42f * cut; roll += 0.2f * wind - 0.85f * cut;
        }
        vmHand.identity().translate(hx, hy, hz).rotateY(yaw).rotateX(pitch).rotateZ(roll);
        // the model's +x is its length (blade, bar): it points away from the eye; its grip sits on the frame's origin
        Mat4 mm = new Mat4().set(vmHand).rotateY((float) Math.PI / 2).scale(s, s, s).translate(-(g[0] - m.sx / 2f), -g[1], -(g[2] - m.sz / 2f));
        if (spray || thrown) mm.set(vmHand).scale(s, s, s).translate(-(g[0] - m.sx / 2f), -g[1], -(g[2] - m.sz / 2f));
        float light = Math.max(0.25f, ActorRenderer.lightAt(raid.world, p.x, p.y, p.z, raid.daylight())) + raid.muzzleFlash * 0.4f;
        c.mr.material(0.46f, spray || thrown ? 0.2f : 0.55f);
        String key = spray ? "def:med.spray" : "w:" + h.visualKey();
        c.mr.draw(c.mr.mesh(key, m, 1), mm, light, 0xFFFFFFFF, 0);
        c.mr.material();
        vmHand.transformPoint(0, 0, 0, vt);
        arm(playerLook, true, vt[0], vt[1] - 0.012f, vt[2] + 0.012f, 0.19f, -0.34f, -0.04f, light);
        if (spray && sprayUp > 0.3f) {
            // the spray itself: a cone of mist from the nozzle onto the body, seen from inside it
            mm.transformPoint(0, m.sy - g[1] + 1.5f, 0, ve);
            for (int k = 0; k < 14; k++) {
                float t = ((raid.time * 2.6f + k / 14f) % 1f), spread = t * 0.11f;
                float ox = (float) Math.sin(k * 2.4f + raid.time * 1.3f) * spread, oy = (float) Math.cos(k * 1.7f) * spread;
                float px = ve[0] + (0.0f - ve[0]) * t + ox, py = ve[1] + (-0.26f - ve[1]) * t + oy, pz = ve[2] + (0.02f - ve[2]) * t;
                float size = 0.012f + t * 0.05f;
                Mat4 fm = new Mat4().translate(px, py - size / 2, pz).scale(size, size, size);
                c.mr.cube(fm, Ui.alpha(0xFFE6F6F2, (1 - t) * 0.5f * sprayUp), 1, 0.5f);
            }
        }
        if (thrown && throwAiming && h.def().throwable != null && !"noise".equals(h.def().throwable.kind) && !"flare".equals(h.def().throwable.kind)) {
            // the pin is out: the spoon is held down under the fingers and the free hand is clear of it
            arm(playerLook, false, vt[0] - 0.13f, vt[1] - 0.06f, vt[2] + 0.02f, -0.14f, -0.34f, -0.10f, light);
        }
    }

    private float sprayUp;
    private float mistAcc;

    /**
     * 0.7: the first-aid spray is seen being used. While the can is working, a mist leaves it onto the body: in the
     * world as particles round the chest (third person, and anyone watching), and in first person as the cone drawn
     * with the can in {@link #drawHandheld}.
     */
    private void sprayMist(float dt) {
        if (raid.body.using == null || !"med.spray".equals(raid.body.using.id) || raid.player.dead) { mistAcc = 0; if (raid.body.using == null) sprayUp = 0; return; }
        Actor p = raid.player;
        float t = 1 - raid.body.useTimer / Math.max(0.1f, raid.body.using.medical.useTime);
        if (t < 0.15f || t > 0.9f) return;
        if (c.audio != null && c.audio.available && (int) (t * 9) != (int) ((t - dt / Math.max(0.1f, raid.body.using.medical.useTime)) * 9)) c.audio.play("spray_hiss", 0.35f, 0, 0.95f + raid.rng.nextFloat() * 0.1f);
        mistAcc += dt * 46;
        float fx = (float) -Math.sin(p.facing()), fz = (float) -Math.cos(p.facing()), rx = (float) Math.cos(p.facing()), rz = (float) -Math.sin(p.facing());
        while (mistAcc >= 1) {
            mistAcc -= 1;
            float side = raid.rng.nextFloat() - 0.5f, up = raid.rng.nextFloat();
            // from the can in front of the chest, back onto the torso and the arm, drifting down: a fine mist (1.0: motes of the particle system)
            int mi = raid.fx.spawn(absentialis.engine.fx.ParticleSystem.MOTE, absentialis.engine.fx.ParticleSystem.GRAVITY | absentialis.engine.fx.ParticleSystem.LIT,
                    p.x + fx * 0.36f + rx * (0.12f + side * 0.1f), p.y + p.height() * (0.60f + up * 0.16f), p.z + fz * 0.36f + rz * (0.12f + side * 0.1f),
                    -fx * (1.3f + raid.rng.nextFloat()) + rx * side * 1.2f, 1.2f + raid.rng.nextFloat() * 0.8f, -fz * (1.3f + raid.rng.nextFloat()) + rz * side * 1.2f,
                    0.3f + raid.rng.nextFloat() * 0.25f, 0.03f + raid.rng.nextFloat() * 0.03f, 0.12f, 0.90f, 0.96f, 0.95f, 0.5f);
            raid.fx.tune(mi, 3f, 2.5f, 0);
        }
    }

    /** Third person: the muzzle and the ejection port of the weapon as the body is holding it (smoke and brass leave from there). */
    private void thirdPersonPorts(Actor p) {
        lensKnown = false; playerPortsKnown = false;
        ItemInstance h = held();
        if (h == null || h.def().weapon == null || h.def().weapon.melee() || p.dead || playerLook == null) return;
        c.ar.muzzle(p, playerLook, p.pitch, vt);
        float fx = (float) (-Math.sin(p.yaw) * Math.cos(p.pitch)), fy = (float) Math.sin(p.pitch), fz = (float) (-Math.cos(p.yaw) * Math.cos(p.pitch));
        float rx = (float) Math.cos(p.yaw), rz = (float) -Math.sin(p.yaw);
        playerPorts[0] = vt[0]; playerPorts[1] = vt[1]; playerPorts[2] = vt[2];
        float back = Math.min(0.5f, ItemModels.of(h).sx / 40f * 0.55f);
        playerPorts[3] = vt[0] - fx * back + rx * 0.05f; playerPorts[4] = vt[1] - fy * back + 0.03f; playerPorts[5] = vt[2] - fz * back + rz * 0.05f;
        playerPortsKnown = true;
        if (lightOnWeapon) { lensWorld[0] = vt[0] - fx * 0.18f; lensWorld[1] = vt[1] - 0.07f; lensWorld[2] = vt[2] - fz * 0.18f; lensKnown = true; }
    }

    private static final int[] MARK_COLORS = {SquadPanel.GOLD_COL, SquadPanel.RED_COL, SquadPanel.BLUE_COL};

    /**
     * 0.8: where the squad was sent, standing in the world where the order pointed - a staff with a pennant in the
     * team's colour over a ring on the ground, with a second ring that closes onto the spot. It is solid for a moment
     * and then fades: the staff sinks, the rings thin and the light it throws dies away.
     */
    /** 1.1: how far off a body's loot beam is drawn, how tall and how thick it is, and how bright at its foot. */
    public static float BEAM_RANGE = 46f, BEAM_HEIGHT = 3.4f, BEAM_WIDTH = 0.07f, BEAM_ALPHA = 0.30f;
    /** Beams drawn on the last frame (gates and the capture script). */
    public int lootBeams;

    /**
     * 1.1: a body with something on it says so. A thin column of light stands over every corpse that still has anything
     * to take - never the survivor's own - in the colour of the best thing it carries, by the same grades the inventory
     * uses (white for common, green, blue, purple, orange, gold). It is deliberately quiet: a few centimetres through,
     * a little over three metres tall, fading to nothing at the top, hardly there in daylight and plain in the dark; it
     * thins away as you walk up to it so that it is never in front of what you are searching; and a body that has been
     * emptied has none.
     */
    private void drawLootBeams(absentialis.engine.render.ModelRenderer mr) {
        lootBeams = 0;
        Actor pl = raid.player;
        for (Actor a : raid.actors) {
            if (!a.dead || a.isPlayer || a.corpse == null) continue;
            float d = a.distXZ(pl.x, pl.z);
            if (d > BEAM_RANGE) continue;
            int tier = Raid.bestLootTier(a);
            if (tier < 0) continue;
            int col = absentialis.gameplay.items.ItemDef.TIER_COLORS[Math.max(0, Math.min(5, tier))] & 0xFFFFFF;
            float near = Math.max(0, Math.min(1, (d - 1.6f) / 2.4f)), far = Math.max(0, Math.min(1, (BEAM_RANGE - d) / 8f));
            float pulse = 0.86f + 0.14f * (float) Math.sin(raid.time * 1.7f + a.id);
            float alpha = BEAM_ALPHA * near * far * pulse;
            if (alpha < 0.012f) continue;
            float bx = a.ragdoll != null ? a.ragdoll.x[absentialis.character.Ragdoll.PELVIS] : a.x, bz = a.ragdoll != null ? a.ragdoll.z[absentialis.character.Ragdoll.PELVIS] : a.z, by = a.y + 0.15f;
            int segs = 7;
            for (int k = 0; k < segs; k++) {
                float t0 = k / (float) segs, t1 = (k + 1) / (float) segs, fade = (1 - t0) * (1 - t0);
                float wdt = BEAM_WIDTH * (1 - 0.5f * t0);
                mr.beam(bx, by + BEAM_HEIGHT * t0, bz, bx, by + BEAM_HEIGHT * t1, bz, wdt, ((int) (alpha * fade * 255) << 24) | col, 1f);
            }
            // a soft foot of the same colour on the ground, so the eye finds which body it belongs to
            mr.beam(bx, by - 0.08f, bz, bx, by - 0.04f, bz, 0.5f, ((int) (alpha * 0.5f * 255) << 24) | col, 1f);
            lootBeams++;
        }
    }

    private void drawOrderMarks(absentialis.engine.render.ModelRenderer mr) {
        if (raid.orderMarks.isEmpty()) return;
        mr.flags = absentialis.engine.render.DrawList.NO_SHADOW;
        for (float[] m : raid.orderMarks) {
            float a = raid.orderMarkAlpha(m);
            if (a <= 0) continue;
            int col = MARK_COLORS[(int) m[4] % 3] | 0xFF000000;
            float age = raid.time - m[3], x = m[0], y = m[1] + 0.03f, z = m[2];
            boolean sweep = m[5] > 0;
            // the ring that marks the spot, and one closing in on it when the order is fresh
            ring(mr, x, y, z, sweep ? 1.6f : 0.75f, 0.05f * (0.4f + 0.6f * a), Ui.alpha(col, a));
            if (sweep) ring(mr, x, y, z, 0.75f, 0.03f, Ui.alpha(col, a * 0.7f));
            float close = Math.max(0, 1 - age / 0.45f);
            if (close > 0) ring(mr, x, y, z, 0.75f + close * 2.2f, 0.035f, Ui.alpha(col, (1 - close) * a));
            // the staff and its pennant: a thing standing there, in the team's colour
            float h = 1.9f * (0.35f + 0.65f * a), bob = (float) Math.sin(raid.time * 3.2f + m[0]) * 0.03f;
            tmp.identity().translate(x, y, z).scale(0.05f, h, 0.05f).translate(0, 0, 0);
            mr.cube(tmp, Ui.alpha(0xFFE8E4D8, a), 1, 0.35f);
            tmp.identity().translate(x, y + h - 0.32f + bob, z).rotateY(raid.time * 1.4f).translate(0.19f, 0, 0).scale(0.38f, 0.26f, 0.04f).translate(0, 0, 0);
            mr.cube(tmp, Ui.alpha(col, a), 1, 0.9f);
            tmp.identity().translate(x, y + h + bob, z).rotateY(raid.time * 1.4f).scale(0.11f, 0.11f, 0.11f).translate(0, -0.5f, 0);
            mr.cube(tmp, Ui.alpha(col, a), 1, 1);
        }
        mr.flags = 0;
    }

    private final float[] arcPts = new float[3 * 200];

    /**
     * Where the throw in hand will go (0.7): the path it will fly, drawn in the world as a dotted line from the hand,
     * the place it first touches down, and - for anything that bursts - a ring the size of what it will do there. It is
     * the raid's own flight step run ahead of time, so the line is where the grenade goes.
     */
    private void drawThrowArc(absentialis.engine.render.ModelRenderer mr) {
        ItemInstance g = currentThrow();
        if (g == null) return;
        Actor p = raid.player;
        float dx = (float) (-Math.sin(p.yaw) * Math.cos(p.pitch)), dy = (float) Math.sin(p.pitch), dz = (float) (-Math.cos(p.yaw) * Math.cos(p.pitch));
        int[] info = new int[2];
        int n = raid.predictThrow(g.def(), dx, dy, dz, throwPower(), arcPts, 200, info);
        if (n < 2) return;
        int land = info[0] >= 0 ? info[0] : n - 1;
        mr.flags = absentialis.engine.render.DrawList.NO_SHADOW;
        for (int i = 1; i <= land && i < n; i++) {
            if ((i & 1) == 0) continue;                                         // dotted
            int a = (i - 1) * 3, b = i * 3;
            float k = i / (float) Math.max(1, land);
            mr.beam(arcPts[a], arcPts[a + 1], arcPts[a + 2], arcPts[b], arcPts[b + 1], arcPts[b + 2], 0.02f + 0.02f * k, Ui.alpha(0xFFFFE08A, 0.85f - 0.3f * k), 1);
        }
        // after the first bounce: fainter, to where it will be when the fuse runs out
        for (int i = land + 1; i < n; i++) { if ((i & 1) == 0) continue; int a = (i - 1) * 3, b = i * 3; mr.beam(arcPts[a], arcPts[a + 1], arcPts[a + 2], arcPts[b], arcPts[b + 1], arcPts[b + 2], 0.018f, 0x70FFE08A, 1); }
        float lx = arcPts[land * 3], ly = arcPts[land * 3 + 1] + 0.04f, lz = arcPts[land * 3 + 2];
        ring(mr, lx, ly, lz, 0.32f, 0.035f, 0xFFFFE08A);
        absentialis.gameplay.items.ItemDef.Throwable th = g.def().throwable;
        float ex = arcPts[(n - 1) * 3], ey = arcPts[(n - 1) * 3 + 1] + 0.05f, ez = arcPts[(n - 1) * 3 + 2];
        if (th.radius > 0.5f) ring(mr, ex, ey, ez, th.radius, 0.03f, "fire".equals(th.kind) ? 0x90FF8A30 : "flash".equals(th.kind) ? 0x90A0C8FF : 0x90FF5040);
        mr.flags = 0;
    }

    /**
     * 0.7, the NeuralAI overlay in the world (console: ai). For every person near enough to matter it draws what that
     * mind has to go on, and only that: the cone it sees in, the place it believes its target to be with the circle of
     * its doubt round it, where it is going and what it means to get behind, and - for a squad that is searching - the
     * regions of its search, brighter the more it expects of them. One thing more is drawn, in magenta and labelled as
     * such: where the target really is. That is for whoever is tuning; no mind is ever given it.
     */
    private void drawAiDebug(absentialis.engine.render.ModelRenderer mr) {
        absentialis.neuralai.runtime.NeuralAi ai = raid.neural;
        mr.flags = absentialis.engine.render.DrawList.NO_SHADOW;
        java.util.Set<Long> squadsDrawn = new java.util.HashSet<>();
        for (Actor a : raid.actors) {
            if (a.isPlayer || a.dead || a.distXZ(raid.player.x, raid.player.z) > 70) continue;
            absentialis.neuralai.runtime.Mind m = ai.mind(a.id);
            if (m == null) continue;
            float ex = a.x, ey = a.eyeY(), ez = a.z;
            // the cone of sight (the far half-angle; close in it is wider). 0.8: a mind without eyes has no cone - nothing is drawn for it,
            // because there is nothing there; what it has instead is the reach of its ears, drawn as a ring on the ground
            absentialis.neuralai.api.GameWorld.SelfState s = raid.neuralWorld.self(a.id);
            float half = (float) Math.toRadians(s.arcFarDeg()), len = (float) Math.min(16, s.sightRange());
            if (!m.blind) for (int k = -1; k <= 1; k += 2) { float h = a.yaw + k * half; mr.beam(ex, ey, ez, ex - (float) Math.sin(h) * len, ey, ez - (float) Math.cos(h) * len, 0.015f, m.creature ? 0x60FFB060 : 0x6080FF90, 1); }
            else ring(mr, a.x, a.y + 0.08f, a.z, 9f * (float) s.hearing() / 2.4f, 0.03f, 0x7060B0FF);
            absentialis.neuralai.cognition.TargetBelief b = m.belief;
            if (b.known()) {
                int col = switch (b.kind()) { case VISIBLE -> 0xFFFF4030; case LAST_SEEN -> 0xFFFF9A30; case APPROXIMATE -> 0xFFFFE060; default -> 0xFF60D8FF; };
                float bx = (float) b.estimatedPosition().x(), by = (float) b.estimatedPosition().y() + 0.06f, bz = (float) b.estimatedPosition().z();
                ring(mr, bx, by, bz, Math.max(0.35f, (float) b.radiusMeters()), 0.03f, col);
                mr.beam(ex, ey, ez, bx, by + 1.1f, bz, 0.012f, Ui.alpha(col, 0.55f), 1);
                // the truth, for the developer: never an input to anything above
                Actor t = b.targetId() > 0 ? raid.actor((int) b.targetId()) : null;
                if (t != null && !t.dead) { ring(mr, t.x, t.y + 0.05f, t.z, 0.5f, 0.045f, 0xFFFF40FF); mr.beam(t.x, t.y, t.z, t.x, t.y + 2.6f, t.z, 0.02f, 0xFFFF40FF, 1); }
            }
            if (m.cover != null) mr.beam(a.x, a.y + 0.2f, a.z, (float) m.cover.position().x(), (float) m.cover.position().y() + 0.2f, (float) m.cover.position().z(), 0.02f, 0xFF50A0FF, 1);
            else if (m.destination != null) mr.beam(a.x, a.y + 0.2f, a.z, (float) m.destination.x(), (float) m.destination.y() + 0.2f, (float) m.destination.z(), 0.02f, 0xFFFFFFFF, 1);
            absentialis.neuralai.group.SquadCoordinator sq = ai.squad(m);
            if (sq != null && sq.search != null && squadsDrawn.add(sq.id)) {
                for (absentialis.neuralai.spatial.SearchGrid.Cell c : sq.search.cells()) {
                    float p = (float) Math.min(1, c.probability * 6);
                    boolean held = c.reservedUntil > raid.time;
                    ring(mr, (float) c.centre.x(), (float) c.centre.y() + 0.05f, (float) c.centre.z(), held ? 0.9f : 0.6f, 0.03f, held ? 0xFFFFFFFF : Ui.alpha(0xFF40FF80, 0.2f + 0.8f * p));
                }
            }
        }
    }

    private void ring(absentialis.engine.render.ModelRenderer mr, float x, float y, float z, float r, float thick, int col) {
        int seg = Math.max(12, Math.min(40, (int) (r * 10)));
        for (int k = 0; k < seg; k++) {
            double a0 = k * Math.PI * 2 / seg, a1 = (k + 1) * Math.PI * 2 / seg;
            mr.beam(x + (float) Math.sin(a0) * r, y, z + (float) Math.cos(a0) * r, x + (float) Math.sin(a1) * r, y, z + (float) Math.cos(a1) * r, thick, col, 1);
        }
    }

    /** One viewmodel arm: forearm and hand run from an off-screen elbow to the hand target, upper arm behind it. */
    private void arm(ActorRenderer.Look look, boolean right, float tx, float ty, float tz, float ex, float ey, float ez, float light) {
        float ux = tx - ex, uy = ty - ey, uz = tz - ez, ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1e-4f) return;
        ux /= ul; uy /= ul; uz /= ul;
        float k = VM_VOX, fore = 7 * k;
        // forearm pivot (elbow) sits a forearm-and-hand behind the target
        float px = tx - ux * fore, py = ty - uy * fore, pz = tz - uz * fore;
        int up = right ? Rig.R_UPPER_ARM : Rig.L_UPPER_ARM;
        Rig.aimBone(vmBone, px, py, pz, ux, uy, uz, k);
        c.ar.drawPart(look, up + 1, vmBone, light);
        Rig.aimBone(vmBone, px + ux * 5 * k, py + uy * 5 * k, pz + uz * 5 * k, ux, uy, uz, k);
        c.ar.drawPart(look, up + 2, vmBone, light);
        // upper arm from an off-screen shoulder down to the elbow
        float sx = right ? 0.24f : -0.22f, sy = -0.46f, sz = 0.10f;
        float wx = px - sx, wy = py - sy, wz = pz - sz, wl = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
        wx /= wl; wy /= wl; wz /= wl;
        Rig.aimBone(vmBone, px - wx * 5 * k, py - wy * 5 * k, pz - wz * 5 * k, wx, wy, wz, k);
        c.ar.drawPart(look, up, vmBone, light);
    }

    /**
     * The picture through a magnified scope: the tube's shadow round a clear circle, a fine crosshair that thickens
     * toward the edge (the eye finds the centre by following the bars in), range marks on the lower limb, and no
     * dot over the point of aim - only where the hairs would cross.
     */
    private void scopeOverlay(Ui ui, int W, int H) {
        float r = Math.min(W, H) * 0.44f;
        float cx = W / 2f, cy = H / 2f;
        ui.b.rect(0, 0, cx - r, H, 0xFF000000); ui.b.rect(cx + r, 0, W - cx - r, H, 0xFF000000);
        ui.b.rect(cx - r, 0, 2 * r, cy - r, 0xFF000000); ui.b.rect(cx - r, cy + r, 2 * r, H - cy - r, 0xFF000000);
        // the round edge of the tube: the corners of the square are filled in, row by row
        int rows = (int) r, step = Math.max(1, ui.s(2));
        for (int y = -rows; y < rows; y += step) {
            float half = (float) Math.sqrt(Math.max(0, r * r - (y + step * 0.5f) * (y + step * 0.5f)));
            ui.b.rect(cx - r, cy + y, r - half, step, 0xFF000000); ui.b.rect(cx + half, cy + y, r - half, step, 0xFF000000);
            float soft = r * 0.06f;
            ui.b.rect(cx - half, cy + y, soft, step, 0x70000000); ui.b.rect(cx + half - soft, cy + y, soft, step, 0x70000000);
        }
        float gap = r * 0.035f, thin = Math.max(1, ui.s(1)), thick = Math.max(2, ui.s(3)), inner = r * 0.42f;
        ui.b.rect(cx - inner, cy - thin / 2, inner - gap, thin, 0xE0000000); ui.b.rect(cx + gap, cy - thin / 2, inner - gap, thin, 0xE0000000);
        ui.b.rect(cx - thin / 2, cy - inner, thin, inner - gap, 0xE0000000); ui.b.rect(cx - thin / 2, cy + gap, thin, inner - gap, 0xE0000000);
        ui.b.rect(cx - r, cy - thick / 2, r - inner, thick, 0xF0000000); ui.b.rect(cx + inner, cy - thick / 2, r - inner, thick, 0xF0000000);
        ui.b.rect(cx - thick / 2, cy + inner, thick, r - inner, 0xF0000000);
        for (int k = 1; k <= 3; k++) { float my = cy + gap + (inner - gap) * k / 4f, mw = r * (0.05f - k * 0.008f); ui.b.rect(cx - mw, my, mw * 2, thin, 0xE0000000); }
    }

    /**
     * Looking through a red dot or a holographic sight: the dot (or ring and dot) the sight projects, at the point of
     * aim. It is small, bright and sits on the target without hiding it; the weapon's own sight window frames it.
     */
    private void dotOverlay(Ui ui, int W, int H, boolean holo, float k) {
        float cx = W / 2f, cy = H / 2f;
        int red = Ui.alpha(0xFFFF3A28, k), glow = Ui.alpha(0xFFFF3A28, k * 0.22f);
        float d = Math.max(2, ui.s(3));
        ui.b.rect(cx - d * 1.5f, cy - d * 1.5f, d * 3, d * 3, glow);
        ui.b.rect(cx - d / 2, cy - d / 2, d, d, red);
        if (!holo) return;
        float rr = ui.s(26), t = Math.max(1, ui.s(2));
        for (int i = 0; i < 48; i++) {
            if (i % 12 == 0) continue;                              // four breaks in the ring, at the quarters
            double a = i / 48.0 * Math.PI * 2;
            ui.b.rect(cx + (float) Math.cos(a) * rr - t / 2, cy + (float) Math.sin(a) * rr - t / 2, t, t, red);
        }
        for (int q = 0; q < 4; q++) { float ax = q == 0 ? 1 : q == 2 ? -1 : 0, ay = q == 1 ? 1 : q == 3 ? -1 : 0; ui.b.rect(cx + ax * (rr + ui.s(5)) - (ax != 0 ? ui.s(4) : t / 2), cy + ay * (rr + ui.s(5)) - (ay != 0 ? ui.s(4) : t / 2), ax != 0 ? ui.s(8) : t, ay != 0 ? ui.s(8) : t, red); }
    }

    private void drawMinimap(Ui ui) {
        // 1.1, the map, quietened. The sheet is still the field map, but under a grey wash that takes the beige out of it and
        // flattens its contrast, so that it is a background; its frame is one thin line; and the two things it is looked at
        // for stand out of it - the survivor (a white arrow with a dark edge, always at the middle) and the way out (amber,
        // and when it is off the sheet, a pip on the edge in its direction). The place is still named under it. No compass.
        float safe = hud11.safe(ui);
        float s = ui.s(190), x = ui.w - s - safe - ui.s(18), y = safe + ui.s(18);
        float span = 120;
        c.map.draw(ui, c.profile, x, y, s, s, raid.player.x - span / 2, raid.player.z - span / 2, span, raid.exits, raid.player.x, raid.player.z, raid.player.yaw, false);
        ui.b.rect(x, y, s, s, 0x6A262A2C);
        status.minimapContacts(ui, x, y, s, raid.player.x - span / 2, raid.player.z - span / 2, span);
        ui.b.outline(Math.round(x), Math.round(y), Math.round(s), Math.round(s), 1, 0xB0D8D4C6);
        // the ways out that can be used now
        float k = s / span, mcx = x + s / 2, mcy = y + s / 2;
        for (Raid.ExitState ex : raid.exits) {
            if (!ex.open || !ex.eligible && !ex.signaled) continue;
            float ex0 = (ex.exit.x - raid.player.x) * k, ez0 = (ex.exit.z - raid.player.z) * k, lim = s / 2 - ui.s(7);
            boolean off = Math.abs(ex0) > lim || Math.abs(ez0) > lim;
            if (off) { float m2 = Math.max(Math.abs(ex0), Math.abs(ez0)); ex0 = ex0 / m2 * lim; ez0 = ez0 / m2 * lim; }
            float px = mcx + ex0, py = mcy + ez0, q = off ? ui.s(4) : ui.s(6);
            ui.b.line(px - q - 1, py, px, py - q - 1, ui.s(4), 0xC0000000); ui.b.line(px, py - q - 1, px + q + 1, py, ui.s(4), 0xC0000000); ui.b.line(px + q + 1, py, px, py + q + 1, ui.s(4), 0xC0000000); ui.b.line(px, py + q + 1, px - q - 1, py, ui.s(4), 0xC0000000);
            ui.b.line(px - q, py, px, py - q, ui.s(2), 0xFFE2A034); ui.b.line(px, py - q, px + q, py, ui.s(2), 0xFFE2A034); ui.b.line(px + q, py, px, py + q, ui.s(2), 0xFFE2A034); ui.b.line(px, py + q, px - q, py, ui.s(2), 0xFFE2A034);
        }
        // the survivor: an arrow, pointing the way they face
        {
            float yw = raid.player.yaw, fx = (float) -Math.sin(yw), fz = (float) -Math.cos(yw), rx2 = (float) Math.cos(yw), rz2 = (float) -Math.sin(yw), L = ui.s(9), Wd = ui.s(6);
            float tx = mcx + fx * L, tz = mcy + fz * L, lx = mcx - fx * L * 0.7f - rx2 * Wd, lz = mcy - fz * L * 0.7f - rz2 * Wd, rx3 = mcx - fx * L * 0.7f + rx2 * Wd, rz3 = mcy - fz * L * 0.7f + rz2 * Wd, bx = mcx - fx * L * 0.25f, bz = mcy - fz * L * 0.25f;
            for (int pass = 0; pass < 2; pass++) {
                float th = pass == 0 ? ui.s(5) : ui.s(2.4f);
                int col = pass == 0 ? 0xE0000000 : 0xFFFFFFFF;
                ui.b.line(tx, tz, lx, lz, th, col); ui.b.line(lx, lz, bx, bz, th, col); ui.b.line(bx, bz, rx3, rz3, th, col); ui.b.line(rx3, rz3, tx, tz, th, col);
            }
        }
        District d = raid.world.district(raid.player.x, raid.player.z);
        // the hour and the place under the map - the only clock on the screen
        Hud11.shade(ui, x - ui.s(14), y + s + ui.s(2), s + ui.s(28), ui.s(34), 0.9f);
        { String dn = d.display.toUpperCase(); float ds = absentialis.ui.Layout.fitSize(ui, dn, s - ui.s(104), 15, 10); Hud11.txtR(ui, absentialis.ui.Layout.elide(ui, dn, s - ui.s(104), ds), x + s, y + s + ui.s(9), ds, Ui.TXT_HI); }
        float out = raid.world.exposure(raid.player.x, raid.player.z);
        if (out > 0) { Hud11.shade(ui, x - ui.s(14), y + s + ui.s(32), s + ui.s(28), ui.s(30), 1); Hud11.cross(ui, x + s - ui.tw("RADIATION ZONE", 15) - ui.s(18), y + s + ui.s(40), ui.s(12), Ui.RED); Hud11.txtR(ui, "RADIATION ZONE", x + s, y + s + ui.s(37), 15, Ui.RED); }
        // the clock runs: schedules, staffing and what the dark hides all follow it
        double hr = raid.hour() % 24;
        Hud11.txt(ui, String.format("%02d:%02d", (int) hr, (int) (hr * 60) % 60), x, y + s + ui.s(8), 16, raid.night() ? Ui.BLUE : Ui.TXT_HI);
        Hud11.txt(ui, raid.night() ? "NIGHT" : raid.daylight() < 0.95f ? (hr < 12 ? "DAWN" : "DUSK") : "DAY", x + ui.s(52), y + s + ui.s(11), 12, Ui.TXT_DIM);
        if (Block.trunk(raid.world.get((int) Math.floor(raid.player.x), World.GROUND - 1, (int) Math.floor(raid.player.z))) || Block.trunk(raid.world.get((int) Math.floor(raid.player.x) + 2, World.GROUND - 1, (int) Math.floor(raid.player.z))) || Block.trunk(raid.world.get((int) Math.floor(raid.player.x), World.GROUND - 1, (int) Math.floor(raid.player.z) + 2)))
            ui.textR("RING ROAD - follow the double line to any district", x + s, y + s + ui.s(66), 12, Ui.AMBER);
    }

    private void drawMap(Ui ui) {
        ui.b.rect(0, 0, ui.w, ui.h, 0xD0080807);
        float s = Math.min(ui.w, ui.h) - ui.s(120), x = (ui.w - s) / 2, y = ui.s(70);
        ui.window("Field Map  -  " + raid.profile.zone.day + "d " + String.format("%02d:00", (int) (raid.profile.zone.hour + raid.time / 120) % 24), "map", x - ui.s(12), y - ui.s(56), s + ui.s(24), s + ui.s(70));
        float span = World.W / mapZoom;
        // zoomed in, the view follows the survivor and stops at the edge of the sheet; zoomed out past the whole city the sheet sits in the middle of the table (0.9)
        float cx = span >= World.W ? World.W / 2f : Math.max(span / 2, Math.min(World.W - span / 2, raid.player.x)), cz = span >= World.D ? World.D / 2f : Math.max(span / 2, Math.min(World.D - span / 2, raid.player.z));
        c.map.draw(ui, c.profile, x, y, s, s, cx - span / 2, cz - span / 2, span, raid.exits, raid.player.x, raid.player.z, raid.player.yaw, true);
        // 1.0: the advisors (a square, bright when they have something to say) and the place a mission in hand is sending the survivor (a ring)
        if (raid.chains != null) { float mk = s / span, wx0 = cx - span / 2, wz0 = cz - span / 2;
          for (float[] cm : raid.chains.marks()) {
            float mx = x + (cm[0] - wx0) * mk, my = y + (cm[1] - wz0) * mk;
            if (mx < x || my < y || mx > x + s || my > y + s) continue;
            if (cm[2] == 2) { ui.b.outline(mx - ui.s(7), my - ui.s(7), ui.s(14), ui.s(14), 2, 0xFFE2A034); continue; }
            int col = cm[2] == 1 ? 0xFFF4EEDC : 0xFF8E897A;
            ui.b.rect(mx - ui.s(5), my - ui.s(5), ui.s(10), ui.s(10), 0xFF14100C); ui.b.rect(mx - ui.s(3), my - ui.s(3), ui.s(6), ui.s(6), col);
          } }
        // 0.9: the hotspots the survivor has been told of, where they are
        { float mk = s / span, wx0 = cx - span / 2, wz0 = cz - span / 2; int n = 0;
          for (DistrictEvents.Live l : raid.events.hotspots) {
            if (!l.announced) continue;
            float mx = x + (l.x - wx0) * mk, my = y + (l.z - wz0) * mk;
            if (mx < x || my < y || mx > x + s || my > y + s) continue;
            int col = l.resolved ? 0xFF7A766C : 0xFFE03A2E;
            ui.b.rect(mx - ui.s(5), my - ui.s(5), ui.s(10), ui.s(10), 0xFF14100C); ui.b.rect(mx - ui.s(3), my - ui.s(3), ui.s(6), ui.s(6), col);
            ui.font.draw(ui.b, l.def.name() + (l.resolved ? " (over)" : ""), mx + ui.s(8), my - ui.s(6), 12 * ui.scale, col);
            n++;
          } }
        c.map.legend(ui, c.profile, x + s + ui.s(30), y);
        float ly = y + ui.s(220);
        ui.text("EXITS", x + s + ui.s(30), ly, 14, Ui.TXT_HI);
        for (Raid.ExitState e : raid.exits) {
            ly += ui.s(34);
            if (ly > ui.h - ui.s(40)) break;
            ui.text(e.exit.name, x + s + ui.s(30), ly, 13, e.eligible ? Ui.GREEN : Ui.TXT_DIM);
            ui.text(e.requirement, x + s + ui.s(30), ly + ui.s(15), 10, Ui.TXT_DIM);
        }
        ui.text("[Scroll] zoom   [M] close", x, y + s + ui.s(8), 12, Ui.TXT_DIM);
    }

    private void drawInventory(Ui ui, float dt) {
        ui.b.rect(0, 0, ui.w, ui.h, 0xD0060706);
        var inv = c.inv;
        // 1.0: a cell is one size, whatever the rank of the case - a bigger case is a bigger panel, not smaller squares
        // (it only gives way on a screen too small to hold the whole of it)
        inv.begin(48);
        Container pack = lo().packGrid();
        inv.fit(ui.h - ui.s(190), lo().caseGrid.h + (pack == null ? 0 : pack.h + 1));
        float pad = ui.s(14), m = ui.s(14);
        float rightW = panel.width(lo()), rightH = ui.s(44) + panel.height(lo()) + m;
        // the left panel is there when there is something to look into - and only then. An opened container that has nothing in it still shows: it says Empty.
        boolean left = lootSide != null, body = left && lootBody != null && lootBody.corpseGear != null;
        float gearH0 = body ? panel.dollH(false) + ui.s(34) : 0;
        float leftW = left ? Math.max(Math.max(lootSide.w * inv.cell, body ? panel.dollW() : 0) + m * 2, ui.s(230)) : 0;
        float leftH = left ? ui.s(44) + gearH0 + lootSide.h * inv.cell + m + ui.s(8) : 0;
        float statW = ui.s(250), statH = ui.s(388);
        float total = (left ? leftW + pad : 0) + rightW + m * 2 + pad + statW;
        float x0 = Math.round(Math.max(pad, (ui.w - total) / 2));
        float top = Math.round(Math.max(ui.s(30), (ui.h - Math.max(rightH, Math.max(leftH, statH))) / 2 - ui.s(12)));
        if (left) {
            String title = lootTitle != null && !lootTitle.isEmpty() ? lootTitle : lootSide.label != null && !lootSide.label.isEmpty() ? lootSide.label : "Container";
            float cy = ui.window(title, "search", x0, top, leftW, leftH);
            float gx = x0 + Math.round((leftW - lootSide.w * inv.cell) / 2), gearH = body ? bodyGear(ui, lootBody, x0 + Math.round((leftW - panel.dollW()) / 2), cy, panel.dollW()) : 0;
            inv.grid(lootSide, gx, cy + ui.s(2) + gearH, null);
            if (lootSide.items.isEmpty()) ui.textC("Empty", x0 + leftW / 2, cy + gearH + lootSide.h * inv.cell / 2 - ui.s(10), 20, Ui.TXT_DIM);
            x0 += leftW + pad;
        }
        // the survivor: equipment as it is worn, the case beside it, what can be done under the case
        float ry = ui.window(c.profile.name == null || c.profile.name.isEmpty() ? "Survivor" : c.profile.name, "inv", x0, top, rightW + m * 2, rightH);
        panel.draw(c.profile, x0 + m, ry, rightW, rightH - (ry - top) - m, lootSide);
        // the condition of the body, in one narrow column
        status.statusPanel(ui, new absentialis.ui.Layout.Box(x0 + rightW + m * 2 + pad, top, statW, statH), held(), playerLook);
        if (c.in.pressed("sort")) { InventoryService.Result r = InventoryService.autoSort(lo().caseGrid); inv.say(r.ok() ? "Case sorted" : r.msg(), r.ok() ? Ui.GREEN : Ui.RED); }
        inv.end();
        // 0.6: the key that opened the menu closes it. A tap of [F] (the interact binding) shuts the menu, as Escape does;
        // holding it past the tap time over an item is the full-screen inspect it always was. The press that opened the
        // menu is ignored until the key has been let go once.
        boolean fDown = c.in.down("interact");
        boolean shut = false;
        if (inv.inspectOpen != null) { fHeld = 0; inspectArmed = false; inspectWait = true; }
        else if (!fDown) {
            if (!inspectWait && !inspectArmed && fHeld > 0 && fHeld < F_TAP) shut = true;
            inspectWait = false; inspectArmed = false; fHeld = 0;
        } else if (!inspectWait) {
            fHeld += dt;
            if (!inspectArmed && fHeld >= F_TAP && inv.hover != null) { inspectArmed = true; inv.inspected = inv.hover; }
        }
        if (inspectArmed) inv.fullInspect(inspectYaw + inv.spin * 0.2f, inspectPitch);
        autoCloseLoot(dt);
        if (shut && invOpen) closeInventory();
    }

    /** The body being searched (null for a container). */
    private Actor lootBody;
    /** The slots a body's equipment is shown in, in the order they are laid out: what is in the hands, then what is worn. */
    public static final String[] BODY_SLOTS = {"primary", "sidearm", "melee", "head", "face", "armor", "backpack"};

    /**
     * 0.9: what a body has on it, as equipment - each thing in the slot it was worn or held in, the same slots the
     * survivor's own kit sits in on the other side of the screen. Click and drag, or shift-click to take. Returns the height used.
     */
    private float bodyGear(Ui ui, Actor body, float x, float y, float w) {
        // 1.0: laid out as on a body - the same figure the survivor's own equipment is shown on
        int on = 0;
        for (ItemInstance it : body.corpseGear.slots.values()) if (it != null) on++;
        float dh = panel.doll(body.corpseGear, x, y, lo().caseGrid, false, 0, 1);
        ui.textC(on == 0 ? "nothing left on the body" : on + " item" + (on == 1 ? "" : "s") + " on the body", x + w / 2, y + dh + ui.s(8), 13, Ui.TXT_DIM);
        return dh + ui.s(34);
    }

    private void drawPause(Ui ui) {
        ui.b.rect(0, 0, ui.w, ui.h, 0xB0000000);
        float w = ui.s(360), h = ui.s(330), x = (ui.w - w) / 2, y = (ui.h - h) / 2;
        float cy = ui.window("Paused", "cog", x, y, w, h);
        float bw = w - ui.s(48), bh = ui.s(44);
        if (ui.button("p_resume", x + ui.s(24), cy + ui.s(6), bw, bh, "RESUME", "arrow", true)) paused = false;
        if (ui.button("p_settings", x + ui.s(24), cy + ui.s(58), bw, bh, "SETTINGS", "cog", true)) pauseAction = "settings";
        if (ui.button("p_abandon", x + ui.s(24), cy + ui.s(110), bw, bh, "ABANDON OPERATION", "flag", true)) { raid.debrief.outcome = Raid.Debrief.Outcome.ABANDONED; raid.debrief.reason = "Abandoned the operation"; paused = false; }
        if (ui.button("p_quit", x + ui.s(24), cy + ui.s(162), bw, bh, "QUIT TO DESKTOP", "cross", true)) pauseAction = "quit";
        ui.wrap("Abandoning or quitting ends the operation where you stand. You keep everything you are carrying; you give up the extraction pay.", x + ui.s(24), cy + ui.s(218), bw, 11, Ui.TXT_DIM);
    }

    public String pauseAction;

    private void aiDebug(Ui ui) {
        float[] o = new float[3];
        for (Actor a : raid.actors) {
            if (a.isPlayer || a.dead || a.distXZ(raid.player.x, raid.player.z) > 80) continue;
            float w = pv.project(a.x, a.y + a.height() + 0.4f, a.z, o);
            if (w <= 0) continue;
            float sx = (o[0] * 0.5f + 0.5f) * ui.w, sy = (1 - (o[1] * 0.5f + 0.5f)) * ui.h;
            int col = "engage".equals(a.state) ? Ui.RED : "investigate".equals(a.state) || "search".equals(a.state) ? Ui.AMBER : Ui.GREEN;
            ui.textC(a.faction + "/" + a.archetype + " " + a.state + (a.hostileToPlayer ? " H" : "") + String.format(" aw%.1f T%d", a.awareness, a.tier), sx, sy, 11, col);
            if (!a.infected()) ui.textC(absentialis.neuralai.integration.NeuralHost.describe(a) + (a.memConfirmed ? "  seen" : "  hearsay") + String.format(" %.0fs  err %.0fm", raid.time - a.memTime, a.memErr) + (a.anat == null ? "" : "  " + AIInjuryAdapter.describe(a)), sx, sy + ui.s(12), 10, Ui.TXT_DIM);
        }
        // NeuralAI: one line per mind in view (what it is, believes and is doing) and the last thing its trace recorded
        float[] q = new float[3];
        for (Actor a : raid.actors) {
            if (a.isPlayer || a.dead || a.distXZ(raid.player.x, raid.player.z) > 60) continue;
            absentialis.neuralai.runtime.Mind m = raid.neural.mind(a.id);
            if (m == null || pv.project(a.x, a.y + a.height() + 0.4f, a.z, q) <= 0) continue;
            float sx = (q[0] * 0.5f + 0.5f) * ui.w, sy = (1 - (q[1] * 0.5f + 0.5f)) * ui.h;
            ui.textC(raid.neural.describe(a.id), sx, sy + ui.s(26), 10, 0xFFE8E0C8);
            ui.textC(m.trace.last(), sx, sy + ui.s(38), 10, Ui.TXT_DIM);
        }
        ui.text("NeuralAI: green cone = sight (amber: an infected; none at all: no eyes, and the blue ring is how far it hears a footstep)   ring = believed position and its uncertainty (red seen / orange last seen / yellow approximate / cyan evidence)   blue = cover   white = destination, reserved search region   green rings = search regions", ui.s(8), ui.s(126), 10, Ui.TXT_DIM);
        ui.text("MAGENTA = where the target ACTUALLY is (developer only - no mind is given this)", ui.s(8), ui.s(140), 10, 0xFFFF40FF);
        ui.text(String.format("NeuralAI: %d minds, %d decisions, %d action changes, %d interrupts, %d weapon commands (%d speculative), %d held for a friend", raid.neural.minds().size(), raid.neural.decisions, raid.neural.actionChanges, raid.neural.interrupts, raid.neural.weaponCommands, raid.neural.speculativeCommands, raid.friendlyHolds), ui.s(8), ui.s(154), 10, Ui.TXT_DIM);
        ui.text("AI tiers: active " + raid.actors.stream().filter(a -> !a.dead && a.tier == 0).count() + "  regional " + raid.actors.stream().filter(a -> !a.dead && a.tier == 1).count() + "  population " + raid.populationTotal(), ui.s(8), ui.s(110), 12, Ui.AMBER);
    }
}
