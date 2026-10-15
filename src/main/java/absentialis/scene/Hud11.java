package absentialis.scene;

import absentialis.entity.Actor;
import absentialis.entity.Vital;
import absentialis.gameplay.CombatConfig;
import absentialis.gameplay.CombatConfig.Condition;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.ui.Layout;
import absentialis.ui.Layout.Box;
import absentialis.ui.Ui;

/**
 * The 1.1 heads-up display. The 1.0 HUD was one plate in the lower right with everything on it - portrait, health in
 * figures, ammunition, the weapon, four meters and a divider - and a persistent rank panel top left. This is that
 * information taken apart and put where each piece is looked for:
 *
 *   lower right .... the weapon, what is in it, and the health arc: the three things a fight is run on. The loaded
 *                    count is the largest thing on the screen's edge (38 px at 1080p), full white; the reserve is
 *                    half its size and grey; an empty weapon is red. Health is the arc and, where "100 / 100" used
 *                    to be, a monitor trace: it beats faster and smaller as the body fails and says so in a word.
 *   lower left ..... stamina and focus - and food and water only when they are low, changing, or asked for.
 *   top centre ..... experience: what was earned, and (after a kill only) how far along the Rank is, for three
 *                    seconds. A promotion is announced there, properly.
 *   top left ....... the routine notices, one at a time, and the kill feed directly under them.
 *   top right ...... the map, quietened: a desaturated sheet, a thin frame, the survivor's arrow and the way out.
 *
 * Nothing sits on a slab. Each group has a soft dark gradient of its own behind it, text has a one-pixel shadow, and
 * every stroke is one weight. One sans-serif family throughout with numerals set to a fixed pitch, so a counter does
 * not jitter sideways as it counts. What is not changing fades after three seconds (a 200 ms transition) and comes
 * back the moment it does; a warning never fades. "Always show HUD", the HUD's scale and the safe area are options.
 */
public final class Hud11 {
    private final Ctx c;
    private final Raid raid;
    private final StatusHud status;
    private final CombatConfig cfg = CombatConfig.get();

    /** Seconds an indicator stays after it last changed, and how long it takes to come or go. */
    public static float IDLE_AFTER = 3f, FADE = 0.2f;
    /** Text sizes at 1080p (the brief's figures): loaded count, reserve, fire mode, labels. */
    public static final float LOADED_PX = 38, RESERVE_PX = 19, MODE_PX = 15, LABEL_PX = 16;

    /** Eased visibility of each group, 0..1 (gates read them): weapon, health, stamina, focus, food, water. */
    public float aWeapon = 1, aHealth = 1, aStamina, aFocus, aFood, aWater;
    /** Until when everything is shown because it was asked for (the status key). */
    public float peekUntil = -99;
    private float lastAmmoChange = -99, lastHealthChange = -99, lastFoodChange = -99, lastWaterChange = -99, shownLoaded = -1, shownReserve = -1, shownHp = -1, shownFood = -1, shownWater = -1, heldFor;
    private String shownWeapon = "";
    /** Where the groups were drawn last frame (gates: nothing leaves the screen, nothing overlaps). */
    public Box weaponBox = new Box(0, 0, 0, 0), vitalsBox = new Box(0, 0, 0, 0);
    private float saved = 1;

    Hud11(Ctx c, Raid raid, StatusHud status) { this.c = c; this.raid = raid; this.status = status; }

    /** The HUD's own scale: the interface scale, the HUD option, and the height of the screen against 1080. */
    public float scale(Ui ui) { return Math.max(0.5f, c.settings.uiScale * c.settings.hudScale * ui.h / 1080f); }
    /** The margin kept clear at every edge (pixels). */
    public float safe(Ui ui) { return Math.round(ui.h * c.settings.hudSafe); }

    /** Everything between begin and end is laid out in HUD units. */
    public void begin(Ui ui) { saved = ui.scale; ui.scale = scale(ui); }
    public void end(Ui ui) { ui.scale = saved; }

    // ------------------------------------------------------------------ drawing helpers
    /** A soft dark patch behind a group: darkest in the middle, gone at the edges - no slab, no border. */
    public static void shade(Ui ui, float x, float y, float w, float h, float alpha) {
        if (alpha <= 0.01f) return;
        // (looked at: the first version stacked six shrinking rectangles, and read as six rectangles - a slab with steps
        // in it. It is one quad now, of a texture whose darkness falls away smoothly to nothing at every edge.)
        if (softTex == 0) softTex = soft();
        float gx = Math.min(w * 0.18f, ui.s(40)), gy = Math.min(h * 0.3f, ui.s(18));
        ui.b.image(softTex, Math.round(x - gx), Math.round(y - gy), Math.round(w + gx * 2), Math.round(h + gy * 2), Ui.alpha(0xFF040505, alpha * 0.62f));
    }
    private static int softTex;

