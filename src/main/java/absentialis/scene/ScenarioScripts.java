package absentialis.scene;

import static absentialis.scene.Scenarios.*;

import absentialis.persistence.Profile;

/**
 * The scripts {@link Scenarios} can run. Each is a short, named sequence built for one thing that has to be looked
 * at: a screen, a pose, a room. Add one here when a change needs eyes on it.
 */
final class ScenarioScripts {
    private ScenarioScripts() {}

    /** Leave the allegiance question unanswered (to photograph it). */
    static boolean keepUnaligned;

    static void build(String name) {
        switch (name == null ? "" : name) {
            case "shop" -> shop();
            case "menus" -> menus();
            case "shop_user" -> shopUser();
            case "specials" -> specials();
            case "falls" -> falls();
            case "car" -> car();
            case "interiors" -> interiors();
            case "devmap" -> devmap();
            case "glass" -> glass();
            case "poses" -> poses();
            case "devmode" -> devmode();
            case "director" -> director();
            case "landmarks" -> landmarks();
            case "v09" -> v09();
            case "gear" -> gear();
            case "fx10" -> fx10();
            case "props10" -> props10();
            case "climb10" -> climb10();
            case "ui10" -> ui10();
            case "chain10" -> chain10();
            case "infected10" -> infected10();
            default -> { if (!ScenarioScripts11.build(name)) System.out.println("[Scenario] unknown: " + name); }
        }
    }

    /** Buying a weapon and seeing it in the stash without reloading the profile (the 0.6 report). */
    private static void shop() {
        profile(p -> p.scrip = 200000);
        hideout(6, h -> h.hideout().tab = HideoutScreen.TAB_INVENTORY);
        hideout(16, h -> shot(h, "shop_00_stash_before"));
        hideout(18, h -> h.hideout().debugShow(0, false));
        hideout(28, h -> shot(h, "shop_01_weapons"));
        hideout(30, h -> { h.hideout().debugBuy("weapon.pm9"); h.console("give weapon.matilda"); });
        hideout(40, h -> shot(h, "shop_02_after_buy"));
        hideout(42, h -> h.hideout().tab = HideoutScreen.TAB_INVENTORY);
        hideout(52, h -> shot(h, "shop_03_stash_after"));
        hideout(54, h -> { Profile p = h.ctx().profile; StringBuilder b = new StringBuilder(); for (var i : p.stash.items) b.append(i.defId).append('@').append(i.x).append(',').append(i.y).append(' '); System.out.println("[Scenario] stash: " + b); });
    }

    /** The same purchase against a profile already in the scratch save directory as slot 3 (a copy of a real one). */
    private static void shopUser() {
        hideout(6, h -> { h.ctx().profile.scrip += 200000; h.hideout().tab = HideoutScreen.TAB_INVENTORY; });
        hideout(16, h -> shot(h, "user_00_stash_before"));
        hideout(18, h -> h.hideout().debugShow(0, false));
        hideout(30, h -> { h.hideout().debugBuy("weapon.pm9"); h.console("give weapon.matilda"); });
        hideout(42, h -> h.hideout().tab = HideoutScreen.TAB_INVENTORY);
        hideout(52, h -> shot(h, "user_01_stash_after"));
        hideout(54, h -> { Profile p = h.ctx().profile; StringBuilder b = new StringBuilder(); for (var i : p.stash.items) b.append(i.defId).append('@').append(i.x).append(',').append(i.y).append(i.rotated ? "r " : " "); System.out.println("[Scenario] stash " + p.stash.w + "x" + p.stash.h + ": " + b + " verify=" + p.verify()); });
    }

    private static final String[] CAST = {"walker", "sprinter", "bomber", "spewer", "tank", "gladiator", "butcher"};
    private static final absentialis.entity.Actor[] cast = new absentialis.entity.Actor[CAST.length];
    private static float cx, cy, cz;

    /** The infected, stood in a row in daylight and walked round: every special's model, front, side and back. */
    private static void specials() {
        profile(null);
        raid(20, h -> {
            Raid r = h.raid().raid;
            r.god = true; r.populate = false; r.freezeAI = true;
            r.actors.removeIf(a -> !a.isPlayer);
            cx = r.player.x; cy = r.player.y; cz = r.player.z;
            for (int i = 0; i < CAST.length; i++) {
                cast[i] = r.debugSpawn(CAST[i], cx + (i - 3) * 2.4f, cz - 7, cy);
                cast[i].yaw = (float) Math.PI;
                cast[i].homeX = cast[i].x; cast[i].homeZ = cast[i].z;
            }
            place(r, cx, cy, cz + 1.5f, 0, -0.02f);
        });
        raid(50, h -> shot(h, "spec_00_row"));
        int f = 54;
        for (int i = 2; i < CAST.length; i++) {
            final int k = i;
            raid(f, h -> place(h.raid().raid, cast[k].x, cast[k].y, cast[k].z + (k == 4 ? 4.2f : 3.0f), 0, k == 4 ? 0.12f : 0.02f));
            raid(f + 12, h -> shot(h, "spec_" + k + "0_" + CAST[k] + "_front"));
            raid(f + 14, h -> place(h.raid().raid, cast[k].x + (k == 4 ? 4.2f : 3.0f), cast[k].y, cast[k].z, (float) Math.PI / 2, k == 4 ? 0.12f : 0.02f));
            raid(f + 26, h -> shot(h, "spec_" + k + "1_" + CAST[k] + "_side"));
            raid(f + 28, h -> place(h.raid().raid, cast[k].x, cast[k].y, cast[k].z - (k == 4 ? 4.2f : 3.0f), (float) Math.PI, k == 4 ? 0.12f : 0.02f));
            raid(f + 40, h -> shot(h, "spec_" + k + "2_" + CAST[k] + "_back"));
            f += 42;
        }
        // bile: what it does to the picture
        raid(f, h -> { Raid r = h.raid().raid; place(r, cx, cy, cz + 1.5f, 0, -0.02f); r.bile(r.player, 15); });
        raid(f + 30, h -> shot(h, "spec_90_biled"));
    }

    private static absentialis.entity.Actor subject;
    private static float sx, sy, sz;

    /** A hit, by name, on the subject standing at the mark. The blow travels +x (left to right across the picture). */
    private static void strike(Raid r, String what) {
        absentialis.entity.Actor from = new absentialis.entity.Actor();
        from.x = sx - 12; from.y = sy; from.z = sz; from.faction = "raiders"; from.archetype = "rifleman";
        absentialis.entity.Actor v = subject;
        switch (what) {
            case "chest" -> r.damageActor(v, from, 9999, absentialis.gameplay.items.Content.def("ammo.556"), "torso", v.x, v.y + 1.25f, v.z);
            case "head_from_behind" -> r.damageActor(v, from, 9999, absentialis.gameplay.items.Content.def("ammo.556"), "head", v.x, v.y + 1.68f, v.z);
            case "leg" -> r.damageActor(v, from, 9999, absentialis.gameplay.items.Content.def("ammo.556"), "legs", v.x, v.y + 0.5f, v.z);
            case "buckshot" -> { r.hitKind = ""; v.hitForce = 160; r.damageActor(v, from, 9999, absentialis.gameplay.items.Content.def("ammo.shells"), "torso", v.x, v.y + 1.2f, v.z); }
            case "running" -> { v.vx = 5.2f; v.pvx = 5.2f; r.damageActor(v, from, 9999, absentialis.gameplay.items.Content.def("ammo.556"), "torso", v.x, v.y + 1.25f, v.z); }
            case "blast" -> r.explode(sx - 2.6f, sy + 0.3f, sz, 6, 400, 0, false);
            case "shove" -> r.gore.ragdolls.knockDown(v, 1, 0, 95, 2.2f);
            case "shove_back" -> r.gore.ragdolls.knockDown(v, 1, 0, 95, 2.2f);
            default -> { }
        }
    }

    /**
     * The fall study (instructions.md, Sydney Engine touching up): one body after another is hit in a named way in
     * front of a fixed camera and photographed every third frame, and each run is tiled into a contact sheet.
     * {cast, facing yaw, the hit, frames to film}. Facing pi/2 looks at the shooter (-x); -pi/2 has its back to it.
     */
    private static final Object[][] FALLS = {
            {"survivors", 1.5708f, "chest", 132}, {"survivors", -1.5708f, "head_from_behind", 132}, {"military", 1.5708f, "leg", 132}, {"raiders", 3.1416f, "buckshot", 132},
            {"survivors", -1.5708f, "running", 132}, {"military", 1.5708f, "blast", 132}, {"survivors", 1.5708f, "shove", 330}, {"mercenaries", -1.5708f, "shove_back", 330},
            {"walker", 1.5708f, "chest", 132}, {"walker", 1.5708f, "shove", 330}};

    private static void falls() {
        profile(null);
        raid(20, h -> {
            Raid r = h.raid().raid;
            r.god = true; r.populate = false; r.freezeAI = true;
            r.actors.removeIf(a -> !a.isPlayer);
            sx = r.player.x; sy = r.player.y; sz = r.player.z - 3.0f;
            place(r, sx + 0.8f, sy, sz + 3.0f, 0, -0.16f);
        });
        int f = 30;
        for (int k = 0; k < FALLS.length; k++) {
            final int n = k;
            final Object[] e = FALLS[k];
            final int frames = (Integer) e[3] > 200 ? 360 : 96, every = frames > 200 ? 15 : 4;
            raid(f, h -> {
                Raid r = h.raid().raid;
                r.actors.removeIf(a -> !a.isPlayer);
                subject = r.debugSpawn((String) e[0], sx, sz, sy);
                subject.yaw = (Float) e[1]; subject.hostileToPlayer = false; subject.homeX = sx; subject.homeZ = sz;
                place(r, sx + 0.8f, sy, sz + 3.0f, 0, -0.16f);
            });
            raid(f + 24, h -> strike(h.raid().raid, (String) e[2]));
            final String name = String.format("fall_%02d_%s_%s", n, e[0], e[2]);
            for (int i = 0; i * every < frames; i++) { final int idx = i; raid(f + 24 + i * every, h -> shot(h, String.format("%s_%03d", name, idx))); }
            raid(f + 24 + frames + 2, h -> sheet(name, 8, 300, 120, 1000, 700, 0.25f));
            f += 24 + frames + 6;
        }
    }

