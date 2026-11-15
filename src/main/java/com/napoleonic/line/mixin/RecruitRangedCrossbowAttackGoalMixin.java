package com.napoleonic.line.mixin;

import com.napoleonic.line.ai.LineInfantryHelper;
import com.napoleonic.line.entity.IRecruitLineData;
import com.napoleonic.line.entity.LineInfantryMode;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import com.talhanation.recruits.entities.ai.RecruitRangedCrossbowAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecruitRangedCrossbowAttackGoal.class)
public abstract class RecruitRangedCrossbowAttackGoalMixin {
    @Shadow(remap = false)
    @Final
    private CrossBowmanEntity crossBowman;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void napoleonic$gateCanUse(CallbackInfoReturnable<Boolean> cir) {
        if (this.crossBowman instanceof IRecruitLineData lineData) {
            if (napoleonic$blockVanillaShot(lineData)) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
    private void napoleonic$gateCanContinueToUse(CallbackInfoReturnable<Boolean> cir) {
        if (this.crossBowman instanceof IRecruitLineData lineData) {
            if (napoleonic$blockVanillaShot(lineData)) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void napoleonic$onTick(CallbackInfo ci) {
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
