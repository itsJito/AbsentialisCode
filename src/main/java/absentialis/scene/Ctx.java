package absentialis.scene;

import absentialis.engine.Input;
import absentialis.engine.Window;
import absentialis.engine.audio.UltraEngine;
import absentialis.engine.events.EventBus;
import absentialis.engine.render.Atlas;
import absentialis.engine.render.ModelRenderer;
import absentialis.engine.render.WorldRenderer;
import absentialis.persistence.Profile;
import absentialis.persistence.Settings;
import absentialis.ui.InventoryUi;
import absentialis.ui.ItemIcons;
import absentialis.ui.MapView;
import absentialis.ui.Ui;
import absentialis.world.World;

/** Shared services handed to every screen (no globals). */
public final class Ctx {
    public Window win;
    public Input in;
    public Ui ui;
    public ItemIcons icons;
    public InventoryUi inv;
    public MapView map;
    public Console console;
    public UltraEngine audio;
    public Settings settings;
    public EventBus bus = new EventBus();
    public World world;
    public Atlas atlas;
    public WorldRenderer wr;
    /** The deferred hybrid-GI renderer; null when the driver has no OpenGL 4.5 (the legacy forward path is used). */
    public absentialis.engine.render.HybridRenderer hr;
    public ModelRenderer mr;
    public ActorRenderer ar;
    public Profile profile;
    public float time;

    public void sfx(String name, float gain) { if (audio != null && audio.available) audio.play(name, gain); }
    public void sfx(String name, float gain, float pitch) { if (audio != null && audio.available) audio.play(name, gain, 0, pitch); }
    /** Interface feedback (hover, click, invalid): quiet, never positional. */
    public void ui(String name) { if (audio != null && audio.available) audio.play(name, name.equals("ui_hover") ? 0.25f : 0.45f, 0, 1, absentialis.engine.audio.UltraEngine.UI); }
}
