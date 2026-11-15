package com.napoleonic.line.network;

import com.napoleonic.line.client.ClientSetup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CMusketShotEffectPacket {
    public final Vec3 muzzleOrigin;
    public final Vec3 direction;
    public final Vec3 panOrigin;

    public S2CMusketShotEffectPacket(Vec3 muzzleOrigin, Vec3 direction, Vec3 panOrigin) {
        this.muzzleOrigin = muzzleOrigin;
        this.direction = direction;
        this.panOrigin = panOrigin;
    }

    public static void encode(S2CMusketShotEffectPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat((float) msg.muzzleOrigin.x);
        buf.writeFloat((float) msg.muzzleOrigin.y);
        buf.writeFloat((float) msg.muzzleOrigin.z);
        buf.writeFloat((float) msg.direction.x);
        buf.writeFloat((float) msg.direction.y);
        buf.writeFloat((float) msg.direction.z);
        buf.writeFloat((float) msg.panOrigin.x);
        buf.writeFloat((float) msg.panOrigin.y);
        buf.writeFloat((float) msg.panOrigin.z);
    }

    public static S2CMusketShotEffectPacket decode(FriendlyByteBuf buf) {
        Vec3 muzzle = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        Vec3 dir = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        Vec3 pan = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        return new S2CMusketShotEffectPacket(muzzle, dir, pan);
    }

    public static void handle(S2CMusketShotEffectPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientSetup.spawnShotParticles(msg.muzzleOrigin, msg.direction, msg.panOrigin)
        ));
        ctx.setPacketHandled(true);
    }
}
