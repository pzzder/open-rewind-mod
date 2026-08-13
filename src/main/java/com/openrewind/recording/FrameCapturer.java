package com.openrewind.recording;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/**
 * Reads the rendered frame back from the OpenGL back-buffer.
 *
 * <p>This runs on the render thread (must – GL context is thread-local), so we
 * keep the work minimal: a single {@code glReadPixels} into a reusable direct
 * buffer, then a copy into a plain {@code byte[]} that gets handed to the
 * encoder thread. The vertical flip and colour-space conversion (RGBA -> YUV)
 * happen off-thread in {@link VideoEncoder}.</p>
 */
public class FrameCapturer {

    private final int width;
    private final int height;
    private final ByteBuffer pixelBuffer; // direct buffer reused every frame

    public FrameCapturer(int width, int height) {
        this.width  = width;
        this.height = height;
        this.pixelBuffer = BufferUtils.createByteBuffer(width * height * 4);
    }

    public int getWidth()  { return width;  }
    public int getHeight() { return height; }

    /**
     * Grab the current back-buffer. MUST be called on the render thread, ideally
     * at {@code RenderTickEvent.Phase.END} before the buffers are swapped.
     *
     * @return a raw RGBA byte array, bottom-up (GL origin is bottom-left).
     */
    public byte[] grab() {
        pixelBuffer.clear();

        // make sure everything is drawn before we read it back
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadBuffer(GL11.GL_BACK);
        GL11.glReadPixels(0, 0, width, height,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixelBuffer);

        byte[] rgba = new byte[width * height * 4];
        pixelBuffer.get(rgba);
        return rgba;
    }
}
