package absentialis.engine.render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL42.*;
import static org.lwjgl.opengl.GL43.*;

import absentialis.world.World;
import java.nio.ByteBuffer;

/**
 * Directional irradiance probes for the whole Zone.
 *
 * One probe every four blocks (176 x 24 x 176), each holding irradiance as a constant and a linear term per colour
 * channel, the mean free distance it sees in each direction (for visibility weighting) and where it was moved to
 * when its grid point fell inside a wall. The textures cover the entire map, so they are also the cache: a region
 * (one 16 x 16 block column = 4 x 24 x 4 probes) is recomputed only when something that lights it has changed.
 *
 * Probes are lit on the GPU by a compute shader that walks rays through the {@link VoxelVolume}. Each frame a
 * budget of regions is updated, most urgent first:
 *   1. regions whose voxels changed (a wall blown out, a door opened) and their neighbours,
 *   2. regions that have never been lit, nearest the camera first,
 *   3. regions whose lighting is out of date because the sun has moved or district power changed,
 *   4. everything else in a slow round-robin, so multi-bounce light keeps converging.
 * A fresh region needs several passes before bounced light has settled; that is the "converges over multiple
 * frames" of the design, and the reason raids pre-light the area around the insertion while loading.
 */
public final class ProbeField {
    public static final int PX = 176, PY = 24, PZ = 176, SPACING = 4, REGION_PROBES = 4 * PY * 4, MAX_BATCH = 48;
    /** Passes after which a region's bounced light is considered settled for its current lighting signature. */
    public static final int SETTLED = 6;

    public int texR, texG, texB, texVis, texMeta;
    private Shader compute;
    private final int[] passes = new int[World.CX * World.CZ];        // updates since last invalidation
    private final int[] signature = new int[World.CX * World.CZ];     // lighting signature the region was last lit for
    private final long[] lastFrame = new long[World.CX * World.CZ];
    private final boolean[] everLit = new boolean[World.CX * World.CZ];
    private int currentSignature = 1;
    private long frame;
    private final int[] batch = new int[MAX_BATCH * 2];
    private final float[] blend = new float[MAX_BATCH];
    private final float[] score = new float[World.CX * World.CZ];
    private final float[] rot = new float[9];
    private final java.util.Random rng = new java.util.Random(7);
    /** Statistics for the debug overlay and the quality gates. */
    public int updatedThisFrame, unsettled, neverLit;
    public long totalUpdates;

    public void init() {
        texR = volume(GL_RGBA16F, GL_LINEAR);
        texG = volume(GL_RGBA16F, GL_LINEAR);
        texB = volume(GL_RGBA16F, GL_LINEAR);
        texVis = volume(GL_RGBA16F, GL_NEAREST);
        texMeta = volume(GL_RGBA8, GL_NEAREST);
        compute = new Shader(Glsl.PROBE_CS);
        neverLit = passes.length;
    }

