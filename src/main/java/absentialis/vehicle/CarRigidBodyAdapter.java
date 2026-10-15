package absentialis.vehicle;

import absentialis.world.World;

/**
 * The car's one dynamic rigid body, and its connection to the voxel world.
 *
 * State is the usual: centre-of-mass position, orientation (quaternion), linear and angular velocity, mass and a
 * diagonal inertia tensor in the body frame. Everything that moves the car does so through {@link #applyImpulse}:
 * delta velocity = impulse / mass, and an impulse applied away from the centre of mass also turns the body by
 * (contact offset x impulse) through the inverse inertia. The body is integrated exactly once per fixed step.
 *
 * Collision uses simplified geometry, not the visible voxels: the chassis is a compound of boxes, sampled at their
 * corners and along their edges. A sample that ends a step inside solid voxels is pushed out through the face it
 * entered by, and the body receives a normal impulse (with a little restitution) and a friction impulse at that
 * point - so an off-centre hit spins the car and a glancing one scrubs speed. The strongest impact of the step is
 * exposed for the damage model.
 */
public final class CarRigidBodyAdapter {
    public float px, py, pz;                 // centre of mass, world
    public float qw = 1, qx, qy, qz;         // orientation, body -> world
    public float vx, vy, vz;                 // linear velocity, world
    public float wx, wy, wz;                 // angular velocity, world
    public float mass = 1000, invMass = 0.001f;
    private float invIx, invIy, invIz;       // inverse principal inertia, body frame (x pitch, y yaw, z roll)
    /** Rotation matrix body -> world, row-major. */
    public final float[] r = {1, 0, 0, 0, 1, 0, 0, 0, 1};

    // ---- collision geometry and results
    private float[] local = new float[0];    // sample points in the body frame (relative to the centre of mass)
    private float[] prev = new float[0];     // their world positions at the end of the previous step
    private boolean prevValid;
    /** Strongest impact of the last step: speed change it caused (m/s), where, and along which normal. */
    public float impact, impactX, impactY, impactZ, impactNX, impactNY, impactNZ;
    /** Local position of the sample that took the strongest impact (damage zones are derived from it). */
    public float impactLX, impactLY, impactLZ;
    public int contacts;
    public interface BlockHit { void hit(int x, int y, int z, float speed); }

    public void setMass(float m, float yawInertia, float pitchInertia, float rollInertia) {
        mass = m; invMass = 1f / m;
        invIx = 1f / pitchInertia; invIy = 1f / yawInertia; invIz = 1f / rollInertia;
    }

    /** Samples the compound chassis shape: box corners, edge midpoints and face centres of the larger faces. */
    public void setShape(java.util.List<float[]> boxes, float comX, float comY, float comZ) {
        java.util.List<Float> pts = new java.util.ArrayList<>();
        for (float[] b : boxes) {
            int nz = Math.max(2, Math.round(b[5] * 2 / 0.9f)), nx = Math.max(2, Math.round(b[3] * 2 / 0.9f));
            for (int iy = 0; iy <= 1; iy++) for (int ix = 0; ix <= nx; ix++) for (int iz = 0; iz <= nz; iz++) {
                // the shell only: interior points would never touch anything first
                if (ix != 0 && ix != nx && iz != 0 && iz != nz) continue;
                pts.add(b[0] - b[3] + 2 * b[3] * ix / nx - comX);
                pts.add(b[1] - b[4] + 2 * b[4] * iy - comY);
                pts.add(b[2] - b[5] + 2 * b[5] * iz / nz - comZ);
            }
            // roof and floor centres, so a car on its roof rests on more than its rim
            for (int iy = 0; iy <= 1; iy++) { pts.add(b[0] - comX); pts.add(b[1] - b[4] + 2 * b[4] * iy - comY); pts.add(b[2] - comZ); }
        }
        local = new float[pts.size()];
        for (int i = 0; i < local.length; i++) local[i] = pts.get(i);
        prev = new float[local.length];
        prevValid = false;
    }

    public int samples() { return local.length / 3; }

