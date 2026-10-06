package com.archiangel.quadhotbar;

import com.archiangel.quadhotbar.client.QuadHotbarClientEvents;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

public final class QuadHotbarNetwork {

    public static final String PROTOCOL = "11";

    private QuadHotbarNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL).optional();
        QuadHotbarPolicyNetwork.register(registrar);
        QuadHotbarOverviewNetwork.register(registrar);
        QuadHotbarContainerOverview.register(registrar);
        registrar.playToClient(
                ServerSupportPayload.TYPE,
                ServerSupportPayload.STREAM_CODEC,
                ServerSupportPayload::handle);
        registrar.playToClient(
                PageSyncPayload.TYPE, PageSyncPayload.STREAM_CODEC, PageSyncPayload::handle);
        registrar.playToClient(
                SelectionSyncPayload.TYPE,
                SelectionSyncPayload.STREAM_CODEC,
                SelectionSyncPayload::handle);
        registrar.playToServer(
                PageRequestPayload.TYPE,
                PageRequestPayload.STREAM_CODEC,
                PageRequestPayload::handle);
    }

    public static void requestPageSettings(int pages, int rows) {
        PacketDistributor.sendToServer(
                new PageRequestPayload(
                        0,
                        pages,
                        rows,
                        0,
                        QuadHotbarConfig.inventoryRowOrder
                                == QuadHotbarLayout.InventoryOrder.BOTTOM_TO_TOP));
    }

    public static void requestSlot(int row, int column, int requestId) {
        PacketDistributor.sendToServer(new PageRequestPayload(1, row, column, requestId, false));
    }

    public static void requestScroll(int direction, boolean currentHotbarOnly, int requestId) {
        PacketDistributor.sendToServer(
                new PageRequestPayload(4, direction, currentHotbarOnly ? 1 : 0, requestId, false));
    }

    public static void requestInventoryPage(int direction) {
        QuadHotbarOverviewNetwork.turnPage(direction);
    }

    public static void turnInventoryPage(ServerPlayer player, int direction) {
        QuadHotbarPages.State state = QuadHotbarPages.state(player);
        int selected = Mth.clamp(player.getInventory().selected, 0, QuadHotbarPages.PAGE_SIZE - 1);
        int page = Math.floorMod(state.activePage + direction, state.pageCount);
        state.hotbarPage = QuadHotbarPages.orderedRow(state, page, selected) / state.hotbarRows;
        changeActive(player, page, selected);
    }

    public static void requestHotbarPage(int direction) {
        PacketDistributor.sendToServer(new PageRequestPayload(3, direction, 0, 0, false));
    }

    public static void sendPageSync(ServerPlayer player) {
        sendPageSync(player, 0);
    }

    private static void sendPageSync(ServerPlayer player, int selectionRequestId) {
        QuadHotbarPolicyNetwork.sync(player);
        QuadHotbarPages.State state = QuadHotbarPages.state(player);
        int selectedRow =
                QuadHotbarPages.orderedRow(
                        state, state.activePage, Mth.clamp(player.getInventory().selected, 0, 35));
        if (selectedRow < state.hotbarPage * state.hotbarRows
                || selectedRow >= (state.hotbarPage + 1) * state.hotbarRows) {
            state.hotbarPage =
                    Mth.clamp(
                            selectedRow / state.hotbarRows,
                            0,
                            QuadHotbarPages.hotbarPageCount(state) - 1);
        }
        int firstPage = state.hotbarPage * state.hotbarRows / 4;
        int lastPage =
                (state.hotbarPage * state.hotbarRows + QuadHotbarPages.visibleRows(state) - 1) / 4;
        PacketDistributor.sendToPlayer(
                player,
                new PageSyncPayload(
                        state.pageCount,
                        state.hotbarRows,
                        QuadHotbarPages.hotbarPageCount(state),
                        state.activePage,
                        state.hotbarPage,
                        player.getInventory().selected,
                        selectionRequestId,
                        state.inventoryRowOrder == QuadHotbarLayout.InventoryOrder.BOTTOM_TO_TOP,
                        QuadHotbarPages.snapshot(player, firstPage),
                        QuadHotbarPages.snapshot(player, lastPage),
                        QuadHotbarPages.snapshot(
                                player, lastPage - firstPage == 2 ? firstPage + 1 : firstPage)));
    }

    /**
     * Also protects saved player data when a world's limits were reduced while the player was
     * offline.
     */
    public static boolean enforceServerLimits(ServerPlayer player) {
        QuadHotbarPages.State state = QuadHotbarPages.state(player);
        QuadHotbarPages.PageSettings allowed =
                QuadHotbarPages.limitSettings(
                        state.pageCount,
                        state.hotbarRows,
                        QuadHotbarServerConfig.forPlayer(player).maxInventoryPages(),
                        QuadHotbarServerConfig.forPlayer(player).maxHotbarRows());
        if (state.pageCount == allowed.pages()
                && state.hotbarRows == allowed.rows()
                && state.activePage < state.pageCount
                && state.hotbarPage < QuadHotbarPages.hotbarPageCount(state)) {
            return false;
        }
        applyPageSettings(player, allowed.pages(), allowed.rows());
        return true;
    }

    private static void applyPageSettings(
            ServerPlayer player, int requestedPages, int requestedRows) {
        QuadHotbarPages.PageSettings allowed =
                QuadHotbarPages.limitSettings(
                        requestedPages,
                        requestedRows,
                        QuadHotbarServerConfig.forPlayer(player).maxInventoryPages(),
                        QuadHotbarServerConfig.forPlayer(player).maxHotbarRows());
        QuadHotbarPages.State state = QuadHotbarPages.state(player);
        if ((state.pageCount != allowed.pages() || state.hotbarRows != allowed.rows())
                && player.containerMenu instanceof QuadHotbarOverviewMenu) player.closeContainer();
        if (state.activePage >= allowed.pages()) {
            QuadHotbarPages.switchActivePage(player, 0);
        }
        state.pageCount = allowed.pages();
        state.hotbarRows = allowed.rows();
        int selected = Mth.clamp(player.getInventory().selected, 0, QuadHotbarPages.PAGE_SIZE - 1);
        player.getInventory().selected = selected;
        int globalRow = QuadHotbarPages.orderedRow(state, state.activePage, selected);
        state.hotbarPage =
                Mth.clamp(
                        globalRow / state.hotbarRows,
                        0,
                        QuadHotbarPages.hotbarPageCount(state) - 1);
        player.inventoryMenu.broadcastFullState();
        sendPageSync(player);
    }

    private static void changeActive(ServerPlayer player, int page, int slot) {
        changeActive(player, page, slot, 0);
    }

    private static void changeActive(
            ServerPlayer player, int page, int slot, int selectionRequestId) {
        QuadHotbarPages.switchActivePage(player, page);
        player.getInventory().selected = Mth.clamp(slot, 0, QuadHotbarPages.PAGE_SIZE - 1);
        player.inventoryMenu.broadcastFullState();
        sendPageSync(player, selectionRequestId);
        if (player.containerMenu instanceof QuadHotbarOverviewMenu menu) {
            menu.activePage = QuadHotbarPages.state(player).activePage;
            menu.broadcastFullState();
        }
    }

    @EventBusSubscriber(modid = QuadHotbar.MODID)
    public static final class ForgeEvents {
        private ForgeEvents() {}

        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                QuadHotbarPolicyNetwork.forget(player);
                QuadHotbarPolicyNetwork.sync(player);
                PacketDistributor.sendToPlayer(
                        player, new ServerSupportPayload(player.getInventory().selected));
                QuadHotbarPages.State state = QuadHotbarPages.state(player);
                applyPageSettings(player, state.pageCount, state.hotbarRows);
            }
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player)
                QuadHotbarPolicyNetwork.forget(player);
        }

        @SubscribeEvent
        public static void onPlayerTick(
                net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player
                    && player.tickCount % 20 == 0
                    && QuadHotbarPolicyNetwork.sync(player)) {
                if (!enforceServerLimits(player)) sendPageSync(player);
            }
        }

        @SubscribeEvent
        public static void onPlayerClone(PlayerEvent.Clone event) {
            if (event.getOriginal() instanceof ServerPlayer original
                    && event.getEntity() instanceof ServerPlayer replacement) {
                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                QuadHotbarPages.save(original, tag);
                QuadHotbarPages.load(replacement, tag);
            }
        }

        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                QuadHotbarPages.State state = QuadHotbarPages.state(player);
                // A normal death resets Inventory.selected, while the saved hotbar page is
                // retained.
                // Reconcile them before the first post-respawn snapshot reaches the new client
                // player.
                applyPageSettings(player, state.pageCount, state.hotbarRows);
            }
        }
    }

    private record PageRequestPayload(
            int action, int first, int second, int requestId, boolean bottomToTop)
            implements CustomPacketPayload {
        private static final Type<PageRequestPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(QuadHotbar.MODID, "page_request"));
        private static final StreamCodec<ByteBuf, PageRequestPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public PageRequestPayload decode(ByteBuf buffer) {
                        return new PageRequestPayload(
                                ByteBufCodecs.VAR_INT.decode(buffer),
                                ByteBufCodecs.VAR_INT.decode(buffer),
                                ByteBufCodecs.VAR_INT.decode(buffer),
                                ByteBufCodecs.VAR_INT.decode(buffer),
                                ByteBufCodecs.BOOL.decode(buffer));
                    }

                    @Override
                    public void encode(ByteBuf buffer, PageRequestPayload payload) {
                        ByteBufCodecs.VAR_INT.encode(buffer, payload.action);
                        ByteBufCodecs.VAR_INT.encode(buffer, payload.first);
                        ByteBufCodecs.VAR_INT.encode(buffer, payload.second);
                        ByteBufCodecs.VAR_INT.encode(buffer, payload.requestId);
                        ByteBufCodecs.BOOL.encode(buffer, payload.bottomToTop);
                    }
                };

        private static void handle(PageRequestPayload request, IPayloadContext context) {
            context.enqueueWork(
                    () -> {
                        if (!(context.player() instanceof ServerPlayer player)) {
                            return;
                        }
                        if (player.containerMenu != player.inventoryMenu) {
                            sendPageSync(
                                    player,
                                    request.requestId); // Reject and correct client prediction
                            // while another menu is open.
                            return;
                        }
                        QuadHotbarPages.State state = QuadHotbarPages.state(player);
                        if (request.action == 0) {
                            state.inventoryRowOrder =
                                    request.bottomToTop
                                            ? QuadHotbarLayout.InventoryOrder.BOTTOM_TO_TOP
                                            : QuadHotbarLayout.InventoryOrder.TOP_TO_BOTTOM;
                            applyPageSettings(player, request.first, request.second);
                            return;
                        }
                        enforceServerLimits(player);
                        if (request.action == 1) {
                            if (request.first < 0
                                    || request.first >= QuadHotbarPages.visibleRows(state)
                                    || request.second < 0
                                    || request.second >= 9) {
                                sendPageSync(player, request.requestId);
                                return;
                            }
                            int page = QuadHotbarPages.pageForRow(state, request.first);
                            int slot =
                                    QuadHotbarPages.slotForRow(
                                            state, request.first, request.second);
                            if (page == state.activePage) {
                                player.getInventory().selected = slot;
                                PacketDistributor.sendToPlayer(
                                        player, new SelectionSyncPayload(slot, request.requestId));
                            } else {
                                changeActive(player, page, slot, request.requestId);
                            }
                        } else if (request.action == 2
                                || request.action == 3
                                || request.action == 4) {
                            if (request.first != -1 && request.first != 1) {
                                return;
                            }
                            int selectedSlot =
                                    Mth.clamp(
                                            player.getInventory().selected,
                                            0,
                                            QuadHotbarPages.PAGE_SIZE - 1);
                            int column = selectedSlot % 9;
                            if (request.action == 4) {
                                if (request.second != 0 && request.second != 1) {
                                    sendPageSync(player, request.requestId);
                                    return;
                                }
                                int selectedRow =
                                        QuadHotbarPages.orderedRow(
                                                state, state.activePage, selectedSlot);
                                state.hotbarPage =
                                        Mth.clamp(
                                                selectedRow / state.hotbarRows,
                                                0,
                                                QuadHotbarPages.hotbarPageCount(state) - 1);
                                int visualSlot =
                                        (selectedRow - state.hotbarPage * state.hotbarRows) * 9
                                                + column;
                                int next =
                                        request.second == 1
                                                ? visualSlot / 9 * 9
                                                        + Math.floorMod(column - request.first, 9)
                                                : Math.floorMod(
                                                        visualSlot - request.first,
                                                        QuadHotbarPages.visibleRows(state) * 9);
                                int page = QuadHotbarPages.pageForRow(state, next / 9);
                                int slot = QuadHotbarPages.slotForRow(state, next / 9, next % 9);
                                changeActive(player, page, slot, request.requestId);
                            } else if (request.action == 2) {
                                // Keep the legacy action consistent with cursor-safe
                                // overview_request.
                                turnInventoryPage(player, request.first);
                            } else {
                                int selectedRow =
                                        QuadHotbarPages.orderedRow(
                                                state, state.activePage, selectedSlot);
                                int relativeRow =
                                        Mth.clamp(
                                                selectedRow - state.hotbarPage * state.hotbarRows,
                                                0,
                                                state.hotbarRows - 1);
                                state.hotbarPage =
                                        Math.floorMod(
                                                state.hotbarPage + request.first,
                                                QuadHotbarPages.hotbarPageCount(state));
                                int nextRow = state.hotbarPage * state.hotbarRows + relativeRow;
                                changeActive(
                                        player,
                                        nextRow / 4,
                                        state.inventoryRowOrder.inventoryRow(nextRow % 4) * 9
                                                + column);
                            }
                        }
                    });
        }

        @Override
        public Type<PageRequestPayload> type() {
            return TYPE;
        }
    }

    private record SelectionSyncPayload(int selectedSlot, int requestId)
            implements CustomPacketPayload {
        private static final Type<SelectionSyncPayload> TYPE =
                new Type<>(
                        ResourceLocation.fromNamespaceAndPath(QuadHotbar.MODID, "selection_sync"));
        private static final StreamCodec<ByteBuf, SelectionSyncPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT,
                        SelectionSyncPayload::selectedSlot,
                        ByteBufCodecs.VAR_INT,
                        SelectionSyncPayload::requestId,
                        SelectionSyncPayload::new);

        private static void handle(SelectionSyncPayload payload, IPayloadContext context) {
            context.enqueueWork(
                    () ->
                            QuadHotbarClientEvents.syncPageSelection(
                                    payload.selectedSlot, payload.requestId));
        }

        @Override
        public Type<SelectionSyncPayload> type() {
            return TYPE;
        }
    }

    // Protocol 9 includes the middle inventory page: seven visible hotbars can span three pages.
    // The hotbar page count is derived, not a configurable limit.
    private record PageSyncPayload(
            int pageCount,
            int hotbarRows,
            int hotbarPageCount,
            int activePage,
            int hotbarPage,
            int selectedSlot,
            int requestId,
            boolean bottomToTop,
            List<ItemStack> firstItems,
            List<ItemStack> secondItems,
            List<ItemStack> middleItems)
            implements CustomPacketPayload {
        private static final Type<PageSyncPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(QuadHotbar.MODID, "page_sync"));
        private static final StreamCodec<RegistryFriendlyByteBuf, PageSyncPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public PageSyncPayload decode(RegistryFriendlyByteBuf buffer) {
                        int pageCount = buffer.readVarInt();
                        int rows = buffer.readVarInt();
                        int hotbarPageCount = buffer.readVarInt();
                        int active = buffer.readVarInt();
                        int hotbar = buffer.readVarInt();
                        int selected = buffer.readVarInt();
                        int requestId = buffer.readVarInt();
                        boolean bottomToTop = buffer.readBoolean();
                        return new PageSyncPayload(
                                pageCount,
                                rows,
                                hotbarPageCount,
                                active,
                                hotbar,
                                selected,
                                requestId,
                                bottomToTop,
                                readItems(buffer),
                                readItems(buffer),
                                readItems(buffer));
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, PageSyncPayload payload) {
                        buffer.writeVarInt(payload.pageCount);
                        buffer.writeVarInt(payload.hotbarRows);
                        buffer.writeVarInt(payload.hotbarPageCount);
                        buffer.writeVarInt(payload.activePage);
                        buffer.writeVarInt(payload.hotbarPage);
                        buffer.writeVarInt(payload.selectedSlot);
                        buffer.writeVarInt(payload.requestId);
                        buffer.writeBoolean(payload.bottomToTop);
                        writeItems(buffer, payload.firstItems);
                        writeItems(buffer, payload.secondItems);
                        writeItems(buffer, payload.middleItems);
                    }
                };

        private static List<ItemStack> readItems(RegistryFriendlyByteBuf buffer) {
            List<ItemStack> items = new ArrayList<>(QuadHotbarPages.PAGE_SIZE);
            for (int i = 0; i < QuadHotbarPages.PAGE_SIZE; i++) {
                items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
            }
            return items;
        }

        private static void writeItems(RegistryFriendlyByteBuf buffer, List<ItemStack> items) {
            for (int i = 0; i < QuadHotbarPages.PAGE_SIZE; i++) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, items.get(i));
            }
        }

        private static void handle(PageSyncPayload payload, IPayloadContext context) {
            context.enqueueWork(
                    () ->
                            QuadHotbarClientEvents.syncPagePayload(
                                    payload.pageCount,
                                    payload.hotbarRows,
                                    payload.activePage,
                                    payload.hotbarPage,
                                    payload.selectedSlot,
                                    payload.requestId,
                                    payload.bottomToTop,
                                    payload.firstItems,
                                    payload.secondItems,
                                    payload.middleItems));
        }

        @Override
        public Type<PageSyncPayload> type() {
            return TYPE;
        }
    }

    private record ServerSupportPayload(int selectedSlot) implements CustomPacketPayload {
        private static final Type<ServerSupportPayload> TYPE =
                new Type<>(
                        ResourceLocation.fromNamespaceAndPath(QuadHotbar.MODID, "server_support"));
        private static final StreamCodec<ByteBuf, ServerSupportPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT,
                        ServerSupportPayload::selectedSlot,
                        ServerSupportPayload::new);

        private static void handle(ServerSupportPayload payload, IPayloadContext context) {
            context.enqueueWork(
                    () -> QuadHotbarClientEvents.syncServerSelectedSlot(payload.selectedSlot()));
        }

        @Override
        public Type<ServerSupportPayload> type() {
            return TYPE;
        }
    }
}
