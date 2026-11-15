package com.napoleonic.line.ai;

import com.napoleonic.line.ModSounds;
import com.napoleonic.line.config.NapoleonicServerConfig;
import com.napoleonic.line.entity.IRecruitLineData;
import com.napoleonic.line.entity.LineInfantryMode;
import com.napoleonic.line.network.NapoleonicNetwork;
import com.napoleonic.line.network.S2CMusketShotEffectPacket;
import com.napoleonic.line.compat.MusketModCompat;
import com.talhanation.recruits.RecruitEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LineInfantryHelper {
    private static final String RUB_CARRY_ORDER_NBT_KEY = "RubBoomstickInHands";

    private static final Set<ResourceLocation> MEDIEVAL_BOOMSTICK_IDS = Set.of(
            new ResourceLocation("medieval_boomsticks", "arquebus"),
            new ResourceLocation("medieval_boomsticks", "handgonne"),
            new ResourceLocation("medieval_boomsticks", "spikedhandgonne")
    );

    private static final Set<ResourceLocation> THIRTY_YEARS_WAR_IDS = Set.of(
            new ResourceLocation("thirtyyearswar", "heavy_matchlock_musket"),
            new ResourceLocation("thirtyyearswar", "baroque_matchlock_musket"),
            new ResourceLocation("thirtyyearswar", "snaphance_musket"),
            new ResourceLocation("thirtyyearswar", "wheellock_carbine"),
            new ResourceLocation("thirtyyearswar", "wheellock_pistol"),
            new ResourceLocation("thirtyyearswar", "baroque_wheellock_pistol")
    );

    private static final Set<ResourceLocation> ARTILLERY_ADDON_IDS = Set.of(
            new ResourceLocation("artillery_addon", "handgonne"),
            new ResourceLocation("artillery_addon", "spiked_handgonne"),
            new ResourceLocation("artillery_addon", "arquebus"),
            new ResourceLocation("artillery_addon", "matchlock_musket"),
            new ResourceLocation("artillery_addon", "flintlock_musket"),
            new ResourceLocation("artillery_addon", "bayonet_musket"),
            new ResourceLocation("artillery_addon", "caliver"),
            new ResourceLocation("artillery_addon", "blunderbuss"),
            new ResourceLocation("artillery_addon", "pistol"),
            new ResourceLocation("artillery_addon", "matchlock_pistol"),
            new ResourceLocation("artillery_addon", "flintlock_pistol"),
            new ResourceLocation("artillery_addon", "double_barreled_pistol")
    );

    private static final ResourceLocation ROUND_BALL_ID = new ResourceLocation("medieval_boomsticks", "round_ball");
    private static final ResourceLocation TYW_CARTRIDGE_ID = new ResourceLocation("thirtyyearswar", "musket_cartridge");
    private static final ResourceLocation AA_MUSKET_BALL_ID = new ResourceLocation("artillery_addon", "musket_ball");
    private static final ResourceLocation AA_BLUNDERBUSS_SHOT_ID = new ResourceLocation("artillery_addon", "blunderbuss_shot");
    private static final ResourceLocation AA_GUNPOWDER_POUCH_ID = new ResourceLocation("artillery_addon", "gunpowder_pouch");

    private static final ResourceLocation ARQUEBUS_SHOOT_SOUND_ID = new ResourceLocation("medieval_boomsticks", "arquebus_shoot");
    private static final ResourceLocation HANDGONNE_SHOOT_SOUND_ID = new ResourceLocation("medieval_boomsticks", "handgonne_shoot");

    private static long currentBudgetTick = -1L;
    private static int losChecksThisTick = 0;

    private static final Map<UUID, Glance> GLANCES = new ConcurrentHashMap<>();

    private record Glance(Vec3 point, long untilTick) {
    }

    // Cached target search per tick per chunk-region to avoid repeated getEntitiesOfClass calls
    private static long cachedTargetScanTick = -1L;
    private static ServerLevel cachedTargetScanLevel = null;
    private static Vec3 cachedTargetScanCenter = Vec3.ZERO;
    private static List<LivingEntity> cachedTargetCandidates = List.of();

    public static boolean consumeLosBudget(Level level) {
        long tick = level.getGameTime();
        if (tick != currentBudgetTick) {
            currentBudgetTick = tick;
            losChecksThisTick = 0;
        }
        if (losChecksThisTick >= NapoleonicServerConfig.MAX_LOS_CHECKS_PER_TICK.get()) {
            return false;
        }
        losChecksThisTick++;
        return true;
    }

    public static boolean isMedievalBoomstick(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && MEDIEVAL_BOOMSTICK_IDS.contains(id);
    }

    public static boolean isArtilleryAddonFirearm(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && ARTILLERY_ADDON_IDS.contains(id);
    }

    public static boolean isThirtyYearsWarFirearm(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && THIRTY_YEARS_WAR_IDS.contains(id);
    }

    public static boolean isMusketWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (MusketModCompat.isLoaded() && MusketModCompat.isGun(stack)) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            return false;
        }
        return MEDIEVAL_BOOMSTICK_IDS.contains(id)
                || THIRTY_YEARS_WAR_IDS.contains(id)
                || ARTILLERY_ADDON_IDS.contains(id);
    }

    public static boolean isFirearmAmmo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null && "musketmod".equals(id.getNamespace()) && "cartridge".equals(id.getPath())) {
            return true;
        }
        if (id == null) {
            return false;
        }
        return id.equals(ROUND_BALL_ID)
                || id.equals(TYW_CARTRIDGE_ID)
                || id.equals(AA_MUSKET_BALL_ID)
                || id.equals(AA_BLUNDERBUSS_SHOT_ID)
                || id.equals(AA_GUNPOWDER_POUCH_ID);
    }

    public static boolean hasMusketAnywhere(AbstractRecruitEntity recruit) {
        if (isMusketWeapon(recruit.getMainHandItem()) || isMusketWeapon(recruit.getOffhandItem())) {
            return true;
        }
        var inv = recruit.getInventory();
        for (int i = 6; i < inv.getContainerSize(); i++) {
            if (isMusketWeapon(inv.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    /** Boomstick, Thirty Years' War, and artillery-addon guns that must keep the mod's reload and damage. */
    public static boolean isBalancedFirearm(ItemStack stack) {
        return isMedievalBoomstick(stack) || isThirtyYearsWarFirearm(stack) || isArtilleryAddonFirearm(stack);
    }

    public static boolean holdsBalancedFirearm(AbstractRecruitEntity recruit) {
        if (isBalancedFirearm(recruit.getMainHandItem()) || isBalancedFirearm(recruit.getOffhandItem())) {
            return true;
        }
        var inv = recruit.getInventory();
        for (int i = 6; i < inv.getContainerSize(); i++) {
            if (isBalancedFirearm(inv.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Safely finishes any active offhand food/drink use before swapping hand items so no item is ever lost or duped.
     */
    private static void finishActiveItemUseBeforeSwap(AbstractRecruitEntity recruit) {
        if (recruit.isUsingItem()) {
            recruit.stopUsingItem();
            if (recruit.getBeforeItemSlot() > 0) {
                recruit.resetItemInHand();
            }
        }
    }

    /**
     * Moves the recruit's firearm to OFF_HAND for «На плечо» (Shoulder Arms) using an exact slot swap.
     */
    public static boolean ensureMusketInOffhand(AbstractRecruitEntity recruit) {
        recruit.getPersistentData().putBoolean(RUB_CARRY_ORDER_NBT_KEY, false);
        if (isMusketWeapon(recruit.getOffhandItem())) {
            setStackReloadingFlag(recruit.getOffhandItem(), 0, 1, false);
            return true;
        }
        finishActiveItemUseBeforeSwap(recruit);
        if (isMusketWeapon(recruit.getOffhandItem())) {
            setStackReloadingFlag(recruit.getOffhandItem(), 0, 1, false);
            return true;
        }
        if (isMusketWeapon(recruit.getMainHandItem())) {
            ItemStack main = recruit.getMainHandItem().copy();
            setStackReloadingFlag(main, 0, 1, false);
            ItemStack off = recruit.getOffhandItem().copy();
            recruit.setItemInHand(InteractionHand.MAIN_HAND, off);
            recruit.setItemInHand(InteractionHand.OFF_HAND, main);
            return true;
        }
        var inv = recruit.getInventory();
        for (int i = 6; i < inv.getContainerSize(); i++) {
            ItemStack slotStack = inv.getItem(i);
            if (isMusketWeapon(slotStack)) {
                ItemStack gun = slotStack.copy();
                setStackReloadingFlag(gun, 0, 1, false);
                ItemStack off = recruit.getOffhandItem().copy();
                inv.setItem(i, off);
                recruit.setItemInHand(InteractionHand.OFF_HAND, gun);
                return true;
            }
        }
        return false;
    }

    /**
     * Moves the recruit's firearm back to MAIN_HAND for Present Arms, firing, or reloading using an exact slot swap.
     */
    public static boolean ensureMusketInMainHand(AbstractRecruitEntity recruit) {
        recruit.getPersistentData().putBoolean(RUB_CARRY_ORDER_NBT_KEY, true);
        if (isMusketWeapon(recruit.getMainHandItem())) {
            return true;
        }
        finishActiveItemUseBeforeSwap(recruit);
        if (isMusketWeapon(recruit.getMainHandItem())) {
            return true;
        }
        if (isMusketWeapon(recruit.getOffhandItem())) {
            ItemStack off = recruit.getOffhandItem().copy();
            ItemStack main = recruit.getMainHandItem().copy();
            recruit.setItemInHand(InteractionHand.OFF_HAND, main);
            recruit.setItemInHand(InteractionHand.MAIN_HAND, off);
            return true;
        }
        var inv = recruit.getInventory();
        for (int i = 6; i < inv.getContainerSize(); i++) {
            ItemStack slotStack = inv.getItem(i);
            if (isMusketWeapon(slotStack)) {
                ItemStack gun = slotStack.copy();
                ItemStack main = recruit.getMainHandItem().copy();
                inv.setItem(i, main);
                recruit.setItemInHand(InteractionHand.MAIN_HAND, gun);
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static ItemStack getRecruitMusketStack(AbstractRecruitEntity recruit) {
        if (isMusketWeapon(recruit.getMainHandItem())) {
            return recruit.getMainHandItem();
        }
        if (isMusketWeapon(recruit.getOffhandItem())) {
            return recruit.getOffhandItem();
        }
        return null;
    }

    public static boolean isStackLoaded(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (MusketModCompat.isLoaded() && MusketModCompat.isGun(stack)) {
            return MusketModCompat.isStackLoaded(stack);
        }
        if (stack.getItem() instanceof CrossbowItem || isMedievalBoomstick(stack) || isThirtyYearsWarFirearm(stack)) {
            return CrossbowItem.isCharged(stack);
        }
        if (isArtilleryAddonFirearm(stack)) {
            CompoundTag tag = stack.getTag();
            return tag != null && (tag.getBoolean("isLoaded") || tag.getByte("isLoaded") != 0);
        }
        return false;
    }

    public static void setStackLoaded(ItemStack stack, boolean loaded) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (MusketModCompat.isLoaded() && MusketModCompat.isGun(stack)) {
            MusketModCompat.setLoaded(stack, loaded);
            return;
        }
        if (stack.getItem() instanceof CrossbowItem || isMedievalBoomstick(stack) || isThirtyYearsWarFirearm(stack)) {
            CompoundTag tag = stack.getOrCreateTag();
            CrossbowItem.setCharged(stack, loaded);
            tag.putBoolean("Charged", loaded);
            tag.putBoolean("recharge", false);
            tag.putBoolean("fire", !loaded);
            ListTag projectiles = new ListTag();
            if (loaded) {
                Item projItem = ForgeRegistries.ITEMS.getValue(ROUND_BALL_ID);
                if (projItem == null || projItem == net.minecraft.world.item.Items.AIR) {
                    projItem = net.minecraft.world.item.Items.ARROW;
                }
                ItemStack projStack = new ItemStack(projItem, 1);
                CompoundTag projTag = new CompoundTag();
                projStack.save(projTag);
                projectiles.add(projTag);
            }
            tag.put("ChargedProjectiles", projectiles);
            return;
        }
        if (isArtilleryAddonFirearm(stack)) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putBoolean("isLoaded", loaded);
            tag.putInt("loadingStage", 0);
        }
    }

    public static void setStackReloadingFlag(ItemStack stack, int progressTicks, int totalTicks, boolean reloading) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (MusketModCompat.isLoaded() && MusketModCompat.isGun(stack)) {
            int stage = reloading ? Math.min(3, (progressTicks * 3) / Math.max(1, totalTicks)) : 0;
            MusketModCompat.setLoadingStage(stack, stage);
            return;
        }
        if (isMedievalBoomstick(stack) || stack.getItem() instanceof CrossbowItem) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putBoolean("recharge", reloading);
            if (reloading) {
                tag.putBoolean("fire", false);
            }
            return;
        }
        if (isArtilleryAddonFirearm(stack)) {
            CompoundTag tag = stack.getOrCreateTag();
            int stage = reloading ? Math.min(3, Math.max(1, (progressTicks * 3) / Math.max(1, totalTicks))) : 0;
            tag.putInt("loadingStage", stage);
        }
    }

    public static boolean isMusketLoaded(AbstractRecruitEntity recruit) {
        ItemStack stack = getRecruitMusketStack(recruit);
        return isStackLoaded(stack);
    }

    public static void setMusketLoaded(AbstractRecruitEntity recruit, boolean loaded) {
        ItemStack stack = getRecruitMusketStack(recruit);
        if (stack != null) {
            setStackLoaded(stack, loaded);
        }
    }

    /**
     * Line infantry does not spend cartridges, round balls, or pouches. Any musket, arquebus, or matchlock reloads dry.
     */
    public static boolean hasOrConsumeCartridge(AbstractRecruitEntity recruit, boolean consume) {
        return true;
    }

    public static int getRecruitReloadTotalTicks() {
        return Math.max(20, (int) Math.round(NapoleonicServerConfig.RECRUIT_RELOAD_SECONDS.get() * 20.0D));
    }

    /**
     * Base reload plus a small per-recruit swing, so the rank does not finish together.
     */
    public static int rollRecruitReloadTicks(RandomSource random) {
        int base = getRecruitReloadTotalTicks();
        double seconds = 2.0D;
        try {
            if (NapoleonicServerConfig.SPEC.isLoaded()) {
                seconds = NapoleonicServerConfig.RECRUIT_RELOAD_JITTER_SECONDS.get();
            }
        } catch (Exception ignored) {
        }
        int jitter = (int) Math.round(Math.max(0.0D, seconds) * 20.0D);
        if (jitter <= 0 || random == null) {
            return base;
        }
        return Math.max(40, base + random.nextInt(jitter * 2 + 1) - jitter);
    }

    public static void faceToward(AbstractRecruitEntity recruit, Vec3 point) {
        double dx = point.x - recruit.getX();
        double dz = point.z - recruit.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        recruit.setYRot(yaw);
        recruit.setYHeadRot(yaw);
        recruit.yBodyRot = yaw;
        recruit.getLookControl().setLookAt(point.x, point.y, point.z, 70.0F, 70.0F);
    }

    public static void glanceLineAt(List<AbstractRecruitEntity> recruits, Vec3 centerPoint, Vec3 fallbackForward) {
        if (recruits.isEmpty()) {
            return;
        }
        double sumX = 0.0D, sumZ = 0.0D;
        for (AbstractRecruitEntity recruit : recruits) {
            sumX += recruit.getX();
            sumZ += recruit.getZ();
        }
        double n = recruits.size();
        long until = recruits.get(0).level().getGameTime() + 8L;
        for (AbstractRecruitEntity recruit : recruits) {
            Vec3 lane = new Vec3(
                    centerPoint.x + (recruit.getX() - sumX / n),
                    centerPoint.y,
                    centerPoint.z + (recruit.getZ() - sumZ / n));
            GLANCES.put(recruit.getUUID(), new Glance(lane, until));
            faceToward(recruit, lane);
        }
    }

    @Nullable
    public static Vec3 activeGlance(AbstractRecruitEntity recruit) {
        Glance glance = GLANCES.get(recruit.getUUID());
        if (glance == null) {
            return null;
        }
        if (recruit.level().getGameTime() > glance.untilTick()) {
            GLANCES.remove(recruit.getUUID());
            return null;
        }
        return glance.point();
    }

    private static void spawnFallbackShot(ServerLevel level, AbstractRecruitEntity shooter, Vec3 muzzlePos,
                                          Vec3 direction, float blocksPerSecond, float damage) {
        EntityType<?> boomstickBall = null;
        for (var entry : ForgeRegistries.ENTITY_TYPES.getEntries()) {
            ResourceLocation id = entry.getKey().location();
            if ("medieval_boomsticks".equals(id.getNamespace()) && id.getPath().contains("round_ball")) {
                boomstickBall = entry.getValue();
                break;
            }
        }
        if (boomstickBall != null) {
            Entity entity = boomstickBall.create(level);
            if (entity != null) {
                entity.setPos(muzzlePos.x, muzzlePos.y, muzzlePos.z);
                entity.setDeltaMovement(direction.scale(Math.max(1.5D, blocksPerSecond / 20.0D)));
                if (entity instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
                    projectile.setOwner(shooter);
                }
                level.addFreshEntity(entity);
                return;
            }
        }
        Arrow arrow = new Arrow(level, shooter);
        arrow.setPos(muzzlePos.x, muzzlePos.y, muzzlePos.z);
        arrow.shoot(direction.x, direction.y, direction.z, (float) Math.max(1.5D, blocksPerSecond / 20.0D), 0.0F);
        arrow.setBaseDamage(Math.max(1.0D, damage / 6.0D));
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        level.addFreshEntity(arrow);
    }

    public static void playReloadStageSound(AbstractRecruitEntity recruit, int progressTicks, int totalTicks) {
        if (totalTicks <= 0) return;
        int stage1 = Math.max(1, totalTicks / 4);
        int stage2 = Math.max(2, totalTicks / 2);
        int stage3 = Math.max(3, (totalTicks * 3) / 4);

        SoundEvent sound = null;
        if (progressTicks == stage1) {
            sound = MusketModCompat.loadSound(0);
        } else if (progressTicks == stage2) {
            sound = MusketModCompat.loadSound(1);
        } else if (progressTicks == stage3) {
            sound = MusketModCompat.loadSound(2);
        } else if (progressTicks >= totalTicks) {
            sound = MusketModCompat.loadSound(3);
        }
        if (sound != null) {
            recruit.level().playSound(null, recruit.getX(), recruit.getY(), recruit.getZ(),
                    sound, SoundSource.NEUTRAL, 1.2F, 0.94F + recruit.getRandom().nextFloat() * 0.12F);
        }
    }

    /**
     * Returns true if other is an ally, same owner, same team/faction, same group, or a mount ridden by an ally.
     */
    public static boolean isFriendlyOrProtected(AbstractRecruitEntity shooter, Entity candidate) {
        if (candidate == shooter) return true;
        if (shooter.getVehicle() != null && shooter.getVehicle() == candidate) return true;

        if (candidate instanceof Animal animal && animal.isVehicle()) {
            Entity passenger = animal.getFirstPassenger();
            if (passenger != null) {
                return isFriendlyOrProtected(shooter, passenger);
            }
        }

        if (candidate instanceof LivingEntity living) {
            if (shooter.getOwnerUUID() != null && shooter.getOwnerUUID().equals(living.getUUID())) {
                return true;
            }
            if (living instanceof AbstractRecruitEntity otherRecruit) {
                if (shooter.isOwned() && otherRecruit.isOwned()
                        && shooter.getOwnerUUID() != null
                        && shooter.getOwnerUUID().equals(otherRecruit.getOwnerUUID())) {
                    return true;
                }
                if (shooter.getGroup() != null && shooter.getGroup().equals(otherRecruit.getGroup())) {
                    return true;
                }
            }
            return !RecruitEvents.canAttack(shooter, living);
        }
        return false;
    }

    /**
     * Checks whether the shooter has a clear line of fire to targetPos without hitting solid walls
     * or friendly recruits/allies in front. If blocked by an ally, optionally tries a small lateral offset.
     */
    @Nullable
    public static Vec3 findClearFiringOrigin(AbstractRecruitEntity shooter, Vec3 targetPos) {
        if (shooter.isEyeInFluid(FluidTags.WATER) || shooter.isEyeInFluid(FluidTags.LAVA)) {
            return null;
        }
        Vec3 eyePos = new Vec3(shooter.getX(), shooter.getEyeY() - 0.12D, shooter.getZ());
        Vec3 toTarget = targetPos.subtract(eyePos);
        double dist = toTarget.length();
        if (dist < 1.0E-3D || dist > NapoleonicServerConfig.MAX_FIRING_RANGE.get() + 8.0D) {
            return null;
        }
        Vec3 dir = toTarget.scale(1.0D / dist);

        if (isCorridorClear(shooter, eyePos, dir, dist)) {
            return eyePos;
        }

        // Blocked (e.g. by a friendly recruit in the front rank): try a small lateral shoulder/step offset (left/right)
        Vec3 right = new Vec3(-dir.z, 0.0D, dir.x).normalize();
        double[] offsets = new double[]{0.65D, -0.65D, 1.15D, -1.15D};
        for (double lat : offsets) {
            Vec3 candidateOrigin = eyePos.add(right.scale(lat));
            BlockPos feetPos = BlockPos.containing(candidateOrigin.x, shooter.getY(), candidateOrigin.z);
            if (!shooter.level().getBlockState(feetPos.above()).isSolidRender(shooter.level(), feetPos.above())) {
                Vec3 newDir = targetPos.subtract(candidateOrigin).normalize();
                if (isCorridorClear(shooter, candidateOrigin, newDir, dist)) {
                    // Nudge recruit slightly toward the clear lane if not mounted
                    if (!shooter.isPassenger() && Math.abs(lat) <= 0.7D) {
                        shooter.getMoveControl().strafe(0.0F, lat > 0 ? 0.5F : -0.5F);
                    }
                    return candidateOrigin;
                }
            }
        }
        return null;
    }

    private static boolean isCorridorClear(AbstractRecruitEntity shooter, Vec3 origin, Vec3 dir, double dist) {
        Level level = shooter.level();
        Vec3 muzzleStart = origin.add(dir.scale(0.85D));
        // 1. Check solid terrain obstruction up to min(dist - 1.2, 64.0)
        double blockCheckDist = Math.max(1.0D, Math.min(dist - 1.2D, 64.0D));
        Vec3 blockCheckEnd = origin.add(dir.scale(blockCheckDist));
        BlockHitResult blockHit = level.clip(new ClipContext(
                muzzleStart, blockCheckEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        if (blockHit.getType() != HitResult.Type.MISS) {
            double hitDist = blockHit.getLocation().distanceTo(origin);
            if (hitDist < dist - 1.5D) {
                return false;
            }
        }

        // 2. Check friendly entities in front along the first 28 blocks of the firing corridor
        double allyCheckDist = Math.min(dist, 28.0D);
        Vec3 allyCheckEnd = origin.add(dir.scale(allyCheckDist));
        AABB corridorBox = new AABB(muzzleStart, allyCheckEnd).inflate(0.5D);
        EntityHitResult allyHit = ProjectileUtil.getEntityHitResult(
                level,
                shooter,
                muzzleStart,
                allyCheckEnd,
                corridorBox,
                entity -> entity.isPickable()
                        && isFriendlyOrProtected(shooter, entity)
                        && entity.position().subtract(shooter.position()).dot(dir) > 0.75D,
                0.2F
        );
        return allyHit == null;
    }

    /**
     * Fires the recruit's firearm (MusketMod gun, Medieval Boomsticks arquebus/handgonne, etc.)
     * from firingOrigin toward targetPos with ballistic gravity compensation, natural distance-based dispersion,
     * custom smoke/flash/pan/tracer packet, and loud gunshot audio.
     */
    public static boolean fireMusketAtPoint(AbstractRecruitEntity shooter, Vec3 firingOrigin, Vec3 targetPos) {
        if (!(shooter.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!ensureMusketInMainHand(shooter)) {
            return false;
        }
        ItemStack stack = shooter.getMainHandItem();
        if (!isMusketWeapon(stack) || !isStackLoaded(stack)) {
            return false;
        }

        Vec3 delta = targetPos.subtract(firingOrigin);
        double horizontalDist = Math.hypot(delta.x, delta.z);
        double totalDist = delta.length();
        if (totalDist < 0.5D) {
            return false;
        }

        float bulletSpeedBlocksPerSec = 180.0F;
        float stdDev = 1.0F;
        float dropReduction = 1.0F;
        int pellets = 1;
        SoundEvent fireSound = MusketModCompat.musketFireSound();
        boolean hasFlame = false;

        MusketModCompat.GunStats gunStats = MusketModCompat.stats(stack);
        if (gunStats != null) {
            bulletSpeedBlocksPerSec = gunStats.speed;
            stdDev = gunStats.stdDev;
            dropReduction = gunStats.dropReduction;
            pellets = gunStats.pellets;
            fireSound = gunStats.fireSound;
            hasFlame = gunStats.flame;
        } else {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            String path = itemId != null ? itemId.getPath() : "";
            if (path.contains("handgonne")) {
                bulletSpeedBlocksPerSec = 150.0F;
                stdDev = 2.0F;
                SoundEvent custom = ForgeRegistries.SOUND_EVENTS.getValue(HANDGONNE_SHOOT_SOUND_ID);
                if (custom != null) fireSound = custom;
            } else if (path.contains("blunderbuss")) {
                bulletSpeedBlocksPerSec = 140.0F;
                stdDev = 3.5F;
                pellets = 8;
                if (fireSound == null) {
                    fireSound = MusketModCompat.musketFireSound();
                }
            } else {
                // Arquebus and the other smoothbores. Same volume as an ewe musket shot.
                bulletSpeedBlocksPerSec = 175.0F;
                stdDev = 1.25F;
                fireSound = ModSounds.ARQUEBUS_FIRE.get();
            }
        }

        double speedPerTick = Math.max(2.0D, bulletSpeedBlocksPerSec / 20.0D);
        double flightTicks = totalDist / (speedPerTick * 0.94D);
        double gravityDropCompensation = 0.5D * MusketModCompat.gravity() * flightTicks * flightTicks;

        Vec3 compensatedVec = new Vec3(delta.x, delta.y + gravityDropCompensation, delta.z).normalize();

        faceToward(shooter, targetPos);

        RandomSource random = shooter.getRandom();
        float effectiveStdDev = stdDev * (1.0F + (float) (horizontalDist / 160.0D));
        Vec3 primarySpreadDir = MusketModCompat.addSpread(compensatedVec, random, effectiveStdDev);
        Vec3 muzzlePos = firingOrigin.add(primarySpreadDir.scale(0.85D));

        float baseDamage = NapoleonicServerConfig.MUSKET_DAMAGE.get().floatValue();

        if (MusketModCompat.isLoaded()) {
            MusketModCompat.spawnBullets(serverLevel, shooter, muzzlePos, primarySpreadDir, compensatedVec,
                    bulletSpeedBlocksPerSec, effectiveStdDev, baseDamage, dropReduction, pellets, hasFlame, random);
        } else {
            spawnFallbackShot(serverLevel, shooter, muzzlePos, primarySpreadDir, bulletSpeedBlocksPerSec, baseDamage);
        }

        // Unload weapon
        setStackLoaded(stack, false);
        setStackReloadingFlag(stack, 0, 1, false);

        // Play loud, far-reaching gunshot sound with slight random pitch variation
        if (fireSound != null) {
            float volume = NapoleonicServerConfig.GUNSHOT_SOUND_VOLUME.get().floatValue();
            float pitchVar = NapoleonicServerConfig.GUNSHOT_PITCH_VARIATION.get().floatValue();
            float pitch = 1.0F + (random.nextFloat() * 2.0F - 1.0F) * pitchVar;
            serverLevel.playSound(null, firingOrigin.x, firingOrigin.y, firingOrigin.z,
                    fireSound, SoundSource.PLAYERS, volume, pitch);
        }

        // Send a SINGLE compact packet to nearby clients for pan smoke, muzzle flash, 20-40 smoke cloud particles, and tracer
        if (!serverLevel.players().isEmpty()) {
            Vec3 panPos = firingOrigin.add(primarySpreadDir.scale(0.32D)).add(0.0D, 0.04D, 0.0D);
            PacketDistributor.TargetPoint targetPoint = new PacketDistributor.TargetPoint(
                    muzzlePos.x, muzzlePos.y, muzzlePos.z, 128.0D, serverLevel.dimension());
            NapoleonicNetwork.CHANNEL.send(
                    PacketDistributor.NEAR.with(() -> targetPoint),
                    new S2CMusketShotEffectPacket(muzzlePos, primarySpreadDir, panPos)
            );
        }

        return true;
    }

    /**
     * Computes the volley ellipse geometry and assigns each ready recruit in the group its own
     * independent Gaussian-distributed target point inside the ellipse and a 0..6 tick ragged delay.
     */
    public static int scheduleGroupVolley(List<AbstractRecruitEntity> groupRecruits, Vec3 centerPoint, Vec3 fallbackForward) {
        if (groupRecruits.isEmpty()) {
            return 0;
        }

        // Compute group centroid
        double sumX = 0.0D, sumY = 0.0D, sumZ = 0.0D;
        for (AbstractRecruitEntity r : groupRecruits) {
            sumX += r.getX();
            sumY += r.getY();
            sumZ += r.getZ();
        }
        double n = groupRecruits.size();
        Vec3 centroid = new Vec3(sumX / n, sumY / n, sumZ / n);

        // Horizontal forward axis from group centroid to target point
        Vec3 horizToTarget = new Vec3(centerPoint.x - centroid.x, 0.0D, centerPoint.z - centroid.z);
        Vec3 forwardAxis;
        if (horizToTarget.lengthSqr() > 1.0E-4D) {
            forwardAxis = horizToTarget.normalize();
        } else if (fallbackForward.horizontalDistanceSqr() > 1.0E-4D) {
            forwardAxis = new Vec3(fallbackForward.x, 0.0D, fallbackForward.z).normalize();
        } else {
            forwardAxis = new Vec3(0.0D, 0.0D, 1.0D);
        }

        // Perpendicular front axis (across the line of battle)
        Vec3 rightAxis = new Vec3(-forwardAxis.z, 0.0D, forwardAxis.x);

        // Measure front width of the group projected onto rightAxis
        double minProj = Double.POSITIVE_INFINITY;
        double maxProj = Double.NEGATIVE_INFINITY;
        for (AbstractRecruitEntity r : groupRecruits) {
            double dx = r.getX() - centroid.x;
            double dz = r.getZ() - centroid.z;
            double proj = dx * rightAxis.x + dz * rightAxis.z;
            if (proj < minProj) minProj = proj;
            if (proj > maxProj) maxProj = proj;
        }
        double frontWidth = (maxProj > minProj) ? Mth.clamp(maxProj - minProj, 0.0D, 64.0D) : 0.0D;
        double ellipseWidth = frontWidth + NapoleonicServerConfig.VOLLEY_EXTRA_WIDTH.get();
        double ellipseDepth = NapoleonicServerConfig.VOLLEY_DEPTH.get();
        double semiMajorA = Math.max(2.0D, ellipseWidth * 0.5D);
        double semiMinorB = Math.max(0.5D, ellipseDepth * 0.5D);

        int minDelay = NapoleonicServerConfig.VOLLEY_MIN_DELAY_TICKS.get();
        int maxDelay = Math.max(minDelay, NapoleonicServerConfig.VOLLEY_MAX_DELAY_TICKS.get());
        // A configured window of only a few ticks still sounds like one shot. Keep at least ~0.6s of ripple.
        int delaySpan = Math.max(12, maxDelay - minDelay);
        int maxPerTick = Math.max(1, NapoleonicServerConfig.VOLLEY_MAX_CALCULATIONS_PER_TICK.get());

        // Track how many shots are scheduled per delay tick so 50+ recruits never spike a single tick
        int[] perTickCounts = new int[minDelay + delaySpan + 32];
        int readyShooters = 0;

        for (AbstractRecruitEntity recruit : groupRecruits) {
            IRecruitLineData data = (IRecruitLineData) recruit;
            ensureMusketInMainHand(recruit);
            data.napoleonic$setMode(LineInfantryMode.VOLLEY);
            recruit.setTarget(null);

            if (!isMusketLoaded(recruit)) {
                // Unloaded recruits do not fire in this volley; they immediately reload and wait
                data.napoleonic$setVolleyPending(false);
                data.napoleonic$setFiredVolley(true);
                if (!data.napoleonic$isReloading() && hasOrConsumeCartridge(recruit, true)) {
                    data.napoleonic$setReloadTotalTicks(rollRecruitReloadTicks(recruit.getRandom()));
                    data.napoleonic$setReloadProgressTicks(0);
                    data.napoleonic$setReloading(true);
                }
                data.napoleonic$syncToTrackingClients();
                continue;
            }

            RandomSource rng = recruit.getRandom();
            // Parallel to the crosshair: the block under the sight, shifted by this recruit's place in the rank.
            Vec3 laneCenter = new Vec3(
                    centerPoint.x + (recruit.getX() - centroid.x),
                    centerPoint.y,
                    centerPoint.z + (recruit.getZ() - centroid.z));
            Vec3 individualPoint = sampleEllipticalVolleyTarget(
                    rng, laneCenter, forwardAxis, rightAxis, Math.min(semiMajorA, 2.5D), Math.min(semiMinorB, 1.5D));

            int baseDelay = minDelay + rng.nextInt(delaySpan + 1);
            while (baseDelay < perTickCounts.length - 1 && perTickCounts[baseDelay] >= maxPerTick) {
                baseDelay++;
            }
            perTickCounts[baseDelay]++;

            data.napoleonic$setVolleyAimPoint(individualPoint);
            data.napoleonic$setVolleyDelayTicks(baseDelay);
            faceToward(recruit, individualPoint);
            data.napoleonic$setVolleyPending(true);
            data.napoleonic$setFiredVolley(false);
            data.napoleonic$setReloading(false);
            data.napoleonic$syncToTrackingClients();
            readyShooters++;
        }

        return readyShooters;
    }

    /**
     * Samples an individual target point inside the ellipse using a 2D truncated Gaussian distribution
     * (center denser, edges sparser, guaranteed spread across the ellipse width and depth).
     */
    public static Vec3 sampleEllipticalVolleyTarget(RandomSource rng, Vec3 center,
                                                    Vec3 forwardAxis, Vec3 rightAxis,
                                                    double semiMajorA, double semiMinorB) {
        // Sample u (along rightAxis / width) and v (along forwardAxis / depth) from N(0, 0.48^2)
        double u;
        double v;
        int attempts = 0;
        do {
            u = rng.nextGaussian() * 0.48D;
            v = rng.nextGaussian() * 0.48D;
            attempts++;
        } while (u * u + v * v > 1.0D && attempts < 6);

        double normSq = u * u + v * v;
        if (normSq > 1.0D) {
            double inv = 1.0D / Math.sqrt(normSq);
            u *= inv * (0.75D + rng.nextDouble() * 0.24D);
            v *= inv * (0.75D + rng.nextDouble() * 0.24D);
        }

        double offsetRight = u * semiMajorA;
        double offsetForward = v * semiMinorB;
        // Aim ~0.75 blocks above the ground impact point (chest/torso height for entities standing in the zone)
        double targetX = center.x + rightAxis.x * offsetRight + forwardAxis.x * offsetForward;
        double targetZ = center.z + rightAxis.z * offsetRight + forwardAxis.z * offsetForward;
        double targetY = center.y + 0.65D + (rng.nextDouble() - 0.5D) * 0.35D;
        return new Vec3(targetX, targetY, targetZ);
    }

    /**
     * Throttled, allocation-light target finder for FIRE_AT_WILL mode.
     * Shares a single spatial scan per server tick when recruits are near each other.
     */
    @Nullable
    public static LivingEntity findFireAtWillTarget(AbstractRecruitEntity recruit) {
        if (!(recruit.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        double maxRange = NapoleonicServerConfig.MAX_FIRING_RANGE.get();
        double maxRangeSqr = maxRange * maxRange;

        LivingEntity current = recruit.getTarget();
        if (current != null && current.isAlive() && !current.isRemoved()
                && recruit.distanceToSqr(current) <= maxRangeSqr
                && isValidFireAtWillTarget(recruit, current)
                && recruit.getSensing().hasLineOfSight(current)) {
            return current;
        }

        long gameTime = serverLevel.getGameTime();
        Vec3 pos = recruit.position();
        if (cachedTargetScanTick != gameTime
                || cachedTargetScanLevel != serverLevel
                || cachedTargetScanCenter.distanceToSqr(pos) > 256.0D) {
            cachedTargetScanTick = gameTime;
            cachedTargetScanLevel = serverLevel;
            cachedTargetScanCenter = pos;
            AABB scanBox = recruit.getBoundingBox().inflate(Math.min(64.0D, maxRange), 24.0D, Math.min(64.0D, maxRange));
            cachedTargetCandidates = serverLevel.getEntitiesOfClass(
                    LivingEntity.class,
                    scanBox,
                    e -> e.isAlive() && !e.isRemoved() && !e.isSpectator()
            );
        }

        LivingEntity bestTarget = null;
        double bestDistSqr = maxRangeSqr;
        for (int i = 0, size = cachedTargetCandidates.size(); i < size; i++) {
            LivingEntity candidate = cachedTargetCandidates.get(i);
            if (candidate == recruit || !candidate.isAlive()) continue;
            double dSqr = recruit.distanceToSqr(candidate);
            if (dSqr >= bestDistSqr) continue;
            if (!isValidFireAtWillTarget(recruit, candidate)) continue;
            if (!recruit.getSensing().hasLineOfSight(candidate)) continue;
            bestDistSqr = dSqr;
            bestTarget = candidate;
        }

        if (bestTarget != null) {
            recruit.setTarget(bestTarget);
        }
        return bestTarget;
    }

    public static boolean isValidFireAtWillTarget(AbstractRecruitEntity recruit, LivingEntity target) {
        if (target == recruit || !target.isAlive() || target.isRemoved()) return false;
        if (target instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (isFriendlyOrProtected(recruit, target)) return false;

        // If recruit is set to Passive (3), Fire at Will still attacks valid Neutral/Hostile targets per Requirement 7
        if (recruit.getState() == 3) {
            if (target instanceof Enemy) {
                return recruit.canAttack(target);
            }
            if (target.getLastHurtByMob() != null
                    && (target.getLastHurtByMob() == recruit || target.getLastHurtByMob() == recruit.getOwner())) {
                return recruit.canAttack(target);
            }
            return RecruitEvents.isEnemy(recruit.getTeam(), target.getTeam()) && recruit.canAttack(target);
        }
        return recruit.shouldAttack(target);
    }

    private LineInfantryHelper() {
    }
}
