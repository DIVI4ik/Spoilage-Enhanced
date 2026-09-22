package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Ages food inside an allay's inventory.
 *
 * <p>Pass 880 (L13 — observed behaviour): the allay's inventory is a
 * {@link net.minecraft.world.SimpleContainer} which does NOT call
 * {@code inventoryTick} on its contents — the only callers are
 * {@code EntityEquipment} (armor/held) and the player's {@code Inventory}.
 * So a carrot carried by an allay never aged — verified live: after twenty
 * seconds the NBT still read {count:1,id:"minecraft:carrot"} with no
 * spoilage component.</p>
 *
 * <p>The cadence is the same 20-tick window the other container paths use,
 * offset by the entity id so a flock of allays does not all tick on the same
 * boundary (same phase-spreading as {@code ItemEntityMixin}). The probe keys
 * on {@link SpoilageConfig#isSpoilable} rather than on the component being
 * present, so an unstamped item is lazily stamped by
 * {@link FoodSpoilageUtil#updateSpoilage} exactly as the bundle, minecart and
 * brewing-stand branches do.</p>
 */
@Mixin(Allay.class)
public abstract class AllayMixin {

    @Shadow
    private net.minecraft.world.SimpleContainer inventory;

    @Inject(method = "tick", at = @At("RETURN"))
    private void spoilage_enhanced$ageInventoryContents(CallbackInfo ci) {
        Allay self = (Allay) (Object) this;
        if (self.level().isClientSide() || (self.getId() + self.tickCount) % 20 != 0) {
            return;
        }

        List<ItemStack> stacks = self.getInventory().getItems();
        boolean anySpoilable = false;
        for (ItemStack stack : stacks) {
            // Pass 1201 (L13 observed): the isSpoilable probe skipped a bundle held
            // by an allay — the food inside never aged. Use the depth-2 food-carrying
            // probe (pass 1199).
            if (!stack.isEmpty() && FoodSpoilageUtil.stackIsOrCarriesSpoilableFood(stack)) {
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
            // Pass 1201: no isSpoilable filter — updateSpoilage's own guards no-op on
            // stacks that are neither spoilable nor food-carrying, and a bundle or
            // nested shulker held by the allay must reach its BUNDLE_CONTENTS/CONTAINER
            // branch (same reasoning as the minecart loop, pass 1191).
            if (!FoodSpoilageUtil.stackIsOrCarriesSpoilableFood(stack)) continue;
            // Trim over-tracked to count, keeping the WORST trackers — same invariant as
            // ItemEntityMixin.onTick and the bundle/container/minecart/brewing-stand branches.
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
            FoodSpoilageUtil.updateSpoilage(stack, self.level());
            com.spoilageenhanced.component.SpoilageData after =
                    stack.get(ModDataComponentTypes.SPOILAGE);
            if (!java.util.Objects.equals(before, after)) {
                changed = true;
            }
        }
        if (changed && SpoilageEnhancedLogger.isTraceEnabled()) {
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE,
                    "AllayMixin: aged food in allay inventory");
        }
    }
}