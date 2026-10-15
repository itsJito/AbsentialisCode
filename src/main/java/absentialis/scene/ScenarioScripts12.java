package absentialis.scene;

import static absentialis.scene.Scenarios.*;

import absentialis.entity.Actor;
import absentialis.world.World;
import absentialis.world.gen.DevMap;

/**
 * The scripts added for 1.2 (see {@link ScenarioScripts}): things that can only be judged by looking at them. On the
 * developer map, like the 1.1 scripts.
 *
 *   ABS_SCENARIO=v12 ... the ladder and the cards (in the hideout); then, on the devmap: the animals alive and - as
 *                         bodies of the Sydney Engine's - dead; a spider rearing to strike; a crow on the wing; the
 *                         guard, with empty hands and with a blade, in third person and through the eyes; and
 *                         Developer Mode's list with the spot ringed on the ground.
 */
final class ScenarioScripts12 {
    private ScenarioScripts12() {}

    static boolean build(String name) {
        switch (name == null ? "" : name) {
            case "v12" -> v12();
            default -> { return ScenarioScripts13.build(name); }
        }
        return true;
    }

    private static void v12() {
        profile(p -> { give(p, "melee.knife", 1, true); p.prog.addXp(60000); p.prog.bestRank = 50; p.prog.prestige = 2; });
        // ---- the hideout: the ladder, and three cards
        hideout(10, h -> h.hideout().debugDeployTab(1));
        hideout(26, h -> shot(h, "ui12_00_ladder"));
        hideout(28, h -> { h.hideout().debugShow(-1, false); h.hideout().tab = HideoutScreen.TAB_PROFILE; h.ctx().profile.cardBackground = "prestige"; });
        hideout(40, h -> shot(h, "ui12_01_card_skull"));
        hideout(42, h -> h.ctx().profile.cardBackground = "operator");
        hideout(52, h -> shot(h, "ui12_02_card_operator"));
        hideout(54, h -> h.ctx().profile.cardBackground = "prestigious");
        hideout(64, h -> shot(h, "ui12_03_card_cross"));
        hideout(66, h -> h.ctx().profile.cardBackground = "thunder");
        hideout(76, h -> shot(h, "ui12_04_card_bolt"));
        hideout(78, h -> h.ctx().profile.cardBackground = "night");
        hideout(88, h -> shot(h, "ui12_05_card_stars"));
        hideout(90, h -> h.ctx().profile.cardBackground = "field");
        // (the devmap is asked for only once the hideout has been photographed: asking for it deploys)
        Scenarios.hour = 13;
        for (int f = 100; f < 3000; f += 30) hideout(f, h -> { if (DevMap.mansion == null && h.worldReady()) h.console("devmap 0"); });
        final float g = World.GROUND, rx = DevMap.ROAD_X0 + 30, rz = (DevMap.ROAD_Z0 + DevMap.ROAD_Z1) / 2f + 0.5f;
        final Animals.Animal[] an = new Animals.Animal[10];
        final Actor[] man = new Actor[2];
        // ---- the animals, alive
        ScenarioScripts11.at(30, h -> { Raid r = h.raid().raid; r.god = true; r.devGhost = true; r.freezeAI = true; h.console("weather clear"); h.console("time 13"); place(r, rx - 12, g, rz, 0, 0);
            h.raid().photo = true;
            an[0] = r.animals.spawn(Animals.Kind.DOG, rx, g, rz); an[1] = r.animals.spawn(Animals.Kind.IDOG, rx + 2.2f, g, rz);
            an[2] = r.animals.spawn(Animals.Kind.CHICKEN, rx + 4f, g, rz); an[3] = r.animals.spawn(Animals.Kind.CROW, rx + 5.2f, g, rz);
            an[4] = r.animals.spawn(Animals.Kind.SPIDER, rx + 8.5f, g, rz); an[5] = r.animals.spawn(Animals.Kind.BROOD, rx + 12.5f, g, rz);
            for (int i = 0; i < 6; i++) an[i].yaw = (float) Math.PI * 0.75f;
            h.raid().camOverride = ScenarioScripts11.look(rx + 5.5f, g + 2.6f, rz + 9.5f, rx + 6, g + 0.7f, rz, 58); });
        ScenarioScripts11.at(110, h -> shot(h, "animals12_00_alive"));
        // ---- and dead: every one of them a body, struck from a different side
        ScenarioScripts11.at(112, h -> { Raid r = h.raid().raid; for (int i = 0; i < 6; i++) r.animals.hurt(an[i], 9999, r.player.id, (float) Math.cos(i * 1.3f), (float) Math.sin(i * 1.3f)); });
        ScenarioScripts11.at(124, h -> shot(h, "animals12_01_falling"));
        ScenarioScripts11.at(260, h -> shot(h, "animals12_02_dead"));
        ScenarioScripts11.at(262, h -> h.raid().camOverride = ScenarioScripts11.look(rx + 10.5f, g + 1.5f, rz + 4.2f, rx + 10.5f, g + 0.4f, rz, 50));
        ScenarioScripts11.at(280, h -> shot(h, "animals12_03_dead_spiders"));
        // ---- a spider rearing at a man who has walked onto its ground
        ScenarioScripts11.at(284, h -> { Raid r = h.raid().raid; r.animals.all.clear(); r.freezeAI = false;
            an[6] = r.animals.spawn(Animals.Kind.SPIDER, rx + 20, g, rz); an[6].yaw = (float) -Math.PI / 2;
            man[0] = r.debugSpawn("survivors", rx + 22.0f, rz, g); if (man[0] != null) { man[0].hostileToPlayer = false; man[0].hp = man[0].maxHp = 9999; r.freezeOnly(man[0]); }
            h.raid().camOverride = ScenarioScripts11.look(rx + 21, g + 1.3f, rz + 4.4f, rx + 21, g + 0.7f, rz, 50); });
        for (int k = 0; k < 6; k++) { final int kk = k; ScenarioScripts11.at(300 + k * 10, h -> shot(h, "animals12_1" + kk + "_strike")); }
        // ---- a crow, flushed, on the wing
        ScenarioScripts11.at(364, h -> { Raid r = h.raid().raid; r.animals.all.clear(); if (man[0] != null) r.actors.remove(man[0]);
            an[7] = r.animals.spawn(Animals.Kind.CROW, rx + 30, g, rz);
            man[1] = r.debugSpawn("survivors", rx + 28.2f, rz, g); if (man[1] != null) { man[1].hostileToPlayer = false; r.freezeOnly(man[1]); } });
        for (int k = 0; k < 6; k++) { final int kk = k; ScenarioScripts11.at(372 + k * 14, h -> { Animals.Animal c = an[7]; h.raid().camOverride = ScenarioScripts11.look(c.x - 1.2f, c.y + 0.5f, c.z + 2.6f, c.x, c.y + 0.2f, c.z, 46); });
            ScenarioScripts11.at(373 + k * 14, h -> shot(h, "animals12_2" + kk + "_flight")); }
        // ---- the guard: empty hands, third person and first; then with the blade
        ScenarioScripts11.at(470, h -> { Raid r = h.raid().raid; r.animals.all.clear(); if (man[1] != null) r.actors.remove(man[1]); r.devGhost = false; r.freezeAI = true;
            place(r, rx + 40, g, rz, (float) Math.PI / 2, 0); h.raid().scenarioHold("primary"); h.raid().thirdPerson = true; h.raid().scenarioBlock = true;
            h.raid().camOverride = ScenarioScripts11.look(rx + 37.6f, g + 1.5f, rz + 1.4f, rx + 40, g + 1.2f, rz, 44); });
        ScenarioScripts11.at(500, h -> shot(h, "guard12_00_third_person"));
        ScenarioScripts11.at(502, h -> { h.raid().thirdPerson = false; h.raid().camOverride = null; h.raid().photo = false; });
        ScenarioScripts11.at(520, h -> shot(h, "guard12_01_first_person"));
        ScenarioScripts11.at(522, h -> { h.raid().scenarioHold("melee"); });
        ScenarioScripts11.at(545, h -> shot(h, "guard12_02_blade_first_person"));
        ScenarioScripts11.at(547, h -> { Raid r = h.raid().raid; h.raid().thirdPerson = true; h.raid().photo = true; h.raid().camOverride = ScenarioScripts11.look(rx + 37.6f, g + 1.5f, rz + 1.4f, rx + 40, g + 1.2f, rz, 44); });
        ScenarioScripts11.at(570, h -> shot(h, "guard12_03_blade_third_person"));
        // ---- Developer Mode: the list, and the spot
        ScenarioScripts11.at(572, h -> { h.raid().scenarioBlock = false; h.raid().thirdPerson = false; h.raid().camOverride = null; h.raid().photo = false; Raid r = h.raid().raid; place(r, rx + 40, g, rz, (float) Math.PI / 2, -0.35f); h.raid().dev.toggle(); });
        ScenarioScripts11.at(590, h -> { h.raid().dev.aimNow(); h.raid().dev.panel = true; h.raid().dev.tab = 0; });
        ScenarioScripts11.at(606, h -> shot(h, "dev12_00_list"));
        ScenarioScripts11.at(608, h -> { h.raid().dev.panel = false; h.raid().dev.aimNow(); String s = h.raid().dev.spawnNpc("military:marksman"); System.out.println("[Scenario] dev spawn: " + s + " at " + java.util.Arrays.toString(h.raid().dev.target)); });
        ScenarioScripts11.at(640, h -> { System.out.println("[Scenario] dev pick: " + h.raid().dev.pickUp()); });
        ScenarioScripts11.at(660, h -> shot(h, "dev12_01_spawned_and_held"));
        ScenarioScripts11.at(662, h -> System.out.println("[Scenario] dev put: " + h.raid().dev.putDown()));
        ScenarioScripts11.at(680, h -> shot(h, "dev12_02_put_down"));
    }
}
