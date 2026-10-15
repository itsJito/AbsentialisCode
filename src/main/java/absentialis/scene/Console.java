package absentialis.scene;

import static org.lwjgl.glfw.GLFW.*;

import absentialis.engine.Input;
import absentialis.ui.Ui;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Developer console (`~`): command line with Tab autocomplete (commands and their arguments),
 * history (Up/Down) and a scrollback log. Commands are registered by the app with a handler
 * and an argument-completion source.
 */
public final class Console {
    public boolean open;
    private final StringBuilder line = new StringBuilder();
    private final List<String> log = new ArrayList<>(), history = new ArrayList<>();
    private int histPos = -1;
    private float anim;
    public record Cmd(String help, Function<String[], String> run, Function<Integer, List<String>> args) {}
    public final Map<String, Cmd> cmds = new LinkedHashMap<>();
    private List<String> suggestions = new ArrayList<>();

    public void register(String name, String help, Function<String[], String> run, Function<Integer, List<String>> args) {
        cmds.put(name, new Cmd(help, run, args));
    }

    public void print(String s) { for (String l : s.split("\n")) log.add(l); while (log.size() > 300) log.remove(0); lastLine = s; }

    public void toggle() { open = !open; line.setLength(0); }

    /** Handles typing; returns true while the console captures input. */
    public boolean input(Input in) {
        if (!open) return false;
        while (!in.typed.isEmpty()) {
            char c = in.typed.poll();
            if (c == '`' || c == '~') continue;
            line.append(c);
        }
        if (in.keyPressed(GLFW_KEY_BACKSPACE) && line.length() > 0) line.setLength(line.length() - 1);
        if (in.keyPressed(GLFW_KEY_ENTER)) { exec(line.toString().trim()); line.setLength(0); histPos = -1; }
        if (in.keyPressed(GLFW_KEY_UP) && !history.isEmpty()) { histPos = Math.min(history.size() - 1, histPos + 1); line.setLength(0); line.append(history.get(history.size() - 1 - histPos)); }
        if (in.keyPressed(GLFW_KEY_DOWN) && histPos > 0) { histPos--; line.setLength(0); line.append(history.get(history.size() - 1 - histPos)); }
        suggestions = complete(line.toString());
        if (in.keyPressed(GLFW_KEY_TAB) && !suggestions.isEmpty()) {
            String s = line.toString();
            int sp = s.lastIndexOf(' ');
            line.setLength(0);
            line.append(sp < 0 ? suggestions.get(0) + " " : s.substring(0, sp + 1) + suggestions.get(0));
        }
        return true;
    }

    public List<String> complete(String s) {
        List<String> out = new ArrayList<>();
        String[] parts = s.split(" ", -1);
        if (parts.length <= 1) { for (String c : cmds.keySet()) if (c.startsWith(parts[0])) out.add(c); return out; }
        Cmd c = cmds.get(parts[0]);
        if (c == null || c.args == null) return out;
        List<String> opts = c.args.apply(parts.length - 2);
        if (opts == null) return out;
        String last = parts[parts.length - 1].toLowerCase();
        for (String o : opts) if (o.toLowerCase().startsWith(last)) out.add(o);
        if (out.size() > 12) out = out.subList(0, 12);
        return out;
    }

    /** The last line the console printed (scripts read what a command answered). */
    public String last() { return lastLine; }
    private String lastLine = "";

    public void exec(String s) {
        if (s.isEmpty()) return;
        history.add(s);
        print("> " + s);
        String[] p = s.split("\\s+");
        Cmd c = cmds.get(p[0].toLowerCase());
        if (c == null) { print("Unknown command. Type 'help'."); return; }
        try {
            String r = c.run.apply(p);
            if (r != null && !r.isEmpty()) print(r);
        } catch (Exception e) {
            print("Error: " + e.getMessage());
        }
    }

    public String help() {
        StringBuilder b = new StringBuilder("Commands:");
        for (var e : cmds.entrySet()) b.append("\n  ").append(e.getKey()).append(" - ").append(e.getValue().help);
        return b.toString();
    }

    public void draw(Ui ui, float dt) {
        anim += ((open ? 1 : 0) - anim) * Math.min(1, dt / Ui.EASE);
        if (anim < 0.01f) return;
        float h = ui.h * 0.45f * anim;
        ui.b.rect(0, 0, ui.w, h, 0xE40C0E0D);
        ui.b.rect(0, h - 2, ui.w, 2, 0xFF8A6420);
        float lh = ui.s(17), y = h - ui.s(36);
        ui.text("> " + line + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : ""), ui.s(12), y, 14, Ui.AMBER);
        if (!suggestions.isEmpty() && line.length() > 0) ui.text(String.join("   ", suggestions), ui.s(12), y + ui.s(17), 12, Ui.TXT_DIM);
        for (int i = log.size() - 1; i >= 0 && y > ui.s(4); i--) {
            y -= lh;
            String l = log.get(i);
            ui.text(l, ui.s(12), y, 13, l.startsWith(">") ? Ui.TXT_HI : l.startsWith("Error") ? Ui.RED : Ui.TXT);
        }
    }
}
