package com.napoleonic.line.mixin;

import com.napoleonic.line.config.NapoleonicServerConfig;
import com.napoleonic.line.network.NapoleonicNetwork;
import com.napoleonic.line.network.S2CMusketShotEffectPacket;
import ewewukek.musketmod.BulletEntity;
import ewewukek.musketmod.GunItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GunItem.class)
public abstract class GunItemMixin {
    @Shadow(remap = false)
    public abstract float bulletStdDev();

    @Shadow(remap = false)
    public abstract float bulletSpeed();

    @Shadow(remap = false)
    public abstract int pelletCount();

    @Shadow(remap = false)
    public abstract float bulletDropReduction();

    @Shadow(remap = false)
    public abstract SoundEvent fireSound(ItemStack stack);

    @Inject(method = "getLoadingDuration", at = @At("HEAD"), cancellable = true, remap = false)
    private static void napoleonic$getLoadingDuration(ItemStack stack, CallbackInfoReturnable<Pair<Integer, Integer>> cir) {
        double seconds = 15.0D;
        try {
            if (NapoleonicServerConfig.SPEC.isLoaded()) {
                seconds = NapoleonicServerConfig.PLAYER_RELOAD_SECONDS.get();
            }
        } catch (Exception ignored) {
        }
        int stages = 6;
        int totalTicks = Math.max(20, (int) Math.round(seconds * 20.0D));
        int ticksPerStage = Math.max(5, totalTicks / stages);
        cir.setReturnValue(Pair.of(stages, ticksPerStage));
    }

    @Inject(method = "fire(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void napoleonic$onFire(LivingEntity entity, ItemStack stack, Vec3 direction, Vec3 smokeOffset, CallbackInfo ci) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            ci.cancel();
            return;
        }
        Vec3 origin = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());
        boolean flame = GunItem.hasFlame(stack);
        int count = pelletCount();
        float baseDamage = 35.0F;
        try {
            if (NapoleonicServerConfig.SPEC.isLoaded()) {
                baseDamage = NapoleonicServerConfig.MUSKET_DAMAGE.get().floatValue();
            }
        } catch (Exception ignored) {
        }

        for (int i = 0; i < count; i++) {
            BulletEntity bullet = new BulletEntity(serverLevel);
            bullet.setOwner(entity);
            bullet.setPos(origin);
            bullet.setVelocity(bulletSpeed(), GunItem.addSpread(direction, entity.getRandom(), bulletStdDev()));
            bullet.damage = baseDamage;
            bullet.setDropReduction(bulletDropReduction());
            bullet.setPelletCount(count);
            if (flame) {
                bullet.setSecondsOnFire(100);
                bullet.setSharedFlagOnFire(true);
            }
            serverLevel.addFreshEntity(bullet);
        }

        SoundEvent sound = fireSound(stack);
        if (sound != null) {
            float volume = 4.5F;
            float pitchVar = 0.08F;
            try {
                if (NapoleonicServerConfig.SPEC.isLoaded()) {
                    volume = NapoleonicServerConfig.GUNSHOT_SOUND_VOLUME.get().floatValue();
                    pitchVar = NapoleonicServerConfig.GUNSHOT_PITCH_VARIATION.get().floatValue();
                }
            } catch (Exception ignored) {
            }
            float pitch = 1.0F + (entity.getRandom().nextFloat() * 2.0F - 1.0F) * pitchVar;
            serverLevel.playSound(null, origin.x, origin.y, origin.z,
                    sound, SoundSource.PLAYERS, volume, pitch);
        }

        Vec3 muzzlePos = origin.add(smokeOffset).add(direction.normalize().scale(0.55D));
        Vec3 panPos = origin.add(smokeOffset).add(direction.normalize().scale(0.15D)).add(0.0D, 0.04D, 0.0D);
        PacketDistributor.TargetPoint point = new PacketDistributor.TargetPoint(
                muzzlePos.x, muzzlePos.y, muzzlePos.z, 128.0D, serverLevel.dimension());
        NapoleonicNetwork.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> point),
                new S2CMusketShotEffectPacket(muzzlePos, direction, panPos)
        );

        ci.cancel();
    }

    @Inject(method = "fireParticles", at = @At("HEAD"), cancellable = true, remap = false)
    private static void napoleonic$suppressDefaultFireParticles(Level level, Vec3 origin, Vec3 direction, CallbackInfo ci) {
        ci.cancel();
    }
}
