package absentialis.scene;

import absentialis.engine.render.Screenshot;
import absentialis.entity.Actor;
import absentialis.gameplay.inventory.InventoryService;
import absentialis.gameplay.items.ItemInstance;
import absentialis.persistence.Profile;
import absentialis.persistence.SaveSystem;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Scripted visual verification by name (0.7): {@code ABS_SCENARIO=<name> ABS_SHOTS=<dir>} runs one short, focused
 * script against the real game - real window, real renderer, real input path - writes its screenshots and exits.
 * The long ABS_AUTOSHOT pass is the tour of everything; these are what a single change is checked with.
 *
 * A scenario is a list of timed steps on a timeline measured in frames from the moment its stage is reached
 * (the hideout, or a raid that has finished loading). Steps run on the main thread between frames.
 */
public final class Scenarios {
    private Scenarios() {}

    /** What a script can reach. */
    public interface Host {
        Ctx ctx();
        HideoutScreen hideout();
        RaidScreen raid();
        boolean inHideout();
        boolean inRaid();
        boolean inTitle();
        boolean inDebrief();
        boolean worldReady();
        void loadProfile(int slot);
        void deploy(int insertion, double hour);
        void quit();
        void console(String line);
    }

    private record Step(int frame, Consumer<Host> run) {}

    private static final List<Step> hideoutSteps = new ArrayList<>(), raidSteps = new ArrayList<>();
    private static String name, dir;
    private static int hideoutT = -1, raidT = -1, frame;
    private static boolean built, wantsRaid;
    public static int insertion;
    public static double hour = 12;

    public static boolean active() { return System.getenv("ABS_SCENARIO") != null && !System.getenv("ABS_SCENARIO").isBlank(); }

    public static void shot(Host h, String file) {
        var w = h.ctx().win;
        Screenshot.save(w.width, w.height, dir + "/" + file + ".png");
        System.out.println("[Scenario] shot " + file);
    }

    /**
     * Tiles a run of screenshots into one contact sheet: the frames named prefix + "_000", "_001" ... are cropped to
     * the box (x, y, w, h in screen pixels), scaled, and laid out cols to a row in order. A fall is thirty pictures;
     * this is how thirty pictures are looked at as one motion.
     */
    public static void sheet(String prefix, int cols, int x, int y, int w, int h, float scale) {
        try {
            List<java.awt.image.BufferedImage> frames = new ArrayList<>();
            for (int i = 0; ; i++) {
                java.io.File f = new java.io.File(dir, String.format("%s_%03d.png", prefix, i));
                if (!f.exists()) break;
                frames.add(javax.imageio.ImageIO.read(f));
            }
            if (frames.isEmpty()) return;
            int tw = Math.round(w * scale), th = Math.round(h * scale), rows = (frames.size() + cols - 1) / cols;
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(cols * tw, rows * th, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            for (int i = 0; i < frames.size(); i++) {
                java.awt.image.BufferedImage fr = frames.get(i);
                int cx = Math.max(0, Math.min(fr.getWidth() - w, x)), cy = Math.max(0, Math.min(fr.getHeight() - h, y));
                int dx = (i % cols) * tw, dy = (i / cols) * th;
                g.drawImage(fr, dx, dy, dx + tw, dy + th, cx, cy, cx + w, cy + h, null);
                g.setColor(java.awt.Color.YELLOW);
                g.drawString(String.valueOf(i), dx + 4, dy + 12);
            }
            g.dispose();
            javax.imageio.ImageIO.write(out, "png", new java.io.File(dir, prefix + "_sheet.png"));
            for (int i = 0; i < frames.size(); i++) new java.io.File(dir, String.format("%s_%03d.png", prefix, i)).delete();
            System.out.println("[Scenario] sheet " + prefix + " (" + frames.size() + " frames)");
        } catch (Exception e) { System.out.println("[Scenario] sheet failed: " + e); }
    }

    static void hideout(int f, Consumer<Host> r) { hideoutSteps.add(new Step(f, r)); }
    static void raid(int f, Consumer<Host> r) { raidSteps.add(new Step(f, r)); wantsRaid = true; }

    /** A fresh profile in slot 3 for the script to use (never one of the player's). */
    public static void profile(Consumer<Profile> setup) {
        Profile p = new Profile();
        p.slot = 3;
        p.name = "Scenario";
        p.starterKit();
        p.contracts.refill(p.seed, 1);
        if (setup != null) setup.accept(p);
        SaveSystem.save(p);
    }

    public static void give(Profile p, String id, int qty, boolean toLoadout) {
        ItemInstance it = new ItemInstance(id);
        it.qty = Math.max(1, Math.min(it.def().maxStack, qty));
        it.origin = "scenario";
        if (it.def().weapon != null && !it.def().weapon.melee()) { it.rounds = it.capacity(); it.ammoId = absentialis.gameplay.items.Content.ammoFor(it.def().weapon.caliber).id; }
        if (toLoadout) {
            String slot = it.def().slot;
            if (slot != null && p.loadout.slot(slot) == null && InventoryService.equip(it, p.loadout, slot).ok()) return;
            if (InventoryService.store(it, p.loadout).ok()) return;
        }
        InventoryService.add(it, p.stash);
    }

    public static void place(Raid r, float x, float y, float z, float yaw, float pitch) {
        Actor p = r.player;
        p.x = x; p.y = y; p.z = z; p.vx = p.vy = p.vz = 0; p.yaw = yaw; p.pitch = pitch;
    }

    /** One frame of the running script. */
    public static void step(Host h) {
        if (!built) {
            built = true;
            name = System.getenv("ABS_SCENARIO");
            dir = System.getenv("ABS_SHOTS");
            if (dir == null || dir.isBlank()) dir = "build/scenario";
            new java.io.File(dir).mkdirs();
            ScenarioScripts.build(name);
            System.out.println("[Scenario] " + name + ": " + hideoutSteps.size() + " hideout steps, " + raidSteps.size() + " raid steps -> " + dir);
        }
        frame++;
        if (h.inTitle()) { if (frame > 12 && (!wantsRaid || h.worldReady())) h.loadProfile(3); return; }
        if (h.inHideout()) {
            if (hideoutT < 0) { hideoutT = frame; if (!absentialis.gameplay.Alliance.chosen(h.ctx().profile) && !ScenarioScripts.keepUnaligned) { absentialis.gameplay.Alliance.choose(h.ctx().profile, "military"); h.hideout().enter(); } }
            int rel = frame - hideoutT;
            int last = 0;
            for (Step s : hideoutSteps) { last = Math.max(last, s.frame()); if (s.frame() == rel) s.run().accept(h); }
            if (rel > last + 4) {
                if (!wantsRaid) { h.quit(); return; }
                if (raidT < 0 && h.worldReady() && rel > last + 8 && !deployed) { deployed = true; h.deploy(insertion, hour); }
            }
            return;
        }
        if (h.inRaid()) {
            if (raidT < 0) raidT = frame;
            int rel = frame - raidT;
            int last = 0;
            for (Step s : raidSteps) { last = Math.max(last, s.frame()); if (s.frame() == rel) s.run().accept(h); }
            if (rel > last + 6) h.quit();
            return;
        }
        if (h.inDebrief()) h.quit();
        if (frame > 60000) h.quit();
    }

    private static boolean deployed;

    public static int g() { return World.GROUND; }
}
