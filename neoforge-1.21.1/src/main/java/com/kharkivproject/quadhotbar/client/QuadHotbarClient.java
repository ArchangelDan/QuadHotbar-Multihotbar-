package com.kharkivproject.quadhotbar.client;

import com.kharkivproject.quadhotbar.QuadHotbar;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only mod entry point. Keeping all Screen references here prevents
 * dedicated servers from loading client GUI classes.
 */
@Mod(value = QuadHotbar.MODID, dist = Dist.CLIENT)
public final class QuadHotbarClient {

    public QuadHotbarClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(QuadHotbarKeyMappings::register);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (container, parent) -> new QuadHotbarConfigScreen(parent));
    }
}
