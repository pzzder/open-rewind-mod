package com.openrewind.replay.playback;

import com.openrewind.OpenRewind;
import com.openrewind.replay.ReplayFile;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NettyPacketDecoder;
import net.minecraft.network.NetworkManager;
import net.minecraft.client.network.NetHandlerPlayClient;

import java.io.File;

/**
 * Feeds a recorded packet stream back into the client to reconstruct the world,
 * driving playback by a virtual clock.
 *
 * <h3>Mechanism (this part is exact)</h3>
 * We build an {@link EmbeddedChannel} whose pipeline is Minecraft's own
 * {@link NettyPacketDecoder} followed by a client-direction {@link NetworkManager}
 * bound to a {@link NetHandlerPlayClient}. Writing each stored frame's bytes into
 * the channel makes the vanilla decoder turn them back into {@code Packet}
 * objects, which the {@code NetworkManager} then dispatches to the play handler
 * exactly as if they had come from a real server — rebuilding chunks, entities,
 * inventory, everything. This is the same technique ReplayMod uses.
 *
 * <p>The channel's {@code attrKeyConnectionState} attribute is set to
 * {@link EnumConnectionState#PLAY} so the decoder selects the correct protocol.</p>
 *
 * <h3>Integration point (needs live iteration)</h3>
 * The {@link NetHandlerPlayClient} passed in must be one whose world-load hooks
 * put Minecraft into a local "replay world" rather than a real server session.
 * {@link ReplayHandler} is responsible for creating that; wiring it correctly is
 * the part that must be iterated inside a running 1.8.9 client.
 */
public class ReplaySender {

    private final File replayFile;
    private final NetworkManager fakeManager;
    private final EmbeddedChannel channel;

    private ReplayFile.PacketStream stream;
    private ReplayFile.TimedPacket pending;   // next packet not yet dispatched
    private long virtualTimeMs = 0;
    private boolean finished = false;

    public ReplaySender(File replayFile, NetHandlerPlayClient playHandler) {
        this.replayFile = replayFile;

        // reuse the network manager the bootstrap bound to this play handler
        this.fakeManager = playHandler.getNetworkManager();
        this.fakeManager.setConnectionState(EnumConnectionState.PLAY);
        this.fakeManager.setNetHandler(playHandler);

        // pipeline: decoder -> networkManager (dispatches to the play handler)
        NettyPacketDecoder decoder = new NettyPacketDecoder(EnumPacketDirection.CLIENTBOUND);
        this.channel = new EmbeddedChannel(decoder, fakeManager);
        this.channel.attr(NetworkManager.attrKeyConnectionState).set(EnumConnectionState.PLAY);
    }

    public void open() throws Exception {
        stream = ReplayFile.openPacketStream(replayFile);
        pending = stream.next();
    }

    public boolean isFinished() { return finished; }
    public long getVirtualTimeMs() { return virtualTimeMs; }

    /**
     * Advance playback so that every packet with timestamp &lt;= {@code targetMs}
     * has been dispatched. Call once per output frame with the frame's time.
     */
    public void advanceTo(long targetMs) {
        virtualTimeMs = targetMs;
        try {
            while (pending != null && pending.timestampMs <= targetMs) {
                dispatch(pending.frameBytes);
                pending = stream.next();
            }
            if (pending == null) finished = true;
        } catch (Exception e) {
            OpenRewind.logger.error("[OpenRewind] replay dispatch failed", e);
            finished = true;
        }
    }

    private void dispatch(byte[] frame) {
        // hand the raw frame to the vanilla decoder; it produces a Packet which
        // the NetworkManager forwards to the NetHandlerPlayClient.
        channel.writeInbound(Unpooled.wrappedBuffer(frame));
        // process any decoded messages that stayed queued
        channel.runPendingTasks();
    }

    public void close() {
        try { if (stream != null) stream.close(); } catch (Exception ignored) { }
        try { channel.close(); } catch (Exception ignored) { }
    }
}
