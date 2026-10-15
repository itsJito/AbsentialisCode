package absentialis.scene;

import absentialis.gameplay.progression.Progression;
import absentialis.ui.Ui;
import java.util.ArrayList;
import java.util.List;

/**
 * Experience, seen as it is earned (0.6) - and, since 0.8, only then. The bar is not part of the HUD: it is not on
 * screen at all until something earns experience, comes up small in the top corner with the Rank's insignia for a few
 * seconds, and goes again. The minutes simply spent in the Zone still count, but say nothing. When what has been
 * earned carries the survivor over a Rank, that is announced once - the new insignia, the new title, and the sound
 * of it.
 *
 * Nothing here awards anything. Experience is still settled once, at the debrief, by {@link RaidResolver}; this
 * class watches the same counters the resolver will read and prices them with the resolver's own constants, so the
 * figure on screen is the figure that will be paid (halved if the operation fails).
 */
public final class XpFeed {
    /** One line of feedback: how much, what for, and when it appeared. */
    public static final class Toast { public String label; public int amount; public float at; }

    private final Raid raid;
    public final List<Toast> toasts = new ArrayList<>();
    private int containers, cells, documents, aided, minutes, stealth, hotspots;
    private boolean power;
    private float lastGain = -99, shownProgress = -1;
    /** Seconds a line stays, how long the bar stays after a gain, how long it takes to come and go, and how long a rank-up is announced. */
    public static float TOAST_LIFE = 3.2f, HOLD = 4.5f, FADE = 0.45f, RANK_NOTICE = 4.2f;
    /** The Rank the bar last showed, and the rank-up being announced (0 = none). */
    private int shownRank = -1;
    public int noticeRank;
    public float noticeAt = -99;
    /** Rank-ups announced this operation (tests). */
    public int notices;
    /** Asked to play a sound by name (the screen that owns the feed supplies it). */
    public java.util.function.Consumer<String> sound;

    public XpFeed(Raid raid) { this.raid = raid; }

    /** Experience this operation has earned so far, by the resolver's own prices (the extraction bonus and loot value come at the debrief). */
    public int pending() {
        Raid.Debrief d = raid.debrief;
        return d.containersSearched * RaidResolver.XP_CONTAINER + d.cellsRevealed * RaidResolver.XP_CELL + d.documents * RaidResolver.XP_DOCUMENT + d.aided * RaidResolver.XP_AID
                + (d.powerRestored ? RaidResolver.XP_POWER : 0) + d.hotspots * raid.events.xp + Math.min(d.stealthKills, RaidResolver.XP_STEALTH_CAP) * RaidResolver.XP_STEALTH_KILL + Math.round(Math.min(raid.time, RaidResolver.XP_TIME_CAP) / 60f * RaidResolver.XP_PER_MINUTE);
    }

    private void post(String label, int amount) {
        if (amount <= 0) return;
        lastGain = raid.time;
        // a run of the same thing (map cells while walking) adds to the line already showing instead of stacking a column of them
        for (Toast t : toasts) if (t.label.equals(label) && raid.time - t.at < TOAST_LIFE * 0.75f) { t.amount += amount; t.at = raid.time; return; }
        Toast t = new Toast();
        t.label = label; t.amount = amount; t.at = raid.time;
        toasts.add(t);
        while (toasts.size() > 3) toasts.remove(0);
    }

    /** How visible the bar is right now, 0 to 1: nothing until experience is earned, then up, held, and away. */
    public float visible() {
        float since = raid.time - lastGain;
        if (since < 0 || since > HOLD + FADE) return 0;
        return Math.min(1, since / (FADE * 0.4f)) * Math.min(1, (HOLD + FADE - since) / FADE);
    }

    /** True while a rank-up is being announced. */
    public boolean announcing() { return noticeRank > 0 && raid.time - noticeAt < RANK_NOTICE; }

