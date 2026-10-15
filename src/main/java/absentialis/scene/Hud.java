package absentialis.scene;

import absentialis.gameplay.items.ItemInstance;
import absentialis.ui.Layout;
import absentialis.ui.Ui;
import java.util.ArrayList;
import java.util.List;

/**
 * In-raid HUD (addendum P1): minimal and contextual. Health / Stamina / Focus cores appear only
 * when they change; ammo only on fire or reload; the hour of the day sits top-centre (0.7: there is no
 * extraction countdown - operations have no time limit); hit feedback is directional with optional (scaled) shake;
 * captions describe sounds with a direction tag.
 */
public final class Hud {
    private final Ctx c;
    public float ammoShown = -99;
    private final List<Object[]> captions = new ArrayList<>(); // {text, until, color}
    private final List<Object[]> messages = new ArrayList<>();
    private float coreAlphaH, coreAlphaS, coreAlphaF, ammoAlpha;

    public Hud(Ctx c) { this.c = c; }

    /** 1.1: seconds between one notice appearing and the next (they used to arrive in a heap), how long each takes to come and go, and how long it stays. */
    public static float NOTE_GAP = 0.45f, NOTE_FADE = 0.2f, NOTE_LIFE = 5f;
    private float lastShown = -99;

    /**
     * A routine notice for the top-left column. 1.1: {text, until, colour, shown-from, y (eased), alpha (eased)}. A notice
     * that is already up is not put up again - it stays that much longer, without blinking; a new one waits its turn, a
     * little under half a second after the one before it.
     */
    public void message(String s, int col, float now) {
        for (Object[] o : messages) if (o[0].equals(s)) { o[1] = Math.max((Float) o[1], Math.max(now, (Float) o[3]) + NOTE_LIFE); return; }
        float from = Math.max(now, lastShown + NOTE_GAP);
        lastShown = from;
        messages.add(new Object[]{s, from + NOTE_LIFE, col, from, Float.NaN, 0f});
        while (messages.size() > 6) messages.remove(0);
    }
    /** Notices on screen or waiting (gates). */
    public int notices() { return messages.size(); }
    /** The eased alpha of each notice on the last frame drawn (gates: nothing blinks). */
    public float[] noticeAlphas() { float[] a = new float[messages.size()]; for (int i = 0; i < a.length; i++) a[i] = (Float) messages.get(i)[5]; return a; }

    public void caption(String category, float bearingDeg, float loud, float now) {
        if (!c.settings.subtitles) return;
        String name = switch (category) {
            case "shot" -> "Gunfire"; case "shot_suppressed" -> "Suppressed shots"; case "explosion" -> "Explosion"; case "glass_break" -> "Glass breaking";
            case "groan" -> "Infected groan"; case "screech" -> "SCREECHER ALARM"; case "gurgle" -> "Fat infected swelling"; case "roar" -> "GIANT ROAR";
            case "shriek" -> "Fast infected shriek"; case "retch" -> "Fat infected retching"; case "splash" -> "Acid splash"; case "stomp" -> "Heavy footfalls";
            case "shout" -> "Shouting"; case "whistle" -> "Whistle"; case "radio_chatter" -> "Radio chatter";
            case "door_open", "door_close" -> "Door"; case "reload" -> "Reloading"; case "flare" -> "Flare"; case "alarm_clock" -> "Alarm clock ringing";
            case "impact" -> "Bullet impacts"; case "debris" -> "Debris"; case "melee" -> "Struggle"; case "search" -> "Rummaging"; case "bounce" -> "Clatter";
            default -> null;
        };
        if (name == null) return;
        String dir = bearing(bearingDeg);
        String dist = loud > 0.7f ? "close" : loud > 0.35f ? "near" : "distant";
        String text = "[" + name + " - " + dist + ", " + dir + "]";
        for (Object[] o : captions) if (o[0].equals(text)) { o[1] = now + 3f; return; }
        captions.add(new Object[]{text, now + 3f, category.equals("screech") || category.equals("roar") || category.equals("shriek") || category.equals("retch") || category.equals("stomp") ? Ui.RED : Ui.TXT});
        while (captions.size() > 4) captions.remove(0);
    }

    public static String bearing(float deg) {
        deg = ((deg % 360) + 360) % 360;
        if (deg < 30 || deg > 330) return "ahead";
        if (deg < 150) return deg < 60 ? "front-right" : deg < 120 ? "right" : "behind-right";
        if (deg < 210) return "behind";
        return deg > 300 ? "front-left" : deg > 240 ? "left" : "behind-left";
    }

