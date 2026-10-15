# NeuralAI Engine

**Java integration and implementation specification**  
**Animation backend:** Sydney Engine (procedural animation only)  
**Target:** An existing real-time stealth/action game  
**Java baseline:** Java 21+

> This guide specifies a buildable architecture designed to reproduce the *observable mechanics* of advanced coordinated human-enemy AI: gradual sensing, partial knowledge, tactical searching, cover, flanking, suppression, communication, and reactive melee. It does not claim to recover any proprietary source code or guarantee exact behavioral parity. All numeric values below are calibration defaults, not verified parameters from another engine.

## 1. Ownership and integration boundaries

NeuralAI owns **what an NPC knows, decides and attempts**. The host game owns authoritative entities, collision/physics, weapons, damage, audio, terrain, gameplay events and navigation data. **Sydney Engine owns all pose generation and procedural animation**, including locomotion, foot placement, aim, recoil, turning, stance transitions, vaulting, melee contact and hit reactions. NeuralAI does **not** ship animation clips, keyframe sequences or a motion-matching database; it issues intent and constraints and receives Sydney's feasibility/completion feedback.

```
Existing game: Entity/Physics/Audio/Weapons/Navmesh/Events
         | observations             ^ validated gameplay actions
         v                          |
  NeuralAI: senses -> beliefs -> group coordination -> decisions
         | motion / pose / melee intents       ^ feasibility + action status
         v                                     |
     Sydney Engine: full procedural movement and animation
```

**Authoritative safety rule:** the game validates all hits, damage and collision. An animation pose never directly awards damage; a weapon/melee action must pass host-game rules.

### Dependencies

Use a host-supplied `Vec3` adapter or the minimal record below. No external AI library is required. A navigation mesh or waypoint graph and raycasts are required for credible results.

```java
package neuralai.api;
public record Vec3(double x, double y, double z) {
    public Vec3 add(Vec3 o) { return new Vec3(x+o.x, y+o.y, z+o.z); }
    public Vec3 sub(Vec3 o) { return new Vec3(x-o.x, y-o.y, z-o.z); }
    public Vec3 mul(double n) { return new Vec3(x*n, y*n, z*n); }
    public double dot(Vec3 o) { return x*o.x+y*o.y+z*o.z; }
    public double length() { return Math.sqrt(dot(this)); }
    public double distance(Vec3 o) { return sub(o).length(); }
    public Vec3 normalized() { double d=length(); return d<1e-9?new Vec3(0,0,0):mul(1/d); }
}
```

## 2. Minimal contracts with the existing game

These interfaces are the **integration seam**. Implement them against the existing game's API; never give NPC logic direct access to omniscient player transforms except inside sensory queries.

```java
package neuralai.api;
import java.util.*;

public interface GameWorld {
    double simulationTimeSeconds();
    Vec3 actorPosition(long id);
    Vec3 actorForward(long id);
    boolean isAlive(long id);
    boolean rayClear(Vec3 from, Vec3 to); // physics occlusion
    List<Long> potentialTargetsNear(Vec3 center, double radius); // sensor-only candidate set
    List<SoundEvent> soundEventsSince(long sequence);
    List<Evidence> evidenceNear(Vec3 center, double radius);
    NavPath path(Vec3 from, Vec3 to);
    List<TacticalPoint> tacticalPointsNear(Vec3 origin, double radius);
    void issueWeaponCommand(long actor, WeaponCommand command);
}
public record NavPath(List<Vec3> corners, double length, boolean reachable) {}
public record TacticalPoint(long id, Vec3 position, Vec3 peekPosition, Vec3 normal) {}
public record SoundEvent(long sequence, Vec3 position, double radius,
                         double intensity, String kind, long sourceId) {}
public record Evidence(Vec3 position, Vec3 sourceDirection,
                       double confidence, String kind) {}
public record WeaponCommand(Vec3 aim, boolean trigger, boolean reload) {}
```

Use a **monotonic sequence** for events rather than consuming a sound queue independently per NPC: each NPC has its own cursor and hears only spatially relevant events. For scalability, the host should index events and actors spatially.

