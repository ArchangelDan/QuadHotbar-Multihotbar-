package com.archiangel.quadhotbar.gametest;

import com.archiangel.quadhotbar.QuadHotbar;
import com.archiangel.quadhotbar.QuadHotbarConnectionGuard;
import com.archiangel.quadhotbar.QuadHotbarNetwork;
import com.archiangel.quadhotbar.network.PayloadTransport;

import io.netty.buffer.Unpooled;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.HandshakeHandler;
import net.minecraftforge.network.HandshakeMessages;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@GameTestHolder("quadhotbar")
@PrefixGameTestTemplate(false)
public final class ConnectionGuardTests {
    @GameTest(template = "empty", batch = "connection")
    public static void oldAndIncompleteClientsRejected(GameTestHelper helper) throws Exception {
        var method = NetworkRegistry.class.getDeclaredMethod("buildChannelVersions");
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var current = (Map<ResourceLocation, String>) method.invoke(null);
        helper.assertTrue(
                QuadHotbarConnectionGuard.status(current)
                        == QuadHotbarConnectionGuard.Status.COMPATIBLE,
                "Current channel registry rejected");
        helper.assertTrue(
                "2".equals(current.get(PayloadTransport.CHANNEL_ID)),
                "Legacy clients cannot reach the native short login rejection");
        helper.assertTrue(
                QuadHotbarConnectionGuard.status(Map.of(PayloadTransport.CHANNEL_ID, "2"))
                        == QuadHotbarConnectionGuard.Status.OUTDATED,
                "Released 1.0.x client accepted");
        for (String version : List.of("1", "2", "10", "12", "broken", ""))
            helper.assertTrue(
                    QuadHotbarConnectionGuard.status(
                                    Map.of(
                                            PayloadTransport.CHANNEL_ID,
                                            "2",
                                            PayloadTransport.PROTOCOL_CHANNEL_ID,
                                            version))
                            .rejected(),
                    "Incompatible schema accepted: " + version);
        helper.assertTrue(
                QuadHotbarConnectionGuard.status(
                                Map.of(
                                        PayloadTransport.PROTOCOL_CHANNEL_ID,
                                        QuadHotbarNetwork.PROTOCOL))
                        .rejected(),
                "Incomplete handshake accepted");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "connection")
    public static void optionalAndOtherModsUnaffected(GameTestHelper helper) {
        helper.assertTrue(
                QuadHotbarConnectionGuard.status(Map.of())
                        == QuadHotbarConnectionGuard.Status.NOT_INSTALLED,
                "Optional clients rejected");
        helper.assertTrue(
                QuadHotbarConnectionGuard.status(
                                Map.of(new ResourceLocation("create", "test"), "old"))
                        == QuadHotbarConnectionGuard.Status.NOT_INSTALLED,
                "Another mod affected");
        for (String language : List.of("en_us", "ru_ru")) {
            var reason =
                    (TranslatableContents)
                            QuadHotbarConnectionGuard.reason(
                                            QuadHotbarConnectionGuard.Status.OUTDATED, language)
                                    .getContents();
            helper.assertTrue(
                    reason.getFallback()
                            .contains(language.equals("ru_ru") ? "Устаревшая" : "Outdated"),
                    "Missing language fallback");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "connection")
    public static void nativeLoginStopsBeforeWorldJoin(GameTestHelper helper) throws Exception {
        Component[] rejected = {null};
        var connection =
                new Connection(PacketFlow.SERVERBOUND) {
                    @Override
                    public boolean isMemoryConnection() {
                        return true;
                    }
                };
        connection.setListener(
                new ServerLoginPacketListenerImpl(helper.getLevel().getServer(), connection) {
                    @Override
                    public void disconnect(Component reason) {
                        rejected[0] = reason;
                    }
                });
        var constructor =
                HandshakeHandler.class.getDeclaredConstructor(
                        Connection.class, NetworkDirection.class);
        constructor.setAccessible(true);
        var handler = constructor.newInstance(connection, NetworkDirection.LOGIN_TO_SERVER);
        var contextConstructor =
                NetworkEvent.Context.class.getDeclaredConstructor(
                        Connection.class, NetworkDirection.class, int.class);
        contextConstructor.setAccessible(true);
        var context =
                contextConstructor.newInstance(connection, NetworkDirection.LOGIN_TO_SERVER, 0);
        var input = new FriendlyByteBuf(Unpooled.buffer());
        HandshakeMessages.C2SModListReply reply;
        try {
            input.writeVarInt(1).writeUtf("quadhotbar");
            input.writeVarInt(1).writeResourceLocation(PayloadTransport.CHANNEL_ID).writeUtf("2");
            input.writeVarInt(0);
            reply = HandshakeMessages.C2SModListReply.decode(input);
        } finally {
            input.release();
        }
        var dispatch =
                HandshakeHandler.class.getDeclaredMethod(
                        "handleClientModListOnServer",
                        HandshakeMessages.C2SModListReply.class,
                        Supplier.class);
        dispatch.setAccessible(true);
        int playersBefore = helper.getLevel().getServer().getPlayerCount();
        dispatch.invoke(handler, reply, (Supplier<NetworkEvent.Context>) () -> context);
        helper.assertTrue(
                rejected[0] != null && context.getPacketHandled(),
                "Real Forge login hook failed to stop an old client");
        var reason = (TranslatableContents) rejected[0].getContents();
        helper.assertTrue(
                reason.getKey().equals("quadhotbar.disconnect.outdated_version")
                        && reason.getArgs()[0].equals(
                                ModList.get()
                                        .getModContainerById(QuadHotbar.MODID)
                                        .orElseThrow()
                                        .getModInfo()
                                        .getVersion()
                                        .toString()),
                "Wrong server version in disconnect");
        helper.assertTrue(
                helper.getLevel().getServer().getPlayerCount() == playersBefore,
                "Old client entered the world");
        helper.succeed();
    }
}
