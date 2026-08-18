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
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;getSelectionSize()I"),
            require = 0
    )
    private int quadhotbar$allowFullInventorySelectionNamed() {
        return Inventory.INVENTORY_SIZE;
    }

    @Redirect(
            method = "m_7798_",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;m_36059_()I"),
            remap = false
    )
    private int quadhotbar$allowFullInventorySelectionSrg() {
        return Inventory.INVENTORY_SIZE;
    }
}
