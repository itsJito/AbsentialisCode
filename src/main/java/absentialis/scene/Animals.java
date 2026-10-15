package absentialis.scene;

import absentialis.engine.math.Mat4;
import absentialis.engine.render.ModelRenderer;
import absentialis.engine.render.Tex;
import absentialis.engine.voxel.VoxModel;
import absentialis.entity.Actor;
import absentialis.entity.Physics;
import absentialis.gameplay.items.Content;
import absentialis.gameplay.items.ItemInstance;
import absentialis.neuralai.animal.AnimalBrain;
import absentialis.neuralai.animal.AnimalBrain.Intent;
import absentialis.neuralai.animal.AnimalBrain.Move;
import absentialis.neuralai.animal.AnimalBrain.Thing;
import absentialis.world.District;
import absentialis.world.World;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.1, animals - and the two kinds of the infected that go on four legs or eight. Everything here is a body for one
 * of the minds in {@link absentialis.neuralai.animal.AnimalBrain}: this class senses for it, moves it, lets it bite
 * and be shot, makes its noise, drops what it was carrying and draws it.
 *
 * Each kind is built to the brief's measurements, in voxels (width x height x depth), as separate rigid parts hung
 * from pivots - a head that turns, legs that swing from the hip, wings that beat from the shoulder, a tail that wags,
 * a jaw that opens - and every one of them is animated from what the body is actually doing: the legs by the ground
 * it has covered, the wings by whether it is in the air, the head by what it is looking at.
 *
 *   Husky ........... white. Head 6x6x4, body 6x9x6, mane 8x6x7, legs 2x8x2, tail 2x8x2, muzzle 3x3x4, ears 2x2x1.
 *   Crow ............ black. Head 3x3x3, body 4x5x6, wings 1x4x6, legs 1x3x1, tail 3x1x5, beak 1x1x3.
 *   Bat ............. black. Head 4x3x2, body 3x5x2, wings: inner 2x7 and outer 6x8, a single voxel thick.
 *   Chicken ......... white. Head 4x6x3, body 6x8x6, legs 3x5x3.
 *   Spider .......... head 8x8x8, middle 6x6x6, abdomen 10x8x12, eight legs of 16x2x2.
 *   Infected dog .... the husky, black, the jaw hanging open on its teeth.
 *   Mutated spider .. the spider, furred and dark, with its young inside it.
 */
public final class Animals {
    public enum Kind {
        //        radius height hp   walk  run   unit     ground
        DOG(0.30f, 0.80f, 60, 1.7f, 6.2f, 0.0625f, true),
        CROW(0.16f, 0.30f, 6, 0.8f, 8.0f, 0.045f, false),
        BAT(0.13f, 0.24f, 4, 0.5f, 5.6f, 0.040f, false),
        CHICKEN(0.20f, 0.46f, 8, 0.9f, 3.8f, 0.055f, true),
        SPIDER(0.62f, 0.78f, 90, 1.6f, 5.0f, 0.0625f, true),
        IDOG(0.30f, 0.80f, 55, 1.9f, 7.5f, 0.0625f, true),
        BROOD(0.66f, 0.82f, 150, 1.7f, 5.6f, 0.068f, true),
        SPIDERLING(0.15f, 0.2f, 5, 1.2f, 4.6f, 0.018f, true);

        public final float radius, height, hp, walk, run, unit;
        public final boolean ground;
        Kind(float radius, float height, float hp, float walk, float run, float unit, boolean ground) { this.radius = radius; this.height = height; this.hp = hp; this.walk = walk; this.run = run; this.unit = unit; this.ground = ground; }
        public boolean infected() { return this == IDOG || this == BROOD || this == SPIDERLING; }
        public static Kind of(String name) {
            if (name == null) return null;
            String n = name.trim().toLowerCase().replace(' ', '_');
            return switch (n) {
                case "dog", "husky" -> DOG; case "crow" -> CROW; case "bat" -> BAT; case "chicken", "hen" -> CHICKEN; case "spider" -> SPIDER;
                case "idog", "infected_dog", "hound" -> IDOG; case "brood", "infected_spider", "mutated_spider" -> BROOD; case "spiderling", "baby_spider" -> SPIDERLING;
                default -> null;
            };
        }
        public String display() { return switch (this) { case DOG -> "Husky"; case CROW -> "Crow"; case BAT -> "Bat"; case CHICKEN -> "Chicken"; case SPIDER -> "Giant Spider"; case IDOG -> "Infected Dog"; case BROOD -> "Mutated Spider"; default -> "Spiderling"; }; }
    }

    public static final class Animal {
        private static int next = 1;
        public final int id = next++;
        public final Kind kind;
        public final AnimalBrain brain;
        public final Intent intent = new Intent();
        /** The body the voxel physics moves (ground animals); what flies keeps its own position. */
        public final Actor body = new Actor();
        public float x, y, z, yaw, hp, homeX, homeY, homeZ;
        /** Whose it is (an actor id; 0 = nobody's), and which pack it runs with (0 = none). */
        public int ownerId, packId;
        public boolean dead, flying, onCeiling, rising, sitting, mouthOpen, alarmed, lootDropped;
        public float diedAt = -99, hurtAt = -99, phase, wing, speed, headYaw, jaw, ceilT, leapUntil = -99, attackAnim, youngAt = -99;
        public long attackerId;
        /** What it will leave when it dies (item ids; decided when it is made, so that what a crow carries is the crow's own). */
        public final List<String> carrying = new ArrayList<>();
        public int young;
        /**
         * 1.2: dead (or thrown), the animal is a body of the Sydney Engine's - built from the pose it died in - and is drawn
         * from that. Alive: how fast it is turning (radians a second), climbing (metres a second, on the wing), how much of a
         * glide and of a landing flare it is in (0..1), how far through the wind-up of a strike or a leap (0..1), and its
         * velocity on the wing.
         */
        public absentialis.sydney.SoftBody rag;
        public float turn, climb, glide, flare, windUp, fvx, fvy, fvz;

        Animal(Kind k, AnimalBrain b) { kind = k; brain = b; hp = k.hp; body.radius = k.radius; body.stand = k.height; body.faction = "animal"; body.archetype = k.name().toLowerCase(); }
        public long ref() { return -id; }
    }

    private final Raid raid;
    public final List<Animal> all = new ArrayList<>();
    private int nextPack = 1;
    /** Counters (gates): what was killed, what was dropped, bites landed on people, limbs taken by infected dogs, crows that flew, young released. */
    public int killed, drops, bites, limbsTaken, flushed, youngReleased, spawned;
    private boolean populated, whistled;
    /** The item that is the survivor's dog: carried into an operation, the husky deploys at their heel. */
    public static final String WHISTLE = "companion.husky";

    Animals(Raid raid) { this.raid = raid; solid = (x, y, z) -> raid.world.solid(x, y, z); }

    // ------------------------------------------------------------------ making them
    private static AnimalBrain brain(Kind k) {
        return switch (k) {
            case DOG -> new AnimalBrain.Dog(); case CROW -> new AnimalBrain.Crow(); case BAT -> new AnimalBrain.Bat(); case CHICKEN -> new AnimalBrain.Chicken();
            case SPIDER -> new AnimalBrain.Spider(); case IDOG -> new AnimalBrain.PackDog(); case BROOD -> new AnimalBrain.Brood(); default -> new AnimalBrain.PackDog();
        };
    }

    public Animal spawn(Kind k, float x, float y, float z) {
        Animal a = new Animal(k, brain(k));
        a.x = a.homeX = x; a.y = a.homeY = y; a.z = a.homeZ = z;
        a.yaw = raid.rng.nextFloat() * 6.28f;
        a.body.x = x; a.body.y = y; a.body.z = z;
        switch (k) {
            case CROW -> {
                // three in four carry something: cash, a grenade of any kind, gunpowder of any grade, or loose rounds
                if (raid.rng.nextFloat() < CROW_LOOT) {
                    String[][] pool = {{"val.ledger_cash"}, {"grenade.frag", "grenade.incendiary", "grenade.flash", "grenade.pipe"}, {"mat.gunpowder_s", "mat.gunpowder_m", "mat.gunpowder_l"}, {"ammo.handgun", "ammo.shells", "ammo.rifle", "ammo.smg", "ammo.assault", "ammo.magnum", "ammo.bolt"}};
                    String[] g = pool[raid.rng.nextInt(pool.length)];
                    a.carrying.add(g[raid.rng.nextInt(g.length)]);
                }
            }
            case BAT -> { if (raid.rng.nextFloat() < BAT_LOOT) { String[] g = raid.rng.nextFloat() < 0.5f ? new String[]{"mat.gunpowder_s", "mat.gunpowder_m", "mat.gunpowder_l"} : new String[]{"ammo.handgun", "ammo.shells", "ammo.rifle", "ammo.smg", "ammo.assault"}; a.carrying.add(g[raid.rng.nextInt(g.length)]); } }
            case CHICKEN -> { int n = 1 + (raid.rng.nextFloat() < 0.35f ? 1 : 0); for (int i = 0; i < n; i++) { float r = raid.rng.nextFloat(); a.carrying.add(r < EGG_GOLDEN ? "food.egg_golden" : r < EGG_GOLDEN + 0.36f ? "food.egg_white" : "food.egg_brown"); } }
            case BROOD -> { a.young = raid.rng.nextFloat() < BROOD_YOUNG ? 3 + raid.rng.nextInt(4) : 0; String j = raid.rollJewellery(); if (j != null) a.carrying.add(j); }   // 1.2: it is one of the special infected, and has a piece of somebody's jewellery in it like the rest
            default -> { }
        }
        all.add(a);
        spawned++;
        return a;
    }
    /** The chance a crow is carrying something (the brief: 75%), a bat likewise (small), a golden egg, and that a mutated spider has young in it. */
    public static float CROW_LOOT = 0.75f, BAT_LOOT = 0.18f, EGG_GOLDEN = 0.05f, BROOD_YOUNG = 0.4f;

    /** A dog for somebody: it is theirs from now on. */
    public Animal dogFor(Actor owner) {
        float ang = raid.rng.nextFloat() * 6.28f;
        int gy = raid.standY((int) Math.floor(owner.x + Math.sin(ang) * 1.6f), (int) Math.floor(owner.z + Math.cos(ang) * 1.6f), Math.round(owner.y));
        Animal d = spawn(Kind.DOG, owner.x + (float) Math.sin(ang) * 1.6f, gy > 0 ? gy : owner.y, owner.z + (float) Math.cos(ang) * 1.6f);
        d.ownerId = owner.id;
        return d;
    }

    /** A pack of infected dogs: never fewer than two, never more than six, loyal to each other and to nothing else. */
    public List<Animal> pack(float x, float y, float z, int n) {
        n = Math.max(2, Math.min(6, n));
        List<Animal> out = new ArrayList<>();
        int pid = nextPack++;
        for (int i = 0; i < n; i++) {
            double ang = i * 2.399963;
            float px = x + (float) Math.cos(ang) * (0.8f + 0.5f * i), pz = z + (float) Math.sin(ang) * (0.8f + 0.5f * i);
            int gy = raid.standY((int) Math.floor(px), (int) Math.floor(pz), Math.round(y));
            Animal d = spawn(Kind.IDOG, px, gy > 0 ? gy : y, pz);
            d.packId = pid;
            out.add(d);
        }
        return out;
    }

    /** By name, where somebody is pointing (the console and Developer Mode). */
    public String debugSpawn(String what, float x, float y, float z, int count) {
        Kind k = Kind.of(what);
        if (k == null) return null;
        if (k == Kind.IDOG) { pack(x, y, z, Math.max(2, count)); return "A pack of " + Math.max(2, Math.min(6, count)) + " infected dogs"; }
        for (int i = 0; i < Math.max(1, count); i++) {
            Animal a = spawn(k, x + (i % 3) * 0.9f, k == Kind.BAT ? roofUnder(x, y, z) : y, z + (i / 3) * 0.9f);
            if (k == Kind.DOG) a.ownerId = raid.player.id;
        }
        return Math.max(1, count) + " x " + k.display() + (k == Kind.DOG ? " (yours)" : "");
    }

