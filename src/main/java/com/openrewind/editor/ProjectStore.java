package com.openrewind.editor;

import com.openrewind.config.RewindConfig;
import com.openrewind.recording.RecordingMetadata;
import com.openrewind.util.JsonIO;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Filesystem-backed store for recordings and editor projects. Keeps the GUI
 * free of IO details.
 */
public final class ProjectStore {

    private ProjectStore() { }

    /** Load metadata for every recording that has a .json sidecar, newest first. */
    public static List<RecordingMetadata> listRecordings() {
        List<RecordingMetadata> out = new ArrayList<RecordingMetadata>();
        File dir = RewindConfig.getRecordingsDir();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                RecordingMetadata m = JsonIO.read(f, RecordingMetadata.class);
                if (m != null) out.add(m);
            }
        }
        out.sort(new Comparator<RecordingMetadata>() {
            public int compare(RecordingMetadata a, RecordingMetadata b) {
                return Long.compare(b.createdAt, a.createdAt);
            }
        });
        return out;
    }

    public static File videoFileFor(String id) {
        return new File(RewindConfig.getRecordingsDir(), id + ".mp4");
    }

    /** List saved projects, newest first. */
    public static List<Project> listProjects() {
        List<Project> out = new ArrayList<Project>();
        File dir = RewindConfig.getProjectsDir();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                Project p = JsonIO.read(f, Project.class);
                if (p != null) out.add(p);
            }
        }
        Collections.sort(out, new Comparator<Project>() {
            public int compare(Project a, Project b) {
                return Long.compare(b.modifiedAt, a.modifiedAt);
            }
        });
        return out;
    }

    public static void saveProject(Project p) {
        p.touch();
        File f = new File(RewindConfig.getProjectsDir(), sanitize(p.name) + ".json");
        JsonIO.write(f, p);
    }

    public static void deleteProject(Project p) {
        File f = new File(RewindConfig.getProjectsDir(), sanitize(p.name) + ".json");
        if (f.exists()) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }

    private static String sanitize(String s) {
        if (s == null || s.isEmpty()) return "project";
        return s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
