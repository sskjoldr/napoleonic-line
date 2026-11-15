package com.napoleonic.line.mixin;

import com.napoleonic.line.ai.LineInfantryHelper;
import com.napoleonic.line.entity.IRecruitLineData;
import com.napoleonic.line.entity.LineInfantryMode;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import com.talhanation.recruits.entities.ai.compat.RecruitRangedMusketAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecruitRangedMusketAttackGoal.class)
public abstract class RecruitRangedMusketAttackGoalMixin {
    @Shadow(remap = false)
    @Final
    private CrossBowmanEntity crossBowman;

    @Shadow(remap = false)
    private int weaponLoadTime;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void napoleonic$gateCanUse(CallbackInfoReturnable<Boolean> cir) {
        if (this.crossBowman instanceof IRecruitLineData lineData) {
            if (LineInfantryHelper.holdsBalancedFirearm(this.crossBowman)
                    || lineData.napoleonic$getMode() != LineInfantryMode.CEASE_FIRE) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
    private void napoleonic$gateCanContinueToUse(CallbackInfoReturnable<Boolean> cir) {
        if (this.crossBowman instanceof IRecruitLineData lineData) {
            if (LineInfantryHelper.holdsBalancedFirearm(this.crossBowman)
                    || lineData.napoleonic$getMode() != LineInfantryMode.CEASE_FIRE) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "start", at = @At("TAIL"))
    private void napoleonic$onStart(CallbackInfo ci) {
        this.weaponLoadTime = LineInfantryHelper.getRecruitReloadTotalTicks();
    }

    /**
     * When the recruit is in a Napoleonic mode, cancel Recruits' built-in goal immediately.
     * When in CEASE_FIRE, enforce the 18-second Napoleonic reload time and stage sounds.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void napoleonic$onTick(CallbackInfo ci) {
        if (this.crossBowman == null) return;
        IRecruitLineData lineData = (IRecruitLineData) this.crossBowman;
        if (LineInfantryHelper.holdsBalancedFirearm(this.crossBowman)
                || lineData.napoleonic$getMode() != LineInfantryMode.CEASE_FIRE) {
            ci.cancel();
            return;
        }
        int totalTicks = LineInfantryHelper.getRecruitReloadTotalTicks();
        this.weaponLoadTime = totalTicks;
        int usingTicks = this.crossBowman.getTicksUsingItem();
        if (usingTicks > 0) {
            LineInfantryHelper.playReloadStageSound(this.crossBowman, usingTicks, totalTicks);
        }
    }
}
