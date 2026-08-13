package com.openrewind.replay;

/**
 * A recorded client-side GUI open/close event. State-based replays reconstruct
 * the 3D world from the packet stream, but a plain packet replay does not know
 * <em>which</em> screen the player had open (opening your own inventory is a
 * purely client-side action with no server packet). This track is OpenRewind's
 * equivalent of the extra hooks Lunar Rewind adds so that, in POV mode, the
 * export can re-open the correct container / inventory screen at the right time.
 *
 * <p>The container <em>contents</em> themselves come from the recorded
 * {@code S2DPacketOpenWindow} / {@code S30PacketWindowItems} packets in the main
 * stream; this event only records the timing + which screen type + window id so
 * the exporter can rebuild the matching {@code GuiScreen}.</p>
 */
public class GuiEvent {

    public enum Type { OPEN, CLOSE }

    /** Milliseconds from recording start. */
    public long   timeMs;
    public Type   type;
    /** Fully-qualified GuiScreen class name (e.g. net.minecraft.client.gui.inventory.GuiInventory). */
    public String screenClass;
    /** Server window id if this is a container screen, else -1 (own inventory / crafting). */
    public int    windowId = -1;
    /** Optional container title for reconstruction. */
    public String title = "";

    public GuiEvent() { }

    public GuiEvent(long timeMs, Type type, String screenClass, int windowId, String title) {
        this.timeMs = timeMs;
        this.type = type;
        this.screenClass = screenClass;
        this.windowId = windowId;
        this.title = title == null ? "" : title;
    }
}
