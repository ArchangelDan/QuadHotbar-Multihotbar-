package com.archiangel.quadhotbar.mixin;

import com.archiangel.quadhotbar.compat.DeathPageRecovery;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "de.maxhenkel.gravestone.blocks.GraveStoneBlock", remap = false)
public abstract class GraveStoneRecoveryMixin {
    @Inject(method = "fillPlayerInventory", at = @At("HEAD"), cancellable = true)
    private void quadhotbar$restoreOriginalPageSlots(
            Player player, @Coerce Object death, CallbackInfoReturnable<NonNullList<ItemStack>> cir)
            throws ReflectiveOperationException {
        // Resolve before moving items; the native caller owns/drops the returned overflow.
        var getAdditional = death.getClass().getMethod("getAdditionalItems");
        var remainder = DeathPageRecovery.restore(player, death, true);
        if (remainder == null) return;
        ((java.util.List<?>) getAdditional.invoke(death)).clear();
        cir.setReturnValue(remainder);
    }
}
