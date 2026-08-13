package com.openrewind.input;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import org.lwjgl.input.Keyboard;

/**
 * All OpenRewind key bindings. They appear under a dedicated "OpenRewind"
 * category in the vanilla Controls screen so users can rebind them.
 *
 * <p>Defaults mirror Lunar Rewind where sensible:
 * <ul>
 *     <li>Toggle recording – R (combine with Shift by holding it, handled in
 *         {@link KeyInputHandler})</li>
 *     <li>Add marker – M</li>
 *     <li>Pause / resume – P</li>
 *     <li>Open editor – O</li>
 * </ul></p>
 */
public final class KeyBindings {

    private KeyBindings() { }

    public static final String CATEGORY = "OpenRewind";

    public static KeyBinding toggleRecording;
    public static KeyBinding addMarker;
    public static KeyBinding pauseResume;
    public static KeyBinding openEditor;
    public static KeyBinding openSettings;
    public static KeyBinding saveShadow;

    public static void register() {
        toggleRecording = new KeyBinding("key.openrewind.toggle",  Keyboard.KEY_R, CATEGORY);
        addMarker       = new KeyBinding("key.openrewind.marker",  Keyboard.KEY_M, CATEGORY);
        pauseResume     = new KeyBinding("key.openrewind.pause",   Keyboard.KEY_P, CATEGORY);
        openEditor      = new KeyBinding("key.openrewind.editor",  Keyboard.KEY_O, CATEGORY);
        openSettings    = new KeyBinding("key.openrewind.settings", Keyboard.KEY_NONE, CATEGORY);
        saveShadow      = new KeyBinding("key.openrewind.shadow",  Keyboard.KEY_NONE, CATEGORY);

        ClientRegistry.registerKeyBinding(toggleRecording);
        ClientRegistry.registerKeyBinding(addMarker);
        ClientRegistry.registerKeyBinding(pauseResume);
        ClientRegistry.registerKeyBinding(openEditor);
        ClientRegistry.registerKeyBinding(openSettings);
        ClientRegistry.registerKeyBinding(saveShadow);
    }
}
