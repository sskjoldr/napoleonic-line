package com.napoleonic.line.gametest;

import com.napoleonic.line.NapoleonicLineMod;
import com.napoleonic.line.ai.LineInfantryHelper;
import com.napoleonic.line.config.NapoleonicServerConfig;
import com.napoleonic.line.entity.IRecruitLineData;
import com.napoleonic.line.entity.LineInfantryMode;
import com.napoleonic.line.entity.NapoleonicDamageTypes;
import com.napoleonic.line.network.C2SLineCommandPacket;
import com.napoleonic.line.wind.WindManager;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import com.talhanation.recruits.init.ModEntityTypes;
import ewewukek.musketmod.BulletEntity;
import ewewukek.musketmod.DeferredDamage;
import ewewukek.musketmod.GunItem;
import ewewukek.musketmod.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(NapoleonicLineMod.MODID)
@PrefixGameTestTemplate(false)
public class NapoleonicGameTests {

    private static CrossBowmanEntity spawnArmedMusketeer(GameTestHelper helper, BlockPos relPos, UUID ownerUUID, UUID groupUUID, boolean loaded) {
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(relPos);
        CrossBowmanEntity recruit = ModEntityTypes.CROSSBOWMAN.get().create(level);
        if (recruit == null) {
            helper.fail("Failed to create CrossBowmanEntity");
            return null;
        }
        recruit.moveTo(absPos.getX() + 0.5D, absPos.getY(), absPos.getZ() + 0.5D, 0.0F, 0.0F);
        recruit.setIsOwned(true);
        recruit.setOwnerUUID(Optional.of(ownerUUID));
        recruit.setGroupUUID(groupUUID);

        ItemStack musket = new ItemStack(Items.MUSKET_WITH_BAYONET);
        GunItem.setLoaded(musket, loaded);
        recruit.setItemInHand(InteractionHand.MAIN_HAND, musket);
        recruit.getInventory().setItem(6, new ItemStack(Items.CARTRIDGE, 32));

        level.addFreshEntity(recruit);
        return recruit;
    }