    private float roofUnder(float x, float y, float z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        for (int yy = (int) Math.floor(y) + 1; yy < Math.min(World.H - 1, (int) y + 9); yy++) if (raid.world.solid(ix, yy, iz)) return yy - Kind.BAT.height - 0.02f;
        return y + 2.5f;
    }

    /**
     * What an operation starts with: crows on the roofs and the lamp posts, bats under the roofs of dark places,
     * chickens in the yards of the outskirts, a dog at the heel of one armed person in fourteen (and no strays),
     * spiders where nobody goes, and - out where the city ends - packs.
     */
    void populate() {
        if (populated) return;
        populated = true;
        World w = raid.world;
        if (w.dev) return;
        java.util.Random r = new java.util.Random(raid.profile.seed * 31 + raid.profile.zone.day * 131L);
        Actor p = raid.player;
        for (int i = 0; i < 260; i++) {
            float ang = r.nextFloat() * 6.28f, rad = 14 + r.nextFloat() * 150;
            int x = (int) (p.x + Math.cos(ang) * rad), z = (int) (p.z + Math.sin(ang) * rad);
            if (x < World.CITY_MIN + 4 || z < World.CITY_MIN + 4 || x > World.CITY_MAX - 4 || z > World.CITY_MAX - 4) continue;
            District d = w.district(x, z);
            int top = w.heightAt(x, z), gy = raid.groundY(x, z);
            float roll = r.nextFloat();
            if (roll < 0.42f) { if (top >= World.GROUND + 2 && !w.solid(x, top + 1, z)) spawn(Kind.CROW, x + 0.5f, top + 1, z + 0.5f); }
            else if (roll < 0.56f) { if (gy > 0 && top > gy + 3 && w.sky(x, gy + 1, z) < 6) spawn(Kind.BAT, x + 0.5f, roofUnder(x + 0.5f, gy, z + 0.5f), z + 0.5f); }
            else if (roll < 0.70f) { if (gy > 0 && (d == District.WILDERNESS || d == District.MARKET) && top <= gy) spawn(Kind.CHICKEN, x + 0.5f, gy, z + 0.5f); }
            // 1.3: no strays - a dog in the city is somebody's (Raid gives one to one armed person in fourteen)
            else if (roll < 0.79f) { if (gy > 0 && (d == District.WILDERNESS || d == District.INDUSTRIAL) && rad > 50) spawn(r.nextFloat() < 0.45f ? Kind.BROOD : Kind.SPIDER, x + 0.5f, gy, z + 0.5f); }
            else if (roll < 0.83f) { if (gy > 0 && top <= gy && rad > 70 && (d == District.WILDERNESS || raid.night())) pack(x + 0.5f, gy, z + 0.5f, 2 + r.nextInt(5)); }
        }
    }

    /** True when somebody already has a living dog. */
    public boolean owned(int actorId) { for (Animal a : all) if (!a.dead && a.kind == Kind.DOG && a.ownerId == actorId) return true; return false; }
    /** The survivor's own dog, if they brought one and it is alive. */
    public Animal playersDog() { for (Animal a : all) if (!a.dead && a.kind == Kind.DOG && a.ownerId == raid.player.id) return a; return null; }

    public Animal byRef(long ref) { if (ref >= 0) return null; for (Animal a : all) if (a.id == -ref) return a; return null; }
    /** 1.3, studies and gates: when set, every animal's intent comes from this instead of its mind (walk it, run it, sit it). */
    public java.util.function.BiConsumer<Animal, Intent> script;

    public int count(Kind k) { int n = 0; for (Animal a : all) if (a.kind == k && !a.dead) n++; return n; }

    // ------------------------------------------------------------------ the senses
    private final class View implements AnimalBrain.Senses {
        Animal a;
        final Thing t1 = new Thing(), t2 = new Thing(), t3 = new Thing(), t4 = new Thing(), t5 = new Thing(), t6 = new Thing();
        @Override public float time() { return raid.time; }
        @Override public float x() { return a.x; } @Override public float y() { return a.y; } @Override public float z() { return a.z; }
        @Override public float homeX() { return a.homeX; } @Override public float homeY() { return a.homeY; } @Override public float homeZ() { return a.homeZ; }
        @Override public float random() { return raid.rng.nextFloat(); }

        private float d(float x, float y, float z) { float dx = x - a.x, dy = y - a.y, dz = z - a.z; return (float) Math.sqrt(dx * dx + dy * dy + dz * dz); }
        private Thing thing(Thing t, Actor o) { return t.set(o.x, o.y, o.z, Math.max(0, d(o.x, o.y + 0.4f, o.z) - o.radius), (float) Math.sqrt(o.vx * o.vx + o.vz * o.vz), o.id); }

        @Override public Thing person(float within) {
            t1.none();
            for (Actor o : raid.actors) {
                if (o.dead || o.infected() || o.isPlayer && raid.playerIgnored() || o.id == a.ownerId || Math.abs(o.x - a.x) > within + 1 || Math.abs(o.z - a.z) > within + 1) continue;
                float dd = d(o.x, o.y + 0.4f, o.z) - o.radius;
                if (dd < within && dd < t1.dist) thing(t1, o);
            }
            return t1;
        }
        @Override public Thing infected(float within) {
            t2.none();
            for (Actor o : raid.actors) {
                if (o.dead || !o.infected() || Math.abs(o.x - a.x) > within + 1 || Math.abs(o.z - a.z) > within + 1) continue;
                float dd = d(o.x, o.y + 0.4f, o.z) - o.radius;
                if (dd < within && dd < t2.dist) thing(t2, o);
            }
            for (Animal o : all) {
                if (o == a || o.dead || !o.kind.infected()) continue;
                float dd = d(o.x, o.y, o.z) - o.kind.radius;
                if (dd < within && dd < t2.dist) t2.set(o.x, o.y, o.z, Math.max(0, dd), o.speed, o.ref());
            }
            return t2;
        }
        @Override public Thing prey(float within) {
            t3.none();
            boolean hunter = a.kind.infected();
            // people first: anything on two legs that is not one of the dead
            for (Actor o : raid.actors) {
                if (o.dead || o.isPlayer && (raid.playerIgnored() || raid.god && hunter && false) || Math.abs(o.x - a.x) > within + 1 || Math.abs(o.z - a.z) > within + 1) continue;
                if (o.infected() && (hunter || a.kind == Kind.SPIDER)) continue;                                  // the dead are nothing to an infected animal - and (1.2) nothing to a spider either: it wants what is alive
                if (o.seated) continue;
                float dd = d(o.x, o.y + 0.4f, o.z) - o.radius;
                if (dd < within && dd < t3.dist && (dd < 9 || Physics.los(raid.world, a.x, a.y + a.kind.height * 0.7f, a.z, o.x, o.y + o.height() * 0.6f, o.z))) thing(t3, o);
            }
            if (t3.valid && t3.dist < 12) return t3;
            for (Animal o : all) {
                if (o == a || o.dead || o.kind == a.kind && o.packId == a.packId || o.kind == Kind.SPIDERLING && a.kind == Kind.BROOD || a.kind == Kind.SPIDERLING && o.kind == Kind.BROOD) continue;
                if (a.kind == Kind.SPIDER && o.kind.infected() || hunter && (o.kind == Kind.BROOD || o.kind == Kind.SPIDER || o.kind == Kind.SPIDERLING || o.kind == Kind.CROW || o.kind == Kind.BAT)) continue;
                if (a.kind == Kind.SPIDER && (o.kind == Kind.CROW || o.kind == Kind.BAT || o.kind == Kind.SPIDER)) continue;
                float dd = d(o.x, o.y, o.z) - o.kind.radius;
                if (dd < Math.min(within, 16) && dd < t3.dist) t3.set(o.x, o.y, o.z, Math.max(0, dd), o.speed, o.ref());
            }
            return t3;
        }
        @Override public Thing owner() { Actor o = a.ownerId == 0 ? null : raid.actor(a.ownerId); if (o == null || o.dead) { if (o != null && o.dead) a.ownerId = 0; return t4.none(); } return thing(t4, o); }
        @Override public Thing ownerThreat() {
            t5.none();
            Actor own = a.ownerId == 0 ? null : raid.actor(a.ownerId);
            if (own == null) return t5;
            // whoever is fighting its owner: someone engaged on them, whoever last hurt them, whoever they are shooting at
            for (Actor o : raid.actors) {
                if (o == own || o.dead || o.seated) continue;
                boolean onOwner = o.targetId == own.id && "engage".equals(o.state) && (o.infected() || !own.isPlayer || o.hostileToPlayer);
                boolean hurtOwner = o.id == own.lastAttacker && raid.time - own.lastHitTime < 6;
                boolean ownerOn = !own.isPlayer && own.targetId == o.id && "engage".equals(own.state);
                if (!(onOwner || hurtOwner || ownerOn)) continue;
                float dd = d(o.x, o.y + 0.4f, o.z) - o.radius;
                if (dd < t5.dist) thing(t5, o);
            }
            for (Animal o : all) {
                if (o == a || o.dead || !o.kind.infected()) continue;
                float od = (float) Math.sqrt((o.x - own.x) * (o.x - own.x) + (o.z - own.z) * (o.z - own.z));
                if (od > 10) continue;
                float dd = d(o.x, o.y, o.z) - o.kind.radius;
                if (dd < t5.dist) t5.set(o.x, o.y, o.z, Math.max(0, dd), o.speed, o.ref());
            }
            return t5;
        }
        @Override public Thing pack() {
            t6.none();
            if (a.packId == 0) return t6;
            float sx = 0, sy = 0, sz = 0; int n = 0;
            for (Animal o : all) if (o != a && !o.dead && o.packId == a.packId) { sx += o.x; sy += o.y; sz += o.z; n++; }
            if (n == 0) return t6;
            return t6.set(sx / n, sy / n, sz / n, d(sx / n, sy / n, sz / n), 0, 0);
        }
        @Override public int packSize() { int n = 0; for (Animal o : all) if (!o.dead && o.packId == a.packId && a.packId != 0) n++; return Math.max(1, n); }
        @Override public boolean dark() { return raid.night() || raid.world.sky((int) Math.floor(a.x), (int) Math.floor(a.y), (int) Math.floor(a.z)) < 5; }
        @Override public boolean roofed() { int ix = (int) Math.floor(a.x), iz = (int) Math.floor(a.z); for (int yy = (int) Math.floor(a.y + a.kind.height); yy <= (int) a.y + 7 && yy < World.H; yy++) if (raid.world.solid(ix, yy, iz)) return a.onCeiling || a.rising || yy - a.y > 2.4f; return false; }
        @Override public float hurtAt() { return a.hurtAt; }
        @Override public Thing attacker() { Actor o = a.attackerId > 0 ? raid.actor((int) a.attackerId) : null; if (o != null && !o.dead) return thing(t5, o); Animal an = byRef(a.attackerId); return an == null || an.dead ? t5.none() : t5.set(an.x, an.y, an.z, d(an.x, an.y, an.z), an.speed, an.ref()); }
        @Override public boolean perchNear(float x, float z, float within, float[] out) {
            float best = -1;
            for (int k = 0; k < 14; k++) {
                int px = (int) Math.floor(x + (raid.rng.nextFloat() - 0.5f) * 2 * within), pz = (int) Math.floor(z + (raid.rng.nextFloat() - 0.5f) * 2 * within);
                if (px < 2 || pz < 2 || px >= World.W - 2 || pz >= World.D - 2) continue;
                int top = raid.world.heightAt(px, pz);
                if (top + 2 >= World.H || raid.world.solid(px, top + 1, pz)) continue;
                float score = top + raid.rng.nextFloat() * 2;
                if (score > best) { best = score; out[0] = px + 0.5f; out[1] = top + 1; out[2] = pz + 0.5f; }
            }
            return best >= 0;
        }
        @Override public boolean groundNear(float x, float z, float within, float[] out) {
            for (int k = 0; k < 6; k++) {
                int px = (int) Math.floor(x + (raid.rng.nextFloat() - 0.5f) * within), pz = (int) Math.floor(z + (raid.rng.nextFloat() - 0.5f) * within);
                if (px < 2 || pz < 2 || px >= World.W - 2 || pz >= World.D - 2) continue;
                int gy = raid.standY(px, pz, Math.round(a.y));
                if (gy <= 0 || Math.abs(gy - a.y) > 2.5f) continue;
                out[0] = px + 0.5f; out[1] = gy; out[2] = pz + 0.5f;
                return true;
            }
            return false;
        }
        @Override public boolean clearTo(float x, float z) { return raid.world.nav.clear(a.x, a.y, a.z, x, z, false); }
    }
    private final View view = new View();