### Sydney Engine bridge

Sydney must take semantic requests, not canned animation names. The returned `ActionStatus` allows AI to commit to a task until Sydney says it completed, failed, was blocked or was interrupted.

```java
package neuralai.api;

public interface SydneyBridge {
    enum Stance { STAND, CROUCH, PRONE }
    enum Locomotion { STOP, WALK, RUN, SPRINT }
    enum ActionStatus { RUNNING, COMPLETED, BLOCKED, FAILED, INTERRUPTED }

    record MotionIntent(Vec3 destination, Vec3 facingTarget,
                        Stance stance, Locomotion gait,
                        boolean aimWhileMoving) {}
    record AimIntent(Vec3 worldTarget, double steadiness) {}
    record MeleeIntent(long targetId, String attackType,
                       double reachMeters, double commitmentSeconds) {}
    record ReactionIntent(Vec3 impulse, String reactionType,
                          double severity) {}

    void setMotion(long actorId, MotionIntent intent);
    void setAim(long actorId, AimIntent intent);
    void requestMelee(long actorId, long actionId, MeleeIntent intent);
    void requestReaction(long actorId, long actionId, ReactionIntent intent);
    ActionStatus status(long actorId, long actionId);
    boolean canReachMelee(long attackerId, long targetId, double reachMeters);
    void cancelAction(long actorId, long actionId);
}
```

**Sydney procedural requirements:** produce physically coherent locomotion from destination/velocity; align feet to contact surfaces; maintain balance and weight shifts; orient spine/head/weapon toward look/aim targets; handle occlusion-aware crouch/prone postures; procedurally create attack windup/contact/recovery; support hit reaction and dodge interruption; return action milestones or status. For melee, Sydney supplies the *contact window*, but the host game's combat subsystem alone decides whether a valid hit occurs. If Sydney lacks a requested capability, return `FAILED` and let the AI select a fallback action.

**Coordinate contract:** settle axes, units (meters), handedness, world origin, update order, and thread affinity before connecting either engine. If Sydney outputs root motion, the game's character controller must reconcile it with collision and authoritative final position.

## 3. Fixed-step scheduling and event processing

Run cognition on a fixed step; Sydney and the renderer can update every visual frame. Avoid solving all navigation and perception for all actors each frame.

| System | Suggested default | Immediate event overrides |
|---|---:|---|
| Sydney procedural pose | Render/physics frame | Animation/control tick |
| Nearby vision | 10–20 Hz | Sudden visible threat |
| Belief aging | 10 Hz | Fresh sighting/report |
| Tactical decisions | 5 Hz | Grenade, melee threat, lost path |
| Group roles | 2 Hz | Actor down, sighting, role expiry |
| Expensive search/cover refresh | 1–3 Hz | Region invalidated |

Use a deterministic seed per encounter and record the inputs. Never scale action cooldowns by render framerate.

```java
public final class FixedStepper {
    private final double interval;
    private double accumulator;
    public FixedStepper(double ticksPerSecond) { interval=1.0/ticksPerSecond; }
    public void update(double frameSeconds, Runnable tick) {
        accumulator += Math.min(frameSeconds, 0.25);
        int steps=0;
        while (accumulator >= interval && steps++ < 5) {
            tick.run(); accumulator-=interval;
        }
    }
}
```

Use **snapshot -> perception -> memory -> communication -> coordination -> decisions -> commands -> host/Sydney**. If these phases run on different threads, apply results only against the same simulation snapshot or perform a version check. No race should let a search assignment overwrite an emergency dodge.

## 4. Perception: visual, hearing, evidence

### Visual detection

1. Broad-phase candidates by radius.
2. Use a **near wide / far narrow** field of view: at close distance allow large peripheral angles; at farther distance narrow the allowed half-angle.
3. Raycast from eyes to sampled target points (e.g., torso and head). Include stance, vegetation, darkness, movement, body exposure and difficulty factors as multipliers between 0 and 1.
4. Accumulate detection toward confirmation when visible; decay otherwise. Avoid instantly promoting a glimpse to exact target knowledge.
5. Maintain separate flags for `suspicious`, `probable`, and `visuallyConfirmed` with hysteresis.

