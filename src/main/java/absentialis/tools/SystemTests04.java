package absentialis.tools;

import absentialis.neuralai.integration.NeuralHost;
import absentialis.anatomy.VoxelBody;
import absentialis.character.Rig;
import absentialis.entity.Actor;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.squad.Roster;
import absentialis.persistence.Profile;
import absentialis.scene.InfectionSystem;
import absentialis.scene.Raid;
import absentialis.vehicle.CarHandlingData;
import absentialis.world.Block;
import absentialis.world.World;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Version 0.4 gates for the systems that are not vehicles: infection, death, fire, anatomy, combat AI, audio, squad names, the garage. */
final class SystemTests04 {
    private SystemTests04() {}

    private static final int G = World.GROUND;

    private static int solid(VoxelBody b, int... groups) { int n = 0; for (int g : groups) if (b.chunks[g] != null) n += b.chunks[g].solid; return n; }
    private static void run(Raid r, float seconds) { for (int i = 0; i < Math.round(seconds * 60); i++) r.update(1 / 60f); }

    static void run(SelfTests04.Check t) {
        infection(t);
        death(t);
        fire(t);
        anatomy(t);
        ai(t);
        misc(t);
    }

    private static void infection(SelfTests04.Check t) {
        System.out.println("[v0.4 infection: a bite, a course, a cure - or a turning]");
        ItemDef av = Content.ITEMS.get(InfectionSystem.ANTIVIRAL);
        t.check("Antiviral Medicine exists as a medical item", av != null && av.medical != null && av.medical.antiviral && av.name.equals("Antiviral Medicine"), av == null ? "missing" : av.name);
        String loot = "";
        try (java.io.InputStream in = SystemTests04.class.getResourceAsStream("/content/loot.json")) { loot = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); } catch (Exception e) { loot = ""; }
        int tables = loot.split("med\\.antiviral", -1).length - 1;
        t.check("Antiviral Medicine is common loot (in many tables)", tables >= 5, tables + " loot tables");

        Raid r = SelfTests.stage(new Profile());
        r.god = false;
        Actor walker = r.debugSpawn("walker", 330, 500, G), giant = r.debugSpawn("tank", 334, 500, G);
        r.freezeAI = true;
        t.check("the infected that bite, bite; a giant does not", r.infection.biteChance(walker, r.player) > 0.2f && r.infection.biteChance(giant, r.player) == 0 && r.infection.biteChance(walker, giant) == 0,
                String.format("walker %.2f", r.infection.biteChance(walker, r.player)));
        r.infection.bitten(r.player, walker);
        float first = r.body.infection;
        run(r, 5);
        t.check("a bite infects the player and the infection climbs untreated", first > 5 && r.body.infection > first && "BITTEN".equals(InfectionSystem.stage(first)), String.format("%.1f -> %.1f in 5 s", first, r.body.infection));
        r.infection.cure(r.player);
        run(r, 2);
        t.check("Antiviral Medicine clears it at any stage", r.body.infection == 0 && r.player.infection == 0 && r.infection.cured == 1, null);
        r.deathCam = 3.6f;                 // as in play: the body falls, and the raid waits on the player's choice
        r.player.anim = new absentialis.character.Animator();
        r.deathHold = true;
        r.infection.bitten(r.player, walker);
        r.body.infection = InfectionSystem.TURN - 0.05f;
        run(r, 1.5f);
        boolean died = r.player.dead && r.deathReason().startsWith("Turned");
        run(r, 6);
        Actor husk = r.infection.playerHusk;
        t.check("untreated, the player dies of it and rises as an infected", died && husk != null && husk.infected() && husk.turned && !husk.dead, died ? "husk " + (husk == null ? "never rose" : husk.archetype) : "still alive at " + r.body.infection);

