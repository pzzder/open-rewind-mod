package com.openrewind.proxy;

import com.openrewind.gui.MainMenuHandler;
import com.openrewind.gui.PauseMenuHandler;
import com.openrewind.hud.RecordingHud;
import com.openrewind.input.KeyBindings;
import com.openrewind.input.KeyInputHandler;
import com.openrewind.recording.RecordingManager;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Client proxy – wires up everything that only exists on the physical client:
 * key bindings, the input handler, the recording HUD and the recording
 * manager's lifecycle hooks.
 */
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        // register keybinds early so they appear in Controls menu
        KeyBindings.register();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        // The RecordingManager is a singleton event subscriber:
        //  - RenderTickEvent.END  -> grab a frame
        //  - ClientTickEvent      -> flush metadata, handle auto-record
        MinecraftForge.EVENT_BUS.register(RecordingManager.get());

        // Keyboard handler (Shift+R etc.)
        MinecraftForge.EVENT_BUS.register(new KeyInputHandler());

        // On-screen recording indicator
        MinecraftForge.EVENT_BUS.register(new RecordingHud());

        // Pause-menu (ESC) Record / Editor buttons
        MinecraftForge.EVENT_BUS.register(new PauseMenuHandler());
        // Main-menu Rewind + settings buttons
        MinecraftForge.EVENT_BUS.register(new MainMenuHandler());
        MinecraftForge.EVENT_BUS.register(com.openrewind.replay.ReplayRecordingManager.get());

        // start the shadow buffer if it's enabled in config
        RecordingManager.get().syncShadowState();
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        // make sure any in-progress encoder is flushed if the game is killed
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (RecordingManager.get().isRecording()) {
                RecordingManager.get().emergencyStop();
            }
        }, "OpenRewind-Shutdown"));
    }
}
