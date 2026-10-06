package com.archiangel.quadhotbar.mixin;

import com.archiangel.quadhotbar.client.QuadHotbarOverviewScreen;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Keep item, tab and widget tooltips above the draggable inventory overlay. */
@Mixin(GuiGraphics.class)
public abstract class OverviewTooltipLayerMixin {
    @Shadow
    public abstract PoseStack pose();

    @Inject(method = "renderTooltipInternal", at = @At("HEAD"))
    private void quadhotbar$raiseTooltip(
            Font font,
            List<ClientTooltipComponent> lines,
            int x,
            int y,
            ClientTooltipPositioner positioner,
            CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof QuadHotbarOverviewScreen) {
            pose().pushPose();
            pose().translate(0, 0, 500);
        }
    }

    @Inject(method = "renderTooltipInternal", at = @At("RETURN"))
    private void quadhotbar$restoreTooltip(
            Font font,
            List<ClientTooltipComponent> lines,
            int x,
            int y,
            ClientTooltipPositioner positioner,
            CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof QuadHotbarOverviewScreen) pose().popPose();
    }
}
