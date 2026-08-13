package com.openrewind.recording;

/**
 * A single marker placed on the recording timeline. Markers are cheap
 * timestamp annotations the player drops mid-game ("clutch here!") that later
 * show up in the editor as jump points.
 */
public class Marker {

    /** Milliseconds from the start of the recording (excludes paused time). */
    public final long timeMs;
    /** Optional label; empty for a quick marker. */
    public final String label;

    public Marker(long timeMs, String label) {
        this.timeMs = timeMs;
        this.label  = label == null ? "" : label;
    }

    public Marker(long timeMs) {
        this(timeMs, "");
    }
}
