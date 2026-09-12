package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ages food inside a hopper's own inventory at the same 20-tick cadence as other containers.
 *
 * <p>HopperBlockEntity has no serverTick — vanilla ticks hoppers via the static
 * {@code pushItemsTick}, which only moves items (eject/suck) and never calls
 * {@code inventoryTick} on the hopper's own contents. HopperBlockEntityMixin only
 * intercepts transfers (addItem, tryMoveInItem), so food sitting in a hopper did not
 * age — a mini-freezer in the middle of every pipeline (PLAYER_REPORTS §10).</p>
 *
 * <p>This mixin injects at the RETURN of {@code pushItemsTick} and ages the hopper's
 * contents using the same phase-spread ({@code shouldSkipAgingTick}) as the other
 * container paths, so a row of hoppers does not all age on the same tick boundary.</p>
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperAgingMixin {

    @Inject(
        method = "pushItemsTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/HopperBlockEntity;)V",
        at = @At("RETURN")
    )
    private static void spoilage_enhanced_ageHopperContents(Level level, BlockPos pos, BlockState state,
            HopperBlockEntity hopper, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        if (FoodSpoilageUtil.shouldSkipAgingTick(pos, level.getGameTime())) {
            return;
        }

        // Fast-path probe: skip the whole method when no slot holds spoilable food.
        boolean anySpoilable = false;
        for (int i = 0; i < hopper.getContainerSize(); i++) {
            ItemStack stack = hopper.getItem(i);
            if (!stack.isEmpty() && SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
                anySpoilable = true;
                break;
            }
        }
        if (!anySpoilable) {
            return;
        }

        boolean changed = false;
        for (int i = 0; i < hopper.getContainerSize(); i++) {
            ItemStack stack = hopper.getItem(i);
            if (stack.isEmpty()) continue;
            if (!SpoilageConfig.getInstance().isSpoilable(stack.getItem())) continue;

            int count = stack.getCount();
            if (count > 0) {
                SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
                if (data != null && data.totalTracked() > count) {
                    SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, count);
                    stack.set(ModDataComponentTypes.SPOILAGE, split[1]);
                    changed = true;
                }
            }
            SpoilageData before = stack.get(ModDataComponentTypes.SPOILAGE);
            FoodSpoilageUtil.updateSpoilage(stack, level);
            SpoilageData after = stack.get(ModDataComponentTypes.SPOILAGE);
            if (!java.util.Objects.equals(before, after)) {
                changed = true;
            }
        }
        if (changed) {
            hopper.setChanged();
        }
    }
}
