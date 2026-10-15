package absentialis.scene;

import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import java.util.HashSet;
import java.util.Set;

/**
 * 1.0: meeting people. Each faction has its own way of stopping someone on the road - the Military want papers, the
 * Rail Yard Crew want a toll, the shelters' people ask for help, Halberd offers a price - and each is put to the
 * survivor as a question with real answers: what it costs, what it earns, and what refusing does. One to a faction
 * in an operation, never in the middle of a fight, and only from people who are not already enemies.
 */
public final class Encounters {
    private final Raid raid;
    private final Set<String> had = new HashSet<>();
    private float tick = 6, quiet = 45;
    /** How near someone has to be to stop the survivor (metres), the least time between two meetings (seconds), and the chance at each look (every two seconds). */
    public static float REACH = 8f, BETWEEN = 80f, CHANCE = 0.3f;
    public int met;
    public String last = "";

    public Encounters(Raid raid) { this.raid = raid; }

    public void update(float dt, ChoicePopup popup) {
        quiet -= dt; tick -= dt;
        if (tick > 0 || quiet > 0 || popup.open) return;
        tick = 2f;
        Actor p = raid.player;
        if (p.dead || raid.vehicles.inside() || raid.extracting != null) return;
        // not while anything is hunting the survivor
        for (Actor a : raid.nearby(p.x, p.z, 30)) if (!a.isPlayer && !a.dead && ("engage".equals(a.state) || "search".equals(a.state)) && (a.infected() || a.hostileToPlayer)) return;
        for (Actor a : raid.nearby(p.x, p.z, REACH)) {
            if (a.isPlayer || a.dead || a.infected() || a.mate() || a.advisor || a.hostileToPlayer || a.ragdoll != null || had.contains(a.faction) || Math.abs(a.y - p.y) > 1.5f) continue;
            if (Physics.openness(raid.world, p.x, p.eyeY(), p.z, a.x, a.y + 1.5f, a.z) < 0.7f || !raid.rng.chance(CHANCE)) continue;
            if (meet(a, popup)) { had.add(a.faction); quiet = BETWEEN; met++; last = a.faction; return; }
        }
    }

    /** For the scripted runs and the gates: the meeting a faction's people would have, now, with this one of them. */
    public boolean force(Actor a, ChoicePopup popup) { return meet(a, popup); }

    private void turn(String faction, float x, float z) {
        for (Actor o : raid.nearby(x, z, 28)) if (!o.dead && faction.equals(o.faction) && !o.mate() && !o.advisor) { o.hostileToPlayer = true; o.aggressor = true; }
    }

    private boolean pay(long cost) { if (raid.profile.scrip < cost) { raid.msg("You do not have " + absentialis.Version.coins(cost), 0xFFD8483A); return false; } raid.profile.scrip -= cost; return true; }

