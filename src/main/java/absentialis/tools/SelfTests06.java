package absentialis.tools;

import absentialis.world.District;
import absentialis.world.MapData;
import absentialis.world.World;

/**
 * Version 0.6 gates. Each section names the requirement of the 0.6 brief it covers; the tracker
 * ({@code docs/V0.6_IMPLEMENTATION.md}) points back here.
 */
final class SelfTests06 {
    private SelfTests06() {}

    static final int G = World.GROUND;
    static final long[] SEEDS = {1337L, 20260510L};
    private static final java.util.Map<Long, World> WORLDS = new java.util.HashMap<>();

    /** One generated world per seed for the whole 0.6 suite (generation is the slow part). */
    static World world(long seed) {
        return WORLDS.computeIfAbsent(seed, s -> { World w = new World(); new absentialis.world.gen.CityGenerator(w, s, null).generate(); return w; });
    }

    static void run(String which, boolean all, SelfTests04.Check t) {
        if (all || which.contains("v06") || which.contains("zone06")) SelfTests.guard(() -> { zones(t); });
        if (all || which.contains("v06") || which.contains("audio06")) SelfTests.guard(() -> { audio(t); });
        if (all || which.contains("v06") || which.contains("phys06")) SelfTests.guard(() -> { physics(t); });
        SelfTests.guard(() -> RivalryTests.run(which, all, t));
        SelfTests.guard(() -> SydneyTests.run(which, all, t));
        if (all || which.contains("v06") || which.contains("world06")) SelfTests.guard(() -> { worldAndItems(t); });
        if (all || which.contains("v06") || which.contains("combat06")) SelfTests.guard(() -> { combat(t); });
        if (all || which.contains("v06") || which.contains("meta06")) SelfTests.guard(() -> { meta(t); });
    }

