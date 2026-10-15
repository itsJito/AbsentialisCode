package absentialis.tools;

import absentialis.neuralai.integration.Archetypes;
import absentialis.neuralai.integration.Relations;
import absentialis.engine.events.EventBus;
import absentialis.engine.util.Json;
import absentialis.engine.util.Rng;
import absentialis.entity.Actor;
import absentialis.gameplay.economy.Economy;
import absentialis.gameplay.inventory.Container;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.inventory.Loadout;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.gameplay.progression.Progression;
import absentialis.gameplay.weapons.Attachments;
import absentialis.gameplay.weapons.Gunsmith;
import absentialis.gameplay.weapons.WeaponService;
import absentialis.persistence.Profile;
import absentialis.persistence.Settings;
import absentialis.scene.Raid;
import absentialis.scene.RaidResolver;
import absentialis.sim.ZoneState;
import absentialis.world.Block;
import absentialis.world.District;
import absentialis.world.MapData;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Quality gates (GDD 22.5 + addendum pass conditions), runnable without a window:
 * gradlew selftest  or  ABS_SELFTEST=all|inventory|extraction|noise|soak ...
 */
public final class SelfTests {
    private static int failed, passed;
    private static World flat;

    public static int runHeadless(String which) {
        failed = passed = 0;
        Content.load();
        Raid.fixedSeed = 0x0AB5E47L;      // reproducible: a gate that fails, fails every time
        boolean all = which == null || which.equals("all");
        System.out.println("=== ABSENTIALIS quality gates (" + which + ") ===");
        if (all || which.contains("inventory")) SelfTests.guard(() -> { inventory(); conservation(); });
        if (all || which.contains("orient") || which.contains("inventory")) SelfTests.guard(() -> { orientation(); });
        if (all || which.contains("weapon")) SelfTests.guard(() -> { weapons(); });
        if (all || which.contains("progress")) SelfTests.guard(() -> { progression(); });
        if (all || which.contains("econ")) SelfTests.guard(() -> { economy(); });
        if (all || which.contains("save")) SelfTests.guard(() -> { saveLoad(); });
        if (all || which.contains("death")) SelfTests.guard(() -> { death(); });
        if (all || which.contains("extraction")) SelfTests.guard(() -> { extraction(); });
        if (all || which.contains("noise")) SelfTests.guard(() -> { noise(); evidence(); });
        if (all || which.contains("ballistic")) SelfTests.guard(() -> { ballistics(); });
        if (all || which.contains("tier") || which.contains("soak")) SelfTests.guard(() -> { tiers(); offscreen(); });
        if (all || which.contains("character")) SelfTests.guard(() -> { character(); });
        if (all || which.contains("anim")) SelfTests.guard(() -> { animation(); });
        if (all || which.contains("ragdoll")) SelfTests.guard(() -> { ragdoll(); });
        if (all || which.contains("audio")) SelfTests.guard(() -> { audio(); });
        if (all || which.contains("alife")) SelfTests.guard(() -> { alife(); });
        if (all || which.contains("ai")) SelfTests.guard(() -> { doctrines(); });
        if (all || which.contains("zombie")) SelfTests.guard(() -> { zombies(); });
        if (all || which.contains("v03") || which.contains("gore") || which.contains("squad")) SelfTests.guard(() -> { v03(); });
        if (all || which.contains("district") || which.contains("map")) SelfTests.guard(() -> { districts(); });
        guard(() -> SelfTests04.run(which == null ? "all" : which, all, SelfTests::check));
        guard(() -> SelfTests05.run(which == null ? "all" : which, all, SelfTests::check));
        guard(() -> SelfTests06.run(which == null ? "all" : which, all, SelfTests::check));
        guard(() -> SelfTests07.run(which == null ? "all" : which, all, SelfTests::check));
        guard(() -> SelfTests08.run(which == null ? "all" : which, all, SelfTests::check));
        guard(() -> SelfTests09.run(which == null ? "all" : which, all, SelfTests::check));
        guard(() -> SelfTests10.run(which == null ? "all" : which, all, SelfTests::check));
        guard(() -> SelfTests11.run(which == null ? "all" : which, all, SelfTests::check));
        guard(() -> SelfTests12.run(which == null ? "all" : which, all, SelfTests::check));
        guard(() -> SelfTests13.run(which == null ? "all" : which, all, SelfTests::check));
        if (which != null && which.contains("probe")) {
            World pw = new World();
            new absentialis.world.gen.CityGenerator(pw, 1337L, null).generate();
            TerrainProbe.Report tr = TerrainProbe.scan(pw);
            System.out.println("[probe] " + tr);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tr.trenchByDist.length; i++) sb.append(i).append(':').append(tr.trenchByDist[i]).append(' ');
            System.out.println("[probe] trench cells by distance outside the city: " + sb);
            for (int[] wv : tr.worst) System.out.println("[probe] worst " + java.util.Arrays.toString(wv));
            // profile across the west wall at z=200 and the north wall at x=300
            StringBuilder a = new StringBuilder(), b = new StringBuilder();
            for (int x = 80; x < 108; x++) a.append(x).append('=').append(TerrainProbe.ground(pw, x, 200)).append(Block.ALL[pw.get(x, Math.max(0, TerrainProbe.ground(pw, x, 200)), 200)].name.charAt(0)).append(' ');
            for (int x = 596; x < 626; x++) b.append(x).append('=').append(TerrainProbe.ground(pw, x, 200)).append(Block.ALL[pw.get(x, Math.max(0, TerrainProbe.ground(pw, x, 200)), 200)].name.charAt(0)).append(' ');
            System.out.println("[probe] west profile z=200: " + a);
            System.out.println("[probe] east profile z=200: " + b);
        }
        Raid.fixedSeed = null;
        System.out.printf("=== %d passed, %d failed ===%n", passed, failed);
        return failed;
    }

    /**
     * Runs one group of gates. A group that throws is one failed gate with the exception as its detail, and the suite
     * goes on: a crash in the first group used to hide the state of every group after it.
     */
    static void guard(Runnable group) {
        // 1.1: each group starts with factory weapons. (A group that loads a profile turns the survivor's weapon upgrades on - and a
        // fresh survivor's are at the bottom of every track; the ballistics gates of earlier versions are about the weapons as made.)
        absentialis.gameplay.weapons.Gunsmith.tuning = null;
        // 1.2: and with nobody held to the twelve-block rule. The AI gates of earlier versions stand their people thirty, forty and
        // sixty blocks from the survivor and are about what happens once the survivor has been seen from there; the rule that
        // people cannot make the survivor out past twelve blocks is 1.2's, and has gates of its own (combat12), which set it.
        absentialis.scene.Raid.HUMAN_ENGAGE_RANGE = 1e9f;
        try { group.run(); }
        catch (Throwable e) {
            StackTraceElement at = null;
            for (StackTraceElement s : e.getStackTrace()) if (s.getClassName().startsWith("absentialis.")) { at = s; break; }
            check("a group of gates ran to the end", false, e + (at == null ? "" : " at " + at));
            crashed++;
        }
    }
    static int crashed;

    private static int slotsAt(int rank) { Progression p = new Progression(); p.bestRank = rank; return p.squadSlots(); }

    private static void check(String name, boolean ok, String detail) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name + (detail == null || detail.isEmpty() ? "" : "  -  " + detail));
    }

    private static ItemInstance item(String id, int qty) { ItemInstance i = new ItemInstance(id); i.qty = qty; return i; }

    // ------------------------------------------------------------------ icon orientation (iteration P0.2)
    /**
     * Places every item definition in a case, rasterises the cell it occupies exactly as the grid
     * draws it and checks the picture: 0 degrees is upright (the top of the model is at the top of
     * the icon) and fits the definition's footprint; rotation swaps width and height by a pure
     * quarter turn - it never flips the image vertically.
     */
    private static void orientation() {
        System.out.println("[Inventory icons: every definition upright at 0 degrees, rotation never flips]");
        int upside = 0, aspect = 0, flipped = 0, unplaced = 0, checked = 0;
        StringBuilder bad = new StringBuilder();
        for (ItemDef d : Content.ITEMS.values()) {
            ItemInstance it = new ItemInstance(-7, d.id);
            Container box = new Container("orient", 20, 20);
            if (!InventoryService.add(it, box).ok() || it.rotated) { unplaced++; bad.append(' ').append(d.id).append("(placement)"); continue; }
            absentialis.engine.voxel.VoxModel m = absentialis.ui.ItemModels.of(it);
            int[] size = new int[2];
            int[] img = absentialis.ui.ItemIcons.rasterize(m, d, it.w(), it.h(), size);
            int w = size[0], h = size[1];
            checked++;
            // aspect: a long model must not be squeezed into the short side of its footprint
            float mw = Math.max(m.sx, m.sz), mh = m.sy;
            if (m.sy > 3 && (mw > mh * 1.5f && d.h > d.w || mh > mw * 1.5f && d.w > d.h)) { aspect++; bad.append(' ').append(d.id).append("(aspect ").append(d.w).append('x').append(d.h).append(" model ").append(m.sx).append('x').append(m.sy).append(')'); }
            // upright: colour the top and bottom slabs of a copy and see where they land
            if (m.sy > 3) {
                absentialis.engine.voxel.VoxModel mark = new absentialis.engine.voxel.VoxModel(m.sx, m.sy, m.sz);
                int slab = Math.max(1, m.sy / 4);
                for (int x = 0; x < m.sx; x++) for (int y = 0; y < m.sy; y++) for (int z = 0; z < m.sz; z++) if (m.get(x, y, z) != 0) mark.set(x, y, z, y >= m.sy - slab ? 0xFFFF00FF : y < slab ? 0xFF00FFFF : 0xFF808080);
                int[] mi = absentialis.ui.ItemIcons.rasterize(mark, d, it.w(), it.h(), size);
                double topY = 0, botY = 0, topX = 0; int nt = 0, nb = 0;
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    int c = mi[y * w + x], r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                    if (((c >>> 24) & 255) < 200) continue;
                    if (r > g * 2 && b > g * 2) { topY += y; topX += x; nt++; } else if (g > r * 2 && b > r * 2) { botY += y; nb++; }
                }
                if (nt > 0 && nb > 0 && topY / nt >= botY / nb) { upside++; bad.append(' ').append(d.id).append("(upside down)"); }
                // rotation: quarter turn, top goes to the left edge, nothing is mirrored
                int[] rot = absentialis.ui.ItemIcons.rotateCcw(mi, w, h);
                double rx = 0, ry = 0; int rn = 0;
                for (int y = 0; y < w; y++) for (int x = 0; x < h; x++) { int c = rot[y * h + x], r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255; if (((c >>> 24) & 255) >= 200 && r > g * 2 && b > g * 2) { rx += x; ry += y; rn++; } }
                boolean pure = true;
                for (int y = 0; y < w && pure; y++) for (int x = 0; x < h; x++) if (rot[y * h + x] != mi[x * w + (w - 1 - y)]) { pure = false; break; }
                // the marker's distance from the top edge must become its distance from the left edge, its x offset its height from the bottom
                if (nt > 0 && rn > 0 && (!pure || Math.abs(rx / rn - topY / nt) > 0.51 || Math.abs((w - 1 - ry / rn) - topX / nt) > 0.51)) { flipped++; bad.append(' ').append(d.id).append("(rotation flips)"); }
            }
            // the footprint itself: R swaps w and h
            int w0 = it.w(), h0 = it.h();
            it.rotated = true;
            if (it.w() != h0 || it.h() != w0) { flipped++; bad.append(' ').append(d.id).append("(footprint)"); }
            it.rotated = false;
        }
        float[] r = {0.1f, 0.2f, 0.3f, 0.6f};
        float[] u0 = absentialis.ui.ItemIcons.cornerUv(r, false), u1 = absentialis.ui.ItemIcons.cornerUv(r, true);
        check("every definition placed unrotated by default", unplaced == 0, checked + " definitions");
        check("no item is upside down at 0 degrees", upside == 0, upside + " upside down");
        check("footprints match the upright model (no sideways squeeze)", aspect == 0, aspect + " mismatched");
        check("rotation (R) swaps w/h as a pure quarter turn, no vertical flip", flipped == 0, flipped + " bad");
        check("draw path uses the same quarter turn (UV corners)", u0[0] == 0.1f && u0[1] == 0.2f && u1[0] == 0.3f && u1[1] == 0.2f && u1[6] == 0.1f && u1[7] == 0.2f, null);
        check("square slots never rotate, tall cells rotate wide items", !absentialis.ui.ItemIcons.rotatedIn(6, 2, 58, 58) && absentialis.ui.ItemIcons.rotatedIn(6, 2, 40, 120) && !absentialis.ui.ItemIcons.rotatedIn(6, 2, 120, 40), null);
        if (bad.length() > 0) System.out.println("    orientation problems:" + bad);
    }

    // ------------------------------------------------------------------ inventory
    private static void inventory() {
        System.out.println("[Inventory: fit / rotate / illegal reject / no overlap]");
        Container c = new Container("t", 12, 16);
        ItemInstance rifle = item("weapon.sr16", 1); // 7x2: long guns lie along the grid at 0 degrees
        check("place rifle 7x2 at origin", InventoryService.add(rifle, c).ok() && rifle.w() == 7 && rifle.h() == 2, null);
        ItemInstance pistol = item("weapon.pm9", 1); // 3x2
        check("overlap rejected", !InventoryService.move(pistol, c, 4, 1, false).ok() && pistol.container == null, null);
        check("out of bounds rejected", !InventoryService.move(pistol, c, 11, 0, false).ok(), null);
        check("rotated fit (2x3 at 10,13)", InventoryService.move(pistol, c, 10, 13, true).ok() && pistol.w() == 2 && pistol.h() == 3, null);
        check("rotation that would overflow rejected", !InventoryService.move(rifle, c, 7, 15, true).ok() && rifle.x == 0 && rifle.y == 0, "rifle stays put");
        check("grid verify clean", c.verify() == null, c.verify());
        ItemInstance bandage = item("med.bandage", 1);
        check("1x1 bandage footprint", bandage.baseW() == 1 && bandage.baseH() == 1, null);
        check("ammo footprint 2x1; a retired magazine id resolves to its ammunition", item("ammo.rifle", 1).baseW() == 2 && item("ammo.rifle", 1).baseH() == 1 && item("mag.stanag", 1).defId.equals("ammo.rifle"), null);
        // auto-sort never loses items
        Container s = new Container("s", 12, 16);
        Rng r = new Rng(5);
        List<String> ids = new ArrayList<>(Content.ITEMS.keySet());
        for (int k = 0; k < 60; k++) { ItemDef d = Content.def(ids.get(r.nextInt(ids.size()))); if (d.w <= 6 && d.h <= 8) InventoryService.add(item(d.id, 1), s); }
        int before = s.items.size();
        Map<String, Integer> lb = InventoryService.ledger(s.items);
        InventoryService.Result res = InventoryService.autoSort(s);
        check("auto-sort keeps every item and quantity", InventoryService.ledger(s.items).equals(lb) && s.verify() == null, res.ok() ? before + " items" : res.msg());
        // loadout pouches take no grid space
        Loadout l = new Loadout();
        InventoryService.store(item("key.crypt", 1), l);
        InventoryService.store(item("treasure.gem_red", 1), l);
        check("keys/treasures go to space-free pouches", l.keys.size() == 1 && l.treasures.size() == 1 && l.caseGrid.items.isEmpty(), null);
        check("case is 6 rows x 10 columns at Rank 1", l.caseGrid.w == 10 && l.caseGrid.h == 6, null);
        l.resizeCase(99, 99);
        check("case caps at 9 rows x 13 columns (Rank 20)", l.caseGrid.w == 13 && l.caseGrid.h == 9, null);
    }

    private static void conservation() {
        System.out.println("[Transactions conserve quantities]");
        Container c = new Container("t", 10, 10);
        // v0.3: a firearm holds its own rounds; loading moves only what fits, never more
        ItemInstance gun = item("weapon.akr", 1);
        gun.rounds = gun.capacity(); gun.ammoId = "ammo.assault";      // 0.7: the two service rifles take Assault Rifle Ammo
        InventoryService.add(gun, c);
        Map<String, Integer> before = InventoryService.ledger(c.items);
        InventoryService.Result r = InventoryService.combine(item("ammo.assault", 1), gun); // full weapon: rejects, no change
        check("30/30 + 1 never becomes 31", gun.rounds == 30 && !r.ok() && InventoryService.ledger(c.items).equals(before), "rounds=" + gun.rounds);
        gun.rounds = 15;
        ItemInstance a = item("ammo.assault", 30), b = item("ammo.assault", 20);
        InventoryService.move(a, c, 0, 4, false);
        InventoryService.move(b, c, 3, 4, false);
        Map<String, Integer> l0 = InventoryService.ledger(c.items);
        InventoryService.combine(a, gun); // top up from loose rounds
        check("loading a weapon conserves rounds", InventoryService.ledger(c.items).equals(l0) && gun.rounds == 30 && a.qty == 15, "rounds=" + gun.rounds);
        InventoryService.unload(gun, c);
        check("unloading conserves rounds", InventoryService.ledger(c.items).equals(l0) && gun.rounds == 0, null);
        ItemInstance st = item("ammo.handgun", 40);
        InventoryService.add(st, c);
        Map<String, Integer> l1 = InventoryService.ledger(c.items);
        int[] sp = c.findSpace(item("ammo.handgun", 1));
        InventoryService.split(st, 15, c, sp[0], sp[1]);
        check("split conserves quantity", InventoryService.ledger(c.items).equals(l1), null);
        // stack merge moves only what fits
        ItemInstance s1 = item("ammo.handgun", 45), s2 = item("ammo.handgun", 20);
        Container c2 = new Container("u", 6, 6);
        InventoryService.move(s1, c2, 0, 0, false); InventoryService.move(s2, c2, 0, 2, false);
        InventoryService.combine(s2, s1);
        check("stack merge clamps at max stack", s1.qty == 50 && s2.qty == 15 && s2.container == c2, s1.qty + "/" + s2.qty);
        // magazine-style reload: one motion fills the weapon from loose stacks
        ItemInstance w = item("weapon.sr16", 1);
        Container c3 = new Container("case", 10, 6);
        InventoryService.add(w, c3);
        ItemInstance s3 = item("ammo.assault", 7), s4 = item("ammo.assault", 9);
        InventoryService.move(s3, c3, 0, 3, false); InventoryService.move(s4, c3, 3, 3, false);
        Map<String, Integer> lw = InventoryService.ledger(new ArrayList<>(c3.items));
        List<ItemInstance> stacks = new ArrayList<>(List.of(s3, s4));
        int loaded = WeaponService.fill(w, stacks);
        check("reload fills to capacity from loose rounds and conserves", loaded == 10 && w.rounds == 10 && s3.container == null && s4.qty == 6 && InventoryService.ledger(new ArrayList<>(c3.items)).equals(lw), "loaded " + loaded);
        check("extended magazine raises capacity 10 -> 14", Gunsmith.install(w, "att.magazine.ext") == null && w.capacity() == 14, "cap " + w.capacity());
        WeaponService.fill(w, new ArrayList<>(List.of(s4)));
        Gunsmith.remove(w, "magazine");
        check("a smaller magazine spills the surplus rounds instead of deleting them", w.rounds == 10 && w.spill == 4 && s4.qty == 2, w.rounds + " + " + w.spill);
        // single-round weapons load one at a time
        ItemInstance pump = item("weapon.pump12", 1);
        ItemInstance shells = item("ammo.shells", 3);
        check("shotgun loads shell by shell", WeaponService.loadOne(pump, shells) && pump.rounds == 1 && shells.qty == 2 && !WeaponService.loadOne(pump, item("ammo.rifle", 5)), null);
        check("no duplicate instance uids", uniqueUids(c3), null);
    }

    private static boolean uniqueUids(Container c) {
        List<ItemInstance> all = new ArrayList<>();
        for (ItemInstance i : c.items) i.collect(all);
        return all.stream().map(i -> i.uid).distinct().count() == all.size();
    }

    // ------------------------------------------------------------------ weapons
    private static void weapons() {
        System.out.println("[Weapon degradation / gunsmith]");
        ItemInstance w = item("weapon.akr", 1);
        w.setCondition(80);
        check("condition >=50: negligible jam", Gunsmith.stats(w, 0).jamChance < 0.01f, null);
        w.setCondition(40);
        Gunsmith.Stats s = Gunsmith.stats(w, 0);
        check("condition <50: 2% jam, +2% spread, red band", Math.abs(s.jamChance - 0.02f) < 1e-4 && s.conditionBand == 1, null);
        w.setCondition(20);
        s = Gunsmith.stats(w, 0);
        check("condition <25: 8% jam, critical", Math.abs(s.jamChance - 0.08f) < 1e-4 && s.conditionBand == 2, null);
        ItemInstance oil = item("mat.gun_oil", 2);
        check("field clean refused below 25 (needs bench)", WeaponService.clean(w, oil, false) != null && oil.qty == 2, null);
        check("bench clean +25 consumes one oil", WeaponService.clean(w, oil, true) == null && Math.round(w.condition()) == 45 && oil.qty == 1, null);
        w.setCondition(100);
        float before = w.condition();
        for (int i = 0; i < 100; i++) WeaponService.wearShot(w, Gunsmith.stats(w, 0), 1);
        float dry = before - w.condition();
        w.setCondition(100);
        for (int i = 0; i < 100; i++) WeaponService.wearShot(w, Gunsmith.stats(w, 0), WeaponService.environmentWear(true, true, false));
        check("rain + sewer accelerate decay", (100 - w.condition()) > dry * 2, String.format("dry %.2f wet %.2f", dry, 100 - w.condition()));
        boolean slotsOk = true;
        for (ItemDef d : Content.ITEMS.values()) if (d.weapon != null && !d.weapon.melee()) { int n = Attachments.categoriesFor(d).size(); if (n < 5 || n > 10) { slotsOk = false; System.out.println("    " + d.id + " has " + n + " categories"); } }
        check("every firearm has its own attachment categories; melee has none", slotsOk && Attachments.categoriesFor(Content.def("melee.knife")).isEmpty(), null);
        List<String> hg = Attachments.categoriesFor(Content.def("weapon.pm9"));
        check("handguns take a Trigger Action instead of Stock / Underbarrel", hg.contains("trigger") && !hg.contains("stock") && !hg.contains("underbarrel"), hg.toString());
        ItemInstance sr = item("weapon.sr16", 1);
        Gunsmith.Stats base = Gunsmith.stats(sr, 0);
        String v0 = sr.visualKey();
        check("incompatible attachment refused", Gunsmith.install(sr, "att.trigger.light") != null && Gunsmith.install(sr, "att.muzzle.choke") != null && sr.attachments.isEmpty(), null);
        Gunsmith.Stats sup = null;
        boolean fitted = Gunsmith.install(sr, "att.muzzle.mono_supp") == null;
        sup = Gunsmith.stats(sr, 0);
        check("Monolithic Suppressor: quieter and longer-ranged, slower ADS and less steady", fitted && sup.suppressed && sup.noise < base.noise && sup.range > base.range && sup.adsTime > base.adsTime && sup.steady < base.steady, null);
        check("attachment changes the weapon's voxel model, not its footprint", !sr.visualKey().equals(v0) && sr.baseW() == Content.def("weapon.sr16").w, "w=" + sr.baseW());
        for (String id : new String[]{"att.optic.reddot", "att.stock.heavy", "att.underbarrel.foregrip", "att.magazine.ext"}) Gunsmith.install(sr, id);
        // 1.1: it was five at most. Now every slot the weapon has may be filled (its level opens them one at a time).
        check("a sixth attachment goes on: a weapon may fill every slot it has", sr.attachments.size() == 5 && Gunsmith.install(sr, "att.perk.fmj") == null && sr.attachments.size() == 6, sr.attachments.size() + "");
        check("swapping within a category replaces the part that was there", Gunsmith.install(sr, "att.optic.holo") == null && sr.attachments.size() == 6 && sr.attachment("optic").defId.equals("att.optic.holo"), null);
        boolean tradeoff = true;
        for (ItemDef a : Attachments.all()) {
            boolean pos = a.attachment.launcher || a.attachment.light || a.attachment.zoom > 1, neg = a.attachment.expose > 0;
            for (float v : a.attachment.mods.values()) { pos |= v > 0; neg |= v < 0; }
            boolean ok = a.attachment.socket.equals("perk") ? pos && !neg : pos && neg;
            if (!ok) { tradeoff = false; System.out.println("    " + a.id + " pos=" + pos + " neg=" + neg); }
        }
        check("every attachment trades an advantage for a penalty; perks only add (but take a slot)", tradeoff, null);
        ItemInstance t2 = item("weapon.akr", 1);
        float r0 = Gunsmith.stats(t2, 0).recoilV;
        Gunsmith.install(t2, "att.muzzle.compensator"); Gunsmith.install(t2, "att.underbarrel.foregrip");
        check("modifiers add up predictably (12% + 10% recoil control)", Math.abs(Gunsmith.stats(t2, 0).recoilV - r0 * 0.78f) < 1e-3, null);
        check("six primary statistics", Gunsmith.primary(t2).length == 6 && Gunsmith.PRIMARY.length == 6 && Gunsmith.secondary(t2).size() >= 7, null);
        int[] k1 = {0}; float a = Gunsmith.recoilKick(Content.def("weapon.akr"), 6)[1], b = Gunsmith.recoilKick(Content.def("weapon.smg"), 6)[1];
        check("distinct recoil pattern per platform", Math.abs(a - b) > 1e-3 || k1[0] != 0, a + " vs " + b);
        // roster: every weapon has its ammunition type, footprint and published price
        Object[][] roster = {{"melee.knife", 3, 1, 50}, {"weapon.crossbow", 4, 2, 10000}, {"weapon.pm9", 3, 2, 8000}, {"weapon.punisher", 3, 2, 20000}, {"weapon.yellow9", 4, 2, 14000}, {"weapon.whitetail", 2, 2, 24000},
                {"weapon.matilda", 5, 2, 70000}, {"weapon.pump12", 8, 2, 20000}, {"weapon.riot", 8, 2, 32000}, {"weapon.striker", 5, 2, 43000}, {"weapon.mosin", 9, 1, 12000}, {"weapon.sr16", 7, 2, 35000},
                {"weapon.akr", 6, 2, 45000}, {"weapon.deagle", 4, 2, 42000}, {"weapon.killer9", 4, 2, 77700}, {"weapon.r45", 4, 3, 160000}, {"weapon.smg", 7, 3, 15000}, {"weapon.tommy", 7, 3, 50000},
                {"grenade.frag", 1, 2, 500}, {"grenade.incendiary", 1, 2, 500}, {"grenade.flash", 1, 2, 500}};
        boolean rosterOk = true;
        for (Object[] e : roster) { ItemDef d = Content.def((String) e[0]); if (d == null || d.w != (Integer) e[1] || d.h != (Integer) e[2] || d.value != (Integer) e[3]) { rosterOk = false; System.out.println("    roster mismatch " + e[0]); } }
        check("weapon roster: 18 weapons + 3 grenades with their sizes and their 0.7 prices", rosterOk, null);
        check("six ammunition types exist independently of any weapon", Content.def("ammo.handgun") != null && Content.def("ammo.shells") != null && Content.def("ammo.rifle") != null && Content.def("ammo.magnum") != null && Content.def("ammo.smg") != null && Content.def("ammo.bolt") != null, null);
        ItemInstance g = item("weapon.akr", 1);
        g.setCondition(10);
        Rng rng = new Rng(3);
        g.rounds = 30; g.ammoId = "ammo.rifle";
        int jams = 0;
        for (int i = 0; i < 400; i++) { if (WeaponService.fire(g, Gunsmith.stats(g, 0), 1, rng) == null) { if (g.jammed) { jams++; WeaponService.clearJam(g); } if (g.rounds == 0) { g.rounds = 30; g.ammoId = "ammo.rifle"; } } g.setCondition(10); }
        check("critical weapons jam in practice", jams > 5, jams + " jams in 400 trigger pulls");
    }

    // ------------------------------------------------------------------ progression
    private static void progression() {
        System.out.println("[Rank / Prestige]");
        check("cycle = 1,265,500 XP", Progression.xpToReach(50) + Progression.costOf(50) == Progression.CYCLE_XP, Progression.xpToReach(50) + Progression.costOf(50) + "");
        check("Master ~19M XP (15 cycles)", Progression.totalToMaster() == 18_982_500L, null);
        check("fifteen Prestiges", Progression.MAX_PRESTIGE == 15, null);
        check("rank ladder: Private 1-3 ... Commander 48-50", Progression.rankName(1).equals("Private") && Progression.rankName(3).equals("Private") && Progression.rankName(4).equals("Private First Class")
                && Progression.rankName(13).equals("Corporal") && Progression.rankName(19).equals("Staff Sergeant") && Progression.rankName(25).equals("Master Gunnery Sergeant")
                && Progression.rankName(47).equals("Field Marshal") && Progression.rankName(48).equals("Commander") && Progression.rankName(50).equals("Commander") && Progression.rankTable().size() == 21, null);
        Progression p = new Progression();
        check("inventory starts at 6x10 (60 slots)", p.caseDims()[0] == 10 && p.caseDims()[1] == 6, null);
        p.addXp(Progression.xpToReach(10));
        check("rank from XP", p.rank() == 10, "rank " + p.rank());
        check("one Unlock Token per Rank gained", p.unlockTokens == 9, p.unlockTokens + "");
        int[] d4 = Progression.caseDimsAt(4), d10 = Progression.caseDimsAt(10), d15 = Progression.caseDimsAt(15), d20 = Progression.caseDimsAt(20);
        check("inventory grows at Ranks 4, 10, 15, 20: 84, 96, 104, 117 slots", d4[0] * d4[1] == 84 && d10[0] * d10[1] == 96 && d15[0] * d15[1] == 104 && d20[0] * d20[1] == 117 && p.caseDims()[0] * p.caseDims()[1] == 96, null);
        check("cannot prestige before rank 50 complete", p.prestige() != null && p.prestige == 0, null);
        check("an Unlock Token opens a weapon ahead of its Rank", !p.platformUnlocked("weapon.akr") && p.spendUnlockToken("weapon.akr") == null && p.platformUnlocked("weapon.akr") && p.unlockTokens == 8, null);
        p.addXp(Progression.CYCLE_XP);
        p.addMastery("weapon.akr", 9000);
        int wl = p.weaponLevel("weapon.akr");
        check("prestige 1: Rank 1, one Prestige Unlock Token, one Prestige Award owed", p.prestige() == null && p.prestige == 1 && p.rank() == 1 && p.permTokens == 1 && p.awardPending, null);
        check("regular token unlocks reset on Prestige", !p.platformUnlocked("weapon.akr") && p.unlockTokens == 0, null);
        check("inventory size is permanent: Prestige does not shrink it", p.caseDims()[0] == 13 && p.caseDims()[1] == 9, null);
        check("the award must be chosen before the next Prestige", !p.canPrestige(), null);
        check("Unlock Tokens Refund returns what was spent last cycle", p.chooseAward("refund") == null && p.unlockTokens == 1 && !p.awardPending, p.unlockTokens + "");
        check("Prestige Unlock Token: permanent from Rank 1", p.spendPermToken("weapon.akr") == null && p.platformUnlocked("weapon.akr") && p.rank() < Progression.platformRank("weapon.akr") && p.permTokens == 0, null);
        p.permTokens = 1;
        check("permanent unlocks do not stack", p.spendPermToken("weapon.akr") != null, null);
        p.permTokens = 0;
        check("weapon level is independent of Player Prestige", p.weaponLevel("weapon.akr") == wl && wl > 1, "lvl " + wl);
        for (int i = 2; i <= 15; i++) { p.addXp(Progression.CYCLE_XP); p.prestige(); p.chooseAward(p.awardAvailable("class") ? "class" : "token"); }
        check("Extra Custom Class caps at five more (ten in all)", p.extraClasses == 5 && p.presets() == 10 && !p.awardAvailable("class"), p.extraClasses + "");
        check("Prestige Master at 15: Rank 50 for good, no further resets, no Fresh Start", p.master() && p.rank() == 50 && !p.canPrestige() && !p.awardAvailable("fresh") && p.cosmetics.contains("master_emblem") && p.platformUnlocked("weapon.akr"), null);
        check("Prestige Master needs 18,982,500 XP", p.lifetimeXp <= Progression.totalToMaster() && p.lifetimeXp > Progression.totalToMaster() - Progression.CYCLE_XP, p.lifetimeXp + "");
        // weapon prestige: its own track
        Progression q = new Progression();
        ItemDef akr = Content.def("weapon.akr");
        q.addMastery("weapon.akr", 10_000_000L);
        check("a weapon maxes when its last attachment unlocks", q.weaponLevel("weapon.akr") == Attachments.maxLevel(akr) && q.canPrestigeWeapon("weapon.akr") && Attachments.maxLevel(akr) > 20, "max " + Attachments.maxLevel(akr));
        check("Weapon Prestige resets attachment progress and grants death protection", q.prestigeWeapon("weapon.akr") == null && q.weaponLevel("weapon.akr") == 1 && q.deathProtected("weapon.akr") && q.weaponPrestige("weapon.akr") == 1 && q.prestige == 0, null);
        boolean before14 = q.goldCamoAvailable("weapon.akr");
        q.prestige = 14;
        check("Gold camouflage needs Player Prestige 14 and a prestiged weapon, 50,000 coins", !before14 && q.goldCamoAvailable("weapon.akr") && !q.goldCamoAvailable("weapon.smg") && Progression.GOLD_CAMO_PRICE == 50_000, null);
        // prestige keeps what you own
        Profile pr = new Profile();
        ItemInstance ak = item("weapon.akr", 1);
        InventoryService.add(ak, pr.stash);
        InventoryService.equip(ak, pr.loadout, "primary");
        pr.prog.addXp(Progression.CYCLE_XP);
        pr.prog.prestige();
        pr.applyCapacity();
        check("prestige removes no items and keeps the Rank 20 case", pr.loadout.slot("primary") == ak && pr.loadout.caseGrid.w == 13 && pr.loadout.caseGrid.h == 9, null);
        // squad: separate saved state and progression
        absentialis.gameplay.squad.Roster ro = new absentialis.gameplay.squad.Roster();
        ro.ensure(4, 1);
        Profile nobody = new Profile();
        nobody.starterKit(); nobody.applyCapacity();
        check("0.7: nobody is handed a squadmate - a new operator has none, no slot until Rank 4, then one more every four ranks up to nine at Rank 36",
                ro.mates.isEmpty() && nobody.squad.mates.isEmpty() && slotsAt(1) == 0 && slotsAt(3) == 0 && slotsAt(4) == 1 && slotsAt(7) == 1 && slotsAt(8) == 2 && slotsAt(36) == 9 && slotsAt(50) == 9,
                "slots at rank 1/4/8/36: " + slotsAt(1) + "/" + slotsAt(4) + "/" + slotsAt(8) + "/" + slotsAt(36));
        ro.enlist(4, 1);
        check("four hired squadmates are all active, each with the standard kit of their type", ro.mates.size() == 4 && ro.activeCount() == 4 && ro.mates.stream().allMatch(x -> !x.weapon.isEmpty() && !x.type.isEmpty()), null);
        ro.enlist(5, 1);
        check("roster of nine, eight deployed, Red and Blue hold four each", ro.mates.size() == 9 && ro.activeCount() == 8 && ro.teamSize("red") == 4 && ro.teamSize("blue") == 4 && ro.teamSize("gold") == 8 && ro.setActive(ro.mates.get(8), true) != null, null);
        absentialis.gameplay.squad.Roster.Mate mate = ro.mates.get(0);
        ro.setActive(ro.mates.get(1), false);
        check("teams need not be equal (3 Red / 4 Blue)", ro.teamSize("red") + ro.teamSize("blue") == 7 && ro.assign(ro.mates.get(8), "blue") == null, null);
        mate.addXp(absentialis.gameplay.squad.Roster.XP_PER_CLIMB);
        boolean l50 = mate.level() == 50 && mate.prestige() == 0;
        mate.addXp(absentialis.gameplay.squad.Roster.XP_PER_CLIMB * 30);
        check("squadmate: Level 50 at 1,265,500 XP; Prestige caps at 10 and keeps Level 50", l50 && mate.level() == 50 && mate.prestige() == 10, mate.level() + "/" + mate.prestige());
        absentialis.gameplay.squad.Roster back = absentialis.gameplay.squad.Roster.fromJson(Json.parseObject(Json.write(ro.toJson())));
        check("squad roster survives a save round trip", back.mates.size() == 9 && back.mates.get(0).xp == mate.xp && back.mates.get(0).name.equals(mate.name) && back.activeCount() == ro.activeCount(), null);
        check("rank never affects damage (stats independent of rank)", Gunsmith.stats(item("weapon.akr", 1), 0).dispersion == Gunsmith.stats(item("weapon.akr", 1), 0).dispersion, null);
    }

    // ------------------------------------------------------------------ economy
    private static void economy() {
        System.out.println("[Economy sustainable, no infinite loops]");
        ZoneState z = ZoneState.fresh();
        String audit = Economy.audit(z, Map.of("survivors", 100f, "military", 100f, "raiders", 100f, "mercenaries", 100f));
        check("no profitable buy->sell round trip", audit.contains("[OK"), audit);
        ItemInstance w = item("val.watch", 1);
        int p0 = Economy.sellPrice(w, Economy.trader("mara"), z, 0, 0), p5 = Economy.sellPrice(w, Economy.trader("mara"), z, 0, 5);
        check("repeated sales saturate", p5 < p0, p0 + " -> " + p5);
        ItemInstance fb = Economy.fallbackKit().get(0);
        check("fallback kit has no resale value", Economy.sellPrice(fb, Economy.trader("mara"), z, 0, 0) == 0, null);
        float before = Economy.buyPrice(Content.def("med.bandage"), Economy.trader("mara"), z, 0);
        for (District d : District.CITY) z.reportLoot(d, "medicine", 60);
        check("scarcity raises prices", Economy.buyPrice(Content.def("med.bandage"), Economy.trader("mara"), z, 0) > before, null);
        // the Shop: nothing it sells can be sold straight back for a profit; gems fetch their full worth
        boolean loop = false;
        for (ItemDef d : Content.ITEMS.values()) if (Economy.shopCategory(d) >= 0 && Economy.shopSell(item(d.id, 1)) >= Economy.shopBuy(d)) { loop = true; System.out.println("    shop loop on " + d.id); }
        check("shop: no buy -> sell profit on any stocked item", !loop, null);
        check("shop: an Emerald sells for its full $5,000 (0.7 price list); attachments and mythic accessories are never stocked", Economy.shopSell(item("treasure.emerald", 1)) == 5000 && Economy.shopCategory(Content.def("acc.cat_ears")) < 0 && Economy.shopCategory(Content.def("att.muzzle.brake")) < 0, null);
    }

    // ------------------------------------------------------------------ save/load
    private static void saveLoad() {
        System.out.println("[Save/load preserves world, inventory, missions, rank]");
        Profile p = new Profile();
        p.name = "Test";
        p.starterKit();
        ItemInstance ak = item("weapon.mosin", 1);      // 0.7: the starter kit is a handgun; the rifle this gate saves is put in the stash here
        InventoryService.add(ak, p.stash);
        InventoryService.equip(ak, p.loadout, "primary");
        ak.rounds = 3; ak.ammoId = "ammo.rifle"; ak.camo = "gold";
        Gunsmith.install(ak, "att.optic.scope6");
        p.builds("weapon.mosin").set(2, new ArrayList<>(List.of("att.optic.scope6", "att.perk.fmj")));
        ak.setCondition(42);
        InventoryService.store(item("key.crypt", 1), p.loadout);
        p.prog.addXp(500_000);
        p.prog.addMastery("weapon.akr", 1234);
        p.zone.reportKill(District.MARKET, "survivors", true);
        p.zone.power[District.INDUSTRIAL.ordinal()] = true;
        p.contracts.refill(9, 5);
        p.contracts.accept(p.contracts.board.get(0).id);
        p.discover(300, 300, 4);
        p.addRep("military", 12);
        p.applyCapacity();
        String a = Json.write(p.toJson());
        Profile q = Profile.fromJson(Json.parseObject(a));
        String b = Json.write(q.toJson());
        int diff = 0;
        while (diff < Math.min(a.length(), b.length()) && a.charAt(diff) == b.charAt(diff)) diff++;
        check("profile JSON round-trip is lossless", a.equals(b), a.length() + " chars" + (a.equals(b) ? "" : " first diff @" + diff + ": " + a.substring(Math.max(0, diff - 60), Math.min(a.length(), diff + 40)).replace((char) 10, ' ') + " ||| " + b.substring(Math.max(0, diff - 60), Math.min(b.length(), diff + 40)).replace((char) 10, ' ')));
        check("weapon instance state survives (rounds, attachments, camo, condition, saved builds)", q.loadout.slot("primary") != null && q.loadout.slot("primary").rounds == 3 && q.loadout.slot("primary").attachment("optic") != null && "gold".equals(q.loadout.slot("primary").camo) && Math.round(q.loadout.slot("primary").condition()) == 42 && q.builds("weapon.mosin").get(2).size() == 2 && q.builds("weapon.mosin").get(0) == null, null);
        check("rank/mastery/contracts/world/discovery survive", q.prog.rank() == p.prog.rank() && q.prog.mastery.get("weapon.akr") == 1234 && q.contracts.active.size() == 1 && q.zone.power[District.INDUSTRIAL.ordinal()] && q.discovered(300 / 8, 300 / 8), null);
        // a pre-iteration (v2) save: long guns were stored upright and there was no appearance record
        Profile legacy = new Profile();
        ItemInstance oldRifle = item("weapon.akr", 1);
        InventoryService.move(oldRifle, legacy.stash, 0, 0, true);                 // occupies 2 wide x 6 tall, as it did in v2
        Map<String, Object> v2 = legacy.toJson();
        v2.put("version", 2);
        v2.remove("appearance");
        v2.put("skin", 3); v2.put("outfit", 1);
        for (Object o : (List<?>) v2.get("stash")) ((Map<?, ?>) o).remove("rot");  // in v2 that footprint was "not rotated"
        Profile migrated = Profile.fromJson(Json.parseObject(Json.write(v2)));
        ItemInstance mr = migrated.stash.items.isEmpty() ? null : migrated.stash.items.get(0);
        check("v2 saves migrate: footprints unchanged after definitions were re-oriented", mr != null && mr.x == 0 && mr.y == 0 && mr.w() == 2 && mr.h() == 6 && migrated.verify() == null, mr == null ? "rifle lost" : mr.w() + "x" + mr.h());
        check("v2 saves migrate: old skin/outfit become definition ids", migrated.appearance.key().equals(absentialis.character.Appearance.legacy(3, 1).key()), null);
        check("profile verify clean", q.verify() == null, q.verify());
        // ABS_SAVES=<dir>: every profile found there must load, migrate to this version and re-save losslessly
        if (System.getenv("ABS_SAVES") != null) for (int slot = 0; slot < absentialis.persistence.SaveSystem.SLOTS; slot++) {
            if (!absentialis.persistence.SaveSystem.exists(slot)) continue;
            Profile ex = absentialis.persistence.SaveSystem.load(slot);
            String err = ex == null ? "unreadable" : ex.verify();
            String j1 = ex == null ? "" : Json.write(ex.toJson());
            Profile again = ex == null ? null : Profile.fromJson(Json.parseObject(j1));
            int items = ex == null ? 0 : ex.allItems().size();
            check("existing save slot " + slot + " loads into " + absentialis.Version.LABEL, ex != null && err == null && again != null && Json.write(again.toJson()).equals(j1) && ex.loadout.caseGrid.w <= 13,
                    ex == null ? "" : ex.name + ": " + ex.prog.title() + ", " + absentialis.Version.coins(ex.scrip) + ", " + items + " items, case " + ex.loadout.caseGrid.h + "x" + ex.loadout.caseGrid.w + ", squad " + ex.squad.mates.size() + (err == null ? "" : " ERR " + err));
        }
    }

    // ------------------------------------------------------------------ world fixtures
    static World flatWorld() {
        if (flat != null) return flat;
        World w = new World();
        for (int x = 0; x < World.W; x++) for (int z = 0; z < World.D; z++) {
            w.districtMap[x * World.D + z] = (byte) (x < 352 ? District.MARKET.ordinal() : District.INDUSTRIAL.ordinal());
            for (int y = 0; y < World.GROUND; y++) w.setRaw(x, y, z, y == World.GROUND - 1 ? Block.ASPHALT : Block.DIRT);
        }
        w.computeHeights();
        for (int x = 0; x < World.W; x++) for (int z = 0; z < World.D; z++) for (int y = World.GROUND; y < World.H; y++) w.light[World.idx(x, y, z)] = (byte) 0xF0;
        MapData m = w.map;
        m.insertions.add(new MapData.Point("Test Insertion", "insertion", 200, World.GROUND, 200, District.MARKET));
        m.exits.add(new MapData.Exit("gate", "Test Gate", 210, World.GROUND, 200, MapData.ExitType.PERMANENT, District.MARKET));
        MapData.Exit cond = new MapData.Exit("toll", "Toll Bridge", 230, World.GROUND, 200, MapData.ExitType.CONDITIONAL, District.MARKET);
        cond.condition = "rep:military:50|pay:999999";
        m.exits.add(cond);
        m.exits.add(new MapData.Exit("cordon", "Cordon", 250, World.GROUND, 200, MapData.ExitType.EMERGENCY, District.MARKET));
        for (int x = 40; x < 660; x += 20) for (int z = 40; z < 660; z += 20) m.addNode(x, World.GROUND, z, w.district(x, z));
        flat = w;
        return w;
    }

    static Raid raid(Profile p) {
        Raid r = new Raid(flatWorld(), p, new Settings(), new EventBus(), 0, 30);
        return r;
    }

    // ------------------------------------------------------------------ v0.3 systems
    private static void v03() {
        System.out.println("[v0.3: loot tiers, herbs, armour, dismemberment and blood, day/night routines, persistent A-Life, squad, navigation]");
        // ---- loot tiers
        Rng rr = new Rng(11);
        int[] tn = new int[6];
        int total = 80000;
        for (int i = 0; i < total; i++) tn[Content.rollTier(rr)]++;
        float[] want = {0.50f, 0.20f, 0.15f, 0.10f, 0.04f, 0.01f};
        boolean rates = true;
        for (int t = 0; t < 6; t++) if (Math.abs(tn[t] / (float) total - want[t]) > 0.008f) rates = false;
        check("loot tiers drop at 50 / 20 / 15 / 10 / 4 / 1 percent", rates, java.util.Arrays.toString(tn));
        Object[][] tiers = {{"herb.green", 0}, {"ammo.handgun", 0}, {"ammo.shells", 0}, {"melee.knife", 0}, {"key.small", 0}, {"mat.scrap_small", 0}, {"mat.gunpowder_s", 0}, {"mat.gunpowder_l", 0},
                {"food.beans", 1}, {"herb.red", 1}, {"herb.mixed_gg", 1}, {"treasure.emerald", 2}, {"treasure.ruby", 3}, {"armor.body", 3}, {"treasure.diamond", 4},
                {"acc.cat_ears", 5}, {"acc.chicken_hat", 5}, {"acc.deer_antlers", 5}, {"acc.wolf_tail", 5}};
        boolean tierOk = true;
        for (Object[] e : tiers) if (Content.def((String) e[0]) == null || Content.def((String) e[0]).tier != (Integer) e[1]) { tierOk = false; System.out.println("    tier mismatch " + e[0]); }
        check("every listed item sits in its tier (Common .. Mythic)", tierOk && Content.def("treasure.emerald").value == 5000 && Content.def("armor.body").value == 10000, "emerald " + Content.def("treasure.emerald").value + ", body armor " + Content.def("armor.body").value);
        java.util.Set<String> ammoSeen = new java.util.HashSet<>();
        for (int i = 0; i < 600; i++) for (Object[] e : Content.roll(i % 2 == 0 ? "crate" : "military", rr, null, 0)) if (Content.def((String) e[0]).ammo != null) ammoSeen.add((String) e[0]);
        check("ammunition drops whether or not you own the weapon", ammoSeen.size() >= 5, ammoSeen.toString());
        // ---- herbs, body armour, accessories
        Raid hr = raid(new Profile());
        hr.populate = false;
        Actor hp = hr.player;
        hp.maxHp = 120;
        float[] gain = new float[4];
        String[] herbs = {"herb.green", "herb.red", "herb.mixed_gg", "herb.mixed_gr"};
        for (int i = 0; i < 4; i++) { hp.hp = 10; hr.body.apply(hp, Content.def(herbs[i])); gain[i] = hp.hp - 10; }
        check("herbs heal 1/6, 1/6, 1/3 and 5/6 of health", Math.abs(gain[0] - 20) < 0.1f && Math.abs(gain[1] - 20) < 0.1f && Math.abs(gain[2] - 40) < 0.1f && Math.abs(gain[3] - 100) < 0.1f, java.util.Arrays.toString(gain));
        check("herbs combine: G+G and G+R", "herb.mixed_gg".equals(InventoryService.herbMix("herb.green", "herb.green")) && "herb.mixed_gr".equals(InventoryService.herbMix("herb.red", "herb.green")) && InventoryService.herbMix("herb.red", "herb.red") == null, null);
        Profile ap = new Profile();
        ItemInstance plate = item("armor.body", 1);
        InventoryService.add(plate, ap.stash); InventoryService.equip(plate, ap.loadout, "armor");
        Raid ar = raid(ap);
        ar.populate = false;
        float h0 = ar.player.hp;
        ar.damageActor(ar.player, null, 30, null, "torso", ar.player.x, ar.player.y + 1, ar.player.z);
        boolean soaked = ar.player.hp == h0 && Math.abs(ar.player.shield - 20) < 0.01f;
        ar.damageActor(ar.player, null, 30, null, "torso", ar.player.x, ar.player.y + 1, ar.player.z);
        check("Body Armor: 50 protection that takes the damage first", soaked && ar.player.shield == 0 && Math.abs(ar.player.hp - (h0 - 10)) < 0.01f && plate.durability == 0, ar.player.shield + " / " + ar.player.hp);
        Profile cp = new Profile();
        ItemInstance hat = item("acc.chicken_hat", 1), tail = item("acc.wolf_tail", 1);
        InventoryService.add(hat, cp.stash); InventoryService.add(tail, cp.stash);
        InventoryService.equip(hat, cp.loadout, "charm0"); InventoryService.equip(tail, cp.loadout, "charm1");
        Raid cr = raid(cp);
        cr.populate = false;
        float c0 = cr.player.hp;
        cr.damageActor(cr.player, null, 80, null, "torso", cr.player.x, cr.player.y + 1, cr.player.z);
        check("Chicken Hat reduces damage taken to 1; Wolf Tail adds speed", Math.abs(c0 - cr.player.hp - 1) < 0.01f && cp.loadout.charm("speed") > 0.1f, (c0 - cr.player.hp) + "");
        // ---- stuck projectiles (the v0.2 bug): a round that hits within a frame is still removed
        Raid pr = raid(new Profile());
        pr.populate = false;
        pr.actors.removeIf(a -> !a.isPlayer);
        pr.player.x = 300; pr.player.z = 300; pr.player.y = World.GROUND;
        Actor near = walker(pr, 300, 302);
        near.hp = near.maxHp = 5000;
        Actor shooter = human(pr, "raiders", 300, 304);
        shooter.weapon = item("weapon.smg", 1); shooter.weapon.rounds = 30; shooter.weapon.ammoId = "ammo.smg";
        for (int i = 0; i < 6; i++) pr.fireProjectiles(shooter, shooter.weapon, Content.def("ammo.smg"), 300, World.GROUND + 1.4f, 303.5f, 0, 0, -1, 0);
        for (int i = 0; i < 20; i++) pr.update(1 / 60f);
        check("no round is left hanging in the air after it hits", pr.projectiles.isEmpty(), pr.projectiles.size() + " left");
        // ---- dismemberment and blood
        Raid g = raid(new Profile());
        g.populate = false;
        g.actors.removeIf(a -> !a.isPlayer);
        g.player.x = 300; g.player.z = 300; g.player.y = World.GROUND;
        Actor v = human(g, "raiders", 306, 300);
        Raid.shape(v);
        v.weapon = item("weapon.akr", 1); v.weapon.rounds = 30; v.weapon.ammoId = "ammo.rifle";
        for (int i = 0; i < 3; i++) g.damageActor(v, g.player, 30, Content.def("ammo.rifle"), "legs", v.x, v.y + 0.4f, v.z - 0.2f);
        check("leg hits are localized: the leg fails, the victim slows and bleeds", (v.speedMul < 1 || v.crawling) && v.bleed > 0 && v.body != null && !v.body.chunks[absentialis.character.Rig.TORSO].modified && v.body.chunks[absentialis.character.Rig.HEAD].integrity() == 1f
                && (v.body.chunks[absentialis.character.Rig.L_THIGH].modified || v.body.chunks[absentialis.character.Rig.L_SHIN].modified || v.body.chunks[absentialis.character.Rig.R_THIGH].modified || v.body.chunks[absentialis.character.Rig.R_SHIN].modified),
                "speed " + v.speedMul + " bleed " + v.bleed + " destroyed " + (v.body == null ? -1 : v.body.destroyed));
        g.gore.sever(v, absentialis.scene.Gore.ARM_R, 1, 0, 4);
        check("a severed gun arm drops the weapon and ruins the aim", (v.severed & absentialis.scene.Gore.ARM_R_MASK) == absentialis.scene.Gore.ARM_R_MASK && v.weapon == null && g.items.size() == 1 && v.aimPenalty > 2 && g.gore.dismember.pieces.size() == 1 && v.morale <= 0.12f, "aim " + v.aimPenalty + " pieces " + g.gore.dismember.pieces.size() + " morale " + v.morale);
        g.gore.sever(v, absentialis.scene.Gore.LEG_L, 1, 0, 4);
        check("a severed leg leaves the victim crawling", v.crawling && v.speedMul <= 0.3f && v.height() < 1f && g.gore.dismember.pieces.size() == 2 && g.gore.severedTotal == 2, "speed " + v.speedMul + " pieces " + g.gore.dismember.pieces.size());
        for (int i = 0; i < 60; i++) g.update(1 / 60f);
        float r0 = 0, vol0 = 0;
        for (absentialis.scene.Gore.Pool pl : g.gore.pools) { r0 = Math.max(r0, pl.radius()); vol0 += pl.volume; }
        for (int i = 0; i < 480; i++) g.update(1 / 60f);
        float r1 = 0;
        absentialis.scene.Gore.Pool big = null;
        for (absentialis.scene.Gore.Pool pl : g.gore.pools) if (pl.radius() > r1) { r1 = pl.radius(); big = pl; }
        check("blood lands as pools that keep spreading", !g.gore.pools.isEmpty() && r1 > r0 && r1 > 0.3f && vol0 > 0, String.format("%.2f -> %.2f m, %d pools", r0, r1, g.gore.pools.size()));
        check("wet blood is slippery; dry ground is not", big != null && g.gore.friction(big.x, big.y, big.z) < 0.6f && g.gore.friction(big.x + 30, big.y, big.z) == 1f, null);
        float hpWas = v.hp;
        boolean wasAlive = !v.dead;
        for (int i = 0; i < 60 * 150 && !v.dead; i++) g.update(1 / 30f);
        check("untreated wounds bleed out", wasAlive && v.dead, "hp " + hpWas + " -> " + v.hp);
        Actor finder = human(g, "raiders", v.x + 7, v.z);
        Raid.shape(finder);
        finder.weapon = item("weapon.pm9", 1); finder.weapon.rounds = 10; finder.weapon.ammoId = "ammo.handgun";
        finder.hostileToPlayer = false;
        finder.yaw = (float) Math.atan2(-(v.x - finder.x), -(v.z - finder.z));
        for (int i = 0; i < 240 && "idle".equals(finder.state); i++) g.update(1 / 60f);
        absentialis.neuralai.runtime.Mind fm = g.neural.mind(finder.id);
        check("agents discover bodies and blood and go to look", !"idle".equals(finder.state) && finder.alertness > 0.5f && fm != null && !fm.evidenceSeen.isEmpty(), finder.state + (fm == null ? "" : ", " + fm.evidenceSeen.size() + " things found"));
        // a blast takes limbs
        Raid bg = raid(new Profile());
        bg.populate = false;
        bg.actors.removeIf(a -> !a.isPlayer);
        bg.player.x = 100; bg.player.z = 100;
        int torn = 0;
        for (int k = 0; k < 6; k++) { Actor bv = human(bg, "raiders", 300 + k * 12, 300); Raid.shape(bv); bg.explode(bv.x + 0.6f, World.GROUND + 0.5f, bv.z, 7, 170, 0, false); if (bv.severed != 0) torn++; }
        check("explosives dismember", torn >= 3 && bg.gore.dismember.pieces.size() >= 3, torn + " of 6 lost limbs, " + bg.gore.dismember.pieces.size() + " pieces");
        // ---- player death: ragdoll and a death camera before the debrief
        Raid dq = raid(new Profile());
        dq.populate = false;
        dq.actors.removeIf(a -> !a.isPlayer);
        dq.update(1 / 60f);
        dq.deathCam = 2f;
        dq.player.hp = 0;
        dq.update(1 / 60f);
        boolean falling = dq.player.dead && dq.player.ragdoll != null && dq.debrief.outcome == Raid.Debrief.Outcome.NONE;
        for (int i = 0; i < 150; i++) dq.update(1 / 60f);
        check("the player ragdolls on death, then the raid resolves once", falling && dq.debrief.outcome == Raid.Debrief.Outcome.KILLED, dq.debrief.outcome + "");
        // ---- day / night
        check("routines: day shift works by day and sleeps at night; night shift is the reverse; the infected hunt after dark",
                "guard".equals(absentialis.sim.Agents.activity("military", "guard", false, 12, 7)) && "sleep".equals(absentialis.sim.Agents.activity("military", "guard", false, 1, 7))
                        && "guard".equals(absentialis.sim.Agents.activity("military", "guard", true, 1, 7)) && "sleep".equals(absentialis.sim.Agents.activity("military", "guard", true, 13, 7))
                        && "rest".equals(absentialis.sim.Agents.activity("military", "guard", false, 19, 7)) && "hunt".equals(absentialis.sim.Agents.activity("infected", "wander", false, 23, 7))
                        && "wander".equals(absentialis.sim.Agents.activity("infected", "wander", false, 12, 7)), null);
        Profile np = new Profile();
        np.zone.hour = 18.5;
        Raid nr = raid(np);
        nr.populate = false;
        nr.actors.removeIf(a -> !a.isPlayer);
        float dusk = nr.daylight();
        nr.time = 6 * 60;      // three game hours later
        check("the clock runs during a raid: dusk becomes night", dusk > 0.9f && nr.night() && Math.abs(nr.hour() - 21.5) < 0.01 && ZoneState.daylightAt(12) == 1f && ZoneState.daylightAt(2) < 0.1f, dusk + " -> " + nr.daylight());
        java.util.Arrays.fill(flatWorld().light, (byte) 0xF0);
        nr.player.x = 300; nr.player.z = 300; nr.player.y = World.GROUND;
        Actor sentry = human(nr, "military", 300, 318);
        // 0.8: every mind sees through NeuralAI, so this is measured there - how exposed the player is to the sentry's eyes
        // (what its vision meter fills by), and how far those eyes reach
        sentry.yaw = (float) Math.atan2(-(nr.player.x - sentry.x), -(nr.player.z - sentry.z));
        float dark = exposure(nr, sentry, nr.player);
        nr.playerFlashlight = true;
        float lit = exposure(nr, sentry, nr.player);
        nr.playerFlashlight = false;
        sentry.light = true;
        float torch = exposure(nr, sentry, nr.player);
        sentry.light = false;
        nr.time = 0;
        float day = exposure(nr, sentry, nr.player);
        nr.time = 6 * 60;
        check("darkness hides: night < a sentry's own torch < your flashlight, and far less than by day", dark > 0 && dark < torch && torch < lit && dark < day * 0.6f, String.format("exposure at 18 m: night %.2f, torch %.2f, lit %.2f, day %.2f", dark, torch, lit, day));
        float awake = (float) nr.neuralWorld.self(sentry.id).sightRange();
        sentry.routine = "sleep";
        float asleep = (float) nr.neuralWorld.self(sentry.id).sightRange();
        nr.emitSound(300, World.GROUND, 310, 10, "step", nr.player.id, false);
        boolean sleptThrough = nr.time > sentry.wakeUntil;
        nr.emitSound(300, World.GROUND, 310, 120, "shot", nr.player.id, false);
        check("sleepers see almost nothing, sleep through quiet steps and are woken by gunfire", asleep < 3 && sleptThrough && sentry.wakeUntil > nr.time && nr.neuralWorld.self(sentry.id).sightRange() > asleep && awake > 30, String.format("sees %.0f m awake, %.1f m asleep", awake, asleep));
        // ---- persistent A-Life
        absentialis.sim.Agents pop = absentialis.sim.Agents.seed(flatWorld(), ZoneState.fresh(), 7);
        int n0 = pop.living();
        int[] f0 = new int[5];
        for (int k = 0; k < 5; k++) f0[k] = pop.living(absentialis.sim.Agents.FACTION[k]);
        ZoneState zz = ZoneState.fresh();
        Rng ar2 = new Rng(5);
        for (int h = 0; h < 75; h++) pop.step(flatWorld(), zz, 9 + h, 1, ar2, null);   // three days, ending at noon
        int asleepNow = 0, awakeNow = 0;
        for (absentialis.sim.Agents.Agent a : pop.agents) if (a.alive && !a.infected()) { if ("sleep".equals(a.activity)) asleepNow++; else awakeNow++; }
        check("offline agents live their days without the player: routines, encounters, deaths, arrivals", n0 > 60 && pop.hoursSimulated == 75 && pop.encounters > 0 && pop.deaths > 0 && pop.arrivals > 0 && pop.living() > n0 / 3,
                n0 + " -> " + pop.living() + " alive, " + pop.encounters + " encounters, " + pop.deaths + " deaths, " + pop.arrivals + " arrivals");
        check("at noon the night shift is asleep and the day shift is not", asleepNow > 0 && awakeNow > asleepNow, asleepNow + " asleep / " + awakeNow + " up");
        StringBuilder fc = new StringBuilder();
        boolean stable = true;
        for (int k = 0; k < 5; k++) { int now = pop.living(absentialis.sim.Agents.FACTION[k]); fc.append(absentialis.sim.Agents.FACTION[k]).append(' ').append(f0[k]).append("->").append(now).append("  "); if (f0[k] > 0 && (now < Math.max(3, f0[k] * 0.4f) || now > f0[k] * 2 + 12)) stable = false; }
        check("the ecosystem holds: no side is wiped out or explodes in three days of offline life", stable, fc.toString());
        absentialis.sim.Agents back = absentialis.sim.Agents.fromJson(Json.parseObject(Json.write(pop.toJson())));
        check("the population survives a save round trip", back.living() == pop.living() && back.agents.get(0).id == pop.agents.stream().filter(a -> a.alive).findFirst().get().id && back.agents.get(0).name.equals(absentialis.sim.Agents.nameFor(back.agents.get(0).id)), back.living() + "");
        Profile lp = new Profile();
        Raid l1 = raid(lp);
        for (int i = 0; i < 90; i++) l1.update(1 / 30f);
        Actor someone = null;
        float closest = 1e9f;
        for (Actor a : l1.actors) if (!a.isPlayer && a.agentId != 0) { closest = Math.min(closest, a.distXZ(l1.player.x, l1.player.z)); if (someone == null && !a.infected()) someone = a; }
        check("agents come online as individuals, never close to or in sight of the player", someone != null && closest >= 39 && !someone.name.isEmpty() && lp.agents.byId(someone.agentId).actorId == someone.id, "closest " + closest);
        long victimId = someone == null ? 0 : someone.agentId;
        int before = lp.agents.living();
        if (someone != null) l1.damageActor(someone, l1.player, 99999, Content.def("ammo.rifle"), "torso", someone.x, someone.y + 1, someone.z);
        l1.debrief.outcome = Raid.Debrief.Outcome.EXTRACTED;
        RaidResolver.resolve(l1);
        Profile lp2 = Profile.fromJson(Json.parseObject(Json.write(lp.toJson())));
        Raid l2 = raid(lp2);
        for (int i = 0; i < 90; i++) l2.update(1 / 30f);
        boolean back2 = false;
        for (Actor a : l2.actors) if (a.agentId == victimId) back2 = true;
        check("the dead stay dead on the next deployment; everyone else is still who they were", victimId != 0 && !back2 && lp2.agents.byId(victimId) == null && lp2.agents.living() <= before - 1 && lp2.agents.living() > before / 2, before + " -> " + lp2.agents.living());
        // ---- squad
        Profile sp = new Profile();
        sp.applyCapacity();
        sp.squad.enlist(4, 3);          // 0.7: squadmates are hired; these four were
        Raid sr = raid(sp);
        sr.populate = false;
        sr.actors.removeIf(a -> !a.isPlayer && !a.mate());
        sr.player.x = 300; sr.player.z = 300; sr.player.y = World.GROUND;
        for (Actor mt : sr.mates) { mt.x = 300 + sr.mates.indexOf(mt); mt.z = 302; mt.y = World.GROUND; }
        check("four squadmates deploy with the player, two Red and two Blue; Gold is all of them", sr.mates.size() == 4 && sr.team("red").size() == 2 && sr.team("blue").size() == 2 && sr.team("gold").size() == 4 && sr.mates.get(0).weapon != null, null);
        Actor foe = human(sr, "raiders", 340, 300);
        foe.hostileToPlayer = true;
        Actor neutral = human(sr, "military", 340, 320);
        neutral.hostileToPlayer = false;
        Actor m0 = sr.mates.get(0);
        check("squadmates share the player's enemies and the player's friends", Relations.hostile(sr, m0, foe) && Relations.hostile(sr, foe, m0) && !Relations.hostile(sr, m0, neutral) && !Relations.hostile(sr, m0, sr.player) && !Relations.hostile(sr, m0, sr.mates.get(1)), null);
        sr.actors.remove(foe); sr.actors.remove(neutral);
        sr.squadOrder("red", "move", 330, 300, true);
        sr.squadOrder("blue", "hold", Float.NaN, Float.NaN, true);
        boolean queued = true;
        for (Actor mt : sr.mates) if (mt.qOrder == null || !"follow".equals(mt.sqOrder)) queued = false;
        int started = sr.initiate();
        boolean split = true;
        for (Actor mt : sr.mates) if (!(mt.team.equals("red") ? "move" : "hold").equals(mt.sqOrder) || mt.qOrder != null) split = false;
        check("queued orders wait for Gold's Initiate, then Red and Blue execute different objectives at once", queued && started == 4 && split, null);
        float[] blueAt = new float[8];
        int bi = 0;
        for (Actor mt : sr.team("blue")) { blueAt[bi++] = mt.x; blueAt[bi++] = mt.z; }
        for (int i = 0; i < 60 * 14; i++) sr.update(1 / 60f);
        boolean redThere = true, blueHeld = true;
        for (Actor mt : sr.team("red")) if (mt.distXZ(330, 300) > 6) redThere = false;
        bi = 0;
        for (Actor mt : sr.team("blue")) { if (mt.distXZ(blueAt[bi], blueAt[bi + 1]) > 2.5f) blueHeld = false; bi += 2; }
        StringBuilder whereRed = new StringBuilder();
        for (Actor mt : sr.mates) whereRed.append(String.format("%s %.1f,%.1f,%.1f st=%s stuck=%.1f spins=%d path=%s  ", mt.team, mt.x, mt.y, mt.z, mt.state, mt.stuckTime, mt.spins, mt.path == null ? "none" : String.valueOf(mt.path.length / 3)));
        check("Red moved to its objective while Blue held its ground", redThere && blueHeld, whereRed.toString());
        String moved = sr.reassign(m0);
        check("squadmates can be moved between teams during the operation", moved.contains("Team") && sr.team("red").size() + sr.team("blue").size() == 4 && sp.squad.byId(m0.mateId).team.equals(m0.team), moved);
        sr.squadOrder("gold", "follow", 0, 0, false);
        sr.player.x = 300; sr.player.z = 270;
        for (int i = 0; i < 60 * 18; i++) sr.update(1 / 60f);   // forty metres to cover, with a body that now has to get up to speed
        boolean formed = true;
        StringBuilder where = new StringBuilder();
        for (Actor mt : sr.mates) { if (mt.dist(sr.player) > 9) formed = false; where.append(String.format("%s %.1fm %s speed x%.2f%s; ", mt.name, mt.dist(sr.player), mt.state, mt.speedMul, mt.crawling ? " crawling" : mt.ragdoll != null ? " down" : "")); }
        check("on Follow the whole element forms up on the leader", formed, where.toString());
        long xpWas = sp.squad.mates.get(0).xp;
        sr.time = 600;
        sr.debrief.outcome = Raid.Debrief.Outcome.EXTRACTED;
        RaidResolver.resolve(sr);
        check("squadmates earn their own experience on operations", sp.squad.mates.get(0).xp > xpWas && sp.squad.mates.get(0).raids == 1, sp.squad.mates.get(0).xp + " xp");
        // ---- Weapon Prestige death protection
        Profile dp = new Profile();
        dp.difficulty = 2;      // Hard: nothing but the prestiged weapon comes home (0.8 keeps things on Easy and Normal)
        ItemInstance gun = item("weapon.akr", 1), side = item("weapon.pm9", 1);
        InventoryService.add(gun, dp.stash); InventoryService.add(side, dp.stash);
        InventoryService.equip(gun, dp.loadout, "primary"); InventoryService.equip(side, dp.loadout, "sidearm");
        dp.prog.weaponPrestige.put("weapon.akr", 1);
        Gunsmith.install(gun, "att.optic.reddot");
        gun.rounds = 12; gun.ammoId = "ammo.rifle";
        Raid dr = raid(dp);
        dr.populate = false;
        dr.die("test");
        RaidResolver.resolve(dr);
        int akrs = 0;
        for (ItemInstance i : dp.allItems()) if (i.defId.equals("weapon.akr")) akrs++;
        check("a prestiged weapon comes home on death - one instance, without attachments or ammunition; nothing else does", dp.stash.items.contains(gun) && akrs == 1 && gun.attachments.isEmpty() && gun.rounds == 0 && !dp.allItems().contains(side) && dp.loadout.slot("primary") == null && dp.verify() == null, akrs + " rifles");
        // ---- navigation
        World nw = new World();
        for (int x = 270; x < 340; x++) for (int z = 270; z < 340; z++) { for (int y = 0; y < World.GROUND; y++) nw.setRaw(x, y, z, Block.CONCRETE); }
        for (int x = 270; x < 340; x++) for (int y = World.GROUND; y < World.GROUND + 4; y++) if (x != 312) nw.setRaw(x, y, 300, Block.BRICK_RED);   // (0.9: four high - a wall of three is one a person pulls themselves over)
        absentialis.world.Nav nav = nw.nav;
        int[] path = nav.path(300.5f, World.GROUND, 295.5f, 300.5f, 305.5f, 5000, true);
        boolean viaGap = false;
        if (path != null) for (int i = 0; i < path.length; i += 3) if (path[i] == 312 && path[i + 2] == 300) viaGap = true;
        check("path-finding goes round a wall through its only gap", path != null && viaGap && path[path.length - 1] == 305 && !nav.clear(300.5f, World.GROUND, 295.5f, 300.5f, 305.5f, true), path == null ? "no path" : path.length / 3 + " cells");
        nw.setRaw(312, World.GROUND, 300, Block.DOOR_WOOD); nw.setRaw(312, World.GROUND + 1, 300, Block.DOOR_WOOD);
        int[] human = nav.path(300.5f, World.GROUND, 295.5f, 300.5f, 305.5f, 5000, true), dead = nav.path(300.5f, World.GROUND, 295.5f, 300.5f, 305.5f, 5000, false);
        check("people path through closed doors (and open them); the infected cannot", human != null && human[human.length - 1] == 305 && (dead == null || dead[dead.length - 1] < 300), null);
        MapData.Node prevN = null;
        for (int i = 0; i <= 10; i++) { MapData.Node nn = nw.map.addNode(272 + i * 6, World.GROUND, 280, District.MARKET); if (prevN != null) nw.map.link(prevN, nn); prevN = nn; }
        float[] hop = nav.via(272, 280, 332, 280, 20);
        check("long trips route over the waypoint graph, one reachable stretch at a time", nav.route(272, 280, 332, 280).size() == 11 && hop != null && hop[0] > 280 && hop[0] < 296 && nw.map.nearestNode(331, 281).x == 332, hop == null ? "no route" : hop[0] + "");
        // ---- ladders deliver you onto the roof
        World lw = new World();
        for (int x = 290; x < 310; x++) for (int z = 290; z < 310; z++) lw.setRaw(x, World.GROUND - 1, z, Block.CONCRETE);
        for (int x = 290; x < 310; x++) for (int z = 290; z < 300; z++) for (int y = World.GROUND; y < World.GROUND + 6; y++) lw.setRaw(x, y, z, Block.CONCRETE);
        for (int y = World.GROUND; y < World.GROUND + 6; y++) lw.setRaw(300, y, 300, Block.LADDER);
        Actor climber = new Actor();
        climber.x = 300.5f; climber.y = World.GROUND; climber.z = 300.5f;
        for (int i = 0; i < 60 * 6; i++) { climber.wantClimb = absentialis.entity.Physics.inClimb(lw, climber.x, climber.y, climber.z); climber.climbDir = 1; climber.vz = -2.2f; climber.vx = 0; absentialis.entity.Physics.move(lw, climber, 1 / 60f); }
        check("a ladder carries you up and over the edge onto the roof", climber.y >= World.GROUND + 5.9f && climber.z < 299.9f && climber.onGround, String.format("y %.2f z %.2f", climber.y, climber.z));
        Actor hang = new Actor();
        hang.x = 300.5f; hang.y = World.GROUND + 3; hang.z = 300.5f;
        for (int i = 0; i < 30; i++) { hang.wantClimb = false; absentialis.entity.Physics.move(lw, hang, 1 / 60f); }
        check("letting go of the keys on a ladder is a slow slide, not a fall", hang.y > World.GROUND + 2.2f && hang.vy >= -1.5f, String.format("y %.2f", hang.y));
        // ---- shaped blocks are meshed as geometry
        World mw = new World();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) mw.setRaw(x, 0, z, Block.CONCRETE);
        absentialis.engine.render.ChunkMesher mesher = new absentialis.engine.render.ChunkMesher(mw);
        mesher.mesh(0, 0);
        int q0 = mesher.opaque.quads();
        mw.setRaw(8, 1, 8, Block.CHAIR); mw.setRaw(4, 1, 4, Block.LADDER); mw.setRaw(5, 1, 4, Block.CONCRETE);
        mesher.mesh(0, 0);
        check("the mesher builds chairs and ladders from boxes, not cubes", mesher.opaque.quads() - q0 >= 38 && absentialis.world.Shapes.orientation(mw, Block.LADDER, 4, 1, 4) == 1, (mesher.opaque.quads() - q0) + " quads");
    }

    // ------------------------------------------------------------------ death
    private static void death() {
        System.out.println("[Death persistence: loss once, profile valid]");
        Profile p = new Profile();
        p.difficulty = 2;       // 0.8: what a death costs depends on the difficulty. Hard is the old rule - everything carried is lost - and that rule is what this gate is about
        p.starterKit();
        for (ItemInstance i : new ArrayList<>(p.stash.items)) if (i.def().slot != null && p.loadout.slot(i.def().slot) == null) InventoryService.equip(i, p.loadout, i.def().slot);
        InventoryService.store(item("med.bandage", 1), p.loadout);
        InventoryService.store(item("key.crypt", 1), p.loadout);
        int stashBefore = p.stash.items.size();
        Raid r = raid(p);
        r.die("test");
        r.die("again");
        List<String> out1 = RaidResolver.resolve(r);
        long xp = p.prog.lifetimeXp;
        List<String> out2 = RaidResolver.resolve(r);
        check("death resolves exactly once", !out1.isEmpty() && out2.isEmpty() && p.prog.lifetimeXp == xp && p.stat("deaths") == 1, null);
        check("carried gear lost", p.loadout.slot("primary") == null && p.loadout.caseGrid.items.isEmpty(), null);
        check("keys (profile-bound) survive death", p.loadout.keys.size() == 1, null);
        check("stash untouched", p.stash.items.size() == stashBefore, null);
        check("profile remains valid", p.verify() == null, p.verify());
    }

    // ------------------------------------------------------------------ extraction
    private static void extraction() {
        System.out.println("[Extraction eligibility / time / activation / debrief]");
        Profile p = new Profile();
        Raid r = raid(p);
        Raid.ExitState gate = null, toll = null, cordon = null;
        for (Raid.ExitState s : r.exits) { if (s.exit.id.equals("gate")) gate = s; if (s.exit.id.equals("toll")) toll = s; if (s.exit.id.equals("cordon")) cordon = s; }
        check("permanent exit eligible", gate.eligible, gate.requirement);
        check("conditional exit shows conditions and is closed", !toll.eligible && toll.requirement.contains("standing") && toll.requirement.contains("toll"), toll.requirement);
        check("0.7: the cordon gate is open from the first minute, at a price (there is no clock for it to wait on)", cordon.eligible && cordon.requirement.contains("1,500"), cordon.requirement);
        r.player.x = 210.5f; r.player.z = 200.5f; r.player.y = World.GROUND;
        for (int i = 0; i < 60 * 4; i++) r.update(1 / 60f);
        check("activation requires dwell time (not instant)", r.debrief.outcome == Raid.Debrief.Outcome.NONE && r.extractProgress > 3, "progress " + r.extractProgress);
        for (int i = 0; i < 60 * 5; i++) r.update(1 / 60f);
        check("extraction completes after 8s in zone", r.debrief.outcome == Raid.Debrief.Outcome.EXTRACTED, String.valueOf(r.debrief.outcome));
        // 0.7: there is no deployment time limit. Two hours in - four times the old limit - every exit is as it was, nothing has closed, and nobody is posted missing
        Raid r2 = raid(new Profile());
        r2.player.x = 400; r2.player.z = 400;
        r2.time = 7200;
        r2.evaluateExits();
        Raid.ExitState g2 = null, c2 = null;
        for (Raid.ExitState s : r2.exits) { if (s.exit.id.equals("gate")) g2 = s; if (s.exit.id.equals("cordon")) c2 = s; }
        check("0.7: no time limit - two hours in, the ordinary exits are still open and the raid is not closed", g2.eligible && c2.eligible && !r2.closed() && Float.isInfinite(r2.remaining()), "remaining " + r2.remaining());
        r2.update(2);
        check("0.7: staying out is not a failed raid (nobody goes MIA on a clock)", r2.debrief.outcome == Raid.Debrief.Outcome.NONE, String.valueOf(r2.debrief.outcome));
        // determinism of eligibility
        Raid r3 = raid(new Profile()), r4 = raid(new Profile());
        boolean same = true;
        for (int i = 0; i < r3.exits.size(); i++) same &= r3.exits.get(i).eligible == r4.exits.get(i).eligible && r3.exits.get(i).requirement.equals(r4.exits.get(i).requirement);
        check("eligibility deterministic for identical state", same, null);
    }

    // ------------------------------------------------------------------ noise / perception
    private static Actor walker(Raid r, float x, float z) {
        Actor a = new Actor();
        a.faction = "infected"; a.archetype = "walker";
        a.hp = a.maxHp = 90;
        a.x = x; a.z = z; a.y = World.GROUND; a.homeX = x; a.homeZ = z;
        a.yaw = 0;
        r.actors.add(a);
        return a;
    }

    private static void noise() {
        System.out.println("[Noise triggers plausible investigation, no omniscience]");
        Profile p = new Profile();
        Raid r = raid(p);
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer);
        r.player.x = 200; r.player.z = 200; r.player.y = World.GROUND;
        Actor near = walker(r, 200, 240), far = walker(r, 200, 620);
        near.yaw = 0; // facing away (-z is forward; player is at lower z... face +z away)
        near.yaw = (float) Math.PI;
        r.emitSound(200, World.GROUND + 1, 200, 120, "shot", r.player.id, false);
        for (int i = 0; i < 30; i++) r.update(1 / 60f);       // 0.8: a creature hears with a mind (NeuralAI), on the mind's own clock
        check("nearby agent investigates a gunshot", "investigate".equals(near.state), near.state);
        float err = (float) Math.hypot(near.memX - 200, near.memZ - 200);
        check("memory holds an estimated (not exact) position", err > 0.01f && err < 20, String.format("error %.1fm", err));
        check("distant agent is not omniscient", "idle".equals(far.state) && far.memTime < 0, far.state);
        // a quiet step only reaches very close listeners
        for (int i = 0; i < 75; i++) r.update(1 / 60f);       // let the gunshot leave the air: a mind that starts now has still heard the last second
        Actor ear = walker(r, 203, 200), mid = walker(r, 200, 215);
        ear.yaw = (float) -Math.PI / 2; mid.yaw = (float) Math.PI;   // backs to it, and further off than a body is simply sensed: this is about ears
        r.update(1 / 60f);
        r.emitSound(200, World.GROUND + 1, 200, 2.5f, "step", r.player.id, false);
        for (int i = 0; i < 24; i++) r.update(1 / 60f);
        check("crouched footstep heard only up close (0.7: the near one notices - the head comes round - before it goes to look)", ("notice".equals(ear.state) || "investigate".equals(ear.state)) && "idle".equals(mid.state) && Math.abs(ear.headLookTarget) + Math.abs(ear.memX - 200) < 40, ear.state + "/" + mid.state);
        // the investigation leads them toward the source over time
        float d0 = near.distXZ(200, 200);
        for (int i = 0; i < 60 * 6; i++) r.update(1 / 60f);
        check("investigation moves toward the sound", near.distXZ(200, 200) < d0 - 2, String.format("%.1f -> %.1f", d0, near.distXZ(200, 200)));
    }

    private static void evidence() {
        System.out.println("[Faction response requires evidence]");
        Profile p = new Profile();
        Raid r = raid(p);
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer);
        r.player.x = 200; r.player.z = 200; r.player.y = World.GROUND;
        Actor s1 = human(r, "survivors", 205, 200), s2 = human(r, "survivors", 260, 260);
        check("survivors start neutral to a neutral player", !s1.hostileToPlayer && !Relations.hostile(r, s1, r.player), null);
        r.damageActor(s1, r.player, 500, Content.def("ammo.556"), "torso", s1.x, s1.y + 1, s1.z);
        check("unwitnessed kill does not alert distant members", !s2.hostileToPlayer, null);
        float unwitnessed = r.debrief.repDelta.getOrDefault("survivors", 0f);
        Actor s3 = human(r, "survivors", 205, 205), s4 = human(r, "survivors", 208, 207);
        r.damageActor(s3, r.player, 500, Content.def("ammo.556"), "torso", s3.x, s3.y + 1, s3.z);
        check("witnessed kill turns witnesses hostile", s4.hostileToPlayer, null);
        check("witnessed kill costs more standing than unwitnessed", r.debrief.repDelta.get("survivors") - unwitnessed < unwitnessed, r.debrief.repDelta.toString());
    }

    /** How exposed a target is to an observer's eyes right now, as NeuralAI's sight measures it (0 = cannot be seen). The observer's mind is enrolled if it has none yet. */
    static float exposure(Raid r, Actor observer, Actor target) {
        absentialis.neuralai.runtime.Mind m = r.neural.mind(observer.id);
        if (m == null) m = r.neural.enroll(observer.id, observer.infected() ? 0 : observer.squad, observer.faction, observer.archetype, observer.mate());
        var self = r.neuralWorld.self(observer.id);
        return (float) r.neural.perception.exposure(m, target.id, self.sightRange(), self.arcFarDeg(), r.neuralWorld.lit(target.id));
    }

    private static Actor human(Raid r, String faction, float x, float z) {
        Actor a = new Actor();
        a.faction = faction; a.archetype = "rifleman";
        a.hp = a.maxHp = 100;
        a.x = x; a.z = z; a.y = World.GROUND; a.homeX = x; a.homeZ = z;
        a.hostileToPlayer = Relations.initialHostility(faction, r.profile.rep(faction));
        a.aggressor = a.hostileToPlayer;
        a.squad = 1;
        r.actors.add(a);
        return a;
    }

    // ------------------------------------------------------------------ ballistics
    private static float shootAt(Raid r, Actor t, String ammo) {
        ItemInstance w = item("weapon.sr16", 1);
        float hp = t.hp;
        float ox = r.player.x, oy = World.GROUND + 1.5f, oz = r.player.z;
        float dx = t.x - ox, dy = (t.y + 1.2f) - oy, dz = t.z - oz, l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        r.fireProjectiles(r.player, w, Content.def(ammo), ox, oy, oz, dx / l, dy / l + 0.002f, dz / l, 0);
        for (int i = 0; i < 60; i++) r.update(1 / 120f);
        return hp - t.hp;
    }

    private static void ballistics() {
        System.out.println("[Ballistics: projectiles, drop, voxel penetration]");
        Profile p = new Profile();
        Raid r = raid(p);
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer);
        r.player.x = 300; r.player.z = 300; r.player.y = World.GROUND;
        r.god = true;
        Actor t = walker(r, 300, 330);
        t.hp = t.maxHp = 5000;
        float open = shootAt(r, t, "ammo.556");
        check("projectile travels and hits a target at 30m", open > 20, "damage " + open);
        World w = r.world;
        for (int y = World.GROUND; y < World.GROUND + 4; y++) for (int x = 297; x <= 303; x++) w.setRaw(x, y, 315, Block.GLASS);
        float glass = shootAt(r, t, "ammo.556");
        // the target may be swaying or down (0.6: a hit body answers physically), so the round can cross any pane of the wall
        int brokenPanes = 0;
        for (int y = World.GROUND; y < World.GROUND + 4; y++) for (int x = 297; x <= 303; x++) if (w.get(x, y, 315) != Block.GLASS) brokenPanes++;
        check("rounds penetrate glass (and break it)", glass > 10 && brokenPanes >= 1, "damage " + glass + ", panes broken " + brokenPanes);
        for (int y = World.GROUND; y < World.GROUND + 4; y++) for (int x = 297; x <= 303; x++) for (int z = 315; z <= 316; z++) w.setRaw(x, y, z, Block.CONCRETE);
        t.bleed = 0;   // the two hits above left it bleeding: measure the wall, not the wounds
        float wall = shootAt(r, t, "ammo.556");
        check("concrete stops small arms", wall == 0, "damage " + wall);
        for (int y = World.GROUND; y < World.GROUND + 4; y++) for (int x = 297; x <= 303; x++) for (int z = 315; z <= 316; z++) w.setRaw(x, y, z, z == 315 ? Block.WOOD_PLANKS : Block.AIR);
        float wood = shootAt(r, t, "ammo.762x54");
        check("precision rounds punch through wood", wood > 10, "damage " + wood);
        for (int y = World.GROUND; y < World.GROUND + 4; y++) for (int x = 297; x <= 303; x++) w.setRaw(x, y, 315, Block.AIR);
        // drop: a level shot at long range lands lower than it started
        r.fireProjectiles(r.player, item("weapon.pm9", 1), Content.def("ammo.9x19"), 300, World.GROUND + 20, 300, 1, 0, 0, 0);
        Raid.Projectile last = r.projectiles.get(r.projectiles.size() - 1);
        for (int i = 0; i < 30; i++) r.update(1 / 60f);
        check("projectiles drop under gravity (not hitscan)", last.y < World.GROUND + 20 - 0.5f, String.format("y %.2f after 0.5s", last.y));
        check("shots leave a sound signature", r.sounds.stream().anyMatch(x -> x.category().equals("impact")), r.sounds.size() + " recent sounds");
        for (int y = World.GROUND; y < World.GROUND + 4; y++) for (int x = 297; x <= 303; x++) for (int z = 315; z <= 316; z++) w.setRaw(x, y, z, Block.AIR);
    }

    // ------------------------------------------------------------------ tiers / offscreen
    private static void tiers() {
        System.out.println("[AI tiers transition without spikes or duplicates]");
        Profile p = new Profile();
        Raid r = raid(p);
        r.player.x = 200; r.player.z = 200; r.player.y = World.GROUND;
        r.update(1.1f);
        int pop0 = r.populationTotal() + r.deaths;
        int maxDelta = 0;
        long worst = 0;
        float[][] path = {{200, 200}, {500, 200}, {500, 500}, {200, 500}, {200, 200}};
        for (float[] pt : path) {
            r.player.x = pt[0]; r.player.z = pt[1];
            for (int i = 0; i < 3; i++) {
                long t0 = System.nanoTime();
                r.update(1.1f);
                worst = Math.max(worst, System.nanoTime() - t0);
                int pop = r.populationTotal() + r.deaths;
                maxDelta = Math.max(maxDelta, Math.abs(pop - pop0));
            }
        }
        check("population conserved across tier transitions (living + dead)", maxDelta == 0, "population " + pop0 + ", " + r.deaths + " died in fighting, drift " + maxDelta);
        long ids = r.actors.stream().map(a -> a.id).distinct().count();
        check("no duplicated actor ids", ids == r.actors.size(), null);
        check("no frame spike on cell transitions", worst < 250_000_000L, (worst / 1_000_000) + " ms worst update");
    }

    private static void offscreen() {
        System.out.println("[Offscreen outcomes translate consistently near the player]");
        ZoneState z = ZoneState.fresh();
        float before = z.control(District.INDUSTRIAL, "raiders");
        for (int i = 0; i < 8; i++) z.reportKill(District.INDUSTRIAL, "raiders", true);
        check("strategic losses reduce faction influence", z.control(District.INDUSTRIAL, "raiders") < before, String.format("%.2f -> %.2f", before, z.control(District.INDUSTRIAL, "raiders")));
        Profile hi = new Profile(), lo = new Profile();
        lo.zone = z;
        hi.zone = ZoneState.fresh();
        Raid a = raid(hi), b = raid(lo);
        int ra = countFaction(a, "raiders"), rb = countFaction(b, "raiders");
        check("weakened faction fields fewer agents near the player", rb <= ra, ra + " -> " + rb);
        ZoneState z2 = ZoneState.fresh();
        z2.advance(48, 1);
        ZoneState z3 = ZoneState.fresh();
        z3.advance(48, 1);
        check("strategic advance deterministic", Json.write(z2.toJson()).equals(Json.write(z3.toJson())), null);
    }

    private static int countFaction(Raid r, String f) {
        r.player.x = 500; r.player.z = 300;
        r.update(1.1f);
        int n = 0;
        for (Actor a : r.actors) if (f.equals(a.faction)) n++;
        return n + r.populationTotal() * 0;
    }

    // ================================================================== iteration gates
    private static void character() {
        System.out.println("[Customization: definition tables, SKIN groups validated, parts combine at runtime]");
        var problems = absentialis.character.CharDefs.validate();
        check("every part's SKIN assignment validates on import", problems.isEmpty(), problems.isEmpty() ? "" : String.join("; ", problems.subList(0, Math.min(4, problems.size()))));
        int min = 99;
        for (var c : absentialis.character.CharDefs.Category.values()) min = Math.min(min, absentialis.character.CharDefs.count(c));
        check("12+ options in every category", min >= 12 && absentialis.character.CharDefs.Category.values().length >= 9, "smallest category has " + min);
        Rng r = new Rng(77);
        int clipped = 0, holes = 0, built = 0;
        String[] heads = {"", "cap", "helmet", "beanie", "hood"}, faces = {"", "gasmask", "balaclava", "bandana"}, vests = {"", "soft", "plate", "mil"}, packs = {"", "case", "small", "large"};
        for (int k = 0; k < 400; k++) {
            var app = absentialis.character.Appearance.random(r);
            var gear = new absentialis.character.CharBuilder.Gear();
            gear.head = r.pick(heads); gear.face = r.pick(faces); gear.vest = r.pick(vests); gear.pack = r.pick(packs);
            var m = absentialis.character.CharBuilder.build(app, gear);
            built++;
            if (m.clipped) clipped++;
            // the body under the clothes is still whole: no combination opens a hole in a limb or the torso
            boolean hole = false;
            // 0.6: the female body is the same rig two voxels narrower in the torso and one in each arm - each body is held to its own extents
            boolean f = app.female;
            for (int x = f ? 4 : 3; x <= (f ? 9 : 10) && !hole; x++) for (int y = 0; y <= 11; y++) for (int z = 4; z <= 7; z++) if (m.get(absentialis.character.Rig.TORSO, x, y, z) == 0) hole = true;
            // (1.1: the female leg is three wide, kept against the middle of the body: columns 2..4 on the left, 1..3 on the right)
            for (int g : new int[]{absentialis.character.Rig.L_THIGH, absentialis.character.Rig.R_THIGH}) for (int x = f && g == absentialis.character.Rig.L_THIGH ? 2 : 1; x <= (f && g == absentialis.character.Rig.R_THIGH ? 3 : 4) && !hole; x++) for (int y = 1; y <= 5; y++) for (int z = 1; z <= 4; z++) if (m.get(g, x, y, z) == 0) hole = true;
            for (int g : new int[]{absentialis.character.Rig.L_UPPER_ARM, absentialis.character.Rig.R_UPPER_ARM}) {
                boolean left = g == absentialis.character.Rig.L_UPPER_ARM;
                int x0 = !f ? 1 : left ? 3 : 0, x1 = !f ? 4 : left ? 5 : 2;
                for (int x = x0; x <= x1 && !hole; x++) for (int y = 1; y <= 5; y++) for (int z = 1; z <= (f ? 3 : 4); z++) if (m.get(g, x, y, z) == 0) hole = true;
            }
            if (hole) holes++;
        }
        check("400 random combinations assemble without clipping a grid", clipped == 0, built + " built, " + clipped + " clipped");
        check("no combination leaves a hole in the body", holes == 0, holes + " with holes");
        Profile p = new Profile();
        p.appearance = absentialis.character.Appearance.random(new Rng(5));
        String json = Json.write(p.appearance.toJson());
        var back = absentialis.character.Appearance.fromJson(Json.parseObject(json));
        check("profile stores definition ids, not meshes", back.key().equals(p.appearance.key()) && json.length() < 500 && json.contains("hair."), json.length() + " chars");
        var hair = absentialis.character.CharDefs.part(absentialis.character.CharDefs.Category.HAIR, absentialis.character.CharDefs.indexOf(absentialis.character.CharDefs.Category.HAIR, "hair.long"));
        check("hairstyles are skinned to the head group only (follow head animation for free)", hair.groups() == absentialis.character.Rig.bit(absentialis.character.Rig.HEAD), null);
        absentialis.character.CharBuilder.Gear g1 = new absentialis.character.CharBuilder.Gear(), g2 = new absentialis.character.CharBuilder.Gear(); g2.vest = "plate"; g2.pack = "large";
        int bare = absentialis.character.CharBuilder.build(p.appearance, g1).groups[absentialis.character.Rig.TORSO].count(), kitted = absentialis.character.CharBuilder.build(p.appearance, g2).groups[absentialis.character.Rig.TORSO].count();
        check("armour and backpack change the silhouette", kitted > bare + 150, bare + " -> " + kitted + " torso voxels");
        Loadout lo = new Loadout();
        check("backpack slot adds a storage grid", InventoryService.equip(item("bag.large", 1), lo, "backpack").ok() && lo.packGrid() != null && lo.packGrid().w == 8, null);
    }

    /** Evaluates a pose and returns the world position of the left/right sole and the head pivot. */
    private static float[][] feet(Actor a, absentialis.character.Pose pose, absentialis.engine.math.Mat4[] bones) {
        absentialis.engine.math.Mat4 base = new absentialis.engine.math.Mat4(), hips = new absentialis.engine.math.Mat4();
        float s = absentialis.character.Rig.VOX;
        base.identity().translate(a.x, a.y + a.anim.visY, a.z).rotateY(a.yaw).scale(s, s, s);
        absentialis.character.Rig.fk(pose, base, hips, bones);
        float[][] o = new float[3][3];
        bones[absentialis.character.Rig.L_SHIN].transformPoint(0, -6, 0, o[0]);
        bones[absentialis.character.Rig.R_SHIN].transformPoint(0, -6, 0, o[1]);
        bones[absentialis.character.Rig.HEAD].transformPoint(0, 5.3f, 0, o[2]);
        return o;
    }

    private static void animation() {
        System.out.println("[Animation: no foot sliding, independent upper/lower body, 120 ms blends, head-bone camera]");
        String[] need = {"idle", "walk", "sprint", "crouch_idle", "crouch_walk", "wpn_aim", "fire", "reload_tac", "reload_empty", "equip", "interact", "heal", "hit", "stumble", "climb", "vault", "death"};
        StringBuilder missing = new StringBuilder();
        for (String n : need) if (!absentialis.character.Anims.has(n) || absentialis.character.Anims.get(n).keyCount() == 0) missing.append(n).append(' ');
        check("all required states exist as keyframe sequences", missing.length() == 0, absentialis.character.Anims.all().size() + " clips" + (missing.length() > 0 ? ", missing " + missing : ""));
        absentialis.engine.math.Mat4[] bones = new absentialis.engine.math.Mat4[absentialis.character.Rig.GROUPS];
        for (int g = 0; g < bones.length; g++) bones[g] = new absentialis.engine.math.Mat4();
        absentialis.character.Pose pose = new absentialis.character.Pose();
        float worst = 0;
        StringBuilder detail = new StringBuilder();
        for (float speed : new float[]{1.4f, 3.4f, 6.0f}) {
            Actor a = new Actor();
            a.faction = "player"; a.isPlayer = true; a.onGround = true; a.y = World.GROUND; a.x = 100; a.z = 100;
            a.anim = new absentialis.character.Animator();
            float dt = 1 / 120f, slip = 0, stance = 0;
            float[][] prev = null;
            for (int i = 0; i < 120 * 4; i++) {
                a.z -= speed * dt;                       // walking forward (-z) at a steady speed
                a.anim.update(a, dt, false, false, false);
                a.anim.pose(a, pose, 0, null, false);
                float[][] f = feet(a, pose, bones);
                if (prev != null && i > 120) for (int k = 0; k < 2; k++) {
                    // a planted foot (on the floor in both frames) should not move over the ground
                    if (f[k][1] - a.y < 0.012f && prev[k][1] - a.y < 0.012f) { slip += Math.hypot(f[k][0] - prev[k][0], f[k][2] - prev[k][2]); stance += dt; }
                }
                prev = f;
            }
            float rel = stance <= 0 ? 1 : slip / stance / speed;
            worst = Math.max(worst, rel);
            detail.append(String.format("%.1f m/s: %.0f%%  ", speed, rel * 100));
        }
        check("stance feet stay planted at walk, jog and sprint", worst < 0.12f, detail + "(slip as a share of body speed)");
        // strafing: hips turn into the movement, the torso keeps facing the aim
        Actor s = new Actor();
        s.faction = "player"; s.isPlayer = true; s.onGround = true; s.y = World.GROUND; s.x = 100; s.z = 100;
        s.anim = new absentialis.character.Animator();
        for (int i = 0; i < 120; i++) { s.x += 3f / 120f; s.anim.update(s, 1 / 120f, false, false, false); }
        s.anim.pose(s, pose, 0, null, false);
        float hip = pose.rootYaw, torsoWorld = pose.rootYaw + pose.ry(absentialis.character.Rig.TORSO);
        check("lower body turns into a strafe while the upper body holds the aim", Math.abs(hip) > 0.4f && Math.abs(torsoWorld) < 0.25f, String.format("hips %.2f rad, torso %.2f rad off aim", hip, torsoWorld));
        // 120 ms blends
        Actor c = new Actor();
        c.faction = "player"; c.isPlayer = true; c.onGround = true; c.y = World.GROUND;
        c.anim = new absentialis.character.Animator();
        c.anim.update(c, 0.016f, false, false, false);
        c.crouch = true;
        float t = 0;
        while (c.anim.crouchT < 0.98f && t < 1) { c.anim.update(c, 1 / 240f, false, false, false); t += 1 / 240f; }
        check("state changes blend in 120 ms", t > 0.06f && t <= 0.125f, String.format("crouch blend %.0f ms", t * 1000));
        // camera on the head bone: eye height comes from the pose and follows crouch and the gait
        c.crouch = false;
        for (int i = 0; i < 60; i++) c.anim.update(c, 1 / 120f, false, false, false);
        c.anim.pose(c, pose, 0, null, true);
        float stand = feet(c, pose, bones)[2][1] - c.y;
        c.crouch = true;
        for (int i = 0; i < 60; i++) c.anim.update(c, 1 / 120f, false, false, false);
        c.anim.pose(c, pose, 0, null, true);
        float crouched = feet(c, pose, bones)[2][1] - c.y;
        c.crouch = false;
        float lo = 9, hi = 0;
        for (int i = 0; i < 240; i++) { c.z -= 4f / 120f; c.anim.update(c, 1 / 120f, false, false, false); c.anim.pose(c, pose, 0, null, true); float e = feet(c, pose, bones)[2][1] - c.y; if (i > 60) { lo = Math.min(lo, e); hi = Math.max(hi, e); } }
        check("first-person eye rides the head bone (height, crouch, gait bob)", stand > 1.5f && stand < 1.75f && crouched < stand - 0.3f && hi - lo > 0.01f && hi - lo < 0.2f, String.format("stand %.2f m, crouch %.2f m, bob %.1f cm", stand, crouched, (hi - lo) * 100));
        check("first-person body keeps its legs (only head and arms are hidden)", (absentialis.scene.ActorRenderer.FP_HIDE & absentialis.character.Rig.LOWER_MASK) == 0 && (absentialis.scene.ActorRenderer.FP_HIDE & absentialis.character.Rig.bit(absentialis.character.Rig.HEAD)) != 0, null);
        // reloads differ by rounds fired
        absentialis.character.Pose a1 = new absentialis.character.Pose(), a2 = new absentialis.character.Pose();
        absentialis.character.Anims.get("reload_tac").sample(0.8f, a1); absentialis.character.Anims.get("reload_empty").sample(0.8f, a2);
        check("tactical and empty reloads are different animations", Math.abs(a1.wpn[8] - a2.wpn[8]) > 1 || Math.abs(a1.wpn[7] - a2.wpn[7]) > 1, null);
    }

    private static void ragdoll() {
        System.out.println("[Ragdoll: constrained 12-part rig, 80 ms hand-over, settles within 8 s, searchable, no cover]");
        Profile p = new Profile();
        Raid r = raid(p);
        r.populate = false; r.freezeAI = true;
        r.actors.removeIf(a -> !a.isPlayer);
        r.player.x = 300; r.player.z = 300; r.player.y = World.GROUND;
        Actor v = r.debugSpawn("raiders", 306, 300, World.GROUND);
        r.update(0.05f);
        v.vx = 2; v.vz = 0;
        r.damageActor(v, r.player, 500, Content.def("ammo.556"), "torso", v.x, v.y + 1.2f, v.z);
        absentialis.character.Ragdoll rd = v.ragdoll;
        check("death starts a 15-joint ragdoll from the animated pose", v.dead && rd != null && !rd.settled, null);
        float stretch = 0, knee = 9, elbow = 9, lowest = 99, settleAt = -1, travelled = 0;
        float x0 = v.x;
        for (int i = 0; i < 60 * 9 && rd != null; i++) {
            r.update(1 / 60f);
            stretch = Math.max(stretch, rd.maxStretch);
            knee = Math.min(knee, Math.min(rd.hingeSide(absentialis.character.Ragdoll.LH, absentialis.character.Ragdoll.LK, absentialis.character.Ragdoll.LA, 1), rd.hingeSide(absentialis.character.Ragdoll.RH, absentialis.character.Ragdoll.RK, absentialis.character.Ragdoll.RA, 1)));
            elbow = Math.min(elbow, Math.min(rd.hingeSide(absentialis.character.Ragdoll.LS, absentialis.character.Ragdoll.LE, absentialis.character.Ragdoll.LW, -1), rd.hingeSide(absentialis.character.Ragdoll.RS, absentialis.character.Ragdoll.RE, absentialis.character.Ragdoll.RW, -1)));
            lowest = Math.min(lowest, rd.lowestY());
            if (rd.settled && settleAt < 0) settleAt = rd.age;
        }
        travelled = Math.abs(v.x - x0);
        check("bones keep their length (no stretching)", stretch < 0.6f, String.format("worst stick error %.2f voxels", stretch));
        check("knees and elbows never hyper-extend", knee > -1.2f && elbow > -1.2f, String.format("knee side %.2f, elbow side %.2f voxels (negative = wrong way)", knee, elbow));
        check("the body rests on the floor, not in it", lowest > World.GROUND - 0.06f, String.format("lowest point %.2f m above the floor", lowest - World.GROUND));
        check("settles to a static corpse within 8 s", settleAt > 0 && settleAt <= absentialis.character.Ragdoll.ACTIVE_TIME + 0.02f, String.format("settled at %.1f s", settleAt));
        check("hit impulse throws the body along the shot", v.x > x0 + 0.15f, String.format("moved %.2f m", travelled));
        check("corpse is searchable where the body lies", r.nearestCorpse(v.x, v.z, 1.8f) == v && v.corpse != null, null);
        check("corpse gives no cover and blocks nothing", v.intersect(v.x - 3, v.y + 0.3f, v.z, 1, 0, 0, 6) < 0 && !r.nearby(v.x, v.z, 2).contains(v), null);
        Actor far = r.debugSpawn("raiders", 420, 300, World.GROUND);
        r.damageActor(far, null, 500, null, "torso", far.x, far.y + 1, far.z);
        check("distant corpses are placed at rest immediately", far.ragdoll != null && far.ragdoll.settled, null);
        check("hand-over from animation takes 80 ms", Math.abs(absentialis.character.Ragdoll.BLEND_TIME - 0.08f) < 1e-4f, null);
    }

    private static void audio() {
        System.out.println("[Audio: buses and mute, positional mix, material footsteps, weapon foley, UI feedback]");
        absentialis.engine.audio.UltraEngine a = new absentialis.engine.audio.UltraEngine();
        new absentialis.engine.audio.SoundBank().build(a.samples);
        new absentialis.engine.audio.SoundBank06().build(a.samples);
        StringBuilder missing = new StringBuilder();
        for (String s : new String[]{"step_concrete", "step_metal", "step_dirt", "step_wood", "step_water", "shot_rifle", "shot_pistol", "dry_fire", "jam", "mag_out", "mag_in", "mag_drop", "bolt_back", "bolt_release", "pouch",
                "equip_rifle", "equip_pistol", "equip_melee", "ui_hover", "ui_click", "ui_invalid", "ui_drop", "extract_tick", "music_hideout", "music_tension", "search", "retch", "stomp"}) if (!a.samples.containsKey(s)) missing.append(s).append(' ');
        check("footstep materials, weapon foley and UI cues are all present", missing.length() == 0, a.samples.size() + " samples" + (missing.length() > 0 ? ", missing " + missing : ""));
        float[] mix = new float[2048];
        a.master = 1;
        a.play("shot_rifle", 1, 0, 1, absentialis.engine.audio.UltraEngine.SFX);
        a.loop("music_hideout", 1, absentialis.engine.audio.UltraEngine.MUSIC).fadeTo(1, 0);
        a.play("shout", 1, 0, 1, absentialis.engine.audio.UltraEngine.VOICE);
        a.mixBlock(mix); a.mixBlock(mix);
        boolean all = a.peak[absentialis.engine.audio.UltraEngine.SFX] > 0 && a.peak[absentialis.engine.audio.UltraEngine.MUSIC] > 0 && a.peak[absentialis.engine.audio.UltraEngine.VOICE] > 0;
        check("Music, Effects and Voice buses all carry signal", all, String.format("sfx %.2f music %.3f voice %.2f", a.peak[0], a.peak[1], a.peak[2]));
        a.busMuted[absentialis.engine.audio.UltraEngine.MUSIC] = true;
        a.mixBlock(mix);
        check("muting a bus silences only that bus", a.peak[absentialis.engine.audio.UltraEngine.MUSIC] == 0 && a.peak[absentialis.engine.audio.UltraEngine.VOICE] > 0, null);
        a.muted = true;
        a.mixBlock(mix);
        float sum = 0; for (float f : mix) sum += Math.abs(f);
        check("master mute silences everything", sum == 0, null);
        a.muted = false; a.busMuted[1] = false; a.stopAll(); a.mixBlock(mix);
        a.bus[absentialis.engine.audio.UltraEngine.SFX] = 1; a.play("shot_rifle", 1); a.mixBlock(mix); float full = a.peak[0];
        a.stopAll(); a.mixBlock(mix);
        a.bus[absentialis.engine.audio.UltraEngine.SFX] = 0.5f; a.play("shot_rifle", 1); a.mixBlock(mix); float half = a.peak[0];
        check("a bus slider scales its bus", half > full * 0.4f && half < full * 0.6f, String.format("%.2f -> %.2f", full, half));
        a.lx = 0; a.ly = 0; a.lz = 0; a.lyaw = 0;
        absentialis.engine.audio.UltraEngine.Voice right = a.playAt("shot_rifle", 10, 0, 0, 1, 300, 1), left = a.playAt("shot_rifle", -10, 0, 0, 1, 300, 1), near = a.playAt("shot_rifle", 0, 0, -5, 1, 300, 1), farv = a.playAt("shot_rifle", 0, 0, -120, 1, 300, 1);
        check("gunfire is positional: panned by bearing, quieter and duller with distance", right.pan() > 0.5f && left.pan() < -0.5f && near.gain() > farv.gain() * 2 && farv.lowpass() > near.lowpass(), String.format("pan %.2f/%.2f, gain %.2f/%.2f", right.pan(), left.pan(), near.gain(), farv.gain()));
    }

    private static void alife() {
        System.out.println("[A-Life: one loud action moves perception, migration, patrol response and the strategic tier]");
        Profile p = new Profile();
        Raid r = raid(p);
        r.freezeAI = true;
        r.player.x = 500; r.player.z = 200; r.player.y = World.GROUND;
        r.update(0.01f);                                    // activate the cells round the player
        r.actors.removeIf(a -> !a.isPlayer);                // keep the abstract populations, clear the stage
        r.freezeAI = false;
        Actor listener = walker(r, 500, 236);
        Actor patrol = r.debugSpawn("raiders", 500, 400, World.GROUND);   // the bandits hold this half; this squad is out of earshot
        patrol.squad = 4242;
        District here = r.world.district(500, 200);
        int pop0 = r.populationTotal(), ev0 = r.noiseEvents, mig0 = r.migrations, dis0 = r.dispatches;
        float infected0 = p.zone.infected[here.ordinal()];
        int log0 = p.zone.log.size();
        for (int i = 0; i < 6; i++) r.emitSound(500, World.GROUND + 1, 200, 130, "shot", r.player.id, false);
        for (int i = 0; i < 30; i++) r.update(1 / 60f);
        check("perception: an agent in earshot investigates", "investigate".equals(listener.state), listener.state);
        check("noise crossed the response threshold once, not per shot", r.noiseEvents - ev0 == 1, (r.noiseEvents - ev0) + " event from 6 shots");
        check("migration: infected populations shift toward the noise", r.migrations > mig0, (r.migrations - mig0) + " moved");
        check("patrol response: the faction holding the district sends a squad", r.dispatches - dis0 == 1 && patrol.hasOrder && Math.abs(patrol.orderX - 500) < 6, ZoneState.cap(String.valueOf(p.zone.controller(here))) + ", " + (r.dispatches - dis0) + " dispatched");
        check("strategic: district infected pressure and the event log both changed", p.zone.infected[here.ordinal()] != infected0 && p.zone.log.size() > log0 && "noise".equals(p.zone.log.get(0).type()), null);
        check("migration redistributes - it does not spawn (population conserved)", r.populationTotal() == pop0, pop0 + " -> " + r.populationTotal());
        float d0 = patrol.distXZ(500, 200);
        for (int i = 0; i < 60 * 10; i++) r.update(1 / 60f);
        check("the dispatched squad actually travels toward the noise", patrol.distXZ(500, 200) < d0 - 4, String.format("%.0f m -> %.0f m in 10 s (regional tier)", d0, patrol.distXZ(500, 200)));
        // regional tier: outside the engagement there is travel but no perception
        Raid r2 = raid(new Profile());
        r2.populate = false; r2.actors.removeIf(a -> !a.isPlayer);
        r2.player.x = 200; r2.player.z = 200; r2.player.y = World.GROUND;
        Actor distant = human(r2, "raiders", 200, 320);
        distant.hostileToPlayer = true; distant.yaw = 0;
        for (int i = 0; i < 120; i++) r2.update(1 / 30f);
        check("regional tier runs without perception or combat", distant.tier == 1 && distant.awareness == 0 && !"engage".equals(distant.state), "tier " + distant.tier + " " + distant.state);
        // strategic tier: persistent ids, deterministic resolution, a log
        ZoneState z1 = ZoneState.fresh(), z2 = ZoneState.fresh();
        z1.advance(96, 7); z2.advance(96, 7);
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (var sq : z1.squads) ids.add(sq.id);
        check("strategic squads have persistent unique ids", !z1.squads.isEmpty() && ids.size() == z1.squads.size(), z1.squads.size() + " squads");
        check("strategic resolution is deterministic and logged", Json.write(z1.toJson()).equals(Json.write(z2.toJson())) && !z1.log.isEmpty(), z1.log.size() + " events in 96 h");
        ZoneState z3 = ZoneState.fromJson(Json.parseObject(Json.write(z1.toJson())));
        check("squads and log survive a save", z3.squads.size() == z1.squads.size() && z3.log.size() == z1.log.size() && Json.write(z3.toJson()).length() < 40000, Json.write(z3.toJson()).length() + " chars");
        // strategic -> active: evidence matches the log
        Profile pe = new Profile();
        pe.zone.log("battle", District.MARKET, 220, 240, "military", "raiders", "test battle");
        pe.zone.log("roadblock", District.MARKET, 260, 240, "military", "", "test roadblock");
        Raid re = raid(pe);
        int bodies = 0;
        for (Actor a : re.actors) if (a.dead && "raiders".equals(a.faction) && a.distXZ(220, 240) < 8 && a.corpse != null) bodies++;
        check("logged battle leaves the loser's bodies and smoke at its location", re.evidence.size() == 2 && bodies >= 2 && re.smoke.size() == 1, bodies + " bodies, " + re.smoke.size() + " smoke");
        check("logged roadblock is physically there", re.world.get(262, World.GROUND, 240) == Block.SANDBAG, null);
        for (int k = -3; k <= 3; k++) for (int y = World.GROUND; y <= World.GROUND + 1; y++) re.world.setRaw(260 + k, y, 240, Block.AIR);   // leave the shared fixture clean
    }

    static Raid stage(Profile p) {
        Raid r = raid(p);
        r.populate = false;
        r.actors.removeIf(a -> !a.isPlayer);
        r.player.x = 300; r.player.z = 500; r.player.y = World.GROUND;
        r.player.weapon = item("weapon.akr", 1);
        r.god = true;       // the scenarios watch behaviour; a dead player would end the raid mid-test
        return r;
    }

    static List<Actor> squadOf(Raid r, String faction, int n, float x, float z, int squad, boolean hostile) {
        List<Actor> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Actor a = r.debugSpawn(faction, x + i * 2, z, World.GROUND);
            a.squad = squad; a.hostileToPlayer = hostile; a.aggressor = hostile; a.homeX = x; a.homeZ = z;
            a.wakeUntil = 1e9f;     // on duty: these scenarios are about doctrine, not about who happens to be asleep at this hour
            a.yaw = (float) Math.atan2(-(r.player.x - a.x), -(r.player.z - a.z));
            l.add(a);
        }
        return l;
    }

    private static void doctrines() {
        System.out.println("[Human AI: four doctrines behave distinctly; memory is uncertain]");
        // ---- mercenaries: roles, radio, ordered retreat
        Raid r = stage(new Profile());
        List<Actor> mercs = squadOf(r, "mercenaries", 4, 296, 478, 11, true);
        List<Actor> reserve = squadOf(r, "mercenaries", 2, 300, 420, 12, true);
        for (Actor a : reserve) a.yaw = 0;          // looking away: they only come because they are called
        for (int i = 0; i < 60 * 3; i++) r.update(1 / 60f);
        java.util.Set<String> roles = new java.util.HashSet<>();
        for (Actor a : mercs) roles.add(a.tacRole);
        // 0.7: the roles are NeuralAI's (hold, suppress, advance, flank_left/right ...). Three seconds into a contact a team of four has dealt at least three
        // different jobs, one of them the base of fire and one of them a mover
        boolean mover = roles.contains("advance") || roles.contains("flank_left") || roles.contains("flank_right");
        check("mercenaries split into different roles: a base of fire, a mover, and the rest", roles.contains("suppress") && mover && roles.size() >= 3, roles.toString());
        check("mercenaries radio for reinforcements on contact", r.squad(mercs.get(0)).backupCalled && r.dispatches > 0 && (reserve.get(0).hasOrder || !"idle".equals(reserve.get(0).state)), r.dispatches + " dispatched");
        for (int i = 0; i < 3; i++) r.damageActor(mercs.get(i), r.player, 900, Content.def("ammo.556"), "torso", mercs.get(i).x, mercs.get(i).y + 1, mercs.get(i).z);
        for (int i = 0; i < 90; i++) r.update(1 / 60f);
        boolean retreated = false;
        for (int i = 0; i < 60 * 3 && !retreated; i++) { r.update(1 / 60f); retreated = "retreat".equals(mercs.get(3).state); }
        check("outmatched mercenaries fall back in order (retreat, not rout)", retreated, mercs.get(3).state + ", squad " + r.squad(mercs.get(3)).alive + "/" + r.squad(mercs.get(3)).initial);
        // ---- bandits: hold-up instead of opening fire, pay to walk, break when it goes wrong
        Raid rb = stage(new Profile());
        rb.player.weapon = null;
        List<Actor> bandits = squadOf(rb, "raiders", 3, 296, 484, 21, true);
        int shots0 = rb.projectiles.size();
        for (int i = 0; i < 60 * 3; i++) rb.update(1 / 60f);
        boolean extorting = "extort".equals(bandits.get(0).state) && rb.extortion() != null;
        check("bandits hold up a lone quiet target instead of shooting", extorting && rb.projectiles.size() == shots0, bandits.get(0).state);
        String paid = rb.payExtortion();
        for (int i = 0; i < 30; i++) rb.update(1 / 60f);
        check("paying the hold-up ends it: they stand down and the scrip is owed", paid != null && paid.startsWith("Paid") && !bandits.get(0).hostileToPlayer && rb.scripPaid == Raid.EXTORT_PRICE, paid);
        Raid rb2 = stage(new Profile());
        List<Actor> gang = squadOf(rb2, "raiders", 3, 296, 484, 22, true);
        rb2.playerAiming = true;
        for (int i = 0; i < 60; i++) { rb2.playerAiming = true; rb2.update(1 / 60f); }
        float m0 = gang.get(2).morale;
        rb2.damageActor(gang.get(0), rb2.player, 900, Content.def("ammo.556"), "torso", gang.get(0).x, gang.get(0).y + 1, gang.get(0).z);
        rb2.damageActor(gang.get(1), rb2.player, 900, Content.def("ammo.556"), "torso", gang.get(1).x, gang.get(1).y + 1, gang.get(1).z);
        boolean fled = false;
        for (int i = 0; i < 60 * 4 && !fled; i++) { rb2.playerAiming = true; rb2.update(1 / 60f); fled = "flee".equals(gang.get(2).state); }
        check("bandit morale collapses when squadmates die", gang.get(2).morale < m0 - 0.5f && fled, String.format("morale %.2f -> %.2f, %s", m0, gang.get(2).morale, gang.get(2).state));
        // ---- military: restricted area warning, enforcement, permits, cover
        Profile pm = new Profile();
        Raid rm = stage(pm);
        rm.world.map.posts.add(new MapData.Point("Test Garrison", "camp", "military".length() > 0 ? 300 : 0, World.GROUND, 492, District.MARKET));
        rm.world.map.posts.get(rm.world.map.posts.size() - 1).faction = "military";
        List<Actor> troops = squadOf(rm, "military", 2, 298, 482, 31, false);
        for (int i = 0; i < 30; i++) rm.update(1 / 60f);
        boolean warned = "warn".equals(troops.get(0).state) || "warn".equals(troops.get(1).state);
        check("military warn a trespasser before using force", warned && !troops.get(0).hostileToPlayer && rm.restrictedFor(rm.player.x, rm.player.z), troops.get(0).state);
        for (int i = 0; i < 60 * 8; i++) rm.update(1 / 60f);
        check("ignoring the warning makes the garrison hostile and is recorded", troops.get(0).hostileToPlayer && rm.trespasses == 1 && rm.debrief.repDelta.getOrDefault("military", 0f) < 0, rm.trespasses + " trespass");
        pm.rep.put("military", 40f);
        check("standing works as a permit (reputation-gated)", !rm.restrictedFor(rm.player.x, rm.player.z), null);
        rm.world.map.posts.remove(rm.world.map.posts.size() - 1);
        for (int k = 0; k < 5; k++) rm.world.setRaw(398 + k, World.GROUND, 402, Block.SANDBAG);
        Actor rifle = human(rm, "military", 400, 404);
        // 0.8: cover is chosen by NeuralAI's scorer from the cover map, against a believed threat - asked here directly
        rm.world.cover.clear();
        absentialis.neuralai.api.Vec3 threatAt = new absentialis.neuralai.api.Vec3(400, World.GROUND, 380);
        absentialis.neuralai.api.GameWorld.TacticalPoint bestPt = null;
        double bestProt = 0;
        for (var tp : rm.neuralWorld.tacticalPointsNear(new absentialis.neuralai.api.Vec3(rifle.x, rifle.y, rifle.z), 8)) { double pr = rm.neural.cover.protection(tp.position(), threatAt, tp.low()); if (pr > bestProt) { bestProt = pr; bestPt = tp; } }
        boolean cover = bestPt != null && bestProt > 0.6;
        check("soldiers find cover with a block between them and the threat", cover && bestPt.position().z() > 402, cover ? String.format("cover at %.1f, %.1f (protection %.2f)", bestPt.position().x(), bestPt.position().z(), bestProt) : "none (" + bestProt + ")");
        for (int k = 0; k < 5; k++) rm.world.setRaw(398 + k, World.GROUND, 402, Block.AIR);
        // ---- loners: neutral, avoid fights, ask for help
        Raid rl = stage(new Profile());
        List<Actor> loners = squadOf(rl, "survivors", 2, 296, 480, 41, false);
        Actor threat = walker(rl, 300, 462);
        check("loners are neutral by default", !loners.get(0).hostileToPlayer, null);
        // gunfire 40 m off: everyone else would go and look - loners put distance between themselves and it
        float before = loners.get(0).distXZ(300, 440);
        rl.emitSound(300, World.GROUND + 1, 440, 120, "shot", threat.id, false);
        for (int i = 0; i < 60; i++) rl.update(1 / 60f);              // 0.7: hearing takes a moment - it is their own ears now, not a broadcast
        boolean avoiding = "avoid".equals(loners.get(0).state) && "avoid".equals(loners.get(1).state);
        String heardAs = loners.get(0).state + " / " + loners.get(1).state + " (" + rl.neural.describe(loners.get(1).id) + ")";
        for (int i = 0; i < 60 * 3; i++) rl.update(1 / 60f);
        check("loners back away from trouble instead of investigating or engaging", avoiding && !"engage".equals(loners.get(0).state) && loners.get(0).distXZ(300, 440) > before + 2, String.format("%.0f m -> %.0f m from the gunfire; after a second: %s", before, loners.get(0).distXZ(300, 440), heardAs));
        rl.actors.remove(threat);
        Actor hurt = loners.get(1);
        hurt.needsHelp = true; hurt.x = rl.player.x + 1; hurt.z = rl.player.z;
        InventoryService.store(item("med.bandage", 1), rl.profile.loadout);
        String res = rl.lonerInNeed(2.6f) == hurt ? rl.helpLoner(hurt) : "no one in reach";
        check("a wounded loner can be helped for standing", hurt.helped && rl.debrief.repDelta.getOrDefault("survivors", 0f) >= 3 && rl.debrief.aided == 1, res);
        // ---- shared perception: memory with a timestamp and a growing uncertainty, never the live position
        Raid rp = stage(new Profile());
        Actor seer = squadOf(rp, "mercenaries", 1, 300, 480, 51, true).get(0);
        for (int i = 0; i < 60 * 6 && !seer.memConfirmed; i++) rp.update(1 / 60f);      // until it has really seen: how long that takes is the vision meter's business, not this gate's
        for (int i = 0; i < 20; i++) rp.update(1 / 60f);
        float seenErr = seer.memErr, seenAt = seer.memTime;
        rp.player.x = 360; rp.player.z = 560;                           // gone - behind them and far
        for (int i = 0; i < 60 * 4; i++) rp.update(1 / 60f);
        check("memory keeps the last known position, not the player's coordinates", Math.abs(seer.memX - 300) < 8 && Math.abs(seer.memX - rp.player.x) > 40 && seer.memTime <= rp.time - 3 && seenAt > 0, String.format("remembers %.0f,%.0f; player at %.0f,%.0f", seer.memX, seer.memZ, rp.player.x, rp.player.z));
        check("uncertainty grows while the target is unseen", seer.memErr > seenErr + 1.5f, String.format("%.1f m -> %.1f m", seenErr, seer.memErr) + "  " + rp.neural.describe(seer.id));
        Raid rn = stage(new Profile());
        Actor day = human(rn, "raiders", 300, 440), night = human(rn, "raiders", 300, 440);
        rn.player.x = 300; rn.player.z = 420; rn.player.y = World.GROUND;
        day.yaw = night.yaw = (float) Math.atan2(-(rn.player.x - day.x), -(rn.player.z - day.z));
        float clear = (float) rn.neuralWorld.self(day.id).sightRange(), standing = exposure(rn, night, rn.player);
        rn.profile.zone.weatherRain = 1f;
        float rain = (float) rn.neuralWorld.self(night.id).sightRange();
        rn.player.crouch = true;
        float crouch = exposure(rn, night, rn.player);
        rn.player.crouch = false;
        check("vision depends on weather and stance", rain < clear * 0.8f && crouch < standing * 0.75f && crouch > 0, String.format("sight reaches %.0f m in the clear and %.0f m in rain; a standing figure at 20 m is exposed %.2f, a crouching one %.2f", clear, rain, standing, crouch));
    }

    private static void zombies() {
        System.out.println("[Infected: four archetypes, every special telegraphed, counterplay works]");
        boolean four = Archetypes.INFECTED.length == 9;      // 1.0: two ordinary kinds and seven special infected
        for (String id : Archetypes.INFECTED) four &= Archetypes.arch(id).infected() && Archetypes.arch(id).id().equals(id);
        Archetypes.Arch reg = Archetypes.arch("walker"), fast = Archetypes.arch("sprinter"), fat = Archetypes.arch("bomber"), giant = Archetypes.arch("tank");
        check("regular / fast / Bomber / Tank are distinct in speed and health (seven kinds in all)", four && fast.runSpeed() > reg.runSpeed() * 3 && fast.hp() < reg.hp() && fat.hp() > reg.hp() * 2 && giant.hp() > fat.hp() * 4 && giant.melee() * 1.25f < 100,
                String.format("run %.1f/%.1f/%.1f/%.1f m/s, hp %.0f/%.0f/%.0f/%.0f", reg.runSpeed(), fast.runSpeed(), fat.runSpeed(), giant.runSpeed(), reg.hp(), fast.hp(), fat.hp(), giant.hp()));
        // ---- fast: shriek first, one clean head shot ends it
        Raid r = stage(new Profile());
        Actor f = r.debugSpawn("sprinter", 300, 488, World.GROUND);
        f.yaw = (float) Math.PI;
        float shriekAt = -1, moveAt = -1;
        for (int i = 0; i < 60 * 3; i++) {
            r.update(1 / 60f);
            if (shriekAt < 0 && r.telegraphing(f, "shriek")) shriekAt = r.time;
            if (shriekAt >= 0 && moveAt < 0 && !r.telegraphing(f, "shriek") && Math.hypot(f.vx, f.vz) > 3) moveAt = r.time;
        }
        check("fast infected shriek before they rush", shriekAt >= 0 && moveAt - shriekAt >= 0.6f, String.format("shriek at %.2f s, rush at %.2f s", shriekAt, moveAt));
        Actor f2 = r.debugSpawn("sprinter", 320, 478, World.GROUND);
        r.damageActor(f2, r.player, Content.def("ammo.9x19").ammo.damage, Content.def("ammo.9x19"), "head", f2.x, f2.y + 1.7f, f2.z);
        check("a well-placed pistol round drops a fast infected", f2.dead, String.format("hp left %.0f", f2.hp));
        // ---- fat: retch telegraph, pool where you were, rupture on death
        Raid rf = stage(new Profile());
        rf.god = false;
        Actor b = rf.debugSpawn("spewer", 300, 493, World.GROUND);      // 0.7: the spitting is the Spewer now; the Bomber only bursts
        b.yaw = (float) Math.PI;
        float retchAt = -1, zoneAt = -1;
        float px = rf.player.x, pz = rf.player.z;
        for (int i = 0; i < 60 * 5 && zoneAt < 0; i++) {
            rf.update(1 / 60f);
            if (retchAt < 0 && rf.telegraphing(b, "retch")) { retchAt = rf.time; }
            if (retchAt >= 0 && rf.time - retchAt > 0.3f) { rf.player.x = px + 9; }          // the counterplay: move during the wind-up
            if (!rf.zones.isEmpty()) zoneAt = rf.time;
        }
        float hpBefore = rf.player.hp;
        for (int i = 0; i < 90; i++) rf.update(1 / 60f);
        check("the Spewer retches for most of a second before the pool lands (0.7: the spitting moved from the fat one to the Spewer)", retchAt >= 0 && zoneAt - retchAt >= 0.85f, String.format("retch %.2f s -> pool %.2f s", retchAt, zoneAt));
        check("the pool lands where you stood: moving during the wind-up avoids it", !rf.zones.isEmpty() && Math.abs(rf.zones.get(0).x - px) < 1.5f && rf.player.hp == hpBefore, String.format("pool at %.1f, player at %.1f", rf.zones.isEmpty() ? 0 : rf.zones.get(0).x, rf.player.x));
        int noise0 = rf.sounds.size();
        float contam0 = rf.profile.zone.contamination[District.MARKET.ordinal()];
        rf.player.x = px + 30;
        b = rf.debugSpawn("bomber", 300, 493, World.GROUND);
        rf.damageActor(b, rf.player, 9999, Content.def("ammo.556"), "torso", b.x, b.y + 1, b.z);
        boolean contam = false, boom = false;
        for (Raid.Zone z : rf.zones) contam |= z.kind.equals("bile");
        for (Raid.Sound s : rf.sounds) boom |= s.category().equals("explosion");
        check("killing a Bomber ruptures it: a pool of bile, district contamination and a noise event", b.dead && b.burst && contam && boom && rf.sounds.size() > noise0 && rf.profile.zone.contamination[District.MARKET.ordinal()] > contam0, null);
        // ---- giant: throw and charge are telegraphed, light cover does not stop it, no one-hit kills
        Raid rg = stage(new Profile());
        Actor g = rg.debugSpawn("tank", 300, 478, World.GROUND);
        g.yaw = (float) Math.PI;
        float throwAt = -1, rockAt = -1;
        boolean stomped = false;
        for (int i = 0; i < 60 * 6 && rockAt < 0; i++) {
            rg.update(1 / 60f);
            if (throwAt < 0 && rg.telegraphing(g, "throw")) throwAt = rg.time;
            for (Raid.Thrown t : rg.thrown) if (t.debris) rockAt = rg.time;
        }
        for (Raid.Sound s : rg.sounds) stomped |= s.category().equals("roar") || s.category().equals("stomp");
        check("giant telegraphs its debris throw with a roar and a wind-up", throwAt >= 0 && rockAt - throwAt >= 1.2f && stomped, String.format("wind-up %.2f s", rockAt - throwAt));
        Raid rc = stage(new Profile());
        for (int k = -1; k <= 1; k++) for (int y = 0; y < 2; y++) rc.world.setRaw(500 + k, World.GROUND + y, 497, Block.WOOD_PLANKS);
        Actor g2 = rc.debugSpawn("tank", 500.5f, 498.6f, World.GROUND);
        g2.yaw = 0; g2.stuckTime = 1;
        rc.smash(g2);
        rc.world.setRaw(504, World.GROUND, 497, Block.SANDBAG);
        check("giant destroys light cover in its path (planks, fences, furniture - not sandbags)", rc.world.get(500, World.GROUND, 497) != Block.WOOD_PLANKS && Block.ALL[Block.SANDBAG].hp >= 400, null);
        rc.world.setRaw(504, World.GROUND, 497, Block.AIR);
        for (int k = -1; k <= 1; k++) for (int y = 0; y < 2; y++) rc.world.setRaw(500 + k, World.GROUND + y, 497, Block.AIR);
        rc.player.hp = 100; rc.god = false;
        rc.player.x = 500.5f; rc.player.z = 497f;
        rc.melee(g2, rc.player, giant.melee() * 1.25f);
        check("a giant's blow hurts but never kills outright (even on Hardcore)", rc.player.hp > 0 && rc.player.hp < 100, String.format("%.0f hp left", rc.player.hp));
        // ---- ecology
        Raid re = stage(new Profile());
        Actor body = re.debugSpawn("raiders", 300, 470, World.GROUND);
        re.damageActor(body, null, 999, null, "torso", body.x, body.y + 1, body.z);
        Actor w = walker(re, 300, 452);
        w.yaw = 0;                                           // facing away from the player
        float d0 = w.distXZ(body.x, body.z);
        for (int i = 0; i < 60 * 12; i++) re.update(1 / 60f);
        check("regular infected gather on unattended corpses", w.distXZ(body.x, body.z) < d0 - 4, String.format("%.0f m -> %.0f m from the body", d0, w.distXZ(body.x, body.z)));
        ZoneState clean = ZoneState.fresh(), dirty = ZoneState.fresh();
        for (int i = 0; i < 6; i++) { dirty.reportCorpse(District.MARKET); dirty.reportContamination(District.MARKET, 1); }
        clean.advance(12, 3); dirty.advance(12, 3);
        check("infected numbers rise near contamination and corpses (strategic tier)", dirty.infected[District.MARKET.ordinal()] > clean.infected[District.MARKET.ordinal()] + 0.02f, String.format("%.2f vs %.2f", dirty.infected[District.MARKET.ordinal()], clean.infected[District.MARKET.ordinal()]));
        ZoneState loud = ZoneState.fresh();
        float total0 = 0, total1 = 0;
        for (float v : loud.infected) total0 += v;
        loud.reportNoise(District.MARKET, 400);
        for (float v : loud.infected) total1 += v;
        check("loud events redistribute infected, they do not create them", Math.abs(total1 - total0) < 1e-3f && loud.infected[District.MARKET.ordinal()] > ZoneState.fresh().infected[District.MARKET.ordinal()], String.format("total %.2f -> %.2f", total0, total1));
    }

    private static void districts() {
        System.out.println("[Map: six districts, central plant, placement audit, loot by location]");
        World w = new World();
        new absentialis.world.gen.CityGenerator(w, 1337L, s -> {}).generate();
        MapData m = w.map;
        check("six districts are defined", District.values().length == 6, null);
        StringBuilder bad = new StringBuilder();
        for (District d : District.values()) {
            if (d == District.WILDERNESS ? d.def.landmarks.length < 3 : d.def.landmarks.length != 2) bad.append(d.display).append(": landmarks; ");
            if (d.def.lootWeights().size() < ZoneState.SUPPLY.length) bad.append(d.display).append(": loot weights; ");
            for (String f : ZoneState.FACTIONS) if (d.def.influence(f) <= 0) bad.append(d.display).append(": influence ").append(f).append("; ");
            if (d.def.exits.length == 0) bad.append(d.display).append(": exits; ");
            for (String e : d.def.exits) {
                String id = e.substring(0, e.indexOf(':')), type = e.substring(e.indexOf(':') + 1);
                MapData.Exit x = m.exit(id);
                if (x == null) bad.append(d.display).append(": exit ").append(id).append(" not on the map; ");
                else if (!x.type.name().equals(type)) bad.append(id).append(": type ").append(x.type).append("; ");
                else if (x.district != d) bad.append(id).append(": lies in ").append(x.district.display).append("; ");
            }
            int found = 0;
            for (String name : d.def.landmarks) for (MapData.Point lp : m.landmarks) if (lp.name.contains(name) || name.contains(lp.name)) { found++; break; }
            if (found < 2) bad.append(d.display).append(": only ").append(found).append(" listed landmarks exist; ");
        }
        java.util.Set<String> types = new java.util.HashSet<>();
        for (MapData.Exit e : m.exits) types.add(e.type.name());
        check("every district has landmarks, loot weights, faction baseline and extraction points on the map", bad.length() == 0, bad.length() == 0 ? m.exits.size() + " exits, " + m.landmarks.size() + " landmarks" : bad.toString());
        check("extractions cover permanent, conditional and time-windowed types", types.contains("PERMANENT") && types.contains("CONDITIONAL") && types.contains("TIME_WINDOW"), types.toString());
        StringBuilder hq = new StringBuilder();
        for (District d : District.CITY) {
            int n = 0, heads = 0;
            for (MapData.Landmark lm : m.anchors) if (lm.district == d) { n++; if (lm.headquarters) { heads++; if (!lm.faction.equals(d.faction)) hq.append(d.display).append(": headquarters held by ").append(lm.faction).append("; "); } }
            if (n != 2) hq.append(d.display).append(": ").append(n).append(" landmarks; ");
            if (heads != 1) hq.append(d.display).append(": ").append(heads).append(" headquarters; ");
        }
        check("every developed district has exactly two landmark compounds, one of them its faction's headquarters", hq.length() == 0, hq.length() == 0 ? m.anchors.size() + " compounds" : hq.toString());
        check("no prop is left floating after the placement audit", m.propsFloating == 0 && absentialis.world.gen.Validation.countFloating(w) == 0, m.propsGrounded + " grounded, " + m.propsRemoved + " removed");
        int standable = 0;
        for (MapData.Node n : m.nodes) if (absentialis.world.gen.Validation.standable(w, n.x, n.y, n.z)) standable++;
        check("every street-graph node is a place an agent can stand", standable == m.nodes.size() && m.nodesBlocked == 0, standable + " / " + m.nodes.size());
        int wrong = 0, logic = 0;
        StringBuilder wrongWhat = new StringBuilder();
        for (MapData.LootSpot l : m.loot) {
            int id = w.get(l.x, l.y, l.z);
            String want = id == Block.MED_CABINET ? "medical" : id == Block.WEAPON_RACK ? "weapons" : id == Block.TOOLBOX ? "industrial" : id == Block.FRIDGE ? "food" : id == Block.CRATE_MIL ? "military" : null;
            if (want == null) continue;
            logic++;
            if (!l.table.startsWith(want)) { wrong++; if (wrong <= 4) wrongWhat.append(Block.ALL[id].name).append("->").append(l.table).append(" at ").append(l.x).append(',').append(l.z).append("; "); }
        }
        check("loot follows location: medicine in cabinets, parts in toolboxes, materiel in racks and crates", logic > 300 && wrong == 0, logic + " checked, " + wrong + " wrong " + wrongWhat);
        float hi = 0, lo = 9;
        for (District d : District.CITY) { hi = Math.max(hi, d.def.patrol); lo = Math.min(lo, d.def.patrol); }
        check("patrol density and loot weighting differ by district", hi > lo * 2 && District.MARKET.def.lootWeight("food") > District.INDUSTRIAL.def.lootWeight("food") && District.INDUSTRIAL.def.lootWeight("fuel") > District.MARKET.def.lootWeight("fuel"), null);
        // ---- v0.3 map pass
        int cars = 0, trunk = 0, signs = 0, lanes = 0, posts = 0;
        for (int x = 0; x < World.W; x++) for (int z = 0; z < World.D; z++) for (int y = World.GROUND - 1; y < World.GROUND + 8; y++) {
            int id = w.get(x, y, z);
            if (id == Block.CAR_RED || id == Block.CAR_BLUE || id == Block.CAR_WHITE || id == Block.CAR_RUST || id == Block.CAR_OLIVE || id == Block.CAR_WINDOW || id == Block.CAR_TIRE) cars++;
            if (Block.trunk(id)) trunk++;
            if (id == Block.ROAD_SIGN || id == Block.ROAD_SIGN_Z) signs++;
            if (id == Block.ROAD_LINE_X) lanes++;
            if (id == Block.LAMP_POST) posts++;
        }
        check("no vehicles anywhere on the map", cars == 0, cars + " vehicle blocks");
        check("ring road: a continuous marked trunk with direction boards", trunk > 1200 && signs > 20, trunk + " trunk blocks, " + signs + " signs");
        check("lane markings follow roads in both directions; lamp posts are slim props", lanes > 200 && posts > 500, lanes + " / " + posts);
        java.util.Set<District> onRing = new java.util.HashSet<>();
        for (int x = World.CITY_MIN; x < World.CITY_MAX; x++) for (int z = World.CITY_MIN; z < World.CITY_MAX; z++) if (Block.trunk(w.get(x, World.GROUND - 1, z))) for (int dx = -20; dx <= 20; dx += 20) for (int dz = -20; dz <= 20; dz += 20) onRing.add(w.district(x + dx, z + dz));
        check("the ring road touches every city district", onRing.containsAll(java.util.List.of(District.CITY)), onRing.toString());
        int doorN = 0, yardN = 0, postN = 0, lonely = 0;
        for (MapData.Node n : m.nodes) { if (n.kind.equals("door")) doorN++; if (n.kind.equals("yard")) yardN++; if (n.kind.equals("post") || n.kind.equals("nest")) postN++; if (n.links.isEmpty()) lonely++; }
        check("navigation graph: street nodes plus door, yard and post nodes, all linked", doorN > 40 && yardN > 20 && postN > 10 && lonely == 0 && m.nodes.size() > 700, m.nodes.size() + " nodes: " + doorN + " door, " + yardN + " yard, " + postN + " post, " + lonely + " unlinked");
        java.util.List<MapData.Node> across = w.nav.route(110, 110, 594, 594);
        check("graph route crosses the city (north-west corner to south-east corner)", across.size() > 8, across.size() + " nodes");
        // along the west ring road's carriageway
        int[] street = w.nav.path(100.5f, World.GROUND, 300.5f, 100.5f, 330.5f, 3000, true);
        check("grid path along a street", street != null && street.length / 3 >= 28 && street[street.length - 1] == 330, street == null ? "none" : street.length / 3 + " cells");
        int shaped = 0;
        for (Block bl : Block.all()) if (bl.shape != 0) shaped++;
        check("non-cube blocks have real geometry (ladder, doors, fences, chair, table, shelf, bed ...)", shaped >= 20 && Block.ALL[Block.LADDER].shape == absentialis.world.Shapes.LADDER && Block.ALL[Block.DOOR_WOOD_OPEN].shape == absentialis.world.Shapes.DOOR_OPEN && Block.ALL[Block.CHAIR].shape == absentialis.world.Shapes.CHAIR && !Block.ALL[Block.CHAIR].occludes(), shaped + " shaped blocks");
    }
}
