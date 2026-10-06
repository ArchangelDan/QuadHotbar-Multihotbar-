package com.archiangel.quadhotbar.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow public net.minecraft.server.level.ServerPlayer player;

    @Inject(method = "handlePlaceRecipe", at = @At("TAIL"))
    private void quadhotbar$vanillaRecipeInOverview(
            net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket packet,
            CallbackInfo ci) {
        if (!player.isSpectator()
                && player.containerMenu
                        instanceof com.archiangel.quadhotbar.QuadHotbarOverviewMenu menu
                && !menu.standalone
                && menu.containerId == packet.getContainerId()
                && menu.stillValid(player)) {
            player.server
                    .getRecipeManager()
                    .byKey(packet.getRecipe())
                    .ifPresent(
                            recipe -> {
                                player.inventoryMenu.handlePlacement(
                                        packet.isShiftDown(), recipe, player);
                                menu.broadcastFullState();
                            });
        }
    }

    @Redirect(
            method = "handleSetCarriedItem",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/player/Inventory;getSelectionSize()I"))
    private int quadhotbar$allowFullInventorySelection() {
        return Inventory.INVENTORY_SIZE;
    }
}
