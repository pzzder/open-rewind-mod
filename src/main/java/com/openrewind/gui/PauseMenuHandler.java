package com.openrewind.gui;

import com.openrewind.config.RewindConfig;
import com.openrewind.recording.RecordingManager;
import com.openrewind.replay.ReplayRecordingManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Injects OpenRewind controls into the vanilla pause (ESC) menu, mirroring how
 * Lunar Rewind lets you start / stop a recording from the pause screen. Routes
 * to the active engine (state-based by default).
 */
public class PauseMenuHandler {

    private static final int ID_REC    = 90_100;
    private static final int ID_EDITOR = 90_101;
    private static final int ID_SHADOW = 90_102;

    private boolean recording() {
        return RewindConfig.stateRecording
                ? ReplayRecordingManager.get().isRecording()
                : RecordingManager.get().isRecording();
    }

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.getGui();
        if (!(gui instanceof GuiIngameMenu)) return;
        int x = gui.width / 2 - 100;
        int y = gui.height / 4 + 8;
        String recLabel = recording() ? "\u00A7cStop Rewind" : "\u00A7aRecord Rewind";
        event.getButtonList().add(new GuiButton(ID_REC, x, y - 24, 98, 20, recLabel));
        event.getButtonList().add(new GuiButton(ID_EDITOR, x + 102, y - 24, 98, 20, "Rewind Editor"));
        // Shadow only applies to the pixel engine; hidden in state mode
        if (!RewindConfig.stateRecording && RecordingManager.get().isShadowRunning()) {
            event.getButtonList().add(new GuiButton(ID_SHADOW, x, y - 48, 200, 20,
                    "\u00A7dSave Shadow (" + (int) RecordingManager.get().shadowBufferedSeconds() + "s)"));
        }
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Post event) {
        GuiScreen gui = event.getGui();
        if (!(gui instanceof GuiIngameMenu)) return;
        GuiButton b = event.getButton();
        if (b == null) return;
        if (b.id == ID_REC) {
            if (RewindConfig.stateRecording) ReplayRecordingManager.get().toggle();
            else RecordingManager.get().toggleRecording();
            b.displayString = recording() ? "\u00A7cStop Rewind" : "\u00A7aRecord Rewind";
        } else if (b.id == ID_EDITOR) {
            Minecraft.getMinecraft().displayGuiScreen(RewindConfig.stateRecording
                    ? new GuiReplayBrowser(null) : new GuiRecordingList(null));
        } else if (b.id == ID_SHADOW) {
            RecordingManager.get().saveShadow();
        }
    }
}
