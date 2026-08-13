package com.openrewind.replay.playback;

import com.openrewind.OpenRewind;
import com.openrewind.replay.BlockPlacement;
import com.openrewind.replay.GuiEvent;
import com.openrewind.replay.ReplayFile;
import com.openrewind.replay.ReplayMetadata;

import net.minecraft.block.Block;
import net.minecraft.util.BlockPos;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;

import java.io.File;
import java.util.List;

/**
 * Orchestrates a replay playback session: it puts the client into a local
 * "replay world", drives the {@link ReplaySender} that rebuilds that world from
 * the recorded packets, and manages the {@link CameraEntity}.
 *
 * <p>The {@link ReplaySender} mechanism (decode + dispatch packets) is exact and
 * self-contained. What has to be iterated inside a live 1.8.9 client is the
 * session bootstrap below — creating a {@link WorldClient} + a
 * {@link NetHandlerPlayClient} that render into the game without a real server,
 * exactly as ReplayMod does. Those hooks are marked <b>[LIVE-INTEGRATION]</b>.</p>
 */
public class ReplayHandler {

    private final File replayFile;
    private final ReplayMetadata metadata;
    private final List<GuiEvent> guiTrack;
    private final List<BlockPlacement> predictions;
    private int predIndex = 0;

    private ReplaySender sender;
    private CameraEntity camera;
    private WorldClient replayWorld;
    private NetHandlerPlayClient replayNetHandler;
    private boolean active;

    public ReplayHandler(File replayFile) {
        this.replayFile = replayFile;
        this.metadata   = ReplayFile.readMetadata(replayFile);
        this.guiTrack   = ReplayFile.readGuiEvents(replayFile);
        this.predictions = ReplayFile.readPlacements(replayFile);
    }

    public ReplayMetadata getMetadata() { return metadata; }
    public List<GuiEvent> getGuiTrack() { return guiTrack; }
    public CameraEntity   getCamera()   { return camera; }
    public ReplaySender   getSender()   { return sender; }
    public boolean        isActive()    { return active; }
    public long           getDurationMs() { return metadata != null ? metadata.duration : 0; }

    /**
     * Enter the replay: build the local world + net handler, wire the sender and
     * camera, and switch Minecraft's render-view entity to the camera.
     */
    public void start() throws Exception {
        Minecraft mc = Minecraft.getMinecraft();

        // [LIVE-INTEGRATION] Build a client-only WorldClient + NetHandlerPlayClient
        // that the sender can feed. ReplayMod constructs these with a dummy
        // GameProfile + NetworkManager and installs the world into Minecraft.
        // The exact constructor wiring must be validated against a running client;
        // it involves NetHandlerPlayClient(mc, guiScreen, networkManager, profile)
        // and mc.loadWorld(worldClient). This is the one part that cannot be
        // verified offline and is expected to need iteration in dev.
        this.replayNetHandler = ReplaySessionBootstrap.createPlayHandler(mc);
        this.sender = new ReplaySender(replayFile, replayNetHandler);
        this.sender.open();

        // pull in the first slice so a world exists before we grab the camera
        this.sender.advanceTo(0);
        this.replayWorld = mc.theWorld;
        if (replayWorld != null) {
            this.camera = new CameraEntity(replayWorld);
            mc.setRenderViewEntity(camera);
        }
        active = true;
        OpenRewind.logger.info("[OpenRewind] replay session started: {}", replayFile.getName());
    }

    /** Advance the whole session to a point in time (ms from start). */
    public void seekTo(long timeMs) {
        if (sender != null) sender.advanceTo(timeMs);
        applyPredictionsUpTo(timeMs);
    }

    /**
     * Apply your recorded own-block placements up to {@code timeMs}, so bridging
     * blocks show at the moment you placed them instead of one ping late. The
     * server's authoritative block-change packet (already in the stream) will
     * naturally overwrite the prediction when it arrives — same reconciliation
     * as live client prediction.
     *
     * <p>[LIVE-INTEGRATION] {@code setBlockState} into the reconstructed world is
     * a real call; edge cases (orientation-specific block states, replaceable
     * targets) are refined against a running client. Assumes monotonic forward
     * playback (export), which is how {@link ReplayExporter} drives it.</p>
     */
    public void applyPredictionsUpTo(long timeMs) {
        if (predictions == null || predictions.isEmpty()) return;
        net.minecraft.client.multiplayer.WorldClient world = Minecraft.getMinecraft().theWorld;
        if (world == null) return;
        while (predIndex < predictions.size() && predictions.get(predIndex).timeMs <= timeMs) {
            BlockPlacement p = predictions.get(predIndex++);
            try {
                Block b = Block.getBlockFromName(p.block);
                if (b != null) {
                    world.setBlockState(new BlockPos(p.x, p.y, p.z), b.getDefaultState());
                }
            } catch (Throwable ignored) { }
        }
    }

    public void stop() {
        active = false;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            mc.setRenderViewEntity(mc.thePlayer);
        } catch (Throwable ignored) { }
        if (sender != null) sender.close();
        OpenRewind.logger.info("[OpenRewind] replay session ended");
    }
}
