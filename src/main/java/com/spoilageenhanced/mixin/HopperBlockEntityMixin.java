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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {

    /**
     * Ages food sitting in a hopper (PLAYER_REPORTS.md §10, pass 1148).
     *
     * <p>Vanilla never calls {@code inventoryTick} on a hopper's own contents —
     * {@code pushItemsTick} (HopperBlockEntity.java:97) only moves items in and out — so a
     * hopper holding food is a mini-freezer: items age in the chest above and the chest below
     * but not while in transit. The hopper is a container like any other; there is no design
     * argument for it being a freezer. Injected at RETURN of {@code pushItemsTick}, which the
     * block's ticker calls every server tick (HopperBlock.java:96), on the same 20-tick
     * phase-spread cadence as {@code FridgeBlockEntityMixin} and
     * {@code BrewingStandBlockEntityMixin}.</p>
     */
    @Inject(
            method = "pushItemsTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/HopperBlockEntity;)V",
            at = @At("RETURN"),
            require = 0
    )
    private static void spoilage_enhanced$ageHopperContents(
            net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state,
            HopperBlockEntity entity,
            CallbackInfo ci
    ) {
        if (level == null || level.isClientSide()) {
            return;
        }
        // Phase-spread by position, same as FridgeBlockEntityMixin.
        if (FoodSpoilageUtil.shouldSkipAgingTick(pos, level.getGameTime())) {
            return;
        }
        Container container = entity;
        int slots = container.getContainerSize();
        boolean anySpoilable = false;
        for (int i = 0; i < slots; i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
                anySpoilable = true;
                break;
            }
        }
        if (!anySpoilable) {
            return;
        }
        for (int i = 0; i < slots; i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            if (!SpoilageConfig.getInstance().isSpoilable(stack.getItem())) continue;
            FoodSpoilageUtil.updateSpoilage(stack, level);
        }
    }

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

        // Pass 843 (L13 — observed behaviour): the Pass 520 rotten guard here was REMOVED.
        // It rejected a hopper sucking up ANY rotten item entity, regardless of what the
        // hopper feeds — but a hopper feeding a chest is storage, and the player can put
        // rotten food into that same chest by hand (ScreenHandlerMixin's guard only covers
        // PROCESSING input slots, not ChestMenu). The over-broad guard clogged item-sorting
        // systems: one rotten carrot entering a hopper pipe held the hopper forever, because
        // the transfer guard below rejected every onward move into storage too. The hopper
        // itself is storage; the processing targets are protected by the transfer guard
        // below, which is scoped to furnace/brewing inputs exactly like the ScreenHandler.
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
        //
        // Pass 843 (L13 — observed behaviour): the guard is now SCOPED to processing
        // containers, matching ScreenHandlerMixin.isProcessingInputSlot — the reference
        // guard only covers crafting grids, furnace inputs and brewing inputs, and a
        // player can hand-place rotten food into a chest that a hopper was forbidden to
        // fill. Storage targets (chest, barrel, shulker, another hopper, dispenser) take
        // rotten food exactly as a player would place it; furnace and brewing inputs
        // still refuse it.
        if (container instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
                || container instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity) {
            int slotSpace = container.getMaxStackSize() - current.getCount();
            int transferCount = Math.min(itemStack.getCount(), Math.max(0, slotSpace));
            if (transferCount > 0 && FoodSpoilageUtil.worstSliceContainsRotten(itemStack, transferCount)) {
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE,
                        "HopperBlockEntityMixin: rejected rotten transfer of " + transferCount + " items into slot " + slot);
                cir.setReturnValue(itemStack);
                cir.cancel();
                return;
            }
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
