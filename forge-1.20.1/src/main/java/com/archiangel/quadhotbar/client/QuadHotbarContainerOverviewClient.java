package com.archiangel.quadhotbar.client;

import com.archiangel.quadhotbar.QuadHotbarContainerOverview;
import com.archiangel.quadhotbar.QuadHotbarOverviewMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;

public final class QuadHotbarContainerOverviewClient {
    private QuadHotbarContainerOverviewClient() {}

    public static void request() {
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen instanceof AbstractContainerScreen<?> screen)
            QuadHotbarContainerOverview.requestOpen(screen.getMenu().containerId);
    }

    public static void open(QuadHotbarContainerOverview.Header header) {
        var mc = Minecraft.getInstance();
        if (mc.player == null
                || !(mc.screen instanceof AbstractContainerScreen<?> base)
                || base instanceof QuadHotbarOverviewScreen
                || mc.player.containerMenu != base.getMenu()
                || base.getMenu().containerId != header.parent()
                || header.pages() < 1
                || header.pages() > 100) {
            QuadHotbarContainerOverview.close(header.parent(), header.token());
            return;
        }
        var menu =
                QuadHotbarOverviewMenu.external(
                        header.parent(),
                        mc.player.getInventory(),
                        header.pages(),
                        header.active(),
                        header.height(),
                        base.getMenu(),
                        new SimpleContainer(header.pages() * 36),
                        header.death());
        mc.setScreen(
                new QuadHotbarOverviewScreen(
                        menu,
                        mc.player.getInventory(),
                        Component.translatable(
                                header.death()
                                        ? "quadhotbar.overview.death_title"
                                        : "quadhotbar.overview.title"),
                        base,
                        header.token()));
    }

    public static void sync(QuadHotbarContainerOverview.Frame frame) {
        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof QuadHotbarOverviewScreen screen)
                || !screen.getMenu().external
                || screen.externalToken() != frame.token()
                || screen.getMenu().containerId != frame.parent()) return;
        if (frame.page() == -2) {
            screen.closeExternalPanel();
            return;
        }
        var menu = screen.getMenu();
        if (frame.page() >= 0 && frame.page() < menu.pageCount && frame.stacks().size() == 36)
            for (int i = 0; i < 36; i++)
                menu.slots.get(frame.page() * 36 + i).set(frame.stacks().get(i));
        menu.setCarried(frame.cursor());
    }
}
