package com.kharkivproject.quadhotbar.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Redirect(
            method = "handleSetCarriedItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;getSelectionSize()I")
    )
    private int quadhotbar$allowFullInventorySelection() {
        return Inventory.INVENTORY_SIZE;
    }
}
