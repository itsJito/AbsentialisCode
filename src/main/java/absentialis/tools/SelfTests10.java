package absentialis.tools;

import absentialis.entity.Actor;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemInstance;
import absentialis.gameplay.missions.Chains;
import absentialis.neuralai.integration.Archetypes;
import absentialis.neuralai.integration.Creatures;
import absentialis.persistence.Profile;
import absentialis.scene.ChoicePopup;
import absentialis.scene.Encounters;
import absentialis.scene.Raid;
import absentialis.world.Block;
import absentialis.world.World;

/**
 * Quality gates for 1.0: the faction missions and their advisors, the two new special infected, a big body going
 * through a timber wall at a door, the survivor's body being looted, bodies that do not lie for ever, the choice
 * window, and Sydney's contextual motion. Headless, like the rest; `gradlew selftest`, or ABS_SELFTEST=v10.
 */
final class SelfTests10 {
    private SelfTests10() {}

    static void run(String which, boolean all, SelfTests04.Check t) {
        if (all || which.contains("v10") || which.contains("missions10")) SelfTests.guard(() -> missions(t));
        if (all || which.contains("v10") || which.contains("infected10")) SelfTests.guard(() -> infected(t));
        if (all || which.contains("v10") || which.contains("world10")) SelfTests.guard(() -> world(t));
        if (all || which.contains("v10") || which.contains("motion10")) SelfTests.guard(() -> motion(t));
    }

    private static final int G = World.GROUND;

    private static void run(Raid r, float seconds) { for (int i = 0; i < Math.round(seconds * 60); i++) r.update(1 / 60f); }

