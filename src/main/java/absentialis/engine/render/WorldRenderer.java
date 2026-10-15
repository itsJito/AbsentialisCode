package absentialis.engine.render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import absentialis.engine.math.Frustum;
import absentialis.engine.math.Mat4;
import absentialis.world.World;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.lwjgl.system.MemoryUtil;

/**
 * Chunk-column renderer: background greedy meshing, GPU upload, frustum culling, opaque/cutout pass
 * and sorted translucent pass, plus the procedural sky.
 */
public final class WorldRenderer {
    private final World world;
    public final Atlas atlas;
    private Shader shader, sky;
    private int ebo, skyVao;
    private static final int MAX_QUADS = 1 << 20;

    private final int[] vaoO = new int[World.CX * World.CZ], vboO = new int[World.CX * World.CZ], quadsO = new int[World.CX * World.CZ];
    private final int[] vaoT = new int[World.CX * World.CZ], vboT = new int[World.CX * World.CZ], quadsT = new int[World.CX * World.CZ];
    private final int[] seqApplied = new int[World.CX * World.CZ];
    private final AtomicInteger seqGen = new AtomicInteger(1);
    private final boolean[] inFlight = new boolean[World.CX * World.CZ];

    private record Result(int col, int seq, byte[] o, int oSize, byte[] t, int tSize) {}
    private final ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();
    private final ExecutorService pool;
    private final ThreadLocal<ChunkMesher> meshers;
    public final AtomicInteger pending = new AtomicInteger();
    /** 1.3: told of every column that is meshed again (what is drawn as objects in it may have changed too). */
    public volatile java.util.function.IntConsumer onColumnDirty;
    public int drawnColumns, drawnQuads;
    public float viewDistance = 300;
    private final Frustum frustum = new Frustum();

    public WorldRenderer(World world, Atlas atlas) {
        this.world = world;
        this.atlas = atlas;
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        pool = Executors.newFixedThreadPool(threads, r -> { Thread t = new Thread(r, "mesher"); t.setDaemon(true); t.setPriority(Thread.NORM_PRIORITY - 1); return t; });
        meshers = ThreadLocal.withInitial(() -> new ChunkMesher(world));
    }

    public void init() {
        shader = new Shader(WORLD_VS, WORLD_FS);
        sky = new Shader(SKY_VS, SKY_FS);
        IntBuffer ib = MemoryUtil.memAllocInt(MAX_QUADS * 6);
        for (int q = 0; q < MAX_QUADS; q++) { int b = q * 4; ib.put(b).put(b + 1).put(b + 2).put(b + 2).put(b + 3).put(b); }
        ib.flip();
        ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
        MemoryUtil.memFree(ib);
        skyVao = glGenVertexArrays();
    }

    /** Queues meshing jobs for every dirty column. */
    public void scheduleDirty() {
        if (!world.anyDirty) return;
        world.anyDirty = false;
        for (int c = 0; c < world.dirtyColumns.length; c++) {
            if (!world.dirtyColumns[c]) continue;
            world.dirtyColumns[c] = false;
            submit(c);
            if (onColumnDirty != null) onColumnDirty.accept(c);
            if (!voxelQueued[c]) { voxelQueued[c] = true; voxelDirty.add(c); }
        }
    }

    private void submit(int c) {
        final int seq = seqGen.getAndIncrement();
        pending.incrementAndGet();
        final int cx = c / World.CZ, cz = c % World.CZ;
        pool.submit(() -> {
            try {
                ChunkMesher m = meshers.get();
                m.mesh(cx, cz);
                results.add(new Result(c, seq, java.util.Arrays.copyOf(m.opaque.data, m.opaque.size), m.opaque.size,
                        java.util.Arrays.copyOf(m.translucent.data, m.translucent.size), m.translucent.size));
            } catch (Throwable t) {
                t.printStackTrace();
                pending.decrementAndGet();
            }
        });
    }

    /** Uploads finished meshes; budgetMs limits main-thread time (0 = unlimited). */
    public void uploadResults(int maxCount) {
        Result r;
        int n = 0;
        while ((maxCount <= 0 || n < maxCount) && (r = results.poll()) != null) {
            pending.decrementAndGet();
            n++;
            if (r.seq < seqApplied[r.col]) continue;
            seqApplied[r.col] = r.seq;
            upload(r.col, r.o, r.oSize, true);
            upload(r.col, r.t, r.tSize, false);
        }
    }

