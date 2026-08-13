package com.openrewind.replay.edit;

/**
 * A text or image overlay placed on a time range of the timeline — Lunar
 * Rewind's text / image layers. Rendered on top of the video during export.
 */
public class OverlayLayer {

    public enum Type { TEXT, IMAGE }

    public Type   type = Type.TEXT;
    /** For TEXT: the string to draw. For IMAGE: absolute file path. */
    public String content = "";
    /** Normalised position 0..1 (fraction of width/height). */
    public double x = 0.5, y = 0.1;
    /** Scale multiplier. */
    public double scale = 1.0;
    /** ARGB colour (text). */
    public int    color = 0xFFFFFFFF;
    /** Visible time range (ms). */
    public long   startMs = 0, endMs = Long.MAX_VALUE;

    public boolean visibleAt(long t) { return t >= startMs && t <= endMs; }
}
