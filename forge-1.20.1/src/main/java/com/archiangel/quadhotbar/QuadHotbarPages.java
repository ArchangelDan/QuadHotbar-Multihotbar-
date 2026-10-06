package com.archiangel.quadhotbar;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Server-owned extra inventory pages. The vanilla 36 slots always hold the active page. */
public final class QuadHotbarPages {

    public static final int PAGE_SIZE = 36;
    public static final int MAX_PAGES = 100;
    public static final int MAX_HOTBAR_ROWS = 8;
    private static final String TAG = "QuadHotbarPages";
    private static final ReferenceQueue<Player> STALE_PLAYERS = new ReferenceQueue<>();
    private static final Map<IdentityWeakReference, State> STATES = new HashMap<>();

    private QuadHotbarPages() {}

    public static int pageStep(int rows) {
        return switch (rows) {
            case 3, 6 -> 3;
            case 5 -> 5;
            case 7 -> 7;
            case 8 -> 2;
            default -> 1;
        };
    }

    public static int maximumInventoryPages(int rows) {
        int step = pageStep(rows);
        return MAX_PAGES - MAX_PAGES % step;
    }

    /** Round up to a complete hotbar page; near the cap, round down instead. */
    public static int alignInventoryPages(int requested, int rows) {
        int step = pageStep(rows);
        int clamped = Mth.clamp(requested, 1, MAX_PAGES);
        int roundedUp = (clamped + step - 1) / step * step;
        return roundedUp <= MAX_PAGES ? roundedUp : maximumInventoryPages(rows);
    }

    /** Apply world limits without ever rounding a page count above the server's cap. */
    public static PageSettings limitSettings(
            int requestedPages, int requestedRows, int maximumPages, int maximumRows) {
        int pageCap = Mth.clamp(maximumPages, 1, MAX_PAGES);
        int rows = Mth.clamp(requestedRows, 2, Mth.clamp(maximumRows, 2, MAX_HOTBAR_ROWS));
        while (pageStep(rows) > pageCap) {
            rows--;
        }
        int step = pageStep(rows);
        int largestCompletePageCount = pageCap / step * step;
        int pages =
                Math.min(
                        alignInventoryPages(Mth.clamp(requestedPages, 1, pageCap), rows),
                        largestCompletePageCount);
        return new PageSettings(pages, rows);
    }

    public record PageSettings(int pages, int rows) {}

    public static synchronized State state(Player player) {
        expungeStalePlayers();
        IdentityWeakReference lookup = new IdentityWeakReference(player, null);
        State state = STATES.get(lookup);
        if (state == null) {
            state = new State();
            STATES.put(new IdentityWeakReference(player, STALE_PLAYERS), state);
        }
        return state;
    }

    public static ItemStack visibleStack(Player player, int row, int column) {
        State state = state(player);
        int globalRow = state.hotbarPage * state.hotbarRows + row;
        if (globalRow >= state.pageCount * 4) {
            return ItemStack.EMPTY;
        }
        int page = globalRow / 4;
        int slot = state.inventoryRowOrder.inventoryRow(globalRow % 4) * 9 + column;
        if (page == state.activePage) {
            return player.getInventory().items.get(slot);
        }
        return state.pages.get(page).get(slot);
    }

    public static int pageForRow(State state, int row) {
        return (state.hotbarPage * state.hotbarRows + row) / 4;
    }

    public static int slotForRow(State state, int row, int column) {
        return state.inventoryRowOrder.inventoryRow((state.hotbarPage * state.hotbarRows + row) % 4)
                        * 9
                + column;
    }

    public static int orderedRow(State state, int page, int slot) {
        return page * 4 + state.inventoryRowOrder.hotbarRow(slot / 9);
    }

    public static int hotbarPageCount(State state) {
        return maximumHotbarPages(state);
    }

    public static int maximumHotbarPages(State state) {
        return Math.max(1, state.pageCount * 4 / state.hotbarRows);
    }

    public static int visibleRows(State state) {
        return Math.min(
                state.hotbarRows, state.pageCount * 4 - state.hotbarPage * state.hotbarRows);
    }

