package com.archiangel.quadhotbar.network;

import com.archiangel.quadhotbar.QuadHotbar;
import com.archiangel.quadhotbar.QuadHotbarNetwork;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.BiConsumer;

/** The only loader-specific transport; packet logic and permissions stay server-authoritative. */
public final class PayloadTransport {
    public static final ResourceLocation CHANNEL_ID =
            new ResourceLocation(QuadHotbar.MODID, "main");
    public static final ResourceLocation PROTOCOL_CHANNEL_ID =
            new ResourceLocation(QuadHotbar.MODID, "protocol");
    // Keep the legacy envelope recognizable so old clients reach our clear login rejection.
    // The separate channel negotiates the actual packet schema before any gameplay packets.
    private static final SimpleChannel PROTOCOL_CHANNEL =
            NetworkRegistry.newSimpleChannel(
                    PROTOCOL_CHANNEL_ID,
                    () -> QuadHotbarNetwork.PROTOCOL,
                    NetworkRegistry.acceptMissingOr(QuadHotbarNetwork.PROTOCOL),
                    NetworkRegistry.acceptMissingOr(QuadHotbarNetwork.PROTOCOL));
    private static final SimpleChannel CHANNEL =
            NetworkRegistry.newSimpleChannel(
                    CHANNEL_ID,
                    () -> "2",
                    NetworkRegistry.acceptMissingOr("2"),
                    NetworkRegistry.acceptMissingOr("2"));
    private static int nextId;

    private PayloadTransport() {}

    static <T extends ForgePayload> void register(
            ForgePayload.Type<T> type,
            StreamCodec<? super FriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler,
            NetworkDirection direction) {
        CHANNEL.registerMessage(
                nextId++,
                type.messageClass(),
                (message, buffer) -> codec.encode(buffer, message),
                codec::decode,
                (message, supplier) -> {
                    var context = supplier.get();
                    handler.accept(message, IPayloadContext.of(context));
                    context.setPacketHandled(true);
                },
                Optional.of(direction));
    }

    public static boolean supports(ServerPlayer player) {
        return player.connection != null
                && player.connection.connection.channel() != null
                && CHANNEL.isRemotePresent(player.connection.connection)
                && PROTOCOL_CHANNEL.isRemotePresent(player.connection.connection);
    }

    public static void sendToServer(ForgePayload message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(ServerPlayer player, ForgePayload message) {
        if (supports(player)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
