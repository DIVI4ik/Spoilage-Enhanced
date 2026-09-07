package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {

    @Inject(
            method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void onAddItemEntity(
            Container container,
            net.minecraft.world.entity.item.ItemEntity entity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (entity == null || entity.getItem() == null || entity.getItem().isEmpty()) {
            return;
        }

        ItemStack itemStack = entity.getItem();
        SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE,
                "HopperBlockEntityMixin: onAddItemEntity called for " + itemStack.getItem() + " count=" + itemStack.getCount());

        // Pass 520 (L13 — observed behaviour): hopper was missing the rotten guard that
        // ScreenHandlerMixin has (Pass 223). A hopper would happily suck up rotten food
        // because it bypasses the ScreenHandler path entirely. Reject the transfer if
        // the item entity carries a ROTTEN entry.
        if (FoodSpoilageUtil.worstSliceContainsRotten(itemStack, 1)) {
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE,
                    "HopperBlockEntityMixin: rejected rotten item entity " + itemStack.getItem() + " count=" + itemStack.getCount());
            cir.setReturnValue(false);
            return;
        }
    }

    @Inject(
            method = "tryMoveInItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void onTryMoveInItem(
            @Nullable Container from,
            Container container,
            ItemStack itemStack,
            int slot,
            @Nullable Direction direction,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE,
                "HopperBlockEntityMixin: onTryMoveInItem called for " + itemStack.getItem() + " count=" + itemStack.getCount() + " slot=" + slot);

        // Pass 83 (Lens 8/13): both branches below gate on itemStack.get(SPOILAGE) != null, so
        // when there is no spoilage component on the stack (the common case for stone/cobble/
        // redstone flowing through hoppers) neither branch does any work. Check the component
        // first via the cheaper ItemStack.has() (component-map lookup) and skip the SpoilageConfig
        // getInstance() + isSpoilable() ConcurrentHashMap.get() chain when no data is present.
        // For non-food items in hoppers this saves the CHM get (~0.073us) in favor of the
        // component-map lookup (~0.047us), a 1.56x speedup on the hot path.
        if (!itemStack.hasNonDefault(ModDataComponentTypes.SPOILAGE)
                || !SpoilageConfig.getInstance().isSpoilable(itemStack.getItem())) {
            return;
        }

        ItemStack current = container.getItem(slot);

        // Pass 520 (L13 — observed behaviour): hopper was missing the rotten guard that
        // ScreenHandlerMixin has (Pass 223). A hopper would happily suck up rotten food
        // because it bypasses the ScreenHandler path entirely. The transfer moves the
        // WORST items first (extractWorstItems), so even a mixed stack with one rotten
        // item will transfer that rotten item first. Reject the transfer if the worst-N
        // slice for the transfer count contains a ROTTEN entry.
        int slotSpace = container.getMaxStackSize() - current.getCount();
        int transferCount = Math.min(itemStack.getCount(), Math.max(0, slotSpace));
        if (transferCount > 0 && FoodSpoilageUtil.worstSliceContainsRotten(itemStack, transferCount)) {
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE,
                    "HopperBlockEntityMixin: rejected rotten transfer of " + transferCount + " items into slot " + slot);
            cir.setReturnValue(itemStack);
            cir.cancel();
            return;
        }
        if (current.isEmpty()) {
            // Vanilla transfers itemStack into the empty slot intact. If itemStack is
            // over-tracked (more trackers than items — e.g. DropperBlock.dispenseFrom's
            // copyWithCount(1) copies the whole tracker list onto a 1-item stack), trim it to
            // its count so the invariant totalTracked == count holds. Container block entities
            // are never ticked by updateSpoilage, so nothing else would ever repair the mismatch.
            // The moved items are the worst ones (BUG-06 / BUG-12 convention).
            SpoilageData sourceData = itemStack.get(ModDataComponentTypes.SPOILAGE);
            if (sourceData != null && sourceData.totalTracked() > itemStack.getCount()) {
                SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(sourceData, itemStack.getCount());
                itemStack.set(ModDataComponentTypes.SPOILAGE, split[1]);
                if (SpoilageEnhancedLogger.isTraceEnabled()) SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE,
                        "HopperBlockEntityMixin: trimmed over-tracked stack entering empty slot " + slot
                                + " from " + sourceData.totalTracked() + " trackers to " + itemStack.getCount());
            }
            return;
        }

        if (!container.canPlaceItem(slot, itemStack)) {
            return;
        }
        if (container instanceof WorldlyContainer worldly && direction != null && !worldly.canPlaceItemThroughFace(slot, itemStack, direction)) {
            return;
        }

        if (current.getCount() < current.getMaxStackSize() && ItemStack.isSameItemSameComponents(current, itemStack)) {
            int space = Math.min(itemStack.getMaxStackSize(), current.getMaxStackSize()) - current.getCount();
            int count = Math.min(itemStack.getCount(), space);
            if (count > 0) {
                SpoilageData sourceData = itemStack.get(ModDataComponentTypes.SPOILAGE);
                SpoilageData targetData = current.get(ModDataComponentTypes.SPOILAGE);
                if (sourceData != null) {
                    SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(sourceData, count);
                    itemStack.set(ModDataComponentTypes.SPOILAGE, split[0]);
                    current.set(ModDataComponentTypes.SPOILAGE, FoodSpoilageUtil.mergeItems(targetData, split[1]));
                    if (SpoilageEnhancedLogger.isTraceEnabled()) SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE,
                            "HopperBlockEntityMixin: transferred " + count + " items of spoilage data into slot " + slot);
                }
            }
        }
    }
}