    /** 0.8: set by the screen each frame - a laser is on (it is the reticle), and the weapon is being aimed in third person. */
    public boolean laserOn, thirdPersonAim;
    /** 0.9, over the shoulder: the muzzle's line meets something before the reticle's point does - and where on the screen it stops; and how wide the next shot can go. */
    public boolean aimBlocked;
    public float aimBlockX, aimBlockY, aimSpread;
    /** The stance last shown (0 standing, 1 crouched, 2 prone) and when it was taken up; how long the icon stays. */
    public int stance;
    public float stanceAt = -99;
    public static float STANCE_SECONDS = 1.8f;

    /** True while the stance icon is on screen (tests). */
    public boolean stanceShowing(float now) { return now - stanceAt < STANCE_SECONDS; }

    /** A small figure in a square of the given size: upright, folded at the knees, or flat on the ground. */
    static void stanceIcon(Ui ui, int stance, float x, float y, float size, float alpha) {
        int col = Ui.alpha(0xFFF4F0E4, alpha), back = Ui.alpha(0xA0101214, alpha);
        float u = size / 12f;
        ui.b.rect(x - u, y - u, size + 2 * u, size + 2 * u, back);
        if (stance == 0) {
            ui.b.rect(x + 5 * u, y + 0.5f * u, 2 * u, 2 * u, col);                                 // head
            ui.b.rect(x + 4.5f * u, y + 3 * u, 3 * u, 4 * u, col);                                 // body
            ui.b.rect(x + 4.5f * u, y + 7 * u, 1.2f * u, 4.5f * u, col); ui.b.rect(x + 6.3f * u, y + 7 * u, 1.2f * u, 4.5f * u, col);   // legs
        } else if (stance == 1) {
            ui.b.rect(x + 5.5f * u, y + 3 * u, 2 * u, 2 * u, col);
            ui.b.rect(x + 4.5f * u, y + 5.5f * u, 3 * u, 3 * u, col);
            ui.b.rect(x + 3.5f * u, y + 8.5f * u, 4 * u, 1.2f * u, col); ui.b.rect(x + 3.5f * u, y + 8.5f * u, 1.2f * u, 3 * u, col); ui.b.rect(x + 6.8f * u, y + 9.7f * u, 1.2f * u, 1.8f * u, col);   // folded legs
        } else {
            ui.b.rect(x + 0.5f * u, y + 7.5f * u, 2 * u, 2 * u, col);
            ui.b.rect(x + 3 * u, y + 8 * u, 4.5f * u, 2 * u, col);
            ui.b.rect(x + 7.5f * u, y + 8.6f * u, 4 * u, 1.3f * u, col);
        }
        ui.b.rect(x, y + size - 0.4f * u, size, Math.max(1, 0.4f * u), Ui.alpha(0xFF8A6420, alpha));     // the ground
    }

