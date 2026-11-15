package com.napoleonic.line.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.napoleonic.line.client.gui.LineInfantryCategory;
import com.napoleonic.line.client.gui.LineInfantryScreen;
import com.talhanation.recruits.client.events.CommandCategoryManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public final class ClientSetup {
    public static final KeyMapping OPEN_MENU_KEY = new KeyMapping(
            "key.napoleonic_line.open_menu",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.napoleonic_line"
    );

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(ClientSetup::onClientSetup);
        modEventBus.addListener(ClientSetup::onRegisterKeyMappings);

        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(VolleyAimingClientHandler::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(VolleyAimingClientHandler::onMouseInput);
        MinecraftForge.EVENT_BUS.addListener(VolleyAimingClientHandler::onKeyInput);
        MinecraftForge.EVENT_BUS.addListener(VolleyAimingClientHandler::onRenderLevelStage);
    }

    private static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Register our Line Infantry tab in Recruits' CommandScreen
            CommandCategoryManager.register(new LineInfantryCategory(), 15);
            MusketClientHooks.registerItemProperties();
        });
    }

    private static void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU_KEY);
    }

    private static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        while (OPEN_MENU_KEY.consumeClick()) {
            if (mc.screen == null) {
                VolleyAimingClientHandler.cancelAiming(false);
                mc.setScreen(new LineInfantryScreen(mc.player));
            }
        }
    }

    /**
     * Plain campfire smoke. Napoleonic Line Effects cancels {@link MusketShotClientEvent}
     * and draws the dense cloud plus tracers instead.
     */
    public static void spawnShotParticles(Vec3 muzzleOrigin, Vec3 direction, Vec3 panOrigin) {
        MusketShotClientEvent shot = new MusketShotClientEvent(muzzleOrigin, direction, panOrigin);
        MinecraftForge.EVENT_BUS.post(shot);
        if (shot.isCanceled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        RandomSource rng = level.random;
        Vec3 normDir = direction.lengthSqr() > 1.0E-4D ? direction.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
        for (int i = 0; i < 8; i++) {
            double spread = 0.12D + rng.nextDouble() * 0.35D;
            level.addParticle(
                    ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    muzzleOrigin.x + (rng.nextDouble() - 0.5D) * spread,
                    muzzleOrigin.y + rng.nextDouble() * 0.2D,
                    muzzleOrigin.z + (rng.nextDouble() - 0.5D) * spread,
                    normDir.x * 0.02D,
                    0.02D,
                    normDir.z * 0.02D
            );
        }
        level.addParticle(ParticleTypes.POOF, panOrigin.x, panOrigin.y, panOrigin.z, 0.0D, 0.02D, 0.0D);
    }

    private ClientSetup() {
    }
}

