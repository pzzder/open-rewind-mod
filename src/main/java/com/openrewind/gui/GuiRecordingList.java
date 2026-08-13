package com.openrewind.gui;

import com.openrewind.editor.ProjectStore;
import com.openrewind.recording.RecordingManager;
import com.openrewind.recording.RecordingMetadata;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Browser for finished recordings. Select one, then Quick View (play it) or
 * Create Project (open the Create-Project dialog → editor). Mirrors Lunar
 * Rewind's editor landing screen.
 */
public class GuiRecordingList extends GuiScreen {

    private final GuiScreen parent;
    private List<RecordingMetadata> recordings;
    private int selected = -1;
    private int scroll = 0;

    private static final int ROW_H = 30;
    private static final int LIST_TOP = 40;
    private static final SimpleDateFormat DF =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);

    private static final int ID_QUICK_VIEW  = 1;
    private static final int ID_NEW_PROJECT = 2;
    private static final int ID_SHADOW      = 3;
    private static final int ID_BACK        = 4;

    public GuiRecordingList(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        recordings = ProjectStore.listRecordings();
        buttonList.clear();
        int y = height - 30;
        buttonList.add(new GuiButton(ID_QUICK_VIEW,  width / 2 - 205, y, 100, 20, "Quick View"));
        buttonList.add(new GuiButton(ID_NEW_PROJECT, width / 2 - 100, y, 140, 20, "Create Project"));
        buttonList.add(new GuiButton(ID_SHADOW,      width / 2 + 45,  y, 100, 20, "Save Shadow"));
        buttonList.add(new GuiButton(ID_BACK,        width / 2 + 150, y, 55,  20, "Back"));
    }

    private int listBottom() { return height - 40; }

    @Override
    protected void actionPerformed(GuiButton b) {
        switch (b.id) {
            case ID_BACK: mc.displayGuiScreen(parent); return;
            case ID_QUICK_VIEW:
                if (validSelection()) mc.displayGuiScreen(new GuiQuickView(this, recordings.get(selected)));
                return;
            case ID_NEW_PROJECT:
                if (validSelection()) mc.displayGuiScreen(new GuiCreateProject(this, recordings.get(selected)));
                return;
            case ID_SHADOW:
                RecordingManager.get().saveShadow();
                return;
        }
    }

    private boolean validSelection() {
        return selected >= 0 && selected < recordings.size();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseY >= LIST_TOP && mouseY < listBottom()) {
            int idx = (mouseY - LIST_TOP + scroll) / ROW_H;
            if (idx >= 0 && idx < recordings.size()) {
                if (idx == selected) {
                    mc.displayGuiScreen(new GuiCreateProject(this, recordings.get(idx)));
                } else {
                    selected = idx;
                }
            }
        }
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int dWheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (dWheel != 0) {
            scroll -= Integer.signum(dWheel) * ROW_H;
            int max = Math.max(0, recordings.size() * ROW_H - (listBottom() - LIST_TOP));
            scroll = Math.max(0, Math.min(scroll, max));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "\u00A7lYour Recordings", width / 2, 16, 0xFFFFFF);

        if (recordings.isEmpty()) {
            drawCenteredString(fontRendererObj,
                    "\u00A77No recordings yet. Press Shift+R in-game to record.",
                    width / 2, height / 2, 0xAAAAAA);
        }

        int x0 = width / 2 - 205;
        int x1 = width / 2 + 205;
        for (int i = 0; i < recordings.size(); i++) {
            int rowY = LIST_TOP + i * ROW_H - scroll;
            if (rowY + ROW_H < LIST_TOP || rowY > listBottom()) continue;
            RecordingMetadata m = recordings.get(i);
            int bg = (i == selected) ? 0x804488FF : 0x60000000;
            drawRect(x0, rowY, x1, rowY + ROW_H - 2, bg);
            fontRendererObj.drawStringWithShadow(m.title, x0 + 6, rowY + 4, 0xFFFFFF);
            String sub = DF.format(new Date(m.createdAt))
                    + "  \u00B7  " + formatDur(m.durationMs)
                    + "  \u00B7  " + m.width + "x" + m.height + "@" + m.fps
                    + (m.markers != null && !m.markers.isEmpty() ? "  \u00B7  \u2691" + m.markers.size() : "")
                    + (m.hasMicrophone ? "  \u00B7  mic" : "");
            fontRendererObj.drawStringWithShadow("\u00A77" + sub, x0 + 6, rowY + 15, 0xAAAAAA);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    static String formatDur(long ms) {
        long s = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    @Override
    public boolean doesGuiPauseGame() { return true; }
}
