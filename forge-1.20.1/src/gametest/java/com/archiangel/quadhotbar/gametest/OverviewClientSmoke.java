package com.archiangel.quadhotbar.gametest;

import com.archiangel.quadhotbar.QuadHotbarConfig;
import com.archiangel.quadhotbar.QuadHotbarContainerOverview;
import com.archiangel.quadhotbar.QuadHotbarNetwork;
import com.archiangel.quadhotbar.QuadHotbarOverviewNetwork;
import com.archiangel.quadhotbar.QuadHotbarPages;
import com.archiangel.quadhotbar.QuadHotbarServerConfig;
import com.archiangel.quadhotbar.client.QuadHotbarClientEvents;
import com.archiangel.quadhotbar.client.QuadHotbarConfigScreen;
import com.archiangel.quadhotbar.client.QuadHotbarKeyMappings;
import com.archiangel.quadhotbar.client.QuadHotbarOverviewScreen;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.controls.KeyBindsList;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.nio.file.Files;

/** Opt-in real-client regression run; excluded from published jars. */
@EventBusSubscriber(modid = "quadhotbar", value = Dist.CLIENT)
public final class OverviewClientSmoke {
    private static void requireSharedPreferences() {
        int rows = QuadHotbarConfig.hotbarRows, pages = QuadHotbarConfig.inventoryPages;
        int x = QuadHotbarConfig.overviewX, y = QuadHotbarConfig.overviewY;
        int wheelStep = QuadHotbarConfig.overviewScrollStep;
        boolean freedom = QuadHotbarConfig.experimentsEnabled, enabled = QuadHotbarConfig.enabled;
        var corner = QuadHotbarConfig.mainHotbarCorner;
        for (boolean remote : new boolean[] {true, false, true, false}) {
            QuadHotbarConfig.setMultiplayerContext(remote);
            require(
                    QuadHotbarConfig.multiplayerContext() == remote
                            && QuadHotbarConfig.hotbarRows == rows
                            && QuadHotbarConfig.inventoryPages == pages
                            && QuadHotbarConfig.overviewX == x
                            && QuadHotbarConfig.overviewY == y
                            && QuadHotbarConfig.overviewScrollStep == wheelStep
                            && QuadHotbarConfig.experimentsEnabled == freedom
                            && QuadHotbarConfig.enabled == enabled
                            && QuadHotbarConfig.mainHotbarCorner == corner,
                    "Connection context replaced personal preferences");
        }
        System.out.println("QuadHotbar shared personal preferences PASSED");
    }