    /** Looks at what has been done since the last frame and posts a line for each thing that earned experience. */
    public void update() {
        Raid.Debrief d = raid.debrief;
        if (d.containersSearched > containers) post("Container searched", (d.containersSearched - containers) * RaidResolver.XP_CONTAINER);
        if (d.cellsRevealed > cells) post("Reconnaissance", (d.cellsRevealed - cells) * RaidResolver.XP_CELL);
        if (d.documents > documents) post("Intel recovered", (d.documents - documents) * RaidResolver.XP_DOCUMENT);
        if (d.aided > aided) post("Aided a loner", (d.aided - aided) * RaidResolver.XP_AID);
        if (d.powerRestored && !power) post("Restored district power", RaidResolver.XP_POWER);
        if (d.hotspots > hotspots) { post("Hotspot settled", (d.hotspots - hotspots) * raid.events.xp); hotspots = d.hotspots; }
        if (d.stealthKills > stealth) { post("Silent takedown", (Math.min(d.stealthKills, RaidResolver.XP_STEALTH_CAP) - Math.min(stealth, RaidResolver.XP_STEALTH_CAP)) * RaidResolver.XP_STEALTH_KILL); stealth = d.stealthKills; }
        // time in the Zone is counted and paid, and is not news: it does not bring the bar up
        containers = d.containersSearched; cells = d.cellsRevealed; documents = d.documents; aided = d.aided; power = d.powerRestored; minutes = (int) (raid.time / 60f);
        toasts.removeIf(t -> raid.time - t.at > TOAST_LIFE);
        // over a Rank? Said once.
        float xpMul = 1 + raid.profile.loadout.charm("xp");
        int rank = (int) raid.profile.prog.preview(Math.round(pending() * xpMul))[0];
        if (shownRank < 0) shownRank = rank;
        if (rank > shownRank) {
            shownRank = rank; noticeRank = rank; noticeAt = raid.time; notices++;
            lastGain = raid.time;
            if (sound != null) sound.accept("rank_up");
        }
    }

    /** 1.1: how long the progress toward the next Rank is shown after a kill (seconds), and when the last kill was. */
    public static float KILL_SHOW = 3f;
    private float lastKill = -99;
    private int killsSeen = -1;

    /** True while the progress toward the next Rank is on screen: for three seconds after a kill, and at no other time. */
    public boolean barVisible() { return raid.time - lastKill >= 0 && raid.time - lastKill < KILL_SHOW; }

    private void watchKills() {
        int n = 0;
        for (int v : raid.debrief.kills.values()) n += v;
        if (killsSeen < 0) killsSeen = n;
        if (n > killsSeen) { killsSeen = n; lastKill = raid.time; }
    }

