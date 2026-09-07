package com.spoilageenhanced.mixin;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ComposterBlock.class)
public class ComposterBlockMixin {

    /**
     * Targets the private addItem() rather than the public insertItem(): addItem is the
     * single point both the player interaction (useItemOn) and the hopper/dispenser path
     * (insertItem) go through, and it does not consume the stack, so cancelling it here
     * leaves the callers' own shrink/consume calls intact.
     */
    @Inject(method = "addItem", at = @At("HEAD"), cancellable = true)
    private static void onAddToComposter(@Nullable Entity entity, BlockState state, LevelAccessor world, BlockPos pos,
            ItemStack item, CallbackInfoReturnable<BlockState> cir) {
        // Pass 87 (Lens 8/13): the work below returns when data is null/empty, so when the
        // composted stack has no SPOILAGE component nothing happens. Check the component first
        // via the cheaper ItemStack.has() and skip the isSpoilable() CHM get when absent.
        if (!item.hasNonDefault(ModDataComponentTypes.SPOILAGE)) {
            return;
        }
        if (!SpoilageConfig.getInstance().isSpoilable(item.getItem())) {
            return;
        }
        // Pass 131 (Lens 4): ComposterBlock.COMPOSTABLES is an Object2FloatOpenHashMap
        // whose defaultReturnValue is -1.0F (ComposterBlock.java:68), so getFloat()
        // returns -1.0F for missing keys — NOT 0.0f. The old containsKey() + getFloat()
        // pair was two hash lookups for the same key; one getFloat() call replaces both,
        // and the <= 0.0F guard below is what actually rejects non-compostable items.
        float defaultChance = ComposterBlock.COMPOSTABLES.getFloat(item.getItem());
        if (defaultChance <= 0.0F) {
            return;
        }

        SpoilageData data = item.get(ModDataComponentTypes.SPOILAGE);
        if (data == null || data.isEmpty()) {
            return;
        }
        float newChance;

        FoodSpoilageUtil.SpoilageState worst = FoodSpoilageUtil.getWorstState(item);

        SpoilageConfig.ComposterConfig composterCfg = SpoilageConfig.getInstance().getComposterConfig();
        if (worst == FoodSpoilageUtil.SpoilageState.ROTTEN) {
            newChance = composterCfg.rotten_chance;
        } else if (worst == FoodSpoilageUtil.SpoilageState.STALE) {
            newChance = Math.max(composterCfg.stale_chance, defaultChance);
        } else {
            newChance = Math.min(composterCfg.fresh_chance, defaultChance);
        }

        int level = state.getValue(ComposterBlock.LEVEL);
        // Pass 610 (L9 — interaction): only extract the tracker when the compost will actually
        // succeed. The old code extracted first and then checked the chance — a rotten item
        // with composterCfg.rotten_chance=0.0F (the documented "rotten items produce no
        // bone meal" config) lost a tracker on every attempt, even though the stack wasn't
        // composted. The caller (insertItem or useItemOn) still consumes the stack, so the
        // lost tracker meant the player was charged a fresh item for nothing.
        boolean willSucceed = (level == 0 && newChance > 0.0F) || (newChance > 0.0F && world.getRandom().nextDouble() < (double) newChance);
        if (!willSucceed) {
            SpoilageEnhancedLogger.log("Composter: Rejected " + BuiltInRegistries.ITEM.getKey(item.getItem())
                + " (State: " + worst + "). Chance: " + newChance + " (no tracker change)");
            cir.setReturnValue(state);
            return;
        }

        SpoilageData[] split = FoodSpoilageUtil.extractWorstItems(data, 1);
        item.set(ModDataComponentTypes.SPOILAGE, split[0]);

        SpoilageEnhancedLogger.log("Composter: Added " + BuiltInRegistries.ITEM.getKey(item.getItem())
            + " (State: " + worst + "). Chance: " + newChance + ", Success: true");

        int nextLevel = level + 1;
        BlockState nextState = state.setValue(ComposterBlock.LEVEL, nextLevel);
        world.setBlock(pos, nextState, 3);
        world.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(entity, nextState));
        if (nextLevel == 7) {
            world.scheduleTick(pos, state.getBlock(), 20);
        }
        cir.setReturnValue(nextState);
    }
}