```java
public final class VisionMeter {
    private double progress;
    public double tick(double dt, double exposure01, double alertMultiplier) {
        double gainPerSec = 0.8 * Math.max(0, exposure01) * alertMultiplier;
        double decayPerSec = exposure01 > 0.01 ? 0 : 0.22;
        progress=Math.max(0,Math.min(1,progress+dt*(gainPerSec-decayPerSec)));
        return progress;
    }
    public boolean confirmed() { return progress >= 0.999; }
}
```

**Initial test thresholds**: look-at stimulus at `0.2`, investigate at `0.5`, confirmed at `1.0`; apply separate lower transition thresholds for de-escalation. Do not treat values as fixed facts about any other game.

### Logical hearing

On game events emit `SoundEvent`. Evaluate distance attenuation, obstruction and actor hearing profile. Estimate bearing and a spatial uncertainty radius; **do not grant the emitter's precise position unless the sound is unambiguous**. A gunshot can indicate a threat direction without identifying the shooter's exact hiding place.

### Evidence

Create persistent evidence records for a fallen ally, recent impacts, forced doors or suspicious environmental changes. Evidence contributes **inference**, not perfect knowledge. Body discovery can elevate squad alertness; impact marks can yield a direction estimate. De-duplicate previously processed evidence by ID or observation timestamp.

## 5. Belief memory and uncertainty

Each NPC must have its own beliefs; squad-level knowledge is a *separate* communicated store. Use immutable snapshots for planners.

```java
package neuralai.cognition;
import neuralai.api.Vec3;
public enum ConfidenceClass { NONE, EVIDENCE, APPROXIMATE, LAST_SEEN, VISIBLE }
public record TargetBelief(long targetId, ConfidenceClass kind,
        Vec3 estimatedPosition, Vec3 direction,
        double radiusMeters, double confidence01,
        double lastEvidenceTime) {}
```

On direct line of sight, set a high-confidence estimated position. On losing visual contact, keep the last observed position and enlarge uncertainty over time, constrained by reachable graph edges. A simple model:

`radius(t) = initialRadius + assumedTargetSpeed * elapsedSeconds`.

A higher-fidelity model runs a sparse belief distribution over connected navmesh/search cells:

`P(nextCell) += P(oldCell) * transitionProbability(oldCell,nextCell)`.

Then remove or reduce probability for locations actually visible and empty. **Do not erase a checked room forever**: probability can flow back in through accessible entrances. Store evidence source, reliability and expiry time so stale radio reports do not override fresh vision.

**No omniscience invariant:** apart from the perception adapter, no cognition/decision method may query `actorPosition(targetId)` to find a hidden target. Enforce this with API boundaries and tests.

## 6. Search graph and investigation

Partition navigable areas into inspectable regions (rooms, doorways, patches of concealment, cover clusters). Precompute which cells are observable from each candidate inspection point. Record `lastObserved`, `lastInspected`, target belief probability and reservation owner.

**Search progression:**

`REACT -> APPROACH_LAST_KNOWN -> INSPECT -> EXPAND -> SWEEP -> REACQUIRE or DEESCALATE`.

Score candidate search region `r` as:

`score(r) = 3*likelihood + 2*concealment + 2*expectedInformationGain - pathCost - 3*reservationPenalty`.

Normalize inputs to comparable ranges. The coordinator reserves destinations with TTLs and releases reservations on death, failed path, new visual contact, or timeout. Reward inspection points that expose several previously unseen cells. Searchers should avoid duplicate destinations and avoid magically pivoting toward a moved target unless new evidence exists.

```java
public record SearchReservation(long regionId, long actorId, double expiresAt) {}
public interface SearchPlanner {
    long bestRegionFor(long actorId, TargetBelief belief, double now);
    boolean reserve(long regionId, long actorId, double until);
    void releaseActor(long actorId);
    void markVisibleEmpty(long actorId, long regionId, double now);
}
```

