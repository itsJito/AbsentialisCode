package absentialis.scene;

import absentialis.engine.math.Mat4;
import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.entity.Vital;
import absentialis.gameplay.CombatConfig;
import absentialis.gameplay.CombatConfig.Condition;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemDef;
import absentialis.gameplay.items.ItemInstance;
import absentialis.ui.Layout;
import absentialis.ui.Layout.Box;
import absentialis.ui.Portrait;
import absentialis.ui.Ui;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The 0.5 survival-action interface (brief sections 9.2, 10, 11, 12, 13): one visual language for the lower-right
 * character status module (portrait, curved health gauge, ammunition), the squad column and killfeed on the left
 * edge, the centre-screen hit marker, speech bubbles over heads, minimap contacts and the inventory's status
 * panel. Everything is read from authoritative state each frame - entity health, the loadout, the raid's
 * confirmed events - and laid out in anchored, scaled units, so nothing here can drift from the game or from the
 * screen it is drawn on.
 */
public final class StatusHud {
    private final Ctx c;
    private final Raid raid;
    private final CombatConfig cfg = CombatConfig.get();
    public final CombatFeed feed;
    private final Portrait portraits = new Portrait();
    private final Map<String, ItemInstance> iconItems = new HashMap<>();
    private float shownHp = -1, ammoDim = 1;
    /** Layout results of the last frame (gates check the module stays on screen and the feed clear of the squad column). */
    public Box moduleBox = new Box(0, 0, 0, 0), squadBox = new Box(0, 0, 0, 0), feedBox = new Box(0, 0, 0, 0);
    public int bubblesDrawn, bubblesSkipped;
    /** 1.1: the left edge of the kill feed when something else decides it (0: the old margin). */
    public float feedX;
    /** 1.1: the highest the squad column may start (it goes under the kill feed, never over it), and its left edge. */
    public float squadMinY, squadX;

    StatusHud(Ctx c, Raid raid) {
        this.c = c; this.raid = raid;
        feed = new CombatFeed(raid);
        feed.attach(raid.bus);
    }

    // ------------------------------------------------------------------ shared pieces
    /** Arc length of the health gauge for a maximum health: a half circle at the base maximum, wrapping further as it grows. */
    public static float gaugeSpan(float maxHp, CombatConfig cfg) {
        float k = Math.max(0, Math.min(1, (maxHp - cfg.baseHealth) / Math.max(1f, cfg.healthCap - cfg.baseHealth)));
        return (float) Math.PI * (1f + 0.78f * k);
    }

    /** Reserve rounds for a weapon: every loose round of its calibre carried, not counting what is loaded. */
    public static int reserve(Raid raid, ItemInstance weapon) {
        if (weapon == null || weapon.def().weapon == null || weapon.def().weapon.caliber == null) return 0;
        String cal = weapon.def().weapon.caliber;
        int n = 0;
        for (ItemInstance it : raid.profile.loadout.all()) { ItemDef d = it.def(); if (d.ammo != null && cal.equals(d.ammo.caliber)) n += it.qty; }
        return n;
    }

    private int portrait(Actor a, ActorRenderer.Look look) {
        Condition cond = cfg.condition(a.hp, a.maxHp);
        return portraits.texture(look.key, () -> c.ar.flatten(look), a.dead ? Condition.CRITICAL : cond, a.dead || a.vital != Vital.ACTIVE);
    }

    private ItemInstance iconFor(String defId) {
        if (defId == null || defId.isEmpty() || Content.def(defId) == null) return null;
        return iconItems.computeIfAbsent(defId, id -> new ItemInstance(-9, id));
    }

    /** An item's icon fitted inside a box, keeping its proportions. */
    void icon(Ui ui, ItemInstance it, float x, float y, float w, float h, int argb) {
        float ar = it.baseW() / (float) Math.max(1, it.baseH());
        float iw = Math.min(w, h * ar), ih = iw / ar;
        c.icons.draw(ui.b, it, Math.round(x + (w - iw) / 2), Math.round(y + (h - ih) / 2), iw, ih, argb);
    }

