package com.openrewind.recording;

import com.openrewind.OpenRewind;

import org.jcodec.api.awt.AWTSequenceEncoder;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Asynchronous H.264 video encoder.
 *
 * <p>Raw RGBA frames captured on the render thread are dropped into a bounded
 * queue. A dedicated worker thread pulls them off, performs the (expensive)
 * vertical flip + optional downscale + RGBA→RGB conversion, and feeds the
 * result to JCodec's {@link AWTSequenceEncoder}, which muxes an MP4 at a
 * constant frame rate.</p>
 *
 * <p>The queue is bounded so a slow disk can't blow up the heap; if the encoder
 * falls behind, the render thread's {@link #submit} call will drop frames
 * rather than stall the game.</p>
 */
public class VideoEncoder {

    /** A captured frame plus the source dimensions it was grabbed at. */
    private static final class RawFrame {
        final byte[] rgba;
        final int    srcW;
        final int    srcH;
        RawFrame(byte[] rgba, int srcW, int srcH) {
            this.rgba = rgba; this.srcW = srcW; this.srcH = srcH;
        }
    }

    private static final RawFrame POISON = new RawFrame(null, 0, 0);

    private final File outputFile;
    private final int  fps;
    private final int  targetW;
    private final int  targetH;

    private final BlockingQueue<RawFrame> queue = new LinkedBlockingQueue<RawFrame>(90);
    private final AtomicBoolean running   = new AtomicBoolean(false);
    private final AtomicInteger encoded   = new AtomicInteger(0);
    private final AtomicInteger dropped   = new AtomicInteger(0);

    private Thread            worker;
    private AWTSequenceEncoder encoder;
    private volatile Exception failure;

    public VideoEncoder(File outputFile, int fps, int targetW, int targetH) {
        this.outputFile = outputFile;
        this.fps        = Math.max(1, fps);
        // H.264 requires even dimensions
        this.targetW    = targetW  & ~1;
        this.targetH    = targetH  & ~1;
    }

    public int  getEncodedFrames() { return encoded.get(); }
    public int  getDroppedFrames() { return dropped.get(); }
    public File getOutputFile()    { return outputFile;    }

    /** Spin up the encoder file + worker thread. */
    public void start() throws Exception {
        encoder = AWTSequenceEncoder.createSequenceEncoder(outputFile, fps);
        running.set(true);
        worker = new Thread(this::loop, "OpenRewind-Encoder");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    /**
     * Offer a raw frame to the encoder. Non-blocking: if the queue is full the
     * frame is dropped so we never freeze the render thread.
     */
    public void submit(byte[] rgba, int srcW, int srcH) {
        if (!running.get()) return;
        if (!queue.offer(new RawFrame(rgba, srcW, srcH))) {
            dropped.incrementAndGet();
        }
    }

    /** Signal end-of-stream and block until the worker has flushed the file. */
    public void finish() {
        if (!running.get()) return;
        try {
            queue.put(POISON);
            if (worker != null) worker.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public Exception getFailure() { return failure; }

    // ------------------------------------------------------------------------

    private void loop() {
        try {
            while (true) {
                RawFrame f = queue.take();
                if (f == POISON) break;
                BufferedImage img = toImage(f);
                encoder.encodeImage(img);
                encoded.incrementAndGet();
            }
            encoder.finish();
        } catch (Exception e) {
            failure = e;
            OpenRewind.logger.error("[OpenRewind] encoder thread failed", e);
        } finally {
            running.set(false);
        }
    }

    /**
     * Convert a bottom-up RGBA frame into a top-down RGB {@link BufferedImage},
     * scaling to the target resolution if necessary.
     */
    private BufferedImage toImage(RawFrame f) {
        BufferedImage src = new BufferedImage(f.srcW, f.srcH, BufferedImage.TYPE_INT_RGB);
        int[] row = new int[f.srcW];
        for (int y = 0; y < f.srcH; y++) {
            int glRow = f.srcH - 1 - y;      // flip vertically (GL origin bottom-left)
            int base  = glRow * f.srcW * 4;
            for (int x = 0; x < f.srcW; x++) {
                int i = base + x * 4;
                int r = f.rgba[i]     & 0xFF;
                int g = f.rgba[i + 1] & 0xFF;
                int b = f.rgba[i + 2] & 0xFF;
                row[x] = (r << 16) | (g << 8) | b;
            }
            src.setRGB(0, y, f.srcW, 1, row, 0, f.srcW);
        }

        if (f.srcW == targetW && f.srcH == targetH) {
            return src;
        }
        BufferedImage dst = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = dst.createGraphics();
        g2.drawImage(src, 0, 0, targetW, targetH, null);
        g2.dispose();
        return dst;
    }
}
