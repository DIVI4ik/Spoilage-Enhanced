package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import com.spoilageenhanced.util.SpoilageEnhancedTranslations;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Mixin(AbstractContainerMenu.class)
public abstract class ScreenHandlerMixin {

    @Shadow
    public NonNullList<Slot> slots;

    @Shadow
    private Set<Slot> quickcraftSlots;

    @Shadow
    private int quickcraftStatus;

    @Unique
    private SpoilageData spoilage_enhanced$savedSpoilageData;
    @Unique
    private java.util.List<Slot> spoilage_enhanced$savedDragSlots;
    @Unique
    private Map<Slot, ItemStack> spoilage_enhanced$savedSlotStacks;
    @Unique
    private Map<Slot, Integer> spoilage_enhanced$pickAllSlotCounts;
    @Unique
    private Map<Slot, SpoilageData> spoilage_enhanced$pickAllSlotData;

    @Inject(method = "moveItemStackTo(Lnet/minecraft/world/item/ItemStack;IIZ)Z", at = @At("HEAD"))
    private void onInsertItem(ItemStack stack, int startIndex, int endIndex, boolean fromLast,
            CallbackInfoReturnable<Boolean> cir) {
        // Pass 86 (Lens 8/13): the inner merge branch gates on stackData != null, so when the
        // inserted stack has no SPOILAGE component (every non-food shift-click spread — tools,
        // blocks, mob drops) nothing happens. Check the component first via the cheaper
        // ItemStack.has() and skip the isSpoilable() CHM get when no data is present.
        if (stack.isEmpty() || !stack.isStackable()) return;
        if (!stack.hasNonDefault(ModDataComponentTypes.SPOILAGE)) return;
        if (!SpoilageConfig.getInstance().isSpoilable(stack.getItem())) return;

        int i = fromLast ? endIndex - 1 : startIndex;
        int simulatedCount = stack.getCount();
        if (SpoilageEnhancedLogger.isTraceEnabled()) SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE, "ScreenHandlerMixin: moveItemStackTo for " + BuiltInRegistries.ITEM.getKey(stack.getItem()));

