package absentialis.director;

import absentialis.character.Animator;
import absentialis.character.CharBuilder;
import absentialis.entity.Actor;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.inventory.Loadout;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.neuralai.integration.Archetypes;
import absentialis.neuralai.integration.Creatures;
import absentialis.scene.ActorRenderer;
import absentialis.scene.Raid;
import absentialis.scene.RaidScreen;
import absentialis.sim.ZoneState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ActorManager: who can be played in Director Mode, what each of them looks like and can do, and the business of
 * becoming one of them.
 *
 * The registry is built from the game's own tables - the survivor as they were made, a member of each of the four
 * factions in several appearances, every kind of infected and each special infected - so a character added to the
 * game is an actor here without anything else being written.
 *
 * Capabilities are checked, not assumed: a person can carry a weapon and drive; the infected can do neither, move at
 * their own kind's pace, have their own health and strike the way their kind strikes.
 */
public final class ActorManager {
    /** One kind of actor. */
    public static final class Def {
        public String id, name, category, faction, archetype;
        public boolean infected, canArm, canDrive;
        public int variants = 1;
        public float hp = 100, speedMul = 1, scale = 1, strike = 0;
        /** 0.9: a character the player made: its own appearance instead of a faction's, and a count that moves when it is edited. */
        public absentialis.character.Appearance app;
        public int rev;
    }

    public static final String CUSTOM = "Your characters";

    /** 0.9: the characters made in the creator become actors of their own category. Called whenever the list changes. */
    public void customs(List<PersistenceManager.Character> list) {
        Map<String, Integer> revs = new LinkedHashMap<>();
        for (Def d : defs) if (CUSTOM.equals(d.category)) revs.put(d.id, d.rev);
        defs.removeIf(d -> CUSTOM.equals(d.category));
        int at = 1;
        for (PersistenceManager.Character ch : list) {
            Def d = new Def();
            d.id = "custom:" + ch.name; d.name = ch.name; d.category = CUSTOM; d.faction = "player"; d.archetype = "rifleman"; d.canArm = true; d.canDrive = true;
            d.app = ch.app; d.rev = revs.getOrDefault(d.id, 0) + 1;
            defs.add(at++, d);
        }
        looks.keySet().removeIf(k -> k.startsWith("custom:"));
    }

    /** A chosen actor: the kind, which appearance, which loadout. This is what the shortlist stores. */
    public static final class Spec {
        public String def = "you";
        public int variant, loadout;
        public Spec() { }
        public Spec(String def, int variant, int loadout) { this.def = def; this.variant = variant; this.loadout = loadout; }
        public Spec copy() { return new Spec(def, variant, loadout); }
        public boolean same(Spec o) { return o != null && def.equals(o.def) && variant == o.variant && loadout == o.loadout; }
        Map<String, Object> toJson() { Map<String, Object> m = new LinkedHashMap<>(); m.put("def", def); m.put("variant", variant); m.put("loadout", loadout); return m; }
        static Spec fromJson(Map<String, Object> m) { Object d = m.get("def"); if (!(d instanceof String s)) return null; return new Spec(s, m.get("variant") instanceof Number n ? n.intValue() : 0, m.get("loadout") instanceof Number n2 ? n2.intValue() : 0); }
    }

    /** What a person can be sent in with: {name, weapon, body armour, helmet}. */
    public static final String[][] LOADOUTS = {
            {"Unarmed", "", "", ""}, {"Handgun", "weapon.pm9", "", ""}, {"Submachine gun", "weapon.smg", "armor.vest_soft", ""}, {"Shotgun", "weapon.pump12", "armor.vest_soft", ""},
            {"Assault rifle", "weapon.akr", "armor.military", "helmet.ssh"}, {"Marksman", "weapon.mosin", "armor.vest_soft", "helmet.cap"}, {"Heavy", "weapon.akr", "armor.heavy", "helmet.ballistic"}};
    public static final int PEOPLE_VARIANTS = 6;

