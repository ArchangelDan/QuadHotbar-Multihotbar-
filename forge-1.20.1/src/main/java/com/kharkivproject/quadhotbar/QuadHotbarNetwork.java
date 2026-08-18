package com.kharkivproject.quadhotbar;

import com.kharkivproject.quadhotbar.client.QuadHotbarClientEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = QuadHotbar.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuadHotbarNetwork {

    private static final String PROTOCOL = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(QuadHotbar.MODID, "main"),
            () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL),
            NetworkRegistry.acceptMissingOr(PROTOCOL));

    private QuadHotbarNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(
                0,
                ServerSupportMessage.class,
                ServerSupportMessage::encode,
                ServerSupportMessage::decode,
                ServerSupportMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ServerSupportMessage(player.getInventory().selected));
    }

    private record ServerSupportMessage(int selectedSlot) {

        private static void encode(ServerSupportMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.selectedSlot);
        }

        private static ServerSupportMessage decode(FriendlyByteBuf buffer) {
            return new ServerSupportMessage(buffer.readVarInt());
        }

        private static void handle(ServerSupportMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> QuadHotbarClientEvents.syncServerSelectedSlot(message.selectedSlot));
            context.setPacketHandled(true);
        }
    }
}
