package absentialis.director;

import absentialis.entity.Actor;
import absentialis.scene.Raid;
import absentialis.scene.RaidScreen;
import absentialis.scene.VehicleSystem;
import absentialis.sim.ZoneState;
import absentialis.vehicle.CarHandlingData;
import absentialis.world.District;
import absentialis.world.MapData;
import java.util.ArrayList;
import java.util.List;

/**
 * The five managers of Director Mode that act on the world rather than on the screen. Each owns one kind of setting,
 * applies a change the moment it is made, can say what is in force (the menus read their labels from here), and can
 * put back what it found.
 */
public final class WorldManagers {
    private WorldManagers() {}

    // ================================================================== WorldManager: time and weather
    public static final class WorldManager {
        public static final String[] TIMES = {"Midnight", "Small hours", "Dawn", "Morning", "Midday", "Afternoon", "Sunset", "Evening"};
        public static final float[] HOURS = {0f, 3f, 6f, 9f, 12f, 15f, 18.5f, 21f};
        /** Only Clear, until there is a weather system to choose from (the brief says so); the selector is there for when there is. */
        public static final String[] WEATHER = {"Clear"};
        private final Raid raid;
        public int time = 4, weather = 0;
        public boolean frozen;
        private double frozenHour, hourWas;
        private float rainWas;
        private boolean saved;

        public WorldManager(Raid raid) { this.raid = raid; }

        public void save() { if (saved) return; saved = true; hourWas = raid.profile.zone.hour; rainWas = raid.profile.zone.weatherRain; time = nearest(raid.hour() % 24); }
        private static int nearest(double h) { int best = 0; double bd = 99; for (int i = 0; i < HOURS.length; i++) { double d = Math.abs(((h - HOURS[i] + 36) % 24) - 12); if (d < bd) { bd = d; best = i; } } return best; }

        public String timeName() { return TIMES[time]; }
        public void setTime(int index) {
            time = Math.floorMod(index, TIMES.length);
            setHour(HOURS[time]);
        }
        /** Sets the clock: the hour of the day the world shows from this moment. */
        public void setHour(double hour) { raid.profile.zone.hour = ((hour - raid.time / 120.0) % 24 + 24) % 24; frozenHour = hour; }
        public void freeze(boolean on) { frozen = on; frozenHour = raid.hour() % 24; }
        public String weatherName() { return WEATHER[weather]; }
        public void setWeather(int index) { weather = Math.floorMod(index, WEATHER.length); }

        /** Each frame: a frozen clock is held where it was stopped, and the chosen weather is kept. */
        public void update() {
            if (frozen) raid.profile.zone.hour = ((frozenHour - raid.time / 120.0) % 24 + 24) % 24;
            raid.profile.zone.weatherRain = 0;                           // Clear, and it stays clear for the session
        }

        public void restore() { if (!saved) return; raid.profile.zone.hour = hourWas; raid.profile.zone.weatherRain = rainWas; frozen = false; saved = false; }
    }

    // ================================================================== PopulationManager: how many people, infected and vehicles
    public static final class PopulationManager {
        public static final String[] LEVELS = {"None", "Low", "Normal", "High"};
        /** How many of each are kept within {@link #RADIUS} metres of the actor at each level (Normal leaves the world as the game made it). */
        public static final int[] NPC_TARGET = {0, 6, -1, 26}, CAR_TARGET = {0, 2, -1, 9};
        public static float RADIUS = 90f;
        private final Raid raid;
        public int npc = 2, vehicles = 2;
        /** Bodies taken out of the world by a lower setting: they are kept, not destroyed, and come back when the setting is raised or the session ends. */
        public final List<Actor> benched = new ArrayList<>();
        public final List<VehicleSystem.Car> benchedCars = new ArrayList<>();
        /** What a higher setting added: removed when the session ends. */
        public final List<Actor> added = new ArrayList<>();
        public final List<VehicleSystem.Car> addedCars = new ArrayList<>();
        private float tick;

        public PopulationManager(Raid raid) { this.raid = raid; }