    private final RaidScreen screen;
    public final List<Def> defs = new ArrayList<>();
    /** The actor being played ("you" with nothing changed until one is chosen). */
    public Spec current = new Spec();
    public String lastProblem = "";
    /** How many times the controlled character has been replaced this session (tests). */
    public int switches;
    private String familyWas;
    private float nextStrike;
    private float scaleWas = 1, maxHpWas = 100;
    private final Map<String, ActorRenderer.Look> looks = new LinkedHashMap<>();

    public ActorManager(RaidScreen screen) {
        this.screen = screen;
        Def you = new Def();
        you.id = "you"; you.name = "The survivor"; you.category = "Survivor"; you.faction = "player"; you.archetype = "player"; you.canArm = true; you.canDrive = true;
        defs.add(you);
        for (String f : ZoneState.FACTIONS) {
            Def d = new Def();
            d.id = f; d.name = absentialis.gameplay.Alliance.name(f) + " member"; d.category = "People"; d.faction = f; d.archetype = "rifleman"; d.canArm = true; d.canDrive = true; d.variants = PEOPLE_VARIANTS;
            defs.add(d);
        }
        for (Archetypes.Arch a : Archetypes.all()) {
            if (!a.infected()) continue;
            Def d = new Def();
            d.id = a.id(); d.name = Archetypes.displayName(a.id()); d.category = Archetypes.special(a.id()) ? "Special infected" : "Infected"; d.faction = "infected"; d.archetype = a.id(); d.infected = true;
            d.name = d.name.substring(0, 1).toUpperCase() + d.name.substring(1);
            d.hp = a.hp(); d.scale = Archetypes.scale(a.id()); d.strike = a.melee(); d.variants = Archetypes.special(a.id()) ? 1 : 4;
            d.speedMul = Math.max(0.45f, Math.min(1.5f, a.runSpeed() / 5.2f));
            defs.add(d);
        }
    }

    public Def def(String id) { for (Def d : defs) if (d.id.equals(id)) return d; return null; }
    public List<String> categories() { List<String> l = new ArrayList<>(); for (Def d : defs) if (!l.contains(d.category)) l.add(d.category); return l; }

    public String describe(Spec s) {
        Def d = def(s.def);
        if (d == null) return "(unknown actor)";
        return d.name + (d.variants > 1 ? "  -  look " + (Math.floorMod(s.variant, d.variants) + 1) : "") + (d.canArm && !d.id.equals("you") ? "  -  " + LOADOUTS[Math.floorMod(s.loadout, LOADOUTS.length)][0] : "");
    }

    /** What an actor can do, in a line (the casting screen shows it under the preview). */
    public String capabilities(Def d) {
        if (d.id.equals("you")) return "Your own survivor, as they stand: their kit, their health, their skills.";
        if (d.app != null) return "A character of your own making. Can carry and fire weapons, drive vehicles, open doors. 100 health.";
        if (!d.infected) return "Can carry and fire weapons, drive vehicles, open doors. 100 health.";
        return "Cannot use weapons or vehicles. " + Math.round(d.hp) + " health. " + (d.strike > 0 ? "Strikes for " + Math.round(d.strike) + ". " : "Its swing kills what it reaches. ") + (d.speedMul > 1.05f ? "Fast." : d.speedMul < 0.8f ? "Slow." : "") + (Archetypes.arch(d.archetype).blind() ? " Others of its kind are blind; you are not." : "") + " The infected leave it alone; people do not.";
    }

