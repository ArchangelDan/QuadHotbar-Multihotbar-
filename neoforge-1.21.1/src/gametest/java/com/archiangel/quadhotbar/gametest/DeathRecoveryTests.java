package com.archiangel.quadhotbar.gametest;

import com.archiangel.quadhotbar.QuadHotbarPages;
import com.archiangel.quadhotbar.compat.DeathPageAccess;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("quadhotbar")
@PrefixGameTestTemplate(false)
public final class DeathRecoveryTests {
    private static String installed() {
        if (ModList.get().isLoaded("corpse")) return "corpse";
        if (ModList.get().isLoaded("gravestone")) return "gravestone";
        return null;
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "recovery-test");
        var player =
                new ServerPlayer(
                        helper.getLevel().getServer(),
                        helper.getLevel(),
                        profile,
                        ClientInformation.createDefault());
        player.connection = new FakePlayer(helper.getLevel(), profile).connection;
        QuadHotbarPages.state(player).pageCount = 3;
        QuadHotbarPages.state(player).hotbarRows = 4;
        return player;
    }

    private static Object death(ServerPlayer player, String mod) throws Exception {
        var type = Class.forName("de.maxhenkel." + mod + ".corelib.death.Death");
        Object death = type.getMethod("fromPlayer", Player.class).invoke(null, player);
        player.captureDrops(new ArrayList<ItemEntity>());
        player.getInventory().dropAll();
        type.getMethod("processDrops", java.util.Collection.class)
                .invoke(death, player.captureDrops(null));
        // Exercise the real compacting NBT format, not just in-memory addresses.
        var tag =
                (CompoundTag)
                        type.getMethod("toNBT", HolderLookup.Provider.class)
                                .invoke(death, player.registryAccess());
        return type.getMethod("fromNBT", HolderLookup.Provider.class, CompoundTag.class)
                .invoke(null, player.registryAccess(), tag);
    }

    private static AbstractContainerMenu corpseMenu(ServerPlayer player, Object death, boolean main)
            throws Exception {
        var entityType = Class.forName("de.maxhenkel.corpse.entities.CorpseEntity");
        Object corpse =
                entityType
                        .getMethod("createFromDeath", Player.class, death.getClass())
                        .invoke(null, player, death);
        var menuType =
                Class.forName(
                        "de.maxhenkel.corpse.gui."
                                + (main
                                        ? "CorpseInventoryContainer"
                                        : "CorpseAdditionalContainer"));
        var menu =
                (AbstractContainerMenu)
                        menuType.getConstructor(
                                        int.class,
                                        Inventory.class,
                                        entityType,
                                        boolean.class,
                                        boolean.class)
                                .newInstance(12, player.getInventory(), corpse, true, true);
        player.containerMenu = menu;
        return menu;
    }

    private static List<ItemStack> recover(ServerPlayer player, Object death, String mod)
            throws Exception {
        if (mod.equals("corpse")) {
            var menu = corpseMenu(player, death, true);
            menu.getClass().getMethod("transferItems").invoke(menu);
            return items(death, "getAdditionalItems");
        }
        Object holder =
                Class.forName("de.maxhenkel.gravestone.Main").getField("GRAVESTONE").get(null);
        Object block = holder.getClass().getMethod("get").invoke(holder);
        @SuppressWarnings("unchecked")
        var overflow =
                (List<ItemStack>)
                        block.getClass()
                                .getMethod("fillPlayerInventory", Player.class, death.getClass())
                                .invoke(block, player, death);
        return overflow;
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> items(Object death, String method) throws Exception {
        return (List<ItemStack>) death.getClass().getMethod(method).invoke(death);
    }

    @GameTest(template = "empty", batch = "overview")
    public static void nativeRecoveryRestoresOriginalPagesAndPreservesOverflow(
            GameTestHelper helper) throws Exception {
        String mod = installed();
        if (mod == null) {
            helper.succeed();
            return;
        }
        var player = player(helper);
        QuadHotbarPages.switchActivePage(player, 1);
        QuadHotbarPages.setPageItem(player, 1, 9, new ItemStack(Items.IRON_INGOT, 5));
        QuadHotbarPages.setPageItem(player, 1, 7, new ItemStack(Items.OAK_LOG, 9));
        QuadHotbarPages.setPageItem(player, 0, 0, new ItemStack(Items.GOLD_INGOT, 7));
        QuadHotbarPages.setPageItem(player, 2, 17, new ItemStack(Items.DIAMOND, 13));
        QuadHotbarPages.setPageItem(player, 99, 35, new ItemStack(Items.EMERALD, 2));
        player.getInventory().armor.set(0, new ItemStack(Items.IRON_BOOTS));
        player.getInventory().offhand.set(0, new ItemStack(Items.SHIELD));
        Object death = death(player, mod);
        QuadHotbarPages.state(player).pageCount = 1;
        QuadHotbarPages.switchActivePage(player, 0);
        for (int slot = 0; slot < 36; slot++)
            QuadHotbarPages.setPageItem(player, 0, slot, new ItemStack(Items.STONE, 64));
        QuadHotbarPages.setPageItem(player, 1, 9, new ItemStack(Items.COPPER_INGOT, 3));
        QuadHotbarPages.setPageItem(player, 2, 17, new ItemStack(Items.LAPIS_LAZULI, 2));
        QuadHotbarPages.setPageItem(player, 99, 35, new ItemStack(Items.COAL, 4));
        player.getInventory().armor.set(0, new ItemStack(Items.NETHERITE_BOOTS));
        List<ItemStack> overflow = recover(player, death, mod);
        helper.assertTrue(
                QuadHotbarPages.getPageItem(player, 0, 0).is(Items.GOLD_INGOT)
                        && QuadHotbarPages.getPageItem(player, 0, 0).getCount() == 7
                        && QuadHotbarPages.getPageItem(player, 1, 9).is(Items.IRON_INGOT)
                        && QuadHotbarPages.getPageItem(player, 1, 9).getCount() == 5
                        && QuadHotbarPages.getPageItem(player, 1, 7).getCount() == 9
                        && QuadHotbarPages.getPageItem(player, 2, 17).is(Items.DIAMOND)
                        && QuadHotbarPages.getPageItem(player, 2, 17).getCount() == 13
                        && QuadHotbarPages.getPageItem(player, 99, 35).is(Items.EMERALD)
                        && QuadHotbarPages.getPageItem(player, 99, 35).getCount() == 2,
                "Native " + mod + " recovery did not restore original page/slot addresses");
        helper.assertTrue(
                player.getInventory().armor.get(0).is(Items.IRON_BOOTS)
                        && player.getInventory().offhand.get(0).is(Items.SHIELD)
                        && QuadHotbarPages.state(player).activePage == 0
                        && QuadHotbarPages.state(player).pageCount == 1,
                "Recovery changed page limits/current page or broke native equipment recovery");
        helper.assertTrue(
                count(overflow, Items.STONE) == 64
                        && count(overflow, Items.COPPER_INGOT) == 3
                        && count(overflow, Items.LAPIS_LAZULI) == 2
                        && count(overflow, Items.COAL) == 4
                        && count(overflow, Items.NETHERITE_BOOTS) == 1
                        && overflow.stream().mapToInt(ItemStack::getCount).sum() == 74,
                "Occupied-slot recovery lost/duplicated displaced items");
        helper.assertTrue(
                items(death, "getMainInventory").stream().allMatch(ItemStack::isEmpty),
                "Death retained a second copy of restored main-page items");
        if (mod.equals("corpse")) {
            helper.assertTrue(
                    java.util.Arrays.stream(((DeathPageAccess) death).quadhotbar$pageSlots())
                            .allMatch(i -> i == -1),
                    "Displaced items inherited old death addresses");
            var additional = corpseMenu(player, death, false);
            additional.getClass().getMethod("transferItems").invoke(additional);
            helper.assertTrue(
                    items(death, "getAdditionalItems").stream().mapToInt(ItemStack::getCount).sum()
                            == 74,
                    "Repeated transfer destroyed overflow in a full inventory");
            for (int slot = 0; slot < 36; slot++)
                QuadHotbarPages.setPageItem(player, 0, slot, ItemStack.EMPTY);
            additional.getClass().getMethod("transferItems").invoke(additional);
            helper.assertTrue(
                    items(death, "getAdditionalItems").stream().allMatch(ItemStack::isEmpty),
                    "Corpse could not transfer displaced items once room became available");
        } else {
            helper.assertTrue(
                    items(death, "getAdditionalItems").isEmpty(),
                    "GraveStone retained a second copy of the overflow returned for dropping");
            helper.assertTrue(
                    recover(player, death, mod).isEmpty(),
                    "Repeated grave recovery returned a duplicate item");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void legacyDeathWithoutPageMetadataKeepsNativeRecovery(GameTestHelper helper)
            throws Exception {
        String mod = installed();
        if (mod == null) {
            helper.succeed();
            return;
        }
        var player = player(helper);
        QuadHotbarPages.switchActivePage(player, 1);
        QuadHotbarPages.setPageItem(player, 1, 9, new ItemStack(Items.IRON_INGOT, 5));
        QuadHotbarPages.setPageItem(player, 2, 17, new ItemStack(Items.DIAMOND, 13));
        Object death = death(player, mod);
        ((DeathPageAccess) death).quadhotbar$setActivePage(-1);
        ((DeathPageAccess) death).quadhotbar$setPageSlots(new int[0]);
        QuadHotbarPages.state(player).pageCount = 1;
        QuadHotbarPages.switchActivePage(player, 0);
        var overflow = recover(player, death, mod);
        helper.assertTrue(
                QuadHotbarPages.getPageItem(player, 0, 9).is(Items.IRON_INGOT)
                        && QuadHotbarPages.getPageItem(player, 0, 9).getCount() == 5
                        && QuadHotbarPages.getPageItem(player, 1, 9).isEmpty()
                        && QuadHotbarPages.getPageItem(player, 2, 17).isEmpty()
                        && count(player.getInventory().items, Items.DIAMOND) == 13
                        && overflow.stream().allMatch(ItemStack::isEmpty),
                "An old death without page metadata lost its native recovery behavior");
        helper.succeed();
    }

    private static int count(List<ItemStack> items, net.minecraft.world.item.Item item) {
        return items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
}
