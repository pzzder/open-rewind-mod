package com.openrewind.replay;

import com.openrewind.OpenRewind;
import com.openrewind.config.RewindConfig;
import com.openrewind.recording.AudioRecorder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.network.NetworkManager;

import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Owns the lifecycle of a state-based recording:
 *
 * <ul>
 *   <li>On {@link FMLNetworkEvent.ClientConnectedToServerEvent} it installs the
 *       {@link PacketRecorder} into the connection's Netty pipeline (if
 *       auto-record or a manual recording is active) and opens a {@link ReplayFile}.</li>
 *   <li>It mirrors {@link com.openrewind.recording.AudioRecorder} so mic/system
 *       sound is kept as a side-track (Lunar's Rewind has audio tracks too).</li>
 *   <li>It records the client GUI open/close track via {@link GuiOpenEvent} so a
 *       POV export can re-open the inventory / chest the player had open.</li>
 *   <li>On disconnect (or manual stop) it finalises the {@code .orwr} container.</li>
 * </ul>
 *
 * <p>This is the state-mode counterpart to the pixel-mode
 * {@link com.openrewind.recording.RecordingManager}; which one is active is
 * chosen by {@link RewindConfig#stateRecording}.</p>
 */
public class ReplayRecordingManager {

    private static final ReplayRecordingManager INSTANCE = new ReplayRecordingManager();
    public static ReplayRecordingManager get() { return INSTANCE; }
    private ReplayRecordingManager() { }

    private volatile boolean recording = false;
    private ReplayFile   replay;
    private ReplayMetadata meta;
    private AudioRecorder audio;
    private long startMillis;
    private String currentId;
    private NetworkManager hookedManager;

    private boolean paused;
    private long pausedAccumMs;
    private long pauseStartedAt;
    private final java.util.List<Long> markers = new java.util.ArrayList<Long>();
    private String flashMessage;
    private long   flashUntil;

    public boolean isRecording() { return recording; }
    public boolean isPaused()    { return paused; }
    public int     markerCount() { return markers.size(); }
    public long elapsedMs() {
        if (!recording) return 0;
        long p = pausedAccumMs + (paused ? System.currentTimeMillis() - pauseStartedAt : 0);
        return System.currentTimeMillis() - startMillis - p;
    }

    public String getFlashMessage() {
        return System.currentTimeMillis() < flashUntil ? flashMessage : null;
    }

    private void flash(String msg) {
        this.flashMessage = msg;
        this.flashUntil = System.currentTimeMillis() + 2000;
    }

    /** Drop a marker at the current recording time. */
    public synchronized void addMarker() {
        if (!recording) return;
        markers.add(elapsedMs());
        flash("\u00A7bMarker #" + markers.size());
    }

    /**
     * Pause / resume packet capture. NOTE: pausing leaves a gap in the packet
     * stream, so anything that changed while paused won't be reflected on
     * playback until the next full update — the same caveat ReplayMod documents.
     */
    public synchronized void togglePause() {
        if (!recording || replay == null) return;
        if (paused) {
            pausedAccumMs += System.currentTimeMillis() - pauseStartedAt;
            paused = false;
            replay.setPaused(false);
            flash("\u00A7aResumed");
        } else {
            pauseStartedAt = System.currentTimeMillis();
            paused = true;
            replay.setPaused(true);
            flash("\u00A7ePaused");
        }
    }

    // ------------------------------------------------------------------------
    //  Connection lifecycle
    // ------------------------------------------------------------------------

    @SubscribeEvent
    public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        if (!RewindConfig.stateRecording) return;
        // auto-record from the moment of connection captures the full join +
        // chunk stream, which is required to rebuild the world on playback.
        if (RewindConfig.autoRecordOnJoin) {
            startRecording(event.getManager());
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        if (recording) stopRecording();
    }

    // ------------------------------------------------------------------------
    //  Manual control (Shift+R when in state mode)
    // ------------------------------------------------------------------------

    /** Toggle recording using the client's current live connection. */
    public synchronized void toggle() {
        if (recording) { stopRecording(); return; }
        NetworkManager nm = currentManager();
        if (nm == null) {
            OpenRewind.logger.warn("[OpenRewind] not connected – cannot start state recording");
            return;
        }
        startRecording(nm);
    }

    private NetworkManager currentManager() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null) return null;
        return mc.getNetHandler().getNetworkManager();
    }

    public synchronized void startRecording(NetworkManager manager) {
        if (recording || manager == null || manager.channel() == null) return;
        try {
            currentId = "replay_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT)
                    .format(new Date());
            File out = new File(RewindConfig.getReplaysDir(), currentId + ".orwr");
            replay = ReplayFile.beginRecording(out);
            startMillis = System.currentTimeMillis();

            // install the netty capture handler just before the vanilla decoder
            hookedManager = manager;
            manager.channel().pipeline().addBefore("decoder",
                    PacketRecorder.HANDLER_NAME, new PacketRecorder(replay, startMillis));

            // audio side-track (kept like Lunar's audio tracks)
            audio = new AudioRecorder(RewindConfig.getReplaysDir(), currentId);
            audio.start();

            meta = new ReplayMetadata();
            meta.date = startMillis;
            meta.singleplayer = manager.isLocalChannel();
            meta.serverName = describeServer();

            recording = true;
            paused = false; pausedAccumMs = 0; markers.clear();
            flash("\u00A7cREC \u00A7fstate recording");
            OpenRewind.logger.info("[OpenRewind] STATE recording started -> {}", out.getName());
        } catch (Throwable t) {
            OpenRewind.logger.error("[OpenRewind] failed to start state recording", t);
            cleanup();
        }
    }

    public synchronized void stopRecording() {
        if (!recording) return;
        recording = false;

        // remove the netty handler
        try {
            if (hookedManager != null && hookedManager.channel() != null
                    && hookedManager.channel().pipeline().get(PacketRecorder.HANDLER_NAME) != null) {
                hookedManager.channel().pipeline().remove(PacketRecorder.HANDLER_NAME);
            }
        } catch (Throwable ignored) { }

        File audioWav = null;
        if (audio != null) {
            audio.stop();
            audioWav = audio.getSysFile() != null ? audio.getSysFile() : audio.getMicFile();
        }

        if (replay != null && meta != null) {
            meta.duration = System.currentTimeMillis() - startMillis;
            meta.markers = new java.util.ArrayList<Long>(markers);
            replay.finish(meta, audioWav);
            flash("\u00A7aReplay saved");
        }
        cleanup();
    }

    private void cleanup() {
        replay = null; meta = null; audio = null; hookedManager = null;
    }

    private String describeServer() {
        try {
            if (Minecraft.getMinecraft().getCurrentServerData() != null) {
                return Minecraft.getMinecraft().getCurrentServerData().serverIP;
            }
            if (Minecraft.getMinecraft().isSingleplayer()) return "singleplayer";
        } catch (Throwable ignored) { }
        return "unknown";
    }

    // ------------------------------------------------------------------------
    //  Client GUI track (Lunar-style: re-open inventory/chest in POV export)
    // ------------------------------------------------------------------------

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!recording || replay == null) return;
        long t = elapsedMs();
        GuiScreen gui = event.getGui();
        if (gui == null) {
            // a screen was closed
            replay.addGuiEvent(new GuiEvent(t, GuiEvent.Type.CLOSE, "", -1, ""));
            return;
        }
        // ignore our own screens
        if (gui.getClass().getName().startsWith("com.openrewind")) return;

        int windowId = -1;
        if (gui instanceof GuiContainer) {
            try {
                Container c = ((GuiContainer) gui).inventorySlots;
                if (c != null) windowId = c.windowId;
            } catch (Throwable ignored) { }
        }
        replay.addGuiEvent(new GuiEvent(t, GuiEvent.Type.OPEN,
                gui.getClass().getName(), windowId, ""));
    }

    // ------------------------------------------------------------------------
    //  Own-block prediction track (fixes "bridging blocks appear late")
    // ------------------------------------------------------------------------

    /**
     * Records your own block placements at the instant you make them. On
     * playback the exporter applies these predicted blocks immediately (before
     * the server's authoritative update arrives one ping later), so blocks you
     * place under your feet in PvP line up with what you saw live.
     */
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!recording || replay == null || !RewindConfig.predictOwnBlocks) return;
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        if (event.entityPlayer != Minecraft.getMinecraft().thePlayer) return;
        if (event.pos == null || event.face == null) return;

        try {
            ItemStack held = event.entityPlayer.getHeldItem();
            if (held == null) return;
            Block block = Block.getBlockFromItem(held.getItem());
            if (block == null || block == net.minecraft.init.Blocks.air) return;

            // block goes onto the face of the clicked block
            BlockPos placed = event.pos.offset(event.face);
            String name = Block.blockRegistry.getNameForObject(block).toString();
            replay.addPlacement(new BlockPlacement(elapsedMs(),
                    placed.getX(), placed.getY(), placed.getZ(), name, event.face.getIndex()));
        } catch (Throwable ignored) { }
    }
}