    /** Getting into a car and out of it again, in third person, photographed every fourth frame (the door should lead the body both ways). */
    private static void car() {
        profile(null);
        raid(20, h -> {
            Raid r = h.raid().raid;
            r.god = true; r.populate = false; r.freezeAI = true;
            r.actors.removeIf(a -> !a.isPlayer);
            h.raid().thirdPerson = true;
            h.console("vehicle car.volna");
        });
        raid(60, h -> {
            Raid r = h.raid().raid;
            if (r.vehicles.cars.isEmpty()) { System.out.println("[Scenario] no car"); return; }
            VehicleSystem.Car c = r.vehicles.cars.get(r.vehicles.cars.size() - 1);
            float[] o = new float[3];
            c.sim.origin(o);
            // stand off the driver's side, a few steps from the door
            place(r, o[0] - c.sim.body.rightX() * 2.1f - c.sim.body.fwdX() * 1.2f, r.player.y, o[2] - c.sim.body.rightZ() * 2.1f - c.sim.body.fwdZ() * 1.2f, (float) Math.atan2(c.sim.body.rightX(), c.sim.body.rightZ()), -0.1f);
        });
        raid(90, h -> { boolean ok = h.raid().raid.vehicles.enter(); System.out.println("[Scenario] enter " + ok); });
        for (int i = 0; i < 28; i++) { final int idx = i; raid(90 + i * 4, h -> { shot(h, String.format("car_enter_%03d", idx)); System.out.println(String.format("[Scenario] enter frame %d: progress %.2f door %.2f", idx, h.raid().raid.vehicles.phaseProgress(), h.raid().raid.vehicles.doorOpen())); }); }
        raid(206, h -> sheet("car_enter", 7, 250, 150, 1100, 650, 0.26f));
        // 0.8: the driver in the seat, looked at through the glass from the driver's side, from in front and from the far side
        float[] look = {2.25f, 3.1416f, -2.2f, 1.5708f};
        for (int i = 0; i < look.length; i++) {
            final int k = i;
            raid(210 + i * 16, h -> { Raid r = h.raid().raid; if (r.vehicles.driving != null) { r.player.yaw = VehicleSystem.playerYawOf(r.vehicles.driving.sim) + look[k]; r.player.pitch = -0.20f; } });
            raid(222 + i * 16, h -> { shot(h, "car_drive_" + k); Raid r = h.raid().raid; System.out.println("[Scenario] driver seated " + r.player.seated + " at the wheel " + r.player.atWheel + " seatW " + (r.player.anim == null ? -1 : r.player.anim.seatW) + " speed " + (r.player.anim == null ? -1 : r.player.anim.speed)); });
        }
        raid(280, h -> System.out.println("[Scenario] exit " + h.raid().raid.vehicles.exit()));
        for (int i = 0; i < 28; i++) { final int idx = i; raid(280 + i * 4, h -> { shot(h, String.format("car_exit_%03d", idx)); System.out.println(String.format("[Scenario] exit frame %d: progress %.2f door %.2f edge %.2f", idx, h.raid().raid.vehicles.phaseProgress(), h.raid().raid.vehicles.doorOpen(), h.raid().raid.vehicles.cars.get(0).visual.doorEdgeX())); }); }
        raid(396, h -> sheet("car_exit", 7, 250, 150, 1100, 650, 0.26f));
    }

    /**
     * 1.0, the Astratto Engine's new systems in the city, each photographed: the foliage and the decals of a residential
     * street by day, then the same street in rain, in a storm at the moment of a strike, in snow and in fog; a blast and
     * what it leaves; a muzzle at the moment of a shot. ABS_FX_AT=district moves it (default residential).
     */
    private static void fx10() {
        profile(null);
        Scenarios.hour = 13;
        String at = System.getenv("ABS_FX_AT") == null ? "residential" : System.getenv("ABS_FX_AT");
        raid(30, h -> { Raid r = h.raid().raid; r.god = true; r.freezeAI = true; r.populate = false; h.raid().photo = true; h.console("weather clear"); h.console("time 13"); h.console("tp " + at);
            for (absentialis.entity.Actor a : new java.util.ArrayList<>(r.actors)) if (!a.isPlayer) r.actors.remove(a); });
        raid(40, h -> { Raid r = h.raid().raid; fxAt[0] = r.player.x; fxAt[1] = r.player.y; fxAt[2] = r.player.z; fxAt[3] = r.player.yaw; r.player.pitch = -0.12f; h.raid().thirdPerson = false; });
        raid(260, h -> shot(h, "fx_00_street_clear"));
        raid(262, h -> { h.raid().raid.player.pitch = -0.75f; });
        raid(330, h -> shot(h, "fx_01_ground_foliage_decals"));
        raid(332, h -> { h.raid().raid.player.pitch = -0.12f; h.raid().thirdPerson = true; });
        raid(400, h -> shot(h, "fx_02_third_person"));
        raid(402, h -> { h.raid().thirdPerson = false; h.console("weather rain now"); });
        raid(620, h -> shot(h, "fx_03_rain"));
        raid(622, h -> h.console("weather storm now"));
        raid(800, h -> h.console("weather strike 110 front"));
        raid(803, h -> shot(h, "fx_04_storm_strike"));
        raid(840, h -> shot(h, "fx_05_storm"));
        raid(842, h -> h.console("weather snow now"));
        raid(1200, h -> shot(h, "fx_06_snow"));
        raid(1202, h -> h.console("weather fog now"));
        raid(1420, h -> shot(h, "fx_07_fog"));
        raid(1422, h -> h.console("weather clear"));
        raid(1470, h -> { Raid r = h.raid().raid; float fx = (float) -Math.sin(r.player.yaw), fz = (float) -Math.cos(r.player.yaw); r.explode(r.player.x + fx * 9, r.player.y + 0.4f, r.player.z + fz * 9, 6, 0, 0, false); });
        raid(1474, h -> shot(h, "fx_08_blast_flash"));
        raid(1490, h -> shot(h, "fx_09_blast_smoke"));
        raid(1560, h -> shot(h, "fx_10_blast_after"));
        raid(1562, h -> { Raid r = h.raid().raid; float fx = (float) -Math.sin(r.player.yaw), fz = (float) -Math.cos(r.player.yaw), rx = (float) Math.cos(r.player.yaw), rz = (float) -Math.sin(r.player.yaw);
            r.fx.muzzle(r.player.x + fx * 2.2f + rx * 0.5f, r.player.y + 1.0f, r.player.z + fz * 2.2f + rz * 0.5f, rx, 0, rz, 1.4f, false, r.player.y);
            r.fx.sparks(r.player.x + fx * 3f - rx * 0.8f, r.player.y + 1.2f, r.player.z + fz * 3f - rz * 0.8f, -fx, 0.3f, -fz, 18, 7f); });
        raid(1564, h -> shot(h, "fx_11_muzzle_and_sparks"));
        raid(1580, h -> { Raid r = h.raid().raid; var hr = h.ctx().hr; System.out.println("[Scenario] fx: particles " + r.fx.count() + " spawned " + r.fx.spawned + " dropped " + r.fx.dropped + "; foliage " + (hr == null ? -1 : hr.foliage.instances) + " plants, " + (hr == null ? -1 : hr.foliage.triangles) + " triangles, " + (hr == null ? -1 : hr.foliage.patchesBuilt) + " patches; decals " + (hr == null ? -1 : hr.decals.staticQuads) + " static, " + (hr == null ? -1 : hr.decals.dynamicQuads) + " dynamic; strikes " + r.weather.strikes); });
    }
    private static final float[] fxAt = new float[4];

