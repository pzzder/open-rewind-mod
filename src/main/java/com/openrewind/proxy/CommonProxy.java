package com.openrewind.proxy;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Server / common proxy. OpenRewind is a client-only mod, so this base proxy
 * intentionally does nothing – it only exists so the mod can load without
 * crashing on a dedicated server classpath.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event)  { }

    public void init(FMLInitializationEvent event)        { }

    public void postInit(FMLPostInitializationEvent event) { }
}
