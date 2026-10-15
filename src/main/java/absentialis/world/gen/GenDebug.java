package absentialis.world.gen;

import static absentialis.world.gen.Gen.G;

import absentialis.world.Block;
import absentialis.world.District;
import absentialis.world.MapData;
import absentialis.world.World;
import absentialis.world.gen.Streets.Rect;
import absentialis.world.gen.WorldPlan.LotPlan;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import javax.imageio.ImageIO;

/**
 * Developer views of the generated world (brief 1.4 and 15): one PNG per layer, written when {@code ABS_GENDUMP}
 * names a directory, and the same layers as colour functions for the in-game map overlay ({@code gen <layer>} in
 * the console). Layers: plan (districts, reserved roads, lots), footprints (valid / repaired / rejected),
 * rooms (room graph: nodes by purpose, doorways, links), nav (connected walkable regions at street level),
 * invalid (everything the validator flagged), materials (floor and wall finish per room), cover.
 */
public final class GenDebug {
    private GenDebug() {}

    public static final String[] LAYERS = {"plan", "footprints", "rooms", "nav", "invalid", "materials", "cover"};
    private static final int S = 2;

    private static final int[] DISTRICT = {0x6E8B5A, 0x7C8FB8, 0xB08A5A, 0xC9A66B, 0x8FA070, 0x9AA7B5};

    public static int districtColor(District d) { return DISTRICT[d.ordinal()]; }

    private static int typeColor(String type) {
        int h = type.hashCode() * 0x9E3779B1;
        return 0x404040 | (h >>> 8 & 0xBFBFBF);
    }

