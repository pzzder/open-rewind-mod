package com.openrewind.gui;

import com.openrewind.config.RewindConfig;
import com.openrewind.editor.Clip;
import com.openrewind.editor.ExportManager;
import com.openrewind.editor.Project;
import com.openrewind.editor.ProjectStore;
import com.openrewind.editor.VideoPlayer;
import com.openrewind.gui.widget.Timeline;
import com.openrewind.recording.RecordingMetadata;
import com.openrewind.util.GlFrameTexture;
import com.openrewind.util.JsonIO;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * The Rewind Editor – the open-source counterpart to Lunar's Rewind editor.
 *
 * <ul>
 *   <li><b>Live preview</b> of the selected clip (JCodec decode → GL texture)
 *       with play / pause / ±5&nbsp;s / split controls.</li>
 *   <li><b>Trim timeline</b> with draggable in/out handles and marker ticks.</li>
 *   <li><b>Clip properties</b>: speed, brightness, saturation, volume.</li>
 *   <li><b>Split tool</b> to cut the current clip at the playhead into two.</li>
 *   <li><b>Export settings</b>: resolution preset, orientation, framerate,
 *       mono/stereo, encoder (software / ffmpeg) + a live progress bar.</li>
 * </ul>
 */
public class GuiRewindEditor extends GuiScreen implements ExportManager.ProgressListener {

    private final GuiScreen parent;
    private Project project;

    private int clipIndex = 0;
    private RecordingMetadata meta;   // metadata of the current clip's source
    private Timeline timeline;
    private VideoPlayer player;
    private final GlFrameTexture texture = new GlFrameTexture();

    private int pvX, pvY, pvW, pvH;   // preview rectangle

    // export status (written from worker thread)
    private volatile float  exportProgress = -1f;
    private volatile String exportStage    = "";
    private volatile File   exportResult   = null;
    private volatile boolean exportMuxed   = false;
    private volatile String  exportError   = null;

    private static final int ID_PLAY = 1, ID_BACK5 = 2, ID_FWD5 = 3, ID_SPLIT = 4, ID_RESET = 5;
    private static final int ID_SPEED_D = 10, ID_SPEED_U = 11;
    private static final int ID_BRI_D = 12, ID_BRI_U = 13;
    private static final int ID_SAT_D = 14, ID_SAT_U = 15;
    private static final int ID_VOL_D = 16, ID_VOL_U = 17;
    private static final int ID_RES = 20, ID_FPS = 21, ID_AUDIO = 22, ID_ENCODER = 23, ID_ORI = 24;
    private static final int ID_PREV_CLIP = 30, ID_NEXT_CLIP = 31, ID_DEL_CLIP = 32;
    private static final int ID_SAVE = 40, ID_EXPORT = 41, ID_BACK = 42;

