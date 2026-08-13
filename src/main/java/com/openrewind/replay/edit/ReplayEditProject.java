package com.openrewind.replay.edit;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-replay edit settings for the state-engine editor — the full timeline model
 * matching Lunar Rewind's editor: camera path/mode, keyframed property tracks
 * (brightness / saturation / zoom / FOV / volume over time), overlay layers
 * (text / image), imported audio tracks, effect flags, and export options.
 * Persisted as {@code <id>.orwr.edit.json}.
 */
public class ReplayEditProject {

    /** Replay id (the .orwr base name). */
    public String replayId;

    // ---- camera (Camera panel) ---------------------------------------------
    public ReplayCamera camera = new ReplayCamera();

    // ---- keyframed property tracks (per-property keyframes) ----------------
    // A track with no keyframes behaves as a constant = its defaultValue, so the
    // editor's +/- steppers edit defaultValue and "Add keyframe" adds a curve.
    public PropertyTrack brightness   = new PropertyTrack("brightness", 0.0);
    public PropertyTrack saturation   = new PropertyTrack("saturation", 1.0);
    public PropertyTrack zoom         = new PropertyTrack("zoom", 1.0);
    public PropertyTrack fov          = new PropertyTrack("fov", 70.0);
    public PropertyTrack masterVolume = new PropertyTrack("volume", 1.0);

    /** Static camera-shake amplitude (deg); 0 = off. */
    public double cameraShake = 0.0;

    // ---- effect flags (Effects tab) ----------------------------------------
    public enum EffectType { CHROMA_KEY, VIGNETTE, SHADER_OVERLAY, RENDER_SCOREBOARD, RENDER_BOSSBAR }
    /** Enabled effect names (EffectType.name()). */
    public List<String> effects = new ArrayList<String>();
    /** Chroma-key target colour (ARGB) when CHROMA_KEY enabled. */
    public int chromaColor = 0xFF00FF00;

    // ---- timeline layers / tracks ------------------------------------------
    public List<OverlayLayer> overlays    = new ArrayList<OverlayLayer>();
    public List<AudioTrack>   audioTracks = new ArrayList<AudioTrack>();

    // ---- export settings (Export tab) --------------------------------------
    public int    exportFps    = 60;
    public int    exportHeight = 1080;   // width follows aspect
    public String orientation  = "Horizontal";
    public String format       = "mp4";
    public String encoder      = "software"; // software (JCodec) / ffmpeg

    public ReplayEditProject() { }
    public ReplayEditProject(String replayId) { this.replayId = replayId; }

    public boolean hasEffect(EffectType t) { return effects.contains(t.name()); }
    public void toggleEffect(EffectType t) {
        if (!effects.remove(t.name())) effects.add(t.name());
    }
}