    /**
     * 1.0, the pull-up (Sydney's contextual motion), on the devmap's three-block platform: four frames of it from the side
     * in third person with a rifle, four through the survivor's own eyes, and four of a jump with a rifle in the hands.
     * Each set is tiled into one sheet. ABS_CLIMB_T sets the four moments (default 0.12, 0.38, 0.62, 0.86).
     */
    private static void climb10() {
        profile(p -> give(p, "weapon.akr", 1, true));
        Scenarios.hour = 13;
        for (int f = 8; f < 3000; f += 30) hideout(f, h -> { if (absentialis.world.gen.DevMap.mansion == null && h.worldReady()) h.console("devmap 0"); });
        final float px = absentialis.world.gen.DevMap.YARD_X + 8.5f, pz = absentialis.world.gen.DevMap.YARD_Z + 18.9f;   // south of the platform (z 14..17), facing it
        final float[] at = {0.12f, 0.38f, 0.62f, 0.86f};
        if (System.getenv("ABS_CLIMB_T") != null) { String[] s = System.getenv("ABS_CLIMB_T").split(","); for (int i = 0; i < 4 && i < s.length; i++) at[i] = Float.parseFloat(s[i]); }
        raid(30, h -> { Raid r = h.raid().raid; r.god = true; h.raid().photo = true; h.console("weather clear"); h.console("time 13"); h.raid().scenarioHold("primary"); });
        for (int view = 0; view < 3; view++) {
            final int vw = view, base = 60 + view * 620;
            // 0: from the side; 1: through the survivor's eyes; 2: from behind (the slung weapon, the hands on the lip)
            raid(base, h -> { Raid r = h.raid().raid; place(r, px, g(), pz, 0f, 0f); r.player.onGround = true; r.player.mantleT = 0; h.raid().thirdPerson = vw != 1; h.raid().photo = vw != 1;
                h.raid().camOverride = vw == 0 ? new float[]{px + 4.2f, g() + 2.9f, pz - 0.7f, 1.5708f, -0.03f, 66f} : vw == 2 ? new float[]{px - 1.4f, g() + 3.4f, pz + 4.4f, -0.28f, -0.22f, 58f} : null; });
            for (int k = 0; k < 4; k++) {
                final int kk = k;
                // each frame is its own climb, stopped at its moment: the body is put back, the pull-up started and run to that point
                raid(base + 30 + k * 140, h -> { Raid r = h.raid().raid; place(r, px, g(), pz, 0f, vw == 1 ? -0.1f : 0f); r.player.onGround = true; r.player.mantleT = 0; boolean ok = h.raid().scenarioMantle(); if (kk == 0) System.out.println("[Scenario] pull-up started: " + ok + " (to " + r.player.mToX + "," + r.player.mToY + "," + r.player.mToZ + " from " + r.player.mFromX + "," + r.player.mFromZ + ", " + r.player.mantleDur + " s) yaw " + r.player.yaw + " faceSet " + r.player.faceSet + " faceYaw " + r.player.faceYaw + " facing " + r.player.facing()); });
                for (int w = 1; w < 136; w++) raid(base + 30 + k * 140 + w, h -> { Raid r = h.raid().raid; if (r.player.mantleDur > 0 && r.player.mantling()) { float t = 1 - r.player.mantleT / r.player.mantleDur; if (t >= at[kk]) r.player.mantleT = r.player.mantleDur * (1 - at[kk]); } });
                raid(base + 30 + k * 140 + 136, h -> shot(h, String.format("%s_%03d", vw == 0 ? "climb_tp" : vw == 1 ? "climb_fp" : "climb_back", kk)));
            }
            raid(base + 600, h -> sheet(vw == 0 ? "climb_tp" : vw == 1 ? "climb_fp" : "climb_back", 4, vw == 0 ? 380 : 250, 0, vw == 0 ? 640 : 1100, 900, vw == 0 ? 0.62f : 0.4f));
        }
        // the jump with a rifle: the hands stay on it
        final int jb = 60 + 3 * 620;
        raid(jb, h -> { Raid r = h.raid().raid; place(r, px - 4, g(), pz + 4, 0f, 0f); r.player.onGround = true; r.player.mantleT = 0; h.raid().thirdPerson = true; h.raid().photo = true; h.raid().camOverride = new float[]{px - 4 + 3.2f, g() + 1.7f, pz + 4 - 0.4f, 1.5708f, -0.05f, 62f}; });
        raid(jb + 40, h -> { Raid r = h.raid().raid; r.player.vy = absentialis.entity.Physics.JUMP; r.player.onGround = false; if (r.player.anim != null) r.player.anim.play("jump", 0.5f); });
        for (int k = 0; k < 4; k++) { final int kk = k; raid(jb + 44 + k * 9, h -> shot(h, String.format("jump_%03d", kk))); }
        raid(jb + 90, h -> { sheet("jump", 4, 350, 60, 900, 780, 0.5f); h.raid().camOverride = null; });
    }

    private static final absentialis.entity.Actor[] newCast = new absentialis.entity.Actor[3];