    public void updateRotation() {
        float l = (float) Math.sqrt(qw * qw + qx * qx + qy * qy + qz * qz);
        qw /= l; qx /= l; qy /= l; qz /= l;
        float xx = qx * qx, yy = qy * qy, zz = qz * qz, xy = qx * qy, xz = qx * qz, yz = qy * qz, wxq = qw * qx, wyq = qw * qy, wzq = qw * qz;
        r[0] = 1 - 2 * (yy + zz); r[1] = 2 * (xy - wzq); r[2] = 2 * (xz + wyq);
        r[3] = 2 * (xy + wzq); r[4] = 1 - 2 * (xx + zz); r[5] = 2 * (yz - wxq);
        r[6] = 2 * (xz - wyq); r[7] = 2 * (yz + wxq); r[8] = 1 - 2 * (xx + yy);
    }

    /** Orientation from a heading (radians about +y; 0 faces +z). */
    public void setYaw(float yaw) { qw = (float) Math.cos(yaw / 2); qx = 0; qy = (float) Math.sin(yaw / 2); qz = 0; updateRotation(); }
    public float yaw() { return (float) Math.atan2(r[2], r[8]); }

    // world axes of the body
    public float rightX() { return r[0]; } public float rightY() { return r[3]; } public float rightZ() { return r[6]; }
    public float upX() { return r[1]; } public float upY() { return r[4]; } public float upZ() { return r[7]; }
    public float fwdX() { return r[2]; } public float fwdY() { return r[5]; } public float fwdZ() { return r[8]; }

    /** Body-frame point (relative to the centre of mass) to world. */
    public void toWorld(float lx, float ly, float lz, float[] out) {
        out[0] = px + r[0] * lx + r[1] * ly + r[2] * lz;
        out[1] = py + r[3] * lx + r[4] * ly + r[5] * lz;
        out[2] = pz + r[6] * lx + r[7] * ly + r[8] * lz;
    }

    public void toLocal(float x, float y, float z, float[] out) {
        float dx = x - px, dy = y - py, dz = z - pz;
        out[0] = r[0] * dx + r[3] * dy + r[6] * dz;
        out[1] = r[1] * dx + r[4] * dy + r[7] * dz;
        out[2] = r[2] * dx + r[5] * dy + r[8] * dz;
    }

    /** Velocity of the body at a world point: linear velocity + angular velocity x offset. */
    public void velocityAt(float x, float y, float z, float[] out) {
        float rx = x - px, ry = y - py, rz = z - pz;
        out[0] = vx + wy * rz - wz * ry;
        out[1] = vy + wz * rx - wx * rz;
        out[2] = vz + wx * ry - wy * rx;
    }

    /** Applies inverse world inertia to a torque-like vector: R * diag(invI) * R^T * t. */
    private void invInertia(float tx, float ty, float tz, float[] out) {
        float lx = r[0] * tx + r[3] * ty + r[6] * tz, ly = r[1] * tx + r[4] * ty + r[7] * tz, lz = r[2] * tx + r[5] * ty + r[8] * tz;
        lx *= invIx; ly *= invIy; lz *= invIz;
        out[0] = r[0] * lx + r[1] * ly + r[2] * lz;
        out[1] = r[3] * lx + r[4] * ly + r[5] * lz;
        out[2] = r[6] * lx + r[7] * ly + r[8] * lz;
    }

    private final float[] t3 = new float[3];

    /** Impulse (N s) at a world point. */
    public void applyImpulse(float x, float y, float z, float jx, float jy, float jz) {
        vx += jx * invMass; vy += jy * invMass; vz += jz * invMass;
        float rx = x - px, ry = y - py, rz = z - pz;
        invInertia(ry * jz - rz * jy, rz * jx - rx * jz, rx * jy - ry * jx, t3);
        wx += t3[0]; wy += t3[1]; wz += t3[2];
    }

    public void applyCentralImpulse(float jx, float jy, float jz) { vx += jx * invMass; vy += jy * invMass; vz += jz * invMass; }

