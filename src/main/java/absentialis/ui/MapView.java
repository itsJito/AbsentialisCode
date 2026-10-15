package absentialis.ui;

import static org.lwjgl.opengl.GL11.*;

import absentialis.engine.render.Tex;
import absentialis.engine.util.Rng;
import absentialis.persistence.Profile;
import absentialis.sim.ZoneState;
import absentialis.world.Block;
import absentialis.world.District;
import absentialis.world.MapData;
import absentialis.world.Material;
import absentialis.world.World;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/**
 * Hand-drawn field map (addendum P2.4): the real city rendered as ink on parchment, feathered
 * fog of war over unreconnoitred cells, faction crosshatching by district control, extraction
 * stamps and landmark labels. Only what the survivor has seen (or been told) is drawn.
 */
public final class MapView {
    private int baseTex, hatchTex, fogTex;
    private World world;
    private final ByteBuffer fog = MemoryUtil.memAlloc(Profile.MAP_CELLS * Profile.MAP_CELLS);

    public static final int PAPER = Tex.rgb(214, 198, 160), INK = Tex.rgb(58, 44, 30);

    public void build(World w) {
        world = w;
        int N = World.W;
        int[] img = new int[N * N];
        for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) {
            int h = w.heightAt(x, z);
            int top = w.get(x, h, z);
            Block b = Block.ALL[top];
            float n = Rng.hash(x, 0, z, 5);
            int paper = Tex.shade(PAPER, 0.94f + n * 0.08f);
            int c = paper;
            District d = w.district(x, z);
            if (b.is(Block.LIQUID) || w.get(x, h + 1, z) == Block.WATER) c = Tex.mix(paper, Tex.rgb(90, 120, 130), 0.45f);
            else if (h > World.GROUND + 2) {
                // buildings: ink wash darker with height + diagonal hatch
                float t = Math.min(1, (h - World.GROUND) / 40f);
                c = Tex.mix(paper, Tex.rgb(104, 80, 56), 0.45f + t * 0.35f);
                if ((x + z) % 4 == 0) c = Tex.shade(c, 0.85f);
            } else if (Block.trunk(b.id)) c = Tex.mix(paper, Tex.rgb(214, 150, 40), 0.85f);   // the ring road reads at a glance
            else if (b.mat == Material.ASPHALT) c = Tex.mix(paper, Tex.rgb(170, 160, 140), 0.25f);
            else if (b.mat == Material.GRASS || b.mat == Material.FOLIAGE || b.mat == Material.DIRT) {
                c = Tex.mix(paper, Tex.rgb(130, 140, 90), d == District.WILDERNESS ? 0.3f : 0.2f);
                if (n > 0.93f) c = Tex.mix(c, INK, 0.4f); // stipple
            }
            if (d == District.WILDERNESS && World.outsideDistance(x, z) > 0) c = Tex.mix(c, Tex.rgb(150, 160, 110), Math.min(0.35f, World.outsideDistance(x, z) / 200f));
            img[z * N + x] = c;
        }
        // ink outlines where height steps (building edges, walls)
        int[] out = img.clone();
        for (int x = 1; x < N - 1; x++) for (int z = 1; z < N - 1; z++) {
            int h = w.heightAt(x, z);
            int dh = Math.max(Math.abs(h - w.heightAt(x + 1, z)), Math.abs(h - w.heightAt(x, z + 1)));
            if (dh >= 3) out[z * N + x] = Tex.mix(img[z * N + x], INK, 0.75f);
            else if (dh >= 1 && h > World.GROUND + 2) out[z * N + x] = Tex.mix(img[z * N + x], INK, 0.3f);
        }
        // district borders as dashed ink
        for (int x = 1; x < N; x++) for (int z = 1; z < N; z++)
            if (w.district(x, z) != w.district(x - 1, z) || w.district(x, z) != w.district(x, z - 1)) if (((x + z) / 3) % 2 == 0) out[z * N + x] = Tex.mix(out[z * N + x], Tex.rgb(140, 40, 30), 0.7f);
        baseTex = upload(out, N, N, GL_RGBA, GL_NEAREST);
        fogTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, fogTex);
        glTexImage2D(GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL30.GL_R8, Profile.MAP_CELLS, Profile.MAP_CELLS, 0, GL_RED, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
    }

    public boolean ready() { return baseTex != 0; }

    private static final int[] FACTION_INK = {Tex.rgb(70, 90, 50), Tex.rgb(150, 50, 30), Tex.rgb(40, 80, 140), Tex.rgb(110, 20, 60)};

    /** Re-hatches districts by their controlling faction (call when zone state changes). */
    public void rebuildHatch(ZoneState z) {
        int N = World.W / 2;
        int[] img = new int[N * N];
        for (int x = 0; x < N; x++) for (int y = 0; y < N; y++) {
            District d = world.district(x * 2, y * 2);
            String c = z.controller(d);
            if (c == null || d == District.WILDERNESS) continue;
            int fi = ZoneState.fi(c);
            float strength = z.control(d, c);
            int period = fi % 2 == 0 ? 7 : 6;
            boolean line = fi < 2 ? (x + y) % period == 0 : (x - y + 1000) % period == 0;
            if (line) img[y * N + x] = (((int) (90 + strength * 120)) << 24) | (FACTION_INK[fi] & 0xFFFFFF);
        }
        if (hatchTex != 0) glDeleteTextures(hatchTex);
        hatchTex = upload(img, N, N, GL_RGBA, GL_NEAREST);
    }

    private static int upload(int[] px, int w, int h, int fmt, int filter) {
        ByteBuffer b = MemoryUtil.memAlloc(w * h * 4);
        for (int c : px) b.put((byte) Tex.r(c)).put((byte) Tex.g(c)).put((byte) Tex.b(c)).put((byte) Tex.a(c));
        b.flip();
        int t = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, t);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, fmt, GL_UNSIGNED_BYTE, b);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        // 0.9: the map is one sheet - it never tiles (the default wrap repeated it past its edges when zoomed out)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, 0x812F /* GL_CLAMP_TO_EDGE */);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, 0x812F);
        MemoryUtil.memFree(b);
        return t;
    }

    private void uploadFog(Profile p) {
        fog.clear();
        int n = Profile.MAP_CELLS;
        for (int z = 0; z < n; z++) for (int x = 0; x < n; x++) fog.put((byte) (p.discovered(x, z) ? 0 : 255));
        fog.flip();
        glBindTexture(GL_TEXTURE_2D, fogTex);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, n, n, GL_RED, GL_UNSIGNED_BYTE, fog);
    }

    /**
     * Draws the map region [wx0,wz0]-[wx0+span] into the screen rect.
     * exits: optional raid exit states (eligibility colouring); px/pz/pyaw: player marker (NaN = none).
     */
    public void draw(Ui ui, Profile p, float sx, float sy, float sw, float sh, float wx0, float wz0, float span,
                     java.util.List<absentialis.scene.Raid.ExitState> exits, float px, float pz, float pyaw, boolean labels) {
        if (!ready()) return;
        uploadFog(p);
        var b = ui.b;
        b.scissor((int) sx, (int) sy, (int) sw, (int) sh);
        // the table the map lies on: whatever of the view is beyond the edge of the sheet
        b.rect(sx, sy, sw, sh, 0xFF14100C);
        // 0.9: only the part of the view that is on the sheet is drawn from it. Zoomed out past the edge of the world the
        // sheet used to be sampled outside 0..1 and came round again; now there is the map, once, and the table round it
        final float fullX = sx, fullY = sy, fullW = sw, fullH = sh, k0 = sw / span;
        float cx0 = Math.max(0, wx0), cz0 = Math.max(0, wz0), cx1 = Math.min(World.W, wx0 + span), cz1 = Math.min(World.D, wz0 + span * (sh / sw));
        if (cx1 <= cx0 || cz1 <= cz0) { b.noScissor(); return; }
        float u0 = cx0 / World.W, v0 = cz0 / World.D, u1 = cx1 / World.W, v1 = cz1 / World.D;
        float qx = fullX + (cx0 - wx0) * k0, qy = fullY + (cz0 - wz0) * k0, qw = (cx1 - cx0) * k0, qh = (cz1 - cz0) * k0;
        b.rect(qx - 2, qy - 2, qw + 4, qh + 4, 0xFF3A2C1E);
        b.rect(qx, qy, qw, qh, 0xFF2A2218);
        b.quad(baseTex, qx, qy, qw, qh, u0, v0, u1, v1, 1, 1, 1, 1, 0);
        // fog: mode 1 = alpha from the red channel, feathered by linear filtering; territory hatching
        // is common knowledge (radio, word of mouth) so it is drawn over the fog
        b.quad(fogTex, qx, qy, qw, qh, u0, v0, u1, v1, 0.30f, 0.25f, 0.18f, 0.80f, 1);
        if (hatchTex != 0) b.quad(hatchTex, qx, qy, qw, qh, u0, v0, u1, v1, 1, 1, 1, 0.85f, 0);
        float k = sw / span;
        // landmarks & sites (only where reconnoitred)
        if (labels) for (MapData.Point lm : world.map.landmarks) {
            if (!p.discovered(lm.x / Profile.MAP_CELL, lm.z / Profile.MAP_CELL)) continue;
            float mx = sx + (lm.x - wx0) * k, my = sy + (lm.z - wz0) * k;
            if (mx < sx || my < sy || mx > sx + sw || my > sy + sh) continue;
            ui.b.rect(mx - 2, my - 2, 4, 4, 0xFF3A2C1E);
            ui.font.draw(b, lm.name, mx + 5, my - 7, 12 * ui.scale, 0xFF3A2C1E);
        }
        // extraction stamps
        int radio = p.level("radio");
        for (MapData.Exit e : world.map.exits) {
            boolean known = p.discovered(e.x / Profile.MAP_CELL, e.z / Profile.MAP_CELL) || radio >= 1 || e.type == MapData.ExitType.PERMANENT;
            if (!known) continue;
            float mx = sx + (e.x - wx0) * k, my = sy + (e.z - wz0) * k;
            if (mx < sx - 20 || my < sy - 20 || mx > sx + sw + 20 || my > sy + sh + 20) continue;
            int col = 0xFFB07A20;
            if (exits != null) for (var st : exits) if (st.exit == e) col = st.eligible ? 0xFF3A8A3A : 0xFFA03020;
            int g = Math.max(1, Math.round(ui.scale * (labels ? 2 : 1)));
            ui.glyph("stamp", mx - 6 * g, my - 6 * g, g, col);
            if (labels) ui.font.draw(b, e.name, mx + 8 * g, my - 6, 12 * ui.scale, col);
        }
        // known nests (radio level 2 intel)
        if (radio >= 2) for (MapData.Point n : world.map.nests) {
            float mx = sx + (n.x - wx0) * k, my = sy + (n.z - wz0) * k;
            if (mx > sx && my > sy && mx < sx + sw && my < sy + sh) ui.glyph("skull", mx - 6, my - 6, 1, 0xC0702020);
        }
        if (!Float.isNaN(px)) {
            float mx = sx + (px - wx0) * k, my = sy + (pz - wz0) * k;
            float fx = (float) -Math.sin(pyaw), fz = (float) -Math.cos(pyaw);
            b.line(mx, my, mx + fx * 14 * ui.scale, my + fz * 14 * ui.scale, 2, 0xFFE2A034);
            int g = Math.max(1, Math.round(ui.scale));
            ui.glyph("you", mx - 6 * g, my - 6 * g, g, 0xFFFFFFFF);
        }
        b.noScissor();
    }

    /** Full-screen style map with a legend; hover reads district intel. */
    public void legend(Ui ui, Profile p, float x, float y) {
        ui.text("LEGEND", x, y, 14, Ui.TXT_HI);
        String[] names = {"Military", "Bandits", "Loners", "Mercenaries"};   // (0.9: the names the factions have had since 0.8 - this legend still said Raiders, Survivors and Cultists)
        for (int i = 0; i < 4; i++) {
            float ly = y + ui.s(22) + i * ui.s(20);
            for (int k = 0; k < 4; k++) ui.b.line(x + k * 5, ly + 14, x + k * 5 + 10, ly + 2, 2, 0xFF000000 | FACTION_INK[i]);
            ui.text(names[i] + " control", x + ui.s(28), ly, 13, Ui.TXT);
        }
        float ly = y + ui.s(110);
        ui.glyph("stamp", x, ly, 1, 0xFF3A8A3A); ui.text("Extraction (open)", x + ui.s(28), ly - 2, 13, Ui.TXT);
        ui.glyph("stamp", x, ly + ui.s(20), 1, 0xFFA03020); ui.text("Extraction (closed / conditions)", x + ui.s(28), ly + ui.s(18), 13, Ui.TXT);
        ui.text("Fogged areas are unreconnoitred.", x, ly + ui.s(46), 12, Ui.TXT_DIM);
    }
}
