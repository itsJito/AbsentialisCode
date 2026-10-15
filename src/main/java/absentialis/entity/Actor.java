package absentialis.entity;

import absentialis.gameplay.inventory.Container;
import absentialis.gameplay.items.ItemInstance;

/**
 * A physical agent in a raid: the player, a faction member or an infected. Holds body state,
 * hit regions, perception memory and the voxel appearance description. Every mind is a NeuralAI mind
 * (package neuralai); this class is plain state shared by every system - what a mind decided is published here.
 */
public final class Actor {
    private static int nextId = 1;

    /** The id the next actor will be given, and a way to put the counter back (a replay must hand out the same ids as the run it replays). */
    public static int nextId() { return nextId; }
    public static void nextId(int id) { nextId = Math.max(1, id); }

    public final int id = nextId++;
    public String faction;        // player, military, raiders (bandits), survivors (loners), mercenaries, infected
    public String archetype;      // rifleman, marksman, shotgunner, walker, shambler, sprinter, screecher, bomber, tank
    public boolean isPlayer;

    // ----- body
    public float x, y, z, vx, vy, vz, yaw, pitch, radius = 0.3f;
    public float stand = 1.8f;
    /** 0.9: the height of where this body is going when that is on another floor (NaN: wherever its column is first reached). */
    public float goalY = Float.NaN, goalYAt = -1;
    /** 0.9, pulling up onto a ledge: seconds left of it (0 = not doing it), how long it takes, and where from and to. */
    public float mantleT, mantleDur, mFromX, mFromY, mFromZ, mToX, mToY, mToZ;
    public boolean mantling() { return mantleT > 0 || hanging; }
    /**
     * 1.1, the hang: hands on a ledge, the body under them, the feet off the ground (entity.Physics.hang). Where the body
     * hangs, how long it has been there, and the swing of the legs - a pendulum set going by the speed the ledge was taken at.
     */
    public boolean hanging;
    public float hangX, hangY, hangZ, hangT, hangSwing, hangVel;
    /** 0.9, the Butcher's saw: how far its chain has run (links), and how hard the engine is working (0 stopped, about 0.35 idling, 1 revving). */
    public float sawPhase, sawRev;
    public boolean onGround, crouch, climbing, wantClimb, sprinting;
    /** 0.7: lying prone. Half a metre high, slow, quiet and hard to see; crawls in any direction. */
    public boolean prone;
    /** 0.7 cover: which shoulder is to the wall (+1 right, -1 left, 0 = not side-on), and whether the body is half-risen to look over low cover. */
    public float coverSide;
    public boolean coverPeek;
    public int climbDir = 1;
    public float stepped, fallSpeed;
    public float hp = 100, maxHp = 100, armorDur, armorMax;
    /** Body Armor protection that absorbs damage before health does. */
    public float shield, shieldMax;
    /** Blinded by a flash grenade until this raid time. */
    public float blindUntil;
    public int armorClass;
    public boolean helmet;
    /**
     * 0.8. How good the helmet is (0 = none; until 0.8 a helmet was judged by the class of the vest under it), how good
     * the pack is against a hit in the back (0 = no protection), and whether the armour reaches down the arms and legs.
     */
    public int helmetClass, packClass;
    public boolean armorArms, armorLegs;
    public boolean dead;
    public float deadTime, hurtFlash, walkPhase;
    public int lastAttacker;
    public float speedMul = 1;

    // ----- equipment / corpse
    /**
     * 0.7: what this character is wearing, by item id ("" = nothing): helmet or hat, face cover, body armour. The
     * model is dressed from these. When the character dies they become real items on the body ({@link #wornItems}),
     * and a body that has been stripped of one is drawn without it.
     */
    public String wornHead = "", wornFace = "", wornArmor = "";
    /** 0.8: the pack on the back, by item id ("" = whatever the faction's look gives them). */
    public String wornPack = "";
    /** The worn items as instances, in the order head, face, armour (null where there was nothing): made at death, placed in the corpse. */
    public ItemInstance[] wornItems;
    /** 0.9: what a body has on it and in its hands, each thing in the slot it was worn or carried in (searching a body shows these as equipment; its container holds only what was in its pockets). */
    public absentialis.gameplay.inventory.Loadout corpseGear;
    public ItemInstance wornPackItem;
    public ItemInstance weapon;
    public Container corpse;      // lootable inventory (created on death)
    public String look = "";      // appearance key for the voxel model cache

