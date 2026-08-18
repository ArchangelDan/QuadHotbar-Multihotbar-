package com.kharkivproject.quadhotbar.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class InventoryMixin {

    @Shadow(remap = false)
    public int f_35977_;

    @Shadow(remap = false)
    @Final
    public NonNullList<ItemStack> f_35974_;

    @Inject(method = "m_36056_", at = @At("HEAD"), cancellable = true, remap = false)
    private void quadhotbar$getSelectedSrg(CallbackInfoReturnable<ItemStack> cir) {
        if (this.f_35977_ >= 0 && this.f_35977_ < this.f_35974_.size())
            cir.setReturnValue(this.f_35974_.get(this.f_35977_));
    }

    @Inject(method = "m_36020_", at = @At("HEAD"), cancellable = true, remap = false)
    private void quadhotbar$getDestroySpeedSrg(BlockState state, CallbackInfoReturnable<Float> cir) {
        ItemStack selectedStack = this.f_35977_ >= 0 && this.f_35977_ < this.f_35974_.size() ? this.f_35974_.get(this.f_35977_) : ItemStack.EMPTY;
        cir.setReturnValue(selectedStack.getDestroySpeed(state));
    }
}
