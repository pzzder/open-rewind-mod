package com.openrewind.replay.edit;

/**
 * A single camera keyframe on the replay timeline: the camera pose (position +
 * look + FOV) at a given time. The exporter interpolates between consecutive
 * keyframes to fly the camera along a smooth path — the core of Lunar Rewind's
 * Flight-mode cinematic camera.
 */
public class CameraKeyframe {

    /** Milliseconds from the start of the replay. */
    public long   timeMs;
    public double x, y, z;
    public float  yaw, pitch;
    public float  fov = 70f;

    /** Interpolation into the NEXT keyframe: 0 = linear, 1 = smooth (ease). */
    public int    interpolation = 1;

    public CameraKeyframe() { }

    public CameraKeyframe(long timeMs, double x, double y, double z,
                          float yaw, float pitch, float fov) {
        this.timeMs = timeMs;
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch; this.fov = fov;
    }
}
