package com.kharkivproject.quadhotbar;

import com.kharkivproject.quadhotbar.client.QuadHotbarClientEvents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class QuadHotbarNetwork {

    private static final String PROTOCOL = "2";

    private QuadHotbarNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL).optional();
        registrar.playToClient(ServerSupportPayload.TYPE, ServerSupportPayload.STREAM_CODEC, ServerSupportPayload::handle);
    }

    @EventBusSubscriber(modid = QuadHotbar.MODID)
    public static final class ForgeEvents {
        private ForgeEvents() {
        }

        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, new ServerSupportPayload(player.getInventory().selected));
            }
        }
    }

    private record ServerSupportPayload(int selectedSlot) implements CustomPacketPayload {
        private static final Type<ServerSupportPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(QuadHotbar.MODID, "server_support"));
        private static final StreamCodec<ByteBuf, ServerSupportPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                ServerSupportPayload::selectedSlot,
                ServerSupportPayload::new);

        private static void handle(ServerSupportPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> QuadHotbarClientEvents.syncServerSelectedSlot(payload.selectedSlot()));
        }

        @Override
        public Type<ServerSupportPayload> type() {
            return TYPE;
        }
    }
}
