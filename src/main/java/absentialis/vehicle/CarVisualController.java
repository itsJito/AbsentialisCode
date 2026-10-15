package absentialis.vehicle;

import absentialis.engine.math.Mat4;
import absentialis.engine.render.DrawList;
import absentialis.engine.render.ModelRenderer;
import absentialis.engine.render.Tex;
import absentialis.engine.voxel.VoxModel;

/**
 * The visible car. It reads the simulation and never writes to it: the chassis model follows the rigid body's
 * interpolated transform, each wheel model sits where its suspension has put it, turns with the steering and
 * spins at the speed the tyre solver gave it, and a burst tyre is drawn flat and wobbling.
 *
 * Every family is built from one parametric <em>base</em>: a lower body with wheel arches, a cabin with pillars
 * and glass, and the family's own additions (a pickup bed, a van's box, a light bar). Collision damage deforms
 * that base zone by zone - the struck end is compressed toward the cabin, panels are pushed in, paint is scraped
 * to bare metal, glass and lamps go - so the same car looks different depending on what it has been through.
 * The model is rebuilt only when the damage state's version changes.
 */
public final class CarVisualController {
    /** One model voxel is a tenth of a metre. */
    public static final float VOX = 0.1f;
    private static final int TYRE = Tex.rgb(24, 24, 26), RIM = Tex.rgb(150, 152, 156), GLASS = 0x58A6C4D2, DARK = Tex.rgb(34, 36, 40), CHROME = Tex.rgb(176, 178, 182),
            LAMP = Tex.rgb(244, 236, 200), TAIL = Tex.rgb(196, 30, 26), SEAT = Tex.rgb(58, 52, 46), RED = Tex.rgb(200, 36, 30), BLUE = Tex.rgb(40, 80, 210), WHITE = Tex.rgb(232, 230, 222);

    private final CarVehicle car;
    private final String key;
    private int builtVersion = -1, builtPaint;
    private VoxModel body, glass, door, door2, wheel, wheelFlat;
    /** 1.1: the passenger's door is a door too: how far it stands open (0..1), and whether it has been torn off. */
    public float doorOpen2;
    public boolean door2Gone;
    private final Mat4 base = new Mat4(), m = new Mat4();
    /** Driver's door swing, 0 closed .. 1 open (entry and exit animation). */
    public float doorOpen;
    /** How far the door stands out from the body when fully open (radians). */
    public static float DOOR_SWING = 1.15f;

    /** 0.8: which way the free edge of the open door has gone, in the car's own frame: negative x is out from the driver's side, where it belongs. */
    public float doorEdgeX() { return hingeX() - (float) Math.sin(doorOpen * DOOR_SWING) * doorLength(); }
    /** The driver's door has been torn off (0.6: it is a real door, and can be): nothing is drawn on the hinge. */
    public boolean doorGone;

    /** The driver's door as a loose leaf (x along it from the hinge, y up, z through it): what flies off when the hinges go. */
    public VoxModel doorLeaf() {
        if (body == null) build();
        VoxModel l = new VoxModel(door.sz, door.sy, door.sx);
        for (int x = 0; x < door.sx; x++) for (int y = 0; y < door.sy; y++) for (int z = 0; z < door.sz; z++) l.set(z, y, x, door.get(x, y, z));
        return l;
    }
    public String doorKey() { return key + ":leaf"; }
    /** The hinge in the car's frame (metres), and the leaf's length and height. */
    public float hingeX() { if (body == null) build(); return hingeX; }
    public float hingeY() { if (body == null) build(); return hingeY; }
    public float hingeZ() { if (body == null) build(); return hingeZ; }
    public float doorLength() { if (body == null) build(); return door.sz * VOX; }
    public float doorHeight() { if (body == null) build(); return door.sy * VOX; }
    /** Set while the camera sits inside: the panes are left out so the driver can see the road. */
    public boolean hideGlass;
    // door hinge in the car frame, and the driver's seat
    private float hingeX, hingeY, hingeZ, hinge2X;
    /** The passenger door's hinge, across the car from the driver's. */
    public float hinge2X() { if (body == null) build(); return hinge2X; }
    public final float seatX, seatY, seatZ;

