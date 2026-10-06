package com.archiangel.quadhotbar.mixin;

import com.archiangel.quadhotbar.client.QuadHotbarOverviewScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.20.1's private/static slot drawing needs hooks rather than 1.21.1's overrides. */
@Mixin(AbstractContainerScreen.class)
public abstract class OverviewSlotRenderMixin {
    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void quadhotbar$clipSlot(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        if ((Object) this instanceof QuadHotbarOverviewScreen screen)
            screen.beginSlotRendering(graphics);
    }

    @Inject(method = "renderSlot", at = @At("RETURN"))
    private void quadhotbar$endSlot(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        if ((Object) this instanceof QuadHotbarOverviewScreen screen)
            screen.endSlotRendering(graphics);
    }

    @Inject(
            method = "renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;IIII)V",
            at = @At("HEAD"),
            remap = false)
    private static void quadhotbar$clipHighlight(
            GuiGraphics graphics, int x, int y, int z, int color, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof QuadHotbarOverviewScreen screen
                && screen.isRenderingOwnSlots()) screen.beginSlotRendering(graphics);
    }

    @Inject(
            method = "renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;IIII)V",
            at = @At("RETURN"),
            remap = false)
    private static void quadhotbar$endHighlight(
            GuiGraphics graphics, int x, int y, int z, int color, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof QuadHotbarOverviewScreen screen
                && screen.isRenderingOwnSlots()) screen.endSlotRendering(graphics);
    }
}
