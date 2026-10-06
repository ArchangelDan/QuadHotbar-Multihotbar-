package com.archiangel.quadhotbar.mixin;

import com.archiangel.quadhotbar.client.QuadHotbarOverviewScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class OverviewRecipeGhostMixin {
    @Inject(method = "handlePlaceRecipe", at = @At("TAIL"))
    private void quadhotbar$vanillaGhost(
            ClientboundPlaceGhostRecipePacket packet, CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        if (packet.getContainerId() == 0 && mc.screen instanceof QuadHotbarOverviewScreen overview)
            mc.getConnection()
                    .getRecipeManager()
                    .byKey(packet.getRecipe())
                    .ifPresent(overview::showGhostRecipe);
    }
}
