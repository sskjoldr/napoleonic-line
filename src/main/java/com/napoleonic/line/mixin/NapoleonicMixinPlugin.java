package com.napoleonic.line.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Musket Mod mixins are skipped when that mod is not installed, so this mod still boots.
 */
public class NapoleonicMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Checked before GunItemMixin: the Thirty Years War mixin name also ends with GunItemMixin.
        if (mixinClassName.endsWith("ThirtyYearsWarGunItemMixin")) {
            return thirtyYearsWarLoaded();
        }
        if (mixinClassName.endsWith("GunItemMixin") || mixinClassName.endsWith("BulletEntityMixin")) {
            return LoadingModList.get() != null && LoadingModList.get().getModFileById("musketmod") != null;
        }
        return true;
    }

    private static boolean thirtyYearsWarLoaded() {
        return LoadingModList.get() != null && LoadingModList.get().getModFileById("thirtyyearswar") != null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        // Added only when the mod is installed, so Mixin does not look up GunItem on a server without it.
        if (thirtyYearsWarLoaded()) {
            return List.of("ThirtyYearsWarGunItemMixin");
        }
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
