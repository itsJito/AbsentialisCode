# Sydney Engine — Physics Core Implementation Instructions

**Scope:** Headless, physics-driven humanoid control **inside the game already under development**. Java 21+. No GUI, editor, SDK, standalone application, animation authoring system, UI, or new game engine. Use the game's current physics world and rendering skeleton. This is an outcome-oriented reconstruction of huuman dynamics

## 0. Runtime constraints

- Execute all character control before the game's **single shared physics-world step**. Never step the physics engine once per character.
- Fixed simulation timestep: start at `1/120 s`, optionally use additional solver substeps. Render independently.
- Physics owns positions/velocities while physically controlled; game logic issues intentions and impulses, not bone transforms.
- If the existing game's physics backend cannot expose per-joint motor torques, joint limits, contact impulses and articulated constraints, extend/replace that backend capability before implementing behaviors.
- Use no allocation-heavy or blocking operations in the per-step control path.

## 1. Construct the physical humanoid

Implement 17–25 rigid bodies: pelvis, abdomen, chest, head/optional neck, left/right upper arm, forearm, hand, thigh, shin and foot; optionally toes. Capsules/convex hulls for collisions, accurate inertia tensors and reasonable segment-mass fractions summing to character mass. Parent/child constraints limit anatomical degrees of freedom; disable collision between directly adjacent segments. Feet require broad contact patches and realistic friction (not spherical point feet). Configure pelvis as articulation root, but **do not artificially pin it**.

For each joint store: parent and child joint frames; constrained axes and angular stops; relative quaternion, relative angular velocity; target orientation and speed; three-axis gains and limits; maximum torque; contact/constraint diagnostics. Use consistent local coordinate frames and normalize quaternions. Build and test passive ragdoll first: no explosions, joint drift, penetration, or unrealistic mass ratios.

## 2. Implement active joint motors

The defining mechanical element is **simulated muscle effort**: continuous, torque-limited feedback that tries to attain desired poses while remaining vulnerable to collisions, gravity and external impulses.

For a single axis:

```java
static double pdTorque(double targetAngle, double angle, double angularVel,
                       double kp, double kd, double limit) {
    double error = Math.atan2(Math.sin(targetAngle - angle),
                              Math.cos(targetAngle - angle));
    return Math.max(-limit, Math.min(limit, kp * error - kd * angularVel));
}
```

For 3D rotation: compute `qError = qTarget * inverse(qCurrent)` **in the joint's control frame**, choose its shortest-axis-angle logarithm, and apply `tau = Kp * log(qError) - Kd * omegaRelative`, with per-axis torque saturation. Respect the physically allowed degrees of freedom, project prohibited torque components away, and optionally add gravity feedforward. The engine applies torque through its joint-motor or articulation actuator API; **never rotate bodies directly** to achieve targets. Define stiffness/torque profiles such as relaxed, upright, bracing, injured and limp; interpolate profiles rather than snapping them.

Tune gains relative to inertia and timestep; choose damping near critical as an initial guess (`kd ≈ 2*sqrt(kp*I_effective)` per axis), then test with contact and impact perturbations. Never treat example gains as universal physical constants.

## 3. Sample physical feedback every tick

From the *previous completed physics state*, calculate:

- Whole-body center of mass `COM = Σ(m_i * x_i) / Σm_i`; its filtered velocity and estimated angular momentum.
- Pelvis/torso/head poses and angular velocities; joint angles, speeds and limit proximity.
- Foot contacts: points, normals, normal impulses, friction/slip, planted vs swing status; current support polygon.
- Contact impulses and relative velocities for hits; reachability and suitable nearby brace/grab surfaces.

Separate raw measurements from filtered control signals. Set contact enter/exit thresholds with hysteresis; reset filters sensibly after teleports, respawns and controlled-mode changes.

## 4. Stand upright using whole-body feedback

Implement balance before any other complex behavior. For stance:

1. Estimate projection of COM and COM velocity relative to the current support polygon.
2. Choose desired pelvis/torso orientation and COM correction; keep the head approximately upright.
3. Resolve desired ankle, hip, knee and spine joint targets while observing support-foot friction and joint limits.
4. Apply torques and reassess after the world steps; do not zero root velocity or lock feet to the world without an appropriate physical contact model.

For a simple initial predictive measure on flat ground, approximate capture point `xCP = xCOM + vCOM * sqrt(h/g)` (horizontal coordinates). It is only an inverted-pendulum heuristic: do not mistake it for a general whole-body stability guarantee. Prefer a torso angular momentum and contact-aware controller as fidelity improves.

## 5. Make reactive stepping work

When predicted recovery cannot be achieved by posture correction inside the present support region, choose a **reachable** step target toward the estimated capture point, limited by leg reach, terrain slope, collision, pelvis velocity and step timing. Transfer support weight to the stance leg, lift the swing foot along a clearance trajectory, drive it using leg IK, physically place it, detect contact, then rebuild the support polygon. Continue stepping as needed. Never teleport a foot or impose impossible planted contacts.

