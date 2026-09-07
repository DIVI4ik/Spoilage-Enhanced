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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
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

@Mixin(ServerLevel.class)
public class InteractionSpawnEntityMixin {

    @Inject(method = "addFreshEntity", at = @At("HEAD"))
    private void onSpawnEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            if (stack.has(ModDataComponentTypes.SPOILAGE)) {
                return;
            }

            Item item = stack.getItem();

            if (SpoilageConfig.getInstance().isSpoilable(item)) {

                // Pass 601 (L2 — lifecycle): pass the current game time so a leaked context
                // (exception inside useItemOn skipped the RETURN clear) reads as inactive
                // instead of stamping this unrelated drop with a stale block's spoilage.
                if (!ActiveInteractionContext.isActive(((ServerLevel) (Object) this).getGameTime())) {
                    ServerLevel world = (ServerLevel) (Object) this;
                    BlockPos dropPos = itemEntity.blockPosition();

                    // Never steal or delete tracking from a block that is still standing in the world
                    if (!world.getBlockState(dropPos).isAir()) {
                        return;
                    }

                    BlockSpoilageData data = BlockSpoilageData.get(world);
                    
                    if (data.isTracked(dropPos)) {
                        FoodSpoilageUtil.SpoilageState spoilState = data.getSpoilageState(dropPos, world, item);
                        long remainingTicks = data.getTicksUntilNextStage(dropPos, world, item);
                        
                        long expire = world.getGameTime() + remainingTicks;
                        SpoilageData dropData;
                        if (spoilState == FoodSpoilageUtil.SpoilageState.ROTTEN) {
                            dropData = new SpoilageData(Collections.emptyList(), Collections.emptyList(), stack.getCount(), SpoilageConfig.getInstance().getSpoilageSpeedMultiplier());
                        } else if (spoilState == FoodSpoilageUtil.SpoilageState.STALE) {
                            List<Long> list = new ArrayList<>();
                            for (int i = 0; i < stack.getCount(); i++) list.add(expire);
                            dropData = new SpoilageData(Collections.emptyList(), list, 0, SpoilageConfig.getInstance().getSpoilageSpeedMultiplier());
                        } else {
                            List<Long> list = new ArrayList<>();
                            for (int i = 0; i < stack.getCount(); i++) list.add(expire);
                            dropData = new SpoilageData(list, Collections.emptyList(), 0, SpoilageConfig.getInstance().getSpoilageSpeedMultiplier());
                        }
                        
                        stack.set(ModDataComponentTypes.SPOILAGE, dropData);
                        itemEntity.setItem(stack);
                        SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.TRACE, "InteractionSpawnEntityMixin: Applied " + spoilState + " to " + BuiltInRegistries.ITEM.getKey(item) + " dropped from block at " + dropPos);
                        data.remove(dropPos);
                    }
                    return;
                }

                String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
                BlockPos blockPos = ActiveInteractionContext.getPos();
                BlockState state = ActiveInteractionContext.getState();
                ServerLevel world = (ServerLevel) (Object) this;

                String expectedDrop = DynamicFoodBlockCache.getFoodDrop(state, world, blockPos);
                if (expectedDrop == null) {
                    // Nothing declared this block a food source — but one just came out of it.
                    // A hand-picked plant never appears in a loot table, so this interaction is
                    // the only evidence there is. Learn it and carry on with the same harvest,
                    // so the very first apple is tracked rather than the second.
                    if (DynamicFoodBlockCache.learnFoodDropFromInteraction(
                            state, blockPos, itemEntity.blockPosition(), world, itemId)) {
                        expectedDrop = itemId;
                    }
                }
                if (expectedDrop == null || !expectedDrop.equals(itemId)) {
                    return;
                }

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
                itemEntity.setItem(stack);
            }
        }
    }

    @Unique
    private static void applyFresh(ItemStack stack, long expirationTime) {
        List<Long> list = new ArrayList<>();
        for (int i = 0; i < stack.getCount(); i++) {
            list.add(expirationTime);
        }
        stack.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(list, Collections.emptyList(), 0, SpoilageConfig.getInstance().getSpoilageSpeedMultiplier()));
    }

    @Unique
    private static void applyStale(ItemStack stack, long expirationTime) {
        List<Long> list = new ArrayList<>();
        for (int i = 0; i < stack.getCount(); i++) {
            list.add(expirationTime);
        }
        stack.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(Collections.emptyList(), list, 0, SpoilageConfig.getInstance().getSpoilageSpeedMultiplier()));
    }

    @Unique
    private static void applyRotten(ItemStack stack) {
        stack.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(Collections.emptyList(), Collections.emptyList(), stack.getCount(), SpoilageConfig.getInstance().getSpoilageSpeedMultiplier()));
    }
}