    // ------------------------------------------------------------------ one frame
    void update(float dt) {
        if (raid.populate && !populated && raid.time > 1f) populate();
        // the survivor's husky comes with them when its whistle is in their kit
        if (!whistled && raid.time > 0.3f) { whistled = true; if (raid.profile != null && raid.profile.loadout.count(WHISTLE) > 0 && !owned(raid.player.id)) dogFor(raid.player); }
        for (int i = 0; i < all.size(); i++) {
            Animal a = all.get(i);
            if (a.dead) {
                // 1.2: the dead ones are bodies of the Sydney Engine's: they fall, tumble and come to rest as the physics has it,
                // lie a while and are gone
                if (a.rag != null) {
                    twitching = a;
                    a.rag.update(solid, dt, twitch);
                    a.x = a.rag.centreX(); a.y = a.rag.lowestY(); a.z = a.rag.centreZ();
                }
                if (raid.time - a.diedAt > 70) { all.remove(i--); }
                continue;
            }
            float far = Math.abs(a.x - raid.player.x) + Math.abs(a.z - raid.player.z);
            if (far > 260 && a.ownerId == 0) continue;                             // too far off to matter to anybody
            view.a = a;
            a.intent.clear();
            if (script != null) script.accept(a, a.intent);
            else if (!raid.freezeAI) a.brain.think(view, a.intent, dt);
            act(a, dt);
        }
    }

    private final absentialis.sydney.SoftBody.Solid solid;
    private Animal twitching;
    private final Runnable twitch = this::twitch;

