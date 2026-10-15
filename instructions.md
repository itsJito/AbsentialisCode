# CLAUDE CODE MASTER IMPLEMENTATION PROMPT — GAME VERSION 1.3

> **PRIMARY DIRECTIVE** You are a senior expert level game-engine engineer, technical director, AI systems architect, physics programmer, gameplay designer, and quality-assurance lead. Utilize the most advanced **appropriate, proven, maintainable, and performant** techniques available within this repository and its existing engine to deliver an exceptionally polished, production-quality **Version 1.3**. Do not settle for superficial fixes, stub implementations, visual fakery, or merely writing a plan. **Refine the execution of the ideas; do not alter, replace, dilute, or reinterpret their creative intent.** The detailed requirements below are the product specification. Inspect the actual project before deciding how to implement anything. Make changes in the codebase, verify them, and clearly report what is complete versus unverified.

Note: any testing of mechanics should be done in devmap

Bugs to be fixed:
  - Re-judge interiors by eye; interiors were fixed by rule only and were not re-judged room by room.
  - Doors appear as broken textures when looking through glass doors.
  - When the player has his sights perfectly locked on an NPC that is far away but perfectly within shooting distance, the players bullets phase through the NPC.
  - Pistol reloading animations are bugged
  - Human AI gets stuck turning in circles, especially on stairs and interiors.
  - Crawler animations 
  - Human AI repeatedly shove the crawler when close; nothing happens
  - When a tank throws a human character into the air, they should ragdoll via sydney.
  - Railing object is bugged at X: 134, Y: 30, Z: 401. 
  - Chandeliers are bugged at X: 150, Y: 30 Z: 360.
  - Chair is bugged at X: 259, Y: 23, Z: 162.
  - Toilet is bugged at X: 265, Y: 23, Z: 162, aswell as at X: 317, Y: 23.4, Z:247 and X: 268, Y: 29, Z: 172. (Note: all of these toilets and any toilet sharing that same disgusting model should be replaced by the one used at X: 155, Y: 23, Z: 406.)
  - Dog tails are inverted (currently shows them facing upward across their back)

Things to remove:
  - None, all iteration / migration. Remove dead code if needed.

Changes to make:
  - Human AI tactics and pathfinding work.
  - Replace chicken, crow and spider SFX to real HQ ones online.
  - Dogs (not infected) should be less common, and when they spawn they should have an owner.
  - When blocking, the player should be locked to the players cursor and turn with it, but not when not blocking.
  - Player punches should break glass.
  - Punching an NPC x3 in a full combo should not kill them, and only show the blood mist effect not actual pooling blood. The NPC (human) should be put into an unconscious state and reawaken in a minute and a half, unaware of what just happened but now on alert.

Things to add:
  - There should be bloody mist that varies and is dynamic depending on what part of the character you shoot and how bad.

Things to overhaul:
  - Animation-system overhaul. All animals need realistic and polished walk animations depending on their rig. No awkward shuffling or sliding. Infected walk should be polished. Basically refine what's already there and allow for and improve on contextual dynamic animations.

Things to Polish:
  - Infected walk, window-crawl and 1x1 crawl animations; crawler gait.
  - Ammo box, med cabinet and toolbox art must be redesigned and polished; containers and wooden planks covering windows should be displayed as voxel objects instead of blocks.
  - There should be a small Inventory discard case that only shows when the player is moving or rotating an object so they can discard multiple items at once. Shift clicking equipment and weapons from the stash in deployment should auto-equip
  - Optimize aggressively and ingeniusly

Non-Negotiable most important tasks to do:
  - Start and complete stage 2 of migrating the Astratto Engine from Java to C++ (rendering) making sure it matches the current output 1:1. Start stage 2 of the migration.
  - Human AI tactics and factions need polish. Human AI sometimes runs backwards, they should move their bodies realistically aswell as behave realistically.
  
Notes:
  - When polishing animations, capture 60 seconds of footage and analyze every 15th frame to tweak both ragdoll and canned animation, all handled through sydney.
  - Polish NeuralAI but note it will also be migrated to C++ eventually.



### Special Instructions:
  Update the CHANGELOG.md when finished to succinctly explain every minor and major addition you made to create version 1.3 added follow the format of the previous changelog entries. Dont remove previous entries, add to it.
