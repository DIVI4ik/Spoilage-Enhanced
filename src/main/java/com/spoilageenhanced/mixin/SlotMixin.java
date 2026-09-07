package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotMixin {

    @Shadow
    public abstract ItemStack getItem();

    @Inject(method = "safeInsert(Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"))
    private void onInsertStack(ItemStack stack, int count, CallbackInfoReturnable<ItemStack> cir) {
        Slot self = (Slot) (Object) this;
        ItemStack current = self.getItem();

        // Pass 84 (Lens 8/13): the work below gates on stackData != null, so when the inserted
        // stack has no SPOILAGE component (every non-food GUI insertion — stone, tools, blocks)
        // nothing happens. Check the component first via the cheaper ItemStack.has() and skip
        // the SpoilageConfig.getInstance().isSpoilable() CHM get when no data is present.
        if (!stack.hasNonDefault(ModDataComponentTypes.SPOILAGE)) {
            return;
        }
        if (SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
            int maxInsert = Math.min(count, self.getMaxStackSize(stack) - current.getCount());
            int actualInsert = Math.min(stack.getCount(), maxInsert);

            if (actualInsert > 0 && ItemStack.isSameItemSameComponents(stack, current)) {
                if (SpoilageEnhancedLogger.isTraceEnabled()) SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE, "SlotMixin: safeInsert transferring " + actualInsert + " items");
                SpoilageData stackData = stack.get(ModDataComponentTypes.SPOILAGE);
                SpoilageData currentData = current.get(ModDataComponentTypes.SPOILAGE);
                if (stackData != null) {
                    SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(stackData, actualInsert);
                    stack.set(ModDataComponentTypes.SPOILAGE, split[0]);
                    current.set(ModDataComponentTypes.SPOILAGE, FoodSpoilageUtil.mergeItems(currentData, split[1]));
                }
            }
        }
    }

    @Inject(method = "safeClone", at = @At("RETURN"), cancellable = true)
    private void onSafeClone(Player player, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (result.isEmpty()) return;
        Slot self = (Slot) (Object) this;
        ItemStack original = self.getItem();
        // Pass 84 (Lens 8/13): the work below gates on originalData != null, so check the
        // slot's component first (cheap component-map lookup) and skip the isSpoilable()
        // CHM get when the slot item has no spoilage data (every non-food slot clone).
        if (!original.hasNonDefault(ModDataComponentTypes.SPOILAGE)) {
            return;
        }
        if (SpoilageConfig.getInstance().isSpoilable(result.getItem())) {
            SpoilageData originalData = original.get(ModDataComponentTypes.SPOILAGE);
            if (originalData != null && !originalData.isEmpty() && original.getCount() > 0) {
                int targetCount = result.getCount();
                java.util.List<Long> origFresh = originalData.freshExpirations();
                java.util.List<Long> origStale = originalData.staleExpirations();
                int origRotten = originalData.rottenCount();
                int origTotal = origFresh.size() + origStale.size() + origRotten;

                if (origTotal > 0 && targetCount > origTotal) {
                    java.util.List<Long> newFresh = new java.util.ArrayList<>();
                    java.util.List<Long> newStale = new java.util.ArrayList<>();
                    int newRotten = 0;

                    for (int i = 0; i < targetCount; i++) {
                        int idx = i % origTotal;
                        if (idx < origFresh.size()) {
                            newFresh.add(origFresh.get(idx));
                        } else if (idx < origFresh.size() + origStale.size()) {
                            newStale.add(origStale.get(idx - origFresh.size()));
                        } else {
                            newRotten++;
                        }
                    }
                    result.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(newFresh, newStale, newRotten, originalData.speedMultiplier()));
                }
            }
        }
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void onCanTakeItems(Player playerEntity, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof net.minecraft.world.inventory.MerchantResultSlot) {
            Slot self = (Slot) (Object) this;
            if (self.container instanceof net.minecraft.world.inventory.MerchantContainer merchantContainer) {
                ItemStack firstBuy = merchantContainer.getItem(0);
                ItemStack secondBuy = merchantContainer.getItem(1);

                if (!firstBuy.isEmpty() && SpoilageConfig.getInstance().isSpoilable(firstBuy.getItem())) {
                    if (FoodSpoilageUtil.getWorstState(firstBuy) == FoodSpoilageUtil.SpoilageState.ROTTEN) {
                        cir.setReturnValue(false);
                        return;
                    }
                }
                if (!secondBuy.isEmpty() && SpoilageConfig.getInstance().isSpoilable(secondBuy.getItem())) {
                    if (FoodSpoilageUtil.getWorstState(secondBuy) == FoodSpoilageUtil.SpoilageState.ROTTEN) {
                        cir.setReturnValue(false);
                        return;
                    }
                }
            }
        }
    }

    @Inject(method = "onTake", at = @At("HEAD"))
    private void onTakeItem(Player player, ItemStack stack, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if ((Object) this instanceof net.minecraft.world.inventory.MerchantResultSlot) {
            if (!player.level().isClientSide() && !stack.isEmpty()
                    && SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
                FoodSpoilageUtil.makeFresh(stack, player.level());
            }
        }
    }
}
