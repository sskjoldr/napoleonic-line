package com.napoleonic.line.client;

import com.napoleonic.line.entity.IRecruitLineData;
import com.napoleonic.line.entity.LineInfantryMode;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class ClientLineStateCache {
    public record ClientRecruitState(
            LineInfantryMode mode,
            boolean reloading,
            int reloadProgressTicks,
            int reloadTotalTicks
    ) {
    }

    private static final Int2ObjectMap<ClientRecruitState> CACHE = new Int2ObjectOpenHashMap<>();

    public static void updateState(int entityId, LineInfantryMode mode, boolean reloading,
                                   int reloadProgressTicks, int reloadTotalTicks) {
        CACHE.put(entityId, new ClientRecruitState(mode, reloading, reloadProgressTicks, reloadTotalTicks));
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Entity entity = mc.level.getEntity(entityId);
            if (entity instanceof IRecruitLineData lineData) {
                lineData.napoleonic$setMode(mode);
                lineData.napoleonic$setReloading(reloading);
                lineData.napoleonic$setReloadProgressTicks(reloadProgressTicks);
                lineData.napoleonic$setReloadTotalTicks(reloadTotalTicks);
            }
        }
    }

    public static LineInfantryMode getMode(Entity entity) {
        if (entity instanceof IRecruitLineData lineData) {
            LineInfantryMode m = lineData.napoleonic$getMode();
            if (m != LineInfantryMode.CEASE_FIRE) {
                return m;
            }
        }
        ClientRecruitState cached = CACHE.get(entity.getId());
        return cached != null ? cached.mode() : LineInfantryMode.CEASE_FIRE;
    }

    public static boolean isReloading(Entity entity) {
        if (entity instanceof IRecruitLineData lineData && lineData.napoleonic$isReloading()) {
            return true;
        }
        ClientRecruitState cached = CACHE.get(entity.getId());
        return cached != null && cached.reloading();
    }

    public static float getReloadProgressFraction(Entity entity) {
        if (entity instanceof IRecruitLineData lineData && lineData.napoleonic$isReloading()) {
            int total = Math.max(1, lineData.napoleonic$getReloadTotalTicks());
            return (float) lineData.napoleonic$getReloadProgressTicks() / (float) total;
        }
        ClientRecruitState cached = CACHE.get(entity.getId());
        if (cached != null && cached.reloading()) {
            return (float) cached.reloadProgressTicks() / (float) Math.max(1, cached.reloadTotalTicks());
        }
        return 0.0F;
    }

    public static void clear() {
        CACHE.clear();
    }

    private ClientLineStateCache() {
    }
}
