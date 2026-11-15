package com.napoleonic.line.mixin;

import com.napoleonic.line.ai.LineInfantryHelper;
import com.napoleonic.line.ai.NapoleonicMusketGoal;
import com.napoleonic.line.entity.IRecruitLineData;
import com.napoleonic.line.entity.LineInfantryMode;
import com.napoleonic.line.network.NapoleonicNetwork;
import com.napoleonic.line.network.S2CRecruitLineStatePacket;
import com.talhanation.recruits.entities.AbstractInventoryEntity;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractRecruitEntity.class)
public abstract class AbstractRecruitEntityMixin extends AbstractInventoryEntity implements IRecruitLineData {
    @Unique
    private static final String NBT_KEY = "NapoleonicLineData";

    @Unique
    private LineInfantryMode napoleonic$mode = LineInfantryMode.CEASE_FIRE;
    @Unique
    private boolean napoleonic$reloading = false;
    @Unique
    private int napoleonic$reloadProgressTicks = 0;
    @Unique
    private int napoleonic$reloadTotalTicks = 360;
    @Unique
    private boolean napoleonic$volleyPending = false;
    @Unique
    private boolean napoleonic$firedVolley = false;
    @Unique
    private int napoleonic$volleyDelayTicks = 0;
    @Unique
    @Nullable
    private Vec3 napoleonic$volleyAimPoint = null;

    protected AbstractRecruitEntityMixin(EntityType<? extends AbstractInventoryEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void napoleonic$registerMusketGoal(CallbackInfo ci) {
        AbstractRecruitEntity self = (AbstractRecruitEntity) (Object) this;
        this.goalSelector.addGoal(0, new NapoleonicMusketGoal(self));
    }

    @Inject(method = "searchForTargets", at = @At("HEAD"), cancellable = true, remap = false)
    private void napoleonic$onSearchForTargets(CallbackInfo ci) {
        AbstractRecruitEntity self = (AbstractRecruitEntity) (Object) this;
        boolean arquebusKeepsModAi = LineInfantryHelper.holdsBalancedFirearm(self);
        if (this.napoleonic$mode != LineInfantryMode.CEASE_FIRE || arquebusKeepsModAi) {
            // Line orders use our own target finder. Arquebuses keep that finder even on
            // ordinary Recruits commands, so they never fall back to the crossbow attack.
            ci.cancel();
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void napoleonic$saveLineData(CompoundTag nbt, CallbackInfo ci) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Mode", this.napoleonic$mode.getId());
        tag.putBoolean("Reloading", this.napoleonic$reloading);
        tag.putInt("ReloadProgress", this.napoleonic$reloadProgressTicks);
        tag.putInt("ReloadTotal", this.napoleonic$reloadTotalTicks);
        tag.putBoolean("VolleyPending", this.napoleonic$volleyPending);
        tag.putBoolean("FiredVolley", this.napoleonic$firedVolley);
        tag.putInt("VolleyDelay", this.napoleonic$volleyDelayTicks);
        if (this.napoleonic$volleyAimPoint != null) {
            tag.putDouble("VolleyAimX", this.napoleonic$volleyAimPoint.x);
            tag.putDouble("VolleyAimY", this.napoleonic$volleyAimPoint.y);
            tag.putDouble("VolleyAimZ", this.napoleonic$volleyAimPoint.z);
        }
        nbt.put(NBT_KEY, tag);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void napoleonic$readLineData(CompoundTag nbt, CallbackInfo ci) {
        if (nbt.contains(NBT_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag tag = nbt.getCompound(NBT_KEY);
            this.napoleonic$mode = LineInfantryMode.byId(tag.getInt("Mode"));
            this.napoleonic$reloading = tag.getBoolean("Reloading");
            this.napoleonic$reloadProgressTicks = tag.getInt("ReloadProgress");
            this.napoleonic$reloadTotalTicks = Math.max(20, tag.getInt("ReloadTotal"));
            this.napoleonic$volleyPending = tag.getBoolean("VolleyPending");
            this.napoleonic$firedVolley = tag.getBoolean("FiredVolley");
            this.napoleonic$volleyDelayTicks = tag.getInt("VolleyDelay");
            if (tag.contains("VolleyAimX")) {
                this.napoleonic$volleyAimPoint = new Vec3(
                        tag.getDouble("VolleyAimX"),
                        tag.getDouble("VolleyAimY"),
                        tag.getDouble("VolleyAimZ")
                );
            } else {
                this.napoleonic$volleyAimPoint = null;
            }
        }
    }

    @Override
    public LineInfantryMode napoleonic$getMode() {
        return this.napoleonic$mode;
    }

    @Override
    public void napoleonic$setMode(LineInfantryMode mode) {
        this.napoleonic$mode = mode != null ? mode : LineInfantryMode.CEASE_FIRE;
    }

    @Override
    public int napoleonic$getReloadProgressTicks() {
        return this.napoleonic$reloadProgressTicks;
    }

    @Override
    public void napoleonic$setReloadProgressTicks(int ticks) {
        this.napoleonic$reloadProgressTicks = Math.max(0, ticks);
    }

    @Override
    public int napoleonic$getReloadTotalTicks() {
        return this.napoleonic$reloadTotalTicks;
    }

    @Override
    public void napoleonic$setReloadTotalTicks(int ticks) {
        this.napoleonic$reloadTotalTicks = Math.max(20, ticks);
    }

    @Override
    public boolean napoleonic$isReloading() {
        return this.napoleonic$reloading;
    }

    @Override
    public void napoleonic$setReloading(boolean reloading) {
        this.napoleonic$reloading = reloading;
    }

    @Override
    public boolean napoleonic$isVolleyPending() {
        return this.napoleonic$volleyPending;
    }

    @Override
    public void napoleonic$setVolleyPending(boolean pending) {
        this.napoleonic$volleyPending = pending;
    }

    @Override
    @Nullable
    public Vec3 napoleonic$getVolleyAimPoint() {
        return this.napoleonic$volleyAimPoint;
    }

    @Override
    public void napoleonic$setVolleyAimPoint(@Nullable Vec3 point) {
        this.napoleonic$volleyAimPoint = point;
    }

    @Override
    public int napoleonic$getVolleyDelayTicks() {
        return this.napoleonic$volleyDelayTicks;
    }

    @Override
    public void napoleonic$setVolleyDelayTicks(int delayTicks) {
        this.napoleonic$volleyDelayTicks = Math.max(0, delayTicks);
    }

    @Override
    public boolean napoleonic$hasFiredVolley() {
        return this.napoleonic$firedVolley;
    }

    @Override
    public void napoleonic$setFiredVolley(boolean fired) {
        this.napoleonic$firedVolley = fired;
    }

    @Override
    public void napoleonic$syncToTrackingClients() {
        if (!this.level().isClientSide() && !this.level().players().isEmpty()) {
            NapoleonicNetwork.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> this),
                    new S2CRecruitLineStatePacket(
                            this.getId(),
                            this.napoleonic$mode,
                            this.napoleonic$reloading,
                            this.napoleonic$reloadProgressTicks,
                            this.napoleonic$reloadTotalTicks
                    )
            );
        }
    }
}