    /**
     * What is left in a body for the first moments after it has died, as forces on it (run before every step of its physics):
     * a spider's legs draw in under it, a dog's hind legs kick, a hen flaps. None of it is a pose - where anything ends up is
     * the physics'.
     */
    private void twitch() {
        Animal a = twitching;
        absentialis.sydney.SoftBody b = a.rag;
        float age = b.age;
        switch (a.kind) {
            case SPIDER, BROOD, SPIDERLING -> {
                if (age < 0.12f || age > 2.8f) return;
                float cx = (b.x[0] + b.x[1] + b.x[2] + b.x[3]) * 0.25f, cy = (b.y[0] + b.y[1] + b.y[2] + b.y[3]) * 0.25f, cz = (b.z[0] + b.z[1] + b.z[2] + b.z[3]) * 0.25f;
                float pull = Math.min(1, (age - 0.12f) / 0.9f) * 3.4f * (a.kind.unit / 0.0625f);
                for (int i = 15; i < 23; i++) {
                    float dx = cx - b.x[i], dy = cy - b.y[i], dz = cz - b.z[i], d = Math.max(0.05f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
                    float tremor = age < 1.2f ? (float) Math.sin(age * 55 + i * 1.7f) * 1.2f : 0;
                    b.force(i, dx / d * pull, dy / d * pull + tremor, dz / d * pull);
                }
            }
            case DOG, IDOG -> { if (age < 0.8f) { float k = (float) Math.sin(age * 34) * 9f * (1 - age / 0.8f); b.force(8, 0, k, k * 0.6f); b.force(9, 0, -k, -k * 0.6f); } }
            case CHICKEN -> { if (age < 1.5f) { float k = (float) Math.sin(age * 48) * 1.5f * (1 - age / 1.5f); b.force(6, 0, k, 0); b.force(7, 0, -k, 0); b.force(8, 0, 0, k * 0.5f); } }
            default -> { }
        }
    }

    private void act(Animal a, float dt) {
        Intent in = a.intent;
        Kind k = a.kind;
        a.sitting = in.sitting; a.mouthOpen = in.mouthOpen || k == Kind.IDOG; a.alarmed = in.alarmed;
        a.jaw += ((a.mouthOpen ? 1 : 0) - a.jaw) * Math.min(1, dt * 9);
        a.attackAnim = Math.max(0, a.attackAnim - dt * 2.6f);
        a.windUp = in.windUp;
        float yaw0 = a.yaw;
        if (!in.voice.isEmpty()) voice(a, in.voice);
        float px = a.x, pz = a.z;
        boolean leaping = raid.time < a.leapUntil;
        if (in.move != Move.CLING) a.rising = false;
        // on a ceiling with nothing asked of it (a wind-up, a wait), it stays on the ceiling
        if (a.onCeiling && in.move == Move.STAY && a.kind != Kind.BAT) in.go(Move.CLING, a.x, a.y, a.z, 1);
        switch (in.move) {
            case FLY -> { a.flying = true; a.onCeiling = false; flyTo(a, in.x, in.y, in.z, k.run * in.speed, dt); }
            case PERCH -> {
                // 1.2: a bird that has nothing under it is not perched, and does not drop like a stone either: it comes down, on
                // the wing, onto whatever is below it
                int ix = (int) Math.floor(a.x), iz = (int) Math.floor(a.z);
                if (raid.world.solid(ix, (int) Math.floor(a.y - 0.1f), iz)) { a.flying = false; a.fvx = a.fvy = a.fvz = 0; }
                else {
                    int yy = (int) Math.floor(a.y);
                    while (yy > 0 && !raid.world.solid(ix, yy - 1, iz)) yy--;
                    a.flying = true;
                    flyTo(a, ix + 0.5f, yy, iz + 0.5f, k.run * 0.6f, dt);
                    if (Math.abs(a.y - yy) < 0.05f) { a.flying = false; a.fvx = a.fvy = a.fvz = 0; }
                }
            }
            case HANG -> {
                float dx = in.x - a.x, dy = in.y - a.y, dz = in.z - a.z;
                if (dx * dx + dy * dy + dz * dz > 0.2f) { a.flying = true; flyTo(a, in.x, in.y, in.z, k.run, dt); } else { a.flying = false; a.onCeiling = true; }
            }
            case CLING -> cling(a, in.x, in.z, k.run * 0.8f, dt);
            case LEAP -> {
                if (!leaping && a.body.onGround) {
                    float dx = in.x - a.x, dz = in.z - a.z, d = Math.max(0.3f, (float) Math.sqrt(dx * dx + dz * dz)), t = LEAP_TIME - 0.2f;
                    a.body.vx = dx / t; a.body.vz = dz / t; a.body.vy = 4.3f + Math.max(0, in.y - a.y) * 3f; a.body.onGround = false;
                    a.leapUntil = raid.time + LEAP_TIME;
                    a.yaw = (float) Math.atan2(-dx, -dz);
                    leaping = true;
                }
                ground(a, 0, 0, 0, dt, true);
            }
            case WALK, RUN -> {
                a.flying = false;
                if (a.onCeiling) { a.onCeiling = false; a.body.onGround = false; a.body.vy = -0.5f; }
                float sp = (in.move == Move.RUN ? k.run : k.walk) * in.speed;
                ground(a, in.x, in.z, sp, dt, leaping);
            }
            default -> { if (k.ground && !a.onCeiling) ground(a, a.x, a.z, 0, dt, leaping); }
        }
        // two animals are never in the same place: each gives the other room (a pack rings what it has pulled down)
        if (k.ground && !a.onCeiling) for (Animal o : all) {
            if (o == a || o.dead || !o.kind.ground || o.onCeiling || Math.abs(o.x - a.x) > 2 || Math.abs(o.z - a.z) > 2 || Math.abs(o.y - a.y) > 1.5f) continue;
            float sx = a.x - o.x, sz = a.z - o.z, sd = (float) Math.sqrt(sx * sx + sz * sz), need = (k.radius + o.kind.radius) * 1.15f;
            if (sd >= need) continue;
            if (sd < 1e-3f) { sx = (float) Math.cos(a.id * 2.4f); sz = (float) Math.sin(a.id * 2.4f); sd = 1; }
            float push = Math.min(need - sd, dt * 3.5f) * 0.5f, nx = a.x + sx / sd * push, nz = a.z + sz / sd * push;
            if (Physics.boxFree(raid.world, nx, a.y + 0.05f, nz, k.radius * 0.8f, k.height * 0.8f)) { a.x = nx; a.z = nz; a.body.x = nx; a.body.z = nz; }
        }
        a.ceilT += ((a.onCeiling ? 1 : 0) - a.ceilT) * Math.min(1, dt * 5);
        float moved = (float) Math.sqrt((a.x - px) * (a.x - px) + (a.z - pz) * (a.z - pz));
        a.speed += (moved / Math.max(1e-4f, dt) - a.speed) * Math.min(1, dt * 10);
        a.phase += moved / (k == Kind.DOG || k == Kind.IDOG ? 0.9f : k == Kind.CHICKEN ? 0.3f : k == Kind.SPIDERLING ? 0.12f : 0.75f);
        a.wing += dt * (a.flying ? (k == Kind.BAT ? 26f : 15f + a.flare * 7f + Math.max(0, a.climb) * 1.2f) * (1 - a.glide * 0.85f) : in.alarmed && k == Kind.CHICKEN ? 22f : 0);
        // facing: what it was told to look at, else where it is going
        float want = a.yaw;
        if (!Float.isNaN(in.faceX)) { float fx = in.faceX - a.x, fz = in.faceZ - a.z; if (fx * fx + fz * fz > 0.05f) want = (float) Math.atan2(-fx, -fz); }
        else if (a.flying && a.fvx * a.fvx + a.fvz * a.fvz > 0.6f) want = (float) Math.atan2(-a.fvx, -a.fvz);
        else if (moved > 1e-4f && !a.flying) want = (float) Math.atan2(-(a.x - px), -(a.z - pz));
        a.yaw = absentialis.neuralai.integration.Steering.turn(a.yaw, want, dt * (a.flying ? 3.2f : 8));
        // 1.2, for the drawing: how fast it is turning; where its head is turned (to what it is looking at, as far as a neck goes);
        // on the wing, whether it is climbing, gliding, or braking to land
        a.turn += (absentialis.neuralai.integration.Steering.wrapPi(a.yaw - yaw0) / Math.max(1e-4f, dt) - a.turn) * Math.min(1, dt * 6);
        float look = 0;
        if (!Float.isNaN(in.faceX)) look = Math.max(-0.9f, Math.min(0.9f, absentialis.neuralai.integration.Steering.wrapPi((float) Math.atan2(-(in.faceX - a.x), -(in.faceZ - a.z)) - a.yaw)));
        a.headYaw += (look - a.headYaw) * Math.min(1, dt * 7);
        if (a.flying) {
            a.climb += (a.fvy - a.climb) * Math.min(1, dt * 5);
            float hs = (float) Math.sqrt(a.fvx * a.fvx + a.fvz * a.fvz);
            boolean coasting = hs > k.run * 0.5f && (a.fvy < -0.6f || Math.abs(a.fvy) < 0.5f && Math.sin(raid.time * 0.9f + a.id * 1.3f) > 0.35f);
            a.glide += ((coasting && k == Kind.CROW ? 1 : 0) - a.glide) * Math.min(1, dt * 3.5f);
        } else { a.climb = 0; a.glide = 0; a.flare += (0 - a.flare) * Math.min(1, dt * 6); }
        if (in.attack != 0 || "miss".equals(in.attackKind)) attack(a, in);
        // a pounce that arrives on somebody is a bite, whatever the mind has moved on to
        if (leaping && k == Kind.IDOG) for (Actor o : raid.nearby(a.x, a.z, 1.6f)) {
            if (o.dead || o.infected() || o.seated || Math.abs(o.y - a.y) > 1.6f || o.distXZ(a.x, a.z) > k.radius + o.radius + 0.35f) continue;
            a.leapUntil = -99;
            in.attack = o.id; in.attackKind = "maul"; in.damage = AnimalBrain.PackDog.BITE * 1.25f;
            attack(a, in);
            break;
        }
    }

    /** How long a leap is in the air (seconds): what the drawing reads how far through one a dog is from. */
    public static final float LEAP_TIME = 0.62f;

    /**
     * 1.2, flight. In 1.1 a bird went at where it was going in a straight line - down off a roof, along the street at
     * knee height, into the wall of the building it was making for - and then went up the wall a step at a time: it ran
     * at things and climbed them. Now it flies. Out under the sky it takes off steeply, holds a height that clears
     * everything between it and where it is going (with air to spare), and comes down onto the place only at the end of
     * the way, slowing as it does; its velocity is turned and changed at a rate, so it arcs and banks instead of
     * snapping. Under a roof (a bat on its rounds, a crow that has got indoors) there is no "over": it goes at the place
     * and round what is in the way.
     */
    private void flyTo(Animal a, float tx, float ty, float tz, float speed, float dt) {
        World w = raid.world;
        float dx = tx - a.x, dy = ty - a.y, dz = tz - a.z, dh = (float) Math.sqrt(dx * dx + dz * dz), d = (float) Math.sqrt(dh * dh + dy * dy);
        if (d < 0.12f) { a.x = tx; a.y = ty; a.z = tz; a.fvx = a.fvy = a.fvz = 0; a.flare = 0; a.body.x = a.x; a.body.y = a.y; a.body.z = a.z; a.body.vy = 0; return; }
        float hx = dh > 1e-3f ? dx / dh : 0, hz = dh > 1e-3f ? dz / dh : 0;
        int ix = (int) Math.floor(a.x), iz = (int) Math.floor(a.z);
        boolean open = a.kind == Kind.CROW && w.heightAt(ix, iz) < a.y + 0.5f;
        float wantX, wantY, wantZ;
        if (open) {
            // the highest thing on the way (not counting the place it is going to land on), and the height to hold
            float over = -1, next = -1;
            int look = (int) Math.min(9, dh - 1.2f);
            for (int kk = 0; kk <= look; kk++) { float top = w.heightAt((int) Math.floor(a.x + hx * kk), (int) Math.floor(a.z + hz * kk)) + 1; over = Math.max(over, top); if (kk <= 1) next = Math.max(next, top); }
            boolean approach = dh < 2.6f;
            float hold = approach ? ty : Math.max(over + 1.7f, ty + Math.min(5.5f, dh * 0.4f));
            wantY = Math.max(-speed * 0.55f, Math.min(speed * 0.9f, (hold - a.y) * (approach ? 3.2f : 2.4f)));
            // forward: not into something it is not yet over; slower as it comes in
            float hs = a.y < next + 0.25f && !approach ? speed * 0.12f : approach ? Math.min(speed, Math.max(0.9f, dh * 2.4f)) : speed;
            wantX = hx * hs; wantZ = hz * hs;
            a.flare += ((approach || a.y < next + 0.25f ? 1 : 0) - a.flare) * Math.min(1, dt * 5);
        } else {
            float sp = Math.min(speed, Math.max(0.8f, d * 3f));
            wantX = dx / d * sp; wantY = dy / d * sp; wantZ = dz / d * sp;
            a.flare += ((d < 1.5f ? 1 : 0) - a.flare) * Math.min(1, dt * 5);
        }
        float kacc = Math.min(1, dt * (open ? 3.6f : 6f));
        a.fvx += (wantX - a.fvx) * kacc; a.fvy += (wantY - a.fvy) * Math.min(1, dt * 5f); a.fvz += (wantZ - a.fvz) * kacc;
        float nx = a.x + a.fvx * dt, ny = a.y + a.fvy * dt, nz = a.z + a.fvz * dt;
        if (w.solid((int) Math.floor(nx), (int) Math.floor(ny), (int) Math.floor(nz)) || w.solid((int) Math.floor(nx), (int) Math.floor(ny + a.kind.height), (int) Math.floor(nz))) {
            // something in the way after all: it is gone over or round, never through - and never walked up
            if (d < 0.9f) { nx = tx; ny = ty; nz = tz; a.fvx = a.fvy = a.fvz = 0; }
            else if (!w.solid(ix, (int) Math.floor(ny + a.kind.height + 0.3f), iz) && !w.solid(ix, (int) Math.floor(a.y + Math.abs(speed) * dt + a.kind.height + 0.3f), iz)) { nx = a.x; nz = a.z; a.fvx *= 0.3f; a.fvz *= 0.3f; a.fvy = Math.max(a.fvy, speed * 0.7f); ny = a.y + a.fvy * dt; }
            else if (!w.solid((int) Math.floor(nx), (int) Math.floor(a.y), iz) && !w.solid((int) Math.floor(nx), (int) Math.floor(a.y + a.kind.height), iz)) { ny = a.y; nz = a.z; a.fvz = 0; a.fvy = 0; }
            else if (!w.solid(ix, (int) Math.floor(a.y), (int) Math.floor(nz)) && !w.solid(ix, (int) Math.floor(a.y + a.kind.height), (int) Math.floor(nz))) { ny = a.y; nx = a.x; a.fvx = 0; a.fvy = 0; }
            else { nx = a.x; ny = a.y; nz = a.z; a.fvx = a.fvy = a.fvz = 0; }
        }
        a.x = nx; a.y = ny; a.z = nz;
        a.body.x = a.x; a.body.y = a.y; a.body.z = a.z; a.body.vy = 0; a.body.onGround = false;
    }

    /** Along the ground toward a point, at a speed, with the voxel physics every other body uses: one-block ledges are hopped, walls stop it. */
    private void ground(Animal a, float tx, float tz, float speed, float dt, boolean ballistic) {
        Actor b = a.body;
        b.x = a.x; b.y = a.y; b.z = a.z;
        if (!ballistic) {
            float dx = tx - a.x, dz = tz - a.z, d = (float) Math.sqrt(dx * dx + dz * dz);
            float sp = d < 0.25f ? 0 : speed;
            float wx = d < 1e-3f ? 0 : dx / d * sp, wz = d < 1e-3f ? 0 : dz / d * sp, k = Math.min(1, dt * 10);
            b.vx += (wx - b.vx) * k; b.vz += (wz - b.vz) * k;
        }
        boolean blocked = Physics.move(raid.world, b, dt);
        if (blocked && b.onGround && speed > 0.2f && !ballistic) b.vy = Physics.JUMP * (a.kind == Kind.CHICKEN ? 0.9f : 1f);
        b.fallSpeed = 0;
        a.x = b.x; a.y = b.y; a.z = b.z;
        if (a.y < 1) { a.y = raid.groundY((int) a.x, (int) a.z); b.vy = 0; }
    }

    /** Under a ceiling: the body hangs from it and goes along it for as long as there is ceiling to go along; where it ends, it lets go. */
    private void cling(Animal a, float tx, float tz, float speed, float dt) {
        int ix = (int) Math.floor(a.x), iz = (int) Math.floor(a.z), top = -1;
        for (int yy = (int) Math.floor(a.y + a.kind.height); yy <= (int) a.y + 8 && yy < World.H; yy++) if (raid.world.solid(ix, yy, iz)) { top = yy; break; }
        if (top < 0) { a.onCeiling = false; a.rising = false; ground(a, tx, tz, speed, dt, false); return; }
        float want = top - a.kind.height - 0.03f;
        if (!a.onCeiling) { a.rising = true; a.y += Math.min(want - a.y, dt * 6f); if (want - a.y < 0.05f) { a.onCeiling = true; a.rising = false; } a.body.vy = 0; a.body.y = a.y; a.body.onGround = false; return; }
        a.y = want;
        float dx = tx - a.x, dz = tz - a.z, d = (float) Math.sqrt(dx * dx + dz * dz);
        if (d < 1.4f) return;
        float nx = a.x + dx / d * speed * dt, nz = a.z + dz / d * speed * dt;
        boolean roof = raid.world.solid((int) Math.floor(nx), top, (int) Math.floor(nz)), room = Physics.boxFree(raid.world, nx, a.y, nz, a.kind.radius * 0.6f, a.kind.height);
        if (roof && room) { a.x = nx; a.z = nz; a.body.x = nx; a.body.z = nz; a.body.y = a.y; }
        else { a.onCeiling = false; a.body.x = a.x; a.body.y = a.y; a.body.z = a.z; a.body.vy = -0.5f; a.body.onGround = false; }
    }

    // ------------------------------------------------------------------ teeth
    private void attack(Animal a, Intent in) {
        a.attackAnim = 1;
        Actor t = in.attack > 0 ? raid.actor((int) in.attack) : null;
        Animal ta = in.attack < 0 ? byRef(in.attack) : null;
        float fx = (float) -Math.sin(a.yaw), fz = (float) -Math.cos(a.yaw);
        if ("miss".equals(in.attackKind)) return;
        if ("spit".equals(in.attackKind)) { raid.venom(a.x, a.y + a.kind.height * 0.8f, a.z, in.x, in.y, in.z); return; }
        if (ta != null) { hurt(ta, in.damage, a.ref(), fx, fz); return; }
        if (t == null || t.dead) return;
        Actor credit = a.ownerId != 0 ? raid.actor(a.ownerId) : null;
        switch (in.attackKind) {
            case "tackle" -> { raid.tackle(null, t, fx, fz, in.damage); bites++; }
            case "maul" -> {
                // an infected dog goes for an arm - never the head - and what its jaws close on can come away
                boolean left = raid.rng.nextFloat() < 0.5f;
                raid.hitKind = "bite";
                raid.damageActor(t, null, in.damage * (t.isPlayer ? raid.difficultyDamage() : 1), null, "arms", t.x, t.y + t.height() * 0.6f, t.z);
                raid.hitKind = "";
                bites++;
                if (!t.dead && !t.isPlayer && raid.rng.nextFloat() < MAUL_SEVER) { raid.gore.sever(t, left ? absentialis.character.Rig.L_FOREARM : absentialis.character.Rig.R_FOREARM, fx, fz, 30); limbsTaken++; }
                else if (t.isPlayer) { raid.body.armInjury = Math.min(1, raid.body.armInjury + 0.3f); if (raid.rng.nextFloat() < 0.5f) raid.body.bleedLight = true; raid.infection.bitten(t, null); }
            }
            default -> {
                raid.hitKind = "bite";
                raid.damageActor(t, credit, in.damage * (t.isPlayer ? raid.difficultyDamage() : 1), null, "thrust".equals(in.attackKind) ? "torso" : "legs", t.x, t.y + ("thrust".equals(in.attackKind) ? 1.1f : 0.5f), t.z);
                raid.hitKind = "";
                bites++;
            }
        }
        raid.cue("bite", a.x, a.y + 0.4f, a.z, 0.6f, 26);
    }
    /** The chance an infected dog's bite takes the forearm it has hold of (people other than the survivor; theirs is wounded and bitten instead). */
    public static float MAUL_SEVER = 0.2f;

    /** Damage to an animal, from whoever (an actor id, or an animal's negative reference). */
    public void hurt(Animal a, float dmg, long by, float dx, float dz) {
        if (a.dead) return;
        a.hp -= dmg; a.hurtAt = raid.time; a.attackerId = by;
        // 1.2: a hit on an animal is a hit - the marker says so, as it does for anything else that is shot
        if (by > 0 && dmg > 0) raid.bus.emit(new absentialis.engine.events.Events.HitConfirmed((int) by, (int) a.ref(), "animal", dmg, raid.time));
        a.body.vx += dx * 2.2f; a.body.vz += dz * 2.2f;
        raid.fx.spray(a.x, a.y + a.kind.height * 0.6f, a.z, dx, 0.4f, dz, 6, 2.2f, a.kind.infected() ? 0x1E3A12 : 0x7A0C0C, 0.035f);
        if (a.hp > 0) { if (a.kind == Kind.DOG) voice(a, "whine"); else if (a.kind == Kind.CHICKEN) voice(a, "squawk"); return; }
        a.dead = true; a.diedAt = raid.time;
        // 1.2: from here it is the Sydney Engine's - a body built from the pose it died in, moving as it was moving, struck as it was struck
        a.rag = AnimalBodies.rig(a);
        boolean wing = a.flying;
        a.rag.velocity(wing ? a.fvx : a.body.vx, wing ? a.fvy : Math.max(0, a.body.vy), wing ? a.fvz : a.body.vz);
        float heavy = a.kind == Kind.SPIDER || a.kind == Kind.BROOD ? 0.45f : a.kind == Kind.DOG || a.kind == Kind.IDOG ? 0.7f : 1.25f, kick = Math.min(7f, 1.4f + dmg * 0.07f) * heavy;
        a.rag.impulseAt(a.x - dx * 0.3f, a.y + a.kind.height * 0.65f, a.z - dz * 0.3f, dx * kick, kick * 0.3f, dz * kick, Math.max(0.5f, a.kind.radius * 2.4f));
        a.flying = false; a.onCeiling = false; a.rising = false; a.ceilT = 0; a.windUp = 0; a.attackAnim = 0; a.body.onGround = false;
        killed++;
        raid.bus.emit(new absentialis.engine.events.Events.AnimalKilled(by > 0 ? (int) by : 0, a.kind.display(), raid.time));
        voice(a, a.kind == Kind.DOG ? "whine" : a.kind == Kind.CROW ? "caw" : a.kind == Kind.CHICKEN ? "squawk" : a.kind == Kind.BAT ? "squeak" : a.kind == Kind.IDOG ? "snarl" : "hiss");
        if (!a.lootDropped) {
            a.lootDropped = true;
            for (String id : a.carrying) {
                if (Content.def(id) == null) continue;
                ItemInstance it = new ItemInstance(id);
                it.origin = raid.raidId; it.foundInRaid = true;
                if (it.def().maxStack > 1 && id.startsWith("ammo.")) it.qty = Math.max(1, Math.min(it.def().maxStack, 4 + raid.rng.nextInt(9)));
                raid.dropItem(it, a.x + (raid.rng.nextFloat() - 0.5f) * 0.4f, a.y + 0.3f, a.z + (raid.rng.nextFloat() - 0.5f) * 0.4f);
                drops++;
            }
        }
        // what was inside a mutated spider comes out of it
        if (a.kind == Kind.BROOD && a.young > 0) {
            for (int i = 0; i < a.young; i++) { Animal y = spawn(Kind.SPIDERLING, a.x + (raid.rng.nextFloat() - 0.5f) * 1.2f, a.y + 0.2f, a.z + (raid.rng.nextFloat() - 0.5f) * 1.2f); y.packId = -a.id; youngReleased++; }
            a.young = 0;
        }
        if (by > 0 && by == raid.player.id) raid.debrief.kills.merge(a.kind.name().toLowerCase(), 1, Integer::sum);
    }

    private final float[] hitMult = new float[1];
    /** Gates: rounds that struck an animal, and those of them that struck a head. */
    public int roundsLanded, headShots, runDowns;

    /**
     * A round along a line: the first living animal it meets takes it. Returns true when it stopped in one.
     * (1.2: met by the boxes of its body, turned as it is turned - {@link AnimalBodies} - not by a ball about its middle.)
     */
    public boolean shot(Raid.Projectile p, float dx, float dy, float dz, float len) {
        Animal best = null;
        float bt = len, bm = 1;
        for (Animal a : all) {
            if (a.dead) continue;
            float reach = AnimalBodies.reach(a.kind);
            if (Math.abs(a.x - p.x) > len + reach || Math.abs(a.z - p.z) > len + reach || Math.abs(a.y - p.y) > len + reach + 1) continue;
            hitMult[0] = 1;
            float t = AnimalBodies.rayHit(a, p.x, p.y, p.z, dx, dy, dz, bt, hitMult);
            if (t >= 0 && t <= bt) { bt = t; best = a; bm = hitMult[0]; }
        }
        if (best == null) return false;
        roundsLanded++;
        if (bm > 1.2f) headShots++;
        hurt(best, p.damage * bm, p.owner, dx, dz);
        raid.cue("impact_flesh", p.x + dx * bt, p.y + dy * bt, p.z + dz * bt, 0.5f, 30);
        return true;
    }

    /**
     * 1.2: a car against an animal. It is hurt by how fast the bodywork met it; what that kills leaves the bumper as a
     * body with the car's speed, and what it does not kill is knocked aside.
     */
    public void runDown(Animal a, float dmg, int driverId, float nx, float nz, float carVx, float carVz, float closing) {
        if (a.dead) return;
        runDowns++;
        if (a.flying) { a.fvx = carVx; a.fvz = carVz; }
        a.body.vx = carVx + nx * closing * 0.3f; a.body.vz = carVz + nz * closing * 0.3f; a.body.vy = Math.max(a.body.vy, 1.6f + closing * 0.22f); a.body.onGround = false;
        hurt(a, dmg, driverId, nx, nz);
        if (a.dead && a.rag != null) a.rag.velocity(carVx + nx * closing * 0.3f, 1.6f + closing * 0.22f, carVz + nz * closing * 0.3f);
        raid.cue("impact_flesh", a.x, a.y + 0.3f, a.z, 0.7f, 30);
    }

    /** The animal a blow from somebody would land on (a punch, a blade): in front of them and in reach. */
    public Animal inFront(Actor p, float reach) {
        float fx = (float) -Math.sin(p.yaw), fz = (float) -Math.cos(p.yaw);
        Animal best = null; float bd = reach;
        for (Animal a : all) {
            if (a.dead || Math.abs(a.y - p.y) > 1.8f) continue;
            float dx = a.x - p.x, dz = a.z - p.z, d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d - a.kind.radius < bd && (dx * fx + dz * fz) / Math.max(0.01f, d) > 0.5f) { bd = d - a.kind.radius; best = a; }
        }
        return best;
    }

