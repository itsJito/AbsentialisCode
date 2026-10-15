# Attribution - third-party audio

Absentialis 0.5 uses the following recordings. Originals are kept unmodified in `tools/audio/originals/`; the files
the game plays are generated from them by `gradlew prepareAudio` (cut, layered, level-matched, resampled). Full
per-file detail: `docs/audio/weapon_asset_manifest.md`.

## The Free Firearm Sound Library
- Authors: Ben Jaszczak, Brian Nelson, Kevin Heras, Matthew Nanney
- Licence: CC0 1.0 Universal (public domain dedication) - https://creativecommons.org/publicdomain/zero/1.0/
- Source: https://opengameart.org/content/the-free-firearm-sound-library
- Used for: all gunshot layers (`assets/audio/weapons/`)

## Gun Reload Sound Effects
- Author: BMacZero (Brian MacIntosh)
- Licence: CC0 1.0 Universal
- Source: https://opengameart.org/content/gun-reload-sound-effects
- Files: clipload1.wav, clipload2.wav, singlebullet1.wav
- Used for: magazine and single-round handling (`assets/audio/handling/mag_*`, `round_*`)

## Gun Reload Sounds
- Author: as credited on the source page (to be copied here before public release; it was not recorded at download)
- Licence: CC0 1.0 Universal, as stated on the source page
- Source: https://opengameart.org/content/gun-reload-sounds
- Files: gunreload1.wav, assaultriflereload1.wav, shotguncock.wav
- Used for: reload, bolt and pump handling (`assets/audio/handling/rifle_reload_*`, `pistol_reload_*`, `pump_*`)

## Footsteps on different surfaces
- Author: congusbongus - https://opengameart.org/content/footsteps-on-different-surfaces
- Licence: Creative Commons Attribution 3.0 - https://creativecommons.org/licenses/by/3.0/
- Derived by that author from (per the `license.txt` in each folder, kept alongside the originals):
  - boots, grass, wood, tile, water: "footstep-concrete.wav", "footstep-grass.wav", "footstep-wood.wav" by swuing
    (freesound.org, CC-BY 3.0)
  - tile: also "Squeaky footstep.wav" by ceberation (freesound.org)
  - water: also "Water Footsteps" by EminYILDIRIM (freesound.org)
  - metal: "boots on aluminum ladder 01" by Eelke (freesound.org, CC-BY 3.0)
  - gravel: "Pack: Gravel Footsteps" by Ali_6868 (freesound.org, CC0)
- Changes made here: mono fold-down, level normalisation, resampling; the asphalt and dirt sets are filtered,
  re-pitched blends of the boots, grass and gravel recordings.
- Used for: footsteps (`assets/audio/footsteps/`)

No audio from commercial games, films or paid sound libraries is included.

## Added in 0.7: voices of the infected and of people

Downloaded 2026-10-11. All three are CC0 1.0 Universal (public domain dedication) as stated on their source pages.
The untouched downloads are in `tools/audio/originals/<pack>/`; which take became which sound is listed in
`docs/audio/v07_voice_manifest.json`. The files are used as recorded: copied to `assets/audio/` under the name the game
plays, and peak-normalised by the loader. They were chosen by measured length and pitch and by their file names, not by
ear (the work was done without audio playback) - if a take is wrong for its sound, drop a better one in under the same name.

### Zombies Sound Pack
- Author: artisticdude - https://opengameart.org/users/artisticdude
- Licence: CC0 1.0 Universal - https://creativecommons.org/publicdomain/zero/1.0/
- Source: https://opengameart.org/content/zombies-sound-pack
- Used for: groan (5 takes), growl (4), shriek (2 of 3), the death of the infected (4)

### 80 CC0 creature SFX
- Author: rubberduck - https://opengameart.org/users/rubberduck
- Licence: CC0 1.0 Universal
- Source: https://opengameart.org/content/80-cc0-creature-sfx
- Used for: shriek, screech, roar (2), the Gladiator's roar, gurgle (2), retch (2), feeding (3), bite, the turning

### grunts of male death and pain
- Author: thebardofblasphemy - https://opengameart.org/users/thebardofblasphemy
- Licence: CC0 1.0 Universal
- Source: https://opengameart.org/content/grunts-male-death-and-pain
- Used for: people hurt (5 takes) and people dying (5), cut from the one recording at its silences

(0.7's note that the chainsaw, the claws, the hammer, casings, hulls and the spray were synthesised no longer holds.)

## The sound pack (0.8, completed in 0.9)

Since 0.9 nothing the game plays is synthesised. `assets/audio/pack/` holds the 298 recordings supplied by the game's
creator and 163 found online: all of those are from Freesound (https://freesound.org), all under Creative Commons 0
(public domain dedication), fetched as the site's HQ preview MP3s, and each is credited by file, title, author and
address in `assets/audio/pack/CREDITS.txt` and in the `credits` list of `src/main/resources/content/soundpack.json`,
which also says which sound each file is used for. They were chosen by name, length and loudness envelope
(`gradlew audioProbe`), not by ear.