    public GuiRewindEditor(Project project) {
        this.parent = null;
        this.project = project;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        if (project == null || project.clips.isEmpty()) {
            mc.displayGuiScreen(new GuiRecordingList(this));
            return;
        }
        clipIndex = Math.max(0, Math.min(clipIndex, project.clips.size() - 1));
        loadClip(clipIndex);

        pvX = 8; pvY = 26; pvW = width - 190; pvH = height - 150;

        int ty = pvY + pvH + 4;
        buttonList.add(new GuiButton(ID_BACK5, pvX,       ty, 44, 20, "-5s"));
        buttonList.add(new GuiButton(ID_PLAY,  pvX + 48,  ty, 60, 20, "Play"));
        buttonList.add(new GuiButton(ID_FWD5,  pvX + 112, ty, 44, 20, "+5s"));
        buttonList.add(new GuiButton(ID_SPLIT, pvX + 160, ty, 70, 20, "\u2702 Split"));
        buttonList.add(new GuiButton(ID_RESET, pvX + 234, ty, 80, 20, "Reset trim"));

        int rx = width - 176, rw = 168, ry = 30, rh = 20, gap = 24;
        buttonList.add(new GuiButton(ID_SPEED_D, rx,          ry, 20, rh, "-"));
        buttonList.add(new GuiButton(ID_SPEED_U, rx + rw - 20, ry, 20, rh, "+")); ry += gap;
        buttonList.add(new GuiButton(ID_BRI_D,   rx,          ry, 20, rh, "-"));
        buttonList.add(new GuiButton(ID_BRI_U,   rx + rw - 20, ry, 20, rh, "+")); ry += gap;
        buttonList.add(new GuiButton(ID_SAT_D,   rx,          ry, 20, rh, "-"));
        buttonList.add(new GuiButton(ID_SAT_U,   rx + rw - 20, ry, 20, rh, "+")); ry += gap;
        buttonList.add(new GuiButton(ID_VOL_D,   rx,          ry, 20, rh, "-"));
        buttonList.add(new GuiButton(ID_VOL_U,   rx + rw - 20, ry, 20, rh, "+")); ry += gap + 6;

        buttonList.add(new GuiButton(ID_RES,     rx, ry, rw, rh, "Res: " + project.resolutionPreset)); ry += gap;
        buttonList.add(new GuiButton(ID_ORI,     rx, ry, rw, rh, "Orient: " + project.orientation));    ry += gap;
        buttonList.add(new GuiButton(ID_FPS,     rx, ry, rw, rh, "FPS: " + project.exportFps));          ry += gap;
        buttonList.add(new GuiButton(ID_AUDIO,   rx, ry, rw, rh, "Audio: " + (project.audioMono ? "Mono" : "Stereo"))); ry += gap;
        buttonList.add(new GuiButton(ID_ENCODER, rx, ry, rw, rh, "Encoder: " + project.exportEncoder));  ry += gap + 6;

        buttonList.add(new GuiButton(ID_PREV_CLIP, rx,       ry, 52, rh, "\u25C0 Clip"));
        buttonList.add(new GuiButton(ID_NEXT_CLIP, rx + 58,  ry, 52, rh, "Clip \u25B6"));
        buttonList.add(new GuiButton(ID_DEL_CLIP,  rx + 116, ry, 52, rh, "\u00A7cDel"));

        int by = height - 24;
        buttonList.add(new GuiButton(ID_SAVE,   pvX,        by, 80,  20, "Save"));
        buttonList.add(new GuiButton(ID_EXPORT, pvX + 84,   by, 120, 20, "\u00A7aExport"));
        buttonList.add(new GuiButton(ID_BACK,   width - 64, by, 56,  20, "Back"));
    }

    private void loadClip(int idx) {
        Clip clip = project.clips.get(idx);
        meta = loadMeta(clip.sourceId);
        long dur = meta != null ? meta.durationMs : clip.outMs;

        int tlX = 8, tlW = width - 190, tlY = height - 96, tlH = 18;
        timeline = new Timeline(tlX, tlY, tlW, tlH, dur);
        timeline.setRange(clip.inMs, clip.outMs);
        if (meta != null && meta.markers != null) {
            long[] mk = new long[meta.markers.size()];
            for (int i = 0; i < mk.length; i++) mk[i] = meta.markers.get(i).timeMs;
            timeline.markerMs = mk;
        }

        if (player != null) player.close();
        File mp4 = new File(RewindConfig.getRecordingsDir(), clip.sourceId + ".mp4");
        player = new VideoPlayer(mp4, meta != null && meta.fps > 0 ? meta.fps : 30, dur);
        player.open();
        player.seek(clip.inMs);
    }

