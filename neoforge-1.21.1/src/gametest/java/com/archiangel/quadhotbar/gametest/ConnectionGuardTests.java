package com.archiangel.quadhotbar.gametest;

import com.archiangel.quadhotbar.QuadHotbar;
import com.archiangel.quadhotbar.QuadHotbarConnectionGuard;
import com.archiangel.quadhotbar.QuadHotbarNetwork;
import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.payload.ModdedNetworkQueryComponent;
import net.neoforged.neoforge.network.payload.ModdedNetworkQueryPayload;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistration;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@GameTestHolder("quadhotbar")
@PrefixGameTestTemplate(false)
public final class ConnectionGuardTests {
    private static ModdedNetworkQueryComponent channel(
            String path, String version, PacketFlow flow) {
        return new ModdedNetworkQueryComponent(
                ResourceLocation.fromNamespaceAndPath(QuadHotbar.MODID, path),
                version,
                Optional.of(flow),
                true);
    }

    @SuppressWarnings("unchecked")
    private static Set<ModdedNetworkQueryComponent> currentChannels() throws Exception {
        var field = NetworkRegistry.class.getDeclaredField("PAYLOAD_REGISTRATIONS");
        field.setAccessible(true);
        var registrations =
                (Map<ConnectionProtocol, Map<ResourceLocation, PayloadRegistration<?>>>)
                        field.get(null);
        Set<ModdedNetworkQueryComponent> result = new HashSet<>();
        for (var registration : registrations.get(ConnectionProtocol.PLAY).values()) {
            if (registration.id().getNamespace().equals(QuadHotbar.MODID))
                result.add(new ModdedNetworkQueryComponent(registration));
        }
        return result;
    }

    @GameTest(template = "empty", batch = "connection")
    public static void oldAndIncompleteClientsRejected(GameTestHelper helper) throws Exception {
        var current = currentChannels();
        helper.assertTrue(
                !current.isEmpty()
                        && QuadHotbarConnectionGuard.status(
                                        Map.of(ConnectionProtocol.PLAY, current))
                                == QuadHotbarConnectionGuard.Status.COMPATIBLE,
                "Guard rejected the real current payload registry");
        for (String protocol : List.of("1", "2", "10")) {
            var old = Set.of(channel("server_support", protocol, PacketFlow.CLIENTBOUND));
            helper.assertTrue(
                    QuadHotbarConnectionGuard.status(Map.of(ConnectionProtocol.PLAY, old))
                            == QuadHotbarConnectionGuard.Status.OUTDATED,
                    "Old release/beta accepted: " + protocol);
        }
        for (var missing : current) {
            var incomplete = new HashSet<>(current);
            incomplete.remove(missing);
            helper.assertTrue(
                    QuadHotbarConnectionGuard.status(Map.of(ConnectionProtocol.PLAY, incomplete))
                            .rejected(),
                    "Missing payload accepted: " + missing.id());
        }
        for (String protocol : List.of("12", "broken", "")) {
            helper.assertTrue(
                    QuadHotbarConnectionGuard.status(
                                    Map.of(
                                            ConnectionProtocol.PLAY,
                                            Set.of(
                                                    channel(
                                                            "server_support",
                                                            protocol,
                                                            PacketFlow.CLIENTBOUND))))
                            .rejected(),
                    "Unknown protocol accepted");
        }
        var wrongFlow = new HashSet<>(current);
        wrongFlow.removeIf(c -> c.id().getPath().equals("page_request"));
        wrongFlow.add(channel("page_request", QuadHotbarNetwork.PROTOCOL, PacketFlow.CLIENTBOUND));
        helper.assertTrue(
                QuadHotbarConnectionGuard.status(Map.of(ConnectionProtocol.PLAY, wrongFlow))
                        .rejected(),
                "Wrong packet direction accepted");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "connection")
    public static void optionalAndOtherModsUnaffected(GameTestHelper helper) {
        helper.assertTrue(
                QuadHotbarConnectionGuard.status(Map.of())
                        == QuadHotbarConnectionGuard.Status.NOT_INSTALLED,
                "Local/empty negotiation was rejected");
        var other =
                new ModdedNetworkQueryComponent(
                        ResourceLocation.fromNamespaceAndPath("create", "test"),
                        "old",
                        Optional.of(PacketFlow.CLIENTBOUND),
                        true);
        helper.assertTrue(
                QuadHotbarConnectionGuard.status(Map.of(ConnectionProtocol.PLAY, Set.of(other)))
                        == QuadHotbarConnectionGuard.Status.NOT_INSTALLED,
                "Guard interfered with another mod");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "connection")
    public static void nativeConfigurationStopsBeforeWorldJoin(GameTestHelper helper) {
        for (String language : List.of("en_us", "ru_ru")) {
            var defaults = ClientInformation.createDefault();
            var info =
                    new ClientInformation(
                            language,
                            defaults.viewDistance(),
                            defaults.chatVisibility(),
                            defaults.chatColors(),
                            defaults.modelCustomisation(),
                            defaults.mainHand(),
                            defaults.textFilteringEnabled(),
                            defaults.allowsListing());
            Component[] reason = {null};
            int[] packets = {0};
            int playersBefore = helper.getLevel().getServer().getPlayerCount();
            var listener =
                    new ServerConfigurationPacketListenerImpl(
                            helper.getLevel().getServer(),
                            new Connection(PacketFlow.SERVERBOUND),
                            new CommonListenerCookie(
                                    new GameProfile(UUID.randomUUID(), "OldClient"),
                                    0,
                                    info,
                                    false,
                                    ConnectionType.OTHER)) {
                        @Override
                        public void disconnect(Component component) {
                            reason[0] = component;
                        }

                        @Override
                        public void send(Packet<?> packet, PacketSendListener callback) {
                            packets[0]++;
                        }
                    };
            listener.handleCustomPayload(
                    new ServerboundCustomPayloadPacket(
                            new ModdedNetworkQueryPayload(
                                    Map.of(
                                            ConnectionProtocol.PLAY,
                                            Set.of(
                                                    channel(
                                                            "server_support",
                                                            "2",
                                                            PacketFlow.CLIENTBOUND))))));
            helper.assertTrue(
                    reason[0] != null && packets[0] == 0,
                    "Real configuration hook did not stop native channel negotiation");
            var contents = (TranslatableContents) reason[0].getContents();
            helper.assertTrue(
                    contents.getKey().equals("quadhotbar.disconnect.outdated_version")
                            && contents.getArgs()[0].equals(
                                    net.neoforged.fml.ModList.get()
                                            .getModContainerById(QuadHotbar.MODID)
                                            .orElseThrow()
                                            .getModInfo()
                                            .getVersion()
                                            .toString()),
                    "Disconnect recommends wrong server version");
            helper.assertTrue(
                    contents.getFallback()
                            .contains(language.equals("ru_ru") ? "Устаревшая" : "Outdated"),
                    "Old clients without the new language keys cannot read the reason");
            helper.assertTrue(
                    helper.getLevel().getServer().getPlayerCount() == playersBefore,
                    "Old client entered the world before rejection");
        }
        helper.succeed();
    }
}
