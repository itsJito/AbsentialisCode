package absentialis.engine.render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL42.*;
import static org.lwjgl.opengl.GL43.*;

import absentialis.engine.math.Frustum;
import absentialis.engine.math.Mat4;
import absentialis.world.Block;
import absentialis.world.World;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.lwjgl.system.MemoryUtil;

/**
 * Hybrid global-illumination renderer (OpenGL 4.5): a deferred, physically based, HDR pipeline.
 *
 * <pre>
 *   voxel sync ......... changed columns -> GPU voxel volume -> probe regions invalidated
 *   probe update ....... compute shader, voxel ray traversal, prioritised regions (ProbeField)
 *   shadow cascades .... sun / moon, world + every recorded model (player and NPCs cast shadows)
 *   G-buffer ........... albedo+emission | normal+flags | roughness, metal, baked AO, baked sky/block light | depth
 *   light culling ...... compute shader bins the dynamic lights into 16 px screen tiles
 *   GTAO, SSR .......... ground-truth ambient occlusion; reflections traced against depth, lit from last frame
 *   lighting ........... sun with cascades, tile lights (voxel-traced shadows), probe irradiance, emission, sky
 *   volumetrics ........ half-resolution fog march with sun shafts and local-light glow, depth-aware upsample
 *   forward ............ glass and water, see-through and self-lit models, particles (fire, smoke, embers)
 *   TAA -> bloom -> auto exposure -> ACES tone map (+ injury, infection and death grading) -> window
 * </pre>
 *
 * Nothing is baked into a single lighting solution: the probes are relit as the sun moves, as power comes and goes
 * and as the world is damaged, and they are cached for every region that has not changed.
 */
public final class HybridRenderer {
    /** Scalable settings; see {@link #preset(int)} for the four presets the options menu offers. */
    public static final class Quality {
        public int shadowRes = 2048, cascades = 4, shadowedLights = 2, probeBudget = 8, probeRays = 32, volumetricSteps = 14, maxLights = 128;
        public boolean gtao = true, ssr = true, volumetrics = true, taa = true, bloom = true;
        public float renderScale = 1f, shadowDistance = 150f;
    }

    public final Quality q = new Quality();
    public static final String[] PRESETS = {"Low", "Medium", "High", "Ultra"};

    /** Applies one of the four presets (0 Low .. 3 Ultra). Individual switches can still be changed afterwards. */
    public void preset(int level) {
        switch (Math.max(0, Math.min(3, level))) {
            case 0 -> { q.shadowRes = 1024; q.cascades = 3; q.shadowedLights = 0; q.probeBudget = 3; q.probeRays = 16; q.volumetricSteps = 8; q.maxLights = 48; q.shadowDistance = 100; }
            case 1 -> { q.shadowRes = 2048; q.cascades = 3; q.shadowedLights = 1; q.probeBudget = 5; q.probeRays = 24; q.volumetricSteps = 10; q.maxLights = 80; q.shadowDistance = 140; }
            case 2 -> { q.shadowRes = 2048; q.cascades = 4; q.shadowedLights = 2; q.probeBudget = 8; q.probeRays = 32; q.volumetricSteps = 14; q.maxLights = 128; q.shadowDistance = 150; }
            default -> { q.shadowRes = 4096; q.cascades = 4; q.shadowedLights = 6; q.probeBudget = 20; q.probeRays = 64; q.volumetricSteps = 24; q.maxLights = 224; q.shadowDistance = 240; }
        }
    }

    /** Everything one frame needs from the game. */
    public static final class Frame {
        public final Mat4 view = new Mat4(), proj = new Mat4(), vmProj = new Mat4();
        public float camX, camY, camZ, fwdX, fwdY, fwdZ, fovY, aspect, near = 0.05f, far = 900f, dt;
        public WorldRenderer.Env env;
        /** Screen grading: 0..1 each. death drains colour and adds grain, hurt reddens the edges, infection greens them. */
        public float death, hurt, infection, flash;
        /** 0.7: covered in infected bile, 0..1 - the picture swims out of focus and goes green at the edges. */
        public float bile;
        public float viewDistance = 260;
        public boolean reset;
        /** 1.0: where the fog is measured from - the player, whichever way the camera is looking at them. NaN = the camera. */
        public float fogX = Float.NaN, fogY, fogZ;
        /** 1.0: how much of the glow round bright things is laid over the picture (less of it under a roof). */
        public float bloom = 0.05f;
    }

    public final Frame frame = new Frame();
    public final DrawList scene = new DrawList(), viewmodel = new DrawList();
    public final ParticleBatch particles = new ParticleBatch();
    public final ProbeField probes = new ProbeField();
    /** 1.0: the Astratto foliage system and decal engine, drawn into the G-buffer with the world (null = none). */
    public absentialis.engine.foliage.Foliage foliage;
    public absentialis.engine.decal.Decals decals;
    /** 1.0: the Astratto prop system: static voxel models, instanced (null = none). */
    public absentialis.engine.props.Props props;
    public VoxelVolume voxels;
    private World world;
    private Atlas atlas;

    // ---- programs
    private Shader gWorld, gModel, sWorld, sModel, lighting, cull, gtao, aoBlur, ssr, vol, fogApply, fWorld, fModel, particle, taa, bloomDown, bloomUp, lum, exposure, tonemap, copy;
    // ---- targets
    private int rw, rh, winW, winH;
    private int gFbo, gAlbedo, gNormal, gMat, gDepth;
    private int hdrAFbo, hdrA, hdrBFbo, hdrBColorFbo, hdrB;
    private final int[] hist = new int[2], histFbo = new int[2];
    private int histIdx;
    private int aoRawFbo, aoRaw, aoFbo, ao, ssrFbo, ssrTex, fogFbo, fogTex;
    private static final int BLOOM_LEVELS = 6;
    private final int[] bloomTex = new int[BLOOM_LEVELS], bloomFbo = new int[BLOOM_LEVELS], bloomW = new int[BLOOM_LEVELS], bloomH = new int[BLOOM_LEVELS];
    private int lumTex, lumFbo;
    private final int[] expoTex = new int[2], expoFbo = new int[2];
    private int expoIdx;
    private int shadowTex, shadowRes, shadowLayers;
    private final int[] shadowFbo = new int[4];
    private int layerMat, fsVao, lightBuf, tileBuf, tilesX, tilesY;
    private static final int TILE = 16, MAX_LIGHTS = 256;
    private final FloatBuffer lightData = MemoryUtil.memAllocFloat(MAX_LIGHTS * 12);
    private int lightCount;
    // ---- matrices
    private final Mat4 projJ = new Mat4(), pv = new Mat4(), pvJ = new Mat4(), invPV = new Mat4(), invPVJ = new Mat4(), invProj = new Mat4(), invView = new Mat4(), prevPV = new Mat4(), vmPV = new Mat4(), tmp = new Mat4();
    private final Mat4[] shadowVP = {new Mat4(), new Mat4(), new Mat4(), new Mat4()};
    private final float[] shadowVPFlat = new float[64], splits = new float[4], cascadeTexel = new float[4], m16 = new float[16];
    private final Frustum frustum = new Frustum(), lightFrustum = new Frustum();
    private long frameNo;
    private boolean historyValid;
    private final float[] sunDir = new float[3], sunCol = new float[3], zenith = new float[3], horizon = new float[3], fogCol = new float[3], ambient = new float[3];
    private int lastSunBucket = Integer.MIN_VALUE;
    /** 0 = final image; 1 AO, 2 reflections, 3 fog, 4 albedo, 5 normals (developer views: console "gfx view n"). */
    public int debugView;
    public String stats = "";
    private final boolean profiling = System.getenv("ABS_GPUPROF") != null;
    private static final String[] PASS = {"probes", "shadows", "gbuffer", "cull", "ao", "ssr", "lighting", "fog", "forward", "resolve", "gbuf-models"};
    private long quadsSeen;
    private final double[] passMs = new double[PASS.length];
    private long profT, profFrames;