    /**
     * The portrait inside its gauge. Returns nothing; draws at (cx, cy) with outer radius r. Shared by the gameplay
     * module and the inventory panel so the two read as one object.
     */
    void gauge(Ui ui, Actor p, ActorRenderer.Look look, float cx, float cy, float r, float t) {
        float r0 = r - ui.s(8), r1 = r;
        Condition cond = cfg.condition(p.hp, p.maxHp);
        int col = cfg.color(cond);
        float a0 = (float) Math.PI, span = gaugeSpan(p.maxHp, cfg);
        // portrait on a dark disc
        Layout.arc(ui, cx, cy, 0, r0 - ui.s(3), 0, (float) Math.PI * 2, 0xE0101311);
        float ps = Math.round((r0 - ui.s(5)) * 1.42f);
        ui.b.image(portrait(p, look), Math.round(cx - ps / 2), Math.round(cy - ps / 2), ps, ps, p.downed() ? 0xFFB0A8A8 : 0xFFFFFFFF);
        // track, then the fill: current health as a share of the arc the maximum allows
        Layout.arc(ui, cx, cy, r0 - 1, r1 + 1, a0 - 0.012f, a0 + span + 0.012f, 0xFF0B0C0B);
        Layout.arc(ui, cx, cy, r0, r1, a0, a0 + span, 0xFF24282A);
        if (shownHp < 0) shownHp = p.hp;
        shownHp += (p.hp - shownHp) * Math.min(1, ui.dt * 9);
        float f = Math.max(0, Math.min(1, shownHp / Math.max(1f, p.maxHp)));
        if (p.downed()) {
            // on the ground the gauge is the blood that is left
            float bl = raid.casualty.bleedFraction(p), pulse = 0.7f + 0.3f * (float) Math.sin(t * 6);
            Layout.arc(ui, cx, cy, r0, r1, a0, a0 + span * bl, Ui.alpha(cfg.color(Condition.CRITICAL), pulse));
            if (p.vital == Vital.REVIVING) Layout.arc(ui, cx, cy, r1 + ui.s(2), r1 + ui.s(5), a0, a0 + span * raid.casualty.reviveFraction(p), 0xFF7DBA5C);
        } else {
            // a recent loss shows for a moment as a paler tail behind the fill
            if (shownHp > p.hp + 0.5f) Layout.arc(ui, cx, cy, r0, r1, a0 + span * Math.max(0, p.hp / p.maxHp), a0 + span * f, 0xFFE8E0D0);
            float pulse = cond == Condition.CRITICAL ? 0.72f + 0.28f * (float) Math.sin(t * 5.2f) : 1;       // restrained: brightness, not size
            Layout.arc(ui, cx, cy, r0, r1, a0, a0 + span * Math.max(0, Math.min(1, p.hp / p.maxHp)), Ui.alpha(col, pulse));
            Layout.arc(ui, cx, cy, r1 - ui.s(2), r1, a0, a0 + span * Math.max(0, Math.min(1, p.hp / p.maxHp)), Ui.alpha(Ui.mix(col, 0xFFFFFFFF, 0.35f), pulse));
        }
        // a notch every fifty points, so a longer gauge reads as more health and not as a bigger picture
        for (float hp = 50; hp < p.maxHp - 1; hp += 50) { float a = a0 + span * hp / p.maxHp; Layout.arc(ui, cx, cy, r0, r1, a - 0.014f, a + 0.014f, 0xFF0B0C0B); }
        // body armour: a thin outer band over the same sweep
        if (p.shieldMax > 0) {
            Layout.arc(ui, cx, cy, r1 + ui.s(3), r1 + ui.s(6), a0, a0 + span, 0xFF1A2026);
            Layout.arc(ui, cx, cy, r1 + ui.s(3), r1 + ui.s(6), a0, a0 + span * Math.max(0, p.shield / p.shieldMax), 0xFF6AA4D6);
        }
    }

    /** 1.0: the size of the lower-right block (unscaled), its margins, and the one plate colour every piece of the HUD sits on. */
    public static final int MOD_W = 404, MOD_H = 150, MOD_MX = 14, MOD_MY = 12, PLATE = 0xB80B0C0B, PLATE_LINE = 0x30FFFFFF;

    /** A dark plate with a hairline round it: what every piece of the HUD sits on, so that all of it reads over any scene. */
    public static void plate(Ui ui, float x, float y, float w, float h, float a) {
        ui.b.rect(Math.round(x), Math.round(y), Math.round(w), Math.round(h), Ui.alpha(PLATE, a));
        ui.b.outline(Math.round(x), Math.round(y), Math.round(w), Math.round(h), 1, Ui.alpha(PLATE_LINE, a));
    }

    /** A labelled meter: the name, then the bar. With {@code sprint}, the running mark before the name. */
    private void survival(Ui ui, float x, float y, float w, String label, float f, int col, boolean sprint) {
        float lx = x;
        if (sprint) {
            // the sprint mark: two chevrons, leaning into a run
            int cc = Ui.alpha(col, 0.95f);
            for (int k = 0; k < 2; k++) { float cx = x + k * ui.s(5); ui.b.line(cx, y + ui.s(2), cx + ui.s(4), y + ui.s(7), Math.max(1.5f, ui.s(1.6f)), cc); ui.b.line(cx + ui.s(4), y + ui.s(7), cx, y + ui.s(12), Math.max(1.5f, ui.s(1.6f)), cc); }
            lx += ui.s(13);
        }
        ui.text(label, lx, y, 11, Ui.TXT);
        float bx = x + ui.s(76), bw = w - ui.s(76);
        ui.b.rect(bx, y + ui.s(4), bw, ui.s(6), 0xFF1A1C1B);
        ui.b.rect(bx, y + ui.s(4), bw * Math.max(0, Math.min(1, f)), ui.s(6), col);
    }

