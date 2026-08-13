package com.openrewind;

import com.openrewind.config.RewindConfig;
import com.openrewind.proxy.CommonProxy;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import org.apache.logging.log4j.Logger;

/**
 * OpenRewind – main mod entry point.
 *
 * <p>OpenRewind is a client-side recording / clipping / editing suite for
 * Minecraft 1.8.9, built in the spirit of Lunar Client's Rewind but fully
 * open source and usable on any launcher (Forge). It handles:</p>
 *
 * <ul>
 *     <li>Framebuffer capture on the render thread (glReadPixels).</li>
 *     <li>Asynchronous H.264 encoding on a dedicated worker thread (JCodec).</li>
 *     <li>Microphone + system audio capture (javax.sound.sampled).</li>
 *     <li>A recording HUD (red dot + timer + marker flash).</li>
 *     <li>An in-game timeline editor for trimming and exporting clips.</li>
 * </ul>
 */
@Mod(
        modid   = OpenRewind.MODID,
        name    = OpenRewind.NAME,
        version = OpenRewind.VERSION,
        clientSideOnly = true,
        acceptedMinecraftVersions = "[1.8.9]"
)
public class OpenRewind {

    public static final String MODID   = "openrewind";
    public static final String NAME    = "OpenRewind";
    // token replaced by ForgeGradle at build time
    public static final String VERSION = "@MOD_VERSION@";

    @Mod.Instance(MODID)
    public static OpenRewind instance;

    @SidedProxy(
            clientSide = "com.openrewind.proxy.ClientProxy",
            serverSide = "com.openrewind.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    public static Logger logger;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        logger.info("[{}] preInit – loading configuration", NAME);
        // config file lives in .minecraft/config/openrewind.cfg
        RewindConfig.load(event.getSuggestedConfigurationFile());
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        logger.info("[{}] init", NAME);
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        logger.info("[{}] postInit – ready", NAME);
        proxy.postInit(event);
    }
}