    private void mark(int pass) {
        if (!profiling) return;
        glFinish();
        long now = System.nanoTime();
        if (pass >= 0) passMs[pass] += (now - profT) / 1e6;
        profT = now;
    }

    /** Average milliseconds per pass since start (only when profiling was enabled). */
    public String profile() {
        if (!profiling || profFrames == 0) return "";
        StringBuilder b = new StringBuilder("[GPU] ");
        double total = 0;
        for (int i = 0; i < PASS.length; i++) { b.append(String.format("%s %.2f  ", PASS[i], passMs[i] / profFrames)); total += passMs[i] / profFrames; }
        return b.append(String.format("| total %.2f ms | %d world quads a frame", total, quadsSeen / profFrames)).toString();
    }

    public void init(World world, Atlas atlas, int winW, int winH) {
        this.world = world;
        this.atlas = atlas;
        gWorld = new Shader(Glsl.WORLD_VS, Glsl.WORLD_GBUF_FS);
        gModel = new Shader(Glsl.MODEL_VS, Glsl.MODEL_GBUF_FS);
        sWorld = new Shader(Glsl.WORLD_VS, Glsl.WORLD_SHADOW_FS);
        sModel = new Shader(Glsl.MODEL_VS, Glsl.MODEL_SHADOW_FS);
        lighting = new Shader(Glsl.FS_VS, Glsl.LIGHTING_FS);
        cull = new Shader(Glsl.LIGHT_CULL_CS);
        gtao = new Shader(Glsl.FS_VS, Glsl.GTAO_FS);
        aoBlur = new Shader(Glsl.FS_VS, Glsl.AO_BLUR_FS);
        ssr = new Shader(Glsl.FS_VS, Glsl.SSR_FS);
        vol = new Shader(Glsl.FS_VS, Glsl.VOLUMETRIC_FS);
        fogApply = new Shader(Glsl.FS_VS, Glsl.FOG_APPLY_FS);
        fWorld = new Shader(Glsl.WORLD_VS, Glsl.WORLD_FWD_FS);
        fModel = new Shader(Glsl.MODEL_VS, Glsl.MODEL_FWD_FS);
        particle = new Shader(Glsl.PARTICLE_VS, Glsl.PARTICLE_FS);
        taa = new Shader(Glsl.FS_VS, Glsl.TAA_FS);
        bloomDown = new Shader(Glsl.FS_VS, Glsl.BLOOM_DOWN_FS);
        bloomUp = new Shader(Glsl.FS_VS, Glsl.BLOOM_UP_FS);
        lum = new Shader(Glsl.FS_VS, Glsl.LUM_FS);
        exposure = new Shader(Glsl.FS_VS, Glsl.EXPOSURE_FS);
        tonemap = new Shader(Glsl.FS_VS, Glsl.TONEMAP_FS);
        copy = new Shader(Glsl.FS_VS, Glsl.COPY_FS);
        fsVao = glGenVertexArrays();
        lightBuf = glGenBuffers();
        tileBuf = glGenBuffers();
        particles.init();
        probes.init();
        voxels = new VoxelVolume(world);
        voxels.init(atlas);
        buildLayerMaterials();
        // exposure and luminance targets do not depend on the window
        lumTex = tex2D(GL_R16F, 256, 256, GL_LINEAR_MIPMAP_NEAREST, 9);
        lumFbo = fbo(lumTex, 0);
        for (int i = 0; i < 2; i++) { expoTex[i] = tex2D(GL_R16F, 1, 1, GL_NEAREST, 1); expoFbo[i] = fbo(expoTex[i], 0); }
        resize(winW, winH);
    }

