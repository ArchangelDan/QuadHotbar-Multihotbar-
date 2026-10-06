package com.archiangel.quadhotbar;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.payload.ModdedNetworkQueryComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Reject incompatible installed clients before registry/player/inventory synchronization. */
public final class QuadHotbarConnectionGuard {
    private static final Map<String, PacketFlow> REQUIRED =
            Map.of(
                    "server_support", PacketFlow.CLIENTBOUND,
                    "page_sync", PacketFlow.CLIENTBOUND,
                    "selection_sync", PacketFlow.CLIENTBOUND,
                    "player_policy", PacketFlow.CLIENTBOUND,
                    "overview_return", PacketFlow.CLIENTBOUND,
                    "container_overview_header", PacketFlow.CLIENTBOUND,
                    "container_overview_frame", PacketFlow.CLIENTBOUND,
                    "page_request", PacketFlow.SERVERBOUND,
                    "overview_request", PacketFlow.SERVERBOUND,
                    "container_overview_action", PacketFlow.SERVERBOUND);

    public enum Status {
        NOT_INSTALLED,
        COMPATIBLE,
        OUTDATED,
        INCOMPATIBLE;

        public boolean rejected() {
            return this == OUTDATED || this == INCOMPATIBLE;
        }
    }

    private QuadHotbarConnectionGuard() {}

    public static Status status(
            Map<ConnectionProtocol, Set<ModdedNetworkQueryComponent>> channels) {
        boolean installed = false;
        Map<String, ModdedNetworkQueryComponent> play = new HashMap<>();
        for (var entry : channels.entrySet()) {
            for (var channel : entry.getValue()) {
                if (!channel.id().getNamespace().equals(QuadHotbar.MODID)) continue;
                installed = true;
                if (!channel.version().equals(QuadHotbarNetwork.PROTOCOL)) {
                    try {
                        return Integer.parseInt(channel.version())
                                        < Integer.parseInt(QuadHotbarNetwork.PROTOCOL)
                                ? Status.OUTDATED
                                : Status.INCOMPATIBLE;
                    } catch (NumberFormatException ignored) {
                        return Status.INCOMPATIBLE;
                    }
                }
                if (entry.getKey() == ConnectionProtocol.PLAY
                        && play.put(channel.id().getPath(), channel) != null)
                    return Status.INCOMPATIBLE;
            }
        }
        // Do not turn optional client/server installation into a new mandatory dependency.
        if (!installed) return Status.NOT_INSTALLED;
        for (var required : REQUIRED.entrySet()) {
            var channel = play.get(required.getKey());
            if (channel == null
                    || !channel.flow().equals(java.util.Optional.of(required.getValue())))
                return Status.INCOMPATIBLE;
        }
        return Status.COMPATIBLE;
    }

    public static Component reason(Status status, String language) {
        if (!status.rejected()) throw new IllegalArgumentException("Client is not incompatible");
        boolean russian = "ru_ru".equalsIgnoreCase(language);
        boolean outdated = status == Status.OUTDATED;
        String fallback =
                russian
                        ? (outdated
                                        ? "Устаревшая версия QuadHotbar."
                                        : "Несовместимая версия QuadHotbar.")
                                + "\nУстановите QuadHotbar %s"
                        : (outdated
                                        ? "Outdated QuadHotbar version."
                                        : "Incompatible QuadHotbar version.")
                                + "\nInstall QuadHotbar %s";
        String version =
                ModList.get()
                        .getModContainerById(QuadHotbar.MODID)
                        .orElseThrow()
                        .getModInfo()
                        .getVersion()
                        .toString();
        // Old clients do not have our new language keys: the locale-specific fallback is essential.
        return Component.translatableWithFallback(
                outdated
                        ? "quadhotbar.disconnect.outdated_version"
                        : "quadhotbar.disconnect.incompatible_version",
                fallback,
                version);
    }
}
