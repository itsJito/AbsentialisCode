package absentialis.world.gen;

import static absentialis.world.gen.Gen.G;

import absentialis.engine.util.Rng;
import absentialis.world.Block;
import absentialis.world.MapData;
import absentialis.world.gen.GenDefs.DistrictDef;
import absentialis.world.gen.WorldPlan.LotPlan;
import absentialis.world.gen.arch.Architect;

/**
 * Realises an ordinary lot of the plan (pipeline stages 7 to 9): its ground, its supporting building, the walk
 * from the door to the street, and the yard that says what kind of place it is - a fenced garden, a loading
 * apron, a paddock, a car park. Yard dressing is cover and character; it is placed only on free cells of the lot
 * and never on a door's approach or a path.
 */
public final class LotBuilder {
    private final Gen g;
    private final Architect architect;

    public LotBuilder(Gen g, Architect architect) { this.g = g; this.architect = architect; }

    public MapData.Building build(LotPlan lp) {
        DistrictDef def = g.defs.of(lp.district);
        Rng r = new Rng(lp.seed);
        Site s = new Site(g, architect, lp.rect, lp.front, lp.district, def, r);
        s.begin();
        s.ground(groundFor(lp.yard, def));
        MapData.Building b = null;
        if (lp.foot != null) {
            int[] lo = s.local(lp.foot[0], lp.foot[1]), hi = s.local(lp.foot[2], lp.foot[3]);
            b = s.building(lp.type, Math.min(lo[0], hi[0]), Math.min(lo[1], hi[1]), Math.max(lo[0], hi[0]), Math.max(lo[1], hi[1]), 0, lp.floors, "", "");
            s.walkToStreet(b, walkFor(lp.yard));
        }
        yard(s, lp, b, r);
        s.end();
        return b;
    }

    private static int groundFor(String yard, DistrictDef def) {
        return switch (yard) {
            case "fenced", "verge", "wild", "paddock", "park" -> Block.GRASS;
            case "farmyard", "field" -> Block.DIRT;
            case "drive", "gravel", "substation" -> Block.GRAVEL;
            case "loading", "service", "loading_yard", "checkpoint" -> Block.CONCRETE_FLOOR;
            case "parking", "motor_yard" -> Block.ASPHALT;
            default -> "grass".equals(def.ground) ? Block.GRASS : "concrete".equals(def.ground) ? Block.CONCRETE_FLOOR : Block.SIDEWALK;
        };
    }

    private static int walkFor(String yard) {
        return switch (yard) { case "fenced", "wild", "verge" -> Block.COBBLE; case "farmyard", "paddock" -> Block.GRAVEL; default -> 0; };
    }

