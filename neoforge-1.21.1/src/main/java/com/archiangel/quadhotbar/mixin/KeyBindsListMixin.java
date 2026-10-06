package com.archiangel.quadhotbar.mixin;

import com.archiangel.quadhotbar.client.QuadHotbarKeyMappings;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Arrays;

@Mixin(KeyBindsList.class)
public abstract class KeyBindsListMixin {
    @ModifyExpressionValue(
            method = "<init>",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/Options;keyMappings:[Lnet/minecraft/client/KeyMapping;"))
    private KeyMapping[] quadhotbar$visibleBindings(KeyMapping[] mappings) {
        // Filter only the UI copy. Options retains every binding for saving, remapping and
        // reset-all.
        return Arrays.stream(mappings)
                .filter(QuadHotbarKeyMappings::isVisibleInControls)
                .toArray(KeyMapping[]::new);
    }
}