    /**
     * Effective inverse mass of the body at a world point along a unit direction:
     * 1/m + n . ((I^-1 (r x n)) x r). An impulse j along n changes the point's speed along n by j times this.
     */
    public float invMassAt(float x, float y, float z, float nx, float ny, float nz) {
        float rx = x - px, ry = y - py, rz = z - pz;
        float cx = ry * nz - rz * ny, cy = rz * nx - rx * nz, cz = rx * ny - ry * nx;
        invInertia(cx, cy, cz, t3);
        float ax = t3[1] * rz - t3[2] * ry, ay = t3[2] * rx - t3[0] * rz, az = t3[0] * ry - t3[1] * rx;
        return invMass + ax * nx + ay * ny + az * nz;
    }

    /** One integration step: positions from velocities. Called once per fixed update, after every impulse. */
    public void integrate(float dt) {
        px += vx * dt; py += vy * dt; pz += vz * dt;
        float hx = wx * dt * 0.5f, hy = wy * dt * 0.5f, hz = wz * dt * 0.5f;
        float nw = qw - hx * qx - hy * qy - hz * qz;
        float nx = qx + hx * qw + hy * qz - hz * qy;
        float ny = qy + hy * qw + hz * qx - hx * qz;
        float nz = qz + hz * qw + hx * qy - hy * qx;
        qw = nw; qx = nx; qy = ny; qz = nz;
        updateRotation();
    }

    private final float[] wp = new float[3], vel = new float[3];

