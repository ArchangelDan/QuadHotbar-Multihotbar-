package com.archiangel.quadhotbar;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(QuadHotbar.MODID)
public class QuadHotbar {

    public static final String MODID = "quadhotbar";

    public QuadHotbar() {
        var bus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        QuadHotbarOverviewMenu.MENUS.register(bus);
        bus.addListener(QuadHotbarConfig::onLoad);
        QuadHotbarNetwork.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, QuadHotbarConfig.SPEC);
        // Host-owned policy is never writable by clients and is synchronized per player.
        ModLoadingContext.get()
                .registerConfig(
                        ModConfig.Type.COMMON,
                        QuadHotbarServerConfig.SPEC,
                        "quadhotbar-server.toml");
    }
}
