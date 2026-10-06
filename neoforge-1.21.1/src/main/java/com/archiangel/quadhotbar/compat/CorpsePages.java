package com.archiangel.quadhotbar.compat;

import com.archiangel.quadhotbar.QuadHotbarPages;
import com.archiangel.quadhotbar.QuadHotbarServerConfig;
import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;

/** Only adapts a Corpse menu that has already granted access; never searches arbitrary entities. */
public final class CorpsePages {
    private CorpsePages() {}

    public record View(Container storage, int pages, boolean editable) {}

    public static boolean transfer(AbstractContainerMenu menu, ServerPlayer player) {
        if (player.containerMenu != menu
                || !menu.stillValid(player)
                || !QuadHotbarServerConfig.deathPageCompatibility()) return false;
        try {
            if (!(boolean) menu.getClass().getMethod("isEditable").invoke(menu)) return false;
            Object corpse = menu.getClass().getMethod("getCorpse").invoke(menu);
            Object death = corpse.getClass().getMethod("getDeath").invoke(corpse);
            boolean main = menu.getClass().getName().endsWith(".CorpseInventoryContainer");
            var openAdditional =
                    main
                            ? Class.forName("de.maxhenkel.corpse.gui.Guis")
                                    .getMethod(
                                            "openAdditionalItems",
                                            ServerPlayer.class,
                                            menu.getClass())
                            : null;
            var remainder = DeathPageRecovery.restore(player, death, main);
            if (remainder == null) return false;
            menu.broadcastFullState();
            if (main && !remainder.isEmpty()) openAdditional.invoke(null, player, menu);
            return true;
        } catch (ReflectiveOperationException exception) {
            LogUtils.getLogger()
                    .warn("Could not adapt Corpse take-all; using its native transfer", exception);
            return false;
        }
    }

    public static View find(AbstractContainerMenu menu) {
        if (!QuadHotbarServerConfig.deathPageCompatibility()
                || !menu.getClass().getName().startsWith("de.maxhenkel.corpse.gui.")) return null;
        try {
            Object corpse = menu.getClass().getMethod("getCorpse").invoke(menu);
            Object death = corpse.getClass().getMethod("getDeath").invoke(corpse);
            if (!(death instanceof DeathPageAccess access)) return null;
            @SuppressWarnings("unchecked")
            var items =
                    (List<ItemStack>)
                            death.getClass().getMethod("getAdditionalItems").invoke(death);
            int[] positions = access.quadhotbar$pageSlots();
            int pages = 0;
            for (int i = 0; i < Math.min(items.size(), positions.length); i++)
                if (!items.get(i).isEmpty() && positions[i] >= 0)
                    pages = Math.max(pages, positions[i] / 36 + 1);
            if (pages == 0) return null;
            @SuppressWarnings("unchecked")
            var main =
                    (List<ItemStack>) death.getClass().getMethod("getMainInventory").invoke(death);
            int active = access.quadhotbar$activePage();
            if (active >= 0) pages = Math.max(pages, active + 1);
            pages = Math.min(pages, QuadHotbarPages.MAX_PAGES);
            boolean editable = (boolean) menu.getClass().getMethod("isEditable").invoke(menu);
            return new View(
                    new DeathContainer(items, positions, main, active, pages * 36, menu),
                    pages,
                    editable);
        } catch (ReflectiveOperationException exception) {
            LogUtils.getLogger()
                    .warn(
                            "Could not adapt this Corpse inventory; its native item GUI remains"
                                    + " available",
                            exception);
            return null;
        }
    }

    private static final class DeathContainer implements Container {
        private final List<ItemStack> items;
        private final int[] indices;
        private final ItemStack[] identities;
        private final AbstractContainerMenu parent;
        private final List<ItemStack> main;
        private final int active;

        DeathContainer(
                List<ItemStack> items,
                int[] positions,
                List<ItemStack> main,
                int active,
                int size,
                AbstractContainerMenu parent) {
            this.items = items;
            this.parent = parent;
            this.main = main;
            this.active = active;
            indices = new int[size];
            Arrays.fill(indices, -1);
            identities = new ItemStack[size];
            for (int i = 0; i < Math.min(items.size(), positions.length); i++) {
                int address = positions[i];
                if (address >= 0 && address < size && indices[address] < 0) {
                    indices[address] = i;
                    identities[address] = items.get(i);
                }
            }
        }

        public int getContainerSize() {
            return indices.length;
        }

        public boolean isEmpty() {
            for (int i = 0; i < indices.length; i++) if (!getItem(i).isEmpty()) return false;
            return true;
        }

        public ItemStack getItem(int slot) {
            if (isMainSlot(slot)) return main.get(slot % 36);
            int index = slot >= 0 && slot < indices.length ? indices[slot] : -1;
            return index >= 0 && index < items.size() && items.get(index) == identities[slot]
                    ? items.get(index)
                    : ItemStack.EMPTY;
        }

        public ItemStack removeItem(int slot, int amount) {
            return getItem(slot).split(amount);
        }

        public ItemStack removeItemNoUpdate(int slot) {
            var stack = getItem(slot);
            setItem(slot, ItemStack.EMPTY);
            return stack;
        }

        public void setItem(int slot, ItemStack stack) {
            if (isMainSlot(slot)) {
                main.set(slot % 36, stack);
                return;
            }
            if (slot >= 0
                    && slot < indices.length
                    && indices[slot] >= 0
                    && indices[slot] < items.size()
                    && items.get(indices[slot]) == identities[slot]) {
                items.set(indices[slot], stack);
                identities[slot] = stack;
            }
        }

        public void setChanged() {}

        private boolean isMainSlot(int slot) {
            // Main-inventory slots are live aliases, not copied items. Native Corpse
            // clicks may replace their stacks, so always read the canonical list.
            return slot >= 0
                    && slot < indices.length
                    && indices[slot] < 0
                    && active >= 0
                    && slot / 36 == active
                    && slot % 36 < main.size();
        }

        public boolean stillValid(Player player) {
            return player.containerMenu == parent && parent.stillValid(player);
        }

        public void clearContent() {
            for (int i = 0; i < indices.length; i++) setItem(i, ItemStack.EMPTY);
        }
    }
}