        public String npcName() { return LEVELS[npc]; }
        public String vehicleName() { return LEVELS[vehicles]; }
        public void setNpc(int level) { npc = Math.floorMod(level, LEVELS.length); tick = 0; }
        public void setVehicles(int level) { vehicles = Math.floorMod(level, LEVELS.length); tick = 0; }

        private boolean ambient(Actor a) { return !a.isPlayer && !a.mate() && !a.dead; }

        /** A few times a second the world is brought toward what the settings ask for - a little at a time, out of sight where it can be. */
        public void update(float dt, List<Actor> staged) {
            tick -= dt;
            if (tick > 0) return;
            tick = 0.4f;
            Actor p = raid.player;
            // ---- people and infected
            List<Actor> near = new ArrayList<>();
            for (Actor a : raid.actors) if (ambient(a) && !staged.contains(a) && a.distXZ(p.x, p.z) < RADIUS) near.add(a);
            int want = NPC_TARGET[npc];
            if (want < 0) { giveBack(4); for (Actor a : new ArrayList<>(added)) if (a.distXZ(p.x, p.z) > 40 || a.dead) { remove(a); added.remove(a); break; } }
            else if (near.size() > want) {
                // furthest first, so the street empties from the far end
                near.sort((a, b) -> Float.compare(b.distXZ(p.x, p.z), a.distXZ(p.x, p.z)));
                for (int i = 0; i < Math.min(6, near.size() - want); i++) { Actor a = near.get(i); remove(a); if (added.remove(a)) continue; benched.add(a); }
            } else if (near.size() < want) {
                if (!benched.isEmpty()) giveBack(2);
                else for (int i = 0; i < Math.min(2, want - near.size()); i++) spawnAmbient();
            }
            // ---- vehicles (never the one being driven, or one the scene put there)
            List<VehicleSystem.Car> cars = new ArrayList<>();
            for (VehicleSystem.Car c : raid.vehicles.cars) if (c != raid.vehicles.driving && !c.owned) cars.add(c);
            int wantCars = CAR_TARGET[vehicles];
            if (wantCars < 0) { while (!benchedCars.isEmpty()) raid.vehicles.cars.add(benchedCars.remove(benchedCars.size() - 1)); }
            else if (cars.size() > wantCars) { VehicleSystem.Car c = cars.get(cars.size() - 1); raid.vehicles.cars.remove(c); if (!addedCars.remove(c)) benchedCars.add(c); }
            else if (cars.size() < wantCars) {
                if (!benchedCars.isEmpty()) raid.vehicles.cars.add(benchedCars.remove(benchedCars.size() - 1));
                else { VehicleSystem.Car c = spawnCar(); if (c != null) addedCars.add(c); }
            }
        }

        private void remove(Actor a) { raid.neural.remove(a.id, true); raid.neuralBody.forget(a.id); raid.actors.remove(a); }
        private void giveBack(int n) { for (int i = 0; i < n && !benched.isEmpty(); i++) { Actor a = benched.remove(benched.size() - 1); if (!raid.actors.contains(a)) raid.actors.add(a); } }

        /** Somewhere a person could have walked from: a street point 35 to 80 metres off, not in front of the camera if that can be helped. */
        private float[] place() {
            Actor p = raid.player;
            List<MapData.Node> nodes = raid.world.map.nodes;
            float fx = (float) -Math.sin(p.yaw), fz = (float) -Math.cos(p.yaw);
            MapData.Node best = null;
            for (int tries = 0; tries < 40 && !nodes.isEmpty(); tries++) {
                MapData.Node n = nodes.get(raid.rng.nextInt(nodes.size()));
                float dx = n.x - p.x, dz = n.z - p.z, d = (float) Math.sqrt(dx * dx + dz * dz);
                if (d < 35 || d > 80) continue;
                best = n;
                if ((dx * fx + dz * fz) / d < 0.3f) break;               // behind or to the side: take it
            }
            if (best == null) { double ang = raid.rng.nextFloat() * Math.PI * 2; int x = (int) (p.x + Math.cos(ang) * 45), z = (int) (p.z + Math.sin(ang) * 45); int gy = raid.groundY(x, z); return gy < 0 ? null : new float[]{x + 0.5f, gy, z + 0.5f}; }
            return new float[]{best.x + 0.5f, best.y, best.z + 0.5f};
        }