    /** The look of an actor (built once, then kept: this is step 2 of a switch - the model is loaded before it is needed). */
    public ActorRenderer.Look look(Spec s) {
        Def d = def(s.def);
        if (d == null || d.id.equals("you")) return ActorRenderer.playerLook(screen.ctx().profile, null);
        String key = d.id + "/" + Math.floorMod(s.variant, d.variants) + "/" + (d.canArm ? Math.floorMod(s.loadout, LOADOUTS.length) : 0);
        ActorRenderer.Look have = looks.get(key);
        if (have != null) return have;
        ActorRenderer.Look l = new ActorRenderer.Look();
        long seed = 7001L + d.id.hashCode() * 31L + Math.floorMod(s.variant, d.variants) * 977L;
        if (d.infected) {
            l.gear.infected = d.archetype;
            l.scale = d.scale;
            l.app = CharBuilder.factionLook("infected", seed, l.gear);
            if ("tank".equals(d.archetype) || "gladiator".equals(d.archetype) || "butcher".equals(d.archetype)) l.app.sex(false);
        } else {
            String[] lo = LOADOUTS[Math.floorMod(s.loadout, LOADOUTS.length)];
            l.gear.vest = ActorRenderer.vestGear(lo[2]);
            l.gear.head = ActorRenderer.headGear(lo[3]);
            if (d.app != null) l.app = d.app.copy();
            else { l.app = CharBuilder.factionLook(d.faction, seed, l.gear); l.app.sex(Math.floorMod(s.variant, d.variants) % 2 == 1); }
        }
        l.key = CharBuilder.key(l.app, l.gear);
        looks.put(key, l);
        return l;
    }

    /** Step 1: can this actor be put on, here and now? Null if so, otherwise why not. */
    public String validate(Spec s) {
        Def d = def(s.def);
        Raid r = screen.raid;
        if (d == null) return "That actor is no longer in the game";
        if (r.player.dead) return "Not while the actor is down";
        if (!d.canDrive && (r.vehicles.inside() || r.vehicles.busy())) return d.name + " cannot be in a vehicle: get out first";
        return null;
    }

    /**
     * Becoming an actor - the seven steps of the design, in order:
     *   1 validate the choice;  2 load the model;  3 note where the character stands and faces;
     *   4 replace the controlled character;  5 apply its appearance and what it can do;
     *   6 give the controls and the camera back;  7 let go of what the last actor needed.
     * Returns null when it is done, or the reason it was not.
     */
    public String switchTo(Spec s, Loadout original) {
        lastProblem = validate(s);                                                             // 1
        if (lastProblem != null) return lastProblem;
        Def d = def(s.def);
        Raid r = screen.raid;
        Actor p = r.player;
        ActorRenderer.Look look = d.id.equals("you") ? null : look(s);                         // 2
        if (look != null) screen.ctx().ar.flatten(look);
        float x = p.x, y = p.y, z = p.z, yaw = p.yaw, pitch = p.pitch;                        // 3
        if (familyWas == null) { familyWas = p.anim == null ? "human" : p.anim.family; scaleWas = p.anim == null ? 1 : p.anim.scale; maxHpWas = p.maxHp; }
        Spec before = current;
        current = s.copy();                                                                    // 4
        screen.lookOverride = look;
        r.playerKind = d.infected ? d.archetype : "";
        if (p.anim != null) { p.anim.family = d.infected ? Animator.familyOf(d.archetype, true) : familyWas; p.anim.scale = d.infected ? d.scale : scaleWas; p.anim.action = ""; }
        p.maxHp = d.id.equals("you") ? maxHpWas : d.hp;                                        // 5
        p.hp = p.maxHp;
        r.playerSpeedMul = d.speedMul;
        r.playerCanDrive = d.canDrive;
        if (d.id.equals("you")) { if (original != null) r.profile.loadout = Loadout.fromJson(original.toJson()); }
        else r.profile.loadout = d.canArm ? kit(LOADOUTS[Math.floorMod(s.loadout, LOADOUTS.length)]) : new Loadout();
        r.applyArmor();
        p.weapon = null;
        if (d.infected) screen.thirdPersonView(true);                                           // something with no hands is seen from outside
        screen.scenarioHold(r.profile.loadout.slot("primary") != null ? "primary" : r.profile.loadout.slot("sidearm") != null ? "sidearm" : "melee");
        p.x = x; p.y = y; p.z = z; p.yaw = yaw; p.pitch = pitch; p.vx = p.vy = p.vz = 0;      // 6
        p.prone = false; p.crouch = false;
        if (before != null && !before.same(s)) { Def bd = def(before.def); if (bd != null && !bd.id.equals("you")) looks.remove(bd.id + "/" + Math.floorMod(before.variant, bd.variants) + "/" + (bd.canArm ? Math.floorMod(before.loadout, LOADOUTS.length) : 0)); }   // 7
        switches++;
        return null;
    }

