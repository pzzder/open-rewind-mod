package com.example;

import net.minecraft.init.Blocks;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = ExampleMod.MOD_ID, version = ExampleMod.MOD_VERSION, name = ExampleMod.MOD_NAME)
public class ExampleMod {
    public static final String MOD_ID = "@ID@";
    public static final String MOD_VERSION = "@VER@";
    public static final String MOD_NAME = "@NAME@";
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        System.out.println("Dirt: " + Blocks.dirt.getUnlocalizedName());
    }
}
