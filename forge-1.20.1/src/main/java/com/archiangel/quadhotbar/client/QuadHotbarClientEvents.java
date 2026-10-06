package com.archiangel.quadhotbar.client;

import com.archiangel.quadhotbar.QuadHotbar;
import com.archiangel.quadhotbar.QuadHotbarConfig;
import com.archiangel.quadhotbar.QuadHotbarLayout;
import com.archiangel.quadhotbar.QuadHotbarNetwork;
import com.archiangel.quadhotbar.QuadHotbarPages;
import com.archiangel.quadhotbar.QuadHotbarPolicy;
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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import org.lwjgl.glfw.GLFW;

import java.util.List;

@EventBusSubscriber(modid = QuadHotbar.MODID, value = Dist.CLIENT)
public final class QuadHotbarClientEvents {

    private static final ResourceLocation HOTBAR_SPRITE =
            new ResourceLocation("minecraft", "hud/hotbar");
    private static final ResourceLocation HOTBAR_SELECTION_SPRITE =
            new ResourceLocation("minecraft", "hud/hotbar_selection");
    private static final ResourceLocation HOTBAR_OFFHAND_LEFT_SPRITE =
            new ResourceLocation("minecraft", "hud/hotbar_offhand_left");
    private static final ResourceLocation HOTBAR_OFFHAND_RIGHT_SPRITE =
            new ResourceLocation("minecraft", "hud/hotbar_offhand_right");
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int SELECTOR_WIDTH = 24;
    private static final int SELECTOR_HEIGHT = 23;
    private static final int VANILLA_HOTBAR_SLOTS = 9;
    private static final long PAGE_LABEL_DURATION_NANOS = 3_000_000_000L;
    private static int activeVisualRow = 0;
    private static int pendingVisualSlot = -1;
    private static int pendingServerSelectedSlot = -1;
    private static int savedSelectedSlotBeforeVanilla = -1;
    private static boolean movingExperienceLayer = false;
    private static boolean serverSupportsExtendedSlots = false;
    private static boolean pageSettingsSent = false;
    private static int requestedVisualSlot = -1;
    private static boolean awaitingRecenter = false;
    private static int latestSlotRequestId = 0;
    private static int nextSlotRequestId = 0;
    private static boolean receivedPageSnapshot = false;
    private static long inventoryPageLabelUntilNanos = 0;
    private static long hotbarPageLabelUntilNanos = 0;

