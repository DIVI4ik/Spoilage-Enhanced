package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.CraftingSpoilageTransfer;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Intercepts Crafter crafting to transfer spoilage data from ingredients to the result,
 * just like CraftingResultSlotMixin does for player crafting tables.
 *
 * <p>Injects into dispenseItem which receives the already-assembled result,
 * allowing us to modify its spoilage component before it is dispensed.</p>
 */
@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {

    @Inject(
        method = "dispenseItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/CrafterBlockEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/item/crafting/RecipeHolder;)V",
        at = @At("HEAD")
    )
    private void spoilage_enhanced_onCrafterDispense(
            ServerLevel level, BlockPos pos, CrafterBlockEntity blockEntity,
            ItemStack results, BlockState blockState,
            RecipeHolder<?> recipe, CallbackInfo ci) {
        if (results.isEmpty()) return;

        // Collect ingredients from the crafter's 9 slots
        List<ItemStack> ingredients = new ArrayList<>(blockEntity.getContainerSize());
        for (int i = 0; i < blockEntity.getContainerSize(); i++) {
            ingredients.add(blockEntity.getItem(i));
        }

        CraftingSpoilageTransfer.Result transfer =
                CraftingSpoilageTransfer.compute(ingredients, results, level.getGameTime());
        if (transfer == null) return;

        results.set(ModDataComponentTypes.SPOILAGE, transfer.data());
        SpoilageEnhancedLogger.log("Crafter: Spoilage transferred from " + transfer.source().getItem()
                + " to " + results.getItem() + " (State: " + transfer.state() + ")");
    }

    /**
     * Reconciles spoilage trackers with the reduced stack counts after the Crafter consumes
     * one item per slot.
     *
     * <p>{@code CrafterBlock.dispenseFrom} consumes ingredients with {@code it.shrink(1)} on
     * every non-empty slot AFTER {@code dispenseItem} has already handed the result its
     * spoilage data. Vanilla {@code shrink} never touches data components, and Crafter slots
     * receive neither {@code Item.inventoryTick} nor {@code ItemEntity.tick}, so nothing else
     * ever repairs the count/tracker mismatch. A slot that held N items with N trackers is left
     * with N-1 items but still N trackers — a phantom (worst) tracker that the next craft's
     * {@code CraftingSpoilageTransfer.compute} reads via {@code Collections.min}, making the
     * result inherit spoilage from an item that was already consumed.</p>
     *
     * <p>Injecting at RETURN runs after the shrink loop, so each slot's tracker list is trimmed
     * to its new count. The worst trackers are dropped first, matching the mod's convention that
     * the worst item is the one consumed (BUG-06 / BUG-12).</p>
     */
    @Inject(
        method = "dispenseFrom(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)V",
        at = @At("RETURN")
    )
    private void spoilage_enhanced_reconcileAfterCraft(BlockState state, ServerLevel level, BlockPos pos, CallbackInfo ci) {
        if (!(level.getBlockEntity(pos) instanceof CrafterBlockEntity blockEntity)) return;

        for (int i = 0; i < blockEntity.getContainerSize(); i++) {
            ItemStack stack = blockEntity.getItem(i);
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

            // More trackers than items: the Crafter consumed (tracked - count) items without
            // updating the component. Drop the worst trackers, keep the best.
            SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, tracked - count);
            stack.set(ModDataComponentTypes.SPOILAGE, split[0]);
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE,
                    "Crafter: reconciled slot " + i + " " + stack.getItem()
                            + " from " + tracked + " trackers to " + count);
        }
    }
}