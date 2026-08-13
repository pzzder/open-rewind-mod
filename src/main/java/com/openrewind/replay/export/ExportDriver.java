package com.openrewind.replay.export;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Pumps {@link ReplayExporter#renderStep()} once per rendered frame on the
 * client render thread (rendering must happen there), then unregisters itself
 * when the export finishes. This is how the offline "render every frame" export
 * is driven without blocking the game loop.
 */
public class ExportDriver {

    private static ExportDriver active;

    private final ReplayExporter exporter;

    private ExportDriver(ReplayExporter exporter) { this.exporter = exporter; }

    /** Begin driving an exporter (no-op if one is already running). */
    public static boolean start(ReplayExporter exporter) {
        if (active != null) return false;
        active = new ExportDriver(exporter);
        MinecraftForge.EVENT_BUS.register(active);
        return true;
    }

    public static boolean isBusy() { return active != null; }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!exporter.isRunning()) {
            MinecraftForge.EVENT_BUS.unregister(this);
            active = null;
            return;
        }
        exporter.renderStep();
    }
}