    // ------------------------------------------------------------------ profile, alliance, population, ranks, squad, names, rivalry in the raid
    private static void meta(SelfTests04.Check t) {
        System.out.println("[v0.6 profile: version and migration, names, alliance, population, ranks and leaderboard, permadeath, rivalry in the raid]");
        absentialis.gameplay.items.Content.load();
        // ---- version and save format; a 0.5 save loads, keeps what it had, and gains the 0.6 fields with their defaults
        t.check("this build is Version 1.3 with save format 8", "1.3".equals(absentialis.Version.NUMBER) && absentialis.Version.SAVE_FORMAT == 8 && absentialis.Version.LABEL.contains("1.3"), absentialis.Version.LABEL + ", format " + absentialis.Version.SAVE_FORMAT);
        absentialis.persistence.Profile fresh = new absentialis.persistence.Profile();
        fresh.name = "Gate"; fresh.starterKit(); fresh.applyCapacity();
        fresh.squad.enlist(4, 5);       // the four a 0.5 profile was given: 0.7 hands out none, and takes none away
        java.util.Map<String, Object> old = fresh.toJson();
        old.put("version", 5);
        for (String k : new String[]{"alliance", "cardBackground", "population", "rivalry"}) old.remove(k);
        @SuppressWarnings("unchecked") java.util.Map<String, Object> sq = (java.util.Map<String, Object>) old.get("squad");
        sq.remove("granted"); sq.remove("hired"); sq.remove("fallen");
        @SuppressWarnings("unchecked") java.util.List<Object> ms = (java.util.List<Object>) sq.get("mates");
        @SuppressWarnings("unchecked") java.util.Map<String, Object> m0 = (java.util.Map<String, Object>) ms.get(0), m1 = (java.util.Map<String, Object>) ms.get(1);
        m0.put("name", "Dmitri \"Kestrel\""); m1.put("name", "My Own Name");
        absentialis.persistence.Profile up = absentialis.persistence.Profile.fromJson(absentialis.engine.util.Json.parseObject(absentialis.engine.util.Json.write(old)));
        String n0 = up.squad.mates.get(0).name, n1 = up.squad.mates.get(1).name;
        boolean american = !n0.startsWith("Dmitri") && n0.endsWith("\"Kestrel\"");
        t.check("a 0.5 save migrates: items and squad intact, new fields at their defaults, generated squad names redrawn, typed names untouched",
                up.stash.items.size() == fresh.stash.items.size() + 1 && up.stash.items.stream().anyMatch(i -> i.defId.equals("tool.flashlight")) && up.squad.mates.size() == 4 && up.alliance.isEmpty() && "field".equals(up.cardBackground) && up.rivalry.groups.isEmpty() && american && n1.equals("My Own Name") && up.verify() == null,
                "stash " + fresh.stash.items.size() + " -> " + up.stash.items.size() + ", card " + up.cardBackground + ", groups " + up.rivalry.groups.size() + ", squad of " + up.squad.mates.size() + "; \"Dmitri \\\"Kestrel\\\"\" -> " + n0 + "; typed name kept: " + n1 + "; integrity: " + up.verify());
        String j1 = absentialis.engine.util.Json.write(up.toJson());
        absentialis.persistence.Profile again = absentialis.persistence.Profile.fromJson(absentialis.engine.util.Json.parseObject(j1));
        t.check("a 0.6 profile round-trips through save and load unchanged (rivalry state included)", absentialis.engine.util.Json.write(again.toJson()).equals(j1), j1.length() + " characters");

        // ---- names
        java.util.Set<String> oldNames = java.util.Set.of("Anton", "Boris", "Dima", "Fyodor", "Grisha", "Ilya", "Kolya", "Misha", "Nikita", "Oleg", "Petro", "Sasha", "Taras", "Vadim", "Yaroslav", "Zhenya", "Olena", "Daria", "Halyna", "Ksenia", "Larysa", "Dmitri", "Oksana", "Yuri", "Katya", "Pavel", "Irina", "Sergei", "Viktor", "Bohdan", "Kovalenko", "Petrenko", "Shevchuk", "Volkov", "Sokolov");
        int bad = 0, women = 0, mismatch = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.Set<String> fem = new java.util.HashSet<>(java.util.List.of(absentialis.sim.Names.FEMALE));
        for (long id = 1; id <= 2000; id++) {
            String n = absentialis.sim.Agents.nameFor(id);
            for (String part : n.split(" ")) if (oldNames.contains(part)) bad++;
            seen.add(n);
            boolean f = absentialis.sim.Names.female(id);
            if (f) women++;
            if (fem.contains(n.split(" ")[0]) != f) mismatch++;
        }
        t.check("generated names are American, and a name always matches the body it belongs to", bad == 0 && mismatch == 0 && seen.size() > 1200 && women > 500 && women < 1000,
                "2000 ids: " + seen.size() + " distinct names, " + women + " women, " + bad + " from the old tables, " + mismatch + " mismatched; e.g. " + absentialis.sim.Agents.nameFor(7) + ", " + absentialis.sim.Agents.nameFor(8) + ", " + absentialis.sim.Agents.nameFor(9));

        // ---- alliance: where you may deploy, what you are offered
        absentialis.persistence.Profile p = new absentialis.persistence.Profile();
        p.name = "Gate"; p.starterKit(); p.applyCapacity();
        p.squad.enlist(4, 6);
        boolean anywhere = true;
        for (District d : District.CITY) if (!absentialis.gameplay.Alliance.mayDeploy(p, d)) anywhere = false;
        absentialis.gameplay.Alliance.choose(p, "military");
        StringBuilder dep = new StringBuilder();
        int open = 0;
        for (District d : District.CITY) if (absentialis.gameplay.Alliance.mayDeploy(p, d)) { open++; dep.append(d.display).append(' '); }
        boolean homeOnly = open >= 1 && absentialis.gameplay.Alliance.mayDeploy(p, District.CIVIC) && !absentialis.gameplay.Alliance.mayDeploy(p, District.INDUSTRIAL);
        // taking a district: once the strategic layer says the military control it, it opens
        int fi = java.util.List.of(absentialis.sim.ZoneState.FACTIONS).indexOf("military");
        for (int f = 0; f < 4; f++) p.zone.influence[District.MARKET.ordinal()][f] = f == fi ? 0.8f : 0.05f;
        boolean taken = absentialis.gameplay.Alliance.mayDeploy(p, District.MARKET);
        // 0.7: the operator is a third party who has declared an allegiance, not a member: the declared side starts friendly (35), and nobody is made an enemy by the choice
        t.check("an allegiance restricts deployment to that side's headquarters until it takes other ground; it makes a friend and no enemies", anywhere && homeOnly && taken && p.rep("military") >= 35 && p.rep("raiders") >= -20.5f,
                "unaligned: anywhere; Military: " + dep.toString().trim() + "; after the Military take the Residential district it opens too; standing military " + Math.round(p.rep("military")) + ", bandits " + Math.round(p.rep("raiders")));
        java.util.Map<String, String> boards = new java.util.LinkedHashMap<>();
        // 0.8: the board is ten written contracts, the same ten for everyone (tools.SelfTests08 story08). What a leaning
        // changes is whose work is put in front of the survivor first: their own side's, every time.
        boolean ownFirst = true;
        for (String a : absentialis.sim.ZoneState.FACTIONS) {
            absentialis.gameplay.missions.Contracts cs = new absentialis.gameplay.missions.Contracts();
            cs.refill(99, 10, a);
            int own = 0, lead = 0;
            for (var k : cs.board) if (k.giver.equals(a)) own++;
            for (var k : cs.board) { if (!k.giver.equals(a)) break; lead++; }
            if (cs.board.size() != 10 || own < 2 || lead != own) ownFirst = false;
            boards.put(a, own + " of its own, first on a board of " + cs.board.size());
        }
        t.check("each leaning is shown its own side's work first, on the same board of ten", ownFirst, boards.toString());

        // ---- population settings scale the Zone, per group
        World w = world(SEEDS[0]);
        absentialis.sim.ZoneState z = absentialis.sim.ZoneState.fresh();
        absentialis.sim.Agents base = absentialis.sim.Agents.seed(w, z, 5), dbl = absentialis.sim.Agents.seed(w, z, 5, k -> k.equals("all") ? 2f : 1f), noInf = absentialis.sim.Agents.seed(w, z, 5, k -> k.equals("infected") ? 0.25f : k.equals("military") ? 2f : 1f);
        int b0 = base.living("infected"), b1 = base.living("military"), d0 = dbl.living("infected"), n0i = noInf.living("infected"), n1m = noInf.living("military"), n2 = noInf.living("raiders"), b2 = base.living("raiders");
        t.check("population settings scale how many people the Zone holds, overall and per group", Math.abs(d0 - b0 * 2) <= b0 * 0.12f && n0i < b0 * 0.35f && n1m > b1 * 1.7f && Math.abs(n2 - b2) <= b2 * 0.15f + 3 && dbl.agents.size() > base.agents.size() * 1.8f,
                "designed: " + b0 + " infected, " + b1 + " military, " + b2 + " bandits (" + base.agents.size() + " in all); x2 everyone: " + dbl.agents.size() + "; infected x0.25 and military x2: " + n0i + " infected, " + n1m + " military, " + n2 + " bandits");

        // ---- ranks and the leaderboard
        p.agents = base;
        absentialis.rivalry.RivalryWorld.engine(p);
        java.util.List<absentialis.sim.Leaderboard.Row> rows = absentialis.sim.Leaderboard.rows(p);
        int dead0 = 0, ranked = 0;
        boolean sorted = true;
        for (int i = 0; i < rows.size(); i++) { if (rows.get(i).rank() >= 1 && !rows.get(i).title().isEmpty()) ranked++; if (i > 0 && rows.get(i).xp() > rows.get(i - 1).xp()) sorted = false; }
        absentialis.sim.Agents.Agent victim = null;
        for (absentialis.sim.Agents.Agent a : base.agents) if (a.alive && !a.infected()) { victim = a; break; }
        int before = rows.size();
        victim.alive = false;
        absentialis.sim.Agents.Agent climber = null;
        for (absentialis.sim.Agents.Agent a : base.agents) if (a.alive && !a.infected()) { climber = a; break; }
        int r0 = climber.rank();
        climber.kills += 40;
        java.util.List<absentialis.sim.Leaderboard.Row> rows2 = absentialis.sim.Leaderboard.rows(p);
        for (absentialis.sim.Leaderboard.Row r : rows2) if (r.name().equals(victim.name) && r.kind().equals("agent") && r.xp() == victim.xp()) dead0++;
        int[] cnt = absentialis.sim.Leaderboard.counts(rows);
        t.check("everyone alive has a Rank and is on the leaderboard; the dead are not; kills raise Rank", ranked == rows.size() && sorted && rows2.size() == before - 1 && climber.rank() > r0 && cnt[0] == 5 && cnt[1] >= 8 && cnt[2] > 50,
                rows.size() + " rows (" + cnt[0] + " yours, " + cnt[1] + " group leaders and lieutenants, " + cnt[2] + " others), every one ranked; one death -> " + rows2.size() + " rows; 40 kills took " + climber.name + " from Rank " + r0 + " to " + climber.rank());
        victim.alive = true; climber.kills -= 40;

        // ---- permadeath and recruitment
        absentialis.gameplay.squad.Roster ro = p.squad;
        absentialis.gameplay.squad.Roster.Mate gone = ro.mates.get(0);
        gone.xp = absentialis.gameplay.progression.Progression.xpToReach(20); gone.kills = 31;
        String goneName = gone.name;
        ro.bury(gone, 3, "killed by a bandit rifleman");
        p.applyCapacity();          // re-opening the roster must not conjure a replacement
        int afterDeath = ro.mates.size();
        p.prog.bestRank = Math.max(p.prog.bestRank, 16);      // four places on the roster (0.7: Ranks 4, 8, 12 and 16)
        java.util.List<absentialis.gameplay.squad.Roster.Offer> offers = ro.offers(p.seed, p.zone.day, 10);
        absentialis.gameplay.squad.Roster.Offer o = offers.get(0);
        String poor = ro.hire(o, p.prog.squadSlots(), 10);
        String hired = ro.hire(o, p.prog.squadSlots(), 1_000_000);
        boolean offerGone = ro.offers(p.seed, p.zone.day, 10).size() == offers.size() - 1;
        String full = ro.hire(offers.get(1), p.prog.squadSlots(), 1_000_000);
        absentialis.gameplay.squad.Roster back = absentialis.gameplay.squad.Roster.fromJson(absentialis.engine.util.Json.parseObject(absentialis.engine.util.Json.write(ro.toJson())));
        t.check("a squadmate who dies is gone for good - rank, record and all - and a replacement has to be recruited and paid for", afterDeath == 3 && ro.byId(gone.id) == null && ro.fallen.size() == 1 && ro.fallen.get(0).name().equals(goneName) && poor != null && hired == null && ro.mates.size() == 4
                        && offerGone && full != null && offers.size() == 3 && o.price() >= absentialis.gameplay.squad.Roster.RECRUIT_BASE && back.fallen.size() == 1 && back.mates.size() == 4,
                goneName + " (" + ro.fallen.get(0).title() + ", " + ro.fallen.get(0).kills() + " kills) struck off; roster 4 -> " + afterDeath + " and not refilled; hired " + o.mate().name + " for " + o.price() + " coins; no room for a fifth: " + full);

        // ---- rivalry in the raid: what a group's people see is what the group knows
        absentialis.scene.Raid r = SelfTests.stage(p);
        r.freezeAI = true;
        r.player.x = 300; r.player.z = 430; r.player.y = G;
        absentialis.entity.Actor s1 = r.debugSpawn("raiders", 310, 430, G), s2 = r.debugSpawn("raiders", 312, 432, G), lone = r.debugSpawn("raiders", 300, 560, G);
        for (absentialis.entity.Actor a : new absentialis.entity.Actor[]{s1, s2, lone}) { r.rivalry.assign(a, 7); a.squad = 4242; }
        absentialis.rivalry.RivalryState.Group grp = r.rivalry.group(s1);
        float f0 = grp.playerRelation.favor;
        int ev0 = r.rivalry.engine.s.log.size();
        r.update(1 / 60f);
        r.damageActor(s1, r.player, 500, absentialis.gameplay.items.Content.def("ammo.rifle"), "torso", s1.x, s1.y + 1, s1.z);
        float f1 = grp.playerRelation.favor;
        boolean witnessedKill = s1.dead && f1 < f0 - 3;
        // the other one, far off with nobody of theirs in sight
        r.player.x = 300; r.player.z = 550;
        r.update(1 / 60f);
        int mem0 = grp.groupMemories.size();
        r.damageActor(lone, r.player, 500, absentialis.gameplay.items.Content.def("ammo.rifle"), "torso", lone.x, lone.y + 1, lone.z);
        boolean unseen = lone.dead && Math.abs(grp.playerRelation.favor - f1) < 0.01f;
        boolean lossOnly = false;
        for (absentialis.rivalry.RivalryState.Memory m : grp.groupMemories) if ("LOSS_UNKNOWN".equals(m.type)) lossOnly = true;
        t.check("in a raid, a killing seen by the group costs favor with that group; one nobody of theirs saw does not", grp != null && witnessedKill && unseen && lossOnly && r.rivalry.engine.s.log.size() >= ev0 + 2,
                grp.name + ": favor " + Math.round(f0) + " -> " + Math.round(f1) + " (witnessed) -> " + Math.round(grp.playerRelation.favor) + " (unwitnessed; they know only that someone is missing); " + (r.rivalry.engine.s.log.size() - ev0) + " events on record");
        r.actors.remove(s1); r.actors.remove(s2); r.actors.remove(lone);
    }

