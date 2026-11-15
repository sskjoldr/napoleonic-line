package com.napoleonic.line;

import com.mojang.logging.LogUtils;
import com.napoleonic.line.client.ClientSetup;
import com.napoleonic.line.config.NapoleonicClientConfig;
import com.napoleonic.line.config.NapoleonicServerConfig;
import com.napoleonic.line.entity.IRecruitLineData;
import com.napoleonic.line.entity.LineInfantryMode;
import com.napoleonic.line.entity.NapoleonicDamageTypes;
import com.napoleonic.line.network.NapoleonicNetwork;
import com.napoleonic.line.network.S2CRecruitLineStatePacket;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

@Mod(NapoleonicLineMod.MODID)
public class NapoleonicLineMod {
    public static final String MODID = "napoleonic_line";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static boolean isBoomsticksAddonLoaded = false;
    private static final ThreadLocal<Float> PRE_MITIGATION_MUSKET_DAMAGE = new ThreadLocal<>();

    @SuppressWarnings("removal")
    public NapoleonicLineMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, NapoleonicServerConfig.SPEC, "napoleonic_line-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, NapoleonicClientConfig.SPEC, "napoleonic_line-client.toml");

        ModSounds.register(modEventBus);
        NapoleonicNetwork.register();

        isBoomsticksAddonLoaded = ModList.get().isLoaded("recruits_use_boomsticks");
        if (isBoomsticksAddonLoaded) {
            LOGGER.info("[Napoleonic Line Infantry] Detected 'recruits_use_boomsticks' — Line Infantry musket AI priority enabled.");
        }

        MinecraftForge.EVENT_BUS.addListener(this::onStartTracking);
        MinecraftForge.EVENT_BUS.addListener(this::onEntityJoinLevel);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onLivingHurt);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, this::onLivingDamage);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, this::onLivingChangeTarget);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modEventBus));
    }

    private void onEntityJoinLevel(final net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel && !serverLevel.players().isEmpty()) {
            net.minecraft.world.entity.Entity entity = event.getEntity();
            net.minecraft.resources.ResourceLocation typeKey = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            if (typeKey != null && typeKey.getNamespace().equals("medieval_boomsticks") && typeKey.getPath().contains("round_ball")) {
                net.minecraft.world.phys.Vec3 vel = entity.getDeltaMovement();
                net.minecraft.world.phys.Vec3 dir = vel.lengthSqr() > 1.0E-4D ? vel.normalize() : entity.getLookAngle();
                net.minecraft.world.phys.Vec3 muzzlePos = entity.position().add(dir.scale(0.35D));
                net.minecraft.world.phys.Vec3 panPos = entity.position().add(dir.scale(0.1D)).add(0.0D, 0.04D, 0.0D);
                PacketDistributor.TargetPoint point = new PacketDistributor.TargetPoint(
                        muzzlePos.x, muzzlePos.y, muzzlePos.z, 128.0D, serverLevel.dimension());
                NapoleonicNetwork.CHANNEL.send(
                        PacketDistributor.NEAR.with(() -> point),
                        new com.napoleonic.line.network.S2CMusketShotEffectPacket(muzzlePos, dir, panPos)
                );
            }
        }
    }

    private void onStartTracking(final PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof AbstractRecruitEntity recruit
                && event.getEntity() instanceof ServerPlayer serverPlayer) {
            IRecruitLineData data = (IRecruitLineData) recruit;
            NapoleonicNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new S2CRecruitLineStatePacket(
                            recruit.getId(),
                            data.napoleonic$getMode(),
                            data.napoleonic$isReloading(),
                            data.napoleonic$getReloadProgressTicks(),
                            data.napoleonic$getReloadTotalTicks()
                    )
            );
        }
    }

    private void onLivingHurt(final LivingHurtEvent event) {
        if (event.getSource().is(NapoleonicDamageTypes.MUSKET_BALL)) {
            PRE_MITIGATION_MUSKET_DAMAGE.set(event.getAmount());
            return;
        }
        net.minecraft.world.entity.Entity direct = event.getSource().getDirectEntity();
        if (direct != null) {
            net.minecraft.resources.ResourceLocation typeKey = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(direct.getType());
            if (typeKey != null && typeKey.getNamespace().equals("medieval_boomsticks") && typeKey.getPath().contains("round_ball")) {
                float baseDmg = NapoleonicServerConfig.SPEC.isLoaded()
                        ? NapoleonicServerConfig.MUSKET_DAMAGE.get().floatValue()
                        : 35.0F;
                event.setAmount(baseDmg);
                PRE_MITIGATION_MUSKET_DAMAGE.set(baseDmg);
                return;
            }
        }
        PRE_MITIGATION_MUSKET_DAMAGE.remove();
    }

    private void onLivingDamage(final LivingDamageEvent event) {
        try {
            Float preDamage = PRE_MITIGATION_MUSKET_DAMAGE.get();
            if (preDamage == null) {
                return;
            }
            boolean isMusket = event.getSource().is(NapoleonicDamageTypes.MUSKET_BALL);
            if (!isMusket) {
                // For medieval_boomsticks round_ball_projectile, bypass armor and Resistance as well
                event.setAmount(preDamage);
                return;
            }
            LivingEntity target = event.getEntity();
            if (NapoleonicServerConfig.IGNORE_RESISTANCE_EFFECT.get() && target.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
                // Bypass Resistance damage reduction while allowing Totem of Undying to trigger normally in LivingEntity.hurt
                event.setAmount(preDamage);
            }
        } finally {
            PRE_MITIGATION_MUSKET_DAMAGE.remove();
        }
    }

    private void onLivingChangeTarget(final LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof AbstractRecruitEntity recruit && event.getNewTarget() != null) {
            IRecruitLineData data = (IRecruitLineData) recruit;
            LineInfantryMode mode = data.napoleonic$getMode();
            if (mode == LineInfantryMode.VOLLEY || mode == LineInfantryMode.RELOAD || mode == LineInfantryMode.PRESENT_ARMS) {
                event.setCanceled(true);
            } else if (mode == LineInfantryMode.SHOULDER_ARMS && !NapoleonicServerConfig.SHOULDER_ARMS_MELEE_DEFENSE.get()) {
                event.setCanceled(true);
            }
        }
    }
}