        Raid r2 = SelfTests.stage(new Profile());
        r2.freezeAI = true;
        Actor z = r2.debugSpawn("walker", 330, 500, G);
        Actor npc = r2.debugSpawn("survivors", 320, 500, G);
        int id = npc.id;
        String name = npc.name;
        var look = npc.appearance;
        r2.infection.bitten(npc, z);
        npc.infectTreatAt = -1;
        boolean bitten = npc.infection > 0 && !npc.infected();
        npc.infection = InfectionSystem.TURN - 0.2f;
        run(r2, 2);
        t.check("an untreated NPC turns: same body, same name, now one of the infected", bitten && npc.infected() && npc.turned && "survivors".equals(npc.turnedFrom) && npc.id == id && npc.name.equals(name) && !npc.dead && npc.weapon == null && r2.infection.turnedCount == 1,
                npc.faction + " / " + npc.archetype + (look == null ? "" : ", look kept"));
        Actor carrier = r2.debugSpawn("military", 310, 500, G);
        r2.infection.bitten(carrier, z);
        carrier.infectTreatAt = r2.time + 1;
        run(r2, 3);
        t.check("an NPC carrying a dose treats their own bite", carrier.infection == 0 && !carrier.infected(), "infection " + carrier.infection);
        // squadmates can be dosed by the player
        Profile p3 = new Profile();
        absentialis.gameplay.inventory.InventoryService.store(new absentialis.gameplay.items.ItemInstance(InfectionSystem.ANTIVIRAL), p3.loadout);
        Raid r3 = SelfTests.stage(p3);
        r3.freezeAI = true;
        Actor friend = r3.debugSpawn("survivors", 301, 501, G);
        friend.hostileToPlayer = false;
        r3.infection.bitten(friend, r3.debugSpawn("walker", 340, 500, G));
        friend.infectTreatAt = -1;
        String had = r3.infection.give(friend);
        boolean none = true;
        for (var it : p3.loadout.all()) none &= !it.defId.equals(InfectionSystem.ANTIVIRAL);
        t.check("the player can give a dose to someone who was bitten, and it is used up", r3.infection.patient(4) == null && friend.infection == 0 && had.startsWith("Antiviral given") && none, had);
    }

    private static void death(SelfTests04.Check t) {
        System.out.println("[v0.4 death: the raid waits on the player's choice; Ironman is recorded]");
        Raid r = SelfTests.stage(new Profile());
        r.god = false;
        r.deathCam = 3.6f;
        r.player.anim = new absentialis.character.Animator();
        r.deathHold = true;
        r.die("Shot");
        run(r, 12);
        boolean held = r.player.dead && r.debrief.outcome == Raid.Debrief.Outcome.NONE;
        r.resolveDeath();
        t.check("after death the world keeps running until respawn / menu / quit is chosen", held && r.debrief.outcome == Raid.Debrief.Outcome.KILLED, r.debrief.outcome + " - " + r.debrief.reason);
        Profile p = new Profile();
        p.ironman = true;
        p.garage.add("car.volna");
        p.deployVehicle = "car.volna";
        Profile q = Profile.fromJson(p.toJson());
        t.check("Ironman and the garage survive a save and load", q.ironman && q.garage.contains("car.volna") && q.deployVehicle.equals("car.volna") && !Profile.fromJson(new Profile().toJson()).ironman, null);
        t.check("this build is Version 1.3", absentialis.Version.NUMBER.equals("1.3") && absentialis.Version.LABEL.contains("1.3"), absentialis.Version.LABEL);
    }

    private static void fire(SelfTests04.Check t) {
        System.out.println("[v0.4 fire: an effect that burns, not a block]");
        boolean block = false;
        for (Block b : Block.ALL) if (b != null && "fire".equals(b.name)) block = true;
        try { Block.class.getField("FIRE"); block = true; } catch (NoSuchFieldException e) { /* gone, as intended */ }
        t.check("there is no fire voxel any more", !block, null);
        Raid r = SelfTests.stage(new Profile());
        r.freezeAI = true;
        Actor v = r.debugSpawn("raiders", 320, 500, G), far = r.debugSpawn("raiders", 340, 500, G);
        float hp = v.hp, hpFar = far.hp;
        r.fire.ignite(320, G, 500, 1.6f, 6, 18, 0);
        run(r, 2);
        int live = r.fire.fires.size(), parts = r.fire.particles();
        t.check("a fire burns whoever stands in it and nobody else", v.hp < hp - 10 && far.hp == hpFar && live == 1 && parts > 10, String.format("hp %.0f -> %.0f, %d particles", hp, v.hp, parts));
        run(r, 7);
        t.check("a fire burns out when its fuel is spent", r.fire.fires.isEmpty(), r.fire.fires.size() + " still burning");
    }

    private static void anatomy(SelfTests04.Check t) {
        System.out.println("[v0.4 anatomy: damage is local, structural and kept]");
        Raid r = SelfTests.stage(new Profile());
        r.freezeAI = true;
        ItemDef ammo = Content.def("ammo.556");
        Actor v = r.debugSpawn("raiders", 310, 500, G);
        v.hp = v.maxHp = 5000;
        VoxelBody b = r.gore.body(v);
        int[] legs = {Rig.L_THIGH, Rig.L_SHIN, Rig.R_THIGH, Rig.R_SHIN}, upper = {Rig.HEAD, Rig.L_UPPER_ARM, Rig.L_FOREARM, Rig.R_UPPER_ARM, Rig.R_FOREARM};
        int legs0 = solid(b, legs), up0 = solid(b, upper);
        r.damageActor(v, r.player, 45, ammo, "legs", v.x, v.y + 0.5f, v.z);
        int legs1 = solid(b, legs), up1 = solid(b, upper);
        t.check("a hit to the legs removes leg voxels and leaves the head and arms untouched", legs1 < legs0 && up1 == up0, (legs0 - legs1) + " leg voxels destroyed, " + (up0 - up1) + " elsewhere");
        run(r, 6);
        t.check("wounds persist: nothing grows back", solid(b, legs) == legs1 && r.gore.body(v) == b, null);
        // energy scales the wound
        Actor lo = r.debugSpawn("raiders", 314, 500, G), hi = r.debugSpawn("raiders", 318, 500, G);
        lo.hp = lo.maxHp = hi.hp = hi.maxHp = 5000;
        int[] every = new int[Rig.GROUPS];
        for (int i = 0; i < every.length; i++) every[i] = i;
        int lo0 = solid(r.gore.body(lo), every), hi0 = solid(r.gore.body(hi), every);
        r.damageActor(lo, r.player, 35, ammo, "torso", lo.x, lo.y + 1.2f, lo.z);
        r.damageActor(hi, r.player, 160, ammo, "torso", hi.x, hi.y + 1.2f, hi.z);
        int dLo = lo0 - solid(r.gore.body(lo), every), dHi = hi0 - solid(r.gore.body(hi), every);
        t.check("a heavy hit destroys far more tissue than a light one", dHi > dLo * 1.5f && dHi > dLo + 8, dLo + " against " + dHi + " voxels");
        // structure decides dismemberment: keep hitting one leg until it comes away
        Actor d = r.debugSpawn("raiders", 322, 500, G);
        d.hp = d.maxHp = 50000;
        int shots = 0;
        // (real shots: each along its own line across the thigh, as a burst would land)
        while (shots < 60 && (d.severed & (absentialis.scene.Gore.LEG_L_MASK | absentialis.scene.Gore.LEG_R_MASK)) == 0) {
            float oy = d.y + d.height() * 0.33f, ox = d.x - 0.24f + (shots * 7 % 25) * 0.02f;
            var en = r.gore.trace(d, ox, oy, d.z + 3, 0, 0, -1, 6);
            if (en != null) r.gore.impact(d, r.player, en, absentialis.anatomy.DamageProcessor.BULLET, 70, 3.5f, 0, 0, -1);
            shots++;
        }
        run(r, 1);
        boolean off = (d.severed & (absentialis.scene.Gore.LEG_L_MASK | absentialis.scene.Gore.LEG_R_MASK)) != 0;
        t.check("a limb comes away when its structure fails, not on the first hit", off && shots > 1 && !r.gore.dismember.pieces.isEmpty() && d.anat != null && !d.anat.canWalk, shots + " hits, " + r.gore.dismember.pieces.size() + " piece(s), " + (d.anat == null ? "" : d.anat.summary()));
        t.check("the AI is told what its body can still do", d.crawling || d.speedMul < 0.6f, "speed x" + d.speedMul + (d.crawling ? ", crawling" : ""));
    }

    private static void ai(SelfTests04.Check t) {
        System.out.println("[v0.4 combat AI: perception, not omniscience]");
        World w = SelfTests.flatWorld();
        Raid r = SelfTests.stage(new Profile());
        r.player.weapon = null;
        java.util.List<int[]> edits = new java.util.ArrayList<>();
        for (int x = 280; x <= 320; x++) for (int y = G; y < G + 5; y++) { edits.add(new int[]{x, y, 490, w.get(x, y, 490)}); w.setRaw(x, y, 490, Block.BEDROCK); }
        List<Actor> squad = SelfTests.squadOf(r, "mercenaries", 3, 296, 470, 41, true);
        run(r, 8);
        boolean blind = true;
        for (Actor a : squad) blind &= a.targetId == 0 && a.awareLevel <= 1 && !"engage".equals(a.state);
        t.check("enemies behind a wall with nothing to hear do not know where the player is", blind, squad.get(0).state + ", level " + squad.get(0).awareLevel);
        for (int i = edits.size() - 1; i >= 0; i--) { int[] e = edits.get(i); w.setRaw(e[0], e[1], e[2], e[3]); }
        int said0 = r.said;
        run(r, 6);
        boolean engaged = false;
        int maxLevel = 0;
        for (Actor a : squad) { engaged |= a.targetId == r.player.id; maxLevel = Math.max(maxLevel, a.awareLevel); }
        t.check("with the wall gone they see, escalate and call it out", engaged && maxLevel >= 3 && r.said > said0, "level " + maxLevel + ", " + (r.said - said0) + " lines spoken");
        Actor a = squad.get(0);
        float s0 = a.suppression;
        for (int i = 0; i < 4; i++) NeuralHost.nearMiss(r, a, a.x + 0.4f, a.y + 1.5f, a.z, r.player.id);
        t.check("near misses suppress", a.suppression > s0 + 0.2f, String.format("%.2f -> %.2f", s0, a.suppression));
        // lose them: the memory is of a place, and the search covers ground
        // (the player slips into a sealed room sixty metres off; nobody saw where)
        edits.clear();
        for (int x = 356; x <= 364; x++) for (int z = 496; z <= 504; z++) if (x == 356 || x == 364 || z == 496 || z == 504) for (int y = G; y < G + 5; y++) { edits.add(new int[]{x, y, z, w.get(x, y, z)}); w.setRaw(x, y, z, Block.BEDROCK); }
        r.player.x = 360.5f; r.player.z = 500.5f;
        String trace = "";
        boolean searching = false;
        for (int k = 0; k < 5; k++) { run(r, 5); trace += " " + (k + 1) * 5 + "s:"; for (Actor m : squad) { trace += m.state.charAt(0); searching |= "search".equals(m.state); } }
        boolean knows = false;
        for (Actor m : squad) knows |= m.targetId == r.player.id && Math.hypot(m.memX - 360.5f, m.memZ - 500.5f) < 12;
        for (int i = edits.size() - 1; i >= 0; i--) { int[] e = edits.get(i); w.setRaw(e[0], e[1], e[2], e[3]); }
        t.check("contact lost: they search the last known area instead of tracking the player", searching && !knows, trace + String.format(", remembers %.0f,%.0f", squad.get(0).memX, squad.get(0).memZ));
    }

    private static void misc(SelfTests04.Check t) {
        System.out.println("[v0.4 audio, squad names, garage]");
        Map<String, float[]> bank = new HashMap<>();
        new absentialis.engine.audio.SoundBank().build(bank);
        int base = bank.size();
        new absentialis.engine.audio.SoundBank04().build(bank);
        String missing = "";
        float peak = 0;
        for (String s : new String[]{"engine_loop", "tyre_skid", "car_crash", "fire_loop", "bone_snap", "death_drone", "bark_contact", "car_horn", "car_door", "bite", "antiviral", "infected_turn", "cough", "ui_buy", "ui_tab"}) {
            float[] b = bank.get(s);
            if (b == null) { missing += s + " "; continue; }
            float pk = 0;
            for (float f : b) pk = Math.max(pk, Math.abs(f));
            if (pk < 0.02f || Float.isNaN(pk)) missing += s + "(silent) ";
            peak = Math.max(peak, pk);
        }
        t.check("the sound bank holds audible samples for the 0.4 systems", missing.isEmpty() && bank.size() > base && peak <= 1.001f, bank.size() + " sounds, peak " + peak + (missing.isEmpty() ? "" : ", missing: " + missing));

        Roster ro = new Roster();
        ro.enlist(4, 7);
        Roster.Mate m = ro.mates.get(0);
        m.name = Roster.cleanName("   Sgt.  \"Dutch\"   van der Linde-Okonkwo the Third   ");
        Roster back = Roster.fromJson(ro.toJson());
        t.check("a squadmate's custom name is cleaned, kept and saved", back.mates.get(0).name.equals(m.name) && !m.name.isEmpty() && !m.name.startsWith(" ") && !m.name.contains("  ") && m.name.length() <= 20 && Roster.cleanName(" 	 ").isEmpty(), "'" + m.name + "'");

        CarHandlingData.load();
        boolean priced = true;
        for (CarHandlingData d : CarHandlingData.all()) { float[] rt = absentialis.scene.GaragePanel.ratings(d); priced &= d.price > 0 && d.rank >= 1 && !d.name.isEmpty(); for (float f : rt) priced &= f > 0 && f <= 1; }
        t.check("every vehicle is for sale with a price, a rank and comparable ratings", priced, null);
        // the vehicle you bring is the one waiting, and no operation holds more than four
        Profile p = new Profile();
        p.garage.add("car.kolos");
        p.deployVehicle = "car.kolos";
        Raid r = SelfTests.raid(p);
        int owned = 0;
        for (var c : r.vehicles.cars) if (c.owned && c.sim.data.id.equals("car.kolos")) owned++;
        t.check("the vehicle you bring waits at the insertion; never more than four in an operation", owned == 1 && r.vehicles.cars.size() <= absentialis.scene.VehicleSystem.MAX, owned + " owned of " + r.vehicles.cars.size());
        r.god = false;
        r.die("Shot");
        run(r, 8);
        r.resolveDeath();
        absentialis.scene.RaidResolver.resolve(r);
        t.check("die, and the vehicle you brought is gone from the garage", !p.garage.contains("car.kolos") && p.deployVehicle.isEmpty(), p.garage.toString());
    }
}
