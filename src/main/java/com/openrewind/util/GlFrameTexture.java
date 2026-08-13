package com.openrewind.util;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

/**
 * A mutable OpenGL texture used to display decoded video frames inside a
 * {@code GuiScreen}. Upload a {@link BufferedImage} with {@link #upload}, then
 * {@link #drawScaled} it into a rectangle. All calls must be made on the render
 * thread (GL context is thread-local).
 */
public class GlFrameTexture {

    private int texId = -1;
    private int texW = 0, texH = 0;
    private ByteBuffer buf;

    public int getWidth()  { return texW; }
    public int getHeight() { return texH; }
    public boolean isReady() { return texId != -1; }

    /** Push a frame into the texture, (re)allocating GL storage if the size changed. */
    public void upload(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);

        if (buf == null || texW != w || texH != h) {
            buf = BufferUtils.createByteBuffer(w * h * 4);
        }
        buf.clear();
        for (int p : pixels) {
            buf.put((byte) ((p >> 16) & 0xFF)); // R
            buf.put((byte) ((p >> 8) & 0xFF));  // G
            buf.put((byte) (p & 0xFF));         // B
            buf.put((byte) 0xFF);               // A
        }
        buf.flip();

        if (texId == -1) {
            texId = GL11.glGenTextures();
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        if (texW != w || texH != h) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            texW = w; texH = h;
        } else {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, w, h,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        }
    }

    /** Draw the texture into the given screen rectangle (immediate mode). */
    public void drawScaled(int x, int y, int w, int h) {
        if (texId == -1) return;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(x,     y);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(x,     y + h);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(x + w, y);
        GL11.glEnd();
    }

    public void dispose() {
        if (texId != -1) {
            GL11.glDeleteTextures(texId);
            texId = -1;
        }
    }
}
