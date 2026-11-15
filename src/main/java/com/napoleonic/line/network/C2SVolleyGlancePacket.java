package com.napoleonic.line.network;

import com.napoleonic.line.ai.LineInfantryHelper;
import com.napoleonic.line.config.NapoleonicServerConfig;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.network.FriendlyByteBuf;
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

/**
 * While Volley at Point is being aimed, turn the line to face the block under the crosshair.
 * Does not fire.
 */
public class C2SVolleyGlancePacket {
    private final List<UUID> groupUUIDs;
    private final Vec3 targetPoint;

    public C2SVolleyGlancePacket(List<UUID> groupUUIDs, Vec3 targetPoint) {
        this.groupUUIDs = groupUUIDs;
        this.targetPoint = targetPoint;
    }

    public static void encode(C2SVolleyGlancePacket msg, FriendlyByteBuf buf) {
        int count = Math.min(32, msg.groupUUIDs.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeUUID(msg.groupUUIDs.get(i));
        }
        buf.writeDouble(msg.targetPoint.x);
        buf.writeDouble(msg.targetPoint.y);
        buf.writeDouble(msg.targetPoint.z);
    }

    public static C2SVolleyGlancePacket decode(FriendlyByteBuf buf) {
        int count = Math.min(32, Math.max(0, buf.readVarInt()));
        List<UUID> uuids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            uuids.add(buf.readUUID());
        }
        return new C2SVolleyGlancePacket(uuids, new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    }

    public static void handle(C2SVolleyGlancePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null || !(sender.level() instanceof ServerLevel serverLevel)) {
                return;
            }
            if (msg.groupUUIDs.isEmpty() || !Double.isFinite(msg.targetPoint.x)) {
                return;
            }
            double maxDist = NapoleonicServerConfig.VOLLEY_MAX_RAYCAST_DISTANCE.get() + 12.0D;
            if (sender.position().distanceToSqr(msg.targetPoint) > maxDist * maxDist) {
                return;
            }
            Set<UUID> groupSet = new HashSet<>(msg.groupUUIDs);
            UUID senderUUID = sender.getUUID();
            List<AbstractRecruitEntity> recruits = serverLevel.getEntitiesOfClass(
                    AbstractRecruitEntity.class,
                    sender.getBoundingBox().inflate(160.0D, 96.0D, 160.0D),
                    r -> r.isAlive()
                            && r.isOwned()
                            && senderUUID.equals(r.getOwnerUUID())
                            && r.getGroup() != null
                            && groupSet.contains(r.getGroup())
                            && LineInfantryHelper.hasMusketAnywhere(r)
            );
            LineInfantryHelper.glanceLineAt(recruits, msg.targetPoint, sender.getLookAngle());
        });
        ctx.setPacketHandled(true);
    }
}