    public static void switchActivePage(Player player, int page) {
        State state = state(player);
        page = Mth.clamp(page, 0, state.pageCount - 1);
        if (state.activePage == page) {
            return;
        }
        Inventory inventory = player.getInventory();
        List<ItemStack> old = state.pages.get(state.activePage);
        List<ItemStack> next = state.pages.get(page);
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            old.set(slot, inventory.items.get(slot));
            inventory.items.set(slot, next.get(slot));
            next.set(slot, ItemStack.EMPTY);
        }
        state.activePage = page;
        inventory.setChanged();
    }

    public static List<ItemStack> snapshot(Player player, int page) {
        State state = state(player);
        List<ItemStack> source =
                page == state.activePage ? player.getInventory().items : state.pages.get(page);
        List<ItemStack> snapshot = new ArrayList<>(PAGE_SIZE);
        for (ItemStack stack : source) {
            snapshot.add(stack.copy());
        }
        return snapshot;
    }

    public static ItemStack getPageItem(Player player, int page, int slot) {
        State state = state(player);
        return (page == state.activePage ? player.getInventory().items : state.pages.get(page))
                .get(slot);
    }

    public static void setPageItem(Player player, int page, int slot, ItemStack stack) {
        State state = state(player);
        (page == state.activePage ? player.getInventory().items : state.pages.get(page))
                .set(slot, stack);
        player.getInventory().setChanged();
    }

    /** Add a pickup to enabled pages only, preferring matching stacks before empty slots. */
    public static boolean addPickup(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        State state = state(player);
        Inventory inventory = player.getInventory();
        int before = stack.getCount();
        if (!stack.isDamaged()) {
            if (inventory.selected >= 0 && inventory.selected < PAGE_SIZE) {
                mergePickup(inventory.items.get(inventory.selected), stack, inventory);
            }
            mergePickup(inventory.getItem(40), stack, inventory);
            for (ItemStack existing : inventory.items) {
                mergePickup(existing, stack, inventory);
            }
            for (int distance = 1; distance < state.pageCount && !stack.isEmpty(); distance++) {
                int lower = state.activePage - distance;
                int upper = state.activePage + distance;
                if (lower >= 0) {
                    mergePage(state.pages.get(lower), stack, inventory);
                }
                if (upper < state.pageCount) {
                    mergePage(state.pages.get(upper), stack, inventory);
                }
            }
        }

        fillEmptySlots(inventory.items, stack, inventory);
        for (int distance = 1; distance < state.pageCount && !stack.isEmpty(); distance++) {
            int lower = state.activePage - distance;
            int upper = state.activePage + distance;
            if (lower >= 0) {
                fillEmptySlots(state.pages.get(lower), stack, inventory);
            }
            if (upper < state.pageCount) {
                fillEmptySlots(state.pages.get(upper), stack, inventory);
            }
        }
        if (stack.getCount() < before) {
            inventory.setChanged();
            return true;
        }
        // Unlike vanilla creative inventory, a full inventory must not void the entity.
        return false;
    }

    private static void mergePage(List<ItemStack> page, ItemStack incoming, Inventory inventory) {
        for (ItemStack existing : page) {
            mergePickup(existing, incoming, inventory);
            if (incoming.isEmpty()) {
                return;
            }
        }
    }

    private static void mergePickup(ItemStack existing, ItemStack incoming, Inventory inventory) {
        if (existing.isEmpty()
                || incoming.isEmpty()
                || !existing.isStackable()
                || !ItemStack.isSameItemSameTags(existing, incoming)) {
            return;
        }
        int room =
                Math.min(inventory.getMaxStackSize(), existing.getMaxStackSize())
                        - existing.getCount();
        int moved = Math.min(room, incoming.getCount());
        if (moved > 0) {
            existing.grow(moved);
            existing.setPopTime(5);
            incoming.shrink(moved);
        }
    }

    private static void fillEmptySlots(
            List<ItemStack> page, ItemStack incoming, Inventory inventory) {
        for (int slot = 0; slot < PAGE_SIZE && !incoming.isEmpty(); slot++) {
            if (page.get(slot).isEmpty()) {
                int moved =
                        Math.min(
                                incoming.getCount(),
                                Math.min(inventory.getMaxStackSize(), incoming.getMaxStackSize()));
                if (moved > 0) {
                    ItemStack placed = incoming.copyWithCount(moved);
                    placed.setPopTime(5);
                    page.set(slot, placed);
                    incoming.shrink(moved);
                }
            }
        }
    }

    /** Vanilla drops the active page; this drops every other page, including hidden ones. */
    public static void dropStored(Player player) {
        State state = state(player);
        for (int page = 0; page < MAX_PAGES; page++) {
            if (page == state.activePage) {
                continue;
            }
            List<ItemStack> items = state.pages.get(page);
            for (int slot = 0; slot < PAGE_SIZE; slot++) {
                ItemStack stack = items.get(slot);
                if (!stack.isEmpty()) {
                    if (!EnchantmentHelper.hasVanishingCurse(stack)) {
                        var dropped = player.drop(stack, true, false);
                        if (dropped != null && QuadHotbarServerConfig.deathPageCompatibility())
                            dropped.getPersistentData()
                                    .putInt("QuadHotbarPageSlot", page * PAGE_SIZE + slot);
                    }
                    items.set(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    public static void receiveSnapshot(
            Player player,
            int pageCount,
            int hotbarRows,
            int activePage,
            int hotbarPage,
            int selectedSlot,
            boolean bottomToTop,
            List<ItemStack> firstItems,
            List<ItemStack> secondItems,
            List<ItemStack> middleItems) {
        State state = state(player);
        state.pageCount = Mth.clamp(pageCount, 1, MAX_PAGES);
        state.hotbarRows = Mth.clamp(hotbarRows, 2, state.pageCount >= 2 ? MAX_HOTBAR_ROWS : 4);
        state.activePage = Mth.clamp(activePage, 0, state.pageCount - 1);
        state.hotbarPage = Mth.clamp(hotbarPage, 0, hotbarPageCount(state) - 1);
        state.inventoryRowOrder =
                bottomToTop
                        ? QuadHotbarLayout.InventoryOrder.BOTTOM_TO_TOP
                        : QuadHotbarLayout.InventoryOrder.TOP_TO_BOTTOM;
        int firstPage = state.hotbarPage * state.hotbarRows / 4;
        int lastPage = (state.hotbarPage * state.hotbarRows + visibleRows(state) - 1) / 4;
        copyInto(
                firstPage == state.activePage
                        ? player.getInventory().items
                        : state.pages.get(firstPage),
                firstItems);
        if (lastPage != firstPage) {
            copyInto(
                    lastPage == state.activePage
                            ? player.getInventory().items
                            : state.pages.get(lastPage),
                    secondItems);
        }
        if (lastPage - firstPage == 2) {
            int middlePage = firstPage + 1;
            copyInto(
                    middlePage == state.activePage
                            ? player.getInventory().items
                            : state.pages.get(middlePage),
                    middleItems);
        }
        // The active page lives only in Inventory.items, never in the page cache.
        Collections.fill(state.pages.get(state.activePage), ItemStack.EMPTY);
        player.getInventory().selected = Mth.clamp(selectedSlot, 0, PAGE_SIZE - 1);
    }

    private static void copyInto(List<ItemStack> target, List<ItemStack> source) {
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            target.set(slot, slot < source.size() ? source.get(slot).copy() : ItemStack.EMPTY);
        }
    }

    public static void save(Player player, CompoundTag playerTag) {
        State state = state(player);
        CompoundTag tag = new CompoundTag();
        tag.putInt("ActivePage", state.activePage);
        tag.putInt("HotbarPage", state.hotbarPage);
        tag.putInt("HotbarRows", state.hotbarRows);
        tag.putInt("PageCount", state.pageCount);
        tag.putBoolean(
                "BottomToTop",
                state.inventoryRowOrder == QuadHotbarLayout.InventoryOrder.BOTTOM_TO_TOP);
        ListTag pages = new ListTag();
        for (int page = 0; page < MAX_PAGES; page++) {
            if (page == state.activePage) {
                continue; // Vanilla already saves the active page.
            }
            ListTag items = new ListTag();
            for (int slot = 0; slot < PAGE_SIZE; slot++) {
                ItemStack stack = state.pages.get(page).get(slot);
                if (!stack.isEmpty()) {
                    CompoundTag itemTag = new CompoundTag();
                    itemTag.putByte("Slot", (byte) slot);
                    items.add(stack.save(itemTag));
                }
            }
            if (!items.isEmpty()) {
                CompoundTag pageTag = new CompoundTag();
                pageTag.putInt("Page", page);
                pageTag.put("Items", items);
                pages.add(pageTag);
            }
        }
        tag.put("StoredPages", pages);
        playerTag.put(TAG, tag);
    }

    public static void load(Player player, CompoundTag playerTag) {
        State state = new State();
        putState(player, state);
        if (!playerTag.contains(TAG, 10)) {
            return;
        }
        CompoundTag tag = playerTag.getCompound(TAG);
        state.pageCount = Mth.clamp(tag.getInt("PageCount"), 1, MAX_PAGES);
        state.activePage = Mth.clamp(tag.getInt("ActivePage"), 0, MAX_PAGES - 1);
        state.hotbarRows =
                Mth.clamp(tag.getInt("HotbarRows"), 2, state.pageCount >= 2 ? MAX_HOTBAR_ROWS : 4);
        state.hotbarPage = Mth.clamp(tag.getInt("HotbarPage"), 0, hotbarPageCount(state) - 1);
        state.inventoryRowOrder =
                tag.getBoolean("BottomToTop")
                        ? QuadHotbarLayout.InventoryOrder.BOTTOM_TO_TOP
                        : QuadHotbarLayout.InventoryOrder.TOP_TO_BOTTOM;
        ListTag pages = tag.getList("StoredPages", 10);
        for (int i = 0; i < pages.size(); i++) {
            CompoundTag pageTag = pages.getCompound(i);
            int page = pageTag.getInt("Page");
            if (page < 0 || page >= MAX_PAGES || page == state.activePage) {
                continue;
            }
            ListTag items = pageTag.getList("Items", 10);
            for (int j = 0; j < items.size(); j++) {
                CompoundTag itemTag = items.getCompound(j);
                int slot = itemTag.getByte("Slot") & 255;
                if (slot < PAGE_SIZE) {
                    state.pages.get(page).set(slot, ItemStack.of(itemTag));
                }
            }
        }
    }

    public static synchronized void clear(Player player) {
        expungeStalePlayers();
        STATES.remove(new IdentityWeakReference(player, null));
    }

    private static synchronized void putState(Player player, State state) {
        expungeStalePlayers();
        STATES.put(new IdentityWeakReference(player, STALE_PLAYERS), state);
    }

    private static void expungeStalePlayers() {
        Reference<? extends Player> stale;
        while ((stale = STALE_PLAYERS.poll()) != null) {
            STATES.remove(stale);
        }
    }

    private static final class IdentityWeakReference extends WeakReference<Player> {
        private final int identityHash;

        private IdentityWeakReference(Player player, ReferenceQueue<Player> queue) {
            super(player, queue);
            identityHash = System.identityHashCode(player);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            return object instanceof IdentityWeakReference other
                    && get() != null
                    && get() == other.get();
        }
    }

    public static final class State {
        public int activePage;
        public int hotbarPage;
        public int hotbarRows = 2;
        public int pageCount = 1;
        public QuadHotbarLayout.InventoryOrder inventoryRowOrder =
                QuadHotbarLayout.InventoryOrder.TOP_TO_BOTTOM;
        private final List<List<ItemStack>> pages = new ArrayList<>(MAX_PAGES);

        private State() {
            for (int page = 0; page < MAX_PAGES; page++) {
                List<ItemStack> items = new ArrayList<>(PAGE_SIZE);
                for (int slot = 0; slot < PAGE_SIZE; slot++) {
                    items.add(ItemStack.EMPTY);
                }
                pages.add(items);
            }
        }
    }
}
