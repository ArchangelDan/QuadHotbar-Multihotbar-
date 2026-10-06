package com.archiangel.quadhotbar.mixin;

import com.archiangel.quadhotbar.client.QuadHotbarKeyMappings;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.controls.KeyBindsList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Arrays;

@Mixin(KeyBindsList.class)
public abstract class KeyBindsListMixin {
    @Redirect(
            method = "<init>",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/Options;keyMappings:[Lnet/minecraft/client/KeyMapping;"))
    private KeyMapping[] quadhotbar$visibleBindings(Options options) {
        KeyMapping[] mappings = options.keyMappings;
        // Filter only the UI copy. Options retains every binding for saving, remapping and
        // reset-all.
        return Arrays.stream(mappings)
                .filter(QuadHotbarKeyMappings::isVisibleInControls)
                .toArray(KeyMapping[]::new);
    }
}
