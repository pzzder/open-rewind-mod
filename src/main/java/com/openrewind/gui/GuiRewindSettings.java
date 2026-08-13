package com.openrewind.gui;

import com.openrewind.config.RewindConfig;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import java.io.IOException;

/**
 * In-game settings screen for OpenRewind. Toggles and +/- steppers write
 * straight into {@link RewindConfig} and persist on close.
 *
 * <p>OpenRewind records with the Lunar-style state engine; the old pixel-only
 * options (engine toggle, fixed-res capture) are gone.</p>
 */
public class GuiRewindSettings extends GuiScreen {

    private final GuiScreen parent;

    private static final int ID_FPS_DOWN = 1, ID_FPS_UP = 2;
    private static final int ID_RES_DOWN = 3, ID_RES_UP = 4;
    private static final int ID_QUALITY  = 5;
    private static final int ID_MIC      = 6;
    private static final int ID_SYS      = 7;
    private static final int ID_AUTOREC  = 8;
    private static final int ID_HUD      = 9;
    private static final int ID_BACKUP   = 10;
    private static final int ID_DONE     = 11;
    private static final int ID_OPEN_EDITOR = 12;

    private static final int[] RES_STEPS = {0, 480, 720, 1080, 1440, 2160};

    public GuiRewindSettings(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        int y  = 40;
        int row = 24;

        buttonList.add(new GuiButton(ID_FPS_DOWN, cx - 100, y, 20, 20, "-"));
        buttonList.add(new GuiButton(ID_FPS_UP,   cx + 80,  y, 20, 20, "+"));
        y += row;
        buttonList.add(new GuiButton(ID_RES_DOWN, cx - 100, y, 20, 20, "-"));
        buttonList.add(new GuiButton(ID_RES_UP,   cx + 80,  y, 20, 20, "+"));
        y += row;
        buttonList.add(new GuiButton(ID_QUALITY, cx - 100, y, 200, 20, qualityLabel()));
        y += row + 6;

        buttonList.add(new GuiButton(ID_MIC,    cx - 155, y, 150, 20, toggle("Microphone", RewindConfig.recordMicrophone)));
        buttonList.add(new GuiButton(ID_SYS,    cx + 5,   y, 150, 20, toggle("System audio", RewindConfig.recordSystemAudio)));
        y += row;
        buttonList.add(new GuiButton(ID_AUTOREC, cx - 155, y, 150, 20, toggle("Auto-record", RewindConfig.autoRecordOnJoin)));
        buttonList.add(new GuiButton(ID_HUD,     cx + 5,   y, 150, 20, toggle("Show HUD", RewindConfig.showHud)));
        y += row;
        buttonList.add(new GuiButton(ID_BACKUP,  cx - 155, y, 150, 20, toggle("Crash backup", RewindConfig.backupOnCrash)));
        buttonList.add(new GuiButton(ID_OPEN_EDITOR, cx + 5, y, 150, 20, "Open Editor \u2192"));
        y += row + 10;

        buttonList.add(new GuiButton(ID_DONE, cx - 100, y, 200, 20, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        switch (b.id) {
            case ID_FPS_DOWN: RewindConfig.videoFps = clamp(RewindConfig.videoFps - 5, 5, 120); break;
            case ID_FPS_UP:   RewindConfig.videoFps = clamp(RewindConfig.videoFps + 5, 5, 120); break;
            case ID_RES_DOWN: RewindConfig.maxResolution = stepRes(-1); break;
            case ID_RES_UP:   RewindConfig.maxResolution = stepRes(+1); break;
            case ID_QUALITY:
                RewindConfig.videoQuality = Math.round((RewindConfig.videoQuality + 0.25) % 1.25 * 100) / 100.0;
                if (RewindConfig.videoQuality < 0.25) RewindConfig.videoQuality = 0.25;
                b.displayString = qualityLabel();
                break;
            case ID_MIC:      RewindConfig.recordMicrophone  = !RewindConfig.recordMicrophone;  b.displayString = toggle("Microphone", RewindConfig.recordMicrophone); break;
            case ID_SYS:      RewindConfig.recordSystemAudio  = !RewindConfig.recordSystemAudio; b.displayString = toggle("System audio", RewindConfig.recordSystemAudio); break;
            case ID_AUTOREC:  RewindConfig.autoRecordOnJoin   = !RewindConfig.autoRecordOnJoin;  b.displayString = toggle("Auto-record", RewindConfig.autoRecordOnJoin); break;
            case ID_HUD:      RewindConfig.showHud            = !RewindConfig.showHud;           b.displayString = toggle("Show HUD", RewindConfig.showHud); break;
            case ID_BACKUP:   RewindConfig.backupOnCrash      = !RewindConfig.backupOnCrash;     b.displayString = toggle("Crash backup", RewindConfig.backupOnCrash); break;
            case ID_OPEN_EDITOR: mc.displayGuiScreen(new GuiRecordingList(this)); return;
            case ID_DONE:     RewindConfig.save(); mc.displayGuiScreen(parent); return;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "\u00A7lOpenRewind Settings", width / 2, 16, 0xFFFFFF);
        int cx = width / 2;
        int y = 40;
        drawCenteredString(fontRendererObj, "FPS: " + RewindConfig.videoFps, cx, y + 6, 0xFFFF55);
        y += 24;
        drawCenteredString(fontRendererObj, "Max res: " + resLabel(RewindConfig.maxResolution), cx, y + 6, 0xFFFF55);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        RewindConfig.save();
    }

    @Override
    public boolean doesGuiPauseGame() { return true; }

    // ---- helpers -----------------------------------------------------------
    private String qualityLabel() {
        String q = RewindConfig.videoQuality >= 0.9 ? "Best"
                 : RewindConfig.videoQuality >= 0.7 ? "High"
                 : RewindConfig.videoQuality >= 0.45 ? "Medium" : "Low";
        return "Quality: " + q;
    }

    private String resLabel(int r) { return r == 0 ? "Native" : r + "p"; }

    private int stepRes(int dir) {
        int idx = 0;
        for (int i = 0; i < RES_STEPS.length; i++) if (RES_STEPS[i] == RewindConfig.maxResolution) idx = i;
        idx = clamp(idx + dir, 0, RES_STEPS.length - 1);
        return RES_STEPS[idx];
    }

    private static String toggle(String name, boolean on) {
        return name + ": " + (on ? "\u00A7aON" : "\u00A7cOFF");
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
