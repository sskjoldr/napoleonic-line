package com.napoleonic.line.mixin;

import com.napoleonic.line.ai.LineInfantryHelper;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrossBowmanEntity.class)
public abstract class CrossBowmanEntityMixin {
    @Inject(method = "canHoldItem", at = @At("HEAD"), cancellable = true)
    private void napoleonic$canHoldMusketOrCartridge(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (itemStack != null && (LineInfantryHelper.isMusketWeapon(itemStack) || LineInfantryHelper.isFirearmAmmo(itemStack))) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
    private void napoleonic$wantsToPickUpCartridge(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (itemStack != null && LineInfantryHelper.isFirearmAmmo(itemStack)) {
            cir.setReturnValue(true);
        }
    }
}

