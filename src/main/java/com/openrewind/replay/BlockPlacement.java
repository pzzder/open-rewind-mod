package com.openrewind.replay;

/**
 * A recorded client-side block placement ("your own" prediction).
 *
 * <p>State replays only contain server-authoritative block updates, so blocks
 * you place (e.g. bridging under your feet in PvP) appear one round-trip (your
 * ping) <em>late</em> on playback — the well-known replay artifact Lunar Rewind
 * has too. OpenRewind additionally records your placement <em>attempts</em> here
 * so the exporter can show the predicted block at the moment you placed it,
 * matching what you saw live; the server's later authoritative update then
 * reconciles it (exactly like client prediction during normal play).</p>
 */
public class BlockPlacement {

    /** Milliseconds from recording start. */
    public long   timeMs;
    public int    x, y, z;
    /** Block registry name, e.g. "minecraft:stone". */
    public String block;
    /** Facing ordinal the block was placed against (for orientation-aware blocks). */
    public int    facing;

    public BlockPlacement() { }

    public BlockPlacement(long timeMs, int x, int y, int z, String block, int facing) {
        this.timeMs = timeMs;
        this.x = x; this.y = y; this.z = z;
        this.block = block;
        this.facing = facing;
    }
}
