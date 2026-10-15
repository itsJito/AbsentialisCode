package absentialis.director;

import static org.lwjgl.glfw.GLFW.*;

import absentialis.engine.Input;
import absentialis.entity.Actor;
import absentialis.scene.Raid;
import absentialis.scene.RaidScreen;
import java.util.ArrayList;
import java.util.List;

/** The two managers of Director Mode that stage the scene: what the actor does and says, and where it is seen from. */
public final class StageManagers {
    private StageManagers() {}

    // ================================================================== PerformanceManager: gestures and dialogue
    public static final class PerformanceManager {
        /** A gesture: its name, the clip it plays, how long, and whether it is something the infected do. */
        public record Gesture(String name, String clip, float seconds, boolean infected) {}
        public record Line(String group, String text, String voice) {}

        public static final Gesture[] GESTURES = {
                new Gesture("Wave", "gest_wave", 1.6f, false), new Gesture("Point", "gest_point", 1.4f, false), new Gesture("Hands up", "gest_surrender", 2.4f, false),
                new Gesture("Salute", "gest_salute", 1.5f, false), new Gesture("Beckon", "gest_beckon", 1.5f, false), new Gesture("Shrug", "gest_shrug", 1.3f, false),
                new Gesture("Stumble", "stumble", 0.8f, false), new Gesture("Flinch", "hit", 0.5f, false),
                new Gesture("Shriek", "inf_shriek", 0.8f, true), new Gesture("Listen", "glad_hark", 0.9f, true), new Gesture("Rear up", "claw_windup", 0.6f, true), new Gesture("Retch", "fat_retch", 1.0f, true)};

        public static final Line[] LINES = {
                new Line("Greeting", "Easy. I'm not here for trouble.", "bark_clear"), new Line("Greeting", "You're a long way from the shelters.", "bark_clear"),
                new Line("Warning", "That's far enough.", "bark_contact"), new Line("Warning", "Drop it. Now.", "bark_contact"), new Line("Warning", "This street is ours. Turn round.", "bark_contact"),
                new Line("Order", "On me. Move.", "bark_move"), new Line("Order", "Hold here. Eyes open.", "bark_cover"), new Line("Order", "Cover that door.", "bark_cover"),
                new Line("Alarm", "Contact! Front!", "bark_contact"), new Line("Alarm", "They're in the building!", "bark_contact"), new Line("Alarm", "Man down!", "bark_down"),
                new Line("Taunt", "Is that all of you?", "bark_taunt"), new Line("Taunt", "Come and take it.", "bark_taunt"),
                new Line("Quiet", "They say a town can die.", "bark_clear"), new Line("Quiet", "Dead places aren't supposed to breathe.", "bark_clear"), new Line("Quiet", "All records are to remain inside Absentialis.", "bark_clear")};

        private final RaidScreen screen;
        public String last = "";
        public int performed;

        PerformanceManager(RaidScreen screen) { this.screen = screen; }

        public List<Gesture> gestures(boolean infectedActor) { List<Gesture> l = new ArrayList<>(); for (Gesture g : GESTURES) if (g.infected() == infectedActor || !g.infected() && !infectedActor) l.add(g); return l; }
        public List<String> groups() { List<String> l = new ArrayList<>(); for (Line x : LINES) if (!l.contains(x.group())) l.add(x.group()); return l; }
        public List<Line> lines(String group) { List<Line> l = new ArrayList<>(); for (Line x : LINES) if (x.group().equals(group)) l.add(x); return l; }

        /** Plays a gesture on the actor, if it is one this actor can make. Null when it played, otherwise why not. */
        public String perform(Gesture g, boolean infectedActor) {
            Raid r = screen.raid;
            Actor p = r.player;
            if (g.infected() && !infectedActor) return "Only the infected do that";
            if (!g.infected() && infectedActor && !g.clip().equals("stumble") && !g.clip().equals("hit")) return "The infected do not make that gesture";
            if (p.anim == null || absentialis.character.Anims.get(g.clip()) == null) return "This actor has no such movement";
            if (r.vehicles.inside()) return "Not from inside a vehicle";
            p.anim.play(g.clip(), g.seconds());
            last = g.name(); performed++;
            return null;
        }

        /** The actor says a line: it is heard, it is shown over their head, and anyone near enough hears a voice. */
        public String speak(Line l, boolean infectedActor) {
            Raid r = screen.raid;
            if (infectedActor) return "The infected do not speak";
            r.player.lastSay = -99;                                     // the director's line is not subject to the chatter limit
            r.say(r.player, l.text(), l.voice());
            if (screen.ctx().audio != null && screen.ctx().audio.available) screen.ctx().audio.play(screen.ctx().audio.variant("shout", performed), 0.5f, 0, 1f, absentialis.engine.audio.UltraEngine.VOICE);
            last = l.text(); performed++;
            return null;
        }
    }

