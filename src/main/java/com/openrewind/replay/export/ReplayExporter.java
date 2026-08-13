package com.openrewind.replay.export;

import com.openrewind.OpenRewind;
import com.openrewind.config.RewindConfig;
import com.openrewind.recording.OffscreenCapturer;
import com.openrewind.recording.VideoEncoder;
import com.openrewind.replay.playback.CameraEntity;
import com.openrewind.replay.playback.ReplayHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

import java.io.File;

/**
 * Re-renders a replay to an MP4 at an arbitrary resolution and frame rate.
 *
 * <h3>Why this beats pixel export</h3>
 * Every output frame is a <em>fresh render</em> of the reconstructed world, so:
 * <ul>
 *   <li>Any resolution is native (independent of the window that recorded it).</li>
 *   <li>Any frame rate is genuine — for FPS above the tick rate we pass Minecraft
 *       a fractional {@code partialTicks}, and the vanilla renderer interpolates
 *       entity/camera motion itself. This is real sub-tick rendering, <b>not</b>
 *       image blending, so 60→120→240 is smooth with no ghosting — exactly the
 *       property Lunar Rewind has and pixel capture can't.</li>
 * </ul>
 *
 * <h3>Drive model</h3>
 * Rendering must happen on the client render thread, so {@link #renderStep()} is
 * pumped once per real frame by the export driver. Each call advances the
 * virtual clock by one output-frame interval, seeks the {@link ReplayHandler}'s
 * sender to that time, positions the {@link CameraEntity} (POV = follow the
 * recorded player), renders one frame off-screen at the target resolution, and
 * feeds it to the {@link VideoEncoder}.
 *
 * <p><b>[LIVE-INTEGRATION]</b> The actual off-screen render call
 * ({@code entityRenderer.renderWorld(partialTicks, nano)} into a target-sized
 * framebuffer) reuses the same technique as {@link OffscreenCapturer}; wiring it
 * to render the <em>replay</em> world (and, in POV mode, the recorded HUD +
 * open container from the GUI track) is the part to validate in a live client.</p>
 */
public class ReplayExporter {

    public interface ProgressListener {
        void onProgress(float f, String stage);
        void onComplete(File out);
        void onError(Exception e);
    }

    private final ReplayHandler handler;
    private final ProgressListener listener;
    private final int fps;
    private final int outW, outH;

    private VideoEncoder encoder;
    private final OffscreenCapturer offscreen = new OffscreenCapturer();
    private CameraEntity camera;
    private com.openrewind.replay.edit.ReplayEditProject edit;

    /** Attach the edit project (camera path / mode / hide-HUD / effects). */
    public void setEditProject(com.openrewind.replay.edit.ReplayEditProject e) { this.edit = e; }

    private long virtualMs = 0;
    private final long frameIntervalMs;
    private final long durationMs;
    private boolean running = false;

    public ReplayExporter(ReplayHandler handler, int fps, int outW, int outH,
                          ProgressListener listener) {
        this.handler = handler;
        this.listener = listener;
        this.fps = Math.max(1, fps);
        this.outW = outW & ~1;
        this.outH = outH & ~1;
        this.frameIntervalMs = Math.max(1, 1000L / this.fps);
        this.durationMs = handler.getDurationMs();
    }

    public boolean isRunning() { return running; }

    public void begin() throws Exception {
        File out = new File(RewindConfig.getExportsDir(),
                "replay_" + System.currentTimeMillis() + ".mp4");
        encoder = new VideoEncoder(out, fps, outW, outH);
        encoder.start();
        if (!handler.isActive()) handler.start();
        camera = handler.getCamera();
        virtualMs = 0;
        running = true;
        listener.onProgress(0f, "Rendering");
    }

