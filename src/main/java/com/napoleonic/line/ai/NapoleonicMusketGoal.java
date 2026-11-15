package com.napoleonic.line.ai;

import com.napoleonic.line.config.NapoleonicServerConfig;
import com.napoleonic.line.entity.IRecruitLineData;
import com.napoleonic.line.entity.LineInfantryMode;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Priority-0 AI Goal that governs recruits in Napoleonic Line Infantry modes:
 * - SHOULDER_ARMS («На плечо»)
 * - VOLLEY («Залп по точке»)
 * - FIRE_AT_WILL («Свободный огонь»)
 * - RELOAD («Перезарядка»)
 * On cease fire, ewe muskets yield to Recruits. Arquebuses and the other boomstick guns stay here.
 */
public class NapoleonicMusketGoal extends Goal {
    private static final UUID RELOAD_SLOWDOWN_UUID = UUID.fromString("c84162c4-7168-4f6b-9d1a-53f9b2019481");

    private final AbstractRecruitEntity recruit;
    private final IRecruitLineData lineData;
    private int aimSettleTicks = 0;

    public NapoleonicMusketGoal(AbstractRecruitEntity recruit) {
        this.recruit = recruit;
        this.lineData = (IRecruitLineData) recruit;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LineInfantryMode mode = this.lineData.napoleonic$getMode();
        if (mode == LineInfantryMode.CEASE_FIRE) {
            return LineInfantryHelper.holdsBalancedFirearm(this.recruit);
        }
        return LineInfantryHelper.hasMusketAnywhere(this.recruit);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.aimSettleTicks = 0;
        applyModeHandState();
    }

    @Override
    public void stop() {
        removeReloadSlowdown();
        if (this.lineData.napoleonic$isReloading()) {
            ItemStack stack = LineInfantryHelper.getRecruitMusketStack(this.recruit);
            if (stack != null) {
                LineInfantryHelper.setStackReloadingFlag(stack, 0, 1, false);
            }
            this.lineData.napoleonic$setReloading(false);
            this.lineData.napoleonic$setReloadProgressTicks(0);
            this.lineData.napoleonic$syncToTrackingClients();
        }
        this.recruit.setAggressive(false);
    }

    @Override
    public void tick() {
        LineInfantryMode mode = this.lineData.napoleonic$getMode();
        switch (mode) {
            case SHOULDER_ARMS -> tickShoulderArms();
            case PRESENT_ARMS -> tickPresentArms();
            case VOLLEY -> tickVolley();
            case FIRE_AT_WILL -> tickFireAtWill();
            case RELOAD -> tickReloadMode();
            case CEASE_FIRE -> {
                if (LineInfantryHelper.holdsBalancedFirearm(this.recruit)) {
                    tickFireAtWill();
                }
            }
        }
    }

    private void applyModeHandState() {
        LineInfantryMode mode = this.lineData.napoleonic$getMode();
        if (mode == LineInfantryMode.SHOULDER_ARMS) {
            LineInfantryHelper.ensureMusketInOffhand(this.recruit);
        } else if (mode == LineInfantryMode.CEASE_FIRE) {
            if (LineInfantryHelper.holdsBalancedFirearm(this.recruit)) {
                LineInfantryHelper.ensureMusketInMainHand(this.recruit);
            }
        } else {
            LineInfantryHelper.ensureMusketInMainHand(this.recruit);
        }
    }

    private void tickShoulderArms() {
        removeReloadSlowdown();
        if (this.lineData.napoleonic$isReloading()) {
            ItemStack stack = LineInfantryHelper.getRecruitMusketStack(this.recruit);
            if (stack != null) {
                LineInfantryHelper.setStackReloadingFlag(stack, 0, 1, false);
            }
            this.lineData.napoleonic$setReloading(false);
            this.lineData.napoleonic$setReloadProgressTicks(0);
            this.lineData.napoleonic$syncToTrackingClients();
        }
        LineInfantryHelper.ensureMusketInOffhand(this.recruit);
        this.recruit.setAggressive(false);

        // In Shoulder Arms, recruit does not shoot or search for targets.
        // Only if shoulderArmsMeleeDefense is true and an attacker is right next to the recruit (< 3.5 blocks) do we keep target.
        if (!NapoleonicServerConfig.SHOULDER_ARMS_MELEE_DEFENSE.get()) {
            if (this.recruit.getTarget() != null) {
                this.recruit.setTarget(null);
            }
        } else {
            LivingEntity target = this.recruit.getTarget();
            if (target != null && this.recruit.distanceToSqr(target) > 14.0D) {
                this.recruit.setTarget(null);
            }
        }

        // Maintain formation movement (Follow / HoldPos / MoveToPos goals handle navigation since we only claim Flag.LOOK)
        handleFormationMovement();
    }

