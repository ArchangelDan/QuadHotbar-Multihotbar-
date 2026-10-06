package com.archiangel.quadhotbar.mixin;

import com.archiangel.quadhotbar.QuadHotbarConnectionGuard;

import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraftforge.network.HandshakeHandler;
import net.minecraftforge.network.HandshakeMessages;
import net.minecraftforge.network.NetworkEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/** Stop incompatible peers before player/world synchronization, without bypassing Forge checks. */
@Mixin(value = HandshakeHandler.class, remap = false)
public abstract class ForgeHandshakeMixin {
    @Inject(method = "handleServerModListOnClient", at = @At("HEAD"), cancellable = true)
    private void quadhotbar$rejectOldServer(
            HandshakeMessages.S2CModList reply,
            Supplier<NetworkEvent.Context> supplier,
            CallbackInfo ci) {
        var status = QuadHotbarConnectionGuard.status(reply.getChannels());
        if (!status.rejected()) return;
        com.archiangel.quadhotbar.client.QuadHotbarClient.rejectIncompatibleServer(supplier.get());
        ci.cancel();
    }

    @Inject(method = "handleClientModListOnServer", at = @At("HEAD"), cancellable = true)
    private void quadhotbar$rejectOldClient(
            HandshakeMessages.C2SModListReply reply,
            Supplier<NetworkEvent.Context> supplier,
            CallbackInfo ci) {
        var status = QuadHotbarConnectionGuard.status(reply.getChannels());
        if (!status.rejected()) return;
        var context = supplier.get();
        context.setPacketHandled(true);
        // 1.20.1 has not received ClientInformation at this login stage. English fallback is safe.
        var connection = context.getNetworkManager();
        if (connection.getPacketListener() instanceof ServerLoginPacketListenerImpl login)
            login.disconnect(QuadHotbarConnectionGuard.reason(status, "en_us"));
        else connection.disconnect(QuadHotbarConnectionGuard.reason(status, "en_us"));
        ci.cancel();
    }
}
