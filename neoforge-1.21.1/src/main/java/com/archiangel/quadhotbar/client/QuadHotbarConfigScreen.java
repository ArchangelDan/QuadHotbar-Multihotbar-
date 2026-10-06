package com.archiangel.quadhotbar.client;

import com.archiangel.quadhotbar.QuadHotbarConfig;
import com.archiangel.quadhotbar.QuadHotbarLayout;
import com.archiangel.quadhotbar.QuadHotbarPages;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class QuadHotbarConfigScreen extends Screen {
    private final Screen parent;

    private static Component profileTitle(String key) {
        return Component.translatable(
                "quadhotbar.config.profile_title",
                Component.translatable(key),
                Component.translatable(
                        QuadHotbarConfig.multiplayerContext()
                                ? "quadhotbar.config.profile.multiplayer"
                                : "quadhotbar.config.profile.singleplayer"));
    }

    public QuadHotbarConfigScreen(Screen parent) {
        super(profileTitle("quadhotbar.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int size = Math.min(220, width - 40);
        addRenderableWidget(
                Button.builder(
                                Component.translatable("quadhotbar.config.client_settings"),
                                b -> minecraft.setScreen(new GeneralSettingsScreen(this)))
                        .bounds((width - size) / 2, 54, size, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.translatable("gui.done"), b -> onClose())
                        .bounds((width - size) / 2, height - 29, size, 20)
                        .build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float tick) {
        super.render(graphics, x, y, tick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
    }

    private static final class GeneralSettingsScreen extends Screen {
        private static long warningUntil;
        private final Screen parent;
        private final List<Setting> settings = new ArrayList<>();
        private final List<Section> sections = new ArrayList<>();
        private Button done;
        private int labelX, labelWidth, valueX, valueWidth, resetX, contentHeight;
        private double scroll;

        private GeneralSettingsScreen(Screen parent) {
            super(profileTitle("quadhotbar.config.client_settings"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            settings.clear();
            sections.clear();
            contentHeight = 0;
            int contentWidth = Math.min(480, width - 28);
            labelX = (width - contentWidth) / 2;
            valueWidth = Math.min(150, Math.max(96, contentWidth / 3));
            resetX = labelX + contentWidth - 46;
            valueX = resetX - valueWidth - 4;
            labelWidth = Math.max(0, valueX - labelX - 10);
            toggle(
                    "quadhotbar.config.enabled.description",
                    () -> QuadHotbarConfig.enabled,
                    v -> QuadHotbarClientEvents.setModEnabled(minecraft, v),
                    true,
                    () -> true);
            button(
                    "quadhotbar.config.hotbar_rows.description",
                    () ->
                            Component.translatable(
                                    "quadhotbar.config.hotbar_rows.value", displayedRows()),
                    this::changeRows,
                    () -> QuadHotbarConfig.setHotbarRows(2),
                    () -> QuadHotbarConfig.hotbarRows == 2,
                    () -> true);
            toggle(
                    "quadhotbar.config.scroll_between_hotbars.description",
                    () -> QuadHotbarConfig.scrollBetweenHotbars,
                    QuadHotbarConfig::setScrollBetweenHotbars,
                    true,
                    () -> true);
            toggle(
                    "quadhotbar.config.show_all_keybinds",
                    () -> QuadHotbarConfig.showAllKeybinds,
                    QuadHotbarConfig::setShowAllKeybinds,
                    false,
                    () -> true);
            button(
                            "quadhotbar.layout.order",
                            () ->
                                    Component.translatable(
                                            QuadHotbarConfig.inventoryRowOrder.translationKey()),
                            d ->
                                    QuadHotbarConfig.setInventoryRowOrder(
                                            QuadHotbarConfig.inventoryRowOrder
                                                            == QuadHotbarLayout.InventoryOrder
                                                                    .TOP_TO_BOTTOM
                                                    ? QuadHotbarLayout.InventoryOrder.BOTTOM_TO_TOP
                                                    : QuadHotbarLayout.InventoryOrder
                                                            .TOP_TO_BOTTOM),
                            () ->
                                    QuadHotbarConfig.setInventoryRowOrder(
                                            QuadHotbarLayout.InventoryOrder.TOP_TO_BOTTOM),
                            () ->
                                    QuadHotbarConfig.inventoryRowOrder
                                            == QuadHotbarLayout.InventoryOrder.TOP_TO_BOTTOM,
                            () -> true)
                    .value
                    .setTooltip(
                            Tooltip.create(Component.translatable("quadhotbar.layout.order_hint")));
            button(
                            "quadhotbar.layout.corner",
                            () ->
                                    Component.translatable(
                                            QuadHotbarConfig.mainHotbarCorner.translationKey()),
                            d -> {
                                var corners = QuadHotbarLayout.MainCorner.values();
                                QuadHotbarConfig.setMainHotbarCorner(
                                        corners[
                                                Math.floorMod(
                                                        QuadHotbarConfig.mainHotbarCorner.ordinal()
                                                                + d,
                                                        corners.length)]);
                            },
                            () ->
                                    QuadHotbarConfig.setMainHotbarCorner(
                                            QuadHotbarLayout.MainCorner.BOTTOM_LEFT),
                            () ->
                                    QuadHotbarConfig.mainHotbarCorner
                                            == QuadHotbarLayout.MainCorner.BOTTOM_LEFT,
                            () -> true)
                    .value
                    .setTooltip(
                            Tooltip.create(
                                    Component.translatable("quadhotbar.layout.corner_hint")));
            section("quadhotbar.config.experiments.title");
            toggle(
                    "quadhotbar.config.experiments.description",
                    () -> QuadHotbarConfig.experimentsEnabled && freedomAllowed(),
                    v -> {
                        QuadHotbarConfig.setExperimentsEnabled(v);
                        warningUntil = v ? System.nanoTime() + 4_000_000_000L : 0;
                    },
                    false,
                    GeneralSettingsScreen::freedomAllowed);
            number(
                    "quadhotbar.config.inventory_pages.description",
                    () -> available().pages(),
                    this::setPages,
                    () -> QuadHotbarPages.pageStep(available().rows()),
                    GeneralSettingsScreen::maxPages,
                    () -> QuadHotbarPages.pageStep(available().rows()),
                    GeneralSettingsScreen::freedom);
            toggle(
                    "quadhotbar.config.page_buttons.description",
                    () -> QuadHotbarConfig.showPageButtons,
                    QuadHotbarConfig::setShowPageButtons,
                    true,
                    GeneralSettingsScreen::freedom);
            toggle(
                    "quadhotbar.config.page_labels.description",
                    () -> QuadHotbarConfig.alwaysShowPageLabels,
                    QuadHotbarConfig::setAlwaysShowPageLabels,
                    false,
                    GeneralSettingsScreen::freedom);
            section("quadhotbar.overview.settings", GeneralSettingsScreen::overviewPermitted);
            toggle(
                    "quadhotbar.overview.enabled",
                    () -> QuadHotbarConfig.overviewEnabled,
                    QuadHotbarConfig::setOverviewEnabled,
                    true,
                    GeneralSettingsScreen::overviewPermitted);
            toggle(
                    "quadhotbar.overview.button",
                    () -> QuadHotbarConfig.showOverviewButton,
                    QuadHotbarConfig::setShowOverviewButton,
                    true,
                    () ->
                            overviewAllowed()
                                    && (!server()
                                            || QuadHotbarClientEvents.serverPolicy()
                                                    .showOverviewButton()));
            button(
                    "quadhotbar.overview.width",
                    () ->
                            Component.literal(
                                    Integer.toString(QuadHotbarOverviewSizing.effectiveWidth())),
                    GeneralSettingsScreen::changeWidth,
                    () -> QuadHotbarConfig.setOverviewWidth(9),
                    () -> QuadHotbarConfig.overviewWidth == 9,
                    () -> overviewAllowed() && maxWidth() > 9);
            button(
                    "quadhotbar.overview.first_page_side",
                    () ->
                            Component.translatable(
                                    QuadHotbarConfig.overviewFirstPageRight
                                            ? "quadhotbar.overview.right"
                                            : "quadhotbar.overview.left"),
                    d ->
                            QuadHotbarConfig.setOverviewFirstPageRight(
                                    !QuadHotbarConfig.overviewFirstPageRight),
                    () -> QuadHotbarConfig.setOverviewFirstPageRight(false),
                    () -> !QuadHotbarConfig.overviewFirstPageRight,
                    () -> overviewAllowed() && QuadHotbarOverviewSizing.effectiveWidth() > 9);
            button(
                    "quadhotbar.overview.page_order",
                    () ->
                            Component.translatable(
                                    QuadHotbarConfig.overviewHorizontal
                                            ? "quadhotbar.overview.horizontal"
                                            : "quadhotbar.overview.vertical"),
                    d ->
                            QuadHotbarConfig.setOverviewHorizontal(
                                    !QuadHotbarConfig.overviewHorizontal),
                    () -> QuadHotbarConfig.setOverviewHorizontal(true),
                    () -> QuadHotbarConfig.overviewHorizontal,
                    () -> overviewAllowed() && QuadHotbarOverviewSizing.effectiveWidth() > 9);
            toggle(
                    "quadhotbar.overview.auto_open",
                    () -> QuadHotbarConfig.autoOpenOverview,
                    QuadHotbarConfig::setAutoOpenOverview,
                    true,
                    GeneralSettingsScreen::overviewAllowed);
            number(
                    "quadhotbar.overview.height",
                    () -> Math.min(maxHeight(), QuadHotbarConfig.overviewHeight),
                    QuadHotbarConfig::setOverviewHeight,
                    () -> 1,
                    GeneralSettingsScreen::maxHeight,
                    () -> Math.min(9, maxHeight()),
                    GeneralSettingsScreen::overviewAllowed);
            toggle(
                    "quadhotbar.overview.draggable",
                    () -> QuadHotbarConfig.overviewDraggable,
                    QuadHotbarConfig::setOverviewDraggable,
                    true,
                    GeneralSettingsScreen::overviewAllowed);
            number(
                    "quadhotbar.overview.scroll_step",
                    () -> QuadHotbarConfig.overviewScrollStep,
                    QuadHotbarConfig::setOverviewScrollStep,
                    () -> 1,
                    () -> QuadHotbarConfig.MAX_OVERVIEW_SCROLL_STEP,
                    () -> QuadHotbarConfig.DEFAULT_OVERVIEW_SCROLL_STEP,
                    GeneralSettingsScreen::overviewAllowed);
            button(
                            "quadhotbar.overview.position",
                            () ->
                                    Component.translatable(
                                            customPosition()
                                                    ? "quadhotbar.overview.custom"
                                                    : "quadhotbar.overview.automatic"),
                            d ->
                                    QuadHotbarConfig.setOverviewPosition(
                                            customPosition() ? -1 : Math.max(0, width - 196),
                                            customPosition() ? -1 : 36),
                            () -> QuadHotbarConfig.setOverviewPosition(-1, -1),
                            () -> !customPosition(),
                            GeneralSettingsScreen::overviewAllowed)
                    .value
                    .setTooltip(
                            Tooltip.create(
                                    Component.translatable("quadhotbar.overview.drag_hint")));
            number(
                    "quadhotbar.overview.position_x",
                    () -> Math.max(0, QuadHotbarConfig.overviewX),
                    v -> QuadHotbarConfig.setOverviewPosition(v, QuadHotbarConfig.overviewY),
                    () -> 0,
                    () -> 10000,
                    () -> 0,
                    () -> overviewAllowed() && customPosition());
            number(
                    "quadhotbar.overview.position_y",
                    () -> Math.max(0, QuadHotbarConfig.overviewY),
                    v -> QuadHotbarConfig.setOverviewPosition(QuadHotbarConfig.overviewX, v),
                    () -> 0,
                    () -> 10000,
                    () -> 0,
                    () -> overviewAllowed() && customPosition());
            done =
                    addRenderableWidget(
                            Button.builder(Component.translatable("gui.done"), b -> onClose())
                                    .bounds((width - 180) / 2, height - 27, 180, 20)
                                    .build());
            refresh();
        }

        private void section(String key) {
            section(key, () -> true);
        }

        private void section(String key, BooleanSupplier allowed) {
            sections.add(new Section(key, contentHeight, allowed));
            contentHeight += 30;
        }

        private Setting button(
                String key,
                Supplier<Component> message,
                IntConsumer change,
                Runnable reset,
                BooleanSupplier isDefault,
                BooleanSupplier allowed) {
            Setting s = new Setting(key, contentHeight, allowed, isDefault);
            settings.add(s);
            contentHeight += 26;
            s.change = change;
            s.message = message;
            s.value =
                    addRenderableWidget(
                            Button.builder(message.get(), b -> apply(() -> change.accept(1)))
                                    .bounds(valueX, 0, valueWidth, 20)
                                    .build());
            s.widgets.add(s.value);
            reset(s, reset);
            return s;
        }

        private void toggle(
                String key,
                BooleanSupplier get,
                Consumer<Boolean> set,
                boolean defaultValue,
                BooleanSupplier allowed) {
            button(
                    key,
                    () ->
                            Component.translatable(
                                    get.getAsBoolean()
                                            ? "quadhotbar.config.on"
                                            : "quadhotbar.config.off"),
                    d -> set.accept(!get.getAsBoolean()),
                    () -> set.accept(defaultValue),
                    () -> get.getAsBoolean() == defaultValue,
                    allowed);
        }

        private void number(
                String key,
                IntSupplier get,
                IntConsumer set,
                IntSupplier minimum,
                IntSupplier maximum,
                IntSupplier defaultValue,
                BooleanSupplier allowed) {
            Setting s =
                    new Setting(
                            key,
                            contentHeight,
                            allowed,
                            () -> get.getAsInt() == defaultValue.getAsInt());
            settings.add(s);
            contentHeight += 26;
            s.get = get;
            s.set = v -> set.accept(Mth.clamp(v, minimum.getAsInt(), maximum.getAsInt()));
            s.minimum = minimum;
            s.maximum = maximum;
            s.previous =
                    addRenderableWidget(
                            Button.builder(
                                            Component.literal("<"),
                                            b ->
                                                    apply(
                                                            () ->
                                                                    s.set.accept(
                                                                            get.getAsInt()
                                                                                    - step(key))))
                                    .bounds(valueX, 0, 20, 20)
                                    .build());
            s.field =
                    addRenderableWidget(
                            new EditBox(
                                    font,
                                    valueX + 24,
                                    0,
                                    valueWidth - 48,
                                    20,
                                    Component.translatable(key)));
            s.field.setMaxLength(5);
            s.field.setFilter(text -> text.matches("[0-9]*"));
            s.next =
                    addRenderableWidget(
                            Button.builder(
                                            Component.literal(">"),
                                            b ->
                                                    apply(
                                                            () ->
                                                                    s.set.accept(
                                                                            get.getAsInt()
                                                                                    + step(key))))
                                    .bounds(valueX + valueWidth - 20, 0, 20, 20)
                                    .build());
            s.widgets.addAll(List.of(s.previous, s.field, s.next));
            reset(s, () -> s.set.accept(defaultValue.getAsInt()));
        }

        private int step(String key) {
            return key.equals("quadhotbar.config.inventory_pages.description")
                    ? QuadHotbarPages.pageStep(available().rows())
                    : 1;
        }

        private void reset(Setting s, Runnable action) {
            s.reset =
                    addRenderableWidget(
                            Button.builder(
                                            Component.translatable("quadhotbar.config.reset"),
                                            b -> apply(action))
                                    .bounds(resetX, 0, 46, 20)
                                    .build());
            s.widgets.add(s.reset);
            String tooltipKey =
                    s.key.equals("quadhotbar.overview.auto_open")
                                    || s.key.equals("quadhotbar.overview.width")
                                    || s.key.equals("quadhotbar.overview.page_order")
                                    || s.key.equals("quadhotbar.overview.first_page_side")
                            ? s.key + ".description"
                            : s.key;
            for (AbstractWidget widget : s.widgets)
                widget.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        }

        private void apply(Runnable action) {
            commitInputs();
            action.run();
            save();
        }

        private void save() {
            QuadHotbarConfig.save();
            QuadHotbarClientEvents.syncPageSettings();
            refresh();
        }

        private void commitInputs() {
            for (Setting s : settings)
                if (s.field != null && s.field.isFocused()) {
                    // Focusing an unchanged effective value must not save a server cap over
                    // the player's personal preference.
                    if (s.allowed.getAsBoolean()
                            && !s.field.getValue().isEmpty()
                            && !s.field.getValue().equals(s.displayedInput)) {
                        int entered = Integer.parseInt(s.field.getValue());
                        s.set.accept(entered);
                    }
                    s.field.setFocused(false);
                }
            setFocused(null);
        }

        private void refresh() {
            scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - (height - 74)));
            for (Setting s : settings) {
                int y = 40 + s.y - (int) scroll;
                boolean visible = y >= 36 && y + 20 <= height - 34,
                        enabled = s.allowed.getAsBoolean();
                for (AbstractWidget widget : s.widgets) {
                    widget.setY(y);
                    widget.visible = visible;
                    widget.active = enabled;
                }
                s.reset.active = enabled && !s.isDefault.getAsBoolean();
                if (s.value != null) s.value.setMessage(s.message.get());
                if (s.field != null) {
                    s.field.setEditable(enabled);
                    if (!s.field.isFocused()) {
                        s.displayedInput = Integer.toString(s.get.getAsInt());
                        s.field.setValue(s.displayedInput);
                    }
                    s.previous.active = enabled && s.get.getAsInt() > s.minimum.getAsInt();
                    s.next.active = enabled && s.get.getAsInt() < s.maximum.getAsInt();
                }
            }
        }

        private void changeRows(int d) {
            if (!freedom() && QuadHotbarConfig.hotbarRows > maxRows()) {
                // First explicit edit accepts the ordinary cap, in either click direction.
                QuadHotbarConfig.setHotbarRows(maxRows());
                return;
            }
            int next = available().rows() + d;
            while (next >= 2 && next <= maxRows() && QuadHotbarPages.pageStep(next) > maxPages())
                next += d;
            if (next < 2 || next > maxRows()) next = d > 0 ? 2 : maxRows();
            QuadHotbarConfig.setHotbarRows(next);
        }

        private static int displayedRows() {
            return !QuadHotbarConfig.experimentsEnabled && QuadHotbarConfig.hotbarRows > 4
                    ? QuadHotbarConfig.hotbarRows
                    : available().rows();
        }

        private void setPages(int pages) {
            // Editing pages must not persist a server-imposed hotbar cap as a personal preference.
            QuadHotbarConfig.setInventoryPages(Math.min(pages, maxPages()));
        }

        private static boolean server() {
            return QuadHotbarClientEvents.hasModdedServer();
        }

        private static boolean freedomAllowed() {
            return !server() || QuadHotbarClientEvents.serverPolicy().allowFreedom();
        }

        private static boolean freedom() {
            return QuadHotbarConfig.experimentsEnabled && freedomAllowed();
        }

        private static boolean overviewAllowed() {
            return QuadHotbarConfig.overviewEnabled && overviewPermitted();
        }

        private static boolean overviewPermitted() {
            return freedom()
                    && available().pages() > 1
                    && (!server() || QuadHotbarClientEvents.serverPolicy().allowPageOverview());
        }

        private static int maxPages() {
            return server()
                    ? QuadHotbarClientEvents.serverPolicy().maxInventoryPages()
                    : QuadHotbarPages.MAX_PAGES;
        }

        private static int maxRows() {
            int rows = QuadHotbarConfig.experimentsEnabled ? QuadHotbarPages.MAX_HOTBAR_ROWS : 4;
            if (server())
                rows = Math.min(rows, QuadHotbarClientEvents.serverPolicy().maxHotbarRows());
            return QuadHotbarPages.limitSettings(maxPages(), rows, maxPages(), rows).rows();
        }

        private static QuadHotbarPages.PageSettings available() {
            return QuadHotbarPages.limitSettings(
                    QuadHotbarConfig.inventoryPages,
                    QuadHotbarConfig.hotbarRows,
                    maxPages(),
                    maxRows());
        }

        private static int maxHeight() {
            return server() ? QuadHotbarClientEvents.serverPolicy().maxOverviewHeight() : 50;
        }

        private static int maxWidth() {
            return QuadHotbarOverviewSizing.maxWidth();
        }

        private static void changeWidth(int direction) {
            int choices = maxWidth() / 9;
            int current = QuadHotbarOverviewSizing.effectiveWidth() / 9 - 1;
            QuadHotbarConfig.setOverviewWidth(
                    (Math.floorMod(current + direction, choices) + 1) * 9);
        }

        private static boolean customPosition() {
            return QuadHotbarConfig.overviewX >= 0 || QuadHotbarConfig.overviewY >= 0;
        }

        @Override
        public boolean mouseClicked(double x, double y, int b) {
            for (Setting s : settings)
                if (s.field != null && s.field.isFocused() && !s.field.isMouseOver(x, y)) {
                    commitInputs();
                    save();
                    break;
                }
            if (b == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                for (Setting s : settings)
                    if (s.value != null
                            && s.value.visible
                            && s.value.active
                            && s.value.isMouseOver(x, y)) {
                        apply(() -> s.change.accept(-1));
                        return true;
                    }
            return super.mouseClicked(x, y, b);
        }

        @Override
        public boolean mouseScrolled(double x, double y, double h, double v) {
            commitInputs();
            save();
            scroll -= v * 26;
            refresh();
            return true;
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key == GLFW.GLFW_KEY_ENTER
                    || key == GLFW.GLFW_KEY_KP_ENTER
                    || key == GLFW.GLFW_KEY_TAB) {
                if (settings.stream().anyMatch(s -> s.field != null && s.field.isFocused())) {
                    commitInputs();
                    save();
                    if (key != GLFW.GLFW_KEY_TAB) return true;
                }
            }
            if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
                commitInputs();
                save();
                scroll += (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * (height - 74);
                refresh();
                return true;
            }
            return super.keyPressed(key, scanCode, modifiers);
        }

        @Override
        public void onClose() {
            commitInputs();
            save();
            minecraft.setScreen(parent);
        }

        @Override
        public void render(GuiGraphics graphics, int x, int y, float tick) {
            renderBackground(graphics, x, y, tick);
            refresh();
            QuadHotbarVanillaUi.optionsList(graphics, width, 36, height - 34, (int) scroll);
            graphics.enableScissor(0, 36, width, height - 34);
            for (Section s : sections) {
                int rowY = 40 + s.y - (int) scroll;
                graphics.drawString(
                        font,
                        Component.translatable(s.key),
                        labelX,
                        rowY + 4,
                        s.allowed.getAsBoolean() ? 0xFFFFFF : 0x999999);
                graphics.hLine(labelX, resetX + 46, rowY + 19, 0xFF808080);
            }
            for (Setting s : settings) {
                int rowY = 40 + s.y - (int) scroll;
                if (rowY < 36 || rowY + 20 > height - 34) continue;
                graphics.drawString(
                        font,
                        font.plainSubstrByWidth(
                                Component.translatable(s.key).getString(), labelWidth),
                        labelX,
                        rowY + 6,
                        s.allowed.getAsBoolean() ? 0xFFFFFF : 0x999999,
                        true);
                for (AbstractWidget widget : s.widgets) widget.render(graphics, x, y, tick);
            }
            graphics.disableScissor();
            if (contentHeight > height - 74) {
                int trackHeight = height - 74,
                        knobHeight = Math.max(12, trackHeight * trackHeight / contentHeight);
                int knobY =
                        38
                                + (int)
                                        (scroll
                                                / Math.max(1, contentHeight - trackHeight)
                                                * (trackHeight - knobHeight));
                QuadHotbarVanillaUi.scrollbar(
                        graphics, width - 8, 38, trackHeight, knobY, knobHeight);
            }
            graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
            done.render(graphics, x, y, tick);
            int color = QuadHotbarFade.color(warningUntil, 0xFF5555);
            if (QuadHotbarConfig.experimentsEnabled && color != 0) {
                var lines =
                        font.split(
                                Component.translatable("quadhotbar.config.experiments.warning"),
                                width - 32);
                int warningY = height - 37 - lines.size() * 10;
                graphics.fill(
                        8, warningY - 3, width - 8, height - 34, (color & 0xFF000000) | 0x151515);
                for (int i = 0; i < lines.size(); i++)
                    graphics.drawCenteredString(
                            font, lines.get(i), width / 2, warningY + i * 10, color);
            }
        }

        private record Section(String key, int y, BooleanSupplier allowed) {}

        private static final class Setting {
            final String key;
            final int y;
            final BooleanSupplier allowed, isDefault;
            final List<AbstractWidget> widgets = new ArrayList<>();
            Button value, reset, previous, next;
            EditBox field;
            String displayedInput;
            Supplier<Component> message;
            IntConsumer change, set;
            IntSupplier get, minimum, maximum;

            Setting(String key, int y, BooleanSupplier allowed, BooleanSupplier isDefault) {
                this.key = key;
                this.y = y;
                this.allowed = allowed;
                this.isDefault = isDefault;
            }
        }
    }
}
