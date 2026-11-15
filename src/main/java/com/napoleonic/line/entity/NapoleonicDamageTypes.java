package com.napoleonic.line.entity;

import com.napoleonic.line.NapoleonicLineMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class NapoleonicDamageTypes {
    public static final ResourceKey<DamageType> MUSKET_BALL = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(NapoleonicLineMod.MODID, "musket_ball")
    );

    public static DamageSource musketBall(Level level, Entity bullet, @Nullable Entity shooter) {
        var registry = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        var holderOpt = registry.getHolder(MUSKET_BALL);
        if (holderOpt.isPresent()) {
            return new DamageSource(holderOpt.get(), bullet, shooter != null ? shooter : bullet);
        }
        return level.damageSources().indirectMagic(bullet, shooter != null ? shooter : bullet);
    }

    private NapoleonicDamageTypes() {
    }
}