    private void tickPresentArms() {
        removeReloadSlowdown();
        if (this.lineData.napoleonic$isReloading()) {
            ItemStack stack = LineInfantryHelper.getRecruitMusketStack(this.recruit);
            if (stack != null) {
                LineInfantryHelper.setStackReloadingFlag(stack, 0, 1, false);
            }
            this.lineData.napoleonic$setReloading(false);
            this.lineData.napoleonic$setReloadProgressTicks(0);
            this.lineData.napoleonic$syncToTrackingClients();
        }
        LineInfantryHelper.ensureMusketInMainHand(this.recruit);
        // Hold weapon ready in main hand without firing
        this.recruit.setAggressive(true);
        if (this.recruit.getTarget() != null) {
            this.recruit.setTarget(null);
        }
        handleFormationMovement();
    }

    private void tickVolley() {
        LineInfantryHelper.ensureMusketInMainHand(this.recruit);
        if (this.recruit.getTarget() != null) {
            this.recruit.setTarget(null);
        }

        if (this.lineData.napoleonic$isVolleyPending()) {
            removeReloadSlowdown();
            this.recruit.setAggressive(true);
            Vec3 aimPoint = this.lineData.napoleonic$getVolleyAimPoint();
            if (aimPoint == null || !LineInfantryHelper.isMusketLoaded(this.recruit)) {
                this.lineData.napoleonic$setVolleyPending(false);
                this.lineData.napoleonic$setFiredVolley(true);
                this.lineData.napoleonic$syncToTrackingClients();
                return;
            }

            // Keep the rank looking down the player's crosshair while the ripple delay runs.
            LineInfantryHelper.faceToward(this.recruit, aimPoint);
            if (!this.recruit.isPassenger()) {
                this.recruit.getNavigation().stop();
            }

            int delay = this.lineData.napoleonic$getVolleyDelayTicks();
            if (delay > 0) {
                this.lineData.napoleonic$setVolleyDelayTicks(delay - 1);
                return;
            }

            // Delay reached: check per-tick raycast budget (if exceeded, wait 1 tick so 50+ recruits spread smoothly)
            if (!LineInfantryHelper.consumeLosBudget(this.recruit.level())) {
                return;
            }

            Vec3 clearOrigin = LineInfantryHelper.findClearFiringOrigin(this.recruit, aimPoint);
            if (clearOrigin != null) {
                LineInfantryHelper.fireMusketAtPoint(this.recruit, clearOrigin, aimPoint);
            }
            // Whether fired or skipped due to friendly/wall obstruction, volley is complete for this shooter
            this.lineData.napoleonic$setVolleyPending(false);
            this.lineData.napoleonic$setFiredVolley(true);
            this.lineData.napoleonic$syncToTrackingClients();
            return;
        }

        // After firing the volley, automatically reload (18s) and then wait for the next command without shooting
        if (!LineInfantryHelper.isMusketLoaded(this.recruit)) {
            this.recruit.setAggressive(false);
            tickActiveReload();
        } else {
            removeReloadSlowdown();
            if (this.lineData.napoleonic$isReloading()) {
                ItemStack stack = LineInfantryHelper.getRecruitMusketStack(this.recruit);
                if (stack != null) {
                    LineInfantryHelper.setStackReloadingFlag(stack, 0, 1, false);
                }
                this.lineData.napoleonic$setReloading(false);
                this.lineData.napoleonic$syncToTrackingClients();
            }
            // Keep ready posture if loaded and waiting for next order
            this.recruit.setAggressive(true);
            handleFormationMovement();
        }
    }

