package com.openrewind.input;

import com.openrewind.gui.GuiRewindEditor;
import com.openrewind.gui.GuiRewindSettings;
import com.openrewind.config.RewindConfig;
import com.openrewind.recording.RecordingManager;
import com.openrewind.replay.ReplayRecordingManager;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;

import org.lwjgl.input.Keyboard;

/**
 * Translates raw key presses into OpenRewind actions and routes them to the
 * active recording engine. OpenRewind now defaults to the <b>state-based</b>
 * engine (like Lunar Rewind); the pixel engine remains only as an internal
 * fallback and is not exposed as a user option.
 *
 * <p>To faithfully reproduce Lunar's "Shift + R" toggle we don't just rely on
 * the bound key firing – we also require the shift modifier while the bind is
 * still the plain "R" default, so a bare "R" doesn't accidentally start a
 * recording. Rebinding the key in the Controls menu removes that requirement.</p>
 */
public class KeyInputHandler {

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        RecordingManager pixel = RecordingManager.get();
        ReplayRecordingManager state = ReplayRecordingManager.get();
        boolean useState = RewindConfig.stateRecording;

        if (KeyBindings.toggleRecording.isPressed()) {
            boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                    || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            if (KeyBindings.toggleRecording.getKeyCode() != Keyboard.KEY_R || shift) {
                if (useState) state.toggle();
                else          pixel.toggleRecording();
            }
        }

        if (KeyBindings.addMarker.isPressed()) {
            if (useState) state.addMarker();
            else if (pixel.isRecording()) pixel.addMarker();
        }

        if (KeyBindings.pauseResume.isPressed()) {
            if (useState) state.togglePause();
            else if (pixel.isRecording()) pixel.togglePause();
        }

        if (KeyBindings.openEditor.isPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(useState
                    ? new com.openrewind.gui.GuiReplayBrowser(null)
                    : new GuiRewindEditor(null));
        }

        if (KeyBindings.openSettings.isPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiRewindSettings(null));
        }

        if (KeyBindings.saveShadow.isPressed()) {
            // Shadow currently lives in the pixel engine; in state mode it is
            // superseded by continuous cheap recording (Shift+R).
            if (!useState) pixel.saveShadow();
        }
    }
}
