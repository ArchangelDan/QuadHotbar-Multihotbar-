package com.archiangel.quadhotbar;

/** Effective permissions for one player, calculated only by the server. */
public record QuadHotbarPolicy(
        boolean allowFreedom,
        boolean overviewAllowed,
        boolean showOverviewButton,
        boolean containerOverviewAllowed,
        QuadHotbarServerConfig.OverviewLayout overviewLayout,
        int pageLimit,
        int rowLimit,
        int maxOverviewWidth,
        int maxOverviewHeight) {
    public static final QuadHotbarPolicy UNRESTRICTED =
            new QuadHotbarPolicy(
                    true,
                    true,
                    true,
                    true,
                    QuadHotbarServerConfig.OverviewLayout.CLIENT,
                    100,
                    8,
                    QuadHotbarOverviewLayout.MAX_WIDTH,
                    50);

    public int maxInventoryPages() {
        return allowFreedom ? pageLimit : 1;
    }

    public int maxHotbarRows() {
        return Math.min(rowLimit, allowFreedom ? 8 : 4);
    }

    public boolean allowPageOverview() {
        return allowFreedom && overviewAllowed;
    }

    public boolean allowContainerOverview() {
        return allowPageOverview() && containerOverviewAllowed;
    }
}