    private void voice(Animal a, String v) {
        String s = switch (v) {
            case "bark" -> "dog_bark"; case "growl" -> a.kind == Kind.IDOG ? "idog_snarl" : "dog_growl"; case "whine" -> "dog_whine"; case "pant" -> "dog_pant"; case "snarl" -> "idog_snarl";
            case "caw" -> "crow_caw"; case "squeak" -> "bat_squeak"; case "cluck" -> "chicken_cluck"; case "squawk" -> "chicken_squawk"; case "hiss" -> "spider_hiss";
            default -> "";
        };
        if (s.isEmpty()) return;
        float range = switch (v) { case "bark", "snarl", "caw" -> 60; case "squeak", "cluck", "pant" -> 18; default -> 34; };
        raid.cue(s, a.x, a.y + a.kind.height, a.z, v.equals("pant") ? 0.3f : 0.7f, range);
        voices.merge(v, 1, Integer::sum);
        if (a.kind == Kind.CROW && v.equals("caw") && a.alarmed) flushed++;
        // a dog's bark and a pack's snarl are heard by everything with ears
        if (v.equals("bark") || v.equals("snarl")) raid.emitSound(a.x, a.y + 0.5f, a.z, v.equals("bark") ? 34 : 26, "shout", 0, false);
    }
    /** Every noise made this operation, by kind (gates). */
    public final java.util.Map<String, Integer> voices = new java.util.HashMap<>();

    // ------------------------------------------------------------------ drawing
    private static final java.util.Map<String, VoxModel> PARTS = new java.util.HashMap<>();
    private final Mat4 base = new Mat4(), m = new Mat4();

    private static VoxModel part(String key, int w, int h, int d, int col) { return PARTS.computeIfAbsent(key, kk -> new VoxModel(w, h, d).box(0, 0, 0, w - 1, h - 1, d - 1, col).jitter(0.05f, kk.hashCode())); }
    private static VoxModel put(String key, VoxModel v) { PARTS.put(key, v); return v; }

    /** The size of each part of each kind, in voxels (width, height, depth): what the brief asks for, and what a gate measures. */
    public static int[] size(Kind k, String part) {
        return switch (k) {
            case DOG, IDOG -> switch (part) { case "head" -> new int[]{6, 6, 4}; case "body" -> new int[]{6, 6, 9}; case "mane" -> new int[]{8, 7, 6}; case "leg" -> new int[]{2, 8, 2}; case "legu" -> new int[]{2, 4, 2}; case "legl" -> new int[]{2, 4, 2}; case "tail" -> new int[]{2, 8, 2}; case "muzzle" -> new int[]{3, 3, 4}; case "ear" -> new int[]{2, 2, 1}; default -> null; };
            case CROW -> switch (part) { case "head" -> new int[]{3, 3, 3}; case "body" -> new int[]{4, 5, 6}; case "wing" -> new int[]{1, 4, 6}; case "leg" -> new int[]{1, 3, 1}; case "tail" -> new int[]{3, 1, 5}; case "beak" -> new int[]{1, 1, 3}; default -> null; };
            case BAT -> switch (part) { case "head" -> new int[]{4, 3, 2}; case "body" -> new int[]{3, 5, 2}; case "wing_in" -> new int[]{2, 7, 1}; case "wing_out" -> new int[]{6, 8, 1}; default -> null; };
            case CHICKEN -> switch (part) { case "head" -> new int[]{4, 6, 3}; case "body" -> new int[]{6, 6, 8}; case "leg" -> new int[]{3, 5, 3}; default -> null; };
            default -> switch (part) { case "head" -> new int[]{8, 8, 8}; case "middle" -> new int[]{6, 6, 6}; case "abdomen" -> new int[]{10, 8, 12}; case "leg" -> new int[]{16, 2, 2}; default -> null; };
        };
    }

