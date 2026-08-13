package com.openrewind.editor;

import com.openrewind.OpenRewind;
import com.openrewind.config.RewindConfig;
import com.openrewind.recording.RecordingMetadata;
import com.openrewind.util.JsonIO;

import org.jcodec.api.FrameGrab;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Renders a pixel-mode {@link Project} into an MP4 by decoding the required
 * frames from each source recording and re-encoding only the kept ranges, in
 * order, applying per-clip effects (speed / brightness / saturation).
 *
 * <p>Audio is mixed to a synchronised WAV via {@link AudioMixer}; if an
 * {@code ffmpeg} binary is available we mux the WAV into the MP4, otherwise the
 * WAV is left beside the video.</p>
 *
 * <p><b>Frame rate note:</b> pixel recordings only hold the frames that were
 * captured, so exporting above the recorded FPS simply repeats frames (Lunar
 * Rewind gets genuinely new high-FPS frames because it re-renders from state —
 * that is what the {@code com.openrewind.replay} state engine does instead).</p>
 *
 * <p>Runs on its own worker thread; progress is reported through a
 * {@link ProgressListener} so the GUI can render a bar.</p>
 */
public class ExportManager {

    public interface ProgressListener {
        void onProgress(float fraction, String stage);
        void onComplete(File output, boolean audioMuxed);
        void onError(Exception e);
    }

    private final Project project;
    private final ProgressListener listener;

    public ExportManager(Project project, ProgressListener listener) {
        this.project  = project;
        this.listener = listener;
    }

