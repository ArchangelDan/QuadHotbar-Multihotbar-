package com.kharkivproject.quadhotbar.client;

import com.kharkivproject.quadhotbar.QuadHotbar;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = QuadHotbar.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class QuadHotbarKeyMappings {

    private static final int FIRST_CUSTOM_SLOT = 9;
    public static final String CATEGORY = "key.categories.quadhotbar";
    public static final KeyMapping OPEN_CONFIG = create("key.quadhotbar.open_config");
    public static final KeyMapping TOGGLE_HOTBARS = new KeyMapping("key.quadhotbar.toggle_hotbars", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, CATEGORY);
    public static final KeyMapping[] HOTBAR_SLOTS = createSlotMappings();

    private QuadHotbarKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG);
        event.register(TOGGLE_HOTBARS);
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

    private static KeyMapping[] createSlotMappings() {
        KeyMapping[] mappings = new KeyMapping[36 - FIRST_CUSTOM_SLOT];
        for (int slot = 0; slot < mappings.length; slot++) {
            mappings[slot] = create("key.quadhotbar.slot." + (FIRST_CUSTOM_SLOT + slot + 1));
        }
        return mappings;
    }

    private static KeyMapping create(String translationKey) {
        return new KeyMapping(translationKey, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    }
}