    private static int volume(int format, int filter) {
        int t = glGenTextures();
        glBindTexture(GL_TEXTURE_3D, t);
        glTexStorage3D(GL_TEXTURE_3D, 1, format, PX, PY, PZ);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        // storage starts undefined: clear it so unlit probes read as black and invalid
        org.lwjgl.opengl.GL44.glClearTexImage(t, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
        return t;
    }

    /** A column's voxels changed. Light reaches about two regions further, so they are re-lit too. */
    public void invalidate(int cx, int cz, boolean spread) {
        int r = spread ? 2 : 0;
        for (int x = Math.max(0, cx - r); x <= Math.min(World.CX - 1, cx + r); x++)
            for (int z = Math.max(0, cz - r); z <= Math.min(World.CZ - 1, cz + r); z++) {
                int c = x * World.CZ + z;
                passes[c] = Math.min(passes[c], x == cx && z == cz ? 0 : 2);
            }
    }

    /** The lighting everything was computed for is no longer current (sun moved a step, power switched, a new raid). */
    public void lightingChanged() { currentSignature++; }

    /** Forgets everything: a new world state will be lit from scratch. */
    public void reset() {
        java.util.Arrays.fill(passes, 0);
        java.util.Arrays.fill(everLit, false);
        java.util.Arrays.fill(signature, 0);
        currentSignature++;
    }

    /** Share of the regions within radius blocks of a point whose light has settled (loading screens wait on this). */
    public float settledNear(float x, float z, float radius) {
        int n = 0, ok = 0;
        for (int cx = 0; cx < World.CX; cx++) for (int cz = 0; cz < World.CZ; cz++) {
            float dx = cx * 16 + 8 - x, dz = cz * 16 + 8 - z;
            if (dx * dx + dz * dz > radius * radius) continue;
            n++;
            int c = cx * World.CZ + cz;
            if (signature[c] == currentSignature && passes[c] >= SETTLED - 2) ok++;
        }
        return n == 0 ? 1 : ok / (float) n;
    }

    /**
     * Lights up to 'budget' regions this frame. Priorities are evaluated around the camera; sun and sky arrive in
     * the same radiance units the lighting pass uses.
     */
    public void update(VoxelVolume vox, int budget, int rays, float camX, float camZ, float[] sunDir, float[] sunCol, float[] zenith, float[] horizon) {
        frame++;
        budget = Math.min(MAX_BATCH, budget);
        updatedThisFrame = 0;
        if (budget <= 0) return;
        // ---- score every region (1936 of them: trivial) and take the best 'budget'
        int uns = 0, never = 0;
        for (int cx = 0; cx < World.CX; cx++) for (int cz = 0; cz < World.CZ; cz++) {
            int c = cx * World.CZ + cz;
            float dx = cx * 16 + 8 - camX, dz = cz * 16 + 8 - camZ;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            float near = 1f / (1f + dist / 48f);
            boolean stale = signature[c] != currentSignature;
            float s;
            float age = (frame - lastFrame[c]) / 60f;                           // roughly seconds since this region was last lit
            if (!everLit[c]) { s = dist < 220 ? 40 + near * 60 : 12 + near * 20; never++; }
            else if (passes[c] < SETTLED && !stale) { s = 30 + near * 80 + (SETTLED - passes[c]) * 4; uns++; }
            else if (stale && dist < 170) { s = 10 + near * 70; uns++; }       // what the player can see is brought up to date at once
            else if (stale) s = Math.min(8f, age / 4f);                          // the rest of the city follows over the next half minute
            else s = Math.min(6f, age / 45f) + near * 0.25f;                     // settled: a slow refresh so bounced light keeps converging
            score[c] = s;
        }
        unsettled = uns; neverLit = never;
        int n = 0;
        for (int k = 0; k < budget; k++) {
            int best = -1;
            float bs = 0.6f;
            for (int c = 0; c < score.length; c++) if (score[c] > bs) { bs = score[c]; best = c; }
            if (best < 0) break;
            // below this score the work is background: it gets a quarter of the budget, so a settled scene costs little
            if (bs < 25f && n >= Math.max(1, budget / 4)) break;
            score[best] = -1;
            batch[n * 2] = best / World.CZ; batch[n * 2 + 1] = best % World.CZ;
            boolean stale = signature[best] != currentSignature;
            // first light replaces whatever was there; after that new samples are folded in so noise averages out
            blend[n] = !everLit[best] ? 1f : passes[best] < 2 || stale ? 0.6f : passes[best] < SETTLED ? 0.35f : 0.2f;
            if (stale) { signature[best] = currentSignature; passes[best] = Math.min(passes[best], SETTLED - 3); }
            passes[best]++;
            everLit[best] = true;
            lastFrame[best] = frame;
            n++;
        }
        if (n == 0) return;
        // ---- a fresh random rotation of the ray set each frame: successive passes sample different directions
        randomRotation();
        compute.use();
        compute.tex("uVox", 0, GL_TEXTURE_3D, vox.tex);
        compute.tex("uPalette", 1, GL_TEXTURE_2D, vox.palette);
        compute.tex("uProbeR", 2, GL_TEXTURE_3D, texR);
        compute.tex("uProbeG", 3, GL_TEXTURE_3D, texG);
        compute.tex("uProbeB", 4, GL_TEXTURE_3D, texB);
        compute.uiv("uOpaque", vox.opaqueMask);
        compute.iv2("uRegions", batch);
        compute.fv("uRegionBlend", blend);
        compute.i1("uRegionCount", n);
        compute.i1("uRays", rays);
        compute.mat3("uRot", rot);
        compute.f3("uSunDir", sunDir[0], sunDir[1], sunDir[2]);
        compute.f3("uSunCol", sunCol[0], sunCol[1], sunCol[2]);
        compute.f3("uSkyZenith", zenith[0], zenith[1], zenith[2]);
        compute.f3("uSkyHorizon", horizon[0], horizon[1], horizon[2]);
        glBindImageTexture(0, texR, 0, true, 0, GL_READ_WRITE, GL_RGBA16F);
        glBindImageTexture(1, texG, 0, true, 0, GL_READ_WRITE, GL_RGBA16F);
        glBindImageTexture(2, texB, 0, true, 0, GL_READ_WRITE, GL_RGBA16F);
        glBindImageTexture(3, texVis, 0, true, 0, GL_READ_WRITE, GL_RGBA16F);
        glBindImageTexture(4, texMeta, 0, true, 0, GL_READ_WRITE, GL_RGBA8);
        glDispatchCompute((n * REGION_PROBES + 63) / 64, 1, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
        updatedThisFrame = n;
        totalUpdates += n;
    }

    private void randomRotation() {
        // a uniformly random rotation from a random unit quaternion
        double u1 = rng.nextDouble(), u2 = rng.nextDouble() * 2 * Math.PI, u3 = rng.nextDouble() * 2 * Math.PI;
        double a = Math.sqrt(1 - u1), b = Math.sqrt(u1);
        float x = (float) (a * Math.sin(u2)), y = (float) (a * Math.cos(u2)), z = (float) (b * Math.sin(u3)), w = (float) (b * Math.cos(u3));
        rot[0] = 1 - 2 * (y * y + z * z); rot[1] = 2 * (x * y + z * w); rot[2] = 2 * (x * z - y * w);
        rot[3] = 2 * (x * y - z * w); rot[4] = 1 - 2 * (x * x + z * z); rot[5] = 2 * (y * z + x * w);
        rot[6] = 2 * (x * z + y * w); rot[7] = 2 * (y * z - x * w); rot[8] = 1 - 2 * (x * x + y * y);
    }

    public void dispose() { glDeleteTextures(texR); glDeleteTextures(texG); glDeleteTextures(texB); glDeleteTextures(texVis); glDeleteTextures(texMeta); }
}