    /**
     * 1.1: everything about experience is said at the top centre of the screen. What was earned is a line there for a
     * few seconds - the amount and what it was for - and that is all an ordinary gain shows. How far along the Rank
     * is appears only after a kill, for three seconds. A promotion is announced in the same place ({@link #promotion}).
     * Returns where the top-left notice column may start (it no longer has anything of this above it).
     */
    public float draw(Ui ui) {
        watchKills();
        Progression pr = raid.profile.prog;
        float xpMul = 1 + raid.profile.loadout.charm("xp");
        int pend = Math.round(pending() * xpMul);
        float[] pv = pr.preview(pend);
        int rank = (int) pv[0];
        float target = pv[1];
        if (shownProgress < 0) shownProgress = target;
        shownProgress += ((target < shownProgress - 0.5f ? 1.02f : target) - shownProgress) * Math.min(1, ui.dt * 4);
        if (shownProgress > 1) shownProgress = 0;
        float cx = Math.round(ui.w / 2f), y = Math.round(ui.h * 0.045f) + ui.s(10);
        boolean promo = announcing();
        // ---- after a kill: the Rank and how far along it is, for three seconds
        barAlpha += (((barVisible() && !promo) ? 1f : 0f) - barAlpha) * Math.min(1, ui.dt / 0.2f);
        if (barAlpha > 0.01f) {
            float w = ui.s(300), h = Math.max(2, ui.s(3)), is = ui.s(26);
            Hud11.shade(ui, cx - w / 2 - ui.s(40), y - ui.s(10), w + ui.s(80), ui.s(52), barAlpha);
            absentialis.ui.Insignia.draw(ui, rank, cx - w / 2 - is - ui.s(6), y - ui.s(2), is, Ui.alpha(0xFFD6CEB8, barAlpha));
            Hud11.txt(ui, Progression.rankName(rank).toUpperCase(), cx - w / 2, y - ui.s(4), 15, Ui.alpha(Ui.TXT_HI, barAlpha));
            Hud11.numR(ui, pr.master() ? "MAX" : Math.round(target * 100) + "%", cx + w / 2, y - ui.s(4), 15, Ui.alpha(Ui.TXT, barAlpha));
            ui.b.rect(cx - w / 2, y + ui.s(18), w, h, Ui.alpha(0xFF1A1C1B, barAlpha));
            float banked = rank == pr.rank() ? Math.max(0, Math.min(1, pr.rankProgress())) : 0;
            ui.b.rect(cx - w / 2, y + ui.s(18), w * banked, h, Ui.alpha(0xFF8A6420, barAlpha));
            ui.b.rect(cx - w / 2 + w * banked, y + ui.s(18), w * Math.max(0, Math.min(1, shownProgress) - banked), h, Ui.alpha(0xFFE2A034, barAlpha));
            y += ui.s(34);
        }
        // ---- what was earned: one line each, centred, newest at the top
        if (!promo) for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            float age = raid.time - t.at, a = Math.min(1, age / 0.2f) * Math.min(1, (TOAST_LIFE - age) / 0.2f);
            if (a <= 0.02f) continue;
            String sx = "+" + String.format("%,d", Math.round(t.amount * xpMul)) + " XP";
            float w1 = Hud11.numW(ui, sx, 17), w2 = ui.tw(t.label, 15), tw = w1 + ui.s(12) + w2;
            Hud11.shade(ui, cx - tw / 2 - ui.s(30), y - ui.s(4), tw + ui.s(60), ui.s(30), a);
            Hud11.numR(ui, sx, cx - tw / 2 + w1, y, 17, Ui.alpha(0xFFE8C860, a));
            Hud11.txt(ui, t.label, cx - tw / 2 + w1 + ui.s(12), y + ui.s(2), 15, Ui.alpha(Ui.TXT, a));
            y += ui.s(28);
        }
        if (promo) promotion(ui, cx, Math.round(ui.h * 0.045f));
        return ui.s(18);
    }
    private float barAlpha;

    /**
     * 1.1, the promotion. It used to be a line of text among the notices. Now: the top centre of the screen goes dark
     * behind two gold rules that run out from the middle; the insignia of the new Rank drops into the gap between them,
     * large, and settles; chevrons step out to either side of it like the wings of a badge; over it, small and spaced,
     * "PROMOTED"; beside it the Rank by name and its number. It holds for a little over three seconds and goes the way
     * it came. Nothing else on the screen moves for it.
     */
    private void promotion(Ui ui, float cx, float top) {
        float age = raid.time - noticeAt, life = RANK_NOTICE, in = Math.min(1, age / 0.35f), out = Math.min(1, (life - age) / 0.45f), a = Math.min(1, age / 0.12f) * out;
        float e = 1 - (1 - in) * (1 - in) * (1 - in);
        int gold = 0xFFE8B83C, pale = 0xFFF6E9B8, dark = 0xFF14110A;
        float w = ui.s(620) * e, h = ui.s(128), y = top + ui.s(6);
        // the field: dark, wide in the middle, with the first flash of the announcement over it
        Hud11.shade(ui, cx - ui.s(400), y - ui.s(16), ui.s(800), h + ui.s(40), a);
        ui.b.rect(cx - w / 2, y + ui.s(8), w, h - ui.s(16), Ui.alpha(dark, a * 0.72f));
        if (age < 0.22f) ui.b.rect(cx - w / 2, y + ui.s(8), w, h - ui.s(16), Ui.alpha(0xFFFFF2C8, (1 - age / 0.22f) * 0.55f));
        // the two rules, and the ticks at their ends
        float th = Math.max(2, ui.s(3));
        ui.b.rect(cx - w / 2, y + ui.s(8), w, th, Ui.alpha(gold, a)); ui.b.rect(cx - w / 2, y + h - ui.s(8) - th, w, th, Ui.alpha(gold, a));
        ui.b.rect(cx - w / 2, y + ui.s(8) + th + ui.s(2), w, 1, Ui.alpha(gold, a * 0.5f)); ui.b.rect(cx - w / 2, y + h - ui.s(8) - th - ui.s(3), w, 1, Ui.alpha(gold, a * 0.5f));
        for (int k = 0; k < 5; k++) for (int side = -1; side <= 1; side += 2) {
            float tx = cx + side * (w / 2 - ui.s(10) - k * ui.s(9));
            ui.b.line(tx - ui.s(3), y + h - ui.s(16), tx + ui.s(3), y + ui.s(16), Math.max(1.5f, ui.s(2)), Ui.alpha(gold, a * (0.55f - k * 0.09f)));
        }
        // the insignia: dropped in large, settling
        float st = Math.min(1, age / 0.42f), over = 1 + 1.6f * (float) Math.pow(1 - st, 3);
        float is = ui.s(54) * over, iy = y + h / 2 - is / 2 + ui.s(2);
        absentialis.ui.Insignia.draw(ui, noticeRank, cx - is / 2 + ui.s(2), iy + ui.s(2), is, Ui.alpha(0xFF000000, a * 0.6f));
        absentialis.ui.Insignia.draw(ui, noticeRank, cx - is / 2, iy, is, Ui.alpha(pale, a));
        // the wings: three chevrons a side, stepping out one after another
        for (int k = 0; k < 3; k++) {
            float kt = Math.max(0, Math.min(1, (age - 0.22f - k * 0.08f) / 0.25f)), ke = 1 - (1 - kt) * (1 - kt);
            if (kt <= 0) continue;
            for (int side = -1; side <= 1; side += 2) {
                float bx = cx + side * (ui.s(46) + k * ui.s(20) * ke), by = y + h / 2 + ui.s(2), ch = ui.s(15) - k * ui.s(2), cw = ui.s(9);
                float lw = Math.max(2, ui.s(3.2f) - k * 0.5f);
                ui.b.line(bx, by - ch, bx + side * cw, by, lw, Ui.alpha(gold, a * ke * (1 - k * 0.2f)));
                ui.b.line(bx + side * cw, by, bx, by + ch, lw, Ui.alpha(gold, a * ke * (1 - k * 0.2f)));
            }
        }
        // the words
        float wt = Math.max(0, Math.min(1, (age - 0.3f) / 0.3f));
        Hud11.txtC(ui, "P R O M O T E D", cx, y + ui.s(17), 14, Ui.alpha(gold, a * wt));
        String name = Progression.rankName(noticeRank).toUpperCase();
        float ns = 26;
        Hud11.txt(ui, name, cx - ui.s(124) - ui.tw(name, ns) - (1 - wt) * ui.s(24), y + h / 2 - ui.s(13), ns, Ui.alpha(0xFFFFFFFF, a * wt));
        Hud11.txt(ui, "RANK " + noticeRank, cx + ui.s(124) + (1 - wt) * ui.s(24), y + h / 2 - ui.s(13), ns, Ui.alpha(pale, a * wt));
        Hud11.txtC(ui, "FIELD PROMOTION", cx, y + h - ui.s(31), 12, Ui.alpha(0xFFB8AE90, a * wt));
    }
}
