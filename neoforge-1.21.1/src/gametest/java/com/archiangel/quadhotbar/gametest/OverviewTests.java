package com.archiangel.quadhotbar.gametest;

import com.archiangel.quadhotbar.QuadHotbarOverviewMenu;
import com.archiangel.quadhotbar.QuadHotbarOverviewNetwork;
import com.archiangel.quadhotbar.QuadHotbarPages;
import com.archiangel.quadhotbar.QuadHotbarServerConfig;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("quadhotbar")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "quadhotbar")
public class OverviewTests {
    @SubscribeEvent
    public static void template(ServerStartedEvent event) {
        if (!Boolean.getBoolean("neoforge.gameTestServer")) return;
        ServerLevel level = event.getServer().overworld();
        level.getStructureManager()
                .getOrCreate(ResourceLocation.fromNamespaceAndPath("quadhotbar", "empty"))
                .fillFromWorld(
                        level,
                        new BlockPos(0, 200, 0),
                        new Vec3i(1, 1, 1),
                        false,
                        Blocks.STRUCTURE_VOID);
    }

    private static Player player(GameTestHelper helper, int pages) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        QuadHotbarPages.state(player).pageCount = pages;
        return player;
    }

    private static QuadHotbarOverviewMenu menu(Player player, boolean standalone) {
        var state = QuadHotbarPages.state(player);
        return new QuadHotbarOverviewMenu(
                1, player.getInventory(), state.pageCount, state.activePage, standalone, 9, 9);
    }

    @GameTest(template = "empty", batch = "overview")
    public static void overviewWidthsAndPageGeometry(GameTestHelper helper) {
        int[] inputs = {-1, 9, 17, 18, 26, 27, Integer.MAX_VALUE};
        int[] expected = {9, 9, 9, 18, 18, 27, 27};
        for (int i = 0; i < inputs.length; i++)
            helper.assertTrue(
                    com.archiangel.quadhotbar.QuadHotbarOverviewLayout.normalizeWidth(inputs[i])
                            == expected[i],
                    "Width was not aligned to complete nine-slot groups");
        for (boolean right : new boolean[] {false, true}) {
            for (int page = 0; page < 5; page++) {
                int column =
                        com.archiangel.quadhotbar.QuadHotbarOverviewLayout.column(
                                page, 5, 2, true, right);
                int row = com.archiangel.quadhotbar.QuadHotbarOverviewLayout.row(page, 5, 2, true);
                helper.assertTrue(
                        column == (right ? 1 - page % 2 : page % 2) && row == page / 2,
                        "Five-page row order or mirrored final page is wrong");
            }
            for (int page = 0; page < 5; page++)
                helper.assertTrue(
                        com.archiangel.quadhotbar.QuadHotbarOverviewLayout.column(
                                                page, 5, 2, false, right)
                                        == (right ? 1 - page / 3 : page / 3)
                                && com.archiangel.quadhotbar.QuadHotbarOverviewLayout.row(
                                                page, 5, 2, false)
                                        == page % 3,
                        "Column order is wrong");
        }
        Player owner = player(helper, 5);
        for (boolean standalone : new boolean[] {false, true}) {
            var wide = new QuadHotbarOverviewMenu(1, owner.getInventory(), 5, 0, standalone, 27, 9);
            helper.assertTrue(
                    wide.columns == 27 && wide.pageSlots == 180,
                    "Wide overview changed page storage or rejected docked width");
        }
        for (int pages = 1; pages <= 100; pages++)
            for (int columns = 1; columns <= 3; columns++)
                for (boolean horizontal : new boolean[] {false, true})
                    for (boolean right : new boolean[] {false, true}) {
                        var occupied = new java.util.HashSet<String>();
                        int rows =
                                com.archiangel.quadhotbar.QuadHotbarOverviewLayout.rows(
                                        pages, columns);
                        for (int page = 0; page < pages; page++) {
                            int x =
                                    com.archiangel.quadhotbar.QuadHotbarOverviewLayout.column(
                                            page, pages, columns, horizontal, right);
                            int y =
                                    com.archiangel.quadhotbar.QuadHotbarOverviewLayout.row(
                                            page, pages, columns, horizontal);
                            helper.assertTrue(
                                    x >= 0
                                            && x < columns
                                            && y >= 0
                                            && y < rows
                                            && occupied.add(x + ":" + y),
                                    "Pages overlap or leave their viewport");
                        }
                    }
        helper.succeed();
    }

    private static int count(Player player) {
        int count = 0;
        for (int page = 0; page < QuadHotbarPages.MAX_PAGES; page++)
            for (int slot = 0; slot < 36; slot++)
                count += QuadHotbarPages.getPageItem(player, page, slot).getCount();
        return count;
    }

    private static ServerPlayer serverPlayer(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "overview-test");
        var player =
                new ServerPlayer(
                        helper.getLevel().getServer(),
                        helper.getLevel(),
                        profile,
                        ClientInformation.createDefault()) {
                    @Override
                    public boolean hasDisconnected() {
                        return false;
                    }
                };
        // Real ServerPlayer menu lifecycle, but no external connection or login/save side effects.
        player.connection = new FakePlayer(helper.getLevel(), profile).connection;
        player.initInventoryMenu();
        QuadHotbarPages.state(player).pageCount = 3;
        return player;
    }

    private static void request(ServerPlayer player, int action, ItemStack clientCursor)
            throws Exception {
        Class<?> requestType =
                Class.forName(QuadHotbarOverviewNetwork.class.getName() + "$Request");
        var constructor =
                requestType.getDeclaredConstructor(
                        int.class, int.class, boolean.class, int.class, int.class, ItemStack.class);
        constructor.setAccessible(true);
        var handle =
                QuadHotbarOverviewNetwork.class.getDeclaredMethod(
                        "handle", ServerPlayer.class, requestType);
        handle.setAccessible(true);
        handle.invoke(null, player, constructor.newInstance(action, 1, false, 9, 9, clientCursor));
    }

    private static ModConfigSpec.BooleanValue policy(String name) throws Exception {
        var field = QuadHotbarServerConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        return (ModConfigSpec.BooleanValue) field.get(null);
    }

    @GameTest(template = "empty", batch = "overview")
    public static void serverCanDenyOverview(GameTestHelper helper) throws Exception {
        ServerPlayer player = serverPlayer(helper);
        player.inventoryMenu.setCarried(new ItemStack(Items.DIAMOND, 23));
        var policy = policy("ALLOW_OVERVIEW");
        boolean previous = policy.get();
        try {
            policy.set(false);
            policy.clearCache(); // Simulate the world restart required by this server setting.
            request(player, 1, new ItemStack(Items.DIAMOND, 64));
            helper.assertTrue(
                    player.containerMenu == player.inventoryMenu, "Server overview ban bypassed");
            helper.assertTrue(
                    player.inventoryMenu.getCarried().getCount() == 23,
                    "Denied request changed cursor");
        } finally {
            policy.set(previous);
            policy.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void serverCanDenyFreedomWithoutErasingItems(GameTestHelper helper)
            throws Exception {
        ServerPlayer player = serverPlayer(helper);
        QuadHotbarPages.switchActivePage(player, 2);
        player.getInventory().setItem(10, new ItemStack(Items.DIAMOND, 47));
        QuadHotbarPages.state(player).hotbarRows = 6;
        var policy = policy("ALLOW_FREEDOM");
        boolean previous = policy.get();
        try {
            policy.set(false);
            policy.clearCache();
            request(player, 1, ItemStack.EMPTY);
            var state = QuadHotbarPages.state(player);
            helper.assertTrue(
                    state.pageCount == 1 && state.activePage == 0 && state.hotbarRows <= 4,
                    "Freedom ban bypassed");
            helper.assertTrue(
                    player.containerMenu == player.inventoryMenu, "Forbidden overview opened");
            helper.assertTrue(
                    QuadHotbarPages.getPageItem(player, 2, 10).getCount() == 47
                            && count(player) == 47,
                    "Freedom ban erased items");
        } finally {
            policy.set(previous);
            policy.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void menuRoundTripPreservesCursorAndCrafting(GameTestHelper helper)
            throws Exception {
        ServerPlayer player = serverPlayer(helper);
        player.inventoryMenu.getCraftSlots().setItem(0, new ItemStack(Items.DIAMOND, 5));
        player.inventoryMenu.setCarried(new ItemStack(Items.DIAMOND, 23));
        for (int turn = 0; turn < 20; turn++) {
            // A survival client cannot inject a forged cursor into either transition.
            request(player, 1, new ItemStack(Items.DIAMOND, 64));
            helper.assertTrue(
                    player.containerMenu instanceof QuadHotbarOverviewMenu,
                    "Overview did not open");
            helper.assertTrue(
                    player.containerMenu.getCarried().getCount() == 23,
                    "Opening lost/replaced cursor");
            helper.assertTrue(
                    player.inventoryMenu.getCarried() == player.containerMenu.getCarried(),
                    "Cursor was copied, not shared");
            helper.assertTrue(
                    player.inventoryMenu.getCraftSlots().getItem(0).getCount() == 5
                            && count(player) == 0,
                    "Opening changed crafting grid");
            request(player, 2, new ItemStack(Items.DIAMOND, 64));
            helper.assertTrue(
                    player.containerMenu == player.inventoryMenu, "Inventory not restored");
            helper.assertTrue(
                    player.inventoryMenu.getCarried().getCount() == 23 && count(player) == 0,
                    "Returning lost/duplicated cursor");
        }
        player.closeContainer();
        helper.assertTrue(
                player.inventoryMenu.getCarried().isEmpty() && count(player) == 28,
                "Final close lost items");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void creativeCursorTransferred(GameTestHelper helper) throws Exception {
        ServerPlayer player = serverPlayer(helper);
        player.setGameMode(GameType.CREATIVE);
        request(player, 1, new ItemStack(Items.DIAMOND, 31));
        helper.assertTrue(
                player.containerMenu.getCarried().getCount() == 31,
                "Creative cursor not transferred");
        request(player, 2, new ItemStack(Items.DIAMOND, 31));
        helper.assertTrue(
                player.inventoryMenu.getCarried().getCount() == 31 && count(player) == 0,
                "Creative return lost/duplicated cursor");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void fullOverviewPacketRoundTrip(GameTestHelper helper) {
        Player player = player(helper, 100);
        for (int page = 0; page < 100; page++)
            for (int slot = 0; slot < 36; slot++)
                QuadHotbarPages.setPageItem(player, page, slot, new ItemStack(Items.DIAMOND, 64));
        var menu = menu(player, false);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        try {
            var packet =
                    new ClientboundContainerSetContentPacket(
                            1, 0, menu.getItems(), new ItemStack(Items.DIAMOND, 7));
            ClientboundContainerSetContentPacket.STREAM_CODEC.encode(buffer, packet);
            var decoded = ClientboundContainerSetContentPacket.STREAM_CODEC.decode(buffer);
            helper.assertTrue(
                    decoded.getItems().size() == 3646
                            && decoded.getItems().get(3599).getCount() == 64,
                    "100-page inventory packet truncated");
            helper.assertTrue(decoded.getCarriedItem().getCount() == 7, "Packet lost cursor");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void cursorSurvivesPageSwitch(GameTestHelper helper) {
        Player player = player(helper, 3);
        player.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 23));
        player.inventoryMenu.clicked(9, 0, ClickType.PICKUP, player);
        QuadHotbarPages.switchActivePage(player, 2);
        helper.assertTrue(
                player.inventoryMenu.getCarried().getCount() == 23, "Page switch lost cursor");
        player.inventoryMenu.clicked(10, 0, ClickType.PICKUP, player);
        helper.assertTrue(
                QuadHotbarPages.getPageItem(player, 2, 10).getCount() == 23,
                "Destination did not receive items");
        helper.assertTrue(count(player) == 23, "Page switch duplicated/lost items");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void overviewMovesBetweenPages(GameTestHelper helper) {
        Player player = player(helper, 100);
        QuadHotbarPages.setPageItem(player, 0, 9, new ItemStack(Items.DIAMOND, 37));
        var menu = menu(player, true);
        menu.clicked(9, 0, ClickType.PICKUP, player);
        menu.clicked(99 * 36 + 35, 0, ClickType.PICKUP, player);
        helper.assertTrue(
                QuadHotbarPages.getPageItem(player, 99, 35).getCount() == 37,
                "Last page did not receive items");
        helper.assertTrue(
                menu.getCarried().isEmpty() && count(player) == 37,
                "Overview lost/duplicated items");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void dockAliasesAreOneStorage(GameTestHelper helper) {
        Player player = player(helper, 3);
        QuadHotbarPages.switchActivePage(player, 1);
        player.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 17));
        var menu = menu(player, false);
        menu.clicked(menu.pageSlots + 9, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.slots.get(36 + 9).getItem().isEmpty(), "Alias left a duplicate");
        menu.clicked(36 + 10, 0, ClickType.PICKUP, player);
        helper.assertTrue(
                menu.slots.get(menu.pageSlots + 10).getItem().getCount() == 17,
                "Alias did not update");
        helper.assertTrue(count(player) == 17, "Dock duplicated/lost items");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void dragDoesNotCountAliasesTwice(GameTestHelper helper) {
        Player player = player(helper, 3);
        var menu = menu(player, false);
        menu.setCarried(new ItemStack(Items.DIAMOND, 12));
        menu.clicked(-999, 0, ClickType.QUICK_CRAFT, player);
        menu.clicked(9, 1, ClickType.QUICK_CRAFT, player);
        menu.clicked(menu.pageSlots + 9, 1, ClickType.QUICK_CRAFT, player);
        menu.clicked(36, 1, ClickType.QUICK_CRAFT, player);
        menu.clicked(-999, 2, ClickType.QUICK_CRAFT, player);
        helper.assertTrue(
                QuadHotbarPages.getPageItem(player, 0, 9).getCount() == 6,
                "Drag counted alias twice");
        helper.assertTrue(
                QuadHotbarPages.getPageItem(player, 1, 0).getCount() == 6,
                "Drag distribution incorrect");
        helper.assertTrue(
                count(player) + menu.getCarried().getCount() == 12, "Drag lost/duplicated items");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void shiftClickMergesAndConserves(GameTestHelper helper) {
        Player player = player(helper, 3);
        QuadHotbarPages.setPageItem(player, 2, 35, new ItemStack(Items.DIAMOND, 40));
        QuadHotbarPages.setPageItem(player, 1, 10, new ItemStack(Items.DIAMOND, 50));
        var menu = menu(player, true);
        menu.clicked(2 * 36 + 35, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(
                QuadHotbarPages.getPageItem(player, 1, 10).getCount() == 64,
                "Shift click did not merge");
        helper.assertTrue(
                QuadHotbarPages.getPageItem(player, 2, 35).isEmpty() && count(player) == 90,
                "Shift click lost/duplicated items");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void hiddenPagesPersist(GameTestHelper helper) {
        Player player = player(helper, 100);
        QuadHotbarPages.setPageItem(player, 99, 35, new ItemStack(Items.DIAMOND, 41));
        QuadHotbarPages.state(player).pageCount = 1;
        CompoundTag saved = new CompoundTag();
        QuadHotbarPages.save(player, saved);
        Player restored = player(helper, 1);
        QuadHotbarPages.load(restored, saved);
        helper.assertTrue(
                QuadHotbarPages.getPageItem(restored, 99, 35).getCount() == 41,
                "Hidden page was erased");
        helper.assertTrue(count(player(helper, 100)) == 0, "Items leaked to another player/world");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void vanillaArmorOffhandAndCraftingWork(GameTestHelper helper) throws Exception {
        ServerPlayer player = serverPlayer(helper);
        player.inventoryMenu.getCraftSlots().setItem(0, new ItemStack(Items.OAK_LOG, 2));
        request(player, 1, ItemStack.EMPTY);
        var menu = (QuadHotbarOverviewMenu) player.containerMenu;
        menu.setCarried(new ItemStack(Items.DIAMOND, 7));
        menu.clicked(menu.pageSlots + 5, 0, ClickType.PICKUP, player);
        helper.assertTrue(
                menu.getCarried().getCount() == 7 && player.getInventory().armor.get(3).isEmpty(),
                "Armor accepted arbitrary items");
        menu.clicked(menu.pageSlots + 45, 0, ClickType.PICKUP, player);
        helper.assertTrue(
                player.getInventory().offhand.get(0).getCount() == 7 && menu.getCarried().isEmpty(),
                "Offhand is not vanilla storage");
        menu.clicked(menu.pageSlots, 0, ClickType.PICKUP, player);
        helper.assertTrue(
                menu.getCarried().is(Items.OAK_PLANKS) && menu.getCarried().getCount() == 4,
                "Vanilla crafting output failed");
        helper.assertTrue(
                player.inventoryMenu.getCraftSlots().getItem(0).getCount() == 1,
                "Crafting did not consume one ingredient");
        menu.clicked(36 + 10, 0, ClickType.PICKUP, player);
        menu.clicked(menu.pageSlots + 1, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.OAK_LOG), "Crafting input not selectable");
        request(player, 2, ItemStack.EMPTY);
        helper.assertTrue(
                player.inventoryMenu.getCarried().is(Items.OAK_LOG),
                "Returning lost crafting cursor");
        helper.assertTrue(
                QuadHotbarPages.getPageItem(player, 1, 10).getCount() == 4,
                "Overview did not receive crafted items");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void dockedPageSwitchSharesCursorAndSlots(GameTestHelper helper)
            throws Exception {
        ServerPlayer player = serverPlayer(helper);
        player.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 23));
        request(player, 1, ItemStack.EMPTY);
        var menu = (QuadHotbarOverviewMenu) player.containerMenu;
        menu.clicked(menu.pageSlots + 9, 0, ClickType.PICKUP, player);
        request(player, 0, new ItemStack(Items.DIAMOND, 64));
        helper.assertTrue(
                menu.activePage == 1 && menu.stillValid(player),
                "Overview became stale after changing vanilla page");
        menu.clicked(menu.pageSlots + 10, 0, ClickType.PICKUP, player);
        helper.assertTrue(
                QuadHotbarPages.getPageItem(player, 1, 10).getCount() == 23 && count(player) == 23,
                "Page switch lost/duplicated items");
        request(player, 2, ItemStack.EMPTY);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "overview")
    public static void overviewCloseReturnsCursorAndCrafting(GameTestHelper helper)
            throws Exception {
        ServerPlayer player = serverPlayer(helper);
        player.inventoryMenu.getCraftSlots().setItem(0, new ItemStack(Items.DIAMOND, 5));
        request(player, 1, ItemStack.EMPTY);
        player.containerMenu.setCarried(new ItemStack(Items.DIAMOND, 23));
        player.closeContainer();
        helper.assertTrue(
                player.inventoryMenu.getCraftSlots().isEmpty()
                        && player.inventoryMenu.getCarried().isEmpty(),
                "Close stranded crafting/cursor");
        helper.assertTrue(count(player) == 28, "Escape/disconnect close lost/duplicated items");
        helper.succeed();
    }
}
