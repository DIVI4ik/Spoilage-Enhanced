package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {

    @Unique
    private static long spoilage_enhanced_cachedWorldTime = 0L;

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void spoilage_enhanced_trackWorldTime(
            ServerLevel world, BlockPos pos, BlockState state,
            AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {
        spoilage_enhanced_cachedWorldTime = world.getGameTime();
    }

    @Inject(method = "canBurn", at = @At("HEAD"), cancellable = true)
    private static void spoilage_enhanced_blockRottenSmelting(
            NonNullList<ItemStack> slots,
            int count,
            ItemStack result,
            CallbackInfoReturnable<Boolean> cir) {

        if (result.isEmpty()) return;

        ItemStack inputStack = slots.get(0);
        if (!inputStack.isEmpty()
                && SpoilageConfig.getInstance().isSpoilable(inputStack.getItem())) {
            if (FoodSpoilageUtil.isEntirelyRotten(inputStack)) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "burn", at = @At("RETURN"))
    private static void spoilage_enhanced_purifyOnSmelt(
            NonNullList<ItemStack> slots,
            ItemStack result,
            ItemStack source,
            CallbackInfo ci) {

        ItemStack outputStack = slots.get(2);
        if (outputStack.isEmpty()
                || !SpoilageConfig.getInstance().isSpoilable(outputStack.getItem()))
            return;

        long DEFAULT_FRESH_TICKS = SpoilageConfig.getInstance()
                .getFreshDurationForItem(outputStack.getItem());

        // Pass 118 (Lens 13): the old code copied freshExpirations() and staleExpirations() into
        // new ArrayLists and then passed them to the SpoilageData constructor — which copies them
        // AGAIN (compact constructor). The accessors already return defensive copies, so the
        // explicit new ArrayList<>() wrappers were double copies. Now: the fresh side gets one
        // sized list (existing + the new entry); the stale side passes the accessor result
        // straight through (the constructor copies it); the null case uses the shared empty list
        // (free since Pass 88).
        SpoilageData data = outputStack.get(ModDataComponentTypes.SPOILAGE);
        List<Long> freshList;
        List<Long> staleList;
        int rottenCount;
        if (data != null) {
            freshList = new ArrayList<>(data.freshExpirations().size() + 1);
            freshList.addAll(data.freshExpirations());
            staleList = data.staleExpirations();
            rottenCount = data.rottenCount();
        } else {
            freshList = new ArrayList<>(1);
            staleList = Collections.emptyList();
            rottenCount = 0;
        }
        freshList.add(spoilage_enhanced_cachedWorldTime + DEFAULT_FRESH_TICKS);

        SpoilageData updated = new SpoilageData(freshList, staleList, rottenCount, SpoilageConfig.getInstance().getSpoilageSpeedMultiplier());
        outputStack.set(ModDataComponentTypes.SPOILAGE, updated);

        SpoilageEnhancedLogger.log("Furnace: Added 1 FRESH timer to output: " + outputStack.getItem());
    }
}
