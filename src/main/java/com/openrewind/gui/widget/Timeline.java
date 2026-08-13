package com.openrewind.gui.widget;

import net.minecraft.client.gui.Gui;

/**
 * A draggable trim timeline widget. Renders a horizontal track representing the
 * full source duration, an in-point and out-point handle, marker ticks, and a
 * playhead. All times are in milliseconds.
 *
 * <p>The owning screen forwards mouse events and reads back {@link #getInMs()} /
 * {@link #getOutMs()} to update the current clip.</p>
 */
public class Timeline extends Gui {

    public int x, y, width, height;
    public long durationMs;
    public long[] markerMs = new long[0];

    private long inMs;
    private long outMs;
    private long playheadMs;

    /** 0 = none, 1 = in handle, 2 = out handle, 3 = scrub. */
    private int dragging = 0;

    private static final int HANDLE_W = 5;

    public Timeline(int x, int y, int width, int height, long durationMs) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        this.durationMs = Math.max(1, durationMs);
        this.inMs = 0;
        this.outMs = this.durationMs;
    }

    public long getInMs()  { return inMs;  }
    public long getOutMs() { return outMs; }
    public long getPlayheadMs() { return playheadMs; }

    public void setRange(long in, long out) {
        this.inMs = clamp(in, 0, durationMs);
        this.outMs = clamp(out, inMs + 1, durationMs);
    }

    // ---- coordinate helpers ------------------------------------------------
    private int timeToX(long ms) {
        return x + (int) ((double) ms / durationMs * width);
    }
    private long xToTime(int px) {
        return clamp((long) ((double) (px - x) / width * durationMs), 0, durationMs);
    }

    // ---- input -------------------------------------------------------------
    public void mouseClicked(int mx, int my) {
        if (my < y - 4 || my > y + height + 4) return;
        int inX  = timeToX(inMs);
        int outX = timeToX(outMs);
        if (Math.abs(mx - inX) <= HANDLE_W + 2) {
            dragging = 1;
        } else if (Math.abs(mx - outX) <= HANDLE_W + 2) {
            dragging = 2;
        } else if (mx >= x && mx <= x + width) {
            dragging = 3;
            playheadMs = xToTime(mx);
        }
    }

    public void mouseDragged(int mx, int my) {
        switch (dragging) {
            case 1: inMs  = clamp(xToTime(mx), 0, outMs - 1); break;
            case 2: outMs = clamp(xToTime(mx), inMs + 1, durationMs); break;
            case 3: playheadMs = xToTime(mx); break;
            default: break;
        }
    }

    public void mouseReleased() { dragging = 0; }

    // ---- render ------------------------------------------------------------
    public void draw() {
        // track background
        drawRect(x, y, x + width, y + height, 0xFF202020);
        // kept range
        drawRect(timeToX(inMs), y, timeToX(outMs), y + height, 0x804488FF);

        // markers
        for (long m : markerMs) {
            int mxp = timeToX(m);
            drawRect(mxp, y - 3, mxp + 1, y + height + 3, 0xFF66CCFF);
        }

        // in / out handles
        drawHandle(timeToX(inMs),  0xFF44FF44);
        drawHandle(timeToX(outMs), 0xFFFF4444);

        // playhead
        int px = timeToX(playheadMs);
        drawRect(px, y - 2, px + 1, y + height + 2, 0xFFFFFFFF);
    }

    private void drawHandle(int hx, int color) {
        drawRect(hx - HANDLE_W / 2, y - 3, hx + HANDLE_W / 2 + 1, y + height + 3, color);
    }

    private static long clamp(long v, long lo, long hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
