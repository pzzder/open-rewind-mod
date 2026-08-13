package com.openrewind.editor;

import com.openrewind.OpenRewind;

import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A lightweight software video player for the editor preview window.
 *
 * <p>A background thread decodes the source MP4 sequentially with JCodec,
 * keeping {@link #currentImage} pointed at the frame nearest the current
 * playback position. The GUI polls {@link #getCurrentFrame()} each render tick
 * and uploads it to a {@link com.openrewind.util.GlFrameTexture}.</p>
 *
 * <p>Playback position advances by wall-clock &times; {@link #speed}. Seeking
 * requests a JCodec key-frame seek; because software decode is slow, seeks are
 * approximate (nearest preceding key frame) which is fine for scrubbing.</p>
 */
public class VideoPlayer {

    private final File file;
    private final int fps;
    private final long durationMs;

    private volatile BufferedImage currentImage;
    private final AtomicLong positionMs = new AtomicLong(0);
    private final AtomicBoolean playing = new AtomicBoolean(false);
    private volatile double speed = 1.0;
    private volatile long seekRequestMs = -1;
    private volatile boolean alive = true;

    private Thread decodeThread;

    public VideoPlayer(File file, int fps, long durationMs) {
        this.file = file;
        this.fps = Math.max(1, fps);
        this.durationMs = Math.max(1, durationMs);
    }

    public void open() {
        decodeThread = new Thread(this::decodeLoop, "OpenRewind-Preview");
        decodeThread.setDaemon(true);
        decodeThread.start();
    }

    public void close() {
        alive = false;
        if (decodeThread != null) decodeThread.interrupt();
    }

    public BufferedImage getCurrentFrame() { return currentImage; }
    public long getPositionMs() { return positionMs.get(); }
    public long getDurationMs() { return durationMs; }
    public boolean isPlaying()  { return playing.get(); }
    public double getSpeed()    { return speed; }

    public void play()  { playing.set(true); }
    public void pause() { playing.set(false); }
    public void togglePlay() { playing.set(!playing.get()); }
    public void setSpeed(double s) { this.speed = Math.max(0.1, Math.min(8.0, s)); }
    public void seek(long ms) {
        long clamped = Math.max(0, Math.min(durationMs, ms));
        positionMs.set(clamped);
        seekRequestMs = clamped;
    }
    public void skip(long deltaMs) { seek(positionMs.get() + deltaMs); }

    // ------------------------------------------------------------------------

    private void decodeLoop() {
        FrameGrab grab = null;
        int decodedIndex = 0;         // frame index of the last decoded frame
        long lastWall = System.currentTimeMillis();
        try {
            grab = FrameGrab.createFrameGrab(NIOUtils.readableChannel(file));
            while (alive) {
                // ---- handle a pending seek ----------------------------------
                long sr = seekRequestMs;
                if (sr >= 0) {
                    seekRequestMs = -1;
                    try {
                        grab.seekToSecondPrecise(sr / 1000.0);
                        decodedIndex = (int) (sr / 1000.0 * fps);
                    } catch (Exception e) {
                        // reopen from start on seek failure
                        grab = FrameGrab.createFrameGrab(NIOUtils.readableChannel(file));
                        decodedIndex = 0;
                    }
                    Picture p = grab.getNativeFrame();
                    if (p != null) currentImage = AWTUtil.toBufferedImage(p);
                    lastWall = System.currentTimeMillis();
                }

                // ---- advance playback clock ---------------------------------
                long now = System.currentTimeMillis();
                if (playing.get()) {
                    long delta = (long) ((now - lastWall) * speed);
                    long pos = positionMs.get() + delta;
                    if (pos >= durationMs) { pos = durationMs; playing.set(false); }
                    positionMs.set(pos);
                }
                lastWall = now;

                // ---- decode forward until we reach the current position -----
                long targetIndex = (long) (positionMs.get() / 1000.0 * fps);
                int guard = 0;
                while (decodedIndex < targetIndex && guard++ < 240) {
                    Picture p = grab.getNativeFrame();
                    if (p == null) { playing.set(false); break; }
                    decodedIndex++;
                    if (decodedIndex >= targetIndex) {
                        currentImage = AWTUtil.toBufferedImage(p);
                    }
                }

                Thread.sleep(playing.get() ? 8 : 30);
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            OpenRewind.logger.error("[OpenRewind] preview decode failed", e);
        }
    }
}
