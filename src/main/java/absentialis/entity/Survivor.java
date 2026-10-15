package absentialis.entity;

import absentialis.gameplay.items.ItemDef;

/**
 * Player body state (GDD 9, addendum P2.4): the three cores - Health (red), Stamina (yellow) and
 * Focus (recoil control) - plus injuries, hunger, thirst, pain, radiation and infection. Each core
 * remembers when it last changed so the HUD can show it only on change.
 */
public final class Survivor {
    public float stamina = 100, focus = 100, energy = 80, hydration = 80, pain, rad, infection;
    public boolean bleedLight, bleedHeavy, fracture;
    /** Localized injuries 0..1: a hurt arm ruins weapon handling, a hurt leg slows you down. Treatment eases them. */
    public float armInjury, legInjury;
    public float healthChanged = -99, staminaChanged = -99, focusChanged = -99; // raid time of last change
    public float exhausted;     // seconds of forced recovery after hitting 0 stamina
    public float useTimer;      // in-progress item use
    /** Yellow Herbs taken and not yet written to the profile (the raid applies them and the new maximum at once). */
    public int herbsTaken;
    public ItemDef using;
    public float staminaDrainMul = 1;
    /** Antibiotics hold the infection where it is until this raid time; the stage last announced to the player. */
    public float suppressUntil = -99;
    public int infectionStage;
    private float now;

    /** Screen tint for the infection, 0..1: nothing while merely bitten, a slow sick pulse from fever on. */
    public float infectionFx(float t) {
        if (infection < 25) return 0;
        float k = Math.min(1, (infection - 25) / 65f);
        return k * (0.72f + 0.28f * (float) Math.sin(t * (1.6f + k * 2.2f)));
    }

    /** Movement speed points (addendum): walk 90, run 180, crouch 45 -> metres/s = points / 25. */
    public static final float WALK = 90, RUN = 180, CROUCH = 45;

    public float moveSpeed(Actor a, boolean sprint, boolean walkKey, float massKg, float capacityKg, float weaponMass) {
        float pts = a.crouch ? CROUCH : sprint && canSprint() ? RUN : walkKey ? WALK * 0.55f : WALK;
        float over = Math.max(0, massKg - capacityKg) / Math.max(1, capacityKg);
        float load = 1f - Math.min(0.5f, massKg / Math.max(1, capacityKg) * 0.12f) - Math.min(0.4f, over * 0.8f);
        float weapon = 1f - Math.min(0.15f, weaponMass * 0.012f);
        float injury = (fracture ? 0.6f : 1f) * (1f - 0.35f * legInjury);
        return pts / 25f * load * weapon * injury * a.speedMul;
    }

    /**
     * 1.1: out of breath. Set when a sprint runs the stamina down to where it has to stop, and held until a real breath
     * has been taken ({@link #SECOND_WIND}). Without it a held sprint key ran, stopped at 8, recovered to 8.1 and ran
     * again several times a second - and the camera, the field of view and the gait went with it each time.
     */
    public boolean winded;
    public static final float SPRINT_FLOOR = 8, SECOND_WIND = 40;

    public boolean canSprint() { return stamina > SPRINT_FLOOR && !winded && exhausted <= 0 && !fracture; }

