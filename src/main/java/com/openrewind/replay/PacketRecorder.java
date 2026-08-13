package com.openrewind.replay;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Netty inbound handler that captures the raw, decompressed, clientbound packet
 * frames straight off the network pipeline and streams them into a
 * {@link ReplayFile}. Installed with
 * {@code channel.pipeline().addBefore("decoder", NAME, this)} so it sees exactly
 * the bytes Minecraft's {@code NettyPacketDecoder} is about to decode (varint id
 * + payload), and forwards them untouched.
 *
 * <p>This is the crux of why state recording is cheap: no rendering, no pixel
 * reads, no video encoding while you play — just copying small packet buffers to
 * a background writer.</p>
 */
public class PacketRecorder extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "openrewind_recorder";

    private final ReplayFile replay;
    private final long startMillis;

    public PacketRecorder(ReplayFile replay, long startMillis) {
        this.replay = replay;
        this.startMillis = startMillis;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            // snapshot without disturbing the reader index Minecraft relies on
            int readable = buf.readableBytes();
            if (readable > 0) {
                byte[] data = new byte[readable];
                buf.getBytes(buf.readerIndex(), data);
                int ts = (int) (System.currentTimeMillis() - startMillis);
                replay.writePacket(ts, data);
            }
        }
        // always forward so the game keeps working normally
        super.channelRead(ctx, msg);
    }
}
