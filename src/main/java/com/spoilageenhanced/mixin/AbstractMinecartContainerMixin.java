package com.spoilageenhanced.mixin;

import com.spoilageenhanced.config.SpoilageConfig;
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
            if (stack.isEmpty()) {
                continue;
            }
            if (SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
                anySpoilable = true;
                break;
            }
            // Pass 1191 (L13): a stack that is not itself food can still CARRY it — a
            // bundle or nested shulker box. Without this the whole container was skipped
            // as 'no food here' and the carried food never aged. Same probe shape as
            // ContainerAgingSweepMixin.
            if (stack.has(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS)) {
                net.minecraft.world.item.component.BundleContents contents =
                        stack.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
                for (net.minecraft.world.item.ItemStackTemplate template : contents.items()) {
                    if (SpoilageConfig.getInstance().isSpoilable(template.item().value())) {
                        anySpoilable = true;
                        break;
                    }
                }
            } else if (stack.has(net.minecraft.core.component.DataComponents.CONTAINER)) {
                net.minecraft.world.item.component.ItemContainerContents contents =
                        stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
                for (net.minecraft.world.item.ItemStackTemplate template : contents.nonEmptyItems()) {
                    if (SpoilageConfig.getInstance().isSpoilable(template.item().value())) {
                        anySpoilable = true;
                        break;
                    }
                }
            }
            if (anySpoilable) {
                break;
            }
        }
        if (!anySpoilable) {
            return;
        }

        boolean changed = false;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            // Pass 1191: updateSpoilage no-ops on stacks that are neither spoilable nor
            // food-carrying (its own guards), so no isSpoilable filter here — a bundle or
            // nested shulker box in this minecart must reach its BUNDLE_CONTENTS/CONTAINER
            // branch. A minecart is an entity, so the container sweep cannot cover it.
            // Pass 879 (L13 — observed behaviour): the bundle and container branches call
            // updateSpoilage on EVERY spoilable stack, which lazily stamps one via
            // initializeItemSpoilage when the component is null/empty. This branch skipped
            // any stack without the component, so a plain carrot summoned into a chest
            // minecart stayed unstamped forever — verified live: after ten seconds the
            // NBT still read {count:1,Slot:0b,id:"minecraft:carrot"} with no spoilage.
            // Remove the skip and let updateSpoilage do the stamping, exactly as the bundle
            // branch (FoodSpoilageUtil.updateBundleItemSpoilage) does.
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