    // ------------------------------------------------------------------ weapons: what takes a head off
    /** Shoots a fresh, unarmoured soldier in the head from a given range until dead. Returns {decapitated ? 1 : 0, rounds fired}. */
    private static int[] headshots(absentialis.scene.Raid r, String weaponId, float range, int index) {
        absentialis.entity.Actor p = r.player;
        absentialis.entity.Actor v = r.debugSpawn("military", p.x + range, p.z + index * 0.001f, G);
        v.helmet = false; v.armorClass = 0; v.armorDur = 0;
        absentialis.gameplay.items.ItemInstance w = new absentialis.gameplay.items.ItemInstance(weaponId);
        absentialis.gameplay.items.ItemDef ammo = absentialis.gameplay.items.Content.ammoFor(w.def().weapon.caliber);
        r.update(1 / 60f);
        int shots = 0;
        while (!v.dead && shots < 30) {
            float ox = p.x + 0.4f, oy = p.y + 1.6f, oz = p.z, tx = v.x, ty = v.y + v.height() * 0.93f, tz = v.z;
            float dx = tx - ox, dy = ty - oy, dz = tz - oz, l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            v.vital = absentialis.entity.Vital.ACTIVE;
            r.fireProjectiles(p, w, ammo, ox, oy, oz, dx / l, dy / l, dz / l, 0);
            shots++;
            for (int i = 0; i < 12; i++) r.update(1 / 60f);
        }
        boolean off = absentialis.scene.Gore.lost(v, absentialis.scene.Gore.HEAD);
        if (System.getenv("ABS_DEBUG06") != null) System.out.println("    dbg " + weaponId + " region=" + v.hitRegion + " headPellet=" + (r.time - v.headPelletAt) + " decaps=" + r.gore.decapitations + " off=" + off + " range=" + v.lastHitRange + " wpn=" + v.lastHitWeapon + " force=" + v.hitForce + " severed=" + Integer.toBinaryString(v.severed));
        r.actors.remove(v);
        return new int[]{v.dead && off ? 1 : 0, shots, v.dead ? 1 : 0};
    }

    private static void combat(SelfTests04.Check t) {
        System.out.println("[v0.6 weapons: shotguns decapitate at close range, handguns never do]");
        absentialis.gameplay.items.Content.load();
        absentialis.scene.Raid r = SelfTests.stage(new absentialis.persistence.Profile());
        r.freezeAI = true;
        r.player.x = 320; r.player.z = 360; r.player.y = G;
        int n = 8, sg = 0, sgShots = 0, hg = 0, hgKills = 0, hgShots = 0, sgFar = 0;
        for (String id : new String[]{"weapon.striker", "weapon.pump12"}) for (int i = 0; i < n / 2; i++) { int[] o = headshots(r, id, 4f, i); sg += o[0]; sgShots += o[1]; }
        for (int i = 0; i < n; i++) { int[] o = headshots(r, "weapon.pm9", 4f, i); hg += o[0]; hgKills += o[2]; hgShots += o[1]; }
        for (int i = 0; i < n; i++) { int[] o = headshots(r, "weapon.whitetail", 3f, i); hg += o[0]; hgKills += o[2]; hgShots += o[1]; }
        t.check("a close-range shotgun head shot is an instant decapitation", sg == n && sgShots == n, sg + " of " + n + " heads off, " + sgShots + " shells for " + n + " kills");
        t.check("handguns never decapitate on a head shot", hg == 0 && hgKills == 2 * n, hg + " heads off in " + hgKills + " handgun head-shot kills (" + hgShots + " rounds)");
        absentialis.gameplay.CombatConfig c = r.combat;
        t.check("decapitation rules are data: shotgun certain inside its close range, handgun never, rifle by force", c.decap("shotgun").chance(10, 3) == 1f && c.decap("shotgun").chance(200, 8) == 0f && c.decap("handgun").chance(999, 1) == 0f && c.decap("rifle").chance(60, 5) == 0f && c.decap("rifle").chance(200, 50) > 0.5f,
                String.format("shotgun@5m %.2f, shotgun@30m force 60 %.2f, handgun %.2f, rifle force 200 %.2f", c.decap("shotgun").chance(10, 5), c.decap("shotgun").chance(60, 30), c.decap("handgun").chance(999, 1), c.decap("rifle").chance(200, 50)));
    }

    // ------------------------------------------------------------------ movement, bodies, doors, ladders, glass, steering
    private static final java.util.List<int[]> EDITS = new java.util.ArrayList<>();
    private static void put(World w, int x, int y, int z, int id) { EDITS.add(new int[]{x, y, z, w.get(x, y, z)}); w.set(x, y, z, id); }
    private static void restore(World w) { for (int i = EDITS.size() - 1; i >= 0; i--) { int[] e = EDITS.get(i); w.set(e[0], e[1], e[2], e[3]); } EDITS.clear(); }
    private static void run(absentialis.scene.Raid r, float seconds) { for (int i = 0; i < Math.round(seconds * 60); i++) r.update(1 / 60f); }

    /** Walks a body in a straight line under plain physics (what the player's own movement code does). */
    private static void walk(World w, absentialis.entity.Actor a, float vx, float vz, float seconds) {
        for (int i = 0; i < Math.round(seconds * 60); i++) { a.vx = vx; a.vz = vz; absentialis.entity.Physics.move(w, a, 1 / 60f); }
    }

    private static absentialis.entity.Actor seeker(absentialis.scene.Raid r, float x, float z, float tx, float tz) {
        absentialis.entity.Actor a = r.debugSpawn("walker", x, z, G);
        a.homeX = tx; a.homeZ = tz;
        lead(r, a, tx, tz);
        return a;
    }

    /** 0.8: sends an agent somewhere the way the game does - by giving its mind a place to go and look at (NeuralAI). Called again now and then, it keeps the place fresh. */
    private static void lead(absentialis.scene.Raid r, absentialis.entity.Actor a, float tx, float tz) {
        if (r.neural.mind(a.id) == null) r.neural.enroll(a.id, 0, a.faction, a.archetype, false);
        r.neural.onDispatch(a.id, new absentialis.neuralai.api.Vec3(tx, a.y, tz));
    }