    // ------------------------------------------------------------------ the lower-right block
    /**
     * 1.0: one block, one plate, everything on a line with something else. Health (the gauge and its number) and the
     * ammunition in the weapon are the two large things; under them the weapon by name, and the four things the body
     * runs on - each with its name on it. When there is nothing left to load, it says what there is none of, by the counter.
     */
    public void module(Ui ui, ItemInstance held, float reloadT, float reloadMax, ActorRenderer.Look look, float ammoShown) {
        Actor p = raid.player;
        float t = raid.time;
        Box box = Layout.anchor(ui, 1, 1, MOD_W, MOD_H, MOD_MX, MOD_MY);
        moduleBox = box;
        plate(ui, box.x, box.y, box.w, box.h, 1);
        float r = ui.s(40), cx = box.r() - ui.s(58), cy = box.y + ui.s(52), in = ui.s(14);
        gauge(ui, p, look, cx, cy, r, t);
        // health, in figures, under the portrait: the number first
        Condition cond = cfg.condition(p.hp, p.maxHp);
        int hc = p.downed() ? cfg.color(Condition.CRITICAL) : cfg.color(cond);
        String hp = Math.max(0, Math.round(p.hp)) + " / " + Math.round(p.maxHp);
        ui.textC(hp, cx, cy + r + ui.s(12), 17, hc);
        String state = p.downed() ? (p.vital == Vital.REVIVING ? "BEING REVIVED" : "DOWNED  " + Math.max(0, (int) Math.ceil(p.bleedLeft)) + "s") : cond.label.toUpperCase();
        ui.textC(state, cx, cy + r + ui.s(33), 11, hc);

        // ---- the weapon and what is in it
        float lx = box.x + in, ax = cx - r - ui.s(20), ty = box.y + ui.s(12);     // left edge; right edge of the ammunition
        boolean gun = held != null && held.def().weapon != null && !held.def().weapon.melee();
        ammoDim += ((t - ammoShown < 3.5f || reloadT > 0 ? 1f : 0.8f) - ammoDim) * Math.min(1, ui.dt * 6);
        if (gun) {
            int loaded = held.loadedRounds(), res = reserve(raid, held);
            String big = String.valueOf(loaded), small = "/ " + res;
            float sw = ui.tw(small, 18);
            ui.textR(small, ax, ty + ui.s(20), 18, Ui.alpha(res == 0 ? Ui.AMBER : Ui.TXT, ammoDim));
            ui.textR(big, ax - sw - ui.s(8), ty - ui.s(2), 44, Ui.alpha(loaded == 0 ? Ui.RED : Ui.TXT_HI, ammoDim));
            float used = sw + ui.s(8) + ui.tw(big, 44) + ui.s(12);
            icon(ui, held, lx, ty + ui.s(2), Math.min(ui.s(112), ax - lx - used), ui.s(40), Ui.alpha(0xFFFFFFFF, ammoDim));
            float ny = ty + ui.s(50);
            // nothing left to reload with: said here, by the counter, in the name of what is missing
            ItemDef am = held.def().weapon.caliber == null ? null : Content.ammoFor(held.def().weapon.caliber);
            String none = res > 0 ? null : "No " + (am == null ? "ammunition" : am.name);
            float nw = none == null ? 0 : ui.tw(none, 12) + ui.s(10);
            if (none != null) ui.textR(none, ax, ny, 12, loaded == 0 ? Ui.RED : Ui.AMBER);
            String mode = held.fireModeName().toUpperCase();
            float mw = ui.tw(mode, 11) + ui.s(8);
            Layout.fit(ui, held.def().name, lx, ny - ui.s(1), ax - lx - nw - mw, 13, 10, Ui.TXT_HI);
            ui.text(mode, lx + Math.min(ui.tw(held.def().name, 13), ax - lx - nw - mw) + ui.s(8), ny + ui.s(1), 11, Ui.TXT_DIM);
            // the weapon's condition, and a reload as it happens, on one thin line under its name
            float by = ny + ui.s(19), bw = ax - lx;
            ui.b.rect(lx, by, bw, ui.s(3), 0xFF1A1C1B);
            if (reloadT > 0) ui.b.rect(lx, by, bw * (1 - reloadT / Math.max(0.01f, reloadMax)), ui.s(3), 0xFFE2A034);
            else { float c2 = held.condition() / 100f; ui.b.rect(lx, by, bw * c2, ui.s(3), c2 < 0.25f ? Ui.RED : c2 < 0.5f ? Ui.AMBER : 0xFF7A8070); }
            if (held.jammed) { float jw = ui.tw("JAMMED - [R] CLEAR", 13) + ui.s(16); plate(ui, box.r() - jw, box.y - ui.s(30), jw, ui.s(24), 1); ui.textR("JAMMED - [R] CLEAR", box.r() - ui.s(8), box.y - ui.s(25), 13, Ui.RED); }
        } else if (held != null) {
            icon(ui, held, lx, ty + ui.s(2), ui.s(112), ui.s(40), 0xE0FFFFFF);
            Layout.fit(ui, held.def().name, lx, ty + ui.s(49), ax - lx, 13, 10, Ui.TXT_HI);
        } else ui.text(p.downed() ? "NO SIDEARM" : "UNARMED", lx, ty + ui.s(16), 15, Ui.TXT_DIM);

        // ---- what the body runs on: four meters, each with its name
        var s = raid.body;
        float my = box.y + ui.s(98), mw2 = (ax - lx - ui.s(14)) / 2;
        survival(ui, lx, my, mw2, "STAMINA", s.stamina / 100f, s.exhausted > 0 ? Ui.RED : 0xFFE0B830, true);
        survival(ui, lx + mw2 + ui.s(14), my, mw2, "FOCUS", s.focus / 100f, 0xFF6AA4D6, false);
        survival(ui, lx, my + ui.s(22), mw2, "FOOD", s.energy / 100f, s.energy < 20 ? Ui.RED : 0xFFB8935A, false);
        survival(ui, lx + mw2 + ui.s(14), my + ui.s(22), mw2, "WATER", s.hydration / 100f, s.hydration < 20 ? Ui.RED : 0xFF5AA0B8, false);
    }