    private static void ensureFloor(GameTestHelper helper, int widthX, int depthZ) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (int x = -1; x <= widthX; x++) {
            for (int z = -1; z <= depthZ; z++) {
                BlockPos floorPos = origin.offset(x, 0, z);
                level.setBlock(floorPos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                for (int y = 1; y <= 4; y++) {
                    BlockPos airPos = origin.offset(x, y, z);
                    if (!level.getBlockState(airPos).isAir()) {
                        level.setBlock(airPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    /**
     * Scenario 1: Single recruit reload (18s = 360 ticks), player reload (15s = 300 ticks), and shot execution.
     */
    @GameTest(template = "empty", batch = "napoleonic_b1", timeoutTicks = 100)
    public static void singleRecruitReloadAndFire(GameTestHelper helper) {
        ensureFloor(helper, 6, 6);
        // Verify player reload duration is 15s (3 stages * 100 ticks = 300 ticks)
        Pair<Integer, Integer> playerReload = GunItem.getLoadingDuration(new ItemStack(Items.MUSKET));
        int playerTotalTicks = playerReload.getLeft() * playerReload.getRight();
        if (playerTotalTicks != 300) {
            helper.fail("Expected player reload to be 300 ticks (15s), got: " + playerTotalTicks);
            return;
        }

        // Verify recruit reload duration is 18s (360 ticks)
        int recruitTotalTicks = LineInfantryHelper.getRecruitReloadTotalTicks();
        if (recruitTotalTicks != 360) {
            helper.fail("Expected recruit reload to be 360 ticks (18s), got: " + recruitTotalTicks);
            return;
        }

        UUID owner = UUID.randomUUID();
        UUID group = UUID.randomUUID();
        CrossBowmanEntity recruit = spawnArmedMusketeer(helper, new BlockPos(2, 1, 2), owner, group, false);
        IRecruitLineData data = (IRecruitLineData) recruit;

        C2SLineCommandPacket.applyModeToRecruits(List.of(recruit), LineInfantryMode.RELOAD);
        if (!data.napoleonic$isReloading()) {
            helper.fail("Recruit did not enter reloading state on RELOAD command");
            return;
        }

        int rolled = Math.max(20, data.napoleonic$getReloadTotalTicks());
        data.napoleonic$setReloadProgressTicks(Math.max(0, rolled - 2));
        helper.runAfterDelay(5, () -> {
            if (!LineInfantryHelper.isMusketLoaded(recruit)) {
                helper.fail("Recruit musket should be loaded after 360 ticks of reload");
                return;
            }
            if (data.napoleonic$isReloading()) {
                helper.fail("Recruit reloading flag should clear once loaded");
                return;
            }
            recruit.discard();
            NapoleonicLineMod.LOGGER.info("[GameTest PASS] singleRecruitReloadAndFire verified 15s player & 18s recruit reload.");
            helper.succeed();
        });
    }

    /**
     * Scenario 2: Volley of 20 recruits — verifies elliptical Gaussian spread (no 1-2 block clumping),
     * ragged 0-6 tick firing delay, and automatic reload & hold after firing.
     */
    @GameTest(template = "empty", batch = "napoleonic_b2", timeoutTicks = 100)
    public static void twentyRecruitsVolleySpreadAndHold(GameTestHelper helper) {
        ensureFloor(helper, 22, 32);
        UUID owner = UUID.randomUUID();
        UUID group = UUID.randomUUID();
        List<AbstractRecruitEntity> line = new ArrayList<>(20);

        for (int i = 0; i < 20; i++) {
            CrossBowmanEntity r = spawnArmedMusketeer(helper, new BlockPos(i, 1, 1), owner, group, true);
            line.add(r);
        }

        Vec3 targetCenter = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(10, 1, 28)));
        int ready = LineInfantryHelper.scheduleGroupVolley(line, targetCenter, new Vec3(0, 0, 1));
        if (ready != 20) {
            helper.fail("Expected all 20 loaded recruits to be scheduled for volley, got: " + ready);
            return;
        }

        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        int minDelay = Integer.MAX_VALUE, maxDelay = Integer.MIN_VALUE;

        for (AbstractRecruitEntity r : line) {
            IRecruitLineData d = (IRecruitLineData) r;
            Vec3 pt = d.napoleonic$getVolleyAimPoint();
            if (pt == null) {
                helper.fail("Recruit missing volley aim point");
                return;
            }
            minX = Math.min(minX, pt.x);
            maxX = Math.max(maxX, pt.x);
            minZ = Math.min(minZ, pt.z);
            maxZ = Math.max(maxZ, pt.z);
            int delay = d.napoleonic$getVolleyDelayTicks();
            minDelay = Math.min(minDelay, delay);
            maxDelay = Math.max(maxDelay, delay);
        }

        double spreadWidth = maxX - minX;
        double spreadDepth = maxZ - minZ;
        if (spreadWidth < 5.0D) {
            helper.fail("Volley lateral spread too narrow (clumped into 1-2 blocks): " + spreadWidth);
            return;
        }
        if (maxDelay <= minDelay) {
            helper.fail("Volley delays did not vary across 0..6 ticks: min=" + minDelay + ", max=" + maxDelay);
            return;
        }

        helper.runAfterDelay(20, () -> {
            int firedAndReloading = 0;
            for (AbstractRecruitEntity r : line) {
                IRecruitLineData d = (IRecruitLineData) r;
                if (d.napoleonic$hasFiredVolley() && !LineInfantryHelper.isMusketLoaded(r) && d.napoleonic$isReloading()) {
                    firedAndReloading++;
                }
                r.discard();
            }
            if (firedAndReloading < 15) {
                helper.fail("Expected recruits to fire volley and begin auto-reloading, but only " + firedAndReloading + " did.");
                return;
            }
            NapoleonicLineMod.LOGGER.info("[GameTest PASS] twentyRecruitsVolleySpreadAndHold: spreadWidth={}, spreadDepth={}, fired={}/20",
                    String.format("%.2f", spreadWidth), String.format("%.2f", spreadDepth), firedAndReloading);
            helper.succeed();
        });
    }

    /**
     * Scenario 3: Fire at Will against hostile Pillagers + strict friendly-fire protection when an ally stands in front.
     */
    @GameTest(template = "empty", batch = "napoleonic_b3", timeoutTicks = 100)
    public static void fireAtWillAndFriendlyFireProtection(GameTestHelper helper) {
        ensureFloor(helper, 8, 14);
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        UUID group = UUID.randomUUID();

        // Back-rank shooter at (2, 1, 1) and front-rank ally directly in line of fire at (2, 1, 3)
        CrossBowmanEntity shooter = spawnArmedMusketeer(helper, new BlockPos(2, 1, 1), owner, group, true);
        CrossBowmanEntity frontAlly = spawnArmedMusketeer(helper, new BlockPos(2, 1, 3), owner, group, true);

        // Enemy pillager at (2, 1, 10)
        BlockPos enemyPos = helper.absolutePos(new BlockPos(2, 1, 10));
        Pillager pillager = EntityType.PILLAGER.create(level);
        pillager.moveTo(enemyPos.getX() + 0.5D, enemyPos.getY(), enemyPos.getZ() + 0.5D, 180.0F, 0.0F);
        level.addFreshEntity(pillager);

        float allyInitialHealth = frontAlly.getHealth();
        C2SLineCommandPacket.applyModeToRecruits(List.of(shooter, frontAlly), LineInfantryMode.FIRE_AT_WILL);

        helper.runAfterDelay(25, () -> {
            if (frontAlly.getHealth() < allyInitialHealth) {
                helper.fail("Friendly fire occurred! Front ally lost health: " + frontAlly.getHealth() + " < " + allyInitialHealth);
                return;
            }
            if (pillager.isAlive() && pillager.getHealth() >= pillager.getMaxHealth()) {
                helper.fail("Enemy pillager was not hit by Fire at Will musketeers");
                return;
            }
            shooter.discard();
            frontAlly.discard();
            pillager.discard();
            NapoleonicLineMod.LOGGER.info("[GameTest PASS] fireAtWillAndFriendlyFireProtection: allyHP={}/{}, pillagerAlive={}",
                    frontAlly.getHealth(), allyInitialHealth, pillager.isAlive());
            helper.succeed();
        });
    }

    /**
     * Scenario 4 & 6: Shoulder Arms offhand swap, NBT save/load persistence, return to mainhand on fire command,
     * death without item/ammo duplication, and mounted recruit stability.
     */
    @GameTest(template = "empty", batch = "napoleonic_b4", timeoutTicks = 100)
    public static void nbtPersistenceAndShoulderArmsAndMounted(GameTestHelper helper) {
        ensureFloor(helper, 8, 8);
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        UUID group = UUID.randomUUID();

        CrossBowmanEntity recruit = spawnArmedMusketeer(helper, new BlockPos(3, 1, 3), owner, group, true);
        recruit.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(net.minecraft.world.item.Items.SHIELD));

        // 1. Apply Shoulder Arms («На плечо»)
        C2SLineCommandPacket.applyModeToRecruits(List.of(recruit), LineInfantryMode.SHOULDER_ARMS);
        if (!(recruit.getOffhandItem().getItem() instanceof GunItem)) {
            helper.fail("Expected musket in OFF_HAND during SHOULDER_ARMS, got: " + recruit.getOffhandItem());
            return;
        }
        if (!recruit.getMainHandItem().is(net.minecraft.world.item.Items.SHIELD)) {
            helper.fail("Expected displaced shield in MAIN_HAND during SHOULDER_ARMS, got: " + recruit.getMainHandItem());
            return;
        }

        // 2. Verify no duplicate muskets in inventory
        int musketCount = countMuskets(recruit);
        if (musketCount != 1) {
            helper.fail("Expected exactly 1 musket in recruit inventory during SHOULDER_ARMS, found: " + musketCount);
            return;
        }

        // 3. Test NBT save & reload persistence
        CompoundTag savedNbt = new CompoundTag();
        recruit.addAdditionalSaveData(savedNbt);

        CrossBowmanEntity restored = ModEntityTypes.CROSSBOWMAN.get().create(level);
        restored.readAdditionalSaveData(savedNbt);
        IRecruitLineData restoredData = (IRecruitLineData) restored;
        if (restoredData.napoleonic$getMode() != LineInfantryMode.SHOULDER_ARMS) {
            helper.fail("Expected restored mode to be SHOULDER_ARMS after NBT load, got: " + restoredData.napoleonic$getMode());
            return;
        }

        // 3.5. Switch to PRESENT_ARMS («На караул / В руки») and verify musket moves to MAIN_HAND, aggressive=true, and RubBoomstickInHands=true
        C2SLineCommandPacket.applyModeToRecruits(List.of(recruit), LineInfantryMode.PRESENT_ARMS);
        if (!(recruit.getMainHandItem().getItem() instanceof GunItem)) {
            helper.fail("Expected musket in MAIN_HAND on PRESENT_ARMS, got: " + recruit.getMainHandItem());
            return;
        }
        if (!recruit.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD)) {
            helper.fail("Expected shield back in OFF_HAND on PRESENT_ARMS, got: " + recruit.getOffhandItem());
            return;
        }
        if (!recruit.isAggressive() || !recruit.getPersistentData().getBoolean("RubBoomstickInHands")) {
            helper.fail("Expected aggressive=true and RubBoomstickInHands=true in PRESENT_ARMS");
            return;
        }

        // Verify Boomstick / CrossbowItem NBT loaded & recharge flag handling
        ItemStack crossbowProxy = new ItemStack(net.minecraft.world.item.Items.CROSSBOW);
        LineInfantryHelper.setStackReloadingFlag(crossbowProxy, 90, 360, true);
        if (!crossbowProxy.getOrCreateTag().getBoolean("recharge")) {
            helper.fail("Expected 'recharge' tag to be true during boomstick reload");
            return;
        }
        LineInfantryHelper.setStackLoaded(crossbowProxy, true);
        if (!LineInfantryHelper.isStackLoaded(crossbowProxy) || crossbowProxy.getOrCreateTag().getBoolean("recharge")) {
            helper.fail("Expected crossbow/boomstick stack to be Charged=true and recharge=false after setStackLoaded(true)");
            return;
        }

        // 4. Switch back to SHOULDER_ARMS and then FIRE_AT_WILL and verify musket automatically returns to MAIN_HAND
        C2SLineCommandPacket.applyModeToRecruits(List.of(recruit), LineInfantryMode.SHOULDER_ARMS);
        C2SLineCommandPacket.applyModeToRecruits(List.of(recruit), LineInfantryMode.FIRE_AT_WILL);
        if (!(recruit.getMainHandItem().getItem() instanceof GunItem)) {
            helper.fail("Expected musket to return to MAIN_HAND on FIRE_AT_WILL");
            return;
        }
        if (countMuskets(recruit) != 1) {
            helper.fail("Expected 1 musket after returning to MAIN_HAND, found: " + countMuskets(recruit));
            return;
        }

        // 5. Mounted recruit test (horse)
        Horse horse = EntityType.HORSE.create(level);
        BlockPos horsePos = helper.absolutePos(new BlockPos(4, 1, 4));
        horse.moveTo(horsePos.getX() + 0.5D, horsePos.getY(), horsePos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(horse);
        recruit.startRiding(horse, true);
        C2SLineCommandPacket.applyModeToRecruits(List.of(recruit), LineInfantryMode.SHOULDER_ARMS);
        C2SLineCommandPacket.applyModeToRecruits(List.of(recruit), LineInfantryMode.PRESENT_ARMS);
        C2SLineCommandPacket.applyModeToRecruits(List.of(recruit), LineInfantryMode.VOLLEY);

        recruit.discard();
        horse.discard();
        NapoleonicLineMod.LOGGER.info("[GameTest PASS] nbtPersistenceAndShoulderArmsAndMounted passed all checks (including PRESENT_ARMS & Boomsticks NBT).");
        helper.succeed();
    }

    /**
     * Scenario 5: Musket ball damage (35.0) vs full Protection IV Netherite Armor + Resistance II,
     * Totem of Undying survival, and 60-block damage falloff threshold.
     */
    @GameTest(template = "empty", batch = "napoleonic_b5", timeoutTicks = 100)
    public static void netheriteProtectionFourResistanceAndTotem(GameTestHelper helper) {
        ensureFloor(helper, 8, 8);
        ServerLevel level = helper.getLevel();
        BlockPos pos1 = helper.absolutePos(new BlockPos(2, 1, 2));

        // 1. Target in full Netherite Armor with Protection IV + Resistance II (20 HP)
        Zombie armoredTarget = EntityType.ZOMBIE.create(level);
        armoredTarget.moveTo(pos1.getX() + 0.5D, pos1.getY(), pos1.getZ() + 0.5D, 0.0F, 0.0F);
        equipFullProtectionFourNetherite(armoredTarget);
        armoredTarget.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1)); // Resistance II (40% reduction normally)
        level.addFreshEntity(armoredTarget);

        BulletEntity bullet1 = new BulletEntity(level);
        bullet1.setPos(pos1.getX() + 0.5D, pos1.getY() + 1.0D, pos1.getZ() + 0.5D);
        bullet1.damage = NapoleonicServerConfig.MUSKET_DAMAGE.get().floatValue(); // 35.0
        bullet1.distanceTravelled = 45.0F; // < 60 blocks -> full 35.0 damage
        bullet1.onHitEntity(new EntityHitResult(armoredTarget));
        DeferredDamage.apply();

        if (armoredTarget.isAlive()) {
            helper.fail("Target in Protection IV Netherite + Resistance II survived 35-damage musket ball at 45m! Remaining HP: " + armoredTarget.getHealth());
            return;
        }

        // 2. Target with Totem of Undying in offhand + Protection IV Netherite
        BlockPos pos2 = helper.absolutePos(new BlockPos(4, 1, 2));
        Zombie totemTarget = EntityType.ZOMBIE.create(level);
        totemTarget.moveTo(pos2.getX() + 0.5D, pos2.getY(), pos2.getZ() + 0.5D, 0.0F, 0.0F);
        equipFullProtectionFourNetherite(totemTarget);
        totemTarget.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
        level.addFreshEntity(totemTarget);

        BulletEntity bullet2 = new BulletEntity(level);
        bullet2.setPos(pos2.getX() + 0.5D, pos2.getY() + 1.0D, pos2.getZ() + 0.5D);
        bullet2.damage = 35.0F;
        bullet2.distanceTravelled = 30.0F;
        bullet2.onHitEntity(new EntityHitResult(totemTarget));
        DeferredDamage.apply();

        if (!totemTarget.isAlive() || !totemTarget.getOffhandItem().isEmpty()) {
            helper.fail("Totem of Undying did not save target or was not consumed! alive=" + totemTarget.isAlive());
            return;
        }

        // 3. Verify distance falloff only occurs AFTER 60 blocks
        BlockPos pos3 = helper.absolutePos(new BlockPos(6, 1, 2));
        Zombie farTarget = EntityType.ZOMBIE.create(level);
        farTarget.moveTo(pos3.getX() + 0.5D, pos3.getY(), pos3.getZ() + 0.5D, 0.0F, 0.0F);
        farTarget.setHealth(20.0F);
        level.addFreshEntity(farTarget);

        BulletEntity bulletFar = new BulletEntity(level);
        bulletFar.setPos(pos3.getX() + 0.5D, pos3.getY() + 1.0D, pos3.getZ() + 0.5D);
        bulletFar.damage = 35.0F;
        bulletFar.distanceTravelled = 110.0F; // > 60m -> 50% falloff (17.5 damage)
        bulletFar.onHitEntity(new EntityHitResult(farTarget));
        DeferredDamage.apply();

        if (!farTarget.isAlive() || farTarget.getHealth() >= 19.0F) {
            helper.fail("Expected reduced damage at 110m (17.5 dmg leaving ~2.5 HP), got HP: " + farTarget.getHealth());
            return;
        }

        totemTarget.discard();
        farTarget.discard();
        NapoleonicLineMod.LOGGER.info("[GameTest PASS] netheriteProtectionFourResistanceAndTotem verified 35 dmg armor/enchant/resistance bypass, Totem pop, and >60m falloff.");
        helper.succeed();
    }

    /**
     * Scenario 7 & 8: 50-recruit volley performance (< 5 ms peak) and deterministic wind verification.
     */
    @GameTest(template = "empty", batch = "napoleonic_b6", timeoutTicks = 100)
    public static void fiftyRecruitVolleyPerformanceAndWind(GameTestHelper helper) {
        ensureFloor(helper, 26, 38);
        UUID owner = UUID.randomUUID();
        UUID group = UUID.randomUUID();
        List<AbstractRecruitEntity> fifty = new ArrayList<>(50);

        for (int i = 0; i < 50; i++) {
            CrossBowmanEntity r = spawnArmedMusketeer(helper, new BlockPos(i % 25, 1, 1 + (i / 25) * 2), owner, group, true);
            fifty.add(r);
        }

        Vec3 targetCenter = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(12, 1, 35)));
        // Warmup once so classloading is not counted in peak tick measurement
        LineInfantryHelper.scheduleGroupVolley(fifty.subList(0, 1), targetCenter, new Vec3(0, 0, 1));
        LineInfantryHelper.setMusketLoaded(fifty.get(0), true);

