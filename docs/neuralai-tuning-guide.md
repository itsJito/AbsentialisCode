# NeuralAI tuning guide

The engine is `absentialis.neuralai`; its numbers are in `src/main/resources/content/neuralai.json`
(`runtime/NeuralConfig.java` holds the defaults and reads the file). Change a number, run
`ABS_SELFTEST=neural ./gradlew.bat selftest --no-daemon -q`, and read what each of the twelve scenarios prints:
they report distances, times and radii, so a change shows up as a number before it shows up as a failure.

To watch a mind work, type `ai` in the console during a raid. Green cone: sight. Ring: where it believes the
target is and how unsure it is (red seen, orange last seen, yellow approximate, cyan evidence). Blue line: its
cover. White: its destination. Green rings: its squad's search regions. **Magenta is the truth** and is drawn for
you only. Under each head: state / role, action and its utility, the vision meter, the belief, what the body last
reported, and the last line of its decision trace.

## The one rule

Nothing in `decision/` or `group/` may read a target's position. If an agent seems to know too much, the fix is in
what it was told (`perception/`, `ReportBus`), never a fudge downstream. Scenarios 1, 2, 3, 10 and 11 exist to
catch that.

## What to turn for what

| You see | Turn | Where |
|---|---|---|
| Spotted too fast or too slow | the vision meter's fill rates and the near/far arc; `reactionLatency` per faction | `perception`, profiles |
| Hears through walls too well | the 0.55 wall factor and `hearErrorPerMetre`, `hearUnambiguous` | `Perception.hear` |
| Forgets a noise before reaching it / never lets go | `forgetAfter`; the "worth investigating" test | `BeliefStore.age`, `NeuralAi.transition` |
| Knows too well where a hidden target went | `assumedTargetSpeed`, `maxUncertainty` (radius growth) | config |
| Search too short, too long, or two men on one room | `searchPatience` per faction, `reservationTtl`, the grid's weights | profiles, `SearchGrid` |
| Everyone holds / everyone charges | role quotas (HOLD is capped at half of a fit squad of three or more), `MIN_FIT`, the role-bias table, `switchingCost`, `roleSeconds` | `SquadCoordinator` |
| Standing shoulder to shoulder | cover weights (`wProtection`, `wFiringAngle`, `wFlank`, `wTravel`, `wExposure`), the ally-distance penalty, the step-aside distance (1.5 m) | `CoverScorer`, `NeuralAi.act` |
| Jitter between two actions | each action's `commitment()`, the selector's hysteresis and cooldowns | `Actions`, `Selector` |
| Pushes at a wall | the 1.0-1.5 s "blocked" thresholds in the moving actions | `Actions` |
| Too accurate / hopeless | the host's aim error by confidence, range and suppression | `Raid.npcFireAt` |
| Hits friends | it should never: a shot with a friend in the line is held (`Raid.npcCommandFire`, counted as `friendlyHolds`) | host |
| Melee whiffs or lands through a dodge | `MeleeSpec` windup / active window / recovery, reach | `combat/MeleeSpec`, `SydneyAdapter` |
| Sprints too much | `SPRINT_MUL`, `SPRINT_MAX`, `SPRINT_SECONDS`, `SPRINT_RECOVER` | `SydneyAdapter` |
| Difficulty | `NeuralAi.reactionScale` only (easy 1.35, hard 0.7): it shortens recognition and buys no knowledge | `Raid` constructor |
| Cost | `visionHz`, `beliefHz`, `decisionHz`; `Raid.NAV_BUDGET` (paths planned per frame) | config, host |

## What stays the host's

The raid decides everything that actually happens: whether a round leaves the barrel, what it hits, whether a blow
lands, where a body can stand. Doctrine also stays with the host and gets the first word on a new contact
(`Brain.firstContact`): bandits hold people up before shooting, loners back away from trouble they have only heard,
contractors call for support and break contact in order when two thirds of the team is down. The engine's state is
published one way into the fields the rest of the game reads (`Brain.publish`).

## Replays

`Raid.replayPoint()` / `Raid.replayFrom()` put the two process-wide counters (raid serial, next actor id) back, so a
raid built from the same seeds makes the same decisions. Scenario 12 holds that; if it fails after a change, the
change introduced an unordered collection or a clock.