    public void update(Actor a, float dt, float t, boolean moving, boolean sprinting, boolean aiming, float weaponMass, float radiation, boolean gasMask, float hungerMul) {
        float oldS = stamina, oldF = focus, oldH = a.hp;
        now = t;
        if (sprinting && moving && canSprint()) stamina -= dt * (14 + weaponMass * 1.2f) * staminaDrainMul;
        else if (exhausted > 0) exhausted -= dt;
        else stamina += dt * (moving ? 7 : 14) * (energy < 15 || hydration < 15 ? 0.5f : 1) * (infection >= 25 ? 0.65f : 1);
        if (stamina <= 0) { stamina = 0; exhausted = 3; }
        stamina = Math.min(100, stamina);
        if (sprinting && stamina <= SPRINT_FLOOR) winded = true;
        else if (stamina >= SECOND_WIND) winded = false;
        // focus: drains while steadying an aimed weapon, recovers otherwise
        if (aiming) focus -= dt * (6 + weaponMass * 1.5f);
        else focus += dt * 16;
        focus = Math.max(0, Math.min(100, focus));
        // survival drains
        energy = Math.max(0, energy - dt * 0.018f * hungerMul);
        hydration = Math.max(0, hydration - dt * 0.03f * hungerMul);
        if (energy <= 0 || hydration <= 0) a.hp -= dt * 0.25f;
        if (bleedLight) a.hp -= dt * 0.35f;
        if (bleedHeavy) a.hp -= dt * 1.4f;
        pain = Math.max(0, pain - dt * 0.6f);
        armInjury = Math.max(0, armInjury - dt * 0.004f);
        legInjury = Math.max(0, legInjury - dt * 0.003f);
        if (radiation > 0) rad = Math.min(100, rad + radiation * dt * (gasMask ? 0.35f : 1f));
        if (rad > 60) a.hp -= dt * (rad - 60) * 0.02f;
        // the infection's course is run by scene.InfectionSystem; these are its symptoms
        if (infection >= 75) { a.hp -= dt * 0.38f; pain = Math.max(pain, 20); focus = Math.min(focus, 45); }
        else if (infection >= 50) { a.hp -= dt * 0.14f; focus = Math.min(focus, 70); }
        if (Math.abs(stamina - oldS) > 0.01f) staminaChanged = t;
        if (Math.abs(focus - oldF) > 0.01f) focusChanged = t;
        if (Math.abs(a.hp - oldH) > 0.001f) healthChanged = t;
    }

    /** Recoil multiplier from focus: steady when focused, sloppy when spent. */
    public float recoilMul() { return 1.35f - 0.55f * (focus / 100f) + (pain > 30 ? 0.1f : 0) + 0.6f * armInjury; }

    /** Applies a consumable's effect. Returns a short feedback line. */
    public String apply(Actor a, ItemDef d) {
        StringBuilder s = new StringBuilder();
        if (d.medical != null) {
            ItemDef.Medical m = d.medical;
            if (m.maxHealth) herbsTaken++;
            if (m.heal > 0) { a.hp = Math.min(a.maxHp, a.hp + m.heal); s.append("+").append((int) m.heal).append(" health "); }
            if (m.healFrac > 0) { float h = a.maxHp * m.healFrac; a.hp = Math.min(a.maxHp, a.hp + h); s.append("+").append(Math.round(h)).append(" health "); }
            if (m.stopLight && bleedLight) { bleedLight = false; s.append("bleeding stopped "); }
            if (m.stopHeavy && bleedHeavy) { bleedHeavy = false; s.append("heavy bleed clamped "); }
            if (m.fracture && fracture) { fracture = false; s.append("fracture splinted "); }
            if (m.fracture) legInjury = 0;
            if (m.heal > 0 || m.healFrac > 0) { float k = m.healFrac > 0 ? 1 - m.healFrac : 0.5f; armInjury *= k; legInjury *= k; }
            if (m.pain > 0) { pain = Math.max(pain, m.pain); s.append("pain suppressed "); }
            if (m.rad > 0) { rad = Math.max(0, rad - m.rad); s.append("-").append((int) m.rad).append(" rad "); }
            if (m.antiviral) { s.append(infection > 0 ? "infection cured " : "no infection to treat "); infection = 0; infectionStage = 0; suppressUntil = -99; }
            else if (m.infection) {
                // antibiotics do not kill the virus: they buy time
                if (infection > 0) { suppressUntil = now + 120; infection = Math.max(1, infection - 6); s.append("infection held for two minutes - it still needs Antiviral Medicine "); }
                else s.append("nothing to treat ");
            }
        }
        if (d.food != null) {
            ItemDef.Food f = d.food;
            energy = Math.max(0, Math.min(100, energy + f.energy));
            hydration = Math.max(0, Math.min(100, hydration + f.hydration));
            if (f.infection > 0) infection = Math.min(99, infection + f.infection * 22);
            if (f.fatigue < 0) stamina = Math.min(100, stamina + 40);
            s.append(f.energy > 0 ? "+food " : "").append(f.hydration > 0 ? "+water " : "");
        }
        return s.toString().trim();
    }
}
