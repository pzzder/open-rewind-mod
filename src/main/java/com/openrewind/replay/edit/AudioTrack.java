package com.openrewind.replay.edit;

/**
 * An imported external audio track (music / commentary) placed on the timeline,
 * mixed into the export alongside the replay's own audio side-track — Lunar's
 * "import media → audio track".
 */
public class AudioTrack {

    /** Absolute path to the imported .wav/.mp3/.ogg file. */
    public String path = "";
    /** Where on the timeline it starts (ms). */
    public long   offsetMs = 0;
    /** Linear gain. */
    public double gain = 1.0;

    public AudioTrack() { }
    public AudioTrack(String path, long offsetMs, double gain) {
        this.path = path; this.offsetMs = offsetMs; this.gain = gain;
    }
}