    /**
     * Render exactly one output frame. Called once per real client frame by the
     * export driver until {@link #isRunning()} turns false.
     */
    public void renderStep() {
        if (!running) return;
        try {
            handler.seekTo(virtualMs);
            positionCamera();

            // Force Hide HUD: suppress the game overlay for the exported frame
            Minecraft mc = Minecraft.getMinecraft();
            int savedHideGui = mc.gameSettings.hideGUI ? 1 : 0;
            boolean hide = edit != null && edit.camera != null && edit.camera.hideHud;
            if (hide) mc.gameSettings.hideGUI = true;

            // partialTicks lets vanilla interpolate motion between ticks for
            // genuinely smooth high-fps output (no image blending / ghosting).
            float partialTicks = (virtualMs % 50L) / 50.0f; // 20 ticks/s = 50 ms/tick

            byte[] rgba = offscreen.renderAndGrab(outW, outH, partialTicks);
            if (hide) mc.gameSettings.hideGUI = (savedHideGui == 1);
            if (rgba != null) {
                // apply keyframed colour grade (brightness/saturation) for this frame
                if (edit != null) {
                    double bri = edit.brightness.valueAt(virtualMs);
                    double sat = edit.saturation.valueAt(virtualMs);
                    if (Math.abs(bri) > 0.001 || Math.abs(sat - 1.0) > 0.001) {
                        applyColor(rgba, bri, sat);
                    }
                }
                encoder.submit(rgba, outW, outH);
            }

            listener.onProgress(Math.min(0.99f, virtualMs / (float) Math.max(1, durationMs)),
                    "Rendering");

            virtualMs += frameIntervalMs;
            if (virtualMs > durationMs) finish();
        } catch (Exception e) {
            running = false;
            OpenRewind.logger.error("[OpenRewind] replay export failed", e);
            listener.onError(e);
        }
    }

    /** Position the camera per the edit project: SYNC follows the player, FLIGHT flies the keyframe path. */
    private void positionCamera() {
        if (camera == null) return;
        Minecraft mc = Minecraft.getMinecraft();

        com.openrewind.replay.edit.ReplayCamera cam = edit != null ? edit.camera : null;
        if (cam != null && cam.mode == com.openrewind.replay.edit.ReplayCamera.Mode.FLIGHT) {
            com.openrewind.replay.edit.ReplayCamera.Pose pose = cam.poseAt(virtualMs);
            if (pose != null) {
                float yaw = pose.yaw, pitch = pose.pitch;
                if (edit.cameraShake > 0) { // camera shake effect
                    yaw   += (float) ((Math.random() - 0.5) * edit.cameraShake);
                    pitch += (float) ((Math.random() - 0.5) * edit.cameraShake);
                }
                camera.moveCamera(pose.x, pose.y, pose.z, yaw, pitch);
                return;
            }
        }

        // SYNC / default: follow the recorded player's eye (POV)
        Entity target = mc.thePlayer;
        if (target != null) {
            camera.moveCamera(target.posX, target.posY + target.getEyeHeight(), target.posZ,
                    target.rotationYaw, target.rotationPitch);
        }
    }

    /** In-place brightness offset + saturation on a bottom-up RGBA frame. */
    private static void applyColor(byte[] rgba, double brightness, double saturation) {
        int add = (int) (brightness * 255);
        for (int i = 0; i + 2 < rgba.length; i += 4) {
            int r = rgba[i] & 0xFF, g = rgba[i + 1] & 0xFF, b = rgba[i + 2] & 0xFF;
            double luma = 0.299 * r + 0.587 * g + 0.114 * b;
            r = clamp8((int) (luma + (r - luma) * saturation) + add);
            g = clamp8((int) (luma + (g - luma) * saturation) + add);
            b = clamp8((int) (luma + (b - luma) * saturation) + add);
            rgba[i] = (byte) r; rgba[i + 1] = (byte) g; rgba[i + 2] = (byte) b;
        }
    }

    private static int clamp8(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    private void finish() {
        running = false;
        if (encoder != null) encoder.finish();
        handler.stop();
        File out = encoder != null ? encoder.getOutputFile() : null;
        listener.onProgress(1f, "Done");
        listener.onComplete(out);
        OpenRewind.logger.info("[OpenRewind] replay export complete -> {}",
                out != null ? out.getName() : "?");
    }
}
