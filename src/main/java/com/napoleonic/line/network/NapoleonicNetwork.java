package com.napoleonic.line.network;

import com.napoleonic.line.NapoleonicLineMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class NapoleonicNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(NapoleonicLineMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.registerMessage(
                packetId++,
                C2SLineCommandPacket.class,
                C2SLineCommandPacket::encode,
                C2SLineCommandPacket::decode,
                C2SLineCommandPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                packetId++,
                C2SVolleyGlancePacket.class,
                C2SVolleyGlancePacket::encode,
                C2SVolleyGlancePacket::decode,
                C2SVolleyGlancePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                packetId++,
                C2SVolleyFirePacket.class,
                C2SVolleyFirePacket::encode,
                C2SVolleyFirePacket::decode,
                C2SVolleyFirePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                packetId++,
                S2CRecruitLineStatePacket.class,
                S2CRecruitLineStatePacket::encode,
                S2CRecruitLineStatePacket::decode,
                S2CRecruitLineStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        CHANNEL.registerMessage(
                packetId++,
                S2CMusketShotEffectPacket.class,
                S2CMusketShotEffectPacket::encode,
                S2CMusketShotEffectPacket::decode,
                S2CMusketShotEffectPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    private NapoleonicNetwork() {
    }
}
