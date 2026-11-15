package com.napoleonic.line.compat;

import ewewukek.musketmod.BulletEntity;
import ewewukek.musketmod.GunItem;
import ewewukek.musketmod.Items;
import ewewukek.musketmod.Sounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

/**
 * Every reference to ewewukek's Musket Mod lives here.
 * Callers check {@link #isLoaded()} first so this class is never entered when the mod is absent.
 */
public final class MusketModCompat {
    private MusketModCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("musketmod");
    }

    public static boolean isGun(ItemStack stack) {
        if (!isLoaded() || stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof GunItem;
    }

    public static boolean isStackLoaded(ItemStack stack) {
        return isGun(stack) && GunItem.isLoaded(stack);
    }

    public static void setLoaded(ItemStack stack, boolean loaded) {
        if (!isGun(stack)) {
            return;
        }
        GunItem.setLoaded(stack, loaded);
        if (!loaded) {
            GunItem.setLoadingStage(stack, 0);
        }
    }

    public static void setLoadingStage(ItemStack stack, int stage) {
        if (!isGun(stack)) {
            return;
        }
        GunItem.setLoadingStage(stack, stage);
    }

    public static ItemStack icon() {
        if (!isLoaded()) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(Items.MUSKET_WITH_BAYONET);
    }

    public static SoundEvent musketFireSound() {
        if (!isLoaded()) {
            return null;
        }
        return Sounds.MUSKET_FIRE;
    }

    public static SoundEvent loadSound(int stage) {
        if (!isLoaded()) {
            return null;
        }
        return switch (stage) {
            case 0 -> Sounds.MUSKET_LOAD_0;
            case 1 -> Sounds.MUSKET_LOAD_1;
            case 2 -> Sounds.MUSKET_LOAD_2;
            default -> Sounds.MUSKET_READY;
        };
    }

    public static double gravity() {
        if (!isLoaded()) {
            return 0.05D;
        }
        return BulletEntity.GRAVITY;
    }

    public static GunStats stats(ItemStack stack) {
        if (!isGun(stack)) {
            return null;
        }
        GunItem gun = (GunItem) stack.getItem();
        GunStats stats = new GunStats();
        stats.speed = gun.bulletSpeed();
        stats.stdDev = gun.bulletStdDev();
        stats.dropReduction = gun.bulletDropReduction();
        stats.pellets = gun.pelletCount();
        stats.fireSound = gun.fireSound(stack);
        stats.flame = GunItem.hasFlame(stack);
        return stats;
    }

    public static Vec3 addSpread(Vec3 direction, RandomSource random, float spreadStdDev) {
        if (isLoaded()) {
            return GunItem.addSpread(direction, random, spreadStdDev);
        }
        float gaussian = Math.abs((float) random.nextGaussian());
        if (gaussian > 4.0F) {
            gaussian = 4.0F;
        }
        float error = (float) Math.toRadians(spreadStdDev) * gaussian;
        Vec3 n1;
        Vec3 n2;
        if (Math.abs(direction.x) < 1.0E-5D && Math.abs(direction.z) < 1.0E-5D) {
            n1 = new Vec3(1, 0, 0);
            n2 = new Vec3(0, 0, 1);
        } else {
            n1 = new Vec3(-direction.z, 0, direction.x).normalize();
            n2 = direction.cross(n1);
        }
        float angle = random.nextFloat() * ((float) Math.PI * 2.0F);
        return direction.scale(Math.cos(error))
                .add(n1.scale(Math.sin(error) * Math.cos(angle)))
                .add(n2.scale(Math.sin(error) * Math.sin(angle)))
                .normalize();
    }

    public static void spawnBullets(ServerLevel level, LivingEntity shooter, Vec3 muzzlePos, Vec3 primaryDir,
                                     Vec3 compensatedDir, float bulletSpeed, float stdDev, float damage,
                                     float dropReduction, int pellets, boolean flame, RandomSource random) {
        if (!isLoaded()) {
            return;
        }
        for (int i = 0; i < pellets; i++) {
            Vec3 pelletDir = (i == 0) ? primaryDir : GunItem.addSpread(compensatedDir, random, stdDev);
            BulletEntity bullet = new BulletEntity(level);
            bullet.setOwner(shooter);
            bullet.setPos(muzzlePos);
            bullet.setVelocity(bulletSpeed, pelletDir);
            bullet.damage = damage;
            bullet.setDropReduction(dropReduction);
            bullet.setPelletCount(pellets);
            if (flame) {
                bullet.setSecondsOnFire(100);
                bullet.setSharedFlagOnFire(true);
            }
            level.addFreshEntity(bullet);
        }
    }

    public static final class GunStats {
        public float speed;
        public float stdDev;
        public float dropReduction;
        public int pellets;
        public SoundEvent fireSound;
        public boolean flame;
    }
}
