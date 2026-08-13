package com.openrewind.replay.edit;

import com.openrewind.config.RewindConfig;
import com.openrewind.replay.ReplayFile;
import com.openrewind.replay.ReplayMetadata;
import com.openrewind.util.JsonIO;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Lists recorded {@code .orwr} replays and loads / saves their
 * {@link ReplayEditProject} sidecars.
 */
public final class ReplayStore {

    private ReplayStore() { }

    public static final class Entry {
        public final File file;
        public final ReplayMetadata meta;
        public Entry(File f, ReplayMetadata m) { this.file = f; this.meta = m; }
    }

    /** All replays, newest first. */
    public static List<Entry> listReplays() {
        List<Entry> out = new ArrayList<Entry>();
        File dir = RewindConfig.getReplaysDir();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".orwr"));
        if (files != null) {
            for (File f : files) {
                ReplayMetadata m = ReplayFile.readMetadata(f);
                if (m != null) out.add(new Entry(f, m));
            }
        }
        Collections.sort(out, new Comparator<Entry>() {
            public int compare(Entry a, Entry b) { return Long.compare(b.meta.date, a.meta.date); }
        });
        return out;
    }

    private static File sidecar(File replay) {
        return new File(replay.getParentFile(), replay.getName() + ".edit.json");
    }

    public static ReplayEditProject loadOrCreate(File replay) {
        ReplayEditProject p = JsonIO.read(sidecar(replay), ReplayEditProject.class);
        if (p == null) {
            p = new ReplayEditProject(replay.getName().replace(".orwr", ""));
        }
        if (p.camera == null) p.camera = new ReplayCamera();
        if (p.brightness == null)   p.brightness   = new PropertyTrack("brightness", 0.0);
        if (p.saturation == null)   p.saturation   = new PropertyTrack("saturation", 1.0);
        if (p.zoom == null)         p.zoom         = new PropertyTrack("zoom", 1.0);
        if (p.fov == null)          p.fov          = new PropertyTrack("fov", 70.0);
        if (p.masterVolume == null) p.masterVolume = new PropertyTrack("volume", 1.0);
        if (p.effects == null)      p.effects      = new java.util.ArrayList<String>();
        if (p.overlays == null)     p.overlays     = new java.util.ArrayList<OverlayLayer>();
        if (p.audioTracks == null)  p.audioTracks  = new java.util.ArrayList<AudioTrack>();
        return p;
    }

    public static void save(File replay, ReplayEditProject project) {
        JsonIO.write(sidecar(replay), project);
    }
}