**Scenario acceptance:** a target vanishes behind one of three reachable structures; guards first inspect last-seen cover, split across plausible nearby structures, and do not track the unseen target across the map.

## 7. Tactical positions, cover and flanking

Compute cover from candidate points. For each point evaluate: physical protection (multiple vertical raycasts), valid left/right peeking, angle to latest estimated target region, approach exposure, path cost and reservations. Never score against hidden actual target coordinates.

`coverScore = 3*protection + 2*firingAngle + 1.5*flankAdvantage - travelCost - 2.5*exposure`.

Flank planning: establish a combat direction using the **believed** engagement axis, then select reachable side angles. Reject routes that cross the expected firing lane when safer alternatives exist. Reserve flank destinations; abandon the flank when knowledge becomes too stale or approach risk is excessive. Maintain a small number of active flankers so every NPC does not choose the same attack.

Provide fallback if no valid cover exists: remain behind available geometry, retreat to a defensive point, or hold and suppress depending on role.

## 8. Squad coordinator and communication

AI agents independently handle immediate threats, but the coordinator allocates *roles* and spatial resources. Roles: `HOLD`, `SUPPRESS`, `ADVANCE`, `FLANK_LEFT`, `FLANK_RIGHT`, `INVESTIGATE`, `SEARCH`, `REPOSITION`, `RETREAT`.

For actor `a` and role `r`, maximize:

`utility(a,r) = suitability + positioning + equipment + knowledge - risk - switchingCost`.

Apply quotas (e.g., one flanker per side and one or two suppressors), actor eligibility and role expiry. A greedy maximum-score assignment works for small groups; a maximum-weight bipartite matcher improves globally optimal allocation. **Emergency dodge, survival and collision avoidance override tactical role commitments.**

Information transfer should be an explicit event with channel (`SHOUT`, `RADIO`, `VISUAL_SIGNAL`), range, delay, confidence and source. The listener receives the reported estimate and source reliability, *not* live target coordinates. Dialogue may narrate a communication but is not a substitute for knowledge propagation.

## 9. Hierarchical behavior and utility selection

Top-level cognitive states: `PATROL`, `SUSPICIOUS`, `INVESTIGATE`, `SEARCH`, `COMBAT`, `WITHDRAW`, `INCAPACITATED`, `DEAD`. Each state exposes a set of candidate actions. A utility selector scores only actions whose preconditions hold.

```java
public interface AiAction {
    String name();
    boolean allowed(AiContext context);
    double utility(AiContext context);
    void begin(AiContext context);
    void tick(AiContext context, double dt);
    boolean finished(AiContext context);
    void cancel(AiContext context);
}

public final class Selector {
    public AiAction choose(AiContext c, java.util.List<AiAction> actions) {
        AiAction best=null; double score=Double.NEGATIVE_INFINITY;
        for (var a: actions) {
            if (!a.allowed(c)) continue;
            double u=a.utility(c);
            if (u>score) { score=u; best=a; }
        }
        return best;
    }
}
```

The host defines `AiContext` as an immutable read-only snapshot of the NPC belief, current role, relevant navigation scores, weapon state, threat flags and Sydney action status. **Production requirement:** enforce minimum commitment durations, action-specific cooldowns, score-change hysteresis and emergency interrupts, otherwise agents jitter between decisions.

## 10. Ranged combat

Aim at a *visible target* or into a *last-known region* as explicitly designated speculative suppression. Only actual visibility enables tracking exact movement. Role determines burst patterns, peek timing and movement pressure. The host-game weapon system implements raycasts, bullet travel, hit registration, damage, ammunition and recoil. Sydney generates procedural aiming, weapon stabilization, recoil, stance adjustment, body rotation and cover-peek poses from `AimIntent` and motion constraints.

**Fire safety:** prevent shots through friendly bodies using the host collision layers; preserve weapon cooldowns. Avoid turning suppression into a hidden-target aimbot. Decrease fire precision as confidence and direct visibility decline.

## 11. Melee and Sydney procedural animation

The AI selects an attack *intent*, not an animation clip. Sydney performs procedural windup, reach/body alignment, target-relative motion, balance, arm trajectory, contact handling and recovery.

