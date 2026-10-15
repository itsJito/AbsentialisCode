package absentialis.sydney;

import static absentialis.character.Ragdoll.*;
import static absentialis.sydney.PhysicsRig.*;

import absentialis.character.Ragdoll;

/**
 * A behaviour supplies goals - poses to hold, a place to put a foot, a direction to reach in - and nothing else. It
 * never moves a particle and never applies a force: {@link MotorController} executes what it asks for as far as the
 * body's strength goes, and the solver decides what happens.
 *
 * The behaviours here are the body's whole repertoire for staying on its feet and for what follows when it cannot:
 * standing, a recovery step, bracing for the ground, the fall itself, lying, a hand to the wound, getting up, and the
 * slackening of death.
 */
public abstract class BehaviorController {
    /** Writes this behaviour's goals. Channels already claimed by a more urgent behaviour are left alone. */
    public abstract void goals(SydneyCharacter c, Goal g, float dt);

    /** The standing leg: thigh a little forward of straight down, shin a little back - knees soft, inside the joint's range. */
    static final float STANCE_HIP = -0.21f, STANCE_KNEE = 0.21f;

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    static float smooth(float t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    /** A world direction expressed in the trunk's frame (x right, y up, z back). */
    static void local(SensorSystem s, float wx, float wy, float wz, float[] out) {
        out[0] = wx * s.right[0] + wy * s.right[1] + wz * s.right[2];
        out[1] = wx * s.up[0] + wy * s.up[1] + wz * s.up[2];
        out[2] = -(wx * s.fwd[0] + wy * s.fwd[1] + wz * s.fwd[2]);
    }

    // ------------------------------------------------------------------ standing
    /** Upright over the feet: legs straight under the hips, the trunk held vertical, the ankles working, the arms where the animation had them. */
    public static final class Stand extends BehaviorController {
        /** What each leg is being held at: left hip, left knee, right hip, right knee (trunk frame). */
        private final float[][] leg = {{0, -1, STANCE_HIP}, {0, -1, STANCE_KNEE}, {0, -1, STANCE_HIP}, {0, -1, STANCE_KNEE}};
        private static final int[] LEG_JOINT = {J_L_HIP, J_L_KNEE, J_R_HIP, J_R_KNEE};
        private final float[] f = new float[9];
        /** 0 standing easy .. 1 carrying a moving body. */
        public float effort;

        /**
         * A foot has just come down somewhere new. The legs are held as they landed - a leg planted ahead of a moving
         * body is a strut to pivot over, and pulling it straight back under the hips would throw the body forward
         * onto it - and only ease back to an ordinary stance as the body comes to rest.
         */
        public void plant(SydneyCharacter c) {
            Ragdoll r = c.rig.r;
            for (int k = 0; k < 4; k++) {
                int[] J = JOINTS[LEG_JOINT[k]];
                float dx = r.x[J[2]] - r.x[J[1]], dy = r.y[J[2]] - r.y[J[1]], dz = r.z[J[2]] - r.z[J[1]], l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (l < 1e-5f) continue;
                MotorController.frame(c.rig, c.sense, LEG_JOINT[k], f);
                MotorController.toLocal(f, dx / l, dy / l, dz / l, leg[k]);
            }
        }

        public void reset() { for (int k = 0; k < 4; k++) { leg[k][0] = 0; leg[k][1] = -1; leg[k][2] = (k & 1) == 0 ? STANCE_HIP : STANCE_KNEE; } }

        @Override public void goals(SydneyCharacter c, Goal g, float dt) {
            g.stance = true;
            float ease = Math.min(1, dt * (c.sense.speed < 0.45f ? 4f : 1.2f));
            for (int k = 0; k < 4; k++) {
                leg[k][0] += (0 - leg[k][0]) * ease; leg[k][1] += (-1 - leg[k][1]) * ease; leg[k][2] += (((k & 1) == 0 ? STANCE_HIP : STANCE_KNEE) - leg[k][2]) * ease;
            }
            // a planted leg's hip is busy holding the trunk up (the posture torque is the hip's torque): its own pose is
            // held only lightly, or the two would fight over the same joint. A leg in the air is placed firmly.
            // Which of the two it is doing more of depends on what is happening: standing quietly the hips hold the trunk
            // up and the legs are left easy; with the body moving, or on one leg, the legs are locked against the trunk
            // to carry it and the trunk is left to follow.
            SensorSystem s = c.sense;
            float want = s.feet < 2 || s.speed > 0.75f ? 1 : s.speed > 0.35f ? (s.speed - 0.35f) / 0.4f : 0;
            effort = want > effort ? want : Math.max(want, effort - dt * 2.5f);
            float hip = lerp(c.cfg.stanceHip, c.cfg.carryHip, effort);
            g.joint(J_L_HIP, leg[0][0], leg[0][1], leg[0][2], s.footL ? hip : 1); g.joint(J_L_KNEE, leg[1][0], leg[1][1], leg[1][2], 1);
            g.joint(J_R_HIP, leg[2][0], leg[2][1], leg[2][2], s.footR ? hip : 1); g.joint(J_R_KNEE, leg[3][0], leg[3][1], leg[3][2], 1);
            g.joint(J_NECK, 0, 1, 0, 0.8f);
            for (int j = J_L_SHOULDER; j <= J_R_ELBOW; j++) g.joint(j, c.pose[j][0], c.pose[j][1], c.pose[j][2], 0.6f);
            g.posture(0, 1, 0, lerp(c.cfg.standPosture, c.cfg.carryPosture, effort), 0);
            g.balance(1);
        }
    }

    // ------------------------------------------------------------------ the recovery step
    /**
     * One step toward where the body is going. The foot that is behind the fall is lifted and sent to the capture
     * point (a little beyond it, and no further than a leg can reach); the other leg stays planted and the rest of
     * standing carries on round it. Whether the foot gets there in time is not this behaviour's to decide.
     */
    public static final class Step extends BehaviorController {
        public int side = -1;
        public float t, sx, sy, sz, tx, tz;

        /** Chooses the foot and the place. False when there is no foot to step with. */
        public boolean begin(SydneyCharacter c) {
            Ragdoll r = c.rig.r;
            SensorSystem s = c.sense;
            float l = Math.max(1e-4f, s.err), dx = s.errX / l, dz = s.errZ / l;
            float dl = (r.x[LA] - s.supX) * dx + (r.z[LA] - s.supZ) * dz, dr = (r.x[RA] - s.supX) * dx + (r.z[RA] - s.supZ) * dz;
            // the trailing foot steps - unless the fall is sideways across the other leg, when the near foot has to
            side = dl <= dr ? 0 : 1;
            if (side == 0 && !c.rig.legL || side == 1 && !c.rig.legR) side = 1 - side;
            if (side == 0 && !c.rig.legL || side == 1 && !c.rig.legR) return false;
            int a = side == 0 ? LA : RA, other = side == 0 ? RA : LA;
            float lat = (side == 0 ? -1 : 1) * 0.09f * c.scale;
            // the body will stand on both feet, so it is the point between them that has to end up past the capture
            // point: the stepping foot goes as far beyond it as the other foot is behind it
            float lead = c.cfg.stepLead;
            tx = s.cpX + (s.cpX - r.x[other]) * lead + dx * c.cfg.stepOvershoot * c.scale + s.right[0] * lat;
            tz = s.cpZ + (s.cpZ - r.z[other]) * lead + dz * c.cfg.stepOvershoot * c.scale + s.right[2] * lat;
            float ox = tx - r.x[other], oz = tz - r.z[other], ol = (float) Math.hypot(ox, oz), max = c.cfg.stepReach * c.scale;
            if (ol > max) { tx = r.x[other] + ox / ol * max; tz = r.z[other] + oz / ol * max; }
            sx = r.x[a]; sy = r.y[a]; sz = r.z[a];
            t = 0;
            return true;
        }

        public boolean done(SydneyCharacter c) {
            int a = side == 0 ? LA : RA;
            return t >= c.cfg.stepTime || t > c.cfg.stepTime * 0.55f && c.rig.r.grounded[a];
        }

        @Override public void goals(SydneyCharacter c, Goal g, float dt) {
            t += dt;
            float k = Math.min(1, t / c.cfg.stepTime), e = smooth(k);
            int hip = side == 0 ? J_L_HIP : J_R_HIP, knee = side == 0 ? J_L_KNEE : J_R_KNEE, a = side == 0 ? LA : RA;
            // the swing leg goes slack enough to be carried: the foot is what is being steered
            g.joint(hip, 0, -0.85f, -0.5f, 0.15f);
            g.joint(knee, 0, -0.9f, 0.4f, 0.15f);
            float y = sy + (float) Math.sin(k * Math.PI) * c.cfg.stepLift * c.scale;
            g.reach(2 + side, a, PELVIS, lerp(sx, tx, e), y, lerp(sz, tz, e), c.cfg.stepForce * c.cfg.strength, 7);
        }
    }

    // ------------------------------------------------------------------ bracing
    /**
     * The fall is coming and the arms go out to meet what the body is falling toward; the head is carried away from
     * it. How much of this a body does is its own: a person does it without thinking, the infected barely at all.
     */
    public static final class Brace extends BehaviorController {
        private final float[] v = new float[3];

        @Override public void goals(SydneyCharacter c, Goal g, float dt) {
            float b = c.cfg.brace;
            if (b <= 0.01f) return;
            SensorSystem s = c.sense;
            float fx = s.comV[0], fz = s.comV[2], l = (float) Math.hypot(fx, fz);
            if (l < 0.15f) { fx = s.errX; fz = s.errZ; l = (float) Math.hypot(fx, fz); }
            if (l < 1e-3f) { fx = s.fwd[0]; fz = s.fwd[2]; l = Math.max(1e-3f, (float) Math.hypot(fx, fz)); }
            fx /= l; fz /= l;
            for (int side = 0; side < 2; side++) {
                float out = (side == 0 ? -1 : 1) * 0.3f;
                local(s, fx * 0.8f + s.right[0] * out, -0.55f, fz * 0.8f + s.right[2] * out, v);
                g.joint(side == 0 ? J_L_SHOULDER : J_R_SHOULDER, v[0], v[1], v[2], b);
                local(s, fx * 0.9f, -0.35f, fz * 0.9f, v);
                g.joint(side == 0 ? J_L_ELBOW : J_R_ELBOW, v[0], v[1], v[2], b * 0.8f);
            }
            local(s, s.up[0] - fx * 0.6f, s.up[1] + 0.3f, s.up[2] - fz * 0.6f, v);
            g.joint(J_NECK, v[0], v[1], v[2], b);
        }
    }

    // ------------------------------------------------------------------ the fall
    /** Balance is lost: the legs fold rather than lock, taking height off the fall, and the trunk is no longer fought for. */
    public static final class Fall extends BehaviorController {
        @Override public void goals(SydneyCharacter c, Goal g, float dt) {
            float k = c.cfg.fallStrength;
            g.joint(J_L_HIP, 0, -0.75f, -0.65f, k); g.joint(J_L_KNEE, 0, -0.8f, 0.6f, k);
            g.joint(J_R_HIP, 0, -0.8f, -0.55f, k); g.joint(J_R_KNEE, 0, -0.75f, 0.65f, k);
            g.joint(J_NECK, 0, 1, -0.2f, k);
            for (int j = J_L_SHOULDER; j <= J_R_ELBOW; j++) g.joint(j, c.pose[j][0], c.pose[j][1], c.pose[j][2], k * 0.5f);
            g.posture(0, 1, 0, 0, 0);
            g.balance(0);
        }
    }

    // ------------------------------------------------------------------ in the air
    /**
     * 1.1: a body in the air with nothing under it. It is not limp and it is not posed: the legs run on nothing, one
     * after the other, and the arms go round for a balance that is not to be had - faster the longer the fall has gone
     * on. All of it is motor goals like anything else the body does; what the limbs actually do is the solver's.
     */
    public static final class Flail extends BehaviorController {
        public float t;

        @Override public void goals(SydneyCharacter c, Goal g, float dt) {
            t += dt;
            if (c.cfg.style != SydneyConfig.HUMAN && c.cfg.brace < 0.2f) {
                // the dead fall as they walk: without alarm. The arms stay out in front; the legs trail.
                g.joint(J_L_SHOULDER, -0.2f, -0.3f, -0.9f, 0.35f); g.joint(J_R_SHOULDER, 0.2f, -0.3f, -0.9f, 0.35f);
                return;
            }
            float rate = 7f + Math.min(5f, t * 2.2f), ph = t * rate, s1 = (float) Math.sin(ph), c1 = (float) Math.cos(ph), k = 0.55f;
            g.joint(J_L_HIP, 0, -0.82f, -0.55f * s1, k); g.joint(J_L_KNEE, 0, -0.75f, 0.35f + 0.45f * Math.max(0, -c1), k);
            g.joint(J_R_HIP, 0, -0.82f, 0.55f * s1, k); g.joint(J_R_KNEE, 0, -0.75f, 0.35f + 0.45f * Math.max(0, c1), k);
            float a1 = ph * 0.8f, a2 = ph * 0.8f + 2.1f;
            g.joint(J_L_SHOULDER, -0.75f, 0.15f + 0.6f * (float) Math.sin(a1), -0.5f * (float) Math.cos(a1), k);
            g.joint(J_R_SHOULDER, 0.75f, 0.15f + 0.6f * (float) Math.sin(a2), -0.5f * (float) Math.cos(a2), k);
            g.joint(J_L_ELBOW, -0.4f, -0.3f + 0.4f * (float) Math.sin(a1 + 1), -0.7f, k * 0.8f);
            g.joint(J_R_ELBOW, 0.4f, -0.3f + 0.4f * (float) Math.sin(a2 + 1), -0.7f, k * 0.8f);
        }
    }

    // ------------------------------------------------------------------ on the ground
    /** Lying where it came to rest: a little tone in the muscles and no more. */
    public static final class Lie extends BehaviorController {
        public float tone = 1;

        @Override public void goals(SydneyCharacter c, Goal g, float dt) {
            float k = c.cfg.downStrength * tone;
            g.joint(J_L_HIP, 0, -1, -0.1f, k); g.joint(J_L_KNEE, 0, -1, 0.15f, k);
            g.joint(J_R_HIP, 0, -1, -0.1f, k); g.joint(J_R_KNEE, 0, -1, 0.15f, k);
            g.joint(J_NECK, 0, 1, 0, k);
            g.joint(J_L_SHOULDER, -0.35f, -0.9f, 0, k); g.joint(J_L_ELBOW, -0.2f, -0.9f, -0.3f, k);
            g.joint(J_R_SHOULDER, 0.35f, -0.9f, 0, k); g.joint(J_R_ELBOW, 0.2f, -0.9f, -0.3f, k);
            g.posture(0, 1, 0, 0, 0);
            g.balance(0);
        }
    }

    /**
     * A hand to the wound. A person who has been shot and still has a hand to spare puts it where it hurts; the
     * shoulder and elbow of that arm are given up to the reach. The infected do not do this - nothing tells them to.
     */
    public static final class HoldWound extends BehaviorController {
        @Override public void goals(SydneyCharacter c, Goal g, float dt) {
            if (!c.cfg.holdWound || !c.alive || c.wound < 0) return;
            int w = c.wound;
            boolean leftSide = w == LS || w == LE || w == LW || w == LH || w == LK || w == LA;
            boolean woundOnLeftArm = w == LS || w == LE || w == LW, woundOnRightArm = w == RS || w == RE || w == RW;
            // the far hand for an arm, the near hand for anything else
            int arm = woundOnLeftArm ? 1 : woundOnRightArm ? 0 : leftSide ? 0 : 1;
            if (arm == 0 && !c.rig.armL || arm == 1 && !c.rig.armR) arm = 1 - arm;
            if (arm == 0 && (!c.rig.armL || woundOnLeftArm) || arm == 1 && (!c.rig.armR || woundOnRightArm)) return;
            if (!g.reachFree(arm) || !g.free(arm == 0 ? J_L_SHOULDER : J_R_SHOULDER)) return;
            Ragdoll r = c.rig.r;
            g.joint(arm == 0 ? J_L_SHOULDER : J_R_SHOULDER, 0, -1, 0, 0);
            g.joint(arm == 0 ? J_L_ELBOW : J_R_ELBOW, 0, -1, 0, 0);
            g.reach(arm, arm == 0 ? LW : RW, NECK, r.x[w], r.y[w], r.z[w], c.cfg.holdForce * c.cfg.strength, 3);
            c.holding = true;
        }
    }

    // ------------------------------------------------------------------ getting up
    /**
     * Ground to standing. The feet are drawn in under the hips, the legs fold into a crouch and then push: the trunk
     * is driven up toward standing height with what strength the legs have, turned upright against whatever is on
     * the ground, and balanced as it rises. Every part of that is a bounded force with its reaction - if there is no
     * room, or a leg is not there, or something knocks it down again, it does not get up.
     */
    public static final class GetUp extends BehaviorController {
        public float t, h0;
        private final float[] up0 = new float[3];

        public void begin(SydneyCharacter c) {
            t = 0; h0 = c.sense.height;
            System.arraycopy(c.sense.up, 0, up0, 0, 3);
        }

        public float progress(SydneyCharacter c) { return t / c.cfg.getUpTime; }

        /**
         * 1.1, the Crawler: it does not stand. It turns itself belly-down along the line it already lies on, plants its
         * hands wide ahead of its shoulders and its feet wide behind its hips - elbows and knees out and up, like a
         * spider's - and pushes its trunk off the ground to the height it runs at.
         */
        private void scramble(SydneyCharacter c, Goal g, float p) {
            SensorSystem s = c.sense;
            Ragdoll r = c.rig.r;
            float e = smooth(p / 0.7f), sc = c.scale;
            float ax = r.x[NECK] - r.x[PELVIS], az = r.z[NECK] - r.z[PELVIS], al = (float) Math.hypot(ax, az);
            if (al < 0.05f * sc) { ax = up0[0]; az = up0[2]; al = (float) Math.hypot(ax, az); }
            if (al < 1e-3f) { ax = 0; az = -1; al = 1; }
            ax /= al; az /= al;
            float sx = -az, sz = ax;                                    // across the body, to its right when it is belly-down
            g.stance = true;
            // the trunk: level, a little head-high; which side is up is the limbs' business (they are sent to the ground)
            g.posture(ax * 0.95f, 0.30f, az * 0.95f, 1, 1f);
            g.balance(0);
            g.lift(0.4f + 0.6f * e, s.standHeight * c.cfg.crawlHeight);
            float lg = 0.55f + 0.4f * e;
            g.joint(J_L_SHOULDER, -0.80f, -0.35f, 0.50f, lg); g.joint(J_L_ELBOW, -0.25f, -0.55f, 0.80f, lg);
            g.joint(J_R_SHOULDER, 0.80f, -0.35f, 0.50f, lg); g.joint(J_R_ELBOW, 0.25f, -0.55f, 0.80f, lg);
            g.joint(J_L_HIP, -0.75f, -0.45f, 0.50f, lg); g.joint(J_L_KNEE, -0.2f, -0.6f, 0.78f, lg);
            g.joint(J_R_HIP, 0.75f, -0.45f, 0.50f, lg); g.joint(J_R_KNEE, 0.2f, -0.6f, 0.78f, lg);
            g.joint(J_NECK, 0, 0.75f, -0.65f, 0.9f);
            float gy = s.groundY + 0.06f * sc, pull = 460f * c.cfg.strength;
            if (c.rig.armL) g.reach(0, LW, NECK, r.x[NECK] + ax * 0.22f * sc - sx * 0.40f * sc, gy, r.z[NECK] + az * 0.22f * sc - sz * 0.40f * sc, pull, 5);
            if (c.rig.armR) g.reach(1, RW, NECK, r.x[NECK] + ax * 0.22f * sc + sx * 0.40f * sc, gy, r.z[NECK] + az * 0.22f * sc + sz * 0.40f * sc, pull, 5);
            if (c.rig.legL) g.reach(2, LA, PELVIS, r.x[PELVIS] - ax * 0.34f * sc - sx * 0.38f * sc, gy, r.z[PELVIS] - az * 0.34f * sc - sz * 0.38f * sc, pull, 5);
            if (c.rig.legR) g.reach(3, RA, PELVIS, r.x[PELVIS] - ax * 0.34f * sc + sx * 0.38f * sc, gy, r.z[PELVIS] - az * 0.34f * sc + sz * 0.38f * sc, pull, 5);
        }

        @Override public void goals(SydneyCharacter c, Goal g, float dt) {
            t += dt;
            SensorSystem s = c.sense;
            Ragdoll r = c.rig.r;
            float p = Math.min(1, progress(c)), e = smooth((p - 0.25f) / 0.75f), tuck = smooth(p / 0.3f);
            if (c.cfg.style == SydneyConfig.CRAWLER) { scramble(c, g, p); return; }
            boolean dead = c.cfg.style == SydneyConfig.INFECTED;
            g.stance = true;
            // crouch, then extend
            float hy = lerp(-0.35f, -1, e), hz = lerp(-0.94f, STANCE_HIP, e), ky = lerp(-0.9f, -1, e), kz = lerp(0.45f, STANCE_KNEE, e), lg = 0.5f + 0.5f * tuck;
            g.joint(J_L_HIP, 0, hy, hz, lg); g.joint(J_L_KNEE, 0, ky, kz, lg);
            g.joint(J_R_HIP, 0, hy, hz, lg); g.joint(J_R_KNEE, 0, ky, kz, lg);
            if (dead) {
                // 1.1, one of the dead: nothing it does on the way up is for balance or for comfort. The head hangs until the
                // trunk is nearly up and then comes up with a jerk; the arms are not used to push - they trail from the
                // shoulders and only at the end swing forward to where it carries them; the trunk is folded at the hips and
                // is hauled upright late, in two heaves and not one smooth rise.
                float late = smooth((p - 0.72f) / 0.22f), heave = smooth((p - 0.34f) / 0.26f) * 0.55f + smooth((p - 0.74f) / 0.2f) * 0.45f;
                g.joint(J_NECK, 0, lerp(0.30f, 1, late), lerp(-0.95f, -0.15f, late), 0.7f);
                float ag = 0.10f + 0.45f * late, az2 = lerp(0.30f, -0.55f, late), ay = lerp(-0.95f, -0.75f, late);
                g.joint(J_L_SHOULDER, -0.12f, ay, az2, ag); g.joint(J_L_ELBOW, -0.05f, -0.95f, lerp(0.1f, -0.35f, late), ag);
                g.joint(J_R_SHOULDER, 0.12f, ay, az2, ag); g.joint(J_R_ELBOW, 0.05f, -0.95f, lerp(0.1f, -0.35f, late), ag);
                // folded forward over the legs (the way the body faces, flat on the ground) until the heaves bring it up
                float fx = s.fwd[0], fz = s.fwd[2], fl = (float) Math.hypot(fx, fz);
                if (fl < 0.2f) { fx = up0[0]; fz = up0[2]; fl = Math.max(1e-3f, (float) Math.hypot(fx, fz)); }
                fx /= fl; fz /= fl;
                float bend = 0.85f * (1 - heave);
                float tx = lerp(up0[0], fx * bend, smooth(p / 0.35f)), ty = lerp(up0[1], 1 - bend * 0.45f, smooth(p / 0.35f)), tz = lerp(up0[2], fz * bend, smooth(p / 0.35f));
                float tl = Math.max(1e-4f, (float) Math.sqrt(tx * tx + ty * ty + tz * tz));
                g.posture(tx / tl, ty / tl, tz / tl, 1, 0.8f);
            } else {
                g.joint(J_NECK, 0, 1, 0, 0.8f);
                float ag = 0.5f;
                g.joint(J_L_SHOULDER, -0.25f, -0.9f, lerp(-0.45f, 0, e), ag); g.joint(J_L_ELBOW, -0.1f, -0.95f, lerp(-0.3f, 0, e), ag);
                g.joint(J_R_SHOULDER, 0.25f, -0.9f, lerp(-0.45f, 0, e), ag); g.joint(J_R_ELBOW, 0.1f, -0.95f, lerp(-0.3f, 0, e), ag);
            }
            float k = smooth(p / 0.6f);
            float ux = lerp(up0[0], 0, k), uy = lerp(up0[1], 1, k), uz = lerp(up0[2], 0, k), ul = Math.max(1e-4f, (float) Math.sqrt(ux * ux + uy * uy + uz * uz));
            g.posture(ux / ul, uy / ul, uz / ul, 1, 0.8f);
            g.balance(0.3f + 0.7f * e);
            g.lift(tuck, lerp(Math.max(h0, s.standHeight * 0.42f), s.standHeight, e));
            // the feet come in under the hips (flat on the horizontal: the trunk may still be lying down)
            float rx = s.right[0], rz = s.right[2], rl = (float) Math.hypot(rx, rz);
            if (rl < 0.2f) { rx = s.fwd[2]; rz = -s.fwd[0]; rl = Math.max(1e-3f, (float) Math.hypot(rx, rz)); }
            rx /= rl; rz /= rl;
            float fy = s.groundY + 0.08f * c.scale, half = 0.11f * c.scale, pull = 520f * c.cfg.strength * (1 - 0.6f * e);
            float bx = lerp(r.x[PELVIS], s.com[0], 0.5f), bz = lerp(r.z[PELVIS], s.com[2], 0.5f);
            if (c.rig.legL) g.reach(2, LA, PELVIS, bx - rx * half, fy, bz - rz * half, pull, 4);
            if (c.rig.legR) g.reach(3, RA, PELVIS, bx + rx * half, fy, bz + rz * half, pull, 4);
        }
    }

    // ------------------------------------------------------------------ death
    /** The muscles let go. What tone there was drains over a moment, and after that the body is the solver's alone. */
    public static final class Limp extends BehaviorController {
        public float t;

        @Override public void goals(SydneyCharacter c, Goal g, float dt) {
            t += dt;
            // 0.7 (fall study): a body does not switch off all at once. The legs go first - the knees fold under it in the first
            // third of the fade - while the trunk and neck keep a little of their tone for the whole of it, so what the pictures
            // show is a collapse from the ground up (knees, hips, then the chest and head following) and not a plank tipping over
            float fade = Math.max(0.05f, c.cfg.deathFade), u = t / fade;
            float legs = Math.max(0, 1 - u * 3f) * 0.35f, trunk = Math.max(0, 1 - u) * 0.35f;
            g.joint(J_L_HIP, 0, -1, 0, legs); g.joint(J_L_KNEE, 0, -1, 0.2f, legs);
            g.joint(J_R_HIP, 0, -1, 0, legs); g.joint(J_R_KNEE, 0, -1, 0.2f, legs);
            g.joint(J_NECK, 0, 1, 0, trunk);
            for (int j = J_L_SHOULDER; j <= J_R_ELBOW; j++) g.joint(j, c.pose[j][0], c.pose[j][1], c.pose[j][2], trunk * 0.6f);
            g.posture(0, 1, 0, trunk * c.cfg.deathPosture, 0);
            g.balance(0);
        }
    }
}