    // ------------------------------------------------------------------ squad column (left edge)
    /** Draws the squad status column and returns the y just below it. */
    public float squad(Ui ui, String squadSel, int[] teamColors) {
        List<Actor> el = raid.element;
        float x = squadX > 0 ? squadX : ui.s(18), y = Math.max(Math.round(ui.h * 0.30f), squadMinY), w = ui.s(206), rh = ui.s(24);
        if (el.isEmpty()) { squadBox = new Box(x, y, 0, 0); return y; }
        String[] teams = {"gold", "red", "blue"};
        for (int i = 0; i < 3; i++) {
            boolean sel = teams[i].equals(squadSel);
            ui.b.rect(x + i * ui.s(69), y, ui.s(66), ui.s(16), sel ? Ui.alpha(teamColors[i], 0.9f) : 0x90101010);
            ui.textC(teams[i].toUpperCase() + " " + raid.team(teams[i]).size(), x + i * ui.s(69) + ui.s(33), y + ui.s(2), 10, sel ? 0xFF101010 : teamColors[i]);
        }
        float top = y;
        y += ui.s(20);
        for (Actor m : el) {
            boolean dead = m.dead, down = m.downed();
            Condition cond = cfg.condition(m.hp, m.maxHp);
            int col = dead ? 0xFF6A665C : down ? cfg.color(Condition.CRITICAL) : cfg.color(cond);
            // the row itself tells active from incapacitated at a glance: tint, bar, word
            ui.b.rect(x, y, w, rh - ui.s(2), dead ? 0x70080808 : down ? 0x905A1410 : 0x80101010);
            ui.b.rect(x, y, ui.s(4), rh - ui.s(2), dead ? 0xFF4A463E : teamColors["red".equals(m.team) ? 1 : 2]);
            float nx = x + ui.s(10);
            Layout.fit(ui, Raid.displayName(m), nx, y + ui.s(1), ui.s(92), 11, 8, dead ? 0xFF7A766C : Ui.TXT_HI);
            String word = dead ? "DEAD" : m.vital == Vital.REVIVING ? "REVIVING" : down ? "DOWN " + Math.max(0, (int) Math.ceil(m.bleedLeft)) + "s" : cond.label.toUpperCase();
            ui.textR(word, x + w - ui.s(6), y + ui.s(1), 10, col);
            float bx = nx, bw = w - ui.s(18), by = y + ui.s(15);
            ui.b.rect(bx, by, bw, ui.s(4), 0xFF1A1C1B);
            if (dead) { ui.b.rect(bx, by + ui.s(1), bw, 1, 0xFF4A463E); }
            else if (down) {
                float pulse = 0.65f + 0.35f * (float) Math.sin(raid.time * 6);
                ui.b.rect(bx, by, bw * raid.casualty.bleedFraction(m), ui.s(4), Ui.alpha(col, pulse));
                if (m.vital == Vital.REVIVING) ui.b.rect(bx, by + ui.s(4), bw * raid.casualty.reviveFraction(m), ui.s(2), Ui.GREEN);
            } else ui.b.rect(bx, by, bw * Math.max(0, Math.min(1, m.hp / m.maxHp)), ui.s(4), col);
            if (!dead && !down) { String ord = ("engage".equals(m.state) ? "FIGHTING" : m.sqOrder.toUpperCase()) + (m.qOrder != null ? " > " + m.qOrder.toUpperCase() : ""); ui.text(ord, nx + ui.s(96), y + ui.s(2), 8, m.qOrder != null ? Ui.AMBER : Ui.TXT_DIM); }
            y += rh;
        }
        ui.text("[T] team  [F1] follow  [F2] hold  [F3] move  [F4] sweep", x, y + ui.s(2), 9, Ui.TXT_DIM);
        ui.text("[Ctrl]+order queue  [F5] initiate  [F6] swap team", x, y + ui.s(14), 9, Ui.TXT_DIM);
        y += ui.s(30);
        squadBox = new Box(x, top, w, y - top);
        return y;
    }

