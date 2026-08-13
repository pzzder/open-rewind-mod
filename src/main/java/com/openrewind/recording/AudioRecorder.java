package com.openrewind.recording;

import com.openrewind.OpenRewind;
import com.openrewind.config.RewindConfig;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * Captures audio in parallel with the video.
 *
 * <p>Two independent sources are supported:</p>
 * <ul>
 *   <li><b>Microphone</b> – the default capture device via a {@link TargetDataLine}.</li>
 *   <li><b>System audio</b> – captured from a loopback / "Stereo Mix" style
 *       device if the OS exposes one (Windows: Stereo Mix, Linux: a PulseAudio
 *       monitor source, macOS: a virtual device such as BlackHole).</li>
 * </ul>
 *
 * <p>Each active source is written to its own temporary WAV file on a worker
 * thread. When recording stops the WAVs are mixed and muxed into the final
 * container by {@link com.openrewind.editor.ExportManager}; keeping them
 * separate during capture lets the user re-balance mic vs. game volume later
 * in the editor – something raw single-track capture can't do.</p>
 */
public class AudioRecorder {

    /** 48 kHz, 16-bit, stereo, signed, little-endian. */
    private static final AudioFormat FORMAT =
            new AudioFormat(48_000f, 16, 2, true, false);

    private final File micFile;
    private final File sysFile;

    private TargetDataLine micLine;
    private TargetDataLine sysLine;
    private Thread micThread;
    private Thread sysThread;
    private volatile boolean running;

    private boolean micActive;
    private boolean sysActive;

    public AudioRecorder(File baseDir, String id) {
        this.micFile = new File(baseDir, id + "_mic.wav");
        this.sysFile = new File(baseDir, id + "_sys.wav");
    }

    public boolean hasMicrophone() { return micActive; }
    public boolean hasSystemAudio() { return sysActive; }
    public File getMicFile() { return micActive ? micFile : null; }
    public File getSysFile() { return sysActive ? sysFile : null; }

    public void start() {
        running = true;
        if (RewindConfig.recordMicrophone) {
            micLine = openLine(null);   // default input
            if (micLine != null) {
                micActive = true;
                micThread = spawn(micLine, micFile, "OpenRewind-Audio-Mic");
            }
        }
        if (RewindConfig.recordSystemAudio) {
            sysLine = openLoopbackLine();
            if (sysLine != null) {
                sysActive = true;
                sysThread = spawn(sysLine, sysFile, "OpenRewind-Audio-Sys");
            } else {
                OpenRewind.logger.warn("[OpenRewind] No system-audio loopback device found; "
                        + "system sound will not be recorded on this machine.");
            }
        }
    }

    public void stop() {
        running = false;
        closeQuietly(micLine);
        closeQuietly(sysLine);
        join(micThread);
        join(sysThread);
    }

    // ------------------------------------------------------------------------

    private Thread spawn(final TargetDataLine line, final File out, String name) {
        Thread t = new Thread(() -> writeLine(line, out), name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    /**
     * Drains a {@link TargetDataLine} into a WAV file until {@link #running}
     * goes false. We buffer to a piped stream so AudioSystem can write a proper
     * WAV header once the total length is known.
     */
    private void writeLine(TargetDataLine line, File out) {
        try {
            line.open(FORMAT);
            line.start();
            java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[line.getBufferSize() / 5];
            while (running) {
                int n = line.read(buf, 0, buf.length);
                if (n > 0) raw.write(buf, 0, n);
            }
            // flush trailing data
            int n;
            while ((n = line.read(buf, 0, buf.length)) > 0 && raw.size() < 500_000_000) {
                raw.write(buf, 0, n);
                if (n < buf.length) break;
            }
            byte[] pcm = raw.toByteArray();
            AudioInputStream ais = new AudioInputStream(
                    new ByteArrayInputStream(pcm), FORMAT, pcm.length / FORMAT.getFrameSize());
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, out);
            ais.close();
        } catch (Exception e) {
            OpenRewind.logger.error("[OpenRewind] audio capture failed for " + out.getName(), e);
        }
    }

    /** Open a capture line on the given mixer (null = default). */
    private TargetDataLine openLine(Mixer.Info mixerInfo) {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            if (mixerInfo != null) {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (mixer.isLineSupported(info)) {
                    return (TargetDataLine) mixer.getLine(info);
                }
                return null;
            }
            if (!AudioSystem.isLineSupported(info)) return null;
            return (TargetDataLine) AudioSystem.getLine(info);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Best-effort discovery of a loopback / monitor capture device by scanning
     * every mixer for one whose name hints at system-audio capture.
     */
    private TargetDataLine openLoopbackLine() {
        String[] hints = {"stereo mix", "loopback", "monitor", "what u hear",
                          "what you hear", "blackhole", "soundflower", "wave out mix"};
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            String name = (mi.getName() + " " + mi.getDescription()).toLowerCase();
            for (String h : hints) {
                if (name.contains(h)) {
                    TargetDataLine line = openLine(mi);
                    if (line != null) return line;
                }
            }
        }
        return null;
    }

    private static void closeQuietly(Line line) {
        if (line != null) {
            try { line.stop(); line.close(); } catch (Exception ignored) { }
        }
    }

    private static void join(Thread t) {
        if (t != null) {
            try { t.join(3000); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