    private void upload(int col, byte[] data, int size, boolean opaquePass) {
        int[] vaos = opaquePass ? vaoO : vaoT, vbos = opaquePass ? vboO : vboT, quads = opaquePass ? quadsO : quadsT;
        int q = size / (ChunkMesher.VERTEX_BYTES * 4);
        if (q == 0) { quads[col] = 0; return; }
        if (vaos[col] == 0) {
            vaos[col] = glGenVertexArrays();
            vbos[col] = glGenBuffers();
            glBindVertexArray(vaos[col]);
            glBindBuffer(GL_ARRAY_BUFFER, vbos[col]);
            glVertexAttribPointer(0, 4, GL_UNSIGNED_BYTE, false, 12, 0);
            glVertexAttribPointer(1, 4, GL_UNSIGNED_BYTE, false, 12, 4);
            glVertexAttribPointer(2, 4, GL_UNSIGNED_BYTE, true, 12, 8);
            glEnableVertexAttribArray(0);
            glEnableVertexAttribArray(1);
            glEnableVertexAttribArray(2);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        }
        glBindVertexArray(vaos[col]);
        glBindBuffer(GL_ARRAY_BUFFER, vbos[col]);
        ByteBuffer bb = MemoryUtil.memAlloc(size);
        bb.put(data, 0, size).flip();
        glBufferData(GL_ARRAY_BUFFER, bb, GL_STATIC_DRAW);
        MemoryUtil.memFree(bb);
        glBindVertexArray(0);
        quads[col] = Math.min(q, MAX_QUADS);
    }

    public boolean busy() { return pending.get() > 0 || !results.isEmpty(); }

    /**
     * Columns whose voxels changed since the GPU voxel volume last saw them (drained by {@link VoxelVolume} on the
     * render thread). The same columns are the regions whose irradiance probes must be re-lit.
     */
    public final java.util.ArrayDeque<Integer> voxelDirty = new java.util.ArrayDeque<>();
    final boolean[] voxelQueued = new boolean[World.CX * World.CZ];

    /** Draws the opaque + cutout geometry of every column inside a frustum with the bound program (it must take uOrigin). */
    public int drawOpaque(Shader sh, Frustum fr, float cx, float cz, float maxDist) { return drawOpaque(sh, fr, cx, cz, maxDist, false); }

    private long[] order = new long[World.CX * World.CZ];

    /**
     * nearFirst sorts the visible columns front to back, so an expensive fragment shader runs on the nearest
     * surface and is rejected by the depth test for everything behind it.
     */
    public int drawOpaque(Shader sh, Frustum fr, float cx, float cz, float maxDist, boolean nearFirst) {
        float vd2 = maxDist * maxDist;
        int n = 0;
        for (int x = 0; x < World.CX; x++) for (int z = 0; z < World.CZ; z++) {
            int c = x * World.CZ + z;
            if (quadsO[c] == 0) continue;
            float x0 = x * 16, z0 = z * 16;
            float ddx = Math.max(0, Math.max(x0 - cx, cx - x0 - 16)), ddz = Math.max(0, Math.max(z0 - cz, cz - z0 - 16));
            float d2 = ddx * ddx + ddz * ddz;
            if (d2 > vd2) continue;
            if (!fr.aabb(x0, 0, z0, x0 + 16, World.H, z0 + 16)) continue;
            order[n++] = ((long) d2 << 16) | c;
        }
        if (nearFirst) java.util.Arrays.sort(order, 0, n);
        for (int i = 0; i < n; i++) {
            int c = (int) (order[i] & 0xFFFF);
            sh.f3("uOrigin", (c / World.CZ) * 16f, 0, (c % World.CZ) * 16f);
            glBindVertexArray(vaoO[c]);
            glDrawElements(GL_TRIANGLES, quadsO[c] * 6, GL_UNSIGNED_INT, 0);
            drawnQuads += quadsO[c];
        }
        glBindVertexArray(0);
        return n;
    }

    private final List<int[]> transScratch = new ArrayList<>();

