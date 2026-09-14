package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Ages food inside a brewing stand's ingredient slot.
 *
 * <p>Pass 879 (L13 — observed behaviour): BrewingStandBlockEntity extends
 * BaseContainerBlockEntity, which does NOT call inventoryTick on its contents, and
 * there was no BrewingStandBlockEntityMixin at all. So a carrot placed in slot 3
 * never aged — verified live: after twenty seconds the NBT still read
 * {count:1,Slot:3b,id:"minecraft:carrot"} with no spoilage component.</p>
 *
 * <p>The cadence is the same 20-tick window the other container paths use, offset by
 * the block position so a row of brewing stands does not all tick on the same boundary
 * (same phase-spreading as {@code ItemEntityMixin}). The probe keys on
 * {@link SpoilageConfig#isSpoilable} rather than on the component being present, so an
 * unstamped ingredient is lazily stamped by {@link FoodSpoilageUtil#updateSpoilage}
 * exactly as the bundle and minecart branches do.</p>
 */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {

    @Shadow
    protected abstract NonNullList<ItemStack> getItems();

    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void spoilage_enhanced$ageIngredient(Level level, BlockPos pos, BlockState state,
            BrewingStandBlockEntity blockEntity, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        if (com.spoilageenhanced.util.FoodSpoilageUtil.shouldSkipAgingTick(pos, level.getGameTime())) {
            return;
        }

        NonNullList<ItemStack> stacks = ((BrewingStandBlockEntityMixin) (Object) blockEntity).getItems();
        // Pass 1192: shared probe — see ContainerAgingSweepMixin.ageContainer.
        boolean anySpoilable = false;
        for (ItemStack stack : stacks) {
            if (FoodSpoilageUtil.stackIsOrCarriesSpoilableFood(stack)) {
                anySpoilable = true;
                break;
            }
        }
        if (!anySpoilable) {
            return;
        }

        boolean changed = false;
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) continue;
            // Pass 1191: updateSpoilage no-ops on stacks that are neither spoilable nor
            // food-carrying (its own guards), so no isSpoilable filter here — a bundle or
            // nested shulker box in this stand must reach its BUNDLE_CONTENTS/CONTAINER
            // branch.
            // Slot 3 is the ingredient slot. Slots 0-2 are water bottles (potions), which
            // are excluded from spoilage by design — but the loop below is harmless on
            // them: isSpoilable returns false and they are skipped.
            int count = stack.getCount();
            if (count > 0) {
                com.spoilageenhanced.component.SpoilageData data =
                        stack.get(ModDataComponentTypes.SPOILAGE);
                if (data != null && data.totalTracked() > count) {
                    com.spoilageenhanced.component.SpoilageData[] split =
                            FoodSpoilageUtil.extractWorstItems(data, count);
                    stack.set(ModDataComponentTypes.SPOILAGE, split[1]);
                    changed = true;
                }
            }
            com.spoilageenhanced.component.SpoilageData before =
                    stack.get(ModDataComponentTypes.SPOILAGE);
            FoodSpoilageUtil.updateSpoilage(stack, level);
            com.spoilageenhanced.component.SpoilageData after =
                    stack.get(ModDataComponentTypes.SPOILAGE);
            if (!java.util.Objects.equals(before, after)) {
                changed = true;
            }
        }
        if (changed && SpoilageEnhancedLogger.isTraceEnabled()) {
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE,
                    "BrewingStandBlockEntityMixin: aged food in brewing stand at " + pos);
        }
    }
}