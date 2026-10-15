# Changelog

Newest first. Each version lists what changed for the player; file names are given where they help find the work.
What was verified for each version, and how, is in `docs/V1.3_IMPLEMENTATION.md`, `docs/V1.2_IMPLEMENTATION.md`, `docs/V1.1_IMPLEMENTATION.md`, `docs/V1.0_IMPLEMENTATION.md`, `docs/V0.9_IMPLEMENTATION.md`, `docs/V0.8_IMPLEMENTATION.md`,
`docs/V0.7_IMPLEMENTATION.md` and `docs/V0.6_IMPLEMENTATION.md`.

## Version 1.3

What was verified for 1.3, how, and what was not, is in `docs/V1.3_IMPLEMENTATION.md` - read its "Not done" table
before trusting any line below. Nobody has played this version. Full suite on the final build: **706 passed, 4 failed**; the 4
failures are the four 1.1 gates that fail on 1.1's own source as well. New gates: `ABS_SELFTEST=v13` (`tools/SelfTests13.java`).
Pictures of what was looked at by eye are in `docs/v1.3/`.

### Astratto Engine
- **Stage 2: rendering's chunk mesher is C++** (`astratto/native/src/render/chunk_mesher.cpp`, interface 1.1:
  `astratto_mesher_create` / `astratto_mesh_column`). Every column of the city comes out **byte for byte** what the Java
  mesher wrote - with doors drawn and not, with plants left in and taken out (gate `render13`, 2710 columns) - and in
  play the frames match the Java path's pixel for pixel, or within one step in 255 where the light probes settle a
  frame sooner. About 1.8 times faster a column. The Java mesher stays as the reference and the fallback
  (`ABS_NATIVE=0`, or `ABS_NATIVE_MESH=0` for the mesher alone).
- The native core's own self-test meshes a column without any engine or game (30 checks).

### Fixed
- **Rounds through far bodies**: a round is traced against the body as it was drawn that frame (ground under the feet,
  a limp, a Crawler on a wall), not a pose worked out afresh; the graze allowance widens a little with range.
- **Glass doors**: their panes are clear (they read as white boards), and see-through things are drawn far to near, so
  a door or a shard seen through a glass door is no longer laid over it the wrong way round.