    // ================================================================== CameraManager: where the scene is seen from
    public static final class CameraManager {
        public static final String[] MODES = {"Gameplay", "Free camera", "Orbit", "Fixed"};
        public static final int GAMEPLAY = 0, FREE = 1, ORBIT = 2, FIXED = 3;
        /** How long a move from one camera to another takes; how fast the free camera flies; how the orbit is set. */
        public static float TRANSITION = 0.6f, FLY = 7f;
        private final RaidScreen screen;
        public int mode;
        public float fov = 0, orbitRadius = 4.5f, orbitHeight = 1.4f, orbitSpeed = 0.25f;
        private final float[] cam = new float[6], from = new float[6];
        private float blend = 1, orbitAngle;
        private boolean have;

        CameraManager(RaidScreen screen) { this.screen = screen; }

        public String modeName() { return MODES[mode]; }
        public boolean detached() { return mode != GAMEPLAY; }

        /** Changes camera. The picture moves from where it was to where it is going over {@link #TRANSITION} seconds rather than cutting. */
        public void setMode(int m) {
            m = Math.floorMod(m, MODES.length);
            if (m == mode) return;
            Actor p = screen.raid.player;
            if (!have) { cam[0] = p.x; cam[1] = p.eyeY(); cam[2] = p.z; cam[3] = p.yaw; cam[4] = p.pitch; have = true; }
            System.arraycopy(cam, 0, from, 0, 6);
            if (mode == GAMEPLAY) { from[0] = p.x - (float) -Math.sin(p.yaw) * 2.2f; from[1] = p.eyeY() + 0.3f; from[2] = p.z - (float) -Math.cos(p.yaw) * 2.2f; from[3] = p.yaw; from[4] = p.pitch; }
            if (m == FREE || m == FIXED) { cam[0] = from[0]; cam[1] = from[1]; cam[2] = from[2]; cam[3] = from[3]; cam[4] = from[4]; }
            if (m == ORBIT) orbitAngle = p.yaw + (float) Math.PI;
            mode = m;
            blend = 0;
        }

        public void fov(float degrees) { fov = degrees <= 1 ? 0 : Math.max(20, Math.min(110, degrees)); }

        /**
         * Each frame. In the free camera the movement keys fly the camera and the actor stands still ({@code fly} is
         * true while the menu is shut); the orbit turns round the actor by itself; a fixed camera stays where it was
         * left and keeps the actor in the picture only if it was pointed at them.
         */
        void update(Input in, float dt, boolean fly) {
            Actor p = screen.raid.player;
            blend = Math.min(1, blend + dt / Math.max(0.05f, TRANSITION));
            if (mode == GAMEPLAY) { screen.camOverride = null; if (fov > 1) screen.camOverride = null; return; }
            if (mode == FREE && fly) {
                cam[3] -= (float) in.dx * 0.0022f; cam[4] = Math.max(-1.5f, Math.min(1.5f, cam[4] - (float) in.dy * 0.0022f));
                float f = (in.down("forward") ? 1 : 0) - (in.down("back") ? 1 : 0), s = (in.down("right") ? 1 : 0) - (in.down("left") ? 1 : 0);
                float fx = (float) (-Math.sin(cam[3]) * Math.cos(cam[4])), fy = (float) Math.sin(cam[4]), fz = (float) (-Math.cos(cam[3]) * Math.cos(cam[4]));
                float sp = FLY * (in.down("sprint") ? 3.5f : 1) * dt;
                cam[0] += (fx * f + (float) Math.cos(cam[3]) * s) * sp; cam[1] += fy * f * sp + (in.down("jump") ? sp : 0) - (in.down("crouch") ? sp : 0); cam[2] += (fz * f - (float) Math.sin(cam[3]) * s) * sp;
                if (in.keyPressed(GLFW_KEY_EQUAL)) fov(fov <= 1 ? 60 : fov - 5);
                if (in.keyPressed(GLFW_KEY_MINUS)) fov(fov <= 1 ? 80 : fov + 5);
            }
            if (mode == ORBIT) {
                orbitAngle += orbitSpeed * dt;
                cam[0] = p.x + (float) Math.sin(orbitAngle) * orbitRadius; cam[2] = p.z + (float) Math.cos(orbitAngle) * orbitRadius; cam[1] = p.y + orbitHeight;
                float dx = p.x - cam[0], dy = p.y + 1.2f - cam[1], dz = p.z - cam[2], hl = (float) Math.sqrt(dx * dx + dz * dz);
                cam[3] = (float) Math.atan2(-dx, -dz); cam[4] = (float) Math.atan2(dy, hl);
            }
            float k = blend * blend * (3 - 2 * blend);
            float[] out = screen.camOverride != null && screen.camOverride.length == 6 ? screen.camOverride : new float[6];
            for (int i = 0; i < 3; i++) out[i] = from[i] + (cam[i] - from[i]) * k;
            float dyaw = cam[3] - from[3];
            while (dyaw > Math.PI) dyaw -= 2 * Math.PI;
            while (dyaw < -Math.PI) dyaw += 2 * Math.PI;
            out[3] = from[3] + dyaw * k; out[4] = from[4] + (cam[4] - from[4]) * k; out[5] = fov;
            screen.camOverride = out;
        }

        /** True while the free camera has the movement keys (so the actor does not walk off while the camera is being placed). */
        public boolean holdsControls() { return mode == FREE; }
        public float[] position() { return cam.clone(); }
        void restore() { mode = GAMEPLAY; fov = 0; have = false; blend = 1; screen.camOverride = null; }
    }
}