    public void draw(Raid r, ItemInstance held, float reloadT, float reloadMax, String interact, boolean ads, float dt) {
        Ui ui = c.ui;
        float t = r.time;
        var s = r.body;
        // ---- cores (show on change, fade out after 3.5s)
        coreAlphaH = fade(coreAlphaH, t - s.healthChanged < 3.5f || r.player.hp < 35 || s.bleedLight || s.bleedHeavy, dt);
        coreAlphaS = fade(coreAlphaS, t - s.staminaChanged < 2.5f && s.stamina < 99, dt);
        coreAlphaF = fade(coreAlphaF, t - s.focusChanged < 2.5f && s.focus < 99, dt);
        // health, armour, stamina, focus and ammunition are the status block's (scene.StatusHud, lower right).
        // 1.0: the conditions that need words - wounds, bleeding, hunger, radiation, the infection - are part of the same
        // block: a column of plates standing on it, on its right edge, where the eye already goes for the body's state.
        // 1.1: they stand above the weapon-and-health group, wherever that is; each has a mark as well as a colour; and the
        // ones that belong to a meter (winded, hungry, thirsty) are said on the meter, lower left, not here
        float rx = warnRight > 0 ? warnRight : ui.w - ui.s(StatusHud.MOD_MX), sy = (warnTop > 0 ? warnTop : ui.h - ui.s(StatusHud.MOD_MY + StatusHud.MOD_H)) - ui.s(30) - (held != null && held.jammed ? ui.s(36) : 0);
        if (s.bleedHeavy) sy = chip(ui, "HEAVY BLEEDING", rx, sy, Ui.RED);
        else if (s.bleedLight) sy = chip(ui, "BLEEDING", rx, sy, Ui.RED);
        if (s.fracture) sy = chip(ui, "FRACTURE", rx, sy, Ui.RED);
        if (s.armInjury > 0.25f) sy = chip(ui, "ARM WOUNDED", rx, sy, s.armInjury > 0.6f ? Ui.RED : Ui.AMBER);
        if (s.legInjury > 0.25f) sy = chip(ui, "LEG WOUNDED", rx, sy, s.legInjury > 0.6f ? Ui.RED : Ui.AMBER);
        if (s.rad > 5) sy = chip(ui, String.format("RADIATION %.0f", s.rad), rx, sy, s.rad > 60 ? Ui.RED : Ui.AMBER);
        if (s.infection > 0) {
            // the infection is never hidden: stage, how far along, and whether antibiotics are holding it
            String stage = InfectionSystem.stage(s.infection);
            int col = s.infection >= InfectionSystem.CRITICAL ? Ui.RED : s.infection >= InfectionSystem.FEVER ? 0xFFB8D048 : Ui.AMBER;
            boolean heldBack = t < s.suppressUntil;
            String txt = stage.toUpperCase() + (heldBack ? "  (HELD)" : "") + "  -  Antiviral Medicine cures it";
            float w = ui.tw(txt, 13) + ui.s(16);
            StatusHud.plate(ui, rx - w, sy - ui.s(6), w, ui.s(30), 1);
            ui.text(txt, rx - w + ui.s(8), sy - ui.s(2), 13, col);
            ui.b.rect(rx - w + ui.s(8), sy + ui.s(17), w - ui.s(16), ui.s(3), 0xFF1A1C1B);
            ui.b.rect(rx - w + ui.s(8), sy + ui.s(17), (w - ui.s(16)) * Math.min(1, s.infection / 100f), ui.s(3), col);
        }

        // ---- ammunition is drawn by the status module; the 0.4 box is kept only as a fallback for screens without one
        boolean showAmmo = false;
        ammoAlpha = fade(ammoAlpha, showAmmo, dt);
        if (held != null && ammoAlpha > 0.01f && held.def().weapon != null && !held.def().weapon.melee()) {
            float ax = ui.w - ui.s(250), ay = ui.h - ui.s(78);
            int a = Math.round(ammoAlpha * 255);
            ui.b.rect(ax - ui.s(10), ay - ui.s(6), ui.s(238), ui.s(60), (a * 170 / 255) << 24 | 0x0E0F0E);
            ui.glyph("bullet", ax, ay + ui.s(6), Math.max(2, ui.s(2)), Ui.alpha(0xFFFFFFFF, ammoAlpha));
            String rounds = String.valueOf(held.loadedRounds());
            ui.text(rounds, ax + ui.s(34), ay, 30, Ui.alpha(held.loadedRounds() == 0 ? Ui.RED : Ui.TXT_HI, ammoAlpha));
            ui.text(held.fireModeName().toUpperCase(), ax + ui.s(110), ay + ui.s(4), 13, Ui.alpha(Ui.TXT_DIM, ammoAlpha));
            ui.text(held.def().name, ax + ui.s(110), ay + ui.s(22), 12, Ui.alpha(Ui.TXT, ammoAlpha));
            float cond = held.condition() / 100f;
            ui.b.rect(ax + ui.s(34), ay + ui.s(40), ui.s(180) * cond, ui.s(3), Ui.alpha(cond < 0.25f ? Ui.RED : cond < 0.5f ? Ui.AMBER : Ui.GREEN, ammoAlpha));
            if (held.jammed) ui.text("JAMMED - [R] CLEAR", ax, ay - ui.s(24), 15, Ui.RED);
            if (reloadT > 0) {
                float f = 1 - reloadT / Math.max(0.01f, reloadMax);
                ui.b.rect(ax + ui.s(34), ay - ui.s(8), ui.s(180), ui.s(4), 0x80000000);
                ui.b.rect(ax + ui.s(34), ay - ui.s(8), ui.s(180) * f, ui.s(4), 0xFFE2A034);
            }
        }

        // (1.0: the hour is under the minimap, once. It used to be at the top of the screen as well.)

        // ---- 1.0, the lower-centre panel: one place, above the weapon, that does not move. Leaving is the thing it is for:
        // the seconds left, large, with the name of the way out; what to do about it is said under it, and only while it counts.
        // Something being used (a bandage, the spray) counts down in the same place, since it is the same kind of wait.
        float pw = ui.s(340), ph = ui.s(62), px = Math.round(ui.w / 2f - pw / 2), py = ui.h - ui.s(262);
        if (r.extracting != null) {
            float f = r.extractProgress / 8f, left = Math.max(0, 8 - r.extractProgress);
            StatusHud.plate(ui, px, py, pw, ph, 1);
            ui.text("EXTRACTING", px + ui.s(14), py + ui.s(10), 13, Ui.GREEN);
            Layout.fit(ui, r.extracting.exit.name, px + ui.s(14), py + ui.s(30), pw - ui.s(130), 16, 11, Ui.TXT_HI);
            ui.textR(String.format("%.1f", left), px + pw - ui.s(14), py + ui.s(6), 42, Ui.TXT_HI);
            ui.b.rect(px + 1, py + ph - ui.s(5), pw - 2, ui.s(4), 0xFF1A1C1B);
            ui.b.rect(px + 1, py + ph - ui.s(5), (pw - 2) * f, ui.s(4), 0xFF7DBA5C);
            String how = "Stay inside the marked area - leaving it stops the count";
            float hw = ui.tw(how, 12) + ui.s(16);
            StatusHud.plate(ui, ui.w / 2f - hw / 2, py + ph + ui.s(4), hw, ui.s(22), 0.9f);
            ui.textC(how, ui.w / 2f, py + ph + ui.s(8), 12, Ui.TXT);
        } else if (r.body.using != null) {
            float total = Math.max(r.body.useTimer, useTotal);
            useTotal = total;
            StatusHud.plate(ui, px, py, pw, ph, 1);
            ui.text("USING", px + ui.s(14), py + ui.s(10), 13, Ui.TXT_DIM);
            Layout.fit(ui, r.body.using.name, px + ui.s(14), py + ui.s(30), pw - ui.s(130), 16, 11, Ui.TXT_HI);
            ui.textR(String.format("%.1f", Math.max(0, r.body.useTimer)), px + pw - ui.s(14), py + ui.s(6), 42, Ui.TXT_HI);
            ui.b.rect(px + 1, py + ph - ui.s(5), pw - 2, ui.s(4), 0xFF1A1C1B);
            ui.b.rect(px + 1, py + ph - ui.s(5), (pw - 2) * (1 - r.body.useTimer / Math.max(0.01f, total)), ui.s(4), 0xFFD6CEB8);
        } else {
            useTotal = 0;
            Raid.ExitState here = r.exitAt(r.player.x, r.player.y, r.player.z);
            if (here != null) {
                // standing at a way out that cannot be used yet: what it wants, in the same place, without the count
                String need = here.exit.name + "  -  " + here.requirement;
                float nw = Math.min(ui.w - ui.s(80), ui.tw(need, 14) + ui.s(28));
                StatusHud.plate(ui, ui.w / 2f - nw / 2, py + ui.s(16), nw, ui.s(30), 1);
                Layout.fit(ui, need, ui.w / 2f - nw / 2 + ui.s(14), py + ui.s(23), nw - ui.s(28), 14, 10, Ui.AMBER);
            }
        }

        // ---- directional hit feedback
        if (c.settings.hitDirection) for (float[] hm : r.hitMarkers) {
            float age = t - hm[1];
            float rel = hm[0] - r.player.yaw;
            float a = Math.max(0, 1 - age / 1.6f);
            float cx = ui.w / 2f, cy = ui.h / 2f, rad = ui.s(120);
            float R = (float) -Math.sin(rel), F = (float) Math.cos(rel); // attacker in player-local right/forward
            float hx = cx + R * rad, hy = cy - F * rad, tx = F, ty = R;  // arc tangent on screen
            ui.b.line(hx - tx * ui.s(26), hy - ty * ui.s(26), hx + tx * ui.s(26), hy + ty * ui.s(26), ui.s(5), Ui.alpha(0xFFD8483A, a));
        }
        if (r.player.hp < 30) ui.b.rect(0, 0, ui.w, ui.h, Ui.alpha(0x60800000, (30 - r.player.hp) / 30f * (0.6f + 0.4f * (float) Math.sin(t * 4))));

        // ---- crosshair (minimal dot, hidden while aiming)
        if (!ads && interact == null && !laserOn) ui.b.rect(ui.w / 2f - 1, ui.h / 2f - 1, 2, 2, 0xC0FFFFFF);
        // 0.8: aiming in third person there are no sights to look through, so there is a reticle - four ticks and a
        // point, where the round will go. A laser replaces it: with a laser on, its dot in the world is the reticle.
        if (thirdPersonAim && !laserOn && interact == null) {
            // 0.9: the ticks stand apart by how wide the shot can go right now (still and crouched they close up; on the move they open),
            // and when the muzzle's line is blocked the reticle goes dim and a cross marks where the round will actually stop
            float cx = Math.round(ui.w / 2f), cy = Math.round(ui.h / 2f), gap = ui.s(4) + Math.min(ui.s(22), aimSpread * ui.h * 0.011f), len = ui.s(6), th = Math.max(1, Math.round(ui.s(1.5f)));
            int out = aimBlocked ? 0x60000000 : 0xB0000000, in = aimBlocked ? 0x70FFFFFF : 0xF0FFFFFF;
            if (aimBlocked) {
                float mkx = Math.round(aimBlockX), mky = Math.round(aimBlockY), q = ui.s(6);
                for (int k = -1; k <= 1; k += 2) { ui.b.line(mkx - q, mky - q * k, mkx + q, mky + q * k, Math.max(2, ui.s(3.5f)), 0xC0000000); }
                for (int k = -1; k <= 1; k += 2) { ui.b.line(mkx - q, mky - q * k, mkx + q, mky + q * k, Math.max(1, ui.s(1.8f)), 0xFFE03A2E); }
            }
            for (int pass = 0; pass < 2; pass++) {
                float g = pass == 0 ? 1 : 0;
                int col = pass == 0 ? out : in;
                ui.b.rect(cx - gap - len - g, cy - th / 2f - g, len + 2 * g, th + 2 * g, col); ui.b.rect(cx + gap - g, cy - th / 2f - g, len + 2 * g, th + 2 * g, col);
                ui.b.rect(cx - th / 2f - g, cy - gap - len - g, th + 2 * g, len + 2 * g, col); ui.b.rect(cx - th / 2f - g, cy + gap - g, th + 2 * g, len + 2 * g, col);
                ui.b.rect(cx - 1 - g, cy - 1 - g, 2 + 2 * g, 2 + 2 * g, col);
            }
        }
        // ---- stance: said once, when it changes, by a small figure - standing, crouched or prone - and then gone
        int stanceNow = r.player.prone ? 2 : r.player.crouch ? 1 : 0;
        if (stanceNow != stance) { stance = stanceNow; stanceAt = t; }
        float sAge = t - stanceAt;
        if (sAge < STANCE_SECONDS && !r.player.dead && r.vehicles.driving == null) {
            float a = Math.min(1, sAge / 0.12f) * Math.min(1, (STANCE_SECONDS - sAge) / 0.5f);
            stanceIcon(ui, stance, ui.w / 2f - ui.s(17), ui.h - ui.s(150), ui.s(34), a);
        }
        // ---- the squad's orders: while they are holding fire, it says so
        if (!r.mates.isEmpty() && r.holdFire("gold") && !r.player.dead) {
            String hf = "SQUAD HOLDING FIRE  [F7]";
            float hw = ui.tw(hf, 13) + ui.s(22);
            StatusHud.plate(ui, ui.w / 2f - hw / 2, ui.s(10), hw, ui.s(24), 1);
            ui.textC(hf, ui.w / 2f, ui.s(15), 13, Ui.AMBER);
        }
        if (interact != null) {
            ui.b.rect(ui.w / 2f - 2, ui.h / 2f - 2, 4, 4, 0xFFE2A034);
            ui.textC(interact, ui.w / 2f, ui.h / 2f + ui.s(18), 15, Ui.TXT_HI);
        }

        // ---- captions & messages
        float cy = ui.h - ui.s(150);
        for (int i = captions.size() - 1; i >= 0; i--) {
            Object[] o = captions.get(i);
            float left = (Float) o[1] - t;
            if (left <= 0) { captions.remove(i); continue; }
            String txt = (String) o[0];
            float w = ui.tw(txt, 15);
            StatusHud.plate(ui, ui.w / 2f - w / 2 - ui.s(8), cy - ui.s(3), w + ui.s(16), ui.s(22), Math.min(1, left));
            ui.textC(txt, ui.w / 2f, cy, 15, Ui.alpha((Integer) o[2], Math.min(1, left)));
            cy -= ui.s(26);
        }
        // 1.0: everything routine is said in one place - the top-left column, under the experience bar when that is showing:
        // what was picked up, what was learned, what the squad reports. Newest first, each on its own plate.
        // 1.1: the column no longer blinks. A notice eases in over a fifth of a second and out the same way; one that is said
        // again while it is up simply stays; when one above it goes, the rest slide up to close the gap instead of jumping;
        // and they arrive one at a time (see message). Nothing above them moves them about any more - experience is said
        // at the top centre of the screen, not here.
        float mx0 = noteLeft > 0 ? noteLeft : ui.s(14), my = Math.max(ui.s(18), noteTop), rowH = ui.s(30);
        for (int i = 0; i < messages.size(); i++) {
            Object[] o = messages.get(i);
            float until = (Float) o[1], from = (Float) o[3];
            if (t >= until) { messages.remove(i--); continue; }
            if (t < from) continue;                                              // waiting its turn
            float target = Math.min(1, (t - from) / NOTE_FADE) * Math.min(1, (until - t) / NOTE_FADE);
            o[5] = target;
            float y = (Float) o[4];
            y = Float.isNaN(y) ? my : y + (my - y) * Math.min(1, dt * 12);
            o[4] = y;
            String txt = Layout.elide(ui, (String) o[0], ui.w * 0.40f, 16);
            float w = ui.tw(txt, 16) + ui.s(26);
            Hud11.shade(ui, mx0 - ui.s(10), y - ui.s(4), w + ui.s(30), rowH + ui.s(2), target);
            ui.b.rect(mx0, y + ui.s(3), Math.max(1, ui.s(2)), ui.s(18), Ui.alpha((Integer) o[2], target));
            Hud11.txt(ui, txt, mx0 + ui.s(10), y + ui.s(2), 16, Ui.alpha((Integer) o[2], target));
            my += rowH;
        }
        noteBottom = my;
    }