    public CarVisualController(CarVehicle car) {
        this.car = car;
        key = "veh:" + car.id;
        CarHandlingData d = car.data;
        seatX = -d.width * 0.23f;
        seatY = d.wheelRadius * 0.75f + 0.42f;
        float cabFront = cabinFront(d), cabRear = cabinRear(d);
        seatZ = cabFront - (cabFront - cabRear) * (isBox(d) ? 0.22f : 0.38f);
    }

    private static boolean isBox(CarHandlingData d) { return d.model.equals("van") || d.model.equals("ambulance"); }
    private static float cabinFront(CarHandlingData d) { return d.length / 2 - d.length * (isBox(d) ? 0.16f : d.model.equals("offroad") ? 0.26f : 0.30f); }
    private static float cabinRear(CarHandlingData d) {
        return switch (d.model) { case "pickup" -> d.length * 0.02f; case "sedan", "patrol" -> -d.length * 0.30f; case "coupe" -> -d.length * 0.28f; default -> -d.length / 2 + 0.12f; };
    }

    // ------------------------------------------------------------------ building
    private static int vx(float metres) { return Math.round(metres / VOX); }

    /** Builds body, glass and door for the current paint and damage. */
    private void build() {
        CarHandlingData d = car.data;
        CarDamageState dm = car.damage;
        int sx = vx(d.width) + 4, sy = vx(d.height) + 6, sz = vx(d.length) + 6;       // margin for mirrors, bars, racks
        int cx = sx / 2, cz = sz / 2;
        VoxModel b = new VoxModel(sx, sy, sz), g = new VoxModel(sx, sy, sz);
        int paint = car.paint, paint2 = Tex.shade(paint, 0.82f), trim = DARK;
        int hw = vx(d.width / 2), hl = vx(d.length / 2);
        int clear = Math.max(2, vx(d.wheelRadius * 0.75f)), top = vx(d.height);
        boolean box = isBox(d), pickup = d.model.equals("pickup"), tall = d.model.equals("suv") || d.model.equals("offroad") || box;
        int waist = clear + Math.round((top - clear) * (box ? 0.46f : tall ? 0.52f : 0.56f));
        int cabF = cz + vx(cabinFront(d)), cabR = cz + vx(cabinRear(d));
        int x0 = cx - hw, x1 = cx + hw - 1, z0 = cz - hl, z1 = cz + hl - 1;

        // ---- lower body: a shell with a floor, so the cabin is hollow and you can sit in it
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = clear; y <= waist; y++) {
            boolean shell = x == x0 || x == x1 || z == z0 || z == z1 || y == clear;
            boolean nose = z > cabF, tail = z < cabR;
            // bonnet and boot are solid-topped below the waistline
            int deck = nose ? waist - (tall ? 1 : 2) - Math.max(0, (z - cabF) / 6) : tail && !pickup ? waist - (d.model.equals("sedan") || d.model.equals("patrol") || d.model.equals("coupe") ? 1 : 0) : waist;
            if (y > deck) continue;
            if ((nose || tail && !pickup) && y == deck) shell = true;
            if (pickup && tail && y > clear + 2 && x != x0 && x != x1 && z != z0) continue;     // the bed is open
            if (!shell) continue;
            b.set(x, y, z, y <= clear + 1 ? trim : paint);
        }
        // bumpers, grille, lamps
        b.box(x0, clear, z1, x1, clear + 2, z1, trim);
        b.box(x0, clear, z0, x1, clear + 2, z0, trim);
        b.box(cx - hw / 2, clear + 3, z1, cx + hw / 2 - 1, waist - 4, z1, DARK);
        if (dm.lightL) b.box(x0 + 1, waist - 5, z1, x0 + 3, waist - 3, z1, LAMP);
        if (dm.lightR) b.box(x1 - 3, waist - 5, z1, x1 - 1, waist - 3, z1, LAMP);
        b.box(x0 + 1, waist - 4, z0, x0 + 3, waist - 2, z0, TAIL);
        b.box(x1 - 3, waist - 4, z0, x1 - 1, waist - 2, z0, TAIL);

