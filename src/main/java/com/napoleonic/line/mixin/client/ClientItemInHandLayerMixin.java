package com.napoleonic.line.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.napoleonic.line.ai.LineInfantryHelper;
import com.napoleonic.line.compat.MusketModCompat;
import com.napoleonic.line.client.ClientLineStateCache;
import com.napoleonic.line.entity.LineInfantryMode;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemInHandLayer.class, priority = 1100)
public abstract class ClientItemInHandLayerMixin {
    @Inject(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
            )
    )
    private void napoleonic$transformMusketInHand(LivingEntity livingEntity, ItemStack itemStack,
                                                  ItemDisplayContext displayContext, HumanoidArm arm,
                                                  PoseStack poseStack, MultiBufferSource buffer,
                                                  int packedLight, CallbackInfo ci) {
        if (!(livingEntity instanceof AbstractRecruitEntity recruit)) {
            return;
        }
        if (!LineInfantryHelper.isMusketWeapon(itemStack)) {
            return;
        }

        LineInfantryMode mode = ClientLineStateCache.getMode(recruit);

        // «На плечо» (Shoulder Arms): rotate firearm in left hand vertically with barrel/bayonet pointing straight up at left shoulder,
        // and grip resting directly inside the left fist.
        // Note: During RELOAD / PRESENT_ARMS / VOLLEY / FIRE_AT_WILL in MAIN_HAND, no extra offset is applied here
        // so the firearm uses its native thirdperson_righthand / musket_loading / arquebus_recharge grip!
        if (arm == HumanoidArm.LEFT && (mode == LineInfantryMode.SHOULDER_ARMS || !LineInfantryHelper.isMusketWeapon(recruit.getMainHandItem()))) {
            if (LineInfantryHelper.isMedievalBoomstick(itemStack)) {
                poseStack.translate(0.0F, 0.08F, 0.05F);
                if (CrossbowItem.isCharged(itemStack) && !itemStack.getOrCreateTag().getBoolean("recharge")) {
                    // Counteract arquebus_aim.json [12.57, -14.43, -0.54] so barrel points straight up at left shoulder
                    poseStack.mulPose(Axis.XP.rotationDegrees(145.0F));
                    poseStack.mulPose(Axis.YP.rotationDegrees(-14.43F));
                } else {
                    // Counteract arquebus.json [67.75, 0, 0] so barrel points straight up at left shoulder
                    poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
                }
            } else if ((MusketModCompat.isLoaded() && MusketModCompat.isGun(itemStack))
                    || LineInfantryHelper.isThirtyYearsWarFirearm(itemStack)) {
                // Ewe muskets and Thirty Years War guns take their shoulder angle from the arm pose.
            } else {
                poseStack.translate(0.0F, -0.05F, 0.30F);
                poseStack.mulPose(Axis.XP.rotationDegrees(62.0F));
            }
        }
    }
}

