package com.napoleonic.line.mixin;

import com.napoleonic.line.network.NapoleonicNetwork;
import com.napoleonic.line.network.S2CMusketShotEffectPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Visual-only hook for Epic Knights: Thirty Years War. The gun class is absent unless that mod is installed,
 * so this mixin is applied only by {@link NapoleonicMixinPlugin}. It does not play a sound: the gun already does.
 */
@Pseudo
@Mixin(targets = "com.magistuarmory.thirtyyearswar.item.GunItem", remap = false)
public abstract class ThirtyYearsWarGunItemMixin {
    @Inject(method = "performShooting", at = @At("HEAD"), remap = false)
    private void napoleonic$shotEffects(ItemStack stack, Level level, LivingEntity shooter, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel) || shooter == null) {
            return;
        }
        Vec3 dir = shooter.getLookAngle();
        if (dir.lengthSqr() < 1.0E-4D) {
            dir = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            dir = dir.normalize();
        }
        Vec3 eye = shooter.getEyePosition();
        Vec3 muzzle = eye.add(dir.scale(0.9D));
        Vec3 pan = eye.add(dir.scale(0.25D)).add(0.0D, 0.04D, 0.0D);
        PacketDistributor.TargetPoint point = new PacketDistributor.TargetPoint(
                muzzle.x, muzzle.y, muzzle.z, 128.0D, serverLevel.dimension());
        NapoleonicNetwork.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> point),
                new S2CMusketShotEffectPacket(muzzle, dir, pan)
        );
    }
}
