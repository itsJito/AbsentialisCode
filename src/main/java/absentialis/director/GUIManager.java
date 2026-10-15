package absentialis.director;

import static org.lwjgl.glfw.GLFW.*;

import absentialis.engine.Input;
import absentialis.ui.Ui;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * GUIManager: Director Mode's menus. One hierarchical menu system serves both interfaces - the casting screen and
 * the interaction menu - and has every component the design asks for: a title, a highlighted selection, a list that
 * scrolls, left/right value selectors, on/off toggles, nested submenus, options that are shown but disabled (with
 * the reason), confirmation prompts, and a line of navigation hints.
 *
 * Its behaviour: a value changes the moment it is adjusted (the row calls straight through to the manager that owns
 * the setting, and reads its label back from it, so a menu always shows what is actually in force); going back
 * returns to the row that was left; menus stay usable while the world runs; and input is ignored for a moment after
 * a transition ({@link #lock}).
 */
public final class GUIManager {
    /** One row of a menu. */
    public static final class Item {
        final String label;
        Supplier<String> value;                 // shown between < > when the row has a value
        IntConsumer adjust;                     // left (-1) / right (+1)
        Runnable select;                        // Enter
        Menu sub;                               // Enter opens it
        BooleanSupplier enabled = () -> true;
        Supplier<String> disabledWhy = () -> "";
        String confirm;                         // if set, Enter asks this first
        String hint = "";
        Item(String label) { this.label = label; }
        public Item value(Supplier<String> v, IntConsumer a) { value = v; adjust = a; return this; }
        public Item toggle(BooleanSupplier get, java.util.function.Consumer<Boolean> set) { value = () -> get.getAsBoolean() ? "On" : "Off"; adjust = d -> set.accept(!get.getAsBoolean()); select = () -> set.accept(!get.getAsBoolean()); return this; }
        public Item run(Runnable r) { select = r; return this; }
        public Item open(Menu m) { sub = m; return this; }
        public Item when(BooleanSupplier e, Supplier<String> why) { enabled = e; disabledWhy = why; return this; }
        public Item confirm(String question) { confirm = question; return this; }
        public Item hint(String h) { hint = h; return this; }
        public String label() { return label; }
    }

    /** A menu: a title and its rows, and the row that was last highlighted (kept when the menu is left and come back to). */
    public static final class Menu {
        public final String title;
        public final List<Item> items = new ArrayList<>();
        public int sel;
        /** Called when the highlight moves (the casting screen updates its preview from this). */
        public Runnable onHighlight;
        /** Rebuilds the rows each time the menu is opened (for lists whose content changes: the shortlist, the clips). */
        public Runnable onOpen;
        public Menu(String title) { this.title = title; }
        public Item add(String label) { Item i = new Item(label); items.add(i); return i; }
        public Item selected() { return items.isEmpty() ? null : items.get(Math.max(0, Math.min(items.size() - 1, sel))); }
    }

    private final List<Menu> stack = new ArrayList<>();
    private Item asking;                        // a confirmation is up for this row
    private boolean askYes;
    private float lock;                         // seconds for which input is ignored
    public Runnable sound = () -> { };
    public java.util.function.Consumer<String> sfx = s -> { };
    /** Rows visible at once before the list scrolls. */
    public static int VISIBLE = 11;

    // ---- 0.9: the mouse. Where the rows were last drawn (so a click knows what it is on), and where the pointer last was
    private float mX, mY, mW, mRowH, mRowsY, askX, askY, askW;
    private int mFirst, mShown;
    private double lastMouseX = -1, lastMouseY = -1;

    /** Says where the rows are without drawing them (tests: there is no screen). */
    public void layoutForTests(float x, float rowsY, float w, float rowH, int first, int shown) { mX = x; mRowsY = rowsY; mW = w; mRowH = rowH; mFirst = first; mShown = shown; }

    /** Counts the input lock down. Called every frame whether or not a menu is open (0.9: it used to count down only while a menu was being read, so the lock set on entering the scene never ran out and the actor could neither look nor move until a menu had been opened once). */
    public void tick(float dt) { if (lock > 0) lock = Math.max(0, lock - dt); }

    public boolean open() { return !stack.isEmpty(); }
    public Menu current() { return stack.isEmpty() ? null : stack.get(stack.size() - 1); }
    public int depth() { return stack.size(); }
    public boolean asking() { return asking != null; }

    /** Opens a menu as the root (anything already open is closed). */
    public void show(Menu root) { stack.clear(); push(root); }
    public void close() { stack.clear(); asking = null; }
    /** Input is ignored for this long: used across transitions (entering the world, switching actor, travelling). */
    public void lock(float seconds) { lock = Math.max(lock, seconds); }
    public boolean locked() { return lock > 0; }

    private void push(Menu m) {
        if (m.onOpen != null) m.onOpen.run();
        m.sel = Math.max(0, Math.min(m.items.size() - 1, m.sel));
        stack.add(m);
        if (m.onHighlight != null) m.onHighlight.run();
    }

    /** Back one level. Returns false if that closed the root (the caller decides what closing the root means). */
    public boolean back() {
        if (asking != null) { asking = null; return true; }
        if (stack.size() <= 1) return false;
        stack.remove(stack.size() - 1);
        Menu m = current();
        if (m.onHighlight != null) m.onHighlight.run();     // the row that was left is still the one highlighted
        return true;
    }

    private void move(Menu m, int d) {
        if (m.items.isEmpty()) return;
        m.sel = Math.floorMod(m.sel + d, m.items.size());
        sfx.accept("ui_click");
        if (m.onHighlight != null) m.onHighlight.run();
    }

    private void activate(Item it) {
        if (!it.enabled.getAsBoolean()) { sfx.accept("ui_invalid"); return; }
        if (it.sub != null) { sfx.accept("ui_tab"); push(it.sub); return; }
        if (it.select != null) { sfx.accept("ui_confirm"); it.select.run(); }
    }

    /**
     * Keys: Up/Down move, Left/Right adjust, Enter (or Space) selects, Backspace goes back. Returns one of
     * 0 nothing, 1 handled, 2 "back" was pressed at the root (the caller closes the menu or does nothing, as it sees fit).
     */
    public int input(Input in, float dt) {
        if (lock > 0) return 1;
        Menu m = current();
        if (m == null) return 0;
        int byMouse = mouse(in, m);
        if (byMouse != 0) return byMouse;
        boolean up = in.keyPressed(GLFW_KEY_UP) || in.keyPressed(GLFW_KEY_W), down = in.keyPressed(GLFW_KEY_DOWN) || in.keyPressed(GLFW_KEY_S);
        boolean left = in.keyPressed(GLFW_KEY_LEFT) || in.keyPressed(GLFW_KEY_A), right = in.keyPressed(GLFW_KEY_RIGHT) || in.keyPressed(GLFW_KEY_D);
        boolean enter = in.keyPressed(GLFW_KEY_ENTER) || in.keyPressed(GLFW_KEY_KP_ENTER) || in.keyPressed(GLFW_KEY_SPACE), backKey = in.keyPressed(GLFW_KEY_BACKSPACE);
        if (asking != null) {
            if (left || right || up || down) { askYes = !askYes; sfx.accept("ui_click"); }
            if (enter) { Item it = asking; boolean yes = askYes; asking = null; if (yes) { sfx.accept("ui_confirm"); if (it.sub != null) push(it.sub); else if (it.select != null) it.select.run(); } else sfx.accept("ui_back"); }
            if (backKey) { asking = null; sfx.accept("ui_back"); }
            return 1;
        }
        if (up) move(m, -1);
        if (down) move(m, 1);
        Item it = m.selected();
        if (it != null && (left || right) && it.adjust != null) {
            if (it.enabled.getAsBoolean()) { it.adjust.accept(left ? -1 : 1); sfx.accept("inv_grid"); if (m.onHighlight != null) m.onHighlight.run(); } else sfx.accept("ui_invalid");
        }
        if (it != null && enter) {
            if (it.confirm != null && it.enabled.getAsBoolean()) { asking = it; askYes = false; sfx.accept("ui_tab"); }
            else activate(it);
        }
        if (backKey) { sfx.accept("ui_back"); if (!back()) return 2; }
        return 1;
    }

    /**
     * 0.9: the pointer works every menu. Moving it over a row highlights the row; a click selects it, or - on the left or
     * right of a row's value - turns the value down or up; the wheel moves the highlight; the right button goes back.
     * Returns 0 when the mouse did nothing this frame, otherwise what {@link #input} returns.
     */
    private int mouse(Input in, Menu m) {
        float x = (float) in.mouseX, y = (float) in.mouseY;
        boolean moved = Math.abs(in.mouseX - lastMouseX) + Math.abs(in.mouseY - lastMouseY) > 0.5;
        lastMouseX = in.mouseX; lastMouseY = in.mouseY;
        boolean click = in.mousePressed(0), rclick = in.mousePressed(1);
        if (mW <= 0) return 0;
        if (asking != null) {
            float bw = askW, by = askY;
            for (int k = 0; k < 2; k++) {
                float bx = askX + (k == 0 ? -bw - 8 : 8);
                boolean over = x >= bx && x <= bx + bw && y >= by && y <= by + mRowH;
                if (over && moved) askYes = k == 0;
                if (over && click) { Item it = asking; asking = null; if (k == 0) { sfx.accept("ui_confirm"); if (it.sub != null) push(it.sub); else if (it.select != null) it.select.run(); } else sfx.accept("ui_back"); return 1; }
            }
            if (rclick) { asking = null; sfx.accept("ui_back"); return 1; }
            return 0;
        }
        if (rclick) { sfx.accept("ui_back"); return back() ? 1 : 2; }
        boolean inside = x >= mX && x <= mX + mW && y >= mRowsY && y < mRowsY + mShown * mRowH;
        if (in.scroll != 0 && x >= mX && x <= mX + mW) { move(m, in.scroll > 0 ? -1 : 1); return 1; }
        if (!inside) return 0;
        int row = mFirst + (int) ((y - mRowsY) / mRowH);
        if (row < 0 || row >= m.items.size()) return 0;
        if (moved && row != m.sel) { m.sel = row; if (m.onHighlight != null) m.onHighlight.run(); }
        if (!click) return 0;
        m.sel = row;
        Item it = m.items.get(row);
        // a value is turned by clicking beside it: the left third of its side of the row is down, the right third is up
        if (it.adjust != null && it.sub == null && x > mX + mW * 0.52f) {
            if (it.enabled.getAsBoolean()) { it.adjust.accept(x < mX + mW * 0.76f ? -1 : 1); sfx.accept("inv_grid"); if (m.onHighlight != null) m.onHighlight.run(); } else sfx.accept("ui_invalid");
            return 1;
        }
        if (it.confirm != null && it.enabled.getAsBoolean()) { asking = it; askYes = false; sfx.accept("ui_tab"); }
        else activate(it);
        return 1;
    }

    /** Draws the open menu as a compact panel with its top-left corner at (x, y). Returns its height. */
    public float draw(Ui ui, float x, float y, float w, String header) {
        Menu m = current();
        if (m == null) return 0;
        float rowH = ui.s(26), pad = ui.s(12);
        int n = m.items.size(), first = Math.max(0, Math.min(Math.max(0, n - VISIBLE), m.sel - VISIBLE / 2)), shown = Math.min(VISIBLE, n);
        float h = ui.s(58) + shown * rowH + ui.s(54);
        ui.b.rect(x, y, w, h, 0xE60A0B0D);
        ui.b.rect(x, y, w, ui.s(26), 0xFFB03A2E);
        ui.text(header, x + pad, y + ui.s(6), 13, 0xFFFFFFFF);
        // where in the tree this is
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < stack.size(); i++) path.append(i > 0 ? "  >  " : "").append(stack.get(i).title);
        ui.text(path.toString().toUpperCase(), x + pad, y + ui.s(34), 11, Ui.AMBER);
        ui.b.rect(x + pad, y + ui.s(52), w - pad * 2, 1, 0xFF3A3630);
        float ry = y + ui.s(58);
        mX = x; mW = w; mRowH = rowH; mRowsY = ry; mFirst = first; mShown = shown;
        for (int i = first; i < first + shown; i++) {
            Item it = m.items.get(i);
            boolean sel = i == m.sel, on = it.enabled.getAsBoolean();
            if (sel) { ui.b.rect(x + ui.s(4), ry, w - ui.s(8), rowH - ui.s(2), on ? 0xFFE8E2D2 : 0xFF4A463E); }
            int col = !on ? (sel ? 0xFF1A1816 : 0xFF5E5A50) : sel ? 0xFF14120E : Ui.TXT_HI;
            ui.text((sel ? "> " : "  ") + it.label + (it.sub != null ? "" : ""), x + pad, ry + ui.s(5), 13, col);
            if (it.value != null) {
                String v = it.value.get();
                ui.textR((it.adjust != null && on ? "<  " : "") + v + (it.adjust != null && on ? "  >" : ""), x + w - pad, ry + ui.s(5), 13, col);
            } else if (it.sub != null) ui.textR(">", x + w - pad, ry + ui.s(5), 13, col);
            ry += rowH;
        }
        if (n > shown) { float sbh = shown * rowH * shown / n; ui.b.rect(x + w - ui.s(3), y + ui.s(58) + (shown * rowH - sbh) * (first / (float) Math.max(1, n - shown)), ui.s(2), sbh, Ui.AMBER); }
        ui.b.rect(x + pad, ry + ui.s(4), w - pad * 2, 1, 0xFF3A3630);
        Item it = m.selected();
        String info = it == null ? "" : !it.enabled.getAsBoolean() ? "Unavailable: " + it.disabledWhy.get() : it.hint;
        absentialis.ui.Layout.fit(ui, info, x + pad, ry + ui.s(10), w - pad * 2, 11, 9, it != null && !it.enabled.getAsBoolean() ? Ui.RED : Ui.TXT);
        ui.text("Mouse: point, click, wheel, right-click back   Keys: [Up/Down] [Left/Right] [Enter] [Backspace]", x + pad, ry + ui.s(30), 9, Ui.TXT_DIM);
        if (asking != null) {
            float qw = Math.min(w - ui.s(20), ui.s(360)), qh = ui.s(92), qx = x + (w - qw) / 2, qy = y + (h - qh) / 2;
            ui.b.rect(qx - 2, qy - 2, qw + 4, qh + 4, 0xFFB03A2E);
            ui.b.rect(qx, qy, qw, qh, 0xFF0E0F11);
            absentialis.ui.Layout.wrapC(ui, asking.confirm, qx + ui.s(10), qy + ui.s(12), qw - ui.s(20), 12, Ui.TXT_HI);
            float bw = ui.s(90);
            askX = qx + qw / 2; askY = qy + qh - ui.s(34); askW = bw;
            for (int k = 0; k < 2; k++) {
                boolean sel = (k == 0) == askYes;
                float bx = qx + qw / 2 + (k == 0 ? -bw - ui.s(8) : ui.s(8));
                ui.b.rect(bx, qy + qh - ui.s(34), bw, ui.s(24), sel ? 0xFFE8E2D2 : 0xFF24262A);
                ui.textC(k == 0 ? "YES" : "NO", bx + bw / 2, qy + qh - ui.s(29), 12, sel ? 0xFF14120E : Ui.TXT);
            }
        }
        return h;
    }
}