    private static Raid flat(Profile p) {
        Content.load();
        Raid r = new Raid(SelfTests.flatWorld(), p, new absentialis.persistence.Settings(), new absentialis.engine.events.EventBus(), 0, 30);
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer);
        return r;
    }

    // ------------------------------------------------------------------ the faction missions
    private static void missions(SelfTests04.Check t) {
        System.out.println("[v1.0 missions: four chains of five, an advisor to each, decisions that are kept]");
        Chains.load();
        boolean shape = Chains.ADVISORS.size() == 4 && Chains.MISSIONS.size() == 20;
        StringBuilder names = new StringBuilder();
        java.util.Set<String> flags = new java.util.HashSet<>();
        int choices = 0;
        for (Chains.Advisor a : Chains.ADVISORS) {
            names.append(a.name()).append(", ");
            shape &= Chains.length(a.chain()) == 5 && !a.site().isEmpty() && !a.greeting().isEmpty();
            for (int n = 1; n <= 5; n++) {
                Chains.Mission m = Chains.mission(a.chain(), n);
                shape &= m != null && m.choices.size() >= 2 && !m.brief.isEmpty() && !m.discovery.isEmpty() && !m.site.isEmpty() && !m.find.isEmpty();
                if (m != null) for (Chains.Choice c : m.choices) { choices++; shape &= !c.flag().isEmpty() && flags.add(c.flag()) && !c.outcome().isEmpty(); }
            }
        }
        boolean who = names.toString().contains("Nika Beridze") && names.toString().contains("Vera Sokolova") && names.toString().contains("Kateryna Melnyk") && names.toString().contains("Wade Ellis");
        t.check("twenty faction missions in four chains of five, each chain with its advisor (Nika Beridze, Lt. Vera Sokolova, Kateryna Melnyk, Wade Ellis) and every decision with an outcome and a flag of its own", shape && who, Chains.ADVISORS.size() + " advisors, " + Chains.MISSIONS.size() + " missions, " + choices + " choices, " + flags.size() + " flags");

        Chains cs = new Chains();
        String ch = "mercenaries";
        boolean order = !cs.found(ch) && cs.resolve(ch, "buyer") == null;                 // nothing can be found or decided before it is taken
        order &= cs.accept(ch) && !cs.accept(ch) && cs.resolve(ch, "buyer") == null;        // taken once; not decided before it is found
        order &= cs.found(ch);
        Chains.Choice c = cs.resolve(ch, "owner");
        order &= c != null && cs.has("merc.package_returned") && cs.of(ch).done == 1 && cs.of(ch).stage == Chains.Stage.OFFERED && cs.current(ch).n == 2;
        // a choice that needs an earlier decision is only offered to someone who made it
        Chains.Mission last = Chains.mission(ch, 5);
        int openWithout = cs.open(last).size();
        cs.flags.add("merc.drive_copied");
        order &= cs.open(last).size() == openWithout + 1;
        Chains back = Chains.fromJson(absentialis.engine.util.Json.parseObject(absentialis.engine.util.Json.write(cs.toJson())));
        boolean kept = back.of(ch).done == 1 && back.has("merc.package_returned") && "owner".equals(back.taken.get("MERC1")) && back.of("military").done == 0;
        t.check("a chain is strictly in order - offered, taken, found, decided - the decision is kept as a flag, a later choice can depend on it, and all of it survives a save", order && kept, "done " + cs.of(ch).done + ", flags " + cs.flags + ", reloaded " + kept);

        // the window the questions are put in
        ChoicePopup pop = new ChoicePopup();
        int[] got = {-1};
        pop.show("T", "S", "B", i -> got[0] = i).option("a", "").option("b", "").escapes();
        boolean esc = pop.open && pop.escape() && !pop.open && got[0] == 1;
        pop.show("T", "S", "B", i -> got[0] = i).option("a", "").option("b", "");
        boolean must = pop.escape() && pop.open;                                          // no way out of a question that has to be answered
        pop.pick(0);
        t.check("the choice window: an answer is given once, Escape means the answer marked for it, and a question with no such answer has to be answered", esc && must && !pop.open && got[0] == 0, "escape -> " + (esc ? "last option" : "?") + ", unanswerable stays open " + must);

        // a meeting on the road: the toll
        Profile p = new Profile();
        p.scrip = 1000;
        Raid r = flat(p);
        Actor toll = r.debugSpawn("raiders", r.player.x + 2, r.player.z, r.player.y);
        Encounters enc = new Encounters(r);
        float rep0 = p.rep("raiders");
        boolean met = toll != null && enc.force(toll, pop) && pop.open && pop.options.size() == 3;
        pop.pick(0);
        t.check("a faction's people stop the survivor in their own way: the Rail Yard Crew's toll is three answers, and paying it costs what it says and earns what it says", met && p.scrip == 700 && p.rep("raiders") > rep0, "scrip 1000 -> " + p.scrip + ", standing " + rep0 + " -> " + p.rep("raiders"));
    }

    // ------------------------------------------------------------------ the Crawler and the Tyrant
    private static void infected(SelfTests04.Check t) {
        System.out.println("[v1.0 infected: the Crawler and the Tyrant]");
        Archetypes.Arch cr = Archetypes.arch("crawler"), ty = Archetypes.arch("tyrant"), bu = Archetypes.arch("butcher");
        Actor sizeT = new Actor(), sizeB = new Actor();
        sizeT.archetype = "tyrant"; sizeT.faction = "infected"; Raid.shape(sizeT);
        sizeB.archetype = "butcher"; sizeB.faction = "infected"; Raid.shape(sizeB);
        t.check("two new special infected: the Crawler is blind and hears better than anything else that walks; the Tyrant stands taller than the Butcher and is harder to put down", cr.infected() && cr.blind() && cr.hearing() > Archetypes.arch("gladiator").hearing() && ty.infected() && !ty.blind() && sizeT.stand * Archetypes.scale("tyrant") > sizeB.stand * Archetypes.scale("butcher") && ty.hp() > bu.hp() && Archetypes.special("crawler") && Archetypes.special("tyrant") && Archetypes.huge("tyrant"),
                String.format("crawler sight %.0f hearing %.1f; tyrant %.2f m against the Butcher's %.2f m, %.0f hp against %.0f", cr.sight(), cr.hearing(), sizeT.stand, sizeB.stand, ty.hp(), bu.hp()));

        Raid r = flat(new Profile());
        float px = r.player.x, pz = r.player.z, py = r.player.y;
        // the Tyrant's charge: whoever it meets is thrown
        Actor tyr = r.debugSpawn("tyrant", px + 30, pz + 30, py), vic = r.debugSpawn("survivors", px + 32, pz + 30, py);
        float hp0 = vic.hp;
        r.tackle(tyr, vic, 1, 0, Creatures.TACKLE_DAMAGE);
        boolean thrown = vic.vx > 4 && vic.hp < hp0 && r.tackles == 1 && (vic.thrownAt > -50 || vic.dead || vic.ragdoll != null);
        r.god = true;
        r.tackle(tyr, r.player, 0, 1, Creatures.TACKLE_DAMAGE);
        t.check("the Tyrant's charge takes people off their feet: someone it hits is thrown the way it was going and hurt, and the survivor is knocked down and cannot act for a moment", thrown && r.slipT > 1f && r.player.vz > 4, String.format("bystander thrown at %.1f m/s, %.0f -> %.0f hp; survivor down for %.1f s", vic.vx, hp0, vic.hp, r.slipT));

        // the Crawler's tongue: thrown at a place; whoever is there is dragged toward it
        Actor crawler = r.debugSpawn("crawler", px + 60, pz + 60, py), prey = r.debugSpawn("survivors", px + 67, pz + 60, py);
        r.frozen.add(prey.id); r.frozen.add(crawler.id);
        float preyHp = prey.hp;
        r.tongue(crawler, prey.x, prey.y + 1.1f, prey.z);
        boolean hooked = r.tongueHits == 1 && prey.vx < -3 && prey.hp < preyHp && crawler.tongueUntil > r.time;
        r.tongue(crawler, px + 60, py + 1, pz + 75);
        t.check("the Crawler's tongue is thrown at a place: whoever is standing there is hooked, hurt a little and dragged toward it - and thrown at nothing, it catches nothing", hooked && r.tongueHits == 1, String.format("pulled at %.1f m/s, %.0f -> %.0f hp, hits %d", prey.vx, preyHp, prey.hp, r.tongueHits));

        // it takes to a ceiling when nothing is happening
        World w = r.world;
        int cx = (int) px + 90, cz = (int) pz + 90;
        for (int x = cx - 4; x <= cx + 4; x++) for (int z = cz - 4; z <= cz + 4; z++) w.setRaw(x, G + 4, z, Block.CONCRETE);
        Actor roof = r.debugSpawn("crawler", cx + 0.5f, cz + 0.5f, py);
        int before = Creatures.clings;
        r.player.x = cx + 20; r.player.z = cz + 0.5f;
        run(r, 14);
        boolean up = Creatures.clings > before && roof.cling && Math.abs(roof.y - (G + 4 - roof.stand - 0.02f)) < 0.2f;
        for (int x = cx - 4; x <= cx + 4; x++) for (int z = cz - 4; z <= cz + 4; z++) w.setRaw(x, G + 4, z, Block.AIR);
        run(r, 1.5f);
        t.check("a Crawler with nothing to hunt takes to the ceiling and hangs under it - and when the ceiling is gone from over it, it comes down", up && !roof.cling && roof.y < G + 1.5f, "clinging " + up + " at the slab; after the slab was removed: cling " + roof.cling + String.format(", y %.2f", roof.y - G));
    }

    // ------------------------------------------------------------------ the world
    private static void world(SelfTests04.Check t) {
        System.out.println("[v1.0 world: doors and big bodies, the dead, the survivor's kit]");
        Profile p = new Profile();
        Raid r = flat(p);
        World w = r.world;
        float px = r.player.x, pz = r.player.z, py = r.player.y;
        // a timber wall with a door in it, and a Tank on this side of it
        int X = (int) px + 40, Z = (int) pz + 40;
        for (int pass = 0; pass < 2; pass++) {
            int wall = pass == 0 ? Block.WOOD_PLANKS : Block.BRICK_RED, z = Z + pass * 12;
            for (int x = X - 4; x <= X + 4; x++) for (int y = G; y < G + 5; y++) w.setRaw(x, y, z, wall);
            w.setRaw(X, G, z, Block.DOOR_WOOD); w.setRaw(X, G + 1, z, Block.DOOR_WOOD);
        }
        Actor tank = r.debugSpawn("tank", X + 0.5f, Z + 1.8f, py);
        tank.yaw = 0;                                           // facing -z: the wall
        boolean through = r.breach(tank);
        int open = 0;
        for (int x = X - 1; x <= X + 1; x++) for (int y = G; y < G + 4; y++) if (!w.solid(x, y, Z)) open++;
        boolean rest = w.solid(X - 3, G, Z) && w.solid(X + 3, G + 2, Z);
        Actor tank2 = r.debugSpawn("tank", X + 0.5f, Z + 12 + 1.8f, py);
        tank2.yaw = 0;
        r.breach(tank2);
        int brick = 0;
        for (int x = X - 4; x <= X + 4; x++) for (int y = G; y < G + 5; y++) if (w.get(x, y, Z + 12) == Block.BRICK_RED) brick++;
        // and a wall with no door in it is not gone through at all
        for (int x = X - 4; x <= X + 4; x++) for (int y = G; y < G + 5; y++) w.setRaw(x, y, Z + 24, Block.WOOD_PLANKS);
        Actor tank3 = r.debugSpawn("tank", X + 0.5f, Z + 24 + 1.8f, py);
        tank3.yaw = 0;
        boolean noDoor = !r.breach(tank3);
        r.smash(tank3);
        int planks = 0;
        for (int x = X - 4; x <= X + 4; x++) for (int y = G; y < G + 5; y++) if (w.get(x, y, Z + 24) == Block.WOOD_PLANKS) planks++;
        t.check("a Tank at a door goes through the wall the door is in - if the wall is timber: the door and the timber round it are gone, three wide and as high as it stands; brick does not give; and a wall with no door in it is left alone", through && open >= 11 && rest && brick == 43 && noDoor && planks == 45 && r.breaches >= 1,
                "timber: opening of " + open + " cells of 12, wall either side standing " + rest + "; brick wall " + brick + " of 43 bricks left; doorless timber wall " + planks + " of 45 planks left");
        for (int dz : new int[]{0, 12, 24}) for (int x = X - 4; x <= X + 4; x++) for (int y = G; y < G + 5; y++) w.setRaw(x, y, Z + dz, Block.AIR);
        r.actors.removeIf(a -> !a.isPlayer);

        // the dead do not lie for ever
        Actor body = r.debugSpawn("walker", px + 60, pz, py), nearBody = r.debugSpawn("walker", px + 3, pz, py);
        r.debugKill(body); r.debugKill(nearBody);
        body.deadTime = Raid.CORPSE_SECONDS + 1; nearBody.deadTime = Raid.CORPSE_SECONDS + 1;
        run(r, 0.2f);
        boolean far = !r.actors.contains(body), near = r.actors.contains(nearBody);
        nearBody.deadTime = Raid.CORPSE_SECONDS * 3 + 1;
        r.player.x = px - 10;
        run(r, 0.2f);
        t.check("bodies are cleared: one that has lain its time goes when the survivor is well away from it, one they are standing by is left - but not for ever", far && near && !r.actors.contains(nearBody) && r.corpsesCleared == 2, "far body gone " + far + ", near body kept " + near + ", then gone " + !r.actors.contains(nearBody));
        r.player.x = px;

        // the survivor's own body is worth going through
        InventoryService.add(new ItemInstance("med.bandage"), p.loadout.caseGrid);
        int had = p.loadout.all().size();
        Actor killer = r.debugSpawn("raiders", px + 6, pz, py);
        killer.hostileToPlayer = true;
        float keep = Raid.LOOT_KILLER_CHANCE;
        Raid.LOOT_KILLER_CHANCE = 1f;
        r.player.lastAttacker = killer.id; r.player.hp = 0; r.player.dead = true;
        run(r, 14);
        Raid.LOOT_KILLER_CHANCE = keep;
        t.check("whoever killed the survivor may come and go through their kit: they walk to the body, get down by it, and take the best of what is there - a few things, named on the death screen", !r.corpseLootTaken.isEmpty() && r.corpseLootTaken.size() <= Raid.LOOT_TAKES && p.loadout.all().size() == had - r.corpseLootTaken.size() && r.corpseLooter == null,
                "had " + had + " things, taken " + r.corpseLootTaken + ", left " + p.loadout.all().size());

        // the squad at a door: stack, open, go in, say so
        Raid sq = flat(new Profile());
        World sw = sq.world;
        int hx = (int) sq.player.x + 20, hz = (int) sq.player.z + 20;
        for (int x = hx; x <= hx + 8; x++) for (int z = hz; z <= hz + 8; z++) for (int y = G; y <= G + 4; y++) if (y == G + 4 || x == hx || x == hx + 8 || z == hz || z == hz + 8) sw.setRaw(x, y, z, Block.WOOD_PLANKS);
        sw.setRaw(hx + 4, G, hz + 8, Block.DOOR_WOOD); sw.setRaw(hx + 4, G + 1, hz + 8, Block.DOOR_WOOD);
        sq.doors.scan();
        sq.god = true;
        sq.player.x = hx + 4.5f; sq.player.z = hz + 13.5f; sq.player.yaw = 0;
        for (int i = 0; i < 2; i++) { Actor mt = sq.debugSpawn("military", hx + 3.5f + i * 2, hz + 15, G); mt.mateId = 9101 + i; mt.hostileToPlayer = false; sq.mates.add(mt); }
        run(sq, 1);
        int sent = sq.squadTasks.order("gold", hx + 4, G, hz + 8, 0, -1, false);
        boolean wentIn = false, opened = false;
        for (int i = 0; i < 60 * 30 && sq.squadTasks.cleared == 0; i++) {
            sq.update(1 / 60f);
            opened |= !sw.solid(hx + 4, G, hz + 8);
            for (Actor mt : sq.mates) wentIn |= mt.z < hz + 8 && mt.x > hx && mt.x < hx + 8;
        }
        t.check("a team sent through a door stacks on it, opens it, goes in and reports the room clear - by ordinary move and sweep orders given at the right moments, not by a script for that door", sent == 2 && opened && wentIn && sq.squadTasks.cleared == 1, "sent " + sent + ", door opened " + opened + ", somebody inside " + wentIn + ", rooms reported clear " + sq.squadTasks.cleared);
        for (int x = hx; x <= hx + 8; x++) for (int z = hz; z <= hz + 8; z++) for (int y = G; y <= G + 4; y++) sw.setRaw(x, y, z, Block.AIR);

        // hearing: a wall between is a wall between
        Raid h = flat(new Profile());
        World hw = h.world;
        float ex = h.player.x, ez = h.player.z, ey = h.player.y + 1.6f;
        float clear = absentialis.scene.Hearing.through(hw, ex, ey, ez, ex + 12, ey, ez);
        for (int z = (int) ez - 6; z <= (int) ez + 6; z++) for (int y = G; y < G + 6; y++) { hw.setRaw((int) ex + 5, y, z, Block.CONCRETE); hw.setRaw((int) ex + 8, y, z, Block.CONCRETE); }
        float walled = absentialis.scene.Hearing.through(hw, ex, ey, ez, ex + 12, ey, ez);
        for (int z = (int) ez - 6; z <= (int) ez + 6; z++) for (int y = G; y < G + 6; y++) { hw.setRaw((int) ex + 5, y, z, Block.AIR); hw.setRaw((int) ex + 8, y, z, Block.AIR); }
        t.check("sound is heard in the space it is made in: across open ground all of it arrives, and behind two walls almost none of it does", clear > 0.95f && walled < 0.35f, String.format("open %.2f, two walls between %.2f", clear, walled));
    }

    // ------------------------------------------------------------------ Sydney: contextual motion, and the gaits
    private static void motion(SelfTests04.Check t) {
        System.out.println("[v1.0 motion: the pull-up is worked out, not played back]");
        Actor a = new Actor();
        a.mFromX = 10.5f; a.mFromZ = 20.9f; a.mFromY = G; a.mToX = 10.5f; a.mToZ = 19.5f; a.mToY = G + 3; a.mantleDur = 1.3f; a.radius = 0.3f;
        absentialis.sydney.ContextualMotion.Mantle m = new absentialis.sydney.ContextualMotion.Mantle();
        float[] first = null;
        boolean fixed = true, onLip = true;
        float wStart = 0, wMid = 0, wEnd = 0;
        for (int i = 0; i <= 20; i++) {
            a.mantleT = a.mantleDur * (1 - i / 20f);
            absentialis.sydney.ContextualMotion.mantle(a, m);
            if (first == null) first = new float[]{m.handL[0], m.handL[1], m.handL[2], m.handR[0], m.handR[1], m.handR[2]};
            fixed &= Math.abs(m.handL[0] - first[0]) < 1e-4f && Math.abs(m.handL[2] - first[2]) < 1e-4f && Math.abs(m.handR[0] - first[3]) < 1e-4f && Math.abs(m.handR[1] - first[4]) < 1e-4f;
            onLip &= Math.abs(m.handL[1] - (G + 3)) < 0.06f && m.handL[2] > 19.5f && m.handL[2] < 20f && Math.abs(m.handR[0] - m.handL[0]) > 0.35f;
            if (i == 0) wStart = m.handW;
            if (i == 10) wMid = m.handW;
            if (i == 20) wEnd = m.handW;
        }
        float[] wpn = new float[2];
        absentialis.sydney.ContextualMotion.airborneWeapon(5f, 4f, wpn);
        float up = wpn[0];
        absentialis.sydney.ContextualMotion.airborneWeapon(-5f, 4f, wpn);
        t.check("a pull-up is Sydney's: the hands are places on the lip of the real ledge that do not move while the body climbs past them, they take the weight after the reach and let go before the top; and a weapon carried through a jump lags the body going up and settles coming down", fixed && onLip && wStart < 0.05f && wMid > 0.95f && wEnd < 0.05f && up > 0 && wpn[0] < 0,
                String.format("hands fixed %s, on the lip %s; hold %.2f / %.2f / %.2f at start / middle / end; weapon %+.2f rising, %+.2f falling", fixed, onLip, wStart, wMid, wEnd, up, wpn[0]));

        absentialis.character.AnimClip walk = absentialis.character.Anims.get("walk"), jog = absentialis.character.Anims.get("jog"), sprint = absentialis.character.Anims.get("sprint"), fat = absentialis.character.Anims.get("fat_waddle");
        float sWalk = walk.travel / walk.duty, sJog = jog.travel / jog.duty, sSprint = sprint.travel / sprint.duty;
        t.check("a run is a run: the sprint's stride is longer than the jog's and the jog's longer than the walk's (fewer footfalls at the same speed), and the new kinds have gaits of their own", sSprint > sJog * 1.3f && sJog > sWalk * 1.6f && fat.duty > 0.7f && absentialis.character.Anims.get("crawl_go") != null && absentialis.character.Anims.get("tyrant_run") != null && absentialis.character.Anims.get("loot_body") != null,
                String.format("stride (voxels a cycle): walk %.0f, jog %.0f, sprint %.0f", sWalk, sJog, sSprint));
    }
}