    /**
     * Resolves the chassis against solid voxels after integration. restitution and friction are those of a steel
     * body on masonry. Blocks struck at speed are reported to the callback (fences and glass give way).
     */
    public void collide(World w, float restitution, float friction, BlockHit onHit) {
        impact = 0;
        contacts = 0;
        int n = local.length / 3;
        float pushX = 0, pushY = 0, pushZ = 0;
        int pushes = 0;
        for (int i = 0; i < n; i++) {
            toWorld(local[i * 3], local[i * 3 + 1], local[i * 3 + 2], wp);
            float x = wp[0], y = wp[1], z = wp[2];
            int cx = (int) Math.floor(x), cy = (int) Math.floor(y), cz = (int) Math.floor(z);
            if (!w.solid(cx, cy, cz)) { prev[i * 3] = x; prev[i * 3 + 1] = y; prev[i * 3 + 2] = z; continue; }
            // ---- which face did this sample come in through?
            float nx = 0, ny = 0, nz = 0, depth = 0;
            boolean found = false;
            if (prevValid) {
                float ox = prev[i * 3], oy = prev[i * 3 + 1], oz = prev[i * 3 + 2];
                float best = Float.MAX_VALUE;
                // the face whose plane the motion crossed last on the way in, provided the cell beyond it is open
                if (ox <= cx && !w.solid(cx - 1, cy, cz)) { float d = x - cx; if (d < best) { best = d; nx = -1; ny = 0; nz = 0; found = true; } }
                if (ox >= cx + 1 && !w.solid(cx + 1, cy, cz)) { float d = cx + 1 - x; if (d < best) { best = d; nx = 1; ny = 0; nz = 0; found = true; } }
                if (oy <= cy && !w.solid(cx, cy - 1, cz)) { float d = y - cy; if (d < best) { best = d; nx = 0; ny = -1; nz = 0; found = true; } }
                if (oy >= cy + 1 && !w.solid(cx, cy + 1, cz)) { float d = cy + 1 - y; if (d < best) { best = d; nx = 0; ny = 1; nz = 0; found = true; } }
                if (oz <= cz && !w.solid(cx, cy, cz - 1)) { float d = z - cz; if (d < best) { best = d; nx = 0; ny = 0; nz = -1; found = true; } }
                if (oz >= cz + 1 && !w.solid(cx, cy, cz + 1)) { float d = cz + 1 - z; if (d < best) { best = d; nx = 0; ny = 0; nz = 1; found = true; } }
                if (found) depth = best;
            }
            if (!found) {
                // already inside last step (or the first step): leave by the nearest open face
                float best = Float.MAX_VALUE;
                if (!w.solid(cx, cy + 1, cz)) { float d = cy + 1 - y; if (d < best) { best = d; nx = 0; ny = 1; nz = 0; } }
                if (!w.solid(cx - 1, cy, cz)) { float d = x - cx; if (d < best) { best = d; nx = -1; ny = 0; nz = 0; } }
                if (!w.solid(cx + 1, cy, cz)) { float d = cx + 1 - x; if (d < best) { best = d; nx = 1; ny = 0; nz = 0; } }
                if (!w.solid(cx, cy, cz - 1)) { float d = z - cz; if (d < best) { best = d; nx = 0; ny = 0; nz = -1; } }
                if (!w.solid(cx, cy, cz + 1)) { float d = cz + 1 - z; if (d < best) { best = d; nx = 0; ny = 0; nz = 1; } }
                if (!w.solid(cx, cy - 1, cz)) { float d = y - cy; if (d < best) { best = d; nx = 0; ny = -1; nz = 0; } }
                if (best == Float.MAX_VALUE) { nx = 0; ny = 1; nz = 0; best = 0.5f; }   // buried: up is the only way out
                depth = best;
            }
            contacts++;
            // ---- impulse: stop the approach, give a little back
            velocityAt(x, y, z, vel);
            float vn = vel[0] * nx + vel[1] * ny + vel[2] * nz;
            if (vn < 0) {
                float k = invMassAt(x, y, z, nx, ny, nz);
                float e = vn < -2.5f ? restitution : 0;      // resting contacts do not bounce
                float j = -(1 + e) * vn / k;
                applyImpulse(x, y, z, nx * j, ny * j, nz * j);
                float dv = j * invMass;
                if (dv > impact) {
                    impact = dv; impactX = x; impactY = y; impactZ = z; impactNX = nx; impactNY = ny; impactNZ = nz;
                    impactLX = local[i * 3]; impactLY = local[i * 3 + 1]; impactLZ = local[i * 3 + 2];
                }
                if (onHit != null && -vn > 3f) onHit.hit(cx, cy, cz, -vn);
                // friction along the surface, limited by the normal impulse
                velocityAt(x, y, z, vel);
                float tvn = vel[0] * nx + vel[1] * ny + vel[2] * nz;
                float tx = vel[0] - nx * tvn, ty = vel[1] - ny * tvn, tz = vel[2] - nz * tvn;
                float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (tl > 1e-3f) {
                    tx /= tl; ty /= tl; tz /= tl;
                    float kt = invMassAt(x, y, z, tx, ty, tz);
                    float jt = Math.min(tl / kt, friction * j);
                    applyImpulse(x, y, z, -tx * jt, -ty * jt, -tz * jt);
                }
            }
            // ---- position: gather the deepest push per axis instead of summing (many samples share one wall)
            float pd = Math.min(depth, 0.6f);
            if (Math.abs(nx * pd) > Math.abs(pushX)) pushX = nx * pd;
            if (Math.abs(ny * pd) > Math.abs(pushY)) pushY = ny * pd;
            if (Math.abs(nz * pd) > Math.abs(pushZ)) pushZ = nz * pd;
            pushes++;
        }
        if (pushes > 0) {
            px += pushX * 0.8f; py += pushY * 0.8f; pz += pushZ * 0.8f;
            // the samples moved with the body: remember where they are now, outside
            for (int i = 0; i < n; i++) {
                toWorld(local[i * 3], local[i * 3 + 1], local[i * 3 + 2], wp);
                if (!w.solid((int) Math.floor(wp[0]), (int) Math.floor(wp[1]), (int) Math.floor(wp[2]))) { prev[i * 3] = wp[0]; prev[i * 3 + 1] = wp[1]; prev[i * 3 + 2] = wp[2]; }
            }
        }
        prevValid = true;
    }

    /** Kinetic energy (J): the conserved quantity the tests watch across collisions and airborne flight. */
    public float energy() {
        float lx = r[0] * wx + r[3] * wy + r[6] * wz, ly = r[1] * wx + r[4] * wy + r[7] * wz, lz = r[2] * wx + r[5] * wy + r[8] * wz;
        return 0.5f * mass * (vx * vx + vy * vy + vz * vz) + 0.5f * (lx * lx / invIx + ly * ly / invIy + lz * lz / invIz);
    }
}
