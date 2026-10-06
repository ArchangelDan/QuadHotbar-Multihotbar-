package com.archiangel.quadhotbar.mixin;

import com.archiangel.quadhotbar.QuadHotbarConnectionGuard;

import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.neoforged.neoforge.network.payload.ModdedNetworkQueryPayload;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ServerConfigurationPacketListenerImplMixin {
    @Shadow private ClientInformation clientInformation;

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void quadhotbar$checkClientVersion(
            ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (!(packet.payload() instanceof ModdedNetworkQueryPayload query)) return;
        var status = QuadHotbarConnectionGuard.status(query.queries());
        if (!status.rejected()) return;
        // Send a plain vanilla disconnect, not NeoForge's channel-mismatch screen.
        ((ServerConfigurationPacketListenerImpl) (Object) this)
                .disconnect(QuadHotbarConnectionGuard.reason(status, clientInformation.language()));
        ci.cancel();
    }
}