    // ----- published AI state (see neuralai.integration.NeuralHost)
    public String state = "idle";
    public float stateTime, thinkTimer, fireCooldown, burstLeft, alertness, awareness;
    public float memX, memY, memZ, memTime = -999, memConfidence; // last known target position
    public int targetId;
    public float goalX, goalZ, homeX, homeZ;
    public boolean hasGoal;
    public float stuckTime, specialTimer;
    /** 1.0, steering: the side-step taken to get off an obstacle (radians off the wanted heading) and until when it is kept; a route that stopped short of where it was going, and until when the street graph is used instead. */
    public float detourOff, detourUntil = -99, partialUntil = -99;
    /**
     * 1.1, the spin governor (neuralai.integration.Steering): how far this body has turned (radians, summed) since it was
     * last at {@link #spinX}, {@link #spinZ}, and when that was; until when it stands still to break a turning loop; and
     * how many loops have been broken on it (gates, the AI overlay).
     */
    public float spinAcc, spinAt = -99, spinX, spinZ, holdUntil = -99;
    /** 1.1, the horde (scene.Horde): until when this person is running from the dead because there are too many of them, where to, and when the line is next chosen. */
    public float overrunUntil = -99, fleeX, fleeZ, fleeAt = -99;
    /** 1.1: seconds this body has been falling; and the pose physics last had it in (the render skeleton's twelve matrices) with when that was - what is drawn is blended from it when animation takes the body back. */
    public float airTime;
    public float[] ragSnap;
    public float ragSnapAt = -99;
    /** 1.3: the skeleton as it was last drawn (twelve matrices) and when: what a round is traced against, so what is hit is what was seen. */
    public float[] drawnBones;
    /**
     * 1.3, knocked out: until when this body lies senseless (a full combination of punches), and that it has just come
     * round (its mind starts again knowing nothing of what happened, but uneasy). The punches of the current combination
     * that have landed on it, from whom and when last, and whether it held anything against the survivor before them.
     */
    public float unconsciousUntil = -99, fistAt = -99;
    /** 1.3: when this body was thrown as a ragdoll (its landing, the first touch after, is recorded in Raid.landedAt). */
    public float thrownRagAt = -99;
    public boolean wokeUneasy, fistWasHostile;
    public int fistChain, fistFrom;
    public float drawnAt = -99;
    public int spins;
    /** 1.0: seconds one of the survivor's own has been left far behind without getting any nearer. */
    public float lostT;
    public boolean hostileToPlayer, sawPlayerAggress;
    public boolean aggressor;     // hostile to the player before the player harmed it (self-defence context)
    public int cell = -1;         // strategic cell owning this actor (tier transitions)
    public int tier = 0;          // 0 = active, 1 = regional
    public float regionalAccum;
    public int squad;

    // ----- animation / ragdoll (character package; evaluated by the renderer and the camera)
    public absentialis.character.Animator anim;
    public absentialis.character.Ragdoll ragdoll;
    /** Renderer-owned cached look (appearance + gear model key). */
    public Object visual;
    public float hitDirX, hitDirZ;            // direction the last damaging hit travelled
    public String hitRegion = "torso";
    public float hitForce;

    // ----- doctrine state (see neuralai.integration.Routines): shared by every human archetype
    public float morale = 1, risk = 0.5f;     // morale 0..1, risk tolerance 0..1
    public float memErr;                      // uncertainty radius (m) of the remembered position
    public String role = "";                  // suppress, flank, hold, leader ...
    public float coverX, coverZ, coverUntil;  // chosen cover position
    public boolean hasCover;
    public float orderX, orderZ, orderUntil;  // squad / dispatch order (respond to noise, reinforce)
    public boolean hasOrder;
    public float warnTimer, extortTimer, lootTimer, talkTimer, suppressUntil, lastSeenTime = -999;
    public boolean warned, paidOff, needsHelp, helped, looted, calledBackup, restricted;
    /** 0.9: put in the world by a district hotspot or random event (scene.DistrictEvents). */
    public boolean eventActor;
    public int routeIndex = -1;
    public long squadUid;                     // persistent A-Life squad id (0 = ambient)
    public float lastSay = -99;
    public boolean burst;                     // fat infected: already ruptured
    public float staggerUntil;                // slowed by a hit / acid
    /** Items taken off corpses during the raid (dropped with the rest when this agent dies). */
    public final java.util.List<ItemInstance> carried = new java.util.ArrayList<>();

