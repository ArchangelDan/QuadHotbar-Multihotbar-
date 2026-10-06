package com.archiangel.quadhotbar.client;

import com.archiangel.quadhotbar.QuadHotbarConfig;
import com.archiangel.quadhotbar.QuadHotbarOverviewNetwork;
import com.archiangel.quadhotbar.QuadHotbarPages;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public final class QuadHotbarInventoryPageUi {
    private static Screen autoOpenedInventory;

    private QuadHotbarInventoryPageUi() {}

    public static void tickAutoOpen(Minecraft minecraft) {
        Screen screen = minecraft.screen;
        if (!(screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)) {
            autoOpenedInventory = null;
            return;
        }
        if (QuadHotbarConfig.autoOpenOverview
                && QuadHotbarConfig.overviewLastOpen
                && screen != autoOpenedInventory
                && overviewAvailable(minecraft)) {
            // One request per inventory opening, even if the server rejects or delays it.
            autoOpenedInventory = screen;
            QuadHotbarOverviewNetwork.open();
        }
    }

    public static void suppressAutoOpen(Screen inventoryScreen) {
        autoOpenedInventory = inventoryScreen;
    }

    public static void rememberOverviewState(boolean open) {
        if (QuadHotbarConfig.autoOpenOverview && QuadHotbarConfig.overviewLastOpen != open) {
            QuadHotbarConfig.setOverviewLastOpen(open);
            QuadHotbarConfig.save();
        }
    }

    public static boolean pagingAvailable(Minecraft minecraft) {
        return minecraft != null
                && minecraft.player != null
                && QuadHotbarConfig.enabled
                && QuadHotbarConfig.experimentsEnabled
                && QuadHotbarClientEvents.hasModdedServer()
                && QuadHotbarClientEvents.serverPolicy().allowFreedom()
                && QuadHotbarPages.state(minecraft.player).pageCount > 1;
    }

    public static boolean showButtons(Minecraft minecraft) {
        return pagingAvailable(minecraft) && QuadHotbarConfig.showPageButtons;
    }

    public static boolean handlePageKey(Minecraft minecraft, int keyCode, int scanCode) {
        // Leave the inventory binding to vanilla, including conflicts with our page controls.
        if (minecraft.options.keyInventory.matches(keyCode, scanCode)) return false;
        if (QuadHotbarKeyMappings.OPEN_OVERVIEW.matches(keyCode, scanCode)
                && overviewAvailable(minecraft)) {
            openOverview();
            return true;
        }
        if (!pagingAvailable(minecraft)) {
            return false;
        }
        if (QuadHotbarKeyMappings.PREVIOUS_HOTBAR_PAGE.matches(keyCode, scanCode)) {
            QuadHotbarClientEvents.requestInventoryPage(-1);
            return true;
        }
        if (QuadHotbarKeyMappings.NEXT_HOTBAR_PAGE.matches(keyCode, scanCode)) {
            QuadHotbarClientEvents.requestInventoryPage(1);
            return true;
        }
        return false;
    }

    public static boolean overviewAvailable(Minecraft minecraft) {
        return minecraft != null
                && minecraft.player != null
                && QuadHotbarConfig.enabled
                && QuadHotbarConfig.experimentsEnabled
                && QuadHotbarConfig.overviewEnabled
                && QuadHotbarPages.state(minecraft.player).pageCount > 1
                && QuadHotbarClientEvents.hasModdedServer()
                && QuadHotbarClientEvents.serverPolicy().allowPageOverview();
    }

    public static boolean showOverviewButton(Minecraft minecraft) {
        return overviewAvailable(minecraft)
                && QuadHotbarConfig.showOverviewButton
                && QuadHotbarClientEvents.serverPolicy().showOverviewButton();
    }

    public static void openOverview() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof QuadHotbarOverviewScreen overview)
            overview.returnToInventoryWithCursor();
        else if (overviewAvailable(minecraft)) {
            if (minecraft.screen instanceof AbstractContainerScreen<?> screen
                    && !(screen instanceof InventoryScreen
                            || screen instanceof CreativeModeInventoryScreen)) {
                if (QuadHotbarClientEvents.serverPolicy().allowContainerOverview())
                    QuadHotbarContainerOverviewClient.request();
            } else QuadHotbarOverviewNetwork.open();
        }
    }

    public static void renderPageLabel(
            GuiGraphics graphics, Minecraft minecraft, Font font, int centerX, int y) {
        if (!pagingAvailable(minecraft)) {
            return;
        }
        int labelColor = QuadHotbarClientEvents.inventoryPageLabelColor();
        if (labelColor == 0) {
            return;
        }
        QuadHotbarPages.State state = QuadHotbarPages.state(minecraft.player);
        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "quadhotbar.inventory.page", state.activePage + 1, state.pageCount),
                centerX,
                y,
                labelColor);
    }
}
