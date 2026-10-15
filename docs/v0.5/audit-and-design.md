# Version 0.5 - codebase audit and technical design

Stage 1 and Stage 2 of `instructions.md`. Written before any 0.5 code; kept current as the work proceeds.

## 1. Audit (state of 0.4 at the start)

Baseline: `gradlew selftest` = 313 gates passed, 0 failed (4 min 27 s). Source 35.8 k lines in `src/main/java/absentialis`.
`backup/v0.4-src.tar.gz` is identical to the tree this work started from.

| Area | Where | What exists | Defects / gaps against the 0.5 brief |
|---|---|---|---|
| World storage | `world/World` | 704 x 704 x 96 byte voxels, city square 96..608, flat street level G=24, pristine snapshot per raid | `outsideDistance` treats column 608 as inside the city while the city fill stops at 607 (see 2.2) |
| Generation | `world/gen/CityGenerator, Districts, Landmarks, Authored, Buildings, Interiors, Streets, WildernessGen, Validation` | Hand-placed rectangles per district, BSP interiors with random doors, street painter, sewers, metro, canal, wall | No district plan as data; landmarks are not the brief's; interiors are not graph-driven (rooms can be sealed, stairs can land in walls); damage spheres and rubble piles spill onto roads; no accessibility validation |
| Boundary towers (brief 2.1) | `WildernessGen.quarantineWall -> tower()` | Watchtowers every 56 m | Placed at `lo-3` / `hi-1`: outside the wall on the west and north sides, half inside it on the east and south; built from y=G-1 whatever the terrain; four solid `steel_beam` legs read as blue glass. Also `exitsAndInsertions` hangs an unsupported fence-and-lamp column over every exit, including the two outside the wall |
| Perimeter trench (brief 2.2) | `World.outsideDistance`, `WildernessGen.terrain/gapGround`, `CityGenerator.generate` | - | Columns x=608 and z=608 are never filled: the terrain pass and `gapGround` skip them (`outsideDistance <= 0`) and the city fill loops `< CITY_MAX`. Result: a one-block slit open to y=0 along the whole east and south boundary. Measured with `tools/TerrainProbe` |
| Navigation | `world/Nav`, `MapData.nodes` | Bounded voxel A*, street waypoint graph, door/yard nodes | Only wooden doors are passable; no door state, no cover data, no reaction to changed geometry beyond re-querying voxels |
| Human AI | `ai/Brain, Tactics, SquadMind, CloseCombat` | Sight/sound perception with memory and error, squad reports with delay, roles (aggressor, flanker, suppressor, searcher, defender), sampled cover scoring, suppression, search | Cover is sampled per decision (no metadata, no occupancy, no height class, no lean); no downed state or rescue; no medic role; no utility layer naming the decision; chokepoints not modelled |
| Damage / death | `scene/Raid.damageActor, kill`, `scene/Gore`, `anatomy/*`, `character/Ragdoll`, `scene/RagdollController` | Voxel anatomy, wounds, dismemberment for NPCs, Verlet ragdoll with joint limits, death camera | Player body is only drawn in the death view when the third-person path is active; player limbs never sever; no downed state |
| Animation | `character/Animator, Anims, Rig, Pose` | Gait blend by speed, IK arms on the weapon, foot planting | Health condition does not change gait; walk/run threshold blend only; no cover poses, no downed/revive clips |
| Doors | `Block.DOOR_*`, `Raid.toggleDoor` | Two voxel states swapped instantly | No hinge, no damage regions, no AI metadata |
| Combat feedback | `scene/RaidScreen.fire, drawViewmodel`, `scene/Hud` | Hip/ADS view model, damage direction markers | No hit confirmation marker; ADS offset is one constant for every weapon |
| HUD / UI | `scene/Hud`, `ui/Ui`, `ui/InventoryUi`, `scene/HideoutScreen` | Three bar cores, minimap, squad list | No portrait, no curved gauge, no killfeed, no dialogue bubbles; Insertion Point text overflows its card |
| Audio | `engine/audio/AudioEngine, SoundBank, SoundBank04, AssetSounds` | Software mixer on `SourceDataLine`, every sound synthesised at start-up, `.ogg/.wav` override folder | No recorded assets, no per-weapon profile, no layered shots, no reflections or shared reverb, voices are started from the game thread under a lock |
| Persistence | `persistence/Profile, SaveSystem` | JSON profiles, `SAVE_FORMAT = 4` | No permanent maximum-health field |
| Tests | `tools/SelfTests*`, `ABS_AUTOSHOT`, `ABS_PREVIEW` | 313 headless gates, scripted screenshot pass | District gates assert the 0.4 layout and are rewritten for 0.5 |

