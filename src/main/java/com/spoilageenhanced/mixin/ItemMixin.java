package com.spoilageenhanced.mixin;

import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Item.class)
public abstract class ItemMixin {

    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void onInventoryTick(ItemStack stack, ServerLevel world, Entity entity, EquipmentSlot slot,
            CallbackInfo ci) {
        if (stack.isEmpty() || world == null) return;
        // Pass 605 (L4 — hot-path cost): the CONTAINER check was the first thing after the
        // empty/null guards, so every non-CONTAINER item paid a DataComponentMap.has() lookup
        // every tick. For a server with 1000 items in loaded inventories, that's 20,000 wasted
        // lookups/second. The 20-tick throttle is correct for both branches (CONTAINER and
        // non-CONTAINER), so check it first — 19 of 20 ticks return immediately for every item.
        if (world.getGameTime() % 20L != 0L) {
            return;
        }
        if (stack.has(DataComponents.CONTAINER)) {
            FoodSpoilageUtil.updateContainerItemSpoilage(stack, world);
            return;
        }
        // Pass 89 (Lens 8): this mixin runs for EVERY item in EVERY loaded inventory EVERY tick.
        // The old order called isSpoilable() (a ConcurrentHashMap.get()) before the 20-tick
        // throttle, paying the CHM get 20x per second per stack. Reordered: tracked stacks
        // (component present) check the throttle FIRST and skip the config lookup on 19 of 20
        // ticks — a 20x reduction in config lookups on the hottest inventory path.
        // Untracked stacks (no component) also throttle to 20-tick boundary (Pass 129):
        // they only need initialization once, and a 20-tick delay is acceptable.
        if (!SpoilageConfig.getInstance().isSpoilable((Item) (Object) this))
            return;

        if (SpoilageEnhancedLogger.isTraceEnabled()) {
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE, "ItemMixin: inventoryTick for " + BuiltInRegistries.ITEM.getKey(stack.getItem()) + " in slot " + slot);
        }
        FoodSpoilageUtil.updateSpoilage(stack, world);
    }
}
