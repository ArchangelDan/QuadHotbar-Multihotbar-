package com.archiangel.quadhotbar;

/** Layout changes slot presentation and traversal, never stored inventory contents. */
public final class QuadHotbarLayout {
    public enum InventoryOrder {
        TOP_TO_BOTTOM,
        BOTTOM_TO_TOP;

        public int inventoryRow(int hotbarRow) {
            return this == BOTTOM_TO_TOP && hotbarRow != 0 ? 4 - hotbarRow : hotbarRow;
        }

        // Reversing the additional rows is its own inverse. Row zero is always the main hotbar.
        public int hotbarRow(int inventoryRow) {
            return inventoryRow(inventoryRow);
        }

        public String translationKey() {
            return "quadhotbar.layout.order." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum MainCorner {
        TOP_LEFT(true, false),
        TOP_RIGHT(true, true),
        BOTTOM_RIGHT(false, true),
        BOTTOM_LEFT(false, false);
        public final boolean top;
        public final boolean right;

        MainCorner(boolean top, boolean right) {
            this.top = top;
            this.right = right;
        }

        public String translationKey() {
            return "quadhotbar.layout.corner." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private QuadHotbarLayout() {}

    public static int[] coordinates(
            int row,
            int rows,
            int screenWidth,
            int screenHeight,
            int barWidth,
            int barHeight,
            MainCorner corner) {
        int levels = (rows + 1) / 2;
        // An odd final bar remains centered on the farthest level from the main bar.
        boolean centered = rows == 1 || rows % 2 != 0 && row == rows - 1;
        boolean right = (row % 2 != 0) != corner.right;
        int x =
                centered
                        ? screenWidth / 2 - barWidth / 2
                        : screenWidth / 2 - (right ? 0 : barWidth);
        int level = corner.top ? levels - 1 - row / 2 : row / 2;
        int y = screenHeight - barHeight * (level + 1);
        return new int[] {x, y};
    }
}
