package com.archiangel.quadhotbar.mixin;

import com.archiangel.quadhotbar.client.QuadHotbarOverviewScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocalPlayer.class)
public abstract class OverviewReturnScreenMixin {
    @Redirect(
            method = "clientSideCloseContainer",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void quadhotbar$keepCursorDuringReturn(Minecraft minecraft, Screen nextScreen) {
        // The close packet precedes the normal-inventory sync and our return payload.
        // Keep the GUI open between them, so Minecraft never grabs/recenters the mouse.
        if (nextScreen == null
                && minecraft.player != null
                && minecraft.player.isAlive()
                && minecraft.level != null
                && minecraft.screen instanceof QuadHotbarOverviewScreen overview
                && overview.isReturningToInventory()) return;
        minecraft.setScreen(nextScreen);
    }
}