    private static void physics(SelfTests04.Check t) {
        System.out.println("[v0.6 movement: no auto-jump, jumps and ladders for agents, bodies collide, doors, glass, steering]");
        absentialis.gameplay.items.Content.load();
        absentialis.scene.Raid r = SelfTests.stage(new absentialis.persistence.Profile());
        World w = r.world;
        absentialis.entity.Actor p = r.player;

        // ---- walking into a block does not lift a body onto it; a stair does; a jump clears it
        put(w, 304, G, 300, absentialis.world.Block.CONCRETE);
        p.x = 301.5f; p.y = G; p.z = 300.5f; p.vy = 0; p.onGround = true;
        walk(w, p, 3.5f, 0, 2f);
        boolean blocked = p.y < G + 0.05f && p.x < 304f;
        p.vy = absentialis.entity.Physics.JUMP;
        float top = 0;
        boolean jumped = false;
        for (int i = 0; i < 90; i++) { p.vx = 3.5f; p.vz = 0; absentialis.entity.Physics.move(w, p, 1 / 60f); top = Math.max(top, p.y); jumped |= p.onGround && p.y > G + 0.95f && p.x > 304f; }
        t.check("walking into a block no longer lifts you onto it; a jump does", blocked && jumped, String.format("walked into it for 2 s: stopped=%s; jumped: landed on top=%s (peak %.2f)", blocked, jumped, top - G));
        // (1.0: a stair is a stair - treads rising to something - so it is given the block it leads up onto; it is walked up, a tread at a time)
        put(w, 304, G, 303, absentialis.world.Block.STAIRS_CONCRETE);
        put(w, 305, G, 303, absentialis.world.Block.CONCRETE);
        p.x = 301.5f; p.y = G; p.z = 303.5f; p.vy = 0; p.onGround = true; p.stepped = 0;
        boolean stair = false;
        for (int i = 0; i < 72; i++) { p.vx = 3.5f; p.vz = 0; absentialis.entity.Physics.move(w, p, 1 / 60f); stair |= p.onGround && p.y > G + 0.95f; }
        t.check("stairs are still climbed by walking", stair && p.stepped > 0.9f, String.format("on the tread=%s, stepped %.2f", stair, p.stepped));

        // ---- an agent meets a long one-block kerb with no way round it worth taking: it jumps it, it is not carried over
        for (int z = 285; z <= 315; z++) put(w, 330, G, z, absentialis.world.Block.CONCRETE);
        absentialis.entity.Actor j = seeker(r, 326.5f, 300.5f, 335.5f, 300.5f);
        float jy = G; boolean air = false;
        for (int i = 0; i < 60 * 25 && j.distXZ(335.5f, 300.5f) > 2.4f; i++) { if (i % 240 == 239) lead(r, j, 335.5f, 300.5f); r.update(1 / 60f); jy = Math.max(jy, j.y); air |= !j.onGround && j.y > G + 0.3f; }
        t.check("agents are not lifted onto blocks either: a kerb in the way is jumped", j.distXZ(335.5f, 300.5f) <= 2.4f && air && j.stepped == 0, String.format("reached=%s airborne=%s peak %.2f stepped %.2f", j.distXZ(335.5f, 300.5f) <= 2.4f, air, jy - G, j.stepped));
        r.actors.remove(j);

        // ---- ladders: a solid block of building, a ladder up one face, a target in the middle of its roof
        for (int x = 350; x <= 354; x++) for (int z = 298; z <= 302; z++) for (int y = G; y <= G + 4; y++) put(w, x, y, z, absentialis.world.Block.CONCRETE);
        for (int y = G; y <= G + 4; y++) put(w, 349, y, 300, absentialis.world.Block.LADDER);
        int[] path = w.nav.path(343.5f, G, 300.5f, 352.5f, 300.5f, 4000, true);
        boolean pathUp = path != null && path[path.length - 2] == G + 5;
        absentialis.entity.Actor c = seeker(r, 343.5f, 300.5f, 352.5f, 300.5f);
        boolean climbed = false;
        for (int i = 0; i < 60 * 40 && !(c.y > G + 4.9f && c.distXZ(352.5f, 300.5f) < 2.4f); i++) { if (i % 240 == 239) lead(r, c, 352.5f, 300.5f); r.update(1 / 60f); climbed |= c.climbing; }
        boolean up = c.y > G + 4.9f && c.distXZ(352.5f, 300.5f) < 2.4f;
        // and back down again
        c.homeX = 343.5f; c.homeZ = 300.5f; c.path = null;
        lead(r, c, 343.5f, 300.5f);
        float hp0 = c.hp;
        for (int i = 0; i < 60 * 40 && !(c.y < G + 0.1f && c.distXZ(343.5f, 300.5f) < 2.4f); i++) { if (i % 240 == 239) lead(r, c, 343.5f, 300.5f); r.update(1 / 60f); }
        boolean down = c.y < G + 0.1f && c.distXZ(343.5f, 300.5f) < 2.4f;
        t.check("navigation routes up a ladder (the path ends on the roof)", pathUp, path == null ? "no path" : path.length / 3 + " cells, ends at y+" + (path[path.length - 2] - G));
        t.check("an agent climbs a ladder to a roof and comes back down it", up && climbed && down && c.hp >= hp0, String.format("up=%s (held the rungs=%s) down=%s", up, climbed, down));
        r.actors.remove(c);

        // ---- characters do not pass through each other
        absentialis.entity.Actor a1 = r.debugSpawn("survivors", 370.5f, 300.5f, G), a2 = r.debugSpawn("survivors", 370.55f, 300.5f, G);
        r.freezeAI = true;
        p.x = 372; p.z = 312; p.y = G;                 // close by: bodies are only simulated in full near the player
        run(r, 0.5f);
        float apart = a1.distXZ(a2.x, a2.z);
        a1.x = 368.5f; a1.z = 300.5f; a2.x = 371.5f; a2.z = 300.5f;
        float worst = 0, startGap = 3f;
        for (int i = 0; i < 240; i++) { a1.vx = 4f / 0.6f; a1.pvx = a1.vx; a1.vz = 0; r.update(1 / 60f); worst = Math.max(worst, r.bodyOverlap); }
        boolean ahead = a1.x < a2.x;
        t.check("two characters cannot occupy the same place, and one cannot walk through another", apart >= a1.radius + a2.radius - 0.03f && ahead && worst < 0.12f && r.bodyContacts > 0,
                String.format("spawned together they stand %.2f m apart; walking into one: deepest overlap %.2f m, still behind=%s (%.1f,%.1f / %.1f,%.1f), contacts %d", apart, worst, ahead, a1.x, a1.z, a2.x, a2.z, r.bodyContacts));
        r.actors.remove(a1); r.actors.remove(a2);

        // ---- doors: a wall with a single and a double door in it
        for (int x = 390; x <= 402; x++) for (int y = G; y <= G + 2; y++) put(w, x, y, 300, absentialis.world.Block.CONCRETE);
        for (int y = G; y <= G + 1; y++) { put(w, 393, y, 300, absentialis.world.Block.DOOR_WOOD); put(w, 398, y, 300, absentialis.world.Block.DOOR_DOUBLE_WOOD); put(w, 399, y, 300, absentialis.world.Block.DOOR_DOUBLE_WOOD); }
        r.doors.scan();
        absentialis.scene.Doors.Door single = r.doors.at(393, G, 300), dbl = r.doors.at(398, G, 300);
        single.locked = false; single.angle = single.target = 0; dbl.locked = false; dbl.angle = dbl.target = 0;
        p.x = 393.5f; p.z = 302.5f; p.y = G; p.vx = p.vz = 0;
        r.doors.use(single, p);
        run(r, 1.2f);
        // stand in the frame, off centre - the position 0.5 missed - and shut the door on yourself
        p.x = 393.72f; p.z = 300.5f; p.y = G; p.vy = 0;
        r.doors.use(single, p);
        float lift = 0;
        for (int i = 0; i < 180; i++) { r.update(1 / 60f); p.vx = p.vz = 0; absentialis.entity.Physics.move(w, p, 1 / 60f); lift = Math.max(lift, p.y - G); }
        boolean held = !single.solid && lift < 0.06f;
        p.x = 393.5f; p.z = 303.5f;
        r.doors.use(single, p); run(r, 0.2f);
        if (Math.abs(single.target) > 0.1f) r.doors.use(single, p);
        run(r, 1.5f);
        t.check("a door shut on someone standing in it rests against them: nobody is pushed up a floor", held && single.solid, String.format("held off the body=%s, lifted %.2f m, shuts once clear=%s", held, lift, single.solid));

        boolean one = dbl != null && dbl == r.doors.at(399, G, 300) && dbl == r.doors.at(399, G + 1, 300) && dbl.span == 2 && r.doors.doubles == 1;
        r.doors.use(dbl, p);
        run(r, 1.2f);
        boolean bothOpen = !w.solid(398, G, 300) && !w.solid(399, G, 300) && r.doors.state(dbl) == absentialis.scene.Doors.State.OPEN;
        r.doors.use(dbl, p);
        run(r, 1.4f);
        boolean bothShut = w.solid(398, G, 300) && w.solid(399, G + 1, 300);
        int slabs0 = r.doors.slabs.size();
        for (int i = 0; i < 12 && !dbl.destroyed; i++) r.doors.hit(dbl, 398.05f, G + 1f, 300.5f, 40, 0, 1, p.id, "bullet");
        boolean gone = dbl.destroyed && w.get(398, G, 300) == 0 && w.get(399, G + 1, 300) == 0 && r.doors.slabs.size() - slabs0 == 2 && r.doors.at(399, G, 300) == null;
        t.check("a double door is one object two cells wide: one entity, opened, shut and destroyed as one", one && bothOpen && bothShut && gone,
                String.format("one entity=%s both columns open=%s shut=%s; off its hinges: both columns clear and two leaves on the ground=%s", one, bothOpen, bothShut, gone));

        // ---- a body pushes an open leaf out of its way
        r.doors.use(single, p); run(r, 1.2f);
        float full = single.angle;
        single.angle = single.target = Math.signum(full) * 0.62f;       // left ajar: half across the doorway
        absentialis.entity.Actor walker = r.debugSpawn("survivors", 393.5f, 300.5f - Math.signum(full) * 0 + 2.2f, G);
        int pushes0 = r.doors.pushes;
        float a0 = Math.abs(single.angle), minZ = walker.z;
        for (int i = 0; i < 300; i++) { walker.vx = 0; walker.vz = -2.2f / 0.6f; walker.pvz = walker.vz; walker.pvx = 0; r.update(1 / 60f); minZ = Math.min(minZ, walker.z); }
        t.check("a body walking into an open door swings it out of the way and passes", r.doors.pushes > pushes0 && minZ < 299.4f && !single.solid, String.format("%d contacts, leaf %.2f -> %.2f rad, walker reached z %.1f", r.doors.pushes - pushes0, a0, Math.abs(single.angle), minZ));
        r.actors.remove(walker);

        // ---- glass breaks into shards that fall and stay
        put(w, 410, G + 1, 300, absentialis.world.Block.GLASS);
        put(w, 409, G + 1, 300, absentialis.world.Block.CONCRETE); put(w, 411, G + 1, 300, absentialis.world.Block.CONCRETE); put(w, 410, G, 300, absentialis.world.Block.CONCRETE);
        int s0 = r.doors.slabs.size();
        r.breakDX = 0; r.breakDZ = 1;
        r.damageBlock(410, G + 1, 300, 50, p.id);
        r.breakDX = r.breakDZ = 0;
        int shards = 0;
        for (absentialis.scene.Doors.Slab s : r.doors.slabs) if (s.kind == absentialis.scene.Doors.SHARD) shards++;
        run(r, 4f);
        int resting = 0, beyond = 0;
        for (absentialis.scene.Doors.Slab s : r.doors.slabs) if (s.kind == absentialis.scene.Doors.SHARD) { if (s.rest && s.y < G + 1.2f) resting++; if (s.z > 300.6f) beyond++; }
        t.check("a broken window leaves physical shards: thrown the way the round was going, fallen, lying where they landed", w.get(410, G + 1, 300) == 0 && shards >= 6 && resting >= shards - 1 && beyond >= shards / 2,
                String.format("%d shards, %d at rest on the ground, %d on the far side of the frame", shards, resting, beyond));

        // ---- steering: A turns the wheels (and the car) to the driver's left, D to the right
        absentialis.vehicle.CarHandlingData.load();
        float[] side = new float[2];
        for (int k = 0; k < 2; k++) {
            absentialis.vehicle.CarVehicle car = new absentialis.vehicle.CarVehicle(absentialis.vehicle.CarHandlingData.all().get(0));
            car.place(450, G, 420, 0);
            car.engineOn = true;
            for (int i = 0; i < 100; i++) car.step(w);
            float yaw = absentialis.scene.VehicleSystem.playerYaw(car), rx = (float) Math.cos(yaw), rz = (float) -Math.sin(yaw), x0 = car.body.px, z0 = car.body.pz;
            for (int i = 0; i < 150; i++) { car.input.fromKeys(true, false, k == 0, k == 1, false, car.forwardSpeed()); car.wake(); car.step(w); }
            side[k] = (car.body.px - x0) * rx + (car.body.pz - z0) * rz;
        }
        t.check("vehicle steering: A carries the car to the driver's left, D to the right", side[0] < -1f && side[1] > 1f, String.format("A: %.1f m to the right, D: %.1f m to the right (as the driver sees it)", side[0], side[1]));
        restore(w);
        r.doors.scan();
    }