    private static Loadout kit(String[] lo) {
        Loadout l = new Loadout();
        if (!lo[1].isEmpty()) {
            ItemInstance w = new ItemInstance(lo[1]);
            w.origin = "director";
            ItemDef wd = w.def();
            ItemDef ammo = wd.weapon == null ? null : Content.ammoFor(wd.weapon.caliber);
            if (ammo != null) { w.rounds = w.capacity(); w.ammoId = ammo.id; }
            InventoryService.equip(w, l, wd.slot == null ? "primary" : wd.slot);
            if (ammo != null) for (int i = 0; i < 4; i++) { ItemInstance a = new ItemInstance(ammo.id); a.qty = ammo.maxStack; a.origin = "director"; InventoryService.store(a, l); }
        }
        for (String id : new String[]{lo[2], lo[3]}) if (!id.isEmpty() && Content.def(id) != null) { ItemInstance g = new ItemInstance(id); g.origin = "director"; InventoryService.equip(g, l, g.def().slot); }
        ItemInstance knife = new ItemInstance("melee.knife");
        knife.origin = "director";
        InventoryService.equip(knife, l, "melee");
        return l;
    }

    /** An infected actor strikes: the way its kind does, at whoever is in front of it. Returns true if it swung. */
    public boolean strike() {
        Def d = def(current.def);
        Raid r = screen.raid;
        Actor p = r.player;
        if (d == null || !d.infected || p.dead || r.time < nextStrike) return false;
        nextStrike = r.time + ("gladiator".equals(d.archetype) || "butcher".equals(d.archetype) ? 1.3f : 0.7f);
        if ("gladiator".equals(d.archetype)) { if (p.anim != null) p.anim.play("claw_swing", 0); swing(r, p, Creatures.CLAW_REACH, 600, "claw_swing"); return true; }
        if ("butcher".equals(d.archetype)) { if (p.anim != null) p.anim.play("saw_swing", 0); swing(r, p, Creatures.SAW_REACH, 600, "chainsaw_rev"); return true; }
        if (p.anim != null) p.anim.play("tank".equals(d.archetype) ? "giant_smash" : "inf_attack", 0);
        swing(r, p, "tank".equals(d.archetype) ? 2.4f : 1.7f, d.strike, "melee");
        return true;
    }

    /** Everyone living in front of the actor and within reach is hit. */
    private static void swing(Raid r, Actor p, float reach, float dmg, String sound) {
        float fx = (float) -Math.sin(p.yaw), fz = (float) -Math.cos(p.yaw);
        r.cue(sound, p.x, p.y + 1.4f, p.z, 0.8f, 40);
        for (Actor o : new ArrayList<>(r.actors)) {
            if (o == p || o.dead || Math.abs(o.y - p.y) > 1.8f) continue;
            float dx = o.x - p.x, dz = o.z - p.z, dist = (float) Math.sqrt(dx * dx + dz * dz);
            if (dist > reach + o.radius || dist > 0.3f && (dx * fx + dz * fz) / dist < 0.25f) continue;
            r.melee(p, o, Math.max(20, dmg));
        }
    }

    /** Puts the survivor back exactly as they were (the session is ending). */
    public void restore() {
        Raid r = screen.raid;
        Actor p = r.player;
        screen.lookOverride = null;
        r.playerKind = ""; r.playerSpeedMul = 1; r.playerCanDrive = true;
        if (p.anim != null && familyWas != null) { p.anim.family = familyWas; p.anim.scale = scaleWas; p.anim.action = ""; }
        if (familyWas != null) p.maxHp = maxHpWas;
        familyWas = null;
        current = new Spec();
        looks.clear();
    }
}
