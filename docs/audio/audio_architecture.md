# Audio architecture - Absentialis 0.5

## Overview

```
game thread                                             audio thread ("audio-mixer", one block = 1024 frames, 23 ms)
-----------                                             ------------------------------------------------------------
Raid.shotSignature()                                    AudioEngine.mixBlock()
  -> Events.ShotFired (player and every NPC)              one-shot voices (UI, voices, foley, footsteps, ambience)
       -> RaidScreen.gunShot()                            + AudioMixer.mix()            <- the gun bus
            -> GunAudio.shot()                                 drain event ring
                 AcousticEnvironment.analyse()  (rays)         SampleLayerEngine.render()  event -> voices
                 ShotEventScheduler.schedule()  (sample)       VoiceManager.mix()          fixed pool
                 take selection + variation                    LateReverb.process()        one shared FDN
                 AudioMixer.post(ShotEvent) ---- ring ---->    compressor -> peak limiter -> soft knee
                                                           -> engine output limiter -> SourceDataLine
```

Firing code never names a sound. It reports that a round left a weapon; `content/weapon_audio.json` decides what
that sounds like, and the world decides how it arrives.

| Class (`absentialis.engine.audio.gun`) | Responsibility |
|---|---|
| `WeaponAudioProfile` | Per-weapon data: recorded set, layer gains, variation bounds, action events. Loads and decodes every sample once, at start-up. |
| `ShotEvent` | Immutable description of one round: sample position, weapon profile, takes chosen, variation, seed, acoustic snapshot. |
| `ShotEventScheduler` | Places rounds on the mixer's sample clock at exactly 60/RPM seconds apart while a burst continues. |
| `SampleLayerEngine` | Turns an event into voices: transient, body, tail, distant, action, early reflections. |
| `VoiceManager` | Fixed pool of 176 voices, per-layer budgets, fade-out stealing, sample-accurate starts. |
| `AcousticEnvironment` | Occlusion, air absorption, ray-traced early reflections, enclosure, and the parameters of the shared reverb. |
| `AudioMixer` | The gun bus: lock-free event ring, shared late reverb, compressor, limiter, diagnostics. |
| `GunAudio` | The game's single entry point; owns the above. |
| `scene.WorldAcoustics` | The voxel world as `AcousticEnvironment.Geometry`: allocation-free DDA rays, material absorption, footstep surface names. |
| `tools.AudioPrep` | Offline asset pipeline (`gradlew prepareAudio`). |

## Asset pipeline

`gradlew prepareAudio` runs `absentialis.tools.AudioPrep` over `tools/audio/sources.json`:

1. Originals are read from `tools/audio/originals/` and never written to.
2. Each weapon's near-distance file is scanned for single shots (peaks above 45 % of the file's peak, at least 1.6 s
   apart). A take is cut from 1 ms before its onset to 2.6 s, or to just before the next shot. A take with a second
   report within 8 dB of its own in the first second is rejected.
3. Each take is split with a zero-phase crossover at 1.1 kHz: `body` = low band, `transient` = high band for the first
   60-100 ms, `tail` = the rest of the high band. The three sum to the take exactly.
4. The mid-distance file gives the `distant` layer, low-passed at 2.2 kHz.
5. Reload recordings are cut into their separate events; footsteps are normalised one-shots, and two surfaces with no
   recording of their own (asphalt, dirt) are derived and flagged as derived.
6. `assets/audio/index.json` records what was found and measured. `python tools/audio/make_manifest.py` turns it into
   `docs/audio/weapon_asset_manifest.md`.

Everything under `assets/audio/weapons`, `handling` and `footsteps` is generated and can be deleted and rebuilt.
Files placed directly in `assets/audio/` still override bank sounds by name, as before.

## One shot

For each `ShotEvent` the layer engine starts, on the event's sample:

- **transient** of the chosen take - no fade-in, never truncated;
- **body** of an independently chosen take (the bands do not overlap, so they cannot cancel);
- **tail** of the transient's take, scaled by `1 - indoor`: outdoors the recorded decay is the decay, under a roof
  the room replaces it;
- **distant** layer, cross-faded in between 35 m and 150 m while the transient thins between 60 m and 260 m;
- **action** events at the weapon's own offsets (bolt, pump, carrier), audible only within a few tens of metres;
- one voice per **early reflection**, 0.32 s of the distant layer, delayed, filtered and panned to its surface.

Nothing that is already sounding is touched. A voice ends when its sample ends; the only early ending is a budget
steal, which is a 6.5 ms fade, and the oldest voice of the same layer is the one that goes.

Variation per shot (seeded; the same seed reproduces the same shot): take selection that never repeats back to back
and never returns A-B-A when three or more takes exist; playback rate within +-1.5 %; level within +-1 dB. Bounds are
per profile.

## Timing

The game reports rounds on frame boundaries. `ShotEventScheduler` ignores when the report arrived except to ask one
question: is this the same burst? If the report comes no later than 1.6 intervals after the previous round's sample
position, the round is placed exactly one interval after it. Otherwise it starts a new burst 1536 samples (35 ms)
ahead of the mixer clock. Voices carry their absolute start sample and begin inside a block, on that sample. There is
no sleep, timer or thread in the path. The gate renders 20-round bursts at 37, 60 and 144 frames a second and requires
zero samples of deviation.

Remote shots are additionally delayed by distance / 343 m/s.

## Environment

`AcousticEnvironment.analyse` runs on the game thread when the round is fired:

