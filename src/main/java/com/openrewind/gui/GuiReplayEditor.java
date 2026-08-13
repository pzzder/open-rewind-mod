package com.openrewind.gui;

import com.openrewind.replay.edit.AudioTrack;
import com.openrewind.replay.edit.OverlayLayer;
import com.openrewind.replay.edit.ReplayCamera;
import com.openrewind.replay.edit.ReplayEditProject;
import com.openrewind.replay.edit.ReplayStore;
import com.openrewind.replay.export.ExportDriver;
import com.openrewind.replay.export.ReplayExporter;
import com.openrewind.replay.playback.ReplayHandler;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.File;
import java.io.IOException;

/**
 * The state-engine editor's controls — the open-source counterpart to Lunar
 * Rewind's editor panels:
 *
 * <ul>
 *   <li><b>Camera</b>: Sync/Flight, FOV, Speed, Force Hide HUD</li>
 *   <li><b>Properties</b>: brightness / saturation / zoom (keyframable), shake</li>
 *   <li><b>Effects</b>: chroma key, vignette, shader overlay, render scoreboard / boss bar</li>
 *   <li><b>Layers</b>: add text / image overlays; import an audio track</li>
 *   <li><b>Export</b>: framerate / resolution / orientation / encoder → render MP4</li>
 * </ul>
 *
 * <p>A time cursor (\u25C0 \u25B6) picks where "Add keyframe" drops a keyframe for a
 * property, giving real per-property keyframe curves. Flight-path keyframes and
 * the export render run against the live replay session ({@code [LIVE-INTEGRATION]}).</p>
 */
public class GuiReplayEditor extends GuiScreen implements ReplayExporter.ProgressListener {

    private final GuiScreen parent;
    private final File replayFile;
    private ReplayEditProject edit;
    private long cursorMs = 0;

    private volatile float  exportProgress = -1f;
    private volatile String exportStage = "";
    private volatile File   exportResult = null;
    private volatile String exportError = null;

    private static final int ID_MODE=1, ID_FOV_D=2, ID_FOV_U=3, ID_SPD_D=4, ID_SPD_U=5, ID_HIDEHUD=6;
    private static final int ID_BRI_D=7, ID_BRI_U=8, ID_SAT_D=9, ID_SAT_U=10, ID_ZOOM_D=11, ID_ZOOM_U=12, ID_SHAKE_D=13, ID_SHAKE_U=14;
    private static final int ID_CUR_D=15, ID_CUR_U=16, ID_KEY_BRI=17, ID_KEY_SAT=18, ID_KEY_ZOOM=19, ID_KEY_CLEAR=20;
    private static final int ID_FX_CHROMA=21, ID_FX_VIGNETTE=22, ID_FX_SHADER=23, ID_FX_SCORE=24, ID_FX_BOSS=25;
    private static final int ID_ADD_TEXT=26, ID_ADD_IMAGE=27, ID_ADD_AUDIO=28, ID_CLR_LAYERS=29;
    private static final int ID_FPS=30, ID_RES=31, ID_ORI=32, ID_ENC=33;
    private static final int ID_SAVE=40, ID_EXPORT=41, ID_BACK=42;

    public GuiReplayEditor(GuiScreen parent, File replayFile) {
        this.parent = parent; this.replayFile = replayFile;
    }