```java
public record MeleeSpec(String id, double minRange, double maxRange,
                        double maxFacingAngleDeg, double windup,
                        double activeWindow, double recovery) {}
```

State progression:

`APPROACH -> WINDUP -> ACTIVE_WINDOW -> RECOVERY -> RELEASE`.

Eligibility checks: distance, angle, reachability, blocked motion path, cooldown, actor physical condition and Sydney's `canReachMelee`. An attack is reserved for its windup/recovery duration so the brain doesn't repeatedly restart it. The host collision/combat resolver must verify contact **during Sydney's reported contact window**; the player can evade by leaving reach or performing a successful defense. Never teleport the defender into a hit.

Design the Sydney adapter to publish events like `CONTACT_WINDOW_OPEN`, `CONTACT_WINDOW_CLOSED`, `FOOTING_LOST`, `DODGE_COMPLETED`, `MOTION_BLOCKED` and `ACTION_COMPLETED`; map them to game-combat checks and AI replanning. If Sydney currently only exposes action status, add a semantic event callback before implementing high-quality melee.

## 12. Dialogue and personality

Give NPCs identifiers, team membership, alertness, aggression, caution, team preference, search patience and reaction latency. Personality influences action scoring within sane tactical limits. Contextual dialogue events include discovery, contact, lost contact, flanking, casualties, reloading and disengagement. Use line cooldowns and proximity checks; game audio provides actual playback. If a bark is inaudible, do not treat it as a successful verbal message unless another channel delivered the report.

## 13. Integration example

The existing game instantiates one `NeuralAiAgent` per enemy, one `SquadCoordinator` per communicative squad, and an encounter `SearchPlanner`. A thin adapter feeds authoritative observations and consumes intent outputs. The outline below shows the lifecycle; subsystems are interfaces and must be concretely implemented.

```java
// Host game's main update (pseudocode-shaped Java orchestration):
fixedStepper.update(frameDt, () -> {
    WorldSnapshot snapshot = adapter.captureSnapshot();
    var senses = perception.sample(snapshot);       // only sensory access
    beliefStore.ingest(senses, snapshot.time());
    reportBus.deliverDue(snapshot.time());
    searchPlanner.refresh(snapshot, beliefStore);
    squadCoordinator.assign(snapshot, beliefStore, searchPlanner);
    var intents = decisionSystem.plan(snapshot, beliefStore, squadCoordinator);
    hostActionValidator.apply(intents.weaponCommands());
    sydneyAdapter.apply(intents.motionAndPoseCommands());
});
// Host owns physics and applies Sydney procedural output on its supported tick.
```

`WorldSnapshot`, `perception`, `beliefStore`, `reportBus`, `squadCoordinator`, and the adapters are implementation placeholders. They are not part of the Java standard library. Avoid copying the orchestration verbatim before implementing those dependencies.

### Integration sequence into an existing game

1. **Audit existing hooks.** Locate entity IDs, player/faction relationships, spatial queries, navmesh, sounds/events, weapon actions, damage callbacks, procedural character controller and Sydney runtime tick.
2. **Set up adapters.** Implement `GameWorld`, `SydneyBridge` and world-coordinate conversion; write contract tests for raycasts and direction handling.
3. **Run one NPC.** Hook the fixed step into the game loop, display vision meter and debug last-known positions.
4. **Add event intelligence.** Emit sound/evidence events and maintain beliefs across lost contact.
5. **Implement search graph.** Bake or construct search regions from level geometry; show probability/inspection overlays.
6. **Add cover and navigation scoring.** Validate alternative routes and reservations in real encounter maps.
7. **Enable squads.** Add communication delays, role allocation and flank/suppression caps.
8. **Connect Sydney locomotion.** Convert each action into `MotionIntent` plus `AimIntent`; verify foot placement, stance and collision correction.
9. **Connect melee/reactions.** Add Sydney semantic contact events and strict host-side hit validation.
10. **Tune and profile.** Compare repeated scenarios under controlled stance, distance, lighting, number of guards and search history.

## 14. Suggested repository layout