- **Direct path**: gain `ref / (ref + d)`; low-pass cutoff `18 kHz / (1 + d / 55 m)`; multiplied by the openness of the
  straight line (`Physics.openness`, the same measure NPC hearing uses) for both level and cutoff; a further small loss
  behind the muzzle.
- **Early reflections**: a fan of up to 14 rays from the muzzle (8 round the horizon, 4 raised, up, down; 10 for nearby
  NPCs, 6 for far ones). Each hit gives a path muzzle -> surface -> listener; its delay is
  `(reflected - direct) / 343` seconds, its gain falls with path length and the surface material's absorption, and it
  is dropped if the surface cannot be heard from the listener. The six strongest are kept.
- **Enclosure**: the share of rays stopped within 14 m, weighted by whether there is a roof within 9 m.
- **Late field**: `listenerRoom` runs five times a second at the listener and sets decay time, wet level, damping and
  pre-delay for the one shared reverb (a four-line feedback delay network with a Householder matrix and two input
  all-passes). The audio thread glides to new values over a quarter of a second.

Without geometry (menus, headless tests) a fixed medium room is used and the event is flagged `fallback`.

## Mixing and headroom

The gun bus sums at -3 dB, then: a compressor (threshold 0.62, ratio 3.2, slow attack so transients pass) that rides
sustained fire; a peak limiter at 0.92 with instant attack and ~60 ms release; and a soft knee above 0.8 as the last guard (the gates require that no sample
reaches it at more than 1.5x full scale). The bus is scaled by the Effects bus level and the death-sequence duck, then joins
the engine mix, which has its own output limiter.

## Budgets and performance

| Layer | Budget | | Layer | Budget |
|---|---|---|---|---|
| transient | 24 | | reflection | 36 |
| body | 28 | | tail | 28 |
| distant | 14 | | mechanical | 12 |

Measured by the gate "four weapons firing for six seconds" (650 + 900 + 700 + 230 RPM in a closed room, 248 rounds,
peak 114 voices): median 0.8-1.3 ms per 23.2 ms block on the development machine; no events dropped; 0 bytes
allocated over 400 blocks of mixing. Decoded sample memory is 27 MB.

The tail of that distribution is not the mixer's and is not gated: across runs the 95th percentile moved between 2 ms
and 7 ms and the single worst block between 2 ms and 46 ms, and blocks with no voices at all stalled the same way
(JIT, collection, other load on the desktop). The output line buffers four blocks (93 ms). Whether a stall of that size
ever reaches the speakers in play has not been measured.

The audio thread reads no files, takes no lock in the gun bus and allocates nothing. The game thread allocates one
`ShotEvent` and one acoustic snapshot per round (not per frame) and casts at most 14 rays plus 15 openness checks.

## Configuration

`src/main/resources/content/weapon_audio.json`

| Key | Meaning |
|---|---|
| `defaults` | Values every weapon inherits. `tailMakeupDb` raises all recorded tails (the library is dry). |
| `weapons.<id>.set` | Recorded set under `assets/audio/weapons/`. |
| `gain`, `transient`, `body`, `tail`, `distant` | Linear layer gains. |
| `rate` | Playback rate for the whole set (used only to separate substitutes from each other). |
| `variation.rate`, `variation.db` | Per-shot bounds. |
| `action` | Named list in `actions`: `{sample, at, gain, rate}` events after the shot. |
| `refDistance`, `maxDistance` | Distance at which level halves; distance beyond which nothing is rendered. |
| `suppressed` | How a suppressor changes the same recordings. |
| `handling` | Reload cue -> handling sample per weapon family. |

Adding a weapon: add its recordings to `tools/audio/sources.json`, run `gradlew prepareAudio`, add a profile, run
`python tools/audio/make_manifest.py` and `ABS_SELFTEST=gunaudio gradlew selftest`.

## Other audio in 0.5

- **Reloads**: `RaidScreen.reloadFoley` fires cues from the reload animation's own normalised clock (the same value
  that drives the pose), so a cue cannot drift from the motion; each cue plays the recorded handling sample for the
  weapon's family where one exists.
- **Footsteps**: `WorldAcoustics.stepSurface` names the surface from the block underfoot (concrete, asphalt, wood,
  metal, dirt, grass, gravel, tile, water); `AudioEngine.step` picks one of 8-10 recorded steps and never the one just
  played; the player's steps are triggered by animated footfalls, NPC steps are positional.
- **Doors**: latch, kick and break sounds are synthesised (`SoundBank`), positional.

## Tests

`ABS_SELFTEST=gunaudio gradlew selftest` (`tools/AudioTests05`, 40 checks; `ABS_AUDIO_TRACE=1` prints every mix block over 5 ms): identity, RPM grid at several frame rates,
burst restart, take variation and determinism, overlap, release envelope, clipping, headroom, reflection delay,
enclosure, reverb following the room, occlusion, distance delay and layer, suppression, performance, zero allocation,
footstep sets, reload cue mapping, bus level.

## Known limitations

- The result has been verified by measurement, not by ear: no listening test has been run in this build.
- Two takes per weapon for most firearms; no recorded suppressed, interior or mechanical-per-weapon layers (see the
  asset manifest).
- Reflections are first-order and specular-free (a ray's hit point is treated as the reflector); there is no
  diffraction round corners - an occluded shot is attenuated and filtered, not bent.
- Stereo only; no HRTF or surround panning.
- One shared late reverb modelled at the listener: a shot in a hall heard from outside rings in the listener's space,
  with only its early reflections and enclosure taken at the muzzle.
- Explosions, impacts and NPC voices still use the 0.4 one-shot path and do not feed the shared reverb.