A procedural stumble is repeated failed/partial balance correction plus reactive stepping, while arms counter-rotate and the torso responds to angular momentum. This must emerge from simulation, not from a list of stumble animation clips.

## 6. Implement limb intent and behavior arbitration

Per simulation tick evaluate these concurrent controllers, each contributing desired **limb pose, motor strength and priority**:

| Physical behavior | Required control logic |
|---|---|
| Balance | COM/velocity, torso orientation, contact correction |
| Step/stumble | Feasible foot placement, support transfer, repeated capture attempts |
| Impact response | Perturbations from real collision impulses; transient asymmetric motor responses |
| Brace/protect | Predict impact; drive arms between head/torso and threatening surface |
| Reach/grab | Select valid world target; IK hand motion; breakable physical grasp constraint after contact |
| Fall | When recovery is infeasible, reduce standing gains; preserve head protection and bracing |
| Get-up | Identify resting orientation; reposition limbs for support; controlled stand with actual contacts |

Blend *compatible* targets smoothly, but reserve ownership of constrained limbs where behaviors conflict (e.g. stepping owns swing leg; grabbing owns attached hand). For 3D orientations use hemisphere-correct spherical interpolation or a joint-space manifold formulation, **not** component-wise quaternion averages. Introduce activation hysteresis and minimum dwell times to prevent controller thrashing. A behavior outputs intentions, not precomputed bone world transforms.

## 7. IK should guide motors, not bypass physics

Use analytical two-bone IK for arms/legs, with pole vectors and joint-limit checks. Use constrained Jacobian/damped least-squares IK when simultaneous orientation/position constraints require it. Calculate joint target angles, then feed them to the active motors. If a hand target cannot be reached, use the best feasible pose and let physics dictate the outcome. For grasping, require measured hand contact before creating the attachment; limit break force and prevent indefinite rigid attachments.

## 8. Falling, contacts and recovery

A fall is not a ragdoll toggle alone. Classify loss of support from COM prediction, trunk orientation, failed steps and motor saturation. Transition from standing to bracing/head protection with weakened lower-body posture motors. Keep collision response fully active. When the character settles, determine face-up/face-down/side position and a feasible supporting-hand/knee arrangement. Get-up may use an authored **target posture trajectory**, but joints must still physically drive through constraints and actual contacts. Abort/replan if blocked or hit again. Only switch character/root ownership after synchronizing velocities and transforms.

## 9. Integrate into the existing game loop

```java
// Called once by the existing game's fixed-update scheduler.
void fixedUpdate(double dt) {
    for (SydneyBody actor : simulatedCharacters) {
        PhysicsState state = actor.readSolvedWorldState();
        Feedback feedback = actor.measure(state);
        MotorTargets goals = actor.resolvePhysicalBehaviors(feedback, dt);
        JointTargets joints = actor.solveLimbTargets(goals, state);
        actor.applyBoundedJointTorques(joints, state, dt);
    }
    existingPhysicsWorld.step(dt); // ONLY ONCE for all actors/entities
    for (SydneyBody actor : simulatedCharacters) {
        actor.syncExistingRenderSkeleton();
    }
}
```

The class and method names above identify responsibilities, **not** a requirement to create abstract Java interfaces or a separate framework. Integrate directly into the game's existing components and lifecycle; avoid redundant wrappers or a second physics simulation. The existing AI/combat systems specify desired direction, actions, injuries and world impulses; Sydney generates the physical movement. Retarget physical-rig transforms to the *existing* visible skeleton only after the simulation step.

## 10. Prove each layer before advancing

1. **Passive rig:** stable collisions, ragdoll and external impulses; no joint explosion.
2. **Motors:** hold varied poses against gravity; cap torques; maintain stability under impact.
3. **Standing:** balance on flat terrain, then varied friction/slopes; quantify sway and impulse tolerance.
4. **Stepping:** unanticipated forward/backward/lateral pushes produce genuine compensating steps.
5. **Stumbling/falling:** escalating pushes produce recovery, multiple steps or protective collapse automatically.
6. **Environmental contact:** hands brace on walls; gripping constraints attach/release through physical contact.
7. **Get-up:** recover from supine, prone and lateral rest states without teleporting.
8. **Game integration:** combat impulses, uneven terrain, moving platforms and multiple active actors do not break game simulation.

Instrument joint torques, orientations, COM/capture point, support polygon, contact impulses, active behaviors, constraint errors and physics substep cost. Run a deterministic *input replay* test where backend determinism permits; monitor NaNs, deep interpenetration, frame spikes, motor saturation and repeated fall/recover loops.

**Outcome criterion:** Characters physically resist pushes, take plausible steps, protect themselves, interact with obstacles and recover in ways that vary with actual momentum, contacts, direction, terrain and motor limits. If changing collision impulse or contact position barely changes the reaction, the implementation is still animation-driven rather than Sydney's intended physical control system.
