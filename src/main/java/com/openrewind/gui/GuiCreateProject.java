package com.openrewind.gui;

import com.openrewind.editor.Clip;
import com.openrewind.editor.Project;
import com.openrewind.editor.ProjectStore;
import com.openrewind.recording.RecordingMetadata;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import java.io.IOException;

/**
 * "Create Project" dialog, mirroring Lunar Rewind's – lets you set the project
 * name, timeline resolution, orientation, framerate and audio type before the
 * recording is opened in the editor.
 */
public class GuiCreateProject extends GuiScreen {

    private final GuiScreen parent;
    private final RecordingMetadata source;

    private GuiTextField nameField;

    private static final String[] RES = {"Source", "480p", "720p", "1080p", "1440p"};
    private static final String[] ORI = {"Horizontal", "Vertical"};
    private static final int[]    FPS = {24, 30, 48, 60, 120};
    private static final String[] AUD = {"Stereo", "Mono"};

    private int resIdx = 3, oriIdx = 0, fpsIdx = 1, audIdx = 0;

    private static final int ID_RES = 1, ID_ORI = 2, ID_FPS = 3, ID_AUD = 4;
    private static final int ID_CREATE = 5, ID_CANCEL = 6;

    public GuiCreateProject(GuiScreen parent, RecordingMetadata source) {
        this.parent = parent;
        this.source = source;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        int y = 60;

        nameField = new GuiTextField(0, fontRendererObj, cx - 150, y, 300, 20);
        nameField.setMaxStringLength(64);
        nameField.setText(source.title);
        nameField.setFocused(true);
        y += 40;

        buttonList.add(new GuiButton(ID_RES, cx + 40, y, 110, 20, "Resolution: " + RES[resIdx]));
        y += 26;
        buttonList.add(new GuiButton(ID_ORI, cx + 40, y, 110, 20, "Orientation: " + ORI[oriIdx]));
        y += 26;
        buttonList.add(new GuiButton(ID_FPS, cx + 40, y, 110, 20, "Framerate: " + FPS[fpsIdx]));
        y += 26;
        buttonList.add(new GuiButton(ID_AUD, cx + 40, y, 110, 20, "Audio: " + AUD[audIdx]));
        y += 40;

        buttonList.add(new GuiButton(ID_CANCEL, cx - 150, y, 145, 20, "Cancel"));
        buttonList.add(new GuiButton(ID_CREATE, cx + 5,  y, 145, 20, "\u00A7aCreate Project"));
    }

    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        switch (b.id) {
            case ID_RES: resIdx = (resIdx + 1) % RES.length; b.displayString = "Resolution: " + RES[resIdx]; break;
            case ID_ORI: oriIdx = (oriIdx + 1) % ORI.length; b.displayString = "Orientation: " + ORI[oriIdx]; break;
            case ID_FPS: fpsIdx = (fpsIdx + 1) % FPS.length; b.displayString = "Framerate: " + FPS[fpsIdx]; break;
            case ID_AUD: audIdx = (audIdx + 1) % AUD.length; b.displayString = "Audio: " + AUD[audIdx]; break;
            case ID_CANCEL: mc.displayGuiScreen(parent); break;
            case ID_CREATE: createAndOpen(); break;
        }
    }

    private void createAndOpen() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) name = source.title;

        Project p = new Project(name);
        p.clips.add(new Clip(source.id, 0, source.durationMs));
        p.resolutionPreset = RES[resIdx];
        p.orientation      = ORI[oriIdx];
        p.exportFps        = FPS[fpsIdx];
        p.audioMono        = audIdx == 1;
        // seed explicit export size from the source; the preset overrides at export
        p.exportWidth  = source.width;
        p.exportHeight = source.height;

        ProjectStore.saveProject(p);
        mc.displayGuiScreen(new GuiRewindEditor(p));
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (nameField.textboxKeyTyped(c, key)) return;
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        super.mouseClicked(mx, my, btn);
        nameField.mouseClicked(mx, my, btn);
    }

    @Override
    public void updateScreen() {
        nameField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;
        drawCenteredString(fontRendererObj, "\u00A7lCreate Project", cx, 24, 0xFFFFFF);
        drawString(fontRendererObj, "Project Name", cx - 150, 48, 0xAAAAAA);
        nameField.drawTextBox();

        int y = 100;
        drawString(fontRendererObj, "\u00A77Resolution of the timeline",  cx - 150, y + 6, 0xAAAAAA); y += 26;
        drawString(fontRendererObj, "\u00A77Orientation of the timeline", cx - 150, y + 6, 0xAAAAAA); y += 26;
        drawString(fontRendererObj, "\u00A77Framerate of the timeline",   cx - 150, y + 6, 0xAAAAAA); y += 26;
        drawString(fontRendererObj, "\u00A77Audio type of the timeline",  cx - 150, y + 6, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() { return true; }
}