    // ------------------------------------------------------------------ audio: every gun is heard, shotguns hit, no hum or static in the beds
    private static final absentialis.engine.audio.gun.AcousticEnvironment.Geometry FIELD = new absentialis.engine.audio.gun.AcousticEnvironment.Geometry() {
        public float ray(float ox, float oy, float oz, float dx, float dy, float dz, float max, float[] hit) { if (dy < -0.9f) { hit[0] = ox; hit[1] = oy - 1.6f; hit[2] = oz; hit[3] = 0.5f; return 1.6f; } return -1; }
        public float openness(float ax, float ay, float az, float bx, float by, float bz) { return 1; }
    };

    /** Floor for the loudest 100 ms of any firearm at the shooter, measured after the bus limiter (which holds peaks at 0.91). */
    static final float QUIET_RMS = 0.062f;

    /** {peak, loudest 100 ms RMS, energy below 250 Hz as a share of the whole} of one round rendered through the gun bus. */
    static float[] measureShot(absentialis.engine.audio.gun.GunAudio g, String weapon, boolean suppressed, boolean local, float distance) {
        int block = 1024, blocks = 70;
        float[] scratch = new float[block * 2], all = new float[block * blocks];
        g.forget();
        absentialis.engine.audio.gun.ShotEvent e = g.shot(FIELD, weapon, local ? 1 : 2, 60, distance, 1.6f, 0, 0, -1, suppressed, local, 0, 1.6f, 0, 0);
        if (e == null) return new float[]{0, 0, 0};
        for (int b = 0; b < blocks; b++) {
            java.util.Arrays.fill(scratch, 0f);
            g.mixer.mix(scratch, block, 1f);
            for (int i = 0; i < block; i++) all[b * block + i] = (scratch[i * 2] + scratch[i * 2 + 1]) * 0.5f;
        }
        float peak = 0, best = 0, low = 0, tot = 0, lp = 0;
        int win = 4410;
        double acc = 0;
        for (int i = 0; i < all.length; i++) {
            float v = all[i];
            peak = Math.max(peak, Math.abs(v));
            acc += v * v;
            if (i >= win) acc -= all[i - win] * all[i - win];
            if (i >= win - 1) best = Math.max(best, (float) Math.sqrt(Math.max(0, acc) / win));
            lp += 0.035f * (v - lp);                      // one pole at about 250 Hz
            low += lp * lp; tot += v * v;
        }
        return new float[]{peak, best, tot <= 0 ? 0 : low / tot};
    }

    /** Level of a steady tone in a signal relative to its RMS (Goertzel): about 1.4 for a pure sine, near 0 for noise. */
    static float tonal(float[] s, float hz, int rate) {
        double w = 2 * Math.PI * hz / rate, c = 2 * Math.cos(w), s0, s1 = 0, s2 = 0, e = 0;
        for (float v : s) { s0 = v + c * s1 - s2; s2 = s1; s1 = s0; e += v * v; }
        double mag = Math.sqrt(Math.max(0, s1 * s1 + s2 * s2 - c * s1 * s2)) * 2 / s.length, rms = Math.sqrt(e / s.length);
        return rms < 1e-9 ? 0 : (float) (mag / rms);
    }

    /** Loudest 20 ms of high-passed signal against the median 20 ms: bursts of static stand far above the bed they sit in. */
    static float burstiness(float[] s) {
        int win = 882, n = s.length / win;
        float[] e = new float[n];
        float prev = 0;
        for (int k = 0; k < n; k++) { double a = 0; for (int i = 0; i < win; i++) { float v = s[k * win + i], hp = v - prev; prev = v; a += hp * hp; } e[k] = (float) Math.sqrt(a / win); }
        float[] sorted = e.clone();
        java.util.Arrays.sort(sorted);
        float med = sorted[n / 2], max = sorted[n - 1];
        return med < 1e-9f ? (max > 1e-6f ? 999 : 0) : max / med;
    }

    private static void audio(SelfTests04.Check t) {
        System.out.println("[v0.6 audio: every weapon is heard at a consistent level, shotguns carry weight, ambience has no hum or static]");
        absentialis.gameplay.items.Content.load();
        absentialis.engine.audio.gun.GunAudio g = new absentialis.engine.audio.gun.GunAudio();
        g.load(java.nio.file.Path.of("assets", "audio"));
        java.util.Map<String, float[]> bank = new java.util.HashMap<>();
        new absentialis.engine.audio.SoundBank().build(bank);
        new absentialis.engine.audio.SoundBank04().build(bank);
        new absentialis.engine.audio.SoundBank06().build(bank);
        StringBuilder table = new StringBuilder(), quiet = new StringBuilder(), silent = new StringBuilder();
        float minRms = 9, maxRms = 0, shotgunLow = 9, shotgunRms = 9, rifleRms = 0, pistolRms = 0;
        int rifles = 0, pistols = 0, guns = 0;
        for (absentialis.gameplay.items.ItemDef d : absentialis.gameplay.items.Content.ITEMS.values()) {
            if (d.weapon == null || d.weapon.melee()) continue;
            guns++;
            float[] m;
            if (g.handles(d.id)) m = measureShot(g, d.id, false, true, 0);
            else {
                // no recorded profile: the one-shot the firing code falls back to, at the gain it plays it
                float[] s = bank.get(d.weapon.sound);
                float pk = 0; double a = 0; int win = 4410, n = s == null ? 0 : Math.min(s.length, win);
                for (int i = 0; i < n; i++) { pk = Math.max(pk, Math.abs(s[i])); a += s[i] * s[i]; }
                float gain = absentialis.scene.RaidScreen.fallbackShotGain(d.weapon);
                m = new float[]{pk * gain, n == 0 ? 0 : (float) Math.sqrt(a / win) * gain, 0};
            }
            table.append(String.format("%s %.2f/%.3f/%.2f  ", d.id.substring(7), m[0], m[1], m[2]));
            if (m[0] < 0.02f) silent.append(d.id).append(' ');
            boolean special = "special".equals(d.weapon.family);
            if (!special) {
                minRms = Math.min(minRms, m[1]); maxRms = Math.max(maxRms, m[1]);
                if (m[1] < QUIET_RMS) quiet.append(d.id).append(' ');
                if ("shotgun".equals(d.weapon.family)) { shotgunLow = Math.min(shotgunLow, m[2]); shotgunRms = Math.min(shotgunRms, m[1]); }
                else if ("handgun".equals(d.weapon.family) || "magnum".equals(d.weapon.family)) { pistolRms += m[1]; pistols++; }
                else { rifleRms += m[1]; rifles++; }
            }
        }
        System.out.println("    peak / loudest 100 ms RMS / share of energy below 250 Hz:  " + table.toString().trim());
        t.check("every weapon that fires makes a sound (the player's own shot, through the path the game uses)", guns >= 17 && silent.length() == 0, silent.length() == 0 ? guns + " weapons" : "silent: " + silent);
        t.check("no firearm is quiet: the loudest 100 ms of each is at least " + QUIET_RMS + " RMS (0.5: 0.029), and the spread is within 6.6 dB (0.5: 11.6 dB)", quiet.length() == 0 && maxRms / Math.max(1e-6f, minRms) < 2.14f, String.format("%.3f .. %.3f RMS%s", minRms, maxRms, quiet.length() == 0 ? "" : ", quiet: " + quiet));
        float rifleAvg = rifles == 0 ? 0 : rifleRms / rifles, pistolAvg = pistols == 0 ? 0 : pistolRms / pistols;
        t.check("shotguns are the heaviest report: louder than the average rifle and handgun, with real low end", shotgunRms > rifleAvg && shotgunRms > pistolAvg * 1.1f && shotgunLow > 0.22f,
                String.format("quietest shotgun %.3f RMS (rifles %.3f, handguns %.3f), least low-band share %.2f", shotgunRms, rifleAvg, pistolAvg, shotgunLow));
        // ---- ambience beds: wind, city, industry. None may carry a mains hum or bursts of static.
        StringBuilder beds = new StringBuilder();
        float worstTone = 0, worstBurst = 0;
        String worst = "";
        for (String name : new String[]{"amb_machines", "amb_city", "amb_wind", "amb_interior"}) {
            float[] s = bank.get(name);
            if (s == null) { beds.append(name).append(" MISSING "); worstTone = 99; continue; }
            float tone = 0;
            for (float hz : new float[]{41, 50, 60, 100, 110, 120, 180}) tone = Math.max(tone, tonal(s, hz, absentialis.engine.audio.UltraEngine.RATE));
            float burst = burstiness(s);
            beds.append(String.format("%s tone %.2f burst %.1f  ", name, tone, burst));
            if (tone > worstTone) { worstTone = tone; worst = name; }
            worstBurst = Math.max(worstBurst, burst);
        }
        t.check("no ambience bed holds a steady hum (50/60/120 Hz and neighbours under 0.25 of its level)", worstTone < 0.25f, beds.toString().trim() + (worstTone >= 0.25f ? "  <- " + worst : ""));
        t.check("no ambience bed carries bursts of static (loudest 20 ms of hiss under 6x the median)", worstBurst < 6f, String.format("worst %.1fx", worstBurst));
    }