    // ----- navigation (see neuralai.integration.Steering / world.Nav)
    public int[] path;                        // packed cells {x, y, z, ...} toward pathTX/pathTZ
    /** World navigation version the path was planned against, and when this agent may next put a boot to a door. */
    public int pathNav;
    /** 1.2: when a route was last asked of the native core and is still awaited (-99 = none is), and the number of that asking (a late answer to an older one is ignored). */
    public float pathPending = -99;
    public int pathToken;
    public float kickAt = -99;
    public int pathI;
    public float pathTX, pathTZ, pathAt = -99, routeAt = -99, viaX, viaZ;
    public boolean hasVia;

    // ----- anatomical damage (see scene.Gore and package anatomy)
    /** Layered voxel anatomy (created on the first hit) and what the body can still do. */
    public absentialis.anatomy.VoxelBody body;
    public absentialis.anatomy.AnatomicalState anat;
    /** Rig groups that hang limp (broken, not gone); impairments as AIInjuryAdapter bits. */
    public int limpMask, impaired;
    /** 0.6: what this character has on them to trade - made the first time someone asks, and from then on simply what they carry. */
    public absentialis.gameplay.inventory.Container wares;
    /** 0.6, the infected feed: the corpse this one is eating, and (on a corpse) how much of it has been eaten, 0 .. 1. */
    public Actor feedOn;
    public float eaten;
    /** 0.8: a body this one could not get to: it does not try for another until this raid time. */
    public float mealBanUntil = -99;
    /** A living character knocked down under physics gets up after this raid time. */
    public float downUntil;
    /** The injury event the AI acts on: how bad (0..1, fading), which region, when, and who did it. */
    public float injurySeverity, injuryTime = -99, lastCritical = -99;
    public int injuryGroup = -1, injuryFrom;
    /** Rig groups that have been severed and are no longer drawn on the body. */
    public int severed;
    /** Open wounds: blood lost per second, the drip counter of the trail, and what a corpse has yet to drain. */
    public float bleed, trail, poolLeft, slipUntil, aimPenalty = 1;
    public boolean crawling;
    /** Blood pools this agent has already examined (ids), so a trail is followed forward. */
    public java.util.Set<Integer> evidenceSeen;

    // ----- A-Life identity and routine (see sim.Agents)
    public long agentId;                      // persistent agent this body belongs to (0 = none)
    public String name = "";
    public boolean nightShift;
    public String routine = "";               // what the schedule says right now: guard, patrol, scavenge, rest, sleep ...
    public boolean light;                     // carrying a lit torch (night duty)
    public float flashUntil, wakeUntil = -99;  // muzzle flash gives the shooter away; woken sleepers stay up for a while
    public String duty = "patrol";            // on-shift role

    // ----- squad (the player's AI element, see gameplay.squad.Roster)
    public long mateId;
    public String team = "";
    /** 0.7: what kind of squadmate this is (rifleman, defender, sniper, breacher, medic, support): see gameplay.squad.Roster.TYPES. */
    public String mateType = "";
    public absentialis.character.Appearance appearance;
    /** Standing order (follow, hold, move, sweep) and an order queued to run on Initiate. */
    public String sqOrder = "follow", qOrder;
    public float sqX, sqZ, qX, qZ;

    public float evidenceTimer;

    // ----- tactical knowledge and state, as published by NeuralAI (see neuralai.integration.NeuralHost, Blows)
    /** 0 unaware, 1 suspicious, 2 threat aware, 3 engaged, 4 searching. */
    public int awareLevel;
    /** The remembered position was seen by this agent itself (false: heard, inferred or told). */
    public boolean memConfirmed;
    /** How the target was moving when last seen, and where the agent now assumes it is. */
    public float memVX, memVZ, suspX, suspZ, suspTime = -99;
    /** Rounds landing close: 0 .. 1, and pinned (head down) until this raid time. */
    public float suppression, pinnedUntil;
    /** Current tactical role and the position chosen for it: when it was taken, which way it faces, when to reconsider. */
    public String tacRole = "";
    public float coverSince, coverDirX, coverDirZ = 1, relocateAt, positionAt, flankedAt = -99;
    /** Searching: the place this agent has claimed, when it set off, how many it has cleared. */
    public boolean hasSearch;
    public float searchX, searchZ, searchAt, scanT, lastTold = -99, selfAidAt, selfAidUntil = -99;
    public int searched;
    /** Close combat: an attack being wound up (seconds left), which one, and when it last grabbed someone. */
    public float meleeWind, lastGrab = -99;
    public String meleeMove = "";
    /** Velocity last frame (movement has weight: it does not change direction in one tick). */
    public float pvx, pvz;

