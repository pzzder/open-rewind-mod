package com.openrewind.editor;

import java.util.ArrayList;
import java.util.List;

/**
 * An editor project: an ordered list of {@link Clip}s plus export settings.
 * Serialized to JSON in .minecraft/openrewind/projects so work can be resumed.
 */
public class Project {

    public String name;
    public long   createdAt;
    public long   modifiedAt;

    public List<Clip> clips = new ArrayList<Clip>();

    // export settings (fall back to the source recording's values if <= 0)
    public int    exportFps       = 30;
    public int    exportWidth     = 0;   // 0 = keep source
    public int    exportHeight    = 0;
    public double masterVolume    = 1.0D;
    public double micVolume       = 1.0D;
    public double systemVolume    = 1.0D;

    // Create-Project options (Lunar parity)
    public String  resolutionPreset = "Source";    // 480p/720p/1080p/1440p/Source
    public String  orientation      = "Horizontal"; // Horizontal / Vertical
    public boolean audioMono        = false;        // Stereo (false) / Mono (true)
    public String  exportFormat     = "mp4";        // container
    public String  exportCodec      = "H.264";      // informational / ffmpeg hint
    public String  exportEncoder    = "software";   // software / ffmpeg / nvenc

    public Project() { }

    public Project(String name) {
        this.name = name;
        this.createdAt = this.modifiedAt = System.currentTimeMillis();
    }

    public long totalLengthMs() {
        long total = 0;
        for (Clip c : clips) total += c.lengthMs();
        return total;
    }
    /** Total length of the finished export (after per-clip speed effects). */
    public long totalOutputLengthMs() {
        long total = 0;
        for (Clip c : clips) total += c.outputLengthMs();
        return total;
    }

    public void touch() {
        modifiedAt = System.currentTimeMillis();
    }
}
