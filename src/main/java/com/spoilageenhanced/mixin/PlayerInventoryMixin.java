package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class PlayerInventoryMixin {

    @Shadow
    public abstract ItemStack getItem(int slot);

    @Unique
    private boolean spoilage_enhanced$wasEmpty;
    @Unique
    private int spoilage_enhanced$preCount;
    @Unique
    private SpoilageData spoilage_enhanced$preData;

    @Inject(method = "addResource(ILnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"))
    private void onAddResource(int slot, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (slot < 0 || stack.isEmpty()) return;
        // Pass 85 (Lens 8/13): the merge branch below gates on stackData != null and the
        // afterAddResource tail gates on spoilage_enhanced$preData != null, so when the picked-up
        // stack has no SPOILAGE component (every non-food pickup — cobblestone mining, dirt
        // digging, mob drops) nothing happens in either phase. Check the component first via the
        // cheaper ItemStack.has() and skip the isSpoilable() CHM get when no data is present.
        if (!stack.hasNonDefault(ModDataComponentTypes.SPOILAGE)) return;
        if (!SpoilageConfig.getInstance().isSpoilable(stack.getItem())) return;

        ItemStack self = this.getItem(slot);
        spoilage_enhanced$wasEmpty = (self == null || self.isEmpty());
        spoilage_enhanced$preCount = stack.getCount();
        spoilage_enhanced$preData = stack.get(ModDataComponentTypes.SPOILAGE);

        if (!spoilage_enhanced$wasEmpty && SpoilageConfig.getInstance().isSpoilable(self.getItem())) {
            if (ItemStack.isSameItemSameComponents(self, stack)) {
                int maxTake = self.getMaxStackSize() - self.getCount();
                int amountTaken = Math.min(stack.getCount(), maxTake);
                if (amountTaken > 0) {
                    if (SpoilageEnhancedLogger.isTraceEnabled()) SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE, "PlayerInventoryMixin: adding " + amountTaken + " items to slot " + slot);
                    SpoilageData stackData = stack.get(ModDataComponentTypes.SPOILAGE);
                    SpoilageData selfData = self.get(ModDataComponentTypes.SPOILAGE);
                    if (stackData != null) {
                        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(stackData, amountTaken);
                        stack.set(ModDataComponentTypes.SPOILAGE, split[0]);
                        self.set(ModDataComponentTypes.SPOILAGE, FoodSpoilageUtil.mergeItems(selfData, split[1]));
                    }
                }
            }
        }
    }

    @Inject(method = "addResource(ILnet/minecraft/world/item/ItemStack;)I", at = @At("RETURN"))
    private void afterAddResource(int slot, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (spoilage_enhanced$wasEmpty && spoilage_enhanced$preData != null) {
            ItemStack self = this.getItem(slot);
            if (self != null && !self.isEmpty() && SpoilageConfig.getInstance().isSpoilable(self.getItem())) {
                int added = self.getCount();
                if (added > 0) {
                    SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(spoilage_enhanced$preData, added);
                    self.set(ModDataComponentTypes.SPOILAGE, split[1]);
                    if (!stack.isEmpty() && added < spoilage_enhanced$preCount) {
                        stack.set(ModDataComponentTypes.SPOILAGE, split[0]);
                    }
                }
            }
        }
        spoilage_enhanced$preData = null;
    }
}