Constraints: Java 21 toolchain, LWJGL 3.3.3 (GLFW, OpenGL, STB only - STB decodes Ogg Vorbis), no physics or audio middleware,
no git repository. One world seed (1337) is shared by every profile; the layout is nevertheless derived from the seed.

## 2. Design

### 2.1 World (`world/gen`)

*Plan before voxels.* `WorldPlan.of(seed, defs)` computes the whole layout as data and never touches the voxel array:

1. terrain and boundary (wilderness height field; one definition of "inside the city" shared by every stage);
2. district regions: a pinwheel - Downtown in the centre square, four congruent outer slots; which district takes which slot
   is scored from terrain suitability (airfield wants the flattest surroundings, farms the greenest, industry the highway side);
3. arterial roads on the slot boundaries plus the ring road; gates and routes into the wilderness;
4. two landmark lots per district with clearance, each fronting a road;
5. secondary streets (one spine per outer slot, cross streets that never cut a landmark) and mid-block pedestrian alleys;
6. remaining blocks subdivided into lots;
7. a building archetype per lot from the district's weighted list, footprint inside the lot's setbacks, front facing its street.

Districts, landmarks, archetypes, palettes, street widths and faction headquarters are data: `content/districts.json`.
Enum constant names stay (`MARKET`, `OLD_QUARTER`, `CIVIC`, `REDLINE`, `INDUSTRIAL`, `WILDERNESS`) because they are save keys;
`REDLINE` is now displayed as Outskirts.

| District | Landmarks | Headquarters (faction) |
|---|---|---|
| Residential (`MARKET`) | Mansion, large suburban house | Mansion (survivors) |
| Industrial | Power plant, science lab | Power plant (raiders); the lab is a mercenary outpost |
| Governmental (`CIVIC`) | Military base, airport | Military base (military) |
| Downtown (`OLD_QUARTER`) | Police headquarters, grocery store | Police headquarters (mercenaries) |
| Outskirts (`REDLINE`) | Correctional complex, farming compound | Correctional complex (raiders) |

There are four human factions and five districts, so one faction holds two headquarters; the assignment is data.

*Voxels second.* `CityGenerator` then realises the plan in the brief's order. Every structure is built through a clip
rectangle (its lot): a write outside it is refused and counted, so nothing can spill into a road, an alley or a neighbour.

*Buildings from room graphs* (`gen/arch`): a `Blueprint` (archetype) yields per floor a `RoomGraph` - rooms with a purpose
and a size weight, and edges that must exist (entry-hall, hall-room, room-room, stair links between floors). `FloorPlanner`
places the circulation room first and the others against it in graph order, so graph neighbours are physical neighbours.
`Architect` then writes shell, slabs, partitions, one doorway per edge, stairs, windows and finishes (`MaterialRules`), and
`Furnisher` dresses rooms while keeping door approaches and the room's spine clear.

*Validation* (`gen/GenValidator`): flood fill of standable cells from the entrance must reach a floor cell of every room
and every stair landing; no door may open into a solid; structural blocks must be supported; footprints must not overlap;
roads must be clear. Failures are repaired (clear furniture, cut the missing doorway) and re-checked; a building that
still fails is rejected and its lot becomes a yard. Results are kept in `GenReport` for the gates and the overlays.

*Debug views*: `ABS_GENDUMP=<dir>` writes PNG layers (districts, reserved roads, footprints, room graph, navigation
regions, cover, invalid geometry, materials); in a raid the console commands `gen <layer>` and `nav debug` draw the same
data in the world.

### 2.2 Shared character state