    private boolean meet(Actor a, ChoicePopup popup) {
        var pr = raid.profile;
        String who = Raid.displayName(a) + "  -  " + absentialis.gameplay.Alliance.name(a.faction);
        float rep = pr.rep(a.faction);
        switch (a.faction) {
            case "military" -> popup.show("Checkpoint", who, "A soldier steps into your way with a hand raised, and two more are suddenly looking at you. \"Papers. Then the case - open it, slowly.\" Nobody has pointed anything at you yet.", i -> {
                        if (i == 0) { pr.addRep("military", 2); raid.msg("They go through your kit, hand it back, and wave you on.", 0xFFD6CEB8); }
                        else if (i == 1) { if (pay(250)) { pr.addRep("military", 1); raid.msg("The search is over before it starts.", 0xFFD6CEB8); } else { pr.addRep("military", 2); raid.msg("You open the case instead.", 0xFFD6CEB8); } }
                        else { pr.addRep("military", -5); if (pr.rep("military") < 10) { turn("military", a.x, a.z); raid.msg("\"Stop that one!\"", 0xFFD8483A); } else raid.msg("They let you go. They have written something down.", 0xFFE2A034); }
                    })
                    .option("Submit to the search", "Costs nothing but the time.    Military +2", "good")
                    .option("Give them a reason to hurry  (" + absentialis.Version.coins(250) + ")", "Military +1")
                    .option("Refuse, and keep walking", "Military -5." + (rep - 5 < 10 ? "  They do not know you well enough to let that go." : "  They know you; they will let it go, this once."), "bad");
            case "raiders" -> popup.show("The toll", who, "\"Road tax.\" He says it the way you would tell someone the time. The others have spread out without seeming to move. \"Everybody pays. It is cheaper than the alternative.\"", i -> {
                        if (i == 0) { if (pay(300)) { pr.addRep("raiders", 2); raid.msg("He counts it, and steps out of the road.", 0xFFD6CEB8); } else { pr.addRep("raiders", -4); turn("raiders", a.x, a.z); raid.msg("\"Then we take it out of what you are carrying.\"", 0xFFD8483A); } }
                        else if (i == 1) { pr.addRep("raiders", 1); pr.addRep("survivors", -1); raid.msg("You tell them where the dead are thickest, and who was sheltering near it. They let you by.", 0xFFD6CEB8); }
                        else { pr.addRep("raiders", -4); if (pr.rep("raiders") < 15) { turn("raiders", a.x, a.z); raid.msg("\"Wrong answer.\"", 0xFFD8483A); } else raid.msg("He laughs, eventually. \"Next time, then.\"", 0xFFE2A034); }
                    })
                    .option("Pay it  (" + absentialis.Version.coins(300) + ")", "Bandits +2")
                    .option("Pay in what you have seen", "Tell them what is on the roads behind you.    Bandits +1    Loners -1")
                    .option("Refuse", "Bandits -4." + (rep - 4 < 15 ? "  They will take it another way." : "  You are known to them; it will pass."), "bad");
            case "survivors" -> {
                ItemInstance med = null;
                for (ItemInstance it : pr.loadout.all()) if (it.def().medical != null && it.slot == null) { med = it; break; }
                final ItemInstance give = med;
                popup.show("Someone asking", who, "\"Please.\" There is a child's coat over one arm and nobody wearing it. \"Have you anything for a fever? Or food. I can tell you what I have seen. It is all I have.\"", i -> {
                            if (i == 0) { if (give != null) { String n = give.name(); LoadoutPanel.consumeOne(give); pr.addRep("survivors", 5); pr.prog.addXp(150); raid.msg("You give them " + n + ". They do not say thank you. They say a name, twice.", 0xFFA8D08C); } else raid.msg("You have nothing of the kind to give.", 0xFFE2A034); }
                            else if (i == 1) { if (pay(150)) { pr.addRep("survivors", 3); raid.msg("They take it without looking at it.", 0xFFD6CEB8); } }
                            else if (i == 2) { boolean told = false; for (DistrictEvents.Live l : raid.events.hotspots) if (!l.announced && !l.resolved) { l.announced = true; told = true; raid.msg("They tell you about " + l.def.name() + ". It is on your map.", 0xFFD6CEB8); break; } if (!told) raid.msg("They have seen what you have seen. Nothing more.", 0xFFD6CEB8); }
                            else pr.addRep("survivors", -1);
                        })
                        .option(give == null ? "Give them medicine  (you have none)" : "Give them " + give.name(), "Loners +5    +150 XP", give == null ? "dim" : "good")
                        .option("Give them money  (" + absentialis.Version.coins(150) + ")", "Loners +3")
                        .option("Ask what they have seen", "They may know of something worth going to.")
                        .option("Walk on", "Loners -1", "dim").escapes();
            }
            case "mercenaries" -> {
                ItemInstance held = raid.player.weapon;
                ItemDef am = held == null || held.def().weapon == null || held.def().weapon.caliber == null ? null : Content.ammoFor(held.def().weapon.caliber);
                popup.show("A price for everything", who, "\"You have come through the next few blocks and we have not.\" She has a notebook open already. \"Halberd pays for eyes. Or sells - we are overstocked on what you are shooting.\"", i -> {
                            if (i == 0) { pr.scrip += 200; pr.addRep("mercenaries", 2); pr.addRep("survivors", -1); raid.msg("She writes it all down and pays you " + absentialis.Version.coins(200) + ".", 0xFFD6CEB8); }
                            else if (i == 1 && am != null) { if (pay(180)) { ItemInstance box = new ItemInstance(am.id); box.qty = Math.min(am.maxStack, 30); box.foundInRaid = true; if (!InventoryService.add(box, pr.loadout.caseGrid).ok()) raid.dropItem(box, raid.player.x, raid.player.y + 0.3f, raid.player.z); pr.addRep("mercenaries", 1); raid.msg("Thirty rounds, counted out twice.", 0xFFD6CEB8); } }
                        })
                        .option("Sell what you have seen  (+" + absentialis.Version.coins(200) + ")", "Mercenaries +2    Loners -1")
                        .option(am == null ? "Buy ammunition  (nothing they stock fits)" : "Buy " + am.name + "  (" + absentialis.Version.coins(180) + ")", am == null ? "" : "Thirty rounds.    Mercenaries +1", am == null ? "dim" : "")
                        .option("Not today", "", "dim").escapes();
            }
            default -> { return false; }
        }
        return true;
    }
}
