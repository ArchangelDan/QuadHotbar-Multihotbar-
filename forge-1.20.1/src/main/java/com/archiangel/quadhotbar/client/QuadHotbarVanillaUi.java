package com.archiangel.quadhotbar.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Shared rendering from Minecraft's own textures, including resource-pack replacements. */
final class QuadHotbarVanillaUi {
    private static final ResourceLocation CREATIVE_BACKGROUND =
            new ResourceLocation(
                    "minecraft", "textures/gui/container/creative_inventory/tab_items.png");
    private static final ResourceLocation CREATIVE_TABS =
            new ResourceLocation("minecraft", "textures/gui/container/creative_inventory/tabs.png");
    private static final ResourceLocation WIDGETS =
            new ResourceLocation("minecraft", "textures/gui/widgets.png");
    private static final ResourceLocation CONTAINER =
            new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    private QuadHotbarVanillaUi() {}

    static void creativeScrollbar(
            GuiGraphics graphics,
            int x,
            int y,
            int height,
            int knobY,
            int knobHeight,
            boolean enabled) {
        // Only stretch a plain track row. The source ends at y=128; rows below
        // it belong to the container frame, not to the scrolling area.
        graphics.blit(
                CREATIVE_BACKGROUND, x - 1, y + 1, 14, height - 2, 174.0F, 18.0F, 14, 1, 256, 256);
        graphics.blit(CREATIVE_BACKGROUND, x - 1, y, 174, 17, 14, 1);
        graphics.blit(CREATIVE_BACKGROUND, x - 1, y + height - 1, 174, 128, 14, 1);
        knobHeight = Mth.clamp(knobHeight, 1, Math.min(15, height - 2));
        knobY = Mth.clamp(knobY, y + 1, y + height - 1 - knobHeight);
        // Keep the native 12-by-15 handle instead of stretching its body.
        var texture = CREATIVE_TABS;
        int u = enabled ? 232 : 244;
        graphics.blit(texture, x, knobY, 12, knobHeight, (float) u, 0.0F, 12, 15, 256, 256);
    }

    static void container(GuiGraphics graphics, int x, int y, int width, int height) {
        // Nine-slice the chest frame without stretching its pixel-sized bevels.
        patch(graphics, x + 4, y + 4, width - 8, height - 8, 8, 8, 1, 1);
        patch(graphics, x + 4, y, width - 8, 4, 4, 0, 168, 4);
        patch(graphics, x + 4, y + height - 4, width - 8, 4, 4, 218, 168, 4);
        patch(graphics, x, y + 4, 4, height - 8, 0, 4, 4, 214);
        patch(graphics, x + width - 4, y + 4, 4, height - 8, 172, 4, 4, 214);
        patch(graphics, x, y, 4, 4, 0, 0, 4, 4);
        patch(graphics, x + width - 4, y, 4, 4, 172, 0, 4, 4);
        patch(graphics, x, y + height - 4, 4, 4, 0, 218, 4, 4);
        patch(graphics, x + width - 4, y + height - 4, 4, 4, 172, 218, 4, 4);
    }

    private static void patch(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int u,
            int v,
            int textureWidth,
            int textureHeight) {
        graphics.blit(
                CONTAINER,
                x,
                y,
                width,
                height,
                (float) u,
                (float) v,
                textureWidth,
                textureHeight,
                256,
                256);
    }

    static void slot(GuiGraphics graphics, int x, int y, boolean hotbar) {
        graphics.blit(CONTAINER, x, y, 7, 17, 18, 18);
        graphics.fill(x, y, x + 18, y + 18, 0x12000000);
        if (hotbar) graphics.fill(x + 1, y + 1, x + 17, y + 17, 0x22000000);
    }

    static void scrollbar(
            GuiGraphics graphics, int x, int y, int height, int knobY, int knobHeight) {
        graphics.fill(x, y, x + 6, y + height, 0xFF000000);
        graphics.fill(x, knobY, x + 6, knobY + knobHeight, 0xFFC0C0C0);
        graphics.fill(x, knobY, x + 5, knobY + knobHeight - 1, 0xFF808080);
    }

    static void optionsList(GuiGraphics graphics, int width, int top, int bottom, int scroll) {
        if (Minecraft.getInstance().level == null) {
            graphics.setColor(0.125F, 0.125F, 0.125F, 1.0F);
            graphics.blit(
                    Screen.BACKGROUND_LOCATION,
                    0,
                    top,
                    0.0F,
                    (float) (bottom + scroll),
                    width,
                    bottom - top,
                    32,
                    32);
            graphics.setColor(1, 1, 1, 1);
        } else graphics.fill(0, top, width, bottom, 0x50000000);
        graphics.fillGradient(0, top, width, top + 4, 0xFF000000, 0);
        graphics.fillGradient(0, bottom - 4, width, bottom, 0, 0xFF000000);
    }

    static void blitHud(
            GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        switch (sprite.getPath()) {
            case "hud/hotbar" -> graphics.blit(WIDGETS, x, y, 0, 0, width, height);
            case "hud/hotbar_selection" -> graphics.blit(WIDGETS, x, y, 0, 22, width, height);
            case "hud/hotbar_offhand_left" -> graphics.blit(WIDGETS, x + 3, y + 1, 24, 23, 22, 22);
            case "hud/hotbar_offhand_right" -> graphics.blit(WIDGETS, x + 4, y + 1, 46, 23, 22, 22);
            default -> throw new IllegalArgumentException("Unknown hotbar sprite: " + sprite);
        }
    }
}
