package absentialis.tools;

import static org.lwjgl.opengl.GL11.*;

import absentialis.engine.Window;
import absentialis.engine.math.Mat4;
import absentialis.engine.render.Atlas;
import absentialis.engine.render.Screenshot;
import absentialis.engine.render.WorldRenderer;
import absentialis.world.World;
import absentialis.world.gen.CityGenerator;

/** Dev tool (ABS_PREVIEW=dir): generates the Zone and renders fixed camera shots to PNG files. */
public final class MapPreview {
    public static void run(Window win, String dir) {
        World w = new World();
        long t = System.currentTimeMillis();
        new CityGenerator(w, 1337L, s -> System.out.println("[Gen] " + s)).generate();
        long t1 = System.currentTimeMillis();
        w.lighting.computeAll();
        System.out.println("[Light] " + (System.currentTimeMillis() - t1) + " ms");
        Atlas atlas = new Atlas();
        atlas.paintAll();
        atlas.upload();
        atlas.dumpPng(dir + "/atlas.png");
        absentialis.engine.render.ChunkMesher.drawDoors = true;
        WorldRenderer wr = new WorldRenderer(w, atlas);
        wr.init();
        java.util.Arrays.fill(w.dirtyColumns, true);
        w.anyDirty = true;
        long t2 = System.currentTimeMillis();
        wr.scheduleDirty();
        while (wr.busy()) { wr.uploadResults(0); try { Thread.sleep(5); } catch (InterruptedException e) { break; } }
        System.out.println("[Mesh] " + (System.currentTimeMillis() - t2) + " ms, total " + (System.currentTimeMillis() - t) + " ms");
        float[][] shots = {
                // x, y, z, yawDeg, pitchDeg
                {388, 80, 356, 0, -38},      // 0 power plant from over the canal
                {300, 30, 346, 20, 6},       // 1 cooling towers from the embankment
                {364, 26.6f, 331, 0, 14},    // 2 Energetik Square looking at the reactor blocks
                {200, 70, 262, 190, -28},    // 3 Downtown aerial
                {170, 25.6f, 430, 150, -3},  // 4 Residential street
                {352, 60, 470, 0, -22},      // 5 Government quarter toward the canal
                {330, 60, 520, 220, -20},    // 6 Untamed / hospital
                {650, 40, 180, 270, -8},     // 7 wilderness highway looking at the gate
                {250, 26, 320, 180, 0},      // 8 west ring avenue: street dressing
                {352, 95, 352, 0, -89},      // 9 plan view of the centre
                {430, 28, 350, 0, 16},       // 10 the amusement park from the embankment
                {402, 26.6f, 327, -90, 14},  // 11 the Ferris wheel from Energetik Square
        };
        String extra = System.getenv("ABS_PREVIEW_SHOTS");   // "x,y,z,yawDeg,pitchDeg;..." replaces the fixed list
        if (extra != null && !extra.isBlank()) {
            String[] parts = extra.split(";");
            shots = new float[parts.length][5];
            for (int i = 0; i < parts.length; i++) { String[] v = parts[i].trim().split(","); for (int k = 0; k < 5; k++) shots[i][k] = Float.parseFloat(v[k].trim()); }
        }
        WorldRenderer.Env e = new WorldRenderer.Env();
        Mat4 proj = new Mat4(), view = new Mat4(), pv = new Mat4();
        for (int i = 0; i < shots.length; i++) {
            float[] s = shots[i];
            float yaw = (float) Math.toRadians(s[3]), pitch = (float) Math.toRadians(s[4]);
            float fx = (float) (-Math.sin(yaw) * Math.cos(pitch)), fy = (float) Math.sin(pitch), fz = (float) (-Math.cos(yaw) * Math.cos(pitch));
            e.camX = s[0]; e.camY = s[1]; e.camZ = s[2]; e.dirX = fx; e.dirY = fy; e.dirZ = fz;
            e.daylight = 1; e.sunX = 0.4f; e.sunY = 0.75f; e.sunZ = 0.3f; e.time = 10;
            e.fogDensity = 1f / 520f;
            float aspect = win.width / (float) win.height;
            proj.perspective((float) Math.toRadians(70), aspect, 0.1f, 1000f);
            view.identity().rotateX(-pitch).rotateY(-yaw).translate(-s[0], -s[1], -s[2]);
            pv.set(proj).mul(view);
            glViewport(0, 0, win.width, win.height);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            float[] fwd = {fx, fy, fz};
            float[] right = {(float) Math.cos(yaw), 0, (float) -Math.sin(yaw)};
            float[] up = {right[1] * fz - right[2] * fy, right[2] * fx - right[0] * fz, right[0] * fy - right[1] * fx};
            wr.viewDistance = 700;
            wr.renderSky(e, fwd, right, up, (float) Math.tan(Math.toRadians(35)), aspect);
            wr.render(pv, e);
            Screenshot.save(win.width, win.height, dir + "/shot" + i + ".png");
            win.swap();
            win.poll();
        }
        wr.shutdown();
    }
}