    /** Draws the translucent geometry (glass, water) back to front with the bound program. */
    public void drawTranslucent(Shader sh, Frustum fr, float cx, float cz, float maxDist) {
        float vd2 = maxDist * maxDist;
        transScratch.clear();
        for (int x = 0; x < World.CX; x++) for (int z = 0; z < World.CZ; z++) {
            int c = x * World.CZ + z;
            if (quadsT[c] == 0) continue;
            float x0 = x * 16, z0 = z * 16;
            float ddx = Math.max(0, Math.max(x0 - cx, cx - x0 - 16)), ddz = Math.max(0, Math.max(z0 - cz, cz - z0 - 16));
            float d2 = ddx * ddx + ddz * ddz;
            if (d2 > vd2 || !fr.aabb(x0, 0, z0, x0 + 16, World.H, z0 + 16)) continue;
            transScratch.add(new int[]{c, (int) d2});
        }
        transScratch.sort((a, b) -> Integer.compare(b[1], a[1]));
        for (int[] t : transScratch) {
            int c = t[0];
            sh.f3("uOrigin", (c / World.CZ) * 16f, 0, (c % World.CZ) * 16f);
            glBindVertexArray(vaoT[c]);
            glDrawElements(GL_TRIANGLES, quadsT[c] * 6, GL_UNSIGNED_INT, 0);
        }
        glBindVertexArray(0);
    }

    public World world() { return world; }

    // ------------------------------------------------------------------ frame
    public static final class Env {
        /** The fog bank (0.6): metres at which the world starts to dissolve and is gone; how much of the sky it takes at the horizon and overhead. Off by default (previews, menus). */
        public float fogWallStart = 1e9f, fogWallEnd = 2e9f, fogSkyHorizon, fogSkyZenith;
        public float daylight = 1, time, fogDensity = 1f / 200f, flashlight, rain, sunX = 0.3f, sunY = 0.8f, sunZ = 0.4f;
        /** 0.7: the least light any surface gets (daylight that has bounced its way indoors; a trace at night). */
        public float ambientFloor = 0.0035f;
        /** 1.0, the weather system's numbers: cloud over the sun (-1 = take it from rain), how wet open ground is (-1 = from rain), how much snow lies, the lightning's light this instant. */
        public float overcast = -1, wet = -1, snow, lightning;
        public float fogR = 0.6f, fogG = 0.62f, fogB = 0.64f, skyR = 1, skyG = 1, skyB = 1, tintR = 1, tintG = 1, tintB = 1;
        public float camX, camY, camZ, dirX, dirY, dirZ, flashR, flashX, flashY, flashZ;
    }

