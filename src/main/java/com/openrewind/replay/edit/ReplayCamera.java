package com.openrewind.replay.edit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The replay camera model — the "Camera" panel of Lunar Rewind's editor:
 *
 * <ul>
 *   <li><b>Mode</b>: {@code SYNC} follows the recorded player (POV / first
 *       person); {@code FLIGHT} flies a free camera along {@link #keyframes}.</li>
 *   <li><b>FOV</b>, <b>speed</b> and <b>Force&nbsp;Hide&nbsp;HUD</b> mirror the
 *       exact toggles Lunar exposes.</li>
 * </ul>
 *
 * <p>{@link #poseAt(long)} returns the interpolated camera pose the exporter
 * applies each frame. Smooth (ease) interpolation uses a cubic smoothstep, which
 * is what gives cinematic camera paths their gentle acceleration.</p>
 */
public class ReplayCamera {

    public enum Mode { SYNC, FLIGHT }

    public Mode    mode    = Mode.SYNC;
    public float   fov     = 70f;
    public double  speed   = 1.0;   // playback speed multiplier
    public boolean hideHud = false; // "Force Hide HUD"

    public List<CameraKeyframe> keyframes = new ArrayList<CameraKeyframe>();

    /** Immutable pose returned to the exporter. */
    public static final class Pose {
        public final double x, y, z;
        public final float  yaw, pitch, fov;
        public Pose(double x, double y, double z, float yaw, float pitch, float fov) {
            this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch; this.fov = fov;
        }
    }

    public void addKeyframe(CameraKeyframe k) {
        keyframes.add(k);
        sort();
    }

    public void clearKeyframes() { keyframes.clear(); }

    public void sort() {
        Collections.sort(keyframes, new Comparator<CameraKeyframe>() {
            public int compare(CameraKeyframe a, CameraKeyframe b) {
                return Long.compare(a.timeMs, b.timeMs);
            }
        });
    }

    /**
     * Interpolated FLIGHT-mode pose at {@code timeMs}. Returns null in SYNC mode
     * or when there are no keyframes (the exporter then follows the player).
     */
    public Pose poseAt(long timeMs) {
        if (mode != Mode.FLIGHT || keyframes.isEmpty()) return null;
        if (keyframes.size() == 1 || timeMs <= keyframes.get(0).timeMs) {
            CameraKeyframe k = keyframes.get(0);
            return new Pose(k.x, k.y, k.z, k.yaw, k.pitch, k.fov);
        }
        CameraKeyframe last = keyframes.get(keyframes.size() - 1);
        if (timeMs >= last.timeMs) return new Pose(last.x, last.y, last.z, last.yaw, last.pitch, last.fov);

        for (int i = 0; i < keyframes.size() - 1; i++) {
            CameraKeyframe a = keyframes.get(i);
            CameraKeyframe b = keyframes.get(i + 1);
            if (timeMs >= a.timeMs && timeMs <= b.timeMs) {
                double raw = (double) (timeMs - a.timeMs) / Math.max(1, b.timeMs - a.timeMs);
                double t = a.interpolation == 0 ? raw : smoothstep(raw);
                return new Pose(
                        lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.z, b.z, t),
                        (float) lerpAngle(a.yaw, b.yaw, t),
                        (float) lerp(a.pitch, b.pitch, t),
                        (float) lerp(a.fov, b.fov, t));
            }
        }
        return null;
    }

    private static double smoothstep(double t) { return t * t * (3 - 2 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    /** Shortest-path angular interpolation (handles the -180/180 wrap). */
    private static double lerpAngle(float a, float b, double t) {
        double diff = ((b - a) % 360 + 540) % 360 - 180;
        return a + diff * t;
    }
}
