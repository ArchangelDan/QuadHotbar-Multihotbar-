package com.kharkivproject.quadhotbar;

import com.kharkivproject.quadhotbar.client.QuadHotbarConfigScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(QuadHotbar.MODID)
public class QuadHotbar {

    public static final String MODID = "quadhotbar";

    public QuadHotbar() {
        QuadHotbarNetwork.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, QuadHotbarConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(QuadHotbarConfigScreen::new)));
    }
}
