package com.napoleonic.line.mixin.client;

import com.napoleonic.line.ai.LineInfantryHelper;
import com.napoleonic.line.client.ClientLineStateCache;
import com.napoleonic.line.compat.MusketModCompat;
import com.napoleonic.line.entity.LineInfantryMode;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HumanoidModel.class, priority = 1100)
public abstract class ClientHumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void napoleonic$applyLineInfantryArmPoses(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                      float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof AbstractRecruitEntity recruit)) {
            return;
        }
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        LineInfantryMode mode = ClientLineStateCache.getMode(recruit);

        // 1. «На плечо» (Shoulder Arms): firearm in offhand (left hand), held vertically at left shoulder
        if (LineInfantryHelper.isMusketWeapon(recruit.getOffhandItem())
                && (mode == LineInfantryMode.SHOULDER_ARMS || !LineInfantryHelper.isMusketWeapon(recruit.getMainHandItem()))) {
            boolean eweGun = MusketModCompat.isLoaded() && MusketModCompat.isGun(recruit.getOffhandItem());
            if (eweGun || LineInfantryHelper.isThirtyYearsWarFirearm(recruit.getOffhandItem())) {
                // Same shoulder pose as the first Napoleonic Line build: hand by the shoulder, muzzle up.
                // The item itself is not rotated here; the third-person musket transform stays vanilla.
                float deg = (float) Math.PI / 180.0F;
                model.leftArm.xRot = -165.0F * deg;
                model.leftArm.yRot = 60.0F * deg;
                model.leftArm.zRot = -140.0F * deg;
                return;
            }
            float walkSwing = Mth.cos(limbSwing * 0.6662F) * 0.06F * limbSwingAmount;
            model.leftArm.xRot = -0.38F + walkSwing;
            model.leftArm.yRot = 0.0F;
            model.leftArm.zRot = 0.0F;
            return;
        }

        // 2. Reloading animation when reloading a firearm in main hand:
        // Keep the holding arm (rightArm) completely steady in CROSSBOW_CHARGE posture so the gun sits firmly in the hand,
        // and smoothly animate only the loading arm (leftArm).
        if (ClientLineStateCache.isReloading(recruit) && LineInfantryHelper.isMusketWeapon(recruit.getMainHandItem())) {
            boolean rightHanded = recruit.getMainArm() == HumanoidArm.RIGHT;
            var holdingArm = rightHanded ? model.rightArm : model.leftArm;
            var loadingArm = rightHanded ? model.leftArm : model.rightArm;
            float sign = rightHanded ? 1.0F : -1.0F;

            holdingArm.yRot = -0.8F * sign;
            holdingArm.xRot = -0.97079635F;
            holdingArm.zRot = 0.0F;

            // Smooth 2-second cycle per loading step (ramming / priming) on the free hand only
            float progress = (ageInTicks % 40.0F) / 40.0F;
            float tri = progress < 0.5F ? (progress * 2.0F) : (2.0F - progress * 2.0F);
            loadingArm.yRot = Mth.lerp(tri, 0.4F, 0.85F) * sign;
            loadingArm.xRot = Mth.lerp(tri, -0.97079635F, -(float) (Math.PI / 2.0D));
            loadingArm.zRot = 0.0F;
            return;
        }

        // 3. Ready / Aiming posture (Present Arms, Volley, Fire at Will) with firearm in main hand
        if (!ClientLineStateCache.isReloading(recruit)
                && LineInfantryHelper.isMusketWeapon(recruit.getMainHandItem())
                && (mode == LineInfantryMode.PRESENT_ARMS
                    || mode == LineInfantryMode.VOLLEY
                    || mode == LineInfantryMode.FIRE_AT_WILL
                    || (mode == LineInfantryMode.RELOAD && LineInfantryHelper.isMusketLoaded(recruit)))) {
            AnimationUtils.animateCrossbowHold(model.rightArm, model.leftArm, model.head, recruit.getMainArm() == HumanoidArm.RIGHT);
        }
    }
}

