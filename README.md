# ABSENTIALIS — Version 1.2

> **Version 1.2** is the current build (see `CHANGELOG.md` and `docs/V1.2_IMPLEMENTATION.md`): the Astratto Engine
> has a native core in C++ (`astratto/`, see `astratto/ARCHITECTURE.md`) that now searches every route in the game;
> animals ragdoll, are hit properly and fly properly; blocking; the twelve-block rule for people; the police station's
> entrance, backwards beds and misplaced fittings fixed; a new leaderboard and engine mark. To rebuild the native core:
> `./gradlew.bat buildNative` (Visual C++); without it the game runs as before, in Java.

> **Version 1.0** is the current build (see `CHANGELOG.md` and `docs/V1.0_IMPLEMENTATION.md`): weather, foliage,
> decals, props and particles in the Astratto Engine; two new special infected; a reworked HUD and inventory; twenty
> faction missions given by four advisors; Blackmoor rebuilt with a psychiatric wing. Read the tracker's first
> section for what is not done.
>
> **Version 0.9** was the build before it (see `CHANGELOG.md` and `docs/V0.9_IMPLEMENTATION.md`): every sound is a
> recording, NPCs take cover and hide instead of circling, walls block sight, houses are searched room by room, the
> power station is the Chernobyl plant and the big house the Spencer mansion, every district has hotspots of its own,
> there are silent takedowns and ledge pull-ups, and Director Mode has mouse menus, a character creator and
> editor-side camera changes. The notes below describe 0.8, which 0.9 builds on.
>
> **Version 0.8** came before it. What it changed is in `CHANGELOG.md`; what was and was not verified is in
> `docs/V0.8_IMPLEMENTATION.md`. In short: one AI engine (NeuralAI) for everything that thinks, the audio engine is
> now UltraEngine (WAV, Ogg, MP3) with a recorded sound pack, difficulty is locked to the profile, five story
> missions and ten written contracts, eighteen new pieces of equipment, Developer Mode (`devmode`) and Director
> Mode (`directormode`). The game runs on the Astratto Engine (copyright Astratto North of LudusValley Recreations,
> under VEIN Enterprises). Two districts were renamed in 0.8: Governmental is **Munincipal** and the Outskirts are
> the **Barrens**; the sections below were written for earlier versions and use the older names.

Single-player voxel extraction survival shooter set in the quarantined city of Absentialis
(Java 21 + LWJGL 3.3.3, the ASTRATTOENGINE [Astratt0]). Prepare at the hideout, deploy with your squad, scavenge, and get out.

## Build & run

```powershell
.\gradlew.bat run --no-daemon        # play
.\gradlew.bat selftest --no-daemon   # 483 quality gates, no window (exit code != 0 on failure)
.\gradlew.bat prepareAudio --no-daemon   # rebuild assets/audio from tools/audio/originals (only after changing sources)
```

The build pins a **Java 21 toolchain** (LWJGL 3.3.3 does not support Java 24); Gradle provisions one
if needed. Fatal errors are printed and written to `crash.log`.

Version 0.2 saves load directly: the old 12x16 case is repacked into the Rank-sized grid (overflow goes
to the stash), magazines become the rounds they held, retired ammunition ids map to the six new types.
The pre-0.3 source and saves are archived in `backup/v0.2-src.tar.gz`.

## Controls (rebindable in Settings unless marked fixed)

| Action | Key | Action | Key |
|---|---|---|---|
| Move / sprint / walk | WASD / LShift / LAlt | Fire / aim | LMB / RMB |
| Crouch / jump | C / Space | Reload | R |
| Lean | Q / E | Fire mode | B |
| Interact (doors, loot, bodies, locked drawers); hold to revive | F | Primary / sidearm / melee | 1 / 2 / 3 |
| Inventory | I | Quick-use slots | 4-7 |
| Weapon wheel (hold) | Tab | Throw (or launch, with a Grenade Launcher) | G |
| Map | M | Flashlight | L |
| First / third person | V | Console | ~ |
| Slide | Crouch after 2 s of sprint | Barter / trade kit | F, facing someone |

**Driving:** **F** beside a vehicle gets in (and out); squadmates close by get in with you and out when you do. **W / S** throttle and brake-then-reverse, **A / D** steer,
**Space** handbrake, **H** horn, **L** headlights, **J** light bar (emergency vehicles), **V** chase / driver's-seat
camera.

**Moving (0.6):** nothing is stepped onto automatically any more - a block in the way is jumped (**Space**), stairs
are walked. In third person the camera sits over the right shoulder and movement is relative to it; sprint can be
held or toggled (Settings), and crouching after two seconds of sprint slides.

**Flashlight (0.6):** **L** needs a flashlight - one carried in the case (every profile has one in the stash), or a
Mounted Flashlight fitted to the weapon at the workbench.

