package absentialis.scene;

import absentialis.neuralai.integration.Archetypes;
import absentialis.neuralai.integration.NeuralHost;
import absentialis.neuralai.integration.Relations;
import absentialis.character.Animator;
import absentialis.character.Pose;
import absentialis.character.Ragdoll;
import absentialis.character.Rig;
import absentialis.engine.math.Mat4;
import absentialis.engine.events.EventBus;
import absentialis.engine.events.Events;
import absentialis.engine.util.Rng;
import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.entity.Survivor;
import absentialis.gameplay.inventory.Container;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.gameplay.weapons.Gunsmith;
import absentialis.gameplay.weapons.WeaponService;
import absentialis.persistence.Profile;
import absentialis.persistence.Settings;
import absentialis.sim.ZoneState;
import absentialis.world.Block;
import absentialis.world.District;
import absentialis.world.MapData;
import absentialis.world.Material;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One deployment (GDD 2): the operational slice of the persistent Zone. Owns the raid clock and
 * closing phase, extraction rules, ballistics, sound propagation, the tiered agent population
 * and loot. It never writes to the profile directly - outcomes are collected into a
 * {@link Debrief} that {@link RaidResolver} applies exactly once.
 */
public final class Raid {
    public final World world;
    public final Profile profile;
    public final Settings settings;
    public final EventBus bus;
    public final Rng rng;
    public final Actor player = new Actor();
    public final Survivor body = new Survivor();
    public final List<Actor> actors = new ArrayList<>();
    public final List<Projectile> projectiles = new ArrayList<>();
    public final List<Thrown> thrown = new ArrayList<>();
    public final List<WorldItem> items = new ArrayList<>();
    public final List<Sound> sounds = new ArrayList<>();
    /**
     * 1.0: every particle in an operation belongs to the Astratto particle system - sparks, dust, debris, smoke, flame,
     * rain, snow. The old "effects" (loose cubes that fell through the floor) are still how a few places ask for a thrown
     * voxel; what they hand over is now made a particle of the system, which collides, bounces, lies and is lit.
     */
    public final absentialis.engine.fx.ParticleSystem fx = new absentialis.engine.fx.ParticleSystem();
    /** 1.0, the Astratto weather system: fog, rain, storm, snow, wind, lightning - one state everything reads. */
    public final absentialis.engine.weather.Weather weather = new absentialis.engine.weather.Weather();
    public final WorldFx worldFx;
    /** The decal engine and the foliage of the world this operation is in (null without a window: gates). */
    public absentialis.engine.decal.Decals decals;
    public absentialis.engine.foliage.Foliage foliage;
    public absentialis.engine.props.Props props;
    /** Where the weather is made round: the camera when there is one, the survivor otherwise. */
    public float focusX, focusY, focusZ;
    public boolean focusSet;
    public final class EffectSink {
        public void add(Effect e) { fx.ember(e.x, e.y, e.z, e.vx, e.vy, e.vz, e.max > 0 ? e.max : e.life, Math.max(0.03f, e.size), e.color & 0xFFFFFF, e.glow); }
        public int size() { return fx.count(); }
        public void clear() { fx.clear(); }
    }
    public final EffectSink effects = new EffectSink();
    public final List<Telegraph> telegraphs = new ArrayList<>();
    public final List<Zone> zones = new ArrayList<>();
    public final Map<Integer, Squad> squads = new HashMap<>();
    /** Anatomical damage, severed parts and blood. */
    public final Gore gore = new Gore(this);
    /** Simulated fire: burn barrels, incendiary pools, anything that is alight. */
    public final FireSystem fire = new FireSystem(this);
    /** Bites, the course of the infection, treatment and turning. */
    public final InfectionSystem infection = new InfectionSystem(this);
    /** Drivable vehicles: never more than four in an operation. */
    public final VehicleSystem vehicles = new VehicleSystem(this);
    /** Every door as a hinged, damageable entity. */
    public final Doors doors = new Doors(this);
    /** 1.1: how many always-there items this operation started with. */
    public int fixedItems;
    /** 1.1: where the dead gather, what gunfire does to them, and when people run. */
    public final Horde horde = new Horde(this);
    /** 1.1: animals, and the infected that go on four legs and eight. */
    public final Animals animals = new Animals(this);
    /** 0.9: a window breaking is one sound, not one for every pane and shard. */
    public final GlassAudio glass = new GlassAudio(this);
    /** 0.9: what is going on in each district - its hotspot, and the smaller things that happen near the survivor. */
    public final DistrictEvents events = new DistrictEvents(this);
    /**
     * 0.7, the NeuralAI engine: what every human NPC - hostile, neutral or one of the player's own - knows, decides
     * and attempts (package neuralai). neuralWorld is its view of this raid; neuralBody is the bridge it moves bodies
     * through. The raid stays the authority on everything that actually happens.
     */
    public final absentialis.neuralai.integration.GameAdapter neuralWorld = new absentialis.neuralai.integration.GameAdapter(this);
    public final absentialis.neuralai.integration.SydneyAdapter neuralBody = new absentialis.neuralai.integration.SydneyAdapter(this);
    public absentialis.neuralai.runtime.NeuralAi neural;
    /** Every sound of the last few seconds, in order, each with a sequence number: listeners keep a cursor and hear each one once. */
    private final ArrayList<absentialis.neuralai.api.GameWorld.SoundEvent> soundLog = new ArrayList<>();
    private long soundSeq;
    private final HashMap<Integer, Actor> actorIndex = new HashMap<>();

    public long soundSequence() { return soundSeq; }

    public List<absentialis.neuralai.api.GameWorld.SoundEvent> soundsSince(long sequence) {
        int n = soundLog.size(), i = n;
        while (i > 0 && soundLog.get(i - 1).sequence() > sequence) i--;
        return i >= n ? List.of() : new ArrayList<>(soundLog.subList(i, n));
    }

    /** 0.6: the bridge to the Group Rivalry System - which group each member belongs to, who the player meets, what gets reported. */
    public RaidRivalry rivalry;
    private boolean encountersSpawned;
    /** Incapacitation, bleed-out and rescue: the life-state machine of every human. */
    public final Casualty casualty = new Casualty(this);
    public final absentialis.gameplay.CombatConfig combat = absentialis.gameplay.CombatConfig.get();
    /** The weapon behind the damage being applied right now (the round's, not whatever the shooter holds by then). */
    private String hitWeaponId;
    /** How far the round being resolved had flown when it struck. */
    private float hitTravel = 999;
    /** The player's deployed AI squadmates (Gold Team = all of them). */
    public final List<Actor> mates = new ArrayList<>();
    /** Everyone who deployed with the player, living or dead, in deployment order (the squad status column). */
    public final List<Actor> element = new ArrayList<>();
    /** How the damage being applied right now was delivered ("blast" while an explosion resolves). */
    public String hitKind = "";
    private String lastKind = "bullet";
    /** Seconds the camera stays on the player's body before the debrief (0 = resolve at once: headless runs). */
    public float deathCam;
    public float dying = -1, slipT;
    private String dyingReason = "";
    /** With a death camera: the raid stays on the body until {@link #resolveDeath()} (the player picks respawn, menu or quit). */
    public boolean deathHold;
    public String deathReason() { return dyingReason; }

    /** Ends a held death: the raid resolves as KILLED on this call. */
    public void resolveDeath() { if (player.dead && debrief.outcome == Debrief.Outcome.NONE) finishDeath(dyingReason.isEmpty() ? "Killed in action" : dyingReason); }
    private float alifeTimer;
    /** Smoke columns left by off-screen fighting (A-Life evidence): {x, y, z, the emitter's running remainder}. 1.1: each is a smoulder on the ground - ash, embers, a glow - and its smoke is the particle system's. */
    public final List<float[]> smoke = new ArrayList<>();
    /** How far off a column of smoke is still made (it can be seen a long way), and puffs of it made this operation (gates). */
    public static float SMOKE_WITHIN = 190f;
    public long smokePuffs;

