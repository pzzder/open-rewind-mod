package com.openrewind.config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

/**
 * Central configuration for OpenRewind, backed by Forge's {@link Configuration}
 * (a plain .cfg file in .minecraft/config/openrewind.cfg).
 *
 * <p>All values are read once at load and can be re-saved from the in-game
 * settings screen via {@link #save()}.</p>
 */
public final class RewindConfig {

    private RewindConfig() { }

    private static Configuration config;

    // ---- Video -------------------------------------------------------------
    /** Target frames per second written into the output video. */
    public static int   videoFps          = 30;
    /** 0 = match window size, otherwise scale the longest edge to this value. */
    public static int   maxResolution     = 1080;
    /** JCodec quality 0.0 (small) .. 1.0 (best). */
    public static double videoQuality     = 0.75D;
    /** Hard cap on recording length (seconds) to avoid filling the disk. */
    public static int   maxRecordSeconds  = 600;

    /**
     * Offscreen capture: re-render each frame into an off-screen framebuffer at
     * {@link #captureHeight} instead of reading the window. Makes the output
     * resolution independent of the window size (like Lunar Rewind) — play in a
     * tiny window and still export true 1080p. Costs an extra render pass per
     * captured frame (heavy on GPU/CPU).
     */
    public static boolean offscreenCapture = true;
    /** Target vertical resolution for offscreen capture; width follows window aspect. */
    public static int     captureHeight    = 1080;

    /**
     * When the export FPS is higher than the source's captured FPS, synthesise
     * genuine in-between frames by blending the two bracketing source frames
     * (motion interpolation) instead of duplicating frames. Makes 60→120 export
     * actually smoother. Off = frames are duplicated (bit-for-bit, no blur).
     */
    /**
     * Use the state-based replay engine (records the network packet stream +
     * camera + GUI track, like Lunar Rewind / ReplayMod) instead of pixel
     * capture. Near-zero recording cost; frames are re-rendered at export time.
     */
    public static boolean stateRecording = true;

    /**
     * Record your own block placements so the exporter can show them at the
     * instant you placed them (client prediction) instead of one ping late —
     * fixes "bridging blocks appear delayed" in PvP replays.
     */
    public static boolean predictOwnBlocks = true;

    // ---- Audio -------------------------------------------------------------
    public static boolean recordMicrophone = false;
    public static boolean recordSystemAudio = true;
    /** Microphone gain multiplier applied before mixing. */
    public static double  micGain          = 1.0D;

    // ---- Behaviour ---------------------------------------------------------
    public static boolean autoRecordOnJoin    = false;
    public static boolean continueAcrossWorlds = true;
    public static boolean backupOnCrash        = true;
    /** Show the red-dot + timer overlay while recording. */
    public static boolean showHud              = true;

    // ---- Shadow Rewind (rolling replay buffer) -----------------------------
    /** Continuously buffer the last N seconds so you can save a clip you forgot to record. */
    public static boolean shadowEnabled     = false;
    /** Length of the rolling buffer in seconds (Lunar presets: 45 / 90 / 180). */
    public static int     shadowSeconds     = 45;
    /** Longest edge (px) the shadow buffer keeps – smaller = less RAM. */
    public static int     shadowResolution  = 720;
    /** Shadow buffer capture fps. */
    public static int     shadowFps         = 30;
    /** Soft RAM budget (MB) for the shadow buffer; oldest frames evicted past it. */
    public static int     shadowMemoryMb    = 512;

    // ---- Storage -----------------------------------------------------------
    /** Sub-folder of .minecraft where recordings & projects live. */
    public static String  outputFolder = "openrewind";

    private static File   gameDir;

    // ------------------------------------------------------------------------

    public static void load(File file) {
        config = new Configuration(file);
        gameDir = file.getParentFile().getParentFile(); // config/ -> .minecraft/
        sync();
    }

