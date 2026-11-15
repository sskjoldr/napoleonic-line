package com.napoleonic.line.mixin.client;

import com.napoleonic.line.ai.LineInfantryHelper;
import com.napoleonic.line.client.ClientLineStateCache;
import com.napoleonic.line.entity.LineInfantryMode;
import com.talhanation.recruits.client.render.RecruitVillagerRenderer;
import com.talhanation.recruits.entities.AbstractInventoryEntity;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RecruitVillagerRenderer.class, priority = 1100)
public abstract class ClientRecruitVillagerArmPoseMixin {
    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true, remap = false)
    private static void napoleonic$getVillagerArmPose(AbstractInventoryEntity entity, InteractionHand hand,
                                                      CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        if (!(entity instanceof AbstractRecruitEntity recruit)) {
            return;
        }
        LineInfantryMode mode = ClientLineStateCache.getMode(recruit);
        ItemStack stack = recruit.getItemInHand(hand);

        // 1. «На плечо» (Shoulder Arms): firearm in offhand -> ITEM pose on offhand, normal/empty on main hand
        if (mode == LineInfantryMode.SHOULDER_ARMS || (hand == InteractionHand.OFF_HAND && LineInfantryHelper.isMusketWeapon(stack))) {
            if (hand == InteractionHand.OFF_HAND && LineInfantryHelper.isMusketWeapon(stack)) {
                cir.setReturnValue(HumanoidModel.ArmPose.ITEM);
                return;
            }
            if (hand == InteractionHand.MAIN_HAND && mode == LineInfantryMode.SHOULDER_ARMS && stack.isEmpty()) {
                cir.setReturnValue(HumanoidModel.ArmPose.EMPTY);
                return;
            }
        }

        // 2. Firearm in main hand during Napoleonic modes
        if (hand == InteractionHand.MAIN_HAND && LineInfantryHelper.isMusketWeapon(stack)) {
            if (ClientLineStateCache.isReloading(recruit)) {
                cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_CHARGE);
                return;
            }
            if (mode == LineInfantryMode.PRESENT_ARMS
                    || mode == LineInfantryMode.VOLLEY
                    || mode == LineInfantryMode.FIRE_AT_WILL
                    || (mode == LineInfantryMode.RELOAD && LineInfantryHelper.isStackLoaded(stack))
                    || recruit.isAggressive()) {
                cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
            }
        }
    }
}
