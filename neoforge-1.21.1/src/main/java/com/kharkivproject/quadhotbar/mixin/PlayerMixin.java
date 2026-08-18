package com.kharkivproject.quadhotbar.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin {

    private static final String QUADHOTBAR_SELECTED_SLOT_TAG = "QuadHotbarSelectedSlot";

    @Shadow
    public abstract Inventory getInventory();

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void quadhotbar$saveSelectedSlot(CompoundTag compound, CallbackInfo ci) {
        compound.putInt(QUADHOTBAR_SELECTED_SLOT_TAG, Mth.clamp(this.getInventory().selected, 0, Inventory.INVENTORY_SIZE - 1));
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void quadhotbar$loadSelectedSlot(CompoundTag compound, CallbackInfo ci) {
        if (compound.contains(QUADHOTBAR_SELECTED_SLOT_TAG)) {
            this.getInventory().selected = Mth.clamp(compound.getInt(QUADHOTBAR_SELECTED_SLOT_TAG), 0, Inventory.INVENTORY_SIZE - 1);
        }
    }
}