    private void tickFireAtWill() {
        LineInfantryHelper.ensureMusketInMainHand(this.recruit);

        if (!LineInfantryHelper.isMusketLoaded(this.recruit)) {
            this.recruit.setAggressive(false);
            tickActiveReload();
            return;
        }

        removeReloadSlowdown();
        if (this.lineData.napoleonic$isReloading()) {
            ItemStack stack = LineInfantryHelper.getRecruitMusketStack(this.recruit);
            if (stack != null) {
                LineInfantryHelper.setStackReloadingFlag(stack, 0, 1, false);
            }
            this.lineData.napoleonic$setReloading(false);
            this.lineData.napoleonic$syncToTrackingClients();
        }

        int searchInterval = Math.max(10, NapoleonicServerConfig.TARGET_SEARCH_INTERVAL_TICKS.get());
        LivingEntity target = this.recruit.getTarget();
        if (target == null || !target.isAlive() || (this.recruit.tickCount + this.recruit.getId()) % searchInterval == 0) {
            target = LineInfantryHelper.findFireAtWillTarget(this.recruit);
        }

        if (target == null || !target.isAlive() || !LineInfantryHelper.isValidFireAtWillTarget(this.recruit, target)) {
            this.recruit.setAggressive(false);
            this.aimSettleTicks = 0;
            handleFormationMovement();
            return;
        }

        double maxRange = NapoleonicServerConfig.MAX_FIRING_RANGE.get();
        if (this.recruit.distanceToSqr(target) > maxRange * maxRange) {
            this.recruit.setAggressive(false);
            this.aimSettleTicks = 0;
            handleFormationMovement();
            return;
        }

        this.recruit.setAggressive(true);
        Vec3 targetChest = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.62D, target.getZ());
        // Lead moving target slightly
        Vec3 targetVel = target.getDeltaMovement();
        double dist = this.recruit.distanceTo(target);
        double flightTime = dist / 8.5D;
        Vec3 aimPoint = targetChest.add(targetVel.x * flightTime * 0.5D, 0.0D, targetVel.z * flightTime * 0.5D);

        this.recruit.getLookControl().setLookAt(aimPoint.x, aimPoint.y, aimPoint.z, 35.0F, 35.0F);
        if (!this.recruit.isPassenger()) {
            this.recruit.getNavigation().stop();
        }

        this.aimSettleTicks++;
        if (this.aimSettleTicks < 8) {
            return;
        }

        if (!LineInfantryHelper.consumeLosBudget(this.recruit.level())) {
            return;
        }

