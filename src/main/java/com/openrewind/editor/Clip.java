package com.openrewind.editor;

/**
 * One segment on the editor timeline. A clip references a source recording and
 * an in / out point (milliseconds). Multiple clips can be chained to cut out
 * boring parts or reorder highlights. Per-clip effects (speed / brightness /
 * saturation) are applied when the project is exported.
 */
public class Clip {

    /** Recording id (matches {@link com.openrewind.recording.RecordingMetadata#id}). */
    public String sourceId;
    /** Start of the kept range, in ms from the source's beginning. */
    public long inMs;
    /** End of the kept range, in ms. */
    public long outMs;

    // ---- per-clip effects (applied at export) ------------------------------
    /** Playback speed multiplier (0.25 = slow-mo, 2.0 = fast-forward). */
    public double speed = 1.0;
    /** Brightness offset, -1.0 (black) .. +1.0 (white). 0 = unchanged. */
    public double brightness = 0.0;
    /** Saturation multiplier, 0 = greyscale, 1 = unchanged, >1 = punchy. */
    public double saturation = 1.0;

    public Clip() { }

    public Clip(String sourceId, long inMs, long outMs) {
        this.sourceId = sourceId;
        this.inMs = inMs;
        this.outMs = outMs;
    }

    public long lengthMs() {
        return Math.max(0, outMs - inMs);
    }

    /** Output length after the speed effect is applied. */
    public long outputLengthMs() {
        return (long) (lengthMs() / Math.max(0.05, speed));
    }

    public boolean hasColorEffect() {
        return Math.abs(brightness) > 0.001 || Math.abs(saturation - 1.0) > 0.001;
    }
}
