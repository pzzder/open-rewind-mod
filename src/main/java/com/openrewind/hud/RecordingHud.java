package com.openrewind.hud;

import com.openrewind.config.RewindConfig;
import com.openrewind.recording.RecordingManager;
import com.openrewind.replay.ReplayRecordingManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Locale;

/**
 * Draws the recording indicator overlay: a pulsing red dot, the elapsed timer
 * (mm:ss), a marker counter and short "toast" flash messages – the red circle +
 * timer Lunar Rewind shows in the top-right corner. It reflects whichever engine
 * is active (state-based by default, or the internal pixel fallback).
 */
public class RecordingHud extends Gui {

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!RewindConfig.showHud) return;
        // don't bake our own indicator into a captured frame (pixel offscreen pass)
        if (RecordingManager.HUD_SUPPRESSED) return;

        // ---- pull state from the active engine ------------------------------
        boolean recording, paused;
        long elapsed;
        int markerCount;
        String flash;
        boolean shadowRunning = false;
        double shadowSeconds = 0;

        if (RewindConfig.stateRecording) {
            ReplayRecordingManager s = ReplayRecordingManager.get();
            recording   = s.isRecording();
            paused      = s.isPaused();
            elapsed     = s.elapsedMs();
            markerCount = s.markerCount();
            flash       = s.getFlashMessage();
        } else {
            RecordingManager p = RecordingManager.get();
            recording   = p.isRecording();
            paused      = p.isPaused();
            elapsed     = p.elapsedMs();
            markerCount = p.markerCount();
            flash       = p.getFlashMessage();
            shadowRunning = p.isShadowRunning();
            shadowSeconds = p.shadowBufferedSeconds();
        }

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc);
        int sw = res.getScaledWidth();
        int y = 4;

        if (recording) {
            boolean blinkOn = paused || (System.currentTimeMillis() / 500) % 2 == 0;
            int dotColor = paused ? 0xFFFFAA00 : 0xFFFF3030;
            if (blinkOn) drawFilledCircle(sw - 62 + 3, y + 2 + 3, 3.5f, dotColor);

            String timer = formatTime(elapsed);
            if (paused) timer = "\u2016 " + timer;
            mc.fontRendererObj.drawStringWithShadow(timer, sw - 52, y, 0xFFFFFFFF);

            if (markerCount > 0) {
                String mk = "\u2691 " + markerCount;
                int mw = mc.fontRendererObj.getStringWidth(mk);
                mc.fontRendererObj.drawStringWithShadow(mk, sw - 4 - mw, y + 11, 0xFF66CCFF);
            }
        }

        if (flash != null) {
            int fw = mc.fontRendererObj.getStringWidth(flash);
            mc.fontRendererObj.drawStringWithShadow(flash, (sw - fw) / 2, 4, 0xFFFFFFFF);
        }

        if (!recording && shadowRunning) {
            String s = "\u25D0 SHADOW " + (int) shadowSeconds + "s";
            int stw = mc.fontRendererObj.getStringWidth(s);
            mc.fontRendererObj.drawStringWithShadow("\u00A78" + s, sw - 4 - stw, y, 0xFFAAAAAA);
        }
    }

    private static String formatTime(long ms) {
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.ROOT, "%02d:%02d", m, s);
    }

    private void drawFilledCircle(float cx, float cy, float r, int argb) {
        for (int dy = (int) -Math.ceil(r); dy <= r; dy++) {
            float dx = (float) Math.sqrt(Math.max(0, r * r - dy * dy));
            drawRect((int) (cx - dx), (int) (cy + dy), (int) (cx + dx), (int) (cy + dy + 1), argb);
        }
    }
}
