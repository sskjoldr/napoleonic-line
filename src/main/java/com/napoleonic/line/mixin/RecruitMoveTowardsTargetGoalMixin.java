package com.napoleonic.line.mixin;

import com.napoleonic.line.ai.LineInfantryHelper;
import com.napoleonic.line.config.NapoleonicServerConfig;
import com.napoleonic.line.entity.IRecruitLineData;
import com.napoleonic.line.entity.LineInfantryMode;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.entities.ai.RecruitMoveTowardsTargetGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecruitMoveTowardsTargetGoal.class)
public abstract class RecruitMoveTowardsTargetGoalMixin {
    @Shadow(remap = false)
    @Final
    private AbstractRecruitEntity recruit;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void napoleonic$gateMoveTowardsTarget(CallbackInfoReturnable<Boolean> cir) {
        if (this.recruit instanceof IRecruitLineData lineData) {
            LineInfantryMode mode = lineData.napoleonic$getMode();
            if (mode == LineInfantryMode.CEASE_FIRE) {
                if (LineInfantryHelper.holdsBalancedFirearm(this.recruit)) {
                    cir.setReturnValue(false);
                }
                return;
            }
            if (!LineInfantryHelper.hasMusketAnywhere(this.recruit)) {
                return;
            }
            if (mode == LineInfantryMode.SHOULDER_ARMS && !NapoleonicServerConfig.SHOULDER_ARMS_MELEE_DEFENSE.get()) {
                cir.setReturnValue(false);
                return;
            }
            if (mode == LineInfantryMode.VOLLEY || mode == LineInfantryMode.RELOAD
                    || mode == LineInfantryMode.FIRE_AT_WILL || mode == LineInfantryMode.PRESENT_ARMS) {
                cir.setReturnValue(false);
            }
        }
    }
}