        // ---- wheel arches: cut the body around each wheel, then line the cut in dark trim
        int rad = vx(d.wheelRadius * d.wheelScale) + 1;
        for (int i = 0; i < 4; i++) {
            int wz = cz + vx(d.wheels[i][2]), wy = vx(d.wheelRadius);
            boolean left = d.wheels[i][0] < 0;
            for (int x = left ? x0 : x1 - 3; x <= (left ? x0 + 3 : x1); x++) for (int z = wz - rad - 1; z <= wz + rad + 1; z++) for (int y = clear; y <= wy + rad + 1; y++) {
                float dy = y - wy, dzz = z - wz, r2 = dy * dy + dzz * dzz;
                if (r2 <= rad * rad) b.set(x, y, z, 0);
                else if (r2 <= (rad + 1.5f) * (rad + 1.5f) && b.get(x, y, z) != 0) b.set(x, y, z, trim);
            }
        }

        // ---- cabin: pillars, roof, glass
        int roofInset = box ? 0 : 1;
        int pillar = 2;
        for (int x = x0 + roofInset; x <= x1 - roofInset; x++) for (int z = cabR; z <= cabF; z++) for (int y = waist + 1; y <= top; y++) {
            boolean side = x == x0 + roofInset || x == x1 - roofInset, front = z == cabF, rear = z == cabR, roof = y == top;
            if (!side && !front && !rear && !roof) continue;
            // raked windscreen and rear glass on anything that is not a box
            int rake = box ? 0 : (y - waist) / (tall ? 4 : 2);
            if (front && !box) { if (z - rake < cabR) continue; }
            boolean post = side && (z >= cabF - pillar - rake || z <= cabR + pillar || !pickup && !d.model.equals("coupe") && Math.abs(z - (cabF + cabR) / 2) <= 1);
            boolean window = !roof && y < top - 1 && y > waist + 1 && !post && !(box && side && z < cabF - vx(1.3f)) && !(front || rear ? (x <= x0 + roofInset + 1 || x >= x1 - roofInset - 1) : false);
            int zz = front ? z - rake : rear && !box && !tall && !pickup ? z + Math.min(rake, 3) : z;
            if (zz < z0 || zz > z1) continue;
            if (window) {
                boolean broken = front && !dm.windscreen || rear && !dm.rearGlass;
                if (!broken) g.set(x, y, zz, GLASS);
            } else b.set(x, y, zz, roof ? paint2 : paint);
            if (roof && !front) b.set(x, y, z, paint2);
        }
        // interior: dashboard, wheel, seats (dark, so the cabin reads as a space)
        b.box(x0 + 1, waist - 3, cabF - 3, x1 - 1, waist, cabF - 1, DARK);
        int stx = cx + vx(seatX);
        b.box(stx - 1, waist - 1, cabF - 5, stx + 1, waist + 1, cabF - 4, Tex.rgb(20, 20, 22));
        for (int s = 0; s < 2; s++) {
            int sxp = s == 0 ? cx + vx(seatX) : cx - vx(seatX), szp = cz + vx(seatZ);
            b.box(sxp - 2, clear + 1, szp - 2, sxp + 2, clear + 4, szp + 2, SEAT);
            b.box(sxp - 2, clear + 5, szp - 3, sxp + 2, Math.min(top - 2, waist + 4), szp - 2, SEAT);
        }
        // door seams and mirrors
        int doorF = cabF - 1, doorR = Math.max(cabR + 1, cabF - vx(1.15f));
        for (int y = clear + 2; y <= waist; y++) { tintIf(b, x0, y, doorF, 0.7f); tintIf(b, x1, y, doorF, 0.7f); tintIf(b, x0, y, doorR, 0.7f); tintIf(b, x1, y, doorR, 0.7f); }
        b.box(x0 - 1, waist + 1, cabF - 1, x0 - 1, waist + 2, cabF, DARK);
        b.box(x1 + 1, waist + 1, cabF - 1, x1 + 1, waist + 2, cabF, DARK);

