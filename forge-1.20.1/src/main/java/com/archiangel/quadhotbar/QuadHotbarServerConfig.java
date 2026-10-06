package com.archiangel.quadhotbar;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** World-owned limits; only the server uses these values to authorize page requests. */
public final class QuadHotbarServerConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ALLOW_FREEDOM =
            BUILDER.comment(
                            "Allow Freedom (extra inventory pages and up to eight hotbars)."
                                    + " Disabling hides stored pages without deleting items.")
                    .worldRestart()
                    .define("allowFreedom", true);
    private static final ForgeConfigSpec.BooleanValue ALLOW_OVERVIEW =
            BUILDER.comment(
                            "Allow opening and using the all-pages inventory overview, including"
                                    + " its keybind.")
                    .worldRestart()
                    .define("allowPageOverview", true);
    private static final ForgeConfigSpec.BooleanValue SHOW_OVERVIEW_BUTTON =
            BUILDER.comment(
                            "Allow the grid button in the inventory. The keybind is controlled by"
                                    + " allowPageOverview.")
                    .worldRestart()
                    .define("showOverviewButton", true);

    private static final ForgeConfigSpec.BooleanValue ALLOW_CONTAINER_OVERVIEW =
            BUILDER.comment(
                            "Allow the page overview beside chests, furnaces and other item"
                                    + " containers. The original container stays open.")
                    .worldRestart()
                    .define("allowContainerOverview", true);
    private static final ForgeConfigSpec.BooleanValue DEATH_PAGE_COMPATIBILITY =
            BUILDER.comment(
                            "Preserve extra-page slot positions for Corpse/GraveStone death"
                                    + " inventories. Items still follow normal death drops and"
                                    + " keepInventory.")
                    .worldRestart()
                    .define("deathPageCompatibility", true);

    public enum OverviewLayout {
        CLIENT,
        DOCKED,
        STANDALONE
    }

    private static final ForgeConfigSpec.EnumValue<OverviewLayout> OVERVIEW_LAYOUT =
            BUILDER.comment(
                            "CLIENT lets each player choose; DOCKED or STANDALONE forces that"
                                    + " layout.")
                    .worldRestart()
                    .defineEnum("overviewLayout", OverviewLayout.CLIENT);
    private static final ForgeConfigSpec.IntValue MAX_OVERVIEW_WIDTH =
            BUILDER.comment(
                            "Maximum overview width in item slots: 9, 18 or 27."
                                    + " Intermediate values round down to a multiple of 9.")
                    .worldRestart()
                    .defineInRange("maxOverviewWidth", 27, 9, QuadHotbarOverviewLayout.MAX_WIDTH);
    private static final ForgeConfigSpec.IntValue MAX_OVERVIEW_HEIGHT =
            BUILDER.comment(
                            "Maximum overview viewport height in item slots; the display also"
                                    + " limits its visible size.")
                    .worldRestart()
                    .defineInRange("maxOverviewHeight", 50, 1, 50);

    private static final ForgeConfigSpec.IntValue MAX_INVENTORY_PAGES =
            BUILDER.comment(
                            "Maximum inventory pages allowed per player in this world (1-100)."
                                    + " Hidden pages retain their items.")
                    .worldRestart()
                    .defineInRange(
                            "maxInventoryPages",
                            QuadHotbarPages.MAX_PAGES,
                            1,
                            QuadHotbarPages.MAX_PAGES);

    private static final ForgeConfigSpec.IntValue MAX_HOTBAR_ROWS =
            BUILDER.comment(
                            "Maximum number of hotbars shown at once per player in this world"
                                    + " (2-8).")
                    .worldRestart()
                    .defineInRange(
                            "maxHotbarRows",
                            QuadHotbarPages.MAX_HOTBAR_ROWS,
                            2,
                            QuadHotbarPages.MAX_HOTBAR_ROWS);

    private static final ForgeConfigSpec.BooleanValue EXEMPT_OPERATORS =
            BUILDER.comment(
                            "Operators bypass global limits by default. Set false to apply global"
                                    + " limits to operators too. Explicit playerOverrides always"
                                    + " apply.")
                    .worldRestart()
                    .define("exemptOperators", true);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> UNRESTRICTED_PLAYERS =
            BUILDER.comment(
                            "Minecraft usernames exempt from global limits (case-insensitive)."
                                    + " No items are shared between players or worlds.",
                            "Example: unrestrictedPlayers = [\"Player1\", \"Player2\"]")
                    .worldRestart()
                    .defineListAllowEmpty(
                            "unrestrictedPlayers",
                            List.of(),
                            value -> value instanceof String name && validName(name));
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> PLAYER_OVERRIDES =
            BUILDER.comment(
                            "Per-player rules, applied after operator/whitelist exemptions."
                                + " Unspecified settings keep that player's effective defaults.",
                            "Example: playerOverrides = [\"Player1; maxInventoryPages=2;"
                                    + " maxHotbarRows=4\", \"Player2; allowPageOverview=false\"]",
                            "Supported keys: allowFreedom, allowPageOverview, showOverviewButton,"
                                    + " allowContainerOverview, overviewLayout, maxInventoryPages,"
                                    + " maxHotbarRows, maxOverviewWidth, maxOverviewHeight.",
                            "deathPageCompatibility is world-wide, not a permission override."
                                    + " Restart the world/server after editing these settings.")
                    .worldRestart()
                    .defineListAllowEmpty(
                            "playerOverrides",
                            List.of(),
                            value -> value instanceof String rule && parseOverride(rule) != null);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static final Set<String> BOOLEAN_KEYS =
            Set.of(
                    "allowFreedom",
                    "allowPageOverview",
                    "showOverviewButton",
                    "allowContainerOverview");

    private record Override(String name, Map<String, String> values) {}

    private static boolean validName(String name) {
        return name.matches("[A-Za-z0-9_]{1,16}");
    }

    private static Override parseOverride(String rule) {
        String[] parts = rule.split(";", -1);
        String name = parts[0].trim();
        if (!validName(name) || parts.length < 2) return null;
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String[] setting = parts[i].trim().split("=", -1);
            if (setting.length != 2) return null;
            String key = setting[0].trim();
            String value = setting[1].trim();
            try {
                if (BOOLEAN_KEYS.contains(key)) {
                    if (!value.equals("true") && !value.equals("false")) return null;
                } else if (key.equals("overviewLayout")) {
                    OverviewLayout.valueOf(value);
                } else {
                    int number = Integer.parseInt(value);
                    boolean valid =
                            switch (key) {
                                case "maxInventoryPages" -> number >= 1 && number <= 100;
                                case "maxHotbarRows" -> number >= 2 && number <= 8;
                                case "maxOverviewWidth" ->
                                        number == 9 || number == 18 || number == 27;
                                case "maxOverviewHeight" -> number >= 1 && number <= 50;
                                default -> false;
                            };
                    if (!valid) return null;
                }
            } catch (IllegalArgumentException e) {
                return null;
            }
            if (values.put(key, value) != null) return null;
        }
        return new Override(name, values);
    }

    public static QuadHotbarPolicy forPlayer(Player player) {
        return player instanceof ServerPlayer server
                ? resolve(
                        server.getGameProfile().getName(),
                        server.server.getPlayerList().isOp(server.getGameProfile()))
                : globalPolicy();
    }

    /** Reject stale overview clicks immediately after permissions shrink, before the next sync. */
    public static boolean permitsCurrentPages(Player player) {
        var state = QuadHotbarPages.state(player);
        var policy = forPlayer(player);
        return state.pageCount <= policy.maxInventoryPages()
                && state.hotbarRows <= policy.maxHotbarRows();
    }

    public static QuadHotbarPolicy globalPolicy() {
        return new QuadHotbarPolicy(
                ALLOW_FREEDOM.get(),
                ALLOW_OVERVIEW.get(),
                SHOW_OVERVIEW_BUTTON.get(),
                ALLOW_CONTAINER_OVERVIEW.get(),
                OVERVIEW_LAYOUT.get(),
                MAX_INVENTORY_PAGES.get(),
                MAX_HOTBAR_ROWS.get(),
                maxOverviewWidth(),
                maxOverviewHeight());
    }

    public static QuadHotbarPolicy resolve(String name, boolean operator) {
        boolean exempt =
                (operator && EXEMPT_OPERATORS.get())
                        || UNRESTRICTED_PLAYERS.get().stream().anyMatch(name::equalsIgnoreCase);
        QuadHotbarPolicy base = exempt ? QuadHotbarPolicy.UNRESTRICTED : globalPolicy();
        Map<String, String> values = new LinkedHashMap<>();
        for (String rule : PLAYER_OVERRIDES.get()) {
            Override parsed = parseOverride(rule);
            if (parsed != null && parsed.name.equalsIgnoreCase(name)) values.putAll(parsed.values);
        }
        return new QuadHotbarPolicy(
                bool(values, "allowFreedom", base.allowFreedom()),
                bool(values, "allowPageOverview", base.overviewAllowed()),
                bool(values, "showOverviewButton", base.showOverviewButton()),
                bool(values, "allowContainerOverview", base.containerOverviewAllowed()),
                values.containsKey("overviewLayout")
                        ? OverviewLayout.valueOf(values.get("overviewLayout"))
                        : base.overviewLayout(),
                number(values, "maxInventoryPages", base.pageLimit()),
                number(values, "maxHotbarRows", base.rowLimit()),
                number(values, "maxOverviewWidth", base.maxOverviewWidth()),
                number(values, "maxOverviewHeight", base.maxOverviewHeight()));
    }

    private static boolean bool(Map<String, String> values, String key, boolean fallback) {
        return values.containsKey(key) ? Boolean.parseBoolean(values.get(key)) : fallback;
    }

    private static int number(Map<String, String> values, String key, int fallback) {
        return values.containsKey(key) ? Integer.parseInt(values.get(key)) : fallback;
    }

    private QuadHotbarServerConfig() {}

    public static boolean deathPageCompatibility() {
        return DEATH_PAGE_COMPATIBILITY.get();
    }

    private static int maxOverviewWidth() {
        return QuadHotbarOverviewLayout.normalizeWidth(MAX_OVERVIEW_WIDTH.get());
    }

    private static int maxOverviewHeight() {
        return MAX_OVERVIEW_HEIGHT.get();
    }
}
