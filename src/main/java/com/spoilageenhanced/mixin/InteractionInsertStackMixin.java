package com.spoilageenhanced.mixin;

import com.spoilageenhanced.block.BlockSpoilageData;
import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.ActiveInteractionContext;
import com.spoilageenhanced.util.DynamicFoodBlockCache;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(Inventory.class)
public class InteractionInsertStackMixin {

    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"))
    private void onInsertStack(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) return;

        Item item = stack.getItem();
        if (SpoilageConfig.getInstance().isSpoilable(item)) {
            Inventory inv = (Inventory) (Object) this;
            if (inv.player.level() instanceof ServerLevel world) {
                // Pass 601 (L2 — lifecycle): pass the current game time so a leaked context
                // (exception inside useItemOn skipped the RETURN clear) reads as inactive
                // instead of stamping this unrelated insert with a stale block's spoilage.
                if (!ActiveInteractionContext.isActive(world.getGameTime())) {
                    return;
                }
                String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
                BlockPos blockPos = ActiveInteractionContext.getPos();
                BlockState state = ActiveInteractionContext.getState();

                DynamicFoodBlockCache.registerFoodDrop(state, itemId);
                SpoilageEnhancedLogger.log("Intercepted insertStack for " + itemId + " from block state " + state.toString() + " at " + blockPos);

                BlockSpoilageData data = BlockSpoilageData.get(world);
                if (!data.isTracked(blockPos)) {
                    long freshDuration = SpoilageConfig.getInstance().getFreshDurationForItem(item);
                    data.setSpoilageState(blockPos, FoodSpoilageUtil.SpoilageState.FRESH, world.getGameTime() + freshDuration);
                }

                FoodSpoilageUtil.SpoilageState spoilState = data.getSpoilageState(blockPos, world, item);
                BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(blockPos);
                long expirationTime = entry != null ? entry.expirationTime : -1;

                switch (spoilState) {
                    case FRESH:
                        applyFresh(stack, expirationTime);
                        break;
                    case STALE:
                        applyStale(stack, expirationTime);
                        break;
                    case ROTTEN:
                        applyRotten(stack);
                        break;
                }
            }
        }
    }

    @Unique
    private void applyFresh(ItemStack stack, long expirationTime) {
        List<Long> list = new ArrayList<>();
        for (int i = 0; i < stack.getCount(); i++) {
            list.add(expirationTime);
        }
        stack.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(list, Collections.emptyList(), 0, SpoilageConfig.getInstance().getSpoilageSpeedMultiplier()));
    }

    @Unique
    private void applyStale(ItemStack stack, long expirationTime) {
        List<Long> list = new ArrayList<>();
        for (int i = 0; i < stack.getCount(); i++) {
            list.add(expirationTime);
        }
        stack.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(Collections.emptyList(), list, 0, SpoilageConfig.getInstance().getSpoilageSpeedMultiplier()));
    }

    @Unique
    private void applyRotten(ItemStack stack) {
        stack.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(Collections.emptyList(), Collections.emptyList(), stack.getCount(), SpoilageConfig.getInstance().getSpoilageSpeedMultiplier()));
    }
}
