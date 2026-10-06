package com.archiangel.quadhotbar.network;

import net.minecraft.resources.ResourceLocation;

/** Typed messages carried by Forge's versioned SimpleChannel. */
public interface ForgePayload {
    Type<? extends ForgePayload> type();

    record Type<T extends ForgePayload>(ResourceLocation id, Class<T> messageClass) {}
}