`entity/Vital` (enum `ACTIVE, DOWNED, REVIVING, DEAD`) with one transition function in `scene/Casualty`. Health conditions
(`FINE, CAUTION, INJURED, CRITICAL`) and every threshold, timer and chance live in `content/combat.json`
(`gameplay/CombatConfig`). Confirmed combat facts go out on the event bus (`HitConfirmed`, `ActorDowned`, `ActorRevived`,
`ActorKilled` extended with weapon and killer names); the hit marker, killfeed, squad panel and AI all read those events.

### 2.3 Cover, doors, navigation

`world/CoverMap`: cover points derived from voxels per 16 x 16 column chunk (position, normal, height class, left/right
lean, stance, integrity, occupant), rebuilt for a chunk when a block in it changes. `scene/Doors`: door entities
(hinge angle, angular velocity, material, health, damage regions, latch/hinge state, lock) created from the generator's
door records; collision and line of sight ask the door, and a state change marks the door's navigation link.
`world/Nav` gains door-aware passability, crouch cells and a version stamp that invalidates cached paths around a change.

### 2.4 Presentation

`ui/Layout` (anchors, text measurement, wrapping, clipping, UI scale) underlies the new HUD module (portrait, curved
gauge drawn as a triangle-strip arc, ammunition), the squad column, killfeed, dialogue bubbles, the inventory status
panel and the Insertion Point fix.

### 2.5 Audio

`engine/audio/gun`: `WeaponAudioProfile` (data: `content/weapon_audio.json`), immutable `ShotEvent`, `ShotEventScheduler`
(sample-accurate, RPM-exact), `SampleLayerEngine`, `VoiceManager`, `AcousticEnvironment` (occlusion, ray-estimated early
reflections, shared feedback-delay reverb), mixed inside the existing audio thread; game code only posts events to a
lock-free queue. Recorded assets and their licences are listed in `docs/audio/`.

### 2.6 Order of work

World generation and boundary fixes -> navigation/cover/doors -> character state (conditions, downed, death) -> AI ->
combat feedback and HUD -> audio -> validation, gates, documentation.

## 3. As built (end of 0.5)

What follows records where the implementation ended up relative to the design above.

| Area | Where it lives | Verified by |
|---|---|---|
| District plan, roads, lots, landmarks | `world/gen/WorldPlan`, `GenDefs`, `content/districts.json` | `ABS_SELFTEST=world05`, `district` |
| Buildings from room graphs | `world/gen/arch/*`, `content/buildings.json` | reachability flood in `GenValidator`; gates |
| Landmark compounds (10) | `world/gen/LandmarkBuilder` | gates (two per district, one headquarters) |
| Validation, repair, rejection, report | `world/gen/GenValidator`, `GenReport` | report must be clean for two seeds |
| Boundary | `world/gen/WildernessGen`, `World.outsideDistance` | `tools/TerrainProbe` gates (trench, voids, seams, glass ring) |
| Debug layers | `world/gen/GenDebug` (PNG) | reviewed by eye |
| Cover data | `world/CoverMap` | gate; used by `ai/Tactics` and the player |
| Tactical AI | `ai/Tactics`, `ai/Brain`, `ai/SquadMind` | existing AI gates; `ai debug` overlay |
| Casualties | `scene/Casualty`, `entity/Vital`, `content/combat.json` | `ABS_SELFTEST=casualty` |
| Death pipeline | `scene/Raid.die/kill`, `scene/Gore`, `character/Ragdoll` | gate (body persists), autoshot |
| Doors | `scene/Doors`, `World.DoorOracle`, `ai/Nav` | `ABS_SELFTEST=door`, autoshot |
| Feedback and status interface | `scene/CombatFeed`, `scene/StatusHud`, `ui/Layout`, `ui/Portrait` | `ABS_SELFTEST=feed`, `hud05`, autoshot |
| Audio | `engine/audio/gun/*`, `scene/WorldAcoustics`, `tools/AudioPrep` | `ABS_SELFTEST=gunaudio` (40 checks) |

Departures from the design: the in-game overlay for generation layers was not built (PNG output only); balconies were
dropped from the building archetypes; the squad blackboard reuses `SquadMind` rather than a new class; the player's hit
capsule does not follow a lean.
