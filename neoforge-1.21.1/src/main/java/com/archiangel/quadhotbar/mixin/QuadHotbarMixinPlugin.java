package com.archiangel.quadhotbar.mixin;

import net.neoforged.fml.loading.FMLLoader;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Do not probe/load optional death-mod classes when their owning mod is absent. */
public final class QuadHotbarMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (mixin.endsWith("CorpseTransferMixin"))
            return FMLLoader.getLoadingModList().getMods().stream()
                    .anyMatch(mod -> mod.getModId().equals("corpse"));
        if (mixin.endsWith("GraveStoneRecoveryMixin"))
            return FMLLoader.getLoadingModList().getMods().stream()
                    .anyMatch(mod -> mod.getModId().equals("gravestone"));
        if (mixin.endsWith("DeathInventoryMixin")) {
            String id = target.startsWith("de.maxhenkel.corpse.") ? "corpse" : "gravestone";
            return FMLLoader.getLoadingModList().getMods().stream()
                    .anyMatch(mod -> mod.getModId().equals(id));
        }
        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> own, Set<String> other) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String name, ClassNode target, String mixin, IMixinInfo info) {}

    @Override
    public void postApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
}