    @Override
    public void initGui() {
        if (edit == null) edit = ReplayStore.loadOrCreate(replayFile);
        buttonList.clear();
        int cx = width / 2, y = 30, row = 21;

        buttonList.add(new GuiButton(ID_MODE, cx-205, y, 95, 20, "" + edit.camera.mode));
        buttonList.add(new GuiButton(ID_FOV_D, cx-105, y, 20, 20, "-"));
        buttonList.add(new GuiButton(ID_FOV_U, cx-40, y, 20, 20, "+"));
        buttonList.add(new GuiButton(ID_SPD_D, cx+10, y, 20, 20, "-"));
        buttonList.add(new GuiButton(ID_SPD_U, cx+75, y, 20, 20, "+"));
        buttonList.add(new GuiButton(ID_HIDEHUD, cx+100, y, 105, 20, edit.camera.hideHud?"HUD:\u00A7cHID":"HUD:\u00A7aShow")); y += row;

        buttonList.add(new GuiButton(ID_BRI_D, cx-205, y, 20, 20, "-"));
        buttonList.add(new GuiButton(ID_BRI_U, cx-140, y, 20, 20, "+"));
        buttonList.add(new GuiButton(ID_SAT_D, cx-105, y, 20, 20, "-"));
        buttonList.add(new GuiButton(ID_SAT_U, cx-40, y, 20, 20, "+"));
        buttonList.add(new GuiButton(ID_ZOOM_D, cx+10, y, 20, 20, "-"));
        buttonList.add(new GuiButton(ID_ZOOM_U, cx+75, y, 20, 20, "+"));
        buttonList.add(new GuiButton(ID_SHAKE_D, cx+100, y, 20, 20, "-"));
        buttonList.add(new GuiButton(ID_SHAKE_U, cx+185, y, 20, 20, "+")); y += row;

        buttonList.add(new GuiButton(ID_CUR_D, cx-205, y, 20, 20, "\u25C0"));
        buttonList.add(new GuiButton(ID_CUR_U, cx-140, y, 20, 20, "\u25B6"));
        buttonList.add(new GuiButton(ID_KEY_BRI, cx-115, y, 70, 20, "+Key Bri"));
        buttonList.add(new GuiButton(ID_KEY_SAT, cx-40, y, 70, 20, "+Key Sat"));
        buttonList.add(new GuiButton(ID_KEY_ZOOM, cx+35, y, 80, 20, "+Key Zoom"));
        buttonList.add(new GuiButton(ID_KEY_CLEAR, cx+120, y, 85, 20, "Clr keys")); y += row;

        buttonList.add(new GuiButton(ID_FX_CHROMA, cx-205, y, 80, 20, fx("Chroma", ReplayEditProject.EffectType.CHROMA_KEY)));
        buttonList.add(new GuiButton(ID_FX_VIGNETTE, cx-120, y, 80, 20, fx("Vignette", ReplayEditProject.EffectType.VIGNETTE)));
        buttonList.add(new GuiButton(ID_FX_SHADER, cx-35, y, 80, 20, fx("Shader", ReplayEditProject.EffectType.SHADER_OVERLAY)));
        buttonList.add(new GuiButton(ID_FX_SCORE, cx+50, y, 75, 20, fx("Score", ReplayEditProject.EffectType.RENDER_SCOREBOARD)));
        buttonList.add(new GuiButton(ID_FX_BOSS, cx+130, y, 75, 20, fx("Boss", ReplayEditProject.EffectType.RENDER_BOSSBAR))); y += row;

        buttonList.add(new GuiButton(ID_ADD_TEXT, cx-205, y, 95, 20, "+ Text"));
        buttonList.add(new GuiButton(ID_ADD_IMAGE, cx-105, y, 95, 20, "+ Image"));
        buttonList.add(new GuiButton(ID_ADD_AUDIO, cx+10, y, 95, 20, "+ Audio"));
        buttonList.add(new GuiButton(ID_CLR_LAYERS, cx+110, y, 95, 20, "Clr layers")); y += row + 4;

        buttonList.add(new GuiButton(ID_FPS, cx-205, y, 95, 20, "FPS " + edit.exportFps));
        buttonList.add(new GuiButton(ID_RES, cx-105, y, 95, 20, edit.exportHeight + "p"));
        buttonList.add(new GuiButton(ID_ORI, cx+10, y, 95, 20, edit.orientation));
        buttonList.add(new GuiButton(ID_ENC, cx+110, y, 95, 20, edit.encoder)); y += row + 6;

        buttonList.add(new GuiButton(ID_SAVE, cx-205, y, 90, 20, "Save"));
        buttonList.add(new GuiButton(ID_EXPORT, cx-110, y, 210, 20, "\u00A7aExport MP4"));
        buttonList.add(new GuiButton(ID_BACK, cx+105, y, 100, 20, "Back"));
    }

    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        ReplayCamera c = edit.camera;
        switch (b.id) {
            case ID_MODE: c.mode = c.mode==ReplayCamera.Mode.SYNC?ReplayCamera.Mode.FLIGHT:ReplayCamera.Mode.SYNC; b.displayString=""+c.mode; break;
            case ID_HIDEHUD: c.hideHud=!c.hideHud; b.displayString=c.hideHud?"HUD:\u00A7cHID":"HUD:\u00A7aShow"; break;
            case ID_FOV_D: c.fov=clampF(c.fov-5,30,110); break;
            case ID_FOV_U: c.fov=clampF(c.fov+5,30,110); break;
            case ID_SPD_D: c.speed=r2(clampD(c.speed-0.25,0.1,8)); break;
            case ID_SPD_U: c.speed=r2(clampD(c.speed+0.25,0.1,8)); break;
            case ID_BRI_D: edit.brightness.defaultValue=r2(clampD(edit.brightness.defaultValue-0.1,-1,1)); break;
            case ID_BRI_U: edit.brightness.defaultValue=r2(clampD(edit.brightness.defaultValue+0.1,-1,1)); break;
            case ID_SAT_D: edit.saturation.defaultValue=r2(clampD(edit.saturation.defaultValue-0.1,0,2)); break;
            case ID_SAT_U: edit.saturation.defaultValue=r2(clampD(edit.saturation.defaultValue+0.1,0,2)); break;
            case ID_ZOOM_D: edit.zoom.defaultValue=r2(clampD(edit.zoom.defaultValue-0.1,0.5,4)); break;
            case ID_ZOOM_U: edit.zoom.defaultValue=r2(clampD(edit.zoom.defaultValue+0.1,0.5,4)); break;
            case ID_SHAKE_D: edit.cameraShake=r2(clampD(edit.cameraShake-0.5,0,10)); break;
            case ID_SHAKE_U: edit.cameraShake=r2(clampD(edit.cameraShake+0.5,0,10)); break;
            case ID_CUR_D: cursorMs=Math.max(0,cursorMs-1000); break;
            case ID_CUR_U: cursorMs+=1000; break;
            case ID_KEY_BRI: edit.brightness.add(cursorMs, edit.brightness.defaultValue); break;
            case ID_KEY_SAT: edit.saturation.add(cursorMs, edit.saturation.defaultValue); break;
            case ID_KEY_ZOOM: edit.zoom.add(cursorMs, edit.zoom.defaultValue); break;
            case ID_KEY_CLEAR: edit.brightness.clear(); edit.saturation.clear(); edit.zoom.clear(); break;
            case ID_FX_CHROMA: edit.toggleEffect(ReplayEditProject.EffectType.CHROMA_KEY); b.displayString=fx("Chroma",ReplayEditProject.EffectType.CHROMA_KEY); break;
            case ID_FX_VIGNETTE: edit.toggleEffect(ReplayEditProject.EffectType.VIGNETTE); b.displayString=fx("Vignette",ReplayEditProject.EffectType.VIGNETTE); break;
            case ID_FX_SHADER: edit.toggleEffect(ReplayEditProject.EffectType.SHADER_OVERLAY); b.displayString=fx("Shader",ReplayEditProject.EffectType.SHADER_OVERLAY); break;
            case ID_FX_SCORE: edit.toggleEffect(ReplayEditProject.EffectType.RENDER_SCOREBOARD); b.displayString=fx("Score",ReplayEditProject.EffectType.RENDER_SCOREBOARD); break;
            case ID_FX_BOSS: edit.toggleEffect(ReplayEditProject.EffectType.RENDER_BOSSBAR); b.displayString=fx("Boss",ReplayEditProject.EffectType.RENDER_BOSSBAR); break;
            case ID_ADD_TEXT: { OverlayLayer o=new OverlayLayer(); o.type=OverlayLayer.Type.TEXT; o.content="Text"; o.startMs=cursorMs; edit.overlays.add(o); break; }
            case ID_ADD_IMAGE: { OverlayLayer o=new OverlayLayer(); o.type=OverlayLayer.Type.IMAGE; o.content=new File(com.openrewind.config.RewindConfig.getOutputRoot(),"import/image.png").getAbsolutePath(); o.startMs=cursorMs; edit.overlays.add(o); break; }
            case ID_ADD_AUDIO: edit.audioTracks.add(new AudioTrack(new File(com.openrewind.config.RewindConfig.getOutputRoot(),"import/audio.wav").getAbsolutePath(), cursorMs, 1.0)); break;
            case ID_CLR_LAYERS: edit.overlays.clear(); edit.audioTracks.clear(); break;
            case ID_FPS: { int[] s={24,30,60,120,144}; edit.exportFps=next(s,edit.exportFps); b.displayString="FPS "+edit.exportFps; break; }
            case ID_RES: { int[] s={480,720,1080,1440,2160}; edit.exportHeight=next(s,edit.exportHeight); b.displayString=edit.exportHeight+"p"; break; }
            case ID_ORI: edit.orientation=edit.orientation.equals("Horizontal")?"Vertical":"Horizontal"; b.displayString=edit.orientation; break;
            case ID_ENC: edit.encoder=edit.encoder.equals("software")?"ffmpeg":"software"; b.displayString=edit.encoder; break;
            case ID_SAVE: ReplayStore.save(replayFile, edit); break;
            case ID_BACK: ReplayStore.save(replayFile, edit); mc.displayGuiScreen(parent); return;
            case ID_EXPORT: startExport(); break;
        }
    }

    private void startExport() {
        if (ExportDriver.isBusy() || exportProgress >= 0f) return;
        ReplayStore.save(replayFile, edit);
        exportProgress = 0f; exportStage = "Starting"; exportResult = null; exportError = null;
        ReplayHandler handler = new ReplayHandler(replayFile);
        int h = edit.exportHeight & ~1;
        int w = ("Vertical".equalsIgnoreCase(edit.orientation) ? h * 9 / 16 : h * 16 / 9) & ~1;
        ReplayExporter exporter = new ReplayExporter(handler, edit.exportFps, w, h, this);
        exporter.setEditProject(edit);
        try { exporter.begin(); ExportDriver.start(exporter); }
        catch (Exception e) { onError(e); }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float pt) {
        drawDefaultBackground();
        int cx = width / 2;
        drawCenteredString(fontRendererObj, "\u00A7lRewind Editor", cx, 12, 0xFFFFFF);
        String cam = "FOV " + (int) edit.camera.fov + "  Spd " + edit.camera.speed + "x";
        String props = "Bri " + edit.brightness.defaultValue + "  Sat " + edit.saturation.defaultValue
                + "  Zoom " + edit.zoom.defaultValue + "  Shake " + edit.cameraShake;
        String tl = "Cursor " + (cursorMs / 1000) + "s   keys[b" + edit.brightness.keys.size()
                + " s" + edit.saturation.keys.size() + " z" + edit.zoom.keys.size() + "]"
                + "   layers " + edit.overlays.size() + "   audio " + edit.audioTracks.size()
                + "   camKeys " + edit.camera.keyframes.size();
        drawCenteredString(fontRendererObj, "\u00A77" + cam, cx, height - 78, 0xAAAAAA);
        drawCenteredString(fontRendererObj, "\u00A77" + props, cx, height - 66, 0xAAAAAA);
        drawCenteredString(fontRendererObj, "\u00A77" + tl, cx, height - 54, 0xAAAAAA);

        if (exportProgress >= 0f) {
            int bw = width - 120, bx = 60, by = height / 2 - 8;
            drawRect(bx, by, bx + bw, by + 16, 0xC0000000);
            drawRect(bx, by, bx + (int) (bw * Math.min(1f, exportProgress)), by + 16, 0xFF44AA44);
            drawCenteredString(fontRendererObj, exportStage + " " + (int) (exportProgress * 100) + "%", cx, by + 4, 0xFFFFFF);
        }
        if (exportResult != null) drawCenteredString(fontRendererObj, "\u00A7aExported: " + exportResult.getName(), cx, height / 2 + 16, 0x55FF55);
        if (exportError != null)  drawCenteredString(fontRendererObj, "\u00A7cExport failed: " + exportError, cx, height / 2 + 16, 0xFF5555);

        super.drawScreen(mouseX, mouseY, pt);
    }

    private String fx(String n, ReplayEditProject.EffectType t) {
        return (edit.hasEffect(t) ? "\u00A7a" : "\u00A77") + n;
    }
    private static float clampF(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double clampD(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double r2(double v) { return Math.round(v * 100) / 100.0; }
    private static int next(int[] s, int cur) { int i=0; for(int k=0;k<s.length;k++) if(s[k]==cur) i=k; return s[(i+1)%s.length]; }

    @Override public boolean doesGuiPauseGame() { return true; }
    @Override public void onProgress(float f, String s) { exportProgress=f; exportStage=s; }
    @Override public void onComplete(File out) { exportProgress=1f; exportResult=out; }
    @Override public void onError(Exception e) { exportProgress=-1f; exportError = e.getMessage()==null?e.toString():e.getMessage(); }
}
