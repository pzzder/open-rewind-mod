package com.openrewind.recording;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializable metadata for a finished recording. Persisted next to the .mp4
 * as a JSON sidecar (see {@link com.openrewind.util.JsonIO}) so the editor can
 * list recordings without decoding the whole video.
 */
public class RecordingMetadata {

    /** Base file name (no extension), also the recording id. */
    public String id;
    /** Human friendly title shown in the editor list. */
    public String title;
    /** Epoch millis when recording started. */
    public long   createdAt;
    /** Duration in milliseconds. */
    public long   durationMs;
    public int    width;
    public int    height;
    public int    fps;
    public boolean hasMicrophone;
    public boolean hasSystemAudio;
    /** Marker timestamps (ms) + labels. */
    public List<MarkerDTO> markers = new ArrayList<MarkerDTO>();

    public static class MarkerDTO {
        public long   timeMs;
        public String label;
        public MarkerDTO() { }
        public MarkerDTO(long t, String l) { this.timeMs = t; this.label = l; }
    }

    public void addMarker(Marker m) {
        markers.add(new MarkerDTO(m.timeMs, m.label));
    }
}
