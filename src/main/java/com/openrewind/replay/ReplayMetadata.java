package com.openrewind.replay;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializable metadata describing a state-based replay, written as
 * {@code metadata.json} inside the replay container. Mirrors the essential
 * fields of ReplayMod's {@code .mcpr} metadata so the format is familiar and
 * self-describing.
 */
public class ReplayMetadata {

    /** Marker so tools can recognise the container. */
    public boolean singleplayer;
    /** Server address / world name the replay was recorded on. */
    public String  serverName;
    /** Total recorded duration in milliseconds. */
    public long    duration;
    /** Epoch millis when recording started. */
    public long    date;
    /** Minecraft version string (always 1.8.9 here). */
    public String  mcversion = "1.8.9";
    /** OpenRewind format generator tag. */
    public String  generator = "OpenRewind";
    /** Protocol version (47 = 1.8.x). */
    public int     protocol  = 47;
    /** File format revision. */
    public int     fileFormatVersion = 1;

    /** UUIDs of players seen in the recording (for the editor player list). */
    public List<String> players = new ArrayList<String>();

    /** Whether an audio side-track (mic/system WAV) accompanies this replay. */
    public boolean hasAudio;
    /** Recording frame rate hint used by the client-side camera track. */
    public int     cameraTrackHz = 60;

    /** Marker timestamps (ms from start) the player dropped mid-recording. */
    public List<Long> markers = new ArrayList<Long>();
}
