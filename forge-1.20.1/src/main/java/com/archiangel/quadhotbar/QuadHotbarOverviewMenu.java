package com.archiangel.quadhotbar;

import com.archiangel.quadhotbar.mixin.FurnaceMenuAccessor;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** A real server container: the client never supplies the contents of survival inventory pages. */
public final class QuadHotbarOverviewMenu extends AbstractContainerMenu {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, QuadHotbar.MODID);
    public static final RegistryObject<MenuType<QuadHotbarOverviewMenu>> TYPE =
            MENUS.register(
                    "page_overview",
                    () -> IForgeMenuType.create(QuadHotbarOverviewMenu::fromNetwork));
    public final int pageCount;
    public int activePage;
    public final boolean standalone;
    public final int columns;
    public final int viewportRows;
    public final int pageSlots;
    private final AbstractContainerMenu inventoryMenu;
    public final boolean external;
    public final boolean deathInventory;
    public boolean returningToInventory;

    private static QuadHotbarOverviewMenu fromNetwork(
            int id, Inventory inventory, FriendlyByteBuf data) {
        return new QuadHotbarOverviewMenu(
                id,
                inventory,
                data.readVarInt(),
                data.readVarInt(),
                data.readBoolean(),
                data.readVarInt(),
                data.readVarInt());
    }

    public QuadHotbarOverviewMenu(
            int id,
            Inventory inventory,
            int pages,
            int active,
            boolean standalone,
            int columns,
            int height) {
        this(
                id,
                inventory,
                pages,
                active,
                standalone,
                columns,
                height,
                inventory.player.inventoryMenu,
                null,
                false);
    }

    /** Virtual page menu beside a live container; it never replaces or closes that container. */
    public static QuadHotbarOverviewMenu external(
            int id,
            Inventory inventory,
            int pages,
            int active,
            int height,
            AbstractContainerMenu parent,
            Container storage,
            boolean death) {
        return new QuadHotbarOverviewMenu(
                id, inventory, pages, active, false, 9, height, parent, storage, death);
    }

    public static Container playerPages(Player player, int pages) {
        return new PageInventory(player, pages * 36);
    }

    private QuadHotbarOverviewMenu(
            int id,
            Inventory inventory,
            int pages,
            int active,
            boolean standalone,
            int columns,
            int height,
            AbstractContainerMenu cursorMenu,
            Container externalStorage,
            boolean death) {
        super(TYPE.get(), id);
        external = externalStorage != null;
        deathInventory = death;
        this.pageCount = Math.max(1, Math.min(QuadHotbarPages.MAX_PAGES, pages));
        this.activePage = Math.max(0, Math.min(pageCount - 1, active));
        this.standalone = standalone;
        this.columns = QuadHotbarOverviewLayout.normalizeWidth(columns);
        this.viewportRows = Math.max(1, Math.min(50, height));
        this.pageSlots = pageCount * 36;
        this.inventoryMenu = cursorMenu;
        Container storage =
                external ? externalStorage : new PageInventory(inventory.player, pageSlots);
        for (int index = 0; index < pageSlots; index++) {
            addSlot(new OverviewSlot(storage, index, death));
        }
        if (!standalone && !external) {
            // Keep vanilla crafting, armor and offhand semantics. Never reuse the Slot
            // instances themselves: addSlot would overwrite their vanilla menu indices.
            for (Slot slot : inventoryMenu.slots) addSlot(new InventoryAliasSlot(slot));
        }
    }

    public int canonicalSlot(int index) {
        int vanilla = index - pageSlots;
        if (index >= pageSlots && index < slots.size() && vanilla >= 9 && vanilla < 45)
            return activePage * 36 + (vanilla < 36 ? vanilla : vanilla - 36);
        return index;
    }

    // One cursor, shared by the genuine vanilla inventory and the overview.
    @Override
    public ItemStack getCarried() {
        return inventoryMenu.getCarried();
    }

    @Override
    public void setCarried(ItemStack stack) {
        inventoryMenu.setCarried(stack);
    }

    @Override
    public void clicked(int index, int button, ClickType type, Player player) {
        if (!stillValid(player)) return;
        if (index >= slots.size() || (index < 0 && index != -999 && index != -1)) return;
        if (index >= pageSlots && type != ClickType.QUICK_CRAFT) {
            inventoryMenu.clicked(index - pageSlots, button, type, player);
            return;
        }
        int canonical = canonicalSlot(index);
        // The docked inventory is a second view of the same slots, not a second storage.
        // Canonicalizing drag clicks prevents the same physical slot joining a drag twice.
        if (!deathInventory
                && type == ClickType.SWAP
                && button >= 0
                && button < 9
                && canonical == activePage * 36 + button) return;
        super.clicked(canonical, button, type, player);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.index < pageSlots;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        index = canonicalSlot(index);
        if (index < 0 || index >= pageSlots) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        ItemStack stack = source.getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack before = stack.copy();
        if (external) {
            if (deathInventory) {
                QuadHotbarPages.addPickup(player, stack);
            } else {
                // Shift-click deposits into the original container, honoring its own Slot rules.
                int furnaceTarget = -1;
                if (inventoryMenu instanceof AbstractFurnaceMenu) {
                    var furnace = (FurnaceMenuAccessor) inventoryMenu;
                    furnaceTarget =
                            furnace.quadhotbar$canSmelt(stack)
                                    ? 0
                                    : furnace.quadhotbar$isFuel(stack) ? 1 : -1;
                }
                for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++)
                    for (Slot destination : inventoryMenu.slots) {
                        if (stack.isEmpty()) break;
                        if (destination.container == player.getInventory()
                                || !destination.mayPlace(stack)) continue;
                        if (inventoryMenu instanceof AbstractFurnaceMenu
                                && destination.index != furnaceTarget) continue;
                        ItemStack existing = destination.getItem();
                        if (pass == 0
                                && !existing.isEmpty()
                                && ItemStack.isSameItemSameTags(existing, stack)) {
                            int moved =
                                    Math.min(
                                            stack.getCount(),
                                            destination.getMaxStackSize(stack)
                                                    - existing.getCount());
                            if (moved > 0) {
                                existing.grow(moved);
                                stack.shrink(moved);
                                destination.setChanged();
                            }
                        } else if (pass == 1 && existing.isEmpty()) {
                            destination.setByPlayer(
                                    stack.split(
                                            Math.min(
                                                    stack.getCount(),
                                                    destination.getMaxStackSize(stack))));
                        }
                    }
            }
            if (before.getCount() == stack.getCount()) return ItemStack.EMPTY;
            if (stack.isEmpty()) source.setByPlayer(ItemStack.EMPTY);
            else source.setChanged();
            source.onTake(player, stack);
            return before;
        }
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int target = 0; target < pageSlots && !stack.isEmpty(); target++) {
                if (target == index) continue;
                Slot destination = slots.get(target);
                ItemStack existing = destination.getItem();
                if (!destination.mayPlace(stack)) continue;
                if (pass == 0
                        && !existing.isEmpty()
                        && ItemStack.isSameItemSameTags(existing, stack)) {
                    int moved =
                            Math.min(
                                    stack.getCount(),
                                    destination.getMaxStackSize(stack) - existing.getCount());
                    if (moved > 0) {
                        existing.grow(moved);
                        stack.shrink(moved);
                        destination.setChanged();
                    }
                } else if (pass == 1 && existing.isEmpty()) {
                    destination.setByPlayer(
                            stack.split(
                                    Math.min(
                                            stack.getCount(), destination.getMaxStackSize(stack))));
                }
            }
        }
        if (stack.getCount() == before.getCount()) return ItemStack.EMPTY;
        if (stack.isEmpty()) source.setByPlayer(ItemStack.EMPTY);
        else source.setChanged();
        source.onTake(player, stack);
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!player.isAlive()) return false;
        if (!player.level().isClientSide && !QuadHotbarServerConfig.permitsCurrentPages(player))
            return false;
        if (external)
            return player.level().isClientSide
                    || (player.containerMenu == inventoryMenu
                            && inventoryMenu.stillValid(player)
                            && QuadHotbarServerConfig.forPlayer(player).allowContainerOverview()
                            && (deathInventory
                                    || (QuadHotbarPages.state(player).pageCount == pageCount
                                            && QuadHotbarPages.state(player).activePage
                                                    == activePage)));
        return player.level().isClientSide
                || (QuadHotbarServerConfig.forPlayer(player).allowPageOverview()
                        && QuadHotbarPages.state(player).pageCount == pageCount
                        && QuadHotbarPages.state(player).activePage == activePage);
    }

    @Override
    public void removed(Player player) {
        if (external) return;
        super.removed(player);
        if (!returningToInventory) inventoryMenu.removed(player);
        if (player instanceof ServerPlayer server) {
            player.inventoryMenu.broadcastFullState();
            QuadHotbarNetwork.sendPageSync(server);
        }
    }

    public static final class OverviewSlot extends Slot {
        public boolean visible = true;
        private final boolean readOnlyStorage;

        private OverviewSlot(Container container, int index, boolean readOnlyStorage) {
            super(container, index, -10000, -10000);
            this.readOnlyStorage = readOnlyStorage;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !readOnlyStorage;
        }

        @Override
        public boolean isActive() {
            return visible;
        }
    }

    private static final class InventoryAliasSlot extends Slot {
        private final Slot target;

        private InventoryAliasSlot(Slot target) {
            super(target.container, target.getSlotIndex(), -10000, -10000);
            this.target = target;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public ItemStack getItem() {
            return target.getItem();
        }

        @Override
        public void set(ItemStack stack) {
            target.set(stack);
        }

        @Override
        public void setByPlayer(ItemStack stack) {
            target.setByPlayer(stack);
        }

        @Override
        public void setChanged() {
            target.setChanged();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return target.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return target.mayPickup(player);
        }

        @Override
        public int getMaxStackSize() {
            return target.getMaxStackSize();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return target.getMaxStackSize(stack);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            target.onTake(player, stack);
        }
    }

    private static final class PageInventory implements Container {
        private final Player player;
        private final int size;

        private PageInventory(Player player, int size) {
            this.player = player;
            this.size = size;
        }

        @Override
        public int getContainerSize() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < size; i++) if (!getItem(i).isEmpty()) return false;
            return true;
        }

        @Override
        public ItemStack getItem(int index) {
            return QuadHotbarPages.getPageItem(player, index / 36, index % 36);
        }

        @Override
        public ItemStack removeItem(int index, int amount) {
            ItemStack result = getItem(index).split(amount);
            if (!result.isEmpty()) setChanged();
            return result;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            ItemStack stack = getItem(index);
            setItem(index, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            QuadHotbarPages.setPageItem(player, index / 36, index % 36, stack);
        }

        @Override
        public void setChanged() {
            player.getInventory().setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return this.player == player && player.isAlive();
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < size; i++) setItem(i, ItemStack.EMPTY);
        }
    }
}
