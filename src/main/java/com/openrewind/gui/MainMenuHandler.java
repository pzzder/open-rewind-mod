package com.openrewind.gui;

import com.openrewind.config.RewindConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Adds an OpenRewind entry point to the main menu (a "Rewind" button that opens
 * the recording browser / editor, plus a small settings button), mirroring how
 * Lunar surfaces Rewind + its settings gear on its home screen.
 */
public class MainMenuHandler {

    private static final int ID_REWIND   = 90_200;
    private static final int ID_SETTINGS = 90_201;

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.getGui();
        if (!(gui instanceof GuiMainMenu)) return;

        // bottom-left corner, above the version/copyright line
        int x = 6;
        int y = gui.height - 42;
        event.getButtonList().add(new GuiButton(ID_REWIND, x, y, 120, 20, "\u25C0\u25C0 Rewind"));
        event.getButtonList().add(new GuiButton(ID_SETTINGS, x + 122, y, 20, 20, "\u2699"));
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Post event) {
        GuiScreen gui = event.getGui();
        if (!(gui instanceof GuiMainMenu)) return;
        GuiButton b = event.getButton();
        if (b == null) return;
        if (b.id == ID_REWIND) {
            Minecraft.getMinecraft().displayGuiScreen(RewindConfig.stateRecording
                    ? new GuiReplayBrowser(gui) : new GuiRecordingList(gui));
        } else if (b.id == ID_SETTINGS) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiRewindSettings(gui));
        }
    }
}