    /** The lot's back edge, the far a end and whether a cell is clear of the building by at least one cell. */
    private void yard(Site s, LotPlan lp, MapData.Building b, Rng r) {
        int A = s.A, B = s.B;
        switch (lp.yard) {
            case "fenced" -> {
                // a garden behind a low fence: gate on the walk, a second gate onto the alley behind
                int ga = b != null ? s.local(b.entryX, b.entryZ)[0] : A / 2;
                s.enclose(0, 0, A - 1, B - 1, r.chance(0.5) ? Block.HEDGE : Block.IRON_FENCE, 1, 0, new int[]{ga, 0, ga, 0}, new int[]{A / 2, B - 1, A / 2, B - 1}, new int[]{A - 1, 1, A - 1, B - 2});
                s.path(A / 2, B - 1, A / 2, B - 3, 0, 1);
                s.tree(3 + r.nextInt(Math.max(1, A - 6)), B - 5, 5 + r.nextInt(2), r.chance(0.25));
                if (r.chance(0.5)) s.prop(A - 3, B - 3, Block.BARREL);
                if (r.chance(0.6)) s.crate(2, B - 3, Block.TRASH, "trash");
                for (int k = 0; k < 4; k++) { int a = 1 + r.nextInt(A - 2), bb = 1 + r.nextInt(B - 2); if (s.open(a, bb) && s.get(a, bb, G - 1) == Block.GRASS && r.chance(0.6)) s.set(a, bb, G, Block.TALL_GRASS); }
            }
            case "drive" -> { s.prop(1, B - 2, Block.TIRE); s.crate(A - 2, B - 2, Block.TRASH, "trash"); }
            case "paved" -> {
                // a paved forecourt: a bin by the door, planters, a service yard with a dumpster behind
                s.crate(1, 0, Block.TRASH, "trash");
                if (A > 12) { s.prop(2, 1, Block.HEDGE); s.prop(A - 3, 1, Block.HEDGE); }
                s.crate(1, B - 2, Block.DUMPSTER, "trash");
                if (r.chance(0.5)) s.prop(A - 2, B - 2, Block.BARREL);
                if (r.chance(0.4)) s.crate(A - 3, B - 2, Block.CRATE, "crate");
            }
            case "service" -> { s.crate(1, B - 2, Block.DUMPSTER, "trash"); s.prop(A - 2, B - 2, Block.BARREL); s.prop(A - 2, B - 3, Block.BARREL); s.crate(2, B - 2, Block.CRATE, "industrial"); }
            case "loading" -> {
                // a loading apron: hazard-striped bay line, stacked crates and drums along the fence, a chain-link boundary at the back
                s.enclose(0, B - 1, A - 1, B - 1, Block.CHAINLINK, 2, 0, new int[]{A / 2, B - 1, A / 2 + 1, B - 1});
                for (int a = 2; a < A - 2; a += 3) if (s.open(a, 1) && !s.kept(a, 1)) s.set(a, 1, G - 1, Block.HAZARD_STRIPE);
                for (int k = 0; k < 5; k++) { int a = 1 + r.nextInt(A - 2); if (s.crate(a, B - 2, r.chance(0.6) ? Block.CRATE : Block.TOOLBOX, "industrial") && r.chance(0.4) && s.air(a, B - 2, G + 1)) s.set(a, B - 2, G + 1, Block.CRATE); }
                for (int k = 0; k < 3; k++) s.prop(1 + r.nextInt(A - 2), B - 3, Block.BARREL);
                s.crate(1, B - 2, Block.DUMPSTER, "trash");
            }
            case "substation" -> {
                // transformers in a fenced gravel compound; the breaker here restores the district's power
                int a0 = Math.max(1, A - 9), b0 = Math.max(1, B - 9);
                if (A >= 12 && B >= 12) {
                    s.enclose(a0, b0, A - 2, B - 2, Block.CHAINLINK, 2, Block.BARBED, new int[]{a0 + 2, b0, a0 + 2, b0});
                    for (int a = a0 + 2; a <= A - 4; a += 2) { s.prop(a, B - 4, Block.TRANSFORMER); s.set(a, B - 4, G + 1, Block.MACHINE); }
                    g.site("substation", s.x(a0 + 2, B - 4), G, s.z(a0 + 2, B - 4));
                    s.lampPost(a0 + 1, b0 + 1);
                }
            }
            case "checkpoint" -> {
                s.sandbags(1, 1, Math.min(5, A - 2), true);
                s.sandbags(1, B - 2, Math.min(5, A - 2), true);
                s.prop(A - 2, 1, Block.BARRIER); s.prop(A - 3, 1, Block.BARRIER);
                s.crate(A - 2, B - 2, Block.CRATE_MIL, "military");
                s.lampPost(A - 2, B / 2);
                if (b != null) s.post("Checkpoint", "checkpoint", s.def.faction, s.local(b.entryX, b.entryZ)[0], Math.max(0, s.local(b.entryX, b.entryZ)[1]), G);
            }
            case "farmyard" -> {
                s.enclose(0, 0, A - 1, B - 1, Block.WOOD_DARK, 1, 0, new int[]{b != null ? s.local(b.entryX, b.entryZ)[0] : A / 2, 0, b != null ? s.local(b.entryX, b.entryZ)[0] : A / 2, 0}, new int[]{A / 2, B - 1, A / 2 + 1, B - 1});
                s.path(A / 2, B - 1, A / 2, B - 3, 0, 2);
                s.tree(4, B - 6, 6, r.chance(0.3));
                for (int k = 0; k < 3; k++) s.prop(2 + r.nextInt(Math.max(1, A - 4)), B - 3, Block.HAY);
                s.crate(A - 3, B - 3, Block.CRATE, "farm");
                s.prop(A - 3, 2, Block.BARREL);
                // a kitchen garden in the back corner
                for (int a = 2; a < Math.min(A - 2, 9); a++) for (int bb = B - 9; bb < B - 5; bb++) if (s.open(a, bb) && (a & 1) == 0) s.set(a, bb, G, Block.CROP);
            }
            case "paddock" -> {
                s.enclose(0, 0, A - 1, B - 1, Block.WOOD_DARK, 1, 0, new int[]{A / 2, 0, A / 2 + 1, 0}, new int[]{A / 2, B - 1, A / 2 + 1, B - 1});
                s.path(A / 2, 0, A / 2, 2, 0, 2);
                for (int k = 0; k < 4; k++) s.prop(2 + r.nextInt(Math.max(1, A - 4)), B - 3 - r.nextInt(3), Block.HAY);
                s.prop(2, 2, Block.WATER_TANK);
                for (int k = 0; k < 10; k++) { int a = 1 + r.nextInt(A - 2), bb = 1 + r.nextInt(B - 2); if (s.open(a, bb)) s.set(a, bb, G, Block.TALL_GRASS); }
            }
            case "gravel" -> { s.crate(1, B - 2, Block.DUMPSTER, "trash"); s.prop(A - 2, B - 2, Block.BARREL); s.prop(2, 1, Block.TIRE); s.lampPost(A - 2, 1); }
            case "wild", "verge" -> {
                int trees = Math.max(1, A * B / 160);
                for (int k = 0; k < trees; k++) s.tree(3 + r.nextInt(Math.max(1, A - 6)), 3 + r.nextInt(Math.max(1, B - 6)), 5 + r.nextInt(3), r.chance(0.3));
                for (int k = 0; k < A * B / 12; k++) { int a = r.nextInt(A), bb = r.nextInt(B); if (s.open(a, bb) && s.get(a, bb, G - 1) == Block.GRASS) s.set(a, bb, G, Block.TALL_GRASS); }
            }
            default -> yardOnly(s, lp, r);
        }
    }