    /** 1.1: where the warnings stand (the top and right edge of the weapon-and-health group), the left edge of the notice column, and where that column ended this frame (the kill feed starts under it). */
    public float warnTop, warnRight, noteLeft, noteBottom;

    /** 1.0: where the notification column starts this frame (under whatever the experience bar drew), and how long the thing in use takes in all. */
    public float noteTop;
    private float useTotal;

    /** A condition, on a plate, right-aligned on the status block; returns the y of the next one up. */
    private float chip(Ui ui, String text, float rx, float y, int col) {
        // 1.1: a mark and the words, on a soft shade - a warning is read by its shape and its text, not by its colour alone
        float w = ui.tw(text, 15) + ui.s(26);
        Hud11.shade(ui, rx - w - ui.s(12), y - ui.s(3), w + ui.s(22), ui.s(28), 1);
        Hud11.cross(ui, rx - w, y + ui.s(5), ui.s(12), col);
        Hud11.txt(ui, text, rx - w + ui.s(18), y + ui.s(2), 15, col);
        return y - ui.s(28);
    }

    private static float fade(float a, boolean on, float dt) { return a + ((on ? 1 : 0) - a) * Math.min(1, dt / (on ? 0.12f : 0.6f)); }

    /** A core: glyph + 10 depleting pixel cells (deplete, not bars). */
    private void core(Ui ui, String glyph, float x, float y, float f, int col, float alpha, String status) {
        if (alpha < 0.01f) return;
        int g = Math.max(2, ui.s(2));
        ui.b.rect(x - ui.s(6), y - ui.s(6), ui.s(140), ui.s(38), Ui.alpha(0x900E0F0E, alpha));
        ui.glyph(glyph, x, y, g, Ui.alpha(0xFFFFFFFF, alpha));
        float cx = x + 12 * g + ui.s(8), cw = ui.s(8), gap = ui.s(2);
        for (int i = 0; i < 10; i++) {
            float fill = Math.max(0, Math.min(1, f * 10 - i));
            float px = cx + i * (cw + gap);
            ui.b.rect(px, y + ui.s(2), cw, ui.s(20), Ui.alpha(0xFF1A1C1B, alpha));
            if (fill > 0) {
                float h = ui.s(20) * fill;
                ui.b.rect(px, y + ui.s(2) + ui.s(20) - h, cw, h, Ui.alpha(col, alpha));
                ui.b.rect(px, y + ui.s(2) + ui.s(20) - h, cw, 1, Ui.alpha(0x60FFFFFF, alpha));
            }
        }
        if (status != null) ui.text(status, x, y - ui.s(20), 12, Ui.alpha(Ui.RED, alpha));
    }
}