    // ------------------------------------------------------------------ killfeed (left side, under the squad column)
    public void killfeed(Ui ui, float y) {
        float t = raid.time;
        feed.prune(t);
        // (1.1: set in the size the rest of the HUD is - it was 12 and 9 px lettering)
        float x = feedX > 0 ? feedX : ui.s(18), rh = ui.s(28), top = y + ui.s(6);
        final float FS = 15, TS = 11;
        y = top;
        float maxW = 0;
        for (int i = feed.entries.size() - 1; i >= 0; i--) {
            CombatFeed.Entry e = feed.entries.get(i);
            float age = t - e.time, left = cfg.feedLife - age;
            float in = Math.min(1, age / Math.max(0.01f, cfg.feedSlide)), alpha = Math.min(1, left / Math.max(0.01f, cfg.feedFade)) * in;
            float ox = (1 - in) * (1 - in) * -ui.s(60);                    // slides in from the edge, eases to rest
            String killer = e.killerId == 0 ? "" : Layout.elide(ui, e.killer, ui.s(150), FS), victim = Layout.elide(ui, e.victim, ui.s(150), FS);
            ItemInstance wi = iconFor(e.weapon);
            String tag = wi != null ? null : switch (e.cause) { case "blast" -> "EXPLOSION"; case "burn" -> "FIRE"; case "bleed" -> "BLED OUT"; case "acid" -> "ACID"; case "melee" -> "MELEE"; case "vehicle" -> "RUN DOWN"; case "blunt" -> "IMPACT"; default -> "KILLED"; };
            float kw = killer.isEmpty() ? 0 : ui.tw(killer, FS) + ui.s(8), iw = wi != null ? ui.s(54) : ui.tw(tag, TS) + ui.s(12), vw = ui.tw(victim, FS);
            float extra = (e.headshot ? ui.s(28) : 0) + (e.friendlyFire ? ui.s(28) : 0);
            float w = ui.s(10) + kw + iw + ui.s(8) + vw + extra + ui.s(8);
            Hud11.shade(ui, x + ox - ui.s(10), y - ui.s(3), w + ui.s(24), rh, alpha);
            ui.b.rect(x + ox, y + ui.s(2), Math.max(1, ui.s(2)), rh - ui.s(9), Ui.alpha(CombatFeed.color(e.killerId == 0 ? e.victimRel : e.killerRel), alpha));
            float cx = x + ox + ui.s(10);
            if (!killer.isEmpty()) { Hud11.txt(ui, killer, cx, y + ui.s(1), FS, Ui.alpha(CombatFeed.color(e.killerRel), alpha)); cx += kw; }
            if (wi != null) icon(ui, wi, cx, y, ui.s(50), rh - ui.s(6), Ui.alpha(0xFFFFFFFF, alpha));
            else { Hud11.txt(ui, tag, cx + ui.s(3), y + ui.s(4), TS, Ui.alpha(Ui.TXT, alpha)); }
            cx += iw + ui.s(4);
            if (e.headshot) { Hud11.txt(ui, "HS", cx, y + ui.s(4), TS, Ui.alpha(Ui.AMBER, alpha)); cx += ui.s(28); }
            Hud11.txt(ui, victim, cx, y + ui.s(1), FS, Ui.alpha(CombatFeed.color(e.victimRel), alpha));
            cx += vw + ui.s(6);
            if (e.friendlyFire) Hud11.txt(ui, "FF", cx, y + ui.s(4), TS, Ui.alpha(Ui.RED, alpha));
            maxW = Math.max(maxW, w);
            y += rh;
        }
        feedBox = new Box(x, top, maxW, y - top);
    }

