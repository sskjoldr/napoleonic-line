package com.napoleonic.line.mixin;

import com.napoleonic.line.ai.LineInfantryHelper;
import com.napoleonic.line.entity.IRecruitLineData;
import com.napoleonic.line.entity.LineInfantryMode;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.iwoss.recruits_use_boomsticks.ai.RecruitBoomstickAttackGoal", remap = false)
public abstract class RecruitBoomstickAttackGoalMixin {
    @Shadow(remap = false)
    @Final
    private CrossBowmanEntity crossBowman;

    @Inject(method = {"m_8036_", "canUse"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void napoleonic$gateBoomstickCanUse(CallbackInfoReturnable<Boolean> cir) {
        if (this.crossBowman instanceof IRecruitLineData lineData) {
            if (napoleonic$blockVanillaShot(lineData)) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = {"m_8045_", "canContinueToUse"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void napoleonic$gateBoomstickCanContinue(CallbackInfoReturnable<Boolean> cir) {
        if (this.crossBowman instanceof IRecruitLineData lineData) {
            if (napoleonic$blockVanillaShot(lineData)) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = {"m_8037_", "tick"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void napoleonic$gateBoomstickTick(CallbackInfo ci) {
        if (this.crossBowman instanceof IRecruitLineData lineData) {
            if (napoleonic$blockVanillaShot(lineData)) {
                ci.cancel();
            }
        }
    }

    private boolean napoleonic$blockVanillaShot(IRecruitLineData lineData) {
        if (LineInfantryHelper.holdsBalancedFirearm(this.crossBowman)) {
            return true;
        }
        return lineData.napoleonic$getMode() != LineInfantryMode.CEASE_FIRE
                && LineInfantryHelper.hasMusketAnywhere(this.crossBowman);
    }
}
