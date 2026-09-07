package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        // Pass 100 (Lens 8): phase-spreading. The old tickCount % 20 made every dropped item
        // in the world tick on the SAME global boundary (tick 20, 40, 60...), so a field of
        // 1,000 dropped items produced a synchronized load spike every second. Offsetting by
        // the entity id spreads the same work across all 20 ticks of the window — same total
        // work, 1/20th the spike. Entity ids are stable for the entity's lifetime, so each
        // item still ticks exactly once per 20-tick window.
        if ((self.getId() + self.tickCount) % 20 != 0) return;

        ItemStack stack = self.getItem();
        if (!self.level().isClientSide() && !stack.isEmpty()) {
            if (stack.has(DataComponents.CONTAINER)) {
                FoodSpoilageUtil.updateContainerItemSpoilage(stack, self.level());
                // Mutating the stack in place does not mark the SynchedEntityData entry dirty,
                // so the client would keep rendering the stale spoilage bar. setItem() marks it.
                self.setItem(stack);
            } else if (SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
                // A dropped item (player Q-drop) is created via ItemStack.copy() (see
                // LivingEntity.createItemStackToDrop), NOT split(), so it carries ALL trackers of
                // the source stack onto a 1-item entity -> over-tracked (totalTracked > count).
                // updateSpoilage() would "heal" this by keeping the BEST trackers (eat-the-worst
                // semantics from BUG-06/BUG-12), making the dropped item look fresher than it is.
                // Trim to count first, keeping the WORST trackers, so the item preserves its true
                // (worst) spoilage state and merges back correctly on pickup (PlayerInventoryMixin
                // takes the worst tracker via extractWorstItems).
                int count = stack.getCount();
                if (count > 0) {
                    SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
                    if (data != null && data.totalTracked() > count) {
                        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, count);
                        stack.set(ModDataComponentTypes.SPOILAGE, split[1]); // split[1] = worst `count` trackers
                        if (SpoilageEnhancedLogger.isTraceEnabled()) SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE,
                                "ItemEntityMixin: trimmed over-tracked dropped item from "
                                        + data.totalTracked() + " to " + count + " (kept worst)");
                    }
                }
                FoodSpoilageUtil.updateSpoilage(stack, self.level());
                // Same as above: the tick may have transitioned fresh -> stale -> rotten, and
                // without setItem() the client never hears about the new component value.
                self.setItem(stack);
            }
        }
    }

    @Inject(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private static void onMerge(ItemEntity targetEntity, ItemStack targetStack, ItemEntity sourceEntity,
            ItemStack sourceStack, CallbackInfo ci) {
        // Pass 87 (Lens 8/13): the work below gates on sourceData != null, so when the source
        // stack has no SPOILAGE component (every non-food ground merge — cobble drops merging)
        // nothing happens. Check the source component first via the cheaper ItemStack.has() and
        // skip both isSpoilable() CHM gets when no data is present.
        if (!sourceStack.hasNonDefault(ModDataComponentTypes.SPOILAGE)) {
            return;
        }
        if (SpoilageConfig.getInstance().isSpoilable(targetStack.getItem())
                && SpoilageConfig.getInstance().isSpoilable(sourceStack.getItem())) {
            int maxTake = targetStack.getMaxStackSize() - targetStack.getCount();
            int amountTaken = Math.min(sourceStack.getCount(), maxTake);
            if (amountTaken > 0) {
                if (SpoilageEnhancedLogger.isTraceEnabled()) SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE, "ItemEntityMixin: merging " + amountTaken + " items on ground");
                SpoilageData sourceData = sourceStack.get(ModDataComponentTypes.SPOILAGE);
                SpoilageData targetData = targetStack.get(ModDataComponentTypes.SPOILAGE);
                if (sourceData != null) {
                    SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(sourceData, amountTaken);
                    sourceStack.set(ModDataComponentTypes.SPOILAGE, split[0]);
                    targetStack.set(ModDataComponentTypes.SPOILAGE, FoodSpoilageUtil.mergeItems(targetData, split[1]));
                }
            }
        }
    }
}