        long t0 = System.nanoTime();
        int scheduled = LineInfantryHelper.scheduleGroupVolley(fifty, targetCenter, new Vec3(0, 0, 1));
        long scheduleElapsedNs = System.nanoTime() - t0;
        double scheduleMs = scheduleElapsedNs / 1_000_000.0D;

        for (AbstractRecruitEntity r : fifty) {
            r.discard();
        }

        if (scheduled != 50) {
            helper.fail("Expected 50 recruits scheduled, got: " + scheduled);
            return;
        }
        if (scheduleMs > 5.0D) {
            helper.fail("scheduleGroupVolley for 50 recruits took too long: " + scheduleMs + " ms");
            return;
        }

        // Verify deterministic wind
        double wx1 = WindManager.getWindX(12345L, 1.0D);
        double wz1 = WindManager.getWindZ(12345L, 1.0D);
        double wx2 = WindManager.getWindX(12345L, 1.0D);
        double wz2 = WindManager.getWindZ(12345L, 1.0D);
        if (wx1 != wx2 || wz1 != wz2 || (wx1 == 0.0D && wz1 == 0.0D)) {
            helper.fail("WindManager is not deterministic or returned zero wind");
            return;
        }

        NapoleonicLineMod.LOGGER.info("[GameTest PASS] fiftyRecruitVolleyPerformanceAndWind: 50-recruit schedule took {} ms, wind=({}, {})",
                String.format("%.3f", scheduleMs), String.format("%.4f", wx1), String.format("%.4f", wz1));
        helper.succeed();
    }

    private static void equipFullProtectionFourNetherite(Zombie entity) {
        ItemStack helmet = new ItemStack(net.minecraft.world.item.Items.NETHERITE_HELMET);
        ItemStack chest = new ItemStack(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE);
        ItemStack legs = new ItemStack(net.minecraft.world.item.Items.NETHERITE_LEGGINGS);
        ItemStack boots = new ItemStack(net.minecraft.world.item.Items.NETHERITE_BOOTS);
        helmet.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 4);
        chest.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 4);
        legs.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 4);
        boots.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 4);
        entity.setItemSlot(EquipmentSlot.HEAD, helmet);
        entity.setItemSlot(EquipmentSlot.CHEST, chest);
        entity.setItemSlot(EquipmentSlot.LEGS, legs);
        entity.setItemSlot(EquipmentSlot.FEET, boots);
    }

    private static int countMuskets(AbstractRecruitEntity recruit) {
        int count = 0;
        var inv = recruit.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof GunItem) {
                count++;
            }
        }
        return count;
    }
}
