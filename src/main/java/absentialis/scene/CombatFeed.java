package absentialis.scene;

import absentialis.engine.events.EventBus;
import absentialis.engine.events.Events;
import absentialis.entity.Actor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hit confirmation and the killfeed as projections of confirmed combat events (0.5, brief 9.2 and 12). Nothing
 * here is predicted: a marker appears because the raid applied damage and said so on the event bus, a feed line
 * because the raid reported exactly one death. This class holds only what the HUD needs to draw them.
 */
public final class CombatFeed {
    public static final int HIT = 0, DOWN = 1, KILL = 2;

    public static final class Entry {
        public int killerId, victimId, killerRel, victimRel;
        public String killer = "", victim = "", weapon = "", cause = "";
        public boolean friendlyFire, headshot;
        public float time;
    }

    private final Raid raid;
    public final List<Entry> entries = new ArrayList<>();
    private final Set<Integer> reported = new HashSet<>();
    /** The marker being shown: its outcome, when it was raised, and the hit that raised it. */
    public int markerLevel = -1;
    public float markerTime = -99;
    /** Counters (gates): events seen per outcome, kills refused as duplicates, incapacitations (never a feed line). */
    public int hits, downs, kills, duplicates;
    /** Deaths that were counted but given no feed line (the ordinary infected, 0.7). */
    public int quiet;

    public CombatFeed(Raid raid) { this.raid = raid; }
    public int animalKills;

    public void attach(EventBus bus) {
        bus.on(Events.HitConfirmed.class, this::onHit);
        bus.on(Events.ActorDowned.class, this::onDowned);
        bus.on(Events.KillConfirmed.class, this::onKill);
        bus.on(Events.AnimalKilled.class, e -> { animalKills++; if (e.killerId() == raid.player.id) mark(KILL, e.time()); });      // 1.2: the marker, and no line in the feed
    }

    /** Raises the marker unless a more significant outcome is still showing: Kill > Incapacitation > Hit. */
    private void mark(int level, float time) {
        float life = level == KILL ? raid.combat.markerLifeKill : raid.combat.markerLife;
        boolean showing = markerLevel >= 0 && time - markerTime < (markerLevel == KILL ? raid.combat.markerLifeKill : raid.combat.markerLife);
        if (showing && level < markerLevel) return;
        markerLevel = level; markerTime = time;
        if (life <= 0) markerLevel = -1;
    }

    void onHit(Events.HitConfirmed e) {
        if (e.attackerId() != raid.player.id) return;
        hits++;
        mark(HIT, e.time());
    }

    void onDowned(Events.ActorDowned e) {
        downs++;                                         // an incapacitation is not a kill and never a feed line
        if (e.attackerId() == raid.player.id) mark(DOWN, e.time());
    }

    void onKill(Events.KillConfirmed e) {
        if (!reported.add(e.victimId())) { duplicates++; return; }      // one line per death, whatever arrives
        kills++;
        if (e.killerId() == raid.player.id && e.victimId() != raid.player.id) mark(KILL, e.time());
        // 0.7: an ordinary infected going down is not a line in the feed (the hit marker still says it died). The special kinds are.
        Actor dead = raid.actor(e.victimId());
        if ("infected".equals(e.victimFaction()) && (dead == null || !absentialis.neuralai.integration.Archetypes.special(dead.archetype))) { quiet++; return; }
        Entry en = new Entry();
        en.killerId = e.killerId(); en.victimId = e.victimId();
        en.killer = e.killerName(); en.victim = e.victimName(); en.weapon = e.weaponId(); en.cause = e.cause();
        en.friendlyFire = e.friendlyFire(); en.headshot = e.headshot(); en.time = e.time();
        en.killerRel = relation(raid, raid.actor(e.killerId()), e.killerFaction());
        en.victimRel = relation(raid, raid.actor(e.victimId()), e.victimFaction());
        entries.add(en);
        while (entries.size() > raid.combat.feedMax) entries.remove(0);
    }

    public void prune(float time) { entries.removeIf(en -> time - en.time > raid.combat.feedLife); }

    /** Strength 0..1 of the marker right now (0 = nothing to draw). */
    public float marker(float time) {
        if (markerLevel < 0) return 0;
        float life = markerLevel == KILL ? raid.combat.markerLifeKill : raid.combat.markerLife;
        float age = time - markerTime;
        return age < 0 || age >= life ? 0 : 1 - age / life;
    }

    // ---- who is who, for every piece of the interface that colours a character
    public static final int SQUAD = 0, FRIENDLY = 1, NEUTRAL = 2, HOSTILE = 3, SELF = 4;
    /** Blue squadmates, green other friendlies, yellow neutrals, red hostiles (brief 10.1); the player is white. */
    public static final int[] COLORS = {0xFF4F9BE8, 0xFF62C46A, 0xFFE8CF4A, 0xFFE2483A, 0xFFF4EEDC};

    public static int relation(Raid r, Actor a, String faction) {
        if (a != null && a.isPlayer) return SELF;
        if (a != null && a.mate() || a == null && "player".equals(faction)) return SQUAD;
        if (a == null) return "infected".equals(faction) ? HOSTILE : r.profile.rep(faction) >= 25 ? FRIENDLY : NEUTRAL;
        if (a.infected() || a.hostileToPlayer) return HOSTILE;
        return r.profile.rep(a.faction) >= 25 || a.helped || a.paidOff ? FRIENDLY : NEUTRAL;
    }

    public static int color(int relation) { return COLORS[Math.max(0, Math.min(COLORS.length - 1, relation))]; }
}
