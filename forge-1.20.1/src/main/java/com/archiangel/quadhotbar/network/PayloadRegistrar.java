package com.archiangel.quadhotbar.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;

import java.util.function.BiConsumer;

public final class PayloadRegistrar {
    public <T extends ForgePayload> void playToClient(
            ForgePayload.Type<T> type,
            StreamCodec<? super FriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler) {
        PayloadTransport.register(type, codec, handler, NetworkDirection.PLAY_TO_CLIENT);
    }

    public <T extends ForgePayload> void playToServer(
            ForgePayload.Type<T> type,
            StreamCodec<? super FriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler) {
        PayloadTransport.register(type, codec, handler, NetworkDirection.PLAY_TO_SERVER);
    }
}
