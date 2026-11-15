package com.napoleonic.line.network;

import com.napoleonic.line.ai.LineInfantryHelper;
import com.napoleonic.line.entity.IRecruitLineData;
import com.napoleonic.line.entity.LineInfantryMode;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class C2SLineCommandPacket {
    private final LineInfantryMode mode;
    private final List<UUID> groupUUIDs;
    private final String groupNamesSummary;

    public C2SLineCommandPacket(LineInfantryMode mode, List<UUID> groupUUIDs, String groupNamesSummary) {
        this.mode = mode;
        this.groupUUIDs = groupUUIDs;
        this.groupNamesSummary = groupNamesSummary != null ? groupNamesSummary : "";
    }

    public static void encode(C2SLineCommandPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.mode.getId());
        int count = Math.min(32, msg.groupUUIDs.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeUUID(msg.groupUUIDs.get(i));
        }
        buf.writeUtf(msg.groupNamesSummary, 128);
    }

    public static C2SLineCommandPacket decode(FriendlyByteBuf buf) {
        LineInfantryMode mode = LineInfantryMode.byId(buf.readVarInt());
        int count = Math.min(32, Math.max(0, buf.readVarInt()));
        List<UUID> uuids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            uuids.add(buf.readUUID());
        }
        String summary = buf.readUtf(128);
        return new C2SLineCommandPacket(mode, uuids, summary);
    }

    public static void handle(C2SLineCommandPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null || !(sender.level() instanceof ServerLevel serverLevel)) {
                return;
            }
            if (msg.groupUUIDs.isEmpty()) {
                return;
            }
            Set<UUID> groupSet = new HashSet<>(msg.groupUUIDs);
            UUID senderUUID = sender.getUUID();

            AABB searchArea = sender.getBoundingBox().inflate(160.0D, 96.0D, 160.0D);
            List<AbstractRecruitEntity> recruits = serverLevel.getEntitiesOfClass(
                    AbstractRecruitEntity.class,
                    searchArea,
                    r -> r.isAlive()
                            && r.isOwned()
                            && senderUUID.equals(r.getOwnerUUID())
                            && r.getGroup() != null
                            && groupSet.contains(r.getGroup())
                            && LineInfantryHelper.hasMusketAnywhere(r)
            );

            int affected = applyModeToRecruits(recruits, msg.mode);
            String label = msg.groupNamesSummary.isBlank() ? "*" : msg.groupNamesSummary;
            switch (msg.mode) {
                case SHOULDER_ARMS -> sender.displayClientMessage(
                        Component.translatable("msg.napoleonic_line.shoulder_arms", label), true);
                case PRESENT_ARMS -> sender.displayClientMessage(
                        Component.translatable("msg.napoleonic_line.present_arms", label), true);
                case FIRE_AT_WILL -> sender.displayClientMessage(
                        Component.translatable("msg.napoleonic_line.fire_at_will", label), true);
                case RELOAD -> sender.displayClientMessage(
                        Component.translatable("msg.napoleonic_line.reload", label, affected), true);
                case CEASE_FIRE -> sender.displayClientMessage(
                        Component.translatable("msg.napoleonic_line.cease_fire", label), true);
                default -> {
                }
            }
        });
        ctx.setPacketHandled(true);
    }

    public static int applyModeToRecruits(List<AbstractRecruitEntity> recruits, LineInfantryMode mode) {
        int count = 0;
        for (AbstractRecruitEntity recruit : recruits) {
            IRecruitLineData data = (IRecruitLineData) recruit;
            data.napoleonic$setVolleyPending(false);
            data.napoleonic$setVolleyAimPoint(null);
            data.napoleonic$setMode(mode);

            switch (mode) {
                case SHOULDER_ARMS -> {
                    if (data.napoleonic$isReloading()) {
                        var stack = LineInfantryHelper.getRecruitMusketStack(recruit);
                        if (stack != null) LineInfantryHelper.setStackReloadingFlag(stack, 0, 1, false);
                    }
                    data.napoleonic$setReloading(false);
                    data.napoleonic$setReloadProgressTicks(0);
                    LineInfantryHelper.ensureMusketInOffhand(recruit);
                    recruit.setTarget(null);
                    recruit.setAggressive(false);
                    count++;
                }
                case PRESENT_ARMS -> {
                    if (data.napoleonic$isReloading()) {
                        var stack = LineInfantryHelper.getRecruitMusketStack(recruit);
                        if (stack != null) LineInfantryHelper.setStackReloadingFlag(stack, 0, 1, false);
                    }
                    data.napoleonic$setReloading(false);
                    data.napoleonic$setReloadProgressTicks(0);
                    LineInfantryHelper.ensureMusketInMainHand(recruit);
                    recruit.setTarget(null);
                    recruit.setAggressive(true);
                    count++;
                }
                case FIRE_AT_WILL -> {
                    LineInfantryHelper.ensureMusketInMainHand(recruit);
                    count++;
                }
                case RELOAD -> {
                    LineInfantryHelper.ensureMusketInMainHand(recruit);
                    recruit.setTarget(null);
                    recruit.setAggressive(false);
                    if (!LineInfantryHelper.isMusketLoaded(recruit)) {
                        if (!data.napoleonic$isReloading() && LineInfantryHelper.hasOrConsumeCartridge(recruit, true)) {
                            int totalTicks = LineInfantryHelper.rollRecruitReloadTicks(recruit.getRandom());
                            data.napoleonic$setReloadTotalTicks(totalTicks);
                            data.napoleonic$setReloadProgressTicks(0);
                            data.napoleonic$setReloading(true);
                            var stack = LineInfantryHelper.getRecruitMusketStack(recruit);
                            if (stack != null) LineInfantryHelper.setStackReloadingFlag(stack, 0, totalTicks, true);
                        }
                        count++;
                    }
                }
                case CEASE_FIRE -> {
                    LineInfantryHelper.ensureMusketInMainHand(recruit);
                    if (!LineInfantryHelper.holdsBalancedFirearm(recruit)) {
                        if (data.napoleonic$isReloading()) {
                            var stack = LineInfantryHelper.getRecruitMusketStack(recruit);
                            if (stack != null) LineInfantryHelper.setStackReloadingFlag(stack, 0, 1, false);
                        }
                        data.napoleonic$setReloading(false);
                        data.napoleonic$setReloadProgressTicks(0);
                        recruit.setAggressive(false);
                    }
                    count++;
                }
                default -> {
                }
            }
            data.napoleonic$syncToTrackingClients();
        }
        return count;
    }
}