    private VoxModel model(Kind k, String part) {
        String key = k.name() + ":" + part;
        VoxModel v = PARTS.get(key);
        if (v != null) return v;
        int[] s = size(k, part.replaceAll("[0-9_lr]+$", "").isEmpty() ? part : part);
        boolean dog = k == Kind.DOG || k == Kind.IDOG, bad = k == Kind.IDOG;
        int coat = bad ? Tex.rgb(26, 26, 30) : Tex.rgb(236, 236, 240), shade = bad ? Tex.rgb(16, 16, 19) : Tex.rgb(196, 200, 208), nose = Tex.rgb(18, 18, 20), eye = bad ? Tex.rgb(214, 30, 24) : Tex.rgb(90, 160, 220);
        int black = Tex.rgb(22, 22, 26), blackHi = Tex.rgb(40, 40, 48);
        switch (k) {
            case DOG, IDOG -> {
                switch (part) {
                    case "body" -> { v = new VoxModel(6, 6, 9).box(0, 0, 0, 5, 5, 8, coat); v.box(1, 5, 0, 4, 5, 8, shade); if (bad) { v.set(0, 2, 3, Tex.rgb(120, 20, 20)); v.set(5, 3, 6, Tex.rgb(120, 20, 20)); v.box(2, 5, 4, 3, 5, 5, Tex.rgb(96, 14, 14)); } }
                    case "mane" -> { v = new VoxModel(8, 7, 6).box(0, 0, 0, 7, 6, 5, coat); v.box(1, 6, 0, 6, 6, 5, shade); v.box(0, 0, 0, 7, 0, 5, bad ? shade : Tex.rgb(250, 250, 252)); }
                    case "head" -> { v = new VoxModel(6, 6, 4).box(0, 0, 0, 5, 5, 3, coat); v.box(1, 4, 0, 4, 5, 0, shade); v.set(1, 3, 0, eye); v.set(4, 3, 0, eye); if (!bad) { v.set(1, 4, 0, Tex.rgb(150, 156, 166)); v.set(4, 4, 0, Tex.rgb(150, 156, 166)); } }
                    case "muzzle" -> { v = new VoxModel(3, 2, 4).box(0, 0, 0, 2, 1, 3, bad ? shade : Tex.rgb(250, 250, 252)); v.set(1, 1, 0, nose); if (bad) { v.set(0, 0, 0, Tex.rgb(150, 20, 20)); v.set(2, 0, 1, Tex.rgb(150, 20, 20)); } }
                    case "jaw" -> { v = new VoxModel(3, 2, 4); v.box(0, 0, 0, 2, 0, 3, bad ? Tex.rgb(90, 14, 16) : Tex.rgb(214, 214, 220)); int tooth = Tex.rgb(240, 236, 220); v.set(0, 1, 0, tooth); v.set(2, 1, 0, tooth); if (bad) { v.set(0, 1, 2, tooth); v.set(2, 1, 2, tooth); v.set(1, 1, 0, tooth); } }
                    case "ear" -> v = new VoxModel(2, 2, 1).box(0, 0, 0, 1, 1, 0, bad ? black : shade);
                    case "leg" -> { v = new VoxModel(2, 8, 2).box(0, 0, 0, 1, 7, 1, coat); v.box(0, 0, 0, 1, 0, 1, bad ? shade : Tex.rgb(214, 216, 222)); }
                    // 1.3: the leg in two, jointed at the knee (the elbow in front, the hock behind): the upper part, and the lower with the paw
                    case "legu" -> v = new VoxModel(2, 4, 2).box(0, 0, 0, 1, 3, 1, coat);
                    case "legl" -> { v = new VoxModel(2, 4, 2).box(0, 0, 0, 1, 3, 1, coat); v.box(0, 0, 0, 1, 0, 1, bad ? shade : Tex.rgb(214, 216, 222)); v.set(0, 0, 0, bad ? Tex.rgb(10, 10, 12) : Tex.rgb(70, 66, 66)); }
                    default -> { v = new VoxModel(2, 8, 2).box(0, 0, 0, 1, 7, 1, coat); v.box(0, 0, 0, 1, 1, 1, bad ? shade : Tex.rgb(250, 250, 252)); }
                }
            }
            case CROW -> {
                switch (part) {
                    case "body" -> { v = new VoxModel(4, 5, 6).box(0, 0, 0, 3, 4, 5, black); v.box(1, 4, 1, 2, 4, 4, blackHi); }
                    case "head" -> { v = new VoxModel(3, 3, 3).box(0, 0, 0, 2, 2, 2, black); v.set(0, 1, 0, Tex.rgb(200, 200, 190)); v.set(2, 1, 0, Tex.rgb(200, 200, 190)); }
                    case "beak" -> v = new VoxModel(1, 1, 3).box(0, 0, 0, 0, 0, 2, Tex.rgb(46, 44, 40));
                    case "wing" -> { v = new VoxModel(1, 4, 6).box(0, 0, 0, 0, 3, 5, black); v.box(0, 0, 3, 0, 1, 5, blackHi); }
                    case "leg" -> v = new VoxModel(1, 3, 1).box(0, 0, 0, 0, 2, 0, Tex.rgb(60, 54, 48));
                    default -> v = new VoxModel(3, 1, 5).box(0, 0, 0, 2, 0, 4, black);
                }
            }
            case BAT -> {
                int skin = Tex.rgb(30, 26, 30), membrane = Tex.rgb(46, 36, 40);
                switch (part) {
                    case "body" -> v = new VoxModel(3, 5, 2).box(0, 0, 0, 2, 4, 1, skin);
                    case "head" -> { v = new VoxModel(4, 3, 2).box(0, 0, 0, 3, 2, 1, skin); v.set(0, 2, 0, membrane); v.set(3, 2, 0, membrane); v.set(1, 1, 0, Tex.rgb(180, 40, 30)); v.set(2, 1, 0, Tex.rgb(180, 40, 30)); }
                    case "wing_in" -> v = new VoxModel(2, 7, 1).box(0, 0, 0, 1, 6, 0, membrane);
                    default -> { v = new VoxModel(6, 8, 1).box(0, 0, 0, 5, 7, 0, membrane); for (int x = 0; x < 6; x++) v.set(x, 7, 0, skin); for (int x = 1; x < 6; x += 2) v.set(x, 0, 0, 0); }
                }
            }
            case CHICKEN -> {
                int white = Tex.rgb(240, 238, 230), cream = Tex.rgb(214, 210, 196), red = Tex.rgb(200, 36, 30), yellow = Tex.rgb(226, 170, 40);
                switch (part) {
                    case "body" -> { v = new VoxModel(6, 6, 8).box(0, 0, 0, 5, 5, 7, white); v.box(1, 5, 5, 4, 5, 7, cream); v.box(0, 1, 2, 0, 3, 6, cream); v.box(5, 1, 2, 5, 3, 6, cream); }
                    case "head" -> { v = new VoxModel(4, 6, 3).box(0, 0, 0, 3, 5, 2, white); v.set(0, 4, 0, Tex.rgb(20, 20, 20)); v.set(3, 4, 0, Tex.rgb(20, 20, 20)); v.box(1, 5, 0, 2, 5, 1, red); }
                    case "beak" -> { v = new VoxModel(2, 2, 2).box(0, 1, 0, 1, 1, 1, yellow); v.box(0, 0, 0, 1, 0, 0, red); }
                    case "wing" -> v = new VoxModel(1, 4, 6).box(0, 0, 0, 0, 3, 5, cream);
                    default -> { v = new VoxModel(3, 5, 3); v.box(1, 1, 1, 1, 4, 1, yellow); v.box(0, 0, 0, 2, 0, 2, yellow); v.set(0, 0, 2, 0); v.set(2, 0, 2, 0); }
                }
            }
            default -> {
                boolean mutated = k != Kind.SPIDER;
                int hide = mutated ? Tex.rgb(34, 26, 22) : Tex.rgb(58, 46, 34), fur = mutated ? Tex.rgb(64, 48, 36) : Tex.rgb(84, 68, 50), eyeS = mutated ? Tex.rgb(190, 220, 60) : Tex.rgb(150, 20, 20);
                switch (part) {
                    case "head" -> { v = new VoxModel(8, 8, 8).box(0, 0, 0, 7, 7, 7, hide); for (int x = 1; x < 7; x += 2) { v.set(x, 5, 0, eyeS); v.set(x, 3, 0, eyeS); } v.box(1, 0, 0, 2, 1, 0, Tex.rgb(214, 204, 180)); v.box(5, 0, 0, 6, 1, 0, Tex.rgb(214, 204, 180)); if (mutated) for (int i = 0; i < 26; i++) v.set((i * 7) % 8, 7, (i * 5) % 8, fur); }
                    case "middle" -> { v = new VoxModel(6, 6, 6).box(0, 0, 0, 5, 5, 5, hide); if (mutated) v.box(1, 5, 1, 4, 5, 4, fur); }
                    case "abdomen" -> { v = new VoxModel(10, 8, 12).box(0, 1, 0, 9, 6, 11, hide); v.box(1, 0, 1, 8, 7, 10, hide); v.box(3, 7, 2, 6, 7, 9, mutated ? Tex.rgb(120, 150, 40) : Tex.rgb(140, 30, 24)); if (mutated) for (int i = 0; i < 60; i++) v.set((i * 3) % 10, 7, (i * 7) % 12, fur); }
                    default -> { v = new VoxModel(16, 2, 2).box(0, 0, 0, 15, 1, 1, hide); v.box(7, 0, 0, 8, 1, 1, fur); v.box(15, 0, 0, 15, 0, 1, Tex.rgb(20, 16, 14)); }
                }
            }
        }
        return put(key, v.jitter(0.05f, key.hashCode()));
    }

    private void drawPart(ModelRenderer mr, Kind k, String part, Mat4 at, float px, float py, float pz, float light, int tint) {
        VoxModel v = model(k, part);
        mr.draw(mr.meshAt("an:" + k.name() + ":" + part, v, px, py, pz), at, light, tint, 0);
    }

    /** Everything alive (and what has not yet gone of the dead), within sight. */
    void draw(ModelRenderer mr, float camX, float camZ, java.util.function.Function<float[], Float> lightAt) {
        float[] at = new float[3];
        for (Animal a : all) {
            if (Math.abs(a.x - camX) > 130 || Math.abs(a.z - camZ) > 130) continue;
            at[0] = a.x; at[1] = a.y + 0.3f; at[2] = a.z;
            float light = lightAt.apply(at);
            draw(mr, a, light);
        }
    }

