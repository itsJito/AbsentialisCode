package absentialis.scene;

import static org.lwjgl.glfw.GLFW.*;

import absentialis.engine.Input;
import absentialis.entity.Actor;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.neuralai.integration.Archetypes;
import absentialis.neuralai.integration.NeuralHost;
import absentialis.sim.ZoneState;
import absentialis.ui.Ui;
import absentialis.vehicle.CarHandlingData;
import absentialis.world.District;
import absentialis.world.MapData;
import java.util.ArrayList;
import java.util.List;

/**
 * Developer Mode (0.8; console: {@code devmode}). It takes the player character out of the game and leaves a
 * camera: no body, no weapon, no health, no survival HUD - the user flies through the world (noclip), and nothing in
 * it knows they are there. Nobody sees them, hears them, touches them or shoots at them.
 *
 * What is on screen instead is a tool:
 *
 *   a status bar, always: coordinates, district, facing, the hour, how many actors and minds are running, the frame rate;
 *   the workbench ([Tab]): a panel for putting things in the world - people and infected, weapons, every item,
 *     vehicles - where the camera is looking, with a search box, a count and options; and a World page for the clock,
 *     the weather, freezing or clearing the AI, and going to any landmark;
 *   the inspector: whatever is under the middle of the screen - a body's kind, faction, health, what its mind is doing.
 *
 * Leaving it ({@code devmode} again) puts the survivor back on their feet under the camera with everything they had.
 */
public final class DevMode {
    public static final String[] TABS = {"NPCS", "WEAPONS", "ITEMS", "VEHICLES", "WORLD", "PROFILE"};
    /** Flying speed in metres a second, and how much faster with [Shift]. */
    public static float FLY = 12f, FLY_FAST = 4f;

    private final RaidScreen screen;
    public boolean active, panel;
    private boolean godWas, noclipWas, thirdWas;
    public int tab, count = 1;
    public boolean hostile = true, frozen;
    private final StringBuilder search = new StringBuilder();
    private float scroll;
    private String status = "";
    private float statusAt = -99;
    /** Things put in the world from here this session (the status bar shows it; tests read it). */
    public int spawned;
    /** Where the middle of the screen is looking: the spot things are put. */
    public final float[] target = new float[3];
    public boolean targetKnown;
    public Actor inspected;

    public DevMode(RaidScreen screen) { this.screen = screen; }

    private Raid raid() { return screen.raid; }

    /** Turns the mode on or off. Returns what to tell the console. */
    public String toggle() {
        Raid r = raid();
        if (!active) {
            active = true; panel = false;
            godWas = r.god; noclipWas = r.noclip; thirdWas = screen.thirdPerson;
            r.god = true; r.noclip = true; r.devGhost = true;
            screen.thirdPerson = false;
            r.player.prone = false; r.player.crouch = false; r.player.vx = r.player.vy = r.player.vz = 0;
            r.player.y += 1.2f;                                    // lift off: the camera is nobody's eyes now
            return "Developer Mode ON - you are a camera. [Tab] workbench, [WASD] fly, [Space]/[Ctrl] up and down, [Shift] fast. 'devmode' again to leave.";
        }
        active = false; panel = false;
        r.god = godWas; r.noclip = noclipWas; r.devGhost = false;
        screen.thirdPerson = thirdWas;
        // back on their feet, under where the camera was
        int gy = r.groundY((int) Math.floor(r.player.x), (int) Math.floor(r.player.z));
        if (gy >= 0 && !noclipWas) r.player.y = gy;
        r.player.vx = r.player.vy = r.player.vz = 0;
        return "Developer Mode OFF - the survivor is back where the camera was.";
    }

    private void say(String s) { status = s; statusAt = raid().time; }

    /** Each frame while active, before the world updates: the panel's keys, and flying when it is closed. Returns true if the panel has the keyboard. */
    public boolean update(Input in, float dt) {
        Raid r = raid();
        // (1.1: the inventory key opens the workbench but does not shut it - it is a letter, and letters are for the search box)
        if (in.keyPressed(GLFW_KEY_TAB) || !panel && in.pressed("inventory")) { panel = !panel; in.typed.clear(); screen.c.sfx(panel ? "inv_select" : "ui_back", 0.4f); }
        if (panel && in.keyPressed(GLFW_KEY_ESCAPE)) panel = false;
        aim();
        if (!panel) {
            fly(in, dt);
            // 1.1: [G] picks up what is looked at - a body, an animal, a vehicle, something lying on the ground - and [G] again puts it down
            // 1.2: so does a click. The left button takes hold of what the middle of the screen is on (a prop too) and lets it go;
            // the wheel brings what is held nearer or sends it further
            if (in.keyPressed(GLFW_KEY_G) || in.mousePressed(0)) { if (holding()) say(putDown()); else say(pickUp()); }
            if (holding()) {
                if (in.scroll != 0) holdDist = Math.max(1.5f, Math.min(60, holdDist + (float) in.scroll * 0.8f));
                if (in.keyDown(GLFW_KEY_LEFT_BRACKET)) holdDist = Math.max(1.5f, holdDist - 9 * dt);
                if (in.keyDown(GLFW_KEY_RIGHT_BRACKET)) holdDist = Math.min(60, holdDist + 9 * dt);
                if (in.keyDown(GLFW_KEY_COMMA)) holdYaw += 2.2f * dt;
                if (in.keyDown(GLFW_KEY_PERIOD)) holdYaw -= 2.2f * dt;
                carry();
            }
            // quick keys with the panel shut: repeat the last spawn, delete what is looked at
            if (in.keyPressed(GLFW_KEY_DELETE) && inspected != null) { remove(inspected); say("Removed " + label(inspected)); }
            return false;
        }
        if (holding()) carry();
        // the search box takes what is typed
        while (!in.typed.isEmpty()) { char ch = in.typed.poll(); if (ch >= 32 && ch < 127 && ch != '`' && search.length() < 24) search.append(ch); }
        if (in.keyPressed(GLFW_KEY_BACKSPACE) && search.length() > 0) search.setLength(search.length() - 1);
        r.player.vx = r.player.vy = r.player.vz = 0;
        return true;
    }

    private void fly(Input in, float dt) {
        Actor p = raid().player;
        float f = (in.down("forward") ? 1 : 0) - (in.down("back") ? 1 : 0), s = (in.down("right") ? 1 : 0) - (in.down("left") ? 1 : 0);
        float fx = (float) (-Math.sin(p.yaw) * Math.cos(p.pitch)), fy = (float) Math.sin(p.pitch), fz = (float) (-Math.cos(p.yaw) * Math.cos(p.pitch));
        float sp = FLY * (in.down("sprint") ? FLY_FAST : 1) * dt;
        p.x += (fx * f + (float) Math.cos(p.yaw) * s) * sp;
        p.y += fy * f * sp + (in.down("jump") ? sp : 0) - (in.down("crouch") ? sp : 0);
        p.z += (fz * f - (float) Math.sin(p.yaw) * s) * sp;
        p.x = Math.max(1, Math.min(absentialis.world.World.W - 2, p.x)); p.z = Math.max(1, Math.min(absentialis.world.World.D - 2, p.z)); p.y = Math.max(2, Math.min(absentialis.world.World.H + 40, p.y));
        p.vx = p.vy = p.vz = 0; p.onGround = false;
    }

