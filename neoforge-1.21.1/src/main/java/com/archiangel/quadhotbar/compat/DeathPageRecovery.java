package com.archiangel.quadhotbar.compat;

import com.archiangel.quadhotbar.QuadHotbarNetwork;
import com.archiangel.quadhotbar.QuadHotbarPages;
import com.archiangel.quadhotbar.QuadHotbarServerConfig;
import com.mojang.logging.LogUtils;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Extends the death mods' original-slot recovery; their lists remain the sole item owner. */
public final class DeathPageRecovery {
    private DeathPageRecovery() {}

    /** Null means that the caller must retain the death mod's unmodified behavior. */
    public static NonNullList<ItemStack> restore(Player player, Object death, boolean main) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || !QuadHotbarServerConfig.deathPageCompatibility()
                || !(death instanceof DeathPageAccess access)) return null;
        int active = access.quadhotbar$activePage();
        int[] addresses = access.quadhotbar$pageSlots();
        if (active < 0 && java.util.Arrays.stream(addresses).noneMatch(DeathPageRecovery::valid))
            return null;
        List<ItemStack> additional, inventory, armor, offhand;
        try {
            // Resolve every optional API before moving any item. A missing API can
            // safely fall back to native recovery without partially performing it.
            additional = items(death, "getAdditionalItems");
            inventory = main ? items(death, "getMainInventory") : List.of();
            armor = main ? items(death, "getArmorInventory") : List.of();
            offhand = main ? items(death, "getOffHandInventory") : List.of();
        } catch (ReflectiveOperationException exception) {
            LogUtils.getLogger().warn("Could not adapt death inventory recovery", exception);
            return null;
        }
        QuadHotbarNetwork.enforceServerLimits(serverPlayer);
        NonNullList<ItemStack> displaced = NonNullList.create();
        if (main) {
            int page = active >= 0 ? active : QuadHotbarPages.state(player).activePage;
            for (int slot = 0; slot < Math.min(inventory.size(), QuadHotbarPages.PAGE_SIZE); slot++)
                restoreSlot(
                        player,
                        inventory,
                        slot,
                        page * QuadHotbarPages.PAGE_SIZE + slot,
                        displaced);
            restoreEquipment(armor, player.getInventory().armor, displaced);
            restoreEquipment(offhand, player.getInventory().offhand, displaced);
        }
        for (int i = 0; i < additional.size(); i++) {
            int address = i < addresses.length ? addresses[i] : -1;
            if (valid(address)) restoreSlot(player, additional, i, address, displaced);
        }
        // First recover every original slot, then distribute unrelated/displaced
        // items. Otherwise an early pickup can occupy a slot being restored later.
        NonNullList<ItemStack> remainder = NonNullList.create();
        for (var stack : displaced) collectRemainder(player, stack, remainder);
        for (var stack : additional) collectRemainder(player, stack, remainder);
        // An already open CorpseAdditionalContainer has slots bound to list indices.
        // Keep empty entries until its native NBT save compacts them, or broadcasting
        // that same container would read past the newly shortened list.
        for (int i = 0; i < additional.size(); i++) additional.set(i, ItemStack.EMPTY);
        for (int i = 0; i < remainder.size(); i++) {
            if (i < additional.size()) additional.set(i, remainder.get(i));
            else additional.add(remainder.get(i));
        }
        int[] remainingAddresses = new int[additional.size()];
        java.util.Arrays.fill(remainingAddresses, -1);
        access.quadhotbar$setPageSlots(remainingAddresses);
        player.getInventory().setChanged();
        QuadHotbarNetwork.sendPageSync(serverPlayer);
        return remainder;
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> items(Object death, String method)
            throws ReflectiveOperationException {
        return (List<ItemStack>) death.getClass().getMethod(method).invoke(death);
    }

    private static boolean valid(int address) {
        return address >= 0 && address < QuadHotbarPages.MAX_PAGES * QuadHotbarPages.PAGE_SIZE;
    }

    private static void restoreSlot(
            Player player,
            List<ItemStack> source,
            int index,
            int address,
            List<ItemStack> displaced) {
        ItemStack stack = source.get(index);
        if (stack.isEmpty()) return;
        int page = address / QuadHotbarPages.PAGE_SIZE, slot = address % QuadHotbarPages.PAGE_SIZE;
        ItemStack previous = QuadHotbarPages.getPageItem(player, page, slot);
        source.set(index, ItemStack.EMPTY);
        // Recovery may refill a previously hidden page, but never enables it or
        // raises the server's page cap. Only the server-owned death addresses are used.
        QuadHotbarPages.setPageItem(player, page, slot, stack);
        if (!previous.isEmpty() && previous != stack) displaced.add(previous);
    }

    private static void restoreEquipment(
            List<ItemStack> source, List<ItemStack> target, List<ItemStack> displaced) {
        for (int slot = 0; slot < Math.min(source.size(), target.size()); slot++) {
            ItemStack stack = source.get(slot);
            if (stack.isEmpty()) continue;
            ItemStack previous = target.get(slot);
            source.set(slot, ItemStack.EMPTY);
            target.set(slot, stack);
            if (!previous.isEmpty() && previous != stack) displaced.add(previous);
        }
    }

    private static void collectRemainder(
            Player player, ItemStack stack, NonNullList<ItemStack> remainder) {
        if (stack.isEmpty()) return;
        QuadHotbarPages.addPickup(player, stack);
        if (!stack.isEmpty()) remainder.add(stack);
    }
}