**Trading (0.6):** **F** facing a squadmate passes kit either way; facing anyone who is not hostile it opens barter:
goods for goods, on terms set by what their group thinks of you.

**Bitten?** Use Antiviral Medicine from a quick slot or the inventory. **F** on a bitten squadmate or friendly
stranger gives them a dose.

**Cover:** crouch (**C**) against something low, or lean (**Q / E**) at the end of a wall. Aim to rise over low cover.

**Doors:** **F** opens and closes; on a locked door it forces it (loud). Doors can be shot and blown apart.

**Down:** when you are incapacitated you can crawl and fire a sidearm until a squadmate reaches you or the 20 s run out.

**Ladders:** forward climbs, back descends; letting go is a slow slide, not a fall.

**Inventory:** drag to move, **R** rotates, **Shift+click** sends an item to the other side, right-click
for actions, **Z** sorts. Drag a Green Herb onto another herb to mix them. Hover an item and press **F**
to inspect it full-screen.

**Squad (fixed keys):**

| Key | Order |
|---|---|
| T | Choose who the next order goes to: **Gold** (everyone deployed), **Red**, **Blue** |
| F1 / F2 | Follow me / Hold where you are |
| F3 / F4 | Move to the crosshair / Sweep the crosshair |
| Left Ctrl + order | Queue the order for that team instead of issuing it |
| F5 | **Initiate** (Gold): every team runs its queued order at once |
| F6 | Move the squadmate in front of you to the other team |

## What Version 0.6 changed

Full list: `CHANGELOG.md`. What was built, where, and what the evidence for each item is - including what is only
partly done and what nobody has yet played - is in `docs/V0.6_IMPLEMENTATION.md`.

- **Buildings rebuilt** (the map itself is unchanged): late-Soviet exteriors, stairs that work, double doors, dense
  interiors, every block texture redrawn at 64 x 64 with no lettering.
- **Fog** that closes inside the draw distance.
- **Sydney Engine**: active ragdolls. A hit body fights for its balance, steps, falls, and gets itself up.
- **Third person** rebuilt; no auto-jump; characters no longer pass through each other.
- **Alliance**, population settings, a leaderboard and the **Group Rivalry System**.
- Male and female bodies, name tags, squadmate permadeath and recruitment, barter, squad passengers, a physical
  flashlight, six ammunition models, an XP bar, profile cards.

## What Version 0.5 changed

Full list: `CHANGELOG.md`. Design and audit: `docs/v0.5/audit-and-design.md`. Audio: `docs/audio/`.

- **The city is planned, then built.** Five developed districts (Residential, Industrial, Governmental, Downtown,
  Outskirts) and the Wilderness are laid out as data first - roads, lots, two landmark compounds per district, one
  faction headquarters each - and only then turned into blocks. Every building's rooms are reachable or the building
  is repaired or rejected. Existing saves load; the city itself is regenerated (save format 5).
- **The boundary is fixed at its cause**: the trench was a column of ground never filled at the city edge; the
  glass-like towers were watchtowers placed outside the wall. Both have regression gates.
- **Casualties**: humans can go down instead of dying, bleed out in 20 s, and be revived (hold **F**). The infected
  never go down. The player's body stays in the world as a ragdoll.
- **Four health conditions** (Fine, Caution, Injured, Critical) change movement, aim and animation.
- **Cover**: crouch against low cover or lean at the end of a wall and the body settles onto it; aim to rise over low
  cover. NPCs choose cover from the same data, lean, pop up, suppress, relocate and rescue their wounded.
- **Doors** are physical: they swing, take damage by region, lose latches and hinges, and break into debris.
  **F** opens, closes, or forces a locked door.
- **Interface**: lower-right status module (portrait, health arc that lengthens with maximum health, ammunition),
  squad column and killfeed on the left, three-colour hit marker (white hit, yellow incapacitated, red kill),
  relation-coloured minimap, speech bubbles over NPCs.
- **Yellow Herbs** raise maximum health permanently.
- **Audio**: gunfire is built per shot from recorded layers on a sample-accurate clock, with occlusion, ray-traced
  early reflections and one shared reverb; recorded footsteps for seven surfaces; recorded reload handling.
  Credits: `ATTRIBUTION.md`.

## Console (`~`, Tab autocompletes)

`help`, `god`, `noclip`, `thirdperson`, `give <item> [qty]`, `tp <district|exit>`, `setrep <faction> <value>`,
`econ audit`, `skip hour [n]`, `ai debug`, `noise debug`, `alife`, `freeze`, `save`, `load`, `xp <n>`, `heal`,
`extract`, `time <minutesLeft>`, `spawn <archetype|faction>`, `selftest`.

0.6: `money <amount>` (or `+n` / `-n`), `vehicle <id|list|clear>`, `rivalry`, `sydney on|off|stats`.

## Test hooks (environment variables)

