package com.napoleonic.line.entity;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public interface IRecruitLineData {
    LineInfantryMode napoleonic$getMode();

    void napoleonic$setMode(LineInfantryMode mode);

    int napoleonic$getReloadProgressTicks();

    void napoleonic$setReloadProgressTicks(int ticks);

    int napoleonic$getReloadTotalTicks();

    void napoleonic$setReloadTotalTicks(int ticks);

    boolean napoleonic$isReloading();

    void napoleonic$setReloading(boolean reloading);

    boolean napoleonic$isVolleyPending();

    void napoleonic$setVolleyPending(boolean pending);

    @Nullable
    Vec3 napoleonic$getVolleyAimPoint();

    void napoleonic$setVolleyAimPoint(@Nullable Vec3 point);

    int napoleonic$getVolleyDelayTicks();

    void napoleonic$setVolleyDelayTicks(int delayTicks);

    boolean napoleonic$hasFiredVolley();

    void napoleonic$setFiredVolley(boolean fired);

    void napoleonic$syncToTrackingClients();
}
