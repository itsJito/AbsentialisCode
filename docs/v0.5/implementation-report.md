# Absentialis 0.5 - final implementation report

Date: 2026-10-10. Scope: `instructions.md`. Design and audit: `audit-and-design.md`. Change list: `../../CHANGELOG.md`.

## State of the build

- Compiles. **394 of 394** headless quality gates pass (`gradlew selftest`), 81 of them new for 0.5.
- The scripted visual pass (`ABS_AUTOSHOT`) runs start to finish with no exception or mixer fault and writes 48
  screenshots; seven of the new ones were inspected (city overview, east boundary, military base, door, casualty with
  hit marker and killfeed, death camera, operation briefing).
- **Not done by anyone: listening.** The audio work is verified by measurement only.

## Systems implemented

| Brief section | What exists | How it was checked |
|---|---|---|
| 1 World generation | Plan-then-build pipeline; six environments; two landmark compounds per developed district; one faction headquarters each; protected roads; room-graph interiors; validation with repair and rejection; generation report; PNG debug layers | Gates on two seeds (landmarks, headquarters, clean report, entrances, exits, determinism, speed); overview and landmark screenshots |
| 2 Boundary | Trench and glass-like towers removed at cause | `TerrainProbe` gates: 0 trench cells, 0 void columns, 0 seam or edge cliffs, 0 glass in the ring outside the wall; boundary screenshot |
| 3 Tactical AI | Utility-scored intents, squad roles (aggressor, flanker, suppressor, searcher, defender, medic while rescuing), cover selection, lean and pop-up fire, suppression, relocation, withdrawal, chokepoints, rescue | Pre-existing AI gates still pass; rescue and revive gated through the casualty machine. **Cover use, flanking and suppression by NPCs have no dedicated 0.5 gate and were not watched in play** |
| 4 Cover | Cover map from geometry (low/high, lean sides, integrity, occupancy); player contextual cover; cover animation layers | Gate that cover points exist; **third-person cover animation not inspected in a screenshot** |
| 5 Downed / revive | ACTIVE -> DOWNED -> REVIVING -> ACTIVE / DEAD; 20 s bleed-out paused during revive; interruptible; infected and fatal wounds excluded; player crawl and sidearm | 11 gates; screenshot |
| 6 Death | Player body persists as ragdoll (root cause fixed); NPC dismemberment and decapitation; death camera | Gate (body persists); death-camera screenshot; 0.3/0.4 gore gates |
| 7 Health conditions | Four conditions, thresholds and effects in `content/combat.json`; locomotion and aim affected; animation blend | Gates on thresholds and speeds |
| 8 Doors | Door entities: hinge, regions, latch and hinge failure, debris, navigation updates, AI use | 7 gates on a generated world; three screenshots |
| 9 ADS and hit marker | Sight-line alignment per weapon model; white / yellow / red marker from confirmed events with priority | Gates; screenshot shows the red marker |
| 10 Minimap and squad | Relation colours, squad facing icons, squad column on the left | Colours gated; visible in screenshots; **awareness rules not gated** |
| 11 Status module | Portrait, health arc that lengthens with maximum health, loaded and reserve ammunition, inventory status panel, layout helpers, Insertion Point overflow fix | Gauge gated; module and briefing screenshots |
| 12 Killfeed | Left side; killer, weapon, victim; no duplicates; nothing for incapacitation; NPC-on-NPC kills | 5 gates; screenshot |
| 13 Speech bubbles | World-space bubbles from the existing dialogue | **Not gated and not captured in a screenshot** |
| 14 Audio | Recorded footsteps for seven surfaces with variation; reload cues on the animation clock; door sounds | Gates |
| 15 Materials | Material rules per building style, audit in the validator, materials debug layer | Report gate (0 material faults is part of a clean plan only indirectly - see limitations) |
| 16 Version | `absentialis.Version` 0.5, save format 5 with migration, changelog | Gate |
| 17 Gun audio | All eight phases: audit, sourcing, pipeline, the seven named classes, layering, environment, automatic fire, documentation and tests | 40 gates |

## Defects fixed

