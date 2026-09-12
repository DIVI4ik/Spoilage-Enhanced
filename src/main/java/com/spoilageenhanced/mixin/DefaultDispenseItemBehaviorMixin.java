package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes spoilage tracker count on the dispensed item stack.
 *
 * <p>When a Dispenser or Dropper dispenses an item, {@code DefaultDispenseItemBehavior.execute}
 * calls {@code dispensed.split(1)} (or {@code copyWithCount(1)} for containers) to create the
 * 1-item stack that will be shot into the world or placed into a container. Both methods copy
 * the ENTIRE spoilage tracker list onto the 1-item stack. The dispensed item therefore carries
 * N trackers for 1 item — a phantom tracker that makes the item appear more spoiled than it is.
 *
 * <p>This mixin injects at the RETURN of {@code execute} and trims the returned stack's
 * spoilage data to match its count (1). The dispenser/dropper slot is reconciled separately
 * by {@link DispenserBlockMixin} and {@link DropperBlockMixin} at {@code dispenseFrom RETURN}.
 */
@Mixin(net.minecraft.core.dispenser.DefaultDispenseItemBehavior.class)
public abstract class DefaultDispenseItemBehaviorMixin {

    @Inject(
        method = "execute(Lnet/minecraft/core/dispenser/BlockSource;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void spoilage_enhanced_fixDispensedStack(BlockSource source, ItemStack dispensed, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (result == null || result.isEmpty()) return;
        if (!result.hasNonDefault(ModDataComponentTypes.SPOILAGE)) return;
        if (!SpoilageConfig.getInstance().isSpoilable(result.getItem())) return;

        SpoilageData data = result.get(ModDataComponentTypes.SPOILAGE);
        if (data == null || data.isEmpty()) return;

        int tracked = data.totalTracked();
        int count = result.getCount();
        if (tracked <= count) return;

        // Dispensed item has more trackers than its count (typically 1).
        // Keep only the best trackers for the dispensed item.
        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, tracked - count);
        result.set(ModDataComponentTypes.SPOILAGE, split[1]); // split[1] is the extracted (worst) items
    }
}
