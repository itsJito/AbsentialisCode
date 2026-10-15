package absentialis.director;

import absentialis.engine.util.Json;
import absentialis.persistence.SaveSystem;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PersistenceManager: what Director Mode remembers between sessions - the shortlist of up to ten favourite actors,
 * and the settings last used. It is one small file beside the profiles ({@code director.json}); it is not part of any
 * profile, and nothing a session does to the survivor is ever written to theirs.
 */
public final class PersistenceManager {
    public static final int SHORTLIST_MAX = 10;
    public final List<ActorManager.Spec> shortlist = new ArrayList<>();
    public final Map<String, Object> preferences = new LinkedHashMap<>();
    /** 0.9: characters made in Director Mode's own character creator - a name and an appearance each, kept between sessions. */
    public static final class Character { public String name = ""; public absentialis.character.Appearance app = new absentialis.character.Appearance(); }
    public static final int CHARACTERS_MAX = 12;
    public final List<Character> characters = new ArrayList<>();
    public String problem = "";

    public static Path file() { return SaveSystem.dir.resolve("director.json"); }

    public boolean shortlisted(ActorManager.Spec s) { for (ActorManager.Spec o : shortlist) if (o.same(s)) return true; return false; }

    /** Adds an actor to the shortlist, or takes it off if it is already there. Returns what happened, in words. */
    public String toggle(ActorManager.Spec s) {
        for (ActorManager.Spec o : new ArrayList<>(shortlist)) if (o.same(s)) { shortlist.remove(o); save(); return "Removed from the shortlist"; }
        if (shortlist.size() >= SHORTLIST_MAX) return "The shortlist is full (" + SHORTLIST_MAX + "): remove one first";
        shortlist.add(s.copy());
        save();
        return "Shortlisted (" + shortlist.size() + " of " + SHORTLIST_MAX + ")";
    }

    public void save() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Object> sl = new ArrayList<>();
        for (ActorManager.Spec s : shortlist) sl.add(s.toJson());
        m.put("shortlist", sl);
        m.put("preferences", new LinkedHashMap<>(preferences));
        List<Object> cl = new ArrayList<>();
        for (Character ch : characters) { Map<String, Object> o = new LinkedHashMap<>(); o.put("name", ch.name); o.put("appearance", ch.app.toJson()); cl.add(o); }
        m.put("characters", cl);
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), Json.write(m), StandardCharsets.UTF_8);
            problem = "";
        } catch (Exception e) { problem = "Could not save the shortlist: " + e.getMessage(); }
    }

    @SuppressWarnings("unchecked")
    public void load() {
        shortlist.clear(); preferences.clear(); characters.clear();
        try {
            if (!Files.exists(file())) return;
            Map<String, Object> m = Json.parseObject(Files.readString(file(), StandardCharsets.UTF_8));
            for (Object o : Json.list(m, "shortlist")) if (o instanceof Map && shortlist.size() < SHORTLIST_MAX) { ActorManager.Spec s = ActorManager.Spec.fromJson((Map<String, Object>) o); if (s != null) shortlist.add(s); }
            preferences.putAll(Json.obj(m, "preferences"));
            for (Object o : Json.list(m, "characters")) if (o instanceof Map && characters.size() < CHARACTERS_MAX) {
                Map<String, Object> cm = (Map<String, Object>) o;
                Character ch = new Character();
                ch.name = Json.str(cm, "name", "Character");
                ch.app = absentialis.character.Appearance.fromJson(Json.obj(cm, "appearance"));
                if (ch.app != null) characters.add(ch);
            }
        } catch (Exception e) { problem = "The shortlist could not be read (" + e.getMessage() + ") - starting with an empty one"; }
    }

    public int pref(String key, int def) { Object v = preferences.get(key); return v instanceof Number n ? n.intValue() : def; }
    public void setPref(String key, int value) { preferences.put(key, value); }
}
