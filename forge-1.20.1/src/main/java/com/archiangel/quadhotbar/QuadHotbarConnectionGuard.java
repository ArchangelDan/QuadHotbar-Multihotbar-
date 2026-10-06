package com.archiangel.quadhotbar;

import com.archiangel.quadhotbar.network.PayloadTransport;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.Map;

/** Negotiate the schema before Forge finishes login, including legacy envelope-only clients. */
public final class QuadHotbarConnectionGuard {
    private QuadHotbarConnectionGuard() {}

    public enum Status {
        NOT_INSTALLED,
        COMPATIBLE,
        OUTDATED,
        INCOMPATIBLE;

        public boolean rejected() {
            return this == OUTDATED || this == INCOMPATIBLE;
        }
    }

    public static Status status(Map<ResourceLocation, String> channels) {
        String version = channels.get(PayloadTransport.PROTOCOL_CHANNEL_ID);
        if (version == null)
            return channels.containsKey(PayloadTransport.CHANNEL_ID)
                    ? Status.OUTDATED
                    : Status.NOT_INSTALLED;
        if (!channels.containsKey(PayloadTransport.CHANNEL_ID)) return Status.INCOMPATIBLE;
        if (QuadHotbarNetwork.PROTOCOL.equals(version)) return Status.COMPATIBLE;
        try {
            if (Integer.parseInt(version) < Integer.parseInt(QuadHotbarNetwork.PROTOCOL))
                return Status.OUTDATED;
        } catch (NumberFormatException ignored) {
        }
        return Status.INCOMPATIBLE;
    }

    public static Component reason(Status status, String language) {
        return reason(
                status,
                language,
                ModList.get()
                        .getModContainerById(QuadHotbar.MODID)
                        .orElseThrow()
                        .getModInfo()
                        .getVersion()
                        .toString());
    }

    public static Component reason(Status status, String language, String serverVersion) {
        boolean outdated = status == Status.OUTDATED;
        boolean russian = "ru_ru".equalsIgnoreCase(language);
        String fallback =
                russian
                        ? (outdated
                                ? "Устаревшая версия QuadHotbar.\nУстановите QuadHotbar %s"
                                : "Несовместимая версия QuadHotbar.\nУстановите QuadHotbar %s")
                        : (outdated
                                ? "Outdated QuadHotbar version.\nInstall QuadHotbar %s"
                                : "Incompatible QuadHotbar version.\nInstall QuadHotbar %s");
        return Component.translatableWithFallback(
                outdated
                        ? "quadhotbar.disconnect.outdated_version"
                        : "quadhotbar.disconnect.incompatible_version",
                fallback,
                serverVersion);
    }
}
