package com.spoilageenhanced.mixin;

import com.spoilageenhanced.block.BlockSpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.DynamicFoodBlockCache;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StemBlock.class)
public abstract class StemBlockGrowMixin {

    /**
     * Pass 477 (L1 — silent failure / laundering): the RETURN-only hook registered every
     * untracked food block beside an age-7 stem as FRESH with a now-based expiration. But
     * vanilla only grows a fruit into ONE direction, and only when that spot was air
     * (StemBlock.randomTick:96-99). A pumpkin that had been sitting beside the stem for days
     * — world-generated, or grown by an earlier tick and never looked at — was re-registered
     * as brand new, laundering its age away. The lazy-registration path in
     * BlockSpoilageData.getSpoilageState (which anchors to the chunk birth time) is what
     * should handle pre-existing blocks.
     *
     * <p>Fix: capture the four neighbour states at HEAD, and at RETURN only register the
     * positions that actually changed from air to something — i.e. the fruit vanilla just
     * placed. Everything else keeps whatever age it already had.</p>
     */
    @Inject(method = "randomTick", at = @At("HEAD"))
    private void spoilage_enhanced$recordNeighbours(BlockState state, ServerLevel world, BlockPos pos,
            RandomSource random, CallbackInfo ci) {
        if (state.getValue(StemBlock.AGE) != 7) {
            return;
        }
        // Record which horizontal neighbours were air before vanilla ran. Only those can be
        // the fruit this tick placed.
        long[] wasAir = new long[4];
        int i = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            wasAir[i++] = world.getBlockState(pos.relative(dir)).isAir() ? 1L : 0L;
        }
        spoilage_enhanced$neighbourWasAir = wasAir;
    }

    @Unique
    private long[] spoilage_enhanced$neighbourWasAir;

    @Inject(method = "randomTick", at = @At("RETURN"))
    private void onRandomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random, CallbackInfo ci) {
        long[] wasAir = spoilage_enhanced$neighbourWasAir;
        spoilage_enhanced$neighbourWasAir = null;
        if (wasAir == null) {
            // The HEAD hook did not run for this tick (stem was not at age 7 on entry), or the
            // state aged past 7 inside the call. Either way there is no before-picture to
            // compare against, so do nothing — the lazy-registration path covers pre-existing
            // blocks with the correct (chunk-birth) age.
            return;
        }
        int i = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos targetPos = pos.relative(dir);
            boolean neighbourWasAir = wasAir[i++] == 1L;
            if (!neighbourWasAir) {
                // Something was already there before this tick — not the fruit vanilla just
                // placed. Registering it would reset its age to now.
                continue;
            }
            BlockState targetState = world.getBlockState(targetPos);
            if (targetState.isAir()) {
                // Still air — vanilla did not grow a fruit this tick.
                continue;
            }
            String dropItemId = DynamicFoodBlockCache.getFoodDrop(targetState, world, targetPos);
            if (dropItemId != null) {
                BlockSpoilageData data = BlockSpoilageData.get(world);
                if (!data.isTracked(targetPos)) {
                    Item dropItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(dropItemId));
                    long freshDuration = SpoilageConfig.getInstance().getFreshDurationForItem(dropItem);
                    long expirationTime = world.getGameTime() + freshDuration;
                    data.setSpoilageState(targetPos, FoodSpoilageUtil.SpoilageState.FRESH, expirationTime);
                    SpoilageEnhancedLogger.log("StemBlockGrowMixin: Block at " + targetPos + " newly grown, registered fresh expiration: " + expirationTime);
                }
            }
        }
    }
}