    // ------------------------------------------------------------------ 1.1: picking things up and moving them
    /** What is being carried (one of these is not null while something is), how far in front of the camera, and which way it is turned. */
    public Actor heldActor;
    public Animals.Animal heldAnimal;
    public VehicleSystem.Car heldCar;
    public Raid.WorldItem heldItem;
    public float holdDist = 5, holdYaw;
    public int pickedUp, putDown;

    /** 1.2: a prop that is being carried, the blocks that stood in for it where it was (dx, dy, dz, block), and how it sat in its cell. */
    public absentialis.engine.props.Props.Instance heldProp;
    private final List<int[]> heldCells = new ArrayList<>();
    private float propOx, propOy, propOz;

    public boolean holding() { return heldActor != null || heldAnimal != null || heldCar != null || heldItem != null || heldProp != null; }

    // ---- 1.2: the line things are picked and put along: through the middle of the screen, or - with the workbench open and the
    //      pointer free - through the pointer
    private final float[] ray = new float[6];
    private final absentialis.engine.math.Mat4 pv = new absentialis.engine.math.Mat4(), ipv = new absentialis.engine.math.Mat4();
    private boolean pvKnown;
    private float curX, curY;
    /** How far along the ray the world is (a wall, the ground), or 80. */
    private float wallAt = 80;

    /** The camera this frame was drawn with (the screen gives it, so that the pointer can be turned into a line in the world). */
    public void camera(absentialis.engine.math.Mat4 m) { pv.set(m); pvKnown = true; }

    private void unproject(float nx, float ny, float nz, float[] out, int at) {
        float[] m = ipv.m;
        float x = m[0] * nx + m[4] * ny + m[8] * nz + m[12], y = m[1] * nx + m[5] * ny + m[9] * nz + m[13], z = m[2] * nx + m[6] * ny + m[10] * nz + m[14], w = m[3] * nx + m[7] * ny + m[11] * nz + m[15];
        if (Math.abs(w) < 1e-9f) w = 1e-9f;
        out[at] = x / w; out[at + 1] = y / w; out[at + 2] = z / w;
    }

    private final float[] un = new float[6];