    // ----- infection (see scene.InfectionSystem)
    /** 0 = clean; a bite starts it and it climbs to 100, where a human turns. (The player's own level lives on Survivor and is mirrored here.) */
    public float infection;
    /** Raid time at which this agent will use the antiviral it carries (-1 = it has none). */
    public float infectTreatAt = -1;
    /** A human who turned: an infected in the body, clothes and kit they had. */
    public boolean turned;
    public String turnedFrom = "";

    // ----- life state, incapacitation and rescue (see scene.Casualty)
    public Vital vital = Vital.ACTIVE;
    /** Seconds of blood left while DOWNED, when it went down and who put it there. */
    public float bleedLeft, downedAt = -99, helpCallAt = -99;
    public int downedBy;
    /** Rescue in progress: seconds worked, when it began, who is working on this character / whom this one is working on. */
    public float reviveT, reviveFrom = -99;
    public int reviverId, revivingId;
    /** The primary carried while down on a sidearm, and the sidearm itself. */
    public ItemInstance stowed, sidearm;
    /** Raid time of the last damaging hit and how it was delivered (bullet, melee, blast ...). */
    public float lastHitTime = -99;
    public String lastHitKind = "";
    /** 0.6: the weapon behind the last damaging round, how far it had flown, and when a shotgun pellet last struck the head. */
    public String lastHitWeapon = "";
    public float lastHitRange = 999, headPelletAt = -99;
    /** Raid time of death (-99 while alive). */
    public float diedAt = -99;

    // ----- cover in use (see world.CoverMap, neuralai.integration.SydneyAdapter, character.Animator)
    public absentialis.world.CoverMap.Point cover;
    /** Lean out of cover: -1 left .. +1 right as seen facing the obstacle, eased toward leanTarget. */
    public float lean, leanTarget, coverAmt;

    /** The name of the action the mind has committed to (NeuralAI's selector), for the overlay. */
    public String intent = "";
    public float intentScore, intentUntil = -99;
    public int intentChanges;

    /** Raid time the player last had eyes on this character (minimap contacts fade from it). */
    public float seenByPlayer = -99;

    // ----- what this character is saying (speech bubble; set by Raid.say)
    public String sayText = "";
    public float sayAt = -99, sayUntil = -99;

    /** The body this character has, when something upstream decided it (a named agent, a squadmate): see character.Appearance. */
    public boolean female, sexKnown;
    // ----- third person (0.6): the body's own facing, apart from where its owner is looking
    /**
     * In third person the player looks with the camera and the body turns toward where it is going; {@link #yaw}
     * stays the look direction every system aims by, and this is what the model, the feet and the animation face.
     * Unset (first person, and every agent) the body simply faces {@link #yaw}.
     */
    public float faceYaw;
    public boolean faceSet;
    public float facing() { return faceSet ? faceYaw : yaw; }
    /** Sliding on the ground (0.6): seconds left, and the direction it was started in. */
    public float slideT, slideX, slideZ;

    // ----- the Group Rivalry System (see scene.RaidRivalry, package rivalry)
    /** The persistent group this character belongs to ("" = none), and - for a named leader, lieutenant or notable - who they are. */
    public String groupId = "", leaderId = "";
    /** Seed of the look when something other than the agent id decides it (a group's leader looks the same every time). */
    public long lookSeed;
    /** Share of blast damage taken (a group that has learned to spread out and pad up takes less), the Rank this character holds, and whether a director put them here. */
    public float blastResist = 1;
    public int rankLevel;
    public boolean directed;
    /**
     * 1.0, the Crawler: on a ceiling (the body really is up there - it hangs under the ceiling and moves along it), how far
     * it has turned over onto it (0..1) and how far up a wall it is tipped while it scales one (0..1); and its tongue - where
     * the barb is, and until when it is out.
     */
    public boolean cling;
    /** 1.0: an advisor: a fixed person in the world who is spoken to, does not fight and cannot be hurt. */
    public boolean advisor;
    public float ceilT, wallT, tongueX, tongueY, tongueZ, tongueUntil = -99, pounceUntil = -99;
    /** 1.0: something being used is in the hand (an item id - a first-aid spray, a bandage): it is drawn there, and the weapon is slung meanwhile. */
    public String handItem;
    /** 1.0: found in a lair or come in with a night pack - one of the dead who is not one of the Zone's counted agents. */
    public boolean site;
    /** True when the first round of the current exchange found this character unaware (what "stealth" means to those who find the body). */
    public boolean hitUnaware;
    /** Riding in a vehicle (scene.VehicleSystem carries the body; the mind and the physics leave it alone). 0.8: the driver too. */
    public boolean seated;
    /** 0.8: in the driver's seat with both hands on the wheel, and how far the wheel is turned (-1 full left .. +1 full right). */
    public boolean atWheel;
    public float steer;
    /** Raid time before which this body will not jump again (agents jump ledges like anyone else: once, deliberately). */
    public float jumpAt = -99;

