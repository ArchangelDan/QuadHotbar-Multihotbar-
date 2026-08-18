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

    @Shadow
    public int selected;

    @Shadow
    @Final
    public NonNullList<ItemStack> items;

    @Inject(method = "getSelected", at = @At("HEAD"), cancellable = true)
    private void quadhotbar$getSelected(CallbackInfoReturnable<ItemStack> cir) {
        if (this.selected >= 0 && this.selected < this.items.size()) {
            cir.setReturnValue(this.items.get(this.selected));
        }
    }

    @Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
    private void quadhotbar$getDestroySpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        ItemStack selectedStack = this.selected >= 0 && this.selected < this.items.size() ? this.items.get(this.selected) : ItemStack.EMPTY;
        cir.setReturnValue(selectedStack.getDestroySpeed(state));
    }
}