- **Pistol reloads** are a handgun's: the magazine in the grip, the slide racked over the top, the off hand on the gun
  from the first frame (they were a rifle's, with the hand going to a magazine well and a charging handle a pistol has not).
- **People going round in circles on stairs and indoors**: a body walked into the fill under the next tread and stopped
  half way up every flight; the landing was "reached" a step short and turned for there, off the side of the stairs; a
  flight was entered from the side; people hopped up stairs; side-steps went off the edge. All fixed (`Physics`,
  `Steering`). From a door to a room upstairs, 22 of 24 walks arrive where 15 did, with half the turning loops.
- **People shoving a Crawler to no effect**: something low on the ground is stamped on, which hurts and pins it.
- **Thrown by a Tank**: the body is a Sydney ragdoll from the moment it leaves the hand, flies, tumbles and gets up.
- **The railing at 134, 30, 401**: the balustrade carries on round the stairwell instead of ending in one loose post.
- **Chandeliers** (150, 30, 360 and the station's hall) hang by a modelled chain, not a column of fence.
- **The "chair" at 259, 23, 162** was a single pew (a pew alone reads as a broken chair): lone pews are chairs now.
- **Toilets**: every block toilet in the city (336 fixtures, including 265, 23, 162; 317, 23, 247; 268, 29, 172) is the
  porcelain model of the mansion's bathroom, its back to the wall.
- **Dog tails** are carried up and back, not forward over the back.
- **The Crawler** goes on its hands and knees - hips up at a thigh's length, knees on the ground - instead of a trunk
  sitting upright on knees folded under its chest.

### Changed
- **Nobody runs backwards**: moving faster than a walk, the body faces the way it goes and the aim is a twist of the
  torso; backing off under a gun is done at a walk.
- **Dogs**: no strays - every dog has an owner - and one armed person in fourteen has one (it was one in eight).
- **Blocking locks the body to the cursor** (third person): with the guard up it turns with the mouse; down, it
  faces the way it moves again.
- **Punches break glass**: a window, a glass door, a display pane (and the knuckles may be cut).
- **A full combination of punches knocks a person out** - it does not kill them, and it draws mist, not a pool. They lie
  out for a minute and a half and come round knowing nothing of it, but on edge.
- **Shift-click in the stash** puts weapons and equipment on (into their slot, or in place of what is there).
- **Squads hold the way out**: while three or more search a building for someone, one crouches at its door and keeps it.
- **Chicken, crow and spider sounds are real recordings**: hens, wild crows (a hooded crow among them) and Madagascar
  hissing cockroaches for the spider, CC0 from Freesound, cut into single takes and credited in `content/soundpack.json`
  (the human chicken imitations, the synthetic squawk, the film-monster and cat hisses are gone).

### Added
- **Blood mist** by body part and severity: a wide pink cloud from a head, less from the trunk, a puff from a limb; an
  exit wound throws far more, and forward; a blunt blow bursts only what it breaks (`ParticleSystem.bloodMist`).
- **Containers and boarded windows as voxel objects**: a medical cabinet, a tool chest, ammunition cans, a slatted crate,
  a locker, a filing cabinet, a safe and a bin, each modelled and stood with its back to the wall; planks nailed across
  a window with daylight between them (`scene/BlockObjects`). Still the same blocks underneath - as solid, as searchable.
- **The discard case**: a small case that comes up while something is being moved, to drop things into one after
  another and be rid of them together (on an operation they are put down; in the hideout thrown away, asked twice).
- **Climbing through a window or a hole** has a body to it: hands on the sill and the feet scrabbling, flat on the belly
  through the opening, head first down the far side; at a hole at the foot of a wall, down onto the belly first.
- **The dog's legs are jointed** (elbow and hock): a walk, a trot and a gallop that fold and reach instead of stilts.
- Scenarios for looking at things: `spots13` (any place by its coordinates, or every glass door with a door behind it),
  `anims13` (`ABS_PART=animals|infected|reload|objects|discard`: footage at a fixed step, a frame every fifteenth).

## Version 1.2

What was verified for 1.2, how, and what was not, is in `docs/V1.2_IMPLEMENTATION.md` - read its "Not done" table
before trusting any line below. Nobody has played this version. Full suite on the final build: **696 passed, 4 failed**;
the 4 are 1.1's (three re-run against the 1.1 source and failing there the same way). Much of the brief was **not**
reached: the animation overhaul, the AI tactics, the inventory discard case, the container and texture redesigns, the
bloody mist and the new animal recordings are not in it.

### Astratto Engine
- **A native core in C++** (`astratto/native`): a versioned C interface (`astratto.h`), batched commands and events
  once a tick, read-only snapshots, a worker pool. First to cross: the voxel world, **NeuralAI's route search** (every
  body's routes now come from it, searched in parallel) and line of sight - each held to the Java it replaces, answer
  for answer. Builds on its own and tests itself without the game (`./gradlew.bat buildNative`).
- **Java bindings** (`astratto.bridge`), which name nothing of Absentialis. The game falls back to Java without the library.
- The division of engine and game, the bridge's rules, what has crossed and the order the rest should go in:
  `astratto/ARCHITECTURE.md`. A gate keeps the engine's dependencies on the game from growing.
- **Sydney soft bodies** (`sydney/SoftBody`): ragdolls of any shape, not only people's.
- Vector drawing for the interface (`engine/render/Vector2D`). **The mark is a cut capital A**, drawn as vectors.

### Added
- **Blocking**: aim with empty hands or a knife raises a guard, in first and third person; it takes 3% (fists) or 5%
  (knife) off what lands.
- **Animals ragdoll on death** through Sydney: spiders' legs draw in, dogs kick, hens flap; crows fall out of the sky.
- **Animals are hit by cars**: killed and thrown, or knocked aside; a spider or a dog shoves the car.
- The infected dog **coils before it leaps** (hackles up, ears flat), leaps stretched out, and shakes what it holds;
  the husky **lunges and snaps** when it bites without leaping.
- The mutated spider has a piece of jewellery in it, like the other special infected.
- Developer Mode: **every faction trade** (rifleman, marksman, shotgunner) and **the four advisors** in the spawn list.

### Fixed
- **Hit markers on animals**, and **hit boxes** that are the animal's shape and turn with it (a head counts for more,
  a spider's legs for less). It was one ball at the middle of the animal.
- **Crows fly**: up, over what is in the way, gliding and banking, and down onto a perch - no more running at walls and
  climbing them.
- Dogs no longer wag their tails dead. Spiders leave the infected, Tyrants included, alone.
- **Developer Mode picks things up with a click** - bodies, animals, vehicles, items and props - and puts things exactly
  where the cross (or, with the workbench open, the pointer) is; the spot is ringed on the ground.
- **The police station's entrance**: the forecourt wall ran across the front doors. The city has no blocked doorways now.
- **Every bed was backwards** (head out in the room). Pictures, boards and lamps hung on doors, across doorways or on
  nothing are taken down wherever they occur (65 of them), as are ceiling fittings with no ceiling (3).
- The valve at 556, 24, 286 was a wall valve hanging in the air: it stands on its own riser now.
- **Stacked glass**: shards no longer hang in the air when the pane under them is shot out.
- **Doors meet the whole of a body**, limb by limb: a leg is swept round with the leaf, a body wedged against a wall
  stops the door, and animals push doors and are pushed by them.
- Special infected always carry jewellery, even when full.

### Changed
- **People cannot make you out, or fire on you, from more than 12 blocks.** The dead and animals are not limited.
- Spiders fight in measured strikes - a visible rear-up, a recovery, a pause - instead of a stream of bites; the mutated
  spider does one thing at a time and hits a little less hard.
- Animal movement: dogs walk, trot and gallop and lean into turns; spiders rock between their two sets of legs and
  test the ground; crows hop and look about in jerks; hens bob their heads and scratch.
- **The leaderboard**: the top 20 and always you (with your real place under the twentieth if you are lower), medals for
  the first three, the mark of each Rank and the faction beside each name.
- **Profile cards**: the mark of your Rank beside its name; the emblems and patterns are drawn as vector shapes.

## Version 1.1

What was verified for 1.1, how, and what was not, is in `docs/V1.1_IMPLEMENTATION.md`. Read its "Not done, partly
done, and not verified" section before trusting any line below. Nobody has played this version. The 43 gates written
for it pass (`ABS_SELFTEST=v11`); **the full suite was last run before the later changes (656 passed) and has not
been run on the final build.** A large part of the brief was **not** reached: the C++ engine split was not started,
the animation, vehicle-animation and AI overhauls were not done, no room set reached its target score (mansion and
plant 13-16 of 20, station 12-17), and about a dozen smaller items are untouched - all listed in that document.

### Added
- **Animals** (`scene/Animals`, minds in `neuralai/animal/AnimalBrain`): a white husky that heels, warns and
  defends its owner (one armed person in eight has one; yours is sold at Rank 10 as a whistle); crows that fly at
  three blocks and drop loot three times in four; bats; chickens with brown, white and golden eggs (eaten or
  sold, never bought); a giant spider. Built from articulated voxel parts; 22 CC0 recordings.
- **Infected dogs** in packs of two to six - faster than a sprinter, they leap and go for the arm - and the
  **mutated spider**: thrust, tackle, venom spit, across ceilings, sometimes carrying young.
- **Punching**: a three-hit combo with an empty weapon slot, a rest after it, stamina, bodies that answer.
- **Hordes** (`scene/Horde`): the dead gather at hotspots, are drawn by gunfire, and people outnumbered six to
  one run.
- **Central Police Headquarters, built by hand** (`world/gen/LandmarksPolice`): a three-storey main hall with a
  gallery and skylight, west and east wings, a first floor and a basement - 50 rooms. No ceiling under four
  blocks, no corridor under two. A Desert Eagle and a full box of magnum rounds are always in the S.T.A.R.S. office.
- **Weapon upgrades**: every weapon level is WXP earned with that weapon, opens one more attachment slot and gives
  one upgrade - Firepower, Rate of Fire, Reload Speed or Capacity (a knife: Damage or Durability).
- **Attachments from the inventory**: inspecting a weapon shows its level, upgrades and slots, and fits any
  attachment you are carrying.
- **Ledge hang**: a high ledge is taken hold of first; jump pulls up, interact lets go, the legs swing.
- **Bail out** of a car at any speed, as a body in the road. Getting in can be called off.
- Loot beams on bodies by rarity; jewellery on special infected; map items that are always in their place.
- Developer Mode: [G] picks up and moves bodies, animals, vehicles and items; animals in the spawn list.

### Fixed
- Stairs no longer put you prone. Glass frames no longer flicker or eat the wall at distance, and take fog.
- NPCs spinning on the spot, jumping onto every one-high block, and walking into walls at the end of a route.
- Every body ragdolls through Sydney; infected and crawlers get up their own way; the crawler no longer sinks.
- People fall into "down" instead of snapping to it, and put a hand to a wound.
- Guns stay in the hand. The female build is its own model (three-wide arms).
- NPCs no longer stop a firefight for a bloodstain; they blame whoever is near it.
- Looters go to where your body came to rest, not where you were standing.
- The passenger's door opens: it is a real door. Nobody is dragged out of a car through its roof.
- A climb taken at a walk no longer carries you into the block.
- The Spewer's pool no longer lies, or burns, through a wall. Spinels sit in a ceiling and do not turn.
- "i" can be typed in the devmode search; the devmode rain toggle works; menu and deployment music play.
- Grass no longer grows when walked on; sprint and stamina no longer shake the camera; the devmap road.

### Removed
- All ammunition from the shop (gunpowder is sold instead). Flowers in places nothing would grow. Cube smoke.

### Changed
- **HUD**: ammunition and weapon lower right, vitals lower left with a heart trace, fading when idle ([H] shows
  everything); notices queue instead of flickering; XP and promotion top centre. Scale and safe area in Settings.
- **Halloran Mansion**: a second furnishing of every room, curtains, a front with pilasters and a cornice; a pump
  shotgun and twelve shells in the master bedroom. **Power Plant**: pipework, ducts, gauges and lamps in its halls.
- A weapon nobody has worked on does about half its damage, fires and reloads slower and holds less; fully
  upgraded it is the 1.0 weapon and a hair over. Other people's weapons are unchanged.
- Every open attachment slot may be used (it was five at once); slots open by weapon level, not Rank.
- Human marksmanship by difficulty: they miss 50%, 25% or 10% of their shots.
- The Tyrant's punch sends a body about six feet back, level. A slide is on offer after half a second of sprint.
- Stance is Left Ctrl. The red laser stops on bodies and comes fitted to the starter pistol.
- People cast shadows in headlights and torches. The red warning light is an object, not a block.
- Falling for three seconds, or landing hard, is a ragdoll. Smoke is the particle system's.

## Version 1.0

What was verified for 1.0, how, and what was not, is in `docs/V1.0_IMPLEMENTATION.md`. Read its "Not done, partly
done, and not verified" section before trusting any line below. Nobody has played this version: it was checked by
628 headless gates (15 of them new: `ABS_SELFTEST=v10`) and by stills from scripted runs (`docs/v1.0/`). Three things
the brief asked for were **not** achieved: the mansion and the plant are not at 18/20, the game is slower than 0.9,
and Director Mode's interface was not reworked.

### Astratto Engine
- **Particle system** (`engine/fx/ParticleSystem`): every particle in the game is its - flame, smoke, embers, sparks,
  debris, dust, rain, snow, motes, muzzle flash, and a shockwave ring on weapon fire and explosions.
- **Weather** (`engine/weather/Weather`): clear, fog, rain, heavy storm with forked lightning and late thunder, snow.
  Wet ground, puddle ripples, settling snow. Changes by the hour, is saved, and can be set (`weather <kind> [now]`).
- **Foliage** (`engine/foliage`): voxel plants across the whole map, placed by rule and seed - density masks, wall
  feet, fence lines, corners, paving cracks, reeds, ivy. They move in the wind, part for a body and burn off in a blast.
- **Decal engine** (`engine/decal`): dirt beneath windows, moss at wall bases, cracks and patches along paths, rust,
  leaves, stains - 45 painted variants - plus bullet holes and scorch. Also draws a room's own wall finish and ceiling.
- **Props** (`engine/props`, `world/gen/PropLib`): about 220 instanced voxel models - furniture, machinery, wards.
- **Rendering**: the normal matrix is the inverse-transpose; sky scattering (Rayleigh and Mie); soft volumetric fog.
- **The mark** is Atlas holding the Earth on his palm.

### Fixed
- Fog no longer draws the outlines of buildings; it starts nearer and is locked to the survivor, not the camera.
- Bloom and lamp glare are much weaker, most of all indoors.
- **Sound is heard in the space it is made in**: walls, roofs and floors stand between you and gunfire and effects.
- **Inspect works**: a window with the model turning, the description and figures under it, and Close.
- NPCs and infected at doors, turning on the spot, and stuck on a path: steering re-plans, side-steps and allows for
  the width of a body. Human NPCs no longer run backwards to cover. The squad regroups on the leader.
- A developer spawn goes to the floor pointed at.
- The first-aid spray in third person: the weapon is slung while it is used.
- A jump keeps both hands on a long gun (the weapon used to hang in the air).

### Removed
- Every human voice but the survivor's own. The climb grunt.

### Changed
- **Inventory**: the left panel is there only when something is open (an empty container says "Empty"); cells are one
  size at every rank and the panel grows instead; equipment is laid out on a figure, for you and for a searched body;
  controls are under the grid; flat outlines, larger text, gold only for what is selected.
- **HUD**: one lower-right block (health, ammunition, weapon, four labelled meters with a sprint mark); "No Shotgun
  Shells" and the like by the counter; one notification column, top left; the clock only under the minimap; rank-up
  as a small notice; extraction in a fixed lower-centre panel with a large timer. Dark plates behind everything.
- **Third person**: aiming sits a little lower; the camera is closer, is walked along its whole boom so it clips
  less, and in cover slides to the open end to see round the corner.
- **Stairs are stairs** everywhere, walked up and down, never stepped blocks.
- **Animation (Sydney)**: the climb is worked out from the ledge - hands planted on its lip - in third and first
  person; the sprint is a longer, hunched stride with fewer footfalls; the Bomber leans back and rolls.
- **Spawns**: far more infected and more special infected at night; by day about as many people as infected.
  The Butcher only at Halloran Mansion (and sometimes Blackmoor's ward); a Tank lives in the reactor core.
- "Chernobyl Power Plant" is the **Power Plant**.
- Corpses and blood do not stay: bodies clear after a time, blood dries.
- **Bandits loot your corpse** - the one who killed you, as often as not - and the death screen says what they took.
- Shotguns only take a head off inside 3.5 m.
- The shop sorts by rarity or by price, either way. The crafting screen is recipe cards with pictured ingredients.
- Devmode has a PROFILE tab (XP, rank, prestige, money, health, weather) beside its spawn lists.
- New main-menu music and deployment-screen music.

### New special infected
- **Crawler** (Industrial): skinless, on all fours, blind and hears everything. Throws a barbed tongue at a noise and
  drags whoever is there to it; leaps; hangs from ceilings; goes straight up walls.
- **Tyrant** (Munincipal): hulking, greenish-grey, in a bloodstained trench coat, taller than the Butcher. Sets its
  shoulder and charges; whoever it hits is knocked down or thrown.
- A **Tank** (or Tyrant) stopped at a door goes through the wall the door is in - where the wall is timber.

### World
- **Halloran Mansion** and the **Power Plant** rebuilt by hand, room by room, with infected living in both.
- **Blackmoor Correctional** rebuilt as one building with **Ward C**, a psychiatric wing: corridor, day room,
  treatment, hydrotherapy, padded cells, theatre, mortuary.
- Every ordinary room has its own wall finish, its own ceiling and things of its own on the walls and floor.

### Missions and people
- **Twenty faction missions**, five each for Halberd, the Military, the shelters and the Rail Yard Crew, given and
  settled in person by four advisors - Nika Beridze, Lt. Vera Sokolova, Kateryna Melnyk, Wade Ellis - each ending in
  a decision that is kept (`content/faction_missions.json`).
- **Meetings on the road**, one kind per faction - a checkpoint, a toll, someone asking, a price - put as a question
  with real answers.
- **Squad at a door**: look at it and press [F8] to have the team open it and clear the room, [F9] to take the house.

## Version 0.9

What was verified for 0.9, how, and what was not, is in `docs/V0.9_IMPLEMENTATION.md`. Read its "Not done, partly
done, and not verified" section before trusting any line below. Nobody has played this version: it was checked by
613 headless gates (24 of them new: `ABS_SELFTEST=v09`) and by stills from scripted runs (`docs/v0.9/`).

### Sound
- **Nothing the game plays is synthesised any more.** All 94 world sounds that 0.8 left synthesised - engines, tyres,
  horn, doors, reload foley, footstep fallbacks, radio, geiger, siren, thunder, the old one-shot gunshots - and the
  twenty interface, music and voice sounds besides are recordings: 129 more found online (Freesound, all CC0,
  credited by file in `assets/audio/pack/CREDITS.txt`), and supplied recordings where one already fitted. The audio
  gate now fails if a single synthesised name is left. Music, sirens and room beds are streams (no start-up cost).
- A sound that is looped says so in `content/soundpack.json` (`"loop": true`) and is joined to itself at load. The
  engine note starts at the recording's own pitch; the horn sounds once per press (it used to be struck three times).
- **A window breaking is one sound** (`scene/GlassAudio`): the panes of a window that go together are one break,
  and their shards landing are heard once or twice - not once a pane and once a shard. Bullet strikes on glass are
  unchanged.
- The rank-up sound is played with the XP bar in the operation, and **not again on the debrief**.
- `gradlew audioProbe` prints the length and loudness envelope of recordings, for choosing them without hearing them.

### AI (NeuralAI)
- **Nobody runs in circles.** A body turned at a fixed rate while moving at full speed along its heading, so a
  cover point inside its turning circle was orbited instead of reached. Bodies now turn faster, slow by how far off
  their heading they are, ease down as they arrive, and step straight to what is too close to turn onto.
- **Cover and hiding come first.** Taking cover outranks standing in the open; an advance has to gain ground (it
  could be handed the cover it had just left); nobody bounds into the open at a target that is looking with no one
  suppressing; with no cover point to be had a body goes to ground **out of sight** and stays down there; pacing
  left and right while shooting is only done under fire; the "step aside" reflex no longer drags a man off his run.
- **A broken man hides.** Withdrawal is to a place the threat cannot see, and staying down in it - not a run to a
  point 24 m off, a turn, and another run when the threat reappears. He does not come out to hunt what broke him.
- **Walls and objects obstruct vision.** Lines of sight are walked cell by cell (they were sampled every 40 cm and
  leaked past the corners of blocks); furniture hides at the heights where it is solid (a desk, a barrel, a sofa);
  a crouched body's head sample is the face, not the crown, so **crouching behind a single block hides you**. The
  sentry's warning and the stranger's greeting now ask the senses instead of a bare line between two heads.
- **A house is searched as a house.** A target lost in a building, or at its door, makes every room of it on every
  floor a place to look: the squad says so, takes it room by room, and does not leave it half searched.
- **Navigation goes to a floor**, not to a column: a route to a room upstairs ends upstairs (it ended underneath).
  The infected go up to what they are after instead of milling about below it.
- Infected **feeding** is held, not restarted: the body gets down once and stays down (it rose and dropped every
  1.3 seconds). The clip is eight keys round one cycle.
- The **Butcher's saw runs**: the chain moves round the bar, the engine shakes the saw in the fist and is heard as
  one engine that follows the Butcher, climbs when it comes on and screams when the saw goes up - and **stalls when
  the Butcher dies**.

### Bodies
- **Ragdolls stay under physics.** A body that has come to rest is woken by a blast beside it or a round going into
  it, and is simulated until it rests again; limbs no longer pass through the body (self-collision); a body is still
  when it is still, not after eight seconds. Slips, flash staggers and shoves are answered with physics out to 90 m
  (it was a stumble clip past 55 m, and always for those three).
- **Pull-ups.** The survivor ([jump] at a wall) and every person pull themselves up onto a ledge two or three
  blocks high - up to a block taller than they are. People's routes use it; the dead's do not.
- **The silent takedown.** With a knife in the melee slot, [F] behind someone who does not know you are there puts
  them down without a cry: only somebody who was looking that way knows. Enemies from any stance; someone who is no
  enemy only from a crouch. Each pays **250 XP** (the first twelve of an operation).
- **A body's equipment is equipment.** Searching a corpse shows what it held and wore in equipment slots - weapon,
  sidearm, melee, head, face, armour, pack - above what was in its pockets, and none of it loose in the grid.

### Vehicles
- You get in on **the side you are standing at**. From the passenger's side that is the passenger's door and seat,
  then across to the wheel - the body is no longer carried round the car to the driver's door.

### World
- **The power station is the Power plant** (`world/gen/Landmarks09`, drawn by hand on `world/gen/Blueprint`):
  the turbine hall the length of the site with its four sets, the deaerator stack, units 1-2 with the old stack,
  unit 3, the auxiliary block carrying the banded ventilation stack in its frame, unit 4 under the Sarcophagus with
  its cascade wall and buttresses, the administration block and the long elevated corridor to the golden corridor.
  Twenty-eight rooms, none under five blocks high: control rooms, pump halls, unit 3's hall with its reactor lid
  and fuelling machine, unit 4's open to the pit with the lid on its edge. The breaker and the freight siding are
  where the game needs them.
- **The Halloran Mansion is redesigned**, abandoned: the two-storey main hall with its staircase and
  gallery, the dining room with the long table, the fireplace and the balcony, kitchen, bar and piano, the gallery
  of statues, the corridor with the windows, store room, east stairs, library, study and bedrooms; a columned porch
  with a terrace, tall windows, a slate mansard with dormers and chimneys, a fountain, a graveyard, the forest.
  The commune that holds the district camps in the grounds and keeps out of the house.
- **Every district has hotspots and random events of its own** (`content/district_events.json`,
  `scene/DistrictEvents`): one hotspot in progress per operation, turning over with the day - loners holding an
  apartment block downtown or a crew stripping the Fresh Market, the pharmacy run or the boarded house, the fuel
  fight or the workshop strip, the depot break-in or the records office, the roadhouse or the dead at the last
  store - said once on entering the district, marked on the field map, staged when you are near, ended when one
  side is gone, and worth 900 XP and standing if you had a hand in it. Smaller events happen near you: gunfights,
  patrols, looters, scavengers, a survivor calling for help, a pack on the move.
- **Fog is stronger in the morning and at night** (`ZoneState.mistAt`): thickest just after first light, heavy all
  night, gone by the afternoon; the fog bank stands nearer with it.
- Operations **start somewhere sensible**: room to stand, a wall on one or two sides (never an alcove), three ways
  out, and a walked route to the insertion point - never the dead end behind a skip or the wrong side of a fence.
- The field map is **one sheet**: zoomed out past the edge of the city it no longer repeats.
- A member of the Mercenaries is **a mercenary** ("mercenarie" came from cutting the last letter off the faction).

### Interiors and camera
- **Interiors**: the air indoors glows by the light that reaches it (it glowed with the open sky's everywhere, so
  rooms were milky and dark rooms were never dark); the fog bank no longer dissolves the far end of a long hall;
  hand-built rooms' lamps run off the district's power like every other lamp.
- **Over the shoulder**: the camera goes over the **left shoulder** when there is a wall at the right one or you
  lean left, instead of being crushed onto the back of the head; the reticle's ticks open and close with how wide
  the shot can go; and when the muzzle's line is blocked by your own cover the reticle dims and a red cross marks
  where the round will actually stop.

### Director Mode
- **You can look and move.** The lock that holds the controls across the curtain only counted down while a menu
  was open, so after entering the scene nothing answered until [F1] had been pressed once.
- **The mouse works every menu**: point to highlight, click to select, click beside a value to turn it, wheel to
  move, right-click to go back. The pointer is free whenever a menu is open.
- **The character creator is in it**: Create a character - the game's own creator, a name - and it is an actor of
  your own under "Your characters", kept between sessions, shortlistable, editable.
- **Recordings can be edited, and are easy to find**: [F6] or "Edit recordings" (in the scene and on the casting
  screen) opens the editor with the latest take already on the timeline. The timeline is clicked and dragged;
  pieces can be split at the playhead and duplicated; clips can be deleted from the disk.
- **Perspective is chosen in the editor, and only there.** The scene is played from the actor's own view (the
  camera modes are gone from the scene menu, and [V] is refused). A take now keeps a **scene tape** - everybody's
  bones, the weapon in the hand, the vehicles, the camera - so in the editor the same take can be looked at live
  from a third-person, orbit, fixed or free camera and **re-shot** from it as a new clip with the original sound.

### Console
- `hotspot [go]`, `event <kind>`, `photo` (the picture and nothing else).

## Version 0.8

What was verified for 0.8, how, and what was not, is in `docs/V0.8_IMPLEMENTATION.md`. Read its "Not done, partly
done, and not verified" section before trusting any line below. Nobody has played this version: it was checked by
589 headless gates and by stills from scripted runs (`docs/v0.8/`).

### One AI
- **The old AI is gone.** The `absentialis.ai` package (Brain, Tactics, SquadMind, CloseCombat, Specials) is
  deleted. The squad, the infected, the special infected and every faction think in NeuralAI
  (`neuralai/integration/`: Creatures, Routines, Relations, Steering, Blows, NeuralHost; tuning in
  `content/neuralai.json`, which now also holds the body table of every kind).
- The infected have minds of their own kind: they hunt, scour, prowl, notice, **feed** and wander - and feed on any
  body they can reach, the player's and other NPCs' included.
- A regular infected asked for by any of its names (`spawn regular`, `fast`, `zombie`) is one. A name that is no
  kind is refused instead of becoming an unarmed person who ran away.
- **The Gladiator is blind.** No sight, no vision cone, nothing learned by seeing: it has what it hears.
- One mind that trips no longer stops the game: the fault is logged (`ai-fault.log`), counted and the mind restarted.

### Squad
- Squadmates form up behind the **character**, not behind the camera.
- A move order plants a marker where it was sent - a staff, a pennant in the team's colour, a ring, a light - that
  fades after a few seconds.
- **Hold fire** ([F7], console `holdfire`): the squad fires nothing until told, and still defends itself. The HUD
  says so while it holds.
- Nobody opens fire on what is too far off to hit: each weapon has a range it is used at.
- NPCs do not shoot their own cover: they lean round the end of it to fire, or hold and move.

### Fixed
- Glass seen through glass no longer flickers or disappears (frames are opaque; panes blend order-independently).
- The crash in `crash.log` (a role change mid-fight left a mind in cover that was no longer there) and a second
  one (a melee kill while the actor list was being walked). Crashes are now also kept in `crash-history.log`.
- Prone and wounded bodies lie on the floor, not in it; a prone body no longer swings round with the camera.
- A body on the floor below is not offered for searching. A door or a container being looked at wins over a body
  lying near it, and doors push bodies out of their swing.
- Car doors open outward. The driver sits, with hands on the wheel that turn it; nobody slips in blood in a car.
- A car is dented where it was hit.
- The HUD portrait shows the face.
- A lean no longer tilts the picture, and cannot put the eye inside a wall.

### Profile, menu and difficulty
- The game runs on the **Astratto Engine** - copyright Astratto North of LudusValley Recreations, under VEIN
  Enterprises - whose mark is a brain in profile (`engine/Astratto.java`; title and loading screens).
- With no profile the title menu is **New Game, Settings, Quit**. Continue appears only for a profile that loads.
- **Difficulty is chosen when the profile is made and is locked**: Easy, Normal or Hard, with Ironman available on
  any of them. It is no longer in Settings.
- **Death**: on Easy you keep five things of your choice and lose 5% of your cash; on Normal three and 10%; on
  Hard nothing and 20%. Anything carried can be chosen, jewellery included; the death screen has the list.
- The Allegiance screen is replaced by one question - **What are you looking for? Profit, Stability, Peace, Chaos**
  - each a sign and a word. The answer leans you toward the Mercenaries, the Military, the Loners or the Bandits:
  your start district, whose work is first on the board, and a standing with them that never goes away.
- "Government" is the **Military** everywhere. The districts are **Downtown, Residential, Industrial, Munincipal
  and the Barrens**.
- Every operation starts **tucked away**: a wall at your back or shoulder, few sight lines onto you, two ways out.

### Missions and contracts
- **The Names of the Dead**: five investigations from `lore.md`, one to a district - The Roll Call, The Room with
  the Light On, The Empty Transfer, Silence on the Airfield, The Second Inventory. They are never accepted and have
  no clock. Each is a rumour until you set foot on one of its two sites, an investigation until you have seen one
  of its two pieces of evidence (really there, in a container of that place), then wants only that you get out
  alive, and ends with your decision about who is given the proof. Any order. Death takes none of it back.
  Tracked in the CONTRACTS tab; each pays a one-time story award (`content/story.json`).
- **Ten written contracts** replace the generated board: ten different kinds of task from the four sides, three
  held at once, paid on extraction, back on the board two operations later.

### Sound
- The audio engine is **UltraEngine**. It reads **WAV** (8/16/24/32-bit PCM, 32/64-bit float, any channels and
  rate), **Ogg Vorbis** and **MP3**, by what a file contains.
- **All 298 supplied recordings are in the game** (`assets/audio/pack/`, mapped in `content/soundpack.json`): the
  interface and inventory sounds, a bed for each district, bullet strikes by material (masonry, plate, car
  bodywork and glass, wood, earth, foliage, cloth, glass, tile, flesh, armour), explosions, grenades, knives and
  fists, the player's own hurt, death and breathing in a man's or a woman's voice, rank-up, mission, contract and
  squad-loss stings.
- A sound with several takes never plays the same one twice running.
- **Guns lead with close recordings of themselves** (thirteen weapons; suppressed recordings for two), over the
  layered sets that carry distance and reflections.
- The chainsaw, claws, hammer, shotgun hulls, spray, shop purchase, wind, rain and eighteen more are recordings
  found online (Freesound, CC0; `assets/audio/pack/CREDITS.txt`). 94 world sounds are still synthesised.

### HUD and camera
- The XP bar is **not on screen until experience is earned**; it comes up small in the corner with the Rank's
  **insignia**, and goes. Time spent says nothing. A rank-up is announced once, with its sound.
- Third person: the camera is closer, closer again when aiming and out over the right shoulder; the body is solid
  unless a wall squeezes the camera onto it; **aiming shows a reticle**.
- A small **stance icon** appears when you stand, crouch or go prone.
- **Lasers come in red (default), green and blue.** A laser is a module on the weapon, dark until you aim; its
  dot is drawn in the world, lights what it falls on, and replaces the reticle.

### Characters and animation
- The Gladiator and the Butcher have their own wind-ups and swings (the swings 0.7 asked for did not exist), the
  Gladiator's head turns to listen, the saw is alive in the Butcher's hand.
- Sprinting in third person is looser; with a pistol the arms pump as they do with nothing.
- The baseball hat is a cap again.

### Items
- **Eighteen new pieces of equipment**, three to a rarity - a helmet, a set of armour, a pack - from the Hardhat
  and Schoolbag (Rank 3) to the Powered Composite Armor and the Military Warbag (Rank 40). Protection is the armour
  class: helmets protect the head by their own class, armour that covers arms and legs protects them, a ballistic
  pack protects the back.
- **Spinels in ceilings**: lodged overhead indoors and under overhangs outdoors. Shoot one down and pick it up.
- **Five profile cards**: Jail (Rank 4), Thunder (Rank 10), Operator (Rank 20), Prestige (Prestige 1),
  Prestigious I (Prestige 2).

### Developer Mode and Director Mode
- **`devmode`**: the player character is gone. You are a camera (noclip) that nothing sees, hears or touches,
  with coordinates always on screen, an inspector for whatever you look at, and a workbench ([Tab]) for putting
  any NPC, weapon, item or vehicle where you are looking, and for the clock, weather and AI.
- **`directormode`**: a cinematic sandbox (`absentialis.director`, twelve managers). A casting screen (actors with
  a live preview, a shortlist of ten kept between sessions, loadouts, settings), then the world as that actor -
  yourself, a member of any faction, any infected, any special infected - with an interaction menu ([F1]) for time,
  frozen time, wanted status, people and vehicle density, invincibility, noclip, locations, dialogue and gestures,
  switching actor on the spot, vehicles, a free, orbit or fixed camera, NeuralAI, and **recording** ([F5], three
  minutes at most). Clips go to a **cinematic editor** (timeline, sequencing, re-framing, transitions, speed,
  grades, sound levels) that **exports .avi video**. Leaving puts the survivor, their kit and the world back.

### Console
- `give` takes any item by id, by the end of its id or by name, a category, or `all`; what does not fit is put at
  your feet.
- New: `items`, `rank`, `prestige`, `difficulty`, `pos`, `goto`, `kill`, `clear`, `freeze`, `holdfire`, `car`,
  `weather`, `hurt`, `sound`, `spinel`, `faults`, `version`, `devmode`, `directormode`.

### Saves
- Save format 8. Profiles from 0.7 and earlier load: difficulty becomes Normal, contracts that had been accepted
  are kept and paid as agreed, generated offers are replaced by the new board.

## Version 0.7

What was verified for 0.7, how, and what was not, is in `docs/V0.7_IMPLEMENTATION.md`. Read its "Not done, partly
done, and not verified" section before trusting any line below.

### Human AI: the NeuralAI engine
- Every human NPC - hostile, neutral and the player's own squad - now thinks in a dedicated engine
  (`absentialis.neuralai.*`, tuning in `content/neuralai.json`, guide in `docs/neuralai-tuning-guide.md`), built to
  `NeuralAI.md`. Nobody reads a target's position: each mind has what its own eyes and ears gave it (a vision
  meter that fills with exposure, sounds with an error that grows with distance and walls), what it was told
  (reports that arrive by voice or radio, late and second-hand) and what it found (bodies, blood), held as beliefs
  with an uncertainty that grows while the target is unseen.
- From that it searches (a shared grid of regions, divided between the squad), takes cover it has reserved, is
  dealt a role (hold, suppress, advance, flank left or right, retreat) that is re-dealt as the fight changes,
  fights from cover in bursts, fires at where it believes the target is, holds fire when a friend is in the line,
  closes to a blow at arm's length, and gives up a route the body reports blocked.
- **Squadmates no longer see through walls** (they were simply told who shot them, and where). They use the same
  engine as everyone else, follow behind the leader in two files, and keep to the order they were given.
- NPCs and squadmates **sprint** when the situation calls for it, and tire.
- Twelve acceptance scenarios from `NeuralAI.md` section 15 run with the gates (`ABS_SELFTEST=neural`).
- Console `ai`: an overlay of each mind's cone of sight, believed position and uncertainty ring, cover, destination
  and search regions - and, in magenta and labelled as developer-only, where the target really is.

### The infected
- They **wander** when they have nothing to hunt, turn their **heads** to a noise before their bodies, and go to
  look. They are all called **Infected**, and only the special ones appear in the kill feed.
- They **climb through one-block openings** up to three blocks above their feet (up the wall, through on the
  belly, down the far side), and choose an opening over a long walk round. A **Wooden Plank** boards one up ([F]).
- **Special infected**: the Boomer is the **Bomber** (it bursts into bile and no longer spits); the **Spewer**
  spits pools that burn; the **Tank** knuckle-walks, and picks anyone up in one hand and throws them fifteen feet;
  the **Gladiator** is blind, armoured everywhere but its back, comes at a run to any noise and takes heads at
  head height (crouch, or stand still); the **Butcher** rushes what it sees with a chainsaw - a round to its head
  during the wind-up stops the swing. Every attack has a wind-up you can see and hear.
- **Infected bile**: dark green, on the blood system. It does no damage; whoever wears it is hunted by every
  infected within 45 m for 15 seconds and sees through a green blur for as long.

### Sydney Engine and bodies
- Fall study: ten kinds of hit photographed every fourth frame and tiled into contact sheets
  (`docs/v0.7/falls/`, `ABS_SCENARIO=falls`). From it: a dying body **collapses from the ground up** (knees, hips,
  then the trunk) instead of tipping like a plank; a body keeps its **momentum** (the air used to take three
  quarters of its speed every second and the ground all of it); blood pools are a metre across, darker, and no
  longer glow.
- **Vehicles and people**: above a walking pace of closing speed a pedestrian is knocked down and carried by the
  bodywork - legs taken at town speed, the whole body thrown at speed - and a body lying in the road is shoved and
  rolled by a car driven over it.
- **Car doors** lead: to the door while it opens, in through it, and it is pulled shut; out the same way.

### World
- The map, districts, roads and building positions are unchanged.
- **Ceilings are four blocks** everywhere (storeys are five blocks slab to slab; stairs have a fifth tread).
- **Interiors refurnished**: furniture has the shape of furniture (desks, screens, monitors, basins, bins, racks,
  plants, standing lamps, bedside tables, a ceiling fitting that is a fitting); shop shelving is short broken runs,
  mostly bare frames with a few stocked units, heaps and counters; no row of three of the same container anywhere;
  homes are plastered and papered where they were shuttered concrete; daylight reaches indoors.
- **Procedural foliage**: boughs, overlapping noise-shaped leaf clusters, four tones of leaf, soft shading among
  leaves, faces between leaves culled. Trees no longer reach into rooms or over stair heads.
- **Fog** is heavier outdoors (from 30 m, complete by 118 m) and thin under a roof.
- **devmap** (console `devmap [0-3]`, `devmap off`): a walled test field with a 170 m road, an 11 x 11 maze, the
  Zone's mansion on two storeys with a double door, and a yard of cover, openings, glass, containers and ladders.

### Operator, squad, shop
- **No deployment time limit.** Choose **Morning, Afternoon or Midnight**. Abandoning an operation keeps your gear.
- **Coins are Cash ($).** Shop departments: Weapons, Medical, Supplies, Armor, Vehicles, and Sell. Rank-locked
  items are hidden until unlocked; unlocks are announced after an operation. Prices and ammunition per the 0.7
  table; new **Assault Rifle Ammo** for the Semi-Auto and Full-Auto Rifle; the Matilda fires single shots.
- **Rank 1 starts with** a loaded Handgun, 30 rounds, a knife and a First Aid Spray. The Shotgun opens at Rank 2.
- **No squadmates at Rank 1.** A slot opens at Rank 4 and every fourth rank to 36; squadmates are hired in the
  shop and come with the standard kit of their type, shown as equipment rather than a weapon list. The squad
  deploys behind you.
- The character is fixed at creation; the old Operator tab is gone and Rank is now Operator. Treatment is gone
  from it. The player is a third party who declares an allegiance rather than joining a group.
- **Crafting**: ten new recipes (powders, plank, casing); the craft list shows only what can be made now, as
  cards, counts the stash at home, scrolls inside its panel. **Shift-click** equips when nothing else is open.
- New items, armour, hats and gems with their models; corpses carry what they wore and are drawn without it once
  it is taken.

### Handling
- No shot on the click that closes a menu. Hold crouch to go **prone**; crawl any way; sprint from crouch or prone.
- Grenades are **equipped** and thrown along a drawn arc. The knife is larger and stays in the hand.
- Cover is taken side-on with free look. A handgun hangs at rest and is held in both hands to aim.
- A weapon light is a light on the weapon. Guns eject brass their own way and smoke at the muzzle. The First Aid
  Spray is sprayed. Eyes blink, and close in death. The HUD portrait faces front.

### Audio
- **Removed**: every synthesised district bed (the Outskirts hiss, the Residential air, Downtown's bells, the
  hum and drone), and the failed-reload noise.
- **Recorded voices** for the infected and for people, 39 takes from three CC0 packs (`ATTRIBUTION.md`); people cry
  out when hit and when they die. Chosen by measurement, not by ear - see the tracker.
- Weapon sounds were **not** replaced (see the tracker).

### Developer
- `ABS_SCENARIO=<name>` runs a short scripted visual check (`shop`, `menus`, `specials`, `falls`, `car`,
  `interiors`, `devmap`). A gate group that throws is one failed gate, and the suite goes on.
- Console: `devmap`, `time <hour>`, `weaponlevel`, `ai`.

## Version 0.6

### World and buildings
- The map, its districts and its roads are unchanged. **Buildings were rebuilt**: a late-Soviet street - panel
  blocks with balconies, sand-lime and red brick, dark plinths, concrete entrance canopies, windows two panes tall.
- **Stairs fixed.** Every flight has a fourth tread level with the floor it reaches and clear head room over every
  tread (0.5 stopped one rise short and left the slab over the first step).
- Two-cell entrances are **double doors**: one object, two leaves.
- Interiors are **furnished densely** (about half of every room's wall is taken), storage stands two tiers high,
  corridors have the two-tone painted wall, and every room has its light fitting, lit or dead.
- **Textures redrawn at 64 x 64** (were 32 x 32): masonry, panels, plaster, roofing, floors, carpets, ceilings.
  Windows are a casement round a pane. No redrawn texture carries lettering; torn wallpaper and the ringed lamp
  are gone.
- **Fog bank**: the world dissolves from 48 m and is gone by 165 m, inside the draw distance, so nothing is drawn
  at its edge. Nearer at night and in rain. Nobody sees or shoots through it either (`content/combat.json`).
- A broken window leaves **physical shards**, thrown the way the round was going.

### Movement and camera
- **No auto-jump**, for the player or anyone else: a block in the way is jumped, stairs are walked.
- **Third person rebuilt**: right-shoulder camera with free look, smooth tracking, aim zoom, and framing that
  follows what the body is doing; movement relative to the camera in every direction; the body turns to its
  travel; weighted acceleration and turning; a **slide** from crouching after two seconds of sprint. Sprint can be
  held or toggled; distance, shoulder and smoothing are settings.
- Characters **do not pass through each other**. Bodies push doors open; a door rests against someone standing in it.
- Fixed: a door could push you up a floor. Fixed: A steered the car right.

### Combat
- **Sydney Engine** (`sydney/*`, `content/sydney.json`): the passive ragdoll became an active one. A hit body is
  given to physics with the momentum of the blow where it landed; sensors read its balance, virtual muscles
  (bounded PD torques) work to keep it up, and the solver decides. It sways, catches itself with a step, or goes
  down reaching for the ground, lies there, and gets up under its own strength - or is left to crawl if a leg is
  gone. People put a hand to the wound; the infected take more to put down and do not. The player's character is
  given to it only by death. Console: `sydney on|off|stats`.
- **Shotguns** hit much harder; a close-range head shot takes the head off. **Handguns never do.**
- A pump shotgun is **racked after a full reload** before it will fire.
- Firing has weight by weapon: a handgun snaps, a shotgun or a magnum rocks the upper body and settles more slowly.
- **Optics help**: the red dot and the holographic sight are open windows with a projected reticle; scopes have a
  proper sight picture.
- **Gun audio** levelled by measurement: no firearm is quiet, shotguns are the heaviest report, the crossbow has
  its own sound. The industrial hum and static are gone.
- Fixed: crash when drawing a weapon from the Tab wheel. Fixed: the AI could not use ladders.

### People
- **Male or female** bodies (narrower arms, trunk and head; six more hairstyles). Generated names are American
  and match the body.
- **Name tags** over heads in the colour the minimap gives them; squadmates' are always shown.
- The dead **close their eyes**. The infected **eat human corpses**, which waste away and are finally gone.
- AI sight uses three lines to a body (half hidden is half as noticeable), registers the edge of its vision more
  slowly, and looks along the ground until alerted; a floor between muffles sound; the alerted hear further.
- **Barter** with anyone not hostile - goods for goods, on terms set by what their group thinks of you - and pass
  kit freely to and from **squadmates** ([F], facing them).
- **Squadmate permadeath**: a squadmate who dies is gone, with rank and record; replacements are recruited and
  paid for.

### Vehicles
- The driver's door is a **real door** on the door engine: it opens to let you in, shuts behind you, takes damage
  on its hinge and its latch, and can be shot off - after which there is no door to open.
- Getting in and out have their own animations. **Squadmates ride along** (up to three) and get out when you do.

### Items and interface
- The **flashlight is an object**: carry one in the case (one is issued to every profile) or fit the Mounted
  Flashlight to any firearm from the first day. [L], rebindable.
- Herbs are the colour they are called; **Green + Red + Yellow** is a new mixture and shows all three.
- **Six ammunition models**, one per calibre. Shop tabs: a handgun for Weapons, cartridges for Ammo.
- **XP bar** with the percentage to the next Rank, and a line for every award as it is earned.
- **Profile card** with ten backgrounds unlocked by Rank; squadmates have the same card.
- [F] closes the loot menu. Dev console: `money`, `vehicle`.

### The Zone
- **Alliance**, chosen once after creating a profile (Military, Bandits, Loners, Mercenaries): it decides where
  you can deploy from and what work you are offered.
- **Population** sliders before profile creation, overall and per group.
- **Leaderboard** tab on the deployment screen: everyone alive, ranked. Every NPC has a Rank.
- **Group Rivalry System** (`rivalry/*`, `content/rivalry.json`): groups and their leaders remember what you do
  and what they hear of it, adapt to it and act on it; a Rivalries tab shows where you stand and what you can do.
- Fixed: extraction points on the city wall read as Wilderness and irradiated you.

### Project
- Version 0.6 in one place (`absentialis.Version`); save format 6 with migration from 5 (nothing is lost: a
  flashlight is issued, generated squad names are redrawn, typed names are kept).
- 483 quality gates (89 new for 0.6).

## Version 0.5

### World
- Rewrote city generation as a twelve-stage, data-driven pipeline (`content/districts.json`,
  `content/buildings.json`): district layout by terrain suitability, protected road network, lots, landmark
  compounds, room-graph interiors, furnishing, validation with repair or rejection, and a generation report.
- Six environments: Residential, Industrial, Governmental, Downtown, Outskirts, Wilderness. Each developed district
  has exactly two landmark compounds and one faction headquarters.
- Every room of every building is reachable from the street; buildings that cannot be made so are repaired or removed.
- Fixed: the perimeter trench; glass-like towers outside the boundary; floating dead-tree canopies; canopies
  blocking road cells.
- Cover points generated from world geometry, per chunk, invalidated when blocks change.
- Debug layers as PNGs (`ABS_GENDUMP`): plan, footprints, rooms, navigation, invalid cells, materials, cover.

### Combat and characters
- Tactical AI: utility-scored intents (hold, peek, suppress, advance, relocate, withdraw) over a squad blackboard;
  cover selection from the cover map; leaning and pop-up fire; chokepoint awareness; rescue of downed allies.
- Casualty state machine ACTIVE -> DOWNED -> REVIVING -> ACTIVE (or DEAD): 20 s bleed-out, 4 s revive,
  interruptible. Infected are never downed. Fatal regions and catastrophic damage always kill.
- Fixed: the player's body vanished on death.
- Four health conditions with configurable thresholds (`content/combat.json`) affecting speed, sprint, aim and
  animation.
- Cover for the player in first and third person; cover and injury animation layers.
- Physical doors: hinge swing, regional damage, latch and hinge failure, debris, navigation updates, AI door use.
- Aim-down-sights aligned from each weapon model's own sight line, with eased blending.
- Yellow Herbs raise maximum health permanently (saved with the profile, kept through prestige).

### Interface
- Status module (lower right): portrait, health arc that lengthens with maximum health, loaded and reserve ammunition.
- Squad status column and killfeed on the left; one line per confirmed death, none for incapacitation.
- Hit marker from confirmed events only: white hit, yellow incapacitation, red kill.
- Minimap contacts coloured by relation (blue squad with facing, green friendly, yellow neutral, red hostile).
- World-space speech bubbles driven by the existing dialogue.
- Resolution-independent layout helpers; fixed the Insertion Point text overflow in the hideout.

### Audio
- Layered gun audio (`engine/audio/gun`): per-weapon profiles, sample-accurate shot scheduling, transient / body /
  tail / distant / mechanical layers, voice budgets, occlusion, early reflections, one shared late reverb,
  compressor and limiter. Player and NPC fire use the same path.
- Recorded assets with documented licences (`ATTRIBUTION.md`, `docs/audio/weapon_asset_manifest.md`), a reproducible
  pipeline (`gradlew prepareAudio`), originals preserved in `tools/audio/originals/`.
- Recorded footsteps for nine surfaces, with variation. Reload cues play recorded handling sounds on the reload
  animation's clock. Door latch, kick and break sounds.

### Project
- Version 0.5 in one place (`absentialis.Version`); save format 5 with migration from 4.
- 394 quality gates (81 new for 0.5).

## Version 0.4

### Fixed
- **No audio.** The Windows Volume Mixer entry for the Java runtime was at 0%. The game now checks its own mixer
  session at start-up and restores it (`engine/audio/WinAudioSession`), shows the device and mixer level, and has
  a test-sound button with live meters. The mixer thread survives a device fault.
- **Crash** in the blood system (`ConcurrentModificationException`): the gore, projectile, thrown and zone loops no
  longer change a list while walking it.
- [F] only plays the interact animation when something was interacted with.
- Weapon icons are fitted to the model and drawn side-on, so every gun fills the tile it occupies.
- A container closes itself a moment after the last item is taken.

### Changed
- **Infection** (`scene/InfectionSystem`): bites can break the skin; BITTEN > FEVER > SICK > CRITICAL with
  symptoms; Antiviral Medicine cures it at any stage. Untreated, the player dies and rises; NPCs turn in the body
  they had.
- **Death**: third-person view of the body as the picture drains, then RESPAWN / MAIN MENU / QUIT. Ironman makes
  death permanent.
- **Lighting** (`engine/render/HybridRenderer`): OpenGL 4.5 deferred PBR in HDR - cascaded sun shadows, tiled light
  culling, shadowed local lights, irradiance probes, GTAO, reflections, volumetric fog, TAA, bloom, auto-exposure.
  Four presets; falls back to the 0.3 renderer without GL 4.5 (`ABS_LEGACY_GL=1`).
- **Damage** (`anatomy/*`, `scene/Gore`): bodies are layered voxels; hits remove what they pass through; wounds
  persist; a limb comes away when what joins it fails.
- **Enemy AI** (`ai/Tactics`, `ai/SquadMind`, `ai/CloseCombat`): awareness from sight and sound only, memory that
  goes stale, squads that share information, roles, scored cover and flanks, suppression, coordinated search,
  close combat.
- **Vehicles** (`vehicle/*`): four-wheel rigid-body simulation at 50 Hz; eight vehicles in `content/vehicles.json`;
  bought in the shop, one per operation, at most four in an operation.
- **Deployment**: a one-page Operation Briefing.
- **Squad**: every squadmate can be renamed.
- **Fire** is a particle system with light, heat, rain and sound (`scene/FireSystem`); the fire voxel is gone.

### Added
- Version numbers on the title screen, hideout, HUD footer and saves.
