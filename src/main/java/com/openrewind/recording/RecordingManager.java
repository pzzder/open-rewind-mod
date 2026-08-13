package com.openrewind.recording;

import com.openrewind.OpenRewind;
import com.openrewind.config.RewindConfig;
import com.openrewind.util.JsonIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The heart of OpenRewind: a singleton that owns the current recording state
 * and drives the capture pipeline from Forge's render / client tick events.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Start / stop / pause / resume a recording.</li>
 *   <li>On every render frame, decide (based on the target FPS) whether to grab
 *       a frame and hand it to the {@link VideoEncoder}.</li>
 *   <li>Track markers and elapsed (un-paused) time.</li>
 *   <li>Enforce the max-length safety cap.</li>
 *   <li>Write the {@link RecordingMetadata} sidecar when finishing.</li>
 * </ul>
 */
public class RecordingManager {

    private static final RecordingManager INSTANCE = new RecordingManager();
    public static RecordingManager get() { return INSTANCE; }
    private RecordingManager() { }

    // ---- state -------------------------------------------------------------
    private volatile boolean recording = false;
    private volatile boolean paused    = false;

    private FrameCapturer capturer;
    private VideoEncoder  encoder;
    private AudioRecorder audio;
    private RecordingMetadata meta;

    private final List<Marker> markers = new ArrayList<Marker>();

    private long startWallClock;      // System.currentTimeMillis at start
    private long pausedAccumMs;       // total time spent paused
    private long pauseStartedAt;      // wall clock when current pause began
    private long lastFrameNanos;      // for fps throttling
    private long frameIntervalNanos;  // 1e9 / fps

    private String flashMessage;      // brief HUD toast ("Marker added")
    private long   flashUntil;

    private boolean worldWasPresent = false; // for auto-record / world switches

    // Shadow Rewind rolling buffer (runs independently of an active recording)
    private final ShadowRewindBuffer shadow = new ShadowRewindBuffer();
    private FrameCapturer shadowCapturer;     // used to feed shadow when NOT recording
    private long shadowLastNanos = 0;
    private long shadowIntervalNanos;

    // Offscreen (window-size-independent) capture
    private final OffscreenCapturer offscreen = new OffscreenCapturer();
    private boolean useOffscreen = false;
    private int renderW, renderH;             // offscreen render dimensions

    /**
     * Set true only for the split-second we re-render a frame for capture, so
     * the OpenRewind recording indicator (red dot / timer) is NOT baked into the
     * recorded video – the player still sees it live, exactly like Lunar's clean
     * output. Read by {@link com.openrewind.hud.RecordingHud}.
     */
    public static volatile boolean HUD_SUPPRESSED = false;

    // ------------------------------------------------------------------------
    //  Public control API (called from key handler / GUI buttons)
    // ------------------------------------------------------------------------

    public synchronized void toggleRecording() {
        if (recording) stopRecording();
        else           startRecording();
    }

    public synchronized void startRecording() {
        if (recording) return;
        Minecraft mc = Minecraft.getMinecraft();
        int w = mc.displayWidth;
        int h = mc.displayHeight;
        if (w <= 0 || h <= 0) return;

        // compute target (downscaled) dimensions
        int[] target = computeTargetSize(w, h, RewindConfig.maxResolution);

        // decide offscreen (window-independent) capture
        useOffscreen = RewindConfig.offscreenCapture && OffscreenCapturer.isSupported();
        if (useOffscreen) {
            double aspect = (double) w / h;
            renderH = Math.max(2, RewindConfig.captureHeight & ~1);
            renderW = Math.max(2, ((int) Math.round(renderH * aspect)) & ~1);
            target = new int[]{ renderW, renderH };   // encoder outputs at render size
        }

        String id = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
        File recDir = RewindConfig.getRecordingsDir();
        File videoFile = new File(recDir, id + ".mp4");

        try {
            capturer = useOffscreen ? null : new FrameCapturer(w, h);
            encoder  = new VideoEncoder(videoFile, RewindConfig.videoFps, target[0], target[1]);
            encoder.start();

            audio = new AudioRecorder(recDir, id);
            audio.start();

            meta = new RecordingMetadata();
            meta.id         = id;
            meta.title      = "Recording " + id;
            meta.createdAt  = System.currentTimeMillis();
            meta.width      = target[0];
            meta.height     = target[1];
            meta.fps        = RewindConfig.videoFps;

            markers.clear();
            paused = false;
            pausedAccumMs = 0;
            pauseStartedAt = 0;
            startWallClock = System.currentTimeMillis();
            frameIntervalNanos = 1_000_000_000L / Math.max(1, RewindConfig.videoFps);
            lastFrameNanos = 0;
            recording = true;

            flash("\u00A7cREC \u00A7fstarted");
            OpenRewind.logger.info("[OpenRewind] recording started -> {}", videoFile.getName());
        } catch (Exception e) {
            OpenRewind.logger.error("[OpenRewind] failed to start recording", e);
            cleanupFailed();
        }
    }