    private static void requireTemporaryServerLimits() throws Exception {
        var type =
                Class.forName(
                        "com.archiangel.quadhotbar.client.QuadHotbarConfigScreen$GeneralSettingsScreen");
        var available = type.getDeclaredMethod("available");
        available.setAccessible(true);
        var original = QuadHotbarClientEvents.serverPolicy();
        int rows = QuadHotbarConfig.hotbarRows, pages = QuadHotbarConfig.inventoryPages;
        boolean freedom = QuadHotbarConfig.experimentsEnabled;
        try {
            QuadHotbarConfig.setExperimentsEnabled(true);
            QuadHotbarConfig.setHotbarRows(8);
            QuadHotbarConfig.setInventoryPages(4);
            QuadHotbarConfig.setMultiplayerContext(true);
            QuadHotbarClientEvents.setServerPolicy(
                    new com.archiangel.quadhotbar.QuadHotbarPolicy(
                            true,
                            true,
                            true,
                            true,
                            QuadHotbarServerConfig.OverviewLayout.CLIENT,
                            2,
                            4,
                            9,
                            5));
            var effective = (QuadHotbarPages.PageSettings) available.invoke(null);
            require(
                    effective.rows() == 4 && effective.pages() == 2,
                    "Server caps were not applied to effective settings");
            QuadHotbarConfig.save();
            var reload = QuadHotbarConfig.class.getDeclaredMethod("loadPreferences");
            reload.setAccessible(true);
            reload.invoke(null);
            require(
                    QuadHotbarConfig.hotbarRows == 8
                            && QuadHotbarConfig.inventoryPages == 4
                            && QuadHotbarConfig.experimentsEnabled,
                    "Saving or reloading under server caps changed personal preferences");

            var constructor =
                    type.getDeclaredConstructor(net.minecraft.client.gui.screens.Screen.class);
            constructor.setAccessible(true);
            var settingsScreen =
                    (net.minecraft.client.gui.screens.Screen)
                            constructor.newInstance((Object) null);
            var mc = Minecraft.getInstance();
            settingsScreen.init(
                    mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            var settingsField = type.getDeclaredField("settings");
            settingsField.setAccessible(true);
            for (Object setting : (java.util.List<?>) settingsField.get(settingsScreen)) {
                var keyField = setting.getClass().getDeclaredField("key");
                keyField.setAccessible(true);
                if (!keyField.get(setting).equals("quadhotbar.config.inventory_pages.description"))
                    continue;
                var field = setting.getClass().getDeclaredField("field");
                field.setAccessible(true);
                ((net.minecraft.client.gui.components.EditBox) field.get(setting)).setFocused(true);
            }
            var commit = type.getDeclaredMethod("commitInputs");
            commit.setAccessible(true);
            commit.invoke(settingsScreen);
            require(
                    QuadHotbarConfig.hotbarRows == 8 && QuadHotbarConfig.inventoryPages == 4,
                    "Focusing an unchanged field saved server caps as personal preferences");
            var setPages = type.getDeclaredMethod("setPages", int.class);
            setPages.setAccessible(true);
            setPages.invoke(constructor.newInstance((Object) null), 2);
            require(
                    QuadHotbarConfig.hotbarRows == 8,
                    "Editing page count persisted the server-imposed hotbar cap");
            QuadHotbarConfig.setInventoryPages(4);

            QuadHotbarClientEvents.setServerPolicy(
                    com.archiangel.quadhotbar.QuadHotbarPolicy.UNRESTRICTED);
            QuadHotbarConfig.setMultiplayerContext(false);
            effective = (QuadHotbarPages.PageSettings) available.invoke(null);
            require(
                    effective.rows() == 8 && effective.pages() == 4,
                    "Removing server restrictions did not restore personal settings");
            System.out.println("QuadHotbar temporary server limits PASSED");
        } finally {
            QuadHotbarClientEvents.setServerPolicy(original);
            QuadHotbarConfig.setMultiplayerContext(false);
            QuadHotbarConfig.setExperimentsEnabled(true);
            QuadHotbarConfig.setHotbarRows(rows);
            QuadHotbarConfig.setInventoryPages(pages);
            QuadHotbarConfig.setExperimentsEnabled(freedom);
            QuadHotbarConfig.save();
        }
    }

    private static int step, ticks;

    private static void requireOverviewSettingsDisabled(Minecraft mc) throws Exception {
        int height = QuadHotbarConfig.overviewHeight, wheel = QuadHotbarConfig.overviewScrollStep;
        boolean auto = QuadHotbarConfig.autoOpenOverview;
        var originalPolicy = QuadHotbarClientEvents.serverPolicy();
        QuadHotbarConfig.setOverviewEnabled(false);
        try {
            var type =
                    Class.forName(
                            "com.archiangel.quadhotbar.client.QuadHotbarConfigScreen$GeneralSettingsScreen");
            var constructor =
                    type.getDeclaredConstructor(net.minecraft.client.gui.screens.Screen.class);
            constructor.setAccessible(true);
            var screen =
                    (net.minecraft.client.gui.screens.Screen)
                            constructor.newInstance((Object) null);
            screen.init(
                    mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            var settings = type.getDeclaredField("settings");
            settings.setAccessible(true);
            int checked = 0;
            for (Object setting : (java.util.List<?>) settings.get(screen)) {
                var key = setting.getClass().getDeclaredField("key");
                key.setAccessible(true);
                String name = (String) key.get(setting);
                if (!name.startsWith("quadhotbar.overview.")) continue;
                var allowed = setting.getClass().getDeclaredField("allowed");
                allowed.setAccessible(true);
                boolean active =
                        ((java.util.function.BooleanSupplier) allowed.get(setting)).getAsBoolean();
                require(
                        active == name.equals("quadhotbar.overview.enabled"),
                        "Disabled overview has an active dependent setting: " + name);
                checked++;
            }
            require(checked >= 7, "Did not check all overview settings");
            QuadHotbarConfig.setOverviewEnabled(true);
            QuadHotbarClientEvents.setServerPolicy(
                    new com.archiangel.quadhotbar.QuadHotbarPolicy(
                            true,
                            true,
                            true,
                            true,
                            QuadHotbarServerConfig.OverviewLayout.CLIENT,
                            1,
                            4,
                            18,
                            50));
            var permitted = type.getDeclaredMethod("overviewPermitted");
            permitted.setAccessible(true);
            require(
                    !(boolean) permitted.invoke(null),
                    "A server-limited single page left overview settings enabled");
            var state = QuadHotbarPages.state(mc.player);
            int originalPages = state.pageCount;
            boolean originalBindings = QuadHotbarConfig.showAllKeybinds;
            try {
                state.pageCount = 1;
                QuadHotbarConfig.setShowAllKeybinds(false);
                require(
                        !com.archiangel.quadhotbar.client.QuadHotbarInventoryPageUi
                                .overviewAvailable(mc),
                        "Single-page inventory still allowed the overview");
                require(
                        !QuadHotbarKeyMappings.isVisibleInControls(
                                QuadHotbarKeyMappings.OPEN_OVERVIEW),
                        "Unavailable single-page overview binding remained visible");
            } finally {
                state.pageCount = originalPages;
                QuadHotbarConfig.setShowAllKeybinds(originalBindings);
            }
            require(
                    QuadHotbarConfig.overviewHeight == height
                            && QuadHotbarConfig.overviewScrollStep == wheel
                            && QuadHotbarConfig.autoOpenOverview == auto,
                    "Disabling the overview erased its preferences");
            System.out.println("QuadHotbar disabled overview settings PASSED");
        } finally {
            QuadHotbarClientEvents.setServerPolicy(originalPolicy);
            QuadHotbarConfig.setOverviewEnabled(true);
            QuadHotbarConfig.save();
        }
    }

    private static ItemStack picked = ItemStack.EMPTY;
    private static double cursorX, cursorY;
    private static ForgeConfigSpec.IntValue serverRows;
    private static int originalServerRows;
    private static InputConstants.Key originalSlotKey;
    private static net.minecraft.world.SimpleContainer smokeChest, smokeFurnace;
    private static Object smokeDeath;
    private static int corpseDepositSlot;
    private static org.lwjgl.glfw.GLFWCursorPosCallback originalCursorCallback;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (event.phase != TickEvent.Phase.END) return;
        if (!Boolean.getBoolean("quadhotbar.overviewSmoke")) return;
        Minecraft mc = Minecraft.getInstance();
        if (++ticks > 2400)
            throw new IllegalStateException("Overview smoke timed out at step " + step);
        if (step == 0
                && mc.screen instanceof TitleScreen
                && mc.getOverlay() == null
                && ticks > 40) {
            if (Boolean.getBoolean("quadhotbar.overviewRestartCheck")) {
                var expected =
                        Files.readAllLines(
                                mc.gameDirectory.toPath().resolve("overview-smoke-position.txt"));
                require(
                        expected.size() == 5
                                && QuadHotbarConfig.overviewX == Integer.parseInt(expected.get(0))
                                && QuadHotbarConfig.overviewY == Integer.parseInt(expected.get(1))
                                && QuadHotbarConfig.overviewScrollStep
                                        == Integer.parseInt(expected.get(2))
                                && QuadHotbarConfig.hotbarRows == Integer.parseInt(expected.get(3))
                                && QuadHotbarConfig.inventoryPages
                                        == Integer.parseInt(expected.get(4))
                                && !QuadHotbarConfig.experimentsEnabled,
                        "Game restart lost persisted overview settings");
            }
            mc.options.guiScale().set(1);
            requireSharedPreferences();
            mc.resizeDisplay();
            step = 1;
            ticks = 0;
            mc.createWorldOpenFlows()
                    .createFreshLevel(
                            "QuadHotbar-UI-Smoke-" + System.currentTimeMillis(),
                            new LevelSettings(
                                    "UI regression",
                                    GameType.CREATIVE,
                                    false,
                                    Difficulty.PEACEFUL,
                                    true,
                                    new GameRules(),
                                    WorldDataConfiguration.DEFAULT),
                            new WorldOptions(1234, false, false),
                            access ->
                                    access.registryOrThrow(Registries.WORLD_PRESET)
                                            .getHolderOrThrow(WorldPresets.FLAT)
                                            .value()
                                            .createWorldDimensions());
        } else if (step == 1
                && mc.player != null
                && mc.player.isAlive()
                && QuadHotbarClientEvents.hasModdedServer()
                && ticks > 80) {
            QuadHotbarConfig.setEnabled(true);
            // Automated clicks supply explicit Minecraft mouse moves. Ignore physical
            // pointer movement in this disposable dev window between assertions; the
            // user's other applications and the released mod are unaffected.
            originalCursorCallback =
                    org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback(mc.getWindow().getWindow(), null);
            // This disposable test world needs eight rows, even if the development
            // environment retained a six-row cap from an older beta. Do not save it.
            var policy = QuadHotbarServerConfig.class.getDeclaredField("MAX_HOTBAR_ROWS");
            policy.setAccessible(true);
            serverRows = (ForgeConfigSpec.IntValue) policy.get(null);
            originalServerRows = serverRows.get();
            serverRows.set(8);
            serverRows.clearCache();
            QuadHotbarConfig.setExperimentsEnabled(true);
            requireHighRowSelection();
            requireOverviewWidthSettings(mc);
            QuadHotbarConfig.setHotbarRows(6);
            QuadHotbarConfig.setInventoryPages(3);
            QuadHotbarConfig.setShowPageButtons(true);
            QuadHotbarConfig.setAlwaysShowPageLabels(false);
            QuadHotbarConfig.setStandaloneOverview(false);
            QuadHotbarConfig.setOverviewPosition(-1, -1);
            QuadHotbarConfig.setOverviewHeight(9);
            QuadHotbarConfig.setOverviewScrollStep(0);
            require(QuadHotbarConfig.overviewScrollStep == 1, "Scroll step minimum not enforced");
            QuadHotbarConfig.setOverviewScrollStep(1000);
            require(
                    QuadHotbarConfig.overviewScrollStep
                            == QuadHotbarConfig.MAX_OVERVIEW_SCROLL_STEP,
                    "Scroll step maximum not enforced");
            QuadHotbarConfig.setOverviewScrollStep(QuadHotbarConfig.DEFAULT_OVERVIEW_SCROLL_STEP);
            QuadHotbarConfig.setOverviewEnabled(true);
            QuadHotbarConfig.setAutoOpenOverview(true);
            QuadHotbarConfig.setOverviewLastOpen(true);
            // Persist the fixture just as the real config screen does, before the file watcher
            // can reload the earlier policy-regression snapshot over these test preferences.
            QuadHotbarConfig.save();
            QuadHotbarClientEvents.syncPageSettings();
            step = 2;
            ticks = 0;
        } else if (step == 2 && ticks > 30) {
            mc.setScreen(new InventoryScreen(mc.player));
            step = 3;
            ticks = 0;
        } else if (step == 3
                && mc.screen instanceof QuadHotbarOverviewScreen screen
                && ticks > 20) {
            var field = QuadHotbarOverviewScreen.class.getDeclaredField("vanillaScreen");
            field.setAccessible(true);
            var base = (AbstractContainerScreen<?>) field.get(screen);
            require(
                    base
                            instanceof
                            net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen,
                    "Creative UI replaced with imitation");
            require(
                    screen.getGuiLeft() >= base.getGuiLeft() + base.getXSize(),
                    "Overview not to the right");
            require(
                    screen.getGuiTop() == base.getGuiTop() - 28,
                    "Overview not aligned with creative tabs");
            grab(mc, "overview-creative.png");
            var hovered = base.getMenu().slots.get(8);
            moveMouse(
                    mc,
                    screen,
                    base.getGuiLeft() + hovered.x + 4,
                    base.getGuiTop() + hovered.y + 4);
            step = 30;
            ticks = 0;
        } else if (step == 30
                && ticks > 10
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            grab(mc, "overview-tooltip.png");
            var base = base(screen);
            var slot = base.getMenu().slots.get(0);
            screen.mouseClicked(base.getGuiLeft() + slot.x + 4, base.getGuiTop() + slot.y + 4, 0);
            screen.mouseReleased(base.getGuiLeft() + slot.x + 4, base.getGuiTop() + slot.y + 4, 0);
            picked = mc.player.inventoryMenu.getCarried().copy();
            require(!picked.isEmpty(), "Creative catalog cursor empty");
            arrow(screen, 1, 0);
            step = 31;
            ticks = 0;
        } else if (step == 31
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    screen.getMenu().activePage == 1
                            && ItemStack.matches(screen.getMenu().getCarried(), picked),
                    "Creative page arrow lost the carried item");
            arrow(screen, -1, 1);
            require(
                    ItemStack.matches(screen.getMenu().getCarried(), picked),
                    "Right-clicking an arrow dropped the cursor");
            screen.mouseScrolled(screen.getGuiLeft() + 20, screen.getGuiTop() + 60, -1);
            require(scroll(screen) == 0, "Wheel scroll jumped immediately instead of animating");
            step = 32;
            ticks = 0;
        } else if (step == 32
                && ticks > 3
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    scroll(screen) > 0 && scroll(screen) < 20,
                    "Scroll animation skipped intermediate positions");
            step = 33;
            ticks = 0;
        } else if (step == 33
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    Math.abs(scroll(screen) - 20) < 0.1
                            && ItemStack.matches(screen.getMenu().getCarried(), picked),
                    "Scrolling did not settle or changed the carried item");
            QuadHotbarConfig.setOverviewScrollStep(54);
            screen.mouseScrolled(screen.getGuiLeft() + 20, screen.getGuiTop() + 60, -1);
            step = 34;
            ticks = 0;
        } else if (step == 34
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    Math.abs(scroll(screen) - 74) < 0.1
                            && ItemStack.matches(screen.getMenu().getCarried(), picked),
                    "Configured wheel step was ignored or changed the cursor");
            QuadHotbarConfig.setOverviewScrollStep(QuadHotbarConfig.DEFAULT_OVERVIEW_SCROLL_STEP);
            int trackX = screen.getGuiLeft() + screen.getXSize() - 14;
            int trackBottom = screen.getGuiTop() + 28 + 9 * 18;
            screen.mouseClicked(trackX, trackBottom - 1, 0);
            screen.mouseDragged(trackX, trackBottom + 100, 0, 0, 101);
            screen.mouseReleased(trackX, trackBottom + 100, 0);
            require(
                    Math.abs(scroll(screen) - 138) < 0.1,
                    "Dragging beyond track did not clamp to the last page");
            var travel = QuadHotbarOverviewScreen.class.getDeclaredMethod("scrollbarTravel");
            travel.setAccessible(true);
            var handle = QuadHotbarOverviewScreen.class.getDeclaredMethod("scrollbarHeight");
            handle.setAccessible(true);
            int handleHeight = (int) handle.invoke(screen);
            require(
                    handleHeight == 15
                            && 1 + (int) travel.invoke(screen) + handleHeight == 9 * 18 - 1,
                    "Scrollbar is not vanilla-sized or does not touch the inside border");
            double beforeGrab = scroll(screen);
            int knobTop = screen.getGuiTop() + 28 + 1 + (int) travel.invoke(screen);
            screen.mouseClicked(trackX, knobTop + 5, 0);
            require(
                    Math.abs(scroll(screen) - beforeGrab) < 0.1,
                    "Grabbing the handle jumped to its center");
            screen.mouseReleased(trackX, knobTop + 5, 0);
            requireScrollbarGeometry(screen);
            step = 340;
            ticks = 0;
        } else if (step == 340
                && ticks > 3
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            requireScrollbarPixels(mc, screen);
            grab(mc, "overview-scroll-bottom.png");
            int trackX = screen.getGuiLeft() + screen.getXSize() - 14;
            screen.mouseClicked(trackX, screen.getGuiTop() + 28, 0);
            screen.mouseDragged(trackX, screen.getGuiTop() - 100, 0, 0, -128);
            screen.mouseReleased(trackX, screen.getGuiTop() - 100, 0);
            require(scroll(screen) == 0, "Dragging above the track did not clamp to its start");
            step = 341;
            ticks = 0;
        } else if (step == 341
                && ticks > 3
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            requireScrollbarPixels(mc, screen);
            grab(mc, "overview-scroll-top.png");
            int trackX = screen.getGuiLeft() + screen.getXSize() - 14;
            screen.mouseClicked(trackX, screen.getGuiTop() + 28 + 9 * 18 - 1, 0);
            screen.mouseDragged(trackX, screen.getGuiTop() + 500, 0, 0, 500);
            screen.mouseReleased(trackX, screen.getGuiTop() + 500, 0);
            screen.mouseScrolled(screen.getGuiLeft() + 20, screen.getGuiTop() + 60, 1);
            step = 35;
            ticks = 0;
        } else if (step == 35
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    Math.abs(scroll(screen) - 118) < 0.1,
                    "Default wheel step failed after dragging the scrollbar");
            var destination = screen.getMenu().slots.get(36 + 9);
            screen.mouseClicked(
                    screen.getGuiLeft() + destination.x + 4,
                    screen.getGuiTop() + destination.y + 4,
                    0);
            screen.mouseReleased(
                    screen.getGuiLeft() + destination.x + 4,
                    screen.getGuiTop() + destination.y + 4,
                    0);
            step = 4;
            ticks = 0;
        } else if (step == 4
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    ItemStack.matches(QuadHotbarPages.getPageItem(mc.player, 1, 9), picked),
                    "Creative item lost after server synchronization");
            int x = screen.getGuiLeft() + 12, y = screen.getGuiTop() + 10;
            screen.mouseClicked(x, y, 0);
            screen.mouseDragged(20, 40, 0, 20 - x, 40 - y);
            screen.mouseReleased(20, 40, 0);
            require(
                    QuadHotbarConfig.overviewX == screen.getGuiLeft()
                            && QuadHotbarConfig.overviewY == screen.getGuiTop(),
                    "Dragged position not saved");
            step = 5;
            ticks = 0;
        } else if (step == 5
                && ticks > 10
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            grab(mc, "overview-dragged.png");
            var slot = screen.getMenu().slots.get(36 + 9);
            click(screen, screen.getGuiLeft() + slot.x + 4, screen.getGuiTop() + slot.y + 4);
            require(
                    ItemStack.matches(screen.getMenu().getCarried(), picked),
                    "Could not pick up item before close");
            closeWithMouse(mc, screen);
            step = 6;
            ticks = 0;
        } else if (step == 6
                && ticks > 20
                && mc.screen
                        instanceof
                        net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
            requireCursorPosition(mc);
            require(
                    ItemStack.matches(mc.player.inventoryMenu.getCarried(), picked),
                    "Creative close button lost cursor stack");
            QuadHotbarOverviewNetwork.open();
            step = 7;
            ticks = 0;
        } else if (step == 7
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    screen.getGuiLeft() == QuadHotbarConfig.overviewX
                            && screen.getGuiTop() == QuadHotbarConfig.overviewY,
                    "Reopening lost position");
            require(
                    ItemStack.matches(screen.getMenu().getCarried(), picked),
                    "Opening overview lost creative cursor");
            var destination = screen.getMenu().slots.get(36 + 9);
            click(
                    screen,
                    screen.getGuiLeft() + destination.x + 4,
                    screen.getGuiTop() + destination.y + 4);
            screen.onClose();
            step = 8;
            ticks = 0;
        } else if (step == 8 && ticks > 20) {
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            server.execute(
                    () -> {
                        var player = server.getPlayerList().getPlayer(id);
                        QuadHotbarNetwork.turnInventoryPage(
                                player, -QuadHotbarPages.state(player).activePage);
                        player.setGameMode(GameType.SURVIVAL);
                        player.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 23));
                        player.inventoryMenu.broadcastFullState();
                    });
            QuadHotbarConfig.setOverviewPosition(-1, -1);
            step = 20;
            ticks = 0;
        } else if (step == 20 && ticks > 30 && !mc.player.getAbilities().instabuild) {
            mc.setScreen(new InventoryScreen(mc.player));
            step = 21;
            ticks = 0;
        } else if (step == 21
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            var field = QuadHotbarOverviewScreen.class.getDeclaredField("vanillaScreen");
            field.setAccessible(true);
            var base = (AbstractContainerScreen<?>) field.get(screen);
            require(base instanceof InventoryScreen, "Survival UI replaced with imitation");
            require(
                    screen.getGuiTop() == base.getGuiTop(),
                    "Overview not aligned with survival inventory");
            var slot = mc.player.inventoryMenu.slots.get(9);
            click(screen, base.getGuiLeft() + slot.x + 4, base.getGuiTop() + slot.y + 4);
            require(
                    screen.getMenu().getCarried().is(Items.DIAMOND)
                            && screen.getMenu().getCarried().getCount() == 23,
                    "Vanilla survival click did not route to overview server menu");
            arrow(screen, 1, 0);
            step = 41;
            ticks = 0;
        } else if (step == 41
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    screen.getMenu().activePage == 1
                            && screen.getMenu().getCarried().getCount() == 23,
                    "Survival page arrow: page="
                            + screen.getMenu().activePage
                            + ", cursor="
                            + screen.getMenu().getCarried()
                            + ", base="
                            + base(screen).getGuiLeft()
                            + ", panel="
                            + screen.getGuiLeft());
            arrow(screen, -1, 1);
            var dest = screen.getMenu().slots.get(36 + 10);
            click(screen, screen.getGuiLeft() + dest.x + 4, screen.getGuiTop() + dest.y + 4);
            step = 22;
            ticks = 0;
        } else if (step == 22
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    QuadHotbarPages.getPageItem(mc.player, 1, 10).getCount() == 23
                            && screen.getMenu().getCarried().isEmpty(),
                    "Survival transfer was rolled back by server");
            var field = QuadHotbarOverviewScreen.class.getDeclaredField("vanillaScreen");
            field.setAccessible(true);
            var base = (AbstractContainerScreen<?>) field.get(screen);
            int x = screen.getGuiLeft() + 12, y = screen.getGuiTop() + 10;
            screen.mouseClicked(x, y, 0);
            screen.mouseDragged(base.getGuiLeft() + 24, base.getGuiTop() + 20, 0, 0, 0);
            screen.mouseReleased(base.getGuiLeft() + 24, base.getGuiTop() + 20, 0);
            step = 23;
            ticks = 0;
        } else if (step == 23
                && ticks > 10
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            grab(mc, "overview-survival-overlap.png");
            var slot = screen.getMenu().slots.get(36 + 10);
            click(screen, screen.getGuiLeft() + slot.x + 4, screen.getGuiTop() + slot.y + 4);
            require(
                    screen.getMenu().getCarried().getCount() == 23,
                    "Overlay click went to obscured vanilla inventory");
            var dest = screen.getMenu().slots.get(9);
            click(screen, screen.getGuiLeft() + dest.x + 4, screen.getGuiTop() + dest.y + 4);
            step = 24;
            ticks = 0;
        } else if (step == 24
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    QuadHotbarPages.getPageItem(mc.player, 0, 9).getCount() == 23
                            && QuadHotbarPages.getPageItem(mc.player, 1, 10).isEmpty(),
                    "Overlap transfer lost or duplicated items");
            // The recipe-book request must still work with the overview's container ID.
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            server.execute(
                    () -> {
                        var player = server.getPlayerList().getPlayer(id);
                        player.getInventory().setItem(10, new ItemStack(Items.OAK_LOG, 2));
                        var recipe =
                                server.getRecipeManager()
                                        .byKey(
                                                net.minecraft.resources.ResourceLocation
                                                        .withDefaultNamespace("oak_planks"))
                                        .orElseThrow();
                        player.awardRecipes(java.util.List.of(recipe));
                        player.inventoryMenu.broadcastFullState();
                    });
            step = 25;
            ticks = 0;
        } else if (step == 25
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            var recipe =
                    mc.getConnection()
                            .getRecipeManager()
                            .byKey(
                                    net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                                            "oak_planks"))
                            .orElseThrow();
            mc.gameMode.handlePlaceRecipe(screen.getMenu().containerId, recipe, false);
            step = 26;
            ticks = 0;
        } else if (step == 26
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    mc.player.inventoryMenu.getCraftSlots().getItem(0).is(Items.OAK_LOG)
                            && mc.player.inventoryMenu.slots.get(0).getItem().is(Items.OAK_PLANKS),
                    "Recipe book failed with docked overview");
            var slot = screen.getMenu().slots.get(9);
            click(screen, screen.getGuiLeft() + slot.x + 4, screen.getGuiTop() + slot.y + 4);
            require(
                    screen.getMenu().getCarried().is(Items.DIAMOND)
                            && screen.getMenu().getCarried().getCount() == 23,
                    "Could not pick up survival stack before closing");
            closeWithMouse(mc, screen);
            step = 27;
            ticks = 0;
        } else if (step == 27 && ticks > 20 && mc.screen instanceof InventoryScreen) {
            requireCursorPosition(mc);
            require(
                    mc.player.inventoryMenu.getCarried().is(Items.DIAMOND)
                            && mc.player.inventoryMenu.getCarried().getCount() == 23,
                    "Survival close button lost cursor stack");
            var base = (InventoryScreen) mc.screen;
            base.mouseClicked(base.getGuiLeft() + 189, base.getGuiTop() + 124, 0);
            base.mouseReleased(base.getGuiLeft() + 189, base.getGuiTop() + 124, 0);
            step = 51;
            ticks = 0;
        } else if (step == 51
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    screen.getMenu().getCarried().getCount() == 23,
                    "Overview button dropped survival cursor stack");
            var slot = screen.getMenu().slots.get(36 + 11);
            click(screen, screen.getGuiLeft() + slot.x + 4, screen.getGuiTop() + slot.y + 4);
            step = 52;
            ticks = 0;
        } else if (step == 52
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    screen.getMenu().getCarried().isEmpty()
                            && QuadHotbarPages.getPageItem(mc.player, 1, 11).getCount() == 23
                            && QuadHotbarPages.getPageItem(mc.player, 0, 9).isEmpty(),
                    "Cursor transfer lost or duplicated diamonds");
            screen.returnToInventoryWithCursor();
            step = 53;
            ticks = 0;
        } else if (step == 53 && ticks > 20 && mc.screen instanceof InventoryScreen) {
            QuadHotbarConfig.setHotbarRows(8);
            QuadHotbarConfig.setInventoryPages(4);
            QuadHotbarClientEvents.syncPageSettings();
            mc.setScreen(null);
            step = 54;
            ticks = 0;
        } else if (step == 54 && ticks > 20) {
            var state = QuadHotbarPages.state(mc.player);
            require(state.hotbarRows == 8 && state.pageCount == 4, "Server rejected eight hotbars");
            require(QuadHotbarKeyMappings.HOTBAR_SLOTS.length == 63, "Missing slot 55-72 keybinds");
            QuadHotbarNetwork.requestSlot(7, 8, 123);
            step = 55;
            ticks = 0;
        } else if (step == 55 && ticks > 20) {
            require(
                    QuadHotbarPages.state(mc.player).activePage == 1
                            && mc.player.getInventory().selected == 35,
                    "Eighth hotbar selected the wrong server slot");
            grab(mc, "hud-eight-hotbars.png");
            requireOverviewSettingsDisabled(mc);
            QuadHotbarConfig.setOverviewLastOpen(true);
            QuadHotbarConfig.save();
            mc.setScreen(new InventoryScreen(mc.player));
            step = 56;
            ticks = 0;
        } else if (step == 56
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    base(screen) instanceof InventoryScreen,
                    "Survival auto-open replaced vanilla inventory");
            screen.returnToInventoryWithCursor();
            step = 57;
            ticks = 0;
        } else if (step == 57 && ticks > 20 && mc.screen instanceof InventoryScreen) {
            require(
                    QuadHotbarConfig.autoOpenOverview && !QuadHotbarConfig.overviewLastOpen,
                    "Closing only the panel did not pause auto-open");
            var reload = QuadHotbarConfig.class.getDeclaredMethod("loadPreferences");
            reload.setAccessible(true);
            reload.invoke(null);
            require(!QuadHotbarConfig.overviewLastOpen, "Closed overview state was not saved");
            mc.setScreen(null);
            mc.setScreen(new InventoryScreen(mc.player));
            step = 571;
            ticks = 0;
        } else if (step == 571 && ticks > 20) {
            require(
                    mc.screen instanceof InventoryScreen,
                    "Closed panel auto-opened on the next inventory opening");
            com.archiangel.quadhotbar.client.QuadHotbarInventoryPageUi.openOverview();
            step = 572;
            ticks = 0;
        } else if (step == 572
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(QuadHotbarConfig.overviewLastOpen, "Manual opening did not resume auto-open");
            // A remapped inventory key wins even when the pages binding uses the same key.
            var originalInventoryKey = mc.options.keyInventory.getKey();
            var originalOverviewKey = QuadHotbarKeyMappings.OPEN_OVERVIEW.getKey();
            try {
                var key = InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_F15);
                mc.options.keyInventory.setKey(key);
                QuadHotbarKeyMappings.OPEN_OVERVIEW.setKey(key);
                require(
                        screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F15, 0, 0),
                        "Remapped inventory key was not handled");
                require(
                        mc.screen == null && QuadHotbarConfig.overviewLastOpen,
                        "Inventory key closed only the panel or paused auto-open");
            } finally {
                mc.options.keyInventory.setKey(originalInventoryKey);
                QuadHotbarKeyMappings.OPEN_OVERVIEW.setKey(originalOverviewKey);
                KeyMapping.resetMapping();
            }
            mc.setScreen(new InventoryScreen(mc.player));
            step = 573;
            ticks = 0;
        } else if (step == 573
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    QuadHotbarConfig.overviewLastOpen,
                    "Closing the whole inventory incorrectly paused auto-open");
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(
                    mc.screen == null && QuadHotbarConfig.overviewLastOpen,
                    "Esc changed the remembered panel state");
            mc.setScreen(new InventoryScreen(mc.player));
            step = 575;
            ticks = 0;
        } else if (step == 575
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            screen.returnToInventoryWithCursor();
            step = 574;
            ticks = 0;
        } else if (step == 574 && ticks > 20 && mc.screen instanceof InventoryScreen) {
            QuadHotbarConfig.setAutoOpenOverview(false);
            mc.setScreen(null);
            mc.setScreen(new InventoryScreen(mc.player));
            step = 58;
            ticks = 0;
        } else if (step == 58 && ticks > 20) {
            require(
                    mc.screen instanceof InventoryScreen,
                    "Disabled overview auto-open still opened a panel");
            QuadHotbarConfig.setOverviewPosition(-1, -1);
            mc.setScreen(null);
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            server.execute(
                    () -> {
                        var player = server.getPlayerList().getPlayer(id);
                        smokeChest = new net.minecraft.world.SimpleContainer(27);
                        smokeChest.setItem(0, new ItemStack(Items.DIAMOND, 3));
                        player.openMenu(
                                new net.minecraft.world.SimpleMenuProvider(
                                        (container, inventory, owner) ->
                                                net.minecraft.world.inventory.ChestMenu.threeRows(
                                                        container, inventory, smokeChest),
                                        net.minecraft.network.chat.Component.literal(
                                                "Chest regression")));
                    });
            step = 80;
            ticks = 0;
        } else if (step == 80
                && ticks > 20
                && mc.screen instanceof AbstractContainerScreen<?> screen) {
            require(
                    screen.getMenu() instanceof net.minecraft.world.inventory.ChestMenu,
                    "Chest did not open");
            var slot = screen.getMenu().slots.get(0);
            screen.mouseClicked(
                    screen.getGuiLeft() + slot.x + 4, screen.getGuiTop() + slot.y + 4, 0);
            screen.mouseReleased(
                    screen.getGuiLeft() + slot.x + 4, screen.getGuiTop() + slot.y + 4, 0);
            step = 81;
            ticks = 0;
        } else if (step == 81
                && ticks > 20
                && mc.screen instanceof AbstractContainerScreen<?> screen) {
            require(screen.getMenu().getCarried().getCount() == 3, "Chest cursor is empty");
            var grid =
                    screen.children().stream()
                            .filter(
                                    child ->
                                            child
                                                    instanceof
                                                    com.archiangel.quadhotbar.client
                                                            .QuadHotbarControlButton)
                            .map(
                                    child ->
                                            (com.archiangel.quadhotbar.client
                                                            .QuadHotbarControlButton)
                                                    child)
                            .findFirst()
                            .orElseThrow();
            require(grid.visible, "Generic chest overview button is hidden");
            screen.mouseClicked(grid.getX() + 8, grid.getY() + 8, 0);
            screen.mouseReleased(grid.getX() + 8, grid.getY() + 8, 0);
            step = 82;
            ticks = 0;
        } else if (step == 82
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    screen.getMenu().external
                            && mc.player.containerMenu == base(screen).getMenu()
                            && mc.player.containerMenu
                                    instanceof net.minecraft.world.inventory.ChestMenu
                            && screen.getMenu().getCarried().getCount() == 3,
                    "Overview replaced the real chest or lost its carried item");
            grab(mc, "overview-chest.png");
            var slot = screen.getMenu().slots.get(20);
            click(screen, screen.getGuiLeft() + slot.x + 4, screen.getGuiTop() + slot.y + 4);
            step = 83;
            ticks = 0;
        } else if (step == 83
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    screen.getMenu().slots.get(20).getItem().getCount() == 3
                            && screen.getMenu().getCarried().isEmpty(),
                    "Chest-to-page transfer failed");
            var slot = screen.getMenu().slots.get(20);
            click(screen, screen.getGuiLeft() + slot.x + 4, screen.getGuiTop() + slot.y + 4);
            step = 84;
            ticks = 0;
        } else if (step == 84
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(screen.getMenu().getCarried().getCount() == 3, "Page-to-chest pickup failed");
            var slot = base(screen).getMenu().slots.get(1);
            click(
                    screen,
                    base(screen).getGuiLeft() + slot.x + 4,
                    base(screen).getGuiTop() + slot.y + 4);
            step = 85;
            ticks = 0;
        } else if (step == 85
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    smokeChest.getItem(1).getCount() == 3
                            && screen.getMenu().slots.get(20).getItem().isEmpty()
                            && screen.getMenu().getCarried().isEmpty(),
                    "Page transfer duplicated or lost diamonds");
            closeWithMouse(mc, screen);
            step = 86;
            ticks = 0;
        } else if (step == 86
                && ticks > 20
                && mc.screen instanceof AbstractContainerScreen<?> screen) {
            require(
                    screen.getMenu() instanceof net.minecraft.world.inventory.ChestMenu,
                    "Close did not return to the original chest");
            requireCursorPosition(mc);
            screen.onClose();
            step = 87;
            ticks = 0;
        } else if (step == 87 && ticks > 20) {
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            server.execute(
                    () -> {
                        var player = server.getPlayerList().getPlayer(id);
                        QuadHotbarPages.setPageItem(player, 0, 30, new ItemStack(Items.COAL, 6));
                        smokeFurnace = new net.minecraft.world.SimpleContainer(3);
                        player.openMenu(
                                new net.minecraft.world.SimpleMenuProvider(
                                        (container, inventory, owner) ->
                                                new net.minecraft.world.inventory.FurnaceMenu(
                                                        container,
                                                        inventory,
                                                        smokeFurnace,
                                                        new net.minecraft.world.inventory
                                                                .SimpleContainerData(4)),
                                        net.minecraft.network.chat.Component.literal(
                                                "Furnace regression")));
                    });
            step = 88;
            ticks = 0;
        } else if (step == 88
                && ticks > 20
                && mc.screen instanceof AbstractContainerScreen<?> screen) {
            require(
                    screen.getMenu() instanceof net.minecraft.world.inventory.FurnaceMenu,
                    "Furnace did not open");
            var original = QuadHotbarKeyMappings.OPEN_OVERVIEW.getKey();
            QuadHotbarKeyMappings.OPEN_OVERVIEW.setKey(
                    InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_F14));
            require(
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F14, 0, 0),
                    "Overview keybind did not work in a furnace GUI");
            QuadHotbarKeyMappings.OPEN_OVERVIEW.setKey(original);
            KeyMapping.resetMapping();
            step = 89;
            ticks = 0;
        } else if (step == 89
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    screen.getMenu().external
                            && base(screen).getMenu()
                                    instanceof net.minecraft.world.inventory.FurnaceMenu,
                    "Furnace was replaced with the player inventory");
            grab(mc, "overview-furnace.png");
            QuadHotbarContainerOverview.click(
                    screen.getMenu().containerId,
                    screen.externalToken(),
                    30,
                    0,
                    net.minecraft.world.inventory.ClickType.QUICK_MOVE);
            step = 90;
            ticks = 0;
        } else if (step == 90
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    smokeFurnace.getItem(1).is(Items.COAL)
                            && smokeFurnace.getItem(1).getCount() == 6
                            && smokeFurnace.getItem(2).isEmpty()
                            && screen.getMenu().slots.get(30).getItem().isEmpty(),
                    "Furnace shift-transfer bypassed its fuel/result rules");
            closeWithMouse(mc, screen);
            step = 91;
            ticks = 0;
        } else if (step == 91
                && ticks > 20
                && mc.screen instanceof AbstractContainerScreen<?> screen) {
            require(
                    screen.getMenu() instanceof net.minecraft.world.inventory.FurnaceMenu,
                    "Close button did not return to the furnace");
            screen.onClose();
            if (net.minecraftforge.fml.ModList.get().isLoaded("corpse")) {
                var server = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                server.execute(
                        () -> {
                            try {
                                var player = server.getPlayerList().getPlayer(id);
                                var donor =
                                        new net.minecraftforge.common.util.FakePlayer(
                                                player.serverLevel(),
                                                new com.mojang.authlib.GameProfile(
                                                        java.util.UUID.randomUUID(),
                                                        "corpse-ui-test"));
                                donor.getInventory().setItem(9, new ItemStack(Items.COAL, 1));
                                QuadHotbarPages.setPageItem(
                                        donor, 2, 17, new ItemStack(Items.DIAMOND, 13));
                                var deathType =
                                        Class.forName("de.maxhenkel.corpse.corelib.death.Death");
                                smokeDeath =
                                        deathType
                                                .getMethod(
                                                        "fromPlayer",
                                                        net.minecraft.world.entity.player.Player
                                                                .class)
                                                .invoke(null, donor);
                                donor.captureDrops(
                                        new java.util.ArrayList<
                                                net.minecraft.world.entity.item.ItemEntity>());
                                donor.getInventory().dropAll();
                                deathType
                                        .getMethod("processDrops", java.util.Collection.class)
                                        .invoke(smokeDeath, donor.captureDrops(null));
                                var corpseType =
                                        Class.forName("de.maxhenkel.corpse.entities.CorpseEntity");
                                var corpse =
                                        corpseType
                                                .getMethod(
                                                        "createFromDeath",
                                                        net.minecraft.world.entity.player.Player
                                                                .class,
                                                        deathType)
                                                .invoke(null, donor, smokeDeath);
                                // Native history transport supplies a real Corpse GUI without
                                // spawning
                                // an entity; explicit edit access lets the test exercise both
                                // cursors.
                                Class.forName("de.maxhenkel.corpse.gui.Guis")
                                        .getMethod(
                                                "openCorpseGUI",
                                                net.minecraft.server.level.ServerPlayer.class,
                                                corpseType,
                                                boolean.class,
                                                boolean.class)
                                        .invoke(null, player, corpse, true, true);
                            } catch (ReflectiveOperationException exception) {
                                throw new IllegalStateException(
                                        "Corpse UI fixture failed", exception);
                            }
                        });
                step = 92;
            } else prepareBindingChecks(mc);
            ticks = 0;
        } else if (step == 92
                && ticks > 20
                && mc.screen instanceof AbstractContainerScreen<?> screen) {
            require(
                    screen.getMenu().getClass().getName().startsWith("de.maxhenkel.corpse.gui."),
                    "Native Corpse inventory did not open");
            com.archiangel.quadhotbar.client.QuadHotbarInventoryPageUi.openOverview();
            step = 93;
            ticks = 0;
        } else if (step == 93
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    screen.getMenu().deathInventory
                            && screen.getMenu().pageCount == 3
                            && mc.player.containerMenu == base(screen).getMenu(),
                    "Corpse overview replaced its menu or showed the living player's pages");
            screen.mouseScrolled(screen.getGuiLeft() + 20, screen.getGuiTop() + 60, -100);
            step = 94;
            ticks = 0;
        } else if (step == 94
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            var slot = screen.getMenu().slots.get(2 * 36 + 17);
            require(
                    slot.isActive()
                            && slot.getItem().is(Items.DIAMOND)
                            && slot.getItem().getCount() == 13,
                    "Corpse page did not render its stored stack");
            grab(mc, "overview-corpse.png");
            click(screen, screen.getGuiLeft() + slot.x + 4, screen.getGuiTop() + slot.y + 4);
            step = 95;
            ticks = 0;
        } else if (step == 95
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    screen.getMenu().getCarried().is(Items.DIAMOND)
                            && screen.getMenu().getCarried().getCount() == 13,
                    "Corpse page cursor did not synchronize");
            @SuppressWarnings("unchecked")
            var remaining =
                    (java.util.List<ItemStack>)
                            smokeDeath
                                    .getClass()
                                    .getMethod("getAdditionalItems")
                                    .invoke(smokeDeath);
            require(
                    remaining.stream().allMatch(ItemStack::isEmpty),
                    "Corpse retained a second copy after looting");
            var parent = base(screen);
            var slot =
                    parent.getMenu().slots.stream()
                            .filter(
                                    candidate ->
                                            candidate.container == mc.player.getInventory()
                                                    && candidate.getItem().isEmpty()
                                                    && candidate.mayPlace(
                                                            screen.getMenu().getCarried()))
                            .findFirst()
                            .orElseThrow();
            corpseDepositSlot = slot.index;
            click(screen, parent.getGuiLeft() + slot.x + 4, parent.getGuiTop() + slot.y + 4);
            step = 96;
            ticks = 0;
        } else if (step == 96
                && ticks > 20
                && mc.screen instanceof QuadHotbarOverviewScreen screen) {
            require(
                    base(screen).getMenu().slots.get(corpseDepositSlot).getItem().getCount() == 13
                            && screen.getMenu().getCarried().isEmpty()
                            && screen.getMenu().slots.get(2 * 36 + 17).getItem().isEmpty(),
                    "Corpse-to-player cursor transfer lost or duplicated items");
            closeWithMouse(mc, screen);
            step = 97;
            ticks = 0;
        } else if (step == 97
                && ticks > 20
                && mc.screen instanceof AbstractContainerScreen<?> screen) {
            require(
                    screen.getMenu().getClass().getName().startsWith("de.maxhenkel.corpse.gui."),
                    "Closing the corpse panel did not return to its native inventory");
            screen.onClose();
            prepareBindingChecks(mc);
            ticks = 0;
        } else if (step == 70 && ticks > 20) {
            require(QuadHotbarClientEvents.getRows() == 2, "Two hotbars did not activate");
            requireControlsSlots(mc, 9);
            mc.options.save();
            mc.options.load();
            require(
                    QuadHotbarKeyMappings.HOTBAR_SLOTS[62].getKey().getValue()
                            == org.lwjgl.glfw.GLFW.GLFW_KEY_F13,
                    "Saving controls lost a hidden slot binding");
            for (var mapping : QuadHotbarKeyMappings.HOTBAR_SLOTS)
                require(
                        java.util.Arrays.asList(mc.options.keyMappings).contains(mapping),
                        "Controls filter removed a registered binding");
            QuadHotbarConfig.setShowAllKeybinds(true);
            requireControlsSlots(mc, 63);
            QuadHotbarConfig.setShowAllKeybinds(false);
            QuadHotbarConfig.setHotbarRows(8);
            QuadHotbarConfig.setInventoryPages(4);
            QuadHotbarConfig.setAutoOpenOverview(true);
            QuadHotbarConfig.setOverviewHeight(13);
            QuadHotbarConfig.setShowPageButtons(false);
            QuadHotbarConfig.setAlwaysShowPageLabels(true);
            QuadHotbarConfig.setExperimentsEnabled(false);
            requireFreedomPreferences();
            QuadHotbarClientEvents.syncPageSettings();
            mc.setScreen(new QuadHotbarConfigScreen(null));
            step = 71;
            ticks = 0;
        } else if (step == 71 && ticks > 20) {
            require(
                    QuadHotbarPages.state(mc.player).pageCount == 1
                            && QuadHotbarClientEvents.getRows() == 4,
                    "Dormant Freedom preferences affected active hotbars/pages");
            requireControlsSlots(mc, 27);
            require(
                    !QuadHotbarKeyMappings.isVisibleInControls(QuadHotbarKeyMappings.OPEN_OVERVIEW)
                            && !QuadHotbarKeyMappings.isVisibleInControls(
                                    QuadHotbarKeyMappings.NEXT_HOTBAR_PAGE),
                    "Dormant page bindings are visible");
            mc.setScreen(new QuadHotbarConfigScreen(null));
            mc.screen.mouseClicked(mc.screen.width / 2.0, 64, 0);
            step = 72;
            ticks = 0;
        } else if (step == 72 && ticks > 10) {
            var screen = mc.screen;
            var settingsField = screen.getClass().getDeclaredField("settings");
            settingsField.setAccessible(true);
            var settings = (java.util.List<?>) settingsField.get(screen);
            boolean pages = false;
            int disabled = 0;
            for (var setting : settings) {
                var key = setting.getClass().getDeclaredField("key");
                key.setAccessible(true);
                if (key.get(setting).equals("quadhotbar.config.inventory_pages.description"))
                    pages = true;
                if (!pages) continue;
                var allowed = setting.getClass().getDeclaredField("allowed");
                allowed.setAccessible(true);
                require(
                        !((java.util.function.BooleanSupplier) allowed.get(setting)).getAsBoolean(),
                        "Freedom-off page setting is active: " + key.get(setting));
                var widgets = setting.getClass().getDeclaredField("widgets");
                widgets.setAccessible(true);
                for (var widget : (java.util.List<?>) widgets.get(setting))
                    require(
                            !((net.minecraft.client.gui.components.AbstractWidget) widget).active,
                            "Freedom-off page widget is active: " + key.get(setting));
                disabled++;
            }
            require(disabled >= 12, "Page/overview settings were not checked");
            var displayed = screen.getClass().getDeclaredMethod("displayedRows");
            displayed.setAccessible(true);
            require(
                    (int) displayed.invoke(null) == 8,
                    "Config hides the saved eight-row preference");
            var change = screen.getClass().getDeclaredMethod("changeRows", int.class);
            change.setAccessible(true);
            for (int direction : new int[] {1, -1}) {
                change.invoke(screen, direction);
                require(
                        QuadHotbarConfig.hotbarRows == 4 && QuadHotbarConfig.inventoryPages == 4,
                        "First row edit must become four without rewriting pages");
                QuadHotbarConfig.setExperimentsEnabled(true);
                QuadHotbarConfig.setHotbarRows(8);
                QuadHotbarConfig.setExperimentsEnabled(false);
            }
            requireFreedomPreferences();
            grab(mc, "config-freedom-disabled.png");
            QuadHotbarConfig.setExperimentsEnabled(true);
            // Match the real settings button: persist before Forge's watcher can reload
            // the preceding disabled snapshot written by changeRows().
            QuadHotbarConfig.save();
            requireFreedomPreferences();
            QuadHotbarClientEvents.syncPageSettings();
            step = 73;
            ticks = 0;
        } else if (step == 73 && ticks > 20) {
            require(
                    QuadHotbarPages.state(mc.player).pageCount == 4
                            && QuadHotbarClientEvents.getRows() == 8,
                    "Re-enabling Freedom lost eight rows/four pages");
            requireControlsSlots(mc, 63);
            require(
                    QuadHotbarKeyMappings.HOTBAR_SLOTS[62].getKey().getValue()
                            == org.lwjgl.glfw.GLFW.GLFW_KEY_F13,
                    "Re-enabling Freedom lost the slot 72 binding");
            mc.options.setKey(QuadHotbarKeyMappings.HOTBAR_SLOTS[62], originalSlotKey);
            KeyMapping.resetMapping();
            mc.options.save();
            mc.setScreen(new QuadHotbarConfigScreen(null));
            step = 9;
            ticks = 0;
        } else if (step == 9 && ticks > 10) {
            mc.screen.mouseClicked(mc.screen.width / 2.0, 64, 0);
            step = 10;
            ticks = 0;
        } else if (step == 10 && ticks > 10) {
            grab(mc, "config-general.png");
            mc.screen.mouseScrolled(100, 100, -12);
            step = 11;
            ticks = 0;
        } else if (step == 11 && ticks > 10) {
            grab(mc, "config-overview.png");
            QuadHotbarConfig.setOverviewScrollStep(36);
            QuadHotbarConfig.setExperimentsEnabled(false);
            QuadHotbarConfig.save();
            Files.writeString(
                    mc.gameDirectory.toPath().resolve("overview-smoke-position.txt"),
                    QuadHotbarConfig.overviewX
                            + "\n"
                            + QuadHotbarConfig.overviewY
                            + "\n"
                            + QuadHotbarConfig.overviewScrollStep
                            + "\n"
                            + QuadHotbarConfig.hotbarRows
                            + "\n"
                            + QuadHotbarConfig.inventoryPages
                            + "\n");
            System.out.println(
                    "QUADHOTBAR CLIENT SMOKE PASSED: creative, survival, recipe book, authoritative"
                            + " placement, overlap, drag, reopen, general config, eight hotbars,"
                            + " inventory auto-open, configurable smooth scroll, cursor-safe"
                            + " arrows/overview/close, mouse position, hidden bindings preserved,"
                            + " dormant Freedom preferences, first explicit row edit, live"
                            + " chest/furnace sidecars, cross-container cursor transfers, furnace"
                            + " keybind/fuel rules"
                            + (net.minecraftforge.fml.ModList.get().isLoaded("corpse")
                                    ? ", native Corpse GUI and page looting"
                                    : "")
                            + (Boolean.getBoolean("quadhotbar.overviewRestartCheck")
                                    ? ", restart persistence"
                                    : ""));
            step = 12;
            serverRows.set(originalServerRows);
            serverRows.clearCache();
            org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback(
                    mc.getWindow().getWindow(), originalCursorCallback);
            mc.stop();
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    private static void requireHighRowSelection() throws Exception {
        requireTemporaryServerLimits();
        var type =
                Class.forName(
                        "com.archiangel.quadhotbar.client.QuadHotbarConfigScreen$GeneralSettingsScreen");
        var constructor =
                type.getDeclaredConstructor(net.minecraft.client.gui.screens.Screen.class);
        constructor.setAccessible(true);
        Object screen = constructor.newInstance((Object) null);
        var change = type.getDeclaredMethod("changeRows", int.class);
        change.setAccessible(true);
        QuadHotbarConfig.setHotbarRows(6);
        for (int expected : new int[] {7, 8}) {
            change.invoke(screen, 1);
            require(
                    QuadHotbarConfig.hotbarRows == expected
                            && QuadHotbarConfig.inventoryPages * 4 % expected == 0,
                    "Config could not select " + expected + " complete hotbars");
        }
        change.invoke(screen, -1);
        require(QuadHotbarConfig.hotbarRows == 7, "Right click could not select seven hotbars");
        var original = QuadHotbarClientEvents.serverPolicy();
        try {
            // Simulate the effective policy delivered to a non-exempt player. The integrated
            // server owner is now exempt by default, so changing global caps cannot limit them.
            QuadHotbarClientEvents.setServerPolicy(
                    new com.archiangel.quadhotbar.QuadHotbarPolicy(
                            true,
                            true,
                            true,
                            true,
                            QuadHotbarServerConfig.OverviewLayout.CLIENT,
                            100,
                            6,
                            18,
                            50));
            QuadHotbarConfig.setHotbarRows(6);
            change.invoke(screen, 1);
            require(QuadHotbarConfig.hotbarRows <= 6, "Config bypassed the player's hotbar limit");
        } finally {
            QuadHotbarClientEvents.setServerPolicy(original);
        }
    }

    private static void requireOverviewWidthSettings(Minecraft mc) throws Exception {
        double scale = mc.getWindow().getGuiScale();
        int width = QuadHotbarConfig.overviewWidth, pages = QuadHotbarConfig.inventoryPages;
        boolean side = QuadHotbarConfig.overviewFirstPageRight,
                horizontal = QuadHotbarConfig.overviewHorizontal;
        var originalPolicy = QuadHotbarClientEvents.serverPolicy();
        try {
            QuadHotbarClientEvents.setServerPolicy(
                    com.archiangel.quadhotbar.QuadHotbarPolicy.UNRESTRICTED);
            QuadHotbarConfig.setInventoryPages(6);
            QuadHotbarConfig.setOverviewEnabled(true);
            QuadHotbarConfig.setOverviewWidth(27);
            var type =
                    Class.forName(
                            "com.archiangel.quadhotbar.client.QuadHotbarConfigScreen$GeneralSettingsScreen");
            var ctor = type.getDeclaredConstructor(net.minecraft.client.gui.screens.Screen.class);
            ctor.setAccessible(true);
            var change = type.getDeclaredMethod("changeWidth", int.class);
            change.setAccessible(true);
            var settings = type.getDeclaredField("settings");
            settings.setAccessible(true);
            for (int guiScale : new int[] {1, 2, 3, 4}) {
                mc.getWindow().setGuiScale(guiScale);
                int expected = guiScale == 1 ? 27 : guiScale == 2 ? 18 : 9;
                require(
                        com.archiangel.quadhotbar.client.QuadHotbarOverviewSizing.effectiveWidth()
                                == expected,
                        "GUI scale did not limit overview width");
                require(QuadHotbarConfig.overviewWidth == 27, "GUI scale overwrote saved width");
                var screen =
                        (net.minecraft.client.gui.screens.Screen) ctor.newInstance((Object) null);
                screen.init(
                        mc,
                        mc.getWindow().getGuiScaledWidth(),
                        mc.getWindow().getGuiScaledHeight());
                for (Object setting : (java.util.List<?>) settings.get(screen)) {
                    var key = setting.getClass().getDeclaredField("key");
                    key.setAccessible(true);
                    String name = (String) key.get(setting);
                    if (!java.util.Set.of(
                                    "quadhotbar.overview.width",
                                    "quadhotbar.overview.first_page_side",
                                    "quadhotbar.overview.page_order")
                            .contains(name)) continue;
                    var allowed = setting.getClass().getDeclaredField("allowed");
                    allowed.setAccessible(true);
                    require(
                            ((java.util.function.BooleanSupplier) allowed.get(setting))
                                            .getAsBoolean()
                                    == (expected > 9),
                            "Wrong GUI-dependent availability for " + name);
                }
            }
            mc.getWindow().setGuiScale(1);
            QuadHotbarConfig.setOverviewWidth(9);
            for (int expected : new int[] {18, 27, 9}) {
                change.invoke(null, 1);
                require(
                        QuadHotbarConfig.overviewWidth == expected,
                        "Width did not advance by nine slots");
            }
            change.invoke(null, -1);
            require(QuadHotbarConfig.overviewWidth == 27, "Right click did not reverse width");
            QuadHotbarClientEvents.setServerPolicy(
                    new com.archiangel.quadhotbar.QuadHotbarPolicy(
                            true,
                            true,
                            true,
                            true,
                            QuadHotbarServerConfig.OverviewLayout.CLIENT,
                            100,
                            8,
                            18,
                            50));
            require(
                    com.archiangel.quadhotbar.client.QuadHotbarOverviewSizing.effectiveWidth() == 18
                            && QuadHotbarConfig.overviewWidth == 27,
                    "Server width limit erased preferences or was bypassed");
            change.invoke(null, 1);
            require(QuadHotbarConfig.overviewWidth == 9, "Width control bypassed server limit");
            change.invoke(null, -1);
            require(
                    QuadHotbarConfig.overviewWidth == 18,
                    "Reverse width control bypassed server limit");
            QuadHotbarClientEvents.setServerPolicy(
                    com.archiangel.quadhotbar.QuadHotbarPolicy.UNRESTRICTED);
            QuadHotbarConfig.setOverviewWidth(27);
            QuadHotbarConfig.setOverviewFirstPageRight(true);
            QuadHotbarConfig.setOverviewHorizontal(true);
            QuadHotbarConfig.save();
            var reload = QuadHotbarConfig.class.getDeclaredMethod("loadPreferences");
            reload.setAccessible(true);
            reload.invoke(null);
            require(
                    QuadHotbarConfig.overviewWidth == 27
                            && QuadHotbarConfig.overviewFirstPageRight
                            && QuadHotbarConfig.overviewHorizontal,
                    "Overview layout preferences did not persist");
            for (boolean byRows : new boolean[] {false, true})
                for (boolean firstRight : new boolean[] {false, true}) {
                    QuadHotbarConfig.setOverviewHorizontal(byRows);
                    QuadHotbarConfig.setOverviewFirstPageRight(firstRight);
                    var menu =
                            new com.archiangel.quadhotbar.QuadHotbarOverviewMenu(
                                    1, mc.player.getInventory(), 5, 0, true, 27, 50);
                    var overview =
                            new QuadHotbarOverviewScreen(
                                    menu,
                                    mc.player.getInventory(),
                                    net.minecraft.network.chat.Component.literal(
                                            "Width regression"));
                    overview.init(
                            mc,
                            mc.getWindow().getGuiScaledWidth(),
                            mc.getWindow().getGuiScaledHeight());
                    var across = QuadHotbarOverviewScreen.class.getDeclaredField("pagesAcross");
                    across.setAccessible(true);
                    require(
                            across.getInt(overview) == 3,
                            "Three-column overview did not render at GUI 1");
                    var first = menu.slots.get(0);
                    for (int page = 0; page < 5; page++) {
                        var slot = menu.slots.get(page * 36);
                        int x =
                                com.archiangel.quadhotbar.QuadHotbarOverviewLayout.column(
                                        page, 5, 3, byRows, firstRight);
                        int firstX =
                                com.archiangel.quadhotbar.QuadHotbarOverviewLayout.column(
                                        0, 5, 3, byRows, firstRight);
                        int y =
                                com.archiangel.quadhotbar.QuadHotbarOverviewLayout.row(
                                        page, 5, 3, byRows);
                        require(
                                slot.x - first.x == (x - firstX) * 170
                                        && slot.y - first.y == y * 100,
                                "Rendered slot did not match page layout");
                    }
                }
            System.out.println("QuadHotbar GUI width and page layout settings PASSED");
        } finally {
            mc.getWindow().setGuiScale(scale);
            QuadHotbarClientEvents.setServerPolicy(originalPolicy);
            QuadHotbarConfig.setOverviewWidth(width);
            QuadHotbarConfig.setInventoryPages(pages);
            QuadHotbarConfig.setOverviewFirstPageRight(side);
            QuadHotbarConfig.setOverviewHorizontal(horizontal);
            QuadHotbarConfig.save();
        }
    }

    private static void requireScrollbarPixels(Minecraft mc, QuadHotbarOverviewScreen screen)
            throws Exception {
        var top = QuadHotbarOverviewScreen.class.getDeclaredMethod("scrollbarTop");
        var handle = QuadHotbarOverviewScreen.class.getDeclaredMethod("scrollbarHeight");
        top.setAccessible(true);
        handle.setAccessible(true);
        int x = screen.getGuiLeft() + screen.getXSize() - 20;
        int y = screen.getGuiTop() + 28;
        int height = 9 * 18;
        int knobTop = (int) top.invoke(screen), knobHeight = (int) handle.invoke(screen);
        require(
                scroll(screen) == 0 ? knobTop == y + 1 : knobTop + knobHeight == y + height - 1,
                "Handle leaves a gap inside the track at a scroll endpoint");
        try (var pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            require(
                    pixels.getPixelRGBA(x + 5, y) == 0xFF373737
                            && pixels.getPixelRGBA(x + 5, y + height - 1) == 0xFFFFFFFF
                            && pixels.getPixelRGBA(x - 1, y + height - 2) == 0xFF373737,
                    "Rendered track borders do not align with the scrolling viewport");
            require(
                    pixels.getPixelRGBA(x + 5, knobTop) == 0xFFFFFFFF
                            && pixels.getPixelRGBA(x + 5, knobTop + knobHeight - 1) == 0xFF555555,
                    "Rendered handle does not match its drag bounds");
            int gripTop = knobTop + (knobHeight - 11) / 2;
            for (int line = 0; line < 6; line++)
                require(
                        pixels.getPixelRGBA(x + 5, gripTop + line * 2) == 0xFF8B8B8B,
                        "Rendered handle lost a vanilla grip line: " + line);
        }
    }

    private static void requireScrollbarGeometry(QuadHotbarOverviewScreen screen) throws Exception {
        var viewport = QuadHotbarOverviewScreen.class.getDeclaredField("viewportHeight");
        var content = QuadHotbarOverviewScreen.class.getDeclaredField("contentHeight");
        var handle = QuadHotbarOverviewScreen.class.getDeclaredMethod("scrollbarHeight");
        var travel = QuadHotbarOverviewScreen.class.getDeclaredMethod("scrollbarTravel");
        viewport.setAccessible(true);
        content.setAccessible(true);
        handle.setAccessible(true);
        travel.setAccessible(true);
        int originalViewport = viewport.getInt(screen), originalContent = content.getInt(screen);
        try {
            for (int height : new int[] {18, 162, 336}) {
                viewport.setInt(screen, height);
                int previousHandle = Integer.MAX_VALUE;
                for (int total : new int[] {100, 300, 10000}) {
                    content.setInt(screen, total);
                    int handleHeight = (int) handle.invoke(screen),
                            distance = (int) travel.invoke(screen);
                    require(
                            handleHeight >= 1
                                    && handleHeight <= height - 2
                                    && 1 + distance + handleHeight == height - 1
                                    && handleHeight <= previousHandle,
                            "Scrollbar geometry escaped its track or grew with more content");
                    previousHandle = handleHeight;
                }
            }
        } finally {
            viewport.setInt(screen, originalViewport);
            content.setInt(screen, originalContent);
        }
    }

    private static void prepareBindingChecks(Minecraft mc) {
        originalSlotKey = QuadHotbarKeyMappings.HOTBAR_SLOTS[62].getKey();
        mc.options.setKey(
                QuadHotbarKeyMappings.HOTBAR_SLOTS[62],
                InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_F13));
        KeyMapping.resetMapping();
        QuadHotbarConfig.setShowAllKeybinds(false);
        QuadHotbarConfig.setHotbarRows(2);
        QuadHotbarClientEvents.syncPageSettings();
        step = 70;
    }

    private static void requireFreedomPreferences() {
        require(
                QuadHotbarConfig.hotbarRows == 8
                        && QuadHotbarConfig.inventoryPages == 4
                        && QuadHotbarConfig.overviewHeight == 13
                        && QuadHotbarConfig.autoOpenOverview
                        && !QuadHotbarConfig.showPageButtons
                        && QuadHotbarConfig.alwaysShowPageLabels,
                "Freedom toggle overwrote stored preferences");
    }

    private static void requireControlsSlots(Minecraft mc, int expected) throws Exception {
        var screen = new KeyBindsScreen(null, mc.options);
        mc.setScreen(screen);
        var field = KeyBindsScreen.class.getDeclaredField("keyBindsList");
        field.setAccessible(true);
        var list = (KeyBindsList) field.get(screen);
        var key = KeyBindsList.KeyEntry.class.getDeclaredField("key");
        key.setAccessible(true);
        int count = 0;
        for (var entry : list.children()) {
            if (entry instanceof KeyBindsList.KeyEntry
                    && java.util.Arrays.asList(QuadHotbarKeyMappings.HOTBAR_SLOTS)
                            .contains(key.get(entry))) count++;
        }
        require(
                count == expected,
                "Controls: expected " + expected + " slot bindings, got " + count);
    }

    private static AbstractContainerScreen<?> base(QuadHotbarOverviewScreen screen)
            throws Exception {
        var field = QuadHotbarOverviewScreen.class.getDeclaredField("vanillaScreen");
        field.setAccessible(true);
        return (AbstractContainerScreen<?>) field.get(screen);
    }

    private static void arrow(QuadHotbarOverviewScreen screen, int direction, int button)
            throws Exception {
        var base = base(screen);
        boolean creative =
                base
                        instanceof
                        net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
        int x = direction < 0 ? base.getGuiLeft() - 13 : base.getGuiLeft() + base.getXSize() + 13;
        int y = base.getGuiTop() + (creative ? 119 : 148);
        screen.mouseClicked(x, y, button);
        screen.mouseReleased(x, y, button);
    }

    private static double scroll(QuadHotbarOverviewScreen screen) throws Exception {
        var field = QuadHotbarOverviewScreen.class.getDeclaredField("scroll");
        field.setAccessible(true);
        return field.getDouble(screen);
    }

    private static void closeWithMouse(Minecraft mc, QuadHotbarOverviewScreen screen)
            throws Exception {
        var field = QuadHotbarOverviewScreen.class.getDeclaredField("closeButton");
        field.setAccessible(true);
        var button = (net.minecraft.client.gui.components.Button) field.get(screen);
        int x = button.getX() + 10, y = button.getY() + 10;
        moveMouse(mc, screen, x, y);
        click(screen, x, y);
    }

    private static void moveMouse(
            Minecraft mc, net.minecraft.client.gui.screens.Screen screen, int x, int y)
            throws Exception {
        cursorX = x * mc.getWindow().getScreenWidth() / (double) screen.width;
        cursorY = y * mc.getWindow().getScreenHeight() / (double) screen.height;
        // Supply the same move event as a real click without moving the user's OS
        // pointer. An unfocused Windows window can deliver delayed, border-offset
        // GLFW events, which would test window focus rather than our return path.
        var move =
                net.minecraft.client.MouseHandler.class.getDeclaredMethod(
                        "onMove", long.class, double.class, double.class);
        move.setAccessible(true);
        move.invoke(mc.mouseHandler, mc.getWindow().getWindow(), cursorX, cursorY);
    }

    private static void requireCursorPosition(Minecraft mc) {
        require(
                !mc.mouseHandler.isMouseGrabbed()
                        && Math.abs(mc.mouseHandler.xpos() - cursorX) < 1
                        && Math.abs(mc.mouseHandler.ypos() - cursorY) < 1,
                "Returning from overview moved the mouse: expected="
                        + cursorX
                        + ","
                        + cursorY
                        + ", actual="
                        + mc.mouseHandler.xpos()
                        + ","
                        + mc.mouseHandler.ypos()
                        + ", grabbed="
                        + mc.mouseHandler.isMouseGrabbed());
    }

    private static void click(QuadHotbarOverviewScreen screen, int x, int y) {
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
    }

    private static void grab(Minecraft mc, String name) {
        Screenshot.grab(
                mc.gameDirectory,
                name,
                mc.getMainRenderTarget(),
                message -> System.out.println(message.getString()));
    }
}
