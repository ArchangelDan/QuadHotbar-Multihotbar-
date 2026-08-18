package com.kharkivproject.quadhotbar;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class QuadHotbarConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue HOTBAR_ROWS = BUILDER
            .comment("How many hotbar rows should be available and rendered. Valid range: 1-4.")
            .defineInRange("hotbarRows", 2, 1, 4);

    private static final ModConfigSpec.BooleanValue WRAP_SCROLL = BUILDER
            .comment("When true, scrolling past the last slot wraps to the first slot and back.")
            .define("wrapScroll", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

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

    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        hotbarRows = HOTBAR_ROWS.get();
        wrapScroll = WRAP_SCROLL.get();
    }
}