    public synchronized void stopRecording() {
        if (!recording) return;
        recording = false;
        if (paused) { pausedAccumMs += System.currentTimeMillis() - pauseStartedAt; paused = false; }

        long durationMs = elapsedMs();

        // stop audio first (it joins its own threads), then flush encoder
        if (audio   != null) audio.stop();
        if (encoder != null) encoder.finish();

        if (meta != null) {
            meta.durationMs     = durationMs;
            meta.hasMicrophone  = audio != null && audio.hasMicrophone();
            meta.hasSystemAudio = audio != null && audio.hasSystemAudio();
            for (Marker m : markers) meta.addMarker(m);

            File sidecar = new File(RewindConfig.getRecordingsDir(), meta.id + ".json");
            JsonIO.write(sidecar, meta);
        }

        int enc = encoder != null ? encoder.getEncodedFrames() : 0;
        int drp = encoder != null ? encoder.getDroppedFrames() : 0;
        flash("\u00A7aSaved \u00A7f" + enc + " frames"
                + (drp > 0 ? " \u00A7e(" + drp + " dropped)" : ""));
        OpenRewind.logger.info("[OpenRewind] recording stopped: {} frames encoded, {} dropped",
                enc, drp);

        capturer = null; encoder = null; audio = null; meta = null;
    }

    /** Called from the JVM shutdown hook – flush without blocking on GUI. */
    public synchronized void emergencyStop() {
        if (!recording) return;
        if (!RewindConfig.backupOnCrash) return;
        OpenRewind.logger.warn("[OpenRewind] emergency stop – flushing encoder");
        stopRecording();
    }

    public synchronized void togglePause() {
        if (!recording) return;
        if (paused) {
            pausedAccumMs += System.currentTimeMillis() - pauseStartedAt;
            paused = false;
            flash("\u00A7aResumed");
        } else {
            pauseStartedAt = System.currentTimeMillis();
            paused = true;
            flash("\u00A7ePaused");
        }
    }

    public synchronized void addMarker() {
        if (!recording) return;
        Marker m = new Marker(elapsedMs());
        markers.add(m);
        flash("\u00A7bMarker #" + markers.size());
    }

    // ------------------------------------------------------------------------
    //  Shadow Rewind
    // ------------------------------------------------------------------------

    /** Enable/disable the rolling shadow buffer to match the current config. */
    public synchronized void syncShadowState() {
        // pixel shadow must not run in state mode (it would capture pixels and cost perf)
        if (RewindConfig.stateRecording) {
            if (shadow.isRunning()) { shadow.stop(); shadowCapturer = null; }
            return;
        }
        if (RewindConfig.shadowEnabled && !shadow.isRunning()) {
            shadow.start();
            shadowIntervalNanos = 1_000_000_000L / Math.max(1, RewindConfig.shadowFps);
            OpenRewind.logger.info("[OpenRewind] Shadow Rewind buffer started ({}s)",
                    RewindConfig.shadowSeconds);
        } else if (!RewindConfig.shadowEnabled && shadow.isRunning()) {
            shadow.stop();
            shadowCapturer = null;
        }
    }

    /** Persist the current shadow buffer as a saved clip. */
    public synchronized void saveShadow() {
        if (!shadow.isRunning()) {
            flash("\u00A7cShadow Rewind is off");
            return;
        }
        double sec = shadow.bufferedSeconds();
        shadow.saveAsync();
        flash("\u00A7dShadow saved \u00A7f(" + String.format("%.0f", sec) + "s)");
    }

    public boolean isShadowRunning() { return shadow.isRunning(); }
    public double  shadowBufferedSeconds() { return shadow.bufferedSeconds(); }

    // ------------------------------------------------------------------------
    //  Query API (used by the HUD)
    // ------------------------------------------------------------------------

    public boolean isRecording() { return recording; }
    public boolean isPaused()    { return paused; }
    public int     markerCount() { return markers.size(); }

    /** Un-paused elapsed time in ms. */
    public long elapsedMs() {
        if (!recording) return 0;
        long now = System.currentTimeMillis();
        long paused = pausedAccumMs + (this.paused ? now - pauseStartedAt : 0);
        return now - startWallClock - paused;
    }

