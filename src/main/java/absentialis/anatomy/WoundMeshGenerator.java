package absentialis.anatomy;

import absentialis.character.Rig;
import absentialis.engine.render.ModelRenderer;
import absentialis.engine.voxel.VoxModel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Turns damaged anatomy into what is drawn. Wounds are geometry: the mesh of a region is rebuilt from that
 * region's voxels as they now are - holes where voxels were destroyed, raw tissue, bone and organ where the
 * surface is gone, discoloured skin where it was only hurt - at the model's own voxel resolution.
 *
 * Rebuilding is local and off the main thread: only the region that changed is remeshed, its voxels are
 * snapshotted and the vertex data is generated on a worker; the finished vertices are uploaded on the render
 * thread on a later frame. Until then the region keeps the mesh it had, so a hit never stalls a frame and never
 * shows an unwounded body in between. A region that has not been touched draws from the shared model of its look.
 */
public final class WoundMeshGenerator {
    private record Done(String key, int version, float[] verts, int count, boolean translucent) {}

    private final ModelRenderer mr;
    private final Map<String, Integer> built = new HashMap<>(), requested = new HashMap<>();
    private final ConcurrentLinkedQueue<Done> done = new ConcurrentLinkedQueue<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "wound-mesher"); t.setDaemon(true); t.setPriority(Thread.NORM_PRIORITY - 1); return t; });
    public int rebuilt, pending;
    /** Uploads allowed per frame: more than this many regions changing at once spread over the next frames. */
    public int uploadBudget = 10;

    public WoundMeshGenerator(ModelRenderer mr) { this.mr = mr; }

    /**
     * The mesh for a damaged region, or null when none has been built yet for this key (the caller then draws the
     * shared, undamaged mesh for one more frame). Schedules a rebuild when the region's version has moved on.
     */
    public ModelRenderer.Mesh mesh(String key, VoxelBody.Chunk c) {
        Integer have = built.get(key);
        used.put(key, frame);
        if (have == null) {
            // the first mesh of a region is built at once: a limb that has just come off must never be drawn whole
            float[] o = Rig.ORIGIN[c.group];
            boolean[] see = new boolean[1];
            int[] count = new int[1];
            float[] v = ModelRenderer.vertices(c.toModel(), 1f, -o[0], -o[1], -o[2], count, see);
            mr.replace(key, v, count[0], see[0]);
            built.put(key, c.version);
            requested.put(key, c.version);
            rebuilt++;
            return mr.cached(key);
        }
        if (have != c.version) {
            Integer asked = requested.get(key);
            if (asked == null || asked != c.version) {
                requested.put(key, c.version);
                pending++;
                // snapshot on this thread: the simulation may change the chunk again before the worker runs
                final VoxModel snap = c.toModel();
                final int version = c.version;
                final float[] o = Rig.ORIGIN[c.group];
                worker.submit(() -> {
                    try {
                        boolean[] see = new boolean[1];
                        int[] count = new int[1];
                        float[] v = ModelRenderer.vertices(snap, 1f, -o[0], -o[1], -o[2], count, see);
                        done.add(new Done(key, version, v, count[0], see[0]));
                    } catch (Throwable t) {
                        done.add(new Done(key, version, new float[0], 0, false));
                    }
                });
            }
        }
        return have == null ? null : mr.cached(key);
    }

    private final Map<String, Integer> used = new HashMap<>();
    private int frame;

    /** Uploads finished meshes (render thread, once a frame), and now and then frees the ones nothing draws any more. */
    public void pump() {
        frame++;
        if (frame % 600 == 0) {
            java.util.List<String> stale = new java.util.ArrayList<>();
            for (Map.Entry<String, Integer> e : used.entrySet()) if (frame - e.getValue() > 600) stale.add(e.getKey());
            for (String k : stale) { used.remove(k); built.remove(k); requested.remove(k); mr.drop(k); }
        }
        Done d;
        int n = 0;
        while (n < uploadBudget && (d = done.poll()) != null) {
            pending = Math.max(0, pending - 1);
            Integer have = built.get(d.key);
            if (have == null || have > d.version) continue;      // swept meanwhile, or a newer build already landed
            mr.replace(d.key, d.verts, d.count, d.translucent);
            built.put(d.key, d.version);
            rebuilt++;
            n++;
        }
    }

    /** Frees the meshes of a body that has left the world. keys are those used with {@link #mesh}. */
    public void release(String prefix) {
        java.util.List<String> gone = new java.util.ArrayList<>();
        for (String k : built.keySet()) if (k.startsWith(prefix)) gone.add(k);
        for (String k : gone) { built.remove(k); requested.remove(k); mr.drop(k); }
    }

    public int meshes() { return built.size(); }
    public void shutdown() { worker.shutdownNow(); }
}