        private void spawnAmbient() {
            float[] at = place();
            if (at == null) return;
            String kind = raid.rng.nextFloat() < 0.45f ? (raid.rng.nextFloat() < 0.8f ? "walker" : "sprinter") : ZoneState.FACTIONS[raid.rng.nextInt(ZoneState.FACTIONS.length)];
            Actor a = raid.debugSpawn(kind, at[0], at[2], at[1]);
            if (a != null) added.add(a);
        }

        private VehicleSystem.Car spawnCar() {
            float[] at = place();
            List<CarHandlingData> all = new ArrayList<>(CarHandlingData.all());
            if (at == null || all.isEmpty()) return null;
            return raid.vehicles.spawn(all.get(raid.rng.nextInt(all.size())).id, at[0], at[1], at[2], raid.rng.nextFloat() * 6.28f, raid.rng.nextInt(9999));
        }

        /** The session is over: what was added goes, what was set aside comes back. */
        public void restore() {
            for (Actor a : added) remove(a);
            added.clear();
            giveBack(benched.size());
            raid.vehicles.cars.removeAll(addedCars);
            addedCars.clear();
            raid.vehicles.cars.addAll(benchedCars);
            benchedCars.clear();
            npc = 2; vehicles = 2;
        }
    }

    // ================================================================== ModifierManager: what the actor is exempt from
    public static final class ModifierManager {
        private final Raid raid;
        private boolean godWas, noclipWas, freezeWas, saved;
        /** Wanted Status: Disabled means nobody treats the actor as a target; Enabled leaves the world to react as it would. */
        public boolean wanted;

        public ModifierManager(Raid raid) { this.raid = raid; }

        public void save() { if (saved) return; saved = true; godWas = raid.god; noclipWas = raid.noclip; freezeWas = raid.freezeAI; raid.unwanted = !wanted; raid.noDeath = true; }
        public boolean invincible() { return raid.god; }
        public void invincible(boolean on) { raid.god = on; }
        public boolean noclip() { return raid.noclip; }
        public void noclip(boolean on) { raid.noclip = on; if (!on) { int gy = raid.groundY((int) Math.floor(raid.player.x), (int) Math.floor(raid.player.z)); if (gy >= 0 && raid.player.y > gy + 30) raid.player.y = gy; } }
        public void wanted(boolean on) { wanted = on; raid.unwanted = !on; }
        /** NeuralAI: thinking on or off for everybody (bodies stand where they are; the world still runs). */
        public boolean thinking() { return !raid.freezeAI; }
        public void thinking(boolean on) { raid.freezeAI = !on; }
        public boolean overlay() { return raid.aiDebug; }
        public void overlay(boolean on) { raid.aiDebug = on; }

        public void restore() { if (!saved) return; raid.god = godWas; raid.noclip = noclipWas; raid.freezeAI = freezeWas; raid.unwanted = false; raid.noDeath = false; raid.aiDebug = false; saved = false; }
    }

    // ================================================================== LocationManager: the places an actor can be sent
    public static final class LocationManager {
        /** A preset: where, which way to face, and - if it cannot be used - why (a spawn restriction). */
        public record Location(String name, int x, int z, float heading, String unavailable) {
            public boolean available() { return unavailable == null || unavailable.isEmpty(); }
        }
        private final Raid raid;
        public final List<Location> all = new ArrayList<>();
        public String last = "";