    public String getFlashMessage() {
        if (System.currentTimeMillis() < flashUntil) return flashMessage;
        return null;
    }

    // ------------------------------------------------------------------------
    //  Event hooks
    // ------------------------------------------------------------------------

    /** Grab a frame at the END of each rendered frame, throttled to target FPS. */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // never capture our own editor / settings GUI
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        boolean ownGui = isOwnGui(screen);

        // ---- active recording ------------------------------------------------
        if (recording && !paused && encoder != null && !ownGui) {
            long now = System.nanoTime();
            if (lastFrameNanos == 0 || now - lastFrameNanos >= frameIntervalNanos) {
                lastFrameNanos = now;
                byte[] rgba; int fw, fh;
                if (useOffscreen) {
                    rgba = offscreen.renderAndGrab(renderW, renderH, event.renderTickTime);
                    if (rgba != null) { fw = renderW; fh = renderH; }
                    else {
                        // FBO path failed this frame – fall back to a window grab
                        if (capturer == null) capturer = new FrameCapturer(
                                Minecraft.getMinecraft().displayWidth, Minecraft.getMinecraft().displayHeight);
                        rgba = capturer.grab(); fw = capturer.getWidth(); fh = capturer.getHeight();
                    }
                } else {
                    if (capturer == null) return;
                    rgba = capturer.grab(); fw = capturer.getWidth(); fh = capturer.getHeight();
                }
                encoder.submit(rgba, fw, fh);
                if (shadow.isRunning()) shadow.offer(rgba, fw, fh);
            }
            return;
        }

        // ---- shadow-only capture (not recording) -----------------------------
        if (shadow.isRunning() && !ownGui && Minecraft.getMinecraft().theWorld != null) {
            long now = System.nanoTime();
            if (shadowLastNanos != 0 && now - shadowLastNanos < shadowIntervalNanos) return;
            shadowLastNanos = now;
            Minecraft mc = Minecraft.getMinecraft();
            int w = mc.displayWidth, h = mc.displayHeight;
            if (w <= 0 || h <= 0) return;
            if (shadowCapturer == null || shadowCapturer.getWidth() != w || shadowCapturer.getHeight() != h) {
                shadowCapturer = new FrameCapturer(w, h);
            }
            byte[] rgba = shadowCapturer.grab();
            shadow.offer(rgba, w, h);
        }
    }

    /** Housekeeping each client tick: enforce max length + auto-record. */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // keep the shadow buffer in sync with its config toggle
        syncShadowState();

        // ---- world enter / leave transitions --------------------------------
        boolean worldNow = Minecraft.getMinecraft().theWorld != null;
        if (worldNow && !worldWasPresent) {
            // just joined a world / server
            if (RewindConfig.autoRecordOnJoin && !recording) {
                startRecording();
            }
        } else if (!worldNow && worldWasPresent) {
            // left the world; stop unless the user wants a continuous recording
            if (recording && !RewindConfig.continueAcrossWorlds) {
                stopRecording();
            }
        }
        worldWasPresent = worldNow;

        // ---- max-length safety cap ------------------------------------------
        if (recording && elapsedMs() >= RewindConfig.maxRecordSeconds * 1000L) {
            OpenRewind.logger.info("[OpenRewind] max length reached, auto-stopping");
            stopRecording();
        }
    }

    // ------------------------------------------------------------------------

    private boolean isOwnGui(GuiScreen screen) {
        if (screen == null) return false;
        String n = screen.getClass().getName();
        return n.startsWith("com.openrewind.gui");
    }

    private void flash(String msg) {
        this.flashMessage = msg;
        this.flashUntil   = System.currentTimeMillis() + 2000;
    }

    private void cleanupFailed() {
        try { if (audio   != null) audio.stop();   } catch (Exception ignored) { }
        try { if (encoder != null) encoder.finish(); } catch (Exception ignored) { }
        capturer = null; encoder = null; audio = null; meta = null;
        recording = false; paused = false;
    }

    /** Downscale so the longest edge <= max (0 = keep native). Rounds to even. */
    static int[] computeTargetSize(int w, int h, int max) {
        if (max <= 0) return new int[]{ w & ~1, h & ~1 };
        int longest = Math.max(w, h);
        if (longest <= max) return new int[]{ w & ~1, h & ~1 };
        double scale = (double) max / longest;
        int tw = (int) Math.round(w * scale) & ~1;
        int th = (int) Math.round(h * scale) & ~1;
        return new int[]{ Math.max(2, tw), Math.max(2, th) };
    }
}