    /** Lots that carry no building: they are the district's open, navigable outdoor spaces. */
    private void yardOnly(Site s, LotPlan lp, Rng r) {
        int A = s.A, B = s.B;
        switch (lp.type) {
            case "parking" -> {
                s.ground(Block.ASPHALT);
                for (int a = 2; a < A - 2; a += 3) for (int b = 2; b < B - 2; b++) if (b < 7 || b > B - 8) s.set(a, b, G - 1, Block.ROAD_LINE);
                for (int a = 1; a < A - 1; a += 2) { s.prop(a, B / 2, Block.BARRIER); s.keep(a + 1, B / 2); }
                s.lampPost(1, 1); s.lampPost(A - 2, B - 2);
                s.crate(A - 2, 1, Block.TRASH, "trash");
            }
            case "park" -> {
                s.ground(Block.GRASS);
                s.path(A / 2, 0, A / 2, B - 1, Block.COBBLE, 2);
                s.path(0, B / 2, A - 1, B / 2, Block.COBBLE, 2);
                for (int k = 0; k < Math.max(2, A * B / 110); k++) s.tree(3 + r.nextInt(Math.max(1, A - 6)), 3 + r.nextInt(Math.max(1, B - 6)), 5 + r.nextInt(3), r.chance(0.2));
                s.prop(A / 2 - 2, B / 2 - 2, Block.PEW); s.prop(A / 2 + 3, B / 2 + 3, Block.PEW);
                s.crate(A / 2 + 3, B / 2 - 2, Block.TRASH, "trash");
                s.lampPost(A / 2 - 2, B / 2 + 3);
            }
            case "loading_yard" -> {
                // stacked shipping containers in ranks with lanes between: hard cover and sight lines
                s.ground(Block.CONCRETE_FLOOR);
                s.enclose(0, 0, A - 1, B - 1, Block.CHAINLINK, 2, 0, new int[]{A / 2 - 1, 0, A / 2 + 1, 0}, new int[]{A / 2 - 1, B - 1, A / 2 + 1, B - 1});
                s.path(A / 2, 0, A / 2, B - 1, 0, 2);
                int[] cols = {Block.CONTAINER_RED, Block.CONTAINER_BLUE, Block.CONTAINER_GREEN, Block.CONTAINER_ORANGE};
                for (int b = 3; b + 3 < B - 2; b += 7) for (int side = 0; side < 2; side++) {
                    int a0 = side == 0 ? 2 : A / 2 + 3, a1 = side == 0 ? A / 2 - 3 : A - 3;
                    if (a1 - a0 < 4) continue;
                    int h = 1 + r.nextInt(2), col = r.pick(cols);
                    for (int a = a0; a <= a1; a++) for (int bb = b; bb < b + 3; bb++) if (!s.kept(a, bb)) for (int y = G; y < G + h * 3; y++) s.set(a, bb, y, y - G >= 3 ? cols[(a0 + b) & 3] : col);
                }
                s.crate(2, B - 3, Block.CRATE, "industrial"); s.prop(A - 3, B - 3, Block.BARREL);
            }
            case "motor_yard" -> {
                s.ground(Block.ASPHALT);
                s.enclose(0, 0, A - 1, B - 1, Block.CHAINLINK, 2, Block.BARBED, new int[]{A / 2 - 1, 0, A / 2 + 1, 0});
                s.path(A / 2, 0, A / 2, 3, 0, 2);
                for (int a = 3; a < A - 3; a += 4) { s.set(a, B - 4, G - 1, Block.ROAD_LINE); s.set(a, B - 5, G - 1, Block.ROAD_LINE); }
                s.crate(2, B - 2, Block.CRATE_MIL, "military"); s.crate(3, B - 2, Block.TOOLBOX, "industrial");
                s.prop(A - 3, B - 2, Block.BARREL); s.prop(A - 4, B - 2, Block.BARREL); s.prop(A - 3, 2, Block.TIRE);
                s.sandbags(2, 2, Math.min(4, A - 4), true);
                s.lampPost(1, B - 2);
            }
            case "field" -> {
                // a fenced field in rows, with a headland left round it to walk and a gate on each end
                s.ground(Block.DIRT);
                s.enclose(0, 0, A - 1, B - 1, Block.WOOD_DARK, 1, 0, new int[]{A / 2, 0, A / 2 + 1, 0}, new int[]{A / 2, B - 1, A / 2 + 1, B - 1});
                boolean stubble = r.chance(0.3);
                for (int a = 2; a < A - 2; a++) for (int b = 2; b < B - 2; b++) {
                    if (a % 3 == 0) continue;
                    if (b == B / 2) continue;                              // the cross path
                    if (!stubble && r.chance(0.82)) s.set(a, b, G, Block.CROP);
                }
                s.prop(A / 2 + 3, B / 2, Block.HAY);
                s.crate(2, 1, Block.CRATE, "farm");
            }
            default -> { }
        }
    }
}