    public void renderSky(Env e, float[] fwd, float[] right, float[] up, float tanHalf, float aspect) {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        sky.use();
        sky.f3("uF", fwd[0], fwd[1], fwd[2]);
        sky.f3("uR", right[0], right[1], right[2]);
        sky.f3("uU", up[0], up[1], up[2]);
        sky.f2("uScale", tanHalf * aspect, tanHalf);
        sky.f3("uFog", e.fogR, e.fogG, e.fogB);
        sky.f3("uSun", e.sunX, e.sunY, e.sunZ);
        sky.f1("uDay", e.daylight);
        sky.f1("uTime", e.time);
        sky.f1("uRain", e.rain);
        glBindVertexArray(skyVao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    public void render(Mat4 pv, Env e) {
        frustum.update(pv);
        shader.use();
        shader.mat4("uPV", pv);
        shader.i1("uAtlas", 0);
        shader.i1("uNoise", 1);
        shader.f1("uDaylight", e.daylight);
        shader.f1("uTime", e.time);
        shader.f1("uFogDensity", e.fogDensity);
        shader.f3("uFog", e.fogR, e.fogG, e.fogB);
        shader.f3("uSkyCol", e.skyR, e.skyG, e.skyB);
        shader.f3("uTint", e.tintR, e.tintG, e.tintB);
        shader.f3("uCam", e.camX, e.camY, e.camZ);
        shader.f3("uCamDir", e.dirX, e.dirY, e.dirZ);
        shader.f3("uSun", e.sunX, e.sunY, e.sunZ);
        shader.f1("uFlashlight", e.flashlight);
        shader.f4("uMuzzle", e.flashX, e.flashY, e.flashZ, e.flashR);
        shader.f1("uRain", e.rain);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_ARRAY, atlas.texId);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, atlas.noiseTex);
        glActiveTexture(GL_TEXTURE0);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        shader.i1("uPass", 0);
        drawnColumns = 0; drawnQuads = 0;
        List<int[]> transList = new ArrayList<>();
        float vd2 = viewDistance * viewDistance;
        for (int cx = 0; cx < World.CX; cx++) for (int cz = 0; cz < World.CZ; cz++) {
            int c = cx * World.CZ + cz;
            if (quadsO[c] == 0 && quadsT[c] == 0) continue;
            float x0 = cx * 16, z0 = cz * 16;
            float ddx = Math.max(0, Math.max(x0 - e.camX, e.camX - x0 - 16)), ddz = Math.max(0, Math.max(z0 - e.camZ, e.camZ - z0 - 16));
            if (ddx * ddx + ddz * ddz > vd2) continue;
            if (!frustum.aabb(x0, 0, z0, x0 + 16, World.H, z0 + 16)) continue;
            if (quadsO[c] > 0) {
                shader.f3("uOrigin", x0, 0, z0);
                glBindVertexArray(vaoO[c]);
                glDrawElements(GL_TRIANGLES, quadsO[c] * 6, GL_UNSIGNED_INT, 0);
                drawnColumns++;
                drawnQuads += quadsO[c];
            }
            if (quadsT[c] > 0) transList.add(new int[]{c, (int) (ddx * ddx + ddz * ddz)});
        }
        // 0.8: the frames of the panes first, as the solid things they are; then the glass, blended in a way that does not
        // depend on the order it is drawn in (see HybridRenderer.forwardPass)
        transList.sort((a, b) -> Integer.compare(b[1], a[1]));
        shader.i1("uPass", 2);
        for (int[] t : transList) {
            int c = t[0];
            shader.f3("uOrigin", (c / World.CZ) * 16f, 0, (c % World.CZ) * 16f);
            glBindVertexArray(vaoT[c]);
            glDrawElements(GL_TRIANGLES, quadsT[c] * 6, GL_UNSIGNED_INT, 0);
        }
        glEnable(GL_BLEND);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);
        shader.i1("uPass", 1);
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 0) glBlendFunc(GL_ZERO, GL_ONE_MINUS_SRC_ALPHA); else glBlendFunc(GL_SRC_ALPHA, GL_ONE);
            for (int[] t : transList) {
                int c = t[0];
                shader.f3("uOrigin", (c / World.CZ) * 16f, 0, (c % World.CZ) * 16f);
                glBindVertexArray(vaoT[c]);
                glDrawElements(GL_TRIANGLES, quadsT[c] * 6, GL_UNSIGNED_INT, 0);
            }
        }
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glBindVertexArray(0);
    }

    public void shutdown() { pool.shutdownNow(); }

    // ------------------------------------------------------------------ shaders
    private static final String WORLD_VS = """
        #version 330 core
        layout(location=0) in vec4 aPos;
        layout(location=1) in vec4 aTex;
        layout(location=2) in vec4 aLight;
        uniform mat4 uPV;
        uniform vec3 uOrigin;
        uniform float uTime;
        out vec3 vWorld;
        out vec2 vST;
        out vec3 vLight;
        flat out int vLayer;
        flat out int vVar;
        flat out int vFlags;
        flat out int vNormal;
        void main() {
            // shaped blocks carry a sixteenth-of-a-block offset in the spare bits (see ChunkMesher.putVertexF)
            int nb = int(aPos.w + 0.5);
            int fr = int(aLight.w * 255.0 + 0.5);
            vec3 p = aPos.xyz + vec3(float(fr & 15), float(fr >> 4), float(nb >> 3)) / 16.0 + uOrigin;
            int n = nb & 7;
            int pk = int(aTex.w + 0.5);
            vLayer = int(aTex.z + 0.5) + ((pk & 3) << 8);
            vVar = ((pk >> 2) & 3) + 1;
            vFlags = pk >> 4;
            if ((vFlags & 2) != 0 && aPos.y > 0.5) {
                p.x += sin(uTime * 1.7 + p.z * 0.7 + p.y) * 0.05;
                p.z += cos(uTime * 1.3 + p.x * 0.5) * 0.05;
            }
            if ((vFlags & 4) != 0 && n == 2) p.y -= 0.12 + sin(uTime + p.x * 0.3 + p.z * 0.2) * 0.02;
            vWorld = p;
            vST = aTex.xy;
            vNormal = n;
            vLight = aLight.xyz;
            gl_Position = uPV * vec4(p, 1.0);
        }
        """;

    private static final String WORLD_FS = """
        #version 330 core
        in vec3 vWorld;
        in vec2 vST;
        in vec3 vLight;
        flat in int vLayer;
        flat in int vVar;
        flat in int vFlags;
        flat in int vNormal;
        uniform sampler2DArray uAtlas;
        uniform sampler2D uNoise;
        uniform float uDaylight, uFogDensity, uTime, uFlashlight, uRain;
        uniform vec3 uFog, uSkyCol, uTint, uCam, uCamDir, uSun;
        uniform vec4 uMuzzle;
        uniform int uPass;
        out vec4 frag;
        const vec3 NORMALS[7] = vec3[](vec3(1,0,0), vec3(-1,0,0), vec3(0,1,0), vec3(0,-1,0), vec3(0,0,1), vec3(0,0,-1), vec3(0,1,0));
        float hash(vec3 p) { p = fract(p * 0.3183099 + vec3(0.71, 0.113, 0.419)); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
        void main() {
            vec3 N = NORMALS[vNormal];
            vec2 uv;
            if (vNormal == 2 || vNormal == 3) uv = vST;
            else {
                float sg = (vNormal == 0 || vNormal == 5) ? -1.0 : 1.0;
                uv = vec2(vST.x * sg, -vST.y);
            }
            vec3 bp = floor(vWorld - N * 0.02 + (vNormal == 6 ? vec3(0.0) : vec3(0.0)));
            float h = hash(bp);
            int layer = vLayer + min(int(h * float(vVar)), vVar - 1);
            vec4 tex = texture(uAtlas, vec3(uv, float(layer)));
            if (uPass == 0 && tex.a < 0.5) discard;
            vec3 col = tex.rgb;
            bool emissive = (vFlags & 1) != 0;
            bool liquid = (vFlags & 4) != 0;
            // 0.8: pass 2 is the solid part of a pane (its frame), with depth; pass 1 is the glass of it, near face only
            if (uPass == 2 && (liquid || tex.a < 0.92)) discard;
            if (uPass == 1 && !liquid && (tex.a >= 0.92 || !gl_FrontFacing)) discard;
            // material wear: per-block tone jitter, grime blotches, rain streaks, street-level dirt band
            col *= 0.92 + 0.16 * hash(bp + vec3(13.1, 7.7, 3.3));
            if (!emissive && !liquid) {
                vec2 np = (abs(N.y) > 0.5) ? vWorld.xz : (abs(N.x) > 0.5 ? vWorld.zy : vWorld.xy);
                vec4 nz = texture(uNoise, np / 40.0);
                float grime = smoothstep(0.52, 0.78, nz.r) * 0.28;
                if (abs(N.y) < 0.5) {
                    grime += smoothstep(0.55, 0.85, texture(uNoise, vec2(np.x / 12.0, np.y / 80.0)).g) * 0.20;
                    float gy = vWorld.y - 24.0;
                    grime += (1.0 - smoothstep(0.0, 1.6, gy)) * step(-0.5, gy) * 0.22;
                } else if (N.y > 0.5) {
                    grime += smoothstep(0.6, 0.9, texture(uNoise, np / 9.0).b) * 0.15;
                }
                col *= 1.0 - grime;
                col = mix(col, col * vec3(0.85, 0.95, 0.75), smoothstep(0.7, 0.95, nz.a) * 0.5);
                if (uRain > 0.0 && N.y > 0.5) col *= 1.0 - uRain * 0.25;
            }
            float sky = vLight.x, blk = vLight.y, ao = vLight.z;
            float aoF = 0.42 + 0.58 * ao;
            float face = vNormal == 2 || vNormal == 6 ? 1.0 : (vNormal == 3 ? 0.55 : (vNormal < 2 ? 0.80 : 0.90));
            float skyL = pow(sky, 1.5) * uDaylight;
            float sunD = max(dot(N, normalize(uSun)), 0.0) * smoothstep(0.85, 1.0, sky) * uDaylight;
            vec3 sunCol = mix(vec3(1.0, 0.65, 0.4), vec3(1.0, 0.96, 0.88), clamp(uSun.y * 2.0, 0.0, 1.0));
            vec3 amb = uSkyCol * (skyL * 0.72 + 0.045) * face + sunCol * sunD * 0.42;
            vec3 bl = vec3(1.0, 0.76, 0.48) * pow(blk, 1.35) * 1.15 * face;
            vec3 lightC = max(amb, bl) + amb * bl * 0.3;
            if (uFlashlight > 0.0) {
                vec3 d = vWorld - uCam;
                float dist = length(d);
                float spot = smoothstep(0.86, 0.97, dot(d / max(dist, 0.001), uCamDir)) * clamp(1.0 - dist / 30.0, 0.0, 1.0);
                lightC += vec3(1.0, 0.95, 0.85) * spot * 0.95 * max(dot(N, -d / max(dist, 0.001)), 0.25);
            }
            if (uMuzzle.w > 0.0) {
                float md = distance(vWorld, uMuzzle.xyz);
                lightC += vec3(1.0, 0.8, 0.5) * uMuzzle.w * clamp(1.0 - md / 9.0, 0.0, 1.0);
            }
            if (emissive) lightC = vec3(1.3);
            col *= lightC * (emissive ? 1.0 : aoF) * uTint;
            float dist = length(vWorld - uCam);
            float f = 1.0 - exp(-pow(dist * uFogDensity, 1.35));
            f = clamp(f, 0.0, 1.0);
            col = mix(col, uFog, f);
            frag = vec4(col, uPass == 1 ? max(tex.a, liquid ? 0.6 : 0.0) * (1.0 - f * 0.5) : 1.0);
        }
        """;

    private static final String SKY_VS = """
        #version 330 core
        out vec2 vNdc;
        void main() {
            vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2) * 2.0 - 1.0;
            vNdc = p;
            gl_Position = vec4(p, 0.9999, 1.0);
        }
        """;

    private static final String SKY_FS = """
        #version 330 core
        in vec2 vNdc;
        uniform vec3 uF, uR, uU, uFog, uSun;
        uniform vec2 uScale;
        uniform float uDay, uTime, uRain;
        out vec4 frag;
        float h(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
        float n2(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
            return mix(mix(h(i), h(i + vec2(1, 0)), f.x), mix(h(i + vec2(0, 1)), h(i + vec2(1, 1)), f.x), f.y); }
        void main() {
            vec3 d = normalize(uF + uR * vNdc.x * uScale.x + uU * vNdc.y * uScale.y);
            vec3 zenith = mix(vec3(0.02, 0.03, 0.06), vec3(0.32, 0.45, 0.62), uDay);
            zenith = mix(zenith, vec3(0.35, 0.37, 0.38) * max(uDay, 0.15), uRain * 0.8);
            vec3 col = mix(uFog, zenith, smoothstep(-0.02, 0.45, d.y));
            vec3 s = normalize(uSun);
            float sd = max(dot(d, s), 0.0);
            vec3 sunCol = mix(vec3(1.0, 0.5, 0.25), vec3(1.0, 0.95, 0.85), clamp(s.y * 2.0, 0.0, 1.0));
            col += sunCol * (pow(sd, 900.0) * 3.0 + pow(sd, 12.0) * 0.25) * (1.0 - uRain * 0.8) * step(-0.05, s.y);
            float md = max(dot(d, -s), 0.0);
            col += vec3(0.8, 0.85, 0.95) * pow(md, 1500.0) * 2.0 * (1.0 - uDay);
            if (d.y > 0.0) {
                vec2 cp = d.xz / (d.y + 0.15) * 2.0 + vec2(uTime * 0.01, 0.0);
                float c = n2(cp) * 0.6 + n2(cp * 2.3) * 0.3 + n2(cp * 5.1) * 0.1;
                float cover = mix(0.55, 0.2, uRain);
                float cl = smoothstep(cover, cover + 0.3, c) * smoothstep(0.0, 0.25, d.y);
                vec3 cc = mix(vec3(0.08, 0.08, 0.1), vec3(0.85, 0.83, 0.8), uDay) * mix(1.0, 0.55, uRain);
                col = mix(col, cc, cl * 0.85);
                if (uDay < 0.35) col += vec3(step(0.9975, h(floor(d.xz / (d.y + 0.3) * 300.0)))) * (0.35 - uDay) * 2.0 * (1.0 - cl);
            }
            frag = vec4(col, 1.0);
        }
        """;
}