    // ------------------------------------------------------------------ hit marker (centre screen)
    public void hitmarker(Ui ui) {
        float k = feed.marker(raid.time);
        if (k <= 0) return;
        int col = cfg.markerColors[feed.markerLevel];
        float cx = ui.w / 2f, cy = ui.h / 2f;
        // a quick pop on arrival, then it only fades: no growth, no travel
        float pop = 1 + 0.35f * Math.max(0, (k - 0.75f) / 0.25f);
        float gap = ui.s(cfg.markerGap) * pop, len = ui.s(cfg.markerSize) * (feed.markerLevel == CombatFeed.KILL ? 1.25f : 1f), th = Math.max(2, ui.s(2));
        float a = Math.min(1, k * 2.2f);
        for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) {
            ui.b.line(cx + sx * gap + sx, cy + sy * gap + sy, cx + sx * (gap + len) + sx, cy + sy * (gap + len) + sy, th + 2, Ui.alpha(0xB0000000, a));
            ui.b.line(cx + sx * gap, cy + sy * gap, cx + sx * (gap + len), cy + sy * (gap + len), th, Ui.alpha(col, a));
        }
    }

    // ------------------------------------------------------------------ speech bubbles (world space)
    private final float[] ndc = new float[3];
    private final List<Box> placed = new ArrayList<>();
    private final List<Actor> speakers = new ArrayList<>();

    /** Bubbles over the heads of whoever is speaking: anchored to the head, facing the camera, smaller and fainter with distance. */
    public void bubbles(Ui ui, Mat4 pv, float camX, float camY, float camZ) {
        bubblesDrawn = bubblesSkipped = 0;
        float t = raid.time;
        speakers.clear();
        for (Actor a : raid.actors) if (!a.isPlayer && t < a.sayUntil && !a.sayText.isEmpty() && !a.dead) speakers.add(a);
        if (speakers.isEmpty()) return;
        speakers.sort((p, q) -> Float.compare(dist2(p, camX, camY, camZ), dist2(q, camX, camY, camZ)));
        placed.clear();
        for (Actor a : speakers) {
            float d = (float) Math.sqrt(dist2(a, camX, camY, camZ));
            if (d > cfg.talkMaxDistance || bubblesDrawn >= cfg.talkMaxVisible) { bubblesSkipped++; continue; }
            float hx = a.x, hy = a.y + a.height() + 0.42f + (a.anim != null ? a.anim.visY : 0), hz = a.z;
            float w = pv.project(hx, hy, hz, ndc);
            if (w <= 0.05f || Math.abs(ndc[0]) > 1.05f || Math.abs(ndc[1]) > 1.05f) { bubblesSkipped++; continue; }
            // a voice carries through a wall, a bubble does not: out of sight it shows only close by, and fainter
            boolean visible = Physics.los(raid.world, camX, camY, camZ, hx, hy - 0.3f, hz);
            if (!visible && d > 9) { bubblesSkipped++; continue; }
            float age = t - a.sayAt, left = a.sayUntil - t;
            float alpha = Math.min(1, age / 0.15f) * Math.min(1, left / 0.4f) * (d > cfg.talkFadeFrom ? 1 - (d - cfg.talkFadeFrom) / Math.max(1f, cfg.talkMaxDistance - cfg.talkFadeFrom) : 1) * (visible ? 1 : 0.5f);
            if (alpha <= 0.02f) continue;
            float size = d < 8 ? 13 : d < 18 ? 12 : 11;
            float wrapW = ui.s(cfg.talkWrap) * (d < 18 ? 1 : 0.85f);
            List<String> lines = Layout.wrap(ui, a.sayText, wrapW, size);
            if (lines.size() > 4) { lines = new ArrayList<>(lines.subList(0, 4)); lines.set(3, Layout.elide(ui, lines.get(3) + " ...", wrapW, size)); }
            float lh = Layout.lineHeight(ui, size), tw = 0;
            for (String l : lines) tw = Math.max(tw, ui.tw(l, size));
            String who = Raid.displayName(a);
            tw = Math.max(tw, ui.tw(who, 9));
            float bw = tw + ui.s(16), bh = lines.size() * lh + ui.s(18);
            float sx = (ndc[0] * 0.5f + 0.5f) * ui.w, sy = (1 - (ndc[1] * 0.5f + 0.5f)) * ui.h;
            Box b = new Box(Math.round(sx - bw / 2), Math.round(sy - bh - ui.s(8)), bw, bh);
            b.x = Math.max(ui.s(6), Math.min(ui.w - bw - ui.s(6), b.x));
            // step up out of the way of a bubble already placed; give up rather than cover it
            int tries = 0;
            boolean clear = false;
            while (tries++ < 4) { clear = true; for (Box o : placed) if (b.overlaps(o)) { clear = false; b.y = o.y - bh - ui.s(4); break; } if (clear) break; }
            if (!clear || b.y < ui.s(4)) { bubblesSkipped++; continue; }
            placed.add(b);
            int rel = CombatFeed.relation(raid, a, a.faction), col = CombatFeed.color(rel);
            ui.b.rect(b.x, b.y, b.w, b.h, Ui.alpha(0xC80E100F, alpha));
            ui.b.rect(b.x, b.y, b.w, ui.s(2), Ui.alpha(col, alpha));
            if (Math.abs(sx - (b.x + b.w / 2)) < b.w / 2 - ui.s(6) && b.y + b.h < sy) ui.b.line(sx, b.y + b.h, sx, Math.min(sy - ui.s(2), b.y + b.h + ui.s(7)), Math.max(2, ui.s(2)), Ui.alpha(0xC80E100F, alpha));
            ui.text(who, b.x + ui.s(8), b.y + ui.s(4), 9, Ui.alpha(col, alpha));
            for (int i = 0; i < lines.size(); i++) ui.text(lines.get(i), b.x + ui.s(8), b.y + ui.s(15) + i * lh, size, Ui.alpha(Ui.TXT_HI, alpha));
            bubblesDrawn++;
        }
    }

    private static float dist2(Actor a, float x, float y, float z) { return (a.x - x) * (a.x - x) + (a.y - y) * (a.y - y) + (a.z - z) * (a.z - z); }

    // ------------------------------------------------------------------ minimap contacts
    /**
     * Contacts on the minimap (brief 10.1): squadmates always, as arrows turned to their facing; everyone else as
     * a dot, and only while the player has reason to know where they are - in line of sight now, or seen in the
     * last few seconds. The map never shows what is behind a wall nobody has looked round.
     */
    /** Tags drawn last frame, and of those how many were squadmates (gates). */
    public int tagsDrawn, tagsSquad;

    /**
     * Names over heads (0.6). A character close enough to recognise, and in view, has their name above them in the
     * colour the minimap gives them: red for a hostile, yellow for a neutral, green for a friend, blue for the squad.
     * A squadmate's name is always there - at any distance, and through walls (dimmer), because the element leader
     * always knows where their own people are. Under the name: Rank for a human, what it is for the infected.
     */
    public void nameTags(Ui ui, Mat4 pv, float camX, float camY, float camZ) {
        tagsDrawn = tagsSquad = 0;
        for (Actor a : raid.actors) {
            if (a.isPlayer || a.dead || a.seated) continue;
            boolean mate = a.mate();
            if (!mate && a.tier != 0) continue;
            float d = (float) Math.sqrt(dist2(a, camX, camY, camZ));
            float range = mate ? cfg.tagSquadRange : a.infected() ? cfg.tagInfectedRange : cfg.tagRange;
            if (d > range) continue;
            float hy = a.y + a.height() + 0.24f + (a.anim != null ? a.anim.visY : 0);
            float w = pv.project(a.x, hy, a.z, ndc);
            if (w <= 0.05f || Math.abs(ndc[0]) > 1.02f || Math.abs(ndc[1]) > 1.02f) continue;
            boolean visible = Physics.los(raid.world, camX, camY, camZ, a.x, hy - 0.35f, a.z);
            if (!visible && !mate) continue;
            int rel = CombatFeed.relation(raid, a, a.faction);
            int col = a.downed() ? 0xFFE8CF4A : CombatFeed.color(rel);
            float fade = mate ? (visible ? 1 : 0.55f) : Math.min(1, (range - d) / Math.max(1f, range * 0.3f));
            if (t(a) > 0) fade *= 0.0f + 1f;      // a speech bubble sits above the tag, never on it
            float size = cfg.tagSize * (d < 10 ? 1 : d < 40 ? 0.92f : 0.84f);
            String name = Raid.displayName(a);
            String sub = a.downed() ? "DOWN" : a.infected() ? "" : mate ? rankOf(a) : a.rankLevel > 0 ? absentialis.gameplay.progression.Progression.rankName(a.rankLevel) + " " + a.rankLevel : "";
            float sx = (ndc[0] * 0.5f + 0.5f) * ui.w, sy = (1 - (ndc[1] * 0.5f + 0.5f)) * ui.h;
            float tw = ui.tw(name, size), lh = Layout.lineHeight(ui, size);
            float tx = Math.round(sx - tw / 2), ty = Math.round(sy - lh - (sub.isEmpty() ? 0 : ui.s(11)));
            ui.text(name, tx + 1, ty + 1, size, Ui.alpha(0xD0000000, fade));
            ui.text(name, tx, ty, size, Ui.alpha(col, fade));
            if (!sub.isEmpty()) { float sw = ui.tw(sub, size - 3); ui.text(sub, Math.round(sx - sw / 2) + 1, ty + lh, size - 3, Ui.alpha(0xD0000000, fade)); ui.text(sub, Math.round(sx - sw / 2), ty + lh - 1, size - 3, Ui.alpha(Ui.mix(col, 0xFFFFFFFF, 0.35f), fade * 0.9f)); }
            tagsDrawn++;
            if (mate) tagsSquad++;
        }
    }

    private float t(Actor a) { return a.sayUntil - raid.time; }

    private String rankOf(Actor a) {
        absentialis.gameplay.squad.Roster.Mate m = raid.profile.squad.byId(a.mateId);
        return m == null ? "" : m.title();
    }

    public void minimapContacts(Ui ui, float sx, float sy, float sw, float wx0, float wz0, float span) {
        float k = sw / span, t = raid.time;
        Actor p = raid.player;
        ui.b.scissor((int) sx, (int) sy, (int) sw, (int) sw);
        for (Actor a : raid.actors) {
            if (a.isPlayer || a.dead) continue;
            float mx = sx + (a.x - wx0) * k, my = sy + (a.z - wz0) * k;
            if (mx < sx - 6 || my < sy - 6 || mx > sx + sw + 6 || my > sy + sw + 6) continue;
            if (a.mate()) {
                float fx = (float) -Math.sin(a.yaw), fz = (float) -Math.cos(a.yaw), rx = -fz, rz = fx, g = ui.s(5);
                int col = a.downed() ? 0xFFE8CF4A : CombatFeed.color(CombatFeed.SQUAD);
                // an arrowhead: tip ahead, two barbs behind
                ui.b.line(mx + fx * g * 1.5f, my + fz * g * 1.5f, mx - fx * g + rx * g, my - fz * g + rz * g, Math.max(2, ui.s(2.4f)), col);
                ui.b.line(mx + fx * g * 1.5f, my + fz * g * 1.5f, mx - fx * g - rx * g, my - fz * g - rz * g, Math.max(2, ui.s(2.4f)), col);
                ui.b.line(mx - fx * g + rx * g, my - fz * g + rz * g, mx - fx * g - rx * g, my - fz * g - rz * g, Math.max(2, ui.s(2f)), col);
                continue;
            }
            if (a.infected()) continue;                                // the brief's contacts are the human characters
            float d = a.dist(p);
            boolean sees = d < 70 && Physics.los(raid.world, p.x, p.eyeY(), p.z, a.x, a.y + a.height() * 0.8f, a.z);
            if (sees) a.seenByPlayer = t;
            float since = t - a.seenByPlayer;
            if (since > 4f) continue;
            int col = CombatFeed.color(CombatFeed.relation(raid, a, a.faction));
            float alpha = since < 0.5f ? 1 : Math.max(0, 1 - (since - 0.5f) / 3.5f), s = Math.max(3, ui.s(4));
            ui.b.rect(Math.round(mx - s / 2) - 1, Math.round(my - s / 2) - 1, s + 2, s + 2, Ui.alpha(0xFF0A0A0A, alpha));
            ui.b.rect(Math.round(mx - s / 2), Math.round(my - s / 2), s, s, Ui.alpha(col, alpha));
        }
        ui.b.noScissor();
    }

    // ------------------------------------------------------------------ inventory status panel (right side)
    /** The character status panel of the inventory screen: the same gauge, the same numbers, with room to spell them out. */
    public void statusPanel(Ui ui, Box box, ItemInstance held, ActorRenderer.Look look) {
        Actor p = raid.player;
        ui.panelDark(box.x, box.y, box.w, box.h);
        Box in = box.inset(ui.s(14));
        float r = Math.min(ui.s(52), in.w * 0.30f), cx = in.x + in.w / 2, cy = in.y + r + ui.s(8);
        gauge(ui, p, look, cx, cy, r, raid.time);
        Condition cond = cfg.condition(p.hp, p.maxHp);
        float y = cy + r + ui.s(12);
        String name = raid.profile.name == null || raid.profile.name.isEmpty() ? "Survivor" : raid.profile.name;
        float ns = Layout.fitSize(ui, name, in.w, 17, 10);
        ui.textC(Layout.elide(ui, name, in.w, ns), cx, y, ns, Ui.TXT_HI);
        y += ui.s(24);
        ui.textC(raid.profile.prog.title(), cx, y, 12, Ui.TXT_DIM);
        y += ui.s(24);
        y = row(ui, in, y, "Health", Math.max(0, Math.round(p.hp)) + " / " + Math.round(p.maxHp), cfg.color(cond));
        y = row(ui, in, y, "Condition", p.downed() ? "Downed" : cond.label, p.downed() ? cfg.color(Condition.CRITICAL) : cfg.color(cond));
        int herbs = raid.profile.herbs, cap = cfg.herbCap();
        y = row(ui, in, y, "Maximum", Math.round(p.maxHp) + (herbs >= cap ? "  (limit)" : "  (+" + cfg.perHerb + " / Yellow Herb)"), Ui.TXT);
        if (p.shieldMax > 0) y = row(ui, in, y, "Body armor", Math.round(p.shield) + " / " + Math.round(p.shieldMax), Ui.BLUE);
        y += ui.s(6);
        ui.b.rect(in.x, y, in.w, 1, 0x40FFFFFF);
        y += ui.s(8);
        // (1.0: the weapon in the hands is not shown again here - it is in its slot, and Inspect opens it)
        // whatever else the body is carrying: only what is true right now
        var s = raid.body;
        y = meter(ui, in, y, "Stamina", s.stamina / 100f, 0xFFE0B830);
        y = meter(ui, in, y, "Focus", s.focus / 100f, 0xFF6AA4D6);
        y = meter(ui, in, y, "Food", s.energy / 100f, s.energy < 20 ? Ui.RED : 0xFFB8935A);
        y = meter(ui, in, y, "Water", s.hydration / 100f, s.hydration < 20 ? Ui.RED : 0xFF5AA0B8);
        List<String> flags = new ArrayList<>();
        if (s.bleedHeavy) flags.add("Heavy bleeding"); else if (s.bleedLight) flags.add("Bleeding");
        if (s.fracture) flags.add("Fracture");
        if (s.armInjury > 0.25f) flags.add("Arm wounded");
        if (s.legInjury > 0.25f) flags.add("Leg wounded");
        if (s.infection > 0) flags.add(InfectionSystem.stage(s.infection));
        if (s.rad > 5) flags.add("Radiation " + Math.round(s.rad));
        if (!flags.isEmpty() && y < in.b() - ui.s(16)) Layout.paragraph(ui, String.join("  -  ", flags), new Box(in.x, y + ui.s(6), in.w, in.b() - y - ui.s(6)), 13, 10, Ui.AMBER);
    }

    private float row(Ui ui, Box in, float y, String label, String value, int col) {
        ui.text(label, in.x, y, 13, Ui.TXT_DIM);
        float lw = ui.tw(label, 13) + ui.s(10);
        ui.textR(Layout.elide(ui, value, in.w - lw, 13), in.r(), y, 13, col);
        return y + ui.s(20);
    }

    private float meter(Ui ui, Box in, float y, String label, float f, int col) {
        ui.text(label, in.x, y, 13, Ui.TXT_DIM);
        float bx = in.x + ui.s(72), bw = in.w - ui.s(72);
        ui.b.rect(bx, y + ui.s(5), bw, ui.s(7), 0xFF1A1C1B);
        ui.b.rect(bx, y + ui.s(5), bw * Math.max(0, Math.min(1, f)), ui.s(7), col);
        return y + ui.s(21);
    }

    public void dispose() { portraits.clear(); }
}
