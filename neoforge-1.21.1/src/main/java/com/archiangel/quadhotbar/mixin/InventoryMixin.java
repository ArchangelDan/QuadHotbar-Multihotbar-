package com.archiangel.quadhotbar.mixin;

import com.archiangel.quadhotbar.QuadHotbarNetwork;
import com.archiangel.quadhotbar.QuadHotbarPages;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class InventoryMixin {

    @Shadow public int selected;

    @Shadow @Final public NonNullList<ItemStack> items;

    @Shadow @Final public Player player;

    @Inject(method = "getSelected", at = @At("HEAD"), cancellable = true)
    private void quadhotbar$getSelected(CallbackInfoReturnable<ItemStack> cir) {
        if (this.selected >= 0 && this.selected < this.items.size()) {
            cir.setReturnValue(this.items.get(this.selected));
        }
    }

    @Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
    private void quadhotbar$getDestroySpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        ItemStack selectedStack =
                this.selected >= 0 && this.selected < this.items.size()
                        ? this.items.get(this.selected)
                        : ItemStack.EMPTY;
        cir.setReturnValue(selectedStack.getDestroySpeed(state));
    }

    @Inject(
            method = "add(ILnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void quadhotbar$addToEnabledPages(
            int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot == -1 && this.player instanceof ServerPlayer serverPlayer) {
            QuadHotbarNetwork.enforceServerLimits(serverPlayer);
            if (QuadHotbarPages.state(this.player).pageCount <= 1) {
                return;
            }
            boolean added = QuadHotbarPages.addPickup(this.player, stack);
            if (added) {
                QuadHotbarNetwork.sendPageSync(serverPlayer);
            }
            cir.setReturnValue(added);
        }
    }

    @Inject(method = "dropAll", at = @At("TAIL"))
    private void quadhotbar$dropExtraPages(CallbackInfo ci) {
        if (!this.player.level().isClientSide) {
            QuadHotbarPages.dropStored(this.player);
        }
    }
}