    /** Read values from disk into the static fields (creates defaults). */
    public static void sync() {
        config.load();

        videoFps         = config.getInt("videoFps", "video", 30, 5, 120,
                "Frames per second written to the exported video.");
        maxResolution    = config.getInt("maxResolution", "video", 1080, 240, 3840,
                "Longest edge of the recorded frame in pixels (0 = native window size).");
        videoQuality     = config.get("video", "videoQuality", 0.75D,
                "H.264 quality, 0.0 (smallest) to 1.0 (best).").getDouble();
        maxRecordSeconds = config.getInt("maxRecordSeconds", "video", 600, 10, 7200,
                "Automatically stop after this many seconds.");
        offscreenCapture = config.getBoolean("offscreenCapture", "video", true,
                "Re-render each frame off-screen at 'captureHeight' so output resolution is "
                + "independent of the game window size (like Lunar Rewind). Extra render pass per frame.");
        captureHeight    = config.getInt("captureHeight", "video", 1080, 240, 2160,
                "Target vertical resolution for offscreen capture; width follows the window aspect.");
        stateRecording   = config.getBoolean("stateRecording", "engine", true,
                "Use the state-based replay engine (packet stream + camera, like Lunar/ReplayMod) "
                + "instead of pixel capture. Near-zero recording cost; frames re-rendered at export.");
        predictOwnBlocks = config.getBoolean("predictOwnBlocks", "engine", true,
                "Record your own block placements and show them at placement time on playback "
                + "(client prediction), fixing bridging blocks appearing one ping late.");

        recordMicrophone  = config.getBoolean("recordMicrophone", "audio", false,
                "Capture the default microphone while recording.");
        recordSystemAudio = config.getBoolean("recordSystemAudio", "audio", true,
                "Capture the game / system audio (requires a loopback device on some OSes).");
        micGain           = config.get("audio", "micGain", 1.0D,
                "Linear gain applied to the microphone signal.").getDouble();

        autoRecordOnJoin     = config.getBoolean("autoRecordOnJoin", "behaviour", false,
                "Start a recording automatically when joining a world / server.");
        continueAcrossWorlds = config.getBoolean("continueAcrossWorlds", "behaviour", true,
                "Keep a single recording running when switching worlds / servers.");
        backupOnCrash        = config.getBoolean("backupOnCrash", "behaviour", true,
                "Flush the encoder on an unexpected shutdown so footage is not lost.");
        showHud              = config.getBoolean("showHud", "behaviour", true,
                "Show the recording indicator overlay.");

        shadowEnabled    = config.getBoolean("shadowEnabled", "shadow", false,
                "Continuously buffer recent gameplay so you can save a clip after the fact.");
        shadowSeconds    = config.getInt("shadowSeconds", "shadow", 45, 10, 240,
                "How many seconds of gameplay the Shadow Rewind buffer keeps.");
        shadowResolution = config.getInt("shadowResolution", "shadow", 720, 240, 1440,
                "Longest edge (px) the shadow buffer stores – lower saves RAM.");
        shadowFps        = config.getInt("shadowFps", "shadow", 30, 5, 60,
                "Shadow buffer capture frame rate.");
        shadowMemoryMb   = config.getInt("shadowMemoryMb", "shadow", 512, 128, 4096,
                "Soft RAM budget (MB) for the shadow buffer.");

        outputFolder = config.getString("outputFolder", "storage", "openrewind",
                "Folder (under .minecraft) for recordings and editor projects.");

        if (config.hasChanged()) {
            config.save();
        }
    }

    /** Persist the current static field values back to disk. */
    public static void save() {
        config.get("video",     "videoFps",          30).set(videoFps);
        config.get("video",     "maxResolution",      1080).set(maxResolution);
        config.get("video",     "videoQuality",       0.75D).set(videoQuality);
        config.get("video",     "maxRecordSeconds",   600).set(maxRecordSeconds);
        config.get("video",     "offscreenCapture",   true).set(offscreenCapture);
        config.get("video",     "captureHeight",      1080).set(captureHeight);
        config.get("engine",    "stateRecording",     true).set(stateRecording);
        config.get("engine",    "predictOwnBlocks",   true).set(predictOwnBlocks);
        config.get("audio",     "recordMicrophone",   false).set(recordMicrophone);
        config.get("audio",     "recordSystemAudio",  true).set(recordSystemAudio);
        config.get("audio",     "micGain",            1.0D).set(micGain);
        config.get("behaviour", "autoRecordOnJoin",   false).set(autoRecordOnJoin);
        config.get("behaviour", "continueAcrossWorlds", true).set(continueAcrossWorlds);
        config.get("behaviour", "backupOnCrash",      true).set(backupOnCrash);
        config.get("behaviour", "showHud",            true).set(showHud);
        config.get("shadow",    "shadowEnabled",      false).set(shadowEnabled);
        config.get("shadow",    "shadowSeconds",      45).set(shadowSeconds);
        config.get("shadow",    "shadowResolution",   720).set(shadowResolution);
        config.get("shadow",    "shadowFps",          30).set(shadowFps);
        config.get("shadow",    "shadowMemoryMb",     512).set(shadowMemoryMb);
        config.get("storage",   "outputFolder",       "openrewind").set(outputFolder);
        config.save();
    }

    /** Root directory (.minecraft/openrewind) that holds recordings & projects. */
    public static File getOutputRoot() {
        File root = new File(gameDir, outputFolder);
        if (!root.exists()) {
            //noinspection ResultOfMethodCallIgnored
            root.mkdirs();
        }
        return root;
    }

    public static File getRecordingsDir() {
        File d = new File(getOutputRoot(), "recordings");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File getProjectsDir() {
        File d = new File(getOutputRoot(), "projects");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File getExportsDir() {
        File d = new File(getOutputRoot(), "exports");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Directory holding state-based .orwr replay containers. */
    public static File getReplaysDir() {
        File d = new File(getOutputRoot(), "replays");
        if (!d.exists()) d.mkdirs();
        return d;
    }
}
