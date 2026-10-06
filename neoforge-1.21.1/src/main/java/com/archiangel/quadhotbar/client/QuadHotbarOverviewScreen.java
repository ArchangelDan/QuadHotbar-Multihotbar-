package com.archiangel.quadhotbar.client;

import com.archiangel.quadhotbar.QuadHotbarConfig;
import com.archiangel.quadhotbar.QuadHotbarContainerOverview;
import com.archiangel.quadhotbar.QuadHotbarOverviewLayout;
import com.archiangel.quadhotbar.QuadHotbarOverviewMenu;
import com.archiangel.quadhotbar.QuadHotbarOverviewNetwork;
import com.archiangel.quadhotbar.mixin.SlotPositionAccessor;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

public final class QuadHotbarOverviewScreen
        extends AbstractContainerScreen<QuadHotbarOverviewMenu> {
    private static final int CELL = 18;
    private static final int PAGE_HEIGHT = 100;
    private int viewportX;
    private int viewportY;
    private int viewportWidth;
    private int viewportHeight;
    private int pagesAcross;
    private int contentHeight;
    private double scroll;
    private double targetScroll;
    private long lastScrollFrame = System.nanoTime();
    private boolean docked;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private boolean safeClick;
    private boolean returning;
    private AbstractContainerScreen<?> vanillaScreen;
    private QuadHotbarControlButton closeButton;
    private boolean delegatingBaseInput;
    private boolean baseGesture;
    private boolean draggingWindow;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean positionChanged;
    private int externalToken;

    public QuadHotbarOverviewScreen(
            QuadHotbarOverviewMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    public QuadHotbarOverviewScreen(
            QuadHotbarOverviewMenu menu,
            Inventory inventory,
            Component title,
            AbstractContainerScreen<?> parent,
            int token) {
        this(menu, inventory, title);
        vanillaScreen = parent;
        externalToken = token;
    }

    public int externalToken() {
        return externalToken;
    }

    @Override
    protected void init() {
        docked = !menu.standalone;
        if (docked) {
            // CreativeScreen's constructor temporarily selects its local catalog menu.
            // Keep our authoritative server container while using the actual vanilla UI.
            if (vanillaScreen == null)
                vanillaScreen =
                        minecraft.player.hasInfiniteMaterials()
                                ? new CreativeModeInventoryScreen(
                                        minecraft.player,
                                        minecraft.player.connection.enabledFeatures(),
                                        minecraft.options.operatorItemsTab().get())
                                : new InventoryScreen(minecraft.player);
            if (!menu.external) minecraft.player.containerMenu = menu;
            if (vanillaScreen.width == 0) vanillaScreen.init(minecraft, width, height);
            else if (!menu.external
                    || vanillaScreen.width != width
                    || vanillaScreen.height != height)
                vanillaScreen.resize(minecraft, width, height);
            if (!menu.external) minecraft.player.containerMenu = menu;
        }
        int requestedWidth =
                menu.external ? QuadHotbarOverviewSizing.effectiveWidth() : menu.columns;
        pagesAcross = Math.min(requestedWidth, QuadHotbarOverviewSizing.maxWidth()) / 9;
        viewportWidth = pagesAcross * 162 + (pagesAcross - 1) * 8;
        viewportHeight = Math.max(18, Math.min(menu.viewportRows * CELL, height - 64));
        imageWidth = viewportWidth + 32;
        imageHeight = viewportHeight + 44;
        contentHeight = QuadHotbarOverviewLayout.rows(menu.pageCount, pagesAcross) * PAGE_HEIGHT;
        viewportX = 8;
        viewportY = 28;
        super.init();
        if (docked) {
            leftPos = vanillaScreen.getGuiLeft() + vanillaScreen.getXSize() + 28;
            topPos = automaticTop();
        }
        if (QuadHotbarConfig.overviewX >= 0) leftPos = QuadHotbarConfig.overviewX;
        if (QuadHotbarConfig.overviewY >= 0) topPos = QuadHotbarConfig.overviewY;
        clampPosition();
        closeButton =
                addRenderableWidget(
                        QuadHotbarControlButton.close(
                                leftPos + imageWidth - 23,
                                topPos + 3,
                                button -> returnToInventoryWithCursor()));
        updateSlots();
        // Only the player's own overview controls inventory auto-open, not chest/death panels.
        if (!menu.external) QuadHotbarInventoryPageUi.rememberOverviewState(true);
    }

    private int pageColumn(int page) {
        return QuadHotbarOverviewLayout.column(
                page,
                menu.pageCount,
                pagesAcross,
                QuadHotbarConfig.overviewHorizontal,
                QuadHotbarConfig.overviewFirstPageRight);
    }

    private int pageRow(int page) {
        return QuadHotbarOverviewLayout.row(
                page, menu.pageCount, pagesAcross, QuadHotbarConfig.overviewHorizontal);
    }

    private void updateSlots() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - viewportHeight));
        for (int index = 0; index < menu.slots.size(); index++) {
            if (index >= menu.pageSlots) continue; // Vanilla draws its own real slots.
            int x;
            int y;
            boolean visible;
            {
                int page = index / 36;
                int slot = index % 36;
                x = viewportX + pageColumn(page) * 170 + slot % 9 * CELL;
                y =
                        viewportY
                                + pageRow(page) * PAGE_HEIGHT
                                + 18
                                + displayRow(slot) * CELL
                                - (int) scroll;
                visible = y + CELL > viewportY && y < viewportY + viewportHeight;
            }
            Slot target = menu.slots.get(index);
            ((QuadHotbarOverviewMenu.OverviewSlot) target).visible = visible;
            ((SlotPositionAccessor) target).quadhotbar$setX(visible ? x + 1 : -10000);
            ((SlotPositionAccessor) target).quadhotbar$setY(visible ? y + 1 : -10000);
        }
    }

    private static int displayRow(int slot) {
        return slot < 9 ? 3 : slot / 9 - 1;
    }

    private int automaticTop() {
        // Creative tabs extend 28 GUI pixels above the container's nominal top.
        return vanillaScreen.getGuiTop()
                - (vanillaScreen instanceof CreativeModeInventoryScreen ? 28 : 0);
    }

    private void advanceScroll() {
        long now = System.nanoTime();
        double elapsed = Math.min(0.05, Math.max(0, (now - lastScrollFrame) / 1_000_000_000.0));
        lastScrollFrame = now;
        double maximum = Math.max(0, contentHeight - viewportHeight);
        targetScroll = Mth.clamp(targetScroll, 0, maximum);
        scroll = Mth.clamp(scroll, 0, maximum);
        scroll += (targetScroll - scroll) * (1 - Math.exp(-20 * elapsed));
        if (Math.abs(targetScroll - scroll) < 0.05) scroll = targetScroll;
    }

    private boolean inViewport(double x, double y) {
        return x >= leftPos + viewportX
                && x < leftPos + viewportX + viewportWidth
                && y >= topPos + viewportY
                && y < topPos + viewportY + viewportHeight;
    }

    private void clipViewport(GuiGraphics graphics) {
        graphics.enableScissor(
                leftPos + viewportX,
                topPos + viewportY,
                leftPos + viewportX + viewportWidth,
                topPos + viewportY + viewportHeight);
    }

    @Override
    protected boolean isHovering(
            int x, int y, int slotWidth, int slotHeight, double mouseX, double mouseY) {
        return inViewport(mouseX, mouseY)
                && super.isHovering(x, y, slotWidth, slotHeight, mouseX, mouseY);
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        clipViewport(graphics);
        graphics.pose().pushPose();
        graphics.pose().translate(0, (int) scroll - scroll, 0);
        super.renderSlot(graphics, slot);
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    @Override
    protected void renderSlotHighlight(
            GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        clipViewport(graphics);
        graphics.pose().pushPose();
        graphics.pose().translate(0, (int) scroll - scroll, 0);
        super.renderSlotHighlight(graphics, slot, mouseX, mouseY, partialTick);
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        QuadHotbarVanillaUi.container(graphics, leftPos, topPos, imageWidth, imageHeight);
        graphics.drawString(
                font,
                font.plainSubstrByWidth(title.getString(), imageWidth - 38),
                leftPos + 8,
                topPos + 9,
                0x404040,
                false);
        graphics.enableScissor(
                leftPos + viewportX,
                topPos + viewportY,
                leftPos + viewportX + viewportWidth,
                topPos + viewportY + viewportHeight);
        graphics.fill(
                leftPos + viewportX,
                topPos + viewportY,
                leftPos + viewportX + viewportWidth,
                topPos + viewportY + viewportHeight,
                0x1A000000);
        graphics.pose().pushPose();
        graphics.pose().translate(0, (int) scroll - scroll, 0);
        for (int page = 0; page < menu.pageCount; page++) {
            int x = leftPos + viewportX + pageColumn(page) * 170;
            int y = topPos + viewportY + pageRow(page) * PAGE_HEIGHT - (int) scroll;
            if (y + PAGE_HEIGHT < topPos + viewportY || y > topPos + viewportY + viewportHeight)
                continue;
            Component pageTitle =
                    Component.translatable("quadhotbar.overview.page", page + 1)
                            .withStyle(
                                    page == menu.activePage
                                            ? ChatFormatting.BOLD
                                            : ChatFormatting.RESET);
            graphics.drawString(
                    font, pageTitle, x + (162 - font.width(pageTitle)) / 2, y + 4, 0x404040, false);
            graphics.hLine(x, x + 161, y + 15, 0xFF8B8B8B);
            graphics.hLine(x, x + 161, y + 16, 0xFFFFFFFF);
            for (int slot = 0; slot < 36; slot++) {
                QuadHotbarVanillaUi.slot(
                        graphics, x + slot % 9 * CELL, y + 18 + displayRow(slot) * CELL, slot < 9);
            }
        }
        graphics.pose().popPose();
        graphics.disableScissor();
        int trackX = leftPos + viewportX + viewportWidth + 4;
        int trackY = topPos + viewportY;
        int knobY = scrollbarTop();
        QuadHotbarVanillaUi.creativeScrollbar(
                graphics,
                trackX,
                trackY,
                viewportHeight,
                knobY,
                scrollbarHeight(),
                contentHeight > viewportHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        advanceScroll();
        if (docked) {
            if (QuadHotbarConfig.overviewX < 0
                    && QuadHotbarConfig.overviewY < 0
                    && !draggingWindow) {
                leftPos = vanillaScreen.getGuiLeft() + vanillaScreen.getXSize() + 28;
                topPos = automaticTop();
                clampPosition();
                closeButton.setX(leftPos + imageWidth - 23);
                closeButton.setY(topPos + 3);
            }
            boolean covered = containsPanel(mouseX, mouseY);
            vanillaScreen.renderWithTooltip(
                    graphics, covered ? -10000 : mouseX, covered ? -10000 : mouseY, partialTick);
        }
        updateSlots();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        graphics.pose().popPose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int x, int y, float partialTick) {
        if (!docked) renderTransparentBackground(graphics);
        renderBg(graphics, partialTick, x, y);
    }

    public boolean isBaseScreen(Object screen) {
        return screen == vanillaScreen;
    }

    public boolean isDelegatingBaseInput() {
        return delegatingBaseInput;
    }

    public boolean containsPanel(double x, double y) {
        return x >= leftPos && x < leftPos + imageWidth && y >= topPos && y < topPos + imageHeight;
    }

    private boolean baseInput(Supplier<Boolean> action) {
        delegatingBaseInput = true;
        try {
            return action.get();
        } finally {
            delegatingBaseInput = false;
        }
    }

    private void clampPosition() {
        leftPos = Mth.clamp(leftPos, 0, Math.max(0, width - imageWidth));
        topPos = Mth.clamp(topPos, 0, Math.max(0, height - imageHeight));
    }

    private void savePosition() {
        if (positionChanged) {
            QuadHotbarConfig.setOverviewPosition(leftPos, topPos);
            QuadHotbarConfig.save();
            positionChanged = false;
        }
    }

    @Override
    protected boolean hasClickedOutside(double x, double y, int left, int top, int button) {
        return false; // Headers, page separators and panel margins are safe cursor-holding areas.
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (returning) return true;
        if (docked && !containsPanel(x, y)) {
            baseGesture = true;
            return baseInput(() -> vanillaScreen.mouseClicked(x, y, button));
        }
        baseGesture = false;
        if (button == 0
                && QuadHotbarConfig.overviewDraggable
                && y >= topPos
                && y < topPos + 24
                && x >= leftPos
                && x < leftPos + imageWidth - 26) {
            draggingWindow = true;
            dragOffsetX = x - leftPos;
            dragOffsetY = y - topPos;
            return true;
        }
        if (button == 0
                && x >= leftPos + viewportX + viewportWidth + 3
                && x < leftPos + viewportX + viewportWidth + 17
                && y >= topPos + viewportY
                && y < topPos + viewportY + viewportHeight) {
            draggingScrollbar = true;
            int knobTop = scrollbarTop();
            scrollbarGrabOffset =
                    y >= knobTop && y < knobTop + scrollbarHeight()
                            ? y - knobTop
                            : scrollbarHeight() / 2.0;
            dragScrollbar(y);
            return true;
        }
        boolean overSlot =
                inViewport(x, y)
                        && menu.slots.stream()
                                .anyMatch(
                                        slot ->
                                                slot.isActive()
                                                        && x >= leftPos + slot.x
                                                        && x < leftPos + slot.x + 16
                                                        && y >= topPos + slot.y
                                                        && y < topPos + slot.y + 16);
        if (!overSlot && !(x >= leftPos + imageWidth - 23 && y < topPos + 24)) {
            safeClick = true;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (draggingWindow) {
            draggingWindow = false;
            savePosition();
            return true;
        }
        if (baseGesture) {
            baseGesture = false;
            // A gesture started underneath must not place/drop into an obscured
            // vanilla slot when released over the overlay.
            return baseInput(
                    () ->
                            vanillaScreen.mouseReleased(
                                    containsPanel(x, y) ? vanillaScreen.getGuiLeft() + 88 : x,
                                    containsPanel(x, y) ? vanillaScreen.getGuiTop() + 4 : y,
                                    button));
        }
        if (draggingScrollbar || safeClick || returning) {
            draggingScrollbar = false;
            safeClick = false;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (draggingWindow) {
            leftPos = (int) Math.round(x - dragOffsetX);
            topPos = (int) Math.round(y - dragOffsetY);
            clampPosition();
            closeButton.setX(leftPos + imageWidth - 23);
            closeButton.setY(topPos + 3);
            positionChanged = true;
            updateSlots();
            return true;
        }
        if (baseGesture)
            return containsPanel(x, y)
                    || baseInput(() -> vanillaScreen.mouseDragged(x, y, button, dx, dy));
        if (draggingScrollbar) {
            dragScrollbar(y);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    private int scrollbarHeight() {
        // Match the creative inventory's fixed native handle, regardless of page count.
        return Math.min(15, Math.max(1, viewportHeight - 2));
    }

    private int scrollbarTravel() {
        // The handle touches the inside of the track's one-pixel border at both ends.
        return Math.max(0, viewportHeight - 2 - scrollbarHeight());
    }

    private int scrollbarTop() {
        double fraction = Mth.clamp(scroll / Math.max(1, contentHeight - viewportHeight), 0, 1);
        return topPos + viewportY + 1 + (int) (fraction * scrollbarTravel());
    }

    private void dragScrollbar(double y) {
        double fraction =
                (y - topPos - viewportY - 1 - scrollbarGrabOffset) / Math.max(1, scrollbarTravel());
        scroll = Mth.clamp(fraction, 0, 1) * Math.max(0, contentHeight - viewportHeight);
        targetScroll = scroll;
        updateSlots();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (returning) return true;
        if (docked && !containsPanel(x, y))
            return baseInput(() -> vanillaScreen.mouseScrolled(x, y, horizontal, vertical));
        targetScroll =
                Mth.clamp(
                        targetScroll - vertical * QuadHotbarConfig.overviewScrollStep,
                        0,
                        Math.max(0, contentHeight - viewportHeight));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (returning) return true;
        // Inventory always closes the entire container, even if the overview binding conflicts.
        // This must not be treated as manually hiding just the pages panel.
        if (key == GLFW.GLFW_KEY_ESCAPE || minecraft.options.keyInventory.matches(key, scanCode)) {
            onClose();
            return true;
        }
        if (QuadHotbarKeyMappings.OPEN_OVERVIEW.matches(key, scanCode)) {
            returnToInventoryWithCursor();
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
            targetScroll =
                    Mth.clamp(
                            targetScroll
                                    + (key == GLFW.GLFW_KEY_PAGE_DOWN
                                            ? viewportHeight
                                            : -viewportHeight),
                            0,
                            Math.max(0, contentHeight - viewportHeight));
            return true;
        }
        return docked && hoveredSlot == null
                ? baseInput(() -> vanillaScreen.keyPressed(key, scanCode, modifiers))
                : super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return docked
                ? baseInput(() -> vanillaScreen.charTyped(character, modifiers))
                : super.charTyped(character, modifiers);
    }

    @Override
    protected void containerTick() {
        if (vanillaScreen != null) vanillaScreen.tick();
    }

    @Override
    public void onClose() {
        savePosition();
        if (menu.external) QuadHotbarContainerOverview.close(menu.containerId, externalToken);
        else if (minecraft.player.hasInfiniteMaterials())
            QuadHotbarOverviewNetwork.syncCreativeCursor();
        super.onClose();
    }

    @Override
    public void removed() {
        savePosition();
        if (menu.external) QuadHotbarContainerOverview.close(menu.containerId, externalToken);
        else if (vanillaScreen != null) vanillaScreen.removed();
        super.removed();
    }

    public void returnToInventoryWithCursor() {
        savePosition();
        if (menu.external) {
            closeExternalPanel();
            return;
        }
        if (!returning) {
            QuadHotbarInventoryPageUi.rememberOverviewState(false);
            returning = true;
            QuadHotbarOverviewNetwork.returnToInventory();
        }
    }

    public void closeExternalPanel() {
        savePosition();
        returning = true;
        QuadHotbarContainerOverview.close(menu.containerId, externalToken);
        if (minecraft.player != null && minecraft.player.containerMenu == vanillaScreen.getMenu()) {
            minecraft.setScreen(vanillaScreen);
            QuadHotbarInventoryPageUi.suppressAutoOpen(vanillaScreen);
        }
    }

    @Override
    protected void slotClicked(Slot slot, int index, int button, ClickType type) {
        if (menu.external)
            QuadHotbarContainerOverview.click(menu.containerId, externalToken, index, button, type);
        else super.slotClicked(slot, index, button, type);
    }

    public boolean isReturningToInventory() {
        return returning;
    }

    public static void returnToInventory() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && minecraft.player.containerMenu == minecraft.player.inventoryMenu) {
            minecraft.setScreen(new InventoryScreen(minecraft.player));
            // Closing just this panel must not immediately auto-open it again.
            QuadHotbarInventoryPageUi.suppressAutoOpen(minecraft.screen);
        }
    }

    public void showGhostRecipe(net.minecraft.world.item.crafting.RecipeHolder<?> recipe) {
        if (vanillaScreen instanceof InventoryScreen inventory)
            inventory
                    .getRecipeBookComponent()
                    .setupGhostRecipe(recipe, minecraft.player.inventoryMenu.slots);
    }
}