    private final AnimalBodies.Pose rp = new AnimalBodies.Pose();
    private final Mat4 trunk = new Mat4(), head = new Mat4(), hip = new Mat4();

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : v > hi ? hi : v; }
    /** A number in 0..1 that is the same for the same whole number and looks like chance from one to the next. */
    private static float hash(int n) { n = (n ^ 61) ^ (n >>> 16); n *= 9; n ^= n >>> 4; n *= 0x27d4eb2d; n ^= n >>> 15; return (n & 0xFFFF) / 65535f; }

    /**
     * One animal. Alive, every part is placed from what the body is doing - the ground it has covered, how fast, which
     * way it is turning, whether it is winding up to strike. Dead (or thrown), it is a body of the Sydney Engine's, and
     * every part is placed from where the physics has put it: nothing here decides how an animal lies.
     */
    private void draw(ModelRenderer mr, Animal a, float light) {
        Kind k = a.kind;
        float u = k.unit, t = raid.time, cyc = a.phase * 6.2832f, move = Math.min(1, a.speed / Math.max(0.3f, k.walk));
        int tint = 0xFFFFFFFF;
        boolean rag = a.rag != null;
        if (rag) { AnimalBodies.pose(a, a.rag, rp); base.set(rp.base); }
        else {
            base.identity().translate(a.x, a.y, a.z).rotateY(a.yaw);
            // on a ceiling the whole animal is turned over about its own middle
            if (a.ceilT > 0.01f) base.translate(0, k.height * 0.5f, 0).rotateZ((float) Math.PI * a.ceilT).translate(0, -k.height * 0.5f, 0);
            base.scale(u, u, u);
        }
        boolean leaping = !rag && t < a.leapUntil;
        switch (k) {
            case DOG, IDOG -> {
                boolean bad = k == Kind.IDOG;
                float legH = 8;
                if (rag) trunk.set(base).translate(0, legH, 0);
                else {
                    float sit = a.sitting ? 1 : 0, run = Math.min(1, a.speed / 4f), gal = clamp((a.speed - 3.3f) / 1.5f, 0, 1);
                    // how far through a leap (0 the moment it leaves the ground, 1 as it lands), and through a bite (a lunge out and back)
                    float lt = leaping ? clamp(1 - (a.leapUntil - t) / LEAP_TIME, 0, 1) : 0, bite = leaping ? 0 : (float) Math.sin(Math.PI * clamp((1 - a.attackAnim) * 1.2f, 0, 1)) * (a.attackAnim > 0 ? 1 : 0);
                    float coil = a.windUp;
                    float bob = gal * (float) Math.sin(cyc) * 1.0f + (1 - gal) * (float) Math.abs(Math.sin(cyc)) * 0.4f * move, breathe = (1 - move) * (float) Math.sin(t * (a.mouthOpen ? 5.5f : 2.1f) + a.id) * 0.12f;
                    float pitch = sit * -0.42f + gal * (float) Math.sin(cyc + 0.9f) * 0.15f + (leaping ? 0.5f - lt * 0.85f : 0) - bite * 0.1f + coil * 0.22f;
                    float roll = clamp(a.turn * 0.045f * run, -0.24f, 0.24f);
                    trunk.set(base).translate(0, legH + bob + breathe - sit * 2.2f - bite * 0.7f - coil * 1.8f + (leaping ? (float) Math.sin(Math.PI * lt) * 1.2f : 0), -bite * 3.2f + coil * 1.4f).rotateZ(roll).rotateX(pitch);
                }
                drawPart(mr, k, "body", trunk, 3, 0, 4.5f, light, tint);
                // the hackles of an infected dog stand when it is about to come
                m.set(trunk).translate(0, -0.5f + (bad ? (a.windUp + (leaping ? 1 : 0)) * 0.7f : 0), -5.5f);
                drawPart(mr, k, "mane", m, 4, 0, 3, light, tint);
                // ---- head, jaw, ears
                float hp, hy, open, earBack;
                if (rag) {
                    float vx = rp.lx(AnimalBodies.HEAD), vy = rp.ly(AnimalBodies.HEAD) - (legH + 5.5f), vz = rp.lz(AnimalBodies.HEAD) + 5.5f;
                    hp = clamp((float) Math.atan2(vy, -vz), -1.2f, 1.0f); hy = clamp((float) Math.atan2(-vx, -vz), -1.1f, 1.1f);
                    open = (bad ? 0.5f : 0.25f) * Math.min(1, a.rag.age * 2); earBack = 0.2f;
                } else {
                    float run = Math.min(1, a.speed / 4f), t01 = 1 - a.attackAnim;
                    // a bite: the jaw opens through the reach and shuts hard at the end of it
                    float snap = a.attackAnim > 0 && !leaping ? (t01 < 0.5f ? t01 / 0.5f : Math.max(0, 1 - (t01 - 0.5f) / 0.12f)) : 0;
                    float pant = a.mouthOpen && !a.alarmed && !bad ? 0.07f + 0.06f * (float) Math.sin(t * 13 + a.id) : 0;
                    hp = (a.alarmed ? 0.18f : -0.05f) - (a.sitting ? -0.42f : 0) + 0.08f * (float) Math.sin(cyc * 2) * run + (leaping ? 0.35f : 0) - snap * 0.25f + a.windUp * 0.3f;
                    // what it has hold of is shaken
                    hy = a.headYaw + (bad && a.attackAnim > 0.05f && !leaping ? (float) Math.sin(t * 36) * 0.5f * a.attackAnim : 0);
                    open = a.jaw * (bad ? 0.5f : 0.3f) + snap * 0.75f + pant + (leaping && bad ? 0.55f : 0) + (bad ? 0.1f * (float) Math.sin(t * 9 + a.id) * run : 0);
                    earBack = leaping || a.attackAnim > 0 || a.windUp > 0 ? 0.7f : t - a.hurtAt < 2.5f ? 0.5f : 0;
                }
                head.set(trunk).translate(0, 2.5f, -8.2f).rotateX(hp).rotateY(hy);
                drawPart(mr, k, "head", head, 3, 0, 2, light, tint);
                m.set(head).translate(0, 1f, -2f);
                drawPart(mr, k, "muzzle", m, 1.5f, 0, 4, light, tint);
                m.set(head).translate(0, 1f, -2f).rotateX(-open);
                drawPart(mr, k, "jaw", m, 1.5f, 1, 4, light, tint);
                for (int s = -1; s <= 1; s += 2) { m.set(head).translate(s * 2f, 6f, 2.5f).rotateX(-earBack).rotateZ(s * (a.alarmed && !rag ? 0.05f : -0.25f) + (rag ? 0 : 0.05f * (float) Math.sin(t * 0.7f + a.id + s))); drawPart(mr, k, "ear", m, 1, 0, 0.5f, light, tint); }
                // ---- legs. A walk puts down one foot at a time; a trot, the diagonals together; a gallop, the back pair then the front
                for (int i = 0; i < 4; i++) {
                    boolean front = i < 2, left = (i & 1) == 0;
                    float lx = left ? -2f : 2f, lz = front ? -6.2f : 3.2f;
                    m.set(trunk).translate(lx, 0.6f, lz);
                    // 1.3: two segments, jointed. 'knee' bends the lower one: back for a foreleg (the wrist folds as the paw lifts),
                    // forward-and-under for a hind leg, which stands with its hock angled back off a thigh angled forward
                    float knee;
                    if (rag) { int pi = 6 + i; AnimalBodies.hang(m, rp.lx(pi) - lx, rp.ly(pi) - (legH + 0.6f), rp.lz(pi) - lz); knee = 0; }
                    else if (a.sitting && !front) { m.translate(0, 0.9f, 0).rotateX(1.35f); knee = -2.1f; }
                    else if (leaping) { float lt = clamp(1 - (a.leapUntil - t) / LEAP_TIME, 0, 1); m.rotateX(lt < 0.55f ? (front ? 1.05f : -1.0f) : (front ? 1.05f - (lt - 0.55f) * 1.6f : -1.0f + (lt - 0.55f) * 3.4f)); knee = lt < 0.55f ? (front ? -0.9f : 0.7f) : 0; }
                    else {
                        float gal = clamp((a.speed - 3.3f) / 1.5f, 0, 1), trot = clamp((a.speed - 1.9f) / 1.0f, 0, 1) * (1 - gal), walk = 1 - gal - trot;
                        float pw = cyc + (front ? (left ? 1.5708f : 4.7124f) : (left ? 0 : 3.1416f)), pt = cyc + (front == left ? 0 : 3.1416f), pg = cyc + (front ? 3.1416f : 0) + (left ? 0 : 0.6f);
                        // (the gallop's reach was a straight stick thrown out at almost a right angle: a jointed leg reaches less and folds more)
                        float swing = (walk * (float) Math.sin(pw) * 0.42f + trot * (float) Math.sin(pt) * 0.55f + gal * (float) Math.sin(pg) * 0.72f) * move;
                        float lift01 = Math.max(0, walk * (float) Math.cos(pw) + trot * (float) Math.cos(pt) + gal * (float) Math.cos(pg)) * move;
                        float lift = lift01 * (0.25f + gal * 0.45f);
                        // a bite is made off braced forelegs; a coil sits back on the hind ones
                        float brace = (a.attackAnim > 0 ? (front ? -0.3f : 0.2f) * (float) Math.sin(Math.PI * clamp((1 - a.attackAnim) * 1.2f, 0, 1)) : 0) + a.windUp * (front ? 0.3f : -0.45f);
                        float stance = front ? 0 : 0.28f * (1 - a.windUp);
                        m.translate(0, lift, 0).rotateX(swing + brace + stance);
                        knee = front ? -lift01 * (0.75f + gal * 0.6f) : -0.5f * (1 - a.windUp) + lift01 * (0.6f + gal * 0.5f) + a.windUp * -0.6f;
                    }
                    drawPart(mr, k, "legu", m, 1, 4, 1, light, tint);
                    m.translate(0, -4, 0).rotateX(knee);
                    drawPart(mr, k, "legl", m, 1, 4, 1, light, tint);
                }
                // ---- tail: carried over the back, wagged for an owner, streamed out behind at a run, between the legs when it has been hurt
                m.set(trunk).translate(0, 5.5f, 4.5f);
                if (rag) AnimalBodies.hang(m, rp.lx(10), rp.ly(10) - (legH + 5.5f), rp.lz(10) - 4.5f);
                else {
                    float run = Math.min(1, a.speed / 4f), hurt = t - a.hurtAt < 3 ? 1 : 0;
                    float wag = a.alarmed || bad || hurt > 0 ? 0 : (float) Math.sin(t * 11 + a.id) * 0.5f * (a.ownerId != 0 ? 1 : 0.3f) * (1 - run * 0.6f);
                    float carry = bad ? 1.15f + 0.05f * (float) Math.sin(t * 23 + a.id) : hurt > 0 ? 0.35f : a.alarmed ? 2.6f : 2.0f - 0.75f * run;
                    // the tail hangs from its root; it is swung back (+z, away from the head) and up by the carry - a positive turn
                    // about x would swing it forward, over the back towards the head (1.2's tails were so)
                    m.rotateX(-carry).rotateZ(wag + clamp(-a.turn * 0.05f, -0.4f, 0.4f));
                }
                drawPart(mr, k, "tail", m, 1, 8, 1, light, tint);
            }
            case CROW -> {
                float fly = !rag && a.flying ? 1 : 0;
                if (rag) trunk.set(base).translate(0, 3, 0);
                else {
                    float hop = a.flying ? 0 : (float) Math.abs(Math.sin(cyc)) * 0.9f * move;
                    // on the wing the body lies along the way it is going - nose up in a climb and to brake, level in a glide - and banks into a turn
                    float pitch = fly * (0.38f + clamp(a.climb * 0.05f, -0.22f, 0.22f) + a.flare * 0.2f - a.glide * 0.18f) - 0.25f;
                    trunk.set(base).translate(0, 3 + hop, 0).rotateZ(fly * clamp(-a.turn * 0.16f, -0.7f, 0.7f)).rotateX(pitch);
                }
                drawPart(mr, k, "body", trunk, 2, 0, 3, light, tint);
                float hp, hy;
                if (rag) { float vx = rp.lx(AnimalBodies.HEAD), vy = rp.ly(AnimalBodies.HEAD) - 7.5f, vz = rp.lz(AnimalBodies.HEAD) + 2.5f; hp = clamp((float) Math.atan2(vy, -vz) - 0.3f, -1.2f, 1.0f); hy = clamp((float) Math.atan2(-vx, -vz), -1.2f, 1.2f); }
                else {
                    // a perched bird looks about in jerks, not sweeps, and dips its head now and then
                    int beat = (int) Math.floor(t * 1.4f + a.id * 0.37f);
                    float f = clamp((t * 1.4f + a.id * 0.37f - beat) * 6, 0, 1), look = (hash(beat * 31 + a.id) - 0.5f) * 1.5f, was = (hash((beat - 1) * 31 + a.id) - 0.5f) * 1.5f;
                    float dip = Math.max(0, (float) Math.sin(t * 0.83f + a.id * 1.7f) - 0.94f) * 16;
                    hy = fly > 0 ? 0 : was + (look - was) * f;
                    hp = 0.25f - fly * 0.55f - dip * 0.9f + (a.alarmed ? 0.2f : 0);
                }
                head.set(trunk).translate(0, 4f, -3f).rotateX(hp).rotateY(hy);
                drawPart(mr, k, "head", head, 1.5f, 0, 1.5f, light, tint);
                m.set(head).translate(0, 1f, -1.5f);
                drawPart(mr, k, "beak", m, 0.5f, 0, 3, light, tint);
                for (int s = -1; s <= 1; s += 2) {
                    m.set(trunk).translate(s * 2f, 3.5f, 0);
                    if (rag) { int pi = s < 0 ? 6 : 7; AnimalBodies.hang(m, rp.lx(pi) - s * 2f, rp.ly(pi) - 6.5f, rp.lz(pi)); }
                    else {
                        // folded along the body when it sits; out and beating when it flies - deep and fast to get up and to brake, held out flat to glide
                        float amp = (1 - a.glide) * (0.75f + a.flare * 0.35f + clamp(a.climb * 0.08f, 0, 0.3f)), beat = (float) Math.sin(a.wing);
                        m.rotateZ(s * fly * (1.5f + beat * amp + a.glide * 0.12f)).rotateX(fly * beat * amp * -0.18f);
                    }
                    drawPart(mr, k, "wing", m, 0.5f, 4, 3, light, tint);
                    m.set(trunk).translate(s * 1f, 0, 0.5f).rotateX(rag ? 0.4f : 0.25f - fly * (1.2f - a.flare * 1.5f));
                    drawPart(mr, k, "leg", m, 0.5f, 3, 0.5f, light, tint);
                }
                m.set(trunk).translate(0, 2.5f, 3f);
                if (rag) m.rotateX(clamp((rp.ly(8) - 5.5f) * 0.25f, -0.9f, 0.9f)); else m.rotateX(-0.2f - a.flare * 0.5f + (fly > 0 ? 0 : 0.12f * Math.max(0, (float) Math.sin(t * 1.9f + a.id))));
                drawPart(mr, k, "tail", m, 1.5f, 0, 0, light, tint);
            }
            case BAT -> {
                float fly = !rag && a.flying ? 1 : 0, flap = (float) Math.sin(a.wing), hang = rag || a.flying ? 0 : 1;
                // roosting, it hangs head down with its wings wrapped round it
                if (rag) trunk.set(base).translate(0, 3, 0);
                else trunk.set(base).translate(0, 3 + hang * 2, 0).rotateZ(hang * (float) Math.PI + fly * clamp(-a.turn * 0.12f, -0.6f, 0.6f)).rotateX(fly * 1.1f);
                drawPart(mr, k, "body", trunk, 1.5f, 2.5f, 1, light, tint);
                m.set(trunk).translate(0, 2.5f, 0);
                drawPart(mr, k, "head", m, 2, 0, 1, light, tint);
                for (int s = -1; s <= 1; s += 2) {
                    float beat = fly * flap * 0.9f, fold = hang * 1.25f;
                    hip.set(trunk).translate(s * 1.5f, 0, 0);
                    if (rag) { int pi = s < 0 ? 6 : 7; float vx = Math.abs(rp.lx(pi)) - 1.5f, vy = rp.ly(pi) - 3; hip.rotateZ(s * clamp((float) Math.atan2(vy, Math.max(0.2f, vx)), -1.4f, 1.4f)); }
                    else hip.rotateY(s * (fold + beat * 0.2f)).rotateZ(s * beat * 0.4f);
                    m.set(hip);
                    drawPart(mr, k, "wing_in", m, s < 0 ? 2 : 0, 3.5f, 0.5f, light, tint);
                    m.set(hip).translate(s * 2f, 0, 0).rotateY(rag ? 0 : s * (fold * 0.9f + beat * 0.7f));
                    drawPart(mr, k, "wing_out", m, s < 0 ? 6 : 0, 4f, 0.5f, light, tint);
                }
            }
            case CHICKEN -> {
                boolean pecking = !rag && "peck".equals(a.brain.state);
                // a peck is a quick dip and a slower rise, and now and then a scrape of one foot at the ground first
                float pw = t * 2.6f + a.id, pk = pecking ? Math.max(0, (float) Math.sin(pw)) : 0, peck = pk * pk * (3 - 2 * pk);
                float scratch = pecking && Math.sin(t * 0.61f + a.id * 2.3f) > 0.55 ? (float) Math.sin(t * 12) : 0;
                float flap = !rag && a.alarmed ? (float) Math.sin(a.wing) : 0, ruffle = !rag && !a.alarmed ? Math.max(0, (float) Math.sin(t * 0.47f + a.id * 3.1f) - 0.96f) * 25 * (float) Math.sin(t * 30) : 0;
                if (rag) trunk.set(base).translate(0, 5, 0);
                else trunk.set(base).translate(0, 5 + (a.alarmed ? (float) Math.abs(Math.sin(cyc)) * 0.8f * move : (float) Math.abs(Math.sin(cyc)) * 0.25f * move), 0).rotateZ((float) Math.sin(cyc) * 0.07f * move).rotateX(-peck * 0.4f + (a.alarmed ? 0.18f : 0));
                drawPart(mr, k, "body", trunk, 3, 0, 4, light, tint);
                if (rag) { float vx = rp.lx(AnimalBodies.HEAD), vy = rp.ly(AnimalBodies.HEAD) - 9.5f, vz = rp.lz(AnimalBodies.HEAD) + 3f; head.set(trunk).translate(0, 3.5f, -4f).rotateX(clamp((float) Math.atan2(vy, -vz) - 0.6f, -1.3f, 0.9f)).rotateY(clamp((float) Math.atan2(-vx, -vz), -1.2f, 1.2f)); }
                else {
                    // a walking hen's head stays where it is while the body catches it up, then goes forward all at once
                    float step = a.phase * 2 - (float) Math.floor(a.phase * 2), hold = (0.5f - step) * 1.7f * move * (a.alarmed ? 0.3f : 1);
                    head.set(trunk).translate(0, 3.5f - (a.alarmed ? 0.9f : 0), -4f + hold - (a.alarmed ? 1.1f : 0)).rotateX(-peck * 1.15f + (a.alarmed ? -0.35f : 0)).rotateY(pecking || a.alarmed ? 0 : (hash((int) Math.floor(t * 0.9f + a.id)) - 0.5f) * 0.9f);
                }
                drawPart(mr, k, "head", head, 2, 0, 1.5f, light, tint);
                m.set(head).translate(0, 2.5f, -1.5f);
                drawPart(mr, k, "beak", m, 1, 0, 2, light, tint);
                for (int s = -1; s <= 1; s += 2) {
                    m.set(trunk).translate(s * 3f, 4.5f, 0.5f);
                    if (rag) { int pi = s < 0 ? 6 : 7; AnimalBodies.hang(m, rp.lx(pi) - s * 3f, rp.ly(pi) - 9.5f, rp.lz(pi) - 0.5f); }
                    else m.rotateZ(s * (0.1f + Math.abs(flap) * 1.3f + Math.abs(ruffle) * 0.25f));
                    drawPart(mr, k, "wing", m, 0.5f, 4, 3, light, tint);
                    m.set(base).translate(s * 1.5f, 5, 0.5f);
                    if (rag) { int pi = s < 0 ? 8 : 9; AnimalBodies.hang(m, rp.lx(pi) - s * 1.5f, rp.ly(pi) - 5, rp.lz(pi) - 0.5f); }
                    else m.rotateX((float) Math.sin(cyc + (s < 0 ? 0 : Math.PI)) * 0.75f * move + (s < 0 ? scratch * 0.6f : 0));
                    drawPart(mr, k, "leg", m, 1.5f, 5, 1.5f, light, tint);
                }
            }
            default -> {
                float wind = rag ? 0 : a.windUp, strike = rag ? 0 : a.attackAnim, breathe = rag ? 0 : (float) Math.sin(t * 1.4f + a.id) * 0.25f;
                boolean lurk = !rag && "lurk".equals(a.brain.state) && !a.onCeiling;
                if (rag) trunk.set(base).translate(0, 7, 0);
                else {
                    // it rears to strike - the front of it up and back - and comes down and forward with the blow; walking, the body
                    // rocks from one set of four to the other
                    float sway = (float) Math.sin(cyc) * 0.45f * move, bob = (float) Math.abs(Math.sin(cyc)) * 0.35f * move;
                    trunk.set(base).translate(sway, 7 + breathe * 0.4f + bob + wind * 1.6f - (lurk ? 1.3f : 0) - strike * 0.8f, wind * 1.6f - strike * 2.4f)
                            .rotateZ(sway * 0.035f).rotateX(wind * 0.55f - strike * 0.25f + (a.alarmed ? 0.08f : 0));
                }
                drawPart(mr, k, "middle", trunk, 3, 3, 3, light, tint);
                m.set(trunk).translate(0, 0.5f, -3f).rotateY(rag ? 0 : a.headYaw * 0.6f);
                drawPart(mr, k, "head", m, 4, 4, 8, light, tint);
                m.set(trunk).translate(0, 0.8f + breathe, 3f);
                if (rag) m.rotateY(clamp((float) Math.atan2(rp.lx(6), rp.lz(6) - 3), -0.5f, 0.5f)).rotateX(clamp((float) -Math.atan2(rp.ly(6) - 7.8f, rp.lz(6) - 3), -0.6f, 0.6f));
                else m.rotateY(clamp(a.turn * 0.07f, -0.35f, 0.35f)).rotateX(-0.12f - wind * 0.3f + breathe * 0.06f);   // the abdomen follows the turn late
                drawPart(mr, k, "abdomen", m, 5, 4, 0, light, tint);
                // eight legs, working in alternate fours: each is lifted, carried forward and set down, and the other four push
                for (int i = 0; i < 8; i++) {
                    int row = i / 2, s = (i & 1) == 0 ? -1 : 1;
                    float hx = s * 3f, hz = (row - 1.5f) * 1.4f;
                    if (rag) {
                        int kn = 7 + i, ft = 15 + i;
                        m.set(trunk).translate(hx, 0.5f, hz);
                        AnimalBodies.along(m, rp.lx(kn) - hx, rp.ly(kn) - 7.5f, rp.lz(kn) - hz);
                        drawPart(mr, k, "leg", m, 0, 1, 1, light, tint);
                        m.set(base).translate(rp.lx(kn), rp.ly(kn), rp.lz(kn));
                        AnimalBodies.along(m, rp.lx(ft) - rp.lx(kn), rp.ly(ft) - rp.ly(kn), rp.lz(ft) - rp.lz(kn));
                        drawPart(mr, k, "leg", m, 0, 1, 1, light, tint);
                        continue;
                    }
                    float fan = (row - 1.5f) * 0.42f, ph = cyc + ((row + (s < 0 ? 0 : 1)) % 2 == 0 ? 0 : (float) Math.PI);
                    float step = (float) Math.sin(ph) * 0.34f * move, lift = Math.max(0, (float) Math.cos(ph)) * 0.36f * move;
                    // still, one leg at a time tests the ground
                    if (move < 0.05f && wind == 0 && ((int) Math.floor(t * 0.6f + a.id * 0.7f)) % 11 == i) lift += Math.max(0, (float) Math.sin(t * 8)) * 0.16f;
                    // rearing: the front pair right up and spread, the second pair half way; striking: the front pair down and forward, hard
                    float raise = wind * (row == 0 ? 0.95f : row == 1 ? 0.4f : 0) - strike * (row == 0 ? 0.55f : 0), reachF = (row == 0 ? wind * -0.25f + strike * 0.5f : 0);
                    float crouch = lurk ? 0.18f : 0;
                    hip.set(trunk).translate(hx, 0.5f, hz).rotateY(-s * (fan + step - reachF)).rotateZ(s * (0.55f + lift + raise + crouch));
                    m.set(hip);
                    if (s < 0) m.rotateY((float) Math.PI);
                    drawPart(mr, k, "leg", m, 0, 1, 1, light, tint);
                    // the lower half of the leg: the same piece again, hung from the end of the first and turned down to the ground
                    m.set(hip).translate(s * 15.5f, 0, 0).rotateZ(-s * (1.75f + crouch * 0.6f - raise * 0.55f - lift * 0.35f));
                    if (s < 0) m.rotateY((float) Math.PI);
                    drawPart(mr, k, "leg", m, 0, 1, 1, light, tint);
                }
            }
        }
    }
}
