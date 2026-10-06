package com.archiangel.quadhotbar.client;

import com.archiangel.quadhotbar.QuadHotbarConfig;
import com.archiangel.quadhotbar.QuadHotbarPages;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import org.lwjgl.glfw.GLFW;

public final class QuadHotbarKeyMappings {

    private static final int FIRST_CUSTOM_SLOT = 9;
    public static final String CATEGORY = "key.categories.quadhotbar";
    public static final KeyMapping OPEN_CONFIG = create("key.quadhotbar.open_config");
    public static final KeyMapping OPEN_OVERVIEW = create("key.quadhotbar.open_overview");
    public static final KeyMapping TOGGLE_HOTBARS =
            new KeyMapping(
                    "key.quadhotbar.toggle_hotbars",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_GRAVE_ACCENT,
                    CATEGORY);
    public static final KeyMapping PREVIOUS_HOTBAR_PAGE =
            create("key.quadhotbar.previous_hotbar_page");
    public static final KeyMapping NEXT_HOTBAR_PAGE = create("key.quadhotbar.next_hotbar_page");
    public static final KeyMapping[] HOTBAR_SLOTS = createSlotMappings();

    private QuadHotbarKeyMappings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG);
        event.register(OPEN_OVERVIEW);
        event.register(TOGGLE_HOTBARS);
        event.register(PREVIOUS_HOTBAR_PAGE);
        event.register(NEXT_HOTBAR_PAGE);
        for (KeyMapping keyMapping : HOTBAR_SLOTS) {
            event.register(keyMapping);
        }
    }

    public static int consumeHotbarSlot() {
        for (int slot = 0; slot < HOTBAR_SLOTS.length; slot++) {
            if (HOTBAR_SLOTS[slot].consumeClick()) {
                return FIRST_CUSTOM_SLOT + slot;
            }
        }
        return -1;
    }

    public static boolean isVisibleInControls(KeyMapping mapping) {
        if (QuadHotbarConfig.showAllKeybinds) return true;
        Minecraft minecraft = Minecraft.getInstance();
        int rows =
                minecraft.player == null
                        ? QuadHotbarConfig.enabled
                                ? Mth.clamp(
                                        QuadHotbarConfig.hotbarRows,
                                        2,
                                        QuadHotbarConfig.experimentsEnabled
                                                ? QuadHotbarPages.MAX_HOTBAR_ROWS
                                                : 4)
                                : 1
                        : QuadHotbarClientEvents.getRows();
        for (int index = 0; index < HOTBAR_SLOTS.length; index++) {
            if (mapping == HOTBAR_SLOTS[index]) return FIRST_CUSTOM_SLOT + index < rows * 9;
        }
        boolean freedom =
                QuadHotbarConfig.enabled
                        && QuadHotbarConfig.experimentsEnabled
                        && (minecraft.player == null
                                || QuadHotbarClientEvents.hasModdedServer()
                                        && QuadHotbarClientEvents.serverPolicy().allowFreedom());
        int pages =
                minecraft.player == null
                        ? QuadHotbarConfig.inventoryPages
                        : QuadHotbarPages.state(minecraft.player).pageCount;
        if (mapping == OPEN_OVERVIEW)
            return freedom
                    && pages > 1
                    && QuadHotbarConfig.overviewEnabled
                    && (minecraft.player == null
                            || QuadHotbarClientEvents.serverPolicy().allowPageOverview());
        if (mapping == PREVIOUS_HOTBAR_PAGE || mapping == NEXT_HOTBAR_PAGE) {
            return freedom && pages > 1;
        }
        // Config/toggle remain accessible; never filter vanilla or other mods' bindings.
        return true;
    }

    private static KeyMapping[] createSlotMappings() {
        KeyMapping[] mappings =
                new KeyMapping[QuadHotbarPages.MAX_HOTBAR_ROWS * 9 - FIRST_CUSTOM_SLOT];
        for (int slot = 0; slot < mappings.length; slot++) {
            mappings[slot] = create("key.quadhotbar.slot." + (FIRST_CUSTOM_SLOT + slot + 1));
        }
        return mappings;
    }

    private static KeyMapping create(String translationKey) {
        return new KeyMapping(
                translationKey, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    }
}
