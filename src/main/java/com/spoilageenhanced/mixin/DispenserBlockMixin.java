package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reconciles spoilage trackers with the reduced stack count after a Dispenser dispenses one item.
 *
 * <p>{@code DispenserBlock.dispenseFrom} moves one item either into the world
 * ({@code DefaultDispenseItemBehavior.execute} → {@code dispensed.split(1)}) or into the
 * container in front ({@code itemStack.copyWithCount(1)}). Both {@code split} and
 * {@code copyWithCount} copy the ENTIRE spoilage tracker list onto the 1-item stack, and the
 * remainder is rebuilt with {@code shrink(1)}, which never touches data components. The dispenser
 * slot is therefore left with N-1 items but still N trackers — a phantom (worst) tracker that a
 * downstream Crafter reads via {@code Collections.min}, making the result inherit spoilage from
 * an item that was already dispensed (same class as BUG-18).</p>
 *
 * <p>Container block entities receive neither {@code Item.inventoryTick} nor
 * {@code ItemEntity.tick}, so nothing else ever repairs the count/tracker mismatch. Injecting at
 * RETURN runs after {@code blockEntity.setItem(slot, remaining)}, so the slot's tracker list is
 * trimmed to its new count. The worst trackers are dropped first, matching the mod's convention
 * that the worst item is the one consumed (BUG-06 / BUG-12).</p>
 */
@Mixin(net.minecraft.world.level.block.DispenserBlock.class)
public abstract class DispenserBlockMixin {

    @Inject(
        method = "dispenseFrom(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V",
        at = @At("RETURN")
    )
    private void spoilage_enhanced_reconcileAfterDispense(ServerLevel level, BlockState state, BlockPos pos, CallbackInfo ci) {
        if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.DispenserBlockEntity blockEntity)) return;

        for (int i = 0; i < blockEntity.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = blockEntity.getItem(i);
            // Pass 87 (Lens 8/13): the work below continues when data is null/empty, so when
            // the slot stack has no SPOILAGE component nothing happens. Check the component
            // first via the cheaper ItemStack.has() and skip the isSpoilable() CHM get.
            if (stack.isEmpty() || !stack.hasNonDefault(ModDataComponentTypes.SPOILAGE)) continue;
            if (!SpoilageConfig.getInstance().isSpoilable(stack.getItem())) continue;

            SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
            if (data == null || data.isEmpty()) continue;

            int tracked = data.totalTracked();
            int count = stack.getCount();
            if (tracked <= count) continue;

            // More trackers than items: the Dispenser dispensed (tracked - count) items without
            // updating the component. Drop the worst trackers, keep the best.
            SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, tracked - count);
            stack.set(ModDataComponentTypes.SPOILAGE, split[0]);
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE,
                    "Dispenser: reconciled slot " + i + " " + stack.getItem()
                            + " from " + tracked + " trackers to " + count);
        }
    }
}