| Variable | Effect |
|---|---|
| `ABS_SELFTEST=all` or any of `inventory weapon progress econ save death extraction noise ballistic tier character anim ragdoll audio alife ai zombie district v03` | run gates headless and exit |
| `ABS_SAVES=<dir>` | use another save directory (automated runs never touch real profiles); with `ABS_SELFTEST=save` every profile there is load-checked |
| `ABS_AUTOSHOT=<dir>` | scripted pass saved as PNGs: menus, raid, a flight over every landmark, doors, a casualty, squad contact, death camera, debrief |
| `ABS_PREVIEW=<dir>` | fixed map preview renders |
| `ABS_SELFTEST=v04` / `vehicle` / `system` / `icon` | the 0.4 gates: vehicle handling validation, infection, death, fire, anatomy, combat AI, audio bank |
| `ABS_SELFTEST=v05` / `world05` / `casualty` / `feed` / `door` / `hud05` / `gunaudio` | the 0.5 gates: world generation, casualties, hit marker and killfeed, doors, interface, gun audio |
| `ABS_SELFTEST=v06` / `zone06` / `audio06` / `phys06` / `combat06` / `meta06` / `world06` / `rivalry` / `sydney` | the 0.6 gates: extraction zones, gun and ambience loudness, movement and doors, shotguns, profile and Zone systems, buildings and items, the rivalry acceptance tests, the active ragdoll (`ABS_SYDNEY_TRACE=1` prints each experiment) |
| `ABS_ATLAS=<file.png>` (optionally `ABS_ATLAS_NAMES=a,b,c`) | paint every block texture without a window and write the sheet (or a tiled review sheet of the named ones) |
| `ABS_GENDUMP=<dir>` (with `ABS_SELFTEST=district`), `ABS_GENCROP="x0,z0,x1,z1;..."` | world-generation debug layers as PNGs: plan, footprints, rooms, navigation, invalid, materials, cover |
| `ABS_NOVSYNC=1`, `ABS_GPUPROF=1` | uncapped frame rate; per-pass GPU timings in the log |
| `ABS_LEGACY_GL=1` | force the 0.3 forward renderer |
| `ABS_TEST_NOVEHICLES=1` | operations start without vehicles |

**Recorded audio:** gunfire, footsteps and reload handling come from `assets/audio/weapons`, `footsteps` and `handling`
(generated - see `docs/audio/audio_architecture.md`). Any `.ogg` or `.wav` dropped directly into `assets/audio/` and named
after a bank sound (for example `engine_loop.ogg`) still replaces the synthesised version at start-up.

## Notes on the brief

- The Squad section gives two sets of numbers. The detailed list is implemented: **4** starting squadmates,
  **5** unlockable, roster **9**, **8** deployed (Red 4 + Blue 4).
- The Diamond's worth was left blank; it is set to ₡20,000.
- Inventory sizes are rows x columns (6x10 = 6 rows of 10), so every listed weapon fits lying flat.
- Every firearm loads loose rounds of its ammunition type; physical magazines no longer exist. The
  Magazine attachment category changes capacity and reload speed instead.

## Known limits

- **Gun audio has been verified by measurement, not by ear.** Most firearms have two recorded takes; suppressed fire
  is rendered from unsuppressed recordings; several weapons use substitute recordings (`docs/audio/weapon_asset_manifest.md`).
- Explosions, impacts, voices, vehicles, ambience and music are still synthesised.
- World-generation debug layers are written as PNGs; there is no in-game overlay for them.
- Interiors are furnished by room type from a fixed set of pieces; unlit interiors are very dark by day.
- The player's hit capsule does not lean with the body in cover. NPC leans are animated and exposed correctly.
- Dismemberment applies to NPCs; the player's body ragdolls but is not severed.
- Stereo output only.
- Measured in the scripted pass at 1600x900 (uncapped): mean frame 14.1 ms (71 fps), worst 50 ms, over 2033 raid frames
  including a flight over every landmark. Other resolutions were not measured.
- Ambient occlusion runs at half resolution and TAA uses a simple neighbourhood clamp; fast motion can ghost.
- NPCs do not drive. Vehicles collide with the voxel world and with people, not yet with each other.
- **Nobody has played Version 0.6.** It is verified by 483 headless gates and by stills from the scripted pass.
  Feel, motion and sound - the third-person camera, the slide, firing, Sydney's bodies in motion, every sound -
  have not been judged by a person. See `docs/V0.6_IMPLEMENTATION.md`.
- Sydney's recovery steps save a backward or sideways stumble more often than a forward one; ordinary movement is
  still authored animation.
- Only the driver's door of a vehicle is a real door; NPC combat tactics are the 0.5 system.
- Four of the rivalry system's world adaptations (road blocks, paired sentries, marksmen, hunting parties) are
  decided but not yet placed in the world.
- Combat, casualty, cover and audio numbers (`content/combat.json`, `content/weapon_audio.json`) are first-pass values.
