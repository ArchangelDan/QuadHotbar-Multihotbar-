package com.kharkivproject.quadhotbar;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = QuadHotbar.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class QuadHotbarConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue HOTBAR_ROWS = BUILDER
            .comment("How many hotbar rows should be available and rendered. Valid range: 1-4.")
            .defineInRange("hotbarRows", 2, 1, 4);

    private static final ForgeConfigSpec.BooleanValue WRAP_SCROLL = BUILDER
            .comment("When true, scrolling past the last slot wraps to the first slot and back.")
            .define("wrapScroll", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int hotbarRows = 2;
    public static boolean wrapScroll = true;

    private QuadHotbarConfig() {
    }

    public static void setHotbarRows(int rows) {
        HOTBAR_ROWS.set(Math.max(1, Math.min(4, rows)));
        hotbarRows = HOTBAR_ROWS.get();
    }

    public static void setWrapScroll(boolean value) {
        WRAP_SCROLL.set(value);
        wrapScroll = WRAP_SCROLL.get();
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC)
            return;
        hotbarRows = HOTBAR_ROWS.get();
        wrapScroll = WRAP_SCROLL.get();
    }
}
