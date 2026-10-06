package com.archiangel.quadhotbar.mixin;

import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractFurnaceMenu.class)
public interface FurnaceMenuAccessor {
    @Invoker("canSmelt")
    boolean quadhotbar$canSmelt(ItemStack stack);

    @Invoker("isFuel")
    boolean quadhotbar$isFuel(ItemStack stack);
}
