package com.archiangel.quadhotbar.mixin;

import com.archiangel.quadhotbar.client.QuadHotbarOverviewScreen;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

/** Keep item, tab and widget tooltips above the draggable inventory overlay. */
@Mixin(GuiGraphics.class)
public abstract class OverviewTooltipLayerMixin {
    @Shadow
    public abstract PoseStack pose();

    @WrapMethod(method = "renderTooltipInternal")
    private void quadhotbar$tooltipLayer(
            Font font,
            List<ClientTooltipComponent> lines,
            int x,
            int y,
            ClientTooltipPositioner positioner,
            Operation<Void> original) {
        if (!(Minecraft.getInstance().screen instanceof QuadHotbarOverviewScreen)) {
            original.call(font, lines, x, y, positioner);
            return;
        }
        pose().pushPose();
        try {
            pose().translate(0, 0, 500);
            original.call(font, lines, x, y, positioner);
        } finally {
            pose().popPose();
        }
    }
}
