package com.archiangel.quadhotbar.gametest;

import com.archiangel.quadhotbar.QuadHotbarLayout;
import com.archiangel.quadhotbar.QuadHotbarNetwork;
import com.archiangel.quadhotbar.QuadHotbarPages;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@GameTestHolder("quadhotbar")
@PrefixGameTestTemplate(false)
public class LayoutTests {
    private static ServerPlayer player(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "layout-test");
        var player =
                new ServerPlayer(
                        helper.getLevel().getServer(),
                        helper.getLevel(),
                        profile,
                        ClientInformation.createDefault());
        player.connection = new FakePlayer(helper.getLevel(), profile).connection;
        player.initInventoryMenu();
        return player;
    }

    private static void request(ServerPlayer player, int action, int first, int second, int id)
            throws Exception {
        Class<?> payload = Class.forName(QuadHotbarNetwork.class.getName() + "$PageRequestPayload");
        var constructor =
                payload.getDeclaredConstructor(
                        int.class, int.class, int.class, int.class, boolean.class);
        constructor.setAccessible(true);
        var handler = payload.getDeclaredMethod("handle", payload, IPayloadContext.class);
        handler.setAccessible(true);
        IPayloadContext context =
                (IPayloadContext)
                        Proxy.newProxyInstance(
                                IPayloadContext.class.getClassLoader(),
                                new Class<?>[] {IPayloadContext.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "player" -> player;
                                            case "enqueueWork" -> {
                                                ((Runnable) args[0]).run();
                                                yield CompletableFuture.completedFuture(null);
                                            }
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            method.getName());
                                        });
        handler.invoke(
                null,
                constructor.newInstance(
                        action, first, second, action == 0 ? 0 : id, action == 0 && id == 1),
                context);
    }

    @GameTest(template = "empty", batch = "overview")
    public static void cornersAndDefaultGeometry(GameTestHelper helper) {
        for (int rows = 1; rows <= QuadHotbarPages.MAX_HOTBAR_ROWS; rows++) {
            for (var corner : QuadHotbarLayout.MainCorner.values()) {
                Set<String> occupied = new HashSet<>();
                int[] main = QuadHotbarLayout.coordinates(0, rows, 1000, 600, 182, 22, corner);
                int mainY = corner.top ? 600 - ((rows + 1) / 2) * 22 : 578;
                helper.assertTrue(
                        main[1] == mainY, "Main hotbar is not at the selected vertical corner");
                if (rows > 1)
                    helper.assertTrue(
                            main[0] == (corner.right ? 500 : 318), "Main hotbar is on wrong side");
                for (int row = 0; row < rows; row++) {
                    int[] coords =
                            QuadHotbarLayout.coordinates(row, rows, 1000, 600, 182, 22, corner);
                    helper.assertTrue(occupied.add(coords[0] + ":" + coords[1]), "Hotbars overlap");
                    if (corner == QuadHotbarLayout.MainCorner.BOTTOM_LEFT) {
                        boolean centered = rows == 1 || rows % 2 == 1 && row == rows - 1;
                        int oldX = centered ? 409 : row % 2 == 0 ? 318 : 500;
                        helper.assertTrue(
                                coords[0] == oldX && coords[1] == 600 - (row / 2 + 1) * 22,
                                "Default layout changed");
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void allPagesCoverEverySlotInBothOrders(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var state = QuadHotbarPages.state(player);
        for (int page = 0; page < 21; page++) {
            for (int slot = 0; slot < 36; slot++) {
                QuadHotbarPages.setPageItem(
                        player, page, slot, new ItemStack(Items.STONE, page * 4 + slot / 9 + 1));
            }
        }
        for (var order : QuadHotbarLayout.InventoryOrder.values()) {
            state.inventoryRowOrder = order;
            for (int rows = 2; rows <= QuadHotbarPages.MAX_HOTBAR_ROWS; rows++) {
                state.pageCount = QuadHotbarPages.alignInventoryPages(15, rows);
                state.hotbarRows = rows;
                Set<Integer> visited = new HashSet<>();
                for (int hotbarPage = 0;
                        hotbarPage < QuadHotbarPages.hotbarPageCount(state);
                        hotbarPage++) {
                    state.hotbarPage = hotbarPage;
                    for (int row = 0; row < rows; row++) {
                        for (int column = 0; column < 9; column++) {
                            int page = QuadHotbarPages.pageForRow(state, row);
                            int slot = QuadHotbarPages.slotForRow(state, row, column);
                            helper.assertTrue(
                                    visited.add(page * 36 + slot),
                                    "One slot appears on two hotbars");
                            helper.assertTrue(
                                    QuadHotbarPages.visibleStack(player, row, column).getCount()
                                            == page * 4 + slot / 9 + 1,
                                    "HUD shows a different item from the mapped slot");
                            helper.assertTrue(
                                    QuadHotbarPages.orderedRow(state, page, slot)
                                            == hotbarPage * rows + row,
                                    "Selected-slot inverse mapping disagrees with the HUD");
                        }
                    }
                }
                helper.assertTrue(
                        visited.size() == state.pageCount * 36,
                        "Inventory slots disappeared from the layout");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void serverScrollAndPageSelectionUseReversedRows(GameTestHelper helper)
            throws Exception {
        ServerPlayer player = player(helper);
        request(player, 0, 3, 6, 1);
        for (int page = 0; page < 3; page++) {
            for (int slot = 0; slot < 36; slot++) {
                QuadHotbarPages.setPageItem(
                        player, page, slot, new ItemStack(Items.STONE, page * 4 + slot / 9 + 1));
            }
        }
        request(player, 1, 1, 8, 10);
        helper.assertTrue(
                player.getInventory().selected == 35 && player.getMainHandItem().getCount() == 4,
                "Second hotbar did not select the bottom inventory row");
        request(player, 4, -1, 0, 11);
        helper.assertTrue(
                player.getInventory().selected == 18 && player.getMainHandItem().getCount() == 3,
                "Scroll crossed into the wrong row");
        request(player, 1, 5, 8, 12);
        helper.assertTrue(
                QuadHotbarPages.state(player).activePage == 1
                        && player.getInventory().selected == 35,
                "Sixth hotbar did not select the next page's bottom row");
        request(player, 3, 1, 0, 0);
        helper.assertTrue(
                QuadHotbarPages.state(player).activePage == 2
                        && player.getInventory().selected == 17,
                "Hotbar-page change lost the relative row");
        request(player, 4, -1, 0, 13);
        helper.assertTrue(
                QuadHotbarPages.state(player).activePage == 1
                        && player.getInventory().selected == 18,
                "Wrapping a six-hotbar page selected the wrong slot");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void hotbarPageBindingsWrapBothWays(GameTestHelper helper) throws Exception {
        ServerPlayer player = player(helper);
        for (int rows = 2; rows <= 8; rows++) {
            request(player, 0, QuadHotbarPages.pageStep(rows) * 3, rows, 0);
            var state = QuadHotbarPages.state(player);
            state.hotbarPage = 0;
            request(player, 1, 0, 6, 0);
            int count = QuadHotbarPages.hotbarPageCount(state);
            request(player, 3, -1, 0, 0);
            helper.assertTrue(
                    state.hotbarPage == count - 1 && player.getInventory().selected % 9 == 6,
                    "Previous hotbar-page binding did not wrap from first to last");
            request(player, 3, 1, 0, 0);
            helper.assertTrue(
                    state.hotbarPage == 0 && player.getInventory().selected % 9 == 6,
                    "Next hotbar-page binding did not wrap from last to first");
            for (int i = 0; i < count * 2 + 2; i++) {
                request(player, 3, 1, 0, 0);
                helper.assertTrue(
                        state.hotbarPage == (i + 1) % count, "Repeated page binding lost a step");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void orderChangePreservesItemsAndSelectedSlot(GameTestHelper helper)
            throws Exception {
        ServerPlayer player = player(helper);
        request(player, 0, 3, 6, 0);
        QuadHotbarPages.switchActivePage(player, 1);
        player.getInventory().setItem(10, new ItemStack(Items.DIAMOND, 23));
        player.getInventory().selected = 10;
        QuadHotbarPages.setPageItem(player, 2, 35, new ItemStack(Items.GOLD_INGOT, 47));
        request(player, 0, 3, 6, 1);
        var state = QuadHotbarPages.state(player);
        helper.assertTrue(
                player.getInventory().selected == 10 && player.getMainHandItem().getCount() == 23,
                "Changing order changed the held item");
        helper.assertTrue(
                state.hotbarPage == 1
                        && QuadHotbarPages.getPageItem(player, 2, 35).getCount() == 47,
                "Changing order lost a hidden-page item or failed to recenter");
        CompoundTag saved = new CompoundTag();
        QuadHotbarPages.save(player, saved);
        Player restored = helper.makeMockPlayer(GameType.SURVIVAL);
        QuadHotbarPages.load(restored, saved);
        helper.assertTrue(
                QuadHotbarPages.state(restored).inventoryRowOrder
                        == QuadHotbarLayout.InventoryOrder.BOTTOM_TO_TOP,
                "Inventory order was not saved for rejoin/respawn");
        request(player, 0, 3, 6, 0);
        helper.assertTrue(
                player.getInventory().selected == 10 && player.getMainHandItem().getCount() == 23,
                "Restoring the default order changed the held item");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void legacyInventoryPageRequestPreservesCursor(GameTestHelper helper)
            throws Exception {
        ServerPlayer player = player(helper);
        request(player, 0, 3, 6, 1);
        QuadHotbarPages.switchActivePage(player, 1);
        player.getInventory().selected = 17;
        player.getInventory().setItem(17, new ItemStack(Items.GOLD_INGOT, 7));
        player.inventoryMenu.setCarried(new ItemStack(Items.DIAMOND, 23));
        request(player, 2, 1, 0, 0);
        helper.assertTrue(
                QuadHotbarPages.state(player).activePage == 2
                        && player.getInventory().selected == 17,
                "Legacy page request changed the selected slot or failed to turn the page");
        helper.assertTrue(
                player.inventoryMenu.getCarried().getCount() == 23
                        && QuadHotbarPages.getPageItem(player, 1, 17).getCount() == 7,
                "Legacy page request lost cursor or stored items");
        QuadHotbarNetwork.turnInventoryPage(player, -1);
        helper.assertTrue(
                player.getMainHandItem().getCount() == 7
                        && player.inventoryMenu.getCarried().getCount() == 23,
                "Canonical and legacy page paths disagree");
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    @GameTest(template = "empty", batch = "overview")
    public static void hotbarSnapshotContainsThreePages(GameTestHelper helper) throws Exception {
        Class<?> payload = Class.forName(QuadHotbarNetwork.class.getName() + "$PageSyncPayload");
        var constructor =
                payload.getDeclaredConstructor(
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        boolean.class,
                        List.class,
                        List.class,
                        List.class);
        constructor.setAccessible(true);
        var codecField = payload.getDeclaredField("STREAM_CODEC");
        codecField.setAccessible(true);
        var codec = (StreamCodec<RegistryFriendlyByteBuf, Object>) codecField.get(null);
        var items = Collections.nCopies(36, ItemStack.EMPTY);
        var buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            codec.encode(
                    buffer,
                    constructor.newInstance(15, 6, 10, 4, 3, 17, 42, true, items, items, items));
            for (int expected : new int[] {15, 6, 10, 4, 3, 17, 42}) {
                helper.assertTrue(
                        buffer.readVarInt() == expected, "Protocol 9 snapshot header changed");
            }
            helper.assertTrue(buffer.readBoolean(), "Inventory row-order flag changed");
            for (int i = 0; i < 108; i++) {
                helper.assertTrue(
                        ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer).isEmpty(),
                        "Snapshot item layout changed");
            }
            helper.assertTrue(!buffer.isReadable(), "Unexpected data added to hotbar snapshot");
            buffer.readerIndex(0);
            codec.decode(buffer);
            helper.assertTrue(!buffer.isReadable(), "Snapshot decoder left data unread");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    @GameTest(template = "empty", batch = "overview")
    public static void sevenHotbarsSyncMiddlePageAndEightSurviveRejoin(GameTestHelper helper)
            throws Exception {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var first = Collections.nCopies(36, new ItemStack(Items.STONE, 11));
        var last = Collections.nCopies(36, new ItemStack(Items.GOLD_INGOT, 33));
        var middle = Collections.nCopies(36, new ItemStack(Items.DIAMOND, 22));
        var cacheField = QuadHotbarPages.State.class.getDeclaredField("pages");
        cacheField.setAccessible(true);
        for (int active = 3; active <= 5; active++) {
            QuadHotbarPages.receiveSnapshot(player, 7, 7, active, 2, 0, false, first, last, middle);
            for (int row = 0; row < 7; row++) {
                int page = QuadHotbarPages.pageForRow(QuadHotbarPages.state(player), row);
                helper.assertTrue(
                        QuadHotbarPages.visibleStack(player, row, 0).getCount() == (page - 2) * 11,
                        "Seven-hotbar snapshot lost a visible inventory page");
            }
            helper.assertTrue(
                    ((List<List<ItemStack>>) cacheField.get(QuadHotbarPages.state(player)))
                            .get(active).stream().allMatch(ItemStack::isEmpty),
                    "Active inventory page duplicated into saved storage");
        }
        var allowed = QuadHotbarPages.limitSettings(3, 8, 100, 8);
        helper.assertTrue(
                allowed.rows() == 8 && allowed.pages() == 4,
                "Eight hotbars did not enforce complete two-inventory-page groups");
        var limited = QuadHotbarPages.limitSettings(100, 8, 1, 8);
        helper.assertTrue(
                limited.rows() == 4 && limited.pages() == 1,
                "Eight hotbars bypassed a server's one-page cap");
        QuadHotbarPages.receiveSnapshot(player, 4, 8, 0, 0, 0, false, first, last, middle);
        CompoundTag saved = new CompoundTag();
        QuadHotbarPages.save(player, saved);
        Player restored = helper.makeMockPlayer(GameType.SURVIVAL);
        QuadHotbarPages.load(restored, saved);
        helper.assertTrue(
                QuadHotbarPages.state(restored).hotbarRows == 8,
                "Rejoining discarded the eight-hotbar setting");
        helper.succeed();
    }
}