        Vec3 clearOrigin = LineInfantryHelper.findClearFiringOrigin(this.recruit, aimPoint);
        if (clearOrigin != null) {
            if (LineInfantryHelper.fireMusketAtPoint(this.recruit, clearOrigin, aimPoint)) {
                this.aimSettleTicks = 0;
            }
        }
    }

    private void tickReloadMode() {
        LineInfantryHelper.ensureMusketInMainHand(this.recruit);
        if (this.recruit.getTarget() != null) {
            this.recruit.setTarget(null);
        }

        if (!LineInfantryHelper.isMusketLoaded(this.recruit)) {
            this.recruit.setAggressive(false);
            tickActiveReload();
        } else {
            removeReloadSlowdown();
            if (this.lineData.napoleonic$isReloading()) {
                ItemStack stack = LineInfantryHelper.getRecruitMusketStack(this.recruit);
                if (stack != null) {
                    LineInfantryHelper.setStackReloadingFlag(stack, 0, 1, false);
                }
                this.lineData.napoleonic$setReloading(false);
                this.lineData.napoleonic$setReloadProgressTicks(0);
                this.lineData.napoleonic$syncToTrackingClients();
            }
            this.recruit.setAggressive(true);
            handleFormationMovement();
        }
    }

    private void tickActiveReload() {
        ItemStack stack = LineInfantryHelper.getRecruitMusketStack(this.recruit);
        if (stack == null || !LineInfantryHelper.isMusketWeapon(stack)) {
            removeReloadSlowdown();
            return;
        }

        if (!this.lineData.napoleonic$isReloading()) {
            if (!LineInfantryHelper.hasOrConsumeCartridge(this.recruit, true)) {
                removeReloadSlowdown();
                return;
            }
            int totalTicks = LineInfantryHelper.rollRecruitReloadTicks(this.recruit.getRandom());
            this.lineData.napoleonic$setReloadTotalTicks(totalTicks);
            this.lineData.napoleonic$setReloadProgressTicks(0);
            this.lineData.napoleonic$setReloading(true);
            LineInfantryHelper.setStackReloadingFlag(stack, 0, totalTicks, true);
            this.lineData.napoleonic$syncToTrackingClients();
        }

        // Handle movement during reload according to server config
        if (!NapoleonicServerConfig.MOVE_DURING_RELOAD.get()) {
            if (!this.recruit.isPassenger()) {
                this.recruit.getNavigation().stop();
            }
            applyReloadSlowdown(-1.0D);
        } else {
            double mult = NapoleonicServerConfig.RELOAD_MOVE_SPEED_MULTIPLIER.get();
            applyReloadSlowdown(mult - 1.0D);
        }

        int totalTicks = Math.max(20, this.lineData.napoleonic$getReloadTotalTicks());
        int progress = this.lineData.napoleonic$getReloadProgressTicks() + 1;
        this.lineData.napoleonic$setReloadProgressTicks(progress);

        // Update firearm loading stage / recharge NBT so item properties match
        LineInfantryHelper.setStackReloadingFlag(stack, progress, totalTicks, true);

        LineInfantryHelper.playReloadStageSound(this.recruit, progress, totalTicks);

        if (progress >= totalTicks) {
            LineInfantryHelper.setStackLoaded(stack, true);
            LineInfantryHelper.setStackReloadingFlag(stack, 0, totalTicks, false);
            this.lineData.napoleonic$setReloading(false);
            this.lineData.napoleonic$setReloadProgressTicks(0);
            removeReloadSlowdown();
            this.lineData.napoleonic$syncToTrackingClients();
        } else if (progress % 10 == 0) {
            this.lineData.napoleonic$syncToTrackingClients();
        }
    }

    private void handleFormationMovement() {
        // Note: RecruitFollowOwnerGoal, RecruitHoldPosGoal, and RecruitMoveToPosGoal remain active
        // because NapoleonicMusketGoal only claims Goal.Flag.LOOK!
        // When the recruit is at its HoldPos and has ownerRot, keep facing formation direction.
        Vec3 glance = LineInfantryHelper.activeGlance(this.recruit);
        if (glance != null) {
            LineInfantryHelper.faceToward(this.recruit, glance);
            return;
        }
        if (this.recruit.getShouldHoldPos() && this.recruit.getHoldPos() != null) {
            if (this.recruit.distanceToSqr(this.recruit.getHoldPos()) <= 2.25D && this.recruit.getNavigation().isDone()) {
                this.recruit.setYRot(this.recruit.ownerRot);
                this.recruit.setYHeadRot(this.recruit.ownerRot);
                this.recruit.yBodyRot = this.recruit.ownerRot;
            }
        }
    }

    private void applyReloadSlowdown(double amount) {
        AttributeInstance attr = this.recruit.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        AttributeModifier existing = attr.getModifier(RELOAD_SLOWDOWN_UUID);
        if (existing != null && Math.abs(existing.getAmount() - amount) < 1.0E-4D) {
            return;
        }
        if (existing != null) {
            attr.removeModifier(RELOAD_SLOWDOWN_UUID);
        }
        attr.addTransientModifier(new AttributeModifier(
                RELOAD_SLOWDOWN_UUID,
                "Napoleonic reload movement penalty",
                amount,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        ));
    }

    private void removeReloadSlowdown() {
        AttributeInstance attr = this.recruit.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null && attr.getModifier(RELOAD_SLOWDOWN_UUID) != null) {
            attr.removeModifier(RELOAD_SLOWDOWN_UUID);
        }
    }
}