- Perimeter trench: the outermost city column was never filled (off-by-one in the outside-distance test).
- Glass-like boundary towers: watchtowers were placed three blocks outside the wall.
- Player body vanished on death: the actor was marked dead before the death sequence ran, so no ragdoll was created.
- Floating dead-tree canopies (9259 blocks); canopies blocking road cells.
- Insertion Point text overflow in the hideout.
- Found and fixed during this stage: the audio pipeline's first heuristic chose burst recordings as single-shot
  takes and swapped near and mid perspectives for one pistol; it now uses the library's own master sheet. The
  terrain probe counted gateways in the wall and ground under masts as trench cells.

## Tests performed

- `gradlew selftest`: 394 pass. New filters: `v05`, `world05`, `casualty`, `feed`, `door`, `hud05`, `gunaudio`.
- Gun audio acceptance tests from the brief: identity (16 weapons, 16 distinct recorded sets); no sample-gun effect
  (40 rounds, no take repeated, no A-B-A); release (20-round burst rings on, largest 20 ms step down 4.6 dB); RPM (0
  samples of deviation at 37, 60 and 144 fps for 650, 700, 900 and 70 RPM); environment (reflection delay within 1 ms
  of (reflected - direct) / 343, room against field, occlusion, 200 m travel delay); performance (below).
- Scripted visual pass, 48 screenshots.

One gate needed three revisions and the reason should be known: the mixer-cost gate first limited the single worst
block. Under the full suite that figure moved between 2 ms and 46 ms from run to run, and tracing showed blocks with
no voices stalling the same way, so it measures the JVM and the desktop, not the mixer. The gate now limits the median
and reports the rest.

## Performance observations

- World generation: 2.1-4.4 s (plan 0.1 s, build 1.1 s, validation 3.2 s in the slowest run).
- Scripted pass at 1600x900, uncapped: mean frame 16.7 ms (60 fps), worst 50 ms, 2033 raid frames.
- Gun bus, four weapons in a room (248 rounds in 6 s, peak 114 of 176 voices): median 0.8-1.3 ms per 23.2 ms block;
  no events dropped; no allocation in the mix loop. Voices stolen in that scenario: 1673, all faded.
- Decoded audio: 27 MB. Shipped generated audio: 16 MB. Original recordings kept in the project: 330 MB.

## Known limitations

- Gun audio has not been heard. Balance values in `content/weapon_audio.json` are first-pass.
- Most firearms have two recorded single shots. Suppressed fire is rendered from unsuppressed recordings. Magnum
  handguns, semi-automatic shotguns and three other weapons use substitute recordings. Mechanical sounds are generic,
  not per firearm. Details: `docs/audio/weapon_asset_manifest.md`.
- The near recordings clip at the muzzle blast; this is in the sources and is documented, not repaired.
- Explosions, impacts, voices, vehicles, ambience and music are still synthesised and bypass the new acoustics.
- Reflections are first order; no diffraction; stereo only; one reverb at the listener.
- Buildings have no balconies. The player's hit capsule does not lean. The player's body is not dismembered.
- Existing saves load, but the city is regenerated and per-world state (discovered places, zone log, agents) is reset
  by the format-5 migration.

## Requirements not fully completed

1. **In-game debug visualisation of generation and cover data.** Layers are written as PNGs (`ABS_GENDUMP`); only the
   existing `ai debug` and `noise debug` overlays are in the game.
2. **Behaviour tree / hierarchical state machine.** The tactical layer is utility selection over the existing brain
   states and squad roles; no formal BT or HSM structure was introduced.
3. **Verification of NPC tactics.** Cover use, leaning, suppression and flanking are implemented but were not observed
   or gated in 0.5.
4. **Speech bubbles and minimap awareness rules** are implemented without a gate or screenshot.
5. **Audio by ear**, and an attribution detail: the author credit for "Gun Reload Sounds" (OpenGameArt, CC0 as stated
   on its page) was not recorded at download and must be copied from the page before public release.
6. **"AAA" polish** is not a claim this report makes. The systems are in place and measured; several are first-pass.
