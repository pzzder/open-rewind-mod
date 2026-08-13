package com.openrewind.recording;

import com.openrewind.OpenRewind;
import com.openrewind.config.RewindConfig;
import com.openrewind.util.JsonIO;

import org.jcodec.api.awt.AWTSequenceEncoder;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Shadow Rewind – a rolling replay buffer that continuously keeps the last
 * {@link RewindConfig#shadowSeconds} of gameplay so you can save a clip you
 * forgot to record, exactly like Lunar Client's Shadow Rewind.
 *
 * <p><b>Why JPEG?</b> Lunar buffers game <i>state</i> (cheap). A pixel-based
 * clone can't keep raw frames – 60&nbsp;s of 1080p RGBA is ~15&nbsp;GB. So each
 * captured frame is JPEG-compressed on a worker thread (a few hundred KB) and
 * kept in a time- and RAM-bounded ring. On save we decode the ring and encode a
 * normal MP4.</p>
 *
 * <p>The render thread only ever does a cheap {@code offer()} of the raw bytes;
 * all compression and eviction happen off-thread so frame rate isn't hurt.</p>
 */
public class ShadowRewindBuffer {

    private static final class RawFrame {
        final byte[] rgba; final int w, h; final long t;
        RawFrame(byte[] rgba, int w, int h, long t) { this.rgba = rgba; this.w = w; this.h = h; this.t = t; }
    }
    private static final class StoredFrame {
        final byte[] jpeg; final long t;
        StoredFrame(byte[] jpeg, long t) { this.jpeg = jpeg; this.t = t; }
        int length() { return jpeg.length; }
    }

    private final BlockingQueue<RawFrame> inbox = new LinkedBlockingQueue<RawFrame>(30);
    private final Deque<StoredFrame> ring = new ArrayDeque<StoredFrame>();
    private long ringBytes = 0;
    private final Object ringLock = new Object();

    private volatile boolean running = false;
    private Thread worker;
    private long lastCaptureNanos = 0;
    private long captureIntervalNanos;

    private int targetW, targetH; // decided from first frame + shadowResolution

    public void start() {
        if (running) return;
        running = true;
        captureIntervalNanos = 1_000_000_000L / Math.max(1, RewindConfig.shadowFps);
        worker = new Thread(this::loop, "OpenRewind-Shadow");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    public void stop() {
        running = false;
        if (worker != null) worker.interrupt();
        synchronized (ringLock) { ring.clear(); ringBytes = 0; }
    }

    public boolean isRunning() { return running; }

    /** Called on the render thread; cheap, throttled to shadow fps. */
    public void offer(byte[] rgba, int w, int h) {
        if (!running) return;
        long now = System.nanoTime();
        if (lastCaptureNanos != 0 && now - lastCaptureNanos < captureIntervalNanos) return;
        lastCaptureNanos = now;
        inbox.offer(new RawFrame(rgba, w, h, System.currentTimeMillis()));
    }

    /** Seconds currently held in the buffer. */
    public double bufferedSeconds() {
        synchronized (ringLock) {
            if (ring.size() < 2) return 0;
            return (ring.peekLast().t - ring.peekFirst().t) / 1000.0;
        }
    }

    // ------------------------------------------------------------------------

    private void loop() {
        while (running) {
            try {
                RawFrame f = inbox.take();
                if (targetW == 0) {
                    int[] tgt = RecordingManager.computeTargetSize(f.w, f.h, RewindConfig.shadowResolution);
                    targetW = tgt[0]; targetH = tgt[1];
                }
                byte[] jpeg = compress(f);
                synchronized (ringLock) {
                    ring.addLast(new StoredFrame(jpeg, f.t));
                    ringBytes += jpeg.length;
                    evict();
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                OpenRewind.logger.warn("[OpenRewind] shadow frame skipped: {}", e.toString());
            }
        }
    }

    /** Drop frames older than the window or above the RAM budget. */
    private void evict() {
        long windowMs = RewindConfig.shadowSeconds * 1000L;
        long budget   = RewindConfig.shadowMemoryMb * 1024L * 1024L;
        long newest   = ring.peekLast().t;
        Iterator<StoredFrame> it = ring.iterator();
        while (it.hasNext()) {
            StoredFrame sf = ring.peekFirst();
            if (sf == null) break;
            boolean tooOld = newest - sf.t > windowMs;
            boolean tooBig = ringBytes > budget;
            if (tooOld || tooBig) {
                ring.pollFirst();
                ringBytes -= sf.length();
            } else break;
        }
    }

    /** Flip + downscale + JPEG-compress a raw RGBA frame. */
    private byte[] compress(RawFrame f) throws Exception {
        BufferedImage img = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        // nearest-neighbour flip+scale directly into the target buffer
        int[] rowRgb = new int[targetW];
        for (int y = 0; y < targetH; y++) {
            int srcY = f.h - 1 - (int) ((long) y * f.h / targetH); // flip vertical
            int base = srcY * f.w * 4;
            for (int x = 0; x < targetW; x++) {
                int srcX = (int) ((long) x * f.w / targetW);
                int i = base + srcX * 4;
                int r = f.rgba[i] & 0xFF, g = f.rgba[i + 1] & 0xFF, b = f.rgba[i + 2] & 0xFF;
                rowRgb[x] = (r << 16) | (g << 8) | b;
            }
            img.setRGB(0, y, targetW, 1, rowRgb, 0, targetW);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream(256 * 1024);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam p = writer.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(0.7f);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), p);
        }
        writer.dispose();
        return bos.toByteArray();
    }

    /**
     * Persist the current ring as a normal recording (mp4 + metadata) so it
     * shows up in the editor list. Runs on a worker thread; returns immediately.
     */
    public void saveAsync() {
        final StoredFrame[] snapshot;
        synchronized (ringLock) {
            if (ring.isEmpty()) {
                OpenRewind.logger.info("[OpenRewind] shadow buffer empty – nothing to save");
                return;
            }
            snapshot = ring.toArray(new StoredFrame[0]);
        }
        Thread t = new Thread(() -> writeSnapshot(snapshot), "OpenRewind-ShadowSave");
        t.setDaemon(true);
        t.start();
    }

    private void writeSnapshot(StoredFrame[] frames) {
        try {
            String id = "shadow_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new java.util.Date());
            File recDir = RewindConfig.getRecordingsDir();
            File mp4 = new File(recDir, id + ".mp4");
            int fps = RewindConfig.shadowFps;

            AWTSequenceEncoder enc = AWTSequenceEncoder.createSequenceEncoder(mp4, fps);
            long t0 = frames[0].t;
            long frameIntervalMs = 1000L / fps;
            long emitted = 0;
            for (StoredFrame sf : frames) {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(sf.jpeg));
                long target = sf.t - t0;
                // hold each frame until real elapsed time catches up
                do {
                    enc.encodeImage(img);
                    emitted += frameIntervalMs;
                } while (emitted < target);
            }
            enc.finish();

            RecordingMetadata meta = new RecordingMetadata();
            meta.id = id;
            meta.title = "Shadow " + id.substring(7);
            meta.createdAt = frames[0].t;
            meta.durationMs = frames[frames.length - 1].t - t0;
            meta.width = targetW; meta.height = targetH; meta.fps = fps;
            meta.hasMicrophone = false; meta.hasSystemAudio = false;
            JsonIO.write(new File(recDir, id + ".json"), meta);

            OpenRewind.logger.info("[OpenRewind] shadow saved {} frames -> {}", frames.length, mp4.getName());
        } catch (Exception e) {
            OpenRewind.logger.error("[OpenRewind] shadow save failed", e);
        }
    }
}
