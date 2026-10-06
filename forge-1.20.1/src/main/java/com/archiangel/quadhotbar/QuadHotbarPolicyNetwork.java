package com.archiangel.quadhotbar;

import com.archiangel.quadhotbar.client.QuadHotbarClientEvents;
import com.archiangel.quadhotbar.network.ByteBufCodecs;
import com.archiangel.quadhotbar.network.ForgePayload;
import com.archiangel.quadhotbar.network.PayloadRegistrar;
import com.archiangel.quadhotbar.network.PayloadTransport;
import com.archiangel.quadhotbar.network.StreamCodec;

import io.netty.buffer.ByteBuf;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/** This payload contains the recipient's effective policy, not client-calculated permissions. */
public final class QuadHotbarPolicyNetwork {
    private static final Map<ServerPlayer, QuadHotbarPolicy> SENT = new WeakHashMap<>();

    private QuadHotbarPolicyNetwork() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                Payload.TYPE,
                Payload.CODEC,
                (payload, context) ->
                        context.enqueueWork(
                                () -> QuadHotbarClientEvents.setServerPolicy(payload.policy)));
    }

    public static boolean sync(ServerPlayer player) {
        QuadHotbarPolicy policy = QuadHotbarServerConfig.forPlayer(player);
        if (policy.equals(SENT.get(player))) return false;
        SENT.put(player, policy);
        PayloadTransport.sendToPlayer(player, new Payload(policy));
        return true;
    }

    public static void forget(ServerPlayer player) {
        SENT.remove(player);
    }

    private record Payload(QuadHotbarPolicy policy) implements ForgePayload {
        private static final Type<Payload> TYPE =
                new Type<>(new ResourceLocation(QuadHotbar.MODID, "player_policy"), Payload.class);
        private static final StreamCodec<ByteBuf, Payload> CODEC =
                new StreamCodec<>() {
                    @Override
                    public Payload decode(ByteBuf buffer) {
                        return new Payload(
                                new QuadHotbarPolicy(
                                        buffer.readBoolean(),
                                        buffer.readBoolean(),
                                        buffer.readBoolean(),
                                        buffer.readBoolean(),
                                        QuadHotbarServerConfig.OverviewLayout.values()[
                                                ByteBufCodecs.VAR_INT.decode(buffer)],
                                        ByteBufCodecs.VAR_INT.decode(buffer),
                                        ByteBufCodecs.VAR_INT.decode(buffer),
                                        ByteBufCodecs.VAR_INT.decode(buffer),
                                        ByteBufCodecs.VAR_INT.decode(buffer)));
                    }

                    @Override
                    public void encode(ByteBuf buffer, Payload payload) {
                        QuadHotbarPolicy p = payload.policy;
                        buffer.writeBoolean(p.allowFreedom());
                        buffer.writeBoolean(p.overviewAllowed());
                        buffer.writeBoolean(p.showOverviewButton());
                        buffer.writeBoolean(p.containerOverviewAllowed());
                        ByteBufCodecs.VAR_INT.encode(buffer, p.overviewLayout().ordinal());
                        ByteBufCodecs.VAR_INT.encode(buffer, p.pageLimit());
                        ByteBufCodecs.VAR_INT.encode(buffer, p.rowLimit());
                        ByteBufCodecs.VAR_INT.encode(buffer, p.maxOverviewWidth());
                        ByteBufCodecs.VAR_INT.encode(buffer, p.maxOverviewHeight());
                    }
                };

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
    }
}
