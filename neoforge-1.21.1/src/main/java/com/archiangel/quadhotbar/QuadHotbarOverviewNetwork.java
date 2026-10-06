package com.archiangel.quadhotbar;

import com.archiangel.quadhotbar.client.QuadHotbarOverviewScreen;
import com.archiangel.quadhotbar.client.QuadHotbarOverviewSizing;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class QuadHotbarOverviewNetwork {
    private QuadHotbarOverviewNetwork() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                Request.TYPE,
                Request.CODEC,
                (request, context) ->
                        context.enqueueWork(
                                () -> {
                                    if (context.player() instanceof ServerPlayer player)
                                        handle(player, request);
                                }));
        registrar.playToClient(
                ReturnToInventory.TYPE,
                ReturnToInventory.CODEC,
                (payload, context) ->
                        context.enqueueWork(() -> QuadHotbarOverviewScreen.returnToInventory()));
    }

    public static void turnPage(int direction) {
        send(0, direction);
    }

    public static void open() {
        send(1, 0);
    }

    public static void returnToInventory() {
        send(2, 0);
    }

    public static void syncCreativeCursor() {
        send(3, 0);
    }

    private static void send(int action, int direction) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack creativeCursor =
                player.hasInfiniteMaterials()
                        ? player.inventoryMenu.getCarried().copy()
                        : ItemStack.EMPTY;
        PacketDistributor.sendToServer(
                new Request(
                        action,
                        direction,
                        QuadHotbarConfig.standaloneOverview,
                        QuadHotbarOverviewSizing.effectiveWidth(),
                        QuadHotbarConfig.overviewHeight,
                        creativeCursor));
    }

    private static void handle(ServerPlayer player, Request request) {
        if (request.action == 2) {
            if (!(player.containerMenu instanceof QuadHotbarOverviewMenu menu)) return;
            acceptCreativeCursor(player, request);
            menu.returningToInventory = true;
            ItemStack cursor = menu.getCarried();
            menu.setCarried(ItemStack.EMPTY);
            player.closeContainer();
            player.inventoryMenu.setCarried(cursor);
            player.inventoryMenu.broadcastFullState();
            QuadHotbarNetwork.sendPageSync(player);
            PacketDistributor.sendToPlayer(player, new ReturnToInventory());
            return;
        }
        if (!player.isAlive() || player.isSpectator()) return;
        if (request.action == 3) {
            if (player.containerMenu instanceof QuadHotbarOverviewMenu)
                acceptCreativeCursor(player, request);
            return;
        }
        boolean docked =
                player.containerMenu instanceof QuadHotbarOverviewMenu menu && !menu.standalone;
        if (player.containerMenu != player.inventoryMenu && !(docked && request.action == 0))
            return;
        QuadHotbarNetwork.enforceServerLimits(player);
        if (!QuadHotbarServerConfig.forPlayer(player).allowFreedom()) return;
        if (request.action != 0 && request.action != 1) return;
        if (request.action == 0 && request.direction != -1 && request.direction != 1) return;
        if (request.action == 1 && !QuadHotbarServerConfig.forPlayer(player).allowPageOverview())
            return;

        // Creative cursor contents are client-owned in vanilla. Survival cursor contents never are.
        acceptCreativeCursor(player, request);
        if (request.action == 0) {
            QuadHotbarNetwork.turnInventoryPage(player, request.direction);
            return;
        }
        QuadHotbarPages.State state = QuadHotbarPages.state(player);
        boolean standalone =
                switch (QuadHotbarServerConfig.forPlayer(player).overviewLayout()) {
                    case DOCKED -> false;
                    case STANDALONE -> true;
                    case CLIENT -> request.standalone;
                };
        int width =
                Math.min(
                        QuadHotbarOverviewLayout.normalizeWidth(request.width),
                        QuadHotbarServerConfig.forPlayer(player).maxOverviewWidth());
        int height =
                Math.max(
                        1,
                        Math.min(
                                request.height,
                                QuadHotbarServerConfig.forPlayer(player).maxOverviewHeight()));
        ItemStack cursor = player.inventoryMenu.getCarried();
        // The vanilla inventory remains available in docked mode. Its cursor and
        // crafting grid are shared, not copied or prematurely returned to storage.
        var opened =
                player.openMenu(
                        new SimpleMenuProvider(
                                (id, inventory, owner) -> {
                                    var menu =
                                            new QuadHotbarOverviewMenu(
                                                    id,
                                                    inventory,
                                                    state.pageCount,
                                                    state.activePage,
                                                    standalone,
                                                    width,
                                                    height);
                                    menu.setCarried(cursor);
                                    return menu;
                                },
                                Component.translatable("quadhotbar.overview.title")),
                        data -> {
                            data.writeVarInt(state.pageCount);
                            data.writeVarInt(state.activePage);
                            data.writeBoolean(standalone);
                            data.writeVarInt(width);
                            data.writeVarInt(height);
                        });
        if (opened.isEmpty()) {
            player.inventoryMenu.setCarried(cursor);
            player.inventoryMenu.broadcastFullState();
        }
    }

    private static void acceptCreativeCursor(ServerPlayer player, Request request) {
        ItemStack stack = request.creativeCursor;
        if (player.hasInfiniteMaterials()
                && (stack.isEmpty()
                        || (stack.getCount() <= stack.getMaxStackSize()
                                && stack.isItemEnabled(player.level().enabledFeatures()))))
            player.inventoryMenu.setCarried(stack.copy());
    }

    private record Request(
            int action,
            int direction,
            boolean standalone,
            int width,
            int height,
            ItemStack creativeCursor)
            implements CustomPacketPayload {
        private static final Type<Request> TYPE =
                new Type<>(
                        ResourceLocation.fromNamespaceAndPath(
                                QuadHotbar.MODID, "overview_request"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
                new StreamCodec<>() {
                    public Request decode(RegistryFriendlyByteBuf buffer) {
                        return new Request(
                                buffer.readVarInt(),
                                buffer.readVarInt(),
                                buffer.readBoolean(),
                                buffer.readVarInt(),
                                buffer.readVarInt(),
                                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
                    }

                    public void encode(RegistryFriendlyByteBuf buffer, Request value) {
                        buffer.writeVarInt(value.action);
                        buffer.writeVarInt(value.direction);
                        buffer.writeBoolean(value.standalone);
                        buffer.writeVarInt(value.width);
                        buffer.writeVarInt(value.height);
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, value.creativeCursor);
                    }
                };

        public Type<Request> type() {
            return TYPE;
        }
    }

    private record ReturnToInventory() implements CustomPacketPayload {
        private static final Type<ReturnToInventory> TYPE =
                new Type<>(
                        ResourceLocation.fromNamespaceAndPath(QuadHotbar.MODID, "overview_return"));
        private static final StreamCodec<RegistryFriendlyByteBuf, ReturnToInventory> CODEC =
                new StreamCodec<>() {
                    public ReturnToInventory decode(RegistryFriendlyByteBuf buffer) {
                        return new ReturnToInventory();
                    }

                    public void encode(RegistryFriendlyByteBuf buffer, ReturnToInventory value) {}
                };

        public Type<ReturnToInventory> type() {
            return TYPE;
        }
    }
}
