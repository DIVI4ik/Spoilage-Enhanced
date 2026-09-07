package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ThrowableItemProjectile.class)
public abstract class ThrownItemEntityMixin {

    @ModifyVariable(method = "setItem", at = @At("HEAD"), argsOnly = true)
    private ItemStack spoilage_enhanced_modifyThrownItem(ItemStack itemStack) {
        if (itemStack != null && !itemStack.isEmpty()
                && SpoilageConfig.getInstance().isSpoilable(itemStack.getItem())) {
            ThrowableItemProjectile projectile = (ThrowableItemProjectile) (Object) this;
            if (projectile.level() != null && !projectile.level().isClientSide()) {
                FoodSpoilageUtil.updateSpoilage(itemStack, projectile.level());
            }
            if (itemStack.getCount() > 1) {
                ItemStack singleItem = itemStack.copyWithCount(1);
                SpoilageData current = itemStack.get(ModDataComponentTypes.SPOILAGE);
                if (current != null) {
                    SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(current, 1);
                    singleItem.set(ModDataComponentTypes.SPOILAGE, split[1]);
                    itemStack.set(ModDataComponentTypes.SPOILAGE, split[0]);
                }
                return singleItem;
            }
        }
        return itemStack;
    }
}