    // ------------------------------------------------------------------ resources
    private static int tex2D(int format, int w, int h, int filter, int levels) {
        int t = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, t);
        glTexStorage2D(GL_TEXTURE_2D, levels, format, Math.max(1, w), Math.max(1, h));
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter == GL_NEAREST ? GL_NEAREST : GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return t;
    }

    private static int fbo(int color, int depth) {
        int f = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, f);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
        if (depth != 0) glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depth, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Incomplete framebuffer");
        return f;
    }

    /** Roughness, metalness and emission per atlas layer, from the material of the blocks that use the layer. */
    private void buildLayerMaterials() {
        int layers = Math.max(1, atlas.pixels.size());
        ByteBuffer b = MemoryUtil.memCalloc(layers * 4);
        for (int i = 0; i < layers; i++) b.put(i * 4, (byte) 215).put(i * 4 + 1, (byte) 0).put(i * 4 + 2, (byte) 0).put(i * 4 + 3, (byte) 255);
        for (Block bl : Block.all()) {
            if (bl.texTop == null) continue;
            float rough, metal = 0;
            switch (bl.mat) {
                case METAL -> { rough = 0.42f; metal = 0.9f; }
                case THIN_METAL -> { rough = 0.5f; metal = 0.8f; }
                case TILE -> rough = 0.3f;
                case GLASS -> rough = 0.1f;
                case WATER -> rough = 0.05f;
                case PLASTIC -> rough = 0.5f;
                case WOOD -> rough = 0.72f;
                case ASPHALT -> rough = 0.88f;
                case CONCRETE, STONE, BRICK -> rough = 0.86f;
                case FLESH -> rough = 0.6f;
                default -> rough = 0.95f;
            }
            float emit = bl.light > 0 && bl.is(Block.EMISSIVE_TEX) ? 0.25f + 0.75f * bl.light / 15f : 0;
            int[][] sets = {{bl.layerTop, bl.varTop}, {bl.layerSide, bl.varSide}, {bl.layerBottom, bl.varBottom}};
            for (int[] s : sets) for (int v = 0; v < Math.max(1, s[1]); v++) {
                int l = s[0] + v;
                if (l < 0 || l >= layers) continue;
                b.put(l * 4, (byte) (rough * 255)).put(l * 4 + 1, (byte) (metal * 255));
                // a layer shared by a lit and an unlit block keeps the brighter value; the vertex flag decides who glows
                if ((b.get(l * 4 + 2) & 255) < (int) (emit * 255)) b.put(l * 4 + 2, (byte) (emit * 255));
            }
        }
        layerMat = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, layerMat);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, layers, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, b);
        MemoryUtil.memFree(b);
    }

    private float appliedScale;

    /** (Re)creates every size-dependent target. Safe to call each frame: it returns at once when nothing changed. */
    public void resize(int w, int h) {
        w = Math.max(16, w); h = Math.max(16, h);
        if (w == winW && h == winH && appliedScale == q.renderScale) return;
        if (gFbo != 0) disposeTargets();
        winW = w; winH = h; appliedScale = q.renderScale;
        rw = Math.max(16, Math.round(w * q.renderScale)); rh = Math.max(16, Math.round(h * q.renderScale));
        gAlbedo = tex2D(GL_RGBA8, rw, rh, GL_NEAREST, 1);
        gNormal = tex2D(GL_RGB10_A2, rw, rh, GL_NEAREST, 1);
        gMat = tex2D(GL_RGBA8, rw, rh, GL_NEAREST, 1);
        gDepth = tex2D(GL_DEPTH_COMPONENT32F, rw, rh, GL_NEAREST, 1);
        gFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, gFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, gAlbedo, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, gNormal, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT2, GL_TEXTURE_2D, gMat, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, gDepth, 0);
        glDrawBuffers(new int[]{GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1, GL_COLOR_ATTACHMENT2});
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("G-buffer incomplete");
        hdrA = tex2D(GL_RGBA16F, rw, rh, GL_LINEAR, 1); hdrAFbo = fbo(hdrA, 0);
        hdrB = tex2D(GL_RGBA16F, rw, rh, GL_LINEAR, 1); hdrBFbo = fbo(hdrB, gDepth); hdrBColorFbo = fbo(hdrB, 0);
        for (int i = 0; i < 2; i++) { hist[i] = tex2D(GL_RGBA16F, rw, rh, GL_LINEAR, 1); histFbo[i] = fbo(hist[i], 0); }
        aoRaw = tex2D(GL_R8, rw / 2, rh / 2, GL_LINEAR, 1); aoRawFbo = fbo(aoRaw, 0);
        ao = tex2D(GL_R8, rw, rh, GL_LINEAR, 1); aoFbo = fbo(ao, 0);
        ssrTex = tex2D(GL_RGBA16F, rw / 2, rh / 2, GL_LINEAR, 1); ssrFbo = fbo(ssrTex, 0);
        fogTex = tex2D(GL_RGBA16F, rw / 2, rh / 2, GL_LINEAR, 1); fogFbo = fbo(fogTex, 0);
        int bw = rw / 2, bh = rh / 2;
        for (int i = 0; i < BLOOM_LEVELS; i++) {
            bloomW[i] = Math.max(2, bw); bloomH[i] = Math.max(2, bh);
            bloomTex[i] = tex2D(GL_R11F_G11F_B10F, bloomW[i], bloomH[i], GL_LINEAR, 1);
            bloomFbo[i] = fbo(bloomTex[i], 0);
            bw /= 2; bh /= 2;
        }
        tilesX = (rw + TILE - 1) / TILE; tilesY = (rh + TILE - 1) / TILE;
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, tileBuf);
        glBufferData(GL_SHADER_STORAGE_BUFFER, (long) tilesX * tilesY * 32 * 4, GL_DYNAMIC_COPY);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        historyValid = false;
    }

    private void disposeTargets() {
        glDeleteFramebuffers(new int[]{gFbo, hdrAFbo, hdrBFbo, hdrBColorFbo, histFbo[0], histFbo[1], aoRawFbo, aoFbo, ssrFbo, fogFbo});
        glDeleteTextures(new int[]{gAlbedo, gNormal, gMat, gDepth, hdrA, hdrB, hist[0], hist[1], aoRaw, ao, ssrTex, fogTex});
        glDeleteFramebuffers(bloomFbo);
        glDeleteTextures(bloomTex);
        gFbo = 0;
    }

    private void ensureShadowMaps() {
        if (shadowTex != 0 && shadowRes == q.shadowRes && shadowLayers == q.cascades) return;
        if (shadowTex != 0) { glDeleteTextures(shadowTex); glDeleteFramebuffers(shadowFbo); }
        shadowRes = q.shadowRes; shadowLayers = q.cascades;
        shadowTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, shadowTex);
        glTexStorage3D(GL_TEXTURE_2D_ARRAY, 1, GL_DEPTH_COMPONENT24, shadowRes, shadowRes, 4);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
        for (int i = 0; i < 4; i++) {
            shadowFbo[i] = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo[i]);
            glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, shadowTex, 0, i);
            glDrawBuffer(GL_NONE);
            glReadBuffer(GL_NONE);
            glClearDepth(1.0);
            glClear(GL_DEPTH_BUFFER_BIT);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        cascadeAge = -1;     // every cascade is drawn on the next frame
    }

    private int cascadeAge;

    // ------------------------------------------------------------------ lights
    public static final int LIGHT_SHADOW = 1, LIGHT_SPOT = 2, LIGHT_STATIC = 4;

    public void clearLights() { lightCount = 0; lightData.clear(); bodyCount = 0; }

    /** 1.1: bodies that throw a shadow in local lights (a car's headlights, a torch): upright capsules, up to {@link #MAX_BODIES} a frame, nearest first. */
    public static final int MAX_BODIES = 24;
    private final float[] bodies = new float[MAX_BODIES * 5];
    private int bodyCount;
    public void addBody(float x, float y, float z, float radius, float height) {
        if (bodyCount >= MAX_BODIES) return;
        int o = bodyCount * 5;
        bodies[o] = x; bodies[o + 1] = y; bodies[o + 2] = z; bodies[o + 3] = radius; bodies[o + 4] = height;
        bodyCount++;
    }
    public int bodyCount() { return bodyCount; }

    /**
     * Adds a dynamic light. Add the important ones first: only the first Quality.shadowedLights entries that ask
     * for LIGHT_SHADOW are voxel-traced, and the volumetric pass glows around the first six.
     * Colour is linear radiance per unit albedo at the source (a torch is about 6, a muzzle flash 20).
     */
    public void addLight(float x, float y, float z, float radius, float r, float g, float b, int flags, float dx, float dy, float dz, float cosOuter) {
        if (lightCount >= Math.min(MAX_LIGHTS, q.maxLights)) return;
        lightData.put(x).put(y).put(z).put(radius).put(r).put(g).put(b).put(flags).put(dx).put(dy).put(dz).put(cosOuter);
        lightCount++;
    }

    public void addPoint(float x, float y, float z, float radius, float r, float g, float b, int flags) { addLight(x, y, z, radius, r, g, b, flags, 0, -1, 0, -1); }
    public int lightCount() { return lightCount; }

    // ------------------------------------------------------------------ environment
    private static float sstep(float a, float b, float x) { float t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
    private static float lin(float c) { return (float) Math.pow(Math.max(0, c), 2.2); }

    private void environment(WorldRenderer.Env e) {
        float sl = (float) Math.sqrt(e.sunX * e.sunX + e.sunY * e.sunY + e.sunZ * e.sunZ);
        float sx = e.sunX / sl, sy = e.sunY / sl, sz = e.sunZ / sl;
        float cloud = e.overcast >= 0 ? e.overcast : e.rain;
        float day = e.daylight, clear = 1 - cloud * 0.75f;
        float sunK = sstep(-0.04f, 0.14f, sy) * 3.4f * clear, warm = Math.max(0, Math.min(1, sy * 2.5f));
        float moonK = sstep(0.0f, 0.25f, -sy) * 0.085f * clear;
        if (sunK >= moonK) {
            sunDir[0] = sx; sunDir[1] = sy; sunDir[2] = sz;
            sunCol[0] = sunK; sunCol[1] = sunK * (0.55f + 0.41f * warm); sunCol[2] = sunK * (0.30f + 0.58f * warm);
        } else {
            sunDir[0] = -sx; sunDir[1] = -sy; sunDir[2] = -sz;
            sunCol[0] = moonK * 0.55f; sunCol[1] = moonK * 0.68f; sunCol[2] = moonK;
        }
        float nightR = 0.010f, nightG = 0.014f, nightB = 0.030f;
        zenith[0] = nightR + (0.22f - nightR) * day; zenith[1] = nightG + (0.40f - nightG) * day; zenith[2] = nightB + (0.72f - nightB) * day;
        // the horizon takes the district's fog colour (it is already dimmed for night by the game)
        horizon[0] = lin(e.fogR) * 1.25f + 0.004f; horizon[1] = lin(e.fogG) * 1.25f + 0.005f; horizon[2] = lin(e.fogB) * 1.25f + 0.008f;
        float grey = (zenith[0] + zenith[1] + zenith[2]) / 3f * 0.8f;
        for (int i = 0; i < 3; i++) zenith[i] += (grey - zenith[i]) * cloud * 0.85f;
        float fm = Math.max(1e-3f, Math.max(e.fogR, Math.max(e.fogG, e.fogB)));
        fogCol[0] = 0.55f + 0.45f * e.fogR / fm; fogCol[1] = 0.55f + 0.45f * e.fogG / fm; fogCol[2] = 0.55f + 0.45f * e.fogB / fm;
        for (int i = 0; i < 3; i++) ambient[i] = horizon[i] * 0.65f + zenith[i] * 0.35f;
        // the probes were lit for a sun position: after it has moved a few degrees they are out of date
        int bucket = Math.round((float) Math.atan2(e.sunY, e.sunX) * 14f) * 8 + Math.round(cloud * 4) + (int) (day * 8) * 1000;
        if (bucket != lastSunBucket) { if (lastSunBucket != Integer.MIN_VALUE) probes.lightingChanged(); lastSunBucket = bucket; }
    }

    private void skyUniforms(Shader s, WorldRenderer.Env e) {
        s.f3("uSkyZenith", zenith[0], zenith[1], zenith[2]);
        s.f3("uSkyHorizon", horizon[0], horizon[1], horizon[2]);
        s.f3("uSunDir", sunDir[0], sunDir[1], sunDir[2]);
        s.f3("uSunCol", sunCol[0], sunCol[1], sunCol[2]);
        s.f3("uMoonDir", -sunDir[0], -sunDir[1], -sunDir[2]);
        s.f1("uDay", e.daylight);
        s.f1("uRain", e.overcast >= 0 ? e.overcast : e.rain);
        s.f1("uTime", e.time);
        s.f1("uLightning", e.lightning);
    }

    private void probeUniforms(Shader s, int unit) {
        s.tex("uProbeR", unit, GL_TEXTURE_3D, probes.texR);
        s.tex("uProbeG", unit + 1, GL_TEXTURE_3D, probes.texG);
        s.tex("uProbeB", unit + 2, GL_TEXTURE_3D, probes.texB);
    }

    // ------------------------------------------------------------------ preparation outside the frame
    /**
     * Brings the GPU voxel volume up to date and lights probes as fast as the budget allows (loading screens).
     * Returns how settled the lighting is around the given point, 0..1.
     */
    public float prepare(WorldRenderer wr, WorldRenderer.Env e, float x, float z, int regions) {
        int left = voxels.sync(wr, probes, 400);
        if (left > 0) return 0;
        environment(e);
        for (int done = 0; done < regions; done += ProbeField.MAX_BATCH)
            probes.update(voxels, Math.min(ProbeField.MAX_BATCH, regions - done), q.probeRays, x, z, sunDir, sunCol, zenith, horizon);
        return probes.settledNear(x, z, 96);
    }

    // ------------------------------------------------------------------ the frame
    private static final float[] HALTON_X = {0.5f, 0.25f, 0.75f, 0.125f, 0.625f, 0.375f, 0.875f, 0.0625f}, HALTON_Y = {0.3333f, 0.6667f, 0.1111f, 0.4444f, 0.7778f, 0.2222f, 0.5556f, 0.8889f};

    public void render(WorldRenderer wr, int windowW, int windowH) {
        Frame f = frame;
        WorldRenderer.Env e = f.env;
        resize(windowW, windowH);
        ensureShadowMaps();
        frameNo++;
        wr.drawnQuads = 0;

        // ---- world state on the GPU, then the probes that depend on it
        mark(-1);
        voxels.sync(wr, probes, 48);
        environment(e);
        probes.update(voxels, q.probeBudget, q.probeRays, f.camX, f.camZ, sunDir, sunCol, zenith, horizon);
        if (decals != null) decals.update(f.camX, f.camZ, 2);
        if (props != null) props.update(f.camX, f.camZ);
        if (foliage != null) { foliage.view(f.fwdX, f.fwdZ); foliage.update(f.camX, f.camZ, 3); }
        mark(0);

        // ---- matrices (jittered projection for TAA)
        projJ.set(f.proj);
        if (q.taa) {
            int j = (int) (frameNo % 8);
            projJ.m[8] += (HALTON_X[j] - 0.5f) * 2f / rw;
            projJ.m[9] += (HALTON_Y[j] - 0.5f) * 2f / rh;
        }
        pv.set(f.proj).mul(f.view);
        pvJ.set(projJ).mul(f.view);
        invPV.invert(pv);
        invPVJ.invert(pvJ);
        invProj.invert(projJ);
        invView.invert(f.view);
        if (!historyValid || f.reset) prevPV.set(pv);
        frustum.update(pvJ);

        shadowPass(wr, f); mark(1);
        gbufferPass(wr, f, e); mark(10);
        cullLights(f); mark(3);
        if (q.gtao) aoPass(f);
        mark(4);
        if (q.ssr && historyValid) ssrPass(f);
        mark(5);
        lightingPass(f, e); mark(6);
        fogPass(f, e); mark(7);
        forwardPass(wr, f, e); mark(8);
        resolve(f, e, windowW, windowH); mark(9);
        profFrames++;

        prevPV.set(pv);
        historyValid = true;
        f.reset = false;
        stats = String.format("%dx%d  %d lights  probes +%d (%d unsettled)  %d model draws", rw, rh, lightCount, probes.updatedThisFrame, probes.unsettled, scene.count);
        // leave the state the 2D layer expects
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, windowW, windowH);
        glDepthRange(0, 1);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glActiveTexture(GL_TEXTURE0);
        glUseProgram(0);
    }

    // ------------------------------------------------------------------ shadows
    private final float[] corner = new float[3];

    private void shadowPass(WorldRenderer wr, Frame f) {
        int n = q.cascades;
        float near = 0.2f, far = q.shadowDistance;
        // practical split scheme: mostly logarithmic, so the first cascade stays tight around the player
        for (int i = 0; i < n; i++) {
            float k = (i + 1) / (float) n;
            float log = near * (float) Math.pow(far / near, k), uni = near + (far - near) * k;
            splits[i] = log * 0.82f + uni * 0.18f;
        }
        for (int i = n; i < 4; i++) splits[i] = splits[n - 1];
        boolean lit = sunCol[0] + sunCol[1] + sunCol[2] > 1e-4f;
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glDepthFunc(GL_LEQUAL);
        glDisable(GL_BLEND);
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(1.6f, 3.0f);
        glViewport(0, 0, shadowRes, shadowRes);
        float tanY = (float) Math.tan(f.fovY * 0.5f), tanX = tanY * f.aspect;
        // camera basis from the view matrix rows
        float[] v = f.view.m;
        float rx = v[0], ry = v[4], rz = v[8], ux = v[1], uy = v[5], uz = v[9];
        // light basis
        float lx = sunDir[0], ly = sunDir[1], lz = sunDir[2];
        float ax = Math.abs(ly) > 0.95f ? 1 : 0, ay = Math.abs(ly) > 0.95f ? 0 : 1, az = 0;
        float sxr = ay * lz - az * ly, syr = az * lx - ax * lz, szr = ax * ly - ay * lx;
        float sl = (float) Math.sqrt(sxr * sxr + syr * syr + szr * szr);
        sxr /= sl; syr /= sl; szr /= sl;
        float sux = ly * szr - lz * syr, suy = lz * sxr - lx * szr, suz = lx * syr - ly * sxr;
        for (int i = 0; i < n; i++) {
            // far cascades change slowly on screen: refresh them on alternate frames
            boolean due = cascadeAge < 0 || i == 0 || (i == 1 && (frameNo & 1) == 0) || (i == 2 && (frameNo & 3) == 1) || (i == 3 && (frameNo & 7) == 3);
            if (!due || !lit) continue;
            float zn = i == 0 ? near : splits[i - 1], zf = splits[i];
            // bounding sphere of the frustum slice: stable under rotation, so the map does not shimmer when you turn
            float zc = (zn + zf) * 0.5f;
            float cx = f.camX + f.fwdX * zc, cy = f.camY + f.fwdY * zc, cz = f.camZ + f.fwdZ * zc;
            float hx = zf * tanX, hy = zf * tanY, dz = zf - zc;
            float radius = (float) Math.sqrt(hx * hx + hy * hy + dz * dz) * 1.02f;
            float texel = 2 * radius / shadowRes;
            // snap the sphere centre to whole shadow texels in light space
            float px = cx * sxr + cy * syr + cz * szr, py = cx * sux + cy * suy + cz * suz, pd = cx * lx + cy * ly + cz * lz;
            px = (float) Math.floor(px / texel) * texel; py = (float) Math.floor(py / texel) * texel;
            cx = sxr * px + sux * py + lx * pd; cy = syr * px + suy * py + ly * pd; cz = szr * px + suz * py + lz * pd;
            float back = 220f;
            tmp.lookAt(cx + lx * back, cy + ly * back, cz + lz * back, cx, cy, cz, sux, suy, suz);
            shadowVP[i].ortho(-radius, radius, -radius, radius, 1f, back + radius + 60f).mul(tmp);
            cascadeTexel[i] = texel;
            System.arraycopy(shadowVP[i].m, 0, shadowVPFlat, i * 16, 16);
            glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo[i]);
            glClear(GL_DEPTH_BUFFER_BIT);
            lightFrustum.update(shadowVP[i]);
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);
            sWorld.use();
            sWorld.mat4("uPV", shadowVP[i]);
            sWorld.f1("uTime", f.env.time);
            sWorld.tex("uAtlas", 0, GL_TEXTURE_2D_ARRAY, atlas.texId);
            wr.drawOpaque(sWorld, lightFrustum, f.camX, f.camZ, zf + 150);
            if (foliage != null && i < 2) foliage.drawShadow(shadowVP[i], f.camX, f.camY, f.camZ);
            if (props != null && i < 2) props.drawShadow(shadowVP[i], f.camX, f.camY, f.camZ);
            // characters, vehicles and props: the first three cascades are enough for anything that moves
            if (i < 3) {
                glDisable(GL_CULL_FACE);
                sModel.use();
                sModel.mat4("uPV", shadowVP[i]);
                float r2 = (radius + 4) * (radius + 4);
                for (int k = 0; k < scene.count; k++) {
                    if ((scene.flag[k] & (DrawList.NO_SHADOW | DrawList.FORWARD)) != 0) continue;
                    float ex = scene.x(k) - f.camX, ez = scene.z(k) - f.camZ;
                    if (ex * ex + ez * ez > 90 * 90) continue;       // beyond this a person's shadow is a few pixels
                    float ddx = scene.x(k) - cx, ddy = scene.y(k) - cy, ddz = scene.z(k) - cz;
                    float along = ddx * lx + ddy * ly + ddz * lz;
                    if (ddx * ddx + ddy * ddy + ddz * ddz - along * along > r2) continue;
                    System.arraycopy(scene.mat, k * 16, m16, 0, 16);
                    sModel.mat4("uModel", m16);
                    ModelRenderer.drawGeometry(scene.mesh[k]);
                }
            }
        }
        cascadeAge = 0;
        glDisable(GL_POLYGON_OFFSET_FILL);
        glEnable(GL_CULL_FACE);
    }

    // ------------------------------------------------------------------ G-buffer
    private void gbufferPass(WorldRenderer wr, Frame f, WorldRenderer.Env e) {
        glBindFramebuffer(GL_FRAMEBUFFER, gFbo);
        glViewport(0, 0, rw, rh);
        glDepthRange(0, 1);
        glClearColor(0, 0, 0, 0);
        glClearDepth(1.0);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        gWorld.use();
        gWorld.mat4("uPV", pvJ);
        gWorld.f1("uTime", e.time);
        gWorld.f1("uRain", e.wet >= 0 ? e.wet : e.rain);
        gWorld.f4("uWeather", e.snow, e.rain, 0, 0);
        gWorld.tex("uAtlas", 0, GL_TEXTURE_2D_ARRAY, atlas.texId);
        gWorld.tex("uNoise", 1, GL_TEXTURE_2D, atlas.noiseTex);
        gWorld.tex("uLayerMat", 2, GL_TEXTURE_2D, layerMat);
        gWorld.f1("uAlphaCut", 0.5f);
        wr.drawnColumns = wr.drawOpaque(gWorld, frustum, f.camX, f.camZ, f.viewDistance, true);
        quadsSeen += wr.drawnQuads;
        // 0.8: the solid part of every pane - the casement - goes into the G-buffer too, so that it has depth (see forwardPass)
        gWorld.f1("uAlphaCut", Glsl.GLASS_SOLID);
        wr.drawTranslucent(gWorld, frustum, f.camX, f.camZ, f.viewDistance);
        gWorld.f1("uAlphaCut", 0.5f);
        // 1.0: what lies on the world's surfaces and what grows out of them, lit with it
        if (decals != null) decals.draw(pvJ, f.camX, f.camY, f.camZ);
        if (foliage != null) foliage.drawGBuffer(pvJ, f.camX, f.camY, f.camZ);
        if (props != null) props.drawGBuffer(pvJ, f.camX, f.camY, f.camZ);
        mark(2);

        glDisable(GL_CULL_FACE);
        gModel.use();
        gModel.mat4("uPV", pvJ);
        drawModels(gModel, scene, 0, false);
        // the first-person weapon and hands: their own projection, squeezed into the front of the depth range so
        // nothing in the world can clip them and they still occlude it
        if (viewmodel.count > 0) {
            glDepthRange(0, 0.02);
            vmPV.set(f.vmProj).mul(f.view);
            gModel.mat4("uPV", vmPV);
            drawModels(gModel, viewmodel, 1, true);
            glDepthRange(0, 1);
        }
        glEnable(GL_CULL_FACE);
    }

    /** Replays the opaque entries of a list into the G-buffer. */
    private void drawModels(Shader s, DrawList l, int flag, boolean atCamera) {
        float csky = 1, cblk = 0;
        if (atCamera) { int ix = (int) Math.floor(frame.camX), iy = (int) Math.floor(frame.camY), iz = (int) Math.floor(frame.camZ); csky = world.sky(ix, iy, iz) / 15f; cblk = world.blockLight(ix, iy, iz) / 15f; }
        for (int k = 0; k < l.count; k++) {
            int fl = l.flag[k];
            if ((fl & (DrawList.FORWARD | DrawList.SHADOW_ONLY)) != 0) continue;
            if (!atCamera && !frustum.sphere(l.x(k), l.y(k), l.z(k), 4.5f)) continue;
            int p = k * 8;
            float sky = csky, blk = cblk;
            if (!atCamera) {
                int ix = (int) Math.floor(l.x(k)), iy = (int) Math.floor(l.y(k) + 0.6f), iz = (int) Math.floor(l.z(k));
                sky = world.sky(ix, iy, iz) / 15f; blk = world.blockLight(ix, iy, iz) / 15f;
            }
            System.arraycopy(l.mat, k * 16, m16, 0, 16);
            s.mat4("uModel", m16);
            s.f4("uTint", l.par[p], l.par[p + 1], l.par[p + 2], l.par[p + 3]);
            s.f1("uEmissive", l.par[p + 4]);
            s.f1("uFlag", l.par[p + 4] > 0.5f ? 2 : flag);
            s.f4("uMatParams", l.par[p + 6], l.par[p + 7], sky, blk);
            ModelRenderer.drawGeometry(l.mesh[k]);
        }
    }

    // ------------------------------------------------------------------ light culling
    private void cullLights(Frame f) {
        lightData.flip();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, lightBuf);
        glBufferData(GL_SHADER_STORAGE_BUFFER, (long) MAX_LIGHTS * 48, GL_DYNAMIC_DRAW);
        if (lightCount > 0) glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, lightData);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        lightData.limit(lightData.capacity());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, lightBuf);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, tileBuf);
        cull.use();
        cull.tex("uDepth", 0, GL_TEXTURE_2D, gDepth);
        cull.mat4("uInvProj", invProj);
        cull.mat4("uView", f.view);
        cull.i2("uTiles", tilesX, tilesY);
        cull.i2("uScreen", rw, rh);
        cull.i1("uTileSize", TILE);
        cull.i1("uLightCount", lightCount);
        glDispatchCompute((tilesX + 7) / 8, (tilesY + 7) / 8, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private void fullscreen() { glBindVertexArray(fsVao); glDrawArrays(GL_TRIANGLES, 0, 3); }

    private void aoPass(Frame f) {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glBindFramebuffer(GL_FRAMEBUFFER, aoRawFbo);
        glViewport(0, 0, rw / 2, rh / 2);
        gtao.use();
        gtao.tex("uDepth", 0, GL_TEXTURE_2D, gDepth);
        gtao.tex("uNormal", 1, GL_TEXTURE_2D, gNormal);
        gtao.mat4("uInvProj", invProj);
        gtao.mat4("uView", f.view);
        gtao.f2("uScreen", rw / 2, rh / 2);
        gtao.f1("uFrame", q.taa ? frameNo % 16 : 0);
        gtao.f1("uRadius", 1.6f);
        gtao.f1("uProjScale", 0.5f / (float) Math.tan(f.fovY * 0.5f));
        fullscreen();
        glBindFramebuffer(GL_FRAMEBUFFER, aoFbo);
        glViewport(0, 0, rw, rh);
        aoBlur.use();
        aoBlur.tex("uAO", 0, GL_TEXTURE_2D, aoRaw);
        aoBlur.tex("uDepth", 1, GL_TEXTURE_2D, gDepth);
        aoBlur.f2("uScreen", rw / 2, rh / 2);
        aoBlur.f1("uNear", f.near);
        aoBlur.f1("uFar", f.far);
        fullscreen();
    }

    private void ssrPass(Frame f) {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glBindFramebuffer(GL_FRAMEBUFFER, ssrFbo);
        glViewport(0, 0, rw / 2, rh / 2);
        ssr.use();
        ssr.tex("uDepth", 0, GL_TEXTURE_2D, gDepth);
        ssr.tex("uNormal", 1, GL_TEXTURE_2D, gNormal);
        ssr.tex("uMat", 2, GL_TEXTURE_2D, gMat);
        ssr.tex("uHistory", 3, GL_TEXTURE_2D, hist[histIdx]);
        ssr.mat4("uProj", projJ);
        ssr.mat4("uInvProj", invProj);
        ssr.mat4("uView", f.view);
        ssr.mat4("uInvView", invView);
        ssr.mat4("uPrevPV", prevPV);
        ssr.f2("uScreen", rw / 2, rh / 2);
        ssr.f1("uFrame", q.taa ? frameNo % 16 : 0);
        fullscreen();
    }

    // ------------------------------------------------------------------ lighting
    private void lightingPass(Frame f, WorldRenderer.Env e) {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glBindFramebuffer(GL_FRAMEBUFFER, hdrAFbo);
        glViewport(0, 0, rw, rh);
        Shader s = lighting;
        s.use();
        s.tex("uAlbedo", 0, GL_TEXTURE_2D, gAlbedo);
        s.tex("uNormal", 1, GL_TEXTURE_2D, gNormal);
        s.tex("uMat", 2, GL_TEXTURE_2D, gMat);
        s.tex("uDepth", 3, GL_TEXTURE_2D, gDepth);
        s.tex("uAO", 4, GL_TEXTURE_2D, ao);
        s.tex("uSSR", 5, GL_TEXTURE_2D, ssrTex);
        s.tex("uShadow", 6, GL_TEXTURE_2D_ARRAY, shadowTex);
        probeUniforms(s, 7);
        s.tex("uProbeVis", 10, GL_TEXTURE_3D, probes.texVis);
        s.tex("uVox", 11, GL_TEXTURE_3D, voxels.tex);
        s.uiv("uOpaque", voxels.opaqueMask);
        s.mat4("uInvPV", invPVJ);
        glUniformMatrix4fv(glGetUniformLocation(s.id, "uShadowVP"), false, shadowVPFlat);
        s.f4("uSplits", splits[0], splits[1], splits[2], splits[3]);
        s.f4("uCascadeTexel", cascadeTexel[0], cascadeTexel[1], cascadeTexel[2], cascadeTexel[3]);
        s.i1("uCascades", q.cascades);
        s.i1("uTileSize", TILE);
        s.i2("uTiles", tilesX, tilesY);
        s.i1("uShadowedLights", q.shadowedLights);
        s.i1("uBodyCount", bodyCount);
        if (bodyCount > 0) s.fv("uBodies", bodies);
        s.i1("uUseAO", q.gtao ? 1 : 0);
        s.i1("uUseSSR", q.ssr && historyValid ? 1 : 0);
        s.f3("uCam", f.camX, f.camY, f.camZ);
        s.f3("uCamFwd", f.fwdX, f.fwdY, f.fwdZ);
        s.f3("uTint", 0.6f + 0.4f * e.tintR, 0.6f + 0.4f * e.tintG, 0.6f + 0.4f * e.tintB);
        s.f1("uShadowRes", shadowRes);
        s.f1("uAmbientFloor", e.ambientFloor);
        s.tex("uExposure", 12, GL_TEXTURE_2D, expoTex[expoIdx]);
        skyUniforms(s, e);
        fullscreen();
    }

    private void fogPass(Frame f, WorldRenderer.Env e) {
        float density = e.fogDensity * 0.62f;
        if (q.volumetrics) {
            glBindFramebuffer(GL_FRAMEBUFFER, fogFbo);
            glViewport(0, 0, rw / 2, rh / 2);
            vol.use();
            vol.tex("uDepth", 0, GL_TEXTURE_2D, gDepth);
            vol.tex("uShadow", 1, GL_TEXTURE_2D_ARRAY, shadowTex);
            vol.mat4("uInvPV", invPVJ);
            glUniformMatrix4fv(glGetUniformLocation(vol.id, "uShadowVP"), false, shadowVPFlat);
            vol.f4("uSplits", splits[0], splits[1], splits[2], splits[3]);
            vol.i1("uCascades", q.cascades);
            vol.i1("uLightCount", lightCount);
            vol.i1("uSteps", q.volumetricSteps);
            vol.f3("uCam", f.camX, f.camY, f.camZ);
            vol.f3("uCamFwd", f.fwdX, f.fwdY, f.fwdZ);
            vol.f3("uSunDir", sunDir[0], sunDir[1], sunDir[2]);
            vol.f3("uSunCol", sunCol[0], sunCol[1], sunCol[2]);
            vol.f3("uFogCol", fogCol[0], fogCol[1], fogCol[2]);
            vol.f3("uAmbient", ambient[0], ambient[1], ambient[2]);
            vol.f1("uDensity", density);
            vol.f1("uFrame", q.taa ? frameNo % 16 : 0);
            vol.f1("uHeightBase", World.GROUND);
            vol.f1("uMaxDist", 140f);
            probeUniforms(vol, 3);
            fullscreen();
        }
        glBindFramebuffer(GL_FRAMEBUFFER, hdrBColorFbo);
        glViewport(0, 0, rw, rh);
        fogApply.use();
        fogApply.tex("uColor", 0, GL_TEXTURE_2D, hdrA);
        fogApply.tex("uFog", 1, GL_TEXTURE_2D, fogTex);
        fogApply.tex("uDepth", 2, GL_TEXTURE_2D, gDepth);
        fogApply.f2("uFogSize", rw / 2, rh / 2);
        fogApply.i1("uUseFog", q.volumetrics ? 1 : 0);
        fogApply.mat4("uInvPV", invPVJ);
        fogApply.f3("uCam", f.camX, f.camY, f.camZ);
        fogApply.f3("uFogCol", fogCol[0], fogCol[1], fogCol[2]);
        fogApply.f3("uAmbient", ambient[0], ambient[1], ambient[2]);
        fogApply.f1("uDensity", density);
        fogApply.f1("uHeightBase", World.GROUND);
        fogApply.f1("uMaxDist", 140f);
        fogApply.f1("uNear", f.near);
        fogApply.f1("uFar", f.far);
        skyUniforms(fogApply, e);
        bankUniforms(fogApply, e);
        fullscreen();
    }

    /** The fog bank (0.6): where it starts and ends, how much of it the sky shows, and its colour - the horizon's. */
    private void bankUniforms(Shader s, WorldRenderer.Env e) {
        s.f4("uWall", e.fogWallStart, e.fogWallEnd, e.fogSkyHorizon, e.fogSkyZenith);
        // the bank is the horizon's colour with half its tint washed out: fog, not coloured smoke
        float lum = horizon[0] * 0.3f + horizon[1] * 0.55f + horizon[2] * 0.15f;
        s.f3("uWallCol", (horizon[0] + lum) * 0.5f, (horizon[1] + lum) * 0.5f, (horizon[2] + lum) * 0.5f);
        Frame f = frame;
        if (Float.isNaN(f.fogX)) s.f3("uFogOrigin", f.camX, f.camY, f.camZ); else s.f3("uFogOrigin", f.fogX, f.fogY, f.fogZ);
    }

    // ------------------------------------------------------------------ forward
    private int[] fwdOrder = new int[64];
    private float[] fwdDist = new float[64];

    private void forwardPass(WorldRenderer wr, Frame f, WorldRenderer.Env e) {
        float density = e.fogDensity * 0.62f;
        glBindFramebuffer(GL_FRAMEBUFFER, hdrBFbo);
        glViewport(0, 0, rw, rh);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);
        // glass and water
        fWorld.use();
        fWorld.mat4("uPV", pvJ);
        fWorld.f1("uTime", e.time);
        fWorld.tex("uAtlas", 0, GL_TEXTURE_2D_ARRAY, atlas.texId);
        probeUniforms(fWorld, 1);
        fWorld.f3("uCam", f.camX, f.camY, f.camZ);
        fWorld.f3("uFogCol", fogCol[0], fogCol[1], fogCol[2]);
        fWorld.f3("uAmbient", ambient[0], ambient[1], ambient[2]);
        fWorld.f1("uDensity", density);
        fWorld.tex("uFogTex", 5, GL_TEXTURE_2D, fogTex);
        fWorld.f2("uFogScreen", rw, rh);
        fWorld.i1("uUseFog", q.volumetrics ? 1 : 0);
        fWorld.f1("uFogMax", 140f);
        fWorld.f1("uFogBase", World.GROUND);
        bankUniforms(fWorld, e);
        skyUniforms(fWorld, e);
        // 0.8, glass seen through glass. Ordinary blending needs what is drawn to be in order, far to near, and panes are only
        // in order chunk by chunk - within a chunk, and across a seam, a far pane could be laid over a near one, and which
        // way round it went changed as the camera moved: the textures swapped places. So glass is blended in a way that
        // does not care about order. Every pane first takes its share of the light behind it away (multiplying - the
        // product is the same whatever order they come in), then every pane adds its own colour (adding - likewise).
        glBlendFunc(GL_ZERO, GL_ONE_MINUS_SRC_ALPHA);
        wr.drawTranslucent(fWorld, frustum, f.camX, f.camZ, f.viewDistance);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        wr.drawTranslucent(fWorld, frustum, f.camX, f.camZ, f.viewDistance);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        // see-through and self-lit models
        fModel.use();
        fModel.mat4("uPV", pvJ);
        probeUniforms(fModel, 1);
        fModel.f3("uCam", f.camX, f.camY, f.camZ);
        fModel.f3("uFogCol", fogCol[0], fogCol[1], fogCol[2]);
        fModel.f3("uAmbient", ambient[0], ambient[1], ambient[2]);
        fModel.f1("uDensity", density);
        skyUniforms(fModel, e);
        bankUniforms(fModel, e);
        // 1.3: far to near. Blending over what is already there is only right in that order, and the see-through models were
        // drawn in the order they were listed - a glass door leaf beyond another was laid over the near one, so what was seen
        // through a glass door (another door, a shard) came out wrong and changed as the camera moved
        int nf = 0;
        if (fwdOrder.length < scene.count) { fwdOrder = new int[scene.count * 2]; fwdDist = new float[scene.count * 2]; }
        for (int k = 0; k < scene.count; k++) if ((scene.flag[k] & DrawList.FORWARD) != 0) {
            float dx = scene.mat[k * 16 + 12] - f.camX, dy = scene.mat[k * 16 + 13] - f.camY, dz = scene.mat[k * 16 + 14] - f.camZ;
            fwdDist[k] = dx * dx + dy * dy + dz * dz;
            fwdOrder[nf++] = k;
        }
        for (int i = 1; i < nf; i++) { int v = fwdOrder[i]; float dv = fwdDist[v]; int j = i - 1; while (j >= 0 && fwdDist[fwdOrder[j]] < dv) { fwdOrder[j + 1] = fwdOrder[j]; j--; } fwdOrder[j + 1] = v; }
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) glBlendFunc(GL_SRC_ALPHA, GL_ONE);
            for (int o = 0; o < nf; o++) {
                int k = fwdOrder[o];
                int fl = scene.flag[k];
                if ((fl & DrawList.FORWARD) == 0 || ((fl & DrawList.ADDITIVE) != 0) != (pass == 1)) continue;
                int p = k * 8;
                System.arraycopy(scene.mat, k * 16, m16, 0, 16);
                fModel.mat4("uModel", m16);
                fModel.f4("uTint", scene.par[p], scene.par[p + 1], scene.par[p + 2], scene.par[p + 3]);
                fModel.f1("uEmissive", scene.par[p + 4]);
                ModelRenderer.drawGeometry(scene.mesh[k]);
            }
        }
        // particles: premultiplied, so fire adds and smoke covers in one sorted-enough pass
        if (particles.count > 0) {
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            particle.use();
            particle.mat4("uPV", pvJ);
            float[] v = f.view.m;
            particle.f3("uRight", v[0], v[4], v[8]);
            particle.f3("uUp", v[1], v[5], v[9]);
            particle.tex("uNoise", 0, GL_TEXTURE_2D, atlas.noiseTex);
            particle.tex("uDepth", 1, GL_TEXTURE_2D, gDepth);
            particle.f2("uScreen", rw, rh);
            particle.f1("uTime", e.time);
            particle.f1("uNear", f.near);
            particle.f1("uFar", f.far);
            // depth is sampled in the shader for soft edges, so it cannot also be the depth attachment being tested
            glBindFramebuffer(GL_FRAMEBUFFER, hdrBColorFbo);
            glDisable(GL_DEPTH_TEST);
            particles.draw();
            glEnable(GL_DEPTH_TEST);
        }
        glDisable(GL_BLEND);
        glDepthMask(true);
    }

    // ------------------------------------------------------------------ TAA, bloom, exposure, tone map
    private void resolve(Frame f, WorldRenderer.Env e, int windowW, int windowH) {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        int cur = 1 - histIdx;
        glBindFramebuffer(GL_FRAMEBUFFER, histFbo[cur]);
        glViewport(0, 0, rw, rh);
        if (q.taa && historyValid && !f.reset) {
            taa.use();
            taa.tex("uColor", 0, GL_TEXTURE_2D, hdrB);
            taa.tex("uHistory", 1, GL_TEXTURE_2D, hist[histIdx]);
            taa.tex("uDepth", 2, GL_TEXTURE_2D, gDepth);
            taa.tex("uNormal", 3, GL_TEXTURE_2D, gNormal);
            taa.mat4("uInvPV", invPV);
            taa.mat4("uPrevPV", prevPV);
            taa.f2("uScreen", rw, rh);
            taa.f1("uBlend", 0.1f);
        } else {
            copy.use();
            copy.tex("uTex", 0, GL_TEXTURE_2D, hdrB);
        }
        fullscreen();
        histIdx = cur;
        int sceneTex = hist[cur];

        // bloom: threshold on the way down, tent filter on the way back up
        if (q.bloom) {
            bloomDown.use();
            for (int i = 0; i < BLOOM_LEVELS; i++) {
                glBindFramebuffer(GL_FRAMEBUFFER, bloomFbo[i]);
                glViewport(0, 0, bloomW[i], bloomH[i]);
                bloomDown.tex("uTex", 0, GL_TEXTURE_2D, i == 0 ? sceneTex : bloomTex[i - 1]);
                bloomDown.f2("uTexel", 1f / (i == 0 ? rw : bloomW[i - 1]), 1f / (i == 0 ? rh : bloomH[i - 1]));
                bloomDown.f1("uThreshold", i == 0 ? 1.35f : 0f);
                bloomDown.tex("uExposure", 1, GL_TEXTURE_2D, expoTex[expoIdx]);
                fullscreen();
            }
            glEnable(GL_BLEND);
            glBlendFunc(GL_ONE, GL_ONE);
            bloomUp.use();
            for (int i = BLOOM_LEVELS - 1; i > 0; i--) {
                glBindFramebuffer(GL_FRAMEBUFFER, bloomFbo[i - 1]);
                glViewport(0, 0, bloomW[i - 1], bloomH[i - 1]);
                bloomUp.tex("uTex", 0, GL_TEXTURE_2D, bloomTex[i]);
                bloomUp.f2("uTexel", 1f / bloomW[i], 1f / bloomH[i]);
                fullscreen();
            }
            glDisable(GL_BLEND);
        }

        // average scene luminance -> exposure that adapts over time
        glBindFramebuffer(GL_FRAMEBUFFER, lumFbo);
        glViewport(0, 0, 256, 256);
        lum.use();
        lum.tex("uTex", 0, GL_TEXTURE_2D, sceneTex);
        fullscreen();
        glBindTexture(GL_TEXTURE_2D, lumTex);
        glGenerateMipmap(GL_TEXTURE_2D);
        int ecur = 1 - expoIdx;
        glBindFramebuffer(GL_FRAMEBUFFER, expoFbo[ecur]);
        glViewport(0, 0, 1, 1);
        exposure.use();
        exposure.tex("uLum", 0, GL_TEXTURE_2D, lumTex);
        exposure.tex("uPrev", 1, GL_TEXTURE_2D, expoTex[expoIdx]);
        exposure.f1("uDt", Math.max(0.001f, f.dt));
        exposure.f1("uMin", 0.55f);
        exposure.f1("uMax", 5.0f);
        exposure.f1("uReset", historyValid && !f.reset ? 0 : 1);
        fullscreen();
        expoIdx = ecur;

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, windowW, windowH);
        if (debugView != 0) {
            copy.use();
            copy.tex("uTex", 0, GL_TEXTURE_2D, debugView == 1 ? ao : debugView == 2 ? ssrTex : debugView == 3 ? fogTex : debugView == 4 ? gAlbedo : gNormal);
            fullscreen();
            return;
        }
        tonemap.use();
        tonemap.tex("uColor", 0, GL_TEXTURE_2D, sceneTex);
        tonemap.tex("uBloom", 1, GL_TEXTURE_2D, bloomTex[0]);
        tonemap.tex("uExposure", 2, GL_TEXTURE_2D, expoTex[expoIdx]);
        tonemap.f1("uBloomStrength", q.bloom ? f.bloom : 0f);
        tonemap.f1("uTime", e.time);
        tonemap.f1("uDeath", f.death);
        tonemap.f1("uGrain", 0.018f);
        tonemap.f1("uFlash", f.flash);
        tonemap.f1("uVignette", 0.55f);
        tonemap.f1("uHurt", f.hurt);
        tonemap.f1("uInfection", f.infection);
        tonemap.f1("uBile", f.bile);
        tonemap.f1("uSharpen", q.taa ? 0.35f : 0f);
        tonemap.f2("uScreen", rw, rh);
        fullscreen();
    }

    public void dispose() {
        disposeTargets();
        probes.dispose();
        if (voxels != null) voxels.dispose();
        MemoryUtil.memFree(lightData);
    }
}
