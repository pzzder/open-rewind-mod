package com.openrewind.editor;

import com.openrewind.OpenRewind;
import com.openrewind.config.RewindConfig;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;

/**
 * Mixes the microphone and system-audio WAV tracks of every clip in a project
 * into a single 48 kHz / 16-bit WAV that lines up with the exported video.
 * Handles per-track gains (mic / system / master), per-clip speed (resampling)
 * and the project's stereo / mono setting.
 */
public class AudioMixer {

    private static final float SR = 48_000f;
    private static final AudioFormat STEREO =
            new AudioFormat(SR, 16, 2, true, false);
    private static final int FRAME_SIZE = STEREO.getFrameSize(); // 4 bytes

    private final Project project;

    public AudioMixer(Project project) {
        this.project = project;
    }

    /** @return true if any audio was written. */
    public boolean mixTo(File out) {
        try {
            java.io.ByteArrayOutputStream timeline = new java.io.ByteArrayOutputStream();
            boolean any = false;

            for (Clip clip : project.clips) {
                double speed = Math.max(0.05, clip.speed);
                File mic = new File(RewindConfig.getRecordingsDir(), clip.sourceId + "_mic.wav");
                File sys = new File(RewindConfig.getRecordingsDir(), clip.sourceId + "_sys.wav");

                short[] micPcm = resample(readRange(mic, clip.inMs, clip.outMs), speed);
                short[] sysPcm = resample(readRange(sys, clip.inMs, clip.outMs), speed);

                if (micPcm == null && sysPcm == null) {
                    appendSilence(timeline, clip.outputLengthMs());
                    continue;
                }
                any = true;

                int frames = Math.max(len(micPcm), len(sysPcm)) / 2; // stereo sample pairs
                double gMic = project.micVolume    * project.masterVolume;
                double gSys = project.systemVolume * project.masterVolume;

                byte[] chunk = new byte[frames * FRAME_SIZE];
                for (int i = 0; i < frames * 2; i++) {
                    double s = 0;
                    if (micPcm != null && i < micPcm.length) s += micPcm[i] * gMic;
                    if (sysPcm != null && i < sysPcm.length) s += sysPcm[i] * gSys;
                    short v = clamp(s);
                    chunk[i * 2]     = (byte) (v & 0xFF);
                    chunk[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
                }
                if (project.audioMono) chunk = toMono(chunk);
                timeline.write(chunk, 0, chunk.length);
            }

            if (!any && timeline.size() == 0) return false;

            byte[] pcm = timeline.toByteArray();
            AudioInputStream ais = new AudioInputStream(
                    new ByteArrayInputStream(pcm), STEREO, pcm.length / FRAME_SIZE);
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, out);
            ais.close();
            return any;
        } catch (Exception e) {
            OpenRewind.logger.error("[OpenRewind] audio mix failed", e);
            return false;
        }
    }

    /** Read [inMs,outMs) of a WAV as interleaved stereo shorts at 48k/16/stereo. */
    private short[] readRange(File wav, long inMs, long outMs) {
        if (wav == null || !wav.exists()) return null;
        try {
            AudioInputStream in = AudioSystem.getAudioInputStream(wav);
            AudioInputStream conv = AudioSystem.getAudioInputStream(STEREO, in);

            long startFrame = (long) (inMs / 1000.0 * SR);
            long endFrame   = (long) (outMs / 1000.0 * SR);
            long skipBytes  = startFrame * FRAME_SIZE;
            long wantBytes  = (endFrame - startFrame) * FRAME_SIZE;

            long skipped = 0;
            while (skipped < skipBytes) {
                long s = conv.skip(skipBytes - skipped);
                if (s <= 0) break;
                skipped += s;
            }

            byte[] buf = new byte[(int) Math.min(wantBytes, Integer.MAX_VALUE)];
            int read = 0;
            while (read < buf.length) {
                int n = conv.read(buf, read, buf.length - read);
                if (n < 0) break;
                read += n;
            }
            conv.close(); in.close();

            int samples = read / 2;
            short[] pcm = new short[samples];
            for (int i = 0; i < samples; i++) {
                pcm[i] = (short) ((buf[i * 2] & 0xFF) | (buf[i * 2 + 1] << 8));
            }
            return pcm;
        } catch (Exception e) {
            return null;
        }
    }

    /** Nearest-sample resample of interleaved stereo by a speed factor. */
    private short[] resample(short[] in, double speed) {
        if (in == null || Math.abs(speed - 1.0) < 0.001) return in;
        int inFrames = in.length / 2;
        int outFrames = (int) (inFrames / speed);
        if (outFrames <= 0) return new short[0];
        short[] out = new short[outFrames * 2];
        for (int f = 0; f < outFrames; f++) {
            int sf = (int) (f * speed);
            if (sf >= inFrames) sf = inFrames - 1;
            out[f * 2]     = in[sf * 2];
            out[f * 2 + 1] = in[sf * 2 + 1];
        }
        return out;
    }

    /** Average L/R into both channels (keeps the stereo container). */
    private byte[] toMono(byte[] stereo) {
        for (int i = 0; i + 3 < stereo.length; i += 4) {
            short l = (short) ((stereo[i] & 0xFF) | (stereo[i + 1] << 8));
            short r = (short) ((stereo[i + 2] & 0xFF) | (stereo[i + 3] << 8));
            short m = (short) ((l + r) / 2);
            stereo[i]     = (byte) (m & 0xFF);
            stereo[i + 1] = (byte) ((m >> 8) & 0xFF);
            stereo[i + 2] = (byte) (m & 0xFF);
            stereo[i + 3] = (byte) ((m >> 8) & 0xFF);
        }
        return stereo;
    }

    private void appendSilence(java.io.ByteArrayOutputStream out, long ms) {
        int frames = (int) (ms / 1000.0 * SR);
        byte[] silence = new byte[frames * FRAME_SIZE];
        out.write(silence, 0, silence.length);
    }

    private static int len(short[] a) { return a == null ? 0 : a.length; }

    private static short clamp(double v) {
        if (v > Short.MAX_VALUE) return Short.MAX_VALUE;
        if (v < Short.MIN_VALUE) return Short.MIN_VALUE;
        return (short) v;
    }
}
