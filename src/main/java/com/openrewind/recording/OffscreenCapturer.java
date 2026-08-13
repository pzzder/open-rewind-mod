package com.openrewind.recording;

import com.openrewind.OpenRewind;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/**
 * Renders the game into Minecraft's framebuffer at an arbitrary target
 * resolution and reads the pixels back, decoupling the recorded resolution from
 * the actual window size.
 *
 * <p>This is the technique high-resolution screenshot mods use: temporarily set
 * {@code displayWidth/Height} to the target, resize the main framebuffer, invoke
 * an extra {@link net.minecraft.client.renderer.EntityRenderer#updateCameraAndRender}
 * pass so the whole world + HUD is rasterised at the target resolution, read the
 * framebuffer texture, then restore the original size.</p>
 *
 * <p><b>Cost:</b> a full second render pass per captured frame. It's what makes
 * "record in a tiny window, export 1080p" possible (like Lunar Rewind, which
 * gets it for free by re-rendering from game state), but it is GPU/CPU heavy —
 * hence it is an opt-in toggle.</p>
 *
 * <p>All methods MUST run on the render thread.</p>
 */
public class OffscreenCapturer {

    private ByteBuffer pixelBuffer;
    private int bufW = 0, bufH = 0;

    /** Whether offscreen capture is possible on this machine (needs FBO support). */
    public static boolean isSupported() {
        return OpenGlHelper.isFramebufferEnabled();
    }

    private void ensureBuffer(int w, int h) {
        if (pixelBuffer == null || bufW != w || bufH != h) {
            pixelBuffer = BufferUtils.createByteBuffer(w * h * 4);
            bufW = w; bufH = h;
        }
    }

    /**
     * Render one frame at (targetW, targetH) and return it as bottom-up RGBA
     * (same layout as {@link FrameCapturer#grab}). Returns {@code null} if the
     * offscreen pass could not be performed (caller should fall back to a plain
     * window grab).
     */
    public byte[] renderAndGrab(int targetW, int targetH, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        Framebuffer fb = mc.getFramebuffer();
        if (fb == null || !isSupported()) return null;

        int oldW = mc.displayWidth;
        int oldH = mc.displayHeight;
        if (targetW <= 0 || targetH <= 0) return null;

        try {
            // ---- switch to target size ----------------------------------
            mc.displayWidth  = targetW;
            mc.displayHeight = targetH;
            fb.createBindFramebuffer(targetW, targetH);
            if (mc.entityRenderer.getShaderGroup() != null) {
                mc.entityRenderer.updateShaderGroupSize(targetW, targetH);
            }

            // ---- render the whole frame at the target resolution ---------
            fb.bindFramebuffer(true);
            GL11.glViewport(0, 0, targetW, targetH);
            RecordingManager.HUD_SUPPRESSED = true;   // keep our rec-indicator out of the capture
            try {
                mc.entityRenderer.updateCameraAndRender(partialTicks, System.nanoTime());
            } finally {
                RecordingManager.HUD_SUPPRESSED = false;
            }

            // ---- read the framebuffer colour attachment ------------------
            ensureBuffer(targetW, targetH);
            pixelBuffer.clear();
            fb.bindFramebuffer(false);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, targetW, targetH,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixelBuffer);

            byte[] rgba = new byte[targetW * targetH * 4];
            pixelBuffer.get(rgba);
            return rgba;
        } catch (Throwable t) {
            OpenRewind.logger.error("[OpenRewind] offscreen render failed, "
                    + "falling back to window capture", t);
            return null;
        } finally {
            // ---- always restore the original window size -----------------
            mc.displayWidth  = oldW;
            mc.displayHeight = oldH;
            try {
                fb.createBindFramebuffer(oldW, oldH);
                if (mc.entityRenderer.getShaderGroup() != null) {
                    mc.entityRenderer.updateShaderGroupSize(oldW, oldH);
                }
                fb.bindFramebuffer(true);
                GL11.glViewport(0, 0, oldW, oldH);
            } catch (Throwable ignored) { }
        }
    }
}
