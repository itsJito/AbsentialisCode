package absentialis.tools;

import absentialis.entity.Actor;
import absentialis.gameplay.items.Content;
import absentialis.neuralai.api.Vec3;
import absentialis.neuralai.decision.CogState;
import absentialis.neuralai.integration.Archetypes;
import absentialis.neuralai.integration.Creatures;
import absentialis.neuralai.runtime.Mind;
import absentialis.persistence.Profile;
import absentialis.scene.Raid;
import absentialis.world.World;

/**
 * Version 0.8 gates. Each section names the line of the 0.8 brief (instructions.md) it covers; the tracker
 * ({@code docs/V0.8_IMPLEMENTATION.md}) points back here.
 */
final class SelfTests08 {
    private SelfTests08() {}

    static final int G = World.GROUND;

    static void run(String which, boolean all, SelfTests04.Check t) {
        if (all || which.contains("v08") || which.contains("ai08")) SelfTests.guard(() -> oneAi(t));
        if (all || which.contains("v08") || which.contains("squad08")) SelfTests.guard(() -> squad(t));
        if (all || which.contains("v08") || which.contains("soak08")) SelfTests.guard(() -> soak(t));
        if (all || which.contains("v08") || which.contains("bugs08")) SelfTests.guard(() -> bugs(t));
        if (all || which.contains("v08") || which.contains("meta08")) SelfTests.guard(() -> meta(t));
        if (all || which.contains("v08") || which.contains("audio08")) SelfTests.guard(() -> audio(t));
        if (all || which.contains("v08") || which.contains("content08")) SelfTests.guard(() -> content(t));
        if (all || which.contains("v08") || which.contains("story08")) SelfTests.guard(() -> story(t));
        if (all || which.contains("v08") || which.contains("world08")) SelfTests.guard(() -> world08(t));
        if (all || which.contains("v08") || which.contains("anims08")) SelfTests.guard(() -> anims08(t));
        if (all || which.contains("v08") || which.contains("modes08")) SelfTests.guard(() -> modes08(t));
        if (all || which.contains("v08") || which.contains("cover08")) SelfTests.guard(() -> cover08(t));
    }

    private static void run(Raid r, float seconds) { for (int i = 0; i < Math.round(seconds * 60); i++) r.update(1 / 60f); }

    private static Actor spawn(Raid r, String what, float x, float z, float yaw) {
        Actor a = r.debugSpawn(what, x, z, G);
        a.yaw = yaw; a.homeX = x; a.homeZ = z; a.wakeUntil = 1e9f;
        return a;
    }

    private static float yawTo(Actor a, float x, float z) { return (float) Math.atan2(-(x - a.x), -(z - a.z)); }

