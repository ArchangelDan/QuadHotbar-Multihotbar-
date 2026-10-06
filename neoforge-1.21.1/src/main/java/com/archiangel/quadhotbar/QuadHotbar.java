package com.archiangel.quadhotbar;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(QuadHotbar.MODID)
public class QuadHotbar {

    public static final String MODID = "quadhotbar";

    public QuadHotbar(IEventBus modEventBus, ModContainer modContainer) {
        QuadHotbarOverviewMenu.MENUS.register(modEventBus);
        modEventBus.addListener(QuadHotbarNetwork::registerPayloads);
        modEventBus.addListener(QuadHotbarConfig::onLoad);
        modContainer.registerConfig(ModConfig.Type.CLIENT, QuadHotbarConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, QuadHotbarServerConfig.SPEC);
    }
}
