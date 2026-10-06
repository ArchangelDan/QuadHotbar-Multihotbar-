package com.archiangel.quadhotbar;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class QuadHotbarConfig {
    public static final int DEFAULT_OVERVIEW_SCROLL_STEP = 20;
    public static final int MAX_OVERVIEW_SCROLL_STEP = 180;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Personal preferences are shared by local worlds and remote servers. */
    private static final class Profile {
        private final ModConfigSpec.IntValue HOTBAR_ROWS =
                BUILDER.comment(
                                "How many hotbar rows should be rendered. More than four rows"
                                        + " require experimental inventory pages.")
                        .defineInRange("hotbarRows", 2, 2, QuadHotbarPages.MAX_HOTBAR_ROWS);

        private final ModConfigSpec.BooleanValue ENABLED =
                BUILDER.comment(
                                "Enable QuadHotbar's hotbar rendering and controls. When false, use"
                                        + " the vanilla hotbar.")
                        .define("enabled", true);

        private final ModConfigSpec.BooleanValue SCROLL_BETWEEN_HOTBARS =
                BUILDER.comment(
                                "Allow the mouse wheel to switch between hotbars. When false, it"
                                        + " stays within the selected hotbar.")
                        .define("scrollBetweenHotbars", true);
        private final ModConfigSpec.BooleanValue SHOW_ALL_KEYBINDS =
                BUILDER.comment(
                                "Show all QuadHotbar keybinds in Controls, including unavailable"
                                        + " slots. Hidden bindings are always saved.")
                        .define("showAllKeybinds", false);

        private final ModConfigSpec.EnumValue<QuadHotbarLayout.InventoryOrder> INVENTORY_ROW_ORDER =
                BUILDER.comment(
                                "Order of the three additional inventory rows after the main"
                                        + " hotbar. Does not move items.")
                        .defineEnum(
                                "inventoryRowOrder", QuadHotbarLayout.InventoryOrder.TOP_TO_BOTTOM);
        private final ModConfigSpec.EnumValue<QuadHotbarLayout.MainCorner> MAIN_HOTBAR_CORNER =
                BUILDER.comment(
                                "Corner of the hotbar grid occupied by the main hotbar. Additional"
                                        + " bars follow from that corner.")
                        .defineEnum("mainHotbarCorner", QuadHotbarLayout.MainCorner.BOTTOM_LEFT);

        private final ModConfigSpec.BooleanValue EXPERIMENTS_ENABLED =
                BUILDER.comment("Enable experimental inventory and hotbar paging.")
                        .define("experimentsEnabled", false);

        private final ModConfigSpec.IntValue INVENTORY_PAGES =
                BUILDER.comment(
                                "Number of inventory pages, capped at 100 and aligned to complete"
                                        + " hotbar pages. Reducing it never deletes items.")
                        .defineInRange("inventoryPages", 1, 1, QuadHotbarPages.MAX_PAGES);

        private final ModConfigSpec.BooleanValue SHOW_PAGE_BUTTONS =
                BUILDER.comment(
                                "Show inventory page arrow buttons. Page keybinds still work when"
                                        + " buttons are hidden.")
                        .define("showPageButtons", true);

        private final ModConfigSpec.BooleanValue ALWAYS_SHOW_PAGE_LABELS =
                BUILDER.comment(
                                "Keep inventory and hotbar page numbers visible instead of hiding"
                                        + " them after three seconds.")
                        .define("alwaysShowPageLabels", false);

        private final ModConfigSpec.BooleanValue OVERVIEW_ENABLED =
                BUILDER.define("overviewEnabled", true);
        private final ModConfigSpec.BooleanValue SHOW_OVERVIEW_BUTTON =
                BUILDER.define("showOverviewButton", true);
        private final ModConfigSpec.BooleanValue AUTO_OPEN_OVERVIEW =
                BUILDER.comment(
                                "Automatically open the page overview when opening the player"
                                        + " inventory. Server restrictions still apply.")
                        .define("autoOpenOverview", true);
        private final ModConfigSpec.BooleanValue OVERVIEW_LAST_OPEN =
                BUILDER.comment(
                                "Remember whether the player left the inventory page overview open."
                                    + " Closing only the panel pauses auto-open until it is opened"
                                    + " manually again; closing the whole inventory does not.")
                        .define("overviewLastOpen", true);
        private final ModConfigSpec.BooleanValue STANDALONE_OVERVIEW =
                BUILDER.define("standaloneOverview", false);
        private final ModConfigSpec.IntValue OVERVIEW_WIDTH =
                BUILDER.comment(
                                "Overview width in slots: 9, 18 or 27. GUI scale and server limits"
                                        + " temporarily reduce the displayed width.")
                        .defineInRange("overviewWidth", 9, 9, QuadHotbarOverviewLayout.MAX_WIDTH);
        private final ModConfigSpec.BooleanValue OVERVIEW_FIRST_PAGE_RIGHT =
                BUILDER.comment("Start overview page numbering at the rightmost column.")
                        .define("overviewFirstPageRight", false);
        private final ModConfigSpec.BooleanValue OVERVIEW_HORIZONTAL =
                BUILDER.comment("Fill overview pages by rows instead of by columns.")
                        .define("overviewHorizontal", true);
        private final ModConfigSpec.IntValue OVERVIEW_HEIGHT =
                BUILDER.comment("Overview viewport height in slots; automatically fits the screen.")
                        .defineInRange("overviewHeight", 9, 1, 50);
        private final ModConfigSpec.IntValue OVERVIEW_SCROLL_STEP =
                BUILDER.comment(
                                "Overview scroll distance per mouse wheel step in GUI pixels. A"
                                        + " slot is 18 pixels tall.")
                        .defineInRange(
                                "overviewScrollStep",
                                DEFAULT_OVERVIEW_SCROLL_STEP,
                                1,
                                MAX_OVERVIEW_SCROLL_STEP);
        private final ModConfigSpec.BooleanValue OVERVIEW_DRAGGABLE =
                BUILDER.comment("Allow dragging the overview by its title bar.")
                        .define("overviewDraggable", true);
        private final ModConfigSpec.IntValue OVERVIEW_X =
                BUILDER.comment(
                                "Saved overview X position in GUI pixels. -1 uses automatic"
                                        + " positioning.")
                        .defineInRange("overviewX", -1, -1, 10000);
        private final ModConfigSpec.IntValue OVERVIEW_Y =
                BUILDER.comment(
                                "Saved overview Y position in GUI pixels. -1 uses automatic"
                                        + " positioning.")
                        .defineInRange("overviewY", -1, -1, 10000);
    }

    private static final Profile PREFERENCES = new Profile();

    static {
        // Keep obsolete keys registered so loading an older config does not erase its values.
        // They are no longer selected: server policy only limits effective personal preferences.
        BUILDER.comment("Legacy multiplayer preferences; retained but no longer used.")
                .push("multiplayer");
        new Profile();
        BUILDER.pop();
        BUILDER.comment("Legacy migration marker; retained but no longer used.")
                .define("separateProfilesInitialized", false);
    }

    private static boolean multiplayerContext;

    public static void setMultiplayerContext(boolean multiplayer) {
        multiplayerContext = multiplayer;
    }

    public static boolean multiplayerContext() {
        return multiplayerContext;
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static int hotbarRows = 2;
    public static boolean enabled = true;
    public static boolean scrollBetweenHotbars = true;
    public static boolean showAllKeybinds = false;
    public static QuadHotbarLayout.InventoryOrder inventoryRowOrder =
            QuadHotbarLayout.InventoryOrder.TOP_TO_BOTTOM;
    public static QuadHotbarLayout.MainCorner mainHotbarCorner =
            QuadHotbarLayout.MainCorner.BOTTOM_LEFT;
    public static boolean experimentsEnabled = false;
    public static int inventoryPages = 1;
    public static boolean showPageButtons = true;
    public static boolean alwaysShowPageLabels = false;
    public static boolean overviewEnabled = true;
    public static boolean showOverviewButton = true;
    public static boolean autoOpenOverview = true;
    public static boolean overviewLastOpen = true;
    public static boolean standaloneOverview = false;
    public static int overviewWidth = 9;
    public static boolean overviewFirstPageRight = false;
    public static boolean overviewHorizontal = true;
    public static int overviewHeight = 9;
    public static int overviewScrollStep = DEFAULT_OVERVIEW_SCROLL_STEP;
    public static boolean overviewDraggable = true;
    public static int overviewX = -1;
    public static int overviewY = -1;

    public static void setOverviewEnabled(boolean value) {
        PREFERENCES.OVERVIEW_ENABLED.set(value);
        overviewEnabled = value;
    }

    public static void setShowOverviewButton(boolean value) {
        PREFERENCES.SHOW_OVERVIEW_BUTTON.set(value);
        showOverviewButton = value;
    }

    public static void setAutoOpenOverview(boolean value) {
        if (value && !autoOpenOverview) setOverviewLastOpen(true);
        PREFERENCES.AUTO_OPEN_OVERVIEW.set(value);
        autoOpenOverview = value;
    }

    public static void setOverviewLastOpen(boolean value) {
        PREFERENCES.OVERVIEW_LAST_OPEN.set(value);
        overviewLastOpen = value;
    }

    public static void setStandaloneOverview(boolean value) {
        PREFERENCES.STANDALONE_OVERVIEW.set(value);
        standaloneOverview = value;
    }

    public static void setOverviewWidth(int value) {
        overviewWidth = QuadHotbarOverviewLayout.normalizeWidth(value);
        PREFERENCES.OVERVIEW_WIDTH.set(overviewWidth);
    }

    public static void setOverviewFirstPageRight(boolean value) {
        overviewFirstPageRight = value;
        PREFERENCES.OVERVIEW_FIRST_PAGE_RIGHT.set(value);
    }

    public static void setOverviewHorizontal(boolean value) {
        overviewHorizontal = value;
        PREFERENCES.OVERVIEW_HORIZONTAL.set(value);
    }

    public static void setOverviewHeight(int value) {
        overviewHeight = Math.max(1, Math.min(50, value));
        PREFERENCES.OVERVIEW_HEIGHT.set(overviewHeight);
    }

    public static void setOverviewScrollStep(int value) {
        overviewScrollStep = Math.max(1, Math.min(MAX_OVERVIEW_SCROLL_STEP, value));
        PREFERENCES.OVERVIEW_SCROLL_STEP.set(overviewScrollStep);
    }

    public static void setOverviewDraggable(boolean value) {
        overviewDraggable = value;
        PREFERENCES.OVERVIEW_DRAGGABLE.set(value);
    }

    public static void setOverviewPosition(int x, int y) {
        overviewX = Math.max(-1, Math.min(10000, x));
        overviewY = Math.max(-1, Math.min(10000, y));
        PREFERENCES.OVERVIEW_X.set(overviewX);
        PREFERENCES.OVERVIEW_Y.set(overviewY);
    }

    private QuadHotbarConfig() {}

    public static void setHotbarRows(int rows) {
        PREFERENCES.HOTBAR_ROWS.set(
                Math.max(
                        2,
                        Math.min(experimentsEnabled ? QuadHotbarPages.MAX_HOTBAR_ROWS : 4, rows)));
        hotbarRows = PREFERENCES.HOTBAR_ROWS.get();
        // Editing ordinary hotbars must not rewrite dormant Freedom page preferences.
        if (experimentsEnabled) setInventoryPages(inventoryPages);
    }

    public static void setEnabled(boolean value) {
        PREFERENCES.ENABLED.set(value);
        enabled = PREFERENCES.ENABLED.get();
    }

    public static void setScrollBetweenHotbars(boolean value) {
        PREFERENCES.SCROLL_BETWEEN_HOTBARS.set(value);
        scrollBetweenHotbars = PREFERENCES.SCROLL_BETWEEN_HOTBARS.get();
    }

    public static void setShowAllKeybinds(boolean value) {
        PREFERENCES.SHOW_ALL_KEYBINDS.set(value);
        showAllKeybinds = value;
    }

    public static void setInventoryRowOrder(QuadHotbarLayout.InventoryOrder value) {
        PREFERENCES.INVENTORY_ROW_ORDER.set(value);
        inventoryRowOrder = value;
    }

    public static void setMainHotbarCorner(QuadHotbarLayout.MainCorner value) {
        PREFERENCES.MAIN_HOTBAR_CORNER.set(value);
        mainHotbarCorner = value;
    }

    public static void save() {
        SPEC.save();
    }

    public static void setExperimentsEnabled(boolean value) {
        PREFERENCES.EXPERIMENTS_ENABLED.set(value);
        experimentsEnabled = PREFERENCES.EXPERIMENTS_ENABLED.get();
        // Stored preferences survive disabling Freedom; runtime/world limits decide what is active.
    }

    public static void setInventoryPages(int pages) {
        PREFERENCES.INVENTORY_PAGES.set(
                experimentsEnabled
                        ? QuadHotbarPages.alignInventoryPages(pages, hotbarRows)
                        : Math.max(1, Math.min(QuadHotbarPages.MAX_PAGES, pages)));
        inventoryPages = PREFERENCES.INVENTORY_PAGES.get();
    }

    public static void setShowPageButtons(boolean show) {
        PREFERENCES.SHOW_PAGE_BUTTONS.set(show);
        showPageButtons = PREFERENCES.SHOW_PAGE_BUTTONS.get();
    }

    public static void setAlwaysShowPageLabels(boolean show) {
        PREFERENCES.ALWAYS_SHOW_PAGE_LABELS.set(show);
        alwaysShowPageLabels = PREFERENCES.ALWAYS_SHOW_PAGE_LABELS.get();
    }

    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        loadPreferences();
    }

    private static void loadPreferences() {
        hotbarRows = PREFERENCES.HOTBAR_ROWS.get();
        enabled = PREFERENCES.ENABLED.get();
        scrollBetweenHotbars = PREFERENCES.SCROLL_BETWEEN_HOTBARS.get();
        showAllKeybinds = PREFERENCES.SHOW_ALL_KEYBINDS.get();
        inventoryRowOrder = PREFERENCES.INVENTORY_ROW_ORDER.get();
        mainHotbarCorner = PREFERENCES.MAIN_HOTBAR_CORNER.get();
        experimentsEnabled = PREFERENCES.EXPERIMENTS_ENABLED.get();
        inventoryPages = PREFERENCES.INVENTORY_PAGES.get();
        showPageButtons = PREFERENCES.SHOW_PAGE_BUTTONS.get();
        alwaysShowPageLabels = PREFERENCES.ALWAYS_SHOW_PAGE_LABELS.get();
        overviewEnabled = PREFERENCES.OVERVIEW_ENABLED.get();
        showOverviewButton = PREFERENCES.SHOW_OVERVIEW_BUTTON.get();
        autoOpenOverview = PREFERENCES.AUTO_OPEN_OVERVIEW.get();
        overviewLastOpen = PREFERENCES.OVERVIEW_LAST_OPEN.get();
        standaloneOverview = PREFERENCES.STANDALONE_OVERVIEW.get();
        overviewWidth = QuadHotbarOverviewLayout.normalizeWidth(PREFERENCES.OVERVIEW_WIDTH.get());
        overviewFirstPageRight = PREFERENCES.OVERVIEW_FIRST_PAGE_RIGHT.get();
        overviewHorizontal = PREFERENCES.OVERVIEW_HORIZONTAL.get();
        overviewHeight = PREFERENCES.OVERVIEW_HEIGHT.get();
        overviewScrollStep = PREFERENCES.OVERVIEW_SCROLL_STEP.get();
        overviewDraggable = PREFERENCES.OVERVIEW_DRAGGABLE.get();
        overviewX = PREFERENCES.OVERVIEW_X.get();
        overviewY = PREFERENCES.OVERVIEW_Y.get();
    }
}