    /** A smoulder at a place on the ground: what burnt there is ash and rubble now, and smoke stands over it. */
    public void smoulder(float x, int y, float z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        // the burnt patch: the ground itself turned to ash where it was soil or paving (never a floor indoors, a road marking, a container)
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (Math.abs(dx) + Math.abs(dz) == 2 && ((bx + dx) * 7 + (bz + dz) * 13 & 3) != 0) continue;
            Block g = world.block(bx + dx, y - 1, bz + dz);
            if (g == null || !g.solid() || g.shape != absentialis.world.Shapes.CUBE || g.is(Block.CONTAINER) || g.is(Block.DOOR) || world.solid(bx + dx, y, bz + dz)) continue;
            if (g.mat == Material.GRASS || g.mat == Material.DIRT || g.mat == Material.ASPHALT || g.mat == Material.CONCRETE) world.set(bx + dx, y - 1, bz + dz, Block.ASH);
        }
        smoke.add(new float[]{x, y, z, 0});
        if (decals != null) decals.add(absentialis.engine.decal.DecalAtlas.SCORCH, x, y, z, 0, 1, 0, 3.4f, rng.nextFloat() * 6.28f, 1e6f, 0xFFFFFF, 0.9f);
    }

    private void smokeColumns(float dt) {
        if (smoke.isEmpty()) return;
        long before = fx.spawned;
        for (float[] sm : smoke) {
            float dx = sm[0] - focusX, dz = sm[2] - focusZ;
            if (dx * dx + dz * dz > SMOKE_WITHIN * SMOKE_WITHIN) continue;
            fx.smokeColumn(sm[0], sm[1], sm[2], 1f, dt, sm, 3);
        }
        smokePuffs += fx.spawned - before;
    }
    public static final int EXTORT_PRICE = 200;
    /** Quality gates set this so every run of the suite sees the same dice (null in play: each raid is its own). */
    public static Long fixedSeed;
    private static long raidSerial;

    /**
     * 0.7, deterministic replay: where the two process-wide counters stand ({raid serial, next actor id}), and a way to
     * put them back. A raid built from the same profile seed, the same fixed seed and the same counters is the same raid:
     * the same dice, the same ids, and so - given the same inputs - the same decisions (NeuralAI.md, section 15).
     */
    public static long[] replayPoint() { return new long[]{raidSerial, Actor.nextId()}; }
    public static void replayFrom(long[] point) { raidSerial = point[0]; Actor.nextId((int) point[1]); }
    public float lastPlayerShot = -99;
    /** The player is reloading, treating a wound or off balance until this raid time: the moment opponents wait for. */
    public float playerVulnerableUntil = -99;
    /** Someone has hold of the player until this raid time (movement all but stops until they are hit or it runs out). */
    public float grabUntil = -99;
    public int grabbedBy;
    private float lastMeleeOn = -99, lastMeleeBy;
    /** Noise-propagation service counters: events that crossed the response threshold, infected moved, squads sent. */
    public int noiseEvents, migrations, dispatches, trespasses, scripPaid;
    public final Map<Integer, Container> containers = new HashMap<>();
    public final List<ExitState> exits = new ArrayList<>();
    public final Debrief debrief = new Debrief();
    public final String raidId;
    public final MapData.Point insertion;
    public float time, duration, extractProgress;
    public ExitState extracting;
    public boolean playerFlashlight, god, noclip, playerAiming;
    /** 0.8, Developer Mode: there is no player character in the world - nothing sees, hears, shoots at or bumps into the camera. */
    public boolean devGhost;

    /** 0.6: light comes from a thing. 2 = a flashlight on the weapon in hand, 1 = the hand torch carried in the case, 0 = none. */
    public int lightSource(absentialis.gameplay.items.ItemInstance w) {
        if (w != null && w.hasLight()) return 2;
        return profile != null && profile.loadout.count("tool.flashlight") > 0 ? 1 : 0;
    }
    /** A visible laser or weapon light is on: the player stands out in the dark. */
    public boolean playerLaser;
    /** Screen white-out from a flash grenade (1 = blind), decays over a few seconds. */
    public float flashBlind;
    public boolean aiDebug, noiseDebug;
    public float muzzleFlash, damageShake;
    public final List<float[]> hitMarkers = new ArrayList<>(); // {dirAngle, time}
    /** 1.2: false once the native core has refused this operation's world (or there is none): routes are then searched in Java. */
    public boolean nativeOk = true;
    /** 1.2: how far off (blocks) a person can make the survivor out and will fire on them. Nobody on two legs engages from further. */
    public static float HUMAN_ENGAGE_RANGE = 12f;
    /** 1.3: one armed person in this many has a dog at heel (it was one in eight); there are no stray dogs. */
    public static int DOG_OWNER_EVERY = 14;
    /**
     * 1.2, blocking: the survivor has their guard up (the aim button, with empty hands or a blade), and with what. A guard
     * takes a little off everything that lands on them while it is up: {@link #BLOCK_BARE} with bare hands, {@link #BLOCK_BLADE} with a knife.
     */
    public boolean playerBlocking, blockWithBlade;
    public static float BLOCK_BARE = 0.03f, BLOCK_BLADE = 0.05f;
    public int blocksTaken;
    private float lastBlockCue = -99;
    /** 1.2: when this operation last ran the native core's tick: a body is only made to wait on the engine for a route while that is happening. */
    public float linkFlushedAt = -99;
    public interface Listener {
        void message(String text, int color);
        void caption(String text, float x, float z, float loud);
        default void footstep(Actor a, String material, float speed) {}
        /** A spoken callout: the named voice sample, at the speaker. */
        default void voice(Actor a, String sample) {}
    }
    public Listener listener;
    private final Map<Integer, int[]> cells = new HashMap<>(); // cell -> {infected, military, raiders, survivors, mercenaries}
    private final float[] heat = new float[CELLS * CELLS];   // accumulated loud noise per strategic cell
    private float squadTimer, zoneTimer;
    private final java.util.Set<Integer> activeCells = new java.util.HashSet<>();
    private float cellTimer, discoverTimer, envTimer, radioTimer;
    private boolean warned10, warned3, closedAnnounced;
    /** False disables strategic-cell population (isolated perception tests). */
    public boolean populate = true;
    /** Non-player deaths this raid (population audit: living + dead is conserved). */
    public int deaths;
    public static final int CELL = 64, CELLS = World.W / CELL;

    // ------------------------------------------------------------------ records
    public static final class Projectile {
        public float x, y, z, vx, vy, vz, px, py, pz, damage, power, age;
        /** Effective range and muzzle position: damage falls away past the weapon's range. */
        public float range = 9999, ox, oy, oz;
        /** Where the barrel was pointed (before spread): a charge of shot is judged by where its pattern was centred. */
        public float ax, ay, az;
        public int owner;
        /** 0.9: the body on the ground this round last went into (it moves a body once, not once a step on the way through). */
        public int struckBody;
        public String ownerFaction, weaponId;
        public ItemDef ammo;
        public boolean tracer, dead;
        /** The last actor this round passed close to without hitting (each is startled once per round). */
        public int nearId;
    }
    public static final class Thrown { public float x, y, z, vx, vy, vz, fuse; public ItemDef def; public int owner; public boolean armed, dead, debris, impact, carried; public float warnAt; }
    /** Area denial left on the ground: fat-infected bile ("acid") or the contamination of a rupture ("contam"). */
    public static final class Zone { public float x, y, z, radius, until, dps, tick; public String kind; public int owner; }
    /**
     * A group that moves and decides together. Holds the shared tactical picture (last known target
     * position, not the truth), casualties for morale, and the doctrine bookkeeping.
     */
    public static final class Squad {
        public int id, targetId, initial, alive;
        public String faction;
        public float tx, tz, contactTime = -99, extortUntil;
        public boolean extortDone, paid, bloodied, backupCalled;
        /** Members who went down with nobody watching: {x, y, z, time}. The squad notices when they stop answering. */
        public final List<float[]> missing = new ArrayList<>();
        public long uid;
        public final List<float[]> route = new ArrayList<>();
        /** 0.8: what the raid itself keeps for a squad now that every mind is NeuralAI's - casualties, when it last spoke, and the rescue it is running (scene.Casualty). */
        public float ty, lastSpeech = -99, lastCasualty = -99, rescueAt, rescueScore;
        public int casualties, wounded, rescuerId, casualtyId, rescues;
    }
    public static final class WorldItem { public float x, y, z, vy; public ItemInstance it; public float age; }
    /** 0.8: the five investigations as they lie in this operation's world (sites, evidence, what the survivor has noticed). */
    /**
     * 0.8, Director Mode (absentialis.director). While a session is running: nobody treats the controlled character as
     * a target ({@link #unwanted} - "Wanted Status: Disabled"); the character moves at its own kind's pace; it may or
     * may not be able to drive; it is somebody's kind ("" = a person; otherwise an infected archetype, which the
     * infected leave alone and people shoot); and it cannot be killed out of the session ({@link #noDeath}).
     */
    public boolean unwanted, playerCanDrive = true, noDeath, directorSession;
    public float playerSpeedMul = 1f;
    public String playerKind = "";
    /** True when nothing in the world should react to the user: Developer Mode's camera, or a Director Mode actor nobody is after. */
    public boolean playerIgnored() { return devGhost || unwanted; }
    /** A safe, open place to stand near a point (teleports use it). {x, y, z}. */
    public float[] safeSpot(int x, int z) { return outdoorSpot(x, z); }
    public StoryDirector story;
    /** 1.0: the faction missions - the advisors and what they are asking for. */
    public ChainDirector chains;
    /** 1.0: what the teams have been told to do at a door. */
    public final SquadTasks squadTasks = new SquadTasks(this);
    /** 0.8: the spinels lodged in ceilings and under overhangs, to be shot down. */
    public CeilingSpinels spinels;
    public record Sound(float x, float y, float z, float radius, String category, int source, float time) {}
    /**
     * 0.8: a sound that is only a sound - nobody in the world reacts to it (what they react to goes through
     * {@link #emitSound}); it is what a thing sounded like. A round into brick, into a car door, into a vest. Posted
     * here, played by whoever is listening (scene.RaidScreen empties the list each frame; headless, it simply fills
     * to its cap and stops).
     */
    public record Cue(String sample, float x, float y, float z, float gain, float range, boolean voice) {}
    public final List<Cue> cues = new ArrayList<>();
    public static final int CUE_CAP = 96;
    /** Cues posted since the operation began, by sample (tests, and the audio overlay). */
    public final java.util.Map<String, Integer> cueCount = new java.util.HashMap<>();

    public void cue(String sample, float x, float y, float z, float gain, float range) { cue(sample, x, y, z, gain, range, false); }
    public void cue(String sample, float x, float y, float z, float gain, float range, boolean voice) {
        cueCount.merge(sample, 1, Integer::sum);
        if (cues.size() < CUE_CAP) cues.add(new Cue(sample, x, y, z, gain, range, voice));
    }

    /** What a round sounds like going into a material. */
    public static String impactSound(Material m) {
        return switch (m) {
            case METAL -> "impact_metal"; case THIN_METAL -> "impact_vehicle"; case WOOD -> "impact_wood"; case PLASTIC -> "impact_crate";
            case GLASS -> "impact_glass"; case FABRIC -> "impact_fabric"; case DIRT -> "impact_dirt"; case GRASS -> "impact_grass";
            case FOLIAGE -> "impact_foliage"; case WATER -> "impact_water"; case FLESH -> "impact_flesh"; case TILE -> "impact_ceramic";
            default -> "impact_concrete";
        };
    }

    /** The player's own voice: the recordings are a man's and a woman's, and the survivor is one or the other. */
    public String playerVoice(String what) { return what + (profile.appearance.female ? "_female" : "_male"); }
    public static final class Effect { public float x, y, z, vx, vy, vz, life, max, size; public int color; public boolean glow; }
    public record Telegraph(int actorId, String kind, float until) {}

    public static final class ExitState {
        public final MapData.Exit exit;
        public boolean eligible, signaled, open = true;
        public String requirement = "", status = "";
        public float signalTimer;
        ExitState(MapData.Exit e) { exit = e; }
    }

    public static final class Debrief {
        /** ABANDONED (0.7): the player called the operation off from the pause menu and walked out with what they carried - no extraction pay, nothing lost. */
        public enum Outcome { NONE, EXTRACTED, KILLED, MIA, ABANDONED }
        public Outcome outcome = Outcome.NONE;
        public String reason = "", exitName = "";
        public final Map<String, Integer> kills = new LinkedHashMap<>();
        public final List<String[]> xp = new ArrayList<>(); // {label, amount}
        public final List<String> consequences = new ArrayList<>();
        public final List<String> killedFactions = new ArrayList<>();
        public final Map<String, Float> repDelta = new LinkedHashMap<>();
        public float noise, distance;
        public int cellsRevealed, containersSearched, documents, shotsFired, hits, emergencyCost, aided, lootValue, challenges;
        /** 0.9: people and infected put down silently, from behind, with a knife. */
        public int stealthKills;
        /** 0.9: district hotspots that ended well with the survivor's help. */
        public int hotspots;
        public boolean powerRestored;
        public District deployedIn;
        public boolean resolved;
        /** 0.8: contracts paid and investigations brought out by this operation (the debrief plays their sounds). */
        public int contractsDone, missionsProven;
        /** 0.8: the things the player chose to keep when they died (instance uids; as many as their difficulty allows). */
        public final List<Long> keep = new ArrayList<>();
        /** What the death cost in cash, and what was kept (filled in when the raid resolves). */
        public long cashLost;
        public final List<String> kept = new ArrayList<>();
        public void xp(String label, int amount) { if (amount > 0) xp.add(new String[]{label, String.valueOf(amount)}); }
        public int totalXp() { int t = 0; for (String[] s : xp) t += Integer.parseInt(s[1]); return t; }
    }

    // ------------------------------------------------------------------ setup
    /** 0.6 signature (a length in minutes). 0.7 has no time limit: the figure is ignored and the operation lasts until the player leaves. */
    public Raid(World world, Profile profile, Settings settings, EventBus bus, int insertionIndex, float minutes) { this(world, profile, settings, bus, insertionIndex); }

    public Raid(World world, Profile profile, Settings settings, EventBus bus, int insertionIndex) {
        this.world = world;
        this.profile = profile;
        this.settings = settings;
        this.bus = bus;
        this.worldFx = new WorldFx(world);
        this.weather.set(absentialis.engine.weather.Weather.Kind.of(profile.zone.weather));
        this.weather.snap();
        this.rivalry = new RaidRivalry(this);
        this.raidId = "raid-" + profile.zone.day + "-" + (int) (profile.zone.hour * 60) + "-" + System.nanoTime() % 100000;
        this.rng = new Rng(fixedSeed != null ? profile.seed ^ fixedSeed ^ (raidSerial++ * 0x9E3779B97F4A7C15L) : profile.seed ^ System.nanoTime());
        this.duration = Float.POSITIVE_INFINITY;
        this.neural = new absentialis.neuralai.runtime.NeuralAi(neuralWorld, neuralBody, absentialis.neuralai.runtime.NeuralConfig.get(), (fixedSeed != null ? fixedSeed : 0L) ^ profile.seed ^ 0x5EEDL);
        this.neural.leaderId = player.id;
        this.neural.reactionScale = difficulty() == 0 ? 1.35 : difficulty() == 2 ? 0.7 : 1;
        this.story = new StoryDirector(this);
        this.chains = new ChainDirector(this);
        List<MapData.Point> ins = world.map.insertions;
        insertion = ins.get(Math.floorMod(insertionIndex, ins.size()));
        player.isPlayer = true;
        player.faction = "player";
        player.archetype = "player";
        // 0.8: wherever the operation starts, the survivor starts tucked away - against a wall, out of the long sight lines,
        // with more than one way to go from there. (The devmap keeps its marked spots: they are where its tests begin.)
        float[] spot = world.dev ? outdoorSpot(insertion.x, insertion.z) : tuckedSpot(insertion.x, insertion.z);
        player.x = spot[0]; player.y = spot[1]; player.z = spot[2];
        player.yaw = spot.length > 3 ? spot[3] : (float) Math.atan2(-(352 - player.x), -(352 - player.z));
        this.spinels = new CeilingSpinels(this);
        // 1.1: what is always in a place (map sites of kind "item": the shotgun in the Halloran house, the Desert Eagle at the station)
        for (MapData.Point sp : world.map.sites) {
            if (!"item".equals(sp.kind)) continue;
            boolean box = sp.name.endsWith("*");
            String id = box ? sp.name.substring(0, sp.name.length() - 1) : sp.name;
            int at = id.indexOf('x');
            int n = 1;
            if (at > 0 && id.substring(at + 1).chars().allMatch(Character::isDigit) && Content.def(id) == null) { n = Integer.parseInt(id.substring(at + 1)); id = id.substring(0, at); }
            if (Content.def(id) == null) continue;
            ItemInstance it = new ItemInstance(id);
            it.origin = raidId; it.foundInRaid = true;
            if (it.def().weapon != null && !it.def().weapon.melee()) { it.rounds = it.capacity(); }
            else if (box) it.qty = it.def().maxStack;
            else it.qty = Math.max(1, Math.min(it.def().maxStack, n));
            dropItem(it, sp.x + 0.5f, sp.y + 0.05f, sp.z + 0.5f);
            fixedItems++;
        }
        player.maxHp = combat.maxHealth(profile.herbs);
        player.hp = Math.min(player.maxHp, Math.max(35, profile.health));
        player.name = profile.name == null ? "" : profile.name;
        body.energy = profile.energy;
        body.hydration = profile.hydration;
        body.staminaDrainMul = 1 + profile.loadout.charm("stamina");
        debrief.deployedIn = world.district(player.x, player.z);
        applyArmor();
        actors.add(player);
        deploySquad();
        for (MapData.Exit e : world.map.exits) exits.add(new ExitState(e));
        evaluateExits();
        seedCells();
        if (System.getenv("ABS_TEST_NOPOP") != null || world.dev) populate = false;      // the devmap is empty until the console fills it
        seedEvidence();
        profile.discover(player.x, player.z, 3);
        if (world.map.fires == null) world.scanFires();
        for (int[] b : world.map.fires) fire.bind(b[0], b[1], b[2], 0.42f);
        if (populate && System.getenv("ABS_TEST_NOVEHICLES") == null) vehicles.populate(profile.deployVehicle);
        doors.scan();
    }

    private float burnMsg = -99;

    /** Heat from a fire: damage with no bullet behind it. The player is told once in a while; agents flinch and can die of it. */
    public void burnActor(Actor a, float dmg, int owner) {
        if (a.dead || a.isPlayer && god) return;
        a.hp -= dmg * (a.isPlayer ? difficultyDamage() : 1);
        a.hurtFlash = 0.15f;
        if (a.tier == 0) gore.burn(a, dmg);
        if (a.isPlayer) {
            damageShake = Math.min(1, damageShake + 0.2f);
            body.healthChanged = time;
            if (time - burnMsg > 3) { burnMsg = time; msg("You are burning - move!", 0xFFE2A034); }
            return;
        }
        a.staggerUntil = Math.max(a.staggerUntil, time + 0.4f);
        a.wakeUntil = time + 60;
        if (a.hp <= 0) { a.hitRegion = "torso"; a.hitForce = 6; finish(a, actor(owner), "burn"); }
    }

    /** Nearest standable column under open sky (insertions must not drop you inside a building). */
    float[] outdoorSpot(int x0, int z0) {
        for (int pass = 0; pass < 2; pass++) for (int r = 0; r < 48; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            int x = x0 + dx, z = z0 + dz;
            int y = groundY(x, z);
            if (y < 0 || world.sky(x, y + 1, z) < 15 || world.heightAt(x, z) >= y + 1) continue;
            if (pass == 0 && y > World.GROUND + 1) continue; // street level first, rooftops only as a fallback
            if (!Physics.boxFree(world, x + 0.5f, y, z + 0.5f, 0.35f, 1.8f)) continue;
            return new float[]{x + 0.5f, y, z + 0.5f};
        }
        return new float[]{x0 + 0.5f, Math.max(World.GROUND, groundY(x0, z0)), z0 + 0.5f};
    }

    /** How far from the insertion point a starting place is looked for, how far a sight line is followed, and how far a way out has to run to count. */
    public static int TUCK_RADIUS = 24;
    public static float TUCK_SIGHT = 32f, TUCK_WAY = 9f;
    /** What the last start was scored on: {walls within two blocks (of 4 sides), ways out (of 8), share of 16 directions that can be seen along, metres from the insertion}. */
    public final float[] spawnFacts = new float[4];
    /** Why places were passed over the last time a start was looked for: {not open street, no room, no wall or boxed in, no way out}. */
    public final int[] tuckWhy = new int[4];

    /** Of sixteen directions round a point at head height, the share along which nothing stands for TUCK_SIGHT metres. Fills best[0] with the yaw to be left facing. */
    private float openness(float x, float y, float z, float[] best) { return openness(x, y, z, best, Float.NaN, Float.NaN); }

    /**
     * As above; with somewhere to be going (tx, tz) the yaw chosen is, of the ways that are about as open as the most open
     * one, the one that points nearest to it - so the survivor is left looking down the street toward the operation, not
     * down the longest alley that happens to lead away from it.
     */
    private float openness(float x, float y, float z, float[] best, float tx, float tz) {
        int open = 0;
        float longest = -1;
        float[] reachOf = new float[16];
        for (int k = 0; k < 16; k++) {
            double ang = k * Math.PI / 8;
            float dx = (float) Math.sin(ang), dz = (float) Math.cos(ang), reach = TUCK_SIGHT;
            for (float t = 1; t <= TUCK_SIGHT; t += 1f) if (world.solid((int) Math.floor(x + dx * t), (int) Math.floor(y + 1.5f), (int) Math.floor(z + dz * t))) { reach = t; break; }
            if (reach >= TUCK_SIGHT) open++;
            reachOf[k] = reach;
            longest = Math.max(longest, reach);
        }
        if (best != null) {
            float want = Float.isNaN(tx) ? Float.NaN : (float) Math.atan2(tx - x, tz - z), bestOff = 1e9f;
            for (int k = 0; k < 16; k++) {
                if (reachOf[k] < longest * 0.8f) continue;
                double ang = k * Math.PI / 8;
                float off = Float.isNaN(want) ? k : Math.abs(absentialis.neuralai.integration.Steering.wrapPi((float) ang - want));
                if (off < bestOff) { bestOff = off; best[0] = (float) Math.atan2(-Math.sin(ang), -Math.cos(ang)); }
            }
        }
        return open / 16f;
    }

    /** Free ground cells among the eight round a cell at the same height: elbow room. */
    private int elbowRoom(int x, int y, int z) {
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            if (world.solid(x + dx, y - 1, z + dz) && !world.solid(x + dx, y, z + dz) && !world.solid(x + dx, y + 1, z + dz)) n++;
        }
        return n;
    }

    /** Times the last start had to pass over a better-scoring place because the insertion point could not be walked to from it (0.9). */
    public int tuckUnreachable;

    /**
     * Where the survivor comes in (0.8, reworked in 0.9). Near the insertion point, out of doors and on the street, the
     * place is chosen that a careful person would choose to stand while they got their bearings: something solid at
     * their back or their shoulder, few long lines of sight onto it, and several ways to walk off from it.
     *
     * 0.9: the 0.8 rule scored cover and concealment and nothing else, and the best-scoring places in a city are its
     * worst places to stand - the dead end between two buildings, the gap behind a skip, the inside of a fenced yard.
     * A start now also needs: room (at least six of the eight cells round it free - never wedged in), a wall on one or
     * two sides rather than three (an alcove is where you get trapped, not where you start), three ways out or more,
     * and - checked by walking it - a route from where it is to the insertion point itself, so it is never the wrong
     * side of a fence from the operation. The survivor is left facing the open way that leads toward it.
     * Returns {x, y, z, yaw}.
     */
    private float[] tuckedSpot(int x0, int z0) {
        java.util.Arrays.fill(tuckWhy, 0);
        tuckUnreachable = 0;
        float[] yaw = new float[1];
        List<float[]> found = new ArrayList<>();
        // strictly first; where a district has no such place near its insertion (a compound, a yard), the 0.8 rule's looser one
        for (int pass = 0; pass < 2 && found.isEmpty(); pass++) for (int dx = -TUCK_RADIUS; dx <= TUCK_RADIUS; dx++) for (int dz = -TUCK_RADIUS; dz <= TUCK_RADIUS; dz++) {
            final boolean strict = pass == 0;
            int x = x0 + dx, z = z0 + dz;
            if (!World.inBounds(x, World.GROUND, z)) continue;
            int y = groundY(x, z);
            // out of doors: nothing in the column above (the height map says so whether or not the light has been worked out yet)
            if (y < 0 || y > World.GROUND + 1 || world.heightAt(x, z) >= y + 1) { tuckWhy[0]++; continue; }
            if (!Physics.boxFree(world, x + 0.5f, y, z + 0.5f, 0.35f, 1.8f)) { tuckWhy[1]++; continue; }
            Block under = world.block(x, y - 1, z);
            if (under != null && (under.is(Block.HAZARD) || under.mat == Material.WATER)) continue;
            int room = elbowRoom(x, y, z);
            if (room < (strict ? 6 : 4)) { tuckWhy[1]++; continue; }
            // something solid and tall on how many of the four sides, within two blocks
            int walls = 0;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                boolean wall = false;
                for (int k = 1; k <= 2 && !wall; k++) wall = world.solid(x + d[0] * k, y, z + d[1] * k) && world.solid(x + d[0] * k, y + 1, z + d[1] * k);
                if (wall) walls++;
            }
            if (walls == 0 || walls >= (strict ? 3 : 4)) { tuckWhy[2]++; continue; }
            // ways out: of eight directions, those a body can simply walk along
            int ways = 0;
            for (int k = 0; k < 8; k++) { double ang = k * Math.PI / 4; if (world.nav.clear(x + 0.5f, y, z + 0.5f, x + 0.5f + (float) Math.sin(ang) * TUCK_WAY, z + 0.5f + (float) Math.cos(ang) * TUCK_WAY, false)) ways++; }
            if (ways < (strict ? 3 : 2)) { tuckWhy[3]++; continue; }
            float seen = openness(x + 0.5f, y, z + 0.5f, null), dist = (float) Math.sqrt(dx * dx + dz * dz);
            float score = (walls == 1 ? 2.2f : 2.0f) + Math.min(ways, 6) * 0.5f + (room - 6) * 0.3f - seen * (strict ? 1.6f : 4.5f) - (seen > 0.42f ? 3f : 0f) - dist * 0.05f;   // (1.0: a place most of the long lines reach is not tucked away, whatever else it has)
            found.add(new float[]{x + 0.5f, y, z + 0.5f, score, walls, ways, seen, dist});
        }
        found.sort((p1, p2) -> Float.compare(p2[3], p1[3]));
        // the best place from which the insertion point can actually be walked to (a handful are tried: each is a path search)
        float[] goal = outdoorSpot(x0, z0), best = null;
        for (int i = 0; i < Math.min(10, found.size()) && best == null; i++) {
            float[] c = found.get(i);
            float gd = (float) Math.sqrt((c[0] - goal[0]) * (c[0] - goal[0]) + (c[2] - goal[2]) * (c[2] - goal[2]));
            if (gd < 2.5f) { best = c; break; }
            int[] path = world.nav.path(c[0], c[1], c[2], goal[0], goal[2], 2600, true);
            if (path != null) { int n = path.length / 3; float ex = path[(n - 1) * 3] + 0.5f - goal[0], ez = path[(n - 1) * 3 + 2] + 0.5f - goal[2]; if (ex * ex + ez * ez < 9) best = c; }
            if (best == null) tuckUnreachable++;
        }
        // nowhere that the insertion point can be walked to from (the point itself is fenced in, or on a roof): the best place there is
        if (best == null && !found.isEmpty()) best = found.get(0);
        if (best == null) { spawnFacts[0] = 0; spawnFacts[1] = 0; spawnFacts[2] = 1; spawnFacts[3] = 0; openness(goal[0], goal[1], goal[2], yaw, World.W / 2f, World.D / 2f); return new float[]{goal[0], goal[1], goal[2], yaw[0]}; }
        // facing: the open way that leads on into the district (toward the middle of the map from an edge insertion)
        openness(best[0], best[1], best[2], yaw, World.W / 2f, World.D / 2f);
        spawnFacts[0] = best[4]; spawnFacts[1] = best[5]; spawnFacts[2] = best[6]; spawnFacts[3] = best[7];
        return new float[]{best[0], best[1], best[2], yaw[0]};
    }

    public void applyArmor() {
        ItemInstance arm = profile.loadout.slot("armor"), hel = profile.loadout.slot("head");
        boolean plate = arm != null && arm.def().armor != null && arm.def().armor.shield > 0;
        player.armorClass = arm != null && arm.def().armor != null && !plate ? arm.def().armor.armorClass : 0;
        player.armorDur = arm != null && !plate ? arm.durability : 0;
        player.armorMax = arm != null && !plate ? arm.maxDurability : 0;
        // Body Armor: a pool on top of health that takes the damage first; what is left is stored on the item
        player.shield = plate ? arm.durability : 0;
        player.shieldMax = plate ? arm.def().armor.shield : 0;
        player.helmet = hel != null && hel.def().armor != null && hel.def().armor.armorClass > 0;
        // 0.8: a helmet has a class of its own, armour covers the limbs it says it covers, and a pack stops something from behind
        player.helmetClass = player.helmet ? hel.def().armor.armorClass : 0;
        ItemDef.Armor aa = arm != null ? arm.def().armor : null;
        player.armorArms = aa != null && aa.regions.contains("arms");
        player.armorLegs = aa != null && aa.regions.contains("legs");
        ItemInstance pack = profile.loadout.slot("backpack");
        player.packClass = pack != null && pack.def().armor != null ? pack.def().armor.armorClass : 0;
    }

    /**
     * 0.8: the class of whatever is between a blow and the body, 0 for nothing. The vest for the torso (while it
     * lasts), and for the arms and legs if it reaches them; the helmet for the head; and for a torso hit that comes
     * from behind, the pack if it is better than the vest.
     */
    public int protection(Actor v, String region, Actor attacker) {
        boolean pool = v.shieldMax > 0;                         // a plate pool takes its damage before any of this
        int body = v.armorClass > 0 && (v.armorDur > 0 || pool) ? v.armorClass : 0;
        switch (region) {
            case "head": return v.helmet ? Math.max(1, v.helmetClass > 0 ? v.helmetClass : v.armorClass) : 0;
            case "arms": return v.armorArms ? body : 0;
            case "legs": return v.armorLegs ? body : 0;
            case "torso": {
                if (attacker != null && v.packClass > body) {
                    float dx = v.x - attacker.x, dz = v.z - attacker.z, l = (float) Math.sqrt(dx * dx + dz * dz);
                    // the way the body faces against the way the blow travels: together means it came in from behind
                    if (l > 0.01f && (-Math.sin(v.yaw) * dx - Math.cos(v.yaw) * dz) / l > 0.35f) return v.packClass;
                }
                return body;
            }
            default: return 0;
        }
    }

    /** The clock runs through the raid: two real minutes to the game hour, so a long operation crosses dusk or dawn. */
    public double hour() { return profile.zone.hour + time / 120.0; }
    public float daylight() { return ZoneState.daylightAt(hour()); }
    public boolean night() { return daylight() < 0.35f; }
    /** 0.7: there is no extraction window. Nothing counts down; the exits do not close on a clock. */
    public float remaining() { return Float.POSITIVE_INFINITY; }
    public boolean closed() { return false; }
    /** 0.8: how hard this survivor's Zone is - the profile's, chosen once (0 Easy, 1 Normal, 2 Hard). */
    public int difficulty() { return profile.difficulty; }
    public float difficultySight() { return difficulty() == 0 ? 0.75f : difficulty() == 2 ? 1.15f : 1f; }
    public float difficultyDamage() { return difficulty() == 0 ? 0.7f : difficulty() == 2 ? 1.25f : 1f; }

    /** An actor by id. Indexed (0.7): the AI asks this thousands of times a second, and it used to walk the whole list each time. */
    public Actor actor(int id) {
        Actor a = actorIndex.get(id);
        if (a != null) return a;
        for (Actor x : actors) if (x.id == id) { actorIndex.put(id, x); return x; }
        return null;
    }

    /** A standable spot at or beside a position (public face of the spawn helper: the AI asks where a body could stand). */
    public float[] standSpot(float x, float z) { return standNear(x, z); }

    public List<Actor> nearby(float x, float z, float r) {
        List<Actor> l = new ArrayList<>();
        for (Actor a : actors) if (!a.dead && !(a.isPlayer && devGhost) && Math.abs(a.x - x) < r && Math.abs(a.z - z) < r) l.add(a);
        return l;
    }

    void msg(String s, int col) { if (listener != null) listener.message(s, col); }

    // ------------------------------------------------------------------ extraction rules (GDD 16)
    public void evaluateExits() {
        double hour = profile.zone.hour;
        for (ExitState s : exits) {
            MapData.Exit e = s.exit;
            boolean ok = true;
            StringBuilder req = new StringBuilder();
            switch (e.type) {
                case PERMANENT -> req.append("Always open");
                case EMERGENCY -> { ok = true; req.append("The cordon gate: always open, at a price. Cost: $1,500 or your secured treasures"); }
                case TIME_WINDOW -> {
                    String[] w = e.condition.substring(7).split("-");
                    int a = Integer.parseInt(w[0]), b = Integer.parseInt(w[1]);
                    double h = hour + time / 60.0 * 0.5; // zone clock runs at 30x during raids
                    ok = b > 24 ? (h % 24 >= a || h % 24 < b - 24) : (h % 24 >= a && h % 24 < b);
                    req.append(String.format("Runs %02d:00-%02d:00", a, b % 24));
                }
                case SIGNALED -> { ok = s.signaled; req.append("Throw a signal flare in the zone, then hold for 60s"); }
                case CONDITIONAL -> {
                    ok = false;
                    for (String c : e.condition.split("\\|")) {
                        if (req.length() > 0) req.append(" OR ");
                        if (c.startsWith("item:")) { String id = c.substring(5); ok |= profile.loadout.count(id) > 0; req.append("Carry ").append(Content.def(id) != null ? Content.def(id).name : id); }
                        else if (c.startsWith("rep:")) { String[] p = c.split(":"); ok |= profile.rep(p[1]) >= Float.parseFloat(p[2]); req.append(ZoneState.cap(p[1])).append(" standing >= ").append(p[2]); }
                        else if (c.startsWith("pay:")) { int cost = Integer.parseInt(c.substring(4)); ok |= profile.scrip >= cost; req.append("Pay a ").append(absentialis.Version.coins(cost)).append(" toll"); }
                        else if (c.equals("power")) { ok |= profile.zone.power[District.INDUSTRIAL.ordinal()] || profile.zone.power[e.district.ordinal()]; req.append("District power restored"); }
                    }
                }
            }
            if (e.type != MapData.ExitType.EMERGENCY && closed()) ok = false;
            // a controlling hostile faction restricts its checkpoint
            if (!e.faction.isEmpty() && e.type != MapData.ExitType.EMERGENCY && profile.rep(e.faction) < -40) { ok = false; req.append(" - ").append(ZoneState.cap(e.faction)).append(" have barred you"); }
            // security escalation: a garrison that has locked its district down closes its checkpoints to anyone it has no agreement with
            if (!e.faction.isEmpty() && e.type != MapData.ExitType.EMERGENCY && rivalry != null) for (absentialis.rivalry.RivalryState.Group lg : rivalry.engine.s.active()) {
                if (lg.state("lockdown") <= 0 || !lg.territoryIds.contains(e.district.name()) || !absentialis.rivalry.Doctrine.gameKey(lg.faction).equals(e.faction) || rivalry.engine.treaty(lg, null)) continue;
                ok = false; req.append(" - locked down by ").append(lg.name);
            }
            s.eligible = ok;
            s.requirement = req.toString();
        }
    }

    // ------------------------------------------------------------------ strategic cells -> active population
    private void seedCells() {
        if (profile.agents.agents.isEmpty()) profile.agents = absentialis.sim.Agents.seed(world, profile.zone, profile.seed, profile::population);
        for (absentialis.sim.Agents.Agent g : profile.agents.agents) g.actorId = 0;
        rebuildCells();
    }

    /** The abstract counts are a view of the offline agents: rebuilt whenever agents change hands. */
    private void rebuildCells() { cells.clear(); cells.putAll(profile.agents.allCounts()); }

    private int cellOf(float x, float z) { return V(x) * CELLS + V(z); }
    private static int V(float v) { return Math.max(0, Math.min(CELLS - 1, (int) v / CELL)); }

    /** Activates cells near the player (spawning their population) and folds distant ones back. */
    private void manageCells() {
        int pcx = V(player.x), pcz = V(player.z);
        java.util.Set<Integer> want = new java.util.HashSet<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int a = pcx + dx, b = pcz + dz;
            if (a >= 0 && b >= 0 && a < CELLS && b < CELLS) want.add(a * CELLS + b);
        }
        for (Integer c : want) if (!activeCells.contains(c)) { activeCells.add(c); spawnCell(c); }
        for (Integer c : new ArrayList<>(activeCells)) {
            int a = c / CELLS, b = c % CELLS;
            if (Math.abs(a - pcx) > 2 || Math.abs(b - pcz) > 2) { activeCells.remove(c); foldCell(c); }
        }
    }

    private static final String[] POP_FACTION = {"infected", "military", "raiders", "survivors", "mercenaries"};

    /** Brings every offline agent of a cell online, each as the same individual it was before. */
    private void spawnCell(int c) {
        for (absentialis.sim.Agents.Agent ag : new ArrayList<>(profile.agents.agents)) if (ag.alive && ag.actorId == 0 && ag.cell() == c) materialize(ag, c, 40);
        rebuildCells();
    }

    /**
     * Offline -> online. The agent keeps its identity (look, weapon and kit are seeded by its id), health, home,
     * shift and squad. Refused - the agent simply stays offline a little longer - when the spot is close to the
     * player or anywhere the player could watch it appear.
     */
    private boolean materialize(absentialis.sim.Agents.Agent ag, int c, float minDist) {
        float[] p = standNear(ag.x, ag.z);
        if (p == null) return false;
        float d = player.distXZ(p[0], p[2]);
        if (d < minDist) return false;
        // 1.0, the day belongs to the living: two in five of the dead lie up out of the light until dusk (they are still
        // there - the same agents - and come out at night), so that by day the streets hold about as many people as dead
        if (ag.infected() && daylight() > 0.5f && Math.floorMod(ag.id, 5) < 2) return false;
        if (d < 75 && Physics.los(world, player.x, player.eyeY(), player.z, p[0], p[1] + 1.2f, p[2])) return false;
        Actor a = makeActor(ag.faction, p[0], p[1], p[2], new Rng(ag.id * 7919L + 17));
        if ("tank".equals(a.archetype) && d < 70) a = remake(a, "walker");
        actors.add(a);
        // 1.1: an armed person may have a dog at heel (the same people every time: it goes by who they are). 1.3: one in
        // fourteen (it was one in eight), and these are the only dogs - there are no strays
        if (!ag.infected() && Math.floorMod(ag.id, DOG_OWNER_EVERY) == 3 && a.weapon != null && !animals.owned(a.id)) animals.dogFor(a);
        a.cell = c;
        a.agentId = ag.id; ag.actorId = a.id;
        a.name = a.infected() ? "" : ag.name;
        a.female = absentialis.sim.Names.female(ag.id); a.sexKnown = true;
        a.rankLevel = ag.rank();
        rivalry.assign(a, ag.id);
        a.nightShift = ag.night;
        a.duty = ag.role;
        a.homeX = ag.homeX; a.homeZ = ag.homeZ;
        a.hp = Math.max(8, a.maxHp * ag.hp);
        if (!a.infected()) {
            a.morale = Math.min(a.morale, 0.35f + 0.65f * ag.morale);
            a.squad = ag.squad == 0 ? 500000 + (int) (ag.id % 400000) : 1 + (int) (ag.squad % 400000);
            a.role = "guard".equals(ag.role) ? "hold" : "";
            if (ag.hp < 0.6f && "survivors".equals(a.faction) && rng.chance(0.5)) a.needsHelp = true;
            Squad sq = squadOf(a.squad, a.faction);
            int members = 0;
            for (Actor m : actors) if (!m.dead && m.squad == a.squad && m.faction.equals(a.faction)) members++;
            sq.alive = members; sq.initial = Math.max(sq.initial, members);
            if ("military".equals(a.faction) && sq.route.isEmpty()) buildRoute(sq, a.homeX, a.homeZ);
        }
        a.routine = absentialis.sim.Agents.activity(ag.faction, ag.role, ag.night, hour(), ag.id);
        return true;
    }

    /** A standable street-level spot at or right beside a position, or null. */
    private float[] standNear(float x, float z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        for (int r = 0; r <= 4; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            int y = world.nav.stepY(ix + dx, iz + dz, World.GROUND, false);
            if (y < 0) y = world.nav.stepY(ix + dx, iz + dz, World.GROUND - 2, false);
            if (y >= 0 && Physics.boxFree(world, ix + dx + 0.5f, y, iz + dz + 0.5f, 0.3f, 1.8f)) return new float[]{ix + dx + 0.5f, y, iz + dz + 0.5f};
        }
        int gy = groundY(ix, iz);
        return gy < 0 ? null : new float[]{ix + 0.5f, gy, iz + 0.5f};
    }

    /** Online -> offline: the agent's position and condition are written back; the dead stay as bodies for a while. */
    private void foldCell(int c) {
        for (Actor a : new ArrayList<>(actors)) {
            if (a.isPlayer || a.mate() || a.cell != c) continue;
            if (!a.dead) release(a);
            if (!a.dead || a.deadTime > 120) { actors.remove(a); neural.remove(a.id, false); neuralBody.forget(a.id); }
        }
        rebuildCells();
    }

    private void release(Actor a) {
        absentialis.sim.Agents.Agent ag = a.agentId == 0 ? null : profile.agents.byId(a.agentId);
        if (ag == null) return;
        ag.x = a.x; ag.z = a.z;
        ag.hp = Math.max(0.05f, a.hp / a.maxHp);
        ag.morale = a.infected() ? ag.morale : Math.max(0.05f, Math.min(1, a.morale));
        ag.actorId = 0;
    }

    /** Raid end: everyone still online goes back to the offline population where they stand. */
    public void releaseAll() { for (Actor a : actors) if (!a.dead && !a.isPlayer && !a.mate()) release(a); }

    /** Total living population across folded cells + live actors (tier transition audit). */
    public int populationTotal() {
        int n = 0;
        for (int[] p : cells.values()) for (int v : p) n += v;
        for (Actor a : actors) if (!a.isPlayer && !a.dead && !a.mate() && !a.directed && !a.site) n++;
        return n;
    }

    /** One of the survivor's own brought back up because it had been left far behind and could not get back (counter for the gates). */
    public int mateRegroups;

    /**
     * 1.0: the squad stays a squad. A squadmate told to follow who has been left a long way behind - the survivor drove
     * off, went down a ladder, cut through a building - and has not been able to close the distance for some seconds is
     * brought up to a place a few metres behind the survivor, but only when neither end of that is in the survivor's
     * sight: where they were, and where they come out. Orders to hold or move somewhere are left alone, and so is anyone
     * in a fight, down, or being carried by physics.
     */
    private void regroupMates() {
        for (Actor m : mates) {
            if (m.dead || m.vital != absentialis.entity.Vital.ACTIVE || m.ragdoll != null || m.seated || "engage".equals(m.state)) { m.lostT = 0; continue; }
            if (m.sqOrder != null && !"follow".equals(m.sqOrder)) { m.lostT = 0; continue; }
            float d = m.distXZ(player.x, player.z);
            if (d < 34 && Math.abs(m.y - player.y) < 9) { m.lostT = 0; continue; }
            m.lostT += 1;
            if (m.lostT < (d > 80 ? 4 : 10)) continue;
            if (Physics.los(world, player.x, player.eyeY(), player.z, m.x, m.y + 1.3f, m.z) && d < 120) continue;
            // behind the survivor, 6 to 11 m, on ground that can be stood on and walked from, out of the line of sight
            float bx = (float) Math.sin(player.yaw), bz = (float) Math.cos(player.yaw);
            for (int tries = 0; tries < 14; tries++) {
                float back = 6 + rng.nextFloat() * 5, side = rng.range(-4f, 4f);
                float x = player.x + bx * back - bz * side, z = player.z + bz * back + bx * side;
                int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
                int y = world.nav.stepY(ix, iz, Math.round(player.y), true);
                if (y < 0 || Math.abs(y - player.y) > 2.5f || !Physics.boxFree(world, ix + 0.5f, y, iz + 0.5f, m.radius, m.stand)) continue;
                if (!world.nav.clear(ix + 0.5f, y, iz + 0.5f, player.x, player.z, true) && tries < 10) continue;
                m.x = ix + 0.5f; m.y = y; m.z = iz + 0.5f; m.vx = m.vy = m.vz = 0; m.pvx = m.pvz = 0;
                m.path = null; m.hasVia = false; m.stuckTime = 0; m.lostT = 0; m.partialUntil = -99;
                m.yaw = player.yaw;
                mateRegroups++;
                break;
            }
        }
    }

    // ------------------------------------------------------------------ 1.0: lairs, night packs, where the specials live
    /** Landmarks whose lairs have been filled this operation, and the counters the gates read. */
    private final java.util.Set<String> lairsWoken = new java.util.HashSet<>();
    public int lairSpawned, packSpawned, packs;
    private float packTimer = 50f;
    /** How near the survivor has to come before a landmark's lairs are filled (they are filled once, out of sight). */
    public static float LAIR_WAKE = 95f;

    /** True at or near the landmark with this id (its lot, and a margin round it). */
    public boolean nearLandmark(String id, float x, float z, float margin) {
        for (MapData.Landmark lm : world.map.anchors) if (lm.id.equals(id) && x >= lm.x0 - margin && x <= lm.x1 + margin && z >= lm.z0 - margin && z <= lm.z1 + margin) return true;
        return false;
    }

    /**
     * The dead inside the landmarks. A landmark's lairs (MapData.lairs, marked where its rooms were drawn) are filled
     * the first time the survivor comes near: the ordinary dead in most of them, more of them after dark - and the
     * special that lives there: the Butcher in the big house (and, some operations, in the gaol's asylum wing), the Tank
     * under the reactor.
     */
    private void manageLairs() {
        if (world.map.lairs.isEmpty()) return;
        for (MapData.Point lp : world.map.lairs) {
            if (lairsWoken.contains(lp.name)) continue;
            if (player.distXZ(lp.x + 0.5f, lp.z + 0.5f) > LAIR_WAKE) continue;
            lairsWoken.add(lp.name);
            boolean dark = night();
            Rng r = new Rng(profile.seed ^ lp.name.hashCode() * 31L ^ (long) profile.zone.day * 977L);
            float pop = profile.population("all") * profile.population("infected");
            for (MapData.Point p : world.map.lairs) {
                if (!p.name.equals(lp.name)) continue;
                if (player.distXZ(p.x + 0.5f, p.z + 0.5f) < 22 && Math.abs(p.y - player.y) < 3) continue;       // never in the room the survivor is standing in
                String kind;
                // 1.1: a kennel is a lair of dogs
                if ("idog".equals(p.kind)) { if (r.chance(0.85)) { animals.pack(p.x + 0.5f, p.y, p.z + 0.5f, 2 + r.nextInt(3)); lairSpawned++; } continue; }
                switch (p.kind) {
                    case "butcher" -> { if (!r.chance("jail".equals(p.name) ? 0.35 : 0.8)) continue; kind = "butcher"; }
                    case "tank" -> kind = "tank";
                    default -> {
                        if (!r.chance(Math.min(0.95, (dark ? 0.9 : 0.62) * pop))) continue;
                        float roll = r.nextFloat();
                        kind = roll < 0.58f ? "walker" : roll < 0.84f ? "sprinter" : roll < 0.92f ? "spewer" : dark && roll > 0.97f ? "gladiator" : "bomber";
                    }
                }
                int n = "walker".equals(kind) && r.chance(dark ? 0.6 : 0.3) ? 2 : 1;
                for (int i = 0; i < n; i++) {
                    float x = p.x + 0.5f + (i == 0 ? 0 : r.range(-1.2f, 1.2f)), z = p.z + 0.5f + (i == 0 ? 0 : r.range(-1.2f, 1.2f));
                    int y = standY((int) Math.floor(x), (int) Math.floor(z), p.y);
                    if (y < 0 || Math.abs(y - p.y) > 1 || !Physics.boxFree(world, x, y, z, 0.3f, 1.8f)) { x = p.x + 0.5f; z = p.z + 0.5f; y = p.y; if (!Physics.boxFree(world, x, y, z, 0.3f, 1.8f)) continue; }
                    Actor a = makeActor("infected", x, y, z, r);
                    remake(a, kind);
                    a.look = a.faction + ":" + a.archetype;
                    a.site = true; a.homeX = x; a.homeZ = z;
                    a.cell = cellOf(x, z);
                    actors.add(a);
                    lairSpawned++;
                }
            }
        }
    }

    /**
     * Night belongs to the dead. After dark, packs come in off the streets toward where the survivor is: every minute or
     * so, a handful at a time, from somewhere out of sight - more of them, and more of the dangerous kinds among them,
     * the later it gets. By day none come: the day's dead are the ones already standing about, and the streets are
     * as much the living's as theirs.
     */
    private void nightPacks() {
        float dark = 1 - daylight();
        if (dark < 0.6f || world.dev) { packTimer = Math.min(packTimer, 40f); return; }
        packTimer -= 1f;
        if (packTimer > 0) return;
        packTimer = 38f + rng.nextFloat() * 34f;
        int near = 0;
        for (Actor a : actors) if (!a.dead && a.infected() && a.distXZ(player.x, player.z) < 90) near++;
        float pop = profile.population("all") * profile.population("infected");
        if (near >= Math.round(34 * pop)) return;
        // somewhere on a street 46-72 m off that the survivor cannot see
        for (int tries = 0; tries < 10; tries++) {
            float ang = rng.nextFloat() * 6.2832f, dist = 46 + rng.nextFloat() * 26;
            MapData.Node nd = world.map.nearestNode(player.x + (float) Math.cos(ang) * dist, player.z + (float) Math.sin(ang) * dist);
            if (nd == null || player.distXZ(nd.x, nd.z) < 40 || player.distXZ(nd.x, nd.z) > 85) continue;
            if (Physics.los(world, player.x, player.eyeY(), player.z, nd.x + 0.5f, nd.y + 1.2f, nd.z + 0.5f)) continue;
            int n = Math.max(2, Math.round((3 + rng.nextInt(5)) * pop));
            packs++;
            for (int i = 0; i < n; i++) {
                float x = nd.x + 0.5f + rng.range(-3f, 3f), z = nd.z + 0.5f + rng.range(-3f, 3f);
                int y = standY((int) Math.floor(x), (int) Math.floor(z), nd.y);
                if (y < 0 || !Physics.boxFree(world, x, y, z, 0.3f, 1.8f)) continue;
                Actor a = makeActor("infected", x, y, z, rng);
                a.site = true;
                a.cell = cellOf(x, z);
                // they are coming this way: toward where the survivor was when they set out
                a.homeX = player.x + rng.range(-8f, 8f); a.homeZ = player.z + rng.range(-8f, 8f);
                a.routine = "hunt";
                actors.add(a);
                packSpawned++;
            }
            break;
        }
    }

    private String lastSpawnKind = "";

    private float[] spawnPoint(int cx, int cz, Rng r, boolean infected, String faction) {
        List<MapData.Point> cand = new ArrayList<>();
        for (MapData.Point p : infected ? world.map.nests : world.map.posts) if (p.x / CELL == cx && p.z / CELL == cz && (infected || p.faction.equals(faction))) cand.add(p);
        if (!cand.isEmpty() && r.chance(0.75)) { MapData.Point p = r.pick(cand); lastSpawnKind = p.kind; return new float[]{p.x + r.range(-2f, 2f), p.y, p.z + r.range(-2f, 2f)}; }
        for (int tries = 0; tries < 12; tries++) {
            float x = cx * CELL + r.range(2f, CELL - 2), z = cz * CELL + r.range(2f, CELL - 2);
            MapData.Node n = world.map.nearestNode(x, z);
            if (n != null && Math.abs(n.x - x) < CELL && Math.abs(n.z - z) < CELL) return new float[]{n.x + r.range(-3f, 3f), n.y, n.z + r.range(-3f, 3f)};
        }
        return null;
    }

    /**
     * The standable cell closest to a wanted height: agents spawn on the street or the floor their
     * post is on, never on top of a lamp arm or an awning that happens to be the highest block.
     */
    public int standY(int x, int z, int near) {
        // (1.0: the floor asked for first, then the ones below it - the floor under a point is below it - and only then above)
        if (near > 1 && near < World.H - 3 && world.solid(x, near - 1, z) && !world.solid(x, near, z) && !world.solid(x, near + 1, z)) return near;
        for (int d = 1; d <= 4; d++) { int y = near - d; if (y > 1 && world.solid(x, y - 1, z) && !world.solid(x, y, z) && !world.solid(x, y + 1, z)) return y; }
        for (int d = 1; d <= 4; d++) { int y = near + d; if (y < World.H - 3 && world.solid(x, y - 1, z) && !world.solid(x, y, z) && !world.solid(x, y + 1, z)) return y; }
        return groundY(x, z);
    }

    /** When set, agents keep their bodies and animation but stop deciding (staging, screenshots). */
    public boolean freezeAI;
    /** Individual agents that have been stood still the same way (staging, gates, Director Mode's "thinking off"). */
    public final java.util.Set<Integer> frozen = new java.util.HashSet<>();
    public void freezeOnly(Actor a) { frozen.add(a.id); }
    /** 1.3, gates and scripts: a body whose mind is a script - it is moved by {@link #scriptMind} in place of its own thinking, and everything else (doors, physics, the stuck timer, animation) runs as in play. */
    public final java.util.Set<Integer> scripted = new java.util.HashSet<>();
    public java.util.function.BiConsumer<Actor, Float> scriptMind;

    /** Console / automation: spawns one agent of a faction or infected archetype at a position. */
    public Actor debugSpawn(String what, float x, float z, float nearY) {
        boolean human = java.util.List.of(ZoneState.FACTIONS).contains(what);
        // 0.8: an infected is asked for by the name of its kind - or by any of the names the game shows ("regular", "fast"). A
        // name that is no kind at all used to be built as an unarmed person in the infected's faction, who then ran away.
        if (!human) { what = Archetypes.canonical(what); if (!Archetypes.known(what) || !Archetypes.arch(what).infected()) return null; }
        int y = standY((int) Math.floor(x), (int) Math.floor(z), Math.round(nearY));
        Actor a = makeActor(human ? what : "infected", x, y, z, rng);
        if (!human) remake(a, what);
        a.look = a.faction + ":" + a.archetype;
        a.squad = human ? 900000 + what.hashCode() % 1000 : 0;
        actors.add(a);
        return a;
    }

    /**
     * 1.2 (Developer Mode): somebody of a faction in a particular trade - rifleman, marksman or shotgunner - armed for it.
     * {@link #debugSpawn} arms a faction member with whatever its kit turns up, so the trade could not be chosen.
     */
    public Actor debugSpawnRole(String faction, String role, float x, float z, float nearY) {
        Actor a = debugSpawn(faction, x, z, nearY);
        if (a == null) return null;
        String want = null;
        Map<String, List<String>> kit = Content.KITS.get(faction);
        List<String> prim = kit == null ? List.of() : kit.get("primary");
        for (int pass = 0; pass < 2 && want == null; pass++) for (String id : pass == 0 ? prim : new ArrayList<>(Content.ITEMS.keySet())) {
            ItemDef d = Content.def(id);
            if (d == null || d.weapon == null || d.weapon.melee()) continue;
            boolean shot = d.weapon.family.equals("shotgun"), long_ = id.equals("weapon.mosin") || id.equals("weapon.sr16");
            if (role.equals("shotgunner") ? shot : role.equals("marksman") ? long_ : !shot && !long_ && (pass == 0 || d.weapon.family.equals("rifle") || d.weapon.family.equals("assault"))) { want = id; break; }
        }
        if (want != null) a.weapon = armed(want, rng);
        a.archetype = role;
        shape(a);
        a.visual = null;
        a.look = a.faction + ":" + a.archetype;
        return a;
    }

    public int groundY(int x, int z) {
        for (int y = Math.min(World.H - 3, world.heightAt(x, z) + 1); y > 1; y--) {
            if (!world.solid(x, y, z) && !world.solid(x, y + 1, z) && world.solid(x, y - 1, z)) return y;
        }
        return -1;
    }

    private Actor makeActor(String faction, float x, float y, float z, Rng r) {
        Actor a = new Actor();
        a.faction = faction;
        a.x = x; a.y = y; a.z = z; a.homeX = x; a.homeZ = z;
        a.yaw = r.range(0f, 6.28f);
        if ("infected".equals(faction)) {
            // four archetypes: mostly regulars for group pressure, a few specials that change the fight
            float roll = r.nextFloat();
            boolean closing = false;
            // 0.7: seven kinds. Six in ten are the ordinary dead and two in ten run; the rest are the special infected - a Bomber or a
            // Spewer every so often, and, rarely, one of the three that end a careless operation: the Tank, the Gladiator, the Butcher
            a.archetype = roll < 0.60f ? "walker" : roll < 0.82f ? "sprinter" : roll < 0.885f ? "bomber" : roll < 0.94f ? "spewer" : roll < 0.96f ? "tank" : roll < 0.98f ? "gladiator" : "butcher";
            if (roll >= 0.94f && !r.chance(0.5)) a.archetype = "walker";
            // 1.0: after dark more of them are the dangerous kinds (about one in four, from one in six by day)
            if (time >= 0 && night() && "walker".equals(a.archetype) && r.chance(0.16)) { float k = r.nextFloat(); a.archetype = k < 0.45f ? "sprinter" : k < 0.68f ? "spewer" : k < 0.88f ? "bomber" : "gladiator"; }
            // 1.0: the Butcher is the big house's: nowhere else, unless a developer puts one there (it is a Gladiator anywhere else)
            if ("butcher".equals(a.archetype) && !nearLandmark("mansion", x, z, 30)) a.archetype = "gladiator";
            // 1.0: two that belong to a place. The Crawler is the works': among the machinery of the Industrial district, about one
            // of the dead in nine is one. The Tyrant walks the government quarter: one in twenty-five there - seldom, and never two together.
            District sd = world.district(x, z);
            if (sd == District.INDUSTRIAL && ("walker".equals(a.archetype) || "sprinter".equals(a.archetype)) && r.chance(0.11)) a.archetype = "crawler";
            if (sd == District.CIVIC && "walker".equals(a.archetype) && r.chance(0.04) && countNear("tyrant", x, z, 90) == 0) a.archetype = "tyrant";
        } else {
            Map<String, List<String>> kit = Content.KITS.get(faction);
            List<String> prim = kit == null ? List.of("weapon.akr") : kit.get("primary");
            String w = r.pick(prim);
            a.weapon = armed(w, r);
            ItemDef wd = Content.def(w);
            a.archetype = wd.weapon.family.equals("shotgun") ? "shotgunner" : w.equals("weapon.mosin") || w.equals("weapon.sr16") ? "marksman" : "rifleman";
            List<String> arm = kit == null ? List.of("") : kit.get("armor");
            String av = r.pick(arm);
            if (!av.isEmpty()) { a.armorClass = Content.def(av).armor.armorClass; a.armorDur = Content.def(av).armor.durability; a.armorMax = a.armorDur; a.look += "vest:" + av + ";"; a.wornArmor = av; }
            List<String> hd = kit == null ? List.of("") : kit.get("head");
            String hv = r.pick(hd);
            if (!hv.isEmpty()) { a.helmet = Content.def(hv).armor.armorClass > 0; a.look += "head:" + hv + ";"; a.wornHead = hv; }
            a.hostileToPlayer = Relations.initialHostility(faction, profile.rep(faction));
            a.aggressor = a.hostileToPlayer;
            Relations.initHuman(a);
        }
        shape(a);
        a.look = faction + ":" + a.archetype + ":" + a.look + (a.weapon != null ? a.weapon.defId : "");
        return a;
    }

    /** How many living bodies of an archetype are within a distance of a place. */
    public int countNear(String archetype, float x, float z, float within) {
        int n = 0;
        for (Actor o : actors) if (!o.dead && archetype.equals(o.archetype) && o.distXZ(x, z) < within) n++;
        return n;
    }

    /**
     * 1.0, the Tyrant's charge, or anything else that hits like it: the one struck is taken off their feet and thrown the way
     * the blow was going. Anyone else goes down as a ragdoll where they land; the survivor is thrown, lands hard, and is a
     * moment getting up (they do not get to act while they are down).
     */
    /**
     * 1.1: somebody sitting in a vehicle cannot be taken hold of, thrown, tackled or pulled - they are inside it. What
     * was meant for them lands on the vehicle: the panel on that side is stove in, the car is shoved, and the one inside
     * is shaken and takes a share of it (a body used to be dragged out through the roof). True when that is what happened.
     */
    public boolean struckInVehicle(Actor a, Actor v, float dmg, float dx, float dz) {
        if (v == null || !v.seated) return false;
        vehicleBlows++;
        vehicles.struck(v, a, dmg, dx, dz);
        if (!(v.isPlayer && god)) { v.hp -= dmg * 0.3f * (v.isPlayer ? difficultyDamage() : 1); v.hurtFlash = 0.2f; }
        if (v.isPlayer) { damageShake = Math.min(1, damageShake + 0.6f); body.healthChanged = time; }
        if (v.hp <= 0 && !v.dead) finish(v, a, "blunt");
        return true;
    }
    public int vehicleBlows;
    /** 1.1: ledges taken hold of and hung from, and hangs that ended in letting go. */
    public int hangs, hangDrops;

    /** 1.1: the survivor has gone out of a moving car: a body in the road at the car's speed, hurt by how fast that was. */
    public void bailedOut(Actor p, float speed) {
        float keepX = p.vx, keepY = p.vy, keepZ = p.vz;
        boolean rag = gore.ragdolls.fallRagdoll(p);
        if (rag && p.ragdoll != null) { p.ragdoll.wake(); p.ragdoll.carry(keepX, keepY, keepZ, 1f); }
        else { p.vx = keepX; p.vy = keepY; p.vz = keepZ; p.flingUntil = time + 0.8f; slipT = Math.max(slipT, 1.4f); }
        float dmg = Math.max(0, speed - VehicleSystem.BAIL_SPEED) * BAIL_DAMAGE;
        if (dmg > 0 && !god) { hitKind = "blunt"; damageActor(p, null, dmg * difficultyDamage(), null, speed > 16 ? "torso" : "legs", p.x, p.y + 0.6f, p.z); hitKind = ""; }
        damageShake = 1;
        msg(speed > 14 ? "You threw yourself out at speed" : "You jumped clear", 0xFFE2A034);
        bailRagdolls += rag ? 1 : 0;
    }
    /** Damage for each metre a second over a walking pace that the survivor left a car at; and how many of those exits Sydney took. */
    public static float BAIL_DAMAGE = 2.4f;
    public int bailRagdolls;

    public void tackle(Actor a, Actor v, float dx, float dz, float dmg) {
        if (struckInVehicle(a, v, dmg, dx, dz)) return;
        tackles++;
        v.onGround = false;
        float vh = 7.5f, vy = 3.6f;
        if (v.isPlayer) { v.vx = dx * vh; v.vz = dz * vh; v.vy = vy; v.flingUntil = time + 0.9f; slipT = Math.max(slipT, 1.6f); damageShake = 1; msg("Knocked down!", 0xFFD8483A); }
        else if (!Archetypes.huge(v.archetype)) { v.vx = dx * vh; v.vz = dz * vh; v.vy = vy; v.pvx = v.vx; v.pvz = v.vz; v.staggerUntil = 0; v.thrownAt = time; v.flingUntil = time + 1.2f; }
        hitKind = "blunt";
        float keepX = v.vx, keepY = v.vy, keepZ = v.vz;
        damageActor(v, a, dmg * (v.isPlayer ? difficultyDamage() : 1), null, "torso", v.x, v.y + 1, v.z);
        hitKind = "";
        if (!v.dead && v.ragdoll == null) { v.vx = keepX; v.vy = keepY; v.vz = keepZ; } else v.thrownAt = -99;
        throwRagdoll(v, dx, dz, 0.9f, keepX, keepY, keepZ);
        emitSound(v.x, v.y + 1, v.z, 30, "body_fall", a == null ? 0 : a.id, false);
        if (fx != null) fx.dust(v.x, v.y + 0.2f, v.z, 0, 1, 0, 10, 0.5f, 0x8A8478, 0.5f);
    }
    public int tackles, tongueHits, pounces;

    /**
     * 1.0, the Crawler's tongue: thrown at a place (it cannot see - it throws at what it heard). Whoever is at that place
     * when the barb gets there is hooked, hurt a little, and dragged most of the way to it.
     */
    public void tongue(Actor a, float tx, float ty, float tz) {
        a.tongueX = tx; a.tongueY = ty; a.tongueZ = tz; a.tongueUntil = time + 0.45f;
        emitSound(a.x, a.y + 0.6f, a.z, 26, "splash", a.id, false);
        Actor hit = null; float bd = 1.5f;
        for (Actor o : nearby(tx, tz, 3)) { if (o == a || o.dead || o.infected()) continue; float d = (float) Math.sqrt((o.x - tx) * (o.x - tx) + (o.z - tz) * (o.z - tz)); if (d < bd && Math.abs(o.y - ty) < 2.2f) { bd = d; hit = o; } }
        if (hit == null || Physics.openness(world, a.x, a.y + 0.7f, a.z, hit.x, hit.y + 1.1f, hit.z) < 0.5f) return;
        tongueHits++;
        a.tongueX = hit.x; a.tongueY = hit.y + 1.1f; a.tongueZ = hit.z;
        float dx = a.x - hit.x, dz = a.z - hit.z, l = Math.max(0.01f, (float) Math.sqrt(dx * dx + dz * dz)), pull = Math.min(9.5f, l * 2.6f);
        dx /= l; dz /= l;
        hit.onGround = false;
        hit.vx = dx * pull; hit.vz = dz * pull; hit.vy = 2.6f;
        if (hit.isPlayer) { hit.flingUntil = time + 0.55f; damageShake = Math.min(1, damageShake + 0.7f); msg("Something has hold of you!", 0xFFD8483A); }
        else { hit.pvx = hit.vx; hit.pvz = hit.vz; hit.staggerUntil = time + 0.9f; }
        hitKind = "blade";
        float keepX = hit.vx, keepY = hit.vy, keepZ = hit.vz;
        damageActor(hit, a, 9 * (hit.isPlayer ? difficultyDamage() : 1), null, "torso", hit.x, hit.y + 1.1f, hit.z);
        hitKind = "";
        if (!hit.dead && hit.ragdoll == null) { hit.vx = keepX; hit.vy = keepY; hit.vz = keepZ; }
    }

    /** Body dimensions, health and animation family for an archetype. */
    public static void shape(Actor a) {
        Archetypes.Arch ar = Archetypes.arch(a.archetype);
        a.hp = a.maxHp = ar.hp();
        a.stand = 1.8f; a.radius = 0.3f;
        switch (a.archetype) {
            case "tank" -> { a.stand = 2.6f; a.radius = 0.55f; }
            case "gladiator" -> { a.stand = 2.15f; a.radius = 0.42f; }
            case "butcher" -> { a.stand = 1.88f; a.radius = 0.45f; }
            case "tyrant" -> { a.stand = 2.42f; a.radius = 0.48f; }           // taller than anything but the Tank
            case "crawler" -> { a.stand = 1.0f; a.radius = 0.36f; }            // on all fours: low, and as long as a man is tall
            case "bomber" -> { a.stand = 1.8f; a.radius = 0.46f; }              // character height, and wide
            case "spewer" -> { a.stand = 1.98f; a.radius = 0.28f; }
            case "shambler" -> { a.radius = 0.42f; a.stand = 1.9f; }
            default -> { }
        }
        shapeAnim(a);
    }

    private static void shapeAnim(Actor a) {
        a.anim = new Animator();
        a.anim.family = Animator.familyOf(a.archetype, a.infected());
        a.anim.scale = a.infected() ? Archetypes.scale(a.archetype) : 1f;
    }

    private Actor remake(Actor a, String archetype) { a.archetype = archetype; shape(a); a.visual = null; return a; }

    private ItemInstance armed(String id, Rng r) {
        ItemInstance w = new ItemInstance(id);
        w.origin = raidId;
        w.factory = true;                       // 1.1: nobody has worked on it (the survivor's upgrades are the survivor's)
        w.setCondition(r.range(30f, 85f));
        ItemDef d = w.def();
        w.rounds = Math.max(1, d.weapon.capacity - r.nextInt(Math.max(1, d.weapon.capacity / 3)));
        w.ammoId = Content.ammoFor(d.weapon.caliber).id;
        return w;
    }

    // ------------------------------------------------------------------ update
    public void update(float dt) {
        if (debrief.outcome != Debrief.Outcome.NONE) return;
        time += dt;
        world.nav.budget = NAV_BUDGET;
        actorIndex.clear();
        if (soundLog.size() > 48 && time - soundLog.get(0).time() > 6) { int k = 0; while (k < soundLog.size() && time - soundLog.get(k).time() > 4.5f) k++; soundLog.subList(0, k).clear(); }
        neuralBody.frame(dt);
        if (story != null) story.update(dt);
        if (chains != null) chains.update(dt);
        squadTasks.update(dt);
        neural.frame(dt);
        // 1.2, Astratto native core: the world is given over on the first tick (and again if it was rebuilt), and once a
        // tick everything that has to cross does - blocks changed, doors sealed, the routes bodies asked for - in one call
        if (world.link == null && nativeOk) nativeOk = absentialis.world.NativeLink.attach(world) != null;
        if (world.link != null) { doors.sealsTo(world.link); world.link.flush(dt); linkFlushedAt = time; }
        updateBile(dt);
        cellTimer -= dt;
        if (cellTimer <= 0 && populate) { cellTimer = 1f; manageCells(); manageLairs(); nightPacks(); }
        muzzleFlash = Math.max(0, muzzleFlash - dt * 12);
        damageShake = Math.max(0, damageShake - dt * 3);
        flashBlind = Math.max(0, flashBlind - dt * 0.28f);
        hitMarkers.removeIf(h -> time - h[1] > 1.6f);
        telegraphs.removeIf(t -> time > t.until());
        timerPhases();
        squadTimer -= dt;
        if (squadTimer <= 0) { squadTimer = 1f; countSquads(); missingPatrols(); regroupMates(); for (int i = 0; i < heat.length; i++) heat[i] = Math.max(0, heat[i] - 0.04f); }
        updateZones(dt);
        gore.update(dt);
        fire.update(dt);
        infection.update(dt);
        vehicles.update(dt);
        doors.update(dt);
        updateTakedown(dt);
        glass.update();
        casualty.update(dt);
        // the director's people arrive once the operation is under way (never in a staged or unpopulated raid)
        if (!encountersSpawned && populate && time > 1.5f) { encountersSpawned = true; rivalry.spawnEncounters(); }
        rivalry.update(dt);
        events.update(dt);
        horde.update(dt);
        animals.update(dt);
        if (body.herbsTaken > 0) {
            // permanent: written to the profile the moment it is eaten, whatever happens to the rest of the raid
            int before = profile.herbs;
            profile.herbs = Math.min(combat.herbCap(), profile.herbs + body.herbsTaken);
            body.herbsTaken = 0;
            float gain = combat.maxHealth(profile.herbs) - player.maxHp;
            if (gain > 0) { player.maxHp += gain; player.hp = Math.min(player.maxHp, player.hp + gain); msg("Maximum health +" + Math.round(gain) + "  (" + Math.round(player.maxHp) + ")", 0xFFE0C040); }
            else if (profile.herbs == before) msg("Your body can take no more from the herb - maximum health is at its limit", 0xFFE2A034);
            body.healthChanged = time;
        }
        slipT = Math.max(0, slipT - dt);
        falls(dt);
        // offline agents keep living their day: a quarter of a game hour every thirty seconds
        alifeTimer += dt;
        if (alifeTimer >= 30 && populate) {
            alifeTimer = 0;
            profile.agents.step(world, profile.zone, hour(), 0.25f, rng, activeCells::contains);
            rebuildCells();
        }
        if (player.anim == null) player.anim = new Animator();
        player.anim.blocking = playerBlocking && !player.dead; player.anim.blockBlade = blockWithBlade;
        player.anim.update(player, dt, player.weapon != null, playerAiming, false);
        if (player.dead) lootTheDead(dt);
        // agents: active tier full rate, regional tier at reduced rate (no spikes on transition)
        for (Actor a : new ArrayList<>(actors)) {
            if (a.isPlayer) { if (a.dead && a.ragdoll != null && !a.ragdoll.settled) a.ragdoll.update(world, dt); continue; }   // (alive and under physics - a long fall - the ragdoll controller has the body)
            if (a.dead) {
                a.deadTime += dt;
                // 1.0: the dead do not lie there for good. A body that has been down a while goes once the survivor is well away
                // from it; one they are standing by is left a good deal longer; none outlasts three times the wait.
                if (a.deadTime > CORPSE_SECONDS && !a.mate() && a.eaten < 1f) { float cd = a.dist(player); if (cd > CORPSE_NEAR || a.deadTime > CORPSE_SECONDS * 3 && cd > 4f) { clearCorpse(a); continue; } }
                if (a.ragdoll != null && !a.ragdoll.settled) {
                    a.ragdoll.update(world, dt);
                    a.x = a.ragdoll.x[Ragdoll.PELVIS]; a.z = a.ragdoll.z[Ragdoll.PELVIS]; a.y = Math.max(0, a.ragdoll.lowestY());
                }
                continue;
            }
            if (a.ragdoll != null) continue;   // knocked down: physics has the body until it gets up (RagdollController)
            if (a.injurySeverity > 0) a.injurySeverity = Math.max(0, a.injurySeverity - dt * 0.03f);
            float d = a.distXZ(player.x, player.z);
            a.tier = a.mate() || d < 80 ? 0 : 1;
            if (a.tier == 1) {
                a.regionalAccum += dt;
                if (a.regionalAccum < 0.5f) continue;
                float rdt = a.regionalAccum;
                a.regionalAccum = 0;
                think(a, rdt);
                a.vx *= 0.5f; a.vz *= 0.5f;
                Physics.move(world, a, rdt * 0.5f);
                continue;
            }
            if (a.seated) {                    // riding in a vehicle: the vehicle system carries the body - but the body still has to sit down in it
                if (a.anim == null) shapeAnim(a);
                a.anim.update(a, dt, a.weapon != null, false, false);
                continue;
            }
            if (a.thrownAt > -50) {
                // 0.7: thrown by a Tank. A body in the air goes where it was thrown - no mind, no legs - and where it comes down it
                // comes down in a heap, with whatever speed it still had
                float tvx = a.vx, tvz = a.vz;
                a.pvx = a.vx; a.pvz = a.vz;
                boolean wall = Physics.move(world, a, dt);
                if (a.anim != null) a.anim.update(a, dt, false, false, false);
                if (a.onGround && time - a.thrownAt > 0.12f || wall || time - a.thrownAt > 2.5f) {
                    a.thrownAt = -99; a.flingUntil = time;
                    float l = (float) Math.sqrt(tvx * tvx + tvz * tvz);
                    landedAt[0] = a.x; landedAt[1] = a.z;
                    a.vx = tvx * 0.12f; a.vz = tvz * 0.12f;
                    gore.ragdolls.knockDown(a, l < 0.01f ? 0 : tvx / l, l < 0.01f ? 0 : tvz / l, 0, 3.0f);
                    hitKind = "blunt";
                    damageActor(a, null, 14, null, "torso", a.x, a.y + 0.6f, a.z);
                    hitKind = "";
                }
                continue;
            }
            // 0.5 cleared this after the mind had set it, so no agent ever held a ladder: it is cleared before thinking
            a.wantClimb = false;
            if (scriptMind != null && scripted.contains(a.id)) scriptMind.accept(a, dt);
            else if (!freezeAI && (frozen.isEmpty() || !frozen.contains(a.id))) think(a, dt); else { a.vx *= 0.6f; a.vz *= 0.6f; }
            if (a.dead) continue;
            // movement has weight: velocity eases toward what the mind asked for instead of snapping to it
            float acc = Math.min(1, dt * (a.infected() ? 9f : 12f));
            a.vx = a.pvx + (a.vx - a.pvx) * acc; a.vz = a.pvz + (a.vz - a.pvz) * acc;
            a.pvx = a.vx; a.pvz = a.vz;
            if (time < a.staggerUntil) { a.vx *= 0.35f; a.vz *= 0.35f; }
            if (a.speedMul < 1) { a.vx *= a.speedMul; a.vz *= a.speedMul; }   // crippled or crawling
            if (!a.infected() && a.vital == absentialis.entity.Vital.ACTIVE) { float k = combat.speed[combat.condition(a.hp, a.maxHp).ordinal()]; a.vx *= k; a.vz *= k; }
            if (a.cling) { absentialis.neuralai.integration.Creatures.clingMove(this, a, dt); }                                 // 1.0: a Crawler on a ceiling: the body is up there
            else if (Physics.mantleStep(world, a, dt)) a.stuckTime = 0;                              // 0.9: hauling itself up onto a ledge
            else if (Physics.move(world, a, dt)) a.stuckTime += dt; else a.stuckTime = Math.max(0, a.stuckTime - dt);
            // 1.1: a landing too hard to stand: the legs go and the body is Sydney's (it used to stop dead on its feet from any height)
            if (a.onGround && a.fallSpeed > HARD_LANDING && a.ragdoll == null) {
                float fs = a.fallSpeed;
                a.fallSpeed = 0;
                hitKind = "blunt";
                damageActor(a, null, (fs - HARD_LANDING) * 6f, null, "legs", a.x, a.y + 0.3f, a.z);
                hitKind = "";
                if (!a.dead && a.ragdoll == null) gore.ragdolls.knockDown(a, a.vx * 0.2f, a.vz * 0.2f, 40 + fs * 2, 1.2f + Math.min(2.5f, (fs - HARD_LANDING) * 0.2f));
                hardLandings++;
                continue;
            }
            a.fallSpeed = 0;
            float sp = (float) Math.sqrt(a.vx * a.vx + a.vz * a.vz);
            a.walkPhase += sp * dt * 2.2f;
            if (a.anim == null) shapeAnim(a);
            boolean eng = "engage".equals(a.state) || "extort".equals(a.state) || "warn".equals(a.state) || "search".equals(a.state) || "alert".equals(a.state);
            float before = a.anim.phase;
            a.anim.update(a, dt, a.weapon != null, eng && !a.infected(), eng);
            // footfalls: one per half cycle, on the material actually underfoot
            if (a.onGround && sp > 0.4f && d < 45 && (before < 0.5f) != (a.anim.phase < 0.5f)) footstep(a, sp);
            if (a.y < -10) a.dead = true;
            // infected footsteps & groans are audible cues
            if (a.infected() && rng.chance(dt * 0.08)) emitSound(a.x, a.y + 1, a.z, 10, "groan", a.id, false);
        }
        separate();
        updateProjectiles(dt);
        updateThrown(dt);
        for (WorldItem w : items) {
            w.age += dt;
            w.vy -= Physics.GRAVITY * dt;
            float ny = w.y + w.vy * dt;
            if (world.solid((int) Math.floor(w.x), (int) Math.floor(ny), (int) Math.floor(w.z))) { w.vy = 0; w.y = (float) Math.floor(ny) + 1; }
            else w.y = ny;
        }
        // 1.0: the weather moves on, makes its rain or snow round whoever is looking, and the particle system integrates everything in the air
        worldFx.daylight = daylight();
        weather.set(absentialis.engine.weather.Weather.Kind.of(profile.zone.weather));
        if (!focusSet) { focusX = player.x; focusY = player.eyeY(); focusZ = player.z; }
        weather.update(dt, focusX, focusY, focusZ, fx, worldFx);
        smokeColumns(dt);
        fx.update(dt, worldFx);
        if (decals != null) decals.time = time;
        sounds.removeIf(s -> time - s.time() > 4);
        environment(dt);
        extraction(dt);
        if (player.hp <= 0 && !god && !player.dead) {
            // a blow that would kill may put the player down instead; anything slower (blood, hunger, fever, fire) simply kills
            Actor by = actor(player.lastAttacker);
            boolean blow = time - player.lastHitTime < 0.35f;
            if (!(blow && casualty.lethal(player, by, -player.hp, player.hitRegion, player.lastHitKind, Gore.lost(player, absentialis.character.Rig.HEAD))))
                die(player.vital != absentialis.entity.Vital.ACTIVE ? "Bled out" + (by != null && by != player ? " - put down by " + describe(by) : "") : "Killed in action" + (by != null && by != player ? " by " + describe(by) : ""));
        }
        if (dying > 0 && !deathHold) { dying -= dt; if (dying <= 0) finishDeath(dyingReason); }
    }

    /** 1.1: NPCs that came down too hard to stay on their feet. */
    public int hardLandings;
    /** Minds that threw this raid, and the first fault (shown by the console's `ai faults`). */
    public int aiFaults;
    public String aiFaultText = "";

    /**
     * One character's mind, for one frame (0.8). Everybody thinks in NeuralAI; this is the one call into it. A fault in
     * one mind is that mind's fault and nobody else's: it is written down, the mind is thrown away and started again
     * from nothing on the next frame, and the operation goes on. (0.7 let any exception in any NPC's decision end the
     * game - which is what the crashes downtown were.)
     */
    private void think(Actor a, float dt) {
        if (a == corpseLooter && driveLooter(a, dt)) return;
        // 1.1: overrun - more than six of the dead for every one of their own. The mind is not asked: they run.
        if (time < a.overrunUntil && !a.infected() && horde.flee(a, dt)) return;
        try {
            NeuralHost.update(this, a, dt);
        } catch (RuntimeException e) {
            aiFaults++;
            NeuralHost.faults++;
            if (aiFaults <= 3) {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                String text = "[NeuralAI] mind " + a.id + " (" + a.faction + " " + a.archetype + ", " + a.state + ") faulted at " + String.format("%.1f", time) + " s and was restarted:\n" + sw;
                if (aiFaultText.isEmpty()) aiFaultText = text;
                System.err.println(text);
                try { java.nio.file.Files.writeString(java.nio.file.Path.of("ai-fault.log"), text + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND); } catch (java.io.IOException | RuntimeException ignored) { }
            }
            neural.remove(a.id, false);
            neuralBody.forget(a.id);
            a.vx *= 0.5f; a.vz *= 0.5f;
        }
    }

    /** Paths that may be planned in one frame, across everyone (the rest wait a frame: planning is the dearest thing an agent does). */
    public static int NAV_BUDGET = 10;

    /** Bodies take up room (0.6). True for anyone upright, in the world and under their own control. */
    private boolean solidBody(Actor a) {
        if (a.dead || a.ragdoll != null || a.seated) return false;
        if (a.isPlayer) return !noclip && !vehicles.inside() && !vehicles.busy();
        return a.tier == 0;
    }

    /** How hard a body is to shove aside: the squad gives way to its leader, nobody moves a giant. */
    private static float bodyMass(Actor a) {
        if ("tank".equals(a.archetype)) return 900;
        if ("bomber".equals(a.archetype)) return 170;
        if (a.vital != absentialis.entity.Vital.ACTIVE) return 260;
        return a.mate() ? 38 : 80;
    }

    /** Collisions resolved between characters this raid, and the deepest overlap left standing after the last pass (gates). */
    public int bodyContacts;
    public float bodyOverlap;

    /**
     * Characters do not pass through each other. Every pair of upright bodies whose cylinders overlap is pushed
     * apart along the line between them, each by a share that depends on the mass of the other, and loses the part
     * of its velocity that was carrying it into the other. A push never moves a body into a wall.
     */
    private void separate() {
        int n = actors.size();
        bodyOverlap = 0;
        for (int i = 0; i < n; i++) {
            Actor a = actors.get(i);
            if (!solidBody(a)) continue;
            for (int j = i + 1; j < n; j++) {
                Actor b = actors.get(j);
                float min = a.radius + b.radius, dx = b.x - a.x, dz = b.z - a.z;
                if (Math.abs(dx) >= min || Math.abs(dz) >= min || !solidBody(b)) continue;
                if (a.y >= b.y + b.height() || b.y >= a.y + a.height()) continue;
                float d2 = dx * dx + dz * dz;
                if (d2 >= min * min) continue;
                float d = (float) Math.sqrt(d2), pen = min - d;
                if (d < 1e-3f) { float ang = (a.id * 37 + b.id * 11) * 0.7f; dx = (float) Math.cos(ang); dz = (float) Math.sin(ang); d = 1; pen = min; }
                float nx = dx / d, nz = dz / d;
                float ma = bodyMass(a), mb = bodyMass(b), ka = mb / (ma + mb), kb = ma / (ma + mb);
                boolean movedA = nudge(a, -nx * pen * ka, -nz * pen * ka), movedB = nudge(b, nx * pen * kb, nz * pen * kb);
                // one of them is against a wall: the other takes the whole of it
                if (!movedA && movedB) nudge(b, nx * pen * ka, nz * pen * ka);
                else if (movedA && !movedB) nudge(a, -nx * pen * kb, -nz * pen * kb);
                float rel = (b.vx - a.vx) * nx + (b.vz - a.vz) * nz;
                if (rel < 0) { a.vx += nx * rel * ka; a.vz += nz * rel * ka; b.vx -= nx * rel * kb; b.vz -= nz * rel * kb; }
                bodyContacts++;
                float left = min - (float) Math.sqrt((b.x - a.x) * (b.x - a.x) + (b.z - a.z) * (b.z - a.z));
                if (left > bodyOverlap) bodyOverlap = left;
            }
        }
    }

    private boolean nudge(Actor a, float dx, float dz) {
        if (Physics.bodyFree(world, a.x + dx, a.y, a.z + dz, a.radius, a.height())) { a.x += dx; a.z += dz; return true; }
        if (Physics.bodyFree(world, a.x + dx, a.y, a.z, a.radius, a.height())) { a.x += dx; return true; }
        if (Physics.bodyFree(world, a.x, a.y, a.z + dz, a.radius, a.height())) { a.z += dz; return true; }
        return false;
    }

    public static String describe(Actor a) { return a.infected() ? "a " + a.archetype : "a " + ("military".equals(a.faction) ? "military" : ZoneState.one(a.faction)) + " " + a.archetype; }

    /** 0.7: the clock that closed the cordon is gone; what is left of this is the radio's own pacing. */
    private void timerPhases() { radioTimer -= 1 / 60f; }

    private void radio(String text) {
        emitSound(player.x, player.y + 1.5f, player.z, 0, "radio", player.id, true);
        msg("RADIO: " + text, 0xFFE2A034);
    }

    /** Closing-phase escalation: infected migrate toward the player's area (redistribution, not magic spawns). */
    private void escalate(int stage) {
        int pc = cellOf(player.x, player.z);
        for (Map.Entry<Integer, int[]> e : new ArrayList<>(cells.entrySet())) if (e.getKey() != pc && e.getValue()[0] > 1) for (int k = 0; k < stage; k++) profile.agents.shift(e.getKey(), pc, "infected", rng);
        rebuildCells();
    }

    private void environment(float dt) {
        envTimer -= dt;
        discoverTimer -= dt;
        if (discoverTimer <= 0) {
            discoverTimer = 0.5f;
            int n = profile.discover(player.x, player.z, 3);
            if (n > 0) { debrief.cellsRevealed += n; profile.contracts.onExplore(world.district(player.x, player.z), n); }
            evaluateExits();
        }
        // wilderness radiation: the further out, the worse
        float out = world.exposure(player.x, player.z);
        float radiation = world.radiation(player.x, player.z);
        boolean mask = profile.loadout.slot("face") != null && profile.loadout.slot("face").def().armor != null && profile.loadout.slot("face").def().armor.gasMask
                || profile.loadout.slot("head") != null && profile.loadout.slot("head").def().armor != null && profile.loadout.slot("head").def().armor.gasMask;   // 0.8: a sealed helmet filters too
        if (radiation > 0 && envTimer <= 0) { envTimer = 1.2f; emitSound(player.x, player.y, player.z, 0, "geiger", player.id, true); }
        if (out > 10) player.hp -= dt * out * 0.02f; // the Zone pushes back
        Block feet = world.block((int) Math.floor(player.x), (int) Math.floor(player.y - 0.2f), (int) Math.floor(player.z));
        Block in = world.block((int) Math.floor(player.x), (int) Math.floor(player.y + 0.2f), (int) Math.floor(player.z));
        if ((feet != null && feet.is(Block.HAZARD)) || (in != null && in.is(Block.HAZARD))) { player.hp -= dt * 4; radiation += 1.5f; }
        player.radius = 0.3f;
        float weaponMass = player.weapon == null ? 0 : player.weapon.mass();
        body.update(player, dt, time, Math.abs(player.vx) + Math.abs(player.vz) > 0.3f, player.sprinting, playerAiming, weaponMass, radiation, mask, difficulty() == 2 ? 1.4f : difficulty() == 0 ? 0.7f : 1f);
        // passive weapon decay in rain / sewer water
        boolean sewer = player.y < World.GROUND - 2;
        float env = WeaponService.environmentWear(profile.zone.weatherRain > 0.5f && world.sky((int) player.x, (int) player.eyeY(), (int) player.z) > 12, sewer, out > 0);
        for (String s : new String[]{"primary", "sidearm"}) { ItemInstance w = profile.loadout.slot(s); if (w != null) WeaponService.wearCarried(w, dt, env); }
    }

    public float environmentWear() {
        boolean sewer = player.y < World.GROUND - 2;
        return WeaponService.environmentWear(profile.zone.weatherRain > 0.5f, sewer, world.exposure(player.x, player.z) > 0);
    }

    // ------------------------------------------------------------------ extraction
    public ExitState exitAt(float x, float y, float z) {
        for (ExitState s : exits) {
            MapData.Exit e = s.exit;
            float dx = x - (e.x + 0.5f), dz = z - (e.z + 0.5f);
            if (dx * dx + dz * dz < e.radius * e.radius && Math.abs(y - e.y) < 4) return s;
        }
        return null;
    }

    private void extraction(float dt) {
        for (ExitState s : exits) if (s.signalTimer > 0) {
            s.signalTimer -= dt;
            if (s.signalTimer <= 0) { s.signaled = true; msg("Helicopter on station at " + s.exit.name + " - extraction available.", 0xFF7DBA5C); evaluateExits(); }
        }
        ExitState here = player.dead ? null : exitAt(player.x, player.y, player.z);
        if (here == null || !here.eligible) {
            if (extracting != null) msg("Extraction cancelled - left the zone", 0xFFD8483A);
            extracting = null; extractProgress = 0;
            return;
        }
        if (extracting != here) { extracting = here; extractProgress = 0; bus.emit(new Events.ExtractionStarted(here.exit.id, 8)); }
        // hostile interference delays departure
        boolean threatened = false;
        for (Actor a : nearby(player.x, player.z, 12)) if (!a.isPlayer && Relations.hostile(this, a, player) && "engage".equals(a.state)) threatened = true;
        extractProgress += dt * (threatened ? 0.25f : 1f);
        if (extractProgress >= 8f) extract(here);
    }

    private void extract(ExitState s) {
        MapData.Exit e = s.exit;
        if (e.type == MapData.ExitType.EMERGENCY) {
            if (profile.scrip >= 1500) { debrief.emergencyCost = 1500; debrief.consequences.add("Paid " + absentialis.Version.coins(1500) + " to the cordon guards"); }
            else { debrief.emergencyCost = -1; debrief.consequences.add("Could not pay the cordon - guards confiscated your treasures"); }
        }
        if (e.condition.contains("pay:") && profile.rep("military") < 0) debrief.emergencyCost = Integer.parseInt(e.condition.replaceAll(".*pay:(\\d+).*", "$1"));
        debrief.outcome = Debrief.Outcome.EXTRACTED;
        debrief.exitName = e.name;
        debrief.reason = "Extracted via " + e.name;
        bus.emit(new Events.ExtractionCompleted(e.id));
    }

    /** The player dies once. With a death camera the body ragdolls in the world first; the raid resolves when it ends. */
    public void die(String reason) {
        if (debrief.outcome != Debrief.Outcome.NONE || dying > 0 || player.dead) return;
        // a Director Mode actor is not written out of the scene: it gets up
        if (noDeath) { player.hp = player.maxHp; player.bleed = 0; msg("The actor is down - and up again. (Director Mode)", 0xFFE2A034); return; }
        cue(playerVoice("death"), player.x, player.y + 1.5f, player.z, 0.9f, 40, true);
        Actor killer = actor(player.lastAttacker);
        confirmKill(player, time - player.lastHitTime < 20 ? killer : null);
        casualty.died(player);
        player.dead = true;
        player.vx = player.vz = 0;
        dyingReason = reason;
        if (player.anim == null) shapeAnim(player);
        // the body stays in the world whatever happens next: animation hands it to the ragdoll in the pose it died in
        gore.deathBlow(player, lastKindFor(player));
        // (1.1: a body already under physics - it was falling - stays the body it is, and simply stops trying)
        if (player.ragdoll == null) startRagdoll(player);
        else { absentialis.sydney.SydneyCharacter sc = absentialis.sydney.SydneyCharacter.of(player.ragdoll); if (sc != null) sc.die(); else { player.ragdoll.driver = null; player.ragdoll.live = false; } }
        gore.onDeath(player, Math.max(30, player.hitForce), lastKindFor(player));
        if (deathCam > 0) { dying = deathCam; return; }
        finishDeath(reason);
    }

    private void finishDeath(String reason) {
        if (debrief.outcome != Debrief.Outcome.NONE) return; // death applies once
        debrief.outcome = Debrief.Outcome.KILLED;
        debrief.reason = reason;
        bus.emit(new Events.RaidFailed(reason));
    }

    /** A wounded agent ran out of blood. */
    public void bleedOut(Actor a) {
        if (a.dead) return;
        a.hitRegion = "torso"; a.hitForce = 8;
        a.hp = 0;
        finish(a, actor(a.lastAttacker), "bleed");
    }

    /** Ends a character by a named cause (blood loss, the end of a bleed-out): no incapacitation roll, one death. */
    void finish(Actor a, Actor by, String kind) {
        if (a.dead) return;
        lastKind = kind;
        a.lastHitKind = kind;
        if (a.isPlayer) { a.hp = 0; die(kind.equals("bleed") ? "Bled out" + (by != null && by != a ? " - put down by " + describe(by) : "") : "Killed in action"); return; }
        kill(a, by);
    }

    /** What a character is called on screen: its own name, or what it is. */
    public static String displayName(Actor a) {
        if (a == null) return "";
        // 0.7: the infected are nobody. Whatever they were called, they are "Infected" now - only the special kinds have a name, and it is the name of the kind
        if (a.infected()) return Archetypes.special(a.archetype) ? Archetypes.displayName(a.archetype) : "Infected";
        if (a.name != null && !a.name.isEmpty()) return a.name.contains("\"") ? a.name.substring(a.name.indexOf('"') + 1, a.name.lastIndexOf('"')) : a.name;
        if (a.isPlayer) return "You";
        return speaker(a);
    }

    /** One death, one event: who, by whom, with what. Every route to death comes through here exactly once. */
    private void confirmKill(Actor v, Actor attacker) {
        String cause = lastKindFor(v);
        String weapon = "";
        if (attacker != null && (cause.equals("bullet") || cause.equals("melee"))) weapon = hitWeaponId != null ? hitWeaponId : attacker.weapon != null ? attacker.weapon.defId : "";
        boolean friendly = attacker != null && attacker != v && (attacker.faction.equals(v.faction) || casualty.friendly(attacker, v));
        boolean ours = attacker != null && "player".equals(attacker.faction);
        bus.emit(new Events.KillConfirmed(attacker == null ? 0 : attacker.id, attacker == null ? "" : displayName(attacker), attacker == null ? "" : attacker.faction, v.id, displayName(v), v.faction,
                weapon, cause, friendly, "head".equals(v.hitRegion) && cause.equals("bullet"), ours, time));
    }

    private String lastKindFor(Actor v) { return v.lastHitKind == null || v.lastHitKind.isEmpty() ? lastKind : v.lastHitKind; }

    /** The player lost their footing in fresh blood. */
    public void playerSlip() {
        if (vehicles.inside() || vehicles.busy()) return;        // 0.8: nobody loses their footing sitting down
        slipT = 0.9f;
        damageShake = Math.min(1, damageShake + 0.45f);
        msg("You slip in the blood", 0xFFE2A034);
        emitSound(player.x, player.y, player.z, 12, "slip", player.id, false);
    }

    /**
     * Missing patrol members: a squad does not know the instant one of its own dies out of sight. After a
     * while someone notices the silence, says so, and the squad goes to look where that member was last headed.
     */
    private void missingPatrols() {
        for (Squad sq : squads.values()) {
            if (sq.missing.isEmpty()) continue;
            float[] m = sq.missing.get(0);
            if (time - m[3] < 18) continue;
            sq.missing.remove(0);
            boolean said = false;
            for (Actor a : actors) {
                if (a.dead || a.isPlayer || a.squad != sq.id || !a.faction.equals(sq.faction)) continue;
                if (!said) { say(a, rng.chance(0.5) ? "He's not answering. Something's wrong." : "We're one short - go and look.", "radio_chatter"); said = true; }
                a.morale -= 0.06f;
                a.alertness = Math.max(a.alertness, 0.7f);
                if ("idle".equals(a.state) || "investigate".equals(a.state)) { a.wakeUntil = time + 90; neural.onDispatch(a.id, new absentialis.neuralai.api.Vec3(m[0] + rng.range(-5f, 5f), m[1], m[2] + rng.range(-5f, 5f))); }
            }
        }
    }

    // ------------------------------------------------------------------ sound & perception
    /** Emits a signature; agents within its attenuated radius hear it (GDD 17.2). */
    public void emitSound(float x, float y, float z, float radius, String category, int source, boolean playerOnly) {
        if (playerIgnored() && source == player.id) return;   // a camera makes no sound, and nobody is listening for an actor who is not wanted
        sounds.add(new Sound(x, y, z, radius, category, source, time));
        soundCounts.merge(category, 1, Integer::sum);
        // the same sound, for every mind that listens: busy districts and rain take something off how far it carries
        if (!playerOnly && radius > 0) { float carry = (1 - 0.22f * world.district(x, z).def.noise) * (1 - 0.2f * rain()); soundLog.add(new absentialis.neuralai.api.GameWorld.SoundEvent(++soundSeq, new absentialis.neuralai.api.Vec3(x, y, z), radius * (radius < 12 ? 1 : carry), 1, category, source, time)); }
        if (source == player.id) debrief.noise += radius;
        if (!playerOnly) horde.noise(x, y, z, radius, category);
        if (listener != null && source != player.id) listener.caption(category, x, z, Math.max(0.1f, 1 - player.distXZ(x, z) / Math.max(1, radius * 1.5f)));
        if (playerOnly || radius <= 0) return;
        // every mind listens for itself, from the log (NeuralAI). What is left for the raid is the one thing that is the body's
        // and not the mind's: a sleeper is woken by a noise loud enough to wake it, and then stays up for a while
        for (Actor a : actors) {
            if (a.isPlayer || a.dead || a.id == source || a.infected() || !(absentialis.sim.Agents.asleep(a.routine) && time > a.wakeUntil)) continue;
            float dx = a.x - x, dy = a.y - y, dz = a.z - z;
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            // busy districts mask sound; rain dulls it further
            // at arm's length a sound is a sound; further out, busy districts and rain mask it
            float mask = d < 6 ? 1 : (1 - 0.22f * world.district(a.x, a.z).def.noise) * (1 - 0.2f * rain());
            float hearing = Archetypes.arch(a.archetype).hearing() * mask;
            float eff = radius * hearing;
            if (d > eff) continue;
            float occl = d < 6 ? 1 : 0.45f + 0.55f * Physics.openness(world, x, y, z, a.x, a.eyeY(), a.z);
            // 0.6: a floor between is more than a wall between; and someone already listening for trouble hears more of it
            absentialis.gameplay.CombatConfig pcfg = absentialis.gameplay.CombatConfig.get();
            if (Math.abs(dy) > 3.2f && d < 40 && !category.startsWith("shot") && !category.equals("explosion")) occl *= pcfg.hearStorey;
            if (a.alertness > 0.5f) occl = Math.min(1.2f, occl * pcfg.hearAlert);
            if (d > eff * occl) continue;
            if (1 - d / Math.max(1, eff * occl) >= WAKES_AT) a.wakeUntil = time + 90;
        }
        if (source == player.id) profile.zone.reportNoise(world.district(x, z), radius * 0.05f);
        noiseEvent(x, y, z, radius, category, source);
    }

    /** How loud, 0 .. 1 at the sleeper's ear, a noise has to be to wake someone. */
    public static float WAKES_AT = 0.45f;

    public void telegraph(Actor a, String kind, float dur) {
        telegraphs.add(new Telegraph(a.id, kind, time + dur));
        // every special has an audible cue AND a wind-up animation (never audio-only, never instant)
        String clip = null;
        switch (kind) {
            case "screech" -> { emitSound(a.x, a.y + 1.5f, a.z, 70, "screech", a.id, false); clip = "inf_shriek"; }
            case "shriek" -> { emitSound(a.x, a.y + 1.5f, a.z, 45, "shriek", a.id, false); clip = "inf_shriek"; }
            case "swell" -> { emitSound(a.x, a.y + 1, a.z, 18, "gurgle", a.id, false); clip = "fat_swell"; }
            case "retch" -> { emitSound(a.x, a.y + 1, a.z, 22, "retch", a.id, false); clip = "fat_retch"; }
            case "charge" -> { emitSound(a.x, a.y + 1, a.z, 50, "roar", a.id, false); clip = "giant_windup"; }
            case "throw" -> { emitSound(a.x, a.y + 1, a.z, 55, "roar", a.id, false); clip = "giant_throw"; }
            case "grab" -> { emitSound(a.x, a.y + 1, a.z, 40, "roar", a.id, false); clip = "giant_windup"; }
            case "hark" -> { emitSound(a.x, a.y + 1.6f, a.z, 38, "gladiator_roar", a.id, false); clip = "glad_hark"; }
            case "claw" -> { emitSound(a.x, a.y + 1.4f, a.z, 20, "claw_scrape", a.id, false); clip = "claw_windup"; }
            case "rev" -> { emitSound(a.x, a.y + 1.2f, a.z, 48, "chainsaw_rev", a.id, false); clip = "saw_rev"; }
            // 1.0: the Tyrant drops its shoulder before it comes; the Crawler rears and its jaw opens before the tongue or the leap
            case "tackle" -> { emitSound(a.x, a.y + 1.6f, a.z, 55, "roar", a.id, false); clip = "tyrant_set"; }
            case "tongue" -> { emitSound(a.x, a.y + 0.6f, a.z, 34, "shriek", a.id, false); clip = "crawler_rear"; }
            case "pounce" -> { emitSound(a.x, a.y + 0.6f, a.z, 30, "growl", a.id, false); clip = "crawler_rear"; }
            default -> {}
        }
        if (clip != null) { if (a.anim == null) shapeAnim(a); a.anim.play(clip, dur); }
    }

    public void cancelTelegraph(Actor a, String kind) { telegraphs.removeIf(t -> t.actorId() == a.id && t.kind().equals(kind)); if (a.anim != null) a.anim.action = ""; }

    public boolean telegraphing(Actor a, String kind) { for (Telegraph t : telegraphs) if (t.actorId() == a.id && t.kind().equals(kind)) return true; return false; }

    // ------------------------------------------------------------------ combat
    /** Spawns projectiles for one trigger pull. dir must be normalised. Returns pellets spawned. */
    public int fireProjectiles(Actor shooter, ItemInstance weapon, ItemDef ammo, float ox, float oy, float oz, float dx, float dy, float dz, float spreadDeg) {
        ItemDef.Ammo am = ammo.ammo;
        Gunsmith.Stats st = Gunsmith.stats(weapon, 0);
        float speed = Gunsmith.velocity(ammo) * st.velocityMul;
        int pellets = Math.max(1, am.pellets);
        for (int p = 0; p < pellets; p++) {
            float sp = (float) Math.toRadians(spreadDeg + am.spread * (pellets > 1 ? 1 : 0));
            float ax = dx + (float) rng.nextGaussian() * sp * 0.5f, ay = dy + (float) rng.nextGaussian() * sp * 0.5f, az = dz + (float) rng.nextGaussian() * sp * 0.5f;
            float l = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            Projectile pr = new Projectile();
            pr.x = pr.px = ox; pr.y = pr.py = oy; pr.z = pr.pz = oz;
            pr.vx = ax / l * speed; pr.vy = ay / l * speed; pr.vz = az / l * speed;
            pr.damage = am.damage * weapon.def().weapon.damageMul * st.damageMul * ("shotgun".equals(weapon.def().weapon.family) ? combat.shotgunDamage : 1f);
            pr.power = am.pen * st.penMul / 10f;
            pr.range = st.range; pr.ox = ox; pr.oy = oy; pr.oz = oz;
            pr.ax = dx; pr.ay = dy; pr.az = dz;
            pr.owner = shooter.id;
            pr.ownerFaction = shooter.faction;
            pr.weaponId = weapon.defId;
            pr.ammo = ammo;
            pr.tracer = am.tracer || shooter != player && rng.chance(0.3);
            projectiles.add(pr);
        }
        return pellets;
    }

    public void shotSignature(Actor shooter, ItemInstance weapon, Gunsmith.Stats st, float x, float y, float z) {
        float radius = st.noise * (st.suppressed ? 0.9f : 1.6f);
        boolean silent = "special".equals(weapon.def().weapon.family);   // a crossbow string is not a gunshot
        // the event first: whoever renders the shot from it has done so before the generic sound report arrives
        bus.emit(new Events.ShotFired(shooter.id, shooter.faction, x, y, z, radius, weapon.defId, st.suppressed, st.rpm, (float) -Math.sin(shooter.yaw), (float) -Math.cos(shooter.yaw)));
        emitSound(x, y, z, silent ? st.noise : radius, silent ? "bolt_release" : st.suppressed ? "shot_suppressed" : "shot", shooter.id, false);
        if (shooter == player) {
            debrief.shotsFired++;
            lastPlayerShot = time;
            // firing on a neutral faction member in view is observed aggression
            for (Actor a : nearby(x, z, 50)) if (!a.isPlayer && !a.mate() && !a.infected() && !a.hostileToPlayer && Physics.los(world, a.x, a.eyeY(), a.z, x, y, z)) a.sawPlayerAggress = true;
        }
    }

    private void updateProjectiles(float dt) {
        for (int pi = 0; pi < projectiles.size(); pi++) {   // by index: a hit can chain into anything (ruptures, blasts, debris)
            Projectile p = projectiles.get(pi);
            p.age += dt;
            if (p.dead) continue;
            p.px = p.x; p.py = p.y; p.pz = p.z;
            float rem = dt;
            while (rem > 0 && !p.dead) {
                float step = Math.min(rem, 0.6f / Math.max(1, Math.abs(p.vx) + Math.abs(p.vy) + Math.abs(p.vz)) * 30);
                rem -= step;
                p.vy -= 9.81f * step;
                float nx = p.x + p.vx * step, ny = p.y + p.vy * step, nz = p.z + p.vz * step;
                segment(p, nx, ny, nz);
                if (!p.dead) { p.x = nx; p.y = ny; p.z = nz; }
            }
            if (p.age > 2.5f || p.y < 0) p.dead = true;
        }
        projectiles.removeIf(p -> p.dead || p.age > 3);
    }

    /** Moves along one sub-segment: actor hits first, then voxel penetration/stop. */
    private void segment(Projectile p, float nx, float ny, float nz) {
        float dx = nx - p.x, dy = ny - p.y, dz = nz - p.z, len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-5f) return;
        dx /= len; dy /= len; dz /= len;
        // voxel walk along the segment
        float blockT = Float.MAX_VALUE;
        int bx = 0, by = 0, bz = 0;
        int steps = (int) (len * 4) + 1;
        for (int i = 1; i <= steps; i++) {
            float t = len * i / steps;
            int x = (int) Math.floor(p.x + dx * t), y = (int) Math.floor(p.y + dy * t), z = (int) Math.floor(p.z + dz * t);
            int id = world.get(x, y, z);
            if (id != 0 && Block.ALL[id].solid()) { blockT = t; bx = x; by = y; bz = z; break; }
        }
        // 0.8: a round that passes through a spinel in a ceiling knocks it down (and carries on)
        if (spinels != null && !spinels.stones.isEmpty()) spinels.shot(p.x, p.y, p.z, dx, dy, dz, Math.min(len, blockT));
        // 1.1: an animal in the line takes the round (a crow is a small thing to hit, and one round is the end of it)
        if (!animals.all.isEmpty() && animals.shot(p, dx, dy, dz, Math.min(len, blockT))) { p.dead = true; return; }
        // bodywork and tyres: a car is cover, and a tyre is a target
        float carT = vehicles.cars.isEmpty() ? -1 : vehicles.rayHit(p.x, p.y, p.z, dx, dy, dz, Math.min(len, blockT), carHit);
        Actor hit = null;
        float hitT = Float.MAX_VALUE;
        for (Actor a : actors) {
            if (a.dead) {
                // 0.9: a body on the ground is still a body. A round that goes into one moves the part it strikes (and the round
                // carries on: what is left of a person does not stop it); the body is simulated again until it lies still
                if (a.ragdoll != null && a.tier == 0 && time - a.diedAt > 0.12f && a.id != p.owner && Math.abs(a.x - p.x) < len + 2.5f && Math.abs(a.z - p.z) < len + 2.5f && Math.abs(a.y - p.y) < len + 2.5f) {
                    int part = a.ragdoll.struck(p.x, p.y, p.z, dx, dy, dz, Math.min(len, blockT));
                    if (part >= 0 && p.struckBody != a.id) {
                        p.struckBody = a.id;
                        float push = Math.min(4.5f, 1.2f + p.damage * 0.035f);
                        a.ragdoll.disturb(a.ragdoll.x[part], a.ragdoll.y[part], a.ragdoll.z[part], 0.45f, dx * push, 0.5f + Math.abs(dy) * push, dz * push);
                        corpseHits++;
                        cue("impact_flesh", a.ragdoll.x[part], a.ragdoll.y[part], a.ragdoll.z[part], 0.5f, 30);
                    }
                }
                // the rest of a charge of shot arrives a few milliseconds after the pellet that killed: it still strikes the
                // body, and if it strikes the head at close range the head goes with it (the kill is one blow, not eight)
                if (time - a.diedAt > 0.12f || a.id == p.owner || p.ammo == null || p.ammo.ammo.pellets <= 1 || Math.abs(a.x - p.x) > len + 2 || Math.abs(a.z - p.z) > len + 2) continue;
                float td = a.intersectBody(p.x, p.y, p.z, dx, dy, dz, len);
                if (td < 0 || td > blockT) continue;
                float hyd = p.y + dy * td;
                float trav = (float) Math.sqrt((p.x + dx * td - p.ox) * (p.x + dx * td - p.ox) + (hyd - p.oy) * (hyd - p.oy) + (p.z + dz * td - p.oz) * (p.z + dz * td - p.oz));
                if ("head".equals(a.regionAt(hyd)) && a.tier == 0 && !a.isPlayer) gore.lateHeadPellet(a, p.weaponId, trav, dx, dz);
                continue;
            }
            if (a.id == p.owner) continue;
            // squads do not cut down their own flankers: agents' rounds pass their own side (the player's never do)
            if (a.faction.equals(p.ownerFaction) && (p.owner != player.id || a.mate())) continue;
            if (Math.abs(a.x - p.x) > len + 2 || Math.abs(a.z - p.z) > len + 2) continue;
            float t = a.intersect(p.x, p.y, p.z, dx, dy, dz, len);
            if (t >= 0 && t < hitT) { hitT = t; hit = a; }
            else if (t < 0 && p.nearId != a.id) {
                // a round that passes close is felt: closest approach of this step to the head and chest
                float hx0 = a.x - p.x, hy0 = a.y + a.height() * 0.8f - p.y, hz0 = a.z - p.z;
                float along = Math.max(0, Math.min(Math.min(len, blockT), hx0 * dx + hy0 * dy + hz0 * dz));
                float cx0 = hx0 - dx * along, cy0 = hy0 - dy * along, cz0 = hz0 - dz * along;
                if (cx0 * cx0 + cy0 * cy0 + cz0 * cz0 < 1.7f * 1.7f) {
                    p.nearId = a.id;
                    if (a.isPlayer) emitSound(player.x, player.y + 1.5f, player.z, 0, "bullet_snap", p.owner, true);
                    else NeuralHost.nearMiss(this, a, p.x + dx * along, p.y + dy * along, p.z + dz * along, p.owner);
                }
            }
        }
        if (carT >= 0 && carT < hitT && !(vehicles.driving != null && p.owner == player.id && carT < 2.5f)) {
            float hx = p.x + dx * carT, hy = p.y + dy * carT, hz = p.z + dz * carT;
            float through = vehicles.bullet(carHit[0], carHit[1], hx, hy, hz, p.damage, p.power);
            impact(hx, hy, hz, Material.THIN_METAL, 5, -dx, -dy, -dz, false);
            // through the glass rather than the panel, by where on the car it landed
            if (hy - groundY((int) Math.floor(hx), (int) Math.floor(hz)) > 1.05f) cue("impact_autoglass", hx, hy, hz, 0.7f, 60);
            emitSound(hx, hy, hz, 10, "impact", p.owner, false);
            if (through <= 0) { p.dead = true; p.x = hx; p.y = hy; p.z = hz; return; }
            p.damage *= through; p.power *= through;
        }
        // a leaf standing open in the line of fire
        if (!doors.doors.isEmpty()) {
            float doorT = doors.ray(p.x, p.y, p.z, dx, dy, dz, Math.min(len, blockT), doorHit);
            if (doorT >= 0 && doorT < hitT) {
                float hx = p.x + dx * doorT, hy = p.y + dy * doorT, hz = p.z + dz * doorT;
                float through = doors.strikeOpen(doorHit[0], hx, hy, hz, p.damage, dx, dz, p.owner);
                impact(hx, hy, hz, doorHit[0].kind == Doors.METAL ? Material.METAL : doorHit[0].kind == Doors.GLASS ? Material.GLASS : Material.WOOD, 4, -dx, -dy, -dz, false);
                if (through <= 0) { p.dead = true; p.x = hx; p.y = hy; p.z = hz; return; }
                p.damage *= through; p.power *= through;
            }
        }
        Gore.Entry en = null;
        if (hit != null && hitT < blockT && hit.tier == 0) {
            // the capsule is only the broad phase: trace the round against the body's voxels in its current pose
            float flown = (float) Math.sqrt((p.x - p.ox) * (p.x - p.ox) + (p.y - p.oy) * (p.y - p.oy) + (p.z - p.oz) * (p.z - p.oz)) + hitT;
            en = gore.trace(hit, p.x, p.y, p.z, dx, dy, dz, len + 0.8f, flown);
            if (en == null || en.t > blockT) hit = null;      // between the arm and the ribs, or past the shoulder: a miss
            else hitT = en.t;
        }
        if (hit != null && hitT < blockT) {
            float hy = p.y + dy * hitT, hx = p.x + dx * hitT, hz = p.z + dz * hitT;
            rayEntry = en; rayDX = dx; rayDY = dy; rayDZ = dz; rayPower = p.power;
            float trav = (float) Math.sqrt((hx - p.ox) * (hx - p.ox) + (hy - p.oy) * (hy - p.oy) + (hz - p.oz) * (hz - p.oz));
            float falloff = trav <= p.range ? 1 : Math.max(0.4f, 1 - (trav - p.range) / Math.max(1, p.range) * 0.8f);
            hitWeaponId = p.weaponId;
            hitTravel = trav;
            // a charge of shot is one blow, centred where the barrel pointed: if that centre is on the head, the head takes it,
            // whichever pellet of the pattern happens to be the one that kills
            if (p.ammo != null && p.ammo.ammo != null && p.ammo.ammo.pellets > 1) {
                float along = (hit.x - p.ox) * p.ax + (hit.y + hit.height() * 0.9f - p.oy) * p.ay + (hit.z - p.oz) * p.az;
                float cx = p.ox + p.ax * along, cy = p.oy + p.ay * along, cz = p.oz + p.az * along, rel = (cy - hit.y) / hit.height();
                if (rel > 0.78f && rel < 1.1f && (cx - hit.x) * (cx - hit.x) + (cz - hit.z) * (cz - hit.z) < (hit.radius + 0.2f) * (hit.radius + 0.2f)) hit.headPelletAt = time;
            }
            // a shotgun inside a few metres is the heaviest thing in the Zone: the whole charge arrives together
            ItemDef pw = Content.def(p.weaponId);
            float close = pw != null && pw.weapon != null && "shotgun".equals(pw.weapon.family) && trav <= combat.shotgunCloseRange ? combat.shotgunCloseBonus : 1f;
            damageActor(hit, actor(p.owner), p.damage * falloff * close, p.ammo, hit.regionAt(hy), hx, hy, hz);
            hitWeaponId = null;
            hitTravel = 999;
            rayEntry = null;
            p.dead = true;
            p.x += dx * hitT; p.y += dy * hitT; p.z += dz * hitT;
            return;
        }
        if (blockT < Float.MAX_VALUE) {
            Block b = Block.ALL[world.get(bx, by, bz)];
            Material m = b.mat;
            float hx = p.x + dx * blockT, hy = p.y + dy * blockT, hz = p.z + dz * blockT;
            Doors.Door struck = b.is(Block.DOOR) ? doors.at(bx, by, bz) : null;
            if (struck != null) doors.hit(struck, hx, hy, hz, p.damage, dx, dz, p.owner, "bullet");      // located on the leaf: panel, hinge or latch
            else { breakDX = dx; breakDZ = dz; damageBlock(bx, by, bz, p.damage, p.owner); breakDX = breakDZ = 0; }
            // the face that was struck: the axis on which the hit lies furthest from the middle of the block
            float fnx = hx - (bx + 0.5f), fny = hy - (by + 0.5f), fnz = hz - (bz + 0.5f);
            if (Math.abs(fnx) >= Math.abs(fny) && Math.abs(fnx) >= Math.abs(fnz)) { fnx = Math.signum(fnx); fny = 0; fnz = 0; }
            else if (Math.abs(fny) >= Math.abs(fnz)) { fny = Math.signum(fny); fnx = 0; fnz = 0; }
            else { fnz = Math.signum(fnz); fnx = 0; fny = 0; }
            boolean mark = struck == null && b.shape == 0 && b.opaque() && world.get(bx, by, bz) != 0;
            if (p.power > m.resist) {
                // penetrate: lose energy proportional to material resistance
                p.power -= m.resist;
                p.damage *= Math.max(0.25f, 1 - m.resist / (p.power + m.resist + 0.01f));
                impact(hx, hy, hz, m, 3, fnx, fny, fnz, mark);
                return; // keeps flying through
            }
            impact(hx, hy, hz, m, 7, fnx, fny, fnz, mark);
            emitSound(hx, hy, hz, 8, "impact", p.owner, false);
            // rounds striking the wall beside someone pin them as surely as rounds going past
            for (Actor a : nearby(hx, hz, 2.4f)) if (!a.isPlayer && a.id != p.owner && !a.faction.equals(p.ownerFaction) && Math.abs(a.y + 1 - hy) < 2.2f) NeuralHost.nearMiss(this, a, hx, hy, hz, p.owner);
            p.dead = true;
            p.x = hx; p.y = hy; p.z = hz;
        }
    }

    /** Direction of the blow breaking a block right now (so the glass of a window leaves the frame the way it was hit). */
    public float breakDX, breakDZ;
    private final int[] carHit = new int[2];
    private final Doors.Door[] doorHit = new Doors.Door[1];
    /** The round being resolved right now: where it entered the victim's anatomy, its direction and penetrating power. */
    private Gore.Entry rayEntry;
    private float rayDX, rayDY, rayDZ, rayPower, blastX, blastY, blastZ;
    /** Set by a caller to mark the close attack being resolved as a bite or a cut (default: a blunt blow). */
    public String meleeKind = "";

    private void impact(float x, float y, float z, Material m, int n) { impact(x, y, z, m, n, 0, 1, 0, false); }

    private void breakProp(int x, int y, int z, Material m) {
        if (props == null) return;
        var in = props.remove(x, y, z);
        if (in == null) return;
        var d = absentialis.world.gen.PropLib.get(props.name(in.model));
        if (d == null) return;
        // the cells it covered are found again from the world: every stand-in touching this one, within the model's reach
        int reach = Math.max(d.w, d.d);
        for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) for (int dy = -d.h; dy <= d.h; dy++) {
            if (props.at(x + dx, y + dy, z + dz) != null) continue;
            var other = props.remove(x + dx, y + dy, z + dz);                  // (already gone: this is the same prop's other cells)
            if (Block.prop(world.get(x + dx, y + dy, z + dz)) && other == null && sameProp(in, x + dx, y + dy, z + dz)) world.set(x + dx, y + dy, z + dz, Block.AIR);
        }
        int rgb = (m.r << 16) | (m.g << 8) | m.b;
        fx.debris(in.x + 0.5f + in.ox, in.y + d.h * 0.5f, in.z + 0.5f + in.oz, 0, 0.6f, 0, 10 + 6 * d.w * d.h, 4.2f, rgb, 0.11f);
        fx.dust(in.x + 0.5f + in.ox, in.y + 0.4f, in.z + 0.5f + in.oz, 0, 0.4f, 0, 5, 0.4f, 0x9A948A, 0.3f);
    }

    private boolean sameProp(absentialis.engine.props.Props.Instance in, int x, int y, int z) {
        // a cell belongs to the broken prop when it lies inside the box the prop was stood on
        float cx = in.x + 0.5f + in.ox, cz = in.z + 0.5f + in.oz;
        return Math.abs(x + 0.5f - cx) <= Math.abs(in.ox) + 0.51f && Math.abs(z + 0.5f - cz) <= Math.abs(in.oz) + 0.51f && y >= in.y && y < in.y + 3;
    }

    /**
     * Something has been struck: the sound of it, and what comes off it (1.0, through the Astratto particle system) - chips
     * of its own colour thrown back off the face, a puff of its dust, sparks off metal - and, on a wall or a floor, the
     * hole it leaves (a decal that fades with time).
     */
    private void impact(float x, float y, float z, Material m, int n, float nx, float ny, float nz, boolean mark) {
        cue(impactSound(m), x, y, z, n >= 5 ? 0.8f : 0.55f, 70);
        if (player.distXZ(x, z) > 90) return;
        int rgb = (m.r << 16) | (m.g << 8) | m.b;
        boolean metal = m == Material.METAL || m == Material.THIN_METAL;
        fx.debris(x, y, z, nx, ny, nz, metal ? n * 0.35f : n * 0.8f, 3.4f, rgb, 0.07f);
        if (metal) fx.sparks(x, y, z, nx, ny, nz, 3 + n, 6.5f);
        else if (m != Material.GLASS && m != Material.WATER && m != Material.FLESH) fx.dust(x, y, z, nx, ny, nz, 1 + n * 0.3f, 0.15f + n * 0.012f, mixRgb(rgb, 0x9A948A, 0.45f), 0.26f);
        if (mark && decals != null && m != Material.GLASS && m != Material.WATER && m != Material.FOLIAGE) decals.add(absentialis.engine.decal.DecalAtlas.BULLET, x, y, z, nx, ny, nz, 0.22f, rng.nextFloat() * 6.28f, 75f, 0xFFFFFF, 0.9f);
    }

    private static int mixRgb(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    public void damageBlock(int x, int y, int z, float dmg, int by) {
        int id = world.get(x, y, z);
        Block b = Block.ALL[id];
        if (b.is(Block.DOOR)) {
            // a door is not a block that vanishes: the hit goes to the entity (blast from its centre, anything else square on)
            Doors.Door d = doors.at(x, y, z);
            if (d != null) {
                if ("blast".equals(hitKind)) doors.blast(d, dmg * 0.25f, d.cx() - blastX, d.cz() - blastZ, by);
                else doors.hit(d, x + 0.5f, y + 0.5f, z + 0.5f, dmg, 0, 0, by, "blunt");
            }
            return;
        }
        if (b.hp <= 0) return;
        int idx = World.idx(x, y, z);
        float total = world.blockDamage.getOrDefault(idx, 0) + dmg;
        if (b.is(Block.FRAGILE) || total >= b.hp) {
            if (b.is(Block.CONTAINER) && !isLocked(x, y, z)) {
                Container box = containers.containsKey(idx) ? containers.get(idx) : openContainer(x, y, z);
                for (ItemInstance it : new ArrayList<>(box.items)) { InventoryService.release(it); dropItem(it, x + 0.5f + rng.range(-0.3f, 0.3f), y + 0.6f, z + 0.5f + rng.range(-0.3f, 0.3f)); }
            }
            world.set(x, y, z, b.breaksInto);
            // 1.0: a prop that is broken goes whole - the model, and every cell it stood in - in a burst of its own pieces
            if (Block.prop(id)) breakProp(x, y, z, b.mat);
            // glass does not vanish: the pane comes out of the frame as shards that fall and lie where they land
            if (b.mat == Material.GLASS && b.is(Block.FRAGILE)) {
                float gx = breakDX, gz = breakDZ;
                if ("blast".equals(hitKind)) { gx = x + 0.5f - blastX; gz = z + 0.5f - blastZ; }
                doors.paneBroken(x, y, z, gx, gz);
            }
            bus.emit(new Events.BlockDestroyed(x, y, z, id, by));
            if (b.mat == Material.GLASS) glass.pane(x + 0.5f, y + 0.5f, z + 0.5f, by);
            else emitSound(x + 0.5f, y + 0.5f, z + 0.5f, 22, "debris", by, false);
            impact(x + 0.5f, y + 0.5f, z + 0.5f, b.mat, 10);
        } else world.blockDamage.put(idx, Math.round(total));
    }

    /** Applies a hit: armour check, region multiplier, injuries, death and evidence. */
    public void damageActor(Actor v, Actor attacker, float dmg, ItemDef ammo, String region, float hx, float hy, float hz) {
        if (v.advisor) return;                       // 1.0: an advisor is not part of the fight
        if (v.dead) return;
        if (v.isPlayer && god) return;
        // 1.2: a guard that is up takes a little off whatever lands
        if (v.isPlayer && playerBlocking && dmg > 0) {
            dmg *= 1 - (blockWithBlade ? BLOCK_BLADE : BLOCK_BARE);
            blocksTaken++;
            if (time - lastBlockCue > 0.3f) { lastBlockCue = time; cue("block", v.x, v.y + 1.3f, v.z, 0.5f, 14); }
        }
        // 0.7: plate, and a saw that can be stopped (the special infected answer a hit in their own way)
        if (!v.isPlayer && v.infected() && !v.turned && Archetypes.special(v.archetype)) {
            boolean ray = rayEntry != null;
            dmg = absentialis.neuralai.integration.Creatures.struck(this, v, dmg, hy, ray ? rayDX : attacker != null ? v.x - attacker.x : 0, ray ? rayDZ : attacker != null ? v.z - attacker.z : 0, "blast".equals(hitKind), ammo != null);
        }
        if (!v.isPlayer && time - v.lastHitTime > 2.5f) v.hitUnaware = v.awareLevel == 0 && "idle".equals(v.state);
        // 0.7: people who are hurt say so (not on every round of a burst: once, then not again for a moment)
        if (!v.isPlayer && !v.infected() && v.tier == 0 && dmg >= 6 && time >= v.painAt) { v.painAt = time + 1.4f + rng.nextFloat(); emitSound(v.x, v.y + 1.5f, v.z, 14, "pain", v.id, false); }
        if (v.isPlayer) {
            if (profile.loadout.charm("damage_one") > 0) dmg = Math.min(dmg, 1);
            if (v.shield > 0) {
                float soak = Math.min(v.shield, dmg);
                v.shield -= soak; dmg -= soak;
                ItemInstance plate = profile.loadout.slot("armor");
                if (plate != null && plate.def().armor != null && plate.def().armor.shield > 0) plate.durability = v.shield;
                body.healthChanged = time;
                if (v.shield <= 0) msg("Body armor destroyed", 0xFFD8483A);
                if (dmg <= 0.01f) { damageShake = Math.min(1, damageShake + soak / 60f); if (attacker != null) hitMarkers.add(new float[]{(float) Math.atan2(-(attacker.x - v.x), -(attacker.z - v.z)), time}); v.lastAttacker = attacker == null ? 0 : attacker.id; return; }
            }
        }
        // ---- where did it really land? Trace the blow against the body's voxels (near bodies; far ones use the coarse region)
        String kind = "blast".equals(hitKind) ? "blast" : "vehicle".equals(hitKind) ? "blunt" : ammo != null ? "bullet" : attacker != null ? "melee" : "blunt";
        // 0.8: what the round sounded like going in - a vest, or what is under it
        if (kind.equals("bullet")) cue(v.shield > 0 || v.armorClass > 0 && "torso".equals(region) ? "impact_armor" : dmg >= 45 ? "impact_flesh_heavy" : "impact_flesh", hx, hy, hz, 0.75f, 55);
        // ...and the survivor says so too, in their own voice
        if (v.isPlayer && dmg >= 3 && time >= v.painAt) { v.painAt = time + 0.8f + rng.nextFloat() * 0.6f; cue(playerVoice("hurt"), v.x, v.y + 1.5f, v.z, 0.8f, 40, true); }
        Gore.Entry en = rayEntry;
        float idx = rayDX, idy = rayDY, idz = rayDZ;
        boolean anatomy = v.tier == 0 && !kind.equals("blast");
        boolean traced = en != null;
        if (anatomy && en == null) {
            // no projectile path: the caller named the region. Find where on that region the blow lands, coming
            // from the attacker's side (or, with nobody to blame, from wherever the body is facing).
            float ax = attacker != null ? v.x - attacker.x : (float) Math.sin(v.yaw), az = attacker != null ? v.z - attacker.z : (float) Math.cos(v.yaw);
            float al = (float) Math.sqrt(ax * ax + az * az);
            if (al < 0.05f) { ax = (float) Math.sin(v.yaw); az = (float) Math.cos(v.yaw); al = 1; }
            idx = ax / al; idy = 0; idz = az / al;
            float h = region.equals("legs") ? 0.22f : region.equals("head") ? 0.9f : region.equals("arms") ? 0.56f : 0.62f;
            // an arm is off the body's centre line: aim at the side the blow was reported on
            float side = region.equals("arms") ? 0.3f * ((v.id & 1) == 0 ? 1 : -1) : region.equals("legs") ? 0.11f * ((v.id & 1) == 0 ? 1 : -1) : 0;
            float sx0 = -idz * side, sz0 = idx * side;
            en = gore.trace(v, v.x - idx * 2 + sx0, v.y + v.height() * h, v.z - idz * 2 + sz0, idx, 0, idz, 4);
            if (en == null) en = gore.trace(v, v.x - idx * 2, v.y + v.height() * h, v.z - idz * 2, idx, 0, idz, 4);
        }
        rayEntry = null;
        // only a real projectile path overrides the region the caller reported
        if (en != null && traced) region = absentialis.anatomy.DamageProcessor.regionName(en.group);
        float pen = ammo != null && ammo.ammo != null ? ammo.ammo.pen : 20;
        boolean penetrated = true;
        int cover = protection(v, region, attacker);
        if (cover > 0) {
            float chance = Math.max(0.05f, Math.min(0.95f, (pen - cover * 10) / 20f + 0.5f));
            penetrated = rng.chance(chance);
            if (!region.equals("head")) v.armorDur = Math.max(0, v.armorDur - dmg * (ammo != null && ammo.ammo != null ? ammo.ammo.armorDamage : 0.5f) * 0.4f);
            if (!penetrated) dmg *= 0.22f;
        }
        // fast infected are fragile where it counts: a clean head shot drops them
        // ---- the physical event: voxels destroyed, structure tested, blood, reaction (see Gore)
        float raw = dmg;
        boolean bite = "bite".equals(meleeKind), blade = "blade".equals(meleeKind);
        Gore.Impact im = null;
        if (attacker != null) {
            float ddx = v.x - attacker.x, ddz = v.z - attacker.z, dl = (float) Math.sqrt(ddx * ddx + ddz * ddz);
            if (dl > 0.01f) { v.hitDirX = ddx / dl; v.hitDirZ = ddz / dl; }
        } else { v.hitDirX = 0; v.hitDirZ = 0; }
        if (en != null && anatomy) {
            int type = kind.equals("bullet") ? (penetrated ? absentialis.anatomy.DamageProcessor.BULLET : absentialis.anatomy.DamageProcessor.BLUNT)
                    : bite ? absentialis.anatomy.DamageProcessor.BITE : blade ? absentialis.anatomy.DamageProcessor.BLADE : absentialis.anatomy.DamageProcessor.BLUNT;
            float power = kind.equals("bullet") ? (rayPower > 0 ? rayPower : pen / 10f) : 1f;
            im = gore.impact(v, attacker, en, type, kind.equals("bullet") && !penetrated ? raw * 2.2f : raw, power, idx, idy, idz);
            dmg *= im.vital;
        } else if (kind.equals("blast") && v.tier == 0) gore.blast(v, blastX, blastY, blastZ, raw);
        rayPower = 0;
        float mul = switch (region) { case "head" -> "sprinter".equals(v.archetype) ? 4.5f : v.infected() ? 2.5f : 3.2f; case "torso" -> 1f; case "arms" -> 0.6f; default -> 0.7f; };
        dmg *= mul;
        v.hp -= dmg;
        v.hurtFlash = 0.15f;
        v.hitRegion = region;
        v.lastHitTime = time;
        v.lastHitKind = kind.equals("melee") && "vehicle".equals(hitKind) ? "vehicle" : kind;
        if (kind.equals("bullet")) {
            v.lastHitWeapon = hitWeaponId != null ? hitWeaponId : attacker != null && attacker.weapon != null ? attacker.weapon.defId : "";
            v.lastHitRange = hitTravel < 999 ? hitTravel : attacker != null ? v.dist(attacker) : 999;
            ItemDef hw = Content.def(v.lastHitWeapon);
            if (region.equals("head") && hw != null && hw.weapon != null && "shotgun".equals(hw.weapon.family)) v.headPelletAt = time;
        }
        if (attacker != null && attacker != v && dmg > 0) bus.emit(new Events.HitConfirmed(attacker.id, v.id, region, dmg, time));
        if (attacker != null) v.lastAttacker = attacker.id;
        v.hitForce = dmg;
        // far bodies have no anatomy to react with: they keep the simple flinch
        if (im == null && v.anim != null) v.anim.hit((float) Math.atan2(v.hitDirX, v.hitDirZ));
        if (v.isPlayer && v.anim != null) v.anim.hit((float) Math.atan2(v.hitDirX, v.hitDirZ));
        if (!v.isPlayer && !v.infected()) { v.morale -= dmg / 260f; Squad sq = squad(v); if (sq != null) sq.bloodied = true; }
        if (!gore.mistOnly) v.bleed += raw * (region.equals("legs") ? 0.018f : region.equals("torso") ? 0.012f : 0.014f) * (im != null && im.destroyed == 0 ? 0.3f : 1f);
        bus.emit(new Events.ActorInjured(v.id, attacker == null ? 0 : attacker.id, region, dmg, penetrated));
        lastKind = kind;
        if (v.isPlayer) {
            if (region.equals("arms") || kind.equals("blast")) body.armInjury = Math.min(1, body.armInjury + dmg / 55f);
            if (region.equals("legs") || kind.equals("blast")) body.legInjury = Math.min(1, body.legInjury + dmg / 60f);
            damageShake = Math.min(1, damageShake + dmg / 40f);
            if (attacker != null) hitMarkers.add(new float[]{(float) Math.atan2(-(attacker.x - v.x), -(attacker.z - v.z)), time});
            if (penetrated && dmg > 12 && rng.chance(0.35)) body.bleedLight = true;
            if (penetrated && dmg > 30 && rng.chance(0.3)) body.bleedHeavy = true;
            if (region.equals("legs") && dmg > 15 && rng.chance(0.25)) { body.fracture = true; msg("Leg fractured - splint it", 0xFFD8483A); }
            body.healthChanged = time;
        } else {
            if (attacker == player) debrief.hits++;
            // being shot is unambiguous evidence
            if (attacker != null && (attacker.isPlayer || attacker.mate()) && !v.infected() && !v.mate()) { v.hostileToPlayer = true; for (Actor a : nearby(v.x, v.z, 30)) if (a.faction.equals(v.faction) && a.squad == v.squad) a.hostileToPlayer = true; }
            // being hit tells them they are under attack and roughly from where - a target only if they can see it
            NeuralHost.onShot(this, v, attacker);
            if (v.id == grabbedBy) grabUntil = -99;        // whoever had hold of the player lets go when hit
            v.wakeUntil = time + 120;
        }
        if (v.hp <= 0 && !v.isPlayer) {
            // otherwise-lethal damage: down or dead is decided in one place (the player's turn comes in update())
            boolean catastrophic = Gore.lost(v, absentialis.character.Rig.HEAD) || raw > v.maxHp * 1.6f;
            if (!casualty.lethal(v, attacker, -v.hp, region, v.lastHitKind, catastrophic)) kill(v, attacker);
        }
        // wounded and alive: their group knows it has been shot at
        if (!v.dead && !v.isPlayer && !v.infected() && !v.mate() && attacker != null && (attacker.isPlayer || attacker.mate())) rivalry.onHit(v);
    }

    /** Squadmates killed (or turned) this raid: roster id -> how. They do not come back (0.6). */
    public final Map<Long, String> mateDeaths = new LinkedHashMap<>();
    /** Kills credited to each squadmate this raid (experience at the debrief). */
    public final Map<Long, Integer> mateKills = new HashMap<>();

    private void kill(Actor v, Actor attacker) {
        if (v.dead) return;
        // the player's death is a presentation of its own (ragdoll, camera, choice): it must go through die(), which
        // is what 0.4 skipped here - the body was marked dead before die() ran, so it was never given a ragdoll or drawn
        if (v.isPlayer) { v.vx = v.vz = 0; if (attacker != null) v.lastAttacker = attacker.id; die("Killed in action" + (attacker != null && attacker != v ? " by " + describe(attacker) : "")); return; }
        confirmKill(v, attacker);
        casualty.died(v);
        v.dead = true;
        v.diedAt = time;
        if (v.stowed != null && v.weapon != v.stowed) { if (v.weapon != null) v.carried.add(v.weapon); v.weapon = v.stowed; v.stowed = null; }
        deaths++;
        if (v.tier == 0 && !v.infected()) gore.deathBlow(v, lastKindFor(v));
        if (v.ragdoll == null) startRagdoll(v);      // already down under physics: the same ragdoll simply never gets up
        else {
            absentialis.sydney.SydneyCharacter sc = absentialis.sydney.SydneyCharacter.of(v.ragdoll); if (sc != null) sc.die();
            // (1.1: far off, nobody is watching it fall: it is run forward to where it comes to rest, as a body that died on its feet there is)
            if (v.tier == 1 || v.distXZ(player.x, player.z) > 55) { v.ragdoll.fastForward(world); v.x = v.ragdoll.x[Ragdoll.PELVIS]; v.z = v.ragdoll.z[Ragdoll.PELVIS]; v.y = Math.max(0, v.ragdoll.lowestY()); }
        }
        gore.onDeath(v, v.hitForce, lastKind);
        v.vx = v.vz = 0;
        if (v.agentId != 0) profile.agents.kill(v.agentId);
        // 0.7: a death is heard. A person's last cry, or whatever it is the dead do instead
        if (!v.isPlayer && v.tier == 0 && !"head".equals(v.hitRegion) && !silentKill) emitSound(v.x, v.y + 1.4f, v.z, v.infected() ? 14 : 20, v.infected() ? "infected_death" : "death_cry", v.id, false);
        neural.remove(v.id, true);
        neuralBody.forget(v.id);
        if (v.mate()) {
            absentialis.gameplay.squad.Roster.Mate dm = profile.squad.byId(v.mateId);
            if (dm != null) dm.downs++;
            mateDeaths.put(v.mateId, attacker == null ? lastKindFor(v) : "killed by " + describe(attacker));
            cue("squadmate_died", player.x, player.y + 1.5f, player.z, 0.7f, 1e6f);
            mates.remove(v);
            msg(v.name + " is dead.", 0xFFD8483A);
        }
        if (attacker != null && attacker.mate()) { absentialis.gameplay.squad.Roster.Mate km = profile.squad.byId(attacker.mateId); if (km != null) km.kills++; mateKills.merge(attacker.mateId, 1, Integer::sum); }
        // a squad learns of a death by seeing or hearing it - otherwise the member is simply missing for a while
        boolean noticed = false;
        for (Actor m : actors) if (m != v && !m.dead && !m.isPlayer && m.faction.equals(v.faction) && m.squad == v.squad && m.squad != 0) {
            float md = m.dist(v);
            // 0.9: a silent takedown is known only to those who were looking that way and could make it out - nobody "hears" it at fourteen metres
            if (silentKill) { if (md < 48 && neural.perceives(m.id, player.id)) { NeuralHost.onAllyDown(this, m, v); noticed = true; takedownsSeen++; } continue; }
            if (md < 14 || md < 48 && Physics.los(world, m.x, m.eyeY(), m.z, v.x, v.y + 1, v.z)) { NeuralHost.onAllyDown(this, m, v); noticed = true; }
        }
        if (!noticed && v.squad != 0 && !v.infected() && !v.mate()) { Squad sq = squads.get(v.squad); if (sq != null && sq.faction.equals(v.faction)) sq.missing.add(new float[]{v.x, v.y, v.z, time}); }
        if (v.infected()) profile.zone.reportCorpse(world.district(v.x, v.z));
        // witnesses: same-faction members with line of sight
        boolean witnessed = false;
        for (Actor a : nearby(v.x, v.z, 45)) if (a != v && !a.isPlayer && a.faction.equals(v.faction) && (silentKill ? neural.perceives(a.id, player.id) : Physics.los(world, a.x, a.eyeY(), a.z, v.x, v.y + 1, v.z))) { witnessed = true; if (attacker == player || attacker != null && attacker.mate()) a.hostileToPlayer = true; }
        District d = world.district(v.x, v.z);
        bus.emit(new Events.ActorKilled(v.id, attacker == null ? 0 : attacker.id, v.faction, attacker == null ? "" : attacker.faction, v.x, v.y, v.z, witnessed, v.archetype));
        if (attacker == player || attacker != null && attacker.mate() && !v.mate()) {
            debrief.kills.merge(v.infected() ? v.archetype : v.faction, 1, Integer::sum);
            profile.contracts.onKill(v.infected() ? "infected" : v.faction, d);
            if (v.infected() && absentialis.neuralai.integration.Archetypes.special(v.archetype)) profile.contracts.onSpecial(v.archetype);
            if (v.infected()) { profile.zone.reportInfectedKill(d); }
            else {
                profile.zone.reportKill(d, v.faction, witnessed);
                debrief.killedFactions.add(v.faction);
                // standing reacts to evidence: witnessed murders cost the most, self-defence against an aggressor the least
                float rd = v.aggressor ? -0.5f : witnessed ? -6f : -1.5f;
                debrief.repDelta.merge(v.faction, rd, Float::sum);
                // rivals appreciate it
                for (String f : ZoneState.FACTIONS) if (!f.equals(v.faction) && Relations.factionsHostile(f, v.faction)) debrief.repDelta.merge(f, 0.6f, Float::sum);
                // and the group itself learns of it - if any of its people saw
                rivalry.onKill(v, witnessed, v.hitUnaware);
            }
            if (attacker == player && weaponMastery != null && player.weapon != null) weaponMastery.accept(player.weapon.defId, v.infected() ? 120L : 300L);
        }
        // corpse inventory: what they carried, nothing more
        Container c = new Container("corpse:" + v.id, 8, 8);
        c.label = v.infected() ? ZoneState.cap(v.archetype) + " remains" : ZoneState.cap(v.faction) + " " + v.archetype;
        // 0.9: what a body was holding and wearing is its equipment, and is found on it as equipment - the weapon in the weapon's
        // slot, the helmet on the head, the armour on the chest - not loose among the things in its pockets
        absentialis.gameplay.inventory.Loadout gear = new absentialis.gameplay.inventory.Loadout();
        // a fallen squadmate's kit is recovered with them, not left as free loot
        if (v.weapon != null && !v.mate()) { v.weapon.ownerFaction = v.faction; if (!wear(gear, v.weapon)) InventoryService.add(v.weapon, c); }
        v.weapon = null;
        for (ItemInstance taken : v.carried) InventoryService.add(taken, c);
        v.carried.clear();
        // 0.7: what they were wearing is on the body too, as the things themselves - the helmet, the mask, the armour in
        // whatever state the fight left it. Take one and the body is drawn without it. (A squadmate's kit comes home with them.)
        if ((!v.infected() || v.turned) && !v.mate()) {
            v.wornItems = new ItemInstance[3];
            String[] worn = {v.wornHead, v.wornFace, v.wornArmor};
            for (int k = 0; k < 3; k++) {
                ItemDef wd2 = worn[k].isEmpty() ? null : Content.def(worn[k]);
                if (wd2 == null) continue;
                ItemInstance wi = new ItemInstance(wd2.id);
                wi.origin = raidId; wi.foundInRaid = true; wi.ownerFaction = v.faction;
                float keep = k == 2 && v.armorMax > 0 ? Math.max(0.12f, v.armorDur / v.armorMax) : rng.range(0.25f, 0.7f);
                wi.durability = Math.max(1, wi.maxDurability * keep);
                if (wear(gear, wi) || InventoryService.add(wi, c).ok()) v.wornItems[k] = wi;
            }
            // the pack on its back is a pack: it comes off the body as one
            ItemDef pk = v.wornPack == null || v.wornPack.isEmpty() ? null : Content.def(v.wornPack);
            if (pk != null && "backpack".equals(pk.slot)) { ItemInstance pi = new ItemInstance(pk.id); pi.origin = raidId; pi.foundInRaid = true; pi.ownerFaction = v.faction; pi.durability = Math.max(1, pi.maxDurability * rng.range(0.3f, 0.8f)); if (wear(gear, pi)) v.wornPackItem = pi; }
        }
        v.corpseGear = gear;
        // someone who was traded with has on them exactly what they were left with (their goods were drawn then, not now)
        boolean traded = v.wares != null;
        if (traded) { for (ItemInstance left : new ArrayList<>(v.wares.items)) { InventoryService.release(left); InventoryService.add(left, c); } v.wares = null; }
        if (traded) { /* nothing more to draw */ }
        else if (!v.infected() && !v.mate()) {
            Map<String, List<String>> kit = Content.KITS.get(v.faction);
            String table = kit != null && kit.get("loot") != null ? kit.get("loot").get(0) : "body";
            for (Object[] e : Content.roll(table, rng, profile.zone.availability(d), 0)) addRolled(c, (String) e[0], (Integer) e[1], v.faction);
        } else if (rng.chance(0.25)) for (Object[] e : Content.roll("body", rng, null, 0.3f)) addRolled(c, (String) e[0], (Integer) e[1], null);
        jewellery(v, c);
        v.corpse = c;
        // fat infected rupture however they die: acid, a contamination zone and a noise event
        if ("bomber".equals(v.archetype) && !v.burst) burst(v, attacker);
    }

    public java.util.function.BiConsumer<String, Long> weaponMastery;

    /** 1.1: the grade of the best thing still on a body (in its pockets, in its hands, worn on it): 0 common .. 5 mythic; -1 when there is nothing left to take. */
    public static int bestLootTier(Actor corpse) {
        int best = -1;
        if (corpse.corpse != null) for (ItemInstance it : corpse.corpse.items) best = Math.max(best, it.def().tier);
        if (corpse.corpseGear != null) for (ItemInstance it : onBody(corpse)) if (it != null) best = Math.max(best, it.def().tier);
        return best;
    }

    /** 1.1: what the special infected wear. Every one of them has at least one piece of jewellery on it - most of it ordinary, some of it not. {item id, weight}. */
    public static final Object[][] JEWELLERY = {
            {"val.ring_silver", 26}, {"val.watch", 22}, {"val.chain", 16}, {"val.ring_gold", 13}, {"val.bracelet_gold", 9}, {"val.signet", 6},
            {"val.necklace_pearl", 4}, {"treasure.spinel_pink", 1.6f}, {"val.brooch_ruby", 1.2f}, {"treasure.emerald", 0.7f}, {"treasure.ruby", 0.35f}, {"treasure.diamond", 0.15f}};
    /** Pieces of jewellery put on special infected this operation (gates). */
    public int jewelleryGiven;

    /** 1.2: one piece from {@link #JEWELLERY}, by its weights - what a special infected has on it, and what a mutated spider has in it. */
    public String rollJewellery() {
        float total = 0;
        for (Object[] j : JEWELLERY) if (Content.def((String) j[0]) != null) total += ((Number) j[1]).floatValue();
        float roll = rng.nextFloat() * total;
        String last = null;
        for (Object[] j : JEWELLERY) {
            if (Content.def((String) j[0]) == null) continue;
            last = (String) j[0];
            roll -= ((Number) j[1]).floatValue();
            if (roll <= 0) return last;
        }
        return last;
    }

    private void jewellery(Actor v, Container c) {
        if (!v.infected() || !Archetypes.special(v.archetype)) return;
        float total = 0;
        for (Object[] j : JEWELLERY) if (Content.def((String) j[0]) != null) total += ((Number) j[1]).floatValue();
        // always one; one in four has a second; the big ones (they were somebody once, and well off) one more often than not
        int n = 1 + (rng.chance(0.25) ? 1 : 0) + (Archetypes.huge(v.archetype) && rng.chance(0.55) ? 1 : 0);
        for (int k = 0; k < n; k++) {
            float roll = rng.nextFloat() * total;
            for (Object[] j : JEWELLERY) {
                if (Content.def((String) j[0]) == null) continue;
                roll -= ((Number) j[1]).floatValue();
                if (roll > 0) continue;
                ItemInstance it = new ItemInstance((String) j[0]);
                it.origin = raidId; it.foundInRaid = true;
                // (always: if there is no room on the body for it, room is made - it was possible for a full one to get none)
                if (InventoryService.add(it, c).ok()) jewelleryGiven++;
                else { c.items.add(it); jewelleryGiven++; }
                break;
            }
        }
    }

    private static Actor fake(String f) { Actor a = new Actor(); a.faction = f; return a; }

    private void addRolled(Container c, String id, int qty, String owner) {
        ItemInstance i = new ItemInstance(id);
        ItemDef d = i.def();
        i.qty = d.stackable() ? Math.min(d.maxStack, qty) : 1;
        i.origin = raidId;
        i.foundInRaid = true;
        i.ownerFaction = owner;
        if (d.magazine != null) { i.rounds = rng.range(0, i.capacity()); if (i.rounds > 0) i.ammoId = Content.ammoFor(d.magazine.caliber).id; }
        if (d.weapon != null && !d.weapon.melee()) { i.setCondition(rng.range(25f, 90f)); i.rounds = rng.range(0, Math.max(1, d.weapon.capacity / 2)); if (i.rounds > 0) i.ammoId = Content.ammoFor(d.weapon.caliber).id; }
        InventoryService.add(i, c);
    }

    /** NPC shooting: aim error grows with distance and target movement; real projectiles. */
    public void npcFire(Actor a, Actor t, float dt) { npcFireAt(a, t, 0, 0, 0); }

    /** Suppressive fire on a remembered position: no target needed, wide error, keeps heads down. */
    public void npcSuppress(Actor a, float x, float y, float z) { npcFireAt(a, null, x, y, z); }

    /** An agent reloads from what it carries (agents' reserves are not counted round by round). */
    public void npcReload(Actor a) {
        ItemInstance w = a.weapon;
        if (w == null || w.def().weapon == null || w.def().weapon.melee()) return;
        ItemDef.Weapon wd = w.def().weapon;
        if (w.rounds >= w.capacity() || a.fireCooldown > 0.6f) return;
        w.rounds = w.capacity(); w.ammoId = Content.ammoFor(wd.caliber).id;
        boolean single = "single".equals(wd.reloadStyle);
        a.fireCooldown = single ? Math.min(4.5f, wd.reloadTime * 1.2f * wd.capacity) : wd.reloadTime * 1.3f;
        if (a.anim != null) a.anim.play(single ? "reload_shell" : "reload_empty", Math.min(a.fireCooldown, 2.6f));
        emitSound(a.x, a.y + 1, a.z, 12, "reload", a.id, false);
    }

    /**
     * 0.7, a weapon command from NeuralAI: lay the muzzle on a point in the world and fire. The point is where the
     * agent believes its target is; the raid does not correct it. The raid's own rules still apply - the weapon's
     * cycle, a jam, an empty magazine - and a shot is refused when one of the shooter's own side is in the line of it.
     * Returns 1 when a round left the barrel, 0 when the weapon was not ready, -1 when the shot was held for a friend.
     */
    public int npcCommandFire(Actor a, float ax, float ay, float az, boolean speculative, int intended) {
        ItemInstance w = a.weapon;
        if (w == null || w.def().weapon == null || w.def().weapon.melee() || a.fireCooldown > 0 || time < a.blindUntil) return 0;
        if (!WeaponService.canFire(w)) { if (w.jammed) { WeaponService.clearJam(w); a.fireCooldown = 1.5f; } else npcReload(a); return 0; }
        float ox = a.x, oy = a.eyeY() - 0.1f, oz = a.z, dx = ax - ox, dy = ay - oy, dz = az - oz, d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 0.5f) return 0;
        dx /= d; dy /= d; dz /= d;
        // 0.8: nobody shoots their own cover. With something solid a pace in front of the muzzle and the target well beyond
        // it, the shot is held: someone who can lean round it does (and fires from there on the next call); someone who
        // cannot is told the line is blocked, and NeuralAI moves them to where it is not.
        if (!speculative && d > MUZZLE_CLEAR + 0.6f && blockedNear(ox, oy, oz, dx, dy, dz, MUZZLE_CLEAR)) {
            float yaw = (float) Math.atan2(-dx, -dz), side = leanToClear(a, yaw, (float) Math.asin(Math.max(-1, Math.min(1, dy))), 0.45f);
            if (side != 0 && a.ragdoll == null) {
                a.leanTarget = side;
                a.lean += (side - a.lean) * 0.55f;                 // out a little further with each look
                float rx = (float) Math.cos(yaw), rz = (float) -Math.sin(yaw);
                ox += rx * 0.45f * side; oz += rz * 0.45f * side;
                dx = ax - ox; dy = ay - oy; dz = az - oz; d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= d; dy /= d; dz /= d;
                if (Math.abs(a.lean - side) > 0.4f) { a.fireCooldown = 0.12f; coverLeans++; return 0; }       // still coming out: the round goes when the muzzle is clear
            } else { a.fireCooldown = 0.25f; coverHolds++; return -1; }
        } else if (a.leanTarget != 0 && !blockedNear(ox, oy, oz, dx, dy, dz, MUZZLE_CLEAR)) { a.leanTarget = 0; a.lean = 0; }
        for (Actor o : actors) {
            if (o == a || o.dead || Math.abs(o.x - a.x) > d + 2 || Math.abs(o.z - a.z) > d + 2) continue;
            boolean friend = o.faction.equals(a.faction) || a.mate() && o.isPlayer;
            if (!friend) continue;
            float t = o.intersectBody(ox, oy, oz, dx, dy, dz, d);
            if (t >= 0 && t < d) { a.fireCooldown = 0.25f; friendlyHolds++; return -1; }
        }
        commandFire = true;
        npcFireAt(a, speculative ? null : actor(intended), ax, ay, az);
        commandFire = false;
        return 1;
    }

    /** 1.1: the share of aimed rounds a human NPC misses with, by difficulty (0 Easy, 1 Normal, 2 Hardcore). */
    public static float[] MISS_SHARE = {0.50f, 0.25f, 0.10f};
    public static float missShare(int difficulty) { return MISS_SHARE[Math.max(0, Math.min(2, difficulty))]; }
    /** Aimed rounds fired by human NPCs this operation, and how many of them were laid off the target on purpose (gates). */
    public int npcShots, npcMisses;

    /**
     * 1.1, the Tyrant fist: whoever it lands on is knocked straight back about six feet - along the ground, not up into
     * the air - and goes down where they stop. (The shoulder charge still throws: that is what it is for.)
     */
    public void punchBack(Actor a, Actor v, float dx, float dz) {
        if (v.dead || Archetypes.huge(v.archetype) || v.seated) return;
        punches++;
        if (v.isPlayer) {
            v.vx = dx * PUNCH_SPEED_PLAYER; v.vz = dz * PUNCH_SPEED_PLAYER; v.vy = Math.max(v.vy, 1.0f); v.onGround = false;
            v.flingUntil = time + 0.42f; slipT = Math.max(slipT, 0.9f); damageShake = 1;
            return;
        }
        if (v.ragdoll == null) { v.vx = 0; v.vz = 0; v.vy = 0; gore.ragdolls.shoveDown(v, 1.4f); }
        // the whole body leaves the fist at one speed, level: it slides and tumbles to a stop about six feet on
        // (past the hand-over, in which the pose it was standing in still holds a body back: this one is already on its way)
        if (v.ragdoll != null) { v.ragdoll.wake(); v.ragdoll.carry(dx * PUNCH_SPEED, 0.5f, dz * PUNCH_SPEED, 1f); }
    }
    /** How fast a punched body leaves the fist (m/s): tuned so that it comes to rest about six feet (1.83 m) back. */
    public static float PUNCH_SPEED = 11f, PUNCH_SPEED_PLAYER = 7.0f, PUNCH_FEET = 6f;
    public int punches;

    /** Shots an agent did not take because one of its own was in the way (gates). */
    public int friendlyHolds;
    /** 0.8: shots an NPC held because its own cover was in front of its muzzle (it steps clear instead of shooting the wall). */
    public int coverHolds, coverLeans;
    /** How close something solid has to be in front of a muzzle to count as the shooter's own cover (metres). */
    public static float MUZZLE_CLEAR = 1.25f;

    /** True when something solid stands within {@code reach} metres along a line from a point: the line of a muzzle, or of an eye. */
    public boolean blockedNear(float ox, float oy, float oz, float dx, float dy, float dz, float reach) {
        for (float t = 0.25f; t <= reach; t += 0.12f) if (world.solid((int) Math.floor(ox + dx * t), (int) Math.floor(oy + dy * t), (int) Math.floor(oz + dz * t))) return true;
        return false;
    }

    /**
     * 0.8, aiming round a block. Standing with the sights on something solid a pace away, is there a side to lean to
     * from which the same line is open? Returns -1 (left), 1 (right) or 0 (nothing in the way, or no way round it).
     * The side whose line runs clear for longer wins; a side whose own first step is into a wall is not offered.
     */
    public float leanToClear(Actor a, float yaw, float pitch, float leanMetres) {
        float dx = (float) (-Math.sin(yaw) * Math.cos(pitch)), dy = (float) Math.sin(pitch), dz = (float) (-Math.cos(yaw) * Math.cos(pitch));
        float ox = a.x, oy = a.eyeY() - 0.08f, oz = a.z;
        if (!blockedNear(ox, oy, oz, dx, dy, dz, 1.4f)) return 0;
        float rx = (float) Math.cos(yaw), rz = (float) -Math.sin(yaw);
        float best = 0, bestRun = 2.2f;                       // it has to open the line by at least this much to be worth leaning for
        for (int side = -1; side <= 1; side += 2) {
            float sx = ox + rx * leanMetres * side, sz = oz + rz * leanMetres * side;
            if (world.solid((int) Math.floor(sx), (int) Math.floor(oy), (int) Math.floor(sz))) continue;
            float run = 0;
            for (float t = 0.25f; t <= 6f; t += 0.12f) { if (world.solid((int) Math.floor(sx + dx * t), (int) Math.floor(oy + dy * t), (int) Math.floor(sz + dz * t))) break; run = t; }
            if (run > bestRun) { bestRun = run; best = side; }
        }
        return best;
    }
    private boolean commandFire;

    private void npcFireAt(Actor a, Actor t, float aimX, float aimY, float aimZ) {
        ItemInstance w = a.weapon;
        ItemDef.Weapon wd = w.def().weapon;
        if (!WeaponService.canFire(w)) {
            if (w.jammed) { WeaponService.clearJam(w); a.fireCooldown = 1.5f; return; }
            npcReload(a);
            return;
        }
        if ("pump".equals(w.fireModeName()) || "bolt".equals(w.fireModeName())) { if (!wd.internal()) WeaponService.chamberFromMag(w); }
        Gunsmith.Stats st = Gunsmith.stats(w, 0);
        ItemDef ammo = WeaponService.fire(w, st, 1, rng);
        if (ammo == null) return;
        if (!wd.internal() && !w.chambered) WeaponService.chamberFromMag(w);
        float ox = a.x, oy = a.eyeY() - 0.1f, oz = a.z;
        // a commanded shot goes where it was told to go: the target, when there is one, only says how hard it is to hit
        boolean own = t == null || commandFire;
        float tx = own ? aimX : t.x, ty = own ? aimY : t.y + t.height() * 0.62f, tz = own ? aimZ : t.z;
        // 0.8: leaning out to shoot round something, the muzzle is out there with the shoulder
        if (a.leanTarget != 0 && Math.abs(a.lean) > 0.05f) { float ly = (float) Math.atan2(-(tx - a.x), -(tz - a.z)); ox += (float) Math.cos(ly) * 0.45f * a.lean; oz += (float) -Math.sin(ly) * 0.45f * a.lean; }
        float dx = tx - ox, dy = ty - oy, dz = tz - oz, d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        a.flashUntil = time + 0.3f;
        absentialis.gameplay.squad.Roster.Mate fm = a.mate() ? profile.squad.byId(a.mateId) : null;
        float skill = a.aimPenalty * (fm != null ? 1 - 0.45f * fm.skill() : 1) * (night() && !a.light ? 1.25f : 1) * ("marksman".equals(a.archetype) ? 0.5f : "military".equals(a.faction) || "mercenaries".equals(a.faction) ? 0.85f : 1f);
        float moving = t != null && (Math.abs(t.vx) + Math.abs(t.vz)) > 1 ? 1.8f : 1f;
        skill *= combat.aimError[combat.condition(a.hp, a.maxHp).ordinal()];
        float err = (1.2f + d * 0.035f) * skill * (1 + a.suppression * 0.8f) * moving * (t != null && t.crouch ? 1.3f : 1f) * (t == null ? 2.4f : 1f) * (difficulty() == 0 ? 1.5f : difficulty() == 2 ? 0.8f : 1f);
        // 1.1, marksmanship by difficulty. A person shooting at somebody they can see misses a set share of their rounds:
        // half on Easy, a quarter on Normal, one in ten on Hardcore. A round that is to miss is laid off the body - to
        // one side of it, or over it - by more than the body is wide; a round that is to hit is laid on the middle of it
        // and leaves the barrel as true as the weapon shoots. (What cover, movement and the wall in between then do to
        // either is still theirs to do.) Fire at a place rather than a person - suppression, a guess - is as wild as it was.
        if (t != null && !a.infected()) {
            boolean miss = rng.nextFloat() < missShare(difficulty());
            npcShots++;
            if (miss) {
                npcMisses++;
                float rx = -dz / d, rz = dx / d, rl = Math.max(1e-3f, (float) Math.sqrt(rx * rx + rz * rz)), off = (t.radius + 0.55f + rng.nextFloat() * 0.9f) * (rng.nextFloat() < 0.5f ? -1 : 1);
                boolean over = rng.nextFloat() < 0.25f;
                float mx = tx + (over ? 0 : rx / rl * off), my = ty + (over ? t.height() * 0.5f + 0.45f + rng.nextFloat() * 0.5f : (rng.nextFloat() - 0.5f) * 0.5f), mz = tz + (over ? 0 : rz / rl * off);
                dx = mx - ox; dy = my - oy; dz = mz - oz; d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            fireProjectiles(a, w, ammo, ox, oy, oz, dx / d, dy / d + d * 0.0006f, dz / d, Math.min(0.22f, st.dispersion * 0.3f));
        } else
        fireProjectiles(a, w, ammo, ox, oy, oz, dx / d, dy / d + d * 0.0006f, dz / d, err + st.dispersion);
        if (a.anim != null) a.anim.fire(absentialis.character.Animator.kickOf(a.weapon == null || a.weapon.def().weapon == null ? null : a.weapon.def().weapon.family, a.weapon != null && ("pump".equals(a.weapon.fireModeName()) || "bolt".equals(a.weapon.fireModeName()))));
        shotSignature(a, w, st, ox, oy, oz);
        float rpmGap = 60f / st.rpm;
        if (a.burstLeft <= 0) a.burstLeft = wd.modes.contains("auto") ? rng.range(2, 5) : 1;
        a.burstLeft--;
        // commanded fire is paced by whoever commands it (bursts and the rests between them are the AI's): the weapon only keeps its own cycle
        if (commandFire) a.fireCooldown = wd.modes.contains("auto") ? rpmGap : Math.max(rpmGap, 0.32f + rng.nextFloat() * 0.3f + ("marksman".equals(a.archetype) ? 0.9f : "bolt".equals(w.fireModeName()) || "pump".equals(w.fireModeName()) ? 0.5f : 0));
        else a.fireCooldown = a.burstLeft > 0 ? rpmGap : rng.range(0.5f, 1.4f) + ("marksman".equals(a.archetype) ? 1.2f : 0);
    }

    /**
     * Shove: makes room. The target is thrown back and off balance; against a wall the wall does the damage.
     */
    public void shove(Actor a, Actor t) {
        float dx = t.x - a.x, dz = t.z - a.z, dl = Math.max(0.1f, (float) Math.sqrt(dx * dx + dz * dz));
        dx /= dl; dz /= dl;
        // 1.3: something low on the ground - a Crawler on all fours, a body crawling - is not shoved (a push at chest height
        // meets nothing, and its own legs undid the rest: people stood over a Crawler shoving the air, again and again). It
        // is stamped on: a heel driven down into it, which hurts, and pins it for a moment where it lies.
        if (!t.isPlayer && (t.height() < 1.15f || "crawler".equals(t.archetype) || t.crawling) && !t.cling) {
            stomps++;
            hitKind = "blunt";
            damageActor(t, a, 18, null, "torso", t.x, t.y + 0.3f, t.z);
            hitKind = "";
            t.staggerUntil = Math.max(t.staggerUntil, time + 0.9f);
            t.vx = dx * 1.2f; t.vz = dz * 1.2f; t.pvx = t.vx; t.pvz = t.vz;
            if (a.anim != null) a.anim.play("melee", 0.45f);
            emitSound(t.x, t.y + 0.3f, t.z, 10, "impact_flesh", a.id, false);
            return;
        }
        // is there something solid right behind them?
        boolean wall = world.solid((int) Math.floor(t.x + dx * 1.0f), (int) Math.floor(t.y + 1.0f), (int) Math.floor(t.z + dz * 1.0f));
        t.vx += dx * 6.5f; t.vz += dz * 6.5f;
        emitSound(a.x, a.y + 1, a.z, 9, "melee", a.id, false);
        if (t.isPlayer) {
            damageShake = Math.min(1, damageShake + 0.45f);
            playerVulnerableUntil = Math.max(playerVulnerableUntil, time + 0.5f);
            if (wall) { hitKind = "vehicle"; damageActor(t, a, 11 * difficultyDamage(), null, "torso", t.x, t.y + 1.1f, t.z); hitKind = ""; slipT = Math.max(slipT, 0.45f); msg("Slammed into the wall", 0xFFD8483A); }
        } else {
            t.staggerUntil = Math.max(t.staggerUntil, time + (wall ? 1.2f : 0.7f));
            if (wall) { hitKind = "vehicle"; damageActor(t, a, 12, null, "torso", t.x, t.y + 1.1f, t.z); hitKind = ""; }
        }
    }

    /** Grab: the target is held for a moment - long enough for someone else to shoot. A blow or a round on the holder breaks it. */
    public void grab(Actor a, Actor t) {
        if (struckInVehicle(a, t, 12, t.x - a.x, t.z - a.z)) return;
        emitSound(a.x, a.y + 1, a.z, 9, "grab", a.id, false);
        if (t.isPlayer) { grabbedBy = a.id; grabUntil = time + 1.35f; damageShake = Math.min(1, damageShake + 0.3f); msg("Grabbed! Strike or shoot them to break free", 0xFFD8483A); }
        else t.staggerUntil = Math.max(t.staggerUntil, time + 1.1f);
    }

    /** Lobs a grenade from an agent toward a point: a ballistic throw that lands near it, and a fuse that gives a listener time to move. */
    public void npcThrow(Actor a, float tx, float ty, float tz, String itemId) {
        ItemDef def = Content.def(itemId);
        if (def == null || def.throwable == null) return;
        Thrown t = new Thrown();
        t.x = a.x; t.y = a.eyeY(); t.z = a.z;
        float dx = tx - t.x, dy = ty + 0.3f - t.y, dz = tz - t.z, dist = (float) Math.sqrt(dx * dx + dz * dz);
        float T = Math.max(0.9f, dist / 11f);
        t.vx = dx / T; t.vz = dz / T; t.vy = (dy + 0.5f * Physics.GRAVITY * T * T) / T;
        t.def = def; t.owner = a.id; t.fuse = T + 1.1f;
        thrown.add(t);
        if (a.anim != null) a.anim.play("throw", 0.6f);
        emitSound(a.x, a.y + 1.5f, a.z, 6, "pin", a.id, false);
    }

    public void melee(Actor a, Actor t, float dmg) {
        if (a.dist(t) > 2.2f + a.radius) return;
        // blows on one target are taken in turn: two arriving in the same instant would be one attack too many
        if (time - lastMeleeOn < 0.32f && lastMeleeBy != a.id && t.isPlayer) { a.fireCooldown = Math.max(a.fireCooldown, 0.3f); return; }
        if (t.isPlayer) { lastMeleeOn = time; lastMeleeBy = a.id; }
        // the infected go for the neck and the arms that fend them off; a bite that breaks the skin carries the virus
        boolean bite = rng.chance(infection.biteChance(a, t));
        meleeKind = bite ? "bite" : a.weapon != null && "melee.knife".equals(a.weapon.defId) ? "blade" : "";
        String region = bite ? (rng.chance(0.55) ? "arms" : "torso") : t.isPlayer ? (rng.chance(0.3) ? "arms" : "torso") : "torso";
        float hx = t.x + (a.x - t.x) * 0.3f, hz = t.z + (a.z - t.z) * 0.3f;
        if (bite && a.anim != null) a.anim.play("melee", 0.45f);
        damageActor(t, a, bite ? dmg * 0.8f : dmg, null, region, hx, t.y + t.height() * (bite ? 0.78f : 0.62f), hz);
        meleeKind = "";
        emitSound(a.x, a.y + 1, a.z, 10, "melee", a.id, false);
        if (bite) infection.bitten(t, a);
    }

    /** A status line for the player (not speech: nobody else hears it). */
    public void notice(String text, int color) { msg(text, color); }

    /** An agent is gone from its faction without dying as itself (it turned): squad, roster and A-Life bookkeeping. */
    void agentLost(Actor a) {
        if (a.mate()) {
            absentialis.gameplay.squad.Roster.Mate dm = profile.squad.byId(a.mateId);
            if (dm != null) dm.downs++;
            mateDeaths.put(a.mateId, "turned");
            mates.remove(a);
            a.mateId = 0; a.team = "";
        }
        if (a.agentId != 0) { profile.agents.kill(a.agentId); a.agentId = 0; }
        neural.remove(a.id, true);
        neuralBody.forget(a.id);
        Squad sq = a.squad == 0 ? null : squads.get(a.squad);
        if (sq != null && sq.faction.equals(a.faction)) { sq.alive = Math.max(0, sq.alive - 1); sq.bloodied = true; }
    }

    public void explode(float x, float y, float z, float radius, float dmg, int owner, boolean breakWalls) {
        emitSound(x, y, z, 160, "explosion", owner, false);
        // the crack on top of the boom: a grenade's own report for a small charge, the sharp sweetener for anything bigger
        cue(radius <= 7 ? "grenade_blast" : "impact_explosive", x, y, z, 0.8f, 220);
        hitKind = "blast";
        blastX = x; blastY = y; blastZ = z;
        for (Actor a : nearby(x, z, radius + 2)) {
            float d = a.dist(fakeAt(x, y, z));
            if (d > radius || !Physics.los(world, x, y + 0.5f, z, a.x, a.y + 1, a.z)) continue;
            damageActor(a, actor(owner), dmg * (1 - d / radius) * a.blastResist, null, "torso", a.x, a.y + 1, a.z);
        }
        hitKind = "";
        // 0.9: the dead are thrown by a blast like anything else that is lying there
        for (Actor a : actors) {
            if (!a.dead || a.ragdoll == null || a.tier != 0 || Math.abs(a.x - x) > radius + 2 || Math.abs(a.z - z) > radius + 2) continue;
            float bx = a.x - x, by = a.y + 0.3f - y, bz = a.z - z, d = (float) Math.sqrt(bx * bx + by * by + bz * bz);
            if (d > radius + 1 || !Physics.los(world, x, y + 0.5f, z, a.x, a.y + 0.5f, a.z)) continue;
            float push = Math.min(14f, dmg * 0.16f) * (1 - d / (radius + 1));
            if (push < 0.4f) continue;
            d = Math.max(0.3f, d);
            a.ragdoll.disturb(a.x, a.y + 0.3f, a.z, 3f, bx / d * push, 1.5f + push * 0.55f, bz / d * push);
            a.x = a.ragdoll.x[Ragdoll.PELVIS]; a.z = a.ragdoll.z[Ragdoll.PELVIS];
            corpsesThrown++;
        }
        int r = (int) Math.ceil(radius / (breakWalls ? 2 : 3));
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dy * dy + dz * dz > r * r) continue;
            int bx = (int) Math.floor(x) + dx, by = (int) Math.floor(y) + dy, bz = (int) Math.floor(z) + dz;
            Block b = world.block(bx, by, bz);
            if (b != null && b.hp > 0 && (b.hp < 400 || breakWalls && b.hp < 1000)) damageBlock(bx, by, bz, dmg * 4, owner);
        }
        // 1.0: the blast as the particle system makes it - fireball, flash, the ring on the ground, sparks, smoke, dust - and what it leaves: scorched ground, nothing growing
        float gy = groundY((int) Math.floor(x), (int) Math.floor(z));
        if (gy < 0 || Math.abs(gy - y) > radius) gy = (float) Math.floor(y);
        fx.explosion(x, y, z, radius, gy);
        fx.debris(x, y + 0.3f, z, 0, 1, 0, 14 + radius * 2, 7.5f, 0x3A3632, 0.11f);
        if (decals != null && Math.abs(gy - y) < radius) decals.add(absentialis.engine.decal.DecalAtlas.SCORCH, x, gy, z, 0, 1, 0, Math.min(7f, radius * 1.1f), rng.nextFloat() * 6.28f, 240f, 0xFFFFFF, 0.9f);
        if (foliage != null) foliage.clear(x, z, radius * 0.75f);
        if (player.dist(fakeAt(x, y, z)) < 25) damageShake = 1;
    }

    private static Actor fakeAt(float x, float y, float z) { Actor a = new Actor(); a.x = x; a.y = y; a.z = z; return a; }

    public void explodeBomber(Actor a, Actor attacker) {
        a.hp = 0;
        kill(a, attacker);
    }

    /**
     * A Bomber bursts (0.7). The blast itself is small; what matters is the bile. Everyone it reaches is covered in it,
     * and a pool of it stays on the ground for a while to catch whoever walks through. Bile does no harm of its own.
     */
    private void burst(Actor a, Actor attacker) {
        a.burst = true;
        float bx = a.x, by = a.y, bz = a.z;
        explode(bx, by + 0.8f, bz, 3.6f, 30, a.id, false);
        for (Actor o : new ArrayList<>(nearby(bx, bz, BILE_BURST + 1))) {
            if (o == a || o.dead || o.infected() || o.distXZ(bx, bz) > BILE_BURST || Math.abs(o.y - by) > 2.5f) continue;
            if (!Physics.sight(world, bx, by + 1.0f, bz, o.x, o.y + o.height() * 0.6f, o.z)) continue;       // a wall keeps it off you
            bile(o, BILE_SECONDS);
        }
        for (int i = 0; i < 46; i++) {
            float ang = rng.range(0f, 6.283f), sp = rng.range(1.5f, 6.5f);
            gore.bile(bx, by + 0.9f, bz, (float) Math.sin(ang) * sp, rng.range(1f, 5.5f), (float) Math.cos(ang) * sp, 0.02f + rng.nextFloat() * 0.03f);
        }
        Zone z = new Zone();
        z.x = bx; z.y = by; z.z = bz; z.radius = 3.4f; z.until = time + 14; z.dps = 0; z.kind = "bile"; z.owner = a.id;
        zones.add(z);
        District d = world.district(bx, bz);
        profile.zone.reportNoise(d, 20);
        profile.zone.reportContamination(d, 1);
    }

    // ------------------------------------------------------------------ infected bile (0.7)
    /** How long bile stays on someone, how far a Bomber's burst throws it, and how far off the infected can smell it. */
    public static float BILE_SECONDS = 15, BILE_BURST = 5.5f, BILE_SMELL = 45;
    public int biledCount, scented;
    private float bileTick;

    /** Covers someone in infected bile: no harm done, but for as long as it lasts every infected in smelling distance comes for them. */
    public void bile(Actor v, float seconds) {
        if (v.dead || v.infected()) return;
        boolean fresh = v.bileUntil <= time;
        v.bileUntil = Math.max(v.bileUntil, time + seconds);
        v.biled = true;
        if (fresh) {
            biledCount++;
            if (v.isPlayer) msg("Covered in bile - every infected nearby can smell you.", 0xFF7FB83A);
            else if (!v.infected() && time - v.lastSay > 4) say(v, v.mate() ? "It's all over me - they're coming!" : "Get it off! Get it off!", "bark_pain");
        }
        bileTick = 0;
    }

    /** How much of the player's sight the bile has taken, 0..1: full while it is on, clearing over its last two seconds. */
    public float bileFx() { float left = player.bileUntil - time; return player.dead || left <= 0 ? 0 : Math.min(1, left / 2f); }

    private void updateBile(float dt) {
        bileTick -= dt;
        boolean tick = bileTick <= 0;
        if (tick) bileTick = 0.5f;
        for (int i = 0; i < actors.size(); i++) {
            Actor v = actors.get(i);
            if (!v.biled) continue;
            if (v.dead || time >= v.bileUntil) { v.biled = false; if (v.isPlayer && !v.dead) msg("The bile has worn off.", 0xFFB8C8A0); continue; }
            // it runs off them: drops that fall, and now and then one that marks the ground
            if (v.tier == 0 && rng.chance(dt * 7)) drip(v, 0.3f + rng.nextFloat() * 0.6f, Gore.BILE, false);
            if (v.tier == 0 && rng.chance(dt * 1.2)) gore.bile(v.x + rng.range(-0.25f, 0.25f), v.y + v.height() * 0.5f, v.z + rng.range(-0.25f, 0.25f), 0, 0, 0, 0.01f);
            if (!tick) continue;
            for (Actor o : nearby(v.x, v.z, BILE_SMELL)) {
                if (!o.infected() || o.dead || o.tier != 0 || Math.abs(o.y - v.y) > 12) continue;
                NeuralHost.scent(this, o, v);
                scented++;
            }
        }
    }

    /** A drop falling off a body at a fraction of its height (poison from a Spewer's face, bile off someone covered in it). */
    public void drip(Actor a, float at, int color, boolean glow) {
        if (player.distXZ(a.x, a.z) > 60) return;
        Effect e = new Effect();
        float ax = (float) -Math.sin(a.yaw), fz = (float) -Math.cos(a.yaw);
        e.x = a.x + ax * 0.16f + rng.range(-0.12f, 0.12f); e.y = a.y + a.height() * at; e.z = a.z + fz * 0.16f + rng.range(-0.12f, 0.12f);
        e.vx = a.vx * 0.5f; e.vy = -0.4f; e.vz = a.vz * 0.5f;
        e.life = e.max = 0.55f + rng.nextFloat() * 0.3f; e.size = 0.05f + rng.nextFloat() * 0.03f; e.color = color; e.glow = glow;
        effects.add(e);
    }

    /** Sparks where a round has met plate. */
    public void spark(float x, float y, float z) {
        fx.sparks(x, y, z, 0, 0.6f, 0, 14, 7.5f);
        emitSound(x, y, z, 16, "ricochet", 0, false);
        cue("impact_shield", x, y, z, 0.7f, 60);
    }

    // ------------------------------------------------------------------ the special infected's hands (0.7)
    public int decapitations, flings;
    /** Where the last thrown body first touched the ground (gates). */
    public final float[] landedAt = new float[2];

    /** A swing that takes the head off: the Gladiator's claws, the Butcher's saw. */
    public void decapitate(Actor a, Actor v) {
        if (v.dead || v.isPlayer && god) return;
        float dx = v.x - a.x, dz = v.z - a.z, l = Math.max(0.01f, (float) Math.sqrt(dx * dx + dz * dz));
        meleeKind = "blade";
        damageActor(v, a, 9999, null, "head", v.x, v.y + v.height() * 0.92f, v.z);
        meleeKind = "";
        if (v.dead) { decapitations++; if (!v.isPlayer && !Gore.lost(v, Gore.HEAD)) gore.sever(v, Gore.HEAD, dx / l, dz / l, 4.5f); }
    }

    /** The Tank's hand has closed on someone. */
    public void grabbed(Actor a, Actor v, float hold) {
        if (struckInVehicle(a, v, 18, v.x - a.x, v.z - a.z)) return;
        v.flingUntil = time + hold + 1.1f;
        v.burstLeft = 0; v.fireCooldown = Math.max(v.fireCooldown, hold + 1.5f);
        if (v.isPlayer) { msg("It has you!", 0xFFD8483A); damageShake = Math.min(1, damageShake + 0.6f); }
        emitSound(v.x, v.y + 1, v.z, 18, "melee", a.id, false);
    }

    /** Carried: the held body goes where the hand is, and does nothing of its own. */
    public void hold(Actor a, Actor v, float x, float y, float z) {
        if (v.seated) return;
        if (v.ragdoll != null) return;
        if (Physics.boxFree(world, x, y, z, v.radius * 0.8f, v.height() * 0.8f)) { v.x = x; v.y = y; v.z = z; }
        v.vx = v.vy = v.vz = 0; v.pvx = v.pvz = 0;
        v.staggerUntil = Math.max(v.staggerUntil, time + 0.3f);
        v.flingUntil = Math.max(v.flingUntil, time + 0.3f);
    }

    /**
     * 1.3: a person thrown into the air (by a Tank, or bowled over by a charge) is Sydney's from the moment they leave the
     * ground - a ragdoll carried by the throw's velocity, limbs flailing, that lands however it lands and gets up from
     * there. (0.7 flew the upright body like a box and only let it fall into a heap where it came down.)
     */
    private void throwRagdoll(Actor v, float dx, float dz, float air, float vx, float vy, float vz) {
        if (v.isPlayer || Archetypes.huge(v.archetype)) return;
        // (the blow itself may already have put the body down - a ragdoll that started standing still: it is thrown all the same)
        if (!v.dead && v.ragdoll == null) gore.ragdolls.knockDown(v, dx, dz, 46f, air + 2.2f);
        if (v.ragdoll == null) return;
        // the legs are gone from under it: no standing controller holds a thrown body on its feet
        absentialis.sydney.SydneyCharacter sc = absentialis.sydney.SydneyCharacter.of(v.ragdoll);
        if (sc != null) sc.mind.collapse();
        // off the ground as it leaves the hand (feet on the floor are held by friction, and the throw died in them)
        if (world.solid((int) Math.floor(v.ragdoll.x[Ragdoll.PELVIS]), (int) Math.floor(v.ragdoll.lowestY() + 0.3f), (int) Math.floor(v.ragdoll.z[Ragdoll.PELVIS])) == false) v.ragdoll.shift(0, 0.18f, 0);
        v.ragdoll.wake(); v.ragdoll.carry(vx, vy, vz, 1f);
        if (!v.dead) v.downUntil = Math.max(v.downUntil, time + air + 2.2f);
        v.thrownAt = -99; v.flingUntil = time; v.thrownRagAt = time;
        thrownRagdolls++;
    }
    public int thrownRagdolls;
    /**
     * 1.3: out cold. The body is Sydney's, lying where it falls and not getting up; its mind is let go, so that when it comes
     * round (RagdollController) it begins again - knowing nothing of who did it or why, but uneasy (suspicious, looking
     * about). Whatever it held against the survivor before the beating is what it holds after it.
     */
    public void knockOut(Actor v, float dx, float dz, float seconds) {
        if (v.dead || v.isPlayer) return;
        if (v.ragdoll == null) gore.ragdolls.knockDown(v, dx, dz, 72, seconds);
        if (v.ragdoll == null) return;
        absentialis.sydney.SydneyCharacter sc = absentialis.sydney.SydneyCharacter.of(v.ragdoll);
        if (sc != null) { sc.mind.collapse(); sc.mind.stayDown = true; }
        v.unconsciousUntil = time + seconds;
        v.downUntil = time + seconds;
        v.hostileToPlayer = v.fistWasHostile;
        v.aggressor = v.fistWasHostile && v.aggressor;
        v.fistChain = 0;
        neural.remove(v.id, false);
        knockouts++;
        emitSound(v.x, v.y + 0.5f, v.z, 9, "body_fall", v.id, false);
    }
    public int knockouts;

    /** 1.3: shoves turned into stamps because what was shoved was low on the ground (a Crawler). */
    public int stomps;

    /** Thrown: fifteen feet, the way the Tank is facing. The player flies and lands; anyone else is a ragdoll through the air (1.3). */
    public void fling(Actor a, Actor v, float dx, float dz) {
        if (struckInVehicle(a, v, 20, dx, dz)) return;
        flings++;
        // a thrown body is a projectile: released from the hand's height, to come down fifteen feet off
        float vy = 4.4f, up = Math.max(0, v.y - a.y), g = Physics.GRAVITY, t = (vy + (float) Math.sqrt(vy * vy + 2 * g * up)) / g, vh = absentialis.neuralai.integration.Creatures.THROW_METRES / Math.max(0.2f, t);
        v.onGround = false;
        if (v.isPlayer) { v.vx = dx * vh; v.vz = dz * vh; v.vy = vy; v.flingUntil = time + t + 0.15f; damageShake = 1; }
        else { v.vx = dx * vh; v.vz = dz * vh; v.vy = vy; v.pvx = v.vx; v.pvz = v.vz; v.staggerUntil = 0; v.thrownAt = time; v.flingUntil = time + t + 0.5f; }
        hitKind = "blunt";
        float keepX = v.vx, keepY = v.vy, keepZ = v.vz;
        damageActor(v, a, 16 * (v.isPlayer ? difficultyDamage() : 1), null, "torso", v.x, v.y + 1, v.z);
        hitKind = "";
        if (!v.dead && v.ragdoll == null) { v.vx = keepX; v.vy = keepY; v.vz = keepZ; } else v.thrownAt = -99;
        throwRagdoll(v, dx, dz, t, keepX, keepY, keepZ);
    }

    // ------------------------------------------------------------------ throwables
    public void throwItem(ItemInstance it, float dx, float dy, float dz) { throwItem(it, dx, dy, dz, 1, false); }

    /** Throws (or, with an underbarrel launcher, fires) a grenade: power scales the velocity, impact bursts it on first contact. */
    public void throwItem(ItemInstance it, float dx, float dy, float dz, float power, boolean impact) {
        Thrown t = new Thrown();
        t.impact = impact;
        t.x = player.x + dx * THROW_REACH; t.y = player.eyeY(); t.z = player.z + dz * THROW_REACH;
        t.vx = dx * THROW_SPEED * power; t.vy = dy * THROW_SPEED * power + THROW_LIFT; t.vz = dz * THROW_SPEED * power;
        t.def = it.def(); t.owner = player.id; t.fuse = t.def.throwable.fuse;
        thrown.add(t);
        emitSound(player.x, player.y + 1, player.z, 4, "pin", player.id, true);
    }

    /** Throw speed at full power (m/s), the lift every throw is given, and how far ahead of the body it leaves the hand. */
    public static final float THROW_SPEED = 14f, THROW_LIFT = 3f, THROW_REACH = 0.5f;

    /**
     * Where a throw would go: the same start, the same speed and the same flight step as {@link #throwItem} and
     * {@link #updateThrown}, run ahead of time. Writes up to max points {x, y, z} into out and returns how many;
     * info[0] is the index of the point where it first touches down (-1 if it never does before the fuse), info[1]
     * the number of bounces. The last point is where it will be when the fuse runs out.
     */
    public int predictThrow(ItemDef def, float dx, float dy, float dz, float power, float[] out, int max, int[] info) {
        if (def == null || def.throwable == null) return 0;
        float x = player.x + dx * THROW_REACH, y = player.eyeY(), z = player.z + dz * THROW_REACH;
        float vx = dx * THROW_SPEED * power, vy = dy * THROW_SPEED * power + THROW_LIFT, vz = dz * THROW_SPEED * power;
        float fuse = def.throwable.fuse, dt = 1 / 60f;
        int n = 0, bounces = 0, land = -1, stride = Math.max(1, (int) Math.ceil(fuse / dt / Math.max(2, max - 2)));
        out[0] = x; out[1] = y; out[2] = z; n = 1;
        for (int step = 1; fuse > 0 && step < 1200; step++) {
            fuse -= dt;
            vy -= Physics.GRAVITY * dt;
            float nx = x + vx * dt, ny = y + vy * dt, nz = z + vz * dt;
            if (world.solid((int) Math.floor(nx), (int) Math.floor(y), (int) Math.floor(z))) { vx *= -0.4f; nx = x; }
            if (world.solid((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(nz))) { vz *= -0.4f; nz = z; }
            if (world.solid((int) Math.floor(nx), (int) Math.floor(ny), (int) Math.floor(nz))) { vy *= -0.35f; vx *= 0.6f; vz *= 0.6f; ny = y; bounces++; if (land < 0) { land = n; step = step - step % stride; } }
            x = nx; y = ny; z = nz;
            if ((step % stride == 0 || fuse <= 0) && n < max) { out[n * 3] = x; out[n * 3 + 1] = y; out[n * 3 + 2] = z; n++; }
        }
        if (land >= n) land = n - 1;
        if (info != null && info.length >= 2) { info[0] = land; info[1] = bounces; }
        return n;
    }

    private void updateThrown(float dt) {
        for (int ti = 0; ti < thrown.size(); ti++) {
            Thrown t = thrown.get(ti);
            t.fuse -= dt;
            if (!t.debris && t.def != null && t.def.throwable != null && t.fuse < 2.4f && time >= t.warnAt && !"flare".equals(t.def.throwable.kind) && !"noise".equals(t.def.throwable.kind)) {
                t.warnAt = time + 0.4f;
                neural.onDanger(new absentialis.neuralai.api.Vec3(t.x, t.y, t.z), time + Math.max(0.2f, t.fuse) + 0.5f, t.def.throwable.radius);
            }
            t.vy -= Physics.GRAVITY * dt;
            float nx = t.x + t.vx * dt, ny = t.y + t.vy * dt, nz = t.z + t.vz * dt;
            if (world.solid((int) Math.floor(nx), (int) Math.floor(t.y), (int) Math.floor(t.z))) { t.vx *= -0.4f; nx = t.x; }
            if (world.solid((int) Math.floor(t.x), (int) Math.floor(t.y), (int) Math.floor(nz))) { t.vz *= -0.4f; nz = t.z; }
            if (t.debris) {
                // hurled masonry: bursts on the first thing it touches
                boolean hit = world.solid((int) Math.floor(nx), (int) Math.floor(ny), (int) Math.floor(nz)) || nx == t.x && t.vx != 0 || nz == t.z && t.vz != 0;
                for (Actor a : nearby(nx, nz, 1.6f)) if (a.id != t.owner && Math.abs(a.y + 1 - ny) < 1.6f) hit = true;
                if (hit || t.fuse < -6) { t.dead = true; debrisImpact(t.x, t.y, t.z, t.owner); continue; }
                t.x = nx; t.y = ny; t.z = nz;
                continue;
            }
            if (world.solid((int) Math.floor(nx), (int) Math.floor(ny), (int) Math.floor(nz))) { if (Math.abs(t.vy) > 2) { emitSound(t.x, t.y, t.z, 6, "bounce", t.owner, false); Block on = world.block((int) Math.floor(nx), (int) Math.floor(ny), (int) Math.floor(nz)); Material bm = on == null ? Material.CONCRETE : on.mat; cue(bm == Material.WOOD || bm == Material.PLASTIC ? "bounce_wood" : bm == Material.DIRT || bm == Material.GRASS || bm == Material.FOLIAGE ? "bounce_dirt" : "bounce", t.x, t.y, t.z, 0.6f, 40); } t.vy *= -0.35f; t.vx *= 0.6f; t.vz *= 0.6f; ny = t.y; if (t.impact) t.fuse = 0; }
            if (t.impact && (nx == t.x && t.vx != 0 || nz == t.z && t.vz != 0)) t.fuse = 0;
            t.x = nx; t.y = ny; t.z = nz;
            if (t.fuse <= 0 && !t.dead) {
                t.dead = true;
                ItemDef.Throwable th = t.def.throwable;
                switch (th.kind) {
                    case "flare" -> {
                        emitSound(t.x, t.y, t.z, th.noise, "flare", t.owner, false);
                        cue("smoke_hiss", t.x, t.y, t.z, 0.6f, 60);
                        for (ExitState s : exits) if (s.exit.type == MapData.ExitType.SIGNALED && Math.abs(s.exit.x - t.x) < 14 && Math.abs(s.exit.z - t.z) < 14 && !s.signaled && s.signalTimer <= 0) { s.signalTimer = 60; msg("Flare seen - helicopter inbound to " + s.exit.name + " in 60s. Hold the area.", 0xFFE2A034); }
                        for (int i = 0; i < 30; i++) { Effect e = new Effect(); e.x = t.x; e.y = t.y; e.z = t.z; e.vx = (rng.nextFloat() - 0.5f); e.vy = 3 + rng.nextFloat() * 4; e.vz = (rng.nextFloat() - 0.5f); e.life = e.max = 2 + rng.nextFloat() * 2; e.size = 0.15f; e.color = 0xFFFF4030; e.glow = true; effects.add(e); }
                    }
                    case "noise" -> emitSound(t.x, t.y, t.z, th.noise, "alarm_clock", t.owner, false);
                    case "fire" -> incendiary(t.x, t.y, t.z, th, t.owner);
                    case "flash" -> flashbang(t.x, t.y, t.z, th, t.owner);
                    default -> explode(t.x, t.y, t.z, th.radius, th.damage, t.owner, "pipe".equals(th.kind));
                }
            }
        }
        thrown.removeIf(t -> t.dead);
    }

    /** Incendiary grenade: a burning pool that damages anything standing in it, living or dead. */
    private void incendiary(float x, float y, float z, ItemDef.Throwable th, int owner) {
        int gy = groundY((int) Math.floor(x), (int) Math.floor(z));
        Zone zn = new Zone();
        zn.x = x; zn.y = gy < 0 || Math.abs(gy - y) > 3 ? y : gy; zn.z = z; zn.radius = th.radius; zn.until = time + 9; zn.dps = th.damage; zn.kind = "fire"; zn.owner = owner;
        zones.add(zn);
        emitSound(x, y, z, th.noise, "fire_burst", owner, false);
        // the flames themselves: a pool of real fire over the area the zone burns (the zone carries the damage)
        fire.pool(x, zn.y, z, th.radius, 9, owner);
    }

    /** Flash grenade: everyone with a sight line loses their eyes for a few seconds - the infected for longer. */
    private void flashbang(float x, float y, float z, ItemDef.Throwable th, int owner) {
        emitSound(x, y, z, th.noise, "flashbang", owner, false);
        for (int i = 0; i < 30; i++) { Effect e = new Effect(); e.x = x; e.y = y + 0.3f; e.z = z; e.vx = (rng.nextFloat() - 0.5f) * 12; e.vy = rng.nextFloat() * 8; e.vz = (rng.nextFloat() - 0.5f) * 12; e.life = e.max = 0.25f + rng.nextFloat() * 0.3f; e.size = 0.2f; e.color = 0xFFFFFFFF; e.glow = true; effects.add(e); }
        for (Actor a : nearby(x, z, th.radius)) {
            float d = (float) Math.sqrt((a.x - x) * (a.x - x) + (a.eyeY() - y) * (a.eyeY() - y) + (a.z - z) * (a.z - z));
            if (d > th.radius || !Physics.los(world, x, y + 0.4f, z, a.x, a.eyeY(), a.z)) continue;
            float k = 1 - d / th.radius * 0.6f;
            if (a.isPlayer) {
                // looking away saves most of your sight
                float fx = (float) (-Math.sin(a.yaw) * Math.cos(a.pitch)), fy = (float) Math.sin(a.pitch), fz = (float) (-Math.cos(a.yaw) * Math.cos(a.pitch));
                float facing = d < 0.5f ? 1 : ((x - a.x) * fx + (y - a.eyeY()) * fy + (z - a.z) * fz) / d;
                flashBlind = Math.max(flashBlind, k * (facing > 0.2f ? 1f : 0.35f));
                continue;
            }
            a.blindUntil = time + (a.infected() ? 8 : 5) * k;
            a.awareness = 0;
            a.staggerUntil = Math.max(a.staggerUntil, time + 2.2f * k);
            if (!a.infected()) a.morale -= 0.15f;
            // 0.9: the flash knocks a body back from where it went off - simulated, where the body can be
            { float bx = a.x - x, bz = a.z - z, bl = Math.max(0.01f, (float) Math.sqrt(bx * bx + bz * bz)); if (k < 0.55f || !gore.ragdolls.unbalance(a, bx / bl, bz / bl, 10 + 14 * k, "head")) { if (a.anim != null) a.anim.play("stumble", 0.8f); } }
            neural.nudge(a.id);
        }
    }

    // ------------------------------------------------------------------ locked drawers (Small Key)
    /** One desk or cabinet in six is locked until a Small Key opens it; the roll is fixed by position. */
    public boolean isLocked(int x, int y, int z) {
        int id = world.get(x, y, z);
        if (id != Block.DESK && id != Block.CABINET && id != Block.FILING) return false;
        if (containers.containsKey(World.idx(x, y, z))) return false;
        return Rng.hash(x, y, z, (int) (profile.seed & 0xFFFF) + 77) < 0.17f;
    }

    /** Spends a Small Key on a locked drawer. Returns false when the player has none. */
    public boolean unlock(int x, int y, int z) {
        for (ItemInstance k : new ArrayList<>(profile.loadout.keys)) if ("key.small".equals(k.defId)) { InventoryService.consume(k, 1); unlocked.add(World.idx(x, y, z)); return true; }
        return false;
    }

    private final java.util.Set<Integer> unlocked = new java.util.HashSet<>();

    // ------------------------------------------------------------------ the player's element (Red / Blue / Gold)
    public static final int MATE_SQUAD = 777777;

    /**
     * Deploys the active roster: same individuals, their own level of skill, and (0.7) the standard kit of their type -
     * the weapon in their hands, the armour and helmet they wear, the sidearm and the pack they carry.
     * They always come in behind the player: two files stepping back from the leader's heels, never ahead or beside.
     */
    private void deploySquad() {
        List<absentialis.gameplay.squad.Roster.Mate> act = profile.squad.active();
        float fx = (float) -Math.sin(player.yaw), fz = (float) -Math.cos(player.yaw), rx = (float) Math.cos(player.yaw), rz = (float) -Math.sin(player.yaw);
        for (int i = 0; i < act.size(); i++) {
            absentialis.gameplay.squad.Roster.Mate m = act.get(i);
            float[] p = null;
            // behind, in pairs; if a wall is there, further back, then closer in - but never in front of the leader
            float[][] tries = {{2.2f + (i / 2) * 1.6f, (i % 2 == 0 ? -1 : 1) * 1.1f}, {3.4f + (i / 2) * 1.6f, (i % 2 == 0 ? -1 : 1) * 0.6f}, {1.4f + i * 0.5f, 0}, {5f + i, 0}};
            for (float[] t : tries) {
                float wx = player.x - fx * t[0] + rx * t[1], wz = player.z - fz * t[0] + rz * t[1];
                float[] q = standNear(wx, wz);
                if (q == null || Math.abs(q[1] - player.y) > 2.5f) continue;
                if ((q[0] - player.x) * fx + (q[2] - player.z) * fz > -0.4f) continue;        // not behind
                if (!Physics.los(world, player.x, player.y + 1.2f, player.z, q[0], q[1] + 1.2f, q[2])) continue;   // not through a wall
                p = q; break;
            }
            if (p == null) p = new float[]{player.x - fx * 0.9f, player.y, player.z - fz * 0.9f};
            Actor a = new Actor();
            a.faction = "player";
            a.mateId = m.id; a.team = m.team; a.name = m.name.contains("\"") ? m.name.substring(m.name.indexOf('"') + 1, m.name.lastIndexOf('"')) : m.name;
            a.appearance = m.look;
            a.x = p[0]; a.y = p[1]; a.z = p[2]; a.yaw = player.yaw;
            a.homeX = a.x; a.homeZ = a.z;
            a.weapon = armed(m.weapon, rng);
            a.weapon.setCondition(95);
            for (String id : m.build) Gunsmith.install(a.weapon, id);
            ItemDef wd = a.weapon.def();
            a.archetype = wd.weapon.family.equals("shotgun") ? "shotgunner" : "sniper".equals(m.type) || m.weapon.equals("weapon.mosin") || m.weapon.equals("weapon.sr16") ? "marksman" : "rifleman";
            shape(a);
            a.hp = a.maxHp = m.maxHp();
            // the armour and helmet of the kit are what stop rounds: no more flat issue vest
            ItemDef ad = m.armor.isEmpty() ? null : Content.def(m.armor), hd = m.head.isEmpty() ? null : Content.def(m.head);
            if (ad != null && ad.armor != null) { a.armorClass = Math.max(1, ad.armor.armorClass); a.armorDur = a.armorMax = Math.max(ad.armor.durability, ad.armor.shield); }
            a.helmet = hd != null && hd.armor != null && hd.armor.armorClass > 0;
            if (hd != null && !a.helmet) a.look += "head:helmet.cap;";
            if (!m.sidearm.isEmpty() && Content.def(m.sidearm) != null) { a.sidearm = armed(m.sidearm, rng); a.sidearm.setCondition(95); }
            for (String e : m.pack) {
                int cpos = e.lastIndexOf(':');
                String id = cpos > 0 ? e.substring(0, cpos) : e;
                ItemDef pd = Content.def(id);
                if (pd == null) continue;
                int left = cpos > 0 ? Integer.parseInt(e.substring(cpos + 1)) : 1;
                while (left > 0) { ItemInstance it = new ItemInstance(id); it.qty = pd.stackable() ? Math.min(pd.maxStack, left) : 1; it.origin = raidId; left -= it.qty; a.carried.add(it); }
            }
            a.wornArmor = ad == null ? "" : ad.id; a.wornHead = hd == null ? "" : hd.id;
            a.squad = MATE_SQUAD;
            a.morale = 1; a.risk = "defender".equals(m.type) ? 0.35f : "breacher".equals(m.type) ? 0.7f : 0.5f;
            a.look = "mate:" + m.id + ":" + a.look;
            a.mateType = m.type;
            m.raids++;
            actors.add(a);
            mates.add(a);
            element.add(a);
        }
    }

    /** Living squadmates addressed by a team name: "red", "blue", or "gold" for the whole deployed element. */
    public List<Actor> team(String team) {
        List<Actor> l = new ArrayList<>();
        for (Actor a : mates) if (a.active() && ("gold".equals(team) || a.team.equals(team))) l.add(a);
        return l;
    }

    /**
     * Gives a team an order (follow, hold, move, sweep). A queued order is held by that team until Gold
     * issues Initiate, so Red and Blue can be set up for different objectives and released together.
     * Returns how many squadmates took it.
     */
    public int squadOrder(String team, String order, float x, float z, boolean queued) {
        List<Actor> t = team(team);
        for (int i = 0; i < t.size(); i++) {
            Actor a = t.get(i);
            float ox = x + (float) Math.sin(i * 2.4) * (1.2f + i * 0.5f), oz = z + (float) Math.cos(i * 2.4) * (1.2f + i * 0.5f);
            if (Float.isNaN(x)) { ox = a.x; oz = a.z; }   // "hold": each where they stand
            if (queued) { a.qOrder = order; a.qX = ox; a.qZ = oz; }
            else { a.sqOrder = order; a.sqX = ox; a.sqZ = oz; a.qOrder = null; a.hasGoal = false; a.path = null; neural.order(a.id, order, "follow".equals(order) ? null : new absentialis.neuralai.api.Vec3(ox, a.y, oz)); }
        }
        if (!t.isEmpty() && !queued && ("move".equals(order) || "sweep".equals(order)) && !Float.isNaN(x)) markOrder(x, z, team, order);
        if (!t.isEmpty() && !queued) say(t.get(0), switch (order) { case "hold" -> "Holding."; case "move" -> "Moving."; case "sweep" -> "Sweeping it."; default -> "On you."; }, "radio_chatter");
        return t.size();
    }

    /**
     * 0.8: where the squad was last sent, as a thing in the world - a marker standing on the ground at the spot, in the
     * team's colour, that fades out over a few seconds. {x, y, z, raid time, team (0 gold, 1 red, 2 blue), kind (0 move, 1 sweep)}.
     */
    public final List<float[]> orderMarks = new ArrayList<>();
    /** The height of what the leader was pointing at when the next order was given (NaN = not known: the leader's own floor). */
    public float orderHintY = Float.NaN;
    public static float ORDER_MARK_SECONDS = 4.5f;

    /** How solid a marker still is, 1 .. 0: whole for the first two fifths of its life, then fading out. */
    public float orderMarkAlpha(float[] m) {
        float age = time - m[3];
        if (age < 0 || age >= ORDER_MARK_SECONDS) return 0;
        float hold = ORDER_MARK_SECONDS * 0.4f;
        return age <= hold ? 1 : 1 - (age - hold) / (ORDER_MARK_SECONDS - hold);
    }

    private void markOrder(float x, float z, String team, String order) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        // on the floor that was pointed at (an upper storey, a roof), not the one the leader happens to be standing on
        int y = standY(ix, iz, Float.isNaN(orderHintY) ? Math.round(player.y) : Math.round(orderHintY));
        orderHintY = Float.NaN;
        if (y < 0) y = Math.round(player.y);
        orderMarks.removeIf(m -> time - m[3] > ORDER_MARK_SECONDS);
        orderMarks.add(new float[]{x, y, z, time, "red".equals(team) ? 1 : "blue".equals(team) ? 2 : 0, "sweep".equals(order) ? 1 : 0});
        if (orderMarks.size() > 6) orderMarks.remove(0);
    }

    /** 0.8, hold fire: a team told to hold fire keeps its weapons down unless it has to save itself. Per team; Gold speaks for everyone. */
    private final Map<String, Boolean> fireHeld = new HashMap<>();
    public boolean holdFire(String team) { return "gold".equals(team) ? fireHeld.getOrDefault("red", false) && fireHeld.getOrDefault("blue", false) : fireHeld.getOrDefault(team, false); }

    /** Sets a team's fire order. Returns how many squadmates took it. */
    public int setHoldFire(String team, boolean hold) {
        if ("gold".equals(team)) { fireHeld.put("red", hold); fireHeld.put("blue", hold); }
        fireHeld.put(team, hold);
        List<Actor> t = team(team);
        for (Actor a : t) neural.holdFire(a.id, hold);
        if (!t.isEmpty()) say(t.get(0), hold ? "Holding fire." : "Weapons free.", "radio_chatter");
        return t.size();
    }

    /** Gold's Initiate: every team executes whatever it has queued, at the same moment. */
    public int initiate() {
        int n = 0;
        for (Actor a : mates) if (!a.dead && a.qOrder != null) { a.sqOrder = a.qOrder; a.sqX = a.qX; a.sqZ = a.qZ; a.qOrder = null; a.hasGoal = false; a.path = null; if ("move".equals(a.sqOrder) || "sweep".equals(a.sqOrder)) markOrder(a.sqX, a.sqZ, a.team, a.sqOrder); neural.order(a.id, a.sqOrder, "follow".equals(a.sqOrder) ? null : new absentialis.neuralai.api.Vec3(a.sqX, a.y, a.sqZ)); n++; }
        if (n > 0 && !mates.isEmpty()) msg("GOLD: Initiate - " + n + " executing", 0xFFE2B030);
        return n;
    }

    /** Moves a deployed squadmate between Red and Blue during the operation (four per team at most). */
    public String reassign(Actor a) {
        String to = "red".equals(a.team) ? "blue" : "red";
        if (team(to).size() >= absentialis.gameplay.squad.Roster.TEAM_CAP) return (to.equals("red") ? "Red" : "Blue") + " Team is full";
        a.team = to;
        absentialis.gameplay.squad.Roster.Mate m = profile.squad.byId(a.mateId);
        if (m != null) m.team = to;
        return a.name + " -> " + (to.equals("red") ? "Red" : "Blue") + " Team";
    }

    // ------------------------------------------------------------------ squads (shared tactical picture)
    public Squad squad(Actor a) { return a.squad == 0 || a.infected() || a.isPlayer ? null : squadOf(a.squad, a.faction); }

    private Squad squadOf(int id, String faction) {
        Squad s = squads.get(id);
        if (s == null) {
            s = new Squad();
            s.id = id; s.faction = faction;
            int n = 0;
            for (Actor m : actors) if (m.squad == id && m.faction.equals(faction) && !m.dead) n++;
            s.initial = s.alive = Math.max(1, n);
            squads.put(id, s);
        }
        return s;
    }

    /** Living members of an agent's squad, in a stable order. */
    public List<Actor> squadMembers(Actor a) {
        List<Actor> l = new ArrayList<>();
        for (Actor m : actors) if (!m.dead && !m.isPlayer && m.squad == a.squad && m.faction.equals(a.faction)) l.add(m);
        l.sort(java.util.Comparator.comparingInt(m -> m.id));
        return l;
    }

    public int squadRank(Actor a) { int k = 0; for (Actor m : actors) if (!m.dead && m.squad == a.squad && m.faction.equals(a.faction) && m.id < a.id) k++; return k; }

    private void countSquads() {
        for (Squad s : squads.values()) s.alive = 0;
        for (Actor a : actors) if (!a.dead && !a.isPlayer && a.squad != 0) { Squad s = squads.get(a.squad); if (s != null && s.faction.equals(a.faction)) s.alive++; }
        for (Squad s : squads.values()) s.initial = Math.max(s.initial, s.alive);
    }

    /** A fixed loop on the street graph from the squad's post: same nodes, same order, every time. */
    private void buildRoute(Squad sq, float x, float z) {
        MapData.Node n = world.map.nearestNode(x, z);
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int k = 0; k < 5 && n != null; k++) {
            seen.add(n.id);
            sq.route.add(new float[]{n.x + 0.5f, n.z + 0.5f});
            MapData.Node next = null;
            for (int l : n.links) { MapData.Node c = world.map.nodes.get(l); if (!seen.contains(c.id) && Math.abs(c.x - x) < 70 && Math.abs(c.z - z) < 70 && (next == null || c.id < next.id)) next = c; }
            n = next;
        }
    }

    private static String speaker(Actor a) {
        if (!a.name.isEmpty()) return a.name;
        return switch (a.faction) { case "military" -> "Soldier"; case "raiders" -> "Bandit"; case "mercenaries" -> "Mercenary"; case "survivors" -> "Loner"; default -> ZoneState.cap(a.faction); };
    }

    /** A spoken line: a real sound others can hear, shown to the player when in earshot. */
    public void say(Actor a, String text, String category) {
        if (a == null) { msg(text, 0xFFB8D048); return; }   // no speaker: a status line for the player alone
        if (time - a.lastSay < 2.5f) return;
        a.lastSay = time;
        a.sayText = text; a.sayAt = time; a.sayUntil = time + combat.talkBase + text.length() * combat.talkPerChar;
        said++;
        // "bark_*" names the voice sample for this kind of callout; to other agents every spoken line is a voice
        boolean bark = category.startsWith("bark_");
        emitSound(a.x, a.y + 1.6f, a.z, category.equals("radio_chatter") ? 14 : 30, bark ? "speech" : category, a.id, false);
        if (listener == null) return;
        if (bark) listener.voice(a, category);
        if (a.mate() && a.dist(player) > 12) listener.message(speaker(a) + ": " + text, 0xFF7DBA5C);
    }

    /** Lines spoken this raid (tests: communication must actually happen). */
    public int said;

    /** How strong a target looks to someone sizing it up: armed friends nearby count. */
    public float strengthNear(Actor t, String observerFaction) {
        float s = t.isPlayer ? (t.weapon != null && t.weapon.def().weapon != null && !t.weapon.def().weapon.melee() ? 1.6f : 0.8f) : 1f;
        for (Actor o : nearby(t.x, t.z, 16)) if (o != t && !o.infected() && !o.isPlayer && o.faction.equals(t.faction) && o.weapon != null) s += 1;
        return s;
    }

    /** How hard it is raining, 0..1 (1.0: the weather system's own figure - it builds and dies away). */
    public float rain() {
        // (a save or a test that sets the old rain figure without naming a kind of weather still gets its rain)
        String k = profile.zone.weather;
        float legacy = profile.zone.weatherRain > 0.5f && !"rain".equals(k) && !"storm".equals(k) ? (profile.zone.weatherRain - 0.5f) * 2 : 0;
        return Math.max(weather.rain, legacy);
    }
    /** Extra haze of a district beyond a clear day (0..1). */
    /**
     * How far anyone can see before the fog bank closes (0.6): the same bank the player looks into, nearer at night
     * and in rain. Nobody is seen, or shot at, from the other side of it.
     */
    public float fogSight() {
        absentialis.gameplay.CombatConfig c = absentialis.gameplay.CombatConfig.get();
        return fogSightOf(c);
    }

    /** 0.9: the mist of the hour, 0..1 - thickest in the morning, heavy all night, none in the afternoon. */
    public float mist() { return ZoneState.mistAt(hour()); }

    /** How much nearer than its clear-day distance the fog bank stands now: night, rain and the mist of the hour each bring it in. */
    public float fogNear() {
        absentialis.gameplay.CombatConfig c = absentialis.gameplay.CombatConfig.get();
        return (1 - (1 - c.fogNight) * (1 - daylight())) * (1 - (1 - c.fogRain) * rain()) * (1 - (1 - c.fogMistWall) * mist());
    }

    private float fogSightOf(absentialis.gameplay.CombatConfig c) {
        return Math.min(236f, c.fogEnd * fogNear()) * c.seeFog;
    }

    public float fogAt(float x, float z) { return Math.max(0, Math.min(1, (world.district(x, z).fogDensity - 0.8f) / 0.6f)); }

    /** Ground shock felt by the player (giant footfalls, charges, debris). */
    public void quake(float x, float z, float range, float amount) {
        float d = player.distXZ(x, z);
        if (d < range) damageShake = Math.max(damageShake, amount * (1 - d / range));
    }

    // ------------------------------------------------------------------ restricted areas, extortion, loners
    private List<MapData.Point> restricted;

    /** True when the player stands in a military restricted area without the standing that passes for a permit. */
    public boolean restrictedFor(float x, float z) {
        if (profile.rep("military") >= 25) return false;
        if (restricted == null) {
            restricted = new ArrayList<>();
            for (MapData.Point p : world.map.posts) if ("military".equals(p.faction) && (p.kind.equals("camp") || p.kind.equals("leader") || p.kind.equals("restricted"))) restricted.add(p);
        }
        for (MapData.Point p : restricted) { float dx = x - p.x, dz = z - p.z; if (dx * dx + dz * dz < 16 * 16) return true; }
        return false;
    }

    /** Ignoring the warning is a recorded offence: standing drops and the garrison turns on you. */
    /** Caught in a restricted area by a soldier: the garrison that holds the ground hears of it. */
    public void trespass(Actor by) { trespass(); if (by != null) rivalry.onTrespass(by); }

    public void trespass() {
        trespasses++;
        debrief.repDelta.merge("military", -4f, Float::sum);
        msg("You are now a trespasser in a restricted area.", 0xFFD8483A);
    }

    /** The squad currently holding the player up, if any. */
    public Squad extortion() {
        for (Squad s : squads.values()) if (s.extortDone && !s.paid && !s.bloodied && time < s.extortUntil) return s;
        return null;
    }

    /** Pays a bandit hold-up. The money leaves the profile when the raid resolves. */
    public String payExtortion() {
        Squad s = extortion();
        if (s == null) return null;
        if (profile.scrip - scripPaid < EXTORT_PRICE) return "You do not have " + absentialis.Version.coins(EXTORT_PRICE);
        scripPaid += EXTORT_PRICE;
        s.paid = true;
        rivalry.onTribute(s.id, EXTORT_PRICE);
        return "Paid " + absentialis.Version.coins(EXTORT_PRICE) + ". They lower their weapons.";
    }

    /** A wounded loner who can be helped right now (within reach), or null. */
    /**
     * Someone to trade with: the nearest person in reach and in front of the player who is on their feet, awake, not
     * fighting and not hostile - a squadmate, or anyone whose side is not shooting at you.
     */
    public Actor tradePartner(float reach) {
        Actor best = null;
        float bd = reach;
        float fx = (float) -Math.sin(player.yaw), fz = (float) -Math.cos(player.yaw);
        for (Actor a : nearby(player.x, player.z, reach)) {
            if (a.isPlayer || a.dead || a.infected() || a.downed() || a.seated || a.ragdoll != null || a.crawling) continue;
            if (!a.mate() && (a.hostileToPlayer || "engage".equals(a.state) || "flee".equals(a.state) || "extort".equals(a.state) || "warn".equals(a.state))) continue;
            if (!a.mate() && absentialis.sim.Agents.asleep(a.routine) && time > a.wakeUntil) continue;
            float dx = a.x - player.x, dz = a.z - player.z, d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d < 0.05f || Math.abs(a.y - player.y) > 1.4f || (dx * fx + dz * fz) / d < 0.55f) continue;
            if (d < bd) { bd = d; best = a; }
        }
        return best;
    }

    /**
     * What a character has to trade. A squadmate's pack is what it carries. Anyone else has what they carry and
     * what somebody of their side would have on them - drawn once, the first time they are asked, and from then on
     * it is theirs: what is traded away is gone, what is left is what their body will have on it.
     */
    public Container wares(Actor a) {
        if (a.wares != null) return a.wares;
        Container c = new Container("wares:" + a.id, 8, 7);
        c.label = a.name == null || a.name.isEmpty() ? displayName(a) : a.name;
        for (ItemInstance it : new ArrayList<>(a.carried)) InventoryService.add(it, c);
        a.carried.clear();
        if (!a.mate()) {
            Map<String, List<String>> kit = Content.KITS.get(a.faction);
            String table = kit != null && kit.get("loot") != null ? kit.get("loot").get(0) : "body";
            Rng wr = new Rng(a.id * 7919L + profile.seed);
            for (Object[] e : Content.roll(table, wr, profile.zone.availability(world.district(a.x, a.z)), 0)) addRolled(c, (String) e[0], (Integer) e[1], a.faction);
            for (Object[] e : Content.roll("body", wr, null, 0.3f)) addRolled(c, (String) e[0], (Integer) e[1], null);
        }
        a.wares = c;
        return c;
    }

    public Actor lonerInNeed(float r) {
        for (Actor a : nearby(player.x, player.z, r)) if (!a.isPlayer && a.needsHelp && !a.helped && !a.hostileToPlayer && a.dist(player) < r) return a;
        return null;
    }

    /** Gives a bandage to a wounded loner: standing, experience and a marked stash in return. */
    public String helpLoner(Actor a) {
        ItemInstance bandage = null;
        for (ItemInstance i : profile.loadout.all()) if ("med.bandage".equals(i.defId) && i.parent == null) { bandage = i; break; }
        if (bandage == null) return "You have no bandage to give";
        InventoryService.consume(bandage, 1);
        a.helped = true; a.needsHelp = false;
        a.hp = Math.min(a.maxHp, a.hp + 35);
        rivalry.onAid(a, false);
        debrief.repDelta.merge("survivors", 3f, Float::sum);
        debrief.aided++;
        int n = profile.discover(a.homeX, a.homeZ, 7);
        debrief.cellsRevealed += n;
        emitSound(a.x, a.y + 1, a.z, 5, "search", player.id, true);
        return "The loner patches up and marks what they know on your map" + (n > 0 ? " (" + n + " cells)" : "");
    }

    /** Puts a thing on a body in the slot it belongs in. False when it belongs in none, or that slot is taken (it goes in the pockets instead). */
    private static boolean wear(absentialis.gameplay.inventory.Loadout gear, ItemInstance it) {
        String slot = it.def().slot;
        if (slot == null || !gear.slots.containsKey(slot) || gear.slots.get(slot) != null) return false;
        it.detachLocation();
        gear.slots.put(slot, it); it.slot = slot; it.slotOwner = gear;
        return true;
    }

    /** Everything still on a body: its equipment, then what is in its pockets. */
    public static List<ItemInstance> onBody(Actor corpse) {
        List<ItemInstance> l = new ArrayList<>();
        if (corpse.corpseGear != null) for (ItemInstance it : corpse.corpseGear.slots.values()) if (it != null) l.add(it);
        if (corpse.corpse != null) l.addAll(corpse.corpse.items);
        return l;
    }

    /** A faction looter strips a corpse. Contractors take the hardware; bandits take anything worth carrying. */
    public void npcLoot(Actor a, Actor corpse, boolean hardwareOnly) {
        if (corpse.corpse == null) return;
        if (corpse.corpseGear != null) for (ItemInstance it : new ArrayList<>(corpse.corpseGear.slots.values())) if (it != null) { InventoryService.release(it); a.carried.add(it); }   // equipment is hardware
        for (ItemInstance it : new ArrayList<>(corpse.corpse.items)) {
            ItemDef d = it.def();
            boolean hw = d.weapon != null || d.ammo != null || d.magazine != null || d.armor != null;
            if (hw || !hardwareOnly && d.value >= 20) { InventoryService.release(it); a.carried.add(it); }
        }
        emitSound(corpse.x, corpse.y + 0.5f, corpse.z, 7, "search", a.id, false);
    }

    private Map<Integer, List<MapData.LootSpot>> lootIndex;

    /** A street-level searchable container near a point (loners scavenge what is actually there). */
    public MapData.LootSpot lootSpotNear(float x, float z, float r, int salt) {
        if (lootIndex == null) {
            lootIndex = new HashMap<>();
            for (MapData.LootSpot s : world.map.loot) if (Math.abs(s.y - World.GROUND) <= 1) lootIndex.computeIfAbsent((s.x >> 5) * 64 + (s.z >> 5), k -> new ArrayList<>()).add(s);
        }
        List<MapData.LootSpot> cand = new ArrayList<>();
        int cx = (int) x >> 5, cz = (int) z >> 5;
        for (int a = cx - 1; a <= cx + 1; a++) for (int b = cz - 1; b <= cz + 1; b++) {
            List<MapData.LootSpot> l = lootIndex.get(a * 64 + b);
            if (l != null) for (MapData.LootSpot s : l) if (Math.abs(s.x - x) < r && Math.abs(s.z - z) < r) cand.add(s);
        }
        return cand.isEmpty() ? null : cand.get(Math.floorMod(salt * 31, cand.size()));
    }

    // ------------------------------------------------------------------ infected specials
    /** Fat infected bile: lands where the target stood when the retch began - move and it misses. */
    public void spit(Actor a, float x, float y, float z) {
        int gy = groundY((int) Math.floor(x), (int) Math.floor(z));
        Zone zn = new Zone();
        zn.x = x; zn.y = gy < 0 ? y : gy; zn.z = z; zn.radius = 2.6f; zn.until = time + 9; zn.dps = 9; zn.kind = "acid"; zn.owner = a.id;
        zones.add(zn);
        // people get off ground that is burning them: that is what the pool is for
        neural.onDanger(new absentialis.neuralai.api.Vec3(x, zn.y, z), time + 9, 3.4);
        emitSound(x, zn.y + 0.5f, z, 20, "splash", a.id, false);
        for (int i = 0; i < 26; i++) {
            float t = i / 26f;
            Effect e = new Effect();
            e.x = a.x + (x - a.x) * t; e.z = a.z + (z - a.z) * t; e.y = a.y + 1.6f + (zn.y - a.y - 1.6f) * t + (float) Math.sin(t * Math.PI) * 1.4f;
            e.vx = (rng.nextFloat() - 0.5f); e.vy = rng.nextFloat(); e.vz = (rng.nextFloat() - 0.5f);
            e.life = e.max = 0.5f + rng.nextFloat() * 0.4f; e.size = 0.1f; e.color = 0xFFB4D040; e.glow = true;
            effects.add(e);
        }
    }

    /** 1.1, the mutated spider's spit: a black pool where it lands, which burns what stands in it and is seen coming. */
    public void venom(float sx, float sy, float sz, float x, float y, float z) {
        int gy = standY((int) Math.floor(x), (int) Math.floor(z), Math.round(y));
        Zone zn = new Zone();
        zn.x = x; zn.y = gy < 0 ? y : gy; zn.z = z; zn.radius = 2.2f; zn.until = time + 8; zn.dps = 8; zn.kind = "venom"; zn.owner = 0;
        zones.add(zn);
        venomPools++;
        neural.onDanger(new absentialis.neuralai.api.Vec3(x, zn.y, z), time + 8, 3.0);
        emitSound(x, zn.y + 0.5f, z, 18, "splash", 0, false);
        for (int i = 0; i < 22; i++) {
            float t = i / 22f;
            Effect e = new Effect();
            e.x = sx + (x - sx) * t; e.z = sz + (z - sz) * t; e.y = sy + (zn.y - sy) * t + (float) Math.sin(t * Math.PI) * 1.2f;
            e.vx = (rng.nextFloat() - 0.5f); e.vy = rng.nextFloat(); e.vz = (rng.nextFloat() - 0.5f);
            e.life = e.max = 0.5f + rng.nextFloat() * 0.4f; e.size = 0.1f; e.color = 0xFF2A3A16;
            effects.add(e);
        }
    }
    public int venomPools;

    /** Giant debris throw: a ballistic chunk of masonry aimed at where the target stood. */
    public void hurl(Actor a, float x, float y, float z) {
        Thrown t = new Thrown();
        t.debris = true; t.owner = a.id;
        t.x = a.x; t.y = a.y + a.stand + 0.4f; t.z = a.z;
        float dx = x - t.x, dz = z - t.z, dy = y + 0.8f - t.y, dist = (float) Math.sqrt(dx * dx + dz * dz);
        float T = Math.max(0.7f, Math.min(1.9f, dist / 15f));
        t.vx = dx / T; t.vz = dz / T; t.vy = (dy + 0.5f * Physics.GRAVITY * T * T) / T;
        thrown.add(t);
        emitSound(a.x, a.y + 2, a.z, 30, "debris", a.id, false);
    }

    private void debrisImpact(float x, float y, float z, int owner) {
        emitSound(x, y, z, 60, "debris", owner, false);
        quake(x, z, 22, 0.8f);
        for (Actor a : nearby(x, z, 4)) {
            if (a.id == owner) continue;
            float d = (float) Math.sqrt((a.x - x) * (a.x - x) + (a.y + 1 - y) * (a.y + 1 - y) + (a.z - z) * (a.z - z));
            if (d < 2.8f) damageActor(a, actor(owner), 42 * (1 - d / 3.4f) * difficultyDamage(), null, "torso", a.x, a.y + 1, a.z);
        }
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            int bx = (int) Math.floor(x) + dx, by = (int) Math.floor(y) + dy, bz = (int) Math.floor(z) + dz;
            Block b = world.block(bx, by, bz);
            if (b != null && b.hp > 0 && b.hp < 400) damageBlock(bx, by, bz, 9999, owner);
        }
        impact(x, y, z, Material.CONCRETE, 26);
    }

    /** The giant walks through light cover: fences, furniture, sandbags, wooden walls in its path give way. */
    /** A block that is part of a building's wall: a full cube standing in a column of them (not a crate, a counter or a fence). 1.0: such a wall is not smashed through - only breached at a door. */
    private boolean wallOfBuilding(Block b, int x, int y, int z) {
        if (b.shape != absentialis.world.Shapes.CUBE || b.is(Block.DOOR)) return false;
        Block up = world.block(x, y + 1, z), up2 = world.block(x, y + 2, z);
        return up != null && up.solid() && up.shape == absentialis.world.Shapes.CUBE && up2 != null && up2.solid();
    }

    /** 1.0: doorways a big body has made its own way through (gates, the debrief). */
    public int breaches;

    /**
     * 1.0: a body too big for a door, at a door. Nothing about it is arranged beforehand: wherever one of the big ones is
     * stopped with a door in front of it - going in after someone, or coming out - it goes through the wall the door is in.
     * The door goes, and with it the timber round it: a block either side and the lintel over it, as high as the thing is
     * tall. Only timber gives. A door in brick or concrete is a door it cannot use, and it has to find another way.
     * Returns true when it has made the opening.
     */
    public boolean breach(Actor a) {
        float fx = (float) -Math.sin(a.yaw), fz = (float) -Math.cos(a.yaw);
        int feet = (int) Math.floor(a.y + 0.05f), door = Integer.MIN_VALUE, dbx = 0, dbz = 0;
        for (float s = 0.5f; s <= 2.4f && door == Integer.MIN_VALUE; s += 0.3f) for (int side = -1; side <= 1 && door == Integer.MIN_VALUE; side++) for (int dy = 0; dy <= 1; dy++) {
            int bx = (int) Math.floor(a.x + fx * s - fz * side * 0.7f), bz = (int) Math.floor(a.z + fz * s + fx * side * 0.7f);
            Block b = world.block(bx, feet + dy, bz);
            if (b != null && b.is(Block.DOOR)) { door = feet + dy; dbx = bx; dbz = bz; break; }
        }
        if (door == Integer.MIN_VALUE) return false;
        // the wall runs across the way it is going
        boolean alongZ = Math.abs(fx) > Math.abs(fz);
        int high = (int) Math.ceil(a.stand) + 1, half = a.radius > 0.5f ? 1 : 1, broke = 0;
        for (int depth = 0; depth <= 1; depth++) for (int side = -half; side <= half; side++) for (int dy = 0; dy < high; dy++) {
            int bx = dbx + (alongZ ? depth * (fx > 0 ? 1 : -1) : side), bz = dbz + (alongZ ? side : depth * (fz > 0 ? 1 : -1)), by = feet + dy;
            Block b = world.block(bx, by, bz);
            if (b == null || !b.solid() && !b.is(Block.DOOR)) continue;
            if (depth == 1 && (b.is(Block.DOOR) || b.mat != Material.WOOD)) continue;
            if (!b.is(Block.DOOR) && (b.mat != Material.WOOD || b.hp <= 0)) continue;       // only timber gives
            damageBlock(bx, by, bz, 99999, a.id);
            if (world.solid(bx, by, bz)) world.set(bx, by, bz, Block.AIR);        // (whatever a blow leaves standing of it, the body takes with it)
            if (world.block(bx, by, bz) == null || !world.solid(bx, by, bz)) {
                broke++;
            }
        }
        if (broke == 0) return false;
        breaches++;
        a.stuckTime = 0;
        quake(a.x, a.z, 26, 0.7f);
        emitSound(dbx + 0.5f, door + 1, dbz + 0.5f, 60, "door_break", a.id, false);
        if (this.fx != null) {
            float cx = dbx + 0.5f, cz = dbz + 0.5f;
            this.fx.debris(cx, feet + 1.4f, cz, (float) -Math.sin(a.yaw), 0.35f, (float) -Math.cos(a.yaw), 46, 7.5f, 0x6E5238, 0.11f);
            this.fx.dust(cx, feet + 1.2f, cz, (float) -Math.sin(a.yaw), 0.2f, (float) -Math.cos(a.yaw), 22, 0.9f, 0x9A8F7C, 0.55f);
            this.fx.dust(cx, feet + 0.3f, cz, 0, 1, 0, 12, 0.7f, 0x8A8070, 0.5f);
        }
        if (a.anim != null && !a.anim.playing("giant_smash")) a.anim.play("giant_smash", 0);
        return true;
    }

    public void smash(Actor a) {
        // 1.0: at a door, the wall it is in (if it is timber); anywhere else, only what is light - furniture, fences, boards
        if (Archetypes.huge(a.archetype) && breach(a)) return;
        float fx = (float) -Math.sin(a.yaw), fz = (float) -Math.cos(a.yaw);
        boolean broke = false;
        for (float s = 0.8f; s <= 1.6f; s += 0.8f) for (int side = -1; side <= 1; side++) for (int dy = 0; dy <= 2; dy++) {
            int bx = (int) Math.floor(a.x + fx * s - fz * side * 0.6f), by = (int) Math.floor(a.y) + dy, bz = (int) Math.floor(a.z + fz * s + fx * side * 0.6f);
            Block b = world.block(bx, by, bz);
            if (b != null && b.solid() && b.hp > 0 && b.hp < 400 && !wallOfBuilding(b, bx, by, bz)) { damageBlock(bx, by, bz, 9999, a.id); broke = true; }
        }
        if (broke) { a.stuckTime = 0; quake(a.x, a.z, 20, 0.5f); if (a.anim != null && !a.anim.playing("giant_smash")) a.anim.play("giant_smash", 0); }
    }

    private void updateZones(float dt) {
        zoneTimer -= dt;
        boolean tick = zoneTimer <= 0;
        if (tick) zoneTimer = 0.5f;
        for (int zi = 0; zi < zones.size(); zi++) {   // by index: a bomber dying in a zone adds its own contamination zone
            Zone z = zones.get(zi);
            if (!z.kind.equals("fire") && rng.chance(dt * 14)) {
                float ang = rng.range(0f, 6.28f), rad = rng.nextFloat() * z.radius;
                Effect e = new Effect();
                e.x = z.x + (float) Math.sin(ang) * rad; e.z = z.z + (float) Math.cos(ang) * rad; e.y = z.y + 0.1f;
                e.vx = 0; e.vz = 0; e.vy = 3.4f + rng.nextFloat() * (z.kind.equals("contam") ? 2.5f : 0.8f);
                e.life = e.max = 0.5f + rng.nextFloat() * 0.5f; e.size = z.kind.equals("contam") ? 0.16f : z.kind.equals("fire") ? 0.18f : 0.1f; e.color = z.kind.equals("bile") ? Gore.BILE : z.kind.equals("contam") ? 0xC08CA83A : z.kind.equals("fire") ? (rng.chance(0.5) ? 0xFFFF9A28 : 0xFFE0481C) : 0xFFB4D040; e.glow = true;
                effects.add(e);
            }
            if (!tick) continue;
            for (Actor a : nearby(z.x, z.z, z.radius + 1)) {
                if (a.infected() && !z.kind.equals("fire") || a.distXZ(z.x, z.z) > z.radius || Math.abs(a.y - z.y) > 2.2f) continue;
                if (!z.kind.equals("fire") && !zoneReaches(z, a.x, a.y, a.z)) continue;          // 1.1: not through a wall
                if (z.kind.equals("bile")) { bile(a, BILE_SECONDS); continue; }       // bile does no damage: it marks you
                if (a.isPlayer && god) continue;
                a.hp -= z.dps * 0.5f * (a.isPlayer ? difficultyDamage() : 1);
                a.hurtFlash = 0.15f;
                a.staggerUntil = time + 0.6f;
                if (a.isPlayer) {
                    damageShake = Math.min(1, damageShake + 0.25f);
                    body.healthChanged = time;
                    if (z.kind.equals("contam")) body.infection = Math.min(100, body.infection + 1.5f);
                    if (time - z.tick > 3) { z.tick = time; msg(z.kind.equals("contam") ? "Contaminated ground - get clear!" : z.kind.equals("fire") ? "You are on fire - move!" : "Acid! Move!", 0xFFB8D048); }
                } else if (a.hp <= 0) finish(a, actor(z.owner), z.kind.equals("fire") ? "burn" : "acid");
            }
        }
        zones.removeIf(z -> time > z.until);
    }

    /** 1.1: whether a pool on the ground reaches a spot: open floor there, and nothing solid between it and where the pool landed. */
    public boolean zoneReaches(Zone z, float x, float y, float zz) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(z.y + 0.1f), iz = (int) Math.floor(zz);
        if (world.solid(ix, iy, iz)) return false;
        if (Math.abs(x - z.x) + Math.abs(zz - z.z) < 0.9f) return true;
        return Physics.los(world, z.x, z.y + 0.45f, z.z, x, z.y + 0.45f, zz);
    }

    /** True if the point lies in a hazard zone (HUD warning / AI avoidance). */
    public Zone zoneAt(float x, float y, float z) {
        for (Zone zn : zones) if (Math.abs(y - zn.y) < 2.2f && (x - zn.x) * (x - zn.x) + (z - zn.z) * (z - zn.z) < zn.radius * zn.radius) return zn;
        return null;
    }

    // ------------------------------------------------------------------ ragdolls
    private final Pose deathPose = new Pose();
    private final Mat4 rdBase = new Mat4(), rdHips = new Mat4();
    private final Mat4[] rdBones = new Mat4[Rig.GROUPS];

    /**
     * Death hands the body from animation to a constrained ragdoll: it starts in the exact pose the
     * actor died in, takes an impulse along the hit direction and settles within eight seconds.
     * Bodies far from the player skip the visible simulation and are placed already at rest.
     */
    /** The transforms of a character's twelve skeletal regions right now (animation pose, or the ragdoll if it has the body). Shared scratch. */
    public Mat4[] bones(Actor v) {
        if (rdBones[0] == null) for (int g = 0; g < Rig.GROUPS; g++) rdBones[g] = new Mat4();
        if (v.ragdoll != null) { v.ragdoll.bones(rdBones); return rdBones; }
        // 1.3: the skeleton that was drawn this frame or the last, when there is one - what the player aimed at
        if (!v.dead && v.drawnBones != null && time - v.drawnAt >= 0 && time - v.drawnAt < 0.12f) {
            for (int g = 0; g < Rig.GROUPS; g++) System.arraycopy(v.drawnBones, g * 16, rdBones[g].m, 0, 16);
            return rdBones;
        }
        if (v.anim == null) shapeAnim(v);
        float s = Rig.VOX * v.anim.scale;
        v.anim.pose(v, deathPose, 0, null, false);
        rdBase.identity().translate(v.x, v.y + v.anim.visY, v.z).rotateY(v.facing()).scale(s, s, s);
        Rig.fk(deathPose, rdBase, rdHips, rdBones);
        return rdBones;
    }

    void startRagdoll(Actor v) {
        if (v.anim == null) shapeAnim(v);
        if (rdBones[0] == null) for (int g = 0; g < Rig.GROUPS; g++) rdBones[g] = new Mat4();
        float scale = v.anim.scale, s = Rig.VOX * scale;
        v.anim.pose(v, deathPose, 0, null, false);
        rdBase.identity().translate(v.x, v.y + v.anim.visY, v.z).rotateY(v.facing()).scale(s, s, s);
        Rig.fk(deathPose, rdBase, rdHips, rdBones);
        Ragdoll r = new Ragdoll(scale);
        r.fromPose(rdBones, v.vx, v.vy, v.vz);
        if (r.unbury(world) > 0) unburied++;
        float push = softRagdoll ? 0 : Math.max(1.4f, Math.min(7.5f, 1.2f + v.hitForce / 22f));
        if (!softRagdoll) r.impulse(v.hitRegion, v.hitDirX * push, 0.6f + push * 0.22f, v.hitDirZ * push);
        v.ragdoll = r;
        // 0.6, the Sydney Engine: a body is not handed to a passive ragdoll but to an active one - the same rig, with senses
        // and muscles. The living fight for their balance; the dead let go over a moment instead of at once.
        // 1.1: every body, wherever it is. (One too far off to be watched used to get a passive ragdoll, resolved at once;
        // it is Sydney's all the same now, run forward to where it comes to rest.) The player's own body is Sydney's when
        // it dies and - new - when it has been falling too long to land on its feet.
        boolean far = v.tier == 1 || v.distXZ(player.x, player.z) > 55;
        if (sydneyEnabled && (v.dead || !v.isPlayer || playerSydney)) {
            absentialis.sydney.SydneyCharacter c = absentialis.sydney.SydneyCharacter.attach(r, sydneyKind(v), v.anat == null ? 0 : v.anat.missingMask, v.limpMask);
            c.wound = softRagdoll ? -1 : absentialis.sydney.SydneyCharacter.particleOf(regionGroup(v.hitRegion), false);
            if (!softRagdoll) c.mind.onHit(c, v.hitForce);
            if (v.dead) c.die();
            sydneyBodies++;
        } else passiveBodies++;
        if (far && (v.dead || !sydneyEnabled)) {
            r.fastForward(world);
            v.x = r.x[Ragdoll.PELVIS]; v.z = r.z[Ragdoll.PELVIS]; v.y = Math.max(0, r.lowestY());
        }
    }

    /** Which of Sydney's bodies a character is: what it weighs, how it keeps its feet, and how it gets up. */
    public static String sydneyKind(Actor v) {
        if ("tank".equals(v.archetype) || "tyrant".equals(v.archetype)) return "giant";
        if ("crawler".equals(v.archetype) || "spider".equals(v.archetype)) return "crawler";
        return v.infected() ? "infected" : "human";
    }

    /** 1.1: bodies lifted out of the floor as they were handed to physics, bodies given a ragdoll nothing drives (none, with Sydney on), and living bodies found under the world and put back on it. */
    public int unburied, passiveBodies, buriedRescues;
    /** Set for the length of one {@link #startRagdoll}: the body is handed over as it stands, with no blow behind it (a collapse, a long fall); and the living player may be given to Sydney. */
    boolean softRagdoll, playerSydney;
    /** Seconds of falling after which a body is no longer going to land on its feet: it is Sydney's from then on. */
    public static float FALL_RAGDOLL_AFTER = 3f;
    /** How fast (m/s) a body may come down and still be standing after it: faster, and an NPC goes down in a heap. */
    public static float HARD_LANDING = 13f;

    /**
     * 1.1, falls. Anybody who has been in the air, going down, for {@link #FALL_RAGDOLL_AFTER} seconds is given to
     * Sydney where they are - the player too: they fall as a body falls, limbs working at the air, and what the ground
     * does to them is worked out there. And a living body found inside the world or under it (it happens to what goes
     * on all fours, and to anything handed back from physics in a bad place) is put back on the nearest floor.
     */
    private void falls(float dt) {
        for (int i = 0; i < actors.size(); i++) {
            Actor a = actors.get(i);
            if (a.dead || a.seated || a.cling || a.hanging || a.mantling() || a.ragdoll != null || a.tier != 0 && !a.isPlayer) { a.airTime = 0; continue; }
            if (a.isPlayer && (noclip || devGhost || vehicles.inside() || vehicles.busy())) { a.airTime = 0; continue; }
            boolean falling = !a.onGround && !a.climbing && !a.mantling() && a.vy < -4f && time >= a.pounceUntil && a.breachT <= 0;
            a.airTime = falling ? a.airTime + dt : 0;
            if (a.airTime >= FALL_RAGDOLL_AFTER) { a.airTime = 0; gore.ragdolls.fallRagdoll(a); continue; }
            // buried: the cell its middle is in is solid, and so is the one its feet are in (a stair under the feet is not being buried)
            if (!a.isPlayer && ((a.id + frameNo) & 15) == 0) {
                int bx = (int) Math.floor(a.x), bz = (int) Math.floor(a.z), fy = (int) Math.floor(a.y + 0.05f), my = (int) Math.floor(a.y + a.height() * 0.5f);
                boolean inside = world.solid(bx, my, bz) && world.solid(bx, fy, bz) && !world.block(bx, fy, bz).stair() && !world.block(bx, my, bz).is(Block.DOOR);
                if (inside || a.y < 1.5f) {
                    int gy = standY(bx, bz, Math.max(2, my + 1));
                    if (gy > 0) { a.y = gy; a.vy = 0; a.onGround = true; a.fallSpeed = 0; a.path = null; a.hasGoal = false; buriedRescues++; }
                }
            }
        }
        frameNo++;
    }
    private int frameNo;

    // ------------------------------------------------------------------ 0.9: the silent takedown
    /** How near the knife has to be, how long the whole thing takes, and the moment in it the blade goes in (seconds). */
    public static float TAKEDOWN_REACH = 1.9f, TAKEDOWN_SECONDS = 1.15f, TAKEDOWN_KILL_AT = 0.62f;
    /** The body being taken down (null when none is), and how far into it. */
    public Actor takedown;
    public float takedownT;
    private boolean takedownDone, silentKill;
    /** Takedowns finished, and those somebody saw (tests). */
    public int takedowns, takedownsSeen;
    /** Why the last thing looked at could not be taken down (shown under the prompt; empty when it can). */
    public String takedownWhy = "";

    /** A knife in the melee slot: what a silent takedown is done with. */
    public boolean hasKnife() {
        ItemInstance m = profile.loadout.slot("melee");
        if (m == null || m.def().weapon == null) return false;
        String id = m.defId, model = m.def().weapon.model == null ? "" : m.def().weapon.model;
        return id.contains("knife") || model.contains("knife") || id.contains("bayonet") || id.contains("dagger");
    }

    /** Whether somebody knows the player is there right now: its mind is fighting the player and has them in sight. */
    public boolean awareOfPlayer(Actor a) {
        absentialis.neuralai.runtime.Mind m = neural.mind(a.id);
        if (m == null) return false;
        if (absentialis.sim.Agents.asleep(a.routine) && time > a.wakeUntil) return false;
        var vm = m.meters.get((long) player.id);
        return vm != null && vm.confirmed() && time - vm.lastExposed < 1.5 || m.state == absentialis.neuralai.decision.CogState.COMBAT && m.belief.targetId() == player.id && m.belief.visible();
    }

    /**
     * 0.9: who the player could put down silently where they stand. It takes a knife; the body has to be within arm's
     * reach and in front of the player, on its feet, a person or one of the ordinary dead (nobody gets an arm round a Tank,
     * a Gladiator or a Butcher, and a Bomber is not something to stab at arm's length); the player has to be behind it;
     * and it must not know they are there. Someone who is no enemy is only taken down from a crouch - it is a decision,
     * not something a key press in passing does. Null when there is nobody; {@link #takedownWhy} then says what was missing
     * if someone was close.
     */
    public Actor takedownTarget() {
        takedownWhy = "";
        if (takedown != null || player.dead || player.prone || !player.onGround || vehicles.inside() || !playerKind.isEmpty() || playerIgnored()) return null;
        float fx = (float) -Math.sin(player.yaw), fz = (float) -Math.cos(player.yaw);
        Actor best = null;
        float bd = TAKEDOWN_REACH;
        for (Actor a : actors) {
            if (a.isPlayer || a.dead || a.mate() || a.tier != 0 || a.ragdoll != null || a.seated || a.downed() || Math.abs(a.y - player.y) > 0.9f) continue;
            float dx = a.x - player.x, dz = a.z - player.z, d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d >= bd || d < 0.05f || (dx * fx + dz * fz) / d < 0.55f) continue;
            bd = d; best = a;
        }
        if (best == null) return null;
        Actor a = best;
        if (!hasKnife()) { takedownWhy = ""; return null; }                    // without a knife there is nothing to offer, and nothing is said
        if (Archetypes.huge(a.archetype) || "bomber".equals(a.archetype)) { takedownWhy = "too big to take down"; return null; }
        if ("crawler".equals(a.archetype)) { takedownWhy = "nothing to take hold of"; return null; }
        // behind it: the way it faces points away from the player
        float tx = (float) -Math.sin(a.yaw), tz = (float) -Math.cos(a.yaw), bx = player.x - a.x, bz = player.z - a.z, bl = Math.max(0.05f, (float) Math.sqrt(bx * bx + bz * bz));
        boolean asleep = absentialis.sim.Agents.asleep(a.routine) && time > a.wakeUntil;
        if (!asleep && (tx * bx + tz * bz) / bl > -0.2f) { takedownWhy = "get behind them"; return null; }
        if (awareOfPlayer(a)) { takedownWhy = "they know you are there"; return null; }
        boolean enemy = a.infected() || a.hostileToPlayer || Relations.hostile(this, a, player);
        if (!enemy && !player.crouch) { takedownWhy = "crouch to take down someone who is no enemy"; return null; }
        if (!Physics.sight(world, player.x, player.eyeY(), player.z, a.x, a.y + a.height() * 0.6f, a.z)) return null;
        return a;
    }

    /** Begins a silent takedown of whoever {@link #takedownTarget} offers. False when there is nobody. */
    public boolean startTakedown() {
        Actor a = takedownTarget();
        if (a == null) return false;
        takedown = a; takedownT = 0; takedownDone = false;
        a.staggerUntil = Math.max(a.staggerUntil, time + TAKEDOWN_SECONDS);
        a.fireCooldown = Math.max(a.fireCooldown, TAKEDOWN_SECONDS + 0.3f); a.burstLeft = 0;
        a.vx = a.vz = 0; a.hasGoal = false; a.path = null;
        player.vx = player.vz = 0;
        if (player.anim != null) player.anim.play("melee", TAKEDOWN_SECONDS * 0.8f);
        // a hand over the mouth: what is heard of it does not carry past the next room
        cue("grab", a.x, a.y + 1.4f, a.z, 0.35f, 8);
        return true;
    }

    private void updateTakedown(float dt) {
        Actor a = takedown;
        if (a == null) return;
        if (player.dead || a.dead && !takedownDone || !actors.contains(a)) { takedown = null; return; }
        takedownT += dt;
        // the player closes up behind the body and holds it; neither goes anywhere
        float tx = (float) -Math.sin(a.yaw), tz = (float) -Math.cos(a.yaw);
        float wx = a.x - tx * 0.62f, wz = a.z - tz * 0.62f, k = Math.min(1, dt * 9);
        if (!takedownDone && Physics.boxFree(world, player.x + (wx - player.x) * k, player.y, player.z + (wz - player.z) * k, player.radius, player.height())) { player.x += (wx - player.x) * k; player.z += (wz - player.z) * k; }
        player.vx = player.vz = 0;
        if (!takedownDone) { a.vx = a.vz = 0; a.staggerUntil = Math.max(a.staggerUntil, time + 0.3f); }
        if (!takedownDone && takedownT >= TAKEDOWN_KILL_AT) {
            takedownDone = true;
            takedowns++;
            cue("knife_stab", a.x, a.y + 1.3f, a.z, 0.45f, 7);
            emitSound(a.x, a.y + 1, a.z, 3, "melee", player.id, false);          // a scuffle that carries three metres
            a.hitUnaware = true; a.hitRegion = "torso"; a.hitForce = 2; a.hitDirX = tx * 0.3f; a.hitDirZ = tz * 0.3f;
            a.lastAttacker = player.id;
            lastKind = "blade";
            int seenBefore = takedownsSeen;
            silentKill = true;
            try { a.hp = 0; kill(a, player); } finally { silentKill = false; }
            debrief.stealthKills++;
            boolean seen = takedownsSeen > seenBefore;
            msg(seen ? "Takedown - but it was seen" : "Silent takedown  +" + RaidResolver.XP_STEALTH_KILL + " XP", seen ? 0xFFE2A034 : 0xFF8FD070);
            profile.stat("stealth_kills", 1);
        }
        if (takedownT >= TAKEDOWN_SECONDS) takedown = null;
    }

    /** How many sounds of each kind the raid has made (tests). */
    private final java.util.Map<String, Integer> soundCounts = new java.util.HashMap<>();
    public int soundSequenceCount(String kind) { return soundCounts.getOrDefault(kind, 0); }
    /** Kills a character outright, by nobody (tests and scripted runs). */
    public void debugKill(Actor a) { if (a == null || a.dead) return; a.hp = 0; kill(a, a.lastAttacker == player.id ? player : null); }

    /** 0.9: pull-ups onto ledges, the player's and everybody else's (tests). */
    public int mantles;

    /** 0.9: rounds that went into bodies on the ground, and bodies a blast threw (tests). */
    public int corpseHits, corpsesThrown;

    /** The Sydney Engine drives bodies under physics (off: the 0.5 passive ragdoll, kept for comparison - console "sydney off"). */
    public boolean sydneyEnabled = true;
    /** Bodies handed to Sydney this raid. */
    public int sydneyBodies;

    private static int regionGroup(String region) {
        return switch (region == null ? "" : region) { case "head" -> Rig.HEAD; case "arms" -> Rig.R_UPPER_ARM; case "legs" -> Rig.R_THIGH; default -> Rig.TORSO; };
    }

    private void footstep(Actor a, float speed) {
        if (listener == null) return;
        Block feet = world.block((int) Math.floor(a.x), (int) Math.floor(a.y + 0.2f), (int) Math.floor(a.z));
        Block under = world.block((int) Math.floor(a.x), (int) Math.floor(a.y - 0.1f), (int) Math.floor(a.z));
        String mat = feet != null && feet.mat == Material.WATER ? "water" : WorldAcoustics.stepSurface(under);
        if (!"none".equals(mat)) listener.footstep(a, mat, speed);
    }

    // ------------------------------------------------------------------ noise propagation service (A-Life milestone)
    private static final java.util.Set<String> LOUD = java.util.Set.of("shot", "explosion", "alarm_clock", "screech");

    /**
     * One loud action, several systems: (1) agents in earshot perceive it and investigate (done in
     * emitSound), (2) infected beyond earshot migrate toward it - live ones get a vague heading,
     * abstract cell populations shift a cell closer, (3) the faction holding the district sends its
     * nearest uncommitted squad, (4) district-level infected pressure redistributes and the event is
     * logged. Sustained noise is required: heat builds per cell and decays.
     */
    private void noiseEvent(float x, float y, float z, float radius, String category, int source) {
        if (radius < 40 || !LOUD.contains(category)) return;
        int c = cellOf(x, z);
        heat[c] += radius / 120f;
        if (heat[c] < 3) return;
        heat[c] = -3;   // the same cell needs a fresh burst before it draws another response
        noiseEvents++;
        District d = world.district(x, z);
        migrate(c, x, y, z, radius);
        String ctrl = profile.zone.controller(d);
        Actor src = actor(source);
        boolean sent = ctrl != null && dispatch(ctrl, x, z, src == null ? 0 : src.squad, "noise");
        profile.zone.reportNoise(d, radius);
        profile.zone.log("noise", d, (int) x, (int) z, src == null ? "" : src.faction, sent ? ctrl : "", "Sustained gunfire in the " + d.display + (sent ? " - " + ZoneState.cap(ctrl) + " responded" : ""));
    }

    private void migrate(int c, float x, float y, float z, float radius) {
        // live infected just beyond earshot: a vague pull toward the noise, not a position fix
        int drawn = 0;
        for (Actor a : actors) {
            if (drawn >= 8) break;
            if (a.dead || !a.infected() || "engage".equals(a.state)) continue;
            float d = a.distXZ(x, z);
            if (d < radius * 0.9f || d > radius * 2.4f) continue;
            a.memX = x + (rng.nextFloat() - 0.5f) * 24; a.memZ = z + (rng.nextFloat() - 0.5f) * 24; a.memY = y; a.memTime = time; a.memConfidence = 0.3f; a.memErr = 12;
            if (!"investigate".equals(a.state)) { a.state = "investigate"; a.stateTime = 0; a.hasGoal = false; }
            drawn++; migrations++;
        }
        // abstract populations: every nearby folded cell sends one of its infected a cell closer
        int cx = c / CELLS, cz = c % CELLS;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int ax = cx + dx, az = cz + dz;
            if ((dx == 0 && dz == 0) || ax < 0 || az < 0 || ax >= CELLS || az >= CELLS) continue;
            int[] from = cells.get(ax * CELLS + az);
            if (from == null || from[0] <= 0) continue;
            int tx = ax - Integer.signum(dx), tz = az - Integer.signum(dz), to = tx * CELLS + tz;
            if (!profile.agents.shift(ax * CELLS + az, to, "infected", rng)) continue;
            from[0]--;
            cells.computeIfAbsent(to, k -> new int[5])[0]++;
            migrations++;
            if (activeCells.contains(to) && populate) spawnOne(to, 0);
        }
    }

    /** Materialises one unit of a cell's abstract population at its far edge (never on top of the player). */
    private boolean spawnOne(int c, int k) {
        for (absentialis.sim.Agents.Agent ag : profile.agents.agents) {
            if (!ag.alive || ag.actorId != 0 || ag.cell() != c || !ag.faction.equals(POP_FACTION[k])) continue;
            if (materialize(ag, c, 45)) { int[] pop = cells.get(c); if (pop != null && pop[k] > 0) pop[k]--; return true; }
        }
        return false;
    }

    /**
     * Sends the nearest uncommitted squad of a faction to a position (patrol response, reinforcement).
     * Live squads get a move order; if none is loaded, abstract population shifts a cell toward it.
     */
    public boolean dispatch(String faction, float x, float z, int exceptSquad, String reason) {
        Actor best = null;
        float bd = 260;
        for (Actor a : actors) {
            if (a.dead || a.isPlayer || !a.faction.equals(faction) || a.squad == exceptSquad || a.hasOrder) continue;
            if (!"idle".equals(a.state)) continue;
            float d = a.distXZ(x, z);
            if (d > 12 && d < bd) { bd = d; best = a; }
        }
        if (best != null) {
            for (Actor m : squadMembers(best)) {
                if (!"idle".equals(m.state)) continue;
                m.hasOrder = true; m.orderX = x + rng.range(-4f, 4f); m.orderZ = z + rng.range(-4f, 4f); m.orderUntil = time + 120;
            }
            dispatches++;
            return true;
        }
        int k = 0;
        for (int i = 1; i < POP_FACTION.length; i++) if (POP_FACTION[i].equals(faction)) k = i;
        if (k == 0) return false;
        int tc = cellOf(x, z), tx = tc / CELLS, tz = tc % CELLS, bestCell = -1, bestD = 99;
        for (Map.Entry<Integer, int[]> e : cells.entrySet()) {
            if (e.getValue()[k] <= 0 || e.getKey() == tc) continue;
            int d = Math.max(Math.abs(e.getKey() / CELLS - tx), Math.abs(e.getKey() % CELLS - tz));
            if (d < bestD && d <= 4) { bestD = d; bestCell = e.getKey(); }
        }
        if (bestCell < 0) return false;
        int bx = bestCell / CELLS, bz = bestCell % CELLS, to = (bx + Integer.signum(tx - bx)) * CELLS + bz + Integer.signum(tz - bz);
        int n = Math.min(2, cells.get(bestCell)[k]);
        for (int i = 0; i < n; i++) profile.agents.shift(bestCell, to, faction, rng);
        cells.get(bestCell)[k] -= n;
        cells.computeIfAbsent(to, kk -> new int[5])[k] += n;
        if (activeCells.contains(to) && populate) for (int i = 0; i < n; i++) spawnOne(to, k);
        dispatches++;
        return true;
    }

    /** Abstract population of a strategic cell (tests / debug): {infected, military, bandits, loners, mercenaries}. */
    public int[] cellPopulation(int cx, int cz) { int[] p = cells.get(cx * CELLS + cz); return p == null ? new int[5] : p.clone(); }

    // ------------------------------------------------------------------ strategic -> active evidence
    /**
     * Whatever the strategic tier resolved while this district was unloaded leaves marks that match
     * the log: bodies and smoke where a fight was lost, sandbags where a roadblock went up.
     */
    private void seedEvidence() {
        ZoneState z = profile.zone;
        Rng r = new Rng(profile.seed ^ z.day * 7919L);
        int placed = 0;
        for (absentialis.sim.ALife.Event e : z.log) {
            if (placed >= 8 || e.age(z.day, z.hour) > 36) continue;
            int y = groundY(e.x(), e.z());
            if (y < 0) continue;
            switch (e.type()) {
                case "battle", "overrun" -> {
                    String loser = e.type().equals("battle") ? e.b() : e.a();
                    int bodies = 2 + r.nextInt(2);
                    for (int i = 0; i < bodies; i++) corpseAt(loser, e.x() + r.range(-4f, 4f), e.z() + r.range(-4f, 4f), r);
                    if (e.type().equals("overrun")) corpseAt("infected", e.x() + r.range(-3f, 3f), e.z() + r.range(-3f, 3f), r);
                    smoulder(e.x() + 0.5f, y, e.z() + 0.5f);
                    evidence.add(e);
                    placed++;
                }
                case "roadblock" -> {
                    for (int k = -3; k <= 3; k++) {
                        int bx = e.x() + k, by = groundY(bx, e.z());
                        if (by < 0 || Math.abs(by - y) > 1 || world.get(bx, by, e.z()) != 0) continue;
                        if (k == 0) continue;   // a gap to walk through
                        world.set(bx, by, e.z(), Math.abs(k) == 1 ? Block.BARRIER : Block.SANDBAG);
                        if (Math.abs(k) >= 2 && world.get(bx, by + 1, e.z()) == 0) world.set(bx, by + 1, e.z(), Block.SANDBAG);
                    }
                    evidence.add(e);
                    placed++;
                }
                default -> {}
            }
        }
    }

    /** Strategic log entries that were turned into physical evidence this raid. */
    public final List<absentialis.sim.ALife.Event> evidence = new ArrayList<>();

    private void corpseAt(String faction, float x, float z, Rng r) {
        int y = groundY((int) Math.floor(x), (int) Math.floor(z));
        if (y < 0) return;
        Actor a = makeActor(faction, x, y, z, r);
        a.cell = cellOf(x, z);
        a.tier = 1;
        a.hitDirX = r.range(-1f, 1f); a.hitDirZ = r.range(-1f, 1f); a.hitForce = 30;
        actors.add(a);
        a.hp = 0;
        deaths--;                 // evidence bodies were never part of this raid's living population
        kill(a, null);
        a.deadTime = 30;
    }

    // ------------------------------------------------------------------ loot & interaction
    /** Lazily rolls a searchable block's contents (stockpile-aware) the first time it is opened. */
    public Container openContainer(int x, int y, int z) {
        int key = World.idx(x, y, z);
        Container c = containers.get(key);
        if (c != null) return c;
        Block b = world.block(x, y, z);
        MapData.LootSpot spot = world.map.lootAt(x, y, z);
        String table = unlocked.contains(key) ? "locked" : spot != null ? spot.table : b.containerType != null ? b.containerType : "crate";
        District d = world.district(x, z);
        c = new Container("box:" + key, 8, 6);
        c.label = ZoneState.cap(b.name.replace('_', ' '));
        float depletion = 1f - Math.min(1f, avgSupply(d));
        for (Object[] e : Content.roll(table, new Rng(profile.seed ^ key ^ profile.zone.day * 31L), profile.zone.availability(d), depletion)) {
            addRolled(c, (String) e[0], (Integer) e[1], null);
            profile.zone.reportLoot(d, Content.supplyCategory(Content.def((String) e[0])), 1);
        }
        containers.put(key, c);
        debrief.containersSearched++;
        profile.contracts.onSearch(world.district(x + 0.5f, z + 0.5f));
        if (story != null) story.onContainer(x, y, z, c);
        if (chains != null) chains.onContainer(x, y, z, c);
        bus.emit(new Events.ContainerSearched(key, table, x, y, z));
        emitSound(x + 0.5f, y + 0.5f, z + 0.5f, 6, "search", player.id, false);
        return c;
    }

    private float avgSupply(District d) { float s = 0; for (float v : profile.zone.supply[d.ordinal()]) s += v; return s / ZoneState.SUPPLY.length; }

    public void dropItem(ItemInstance it, float x, float y, float z) {
        WorldItem w = new WorldItem();
        w.it = it; w.x = x; w.y = y; w.z = z;
        items.add(w);
    }

    public WorldItem nearestItem(float x, float y, float z, float r) {
        WorldItem best = null; float bd = r * r;
        for (WorldItem w : items) { float d = (w.x - x) * (w.x - x) + (w.y - y) * (w.y - y) + (w.z - z) * (w.z - z); if (d < bd) { bd = d; best = w; } }
        return best;
    }

    /**
     * The nearest body the infected would eat: any dead person that has come to rest - a stranger, a squadmate, and
     * (0.8) the player too. Not one of their own kind. The player's body is eaten at but never removed from the world.
     */
    public Actor nearestMeal(float x, float z, float r) {
        Actor best = null; float bd = r;
        for (Actor a : actors) if (a.dead && (!a.infected() || a.turned) && a.eaten < 1f && a.ragdoll != null && a.ragdoll.settled) { float d = a.distXZ(x, z); if (d < bd) { bd = d; best = a; } }
        return best;
    }

    // ------------------------------------------------------------------ 1.0: the survivor's own body is worth going through
    /** Who is going through the survivor's kit (null: nobody), how long they have been at it, and what they have taken so far (names, for the death screen). */
    public Actor corpseLooter;
    public float corpseLootT, deadFor;
    public final List<String> corpseLootTaken = new ArrayList<>();
    private boolean corpseLootDecided;
    private float corpseLootNext;
    /** The chance the one who did the killing comes over to take what the survivor had; the chance someone else of theirs does if not; how long it takes; how many things they take. */
    public static float LOOT_KILLER_CHANCE = 0.65f, LOOT_OTHER_CHANCE = 0.35f, LOOT_SECONDS = 4.5f;
    public static int LOOT_TAKES = 3;

    /** The survivor is dead: a moment later, someone who was shooting at them may decide the body is worth the walk. People only - the dead do not loot. */
    private void lootTheDead(float dt) {
        deadFor += dt;
        if (corpseLootDecided || deadFor < 1.2f) return;
        corpseLootDecided = true;
        Actor k = actor(player.lastAttacker);
        if (k != null && !k.dead && !k.infected() && !k.mate() && k.ragdoll == null && k.dist(player) < 70 && rng.chance(LOOT_KILLER_CHANCE)) { corpseLooter = k; return; }
        if (!rng.chance(LOOT_OTHER_CHANCE)) return;
        float bd = 40;
        for (Actor o : actors) { if (o.dead || o.isPlayer || o.infected() || o.mate() || !o.hostileToPlayer || o.ragdoll != null) continue; float d = o.dist(player); if (d < bd) { bd = d; corpseLooter = o; } }
    }

    /**
     * One frame of someone looting the survivor: they walk to the body, sling their weapon, get down by it and go through
     * it - a thing every second or so, the best first - and then go back to what they were doing. Returns false when they
     * are done (or cannot), and their own mind has them again.
     */
    /** 1.1: where the survivor's body is lying (the ragdoll's hips once there is one; the survivor's own position until then). */
    public float corpseX() { return player.ragdoll != null ? player.ragdoll.x[absentialis.character.Ragdoll.PELVIS] : player.x; }
    public float corpseY() { return player.ragdoll != null ? (float) Math.floor(player.ragdoll.y[absentialis.character.Ragdoll.PELVIS] + 0.2f) : player.y; }
    public float corpseZ() { return player.ragdoll != null ? player.ragdoll.z[absentialis.character.Ragdoll.PELVIS] : player.z; }

    private boolean driveLooter(Actor a, float dt) {
        if (a.dead || a.ragdoll != null || !player.dead || corpseLootT > LOOT_SECONDS + 1 || deadFor > 40) { endLooting(a); return false; }
        // 1.1: to where the body is, not to where it was standing when it died (it may have been thrown, or have fallen a storey)
        float cx = corpseX(), cy = corpseY(), cz = corpseZ();
        float d = a.distXZ(cx, cz);
        a.state = "loot";
        if (d > 1.35f || Math.abs(a.y - cy) > 1.6f) {
            absentialis.neuralai.integration.Steering.goTo(this, a, cx, cz, 2.8f, dt);
            a.crouch = false;
            return true;
        }
        absentialis.neuralai.integration.Steering.stop(a);
        a.vx = a.vz = 0;
        a.yaw = absentialis.neuralai.integration.Steering.turn(a.yaw, (float) Math.atan2(-(cx - a.x), -(cz - a.z)), dt * 6);
        a.crouch = true; a.handItem = "search";
        if (a.anim != null && !a.anim.playing("loot_body")) a.anim.play("loot_body", 1.2f);
        corpseLootT += dt;
        if (corpseLootT >= corpseLootNext && corpseLootTaken.size() < LOOT_TAKES) {
            corpseLootNext = corpseLootT + LOOT_SECONDS / (LOOT_TAKES + 0.5f);
            ItemInstance best = null; int bv = 0;
            for (ItemInstance it : profile.loadout.all()) { if (it.def().questItem || "key".equals(it.def().category) || "document".equals(it.def().category)) continue; int v = it.baseValueTotal() + (it.slot != null ? 100000 : 0); if (v > bv) { bv = v; best = it; } }
            if (best != null) {
                corpseLootTaken.add(best.def().stackable() && best.qty > 1 ? best.name() + " x" + best.qty : best.name());
                InventoryService.release(best);
                if (best == player.weapon) player.weapon = null;
                emitSound(cx, cy + 0.3f, cz, 8, "search", a.id, false);
            }
        }
        if (corpseLootT >= LOOT_SECONDS) { endLooting(a); return false; }
        return true;
    }

    private void endLooting(Actor a) { if (corpseLooter == a) corpseLooter = null; a.crouch = false; a.handItem = null; if (a.anim != null && a.anim.playing("loot_body")) a.anim.release(); if ("loot".equals(a.state)) a.state = "idle"; }

    /** 1.0: how long a body lies before it is cleared (seconds), and how near the survivor has to be for it to be left longer (metres). */
    public static float CORPSE_SECONDS = 80f, CORPSE_NEAR = 36f;
    public int corpsesCleared;

    /** A body that has lain long enough is taken out of the world. Only what matters to an operation is left where it lay: keys, papers, anything a contract asks for. */
    private void clearCorpse(Actor corpse) {
        if (!actors.contains(corpse)) return;
        for (ItemInstance it : onBody(corpse)) { String cat = it.def().category; if ("quest".equals(cat) || "key".equals(cat) || "document".equals(cat)) { InventoryService.release(it); dropItem(it, corpse.x, corpse.y + 0.2f, corpse.z); } }
        actors.remove(corpse); neural.remove(corpse.id, false); neuralBody.forget(corpse.id);
        corpsesCleared++;
    }

    /** A corpse has been eaten to nothing: what it carried is left where it lay, and the body is gone from the world. */
    public void consume(Actor corpse) {
        if (!actors.contains(corpse)) return;
        for (ItemInstance it : onBody(corpse)) { InventoryService.release(it); dropItem(it, corpse.x + rng.range(-0.4f, 0.4f), corpse.y + 0.2f, corpse.z + rng.range(-0.4f, 0.4f)); }
        actors.remove(corpse);
        gore.corpsesConsumed++;
    }

    /**
     * 0.8: the body the player could search from where they stand - within reach, on the same floor (0.7 measured across
     * the ground only, and offered the body lying in the room below), and with nothing solid between it and the eye.
     */
    public Actor searchableCorpse(float reach) {
        Actor best = null; float bd = reach;
        for (Actor a : actors) {
            if (!a.dead || a.corpse == null || a.isPlayer || Math.abs(a.y - player.y) > SEARCH_STOREY) continue;
            float d = a.distXZ(player.x, player.z);
            if (d >= bd) continue;
            if (!Physics.sight(world, player.x, player.eyeY(), player.z, a.x, a.y + 0.35f, a.z) && !Physics.sight(world, player.x, player.y + 0.4f, player.z, a.x, a.y + 0.35f, a.z)) continue;
            bd = d; best = a;
        }
        return best;
    }
    /** How far above or below the player's feet a body may lie and still be in reach (a step, a kerb, a bed - not a storey). */
    public static float SEARCH_STOREY = 1.35f;

    public Actor nearestCorpse(float x, float z, float r) {
        Actor best = null; float bd = r;
        for (Actor a : actors) if (a.dead && a.corpse != null && !a.isPlayer) { float d = a.distXZ(x, z); if (d < bd) { bd = d; best = a; } }
        return best;
    }

    /** Toggles a two-block door (and its partner above/below). */
    public void toggleDoor(int x, int y, int z) { toggleDoor(x, y, z, player); }

    /** Opens or closes a door leaf; whoever worked the handle is the source of the sound. */
    public void toggleDoor(int x, int y, int z, Actor by) {
        Doors.Door d = doors.at(x, y, z);
        if (d != null) doors.use(d, by);
    }

    /**
     * 0.7: a one-block opening within reach along a line of sight - a window with the glass out, a gap knocked in a wall -
     * that a Wooden Plank would close, or null. The same test the infected use to decide what they can climb through.
     */
    public int[] barricadeSpot(float ex, float ey, float ez, float dx, float dy, float dz) {
        int ax = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? 1 : -1) : 0, az = ax == 0 ? (dz > 0 ? 1 : -1) : 0;
        for (float t = 0.3f; t <= 3.0f; t += 0.15f) {
            int x = (int) Math.floor(ex + dx * t), y = (int) Math.floor(ey + dy * t), z = (int) Math.floor(ez + dz * t);
            if (world.solid(x, y, z)) return null;
            if (absentialis.neuralai.integration.Breach.hole(world, x, y, z, ax, az)) return new int[]{x, y, z};
        }
        return null;
    }

    public int barricades;

    /** Boards up a one-block opening with a Wooden Plank from the case. The infected cannot climb through what is not open. */
    public String barricade(int[] c) {
        ItemInstance plank = null;
        for (ItemInstance i : profile.loadout.all()) if ("mat.plank".equals(i.defId) && i.parent == null) { plank = i; break; }
        if (plank == null) return "Needs a Wooden Plank to board this up";
        if (!nearby(c[0] + 0.5f, c[2] + 0.5f, 1.2f).stream().allMatch(a -> a.dead || Math.abs(a.x - c[0] - 0.5f) > 0.5f + a.radius || Math.abs(a.z - c[2] - 0.5f) > 0.5f + a.radius || a.y > c[1] + 1 || a.y + a.height() < c[1])) return "Something is in the way";
        InventoryService.consume(plank, 1);
        world.set(c[0], c[1], c[2], Block.BOARDED);
        barricades++;
        emitSound(c[0] + 0.5f, c[1] + 0.5f, c[2] + 0.5f, 26, "hammer", player.id, false);
        return "Boarded up";
    }

    /** Infrastructure repair (substation): 2 electrical parts restore district power. */
    public String repairSite(int x, int y, int z) {
        District d = world.district(x, z);
        if (profile.zone.power[d.ordinal()]) return "Power is already flowing here";
        List<ItemInstance> parts = new ArrayList<>();
        for (ItemInstance i : profile.loadout.all()) if ("mat.electrical".equals(i.defId) && i.parent == null) parts.add(i);
        if (parts.size() < 2) return "Needs 2x Electrical Parts to restore power";
        InventoryService.consume(parts.get(0), 1);
        InventoryService.consume(parts.get(1), 1);
        profile.zone.power[d.ordinal()] = true;
        debrief.powerRestored = true;
        profile.contracts.onPower();
        for (MapData.Point p : world.map.lampsOnPower) if (world.district(p.x, p.z) == d && world.get(p.x, p.y, p.z) == Block.LAMP_OFF) world.set(p.x, p.y, p.z, Block.LAMP);
        emitSound(x, y, z, 60, "machines", player.id, false);
        profile.zone.news("Power restored in the " + d.display + " by an unknown survivor");
        bus.emit(new Events.InfrastructureChanged(d.name(), "power"));
        evaluateExits();
        return "Power restored - lamps, the pumping station tunnel and local trade come back online";
    }
}