    /** 1.0, the two new special infected beside the Butcher: stood in a row, each from the front and the side; then let loose on a bystander. */
    private static void infected10() {
        profile(null);
        Scenarios.hour = 13;
        for (int f = 8; f < 3000; f += 30) hideout(f, h -> { if (absentialis.world.gen.DevMap.mansion == null && h.worldReady()) h.console("devmap 0"); });
        final String[] who = {"crawler", "tyrant", "butcher"};
        final float bx = absentialis.world.gen.DevMap.ROAD_X0 + 30, bz = (absentialis.world.gen.DevMap.ROAD_Z0 + absentialis.world.gen.DevMap.ROAD_Z1) / 2f + 0.5f;
        raid(30, h -> {
            Raid r = h.raid().raid;
            r.god = true; r.populate = false; r.freezeAI = true; h.raid().photo = true; h.console("weather clear");
            r.actors.removeIf(a -> !a.isPlayer);
            for (int i = 0; i < 3; i++) { newCast[i] = r.debugSpawn(who[i], bx + (i - 1) * 3.2f, bz - 6, g()); newCast[i].yaw = (float) Math.PI; newCast[i].homeX = newCast[i].x; newCast[i].homeZ = newCast[i].z; }
            place(r, bx, g(), bz + 2.5f, 0, -0.02f);
        });
        raid(70, h -> shot(h, "inf_00_row"));
        int f = 74;
        for (int i = 0; i < 2; i++) {
            final int k = i;
            raid(f, h -> place(h.raid().raid, newCast[k].x, g(), newCast[k].z + (k == 1 ? 4.6f : 2.6f), 0, k == 1 ? 0.10f : -0.22f));
            raid(f + 14, h -> shot(h, "inf_" + (k + 1) + "0_" + who[k] + "_front"));
            raid(f + 16, h -> place(h.raid().raid, newCast[k].x + (k == 1 ? 4.6f : 2.6f), g(), newCast[k].z, (float) Math.PI / 2, k == 1 ? 0.10f : -0.22f));
            raid(f + 30, h -> shot(h, "inf_" + (k + 1) + "1_" + who[k] + "_side"));
            f += 34;
        }
        // let loose: the Tyrant at a bystander down the road (the charge), then the Crawler at a noise (the tongue, the leap)
        final int g0 = f;
        raid(g0, h -> { Raid r = h.raid().raid; r.actors.removeIf(a -> !a.isPlayer); newCast[1] = r.debugSpawn("tyrant", bx - 9, bz, g()); newCast[1].yaw = (float) -Math.PI / 2; absentialis.entity.Actor v = r.debugSpawn("survivors", bx + 4, bz, g()); newCast[2] = v; v.hostileToPlayer = false; r.frozen.add(v.id); r.freezeAI = false; place(r, bx - 2, g(), bz + 9, 0, -0.05f); });
        for (int k = 0; k < 6; k++) { final int kk = k; raid(g0 + 60 + k * 70, h -> shot(h, "inf_30_tyrant_charge_" + kk)); }
        raid(g0 + 500, h -> { Raid r = h.raid().raid; System.out.println("[Scenario] tyrant: tackles " + r.tackles + ", victim " + (newCast[2].dead ? "dead" : newCast[2].ragdoll != null ? "down" : "standing") + " hp " + Math.round(newCast[2].hp)); });
        final int c0 = g0 + 510;
        raid(c0, h -> { Raid r = h.raid().raid; r.actors.removeIf(a -> !a.isPlayer); newCast[0] = r.debugSpawn("crawler", bx - 8, bz, g()); absentialis.entity.Actor v = r.debugSpawn("survivors", bx + 1, bz, g()); newCast[2] = v; v.hostileToPlayer = false; r.frozen.add(v.id); place(r, bx - 3, g(), bz + 8, 0, -0.08f); });
        for (int k = 0; k < 8; k++) { final int kk = k; raid(c0 + 6 + k * 16, h -> { Raid r = h.raid().raid; if (kk < 6) r.emitSound(newCast[2].x, newCast[2].y + 1, newCast[2].z, 30, "shout", newCast[2].id, false); shot(h, "inf_40_crawler_" + kk); }); }
        raid(c0 + 150, h -> { Raid r = h.raid().raid; System.out.println("[Scenario] crawler: tongue hits " + r.tongueHits + ", pounces " + r.pounces + ", victim hp " + Math.round(newCast[2].hp) + " crawler state " + newCast[0].state + " at " + newCast[0].x + "," + newCast[0].z + " victim at " + newCast[2].x); });
        // indoors: it takes to the ceiling
        raid(c0 + 154, h -> { Raid r = h.raid().raid; r.actors.removeIf(a -> !a.isPlayer); var mn = absentialis.world.gen.DevMap.mansion; float mx = (mn.x0 + mn.x1) / 2f + 0.5f, mz = (mn.z0 + mn.z1) / 2f + 0.5f; int fy = r.standY((int) mx, (int) mz, (int) g()); newCast[0] = r.debugSpawn("crawler", mx, mz, fy); place(r, mx + 4, fy, mz + 5, 0.67f, 0.25f); h.raid().photo = true; });
        raid(c0 + 480, h -> { Raid r = h.raid().raid; var k = newCast[0]; float cx = k.x + 2.4f, cy = r.player.y + 1.0f, cz = k.z + 2.8f, dx = k.x - cx, dy = k.y + 0.5f - cy, dz = k.z - cz; h.raid().camOverride = new float[]{cx, cy, cz, (float) Math.atan2(-dx, -dz), (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)), 60f}; });
        raid(c0 + 520, h -> { Raid r = h.raid().raid; shot(h, "inf_50_crawler_ceiling"); h.raid().camOverride = null; System.out.println("[Scenario] crawler indoors: cling " + newCast[0].cling + " y " + newCast[0].y + " clings " + absentialis.neuralai.integration.Creatures.clings); });
    }

    /** 1.0, the faction missions on the developer map: an advisor is walked up to and spoken to, the work is taken, the thing is found, the decision is made; then a roadside meeting. */
    private static void chain10() {
        profile(p -> give(p, "med.bandage", 2, false));
        Scenarios.hour = 13;
        for (int f = 8; f < 3000; f += 30) hideout(f, h -> { if (absentialis.world.gen.DevMap.mansion == null && h.worldReady()) h.console("devmap 0"); });
        raid(30, h -> { Raid r = h.raid().raid; r.god = true; h.console("weather clear"); });
        raid(80, h -> { Raid r = h.raid().raid; var p0 = r.chains.posts.get(0); place(r, p0.x, p0.y, p0.z + 2.2f, 0f, -0.05f); System.out.println("[Scenario] advisors: " + r.chains.posts.size() + ", first " + p0.advisor.name() + " present " + (p0.actor != null)); });
        raid(130, h -> shot(h, "chain_00_advisor"));
        raid(134, h -> { Raid r = h.raid().raid; r.chains.talk(r.chains.posts.get(0), h.raid().popup); });
        raid(150, h -> shot(h, "chain_01_offer"));
        raid(154, h -> h.raid().popup.pick(0));
        raid(160, h -> { Raid r = h.raid().raid; for (int i = 0; i < 3; i++) r.chains.onContainer(10 + i, 24, 10, null); System.out.println("[Scenario] stage after search: " + r.profile.chains.of("mercenaries").stage); });
        raid(200, h -> { Raid r = h.raid().raid; r.chains.talk(r.chains.posts.get(0), h.raid().popup); });
        raid(216, h -> shot(h, "chain_02_decision"));
        raid(220, h -> h.raid().popup.pick(1));
        raid(236, h -> shot(h, "chain_03_outcome"));
        raid(240, h -> { Raid r = h.raid().raid; h.raid().popup.pick(0); System.out.println("[Scenario] chain: done " + r.profile.chains.of("mercenaries").done + ", flags " + r.profile.chains.flags + ", rep survivors " + r.profile.rep("survivors")); });
        raid(244, h -> { Raid r = h.raid().raid; var v = r.debugSpawn("raiders", r.player.x, r.player.z - 3, r.player.y); v.hostileToPlayer = false; r.frozen.add(v.id); h.raid().encounters.force(v, h.raid().popup); });
        raid(262, h -> shot(h, "chain_04_encounter_toll"));
        raid(266, h -> { h.raid().popup.pick(0); Raid r = h.raid().raid; var v = r.debugSpawn("survivors", r.player.x + 1, r.player.z - 3, r.player.y); v.hostileToPlayer = false; r.frozen.add(v.id); h.raid().encounters.force(v, h.raid().popup); });
        raid(284, h -> shot(h, "chain_05_encounter_asking"));
        raid(288, h -> h.raid().popup.pick(0));
    }

    /** 1.0, the interface: the HUD in play, the inventory alone / with a container / with an empty one. */
    private static void ui10() {
        profile(p -> { give(p, "weapon.akr", 1, true); give(p, "med.spray", 2, false); give(p, "med.bandage", 3, false); });
        Scenarios.hour = 13;
        for (int f = 8; f < 3000; f += 30) hideout(f, h -> { if (absentialis.world.gen.DevMap.mansion == null && h.worldReady()) h.console("devmap 0"); });
        raid(30, h -> { Raid r = h.raid().raid; r.god = true; h.console("weather clear"); h.console("time 13"); h.raid().scenarioHold("primary"); });
        raid(80, h -> shot(h, "ui_00_hud"));
        raid(84, h -> { Raid r = h.raid().raid; r.player.hp = 46; r.body.stamina = 35; r.body.energy = 15; r.body.healthChanged = r.time; r.body.staminaChanged = r.time; h.raid().hud.message("Picked up 5.56 x30", absentialis.ui.Ui.TXT, r.time); });
        raid(100, h -> shot(h, "ui_01_hud_hurt"));
        raid(101, h -> { Raid r = h.raid().raid; r.body.bleedLight = true; r.body.legInjury = 0.5f; r.extracting = r.exits.isEmpty() ? null : r.exits.get(0); r.extractProgress = 2.8f; });
        raid(102, h -> { Raid r = h.raid().raid; r.extracting = r.exits.isEmpty() ? null : r.exits.get(0); r.extractProgress = 2.8f; });
        raid(103, h -> { Raid r = h.raid().raid; r.extracting = r.exits.isEmpty() ? null : r.exits.get(0); r.extractProgress = 2.8f; shot(h, "ui_01b_hud_extracting"); r.extracting = null; r.body.bleedLight = false; });
        raid(104, h -> h.raid().scenarioInventory(0));
        raid(130, h -> shot(h, "ui_02_inventory"));
        raid(134, h -> { h.raid().scenarioInventory(-1); h.raid().scenarioInventory(1); });
        raid(160, h -> shot(h, "ui_03_inventory_container"));
        raid(164, h -> { h.raid().scenarioInventory(-1); h.raid().scenarioInventory(2); });
        raid(172, h -> shot(h, "ui_04_inventory_empty"));
        raid(176, h -> { h.raid().scenarioInventory(-1); h.raid().scenarioInventory(0); h.ctx().inv.selected = h.ctx().profile.loadout.slot("primary"); h.ctx().inv.inspectOpen = h.ctx().inv.selected; });
        raid(196, h -> shot(h, "ui_05_inspect"));
        raid(200, h -> { h.ctx().inv.inspectOpen = null; h.raid().scenarioInventory(-1); });
        // the third-person camera: at rest, aiming over the shoulder (the reference pictures), and using the spray with a rifle slung
        raid(204, h -> { Raid r = h.raid().raid; r.player.hp = 100; r.body.legInjury = 0; r.body.energy = 80; h.raid().thirdPerson = true; h.raid().scenarioAim = false; });
        raid(250, h -> shot(h, "cam_00_third_person"));
        raid(254, h -> h.raid().scenarioAim = true);
        raid(300, h -> shot(h, "cam_01_ots_aim"));
        raid(304, h -> { Raid r = h.raid().raid; h.raid().scenarioAim = false; r.body.using = absentialis.gameplay.items.Content.def("med.spray"); r.body.useTimer = 4f; r.player.anim.play("heal", 4f); h.raid().camOverride = new float[]{r.player.x - 1.9f, r.player.y + 1.45f, r.player.z - 2.3f, (float) Math.PI + 0.7f, -0.08f, 55f}; });
        raid(350, h -> shot(h, "cam_02_spray_third_person"));
        raid(354, h -> { Raid r = h.raid().raid; r.body.using = null; r.body.useTimer = 0; h.raid().camOverride = null; h.raid().thirdPerson = false; });
        raid(360, h -> { h.raid().dev.toggle(); h.raid().dev.panel = true; h.raid().dev.tab = 5; });
        raid(380, h -> shot(h, "ui_06_devmode_profile"));
        raid(384, h -> { h.raid().dev.panel = false; h.raid().dev.toggle(); });
        raid(390, h -> { var p = h.ctx().profile; for (absentialis.gameplay.items.Content.Recipe rc : absentialis.gameplay.items.Content.RECIPES) { if (!rc.where.equals("field")) continue; int n = 0; for (Object[] in : rc.inputs) { if (n++ > 40) break; var it = new absentialis.gameplay.items.ItemInstance((String) in[0]); it.qty = Math.min(it.def().maxStack, (Integer) in[1] * 2); absentialis.gameplay.inventory.InventoryService.store(it, p.loadout); } if (p.loadout.craft.size() > 10) break; } h.raid().scenarioInventory(0); h.raid().scenarioTab(3); });
        raid(410, h -> shot(h, "ui_07_crafting"));
        raid(414, h -> { h.raid().scenarioTab(0); h.raid().scenarioInventory(-1); });
    }

    /** 1.0, the devmap's prop gallery, row by row: every prop model, three pictures to a row. */
    private static void props10() {
        profile(null);
        Scenarios.hour = 13;
        for (int f = 8; f < 3000; f += 30) hideout(f, h -> { if (absentialis.world.gen.DevMap.mansion == null && h.worldReady()) h.console("devmap 0"); });
        raid(30, h -> { Raid r = h.raid().raid; r.god = true; r.noclip = true; h.raid().photo = true; h.raid().thirdPerson = false; h.console("weather clear"); h.console("time 13"); });
        final int G0 = absentialis.world.World.GROUND;
        int f = 40;
        for (int row = 0; row < 12; row++) for (int part = 0; part < 3; part++) {
            final int rw = row, pt = part;
            for (int k = 0; k < 80; k += 20) raid(f + k, h -> { if (rw >= absentialis.world.gen.DevMap.galleryRows) return; Raid r = h.raid().raid;
                place(r, absentialis.world.gen.DevMap.GALLERY_X + 11 + pt * 21, G0 + 0.9f, absentialis.world.gen.DevMap.GALLERY_Z + rw * absentialis.world.gen.DevMap.GALLERY_PITCH + 6.2f, 0f, -0.06f); r.player.onGround = true; r.player.fallSpeed = 0; });
            raid(f + 86, h -> { if (rw < absentialis.world.gen.DevMap.galleryRows) shot(h, String.format("props_%02d_%d", rw, pt)); });
            f += 90;
        }
    }

    private static final java.util.List<Object[]> ROOMS = new java.util.ArrayList<>();

    /**
     * The interior survey (instructions.md rule 10): one room of every kind in every kind of building, photographed
     * from a corner at eye height looking across it, by day. ABS_ROOMS limits how many (default 60); ABS_ROOM_FILTER
     * keeps only rooms whose "archetype/type" contains the text. Each picture is named for what it shows.
     */
    private static void interiors() {
        profile(null);
        final int max = System.getenv("ABS_ROOMS") != null ? Integer.parseInt(System.getenv("ABS_ROOMS")) : 60;
        final String filter = System.getenv("ABS_ROOM_FILTER");
        raid(20, h -> {
            Raid r = h.raid().raid;
            r.god = true; r.populate = false; r.freezeAI = true;
            r.actors.removeIf(a -> !a.isPlayer);
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (absentialis.world.MapData.Building b : r.world.map.buildings) for (absentialis.world.MapData.Room rm : b.rooms) {
                String key = (b.landmark.isEmpty() ? b.archetype : b.landmark) + "/" + rm.type;
                if (rm.circulation && !key.contains("hall") || (rm.x1 - rm.x0) < 2 || (rm.z1 - rm.z0) < 2) continue;
                if (filter != null) { boolean hit = false; for (String part : filter.split(",")) hit |= key.endsWith(part) || key.contains(part + "/"); if (!hit) continue; }
                if (!seen.add(System.getenv("ABS_ROOM_ONE") != null ? rm.type : key) || ROOMS.size() >= max) continue;
                ROOMS.add(new Object[]{key.replace('/', '_').replace(' ', '_'), rm, b});
            }
            System.out.println("[Scenario] interiors: " + ROOMS.size() + " rooms of " + seen.size() + " kinds");
        });
        for (int k = 0; k < max; k++) {
            final int n = k;
            raid(30 + k * 170, h -> {
                if (n >= ROOMS.size()) return;
                Raid r = h.raid().raid;
                absentialis.world.MapData.Room rm = (absentialis.world.MapData.Room) ROOMS.get(n)[1];
                // stand in the free corner cell furthest from the middle that has room to stand, and look at the middle
                float cx = (rm.x0 + rm.x1 + 1) / 2f, cz = (rm.z0 + rm.z1 + 1) / 2f, bx = cx, bz = cz, bd = -1;
                for (int x = rm.x0; x <= rm.x1; x++) for (int z = rm.z0; z <= rm.z1; z++) {
                    if (r.world.solid(x, rm.y + 1, z) || r.world.solid(x, rm.y + 2, z) || !r.world.solid(x, rm.y, z)) continue;
                    float d = (x + 0.5f - cx) * (x + 0.5f - cx) + (z + 0.5f - cz) * (z + 0.5f - cz);
                    if (d > bd) { bd = d; bx = x + 0.5f; bz = z + 0.5f; }
                }
                place(r, bx, rm.y + 1, bz, (float) Math.atan2(-(cx - bx), -(cz - bz)), -0.18f);
                h.raid().thirdPerson = false;
            });
            raid(30 + k * 170 + 164, h -> { if (n < ROOMS.size()) shot(h, String.format("room_%02d_%s", n, ROOMS.get(n)[0])); });
        }
    }

    /** The devmap, loaded the way a developer loads it (the console), and looked at from each of its four parts. */
    private static void devmap() {
        profile(null);
        for (int f = 8; f < 3000; f += 30) hideout(f, h -> { if (absentialis.world.gen.DevMap.mansion == null && h.worldReady()) h.console("devmap 0"); });
        raid(60, h -> { Raid r = h.raid().raid; r.god = true; place(r, absentialis.world.gen.DevMap.ROAD_X0 + 4, g(), (absentialis.world.gen.DevMap.ROAD_Z0 + absentialis.world.gen.DevMap.ROAD_Z1) / 2f + 0.5f, -1.5708f, -0.03f); });
        raid(150, h -> shot(h, "dev_00_road"));
        raid(152, h -> place(h.raid().raid, absentialis.world.gen.DevMap.mazeInX + 1, g(), absentialis.world.gen.DevMap.mazeInZ - 7, 3.1416f, -0.02f));
        raid(240, h -> shot(h, "dev_01_maze"));
        raid(242, h -> place(h.raid().raid, absentialis.world.gen.DevMap.mansion.entryX + 0.5f, g(), absentialis.world.gen.DevMap.mansion.entryZ - 9, 3.1416f, 0.12f));
        raid(330, h -> shot(h, "dev_02_mansion"));
        raid(332, h -> place(h.raid().raid, absentialis.world.gen.DevMap.YARD_X + 2, g(), absentialis.world.gen.DevMap.YARD_Z + 12, -1.35f, -0.05f));
        raid(420, h -> shot(h, "dev_03_yard"));
        raid(422, h -> place(h.raid().raid, absentialis.world.gen.DevMap.holesX + 8, g(), absentialis.world.gen.DevMap.holesZ + 7, 0f, 0.15f));
        raid(510, h -> shot(h, "dev_04_openings"));
    }

    /**
     * 0.8, Developer Mode on the devmap: the status bar and the inspector over a Gladiator and a Butcher that do not
     * know the camera is there, then the workbench open on each of its pages - and a laser, a stance icon and the
     * third-person reticle while the survivor is still a survivor.
     */
    private static void devmode() {
        profile(p -> { p.prog.addXp(absentialis.gameplay.progression.Progression.xpToReach(12)); });
        for (int f = 8; f < 3000; f += 30) hideout(f, h -> { if (absentialis.world.gen.DevMap.mansion == null && h.worldReady()) h.console("devmap 0"); });
        float rx = absentialis.world.gen.DevMap.ROAD_X0 + 6, rz = (absentialis.world.gen.DevMap.ROAD_Z0 + absentialis.world.gen.DevMap.ROAD_Z1) / 2f + 0.5f;
        raid(60, h -> { Raid r = h.raid().raid; r.god = true; place(r, rx, g(), rz, -1.5708f, -0.03f); var rifle = new absentialis.gameplay.items.ItemInstance("weapon.akr"); rifle.rounds = rifle.capacity(); rifle.ammoId = "ammo.assault"; absentialis.gameplay.inventory.InventoryService.equip(rifle, h.ctx().profile.loadout, "primary"); });
        raid(70, h -> h.raid().scenarioHold("primary"));
        // the survivor first: third person, aiming, with and without a laser
        raid(90, h -> { h.raid().thirdPerson = true; h.raid().scenarioAim = true; });
        raid(150, h -> shot(h, "devui_00_thirdperson_reticle"));
        raid(152, h -> { var gun = h.ctx().profile.loadout.slot("primary"); if (gun != null) absentialis.gameplay.weapons.Gunsmith.install(gun, "att.laser.5mw"); h.raid().raid.profile.zone.hour = 22; });
        raid(230, h -> shot(h, "devui_01_green_laser_night"));
        raid(232, h -> { h.raid().thirdPerson = false; });
        raid(290, h -> shot(h, "devui_02_laser_first_person"));
        raid(292, h -> { h.raid().scenarioAim = false; h.raid().raid.profile.zone.hour = 13; h.raid().raid.player.crouch = true; });
        raid(310, h -> shot(h, "devui_03_stance_icon"));
        raid(312, h -> { h.raid().raid.player.crouch = false; h.console("devmode"); });
        raid(330, h -> { Raid r = h.raid().raid; r.player.x = rx; r.player.z = rz; r.player.y = g() + 3.2f; r.player.yaw = -1.5708f; r.player.pitch = -0.32f; h.raid().dev.aimNow(); h.raid().dev.count = 1; h.raid().dev.frozen = false; });
        raid(334, h -> { DevMode d = h.raid().dev; d.target[0] = rx + 9; d.target[1] = g(); d.target[2] = rz - 2; d.spawnNpc("gladiator"); d.target[0] = rx + 11; d.target[2] = rz + 2.5f; d.spawnNpc("butcher"); d.target[0] = rx + 7; d.target[2] = rz + 5; d.spawnNpc("raiders"); });
        raid(520, h -> shot(h, "devui_04_ghost_inspector"));
        for (int t = 0; t < 5; t++) { final int tab = t; raid(524 + t * 40, h -> { h.raid().dev.panel = true; h.raid().dev.tab = tab; }); raid(556 + t * 40, h -> shot(h, "devui_1" + tab + "_workbench_" + DevMode.TABS[tab].toLowerCase())); }
        raid(730, h -> { h.raid().dev.panel = false; h.console("devmode"); });
        raid(760, h -> shot(h, "devui_20_back_as_survivor"));
    }

    /**
     * 0.9 in the city, as it is played: a knife and somebody's back (the prompt, the takedown, the notice), what is found on
     * the body (its equipment in equipment slots), a ledge pulled up onto, the field map zoomed out past the edge of the
     * sheet, and the district's hotspot said and marked.
     */
    private static void v09() {
        profile(null);
        Scenarios.hour = 13;
        final int F = org.lwjgl.glfw.GLFW.GLFW_KEY_F;
        raid(30, h -> { Raid r = h.raid().raid; r.god = true; r.populate = false; h.console("weather clear");
            for (absentialis.entity.Actor a : new java.util.ArrayList<>(r.actors)) if (!a.isPlayer) r.actors.remove(a);
            var knife = new absentialis.gameplay.items.ItemInstance("melee.knife");
            absentialis.gameplay.inventory.InventoryService.equip(knife, h.ctx().profile.loadout, "melee"); });
        raid(40, h -> { Raid r = h.raid().raid; absentialis.entity.Actor p = r.player;
            float fx = (float) -Math.sin(p.yaw), fz = (float) -Math.cos(p.yaw);
            float[] s = r.standSpot(p.x + fx * 1.5f, p.z + fz * 1.5f);
            v09Guard = r.debugSpawn("military", s[0], s[2], s[1]);
            v09Guard.hostileToPlayer = true; v09Guard.yaw = p.yaw; v09Guard.homeX = s[0]; v09Guard.homeZ = s[2]; r.freezeAI = true;
            p.crouch = true; p.pitch = -0.15f; p.yaw = (float) Math.atan2(-(s[0] - p.x), -(s[2] - p.z)); });
        raid(70, h -> { shot(h, "v09_00_takedown_prompt"); System.out.println("[Scenario] takedown on offer: " + (h.raid().raid.takedownTarget() != null) + " " + h.raid().raid.takedownWhy); });
        raid(72, h -> System.out.println("[Scenario] takedown started: " + h.raid().raid.startTakedown()));
        raid(110, h -> shot(h, "v09_01_takedown"));
        raid(200, h -> { Raid r = h.raid().raid; System.out.println("[Scenario] guard dead " + v09Guard.dead + ", stealth kills " + r.debrief.stealthKills + ", seen " + r.takedownsSeen + ", gear slots filled " + (v09Guard.corpseGear == null ? -1 : v09Guard.corpseGear.slots.values().stream().filter(x -> x != null).count()));
            absentialis.entity.Actor p = r.player; p.pitch = -0.9f; p.yaw = (float) Math.atan2(-(v09Guard.x - p.x), -(v09Guard.z - p.z)); });
        raid(260, h -> { shot(h, "v09_02_body"); h.ctx().in.inject(F, true); });
        raid(262, h -> h.ctx().in.inject(F, false));
        raid(300, h -> shot(h, "v09_03_searching_the_body"));
        raid(302, h -> { h.ctx().in.inject(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, true); });
        raid(304, h -> { h.ctx().in.inject(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, false); });
        // the hotspot of the district, brought on and marked; the map zoomed right out
        raid(320, h -> { h.console("hotspot go"); h.console("hotspot"); });
        raid(330, h -> { h.raid().debugMap(true, 0.6f); });
        raid(360, h -> shot(h, "v09_04_map_zoomed_out"));
        raid(362, h -> h.raid().debugMap(false, 1f));
        raid(380, h -> { Raid r = h.raid().raid; System.out.println("[Scenario] hotspots: " + r.events.hotspots.size() + ", announced " + r.events.announced + ", activated " + r.events.activated + ", mantles " + r.mantles); });
    }
    private static absentialis.entity.Actor v09Guard;

    /**
     * 0.9, the two hand-drawn landmarks - the power station as the Chernobyl plant, the big house as the Spencer mansion -
     * from outside and in their rooms. Each picture is taken after the light has had time to settle where the camera was
     * put (the GI probes take a couple of seconds). ABS_LANDMARK=plant or mansion takes only that one's pictures.
     */
    private static void landmarks() {
        // (the power is put on everywhere: a room is judged with its lamps lit; ABS_LANDMARK_DARK=1 leaves it as the Zone has it)
        profile(System.getenv("ABS_LANDMARK_DARK") != null ? null : p -> java.util.Arrays.fill(p.zone.power, true));
        Scenarios.hour = 13;
        String only = System.getenv("ABS_LANDMARK") == null ? "" : System.getenv("ABS_LANDMARK");
        raid(30, h -> { Raid r = h.raid().raid; r.god = true; r.noclip = true; r.freezeAI = true; r.populate = false; h.raid().hudHidden = true; h.raid().photo = true; h.console("weather clear"); h.console("time 13");
            for (absentialis.entity.Actor a : new java.util.ArrayList<>(r.actors)) if (!a.isPlayer) r.actors.remove(a); });
        final int L1 = 7, F1 = 7;
        // {landmark, name, from a, b, y (above the street), look at a, b, y}
        Object[][] shots = {
                {"power_plant", "plant_00_front", 66f, -46f, 30f, 66f, 34f, 12f},
                {"power_plant", "plant_01_sarcophagus", 126f, -16f, 24f, 82f, 30f, 16f},
                {"power_plant", "plant_02_from_the_admin_end", -22f, -8f, 22f, 44f, 36f, 12f},
                {"power_plant", "plant_03_turbine_hall", 10f, 57f, 8f, 80f, 63f, 2f},
                {"power_plant", "plant_04_golden_corridor", 34f, 46.9f, L1 + 1.7f, 97f, 46.9f, L1 + 1.6f},
                {"power_plant", "plant_05_control_room_4", 72f, 49.4f, L1 + 3.2f, 94f, 52f, L1 + 0.8f},
                {"power_plant", "plant_06_reactor_hall_3", 35f, 21f, L1 + 11f, 46f, 32f, L1 + 1f},
                {"power_plant", "plant_07_unit_4", 71f, 21f, L1 + 10f, 85f, 33f, L1 + 0.5f},
                {"power_plant", "plant_08_long_corridor", 31.9f, 13f, L1 + 1.7f, 31.9f, 44f, L1 + 1.6f},
                {"power_plant", "plant_09_control_room_2", 36f, 49.5f, L1 + 2.6f, 56f, 52f, L1 + 1f},
                {"power_plant", "plant_10_sanitary_lock", 60f, 21f, 3.6f, 66f, 42f, 1f},
                {"power_plant", "plant_11_pump_hall_3", 35f, 21f, 3.8f, 56f, 43f, 0.8f},
                {"power_plant", "plant_12_lobby", 9f, 7f, 3.6f, 16f, 14f, 1f},
                {"power_plant", "plant_13_office", 9f, 14f, L1 + 3.4f, 16f, 7f, L1 + 0.8f},
                {"power_plant", "plant_14_archive", 20f, 13f, 2 * L1 + 3.4f, 26f, 7f, 2 * L1 + 0.8f},
                {"power_plant", "plant_15_under_the_core", 71f, 21f, 3.8f, 84f, 33f, 1f},
                {"power_plant", "plant_16_computer_room", 59.5f, 49.5f, L1 + 3f, 68f, 52f, L1 + 1f},
                {"power_plant", "plant_17_reactor_hall_1", 9f, 21f, L1 + 9f, 19f, 33f, L1 + 1f},
                {"power_plant", "plant_18_service_corridor", 20f, 46.9f, 1.7f, 80f, 46.9f, 1.6f},
                {"jail", "jail_00_front", 58f, -14f, 16f, 58f, 30f, 4f},
                {"jail", "jail_01_lobby", 54.5f, 10.5f, 3.3f, 60f, 15f, 1f},
                {"jail", "jail_02_cell_hall", 37f, 35.5f, 7.5f, 78f, 36f, 3f},
                {"jail", "jail_03_upper_tier", 44f, 33.5f, 6.7f, 78f, 37f, 5f},
                {"jail", "jail_04_mess", 11f, 30f, 3.4f, 30f, 35f, 1f},
                {"jail", "jail_05_ward_corridor", 13f, 54.5f, 1.7f, 100f, 54.5f, 1.5f},
                {"jail", "jail_06_day_room", 29.5f, 50.5f, 3.4f, 16f, 46f, 1f},
                {"jail", "jail_07_treatment", 61f, 50.5f, 3.4f, 68f, 46f, 1f},
                {"jail", "jail_08_hydrotherapy", 73f, 50.5f, 3.4f, 84f, 46f, 1f},
                {"jail", "jail_09_observation_ward", 13f, 58f, 3.4f, 32f, 65f, 1f},
                {"jail", "jail_10_theatre", 55f, 58f, 3.4f, 68f, 65f, 1f},
                {"jail", "jail_11_mortuary", 75f, 58f, 3.4f, 87f, 65f, 1f},
                {"jail", "jail_12_padded_cell", 89f, 50.5f, 3.2f, 91f, 45.5f, 1f},
                {"jail", "jail_13_visiting", 42.5f, 15f, 3.3f, 51f, 11f, 1f},
                {"jail", "jail_14_nurses", 32.5f, 50.5f, 3.4f, 40f, 45.5f, 1f},
                {"jail", "jail_15_warden", 63.5f, 14.5f, 3.3f, 72f, 10.5f, 1f},
                {"mansion", "mansion_00_front", 49f, -6f, 9f, 49f, 30f, 7f},
                {"mansion", "mansion_01_corner", 4f, 2f, 15f, 46f, 36f, 6f},
                {"mansion", "mansion_02_main_hall", 44f, 26.6f, 2.2f, 50f, 41f, 5f},
                {"mansion", "mansion_03_hall_from_the_gallery", 41f, 27f, F1 + 2.2f, 51f, 40f, 2f},
                {"mansion", "mansion_04_dining_room", 37.4f, 31.9f, 2.4f, 20f, 31.9f, 2f},
                {"mansion", "mansion_05_corridor", 60f, 39.9f, 1.8f, 81f, 39.9f, 1.7f},
                {"mansion", "mansion_06_statue_gallery", 59.5f, 26.5f, 2.6f, 69f, 36f, 1f},
                {"mansion", "mansion_07_library", 17f, 40f, F1 + 2.6f, 37f, 48f, F1 + 0.8f},
                {"mansion", "mansion_08_kitchen", 25.5f, 48.5f, 3.4f, 17f, 40f, 1f},
                {"mansion", "mansion_09_bar", 37.5f, 48.5f, 3.4f, 29f, 40f, 1f},
                {"mansion", "mansion_10_trophy_room", 80.5f, 48.5f, 3.2f, 73f, 43f, 1f},
                {"mansion", "mansion_11_master_bedroom", 59.5f, 36.5f, F1 + 3f, 68f, 27f, F1 + 0.8f},
                {"mansion", "mansion_12_nursery", 78f, 30.5f, F1 + 3f, 72.5f, 26.5f, F1 + 0.6f},
                {"mansion", "mansion_13_sickroom", 77.5f, 36.5f, F1 + 3f, 72.5f, 33.5f, F1 + 0.6f},
                {"mansion", "mansion_14_far_bedroom", 72.5f, 42.5f, F1 + 3f, 80f, 48.5f, F1 + 0.6f},
                {"mansion", "mansion_15_bathroom", 78f, 26.5f, 3.2f, 73f, 30.5f, 0.8f},
                {"mansion", "mansion_16_store_room", 78f, 33.2f, 3.2f, 72.5f, 37f, 0.6f},
                {"mansion", "mansion_17_east_stairs", 69.5f, 42.5f, 3.2f, 62f, 48.5f, 2f},
        };
        int f = 60, n = 0;
        for (Object[] sh : shots) {
            if (!only.isEmpty() && !((String) sh[0]).startsWith(only) && !((String) sh[1]).startsWith(only)) continue;
            final Object[] q = sh;
            raid(f, h -> lookAt(h.raid().raid, (String) q[0], (Float) q[2], (Float) q[3], (Float) q[4], (Float) q[5], (Float) q[6], (Float) q[7]));
            raid(f + 2, h -> lookAt(h.raid().raid, (String) q[0], (Float) q[2], (Float) q[3], (Float) q[4], (Float) q[5], (Float) q[6], (Float) q[7]));
            for (int k = 20; k < 190; k += 20) raid(f + k, h -> lookAt(h.raid().raid, (String) q[0], (Float) q[2], (Float) q[3], (Float) q[4], (Float) q[5], (Float) q[6], (Float) q[7]));
            raid(f + 190, h -> shot(h, (String) q[1]));
            f += 200; n++;
        }
        raid(f + 10, h -> { Raid r = h.raid().raid; for (var lm : r.world.map.anchors) if (lm.id.equals("power_plant") || lm.id.equals("mansion")) { int rooms = 0, boxes = 0; for (int id : lm.buildings) { var bd = r.world.map.buildings.get(id); rooms += bd.rooms.size(); for (var rm : bd.rooms) if (!rm.reachable) System.out.println("[Scenario] UNREACHABLE " + bd.name + " " + rm.type + " floor " + rm.floor + " at " + rm.x0 + "," + rm.z0 + ".." + rm.x1 + "," + rm.z1 + " y" + rm.y); if (!bd.problems.isEmpty()) System.out.println("[Scenario] problems " + bd.name + ": " + bd.problems); } for (var l : r.world.map.loot) if (l.x >= lm.x0 && l.x <= lm.x1 && l.z >= lm.z0 && l.z <= lm.z1) boxes++; System.out.println("[Scenario] " + lm.name + ": " + lm.buildings.size() + " buildings, " + rooms + " rooms, " + boxes + " containers"); } });
    }

    /** Puts the camera at a place on a landmark's own grid (a along its front, b back from it, y above the street), looking at another. */
    private static void lookAt(Raid r, String landmark, float a, float b, float y, float ta, float tb, float ty) {
        for (var lm : r.world.map.anchors) {
            if (!lm.id.equals(landmark)) continue;
            var fr = new absentialis.world.gen.arch.Frame(lm.x0, lm.z0, lm.x1, lm.z1, lm.front);
            float[] from = local(fr, a, b), to = local(fr, ta, tb);
            float dx = to[0] - from[0], dz = to[1] - from[1], dy = ty - y, hl = (float) Math.sqrt(dx * dx + dz * dz);
            place(r, from[0], g() + y - 1.63f, from[1], (float) Math.atan2(-dx, -dz), (float) Math.atan2(dy, Math.max(0.01f, hl)));
            r.player.onGround = true; r.player.fallSpeed = 0;
            return;
        }
    }

    /** A point on a frame's local grid, in the world: the same mapping the generator uses, for whole and part cells alike. */
    private static float[] local(absentialis.world.gen.arch.Frame fr, float a, float b) {
        int ia = (int) Math.floor(a), ib = (int) Math.floor(b);
        float fa = a - ia, fb = b - ib;
        float x00 = fr.x(ia, ib), z00 = fr.z(ia, ib), x10 = fr.x(ia + 1, ib), z10 = fr.z(ia + 1, ib), x01 = fr.x(ia, ib + 1), z01 = fr.z(ia, ib + 1);
        return new float[]{x00 + (x10 - x00) * fa + (x01 - x00) * fb + 0.5f, z00 + (z10 - z00) * fa + (z01 - z00) * fb + 0.5f};
    }

    /**
     * 0.8, Director Mode in the city, end to end: the casting screen on three kinds of actor, into the world as a
     * Gladiator, the interaction menu and its settings, an orbit camera, a four-second recording, the editor with two
     * pieces on its timeline, an export - and out again, with the survivor as they were.
     */
    private static void director() {
        profile(null);
        final int W = org.lwjgl.glfw.GLFW.GLFW_KEY_W;
        raid(40, h -> { h.raid().raid.god = true; h.console("directormode"); });
        raid(70, h -> shot(h, "dir_00_casting"));
        // 0.9: the character creator inside Director Mode
        raid(72, h -> h.raid().director.beginCreate(-1));
        raid(100, h -> shot(h, "dir_01_character_creator"));
        raid(102, h -> { var d = h.raid().director; var app = new absentialis.character.Appearance(); app.sex(true); System.out.println("[Scenario] create: " + d.createCharacter("Vera", app) + " -> " + d.persistence.characters.size() + " character(s), selected " + d.actors.describe(d.chosen)); d.chosen.loadout = 4; d.openCastingPreview(); });
        raid(130, h -> shot(h, "dir_02_casting_own_character"));
        raid(132, h -> { var d = h.raid().director; System.out.println("[Scenario] enter: " + d.session.enterWorld()); d.world.setTime(4); d.world.freeze(true); d.population.setNpc(3); });
        // 0.9: the actor can look and move as soon as the curtain is up (0.8 held the controls until a menu had been opened once)
        raid(200, h -> { Raid r = h.raid().raid; dirFrom[0] = r.player.x; dirFrom[1] = r.player.z; dirFrom[2] = r.player.yaw; System.out.println("[Scenario] controls held after entering: " + h.raid().director.gui.locked()); h.ctx().in.inject(W, true); h.ctx().in.dx = 40; });
        for (int f = 201; f < 250; f++) raid(f, h -> h.ctx().in.dx = 6);
        raid(250, h -> { Raid r = h.raid().raid; h.ctx().in.inject(W, false); System.out.println(String.format("[Scenario] moved %.2f m and turned %.2f rad with the keys and the mouse", r.player.distXZ(dirFrom[0], dirFrom[1]), Math.abs(r.player.yaw - dirFrom[2]))); });
        raid(260, h -> shot(h, "dir_03_world_as_own_character"));
        raid(262, h -> h.raid().director.openMenu());
        raid(290, h -> { shot(h, "dir_04_interaction_menu"); System.out.println("[Scenario] pointer free while the menu is open: " + h.raid().director.wantsPointer()); });
        raid(292, h -> { var d = h.raid().director; d.gui.close(); System.out.println("[Scenario] record: " + d.recording.start()); h.ctx().in.inject(W, true); });
        for (int f = 293; f < 520; f++) raid(f, h -> h.ctx().in.dx = (dirTick++ / 45) % 2 == 0 ? 3 : -3);
        raid(400, h -> shot(h, "dir_05_recording"));
        raid(520, h -> h.ctx().in.inject(W, false));
        raid(560, h -> { var d = h.raid().director; var tape = d.recording.tape; System.out.println("[Scenario] clip: " + d.recording.stop() + " - " + d.recording.status + "; tape " + (tape == null ? 0 : tape.size()) + " frames" + (tape != null && tape.truncated ? " (truncated)" : "")); });
        raid(580, h -> { var d = h.raid().director; d.session.toEditor(); System.out.println("[Scenario] editor: " + d.editor.timeline.size() + " piece(s) on the timeline when it opens, " + absentialis.director.RecordingManager.clips().size() + " clip(s) on disk, tape " + (d.editor.timeline.isEmpty() ? "-" : d.editor.tapeOf(d.editor.timeline.get(0)) != null)); });
        raid(640, h -> shot(h, "dir_06_editor_footage"));
        // 0.9: perspective is chosen here - the same take from an orbiting camera, then from behind the actor
        raid(642, h -> { var e = h.raid().director.editor; if (!e.timeline.isEmpty()) { e.setCamera(e.timeline.get(0), absentialis.director.ClipEditor.CAM_ORBIT); e.seek(2.0f); } });
        raid(680, h -> shot(h, "dir_07_editor_orbit_camera"));
        raid(682, h -> { var e = h.raid().director.editor; if (!e.timeline.isEmpty()) { e.setCamera(e.timeline.get(0), absentialis.director.ClipEditor.CAM_THIRD); e.seek(5.0f); } });
        raid(720, h -> shot(h, "dir_08_editor_third_person_camera"));
        raid(722, h -> { var e = h.raid().director.editor; if (!e.timeline.isEmpty()) { e.setCamera(e.timeline.get(0), absentialis.director.ClipEditor.CAM_ORBIT); e.reshoot(e.timeline.get(0)); } });
        raid(800, h -> shot(h, "dir_09_reshooting"));
        for (int f = 740; f < 2400; f += 20) raid(f, h -> { var e = h.raid().director.editor; if (!e.shooting() && e.reshoots > 0 && !reshootLogged) { reshootLogged = true; var pc = e.timeline.get(0); System.out.println("[Scenario] re-shot: " + pc.clip.name() + " " + pc.clip.frames() + " frames, sound " + pc.clip.audio() + " - " + e.status); } });
        raid(2400, h -> { var e = h.raid().director.editor; if (!e.timeline.isEmpty()) { var a2 = e.timeline.get(0); var b2 = e.add(a2.clip); b2.in = Math.min(1f, b2.out * 0.3f); b2.effect = 2; b2.transition = 1; b2.zoom = 1.5f; b2.panX = 0.3f; b2.speed = 0.5f; e.seek(1.0f); } });
        raid(2440, h -> shot(h, "dir_10_editor_reshot_take"));
        raid(2442, h -> h.raid().director.editor.export());
        for (int f = 2460; f < 6000; f += 20) raid(f, h -> { var e = h.raid().director.editor; if (!e.exported.isEmpty() && !exportLogged) { exportLogged = true; java.io.File out = new java.io.File(e.exported); System.out.println("[Scenario] exported " + out.getName() + " " + out.length() + " bytes - " + e.status); } });
        raid(6000, h -> shot(h, "dir_11_editor_exported"));
        raid(6002, h -> { var d = h.raid().director; d.editor.closeRequested = true; });
        raid(6060, h -> { var d = h.raid().director; System.out.println("[Scenario] end: " + d.toggle()); });
        raid(6120, h -> { Raid r = h.raid().raid; shot(h, "dir_12_back_as_survivor"); System.out.println("[Scenario] restored: kind '" + r.playerKind + "' unwanted " + r.unwanted + " hp " + r.player.hp + "/" + r.player.maxHp + " saves suspended " + absentialis.persistence.SaveSystem.suspended + " look override " + (h.raid().lookOverride != null)); });
    }
    private static final float[] dirFrom = new float[3];
    private static boolean reshootLogged;
    private static int dirTick;
    private static boolean exportLogged;

    /** 0.8: the eighteen new pieces of equipment worn, three to a row - helmet, armour, pack of one rarity on each of six people. */
    private static void gear() {
        profile(null);
        for (int f = 8; f < 3000; f += 30) hideout(f, h -> { if (absentialis.world.gen.DevMap.mansion == null && h.worldReady()) h.console("devmap 0"); });
        float rx = absentialis.world.gen.DevMap.ROAD_X0 + 6, rz = (absentialis.world.gen.DevMap.ROAD_Z0 + absentialis.world.gen.DevMap.ROAD_Z1) / 2f + 0.5f;
        String[][] sets = {{"helmet.hardhat", "armor.scavenged", "bag.school"}, {"helmet.moto", "armor.rider", "bag.hiking"}, {"helmet.tactical", "armor.tactical_rig", "bag.tactical"},
                {"helmet.ballistic", "armor.full_ballistic", "bag.ballistic"}, {"helmet.sealed", "armor.advanced_suit", "bag.advanced"}, {"helmet.smart", "armor.powered", "bag.warbag"}};
        raid(60, h -> {
            Raid r = h.raid().raid;
            r.god = true; r.freezeAI = true;
            place(r, rx, g(), rz, -1.5708f, -0.05f);
            for (int i = 0; i < sets.length; i++) {
                absentialis.entity.Actor a = r.debugSpawn("survivors", rx + 5.5f, rz - 3.75f + i * 1.5f, g());
                a.hostileToPlayer = false; a.weapon = null; a.yaw = 1.5708f + (i < 3 ? 0 : 0);
                a.wornHead = sets[i][0]; a.wornArmor = sets[i][1]; a.wornPack = sets[i][2]; a.look = "gear08:" + i;
                posed[i] = a;
            }
        });
        raid(200, h -> shot(h, "gear_00_front"));
        raid(202, h -> { for (int i = 0; i < 6; i++) if (posed[i] != null) posed[i].yaw = -1.5708f; });
        raid(300, h -> shot(h, "gear_01_back"));
        raid(302, h -> { Raid r = h.raid().raid; place(r, rx + 3.2f, g(), rz - 3.2f, -1.5708f, -0.05f); for (int i = 0; i < 6; i++) if (posed[i] != null) posed[i].yaw = 1.5708f; });
        raid(400, h -> shot(h, "gear_02_close_left"));
        raid(402, h -> { Raid r = h.raid().raid; place(r, rx + 3.2f, g(), rz + 1.4f, -1.5708f, -0.05f); });
        raid(500, h -> shot(h, "gear_03_close_right"));
    }

    private static final absentialis.entity.Actor[] posed = new absentialis.entity.Actor[8];

    /**
     * 0.8, the poses nobody had looked at from outside: a row of people side-on to the camera on a bare floor - standing,
     * crouched, prone with a rifle, prone with nothing, down wounded - and then the same row from the front. A line is
     * drawn at floor level: nothing of anybody should be under it.
     */
    private static void poses() {
        profile(null);
        raid(20, h -> {
            Raid r = h.raid().raid;
            r.god = true; r.populate = false; r.freezeAI = true;
            r.actors.removeIf(a -> !a.isPlayer);
            absentialis.world.World w = r.world;
            gx = (int) r.player.x; gy = Math.round(r.player.y); gz = (int) r.player.z;
            for (int dx = -12; dx <= 12; dx++) for (int dz = -14; dz <= 4; dz++) { for (int dy = 0; dy <= 5; dy++) w.set(gx + dx, gy + dy, gz + dz, absentialis.world.Block.AIR); w.set(gx + dx, gy - 1, gz + dz, absentialis.world.Block.CONCRETE); }
            String[] who = {"military", "military", "military", "survivors", "military", "raiders"};
            for (int i = 0; i < who.length; i++) {
                absentialis.entity.Actor a = r.debugSpawn(who[i], gx - 6.25f + i * 2.5f + 0.5f, gz - 5 + 0.5f, gy);
                a.yaw = (float) -Math.PI / 2; a.hostileToPlayer = false; a.homeX = a.x; a.homeZ = a.z;      // facing +x: side-on to the camera
                posed[i] = a;
            }
            posed[1].crouch = true;
            posed[2].prone = true;
            posed[3].prone = true; posed[3].weapon = null;
            posed[4].vital = absentialis.entity.Vital.DOWNED; posed[4].bleedLeft = 9999;
            posed[5].prone = true; posed[5].yaw = (float) Math.PI;                                          // prone, feet to the camera... then head to it
            h.raid().thirdPerson = false;
            place(r, gx + 1.0f, gy, gz + 3.5f, 0, -0.10f);
        });
        raid(60, h -> { for (absentialis.entity.Actor a : posed) if (a != null) { a.vx = a.vz = 0; } });
        raid(170, h -> shot(h, "poses_00_side"));
        raid(172, h -> place(h.raid().raid, gx + 1.5f, gy, gz - 0.5f, 0, -0.22f));
        raid(260, h -> shot(h, "poses_01_prone_close"));
        raid(262, h -> place(h.raid().raid, gx + 12.5f, gy, gz - 4.5f, (float) Math.PI / 2, -0.12f));
        raid(350, h -> shot(h, "poses_02_from_the_end"));
    }

    private static int gx, gy, gz;

    /**
     * 0.8, "glass textures freak out when looking at glass through glass": three rows of different glass one behind the
     * other, straddling a chunk seam, in front of a plain wall - looked through from in front, walked past sideways (the
     * order the panes are drawn in changes as the camera crosses the seam) and looked along at a slant.
     */
    private static void glass() {
        profile(null);
        raid(20, h -> {
            Raid r = h.raid().raid;
            r.god = true; r.populate = false; r.freezeAI = true;
            r.actors.removeIf(a -> !a.isPlayer);
            absentialis.world.World w = r.world;
            gx = Math.round(r.player.x / 16f) * 16; gy = Math.round(r.player.y); gz = (int) r.player.z;
            for (int dx = -9; dx <= 9; dx++) for (int dz = -15; dz <= 3; dz++) { for (int dy = 0; dy <= 5; dy++) w.set(gx + dx, gy + dy, gz + dz, absentialis.world.Block.AIR); w.set(gx + dx, gy - 1, gz + dz, absentialis.world.Block.CONCRETE); }
            int[] kinds = {absentialis.world.Block.GLASS, absentialis.world.Block.GLASS_DIRTY, absentialis.world.Block.GLASS_CRACKED};
            for (int k = 0; k < 3; k++) for (int dx = -6; dx <= 6; dx++) for (int dy = 0; dy <= 3; dy++) w.set(gx + dx, gy + dy, gz - 4 - k * 3, kinds[k]);
            for (int dx = -9; dx <= 9; dx++) for (int dy = 0; dy <= 5; dy++) w.set(gx + dx, gy + dy, gz - 14, (dx + dy & 1) == 0 ? absentialis.world.Block.CONCRETE : absentialis.world.Block.SANDBAG);
            h.raid().thirdPerson = false;
            place(r, gx + 0.5f, gy, gz + 0.5f, 0, 0.02f);
        });
        raid(200, h -> shot(h, "glass_00_front"));
        for (int i = 0; i < 12; i++) { final int k = i; raid(204 + i * 6, h -> place(h.raid().raid, gx - 5.5f + k * 1.0f, gy, gz + 0.5f, 0.35f - k * 0.06f, 0.02f)); raid(208 + i * 6, h -> shot(h, String.format("glass_walk_%03d", k))); }
        raid(280, h -> sheet("glass_walk", 4, 300, 150, 1000, 600, 0.4f));
        raid(284, h -> place(h.raid().raid, gx - 7.5f, gy, gz - 2.5f, -1.15f, 0.0f));
        raid(380, h -> shot(h, "glass_01_slant"));
    }

    /** Every tab of the hideout, the shop departments and the deployment briefing. */
    private static void menus() {
        keepUnaligned = true;
        profile(p -> { p.scrip = 90000; p.prog.addXp(absentialis.gameplay.progression.Progression.xpToReach(9)); });
        hideout(5, h -> shot(h, "menu_00_allegiance"));
        hideout(7, h -> { absentialis.gameplay.Alliance.choose(h.ctx().profile, "survivors"); h.hideout().enter(); });
        int f = 12;
        for (int t = 0; t < HideoutScreen.TABS.length; t++) {
            final int tab = t;
            hideout(f, h -> h.hideout().tab = tab);
            hideout(f + 10, h -> shot(h, "menu_1" + tab + "_" + HideoutScreen.TABS[tab].toLowerCase()));
            f += 12;
        }
        for (int d = 0; d <= ShopPanel.RECRUITS; d++) {
            final int dept = d;
            hideout(f, h -> h.hideout().debugShow(dept, false));
            hideout(f + 10, h -> shot(h, "menu_2" + dept + "_shop"));
            f += 12;
        }
        hideout(f, h -> h.hideout().debugShow(-1, true));
        hideout(f + 10, h -> shot(h, "menu_30_briefing"));
        hideout(f + 12, h -> { h.hideout().debugShow(-1, false); h.hideout().unlocks.show(1, 9); });
        hideout(f + 70, h -> shot(h, "menu_31_unlocked"));
        hideout(f + 72, h -> h.hideout().unlocks.dismiss());
    }
}