        public LocationManager(Raid raid) {
            this.raid = raid;
            // the list the design gives, against the places this city has. What it has is used; what it has not is shown, and says so.
            add("Airport", "airport", 0);
            add("Jail", "jail", 0);
            add("City Center", "police_hq", 0);
            District wild = District.WILDERNESS;
            MapData.Node wn = null;
            for (MapData.Node n : raid.world.map.nodes) if (n.district == wild) { wn = n; break; }
            // no street runs into the wilderness: failing a street point, the nearest open ground of it to the middle of the map
            int[] open = null;
            if (wn == null && !raid.world.dev) {
                int cx = absentialis.world.World.W / 2, cz = absentialis.world.World.D / 2;
                long best = Long.MAX_VALUE;
                for (int x = 24; x < absentialis.world.World.W - 24; x += 12) for (int z = 24; z < absentialis.world.World.D - 24; z += 12) {
                    if (raid.world.district(x, z) != wild) continue;
                    int gy = raid.groundY(x, z);
                    if (gy < 0 || raid.world.heightAt(x, z) >= gy + 1) continue;
                    long dd = (long) (x - cx) * (x - cx) + (long) (z - cz) * (z - cz);
                    if (dd < best) { best = dd; open = new int[]{x, z}; }
                }
            }
            if (open != null) all.add(new Location("Wilderness", open[0], open[1], 0, null));
            else all.add(wn != null ? new Location("Wilderness", wn.x, wn.z, 0, null) : new Location("Wilderness", 0, 0, 0, raid.world.dev ? "the developer map has no wilderness" : "no walkable ground was found in the wilderness"));
            add("Desert", "farm", (float) Math.PI * 0.5f);              // the Barrens are as near to a desert as Absentialis has
            add("Industrial District", "power_plant", 0);
            all.add(new Location("Mountains", 0, 0, 0, "Absentialis has no mountains"));
            add("Downtown", "grocery", (float) Math.PI);
            add("Residential", "mansion", 0);
            add("Military Base", "military_base", 0);
            add("Laboratory", "science_lab", 0);
        }

        private void add(String name, String landmark, float heading) {
            for (MapData.Landmark lm : raid.world.map.anchors) if (lm.id.equals(landmark)) {
                // in front of the place, not inside it
                all.add(new Location(name + (name.equals("Desert") ? " (the Barrens)" : ""), lm.cx(), Math.min(lm.z0, lm.z1) - 5, heading, null));
                return;
            }
            all.add(new Location(name, 0, 0, heading, raid.world.dev ? "not on the developer map" : "this city has no such place"));
        }

        /** Sends the actor there: to safe ground at the place, facing the way the preset says. Null if it went, otherwise why not. */
        public String travel(Location l) {
            if (!l.available()) return l.unavailable();
            if (raid.vehicles.inside() || raid.vehicles.busy()) return "Get out of the vehicle first";
            float[] spot = raid.safeSpot(l.x(), l.z());
            Actor p = raid.player;
            p.x = spot[0]; p.y = spot[1] + 0.1f; p.z = spot[2]; p.vx = p.vy = p.vz = 0;
            p.yaw = l.heading(); p.pitch = 0;
            last = l.name();
            return null;
        }
    }

    // ================================================================== VehicleManager: who may drive, and the optional extras
    public static final class VehicleManager {
        private final Raid raid;
        public final List<VehicleSystem.Car> spawned = new ArrayList<>();
        public VehicleManager(Raid raid) { this.raid = raid; }

        public boolean canDrive() { return raid.playerCanDrive; }
        public List<CarHandlingData> models() { return new ArrayList<>(CarHandlingData.all()); }

        /** Parks a vehicle a few metres in front of the actor. */
        public String spawn(String id) {
            Actor p = raid.player;
            float fx = (float) -Math.sin(p.yaw), fz = (float) -Math.cos(p.yaw);
            int gx = (int) (p.x + fx * 7), gz = (int) (p.z + fz * 7), gy = raid.groundY(gx, gz);
            if (gy < 0) return "No ground in front of the actor to put it on";
            VehicleSystem.Car c = raid.vehicles.spawn(id, gx + 0.5f, gy, gz + 0.5f, p.yaw + (float) Math.PI / 2, spawned.size() * 131 + 5);
            if (c == null) return "No such vehicle";
            spawned.add(c);
            return null;
        }

        /** Makes the vehicle being driven - or the nearest one - as it left the factory. */
        public String repair() {
            VehicleSystem.Car c = raid.vehicles.driving != null ? raid.vehicles.driving : raid.vehicles.nearby();
            if (c == null) return "No vehicle here";
            c.sim.damage.repair();
            return null;
        }

        public void restore() { raid.vehicles.cars.removeAll(spawned); spawned.clear(); }
    }
}