        // ---- what makes each family itself
        switch (d.model) {
            case "pickup" -> { b.box(x0, waist + 1, cabR - 1, x1, waist + 2, cabR - 1, paint); b.box(x0 + 1, clear + 2, z0 + 1, x1 - 1, clear + 2, cabR - 1, DARK); b.box(x0, waist + 1, z0, x1, waist + 1, z0, trim); }
            case "suv" -> { b.box(x0 + 2, top + 1, cabR + 3, x0 + 2, top + 1, cabF - 4, CHROME); b.box(x1 - 2, top + 1, cabR + 3, x1 - 2, top + 1, cabF - 4, CHROME); }
            case "offroad" -> {
                b.box(x0 + 1, clear + 1, z1 + 1, x1 - 1, clear + 4, z1 + 1, CHROME);                                    // bull bar
                b.box(x0 + 2, clear + 5, z1 + 1, x0 + 2, waist - 2, z1 + 1, CHROME); b.box(x1 - 2, clear + 5, z1 + 1, x1 - 2, waist - 2, z1 + 1, CHROME);
                for (int y = waist - 5; y <= waist + 1; y++) for (int x = cx - 3; x <= cx + 3; x++) { float dy = y - (waist - 2), dx = x - cx; if (dy * dy + dx * dx <= 12) b.set(x, y, z0 - 1, TYRE); }   // spare wheel
                b.box(x1, waist + 1, cabF - 2, x1, top + 1, cabF - 2, DARK);                                             // snorkel
                b.box(x0 + 1, top + 1, cabR + 2, x1 - 1, top + 1, cabR + 2, DARK); b.box(x0 + 1, top + 1, cabF - 6, x1 - 1, top + 1, cabF - 6, DARK);   // roof rack
            }
            case "ambulance" -> {
                for (int z = z0 + 2; z <= cabF - 12; z++) { b.set(x0, waist - 1, z, RED); b.set(x1, waist - 1, z, RED); b.set(x0, waist - 2, z, RED); b.set(x1, waist - 2, z, RED); }
                int mz = (z0 + cabF - 12) / 2, my = waist + 6;
                for (int k = -3; k <= 3; k++) { b.set(x0, my + k, mz, RED); b.set(x0, my, mz + k, RED); b.set(x1, my + k, mz, RED); b.set(x1, my, mz + k, RED); b.set(x0, my + k, mz + 1, RED); b.set(x1, my + k, mz + 1, RED); }
                b.box(cx - 5, top + 1, cabF - 6, cx - 1, top + 2, cabF - 4, RED); b.box(cx, top + 1, cabF - 6, cx + 4, top + 2, cabF - 4, BLUE);
            }
            case "patrol" -> {
                for (int y = clear + 2; y <= waist; y++) for (int z = doorR; z <= doorF; z++) { b.set(x0, y, z, WHITE); b.set(x1, y, z, WHITE); }
                b.box(cx - 5, top + 1, (cabF + cabR) / 2, cx - 1, top + 2, (cabF + cabR) / 2 + 1, RED); b.box(cx, top + 1, (cabF + cabR) / 2, cx + 4, top + 2, (cabF + cabR) / 2 + 1, BLUE);
                b.box(x0 + 1, clear + 1, z1 + 1, x1 - 1, clear + 3, z1 + 1, DARK);                                       // push bar
            }
            case "van" -> { for (int y = clear + 2; y <= top - 1; y++) { tintIf(b, x0, y, cabF - vx(2.4f), 0.7f); tintIf(b, x1, y, cabF - vx(2.4f), 0.7f); tintIf(b, cx, y, z0, 0.7f); } }
            default -> {}
        }

        // ---- the driver's door is its own piece, so it can swing
        VoxModel dr = new VoxModel(3, top - clear + 1, doorF - doorR + 1);
        for (int y = clear + 2; y <= top - 1; y++) for (int z = doorR + 1; z < doorF; z++) for (int x = x0; x <= x0 + 1; x++) {
            int c = b.get(x, y, z);
            if (c != 0 && x == x0) { dr.set(1, y - clear, doorF - z, c); b.set(x, y, z, 0); }
            int gc = g.get(x, y, z);
            if (gc != 0 && x == x0 + (box ? 0 : roofInset)) { dr.set(1, y - clear, doorF - z, gc); g.set(x, y, z, 0); }
        }
        hingeX = (x0 - cx) * VOX; hingeY = clear * VOX; hingeZ = (doorF - cz) * VOX;
        // ---- 1.1: and so is the passenger's, cut from the other side the same way (it was painted on, and nothing opened when somebody got in there)
        VoxModel dr2 = new VoxModel(3, top - clear + 1, doorF - doorR + 1);
        for (int y = clear + 2; y <= top - 1; y++) for (int z = doorR + 1; z < doorF; z++) for (int x = x1 - 1; x <= x1; x++) {
            int c = b.get(x, y, z);
            if (c != 0 && x == x1) { dr2.set(1, y - clear, doorF - z, c); b.set(x, y, z, 0); }
            int gc = g.get(x, y, z);
            if (gc != 0 && x == x1 - (box ? 0 : roofInset)) { dr2.set(1, y - clear, doorF - z, gc); g.set(x, y, z, 0); }
        }
        door2 = dr2;
        hinge2X = (x1 + 1 - cx) * VOX;

