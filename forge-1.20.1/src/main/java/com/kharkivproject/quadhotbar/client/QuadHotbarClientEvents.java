package com.kharkivproject.quadhotbar.client;

import com.kharkivproject.quadhotbar.QuadHotbar;
import com.kharkivproject.quadhotbar.QuadHotbarConfig;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = QuadHotbar.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuadHotbarClientEvents {

    private static final ResourceLocation WIDGETS = new ResourceLocation("minecraft", "textures/gui/widgets.png");
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int SELECTOR_SIZE = 24;
    private static final int VANILLA_HOTBAR_SLOTS = 9;
    private static int activeVisualRow = 0;
    private static int pendingVisualSlot = -1;
    private static int pendingServerSelectedSlot = -1;
    private static int savedSelectedSlotBeforeVanilla = -1;
    private static boolean vanillaOnly = false;
    private static boolean movingExperienceBar = false;
    private static boolean serverSupportsExtendedSlots = false;

    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiOverlayEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui)
            return;
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id()))
            return;

        event.setCanceled(true);
        updateSelectionMode(minecraft);
        clampSelectedSlot(minecraft.player);
        renderHotbars(event.getGuiGraphics(), minecraft, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
        ensureHudAboveExtraRows(minecraft);
    }

    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui)
            return;

        if (event.getOverlay().id().equals(VanillaGuiOverlay.ITEM_NAME.id())) {
            ensureHudAboveExtraRows(minecraft);
        } else if (event.getOverlay().id().equals(VanillaGuiOverlay.EXPERIENCE_BAR.id()) && shouldLiftExtraHud()) {
            movingExperienceBar = true;
            event.getGuiGraphics().pose().pushPose();
            event.getGuiGraphics().pose().translate(0.0F, -HOTBAR_HEIGHT, 0.0F);
        }
    }

    @SubscribeEvent
    public static void onRenderOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (movingExperienceBar && event.getOverlay().id().equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())) {
            event.getGuiGraphics().pose().popPose();
            movingExperienceBar = false;
        }
    }

    /**
     * Other mods get the scroll wheel first for item-specific interactions.
     * QuadHotbar only handles normal hotbar scrolling as a fallback.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (event.isCanceled())
            return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || getRows() <= 1)
            return;
        updateSelectionMode(minecraft);

        int selected = getSelectedSlot(minecraft.player);
        if (event.getScrollDelta() < 0)
            selected++;
        else if (event.getScrollDelta() > 0)
            selected--;
        else
            return;

        activateVisualSlot(minecraft, selected);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || event.getAction() != GLFW.GLFW_PRESS)
            return;
        updateSelectionMode(minecraft);

        if (vanillaOnly)
            return;

        for (int slot = 0; slot < VANILLA_HOTBAR_SLOTS; slot++) {
            if (!minecraft.options.keyHotbarSlots[slot].matches(event.getKey(), event.getScanCode()))
                continue;

            int currentColumn = getSelectedColumn(minecraft.player);
            int row = currentColumn == slot ? activeVisualRow + 1 : 0;
            if (row >= getRows())
                row = 0;

            pendingVisualSlot = row * VANILLA_HOTBAR_SLOTS + slot;
            activateVisualSlot(minecraft, pendingVisualSlot);
            if (event.isCancelable())
                event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        Minecraft minecraft = Minecraft.getInstance();
        applyPendingServerSelection(minecraft);

        if (minecraft.player != null && minecraft.screen == null && QuadHotbarKeyMappings.OPEN_CONFIG.consumeClick()) {
            minecraft.setScreen(new QuadHotbarConfigScreen(null));
        }

        if (minecraft.player != null && minecraft.screen == null && QuadHotbarKeyMappings.TOGGLE_HOTBARS.consumeClick()) {
            toggleVanillaOnly(minecraft);
            pendingVisualSlot = -1;
        }

        int boundSlot = QuadHotbarKeyMappings.consumeHotbarSlot();
        if (minecraft.player != null && minecraft.screen == null && boundSlot >= 0 && boundSlot < getRows() * VANILLA_HOTBAR_SLOTS) {
            pendingVisualSlot = -1;
            activateVisualSlot(minecraft, boundSlot);
        }

        if (pendingVisualSlot < 0)
            return;

        if (minecraft.player != null) {
            updateSelectionMode(minecraft);
            activateVisualSlot(minecraft, pendingVisualSlot);
        }
        pendingVisualSlot = -1;
    }

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        setServerSupportsExtendedSlots(false);
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        setServerSupportsExtendedSlots(false);
    }

    public static void setServerSupportsExtendedSlots(boolean supported) {
        serverSupportsExtendedSlots = supported;
        if (!supported)
            pendingServerSelectedSlot = -1;
    }

    /**
     * Vanilla's login packets do not synchronize the selected inventory slot.
     * Use the server's saved value before processing client hotbar input.
     */
    public static void syncServerSelectedSlot(int selectedSlot) {
        serverSupportsExtendedSlots = true;
        pendingServerSelectedSlot = Mth.clamp(selectedSlot, 0, Inventory.INVENTORY_SIZE - 1);
        applyPendingServerSelection(Minecraft.getInstance());
    }

    private static void renderHotbars(GuiGraphics graphics, Minecraft minecraft, int screenWidth, int screenHeight) {
        int rows = getRows();
        syncActiveRowFromSelected(minecraft.player);
        int selectedRow = vanillaOnly ? 0 : activeVisualRow;
        int selectedColumn = getSelectedColumn(minecraft.player);

        RenderSystem.enableBlend();
        for (int row = 0; row < rows; row++) {
            int[] coords = getHotbarCoords(row, rows, screenWidth, screenHeight);
            graphics.blit(WIDGETS, coords[0], coords[1], 0, 0, HOTBAR_WIDTH, HOTBAR_HEIGHT);
            renderRowItems(graphics, minecraft, row, coords[0], coords[1]);
        }

        int[] selectedCoords = getHotbarCoords(selectedRow, rows, screenWidth, screenHeight);
        int selectorX = selectedCoords[0] - 1 + selectedColumn * 20;
        int selectorY = selectedCoords[1] - 1;
        graphics.blit(WIDGETS, selectorX, selectorY, 0, HOTBAR_HEIGHT, SELECTOR_SIZE, SELECTOR_SIZE);
        renderOffhandSlot(graphics, minecraft, rows, screenWidth, screenHeight);
        RenderSystem.disableBlend();
    }

    private static void applyPendingServerSelection(Minecraft minecraft) {
        if (minecraft.player == null || pendingServerSelectedSlot < 0)
            return;

        minecraft.player.getInventory().selected = pendingServerSelectedSlot;
        pendingServerSelectedSlot = -1;
        syncActiveRowFromSelected(minecraft.player);
    }

    private static void ensureHudAboveExtraRows(Minecraft minecraft) {
        if (!shouldLiftExtraHud() || !(minecraft.gui instanceof ForgeGui forgeGui))
            return;

        int minHeight = 39 + HOTBAR_HEIGHT;
        forgeGui.leftHeight = Math.max(forgeGui.leftHeight, minHeight);
        forgeGui.rightHeight = Math.max(forgeGui.rightHeight, minHeight);
    }

    private static int[] getHotbarCoords(int row, int rows, int screenWidth, int screenHeight) {
        int x;
        int y;
        if (rows == 1) {
            x = screenWidth / 2 - HOTBAR_WIDTH / 2;
            y = screenHeight - HOTBAR_HEIGHT;
        } else if (rows == 2 || rows == 4) {
            x = screenWidth / 2 - HOTBAR_WIDTH * (row % 2 == 0 ? 1 : 0);
            y = screenHeight - HOTBAR_HEIGHT * (row < 2 ? 1 : 2);
        } else {
            x = (int) (screenWidth / 2 - HOTBAR_WIDTH * (row == 2 ? 0.5F : (row == 1 ? 0 : 1)));
            y = screenHeight - HOTBAR_HEIGHT * (row == 2 ? 2 : 1);
        }
        return new int[] { x, y };
    }

    private static void renderOffhandSlot(GuiGraphics graphics, Minecraft minecraft, int rows, int screenWidth, int screenHeight) {
        ItemStack offhand = minecraft.player.getOffhandItem();
        if (offhand.isEmpty())
            return;

        int leftX = screenWidth;
        int rightX = 0;
        int slotY = screenHeight - HOTBAR_HEIGHT;
        for (int row = 0; row < rows; row++) {
            int[] coords = getHotbarCoords(row, rows, screenWidth, screenHeight);
            if (coords[1] != slotY)
                continue;
            leftX = Math.min(leftX, coords[0]);
            rightX = Math.max(rightX, coords[0] + HOTBAR_WIDTH);
        }

        boolean offhandOnLeft = minecraft.player.getMainArm() == HumanoidArm.RIGHT;
        int slotX = offhandOnLeft ? leftX - HOTBAR_HEIGHT - 5 : rightX + 5;
        int textureX = offhandOnLeft ? SELECTOR_SIZE : SELECTOR_SIZE + HOTBAR_HEIGHT;
        graphics.blit(WIDGETS, slotX, slotY, textureX, HOTBAR_HEIGHT + 1, HOTBAR_HEIGHT, HOTBAR_HEIGHT);
        graphics.renderItem(offhand, slotX + 3, slotY + 3);
        graphics.renderItemDecorations(minecraft.font, offhand, slotX + 3, slotY + 3);
    }

    private static void renderRowItems(GuiGraphics graphics, Minecraft minecraft, int row, int hotbarX, int hotbarY) {
        for (int column = 0; column < VANILLA_HOTBAR_SLOTS; column++) {
            int inventorySlot = (vanillaOnly ? 0 : row) * VANILLA_HOTBAR_SLOTS + column;
            ItemStack stack = minecraft.player.getInventory().getItem(inventorySlot);
            if (stack.isEmpty())
                continue;

            int x = hotbarX + 3 + column * 20;
            int y = hotbarY + 3;
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(minecraft.font, stack, x, y);
        }
    }

    private static void activateVisualSlot(Minecraft minecraft, int visualSlot) {
        if (minecraft.player == null)
            return;

        int max = getRows() * VANILLA_HOTBAR_SLOTS - 1;
        if (QuadHotbarConfig.wrapScroll) {
            if (visualSlot < 0)
                visualSlot = max;
            else if (visualSlot > max)
                visualSlot = 0;
        } else {
            visualSlot = Mth.clamp(visualSlot, 0, max);
        }

        int targetRow = visualSlot / VANILLA_HOTBAR_SLOTS;
        int column = visualSlot % VANILLA_HOTBAR_SLOTS;
        setSelectedSlot(minecraft.player, targetRow * VANILLA_HOTBAR_SLOTS + column);
    }

    private static void toggleVanillaOnly(Minecraft minecraft) {
        if (minecraft.player == null)
            return;

        if (vanillaOnly) {
            vanillaOnly = false;
            if (savedSelectedSlotBeforeVanilla >= 0)
                setSelectedSlot(minecraft.player, savedSelectedSlotBeforeVanilla);
            savedSelectedSlotBeforeVanilla = -1;
        } else {
            savedSelectedSlotBeforeVanilla = getSelectedSlot(minecraft.player);
            vanillaOnly = true;
            setSelectedSlot(minecraft.player, getSelectedColumn(minecraft.player));
        }
    }

    private static void clampSelectedSlot(LocalPlayer player) {
        setSelectedSlot(player, getSelectedSlot(player));
    }

    private static int getSelectedColumn(LocalPlayer player) {
        return Mth.clamp(getSelectedSlot(player) % VANILLA_HOTBAR_SLOTS, 0, VANILLA_HOTBAR_SLOTS - 1);
    }

    private static int getSelectedSlot(LocalPlayer player) {
        int max = getRows() * VANILLA_HOTBAR_SLOTS - 1;
        return Mth.clamp(player.getInventory().selected, 0, max);
    }

    private static void setSelectedSlot(LocalPlayer player, int slot) {
        int max = getRows() * VANILLA_HOTBAR_SLOTS - 1;
        int clampedSlot = Mth.clamp(slot, 0, max);
        if (player.getInventory().selected != clampedSlot) {
            player.getInventory().selected = clampedSlot;
            player.connection.send(new ServerboundSetCarriedItemPacket(clampedSlot));
        }
        syncActiveRowFromSelected(player);
    }

    private static void syncActiveRowFromSelected(LocalPlayer player) {
        int rows = getRows();
        if (activeVisualRow >= rows)
            activeVisualRow = 0;
        if (!vanillaOnly)
            activeVisualRow = Mth.clamp(getSelectedSlot(player) / VANILLA_HOTBAR_SLOTS, 0, rows - 1);
    }

    private static int getRows() {
        return vanillaOnly || !supportsExtendedSlots(Minecraft.getInstance()) ? 1 : Mth.clamp(QuadHotbarConfig.hotbarRows, 1, 4);
    }

    private static boolean shouldLiftExtraHud() {
        return getRows() >= 3;
    }

    private static void updateSelectionMode(Minecraft minecraft) {
        if (minecraft.player == null || supportsExtendedSlots(minecraft))
            return;

        activeVisualRow = 0;
        int selectedColumn = getSelectedColumn(minecraft.player);
        if (minecraft.player.getInventory().selected != selectedColumn)
            setSelectedSlot(minecraft.player, selectedColumn);
    }

    private static boolean supportsExtendedSlots(Minecraft minecraft) {
        return minecraft.getSingleplayerServer() != null || serverSupportsExtendedSlots;
    }

}