    /** 0.7: the target the host's own doctrine last reacted to on first sight (a hold-up, a loner backing off), and when. */
    public int contactHandled;
    public float contactAt = -99;
    /** 0.7, the infected: where the head is turned relative to the body (radians, eased), what it is turning to, and a noise being attended to. */
    public float headLook, headLookTarget, noticeUntil = -99;
    /** 0.7, getting through a hole: seconds left of the climb, where it started, the sill, and where it comes out. */
    public float breachT, breachDur;
    public final float[] breach = new float[9];
    /** 0.7: an opening it has picked out and is walking to (until this time), and when it next looks for one. */
    public float holeUntil = -99, holeScanAt;

    /** 0.7: covered in infected bile until this time (every infected nearby comes for the wearer); mirrored into biled for whoever draws the body. */
    public float bileUntil = -99;
    public boolean biled;
    /** 0.7, the Tank's grab: who it is holding, for how much longer, and when it may grab again. */
    public int grabId;
    public float grabT, grabAt;
    /** 0.7: until this time the body is being carried or is in the air from a throw - its own legs have no say. */
    public float flingUntil = -99;
    /** 0.7: when a Tank let go of this body (it flies as thrown until it lands), or -99. */
    public float thrownAt = -99;
    /** 0.7: when a car may next shove this body along (a body under a car is pushed in steps, not every frame). */
    public float runOverAt;
    /** 0.7: when this one may next cry out at being hit. */
    public float painAt;

    public boolean mate() { return mateId != 0; }
    public boolean active() { return !dead && vital == Vital.ACTIVE; }
    public boolean downed() { return !dead && (vital == Vital.DOWNED || vital == Vital.REVIVING); }

    /** Fractions of standing height: prone, crawling wounded, crouched, sliding. */
    public static final float PRONE_H = 0.30f, CRAWL_H = 0.45f, CROUCH_H = 0.68f;
    public float height() { return prone ? stand * PRONE_H : slideT > 0 ? stand * 0.5f : crouch || crawling ? stand * (crawling ? CRAWL_H : CROUCH_H) : stand; }
    public float eyeY() { return y + height() - 0.17f; }
    public boolean infected() { return "infected".equals(faction); }
    public boolean alive() { return !dead; }

    public float dist(Actor o) { float dx = x - o.x, dy = y - o.y, dz = z - o.z; return (float) Math.sqrt(dx * dx + dy * dy + dz * dz); }
    public float distXZ(float ox, float oz) { float dx = x - ox, dz = z - oz; return (float) Math.sqrt(dx * dx + dz * dz); }

    /** Hit region for a world-space hit height. */
    public String regionAt(float hy) {
        float rel = (hy - y) / height();
        if (rel > 0.83f) return "head";
        if (rel > 0.45f) return "torso";
        return rel > 0.38f ? "arms" : "legs";
    }

    /** Ray-vs-upright-capsule (approximated as AABB). Returns entry t along the segment or -1. */
    public float intersect(float ox, float oy, float oz, float dx, float dy, float dz, float maxT) {
        if (dead) return -1;
        return intersectBody(ox, oy, oz, dx, dy, dz, maxT);
    }

    /** The same test without regard to whether the character is alive (a charge of shot still arriving as the body starts to fall). */
    public float intersectBody(float ox, float oy, float oz, float dx, float dy, float dz, float maxT) {
        float r = radius + 0.05f, h = height();
        float[] lo = {x - r, y, z - r}, hi = {x + r, y + h, z + r}, o = {ox, oy, oz}, d = {dx, dy, dz};
        float t0 = 0, t1 = maxT;
        for (int a = 0; a < 3; a++) {
            if (Math.abs(d[a]) < 1e-7f) { if (o[a] < lo[a] || o[a] > hi[a]) return -1; continue; }
            float ta = (lo[a] - o[a]) / d[a], tb = (hi[a] - o[a]) / d[a];
            if (ta > tb) { float t = ta; ta = tb; tb = t; }
            t0 = Math.max(t0, ta); t1 = Math.min(t1, tb);
            if (t0 > t1) return -1;
        }
        return t0;
    }
}