    private Clip current() { return project.clips.get(clipIndex); }

    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        Clip c = current();
        switch (b.id) {
            case ID_PLAY:  player.togglePlay(); b.displayString = player.isPlaying() ? "Pause" : "Play"; break;
            case ID_BACK5: player.skip(-5000); break;
            case ID_FWD5:  player.skip(5000); break;
            case ID_SPLIT: splitAtPlayhead(); break;
            case ID_RESET: timeline.setRange(0, meta != null ? meta.durationMs : c.outMs); break;

            case ID_SPEED_D: c.speed = round2(clampD(c.speed - 0.25, 0.25, 4.0)); break;
            case ID_SPEED_U: c.speed = round2(clampD(c.speed + 0.25, 0.25, 4.0)); break;
            case ID_BRI_D:   c.brightness = round2(clampD(c.brightness - 0.1, -1.0, 1.0)); break;
            case ID_BRI_U:   c.brightness = round2(clampD(c.brightness + 0.1, -1.0, 1.0)); break;
            case ID_SAT_D:   c.saturation = round2(clampD(c.saturation - 0.1, 0.0, 2.0)); break;
            case ID_SAT_U:   c.saturation = round2(clampD(c.saturation + 0.1, 0.0, 2.0)); break;
            case ID_VOL_D:   project.masterVolume = round2(clampD(project.masterVolume - 0.1, 0.0, 2.0)); break;
            case ID_VOL_U:   project.masterVolume = round2(clampD(project.masterVolume + 0.1, 0.0, 2.0)); break;

            case ID_RES:     cycleRes(b); break;
            case ID_ORI:     project.orientation = project.orientation.equals("Horizontal") ? "Vertical" : "Horizontal";
                             b.displayString = "Orient: " + project.orientation; break;
            case ID_FPS: {
                int[] steps = {24, 30, 48, 60, 120}; int cur = 0;
                for (int i = 0; i < steps.length; i++) if (steps[i] == project.exportFps) cur = i;
                project.exportFps = steps[(cur + 1) % steps.length];
                b.displayString = "FPS: " + project.exportFps; break;
            }
            case ID_AUDIO:   project.audioMono = !project.audioMono;
                             b.displayString = "Audio: " + (project.audioMono ? "Mono" : "Stereo"); break;
            case ID_ENCODER: project.exportEncoder = project.exportEncoder.equals("software") ? "ffmpeg" : "software";
                             b.displayString = "Encoder: " + project.exportEncoder; break;

            case ID_PREV_CLIP: syncClip(); if (clipIndex > 0) { clipIndex--; initGui(); } break;
            case ID_NEXT_CLIP: syncClip(); if (clipIndex < project.clips.size() - 1) { clipIndex++; initGui(); } break;
            case ID_DEL_CLIP:  deleteCurrentClip(); break;

            case ID_SAVE:   syncClip(); ProjectStore.saveProject(project); break;
            case ID_BACK:   syncClip(); ProjectStore.saveProject(project);
                            if (player != null) player.close(); texture.dispose();
                            mc.displayGuiScreen(new GuiRecordingList(this)); break;
            case ID_EXPORT:
                if (exportProgress < 0) {
                    syncClip(); ProjectStore.saveProject(project);
                    exportProgress = 0f; exportStage = "Starting"; exportResult = null; exportError = null;
                    new ExportManager(project, this).exportAsync();
                }
                break;
        }
    }

    private void cycleRes(GuiButton b) {
        String[] r = {"Source", "480p", "720p", "1080p", "1440p"};
        int cur = 0;
        for (int i = 0; i < r.length; i++) if (r[i].equals(project.resolutionPreset)) cur = i;
        project.resolutionPreset = r[(cur + 1) % r.length];
        b.displayString = "Res: " + project.resolutionPreset;
    }

    /** Split the current clip at the preview playhead into two clips. */
    private void splitAtPlayhead() {
        syncClip();
        Clip c = current();
        long cut = player.getPositionMs();
        if (cut <= c.inMs + 50 || cut >= c.outMs - 50) return;
        Clip right = new Clip(c.sourceId, cut, c.outMs);
        right.speed = c.speed; right.brightness = c.brightness; right.saturation = c.saturation;
        c.outMs = cut;
        project.clips.add(clipIndex + 1, right);
        initGui();
    }

    private void deleteCurrentClip() {
        if (project.clips.size() <= 1) return;
        project.clips.remove(clipIndex);
        clipIndex = Math.max(0, clipIndex - 1);
        initGui();
    }

    private void syncClip() {
        if (timeline == null) return;
        Clip c = current();
        c.inMs  = timeline.getInMs();
        c.outMs = timeline.getOutMs();
    }

    // ---- mouse -> timeline + preview scrub --------------------------------
    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        super.mouseClicked(mx, my, btn);
        if (timeline != null) {
            timeline.mouseClicked(mx, my);
            if (my >= timeline.y - 6 && my <= timeline.y + timeline.height + 6
                    && mx >= timeline.x && mx <= timeline.x + timeline.width) {
                long t = (long) ((double) (mx - timeline.x) / timeline.width
                        * (meta != null ? meta.durationMs : 1));
                player.seek(t);
            }
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int btn, long since) {
        if (timeline != null) timeline.mouseDragged(mx, my);
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        super.mouseReleased(mx, my, state);
        if (timeline != null) timeline.mouseReleased();
    }

    // ---- render ------------------------------------------------------------
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawString(fontRendererObj, "\u00A7lRewind Editor", 8, 10, 0xFFFFFF);

        drawRect(pvX - 1, pvY - 1, pvX + pvW + 1, pvY + pvH + 1, 0xFF101010);
        if (player != null && player.getCurrentFrame() != null) {
            texture.upload(player.getCurrentFrame());
            int fw = texture.getWidth(), fh = texture.getHeight();
            double scale = Math.min((double) pvW / fw, (double) pvH / fh);
            int dw = (int) (fw * scale), dh = (int) (fh * scale);
            int dx = pvX + (pvW - dw) / 2, dy = pvY + (pvH - dh) / 2;
            texture.drawScaled(dx, dy, dw, dh);
        } else {
            drawCenteredString(fontRendererObj, "\u00A77Decoding preview\u2026",
                    pvX + pvW / 2, pvY + pvH / 2, 0xAAAAAA);
        }
        if (player != null) {
            drawString(fontRendererObj, fmt(player.getPositionMs()) + " / "
                    + fmt(meta != null ? meta.durationMs : 0), pvX + 2, pvY + pvH - 12, 0xFFFF55);
        }

        int rx = width - 176, ry = 30;
        Clip c = current();
        label(rx, ry, "Speed", c.speed + "x"); ry += 24;
        label(rx, ry, "Brightness", String.valueOf(c.brightness)); ry += 24;
        label(rx, ry, "Saturation", String.valueOf(c.saturation)); ry += 24;
        label(rx, ry, "Volume", String.valueOf(project.masterVolume));

        drawString(fontRendererObj, "\u00A77Clip " + (clipIndex + 1) + "/" + project.clips.size()
                + "  out " + fmt(project.totalOutputLengthMs()), rx, height - 44, 0xAAAAAA);

        if (timeline != null) {
            drawString(fontRendererObj, "In " + fmt(timeline.getInMs())
                    + "   Out " + fmt(timeline.getOutMs()), 8, height - 110, 0xFFFF55);
            timeline.draw();
        }

        if (exportProgress >= 0f) {
            int bw = pvW, bx = pvX, by = pvY + pvH / 2 - 8;
            drawRect(bx, by, bx + bw, by + 16, 0xC0000000);
            drawRect(bx, by, bx + (int) (bw * Math.min(1f, exportProgress)), by + 16, 0xFF44AA44);
            drawCenteredString(fontRendererObj, exportStage + "  " + (int) (exportProgress * 100) + "%",
                    bx + bw / 2, by + 4, 0xFFFFFF);
        }
        if (exportResult != null) {
            drawCenteredString(fontRendererObj, "\u00A7aExported: " + exportResult.getName()
                    + (exportMuxed ? " (with audio)" : " (video + separate WAV)"),
                    pvX + pvW / 2, pvY + pvH / 2 + 16, 0x55FF55);
        }
        if (exportError != null) {
            drawCenteredString(fontRendererObj, "\u00A7cExport failed: " + exportError,
                    pvX + pvW / 2, pvY + pvH / 2 + 16, 0xFF5555);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void label(int rx, int ry, String name, String val) {
        drawCenteredString(fontRendererObj, "\u00A7f" + name + ": \u00A7e" + val, rx + 84, ry + 6, 0xFFFFFF);
    }

    private RecordingMetadata loadMeta(String id) {
        return JsonIO.read(new File(RewindConfig.getRecordingsDir(), id + ".json"), RecordingMetadata.class);
    }

    private static String fmt(long ms) {
        long s = ms / 1000, cs = (ms % 1000) / 10;
        return String.format(Locale.ROOT, "%d:%02d.%02d", s / 60, s % 60, cs);
    }

    private static double clampD(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double round2(double v) { return Math.round(v * 100) / 100.0; }

    @Override
    public void onGuiClosed() {
        if (player != null) player.close();
        texture.dispose();
    }

    @Override
    public boolean doesGuiPauseGame() { return true; }

    // ---- ExportManager.ProgressListener (off-thread) ----------------------
    @Override public void onProgress(float f, String stage) { this.exportProgress = f; this.exportStage = stage; }
    @Override public void onComplete(File out, boolean muxed) { this.exportProgress = 1f; this.exportResult = out; this.exportMuxed = muxed; }
    @Override public void onError(Exception e) { this.exportProgress = -1f; this.exportError = e.getMessage() == null ? e.toString() : e.getMessage(); }
}
