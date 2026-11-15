package com.napoleonic.line.client;

import com.napoleonic.line.compat.MusketModCompat;
import com.napoleonic.line.entity.LineInfantryMode;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import ewewukek.musketmod.ClientUtilities;
import ewewukek.musketmod.GunItem;
import ewewukek.musketmod.Items;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MusketClientHooks {
    private MusketClientHooks() {
    }

    public static void registerItemProperties() {
        if (!MusketModCompat.isLoaded()) {
            return;
        }
        ResourceLocation loadingLoc = new ResourceLocation("musketmod", "loading");
        ResourceLocation aimingLoc = new ResourceLocation("musketmod", "aiming");

        ClampedItemPropertyFunction loadingProp = (stack, level, entity, seed) -> {
            if (entity instanceof AbstractRecruitEntity recruit) {
                LineInfantryMode mode = ClientLineStateCache.getMode(recruit);
                if (recruit.getOffhandItem() == stack && mode == LineInfantryMode.SHOULDER_ARMS) {
                    return 0.0F;
                }
                if (recruit.getMainHandItem() == stack && ClientLineStateCache.isReloading(recruit)) {
                    return 1.0F;
                }
            }
            return entity != null && entity.isUsingItem()
                    && entity.getItemInHand(entity.getUsedItemHand()) == stack
                    && !GunItem.isLoaded(stack) ? 1.0F : 0.0F;
        };

        ClampedItemPropertyFunction aimingProp = (stack, level, entity, seed) -> {
            if (entity instanceof AbstractRecruitEntity recruit) {
                LineInfantryMode mode = ClientLineStateCache.getMode(recruit);
                if (recruit.getOffhandItem() == stack && mode == LineInfantryMode.SHOULDER_ARMS) {
                    return 0.0F;
                }
                if (recruit.getMainHandItem() == stack) {
                    if (ClientLineStateCache.isReloading(recruit)) {
                        return 0.0F;
                    }
                    if (mode == LineInfantryMode.PRESENT_ARMS
                            || mode == LineInfantryMode.VOLLEY
                            || mode == LineInfantryMode.FIRE_AT_WILL
                            || (mode == LineInfantryMode.RELOAD && GunItem.isLoaded(stack))) {
                        return 1.0F;
                    }
                }
            }
            return ClientUtilities.shouldAim(entity, stack) ? 1.0F : 0.0F;
        };

        Item[] guns = new Item[]{
                Items.MUSKET,
                Items.MUSKET_WITH_BAYONET,
                Items.MUSKET_WITH_SCOPE,
                Items.BLUNDERBUSS,
                Items.PISTOL
        };
        for (Item gun : guns) {
            if (gun != null) {
                ItemProperties.register(gun, loadingLoc, loadingProp);
                ItemProperties.register(gun, aimingLoc, aimingProp);
            }
        }
    }
}
