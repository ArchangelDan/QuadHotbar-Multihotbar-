package com.archiangel.quadhotbar.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Inventory controls whose release must never become an outside-slot drop. */
public final class QuadHotbarControlButton extends Button {
    private final boolean grid;
    private final boolean frameless;

    public QuadHotbarControlButton(int x, int y, Component label, OnPress action, boolean grid) {
        this(x, y, label, action, grid, false);
    }

    private QuadHotbarControlButton(
            int x, int y, Component label, OnPress action, boolean grid, boolean frameless) {
        super(x, y, 20, 20, grid ? Component.empty() : label, action, DEFAULT_NARRATION);
        this.grid = grid;
        this.frameless = frameless;
        if (grid || frameless) {
            setTooltip(Tooltip.create(label));
        }
    }

    public static QuadHotbarControlButton close(int x, int y, OnPress action) {
        return new QuadHotbarControlButton(
                x, y, Component.translatable("quadhotbar.overview.close"), action, false, true);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (frameless) {
            var font = Minecraft.getInstance().font;
            graphics.drawString(
                    font,
                    "×",
                    getX() + (getWidth() - font.width("×")) / 2,
                    getY() + (getHeight() - font.lineHeight) / 2,
                    active ? (isHoveredOrFocused() ? 0xFFFFA0 : 0x404040) : 0xA0A0A0,
                    false);
            return;
        }
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        if (grid) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.5F, 0.5F, 0.0F);
            try {
                for (int row = 0; row < 3; row++) {
                    for (int column = 0; column < 3; column++) {
                        int x = getX() + 4 + column * 4;
                        int y = getY() + 4 + row * 4;
                        graphics.fill(x, y, x + 3, y + 3, active ? 0xFFFFFFFF : 0xFF777777);
                    }
                }
            } finally {
                graphics.pose().popPose();
            }
        }
    }
}