    /** Sets {@link #ray}: from the eye through the middle of the screen, or through the pointer when it is free. */
    private void ray(boolean pointer) {
        Actor p = raid().player;
        ray[0] = p.x; ray[1] = p.eyeY(); ray[2] = p.z;
        ray[3] = (float) (-Math.sin(p.yaw) * Math.cos(p.pitch)); ray[4] = (float) Math.sin(p.pitch); ray[5] = (float) (-Math.cos(p.yaw) * Math.cos(p.pitch));
        if (!pointer || !pvKnown) return;
        ipv.invert(pv);
        unproject(curX, curY, -1, un, 0); unproject(curX, curY, 1, un, 3);
        float dx = un[3] - un[0], dy = un[4] - un[1], dz = un[5] - un[2], l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-6f || Float.isNaN(l)) return;
        ray[0] = un[0]; ray[1] = un[1]; ray[2] = un[2]; ray[3] = dx / l; ray[4] = dy / l; ray[5] = dz / l;
    }

    /**
     * The first solid cell along the ray, exactly: where the line goes into it ({@code hit}) and through which face
     * ({@code face}: the way out of the block). Returns how far that is, or -1 for nothing within {@code max}.
     */
    private float cast(float max, float[] hit, int[] face) {
        Raid r = raid();
        float ox = ray[0], oy = ray[1], oz = ray[2], dx = ray[3], dy = ray[4], dz = ray[5];
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        float tdx = dx == 0 ? Float.MAX_VALUE : Math.abs(1 / dx), tdy = dy == 0 ? Float.MAX_VALUE : Math.abs(1 / dy), tdz = dz == 0 ? Float.MAX_VALUE : Math.abs(1 / dz);
        float tmx = dx == 0 ? Float.MAX_VALUE : (dx > 0 ? x + 1 - ox : ox - x) * tdx, tmy = dy == 0 ? Float.MAX_VALUE : (dy > 0 ? y + 1 - oy : oy - y) * tdy, tmz = dz == 0 ? Float.MAX_VALUE : (dz > 0 ? z + 1 - oz : oz - z) * tdz;
        float t = 0;
        face[0] = face[1] = face[2] = 0;
        while (t <= max) {
            if (t > 0.3f && r.world.solid(x, y, z)) { hit[0] = ox + dx * t; hit[1] = oy + dy * t; hit[2] = oz + dz * t; return t; }
            if (tmx <= tmy && tmx <= tmz) { t = tmx; tmx += tdx; x += sx; face[0] = -sx; face[1] = 0; face[2] = 0; }
            else if (tmy <= tmz) { t = tmy; tmy += tdy; y += sy; face[0] = 0; face[1] = -sy; face[2] = 0; }
            else { t = tmz; tmz += tdz; z += sz; face[0] = 0; face[1] = 0; face[2] = -sz; }
        }
        return -1;
    }

    /** The body the ray passes through nearest the eye, no further than {@code within}: a body is as tall as it is, not a point at its waist. */
    private Actor actorOnRay(float within, float[] alongOut) {
        Raid r = raid();
        float ox = ray[0], oy = ray[1], oz = ray[2], dx = ray[3], dy = ray[4], dz = ray[5];
        Actor found = null;
        float best = within;
        for (Actor a : r.actors) {
            if (a.isPlayer) continue;
            // a body the physics has is where its pelvis is
            float bx = a.ragdoll != null ? a.ragdoll.x[absentialis.character.Ragdoll.PELVIS] : a.x, bz = a.ragdoll != null ? a.ragdoll.z[absentialis.character.Ragdoll.PELVIS] : a.z, by = a.ragdoll != null ? a.ragdoll.lowestY() : a.y;
            float ax = bx - ox, az = bz - oz, hl = (float) Math.sqrt(dx * dx + dz * dz);
            float along = hl < 1e-3f ? (by - oy) / dy : (ax * dx + az * dz) / (hl * hl);
            if (along < 0.5f || along > best) continue;
            float px = ox + dx * along - bx, pz = oz + dz * along - bz, py = oy + dy * along;
            boolean down = a.dead || a.ragdoll != null;
            float top = by + (down ? 0.7f : a.height() + 0.25f), wide = down ? 0.95f : Math.max(0.45f, a.radius * 1.6f);
            if (px * px + pz * pz < wide * wide && py > by - 0.25f && py < top) { best = along; found = a; }
        }
        if (alongOut != null) alongOut[0] = best;
        return found;
    }

    private final float[] hitAt = new float[3], along1 = new float[1], mult1 = new float[1];
    private final int[] faceOf = new int[3];

    /**
     * Takes hold of whatever the line is on - a body (living or not), an animal, a vehicle, something lying on the
     * ground, a prop - nearest first, and nothing that is behind a wall. Returns what to say about it.
     */
    public String pickUp() {
        Raid r = raid();
        float ox = ray[0], oy = ray[1], oz = ray[2], dx = ray[3], dy = ray[4], dz = ray[5];
        float wall = cast(80, hitAt, faceOf);
        float best = (wall < 0 ? 80 : wall) + 0.75f;
        String name = null;
        Actor ba = actorOnRay(best, along1);
        if (ba != null) { best = along1[0]; heldActor = ba; name = label(ba); }
        for (Animals.Animal a : r.animals.all) {
            float t;
            if (a.rag == null) { mult1[0] = 1; t = AnimalBodies.rayHit(a, ox, oy, oz, dx, dy, dz, best, mult1); }
            else { float al = (a.x - ox) * dx + (a.y + 0.2f - oy) * dy + (a.z - oz) * dz, qx = ox + dx * al - a.x, qy = oy + dy * al - a.y - 0.2f, qz = oz + dz * al - a.z, rad = Math.max(0.4f, a.kind.radius * 1.3f); t = al > 0.5f && qx * qx + qy * qy + qz * qz < rad * rad ? al : -1; }
            if (t >= 0 && t < best) { best = t; heldAnimal = a; heldActor = null; name = a.kind.display(); }
        }
        float[] o = new float[3];
        for (VehicleSystem.Car c : r.vehicles.cars) {
            c.sim.origin(o);
            float al = (o[0] - ox) * dx + (o[1] + 0.7f - oy) * dy + (o[2] - oz) * dz;
            if (al < 0.5f || al > best + 2) continue;
            float qx = ox + dx * al - o[0], qy = oy + dy * al - o[1] - 0.7f, qz = oz + dz * al - o[2], rad = Math.max(c.sim.data.width, c.sim.data.length * 0.5f) * 0.75f;
            if (qx * qx + qy * qy + qz * qz < rad * rad && al - rad * 0.6f < best) { best = Math.max(0.5f, al - rad * 0.6f); heldCar = c; heldAnimal = null; heldActor = null; name = c.sim.data.name; }
        }
        for (Raid.WorldItem w : r.items) {
            float al = (w.x - ox) * dx + (w.y + 0.1f - oy) * dy + (w.z - oz) * dz;
            if (al < 0.5f || al > best) continue;
            float qx = ox + dx * al - w.x, qy = oy + dy * al - w.y - 0.1f, qz = oz + dz * al - w.z;
            if (qx * qx + qy * qy + qz * qz < 0.4f * 0.4f) { best = al; heldItem = w; heldCar = null; heldAnimal = null; heldActor = null; name = w.it.name(); }
        }
        // a prop: the first of them the line goes through before anything nearer was found (it stands in solid cells of its own)
        if (r.props != null && wall >= 0 && wall <= best + 0.05f) {
            int cx = (int) Math.floor(hitAt[0] - faceOf[0] * 0.02f), cy = (int) Math.floor(hitAt[1] - faceOf[1] * 0.02f), cz = (int) Math.floor(hitAt[2] - faceOf[2] * 0.02f);
            absentialis.engine.props.Props.Instance in = r.props.at(cx, cy, cz);
            if (in != null) {
                heldProp = in; heldItem = null; heldCar = null; heldAnimal = null; heldActor = null;
                best = wall; name = r.props.name(in.model).replace('_', ' ');
                propOx = in.ox; propOy = in.oy; propOz = in.oz;
                // the blocks that stand in for it leave with it, and go down again where it is put
                heldCells.clear();
                absentialis.world.gen.PropLib.Def d = absentialis.world.gen.PropLib.get(r.props.name(in.model));
                int reach = d == null ? 1 : Math.max(d.w, d.d), up = d == null ? 2 : d.h;
                for (int ax = -reach; ax <= reach; ax++) for (int az = -reach; az <= reach; az++) for (int ay = -up; ay <= up; ay++) {
                    int wx = in.x + ax, wy = in.y + ay, wz = in.z + az;
                    if (r.props.at(wx, wy, wz) != in || !absentialis.world.Block.prop(r.world.get(wx, wy, wz))) continue;
                    heldCells.add(new int[]{ax, ay, az, r.world.get(wx, wy, wz)});
                    r.world.set(wx, wy, wz, absentialis.world.Block.AIR);
                }
            }
        }
        if (!holding()) return "Nothing there to pick up (point at a body, an animal, a vehicle, a prop or something on the ground)";
        holdDist = Math.max(1.5f, best);
        holdYaw = heldActor != null ? heldActor.yaw : heldAnimal != null ? heldAnimal.yaw : heldCar != null ? heldCar.sim.body.yaw() : 0;
        pickedUp++;
        return "Holding " + name + "   [click] or [G] put down   [wheel] nearer / further   , . turn";
    }

    /** One frame of carrying: the thing is held on the line, clear of the ground, with no will of its own. */
    private void carry() {
        Raid r = raid();
        float x = ray[0] + ray[3] * holdDist, y = ray[1] + ray[4] * holdDist, z = ray[2] + ray[5] * holdDist;
        x = Math.max(2, Math.min(absentialis.world.World.W - 3, x)); z = Math.max(2, Math.min(absentialis.world.World.D - 3, z)); y = Math.max(2, Math.min(absentialis.world.World.H - 3, y));
        if (heldActor != null) {
            Actor a = heldActor;
            if (!r.actors.contains(a)) { heldActor = null; return; }
            a.x = x; a.y = y - (a.dead ? 0.2f : 0.9f); a.z = z; a.vx = a.vy = a.vz = 0; a.pvx = a.pvz = 0; a.fallSpeed = 0; a.airTime = 0; a.onGround = false; a.yaw = holdYaw; a.path = null; a.hasGoal = false;
            if (a.ragdoll != null) a.ragdoll.shift(a.x - a.ragdoll.x[absentialis.character.Ragdoll.PELVIS], a.y + 0.6f - a.ragdoll.y[absentialis.character.Ragdoll.PELVIS], a.z - a.ragdoll.z[absentialis.character.Ragdoll.PELVIS]);
        } else if (heldAnimal != null) {
            Animals.Animal a = heldAnimal;
            if (!r.animals.all.contains(a)) { heldAnimal = null; return; }
            if (a.rag != null) { a.rag.moveBy(x - a.rag.centreX(), y - a.rag.centreY(), z - a.rag.centreZ()); a.x = x; a.y = a.rag.lowestY(); a.z = z; return; }
            a.x = x; a.y = y - a.kind.height * 0.5f; a.z = z; a.yaw = holdYaw; a.body.x = a.x; a.body.y = a.y; a.body.z = a.z; a.body.vx = a.body.vy = a.body.vz = 0; a.fvx = a.fvy = a.fvz = 0; a.homeX = a.x; a.homeY = a.y; a.homeZ = a.z; a.onCeiling = false;
        } else if (heldCar != null) {
            heldCar.sim.place(x, y - 0.4f, z, holdYaw);
        } else if (heldItem != null) {
            heldItem.x = x; heldItem.y = y; heldItem.z = z;
        } else if (heldProp != null && r.props != null) {
            // drawn where it is held: in the cell that point is in, set off from the middle of it by the rest
            int cx = (int) Math.floor(x), cy = (int) Math.floor(y), cz = (int) Math.floor(z);
            if (cx != heldProp.x || cy != heldProp.y || cz != heldProp.z) r.props.move(heldProp, cx, cy, cz);
            heldProp.ox = x - cx - 0.5f; heldProp.oy = y - cy; heldProp.oz = z - cz - 0.5f;
            r.props.touch();
        }
    }

    /** Lets go: the thing is stood on the floor under where it was being held (a vehicle, an item and a prop are set down; a body drops the last of the way). */
    public String putDown() {
        Raid r = raid();
        String name = "it";
        if (heldActor != null) {
            Actor a = heldActor;
            int gy = r.standY((int) Math.floor(a.x), (int) Math.floor(a.z), Math.round(a.y));
            if (gy > 0 && a.y - gy < 6 && a.y >= gy - 0.5f) a.y = gy;
            a.homeX = a.x; a.homeZ = a.z; a.onGround = false;
            name = label(a);
        } else if (heldAnimal != null) {
            Animals.Animal a = heldAnimal;
            if (a.rag != null) a.rag.wake();
            else if (a.kind.ground) { int gy = r.standY((int) Math.floor(a.x), (int) Math.floor(a.z), Math.round(a.y)); if (gy > 0) a.y = gy; a.body.y = a.y; }
            a.homeX = a.x; a.homeY = a.y; a.homeZ = a.z;
            name = a.kind.display();
        } else if (heldCar != null) {
            float[] o = new float[3];
            heldCar.sim.origin(o);
            int gy = r.groundY((int) Math.floor(o[0]), (int) Math.floor(o[2]));
            if (gy > 0 && o[1] - gy < 8) heldCar.sim.place(o[0], gy + 0.6f, o[2], holdYaw);
            name = heldCar.sim.data.name;
        } else if (heldItem != null) {
            int gy = r.standY((int) Math.floor(heldItem.x), (int) Math.floor(heldItem.z), Math.round(heldItem.y));
            if (gy > 0) heldItem.y = gy + 0.05f;
            name = heldItem.it.name();
        } else if (heldProp != null && r.props != null) {
            // onto the floor under it, sitting in its cell as it sat in the one it came from, with its blocks under it again
            int cx = heldProp.x, cz = heldProp.z, cy = heldProp.y;
            while (cy > 1 && !r.world.solid(cx, cy - 1, cz)) cy--;
            r.props.move(heldProp, cx, cy, cz);
            heldProp.ox = propOx; heldProp.oy = propOy; heldProp.oz = propOz;
            for (int[] c : heldCells) {
                int wx = cx + c[0], wy = cy + c[1], wz = cz + c[2];
                if (r.world.get(wx, wy, wz) == absentialis.world.Block.AIR) r.world.set(wx, wy, wz, c[3]);
                r.props.claim(heldProp, wx, wy, wz);
            }
            heldCells.clear();
            r.props.touch();
            name = r.props.name(heldProp.model).replace('_', ' ');
        } else return "Nothing is being held";
        heldActor = null; heldAnimal = null; heldCar = null; heldItem = null; heldProp = null;
        putDown++;
        return "Put down " + name;
    }

    public void aimNow() { aim(); }

    /**
     * Works out what the line is on: a spot on a surface (where things are put) and the body nearest that line (what is
     * inspected).
     *
     * 1.2: the spot is exact. The line used to be sampled every twenty centimetres and the spot taken a quarter of a metre
     * back from the first sample found inside something - so it was up to half a block short of where the cross was, and
     * a thing put "there" was put beside it. The line is now walked cell by cell to the face it meets: on top of a block
     * the spot is that point; on the side of one it is the floor at the foot of the wall there.
     * With the workbench open the spot stays where it was when it was opened, and is moved by clicking in the world.
     */
    private void aim() {
        ray(panel && !overPanel);
        if (!panel) place();
        inspected = panel && overPanel ? null : actorOnRay((wallAt < 0 ? 80 : wallAt) + 1, null);
    }

    /** Puts the spot where the ray meets the world. */
    private void place() {
        Raid r = raid();
        float t = cast(80, hitAt, faceOf);
        wallAt = t;
        targetKnown = false;
        if (t >= 0) {
            if (faceOf[1] > 0) { target[0] = hitAt[0]; target[1] = Math.round(hitAt[1]); target[2] = hitAt[2]; }
            else {
                // a wall (or a ceiling): the floor at the foot of it, on this side
                target[0] = hitAt[0] + faceOf[0] * 0.35f; target[2] = hitAt[2] + faceOf[2] * 0.35f;
                int gx = (int) Math.floor(target[0]), gz = (int) Math.floor(target[2]), gy = (int) Math.floor(hitAt[1] + faceOf[1] * 0.35f);
                while (gy > 1 && !r.world.solid(gx, gy - 1, gz)) gy--;
                target[1] = gy;
            }
            targetKnown = true;
        } else {
            target[0] = ray[0] + ray[3] * 10; target[2] = ray[2] + ray[5] * 10;
            int gy = r.groundY((int) target[0], (int) target[2]);
            target[1] = gy < 0 ? ray[1] : gy; targetKnown = gy >= 0;
        }
    }

    /** True while the pointer is on the workbench (so that it is not also a line into the world). */
    private boolean overPanel;

    private static String label(Actor a) { return a.infected() ? Archetypes.displayName(a.archetype) + " infected" : ZoneState.cap(a.faction) + (a.name == null || a.name.isEmpty() ? "" : " \"" + a.name + "\""); }

    public void remove(Actor a) {
        Raid r = raid();
        r.neural.remove(a.id, true); r.neuralBody.forget(a.id); r.actors.remove(a); r.mates.remove(a);
        inspected = null;
    }

    // ------------------------------------------------------------------ putting things in the world
    public String spawnNpc(String kind) {
        Raid r = raid();
        // 1.1: animals are spawned here too
        if (Animals.Kind.of(kind) != null) return r.animals.debugSpawn(kind, target[0], target[1], target[2], count);
        // 1.2: the people the list did not have - a faction member of a chosen trade ("military:marksman"), and the four who give work
        if (kind.startsWith("advisor:")) {
            for (absentialis.gameplay.missions.Chains.Advisor ad : absentialis.gameplay.missions.Chains.ADVISORS) {
                if (!ad.name().equals(kind.substring(8))) continue;
                Actor a = r.debugSpawn(ad.faction(), target[0], target[2], target[1]);
                if (a == null) return "Could not place " + ad.name();
                a.name = ad.name(); a.advisor = true; a.hostileToPlayer = false; a.aggressor = false;
                a.yaw = (float) Math.atan2(-(r.player.x - a.x), -(r.player.z - a.z));
                r.frozen.add(a.id);
                spawned++;
                return "Placed " + ad.name() + " (" + ad.role() + ")";
            }
            return "No such advisor: " + kind;
        }
        final String role = kind.contains(":") ? kind.substring(kind.indexOf(':') + 1) : null, who = role == null ? kind : kind.substring(0, kind.indexOf(':'));
        int made = 0;
        for (int i = 0; i < count; i++) {
            double ang = i * 2.399963;                                      // spread round the spot, not stacked on it
            float rad = i == 0 ? 0 : 0.9f * (float) Math.sqrt(i);
            // 1.0: on the floor that was pointed at. The others of a group used to be put on a ring round the spot wherever that
            // fell - through a wall, in the next room, outside - and each then found "the nearest floor" in that column for
            // itself: the street, the storey above, the roof. A place on the ring is now used only if it is on the same floor
            // as the spot and can be walked to from it in a straight line; otherwise that one stands at the spot itself.
            float sx = target[0] + (float) Math.cos(ang) * rad, sz = target[2] + (float) Math.sin(ang) * rad;
            int ty = Math.round(target[1]), fx = (int) Math.floor(sx), fz = (int) Math.floor(sz);
            boolean same = r.world.solid(fx, ty - 1, fz) && !r.world.solid(fx, ty, fz) && !r.world.solid(fx, ty + 1, fz)
                    && (rad == 0 || r.world.nav.clear(target[0], ty, target[2], sx, sz, false));
            if (!same) { sx = target[0]; sz = target[2]; }
            Actor a = role == null ? r.debugSpawn(kind, sx, sz, target[1]) : r.debugSpawnRole(who, role, sx, sz, target[1]);
            if (a == null) return "No such kind: " + kind;
            // (and exactly at that height: a tall kind is not moved up a storey because the one it was asked for is a tight fit)
            if (r.world.solid((int) Math.floor(sx), ty - 1, (int) Math.floor(sz)) && !r.world.solid((int) Math.floor(sx), ty, (int) Math.floor(sz))) a.y = ty;
            if (!a.infected()) a.hostileToPlayer = hostile;
            if (frozen) r.freezeOnly(a);
            a.yaw = (float) Math.atan2(-(r.player.x - a.x), -(r.player.z - a.z));
            made++; spawned++;
        }
        return "Spawned " + made + " x " + kind + (frozen ? " (frozen)" : "");
    }

    public String spawnItem(ItemDef d) {
        Raid r = raid();
        for (int i = 0; i < count; i++) {
            ItemInstance it = new ItemInstance(d.id);
            it.origin = "devmode";
            if (d.maxStack > 1) it.qty = d.maxStack;
            if (d.weapon != null && !d.weapon.melee() && Content.ammoFor(d.weapon.caliber) != null) { it.rounds = it.capacity(); it.ammoId = Content.ammoFor(d.weapon.caliber).id; }
            r.dropItem(it, target[0] + (i % 4) * 0.35f - 0.5f, target[1] + 0.3f, target[2] + (i / 4) * 0.35f);
            spawned++;
        }
        return "Dropped " + count + " x " + d.name;
    }

    public String spawnCar(String id) {
        Raid r = raid();
        VehicleSystem.Car car = r.vehicles.spawn(id, target[0], target[1], target[2], r.player.yaw, spawned * 31 + 7);
        if (car == null) return "No such vehicle: " + id;
        spawned++;
        return "Parked a " + car.sim.data.name;
    }

    // ------------------------------------------------------------------ what is drawn
    /** The whole of the mode's interface: called in place of the game's HUD. */
    public void draw(Ui ui, float dt) {
        Raid r = raid();
        Actor p = r.player;
        float W = ui.w, H = ui.h;
        // ---- status bar
        float bh = ui.s(30);
        ui.b.rect(0, 0, W, bh, 0xE00C0E10);
        ui.b.rect(0, bh, W, ui.s(2), 0xFF4A90E2);
        ui.text("DEVELOPER MODE", ui.s(12), ui.s(8), 13, 0xFF7AB4F0);
        District d = r.world.district(p.x, p.z);
        String coords = String.format("X %.1f   Y %.1f   Z %.1f", p.x, p.y, p.z);
        ui.text(coords, ui.s(170), ui.s(8), 13, Ui.TXT_HI);
        double hr = r.hour() % 24;
        String rest = String.format("%s    facing %03.0f°  pitch %+03.0f°    %02d:%02d    actors %d  minds %d    %d fps", d == null ? "-" : d.display, (Math.toDegrees(-p.yaw) % 360 + 360) % 360, Math.toDegrees(p.pitch), (int) hr, (int) (hr * 60) % 60, r.actors.size() - 1, r.neural.minds().size(), Math.round(1 / Math.max(1e-4f, dt)));
        ui.text(rest, ui.s(170) + ui.tw(coords, 13) + ui.s(28), ui.s(8), 12, Ui.TXT);
        ui.textR((r.freezeAI ? "AI FROZEN   " : "") + absentialis.Version.SHORT + "  " + absentialis.engine.Astratto.NAME, W - ui.s(12), ui.s(9), 11, r.freezeAI ? Ui.AMBER : Ui.TXT_DIM);
        // ---- the spot things are put, and the middle of the screen
        curX = ui.mx / W * 2 - 1; curY = 1 - ui.my / H * 2;
        float pw = Math.min(ui.s(520), W * 0.42f);
        overPanel = panel && ui.mx >= ui.s(14) && ui.mx <= ui.s(14) + pw && ui.my >= bh + ui.s(14);
        if (!panel) { ui.b.rect(W / 2f - ui.s(7), H / 2f, ui.s(14), 1, 0xC07AB4F0); ui.b.rect(W / 2f, H / 2f - ui.s(7), 1, ui.s(14), 0xC07AB4F0); }
        // 1.2: the spot is marked in the world, where it is - a ring on the floor - and not only as numbers under the cross
        if (targetKnown && pvKnown) {
            float[] nd = new float[3];
            float lastX = 0, lastY = 0;
            boolean have = false;
            for (int i = 0; i <= 16; i++) {
                double an = i * Math.PI / 8;
                float wv = pv.project(target[0] + (float) Math.cos(an) * 0.3f, target[1] + 0.03f, target[2] + (float) Math.sin(an) * 0.3f, nd);
                if (wv <= 0.05f) { have = false; continue; }
                float sx = (nd[0] * 0.5f + 0.5f) * W, sy = (1 - (nd[1] * 0.5f + 0.5f)) * H;
                if (have) ui.b.line(lastX, lastY, sx, sy, Math.max(2, ui.s(2)), 0xE07AB4F0);
                lastX = sx; lastY = sy; have = true;
            }
            float wv = pv.project(target[0], target[1] + 0.03f, target[2], nd);
            if (wv > 0.05f) ui.textC(String.format("%.2f, %.0f, %.2f", target[0], target[1], target[2]), (nd[0] * 0.5f + 0.5f) * W, (1 - (nd[1] * 0.5f + 0.5f)) * H + ui.s(12), 10, 0xD07AB4F0);
        }
        // with the workbench open the pointer is free: a click in the world takes hold of what is under it, or puts down what
        // is held, or - on bare ground - moves the spot there
        if (panel && !overPanel && ui.click && !ui.consumed && ui.my > bh) {
            ui.consumed = true;
            ray(true);
            if (holding()) say(putDown());
            else { String got = pickUp(); if (holding()) say(got); else { place(); say(targetKnown ? String.format("Spot moved to %.2f, %.0f, %.2f", target[0], target[1], target[2]) : "Nothing there"); } }
        }
        // ---- inspector
        if (inspected != null && !panel) inspector(ui, inspected, W - ui.s(330), bh + ui.s(14), ui.s(316));
        // ---- last thing said
        if (r.time - statusAt < 4 && !status.isEmpty()) { float sw = ui.tw(status, 13) + ui.s(24); ui.b.rect(W / 2f - sw / 2, H - ui.s(92), sw, ui.s(26), 0xD00C0E10); ui.textC(status, W / 2f, H - ui.s(86), 13, Ui.GREEN); }
        // ---- hints
        String hint = panel ? "[Tab] close    type to search    click a row: it is put on the ringed spot    click the world: pick up / put down / move the spot    [Esc] close"
                : "[Tab] workbench    [WASD] fly    [Space] up  [Ctrl] down    [Shift] fast    [click] pick up / put down    [Del] remove what you are looking at    [`] console";
        ui.b.rect(0, H - ui.s(24), W, ui.s(24), 0xC00C0E10);
        ui.textC(hint, W / 2f, H - ui.s(19), 11, Ui.TXT_DIM);
        if (panel) workbench(ui, ui.s(14), bh + ui.s(14), Math.min(ui.s(520), W * 0.42f), H - bh - ui.s(52));
    }

    private void inspector(Ui ui, Actor a, float x, float y, float w) {
        Raid r = raid();
        String[][] rows = {
                {"Kind", a.infected() ? Archetypes.displayName(a.archetype) : a.archetype}, {"Faction", ZoneState.cap(a.faction)}, {"Id", String.valueOf(a.id)},
                {"Health", a.dead ? "dead" : Math.round(a.hp) + " / " + Math.round(a.maxHp)}, {"Position", String.format("%.1f, %.1f, %.1f", a.x, a.y, a.z)},
                {"State", a.state + "  (" + NeuralHost.describe(a) + ")"}, {"Mind", r.neural.mind(a.id) == null ? "none" : r.neural.mind(a.id).state + (r.neural.mind(a.id).action == null ? "" : " / " + r.neural.mind(a.id).action.name())},
                {"Weapon", a.weapon == null ? "-" : a.weapon.name()}, {"Hostile to you", a.infected() ? "would be" : String.valueOf(a.hostileToPlayer)}, {"Frozen", String.valueOf(r.frozen.contains(a.id) || r.freezeAI)}};
        float h = ui.s(34) + rows.length * ui.s(19) + ui.s(10);
        ui.b.rect(x, y, w, h, 0xE00C0E10);
        ui.b.rect(x, y, ui.s(3), h, 0xFF4A90E2);
        ui.text("INSPECTOR  -  " + label(a), x + ui.s(12), y + ui.s(8), 12, 0xFF7AB4F0);
        float ry = y + ui.s(32);
        for (String[] row : rows) { ui.text(row[0], x + ui.s(12), ry, 11, Ui.TXT_DIM); absentialis.ui.Layout.fit(ui, row[1], x + ui.s(120), ry, w - ui.s(132), 11, 9, Ui.TXT_HI); ry += ui.s(19); }
    }

    private boolean matches(String a, String b) { String f = search.toString().toLowerCase().trim(); return f.isEmpty() || a.toLowerCase().contains(f) || b.toLowerCase().contains(f); }

    private void workbench(Ui ui, float x, float y, float w, float h) {
        Raid r = raid();
        ui.b.rect(x, y, w, h, 0xEE0C0E10);
        ui.b.outline(x, y, w, h, 1, 0xFF2A4A70);
        float tw = (w - ui.s(16)) / TABS.length;
        for (int i = 0; i < TABS.length; i++) if (ui.tab("dev_tab" + i, x + ui.s(8) + i * tw, y + ui.s(8), tw - ui.s(4), ui.s(28), TABS[i], null, tab == i)) { tab = i; scroll = 0; search.setLength(0); }
        float cy = y + ui.s(46);
        if (tab == 4) { world(ui, x + ui.s(12), cy, w - ui.s(24)); return; }
        if (tab == 5) { profile(ui, x + ui.s(12), cy, w - ui.s(24)); return; }
        // search, count and options
        ui.b.rect(x + ui.s(10), cy, w - ui.s(170), ui.s(26), 0xFF16191D);
        ui.b.outline(x + ui.s(10), cy, w - ui.s(170), ui.s(26), 1, 0xFF2A4A70);
        ui.text(search.length() == 0 ? "type to search..." : search + ((int) (r.time * 2) % 2 == 0 ? "_" : ""), x + ui.s(18), cy + ui.s(6), 12, search.length() == 0 ? Ui.TXT_DIM : Ui.TXT_HI);
        int[] counts = {1, 5, 10};
        for (int i = 0; i < counts.length; i++) if (ui.tab("dev_n" + i, x + w - ui.s(152) + i * ui.s(48), cy, ui.s(44), ui.s(26), "x" + counts[i], null, count == counts[i])) count = counts[i];
        cy += ui.s(34);
        if (tab == 0) {
            hostile = ui.toggle("dev_host", x + ui.s(10), cy, (w - ui.s(30)) / 2, "People hostile to the survivor", hostile);
            frozen = ui.toggle("dev_frz", x + ui.s(20) + (w - ui.s(30)) / 2, cy, (w - ui.s(30)) / 2, "Spawn frozen (no thinking)", frozen);
            cy += ui.s(32);
        }
        // the list
        List<String[]> rows = new ArrayList<>();                // {id, name, detail}
        switch (tab) {
            case 0 -> {
                for (var ar : Archetypes.all()) if (ar.infected() && matches(ar.id(), Archetypes.displayName(ar.id()))) rows.add(new String[]{ar.id(), Archetypes.displayName(ar.id()), "infected  -  " + Math.round(ar.hp()) + " hp" + (ar.blind() ? "  -  blind" : "")});
                for (String f : ZoneState.FACTIONS) if (matches(f, absentialis.gameplay.Alliance.name(f))) rows.add(new String[]{f, absentialis.gameplay.Alliance.name(f), "faction member, armed as it comes"});
                for (String f : ZoneState.FACTIONS) for (String tr : new String[]{"rifleman", "marksman", "shotgunner"}) { String nm = absentialis.gameplay.Alliance.name(f) + " " + tr; if (matches(f + ":" + tr, nm)) rows.add(new String[]{f + ":" + tr, nm, "faction member  -  " + Math.round(Archetypes.arch(tr).hp()) + " hp"}); }
                for (absentialis.gameplay.missions.Chains.Advisor ad : absentialis.gameplay.missions.Chains.ADVISORS) if (matches("advisor " + ad.faction(), ad.name())) rows.add(new String[]{"advisor:" + ad.name(), ad.name(), "gives work  -  " + ad.role()});
                for (Animals.Kind ak : Animals.Kind.values()) if (matches(ak.name().toLowerCase(), ak.display())) rows.add(new String[]{ak.name().toLowerCase(), ak.display(), (ak.infected() ? "infected animal" : "animal") + "  -  " + Math.round(ak.hp) + " hp"});
            }
            case 1 -> { for (ItemDef d : Content.ITEMS.values()) if (d.weapon != null && matches(d.id, d.name)) rows.add(new String[]{d.id, d.name, d.weapon.melee() ? "melee" : d.weapon.family + "  -  " + d.weapon.caliber}); }
            case 2 -> { for (ItemDef d : Content.ITEMS.values()) if (d.weapon == null && d.attachment == null && (matches(d.id, d.name) || search.toString().equalsIgnoreCase(d.category))) rows.add(new String[]{d.id, d.name, d.category + "  -  " + d.tierName()}); }
            default -> { for (CarHandlingData d : CarHandlingData.all()) if (matches(d.id, d.name)) rows.add(new String[]{d.id, d.name, d.family}); }
        }
        float listH = y + h - cy - ui.s(10), rowH = ui.s(30);
        float maxScroll = Math.max(0, rows.size() * rowH - listH);
        if (ui.hover(x, cy, w, listH)) scroll = Math.max(0, Math.min(maxScroll, scroll - (float) screen.c.in.scroll * rowH * 2));
        scroll = Math.min(scroll, maxScroll);
        ui.b.scissor((int) x, (int) cy, (int) w, (int) listH);
        for (int i = 0; i < rows.size(); i++) {
            float ry = cy + i * rowH - scroll;
            if (ry + rowH < cy || ry > cy + listH) continue;
            String[] row = rows.get(i);
            boolean hov = ui.hover(x + ui.s(8), ry, w - ui.s(22), rowH - ui.s(3)) && ui.my >= cy && ui.my <= cy + listH;
            ui.b.rect(x + ui.s(8), ry, w - ui.s(22), rowH - ui.s(3), hov ? 0xFF1C2836 : 0xFF14171B);
            if (hov) ui.b.rect(x + ui.s(8), ry, ui.s(3), rowH - ui.s(3), 0xFF4A90E2);
            ui.text(row[1], x + ui.s(20), ry + ui.s(6), 12, hov ? Ui.TXT_HI : Ui.TXT);
            ui.textR(row[2], x + w - ui.s(24), ry + ui.s(7), 10, Ui.TXT_DIM);
            if (hov && ui.click && !ui.consumed) {
                ui.consumed = true;
                if (!targetKnown) { say("Look at somewhere to put it"); continue; }
                say(switch (tab) { case 0 -> spawnNpc(row[0]); case 3 -> spawnCar(row[0]); default -> spawnItem(Content.def(row[0])); });
                screen.c.sfx("ui_confirm", 0.4f);
            }
        }
        ui.b.noScissor();
        if (maxScroll > 0) { float sbh = listH * listH / (rows.size() * rowH); ui.b.rect(x + w - ui.s(8), cy + (listH - sbh) * (scroll / maxScroll), ui.s(3), sbh, 0xFF4A90E2); }
        if (rows.isEmpty()) ui.text("Nothing matches '" + search + "'", x + ui.s(20), cy + ui.s(8), 12, Ui.TXT_DIM);
    }

    /**
     * 1.0: the profile, by hand - experience and rank, prestige, money, the body, the weather. Everything here is the
     * real record (what is changed is what is saved), so a tester can stand at any point of the game in a few clicks.
     */
    private void profile(Ui ui, float x, float y, float w) {
        Raid r = raid();
        var p = r.profile;
        var pr = p.prog;
        float cy = y, bw = (w - ui.s(24)) / 4, bh = ui.s(30), gap = ui.s(8);
        ui.text(p.name + "   -   " + pr.title(), x, cy, 15, Ui.TXT_HI);
        ui.textR(String.format("%,d XP this cycle   -   best rank %d   -   prestige %d", pr.cycleXp, pr.bestRank, pr.prestige), x + w, cy + ui.s(2), 12, Ui.TXT_DIM);
        cy += ui.s(26);
        ui.bar(x, cy, w, ui.s(10), pr.rankProgress(), 0xFF4A90E2);
        cy += ui.s(22);
        ui.text("EXPERIENCE", x, cy, 12, 0xFF7AB4F0); cy += ui.s(20);
        long[] xp = {1000, 25000};
        for (int i = 0; i < 2; i++) if (ui.button("dev_xp" + i, x + i * (bw + gap), cy, bw, bh, String.format("+%,d XP", xp[i]), null, true)) { pr.addXp(xp[i]); say("Rank " + pr.rank()); }
        if (ui.button("dev_rk1", x + 2 * (bw + gap), cy, bw, bh, "RANK +1", null, true)) { pr.addXp(Math.max(1, absentialis.gameplay.progression.Progression.xpToReach(pr.rank() + 1) - pr.cycleXp)); say("Rank " + pr.rank()); }
        if (ui.button("dev_rk10", x + 3 * (bw + gap), cy, bw, bh, "RANK +10", null, true)) { pr.addXp(Math.max(1, absentialis.gameplay.progression.Progression.xpToReach(pr.rank() + 10) - pr.cycleXp)); say("Rank " + pr.rank()); }
        cy += bh + gap;
        if (ui.button("dev_rk0", x, cy, bw, bh, "RESET RANK", null, true)) { pr.cycleXp = 0; pr.bestRank = 1; say("Rank 1, nothing banked"); }
        if (ui.button("dev_pr1", x + (bw + gap), cy, bw, bh, "PRESTIGE +1", null, pr.prestige < absentialis.gameplay.progression.Progression.MAX_PRESTIGE)) { pr.prestige++; say("Prestige " + pr.prestige); }
        if (ui.button("dev_pr0", x + 2 * (bw + gap), cy, bw, bh, "PRESTIGE -1", null, pr.prestige > 0)) { pr.prestige--; say("Prestige " + pr.prestige); }
        if (ui.button("dev_case", x + 3 * (bw + gap), cy, bw, bh, "CASE TO RANK", null, true)) { int[] d = pr.caseDims(); say("Case " + d[0] + " x " + d[1] + " at best rank " + pr.bestRank + " (takes effect at the next deployment)"); }
        cy += bh + ui.s(18);
        ui.text("MONEY", x, cy, 12, 0xFF7AB4F0); ui.textR(absentialis.Version.coins(p.scrip), x + w, cy, 13, Ui.TXT_HI); cy += ui.s(20);
        long[] cash = {1000, 100000};
        for (int i = 0; i < 2; i++) if (ui.button("dev_cash" + i, x + i * (bw + gap), cy, bw, bh, "+" + absentialis.Version.coins(cash[i]), null, true)) p.scrip += cash[i];
        if (ui.button("dev_cash0", x + 2 * (bw + gap), cy, bw, bh, "ZERO", null, true)) p.scrip = 0;
        cy += bh + ui.s(18);
        ui.text("THE BODY", x, cy, 12, 0xFF7AB4F0); cy += ui.s(20);
        if (ui.button("dev_heal", x, cy, bw, bh, "FULL HEAL", null, true)) { r.player.hp = r.player.maxHp; r.body.bleedLight = r.body.bleedHeavy = r.body.fracture = false; r.body.armInjury = r.body.legInjury = 0; r.body.infection = 0; r.body.rad = 0; r.body.energy = r.body.hydration = r.body.stamina = r.body.focus = 100; say("Healed"); }
        if (ui.button("dev_herb", x + (bw + gap), cy, bw, bh, "MAX HEALTH +", null, true)) { p.herbs++; r.player.maxHp += absentialis.gameplay.CombatConfig.get().perHerb; r.player.hp = r.player.maxHp; say("Maximum health " + Math.round(r.player.maxHp)); }
        if (ui.button("dev_hurt", x + 2 * (bw + gap), cy, bw, bh, "HURT 25", null, true)) { r.player.hp = Math.max(1, r.player.hp - 25); r.body.healthChanged = r.time; }
        if (ui.button("dev_inf", x + 3 * (bw + gap), cy, bw, bh, "INFECT", null, true)) { r.body.infection = Math.max(r.body.infection, 30); say("Infected"); }
        cy += bh + gap;
        godWas = ui.toggle("dev_god", x, cy, w / 2 - ui.s(6), "Cannot be hurt (outside devmode too)", godWas);
        cy += ui.s(40);
        ui.text("WEATHER", x, cy, 12, 0xFF7AB4F0); ui.textR(String.valueOf(p.zone.weather), x + w, cy, 12, Ui.TXT_DIM); cy += ui.s(20);
        String[] wk = {"CLEAR", "FOG", "RAIN", "STORM", "SNOW"};
        float ww = (w - gap * 4) / 5;
        for (int i = 0; i < wk.length; i++) if (ui.button("dev_w" + i, x + i * (ww + gap), cy, ww, bh, wk[i], null, true)) {
            var kind = absentialis.engine.weather.Weather.Kind.of(wk[i]);
            p.zone.setWeather(kind.name()); r.weather.set(kind); r.weather.snap();
            say("Weather: " + kind.name().toLowerCase());
        }
    }

    /** 1.1: rain on or off, at once: the kind of weather, the saved zone, what is already falling and the wet on the ground. */
    public void setRain(boolean on) {
        Raid r = raid();
        var kind = on ? absentialis.engine.weather.Weather.Kind.RAIN : absentialis.engine.weather.Weather.Kind.CLEAR;
        r.profile.zone.setWeather(kind.name());
        if (!on) r.profile.zone.weatherRain = 0;
        r.weather.set(kind); r.weather.snap();
        if (!on) { r.weather.wetness = 0; r.fx.clearPrecipitation(); }
        say(on ? "Rain on" : "Rain off");
    }

    private void world(Ui ui, float x, float y, float w) {
        Raid r = raid();
        float cy = y;
        double hourNow = r.hour() % 24;
        float h2 = ui.slider("dev_hour", x, cy, w, "Hour of the day", (float) hourNow, 0, 23.99f, "%.1f");
        if (Math.abs(h2 - hourNow) > 0.02) r.profile.zone.hour = ((h2 - r.time / 120.0) % 24 + 24) % 24;
        cy += ui.s(52);
        // 1.1: the switch works the weather system (it used to set a figure only pre-1.0 code read, and the rain went on falling)
        var wk = r.weather.kind();
        boolean wasRain = wk == absentialis.engine.weather.Weather.Kind.RAIN || wk == absentialis.engine.weather.Weather.Kind.STORM || r.profile.zone.weatherRain > 0.5f;
        boolean rain = ui.toggle("dev_rain", x, cy, w / 2 - ui.s(6), "Rain", wasRain);
        if (rain != wasRain) setRain(rain);
        r.freezeAI = ui.toggle("dev_freeze", x + w / 2 + ui.s(6), cy, w / 2 - ui.s(6), "Freeze all AI", r.freezeAI);
        cy += ui.s(32);
        r.aiDebug = ui.toggle("dev_aidbg", x, cy, w / 2 - ui.s(6), "AI overlay (cones, states)", r.aiDebug);
        FLY = ui.toggle("dev_fast", x + w / 2 + ui.s(6), cy, w / 2 - ui.s(6), "Fast camera", FLY > 20) ? 30 : 12;
        cy += ui.s(40);
        float bw = (w - ui.s(16)) / 3;
        if (ui.button("dev_kill", x, cy, bw, ui.s(32), "KILL ALL", "skull", true)) { int n = 0; for (Actor a : new ArrayList<>(r.actors)) if (!a.isPlayer && !a.dead && !a.mate()) { r.damageActor(a, null, 99999, null, "head", a.x, a.y + 1.6f, a.z); n++; } say("Killed " + n); }
        if (ui.button("dev_clear", x + bw + ui.s(8), cy, bw, ui.s(32), "REMOVE ALL", "cross", true)) { int n = 0; for (Actor a : new ArrayList<>(r.actors)) if (!a.isPlayer && !a.mate()) { remove(a); n++; } say("Removed " + n); }
        if (ui.button("dev_cars", x + (bw + ui.s(8)) * 2, cy, bw, ui.s(32), "SPINEL: GO TO", "search", r.spinels != null && r.spinels.remaining() > 0)) { CeilingSpinels.Stone s = r.spinels.nearest(r.player.x, r.player.y, r.player.z, 3000); if (s != null) { r.player.x = s.x + 1.5f; r.player.y = s.floorY + 0.4f; r.player.z = s.z + 1.5f; say("Under a spinel - look up"); } }
        cy += ui.s(46);
        ui.text("GO TO", x, cy, 12, 0xFF7AB4F0);
        cy += ui.s(20);
        List<MapData.Landmark> lms = r.world.map.anchors;
        int cols = 2;
        float lw = (w - ui.s(8)) / cols;
        for (int i = 0; i < lms.size(); i++) {
            MapData.Landmark lm = lms.get(i);
            if (ui.button("dev_go" + i, x + (i % cols) * (lw + ui.s(8)), cy + (i / cols) * ui.s(32), lw - ui.s(4), ui.s(28), lm.name, null, true)) {
                r.player.x = lm.cx() + 0.5f; r.player.z = Math.min(lm.z0, lm.z1) - 6; r.player.y = absentialis.world.World.GROUND + 14;
                r.player.yaw = (float) Math.PI; r.player.pitch = -0.5f;
                say(lm.name + "  (" + lm.district.display + ")");
            }
        }
    }
}
