package com.archiangel.quadhotbar.gametest;

import com.archiangel.quadhotbar.*;
import com.archiangel.quadhotbar.compat.DeathPageAccess;
import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder("quadhotbar")
@PrefixGameTestTemplate(false)
public final class ContainerOverviewTests {
    private static ServerPlayer player(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "container-test");
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

    private static void action(ServerPlayer player, QuadHotbarContainerOverview.Action action)
            throws Exception {
        var method =
                QuadHotbarContainerOverview.class.getDeclaredMethod(
                        "handle", ServerPlayer.class, QuadHotbarContainerOverview.Action.class);
        method.setAccessible(true);
        method.invoke(null, player, action);
    }

    @SuppressWarnings("unchecked")
    private static Object session(ServerPlayer player) throws Exception {
        var field = QuadHotbarContainerOverview.class.getDeclaredField("SESSIONS");
        field.setAccessible(true);
        return ((Map<UUID, ?>) field.get(null)).get(player.getUUID());
    }

    private static int token(Object session) throws Exception {
        var field = session.getClass().getDeclaredField("token");
        field.setAccessible(true);
        return field.getInt(session);
    }

    @GameTest(template = "empty", batch = "overview")
    public static void sidecarKeepsChestCursorAndRejectsStaleActions(GameTestHelper helper)
            throws Exception {
        var player = player(helper);
        var chest = new SimpleContainer(27);
        var parent = ChestMenu.threeRows(7, player.getInventory(), chest);
        player.containerMenu = parent;
        parent.setCarried(new ItemStack(Items.DIAMOND, 13));
        action(player, new QuadHotbarContainerOverview.Action(0, 7, 0, -1, 0, ClickType.PICKUP, 9));
        Object session = session(player);
        helper.assertTrue(
                session != null
                        && player.containerMenu == parent
                        && parent.getCarried().getCount() == 13,
                "Opening overview replaced chest or dropped its cursor");
        int token = token(session);
        action(player, new QuadHotbarContainerOverview.Action(0, 7, 0, -1, 0, ClickType.PICKUP, 9));
        helper.assertTrue(
                token(session(player)) == token, "Duplicate open replaced the live session token");
        action(
                player,
                new QuadHotbarContainerOverview.Action(
                        1, 7, token, 2 * 36 + 10, 0, ClickType.PICKUP, 0));
        helper.assertTrue(
                QuadHotbarPages.getPageItem(player, 2, 10).getCount() == 13
                        && parent.getCarried().isEmpty(),
                "Chest cursor did not move to the page");
        action(
                player,
                new QuadHotbarContainerOverview.Action(
                        1, 7, token, 2 * 36 + 10, 0, ClickType.QUICK_MOVE, 0));
        helper.assertTrue(
                chest.getItem(0).getCount() == 13
                        && QuadHotbarPages.getPageItem(player, 2, 10).isEmpty(),
                "Shift-click did not deposit page contents into the original chest");
        QuadHotbarPages.setPageItem(player, 2, 10, new ItemStack(Items.GOLD_INGOT, 3));
        action(
                player,
                new QuadHotbarContainerOverview.Action(
                        1, 7, token + 1, 2 * 36 + 10, 0, ClickType.PICKUP, 0));
        helper.assertTrue(parent.getCarried().isEmpty(), "Stale session token was accepted");
        player.containerMenu = player.inventoryMenu;
        action(
                player,
                new QuadHotbarContainerOverview.Action(
                        1, 7, token, 2 * 36 + 10, 0, ClickType.PICKUP, 0));
        helper.assertTrue(
                QuadHotbarPages.getPageItem(player, 2, 10).getCount() == 3,
                "Closed chest session remained writable");
        action(
                player,
                new QuadHotbarContainerOverview.Action(2, 7, token, -1, 0, ClickType.PICKUP, 0));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void sidecarHonorsFurnaceSlotRules(GameTestHelper helper) {
        var player = player(helper);
        var furnace = new SimpleContainer(3);
        var parent = new FurnaceMenu(8, player.getInventory(), furnace, new SimpleContainerData(4));
        player.containerMenu = parent;
        var menu =
                QuadHotbarOverviewMenu.external(
                        8,
                        player.getInventory(),
                        3,
                        0,
                        9,
                        parent,
                        QuadHotbarOverviewMenu.playerPages(player, 3),
                        false);
        QuadHotbarPages.setPageItem(player, 2, 9, new ItemStack(Items.COAL, 6));
        menu.quickMoveStack(player, 2 * 36 + 9);
        helper.assertTrue(
                furnace.getItem(1).is(Items.COAL)
                        && furnace.getItem(1).getCount() == 6
                        && furnace.getItem(2).isEmpty(),
                "Coal bypassed fuel/result Slot rules");
        helper.assertTrue(player.containerMenu == parent, "Furnace menu was replaced");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void deathAddressesSurvivePartialLootAndSerialization(GameTestHelper helper)
            throws Exception {
        if (!ModList.get().isLoaded("corpse") && !ModList.get().isLoaded("gravestone")) {
            helper.succeed();
            return;
        }
        var player = player(helper);
        QuadHotbarPages.switchActivePage(player, 1);
        player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 5));
        QuadHotbarPages.setPageItem(player, 0, 9, new ItemStack(Items.GOLD_INGOT, 7));
        QuadHotbarPages.setPageItem(player, 2, 35, new ItemStack(Items.DIAMOND, 11));
        QuadHotbarPages.setPageItem(player, 99, 35, new ItemStack(Items.EMERALD, 2));
        String mod = ModList.get().isLoaded("corpse") ? "corpse" : "gravestone";
        Class<?> type = Class.forName("de.maxhenkel." + mod + ".corelib.death.Death");
        Object death = type.getMethod("fromPlayer", Player.class).invoke(null, player);
        player.captureDrops(new ArrayList<ItemEntity>());
        player.getInventory().dropAll();
        var drops = player.captureDrops(null);
        type.getMethod("processDrops", java.util.Collection.class).invoke(death, drops);
        @SuppressWarnings("unchecked")
        var extra = (List<ItemStack>) type.getMethod("getAdditionalItems").invoke(death);
        helper.assertTrue(
                extra.stream().mapToInt(ItemStack::getCount).sum() == 20,
                "Corpse lost/duplicated inactive or hidden page drops");
        helper.assertTrue(death instanceof DeathPageAccess, "Optional death mixin did not apply");
        int[] addresses = ((DeathPageAccess) death).quadhotbar$pageSlots();
        helper.assertTrue(
                java.util.Arrays.stream(addresses).anyMatch(i -> i == 99 * 36 + 35),
                "Hidden page slot address was lost");
        for (int page : new int[] {0, 2, 99})
            for (int slot = 0; slot < 36; slot++)
                helper.assertTrue(
                        QuadHotbarPages.getPageItem(player, page, slot).isEmpty(),
                        "Dead player retained a second item copy");
        // Native Corpse serialization removes empty list entries. Addresses must compact with it.
        extra.set(0, ItemStack.EMPTY);
        int remainingAddress = addresses[1];
        var tag =
                (net.minecraft.nbt.CompoundTag)
                        type.getMethod("toNBT", net.minecraft.core.HolderLookup.Provider.class)
                                .invoke(death, helper.getLevel().registryAccess());
        Object restored =
                type.getMethod(
                                "fromNBT",
                                net.minecraft.core.HolderLookup.Provider.class,
                                net.minecraft.nbt.CompoundTag.class)
                        .invoke(null, helper.getLevel().registryAccess(), tag);
        helper.assertTrue(
                ((DeathPageAccess) restored).quadhotbar$pageSlots()[0] == remainingAddress,
                "Partial corpse loot shifted addresses on server save/reload");
        helper.assertTrue(
                ((DeathPageAccess) restored).quadhotbar$activePage() == 1,
                "Death inventory lost the original active page on save/reload");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void corpseSidecarUsesCorpseItemsAndHonorsReadOnlyAccess(GameTestHelper helper)
            throws Exception {
        if (!ModList.get().isLoaded("corpse")) {
            helper.succeed();
            return;
        }
        var player = player(helper);
        player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 5));
        QuadHotbarPages.setPageItem(player, 2, 17, new ItemStack(Items.DIAMOND, 13));
        Class<?> deathType = Class.forName("de.maxhenkel.corpse.corelib.death.Death");
        Object death = deathType.getMethod("fromPlayer", Player.class).invoke(null, player);
        player.captureDrops(new ArrayList<ItemEntity>());
        player.getInventory().dropAll();
        deathType
                .getMethod("processDrops", java.util.Collection.class)
                .invoke(death, player.captureDrops(null));
        Class<?> corpseType = Class.forName("de.maxhenkel.corpse.entities.CorpseEntity");
        Object corpse =
                corpseType
                        .getMethod("createFromDeath", Player.class, deathType)
                        .invoke(null, player, death);
        Class<?> menuType = Class.forName("de.maxhenkel.corpse.gui.CorpseInventoryContainer");
        var constructor =
                menuType.getConstructor(
                        int.class,
                        net.minecraft.world.entity.player.Inventory.class,
                        corpseType,
                        boolean.class,
                        boolean.class);
        var parent =
                (net.minecraft.world.inventory.AbstractContainerMenu)
                        constructor.newInstance(9, player.getInventory(), corpse, true, false);
        player.containerMenu = parent;
        action(player, new QuadHotbarContainerOverview.Action(0, 9, 0, -1, 0, ClickType.PICKUP, 9));
        Object session = session(player);
        helper.assertTrue(session != null, "Corpse sidecar did not open");
        int token = token(session);
        var corpseView = com.archiangel.quadhotbar.compat.CorpsePages.find(parent);
        @SuppressWarnings("unchecked")
        var main = (List<ItemStack>) deathType.getMethod("getMainInventory").invoke(death);
        helper.assertTrue(
                corpseView.storage().getItem(9) == main.get(9),
                "Corpse active page was copied instead of aliased");
        main.set(9, new ItemStack(Items.IRON_INGOT, 3));
        helper.assertTrue(
                corpseView.storage().getItem(9).getCount() == 3,
                "Native corpse looting did not update its active overview page");
        action(
                player,
                new QuadHotbarContainerOverview.Action(
                        1, 9, token, 2 * 36 + 17, 0, ClickType.PICKUP, 0));
        helper.assertTrue(
                parent.getCarried().is(Items.DIAMOND) && parent.getCarried().getCount() == 13,
                "Corpse sidecar read the live player's pages instead of the corpse");
        @SuppressWarnings("unchecked")
        var items = (List<ItemStack>) deathType.getMethod("getAdditionalItems").invoke(death);
        helper.assertTrue(
                items.stream().allMatch(ItemStack::isEmpty),
                "Corpse kept a duplicate after looting its page");
        action(
                player,
                new QuadHotbarContainerOverview.Action(2, 9, token, -1, 0, ClickType.PICKUP, 0));
        // Corpse slot zero is not an alias of the living player's hotbar slot zero.
        items.set(0, new ItemStack(Items.GOLD_INGOT, 7));
        ((DeathPageAccess) death).quadhotbar$setPageSlots(new int[] {0});
        parent.setCarried(ItemStack.EMPTY);
        action(player, new QuadHotbarContainerOverview.Action(0, 9, 0, -1, 0, ClickType.PICKUP, 9));
        token = token(session(player));
        action(
                player,
                new QuadHotbarContainerOverview.Action(1, 9, token, 0, 0, ClickType.SWAP, 0));
        helper.assertTrue(
                player.getInventory().getItem(0).is(Items.GOLD_INGOT)
                        && player.getInventory().getItem(0).getCount() == 7
                        && items.get(0).isEmpty(),
                "Number-key looting mistook a corpse hotbar for the living player's hotbar");
        action(
                player,
                new QuadHotbarContainerOverview.Action(2, 9, token, -1, 0, ClickType.PICKUP, 0));
        // A read-only history/container must remain read-only, even for direct forged RPCs.
        items.set(0, new ItemStack(Items.DIAMOND, 13));
        ((DeathPageAccess) death).quadhotbar$setPageSlots(new int[] {2 * 36 + 17});
        parent.setCarried(ItemStack.EMPTY);
        parent =
                (net.minecraft.world.inventory.AbstractContainerMenu)
                        constructor.newInstance(10, player.getInventory(), corpse, false, true);
        player.containerMenu = parent;
        menuType.getMethod("transferItems").invoke(parent);
        helper.assertTrue(
                items.get(0).getCount() == 13 && parent.getCarried().isEmpty(),
                "Read-only corpse transfer button bypassed native permissions");
        action(
                player,
                new QuadHotbarContainerOverview.Action(0, 10, 0, -1, 0, ClickType.PICKUP, 9));
        token = token(session(player));
        action(
                player,
                new QuadHotbarContainerOverview.Action(
                        1, 10, token, 2 * 36 + 17, 0, ClickType.PICKUP, 0));
        helper.assertTrue(
                parent.getCarried().isEmpty() && items.get(0).getCount() == 13,
                "Read-only corpse/history access was bypassed");
        action(
                player,
                new QuadHotbarContainerOverview.Action(2, 10, token, -1, 0, ClickType.PICKUP, 0));
        // Native "take all" restores the original slot, retaining displaced overflow.
        for (int page = 0; page < 3; page++)
            for (int slot = 0; slot < 36; slot++)
                QuadHotbarPages.setPageItem(player, page, slot, new ItemStack(Items.STONE, 64));
        QuadHotbarPages.setPageItem(player, 2, 10, new ItemStack(Items.DIAMOND, 63));
        items.set(0, new ItemStack(Items.DIAMOND, 10));
        ((DeathPageAccess) death).quadhotbar$setPageSlots(new int[] {2 * 36 + 17});
        var additionalType = Class.forName("de.maxhenkel.corpse.gui.CorpseAdditionalContainer");
        var additional =
                (net.minecraft.world.inventory.AbstractContainerMenu)
                        additionalType
                                .getConstructor(
                                        int.class,
                                        net.minecraft.world.entity.player.Inventory.class,
                                        corpseType,
                                        boolean.class,
                                        boolean.class)
                                .newInstance(11, player.getInventory(), corpse, true, true);
        player.containerMenu = additional;
        additionalType.getMethod("transferItems").invoke(additional);
        helper.assertTrue(
                QuadHotbarPages.getPageItem(player, 2, 17).is(Items.DIAMOND)
                        && QuadHotbarPages.getPageItem(player, 2, 17).getCount() == 10
                        && QuadHotbarPages.getPageItem(player, 2, 10).getCount() == 63
                        && items.size() == 1
                        && items.get(0).is(Items.STONE)
                        && items.get(0).getCount() == 64
                        && ((DeathPageAccess) death).quadhotbar$pageSlots()[0] == -1,
                "Native corpse take-all did not restore the original slot or retain overflow");
        QuadHotbarPages.setPageItem(player, 2, 11, ItemStack.EMPTY);
        additionalType.getMethod("transferItems").invoke(additional);
        helper.assertTrue(
                items.stream().allMatch(ItemStack::isEmpty)
                        && QuadHotbarPages.getPageItem(player, 2, 11).is(Items.STONE)
                        && QuadHotbarPages.getPageItem(player, 2, 11).getCount() == 64
                        && QuadHotbarPages.getPageItem(player, 2, 17).getCount() == 10,
                "Native corpse take-all lost items instead of filling an available page");
        helper.succeed();
    }
}
