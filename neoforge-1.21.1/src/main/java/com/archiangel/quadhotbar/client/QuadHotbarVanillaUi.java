package com.archiangel.quadhotbar.client;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Shared rendering from Minecraft's own textures, including resource-pack replacements. */
final class QuadHotbarVanillaUi {
    private static final ResourceLocation CREATIVE_BACKGROUND =
            ResourceLocation.withDefaultNamespace(
                    "textures/gui/container/creative_inventory/tab_items.png");
    private static final ResourceLocation CREATIVE_SCROLLER =
            ResourceLocation.withDefaultNamespace(
                    "textures/gui/sprites/container/creative_inventory/scroller.png");
    private static final ResourceLocation CREATIVE_SCROLLER_DISABLED =
            ResourceLocation.withDefaultNamespace(
                    "textures/gui/sprites/container/creative_inventory/scroller_disabled.png");
    private static final ResourceLocation CONTAINER =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final ResourceLocation MENU_LIST =
            ResourceLocation.withDefaultNamespace("textures/gui/menu_list_background.png");
    private static final ResourceLocation INWORLD_LIST =
            ResourceLocation.withDefaultNamespace("textures/gui/inworld_menu_list_background.png");
    private static final ResourceLocation SCROLLER =
            ResourceLocation.withDefaultNamespace("widget/scroller");
    private static final ResourceLocation SCROLLER_BACKGROUND =
            ResourceLocation.withDefaultNamespace("widget/scroller_background");

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
        var texture = enabled ? CREATIVE_SCROLLER : CREATIVE_SCROLLER_DISABLED;
        graphics.blit(texture, x, knobY, 12, knobHeight, 0, 0, 12, 15, 12, 15);
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
        graphics.blitSprite(SCROLLER_BACKGROUND, x, y, 6, height);
        graphics.blitSprite(SCROLLER, x, knobY, 6, knobHeight);
    }

    static void optionsList(GuiGraphics graphics, int width, int top, int bottom, int scroll) {
        boolean inWorld = Minecraft.getInstance().level != null;
        RenderSystem.enableBlend();
        graphics.blit(
                inWorld ? INWORLD_LIST : MENU_LIST,
                0,
                top,
                (float) width,
                (float) (bottom + scroll),
                width,
                bottom - top,
                32,
                32);
        graphics.blit(
                inWorld ? Screen.INWORLD_HEADER_SEPARATOR : Screen.HEADER_SEPARATOR,
                0,
                top - 2,
                0.0F,
                0.0F,
                width,
                2,
                32,
                2);
        graphics.blit(
                inWorld ? Screen.INWORLD_FOOTER_SEPARATOR : Screen.FOOTER_SEPARATOR,
                0,
                bottom,
                0.0F,
                0.0F,
                width,
                2,
                32,
                2);
        RenderSystem.disableBlend();
    }
}