    /** Colour of one column for a layer (0 = draw nothing): shared by the PNG dump and the in-game overlay. */
    public static int color(World w, WorldPlan plan, Streets streets, String layer, int x, int z) {
        MapData m = w.map;
        switch (layer) {
            case "plan": {
                if (plan == null) return 0;
                int res = plan.reserved(x, z);
                int base = districtColor(w.district(x, z));
                if (res == WorldPlan.RES_ROAD) return streets != null && streets.isCarriage(x, z) ? 0x303236 : 0x8C8C88;
                if (res == WorldPlan.RES_ALLEY) return 0xD8C8A0;
                if (res == WorldPlan.RES_LANDMARK) return mix(base, 0xFFD040, 0.45f);
                if (res == WorldPlan.RES_LOT) return mix(base, 0xFFFFFF, 0.25f);
                return World.outsideDistance(x, z) > 0 ? mix(base, 0x000000, 0.35f) : base;
            }
            case "footprints": {
                MapData.Building b = m.buildingAt(x, z);
                if (b != null) return !b.valid ? 0xD03030 : b.repaired ? 0xE0C030 : b.landmark.isEmpty() ? 0x4FA860 : 0x3C7FD0;
                if (plan != null && plan.reserved(x, z) == WorldPlan.RES_ROAD) return 0x3A3C40;
                if (plan != null && plan.reserved(x, z) == WorldPlan.RES_ALLEY) return 0x7A705C;
                return World.outsideDistance(x, z) > 0 ? 0x1E241C : 0x55594E;
            }
            case "rooms": case "materials": {
                MapData.Building b = m.buildingAt(x, z);
                if (b == null || !b.valid) return plan != null && plan.reserved(x, z) == WorldPlan.RES_ROAD ? 0x2A2C30 : 0x15171A;
                for (MapData.Room room : b.rooms) if (room.floor == 0 && room.contains(x, z)) {
                    if (layer.equals("materials")) { int id = w.get(x, room.y, z); absentialis.world.Material mt = Block.ALL[id].mat; return mt.r << 16 | mt.g << 8 | mt.b; }
                    return room.reachable ? typeColor(room.type) : 0xFF2020;
                }
                int id = w.get(x, G + 1, z);
                if (layer.equals("materials") && id != 0) { absentialis.world.Material mt = Block.ALL[id].mat; return mix(mt.r << 16 | mt.g << 8 | mt.b, 0, 0.35f); }
                return id != 0 && Block.ALL[id].is(Block.DOOR) ? 0xFFFFFF : 0x000000;
            }
            case "invalid": {
                MapData.Building b = m.buildingAt(x, z);
                if (b != null) return b.valid ? 0x30343A : 0x802020;
                return plan != null && plan.reserved(x, z) == WorldPlan.RES_ROAD ? 0x22242A : 0x101214;
            }
            default: return 0;
        }
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    public static void dump(World w, WorldPlan plan, Streets streets, String dir) {
        try {
            new File(dir).mkdirs();
            for (String layer : new String[]{"plan", "footprints", "rooms", "invalid", "materials"}) {
                BufferedImage img = new BufferedImage(World.W * S, World.D * S, BufferedImage.TYPE_INT_RGB);
                for (int x = 0; x < World.W; x++) for (int z = 0; z < World.D; z++) {
                    int c = color(w, plan, streets, layer, x, z);
                    for (int i = 0; i < S; i++) for (int k = 0; k < S; k++) img.setRGB(x * S + i, z * S + k, c);
                }
                Graphics2D gr = img.createGraphics();
                overlay(gr, w, plan, layer);
                gr.dispose();
                ImageIO.write(img, "png", new File(dir, layer + ".png"));
            }
            // ABS_GENCROP="x0,z0,x1,z1[;...]": the room-graph layer again for those rectangles, eight pixels to the block
            String crops = System.getenv("ABS_GENCROP");
            if (crops != null && !crops.isBlank()) {
                int n = 0;
                for (String c : crops.split(";")) {
                    String[] v = c.trim().split(",");
                    int x0 = Integer.parseInt(v[0].trim()), z0 = Integer.parseInt(v[1].trim()), x1 = Integer.parseInt(v[2].trim()), z1 = Integer.parseInt(v[3].trim()), k = 8;
                    BufferedImage full = new BufferedImage(World.W * S, World.D * S, BufferedImage.TYPE_INT_RGB);
                    for (int x = x0; x < x1; x++) for (int z = z0; z < z1; z++) { int col = color(w, plan, streets, "rooms", x, z); for (int i = 0; i < S; i++) for (int j = 0; j < S; j++) full.setRGB(x * S + i, z * S + j, col); }
                    Graphics2D gr = full.createGraphics();
                    overlay(gr, w, plan, "rooms");
                    gr.dispose();
                    BufferedImage out = new BufferedImage((x1 - x0) * k, (z1 - z0) * k, BufferedImage.TYPE_INT_RGB);
                    Graphics2D go = out.createGraphics();
                    go.drawImage(full.getSubimage(x0 * S, z0 * S, (x1 - x0) * S, (z1 - z0) * S), 0, 0, out.getWidth(), out.getHeight(), null);
                    // furniture and fixtures at standing height, so the dressing can be read against the plan
                    for (int x = x0; x < x1; x++) for (int z = z0; z < z1; z++) {
                        int id = w.get(x, G, z);
                        if (id == 0) continue;
                        Block bl = Block.ALL[id];
                        if (bl.is(Block.DOOR)) continue;
                        boolean furniture = bl.is(Block.CONTAINER) || bl.shape != 0 || Validation.prop(id) || id == Block.COUNTER || id == Block.STOVE || id == Block.SINK || id == Block.HAY;
                        go.setColor(furniture ? new Color(20, 20, 20) : new Color(90, 90, 96));
                        if (furniture) go.fillRect((x - x0) * k + 2, (z - z0) * k + 2, k - 4, k - 4); else if (w.map.buildingAt(x, z) == null) go.drawRect((x - x0) * k + 1, (z - z0) * k + 1, k - 3, k - 3);
                    }
                    go.dispose();
                    ImageIO.write(out, "png", new File(dir, "crop" + (n++) + ".png"));
                }
            }
            ImageIO.write(nav(w), "png", new File(dir, "nav.png"));
            ImageIO.write(surface(w), "png", new File(dir, "surface.png"));
            System.out.println("[Gen] Debug layers written to " + new File(dir).getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[Gen] Could not write debug layers: " + e);
        }
    }

    private static void overlay(Graphics2D gr, World w, WorldPlan plan, String layer) {
        MapData m = w.map;
        gr.setStroke(new BasicStroke(1f));
        if (layer.equals("plan") && plan != null) {
            gr.setColor(new Color(0, 0, 0, 150));
            for (LotPlan lp : plan.lots) { Rect r = lp.rect; gr.drawRect(r.x0() * S, r.z0() * S, r.w() * S - 1, r.d() * S - 1); }
            gr.setColor(Color.WHITE);
            for (LotPlan lp : plan.lots) if (lp.foot != null) gr.drawRect(lp.foot[0] * S, lp.foot[1] * S, (lp.foot[2] - lp.foot[0] + 1) * S - 1, (lp.foot[3] - lp.foot[1] + 1) * S - 1);
            gr.setColor(Color.BLACK);
            for (MapData.Landmark l : m.anchors) gr.drawString((l.headquarters ? "HQ " : "") + l.name + " [" + l.faction + "]", l.x0 * S + 4, l.cz() * S);
            for (WorldPlan.Region rg : plan.regions) gr.drawString(rg.district.display.toUpperCase(), rg.rect.x0() * S + 6, rg.rect.z0() * S + 14);
            gr.setColor(new Color(255, 60, 200));
            for (MapData.Exit e : m.exits) { gr.fillOval(e.x * S - 4, e.z * S - 4, 9, 9); gr.drawString(e.id, e.x * S + 6, e.z * S + 4); }
            gr.setColor(new Color(40, 220, 255));
            for (MapData.Point p : m.insertions) gr.fillRect(p.x * S - 3, p.z * S - 3, 7, 7);
        }
        if (layer.equals("rooms")) {
            // the room graph: a line for every doorway between two rooms of the ground floor
            for (MapData.Building b : m.buildings) {
                if (!b.valid) continue;
                gr.setColor(new Color(255, 255, 255, 200));
                for (MapData.Doorway d : b.doors) {
                    if (d.y != G) continue;
                    MapData.Room ra = d.roomA >= 0 && d.roomA < b.rooms.size() ? b.rooms.get(d.roomA) : null, rb = d.roomB >= 0 && d.roomB < b.rooms.size() ? b.rooms.get(d.roomB) : null;
                    if (ra != null) gr.drawLine(ra.cx() * S + 1, ra.cz() * S + 1, d.x * S + 1, d.z * S + 1);
                    if (rb != null) gr.drawLine(rb.cx() * S + 1, rb.cz() * S + 1, d.x * S + 1, d.z * S + 1);
                }
                gr.setColor(Color.YELLOW);
                for (MapData.Doorway d : b.doors) if (d.y == G) gr.fillRect(d.x * S, d.z * S, S, S);
                gr.setColor(new Color(60, 255, 90));
                gr.fillRect(b.entryX * S, b.entryZ * S, S, S);
            }
        }
        if (layer.equals("invalid") && m.report != null) {
            for (int[] f : m.report.flagged) {
                gr.setColor(switch (f[3]) { case GenValidator.F_ROAD -> Color.ORANGE; case GenValidator.F_ROOM -> Color.RED; case GenValidator.F_DOOR -> Color.MAGENTA; case GenValidator.F_FLOAT -> Color.CYAN; case GenValidator.F_OVERLAP -> Color.WHITE; case GenValidator.F_MATERIAL -> Color.YELLOW; default -> Color.PINK; });
                gr.fillRect(f[0] * S - 1, f[2] * S - 1, S + 2, S + 2);
            }
        }
    }

    /** Connected walkable regions at street level: the largest in green, every island in its own colour. */
    public static BufferedImage nav(World w) {
        BufferedImage img = new BufferedImage(World.W * S, World.D * S, BufferedImage.TYPE_INT_RGB);
        int[] region = new int[World.W * World.D];
        int[] sizes = new int[1 << 16];
        int next = 1;
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int x = World.CITY_MIN; x < World.CITY_MAX; x++) for (int z = World.CITY_MIN; z < World.CITY_MAX; z++) {
            if (region[x * World.D + z] != 0 || !w.nav.stand(x, G, z, true)) continue;
            int id = next++;
            if (id >= sizes.length) break;
            region[x * World.D + z] = id;
            q.add(new int[]{x, z});
            while (!q.isEmpty()) {
                int[] c = q.poll();
                sizes[id]++;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = c[0] + d[0], nz = c[1] + d[1];
                    if (nx < World.CITY_MIN || nz < World.CITY_MIN || nx >= World.CITY_MAX || nz >= World.CITY_MAX || region[nx * World.D + nz] != 0 || !w.nav.stand(nx, G, nz, true)) continue;
                    region[nx * World.D + nz] = id;
                    q.add(new int[]{nx, nz});
                }
            }
        }
        int big = 1;
        for (int i = 1; i < next && i < sizes.length; i++) if (sizes[i] > sizes[big]) big = i;
        int islands = 0, cells = 0;
        for (int i = 1; i < next && i < sizes.length; i++) if (i != big && sizes[i] >= 4) { islands++; cells += sizes[i]; }
        if (w.map.report != null) { w.map.report.navIslands = islands; w.map.report.navIslandCells = cells; }
        for (int x = 0; x < World.W; x++) for (int z = 0; z < World.D; z++) {
            int id = region[x * World.D + z];
            int c = id == 0 ? (w.solid(x, G, z) ? 0x1A1A1E : 0x08080A) : id == big ? 0x2E8B4E : 0xFF0000 | ((id * 97) & 255) << 8 | ((id * 57) & 255);
            for (int i = 0; i < S; i++) for (int k = 0; k < S; k++) img.setRGB(x * S + i, z * S + k, c);
        }
        return img;
    }

    /** Plain top-down picture of the world: the debris colour of the highest block of every column, shaded by height. */
    public static BufferedImage surface(World w) {
        BufferedImage img = new BufferedImage(World.W * S, World.D * S, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < World.W; x++) for (int z = 0; z < World.D; z++) {
            int y = World.H - 1;
            while (y > 0 && w.get(x, y, z) == 0) y--;
            absentialis.world.Material mt = Block.ALL[w.get(x, y, z)].mat;
            float sh = 0.55f + Math.min(0.45f, Math.max(0, y - 8) / 70f);
            int id = w.get(x, y, z);
            int r = mt.r, g = mt.g, b = mt.b;
            if (id == Block.ASPHALT || id == Block.RUNWAY || id == Block.ASPHALT_CRACKED) { r = 52; g = 54; b = 58; }
            if (id == Block.WATER) { r = 60; g = 110; b = 170; }
            if (y <= 0) { r = 255; g = 0; b = 255; }                           // a column with nothing in it would show magenta
            int c = (int) (r * sh) << 16 | (int) (g * sh) << 8 | (int) (b * sh);
            for (int i = 0; i < S; i++) for (int k = 0; k < S; k++) img.setRGB(x * S + i, z * S + k, c);
        }
        return img;
    }
}