    public void exportAsync() {
        Thread t = new Thread(this::export, "OpenRewind-Export");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------------

    private void export() {
        try {
            File exportDir = RewindConfig.getExportsDir();
            String base = sanitize(project.name) + "_" + System.currentTimeMillis();
            File videoOut = new File(exportDir, base + ".mp4");

            int fps = project.exportFps > 0 ? project.exportFps : 30;
            long totalMs = Math.max(1, project.totalLengthMs());
            long doneMs = 0;

            AWTSequenceEncoder enc = AWTSequenceEncoder.createSequenceEncoder(videoOut, fps);

            for (Clip clip : project.clips) {
                RecordingMetadata meta = loadMeta(clip.sourceId);
                File src = new File(RewindConfig.getRecordingsDir(), clip.sourceId + ".mp4");
                if (!src.exists()) {
                    OpenRewind.logger.warn("[OpenRewind] export: missing source {}", src);
                    continue;
                }
                int srcFps = (meta != null && meta.fps > 0) ? meta.fps : fps;

                int ow = project.exportWidth  > 0 ? project.exportWidth
                        : (meta != null ? meta.width  : 0);
                int oh = project.exportHeight > 0 ? project.exportHeight
                        : (meta != null ? meta.height : 0);
                int[] outSize = computeOutputSize(meta, ow, oh);

                doneMs = encodeClip(enc, src, srcFps, fps, clip, outSize[0], outSize[1], totalMs, doneMs);
            }

            enc.finish();
            listener.onProgress(0.9f, "Mixing audio");

            File mixedWav = new File(exportDir, base + ".wav");
            boolean hasAudio = new AudioMixer(project).mixTo(mixedWav);

            boolean muxed = false;
            if (hasAudio) {
                listener.onProgress(0.95f, "Muxing");
                muxed = tryFfmpegMux(videoOut, mixedWav, new File(exportDir, base + "_final.mp4"));
            }

            listener.onProgress(1.0f, "Done");
            listener.onComplete(muxed ? new File(exportDir, base + "_final.mp4") : videoOut, muxed);
        } catch (Exception e) {
            OpenRewind.logger.error("[OpenRewind] export failed", e);
            listener.onError(e);
        }
    }

    /**
     * Decode {@code src} and re-encode the range [in,out) at {@code outFps},
     * applying the clip's speed factor (output length = length / speed) and
     * colour effects. Returns the updated cumulative source-ms (for progress).
     */
    private long encodeClip(AWTSequenceEncoder enc, File src, int srcFps, int outFps,
                            Clip clip, int ow, int oh, long totalMs, long doneMs)
            throws Exception {

        FrameGrab grab = FrameGrab.createFrameGrab(NIOUtils.readableChannel(src));
        double inSec = clip.inMs / 1000.0;
        if (inSec > 0.5) {
            try { grab.seekToSecondPrecise(inSec); } catch (Exception ignored) { }
        }

        long clipLen  = clip.lengthMs();
        double speed  = Math.max(0.05, clip.speed);
        long clipOutLen = (long) (clipLen / speed);
        long outFrameIntervalMs = Math.max(1, 1000L / outFps);

        Picture pic;
        int srcIndex = (int) Math.floor(inSec * srcFps);
        long emittedMs = 0;
        BufferedImage last = null;

        while (emittedMs < clipOutLen && (pic = grab.getNativeFrame()) != null) {
            long srcTimeMs = (long) ((srcIndex++ / (double) srcFps) * 1000.0);
            if (srcTimeMs < clip.inMs) continue;
            if (srcTimeMs >= clip.outMs) break;

            BufferedImage img = AWTUtil.toBufferedImage(pic);
            if (ow > 0 && oh > 0 && (img.getWidth() != ow || img.getHeight() != oh)) {
                img = scale(img, ow & ~1, oh & ~1);
            }
            if (clip.hasColorEffect()) {
                img = applyColor(img, clip.brightness, clip.saturation);
            }
            last = img;

            long targetEmit = (long) ((srcTimeMs - clip.inMs) / speed);
            while (emittedMs <= targetEmit && emittedMs < clipOutLen) {
                enc.encodeImage(img);
                emittedMs += outFrameIntervalMs;
            }
            listener.onProgress(Math.min(0.88f, (doneMs + emittedMs) / (float) totalMs * 0.88f),
                    "Rendering video");
        }
        while (emittedMs < clipOutLen && last != null) {
            enc.encodeImage(last);
            emittedMs += outFrameIntervalMs;
        }
        return doneMs + clipOutLen;
    }

    /** Resolve output width/height from resolution preset + orientation. */
    private int[] computeOutputSize(RecordingMetadata meta, int explicitW, int explicitH) {
        int sw = meta != null ? meta.width  : (explicitW > 0 ? explicitW : 1920);
        int sh = meta != null ? meta.height : (explicitH > 0 ? explicitH : 1080);
        String preset = project.resolutionPreset == null ? "Source" : project.resolutionPreset;

        int shortEdge;
        switch (preset) {
            case "480p":  shortEdge = 480;  break;
            case "720p":  shortEdge = 720;  break;
            case "1080p": shortEdge = 1080; break;
            case "1440p": shortEdge = 1440; break;
            default:
                if (explicitW > 0 && explicitH > 0) return new int[]{ explicitW & ~1, explicitH & ~1 };
                return new int[]{ sw & ~1, sh & ~1 };
        }
        double aspect = (double) sw / sh;
        boolean vertical = "Vertical".equalsIgnoreCase(project.orientation);
        int w, h;
        if (vertical) {
            w = shortEdge;
            h = (int) Math.round(shortEdge * (aspect > 1 ? aspect : 1 / aspect));
        } else {
            h = shortEdge;
            w = (int) Math.round(shortEdge * (aspect > 1 ? aspect : 1 / aspect));
        }
        return new int[]{ Math.max(2, w & ~1), Math.max(2, h & ~1) };
    }

    /** Apply brightness offset + saturation multiplier to a frame. */
    private static BufferedImage applyColor(BufferedImage src, double brightness, double saturation) {
        int w = src.getWidth(), h = src.getHeight();
        int add = (int) (brightness * 255);
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            src.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int p = row[x];
                int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                double luma = 0.299 * r + 0.587 * g + 0.114 * b;
                r = (int) (luma + (r - luma) * saturation) + add;
                g = (int) (luma + (g - luma) * saturation) + add;
                b = (int) (luma + (b - luma) * saturation) + add;
                row[x] = (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b);
            }
            dst.setRGB(0, y, w, 1, row, 0, w);
        }
        return dst;
    }

    private static int clamp8(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    private RecordingMetadata loadMeta(String id) {
        File sidecar = new File(RewindConfig.getRecordingsDir(), id + ".json");
        return JsonIO.read(sidecar, RecordingMetadata.class);
    }

    private static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    /** Mux WAV into MP4 with ffmpeg if it exists; return true on success. */
    private boolean tryFfmpegMux(File video, File wav, File out) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-i", video.getAbsolutePath(),
                    "-i", wav.getAbsolutePath(),
                    "-c:v", "copy", "-c:a", "aac", "-shortest",
                    out.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            java.io.InputStream is = p.getInputStream();
            byte[] buf = new byte[4096];
            while (is.read(buf) != -1) { /* discard */ }
            int code = p.waitFor();
            return code == 0 && out.exists();
        } catch (Exception e) {
            OpenRewind.logger.info("[OpenRewind] ffmpeg not available, leaving separate WAV");
            return false;
        }
    }

    private static String sanitize(String s) {
        if (s == null) return "export";
        return s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
