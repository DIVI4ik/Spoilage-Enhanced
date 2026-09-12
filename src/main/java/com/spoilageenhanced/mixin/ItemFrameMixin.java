package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ages food inside an ItemFrame at the same 20-tick cadence as other containers.
 *
 * <p>ItemFrame extends HangingEntity which extends Entity. The base Entity.tick()
 * does nothing for the held item. Armor stands (LivingEntity) age their equipment
 * via LivingEntity.tick() -> equipment.tick() -> inventoryTick (hooked by ItemMixin).
 * Item frames were a "perfect freezer" — food never aged while displayed.
 *
 * <p>This mixin injects at the RETURN of Entity.tick() (which ItemFrame inherits)
 * and ages the held item stack if it is spoilable. Uses the same phase-spreading
 * as ItemEntityMixin and other container paths.
 */
@Mixin(ItemFrame.class)
public abstract class ItemFrameMixin {

    @Inject(
        method = "tick",
        at = @At("RETURN"),
        require = 0
    )
    private void spoilage_enhanced_ageHeldItem(CallbackInfo ci) {
        ItemFrame frame = (ItemFrame) (Object) this;
        if (frame.level().isClientSide()) {
            return;
        }
        if (FoodSpoilageUtil.shouldSkipAgingTick(frame.blockPosition(), frame.level().getGameTime())) {
            return;
        }

        ItemStack itemStack = frame.getItem();
        if (itemStack.isEmpty()) {
            return;
        }
        if (!SpoilageConfig.getInstance().isSpoilable(itemStack.getItem())) {
            return;
        }

        FoodSpoilageUtil.updateSpoilage(itemStack, frame.level());
        frame.setItem(itemStack);
    }
}