    @SubscribeEvent
    public static void onRenderLayer(RenderGuiOverlayEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!QuadHotbarConfig.enabled || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        if (VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())) {
            event.setCanceled(true);
            updateSelectionMode(minecraft);
            clampSelectedSlot(minecraft.player);
            renderHotbars(event.getGuiGraphics(), minecraft);
            ensureHudAboveExtraRows(minecraft);
        } else if (VanillaGuiOverlay.ITEM_NAME.id().equals(event.getOverlay().id())) {
            ensureHudAboveExtraRows(minecraft);
        } else if (VanillaGuiOverlay.EXPERIENCE_BAR.id().equals(event.getOverlay().id())
                && shouldLiftExtraHud()) {
            movingExperienceLayer = true;
            event.getGuiGraphics().pose().pushPose();
            event.getGuiGraphics()
                    .pose()
                    .translate(0.0F, -HOTBAR_HEIGHT * (hotbarLevels() - 1), 0.0F);
        }
    }

    @SubscribeEvent
    public static void onRenderLayerPost(RenderGuiOverlayEvent.Post event) {
        if (movingExperienceLayer
                && VanillaGuiOverlay.EXPERIENCE_BAR.id().equals(event.getOverlay().id())) {
            event.getGuiGraphics().pose().popPose();
            movingExperienceLayer = false;
        }
    }

    /** Only replace vanilla slot selection after other mods had a chance to consume scrolling. */
    public static boolean onVanillaHotbarScroll(Inventory inventory, double direction) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!QuadHotbarConfig.enabled
                || minecraft.player == null
                || minecraft.player.getInventory() != inventory
                || minecraft.screen != null
                || (getRows() <= 1 && !usesPages())
                || direction == 0.0) {
            return false;
        }
        updateSelectionMode(minecraft);
        int step = (int) Math.signum(direction);
        if (pendingVisualSlot >= 0) {
            pendingVisualSlot =
                    QuadHotbarConfig.scrollBetweenHotbars
                            ? Math.floorMod(
                                    pendingVisualSlot - step, getRows() * VANILLA_HOTBAR_SLOTS)
                            : pendingVisualSlot / VANILLA_HOTBAR_SLOTS * VANILLA_HOTBAR_SLOTS
                                    + Math.floorMod(
                                            pendingVisualSlot % VANILLA_HOTBAR_SLOTS - step,
                                            VANILLA_HOTBAR_SLOTS);
            return true;
        }
        int selected =
                requestedVisualSlot >= 0 ? requestedVisualSlot : getSelectedSlot(minecraft.player);
        if (usesPages() && (awaitingRecenter || selected < 0)) {
            // An external slot change can leave the selected row outside the displayed page.
            // Keep subsequent wheel steps server-authoritative until the final response arrives.
            awaitingRecenter = true;
            requestedVisualSlot = -1;
            QuadHotbarNetwork.requestScroll(
                    step, !QuadHotbarConfig.scrollBetweenHotbars, nextSelectionRequestId());
        } else {
            int next =
                    QuadHotbarConfig.scrollBetweenHotbars
                            ? selected - step
                            : selected / VANILLA_HOTBAR_SLOTS * VANILLA_HOTBAR_SLOTS
                                    + Math.floorMod(
                                            selected % VANILLA_HOTBAR_SLOTS - step,
                                            VANILLA_HOTBAR_SLOTS);
            activateVisualSlot(minecraft, next);
        }
        return true;
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!QuadHotbarConfig.enabled
                || minecraft.player == null
                || minecraft.screen != null
                || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        updateSelectionMode(minecraft);

        for (int slot = 0; slot < VANILLA_HOTBAR_SLOTS; slot++) {
            if (!minecraft.options.keyHotbarSlots[slot].matches(
                    event.getKey(), event.getScanCode())) {
                continue;
            }

            int currentVisualSlot =
                    pendingVisualSlot >= 0
                            ? pendingVisualSlot
                            : requestedVisualSlot >= 0
                                    ? requestedVisualSlot
                                    : getSelectedSlot(minecraft.player);
            int row =
                    currentVisualSlot >= 0 && currentVisualSlot % VANILLA_HOTBAR_SLOTS == slot
                            ? currentVisualSlot / VANILLA_HOTBAR_SLOTS + 1
                            : 0;
            if (row >= getRows()) {
                row = 0;
            }

            pendingVisualSlot = row * VANILLA_HOTBAR_SLOTS + slot;
            if (!usesPages()) {
                activateVisualSlot(minecraft, pendingVisualSlot);
            }
            return;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (policySettingsPending
                && hasModdedServer()
                && minecraft.player.containerMenu == minecraft.player.inventoryMenu) {
            policySettingsPending = false;
            syncPageSettings();
        }
        if (QuadHotbarKeyMappings.OPEN_OVERVIEW.consumeClick() && minecraft.screen == null) {
            QuadHotbarInventoryPageUi.openOverview();
        }
        applyPendingServerSelection(minecraft);
        QuadHotbarInventoryPageUi.tickAutoOpen(minecraft);
        if (!QuadHotbarConfig.enabled
                && minecraft.player != null
                && minecraft.player.getInventory().selected >= VANILLA_HOTBAR_SLOTS) {
            setSelectedSlot(
                    minecraft.player,
                    minecraft.player.getInventory().selected % VANILLA_HOTBAR_SLOTS);
        }

        if (minecraft.player != null
                && minecraft.screen == null
                && QuadHotbarKeyMappings.OPEN_CONFIG.consumeClick()) {
            minecraft.setScreen(new QuadHotbarConfigScreen(null));
        }

        if (minecraft.player != null
                && minecraft.screen == null
                && QuadHotbarKeyMappings.TOGGLE_HOTBARS.consumeClick()) {
            setModEnabled(minecraft, !QuadHotbarConfig.enabled);
        }

        if (minecraft.player != null && minecraft.screen == null && usesPages()) {
            if (QuadHotbarKeyMappings.PREVIOUS_HOTBAR_PAGE.consumeClick()) {
                requestHotbarPage(-1);
            }
            if (QuadHotbarKeyMappings.NEXT_HOTBAR_PAGE.consumeClick()) {
                requestHotbarPage(1);
            }
        }

        int boundSlot = QuadHotbarKeyMappings.consumeHotbarSlot();
        if (QuadHotbarConfig.enabled
                && minecraft.player != null
                && minecraft.screen == null
                && boundSlot >= 0
                && boundSlot < getRows() * VANILLA_HOTBAR_SLOTS) {
            pendingVisualSlot = -1;
            activateVisualSlot(minecraft, boundSlot);
        }

        if (!QuadHotbarConfig.enabled || pendingVisualSlot < 0) {
            pendingVisualSlot = -1;
            return;
        }

        if (minecraft.player != null) {
            updateSelectionMode(minecraft);
            activateVisualSlot(minecraft, pendingVisualSlot);
        }
        pendingVisualSlot = -1;
    }

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        setServerSupportsExtendedSlots(false);
        QuadHotbarConfig.setMultiplayerContext(!Minecraft.getInstance().hasSingleplayerServer());
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        setServerSupportsExtendedSlots(false);
        QuadHotbarConfig.setMultiplayerContext(false);
    }

    @SubscribeEvent
    public static void onClientRespawn(ClientPlayerNetworkEvent.Clone event) {
        // The replacement LocalPlayer has a fresh page state; wait for the server's respawn
        // snapshot.
        activeVisualRow = 0;
        pendingVisualSlot = -1;
        requestedVisualSlot = -1;
        awaitingRecenter = false;
        latestSlotRequestId = 0;
        receivedPageSnapshot = false;
        inventoryPageLabelUntilNanos = 0;
        hotbarPageLabelUntilNanos = 0;
    }

    public static void setServerSupportsExtendedSlots(boolean supported) {
        serverSupportsExtendedSlots = supported;
        if (!supported) {
            serverPolicy = QuadHotbarPolicy.UNRESTRICTED;
            policySettingsPending = false;
            pendingServerSelectedSlot = -1;
            savedSelectedSlotBeforeVanilla = -1;
            pageSettingsSent = false;
            requestedVisualSlot = -1;
            awaitingRecenter = false;
            latestSlotRequestId = 0;
            receivedPageSnapshot = false;
            inventoryPageLabelUntilNanos = 0;
            hotbarPageLabelUntilNanos = 0;
        }
    }

    private static QuadHotbarPolicy serverPolicy = QuadHotbarPolicy.UNRESTRICTED;
    private static boolean policySettingsPending;

    public static QuadHotbarPolicy serverPolicy() {
        return serverPolicy;
    }

    public static void setServerPolicy(QuadHotbarPolicy policy) {
        if (serverPolicy.equals(policy)) return;
        serverPolicy = policy;
        // Apply remembered preferences after an open container closes. Server checks still
        // enforce a reduced policy immediately; this only restores newly permitted preferences.
        policySettingsPending = hasModdedServer();
    }

    public static void onPageSync() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && supportsExtendedSlots(minecraft) && !pageSettingsSent) {
            pageSettingsSent = true;
            syncPageSettings();
        }
    }

    public static void syncPagePayload(
            int pages,
            int rows,
            int activePage,
            int hotbarPage,
            int selectedSlot,
            int requestId,
            boolean bottomToTop,
            List<ItemStack> first,
            List<ItemStack> second,
            List<ItemStack> middle) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        QuadHotbarPages.State previous = QuadHotbarPages.state(minecraft.player);
        long now = System.nanoTime();
        if (receivedPageSnapshot) {
            if (previous.activePage != activePage) {
                inventoryPageLabelUntilNanos = now + PAGE_LABEL_DURATION_NANOS;
            }
            if (previous.hotbarPage != hotbarPage) {
                hotbarPageLabelUntilNanos = now + PAGE_LABEL_DURATION_NANOS;
            }
        }
        QuadHotbarPages.receiveSnapshot(
                minecraft.player,
                pages,
                rows,
                activePage,
                hotbarPage,
                selectedSlot,
                bottomToTop,
                first,
                second,
                middle);
        if (minecraft.player.containerMenu
                instanceof com.archiangel.quadhotbar.QuadHotbarOverviewMenu menu)
            menu.activePage = activePage;
        acknowledgeSlotRequest(requestId);
        receivedPageSnapshot = true;
        onPageSync();
    }

    public static int inventoryPageLabelColor() {
        if (!receivedPageSnapshot) {
            return 0;
        }
        return QuadHotbarConfig.alwaysShowPageLabels
                ? 0xFFFFFFFF
                : QuadHotbarFade.color(inventoryPageLabelUntilNanos, 0xFFFFFF);
    }

    public static void hideInventoryPageLabel() {
        if (!QuadHotbarConfig.alwaysShowPageLabels) {
            inventoryPageLabelUntilNanos = 0;
        }
    }

    public static int hotbarPageLabelColor() {
        if (!receivedPageSnapshot) {
            return 0;
        }
        return QuadHotbarConfig.alwaysShowPageLabels
                ? 0xFFFFFFFF
                : QuadHotbarFade.color(hotbarPageLabelUntilNanos, 0xFFFFFF);
    }

    public static void requestInventoryPage(int direction) {
        requestedVisualSlot = -1;
        awaitingRecenter = false;
        latestSlotRequestId = 0;
        inventoryPageLabelUntilNanos = System.nanoTime() + PAGE_LABEL_DURATION_NANOS;
        QuadHotbarNetwork.requestInventoryPage(direction);
    }

    private static void requestHotbarPage(int direction) {
        requestedVisualSlot = -1;
        awaitingRecenter = false;
        latestSlotRequestId = 0;
        hotbarPageLabelUntilNanos = System.nanoTime() + PAGE_LABEL_DURATION_NANOS;
        QuadHotbarNetwork.requestHotbarPage(direction);
    }

    public static void syncPageSelection(int selectedSlot, int requestId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.getInventory().selected =
                    Mth.clamp(selectedSlot, 0, Inventory.INVENTORY_SIZE - 1);
            acknowledgeSlotRequest(requestId);
            syncActiveRowFromSelected(minecraft.player);
        }
    }

    private static void acknowledgeSlotRequest(int requestId) {
        if (requestId != 0 && requestId == latestSlotRequestId) {
            requestedVisualSlot = -1;
            awaitingRecenter = false;
            latestSlotRequestId = 0;
        }
    }

    private static int nextSelectionRequestId() {
        nextSlotRequestId = nextSlotRequestId == Integer.MAX_VALUE ? 1 : nextSlotRequestId + 1;
        latestSlotRequestId = nextSlotRequestId;
        return latestSlotRequestId;
    }

    public static void syncPageSettings() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && supportsExtendedSlots(minecraft)) {
            requestedVisualSlot = -1;
            awaitingRecenter = false;
            latestSlotRequestId = 0;
            QuadHotbarNetwork.requestPageSettings(
                    QuadHotbarConfig.enabled && QuadHotbarConfig.experimentsEnabled
                            ? QuadHotbarConfig.inventoryPages
                            : 1,
                    QuadHotbarConfig.enabled && QuadHotbarConfig.experimentsEnabled
                            ? QuadHotbarConfig.hotbarRows
                            : Math.min(4, QuadHotbarConfig.hotbarRows));
        }
    }

    public static boolean hasModdedServer() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && supportsExtendedSlots(minecraft);
    }

    /**
     * The selected inventory slot is not included in the vanilla login sync. Apply the server's
     * saved selection before handling local hotbar input.
     */
    public static void syncServerSelectedSlot(int selectedSlot) {
        serverSupportsExtendedSlots = true;
        pendingServerSelectedSlot = Mth.clamp(selectedSlot, 0, Inventory.INVENTORY_SIZE - 1);
        applyPendingServerSelection(Minecraft.getInstance());
        onPageSync();
    }

    private static void renderHotbars(GuiGraphics graphics, Minecraft minecraft) {
        int rows = getRows();
        syncActiveRowFromSelected(minecraft.player);
        int selectedVisualSlot =
                usesPages()
                        ? getVisualSlot(minecraft.player)
                        : activeVisualRow * VANILLA_HOTBAR_SLOTS;
        int selectedRow = selectedVisualSlot < 0 ? -1 : selectedVisualSlot / VANILLA_HOTBAR_SLOTS;
        int selectedColumn = getSelectedColumn(minecraft.player);

        RenderSystem.enableBlend();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, -90.0F);
        for (int row = 0; row < rows; row++) {
            int[] coords = getHotbarCoords(row, rows, graphics.guiWidth(), graphics.guiHeight());
            QuadHotbarVanillaUi.blitHud(
                    graphics, HOTBAR_SPRITE, coords[0], coords[1], HOTBAR_WIDTH, HOTBAR_HEIGHT);
        }

        if (selectedRow >= 0) {
            int[] selectedCoords =
                    getHotbarCoords(selectedRow, rows, graphics.guiWidth(), graphics.guiHeight());
            QuadHotbarVanillaUi.blitHud(
                    graphics,
                    HOTBAR_SELECTION_SPRITE,
                    selectedCoords[0] - 1 + selectedColumn * 20,
                    selectedCoords[1] - 1,
                    SELECTOR_WIDTH,
                    SELECTOR_HEIGHT);
        }
        renderOffhandSlotBackground(graphics, minecraft, rows);
        graphics.pose().popPose();
        RenderSystem.disableBlend();

        for (int row = 0; row < rows; row++) {
            int[] coords = getHotbarCoords(row, rows, graphics.guiWidth(), graphics.guiHeight());
            renderRowItems(graphics, minecraft, row, coords[0], coords[1]);
        }
        renderOffhandSlotItem(graphics, minecraft, rows);
        if (usesPages()) {
            QuadHotbarPages.State state = QuadHotbarPages.state(minecraft.player);
            int count = QuadHotbarPages.hotbarPageCount(state);
            int labelColor = hotbarPageLabelColor();
            if (count > 1 && labelColor != 0) {
                int[] top = getTopRowBounds(rows, graphics.guiWidth(), graphics.guiHeight());
                graphics.drawString(
                        minecraft.font,
                        (state.hotbarPage + 1) + "/" + count,
                        top[0] + 6,
                        top[1] + 7,
                        labelColor,
                        true);
            }
        }
    }

    private static void applyPendingServerSelection(Minecraft minecraft) {
        if (minecraft.player == null || pendingServerSelectedSlot < 0) {
            return;
        }

        minecraft.player.getInventory().selected = pendingServerSelectedSlot;
        pendingServerSelectedSlot = -1;
        if (!QuadHotbarConfig.enabled
                && minecraft.player.getInventory().selected >= VANILLA_HOTBAR_SLOTS) {
            setSelectedSlot(
                    minecraft.player,
                    minecraft.player.getInventory().selected % VANILLA_HOTBAR_SLOTS);
        } else {
            syncActiveRowFromSelected(minecraft.player);
        }
    }

    private static void ensureHudAboveExtraRows(Minecraft minecraft) {
        if (!shouldLiftExtraHud() || !(minecraft.gui instanceof ForgeGui)) {
            return;
        }

        int minHeight = 39 + HOTBAR_HEIGHT * (hotbarLevels() - 1);
        ((ForgeGui) minecraft.gui).leftHeight =
                Math.max(((ForgeGui) minecraft.gui).leftHeight, minHeight);
        ((ForgeGui) minecraft.gui).rightHeight =
                Math.max(((ForgeGui) minecraft.gui).rightHeight, minHeight);
    }

    private static int[] getHotbarCoords(int row, int rows, int screenWidth, int screenHeight) {
        return QuadHotbarLayout.coordinates(
                row,
                rows,
                screenWidth,
                screenHeight,
                HOTBAR_WIDTH,
                HOTBAR_HEIGHT,
                QuadHotbarConfig.mainHotbarCorner);
    }

    private static int[] getTopRowBounds(int rows, int screenWidth, int screenHeight) {
        int topY = screenHeight - HOTBAR_HEIGHT * ((rows + 1) / 2);
        int rightX = 0;
        for (int row = 0; row < rows; row++) {
            int[] coords = getHotbarCoords(row, rows, screenWidth, screenHeight);
            if (coords[1] == topY) rightX = Math.max(rightX, coords[0] + HOTBAR_WIDTH);
        }
        return new int[] {rightX, topY};
    }

    private static void renderOffhandSlotBackground(
            GuiGraphics graphics, Minecraft minecraft, int rows) {
        if (minecraft.player.getOffhandItem().isEmpty()) {
            return;
        }

        int[] bounds = getBottomRowBounds(rows, graphics.guiWidth(), graphics.guiHeight());
        boolean offhandOnLeft = minecraft.player.getMainArm() == HumanoidArm.RIGHT;
        if (offhandOnLeft) {
            QuadHotbarVanillaUi.blitHud(
                    graphics,
                    HOTBAR_OFFHAND_LEFT_SPRITE,
                    bounds[0] - 29,
                    graphics.guiHeight() - 23,
                    29,
                    24);
        } else {
            QuadHotbarVanillaUi.blitHud(
                    graphics,
                    HOTBAR_OFFHAND_RIGHT_SPRITE,
                    bounds[1],
                    graphics.guiHeight() - 23,
                    29,
                    24);
        }
    }

    private static void renderOffhandSlotItem(GuiGraphics graphics, Minecraft minecraft, int rows) {
        ItemStack offhand = minecraft.player.getOffhandItem();
        if (offhand.isEmpty()) {
            return;
        }

        int[] bounds = getBottomRowBounds(rows, graphics.guiWidth(), graphics.guiHeight());
        boolean offhandOnLeft = minecraft.player.getMainArm() == HumanoidArm.RIGHT;
        int x = offhandOnLeft ? bounds[0] - 26 : bounds[1] + 10;
        int y = graphics.guiHeight() - 16 - 3;
        graphics.renderItem(offhand, x, y);
        graphics.renderItemDecorations(minecraft.font, offhand, x, y);
    }

    private static int[] getBottomRowBounds(int rows, int screenWidth, int screenHeight) {
        int leftX = screenWidth;
        int rightX = 0;
        int slotY = screenHeight - HOTBAR_HEIGHT;
        for (int row = 0; row < rows; row++) {
            int[] coords = getHotbarCoords(row, rows, screenWidth, screenHeight);
            if (coords[1] != slotY) {
                continue;
            }
            leftX = Math.min(leftX, coords[0]);
            rightX = Math.max(rightX, coords[0] + HOTBAR_WIDTH);
        }
        return new int[] {leftX, rightX};
    }

    private static void renderRowItems(
            GuiGraphics graphics, Minecraft minecraft, int row, int hotbarX, int hotbarY) {
        for (int column = 0; column < VANILLA_HOTBAR_SLOTS; column++) {
            ItemStack stack =
                    usesPages()
                            ? QuadHotbarPages.visibleStack(minecraft.player, row, column)
                            : minecraft
                                    .player
                                    .getInventory()
                                    .getItem(
                                            QuadHotbarConfig.inventoryRowOrder.inventoryRow(row)
                                                            * VANILLA_HOTBAR_SLOTS
                                                    + column);
            if (stack.isEmpty()) {
                continue;
            }

            int x = hotbarX + 3 + column * 20;
            int y = hotbarY + 3;
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(minecraft.font, stack, x, y);
        }
    }

    private static void activateVisualSlot(Minecraft minecraft, int visualSlot) {
        if (minecraft.player == null) {
            return;
        }

        int max = getRows() * VANILLA_HOTBAR_SLOTS - 1;
        if (visualSlot < 0) {
            visualSlot = max;
        } else if (visualSlot > max) {
            visualSlot = 0;
        }

        int targetRow = visualSlot / VANILLA_HOTBAR_SLOTS;
        int column = visualSlot % VANILLA_HOTBAR_SLOTS;
        if (usesPages()) {
            awaitingRecenter = false;
            requestedVisualSlot = visualSlot;
            QuadHotbarNetwork.requestSlot(targetRow, column, nextSelectionRequestId());
            return;
        }
        setSelectedSlot(minecraft.player, targetRow * VANILLA_HOTBAR_SLOTS + column);
    }

    public static void setModEnabled(Minecraft minecraft, boolean enabled) {
        if (QuadHotbarConfig.enabled == enabled) {
            return;
        }

        if (enabled) {
            QuadHotbarConfig.setEnabled(true);
            if (savedSelectedSlotBeforeVanilla >= 0 && minecraft.player != null) {
                if (QuadHotbarConfig.experimentsEnabled && QuadHotbarConfig.inventoryPages > 1) {
                    setSelectedSlot(
                            minecraft.player,
                            savedSelectedSlotBeforeVanilla % VANILLA_HOTBAR_SLOTS);
                } else {
                    setSelectedSlot(minecraft.player, savedSelectedSlotBeforeVanilla);
                }
            }
            savedSelectedSlotBeforeVanilla = -1;
        } else {
            savedSelectedSlotBeforeVanilla =
                    minecraft.player == null ? -1 : getSelectedSlot(minecraft.player);
            QuadHotbarConfig.setEnabled(false);
            pendingVisualSlot = -1;
            if (minecraft.player != null) {
                setSelectedSlot(
                        minecraft.player,
                        minecraft.player.getInventory().selected % VANILLA_HOTBAR_SLOTS);
            }
        }
        QuadHotbarConfig.save();
        syncPageSettings();
    }

    private static void clampSelectedSlot(LocalPlayer player) {
        if (!usesPages()) {
            setSelectedSlot(player, getSelectedSlot(player));
        }
    }

    private static int getSelectedColumn(LocalPlayer player) {
        return usesPages()
                ? Math.floorMod(player.getInventory().selected, VANILLA_HOTBAR_SLOTS)
                : Mth.clamp(
                        getSelectedSlot(player) % VANILLA_HOTBAR_SLOTS,
                        0,
                        VANILLA_HOTBAR_SLOTS - 1);
    }

    private static int getSelectedSlot(LocalPlayer player) {
        if (usesPages()) {
            return getVisualSlot(player);
        }
        int physicalSlot =
                Mth.clamp(player.getInventory().selected, 0, Inventory.INVENTORY_SIZE - 1);
        int logicalRow =
                QuadHotbarConfig.inventoryRowOrder.hotbarRow(physicalSlot / VANILLA_HOTBAR_SLOTS);
        return Mth.clamp(logicalRow, 0, getRows() - 1) * VANILLA_HOTBAR_SLOTS
                + physicalSlot % VANILLA_HOTBAR_SLOTS;
    }

    private static int getVisualSlot(LocalPlayer player) {
        QuadHotbarPages.State state = QuadHotbarPages.state(player);
        int globalRow =
                QuadHotbarPages.orderedRow(
                        state,
                        state.activePage,
                        Mth.clamp(player.getInventory().selected, 0, Inventory.INVENTORY_SIZE - 1));
        int row = globalRow - state.hotbarPage * state.hotbarRows;
        if (row < 0 || row >= getRows()) {
            return -1;
        }
        return row * VANILLA_HOTBAR_SLOTS
                + Math.floorMod(player.getInventory().selected, VANILLA_HOTBAR_SLOTS);
    }

    private static void setSelectedSlot(LocalPlayer player, int slot) {
        int max = getRows() * VANILLA_HOTBAR_SLOTS - 1;
        int visualSlot = Mth.clamp(slot, 0, max);
        int clampedSlot =
                QuadHotbarConfig.inventoryRowOrder.inventoryRow(visualSlot / VANILLA_HOTBAR_SLOTS)
                                * VANILLA_HOTBAR_SLOTS
                        + visualSlot % VANILLA_HOTBAR_SLOTS;
        if (player.getInventory().selected != clampedSlot) {
            player.getInventory().selected = clampedSlot;
            player.connection.send(new ServerboundSetCarriedItemPacket(clampedSlot));
        }
        syncActiveRowFromSelected(player);
    }

    private static void syncActiveRowFromSelected(LocalPlayer player) {
        int rows = getRows();
        if (activeVisualRow >= rows) {
            activeVisualRow = 0;
        }
        if (usesPages()) {
            int visualSlot = getVisualSlot(player);
            activeVisualRow = visualSlot < 0 ? 0 : visualSlot / VANILLA_HOTBAR_SLOTS;
            return;
        }
        if (QuadHotbarConfig.enabled) {
            activeVisualRow =
                    Mth.clamp(getSelectedSlot(player) / VANILLA_HOTBAR_SLOTS, 0, rows - 1);
        }
    }

    public static int getRows() {
        if (!QuadHotbarConfig.enabled || !supportsExtendedSlots(Minecraft.getInstance())) {
            return 1;
        }
        if (usesPages()) {
            return QuadHotbarPages.visibleRows(
                    QuadHotbarPages.state(Minecraft.getInstance().player));
        }
        return Mth.clamp(QuadHotbarConfig.hotbarRows, 2, 4);
    }

    private static boolean usesPages() {
        Minecraft minecraft = Minecraft.getInstance();
        return QuadHotbarConfig.enabled
                && QuadHotbarConfig.experimentsEnabled
                && minecraft.player != null
                && supportsExtendedSlots(minecraft)
                && serverPolicy().allowFreedom();
    }

    private static boolean shouldLiftExtraHud() {
        return hotbarLevels() > 1;
    }

    private static int hotbarLevels() {
        return (getRows() + 1) / 2;
    }

    private static void updateSelectionMode(Minecraft minecraft) {
        if (minecraft.player == null || supportsExtendedSlots(minecraft)) {
            return;
        }

        activeVisualRow = 0;
        int selectedColumn = getSelectedColumn(minecraft.player);
        if (minecraft.player.getInventory().selected != selectedColumn) {
            setSelectedSlot(minecraft.player, selectedColumn);
        }
    }

    private static boolean supportsExtendedSlots(Minecraft minecraft) {
        return minecraft.getSingleplayerServer() != null || serverSupportsExtendedSlots;
    }
}
