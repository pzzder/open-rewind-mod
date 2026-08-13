package com.openrewind.replay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.openrewind.OpenRewind;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * The OpenRewind state-based replay container (<code>.orwr</code>), modelled on
 * ReplayMod's <code>.mcpr</code>. It is a zip holding:
 *
 * <ul>
 *   <li><b>recording.tmcpr</b> – the timestamped, clientbound PLAY packet stream:
 *       repeating <code>[int timestampMs][int length][length bytes]</code>, where
 *       the bytes are one decoded packet frame (varint id + payload).</li>
 *   <li><b>metadata.json</b> – {@link ReplayMetadata}.</li>
 *   <li><b>gui.json</b> – the client GUI open/close track ({@link GuiEvent}s).</li>
 *   <li><b>audio.wav</b> – optional mic/system audio side-track (Lunar keeps
 *       audio tracks too; the packet stream itself carries no sound).</li>
 * </ul>
 *
 * <h3>Recording path (write)</h3>
 * Packets arrive on the Netty I/O thread. To keep that thread free (this is the
 * whole point of state recording being cheap), {@link #writePacket} only enqueues
 * a copy; a dedicated writer thread streams it to a temp <code>.tmcpr</code>
 * file. {@link #finish} stops the writer and assembles the final zip.
 */
public class ReplayFile {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ---- write side --------------------------------------------------------
    private static final class Frame {
        final int ts; final byte[] data;
        Frame(int ts, byte[] data) { this.ts = ts; this.data = data; }
    }
    private static final Frame POISON = new Frame(-1, null);

    private final File outputZip;
    private final File tempTmcpr;
    private final BlockingQueue<Frame> queue = new LinkedBlockingQueue<Frame>(4096);
    private final List<GuiEvent> guiEvents = new ArrayList<GuiEvent>();
    private final List<BlockPlacement> placements = new ArrayList<BlockPlacement>();
    private volatile Thread writer;
    private volatile boolean writing;
    private volatile boolean paused;
    private volatile int packetCount;

    private ReplayFile(File outputZip, File tempTmcpr) {
        this.outputZip = outputZip;
        this.tempTmcpr = tempTmcpr;
    }

    /** Begin a new recording; opens the temp packet stream + writer thread. */
    public static ReplayFile beginRecording(File outputZip) throws IOException {
        File tmp = new File(outputZip.getParentFile(), outputZip.getName() + ".tmcpr.tmp");
        ReplayFile rf = new ReplayFile(outputZip, tmp);
        rf.startWriter();
        return rf;
    }

    private void startWriter() throws IOException {
        writing = true;
        final DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(tempTmcpr), 1 << 16));
        writer = new Thread(() -> {
            try {
                while (true) {
                    Frame f = queue.take();
                    if (f == POISON) break;
                    out.writeInt(f.ts);
                    out.writeInt(f.data.length);
                    out.write(f.data);
                    packetCount++;
                }
                out.flush();
            } catch (Exception e) {
                OpenRewind.logger.error("[OpenRewind] replay writer failed", e);
            } finally {
                try { out.close(); } catch (IOException ignored) { }
            }
        }, "OpenRewind-ReplayWriter");
        writer.setDaemon(true);
        writer.start();
    }

    /** Enqueue one clientbound packet frame (non-blocking, drops if overloaded). */
    public void writePacket(int timestampMs, byte[] frameBytes) {
        if (!writing || paused) return;
        if (!queue.offer(new Frame(timestampMs, frameBytes))) {
            OpenRewind.logger.warn("[OpenRewind] replay queue full – packet dropped");
        }
    }

    public void addGuiEvent(GuiEvent e) {
        synchronized (guiEvents) { guiEvents.add(e); }
    }

    /** Record one of your own block placements (client-prediction track). */
    public void addPlacement(BlockPlacement p) {
        synchronized (placements) { placements.add(p); }
    }

    /** Pause / resume packet writing (leaves a documented gap in the stream). */
    public void setPaused(boolean p) { this.paused = p; }
    public boolean isPaused() { return paused; }

    public int getPacketCount() { return packetCount; }

    /**
     * Stop recording and assemble the final zip. {@code audioWav} may be null.
     */
    public void finish(ReplayMetadata meta, File audioWav) {
        writing = false;
        try {
            queue.put(POISON);
            if (writer != null) writer.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(outputZip)))) {

            meta.hasAudio = audioWav != null && audioWav.exists();
            putEntry(zip, "metadata.json", GSON.toJson(meta).getBytes(StandardCharsets.UTF_8));

            synchronized (guiEvents) {
                putEntry(zip, "gui.json", GSON.toJson(guiEvents).getBytes(StandardCharsets.UTF_8));
            }
            synchronized (placements) {
                putEntry(zip, "predictions.json", GSON.toJson(placements).getBytes(StandardCharsets.UTF_8));
            }

            // stream the temp packet file in
            zip.putNextEntry(new ZipEntry("recording.tmcpr"));
            copy(tempTmcpr, zip);
            zip.closeEntry();

            if (meta.hasAudio) {
                zip.putNextEntry(new ZipEntry("audio.wav"));
                copy(audioWav, zip);
                zip.closeEntry();
            }
        } catch (IOException e) {
            OpenRewind.logger.error("[OpenRewind] failed to assemble replay zip", e);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tempTmcpr.delete();
        }
        OpenRewind.logger.info("[OpenRewind] replay saved: {} ({} packets)",
                outputZip.getName(), packetCount);
    }

    // ---- read side ---------------------------------------------------------

    /** One decoded record from a replay's packet stream. */
    public static final class TimedPacket {
        public final int    timestampMs;
        public final byte[] frameBytes;
        public TimedPacket(int ts, byte[] b) { this.timestampMs = ts; this.frameBytes = b; }
    }

    public static ReplayMetadata readMetadata(File zipFile) {
        try (ZipFile zf = new ZipFile(zipFile)) {
            ZipEntry e = zf.getEntry("metadata.json");
            if (e == null) return null;
            byte[] b = readAll(zf.getInputStream(e));
            return GSON.fromJson(new String(b, StandardCharsets.UTF_8), ReplayMetadata.class);
        } catch (IOException ex) {
            OpenRewind.logger.error("[OpenRewind] failed reading replay metadata", ex);
            return null;
        }
    }

    public static List<GuiEvent> readGuiEvents(File zipFile) {
        try (ZipFile zf = new ZipFile(zipFile)) {
            ZipEntry e = zf.getEntry("gui.json");
            if (e == null) return new ArrayList<GuiEvent>();
            byte[] b = readAll(zf.getInputStream(e));
            return GSON.fromJson(new String(b, StandardCharsets.UTF_8),
                    new TypeToken<List<GuiEvent>>() { }.getType());
        } catch (IOException ex) {
            return new ArrayList<GuiEvent>();
        }
    }

    /**
     * Open the packet stream for sequential reading. The returned
     * {@link PacketStream} must be closed by the caller.
     */
    public static java.util.List<BlockPlacement> readPlacements(File zipFile) {
        try (ZipFile zf = new ZipFile(zipFile)) {
            ZipEntry e = zf.getEntry("predictions.json");
            if (e == null) return new ArrayList<BlockPlacement>();
            byte[] b = readAll(zf.getInputStream(e));
            java.util.List<BlockPlacement> out = GSON.fromJson(
                    new String(b, StandardCharsets.UTF_8),
                    new TypeToken<List<BlockPlacement>>() { }.getType());
            return out != null ? out : new ArrayList<BlockPlacement>();
        } catch (IOException ex) {
            return new ArrayList<BlockPlacement>();
        }
    }

    public static PacketStream openPacketStream(File zipFile) throws IOException {
        ZipFile zf = new ZipFile(zipFile);
        ZipEntry e = zf.getEntry("recording.tmcpr");
        if (e == null) { zf.close(); throw new IOException("no recording.tmcpr in " + zipFile); }
        return new PacketStream(zf, new DataInputStream(zf.getInputStream(e)));
    }

    /** Sequential reader over the timestamped packet frames. */
    public static final class PacketStream implements AutoCloseable {
        private final ZipFile zf;
        private final DataInputStream in;
        PacketStream(ZipFile zf, DataInputStream in) { this.zf = zf; this.in = in; }

        /** @return the next packet, or null at end of stream. */
        public TimedPacket next() throws IOException {
            int ts;
            try { ts = in.readInt(); } catch (java.io.EOFException eof) { return null; }
            int len = in.readInt();
            byte[] data = new byte[len];
            in.readFully(data);
            return new TimedPacket(ts, data);
        }

        @Override public void close() throws IOException { in.close(); zf.close(); }
    }

    // ---- helpers -----------------------------------------------------------

    private static void putEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static void copy(File f, ZipOutputStream zip) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) != -1) zip.write(buf, 0, n);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[1 << 14];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
