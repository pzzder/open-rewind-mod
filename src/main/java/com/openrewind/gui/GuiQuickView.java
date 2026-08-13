package com.openrewind.gui;

import com.openrewind.config.RewindConfig;
import com.openrewind.editor.VideoPlayer;
import com.openrewind.recording.RecordingMetadata;
import com.openrewind.util.GlFrameTexture;
import com.openrewind.util.JsonIO;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Quick View – play a recording straight through without creating an edit
 * project, mirroring Lunar Rewind's "Quick View" option. Play / pause, scrub,
 * ±5 s skip, and a speed toggle.
 */
public class GuiQuickView extends GuiScreen {

    private final GuiScreen parent;
    private final RecordingMetadata meta;
    private VideoPlayer player;
    private final GlFrameTexture texture = new GlFrameTexture();

    private int barX, barY, barW;

    private static final int ID_PLAY = 1, ID_BACK = 2, ID_BACK5 = 3, ID_FWD5 = 4, ID_SPEED = 5;

    public GuiQuickView(GuiScreen parent, RecordingMetadata meta) {
        this.parent = parent;
        this.meta = meta;
    }

    @Override
    public void initGui() {
        File mp4 = new File(RewindConfig.getRecordingsDir(), meta.id + ".mp4");
        if (player == null) {
            player = new VideoPlayer(mp4, meta.fps > 0 ? meta.fps : 30, meta.durationMs);
            player.open();
            player.seek(0);
        }
        buttonList.clear();
        int cy = height - 24;
        buttonList.add(new GuiButton(ID_BACK5, width / 2 - 130, cy, 40, 20, "-5s"));
        buttonList.add(new GuiButton(ID_PLAY,  width / 2 - 85,  cy, 70, 20, "Pause"));
        buttonList.add(new GuiButton(ID_FWD5,  width / 2 - 10,  cy, 40, 20, "+5s"));
        buttonList.add(new GuiButton(ID_SPEED, width / 2 + 35,  cy, 55, 20, "1.0x"));
        buttonList.add(new GuiButton(ID_BACK,  width - 60, 4, 56, 20, "Back"));

        barX = 40; barW = width - 80; barY = height - 34;
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        switch (b.id) {
            case ID_PLAY:  player.togglePlay(); b.displayString = player.isPlaying() ? "Pause" : "Play"; break;
            case ID_BACK5: player.skip(-5000); break;
            case ID_FWD5:  player.skip(5000); break;
            case ID_SPEED:
                double[] sp = {0.5, 1.0, 2.0, 4.0};
                int idx = 0;
                for (int i = 0; i < sp.length; i++) if (Math.abs(sp[i] - player.getSpeed()) < 0.01) idx = i;
                double ns = sp[(idx + 1) % sp.length];
                player.setSpeed(ns);
                b.displayString = ns + "x";
                break;
            case ID_BACK:
                if (player != null) player.close();
                texture.dispose();
                mc.displayGuiScreen(parent);
                break;
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        super.mouseClicked(mx, my, btn);
        if (my >= barY - 4 && my <= barY + 8 && mx >= barX && mx <= barX + barW) {
            long t = (long) ((double) (mx - barX) / barW * meta.durationMs);
            player.seek(t);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        // upload + draw current frame, letterboxed into the top area
        if (player != null && player.getCurrentFrame() != null) {
            texture.upload(player.getCurrentFrame());
            int availW = width - 20, availH = height - 70;
            int fw = texture.getWidth(), fh = texture.getHeight();
            double scale = Math.min((double) availW / fw, (double) availH / fh);
            int dw = (int) (fw * scale), dh = (int) (fh * scale);
            int dx = (width - dw) / 2, dy = 10 + (availH - dh) / 2;
            drawRect(dx - 1, dy - 1, dx + dw + 1, dy + dh + 1, 0xFF000000);
            texture.drawScaled(dx, dy, dw, dh);
        } else {
            drawCenteredString(fontRendererObj, "\u00A77Buffering\u2026", width / 2, height / 2, 0xAAAAAA);
        }

        // progress bar
        drawRect(barX, barY, barX + barW, barY + 4, 0xFF303030);
        long pos = player != null ? player.getPositionMs() : 0;
        int fillW = (int) ((double) pos / meta.durationMs * barW);
        drawRect(barX, barY, barX + fillW, barY + 4, 0xFF44AAFF);
        drawString(fontRendererObj, fmt(pos) + " / " + fmt(meta.durationMs), barX, barY - 12, 0xFFFFFF);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        if (player != null) player.close();
        texture.dispose();
    }

    static RecordingMetadata loadMeta(String id) {
        return JsonIO.read(new File(RewindConfig.getRecordingsDir(), id + ".json"), RecordingMetadata.class);
    }

    private static String fmt(long ms) {
        long s = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    @Override
    public boolean doesGuiPauseGame() { return true; }
}
