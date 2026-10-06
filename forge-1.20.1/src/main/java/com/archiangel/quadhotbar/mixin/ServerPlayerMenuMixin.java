package com.archiangel.quadhotbar.mixin;

import com.archiangel.quadhotbar.QuadHotbarOverviewNetwork;

import net.minecraft.server.level.ServerPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge's opening hook closes even inventoryMenu, unlike vanilla/NeoForge openMenu. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMenuMixin {
    @Inject(method = "doCloseContainer", at = @At("HEAD"), cancellable = true)
    private void quadhotbar$keepSharedInventory(CallbackInfo ci) {
        if (QuadHotbarOverviewNetwork.preservesVanillaMenu((ServerPlayer) (Object) this))
            ci.cancel();
    }
}
