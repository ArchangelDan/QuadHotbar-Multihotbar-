package com.kharkivproject.quadhotbar.client;

import com.kharkivproject.quadhotbar.QuadHotbarConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class QuadHotbarConfigScreen extends Screen {

    private final Screen parent;

    public QuadHotbarConfigScreen(Screen parent) {
        super(Component.translatable("quadhotbar.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = 220;
        int buttonHeight = 20;
        int x = (this.width - buttonWidth) / 2;
        int y = this.height / 2 - 34;

        addRenderableWidget(Button.builder(rowsMessage(), button -> {
            int nextRows = QuadHotbarConfig.hotbarRows >= 4 ? 1 : QuadHotbarConfig.hotbarRows + 1;
            QuadHotbarConfig.setHotbarRows(nextRows);
            button.setMessage(rowsMessage());
        }).bounds(x, y, buttonWidth, buttonHeight).build());

        addRenderableWidget(Button.builder(wrapMessage(), button -> {
            QuadHotbarConfig.setWrapScroll(!QuadHotbarConfig.wrapScroll);
            button.setMessage(wrapMessage());
        }).bounds(x, y + 24, buttonWidth, buttonHeight).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(x, y + 62, buttonWidth, buttonHeight)
                .build());
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 24, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static Component rowsMessage() {
        return Component.translatable("quadhotbar.config.hotbar_rows", QuadHotbarConfig.hotbarRows);
    }

    private static Component wrapMessage() {
        return Component.translatable(
                "quadhotbar.config.wrap_scroll",
                Component.translatable(QuadHotbarConfig.wrapScroll ? "quadhotbar.config.on" : "quadhotbar.config.off"));
    }
}