        while (simulatedCount > 0 && (fromLast ? i >= startIndex : i < endIndex)) {
            Slot slot = this.slots.get(i);
            ItemStack itemStack = slot.getItem();

            if (!itemStack.isEmpty() && ItemStack.isSameItemSameComponents(stack, itemStack)) {
                int space = Math.min(slot.getMaxStackSize(stack), itemStack.getMaxStackSize()) - itemStack.getCount();
                int taking = Math.min(space, simulatedCount);
                if (taking > 0) {
                    SpoilageData stackData = stack.get(ModDataComponentTypes.SPOILAGE);
                    SpoilageData slotData = itemStack.get(ModDataComponentTypes.SPOILAGE);
                    if (stackData != null) {
                        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(stackData, taking);
                        stack.set(ModDataComponentTypes.SPOILAGE, split[0]);
                        itemStack.set(ModDataComponentTypes.SPOILAGE, FoodSpoilageUtil.mergeItems(slotData, split[1]));
                    }
                    simulatedCount -= taking;
                }
            }

            if (fromLast) {
                --i;
            } else {
                ++i;
            }
        }
    }

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void onInternalSlotClick(int slotIndex, int button, ContainerInput actionType,
            Player player, CallbackInfo ci) {
        if (SpoilageEnhancedLogger.isTraceEnabled()) SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE, "ScreenHandlerMixin: clicked slot=" + slotIndex + " action=" + actionType);

        if (!player.level().isClientSide() && spoilage_enhanced$isProcessingMenu()) {
            if (actionType == ContainerInput.QUICK_MOVE) {
                if (slotIndex >= 0 && slotIndex < this.slots.size()) {
                    Slot slot = this.slots.get(slotIndex);
                    if (slot.container instanceof Inventory) {
                        if (!((Object) this instanceof InventoryMenu)) {
                            ItemStack stack = slot.getItem();
                            // Pass 86: isEntirelyRotten reads the SPOILAGE component — skip the
                            // isSpoilable() CHM get when the component is absent (non-food items).
                            if (!stack.isEmpty() && stack.hasNonDefault(ModDataComponentTypes.SPOILAGE)
                                    && SpoilageConfig.getInstance().isSpoilable(stack.getItem())) {
                                if (FoodSpoilageUtil.isEntirelyRotten(stack)) {
                                    player.sendOverlayMessage(Component.translatable(SpoilageEnhancedTranslations.CMD_ROTTEN_CANNOT_COOK));
                                    ci.cancel();
                                    return;
                                }
                            }
                        }
                    }
                }
            } else if (slotIndex >= 0 && slotIndex < this.slots.size()) {
                Slot slot = this.slots.get(slotIndex);
                if (spoilage_enhanced$isProcessingInputSlot(slotIndex, slot)) {
                    ItemStack stackToInsert = ItemStack.EMPTY;
                    if (actionType == ContainerInput.PICKUP || actionType == ContainerInput.PICKUP_ALL) {
                        stackToInsert = this.getCarried();
                    } else if (actionType == ContainerInput.SWAP) {
                        stackToInsert = player.getInventory().getItem(button);
                    } else if (actionType == ContainerInput.QUICK_CRAFT) {
                        stackToInsert = this.getCarried();
                    }

                    if (!stackToInsert.isEmpty() && stackToInsert.hasNonDefault(ModDataComponentTypes.SPOILAGE)
                            && SpoilageConfig.getInstance().isSpoilable(stackToInsert.getItem())) {
                        // Pass 223 (PLAYER_REPORT §2 — rotten insert): the old guard tested
                        // isEntirelyRotten(stackToInsert) — true only when the WHOLE carried
                        // stack is rotten. But the transfer moves the WORST items first
                        // (SlotMixin.safeInsert -> extractWorstItems), so a right-click (1 item)
                        // takes exactly the worst item, which can be rotten while the stack is
                        // not entirely rotten. Test the slice that is actually about to move:
                        // right-click (QUICK_CRAFT) inserts 1, every other click type inserts
                        // the whole stack. Reject when the worst-N slice contains a ROTTEN entry.
                        int insertCount = (actionType == ContainerInput.QUICK_CRAFT) ? 1 : stackToInsert.getCount();
                        if (FoodSpoilageUtil.worstSliceContainsRotten(stackToInsert, insertCount)) {
                            player.sendOverlayMessage(Component.translatable(SpoilageEnhancedTranslations.CMD_ROTTEN_CANNOT_COOK));
                            ci.cancel();
                            return;
                        }
                    }
                }
            }
        }

        // --- QUICK_CRAFT (drag) handling ---
        // Note: PICKUP (click) is handled automatically by SlotMixin.safeInsert
        if (actionType == ContainerInput.QUICK_CRAFT) {
            int phase = AbstractContainerMenu.getQuickcraftHeader(button);
            if (phase == 2 && this.quickcraftStatus == 1) {
                ItemStack cursor = this.getCarried();
                // Pass 86: savedSpoilageData is only used when non-null; skip the isSpoilable()
                // CHM get when the cursor has no spoilage component (non-food drags).
                if (!cursor.isEmpty() && cursor.hasNonDefault(ModDataComponentTypes.SPOILAGE)
                        && SpoilageConfig.getInstance().isSpoilable(cursor.getItem())) {
                    this.spoilage_enhanced$savedSpoilageData = cursor.get(ModDataComponentTypes.SPOILAGE);
                    this.spoilage_enhanced$savedDragSlots = new java.util.ArrayList<>(this.quickcraftSlots);
                    this.spoilage_enhanced$savedSlotStacks = new HashMap<>();
                    for (Slot slot : this.quickcraftSlots) {
                        this.spoilage_enhanced$savedSlotStacks.put(slot, slot.getItem().copy());
                    }
                }
            }
        }

        // --- PICKUP_ALL (double-click) handling ---
        if (actionType == ContainerInput.PICKUP_ALL) {
            ItemStack cursor = this.getCarried();
            // NOTE (Pass 86 audit): this isSpoilable() call cannot be replaced by a
            // has(SPOILAGE) check — the RETURN handler merges slot data INTO the cursor even
            // when the cursor itself has no component yet, so skipping component-less cursors
            // would change behavior.
            if (!cursor.isEmpty() && SpoilageConfig.getInstance().isSpoilable(cursor.getItem())) {
                this.spoilage_enhanced$pickAllSlotCounts = new HashMap<>();
                this.spoilage_enhanced$pickAllSlotData = new HashMap<>();
                for (Slot slot : this.slots) {
                    ItemStack slotItem = slot.getItem();
                    if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(cursor, slotItem)) {
                        this.spoilage_enhanced$pickAllSlotCounts.put(slot, slotItem.getCount());
                        SpoilageData data = slotItem.get(ModDataComponentTypes.SPOILAGE);
                        if (data != null) {
                            this.spoilage_enhanced$pickAllSlotData.put(slot, data);
                        }
                    }
                }
            }
        }
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void onInternalSlotClickReturn(int slotIndex, int button, ContainerInput actionType,
            Player player, CallbackInfo ci) {
        if (actionType == ContainerInput.QUICK_CRAFT && this.spoilage_enhanced$savedSpoilageData != null) {
            SpoilageData currentCursorData = this.spoilage_enhanced$savedSpoilageData;

            if (this.spoilage_enhanced$savedDragSlots != null) {
                for (Slot slot : this.spoilage_enhanced$savedDragSlots) {
                    ItemStack targetStack = slot.getItem();
                    // NOTE (Pass 86 audit): this isSpoilable() call cannot be replaced by a
                    // has(SPOILAGE) check — the merge below writes cursor data INTO the target
                    // even when the target has no component yet (fresh creative-mode items), so
                    // skipping component-less targets would change behavior.
                    if (targetStack.isEmpty() || !SpoilageConfig.getInstance().isSpoilable(targetStack.getItem()))
                        continue;

                    ItemStack oldStack = this.spoilage_enhanced$savedSlotStacks.get(slot);
                    int oldCount = (oldStack != null && !oldStack.isEmpty()) ? oldStack.getCount() : 0;
                    int addedCount = targetStack.getCount() - oldCount;
                    if (addedCount <= 0)
                        continue;

                    SpoilageData previousSlotData = (oldStack != null && !oldStack.isEmpty()) ? oldStack.get(ModDataComponentTypes.SPOILAGE) : null;
                    SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(currentCursorData, addedCount);
                    currentCursorData = split[0];
                    targetStack.set(ModDataComponentTypes.SPOILAGE, FoodSpoilageUtil.mergeItems(previousSlotData, split[1]));
                }
            }

            ItemStack remainingCursor = this.getCarried();
            if (!remainingCursor.isEmpty() && SpoilageConfig.getInstance().isSpoilable(remainingCursor.getItem())) {
                remainingCursor.set(ModDataComponentTypes.SPOILAGE, currentCursorData);
            }

            this.spoilage_enhanced$savedSpoilageData = null;
            this.spoilage_enhanced$savedDragSlots = null;
            this.spoilage_enhanced$savedSlotStacks = null;
        }

        if (actionType == ContainerInput.PICKUP_ALL && this.spoilage_enhanced$pickAllSlotCounts != null) {
            ItemStack cursor = this.getCarried();
            if (!cursor.isEmpty() && SpoilageConfig.getInstance().isSpoilable(cursor.getItem())) {
                SpoilageData cursorData = cursor.get(ModDataComponentTypes.SPOILAGE);
                for (Map.Entry<Slot, Integer> entry : this.spoilage_enhanced$pickAllSlotCounts.entrySet()) {
                    Slot slot = entry.getKey();
                    int oldCount = entry.getValue();
                    int newCount = slot.getItem().getCount();
                    int taken = oldCount - newCount;
                    if (taken > 0) {
                        SpoilageData slotOldData = this.spoilage_enhanced$pickAllSlotData.get(slot);
                        if (slotOldData != null) {
                            SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(slotOldData, taken);
                            cursorData = FoodSpoilageUtil.mergeItems(cursorData, split[1]);
                        }
                    }
                }
                cursor.set(ModDataComponentTypes.SPOILAGE, cursorData);
            }
            this.spoilage_enhanced$pickAllSlotCounts = null;
            this.spoilage_enhanced$pickAllSlotData = null;
        }
    }

    @Shadow
    public abstract ItemStack getCarried();

    @Shadow
    public abstract boolean canTakeItemForPickAll(ItemStack stack, Slot slot);

    @Unique
    private boolean spoilage_enhanced$isProcessingMenu() {
        Object self = this;
        return self instanceof CraftingMenu
                || self instanceof InventoryMenu
                || self instanceof AbstractFurnaceMenu
                || self instanceof BrewingStandMenu
                || self instanceof LoomMenu
                || self instanceof CartographyTableMenu
                || self instanceof StonecutterMenu
                || self instanceof GrindstoneMenu
                || self instanceof AnvilMenu
                || self instanceof SmithingMenu
                || self instanceof MerchantMenu
                || this.getClass().getSimpleName().contains("CookingPot");
    }

    @Unique
    private boolean spoilage_enhanced$isProcessingInputSlot(int slotIndex, Slot slot) {
        Object self = this;

        if (self instanceof CraftingMenu) {
            return slotIndex >= 1 && slotIndex <= 9;
        }

        if (self instanceof InventoryMenu) {
            return slotIndex >= 1 && slotIndex <= 4;
        }

        if (self instanceof AbstractFurnaceMenu) {
            return slotIndex == 0;
        }

        if (self instanceof BrewingStandMenu) {
            return slotIndex == 3;
        }

        if (this.getClass().getSimpleName().contains("CookingPot")) {
            return slotIndex >= 0 && slotIndex <= 5;
        }

        if (self instanceof LoomMenu
                || self instanceof CartographyTableMenu
                || self instanceof StonecutterMenu
                || self instanceof GrindstoneMenu
                || self instanceof AnvilMenu
                || self instanceof SmithingMenu
                || self instanceof MerchantMenu) {
            return !(slot.container instanceof Inventory);
        }

        return false;
    }
}