```
NeuralAI/
  settings.gradle
  build.gradle
  src/main/java/neuralai/
    api/             # GameWorld, SydneyBridge, Vec3, commands
    runtime/         # Scheduler, event bus, agent lifetime
    perception/      # Vision, sound, evidence
    cognition/       # Beliefs, uncertainty, memory
    spatial/         # Cover, nav, exposure, search graph
    group/           # Reports, roles, reservations
    decision/        # States, utility actions, interrupts
    combat/          # Fire intent, suppression, melee specs
    integration/     # ExistingGameAdapter, SydneyAdapter
    debug/           # Decision traces and overlays
  src/test/java/neuralai/
  config/
    ai-profiles.json
    squad-roles.json
    search-weights.json
  docs/
    tuning-guide.md
```

A minimal Gradle build file:

```groovy
plugins { id 'java-library' }
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
test { useJUnitPlatform() }
```

Add your preferred JUnit dependency in `dependencies { testImplementation(...) }`; choose a version compatible with the host build. Avoid bundling conflicting libraries into the existing game.

## 15. Calibration and acceptance suite

Test repeated encounters with deterministic replay: log actor initial conditions, stimuli, belief changes, reports, role decisions, commands and Sydney action status. Compare the **observable behavior**, not only the number of implemented subsystems.

| Test | Required behavior |
|---|---|
| One-second glimpse then occlusion | Suspicion or memory, not perpetual exact tracking |
| Heard noise behind wall | Direction/region investigation, not automatic sight |
| Fallen ally discovered | Alarm and bounded inference, not perfect attacker location |
| Target runs behind cover | Last-seen search, uncertainty grows over time |
| Two guards search three rooms | Mostly complementary inspection and sensible rechecking |
| Squad sees armed target | Nonidentical assigned roles and feasible positioning |
| Flank path blocked | Reroute or abandon; no clipping through obstacles |
| Melee attack dodged | Sydney contact window resolves miss without teleport |
| Sydney reports blocked motion | AI cancels or replans, no repeating stalled action forever |
| Squad radio disabled | No out-of-range instantaneous knowledge transfer |
| Difficulty increases | Better reaction/tactics without extra omniscience |
| Replayed identical seed | Identical AI decisions given identical world inputs |

### Debug overlays

Display for each agent: FOV shape, LOS rays, detection meter, estimated target point and uncertainty zone, last stimulus, search regions and inspection timestamps, assigned role, tactical path, cover score, current utility scores, action commitment and Sydney action status. Draw *actual* target position in a developer-only overlay, clearly separated from what the AI is allowed to know.

### Common failure modes

- **Psychic AI:** decision code reads target transform directly. Fix with sensor-only belief interfaces.
- **Hivemind AI:** broadcasts bypass hearing/radio limitations. Fix with explicit delivery rules.
- **Identical actions:** group roles and destination reservations are missing.
- **Jitter:** no hysteresis, cooldowns or action commitments.
- **Aim through cover:** target position used instead of believed position and raycast visibility.
- **Brittle search:** checked locations remain forever empty or probabilities spread through walls.
- **Animation mismatch:** AI issues impossible movement not accepted by Sydney; treat failures as planning input.
- **Fake melee hits:** animated proximity grants damage instead of collision/contact-window validation.

## 16. Completion criteria

**Minimum viable integration:** one NPC detects, remembers and searches; navigates and uses cover; can fire only through host-valid actions; and moves exclusively through Sydney procedural intents.

**Advanced integration:** coordinated squads share incomplete information, divide search sectors, assign suppression and flank roles, adapt to casualties, perform procedural Sydney melee and contextual reactions, and recover from blocked actions.

**High-fidelity integration:** scenario-by-scenario tuning of detection curves, uncertainty propagation, search priority, coordination, aggression, attack cadence and Sydney movement response, with instrumented repeatability and tight performance budgets.

The system's central invariant is: **every enemy action must be explainable by its current observations, retained beliefs, received reports, tactical role or immediate physical feedback.** This is the organizing principle that makes advanced human enemy behavior coherent rather than artificially omniscient.