    // ------------------------------------------------------------------ buildings, textures, fog, flashlight, herbs, ammunition, feeding
    private static int colours(absentialis.engine.voxel.VoxModel m, int r0, int g0, int b0, int tol) {
        int n = 0;
        for (int c : m.col) if ((c >>> 24) != 0 && Math.abs(((c >> 16) & 255) - r0) <= tol && Math.abs(((c >> 8) & 255) - g0) <= tol && Math.abs((c & 255) - b0) <= tol) n++;
        return n;
    }

    private static void worldAndItems(SelfTests04.Check t) {
        System.out.println("[v0.6 world and items: stairs, entrances, facades, furnishing, textures, fog, the flashlight, herbs, ammunition, the infected feeding]");
        absentialis.gameplay.items.Content.load();
        // ---- buildings, on every test seed
        for (long seed : SEEDS) {
            World w = world(seed);
            int treads = 0, blocked = 0, flights = 0, rooms = 0, unreachable = 0, doubles = 0, halves = 0, balconies = 0, wallCells = 0, pieces = 0, tall = 0, windows = 0;
            for (absentialis.world.MapData.Building b : w.map.buildings) {
                for (int x = b.x0; x <= b.x1; x++) for (int z = b.z0; z <= b.z1; z++) for (int y = G; y <= Math.min(World.H - 4, b.topY + 1); y++) {
                    int id = w.get(x, y, z);
                    if (id == 0) continue;
                    absentialis.world.Block bl = absentialis.world.Block.ALL[id];
                    if (bl.is(absentialis.world.Block.STEP)) {
                        treads++;
                        // a body rising onto this tread needs the two cells over it
                        if (w.solid(x, y + 1, z) || w.solid(x, y + 2, z)) blocked++;
                        // the top of a flight: a tread level with a slab (the storey's floor is at G-1 + n*FLOOR_H)
                        if ((y - (G - 1)) % absentialis.world.gen.Gen.FLOOR_H == 0 && y > G) flights++;
                    }
                    if (absentialis.world.Block.doubleDoor(id) && y == G) { doubles++; }
                    if ((id == absentialis.world.Block.GLASS || id == absentialis.world.Block.GLASS_DIRTY || id == absentialis.world.Block.GLASS_CRACKED || id == absentialis.world.Block.GLASS_BLUE) && (x == b.x0 || x == b.x1 || z == b.z0 || z == b.z1)) {
                        windows++;
                        int up = w.get(x, y + 1, z);
                        if (up == absentialis.world.Block.GLASS || up == absentialis.world.Block.GLASS_DIRTY || up == absentialis.world.Block.GLASS_CRACKED || up == absentialis.world.Block.GLASS_BLUE) tall++;
                    }
                }
                if ("apartment".equals(b.style)) for (int x = b.x0 - 1; x <= b.x1 + 1; x++) for (int z = b.z0 - 1; z <= b.z1 + 1; z++) {
                    if (x >= b.x0 && x <= b.x1 && z >= b.z0 && z <= b.z1) continue;
                    for (int y = G + 3; y <= b.topY; y++) if (w.get(x, y, z) == absentialis.world.Block.IRON_FENCE) { balconies++; break; }
                }
                for (absentialis.world.MapData.Room rm : b.rooms) {
                    rooms++;
                    if (!rm.reachable) unreachable++;
                    if (rm.circulation) continue;
                    for (int x = rm.x0; x <= rm.x1; x++) for (int z = rm.z0; z <= rm.z1; z++) {
                        if (x != rm.x0 && x != rm.x1 && z != rm.z0 && z != rm.z1) continue;
                        int fl = w.get(x, rm.y, z);
                        if (fl == 0 || !absentialis.world.Block.ALL[fl].solid()) continue;
                        wallCells++;
                        if (w.get(x, rm.y + 1, z) != 0) pieces++;
                    }
                }
            }
            halves = doubles;
            float density = wallCells == 0 ? 0 : pieces / (float) wallCells;
            t.check("seed " + seed + ": no stair tread has a slab or anything else in the head room over it, and every flight ends in a tread level with the floor it reaches",
                    treads > 50 && blocked == 0 && flights > 10, treads + " treads, " + blocked + " obstructed, " + flights + " flights topped at slab level");
            t.check("seed " + seed + ": every room of every building can still be walked to from its entrance", unreachable == 0 && rooms > 200, rooms + " rooms, " + unreachable + " unreachable");
            t.check("seed " + seed + ": double entrances are double doors, flats have balconies, and windows are two panes tall where people live and work",
                    halves >= 4 && halves % 2 == 0 && balconies >= 6 && tall > windows / 4, halves / 2 + " double doors, " + balconies + " balcony rail cells, " + tall + " of " + windows + " window cells with a light above");
            t.check("seed " + seed + ": rooms are furnished along more than a third of their walls", density > 0.34f, String.format("%.0f%% of room wall cells hold a piece (%d of %d)", density * 100, pieces, wallCells));
        }

        // ---- textures: 64 x 64, nothing missing, windows are a frame round a pane
        absentialis.engine.render.Atlas at = new absentialis.engine.render.Atlas();
        at.paintAll();
        int[] glass = at.pixels.get(at.layer("glass"));
        int n = absentialis.engine.render.Atlas.N, opaque = 0, clear = 0, frameRing = 0;
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            int a = glass[y * n + x] >>> 24;
            if (a == 255) opaque++; else if (a < 140) clear++;
            if ((x < 4 || y < 4 || x >= n - 4 || y >= n - 4) && a == 255) frameRing++;
        }
        boolean sized = true;
        for (int[] px : at.pixels) sized &= px.length == 64 * 64;
        t.check("block textures are 64 x 64 (0.5: 32 x 32), none missing, and the new wall finishes exist", n == 64 && sized && at.missing.isEmpty() && at.layerOf.containsKey("dado_green") && at.layerOf.containsKey("paint_blue"),
                at.pixels.size() + " tiles of " + n + " px, missing " + at.missing);
        t.check("a window is a frame round a pane: an opaque casement on every edge, clear glass inside it", frameRing == n * n - (n - 8) * (n - 8) && clear > n * n / 2 && opaque < n * n / 2,
                opaque + " opaque, " + clear + " clear, " + frameRing + " frame pixels on the border");

        // ---- fog
        absentialis.gameplay.CombatConfig cc = absentialis.gameplay.CombatConfig.get();
        t.check("the fog bank closes inside the draw distance, so nothing is ever drawn at its edge", cc.fogStart > 10 && cc.fogStart < cc.fogEnd && cc.fogEnd <= 236 && cc.fogNight <= 1 && cc.fogRain <= 1 && cc.fogSky > 0.2f,
                String.format("dissolves from %.0f m, gone by %.0f m (drawn to 260 m); %.0f%% of it overhead", cc.fogStart, cc.fogEnd, cc.fogSky * 100));

