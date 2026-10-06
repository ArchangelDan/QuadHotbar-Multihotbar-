package com.archiangel.quadhotbar;

import com.archiangel.quadhotbar.client.QuadHotbarContainerOverviewClient;
import com.archiangel.quadhotbar.compat.CorpsePages;
import com.archiangel.quadhotbar.network.ForgePayload;
import com.archiangel.quadhotbar.network.PayloadRegistrar;
import com.archiangel.quadhotbar.network.PayloadTransport;
import com.archiangel.quadhotbar.network.StreamCodec;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A sidecar, not a replacement container. Original GUI packets, cursor and permission rules
 * survive.
 */
@EventBusSubscriber(modid = QuadHotbar.MODID)
public final class QuadHotbarContainerOverview {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int nextToken;

    private QuadHotbarContainerOverview() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                Action.TYPE,
                Action.CODEC,
                (message, context) ->
                        context.enqueueWork(
                                () -> {
                                    if (context.player() instanceof ServerPlayer player)
                                        handle(player, message);
                                }));
        registrar.playToClient(
                Header.TYPE,
                Header.CODEC,
                (message, context) ->
                        context.enqueueWork(() -> QuadHotbarContainerOverviewClient.open(message)));
        registrar.playToClient(
                Frame.TYPE,
                Frame.CODEC,
                (message, context) ->
                        context.enqueueWork(() -> QuadHotbarContainerOverviewClient.sync(message)));
    }

    public static void requestOpen(int parent) {
        PayloadTransport.sendToServer(
                new Action(0, parent, 0, -1, 0, ClickType.PICKUP, QuadHotbarConfig.overviewHeight));
    }

    public static void click(int parent, int token, int slot, int button, ClickType type) {
        PayloadTransport.sendToServer(new Action(1, parent, token, slot, button, type, 0));
    }

    public static void close(int parent, int token) {
        PayloadTransport.sendToServer(new Action(2, parent, token, -1, 0, ClickType.PICKUP, 0));
    }

    private static void handle(ServerPlayer player, Action message) {
        if (message.action == 0) {
            open(player, message.parent, message.height);
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null
                || session.token != message.token
                || session.parent.containerId != message.parent) return;
        if (message.action == 2) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        if (message.action != 1 || !session.valid(player) || !session.editable) return;
        if (message.slot < 0
                && !(message.slot == -999 && message.clickType == ClickType.QUICK_CRAFT)) return;
        if (message.slot >= session.menu.pageSlots
                || !validButton(message.clickType, message.button)) return;
        session.menu.clicked(message.slot, message.button, message.clickType, player);
        session.parent.broadcastFullState();
        QuadHotbarNetwork.sendPageSync(player);
        session.sync(player, false);
    }

    private static boolean validButton(ClickType type, int button) {
        return switch (type) {
            case PICKUP, QUICK_MOVE, THROW, PICKUP_ALL -> button == 0 || button == 1;
            case SWAP -> button >= 0 && button <= 8 || button == 40;
            case CLONE -> button == 2;
            case QUICK_CRAFT -> button >= 0 && button <= 10;
        };
    }

    private static void open(ServerPlayer player, int id, int height) {
        AbstractContainerMenu parent = player.containerMenu;
        if (parent.containerId != id
                || parent == player.inventoryMenu
                || parent instanceof QuadHotbarOverviewMenu
                || parent.slots.isEmpty()
                || !parent.stillValid(player)
                || !player.isAlive()
                || player.isSpectator()
                || !QuadHotbarServerConfig.forPlayer(player).allowContainerOverview()) return;
        // A double click while the first header is in flight must not replace its token.
        var existing = SESSIONS.get(player.getUUID());
        if (existing != null && existing.parent == parent && existing.valid(player)) return;
        QuadHotbarNetwork.enforceServerLimits(player);
        var corpse = CorpsePages.find(parent);
        var state = QuadHotbarPages.state(player);
        int pages = corpse == null ? state.pageCount : corpse.pages();
        int active = corpse == null ? state.activePage : 0;
        int rows =
                Math.max(
                        1,
                        Math.min(
                                height,
                                QuadHotbarServerConfig.forPlayer(player).maxOverviewHeight()));
        var storage =
                corpse == null
                        ? QuadHotbarOverviewMenu.playerPages(player, pages)
                        : corpse.storage();
        var menu =
                QuadHotbarOverviewMenu.external(
                        id,
                        player.getInventory(),
                        pages,
                        active,
                        rows,
                        parent,
                        storage,
                        corpse != null);
        nextToken = nextToken == Integer.MAX_VALUE ? 1 : nextToken + 1;
        var session = new Session(parent, menu, nextToken, corpse == null || corpse.editable());
        SESSIONS.put(player.getUUID(), session);
        PayloadTransport.sendToPlayer(
                player, new Header(id, nextToken, pages, active, rows, corpse != null));
        session.sync(player, true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        var session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (!session.valid(player)) {
            SESSIONS.remove(player.getUUID());
            PayloadTransport.sendToPlayer(
                    player,
                    new Frame(
                            session.parent.containerId,
                            session.token,
                            -2,
                            List.of(),
                            ItemStack.EMPTY));
        } else if (player.tickCount % 2 == 0) session.sync(player, false);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        SESSIONS.clear();
    }

    private static final class Session {
        final AbstractContainerMenu parent;
        final QuadHotbarOverviewMenu menu;
        final int token;
        final boolean editable;
        final List<ItemStack> previous = new ArrayList<>();
        ItemStack previousCursor = ItemStack.EMPTY;

        Session(
                AbstractContainerMenu parent,
                QuadHotbarOverviewMenu menu,
                int token,
                boolean editable) {
            this.parent = parent;
            this.menu = menu;
            this.token = token;
            this.editable = editable;
            for (int i = 0; i < menu.pageSlots; i++) previous.add(ItemStack.EMPTY);
        }

        boolean valid(ServerPlayer player) {
            return menu.stillValid(player) && !player.isSpectator();
        }

        void sync(ServerPlayer player, boolean force) {
            boolean cursorChanged = !ItemStack.matches(previousCursor, menu.getCarried());
            boolean sent = false;
            for (int page = 0; page < menu.pageCount; page++) {
                boolean changed = force;
                var stacks = new ArrayList<ItemStack>(36);
                for (int slot = 0; slot < 36; slot++) {
                    int index = page * 36 + slot;
                    ItemStack stack = menu.slots.get(index).getItem();
                    if (!ItemStack.matches(previous.get(index), stack)) changed = true;
                    stacks.add(stack.copy());
                }
                if (!changed) continue;
                for (int slot = 0; slot < 36; slot++)
                    previous.set(page * 36 + slot, stacks.get(slot).copy());
                PayloadTransport.sendToPlayer(
                        player,
                        new Frame(
                                parent.containerId, token, page, stacks, menu.getCarried().copy()));
                sent = true;
            }
            if (cursorChanged && !sent)
                PayloadTransport.sendToPlayer(
                        player,
                        new Frame(
                                parent.containerId,
                                token,
                                -1,
                                List.of(),
                                menu.getCarried().copy()));
            previousCursor = menu.getCarried().copy();
        }
    }

    public record Action(
            int action,
            int parent,
            int token,
            int slot,
            int button,
            ClickType clickType,
            int height)
            implements ForgePayload {
        static final Type<Action> TYPE =
                new Type<>(
                        new ResourceLocation(QuadHotbar.MODID, "container_overview_action"),
                        Action.class);
        static final StreamCodec<FriendlyByteBuf, Action> CODEC =
                new StreamCodec<>() {
                    public Action decode(FriendlyByteBuf b) {
                        return new Action(
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readEnum(ClickType.class),
                                b.readVarInt());
                    }

                    public void encode(FriendlyByteBuf b, Action v) {
                        b.writeVarInt(v.action);
                        b.writeVarInt(v.parent);
                        b.writeVarInt(v.token);
                        b.writeVarInt(v.slot);
                        b.writeVarInt(v.button);
                        b.writeEnum(v.clickType);
                        b.writeVarInt(v.height);
                    }
                };

        public Type<Action> type() {
            return TYPE;
        }
    }

    public record Header(int parent, int token, int pages, int active, int height, boolean death)
            implements ForgePayload {
        static final Type<Header> TYPE =
                new Type<>(
                        new ResourceLocation(QuadHotbar.MODID, "container_overview_header"),
                        Header.class);
        static final StreamCodec<FriendlyByteBuf, Header> CODEC =
                new StreamCodec<>() {
                    public Header decode(FriendlyByteBuf b) {
                        return new Header(
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readVarInt(),
                                b.readBoolean());
                    }

                    public void encode(FriendlyByteBuf b, Header v) {
                        b.writeVarInt(v.parent);
                        b.writeVarInt(v.token);
                        b.writeVarInt(v.pages);
                        b.writeVarInt(v.active);
                        b.writeVarInt(v.height);
                        b.writeBoolean(v.death);
                    }
                };

        public Type<Header> type() {
            return TYPE;
        }
    }

    public record Frame(int parent, int token, int page, List<ItemStack> stacks, ItemStack cursor)
            implements ForgePayload {
        static final Type<Frame> TYPE =
                new Type<>(
                        new ResourceLocation(QuadHotbar.MODID, "container_overview_frame"),
                        Frame.class);
        static final StreamCodec<FriendlyByteBuf, Frame> CODEC =
                new StreamCodec<>() {
                    public Frame decode(FriendlyByteBuf b) {
                        int parent = b.readVarInt(),
                                token = b.readVarInt(),
                                page = b.readVarInt(),
                                size = b.readVarInt();
                        if (size != 0 && size != 36)
                            throw new IllegalArgumentException("Invalid overview page size");
                        var stacks = new ArrayList<ItemStack>(size);
                        for (int i = 0; i < size; i++) stacks.add(b.readItem());
                        return new Frame(parent, token, page, stacks, b.readItem());
                    }

                    public void encode(FriendlyByteBuf b, Frame v) {
                        b.writeVarInt(v.parent);
                        b.writeVarInt(v.token);
                        b.writeVarInt(v.page);
                        b.writeVarInt(v.stacks.size());
                        for (var stack : v.stacks) b.writeItem(stack);
                        b.writeItem(v.cursor);
                    }
                };

        public Type<Frame> type() {
            return TYPE;
        }
    }
}
