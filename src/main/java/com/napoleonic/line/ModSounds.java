package com.napoleonic.line;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, NapoleonicLineMod.MODID);

    public static final RegistryObject<SoundEvent> ARQUEBUS_FIRE = SOUNDS.register(
            "arquebus_fire",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(NapoleonicLineMod.MODID, "arquebus_fire"))
    );

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }

    private ModSounds() {
    }
}
