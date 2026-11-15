package com.napoleonic.line.mixin;

import com.napoleonic.line.ai.LineInfantryHelper;
import com.napoleonic.line.config.NapoleonicServerConfig;
import com.napoleonic.line.entity.NapoleonicDamageTypes;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import ewewukek.musketmod.BulletEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BulletEntity.class)
public abstract class BulletEntityMixin extends AbstractHurtingProjectile {
    @Shadow(remap = false)
    public short tickCounter;

    @Shadow(remap = false)
    public float damage;

    @Shadow(remap = false)
    public float distanceTravelled;

    @Shadow(remap = false)
    public abstract int pelletCount();

    protected BulletEntityMixin(EntityType<? extends AbstractHurtingProjectile> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void napoleonic$canHitEntity(Entity target, CallbackInfoReturnable<Boolean> cir) {
        Entity owner = this.getOwner();
        if (owner instanceof AbstractRecruitEntity recruit) {
            if (LineInfantryHelper.isFriendlyOrProtected(recruit, target)) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void napoleonic$checkMaxLifetime(CallbackInfo ci) {
        int maxTicks = 60;
        try {
            if (NapoleonicServerConfig.SPEC.isLoaded()) {
                maxTicks = NapoleonicServerConfig.BULLET_MAX_LIFETIME_TICKS.get();
            }
        } catch (Exception ignored) {
        }
        if (this.tickCounter >= maxTicks) {
            this.discard();
            ci.cancel();
        }
    }

    /**
     * Prevent musket balls from breaking or dropping blocks unless enabled in server config.
     */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"
            )
    )
    private boolean napoleonic$preventRemoveBlock(Level level, BlockPos pos, boolean isMoving) {
        if (NapoleonicServerConfig.SPEC.isLoaded() && NapoleonicServerConfig.BULLETS_BREAK_BLOCKS.get()) {
            return level.removeBlock(pos, isMoving);
        }
        return false;
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/TntBlock;explode(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"
            )
    )
    private void napoleonic$preventTntExplode(Level level, BlockPos pos) {
        if (NapoleonicServerConfig.SPEC.isLoaded() && NapoleonicServerConfig.BULLETS_BREAK_BLOCKS.get()) {
            net.minecraft.world.level.block.TntBlock.explode(level, pos);
        }
    }

    @Inject(method = "getDamageSource()Lnet/minecraft/world/damagesource/DamageSource;", at = @At("HEAD"), cancellable = true, remap = false)
    private void napoleonic$customDamageSource(CallbackInfoReturnable<DamageSource> cir) {
        cir.setReturnValue(NapoleonicDamageTypes.musketBall(this.level(), this, this.getOwner()));
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
    private void napoleonic$onHitEntity(EntityHitResult hitResult, CallbackInfo ci) {
        if (!this.level().isClientSide()) {
            Entity target = hitResult.getEntity();
            Entity owner = this.getOwner();
            if (owner instanceof AbstractRecruitEntity recruit && LineInfantryHelper.isFriendlyOrProtected(recruit, target)) {
                ci.cancel();
                return;
            }
            if (owner != null && owner.getTeam() != null && owner.getTeam().isAlliedTo(target.getTeam()) && !owner.getTeam().isAllowFriendlyFire()) {
                ci.cancel();
                return;
            }
            if (owner instanceof Player shooterPlayer && target instanceof Player targetPlayer && !shooterPlayer.canHarmPlayer(targetPlayer)) {
                ci.cancel();
                return;
            }

            DamageSource source = NapoleonicDamageTypes.musketBall(this.level(), this, owner);
            float baseDamage = this.damage > 0.0F
                    ? this.damage
                    : (NapoleonicServerConfig.SPEC.isLoaded() ? NapoleonicServerConfig.MUSKET_DAMAGE.get().floatValue() : 35.0F);

            int pellets = Math.max(1, this.pelletCount());
            if (pellets > 1) {
                baseDamage /= pellets;
            }

            float falloffStart = NapoleonicServerConfig.SPEC.isLoaded()
                    ? NapoleonicServerConfig.DAMAGE_FALLOFF_START_DISTANCE.get().floatValue()
                    : 60.0F;
            float minMult = NapoleonicServerConfig.SPEC.isLoaded()
                    ? NapoleonicServerConfig.DAMAGE_MIN_MULTIPLIER.get().floatValue()
                    : 0.35F;

            // Total distance includes distance travelled in previous ticks + distance to hit point in current tick
            float totalDistance = this.distanceTravelled + (float) this.position().distanceTo(hitResult.getLocation());
            float mult = 1.0F;
            if (totalDistance > falloffStart) {
                float excess = totalDistance - falloffStart;
                mult = Mth.clamp(1.0F - (excess / 100.0F), minMult, 1.0F);
            }

            target.invulnerableTime = 0;
            target.hurt(source, baseDamage * mult);
            if (this.isOnFire() && target.getType() != EntityType.ENDERMAN) {
                target.setSecondsOnFire(5);
            }
        }
        ci.cancel();
    }
}