    /** A 64 x 64 texture, white, whose alpha is 1 in the middle and eases to 0 at every edge. */
    private static int soft() {
        int n = 64;
        java.nio.ByteBuffer bb = org.lwjgl.system.MemoryUtil.memAlloc(n * n * 4);
        for (int v = 0; v < n; v++) for (int u = 0; u < n; u++) {
            float fu = (u + 0.5f) / n, fv = (v + 0.5f) / n;
            float du = Math.min(1, Math.min(fu, 1 - fu) / 0.34f), dv = Math.min(1, Math.min(fv, 1 - fv) / 0.42f);
            du = du * du * (3 - 2 * du); dv = dv * dv * (3 - 2 * dv);
            bb.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) Math.round(255 * du * dv));
        }
        bb.flip();
        int t = org.lwjgl.opengl.GL11.glGenTextures();
        org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, t);
        org.lwjgl.opengl.GL11.glTexImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_RGBA8, n, n, 0, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, bb);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        org.lwjgl.system.MemoryUtil.memFree(bb);
        return t;
    }

    /** Text with a one-pixel shadow under it (it has to read over bright concrete and in a dark room alike). */
    public static float txt(Ui ui, String s, float x, float y, float size, int argb) {
        float a = ((argb >>> 24) & 255) / 255f;
        ui.text(s, x + 1, y + 1, size, Ui.alpha(0xFF000000, a * 0.7f));
        return ui.text(s, x, y, size, argb);
    }
    public static void txtR(Ui ui, String s, float rx, float y, float size, int argb) { txt(ui, s, rx - ui.tw(s, size), y, size, argb); }
    public static void txtC(Ui ui, String s, float cx, float y, float size, int argb) { txt(ui, s, cx - ui.tw(s, size) / 2, y, size, argb); }

    /** The width of one figure at a size: every figure is set on this pitch. */
    public static float pitch(Ui ui, float size) { float m = 0; for (char ch = '0'; ch <= '9'; ch++) m = Math.max(m, ui.tw(String.valueOf(ch), size)); return (float) Math.ceil(m); }
    public static float numW(Ui ui, String s, float size) { float p = pitch(ui, size), w = 0; for (int i = 0; i < s.length(); i++) { char ch = s.charAt(i); w += ch >= '0' && ch <= '9' ? p : ui.tw(String.valueOf(ch), size); } return w; }

    /** Numerals on a fixed pitch, right-aligned at rx: a counter that counts does not move sideways. */
    public static void numR(Ui ui, String s, float rx, float y, float size, int argb) {
        float p = pitch(ui, size), x = rx - numW(ui, s, size);
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            boolean digit = s.charAt(i) >= '0' && s.charAt(i) <= '9';
            float w = digit ? p : ui.tw(ch, size);
            txt(ui, ch, x + (digit ? (p - ui.tw(ch, size)) / 2 : 0), y, size, argb);
            x += w;
        }
    }

    private float ease(float a, boolean on, float dt) { float step = dt / FADE; return on ? Math.min(1, a + step) : Math.max(0, a - step); }

    // ------------------------------------------------------------------ lower right: weapon, ammunition, health
    /**
     * The monitor trace: one heartbeat drawn as a monitor draws it - a small rise, the spike, the slower wave after -
     * scrolling right to left, the newest beat bright and the rest fading behind it. Rate and height follow the body.
     */
    void trace(Ui ui, float x, float y, float w, float h, float bpm, float strength, int col, float alpha, float t) {
        float beats = 2.6f, th = Math.max(2f, ui.s(2.4f)), py = y + h * 0.62f, lastX = x, lastY = py;
        int n = Math.max(48, (int) (w / 1.25f));
        ui.b.rect(x, py, w, 1, Ui.alpha(col, alpha * 0.16f));
        for (int i = 1; i <= n; i++) {
            float k = i / (float) n, ph = (t * bpm / 60f - (1 - k) * beats);
            ph -= (float) Math.floor(ph);
            float v = wave(ph) * strength, px = x + w * k, yy = py - v * h * 0.6f;
            float fade = 0.25f + 0.75f * k * k;
            ui.b.line(lastX, lastY, px, yy, th, Ui.alpha(col, alpha * fade));
            lastX = px; lastY = yy;
        }
        ui.b.rect(lastX - ui.s(2), lastY - ui.s(2), ui.s(4), ui.s(4), Ui.alpha(0xFFFFFFFF, alpha));
    }

    /** One beat, 0..1: P wave, QRS complex, T wave. */
    static float wave(float p) {
        if (p < 0.10f) return 0;
        if (p < 0.20f) return 0.12f * (float) Math.sin((p - 0.10f) / 0.10f * Math.PI);
        if (p < 0.26f) return 0;
        if (p < 0.29f) return -0.18f * (p - 0.26f) / 0.03f;
        if (p < 0.33f) return -0.18f + 1.18f * (p - 0.29f) / 0.04f;
        if (p < 0.37f) return 1.0f - 1.32f * (p - 0.33f) / 0.04f;
        if (p < 0.40f) return -0.32f + 0.32f * (p - 0.37f) / 0.03f;
        if (p < 0.52f) return 0;
        if (p < 0.70f) return 0.22f * (float) Math.sin((p - 0.52f) / 0.18f * Math.PI);
        return 0;
    }

    /** The heart rate shown for a body: resting in the sixties, up with exertion and with loss of blood, thready when it is failing. */
    public float bpm(Actor p) {
        float hurt = 1 - Math.max(0, Math.min(1, p.hp / Math.max(1f, p.maxHp))), wind = 1 - raid.body.stamina / 100f;
        if (p.dead) return 0;
        if (p.downed()) return 128 + 14 * (float) Math.sin(raid.time * 0.9f);
        return 62 + 58 * hurt + 46 * wind + (raid.body.bleedHeavy ? 14 : raid.body.bleedLight ? 6 : 0);
    }

    public void weapon(Ui ui, ItemInstance held, float reloadT, float reloadMax, ActorRenderer.Look look, float ammoShown, float dt) {
        Actor p = raid.player;
        float t = raid.time, m = safe(ui);
        boolean gun = held != null && held.def().weapon != null && !held.def().weapon.melee();
        int loaded = gun ? held.loadedRounds() : 0, res = gun ? StatusHud.reserve(raid, held) : 0;
        String wid = held == null ? "" : held.defId;
        if (loaded != shownLoaded || res != shownReserve || !wid.equals(shownWeapon)) { lastAmmoChange = t; shownLoaded = loaded; shownReserve = res; shownWeapon = wid; }
        if (Math.abs(p.hp - shownHp) > 0.4f) { lastHealthChange = t; shownHp = p.hp; }
        boolean always = c.settings.hudAlways || t < peekUntil;
        Condition cond = cfg.condition(p.hp, p.maxHp);
        boolean hurt = cond.ordinal() >= Condition.INJURED.ordinal() || p.downed() || raid.body.bleedLight || raid.body.bleedHeavy;
        // the weapon and the arc stay: they are what a fight is read from. They only dim a little when nothing is happening
        boolean busy = always || t - lastAmmoChange < IDLE_AFTER || t - ammoShown < IDLE_AFTER || reloadT > 0 || raid.playerAiming || gun && loaded == 0;
        aWeapon += ((busy ? 1f : 0.62f) - aWeapon) * Math.min(1, dt / FADE);
        aHealth += ((always || hurt || t - lastHealthChange < IDLE_AFTER ? 1f : 0.7f) - aHealth) * Math.min(1, dt / FADE);

        float r = ui.s(50), cx = ui.w - m - ui.s(34) - r, cy = ui.h - m - ui.s(74) - r;
        float right = cx - r - ui.s(30), left = right - ui.s(330), top = cy - r - ui.s(6), bottom = ui.h - m;
        weaponBox = new Box(left, top, ui.w - m - left, bottom - top);
        status.moduleBox = weaponBox;
        shade(ui, left - ui.s(30), top - ui.s(6), ui.w - left + ui.s(30), bottom - top + ui.s(10), Math.max(aWeapon, aHealth));

        // ---- health: the arc round the portrait, and the monitor under it
        status.gauge(ui, p, look, cx, cy, r, t);
        int hc = p.downed() ? cfg.color(Condition.CRITICAL) : cfg.color(cond);
        float beat = bpm(p), strength = p.dead ? 0 : p.downed() ? 0.45f : 0.55f + 0.45f * Math.max(0, Math.min(1, p.hp / Math.max(1f, p.maxHp)));
        float tw = r * 2 + ui.s(36), tx = cx - r - ui.s(18), ty = cy + r + ui.s(2);
        trace(ui, tx, ty, tw, ui.s(34), beat, strength, hc, aHealth, t);
        String word = p.downed() ? (p.vital == Vital.REVIVING ? "BEING REVIVED" : "DOWN  " + Math.max(0, (int) Math.ceil(p.bleedLeft)) + "s") : cond.label.toUpperCase();
        // the state is said in a word as well as shown in a colour, with a mark beside it when it is anything but well
        float ww = ui.tw(word, LABEL_PX) + (hurt ? ui.s(18) : 0), wx = cx - ww / 2;
        if (hurt) { cross(ui, wx, ty + ui.s(39), ui.s(13), Ui.alpha(hc, aHealth)); wx += ui.s(18); }
        txt(ui, word, wx, ty + ui.s(35), LABEL_PX, Ui.alpha(hc, aHealth));

        // ---- the weapon and what is in it
        float a = aWeapon;
        if (gun) {
            String big = String.valueOf(loaded), small = String.valueOf(res);
            float bigPx = LOADED_PX, smallPx = RESERVE_PX;
            float base = cy - ui.s(30);
            // the round count: what is in the weapon, full white - red when there is nothing in it; what is carried, grey
            int bigCol = loaded == 0 ? 0xFFE8463A : 0xFFFFFFFF, smallCol = res == 0 ? 0xFFE8463A : 0xFF9AA09A;
            float sw = numW(ui, small, smallPx) + ui.s(4);
            numR(ui, small, right, base + ui.s(17), smallPx, Ui.alpha(smallCol, a));
            // a thin stroke between the two, the same weight as every other stroke on the HUD
            ui.b.line(right - sw - ui.s(9), base + ui.s(34), right - sw - ui.s(3), base + ui.s(10), Math.max(1, ui.s(1.2f)), Ui.alpha(0xFF9AA09A, a * 0.8f));
            numR(ui, big, right - sw - ui.s(14), base, bigPx, Ui.alpha(bigCol, a));
            float used = sw + ui.s(14) + numW(ui, big, bigPx) + ui.s(16);
            status.icon(ui, held, left, base + ui.s(2), Math.min(ui.s(150), right - left - used), ui.s(42), Ui.alpha(0xFFFFFFFF, a));
            // under them: the fire mode, the weapon by name, and - when there is nothing left to load - what there is none of
            float ny = base + ui.s(50);
            String mode = held.fireModeName().toUpperCase();
            ItemDef am = held.def().weapon.caliber == null ? null : Content.ammoFor(held.def().weapon.caliber);
            String none = res > 0 ? null : "NO " + (am == null ? "AMMUNITION" : am.name.toUpperCase());
            txtR(ui, mode, right, ny, MODE_PX, Ui.alpha(0xFFD8D4C6, a));
            float mw = ui.tw(mode, MODE_PX) + ui.s(14);
            float nameSize = Layout.fitSize(ui, held.def().name, right - left - mw, LABEL_PX, 12);
            txt(ui, Layout.elide(ui, held.def().name, right - left - mw, nameSize), left, ny, nameSize, Ui.alpha(0xFFF4EEDC, a));
            if (none != null) txtR(ui, none, right, ny + ui.s(24), 14, Ui.alpha(0xFFE8463A, Math.max(a, 0.9f)));
            // condition, or the reload as it happens: one thin line
            float by = ny + ui.s(24), bw = none != null ? right - left - ui.tw(none, 14) - ui.s(12) : right - left;
            ui.b.rect(left, by + ui.s(7), bw, Math.max(1, ui.s(2)), Ui.alpha(0xFF1A1C1B, a * 0.9f));
            if (reloadT > 0) ui.b.rect(left, by + ui.s(7), bw * (1 - reloadT / Math.max(0.01f, reloadMax)), Math.max(1, ui.s(2)), Ui.alpha(0xFFE2A034, 1));
            else { float c2 = held.condition() / 100f; ui.b.rect(left, by + ui.s(7), bw * c2, Math.max(1, ui.s(2)), Ui.alpha(c2 < 0.25f ? 0xFFE8463A : c2 < 0.5f ? 0xFFE2A034 : 0xFF8A9080, a)); }
            if (held.jammed) { String j = "JAMMED  -  [R] CLEAR"; float jw = ui.tw(j, LABEL_PX) + ui.s(30); shade(ui, right - jw, top - ui.s(34), jw + ui.s(10), ui.s(34), 1); cross(ui, right - jw + ui.s(4), top - ui.s(24), ui.s(12), 0xFFE8463A); txtR(ui, j, right, top - ui.s(27), LABEL_PX, 0xFFE8463A); }
        } else if (held != null) {
            float base = cy - ui.s(18);
            status.icon(ui, held, right - ui.s(150), base, ui.s(150), ui.s(42), Ui.alpha(0xFFFFFFFF, a));
            txtR(ui, held.def().name, right, base + ui.s(48), LABEL_PX, Ui.alpha(0xFFF4EEDC, a));
        } else txtR(ui, p.downed() ? "NO SIDEARM" : "UNARMED", right, cy - ui.s(4), LABEL_PX, Ui.alpha(0xFF9AA09A, a));
    }

    /** The mark that goes with a warning: a small cross, drawn - a cue that does not depend on telling red from amber. */
    static void cross(Ui ui, float x, float y, float s, int argb) {
        float t = Math.max(2, s * 0.3f);
        ui.b.rect(Math.round(x + (s - t) / 2), Math.round(y), Math.round(t), Math.round(s), argb);
        ui.b.rect(Math.round(x), Math.round(y + (s - t) / 2), Math.round(s), Math.round(t), argb);
    }

    // ------------------------------------------------------------------ lower left: what the body runs on
    private float meter(Ui ui, float x, float y, float w, String label, float f, int col, float alpha, boolean warn) {
        if (alpha <= 0.01f) return y;
        float h = ui.s(32);
        y -= h;
        if (warn) cross(ui, x, y + ui.s(4), ui.s(13), Ui.alpha(col, alpha));
        txt(ui, label, x + (warn ? ui.s(19) : 0), y, LABEL_PX, Ui.alpha(warn ? col : 0xFFE4E0D2, alpha));
        float by = y + ui.s(23), th = Math.max(2, ui.s(4));
        ui.b.rect(x, by, w, th, Ui.alpha(0xFF101211, alpha * 0.85f));
        ui.b.rect(x, by, w * Math.max(0, Math.min(1, f)), th, Ui.alpha(col, alpha));
        return y - ui.s(6);
    }

    public void vitals(Ui ui, float dt) {
        var s = raid.body;
        float t = raid.time, m = safe(ui);
        if (Math.abs(s.energy - shownFood) > 0.6f) { if (shownFood >= 0) lastFoodChange = t; shownFood = s.energy; }
        if (Math.abs(s.hydration - shownWater) > 0.6f) { if (shownWater >= 0) lastWaterChange = t; shownWater = s.hydration; }
        boolean always = c.settings.hudAlways || t < peekUntil;
        aStamina = ease(aStamina, always || t - s.staminaChanged < IDLE_AFTER && s.stamina < 99 || s.exhausted > 0 || s.winded, dt);
        aFocus = ease(aFocus, always || t - s.focusChanged < IDLE_AFTER && s.focus < 99, dt);
        aFood = ease(aFood, always || s.energy < 25 || t - lastFoodChange < IDLE_AFTER, dt);
        aWater = ease(aWater, always || s.hydration < 25 || t - lastWaterChange < IDLE_AFTER, dt);
        float x = m + ui.s(30), w = ui.s(220), y = ui.h - m - ui.s(22), top = y;
        float any = Math.max(Math.max(aStamina, aFocus), Math.max(aFood, aWater));
        if (any > 0.01f) {
            int rows = (aStamina > 0.01f ? 1 : 0) + (aFocus > 0.01f ? 1 : 0) + (aFood > 0.01f ? 1 : 0) + (aWater > 0.01f ? 1 : 0);
            shade(ui, x - ui.s(30), y - rows * ui.s(38) - ui.s(10), w + ui.s(70), rows * ui.s(38) + ui.s(26), any);
        }
        y = meter(ui, x, y, w, s.exhausted > 0 || s.winded ? "STAMINA  -  WINDED" : "STAMINA", s.stamina / 100f, s.exhausted > 0 || s.winded ? 0xFFE8463A : 0xFFE0B830, aStamina, s.exhausted > 0 || s.winded);
        y = meter(ui, x, y, w, "FOCUS", s.focus / 100f, 0xFF6AA4D6, aFocus, false);
        y = meter(ui, x, y, w, s.energy < 25 ? "FOOD  -  HUNGRY" : "FOOD", s.energy / 100f, s.energy < 25 ? 0xFFE8463A : 0xFFB8935A, aFood, s.energy < 25);
        y = meter(ui, x, y, w, s.hydration < 25 ? "WATER  -  THIRSTY" : "WATER", s.hydration / 100f, s.hydration < 25 ? 0xFFE8463A : 0xFF5AA0B8, aWater, s.hydration < 25);
        vitalsBox = new Box(x, y, w, top - y);
    }

    /** Everything is shown for a few seconds (the status key). */
    public void peek() { peekUntil = raid.time + 4f; }
}