        // ---- the flashlight is an object
        absentialis.gameplay.items.ItemDef torch = absentialis.gameplay.items.Content.def("tool.flashlight");
        int guns = 0, fit = 0;
        absentialis.gameplay.items.ItemDef mount = null;
        for (absentialis.gameplay.items.ItemDef d : absentialis.gameplay.weapons.Attachments.all()) if (d.id.equals("att.light.torch")) mount = d;
        for (absentialis.gameplay.items.ItemDef d : absentialis.gameplay.items.Content.ITEMS.values()) if (d.weapon != null && !d.weapon.melee()) {
            guns++;
            if (mount != null && absentialis.gameplay.weapons.Attachments.fits(d, mount) && absentialis.gameplay.weapons.Attachments.unlockLevel(d, mount) == 1) fit++;
        }
        absentialis.persistence.Profile lp = new absentialis.persistence.Profile();
        lp.name = "Light"; lp.starterKit(); lp.applyCapacity();
        // 0.7: the Rank 1 kit is the spec's four things - a loaded Handgun, 30 rounds, a knife, a First Aid Spray - and a torch is not one of them
        absentialis.scene.Raid lr = SelfTests.stage(lp);
        boolean starter = lp.stash.items.stream().anyMatch(i -> i.defId.equals("tool.flashlight"));
        int none = lr.lightSource(null);
        absentialis.gameplay.items.ItemInstance carried = new absentialis.gameplay.items.ItemInstance("tool.flashlight");
        absentialis.gameplay.inventory.InventoryService.add(carried, lp.loadout.caseGrid);
        int hand = lr.lightSource(null);
        absentialis.gameplay.items.ItemInstance gun = new absentialis.gameplay.items.ItemInstance("weapon.pm9");
        String why = absentialis.gameplay.weapons.Gunsmith.install(gun, "att.light.torch");
        int onGun = lr.lightSource(gun);
        t.check("the flashlight is a thing: none carried, no light; carried in the case, a hand torch; fitted to the gun, a weapon light - and it fits every firearm (0.7: it is bought or found; the starter kit no longer has one)",
                torch != null && !starter && none == 0 && hand == 1 && why == null && onGun == 2 && gun.hasLight() && fit == guns && guns > 10,
                "starter kit has one " + starter + "; sources " + none + "/" + hand + "/" + onGun + "; mounts on " + fit + " of " + guns + " firearms" + (why == null ? "" : " (" + why + ")"));

        // ---- herbs
        int[] hg = absentialis.ui.ItemModels.herbColors("herb.green"), hr = absentialis.ui.ItemModels.herbColors("herb.red"), hy = absentialis.ui.ItemModels.herbColors("herb.yellow"),
                gr = absentialis.ui.ItemModels.herbColors("herb.mixed_gr"), gry = absentialis.ui.ItemModels.herbColors("herb.mixed_gry");
        absentialis.engine.voxel.VoxModel ym = absentialis.ui.ItemModels.of(absentialis.gameplay.items.Content.def("herb.yellow")), m3 = absentialis.ui.ItemModels.of(absentialis.gameplay.items.Content.def("herb.mixed_gry")),
                m2 = absentialis.ui.ItemModels.of(absentialis.gameplay.items.Content.def("herb.mixed_gr"));
        int yel = colours(ym, 226, 190, 48, 40), yelGreen = colours(ym, 60, 140, 60, 30);
        int g3 = colours(m3, 60, 140, 60, 30), r3 = colours(m3, 176, 40, 50, 30), y3 = colours(m3, 226, 190, 48, 40), g2 = colours(m2, 60, 140, 60, 30), r2 = colours(m2, 176, 40, 50, 30), y2 = colours(m2, 226, 190, 48, 40);
        String mix3 = absentialis.gameplay.inventory.InventoryService.herbMix("herb.mixed_gr", "herb.yellow");
        t.check("herbs are the colours they are called: the yellow herb is yellow, green + red shows both, green + red + yellow shows all three",
                hg.length == 1 && hr.length == 1 && hy.length == 1 && gr.length == 2 && gry.length == 3 && yel > 20 && yelGreen == 0 && g2 > 8 && r2 > 8 && y2 == 0 && g3 > 8 && r3 > 8 && y3 > 8 && "herb.mixed_gry".equals(mix3),
                "yellow herb: " + yel + " yellow voxels, " + yelGreen + " green; G+R: " + g2 + " green " + r2 + " red; G+R+Y: " + g3 + " green " + r3 + " red " + y3 + " yellow; mixing G+R with Y gives " + mix3);

        // ---- ammunition: six kinds, six models
        String[] ammo = {"ammo.smg", "ammo.handgun", "ammo.shells", "ammo.rifle", "ammo.bolt", "ammo.magnum"};
        java.util.Set<String> shapes = new java.util.HashSet<>();
        absentialis.engine.voxel.VoxModel[] am = new absentialis.engine.voxel.VoxModel[6];
        for (int i = 0; i < 6; i++) { am[i] = absentialis.ui.ItemModels.of(absentialis.gameplay.items.Content.def(ammo[i])); shapes.add(am[i].sx + "x" + am[i].sy + "x" + am[i].sz + ":" + java.util.Arrays.hashCode(am[i].col)); }
        boolean smg = colours(am[0], 44, 112, 58, 12) > 80 && colours(am[0], 232, 196, 44, 12) > 10 && am[0].sx <= 9;
        boolean hgun = colours(am[1], 176, 36, 34, 12) > 40 && colours(am[1], 24, 24, 26, 8) > 40 && colours(am[1], 226, 224, 214, 30) > 6;
        boolean shell = colours(am[2], 30, 62, 42, 12) > 80 && colours(am[2], 190, 38, 34, 12) > 40;
        boolean rifle = am[3].sx >= 2 * am[3].sz + 4 && colours(am[3], 226, 224, 214, 14) > 60 && colours(am[3], 52, 120, 70, 12) > 40;
        boolean bolt = colours(am[4], 150, 104, 58, 20) > 30 && colours(am[4], 168, 172, 178, 40) > 8 && colours(am[4], 190, 44, 40, 12) > 6;
        boolean mag = colours(am[5], 22, 22, 24, 8) > 100 && colours(am[5], 190, 194, 200, 12) > 40;
        t.check("each kind of ammunition has its own model: green and yellow SMG carton, red-black-white handgun box, dark green shell carton with red shells, long white-and-green rifle box, wooden bolts, black-and-silver magnum box",
                shapes.size() == 6 && smg && hgun && shell && rifle && bolt && mag, "distinct " + shapes.size() + "; smg " + smg + ", handgun " + hgun + ", shells " + shell + ", rifle " + rifle + ", bolts " + bolt + ", magnum " + mag);

        // ---- vehicles: the door is a door, and the element rides along
        absentialis.vehicle.CarHandlingData.load();
        absentialis.scene.Raid vr = SelfTests.stage(new absentialis.persistence.Profile());
        vr.populate = false; vr.freezeAI = true; vr.god = true;
        vr.actors.removeIf(a -> !a.isPlayer && !a.mate());
        vr.player.x = 321.4f; vr.player.z = 320; vr.player.y = G;
        for (int i = vr.mates.size(); i < 2; i++) { absentialis.entity.Actor m = vr.debugSpawn("military", 318, 322 + i, G); m.mateId = 9001 + i; vr.mates.add(m); }
        int nm = 0;
        for (absentialis.entity.Actor m : vr.mates) { m.x = 317.5f - (nm % 2); m.z = 321 + nm; m.y = G; nm++; }
        absentialis.scene.VehicleSystem vs = vr.vehicles;
        absentialis.scene.VehicleSystem.Car car = vs.spawn(absentialis.vehicle.CarHandlingData.all().get(0).id, 323.2f, G, 320, 0, 0);
        for (int i = 0; i < 30; i++) vr.update(1 / 60f);
        boolean got = car != null && car.door != null && car.door.mobile && vs.enter();
        float swung = 0, enterTook = -1;
        for (int i = 0; i < 150 && got; i++) { vr.update(1 / 60f); swung = Math.max(swung, car.visual.doorOpen); if (enterTook < 0 && vs.driving == car) enterTook = i / 60f; }
        int riders = car == null ? 0 : car.riders.size(), want = Math.min(absentialis.scene.VehicleSystem.MAX_RIDERS, vr.mates.size());
        boolean allSeated = riders > 0;
        if (car != null) for (absentialis.entity.Actor m : car.riders) allSeated &= m.seated && m.distXZ(vr.player.x, vr.player.z) < 3f;
        boolean shut = car != null && car.visual.doorOpen < 0.15f;
        String refused = got ? vs.exit() : "never got in";
        for (int i = 0; i < 120; i++) vr.update(1 / 60f);
        boolean out = vs.driving == null && car != null && car.riders.isEmpty();
        float[] o3 = new float[3];
        if (car != null) car.sim.origin(o3);
        for (absentialis.entity.Actor m : vr.mates) out &= !m.seated && m.distXZ(o3[0], o3[2]) > car.sim.data.width * 0.5f;
        t.check("getting into a car opens its door through the door engine and shuts it behind you; the squad gets in too, rides in its seats, and gets out when you do",
                got && swung > 0.35f && shut && enterTook > 0 && riders == want && allSeated && refused == null && out,
                String.format("door swung to %.0f%%, in after %.2f s, %d of %d squadmates aboard, everyone out and on their feet: %s%s", swung * 100, enterTook, riders, want, out, refused == null ? "" : " (" + refused + ")"));
        // the door can be shot off; a car without one is got into without the door
        int shots = 0;
        float[] hp = new float[3];
        while (car != null && !car.door.destroyed && shots < 40) {
            car.sim.point(car.visual.hingeX() - 0.05f, car.visual.hingeY() + car.visual.doorHeight() * 0.5f, car.visual.hingeZ() - car.visual.doorLength() * 0.1f, hp);
            vs.bullet(vs.cars.indexOf(car), -1, hp[0], hp[1], hp[2], 34, 3f);
            shots++;
        }
        for (int i = 0; i < 20; i++) vr.update(1 / 60f);
        int leaves = 0;
        for (absentialis.scene.Doors.Slab sl : vr.doors.slabs) if (sl.kind == absentialis.scene.Doors.CAR) leaves++;
        vr.player.x = 321.4f; vr.player.z = 320; vr.player.y = G;
        boolean again = car != null && vs.enter();
        float noDoor = -1, swung2 = 0;
        for (int i = 0; i < 150 && again; i++) { vr.update(1 / 60f); swung2 = Math.max(swung2, car.visual.doorOpen); if (noDoor < 0 && vs.driving == car) noDoor = i / 60f; }
        t.check("a car door can be shot off its hinges - it comes away as a loose leaf - and the car is then got into without a door to open",
                car != null && car.door.destroyed && car.visual.doorGone && vs.doorsLost == 1 && leaves == 1 && shots > 1 && again && noDoor > 0 && noDoor < enterTook && swung2 == 0,
                String.format("%d rounds into the hinge side, %d loose door, in again after %.2f s (with the door: %.2f s), door swing %.0f%%", shots, leaves, noDoor, enterTook, swung2 * 100));

