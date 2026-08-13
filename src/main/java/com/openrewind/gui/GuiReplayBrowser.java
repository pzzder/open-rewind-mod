package com.openrewind.gui;

import com.openrewind.replay.edit.ReplayStore;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Browser for state-based {@code .orwr} replays — the landing screen of the
 * Lunar-style Rewind editor. Select a replay, then Edit (camera / effects /
 * export settings) or Quick Export.
 */
public class GuiReplayBrowser extends GuiScreen {

    private final GuiScreen parent;
    private List<ReplayStore.Entry> replays;
    private int selected = -1;
    private int scroll = 0;

    private static final int ROW_H = 30, LIST_TOP = 40;
    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);

    private static final int ID_EDIT = 1, ID_EXPORT = 2, ID_BACK = 3;

    public GuiReplayBrowser(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        replays = ReplayStore.listReplays();
        buttonList.clear();
        int y = height - 30;
        buttonList.add(new GuiButton(ID_EDIT,   width / 2 - 155, y, 100, 20, "Edit"));
        buttonList.add(new GuiButton(ID_EXPORT, width / 2 - 50,  y, 140, 20, "Quick Export"));
        buttonList.add(new GuiButton(ID_BACK,   width / 2 + 95,  y, 60,  20, "Back"));
    }

    private int listBottom() { return height - 40; }
    private boolean valid() { return selected >= 0 && selected < replays.size(); }

    @Override
    protected void actionPerformed(GuiButton b) {
        if (b.id == ID_BACK) { mc.displayGuiScreen(parent); return; }
        if (!valid()) return;
        ReplayStore.Entry e = replays.get(selected);
        if (b.id == ID_EDIT)   mc.displayGuiScreen(new GuiReplayEditor(this, e.file));
        if (b.id == ID_EXPORT) mc.displayGuiScreen(new GuiReplayEditor(this, e.file)); // editor hosts export
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws java.io.IOException {
        super.mouseClicked(mx, my, btn);
        if (my >= LIST_TOP && my < listBottom()) {
            int idx = (my - LIST_TOP + scroll) / ROW_H;
            if (idx >= 0 && idx < replays.size()) {
                if (idx == selected) mc.displayGuiScreen(new GuiReplayEditor(this, replays.get(idx).file));
                else selected = idx;
            }
        }
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int w = org.lwjgl.input.Mouse.getEventDWheel();
        if (w != 0) {
            scroll -= Integer.signum(w) * ROW_H;
            int max = Math.max(0, replays.size() * ROW_H - (listBottom() - LIST_TOP));
            scroll = Math.max(0, Math.min(scroll, max));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float pt) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "\u00A7lReplays", width / 2, 16, 0xFFFFFF);
        if (replays.isEmpty()) {
            drawCenteredString(fontRendererObj,
                    "\u00A77No replays yet. Record one with Shift+R.", width / 2, height / 2, 0xAAAAAA);
        }
        int x0 = width / 2 - 205, x1 = width / 2 + 205;
        for (int i = 0; i < replays.size(); i++) {
            int rowY = LIST_TOP + i * ROW_H - scroll;
            if (rowY + ROW_H < LIST_TOP || rowY > listBottom()) continue;
            ReplayStore.Entry e = replays.get(i);
            drawRect(x0, rowY, x1, rowY + ROW_H - 2, i == selected ? 0x804488FF : 0x60000000);
            fontRendererObj.drawStringWithShadow(e.file.getName().replace(".orwr", ""),
                    x0 + 6, rowY + 4, 0xFFFFFF);
            String sub = DF.format(new Date(e.meta.date))
                    + "  \u00B7  " + (e.meta.duration / 1000) + "s"
                    + "  \u00B7  " + (e.meta.serverName == null ? "?" : e.meta.serverName)
                    + (e.meta.markers != null && !e.meta.markers.isEmpty() ? "  \u00B7  \u2691" + e.meta.markers.size() : "");
            fontRendererObj.drawStringWithShadow("\u00A77" + sub, x0 + 6, rowY + 15, 0xAAAAAA);
        }
        super.drawScreen(mouseX, mouseY, pt);
    }

    @Override
    public boolean doesGuiPauseGame() { return true; }
}
