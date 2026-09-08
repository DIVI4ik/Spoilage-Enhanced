package com.spoilageenhanced.mixin;

import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Ages food inside container minecarts (chest, hopper, and any modded cart extending
 * {@link AbstractMinecartContainer}).
 *
 * <p>Pass 853 (L13 — observed behaviour): {@code AbstractMinecart.tick()} and
 * {@code MinecartHopper.tick()} never call {@code inventoryTick} on their contents, so
 * food inside a chest minecart or hopper minecart never aged — while the same food in a
 * chest block or a bundle (fixed in pass 850) does. This closes the last container-aging
 * gap the behaviour lens found: every vanilla container that can hold food now ages it.</p>
 *
 * <p>The cadence is the same 20-tick window the other container paths use, offset by the
 * entity id so a rail yard full of minecarts does not all tick on the same boundary
 * (same phase-spreading as {@code ItemEntityMixin}). The fast-path spoilable probe runs
 * before any allocation, so a cart full of cobblestone pays one pass over its slots.</p>
 */
@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartContainerMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void spoilage_enhanced$ageContainerContents(CallbackInfo ci) {
        AbstractMinecart self = (AbstractMinecart) (Object) this;
        if (!(self instanceof AbstractMinecartContainer container)) {
            return;
        }
        if (self.level().isClientSide() || self.tickCount % 20 != 0) {
            return;
        }

        List<ItemStack> stacks = container.getItemStacks();
        boolean anySpoilable = false;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && stack.hasNonDefault(com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE)) {
                anySpoilable = true;
                break;
            }
        }
        if (!anySpoilable) {
            return;
        }

        boolean changed = false;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            if (!stack.hasNonDefault(com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE)) continue;
            // Trim over-tracked to count, keeping the WORST trackers — same invariant as
            // ItemEntityMixin.onTick and the bundle/container branches.
            int count = stack.getCount();
            if (count > 0) {
                com.spoilageenhanced.component.SpoilageData data =
                        stack.get(com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE);
                if (data != null && data.totalTracked() > count) {
                    com.spoilageenhanced.component.SpoilageData[] split =
                            FoodSpoilageUtil.extractWorstItems(data, count);
                    stack.set(com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE, split[1]);
                    changed = true;
                }
            }
            com.spoilageenhanced.component.SpoilageData before =
                    stack.get(com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE);
            FoodSpoilageUtil.updateSpoilage(stack, self.level());
            com.spoilageenhanced.component.SpoilageData after =
                    stack.get(com.spoilageenhanced.component.ModDataComponentTypes.SPOILAGE);
            if (!java.util.Objects.equals(before, after)) {
                changed = true;
            }
        }
        if (changed && SpoilageEnhancedLogger.isTraceEnabled()) {
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE,
                    "AbstractMinecartContainerMixin: aged food in " + self.getType());
        }
    }
}
