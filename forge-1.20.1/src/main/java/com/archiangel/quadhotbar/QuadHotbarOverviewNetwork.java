package com.archiangel.quadhotbar;

import com.archiangel.quadhotbar.client.QuadHotbarOverviewScreen;
import com.archiangel.quadhotbar.client.QuadHotbarOverviewSizing;
import com.archiangel.quadhotbar.network.ForgePayload;
import com.archiangel.quadhotbar.network.PayloadRegistrar;
import com.archiangel.quadhotbar.network.PayloadTransport;
import com.archiangel.quadhotbar.network.StreamCodec;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

public final class QuadHotbarOverviewNetwork {
    private static final ThreadLocal<ServerPlayer> OPENING = new ThreadLocal<>();

    public static boolean preservesVanillaMenu(ServerPlayer player) {
        return OPENING.get() == player && player.containerMenu == player.inventoryMenu;
    }

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
                player.getAbilities().instabuild
                        ? player.inventoryMenu.getCarried().copy()
                        : ItemStack.EMPTY;
        PayloadTransport.sendToServer(
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
            PayloadTransport.sendToPlayer(player, new ReturnToInventory());
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
        OPENING.set(player);
        try {
            net.minecraftforge.network.NetworkHooks.openScreen(
                    player,
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
        } finally {
            OPENING.remove();
        }
        if (!(player.containerMenu instanceof QuadHotbarOverviewMenu)) {
            player.inventoryMenu.setCarried(cursor);
            player.inventoryMenu.broadcastFullState();
        }
    }

    private static void acceptCreativeCursor(ServerPlayer player, Request request) {
        ItemStack stack = request.creativeCursor;
        if (player.getAbilities().instabuild
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
            implements ForgePayload {
        private static final Type<Request> TYPE =
                new Type<>(
                        new ResourceLocation(QuadHotbar.MODID, "overview_request"), Request.class);
        private static final StreamCodec<FriendlyByteBuf, Request> CODEC =
                new StreamCodec<>() {
                    public Request decode(FriendlyByteBuf buffer) {
                        return new Request(
                                buffer.readVarInt(),
                                buffer.readVarInt(),
                                buffer.readBoolean(),
                                buffer.readVarInt(),
                                buffer.readVarInt(),
                                buffer.readItem());
                    }

                    public void encode(FriendlyByteBuf buffer, Request value) {
                        buffer.writeVarInt(value.action);
                        buffer.writeVarInt(value.direction);
                        buffer.writeBoolean(value.standalone);
                        buffer.writeVarInt(value.width);
                        buffer.writeVarInt(value.height);
                        buffer.writeItem(value.creativeCursor);
                    }
                };

        public Type<Request> type() {
            return TYPE;
        }
    }

    private record ReturnToInventory() implements ForgePayload {
        private static final Type<ReturnToInventory> TYPE =
                new Type<>(
                        new ResourceLocation(QuadHotbar.MODID, "overview_return"),
                        ReturnToInventory.class);
        private static final StreamCodec<FriendlyByteBuf, ReturnToInventory> CODEC =
                new StreamCodec<>() {
                    public ReturnToInventory decode(FriendlyByteBuf buffer) {
                        return new ReturnToInventory();
                    }

                    public void encode(FriendlyByteBuf buffer, ReturnToInventory value) {}
                };

        public Type<ReturnToInventory> type() {
            return TYPE;
        }
    }
}
