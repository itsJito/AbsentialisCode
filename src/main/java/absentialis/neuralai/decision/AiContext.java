package absentialis.neuralai.decision;

import absentialis.neuralai.api.GameWorld.SelfState;
import absentialis.neuralai.api.SydneyBridge.ActionStatus;
import absentialis.neuralai.api.Vec3;
import absentialis.neuralai.cognition.TargetBelief;
import absentialis.neuralai.group.Role;

/**
 * What one mind knows at the moment it decides: a read-only snapshot. There is no target in here - only the belief
 * about one - and no handle on the world; an action that wants something done asks its engine. Everything an NPC does
 * can be traced back to the fields of the context it was decided in.
 */
public record AiContext(long id, double now, CogState state, Role role, TargetBelief belief, SelfState self,
                        double alert, double suppression, boolean pinned, boolean inCover, double protection,
                        ActionStatus motionStatus, double blockedFor, Vec3 danger, boolean dangerNear,
                        int squadSize, int squadFit, double squadCaution, boolean squadSuppressing, boolean allyAdvancing,
                        boolean mate, String order, Vec3 orderPoint, Vec3 leader, Vec3 leaderForward, boolean leaderSprinting, boolean leaderCrouched,
                        double wantRange, double sinceSeen, double sinceHit) {

    public Vec3 position() { return self.position(); }
    public double distanceToBelief() { return belief.known() ? self.position().distanceXZ(belief.estimatedPosition()) : Double.MAX_VALUE; }
    public boolean hurt() { return self.health() < 0.35; }
    public boolean empty() { return self.ammo() <= 0.001; }
}
