package com.spoilageenhanced.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.spoilageenhanced.block.BlockSpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.DynamicFoodBlockCache;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class BlockStateChangeMixin {

    /**
     * Pass 112 (Lens 8): the old HEAD inject did world.getBlockState(pos) — a chunk-section
     * read — on EVERY server-side setBlock call (every placement, break, piston move, fluid
     * flow, redstone update), pushing the result onto a ThreadLocal stack for the RETURN
     * handler to pop. But vanilla's own setBlock already reads the old state internally
     * (chunk.setBlockState returns it, Level.java line 235) — our HEAD read duplicated work
     * vanilla had already done.
     *
     * <p>Replaced with MixinExtras {@code @Local} capture of vanilla's own {@code oldState}
     * local at RETURN. The HEAD inject, the ThreadLocal deque, and the duplicated
     * chunk-section read are all gone. MixinExtras 0.5.4 ships with the loader (visible in
     * the boot log as "mixinextras 0.5.4").</p>
     */
    @SuppressWarnings("resource")
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
    private void onSetBlockStateReturn(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
            CallbackInfoReturnable<Boolean> cir, @Local(ordinal = 1) BlockState oldState) {
        // Pass 477 (L1 — silent failure, ROOT CAUSE): the previous bare @Local matched the
        // FIRST BlockState local in the LVT — which is the blockState PARAMETER (slot 1), not
        // the oldState local (Level.java:230). The hook therefore always saw old == new, the
        // oldState.equals(state) guard below always returned early, and the air-removal branch
        // NEVER fired: entries for removed blocks stayed in the map forever, so a stem
        // regrowing a fruit on the same spot inherited the OLD expiration instead of starting
        // fresh. @Local(ordinal = 1) selects the second BlockState local — oldState.
        Level world = (Level) (Object) this;
        if (world.isClientSide() || !(world instanceof ServerLevel serverWorld)) {
            return;
        }

        if (!cir.getReturnValue() || oldState == null || oldState.equals(state)) {
            return;
        }

        // 1. If a tracked block became air (broken by water, piston, explosion, fire), remove it from tracking.
        // BUG-14: a player break also lands here, and it lands BEFORE the drop — vanilla clears the
        // block in ServerPlayerGameMode:280 and only drops in :298. Removing outright at this point
        // threw away the state the drop was about to inherit, so park it first; the drop reclaims it
        // in BlockSpoilageData.getSpoilageState, and anything that never drops lets it expire.
        if (state.isAir() && !oldState.isAir()) {
            BlockSpoilageData data = BlockSpoilageData.get(serverWorld);
            if (data.isTracked(pos)) {
                data.park(pos, data.getEntry(pos), serverWorld.getGameTime());
                data.remove(pos);
            }
            return;
        }

        // 2. Fast candidate filter: skip non-growing / non-food blocks (redstone, repeaters, copper, etc.)
        if (oldState.getBlock() == state.getBlock()) {
            net.minecraft.world.level.block.Block block = state.getBlock();
            if (!(block instanceof net.minecraft.world.level.block.CropBlock
                    || block instanceof net.minecraft.world.level.block.StemBlock
                    || block instanceof net.minecraft.world.level.block.AttachedStemBlock
                    || block instanceof net.minecraft.world.level.block.CocoaBlock
                    || block instanceof net.minecraft.world.level.block.SweetBerryBushBlock
                    || block instanceof net.minecraft.world.level.block.NetherWartBlock
                    || SpoilageConfig.getInstance().isBlockTracked(BuiltInRegistries.BLOCK.getKey(block).toString()))) {
                return;
            }

            String oldFood = DynamicFoodBlockCache.getFoodDrop(oldState, serverWorld, pos);
            String newFood = DynamicFoodBlockCache.getFoodDrop(state, serverWorld, pos);

            BlockSpoilageData data = BlockSpoilageData.get(serverWorld);

            // A crop finishing its growth is the moment its freshness starts. Until then there
            // is nothing to spoil: the plant is still growing, and a clock started at planting
            // would run through the whole growth period, leaving food that was stale before it
            // could ever be picked.
            //
            // The check is on the growth stage, not on the drop: DynamicFoodBlockCache answers
            // per BLOCK, so a seedling and a ripe plant look identical to it and the branch
            // below (drop appears where there was none) never fires for a crop at all.
            // Ripeness is asked of DynamicFoodBlockCache, not of the growth stage alone: a plant
            // that signals its fruit with a boolean (cave vines) has a stage that measures
            // something else, and reading it here meant "berries appeared" never registered as
            // the moment freshness starts, while the removal branch below stripped the entry on
            // every state change instead. getFoodDrop above has already derived the rule.
            boolean wasBearing = DynamicFoodBlockCache.bearsFoodYet(oldState, serverWorld, pos);
            boolean isBearing = DynamicFoodBlockCache.bearsFoodYet(state, serverWorld, pos);

            if (newFood != null && !wasBearing && isBearing) {
                Item ripeItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(newFood));
                long freshDuration = SpoilageConfig.getInstance().getFreshDurationForItem(ripeItem);
                data.setSpoilageState(pos, FoodSpoilageUtil.SpoilageState.FRESH,
                        serverWorld.getGameTime() + freshDuration);
                com.spoilageenhanced.util.SpoilageEnhancedLogger.log("BlockStateChange: " + pos
                        + " finished growing (" + newFood + "); freshness starts now.");
                return;
            }

            // Not bearing anything: leave it alone, and make sure nothing tracked it earlier.
            if (!isBearing) {
                if (data.isTracked(pos)) {
                    data.remove(pos);
                }
                return;
            }

            if (newFood != null && oldFood == null) {
                Item dropItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(newFood));
                long freshDuration = SpoilageConfig.getInstance().getFreshDurationForItem(dropItem);
                data.setSpoilageState(pos, FoodSpoilageUtil.SpoilageState.FRESH, serverWorld.getGameTime() + freshDuration);
                com.spoilageenhanced.util.SpoilageEnhancedLogger.log("BlockStateChange: " + pos + " grew " + newFood + " (state: " + state.toString() + "). Birth time reset.");
            }
        }
    }
}
