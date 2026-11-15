package com.napoleonic.line.network;

import com.napoleonic.line.ai.LineInfantryHelper;
import com.napoleonic.line.config.NapoleonicServerConfig;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class C2SVolleyFirePacket {
    private final List<UUID> groupUUIDs;
    private final Vec3 targetPoint;
    private final String groupNamesSummary;

    public C2SVolleyFirePacket(List<UUID> groupUUIDs, Vec3 targetPoint, String groupNamesSummary) {
        this.groupUUIDs = groupUUIDs;
        this.targetPoint = targetPoint;
        this.groupNamesSummary = groupNamesSummary != null ? groupNamesSummary : "";
    }

    public static void encode(C2SVolleyFirePacket msg, FriendlyByteBuf buf) {
        int count = Math.min(32, msg.groupUUIDs.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeUUID(msg.groupUUIDs.get(i));
        }
        buf.writeDouble(msg.targetPoint.x);
        buf.writeDouble(msg.targetPoint.y);
        buf.writeDouble(msg.targetPoint.z);
        buf.writeUtf(msg.groupNamesSummary, 128);
    }

    public static C2SVolleyFirePacket decode(FriendlyByteBuf buf) {
        int count = Math.min(32, Math.max(0, buf.readVarInt()));
        List<UUID> uuids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            uuids.add(buf.readUUID());
        }
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        String summary = buf.readUtf(128);
        return new C2SVolleyFirePacket(uuids, new Vec3(x, y, z), summary);
    }

    public static void handle(C2SVolleyFirePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null || !(sender.level() instanceof ServerLevel serverLevel)) {
                return;
            }
            if (msg.groupUUIDs.isEmpty()) {
                return;
            }
            if (!Double.isFinite(msg.targetPoint.x) || !Double.isFinite(msg.targetPoint.y) || !Double.isFinite(msg.targetPoint.z)) {
                return;
            }
            double maxDist = NapoleonicServerConfig.VOLLEY_MAX_RAYCAST_DISTANCE.get() + 12.0D;
            if (sender.position().distanceToSqr(msg.targetPoint) > maxDist * maxDist) {
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

            int readyShooters = LineInfantryHelper.scheduleGroupVolley(recruits, msg.targetPoint, sender.getLookAngle());
            String label = msg.groupNamesSummary.isBlank() ? "*" : msg.groupNamesSummary;
            if (readyShooters > 0) {
                sender.displayClientMessage(
                        Component.translatable("msg.napoleonic_line.volley_fired", label, readyShooters), true);
            } else {
                sender.displayClientMessage(
                        Component.translatable("msg.napoleonic_line.volley_no_ready"), true);
            }
        });
        ctx.setPacketHandled(true);
    }
}