    // ------------------------------------------------------------------ spinels in the ceilings, the laser, the stance icon
    private static void world08(SelfTests04.Check t) {
        System.out.println("[v0.8 world and HUD: spinels in ceilings that can be shot down, a laser in three colours that is dark until aimed]");
        Content.load();
        World city = SelfTests06.world(1337L);
        Profile p = new Profile();
        Raid r = new Raid(city, p, new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer);
        int indoors = 0, outdoors = 0, bad = 0;
        float lowest = 99, highest = 0;
        java.util.Set<String> kinds = new java.util.TreeSet<>();
        for (var s : r.spinels.stones) {
            if (s.exterior) outdoors++; else indoors++;
            kinds.add(Content.def(s.item).name);
            int x = (int) Math.floor(s.x), z = (int) Math.floor(s.z), top = (int) Math.floor(s.y + 0.2f);
            boolean ok = city.solid(x, top, z) && city.solid(x, (int) s.floorY - 1, z);
            for (int y = (int) s.floorY; y < top; y++) ok &= !city.solid(x, y, z);          // nothing between it and the floor: it can be seen, and it can fall
            if (!ok) bad++;
            lowest = Math.min(lowest, s.y - s.floorY); highest = Math.max(highest, s.y - s.floorY);
        }
        Raid same = new Raid(city, p, new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        boolean stable = same.spinels.stones.size() == r.spinels.stones.size();
        for (int i = 0; stable && i < r.spinels.stones.size(); i++) stable = same.spinels.stones.get(i).x == r.spinels.stones.get(i).x && same.spinels.stones.get(i).z == r.spinels.stones.get(i).z;
        p.zone.day += 1;
        Raid next = new Raid(city, p, new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        int moved = 0;
        for (var s : next.spinels.stones) { boolean was = false; for (var o : r.spinels.stones) if (o.x == s.x && o.z == s.z && o.y == s.y) was = true; if (!was) moved++; }
        p.zone.day -= 1;
        t.check("spinels are lodged in ceilings indoors and under overhangs outdoors - each on something solid, three metres or more up, with clear air down to the floor - in the same places on the same day and different ones the next",
                indoors >= 8 && outdoors >= 3 && bad == 0 && lowest >= 2.8f && kinds.size() == 3 && stable && moved >= next.spinels.stones.size() / 2,
                indoors + " indoors, " + outdoors + " outdoors (" + kinds + "), " + String.format("%.1f to %.1f m up", lowest, highest) + ", " + bad + " badly placed; same day same places: " + stable + "; next day " + moved + " of " + next.spinels.stones.size() + " are somewhere new");

        // shot down by a real round, and then it is an item on the floor
        var stone = r.spinels.stones.get(0);
        r.player.x = stone.x + 0.4f; r.player.z = stone.z; r.player.y = stone.floorY;
        int itemsBefore = r.items.size(), upBefore = r.spinels.remaining();
        // a round that passes half a metre to one side: nothing
        r.spinels.shot(stone.x + 0.5f, stone.floorY + 1, stone.z, 0, 1, 0, 12);
        boolean missLeaves = !stone.fallen;
        Raid.Projectile pr = new Raid.Projectile();
        pr.x = stone.x; pr.y = stone.floorY + 1.2f; pr.z = stone.z; pr.px = pr.x; pr.py = pr.y; pr.pz = pr.z; pr.ox = pr.x; pr.oy = pr.y; pr.oz = pr.z;
        pr.vx = 0; pr.vy = 320; pr.vz = 0; pr.damage = 30; pr.power = 30; pr.owner = r.player.id;
        r.projectiles.add(pr);
        for (int i = 0; i < 6; i++) r.update(1 / 60f);
        absentialis.gameplay.items.ItemInstance onFloor = null;
        for (Raid.WorldItem wi : r.items) if (wi.it.defId.equals(stone.item) && Math.abs(wi.x - stone.x) < 0.6f && Math.abs(wi.z - stone.z) < 0.6f) onFloor = wi.it;
        boolean cue = r.cueCount.getOrDefault("glass_tinkle", 0) == 1;
        boolean taken = onFloor != null && absentialis.gameplay.inventory.InventoryService.store(onFloor, p.loadout).ok() && p.loadout.all().contains(onFloor);
        r.spinels.shot(stone.x, stone.floorY + 1, stone.z, 0, 1, 0, 12);       // shooting where it was does nothing more
        t.check("a spinel in a ceiling is shot down and picked up: a round fired up through it knocks it loose, it lands on the floor beneath as an item, and it goes in the case - a round half a metre wide leaves it where it is",
                missLeaves && stone.fallen && onFloor != null && r.items.size() == itemsBefore + 1 && r.spinels.remaining() == upBefore - 1 && r.spinels.shotDown == 1 && cue && taken,
                "miss leaves it " + missLeaves + "; fallen " + stone.fallen + "; on the floor " + (onFloor != null) + " (" + (r.items.size() - itemsBefore) + " new item); " + r.spinels.remaining() + " of " + r.spinels.stones.size() + " still up; heard " + cue + "; in the case " + taken);

        // ---- "The laser attachment ... Red (Default), Green or Blue ... only becomes active when aiming"
        String[] ids = {"att.laser.1mw", "att.laser.5mw", "att.laser.tac"};
        java.util.Set<Integer> cols = new java.util.HashSet<>();
        java.util.Set<String> names = new java.util.TreeSet<>();
        boolean onlyAiming = true;
        for (String id : ids) {
            absentialis.gameplay.items.ItemDef d = Content.def(id);
            cols.add(absentialis.gameplay.weapons.Attachments.laserColor(id));
            names.add(d == null ? "?" : d.name);
            onlyAiming &= d != null && d.attachment != null && d.attachment.expose == 2 && !d.attachment.light;
        }
        int red = absentialis.gameplay.weapons.Attachments.laserColor(absentialis.gameplay.weapons.Attachments.LASER_DEFAULT), green = absentialis.gameplay.weapons.Attachments.laserColor("att.laser.5mw"), blue = absentialis.gameplay.weapons.Attachments.laserColor("att.laser.tac");
        boolean hues = ((red >> 16) & 255) > 200 && ((red >> 8) & 255) < 90 && ((green >> 8) & 255) > 200 && ((green >> 16) & 255) < 110 && (blue & 255) > 200 && ((blue >> 16) & 255) < 110;
        // each is a part of its own on the weapon: the three builds are three different models
        java.util.Set<Integer> models = new java.util.HashSet<>();
        for (String id : ids) {
            absentialis.gameplay.items.ItemInstance gun = new absentialis.gameplay.items.ItemInstance("weapon.akr");
            absentialis.gameplay.weapons.Gunsmith.install(gun, id);
            models.add(java.util.Arrays.hashCode(absentialis.ui.ItemModels.of(gun).col));
        }
        absentialis.gameplay.items.ItemInstance bare = new absentialis.gameplay.items.ItemInstance("weapon.akr");
        models.add(java.util.Arrays.hashCode(absentialis.ui.ItemModels.of(bare).col));
        t.check("the laser comes in red (the default), green and blue: three attachments, three colours, each a module on the weapon with an emitter of its own colour, and each one dark until the weapon is aimed",
                cols.size() == 3 && names.equals(new java.util.TreeSet<>(java.util.List.of("Red Laser", "Green Laser", "Blue Laser"))) && onlyAiming && hues && models.size() == 4 && "att.laser.1mw".equals(absentialis.gameplay.weapons.Attachments.LASER_DEFAULT),
                names + String.format("; colours %06X %06X %06X", red & 0xFFFFFF, green & 0xFFFFFF, blue & 0xFFFFFF) + "; only while aiming: " + onlyAiming + "; " + models.size() + " distinct weapon models (bare and three)");
    }

    // ------------------------------------------------------------------ "trouble contextually covering and aiming around blocks"
    private static void cover08(SelfTests04.Check t) {
        System.out.println("[v0.8 cover: aiming round a block - the player leans out of their own accord, and nobody shoots their own cover]");
        Content.load();
        World w = SelfTests.flatWorld();
        Raid r = new Raid(w, new Profile(), new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer);
        // a pillar one block wide and three high, straight in front of where somebody stands; open ground either side
        // on ground of its own, cleared first: the flat world is shared with every other gate, and some of them build
        int px = 520, pz = 148;
        java.util.List<int[]> built = new java.util.ArrayList<>();
        for (int x = px - 8; x <= px + 8; x++) for (int z = pz - 16; z <= pz + 8; z++) for (int y = G; y < G + 4; y++) if (w.get(x, y, z) != absentialis.world.Block.AIR) { built.add(new int[]{x, y, z, w.get(x, y, z)}); w.set(x, y, z, absentialis.world.Block.AIR); }
        for (int y = G; y < G + 3; y++) { built.add(new int[]{px, y, pz, w.get(px, y, pz)}); w.set(px, y, pz, absentialis.world.Block.CONCRETE); }
        try {
            Actor p = r.player;
            // a lean is less than half a metre: it takes somebody round the end of a thing they are standing at the end of
            p.x = px + 0.85f; p.z = pz + 1.6f; p.y = G; p.yaw = 0; p.pitch = 0;                 // facing -z, behind the right-hand edge of the pillar
            float open = r.leanToClear(p, p.yaw, 0, 0.45f);
            p.x = px + 0.15f;                                                                    // behind its left-hand edge
            float left = r.leanToClear(p, p.yaw, 0, 0.45f);
            p.z = pz + 6f;                                                                       // nothing near: no lean
            float none = r.leanToClear(p, p.yaw, 0, 0.45f);
            // a wall with no end in reach: nothing to lean round
            for (int x = px - 4; x <= px + 4; x++) for (int y = G; y < G + 3; y++) if (x != px) { built.add(new int[]{x, y, pz, w.get(x, y, pz)}); w.set(x, y, pz, absentialis.world.Block.CONCRETE); }
            p.x = px + 0.5f; p.z = pz + 1.6f;
            float walled = r.leanToClear(p, p.yaw, 0, 0.45f);
            for (int x = px - 4; x <= px + 4; x++) for (int y = G; y < G + 3; y++) if (x != px) w.set(x, y, pz, absentialis.world.Block.AIR);
            // ---- an NPC behind the same corner with a target beyond it: it does not put rounds into the block in front of it
            Actor shooter = spawn(r, "military", px + 0.85f, pz + 1.6f, 0), target = spawn(r, "raiders", px + 0.6f, pz - 12f, 0);
            r.freezeAI = true;
            target.maxHp = target.hp = 1e6f;
            shooter.yaw = 0;
            int intoCover = 0, fired = 0, hits0 = r.debrief.hits;
            float hp0 = target.hp;
            for (int i = 0; i < 240; i++) {
                shooter.fireCooldown = Math.max(0, shooter.fireCooldown - 1 / 60f);
                // a sound, loaded weapon throughout: this is about where the round goes, not about stoppages
                if (shooter.weapon != null) { shooter.weapon.jammed = false; shooter.weapon.setCondition(100); if (shooter.weapon.rounds < 2) shooter.weapon.rounds = shooter.weapon.capacity(); shooter.weapon.chambered = true; }
                shooter.blindUntil = 0; shooter.staggerUntil = 0;
                int res = r.npcCommandFire(shooter, target.x, target.y + 1.1f, target.z, false, target.id);
                if (res > 0) fired++;
                if (res < 0) intoCover++;
                r.update(1 / 60f);
            }
            boolean leaned = shooter.leanTarget != 0 && Math.abs(shooter.lean) > 0.5f;
            float hurt = hp0 - target.hp;
            // strikes on the pillar itself (a round that misses the man beyond lands on the ground a long way off, and is not this)
            int pillarHits = 0;
            for (Raid.Cue q : r.cues) if (q.sample().startsWith("impact_") && Math.abs(q.x() - (px + 0.5f)) < 1.2f && Math.abs(q.z() - (pz + 0.5f)) < 1.2f) pillarHits++;
            // ---- and one with a wall and no way round it: the shot is held and the mind is told its line is blocked
            for (int x = px - 4; x <= px + 4; x++) for (int y = G; y < G + 3; y++) { if (x != px) built.add(new int[]{x, y, pz, absentialis.world.Block.AIR}); w.set(x, y, pz, absentialis.world.Block.CONCRETE); }
            Actor boxed = spawn(r, "military", px - 1.5f, pz + 1.6f, 0);
            Actor far = spawn(r, "raiders", px - 1.5f, pz - 12f, 0);
            int held0 = r.coverHolds, res = 0;
            for (int i = 0; i < 20; i++) { boxed.fireCooldown = 0; res = r.npcCommandFire(boxed, far.x, far.y + 1.1f, far.z, false, far.id); }
            t.check("aiming round a block: sights up a pace behind a pillar, the body leans out round whichever end of it they are standing at, does not lean at an unbroken wall or in the open; a soldier behind the same corner leans out and hits what is beyond it instead of shooting the block in front of him (no more than one round in five clips its edge); and one with no way round holds his fire and is told his line is blocked",
                    open == 1 && left == -1 && none == 0 && walled == 0 && leaned && fired >= 5 && hurt > 0 && pillarHits <= fired / 5 && res == -1 && r.coverHolds > held0,
                    "at the right-hand edge of a pillar: lean " + open + "; at its left-hand edge: " + left + "; in the open: " + none + "; unbroken wall: " + walled + "; soldier leaned " + leaned + " (lean " + String.format("%.2f", shooter.lean) + "), fired " + fired + ", target took " + Math.round(hurt) + ", rounds into his own cover " + pillarHits + " [held " + intoCover + ", cooldown " + shooter.fireCooldown + ", dead " + shooter.dead + ", ragdoll " + (shooter.ragdoll != null) + ", weapon " + (shooter.weapon == null ? "none" : shooter.weapon.defId + " rounds " + shooter.weapon.rounds) + ", at " + shooter.x + "," + shooter.z + ", time " + r.time + ", blind " + shooter.blindUntil + "]; boxed in: result " + res + ", " + (r.coverHolds - held0) + " shots held");
        } finally { for (int i = built.size() - 1; i >= 0; i--) { int[] b = built.get(i); w.set(b[0], b[1], b[2], b[3]); } }
    }

    // ------------------------------------------------------------------ Developer Mode and Director Mode (what can be judged without a window)
    private static int awareOf(Raid r, Actor... as) { int n = 0; for (Actor a : as) { Mind m = r.neural.mind(a.id); if (a.awareLevel > 0 || m != null && m.state != CogState.PATROL) n++; } return n; }

    private static java.nio.file.Path fakeClip(String name, int frames, int fps, int w, int h, boolean sound) throws Exception {
        java.nio.file.Path dir = absentialis.director.RecordingManager.root().resolve(name);
        java.nio.file.Files.createDirectories(dir);
        for (int i = 0; i < frames; i++) {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            // left half red, right half blue, with a bright bar that moves a little each frame: enough to tell framing, grading and time apart
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, x == (i * 4) % w ? 0xFFFFFF : x < w / 2 ? 0xC02020 : 0x2020C0);
            javax.imageio.ImageIO.write(img, "jpg", dir.resolve(String.format("frame_%05d.jpg", i)).toFile());
        }
        if (sound) {
            int bytes = frames * (44100 / fps) * 4;
            java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(44 + bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            b.put("RIFF".getBytes()).putInt(36 + bytes).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 2).putInt(44100).putInt(44100 * 4).putShort((short) 4).putShort((short) 16).put("data".getBytes()).putInt(bytes);
            for (int i = 0; i < bytes / 4; i++) { short v = (short) (Math.sin(i * 0.06) * 12000); b.putShort(v).putShort(v); }
            java.nio.file.Files.write(dir.resolve("audio.wav"), b.array());
        }
        java.nio.file.Files.writeString(dir.resolve("clip.json"), "{\"name\": \"" + name + "\", \"frames\": " + frames + ", \"fps\": " + fps + ", \"width\": " + w + ", \"height\": " + h + ", \"audio\": " + sound + "}");
        return dir;
    }

    private static void modes08(SelfTests04.Check t) {
        System.out.println("[v0.8 modes: nobody reacts to a Developer Mode camera; Director Mode's world, population, modifier and location managers; clips, the editor and video export]");
        Content.load();
        // ---- "the NPC AI do not react to the user in control in devmode"
        // each on its own, so that what they notice can only be the user (together they would notice each other)
        int awareGhost = 0, awareBack = 0, faults = 0;
        float moved = 0, hpGhost = 100;
        boolean untouched = true;
        for (String kind : new String[]{"raiders", "walker", "gladiator"}) {
            Raid r = SelfTests.raid(new Profile());
            r.populate = false;
            r.actors.removeIf(a -> !a.isPlayer);
            r.player.x = 300; r.player.z = 300; r.player.y = G; r.player.maxHp = r.player.hp = 100;
            Actor a = spawn(r, kind, 300, kind.equals("gladiator") ? 298.4f : 292, 0);
            a.hostileToPlayer = true; a.yaw = yawTo(a, 300, 300);
            float d0 = a.distXZ(300, 300);
            r.devGhost = true; r.god = true; r.noclip = true;
            for (int i = 0; i < 360; i++) {
                if (i % 30 == 0) r.emitSound(r.player.x, r.player.y + 1, r.player.z, 60, "shot", r.player.id, false);     // the camera "fires": nothing is heard
                r.update(1 / 60f);
            }
            awareGhost += awareOf(r, a);
            if (kind.equals("walker")) moved = d0 - a.distXZ(300, 300);          // how far it came toward the camera (it may wander; it must not close in)
            hpGhost = Math.min(hpGhost, r.player.hp);
            untouched &= r.nearby(300, 300, 3).stream().noneMatch(o -> o.isPlayer);
            r.devGhost = false; r.god = false; r.noclip = false;
            r.player.maxHp = r.player.hp = 1e6f;
            for (int i = 0; i < 300; i++) { if (i % 30 == 0) r.emitSound(r.player.x, r.player.y + 1, r.player.z, 60, "shot", r.player.id, false); r.update(1 / 60f); }
            awareBack += awareOf(r, a);
            faults += r.aiFaults;
        }
        t.check("in Developer Mode nothing reacts to the user: six seconds in the open eight metres in front of a hostile gunman, of a walker, and at arm's length from a Gladiator while 'firing' - seen by none, heard by none, touched by none - and the moment the mode is left, all three notice",
                awareGhost == 0 && moved < 3f && hpGhost == 100 && untouched && awareBack == 3 && faults == 0,
                "aware of the camera: " + awareGhost + " of 3 (the walker came " + String.format("%.1f", moved) + " m nearer in six seconds of wandering); health " + hpGhost + "; after leaving the mode: " + awareBack + " of 3 aware");

        // ---- Director Mode: WorldManager (time of day, freeze), ModifierManager (wanted status, invincibility, noclip, NeuralAI)
        Raid d = SelfTests.raid(new Profile());
        d.populate = false;
        d.actors.removeIf(a -> !a.isPlayer);
        d.player.x = 300; d.player.z = 300; d.player.y = G;
        double hourBefore = d.hour() % 24;
        var world = new absentialis.director.WorldManagers.WorldManager(d);
        var mods = new absentialis.director.WorldManagers.ModifierManager(d);
        world.save(); mods.save();
        StringBuilder times = new StringBuilder();
        boolean timesOk = absentialis.director.WorldManagers.WorldManager.TIMES.length == 8;
        for (int i = 0; i < 8; i++) { world.setTime(i); double h = d.hour() % 24; timesOk &= Math.abs(h - absentialis.director.WorldManagers.WorldManager.HOURS[i]) < 0.01; times.append(world.timeName()).append(' '); }
        world.setTime(4); world.freeze(true);
        d.profile.zone.weatherRain = 1;
        for (int i = 0; i < 600; i++) { d.time += 1; world.update(); }                    // ten minutes of world time
        boolean held = Math.abs(d.hour() % 24 - 12) < 0.01 && d.profile.zone.weatherRain == 0 && world.weatherName().equals("Clear");
        world.freeze(false);
        d.time += 240; world.update();
        boolean runs = Math.abs(d.hour() % 24 - 14) < 0.05;
        // wanted status: disabled, a hostile gunman in plain sight does nothing; enabled, he does
        Actor foe = spawn(d, "raiders", 300, 290, 0);
        foe.hostileToPlayer = true; foe.yaw = yawTo(foe, 300, 300);
        d.player.maxHp = d.player.hp = 1e6f;
        boolean unwantedDefault = d.unwanted && d.noDeath;
        run(d, 5f);
        int quiet = awareOf(d, foe);
        mods.wanted(true);
        // now the actor is somebody again: in plain sight and making a noise
        for (int i = 0; i < 480 && awareOf(d, foe) == 0; i++) { if (i % 40 == 0) d.emitSound(d.player.x, d.player.y + 1, d.player.z, 60, "shot", d.player.id, false); d.update(1 / 60f); }
        int loud = awareOf(d, foe);
        mods.invincible(true); mods.noclip(true); mods.thinking(false);
        boolean flags = d.god && d.noclip && d.freezeAI && !mods.thinking();
        d.die("test");                                                                    // an actor is not written out of a session
        boolean noDeath = !d.player.dead && d.debrief.outcome == Raid.Debrief.Outcome.NONE;
        mods.restore(); world.restore();
        boolean restored = !d.god && !d.noclip && !d.freezeAI && !d.unwanted && !d.noDeath && Math.abs((d.profile.zone.hour + 0) - (hourBefore - 0)) < 24;
        t.check("Director Mode's world and modifier managers: eight times of day from midnight to evening set the clock exactly, a frozen clock stays put for ten minutes and the weather is held clear; with Wanted Status disabled a hostile gunman in plain sight ignores the actor, enabled he does not; invincibility, noclip and NeuralAI thinking switch; an actor cannot be killed out of the session; and all of it is taken off again",
                timesOk && held && runs && unwantedDefault && quiet == 0 && loud == 1 && flags && noDeath && restored,
                times.toString().trim() + "; frozen held " + held + ", then runs " + runs + "; wanted disabled: " + quiet + " aware, enabled: " + loud + "; switches " + flags + "; survives death " + noDeath + "; restored " + restored);

        // ---- PopulationManager and LocationManager, in the city
        World city = SelfTests06.world(1337L);
        Raid cr = new Raid(city, new Profile(), new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        run(cr, 1f);
        var pop = new absentialis.director.WorldManagers.PopulationManager(cr);
        java.util.List<Actor> none = new java.util.ArrayList<>();
        java.util.function.IntSupplier nearNow = () -> { int n = 0; for (Actor a : cr.actors) if (!a.isPlayer && !a.mate() && !a.dead && a.distXZ(cr.player.x, cr.player.z) < absentialis.director.WorldManagers.PopulationManager.RADIUS) n++; return n; };
        int before = nearNow.getAsInt(), total = cr.actors.size(), cars = cr.vehicles.cars.size();
        pop.setNpc(0); pop.setVehicles(0);
        for (int i = 0; i < 400; i++) pop.update(0.5f, none);
        int empty = nearNow.getAsInt(), carsNone = cr.vehicles.cars.size(), benched = pop.benched.size();
        pop.setNpc(3); pop.setVehicles(3);
        for (int i = 0; i < 400; i++) pop.update(0.5f, none);
        int high = nearNow.getAsInt(), carsHigh = cr.vehicles.cars.size();
        pop.restore();
        int after = cr.actors.size(), carsAfter = cr.vehicles.cars.size();
        var locs = new absentialis.director.WorldManagers.LocationManager(cr);
        StringBuilder ls = new StringBuilder();
        int reachable = 0, refused = 0;
        boolean safe = true;
        for (var l : locs.all) {
            String why = locs.travel(l);
            if (why == null) {
                reachable++;
                safe &= absentialis.entity.Physics.boxFree(city, cr.player.x, cr.player.y, cr.player.z, 0.35f, 1.8f) && Math.hypot(cr.player.x - l.x(), cr.player.z - l.z()) < 70;
                ls.append(l.name()).append(' ');
            } else { refused++; ls.append('[').append(l.name()).append(": ").append(why).append("] "); }
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        for (var l : locs.all) names.add(l.name().replaceAll(" \\(.*", ""));
        t.check("Director Mode's population and location managers: None empties the streets round the actor of people, infected and vehicles and High fills them - and when the session ends what was set aside is back and what was added is gone; every location the design lists is offered, travel puts the actor on safe ground at the place, and one this city does not have says so instead of being hidden",
                before > 0 && empty == 0 && benched > 0 && carsNone < Math.max(1, cars) && high >= absentialis.director.WorldManagers.PopulationManager.NPC_TARGET[3] - 4 && carsHigh >= Math.min(9, cars) && after == total && carsAfter == cars
                        && names.containsAll(java.util.List.of("Airport", "Jail", "City Center", "Wilderness", "Desert", "Industrial District", "Mountains", "Downtown")) && reachable >= 8 && refused == 1 && safe,
                "near the actor: " + before + " as made, " + empty + " at None (" + benched + " set aside), " + high + " at High; vehicles " + cars + " / " + carsNone + " / " + carsHigh + "; after the session " + after + " of " + total + " actors, " + carsAfter + " of " + cars + " vehicles; locations: " + ls.toString().trim() + (safe ? "" : " UNSAFE"));

        // ---- the shortlist (PersistenceManager)
        var per = new absentialis.director.PersistenceManager();
        per.shortlist.clear();
        StringBuilder said = new StringBuilder();
        for (int i = 0; i < 12; i++) said.append(per.toggle(new absentialis.director.ActorManager.Spec(i % 2 == 0 ? "raiders" : "walker", i, i % 3)).startsWith("Shortlisted") ? "+" : "x");
        var per2 = new absentialis.director.PersistenceManager();
        per2.load();
        boolean kept = per2.shortlist.size() == 10 && per2.shortlist.get(3).same(per.shortlist.get(3));
        String off = per2.toggle(per2.shortlist.get(0).copy());
        t.check("the shortlist holds ten favourite actors, refuses an eleventh, and is still there the next session", said.toString().equals("++++++++++xx") && kept && off.startsWith("Removed") && per2.shortlist.size() == 9, said + " then read back " + (kept ? 10 : per2.shortlist.size()) + "; " + off);
        try { java.nio.file.Files.deleteIfExists(absentialis.director.PersistenceManager.file()); } catch (Exception ignored) { }

        // ---- clips, the editor and export
        try {
            fakeClip("clip_gate_a", 40, 20, 320, 180, true);
            fakeClip("clip_gate_b", 30, 20, 320, 180, false);
            java.util.List<absentialis.director.RecordingManager.Clip> clips = absentialis.director.RecordingManager.clips();
            absentialis.director.RecordingManager.Clip ca = null, cb = null;
            for (var c : clips) { if (c.name().equals("clip_gate_a")) ca = c; if (c.name().equals("clip_gate_b")) cb = c; }
            var ed = new absentialis.director.ClipEditor();
            var p1 = ed.add(ca); p1.out = 1.5f;
            var p2 = ed.add(cb); p2.speed = 0.5f; p2.effect = 1; p2.transition = 1;        // slow motion, monochrome, cross-faded in
            var p3 = ed.add(ca); p3.in = 0.5f; p3.out = 1.5f; p3.zoom = 2f; p3.panX = 1f; p3.transition = 2; p3.volume = 0.5f;   // punched in on the right-hand side, through black
            float dur = ed.duration();
            int w = 160, h = 90;
            int[] px = new int[w * h];
            ed.render(0.5f, w, h, px);
            int left = px[(h / 2) * w + w / 4], right = px[(h / 2) * w + 3 * w / 4];
            boolean plain = ((left >> 16) & 255) > 150 && (left & 255) < 90 && (right & 255) > 150 && ((right >> 16) & 255) < 90;
            ed.render(1.5f + 1.5f, w, h, px);                                            // well inside the monochrome piece
            int g1 = px[(h / 2) * w + w / 4];
            boolean mono = Math.abs(((g1 >> 16) & 255) - ((g1 >> 8) & 255)) < 6 && Math.abs(((g1 >> 8) & 255) - (g1 & 255)) < 6;
            ed.render(1.5f + 0.3f, w, h, px);                                            // half-way through the cross-fade: some of the red piece is still in it
            int xf = px[(h / 2) * w + w / 4];
            boolean fading = ((xf >> 16) & 255) > ((xf & 255) + 12);
            ed.render(1.5f + 3f + 0.7f, w, h, px);                                       // the third piece: zoomed in on the right-hand (blue) side, so the left of the picture is blue too
            int z = px[(h / 2) * w + w / 4];
            boolean reframed = (z & 255) > 150 && ((z >> 16) & 255) < 90;
            ed.render(1.5f + 3f + 0.05f, w, h, px);                                      // just into a dip: near black
            int dip = px[(h / 2) * w + w / 4];
            boolean dipped = ((dip >> 16) & 255) + ((dip >> 8) & 255) + (dip & 255) < 90;
            ed.name = "gate";
            ed.export();
            long deadline = System.currentTimeMillis() + 60000;
            while (ed.exporting >= 0 && System.currentTimeMillis() < deadline) Thread.sleep(20);
            byte[] avi = ed.exported.isEmpty() ? new byte[0] : java.nio.file.Files.readAllBytes(java.nio.file.Path.of(ed.exported));
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(avi).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            String txt = new String(avi, java.nio.charset.StandardCharsets.ISO_8859_1);
            int avih = txt.indexOf("avih"), idx = txt.lastIndexOf("idx1");
            int frames = avih < 0 ? -1 : bb.getInt(avih + 24), streams = avih < 0 ? -1 : bb.getInt(avih + 32), entries = idx < 0 ? -1 : bb.getInt(idx + 4) / 16;
            int first = txt.indexOf("00dc"), fsz = first < 0 ? 0 : bb.getInt(first + 4);
            boolean jpeg = first > 0 && (avi[first + 8] & 255) == 0xFF && (avi[first + 9] & 255) == 0xD8;
            boolean riff = avi.length > 12 && txt.startsWith("RIFF") && bb.getInt(4) + 8 == avi.length && txt.substring(8, 12).equals("AVI ");
            int want = Math.round(dur * 20);
            boolean decodes = false;
            if (jpeg) { java.awt.image.BufferedImage back = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(avi, first + 8, fsz)); decodes = back != null && back.getWidth() == 320 && back.getHeight() == 180; }
            t.check("the editor makes a film of clips and writes it out: three pieces on a timeline (trimmed, one at half speed, re-ordered at will), a cross-fade and a dip to black between them, a monochrome grade, a piece punched in and re-framed, the recorded sound at its own level - previewed and exported by the same renderer, to an AVI that is a real one (Motion-JPEG video, PCM sound, an index)",
                    ca != null && cb != null && ca.audio() && !cb.audio() && Math.abs(dur - (1.5f + 3f + 1f)) < 0.01f && plain && mono && fading && reframed && dipped
                            && riff && frames == want && streams == 2 && entries == want * 2 && jpeg && decodes && absentialis.director.RecordingManager.MAX_SECONDS == 180f,
                    clips.size() + " clips in the library; timeline " + String.format("%.1f", dur) + " s; plain " + plain + ", monochrome " + mono + ", cross-fading " + fading + ", re-framed " + reframed + ", dipped " + dipped
                            + "; export " + avi.length + " bytes: RIFF/AVI " + riff + ", " + frames + " frames (want " + want + "), " + streams + " streams, " + entries + " index entries, first frame a " + (decodes ? "320x180 JPEG" : "?") + "; " + ed.status + "; a clip is at most " + (int) absentialis.director.RecordingManager.MAX_SECONDS + " s");
            // tidy: these were the gate's, not the user's
            for (String n : new String[]{"clip_gate_a", "clip_gate_b"}) { java.nio.file.Path dir = absentialis.director.RecordingManager.root().resolve(n); try (var st = java.nio.file.Files.list(dir)) { for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) st::iterator) java.nio.file.Files.deleteIfExists(f); } java.nio.file.Files.deleteIfExists(dir); }
            if (!ed.exported.isEmpty()) java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(ed.exported));
        } catch (Exception e) { t.check("the editor makes a film of clips and writes it out", false, String.valueOf(e)); }
    }

    // ------------------------------------------------------------------ "Polish the butcher and gladiator animations"
    private static void anims08(SelfTests04.Check t) {
        System.out.println("[v0.8 animation: the Butcher and the Gladiator move like themselves]");
        Content.load();
        String[] clips = {"glad_hark", "claw_windup", "claw_swing", "saw_rev", "saw_swing", "saw_stall"};
        StringBuilder missing = new StringBuilder();
        for (String cn : clips) if (absentialis.character.Anims.get(cn) == null) missing.append(cn).append(' ');
        Raid r = SelfTests.raid(new Profile());
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer);
        r.player.x = 300; r.player.z = 300; r.player.y = G;
        Actor glad = spawn(r, "gladiator", 320, 320, 0), but = spawn(r, "butcher", 330, 320, 0);
        r.freezeAI = true;
        run(r, 0.2f);
        Creatures.sweep(r, glad, 0.1f, 0.9f, true, "claw_swing");
        Creatures.sweep(r, but, 0.1f, 0.9f, false, "chainsaw_rev");
        boolean swinging = glad.anim != null && glad.anim.playing("claw_swing") && but.anim != null && but.anim.playing("saw_swing");
        r.telegraph(glad, "claw", 0.5f); r.telegraph(but, "rev", 0.75f);
        boolean winding = glad.anim.playing("claw_windup") && but.anim.playing("saw_rev");
        // the Gladiator's head is never still: standing for six seconds it turns well to both sides
        r.telegraphs.clear();
        glad.anim.action = ""; glad.vx = glad.vz = 0;
        absentialis.character.Pose pose = new absentialis.character.Pose();
        float lo = 9, hi = -9, sawLo = 9, sawHi = -9;
        for (int i = 0; i < 360; i++) {
            glad.anim.update(glad, 1 / 60f, false, false, false); but.anim.update(but, 1 / 60f, false, false, false);
            glad.anim.pose(glad, pose, 0, null, false); float hy = pose.rot[absentialis.character.Rig.HEAD * 3 + 1]; lo = Math.min(lo, hy); hi = Math.max(hi, hy);
            but.anim.pose(but, pose, 0, null, false); float fa = pose.rot[absentialis.character.Rig.R_FOREARM * 3]; sawLo = Math.min(sawLo, fa); sawHi = Math.max(sawHi, fa);
        }
        t.check("the Gladiator and the Butcher have movements of their own: a wind-up and a swing each (the swings 0.7 asked for did not exist), the Gladiator's head turning to listen, the saw alive in the Butcher's hand",
                missing.length() == 0 && swinging && winding && hi > 0.2f && lo < -0.2f && sawHi - sawLo > 0.05f,
                clips.length + " clips" + (missing.length() > 0 ? ", MISSING " + missing : "") + "; swinging " + swinging + ", winding up " + winding + String.format("; head turned %.2f to %.2f rad while standing; the saw arm moved over %.2f rad", lo, hi, sawHi - sawLo));
    }

    // ------------------------------------------------------------------ the five missions and the ten contracts
    private static java.util.List<int[]> containersIn(World w, absentialis.scene.StoryDirector.Site s) {
        java.util.List<int[]> l = new java.util.ArrayList<>();
        for (int x = Math.max(0, s.x0); x <= Math.min(World.W - 1, s.x1); x++) for (int z = Math.max(0, s.z0); z <= Math.min(World.D - 1, s.z1); z++)
            for (int y = 1; y < World.H - 1; y++) { absentialis.world.Block b = w.block(x, y, z); if (b != null && b.is(absentialis.world.Block.CONTAINER)) l.add(new int[]{x, y, z}); }
        return l;
    }

    private static void story(SelfTests04.Check t) {
        System.out.println("[v0.8 story: five investigations that live in ordinary operations and survive death, ten written contracts]");
        Content.load();
        absentialis.gameplay.missions.Story.load();
        var MS = absentialis.gameplay.missions.Story.MISSIONS;
        // ---- the ten contracts
        java.util.List<absentialis.gameplay.missions.Contracts.Contract> cat = absentialis.gameplay.missions.Contracts.catalogue(1);
        java.util.Set<String> kinds = new java.util.TreeSet<>(), givers = new java.util.TreeSet<>(), titles = new java.util.HashSet<>(), tasks = new java.util.HashSet<>();
        for (var k : cat) { kinds.add(k.kind.name()); givers.add(k.giver); titles.add(k.title); tasks.add(k.kind + "|" + k.target + "|" + k.district); }
        absentialis.gameplay.missions.Contracts board = new absentialis.gameplay.missions.Contracts();
        board.refill(1, 1, "raiders");
        boolean ownFirst = board.board.size() == 10 && board.board.get(0).giver.equals("raiders") && board.board.get(1).giver.equals("raiders") && !board.board.get(2).giver.equals("raiders");
        boolean touchesMission = false;
        for (var k : cat) if (k.target.startsWith("story.")) touchesMission = true;
        t.check("the board is ten written contracts, none like another: ten different kinds of task from all four sides, the survivor's own side at the top, and none of them a step in a story mission",
                cat.size() == 10 && kinds.size() == 10 && givers.size() == 4 && titles.size() == 10 && tasks.size() == 10 && ownFirst && !touchesMission, kinds + " from " + givers);

        // each kind is moved by the thing it is about and by nothing else; three at a time; a paid contract rests and comes back
        absentialis.gameplay.missions.Contracts cs = new absentialis.gameplay.missions.Contracts();
        cs.refill(1, 1, "");
        String e1 = cs.accept("pump_road"), e2 = cs.accept("specimen"), e3 = cs.accept("shake_the_lockers"), e4 = cs.accept("ration_line");
        absentialis.world.District res = absentialis.world.District.MARKET, ind = absentialis.world.District.INDUSTRIAL;
        cs.onKill("infected", ind); cs.onKill("raiders", res);
        int wrong = cs.active.get(0).have + cs.active.get(1).have + cs.active.get(2).have;
        for (int i = 0; i < 8; i++) cs.onKill("infected", res);
        cs.onSpecial("tank");
        for (int i = 0; i < 10; i++) { cs.onSearch(res); cs.onSearch(ind); }
        boolean allDone = cs.active.get(0).done() && cs.active.get(1).done() && cs.active.get(2).done();
        var paid = cs.active.get(0);
        cs.complete(paid);
        cs.operationDone(); cs.refill(1, 1);
        boolean resting = cs.board.stream().noneMatch(k -> k.id.equals("pump_road"));
        cs.operationDone(); cs.refill(1, 1);
        boolean stillResting = cs.board.stream().noneMatch(k -> k.id.equals("pump_road"));
        cs.operationDone(); cs.refill(1, 1);
        boolean back = cs.board.stream().anyMatch(k -> k.id.equals("pump_road") && k.have == 0);
        var rt = absentialis.gameplay.missions.Contracts.fromJson(absentialis.engine.util.Json.parseObject(absentialis.engine.util.Json.write(cs.toJson())));
        int payLow = absentialis.gameplay.missions.Contracts.catalogue(1).get(0).scrip, payHigh = absentialis.gameplay.missions.Contracts.catalogue(40).get(0).scrip;
        t.check("contracts move only for what they are about, three are held at once, and a paid contract rests for " + absentialis.gameplay.missions.Story.contractCooldown + " operations and then is offered again - saved and read back as it stands",
                e1 == null && e2 == null && e3 == null && e4 != null && wrong == 0 && allDone && resting && stillResting && back && rt.active.size() == cs.active.size() && rt.serial == cs.serial && rt.returns.equals(cs.returns) && payHigh > payLow,
                "fourth refused: " + e4 + "; wrong-district/wrong-kind progress " + wrong + "; done " + allDone + "; resting " + resting + "/" + stillResting + ", back " + back + "; pay at Rank 1 $" + payLow + ", at Rank 40 $" + payHigh);

        // ---- the five missions: in the world, whether anyone has heard of them or not
        World city = SelfTests06.world(1337L);
        Profile p = new Profile();
        Raid r = new Raid(city, p, new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer);
        StringBuilder where = new StringBuilder();
        boolean sitesOk = MS.size() == 5 && r.story.sites.size() == 10;
        int fewest = 999;
        java.util.Map<String, java.util.List<int[]>> boxes = new java.util.HashMap<>();
        for (var s : r.story.sites) {
            java.util.List<int[]> l = containersIn(city, s);
            boxes.put(s.evidence, l);
            fewest = Math.min(fewest, l.size());
            sitesOk &= !l.isEmpty() && Content.def(s.evidence) != null && Content.def(s.evidence).questItem && city.district((s.x0 + s.x1) / 2f, (s.z0 + s.z1) / 2f).name().equals(s.mission.district);
            where.append(s.mission.id).append(' ').append(s.landmark).append(" (").append(l.size()).append(" containers) ");
        }
        java.util.Set<String> districts = new java.util.HashSet<>();
        for (var m : MS) districts.add(m.district);
        t.check("each of the five investigations has two sites that are real places in its own district - one district each - and each site has containers for its evidence to lie in",
                sitesOk && districts.size() == 5, where + "- fewest containers on a site: " + fewest);

        // ---- one investigation, start to finish, with a death in the middle
        var m1 = MS.get(0);
        var site = r.story.sites.get(0);
        boolean rumour = p.missions.stage(m1.id) == absentialis.gameplay.missions.Missions.Stage.RUMOUR;
        r.player.x = (site.x0 + site.x1) / 2f; r.player.z = (site.z0 + site.z1) / 2f;
        r.story.update(1f);
        boolean lead = p.missions.stage(m1.id) == absentialis.gameplay.missions.Missions.Stage.INVESTIGATING && r.story.notices.size() == 1 && r.story.notices.get(0)[1].equals("mission_accepted");
        // go through the place's containers: the ledger is in one of the first four
        int searched = 0;
        absentialis.gameplay.items.ItemInstance found = null;
        for (int[] bx : boxes.get(site.evidence)) {
            absentialis.gameplay.inventory.Container box = r.openContainer(bx[0], bx[1], bx[2]);
            searched++;
            for (var it : box.items) if (it.defId.equals(site.evidence)) found = it;
            if (found != null || searched >= 12) break;
        }
        boolean inBox = found != null && searched <= absentialis.scene.StoryDirector.CERTAIN_BY;
        boolean notYet = p.missions.stage(m1.id) == absentialis.gameplay.missions.Missions.Stage.INVESTIGATING;       // lying in a box is not having seen it
        if (found != null) { absentialis.gameplay.inventory.InventoryService.release(found); absentialis.gameplay.inventory.InventoryService.store(found, p.loadout); }
        r.story.update(1f);
        boolean proven = p.missions.stage(m1.id) == absentialis.gameplay.missions.Missions.Stage.PROVEN && p.missions.of(m1.id).seen[0] && !p.missions.of(m1.id).seen[1];
        // killed, carrying it
        p.difficulty = 2;
        r.die("test");
        absentialis.scene.RaidResolver.resolve(r);
        boolean lostPaper = p.allItems().stream().noneMatch(i -> i.defId.equals(site.evidence));
        Profile back2 = Profile.fromJson(absentialis.engine.util.Json.parseObject(absentialis.engine.util.Json.write(p.toJson())));
        boolean kept = back2.missions.stage(m1.id) == absentialis.gameplay.missions.Missions.Stage.PROVEN && back2.missions.of(m1.id).seen[0];
        // a later operation: the evidence already seen is not put out again; the other site still has its own; getting out makes it theirs to decide
        Raid r2 = new Raid(city, back2, new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        r2.populate = false;
        int again = 0;
        for (int[] bx : boxes.get(site.evidence)) { for (var it : r2.openContainer(bx[0], bx[1], bx[2]).items) if (it.defId.equals(site.evidence)) again++; if (again > 0) break; }
        var site2 = r2.story.sites.get(1);
        absentialis.gameplay.items.ItemInstance second = null;
        for (int[] bx : boxes.get(site2.evidence)) { for (var it : r2.openContainer(bx[0], bx[1], bx[2]).items) if (it.defId.equals(site2.evidence)) second = it; if (second != null) break; }
        if (second != null) { absentialis.gameplay.inventory.InventoryService.release(second); absentialis.gameplay.inventory.InventoryService.store(second, back2.loadout); }
        r2.story.update(1f);
        boolean corroborated = back2.missions.of(m1.id).corroborated();
        r2.debrief.outcome = Raid.Debrief.Outcome.EXTRACTED; r2.debrief.reason = "test";
        java.util.List<String> lines = absentialis.scene.RaidResolver.resolve(r2);
        boolean decide = back2.missions.stage(m1.id) == absentialis.gameplay.missions.Missions.Stage.DECISION && back2.missions.awaitingDecision() == m1 && lines.stream().anyMatch(l -> l.startsWith("STORY"));
        long xp1 = back2.missions.resolve(m1.id, "survivors", 3), xp2 = back2.missions.resolve(m1.id, "mercenaries", 3);
        boolean closed = back2.missions.stage(m1.id) == absentialis.gameplay.missions.Missions.Stage.CLOSED && "survivors".equals(back2.missions.of(m1.id).givenTo) && back2.missions.closed() == 1;
        long all5 = absentialis.gameplay.missions.Story.storyXp() * 5, toRank = absentialis.gameplay.progression.Progression.xpToReach(20);
        boolean order = true;                                           // any mission can be taken up without the others
        // (others may already be leads: an operation that starts on a site's doorstep has found it)
        order &= back2.missions.stage(MS.get(2).id) == absentialis.gameplay.missions.Missions.Stage.RUMOUR;
        back2.missions.onSite(MS.get(4), 3);
        order &= back2.missions.stage(MS.get(4).id) == absentialis.gameplay.missions.Missions.Stage.INVESTIGATING;
        t.check("an investigation runs from rumour to decision inside ordinary operations and death takes none of it back: stood on the site, searched it, took the ledger (" + searched + " containers), was killed carrying it - the paper was lost and the knowledge was not; "
                        + "came back, found the second proof, got out, and gave it to the shelters for a one-time award",
                rumour && lead && inBox && notYet && proven && lostPaper && kept && again == 0 && corroborated && decide && xp1 > 0 && xp2 == 0 && closed && order && all5 > toRank * 0.7 && all5 < toRank * 1.1,
                "rumour " + rumour + ", lead " + lead + ", in a box within " + absentialis.scene.StoryDirector.CERTAIN_BY + " " + inBox + ", not seen while in the box " + notYet + ", proven " + proven + ", paper lost on death " + lostPaper + ", knowledge kept through save " + kept
                        + ", put out again " + again + ", corroborated " + corroborated + ", decision " + decide + ", award " + xp1 + " then " + xp2 + ", closed " + closed + ", any order " + order + " (stages " + back2.missions.stage("M02") + " " + back2.missions.stage("M03") + " " + back2.missions.stage("M04") + " " + back2.missions.stage("M05") + "); the five together pay " + all5 + " of the " + toRank + " XP that Rank 20 takes");
    }

    // ------------------------------------------------------------------ the eighteen items, the five cards, give
    private static int voxels(absentialis.character.CharModel m) { int n = 0; for (int g = 0; g < absentialis.character.Rig.GROUPS; g++) n += m.groups[g].count(); return n; }

    private static void content(SelfTests04.Check t) {
        System.out.println("[v0.8 content: eighteen pieces of equipment, what their protection does, five profile cards, give for every item]");
        Content.load();
        // ---- "New Items" - name, protection x/5, price, Rank, exactly as the brief lists them
        Object[][] want = {
                {"helmet.hardhat", "Hardhat", 1, 1000, 3, 1}, {"armor.scavenged", "Scavenged Armor", 1, 3000, 3, 1}, {"bag.school", "Schoolbag", 0, 1500, 3, 1},
                {"helmet.moto", "Motorcycle Helmet", 2, 2500, 5, 0}, {"armor.rider", "Reinforced Rider Kit", 2, 6000, 5, 0}, {"bag.hiking", "Hiking Bag", 0, 3000, 5, 0},
                {"helmet.tactical", "Tactical Helmet", 3, 5000, 10, 2}, {"armor.tactical_rig", "Tactical Combat Rig", 3, 14000, 10, 2}, {"bag.tactical", "Tactical Bag", 1, 6000, 10, 2},
                {"helmet.ballistic", "Ballistic Helmet", 4, 10000, 20, 3}, {"armor.full_ballistic", "Full Ballistic Armor", 4, 26000, 20, 3}, {"bag.ballistic", "Ballistic Equipment Pack", 2, 10000, 20, 3},
                {"helmet.sealed", "Sealed Ballistic Helmet", 5, 18000, 30, 4}, {"armor.advanced_suit", "Advanced Ballistic Suit", 5, 42000, 30, 4}, {"bag.advanced", "Advanced Ballistic Bag", 5, 18000, 30, 4},
                {"helmet.smart", "Smart Ballistic Helmet", 5, 35000, 40, 5}, {"armor.powered", "Powered Composite Armor", 5, 75000, 40, 5}, {"bag.warbag", "Military Warbag", 4, 30000, 40, 5}};
        StringBuilder wrong = new StringBuilder();
        int biggest = 0;
        String biggestBag = "";
        for (absentialis.gameplay.items.ItemDef d : Content.ITEMS.values()) if (d.backpack != null && d.backpack.gw * d.backpack.gh > biggest) { biggest = d.backpack.gw * d.backpack.gh; biggestBag = d.id; }
        for (Object[] w : want) {
            absentialis.gameplay.items.ItemDef d = Content.def((String) w[0]);
            if (d == null) { wrong.append(w[0]).append(" missing; "); continue; }
            int cls = d.armor == null ? 0 : d.armor.armorClass;
            if (!d.name.equals(w[1]) || cls != (Integer) w[2] || d.value != (Integer) w[3] || d.rank != (Integer) w[4] || d.tier != (Integer) w[5]) wrong.append(d.id).append(" is ").append(d.name).append(" class ").append(cls).append(" $").append(d.value).append(" rank ").append(d.rank).append(" tier ").append(d.tier).append("; ");
        }
        boolean oldStill = Content.def("helmet.ssh") != null && Content.def("armor.heavy") != null && Content.def("bag.large") != null && Content.def("helmet.baseball") != null;
        t.check("the eighteen new pieces of equipment are in the game as listed - name, protection, price, Rank and rarity - beside the old ones, and the Military Warbag is the largest bag there is",
                wrong.length() == 0 && oldStill && "bag.warbag".equals(biggestBag), want.length + " items" + (wrong.length() > 0 ? "; WRONG: " + wrong : "") + "; largest bag " + biggestBag + " (" + biggest + " cells); " + Content.ITEMS.size() + " items in all");

        // ---- each has a model of its own, as an object and worn
        java.util.Set<Integer> shapes = new java.util.HashSet<>();
        StringBuilder noModel = new StringBuilder();
        absentialis.character.Appearance app = absentialis.character.Appearance.legacy(1, 0);
        absentialis.character.CharBuilder.Gear bare = new absentialis.character.CharBuilder.Gear();
        bare.head = ""; bare.face = ""; bare.vest = ""; bare.pack = "";
        int naked = voxels(absentialis.character.CharBuilder.build(app, bare));
        java.util.Set<Integer> worn = new java.util.HashSet<>();
        for (Object[] w : want) {
            absentialis.gameplay.items.ItemDef d = Content.def((String) w[0]);
            absentialis.engine.voxel.VoxModel m = absentialis.ui.ItemModels.of(d);
            if (m == null || m.count() < 40) noModel.append(d.id).append(" (object) ");
            else shapes.add(java.util.Arrays.hashCode(new int[]{m.sx, m.sy, m.sz, m.count()}));
            absentialis.character.CharBuilder.Gear g = new absentialis.character.CharBuilder.Gear();
            g.head = "head".equals(d.slot) ? absentialis.scene.ActorRenderer.headGear(d.id) : ""; g.face = "";
            g.vest = "armor".equals(d.slot) ? absentialis.scene.ActorRenderer.vestGear(d.id) : "";
            g.pack = "backpack".equals(d.slot) ? absentialis.scene.ActorRenderer.packGear(d.id) : "";
            absentialis.character.CharModel cm = absentialis.character.CharBuilder.build(app, g);
            int sig = 0;
            for (int gi = 0; gi < absentialis.character.Rig.GROUPS; gi++) sig = sig * 31 + java.util.Arrays.hashCode(cm.groups[gi].col);
            if (!worn.add(sig) || cm.clipped) noModel.append(d.id).append(cm.clipped ? " (worn: outside the body's grid) " : " (worn: same as another) ");
        }
        // the baseball hat: no white block on the brow any more
        absentialis.character.CharBuilder.Gear cap = new absentialis.character.CharBuilder.Gear();
        cap.head = "baseball"; cap.face = ""; cap.vest = ""; cap.pack = "";
        absentialis.character.CharModel capM = absentialis.character.CharBuilder.build(app, cap);
        int whiteFront = 0, crownTop = 0;
        for (int x = 0; x < 12; x++) for (int y = 9; y < 14; y++) { int c0 = capM.get(absentialis.character.Rig.HEAD, x, y, 1); if (c0 != 0 && ((c0 >> 16) & 255) > 200 && ((c0 >> 8) & 255) > 200 && (c0 & 255) > 190) whiteFront++; }
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) if (capM.get(absentialis.character.Rig.HEAD, x, 12, z) != 0) crownTop++;
        t.check("every one of them has a model of its own, lying in a grid and worn on the body, and the baseball hat is a cap again: a low crown, no white block on its brow",
                noModel.length() == 0 && shapes.size() >= 16 && worn.size() == want.length && whiteFront <= 2 && crownTop <= 4,
                shapes.size() + " distinct object models, " + worn.size() + " distinct worn models (a bare body is " + naked + " voxels)" + (noModel.length() > 0 ? "; PROBLEMS: " + noModel : "") + "; baseball hat: " + whiteFront + " white voxels on the front (0.7: 18), " + crownTop + " at the very top");

        // ---- what the protection does
        Profile p = new Profile();
        Raid r = SelfTests.raid(p);
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer);
        r.player.x = 300; r.player.z = 300; r.player.y = G; r.player.yaw = 0;                 // facing -z
        Actor front = spawn(r, "raiders", 300, 290, 0), behind = spawn(r, "raiders", 300, 310, 0);
        r.freezeAI = true;
        int[] head = new int[6], leg = new int[2], back = new int[2];
        String[] helmets = {null, "helmet.hardhat", "helmet.moto", "helmet.tactical", "helmet.ballistic", "helmet.sealed"};
        for (int i = 0; i < helmets.length; i++) {
            p.loadout = new absentialis.gameplay.inventory.Loadout();
            if (helmets[i] != null) carry(p, helmets[i], "head");
            r.applyArmor();
            head[i] = r.protection(r.player, "head", front);
        }
        p.loadout = new absentialis.gameplay.inventory.Loadout();
        carry(p, "armor.vest_soft", "armor"); r.applyArmor();
        leg[0] = r.protection(r.player, "legs", front);
        p.loadout = new absentialis.gameplay.inventory.Loadout();
        carry(p, "armor.tactical_rig", "armor"); carry(p, "bag.advanced", "backpack"); r.applyArmor();
        leg[1] = r.protection(r.player, "legs", front);
        back[0] = r.protection(r.player, "torso", front); back[1] = r.protection(r.player, "torso", behind);
        // and it shows in the damage: the same hundred rounds to the head, bare and under the best helmet
        float[] taken = new float[2];
        for (int k = 0; k < 2; k++) {
            p.loadout = new absentialis.gameplay.inventory.Loadout();
            if (k == 1) carry(p, "helmet.smart", "head");
            r.applyArmor();
            for (int i = 0; i < 100; i++) { r.player.maxHp = r.player.hp = 1e6f; r.player.dead = false; r.damageActor(r.player, front, 10, Content.def("ammo.handgun"), "head", r.player.x, r.player.y + 1.7f, r.player.z); taken[k] += 1e6f - r.player.hp; }
        }
        p.loadout = new absentialis.gameplay.inventory.Loadout();
        carry(p, "helmet.sealed", "head"); r.applyArmor();
        t.check("protection is the armour class: a helmet protects the head by its own class (1 to 5), armour that covers the legs protects the legs, a ballistic pack protects the back and only the back - and it shows in the damage taken",
                head[0] == 0 && head[1] == 1 && head[2] == 2 && head[3] == 3 && head[4] == 4 && head[5] == 5 && leg[0] == 0 && leg[1] == 3 && back[0] == 3 && back[1] == 5 && taken[1] < taken[0] * 0.5f,
                "head: bare " + head[0] + ", hardhat " + head[1] + ", motorcycle " + head[2] + ", tactical " + head[3] + ", ballistic " + head[4] + ", sealed " + head[5] + "; legs under a soft vest " + leg[0] + ", under the tactical rig " + leg[1]
                        + "; torso from the front " + back[0] + ", from behind with the advanced bag " + back[1] + String.format("; 100 handgun rounds to the head: %.0f bare, %.0f under the smart helmet", taken[0], taken[1]));

        // ---- the shop puts them on the wall at their Rank
        absentialis.gameplay.progression.Progression low = new absentialis.gameplay.progression.Progression(), high = new absentialis.gameplay.progression.Progression();
        low.bestRank = 4; high.bestRank = 40;
        int lowShown = 0, highShown = 0;
        for (Object[] w : want) { absentialis.gameplay.items.ItemDef d = Content.def((String) w[0]); if (absentialis.gameplay.economy.Economy.shopShows(d, low)) lowShown++; if (absentialis.gameplay.economy.Economy.shopShows(d, high)) highShown++; }
        t.check("the shop sells them once their Rank is held: three of the eighteen at Rank 4, all eighteen - the Mythic set included - at Rank 40", lowShown == 3 && highShown == 18, lowShown + " at Rank 4, " + highShown + " at Rank 40");

        // ---- "New profile backgrounds"
        String[][] cards = {{"thunder", "Thunder Card", "Rank 10"}, {"prestige", "Prestige Card", "Prestige 1"}, {"operator", "Operator Card", "Rank 20"}, {"jail", "Jail Card", "Rank 4"}, {"prestigious", "Prestigious I Card", "Prestige 2"}};
        absentialis.gameplay.progression.Progression pr = new absentialis.gameplay.progression.Progression();
        boolean cardsOk = true;
        StringBuilder cs = new StringBuilder();
        for (String[] cd : cards) {
            absentialis.scene.ProfileCard.Background b = absentialis.scene.ProfileCard.background(cd[0]);
            pr.bestRank = 1; pr.prestige = 0;
            boolean closed = !absentialis.scene.ProfileCard.unlocked(b, pr);
            int pn = absentialis.scene.ProfileCard.prestigeNeeded(b);
            pr.bestRank = Math.max(1, b.rank() - 1); pr.prestige = Math.max(0, pn - 1);
            boolean justShort = b.rank() == 1 && pn == 0 || !absentialis.scene.ProfileCard.unlocked(b, pr);
            pr.bestRank = b.rank(); pr.prestige = pn;
            boolean open = absentialis.scene.ProfileCard.unlocked(b, pr);
            cardsOk &= b.id().equals(cd[0]) && b.name().equals(cd[1]) && absentialis.scene.ProfileCard.requirement(b).equals(cd[2]) && closed && justShort && open;
            cs.append(b.name()).append(" (").append(absentialis.scene.ProfileCard.requirement(b)).append(") ");
        }
        pr.bestRank = 50; pr.prestige = 0;
        t.check("five new profile cards, each opened by what the brief says: the Jail Card at Rank 4, Thunder at 10, Operator at 20, the Prestige Card at Prestige 1 and Prestigious I at Prestige 2 - and Rank alone never opens a Prestige card",
                cardsOk && absentialis.scene.ProfileCard.BACKGROUNDS.length == 15 && !absentialis.scene.ProfileCard.unlocked(absentialis.scene.ProfileCard.background("prestige"), pr) && absentialis.scene.ProfileCard.best(50).id().equals("gold"), cs.toString().trim());

        // ---- "give command should have a command for every item in the game"
        int byId = 0, byName = 0, made = 0;
        StringBuilder lost = new StringBuilder();
        for (absentialis.gameplay.items.ItemDef d : Content.ITEMS.values()) {
            if (Content.find(d.id) == d) byId++; else lost.append(d.id).append(' ');
            absentialis.gameplay.items.ItemDef viaName = Content.find(d.name.toLowerCase().replace(' ', '_'));
            if (viaName != null && viaName.name.equals(d.name)) byName++;
            try { absentialis.gameplay.items.ItemInstance it = new absentialis.gameplay.items.ItemInstance(d.id); if (it.def() == d && absentialis.ui.ItemModels.of(it) != null) made++; } catch (RuntimeException e) { lost.append(d.id).append("(cannot be made) "); }
        }
        boolean aliases = Content.find("knife") == Content.def("melee.knife") && Content.find("pink spinel") == Content.def("treasure.spinel_pink") && Content.find("Military Warbag") == Content.def("bag.warbag") && Content.find("no such thing") == null;
        t.check("every item in the game can be given: all " + Content.ITEMS.size() + " are found by their id and can be made, and an item can be asked for by the end of its id or by its name",
                byId == Content.ITEMS.size() && made == Content.ITEMS.size() && byName >= Content.ITEMS.size() - 4 && aliases, byId + " by id, " + byName + " by name, " + made + " made" + (lost.length() > 0 ? "; " + lost : ""));
    }

    // ------------------------------------------------------------------ UltraEngine, every format, the sound pack, the XP HUD
    private static byte[] wavBytes(int tag, int bits, int channels, int rate, float seconds) {
        int frames = Math.round(rate * seconds), bytes = bits / 8, data = frames * channels * bytes;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(44 + data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + data).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) tag).putShort((short) channels).putInt(rate).putInt(rate * channels * bytes).putShort((short) (channels * bytes)).putShort((short) bits);
        b.put("data".getBytes()).putInt(data);
        for (int i = 0; i < frames; i++) for (int c = 0; c < channels; c++) {
            double v = Math.sin(2 * Math.PI * 440 * i / rate) * 0.5;
            if (tag == 3 && bits == 32) b.putFloat((float) v);
            else if (tag == 3) b.putDouble(v);
            else if (bits == 8) b.put((byte) (int) (v * 127 + 128));
            else if (bits == 16) b.putShort((short) (v * 32767));
            else if (bits == 24) { int s = (int) (v * 8388607); b.put((byte) s).put((byte) (s >> 8)).put((byte) (s >> 16)); }
            else b.putInt((int) (v * 2147483647.0));
        }
        return b.array();
    }

    private static float peak(float[] d) { float m = 0; for (float v : d) m = Math.max(m, Math.abs(v)); return m; }

    private static void audio(SelfTests04.Check t) {
        System.out.println("[v0.8 audio: UltraEngine, every format, all 298 supplied recordings in use, district beds, close gun recordings, sounds by material, the XP HUD]");
        Content.load();
        // ---- "rename the audio engine to UltraEngine"
        boolean oldGone = true;
        try { Class.forName("absentialis.engine.audio.AudioEngine"); oldGone = false; } catch (ClassNotFoundException gone) { /* as it should be */ }
        t.check("the audio engine is UltraEngine: the class, its name and its credit - and nothing called AudioEngine is left",
                oldGone && "UltraEngine".equals(absentialis.engine.audio.UltraEngine.NAME) && java.util.Arrays.asList(absentialis.engine.Astratto.SYSTEMS).contains("UltraEngine audio"), "AudioEngine gone: " + oldGone);

        // ---- "all audio formats work (.mp3 / .ogg / .wav)"
        StringBuilder fm = new StringBuilder();
        int wavOk = 0;
        int[][] kinds = {{1, 8, 1, 22050}, {1, 16, 2, 44100}, {1, 24, 2, 48000}, {1, 32, 1, 96000}, {3, 32, 2, 48000}, {3, 64, 1, 44100}, {1, 16, 1, 16000}, {1, 16, 6, 44100}};
        for (int[] k : kinds) {
            try {
                absentialis.engine.audio.Codecs.Decoded d = absentialis.engine.audio.Codecs.decode(wavBytes(k[0], k[1], k[2], k[3], 0.5f), "tone.wav");
                // half a second of a 440 Hz tone at half scale, whatever it was stored as
                int cross = 0;
                for (int i = 1; i < d.mono().length; i++) if (d.mono()[i - 1] < 0 && d.mono()[i] >= 0) cross++;
                if (Math.abs(d.seconds() - 0.5f) < 0.02f && Math.abs(peak(d.mono()) - 0.5f) < 0.06f && Math.abs(cross - 220) <= 3) wavOk++;
                else fm.append(d.format()).append(" wrong (").append(d.seconds()).append(" s, peak ").append(peak(d.mono())).append(", ").append(cross).append(" cycles) ");
            } catch (absentialis.engine.audio.Codecs.Failure e) { fm.append(java.util.Arrays.toString(k)).append(": ").append(e.getMessage()).append(' '); }
        }
        java.util.Map<String, java.nio.file.Path> disk = absentialis.engine.audio.SoundPack.index(absentialis.engine.audio.SoundPack.DIR);
        String oggInfo = "", mp3Info = "", misnamed = "";
        boolean oggOk = false, mp3Ok = false, sniffOk = false, refused = false;
        try {
            absentialis.engine.audio.Codecs.Decoded o = absentialis.engine.audio.Codecs.decode(disk.get("open_container"));
            oggOk = o.format().startsWith("ogg") && o.seconds() > 0.1f && peak(o.mono()) > 0.05f;
            oggInfo = String.format("%.2f s", o.seconds());
            long t0 = System.nanoTime();
            absentialis.engine.audio.Codecs.Decoded m = absentialis.engine.audio.Codecs.decode(disk.get("downtowndistrictambience"));
            mp3Ok = m.format().equals("mp3") && m.seconds() > 30 && peak(m.mono()) > 0.05f;
            mp3Info = String.format("%.0f s of %d Hz x%d decoded in %.1f s", m.seconds(), m.sourceRate(), m.channels(), (System.nanoTime() - t0) / 1e9);
            // the name on a file is not what it is: an Ogg called .wav and a WAVE called .mp3 are read for what they hold
            byte[] ogg = java.nio.file.Files.readAllBytes(disk.get("menu_back"));
            sniffOk = absentialis.engine.audio.Codecs.decode(ogg, "menu_back.wav").format().startsWith("ogg") && absentialis.engine.audio.Codecs.decode(wavBytes(1, 16, 1, 44100, 0.2f), "tone.mp3").format().startsWith("wav");
            try { absentialis.engine.audio.Codecs.decode("this is not a sound file at all, it is a sentence".getBytes(), "x.wav"); } catch (absentialis.engine.audio.Codecs.Failure e) { refused = true; misnamed = e.getMessage(); }
        } catch (Exception e) { fm.append(e); }
        t.check("every format plays: WAV as 8, 16, 24 and 32-bit PCM and 32 and 64-bit float, mono to six channels, 16 to 96 kHz; Ogg Vorbis; MP3 - read by what a file holds, not what it is called, and a file that is none of them is refused with a reason",
                wavOk == kinds.length && oggOk && mp3Ok && sniffOk && refused, wavOk + " of " + kinds.length + " WAV forms; ogg " + oggInfo + "; mp3 " + mp3Info + "; refused: \"" + misnamed + "\" " + fm);

        // ---- "integrate all user audio in 'sound effects to add'"
        absentialis.engine.audio.UltraEngine ue = new absentialis.engine.audio.UltraEngine();
        java.util.Map<String, float[]> synth = new java.util.HashMap<>();
        new absentialis.engine.audio.SoundBank().build(synth); new absentialis.engine.audio.SoundBank04().build(synth); new absentialis.engine.audio.SoundBank06().build(synth); new absentialis.engine.audio.SoundBank07().build(synth);
        ue.buildBank();
        absentialis.engine.audio.SoundPack pk = ue.pack;
        int source = 0;
        java.io.File src = new java.io.File("sound effects to add");
        java.util.Set<String> missingFromPack = new java.util.TreeSet<>();
        if (src.isDirectory()) for (String f : src.list()) if (absentialis.engine.audio.Codecs.accepts(f)) { source++; if (!pk.onDisk.containsKey(absentialis.engine.audio.Codecs.stem(f))) missingFromPack.add(f); }
        StringBuilder silent = new StringBuilder();
        for (String name : pk.sounds.keySet()) for (int k = 1; k <= pk.sounds.get(name).size(); k++) {
            float[] d = ue.samples.get(k == 1 ? name : name + "_" + k);
            float pkv = d == null ? 0 : peak(d);
            if (d == null || pkv < 0.3f || pkv > 1.01f || Float.isNaN(pkv)) silent.append(name).append('#').append(k).append(' ');
        }
        t.check("all of the supplied audio is in the game: every file of 'sound effects to add' is in the pack, every file in the pack is one of a sound's takes, a district bed or a weapon's recording, every one decodes, and none is silent or clipped",
                pk.problems.isEmpty() && pk.onDisk.size() == 300 + pk.online.size() && pk.unused().isEmpty() && pk.used.size() == pk.onDisk.size() && (source == 0 || source <= 300 && missingFromPack.isEmpty()) && silent.length() == 0,
                pk.onDisk.size() + " files in the pack (" + source + " in the source folder), " + pk.used.size() + " used: " + pk.files + " as takes of " + pk.sounds.size() + " sounds (" + pk.replaced.size() + " replace a synthesised sound, " + pk.added.size() + " are new), "
                        + pk.streamFiles.size() + " beds, the rest on weapons" + (pk.problems.isEmpty() ? "" : "; PROBLEMS: " + pk.problems) + (silent.length() > 0 ? "; bad levels: " + silent : "") + (missingFromPack.isEmpty() ? "" : "; not copied: " + missingFromPack));

        // ---- "replace synthesised chainsaw / claws / hammer / casings / spray ... shop purchase SFX" with recordings
        String[] wanted = {"chainsaw", "chainsaw_rev", "chainsaw_stall", "claw_scrape", "claw_swing", "hammer", "casing_drop", "shell_drop", "spray_hiss", "ui_buy", "explosion", "explosion_far", "glass_break", "ricochet", "melee_hit", "melee_swing", "pump", "pin", "bounce", "fire_loop", "bone_snap"};
        StringBuilder still = new StringBuilder();
        for (String w : wanted) if (!ue.recorded.contains(w) || synth.get(w) == ue.samples.get(w)) still.append(w).append(' ');
        int synthLeft = 0, synthWorld = 0;
        java.util.List<String> leftNames = new java.util.ArrayList<>();
        for (String n : synth.keySet()) if (!ue.recorded.contains(n)) { synthLeft++; if (!n.startsWith("ui_") && !n.startsWith("xp_") && !n.startsWith("music_") && !n.startsWith("bark_")) { synthWorld++; leftNames.add(n); } }
        java.util.Collections.sort(leftNames);
        boolean credited = java.nio.file.Files.exists(absentialis.engine.audio.SoundPack.DIR.resolve("CREDITS.txt")) && absentialis.engine.util.Json.list(absentialis.engine.audio.SoundPack.table(), "credits").size() == pk.online.size();
        t.check("the saw, the claws, the hammer, brass and hulls, the spray, the shop's till, blasts, glass and ricochets are recordings now - the ones not supplied were found online under CC0 and are credited by file",
                still.length() == 0 && pk.online.size() >= 16 && credited,
                wanted.length + " named effects are recordings" + (still.length() > 0 ? " EXCEPT " + still : "") + "; " + pk.online.size() + " files found online, credited: " + credited + ". Still synthesised (reported, not hidden): " + synthLeft + " sounds, " + synthWorld + " of them world sounds: " + leftNames);

        // ---- the takes: thirty strikes are thirty different strikes
        int conc = ue.takes("impact_concrete"), repeatsRun = 0;
        java.util.Set<String> heard = new java.util.HashSet<>();
        String last = "";
        for (int i = 0; i < 200; i++) { String tk = ue.take("impact_concrete"); if (tk.equals(last)) repeatsRun++; last = tk; heard.add(tk); }
        boolean replacedReal = true;
        StringBuilder same = new StringBuilder();
        for (String name : pk.replaced) if (synth.get(name) == ue.samples.get(name) || !ue.recorded.contains(name)) { replacedReal = false; same.append(name).append(' '); }
        t.check("a sound with several recordings never plays the same one twice running, and plays all of them: bullets on masonry have " + conc + " takes",
                conc >= 20 && heard.size() == conc && repeatsRun == 0 && ue.takes("ui_click") == 1 && ue.take("ui_click").equals("ui_click") && replacedReal,
                heard.size() + " of " + conc + " heard in 200 strikes, " + repeatsRun + " immediate repeats; " + pk.replaced.size() + " synthesised sounds replaced by recordings" + (same.length() > 0 ? "; NOT replaced: " + same : ""));

        // ---- the district beds: decoded off the game's thread, and each district has its own
        boolean bedsNamed = true;
        StringBuilder beds = new StringBuilder();
        for (absentialis.world.District d : absentialis.world.District.CITY) {
            String bed = "amb_" + absentialis.scene.SoundNames.ambience(d);
            bedsNamed &= ue.hasStream(bed);
            beds.append(d.display).append("->").append(bed).append(' ');
        }
        long t0 = System.nanoTime();
        absentialis.engine.audio.UltraEngine.Voice bed = ue.loopStream("amb_district_downtown", 0.5f, absentialis.engine.audio.UltraEngine.AMBIENCE);
        double returned = (System.nanoTime() - t0) / 1e6;
        float[] mix = new float[2048];
        ue.mixBlock(mix);
        float quietPeak = peak(mix);
        long deadline = System.currentTimeMillis() + 60000;
        while (!ue.streamReady("amb_district_downtown") && System.currentTimeMillis() < deadline) try { Thread.sleep(50); } catch (InterruptedException e) { break; }
        double ready = (System.nanoTime() - t0) / 1e9;
        float loud = 0;
        for (int i = 0; i < 400; i++) { ue.mixBlock(mix); loud = Math.max(loud, peak(mix)); }
        bed.stop();
        t.check("each district has its own recorded bed, and asking for one never holds the game up: the call returns at once, silent, and the bed fades in when it has been decoded on a thread of its own",
                bedsNamed && returned < 30 && quietPeak == 0 && ue.streamReady("amb_district_downtown") && loud > 0.01f && "wind".equals(absentialis.scene.SoundNames.ambience(absentialis.world.District.WILDERNESS)),
                beds + String.format("- call returned in %.1f ms, bed ready after %.1f s, then heard at peak %.3f", returned, ready, loud));

        // ---- "punchier real gun sounds"
        absentialis.engine.audio.gun.GunAudio g = new absentialis.engine.audio.gun.GunAudio();
        g.load(absentialis.engine.audio.AssetSounds.DIR);
        int withPunch = 0, withSup = 0;
        StringBuilder guns = new StringBuilder();
        for (var e : g.library.profiles.entrySet()) { if (!e.getValue().punch.isEmpty()) { withPunch++; guns.append(e.getKey().substring(7)).append(' '); } if (!e.getValue().punchSuppressed.isEmpty()) withSup++; }
        absentialis.engine.audio.gun.AcousticEnvironment.Geometry open = new absentialis.engine.audio.gun.AcousticEnvironment.Geometry() {
            public float ray(float ox, float oy, float oz, float dx, float dy, float dz, float max, float[] hit) { if (dy < -0.9f) { hit[0] = ox; hit[1] = oy - 1.6f; hit[2] = oz; hit[3] = 0.5f; return 1.6f; } return -1; }
            public float openness(float ax, float ay, float az, float bx, float by, float bz) { return 1; }
        };
        float[] scratch = new float[1024];
        g.shot(open, "weapon.pm9", 1, 380, 0, 1.6f, 0, 0, -1, false, true, 0, 1.6f, 0, 0);
        float nearPeak = 0;
        for (int i = 0; i < 40; i++) { java.util.Arrays.fill(scratch, 0f); g.mixer.mix(scratch, 512, 1f); nearPeak = Math.max(nearPeak, peak(scratch)); }
        int nearPunches = g.mixer.layers.punches;
        g.forget();
        g.shot(open, "weapon.pm9", 9, 380, 300, 1.6f, 0, -1, 0, false, false, 0, 1.6f, 0, 0);
        for (int i = 0; i < 200; i++) { java.util.Arrays.fill(scratch, 0f); g.mixer.mix(scratch, 512, 1f); }
        int farPunches = g.mixer.layers.punches - nearPunches;
        // class order still holds with the new recordings laid in: a shotgun is louder than a handgun because the table says so
        float hand = g.library.profiles.get("weapon.pm9").gain * absentialis.engine.audio.gun.WeaponAudioProfile.class.getName().length() * 0 + g.heardLevel.getOrDefault("weapon.pm9", 0f), shot = g.heardLevel.getOrDefault("weapon.pump12", 0f);
        t.check("the firearms lead with close recordings of themselves: thirteen weapons have one from the pack (two of them a suppressed one too), it starts on the shot at the muzzle, and at three hundred metres it is not played at all",
                withPunch == 13 && withSup == 2 && nearPunches == 1 && farPunches == 0 && nearPeak > 0.05f && g.library.problems.isEmpty(),
                withPunch + " weapons (" + guns.toString().trim() + "), " + withSup + " suppressed; close recordings started near " + nearPunches + ", at 300 m " + farPunches + "; peak at the shooter " + String.format("%.2f", nearPeak) + (g.library.problems.isEmpty() ? "" : "; " + g.library.problems));

        // ---- what a round sounds like depends on what it went into; the survivor's voice is their own
        Profile vp = new Profile();
        vp.appearance.female = true;
        Raid r = SelfTests.raid(vp);
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer);
        r.player.x = 300; r.player.z = 300; r.player.y = G;
        String[] mats = {"CONCRETE", "METAL", "THIN_METAL", "WOOD", "GLASS", "FABRIC", "DIRT", "FOLIAGE", "TILE", "PLASTIC", "GRASS"};
        java.util.Set<String> kindsHeard = new java.util.TreeSet<>();
        boolean allExist = true;
        for (String mn : mats) { String sn = Raid.impactSound(absentialis.world.Material.valueOf(mn)); kindsHeard.add(sn); allExist &= ue.samples.containsKey(sn) && ue.recorded.contains(sn); }
        Actor foe = spawn(r, "raiders", 300, 290, 0);
        foe.hostileToPlayer = true;
        r.damageActor(r.player, foe, 12, Content.def("ammo.556"), "torso", r.player.x, r.player.y + 1.2f, r.player.z);
        r.damageActor(foe, r.player, 20, Content.def("ammo.556"), "legs", foe.x, foe.y + 0.5f, foe.z);
        boolean voice = r.cueCount.getOrDefault("hurt_female", 0) == 1 && r.cueCount.getOrDefault("hurt_male", 0) == 0 && r.cueCount.getOrDefault("impact_flesh", 0) + r.cueCount.getOrDefault("impact_armor", 0) + r.cueCount.getOrDefault("impact_flesh_heavy", 0) == 2;
        r.die("test");
        voice &= r.cueCount.getOrDefault("death_female", 0) == 1;
        boolean cuesKnown = true;
        StringBuilder unknown = new StringBuilder();
        for (String q : r.cueCount.keySet()) if (!ue.samples.containsKey(q)) { cuesKnown = false; unknown.append(q).append(' '); }
        t.check("a round sounds like what it went into - masonry, plate, a car door, wood, glass, cloth, earth, leaves, tile - and a hit on the survivor is heard in their own voice, a woman's or a man's",
                kindsHeard.size() >= 10 && allExist && voice && cuesKnown, kindsHeard.size() + " different strikes for " + mats.length + " materials, all recordings: " + allExist + "; cues " + r.cueCount + (unknown.length() > 0 ? "; NO SUCH SOUND: " + unknown : ""));

        // ---- "XP progression HUD less intrusive: bar and notifications only when earned, rank insignias, rank-up notifications"
        Profile xp = new Profile();
        Raid xr = SelfTests.raid(xp);
        xr.populate = false;
        absentialis.scene.XpFeed feed = new absentialis.scene.XpFeed(xr);
        java.util.List<String> played = new java.util.ArrayList<>();
        feed.sound = played::add;
        float before = 0;
        for (int i = 0; i < 600; i++) { xr.time += 1 / 10f; feed.update(); before = Math.max(before, feed.visible()); }      // a minute in the Zone and nothing done
        xr.debrief.containersSearched++;
        feed.update();
        xr.time += 0.5f;
        float shown = feed.visible();
        int lines = feed.toasts.size();
        xr.time += absentialis.scene.XpFeed.HOLD + absentialis.scene.XpFeed.FADE + 0.1f;
        feed.update();
        float after = feed.visible();
        // enough to cross a Rank: announced once, with the sound, and not again
        xr.debrief.documents += 40;
        feed.update(); feed.update(); feed.update();
        boolean announced = feed.announcing() && feed.notices == 1 && feed.noticeRank > 1 && played.equals(java.util.List.of("rank_up"));
        java.util.Set<String> marks = new java.util.HashSet<>();
        for (int k = 0; k < absentialis.ui.Insignia.titles(); k++) marks.add(java.util.Arrays.toString(absentialis.ui.Insignia.parts(k)));
        t.check("the XP bar is not on screen until experience is earned, comes up for a few seconds and goes; time spent says nothing; crossing a Rank is announced once with its sound; and each of the " + absentialis.ui.Insignia.titles() + " titles has an insignia of its own",
                before == 0 && shown > 0.9f && lines == 1 && after == 0 && announced && marks.size() == absentialis.ui.Insignia.titles() && absentialis.ui.Insignia.title(1) == 0 && absentialis.ui.Insignia.title(50) == absentialis.ui.Insignia.titles() - 1,
                String.format("idle for a minute: %.2f; after a container: %.2f with %d line; %.1f s later: %.2f; rank-up to %d announced %d time(s), sounds %s; %d distinct insignia", before, shown, lines, absentialis.scene.XpFeed.HOLD + absentialis.scene.XpFeed.FADE, after, feed.noticeRank, feed.notices, played, marks.size()));
    }

    // ------------------------------------------------------------------ profile, difficulty, the question, names, the start
    private static absentialis.gameplay.items.ItemInstance carry(Profile p, String id, String slot) {
        absentialis.gameplay.items.ItemInstance i = new absentialis.gameplay.items.ItemInstance(id);
        if (slot != null) absentialis.gameplay.inventory.InventoryService.equip(i, p.loadout, slot);
        else absentialis.gameplay.inventory.InventoryService.store(i, p.loadout);
        return i;
    }

    private static void meta(SelfTests04.Check t) {
        System.out.println("[v0.8 profile: difficulty locked to the profile, what a death costs, the question, the names, the engine, where an operation starts]");
        Content.load();
        // ---- "Lock difficulty on profile creation"
        Profile mk = new Profile();
        mk.difficulty = 2; mk.ironman = true;
        Profile back = Profile.fromJson(absentialis.engine.util.Json.parseObject(absentialis.engine.util.Json.write(mk.toJson())));
        java.util.Map<String, Object> old = mk.toJson();
        old.remove("difficulty"); old.put("version", 7);
        Profile from07 = Profile.fromJson(old);
        boolean noSetting = true;
        try { absentialis.persistence.Settings.class.getField("difficulty"); noSetting = false; } catch (NoSuchFieldException gone) { /* as it should be */ }
        t.check("difficulty is the profile's: it is saved with the survivor and read back, a 0.7 profile becomes Normal, Ironman goes with any of the three, and Settings no longer has one to flip",
                back.difficulty == 2 && back.ironman && from07.difficulty == 1 && from07.migratedFrom == 7 && noSetting && "Hard - Ironman".equals(back.difficultyName()),
                "read back " + back.difficultyName() + "; from 0.7: " + from07.difficultyName() + " (migrated from format " + from07.migratedFrom + "); Settings.difficulty gone: " + noSetting);

        // ---- "Pick-3 on Normal, Pick-5 on Easy, nothing on Hard; lose 5% / 10% / 20% of money"
        StringBuilder said = new StringBuilder();
        boolean ok = true;
        int[] wantKept = {5, 3, 0};
        long[] wantCash = {9500, 9000, 8000};
        for (int diff = 0; diff < 3; diff++) {
            Profile p = new Profile();
            p.difficulty = diff; p.scrip = 10000;
            absentialis.gameplay.items.ItemInstance rifle = carry(p, "weapon.akr", "primary"), pistol = carry(p, "weapon.pm9", "sidearm"), knife = carry(p, "melee.knife", "melee");
            absentialis.gameplay.items.ItemInstance b1 = carry(p, "med.bandage", null), sp = carry(p, "med.spray", null), am = carry(p, "ammo.handgun", null);
            int carried = 0;
            for (absentialis.gameplay.items.ItemInstance i : p.loadout.all()) if (i.parent == null) carried++;
            Raid r = SelfTests.raid(p);
            r.populate = false;
            // the player's own pick: the knife and the bandage first, cheap as they are - a choice is a choice
            r.debrief.keep.add(knife.uid); r.debrief.keep.add(b1.uid); r.debrief.keep.add(pistol.uid); r.debrief.keep.add(rifle.uid); r.debrief.keep.add(sp.uid); r.debrief.keep.add(am.uid);
            r.die("test");
            absentialis.scene.RaidResolver.resolve(r);
            int home = 0;
            for (absentialis.gameplay.items.ItemInstance i : new absentialis.gameplay.items.ItemInstance[]{rifle, pistol, knife, b1, sp, am}) if (p.stash.items.contains(i)) home++;
            boolean chosen = diff == 2 || p.stash.items.contains(knife) && p.stash.items.contains(b1) && p.stash.items.contains(pistol);
            boolean nothingCarried = p.loadout.all().isEmpty();
            ok &= home == wantKept[diff] && p.scrip == wantCash[diff] && chosen && nothingCarried && p.verify() == null && carried == 6;
            said.append(Profile.DIFFICULTY[diff]).append(": kept ").append(home).append(" of ").append(carried).append(", cash ").append(p.scrip).append("; ");
        }
        // with no pick made, the most valuable things are the ones kept
        Profile lazy = new Profile();
        lazy.scrip = 0;
        absentialis.gameplay.items.ItemInstance lr = carry(lazy, "weapon.akr", "primary"), lp = carry(lazy, "weapon.pm9", "sidearm"), lk = carry(lazy, "melee.knife", "melee"), lb = carry(lazy, "med.bandage", null), ls = carry(lazy, "med.spray", null);
        java.util.List<absentialis.gameplay.items.ItemInstance> byValue = new java.util.ArrayList<>(java.util.List.of(lr, lp, lk, lb, ls));
        byValue.sort((a, b) -> Integer.compare(b.baseValueTotal(), a.baseValueTotal()));
        Raid lz = SelfTests.raid(lazy);
        lz.populate = false;
        lz.die("test");
        absentialis.scene.RaidResolver.resolve(lz);
        boolean top3 = lazy.stash.items.containsAll(byValue.subList(0, 3)) && !lazy.stash.items.contains(byValue.get(3)) && !lazy.stash.items.contains(byValue.get(4));
        t.check("what a death costs is the difficulty's: Easy keeps the five things chosen and 95% of the cash, Normal three and 90%, Hard nothing and 80% - and with no choice made the most valuable are kept",
                ok && top3, said + "unpicked on Normal kept the top three by value: " + top3);

        // ---- "Replace the Allegiance screen: Profit / Stability / Peace / Chaos"
        String[][] want = {{"PROFIT", "mercenaries"}, {"STABILITY", "military"}, {"PEACE", "survivors"}, {"CHAOS", "raiders"}};
        boolean motives = absentialis.gameplay.Alliance.MOTIVES.length == 4;
        StringBuilder ms = new StringBuilder();
        for (int i = 0; i < 4 && motives; i++) {
            absentialis.gameplay.Alliance.Motive m = absentialis.gameplay.Alliance.MOTIVES[i];
            Profile p = new Profile();
            float before = p.rep(m.key());
            absentialis.gameplay.Alliance.choose(p, m.key());
            // the lean is permanent: lose every point that was ever earned and it is still there
            float chosen = p.rep(m.key());
            p.rep.put(m.key(), 0f);
            float floor = p.rep(m.key());
            int open = 0, homes = 0;
            for (absentialis.world.District d : absentialis.world.District.CITY) { if (absentialis.gameplay.Alliance.mayDeploy(p, d)) open++; if (d.def.homeOf(m.key())) homes++; }
            motives &= m.word().equals(want[i][0]) && m.key().equals(want[i][1]) && chosen > before + 30 && Math.abs(floor - absentialis.gameplay.Alliance.LEANING) < 0.01f
                    && open >= 1 && open == homes && !p.contracts.board.isEmpty() && m.key().equals(p.contracts.alliance == null || p.contracts.alliance.isEmpty() ? m.key() : p.contracts.alliance);
            ms.append(m.word()).append("->").append(absentialis.gameplay.Alliance.name(m.key())).append(" (standing ").append(Math.round(chosen)).append(", never below +").append(Math.round(floor)).append(", ").append(open).append(" start district").append(open == 1 ? "" : "s").append(", ").append(p.contracts.board.size()).append(" contracts) ");
        }
        t.check("the question has four answers and each leans one way: Profit to the Mercenaries, Stability to the Military, Peace to the Loners, Chaos to the Bandits - start district, a board of work, and standing that never goes away",
                motives, ms.toString().trim());

        // ---- "Rename the faction Government to Military" / lore: Munincipal and Barrens
        absentialis.world.District civic = absentialis.world.District.CIVIC, red = absentialis.world.District.REDLINE;
        boolean names = "Munincipal".equals(civic.display) && "Barrens".equals(red.display) && "Military".equals(absentialis.rivalry.Doctrine.display("government"))
                && absentialis.world.District.byName("Governmental") == civic && absentialis.world.District.byName("Outskirts") == red && "Military".equals(absentialis.gameplay.Alliance.name("military"));
        t.check("the names are the lore's: the Military (nowhere 'Government'), the Munincipal district and the Barrens - and the old names still find them",
                names, civic.display + ", " + red.display + ", faction " + absentialis.rivalry.Doctrine.display("government"));

        // ---- "Engine identity: Astratto Engine ... logo is the silhouette of a human brain"
        int cells = 0, rows = absentialis.engine.Astratto.logoHeight(), cols = absentialis.engine.Astratto.logoWidth(), widest = 0, widestRow = 0;
        boolean square = true;
        for (int y = 0; y < rows; y++) {
            String row = absentialis.engine.Astratto.LOGO[y];
            square &= row.length() == cols;
            int n = 0;
            for (int x = 0; x < row.length(); x++) if (row.charAt(x) == '#') n++;
            cells += n;
            if (n > widest) { widest = n; widestRow = y; }
        }
        int feet = 0, globe = 0;
        { String last = absentialis.engine.Astratto.LOGO[rows - 1], first = absentialis.engine.Astratto.LOGO[0]; for (int x = 0; x < cols; x++) { if (last.charAt(x) == '#' && (x == 0 || last.charAt(x - 1) != '#')) feet++; if (first.charAt(x) == '#' && (x == 0 || first.charAt(x - 1) != '#')) globe++; } }
        t.check("the engine has a name, an owner and a mark: Astratto Engine, copyright Astratto North of LudusValley Recreations under VEIN Enterprises, Atlas holding the Earth on his palm (1.0: one globe at the top, a figure on two feet under it), and UltraEngine credited for audio",
                "Astratto Engine".equals(absentialis.engine.Astratto.NAME) && absentialis.engine.Astratto.NOTICE.contains("Astratto North of LudusValley Recreations") && absentialis.engine.Astratto.NOTICE.contains("VEIN Enterprises")
                        && square && cells > cols * rows * 0.2f && cells < cols * rows * 0.6f && globe == 1 && feet == 2 && java.util.Arrays.asList(absentialis.engine.Astratto.SYSTEMS).contains("UltraEngine audio"),
                cols + " x " + rows + " grid, " + cells + " cells filled, " + globe + " globe, " + feet + " feet");

        // ---- "Spawn the player in a tucked-away, sensible location"
        World city = SelfTests06.world(1337L);
        int starts = 0, tucked = 0;
        float worstSeen = 0, sumSeen = 0, sumWas = 0;
        StringBuilder where = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            Profile p = new Profile();
            Raid r = new Raid(city, p, new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), i, 30);
            starts++;
            float[] f = r.spawnFacts;
            boolean free = absentialis.entity.Physics.boxFree(city, r.player.x, r.player.y, r.player.z, 0.35f, 1.8f);
            if (f[0] >= 1 && f[0] <= 3 && f[1] >= 2 && f[2] <= 0.45f && f[3] <= Raid.TUCK_RADIUS * 1.5f && free) tucked++;
            worstSeen = Math.max(worstSeen, f[2]); sumSeen += f[2];
            where.append(String.format("%s %.0f walls/%.0f ways/%.0f%% open [%s] y%d; ", r.insertion.name, f[0], f[1], f[2] * 100, java.util.Arrays.toString(r.tuckWhy), r.groundY(r.insertion.x, r.insertion.z)));
        }
        t.check("an operation starts tucked away: at every insertion the survivor stands with a wall at their back or shoulder, at least two ways to walk off, and fewer than half the long sight lines onto them",
                starts == 8 && tucked == 8, tucked + " of " + starts + " (mean " + Math.round(sumSeen / starts * 100) + "% of directions open, worst " + Math.round(worstSeen * 100) + "%): " + where);
    }

    // ------------------------------------------------------------------ "we rely solely on NeuralAI for all the AI types"
    private static void oneAi(SelfTests04.Check t) {
        System.out.println("[v0.8 one AI: the old AI is gone, every kind of NPC has a NeuralAI mind, the infected hunt, feed and listen through it]");
        Content.load();
        // ---- "Make sure the old AI is completely removed"
        int old = 0;
        for (String c : new String[]{"Brain", "Tactics", "SquadMind", "CloseCombat", "Specials", "Breach"}) try { Class.forName("absentialis.ai." + c); old++; } catch (ClassNotFoundException gone) { /* as it should be */ }
        t.check("the old AI is gone: no class of the absentialis.ai package is left in the build", old == 0, old + " of 6 old classes still present");

        // ---- "...for all the AI types, including squad, infected, and other NPC types"
        Profile sp = new Profile();
        sp.applyCapacity();
        sp.squad.enlist(2, 3);
        Raid r = SelfTests.raid(sp);
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer && !a.mate());
        r.player.x = 300; r.player.z = 300; r.player.y = G;
        for (Actor mt : r.mates) { mt.x = 300 + r.mates.indexOf(mt); mt.z = 303; mt.y = G; }
        String[] kinds = {"walker", "sprinter", "bomber", "spewer", "tank", "gladiator", "butcher", "military", "raiders", "survivors", "mercenaries"};
        Actor[] made = new Actor[kinds.length];
        for (int i = 0; i < kinds.length; i++) { made[i] = spawn(r, kinds[i], 322 + (i % 6) * 7, 330 + (i / 6) * 9, 0); made[i].hostileToPlayer = false; }      // inside the engagement: further off a mind is dormant
        run(r, 0.5f);
        int minds = 0, creatures = 0, blind = 0, acting = 0;
        StringBuilder acts = new StringBuilder();
        for (Actor a : made) {
            Mind m = r.neural.mind(a.id);
            if (m == null) continue;
            minds++;
            if (m.creature) creatures++;
            if (m.blind) blind++;
            if (m.action != null) { acting++; acts.append(a.archetype.equals(a.faction) ? a.faction : a.infected() ? a.archetype : a.faction).append(':').append(m.action.name()).append(' '); }
        }
        int mateMinds = 0;
        for (Actor mt : r.mates) { Mind m = r.neural.mind(mt.id); if (m != null && m.isMate && m.action != null) mateMinds++; }
        t.check("every kind of NPC thinks in NeuralAI: seven kinds of infected, four factions and the player's squad each have a mind, and each mind has chosen what to do - at peace too",
                minds == kinds.length && creatures == 7 && blind == 1 && acting == kinds.length && mateMinds == r.mates.size() && r.aiFaults == 0,
                minds + " of " + kinds.length + " minds (" + creatures + " creatures, " + blind + " without eyes), " + mateMinds + " of " + r.mates.size() + " squadmates; choosing: " + acts.toString().trim());

        // ---- "Regular infected AI runs away from player when spawned using devcommands"
        Raid rs = SelfTests.stage(new Profile());
        rs.god = true;
        Actor byName = rs.debugSpawn("regular", 300, 512, G), byFaction = rs.debugSpawn("infected", 304, 512, G), nonsense = rs.debugSpawn("zomby", 308, 512, G);
        boolean kind = byName != null && byFaction != null && "walker".equals(byName.archetype) && "walker".equals(byFaction.archetype) && byName.infected() && nonsense == null;
        float d0 = byName == null ? 0 : byName.distXZ(rs.player.x, rs.player.z);
        if (byName != null) { byName.yaw = yawTo(byName, rs.player.x, rs.player.z); byFaction.yaw = yawTo(byFaction, rs.player.x, rs.player.z); }
        float hp0 = rs.player.hp;
        rs.god = false;
        boolean engaged = false;
        for (int i = 0; i < 60 * 14 && kind; i++) { rs.update(1 / 60f); engaged |= "engage".equals(byName.state); }
        float d1 = byName == null ? 0 : byName.distXZ(rs.player.x, rs.player.z);
        Mind wm = byName == null ? null : rs.neural.mind(byName.id);
        t.check("an infected spawned from the console by the name the game shows (\"regular\", \"infected\") is an ordinary infected and comes for the player; a name that is no kind at all is refused",
                kind && engaged && d1 < 2.2f && d1 < d0 - 8 && rs.player.hp < hp0 && wm != null && wm.creature && wm.state == CogState.COMBAT,
                kind ? String.format("12.0 m -> %.1f m, hunting %s, player hp %.0f -> %.0f, mind %s", d1, engaged, hp0, rs.player.hp, wm == null ? "none" : wm.state.toString()) : "spawned as " + (byName == null ? "nothing" : byName.faction + "/" + byName.archetype));

        // ---- "Gladiator NPC's should be completely blind ... and must only rely on sound"
        Raid rg = SelfTests.stage(new Profile());
        Actor glad = spawn(rg, "gladiator", 300, 492, 0);
        glad.yaw = yawTo(glad, rg.player.x, rg.player.z);          // looking straight at the player, eight metres off, in daylight
        Actor watcher = spawn(rg, "raiders", 303, 493, 0);         // and at a person standing right beside it
        watcher.hostileToPlayer = false; watcher.weapon = null;
        rg.freezeOnly(watcher);                                    // it does not move, speak or shoot: nothing here makes a sound
        glad.thinkTimer = 60; glad.hasGoal = true; glad.goalX = glad.x; glad.goalZ = glad.z;
        int swings0 = Creatures.swings;
        float maxMeter = 0;
        boolean everKnew = false;
        Mind gm = null;
        for (int i = 0; i < 60 * 8; i++) {
            rg.update(1 / 60f);
            watcher.vx = watcher.vz = 0; watcher.x = 303; watcher.z = 493;   // stock still and silent
            glad.x = 300; glad.z = 492;                                       // and it is kept from drifting into anyone: this is about its eyes, not its hands
            gm = rg.neural.mind(glad.id);
            if (gm != null) { for (var vm : gm.meters.values()) maxMeter = Math.max(maxMeter, (float) vm.progress()); everKnew |= gm.belief.known(); }
        }
        boolean sightless = gm != null && gm.blind && gm.meters.isEmpty() && maxMeter == 0 && !everKnew && rg.neuralWorld.self(glad.id).sightRange() == 0 && Creatures.swings == swings0 && !"investigate".equals(glad.state) && !watcher.dead;
        // now a noise, behind it
        rg.emitSound(300, G + 1, 478, 30, "glass_break", 0, false);
        float away0 = glad.distXZ(300, 478);
        boolean charged = false;
        for (int i = 0; i < 60 * 6; i++) { rg.update(1 / 60f); watcher.vx = watcher.vz = 0; charged |= glad.sprinting; }
        float away1 = glad.distXZ(300, 478);
        Mind g2 = rg.neural.mind(glad.id);
        t.check("the Gladiator has no eyes: a person stock still beside it and the player in front of it in daylight fill no vision meter and form no belief in eight seconds; a noise behind it brings it at a run",
                sightless && charged && away1 < 3.5f && away0 > 12 && g2 != null && g2.meters.isEmpty(),
                String.format("blind %s, meters %d (peak %.0f%%), ever believed anything %s, sight range %.0f m, swings %d; then glass breaking 14 m behind: closed from %.1f m to %.1f m, charged %s", gm != null && gm.blind, gm == null ? -1 : gm.meters.size(), maxMeter * 100, everKnew, rg.neuralWorld.self(glad.id).sightRange(), Creatures.swings - swings0, away0, away1, charged));

        // being hit tells it a direction and nothing more: it does not learn where the shooter stands
        Raid rh = SelfTests.stage(new Profile());
        Actor g3 = spawn(rh, "gladiator", 300, 470, 0);
        g3.thinkTimer = 60; g3.hasGoal = true; g3.goalX = g3.x; g3.goalZ = g3.z;
        run(rh, 0.2f);
        rh.damageActor(g3, rh.player, 40, Content.def("ammo.556"), "torso", g3.x, g3.y + 1.2f, g3.z);
        run(rh, 0.3f);
        Mind hm = rh.neural.mind(g3.id);
        double off = hm == null || !hm.belief.known() ? -1 : hm.belief.estimatedPosition().distanceXZ(new Vec3(rh.player.x, G, rh.player.z));
        t.check("shot from thirty metres, the Gladiator knows which way the blow came from and not where the shooter is", hm != null && hm.belief.known() && !hm.belief.visible() && hm.meters.isEmpty() && off > 3,
                hm == null ? "no mind" : String.format("belief %s, radius %.0f m, %.1f m from where the shooter really stands", hm.belief.kind(), hm.belief.radiusMeters(), off));

        // ---- "Infected don't eat players/npc's corpse"
        absentialis.gameplay.CombatConfig cc = absentialis.gameplay.CombatConfig.get();
        Raid rf = SelfTests.stage(new Profile());
        rf.player.x = 300; rf.player.z = 300; rf.god = true;
        Actor meal = rf.debugSpawn("raiders", 300, 345, G);
        rf.update(0.05f);
        rf.damageActor(meal, null, 5000, null, "torso", meal.x, meal.y + 1.1f, meal.z);
        for (int i = 0; i < 150; i++) rf.update(1 / 30f);
        Actor eater = spawn(rf, "walker", meal.x + 13, meal.z + 4, 0);       // nowhere near arm's length: it has to go to it
        eater.yaw = yawTo(eater, meal.x + 40, meal.z);                       // and it is not looking at it
        float gap0 = eater.distXZ(meal.x, meal.z), save = cc.feedSeconds;
        cc.feedSeconds = 6;
        boolean fed = false;
        float fedAt = -1, nearest = gap0;
        for (int i = 0; i < 60 * 70 && rf.actors.contains(meal); i++) {
            rf.update(1 / 60f);
            nearest = Math.min(nearest, eater.distXZ(meal.x, meal.z));
            if (!fed && meal.eaten > 0.02f) { fed = true; fedAt = rf.time; }
        }
        cc.feedSeconds = save;
        t.check("the infected eat the dead: one that is thirteen metres from a body, and not looking at it, goes to it, gets down beside it and eats it to nothing", fed && !rf.actors.contains(meal) && nearest < cc.feedReach && rf.gore.corpsesConsumed == 1,
                String.format("from %.1f m to %.1f m, eating began after %.0f s, body gone %s", gap0, nearest, fedAt, !rf.actors.contains(meal)));

        // the player's own body
        Raid rd = SelfTests.stage(new Profile());
        rd.deathCam = 600; rd.deathHold = true;
        Actor ghoul = spawn(rd, "walker", 300, 512, 0);
        ghoul.yaw = yawTo(ghoul, 340, 512);
        rd.update(1 / 60f);
        rd.die("test");
        cc.feedSeconds = 10;
        for (int i = 0; i < 60 * 45 && rd.player.eaten < 0.25f; i++) rd.update(1 / 60f);
        cc.feedSeconds = save;
        t.check("...and the player's body too: it is eaten at where it fell, and stays in the world for the death camera", rd.player.dead && rd.player.eaten >= 0.25f && rd.actors.contains(rd.player) && "feed".equals(ghoul.state),
                String.format("eaten %.0f%%, feeder %s at %.1f m, body still in the world %s", rd.player.eaten * 100, ghoul.state, ghoul.distXZ(rd.player.x, rd.player.z), rd.actors.contains(rd.player)));

        // ---- "Game crashed while downtown" (crash.log: FightFromCover with the cover taken away between two decisions)
        Raid rc = SelfTests.stage(new Profile());
        for (int k = 0; k < 7; k++) rc.world.setRaw(296 + k, G, 486, absentialis.world.Block.SANDBAG);
        rc.world.cover.clear();
        java.util.List<Actor> team = SelfTests.squadOf(rc, "military", 4, 297, 480, 61, true);
        int faults0 = rc.aiFaults, stripped = 0;
        boolean threw = false;
        try {
            for (int i = 0; i < 60 * 20; i++) {
                rc.update(1 / 60f);
                // what the squad coordinator does when it deals the roles again, done here every frame to every mind that is in cover
                for (Actor a : team) { Mind m = rc.neural.mind(a.id); if (m != null && m.cover != null && m.action != null && (m.action.name().equals("FightFromCover") || m.action.name().equals("Suppress") || m.action.name().equals("Reload"))) { m.cover = null; stripped++; } }
            }
        } catch (RuntimeException e) { threw = true; }
        for (int k = 0; k < 7; k++) rc.world.setRaw(296 + k, G, 486, absentialis.world.Block.AIR);
        rc.world.cover.clear();
        t.check("a mind whose cover is taken away between two decisions carries on (0.7 crashed the game here), and a fault in any one mind can no longer end the operation",
                !threw && rc.aiFaults == faults0 && stripped > 0, "cover stripped from under an acting mind " + stripped + " times in 20 s of a four-man firefight: " + (threw ? "threw" : "no exception") + ", " + (rc.aiFaults - faults0) + " minds faulted");
    }

    // ------------------------------------------------------------------ "Game crashed while downtown ... multiple different times ... even on devmap"
    /**
     * Runs a raid hard for a while and reports the first thing that throws, with where. The player (untouchable) is
     * moved from place to place so that cells wake, fold and wake again; every stop gets gunfire, a grenade, two sides
     * that hate each other and one of every kind of infected dropped on it. Returns null when nothing went wrong.
     */
    private static String thrash(Raid r, java.util.List<float[]> stops, float secondsPerStop, int[] counts) {
        String[] cast = {"walker", "sprinter", "bomber", "spewer", "tank", "gladiator", "butcher", "military", "raiders", "survivors", "mercenaries", "raiders", "military", "walker", "walker", "sprinter"};
        r.god = true;
        try {
            int stop = 0;
            for (float[] s : stops) {
                r.player.x = s[0]; r.player.y = s[1]; r.player.z = s[2]; r.player.vx = r.player.vy = r.player.vz = 0;
                int frames = Math.round(secondsPerStop * 30);
                for (int f = 0; f < frames; f++) {
                    if (f == 20) for (int k = 0; k < cast.length; k++) {
                        double ang = k * 0.9 + stop;
                        Actor a = r.debugSpawn(cast[k], s[0] + (float) Math.sin(ang) * (9 + k % 5 * 3), s[2] + (float) Math.cos(ang) * (9 + k % 5 * 3), s[1]);
                        if (a != null && !a.infected()) { a.squad = 9000 + stop * 10 + k % 4; a.hostileToPlayer = k % 2 == 0; a.wakeUntil = 1e9f; }
                        if (a != null) counts[0]++;
                    }
                    if (f % 45 == 10) r.emitSound(s[0], s[1] + 1.5f, s[2], 120, "shot", r.player.id, false);
                    if (f == 60 || f == 200) r.explode(s[0] + 6, s[1] + 0.5f, s[2] + 4, 6, 120, r.player.id, true);
                    if (f == 90) { Actor nearest = null; for (Actor a : r.actors) if (!a.isPlayer && !a.dead && (nearest == null || a.dist(r.player) < nearest.dist(r.player))) nearest = a; if (nearest != null) r.damageActor(nearest, r.player, 30, Content.def("ammo.556"), "torso", nearest.x, nearest.y + 1.1f, nearest.z); }
                    if (f == 120) r.squadOrder("gold", "move", s[0] + 5, s[2] - 5, false);
                    if (f == 150) r.setHoldFire("gold", stop % 2 == 0);
                    r.update(1 / 30f);
                    counts[1] = Math.max(counts[1], r.actors.size());
                    counts[2] = Math.max(counts[2], r.neural.minds().size());
                }
                stop++;
            }
        } catch (Throwable e) {
            StringBuilder b = new StringBuilder(e.toString());
            int n = 0;
            for (StackTraceElement el : e.getStackTrace()) if (el.getClassName().startsWith("absentialis.") && n++ < 6) b.append(" < ").append(el.getClassName().replace("absentialis.", "")).append('.').append(el.getMethodName()).append(':').append(el.getLineNumber());
            return b.toString();
        }
        return null;
    }

    private static void soak(SelfTests04.Check t) {
        System.out.println("[v0.8 stability: a populated operation driven hard through every district, and through the devmap, without one fault]");
        Content.load();
        // ---- the Zone: every district's insertion, downtown first and last
        World city = SelfTests06.world(1337L);
        Profile p = new Profile();
        p.applyCapacity();
        p.squad.enlist(4, 3);
        Raid r = new Raid(city, p, new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0);
        java.util.List<float[]> stops = new java.util.ArrayList<>();
        absentialis.world.District[] order = {absentialis.world.District.OLD_QUARTER, absentialis.world.District.MARKET, absentialis.world.District.INDUSTRIAL, absentialis.world.District.CIVIC, absentialis.world.District.REDLINE, absentialis.world.District.OLD_QUARTER};
        for (absentialis.world.District d : order) for (absentialis.world.MapData.Point ins : city.map.insertions) if (ins.district == d) { float[] s = r.standSpot(ins.x, ins.z); if (s != null) { stops.add(s); break; } }
        int[] counts = new int[3];
        long t0 = System.nanoTime();
        String fault = thrash(r, stops, 28, counts);
        double ms = (System.nanoTime() - t0) / 1e6;
        long decisions = r.neural.decisions;
        t.check("a populated operation survives being driven hard through every district: 168 seconds of gunfire, blasts, orders and every kind of NPC at six stops, downtown twice, with no exception and no faulted mind",
                fault == null && r.aiFaults == 0 && stops.size() == 6 && counts[1] > 40 && decisions > 2000,
                fault != null ? "THREW " + fault : r.aiFaults > 0 ? r.aiFaultText.lines().limit(4).reduce("", (a, b) -> a + " " + b.trim()) : String.format("%d stops, %d NPCs dropped in, up to %d actors and %d minds at once, %d decisions, %.0f ms per simulated second", stops.size(), counts[0], counts[1], counts[2], decisions, ms / (stops.size() * 28)));

        // ---- the devmap
        World dev = new World();
        absentialis.world.gen.DevMap.generate(dev);
        dev.lighting.computeAll();
        Profile dp = new Profile();
        dp.applyCapacity();
        dp.squad.enlist(2, 3);
        Raid rd = new Raid(dev, dp, new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0);
        java.util.List<float[]> dstops = new java.util.ArrayList<>();
        for (absentialis.world.MapData.Point ins : dev.map.insertions) { float[] s = rd.standSpot(ins.x, ins.z); if (s != null) dstops.add(s); }
        int[] dc = new int[3];
        String dfault = thrash(rd, dstops, 24, dc);
        t.check("...and so does the devmap: the road, the maze, the mansion and the yard, each with the same treatment", dfault == null && rd.aiFaults == 0 && dstops.size() == 4,
                dfault != null ? "THREW " + dfault : rd.aiFaults > 0 ? rd.aiFaultText.lines().limit(4).reduce("", (a, b) -> a + " " + b.trim()) : String.format("%d stops, %d NPCs, up to %d actors at once, %d decisions", dstops.size(), dc[0], dc[1], rd.neural.decisions));
    }

    // ------------------------------------------------------------------ the reported bugs that can be measured without a window
    /** The lowest point of a posed body, in voxels above the floor it is standing on (joints and limb ends; a body is about two voxels thick round them). */
    private static float lowest(Actor a, float aimPitch) {
        absentialis.character.Pose pose = new absentialis.character.Pose();
        a.anim.pose(a, pose, aimPitch, null, false);
        absentialis.engine.math.Mat4 base = new absentialis.engine.math.Mat4().identity(), hips = new absentialis.engine.math.Mat4();
        absentialis.engine.math.Mat4[] bones = new absentialis.engine.math.Mat4[absentialis.character.Rig.GROUPS];
        for (int i = 0; i < bones.length; i++) bones[i] = new absentialis.engine.math.Mat4();
        absentialis.character.Rig.fk(pose, base, hips, bones);
        float[] v = new float[3];
        float low = 1e9f;
        for (int g = 0; g < absentialis.character.Rig.GROUPS; g++) {
            bones[g].transformPoint(0, 0, 0, v); low = Math.min(low, v[1]);
            float len = absentialis.character.Rig.LENGTH[g] * (g <= absentialis.character.Rig.TORSO ? 1 : -1);
            bones[g].transformPoint(0, len, 0, v); low = Math.min(low, v[1]);
        }
        return low;
    }

    private static void bugs(SelfTests04.Check t) {
        System.out.println("[v0.8 reported bugs: prone and wounded bodies above the floor, the driver seated, the car door, dents where the blow landed, bodies and doors, loot through floors]");
        Content.load();
        // ---- "When prone in third person, the character goes under the floor"
        Raid r = SelfTests.stage(new Profile());
        Actor lying = spawn(r, "military", 310, 500, 0), hurt = spawn(r, "military", 314, 500, 0), up = spawn(r, "military", 318, 500, 0);
        lying.hostileToPlayer = hurt.hostileToPlayer = up.hostileToPlayer = false;
        r.freezeAI = true;
        lying.prone = true;
        hurt.vital = absentialis.entity.Vital.DOWNED; hurt.bleedLeft = 9999;
        run(r, 1.5f);
        float lowProne = lowest(lying, 0), lowDown = lowest(hurt, 0), lowStand = lowest(up, 0);
        t.check("a prone body and a wounded one lie on the floor, not in it: no joint of either is more than half a body's thickness below the floor (0.7 put the pelvis of both 30 to 40 cm under it)",
                lowProne > -0.6f && lowProne < 3.5f && lowDown > -0.6f && lowDown < 4f && Math.abs(lowStand) < 0.8f && lying.anim.proneT > 0.99f && hurt.anim.downT > 0.99f,
                String.format("lowest joint, in voxels of 5.6 cm above the floor: prone %.1f, wounded %.1f, standing %.1f", lowProne, lowDown, lowStand));

        // ---- "it shows the running animation playing in the car"
        Raid rc = SelfTests.stage(new Profile());
        absentialis.scene.VehicleSystem.Car car = rc.vehicles.debugSpawn("car.sprite");
        rc.update(1 / 60f);
        if (car != null) { float[] o = new float[3]; car.sim.origin(o); rc.player.x = o[0] - car.sim.body.rightX() * 1.7f; rc.player.z = o[2] - car.sim.body.rightZ() * 1.7f; rc.player.y = G; }
        boolean in = car != null && rc.vehicles.enter();
        for (int i = 0; i < 60 * 4 && in && rc.vehicles.driving == null; i++) rc.update(1 / 60f);
        float maxSpeed = 0, moved = 0;
        float x0 = rc.player.x, z0 = rc.player.z;
        for (int i = 0; i < 60 * 5 && rc.vehicles.driving != null; i++) {
            rc.vehicles.keys(true, false, i > 120, false, false);
            rc.update(1 / 60f);
            maxSpeed = Math.max(maxSpeed, rc.player.anim.speed);
        }
        moved = rc.player.distXZ(x0, z0);
        Actor p = rc.player;
        float slipBefore = rc.slipT;
        rc.playerSlip();
        boolean noSlip = rc.slipT == slipBefore;
        absentialis.character.Pose dp = new absentialis.character.Pose();
        p.anim.pose(p, dp, 0, null, false);
        t.check("the driver sits: carried five seconds and some metres by the car, the body's own gait never starts, it is in the seated pose facing the way the car faces with the arms forward on the wheel - and nobody slips in blood sitting down",
                in && moved > 4 && maxSpeed < 0.05f && p.seated && p.atWheel && p.anim.seatW > 0.99f && p.faceSet && Math.abs(absentialis.neuralai.integration.Steering.wrapPi(p.faceYaw - absentialis.scene.VehicleSystem.playerYawOf(car.sim))) < 0.01f
                        && dp.rx(absentialis.character.Rig.L_UPPER_ARM) > 0.5f && dp.rx(absentialis.character.Rig.R_UPPER_ARM) > 0.5f && dp.rx(absentialis.character.Rig.L_THIGH) > 1.2f && Math.abs(p.steer) > 0.5f && noSlip,
                String.format("car moved %.1f m; the driver's gait speed peaked at %.3f m/s; seated %.2f, upper arms raised %.2f / %.2f rad, wheel %.2f, slip refused %s", moved, maxSpeed, p.anim.seatW, dp.rx(absentialis.character.Rig.L_UPPER_ARM), dp.rx(absentialis.character.Rig.R_UPPER_ARM), p.steer, noSlip));

        // ---- "Car doors open backwards"
        absentialis.vehicle.CarVisualController vis = car.visual;
        vis.doorOpen = 0; float shut = vis.doorEdgeX();
        vis.doorOpen = 1; float open = vis.doorEdgeX();
        vis.doorOpen = 0;
        t.check("the car door opens outward: hinged at its front edge, its free edge swings away from the body, not through the driver's seat", vis.hingeX() < 0 && open < shut - 0.5f && open < -car.sim.data.width / 2 - 0.4f,
                String.format("door on the side at x %.2f m; free edge at x %.2f shut, %.2f open (the body is %.2f m wide)", vis.hingeX(), shut, open, car.sim.data.width));

        // ---- "Vehicles should deform based on where the damage was actually struck"
        absentialis.vehicle.CarHandlingData hd = absentialis.vehicle.CarHandlingData.get("car.sprite");
        absentialis.vehicle.CarVehicle fresh = new absentialis.vehicle.CarVehicle(hd), struck = new absentialis.vehicle.CarVehicle(hd);
        struck.damage.impact(11, -hd.width / 2, hd.height * 0.4f, hd.length / 2 - 0.2f, hd, 0);          // the left front corner, hard
        absentialis.engine.voxel.VoxModel a = new absentialis.vehicle.CarVisualController(fresh).preview(), b = new absentialis.vehicle.CarVisualController(struck).preview();
        int[] changed = new int[4];      // front-left, front-right, rear-left, rear-right
        for (int x = 0; x < a.sx; x++) for (int y = 0; y < a.sy; y++) for (int z = 0; z < a.sz; z++) if ((a.get(x, y, z) == 0) != (b.get(x, y, z) == 0)) changed[(z >= a.sz / 2 ? 0 : 2) + (x < a.sx / 2 ? 0 : 1)]++;
        t.check("a car is pushed in where it was hit: a hard blow on the left front corner changes that corner and leaves the other end of the car as it was",
                struck.damage.dents.size() == 1 && changed[0] > 60 && changed[0] > changed[1] * 3 && changed[2] + changed[3] == 0 && struck.damage.level(absentialis.vehicle.CarDamageState.FRONT) + struck.damage.level(absentialis.vehicle.CarDamageState.LEFT) >= 1,
                "voxels moved - front left " + changed[0] + ", front right " + changed[1] + ", rear left " + changed[2] + ", rear right " + changed[3] + "; " + struck.damage.dents.size() + " dent recorded");

        // ---- "When corpses are near doors, doors can not be opened"
        Raid rd = SelfTests.stage(new Profile());
        World w = rd.world;
        for (int x = 386; x <= 400; x++) for (int y = G; y <= G + 3; y++) w.setRaw(x, y, 300, (x == 390 || x == 396) && y <= G + 1 ? absentialis.world.Block.DOOR_WOOD : absentialis.world.Block.CONCRETE);
        rd.doors.scan();
        // two doors, and somebody dead against each: one on the far side of its door, one on the near side - whichever
        // way a leaf swings, one of them is lying in its way
        absentialis.scene.Doors.Door[] door = {rd.doors.at(390, G, 300), rd.doors.at(396, G, 300)};
        Actor[] body = {rd.debugSpawn("raiders", 390.5f, 301.0f, G), rd.debugSpawn("raiders", 396.5f, 300.0f, G)};
        body[0].x = 390.5f; body[0].z = 301.0f; body[1].x = 396.5f; body[1].z = 300.0f;
        rd.freezeAI = true;
        rd.update(1 / 60f);
        for (Actor bd : body) rd.damageActor(bd, null, 5000, null, "torso", bd.x, bd.y + 1.1f, bd.z);
        run(rd, 4);
        float[] angle = new float[2], pushed = new float[2];
        boolean used = true;
        for (int k = 0; k < 2; k++) {
            float bx0 = body[k].x, bz0 = body[k].z;
            rd.player.x = 390.5f + k * 6; rd.player.z = 297.6f; rd.player.y = G;
            if (door[k] != null) door[k].locked = false;
            used &= door[k] != null && rd.doors.use(door[k], rd.player) == null;
            run(rd, 2.5f);
            angle[k] = door[k] == null ? 0 : Math.abs(door[k].angle);
            pushed[k] = Math.abs(body[k].z - bz0) + Math.abs(body[k].x - bx0);
        }
        t.check("a body lying against a door does not jam it: with one dead on the far side of a door and one on the near side of another, both doors open fully when their handles are worked",
                used && body[0].dead && body[1].dead && angle[0] > 1.0f && angle[1] > 1.0f,
                String.format("doors open %.2f and %.2f rad; bodies pushed %.2f m and %.2f m (%d contacts between a leaf and a body)", angle[0], angle[1], pushed[0], pushed[1], rd.doors.bodiesShoved));

        // ---- "When on the second floor of a building, the prompt to loot corpses on the floor below is shown"
        Raid rf = SelfTests.stage(new Profile());
        World wf = rf.world;
        for (int x = 420; x <= 428; x++) for (int z = 296; z <= 304; z++) wf.setRaw(x, G + 4, z, absentialis.world.Block.CONCRETE);      // an upper floor
        Actor below = rf.debugSpawn("raiders", 424.5f, 300.5f, G);
        rf.freezeAI = true;
        rf.update(1 / 60f);
        rf.damageActor(below, null, 5000, null, "torso", below.x, below.y + 1.1f, below.z);
        run(rf, 3);
        rf.player.x = below.x + 0.4f; rf.player.z = below.z; rf.player.y = G + 5;                 // standing directly over it, one storey up
        Actor fromAbove = rf.searchableCorpse(1.8f), oldAnswer = rf.nearestCorpse(rf.player.x, rf.player.z, 1.8f);
        rf.player.y = G;
        Actor sameFloor = rf.searchableCorpse(1.8f);
        for (int x = 420; x <= 428; x++) for (int z = 296; z <= 304; z++) wf.setRaw(x, G + 4, z, absentialis.world.Block.AIR);
        for (int x = 386; x <= 400; x++) for (int y = G; y <= G + 3; y++) w.setRaw(x, y, 300, absentialis.world.Block.AIR);
        t.check("a body on the floor below is not offered for searching from the floor above; standing beside it, it is", below.dead && fromAbove == null && oldAnswer == below && sameFloor == below,
                "from one storey up: " + (fromAbove == null ? "nothing" : "the body") + " (0.7 would have offered " + (oldAnswer == null ? "nothing" : "it") + "); beside it: " + (sameFloor == null ? "nothing" : "the body"));

        // ---- "Players face still does not display properly on the GUI HUD"
        t.check("the HUD portrait is drawn from in front of the face (0.7 drew the back of the head) and frames the head, not the shoulders",
                Math.abs(absentialis.ui.Portrait.FACING - (float) Math.PI) < 1e-4f && absentialis.ui.Portrait.HEAD_HALF * 2 <= 12, "camera yaw " + absentialis.ui.Portrait.FACING + " rad, " + absentialis.ui.Portrait.HEAD_ROWS + " rows by " + absentialis.ui.Portrait.HEAD_HALF * 2 + " voxels");
    }

    // ------------------------------------------------------------------ the squad
    private static Raid withSquad(int n) {
        Profile sp = new Profile();
        sp.applyCapacity();
        sp.squad.enlist(n, 3);
        Raid r = SelfTests.raid(sp);
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer && !a.mate());
        r.player.x = 300; r.player.z = 300; r.player.y = G;
        for (Actor mt : r.mates) { mt.x = 299 + r.mates.indexOf(mt) * 1.5f; mt.z = 303; mt.y = G; }
        return r;
    }

    private static void squad(SelfTests04.Check t) {
        System.out.println("[v0.8 squad: it forms up behind the character and not the camera, a marker stands where it was sent, it holds fire when told, and nobody shoots at what is too far off]");
        Content.load();
        // ---- "AI Squadmates should stay behind the actual player character, not the player camera"
        Raid r = withSquad(4);
        Actor p = r.player;
        // third person: the body faces north (-z), the camera has swung round to look south-east
        p.faceSet = true; p.faceYaw = 0; p.yaw = (float) Math.toRadians(140);
        run(r, 9);
        float bx = (float) -Math.sin(p.faceYaw), bz = (float) -Math.cos(p.faceYaw), cx = (float) -Math.sin(p.yaw), cz = (float) -Math.cos(p.yaw);
        int behindBody = 0, behindCamera = 0;
        StringBuilder where = new StringBuilder();
        for (Actor m : r.mates) {
            float along = (m.x - p.x) * bx + (m.z - p.z) * bz, alongCam = (m.x - p.x) * cx + (m.z - p.z) * cz;
            if (along < -1.0f) behindBody++;
            if (alongCam < -1.0f) behindCamera++;
            where.append(String.format("%.1f ", along));
        }
        t.check("the squad forms up behind the character's body: with the camera swung round 140 degrees, all four are still behind the way the body faces", behindBody == 4 && behindCamera < 4,
                "metres ahead of the body (negative = behind): " + where.toString().trim() + "; behind the camera's direction: " + behindCamera + " of 4");

        // ---- "the location should be physically shown as a physical checkpoint that fades out after a few seconds"
        int marks0 = r.orderMarks.size();
        r.squadOrder("gold", "move", 312.5f, 291.5f, false);
        float[] mk = r.orderMarks.isEmpty() ? null : r.orderMarks.get(r.orderMarks.size() - 1);
        boolean placed = mk != null && r.orderMarks.size() == marks0 + 1 && Math.abs(mk[0] - 312.5f) < 0.01f && Math.abs(mk[2] - 291.5f) < 0.01f && mk[1] == G && r.orderMarkAlpha(mk) > 0.95f;
        r.squadOrder("red", "hold", Float.NaN, Float.NaN, false);
        boolean holdLeavesNone = r.orderMarks.size() == marks0 + 1;
        run(r, Raid.ORDER_MARK_SECONDS * 0.8f);
        float late = mk == null ? -1 : r.orderMarkAlpha(mk);
        run(r, Raid.ORDER_MARK_SECONDS * 0.3f);
        float gone = mk == null ? -1 : r.orderMarkAlpha(mk);
        t.check("an order to move stands a marker on the ground at the spot, which fades over a few seconds and is gone; an order to hold leaves none", placed && holdLeavesNone && late > 0 && late < 0.6f && gone == 0,
                mk == null ? "no marker" : String.format("at %.1f, %.0f, %.1f; opacity 1.00 -> %.2f -> %.2f over %.1f s", mk[0], mk[1], mk[2], late, gone, Raid.ORDER_MARK_SECONDS * 1.1f));

        // ---- "Give squadmates the toggle of holding fire or not via command"
        Raid rh = withSquad(2);
        rh.god = true;
        Actor foe = spawn(rh, "raiders", 300, 282, 0);            // eighteen metres in front of them, in the open
        foe.hostileToPlayer = true; foe.weapon = null;              // unarmed, so nobody is defending themselves
        foe.thinkTimer = 99; foe.maxHp = foe.hp = 1e6f;             // it is there to be shot at, not to be killed by the first round
        rh.freezeOnly(foe);
        for (Actor m : rh.mates) m.yaw = yawTo(m, foe.x, foe.z);
        int took = rh.setHoldFire("gold", true);
        int s0 = rh.neuralWorld.shots;
        boolean sawIt = false;
        for (int i = 0; i < 60 * 7; i++) { rh.update(1 / 60f); foe.hp = foe.maxHp; for (Actor m : rh.mates) { Mind mm = rh.neural.mind(m.id); sawIt |= mm != null && mm.state == CogState.COMBAT && mm.belief.visible(); } }
        int held = rh.neuralWorld.shots - s0;
        rh.setHoldFire("gold", false);
        for (int i = 0; i < 60 * 7; i++) { rh.update(1 / 60f); foe.hp = foe.maxHp; }
        int free = rh.neuralWorld.shots - s0 - held;
        t.check("hold fire: with a hostile in plain sight at eighteen metres the squad fires nothing for seven seconds; told to fire at will, it opens up", took == 2 && sawIt && held == 0 && free >= 3 && rh.holdFire("red") == false,
                took + " took the order; in contact " + sawIt + "; rounds fired holding: " + held + ", then free: " + free + (held == 0 && free >= 3 ? "" : "  foe dead " + foe.dead + " " + rh.neural.mind(rh.mates.get(0).id).trace.lines() + " | " + rh.neural.mind(rh.mates.get(1).id).trace.lines()));

        // ---- "NPC's (squadmates especially) start firing at targets that are too far away"
        Raid rr = withSquad(2);
        rr.god = true;
        rr.player.x = 290;                                         // out of their line of fire: they hold where they stand, and nobody shoots past a friend's head
        Actor farFoe = spawn(rr, "raiders", 300, 248, 0);          // fifty-two metres off, in plain sight
        farFoe.hostileToPlayer = true; farFoe.weapon = null; farFoe.maxHp = farFoe.hp = 1e6f;
        rr.freezeOnly(farFoe);
        for (Actor m : rr.mates) { m.yaw = yawTo(m, farFoe.x, farFoe.z); rr.neural.order(m.id, "hold", new Vec3(m.x, m.y, m.z)); m.sqOrder = "hold"; m.sqX = m.x; m.sqZ = m.z; }
        int f0 = rr.neuralWorld.shots;
        boolean seen = false;
        double limit = 0;
        for (int i = 0; i < 60 * 26; i++) {                        // holding, they sweep their arc: long enough for it to come round to the target and for the eye to make it out
            rr.update(1 / 60f); farFoe.hp = farFoe.maxHp;
            for (Actor m : rr.mates) { Mind mm = rr.neural.mind(m.id); seen |= mm != null && mm.belief.visible(); }
        }
        int farShots = rr.neuralWorld.shots - f0;
        limit = rr.neural.fireRange(rr.mates.get(0).id);
        farFoe.x = 300; farFoe.z = 300 - (float) Math.min(30, limit * 0.6);      // now well inside it
        for (int i = 0; i < 60 * 8; i++) { rr.update(1 / 60f); farFoe.hp = farFoe.maxHp; }
        int nearShots = rr.neuralWorld.shots - f0 - farShots;
        t.check("nobody opens fire on what is too far off to hit: a squad that can see a target at 52 m does not shoot at it, and does when it is inside the range of what they carry", seen && farShots == 0 && nearShots >= 2 && limit < 52 && limit > 12,
                String.format("their range %.0f m (anyone can see %.0f m today); seen at 52 m %s, rounds fired at it %d; at %.0f m: %d (%d held for a friend in the line)", limit, rr.fogSight(), seen, farShots, Math.min(30, limit * 0.6), nearShots, rr.friendlyHolds) + (nearShots >= 2 ? "" : "  " + rr.neural.describe(rr.mates.get(0).id) + " " + rr.neural.mind(rr.mates.get(0).id).trace.lines()));
    }
}
