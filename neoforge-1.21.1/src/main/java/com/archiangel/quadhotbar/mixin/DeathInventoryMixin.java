package com.archiangel.quadhotbar.mixin;

import com.archiangel.quadhotbar.QuadHotbarPages;
import com.archiangel.quadhotbar.compat.DeathPageAccess;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;

/** Both mods relocate the same death API. No dependency is required when they are absent. */
@Pseudo
@Mixin(
        targets = {
            "de.maxhenkel.corpse.corelib.death.Death",
            "de.maxhenkel.gravestone.corelib.death.Death"
        },
        remap = false)
public abstract class DeathInventoryMixin implements DeathPageAccess {
    @Shadow
    public abstract NonNullList<ItemStack> getAdditionalItems();

    @Unique private int[] quadhotbar$positions = new int[0];
    @Unique private int quadhotbar$activePage = -1;

    @Unique
    private final IdentityHashMap<ItemStack, Integer> quadhotbar$remaining =
            new IdentityHashMap<>();

    @Unique
    private final IdentityHashMap<ItemStack, Integer> quadhotbar$dropped = new IdentityHashMap<>();

    @Inject(method = "fromPlayer", at = @At("RETURN"))
    private static void quadhotbar$captureActivePage(
            Player player, CallbackInfoReturnable<Object> cir) {
        if (cir.getReturnValue() instanceof DeathPageAccess death)
            death.quadhotbar$setActivePage(QuadHotbarPages.state(player).activePage);
    }

    @Inject(method = "processDrops", at = @At("HEAD"))
    private void quadhotbar$captureAddresses(Collection<ItemEntity> drops, CallbackInfo ci) {
        quadhotbar$dropped.clear();
        quadhotbar$remaining.clear();
        for (var entity : drops) {
            var tag = entity.getPersistentData();
            if (tag.contains("QuadHotbarPageSlot")) {
                int address = tag.getInt("QuadHotbarPageSlot");
                if (address >= 0 && address < QuadHotbarPages.MAX_PAGES * 36)
                    quadhotbar$dropped.put(entity.getItem(), address);
            }
        }
    }

    @Inject(method = "processDrops", at = @At("TAIL"))
    private void quadhotbar$mapCanonicalItems(Collection<ItemEntity> drops, CallbackInfo ci) {
        var items = getAdditionalItems();
        quadhotbar$positions = new int[items.size()];
        Arrays.fill(quadhotbar$positions, -1);
        for (int i = 0; i < items.size(); i++) {
            var address = quadhotbar$dropped.get(items.get(i));
            if (address != null) {
                quadhotbar$positions[i] = address;
                quadhotbar$remaining.put(items.get(i), address);
            }
        }
        quadhotbar$dropped.clear();
    }

    @Inject(
            method =
                    "toNBT(Lnet/minecraft/core/HolderLookup$Provider;Z)Lnet/minecraft/nbt/CompoundTag;",
            at = @At("RETURN"))
    private void quadhotbar$saveAddresses(
            HolderLookup.Provider registries,
            boolean items,
            CallbackInfoReturnable<CompoundTag> cir) {
        if (quadhotbar$activePage >= 0)
            cir.getReturnValue().putInt("QuadHotbarActivePage", quadhotbar$activePage);
        if (items && quadhotbar$positions.length > 0) {
            // Corelib compacts empty stacks when saving; compact the addresses identically.
            int[] addresses = quadhotbar$pageSlots();
            cir.getReturnValue()
                    .putIntArray(
                            "QuadHotbarPageSlots",
                            java.util.stream.IntStream.range(0, getAdditionalItems().size())
                                    .filter(i -> !getAdditionalItems().get(i).isEmpty())
                                    .map(i -> addresses[i])
                                    .toArray());
        }
    }

    @Inject(method = "fromNBT", at = @At("RETURN"))
    private static void quadhotbar$loadAddresses(
            HolderLookup.Provider registries, CompoundTag tag, CallbackInfoReturnable<Object> cir) {
        if (cir.getReturnValue() instanceof DeathPageAccess death) {
            if (tag.contains("QuadHotbarActivePage"))
                death.quadhotbar$setActivePage(tag.getInt("QuadHotbarActivePage"));
            int[] addresses = tag.getIntArray("QuadHotbarPageSlots");
            death.quadhotbar$setPageSlots(
                    Arrays.copyOf(
                            addresses, Math.min(addresses.length, QuadHotbarPages.MAX_PAGES * 36)));
        }
    }

    @Override
    public int[] quadhotbar$pageSlots() {
        int[] positions = new int[getAdditionalItems().size()];
        for (int i = 0; i < positions.length; i++)
            positions[i] = quadhotbar$remaining.getOrDefault(getAdditionalItems().get(i), -1);
        return positions;
    }

    @Override
    public void quadhotbar$setPageSlots(int[] slots) {
        quadhotbar$positions = slots;
        quadhotbar$remaining.clear();
        for (int i = 0; i < Math.min(slots.length, getAdditionalItems().size()); i++)
            if (slots[i] >= 0 && slots[i] < QuadHotbarPages.MAX_PAGES * 36)
                quadhotbar$remaining.put(getAdditionalItems().get(i), slots[i]);
    }

    @Override
    public int quadhotbar$activePage() {
        return quadhotbar$activePage;
    }

    @Override
    public void quadhotbar$setActivePage(int page) {
        quadhotbar$activePage = page >= 0 && page < QuadHotbarPages.MAX_PAGES ? page : -1;
    }
}
