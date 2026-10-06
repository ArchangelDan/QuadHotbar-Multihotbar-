package com.archiangel.quadhotbar.mixin;

import com.archiangel.quadhotbar.compat.CorpsePages;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
        targets = {
            "de.maxhenkel.corpse.gui.CorpseAdditionalContainer",
            "de.maxhenkel.corpse.gui.CorpseInventoryContainer"
        },
        remap = false)
public abstract class CorpseTransferMixin {
    @Inject(method = "transferItems", at = @At("HEAD"), cancellable = true)
    private void quadhotbar$restoreOriginalPageSlots(CallbackInfo ci) {
        var menu = (AbstractContainerMenu) (Object) this;
        for (var slot : menu.slots) {
            if (slot.container instanceof Inventory inventory
                    && inventory.player instanceof ServerPlayer player) {
                if (CorpsePages.transfer(menu, player)) ci.cancel();
                return;
            }
        }
    }
}