        // ---- damage: each zone is deformed toward the middle of the car by its level
        deform(b, g, dm, cx, cz, x0, x1, z0, z1, clear, top, cabF, cabR, car.id * 131);
        body = b; glass = g; door = dr;
        builtVersion = dm.version; builtPaint = car.paint;
        if (wheel == null) { wheel = wheelModel(d, false); wheelFlat = wheelModel(d, true); }
    }

    private static void tintIf(VoxModel v, int x, int y, int z, float k) { int c = v.get(x, y, z); if (c != 0 && (c >>> 24) == 255) v.set(x, y, z, Tex.shade(c, k)); }

    /**
     * Crumple. The body is pushed in at each place it was struck (CarDamageState.dents), by as much as the blow was
     * worth. Displaced panels buckle by a voxel here and there, paint is scraped to primer and bare steel, and at the
     * worst pieces are missing.
     */
    private static void deform(VoxModel b, VoxModel g, CarDamageState dm, int cx, int cz, int x0, int x1, int z0, int z1, int clear, int top, int cabF, int cabR, int seed) {
        if (dm.dents.isEmpty()) return;
        VoxModel src = copy(b), gsrc = copy(g);
        clear(b); clear(g);
        java.util.Random rnd = new java.util.Random(seed);
        int bare = Tex.rgb(120, 122, 126), primer = Tex.rgb(74, 62, 54);
        // 0.8: the metal gives where it was struck. Every dent pushes what is near it toward the middle of the car (or, from
        // above, down), most at the point of the blow and nothing at all a dent's radius away; dents that overlap add up.
        float midY = (clear + top) * 0.5f * VOX, halfW = (x1 - x0 + 1) * 0.5f * VOX, halfL = (z1 - z0 + 1) * 0.5f * VOX;
        for (int pass = 0; pass < 2; pass++) {
            VoxModel from = pass == 0 ? src : gsrc, to = pass == 0 ? b : g;
            for (int x = 0; x < from.sx; x++) for (int y = 0; y < from.sy; y++) for (int z = 0; z < from.sz; z++) {
                int c = from.get(x, y, z);
                if (c == 0) continue;
                float px = (x + 0.5f - cx) * VOX, py = (y + 0.5f) * VOX, pz = (z + 0.5f - cz) * VOX;
                float mx = 0, my = 0, mz = 0, hit = 0;
                for (float[] d : dm.dents) {
                    // the blow is taken at the skin: a point reported inside the body is carried out to the surface it came through
                    float ex = d[0], ey = Math.max(clear * VOX, Math.min(top * VOX, d[1] + clear * VOX * 0.5f)), ez = d[2];
                    float ddx = px - ex, ddy = (py - ey) * 0.7f, ddz = pz - ez, dist = (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                    if (dist >= d[4]) continue;
                    float t = 1 - dist / d[4], push = t * t * d[3] * DENT_DEPTH;
                    boolean fromAbove = ey > top * VOX * 0.86f && Math.abs(ex) < halfW * 0.8f && Math.abs(ez) < halfL * 0.7f;
                    if (fromAbove) my -= push;
                    else {
                        // toward the car's long axis from a side, toward its middle from an end - whichever the blow was closer to
                        float ax = -ex, az = -ez * (Math.abs(ez) / halfL > Math.abs(ex) / halfW ? 1f : 0.25f), al = (float) Math.sqrt(ax * ax + az * az);
                        if (al > 1e-3f) { mx += ax / al * push; mz += az / al * push; }
                        if (py > midY) my -= push * 0.18f;
                    }
                    hit = Math.max(hit, t * d[3] * 3f);
                }
                float nx = x + mx / VOX, ny = y + my / VOX, nz = z + mz / VOX;
                if (hit > 0.4f) {
                    // buckling, missing pieces and scraped paint grow with how hard this part was hit
                    if (hit > 2.2f && rnd.nextFloat() < 0.16f) continue;
                    if (rnd.nextFloat() < hit * 0.12f) ny += rnd.nextBoolean() ? 1 : -1;
                    if (pass == 0 && (c >>> 24) == 255) { float r = rnd.nextFloat(); c = r < hit * 0.10f ? bare : r < hit * 0.22f ? primer : Tex.shade(c, 1f - Math.min(0.35f, hit * 0.09f)); }
                    if (pass == 1 && hit > 1.2f) continue;      // glass does not bend
                }
                int ix = Math.round(nx), iy = Math.round(ny), iz = Math.round(nz);
                if (to.in(ix, iy, iz)) to.set(ix, iy, iz, c);
            }
        }
    }

    /** How far a full dent pushes the metal in at its centre (metres). */
    public static float DENT_DEPTH = 0.62f;

    private static VoxModel copy(VoxModel v) { VoxModel c = new VoxModel(v.sx, v.sy, v.sz); for (int x = 0; x < v.sx; x++) for (int y = 0; y < v.sy; y++) for (int z = 0; z < v.sz; z++) { int k = v.get(x, y, z); if (k != 0) c.set(x, y, z, k); } return c; }
    private static void clear(VoxModel v) { for (int x = 0; x < v.sx; x++) for (int y = 0; y < v.sy; y++) for (int z = 0; z < v.sz; z++) if (v.get(x, y, z) != 0) v.set(x, y, z, 0); }

    /** A wheel: tyre and rim around the x axis. A flat one sits squashed on its rim. */
    private static VoxModel wheelModel(CarHandlingData d, boolean flat) {
        int r = Math.max(2, vx(d.wheelRadius * d.wheelScale)), w = Math.max(2, vx(0.24f * d.wheelScale));
        VoxModel v = new VoxModel(w, r * 2 + 1, r * 2 + 1);
        for (int x = 0; x < w; x++) for (int y = 0; y <= r * 2; y++) for (int z = 0; z <= r * 2; z++) {
            float dy = y - r, dz = z - r, rr = (float) Math.sqrt(dy * dy + dz * dz);
            if (rr > r + 0.3f) continue;
            boolean tread = rr > r * 0.62f;
            if (flat && tread && rr > r * 0.86f) continue;                       // the carcass has let go of the rim
            boolean spoke = !tread && (Math.abs(dy) < 0.7f || Math.abs(dz) < 0.7f || rr < 1.3f);
            v.set(x, y, z, tread ? (flat ? Tex.rgb(34, 34, 36) : TYRE) : spoke || x > 0 && x < w - 1 ? RIM : Tex.shade(RIM, 0.6f));
        }
        return v;
    }

    // ------------------------------------------------------------------ drawing
    /**
     * Draws the car through the model renderer (recorded for the deferred renderer, or immediate in the legacy
     * one). hideCabin drops the driver's door when the camera is in the driver's seat and would look through it.
     */
    public void draw(ModelRenderer mr, float light) {
        CarHandlingData d = car.data;
        if (body == null || builtVersion != car.damage.version || builtPaint != car.paint) {
            build();
            mr.drop(key + ":b"); mr.drop(key + ":g"); mr.drop(key + ":d");
        }
        car.matrix(base.m);
        // paint is glossy and metallic where it is intact
        mr.material(0.36f, 0.35f);
        ModelRenderer.Mesh mb = mr.cached(key + ":b");
        if (mb == null) mb = mr.mesh(key + ":b", body, VOX);
        mr.draw(mb, base, light, 0xFFFFFFFF, 0);
        // the door swings on its front edge - outward. (0.7 turned it the other way: the free edge went in through the driver's
        // seat and out the far side of the car.) The leaf lies along -z from the hinge on the car's left; a positive turn about
        // the vertical carries that edge toward -x, away from the body.
        m.set(base).translate(hingeX, hingeY, hingeZ).rotateY(doorOpen * DOOR_SWING).scale(VOX, VOX, -VOX).translate(-1.5f, 0, 0);
        if (!doorGone) {
            ModelRenderer.Mesh md = mr.cached(key + ":d");
            if (md == null) md = mr.meshAt(key + ":d", door, 0, 0, 0);
            mr.draw(md, m, light, 0xFFFFFFFF, 0);
        }
        // the passenger's door: the mirror of it - hinged on its front edge on the car's right, its free edge going out toward +x
        if (!door2Gone && door2 != null) {
            m.set(base).translate(hinge2X, hingeY, hingeZ).rotateY(-doorOpen2 * DOOR_SWING).scale(VOX, VOX, -VOX).translate(-1.5f, 0, 0);
            ModelRenderer.Mesh md2 = mr.cached(key + ":d2");
            if (md2 == null) md2 = mr.meshAt(key + ":d2", door2, 0, 0, 0);
            mr.draw(md2, m, light, 0xFFFFFFFF, 0);
        }
        mr.material(0.08f, 0);
        if (!hideGlass) {
            ModelRenderer.Mesh mg = mr.cached(key + ":g");
            if (mg == null) mg = mr.mesh(key + ":g", glass, VOX);
            mr.draw(mg, base, light, 0xFFFFFFFF, 0);
        }
        // wheels: suspension travel, steering, spin, and the wobble of a flat
        mr.material(0.85f, 0);
        for (CarWheel w : car.wheels) {
            boolean flat = w.tyre == CarWheel.Tyre.BURST;
            VoxModel wm = flat ? wheelFlat : wheel;
            float wy = w.ay + d.suspensionUpper - w.length - (flat ? d.wheelRadius * 0.14f : 0);
            float wob = flat ? (float) Math.sin(w.rotation * 1.0f) * 0.12f : 0;
            int half = wm.sy / 2;
            m.set(base).translate(w.ax, wy, w.az).rotateY(w.steerAngle + wob).rotateX(w.rotation).scale(VOX, VOX * (flat ? 0.9f : 1f), VOX).translate(-wm.sx / 2f, -half - 0.5f, -half - 0.5f);
            ModelRenderer.Mesh mw = mr.cached(key + (flat ? ":wf" : ":w"));
            if (mw == null) mw = mr.meshAt(key + (flat ? ":wf" : ":w"), wm, 0, 0, 0);
            mr.draw(mw, m, light, 0xFFFFFFFF, 0);
        }
        mr.material();
        // lamps that are lit are small emitters on the model; the real light they cast is added by the vehicle system
        if (car.lightsOn) {
            int hw = vx(d.width / 2), hl = vx(d.length / 2);
            float ly = (Math.max(2, vx(d.wheelRadius * 0.75f)) + Math.round((vx(d.height) - Math.max(2, vx(d.wheelRadius * 0.75f))) * 0.5f) - 4) * VOX;
            mr.flags = DrawList.NO_SHADOW;
            for (int s = 0; s < 2; s++) {
                if (s == 0 ? !car.damage.lightL : !car.damage.lightR) continue;
                m.set(base).translate((s == 0 ? -1 : 1) * (hw - 2) * VOX, ly, hl * VOX + 0.02f).scale(0.28f, 0.22f, 0.04f).translate(0, 0, 0);
                mr.cube(m, 0xFFFFF4D0, 1, 1);
            }
            for (int s = 0; s < 2; s++) { m.set(base).translate((s == 0 ? -1 : 1) * (hw - 2) * VOX, ly + 0.1f, -hl * VOX - 0.02f).scale(0.28f, 0.2f, 0.04f); mr.cube(m, car.input.brake > 0 ? 0xFFFF3020 : 0xFF901810, 1, car.input.brake > 0 ? 1 : 0.6f); }
            mr.flags = 0;
        }
    }

    /** Releases the cached meshes (the car left the world). */
    public void dispose(ModelRenderer mr) { for (String s : new String[]{":b", ":g", ":d", ":w", ":wf"}) mr.drop(key + s); }

    /** Flat-shaded preview model for menus (shop showcase, deployment screen). */
    public VoxModel preview() { if (body == null) build(); return body; }
}