        // ---- barter and passing kit
        absentialis.persistence.Profile bp = new absentialis.persistence.Profile();
        bp.name = "Trader"; bp.starterKit(); bp.applyCapacity();
        bp.squad.enlist(2, 9);
        absentialis.scene.Raid br = SelfTests.stage(bp);
        br.populate = false; br.freezeAI = true; br.god = true;
        br.actors.removeIf(a -> !a.isPlayer && !a.mate());
        br.player.x = 340; br.player.z = 340; br.player.y = G; br.player.yaw = 0;
        for (absentialis.entity.Actor m : br.mates) { m.x = 330; m.z = 350; m.y = G; if (!br.actors.contains(m)) br.actors.add(m); }   // SelfTests.stage clears the actor list but not the element
        absentialis.entity.Actor loner = br.debugSpawn("survivors", 340, 338.2f, G), bandit = br.debugSpawn("raiders", 352, 352, G);
        loner.hostileToPlayer = false; bandit.hostileToPlayer = true;
        if (loner.carried.isEmpty()) loner.carried.add(new absentialis.gameplay.items.ItemInstance("med.bandage"));     // 0.7: what a stranger happens to carry is the dice's business; this gate needs them to carry something
        br.update(0.02f);
        loner.x = 340; loner.z = 338.2f; loner.y = G;
        absentialis.entity.Actor found = br.tradePartner(2.6f);
        br.player.x = 352; br.player.z = 353.6f;
        absentialis.entity.Actor noDeal = br.tradePartner(2.6f);
        br.player.x = 340; br.player.z = 340;
        absentialis.gameplay.inventory.Container bcase = bp.loadout.caseGrid;
        for (String id : new String[]{"med.bandage", "food.beans", "ammo.rifle", "val.lighter", "treasure.sapphire", "treasure.ruby"}) { absentialis.gameplay.items.ItemInstance it = new absentialis.gameplay.items.ItemInstance(id); if (id.startsWith("ammo")) it.qty = 20; absentialis.gameplay.inventory.InventoryService.add(it, bcase); }
        absentialis.scene.BarterPanel bt = new absentialis.scene.BarterPanel();
        boolean opened = found == loner;
        boolean stingy = false, fair = false, struck = false, moved = false, kept = false;
        int theirsBefore = 0, mineBefore = bcase.items.size();
        if (opened) {
            bt.open(br, bp.loadout, loner);
            theirsBefore = bt.theirGoods().size();
            if (theirsBefore > 0) {
                absentialis.gameplay.items.ItemInstance wanted = bt.theirGoods().get(0);
                for (absentialis.gameplay.items.ItemInstance it : bt.theirGoods()) if (it.def().value * it.qty > wanted.def().value * wanted.qty) wanted = it;
                bt.toggleTake(wanted);
                stingy = !bt.acceptable() && !bt.deal();                       // something for nothing is refused, and nothing moves
                kept = bt.theirGoods().contains(wanted) && bcase.items.size() == mineBefore;
                for (absentialis.gameplay.items.ItemInstance it : new java.util.ArrayList<>(bt.yourGoods())) { if (bt.offering() >= bt.asking()) break; bt.toggleGive(it); }
                fair = bt.acceptable();
                struck = fair && bt.deal();
                moved = struck && bcase.items.contains(wanted) && !bt.theirGoods().contains(wanted) && bt.deals == 1;
            }
        }
        // a squadmate: kit simply changes hands
        boolean passed = false;
        String passNote = "";
        if (!br.mates.isEmpty()) {
            absentialis.entity.Actor mate = br.mates.get(0);
            mate.x = 340; mate.z = 338.4f; mate.y = G; loner.x = 300; loner.z = 300;
            br.update(0.02f);
            mate.x = 340; mate.z = 338.4f; mate.y = G; br.player.x = 340; br.player.z = 340; br.player.yaw = 0;
            absentialis.entity.Actor fm = br.tradePartner(2.6f);
            passNote = "partner " + (fm != null && fm.mate()) + ", case holds " + bcase.items.size();
            if (fm != null && fm.mate()) mate = fm;
            if (fm == mate && !bcase.items.isEmpty()) {
                bt.open(br, bp.loadout, mate);
                absentialis.gameplay.items.ItemInstance gift = bt.yourGoods().get(0);
                bt.toggleGive(gift);
                passed = bt.squad && bt.acceptable() && bt.deal() && bt.theirGoods().contains(gift) && !bcase.items.contains(gift);
            }
        }
        t.check("barter: someone who is not hostile will trade goods for goods - nothing for nothing, a fair offer is taken, and what changes hands really does; a hostile will not trade at all",
                opened && noDeal == null && theirsBefore > 0 && stingy && kept && fair && struck && moved,
                "partner found " + opened + ", hostile refused " + (noDeal == null) + ", they had " + theirsBefore + " things, something-for-nothing refused " + stingy + ", fair offer accepted " + struck + ", goods moved " + moved);
        t.check("kit passes freely between you and a squadmate", passed, "passed " + passed + " (squad of " + br.mates.size() + "; " + passNote + ")");

        // ---- the infected eat the dead
        absentialis.scene.Raid fr = SelfTests.stage(new absentialis.persistence.Profile());
        fr.populate = false; fr.god = true;
        fr.actors.removeIf(a -> !a.isPlayer);
        fr.player.x = 300; fr.player.z = 300; fr.player.y = G;
        absentialis.entity.Actor meal = fr.debugSpawn("raiders", 300, 345, G);
        fr.update(0.05f);
        fr.damageActor(meal, null, 5000, null, "torso", meal.x, meal.y + 1.1f, meal.z);
        for (int i = 0; i < 120; i++) fr.update(1 / 30f);
        absentialis.entity.Actor eater = fr.debugSpawn("walker", meal.x + 1.0f, meal.z + 0.6f, G);
        float save = cc.feedSeconds;
        cc.feedSeconds = 6;
        boolean fed = false, thinned = false;
        float integrityMid = 1;
        int had = meal.corpse == null ? 0 : meal.corpse.items.size();
        for (int i = 0; i < 30 * 40 && fr.actors.contains(meal); i++) {
            fr.update(1 / 30f);
            if ("feed".equals(eater.state)) fed = true;
            if (meal.eaten > 0.4f && meal.eaten < 0.7f && meal.body != null) { integrityMid = meal.body.integrity(); thinned = integrityMid < 0.75f; }
        }
        cc.feedSeconds = save;
        t.check("the infected eat human corpses: one goes to a body, feeds, the body visibly wastes away and is finally gone", meal.dead && fed && thinned && !fr.actors.contains(meal) && fr.gore.corpsesConsumed == 1 && fr.gore.eatenVoxels > 500,
                "fed " + fed + String.format(", %.0f%% of the body left half way", integrityMid * 100) + ", voxels eaten " + fr.gore.eatenVoxels + ", corpse gone " + !fr.actors.contains(meal) + ", carried " + had + " items (left on the ground)");
    }

    // ------------------------------------------------------------------ districts, extraction points and radiation
    private static void zones(SelfTests04.Check t) {
        System.out.println("[v0.6 districts: extraction points are never Wilderness, radiation follows the district]");
        for (long seed : SEEDS) {
            World w = world(seed);
            StringBuilder sb = new StringBuilder();
            int wild = 0, hot = 0;
            for (MapData.Exit e : w.map.exits) {
                boolean cityExit = e.district != District.WILDERNESS;
                int r = (int) Math.ceil(e.radius);
                for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > e.radius * e.radius) continue;
                    float x = e.x + 0.5f + dx, z = e.z + 0.5f + dz;
                    if (cityExit && w.district(x, z) == District.WILDERNESS) wild++;
                    if (w.radiation(x, z) > 0) hot++;
                }
                sb.append(e.id).append('=').append(w.district(e.x, e.z).display).append(' ');
            }
            t.check("seed " + seed + ": no column of a city extraction zone reads as Wilderness, and no extraction zone is irradiated", wild == 0 && hot == 0, wild + " wilderness columns, " + hot + " irradiated; " + sb.toString().trim());
        }
    }
}
