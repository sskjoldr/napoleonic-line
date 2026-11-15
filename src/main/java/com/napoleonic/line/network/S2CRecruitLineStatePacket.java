package com.napoleonic.line.network;

import com.napoleonic.line.client.ClientLineStateCache;
import com.napoleonic.line.entity.LineInfantryMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CRecruitLineStatePacket {
    private final int entityId;
    private final LineInfantryMode mode;
    private final boolean reloading;
    private final int reloadProgressTicks;
    private final int reloadTotalTicks;

    public S2CRecruitLineStatePacket(int entityId, LineInfantryMode mode, boolean reloading,
                                     int reloadProgressTicks, int reloadTotalTicks) {
        this.entityId = entityId;
        this.mode = mode;
        this.reloading = reloading;
        this.reloadProgressTicks = reloadProgressTicks;
        this.reloadTotalTicks = reloadTotalTicks;
    }

    public static void encode(S2CRecruitLineStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeByte(msg.mode.getId());
        buf.writeBoolean(msg.reloading);
        buf.writeVarInt(msg.reloadProgressTicks);
        buf.writeVarInt(msg.reloadTotalTicks);
    }

    public static S2CRecruitLineStatePacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        LineInfantryMode mode = LineInfantryMode.byId(buf.readByte());
        boolean reloading = buf.readBoolean();
        int progress = buf.readVarInt();
        int total = buf.readVarInt();
        return new S2CRecruitLineStatePacket(entityId, mode, reloading, progress, total);
    }

    public static void handle(S2CRecruitLineStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientLineStateCache.updateState(
                        msg.entityId, msg.mode, msg.reloading, msg.reloadProgressTicks, msg.reloadTotalTicks
                )
        ));
        ctx.setPacketHandled(true);
    }
}
