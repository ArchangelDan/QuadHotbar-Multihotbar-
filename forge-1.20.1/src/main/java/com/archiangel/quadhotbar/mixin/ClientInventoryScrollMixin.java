package com.archiangel.quadhotbar.mixin;

import com.archiangel.quadhotbar.client.QuadHotbarClientEvents;

import net.minecraft.world.entity.player.Inventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public abstract class ClientInventoryScrollMixin {

    @Inject(method = "swapPaint", at = @At("HEAD"), cancellable = true)
    private void quadhotbar$scrollExtendedHotbar(double direction, CallbackInfo ci) {
        if (QuadHotbarClientEvents.onVanillaHotbarScroll((Inventory) (Object) this, direction)) {
            ci.cancel();
        }
    }
}
